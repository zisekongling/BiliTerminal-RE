# 测试版构建框架：构建开关 → probe 会话 → 通用上报

> **已拔出（2026-11-08）。** 这套框架只用过一轮：26.11.07 的「部分设备点播卡顿」专项，靠它抓到
> 8 份真机诊断报告、定位到「低端档默认软解」根因；问题在真机验证修复后，代码已按本文 §5.2
> 全部删掉（`util/TestBuild.kt`、`player/PlaybackDiag.kt`、`PlaybackDiagTest.kt`、
> `app/build.gradle` 的 `testBuildName` 段、详情页入口与播放页采集区、`Logu` 的诊断期放大、
> `ErrorCatch` 的带额度 `ringSnapshot`）。**本文是留档的复用配方**：下次再要「出个测试包让真机
> 把现场自己抓回来」，按 §4 的清单照抄重建即可，不用重新设计。

- **日期**：2026-11-07
- **状态**：已实施，随「部分设备点播卡顿」专项首用（首个 probe：诊断播放）。实现与验证记录见 `docs/review/fix-progress.md`
- **涉及文件**：
  - 框架本体：`app/src/main/java/com/RobinNotBad/BiliClient/util/TestBuild.kt`（新建）
  - 首个 probe：`app/src/main/java/com/RobinNotBad/BiliClient/player/PlaybackDiag.kt`（新建）
  - 构建开关：`app/build.gradle`（`testBuildName` + `BuildConfig.TEST_BUILD`）
  - 日志缓冲的诊断期放大：`app/src/main/java/com/RobinNotBad/BiliClient/util/Logu.java`，以及崩溃瞬间取快照的配套改动 `app/src/main/java/com/RobinNotBad/BiliClient/ErrorCatch.java`
  - 入口（详情页）：`app/src/main/res/layout/fragment_video_info.xml`、`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt`、`app/src/main/res/values/strings.xml`
  - 埋点（播放页）：`app/src/main/java/com/RobinNotBad/BiliClient/activity/player/PlayerActivity.kt`
  - 复用但不改动其契约：`util/CrashReporter.kt`、`util/CrashTrail.kt`、`util/LogSanitizer.kt`
- **给谁看**：下一次要「让真机把现场自己抓回来」的人。**新加一个测试版本不需要再设计一遍**，照 §4 的清单抄。

## 1. 目标

线上疑难问题的报告长这样：「有些设备放视频卡顿，上一版又没有」。这类描述里没有任何可定位的信息——设备档位、硬解还是软解、实际拉到哪条流、缓冲/掉帧发生在第几秒、弹幕灌了多少，这些数据散在几十处调用点，用户既看不到也念不出来，AI 也拿不到（用户在自己家里、用自己的账号）。

所以真正的需求不是「加一段日志」，而是：**给用户装一个特殊包，让他正常用一次，现场自己回来**。

这类需求会反复出现（卡顿、耗电、投屏、某个机型专属问题……），因此本轮把它做成**可复用的三层框架**，而不是每次临时埋一段代码：

1. **构建开关**：Gradle 属性决定「是不是测试版、是哪一个」；
2. **probe 会话**：每个专项一个 probe 文件，负责「抓什么、什么时候抓」；
3. **通用上报**：把任意诊断正文当成一条**可筛选的模拟崩溃**发到管理台崩溃表。

三条硬约束贯穿全文：

- **默认零影响**：不传 `-PtestBuild` 的普通包，行为与体积都不该有可观察差异（原理见 §5）；
- **可以一键拔出**：整套删掉只有三步，没有反向依赖（§5）；
- **只抓现场，不收隐私**：诊断正文里不许出现可冒用身份的凭证（§7）。

## 2. 三层结构与数据流

```text
                      gradlew … -PtestBuild=diag
                                │
        ┌───────────────────────┴────────────────────────┐
        │ ① 构建开关  app/build.gradle 的 testBuildName  │
        │    → BuildConfig.TEST_BUILD = "diag"           │
        │    → versionName 追加后缀 "-TEST-diag"         │
        └───────────────────────┬────────────────────────┘
                                │  const val TestBuild.NAME = BuildConfig.TEST_BUILD
                                ▼
                   TestBuild.enabled()   ← 编译期常量求值
                    │                │
              false│                │true
                    ▼                ▼
   普通包：入口 GONE、probe 全是    ② probe 会话（每个专项一个文件，现值 PlaybackDiag）
   空操作、R8 折叠掉整段分支         begin() / event() / metric() / finishAndUpload()
                                              │ 拼正文（指标 + 时间线 + 内存日志）→ capBody(190000)
                                              ▼
                              ③ TestBuild.uploadReport(context, "diag.PlaybackDiag", 摘要, 正文)
                                              │ 自动补前缀 com.RobinNotBad.BiliClient.testbuild.
                                              ▼
                                  CrashReporter.upload(…) → 管理台「崩溃」表
                                  exception  = com.RobinNotBad.BiliClient.testbuild.diag.PlaybackDiag
                                  message    = 诊断播放：<原因>
                                  thread     = testbuild
                                  stack      = 诊断正文（指标 + 时间线 + 内存日志）
                                  log        = CrashTrail 页面轨迹（正文里的日志不再重复塞）
                                  version_name = 26.11.07-TEST-diag
```

