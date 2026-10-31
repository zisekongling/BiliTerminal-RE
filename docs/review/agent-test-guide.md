# Agent 自动化测试手册（Debug 包）

> 面向对象：AI agent / 自动化脚本 / 需要脚本化验证真机行为的人。
> 适用版本：26.10.14 起的 Debug 构建。Release 包**不含**本文描述的任何东西。
> 配套：`docs/architecture-map.md`（架构与坑）、`docs/review/real-device-regression-checklist.md`（发版前真机回归清单）；旧的 T/D 测试计划已归档为 `docs/archive/review/device-test-plan.md`。

---

## 1. 为什么需要这份手册

（已归档的）`device-test-plan.md` 里那句「**全部用例一条都还没执行**」的根因不是没人想测，而是 debug 包**没有给自动化留任何入口**：

| 原来的状况 | 对 agent 的后果 |
| --- | --- |
| `TestActivity` 是 `exported="false"` | adb shell（uid 2000，非 root）**拉不起来**；只能手点「设置 → 开发者工具 → 功能测试」 |
| 全应用仅 `SplashActivity` / `GetIntentActivity` 导出，后者只认 4 种 `type` | 设置页、播放器、动态、消息……**一个都到不了** |
| 日志开关存在 SharedPreferences，改完要重启 | 无法从外部改；手表上点设置项本身就很痛苦 |
| `Logu` 的 tag 是 `类名>方法名` | 一次抓全得全量 grep，被别家进程噪音淹没（**注意：这个 tag 不能动**，见下） |
| `NetWorkUtil` 不记状态码 / 响应体 / 耗时 | 「风控返回 HTML」「`code=-412`」「请求没发出去」在日志里长得一样 |
| UETool 在 debug 下启动时申请悬浮窗权限 | 未授权会**直接 `return` 掉启动主流程**并跳到系统设置页；授权后悬浮窗还会盖住界面 |

26.10.14 这一批改动就是针对上表逐条补的：**一个可编程控制通道 + 一套统一可观测性 + 一个总开关**。

---

## 2. 快速上手

### 2.1 用哪个 flavor

**用 `coexistDebug`，不要用 `standardDebug`。**

- `standardDebug` 的包名是 `com.RobinNotBad.BiliClient`，与正式版**完全相同**。debug 签名与 release 签名不同，同包名不同签名只能先卸载正式版才能装，**用户数据（登录态、设置、缓存）全丢**。
- `coexistDebug` 的包名是 `com.RobinNotBad.BiliClient.coexist`，与正式版**共存**，随便折腾。
- 两者的**代码与资源完全相同**，只有包名与图标不同，所以测出来的结论对正式版一样成立。

```bash
./gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/coexist/debug/app-coexist-universal-debug.apk
adb install -r app/build/outputs/apk/coexist/debug/app-coexist-universal-debug.apk
```

### 2.2 四条准备命令

```bash
PKG=com.RobinNotBad.BiliClient.coexist
BRIDGE="$PKG/com.RobinNotBad.BiliClient.debug.DebugBridge"
T="--es token rebili-agent-bridge"

# ① 允许后台启动 Activity（Android 10+ 的「后台启动限制」会让 start 命令被静默丢弃）
adb shell cmd appops set $PKG SYSTEM_ALERT_WINDOW allow

# ② 把全部教程标记为已读 —— 不做这步，教程页会盖在你拉起的页面上面
adb shell am broadcast -n $BRIDGE $T --es cmd notutorials

# ③ 打开 Agent 测试模式（日志全量 + 落盘 + HTTP 追踪 + 跳过 UETool 悬浮窗）
adb shell am broadcast -n $BRIDGE $T --es cmd agent --ez on true

# ④ 自检
adb shell am broadcast -n $BRIDGE $T --es cmd ping
```

②③ 都立即生效，不需要重启应用。

> **`appops` 要用 `cmd appops`**：实测 Android 12 上 `/system/bin/appops` 不存在
> （报 `appops: inaccessible or not found`），必须走 `cmd` 包一层。
>
> **`cmd appops` 的效果有系统日志为证**（Android 12 实测）：
>
> ```text
> W ActivityTaskManager: Background activity start for com.RobinNotBad.BiliClient.coexist
>    allowed because SYSTEM_ALERT_WINDOW permission is granted.
> ```
>
> 不做这一步，`start` 会被静默丢弃——不报错、页面就是不出来。

