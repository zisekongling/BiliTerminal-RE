# 兄弟分支功能对比报告（2026-11-06）

> 对比我方 `main`@`1c404c3`（RE:哔哩终端 / ReBiliClient，26.11.06.1）与三个兄弟分支：
> [jiabenguiyin/BiliTerminal-Revival](https://github.com/jiabenguiyin/BiliTerminal-Revival)（下称 **Revival**）、
> [PianoEthan/BiliTerminal](https://github.com/PianoEthan/BiliTerminal)（下称 **PianoEthan**）、
> [cyq114514/Re-BiliTerminal](https://github.com/cyq114514/Re-BiliTerminal)（下称 **cyq**）。
>
> 目的：回答两个问题——**对方有哪些我们没做的用户可见功能**、**对方在共有的模块里有哪些做得更好的实现**；然后把结论收敛成可直接排期的移植清单与实现批次。
>
> 三份逐条证据的分报告见 [`docs/review/forks/revival.md`](forks/revival.md)、[`docs/review/forks/piano-ethan.md`](forks/piano-ethan.md)、[`docs/review/forks/cyq.md`](forks/cyq.md)。本报告负责汇总、交叉验证、定优先级与实施计划；**行号级证据以分报告为准**。

---

## 0. 一页结论

### 0.1 三个分支分别是什么

| 分支 | 上游基线 | 定位 | 相对我方的状态 |
| --- | --- | --- | --- |
| Revival | BiliClient 2.9.5-fix | 手表/旧机维护，纯 Java，minSdk 14 | 「**功能补全型**」：播放器内核、连播队列、存储管理、关系管理比我们完整 |
| PianoEthan | BiliClient 2.9.8 | 旧设备极简，纯 Java，minSdk 14 | 「**单一功能型**」：只有一个成体系的增量——**主题引擎**（M3 动态色板 + `.btheme` 主题包） |
| cyq | BiliClient 2.9.8 | 低配/圆屏手表，纯 Java，minSdk 19，可共存 | 「**细节打磨型**」：播放器交互、社交容错、隐私伪装的一批小功能与修复 |

### 0.2 我方不是「功能更少的小弟」

【事实】按分报告的量化口径：我方主源码 354 `.kt` + 141 `.java`、91.5k 行、JVM 单测 134 文件 / 1,429 `@Test`；
Revival 纯 Java 63k 行、415 `@Test` + 60 个仪器化测试；PianoEthan 纯 Java、61 `@Test`；cyq 纯 Java、**0 测试**。

我方在**功能面宽度**（外观主题、伪装、短视频、笔记、投票、教程系统、下载工程、订阅、反馈、圆屏与滚轮、ROM 残缺防御、更新包签名校验、日志脱敏、standard/coexist 双 flavor）上明显领先，三个分报告都逐条给出了「我方更强、不要重复造」的反证。对方领先的是**若干具体模块的完成度**，集中在：取流容错、弹幕内存模型、连播队列、播放器焦点、关系/内容管理、存储清理、主题引擎。

### 0.3 最值得做的事（详见第 7 章批次）

**S 级（低风险、单文件为主，建议第一批全做）**：

1. `FileUtil.readString` 文件流泄漏（PianoEthan 已修，我方未修）。
2. playurl 双接口兜底（WBI → legacy；番剧 v2 → v1，Revival）。
3. 播放器音频焦点（cyq 1.2.9 已做，我方 grep 为 0）。
4. 私信发表情（我方渲染链路已就绪，只缺输入入口，cyq）。
5. 性别 / IP 属地 + 互关徽章（同一份 `x/space/wbi/acc/info` JSON，纯解析 + 展示，cyq）。
6. 短视频接入无极调音（复用我方已测的 `PlayerVolumePolicy`，cyq）。
7. 存储空间页（我方 `FileUtil.clearCache()` 是**零调用者的死代码**，用户没有清理入口，Revival）。
8. 更新检查加 `callTimeout` 总闸（cyq）。
9. 弹幕数量保护（>N 条自动不加载、可手动开，Revival）。
10. 历史记录多选删除（Revival）。

**M 级（第二、三批）**：弹幕窗口加载与磁盘缓存、连播队列（模式/耳机切歌/token 恢复）、移动数据/WiFi 独立画质、番剧 v2 取流与每集进度、评论三层兜底、卡片长按菜单、主题背景图/封面取色、页面栈恢复、崩溃本地队列。

**不建议现在做**：ExoPlayer 统一 DASH（我们 26.10.31 已试过并在 26.11.01 回退，见 §7.5）、SAF 外置 SD、差分更新、音频独立域、桌面图标伪装、`activity-alias` 类改造。

### 0.4 一个与本报告无关、但建议先处理的隐患

【事实】我方 `activity/player/PlayerActivity.kt:21` 直接 `import android.media.session.MediaSession`（**API 21** 类），并把 `MediaSession` 作为**字段类型**（`:230`），而 minSdk 是 19。
【事实】PianoEthan 曾因同一写法在 Android 5.0 以下「`PlayerActivity` 类加载崩溃」并专门修复（提交 `5c480df`：字段类型改为 `Object`、真正使用处再强转 + 局部变量）。
【推测】在 Android 4.4（Dalvik/旧 ART）上，类验证阶段解析该字段类型可能触发 `NoClassDefFoundError`；我方线上崩溃数据里**没有**该堆栈（也没有 API 19 播放页实测记录），所以是「潜在风险」而非已复现缺陷。
**建议**：按对方形态做一次 S 级隔离（字段类型 `Any?` + SDK 门禁内局部变量），并在 API 19 模拟器跑一次播放页冒烟。这条不占用功能排期，但比本报告任何移植项都便宜。

---

## 1. 方法与口径

### 1.1 对比对象与基线

| 项 | 我方 | Revival | PianoEthan | cyq |
| --- | --- | --- | --- | --- |
| 仓库 | `zisekongling/BiliTerminal-RE` | `jiabenguiyin/BiliTerminal-Revival` | `PianoEthan/BiliTerminal` | `cyq114514/Re-BiliTerminal` |
| 对比 commit | `1c404c3`（main） | `60d4559`（v1.2.4，11-04） | `f24a392`（v3.1.3-Qx，11-01） | `567450c`（v1.3.0，11-06） |
| 版本号 | 26.11.06.1 / 2611061 | 1.2.4 / 2026110401 | 3.1.3-Qx / 20261101 | 1.3.0 / 20261106 |
| 上游基线 | 上游 2.7.0 + Kotlin 重写 | 上游 2.9.5-fix | 上游 2.9.8 | 上游 2.9.8 |
| 语言 | Kotlin 354 + Java 141 | 纯 Java 384 | 纯 Java 317 | 纯 Java 403 |
| minSdk / targetSdk / compileSdk | 19 / 34 / 34 | 14 / 26 / 33 | 14 / 26 / 33 | 19 / 26 / 33 |
| 包名 / 可共存 | `…BiliClient` + coexist flavor | 同包名，不可共存 | 同包名，不可共存 | `…BiliClient.re`，可共存 |
| ABI | arm64 / v7a / x86 | v7a / x86 | v7a / x86（mips 空占位） | v7a / x86 |
| JVM 单测 | 134 文件 / 1,429 `@Test` | 71 文件 / 415 `@Test`（+60 仪器化） | 5 文件 / 61 `@Test` | 0 |
| 提交数 / 最近提交 | 583 / 11-06 | 13（压缩历史）/ 11-04 | 60 / 11-01 | 123 / 11-06 |
| 许可 | GPL-3.0 | GPL-3.0 + UI CC BY-NC-SA | GPL-3.0（+ vendored MCU Apache-2.0） | GPL-3.0 |

### 1.2 怎么比的

1. **全量克隆**到仓库外的工作目录（`D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\`），不污染我方工作树。
2. **git blob 哈希逐文件比对**：三仓库无共同 git 历史（都是各自压缩导入），用 `git ls-tree` 的 blob SHA 得到 `only-fork` / `only-ours` / `modified` 三张清单。
3. **目录级 `git diff --no-index --numstat`** 定位改动量，但明确降噪：我方 Kotlin、对方 Java 的同名文件必然全部「modified」「only-fork」，**不能按路径判功能有无**（分报告各自记录了这一点）。
4. **语义阅读 + 逐条核实**：先按域（播放器/弹幕/社交/存储/外观/更新…）读对方代码，再回我方当前代码核实「已/无/更弱」，行号用 grep/read 复核。
5. **交叉验证**：三份分报告 + 本报告对 12 条关键结论做了独立抽查（附录 C），抽查不一致的条目以抽查结果为准。

### 1.3 证据与难度口径

- 每条结论标 **【事实】**（代码/命令可复核）或 **【推测】**（代码推得、未真机验证）。线上用户可感知的结论默认【推测】。
- 难度：**S** ≤ 0.5 人日且不动架构；**M** 1–3 人日或跨 2–4 个文件；**L** > 3 人日或需新依赖/新存储抽象/新播放域。
- 「用户价值」按**目标设备口径**评估：手表优先、旧安卓、低内存、圆屏、无键盘（见 `docs/architecture-map.md` §1.4）。

### 1.4 血统与合规

- 三仓库与我方**同源不同血统**（都源自上游 BiliClient，但 git 对象库互不相干），全部 GPL-3.0，与我们的许可兼容。
- 但**移植 ≠ 复制**：对方是纯 Java、大量中文硬编码在 Activity 里，我方是 Kotlin + 资源化 + 策略类分层。移植一律按我方架构**重写**，文案进 `res/values/strings.xml` / `UiText`。
- 移植来源应在提交信息中注明（例：`feat(player): 音频焦点（参考 cyq114514/Re-BiliTerminal 1.2.9 的实现语义）`），需要时在 `THIRD_PARTY_NOTICES.md` 登记。GPL 项目之间互相借鉴只要保留版权与许可声明即可。
- 我方 `private/` 私有功能目录与本任务无关：三份分报告均未读取、未引用；本报告也不涉及。

### 1.5 与 10-24 旧报告的关系

`docs/review/compare-with-re-biliterminal-261024.md` 已经对 cyq 1.2.3 做过 1080 行全量对比（其中 25 条功能类缺口）。本次不复述：cyq 分报告用我方 **26.11.06.1** 逐条复核后，**已补齐 4 条、仍缺 19 条、部分 1 条、不适用 1 条**，并补充了 1.2.5→1.3.0 的增量。旧报告里「安全 / 兼容 / 规范 / 健壮性」的通用结论不在本报告范围。

### 1.6 用户侧信号（用于排序，不参与功能判定）

后台控制台待处理反馈里反复出现的主题（转述，不引用原文）：**下载/缓存失败**（含「另一个 RE 分支能下，这个不能」）、**短视频弹幕开关不生效**、**跳过片头片尾误判**、**图片拖动卡顿**、**缓存排序/批量移动**。
这说明上表里「取流/下载健壮性、播放器一致性」类移植项的收益不是纸面收益；但反馈本身是缺陷修复，不在本报告的功能清单内计数。

---

## 2. 四个分支画像

### 2.1 一句话画像

- **我方**：功能面最宽的 Kotlin 主线，工程化最好（1429 个 JVM 单测、双 flavor、资源化、隐私与安全基线），播放器与下载在「能覆盖多少场景」上领先，但在播放器内核一致性、弹幕内存模型、连播队列、存储可观测性上欠账。
- **Revival**：上游 2.9.5 的 Java 维护线。**用户可感知的完整性最好**——ExoPlayer 单时间轴 DASH、完整连播队列、SAF 外置 SD、存储清理页、设备档位、举报/拉黑、创作中心稿件管理、历史多选删除。但很多「独有功能」可能来自上游 2.7→2.9.5 的增量（对方历史仅 13 个压缩提交，无法区分归属，分报告已标【推测】）。
- **PianoEthan**：主增量是**主题引擎**（M3 动态色板、`.btheme` ZIP 容器 + 白名单校验 + 原子安装、背景图、播放器封面取色）；其余 60 个提交以 API 14–18 兼容补丁与 Gson 迁移为主，另有少量上游修复与个人资料编辑（release 日志复查后修正，见 §9.2），均不构成对我方的功能增量。
- **cyq**：节奏最快、细节最密。1.2.9 上线「听歌域」（音频页/队列/歌词），1.3.0 补私信表情、卡片长按菜单、性别/IP 属地、互关徽章、分享得经验；同时它把自己 1.3.0 的严重崩溃（横滑必崩）当补丁发——**零测试**的代价。

### 2.2 我方领先项（不必再学，避免重复造轮子）

分报告逐条给了证据，这里只列结论：

- 表冠/旋钮：我方全局 `ui/widget/RotaryEncoderSupport.kt`，对方是控件级三份同构代码。
- 评论带图、收藏 v3、订阅体系：我方均有且更完整。
- 播放器 `destroyed` 守卫体系、弹幕样式（字号/透明度/行数/速度/合并/重叠）、番剧片头片尾与切季定位、从历史定位上次观看的集。
- 搜索风控降级（`RiskControlException` + 排序按钮禁用态）、图片 webp 兼容开关、消息未读与通知、下载工程（分片/断点/原子替换/WakeLock）。
- 隐私与安全：导出组件仅 2 处（对方 22 处）、`allowBackup=false`、日志脱敏、更新包签名校验（对方更新链**没有**签名/包名校验）、未引入第三方 BaaS 上报。
- 工程化：134 个 JVM 测试文件、双 flavor、版本一致性校验、文档体系。

---

## 3. 各分支最亮的增量

### 3.1 Revival（上游 2.9.5 代）：播放器与「完整度」

| # | 增量 | 用户价值 | 我方现状 | 成本 |
| --- | --- | --- | --- | --- |
| 1 | **ExoPlayer 单时间轴 DASH** | 1080P+ 不再「视频一路、音频一路」，拖动/倍速/暂停不漂移 | 我方是 ijk + 外挂 `MediaPlayer` 双播放器，靠偏差校正补偿（结构性方案，见 §7.5 的试错史） | L |
| 2 | **完整连播队列**（UP 主投稿 / 合集 / 收藏夹 / 缓存 + 顺序/倒序/随机/单曲 + 耳机切歌 + token 恢复） | 一个播放页连看整个合集，耳机键切上/下一条 | 我方只有「虚拟合集」= 收藏夹/缓存整夹顺序连播，状态塞在 Activity 里 | M |
| 3 | **SAF 外置 SD 缓存**（file/SAF 双后端、下载库 v5、`content://` 播放） | 缓存写可移动卡 | 我方全链路文件路径，无 DocumentFile | L |
| 4 | **存储空间页**（分类统计 + 有界清理） | 一眼看到图片/临时/更新包占用并清理 | `util/FileUtil.java:49` `clearCache()` 已实现但**全仓无调用者**（死代码） | S |
| 5 | **设备档位 DeviceProfile**（COMPAT/BALANCED/HIGH_END 接管取流/播放器/软解/弹幕上限） | 低配表自动降载 | 我方 `util/PerformanceManager.kt` 只影响图片/列表，**取流恒 `fnval=4048`** | M |
| 6 | playurl 双接口兜底（WBI → legacy、番剧 v2 → v1） | 接口抖动时仍能播 | 我方 WBI 单路径，失败即抛 | S |
| 7 | 弹幕窗口加载（360s 一段、有界内存）+ 数量保护（>5000 不自动加载） | 长视频/低内存表不卡死 | 我方一次性取全部分段全量解析 | M/S |
| 8 | 互动视频进度/分支状态持久化 | 退出后回到同一分支 | 我方互动视频实现完整，但 `currentEdgeId` 只在内存/Intent | S |
| 9 | 视频/动态举报、关注/拉黑、创作中心本人稿件管理、历史多选删除 | 内容与关系管理闭环 | 举报/拉黑/稿件管理无；历史只有单条删除 | S/M（写操作需拍板） |
| 10 | 移动数据 / Wi-Fi 独立默认画质 | 流量降档、Wi-Fi 高清 | 只有单一默认清晰度 | S |
| 11 | 本人弹幕边框、强制滚动弹幕、弹幕窗口、直播弹幕昵称/时钟对齐 | 弹幕可用性 | 昵称**已有**（`PlayerDanmuClientListener.kt:161`）；「强制滚动」是**有开关不生效的死开关**；其余无（§9 有修正） | S |
| 12 | 长按后上/下划切上/下一条视频 | 竖屏浏览省一次回列表 | 无（长按只有倍速） | S |
| 13 | 大图分块解码（超大图/长图/动图/失败态） | 长图不 OOM | 整图 PhotoView + Glide | M |
| 14 | 差分更新（BSDIFF40） | 弱网省流量 | 全量 APK（有签名校验） | L |

### 3.2 PianoEthan：只有主题引擎，但成体系

| # | 增量 | 用户价值 | 我方现状 | 成本 |
| --- | --- | --- | --- | --- |
| 1 | **`.btheme` 主题包导入**（ZIP format 1、条目白名单、8MB/12 条目/64KB JSON/6MB 单图限额、原子替换、卸载回退） | 可装第三方主题、可分享 | 无（7 套编译期主题） | M（依赖 2/3） |
| 2 | **种子色 → M3 色板**（Hct + Content 方案、blend/contrast、19 键 override、深浅两套） | 一套主题无限派生 | 7 套写死表 + 7 张亮色覆盖表 | M（仅代码侧颜色）/ L（XML 也跟随） |
| 3 | **主题背景图**（fit / scrim / 深浅各一张 / 采样解码 + SoftReference） | 观感提升最大的单点 | 只有「弥散光晕」「玻璃质感」两种程序化装饰 | S–M |
| 4 | **播放器封面取色**（进度条/高能曲线随封面变色，范围只放开播放器） | 播放页个性化 | 进度条固定主题色 | M（只需 MCU 的 hct/quantize/score/palettes 子集，**无 Java 8 库依赖**） |
| 5 | 主题设置独立页（预览图列表、长按两次删除、两路导入） | 主题可管理 | 无 | S–M |
| 6 | `FileUtil.readString` 关闭流（提交 `5267f8e`） | 防止 fd 泄漏 | 我方未关闭（见 §5.1） | S |

分报告结论（本报告认可）：两套外观体系是**互斥范式**——对方「1 套主题 + 85 处程序化遍历染色」，我方「86 个 style + 329 个色 + 78 处 `?attr/`」。整搬是 XL，正确路线是**切片**：先背景图/封面取色，再种子色，最后才考虑 `.btheme`。另外对方**没有导出/App 内生成**主题包的能力（样例靠 Python 脚本），我方若顺手做「导出当前主题为 `.btheme`」是成本极低的差异化。

### 3.3 cyq 1.3.0：增量集中在交互与社交细节

| # | 增量 | 用户价值 | 我方现状 | 成本 |
| --- | --- | --- | --- | --- |
| 1 | 私信发表情（`business=im` 表情面板，选中即发 `[name]` 文本） | 私信高频，我方 FEATURES 明确「不支持」 | **渲染链路已就绪**（`adapter/message/PrivateMsgAdapter.kt:119` `textReplaceEmote`），只缺输入入口 | S |
| 2 | 性别 + IP 属地显示 | 判断对方身份/地域，成本极低 | 我方 `api/UserInfoApi.java` 已调用 `x/space/wbi/acc/info`（同一份 JSON 含 `sex`/`ip_location`），但只解析 `sys_notice`/`live_room`/`contract` | S |
| 3 | 互关徽章（`attribute == 6`） | 关系判断 | `api/FollowApi.java` 未解析 `attribute` | S |
| 4 | 播放器音频焦点（失去焦点暂停、不自动恢复、退出释放） | 来电/其他 App 抢音时行为正确 | grep `requestAudioFocus`/`AUDIOFOCUS` **0 命中** | S |
| 5 | 分享得经验（每日一次，本地去重） | 白拿经验 | 转发只有「转发动态」 | S（账号写操作） |
| 6 | 卡片默认长按菜单（稍后再看 / 不感兴趣 / 加入黑名单） | 列表页就地操作 | 我方卡片长按只回调页面自设监听；无 `blockUser` | M（「不感兴趣」对方是本地假实现，**不要照搬**） |
| 7 | 短视频接入无极调音 | 手表喇叭音小 | 短视频只有系统音量 ±1 | S |
| 8 | 搜索结果排序（综合/最多播放/最新发布/最多收藏） | 手表无键盘，排序比换词实用 | `api/SearchApi.java` 无 `order` 参数 | M |
| 9 | 收藏夹「听全部」+ 全屏音频页 + 队列页（1.2.9 整批） | 手表听歌场景 | 无独立音频域（只有「听视频」模式） | L |
| 10 | 收藏夹批量下载（音频/视频） | 批量入队 | 收藏夹只有批量复制/移动/取消收藏 | M |
| 11 | 检查更新限时总闸（`callTimeout`） | 消除「永远卡在检查更新」 | 我方只有 connect/read 超时 | S |
| 12 | 启动密码锁（防熟人翻手机） | 与我方伪装互补 | 只有应用内伪装 + 退出暗号 | S/M |
| 13 | 后续仍缺的旧报告条目：评论三层兜底、番剧 v2 取流 + 每集进度、楼中楼带图隐藏、首页推荐分页游标、片头片尾档位、弹幕磁盘缓存、播放会话哨兵、页面栈恢复、过渡动画开关、`values-night`、无障碍描述 | — | 逐条现状见 cyq 分报告 §2.2 | S–M |

### 3.4 三方交集：出现两次以上 = 高置信需求

| 需求 | 出现在 | 结论 |
| --- | --- | --- |
| 移动数据/Wi-Fi 独立画质 | Revival、cyq | 手表 eSIM 流量场景真实，做 |
| 音频焦点 | cyq（Revival 也有播放器增强但未确认焦点） | 做 |
| 块状/窗口化弹幕与上限 | Revival、cyq（磁盘缓存） | 做 |
| 长按倍速提示可调 / 播放器手势 | Revival | 做（S） |
| 存储清理 / 缓存可观测 | Revival | 做（我方死代码复活） |
| 卡片长按菜单 | cyq | 做（去掉假实现） |
| 性别/IP 属地、互关 | cyq | 做（零接口成本） |
| 播放队列 | Revival、cyq（音频队列） | 做（视频连播队列） |

---

## 4. 跨分支功能增量总表（按域）

> 表中「我方现状」均已回当前代码核实；详细行号见对应分报告。成本口径见 §1.3。

### 4.1 取流与播放健壮性

| 功能 | 来源 | 用户价值 | 我方现状 | 成本 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| playurl 双接口兜底（WBI → legacy、pgc v2 → v1） | Revival | 高：接口抖动时仍能播 | `api/PlayerApi.java` 只走 `x/player/wbi/playurl`（`:125`）与 `pgc/player/web/playurl`（`:348`），失败即抛 | S | **P1** |
| 番剧 v2 取流 + 每集观看进度（`watch_progress`） | cyq | 中高：番剧续播准确 | 仍是 v1 + `season_type=1` 硬编码 | M | P2 |
| 首页推荐分页游标（`ps`/`fresh_idx`/`brush`） | cyq | 中：加载更多不再静默停住 | `api/RecommendApi.java` 只有 `fresh_type` | S/M | P2 |
| 设备档位接管取流/播放器/弹幕上限 | Revival | 中高：低配表稳定 | `PerformanceManager` 只影响图片/列表 | M | P2 |
| `opt*` 容错（排行榜等解析零容错） | cyq | 中：接口轻微变动不崩页 | `api/RankingApi.java` 用 `getJSONObject`/`getString` 链 | S | P3 |
| 播放会话哨兵（判断上次播放是否异常收尾，只留本地） | cyq | 中：崩溃归因 | 无 | M | P3 |

### 4.2 弹幕

| 功能 | 来源 | 用户价值 | 我方现状 | 成本 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| 弹幕数量保护（自动上限 + 手动优先 + 流式计数） | Revival | 高：弹幕海量视频不卡死 | 无阈值逻辑 | S | **P1** |
| 弹幕窗口加载（360s 一段、有界内存、失败重试、可取消） | Revival | 高：长视频内存与请求有界 | 一次性全量取段全量解析 | M | P2 |
| 弹幕磁盘缓存（`cid+segment` 落盘 + TTL） | cyq | 中：重复播放省流量 | 只有分段拉取，无落盘 | M | P2 |
| 本人弹幕边框（CRC32 比对自己 mid 的 hash） | Revival | 中：一眼找到自己 | 无 | S | P2 |
| 强制滚动弹幕 | Revival | 中低 | 开关已有但**不生效**（`SettingsKeys.kt:183` 全仓无消费方） | S | **P1（补漏）** |
| 直播弹幕昵称 | Revival | 中低 | **已有**（`PlayerDanmuClientListener.kt:161`，原报告判错） | — | 不做 |
| 直播弹幕时钟对齐 / 停留倍率 | Revival | 中低 | 时钟走墙钟；停留倍率无（release 日志 D5） | S/M | P2 |
| 弹幕点击空降（时间戳 seek） | cyq | 中 | 只有输入式「跳到指定时间」 | L | P3 |
| 片头片尾五档（关/仅头/仅尾/都跳/询问） | cyq | 中 | 单布尔 + 撤回 | M | P3 |

### 4.3 播放队列、手势与播放器交互

| 功能 | 来源 | 用户价值 | 我方现状 | 成本 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| 音频焦点 | cyq | 高：来电/抢音行为正确 | 0 命中 | S | **P1** |
| 连播队列（UP 主/合集/收藏夹/缓存 + 四模式 + 耳机切歌 + token 恢复） | Revival | 高：连续观看 | 只有虚拟合集顺序连播，状态散在 Activity | M | P2 |
| 短视频无极调音 | cyq | 中高：手表喇叭音小 | 只有系统音量 ±1 | S | **P1** |
| 长按后上/下划切换上/下一条视频 | Revival | 中：竖屏浏览 | 无 | S | P2 |
| 长按倍速提示可调大小/关闭 | Revival | 中：提示不挡画面 | 只有开关与倍率 | S | P2 |
| 互动视频进度/分支持久化 | Revival | 中 | 只在内存/Intent | S | P2 |
| 大图分块解码（长图/动图/失败态） | Revival | 中高：长图 OOM | 整图 PhotoView | M | P2 |
| ExoPlayer 单时间轴 DASH | Revival | 高 | 双播放器补偿（见 §7.5，已试过并回退） | L | 暂缓 |

### 4.4 设置与网络画质

| 功能 | 来源 | 用户价值 | 我方现状 | 成本 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| 移动数据 / Wi-Fi 独立默认画质 | Revival、cyq | 高 | 单一默认清晰度 | S | **P1** |
| 搜索排序（仅视频/专栏支持） | cyq | 中高 | 无 `order` 参数 | M | P2 |
| 过渡动画开关 | cyq | 中 | `overridePendingTransition` 硬编码 | S | P3 |
| `values-night` 资源 | cyq | 中 | 只有 `values-notnight` | S/M | P3 |
| 无障碍 `contentDescription`（交互控件） | cyq | 中 | 布局里仅 8 处 | M | P3 |

### 4.5 存储与下载

| 功能 | 来源 | 用户价值 | 我方现状 | 成本 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| 存储空间页（分类统计 + 有界清理） | Revival | 高：用户没有清理入口 | `FileUtil.clearCache()` 死代码 | S | **P1** |
| 收藏夹批量下载（视频/音频） | cyq | 中 | 只有批量复制/移动/取消收藏 | M | P2 |
| SAF 外置 SD 缓存 | Revival | 低（手表几乎无卡槽） | 全链路文件路径 | L | 暂缓 |
| 差分更新（BSDIFF40） | Revival | 低（基建成本高） | 全量 APK + 签名校验 | L | 暂缓 |

### 4.6 社交、关系与消息

| 功能 | 来源 | 用户价值 | 我方现状 | 成本 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| 私信发表情 | cyq | 高 | 渲染链路已就绪，缺输入入口 | S | **P1** |
| 性别 / IP 属地 | cyq | 中高 | 同一份 JSON 未解析 | S | **P1** |
| 互关徽章 | cyq | 中 | 未解析 `attribute` | S | **P1** |
| 分享得经验 | cyq | 中 | 无 `share/add` | S | P2（写操作需拍板） |
| 卡片长按菜单（稍后再看 + 拉黑） | cyq | 高 | 无默认菜单、无 `blockUser` | M | P2（写操作需拍板） |
| 历史记录多选删除 | Revival | 中 | 只有单条删除 | S | **P1** |
| 视频 / 动态举报 | Revival | 中 | 无入口 | S | P2（写操作需拍板） |
| 评论三层兜底（去重/本地补回/换类型重拉） | cyq | 中高 | 无 | M | P2 |
| 楼中楼带图按 rpid 隐藏入口 | cyq | 中 | 无 `rpid` 可见性判断 | S | P2 |
| 定时发布范围校验 | cyq | 低 | 无 | S | P3 |
| 创作中心稿件管理/删除 | Revival | 中 | 只有统计 | M | 暂缓 |
| 私信视频卡片（手写 gRPC + protobuf） | cyq | 中 | 无 | L | 不做（建议只做文本+链接） |

### 4.7 外观与主题（PianoEthan）

| 功能 | 用户价值 | 我方现状 | 成本 | 优先级 |
| --- | --- | --- | --- | --- |
| 播放器进度条按封面取色 | 中 | 固定主题色 | M | P2（需 MCU 子集） |
| 主题背景图 | 中高 | 只有光晕/玻璃 | S–M | P2 |
| 种子色 → 36 字段色板 | 中高 | 7 套写死 | M（需 vendored MCU + 改 1 个文件） | P3（需批准新依赖） |
| `.btheme` 导入/导出 | 中 | 无 | M（依赖色板） | P3（需批准） |
| 主题设置独立页 | 中 | 设置分组内若干行 | S–M | P3 |
| 全树程序化染色 | — | 78 处 `?attr/` 硬约束 | L–XL | 不做 |

### 4.8 启动、更新与健壮性

| 功能 | 来源 | 用户价值 | 我方现状 | 成本 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| 更新检查加 `callTimeout` 总闸 | cyq | 中：不再永远卡在检查更新 | 只有 connect/read | S | **P1** |
| 崩溃本地留档 + 待发队列（冷启动补发） | cyq（管道思路） | 中高：早期崩溃可观测 | 一次性上传，无队列 | M | P2 |
| 页面栈恢复（进程被杀后可重建页面） | cyq | 中 | 无 | M | P2 |
| SP `getString` 类型保险丝 | cyq | 中：防类型漂移崩溃 | `SharedPreferencesUtil` 只防 null | S | P2 |
| 启动密码锁 | cyq | 中高（隐私） | 只有伪装 + 退出暗号 | S/M | P2 |
| 桌面图标伪装（`activity-alias`） | cyq | 中 | 无 | M | 不做（逐 ROM 兼容风险） |

### 4.9 音频域（评估结论：暂不做）

cyq 1.2.9 与 Revival 都有「音频播放器」方向（音频页 / 队列 / 歌词 / 缓存代理）。我方只有播放器内「听视频（关画面只听声）」。

- 价值真实（手表听歌），但等于**新增一个播放域**：前台服务 + 通知 + 队列 + 进度 + 歌词 + 缓存，且与现有播放器/后台播放入口要统一。成本 L。
- 若要低成本试水，只做「收藏夹连播 + 屏幕可关」的轻量版，不引入新服务；不做歌词（第三方非官方接口，合规与稳定性风险）。

### 4.10 明确暂缓 / 不做

| 项 | 原因 |
| --- | --- |
| ExoPlayer 统一 DASH | 我方 26.10.31 已引入并实测（MuMu 上视频轨 1.1–1.4s 后掉 BUFFERING），26.11.01 紧急回退；重做需要真机矩阵验收，成本 L |
| SAF 外置 SD | 手表几乎无卡槽，要改下载库/扫描/播放/删除四层，收益/成本最差 |
| 差分更新 | 依赖发布侧生成并托管补丁，基建成本高于收益 |
| 音频独立域 / 歌词 | L，且歌词走第三方非官方接口 |
| 桌面图标伪装 | 逐 ROM 兼容（对方自己踩过两次「开关弹回 / 解锁死循环」） |
| 全量 Gson 迁移 | 与我方「无 Retrofit / 无序列化框架」的架构决定冲突 |
| 中继 / 浏览器代理、App 推荐流、DeepSeek | 硬编码维护者服务 / 官方 App 密钥 / 半成品，见 §6 |

---

## 5. 实现质量优化（不是新功能）

| # | 问题 | 对方做法 | 我方现状 | 判断 | 成本 |
| --- | --- | --- | --- | --- | --- |
| 1 | `FileUtil.readString` 不关闭文件流 | 提交 `5267f8e` 改 try-with-resources | `util/FileUtil.java:192-210` 手开 `FileInputStream`/`FileChannel`，无 close/finally | **搬**：通用读取入口，热路径累积 fd，minSdk 19 老表 fd 上限紧；纯预防性修复 | S |
| 2 | 更新检查可能无限等待 | 检查 client 带 `callTimeout 20s`，下载 client 不套总闸 | `util/UpdateManager.kt:42-45` 只有 connect 15s / read 60s | **搬**：DNS 挂死时无上界 | S |
| 3 | SharedPreferences 类型漂移 | 1.3.0 给 `getString` 加保险丝 | `util/SharedPreferencesUtil.java:100-105` 只防 `null` | **搬**：10 行，防 `ClassCastException` 类崩溃 | S |
| 4 | 崩溃上报无本地留档/队列 | 先入队再上传、冷启动补发、上限淘汰 | `api/TerminalApi.java` `uploadCrash` 一次性上传 | **搬管道不搬后端**：落 `cacheDir`，冷启动补发我方接口 | M |
| 5 | 播放异常收尾无法归因 | `PlayerSentinel`：进播放页同步写标记，正常销毁清除，下次启动仍在 = 上次异常；只留本地 | 无 | **搬思路**（native 崩溃 / LMK / 强杀同形，上传会误导归因） | M |
| 6 | 弹幕内存模型 | 窗口加载 + 数量保护 + 流式计数 | 全量 | **搬**（见 §4.2） | M |
| 7 | 进程被杀后页面栈丢失 | 可重建页面入栈/出栈，同步落盘，退出/崩溃清栈 | 无 | **搬**：要接入 `MenuActivity.killAndJump` 与 `CatchActivity`，并做「退出后不回到旧页」负向测试 | M |
| 8 | 列表/切页固定开销 | 1.2.8 批量清理重复系统查询、切页清缓存 | 旧报告 §11 已记「冷启动网络风暴」，方向一致 | **参考自查**，不整搬 | M |
| 9 | 测试策略 | 纯函数分层（`TintRules` 不 import android）+ 分支顺序钉死 + 60 个仪器化 | 我方已有 1429 个 JVM 测试，但缺播放器/弹幕/大图三组 UI 行为回归 | **借鉴 3–5 个仪器化回归用例**，不搬 60 个 | S/M |
| 10 | 主题资源体系 | 运行时染色 | 编译期资源（78 处 `?attr/`） | **不搬**（互斥范式） | — |

---

## 6. 不建议照搬

### 6.1 硬红线（不可搬）

1. **中继 / 浏览器代理**：Revival 硬编码维护者服务 `jp.031030.xyz/bili-relay` 并把 B 站流量改道，违反我方「只走 https + 域名白名单 + 不引入集中式中间人」红线，也把可用性绑在第三方服务器。
2. **App 推荐流**：Revival 硬编码 B 站官方 App 的 `APP_KEY/SECRET` + 伪装 UA + 账号绑定——凭据硬编码 + 模拟官方客户端 + 账号操作三重风险。
3. **第三方 BaaS 崩溃上报**：cyq 直连 Supabase + netlify 反代；与我方「自建接口 + 隐私同意 + 可关」策略冲突。只学队列管道。
4. **`exported="true"` 22 处 / `testkey.jks` 兜底签名**（PianoEthan）：反面教材，我方 Release 签名只在 CI Secrets，且有版本一致性校验。

### 6.2 技术路线不匹配

- 全量 Gson 反序列化（PianoEthan `c87e2ce`，30 个 api 文件）：与我方无序列化框架的架构决定冲突；只借「契约层单测 + 畸形 JSON 用例」的姿势。
- API 14–18 兼容补丁（PianoEthan 大量提交）：我方 minSdk 19，基本不适用；`java.util.function` / `Map.of` 这类 **API 24 陷阱**才是我们要防的，值得在 CI 加一次静态排查。
- targetSdk 26 + 只打 32 位：不同取舍，不能当参照。

### 6.3 对方自己也不完整 / 假实现

- 「不感兴趣」：cyq 是**本地移除**并提示「已减少此类推荐（本地生效）」，注释自认服务端接口未接——照搬会给假承诺；要么不显示「不感兴趣」，要么如实写「本地隐藏」。
- 私信视频卡片：为 3 个字段手写 gRPC + protobuf + TV 扫码 `access_key`，维护成本远超收益；我方只做「文本 + 链接」降级版即可（若要做）。
- 主题引擎没有导出能力，样例包靠 Python 脚本生成（这反而是我方的低成本差异化点）。

### 6.4 需要用户拍板的账号写操作

举报（视频/动态）、拉黑/解除拉黑、分享得经验、私信表情发送：都会以**用户账号**向 B 站提交写请求，风控与账号安全影响由用户承担。建议先做只读/展示类（性别、IP 属地、互关徽章、私信表情的**面板与渲染**），写操作等用户确认后再排。

---

## 7. 实施路线

### 7.1 先修（不占功能排期）

| 项 | 内容 | 成本 |
| --- | --- | --- |
| MediaSession 与 minSdk 19 | `PlayerActivity.kt:21/230` 的 API 21 字段类型隔离；API 19 模拟器冒烟 | S |
| `FileUtil.readString` 关流 | 见 §5.1 | S |

### 7.2 批次 1：S 级、低风险、高收益（建议直接开工）

每项都已核对我方落点文件，均为单文件或双文件改动 + 纯函数单测：

1. **playurl 双接口兜底** — `api/PlayerApi.java`；把「首选/回退」顺序抽成 `player/PlayUrlFallbackPolicy.kt` 纯类；`PlayerApiTest` 扩用例。风险：回退接口同一份内容，Cookie/Referer 仍走 `player/PlayerRequestPolicy.kt`。
2. **播放器音频焦点** — `activity/player/PlayerActivity.kt`（起播 request、`onAudioFocusChange` 暂停、`onStop/onDestroy` abandon），复用 `player/PlayerControlDelegate.kt:43` 已有的 `AudioManager`；抽 `AudioFocusPolicy` 做 JVM 单测。策略建议：`LOSS` 暂停、`LOSS_TRANSIENT` 暂停且恢复时**不自动续播**（避免打断用户手动暂停），后台播放是否持焦要真机验。
3. **私信发表情** — `api/EmoteApi.java` 加 `BUSINESS_IM`、`activity/message/PrivateMsgActivity.kt` 输入行加表情按钮并回填 `[name]`、`EmoteActivity` 回传 `"text"`；渲染侧 `PrivateMsgAdapter.kt:119` 已就绪。风险：`business=im` 面板接口是否对网页 Cookie 开放需实测，失败回落 reply。
4. **性别 / IP 属地 + 互关徽章** — `api/UserInfoApi.java`（`getUserSpaceInfo` 的同一份 JSON `optString("sex")`/`optString("ip_location")`）、`model/UserInfo.java`（Parcelable 读写序同步）、`api/FollowApi.java`（`attribute==6`）、`adapter/user/UserListAdapter.kt` + `res/layout/cell_user_info.xml`。
5. **短视频无极调音** — `activity/video/ShortVideoPlayerActivity.kt` 改走 `player/PlayerVolumePolicy.kt` 的 gain 通道并复用存档键；`PlayerVolumePolicyTest` 已存在。
6. **存储空间页** — 新增 `util/AppStorageManager.kt`（图片缓存/临时/更新包/可用空间快照 + 有界清理）+ `activity/settings/StorageActivity.kt` + layout；入口挂 `activity/settings/SettingGroupActivity.kt` 的存储分组；复用 `util/FileUtil.java:49` 的 `clearCache()`。**清理范围必须白名单，绝不删下载目录**。
7. **更新检查 `callTimeout`** — `util/UpdateManager.kt`。
8. **弹幕数量保护** — `player/DanmakuManager.kt` + `api/DanmakuApi.java`：先查计数、超阈值不自动加载、手动可开；抽 `DanmakuLoadPolicy` 纯函数 + 单测（这是「窗口加载」的低成本前哨）。
9. **历史记录多选删除** — `activity/user/HistoryActivity.kt` + `api/HistoryApi.java`；选择态与「单批上限」抽纯函数单测。
10. **移动数据 / Wi-Fi 独立画质** — 新增 `util/NetworkPlaybackQuality.kt`（网络类型 → 取哪个 qn 键）+ `activity/settings/SettingQualityActivity.kt` 加两个选择项 + `util/SettingsKeys.kt`；纯函数单测。
11. **SP 类型保险丝** — `util/SharedPreferencesUtil.java`。
12. **动态卡片评论入口 + 评论数**（release 日志复查 D2）— `adapter/dynamic/DynamicHolder.kt` + `res/layout/cell_dynamic_action.xml`；「万/亿」缩写抽纯函数 + 单测；无评论显示「评论」。
13. **直播滚动弹幕停留倍率**（D5）— `SettingTerminalPlayerActivity` 加 1.0–3.0 档位（默认 2.0），`PlayerDanmuClientListener`/`DanmakuManager` 取值，与普通弹幕速度分离。
14. **「强制为滚动弹幕」死开关接线**（E2）— 开关已存在但不生效，`player/DanmakuManager.kt` 落实消费。
15. **视频详情争议信息判空**（D9/S1）— `api/VideoInfoApi.java:231-232` 的 `getString("argue_msg")` 改 `optString`，补缺字段 JSON 单测。

**批次 1 验收**：`./gradlew.bat :app:assembleDebug`（双 flavor）+ `:app:testStandardDebugUnitTest` / `:app:testCoexistDebugUnitTest` 全绿；涉及 UI 的项装 MuMu 模拟器实测并把结论写进 `docs/review/fix-progress.md`；面试文案进 `strings.xml`；文档同步 `docs/FEATURES.md`。

### 7.3 批次 2：M 级，按「域」整批做

- **播放器域**：连播队列抽象（`player/PlaybackQueue.kt` 纯逻辑 + 四模式 + 耳机切歌 + token 恢复）、长按上下划切视频、长按倍速提示尺寸、互动视频进度持久化。
- **弹幕域**：窗口加载（360s 一段、有界、可取消、失败重试）+ 磁盘缓存（`cid+segment` + TTL）+ 本人弹幕边框。
- **番剧/推荐域**：番剧 v2 取流 + 每集进度、首页推荐分页游标。
- **评论/消息域**：评论三层兜底（去重/本地补回/换类型重拉）、楼中楼带图按 `rpid` 隐藏。
- **外观域**：主题背景图（`ui/appearance/BackgroundImage.kt` + `ColorScheme.applyWindowTheme`）、播放器封面取色（MCU 子集）。
- **健壮性域**：崩溃本地队列 + 播放会话哨兵 + 页面栈恢复。
- **社交域（需拍板）**：卡片长按菜单（稍后再看 + 拉黑）、私信文本卡片。

### 7.4 批次 3：M–L，需批准或需真机矩阵

- 种子色 → 色板 + `.btheme` 导入/导出（**需批准 vendored MCU**；注意 `dynamiccolor/DynamicColor.java` 的 `java.util.function.Function` 要换自建接口，不需要 desugaring；我方 36 字段映射表才是真正工作量）。
- 大图分块解码（优先无依赖方案：`BitmapRegionDecoder` + 手势自绘；引入 subsampling 需批准）。
- 收藏夹批量下载。
- 搜索排序。
- 启动密码锁。
- 视频/动态举报（写操作，需拍板）。

### 7.5 为什么 ExoPlayer 现在不做（试错史）

【事实】我方 26.10.31 曾引入 ExoPlayer 2.19.1 的「统一时钟」方案：新增 `player/UnifiedDashPlayer.java` / `ExoDashMediaPlayer.java`，把 `ijkPlayer` 抽象成 `playerCore:IMediaPlayer`（112 处改名），并在起播后做健康检查 + 失败回落 IJK。
【事实】实测在 MuMu 模拟器上「只要视频轨参与，播 1.1–1.4 秒后必掉 BUFFERING 且永不恢复」，同时 IJK 链路被拖坏；26.11.01 因此**紧急回退**（`docs/review/fix-progress.md` §六十三/§六十四）。
【结论】Revival 的 ExoPlayer 方案在代码层确实解决了结构性音画漂移，但在我方可用的测试环境里无法验证收益、且回退过一次。**重做的前提是：有真机（含旧手表）可验证 + 明确接受新增依赖与包体增量 + 设备档位二选一（低配自动回落 IJK）。** 在此之前，先用 §5.1 的 playurl 兜底、音频焦点、弹幕窗口等 S/M 项改善播放体验。

### 7.6 明确暂缓

见 §4.9 / §4.10：音频独立域、SAF、差分更新、桌面图标伪装、创作中心稿件管理、中继/App 推荐/DeepSeek。

---

## 8. 风险与合规

1. **署名**：移植的每一条在 commit message 注明参考来源分支与版本；若有成段算法照搬（如弹幕窗口策略、色板派生），在 `THIRD_PARTY_NOTICES.md` 登记。三仓库均 GPL-3.0，与我方兼容。
2. **新依赖需要单独批准**：`material-color-utilities`（Apache-2.0，vendored）、subsampling-scale-image-view、ExoPlayer。项目约定「不轻易引入新第三方库」。
3. **minSdk 19 硬约束**：新代码不得用 `java.util.function.*`、`Map.of`/`List.of`/`String.join`、`ConcurrentHashMap.newKeySet`、`Optional`/`stream`/`time` 等 API 24+ 类/方法（`docs/architecture-map.md` §1.1 有完整清单）；主题/色板类代码尤其要检查。
4. **账号写操作**：举报/拉黑/分享得经验/私信发送都会改变用户账号状态，需用户逐项确认；文案不要承诺到账。
5. **隐私**：不引入第三方 BaaS 上报；新增的本地队列/日志必须走既有 `LogSanitizer` 口径。
6. **私有功能边界**：第三方片源、第三方弹幕等私有能力在 `private/`，本报告所有移植项都落在公开侧，不得与私有挂点混写。

---

## 9. release 日志复核增量（2026-11-06 补）

> 代码差异分析之后又做了一轮「**三个分支的 release 更新日志 vs 已有报告**」的对照（含 GitHub Releases、各仓库应用内更新日志、cyq 的 `docs/更新日志.md`、Revival 官网的 1.2.5-BETA 公告），详见 [`forks/release-notes-delta.md`](forks/release-notes-delta.md)。
> 数字：真缺口 10 条（可排期 9）、报告漏但核实后**非缺口** 12 条、**判错 3 条**、日志吹牛/未入库 3 条、我方同类风险 6 条。

### 9.1 已并入批次 1 的 4 项（S 级）

| 项 | 来源（版本） | 我方现状 | 成本 |
| --- | --- | --- | --- |
| 动态卡片评论入口 + 评论数 | Revival 1.2.3 | `adapter/dynamic/` 四个文件 grep「评论/comment」0 命中，卡片没有评论入口 | S |
| 直播滚动弹幕停留倍率（默认 2×，1.0–3.0） | Revival 1.2.3 | 我方只有 `speedFactor`，无「停留」语义 | S |
| 「强制为滚动弹幕」死开关接线 | 我方自身缺陷（PianoEthan 早有实现） | 开关 + 文案索引都在，**全仓无消费方**，用户开了不生效 | S |
| 视频详情争议信息判空（`argue_msg`） | Revival 1.2.5-BETA 修过同类崩溃 | `api/VideoInfoApi.java:231-232` 判了外层非空仍用 `getString`，缺字段即抛 `JSONException` | S |

### 9.2 报告判错的 3 条（已在上文修正）

- **E1 直播弹幕显示发送者**：报告写「我方无」不成立——设置项、设置页、消费方（`PlayerDanmuClientListener.kt:161`）齐全，已修正为「已有」。
- **E2 强制滚动弹幕**：报告写「我方无」不准确——**有开关但不生效**，比「无」更严重（用户以为开了）。已改为 P1 补漏并进批次 1。
- **E3 PianoEthan 画像**：报告写「其余 60 个提交是兼容补丁与 Gson 迁移」过窄——另有个人资料编辑、评论带图、收藏 v3、隐私模式、崩溃修复等；但这些我方均已对等或更强，**不构成功能增量**，只修正措辞。

### 9.3 新增的批次 2/3 项（M 级为主）

- **D1 收藏夹/历史内搜索 + 跨夹「全部收藏」聚合流**（Revival 1.2.3）：我方收藏列表只有服务端三档排序，无 `keyword`/`allFolders`。M。
- **D3 评论点赞/点踩/删除状态跨页同步**（Revival 1.2.3）：我方只在当前列表内改。M，纯逻辑可 JVM 测。
- **D6 消息「一键已读」+ 返回自动刷新 + 已读同步**（Revival 1.2.5-BETA，**源码未入库**，需先实测接口）。M。
- **D7 收藏夹视频手动排序**（同 1.2.5-BETA，未入库）。M。
- **D8 创作中心稿件搜索/单稿详情/评论快捷入口**（同 1.2.5-BETA，未入库）。M，随稿件管理一起评估。
- **D4 评论举报**：公开侧已有 `OptionalFeatures.replyReport` 私有门面，是否已覆盖待确认；公开侧不另做（写操作）。
- **S3 高能进度条数据源**：对方判定官方 `view_points` 接口失效并换成本地弹幕密度；我方仍走 `bvc.bilivideo.com/pbp/data`，**需实测**是否还有数据（不报错但可能空）。
- **S5 主题 API 降级闸**：若将来引入取色/主题包，必须按 API 降级而不是崩（PianoEthan 曾在 Android 5–9 上 `NoSuchMethodError`）。

### 9.4 日志写了但代码里没有 / 未入库（不采信）

- PianoEthan 应用内日志「增加番剧追番按钮」：代码未见（只有追番数文案），v3.1.3 Release 正文为空、唯一提交是收藏 v3 → **不能当功能版本**。
- Revival 1.2.5-BETA（2026-11-05，官网已发布）：GitHub 与 clone 都没有源码 → D6–D9 只能给日志级结论，实现是否落地未验证。

### 9.5 我方同类风险（修复类条目，供批次 2 顺带处理）

| # | 对方修复 | 我方可疑点 |
| --- | --- | --- |
| S1 | 争议信息缺失崩溃 | `api/VideoInfoApi.java:231-232`（已进批次 1） |
| S2 | `CatchActivity` `setSpan` 越界 | 我方 `activity/CatchActivity.kt:168` 的 `setSpan(..., 0, 8, ...)` 写死 8，靠「可能的崩溃原因：」正好 8 字，改文案就会让崩溃页自己崩 |
| S3 | 高能进度条无波形 | 我方数据源未实测（见 §9.3） |
| S4 | 顶栏返回热区随电量文本变窄 | `res/layout/activity_player.xml:163` 同款结构，真机核 |
| S5 | 旧系统主题 `NoSuchMethodError` | 我方目前无运行时取色，引入时需降级闸 |
| S6 | SP 类型漂移 `ClassCastException` | 已在批次 1（`SharedPreferencesUtil` 保险丝） |

### 9.6 报告漏了但**不是缺口**的 12 条（避免重复造轮子）

收藏夹长按编辑/删除、手势防误触、隐私模式不记历史、界面边距、个人资料编辑、经验变化记录、热搜/排行榜入口、记住倍速、深链白名单、第三方播放器 Cookie 过滤、下载服务重建续跑、设置项搜索——我方均已有（或更强），明细见 `forks/release-notes-delta.md` §3.2。

## 附录 A：四份分报告

| 分报告 | 覆盖内容 | 关键结论 |
| --- | --- | --- |
| [`forks/revival.md`](forks/revival.md) | Revival 全部源码/资源/构建/测试对比；功能增量 20 项；Top10 推荐 | 功能完整度最高，但约一半是上游 2.9.5 增量；ExoPlayer/连播/存储/档位最值得看 |
| [`forks/piano-ethan.md`](forks/piano-ethan.md) | 主题引擎完整拆解（格式/安装/校验/色板/背景/取色）；37 个真正独有文件；Top8 推荐 | 唯一成体系增量是主题；互斥范式，按切片移植；`FileUtil` 流泄漏可直抄 |
| [`forks/cyq.md`](forks/cyq.md) | 旧报告 25 条功能缺口逐条复核（4 已补/19 仍缺/1 部分/1 不适用）+ 1.2.5→1.3.0 增量；Top9 推荐 | 高优先缺口已被我们两个迭代消化；剩下私信表情/音频焦点/性别属地/互关/搜索排序等细节 |
| [`forks/release-notes-delta.md`](forks/release-notes-delta.md) | 三个分支全部 release 更新日志 vs 已有报告的对照：真缺口 10 / 非缺口 12 / 判错 3 / 吹牛未入库 3 / 我方同类风险 6 | 补出 4 个 S 级批次 1 项与 5 个批次 2/3 项；修正 3 条判错（见 §9） |

## 附录 B：统计口径

- 文件数、行数、`@Test` 数按各仓库工作树 `app/src/main`、`app/src/test` 直接计数（PowerShell `Get-Content ... | Measure-Object -Line` 口径）；我方 1,429 个 `@Test` 为源码中 `@Test` 出现次数（与分析中「测试用例数」口径一致，不等于运行时用例数）。
- `only-fork` / `only-ours` / `modified` 基于 `git ls-tree -r` 的 blob SHA 比对，**含 Kotlin↔Java 迁移噪声**，只作线索。
- 三个对方仓库均以 `git clone --filter=blob:none` 拉取到仓库外目录，未改动其内容。

## 附录 C：本次抽查记录（Lead 复核）

| 抽查项 | 结论 |
| --- | --- |
| `FileUtil.readString` 是否真的未关流 | 属实，`util/FileUtil.java:192-210` 无 close/finally |
| 我方是否已有音频焦点 | 属实，grep `requestAudioFocus`/`AUDIOFOCUS` 0 命中 |
| 我方 `clearCache` 是否死代码 | 属实，仅定义处命中 |
| 私信表情渲染是否就绪 | 属实，`PrivateMsgAdapter.kt:119` 有 `textReplaceEmote` |
| `ip_location` 是否未解析 | 属实，grep 0 命中；`UserInfoApi` 的 `getUserSpaceInfo` 只取 `sys_notice`/`live_room`/`contract` |
| playurl 是否单路径 | 属实，`PlayerApi.java:125/348` 单 URL、无回退 |
| ExoPlayer 是否已移除 | 属实，grep `exoplayer` 0 命中 |
| `MediaSession` 是否 import 且作字段 | 属实，`:21` import、`:230` 字段类型 |
| PianoEthan 的 MediaSession 修复提交是否存在 | 属实，`5c480df`「Android 5.0以下 MediaSession 字段导致 PlayerActivity 类加载崩溃」，字段改 `Object` + 局部变量 |
| 我方 `PlayerActivity` 是否调用 `setRequestedOrientation` | 否（26.10.19 的 `Only fullscreen activities can request orientation` 崩溃已随代码移除，当前版本无此调用） |
| 我方导出组件数量 | 2 处（`SplashActivity`/`GetIntentActivity`），与分报告一致 |
| 三份分报告是否触碰 `private/` | 均声明未读取、未引用 |
| 「强制为滚动弹幕」是否死开关 | 属实：开关与设置页在 `SettingTerminalPlayerActivity.kt:162`，全仓无消费方 |
| 动态卡片是否有评论入口 | 属实：`adapter/dynamic/` 四文件 grep「评论/comment」0 命中 |
| 直播弹幕昵称是否已有 | 属实：`PlayerDanmuClientListener.kt:161` 按 `player_danmaku_showsender` 拼接昵称（原报告判错） |