三层的分工是一句话：**开关只回答问题「是不是测试版」，probe 只知道「抓什么」，上报只管「怎么发出去」。** 三者互不知道对方的业务细节，所以任何一层换掉都不牵动另外两层。

### 2.1 为什么复用崩溃通道，而不是反馈通道

- 崩溃上报**不需要用户填表**：装包、用、结束，就这么简单；反馈页要人打字，真机用户不会为了帮开发者排查去写三百字；
- 崩溃上报自带**页面轨迹**（`CrashTrail.snapshot()`）、设备与内存档位字段，probe 不用自己攒环境信息；
- 管理台的列表、详情、批量标已处理、导出脚本**全部现成**，服务端零改动（`crash.log` 列上限 200000 字符，见 §6）。

代价是这些报告会混在真崩溃里。用**固定异常类名前缀**把它们分出来（§6），不要靠肉眼认。

## 3. 每层怎么用

### 3.1 构建开关（`util/TestBuild.kt` + `app/build.gradle`）

| 名字 | 类型 | 普通包 | 测试包 |
| --- | --- | --- | --- |
| `-PtestBuild=<name>` | Gradle 属性 | 不传 | 例 `-PtestBuild=diag` |
| `BuildConfig.TEST_BUILD` | `String` 编译期常量 | `""` | `"diag"` |
| `TestBuild.NAME` | `const val String` | `""` | `"diag"` |
| `TestBuild.enabled()` | `Boolean` | 恒 `false` | 恒 `true` |
| `versionName` | 字符串 | `26.11.07` | `26.11.07-TEST-diag` |

- **值就是变体名**，同一个名字同时出现在 `versionName` 与管理台的报告里，抓回来的数据因此能对上「用户装的是哪个测试包」。名字随便起（`-PtestBuild=1` 也行），只要能认出是哪一次。
- `BuildConfig.TEST_BUILD` 是**编译期常量**（`buildConfigField "String", …`，字面量），`TestBuild.NAME` 才能声明成 `const val`，`enabled()` 才能在编译期定值。**不要**把它改成读 `SharedPreferences` 之类的运行期开关——那样普通包里的分支就折不掉，§5 的「零影响」立刻失效。
- 三条容易写错的地方：
  - `buildConfigField "String", "TEST_BUILD"` **必须无条件定义**（不传属性时给空串 `""`）。若只在 `if (testBuildName)` 分支里定义，普通包的 `BuildConfig` 根本没有这个字段，`util/TestBuild.kt` 直接编译不过。
  - `versionName` 的后缀要加在 `defaultConfig` 上（`versionName += "-TEST-" + testBuildName`），这样两个 flavor、debug 与 release 全都会带；写进单个 buildType 会让另一半包认不出来。
  - `verifyVersionConsistency` 在测试版构建里会自动 `SKIPPED`（`onlyIf { !testBuildName }`）：它比对的是 `strings.xml` 里「【<正式版本号> 本次更新】」锚点，带 `-TEST-` 后缀必然对不上——那是**刻意不同**，不是版本漂移，别去改锚点。
- 名字里的反斜杠与双引号在 `build.gradle` 里已转义，否则生成的 `BuildConfig.java` 编不过。

### 3.2 probe 会话（以 `player/PlaybackDiag.kt` 为范本）

一个 probe 就是**一个有四个动作的 object**，不继承任何基类、不注册任何全局监听：