### 2.3 如果广播没有回显

个别 ROM 的 `am` 不转发 receiver 的 result data（**Android 12 实测正常**）。每个命令的结果都会同时写到
`<应用外部目录>/logs/bridge-last.txt`，回显为空时直接读它（`ping` 会打印 `logDir=` 的实际路径）：

```bash
LOG=/storage/emulated/0/Android/data/$PKG/files/logs

adb shell cat $LOG/bridge-last.txt                    # 最简单（实测可用）
adb pull $LOG/bridge-last.txt ./bridge-last.txt        # 拷到本地（实测可用）
```

> **别用 `run-as` 读这几个文件**：实测 `adb shell run-as <包名> cat /storage/emulated/0/...` 报
> `Permission denied`。`run-as` 只进得了应用的**内部**数据目录，外部 `Android/data/.../files/`
> 不在它的挂载命名空间里。上面两条（`adb shell cat` / `adb pull`）在 Android 12 上都能用。

---

## 3. 命令表

全部命令都需要 `--es token rebili-agent-bridge`。用 `--es cmd help` 可打印同一张表。

| 命令 | 参数 | 作用 |
| --- | --- | --- |
| `ping` | — | 包名 / flavor / 版本 / 各开关状态 / 日志目录 / 当前前台页 |
| `agent` | `--ez on <bool>` | 开关 Agent 测试模式（立即生效，无需重启） |
| `get` | `--es key` `[--es type]` | 读设置项，返回 `key=value` |
| `set` | `--es key` `--es value` `[--es type]` | 写设置项（`type` 省略时按已存在的类型自动推断） |
| `remove` | `--es key` | 删除设置项 |
| `dump` | — | 列出全部设置项（敏感值打码，只报长度） |
| `start` | `--es activity` `[--es extras "aid=2;seekReply=-1"]` `[--ez keepstack true]` | **进程内拉起任意 Activity**（含 `exported=false` 的）；默认 `CLEAR_TOP`，即目标页之上一律清掉 |
| `activities` | — | 列出全部可启动的 Activity 全名 |
| `notutorials` | — | 把全部教程标记为已读（不做的后果见 §6） |
| `http` | `--es url` `[--es method]` `[--es data]` `[--es headers '<JSON>']` `[--ez appheaders true]` | 发一次请求，响应体全文落盘。**`appheaders` 默认 false（26.10.19 起）：不带应用请求头（含登录 Cookie / `X-Install-Id`）；显式传 true 才用应用自己的登录态与请求头** |
| `merge` | `--es video <视频m4s>` `--es audio <音频m4s>` `[--es out <输出mp4>]` | 直接跑一次 `util/MediaMerger.mergeAv`（DASH 音视频重封装），返回返回码 / 耗时 / 输入输出字节数 / 产物轨道与采样数 |
| `log` | `[--es file]` `[--es bytes]` | 读日志尾部（默认 `rebili.log`，65536 字节） |
| `logs` | — | 列出日志文件及大小 |
| `logclear` | `[--es file]` | 清空日志 |
| `trail` | — | 页面轨迹（最近 20 个页面，崩溃报告用的同一份数据） |
| `logring` | `[--es inject <文本>]` | 读**内存日志缓冲**（不落盘那条路）的当前状态；带 `inject` 时先以 W 级写一条再取快照 |
| `crash` | — | 在主线程抛真实异常，**主进程立刻崩溃**。用于验证崩溃上报整条链路（见 §4.7） |
| `top` | — | 当前前台 Activity 全类名 |
| `help` | — | 命令表 |

`type` 可选：`string`（默认）/ `int` / `long` / `bool` / `float`。

---

## 4. 常用场景配方

### 4.1 直达任意页面

```bash
BRIDGE="am broadcast -n $PKG/com.RobinNotBad.BiliClient.debug.DebugBridge --es token rebili-agent-bridge"

# 短类名即可，按清单匹配；也可以写全限定名
adb shell $BRIDGE --es cmd start --es activity VideoInfoActivity --es extras "aid=2"
adb shell $BRIDGE --es cmd start --es activity SettingGroupActivity --es extras "group_type=dev;group_title=开发者工具"
adb shell $BRIDGE --es cmd start --es activity MenuActivity

# 先看看有哪些页面可去（实测 104 个）
adb shell $BRIDGE --es cmd activities
```

