# 修复台账（活文档）

> 这是**唯一**的当前状态 + 待办台账。完整历史正文已归档到 `docs/archive/review/fix-progress-history.md`（§一~§四十三 逐批次流水，4777 行）。
> 什么时候写这里：修完一个 bug / 关掉一个待办 / 每轮真机验证后。**别再往下追加长流水**——超过一屏就并进「历史章节索引」那一行。
> 架构与坑看 `docs/architecture-map.md`；功能清单看 `docs/FEATURES.md`；真机清单看 `docs/review/real-device-regression-checklist.md`（旧的 T/D 测试计划已归档为 `docs/archive/review/device-test-plan.md`，只在要查某条用例原文时翻）。

## 1. 当前版本状态

- **版本号**：`26.11.01`（`versionCode 2611010`，tag `26.11.01`）——CI 的 `verify-version` 会拿 tag 的 `YY.MM.DD` 段去比 `versionName`；`strings.xml` 的 `update_log_current` 锚点是「【26.11.01 本次更新】」，`:app:verifyVersionConsistency` 以该锚点校验。tag `26.10.31`、`26.10.31.1`、`26.11.01` 均已占用（发版记录见 §4 的 §六十一~§六十四）。
- **上一版**：`26.10.31.1` / tag `26.10.31.1`（`versionCode 2610311`、`versionName 26.10.31`）。
- **本轮改动（26.11.01，紧急回退版）**：按用户要求把**播放器相关代码整体退回 26.10.27 发版状态**（§3.90）——撤掉自编译的 ijkplayer 补丁库（`.so` 回到上游预编译版，三 ABI 体积回落）、ExoPlayer 统一时钟内核与 DASH 双流方案，修上一版在真机上「安装包明显变大、视频完全播不出来」；同时合并 PR #18（§3.91 菜单退出按钮、§3.92 媒体控件归类、§3.93 圆屏列表实验效果、§3.94 封面请求复用）与 PR #19（§3.83 举报 / 弹幕消息迁入可选私有功能，公开侧只留门面与挂点）。明细见 `strings.xml` 的 `update_log_current`（5 条编号条目，另 1 条为版本锚点）。
- **上一轮改动（26.10.31.1）**：修复「倍速播放时听到两路重叠音频、其中一路跟不上」（§3.88 终版——给随包 ijkplayer 打内核补丁；该补丁随 26.11.01 的回退一并撤掉）、修掉扫码登录停轮询后的失败提示外露英文异常原文（§3.86）。
- **上一轮改动（26.10.31）**：清晰度按账号权限锁定 + 4K 接入（§4 六十）、播放页内切 1080P（§4 五十九）、私信 2 分钟内撤回、内置选图页、图片加载提速、弥散光晕可选光源位置（§3.76）、系统时间错误防呆（§3.72）、账密 / 短信登录补存 App access_token（§3.87），以及四类修复（工具页跳转杀下层、短视频弹幕不跟设置、空关键词搜索藏历史、倍速两路重叠音频）。另有**一轮私有化整理**：评论举报与弹幕类消息整体迁入可选私有功能，公开仓库只留门面与挂点（§3.83）。明细见 `strings.xml` 的 `update_log_current`（10 条编号条目，另 1 条为版本锚点）。
- **26.10.31 发版时工作区已清空**：26.10.27 之后累积的未发版改动（§3.72~§3.85）与发版前补的登录令牌改动（§3.87）都随本版一起出去了。
- **26.11.01 发版时工作区已清空**：§3.91~§3.94（PR #18）与 §3.83 的私有化整理（PR #19）都随本版一起发布；26.11.01 之后新的待发版改动从这里往下记。
- **下次递增**：先看 `app/build.gradle:23-24`；release immutability 下被 Release 用过的 tag **永久不可复用**，同版本名补丁版只能用 `26.10.24.1` 这种后缀形式，`versionCode` 递增、`versionName` 可不变。改了版本号还要同步 `strings.xml` 里 `update_log_current` 的「【YY.MM.DD 本次更新】」锚点，否则 `:app:verifyVersionConsistency` 会失败。
- **构建命令**（单测任务名**带 flavor 前缀**，`testDebugUnitTest` 自 26.10.11 引入 flavor 后已不存在）：

  ```text
  .\gradlew.bat :app:assembleDebug
  .\gradlew.bat :app:testStandardDebugUnitTest
  .\gradlew.bat :app:testCoexistDebugUnitTest
  .\gradlew.bat :app:assembleRelease
  ```

- **JDK**：需要 17。仓库 `gradle.properties` **不再写死** `org.gradle.java.home`（写死的机器路径换机即报 `Value '...' is invalid`），本机 JVM 不是 17 时在**用户级** `~/.gradle/gradle.properties` 里设，或让 `JAVA_HOME` 指向 17。
- **SDK**：`compileSdk 34` 需要 `platforms/android-34` 与 `build-tools/34.0.0`；缺了 AGP 会在配置阶段自动下载（licenses 已接受时），也可用 `sdkmanager` 预装。`local.properties` 里写 `sdk.dir`，该文件被 gitignore。
- **依赖仓库**：`settings.gradle` 已把 aliyun 的 `google` / `public` / `gradle-plugin` 三个镜像前置，`google()`/`mavenCentral()`/jitpack 仅作兜底。
- **改了 `res/` 文件集合时**：`:app:clean` 与后续命令**必须分两次 Gradle 调用**，且带 `--no-build-cache --no-configuration-cache`，否则会回放陈旧资源（症状是 `Unresolved reference 'R.layout.xxx'`）。
- **版本一致性**：`:app:verifyVersionConsistency` 以 `strings.xml` 的 `update_log_current` 为锚点校验版本一致性；发版说明也由 CI 从它抽取（抽不到直接拒绝发版）。
- **单测（26.10.31 逆向产物迁入私有功能后实测，`testStandardDebugUnitTest` 与 `testCoexistDebugUnitTest` 各跑一遍；26.11.01 合并 PR #18 / #19 后复测）**：**公开配置 103 suites / 1070 tests**、**注入 `private/` 后 106 suites / 1116 tests**，两个 flavor 完全相同（同源码），0 失败 0 错误 0 跳过。两套数字都要记：公开配置少掉的正是随私有源码一起注入的那几个用例类，配置错了会立刻从计数上看出来（另加 `--no-configuration-cache` 跑一次公开配置，避免 `hasPrivateFeature` 回放旧值）。计数方式：`app/src/test/` 下 `*Test.kt|java` 计源文件（另有共享假实现 `util/FakeSharedPreferences.kt`），`app/build/test-results/<flavor>DebugUnitTest/TEST-*.xml` 的 `tests=` 求和（**用 PowerShell 解析会因中文用例名抛 `InvalidCastToXmlDocument`，改用 Python `xml.etree`**）。用例数一律以机器产出为准，队友口述数字不进入文档。
- **26.10.10 那一轮的构建记录（历史，留作「改 minSdk / 重跑 release 链路」时的验证口径）**：`minSdk 24 → 19` 全部改动（含 jsoup 1.16.1、7 个 `res/drawable-v21` 资源）落盘后，按「`clean` → `assembleRelease` → `lintRelease` → 单测」四步跑完（均带 `--no-configuration-cache`，`clean` 与 `assemble` 分两次调用），四步全 `BUILD SUCCESSFUL`（37s / 2m13s / 1m25s / 39s）。当时单测汇总 **49 suites / 460 tests / 0 failures**（任务名当时还是 `testDebugUnitTest`，现已带 flavor 前缀），最新 XML 时间 `2026-10-09T22:59:04`（晚于当轮 APK 出包时间 22:56:58，覆盖了当轮全部源码改动）。`:app:lintRelease` 的 **`NewApi` 从 22 条降到 2 条**，且这 2 条就是 `PlayerActivity.kt:1981` 的已知假阳性。发版前仍建议再跑一次确认。
- **「自动跳过片头片尾」已恢复上线（26.10.22）**：26.10.10 的紧急撤回**已解除**，`SkipOpEdPrefs.FEATURE_ENABLED` 回到 `true`，设置入口与搜索索引均已恢复；口径按用户拍板改为**默认关闭**（`DEFAULT_ENABLED = false`，老用户存档里显式写的值仍然优先）。恢复细节见 §3.3「恢复记录」，乱跳的三个根因修复见 §3.55。
- **本轮基建改动（26.10.10）**：**`minSdk 24 → 21 → 19（Android 4.4）`**，唯一落点是 `app/build.gradle:21`。两级下调的成本不同，分开记：
  - **24 → 21**：代码侧清掉**四处** API 24 才有的调用：`util/AsyncLayoutInflaterX.java` 的 `java.util.function.BooleanSupplier` → 工程内 `CancelledChecker`；`util/SharedPreferencesUtil.java:167` 的 `java.util.function.Consumer` → 工程内 `EditorAction`；`activity/reply/WriteReplyActivity.kt:177` 的 `Map.getOrDefault` → `msgMap[k] ?: 默认值`；`util/VideoPreloadManager.kt:12` 的 `ConcurrentHashMap.newKeySet<Int>()` → `Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())`（**这一处是会真崩的**：类一初始化就 `NoSuchMethodError`，且已确认留在 release dex 里）。两个调用点（`activity/base/BaseActivity.kt:435`、`util/AccountManager.java:202`）走 SAM 转换，未改。
  - **21 → 19**：依赖必须降两处——`androidx.appcompat:appcompat` 1.7.0 → **1.6.1**、`androidx.fragment:fragment-ktx` 1.8.0 → **1.6.2**（只有这两代 AAR 自己还声明 minSdk 14；1.7.0 / 1.8.0 声明的是 21，`checkReleaseAarMetadata` 会硬失败），并**恢复** `androidx.multidex:multidex:2.0.1` + `BiliTerminal.kt` 的 `attachBaseContext()` 里 `MultiDex.install(this)`——26.09.10 那次「minSdk 21 下是死代码」的删除被整体回滚。改回 21 时这三件事要一起撤。
  - **21 → 19 又清掉一批 API 21 调用点**（按 `:app:lintRelease` 的 `NewApi`，`current min is 19`）：`Build.SUPPORTED_ABIS` 三处（**静态数组字段**，低版本一执行即 `NoSuchFieldError`，而它是 `Error`，`catch (Exception)` 兜不住）统一改走 `api/TerminalApi.java` 新增的 `firstAbi()` / `supportedAbis()`（`SDK_INT >= LOLLIPOP` 闸 + 回落 `Build.CPU_ABI`）；`ui/widget/TextClock.kt` 的 `@JvmOverloads` 主构造会经 4 参 `TextView` 构造（API 21），而全工程 **43 个布局**都 inflate 它 → 改成三个显式次构造；`activity/settings/TutorialManagerActivity.kt:195/199` 的 `View.setBackgroundTintList` → `ViewCompat.setBackgroundTintList`；7 个矢量图（`icon_announcement`/`icon_download`/`icon_home`/`icon_nav_dynamic`/`icon_nav_media`/`icon_nav_trending`/`icon_update`）的 `android:tint="?android:attr/…"`（pre-21 解析不了）→ `res/drawable/` 改固定色，**带 `?attr` 的正本原样挪到 `res/drawable-v21/`（两份必须一起改）**。
  - **依赖侧一处真改动（24 → 21 阶段）**：`org.jsoup:jsoup:1.17.2` → **`1.16.1`**。1.16.2 起 jsoup 的 `StringUtil` 在**静态初始化块**里调 API 24 才有的 `ThreadLocal.withInitial(java.util.function.Supplier)`，而 `Jsoup.parse()` 必然用到它，minSdk 19/21 上会 `NoSuchMethodError`。**这不是推测**：把出包的 `classes.dex` 反汇编后确认 1.17.2 的 `withInitial` 就在 `<clinit>` 里，降到 1.16.1 后 dex 中 `java/util/function/` 与 `withInitial` 归零。**jsoup 不要再往上升**。
  - **验证方式**：不只看源码，还把**出包的 `app-universal-release.apk` 的 `classes.dex` 反汇编**逐条归属到类（判据与工具坑见 `docs/architecture-map.md` §1.1 末尾）。定稿包的 dex 实测：`newKeySet` / `KeySetView` / `java/util/function/` / `withInitial` **全为 0**，`newSetFromMap` 为 1；残余 `java/util/stream/` 1 与 `java/time/` 2 已追溯到「只被带 `SDK_INT` 闸或 `@IgnoreJRERequirement` 重载调用」的位置。
  - `:app:lintRelease` 另外两类输出**不改**：`InlinedApi`（常量已内联进字节码，旧系统只是忽略，如 `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS` / `MotionEvent.AXIS_SCROLL`）与 `ObsoleteSdkInt`（现在恒真的 `SDK_INT` 分支）。`activity/player/PlayerActivity.kt:1981` 的 `MediaSession.Callback` 是 **lint 假阳性**——所在 `private fun initMediaSession()` 第一行就是 `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return`。
  - **只对 release 包负责**：`jvmTarget 17` 下 Kotlin 会给 21 个数据类的 `hashCode()` 生成 `java/lang/Long.hashCode(J)` 之类的 API 24 调用，release 里被 R8 全部消除（dex 零命中），**debug 包才有**。本分支只发 release，故不动；要发 debug 包必须先处理这一条。
  - **完整兼容清单（含「再往下调会硬失败」的依赖下限、dex 级审计出的第三方库 API 24 引用与为什么可以不管）见 `docs/architecture-map.md` §1.1**，本文件不复述。

## 2. 未完成待办

### 2.1 真机未验证（无设备 / 无登录态）

- [ ] **收藏夹管理页新版式 + 内容管理入口（§3.80）部分设备验证**：已在 SDK AVD `wear5`（Android 14 / 466×466）上用 `coexistDebug` universal 包核对了**结构与文案**（默认夹与非默认夹两种版式、两行内容管理入口、点「批量删除内容」能开出夹内容页）。**仍未验的**：①点「批量删除内容」后是否真的**直达多选态**（要先登录拉到列表，空列表按设计不进多选）；②「清空失效内容」的确认框与 `data=0` 成功提示；③真手表/圆屏上的实际观感。**注意**：删除与清空都是真实账号的破坏性写操作，真机验证要做好心理准备，别拿主号试。

- [ ] **DASH 下载合并单文件（§3.66）端到端未验证**：需要登录态 + 选 1080P，本机只有模拟器且未登录。容器层已用两条现成 m4s 走 DebugBridge `merge` 实测通过（`result=OK`、`tracks=2`、采样数与源一致），**但「真实下载 → 合并 → 本地列表只剩视频 → 内置播放器有声」整条链路没跑过**。真机用例：登录后下载一个 1080P 视频，观察进度走完 0.88–1.00 的「合并音视频」阶段、目录里只有 `video.mp4`（无 `audio.m4a`、无 `video_merging.tmp`）、本地页直接播放有声；再故意制造合并失败（如把磁盘写满）确认保留双文件且下载不报错。

- [ ] **媒体控件「音乐播放」归类（§3.90）真表未验证**：本机模拟器不可用，会话↔通知配对只有代码级推演。真机用例：开启「系统媒体控件」后播放视频，系统媒体卡片应显示为音乐播放类型且标题正确；切 P / 换清晰度后标题跟随；退出播放页通知消失、不残留僵尸卡片；同时挂着下载任务时控件仍应标「音乐播放」（不再标「下载」）。

- [ ] **「圆屏列表」效果（§3.91）真表未验证**：视觉手感（0.50 缩放下限、0.85 系数的 t³ 淡出）是按代码定的参数。真机用例：圆屏模式下开启后各列表页滚动，条目应边缘变小变透明、中央正常；滑动流畅无卡顿（滚动回调零分配）；横向翻页（短视频 / 搜索）与文章、大图不应缩放；关闭开关重进页面恢复原样。

- [ ] **图片保存改造（对比审计 §3.3 P2-2，第二批）真机未验证**：Android 10+ 默认目录改为应用专属、下载完成经 MediaStore 发布进 `Pictures/哔哩终端/`。用例：看图页下载图片 → 相册可见、无 0 字节残留；再下载同一张覆盖不产生两份；自定义公共目录时行为不变；API 28 及以下仍落公共 Pictures。日志判据 `adb logcat -s PictureMediaStore`。
- [ ] **直播弹幕 wss 校验（§3.3 P2-6）真机未验证**：正常直播间能连、能收弹幕；`host_list` 缺失/异常时应提示而不是继续裸连；握手 Cookie 只剩 `SESSDATA` 后，需确认长连接鉴权仍成功。
- [ ] **深链白名单（§3.3 P2-7）真机未验证**：`bilibili://video/...` 等自定义 scheme 仍能跳转；`https://bilibili.com/...`、`https://b23.tv/...` 能进 `GetIntentActivity`；站外链接被拒且不再打 `debug-host` 日志。
- [ ] **极验参数校验（§3.3 P2-8）真机未验证**：滑块验证仍能正常出图、完成登录；服务端返回异常 `gt`/`challenge` 时提示而不是白屏/崩。
- [ ] **遥测内存档位化需真机观察（§3.3 P2-9 部分）**：客户端崩溃载荷已改为只报 `ram_level` 档位（`ram_total/ram_avail` 恒 0），`device_key` 按产品需求**保留采集**（26.10.24 决定回退删除），隐私文案维持 v2 口径；真机上确认崩溃页/控制台按新字段展示正常即可。

- [ ] **登录页扫码中途退出的竞态（§3.38）真机未复现**：修法是「判活搬进 Runnable + 文案走 application context」，判据已由 R8 mapping 还原（崩点 `QRLoginFragment.kt:284`）。真机用例：扫码页在「已扫码，等待手机确认」时点「跳过 / 返回」/ 按表冠退出，反复若干次，不应再出 `Fragment … not attached to a context`。
- [ ] **缓存页空态行的真机观感（§3.34）**：只在 380×380 表盘模拟器上跑过，未在真手表上确认「未分类视频 0 个 + 啥都木有~」两行的字号/间距是否合适。

- [ ] **缓存页长按菜单的真机手感（§3.35）**：模拟器上长按 400ms 即触发；真手表需确认「长按播放/进文件夹」不会与长按误触冲突、5 项菜单在 45% 滚动区里滑动是否顺手、两侧约 24px 空白点不点得到。

- [ ] **本地音轨防重（§3.13）**：含内嵌音轨 MP4 + 残留 M4A 只播主音轨；纯视频 DASH + M4A 有声；纯音频不重复；反复切听视频/重试不叠声；暂停/恢复/拖进度正常；退出后静音；错误音频不崩。需用 Debug 包观察 `AudioTrack` 日志确认 IJK 轨道列表，UNKNOWN 应提示。下载双文件替换与音频专用模式残留问题仍待修，不能当本次已闭环。

- [ ] **倍速 + DASH 高清仍需 arm 真机实测（§3.88 终版）**：补丁版 `libijk*.so` 已在 x86_64 模拟器上验证「纯视频流真倍速 + 外挂音轨同速 + 同刻只有一路音轨」，但**手表（arm64-v8a）没上过机**，且模拟器上「在线 DASH 双流 + 外挂音轨」因夹具网络跑不通（宿主 HTTP 夹具只能经 `adb reverse` 给 ijk 用，媒体服务进程连不上），只用本地双文件夹具等价验证。真机用例：登录 → 选 1080P 起播（走 DASH 双流）→ 切 2x（画面应真变快，`本地进度`/进度上报每墙钟秒 ≈2 秒内容）→ 期间 `音频轨道重新同步` 计数应 ≤1 且不得出现两路声音 → 3x 长按同样验证 → 拖回 1x 画面恢复正常速率。判据看 `/sdcard/Android/data/<包名>/files/logs/rebili.log`（`AudioTrack>` / `进度上报`）与 `adb logcat -s IJKMEDIA`（`Playback rate:`）。

- [ ] **详情页中央加载遮罩收起时机（§3.11）待真机弱网复测**：用弱网（限速/弱信号）打开视频详情页并立即切到评论栏，评论出现后遮罩必须已消失且不挡触摸；再验番剧详情页的"敬请期待"番（无可用季/集）不再永久停在加载图。纯 UI 时序，JVM 单测覆盖不到。

- [ ] **双击缩放复位与旋转解耦（§3.9）待手表真机确认**：开启优先恢复后，默认竖屏→横屏→双击与默认横屏→竖屏→双击均应暂停且不变方向；再验证放大后双击仅复位且不变方向、第二次双击暂停。覆盖双击快进开/关（开时用中间区域）、系统旋屏/软件旋屏、TextureView/SurfaceView；关闭优先恢复时放大后双击也应暂停。

- [ ] **后台播放前台服务崩溃修复（§3.6）真机未复现**：代码已过两 flavor 构建 + 各 478 例单测，但未在低内存手表上跑过。触发用例：起后台播放后 5 秒内切回前台、快速进出播放页，反复若干次，观察是否再出 `RemoteServiceException: Context.startForegroundService() did not then call Service.startForeground()`。
- [ ] **评论带图 12066**：`img_size` 小数截断是已确证的确定性缺陷，但**与 12066 的因果关系未建立**。
  - 12066 在本地快照与上游 collector 均无文档（`bilibili-API/docs/comment/action.md` 错误码表只到 12001-12052）。
  - 验证方法：真机在 `ReplyApi.sendReply` 失败分支打印**完整响应体**取回 `message` 原文。§二十九(第二处) / §36.5
- [ ] **笔记「无法正常加载」根因未确证**：已排除 csrf、`note_id_str` 精度设计、UI 层。
  - 仍存假设：H1「服务端按 `num` 解析 17 位 `note_id` 丢精度 → 79502」、H2「空 `csrf` 被当参数错误」。**头等未验证项**。
  - 验证方法：同一 id 分别按字符串 / 按 num 各发一次对比。§29.1 / §29.5 / §36.5
  - `api/NoteApi.java:259-276`（getNoteInfo 不带 csrf，与 `bilibili-API/docs/note/info.md:65-71` 一致）
  - 未改清单：`note_id` 传参方式一行未动；`activity/video/` 下无任何写入（`VideoInfoFragment.kt:563-569`）
- [ ] **稍后再看「未看完」第二层待真机**：本地判据已修，但服务端 `viewed=2` 的真实语义未确认。若仍为空 → 抓 `history/toview/web` 的请求 `viewed` 值与响应体。§三十五 / §36.5
- [ ] **收藏夹排序行**：逐条排查后**未发现可修缺陷**（两处入口汇合到同一个 `FavoriteVideoListActivity`）。
  - 真机排查指令：`adb logcat -s "debug-收藏夹排序"`，正常应 `sortBar.visibility=0 mediaId=<非0> fid=<非0> readOnly=false`。
  - 临时诊断埋点在 `activity/user/favorite/FavoriteVideoListActivity.kt` 的 `setupSortBar()` 末尾，倾向保留。§二十二 / §36.5
- [ ] **关注分组移动成员未真机验证**：`FollowGroupAdapter.getUserOwnerGroup(position)` 的位置算术只做过静态复核。§三十六 / §36.5
- [ ] **片头片尾跳过**：跳过逻辑本身（`player/ViewPointSkip.kt`）一行未动，行为改变全在默认值 / 引导判据上；真机清单第 199/200 条未跑（且 §四十三 已整体撤回）。§三十三 / §36.5
- [ ] **内置播放器「切后台回来视频位置出错」代码已修，真机只跑到「未复现」**（26.10.11 用户报告；同日已在 vivo 手机上真机跑过，见下方「真机记录」——本机触发条件不成立，**不等于修复已被证实**）。
  - 机制①（**已修**）：`activity/player/PlayerActivity.kt` 原本**没有** `onSaveInstanceState`/`onRestoreInstanceState`，Activity 被系统回收后重建只能回退到**进入页面那一刻**的 Intent `progress`（`:372` 读、`:3553-3560` `seekTo(progress_history)`）。现加状态保存/恢复（`STATE_POSITION` + `restoredFromSaveState`；恢复的那次不再弹「已从上次的位置播放」）。
  - 机制②（**已修**）：`:2138` 的默认显示方式为 SurfaceView（`player_display` 默认 `SDK_INT < 26`，手表多为 API≥26），切后台回来 Surface 必被销毁重建 → `:2127-2133` 的 `onSurfaceReattached` 用**UI 进度条的值** `seekTo(seekbar_progress.progress)`；叠加 `enable-accurate-seek` 默认 0（不精确 seek）⇒ **每次回前台往回跳一个关键帧间隔**（数秒），反复切会累积。现改为 `seekTo(video_now)` 并加 `!isLiveMode` 守卫，点播开启 `enable-accurate-seek=1`；直播下该进度条恒为 0（`:1190` 只在非 live 分支赋值）的问题随之消失。
  - **遗留未修**：链路页 `activity/video/JumpToPlayerActivity.kt` 的 `onCreate` 仍无 `savedInstanceState` 守卫，系统回收后会重新取流并再 `launch` 一次（不直接造成位置错，但会白跑一次取流）。
  - 真机判据（埋点已在代码里，无需另加）：`adb logcat -s "PlayerActivity>onCreate" "PlayerActivity>onPrepared"` 应看到「恢复位置」+「进度跳转」；`PlayerActivity>checkStall` / `PlayerActivity>switchLiveCandidate` / `PlayerActivity>getExtras`（「直播候选」）对应看门狗与线路轮换。用例见 `docs/archive/review/device-test-plan.md` D3-11~D3-15。
  - **真机记录（26.10.11，设备 vivo `V2229A` / PD2229，Android 16 / SDK 36，arm64-v8a，1080×2520，序列号 `10AC9S2M4L000QL`）**：`adb` 深链 `bilibili://video/170001` → 详情页 → 播放 → 分 P → `PlayerActivity` 正常起播（画面 + 弹幕 + 进度文本，例 `00:12/03:19`）。① 熄屏再亮屏：`00:56 → 00:58`，无回跳；② 把详情页压到播放器之上再返回：无回跳。**但同样的两次操作在修复前的旧 release 包上也复现不出回跳**，说明这台手机上原始触发条件不成立——熄屏不销毁 SurfaceView 的 surface，按 HOME 切走后 vivo 会把任务冻结/回收（再回来落在首页而不是播放器）。⚠️ 装的都是 **release 包**，而 `Logu` 的开关默认只在 `BuildConfig.DEBUG` 打开（`BiliTerminal.kt:228-230`），所以本机 logcat 里**看不到任何 `PlayerActivity>` 埋点**——上面的 logcat 判据必须在 debug 包上执行。
  - 调研与修法见 `docs/archive/superpowers/specs/2026-10-11-player-network-resilience.md` P1-1 / P1-2。§四十四
- [ ] **评论侧「长按双弹窗」未复现**（`res/layout/cell_reply_list.xml` 兄弟节点分析证明布局层面不可能）。§30.2 / §30.4
- [ ] **`MANAGE_DEDUP_MS = 400L` 去重未真机验证**：`app/build.gradle` 只有 junit 与 org.json，**无 Robolectric**，`SystemClock` 无法进 JVM 单测。§30.3 / §30.4
- [ ] **300×300 表盘上弹窗实际可点性未在真机确认**。§30.4
- [ ] **未决项：`activity/reply/ReplyInfoActivity.kt` 分页插入起点 `+2` 疑似越界** ——「发现但未修改」。
  - 建议单独立项 + 真机复现（楼中楼详情页 → 切时间排序 → 持续下滑翻页 → 观察 `IndexOutOfBoundsException` / `Inconsistency detected` / 新页错位）。
  - 判越界前必须先确认**宿主页第 0 位放了什么**：同一个 `ReplyAdapter`，在 `activity/reply/ReplyFragment.kt:250`（主评论列表）第 0 位是「头部占位」，在 `ReplyInfoActivity`（楼中楼详情页）第 0 位却是**根评论**，前提正好相反。§30.5b
- [ ] **动态详情页线上 NPE 未真机复现**：用户只给了混淆栈、未给触发场景。若补丁后同一栈还在，说明真凶是被 R8 内联进来的别处，需新 mapping 重新 retrace，并附触发页面 + 操作步骤。§38.5 / §38.6
- [ ] **收藏夹顶部工具条三处修法均未真机复现**（团队无设备）：
  - (1) 取消勾选不暗（排除法：`itemView.alpha` 只由 `VideoCardHolder.applySelection()` 写）。
  - (3) 若真机仍在列表中途展开，说明 `recyclerView.canScrollVertically(-1)` 不可靠，需改用 `computeVerticalScrollOffset() == 0`。§39.5
- [ ] **滚动收放与长按面板的真机手感未验证**：动画与手势走 View 层，JVM 单测覆盖不到（正因如此才抽出 `util/view/ScrollRetractDecider.kt`）。§37.3 / §37.2
- [ ] **新增反馈页 / 关于页入口 / 崩溃自动上报 / 启动弹窗全部未真机跑过**（条目已补进回归清单第 216~222、223~227 条，**尚未执行**）：
  - ① 关于页 → 反馈页能进、分类、4000 字上限、断网报错文案、成功清空；
  - ② 三个开关关掉后抓包确认无到 `rebiliterminal.zsapp.asia` 的请求；
  - ③ 故意崩溃一次，崩溃页显示「报错ID」且服务端 `/admin/api/crash` 可见；
  - ④ 公告页在上游接口挂掉时仍能显示自建公告（反向也要成立）。正文见 `docs/archive/review/fix-progress-history.md` §41.7。
- [ ] **隐私同意闸门未真机验证**（条目已补进回归清单第 228~231、236 条，**尚未执行**）：全新安装 / 清数据后首次启动必须弹一次；点「不同意」能正常进主界面且抓包无请求。正文见 `docs/archive/review/fix-progress-history.md` §41.8。
- [ ] **minSdk 19 未在 API 19~23 上做过任何运行验证** —— 本机 `D:\Program Files\android-sdk` 下**既无 `emulator` 也无 `system-images`**，本轮 `minSdk 24 → 19` 的结论全部来自静态审计 + 依赖元数据 + lint + dex 反汇编，**没有真机/模拟器背书**。
  - 需覆盖的真机用例已写进 `docs/archive/review/device-test-plan.md`（§1.2 设备侧 + T12 平台 API 兼容）：任一含 `TextClock` 的页面（43 个布局里都有，4.4 上原本会因 4 参 `TextView` 构造直接崩）、列表页 `AsyncLayoutInflaterX.fadeIn`（`activity/base/BaseActivity.kt:435`）、任一写 SharedPreferences 的操作（登录态刷新 / 设置项开关）、发评论或回复（`activity/reply/WriteReplyActivity.kt:177`）、设置页各图标（验证 `res/drawable-v21` 之外的兜底色可接受）、通知（三处 `NotificationChannel` 均有 `SDK_INT >= O` 闸，预期低版本不弹且不崩）。
  - 逐项静态结论与依赖下限见 `docs/architecture-map.md` §1.1。

- [ ] **调试桥（§3.14）未真机验证**：`DebugBridge` 的 `am broadcast` 能否回显结果、`appops set <包名> SYSTEM_ALERT_WINDOW allow` 后 `start` 能否真的拉起 `exported=false` 的页面、`http` 命令在风控下是否可用、日志落盘路径是否可 `adb pull` / `run-as` 取到——全部只过了编译与静态核实。逐条验证清单见 `docs/review/agent-test-guide.md` §2 与 §6。
  - 风险最高的一条：个别精简 ROM 的 `adb shell` 可能不持有 `DUMP` 权限，那样导出组件会被 AMS 拒绝，调试桥整体不可用。兜底方案见手册 §6（`adb root`，或 `run-as <包名> am broadcast`——同 uid 不受导出限制）。

- [ ] **番剧界面补齐（§3.8）未真机验证**：季切 tab、两行选集卡片、追番按钮、全量选集弹窗、历史卡片一键续播、番剧心跳 5 秒 + 看完上报 -1 全部只过了编译 + JVM 单测。真机要看的：① 多季番能切到第二季/剧场版并出集；② 从别的季切回来回到这一季上次看的那一集；③ 一集播完后重进/从历史进来是接下一集而不是停在本集末尾；④ 历史里的番剧卡片点一下直接起播且位置对；⑤ 未登录点追番要提示，不能静默失败。

- [ ] **缩放后左右滑动调进度（§3.32）待真机确认**：开启「左右滑动控制进度」+「视频可缩放」→ 捏合放大 → 松手 → 单指横滑，应完全不出现进度浮层、不动进度条；双指缩回 1 倍后横滑恢复；再验关掉「缩放时可移动」时放大后单指拖动不再移位。判据单测覆盖不到 ACTION_UP 之后的手势分发。

- [ ] **长按顶栏三模式（§3.33）待真机确认**：①「不响应」下普通页面与视频详情页长按都无动作（OPPO 系手表长按顶部本意是系统控制中心，应用侧至少不能自己再动）；②「仅视频详情页」下普通页面无动作、视频详情页仍关闭所有详情页；③「全部页面」= 旧行为；④ 切档后不用退出页面即时生效；⑤ 26.10.19 显式关过旧开关的老用户，升级后设置页显示「仅视频详情页」（不是「不响应」）。

- [ ] **短视频取流协议修订（§3.41）待真机**：本会话无可用设备，只过了两 flavor 构建 + JVM 单测。真机用例：①进入短视频页首屏能出内容、连续上下滑不出现相邻重复视频；②反复退出再进短视频页，首屏正常（旧实现的静态页码已删）；③飞行模式进入 → 出现「获取视频列表失败」，恢复网络后从页面既有入口重试成功；④观察是否还有明显横屏内容（解析层已丢弃 `dimension` 明确宽>高且 `rotate==0` 的卡片）。

- [ ] **缓存视频断点续播（§3.53）待真机**：本机只过了 `:app:assembleDebug` + 两 flavor 单测，位置读写纯函数有 JVM 覆盖，但「退出再进真的接着播」必须真机。用例：① 缓存视频播到约 01:30 退出再进 → 从约 01:30 起播且提示「已从上次的位置播放」（±2s）；② 关掉「从历史位置播放」→ 从头且不提示；③ 看到距结尾 5 秒内 / 播完 → 下次从头；④ 多 P 视频 A 播一半、B 从头 → 各自记住不串；⑤ 单文件、DASH 双文件、听视频（`audio_only`）、虚拟合集连播、多 P 选择页五个入口都验；⑥ 缓存文件夹改名/移动后仍能续播；⑦ 播放中直接杀进程（后台划掉）→ 最多丢 15 秒；⑧ 文件被删/损坏/换成更短的版本 → 静默从头不崩。

- [ ] **短视频页圆屏适配（§3.65）真机未验证**：只在 MuMu 模拟器（`wm size 320x320` + `wm density 160`）上验过。真机用例：圆屏手表开启「圆屏适配」→ 进短视频页，顶栏标题/返回箭头不贴边、底栏进度条与按钮不被裁角、竖屏视频按内接圆缩小居中且不变形；关闭开关后恢复原样；圆屏开时上下切视频、左右滑详情页、弹幕显示均正常。

### 2.2 功能与工程待办

- [x] ~~**「自动跳过片头片尾」处于撤回状态**~~ ——26.10.22 **已恢复**（默认关闭），恢复清单 ①–④ 已完成，⑤⑥（发版公告与版本号）随下次发版做。§3.3
- [ ] **播放器弱网 / 直播：P0-1、P0-3、P1-1、P1-2、P2-1 与半个 P2-3 已实施，其余待办**（26.10.11 已过编译 + 两 flavor 各 478 个 JVM 单测全绿；真机只验证到「播放器能正常起播、回前台位置未复现回跳」，**看门狗/线路轮换未在真机触发**——release 包 `Logu` 默认关闭，需 debug 包 + 真实直播间）：
  - 已实施：**P0-1** `activity/player/PlayerActivity.kt` 的 `setDisplay()` 给在线流加 `rw_timeout=10s` / `addrinfo_timeout=5s` / `reconnect_streamed=1` / `reconnect_delay_max=5s`；**P0-3** 新增 `checkStall()` 停滞看门狗（直播 20s / 点播 30s 不动就重试，直播切候选线路、点播原地重建；最多 3 次、冷却 15s、位置前进 5s 后复位）；**P1-1** 状态保存恢复；**P1-2** 重挂 Surface 改用 `video_now`；**P2-1** 删 `activity/video/ShortVideoPlayerActivity.kt` 里实测不存在的 `max_cached_duration`；**P2-3 的一半** 新增纯函数 `util/live/LiveStreamSelector.kt`（14 个单测），候选按「FLV 各线路 → HLS」排序，随 Intent `live_url_candidates` 交给播放器做运行时线路轮换。
  - **未做**：P0-2 的「重连前重调 `getRoomPlayInfo` 拿新 URL」（现在只在已到手的候选里轮换，URL 过期后仍会失败）、P1-3 后台保活与音频焦点、P2-2 短视频激进参数复核、`PlayerActivity.kt:892` 的 `dns_cache_clear`（二手证据不足，未动）。
  - **明确不采纳**：P0-4 提高直播默认清晰度（`util/TerminalContext.java:259` 写死 `qn=80`=流畅）——用户诉求是弱网可看，低码率是刻意保守；`reconnect_at_eof` 不开——点播正常播完会被当成 EOF 触发重连，有「永远播不完」的风险。
  - 建议清单见 `docs/archive/superpowers/specs/2026-10-11-player-network-resilience.md` §4（已归档，行号为当时位置）；该文档 §2.4 附**本仓库 `.so` 的选项存在性实测**（`reconnect_on_network_error`、`max_cached_duration`、`http-detect-range-support` 均不存在）。§四十四