| 动作 | 职责 | `PlaybackDiag` 的实现要点 |
| --- | --- | --- |
| `begin(context, live, title)` | 开一个新会话：清缓冲、采基线、记起点 | `live = true` 直接返回（本次专项只查点播）；清 `events`/`metrics`，重置 `uploaded`；临时放大 `Logu` 环形缓冲；`recordDeviceBaseline(context)` |
| `event(kind, detail)` | 往时间线追加一行，带相对毫秒 `[+1234ms]` | 任意线程可调；会话未开始是空操作；上限 `MAX_EVENTS = 2000` |
| `metric(key, value)` | 记一条指标快照，**同 key 覆盖** | 任意线程可调；上限 `MAX_METRICS = 200`，超了从最早的 key 开始丢 |
| `finishAndUpload(context, reason)` | 结束会话并上报一次 | 单次门闸 `markUploaded()`；拼正文（失败也发一条「正文生成失败」）；`finally` 里还原日志缓冲上限；网络请求丢给 `CenterThreadPool.run { }` |

纪律（首用里逐条踩过，抄的时候别省）：

1. **只报一次**。「播完」与「用户随后退出播放页」会各调一次 `finishAndUpload`，没有单次门闸就会收到两条重复报告。门闸放在 probe 内部（`markUploaded()`），不要靠调用方自觉；调用方只需防重复收集（`PlayerActivity.diagReported`）。
2. **`event` / `metric` 只做内存操作**，绝不落盘、绝不发网络、绝不阻塞。它们在播放线程与 250ms 主循环上被高频调用，任何 IO 都会变成「诊断包自己更卡」。
3. **上报要自己起线程**。`TestBuild.uploadReport` 是**同步网络请求**，刻意不在工具类里偷偷起线程（耗时行为不该藏在 util 里），所以 probe 自己 `CenterThreadPool.run { }`。
4. **收尾顺序固定**：先把最后的事件与计数写进时间线/指标，再 `finishAndUpload`——它在内部同步取正文快照，之后再 `event` 就进不了这份报告。`PlaybackDiag.finishAndUpload` 里先拼正文、再在 `finally` 还原缓冲，顺序反了正文就吃不到放大后的日志。

**基线采集**（`recordDeviceBaseline`）是 probe 最值得复用的一块：时间线只回答「什么时候卡」，基线才回答「为什么是这台机器卡」。三条实现纪律：

- **不复制判定算法**：档位、性能分、硬件评分一律调 `PerformanceManager` / `DeviceProfile` 的现有 getter，本处只「读数 + 落键」。同一套阈值两份实现必然漂移，届时报告会与播放器实际行为对不上，比没数据更糟。
- **不加 `@Synchronized`**：它会读 sysfs / `ActivityManager` / `SharedPreferences`，可能在主线程被调用；持锁就会挡住播放线程上高频的 `event`。
- **每个键各自兜异常**：取不到写 `n/a`（`metricSafe` + `N_A`）。**缺键与 `n/a` 是两种信息**：前者说明采集代码漏了，后者说明这台机器真的取不到。

指标键按命名空间分组，读报告时能一眼定位：`device.*`（机型/内存/ABI/刷新率/评分分项）、`decode.*`（mediacodec / displayMode / audioOutput 及其来源）、`player.*`、`src.*`（aid/cid/分P/来源）、`stream.*`（host、清晰度）、`quality.*`、`play.*`（退出位置、已播时长、完成度、缓冲次数）、`danmaku.*`、`startup.*`、`app.*`。

**日志缓冲的临时放大**：默认环形缓冲 64K 字符对「放了十几秒就卡」的现场只剩最后几十行，连续性恰好被吃掉。所以 `begin` 里 `Logu.setRingMaxCharsTemp(Logu.RING_MAX_CHARS_DIAG)`（512K），`finishAndUpload` 的 `finally` 里 `Logu.restoreRingMaxChars()`。

> 为什么必须还原：64K 是崩溃上报的既有预算（快照经 Intent extra 跨进程传，撞 Binder 事务上限就 `TransactionTooLargeException`，崩溃页再也起不来）。所以它做成「临时 + 显式还原」而不是普通开关——普通开关就意味着「某处忘了关」会让此后每次真崩溃的上报都变危险。
>
> **配套改动（别漏）**：`ErrorCatch` 把快照塞进 Intent 的那一行改成带额度的 `Logu.ringSnapshot(Logu.RING_MAX_CHARS)`——万一真崩溃正好发生在诊断会话里（缓冲处于放大态），传过去也仍然是默认量级，不为测试版改动正式链路的崩溃上报行为。进程内消费（报告正文、导出）才用无参的 `ringSnapshot()`。