**`extras` 用 `key=value` 分号分隔，这是推荐写法**——纯数字自动转 `Long`，`true`/`false` 转 `Boolean`，其余按 `String`。

也可以写 JSON，但**经 `adb shell` 传递时双引号会被设备端 shell 吃掉**，必须转义：

```bash
adb shell $BRIDGE --es cmd start --es activity VideoInfoActivity --es extras '{\"aid\":2}'
# 不转义（'{"aid":2}'）会到不了应用侧：外层花括号还在、内层引号已被剥掉，JSONObject 直接解析失败
```

**`start` 默认会清掉目标页之上的所有页面**（`FLAG_ACTIVITY_CLEAR_TOP`）。这不是可有可无的细节：
只加 `FLAG_ACTIVITY_NEW_TASK` 时，若目标页恰好是某个已存在任务的栈根，系统会**复用任务而不是新建实例**，
于是 `startActivity` 只是把那个任务提到前台、栈顶仍停着别的页，而命令照样返回 `OK`。
需要「压栈不清栈」（测返回栈行为）时加 `--ez keepstack true`。

启动后务必用 `--es cmd top` 确认真的到了目标页——**`start` 返回 `OK` 只代表 `startActivity` 没抛异常，不代表页面真的到了前台**。

### 4.2 改设置 / 造前置条件

```bash
adb shell $BRIDGE --es cmd set --es key dev_agent_mode --es value true --es type bool
adb shell $BRIDGE --es cmd set --es key dpi --es value 1.2 --es type float
adb shell $BRIDGE --es cmd get --es key player
adb shell $BRIDGE --es cmd dump | head -40
```

这条链让 agent 能**完全绕过设置页 UI** 造出任意初始状态——比如把主题、DPI、播放器、弹幕开关直接写到位，再启动目标页面看渲染结果。

### 4.3 验证接口是否真的通

```bash
adb shell $BRIDGE --es cmd http --es url "https://api.bilibili.com/x/web-interface/nav"
# 返回：OK: 200 (312ms) bytes=1024 / file=<路径> / <响应体前 2000 字>
```

关键在于它**跑在应用进程里、用应用的 Cookie 与 `webHeaders`**——外部 `curl` 复刻不出风控认的这套身份，所以「登录态下接口是否可用」只能这样问。

配合 `log --es file http.log` 能看到所有请求的 `方法 脱敏URL -> 状态码 (耗时ms)` 流水。

### 4.4 崩溃/异常现场取证

```bash
adb shell $BRIDGE --es cmd trail          # 崩溃前走过哪些页面
adb shell $BRIDGE --es cmd log --es bytes 40000
adb shell $BRIDGE --es cmd log --es file http.log --es bytes 20000
```

因为日志会落盘，**即使 adb 连接中断、应用被杀，现场也还在**（这是 logcat 给不了的）。

### 4.5 常规 logcat 过滤

`Logu` 的 logcat tag **就是调用者**（`类名>方法名`），这是**既有诊断契约，不要改**——
`docs/archive/review/device-test-plan.md` 的 D3-11~D3-14 直接写死了 `adb logcat -s "PlayerActivity>onPrepared"`
/ `"PlayerActivity>getExtras"` / `"PlayerActivity>checkStall"`，全工程 269 处 `Logu.*` 调用点都吃这套约定，
`api/NoteApi.java` 的 `adb logcat -s BiliNote` 也是同一思路。改成统一 tag 会把它们一次性作废。

所以「一次抓全」要用**按进程过滤**，而不是改 tag：

```bash
PKG=com.RobinNotBad.BiliClient.coexist
adb logcat --pid=$(adb shell pidof -s $PKG)      # 不看 tag，本进程全量（推荐）
adb logcat -s "PlayerActivity>onPrepared"        # 单点诊断，沿用既有契约
adb logcat -s "PlayerActivity>getExtras" "PlayerActivity>checkStall"
```