- [ ] **播放器核合并剩余项**（26.10.27 从已归档的 `docs/archive/superpowers/specs/2026-09-10-player-core-merge.md` 回迁）：S1–S3 已完成，S6 里 `PlayerIntegrator` / `PlayerScaleMode` 已删、`DanmakuManager.loadFromProtobuf` 已换成 `loadFromProtobufSegments`。仍未做：**S4** 短视频迁到 `player/VideoPlayerCore.kt`（它至今**没有任何消费方**，`PlayerActivity` 只在注释里提到它；短视频仍直接用 `IjkPlayerBridge`）；**S5 后半** `TextureView` 复用与相邻页预创建；**S6 收尾** `player/PlayerControlDelegate.kt` 全库零外部引用，删或接线待决定。
- [ ] **下载链路剩余缺陷**（26.10.27 从已归档的 `docs/archive/superpowers/specs/2026-10-13-download-bug-diagnosis.md` / `2026-10-13-download-engine-optimization.md` 回迁；C3、B1 播放侧、A1/A2、A5 已修，见归档快照 §3.12/§3.13/§3.36）：
  - **B2** `activity/video/local/LocalPageChooseActivity.kt` 删除分 P 时 `videoFileList.removeAt(position)` 按页下标删，双文件目录（旧缓存或合并失败时 `video.mp4` + `audio.m4a`）每页占 2 个元素，会删错；**B3** `LocalListActivity.playVirtualCollection()` 只取 `.mp4`，双文件目录的虚拟合集无声。26.10.25 起新下载合并成单文件（§3.66），这两条只在旧缓存 / 合并失败时可达。
  - **A3** 分片失败回退时进度先满后退；**A4** 未知大小时伪进度封顶 0.9（`DownloadProgressMath.PSEUDO_MAX`）；**A6** 通知栏总进度按「当前文件」口径汇总，跨阶段时可能回退。
  - **引擎层**：没有真正的断点续传（分片失败仍整文件单连接重下）、没有「仅 WiFi 下载」、没有磁盘空间预检（只有合并前的 `StatFs` 检查）、弹幕仍走单文件 XML。分片看门狗 / Range 校验 / 强制 HTTP/1.1 已于 26.10.25 落地（§3.63）。
- [ ] **26.10.05 那个已发布的 Release 说明没能补上**：本机无 `gh` CLI 也无 GitHub token，只能人工在网页端编辑，把 `update_log_current` 的 28 条粘进去。§40.5
- [ ] **服务端没有告警渠道**：只有 systemd `Restart` + `health.log` 留痕（`*/10` 探活 cron 失败只追加一行，要真告警还得接通知渠道）。§42.9
- [ ] **管理控制台只有口令登录**，没有 TOTP / IP 白名单；口令泄露 = 全部后台能力。§42.9
- [ ] **控制台 WebUI 没有真机 / 真浏览器回归记录**（环境无浏览器），只验证了 HTML 结构与接口联通。§42.9
- [ ] **话题广场仍是单页**（`bottom = true`，不翻页）；`page_info.has_more` 已在数据里，将来要翻页可直接接。§三十四
- [ ] **`WriteReplyActivity.kt` 的 `pendingUploads++/--`（`:224`/`:253`）未加同步保护**：已发现主线程入口与后台 `finally` 各改一次、无同步，「与本次用户报的 bug 无关，改动会引入无法验证的风险」→ **发现但不动**。§29.5
- [ ] **§32 遗留的视觉面未动**：
  - 新增 `colorError` 语义色**未重新做对比度测量**（`docs/archive/visual-experience-report.md` §2.25：`#EBE0E2` 叠 `#FF6699` 约 2.1:1）；
  - §2.12「86 处布局引用静态调色板」与 `styles.xml` 组件样式硬编码同源，本次未动。§三十二
- [ ] **收藏夹排序行「更醒目」的视觉标识没做**（无依据，且会动 `res/layout/activity_simple_refresh.xml`）。§二十二
- [ ] **`docs/review/real-device-regression-checklist.md` 第 159 条未改**（不属该轮范围）。§二十二
- [ ] **「个性化设置」开关落点需用户确认**：`long_press_panel_enable` 落在 `activity/settings/SettingPrefActivity.kt`（`setPageName("偏好设置")`，「内容与浏览 → 通用偏好」进入）的「功能」组、紧挨「长按复制」；用户心里的「个性化设置」若是别处，搬走只需改这一行。§37.1
- [ ] **`DynamicApiTest` 曾失败的 2 例（`DynamicApiTest.kt:151`/`:203`）与 `NoteApiTest.kt:198` 1 例**：**已闭环**（§36.3 修掉并全绿），修法见归档「长期结论」；此处仅作登记。
- **功能类 backlog 一律看** `docs/superpowers/plans/2026-08-27-new-features-roadmap.md`，不在本文件展开。

### 2.3 明确不做（别再排期）

- **F4 漫画**：快照 `bilibili-API/docs/manga/` 无任何「追漫/收藏漫画列表」接口，也无漫画搜索接口，项目内漫画实现为零（只有 `activity/user/VipActivity.kt:79-80` 会员权益文案）。
- 用户 26.10.04 明确「漫画算了」，记 **不做**（不是暂缓：暂缓意味着还打算做）。§三十一
- **番剧 B/E 两项（26.10.11 用户拍板暂不做，别顺手补）**：B = 进番剧详情页自动查历史接口定位"上次看到的那一集"；E = 实验室里的「番剧进度诊断页」。同一轮对比里远端的 `BangumiActivity.java`（番剧主页：新番/追番/索引）是**跨全部 tag 都不可达的死代码**，同样不要照抄。§3.8

### 2.4 视觉技术债（26.10.10 从 `docs/archive/visual-experience-report.md` 回迁；数字均为当时 grep 实测）

> 真机验证项已并进 `docs/review/real-device-regression-checklist.md` 第十三节（第 238~247 条）。下面只留「代码里真的还没做」的部分。

- [ ] **顶栏仍是 45 份手抄复制品**：`cell_topbar.xml` 与 `include layout="@layout/cell_topbar"` 全工程 **0 匹配**，`android:id="@+id/top"` 在 **45 个 layout** 里各自手写（`activity_simple_refresh.xml:9`、`activity_menu.xml:10`、`activity_player.xml:144` …），`@dimen/topbar_height`（30dp / w300dp 36dp）**0 引用**（死 token）；退出靠 `BaseActivity.kt:182-197 setTopbarExit()` 代码兜。**批次 4 的公共顶栏成果实际已被回退**（详见 `docs/architecture-map.md` §8.1/§8.5/§7.4），顶部返回是 45 个页面的唯一返回入口、高度无 48dp 保证。值得做（风险中）。
- [ ] **无障碍：`contentDescription` 覆盖率 4.3%**：全 `res/layout` 仅 5 处（`activity_loading.xml:46`、`cell_follow_group.xml:46`、`cell_loading.xml:9`、`fragment_qr_login.xml:57`、`item_vote_option.xml:20`=`@null`），而 `<ImageView|<ImageButton` 共 **115 处** → 缺口 **110**；装饰图也没有 `importantForAccessibility="no"`。TalkBack 全站不可用。可脚本化批量补，风险低。
- [ ] **播放器触控热区大量 <48dp**：`activity_player.xml` 28dp 高 11 处（`:310/319/328/337/347/357/367/388/397/406/418`）、37dp 5 处（`:226/236/245/255/265`）、进度条 `:201`=20dp / `:464`=32dp / `:645`=35dp；该文件对 `@dimen/touch_min`（48dp）**0 引用**。（2.17「点下去零反馈」已修，尺寸没改。）
- [ ] **无自适应图标**：`res/mipmap*` 只有 `mipmap-nodpi/`（约 52 个 png/webp），无 `mipmap-anydpi-v26`、无 `ic_launcher_foreground/background`；桌面图标形状由系统强制裁切。需要设计资产，非纯代码任务。
- [ ] **残留 px 单位与全透明短写色**：`layout_height="0px"` `activity_download.xml:23`、`cell_video_local.xml:87`，`1px` `activity_player.xml:16/17`（共 5 处）；`background="#6000"` `activity_search.xml:129`，`background="#0000"` `cell_article_end.xml:117/140/164` + `fragment_video_info.xml:229/252/276`（共 7 处）。一次改完，零风险。
- [ ] **过小字号**：`textSize="8|9|10|11sp"` 共 **57 处**，其中真正不可读的 6 处是 8sp（`activity_player.xml:181`）、9sp（`activity_captcha_webview.xml:33`）、10sp（`activity_feedback.xml:66/109`、`activity_player.xml:479/493`）；`android:alpha` 在 layout 下仍有 **142 处**（文字靠 alpha 拉层级基本没动）。先修那 6 处成本极低。
- [ ] **浅色主题 `Theme.BiliClient.Light` 是死样式**：`themes.xml:45` 定义、全工程 **0 引用**（活的是 7 套 `Theme.*` + `Theme.*.NoSwipe.AppCompat`）；弹幕颜色仍无设置项（grep `danmaku_.*color` 0 匹配）。二选一：接线或删掉。
- [ ] **`colors.xml` 别名膨胀**：`<color name=` **≥328 条**（`zhihu_*`/`iqiyi_*`/`purple_fantasy_*`/`rainbow_*`/`classic_gray_*`/`terminal_*` 各约 60 条，绝大多数指向同一 hex）。有了 `ui/appearance/ColorScheme.kt` 这个真源后，别名层是纯负担；清理需一批回归。风险中。

**别再重复排查（已排除）**：WebView 白底闪屏**不存在**；菜单按钮 `MaterialButton` 默认 `minHeight=48dip` **已达标**；`ui/widget/recycler/` 与 `fragment_empty.xml` 是**死代码**（不当在用组件排查；`BiliTerminalApp.kt` 已于 26.10.02 删除）。**不做**：裸 `Activity` 收编（`PlayerActivity`/`ImageViewerActivity`/`SplashActivity`/`GetIntentActivity`）——播放器保持黑色系统栏是有意为之，且 `PlayerActivity.kt` 3000 行，收益不抵回归风险（理由已进 `docs/architecture-map.md` §8.7.3）。**可接受**：`CardStyle`/`ButtonStyle` 强制 `clickable+focusable`；间距/圆角 token 已建但布局未全量改用；同构布局重复；布局层静态色已清干净（`@color/(card_dark|pink|divider_dark|pink_light)` 在 `res/layout` 0 处）。

## 3. 最新状态快照（26.10.22 ~ 26.10.31）

> §3.0 ~ §3.51（26.10.09 ~ 26.10.21）已原样归档到 `docs/archive/review/fix-progress-snapshots-261009-261021.md`，**编号不变**：本文其他地方写的 §3.0~§3.51 都去那里找。

### 3.52 存储兼容：默认目录按 API 分档 + 无全盘权限时自动降级（Wear OS，26.10.22，待真机）

- **背景**：§3.28 只把「判据」从权限查询改成试写，试写失败之后仍然是 `finish()` / `return` —— 手表（Wear OS）上「所有文件访问」的接口存在，但系统 UI 不提供这个开关，用户授不了权，于是缓存/下载整条链路走不通。用户要求同时适配高低版本 Android 与这类无法授权的设备。
- **根因（能力假设错）**：默认目录 `Android/media/<包名>/` 在 API 29+ 属公共媒体目录，写它需要 `MANAGE_EXTERNAL_STORAGE`；API 30+ 上只有用户在系统 UI 里勾选才生效，`isExternalStorageManager()` 查得再准、权限在清单里声明得再全都救不了。而下载链路把「能写这个目录」当成了功能前提。
- **改法（`util/FileUtil.java` + 新增 `util/StoragePathPolicy.kt`）**：
  - `util/StoragePathPolicy.kt`（纯逻辑、无 Android API，可 JVM 单测）：`defaultVideoDir(apiLevel, mediaDir, appExternalDir, internalDir)` —— API≥21 用 `Android/media/<包名>/`，API 19/20 用 `getExternalFilesDir("video")`（应用专属、免权限），都拿不到再退 `filesDir/video`；`chooseVideoDir(...)` 保留老用户目录；`hasRealContent(names)` 把 `.nomedia` 这类隐藏标记排除在「有内容」之外。
  - `FileUtil.getVideoDownloadPath()` 拆成「正式目录 `getConfiguredVideoDownloadPath()`」与「降级目录 `getFallbackVideoDownloadPath()`（`getExternalFilesDir("video")` → `filesDir/video`）」，进程级 `videoFallbackActive` 决定返回哪个 —— 下载服务、缓存列表、元数据必须看到同一个根目录，不能各算各的。
  - `resolveVideoDownloadDir(Context)` / `resolvePictureDownloadDir(Context, File)`：正式目录可写就用正式目录（若此前降级过，`mergeMoveDirectoryContents` 把降级目录里的东西搬回，只增不覆盖、同名改成 `name (1).ext`、跳过 `.nomedia`、绝不删目标已有内容）；正式目录不可写但降级目录可写就降级并 toast 实际位置（每个进程只提示一次）；两个都写不了才返回 null。
  - **下载链路不再主动跳系统设置页**（手表上那是死路），`requestStoragePermission` 只保留给设置页里的手动入口。所有入口统一先 resolve：`LocalListActivity`（含 `onResume` 复检，降级状态变过就刷新列表）、`VideoInfoFragment.downloadClick`、`DownloadActivity.onCreate`（图片走 `resolvePictureDownloadDir`）、`VideoQuickCache.handle`（列表长按缓存，以前完全不判可写）、`DownloadService.startSingle` / `startDownload` / `onStartCommand`（服务可能在新进程恢复，标记为空，必须再 resolve 一次）。
  - 设置页「缓存路径」预填 `getConfiguredVideoDownloadPath()`（降级时不会把降级目录写进设置项），新增「去系统设置授予所有文件访问」入口与说明（手表会提示打不开，下载自动降级）。
  - 图片降级保存到应用专属目录时，完成提示改用 `msg_picture_saved_fallback` 明说「不在系统相册中」，且不再调 `PictureMediaStore.scan`（降级目录扫了也不会有 MediaStore 记录，白扫）。
- **为什么不用其它方案**：SAF `ACTION_OPEN_DOCUMENT_TREE` 在手表上通常没有 `DocumentsUI`；`MediaStore` 插入只能管媒体单文件，而缓存目录还要放 `.quality` / `.DOWNLOADING` / 弹幕等附属文件；回退 `targetSdk 29` + `requestLegacyExternalStorage` 是能力倒退，且本仓 `targetSdk 34` 下已失效。结论：把「全盘权限」从**必需**变成**可选**。
- **单测**：新增 `StoragePathPolicyTest` 12 例（API 分档、配置优先、老目录保留、隐藏文件不算内容）、`FileUtilMergeMoveTest` 5 例（搬移、同名不覆盖、递归合并、跳过 `.nomedia`、空输入安全）。判据与理由已同步 `docs/architecture-map.md` §7.41 ④。
- **验证（本机，已跑）**：`$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; ./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --offline --no-configuration-cache` → `BUILD SUCCESSFUL in 1m 49s`；两 flavor 各 **75 个测试类 / 800 例 / 0 失败 0 错误 0 跳过**。
- **真机待验（全部未验证）**：① 手表 Android 7/8/9/11 首次进缓存页 / 点下载直接成功，不弹系统设置页；② 把缓存路径改成公共 `/sdcard/Download/xxx`（无权限）后下载**自动降级**并提示实际位置，文件落在应用专属目录、缓存页能列出；③ PC 的 MTP / 文件管理器能看到该目录（`Android/media` 比 `Android/data` 可见性略好，但都比公共目录差，需在文档/教程里说清）；④ 图片降级保存提示「不在相册中」；⑤ 手机上授权后回到缓存页自动切回正式目录，降级期间下的缓存被搬回。

### 3.53 缓存视频断点续播：内置播放器播本地/缓存视频也从上次位置起（26.10.22，待真机）

- **需求**：内置播放器播放**下载的缓存视频**时也要「从历史位置播放」。此前只有在线视频（含番剧）能续播——位置来自服务端观看历史；本地视频入口构造 `PlayerData(TYPE_LOCAL)` 时从不填 `progress`，`onPrepared` 的 `resumeTarget` 恒为 0，所以永远从头。用户确认口径：进度存 `.video_meta.json`、复用现有「从历史位置播放」开关（不新增）、多 P 按分 P 分别记、纯本地记录、四个入口全要（缓存列表单文件/DASH、多P 选择页、虚拟合集连播、听视频/纯音频）。
- **为什么入口一行都不用改**：四个入口传的 `video_url` 都指向该视频（或该分 P）自己的缓存目录，`File(video_url).absoluteFile.parentFile` 就是 `.video_meta.json` 所在目录。播放器在 `getExtras()` 里反推目录、在 `doSwitchPage()` 里跟着切 P 换目录，入口侧无需传新 extra。
- **数据层**：
  - `model/VideoMeta.java` 新增 `progressMs`（0=从头/已看完）与 `durationMs`（0=未知；存它是为了判断"停在距结尾几秒"），`toJson`/`fromJson` 读写，老 meta 缺键时 `optLong` 兜 0。
  - 新增 `util/LocalVideoProgress.kt`（纯函数、无 Context/文件，可 JVM 单测）：读与写用**同一条判定**，避免"存得进去读不出来"——`<=5s` 无意义、`时长-位置<=5s` 视为看完、位置超过时长视为看完；时长未知（<=0）只按位置判断。
  - `util/VideoMetaManager.kt`：新增 `readResumeMs(folder)` 与 `@Synchronized saveProgress(folder, positionMs, durationMs)`；`saveMeta` 也加 `@Synchronized`。`saveProgress` **先读旧 meta 再合并**（直接 new 一份会冲掉 title/画质列表）。
  - `service/download/DownloadRepository.kt:saveVideoMeta` 原来是 new 一份 `VideoMeta()` 整份覆盖 —— 这正是重下载/换清晰度会冲掉进度的坑，改成先 `readMeta` 合并。
- **播放器侧（`activity/player/PlayerActivity.kt`）**：`getExtras` 里对非 http 的 url 设 `localMediaDir` 并用 `readResumeMs` 补 `progress_history`（单位毫秒，与在线一致）；`doSwitchPage` 切 P 时重算目录并清去重标记；新增 `maybeSaveLocalProgress()`（挂在既有 250ms 定时器上，墙钟 15s 节流）+ `saveLocalProgressNow(reason)`（**主线程同步写**：异步会让"退出补写"被队列里的周期写覆盖）；落盘点＝周期/`onPause`/`onStop`/`onDestroy`/`finish`/`onCompletion`（看完清零）；`onCompletion` 后单曲循环重新开始时清 `finishWatching`，否则第二轮会一直按"看完"写 0。续播本身复用 `onPrepared` 既有那段（`restoredFromSaveState` → `player_from_last` 开关 → 0），未新增开关。
- **已知取舍**：播放器内手动/自动切 P 从 0 起播（沿用 `progress_history = 0` 的既有行为）；位置在首次 prepare 之前不会落盘（防"刚进页面就退出"把旧进度清掉）。
- **单测**：新增 `LocalVideoProgressTest`（14 例：阈值边界、结尾容差、超时长、时长未知、读写一致性循环）与 `VideoMetaProgressTest`（6 例：老格式 meta 读回 0、位置往返、落盘不冲掉 title/aid/cid/画质列表、看完清零、时长未知可续播、目录不存在不抛）。`org.json` 在单测里是真实现（`app/build.gradle` 有 `testImplementation 'org.json:json'`），所以 `VideoMeta` JSON 与文件读写都能在纯 JVM 里测。
- **验证（本机，已跑）**：`.\gradlew.bat :app:assembleDebug --offline` → `BUILD SUCCESSFUL`（121 tasks，1 分内）；`.\gradlew.bat :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --offline` → `BUILD SUCCESSFUL`，两 flavor 各 **76 类 / 804 例 / 0 失败 0 错误**（其中新增 `LocalVideoProgressTest` 12 例、`VideoMetaProgressTest` 6 例）。
- **真机待验**：播到 01:30 退出再进应从约 01:30 起播且提示「已从上次的位置播放」（±2s）；关掉「从历史位置播放」则从头且不提示；看到距结尾 5 秒内/播完 → 下次从头；多 P 不串位置（A 看到一半、B 从头）；单文件 / DASH / 听视频 / 虚拟合集 / 多 P 选择页均生效；缓存文件夹改名或移动后仍有效；视频被删/文件损坏/时长变短时静默从头不崩。

### 3.54 播放页禁止系统右滑返回（无条件，26.10.22，待真机）

- **需求**（用户确认口径）：长视频播放页**禁止通过右滑手势退出应用**，且**不设开关**、页内处处生效；用户明确补充「不要屏蔽其他手势（如滑动调节进度和移动画面等）」。范围只到 `PlayerActivity`（投稿 / 番剧 / 直播 / 缓存视频都走它）；短视频页 `ShortVideoPlayerActivity` 的「右滑退出」是产品定义的另一种语义，不动。反馈方式为静默忽略（不弹提示）。
- **根因（与 issue #1 同源）**：手表右滑返回由窗口属性 `android:windowSwipeToDismiss` 实现，**窗口层直接 `finish()`，不走 `onBackPressed`**，应用收不到。改前 `PlayerActivity.onCreate` 是 `val noSwipe = swipeSeekEnabled || SharedPreferencesUtil.getBoolean(SettingsKeys.BACK_DISABLE, false)`，即**两个开关都关着时右滑照常退出**——这正是用户看到的现象。
- **改法（一处）**：`activity/player/PlayerActivity.kt` `onCreate` 删掉那个复合判断，改为无条件 `setTheme(ColorScheme.themeResId(theme, noSwipe = true))`；`swipeSeekEnabled` 的赋值保留（`onBackPressed` 仍用它判断手势型返回键）。不新增设置项、不改基类、不动 `res/`。
- **为何不碰其他手势**：`*.NoSwipe.AppCompat` 相对主主题**只多一条 `windowSwipeToDismiss=false`**（`ColorScheme.kt:526-557` 的 7 套映射 + `res/values/themes.xml`），配色 / 圆角 / 字体全同；它只改窗口属性，**不参与触摸分发**，滑动调进度、双击、长按倍速、捏合缩放与拖动画面仍由本类自己的手势逻辑处理。
- **行为边界**：左滑 / 右滑调进度（`PLAYER_SWIPE_SEEK`）开启时 `onBackPressed` 里 `if (swipeSeekEnabled) return` 的既有兜底**保留未改**（issue #1 对「把右滑模拟成返回键」的手表）；未开启时返回键照常退出播放页。退出路径仍是顶栏返回按钮 + 返回键。
- **手表端复核（26.10.22 二次审查；用户提示「这个主要是针对手表端的右滑返回设计的」）**：
  - **覆盖的正是手表那条链路**：关闭的就是手表系统「窗口层 swipe-to-dismiss」(`android:windowSwipeToDismiss`)，即 issue #1 实测机型的机制（窗口层直接 `finish()`、应用收不到）。这同时兑现了 `docs/archive/superpowers/specs/2026-10-20-player-touch-lock.md` §4.4 留的预案——「若右滑拦截有副作用，退路是播放页无条件 `noSwipe = true`」。
  - **映射无遗漏**：`ColorScheme.noSwipeThemeResId`（`ui/appearance/ColorScheme.kt:550-558`）对 6 套具名主题 + 默认主题都有 `*.NoSwipe.AppCompat`，没有回落到主主题的分支；`res/values/themes.xml` 里每套 `NoSwipe` 相对主主题只多 `windowSwipeToDismiss=false`，配色 / 圆角 / 字体一致。
  - **无子窗口干扰**：`PlayerActivity` 全文件没有 `Dialog` / `PopupWindow` / `WindowManager.addView`（弹幕、控制栏、互动选项都是 Activity 内的普通 View），不存在「某个弹窗窗口自己还开着右滑」的情况；视频画面是 `layout_video.addView(surfaceView/textureView)`，不是独立 Window。
  - **手机端无副作用**：该窗口属性只有手表系统消费，手机上换 `NoSwipe` 主题的效果与主主题完全相同。
  - **已知不覆盖的两类（用户 26.10.22 拍板：保持现状）**：① 用「模拟按下返回键」实现右滑的手表（应用自己的设置文案点名米兔等，见 `docs/copy-inventory/04-settings.md` 的「禁用返回键」说明）——到应用侧就是一个 `KEYCODE_BACK`，与物理返回键不可区分，窗口属性对它无效，播放页在此类手表上右滑仍会退出（要拦只能连返回键一起拦，违背「只禁右滑手势」的口径）；② 手机 / 平板的系统返回手势不读该窗口属性，仍可退出播放页（要拦需 API 29+ 的 `setSystemGestureExclusionRects`，仓库未用、无真机可验）。
  - **锁定态反馈变化（轻微）**：触摸锁定期间右滑不再触发「开锁图标浮现」——事件在窗口层就被丢掉了，到不了 `finish()` / `onBackPressed`。锁定的语义不变（仍出不去），只是少了一次视觉反馈。
  - **真机判断方法**：若用户那台表实测「右滑仍退出」，就说明它属于上面第 ① 类（模拟返回键），此时开「左右滑动控制进度」或「禁用返回键」都能拦住，但这属于既有开关，不是本功能的缺陷。
- **验证（本机，26.10.22 已跑）**：`.\gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest --offline` → `BUILD SUCCESSFUL in 56s`（两 flavor 都出 Debug 包，128 tasks）；单测按 `app/build/test-results/testStandardDebugUnitTest/*.xml` 实测 **76 类 / 804 例 / 0 失败 0 错误 0 跳过**，与改动前基线一致——本次只换窗口主题属性，没有可 JVM 单测的纯逻辑，故未新增用例（`noSwipe` 是 `ColorScheme.themeResId` 的既有参数，已被既有主题映射覆盖）。
- **真机待验**：① 手表上「左右滑动控制进度」与「禁用返回键」都关着时，播放页右滑不再退出；② 开启「左右滑动控制进度」后右滑仍是调进度、不退出；③ 双击 / 长按倍速 / 捏合缩放 / 拖动画面全部照常；④ 顶栏返回按钮与返回键仍能退出；⑤ 主题配色与改动前一致；⑥ 短视频页右滑退出行为不变（本次未改，回归确认）。

### 3.55 自动跳过片头片尾「乱跳」：三处判定互相污染（26.10.22，待真机）

- **现象**（用户报告）：「有乱跳的问题 / 问题好像是判断循环没有及时退出导致的」。实际表现是开播后进度自己往前跳、跳过的位置不对、偶尔跳完又弹回原处。
- **澄清**：`maybeAutoSkipOpEd()` 里**没有任何循环**，不存在「判断循环没及时退出」。真正的驱动是既有 250ms 主线程定时器（`progressChange` → `maybeAutoSkipOpEd`），它每拍都会重新判定一次，所以任何「已被处理」的标记只要丢了，就会在下一拍立刻重跳。
- **根因三条（互相独立，都会造成乱跳）**：
  1. **跳转入口分不清调用者**：`seekToVideo()` 是拖进度条、按键、滑动、点看点、自动跳过**共用的唯一入口**，原先它无条件调 `onUserSeekTo(position)` 把落点所在片段记为已处理。自动跳过自己跳到区间右端点本来在区间外，但只要与用户操作撞在同一帧/相邻帧，就会被当成「用户想看这段」而把真正该跳的片段吃掉；反过来用户拖动途中的中间帧也可能误标片段。
  2. **重复拉取清空已处理集合**：`loadViewPoints()` 在一次播放里会被调用多次（启动、投屏恢复等），原先**无条件** `skipHandled.clear()`。只要某次响应在用户已越过片头之后才到达，就把「片头已处理」抹掉 → 下一拍定时器重新命中 → 再跳一次，**每多拉一次就多跳一次**。
  3. **异步 seek 回弹**：`seekToVideo()` 先写 `video_now` 再调 `ijkPlayer.seekTo()`，而 IJK 的 seek 是异步的——落地前 `progressChange` 会把 `video_now` 拉回**旧位置**，旧位置若仍在片段内就触发下一轮判定，表现为「跳完又跳回去」。原先这条不变式只靠 `skipHandled` 一条防线，而它恰好会被第 2 条清掉。
- **改法（不改 `ViewPointSkip` 的纯判定，只修接线）**：
  - 新增 `player/SkipCancelPolicy.kt`（纯函数，无 Android 依赖，可 JVM 单测）两个判据：`cancelKeyFor(userInitiated, segmentKey, handled)` 与 `segmentsChanged(old, new)`。
  - `seekToPosition` / `seekToVideo` 新增 `userInitiated: Boolean = true`，由调用点**如实声明**；`maybeAutoSkipOpEd` 传 `false`，其余用户手势/按键/看点列表/撤回按钮都走默认 `true`。`onUserSeekTo` 改走 `SkipCancelPolicy.cancelKeyFor`：非用户发起一律不标记。
  - `loadViewPoints()` 改为只在 `SkipCancelPolicy.segmentsChanged(...)` 为真（片段表真的变了＝换集/换 P）时才清 `skipHandled`；同一批片段（迟到的重复响应、投屏恢复重拉）保留已处理状态。
  - 新增 `SKIP_QUIET_AFTER_SEEK_MS = 600L` 与 `skipQuietUntilMs`：自动跳过跳完起一段静默期（`markSeekTarget` 经 `pendingSeekIsAutoSkip` 触发），盖住「新位置已写、IJK seek 未落地」的窗口。**只由自动跳过起**——用户主动拖动的落点已被正式记为已处理，不需要时间兜底，也不该因为拖一下就压掉 600ms 的自动跳过。
- **为什么不给自动跳过用更短的静默期**：定时器周期本身就是 250ms，静默期短于它拦不住回弹；而片头后紧跟片尾时，任何长于两段间隔的静默期都会把片尾吃掉。600ms（约 2 拍）是「拦得住一次 seek 往返」与「不越过相邻片段」之间的取值。
- **单测**：新增 `SkipCancelPolicyTest`（11 例：用户主动跳进片段算反悔 / 自动跳过自己不算 / 落点不在片段内不标记 / 已处理不重复标记 / 首次加载算变化 / 重复拉到相同片段不算变化 / 内容变了算变化 / 数量相同区间不同算变化 / 换集算变化 / 两批都空不算变化 / 有内容变空算变化）。
- **验证（本机，26.10.22 已跑）**：`.\gradlew.bat :app:assembleDebug --offline --no-configuration-cache` → `BUILD SUCCESSFUL`（两 flavor 都出包）；`testStandardDebugUnitTest` 与 `testCoexistDebugUnitTest` → 各 **817 例 / 0 失败 0 错误**（改动前基线 806 例，新增 11 例）。改动只涉及播放器接线与纯判定，无新增用户可见文案。
- **真机待验**：① 有片头片尾的视频开播后只跳一次，不再出现「十几秒后又跳」；② 番剧切集/切 P 后新一集仍能正常跳（不应因保留 handled 而漏跳）；③ 用户主动拖进片头后不被弹走；④ 连续两段（片头紧邻片尾）时片尾仍跳得到；⑤ 投屏/后台恢复后不重复跳。

### 3.56 公告单源：上游公告改由服务端逐条审核透传 + 正文页标注来源（26.10.23，生产已部署 / 待真机）

- **需求**（用户）：「从应用端移除上游路的公告」，把上游公告接口接到自建服务器，**由维护者亲自逐条选择是否透传**；透传的公告**只在正文页顶部标注一行来源**（列表卡片不加徽标）；透传公告**参与启动差量弹窗**。来源文案定为「此公告来自原版哔哩终端」。
- **客户端改法（两路合并整体拆除，单源化）**：
  - `api/AppInfoApi.java`：删掉 `checkAnnouncement()` / `getAnnouncementList()` 两个方法、`check()` 里的调用、`Announcement` import；`check()` 不再发公告请求，只留更新包清理 / debug 警告（原先那条 `catch (IOException)` 随之失去可抛出点而删除，`uploadStack` 的 catch 仍需要 import）。
  - `api/TerminalApi.java`：删 `mergeAnnouncements` / `loadMergedAnnouncements`，`parseAnnouncements` 增加 `source`（`optString("source","local")`）；`checkAnnouncement()` 改为单一源差量、`MsgUtil.showText(title, content, isRelayed())`；反馈的 `feedbackHeaders` 顺带修掉一处行合并。
  - `model/Announcement.java` 加 `source` 与 `isRelayed()`；`MsgUtil.showText(title,text,relayed)` 新重载传 `relay_source` extra；`activity/ShowTextActivity.kt` 据 extra 显隐顶部来源行（`res/layout/activity_simple_text.xml` 新 `TextView relaySource`）；`adapter/AnnouncementAdapter.kt` 点击时透传 `isRelayed()`。
  - `activity/settings/AnnouncementsActivity.kt`：只调 `getOwnAnnouncementList()`，空态/失败态走 `showEmptyView` + `MsgUtil.err`，删掉「部分公告源连不上」提示（`strings.xml` 该串换成 `text_announcement_relay_source`）。
  - `SettingsKeys.TERMINAL_ANNOUNCEMENT_LAST` 注释更新：本地公告与透传公告**共用这一个游标**。
- **服务端（`server/`，26.10.23 由 teammate 实现，`SCHEMA_VERSION=4`）**：`announcements` 加 `source`/`origin_id`/`relay_state` + 部分唯一索引、新增 `meta` 表存 `relay_last_pull`；上游拉取用标准库 `urllib.request`（`UPSTREAM_ANN_URL` 可被环境变量覆盖、超时 10s、`run_in_threadpool`）；管理接口 `GET /admin/api/announcement/relay`、`POST …/relay/refresh`（失败回 HTTP 200 + `{code:1,msg}`）、`POST …/relay/decision`（approve / reject，reject 可撤销已透传项，refresh 不复活 rejected）；`_VISIBLE_SQL` 只放 `relay_state='approved'`；控制台「公告」页签新增「上游透传（原版哔哩终端）」区块（待审 / 已透传 / 已拒绝 + 手动拉取 + `last_pull` 超 300s 自动补拉一次）；`selftest.py` 新增 §22（8 小节）。
- **契约**：客户端 `get_list` 每行多 `"source": "local"|"relay"`；id 仍是本地自增 + `1_000_000_000`（两类公告共用 id 空间，relay 另存 `origin_id` 去重）；`local` 行行为与排序/差量语义**完全不变**；pending / rejected 对客户端与 `from` 差量都不可见。
- **部署（HK 生产，26.10.23 21:02 完成）**：生产 `/opt/rebiliterminal-api/` 的三件套原为 **26.10.17 基线**（比本目录工作副本新：含 `/admin?token=` 303 → HttpOnly 会话 Cookie、控制台 UI 打磨、selftest §5 旧口令用例），直接用工作副本覆盖会回退这些改动，因此把 relay 改动**合并到生产基线**（产物 diff：`rebiliterminal_api.py` +336/−9、`console.html` +129/−4、`selftest.py` +250/−0），本机用合并产物跑 `selftest.py` 187 项全通过后才上传。
  - 备份：`./backup.sh` → `backups/data-20261023.db`（7 张表）；旧文件另存 `.bak-20261023-210201-beforerelay`。
  - 上传后 `systemctl restart rebiliterminal-api` → active，`/terminal/health` `code=0`；`PRAGMA user_version` 3→4，`announcements` 已加 `source/origin_id/relay_state` 三列、`meta` 表已建。
  - 上线冒烟：公网 `get_list` 正常（local 行带 `"source":"local"`）；未登录 `GET /admin/api/announcement/relay` 与 `POST …/relay/decision` 均 **403**；`https://console.zsapp.asia/` 200 且含 relay 区块（`CONSOLE_VERSION = "v4 2026-10-23"`）；带 token 真拉上游 → `added=3 total=3`，relay 列表 `pending=3 approved=0 rejected=0`；`from=1000000000` 差量返回 4 行且 `relay=0`（待审不泄漏）；本地公告管理接口 41 行且仅 local。
  - 部署后本目录 `server/` 三件套已同步为合并产物（106452 / 83457 / 45849 bytes）。
- **控制台 v5 改版（26.10.23 21:23，已部署）**：
  - 「上游透传」从公告页搬成**独立页签**：`data-tab="relay"`、待审角标 `c-relay`、hash 记 `#relay`、键盘 `1`-`5` 切页（footer 同步改 1-5）；页签打开时 `last_pull` 超 1 小时只是**兜底补拉一次**（初始化时不在 relay 页才预取，避免重复请求）。
  - 「公告」页前端过滤 `reply_feedback_id>0` 的行（后端 `GET /admin/api/announcements` 仍返回这些行与 `reply_feedback_id`，脚本调用方不受影响），37 条定向回复不再混在正式公告里。
  - 定向回复的删除入口挪到「反馈」对话里每条开发者气泡旁：新增 `POST /admin/api/feedback/reply/delete` ← `{id}`（内部公告 id；只删 `reply_feedback_id>0` 的行，不存在/非回复 404）；`_db_feedback_thread` 的 dev 消息多带内部 `ann_id` 供回传，客户端忽略该字段。
  - `selftest.py` 增 `§3` 页签断言与 `§16b` 删除定向回复用例；当前 **201 项**全通过（离线少 2 项真上游冒烟）。
  - 顺带修掉旧 `/admin` 兜底看板的 500：`crash_rows` 把第 11 列（`substr(stack,…)`）当 `ctime` 传给 `ts()`，库里有带堆栈的崩溃就 `int()` 失败、整页 500；改为 `ts(r[12])`，并在 §20.1 加回归断言「有崩溃堆栈时旧看板仍 200」。
  - 部署：`./backup.sh` → `backups/data-20261023.db`（8 张表）；旧文件另存 `.bak-20261023-212226-v5`；上传后 restart active。冒烟：`/` 200 且 `CONSOLE_VERSION = "v5 2026-10-23"`、含 relay 页签与公告筛选代码；公网 `console.zsapp.asia` 200；旧 `/admin` 从 500 恢复 **200**；`relay pending=3 approved=0 rejected=0`；本地公告管理接口 41 行（37 条定向回复由前端隐藏）；客户端 `get_list` 仍 4 行、全部 `source=local`；未登录 relay/回复删除接口均 403。
  - 本目录 `server/` 三件套同步为 109064 / 85728 / 48144 bytes（`ebf682f8…` / `4e9247a5…` / `45d7ac04…`）。