**单测逃生口**：纯 JVM 单测跑在 `standardDebug` 变体里，`TEST_BUILD` 是空串，`enabled()` 恒 false，门闸与正文根本走不到；而默认上报实现要碰 `CenterThreadPool`（静态初始化就 `new Handler(Looper.getMainLooper())`），单测里一加载就炸。于是 probe 开三个 `internal` 字段供单测改写：`enabledForTest`、`reportSinkForTest`、`armTtlMs`——**生产代码从不给它们赋值**。

安全阀：`enabled()` 的真实写法是 `TestBuild.enabled() || (BuildConfig.DEBUG && enabledForTest)`——逃生口**只在 debug 变体里生效**，release 包即使被误置也打不开诊断；`BuildConfig.DEBUG` 是常量，R8 还能把这段读取整个折掉。断言在 `app/src/test/java/com/RobinNotBad/BiliClient/player/PlaybackDiagTest.kt`（门闸、直播不采集、正文分段与截断、缓冲放大与还原、armed 过期）。

### 3.3 通用上报（`TestBuild.uploadReport`）

```kotlin
// @JvmStatic，Kotlin 侧同样要带 context
TestBuild.uploadReport(context: Context?, exception: String, summary: String, body: String): Int
```

| 参数 | 说明 |
| --- | --- |
| `context` | 取内存档位用，可空（取不到记 `UNKNOWN`） |
| `exception` | 报告类名。**不含**前缀时自动补 `com.RobinNotBad.BiliClient.testbuild.`；已带前缀则原样用 |
| `summary` | 进报告的 `message` 字段，人看的一句话（如「诊断播放：中途退出」） |
| `body` | 进报告的 `stack` 字段，任意长文本，先过 `TestBuild.capBody` |

行为约定：

- 上报体由 `CrashReporter.upload(...)` 拼装，额外带上 `CrashTrail.snapshot()` 的页面轨迹、`installId` / `deviceKey`、`versionCode`/`versionName`、SDK、品牌/机型、ABI、内存档位、是否共存版；`thread` 固定 `testbuild`。
- `ringLog` 传空串：**正文已自带内存日志**，再塞一份就是同一段文本出现两次，白占 `log` 的 200000 额度。
- 返回值是管理台给的编号（成功）/ `-1`（失败）；**任何异常都被吞掉、不往外抛**——诊断通道失败绝不能让用户看到崩溃。
- 正文截断在 `TestBuild.capBody` 里做，上限 `TestBuild.BODY_LIMIT_CHARS = 190_000`（给服务端 200000 留余量），超限时尾部追加 `…(正文超限已截断)`。

## 4. 怎么再加一个测试版本（照抄清单）

假设下一个专项叫 `battery`（耗电）：

1. **构建脚本：什么都不用改。** 直接 `./gradlew.bat :app:assembleCoexistRelease -PtestBuild=battery`——变体名就是那个 `battery`，`versionName` 自动变成 `26.11.07-TEST-battery`。
2. **新建一个 probe 文件**，例如 `app/src/main/java/com/RobinNotBad/BiliClient/player/BatteryDiag.kt`，按 §3.2 实现四个动作 + `enabled()` 转发 + `snapshotForTest()`。**照 `PlaybackDiag.kt` 抄骨架**（单次门闸、内存上限、基线采集纪律、正文三段、`CenterThreadPool`、日志缓冲放大与还原），只换采集点。
3. **埋点**：在真正要观测的调用点插 `BatteryDiag.event(...)` / `BatteryDiag.metric(...)`。这些调用**不需要**再包 `if (enabled())`（probe 内部首行就早退），但**不能**在参数里做有代价的工作（参数在任何情况下都会被求值）。
4. **入口用 `if (TestBuild.enabled())` 包住，UI 默认 `GONE`**。XML 里先写好 `android:visibility="gone"`，代码里只在 `enabled()` 时才改成 `VISIBLE` 并挂监听：

```kotlin
if (TestBuild.enabled()) {
    val diagBtn = rootview.findViewById<MaterialButton>(R.id.diag_battery)
    diagBtn.visibility = View.VISIBLE
    diagBtn.setOnClickListener { /* 打标记 → 走正常起播链路 */ }
}
```

5. **入口标记不要穿过正式链路**。详情页到播放页中间还隔着 `JumpToPlayerActivity`（多P 还要先经 `MultiPageActivity`），那边会**新建** Intent，extra 传不过去。与其在正式链路里留转发代码（那会让「删干净」变成难事），不如把状态留在 probe 里：`armNextPlayback()` 打一次性标记（带 TTL），播放页用 `consumeArmed()` 消费。`PlaybackDiag` 就是这套写法：`ARM_TTL_MS = 20_000L`，且**无论命中与否都清掉标记**（否则用户点了诊断播放又放弃，此后每次正常播放都会被当成抓现场）。
6. **上报**：`BatteryDiag.finishAndUpload(context, reason)` 里调