> 注意：工程里还有 38 个文件、110 处直接调 `android.util.Log`（tag 五花八门，`"debug"` 就占 51 处）。
> 这些**不受 `Logu` 开关控制、也不会落盘**，属于尚未收口的历史遗留；要连它们一起看就用
> `--pid` 那条，或 `adb logcat | grep`。
>
> 真正不依赖 adb 连接的做法是读落盘日志（见 §5）——那才是 agent 事后取证的主力手段。

### 4.6 验证内存缓冲与脱敏

既有的 `log` / `logs` 命令读的是**落盘文件**，而落盘只在 Agent 测试模式下才有。**内存日志缓冲**（`Logu` 的环形缓冲）是 release 包唯一能拿到历史日志的那条路——它**刻意不落盘**。所以「缓冲到底有没有在累积、脱敏有没有生效」光看落盘文件是验证不了的，要看内存缓冲就得用 `logring`。

```bash
# 直接读当前缓冲状态，返回形如：
#   OK: ringChars=N，M 行；尾部 X 字符：<内容>
adb shell am broadcast -n <包名>/com.RobinNotBad.BiliClient.debug.DebugBridge \
  --es token rebili-agent-bridge --es cmd logring

# 先以 W 级写一条日志再取快照，专门用来验证脱敏链路
adb shell am broadcast -n <包名>/com.RobinNotBad.BiliClient.debug.DebugBridge \
  --es token rebili-agent-bridge --es cmd logring \
  --es inject 'verify SESSDATA=SECRET123 access_key=xyz normal=keep'
```

`inject` 那条的实测结果里出现 `verify SESSDATA=*** access_key=*** normal=keep`——敏感值被替换、无关键值原样保留，且 caller tag 仍是 `DebugBridge>logRing`（`Logu` 的 tag 契约没被破坏，见 §4.5）。

> 内存缓冲有 `RING_MAX_CHARS`（65536 字符）上限，超限从头部逐条淘汰，所以回显会同时报出 `ringChars` 与行数——两者都是「早先的日志看不到」的成因（一个是不在累积、一个是正常滚动），分开看才分得清。

### 4.7 验证崩溃上报整条链路

用 `start` 拉起 `CatchActivity` 只能验证**接收端**（`ring_log` extra 读得对不对）；而 `ErrorCatch` 里「崩溃瞬间取 `Logu.ringSnapshot()` 塞进 Intent」那行代码**根本不会被执行**。要验整条链路，得造一个真实的崩溃：

```bash
adb shell am broadcast -n <包名>/com.RobinNotBad.BiliClient.debug.DebugBridge \
  --es token rebili-agent-bridge --es cmd crash
```

它用 `Handler(mainLooper).post { throw RuntimeException(...) }` 在**主线程**抛真实异常——刻意脱离 `onReceive` 调用栈，让异常真正走到 `Thread.setDefaultUncaughtExceptionHandler`。**主进程会立刻崩溃**，所以：

- 返回值基本看不到（进程马上就死，`bridge-last.txt` 也来不及写）；
- 预期现象：`logcat -b crash` 出现该 `RuntimeException`、前台变成 `CatchActivity`、`ps` 里出现 `<包名>:error_activity`；
- **验收要看服务端**：崩溃表新增一条，其 `log` 字段应当是「页面轨迹 + 分隔行 + 应用日志」三段结构（内存缓冲见 §4.6）。

> 实测（26.10.16）：触发后服务端收到 `log` 11140 字符 / 45 行，分隔行 `--- 应用日志（内存缓冲，最近若干行）---` 位于中间，前后两段各就各位，且正则自检无明文凭证。

### 4.8 离线验证 DASH 音视频重封装

完整下载要登录 + 选 1080P 才走得到合并分支；而最容易出错的是容器层（MediaExtractor 读分片 MP4 + MediaMuxer 按 sampleTime 归并），拿两条现成的 B 站 m4s 就能单独验：