- **上游公告自动拉取改为服务端每小时一次（26.10.23 晚，用户要求「间隔改为 1 小时一次」）**：
  - 之前只在控制台打开时按需拉（`last_pull` 超 300s），没人开控制台就不拉。现在 FastAPI `startup`
    起 daemon 线程 `relay-auto-pull`，每 `REBILITERMINAL_RELAY_PULL_INTERVAL` 秒（默认 `3600`，
    `0` 关闭）调一次 `_db_relay_refresh()`；启动时按 `relay_last_pull` 倒推等待，**重启不会额外多拉**；
    从没拉过则 5s 后补；失败只 `logging.warning`（logger `rebiliterminal.relay`）+ 退避至多 10 分钟，
    `last_pull` 不写脏。
  - 等待时间抽成纯函数 `_relay_wait_seconds(last_pull, interval, now)`（下限 5s、上限一个周期），
    `selftest.py` 新增 §22.9 五条断言钉边界；自测文件头设 `REBILITERMINAL_RELAY_PULL_INTERVAL=0`
    关线程，避免 TestClient 进 startup 就联网。控制台 `RELAY_AUTO_PULL_AGE` 300→3600，只作兜底。
  - 本机 E2E（临时脚本，已删）：`interval=3` + 假上游 → 线程随 startup 拉起、约 5s 后写入
    `relay_state='pending'` 行、`meta.relay_last_pull` 落库，`calls=1`。
  - **生产上线（26.10.23 21:38，控制台 v6）**：三件套上传 + `systemctl restart` → active。
    白盒验证线程真的在跑：把 `meta.relay_last_pull` 拨到 3700s 前再重启，**8s 内自动拉取成功**
    （`last_pull` 刷新为当前时刻），且拉取**不改写已有审核结论**（2 条 rejected 仍是 rejected，
    未复活）。客户端 `get_list` 仍 4 行全 `local`（1 条待审 + 2 条已拒绝均未泄漏）；
    `console.zsapp.asia` 登录流 303→200、`CONSOLE_VERSION = "v6 2026-10-23"`、`relay-panel` 在。
    备份 `./backup.sh`（ok 20261023，8 表），旧文件 `.bak-20261023-213703-autopull`。
    部署后生产 / 本机三件套 hash 一致：api `c82fac12…`（111718 bytes）、console `e0ce3d44…`（85884）、
    selftest `789e00fa…`（49752）。
- **验证（本机，26.10.23 已跑）**：
  - `.\gradlew.bat :app:assembleDebug --offline` → `BUILD SUCCESSFUL`（standard + coexist 两个 Debug 包）。
  - `:app:testStandardDebugUnitTest` 与 `:app:testCoexistDebugUnitTest` → 两 flavor 各 **815 例 / 0 失败 0 跳过**（`TerminalApiTest` 现 17 例：删掉 3 例「两路合并」并改为断言 `source`）。  这是上游在本 PR 之外的基线上测的；**合入本 PR 后全量基线为 79 类 / 841 例**（见 §1）。  - `server/`：`python -m py_compile` OK；`python server/selftest.py` → **206 项全通过**（含真上游冒烟；离线少 2 项）；本地 uvicorn 真服务 E2E 验证 approve 后客户端立即可见 `source=relay`、上游不可达时 `code=1`、登录后控制台含 relay 区块；定时拉取线程用临时脚本 E2E 通过（见上）；`console.html` 的 script 块 `node --check` 通过。
  - markdownlint：本次改动的 5 个 md 0 错误。
- **未验证 / 待办**：① 生产上 3 条待审上游公告**未做 approve/reject 实操**（避免真实客户端看到未定内容；按用户要求「亲自选择是否透传」留给维护者在控制台点）；② 控制台页签切换、公告筛选、回复删除按钮**没在真实浏览器点过**（目前只有 curl 冒烟 + TestClient 断言）；③ 客户端未真机验证（列表只剩自建源、透传标注、待审不可见、差量弹窗只弹一次、断网不卡启动）；④ 控制台兜底补拉仍无退避（每小时最多一次，后端定时线程已有退避）。
- **注意**：`server/` 整个目录在 `.gitignore` 里（既有约定，服务端代码不进版本库），所以本次服务端改动只存在于工作区与 HK 部署目录，不随客户端提交。

### 3.57 弹幕时间轴改由播放器位置驱动（彻底去掉周期性纠偏，26.10.22）

- **用户反馈**：「弹幕每隔一会自动纠偏本就是一个不正常的行为，按理说应该全程跟随视频进度；其次在快进时这个纠偏更为严重。」并要求核实「弹幕的滚动速度计算是否有问题，导致弹幕与视频差距越来越大」。
- **结论：确实是速率问题，不是阈值问题。** §3.51 的 `decide()` 只是把「清屏」限了频，没有消除漂移本身——根因在 DFM 内部：`DrawHandler.syncTimer()` 把 `SystemClock.uptimeMillis() - mTimeBase` 当成媒体时间，只有「视频恰好按 1.0x 实时播放」时才成立。
- **实测取证（MuMu Android 15 x86_64，1.0x，临时诊断日志 1 秒一次）**：

  ```text
  视频=3743  弹幕=4249  差=-506      ← 弹幕跑快
  视频=7754  弹幕=8722  差=-968
  视频=11773 弹幕=13217 差=-1444
  视频=15789 弹幕=15787 差=2         ← 触发一次吸附（= 清屏重建）
  ```

  视频每 4 秒只前进 3981ms（≈0.9x 墙钟，解码跟不上），弹幕每 4 秒前进 4478ms（≈1.0x 墙钟）——**速率差约 12.5%，差距线性增长**，所以「每隔十来秒必清一次屏」。倍速下同理：2.0x 时墙钟每 3 秒只该走 6 秒的一半，偏差扩大得更快，与用户「快进时更严重」的观察一致。
- **为什么不能继续用 `seekTo` 修**：`seekTo` → `DrawTask.seek()` → `reset()` 是「清空屏上弹幕 + 重建缓存」（§3.51 已确证），拿它做纠偏等于周期性清屏。要让「全程跟随」成立，只能换时间源。
- **修法（4 层，`DanmakuFlameMaster` 模块可改）**：
  - 新增纯逻辑类 `DanmakuFlameMaster/.../controller/MediaClockSync.java`：`project(采样位置, 采样时刻, 当前时刻, 倍速)` 在两次采样之间外推（否则弹幕每 250ms 才动一次）；`nextTimerValue(当前, 理想)` 每帧**最多挪 40ms**（把采样抖动摊平，不出现画面回退）；`isDiscontinuity(上次, 本次)` 位置差 >1s 判为跳转/换源，直接硬对齐不走蠕动。
  - `DrawHandler` 新增 `interface MediaClock { long positionMs(); boolean isPlaying(); }` 与 `setMediaClock()`；`syncTimer()` 优先走 `syncTimerByMediaClock()`，位置为负数（未就绪/已销毁）时**回退原生墙钟分支**。
  - `IDanmakuView` / `DanmakuView` 暴露 `setMediaClock()`（转发不判 `isPrepared`，因为绑定通常早于 prepare）。
  - `player/DanmakuManager.kt` 新增 `bindMediaClock(position, isPlaying)` 并在 `configureAndPrepare()` 末尾重放一次；`PlayerActivity.bindDanmakuView()` 绑 `video_now`（就是 `progressChange` 每 250ms 采样的那个 `@Volatile` 字段）与 `isPrepared && isPlaying && !destroyed`；`ShortVideoPlayerActivity` 同样绑定（`videoNow` 已补 `@Volatile`）。**直播不绑**（没有有意义的播放位置）。
  - `PlayerActivity.progressChange` 里整块周期性吸附删除，`DanmakuSync.kt` 及其 9 例单测**删除**；新增 `MediaClockSyncTest`（12 例：外推、倍速、空档封顶、不倒推、限幅蠕动、半秒内收敛、不连续判定、阈值边界）。
- **为什么不用改单条弹幕的滚动时长**：`DanmakuFactory` 的 `scrollSpeedFactor` 决定「一条弹幕在屏上停留多久（以媒体时间为单位）」。倍速下时钟推进 2x，同一条弹幕自然也是 2 倍速划过屏幕——这正是期望行为，弹幕出现时机与画面严格对齐，不需要额外放大滚动速度。
- **线程安全边界（改动中最容易踩的坑）**：`MediaClock` 的两个方法跑在 DFM **渲染线程**上，只能读主线程采样好的内存值；**绝不能**在里调 `IjkMediaPlayer.currentPosition`（取 native 锁，与主线程 seek/release 并发有卡死风险，也是 26.09.10 那版「渲染线程读位置导致弹幕全没」的老坑）。
- **模拟器实测（26.10.22，coexist debug 包；日志为当时的体检输出，现已收敛为「仅异常时、≥5 秒一条」）**：
  - **1.0x 稳态 40 秒**：偏差 `9 / 11 / 25 / 26 / 5 / 15 / 18 / 10 / 13 / 16 / 9` ms，**横盘不增长**（改前同样条件每 4 秒涨 490ms）。
  - **2.0x 播放 20 秒**（视频每 3 秒推进 ≈6020ms）：偏差 `42 / 39 / 34 / 40 / -102 / 26` ms，仍不累积；`-102` 是单帧采样抖动，下一帧即回落。
  - **拖进度条**：从 81s 拖到 128s 后首个采样偏差 **8ms**（`isDiscontinuity` 硬对齐生效），截图确认弹幕立即可见、无"闪一下又没了"。
  - **长时间无周期性清屏**：整段日志再无任何「纠偏/清屏」记录；`弹幕缺口` 无输出；错误日志只有 MuMu 自己的旋转提示，应用侧零异常。
- **验证（本机）**：`:app:assembleCoexistDebug` 通过；`:app:testStandardDebugUnitTest` = **76 类 / 809 例 / 0 失败**（806 − 9 + 12）。
- **手表端**：**维护者已人工实测通过**（26.10.23，维护者自述，非本会话 agent 复现）：1.0x / 2x 播放与拖进度条下弹幕全程跟随、不再周期性清屏。仍**没有**设备侧量化数据的是「极弱机（真实手表 SoC）上媒体时钟的收敛速度」与「暂停→恢复的瞬时手感」——要等有人拿 `adb logcat -s 弹幕时钟` 在手表上跑一遍才有日志证据。

### 3.58 短视频页没有阻止自动熄屏（26.10.22）

- **问题**（用户提出「检查短视频部分是否没有做阻止自动熄屏」）：**确实没有**。全仓与亮屏相关的代码只有两处——`res/layout/activity_player.xml` 根布局的 `android:keepScreenOn="true"`（长视频播放页）与 `DownloadListActivity` 的下载期 `FLAG_KEEP_SCREEN_ON`；短视频这套（`activity_short_video_player.xml` + `fragment_short_video_page.xml`）**一处都没有**，看短视频到系统熄屏时间就黑屏。
- **改法**（`activity/video/ShortVideoPlayerActivity.kt` + `ShortVideoFeedFragment.kt`）：
  - `PageHolder.isPlaying` 加自定义 setter，任何一处改播放状态都触发宿主 `syncKeepScreenOn()`——赋值点有 8 处（`play` / `pause` / `resume` / `setActive` / `onPrepared` / 完成回调 / `releasePlayer` / state 收集），逐个补调用必漏。
  - 判据取「当前活跃那一页是否真的在播」（`ShortVideoFeedFragment.isCurrentPlaying()` → `ShortVideoPagerAdapter.isCurrentPlaying()` → `activeHolder.isPlaying()`），**每次整体重算**而不是增量 add/clear：增量写法在 `releasePlayer()` 这类「本来就没在播」的路径上会漏，残留一次就是永久亮屏。
  - `onStop` 兜底清标志、`onStart` 回前台重算，避免后台期间把状态带回来。
- **为什么用窗口标志而不是 View 级 `keepScreenOn`**：先试过在 `PageHolder` 里设 `itemView.keepScreenOn`，模拟器实测**暂停后窗口 `fl` 里仍留着 `KEEP_SCREEN_ON`**（RecyclerView 的 item 挂着，View 级标志的清退不可靠）。换成 `window.addFlags/clearFlags(FLAG_KEEP_SCREEN_ON)` 后一次就对。
- **实测（MuMu Android 15 x86_64，coexist debug，判据是 `dumpsys window windows | grep -c 'fl=KEEP_SCREEN_ON'`）**：播放中 `1` → 暂停后 `0` → 恢复播放 `1` → 上滑切下一条 `1`（新一条自动播）。用户随后在自己环境复验通过。
- **验证（本机）**：`:app:assembleCoexistDebug` 通过；`:app:testStandardDebugUnitTest` = 76 类 / 809 例 / 0 失败（本次只有 UI 状态逻辑，无新增可单测的纯函数）。
- **顺带记一笔**：`mWakefulness` **不能**当判据——MuMu 本身不自动熄屏（桌面对照实验 22 秒后仍 `Awake`），必须看窗口 `fl`。

### 3.59 新增「伪装与安全」：双击顶栏切到假计算器 / 假蓝牙提示（issue #22，26.10.22）

- **需求与验收口径见 `docs/superpowers/specs/2026-10-22-disguise-mode.md`**（Gitee 侧 issue #22，GitHub 镜像上没有该编号；技术坑见 `docs/architecture-map.md` §7.53、`docs/agents/pitfalls.md`「伪装与顶栏手势」）。
- **需求**（issue #22，用户定的三条口径）：① 两种样式都做、可在设置里选；② 计算器的退出方式是「输入暗号再按 `=`」；③ 做成设置开关、**默认关**；触发方式为**双击顶栏**。
- **新增**：
  - `util/DisguisePrefs.kt`：开关 / 样式（`calculator` / `bluetooth`）/ 暗号（4~8 位数字）与纯函数 `normalizeCode`、`matchesSecret`。暗号**只允许数字**，否则计算器上敲不出来 = 用户把自己锁在伪装页里，只能强杀应用。
  - `util/CalculatorEngine.kt`：真能算的四则运算求值（先乘除后加减、小数、除零报错、末尾多余运算符忽略、**绝不抛异常**）。伪装页算错就等于暴露。
  - `activity/disguise/CalculatorDisguiseActivity`：深灰底 + 橙色运算符的手表计算器（**刻意不继承 BaseActivity**——主题、DPI 适配、教程、EventBus 任何一项都会让伪装露馅）。暗号命中 → `finish()`；**返回键只当 `C`**，不退页。
  - `activity/disguise/BluetoothDisguiseActivity`：半透明窗口（`Theme.BiliClient.Disguise.Dialog`）上盖一张仿系统提示卡，底下的页面会透出来再压一层暗色；「重新连接」只换一行字；**长按标题 2 秒**退出，返回键被吃掉。
  - `activity/disguise/DisguiseLauncher`：按设置选样式的唯一入口。
  - `BaseActivity.setupTopbarDoubleTapDisguise`：顶栏手势层，技术结论见 `docs/architecture-map.md` §7.53。
  - 设置：新分组 `GROUP_DISGUISE`（「伪装与安全」）+ `SettingMainActivity` 一级入口 + `SettingsIndex` 搜索条目；`strings.xml` 全部文案；清单两个 Activity 均 `exported="false"` 并各带伪装主题。
- **单测**：`CalculatorEngineTest` 14 例（空表达式、单数字、加减、乘除优先、小数显示、整除不带 `.0`、末尾运算符、连续运算符、除零、非法输入、`*` `/` 等价、负数续算、`5×-2`）、`DisguisePrefsTest` 7 例（合法/裁空格/长度/非数字/匹配/非法暗号恒不匹配/样式合法性），伪装相关共 **21 例**。合并上游后的全量基线是 **79 类 / 841 例 / 0 失败**。
- **模拟器实测（MuMu Android 15 x86_64，coexist debug，用户本人操作）**：双击顶栏 → `WindowManagerShell` 日志确认 `CalculatorDisguiseActivity` 在 display 22 打开；输入暗号按 `=` → `disguise>暗号命中，退出伪装` 并回到原页面；全程 **0 条 `FATAL EXCEPTION` / `AndroidRuntime` 异常**；`BaseActivity>setupTopbarDoubleTapDisguise: 顶栏双击伪装已就绪` 在每个页面正常打印。
- **手表端**：**维护者已人工实测通过**（26.10.23，维护者自述）：双击顶栏可切到伪装页、计算器能算、暗号能退出、蓝牙样式轻点不再误退出。仍缺设备侧验证的是「单击顶栏那个 0.3 秒双击判定窗口在手表上的手感」与「半透明蓝牙提示页在圆屏上的布局」——这两条只能在圆屏真机上肉眼判断。

### 3.60 上游审查意见修复（26.10.23）

上游对 PR 提了 1 个 P0 + 7 条 P1/P2。逐条核对后的处理：

- **P0（真回归，§3.57 引入）短视频首播弹幕不显示**：`ShortVideoPlayerActivity` 在 `loadDanmaku()` **之前**调 `danmakuManager?.resume()`，而 `DanmakuManager.resume()` 有 `isPrepared` 守卫，此刻必然 false → 空操作；全页又没有任何 `start()`。DFM 的 UPDATE 循环**只由 START / SEEK_POS / RESUME 三个分支拉起，`prepare()` 本身不会启动**，旧实现是靠周期性 `seekTo` 的副作用（SEEK_POS 落穿到 RESUME）间接启动的。§3.57 删掉周期性 seekTo 时把这个副作用一起删了。
  - 修法：`DanmakuManager` 新增 `start(positionMs = -1)`，并在 `configureAndPrepare()` 末尾自动调用（起播位置优先取媒体时钟的已知位置，续播/切页别从 0 重走）。等价于长视频页原本在 `onPrepared` 里做的 `mDanmakuView.start()`。
  - 实测（模拟器）：进入短视频页**不做任何操作**，弹幕区出现「弹幕准备完毕」+ 多条滚动弹幕（修复前只有烧进画面的字幕）。
- **P1-1 双击顶栏覆盖面与文案不一致**：`PlayerActivity` 不继承 `BaseActivity`（没有手势）；20 个 `asyncInflate` 页面在 `onStart` 时视图还没进来，`findViewById(R.id.top)` 为 null 直接 return（首次进入无效、退出再进才生效）；短视频的顶栏在分页 item 里也装不上。
  - 修法：手势实现抽成 `util/DisguiseGesture.kt`（`install` / `detach` / `isInstalled`，用 `WeakHashMap` 防重复安装）；`BaseActivity` 拿不到顶栏时 `decorView.post` 重试一次；`PlayerActivity.onStart` 与短视频 `PageHolder` 各接一次线。现在「双击任意页面顶栏」与文案一致。
- **P1-2 3× 长按媒体时钟追不上**：`MAX_STEP_MS = 40` 在 60fps 下只能追 2400ms/s，3× 需要 3000ms/s；而 `DISCONTINUITY_MS = 1000` 又大于 3× 的相邻采样差 750ms，永不触发硬对齐。
  - 修法：新增 `stepFor(speed)`（步进上限随倍速放大）与 `SNAP_ERROR_MS = 500`（误差过半秒直接吸附）。`nextTimerValue` 增加 `speed` 参数，补单测 2 例。
- **P1-3 计算器负数无法续算**：`3-6` 之后表达式里留下 `-3`，再按运算符或 `=` 会因为「首字符是运算符」直接 `ERROR`，伪装页显示「错误」很扎眼。
  - 修法：`CalculatorEngine` 支持表达式开头的一元负号；补负数用例 6 条（`-3+2` / `-3×2` / `0-3-3` / `-3` / `-3÷2`。
- **P1-6 文档一致性**：`architecture-map` 的测试清单（§3/§8 两处 76 类 806 例 → 79 类 841 例，补 `MediaClockSyncTest` / `CalculatorEngineTest` / `DisguisePrefsTest` / `SkipCancelPolicyTest`，删已不存在的 `DanmakuSyncTest`）、弹幕时钟旧口径（把「DanmakuSync + 周期性 seekTo」整段换成 §7.51 的现行做法）、`docs/README.md`「19 章」→「20 章」、`FEATURES.md` 重编号后 6 处旧引用、`readme.md` 的分支制度与已不存在的 `testDebugUnitTest`。
- **P2 顺手修**：短视频 `onError` 未把 `isPlaying` 置 false（画面停住却一直挂着 `KEEP_SCREEN_ON`）；`isPlaying` 补 `@Volatile`（弹幕渲染线程读）；`format` 指定 `Locale.US`（默认 Locale 会把小数点写成逗号，结果再回填就解析不回来）；`DrawHandler` 的 SEEK_POS / RESUME 分支重置媒体时钟采样（避免暂停恢复后计时器前冲再回落）；长视频续播 seek 补 `markSeekTarget`；设置里的暗号不再**逐键**弹 toast（`TextWatcher` 每次击键都会触发）。
- **未做（需单独决策）**：把 PR 拆成三份、`readme.md` 功能表补伪装条目之外的市场化描述、`excludeFromRecents`/图标（需真机确认最近任务卡片表现）、`setScreenOnWhilePlaying(true)` 对 TextureView 无效但仍保留（SurfaceView 路径可能有意义，改动收益不明确）。
- **验证**：`:app:assembleCoexistDebug` 通过；`:app:testStandardDebugUnitTest` **79 类 / 841 例 / 0 失败**；模拟器实测 P0 与双击手势；markdownlint 31 文件 0 issue。
- **第二轮审查的三条必修（26.10.23 晚，已修 + 已实测）**：
  1. **双击伪装会无限自旋**：开关开启且本页没有 `R.id.top` 时，`BaseActivity` 用 `decorView.post` 无限重试——有 **11 个页面布局里本来就没有顶栏**（Download / Catch / Tutorial / TutorialPager / JumpToDialog / InputDialog / CaptchaWebView / QRLoginRegister / ConfirmDialog / Dialog / ListDialog），进这些页面会每帧投递一次、一直耗电到页面结束。改成「`postDelayed` + 封顶 20 次 × 50ms + `isFinishing/isDestroyed` 立刻停」，并在放弃时打一条 `XXX 没有 R.id.top，本页不安装双击伪装`。实测：进 `DownloadActivity` 只出现**一次**该日志。
  2. **蓝牙伪装页轻点标题就自动退出**（伪装的致命伤）：`OnTouchListener` 对 `ACTION_DOWN` 返回 `false` → View 没成为触摸目标 → 系统不再派发 `UP`/`CANCEL` → `removeCallbacks` 永远执行不到 → 轻点一下、2 秒后窗口自己 `finish()`。改成**全部返回 true**（消费事件；标题本就没有点击行为）。实测（真实坐标 `[144,1623][2016,1763]`，2160×3840 屏）：轻点 1 次 + 等 4 秒**仍在伪装页**，连点 5 次 + 等 4 秒**仍在**；长按 2.5 秒 → 退出到推荐页，日志 `disguise>长按标题，退出伪装`。
  3. **文案与覆盖不一致 / 关开关不摘手势**：`desc_setting_disguise_enabled` 与 FEATURES 从「任意页面」改成「**有顶栏的页面**」（并说明对话框/崩溃页/教程页没有该手势）；`PlayerActivity.onStart` 与短视频 `PageHolder.syncDisguiseGesture()`（`onBindViewHolder` 每次重算）都补上「关掉开关就 `detach`」，不再需要重进页面才失效。
- **顺带修掉的真 bug（同批）**：
  - **短视频页从来没给弹幕设过倍速**：全页只有 `playerBridge.setSpeed(...)`，`danmakuManager.setSpeed` 一处都没有 → 0.5~3.0x 下 `DrawHandler.mSpeed` 一直是 1.0，媒体时钟的倍速外推与步进补偿（§7.51）在短视频上完全没生效。新增 `applySpeedToPlayers(speed)` 一并设，三个调用点（`cycleSpeed` / `startSpeedBoost` / `endSpeedBoost`）与 `onPrepared` 全部改走它。
  - **`setSpeed` 早于 DFM prepared 会被丢弃**：`DanmakuView.setSpeed` 有 `handler.isPrepared()` 守卫，而播放器 `onPrepared` 通常早于 DFM 的 `prepared()`（后者异步）→ 默认倍速非 1× 时那次调用等于没设。`DanmakuManager` 新增 `setSpeed()` 记 `pendingSpeed`，在 `prepared()` 回调里补设一次；`PlayerActivity` 的 5 处 `mDanmakuView?.setSpeed(...)` 全改走它。
  - **`DrawHandler` 的 PREPARE ready 回调吞掉起播位置**：`ready()` 是后台解析完才回调的（异步），无条件 `pausedPosition = 0` 会把此前 `start(pos)` 设好的位置清零 → 续播/切页时弹幕时间轴从 0 重走一遍。新增 `mPendingStartPos`，`START` 时记录、`ready()` 时优先取它。
  - P2 清扫：删掉计算器页空的 `onTouchEvent`、`DanmakuView.setMediaClock` 注释改成符合实现（只判 `handler != null`，不判 `isPrepared`）、圆屏 padding 抽成 `applyDisguiseRoundPadding`（两伪装页共用，蓝牙页也补上了）、设置分组文案与搜索索引改走 `strings.xml`（新增 `desc_setting_disguise_index`）。
- **第三轮审查（Standards 轴 + 伊莲）后的补充修复（26.10.23 晚）**：
  - **暂停中加载完弹幕会被 autostart 拉起**：`configureAndPrepare()` 末尾原来无条件 `start()`。改成「`prepared()` 回调里按 `mediaPlaying()` 判断」——不在播就不起渲染，之后由宿主各自的 `resume()` 路径拉起（DFM 的 RESUME 分支在 `mReady` 后会自行启动 UPDATE）。直播未绑媒体时钟（`mediaPlaying == null`）照常启动。
  - **重复 start 会把时间轴锚回 0**：`PlayerActivity.onPrepared` 原来裸调 `mDanmakuView!!.start()`，续播/已经跑了一段时会让弹幕跳回开头闪一下。改成 `danmakuManager?.start(video_now)`。
  - **短视频首播那处 `resume()` 是空操作**（`loadDanmaku()` 之前调用，`isPrepared` 必然 false）：删掉；真正的起播由 `prepared()` 负责。
  - **计算器支持 `5×-2`**：`CalculatorEngine` 重写词法，把「期望数字位置出现的 `+`/`-`」当正负号（`5×-2 = -10`、`5--3 = 8`），并抽 `scanNumber()` 消掉原来两段重复的数字扫描；`CalculatorDisguiseActivity` 的运算符处理允许在 `×`/`÷` 后追加 `-`（否则 UI 上构造不出负数运算）。新增/调整 3 例。
  - **未知按键 tag 不再静默拼进表达式**：只接受单个数字字符。
  - **暗号输入改数字键盘**：`SettingsAdapter` 新增 `input_digits` 类型（`TYPE_CLASS_NUMBER`），设置项从 `input_string` 换过去。
  - **Java Javadoc 的 `[X]` 链接**改成 `{@link ...}`（DFM 三个文件）。
  - `CalculatorEngine.ERROR` 保留中文常量，KDoc 写明「纯函数跑在 JVM 单测里没有 Context，故意不读资源」（AGENTS.md 已有该例外条款）。
- **文档计数修正**：`architecture-map` 的测试清单从「80 个文件」改为「**79 个文件**（78 个 `*Test` + 1 个 `FakeSharedPreferences.kt`）、**79 个测试类**」（`CalculatorEngineTest.kt` 内有 `CalculatorEngineTest` 与 `DisguisePrefsTest` 两个类），用例数统一为 **841**。`strings.xml` / `themes.xml` 末尾多余空行已清。
- **手表端验证口径（26.10.23）**：本 PR 涉及的弹幕时间轴、短视频亮屏、伪装功能**已由维护者人工在手表上实测通过**（维护者自述，非本会话 agent 复现，因此没有设备日志/截图入库）。仍未覆盖的是「极弱机上的媒体时钟收敛」「单击顶栏 0.3 秒延迟的手感」「圆屏蓝牙提示页布局」——这三项只能真机肉眼判断，已分别记在 §3.57 / §3.59 的对应条目里。

### 3.61 收藏夹接口层整体迁移到 `x/v3/fav/*` 新族（26.10.23）

- **缘起**：上游 PiliPlus 的收藏夹接口已全部换成 v3 新族（写操作 `x/v3/fav/resource/batch-deal`、收藏夹列表 `x/v3/fav/folder/created/list`、图文收藏 `x/polymer/web-dynamic/v1/opus/feed/fav`），本机还在用 `getBoxList` / `x/space/fav/arc` / `medialist/gateway/*` 一代。本轮只迁**接口层**，UI 与交互零改动；上游有而本机完全没有的 4 类能力（夹内手动排序、收藏夹本身排序、清理失效收藏、夹详情/封面/公开私密编辑）只登记到 `docs/FEATURES.md` §8.5「待实现」。
- **`api/FavoriteApi.java` 重写**：`getFavoriteFolders` 从「`getBoxList` + `list-all` 两次请求手拼 media_id」改成 `x/v3/fav/folder/created/list`（`ps=100` + `has_more` 分页，一次拿 `id`/`title`/`cover`/`attr`）；删除 `getFolderVideos()`（`x/space/fav/arc`）、`legacyOrder()`、`buildMediaId()`、`resolveMaxCount()`、`LEGACY_CUSTOM_FOLDER_MAX_COUNT`；新增纯函数 `isDefaultFolder(attr)`（`attr & 2 == 0`，bit0 私密 / bit1=1 非默认）与 `maxCountOf(isDefault)`；写操作统一收口到私有 `dealFavorites()`（`POST x/v3/fav/resource/batch-deal`，`resources` + `add_media_ids` + `del_media_ids` + `csrf`），`addFavorite`/`deleteFavorite`/`batchDeleteResources` 改签名收 **media_id**；`deleteFolder` 补 `platform=web`；`getFavouriteOpus` 换 `opus/feed/fav`（`page_size=20`，封面兼容对象/字符串，`code != 0` 回退旧 `favlist`）；`getFavoriteState`/`parseFavoriteState` 增加 `mediaIdList` 出参。
- **调用方**：`adapter/favorite/FolderChooseAdapter.kt` 的 `fidList` 换成 `mediaIdList`（点选写操作只认 media_id）；`activity/video/info/AddFavoriteActivity.kt` 收集并下传 `mediaIdList`；`activity/user/favorite/FavoriteVideoListActivity.kt` 删掉 `fid` extra 与老接口兜底分支（`mediaId <= 0` 直接按加载失败）、删掉「连点两次长按删除」（`confirmLegacyRemove()` 整段移除，长按统一走管理菜单/只读菜单）、两处 `deleteFavorite` 改传 `mediaId`；`adapter/favorite/FavoriteFolderAdapter.kt` 不再传 `fid`。`api/LikeCoinFavApi.favorite()`（手拼 media_id 的死路径）一并删除。
- **单测**：`FavoriteApiTest.kt` 删 `resolveMaxCount`/`buildMediaId`/`legacyOrder` 三组用例，改成 `isDefaultFolder`/`maxCountOf` + `parseFavoriteState` 的 `mediaIdList`/默认夹按 attr 判定回归用例（测试数据用线上真实值：默认夹 `attr=0`、自建夹 `attr=22`）。
- **验证**：`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest` 全绿；未上手表实测（接口层改动，UI 未动，按 `docs/agents/workflow.md` 的判据走构建 + 单测）。
- **注意**：接口域名/路径变了但都还在 `bilibili.com` 白名单内，不需要动 `<domain-config>`；`getBoxList` 用的 `space.bilibili.com` 从此不再被收藏链路请求。

### 3.62 搜索结果加排序：单按钮 + 单选弹窗（综合 / 播放量 / 最新 / 收藏，26.10.24，用户反馈）

- **需求来源**：用户反馈原文「希望可以在搜索里加一个最多播放 最多点赞 最新播放之类的分类」。两轮口径：① 先做成**视频 tab 顶部一条常驻排序行**，随后按用户要求改成**单按钮 + 点开弹窗选择**（弹窗用番剧选集同款 `TerminalDialog.singleChoice`，按钮走主题里的经典终端按钮样式）；② 四档 **综合 / 播放量 / 最新 / 收藏**；③ 覆盖**视频 + 专栏**两个 tab。
- **接口事实**（已并入 `docs/bilibili-api-notes.md` §3.13）：`all/v2` 只吃 `keyword`，`order` 是 `search/type` 的参数；视频搜索**没有「最多点赞」**（只有 `click`/`pubdate`/`dm`/`stow`/`scores`）。所以**视频分类选「综合」时仍走 `all/v2`**（保留它把番剧混进第一页的既有行为），选其余档位改走 `search/type?search_type=video&order=…`（不混番剧）。
- **控件为什么在本页而不是 fragment**：`activity_search.xml` 里 `ViewPager` 只对齐到 `@id/top` 下方，`searchbar` 与它同起点、后声明（画在上层）。第一版把控件加进 `fragment_simple_refresh` 顶部，模拟器实测**四个档位正好压在搜索框里**。现在控件是 `activity_search.xml` 的 `searchSortButton`（锚 `searchbar` 下方，`ViewPager` 锚它），`SearchFragment` 只暴露 `sortOptions()` / `currentSortOrder()` / `selectSort()`，`SearchActivity` 负责文案、弹窗与显隐。
- **按钮为什么是裸 `<Button>`**：MaterialComponents 主题会把 `<Button>` inflate 成 MaterialButton 并套用主题里声明的 `materialButtonStyle` —— 默认主题（经典终端）对应 `ButtonStyleTerminal`（`#cc262626` 半透明卡底 + 暖白字 + 圆角），换主题时自动跟随各自的按钮样式。**别写死 `@color/terminal_*`**，浅色主题下会变成暗块。
- **弹窗**：`TerminalDialog.singleChoice(this, title, labels, checked)`，与 `BangumiInfoFragment.getEposideChooseDialog()` 同一款（`›` 引导符 + 选中项 `▸`/`✓` + 底部「点空白处取消」）。**TerminalDialog 只负责拼装、不会自己 show**，调用方必须 `.show()`——第一次就是漏了这个：点按钮只有水波纹，弹窗不出现（模拟器实测抓到）。
- **几个刻意的取舍**：
  - **没搜过不显示**（`hasSearched`）——那时页面上是搜索历史，摆一个「排序」没有意义；搜索框被滚走时按钮**一起收**（两层 chrome 只收一半会在列表顶上留个洞）。
  - **排序不持久化**：每次进搜索页都是「综合」。少一个存储键，也少一类「我什么时候设过排序」的困惑。
  - **`sortOrder` 是 `@Volatile`**：点击在主线程写、`continueLoading` 在 `CenterThreadPool` 线程读（与 `bottom` 同一类可见性问题）。
  - **切换排序时在途结果作废**：请求发出前抓一份 `orderAtRequest`，回包后发现排序已变就整批丢弃，否则会把旧排序的第 2 页追加到新列表里。
- **风控降级不再表现为空列表 + 自动回退旧接口**（26.10.24 用户报「有时候拼接的链接会没有任何搜索结果」，实测抓到原始响应）：`search/type` 在风控降级时会回 `{"code":0,"message":"OK","data":{"v_voucher":"voucher_…"}}` —— `code` 是 0，但 `data` 里**既没有 `result` 也没有 `seid`**，只有 `v_voucher`。以前 `SearchApi` 对它返回 null，页面就只剩空列表 +「啥都木有~」，看上去像排序参数拼错了。
  - **回退链路**（按用户要求）：wbi 那条 → 若拿到 `v_voucher` 空壳，改打**旧接口** `x/web-interface/search/type`（不带 `w_rid`/`wts`/`seid`）→ 旧接口也是空壳才抛 `SearchApi.RiskControlException`。旧接口实测同样吃 `order`（`order=click` 下 `play` 严格降序 7972105 → 5017479 → 4438614 → …），中文长关键词也一样能出。
  - **按钮不可用态**：抛到页面后 `SearchFragment.markSortBlocked(true)`，排序按钮改成「因B站风控不可用」、点它只提示「请稍候再试」；下一次加载成功会自动解除。状态变化经 `onSortStateChanged` 回主线程重画按钮。
  - **判据只认 `v_voucher`**：真没结果的搜索仍会返回带 `result` 的响应，不能因为 `result` 为空就报风控。同理**不要急着怀疑参数**——同一条 URL 形状在冷却几十秒后立刻恢复 200 / `numResults:1000`。接口结论已并进 `docs/bilibili-api-notes.md` §3.14。

- **顺带修的**：`SearchApi` 里三处封面/头像用 `"http:" +` 拼协议（违反「只走 https」），统一改成 `httpsUrl()`（`//` 开头才补 `https:`，已是绝对地址的原样保留）；视频条目解析抽成 `parseVideoEntry`，聚合搜索与分类搜索共用一份。
- **单测**：`SearchApiTest` 从 6 例补到 **12 例**（分类搜索视频解析、非 video 条目跳过、null 输入、聚合分支与分类分支共用同一解析与封面规则，以及两条风控判据）。
- **模拟器实测（MuMu Android 15 x86_64，coexist debug）**：MuMu 会把窗口在 display 2~7 之间搬来搬去，每次搬运都重建 Activity，所以每一步都要现查 `dumpsys display` 拿 physicalId（截图）与逻辑 displayId（`input -d`）。
  - 搜 `linux` → 搜索框下方出现「排序：综合」按钮；点开是单选弹窗（标题「搜索结果排序」，四项带 `›`，当前项 `▸`+`✓`，底部「点空白处取消」）。
  - 选「播放量」→ `order=click&page=1&search_type=video`，结果按播放量降序（797.2万 → 501.7万 → 443.9万 → 412.9万），按钮变成「排序：播放量」。
  - 选「最新」→ `order=pubdate`，首屏全是个位数播放量的新视频（0/11/56 观看），按钮变成「排序：最新」。
  - 选「收藏」→ `order=stow`，按钮变成「排序：收藏」，列表正常返回。
  - 切到**专栏**：按钮文案里的档位变成「阅读量」；切到**用户**、**番剧**：按钮消失。
  - 列表下滑 → 搜索框与排序按钮一起收起、列表顶到顶栏下；回滑恢复。
  - 全程 `logcat` 里 `FATAL EXCEPTION` / `AndroidRuntime` **0 条**。
- **子代理审查（26.10.24）修掉的 4 条**：
  - **过期请求的失败会把按钮锁死**：成功分支有 `orderAtRequest != sortOrder` 就丢弃，`catch` 里却没有同样的判断——风控链路比正常链路慢，最坏情况是新排序已经刷出来了、旧请求才抛 `RiskControlException`，于是按钮被冤枉成「不可用」。两处 `catch` 开头补同一个判据。
  - **`-412` / `-352` 形态漏了**：以前只在 `data` 有 `v_voucher` 时才回退，而 `code=-412` 的响应往往连 `data` 都没有，会走 `if (all.isNull("data")) return null;` 静默返回空列表。新增 `shouldFallback(all)` 同时认两种形态（含 `NetWorkUtil` 重试 5 次后原样返回的 `-412`），旧接口也一样认。
  - **风控文案被吞成「网络错误」**：`MsgUtil.err` 对任何 `IOException` 一律换成 `err_network`，而 `RiskControlException` 正是它的子类 → 「搜索被B站风控了…」只会进 logcat。改为在 `SearchFragment.report()` 里先拦一道（5 个搜索分类共用）。
  - **风控态没有重试入口**：按钮只弹提示、下拉刷新关着、同关键词再点搜索会短路，锁定后再也解不开。现在点按钮会真的重拉第一页（`SearchFragment.retry()`），且 `update(keyword)` 换了关键词时会复位。