```kotlin
val body = TestBuild.capBody(report.toString())
CenterThreadPool.run {
    TestBuild.uploadReport(context, "battery.BatteryDiag", "耗电诊断：$reason", body)
}
```

7. **核对一遍**：`exception` 后缀与文件名/专项对得上（管理台全靠它筛）；`versionName` 带 `-TEST-battery`；正文里没有 §7 列的禁区字段。

**不需要做的事**：不需要在 `build.gradle` 注册名字、不需要建新 flavor、不需要在 `TestBuild.kt` 里改任何一行、不需要动服务端与管理台。这就是把它做成框架而不是一次性的原因。

## 5. 一键拔出

### 5.1 普通包为什么天然零影响

三段事实，都能在代码里直接读到：

1. `app/build.gradle` 里 `testBuildName` 取自 `-PtestBuild`，不传就是空串，`BuildConfig.TEST_BUILD` 因此是恒为空串的**编译期常量**；
2. `util/TestBuild.kt` 里 `const val NAME: String = BuildConfig.TEST_BUILD`，`enabled()` 只读 `NAME`——编译期常量求值，普通包里恒 `false`；
3. 各挂点的形状是 `if (TestBuild.enabled()) { … }`，条件是常量 `false`，release（`minifyEnabled true`，R8）把死分支消除；同时 UI 入口在 XML 里就是 `visibility="gone"`，即使某处没被消除也**不会显示给普通用户**。播放页的采集点也只多一次 boolean 读（`diagActive`）。

于是「忘了关」这件事不存在：不传属性就是关，不存在「某个开关忘了关，诊断入口漏到正式版」的事故形态。这与「改完即生效」的运行期开关（`BiliTerminal.applyLogSwitches()`）刻意相反——诊断入口一旦运行期可控，就有被误开或被抓包打开的风险。

### 5.2 彻底删掉这套东西（三步，全是删除）

1. 删 probe 文件（有一个删一个）：`app/src/main/java/com/RobinNotBad/BiliClient/player/PlaybackDiag.kt`，以及 `app/src/main/java/com/RobinNotBad/BiliClient/testbuild/` 若存在则整个目录；
2. 删各挂点里的 `if (TestBuild.enabled()) { … }` 块：详情页入口（`fragment_video_info.xml` 的 `diag_play` + `VideoInfoFragment` 里那段 + `strings.xml` 的 `text_diag_play` / `a11y_diag_play`）、播放页的采集区（`PlayerActivity` 里 `diag*` 字段与 `initPlaybackDiag` / `diagRecordStaticMetrics` / `diagReport` 等方法组）；
3. 删 `app/build.gradle` 里 `testBuildName` 相关的行，最后删 `app/src/main/java/com/RobinNotBad/BiliClient/util/TestBuild.kt`。

**顺带需要决定的两处**（不删也能编过，但不是「彻底拔出」）：

- `util/Logu.java` 里为诊断会话加的临时放大那一组：`RING_MAX_CHARS_DIAG`、`ringMaxChars`、`trimRingLocked`、`setRingMaxCharsTemp`、`restoreRingMaxChars`、`ringMaxChars()`、`ringSnapshot(int)`。删掉 probe 后 `setRingMaxCharsTemp` / `restoreRingMaxChars` 没有调用方，成为死代码；
- `ErrorCatch` 里 `ringSnapshot(Logu.RING_MAX_CHARS)` 这个**带额度的重载**要一并处置：若 `ringSnapshot(int)` 被删，它得改回无参版本；若保留（更稳妥，它本身是个合理的防御），就留着——两种都能编过，但**不能事后才发现漏改**。

### 5.3 `ErrorCatch` 那处改动不破坏「普通包零影响」

它改的是正式链路的代码，所以单独把口径钉清楚：

- **普通包里没有任何地方调 `Logu.setRingMaxCharsTemp`**（唯一调用方是 probe 的 `begin`，而它第一行就在 `enabled()` 早退）。因此普通包的环缓冲上限恒为 `RING_MAX_CHARS = 65536`，`ringChars <= ringMaxChars` 这条不变式一直成立；
- 于是 `ringSnapshot(65536)` 与原来的无参 `ringSnapshot()` **输出完全一致**——截断只在 `full.length() > maxChars` 时发生，而「超过默认上限」的缓冲在原逻辑里不可能出现。这是等价的替换，不是行为改变；
- 它只在诊断会话把上限放大到 512K 时起作用，作用对象是**真崩溃时崩溃页能不能起来**：崩溃快照要经 Intent extra 跨进程传（Binder 事务上限约 1MB），512K 字符 ≈ 1MB UTF-16 直接传会抛 `TransactionTooLargeException`，被 `ErrorCatch` 外层 `catch` 吞掉的结果就是「崩了但崩溃页起不来」。