```bash
adb shell mkdir -p /sdcard/Android/data/<包名>/files
adb push video.m4s /sdcard/Android/data/<包名>/files/v.m4s
adb push audio.m4s /sdcard/Android/data/<包名>/files/a.m4s
adb shell am broadcast -n <包名>/com.RobinNotBad.BiliClient.debug.DebugBridge \
  --es token rebili-agent-bridge --es cmd merge \
  --es video /sdcard/Android/data/<包名>/files/v.m4s \
  --es audio /sdcard/Android/data/<包名>/files/a.m4s

# 返回示例（26.10.25，Android 14 x86_64 模拟器，匿名 360P 样本）——
# result=OK(0) cost=3381ms
# in video=3867429B audio=1265842B
# out=.../debug_merge.mp4 exists=true size=5167200B
# progress=0,20,50,90
# tracks=2
#   #0 mime=video/hevc samples=2992 durationUs=199269000 lastSampleUs=200029811
#   #1 mime=audio/mp4a-latm samples=8584 durationUs=199319750 lastSampleUs=199296439
```

判据：`tracks=2`、两条轨的 `samples` 与源文件一致、`durationUs` 接近视频时长；`progress` 里能看到整十档回调说明合并阶段在推进进度条。**返回 `OK` 但 `tracks<2` 或采样数为 0，就是容器层回归**（产物会被删除，调用方保留双文件）。若 `out` 落在 `/sdcard/Android/data/...` 报文件不存在，可把样本先 `run-as` 拷进应用私有目录（`/data/data/<包名>/files/`）再传绝对路径。

### 4.9 验证「文件夹合集」分 P 起播与外部音轨（不需要登录）

「点文件夹里的第 2 条却从第 1 条开始播」「未合并的 DASH 缓存播起来没声音」这两类问题，**不需要真机、也不需要登录**——`adb root`（MuMu 自带）就能往 `Android/media/<包名>/` 造夹具，媒体用 ffmpeg 现造：

```bash
PKG=com.RobinNotBad.BiliClient.coexist
R=/sdcard/Android/media/$PKG

# ① 造两条「只有视频轨」的 mp4 + 独立 m4a（这是未合并 DASH 缓存的形态）
ffmpeg -y -f lavfi -i "color=c=red:s=320x240:d=6"  -an -c:v libx264 -pix_fmt yuv420p a.mp4
ffmpeg -y -f lavfi -i "sine=frequency=440:duration=6" -c:a aac b.m4a

# ② 目录约定（见 DownloadPathSpec）：每个视频一个目录，双文件就在目录里，
#    .video_meta.json 的 folderName 决定它归属哪个文件夹
adb shell mkdir -p $R/第一个视频 $R/第二个视频
adb push a.mp4 $R/第一个视频/video.mp4 ; adb push b.m4a $R/第一个视频/audio.m4a
adb push a.mp4 $R/第二个视频/video.mp4 ; adb push b.m4a $R/第二个视频/audio.m4a
# .folders_info.json -> [{"name":"测试合集","createTime":0,"videoTitles":["第一个视频","第二个视频"]}]
# .video_meta.json    -> {"folderName":"测试合集","title":"…","downloadType":"video"}
# .quality            -> 64（只影响列表上显示的清晰度标签）
```

**两个必踩的坑**：

1. **JSON 不能带 BOM**。PowerShell 的 `Out-File -Encoding utf8` / `Set-Content` 会加 BOM，`JSONArray` 解析直接抛异常且被 `catch` 吞掉 → 现象是「文件夹列表空、未分类 0 个」。用 Python 写（`io.open(..., encoding="utf-8")`）或 `[IO.File]::WriteAllText`。
2. **必须装本次构建的包**。改完代码只 `assembleDebug` 不 `adb install` 的话，测的是设备上那个旧包 —— 26.10.29 就因此得出过「修复无效」的错误结论。装完用 `pm dump <包名> | grep -i <本次新增的类名>` 确认一下。

判据（都看 logcat，不需要截图）：

- 点文件夹里**排第二**的那条 → `PlayerApi>jumpToPlayer: 视频地址>.../<那条>/video.mp4`。为了排除「列表顺序恰好相同」的假通过，**两条都要点一次**。
- `PlayerActivity>initAudioTrack: AudioTrack>主文件轨道=[VIDEO]，外部文件可用=true，选择=EXTERNAL` → 紧接 `已启用唯一的外部音频轨道`。
- 打开选集面板切到另一 P → `PlayerActivity>setDisplay: url>` 变成另一条、`initAudioTrack` 再走一遍。
- 想确认真的打开了哪个音频文件：**别查 `/proc/<pid>/fd`**——`android.media.MediaPlayer` 的 fd 在 mediaserver 侧，应用进程里看不到；以 `选择=EXTERNAL` + 文件可读为准，音质差异用不同频率的 `sine` 造样本靠耳朵区分。