- **第二轮审查（26.10.24，外部评审）修掉的 3 条 + 补的测试**：
  - **在途的旧请求仍可能污染新结果**：只在后台线程比了 `sortOrder`，从"检查通过"到 `runOnUiThread` 真正执行之间状态变了仍会把旧结果追加进新列表；换关键词更是完全没校验。改成发请求时抓 `loadGeneration` + `keyword` + `sortOrder` 三样，**后台与改列表前各验一次**（`SearchFragment.invalidateInFlightRequests()` 在 `update` / `selectSort` / `retry` 里自增）。
  - **一次搜索最多 10 个请求**：通用重试对 `-352/-412` 重试 5 次，回退旧接口再走一遍。新增 `NetWorkUtil.getJsonOnce()`，回退只发一次；主链路重试行为不变。
  - **旧接口的其它错误码被伪装成空结果**：`resultOrThrow` 以前对所有非风控情况返回 null，`-400` 之类会显示成「啥都木有」。现在非零 `code` 抛 `SearchApi.SearchException`（`ApiErrorText.opFailed`），页面把错误码弹出来；`SearchFragment.report()` 改成认 `SearchException` 基类，风控与其它错误码都按自己的文案显示。
  - **测试补上判据**：`SearchApi.shouldFallback` / `isRiskControl` 提成 public static 供单测，新增 2 例钉住两种风控形态与「真没结果不算风控」。合并上游前的基线 **79 类 / 847 例 / 0 失败**；合并上游（v3 收藏夹等 15 个提交）后 **87 类 / 941 例 / 0 失败**。
- **合并上游后的回归（26.10.24，本机 MuMu Android 15 x86_64 / coexist debug）**：合入 upstream 15 个提交（收藏夹 v3 迁移、无极调音、安全加固等）后重装包，搜索页排序链路仍正常——「排序：综合」→ 弹窗选「播放量」→ 797.2万 → 501.8万 → 443.9万 → 412.9万 严格降序；`logcat` 无 `FATAL EXCEPTION`（崩溃缓冲里那条是 `uiautomator` 自己的，与本应用无关）。构建与单测见上。
- **用户反馈修复（26.10.24 当天，两处）**：① 「排序按钮消失和出现的时机、动画没跟随搜索框」——旧实现是搜索框 `translationY` 滑 300ms、按钮自己瞬间 GONE，且 `ViewPager` 锚在按钮下面（按钮 GONE 那一下列表整块往下跳）。现在两者由**同一条 `ValueAnimator` 按同一进度收放真实高度**（`SearchActivity.applyTopChrome`，`CHROME_ANIM_MS = 200`），列表跟着平滑补位；自然高度只在 `wrap_content` 时采样并缓存，避免把动画中间值记成自然高度。② 「第二次点搜索框时按钮没消失，挡住候选词」——历史 / 建议浮层与按钮占的是同一条（都锚在 `searchbar` 下方），按钮是 MaterialButton 带海拔，画在浮层上面。现在浮层一律走 `showHistoryDropdown()` / `showSuggestionsDropdown()` / `hideDropdowns()` 三个入口，浮层开着时按钮置 `INVISIBLE`（**让画面不让位置**，列表不整体上移，浮层正好盖住它）。
  - **模拟器实测**：点搜索框 → 候选词第一行完整可见、按钮不再压在上面；滚动 → 搜索框与排序条同时收缩（把时长临时调到 1500ms 抓过中间帧，两者同比例变形）、结束时列表紧贴顶栏；回滚 → 一起展开；切到「番剧」分类按钮消失、切回「视频」恢复；`logcat` 0 条 `FATAL EXCEPTION`。
  - **验证**：`:app:assembleDebug`（coexist）通过；两 flavor 单测各 **87 套 / 941 例 / 0 失败**（纯 UI 改动，未新增用例）；markdownlint 40 文件 0 issue。
- **第二轮审查（26.10.25，外部评审）修掉的 P1 + 文档失实**：
  - **风控直接回 HTML 拦截页时回退链根本没启动**：`SearchApi` 只在拿到**已解析 JSON** 后才判 `shouldFallback`，而风控被拦时 B 站还可能直接回一张 `<!DOCTYPE html>` 的「出错啦」页——那会让 `NetWorkUtil.getJson` 的 doctype 重试耗尽后抛 `IOException`（或非 JSON 抛 `JSONException`），请求层就失败了，页面只显示「网络错误」，排序按钮也进不了风控态。实测形态见 `docs/review/agent-test-guide.md`（`412` + `<!DOCTYPE html>`）。新增 `SearchApi.fallbackToLegacy(keyword, legacyUrl, primary)`：请求层失败时同样兜一次旧接口；旧接口给出明确结论（风控 / 服务端错误码）就用它，两边都失败时**主链路是网络层失败就如实上抛**（真断网别诬陷成风控）、主链路是合法 JSON 的风控降级则抛 `RiskControlException`。
  - **`getJsonOnce` 名不副实**：它原本仍走 `get()` → `executeWithDoctypeRetry`，而风控页正是 HTML，等于又重试 5 次——注释里「只发一次」是错的。现在改成**直连一次**（自己建 `Request`，不经过 doctype 重试），调用点只有 `SearchApi` 的回退链路。
  - 清理审查指出的失实/死代码：`SearchSortOptions.SLOT_COUNT` 零引用且注释指向已废弃的 4 格排序条（删）；`SearchApi.resultOrThrow` 上叠加的两段 javadoc（合并）；`refreshSortBar` 的 KDoc 说「点它只提示」但实现会真的 `retry()`（改注释）；`SearchFragment` 未使用的 `ColorScheme` import（删）。
  - 文档对齐：`FEATURES.md` 的排序按钮说明改成「点它会真的重试一次」；`bilibili-api-notes.md` §3.14 补「三种风控形态都会回退」与「`getJsonOnce` 连 doctype 重试也跳过」；`architecture-map.md` §6 的 http 明文清单移除 `SearchApi`（已改 `httpsUrl()`）。
  - **切到首次访问的分类时排序按钮可能一直不出现**（审查「疑点 A」的加固）：`FragmentStateAdapter` 是懒创建的，`onPageSelected` 里 `findFragmentByTag("f$position")` 可能还是 null，那一刻 `refreshSortBar` 只能判成「本分类不支持排序」把按钮收掉，而 `sortBarEligible` 是缓存值、之后没人重算。现在 `onPageSelected` 之后 `viewPager.post { refreshSortBar() }` 补算一次（幂等，按当时的 currentItem 算，不会自旋）。审查侧的观察是「切 tab 时整体收起、上滑一次即恢复」，判断为 main 既有逻辑而非本功能引入；这条补算属于顺手加固，不是回归修复。
  - **验证**：`:app:assembleDebug` 通过；两 flavor 单测各 **87 套 / 941 例 / 0 失败**；markdownlint 40 文件 0 issue。**HTML 风控回退这条新分支没法主动触发**（要 B 站真回拦截页），靠既有纯函数判据 + 构建与单测兜底，如实记在这里；本轮也**未上设备实测**（模拟器已关闭，且这条路径模拟器同样复现不了）。
- **未做**：用户 / 直播的排序（它们的 `order` 是另一套键：`fans`/`level`、`online`/`live_time`），以及「最多弹幕 / 最多评论」两档（手表屏上弹窗列表已经够长）。

### 3.63 下载卡在 20%：分片下载加看门狗 + Range 校验 + 强制 HTTP/1.1（26.10.25）

- **缘起**：用户报「下载进度卡在 20% 不动」。20% 是 `DownloadService.processDownloadSection` 给 MP4 / 纯音频主文件写死的**起始基准**（`setDownloadProgress(id, 0.2f, ...)` 后 `downFile(..., 0.2f)`），所以卡在 20.0% 基本等于主文件的 `downFileSpeedSeg` 阶段一个字节都没再往前推（失败分支会清掉进度映射，因此不是"映射残留"）。
- **实测（真实 B 站直链，51,973,319 字节的 720P MP4）**：单连接 h1.1 21.28s ≈ 2.44MB/s；5 路并发 Range（h1.1）聚合 ≈ 10.4MB/s（约 4.2 倍）；5 路 h2 流复用一条连接只有 ≈ 4.9MB/s 且**没有传输层停顿**（`maxGap` ≤ 173ms）——所以「多线程本身」没问题，问题在分片实现的容错。
- **定位到的 4 个缺陷**：①轮询循环没有停滞看门狗，唯一的时间约束是 30s 总 join 预算，线程卡在 DNS／h2 流排队／读超时时进度一直停；②join 超时后不检查线程是否还活着就起单连接重下，两拨线程写同一个文件；③`downloadSegment` 不校验 `206`／`Content-Range`，若 CDN 忽略 Range 回 200，每片都把整个文件读一遍（5 片 = 5 倍流量，期间进度不动），最坏 3 次重试 × 5 片；④HEAD 没有 `callTimeout`，"连上但一直不给响应"可以无限期等下去。
- **改法**：
  - 新增 `service/download/SegmentDownloadPolicy.kt`（`internal object`，纯逻辑无 Android 依赖）：`STALL_TIMEOUT_MS = 12s` / `ABORT_JOIN_TIMEOUT_MS = 3s` / `FINISH_JOIN_TIMEOUT_MS = 30s`、`hasStalled` / `isPartialContent` / `isRangeIgnored` / `parseContentRangeStart` / `isContentRangeMismatch` / `shouldDisableSegmentMode`。
  - `DownloadService.downFileSpeedSeg`：轮询里先判停滞（12s 没有新字节 → 记日志、取消在飞 Call、不再干等）→ 中止后只给 3s join 预算，线程没退出就报错（**绝不并发重下**）→ 停滞 / Range 不可用时回滚已计字节、把进度退回基准，再回退整文件单连接；命中后置 companion 的 `@Volatile segmentModeDisabled`，本次进程后续任务直接单连接（不复位）。
  - `downloadSegment`：新增 `SegmentSharedState`（总字节 `AtomicLong`、失败 / Range 不可用 / 已中止三个 `AtomicBoolean`、在飞 `Call` 的 `CopyOnWriteArrayList`、每片最后收字节时刻 `AtomicLongArray`）替代原来的两个裸原子量；每片响应校验「200 = 忽略 Range」「非 206 失败」「Content-Range 起点不符」；读循环加 `written < expectLen` 上界且只写本片范围；每写一块就 `markByte(idx)` 喂看门狗。
  - `downFileSpeed`：HEAD 单独用 15s `callTimeout`；下载专用 OkHttpClient 强制 `HTTP_1_1`（实测 5 连接 h1.1 ≈ 10.4MB/s，5 条 h2 流 ≈ 4.9MB/s）。