所以准确表述是：**改的是正式链路的代码，但普通包行为零差异，而且它防的是一个由此前不存在的放大能力引入的新风险**——不是「顺手改了崩溃链路」。

没有反向依赖：probe 只依赖 `TestBuild`，`TestBuild` 只依赖 `BuildConfig` + `CrashReporter`（既有设施）。删完跑一次 `./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest` 即可确认。

## 6. 管理台怎么认

报告落在**崩溃表**（不是反馈表），用崩溃列表/自带搜索按字段筛：

| 字段 | 测试版报告长什么样 | 怎么用 |
| --- | --- | --- |
| `exception` | `com.RobinNotBad.BiliClient.testbuild.diag.PlaybackDiag` | **唯一的分类依据**。前缀 `com.RobinNotBad.BiliClient.testbuild.` 之后的剩余部分就是 probe 名，`diag.*` 是点播专项 |
| `exception_retraced` | 同 `exception`（这些类名本来就可读，解混淆无事可做） | 不用看 |
| `version_name` | `26.11.07-TEST-diag` | 筛「哪个测试包」；普通用户永远不会出现这个后缀 |
| `thread` | `testbuild` | 抓到的真崩溃里不会有这个线程名，可作旁证 |
| `message` | 如「诊断播放：中途退出」/「诊断播放：播放完成」 | 判断用户是**播完**还是**中途退出**（卡顿用户大多中途退出） |
| `stack` | 诊断正文：`=== 诊断播放报告 ===` / `=== 指标 ===` / `=== 时间线 ===` / `=== 内存日志…` | **主战场**，现场都在这里 |
| `log` | 只有 `CrashTrail` 的页面轨迹 | 了解用户从哪个页面进来的 |
| 机型 / 内存档位 / ABI | 由崩溃通道自动带 | 分档对比「卡的是不是低端表」 |

长度上限：服务端 `crash.log` 列 **200000 字符**；代码在 `TestBuild.BODY_LIMIT_CHARS = 190_000` 处截断正文并留标记，余量给页面轨迹。所以**一份报告不会写不进去**，但也别指望日志无限长——正文被 `…(正文超限已截断)` 收尾就说明该收窄指标/时间线，而不是抬高上限。

收尾：读完的报告用批量标已处理处理掉，别让它和真崩溃混在一起攒着。真崩溃的待处理数才是要盯的指标。

## 7. 隐私红线

诊断正文会经过第三方服务（本项目自建的管理台）落到库里，因此**按「要外传」的标准写**：

1. **不许带可冒用身份的凭证**：`SESSDATA`、`access_key`、`access_token`、`csrf`、`buvid3`/`buvid4`、Cookie 串、手机号。
2. **不许带完整 URL 的查询串**：要写请求地址就只写 `host + path`，或用 `NetWorkUtil.maskUrl` / `LogSanitizer.sanitize` 打码后再写。查询串里最常夹着的就是上面那些凭证。首用里播放页的做法是 `diagHostOf(url)`——**只记主机名**，直链的签名/时效 token 一律不进报告。
3. **复用既有脱敏，不要另写一套**：`util/LogSanitizer.kt` 的 `LogSanitizer.sanitize(text)` 已覆盖 `键=值`、`键%3D值`、JSON `"键":"值"`、裸 11 位手机号四种形态，键清单复用 `NetWorkUtil.SENSITIVE_QUERY_KEYS`。内存日志在**进入环形缓冲之前**就已由 `util/Logu.java` 的 `toFile` 脱敏（先脱敏、再 `shorten()`，顺序不能反），所以 `Logu.ringSnapshot()` 出来的是已脱敏文本——**probe 不需要再脱敏一遍，但也不许绕过 `Logu` 自己拼日志进正文**。
4. **别在正文里重复身份**：`installId`、`deviceKey` 由 `CrashReporter` 自己拼进上报体，`mid` 只在用户开了「附上 mid」时才带；probe 的 `metric` 里**不要**再写 uid / mid / 用户名。
5. **只作内部分析，不原样外传**：报告正文（含日志尾部）不进公开仓库、不进 issue/PR/群聊/文档/截图。要引用就描述结论，不要贴原文。
6. **测试包例外：精确内存容量（已知偏差，仅限测试包）**。`device.ramTotalMB` / `device.ramAvailMB` 报的是**精确容量**，这类精确硬件值在正式包里是明确不报的（对比审计 P2-9：精确字节数等于给机型再加一枚指纹，大众上报只报 `TerminalApi.ramLevel` 出来的档位）。测试包里保留它，是因为本轮要复核的正是**档位误判**——`PerformanceManager.getHardwareScore()` 的 RAM 分档（0–40 分）按 `totalMem` 计算，没有具体容量就无法判断「这台机器被判低档是不是判错了」，报告也就只能看不能改。同类的精确硬件事实（如 `device.hardwareScore.cpu0MaxFreqMHz`）同理。
   - 这条是**用户拍板的已知偏差**，边界写死：**只在按需发放、定向回收的测试包里出现**；正式包的既有口径（只报档位、不报精确字节）不变；数据**只作内部分析**，同样受第 5 条约束不外传。
   - 写新 probe 时别把它当先例：**除了已拍板的这几项，其余精确容量/精确字节一律不许进正文**；将来测试包若扩大发放范围，这条要重审。