---

## 5. 日志与落盘

| 文件 | 内容 | 位置 |
| --- | --- | --- |
| `rebili.log` | `Logu` 的全部输出（`MM-dd HH:mm:ss.SSS 级别 调用者 \| 内容`，调用者即 logcat 的 tag） | 应用专属外部目录 `logs/` 下 |
| `http.log` | 每次 HTTP 请求一行：`方法 脱敏URL -> 状态码 (耗时ms) \| req=… \| res=…` | 同上 |
| `bridge-last.txt` | 最后一条调试桥命令的结果 | 同上 |
| `bridge-http-response.txt` | 最后一条 `http` 命令的响应体全文 | 同上 |

> 上表都是**落盘**通道（只在 Agent 测试模式下才有）。另有一条**内存环形缓冲**——不落盘、release 下也能拿到历史日志，用 `--es cmd logring` 读（见 §4.6）。

**目录位置**：优先 `getExternalFilesDir("logs")`（即 `/sdcard/Android/data/<包名>/files/logs`，无需任何存储权限），外部存储不可用时回退内部 `files/logs`。`ping` 命令会报出实际路径。

**三个刻意的设计**：

- **异步落盘**：业务线程只做一次 O(1) 入队，真正的 I/O 在名为 `rebili-log-writer` 的守护线程里。`Logu.d` 会被 UI 线程调用，同步写文件等于把卡顿直接带给用户。队列容量 4096，满了丢弃并计数（`ping` 会报 `droppedLogs=`）。
- **单文件 2MB 上限**：超限时砍掉前半、只留后半。手表存储小，而 agent 关心的永远是「最近发生了什么」。
- **Cookie / `access_key` / `csrf` 永不落盘**：HTTP 追踪里 URL 查询串的敏感参数替换成 `***`，沿用审计 S2 的「只记 Cookie 键名、不记值」。`dump` 对敏感键只报长度，确实要某个值用 `get --es key <键>` 精确取。
- **WBI 指纹 blob 被压掉**：`dm_img_list` / `dm_img_inter` / `dm_img_str` / `dm_cover_img_str` 这几个查询参数的值替换成 `…`（键名保留）。它们是反爬用的浏览器指纹，单个上千字符且对排障无用——实测不过滤时一行追踪日志 2200+ 字符，跑几个页面 `http.log` 就 74KB，几十次加载就把 2MB 的滚动窗口冲掉。URL 另有 600 字符硬上限兜底。

**实测体量参考**（Android 12 模拟器，跑完约 12 个页面）：`rebili.log` ≈ 35KB、`http.log` ≈ 17KB。
去噪与限长之前 `http.log` 是 75KB（单行 2200+ 字符），**压掉了约 4 倍**。按 2MB 上限算，约上百次页面加载触发一次滚动，够用；要长时间跑批量用例，记得中途 `logclear`。

**追踪能直接暴露风控**——这是它最实用的地方。实测抓到的真实一条：

```text
GET https://api.bilibili.com/x/...&dm_img_list=…&dm_img_inter=…&dm_cover_img_str=… -> 412 (43ms)

  | res=<!DOCTYPE html> … <title>出错啦! - bilibili.com</title>

```

改造前「风控返回 HTML」「`code=-412`」「请求根本没发出去」在日志里长得一模一样，只能靠猜；
现在状态码、耗时、响应体三者齐备，一眼可辨。

> **未登录 + 模拟器环境下接口会被风控**（上面那条就是）。依赖真实接口的用例要么先在应用里登录，
> 要么把「返回 `-412` / doctype HTML」当作预期结果来断言，别误判成应用缺陷。

---

## 6. 已知限制与坑