- **结论：不删多线程**。删掉等于放弃实测约 4.2 倍的吞吐；项目自己的 `docs/archive/superpowers/specs/2026-10-13-download-engine-optimization.md` 定的方向也是「分片健壮化 + 失败语义统一」，不是砍功能。
- **验证**：`./gradlew.bat :app:assembleDebug` 双 flavor 成功；`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 各 **88 suites / 946 tests / 0 failures**（新增 `SegmentDownloadPolicyTest` 8 例）。
- **遗留**：①分片失败兜底仍是「整文件单连接重下」，没有真正的断点续传（P0，见上述 spec）；②PCDN 域名失效时没有切 `backup_url`；③进度公式仍是 0.2 基准 + `%.1f`，大文件上的低速波动在界面上不明显。**未真机复现卡 20%**（本机 adb 无设备、模拟器未开），看门狗 / 降级路径靠单测与真实直链探针（临时探针单测与结果文件已删除，未进提交）验证。

**本方补充（同日，独立修同一批缺陷时多做的两项）**：上游这轮已经把「分片不校验 206 / Content-Range」「join 超时后并发重下」都修了，方向与本方一致。另有两点为本方独有，已并入同一实现：

- **落盘尺寸兜底**：分片全部写满 `expectLen` 之后仍校验 `file.length() == totalSize`。`setLength` 在部分 FUSE / exFAT 存储上可能静默失败，此时即使每片都算对，文件实际尺寸也未必对，仅凭字节计数发现不了。
- **失败原因留痕**：某片 3 次尝试全失败时打一条日志（含分片号与区间）。旧实现静默吞异常，"服务端忽略 Range / 区间对不上"只表现为"多线程慢、退回单连接"，不看日志查不出来。

**本方实测（MuMu Android 15 / coexist debug，未登录）**：av170001 分 P「Хоп」按 720P 下载（`qn=64` → `fnval=1` 单 MP4，7.87 MB > 2 MB 且 CDN 支持 Range → 走的正是分片路径），落地后拉回本机 `ffprobe` 校验 `h264 512x288 + aac / 199.33s / 7871518 字节`，容器可正常解析，0 条 `FATAL EXCEPTION`。未覆盖的是 DASH 双文件分支（未登录拿不到 `qn>64`）。

### 3.64 断网启动仍进自定义启动页：断网兜底进缓存页（26.10.25）

- **缘起**：用户「314」反馈（QQ 截图）：「自从改完打开界面逻辑之后，无网络时打开会跳转到自定义打开界面，希望改回原来那种无网络时打开自动进缓存，能正常加载时再进入自定义界面。」
- **根因**：`3f54b1c` 把启动目标改成「菜单设置里选定的启动页」时，只换了 `SplashActivity` 里的取页逻辑，全流程没有网络可用性判断。`menu_start` 缺省是「推荐」，推荐 / 动态 / 消息等页断网时只能看到加载失败；原本能离线使用的「缓存」页反而被启动页设置挡在后面。
- **改法**（三条，互不耦合）：
  - `util/NetWorkUtil.java:693` 新增 `isNetworkAvailable(Context)`：移植上游 1.2.4 的本地判断（`getActiveNetworkInfo() != null && isConnected()`，不用 API 23 的 `NetworkCapabilities`——`minSdk 19` 上老系统会拒绝），异常一律返回 `true`（fail-open，判断不出来就退回原来的失败路径）。
  - `util/MenuConfig.kt:107/146` 新增 `OFFLINE_START = "local"` 与纯函数 `resolveLaunchStart(enabled, stored, online)`：`online` 走原 `resolveStart`，否则固定 `local`。**只决定本次启动，不写回 `menu_start`**。
  - `activity/SplashActivity.kt:341-363`：启动时先取 `online`，按 `resolveLaunchStart` 选页；断网在 `finish()` 后 `MsgUtil.showMsg(R.string.msg_splash_offline_to_cache)`（缓存页 `BaseActivity` 注册 EventBus 后收到 sticky 消息）；四处启动网络请求（token/Cookie 刷新、`AppInfoApi.check`、公告、匿名统计）整体包进 `if (online)`——这是断网时不再弹「终端接口出现问题」的关键。更新日志页与启动提示都走本地判断，保留。新文案 `msg_splash_offline_to_cache` 进 `strings.xml`。
- **单测**：`MenuConfigTest` 新增 3 例（在线沿用用户设置、断网无论设置成什么都进 `local`、`local` 必为固定项）。
- **验证（MuMu 模拟器，Android 12 / x86_64，26.10.25）**：
  - 在线默认：启动 → `RecommendActivity`（默认启动页）。
  - 在线自定义：`menu_start=settings` → `SettingMainActivity`（任务栈实测）。
  - 断网（`Active default network: none`）：启动 → `LocalListActivity`，logcat `I/MsgUtil>showMsg: 无网络，已进入缓存页`，缓存页底部 Snackbar 显示同一句；全程无「终端接口出现问题」。
  - `./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest` 全绿（双 flavor 各 **88 suites / 952 tests / 0 failures**，含新增 `MenuConfigTest` 3 例；以本轮 rebase 到最新 main 后 `app/build/test-results/` 下 `TEST-*.xml` 的 `tests=` 求和为准）。
- **遗留**：①真手表未验证（本机只有模拟器）；②断网启动仍会有 `BiliTerminal` 启动检查（更新检查、未读、追番）的后台网络失败日志，它们各自 catch 只记日志、不弹提示，属既有行为，本次不动；③模拟器上 `svc wifi disable` / `cmd wifi set-wifi-enabled disabled` 会让 guest 的 adb shell 卡死，断网测试只能靠重启模拟器，后续模拟器断网测试不要用关 Wi-Fi 的方式。

### 3.65 短视频页开启「圆屏适配」后并未适配（26.10.25）

- **缘起**：用户报告「短视频界面在开启圆屏适配后并未适配」。
- **现状（为什么没适配，两处）**：
  - `activity/base/BaseActivity.kt` 的 `setRound()` 只在 `findViewById(R.id.pageName) != null` 时执行；短视频页顶栏在 ViewPager item 的 `fragment_short_video_page.xml` 里、只有 `text_title`，没有 `pageName` → 整段被跳过（普通页面靠它把标题横向内缩 18% 宽、时间居中那一套完全没有作用到本页）。
  - `ShortVideoPlayerActivity.PageHolder.adjustVideoSize()` 只按整屏做 fit，没有圆屏内接圆分支；而本页继承 `InstanceActivity` → `BaseActivity`，`player_ui_round` 打开时 `BaseActivity.onCreate` 会给根 View 加 5%/3% 的 root padding（内容区小于整屏），算画面尺寸时也没扣内容区。
- **改法（单文件 `app/src/main/java/com/RobinNotBad/BiliClient/activity/video/ShortVideoPlayerActivity.kt`，+127 / −13）**：
  - 新增三个属性：`roundAdapted`（读 `SettingsKeys.UI_ROUND`）、`roundPadding = (screenWidth * 0.03f).toInt()`（与 `PlayerActivity.initUI()` 同一口径）、`contentWidth/contentHeight`（= `BaseActivity.window_width/window_height`，≤0 时退回整屏尺寸）。
  - `PageHolder` 先快照 `top`/`videoProgress`/`textProgress` 的 XML 基础 padding，`applyRoundInsets()` 按开关收放：圆屏时 `top.setPadding(padding*7, padding, padding*7, 0)`、进度条左右 `padding*4`、时间文本左 `padding*3`、弹幕上下 margin `padding*3`；关闭时逐项还原（幂等）。`bind()` 与 `initPlayer()`（拿到 `danmakuView` 之后）各调一次。
  - `adjustVideoSize(screenW, screenH)` 重写：可用区域优先取 `itemView` 实测尺寸（回退 `contentWidth/contentHeight`）；圆屏按视频比例求内接圆矩形——`diameter = min(屏宽, 屏高)`、`videoMul = 视频高/宽`、`side = sqrt(diameter² / ((视频高/宽)² + 1))`、宽 = `side`、高 = `side * videoMul`，再按可用区域等比夹；非圆屏分支按可用区域 fit（原逻辑按整屏）。**取 `min` 而不是 `max`**：首轮在 1440×2560 的模拟器上 `max` 会算出比屏宽还宽的画面。
- **验证（MuMu 模拟器，Android 12 / x86，`wm size 320x320` + `wm density 160` 模拟圆屏，26.10.25）**：
  - 圆屏开（5% / 3%）：内容区 `[16,9][304,302]`；`top [16,9][304,35]`、`text_title [79,18][241,35]`（= 16 + 7×9）、`videoprogress [16,216][304,236]`、`text_progress [16,238][304,253]`、`bottom_buttons [16,253][304,302]`。竖屏视频算出 157×279（9:16 内接圆），像素测量视频占列 81..237 与公式一致，四角距圆心 ≈160，底栏各按钮中心距圆心 ≈142~143（均 <160）。
  - 圆屏关：`top [0,0][320,25]`、`text_title [8,4][312,21]`（XML 原值）、竖屏视频 180×320 满高 —— 与改动前完全一致（无回归）。
  - 合入前的最终构建（已删掉 `onStart()` 热刷新钩子）在同一模拟器上复跑：`top`/`videoprogress`/`text_progress`/`bottom_buttons` 坐标与上面逐字一致，16:9 横屏视频同样按内接圆（279×157）居中，播放正常。
  - `.\gradlew.bat :app:assembleDebug` 与 `.\gradlew.bat :app:testStandardDebugUnitTest` 均通过。
- **遗留**：①真手表 / 真圆屏设备未验证（本机只有 MuMu 模拟器）；②没有加 `onStart()` 热刷新钩子——`MenuActivity.killAndJump()` 会先 finish 下层页面，从设置页回到短视频页必然是新实例（冷启动路径），root padding 与内缩都随新实例一次性生效；③验证后期 B 站短视频信息流接口偶发 `API error code=-400: 请求错误`（`ShortVideoFeedApi.x/v2/feed/index/story` 与动态兜底都失败），与本次改动无关；重试后恢复，最终构建复测正常。

### 3.66 DASH 下载完成后合并成单文件：1080P+ 也像 720P 一样一个文件（26.10.25，用户要求）

- **缘起**：用户提出「B 站 1080p 以上的缓存视频分视频流和音频流的分开的，现在我想让你给他变成和 720p 一样的合并的视频」。
- **现状（为什么以前是双文件）**：`processDownloadSection` 里 `qn<=64` 走 `PlayerApi.getVideo`（`fnval=1`，音视频合一 MP4）；1080P+ 只能走 DASH，下成 `video.mp4` + `audio.m4a`，播放时由 `PlayerActivity.audioTrackUrl` 额外挂一条外部音轨。合并层历史上存在过（`util/MediaMerger.kt`），2026-08-18 `25893f3` 因「合并阶段无进度卡 85%」「旧实现先删视频再改名有丢数据风险」被移除，2026-09-11 `183523f` 删掉了类本体；`docs/archive/superpowers/specs/2026-10-13-download-engine-optimization.md` 还写着「不做合并」。这次**决策反转**：两个旧硬伤现在分别有「进度分段常量」与「`safeReplaceTemp` 先备份再替换」的现成解法。
- **改法**：
  - 新增 `app/src/main/java/com/RobinNotBad/BiliClient/util/MediaMerger.kt`（`object MediaMerger`，259 行）：两个 `MediaExtractor` 各取第一条 `video/*` / `audio/*` 轨，**按 `sampleTime` 归并**写入 `MediaMuxer(MUXER_OUTPUT_MPEG_4)`（改掉旧实现「每轮一视频一音频」的交替写）；关键帧标志用 `MediaExtractor.SAMPLE_FLAG_SYNC`（避开 API 21 才有的 `BUFFER_FLAG_KEY_FRAME`）；`setOrientationHint` 透传旋转；写前查 `StatFs` 剩余空间（需 ≥ 两输入之和 + 4MB）；返回码 `RESULT_OK / NO_TRACK / NO_SPACE / STOPPED / FAILED`，**非 OK 一律删产物**。KDoc 写明「失败必须保留双文件、不得判下载失败」。
  - `service/download/DownloadPathSpec.kt`：新增 `FILE_VIDEO_MERGING = "video_merging.tmp"`（刻意不用 `.mp4` 后缀，避免被 `LocalListActivity` 对历史 `video_merged_temp.mp4` 的兼容改名逻辑误认成成品）。
  - `service/download/DownloadProgressMath.kt`：新增 `DASH_ATTACH_END = 0.12f` / `DASH_VIDEO_END = 0.52f` / `DASH_AUDIO_END = 0.88f` / `DASH_MERGE_END = 1.0f` 与 `dashMergeProgress(fraction)`；`DownloadProgressMathTest` 新增 2 例（严格递增 + 区间映射/夹取）。
  - `service/DownloadService.kt`：单 P / 多 P 两处 DASH 分支在 `replaceDashPairOrFail` 之后调 `mergeDashPair`（新增私有方法）——先判 `isPaused` 再合并；合并进度驱动 `setDownloadProgress`，`shouldStop = { !started }`；成功 `safeReplaceTemp(merged, video.mp4)` 后删 `audio.m4a`，失败只记日志。
  - `util/FileUtil.java`：`cleanDownloadTempFiles` 增加删除 `video_merging.tmp`。
  - `res/values/strings.xml`：新增 `text_download_merging`「合并音视频」。
  - `app/src/debug/.../DebugBridge.kt`：新增 `merge` 命令（给两条 m4s 直接跑一次 `MediaMerger`，回显返回码/耗时/产物轨道与采样数），`help` 与 `docs/review/agent-test-guide.md` §4.8 同步。
  - 新增设计文档 `docs/superpowers/specs/2026-10-25-dash-merge-single-file.md`（含历史决策反转说明与 API 19 兼容性依据）。
- **验证**：
  - 构建：`.\gradlew.bat :app:assembleDebug` 双 flavor 成功；`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 各 **88 suites / 954 tests / 0 failures**（`app/build/test-results/` 下 `TEST-*.xml` 求和）。
  - 设备侧容器验证（Wear OS 模拟器 `sdk_gwear_x86_64`，Android 14，装 `standardDebug` universal 包；注意 **x86 单 ABI 包装不上**，本模拟器 `abilist=x86_64,arm64-v8a` 没有 32 位）：把仓库根的两条匿名 360P 样本 m4s（视频 3,867,429B / 音频 1,265,842B）经 `run-as` 拷进应用私有目录，广播 `--es cmd merge` → `result=OK(0) cost=3381ms`、产物 5,167,200B、`tracks=2`（`video/hevc` 2992 samples / `audio/mp4a-latm` 8584 samples，与源文件一致）、`progress=0,20,50,90`。
  - 踩到的坑：`adb shell` 推到 `/sdcard/Android/data/<包名>/files/` 的文件，应用进程里 `File.isFile` 看不到（广播返回「文件不存在」）；改用 `adb push /data/local/tmp` + `run-as cp` 进 `/data/data/<包名>/files/` 后正常。**这是验证路径的坑，不影响真实下载**（真实下载由应用自己写外部目录）。
- **遗留**：①真实登录态下载 1080P→合并→本地播放整条链路未跑（见 §2.1）；②真手表未验证；③HEVC 视频轨写入需 API 24+，但 `DashData` 选流优先 AVC，正常路径不触发；④合并期间多占约一份文件大小的磁盘空间，空间不足直接跳过合并（保留双文件）。

### 3.67 部分设备弹窗显示成「只有方块、没有内容」（OPPO Watch X2 Mini，26.10.25，用户反馈）

- **缘起**：用户发来实拍照片：「修复部分设备弹窗显示成这样的bug」——圆表盘屏幕内一个接近整屏的方形面板，只有 1px 描边、**里面没有任何文字/按钮**。追问后确认：设备 `oppo watch x2 mini`，只有这一款/少数机型出现，**每次启动必现**（即启动时的「隐私说明」`TerminalDialog.choice` 空壳，按钮不可见 = 用户卡在闪屏，进不了 App）。
- **复现对照**（都不是 bug 的成因）：MuMu Android 12 1440×2560@640、`wm size 320x320`+`density 160`、`wm size 454x454`+`density 320`、Wear OS 5 圆屏 466×466@320（`dumpsys uimode` = `0x16`，真手表 uiMode）——隐私弹窗**全部渲染正常**；实验性给 overlay 加 `android:windowIsFloating=false`、`android:windowElevation=0dp` 也不复现（appcompat `values-watch-v20` 那条覆盖不是原因）。
- **取证**：无法拿到该机 adb，临时加了一个诊断包 —— `util/DialogDiagnostics.kt`（`ENABLED=true`）在 `Sheet` 的 `onShow` 后抓版本/设备/Configuration/屏幕 metrics/窗口 `LayoutParams`/主题属性/完整 DecorView 视图树（含每层 `bg` 描述与 `getGlobalVisibleRect`），写 logcat（TAG=`DialogDiag`）+ `files/dialog-diag.txt`，再跳 `ShowTextActivity` 整页显示让用户拍照回传（空白弹窗拍不出来，但这页一定渲染）。用户装上 `coexist release` 诊断包后回传了报告。
- **真机报告（OPPO OWW242 / Android 11 SDK 30 / ColorOS Watch）关键项**：`Configuration 233x233dp densityDpi=320 uiMode=0x26 round=true`；屏幕 `466x466 density=2.0 dpi=320`；窗口 `w=378 h=-2 gravity=17 fmt=fffffffe`、`decor 378x415 bg=Color#00000000`；主题 `android:background=GradientDrawable`、`windowBackground=GradientDrawable`、`windowIsFloating=false`；颜色 = `Theme.ClassicTerminal`（`colorSurface=#FF262626`、`colorOnSurface=#FFEBE0E2`、`colorPrimary=#FFFF6699`，照片里的紫是相机偏色）。
- **根因（视图树直接给出）**：**主题 `android:background` 会被框架/ROM 贴到弹窗 window 里每一个内部视图上** —— 报告里 `SwipeDismissLayout` / `FitWindowsFrameLayout#action_bar_root` / `ContentFrameLayout#content` / `AlertDialogLayout#parentPanel` / `topPanel` / `contentPanel` / `customPanel` / `custom` 的 `bg` 全是同一份 `GradientDrawable`（= `@drawable/dialog_background`）。且 ColorOS Watch 的 `DecorView` 末尾**多一个 `SwipeGlowOverlay`**（378×415），也被贴上同一份**不透明**面板，它排在内容之后 = 画在最上层，把我们 `@id/custom` 里的内容整块盖住。MuMu/Wear OS 没有这个 overlay → 一直只有部分设备复现。（此前用「把主题 background 换成纯品红」的实验已在 MuMu 上证实主题 background 确实落在 appcompat subdecor 各层，只是那里背景透明/无遮挡，肉眼看不出来。）
- **改法（3 个文件，不再依赖主题 `android:background`）**：
  - `res/values/themes.xml`：7 份 `ThemeOverlay.<X>.Dialog` 删掉 `<item name="android:background">@drawable/dialog_background</item>`，只留 `colorSurface`/`colorOnSurface`/`colorError`；注释块补上「为什么不能再写」的证据。
  - `res/drawable/dialog_background.xml`：注释改为「只给 `layout_dialog_terminal.xml` 根视图用，不要再挂主题 android:background」。
  - `util/TerminalDialog.kt`：`Sheet.init` 里 `content.background = AppCompatResources.getDrawable(dialog.context, R.drawable.dialog_background)`（窗口背景仍透明）；新增 `Sheet.stripForeignBackgrounds()`，在 `onShow` 第一帧后遍历 `decorView`，把**不属于 content 子树**的视图背景清成 `null`（兜住 ROM 在 show 之后才挂的装饰层）。
- **验证**：
  - Wear OS 5 模拟器（466×466@320 圆屏，standardDebug universal 包）：隐私弹窗外观与修前**逐字节相同**（截图 sha256 一致），诊断视图树里 `android:background=null`、内部各层 `bg=null`、content 根 `bg=GradientDrawable`（419×440 窗口）。
  - MuMu Android 12 1440×2560@640：面板/圆角/1dp 描边/标题/正文/「不同意·同意」/提示行全部正常。
  - `.\gradlew.bat :app:assembleDebug` 成功；`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 均 BUILD SUCCESSFUL、0 失败。
  - 诊断代码已清理（`DialogDiagnostics.kt` 删除、`TerminalDialog` 与 `SplashActivity` 里 `ENABLED` 分支删除、`SplashActivity` 恢复原样），临时提升的 `versionCode 2610242` 已在 `c340444` 正式提交里保留。
- **遗留**：①**用户侧真机未复测**（本机没有 OPPO 手表），只验证了「主题不再给内部视图贴背景 + 内容树自绘面板」这一条链路；②若该 ROM 还有其他在内容之后挂的装饰层且带**非 null 背景**，`stripForeignBackgrounds()` 会在 `onShow` 后的第一帧清掉它——但如果它在那之后再挂就仍可能遮挡（当前证据里没有这种情况）；③`docs/agents/pitfalls.md` 里若还有「弹窗面板走主题 background」的旧说法需要同步（已 grep：无）。

### 3.68 「我的」页左上箭头方向错误（26.10.25，用户反馈）

**规则**（26.10.25 用户明确）：**由菜单唤起的一级页**用 `arrow_down`，点它打开菜单并在同级页面间切换；**一级页的子栏目**用 `arrow_back`，点它返回上一级。图标与行为必须一致。

- **现象**：菜单进「我的」，顶栏标题左边是 `←`（返回箭头），但点它并不返回——打开的是菜单。
- **根因**：`BaseActivity.kt:584` 对 `InstanceActivity` 装的是「点顶栏打开菜单」（`setMenuClick`），对其它页面装的才是「返回」（`setTopbarExit`）。`MySpaceActivity : InstanceActivity()` 属于菜单级，图标该是 `arrow_down`；但 `activity_myspace.xml:28` 写死了 `@drawable/arrow_back`。
- **为什么不能只改布局**：这个布局被 `MySpaceMoreActivity : BaseActivity()`（返回级）复用，它那边 `arrow_back` 才是对的。改布局等于把「更多」页改坏。
- **修法一（图标跟随角色）**：角色判定收进 `InstanceActivity.setMenuClick()`——装监听时顺手把 `pageName` 的开头图标覆盖成 `arrow_down`。菜单级一律朝下、返回级保持布局里的 `arrow_back`，新页面继承 `InstanceActivity` 就自动对。
- **修法二（子栏目站错队）**：`TodoListActivity`（「TO DO清单」）是从「设置」进的**子栏目**（`SettingGroupActivity:725` / `SettingsIndex:197`），却继承了 `InstanceActivity`——于是顶栏点开菜单、图标也被修法一改成了 `⌄`，反过来错。已改成 `: BaseActivity()`，同时 `activity_todo_list.xml` 里原本写死的 `arrow_back` 就此正确。核对过菜单 14 个入口：全部经 `InstanceActivity`（含 `RefreshMainActivity` 间接继承），只有它一个子栏目混进来。
- **不受影响**：`LocalListActivity`（菜单「缓存」）顶层自己就设 `arrow_down`、进文件夹后设 `arrow_back`，两次都在基类之后执行。
- **实测（MuMu Android 15 x86_64 / coexist debug）**：菜单页 `^`（自身，收起菜单）；「设置」`⌄`、「我的」`⌄` 且点顶栏打开 `MenuActivity` ✓；「TO DO清单」`‹` 且点顶栏返回 `SettingMainActivity` ✓；「更多」仍是 `‹` 且返回 ✓；`logcat` 0 条 `FATAL EXCEPTION`。
- 规则已记进 `docs/agents/pitfalls.md`「基类与列表」。

### 3.69 启动时发现新版本自动跳到更新页（26.10.25，用户要求）

- **缘起**：用户要求「先同步远端仓库，之后修改，当发现新版本的时候自动跳转到下载页面」。手表屏小，此前非强制更新只在启动时闪一条 Toast「发现新版本 x.y.z」（`BiliTerminal.kt` 旧 `:491`），用户很容易错过、也就不会去更新。
- **改法（3 个文件）**：
  - `util/UpdateManager.kt:96` 新增纯函数 `shouldAutoOpenUpdatePage(config, installedVersionCode, lastSeenVersion)`：**非强制** 且 `config.versionCode > installedVersionCode` 且 `config.versionCode != lastSeenVersion` 才返回 true。不碰 `Context` / `SharedPreferences`，直接 JVM 单测。
  - `BiliTerminal.kt` 非强制分支（现 `:545-555`）不再直接弹 Toast：条件满足时 `CenterThreadPool.runOnUiThread { requestAutoOpenUpdatePage(config) }`。`requestAutoOpenUpdatePage()`（`:129`）把版本存进 `@Volatile pendingAutoOpenUpdate`（`:121`）再尝试打开；`tryShowAutoOpenUpdatePage()`（`:140`）在前台 Activity 就绪时 `startActivity(UpdateActivity)`，extras 与强制更新链路一致（`has_config` / `version_code` / `version_name` / `description` / `download_url` / `force_update=false`）；页面拉不起来时退回原来那条 Toast（`:158`）。
  - `onActivityResumed`（`:432`）末尾再调一次 `tryShowAutoOpenUpdatePage()`——**检查更新是异步的，结果可能在启动页还在播动画时返回**，那时直接 `startActivity` 会被启动页随后拉起首屏的动作盖住；`SplashActivity` 不算就绪，已在 `UpdateActivity` 上也不再叠一层。
  - 「一个版本只自动跳一次」不加新状态：更新页 `showUpdateInfo()` 本来就写 `update_last_new_version`，这里直接复用（用户看过没更新就不再反复打扰）。强制更新链路（`forceUpdateBlocking` + `onActivityPreCreated` 拦截）未改。
  - 新增 `app/src/test/java/com/RobinNotBad/BiliClient/util/UpdateManagerAutoOpenTest.kt` 6 例：首次见到新版 / 上次记录更旧 → 跳；强制更新 / 已看过 / 同版本 / 更旧发行版 → 不跳。
- **验证**：
  - 构建：`:app:assembleDebug` 双 flavor 成功；`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 成功，`UpdateManagerAutoOpenTest` 6 例 0 失败。
  - 设备侧（SDK AVD `wear5`，Android 14 / 466×466 圆屏，`coexistDebug` universal 包；**临时**把 `versionCode` 降到 `2610230` 造出「已装 < Gitee 最新 `2610240`」，测完已改回 `2610242`）：
    - 首启：`logcat` 有 `更新检查>发现新版本 26.10.24，已自动打开更新页`；前台 Activity = `UpdateActivity`，页面显示「发现新版本 / 版本 26.10.24 (2610240) / 取消 / 下载更新」。
    - 更新页打开后 `default.xml` 里 `update_last_new_version=2610240`；杀进程再启动，前台停在 `RecommendActivity`、无自动打开日志 → 「一版一次」生效。
    - 环境坑：AVD 启动后 Wi-Fi 默认没连（`wlan0` DOWN、`ip route` 为空），应用报 `Unable to resolve host "gitee.com"`；`adb shell svc wifi enable` + `adb shell cmd wifi connect-network AndroidWifi open` 连上 `10.0.2.16` 后才能测（已记进 `docs/review/agent-test-guide.md` §6）。
- **遗留**：①**启动期若恰好有新公告**（`TerminalApi.checkAnnouncement` 把服务端返回的未读公告逐条 `MsgUtil.showText`，只记最大 id），公告页会叠在更新页之上，按返回逐层退出后才能看到更新页——这是既有启动公告行为，本次不改（用延迟/重排去抢时序太脆弱）；用户已读过最新公告（常态）时不出现。②真手表未验证，只有 SDK AVD。
- **文档**：`docs/FEATURES.md` §14.15 与「后台检查更新」、`docs/copy-inventory/02-update-log.md` L-032、`docs/architecture-map.md`（启动链路 + 改功能注意）。

### 3.70 部分设备内测码更新报 `Trust anchor for certification path not found`（26.10.26，待真机）

- **症状**：部分设备长按「检查更新」输入内测码后报 `java.security.cert.CertPathValidatorException: Trust anchor for certification path not found`；同一台设备走 Gitee / GitHub 的普通更新正常。
- **根因（推断，本机沙箱连不上两个分享站，没能直接看到证书链）**：内测分享站（`file.zsapp.asia` / `share.lanol.cn`，自建 FileCodeBox）用的是 Let's Encrypt 证书，它的根 **ISRG Root X1 直到 Android 7.1.1（API 25）才进系统证书库**。以前老系统靠「DST Root CA X3 交叉签名」兜底，2024 年 Let's Encrypt 已停发这条链，于是 Android 7.1 及以下（手表大量停在 4.4~7.1）找不到信任锚；Gitee / GitHub 用 DigiCert / Sectigo 的老根，不受影响。正好对上「部分设备」。
- **改法**：
  - 新增 `res/raw/extra_ca_roots.pem`：ISRG Root X1（至 2035-06-04）+ ISRG Root X2（至 2040-09-17），两张的 SHA-256 已与 Let's Encrypt 公布的值核对过，指纹写在 `ExtraTrust.java` 类注释里。PEM 里故意不写注释，老系统的证书解析器不一定容忍 PEM 块外的文字。
  - 新增 `util/ExtraTrust.java`：系统 TrustManager 的全部 `acceptedIssuers` + 内置根，一起放进内存 `KeyStore`，再用 `TrustManagerFactory.init(ks)` 生成**平台标准**的 `X509TrustManager`。链、有效期、主机名照常校验，**不是 trust-all**。没写成「先系统、失败再内置」的组合 TrustManager：API 24+ 的系统 TrustManager 是网络安全配置的 RootTrustManager，有 domain-config 时只接受带主机名的 `X509ExtendedTrustManager` 调用，而那个类 API 24 才有，minSdk 19 上继承会类加载失败。任何一步出错都回落系统默认。
  - `util/UpdateManager.kt` 的 `okHttpClient` 套上 `ExtraTrust.apply(...)`，所以更新检查、内测解析、内测 txt、APK 下载都走合并后的信任。**带登录 Cookie 的主客户端 `NetWorkUtil` 没动**，不扩大信任面。
- **验证**：本机沙箱没有 Android SDK，**`assembleDebug` / 单测没跑成**。把 `ExtraTrust` 的纯逻辑部分（`readCertificates` / `systemTrustManager` / `build`）剥出来在 JDK 25 上测过：PEM 读出 2 张证书（ISRG Root X1 / X2）；keytool 自签 CA 签发的叶子证书，系统 TM 拒绝、合并 TM（不含该 CA）**仍然拒绝**、合并 TM（含该 CA）通过。
- **未验证 / 残留**：①Android 编译与单测待跑；②要在 Android 7.1 及以下的真机或 AVD 上跑通内测码下载才算真修好；③如果分享站换成别家证书（比如根也不在老系统里），往 `extra_ca_roots.pem` 追加对应根证书即可，代码不用改；④如果其实是服务器漏发了中间证书，所有设备都会报错，那就不属于这次修的问题，要改服务器配置。

### 3.71 搜索排序入口挪进搜索框内部 + 收起逻辑回退（26.10.26 发版后，用户要求）

- **缘起**：用户要求「查看排序这个按钮加入之前搜索框的逻辑，现在删除搜索框，之后用这个图标（`filter_filters_funnel_list_navigation_sort_sorting_icon_123212.ico`）放在搜索框的最最左边来替代底部的排序功能入口，将搜索框的收起逻辑改为没加入排序功能之前的逻辑（一模一样最好）」。即：撤掉 26.10.24 加的那条全宽排序按钮与为它引入的整套高度动画，排序入口收敛成搜索框内左侧一个图标按钮。
- **改法（2 个代码/布局文件 + 2 张新图 + 3 处文档）**：
  - `app/src/main/res/layout/activity_search.xml`：删掉 `<Button @+id/searchSortButton>`（全宽、锚 `searchbar` 下方那条）；`ViewPager` 的 `layout_constraintTop_toBottomOf` 回 `@id/top`（旧版口径：搜索框盖在列表上方、列表从顶栏下方就开始走）；`searchbar` 内部最左边新增 `@+id/searchSortButton` 的 `ImageView`（30dp 宽、`marginStart 3dp`、`padding 5dp`、`scaleType=fitCenter`、`visibility=gone`、`contentDescription` 复用 `text_search_sort_title`，id 沿用旧名所以 `findViewById` 绑定方式不变）。
  - `SearchActivity.kt`：删整套 `applyTopChrome` / `setViewHeight` / `resetViewHeight` / `naturalHeightOf` / `setDropdownVisible` 与 `chromeAnimator` / `naturalHeights` / `CHROME_ANIM_MS`；`onScrolled` 逐字回退到加排序之前（`ObjectAnimator.ofFloat(searchBar, "translationY", 0f, -height)` + `handler.postDelayed({ visibility = GONE }, 200)`，展开反向，`dy < -1` 才展开，`animate_last` 200ms 节流）；`refreshSortBar()` 只控制图标显隐与点击（`hasSearched` 且当前 fragment 有 `sortOptions()` 才显示，风控时 `alpha=0.4` + 点击 `MsgUtil.showMsg` 后 `retry()`）；新增 `applySortIconVisible()` 在 `keywordInput.setPaddingRelative` 上把 `paddingStart` 在 8dp 与 `SORT_ICON_SLOT_DP=38f`（3dp 边距 + 30dp 图标 + 5dp 间隙）之间切，给图标让位。`showSortDialog()` 与 `TerminalDialog.singleChoice` 未改。
  - 图标：新建 `app/src/main/res/drawable-nodpi/icon_sort_filter.png`（仓库无密度分桶目录，图片一向放 `mipmap-nodpi`）。源 `.ico` 是 128×128 纯黑图形，黑色在 `#80242424` 的搜索框上看不见，用 Pillow 裁 alpha bbox（9,17,119,111）→ 6% 留白 → LANCZOS 缩到 128 → RGB 统一重着色成 `#EBE0E2`（与 `icon_search` 同色），alpha 原样保留。亮色模式另出一份 `app/src/main/res/drawable-notnight-nodpi/icon_sort_filter.png`（同图形、RGB 换成 `#3A3438`，与 `drawable-notnight/icon_search.xml` 的描边同色）：亮色模式的搜索框底是 `#0A000000`（浅底），`#EBE0E2` 那份会隐形，`drawable-notnight` 里既然已经给 `icon_search` 备了深色版，这个新图标也得同一套。以后改这个图标两份一起改（`tools/appearance/gen_light_mode.py` 的约定）。
- **验证**：
  - 构建/单测：`assembleDebug` 双 flavor 成功；`testStandardDebugUnitTest` + `testCoexistDebugUnitTest` 全绿。
  - 设备侧（SDK AVD `wear5`，Android 14 / 466×466 圆屏，standard debug，headless 启动 + `adb emu power ac off` 否则 Wear 会盖一层 `ChargingActivity`）：未搜索时 `searchSortButton` 不在 `uiautomator` dump 里（GONE ✔）；输入 `linux` 回车后以 `ImageView` 出现，bounds `[41,91][101,175]`（= 3dp 边距 + 30dp 图标，密度 2.0）、`content-desc="排序：综合"`、`clickable=true`；像素测量（screencap + Pillow 统计亮列）图标描边占 x=53..88、文字从 x=112 起，互不重叠，`paddingStart` 让位生效；点图标弹出 `TerminalDialog` 单选（标题「搜索结果排序」，项「综合 ✓ / 播放量 / 最新」，第四档「收藏」在滚动区外，底部「点空白处取消」），点「播放量」后 `content-desc` 变「排序：播放量」、列表 798.2万 → 502.0万 降序、弹窗消失，选档 → `selectSort("click")` 链路通。
  - 收起/展开（旧版口径）：上滑 `input swipe 233 350 233 120 300` 后 dump 里 `searchbar` 整个 GONE、卡片从 y=87（`@id/top` 下方）开始铺 ✔；下滑 `input swipe 233 130 233 400 400` 后 `searchbar` 回到 `[23,91][443,175]`、`content-desc` 仍是「排序：播放量」✔。
  - 亮色模式：把 `shared_prefs/default.xml` 的 `ui_color_mode` 临时改成 `light` 复测（测完改回 `dark`）——列表底色平均亮度 237 确认亮色生效，图标区最小亮度 4.0、958 个暗像素，说明 `#3A3438` 那份可见；列扫描图标描边到 x≈103、文字仍从 x=112 起，与暗色一致。
  - `logcat -b crash` 无本应用崩溃（只有模拟器自身 sensor HAL 的 `unexpected sensor type: 26`）。
- **遗留**：①真手表未测，只在 SDK AVD 上验证；②亮色版图标是手工另存的一份 PNG，`tools/appearance/gen_light_mode.py` 目前不覆盖 `drawable-nodpi`，下次重跑该脚本不会自动生成它（不影响运行，只是别以为它是脚本产物）。
- **文档**：`docs/FEATURES.md` §4.2 搜索排序条目、`docs/agents/pitfalls.md` 三条（搜索框盖住 fragment / 收起走 `translationY` / 图标靠 `paddingStart` 让位）、`docs/architecture-map.md` §8.7.3 搜索框滚动隐藏逻辑、`docs/review/fix-progress.md` §1「发版后的未发版改动」与本节。

### 3.72 系统时间错误防呆（26.10.27，用户反馈）

- **缘起**：用户（Sonim XP5800 三防机，非触屏）反馈 B 站与 Gitee 同时报证书错误，系统浏览器弹「Security warning」，要求「无视风险直接信任证书」。用 openssl 查过两条链：B 站是 GlobalSign R1、Gitee 是 TrustAsia → Certum，都是老根，不缺。两家 CA 不同却同时失败，最后确认是**设备时间不对**（没电重启回到出厂时间），所有证书都被判「尚未生效」。trust-all 违反 `docs/agents/security.md` 红线，没做；改成提示用户改时间。
- **改法**：
  - 新增 `util/ClockSanity.kt`（纯函数，不读资源）：`buildDateMillis(versionCode)` 由 YYMMDD0 反推构建日期；`isClockBehind(now, versionCode)` 判当前时间早于构建日期（放宽 1 天抵消时区）；`isCertTimeError(e)` 沿 cause 链找 `CertificateNotYetValidException` / `CertificateExpiredException`，并按消息兜底。
  - `SplashActivity`：进首屏 / 初始化向导后，若 `isClockBehind` 为真，`showMsgLong(msg_clock_behind)`（与断网提示同一套 sticky Snackbar）。
  - `MsgUtil.err`：`IOException` 分支先判 `isCertTimeError`，命中则显示 `err_cert_time`，替代笼统的「网络错误」。
  - 新文案 `err_cert_time` / `msg_clock_behind` 进 `strings.xml`。
- **验证**：
  - `ClockSanityTest` 9 例全过；`assembleDebug` 双 flavor 成功。
  - MuMu（Android 15 / x86_64，root）：`settings put global auto_time 0` + `date 010112002015.00` 把时间拨到 2015-01-01。共存版（未完成初始化）冷启动进 `SetupUIActivity`，截图底部 Snackbar 显示「设备时间似乎不对……请到系统设置里改正日期和时间」✔。普通版 `am start` 推荐页，logcat 原文：`SSLHandshakeException: Unacceptable certificate: CN=GlobalSign RSA OV SSL CA 2018` → `Caused by: CertificateNotYetValidException: Certificate not valid until Wed Nov 21 ... 2018 (compared to Thu Jan 01 12:19:46 GMT+08:00 2015)`，随后 `MsgUtil>showMsgLong: 网络连接被拒：证书时间校验失败 / 请检查设备的日期和时间是否正确` ✔。测完 `auto_time 1` 恢复。
  - 未来时间（同一台 MuMu，`date 060112002031.00`）：推荐页 logcat 原文 `Caused by: CertificateExpiredException: Certificate expired at Tue Nov 21 08:00:00 GMT+08:00 2028 (compared to Sun Jun 01 12:00:01 GMT+08:00 2031)`，随后同样显示 `err_cert_time` ✔（12 秒后的截图里 Snackbar 已按时长消失，以日志为准）。
- **追加：明文取服务器时间，提示里给出具体日期**（同日，用户同意方案）：
  - 新增 `api/ServerTimeApi.kt`：`http://api.bilibili.com/x/report/click/now`（`bilibili.com` 本在明文白名单，不加域名）。**独立 OkHttpClient，不带 Cookie、无拦截器、不跟随跳转**，4 秒超时；`parseNowMillis` 纯解析。
  - `ClockSanity.isSkewed`：本机与服务器时间差 > 1 小时（早晚都算）。
  - `MsgUtil.reportCertTimeError`：先给通用提示，再后台取服务器时间，偏差过大时补一条 `err_cert_time_skew`（设备时间 / 实际时间约）。60 秒内只取一次（`elapsedRealtime` 节流，不受系统时间影响）；该条四行，Snackbar 时长 8 秒并自带「x」。服务器时间**只用于显示**，不参与任何校验。
  - 验证：`ClockSanityTest` 11 例、`ServerTimeApiTest` 2 例全过，双 flavor 单测与 `assembleDebug` 通过。MuMu 拨到 2031-06-01 与 2015-01-01 各测一次，logcat 均出现「设备时间：2031-06-01 12:00 / 2015-01-01 12:00，实际时间约：2026-10-27 18:36」；2031 那次启动后 5 秒截图，提示条四行完整、右侧有「x」✔。测完恢复自动对时。
- **遗留**：①时间被拨到**未来**只在请求失败时提示，启动时不提示——本地没有可靠的「最晚日期」，用上次使用时间当上限会误报；时间只超前几天到几周、证书还没过期时网络本来正常，不影响使用；②系统浏览器 / WebView 的证书弹窗不归本应用管；③未在 XP5800 真机复测。

### 3.73 点赞投币收藏卡片的玻璃质感适配（26.10.27，用户反馈）

- **缘起**：用户反馈视频详情的点赞 / 投币 / 收藏卡片和文章详情末尾的点赞 / 收藏卡片在玻璃质感下观感很差。核查发现两张卡片都在布局里写死底色，玻璃覆盖层管不到：视频页是不透明 surface 底，夹在半透明玻璃按钮中间很突兀；文章页用 `?attr/colorPrimary` 实底，点赞激活后图标（`ThemeTint.setActiveIcon` 也用主色）和底色融成一片，卡片还比上方 UP 卡片宽。
- **改法**：
  - `attrs_glass.xml` 新增 `actionCardColor` / `actionCardStroke`；`themes.xml` 9 个会 inflate 布局的主题都定义了（surface + 各主题 brand 色），`themes_glass.xml` 7 个 `GlassOverlay` 改成玻璃按钮同源的 `glass_btn_bg_fill_<x>` / `glass_btn_stroke_<x>`。
  - `fragment_video_info.xml`、`cell_article_end.xml` 的 `like_coin_fav` 改读这两个属性；文章卡片补上 1dp 描边和 `activity_padding_horizontal` 的左右边距，宽度与 UP 卡片对齐。
  - 非玻璃模式下视频卡片观感不变；文章卡片从主色实底改成和视频页一样的 surface 底 + 主色描边，算顺手统一。
- **验证**：双 flavor `assembleDebug` 与单测通过。MuMu（共存版）截图看了视频详情和文章详情，组合有：粉色玻璃（暗色 / 亮色）、知乎蓝玻璃、经典终端玻璃、粉色非玻璃。两张卡片都和周围的玻璃按钮同一种半透明底和描边，文章卡片宽度与 UP 卡片一致 ✔。测完把主题、玻璃、亮暗设置恢复成默认。
- **遗留**：没有在已点赞状态下截图（要真去点赞），激活态图标和玻璃底的对比是按色值推断的。

### 3.74 弥散光晕代码审查：补单测 + 私信页顶栏挡光晕（26.10.27）

- **缘起**：审查 `ui/appearance/AmbientGlow.kt`。实现本身无误，但注释称「纯函数，供 JVM 单测」而单测为 0；私信页顶栏用 `?android:attr/colorBackground` 不透明底盖在全屏列表上，开光晕后顶部最亮处变成一条纯色横带（经典终端下它取主题 XML 的 `#1B1B24`，与运行时背景 `#000000` 本就不一致）。
- **改法**：
  - 新增 `AmbientGlowTest` 7 例：14 份方案的 `top` 全不透明（drawable 声明了 `OPAQUE`）、几何参数在界内、未知 key 兜底与 `ColorScheme` 一致（B站粉）、色标与 alpha 一一对应且首尾 1 / 0、`scaleAlpha`、亮色 `top` 与亮色背景的 CIE76 ΔE 在 [8, 20]（改米白底色忘了重算 `top` 时会红）。顺手把 `0x70C9A0F0` 补上 `.toInt()`。
  - `activity_private_msg.xml` 根布局 `FrameLayout` → `RelativeLayout`：顶栏去掉底色，列表 `layout_below` 顶栏（`paddingTop` 24dp → 4dp），消息不会再滚到标题下面，所以不再需要不透明底。
- **验证**：`AmbientGlowTest` 7 例全过，`assembleDebug` 通过。MuMu（共存版、经典终端、光晕开）进入真实私信会话截图：标题与时钟直接压在光晕上、没有横带，消息从顶栏下方开始排，输入栏位置不变 ✔。
- **遗留**：没在消息多到需要滚动的会话里测；`clipToPadding=false` 只影响列表自身 4dp 内边距，不会画到顶栏区域（列表本身就在顶栏下方）。

### 3.75 性能审查①：评论 / 动态的表情改为并发加载（26.10.27）

- **缘起**：全量性能审查（手表优先）。`EmoteUtil.replaceSingle` 对每个表情 `submit().get()` **串行阻塞**，而 `Reply` / `DynamicApi.analyzeTextContent` 在构造时就调它：一页 20 条评论 + 楼中楼的表情逐个排队等，首屏要等所有表情下完才显示，弱网下尤其慢；`get()` 无超时，一张卡住就拖到 OkHttp 读超时。私信页更糟：传入的是整个会话的表情表，每条消息都把表里**每一张**图同步取一遍，绝大多数根本不在这条消息里。
- **改法**：
  - `EmoteUtil.textReplaceEmote(ArrayList<Emote>…)`：只对文本里真正出现的表情发请求，先全部 `submit()` 再按同一截止时间（5s）取结果；超时保留原文字。`replaceSingle` 两个重载先判 `contains` 再下载，`get` 也加了 5s 上限。
  - 新增 `EmoteUtil.prefetch(urls)`：整页并发预取（总上限 10s），完成的立即 `clear` 退回内存 LRU；超时未完成的不 clear（clear 会取消下载）。接线点：`ReplyApi.analyzeReplyArray`、`DynamicApi.getDynamicList`。
  - 新增纯函数 `util/EmoteUrlCollector.kt`：评论收 `content.emote.*.url`（含楼中楼），动态只收 `modules.module_dynamic` 子树与 `orig` 里的 EMOJI 节点（评论预览等不渲染的位置不收）。`EmoteUrlCollectorTest` 4 例。
- **验证**：双 flavor `assembleDebug` 与单测通过。MuMu（共存版、已登录）：视频 av80433022 评论区日志 `表情预取 10/10 张，用时 160ms`，楼中楼 `[笑哭]` 截图确认渲染为图片 ✔；动态页 `表情预取 8/8 张，用时 37ms`，列表正常 ✔。
- **遗留**：没有在弱网 / 真表上对比改前改后的首屏耗时；私信会话内的表情没截到图（点击导航在 MuMu 上不稳定），只靠代码路径确认。

### 3.76 弥散光晕可选光源位置：上 / 下 / 左 / 右 / 中间（26.10.27）

- **缘起**：用户希望光晕最亮处不只能在顶部，可选下、左、右、中间，默认仍是上。
- **改法**：
  - `AmbientGlow` 的 14 份 `Spec` 仍只按「顶部」写一份，新增纯函数 `placeGlow` / `baseGradient` 按光源位置变换：下 / 左 / 右是把「沿边位置、离边深度」映射到对应边（下与上、右与左互为镜像，方屏上「左」即「上」转 90°）；中间把各团光晕的重心平移到屏幕中心、半径 × 0.8，底色改为从中心向外的径向渐变。`draw` 仍零分配。
  - 新键 `ui_ambient_glow_source`（`top` / `bottom` / `left` / `right` / `center`，未知值回退 `top`），写入走 `AppearanceManager.setAmbientGlowSource`（规整取值 + 递增版本号）。外观设置在「弥散光晕」下方加「光晕位置」单选，设置搜索可直达。
  - `AmbientGlowTest` 增至 15 例：默认值与规整、落盘与版本号、上位置与原公式一致、各位置贴边、镜像 / 旋转关系、中间位置重心与半径、底色渐变坐标。
- **验证**：双 flavor `assembleDebug` 与单测全过。MuMu（共存版）：选择弹窗列出 5 项；选「左」后页面自动重建、光晕移到左侧，落盘 `ui_ambient_glow_source=left` ✔；下 / 右 / 中间截图位置正确 ✔；亮色经典终端居中正常，五彩斑斓亮色居中时红 / 青两团关于中线对称 ✔。
- **遗留**：没在圆形真表上看「左 / 右」时光晕是否被圆角裁掉太多。

### 3.77 性能审查②：列表图片绑定页面生命周期 + 「翻动时不加载图片」真正生效（26.10.27）

- **缘起**：约 30 处 adapter / holder 用 `Glide.with(BiliTerminal.context!!)`（Application 级，`VideoCardHolder` / `NoticeHolder` / `TimelineAdapter` 还是 companion 里的静态 RequestManager）。后果：离开页面请求不取消，图片继续下载解码；页面退后台不暂停；`ImageAutoLoadScrollListener` 对 Activity 级 RequestManager 的 `pauseRequests()` 管不到它们，「翻动时不加载图片」开关形同虚设。基类与热门 / 必刷 / 排行榜还无条件 `install()`，不看这个开关。
- **改法**：
  - 新增 `GlideUtil.with(view)`：主线程上解析到存活 Activity 时用 `Glide.with(activity)`，否则回落 Application 级（不会抛「destroyed activity」）。不用 `Glide.with(view)`，因为它在 Fragment 树里找宿主，bind 热路径上更慢。上述调用点全部改调它，顺手删掉因此不再使用的 import。
  - `ImageAutoLoadScrollListener` 只在 DRAGGING → SETTLING（手指甩出）时暂停。生效之后要防表冠：表冠走 `smoothScrollBy`，是 IDLE → SETTLING，原来遇到 SETTLING 一律暂停，等于转表冠时图片一张都不加载。纯函数 `shouldPause` 配 `ImageAutoLoadScrollListenerTest` 3 例。
  - 6 处 `install()` 改为 `installIfEnabled()`，设置项关掉时不再暂停。
- **验证**：双 flavor `assembleDebug` 与单测通过。MuMu（共存版）：推荐页连续快速甩动 3 次，停下后封面全部加载 ✔；动态 / 消息 / 推荐快速进出各一次，logcat 没有崩溃 ✔；评论区甩动后头像与表情都正常 ✔。
- **遗留**：MuMu 没有表冠（`input rotaryencoder` 不存在），表冠滚动不暂停这一条只有单测覆盖，需要真表确认。

### 3.78 性能审查③：图片请求宽度按屏幕封顶，图片查看器懒加载（26.10.27）

- **缘起**：列表图请求 512w，高清图请求 1024w，只看档位不看屏幕。手表屏长边多在 320～480px，下载和解码的像素是实际显示的 2～4 倍。`ImageViewerActivity` 一进来就把所有页（动态九图就是 9 张）按 `SIZE_ORIGINAL` 全量加载，低内存的表上容易卡顿甚至 OOM。另外 `VideoCardHolder` 的复用分支只隐藏 UP 主 / 播放量，不再重新显示，卡片复用后这两行可能一直不出来。
- **改法**：
  - `PerformanceManager.capWidthToScreen(tierWidth, screenLongSidePx)` 纯函数：取屏幕长边向上对齐到 320/480/640/720/1024 档，宽度取它与档位值中的较小者；屏幕超过 1024 或读不到尺寸时保持档位值。`getImageMaxWidth()` / `getHqImageMaxWidth()` 都封顶，**只会变小**，CDN 缓存命中仍集中在少数几档。
  - 新增 `getZoomImageMaxWidth()`（不封顶）和 `GlideUtil.url_zoom()`，专给图片查看器用，因为要放大看细节。
  - `ImageViewerActivity` 只加载当前页和前后各一页，翻页时补齐，已加载过的页不再重复请求。
  - `VideoCardHolder` 复用分支补上 `VISIBLE`。
  - `PerformanceManagerTest` 新增 3 例：手表屏用小档，封顶不抬高宽度，大屏 / 未知尺寸保持档位。
- **验证**：双 flavor `assembleDebug` 与单测通过。MuMu（共存版）：466x466 下推荐页封面清晰 ✔；动态九图进入图片查看器后第 1 页正常，滑到第 3/9 页图片都正常加载 ✔；logcat 无崩溃、无 OOM ✔。
- **遗留**：实际请求 URL 里的宽度只靠单测覆盖，没有在模拟器上抓包确认。

### 3.79 反馈列表清账：7 个 bug + 私信撤回 + 内置选图页（26.10.28，用户反馈）

- **缘起**：按控制台未处理反馈逐条核对源码，挑出「原因在代码里看得出来」的 7 条全修，另做两个新功能（#57 私信撤回、#53 内置选图页）。#49（推荐页长按改稍后再看）、#11（内置文件管理器）用户决定不做。括号里是控制台反馈编号。
- **#55 短视频弹幕设置不生效**（此前已回复用户「已修复」，但源码里没有）：`ShortVideoPlayerActivity` 每个 `PageHolder` 写死 `isDanmakuVisible = true`，从不读「播放默认值 → 弹幕」。改成 Activity 级字段，用与长视频同一个 `PlayerDefaults.resolveTriState(PLAYER_DEFAULT_DANMAKU, PLAYER_LAST_DANMAKU)` 开播，切换时写回 `PLAYER_LAST_DANMAKU`。另一半根因在 `DanmakuManager`：`hide()` 有 `isPrepared` 守卫，prepare 之前调用只记状态就返回，所以 `prepared()` 回调里补一次 `danmakuView.hide()`。
- **#44「收到的赞」不显示弹幕点赞**：这类消息（还有「回复我的」里的弹幕被回复）已整体迁入**可选私有功能**，公开侧只留 `OptionalFeatures.fillOptionalMessage` / `openOptionalMessage` 两个挂点；`MessageApi` 的两个消息流在 `default` 分支问一次门面，不认识就照旧「无法识别这个类别」。字段缺省读取（`parseBusinessId` 缺字段给 -1）留在公开侧，`MessageApiBusinessIdTest` 3 例。迁移细节见 §3.83 与私有仓库 README。
- **#52 空关键词搜索后历史记录消失**：`SearchActivity.searchKeyword` 先 `hideDropdowns()` 再判空 `return`。判空挪到最前。
- **#51 文件夹里点 P2 从 P1 开始播 / 老缓存没声音**：`LocalListActivity.playVirtualCollection` 的起播地址固定取 `[0]`，`startPageIndex` 只写进了选集高亮。改成按 `startPageIndex` 取；双文件（未合并 DASH）缓存原来只传 mp4，现在另传与 `videoFileList` 对齐的 `audioFileList`，起播带 `audio_track_url`，`PlayerActivity.switchToLocalPage` 切 P 时把对应音轨交给 `doSwitchPage`（新增 `newAudioTrackUrl` 参数，原来这里固定置 null）。
- **#38 图片查看页仍响应右滑**：清单里的 `Theme.NoSwipe` 被 `BaseActivity.onCreate` 的 `setTheme` 覆盖。`ImageViewerActivity` 覆写 `forceNoSwipeTheme = true`（与短视频页同一钩子），退出走顶栏 / 返回键。
- **#20 表冠默认转得很慢**：原本打算把 `RotaryEncoderSupport` 总开关默认改成开启（`DEFAULT_ENABLED = true`），**用户看过改动后要求回退**，已用 `revert` 撤销，保持默认关闭与原文案。反馈 #20 仍未解决——系统自带表冠滚动在多数页面偏慢，且维护者没有表冠设备可调手感；真正要改需要先拿到原版终端的滚动倍率做对照。
- **#57 私信撤回（新功能）**：长按自己 120 秒内发出的文字 / 图片 → 菜单「复制文字 / 撤回」，撤回即发一条 `msg_type=5`、内容为目标 `msg_key` 的私信（`private_msg_content.md`「撤回消息」）。入口判据 `PrivateMsgApi.canRecall` 纯函数（自己发的、非撤回通知、本地时间看来未超 120 秒；本地时间比消息早时仍给入口，交服务端判，见 §3.72 的时间问题）。顺带：接口对被撤回的原消息仍回原文（`msg_status=1`），原先原文和「撤回了一条消息」两条都显示，现在 `shouldDisplay` 过滤掉原消息；撤回通知 seqno 必大于原消息，过滤不影响 refresh / loadMore 的分页起点。`PrivateMsgRecallTest` 5 例。
- **#53 内置选图页（新功能）**：新增 `activity/ImagePickerActivity`（不导出）+ 纯逻辑 `util/ImagePickerAlbums`（相册分组 / 过滤 / 读图权限名，`ImagePickerAlbumsTest` 4 例）。读 MediaStore 按 `DATE_ADDED` 倒序铺三列缩略图（Glide 按格子尺寸、RGB_565 解码），可按相册筛选；顶部「系统选择器」退回原 `ACTION_GET_CONTENT` 并把结果原样转交。**结果格式与 `ACTION_GET_CONTENT` 相同**（`data.data` 为 content Uri），私信 / 写评论 / 发动态 / 换头像四个调用点只换了 Intent。清单新增 `READ_MEDIA_IMAGES`（Android 13+；13 以下沿用已有的 `READ_EXTERNAL_STORAGE`），只在打开选图页时申请，拒绝后显示说明并可走系统选择器。
- **验证**：双 flavor `assembleDebug` 通过；`testStandardDebugUnitTest` 99 类 / 1039 例 / 0 失败（26.10.29 补 #44 回复流后为 **102 类 / 1087 例**）。MuMu（Android 12L / SDK 32，共存版，已登录）：
  - #52：搜索页空输入点搜索 → 日志出现「还没输入内容喵~」，历史记录列表仍可见（uiautomator 能取到两条历史）✔。
  - #55：`player_default_danmaku=off` 进短视频 → 弹幕 170 条已加载但不显示、按钮是关闭图标 ✔；点按钮打开 → 滚动弹幕出现、`pref_switch_danmaku=true` ✔；再关掉后上滑到下一条 → 仍为关闭 ✔。测完已删掉这两个设置项。
  - #53：选图页三列缩略图正常 ✔；相册弹窗列出「全部图片（226）/ 其他 / burst3 …」，选 burst3 后网格只剩 4 张 ✔；从发动态页进入选一张 → 回到发动态页显示「添加图片(1)」与预览 ✔；「系统选择器」拉起 `documentsui` 的 PickActivity，返回回到选图页 ✔。权限申请链路走到了 `GrantPermissionsActivity`（logcat 可见），但 MuMu 会自动授予，**「拒绝后」的分支没能测到**。
- **遗留**：
  - #44 的两条渲染（「弹幕被点赞」/「弹幕被回复」）仍未在设备上跑过：模拟器未登录，`msgfeed/like` / `msgfeed/reply` 都回 -101，只做过空数据冒烟（`NoticeActivity` 正常渲染、`logcat -b crash` 为空）。解析与文案现在都在私有仓库。
  - **同一轮顺手补的（仍在公开侧）**：`getReplyMsg` 的 `item.getString("source_content")` 与三个消息流共用的 `item.getInt("business_id")`，遇到没有该字段的条目会抛 `JSONException`，**把整页消息带崩**（不是少一条，是整页空白）。
  - ~~#51 没在模拟器上跑~~ → **26.10.29 已在 MuMu 上验证通过**（伪造双文件缓存不需要真机，`adb root` 就能往 `Android/media/<包名>/` 写）：用 ffmpeg 造两条「只有视频轨的 mp4 + 独立 m4a」，按 `DownloadPathSpec` 建 `.folders_info.json` / `.video_meta.json` / `.quality`，实测三条全过——①点文件夹里排第二的视频 → `视频地址` 日志是**那一条**（换成合并前的旧包复测会固定播第一条，正好反证了修复）；②`AudioTrack>主文件轨道=[VIDEO]，外部文件可用=true，选择=EXTERNAL` → `已启用唯一的外部音频轨道`；③在选集面板切到另一 P → `setDisplay: url` 换成另一条、外部音轨重新初始化。配方见 `docs/review/agent-test-guide.md` §4.9。
  - #57 没在模拟器上实测撤回（会真的给对方发私信），只有判据单测；需要真机给自己的小号发一条再撤回。
  - #38 的手势本身只能在真手表上确认（MuMu 没有窗口层右滑返回）。

### 3.80 收藏夹管理页：内容管理（批量删除 / 清空失效）+ 视觉统一（26.10.29，用户要求）

- **缘起**：用户给了 B 站「批量删除内容」（`x/v3/fav/resource/batch-del`）与「清空所有失效内容」（`x/v3/fav/resource/clean`）两份接口文档，要求把功能做在「长按收藏夹跳转出的管理页面」上，并顺手把这个管理页的视觉风格与配色统一到全站口径。
- **入口语义变更**：`FavoriteFolderEditActivity` 从「编辑收藏夹」升级为「收藏夹管理」（顶栏 + manifest label 共用 `text_activityfavoritefolderedit_06`，值改成「收藏夹管理」）。`FavoriteFolderAdapter` 的长按不再拦默认收藏夹（原 `!folder.isDefault` + toast），因为内容管理只在这个页面有入口；默认夹进来改显示常驻提示 `hintDefault`、隐藏改名/删除两块，且不再每次弹 toast。
- **批量删除内容**：管理页新增一行 → 打开 `FavoriteVideoListActivity` 并带 `selectMode=true`，内容页在首屏加载成功后若 `writable && 列表非空` 自动 `enterSelectionMode()`（空夹/只读夹不硬进；切排序不重复进；首屏失败不标记，重试仍可进）。返回时管理页 `setResult(RESULT_OK)` 让收藏夹列表重拉数量。
- **修掉一个真 bug（资源类型位）**：`resources` 的 `{id}:{type}` 里 `2`=视频稿件 / `12`=音频 / `21`=视频合集，原来 `buildResources()` 一律写 `:2`，音频与合集条目删的是错 id 语义。`VideoCard` 末尾追加 `favType`（Parcelable 只能末尾追加）、`getFolderVideosNew` 用 `normalizeFavType(media.optInt("type"))` 填、`buildResources` 按卡片类型拼串、新增 `deleteFavorite(aid, favType, mediaId)` 重载（旧两参委托它）。**没有引入 `batch-del`**：项目既定写操作全部收口 `x/v3/fav/resource/batch-deal`，两者能力重叠（`bilibili-api-notes.md` §6 已记录这条判断）。
- **清空失效内容**：`FavoriteApi.cleanInvalidResources(mediaId)` → `POST x/v3/fav/resource/clean`（form 只有 `media_id`+`csrf`，`mediaId<=0` 直接 `-400`）。成功 `data` 是 `0` 不是条数，所以只提示「清理完成」**不谎报清了几条**；快照里没有 `folder/clean-state`，因此不做「可清理数量」预检。
- **视觉统一**：`activity_favorite_folder_edit.xml` 整文件照 `activity_edit_user_info.xml` 的单卡片编辑页模板重写——12sp `?android:attr/textColorSecondary` 小标题、输入框裸 `@drawable/background_edittext`、保存行是卡片行 `textEnd`+`arrow_forward`、内容管理两行是「标题 + 描述 + 箭头」单行卡片（**不加图标**：`drawable/icon_*.xml` 硬编码浅色 `#ebe0e2`，亮色主题下对比度不足）、危险操作用 `@color/status_error`。卡片一律不写 `cardBackgroundColor`，交给主题的 `materialCardViewStyle`。
- **验证**：`./gradlew.bat :app:assembleDebug` 通过（两 flavor，2m11s）；`testStandardDebugUnitTest` + `testCoexistDebugUnitTest` 通过，`FavoriteApiTest` 20 例全绿（含新增 `normalizeFavType_*` / `buildResources_*` 4 例）。
- **设备侧（SDK AVD `wear5`，`sdk_gwear_x86_64` / Android 14 / 466×466 圆屏 / 320dpi，装 `coexistDebug` universal 包）**：经 DebugBridge 拉起 `FavoriteFolderEditActivity` 核对结构与文案——默认夹（extras `mediaId=1;isDefault=true;title=DemoFolder`）顶栏「收藏夹管理」、`hintDefault` 显示「默认收藏夹仅支持管理内容，不能改名或删除」、`groupInfo`/`groupDanger` 均不在 dump 里（已隐藏）、内容管理小标题 + 「批量删除内容 / 勾选要移除的内容后删除」「清空失效内容 / 清理已失效（被删除或不可见）的收藏」两行卡片；非默认夹（`mediaId=123456;isDefault=false`）`groupInfo` 可见、标题回填 `DemoFolder`、滚动后「保存」行与两行内容管理入口都在；点「批量删除内容」成功开出 `FavoriteVideoListActivity`，顶栏显示 `DemoFolder`（`name` extra 透传成功）。**没有登录态**，故列表拉不到、多选态与两个写操作的成功提示未验。另记一条环境结论：该 AVD 上**所有页面**（含未改动的 `SettingMainActivity`）都渲染成内容放大溢出、左侧与底部被裁的样子，所以只能用来核对结构与文本，**不能做像素级版式对照**；同时 `android_find_text` 在本机不可用（无 Tesseract），`uiautomator dump` 是唯一可靠的判读手段。
- **调试踩坑（对后续设备测试有用）**：PowerShell 5.1 往原生程序传参时会吃掉内嵌双引号，`adb shell 'am broadcast ... --es extras "a=1;b=2"'` 到设备端只剩 `a=1`（分号被设备 shell 当命令分隔符）。可靠写法是 `adb --% shell "am broadcast ... --es extras 'mediaId=1;isDefault=true'"`——`--%` 停止 PowerShell 解析、内层改用**单引号**给设备 shell。
- **遗留**：① 「批量删除内容」是否**直达多选态**、「清空失效内容」的确认框与「清理完成」提示要在登录态下再验（空列表/只读夹按设计不进多选）；② 真手表/圆屏上的实际观感未看，AVD 渲染本身不可信；③ 音频/合集卡片点击仍按视频处理（`aid` 里装 auid/合集 id 走详情/虚拟合集），是改动前既有行为，本次未扩大范围。

### 3.81 长按三倍速期间不再响应横滑调进度（26.10.30，用户报告）

- **缘起**：用户报告「长按触发三倍速时，仍然能触发滑动控制进度」。长按是「按住不放」的动作，手指在加速期间几乎必然有几十像素的横向漂移；旧逻辑把这段漂移当成滑动调进度，浮层跳变、松手还会 seek 到漂移后的位置——本来只想加速看一段，进度却被拽走。
- **根因**：`PlayerActivity.handleSwipeSeekTouch()` 的判据只有「设置开 + 已准备 + 非直播 + 播放器就绪 + 未缩放」（`PlayerSwipeSeekPolicy.isSeekAllowed`），**完全没看 `onLongClick`**；而 `handleSwipeSeekTouch()` 在两套 `layout_control` 触摸分支的 ACTION_MOVE 里都会被调用，所以加速期间横滑照样激活 `swipe_seek_overlay` 并改 `seekbar_progress`。
- **修法**：
  - `app/src/main/java/com/RobinNotBad/BiliClient/activity/player/PlayerGesturePolicies.kt`：`PlayerSwipeSeekPolicy.isSeekAllowed(...)` 增加第 7 个参数 `longPressBoost: Boolean`（**不设默认值**，逼调用点显式表态），判据追加 `&& !longPressBoost`；`handleSwipeSeekTouch()` 把 `onLongClick` 作为该参数传入，被拒后走原有的 `cancelSwipeSeekIfActive()`（浮层不出现、进度不动）。
  - `PlayerActivity`：新增 `private fun endLongPressBoost()` 收口「松手还原倍速」——复位 `onLongClick`、`ijkPlayer?.setSpeed(speed_values[seekbar_speed.progress])`、`danmakuManager?.setSpeed(...)`、恢复 `text_speed` 文案；两套触摸分支的 ACTION_UP 都改调它，并**同时接 ACTION_CANCEL**。
  - **ACTION_CANCEL 必须接**：这是本次翻出来的连带缺陷。改前 `onLongClick` 只在 ACTION_UP 复位，触摸被父容器拦截或失焦 CANCEL 后标志卡在 true，倍速停在 3 倍；一旦把 `onLongClick` 做成滑动门闸，同一个 bug 还会把「横滑调进度」永久锁死（这辈子不再恢复）。
  - 文案：设置项 `settingtext_settingterminalplayeractivity_005` 改为「在视频区域左右滑动快进/快退（未缩放视频、未长按加速时生效）」。
- **验证**：`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest` 通过（两 flavor，1m18s）；`testStandardDebugUnitTest` **99 suites / 1045 tests / 0 失败 0 错误 0 跳过**，`PlayerGesturePoliciesTest` 11 例（新增 `longPressBoost_blocksSwipeSeek`、`longPressRelease_restoresSwipeSeek`）。
  - 设备侧（SDK AVD `wear5`，`sdk_gwear_x86_64` / Android 14 / 466×466，装 `coexistDebug`；`player_scale` / `player_swipe_seek` / `player_longclick` 全开，走的是 `layout_control` 缩放触摸分支）：样本是 `screenrecord` 自录的 44 秒本地 mp4，经 DebugBridge 直接拉起 `PlayerActivity`（`extras url=<本地路径>`，不需要登录）。
    - **对照**：普通横滑（DOWN → 3 次 MOVE）→ dump 出现 `swipe_seek_overlay`、无 `longclick` 日志 → 调进度正常 ✔。
    - **修复后**：DOWN 后按住 1000ms（logcat 出现 `gesture>longclick_down`，三倍速已触发）再 MOVE 两次 → dump **无** `swipe_seek_overlay`、进度文本不动 ✔。
    - **松手恢复**：加速拖到 x=330 期间进度不变，UP 后 logcat `IJKMEDIA: Playback rate: 3.000000` → `Playback rate: 1.000000`，紧接着普通横滑 → 浮层出现（`00:24 / 00:44 (55%)`）✔。
    - **ACTION_CANCEL**：按住加速后发 `input motionevent CANCEL` → 倍速 3.0 → 1.0；CANCEL 之后再普通横滑 → 浮层出现（`00:37 / 00:44 (82%)`）→ `onLongClick` 没卡 true ✔。
- **调试踩坑（对后续播放器设备测试有用）**：① `adb shell input motionevent DOWN/MOVE/UP/CANCEL x y` 能跨进程拼出一条真实触摸序列，`input swipe` 做不到「按住不动再横移」这种时序；② `screenrecord` 录静止画面只产出 3~4 秒、几 KB 的短片，播放器立刻 `FFP_MSG_COMPLETED`、`isPlaying` 不成立就没法测长按，录的时候要同时在设备上制造画面变化（这次录出 44 秒 / 15MB），录完还要 `chmod 660`（属主是 shell，app 读不到）；③ CANCEL 之后前台偶尔被 Wear 系统 UI 抢走，dump 取不到播放器控件是**假阴性**，先用 `dumpsys window | Select-String mCurrentFocus` 确认焦点在 `PlayerActivity` 再判读。
- **遗留**：① 只实测了「开启视频可缩放」的触摸分支（默认设置）；未缩放分支的 UP/CANCEL 收尾代码同构，没单独跑；② 圆屏 AVD 的命中区与真手表手感不同，系统 long press 阈值在真机上的误触率仍待真机观察；③ 长按加速期间**双指缩放/拖动**仍被吞（既有行为，本次未扩大范围）。

### 3.82 亮色模式下按钮的深色描边（26.10.30，用户报告）

- **缘起**：用户贴两张截图——亮色（经典终端 / 纸质终端）视频详情页的「播放」按钮四周有一圈显眼的深灰框，暗色同一页面则是干净的无边框色块，要求把亮色这个描边修掉。第一轮删掉描边后用户又圈出「红线两边的差异依旧明显」，即描边没了、按钮四周仍有一圈比纸底亮的「光边」。
- **根因（两层，都得拆）**：
  - **描边**：不是 MaterialButton 默认样式，是 26.10.26 给亮色单独补的：`themes.xml` 的 `ButtonStyleTerminal` 写了 `strokeColor=@color/terminal_divider` + `strokeWidth=@dimen/terminal_btn_stroke_width`，而 `terminal_btn_stroke_width` 在 `values/dimens.xml` 是 `0dp`（暗色）、被 `values-notnight/dimens.xml` 覆盖成 `1dp`（亮色），`terminal_divider` 的亮色值又是 `#26000000`（15% 黑）——所以只有亮色长出深灰框。
  - **光边**：`ButtonStyleTerminal` 没压掉父样式的 `elevation`（MDC 按钮默认带阴影 + `stateListAnimator` 按下会抬升），而按钮底是 **80% 半透明** 的 `@color/terminal_card_bg`（`#CCFDF6EC`）——阴影从半透明底下面透出来，就在亮色纸底上沿按钮轮廓留了一圈「外深环 + 内侧渐亮」的脏边。暗色看不出，是因为黑底上的阴影本身就是黑的。**同一条坑玻璃主题早就踩过**（`themes_glass.xml:4` 的注释：「卡片 / 按钮 elevation 必须为 0：半透明底下面的阴影会透出来，形成脏边」）。
- **修法**（`app/src/main/res/values/themes.xml` 的 `ButtonStyleTerminal`）：
  - 删掉 `strokeColor` / `strokeWidth` 两项，连同只为它存在的两处 dimen（`values/dimens.xml` 的 `terminal_btn_stroke_width=0dp` 与注释、整份 `values-notnight/dimens.xml`）。
  - 补 `elevation=0dp` + `android:elevation=0dp` + `android:stateListAnimator=@null`（与 `ButtonStyleTerminal.Glass` 同款写法，保留 clickable / ripple，只去掉抬升与阴影）。
  - 亮暗两版回到同一套语义：无描边、无阴影、按钮底 = 卡片色 `@color/terminal_card_bg`，靠「底色 vs 页底」的差区分——亮色 `#CCFDF6EC` 压在 `#EBDFCF` 纸底上实测 `#F9F2E6`，与暗色 `#2A2A2A` 压在 `#000` 上的层级关系一致。
- **未动的**：`@color/terminal_divider` 两个值保留（改完已无引用，删色值要连带动 `tools/appearance/gen_light_mode.py` 的亮色色表，本次不扩大范围）；玻璃质感的 `ButtonStyleTerminal.Glass` 自带 overlay 描边（那是玻璃主题的设计），不受影响；其余六套主题的按钮走 `gradient_button_bg_*` 背景、本来就没写 stroke，也不受影响；卡片上的粉色 `actionCardStroke` 描边与本条无关（`CardStyleTerminal` 的 `cardElevation` 也没动）。
- **验证**：`./gradlew.bat :app:assembleDebug` 通过（两 flavor）；`:app:testStandardDebugUnitTest` + `:app:testCoexistDebugUnitTest` 通过。
  - 设备侧（emulator-5554 / SDK AVD `24031PN0DC` / Android 12，装 `coexistDebug` universal 包，经典终端主题）：亮色视频详情页（`--es bvid BV1jBpE6zEGD --el aid 0`，不加 `--el aid 0` 会走 aid=114514 报 `video object is null`）。
  - **只删描边那一版**：深灰框消失，但沿按钮上边缘扫出来是 `(235,223,207)` 纸底 → `(239,232,222)` 光边 → `(249,242,230)` 按钮底，正是用户圈出的「红线两边」；放大 3x 能看到边缘一圈渐变带。
  - **加 elevation=0 之后**：同一条扫描变成 `(235,223,207)` → `(249,242,230)` 直接跳变，中间那圈 10~14px 的光边带消失，放大后按钮就是一个干净的圆角色块；暗色同页仍是 `#2A2A2A` 色块、无描边无阴影（黑底上本来也看不见阴影）。
  - 测完把模拟器状态还原：`coexist` 的 `shared_prefs/default.xml` 用测前备份覆盖回去（6102 字节、md5 与备份一致、属主仍是 `u0_a52`），`standard` 里临时插进去的 `ui_color_mode=light` 键删掉。
- **遗留**：无。附带记两条：① 复现亮/暗要改 `shared_prefs/default.xml` 的 `ui_color_mode`（写回用 `cat` 覆写保持属主，`sed -i` 会留下 root 属主文件）；② 以后给**半透明底**的组件加 elevation/阴影一律按本条处理——亮色下会变成可见的脏边。

### 3.83 新增「举报评论」（26.10.27，用户要求；26.10.31 迁入可选私有功能）

- **现状**：这条功能**不在公开仓库**。实现与单测已整体搬进私有仓库，公开侧只剩 `util/OptionalFeatures` 的 `replyReportLabel` / `replyReport` 门面和 `ReplyAdapter.showManageMenu` 里的一个条件挂点。
- **公开侧行为**：私有注入不在场时面板里不出现「举报评论」，别人的评论长按仍是「只剩复制文字就直接进复制页」的老行为；`ManageAction.Kind` 为此保留了通用的 `EXTRA`（不复用 `COPY`，否则会误触上面那条直连捷径）。
- **验证**：公开配置（无 `private/`）与私有配置（有 `private/`）各跑过 `assembleDebug` 双 flavor 与两 flavor 单测，均 0 失败。细节见私有仓库 README。
- **遗留**：登录态下的成功提交（`code=0`）仍未验——模拟器没有可用登录态。

### 3.84 按线上崩溃报告修 7 类缺陷（26.10.28，用户要求，mapping 还原后定位）

- **缘起**：控制台积了 50 条待处理崩溃。第一轮只按「堆栈里未混淆的帧」推断，结论不可用；用户提示「编译一遍 release 就有 mapping」后重新做：`./gradlew.bat :app:minifyStandardReleaseWithR8 :app:minifyCoexistReleaseWithR8`（本机没有 `key.jks`，只跑 R8 阶段即可）产出 `app/build/outputs/mapping/{standard,coexist}Release/mapping.txt`，逐帧还原后 **50 条全部归类**，其中 7 类能落到代码。
- **还原的可信度校验**（重要，否则等于瞎猜）：崩溃文本自带的 `adapter:d1.r@…` → mapping 里 `d1.r = MenuSettingAdapter`；`UpdateActivity.O` 的相邻帧 `R0.v` → `UpdateActivity$$ExternalSyntheticLambda0`；页面轨迹与解出的类一致。**跨版本的报告只能解出方法名、行号不可用**（mapping 来自当前 `main`），所以 26.10.19/26.10.16 的那 3 条 NPE 与 3 条 Fragment 崩溃仍未定位。
- **改法（7 处 + 1 个新类 + 1 组单测）**：
  1. `adapter/MenuSettingAdapter.kt` `dragCallback.clearView` → `recyclerView.post { ensureStartValid() }`。ItemTouchHelper 是在 RecyclerView 的 layout/scroll 过程中回调 `clearView` 的，当场 `notifyItemChanged` 必然抛 `Cannot call this method while RecyclerView is computing a layout or scrolling`（**6 例**）。
  2. `activity/settings/UpdateActivity.kt` `startDownload()`：`updateConfig!!.downloadUrl!!` → 先取 `downloadUrl` 判 `isNullOrBlank`，为空提示新文案 `msg_update_no_download_url` 直接返回。根因是强制更新拦截把 SP 里可能为 null 的 `force_update_download_url`（`BiliTerminal.kt` 的 `getString(..., null)`）塞进 intent（**5 例**，NPE 消息为 `null`，Kotlin `!!` 的指纹）。
  3. `activity/video/ShortVideoPlayerActivity.kt` 封面加载套 `GlideUtil.canLoad(coverImage)`；`adapter/user/UserListAdapter.kt` 头像加载套 `GlideUtil.canLoad(holder.itemView)`。Glide 4 的 `with(Activity)`/`with(View)` 会在宿主销毁时抛 `You cannot start a load for a destroyed activity`（**4 例**能确认到这两个调用点）。**注意 `Glide.with(BiliTerminal.context!!)` 不受影响**——那是 `createConfigurationContext(applicationContext)`，走 application manager，不做销毁校验；Opus/私信那几处就是被 R8 lambda 合并误指的。
  4. `activity/video/ShortVideoFeedFragment.kt` 三条 `preloadManager` 回调（`onItemsLoaded` / `onLoadError` / `onRelatedLoaded`）开头加 `if (!isAdded) return@label`。预加载是后台线程拉、回主线程才回调，中间用户可能已退出（**4 例** `Fragment ... not attached to an activity`）。
  5. `activity/settings/setup/SetupUIActivity.kt` 的 `save()` 改走新纯函数 `util/UiScaleInput.kt`（`parseDpi` / `parsePadding`，`toFloatOrNull` + 原范围校验）。旧代码直接 `toFloat()`，线上两次 `For input string: "界面大小"` / `"确认设置"`——都是页面自己的文案，疑似被粘贴进框（**2 例**）。
  6. `activity/player/PlayerActivity.kt` `onDestroy` 里 `requestedOrientation = …` 包 `try/catch (IllegalStateException)`。Android 8.0 在非全屏（分屏/悬浮）下调它抛 「Only fullscreen activities can request orientation」，在 `onDestroy` 里会升级成 `Unable to destroy activity` 直接崩在退出路径（**4 例**，全部 sdk 26 / HONOR HWDUK）。
  7. 新增 `ui/widget/SafeViewPager.kt` 并让 3 个布局（`activity_simple_viewpager.xml` / `activity_emote.xml` / `activity_short_video_player.xml`）改用它：只吞 message 含 `pointerIndex` 的 `IllegalArgumentException`，其余照抛。老 ViewPager 的多指下标越界是已知库缺陷（**7 例**：4 例 `pointerIndex out of range` + 3 例 `invalid pointerIndex -1 for MotionEvent`，全部停在该类的 `onInterceptTouchEvent`）。
- **验证**：
  - `assembleDebug` 双 flavor 成功；两 flavor 单测各 **95 个测试类 / 1041 例 / 0 失败**（新增 `UiScaleInputTest` 9 例，钉住 `"界面大小"` / `"确认设置"` / 空串 / null / 越界 / 保留旧口径的负边距）。
  - 设备侧（MuMu Android 15 / coexist debug）：`SettingMenuActivity` 正常渲染；`VideoInfoActivity` 左右滑能在「简介 / 评论」间切换（走的正是新的 `SafeViewPager`）；`ShortVideoPlayerActivity` 打开并来回滑 3 轮，`logcat -b crash` **为空**。
  - **没能复现验证的部分（如实记录）**：①`clearView` 的崩溃依赖「拖拽结束恰好落在 smoothScroll 期间」，adb 手势造不出来，只改了 1 行 `post`；②`PlayerActivity` 的方向异常要设备处于分屏/悬浮窗口才触发，单窗口模拟器造不出；③DPI 框的中文输入无法用 `adb input text` 复现（走 KeyEvent，被 `DigitsKeyListener` 挡掉），改由 `UiScaleInputTest` 覆盖。
- **遗留**：①仍 6 条无法定位（3 条 26.10.16 的 NPE、3 条 26.10.19 的 Fragment not attached）——需要按对应版本再编一次 release 取 mapping；②`Inconsistency detected`（4 例）与第 1 条同源但无法定位到具体 notify 点；③全仓仍有 ~58 处裸 `Glide.with(...)`，建议后续统一收敛到 `GlideUtil.request*`（已自带 `canLoad`）或 `AccountSwitchActivity` 那种「一次取 RequestManager」的写法。
- **另一条结构性建议**：`mapping.txt` 现在是构建副产物，`build/` 一清就没了，下次再有人报崩溃又回到解不出来的状态。建议在 `build-release.yml` 里把它作为 artifact 按 `versionCode` 归档。

### 3.85 搜索彩蛋第 4 组换成「紫色空灵 + 纳西妲」专属彩蛋（26.10.30，用户要求）

- **缘起**：用户要求删掉搜索页第 4 组彩蛋（「8 个特定真人姓名」那一组），换成「关键词同时包含 `紫色空灵` 和 `纳西妲`」触发的新彩蛋，文案由用户本人提供。
- **改法**：
  - `app/src/main/java/com/RobinNotBad/BiliClient/activity/search/SearchActivity.kt`：删掉 `specialNamesList` 字段与 `searchKeyword()` 里的遍历分支；在同一位置（心理援助词表之后）新增 `if (keyword.contains("紫色空灵") && keyword.contains("纳西妲"))` → `MsgUtil.showText(UiText.of("特殊彩蛋"), getString(R.string.egg_zisekongling_nahida))`。**放在暖心词之后是故意的**：心理危机词的安全兜底优先于玩梗。判定用 `contains`，所以「紫色空灵 纳西妲」中间夹别的字也能命中。
  - `app/src/main/res/values/strings.xml`：删除 `egg_special_names`，新增 `egg_zisekongling_nahida`，13 行按用户原文逐行 `\n`，一字未改（含结尾「会见~」）。无触发方式说明行（用户没写，不与 3、4 组原条目统一）。
  - 文档：`docs/copy-inventory/07-eggs.md` 的搜索彩蛋表（触发代码行号同步到 `450-474`）与 `E-050` 条目、`docs/FEATURES.md:268` 的彩蛋描述。搜索彩蛋仍是 4 组，`README.md` 的 50 条合计不变。
- **验证**：
  - `./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest` 通过（两 flavor）；两 flavor 各 **104 suites / 1112 tests / 0 失败**。
  - 设备侧（emulator-5554 / Android 12 / `coexistDebug` universal 包）：经 DebugBridge `start SearchActivity --es extras 'keyword=紫色空灵 纳西妲;auto_search=true'` → 顶层 `ShowTextActivity`，截图与用户原文逐行一致，13 行一屏内放得下。
  - **回归**：`严炜` 不再弹彩蛋、正常留在 `SearchActivity` 拉真实搜索（旧组已删）；`Robin 纳西妲` 仍弹彩蛋 2、`抑郁` 仍弹暖心词；单搜 `紫色空灵` 不触发（正常搜索）。
  - 说明：DebugBridge 的 intent 入口与真实 UI 提交走的是同一个 `searchKeyword()`（`SearchActivity.kt:329-331` 调用点），故 intent 验证对 UI 路径等效。

### 3.86 扫码登录补齐健壮性：轮询分档 / 失败退避 / 状态码收敛 / web 落地修正（26.10.30，用户要求）

- **缘起**：用户要求「扫码登录体验补齐健壮性」。先做只读审计（对照官方 App 9.16.1 的逆向结论），查出 9 处问题，本轮修其中 7 处；`NetWorkUtil` 的重试/超时与 `AccountManager.saveCurrentAccount()` 的同步网络**按决策不动**。
- **改法（只动 `activity/settings/login/QRLoginFragment.kt` 与 `res/values/strings.xml`，`api/LoginApi.java` 无需改）**：

| 问题 | 改法 | 实测结论 |
| --- | --- | --- |
| 轮询固定 1s、无次数上限（旧 `:263`） | `PollCount` 分档：第 1–30 次 2s、31–60 次 4s、61 次起 6s；`MAX_POLL_COUNT = 90`，到顶即停并提示重取码。Timer 改成「每拍只排一发」（`scheduleNextPoll()`），才能变间隔 | ✔ 2s→4s 在设备上实测到切换（见验证第 3 条）；上限 90 仅代码复核 |
| 一次网络异常就永久停轮询（旧 `:253-261`） | 新增 `onPollFailure()`：连续失败 <3 次 → 提示「网络异常，正在重试……」并以 3s 退避续轮询；第 3 次才停、恢复可点、按模式给 `err_qr_*` 文案。**理由**：手机扫码后用户可能要隔几秒才点确认，一次抖动就让人重扫的代价远大于多轮询两下 | ✔ 实测：失败第 1、2 次各 3s 退避续轮询且显示「网络异常，正在重试……」，第 3 次才停（见验证第 4 条） |
| 未知状态码不停轮询（旧 `:432` / `:349-351`） | 两个 detect 的 `else` 分支：`stopPolling()` + `need_refresh=true` + 恢复二维码可点 + 新文案 `err_qr_unknown_code(%1$d)` | ⚠ 仅代码复核（服务端造不出未知码）；已知码 `86101`/`86039`/`86090` 的分支在设备上跑通 |
| 模式开关卡片可连点，连点两下出两套 Timer（旧 `:113-120` + `:238`） | `startLoginDetect()` 入口先 `stopPolling()`（幂等）；`refreshQrCode()` 期间 `loginModeSwitch.isEnabled=false`，出码/失败回调再恢复 | ✔ 实测：三连点只发出一次 `auth_code` 请求、TV 轮询单条流 2.07–2.12s（见验证第 5 条） |
| 页面切走/销毁只 cancel 不置标志位，正在跑的那一拍会把轮询续回来 | 新增 `pollStopped` 标志 + `stopPolling()`（`pollStopped=true` → `timer.cancel()` → `timer=null`）；`onDestroy` 与三个跳转卡片都不再裸调 `timer?.cancel()` | ✔ 代码复核：文件内只剩 `scheduleNextPoll()` 与 `stopPolling()` 两处 cancel |
| web 成功落地：`.toLong()` 可能崩、`access_key` 不清、`requestSSOs` 抛异常会把成功报成失败 | 新增 `handleWebLoginSuccess()`：cookie 改取 `NetWorkUtil.getCachedCookies()`；`DedeUserID` 用 `toLongOrNull()`，取不到给 `err_qr_no_mid` 而不是崩；`refresh_token` 用 `optString`；**显式 `putString(access_key, "")` 清残留 TV token**（否则 `SplashActivity.kt:396-408` 会走 APP token 刷新分支，web 会话拿不到 cookie 续期）；`requestSSOs()` 包 try 且移到导航之后 | ✔ 端到端实测通过（见验证第 2 条） |
| `refreshQrCode()` 在 IO 线程改 View（旧 `:180-181`） | 换图/置灰/清标志全部挪到 UI 线程，`CenterThreadPool.run{}` 里只留网络 | ✔ 代码复核 |
| 文案：重试/未知码/凭证不全都只有一句模糊提示 | 新增 `text_qr_retrying`、`err_qr_unknown_code`、`err_qr_no_mid`；`err_qr_no_info` 等处的 `%1$s` 兜空串，不再显示 "null" | ✔ 见截图 |

- **验证**：
  - `./gradlew.bat :app:assembleStandardDebug :app:testStandardDebugUnitTest` 通过，**104 suites / 1112 tests / 0 failures / 0 errors**（日志 `outputs/build_login2.log`；工作区同期的私有页测试也在其中，本轮改动前后都是 0 失败）；早前一轮（不含维护者新增测试）为 104 suites / 1076 tests，日志 `outputs/build_login.log`。另编 `assembleCoexistDebug` 供设备实测（代码同源）。
  - 设备侧（emulator-5554 / Wear OS 14 / `coexistDebug` universal 包）：web 模式出码正常（`outputs/smoke/qr_login_web_3.png`）；轮询 130s 窗口抓 38 拍（`outputs/smoke/poll_cadence_130s.log`）——前 27 拍间隔 2.07–2.27s，第 28 拍起 4.07–4.34s，与「第 30 次进 4s 档」的设计一致（抓包前已轮询约 9s，绝对计数≈30）。
  - 该窗口内二维码被外部扫走（`86090` → 2 秒后 `code:0`），**意外把 web 成功链路端到端跑通了**：`saveCookiesLocked` 落地 `SESSDATA/bili_jct/DedeUserID/DedeUserID__ckMd5/sid`，随后 `x/web-interface/card`、`space/notice`、`wbi/acc/info`，最后 GET `data.url`（`passport.biligame.com/.../crossDomain`）；落盘核对 `access_key` 为空串、`cookies` 466B、`csrf` 32B、`refresh_token` 32B、`mid` 已写、`setup=true`、`account_list` 已写；**无崩溃、无 ANR**。
  - **P2 断网实测（三个窗口）**：①连续失败到停——15:21:58.905 起连续 3 次失败（15:21:58.9 / 15:22:02.3 / 15:22:05.7，间隔 3.4s＝3s 退避＋约 0.45s 请求），第 3 次后 `MsgUtil` 打「网络错误(＃°Д°)」、文案回 `err_qr_no_info`，此后 40s 内没有任何 poll（`outputs/smoke/p2_retry_3.png`、`p2_stage3.log`）；②重试文案——离线 2.2s 时截图拍到「网络异常，正在重试……」（`outputs/smoke/p2_retrying_live.png`）；③短暂抖动自愈——失败 2 次后网络恢复，15:24:16.670 轮询成功回到 `86101` 并按 2s 档继续（`outputs/smoke/p2_transient_recovery.log`、`p2_recovered_transient.png`）。**首拍失败很快**：DNS 直接 `UnknownHostException`，`NetWorkUtil` 的内部 5 次重试每次约 0.1s，所以 3 次上限约 7s 就会触发。
  - **P4 连点实测**：在模式卡上 `input tap` 连点 3 下（间隔约 50–100ms）→ 只发出一次 `POST .../passport-tv-login/qrcode/auth_code`（15:24:41.990），没有第二次取码、也没有回切 web 分支；随后 TV 轮询单条流：42.589 / 44.655 / 46.731 / 48.848 / 50.923 / 52.984 / 55.044，间隔 2.07–2.12s、每拍只有一条 `tv_login_state`，说明只有一套 Timer（`outputs/smoke/p4_doubletap.log`、`p4_doubletap_2s.png`、`p4_doubletap_12s.png` 显示「（TV端模式）」）。
  - 环境事实（不是缺陷）：App 退到后台约 100s 后被系统 `ActivityManager: freezing <pid>` 冻结，轮询随之暂停——这是系统对 cached 进程的常规策略，不是 P2 退避在停轮询；回到前台后是否续跑未单独验证。
  - 另一个观察（**已修**）：停轮询后 `err_qr_no_info` 会把异常原文拼进 UI，截图里是「…取二维码信息，点击上…\nUnable to resolve host …」——`%1$s` 拿到的是 `e.message`。现在 `onPollFailure()` 达到上限后不再把 `e.message` 拼进 `scanStat`：web 侧 `IOException` → `err_qr_web_network`、其余 → `err_qr_web_api`（都是不带 `%1$s` 的专用文案），异常详情仍走 `MsgUtil.err` 的日志；`err_qr_no_info` 只留给取码阶段。
- **遗留**：①P2 的 3 次上限在「快速失败」网络下约 7s 就停，10 秒级的电梯/地铁断网仍要用户手点重试（这是拍板口径的必然结果，想更宽容只需调大 `MAX_POLL_FAIL`）；②未知状态码分支服务端造不出来，只有代码复核；③TV 模式只跑了轮询（`86039` 正常），**TV 成功落地（access_token/refresh_token 写盘）未实测**；④90 次上限（≈180s＋C 端耗时）没有跑满，按代码复核；⑤~~`err_qr_no_info` 会外露英文异常原文~~ 已在 26.10.31 补丁里改掉（web 侧换 `err_qr_web_network` / `err_qr_web_api` 两条专用文案，见上）；⑥`refreshQrCode()` 里 `CookiesApi.checkCookies()` 仍可能耗时数秒，取码阶段最坏仍会卡在「正在获取二维码」（`NetWorkUtil` 重试策略未动，属已知边界）。

### 3.87 账密 / 短信登录补存 App access_token（26.10.31，发版前补）

- **缘起 / 根因**：`activity/settings/login/PasswordLoginFragment.kt` 的 `handleLoginSuccess` 与 `SMSLoginFragment.kt` 的同类收尾，从登录响应 `data/token_info` 里只取了 `refresh_token` 写盘，把同一响应中的 `access_token` 丢掉了 → 走账密 / 短信登录后 `SharedPreferencesUtil.access_key` 一直是空串。而 `activity/SplashActivity.kt:396` 按「有 access_key = TV/APP 登录」分流去 `api/AppTokenRefreshApi.refreshAppToken()`（该接口同时续 access_token 与 cookies），为空就只能走 `checkCookieRefresh()`；`NetWorkUtil` 的 `autoAddAccessKey`、`ShortVideoFeedApi` 等依赖 App 令牌的接口也一直拿不到凭证。
- **改法（两处，各自的 `handleLoginSuccess` 内）**：写 `refresh_token` 之后补写 `access_key`——先取 `data.token_info.access_token`，为空再回落 `data.access_token`。取值口径与紧邻的 `refresh_token` 一致，**无条件写**：响应里真没有 access_token 时写入空串 = 显式清掉上一次登录（可能是另一个账号、另一套 appkey）残留的令牌，避免它和本次的 mid / cookies 错配；空 access_key 是个明确定义的状态（`SplashActivity.kt:396` 会走 web cookie 刷新分支，与 §3.86 的 web 登录清空口径一致）。cookie 刷新路径不动。
- **为什么不是兼容性风险**：账密 / 短信登录本身就是 App 接口，返回的 access_token 与 refresh_token 同源；`AccountManager.saveCurrentAccount()` 在这两行之后执行，所以多账号快照也会带上 access_key，切号不丢凭证。
- **验证**：`./gradlew.bat :app:assembleDebug` 通过（含私有功能源码注入）；`testStandardDebugUnitTest` + `testCoexistDebugUnitTest` 各 **1112 例 / 0 失败 / 0 错误**（12:25 实测）。**登录端到端未实测**（需真账号）：待验「账密 / 短信登录 → 重启后按 access_key 分流到 `AppTokenRefreshApi`、access_token 与 cookies 都能续期」。
- **台账口径**：本条只记公共侧改动；配套的私有功能改动按规矩只记在私有仓库，不写本文件。

### 3.88 倍速播放出现两路重叠音频、一路跟不上（26.10.31，用户报告）

- **现象 / 缘起**：用户在 26.10.31 正式版上反馈「倍速播放时音频流会变成两个（重叠），还有一个跟不上」；1.0x 正常。26.10.30 的 §4 五十九引入了 DASH 双流 + 外部音轨，倍速正是那时被顺手「同步」到音轨的。
- **根因（模拟器实测钉死，不是猜的）**：DASH 播放是「ijk 播纯视频流 + `android.media.MediaPlayer` 播纯音频流」。`IjkMediaPlayer.setSpeed(2.0)` 在**没有音轨的纯视频流**上实际不生效——实测 10.6s 墙钟视频位置只前进 10.6s（恒 1.0000x），而 `MediaPlayer.setPlaybackParams(2.0)` 生效 → 音频一路领先视频，`progressChange()` 的「800ms 基线偏差校正」每 ~1.3 秒就把音轨 seek 回去一次（在线 2x 实测 69.4s 内 **47 次**、每次回拉 700~1000ms）。用户听到的「两个重叠 + 一个跟不上」就是同一段音频被反复回拉/跳过。ijk 的变速靠音轨时钟 + `soundtouch` 驱动，纯视频流没有可缩放的音频时钟，**stock ijkplayer 在这类片源上倍速必然无效**（26.10.31 终版用内核补丁修掉，见下）。
- **中间版本（26.10.31 早间，已撤）**：先绕成「倍速时换自带音轨的合一 MP4」——新增 `player/PlayerSpeedStreamPolicy.kt`（7 例单测）+ `PlayerActivity.syncStreamForSpeed()`（挂在 `applySpeedFromSeekbar()` 末尾与滑条 `onStopTrackingTouch()`，`streamSyncPending` 防重入），`applyAudioSpeed()` 对外挂音轨恒设 1x，`applyQuality` 多一个 `fromSpeedSync` 参数，`switchQuality` 放开高清拦截。**放弃原因**：登录态 + 大会员实测 `fnval=1&qn=80` 仍回 `quality:64`（`日志 quality>合一 MP4 档位回填：80 -> 64`），720P 是合一 MP4 的硬天花板（`docs/bilibili-api-notes.md` §3.12），等于强制降清晰度；本地老双文件也仍不能变速。
- **终版改法：改 ijk 内核，让无音轨的纯视频流也真倍速**（用户指出「这个 so 库应该是有 GitHub 源码的」后转向）：
  - `ijkpatch/0001-ffplay-video-playback-rate.patch`：`compute_target_delay()` 里只在「无音轨 + 倍速 != 1」分支把帧间隔除以 `pf_playback_rate`（PTS/时钟不动，所以进度条、弹幕、`getCurrentPosition()`、seek 全部自动正确）；`ijkpatch/0002-ffmpeg-hevc-termios-b0.patch`：arm 上 NDK `<termios.h>` 把波特率常量定义成 `B0`，与 HEVC `TAB_MVF_PU(B0)` 撞名（`x ## v` 粘成 `x0000000`）导致 ffmpeg 编不过，`#undef B0` 即可。构建要点（NDK **r14b** 才有 `make-standalone-toolchain.sh`、`module-lite-hevc` + flac/vp8/vp9、`--disable-ffserver`/`--disable-vda` 已移除、`--disable-bsf=eac3_core`、改了 module 要 `make distclean` 否则 `reuse configure`、Windows 克隆的 CRLF 与符号链接退化）全记在 `ijkpatch/README.md`。
  - 三个 ABI（arm64-v8a / armeabi-v7a / x86）的 `libijk{ffmpeg,player,sdl}.so` 用补丁版重编并替换 `app/libs/`，随包发布。
  - 应用侧退回「倍速就是真倍速」：`applyAudioSpeed()` 恢复跟随倍速（`PlaybackParams().setSpeed(speed)`）；删除 `PlayerSpeedStreamPolicy` 及其单测、`syncStreamForSpeed()`、`applyQuality` 的 `fromSpeedSync`、`userQuality`/`streamSyncPending`、相关三条文案；保留 `PlayerApi.applyActualQuality()`（用响应 `data.quality` 回填 `playerData.qn`）；长按 3x 改为 `longPressBoostActive` + `effectiveSpeedValue()` + `boostLabel()`，与滑条走同一条下发链路（不再直接 `setSpeed(3f)`）。
  - `progressChange()` 重同步分支补 `audioResyncSuppressMs = nowElapsed + AUDIO_RESYNC_COOLDOWN_MS`：`MediaPlayer.seekTo` 是异步的，seek 未落地时 `currentPosition` 仍报旧值，会连着 seek 三次（1x 实测 3 连 seek，听感是音频连跳三下）。
- **验证**：① `:app:assembleDebug` + `:app:testStandardDebugUnitTest` + `:app:testCoexistDebugUnitTest` 全绿，两个 flavor 均 **104 suites / 1112 tests / 0 失败**；② 模拟器 `emulator-5554`（AVD `wear5` / Android 14 x86_64，装载补丁版 `x86_64` 库）：本地 DASH 双文件夹具 @2x → `本地进度` 每 15.1s 前进 30.3s = **2.00x**、`音频轨道重新同步` 仅起播对齐 1 次、`dumpsys media.audio_flinger` 同刻只有 1 条活跃 AudioTrack；@1x → resync 0 次、1.0x；在线 DASH 纯视频流（HTTP 夹具 @2x）→ 进度上报 15s→30s→45s 各 ≈7.5s = **2x**；合一 MP4 @2x 与补丁前一致 ≈2x（音频主时钟路径未受影响）。
- **遗留 / 验证边界**：① **arm 真机（手表）未实测**——模拟器只能载入测试专用、未提交的 `x86_64` 库；② 模拟器上「在线 DASH 双流 + 外挂音轨」没跑通（宿主 HTTP 夹具只能经 `adb reverse` 给 ijk 用，媒体服务进程连不上；`10.0.2.2` 被宿主防火墙挡住），该组合用本地双文件夹具等价验证；③ `fnval=1` 的合一 MP4 绝不能挂外部音轨（§7.4 B1）；④ DASH 流地址 ~120 分钟过期仍未处理（§7.35）。
- 文档同步：`docs/architecture-map.md` §7.57「倍速」几条已改写为终版口径（内核补丁 + 应用侧回退 + 冷却期），`docs/bilibili-api-notes.md` §3.12 补充了「登录态实测仍 `quality:64`」，构建与实测细节在 `ijkpatch/README.md`。

### 3.89 整体转向兄弟仓库的播放架构：普通档回合一 MP4、高档 DASH 统一时钟、`.so` 回退上游（26.10.31 末，用户要求）

- **缘起**：用户报告 1080P 适配后「音画不同步 / 音频轨不加载」，随后追加「听视频模式报 `-10000`」。第一版把在线 DASH 全部改成 ExoPlayer 统一时钟（见 §3.88 之后那一版 `e65cab7`），用户装包后反馈「**包体积增大很多且完全播放不了视频**」，并要求「完全参照 BiliTerminal-Revival 的方案，连 so 库也换成他的，弃用我们目前的方案」。
- **「完全播放不了」的根因**：不是 ExoPlayer 接入本身，而是 `app/libs/` 里那份**自编译的补丁版 `libijk*.so`**（§3.88 为了纯视频流真倍速打的内核补丁）。量化对比：补丁版三 ABI 合计 **23.04 MB**，上游原版 **17.48 MB**（`libijkffmpeg` arm64 7.22→5.29 MB、v7a 5.79→4.40 MB、x86 8.14→6.22 MB，`libijkplayer`/`libijksdl` 同样膨胀）。处理：`git checkout 686f937^ -- app/libs` 回退到上游预编译库（arm64 保留本项目自己的，三方库 v7a/x86 与兄弟仓库**字节数逐一相同**，即同一份上游库）。回退后高档位纯视频流改由 Exo 播，不再需要内核倍速补丁，`ijkpatch/` 与 `rebuild_all.sh` 保留但当前不参与构建。
- **新架构（照搬兄弟仓库）**：
  - `util/DeviceProfile.kt` + `util/DeviceProfilePolicy.kt`：`Tier{COMPAT, BALANCED, HIGH_END}` 按「手表形态 / SDK / memoryClass / 核数」判定，`DEVICE_PROFILE_OVERRIDE` 可覆盖；`maxQuality()` 1080P / 4K、`autoQuality()` 720P / 1080P；`useUnifiedDashPlayer()` **默认全设备 true**（用户拍板：手表也保留 Exo，不抄兄弟仓库「COMPAT 档禁用 Exo」那刀），`useDashDualPlayer()` 恒 false。`BiliTerminal.onCreate` 里 `DeviceProfile.init(this)`。
  - `player/PlaybackStrategyPolicy.kt`：`usesProgressivePlayback(qn)` 只对 qn∈{6,16,32,64} 为 true；`chooseSource(dashQn, progressiveQn, unifiedPlayerAvailable)` 在合一档位 ≥ DASH 档位时选 MUXED，被降档时 unified 可用就保留 DASH（**不把 1080P 静默降成 720P**）、不可用才退 MUXED（否则纯 DASH 无音轨 = 有画面没声音）；`fallbackQualities(qn)` 给出逐档降级序列。
  - `api/PlayerApi.java`：`getPlaybackVideo` / `getPlaybackBangumi` 改「先 DASH（fnval=16，拿完整档位清单 + 真实 `quality` + 音轨）→ 真实档位属于普通档时再补取合一 MP4 → `PlaybackStrategyPolicy` 决定用哪个」，`switchToProgressiveIfBetter()` 用 `DashSnapshot` 做快照回滚，**MUXED 时清 `dashData`/`audioUrl` 但用 `restoreQualityLists()` 保留 DASH 的清晰度清单**（合一接口按请求档位裁剪 `accept`，不还原面板会丢 1080P 档位）；`applyDashStreams` 回填服务端真实 `qn`；新增 `getProgressiveFallback(source, qn)` 供高档起播失败时同档降级。实测依据：真机账号下 `fnval=0`/`fnval=1` 一律 `quality=64`、`accept=[64,16]`，只有 `fnval=16` 才回 `quality=80`、`accept=[112,80,64,32,16]`——**合一 MP4 天花板就是 720P**。
  - `PlayerActivity.kt`：**删掉整个 DASH 双播放器路径**——`initRemoteAudioTrack()`、`retryRemoteAudioTrack()`、`onHighQualityAudioFailed()`、`dashAudioRetryCount`、`dashAudioFallbackInFlight`、`audioBackupUrls` 及两个重试常量全部移除；`initAudioTrack()` 遇到 `UnifiedDashPlayer` 直接返回（在线音轨由内核内部消费），只有**本地双文件**还保留外挂 `MediaPlayer` 与 250ms 漂移校正。高档起播失败改走 `onUnifiedPlayerFailed()` → `applyQuality(720, false)` 换合一 MP4；`applyQuality` 的档位请求用 `minOf(AccountStateStore.clampQuality(newQuality), DeviceProfile.maxQuality())` 夹紧。保留上一版新增的「起播健康检查 + 按版本自动回退 IJK」（`player_unified_blocked_version`）。
  - `app/build.gradle`：`exoplayer-core` 回到 **2.18.7**（与兄弟仓库一致；实测 2.19.1 对「起播 1 秒后永久 BUFFERING」无改善）。
- **验证**：`.\gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest` **BUILD SUCCESSFUL**；**109 suites / 1166 tests / 0 failures / 0 errors**（新增 `DeviceProfilePolicyTest` 17、`PlayerCompatibilityUtilTest` 7、`PlaybackStrategyPolicyTest` 11、`StreamUrlPolicyTest` 12、`DashDataCompatibleAudioTest` 7 全绿）。包体积：`app-standard-arm64-v8a-debug.apk` **19.71 MB**、`armeabi-v7a` 19.31 MB、`x86` 16.23 MB（对比补丁版 .so 时期的三 ABI universal 包 23.5 MB，`.so` 回退单项就省 ~5.5 MB 未压缩）。
- **遗留 / 验证边界**：① **真机最终验收由用户执行**（用户明确「真机验证我来」），模拟器侧只做到「装包可启动、进详情页无崩溃」；② MuMu 模拟器（127.0.0.1:7555，Android 12 x86_64）上 ExoPlayer 统一内核**必现**「READY 播约 1.1–1.4 秒后掉回 BUFFERING 永久卡死」，已排除 seek / 音频轨合并 / App 主动 pause / `changeVideoSize` / 同步异步 codec 适配器 / Exo 版本（2.18.7 与 2.19.1 表现相同），同机官方 B 站客户端正常、而我们**只听音频**时完全正常——所以这是该模拟器视频解码/Surface 链路与 Exo 的组合问题，`scheduleUnifiedHealthCheck()` 的自动回退即为它兜底；③ 回退后的 IJK 路径在该模拟器上也已退化（位置停在 seek 落点、日志出现 `FFP_MSG_COMPLETED`），无法在模拟器上完成端到端播放验收。
- 文档同步：`docs/architecture-map.md` §7.4 整节按新架构重写（总原则、分档、取流决策、高档内核、听视频、接入点、自动回退、取流配套、`.so` 回退），`docs/FEATURES.md` §3.6 清晰度与音画同步两条改口径。

### 3.90 播放器相关代码整体退回 26.10.27 发版状态（26.10.31 末，用户要求）

- **用户决定**：「把播放器全部的相关代码退回到 10.27 tag 发版时候的状态，其他的不变」，改完推送到 `main`。
- **退回范围**（现在这些文件/目录等同于 `git show 26.10.27:...`）：`activity/player/**`、`player/**`（保留项见下）、`api/PlayerApi.java`、`model/PlayerData.java`、`model/DashData.java`、`app/libs` 三 ABI 的 `libijk*.so`；新增于 10.27 之后的播放器实现整批删除（`player/ExoDashMediaPlayer.java`、`UnifiedDashPlayer.java`、`PlaybackStrategyPolicy.kt`、`StreamUrlPolicy.kt`、`util/DeviceProfile*.kt`、`util/PlayerCompatibilityUtil.kt` 及其单测），`ijkpatch/` 整目录删除，`app/build.gradle` 去掉 `exoplayer-core`，`BiliTerminal.kt` 去掉 `DeviceProfile.init(this)`。
- **保留项（不属于播放路径，别的功能依赖）**：`player/PlayerStreamPolicy.kt`、`player/QualityAccessPolicy.kt` 及两个单测——`util/AccountStateStore.kt`、`activity/settings/SettingQualityActivity.kt`、`adapter/QualitySelectorAdapter.kt` 仍用它做**清晰度面板的账号权限锁定**。`activity/video/JumpToPlayerActivity.kt` 的取流调用改回 10.27 的 `PlayerApi.getBangumi(playerData)` / `getVideo(playerData, download != 0)`，但保留 `AccountStateStore.clampQuality()` 的档位夹紧。
- **结果**：在线 1080P 及以上的播放器内切档、DASH 分离流、统一时钟内核、设备分档、CDN 备份地址、兼容音轨优选**全部不再存在**；播放形态回到「合一 MP4 单播放器 + 本地双文件外挂音轨」。
- **验证**：`:app:assembleDebug`（standard + coexist 两个 flavor）**BUILD SUCCESSFUL**；`:app:testStandardDebugUnitTest` **104 suites / 1110 tests / 0 失败 / 0 错误**。真机播放行为按 26.10.27 发版口径复验（用户自测）。
- 文档同步：`docs/architecture-map.md` §7.4 该节改写为「播放器已整体退回 10.27」的现状口径，`docs/FEATURES.md` §3.6 恢复 10.27 的清晰度/音画描述并删掉 1080P 播放与统一时钟两条。

### 3.91 菜单底部新增「退出应用」按钮（26.10.31）

- **缘起**：用户要求菜单里有「退出」按钮，按下后完全关闭应用。菜单一直是纯导航页，手表上没有任何彻底收摊的入口；`desc_back_disable` 的文案从老项目起就写着"菜单最下方的退出按钮"，这次把它补回来。
- **改法**：`MenuActivity` 在 btnList 循环后追加一枚固定的 MaterialButton（不进 `btnNames`——菜单项受「菜单设置」管控且点了要跳页，退出是常驻入口）。点击先 `finishAffinity()` 收掉任务栈，再 `android.os.Process.killProcess(Process.myPid())` 结束进程：线程池 / Glide / 播放器引擎没有统一停机口，光收 Activity 会留下空转的后台进程。写法与 `UpdateActivity.exitApp()` 一致，文案复用既有 `btn_exit_app`（"退出应用"）。
- **验证**：`assembleDebug` + 双 flavor 单测通过。**模拟器不可达**：本机此刻 MuMu 与常见 adb 端口（7555/16384/5555/21503/62001）connect 均被拒绝、`adb devices` 为空，按小改动口径（单文件 UI 逻辑、不碰播放器状态机与线程时序）以构建 + 单测 + 代码走查为准。
- **26.10.31 晚追加（用户要求）**：按钮文案由「退出应用」改为「退出」二字——新增专用字符串 `btn_exit`，`UpdateActivity` 强更弹窗的「退出应用」（`btn_exit_app`）不受影响。

### 3.92 媒体控件误标「下载」修复：会话挂 MediaStyle 通知（26.10.31）

- **缘起**：用户反馈开启「系统媒体控件」后，控件被识别成「下载」而非音乐播放。
- **根因**：播放页的 `MediaSession` 从未绑定过任何通知。系统给媒体控件归类时找不到会话专属的媒体通知，就按应用当时挂着的那条通知取渠道——正在下载的机器上挂的是「下载」渠道（ID 1027），控件于是被标成「下载」。
- **改法**（`PlayerActivity`）：
  - 新增独立渠道 `channel_media_control`（显示名「音乐播放」，IMPORTANCE_LOW，O+ 建、幂等），挂一条带 `Notification.MediaStyle` + 会话令牌的常驻通知（ID **1031**，1027/1028/1029/1030 已占用），分类再叠 `CATEGORY_TRANSPORT` 双保险。系统拿到明确的配对对象后按会话归类；Android 11+ 的媒体卡片本来也要求这条通知才显示会话。
  - 通知只在元数据变化时重发（onPrepared / 切 P / 换清晰度 / 出错重建都会走 `updateMediaSessionMetadata`）；播放中每秒一次的 `updateMediaSessionPlaybackState` 不碰通知，避免 Binder 风暴。进度由 PlaybackState 提供，通知上不放进度条。标题取 `videoTitle`（空回落"RE：哔哩终端正在播放"），小图标 `R.mipmap.icon`，content intent 复用 `PlaybackService` 的 REORDER_TO_FRONT 写法。
  - `onDestroy` 先撤通知再释放会话，不留点不动的僵尸卡片。整体走 LOLLIPOP 守卫，minSdk 19 安全。
- **验证**：`assembleDebug` + 双 flavor 单测通过；模拟器不可达（同 §3.91），会话↔通知配对效果需要真表确认，已在 §2.1 记为待办。

### 3.93 实验性「圆屏列表」效果（26.10.31）

- **缘起**：用户要求实验性设置里新增「圆屏列表」开关，开启后若处于圆屏模式，所有图形界面的滚动列表加圆屏滚动列表效果。
- **改法**：
  - 设置接线：`SettingsKeys.UI_ROUND_LIST_EFFECT`（`ui_round_list_effect`，默认关）→ `buildLabGroup()`「功能开关」分组的 switch → `SettingsIndex` 内联搜索条目（描述"圆屏模式的滚动列表曲率效果"）。生效条件是**本开关 + 圆屏模式**（`player_ui_round`）同时开启；在 `BaseActivity.onCreate` 读取，改后重进页面生效。
  - 新文件 `ui/appearance/RoundListEffect.kt`：全树扫描竖向滚动容器（`RecyclerView` / `ListView` / `ScrollView` / `NestedScrollView`；横向 LayoutManager、`ViewPager2` 及其内部 RV、不可竖滚的 LayoutManager 全部排除），把每个可见条目按其在屏幕上的纵向位置做**等比缩放 + 边缘淡出**——缩放取圆的弦长比 `chord=√(1−t²)`、下限 0.62（中心 1.0 → 屏缘 0.62），淡出取 `t³` 曲线（只在外四分之一段明显）。半径取短边一半；高度超过屏幕半径的条目（文章、大图这类整页内容）不参与。
  - 性能红线（写进了类注释）：只写 `scaleX/scaleY/alpha` 变换属性（setter 内部有等值短路），不 requestLayout、不加层；滚动回调里只做数学变换、全程零分配（坐标数组与扫描栈按窗口复用）；全树扫描只挂在 `OnGlobalLayoutListener`；开关关闭时 `install` 直接 return，一个监听都不挂。容器内不下钻（卡片里的横向封面轮播不被外层效果带动）。
- **验证**：`assembleDebug` + 双 flavor 单测通过；模拟器不可达（同 §3.91），视觉效果与圆屏真机手感待真表确认，已在 §2.1 记为待办。
- **26.10.31 晚追加（用户要求「明显一点、适配绝大多数界面」）**：① 曲线加强——缩放下限 0.62→**0.50**（边缘条目缩到一半）、淡出系数 0.65→**0.85**（边缘条目透明度降到 0.15，t³ 曲线保持中央四分之一段几乎不动）；② 放开 ViewPager2 页内竖向列表（搜索结果、教程翻页原先被整页跳过——翻页器内部 RV 是横向的，本来就被"不可竖滚"挡住，竖向页内列表的窗口坐标算法通用，删掉双保险判断即可）；③ 大行不再按"高于屏幕半径"整段跳过，改按"接近整屏高（≥窗口高）"跳过——466px 手表半径才 233px，最常见的视频卡（高 250~320px）原先全被跳过，效果等于没开。性能红线不变（仍是纯变换属性、零分配）。

### 3.94 性能审查④：绑定热路径微优化（26.10.31）

- **缘起**：用户反馈应用非常耗电，要求在不改变功能的前提下尽力优化。§3.75~§3.78 已处理表情并发、图片生命周期、请求宽度封顶，本轮把剩余静态审计结论收尾。
- **审计结论**（全量代码走查）：无空闲 / 后台耗电循环——播放循环已限流且受位置门控，`PlayerActivity` 的"实时人数"轮询（5s）默认关闭且离开页面即取消，`PlaybackService` 通知定时器仅后台播放时存在；短视频页的延迟渲染是一次性调度不是循环；表情预取有 10s 截止与 `clear` 配对，无泄漏。
- **改法**：`VideoCardHolder.getRequestOptions()` 此前每次绑定都 `new RequestOptions().transform(CenterCrop(), RoundedCorners(dp2px(5f)))`——快速滑动时每个条目分配一次，Glide 内部还会再拷贝一份。改为 companion 里 `by lazy` 缓存一份进程级单例：圆角换算用的 `ToolsUtil.dp2px` 读应用级 density（「设置Density」只改各 Activity 自己的 Resources），进程存活期内不变，缓存安全。行为完全不变（同样 CenterCrop + 5dp 圆角）。
- **验证**：`assembleDebug` + 双 flavor 单测通过（含此前所有单测 0 失败）。耗电的最终定量对比需要真表长时间观察，本次以消除已知浪费点为目标。

## 4. 历史章节索引

> 完整正文在 `docs/archive/review/fix-progress-history.md`；§3.0~§3.51 的状态快照（26.10.09~26.10.21）在 `docs/archive/review/fix-progress-snapshots-261009-261021.md`。**编号在历史上有重复**（`## 二十二` / `## 二十九` 各出现两次、`## 30.` 是阿拉伯数字写法，原为不打断既有交叉引用而保持不动），下表**以归档文件的物理行序为准**。
> §一~§三十 的「收尾状态」按归档正文与 §五 / §十 的登记推断：批次类记「已闭环」，正文明确登记遗留的记「部分遗留」，总览 / 明细 / 建议 / 验证记录类记「纯历史」。

| §编号 | 标题 | 一句话内容 | 收尾状态 |
| --- | --- | --- | --- |
| 一 | 修复总览 | 286 条基线（Critical 23 / High 52 / Medium 105 / Low 106）+ 2026-09-24 深度审查新增 严重 10 / 中等 13 组 / 轻微 11 | 纯历史 |
| 二 | 已修复问题明细 | 逐轮修复记录，逐条 file:line（第一~四轮表格为主） | 纯历史 |
| 三 | 审查前已修复（本次核查确认，无需改动） | 审查前已完成、本次核查确认无需改动的项 | 纯历史 |
| 四 | 构建验证状态 | 当时的构建 / 单测记录与结论 | 纯历史 |
| 五 | 待处理问题（按优先级） | 绝大多数已 `[x]`；仍未关闭的 A7 WebView 输入校验 / A8 教程键污染 / A9 硬编码专栏 id / E1 拆 PlayerActivity / 发布侧 APK 校验等见 §2.2 | 部分遗留 |
| 六 | 下一步建议 | 4 条早期建议，已过时 | 纯历史 |
| 七 | GitHub issue 修复轮次（2026-10-03） | issue #1 手表右滑返回与滑动调进度冲突；根因 `android:windowSwipeToDismiss` 在窗口层直接 finish + `setTheme(ColorScheme.themeResId(theme))` 覆盖 `Theme.NoSwipe*` | 已闭环 |
| 八 | 发版链路改造：Gitee 发行版同步 + 更新检查改读发行版（2026-10-03） | 发行版同步到 Gitee，客户端更新检查改读发行版而非 tag | 已闭环 |
| 九 | 高能进度条接口变更适配（2026-10-04） | pbp 接口变更后的适配 | 已闭环 |
| 十 | 26.10.04 逐条拍板后的待做队列 | A1~A12 / C6b / E1~E6 结论映射表 + 已作废项（D2 跑马灯、D3 圆屏、D5 候选词）+ 数字勘误 + 8 批落地顺序；仍开放的即 A7 / A8 / A9 / E1 | 部分遗留 |
| 十一 | 26.10.04 批次 1：A 组快修落地记录 | A 组快修逐条落地 | 已闭环 |
| 十二 | 26.10.04 批次 2：A1 csrf 实时化 + A10 评论点踩 | csrf 实时化与评论点踩 | 已闭环 |
| 十三 | 26.10.04 批次 3：性能参数接线 + 列表增量刷新越界 + 崩溃页独立进程 | 性能参数接线、增量刷新越界排查（判据进 `architecture-map.md` §7.9）、崩溃页独立进程 | 已闭环 |
| 十四 | 26.10.04 批次 4：SettingsKeys 收敛收尾 + 删假批量 API + 更新包校验 | E4 键名收敛（`SettingsKeysTest` 钉键名）+ 删假批量 API + 更新包校验（`ApkVerifier`） | 已闭环 |
| 十五 | 26.10.04 批次 5（1/4）：私信会话管理（C13） | 私信会话管理（置顶 / 删除 / 查看主页） | 已闭环 |
| 十六 | 26.10.04 批次 5（2/4）：私信发图（C12） | 私信发图；`size` 小 1024 倍缺陷（见 `architecture-map.md` §7.14） | 已闭环 |
| 十七 | 26.10.04 批次 5（3/4）：新消息通知栏通知（C14） | 新消息通知栏通知 | 已闭环 |
| 十八 | 26.10.04 批次 5（4/4）：追番更新提醒（C16） | 追番更新提醒 | 已闭环 |
| 十九 | 26.10.04 批次 6（1/8）：评论置顶 / 取消置顶（C3） | 评论置顶与取消置顶 | 已闭环 |
| 二十 | 26.10.04 批次 6（2/8）：评论楼中楼排序（C4） | 楼中楼排序开关（纯客户端 `Reply.sortReplies`）；后续由 §30. 重做成真双向排序 | 已闭环 |
| 二十一 | 26.10.04 批次 6（3/8）：带图评论的三个静默出错（C6b） | 带图评论三个静默出错；12066 未复现 | 部分遗留 |
| 二十二 | 26.10.04 批次 6（4/8）：动态编辑与管理菜单（C7） | 动态编辑与管理菜单（点击与长按弹同一个菜单） | 已闭环 |
| 二十三 | 26.10.04 批次 6（5/8）：动态置顶与取消置顶（C8） | 动态置顶与取消置顶 | 已闭环 |
| 二十四 | 26.10.04 批次 6（6/8）：动态定时发布（C9） | 动态定时发布 | 已闭环 |
| 二十五 | 26.10.04 批次 6（7/8）：话题广场与话题动态列表（C10） | 话题广场与话题下动态列表；广场仍单页不翻页 | 部分遗留 |
| 二十六 | 26.10.04 批次 6（8/8）：视频笔记查看（C27） | 视频笔记查看；「无法正常加载」根因未确证 | 部分遗留 |
| 二十七 | 26.10.04 批次 7（1/4）：稍后再看「未看完」分类（C18） | 稍后再看「未看完」分类；服务端 `viewed=2` 语义待真机 | 部分遗留 |
| 二十八 | 26.10.04 批次 7（2/4）：收藏夹排序与复制 / 移动（C19） | 收藏夹排序行与复制 / 移动 | 已闭环 |
| 二十九 | 26.10.04 批次 7（3/4）：收藏夹多选删除（C20） | 收藏夹多选删除 | 已闭环 |
| 三十 | 26.10.04 批次 7（4/4）：关注分组增删改（C21） | 关注分组增删改（系统分组不可编辑） | 已闭环 |
| 三十一 | 26.10.04 批次 8（1/2）：拆分 DownloadService（E2） | `service/download/` 分层（`DownloadPathSpec`/`DownloadProgressMath`/`DownloadProgressStore`/`DownloadRepository`/`DownloadNotifier`），对外 `@JvmStatic` API 一字未改；F4 漫画裁为不做；测试数 38 类 / 327 例 | 已闭环 |
| 三十二 | 26.10.05：弹窗（选择框）主题接入 + 终端列表收口 | 三个缺失主题属性、7 份 `ThemeOverlay.<X>.Dialog`、`dialog_background.xml`、`util/TerminalDialog.kt`（369 行四方法）、17 处调用点全迁 | 已闭环（当时的编译验证缺口由 §36.3 关闭；对比度未重测属遗留） |
| 二十二（第二处） | 26.10.04 批次 7（5/5）：置顶会话排序 + 收藏夹排序行 + 关注分组移动成员（C22） | `util/SessionSorter.kt`、`FavoriteVideoListActivity` 链路排查（代码无缺陷 + 诊断埋点）、`FollowApi.moveFollowTagUsers` + 长按移动成员 | 部分遗留（真机未验证） |
| 三十三 | 26.10.05：片头片尾跳过默认开启 + 新增「通知设置」大类 | `player/SkipOpEdPrefs.kt`、`GROUP_NOTIFY` 分组、`SettingsIndex` 条目 | 纯历史（后由 §四十三 整体撤回；「通知设置」部分仍生效） |
| 三十四 | 26.10.05：话题广场加载不出来 + `#话题#` 不解析（C23） | `TopicApi` 换 `pub/search`、三处富文本 switch 补 `RICH_TEXT_NODE_TYPE_TOPIC`、`StringUtil.TopicClickableSpan`、`LinkUrlUtil.parseTopicId` | 已闭环（广场单页不翻页属遗留） |
| 二十九（第二处） | 26.10.04 批次 6 复查（C27 视频笔记 + C6b 带图评论） | `NoteApi.failureText` 分级 + `BiliNote` 埋点、`img_size` 改 double / `buildPictures`、私信 `buildImageContentOfKb` | 部分遗留（笔记根因未确证、12066 未复现） |
| 30. | 长按冲突与楼中楼排序二次切换失效（task-4） | `Reply.sortReplies` 真双向排序 + `ctime`、`DynamicHolder.showManage()` 去重、`ReplyAdapter.canManage`、`cell_reply_list.xml` 兄弟节点分析 | 部分遗留（§30.5b 未决项、真机未验证） |
| 三十五 | 26.10.05：稍后再看「未看完」为空（C18 回归）+ 新增「清除所有已看完」（task-2） | 服务端 `viewed` 参数、`isUnfinished`/`filterUnfinished` 判据翻转、`history/toview/clear` + `clearWatched` | 部分遗留（服务端 `viewed=2` 语义待抓包确认） |
| 三十六 | 26.10.05 Lead 终验：全队冻结后的串行全量验证 + 本轮问题闭环对照 | 13 条用户报告逐条对照表、43 类 / 400 例全绿、`AlertDialog` 自检 0 命中、方法论 5 条 | 已闭环（关闭 §29.4/§30.4/§35.6 的构建缺口） |
| 三十七 | 26.10.05 批次 8：长按操作面板（复制收进弹窗）+ 顶部工具条滚动收回 | `util/LongPressPrefs.kt` + `long_press_panel_enable`、「复制文字」进面板、`setupAutoHideBars` / `expandAutoHideBars` / `util/view/ScrollRetractDecider.kt` | 部分遗留（真机手感未验证） |
| 三十八 | 26.10.05 线上 release 崩溃：动态详情页 NPE（用户提供混淆栈） | mapping retrace 定位 lambda、`Dynamic.ensureDetailFields()`、`diFragment.view!!` 改 `view?.let`、6 例新单测 | 部分遗留（未真机复现） |
| 三十九 | 26.10.05 第三批真机反馈：收藏夹顶部工具条三处 | `supportsChangeAnimations = false`、`manageBar` 只在多选态可见 + 长按菜单「多选」入口、`ScrollRetractDecider.action()` 展开条件收紧为「仅 `!canScrollUp`」、`suppressClickAfterLongPress` | 部分遗留（三处均未真机复现） |
| 四十 | Release 说明漏掉更新日志：根因与兜底（26.10.05 发版后） | `.github/scripts/extract_update_log.py`、`build-release.yml` fail-closed、AGENTS/SKILL 硬要求 | 部分遗留（26.10.05 Release 正文未人工补） |
| 四十一 | 26.10.09：自建反馈界面 + 自建公告源 + 匿名统计 / 崩溃自动上报（新功能） | `server/` 全栈 + `api/TerminalApi.java` + `util/TelemetryReporter.kt` / `CrashReporter.kt` / `CrashTrail.kt` + `activity/FeedbackActivity.kt` + 隐私文案改写 + §41.8 隐私再同意闸门（`PRIVACY_CONSENT_VERSION` / `PRIVACY_PROMPTED_VERSION` / `TerminalDialog.choice`） | 已闭环（明细回归清单待补）；正文见 `docs/archive/review/fix-progress-history.md` |
| 四十二 | 26.10.09：管理控制台 WebUI（遥测 / 反馈 / 崩溃 / 公告四合一）+ 服务端并发加固 | `server/console.html` 零依赖单文件、`run_in_threadpool` + `synchronous=NORMAL`、无状态签名 Cookie + 双 CSRF 防线、`console.zsapp.asia` vhost / 证书、`backup.sh` + 探活、selftest 39 项 | 部分遗留（无告警渠道、无 TOTP/IP 白名单、无浏览器回归） |
| 四十三 | 26.10.10 补丁：紧急撤回「自动跳过片头片尾」（2610102） | `SkipOpEdPrefs.FEATURE_ENABLED = false` + `isEnabled()`、四处落点、`SkipOpEdPrefsTest` +3 例、tag `26.10.10.1` | **26.10.22 已恢复**（`FEATURE_ENABLED = true`，口径改为默认关闭），详见 §3.3 恢复记录 |
| 四十四 | 26.10.11：播放器网络韧性（弱网卡死 / 直播线路 / 切后台位置错） | `PlayerActivity.kt` 加读超时与重连选项 + 停滞看门狗 + `onSaveInstanceState` + 重挂 Surface 改 seek `video_now`；新增 `util/live/LiveStreamSelector.kt`（+14 单测）与 `LiveInfoActivity.kt` 接线、`PlayerData.liveUrlCandidates`；删 `max_cached_duration` | 部分遗留（真机只跑到「位置未复现回跳」；看门狗未真机触发；P0-2 重取 URL、P1-3 后台保活未做） |
| 四十五 | 26.10.11：共存版 product flavor（普通版 + 共存版双包） | `standard` / `coexist` flavor、FileProvider authority 去硬编码、`app/src/coexist/res/mipmap-nodpi/icon.png` 图标覆盖（两版同名 RE:哔哩终端，靠图标区分）、`UpdateRelease.pickApkUrl(namePrefix)`（+4 单测）、`build-release.yml` 整理发行 APK 步骤 | 已闭环（真机双包共存 + 数据隔离 + 同名 + 图标已验；共存版自更新待下次发版） |
| 四十六 | 26.10.11：弹幕「一抖一抖」——时钟基准错位 | `player/DanmakuManager.kt` 的 `updateTimer` 不再 `timer.update(pos)`（删掉位置回调参数）；对齐改到主线程 `PlayerActivity.progressChange()` + `DanmakuSync` 判定 + `mDanmakuView.seekTo(video_now)`（`SEEK_POS` 会重设 `mTimeBase`）；`onPrepared` 起播锚定；短视频播放器同样处理 | 真机取证已完成（42 次/秒硬校正、偏差最大 199s）；修复后待用户实机确认 |
| 四十六 | 26.10.11：线上崩溃 `PlaybackService` 5 秒义务未兑现（`RemoteServiceException`） | 根因 = `onPause` 的 `startForegroundService` 与 `onResume`/`onDestroy` 的 `stopService` 竞态（低内存手表上服务创建慢，`stopService` 先到 → 义务作废 → 5 秒后崩主线程）+ `startUpdateTimer()` 误放 `onCreate`；修法 = `foregrounded`/`stopPending` 两标志 + `onCreate` 就 `enterForeground()` + 兜底通知 + 定时器移到进场之后 | 已过构建与单测（两 flavor 各 478 例全绿）；真机未复现 |
| 四十七 | 26.10.11：控制台补「反馈 / 崩溃」已处理开关，并关掉已解决项 | `server/rebiliterminal_api.py`（`_db_set_handled` + `POST /admin/api/{feedback,crash}/handled`；两个列表接口加 `handled` 与 `pending=1`）、`server/console.html`（默认只看未处理 + 状态标签 + 标记/重开按钮 + 待处理计数）、`server/selftest.py` +17 例、`server/README.md` 与 `docs/architecture-map.md` 同步 | 已闭环（服务器实测：56 项 selftest 全通过、curl 覆盖 403/415/400/404；crash #2「PlaybackService 5 秒义务」与 feedback #2 测试内容已标记；crash #3/#4/#5 `TimeUtil$cache` 未修故保留待处理） |
| 四十八 | 26.10.16：日志基建 + 崩溃 / 反馈附带日志 + 「我的反馈」页面 | `util/Logu.java` 内存环形缓冲（`RING_MAX_CHARS=65536`、只记 W/E/F、不落盘）、`util/LogSanitizer.kt`（键=值脱敏，复用 `NetWorkUtil` 两份键名清单）、`util/LogExporter.kt`（FileProvider 分享）、`util/CrashReporter.kt` 的 `mergeLog()`、`ErrorCatch.java` 的 `ring_log` Intent extra、`CatchActivity.kt` / `FeedbackActivity.kt` 勾选框、`TerminalApi` 的 `log` 参数与 `getMyFeedback()`、`activity/feedback/` 列表 + 详情、服务端 `feedback.log` 列（`SCHEMA_VERSION` 1→2）+ `/terminal/feedback/mine` + `/admin/api/{feedback,crash}/detail` + `log_len` | 部分遗留（服务端已上线；客户端已真机验证通过，仅日志本地导出、崩溃自动上报端到端、跨设备找回未覆盖，见 §3.20） |
| 四十九 | 26.10.16：反馈对话——用户可追加说明 | 服务端 `feedback_comment` 表（`SCHEMA_VERSION` 2→3，**v3 刻意无 DDL**）+ `POST /terminal/feedback/reply` + 归属判据 `_MINE_SQL` 与 `/feedback/mine` **共用同一份** + 追加即把 `handled` 打回 0（reopen 语义）+ `_db_feedback_thread` 合并两侧消息 + 管理台 `user_reply_count` 与对话抽屉；客户端 `TerminalDialog.input()`（+`Sheet.addInput`）、`FeedbackReply.author`、`submitFeedbackReply`（走新增 `postWithHeaders`）、`FeedbackDetailActivity` 时间线与「追加说明」入口、`FeedbackListActivity` 结果码重拉 | 部分遗留（跨设备 `X-Mid` 追加、发送失败重开预填未真机覆盖，见 §3.21） |
| 五十 | 26.10.19：线上崩溃两路清剿（#43–#49） | A 路：#43–#47/#49 无 message NPE = `UserDynamicFragment.continueLoading` 里 `adapter!!`（首屏加载失败 → adapter 从未创建 → 空列表拖动翻页）；三处 `runOnUiThread` 加 `isAdded && view != null` + `adapter?.`，`setRefreshing(false)` 进 `finally`，顺带修 `UserVideoFragment` 插入起点漏 `+1`。B 路：#48 `libijksdl.so` 加载失败 = `UnsatisfiedLinkError`（`Error`）冒到主线程 + `IjkMediaPlayer()` 构造会二次加载；新增 `player/IjkNativeLibs.kt`（失败锁死 + 诊断上传），`PlayerActivity` / `IjkPlayerBridge` / `VideoPlayerCore` / `ShortVideoPlayerActivity` 全部改判返回值并降级 | 部分遗留（构建与两 flavor 单测全绿，**未真机复现**；见 §3.29） |
| 五十一 | 26.10.24：对比审计后的第一批修复（清单见 `docs/review/compare-with-re-biliterminal-261024.md`） | P0-1 图片管线 Cookie 外发：新增 `util/ImageRequestPolicy.kt` + `helper/CustomGlideModule.kt` 应用层/网络层双过滤（站外去 Cookie/Referer/Origin）；P0-2 收藏夹编辑丢隐私/简介：新增 `FavoriteApi.getFolderInfo()` + `attr` bit0 解析，编辑页改为先回填再保存；P1-1 下载通知权限硬门禁 → `DownloadNotificationGate` 改为并行请求不阻断；P1-2 教程键名统一到 `TutorialStore`（`tutorial_ver_<id>`）+ 管理页重写；P1-3 动态「仅自己可见/关闭评论」接线；P1-4 发版 tag 校验（`build-release.yml` 增 `verify-version` job）；P1-5 下载服务 START_STICKY 重启续跑（`DownloadServiceRecovery`）；新增「无极调音」（`PlayerVolumePolicy` + 播放器滑条，短按音量键仍调系统音量）；`strings.xml` +34 条 | 部分遗留（报告 91 条中已修 7 条：P0 2/2、P1 5/20；其余 84 条未动。`assembleDebug` 双 flavor 成功；`testStandardDebugUnitTest` 83 suites / 901 tests / 0 failures、`testCoexistDebugUnitTest` 通过；**播放器/UI 时序改动未真机验证**；afdiancdn 去 Referer 是否 403 待真机） |

| 五十二 | 26.10.24：对比审计 §3.3 安全组五条 P2 加固 + P2-9 部分（清单见 `docs/review/compare-with-re-biliterminal-261024.md` §13.0 第二批） | 图片目录：默认改应用专属 + `PictureMediaStore.publishToGallery()` 发布进 `Pictures/哔哩终端`（API 29+ 走 MediaStore + `IS_PENDING`，旧系统 `scan`），`FileUtil.getConfiguredPictureDownloadPath()`/`PictureSavePolicy` 分档，manifest 注明 MANAGE 只服务用户自定义公共目录；Cookie：`CookieDomainPolicy` 严格域校验（equals/子域点后缀）+ 响应 host 校验（fail-closed）；直播弹幕：wss host 必须 B 站域、端口校验，握手只带 `SESSDATA`；深链：`DeepLinkPolicy` 限定 http/s 到 bilibili.com 子域与 b23.tv，删 `debug-host` 日志；极验：`CaptchaParamPolicy` 白名单 + `JSONObject.quote`；遥测：精确内存只报档位（`TerminalApi.ramLevel`），`device_key` 按产品需求**保留采集**（26.10.24 用户决定回退该子项） | `assembleDebug` 双 flavor 成功；单测 87 suites / 935 tests / 0 failures（新增 4 个测试类 + 重写 `TerminalApiTest`）；**图片发布/直播弹幕/深链/极验均未真机验证**（见 §2.1） |

| 五十三 | 26.10.25：短视频横滑「一顿一顿 / 跳页 / 卡中间 / 按住返回崩溃」整体修复 | 外壳从 `ViewPager2 + SwipePageLayout` 自绘 fakeDrag 换回经典 `ViewPager`（与普通视频详情页同款，原生滚动）：`activity_short_video_player.xml` 改 `androidx.viewpager.widget.ViewPager`，`ShortVideoShellAdapter` 改 `FragmentPagerAdapter`（`BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT`，页面壳实例稳定、`getItemPosition` 恒 `POSITION_UNCHANGED`，换视频只热更新内容不重建页面）；`SwipePageLayout` 重写为只保留「第 0 页右滑退出」边缘手势（不设 `edgeExitCheck` 则永不拦截，进度条/横向滚动控件按坐标避让）；滑去详情页时暂停信息流（`pauseForShellSwipe`/`resumeAfterShellSwipe`），滑回恢复；详情三页维持延迟合并渲染（`RENDER_DELAY_MS=180` / `STAGGER_MS=60`）；删除 `beginFakeDrag/endFakeDrag` 与返回键收口逻辑（`Cannot change current item when ViewPager2 is fake dragging` 崩溃路径随之消失） | 已闭环（`assembleDebug` / `assembleRelease` 均通过；真机 vivo V2229A 按住静止、左右滑一页一页、右滑退出、返回键均正常，26.10.25 用户复测确认「修好了」） |

| 五十四 | 26.10.25：短视频页开启「圆屏适配」后并未适配 | `activity/video/ShortVideoPlayerActivity.kt`：新增 `roundAdapted` / `roundPadding` / `contentWidth`-`contentHeight`；`PageHolder.applyRoundInsets()` 收放顶栏、进度条、时间文本、弹幕留白；`adjustVideoSize()` 增圆屏内接圆分支并按内容区居中 | 部分遗留（MuMu 320×320 模拟圆屏：开/关两态实测通过，`assembleDebug` + `testStandardDebugUnitTest` 通过；真手表未验证，见 §3.65） |

| 五十五 | 26.10.26：评论区「文A」翻译（gRPC `TranslateReply`） | 新增 `api/ReplyTranslateApi.java`（手工 protobuf 编解码 + 5 字节 gRPC 帧 + `x-bili-locale-bin` 目标语言头；`translateReplies`/`translateReply`/`encodeTranslateRequest`/`wrapGrpcFrame`/`parseTranslateResponse`/`buildLocaleHeader` 都是可单测的纯函数）、`res/drawable/icon_translate.xml`（从 B 站 9.16.1 反编译资源 `ic_translate_no_padding.xml` 提取的「文A」6 条 path，fill `#EBE0E2`）、`cell_reply_list.xml` 新增 `item_reply_translate` + `replyTranslation`、`ReplyAdapter.kt`（按钮绑定、已有译文切换、`notifyReplyChanged` 重绑整行）、`model/Reply.java`（`translatedMessage`/`translationShown` + `hasTranslation`/`toggleTranslation`/`applyTranslation`）、`strings.xml` 3 条、`ReplyTranslateApiTest`（21 例）+ `ReplyTranslationTest`（7 例）；文档 `FEATURES.md` §9.5/§9.6、`architecture-map.md` §7.55 与 API 表、`bilibili-api-notes.md` §7 | 部分遗留（`assembleDebug` 双 flavor 通过；`testStandardDebugUnitTest` / `testCoexistDebugUnitTest` 各 91 suites / 988 tests / 0 failures；模拟器 sdk_gwear_x86_64（Android 14，466×466）：视频 80433022 的详情页与列表页点「文A」都实测出现英文译文、再点收起，gRPC 请求 HTTP 200 / 634–643ms，列表 `position-1 → index` 映射正确；per-app locale 改 zh-CN 后同一条中文评论返回空 map、界面不显示译文也不卡死；**失败路径（12002 等业务错误）未上设备、真手表未验**；API 19/20 无 HTTP/2 ALPN 时按普通网络失败提示，见 `docs/bilibili-api-notes.md` §7） |

| 五十六 | 26.10.26：评论区翻译目标语言固定中文 + 入口暂时隐藏 | `api/ReplyTranslateApi.java`：新增 `UI_ENABLED = false`（带开关说明，恢复改成 `true`）与 `TARGET_LOCALE = Locale.SIMPLIFIED_CHINESE`，javadoc 从「跟随系统语言」改为「固定简体中文」；`adapter/ReplyAdapter.kt` 的 `bindTranslation` 开头按 `UI_ENABLED` 提前返回，隐藏 `item_reply_translate`/`replyTranslation` 并清掉点击监听（`buildLocaleHeader(TARGET_LOCALE)`，不再 `Locale.getDefault()`）；`ReplyTranslateApiTest` +1 例（目标语言固定中文断言，共 22 例）；文档 `FEATURES.md` §9.5/§9.6、`architecture-map.md` §7.55、`bilibili-api-notes.md` §7 标注「入口隐藏」 | 部分遗留（`assembleDebug` 双 flavor 通过；`testStandardDebugUnitTest` / `testCoexistDebugUnitTest` 各 91 suites / 989 tests / 0 failures；模拟器 sdk_gwear_x86_64（Android 14，466×466）装 coexist 包实测：视频 80433022 列表页评论行只剩「点赞/踩/回复」，uiautomator dump 列表页与详情页均无 `item_reply_translate`/`replyTranslation` 节点（此前列表页有「文A」）；目标语言为中文时同一条中文评论请求 gRPC 200、服务端返回空 map、界面不显示译文也不卡死；**外语评论→中文仍无样本可验**（上游扫描 25 个视频、多组关键词均未找到英文评论，这也是暂隐藏入口的原因）；本轮 `ReplyInfoActivity` 打开后 `getReply` 回调抛异常自动关闭，详情页未单独复测（与列表页共用 `bindTranslation`）；真手表未验） |

| 五十七 | 26.10.26：发版 26.10.26（2610260）与官网同步 | 版本号 `26.10.24`→`26.10.26`（versionCode `2610242`→`2610260`）、把 26.10.24 的 20 条日志归档进 `update_history_log`、`update_log_current` 按用户口径精简为 12 条；提交 `055eaad`（版本号 + 日志归档）+ `8388d56`（日志精简）并推送 `main` 与 tag `26.10.26`；CI「构建发行版并创建 Release」#15 全部步骤 success，GitHub Release 10 个资产（8 个 APK + `md5sums.txt` + `release-links.txt`，正文 12 条 + MD5 表 + `versionCode` 标记），Gitee 发行版已同步 26.10.26；站点仓库 `bili-terminal-site` 用 `tools/sync-release.py` 重新生成 `data/releases.json` / `index.html` / `changelog.html` 等静态页，提交 `bdc9e37` 推送 `main` | 部分遗留（**官网静态页仍未更新**：线上 `data/releases.json` 最新 tag 仍是 26.10.14、`sitemap.xml` lastmod 仍是 2026-10-14、`changelog.html` 仍写「当前最新版 26.10.14」，且 `rebiterminal.netlify.app` 已返回 404「site-not-found」（站点被改名或反代目标失效），而 `/api/release` 运行时接口已返回 26.10.26 → 判断 Netlify 自 26.10.14 之后没再部署该仓库（`26.10.19`、`26.10.24` 两次同步提交同样未上线），需要在 Netlify 后台检查站点是否仍绑定 `PurpleEthereality/BiliTerminal-RE_web` 的 `main` 分支，或手动触发一次 Deploy；站点仓库内容与本地生成结果均已就绪，恢复部署即自动生效） |

| 五十八 | 26.10.27：发版 26.10.27（2610270）与官网同步 | 版本号 `26.10.26`→`26.10.27`（versionCode `2610260`→`2610270`）、把 26.10.26 的 12 条日志归档进 `update_history_log` 的 `## 2026-10-26` 组、`update_log_current` 按用户口径改成 5 条（暗色 / 亮色 + 弥散光晕 + 玻璃质感、设置改版、搜索排序图标、字幕下沉、Android 7.1 及以下更新失败）；提交 `4cf93c6` 推送 `main` 与 tag `26.10.27`；CI「构建发行版并创建 Release」`40449814197` 全部 success，GitHub Release 10 个资产（8 个 APK + `md5sums.txt` + `release-links.txt`，正文 5 条 + MD5 表 + `versionCode` 标记），Gitee 发行版同步出 11 个资产（8 个 APK + `md5sums.txt` + 源码归档 zip/tar.gz）；站点仓库 `bili-terminal-site` 用 `tools/sync-release.py` 重新生成 `data/releases.json` / `index.html` / `changelog.html` 等静态页，提交 `ac21dde` 推送 `main`；构建过程中 `:app:minifyCoexistReleaseWithR8` 因本地 Gradle 构建缓存里的坏产物（`DynamicApi$UpInfo.class` 为 532 字节全零）失败，删坏 class + `:app:clean` + `--no-build-cache --no-configuration-cache` 重建成功 | 部分遗留（**官网静态页仍未更新**：`changelog.html` 仍写「当前最新版 26.10.14」，Netlify 依旧未从 `PurpleEthereality/BiliTerminal-RE_web` 的 `main` 重新部署，需在 Netlify 后台检查站点绑定或手动 Deploy；`/api/release` 运行时接口已返回 26.10.27 + 5 条日志，动态部分正常；站点仓库内容与本地生成结果均已就绪，恢复部署即自动生效。另：发版提交落地时本地 HEAD 已被人为切到 `refactor/layout-as-subpage`（非本代理所为），已用 `git branch -f main 4cf93c6` 把 `main` 快进并推送，该分支未删，等待用户决定） |

| 五十九 | 26.10.30：播放器内切不到 1080P（根因 + 修复） | 根因：播放链路全走 `fnval=1` 合一 MP4（清晰度天花板 720P，`qn=80` 被服务端降级成 `quality=64`），而 `accept_quality` 仍列 1080P → 界面能看到、切了没效果。修复：新增 `player/PlayerStreamPolicy.kt`（`QN_720P=64`、`requiresDash`、`isPlayableDash`、`isRemoteAudioTrack`，纯函数 + 6 例单测）；`api/PlayerApi.java` 新增 `getPlaybackVideo`/`getPlaybackBangumi`/`getBangumiDash`，`applyDashStreams`/`fillQualityLists` 抽出公共解析；`activity/player/PlayerActivity.kt` 新增 `initRemoteAudioTrack`（`MediaPlayer` + `setDataSource(Context, Uri, headers{Referer/UA/Cookie})` + `prepareAsync` 播 DASH 音轨）、`onHighQualityAudioFailed`（音轨失败一次性回退 720P）、`applyAudioSpeed`（`PlaybackParams` 同步倍速，API 23+）、`switchQuality` 拆出唯一落地路径 `applyQuality`；切P与互动跳转也改走 DASH 入口并带上新音轨。文档同步 `architecture-map.md` §7.57、`bilibili-api-notes.md` §3.12、`FEATURES.md`。**模拟器实测又抓出一个真 bug**：切 1080P 后远程音轨报 `INVALID_OPERATION`(-38) 被误判失败、自动踢回 720P——根因是 `progressChange` 对还没 `prepareAsync` 完成的 `MediaPlayer` 调 `seekTo`；修法是新增 `audioTrackPrepared` 守卫，并补 `retryRemoteAudioTrack`（600ms 重试一次）、`audioDriftBaseline`（只纠变大的漂移，不纠固定偏差）、回退时不覆盖清晰度列表（否则 1080P 入口消失） | 部分遗留（`assembleDebug` 双 flavor 通过；`testStandardDebugUnitTest` / `testCoexistDebugUnitTest` 通过，`PlayerStreamPolicyTest` 6 例全绿；**模拟器 emulator-5554 实测**：`play_qn=80` 起播走 `fnval=4048&qn=80`，播放器内切「高清 1080P60」→ `fnval=4048&qn=116` → `在线 DASH 音轨已就绪`（不再 -38），清晰度列表保持 DASH 档可再切回；但模拟器未登录，实际只拿到 DASH 的 720P 视频流，**1080P 画面本体仍需登录/大会员真机复核**；DASH 流地址约 120 分钟过期导致超长视频中途断流未处理；倍速同步在 API < 23 上无 `PlaybackParams`，仍只靠偏差校正） |
| 六十 | 26.10.30：清晰度按账号权限锁定 + 4K 接入 | 按用户要求把“能不能选”与“能不能播”分开：新增 `player/QualityAccessPolicy.kt`（`maxUnlockedQn`=未登录 720P / 非大会员 1080P / 大会员不限、`isLocked`、`needsLocked4kHint`，9 例单测）与 `util/AccountStateStore.kt`（`x/web-interface/nav` 的 `isLogin`/`vip.status` → SharedPreferences 缓存，`clampQuality` 把超权请求夹到权限内）。播放器清晰度面板：`QualitySelectorAdapter` 支持锁定项（复用 `icon_player_lock`、`alpha=0.55`、点击只提示），未登录锁 1080P 及以上、非大会员锁 1080P60/4K，4K 追加“需开通大会员”提示位（服务端不给无权限账号下发 120）。取流改为**一律先 DASH**（否则 `fnval=1` 会按档位裁剪清单，高档整体消失），请求 `qn` 固定 127（`QN_REQUEST_MAX`，只影响清单宽度）。`SettingQualityActivity` 增加 1080P60/4K 并**按权限隐藏**不可选档位。文档同步 `architecture-map.md` §7.57、`bilibili-api-notes.md` §3.12、`FEATURES.md` | 部分遗留（`assembleDebug` + 两 flavor 单测通过，`QualityAccessPolicyTest` 9 例全绿；**emulator-5554 实测**：匿名面板显示 1080P60/1080P/720P/480P/360P + 锁定「4K 超清」，点锁定项弹对应提示且不切换；设置页仅剩 360P/720P；切 480P 生效（`changeVideoSize video>852x480` + `在线 DASH 音轨已就绪`）。**4K 流本身未验证**：匿名请求 `qn=127&fnval=4048&fourk=1` 的响应只有 `[116,80,64,32,16]`，4K 档由大会员权限决定是否下发，需要大会员账号真机复验） |

| 六十一 | 26.10.31：发版 26.10.31（2610310）与官网同步（首次验证「私有仓库 + 公开仓库」发版链路） | 版本号 `26.10.27`→`26.10.31`（versionCode `2610270`→`2610310`）、把 26.10.27 的 5 条日志归档进 `update_history_log` 的 `## 2026-10-27` 组、`update_log_current` 按用户定稿写 10 条（用户草稿里的「扫一扫」「个人名片 / 视频链接分享」两条属私有功能，按 AGENTS.md 不外传，已从公开日志删掉）；提交 `e3c9378`（账密 / 短信登录补存 access_token，§3.87）+ `bea1fa5`（版本号 + 日志归档）+ `7cae4b2`（日志定稿）并推送 `main` 与 tag `26.10.31`；CI「构建发行版并创建 Release」run `41170272361` 全部 success（`verify-version` + `build-and-release` 7m33s）。**本轮首次真正验证私有注入**：发版前给公开仓库补了 Actions Secrets `PRIVATE_FEATURE_REPO` / `PRIVATE_FEATURE_TOKEN`（原本缺失，注入步骤只会走「按公开版构建」分支），先用 `workflow_dispatch -f build_only=true` 跑 run `41168852898` 预演（注入步骤输出「私有功能源码已注入」、单测 + `assembleRelease` 全绿、未建 Release 不占 tag），正式发版 CI 日志同样显示私有源码参与编译；出包 `app-arm64-v8a-release.apk`（7205288 字节，md5 `3b0b9c8f…`）反查：`classes.dex` 含私有类，`aapt2 dump xmltree` 显示四个私有 Activity 均注册且 `exported=false` / `screenOrientation` 竖屏锁定（旧的网页版 Activity 已不在清单里）。GitHub Release 10 个资产（8 个 APK + `md5sums.txt` + `release-links.txt`，正文 10 条 + MD5 表 + `versionCode` 标记），Gitee 发行版同步 11 个资产（8 个 APK + `md5sums.txt` + 源码归档 zip/tar.gz），下载 Gitee 的 arm64 包与 GitHub 同名包 **字节级一致**（7205288 / `3b0b9c8f…`）；relay 返回 `HTTP 202 {"ok":true,"queued":true,"tag":"26.10.31"}`，`/api/release` 已返回 `tag=26.10.31` + 10 条日志 | 部分遗留（**官网静态页仍未更新**，且这次比前两版更糟：站点仓库远端 `https://github.com/PurpleEthereality/BiliTerminal-RE_web.git` 现在返回 `Repository not found`（未认证 API 404），本地生成的同步提交 `a992a72` 推不上去——前三版是「推上去了但 Netlify 没部署」，这次是**仓库本身已不可访问**，需要用户确认站点仓库被删 / 转移 / 转私有，或提供新的部署方式；线上 `changelog.html` 静态快照仍停在 `v26.10.14`，动态接口 `/api/release` 正常，客户端更新检查与下载不受影响） |
| 六十二 | 26.10.31.1：倍速两路音频重叠的终版修复（ijkplayer 内核补丁）与补丁版重发 | 修复「倍速播放听到两路重叠音频、其中一路跟不上」：根因是 stock ijkplayer 的 `pf_playback_rate` 只作用在音频路径，DASH 纯视频流没有音频时钟 → 视频恒 1x、外挂音轨 2x，800ms 基线校正每 ~1.3s 回拉一次（实测 69.4s 内 47 次）。终版给 ijkplayer 打两笔内核补丁（`ijkpatch/0001` 让「无音轨 + 倍速」时按 `pf_playback_rate` 缩放帧间隔；`ijkpatch/0002` 修 NDK arm 的 `<termios.h>` `B0` 与 HEVC `TAB_MVF_PU(B0)` 撞名），用 WSL + NDK r14b 重编三 ABI 的 `libijk{ffmpeg,player,sdl}.so` 随包发布；应用侧撤掉中间版本"倍速换合一 MP4"绕行（`applyAudioSpeed` 恢复跟随倍速、长按三倍速走 `effectiveSpeedValue()`），并给 `progressChange` 的重同步补 `audioResyncSuppressMs` 冷却（seek 未完成时 `currentPosition` 还报旧值，会连 seek 三下）。实测：纯视频 DASH 2x 视频真 2.00x、本地双文件 2x `resync` 仅起播 1 次、同刻只有 1 条活跃 AudioTrack；单测 104 suites / 1112 tests。代价：`libijkffmpeg.so` arm64 5.29→7.22MB（module 带 294 个 filters）。发版 tag `26.10.31.1`（versionCode 2610311） | 部分遗留（代码/发版/Gitee 已闭环；**官网静态页仍未更新**——站点仓库远端 `https://github.com/PurpleEthereality/BiliTerminal-RE_web.git` 仍返回 `Repository not found`（未认证 API 404），本地同步提交 `a992a72`（26.10.31）与 `10a6e0b`（26.10.31.1）都推不上去，与 26.10.27 / 26.10.31 两次同因；线上 `changelog.html` 静态快照仍停在 `v26.10.14`，动态接口 `/api/release` 正常，客户端更新检查与下载不受影响。另：arm 真机未实测、模拟器无法验"在线 DASH 双流 + 外挂音轨"，见 §2.1） |

| 六十三 | 26.10.31 末：DASH 双流音画不同步 / 音频轨不加载 / 听视频 -10000 的根治（引入统一时钟内核） | 参考兄弟仓库 `BiliTerminal-Revival` 的 ExoPlayer 方案：新增 `player/UnifiedDashPlayer.java`（接口 `setPlaybackSpeed` / `setAudioSource` / `setAudioOnly`）与 `player/ExoDashMediaPlayer.java`（`extends AbstractMediaPlayer`，ExoPlayer **2.19.1**，`MergingMediaSource(true,true,video,audio)` 一条时钟同时消费视频+音频；`setAudioOnly(true)` 只建音频源，供听视频模式用），`app/build.gradle` 加 `com.google.android.exoplayer:exoplayer-core:2.19.1`；`PlayerActivity` 的播放器字段 `ijkPlayer:IjkMediaPlayer?` 抽象成 `playerCore:IMediaPlayer?`（112 处改名），`createPlayerOrBail()` 在「在线点播 && 非直播 && 有远程音轨」时返回统一内核、否则回退 `createIjkPlayerOrBail()`；`setIjkOption()` / `applyPlayerSpeed()` / `MPPrepare()` 按实例类型分支（`IMediaPlayer` 没有 `setSpeed` / `setDataSource(String,Map)`）；`initAudioTrack()` 对统一内核直接 return（否则叠加出第二路音频）；`onPrepared` 里起播后调用 `scheduleUnifiedHealthCheck()`（2.5s 取样 + 2s 复检，位置前进 <800ms 判定不兼容 → 记 `player_unified_blocked_version=BuildConfig.VERSION_NAME` + `retryAfterPlayerError()` 回退 IJK，同版本不再重试）。取流侧配套：新增 `player/StreamUrlPolicy.kt`（主机优先级选主地址 + 备份列表，12 例单测）、`DashData.getBestCompatibleAudioStream()`（只选 AAC/mp4a，绝不选 flac/dolby，7 例单测）、`PlayerData.videoBackupUrls/audioBackupUrls`、`PlayerApi` 的 WBI→legacy `x/player/playurl` 回退与 durl `backup_url` 解析、`jumpToPlayer` 下发 `video_backup_urls`/`audio_backup_urls`；`PlayerActivity.rotateVideoBackupUrl()` 在播放器报错重建时换下一个 CDN。文档同步 `architecture-map.md` §7.4、`FEATURES.md` §3.6 | 部分遗留（`assembleDebug` 双 flavor + `:app:testStandardDebugUnitTest` 通过，1131 用例全绿，含新增 `StreamUrlPolicyTest` 12 例、`DashDataCompatibleAudioTest` 7 例；**MuMu 模拟器（127.0.0.1:7555，x86_64 / Android 12）实测**：1080P DASH 双流能起播（`onTracksChanged: video/avc 1920x1080`、`ExoPlayerLib/2.19.1`），听视频模式（只看音频轨）能持续前进、AudioTrack 长期 `started`，证明统一内核与音频路径可用；但该模拟器上**只要视频轨参与，播约 1.1–1.4 秒后必掉回 BUFFERING 且永不恢复**（已排除 seek、合并音频、App pause、changeVideoSize、异步/同步 MediaCodec 适配器、2.18.7/2.19.1 版本差异；AudioTrack 在同一毫秒被置 paused，Exo 不报 error，播放线程空闲在 Looper），同一模拟器上官方 B 站 App 正常，判断是 MuMu 视频解码/Surface 链路问题；健康检查+回退链路已实测触发（日志 `统一时钟播放器在本机不可用（起播后播放位置仅前进 0ms）` → 重建 IJK → `在线 DASH 音轨已就绪`），但该模拟器随后 IJK 视频位置也不再前进（环境已劣化），**端到端「不卡 + 音画同步」仍需真机复验**；另外看到 `play_qn=80` 起播流仍可能被 `fnval=1` 路径回填成 720P（`applyActualQuality`），需真机确认详情页预取链路） |
| 六十四 | 26.11.01：发版 26.11.01（2611010，紧急回退版）与官网同步尝试 | 版本号 `26.10.31`→`26.11.01`（`versionCode 2610311`→`2611010`）、把 26.10.31.1 的两条日志（倍速两路重叠音频、扫码登录停轮询后外露英文异常）归档进 `update_history_log` 的 `## 2026-10-31` 组、`update_log_current` 按用户定稿写 5 条（播放器整体退回 26.10.27 稳定实现 / 菜单退出按钮 / 圆屏列表实验效果 / 媒体控件归类修复 / 列表封面请求复用）；提交 `d7d5abd` 推送 `main` 与 tag `26.11.01`；CI「构建发行版并创建 Release」run `41220070951` 全部 success（`verify-version` + `build-and-release`，含私有源码注入），GitHub Release `26.11.01` 10 个资产（8 个 APK + `md5sums.txt` + `release-links.txt`，正文 5 条 + MD5 表 + `versionCode` 标记），Gitee 发行版同步 11 个资产，`/api/release` 已返回 `tag=26.11.01` + 5 条日志；出包体积（release）：arm64 7212652 B / v7a 6765149 / x86 7316806 / universal 13104357，coexist 四包 7259921 / 6812411 / 7364074 / 13151631（对比 26.10.31.1 的补丁 .so 版已回落）；文档同步 `architecture-map.md` §7.4、`FEATURES.md` §3.6 与 §3.90 | 部分遗留（**官网静态页仍未更新**：线上 `data/releases.json` 最新条目仍是 `26.10.14`、`changelog.html` 仍写旧版本；本轮确认站点源码仓库 `https://github.com/PurpleEthereality/BiliTerminal-RE_web.git` 已 `Repository not found`（GitHub API `/repos/PurpleEthereality/BiliTerminal-RE_web` → 404，该账号仓库列表里也没有站点仓库），本地站点仓库 `bili-terminal-site` 的三个同步提交（`a992a72` 26.10.31、`10a6e0b` 26.10.31.1、`2f75bec` 26.11.01）都推不上去，且顺带修了 `tools/sync-release.py` 的发行日期口径（GitHub `published_at` 是 UTC，凌晨发版会写成前一天，已统一换算东八区）——需要用户决定重建该仓库并把三个提交推上去、或在 Netlify 里改接另一个仓库/账号；动态接口 `/api/release` 正常，客户端更新检查与下载不受影响。另：本次回退撤掉了 ijkplayer 内核补丁与 ExoPlayer 统一时钟，**播放器改动未在真机复验**（用户自行验证），DASH 1080P 等 26.10.30/31 的播放能力随回退一并移除，见 §3.90） |

## 5. 仍待回迁 / 仅见于归档的长期结论

> 下列结论目前**只存在于归档正文**，`docs/architecture-map.md` §7.29~§7.36 与 AGENTS.md 都还没有；要么回迁到 `architecture-map.md` §7，要么保持「归档可查」。已被 §7 / AGENTS / §6.5 覆盖的结论不再重复。

- **默认值必须收敛到唯一常量**：片头片尾跳过被用户误判为「压根没实现」，实际接线完整，唯一原因是**四处调用点各写一遍字面量 `false`**。漏掉一处就会出现「设置页显示开着、播放器却不跳」这种**半生效状态**（范式：`player/SkipOpEdPrefs.kt` 的 `DEFAULT_ENABLED`、`util/LongPressPrefs.kt`）。§三十三 / §37.1
- **改默认值天然满足「新装默认开 + 老用户尊重显式选择」，不需要任何迁移 / 重置代码**：`SharedPreferences.getBoolean(key, def)` 的语义就是「键不存在时才用 def」。既有同款范式：`util/SharedPreferencesUtil.java:132-144` 的 `loadMenuEnabled()` 用 `sharedPreferences.contains(key)` 区分。§三十三 / §37.1
- **改默认值 / 改键名是两种效果**：改键名等于把老用户设置读成「没设过」，默认值会把他关掉的开关重新打开 → 单测必须有「键名必须稳定」的护栏。§37.1
- **撤回一个已发布的特性，不能只改默认值**（见 §3.3 的 `FEATURE_ENABLED` 否决位）。§四十三
- **废弃接口的经典陷阱：`code=0` + `data=null` 造成静默空列表**：话题广场原走的 `app.bilibili.com/x/topic/web/dynamic/rcmd` **恒返回 `{"code":0,…, "data":null}`**（带 / 不带参数、游客 Cookie、伪装浏览器 headers、`page_size` 9/25/26 全部一样）。因为 `code` 是 0，`if (code != 0) throw` 永不触发 → 静默 `data == null → 返回空列表` → `showEmptyView()`。**按「网络异常 / 抛异常」方向排查全是死路**。替代源：`api.bilibili.com/x/topic/pub/search`（`keywords` 留空即按热度返回整页话题，稳定 20 条，匿名可用）。§三十四
- **单向排序缺陷的形态**：`model/Reply.java` 的 `sortReplies` 首行 `if (replies == null || sort != SORT_LIKE) return;` → `SORT_TIME`（= 0）直接 return、从不重排 → 「时间→热度→时间」往返后顺序**永久停在热度序**。这是**推翻一个旧的设计取舍**（归档正文 `fix-progress.md:1758`「时间序 = 不做任何本地重排」），不是修疏忽。修法用 `ctime`（服务端**恒返回**）显式升序，两个比较器 `LIKE_ORDER`/`TIME_ORDER` 都是 `private static final` 且各自 null 兜底，**两个分支都幂等**。**`pubTime` 是展示文案、不单调，不能做排序键。**§30.1
- **「排序前备份原列表、切回时还原」被否决**：`Reply` 实现 `Serializable` 且跨页传递，模型里藏 mutable 备份字段经翻页 / 点赞 / 删除后极易变脏。§30.1
- **`Reply` 只实现 `Serializable`、无任何 `Parcelable`**（`model/Reply.java:31`），评论相关 `putExtra` 只传标量 → 新增 `ctime` 无需同步 Parcelable（属**理由修正、结论不变**）。§30.1
- **同一手势承载两种语义 = 用户嘴里的「冲突」**：`util/StringUtil.java:170` 的 `setCopy(TextView, String)` 是业务代码自己注册的长按监听（`SharedPreferencesUtil.getBoolean("copy_enable", true)` + `startActivity(CopyTextActivity)`），不是 `LinkMovementMethod`/`textIsSelectable`。**同一个 TextView 只能有一个长按结果** → 修复方向是「按需让位」，不是去改复制本体。§30.2 / §37.1
- **`performLongClick()` 转发 click 会弹两次菜单**：长按手势结束后系统还会补发一次 click。动态侧原代码 `item_dynamic_delete!!.setOnClickListener { item_dynamic_delete!!.performLongClick() }` 就是真实缺陷。修法：**存普通 lambda 而不是 `View.OnLongClickListener`**，收敛到唯一真相源 `DynamicHolder.showManage()` + `MANAGE_DEDUP_MS = 400L` 去重；`showManage()` 在 `manageAction == null` 时返回 `false` → 不消费长按 → 自动回落到 `setCopy`。§30.2 / §37.1
- **`setOnTouchListener` + `ACTION_DOWN` 返回 `false` 也会有补发 click**（`VideoCardHolder.bindClick()`）：多选态下「长按先选上、抬手立刻取消 = 什么也选不中」，非多选态下「长按弹菜单、抬手又跳详情页」。修法：`suppressClickAfterLongPress` 标记，长按回调真执行就立起、随后那次点击自己吞掉，`bindClick()` 里随新绑定清零。§39.2
- **RecyclerView 的 item animator 会吃掉你的 alpha**：`DefaultItemAnimator` 对 `notifyItemChanged` 是另建 ViewHolder 交叉淡入（`supportsChangeAnimations = true` 时 `canReuseUpdatedViewHolder()` 返回 `false`），动画收尾把新布局 alpha 设成 `1f` → 「勾选（目标 1f）看不出问题、取消勾选（目标 0.45f）被静默改回 1f」。修法：`VideoCardAdapter.onAttachedToRecyclerView()` 里 `(recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false`。§39.1
- **`ScrollRetractDecider` 一条反直觉返回值必须保留**：已收着时**继续向下滚仍返回 `COLLAPSE`**，唯一作用是让调用方把累加值清零；返回 `NONE` 会让累加值一直涨，表现为「条收起来以后怎么滚都不回来」。§37.2 / §39.1（工具条的兄弟节点结构与「展开只允许在列表到顶」的口径已在 §7.28 / AGENTS）
- **「自然高度」必须缓存**：条本是 `wrap_content`，收回被压成 0，而 0 高度量不出「原本多高」→ 否则「收得回去、展不回来」；且「收回」只动当前真正可见的条。§37.2
- **`danger` 不要写死下标**：`setOf(2)` 在菜单插入新项后会漂移，改为 `setOf(actions.indexOfFirst { it.first == "取消收藏" })`。§39.1
- **`local` 判据的朝向决定用户看到什么**：稍后再看旧 `isUnfinished` 首行 `if (progress <= 0) return false;` 把「从未播放」当成「不是未看完」→ 整表都从未播放时「未看完」档**就是空的**（与用户现象逐字吻合）；且本工程**从未给 `history/toview/web` 带过 `viewed` 参数**，服务端能力从未被使用。正确：**主判据 = 服务端 `viewed`（0 全部 / 2 未看完）**，本地 `filterUnfinished` 退化为兜底（`duration <= 0` → 一律算「未看完」；只有 `progress >= duration && duration > 0` 才算「已看完」）。§三十五（§7.23 只覆盖了 `isUnfinished` 的判据本身）
- **`content` 长按点击的 span 陷阱**：`StringUtil.LinkClickableSpan` 的 `case TYPE_WEB_URL:` 取的是 `text`（span 显示文本）而**不是**第三个构造参数 `val`（`StringUtil.java:205` 能工作是因为那里 `text` 就是 URL）；且 `updateDrawState` **硬编码 `ds.setColor(Color.rgb(0x66,0xcc,0xff))`**，执行时会**覆盖 `ForegroundColorSpan`** → 话题必须新写 `StringUtil.TopicClickableSpan`（颜色取 `ColorScheme.INSTANCE.getPRIMARY()`，**不硬编码色值**），解析不出 `topic_id` 时退化为「只上色、不可点」。§三十四
- **R8 合并类的 mapping 不是一对一**：`E0.j` 表头写 `DynamicInfoActivity$$ExternalSyntheticLambda1`，但它同时有 `(CollectionInfoActivity, long)` 构造来源；`C0.c` 底下合并了 `VideoRcmdFragment`/`VideoInfoFragment`/`VideoInfoActivity`/`BangumiInfoFragment` 一串同类 SAM 包装。**单看表头类名定归属会错**，必须结合 `residualsignature` 注释与实际方法签名。§38.1
- **崩的是 release 包就必须单独验 release 链路**：`assembleRelease`（含 `minifyReleaseWithR8`）不能只跑 debug。§38.4
- **`app/build/outputs/mapping/release/mapping.txt` 不进版本库**，换机或 `clean` 后即失 → 线上混淆栈要趁 mapping 还在先 retrace。ProGuard retrace 能读 R8 写进 mapping 的 `# {"id":"sourceFile"}` 与 `residualsignature` 注释，**不需要额外装 R8 retrace**。§38.1
- **`Result.onSuccess` 自己已判空**（`if (isSuccess()) { T value = getOrNull(); if (value != null) accept(value); }`）→ **NPE 只可能在消费者 lambda 体内**，不在 `Result` 里，这是比读 mapping 表头更快的定位路径。§38.1
- **并发构建必须串行**：`app/build/` 共享，Gradle 对 `tmp/kotlin-classes`、`intermediates/javac` 先删再写，多人同跑必互踩并产生**假失败**。判据三选：① 复跑同一命令 ② `git status --short` 前后对比确认工作树静止 ③ 报错文件 `git diff` 与 HEAD 有实际差异；不全只能报「疑似在途中间态」。**类型不匹配 = 真错误；引用不存在的类 = 多为中间态。** §36.7
- **用例数一律以机器产出为准**：读 `app/build/test-results/testDebugUnitTest/*.xml` 的 `<testcase>` 计数；队友口述「23 例」实际 25 例（场景数口径与 `@Test` 数口径混用）。口述数字不进入正式文档。§36.7
- **正式文档只写正确结论**；唯一例外是「以错误理由做出的正确决定」，写成「**理由修正、结论不变**」并保留原错误理由，否则后人会重新推演出同一个错误理由。§36.7
- **JVM 单测里的 Android 桩陷阱（`§7` 只覆盖了「判据抽纯函数」这一半）**：`android.util.Pair` 这类**直接 `new` 出来的框架类**在单测下字段恒 null，只断言列表长度完全测不出。判据不是「类型名里有没有 `android`」，而是「**这个值是真实实现算出来的，还是桩给的默认值**」（自实现 Android 接口的 fake 如 `FakeSharedPreferences`、以及 `app/build.gradle` 单独补了真实实现的 `org.json` 都不踩坑）；**「测试文件里没有 `import android.*`」≠「该测试不依赖 Android 桩」**。修法：新建 `model/TopicNode.java` 取代 `Pair` + 回归守卫 `parseTopicNodes_returnsRealObjectsNotAndroidStubs`。§36.3 / §36.7
- **异常堆栈里没有被测方法的帧 → 失败在构造参数转型那一层**：`NoteApiTest.kt:198` 原写 `as String`，Kotlin 在构造器调用当成就抛 NPE，异常根本没进 `failureText`；改 `as String?` 后 null 才传进去（`NoteApi.failureText` 的 null 兜底 `NoteApi.java:237` 失败时就已存在、一行未改）。⚠️ 不要读成「产品代码曾有 null 返回缺陷」。§36.3
- **断言「某属性全工程缺失」必须同时 grep 相邻属性做对照组**：最初结论「弹窗层从未接入主题系统」被「7 族主题都声明了 `colorSurface`」与「XML 侧 71 处 `?attr/` 引用」直接推翻。§三十二（`§8.4` 只覆盖了主题层根因）
- **`Sheet.init` 必须清 window 背景**（`setBackgroundDrawable(TRANSPARENT)`），否则圆角被方角底衬盖掉。§三十二
- **`colorError` 的对比度未重测**：`docs/archive/visual-experience-report.md` §2.25（`#EBE0E2` 叠 `#FF6699` 约 2.1:1）仍待处理。§三十二
- **Python 服务的瓶颈不是 Python 而是 fsync**：单 uvicorn worker 处理「小 JSON + 一次小写入」天花板在几百 req/s；加固两件事：阻塞 sqlite 调用必须 `run_in_threadpool`（否则撞锁时 `busy_timeout=15000` 卡死整个事件循环、连 `/health` 都不响应）、`PRAGMA synchronous=NORMAL`（WAL 下默认 `FULL` = 每次 commit 都 fsync）。§42.2
- **带 `Secure` 的 Cookie 在 http 下不会被回发**：selftest 一开始用 `TestClient(api.app)` 默认 `http://testserver` → 「登录成功但后续全 403」；改成 `TestClient(api.app, base_url="https://console.test")` 才对。这个假失败恰好证明 `Secure` 确实生效。§42.3
- **nginx 签证书的坑**：证书还没签出来时 vhost 不能引用证书文件，得先用只 `listen 80` 的引导配置；引导配置若写了 `location / { return 404; }` 而**没给 `/.well-known/` 单独开口**，acme 的 http-01 会拿到 404（`Invalid response from …/.well-known/acme-challenge/…: 404`）。补 `location ^~ /.well-known/ { root /www/wwwroot/<域名>; allow all; }` 后签发成功。§42.6
- **WAL 模式下备份必须用 `sqlite3 .backup`，不能 `cp`**（最近写入还在 `-wal` 里，直接拷会丢数据）。§42.7
- **PowerShell 传 JSON 的坑**：`curl.exe --data-raw '{"title":"中文…"}'` 含中文时会被 Windows 控制台代码页搞坏 → 服务端 `json.loads` 失败 → `read_json` 返回 `{}` → 报「标题和正文都不能为空」。正确姿势：**把请求体写成无 BOM 的 UTF-8 文件，再用 `--data-binary @file`**。§42.8
- **`update_log_current` 是当版日志的单一真相**（关于页读它、`:app:verifyVersionConsistency` 拿它当锚点），不引入第二份需要维护的文案。§40.3