判据很简单：**这段正文如果被公布，会不会有人因此能登进别人账号？** 会，就不许写。

## 8. 首个 probe：诊断播放（`player/PlaybackDiag.kt`）

| 项 | 内容 |
| --- | --- |
| 入口 | 详情页「播放」**正上方**一个默认 `GONE` 的按钮 `diag_play`，只在 `TestBuild.enabled()` 时显示；点击走 `startPlay(diag = true)`：跳过「首次播放」教程（否则那次点击被提示吞掉，测试同学会以为按钮坏了）、在所有早退判断之后打 `armNextPlayback()` 标记，再走**原样**的起播链路 |
| 播放页识别 | `PlayerActivity.initPlaybackDiag()`：`PlaybackDiag.enabled()` + 非直播时才看两个来源——Intent extra `PlaybackDiag.EXTRA_DIAG`（直接构造播放页的调试入口）与 `consumeArmed()`（穿透正式链路）。两支都要**求值**，别用 `\|\|` 短路掉消费 |
| 生效范围 | **仅点播**。`begin(live = true)` 直接返回；播放页遇到直播也直接返回（且**不消费** armed 标记，免得把标记吃掉） |
| 上报时机 | 播完（`REASON_COMPLETED`，`onCompletion` 侧）与中途退出（`REASON_EXITED`，`onDestroy` 且不是配置变更时）；`markUploaded()` 保证只发一条 |
| 基线 | `device.*`（机型/board/hardware/SDK/ABI/屏幕/刷新率/内存容量与评分分项；精确容量为何允许写、边界在哪见 §7 第 6 条）、`device.perfLevel*` / `perfTier*`、`decode.*`（含「用户设置 vs 默认硬解」的来源标记；26.11.08 起档位不再影响解码方式，见 `docs/review/fix-progress.md` §1 那条卡顿回归）、`player.*` |
| 时间线 | 上限 2000 行，`kind` 常量：`start` / `first_frame` / `prepared` / `buffer_start` / `buffer_end` / `seek` / `stall` / `speed` / `quality` / `audio_focus` / `danmaku` / `danmaku_window` / `skip_oped` / `next_video` / `pause` / `resume` / `lifecycle` / `error` / `complete` |
| 结果指标 | `play.*`（退出位置、最大位置、真正在播的墙钟时长、完成度、缓冲次数/总时长、seek/stall/暂停/恢复计数）、`danmaku.*`（加载路径、段数、窗口次数） |
| 帧率与漂移 | 播放页挂 `Choreographer` 帧回调 + 250ms 主循环采样，累计帧数、长帧、`position` 漂移（相对 `elapsedRealtime`，不用 `currentTimeMillis`——用户改系统时间会让差值失真） |
| 内存日志 | `Logu.ringSnapshot()` 的**尾部**，吃正文剩余预算；会话期间缓冲临时放大到 512K，结束在 `finally` 还原 |
| 单测/交接钩子 | `snapshotForTest(reason)` 只拼正文不上报 |

判读建议（怎么从一份报告里看出「卡在哪」）：先比 `first_frame` 与 `prepared` 的 `[+Xms]`（起播慢还是起播后卡），再看 `buffer_start`/`buffer_end`/`stall` 的密度与对应秒数，然后用 `quality`/`speed`/`danmaku` 三个事件对齐时间轴，看是不是「切清晰度之后开始卡」或「弹幕窗口一开就卡」；最后用基线里的 `decode.*` 与 `device.perf*` 回答「为什么是这台机器」。