下表**每一条都是实测踩出来的**（真机 Android 12 为主，模拟器条目已注明），不是推测。

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| **`start` 返回 `OK`，但 `top` 不是目标页** | ① 教程页盖在上面（见下一行）；② 只加 `NEW_TASK` 时若目标页是某个已存在任务的栈根，系统复用任务而不新建实例，栈顶仍是别的页 | 已修：`start` 默认叠 `CLEAR_TOP`；**并且永远用 `top` 复核**，别信 `OK` |
| **教程页把刚拉起的页面盖住**（全新安装必现） | `BaseActivity.autoTriggerTutorial()` 在 `Tutorials.forPage(javaClass)` 有未读教程时启动 `TutorialPagerActivity`；GUIDE 类还要求「翻到最后一页 + 停留 3 秒」才能点「已阅」 | 先跑 `--es cmd notutorials`（§2.2 第 ② 步） |
| `start` 命令完全没反应、也不报错 | Android 10+ 后台启动 Activity 限制，启动被**静默丢弃** | `adb shell cmd appops set <包名> SYSTEM_ALERT_WINDOW allow`；`ping` 的 `overlayGranted=` 可确认 |
| `appops: inaccessible or not found` | Android 12 起 `/system/bin/appops` 不在 shell PATH | 用 `cmd appops`（见 §2.2） |
| `extras` 里的 JSON 解析失败 | `adb shell` 把命令串交给设备端 shell 重新解析，双引号被剥掉 | 改用 `key=value` 写法（推荐），或转义成 `'{\"aid\":2}'` |
| `am broadcast` 没有 `data="…"` 输出 | 部分 ROM 的 `am` 不转发 receiver 结果（**Android 12 实测正常**，属兜底） | 读 `logs/bridge-last.txt`；`adb shell cat` 或 `adb pull`，**别用 `run-as`**（见 §2.3） |
| 接口大量返回 `-412` / doctype HTML | 未登录或模拟器环境触发风控，**不是应用缺陷** | 先在应用里登录；或把风控响应作为预期结果断言（见 §5） |
| 广播报 `SecurityException` / 没有反应 | 发送方不持有 `DUMP` 权限（少见，个别精简 ROM；**Android 12 模拟器实测 shell 持有 DUMP，正常**） | 用 `adb root`；或 `adb shell run-as <包名> am broadcast -n <包名>/...`（同 uid 不受导出限制，**此路径未实测**） |
| `http` 命令偶发超时 | 刻意把超时压到连接 5s / 读 8s 且不重试——它跑在广播的 `goAsync()` 窗口里，按默认的 8s+16s×5 次重试会直接把广播拖成 ANR | 属预期；长耗时请求不要走这个命令 |
| 日志里 `Log.d` 之类的内容不见了 | 那是 38 个文件里的裸 `android.util.Log`，不走 `Logu`、不落盘 | 用 `adb logcat` 全量看，或后续收口 |
| 装了 `standardDebug` 后正式版没了 | 包名相同、签名不同，安装时覆盖/卸载了正式版 | 改用 `coexistDebug`（见 §2.1） |
| 模拟器里应用报 `Unable to resolve host "gitee.com"`，`adb shell ping 10.0.2.3` 也不通（**模拟器条目**） | AVD 冷启动后 Wi-Fi 默认没连上（`wlan0` DOWN、`ip route` 为空），**不是应用缺陷**；26.10.25 Wear 模拟器实测 | `adb shell svc wifi enable`，再 `adb shell cmd wifi connect-network AndroidWifi open`（网络名用 `cmd wifi list-networks` 确认）；`ip addr` 看到 `wlan0` 拿到 `10.0.2.16/24` 即通 |
| **装好的应用不在主屏、`screencap` 也抓不到**（**MuMu 6.0 条目**，26.10.27 实测） | MuMu 每次启动都把这个包丢到一个**新建的副屏**（`dumpsys window displays` 里 `mDisplayId` 逐次递增，如 14/15/…/22；`dumpsys SurfaceFlinger --display-id` 里叫 `mumuscreen013/014/015`），而该 build 的 **`screencap -d <id>` 对任何 display（含 0）都回 `Failed to take screenshot. Status: -2`**（`adb root` 之后一样），`input` / `uiautomator dump` 不带 `-d` 又只认主屏 | **不截图，改看控件树**：①`dumpsys window displays \| grep -E 'mDisplayId\|mCurrentFocus'` 拿到应用所在 displayId；②`uiautomator dump --display <id> /data/local/tmp/u.xml` + `adb pull`，能拿到**全部文字、content-desc 与 bounds**（比截图更利于断言）；③`input -d <id> tap/swipe`；④**长按**用 `input -d <id> motionevent DOWN x y` → 睡 1s → `motionevent UP x y`（`swipe x y x y 1200` 会被当成滚动，别用）。26.10.27 用这套验过一个需要长按 + 多级弹窗的功能链路 |
| `uiautomator dump` 里看不到 Toast | Toast 不在无障碍树里 | 读 `adb logcat`：本仓库的 `MsgUtil` 会把提示打出来（`I MsgUtil>showMsg: <原文>`），断言日志即可 |