## 9. 验收标准

框架本身（普通包）：

1. `./gradlew.bat :app:assembleRelease` 两个 flavor 都出包，`versionName` **不带** `-TEST-` 后缀。
2. 详情页与播放页**看不到**任何诊断入口（`fragment_video_info.xml` 里该按钮 `visibility="gone"`）。
3. 编译期即可确认：`TestBuild.NAME` 为空串、`TestBuild.enabled()` 恒 `false`。

框架本身（测试包）：

1. `./gradlew.bat :app:assembleCoexistRelease -PtestBuild=diag` 构建成功，`versionName` 为 `26.11.07-TEST-diag`。
2. 详情页出现诊断入口；点它起播一段后退出，管理台崩溃表出现一条 `exception` 以 `com.RobinNotBad.BiliClient.testbuild.` 开头的记录，`version_name` 带 `-TEST-diag`，`stack` 含三段标题。
3. 同一段播放「播完后再退出」只产生**一条**报告；点诊断按钮后不改主意直接退出，再正常播放一段**不应**被当成诊断会话（armed 一次性消费）。
4. 正文里搜不到 `SESSDATA` / `access_key` / `access_token` / 完整查询串（§7）。
5. 会话结束后 `Logu.ringMaxChars()` 回到 65536（诊断期放大已还原）。

工程：

1. `:app:testStandardDebugUnitTest` / `:app:testCoexistDebugUnitTest` 绿（`snapshotForTest` 与单测逃生口可覆盖门闸、直播不采集、正文分段与截断、放大还原）。
2. 新增文案（诊断入口的按钮文字与无障碍描述）进 `res/values/strings.xml`，无硬编码中文。
3. 无新增第三方库、无新增权限、无新增导出组件。

## 10. 未决与风险

以下几条**尚未实测或需要后续跟进**：

1. **R8 折叠到什么程度没有实测**。`if (TestBuild.enabled())` 分支被判死是确定的（常量 `false`），但「`PlaybackDiag` 整个 object 是否被完全移除」「普通包体积有没有因此产生可测差异」本轮没做 mapping / 包体积对比。若哪天要给出「普通包一行诊断代码都不剩」的结论，得拿 `assembleRelease` 的 mapping 或 dex 反查来证明。
2. **诊断会话期间发生真崩溃，内存日志只有 64K**：崩溃快照跨进程传，`ErrorCatch` 因此按默认上限 `Logu.ringSnapshot(Logu.RING_MAX_CHARS)` 截取（不为测试版改动正式链路的崩溃上报行为）。也就是说放大到 512K 的缓冲**只在诊断报告自己的正文里**生效；同一会话里真崩了，那条崩溃报告的日志仍是改动前的量级。这是有意的取舍，别当成 bug。
3. **旧版本报告的类名可读性**：`exception` 前缀本来就是可读类名，解混淆对它无事可做；但如果某版本没有 mapping（`retrace.state = 2`），真崩溃那侧会回落原始栈，两类报告在列表里长得更像，**筛选必须按 `exception` 前缀，不要按 `stack` 内容猜**。
4. **上报是尽力而为**：崩溃通道失败只回 `-1`，报告就丢了。测试包发给用户时要在交接单里写清「回来后确认管理台有记录」，不要假定用户用过一次就一定抓到了。
5. **默认只收 W/E/F 级别日志**：内存日志挂在 `Logu` 的 `w`/`e`/`wtf` 路径上，`v`/`d`/`i` 受 `LOGV/LOGD/LOGI_ENABLED` 门控（release 下为 false）。所以报告里的「内存日志」段在默认情况下只有告警与错误行；需要更密的日志时靠 probe 自己的 `event`，不要指望把详细日志开关塞给用户。
6. **正文额度是共享的**：指标 + 时间线 + 内存日志三段共用 190000 字符，基线本身就有几十个键。若某次现场复核发现「缺的正好是被截掉的日志段」，给 probe 加「日志只带最后 N 行」的开关，而不是抬高上限。
7. **`PlaybackDiag` 的单测逃生口是内部约定**：`enabledForTest` / `reportSinkForTest` / `armTtlMs` 都是 `internal`，只有当测试与 probe 同模块时才可用；`enabled()` 里那道 `BuildConfig.DEBUG` 门是安全阀，**改它之前先想清楚「release 包会不会因此打开诊断」**。