---

## 7. 设计原则（为什么是这些改动）

改 debug 版本时遵循的四条原则，后续加调试能力时建议沿用：

1. **只进 debug 源集**。`DebugBridge` 的类与清单声明都在 `app/src/debug/`，Release 编译时**这个类根本不存在**，R8 也无需靠规则去 strip。安全边界是「发布包不含它」，而不是「它自己判断是不是 debug」。
2. **外部可达必须带权限**。导出的组件挂 `android:permission="android.permission.DUMP"`：DUMP 是签名级权限，普通第三方应用拿不到（同设备上的恶意应用读不到登录态），而 adb shell 持有（`dumpsys` 就靠它），本应用自己天然放行。比在 APK 里埋一个可被反编译读出的 token 强得多。
3. **零侵入**。Agent 模式默认关闭；`Logu.LOG_TO_FILE` / `NetWorkUtil.HTTP_TRACE` 都只在 Agent 模式打开时才为 true，且赋值处被 `BuildConfig.DEBUG`（编译期常量）折叠，**Release 包的行为与改动前逐字节一致**。
4. **可观测性优先于可操作性**。宁可多给一条 `log` / `trail` / `dump` 命令，也不要让 agent 靠「点了之后猜」。反过来，凡是「改完要重启」的开关都改成了立即生效——重启意味着上下文丢失与额外一轮等待，在自动化里代价极高。
5. **不许动既有的诊断契约**。`Logu` 的 tag（= `类名>方法名`）、`NoteApi` 的 `BiliNote` 之类，都是被文档和源码注释写死的排障入口，改一个就会让一批既有指令静默失效。26.10.14 改 `Logu` tag 时踩过这个坑（已回退）：**动手前先 `grep 'logcat -s' docs/ app/src/`**。新增可观测性可以**加**通道（落盘、`--pid` 过滤、调试桥命令），不要**换**掉已有的。

---

## 8. 相关源码位置

| 文件 | 说明 |
| --- | --- |
| `app/src/debug/java/com/RobinNotBad/BiliClient/debug/DebugBridge.kt` | 调试桥本体（命令分发） |
| `app/src/debug/AndroidManifest.xml` | 调试桥与 `TestActivity` 的导出声明 + DUMP 权限 |
| `app/src/main/java/com/RobinNotBad/BiliClient/util/Logu.java` | 异步落盘、**内存环形缓冲**（`logring` 读的就是它）与尾部读取（**logcat 的 tag 刻意未动**，见 §4.5） |
| `app/src/main/java/com/RobinNotBad/BiliClient/util/LogSanitizer.kt` | 日志文本的 `键=值` 脱敏（`logring --es inject` 可现场验证，见 §4.6） |
| `app/src/main/java/com/RobinNotBad/BiliClient/util/NetWorkUtil.java` | `HTTP_TRACE` 与 `HttpTraceInterceptor`、URL 脱敏 |
| `app/src/main/java/com/RobinNotBad/BiliClient/BiliTerminal.kt` | `AGENT_TEST_MODE`、`applyLogSwitches()`、当前前台 Activity |
| `app/src/main/java/com/RobinNotBad/BiliClient/activity/SplashActivity.kt` | Agent 模式下跳过 UETool 悬浮窗 |
| `app/src/main/java/com/RobinNotBad/BiliClient/util/SettingsKeys.kt` | `DEV_AGENT_MODE` / `DEV_LOG_FILE` |
| `app/src/main/java/com/RobinNotBad/BiliClient/util/MediaMerger.kt` | DASH 双文件重封装（`merge` 命令直接调它；下载流程在 `DownloadService.mergeDashPair` 里用，见 §4.8） |
