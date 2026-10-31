# ReBiliClient 架构通读（为改功能准备）

> 通读日期：当前 `main` 分支工作区（`versionName 26.10.19` / `versionCode 2610190`），26.10.04 初版、26.10.10 复核、26.10.11 复核、26.10.20 复核（版本与测试计数）
> 目的：改功能前先摸清**真实**架构。本文结论均基于逐文件读源码核实；`AGENTS.md` 已按本文事实重写。
> 配套阅读：`docs/review/fix-progress.md`（**当前状态快照 + 未完成待办**，唯一活台账）、`docs/FEATURES.md`（用户可见功能总表）、`docs/tutorial-system-redesign.md`（教程系统重做，进行中）。
> 历史调研 / 审计 / 已执行完毕的方案已全部移入 `docs/archive/`（总索引见 `docs/archive/README.md`，文档总索引见 `docs/README.md`）——**归档文档只代表当时，引用前先确认现状**。

---

## 1. 一句话结论：项目没有"新层"

旧版 `AGENTS.md` 曾声称存在两条并存架构（遗留 Java 层 + 新 Kotlin 层）。**实测第二条完全不存在**，这条幻觉已从 `AGENTS.md` 中删除，事实记录如下：

| 旧 AGENTS.md 声称 | 实际情况（已核实） |
| --- | --- |
| `network/api/`（Retrofit + kotlinx-serialization） | 目录**空**，0 个文件 |
| `di/`（Hilt） | 目录**空**，0 个文件 |
| `data/repository/` | 目录**空**，0 个文件 |
| `ui/*`（ViewModel/MVVM） | 只有 `ui/appearance/`（26.09.11 新增，现 10 个文件：`AppearanceManager.kt` / `ColorScheme.kt` / `CornerStyle.kt` / `FontStyle.kt` / `AppearanceApplier.kt`，26.10.26~27 又加 `AmbientGlow.kt` / `ColorMode.kt` / `GlassStyle.kt` / `ThemeTint.kt`，26.10.31 加 `RoundListEffect.kt`）+ `ui/widget/`（13 个文件，26.10.21 新增 `SwipePageLayout.kt`）。`ui/theme/` **已并入 `ui/appearance/`**（原 `ThemeManager.kt` → `ColorScheme.kt`）；其余 `ui/base`、`ui/player`、`ui/video/viewmodel` 等子目录**已全部删除** |
| `BiliTerminalApp.kt`（@HiltAndroidApp）为入口 | **该类已于 26.10.02 整体删除**。Manifest 指向 `.BiliTerminal`（26.09.10 起是 Kotlin，原先为 `.java`）；它从未被实例化，只因 `SplashActivity` 的 UETool 逻辑引用其静态方法与常量而残留，那些已移入 `BiliTerminal.kt` 伴生对象 |

**核实方式**：全工程 `grep '@AndroidEntryPoint|@HiltViewModel|@Inject|@Module|@InstallIn'` → **0 命中**；空目录统计 → **24 个**（26.09.11 已全部删除）。

**结论**：全项目实际是**单层遗留架构**——Java 静态方法 + `org.json` 逐层拆 JSON + `startActivity` 直跳。没有 DI、没有 Repository、没有 ViewModel、没有 Retrofit 调用。

### 死依赖清单 —— **26.09.11 已全部删除**

原先 `app/build.gradle` 里有以下全工程无人使用的依赖，现已移除（连带 `ksp` 与 `kotlin.plugin.serialization` 两个插件、`BiliTerminalApp.kt` 上的 `@HiltAndroidApp`）：

```gradle
// 已删除：
implementation 'com.google.dagger:hilt-android:2.51.1'                      // 无任何注入点
ksp 'com.google.dagger:hilt-compiler:2.51.1'
implementation 'com.squareup.retrofit2:retrofit:2.11.0'                      // 无任何 Interface 声明
implementation 'com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0'
implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0'      // 无 @Serializable
implementation 'androidx.navigation:navigation-fragment-ktx:2.7.7'           // 0 引用
implementation 'androidx.navigation:navigation-ui-ktx:2.7.7'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2'            // 0 引用
implementation 'androidx.asynclayoutinflater:asynclayoutinflater:1.0.0'      // 项目有自实现 AsyncLayoutInflaterX
implementation 'com.google.protobuf:protobuf-javalite:3.21.12'               // ProtobufParser 是手写解析器
implementation 'com.geetest.sensebot:sensebot:4.3.5'                         // 验证码只走 WebView JS
implementation 'androidx.cardview:cardview:1.0.0'                            // 只用 MaterialCardView
debugImplementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
ksp { arg("room.schemaLocation", ...) }                                     // 项目用 SQLiteOpenHelper，无 Room
```

**改功能时的影响**：新代码直接加到 `api/` + `activity/`，沿用静态方法 + `org.json` 风格；`network/api/`、`di/`、`ui/*` 这些空壳目录已删除，别再往里加东西。

> **26.09.10 曾清理过 multidex**：那时 `androidx.multidex:multidex:2.0.1` 依赖、`MultiDex.install(this)` 与
> `attachBaseContext` 覆写都被删掉了，理由是 `minSdk 21` 下 ART 原生支持 multidex。
> **26.10.10 降到 19 之后这段清理被整体回滚**（19 及以下 ART 还不支持原生多 dex），见下一条。

> **minSdk 24 → 21 → 19（Android 4.4）已落地**（26.10.10 下调，最终停在 **19**）：`app/build.gradle:21` 是唯一落点。
> 依赖侧必须降两处——`androidx.appcompat:appcompat` 1.7.0 → **1.6.1**、`androidx.fragment:fragment-ktx` 1.8.0 → **1.6.2**
> （只有这两代 AAR 自己还声明 minSdk 14；1.7.0 / 1.8.0 声明的是 21），并恢复
> `androidx.multidex:multidex:2.0.1` + `BiliTerminal.kt` 里 `override fun attachBaseContext()` 中的
> `MultiDex.install(this)`。改回 21 时这三件事要一起撤掉。
> 代码侧除了 minSdk 21 阶段清掉的 API 24 调用，又清了一批 API 21 调用点，全部列在下面 §1.1。
> `multiDexEnabled true` 保留未动。

### 1.1 minSdk 19（Android 4.4）兼容清单（26.10.10 落定，改代码前核对）

`minSdk` 从 24 一路降到 19，要照顾的是 API 19 / 20 / 21 / 22 / 23 五个区间。
**下表里标「已清除」的都是本轮真改过的代码**；标「不会踩」的是核实过的。逐项结论：

| 会踩的东西 | 何时才有 | 现状 |
| --- | --- | --- |
| `java.util.function.*`（`BooleanSupplier` / `Consumer` / `Supplier` …） | API 24 | **已清除**：`util/AsyncLayoutInflaterX.java` 改用工程内 `CancelledChecker`、`util/SharedPreferencesUtil.java` 改用 `EditorAction`（两个调用点 `activity/base/BaseActivity.kt:435`、`util/AccountManager.java:202` 走 SAM 转换，未改） |
| `java.util.Map.getOrDefault` 等默认方法（`putIfAbsent` / `computeIfAbsent` / `removeIf` / `List.sort`） | API 24 | **已清除**：`activity/reply/WriteReplyActivity.kt:177` 改成 `msgMap[k] ?: 默认值`。注意 `util/Cookies.java:41` 的 `getOrDefault(String, String)` 是**工程自定义方法**（内部就是 `cookieMap.get` + 判空），与平台默认方法无关，改它反而会坏事 |
| `java.util.Optional` / `java.util.stream.*` / `java.time.*` / `CompletableFuture` / `LongAdder` / `ThreadLocal.withInitial` | API 24 / 26 | **工程源码全库零命中**（已 grep `app/src/main` 的 `.kt` + `.java`）。`util/TimeUtil.kt:37` 留了一条「别用 `ThreadLocal.withInitial`」的注释——保持这个状态。**但第三方库里有**，见下面「依赖里的 API 24 引用（dex 级审计）」 |
| `java.util.concurrent.ConcurrentHashMap.newKeySet()`（及它返回的 `KeySetView`） | API 24 | **已清除**：`util/VideoPreloadManager.kt:12` 原用 `newKeySet<Int>()` 做「在飞预加载」去重集合，类一初始化就 `NoSuchMethodError`（已确认穿过 R8 留在 release dex 里）。改成 `java.util.Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())`（API 9，并发语义等价） |
| Kotlin 对 `Int` / `Long` 等基本类型属性的**装箱 `hashCode()`**（`java/lang/Long.hashCode(J)I` 这类静态重载） | API 24 | 不会在 release 踩：`jvmTarget = '17'` 下 Kotlin 生成这些调用，但 R8 在 release 里已全部消除（实测 `classes.dex` 零命中）。**只有出 debug 包时才有风险**（21 个数据类的 `hashCode()`），真进 `HashMap`/`HashSet`/`distinct()`/`groupBy()` 才会炸；发 release 不用管 |
| **`Build.SUPPORTED_ABIS`** | **API 21** | **已清除**：它是静态**数组字段**（不是内联常量），低版本一执行 `getstatic` 就 `NoSuchFieldError`，而它是 `Error`、`catch (Exception e)` 兜不住。三处调用点统一改走 `api/TerminalApi.java` 新增的 `firstAbi()` / `supportedAbis()`（内部有 `SDK_INT >= LOLLIPOP` 闸，低版本回落 API 1 就有的 `Build.CPU_ABI`）：`util/CrashReporter.kt:71`、`api/TerminalApi.java` 的 `ping()`、`util/UpdateManager.kt:160`。定稿包 `classes.dex` 里 `SUPPORTED_ABIS` 只剩 **1 处**引用，落在 D8 生成的 `$$ExternalSyntheticApiModelOutline` 桶类的静态方法里，唯一调用点就在那个 `SDK_INT` 闸内 |
| **`android.widget.TextView(Context, AttributeSet, int, int)`（4 参构造）** | **API 21** | **已清除**：`ui/widget/TextClock.kt` 原来是 `@JvmOverloads constructor(context, attrs, defStyleAttr, defStyleRes) : TextView(context, attrs, defStyleAttr, defStyleRes)`——`@JvmOverloads` 自动生成的 **2 参构造**（就是 `LayoutInflater` 反射调的那个）方法体里调的正是 4 参构造。而全工程有 **43 个布局**写着 `<com.RobinNotBad.BiliClient.ui.widget.TextClock>`，4.4 上一解析就崩。改成三个显式次构造（1 / 2 / 3 参），只碰 API 1 就存在的重载 |
| **`View.setBackgroundTintList`** | **API 21** | **已清除**：`activity/settings/TutorialManagerActivity.kt:195` 与 `:199` 改成 `ViewCompat.setBackgroundTintList(...)`（附注：`statusBadge` 本身没设 `background`，所以 pre-21 下这个方法是个 no-op，不会崩；观感与 21+ 一致） |
| **XML drawable 里的主题引用（`android:tint="?android:attr/..."`）** | **API 21** | **已清除**：7 个矢量图（`icon_announcement` / `icon_download` / `icon_home` / `icon_nav_dynamic` / `icon_nav_media` / `icon_nav_trending` / `icon_update`）在 `res/drawable/` 里的版本改用固定色 `@color/text_primary`（原 `?android:attr/colorForeground`）/ `@color/text_secondary`（原 `?android:attr/textColorSecondary`），**带 `?android:attr` 的正本原样挪到 `res/drawable-v21/`**——API 21+ 的观感与改动前完全一致。**两份文件必须一起改**，`res/drawable/` 那份里留了指针注释 |
| `android.app.NotificationChannel` | API 26 | 不会踩：三处都有 `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)` 闸——`service/PlaybackService.kt:90`、`util/MsgNotifier.kt:156`、`service/download/DownloadNotifier.kt:39` |
| `PendingIntent.FLAG_IMMUTABLE` | 常量 API 23 才有 | 不会踩：编译期常量直接内联，旧系统只是忽略该位 |
| `java.util.Objects.requireNonNull` | API 19 | 安全，`api/*.java` 里大量在用 |
| 矢量图 `VectorDrawable` | 原生支持要 API 21 | 安全：全库**没有** `android:background="@drawable/<矢量>"`，矢量只出现在 `android:src` / `android:drawableStart`，消费方是 AppCompat 控件，pre-21 由 AppCompat 接管 |

**验证方式**：`./gradlew.bat :app:lintRelease --no-configuration-cache`（lint 会按 `current min is 19` 逐条报
`NewApi`）。**26.10.10 定稿实测：全量 1181 issue 里 `NewApi` 从 22 条降到 2 条**，这 2 条就是下面说的
`PlayerActivity.kt:1981` 假阳性（`InlinedApi` 3 条、`ObsoleteSdkInt` 14 条均未变）。除上表外，lint 还有两类**不需要改**的输出：
`InlinedApi`（`FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS`、`MotionEvent.AXIS_SCROLL` 等常量已内联进字节码，旧系统只是忽略）与
`ObsoleteSdkInt`（现在恒真的 `SDK_INT` 分支）。
另外 `activity/player/PlayerActivity.kt:1981` 的 `MediaSession.Callback` 是 **lint 假阳性**——它所在的
`private fun initMediaSession()` 第一行就是 `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return`，
lint 不做跨语句/跨方法的控制流跟踪。

**依赖下限（再往下调会硬失败）**：下限来自 **AAR 自己声明的 `minSdkVersion`**（`checkReleaseAarMetadata` +
清单合并会硬失败），实测值（读缓存 AAR 内的 `AndroidManifest.xml`）：

| 依赖 | 当前版本 → minSdk | 可降到 | 备注 |
| --- | --- | --- | --- |
| `androidx.appcompat:appcompat` | 1.7.0 → **21** | 1.6.1 → **14** | 1.6.1 的 `minCompileSdk` 是 33 |
| `androidx.fragment:fragment(-ktx)` | 1.8.0 → **21** | 1.6.2 → **14** | 同上 |
| `androidx.core:core(-ktx)` | 1.13.1 → **19** | ≤1.9.0 才到 14 | |
| `androidx.activity:activity(-ktx)` | 1.9.0 → **19** | ≤1.2.0 才到 14 | |
| `com.google.android.material:material` | 1.12.0 → **19** | ≤1.5.0 才到 14 | |
| `androidx.recyclerview:recyclerview` | 1.3.2 → **14** | — | |

结论：**降到 19（Android 4.4）在依赖层是通的，且已落地**——`appcompat` 1.7.0→1.6.1、`fragment(-ktx)` 1.8.0→1.6.2，
外加恢复 `androidx.multidex:multidex:2.0.1` + `MultiDex.install()`（19 下 ART 还不支持多 dex）。
`:app:clean` → `:app:assembleRelease` 在 minSdk 19 下 **BUILD SUCCESSFUL**（2m13s），四个包 `aapt2 dump badging` 都是
`minSdkVersion:'19'` / `targetSdkVersion:'34'`（体积：universal **12,564,796 B**、x86 6,777,240 B、arm64-v8a 6,673,074 B、
armeabi-v7a 6,225,574 B），说明 release 全量依赖里没有别的东西要求 >19。
**再往下到 14（Android 4.0）就不通了**：`core 1.13.1` / `activity 1.9.0` / `material 1.12.0` 三族本身就是 19，
得一并降级；且自家代码还要补 API 21 的调用点（见本表上面几行），成本远高于收益。
（对照：上游 2.9.7 时代的 `app/build.gradle` 是 `minSdk 14` + `compileSdk 33` + `targetSdk 26` + Java 9 且**不挂 Kotlin 插件**，
那套能到 14 是因为代码本身就是老纯 Java 工程，不是靠换依赖清单实现的。）

**为什么不能把上游 2.9.7 的依赖清单直接搬过来**（有人问过，记下来省得重复论证）：

1. **那份脚本不挂 Kotlin 插件**，而本工程 `app/src/main` 有 260 个 `.kt`、135 个 `.java`。直接换 = 所有 Kotlin 类不参与编译，
   Java 侧引用 Kotlin 对象（`Logu`、`CenterThreadPool` …）全部找不到符号。
2. **它没有本工程必需的东西**：`kotlinx-coroutines-android`（36 处 `import kotlinx.coroutines`、24 处 `StateFlow`）、
   `core-ktx`（17）、`activity-ktx`（23，含 27 处 `registerForActivityResult`）、`fragment-ktx`（16）、`lifecycle-*`（7）、
   `recyclerview`（122）、`viewpager2`（4）。
3. **okhttp 3.12.1 与 4.12.0 是源码级不兼容**：工程里 46 处 Kotlin 写的是 OkHttp 4 的 Kotlin 属性语法
   `response.body!!` / `response.body?.string()`；3.12.1 是纯 Java 的 `body()` 方法，Kotlin 下 `response.body` 直接不解析。
4. **它会退掉工程级配置**：`compileSdk 33`（`material 1.12.0` 要求 34，`checkReleaseAarMetadata` 直接失败）、
   `targetSdk 26`（通知渠道 / 前台服务类型 / 分区存储等适配全部回退）、`abiFilters 'mips'`（NDK 早已删除 mips，
   且会丢掉 `arm64-v8a`）、`sourceSets exclude '**/GeetestUtil.java'`（本工程根本没这个文件）。
   那份脚本的 `signingConfigs` 里还引用了未定义的 `pro` 变量，**照抄会直接配置失败**。
5. **它会加回已确认零引用的依赖**：`androidx.asynclayoutinflater`（工程另有一个自研 `util/AsyncLayoutInflaterX.java`，
   只依赖 `androidx.core`/`androidx.appcompat`，不依赖它）、`androidx.multidex`、`org.brotli:dec` + `brotli4j`
   （由 `:brotlij` 模块 `api fileTree('libs')` 自带）。`protobuf-javalite` 也**不需要**：
   `com.RobinNotBad.BiliClient.model.DmSegMobileReply` 与 `util/ProtobufParser.java` 是手写的 protobuf wire-format 解析
   （`Cursor` + `readVarint`），`com.google.protobuf` 在 `app/src/main` 与 `:DanmakuFlameMaster`（64 个源文件）里都是 0 命中。

**依赖里的 API 24 引用（dex 级审计，26.10.10 必须知道）**：源码干净不等于包干净。把 `app-universal-release.apk` 的 `classes.dex`
用 `dexdump -d` 反汇编后逐条归属到类，结论：

- **`org.jsoup:jsoup` 必须钉在 `1.16.1`**（`app/build.gradle` 的依赖块）。`1.16.2` 起 `org.jsoup.internal.StringUtil`
  的**静态初始化块**就会调 `ThreadLocal.withInitial(java.util.function.Supplier)`，那两者都是 API 24 才有；而 `StringUtil`
  被 `Jsoup.parse()` 必然用到，于是 Android 4.4/5.0/5.1/6.0 上一解析 HTML 就 `NoSuchMethodError`。工程里 jsoup 调用点只有两处：
  `api/CookieRefreshApi.java:64` 的 `Jsoup.parse(response.body().string())` 与 `util/StringUtil.java:164` 的
  `Jsoup.parse(html).text()`。降到 `1.16.1` 后 dex 里 `java/util/function/` 与 `withInitial` **双双归零**（实测；1.15.4 / 1.16.1 都没有该调用，
  1.16.2 才有）。1.16.1 里残留的 `java.util.function.Consumer` 只出现在已废弃的
  `Node.forEachNode(Consumer)` 之类**方法签名**上，工程从不调用，ART 不会在类加载时解析方法参数类型。
  安全上不吃亏：`CVE-2026-71497`（jsoup 的 `Cleaner` + 自定义 `Safelist` 才可触发的 XSS）影响
  `>= 1.14.3 且 < 1.23.1`，**1.17.2 与 1.16.1 同样在受影响区间内**，降级没有引入新问题；本工程只用
  `Jsoup.parse(...).text()` / `.select()`，从不碰 `Cleaner` / `Safelist`，该 CVE 不适用。
- 残余的 API 24+ 引用只剩三个来源，**都不会在 API 19~23 上执行**，因此不动：
  okhttp 的 `java.time.Duration` / `Instant` 重载（`OkHttpClient$Builder.xxxTimeout(Duration)`、
  `Headers$Builder.add/set(String, Instant)`、`Headers.getInstant`——工程调的全是 `(long, TimeUnit)` 版本）；
  AndroidX 的 `AccessibilityNodeInfoCompat$Api34Impl`（`SDK_INT >= 34` 闸）；
  `androidx.emoji2` 的 `UnprecomputeTextOnModificationSpannable.chars()/codePoints()`（是对 `CharSequence` 的 Java 8 默认方法的**重写**，
  API < 24 的 `CharSequence` 上根本没有这两个方法，没人会调到）。
- R8 产物里 `C1.h` / `N.g` 这种啥都沾一点的「桶」不是业务代码：用 `app/build/outputs/mapping/release/mapping.txt`
  反查可得它们是 **D8 生成的 `$$ExternalSyntheticApiModelOutline*`**，本身就是为老系统的类验证安全而生的。
- 复现方式：解压 APK 取 `classes.dex` → `build-tools/36.0.0/dexdump -d` → 扫 `Ljava/util/function/`、`withInitial`、`Ljava/util/stream/`；
  再用 mapping.txt 把混淆名反查回原名。
- **26.10.10 定稿包的原始串计数**（universal，**只有 1 个 `classes.dex`，3,722,236 B——R8 后方法数没过 64K，`classes2.dex` 不存在**，
  所以 multidex 库对 release 只是给 debug / 未来增长兜底）：`java/util/function/` **0**、`withInitial` **0**、`newKeySet` **0**、
  `KeySetView` **0**、`java/util/Optional` **0**、`java/util/Base64` **0**、`hashCode(J)` **0**、`SUPPORTED_ABIS` **1**；
  `java/util/stream/` **1**、`java/time/` **2**——后三者都已在上面的归属清单里解释清楚（闸内 / 仅签名 / `@IgnoreJRERequirement` 重载）。
- **判据（别被扫描结果吓到）**：Dalvik/ART 上，一个不存在的类**只出现在签名/常量池里**（参数类型、返回类型、字段类型、覆写一个当时还不存在的方法）
  **不会崩**；真会崩的只有三种——invoke 一个 owner 存在于当时但没有的成员（`NoSuchMethodError`）、实例化/解析一个不存在的类
  （`NoClassDefFoundError`）、以及 lambda 脱糖出的合成类**所实现的接口**不存在。所以「dex 里引用了 API 24 类型」**不等于**「会崩」。
- **扫描工具要按完整描述符做键**：只按成员名去键会把重载系统性误报（`Objects.requireNonNull` 取到 `(Object,Supplier)` 报 24、
  `Context.getSystemService` 取到 `(Class)` 报 23、`Map.remove` 取到 `(Object,Object)` 报 24、`View.setFocusable` 取到 `(int)` 报 26），
  一次能刷出 400+ 条假警报。按描述符复核后，app 自己字节码里 since>21 的成员只剩个位数。

**TLS 不受影响**：`util/NetWorkUtil.java:160 setOkHttpSsl()` 已收敛成无条件返回传入的 builder（历史 trust-all 分支整体删除），
所以下调 minSdk **不会**重新激活中间人风险——这正是当初删它的原因。

**没做的事**：本机 `D:\Program Files\android-sdk` 下没有 `emulator`、没有 `system-images`，
**没有在 API 19~23 的设备/模拟器上做过运行验证**，以上全部是静态审计 + 依赖元数据 + `dexdump` 反汇编结论。
真机验证清单见 `docs/archive/review/device-test-plan.md` 的 T12。

---

## 2. 入口与启动链路

```text
AndroidManifest.xml:27  android:name=".BiliTerminal"   ← 真实 Application（Kotlin）
        ↓
BiliTerminal.onCreate()
   ├─ 判进程名（读 /proc/self/cmdline）
   │    ├─ 是 :error_activity（26.10.04 起崩溃页独立进程）
   │    │    → 只做 SharedPreferencesUtil / context / Logu 开关，然后直接 return
   │    └─ 主进程继续
   ├─ SharedPreferencesUtil.sharedPreferences = getSharedPreferences("default")
   ├─ context = getFitDisplayContext(this)      ← DPI 缩放包装 Context
   ├─ PerformanceManager.init(this)             ← 设备分级（26.10.04 起：有缓存直接读，
   │                                                首次先落中档、检测丢 CenterThreadPool）
   ├─ 强制更新拦截：注册 ActivityLifecycleCallbacks，
   │    onActivityPreCreated 里把任何 Activity 换成 UpdateActivity 并 finish
   ├─ ErrorCatch.init / applyLogSwitches()
   ├─ 后台异步：动态更新数、消息未读数（仅已登录 mid != 0）
   └─ checkAppUpdate()                          ← 读 Gitee（失败回落 GitHub）的 releases/latest
        │                                         强制更新 → 立即拦截拉起 UpdateActivity
        └─ 非强制更新 → 挂起待跳，等前台页就绪（onActivityResumed）再打开 UpdateActivity
        ↓
SplashActivity（LAUNCHER，typewriter 动画）
   ├─ Debug 包先要悬浮窗权限（UETool）
   └─ proceedSplashFlowWithPrivacyGate()
        ├─ needsPrivacyConsent() → TerminalDialog.choice 弹一次隐私说明
        │    （同意/不同意都继续启动，不拦人；不同意 = 关掉两个上报开关）
        └─ proceedSplashFlow()
             ├─ 未完成初始设置 → SetupUIActivity
             ├─ 已完成 → SharedPreferencesUtil.loadMenuEnabled().firstOrNull()
             │            → MenuActivity.btnNames[key].second  ← 用"第一个启用的菜单项"当首屏
             └─ 异步：App token / Cookie 刷新、CookiesApi.checkCookies、AppInfoApi.check（更新包清理等）
                       + TerminalApi.checkAnnouncement()（公告差量；**客户端唯一的公告源**）
                       + TelemetryReporter.reportIfNeeded()（匿名日活，一天一次）
```

**改功能注意**：

- `BiliTerminal.onCreate()` 里所有初始化都包在 `if (context == null)` 内——**多进程/重复创建时只会跑一次**。新增全局初始化要放在这个块里，否则可能被跳过。
- **`CatchActivity` 跑在 `:error_activity` 独立进程**（26.10.04 批次 3，`AndroidManifest.xml` 的 `android:process`）。主进程已崩溃时，同进程再启 Activity 容易被一起带走，所以崩溃页必须自给自足：它**不会**经过 `PerformanceManager.init`、`ErrorCatch.init`、未读轮询、更新检查。往 `CatchActivity` 加依赖前先确认那个依赖不要求这些初始化。判进程用 `BiliTerminal.currentProcessName()`（读 `/proc/self/cmdline`——`minSdk 19` 用不了 API 28 的 `Application.getProcessName()`）。
- 首屏由**用户自定义菜单顺序**决定，不是硬编码 `RecommendActivity`。改导航时注意 `loadMenuEnabled()`。
- `onActivityPreCreated` 的强制更新拦截会 **finish 掉任意 Activity**，调试时若被"莫名其妙踢到更新页"，检查 `force_update_required` 这个 SharedPreferences 键。
- **启动期不要直接 `startActivity`**：更新检查是异步的，结果可能在启动页还在播动画时返回，此时打开的页面随后会被首屏盖住。非强制更新的自动跳页因此走 `BiliTerminal` 的 `pendingAutoOpenUpdate` + `onActivityResumed`（`SplashActivity` 不算就绪），一个版本只跳一次（键 `update_last_new_version`）。加同类"启动后自动跳页"功能时照抄这条，别在后台回调里直接拉页面。
- **公告只有一个源**：客户端已**不再直连上游** `api.biliterminal.cn` 的公告接口。上游公告改由自建服务端拉取、管理员在控制台**逐条**审核后透传（`source=relay`），启动差量弹窗与「公告列表」都只读 `TerminalApi.checkAnnouncement()` / `getOwnAnnouncementList()`。**别在客户端重新加回「两路合并」**（26.10.23 已整体删除）。

---

## 3. 导航体系

### 3.1 菜单表（唯一导航注册点）

`activity/MenuActivity.kt:45-59` 的 `btnNames` 是 `LinkedHashMap<String, Pair<标题, Class>>`，共 **14 个入口**：

| key | 标题 | Activity | key | 标题 | Activity |
| --- | --- | --- | --- | --- | --- |
| `recommend` | 推荐 | RecommendActivity | `dynamic` | 动态 | DynamicActivity |
| `short_video` | 短视频 | ShortVideoPlayerActivity | `myspace` | 我的 | MySpaceActivity |
| `popular` | 热门 | PopularActivity | `message` | 消息 | MessageActivity |
| `precious` | 入站必刷 | PreciousActivity | `local` | 缓存 | LocalListActivity |
| `ranking` | 全站排行榜 | RankingActivity | `settings` | 设置 | SettingMainActivity |
| `hotsearch` | 热搜 | HotSearchActivity | `search` | 搜索 | SearchActivity |
| `live` | 直播 | RecommendLiveActivity | `timeline` | 时间线 | TimelineActivity |

`MenuActivity` 是 `launchMode="singleTask"` 的独立页；用户在设置里排序/启用哪些项存在 SharedPreferences（`SharedPreferencesUtil.loadMenuEnabled()`）。

**新增一级页面必须改三处**（漏一处就失效）：

1. `MenuActivity.btnNames` 加一行（id → 标题 + Activity Class）。
2. `util/MenuConfig.kt:14` 的 `ALL_ITEMS` 加同一个 id —— 这是 `menu_enabled` 配置串的**单一数据源**，`MenuConfig.parse()` 遇到未知 key 会直接返回 `null`，导致整份用户菜单配置被判非法并回退默认。决定默认是否显示还要看 `FIXED_ITEMS` / `SWITCHABLE_KEYS` / `SWITCH_DEFAULTS`。
3. `AndroidManifest.xml` 注册 Activity。

> `MenuConfig` 是纯 Kotlin 对象、无 Android 依赖，已有 `app/src/test/.../MenuConfigTest.kt`——改菜单逻辑时**优先在这里加测试**。

### 3.1b「我的」页面入口配置（26.09.10 新增）

`activity/user/MySpaceActivity.kt` 的功能入口不再硬编码，由 `util/MySpaceConfig.kt` 驱动：

- 两份有序 key 串 `myspace_main` / `myspace_more`（`;` 连接），互斥且并集 = `ALL_ITEMS`；任一非法（未知 key、重复、漏项、跨列表重复）整体回退默认并写回。
- 固定项不参与配置：用户卡片永远第一（设置页里不出现），`more`（更多按钮）与 `logout`（退出登录）永远最后两位；**更多列表为空时页面上不显示「更多」按钮**。
- 入口的图标 / 文案 / 跳转统一定义在 `activity/user/MySpaceMenu.kt`，主列表与更多页（`MySpaceMoreActivity`）共用，改跳转只改这一处。
- `creative`（创作中心）仍受通用偏好开关 `creative_enable` 控制：设置页里始终可见可排序，页面上按开关决定是否渲染。
- 设置入口 `activity/settings/SettingMySpaceActivity.kt`，适配器 `adapter/MySpaceSettingAdapter.kt` 单页双分区拖拽（两区都能排序、可互相拖入）。
- 加新入口时同步 `MySpaceConfig.ALL_ITEMS` 与 `MySpaceMenu.ITEMS` 并注册 Manifest；`MySpaceConfigTest.allItems_andMenuKeys_match` 会守卫两者一致。

### 3.2 三个基类层级

```text
AppCompatActivity
  └─ BaseActivity                ← 主题应用、方向、DPI/边距、EventBus 注册、onKeyDown(MENU→finish)
       ├─ InstanceActivity       ← 一级页：MENU 键/顶栏 → 打开 MenuActivity；BiliTerminal.setInstance(this)
       │    └─ RefreshMainActivity  ← 一级页 + 下拉刷新 + 上拉加载（自带菜单按钮）
       └─ RefreshListActivity    ← 二级页 + 下拉刷新 + 上拉加载（顶栏点击 = finish）
```

**选哪个基类**：

| 场景 | 继承 |
| --- | --- |
| 一级页面（菜单能直达） | `InstanceActivity` 或 `RefreshMainActivity` |
| 二级页面（列表 + 分页） | `RefreshListActivity` |
| 二级页面（普通） | `BaseActivity` |

**两级页面的区别在于顶栏点击行为**：`BaseActivity.onStart()` 里 `if (this !is InstanceActivity) setTopbarExit()`——即非一级页点顶栏就 `finish()`；一级页由 `InstanceActivity.setMenuClick()` 改成打开菜单。

**`BaseActivity.onCreate()` 末尾装了表冠页面级兜底**（26.10.17，`installRotaryFallback()`）：把 `ui/widget/RotaryEncoderSupport.handlePage()` 挂在 **DecorView** 的 generic motion 监听器上，谁都没消费旋转事件时，找出本页第一个「可见且当前能上下滚」的竖直容器（`RecyclerView` / `NestedScrollView` / `ScrollView`）并滚它。原因：**旋转编码器事件只沿焦点路径分发**，列表页的 `RecyclerView` 默认不抢焦点，只把监听器挂在 `Rotary*` 控件自身上的旧写法在「页面上没有任何控件获焦」时永远收不到事件（表现为「设置页里能用、退出即失灵」）。两条约束，改之前先看：① **不要改回覆写 `Activity.dispatchGenericMotionEvent`**——generic motion 由 `ViewRootImpl` 直接派给 DecorView，不走那条路；② DecorView 的监听器只在所有子 View 都没处理后才被调用，所以不会与控件级监听器重复滚动，但**任何新的滚动容器都必须能被 `findScrollTarget` 认出来**，否则那一页的表冠仍然失效。

### 3.3 导航坑（两条均已在 26.09.08 修复）

- **MENU 键双重触发**：`InstanceActivity.onKeyDown` 打开 MenuActivity 后原本继续走 `super`，被 `BaseActivity.onKeyDown` 又 `finish()` 当前页。现在前者消费按键并 `return true`。
- **`from` 参数传不出去**：`InstanceActivity.menuClick` 原本判的是刚 new 出来的空 Intent（恒 false），现改为读 `getIntent().getStringExtra("from")`。

> 新增菜单跳转时仍要注意：`MenuActivity.btnNames`、`MenuConfig.ALL_ITEMS`、Manifest 三处必须同步（见 3.1）。

---

## 4. 依赖获取方式（没有 DI）

| 依赖 | 获取方式 | 位置 |
| --- | --- | --- |
| Application 级 Context / 工具 | `BiliTerminal.context`（**静态字段**，`@JvmField`，直接引用） | `BiliTerminal.kt:48` |
| 当前栈顶 Activity | `BiliTerminal.getInstanceActivityOnTop()`（`WeakReference`） | `BiliTerminal.kt:90` |
| 内容缓存 / 数据源 | `TerminalContext.getInstance()`（`InstanceHolder` 懒汉单例） | `TerminalContext.java:371` |
| 网络客户端 | `NetWorkUtil.getOkHttpInstance()`（`AtomicReference` 双检） | `NetWorkUtil.java:85` |
| 设置读写 | `SharedPreferencesUtil` 静态方法（`sharedPreferences` 静态字段） | `util/SharedPreferencesUtil.java` |
| 跨页事件 | **EventBus**（greenrobot），`SnackEvent` 为 sticky | `BaseActivity.kt:291-297,355-358` |
| 线程/协程 | `CenterThreadPool` 静态方法 | `util/CenterThreadPool.java` |

**这意味着**：任何新增功能都可以在任何地方 `BiliTerminal.context` / `TerminalContext.getInstance()` 直接取到全局对象，**不需要也无法用注入**。代价是全局可变状态遍地，测试困难。

---

## 5. 数据流模式

项目里只有**两种**数据流写法，没有第三种：

### 5.1 命令式（绝大多数页面）

```kotlin
CenterThreadPool.run {                       // 后台线程
    try {
        val data = XxxApi.getSomething(id)   // 同步阻塞网络请求
        runOnUiThread { adapter.setData(data) }
    } catch (e: Exception) {
        report(e)                            // BaseActivity.report → MsgUtil.err
    }
}
```

### 5.2 LiveData 式（详情页）

`TerminalContext` 提供 `getVideoInfoByAidOrBvId()` / `getArticleInfoByCvId()` / `getDynamicById()` / `getOpusById()` / `getLiveInfoByRoomId()` / `getReply()`，内部：

```text
LruCache<String, Object>(10) 命中？ → MutableLiveData(Result.success(缓存对象))
                    ↓ miss
CenterThreadPool.supplyAsyncWithLiveData { fetch...().getOrThrow() }
```

- 缓存键格式：`ContentType.getTypeCode() + "_" + id`（如 `video_12345`）。
- `Result` 是项目自己实现的类（`util/Result.java`），不是 Kotlin 的。
- 缓存容量仅 10 条，且**不区分 aid/bvid**——见第 7 节坑点。

### 5.3 线程约定

`CenterThreadPool`（`util/CenterThreadPool.java`）：

- `run(Runnable)` → 实际走 `Dispatchers.IO` 协程（`Build.VERSION.SDK_INT < 17` 的原生线程池分支在 minSdk 19 下**永不执行**，是死代码）。
- `supplyAsyncWithLiveData(Callable<T>)` → `LiveData<Result<T>>`，失败自动 `MsgUtil.err(e)`。
- `observe(Future<T>, Consumer<T>)` → 自动切主线程。
- `runOnUiThread(Runnable)` → `Handler(Looper.getMainLooper()).post`。

**改功能注意**：API 层全部是**同步阻塞**方法，调用方负责丢到后台线程。若在主线程直接调 `XxxApi.getXxx()`，会触发 `NetworkOnMainThreadException` 或卡顿。

---

## 6. 网络层

### 6.1 `NetWorkUtil`（`util/NetWorkUtil.java`，997 行，唯一出口）

- **单例 OkHttpClient**，`followRedirects(false)` + 自定义拦截器手写重定向（`b23.tv` 短链走 `RedirectHandler` 回调）。
- **DNS 强制 IPv4**（`Inet4Selector`，注释称 IPv6 请求有异常）。
- **`callTimeout` 有意不设**（`util/NetWorkUtil.java:144-145` 有注释）：这一个单例 OkHttpClient 同时跑 API 与大文件流式下载，而 `callTimeout` 覆盖「整个调用含读完 body」，会掐断正常的长下载。**别按「统一设超时」的直觉把它加上去**；要限时请用 `connectTimeout`/`readTimeout`。
- **Cookie 管理**：内存缓存 `cachedCookies` + `webHeaders` 静态 `ArrayList`（**索引 1 存 Cookie 字符串**）。`putCookie`/`setCookies` 的 `synchronized` 与 `saveCookiesLocked()`（`saveCookiesFromResponse` 的加锁主体）共用同一把 `NetWorkUtil.class` 锁。
- **csrf 唯一入口**（26.10.04 批次 2，台账 A1）：`currentCsrf()`（`:432`）＝ `pickCsrf(getCachedCookies(), 快照)`（`:443`，纯逻辑、有单测），优先取实时 Cookie 里的 `bili_jct`，取不到才退回 `SharedPreferencesUtil.csrf` 快照；`saveCookiesLocked()`（`:510` 附近）在落 Cookie 时**顺手回写**快照。**所有 POST 的 csrf 都必须调它，不要再直接读 `SharedPreferencesUtil.csrf`**——`bili_jct` 随 Cookie 刷新轮换，用旧快照会静默拿 `-111`（现象是「点赞/评论偶尔点了没反应」）。原来 14 个 `api/` 类里散布 41 处直接读快照的代码已全部收敛到这一处。
- **HTTP 追踪**（26.10.14）：`HTTP_TRACE` 静态开关（默认 false，只有 Agent 测试模式会打开）控制 `HttpTraceInterceptor`，每次请求落一行 `方法 脱敏URL -> 状态码 (耗时ms) | req=… | res=…`。三条硬约束：开关关闭时只做一次 boolean 读就透传；**只预览文本类响应**（视频/图片等二进制跳过，否则每次播放都白读磁盘）；用 `peekBody` 而非 `body.string()`——后者会把流读干，调用方拿到空 body。URL 里 `access_key`/`csrf`/`bili_jct` 等查询参数值一律替换成 `***`（`maskUrl`，与审计 S2「只记 Cookie 键名」同一原则）。
- **风控重试**：`executeJsonWithRiskRetry` 对 `code == -352 / -412` 重试；`executeWithDoctypeRetry` 对返回 `<!doctype`（被风控拦成 HTML）的响应重试。重试次数/间隔读 SharedPreferences（`api_retry_max_times`、`api_retry_interval_seconds`）。**注意调试桥的 `http` 命令不用这个默认重试**：它跑在广播的 `goAsync()` 窗口里，按 8s+16s×5 次重试会直接把广播拖成 ANR，所以另建了一个连接 5s / 读 8s 的短超时 client。
- **隐私模式**：`getJsonPrivacy()` 用 `buildGuestCookieString()` 剔除 `SESSDATA/bili_jct/DedeUserID/sid` 等登录 Cookie。
- **参数构造**：`FormData` 类，内部 `URLEncoder.encode`，默认**不**自动加 `access_key`（注释：web 接口带 access_key 会触发风控）。
- **连通性判断（26.10.25）**：`isNetworkAvailable(Context)`（`:693`）只做本地查询（`ConnectivityManager.getActiveNetworkInfo()`，不发请求、毫秒级），异常 / 取不到一律返回 `true`（fail-open，判断不出来就退回原来的失败路径）。故意用已废弃 API（`minSdk 19` 上不用 `NetworkCapabilities`）。目前唯一用途是启动页断网兜底（`SplashActivity`），**需要判断网络时别再另写一份**。
- **裸 deflate 解压**：`decompress(byte[])`（26.09.11 起为本工程唯一实现，原先
  `DownloadService`/`DownloadActivity`/`PlayerActivity` 各有一份逐字相同的副本）。
  解压失败回退原数据；`Inflater` 持 native 资源，统一在 `finally` 里 `end()`。
  注意与 `api/UserInfoApi.decompressResponse`（br + gzip）**不是同一件事**，不要互相替换。

### 6.2 WBI 签名（`api/ConfInfoApi.java`）

- `signWBI(url_query)`：取 `nav` 接口的 `wbi_img` → `img_key + sub_key` → 经 `MIXIN_KEY_ENC_TAB` 重排取前 32 位得 `mixin_key` → 参数排序 + `&wts=` + `mixin_key` 做 MD5 得 `w_rid`。
- `mixin_key` **每天只刷新一次**（`getDateCurr()` 写 SharedPreferences 的 `last_wbi`）。
- 结果缓存用 3 个 `volatile` 字段（`lastWbiQuery/lastWbiWts/lastWbiSignedUrl`），**非原子**——极端并发下可能读到"query 已换、签名未换"的组合。

### 6.3 API 层组织

`api/` 目录下约 41 个类，每个对应一个 B 站功能域，**全部**通过 `NetWorkUtil` 发请求、用 `org.json` 手工拆包。没有 Retrofit、没有数据类映射（除 `model/` 下少量 POJO）。

### 6.4 明文流量与凭据保护（26.09.13 加固，改网络/清单前必读）

三条要求，改动时别破坏：

1. **不新增明文接口**。`AppInfoApi` 原先 4 处 `http://api.biliterminal.cn` 已全部改为 `https://`（实测该域 https 正常返回 200）。新的自建接口一律用 https（26.10.09 新增的 `rebiliterminal.zsapp.asia` 就是 https，见 §6.5）。
2. **`AndroidManifest.xml` 不再全局开 `usesCleartextTraffic`**，改走
   `res/xml/network_security_config.xml`：`base-config` 禁止明文，只对 `bilibili.com` /
   `hdslb.com` / `bilivideo.com` / `afdiancdn.com` 放行。之所以不能一刀切禁明文，是因为解析层
   仍有若干 `"http:" + url` 的拼接（`LiveCardAdapter` 直播封面、`OpusParagraph` 图片）。
   `SearchApi` 的封面/头像已于 26.10.24 统一改走 `httpsUrl()`（`//` 开头才补 `https:`，已是绝对地址的原样保留），
   不再是"拼 http 再用白名单兜"的那一类。**若要把剩下的也改成 https，必须先在真机验证对应 CDN
   节点支持 https，再把域名从白名单移除。** 新增需要明文的域名要显式加进白名单并说明原因。
3. **登录凭据不得随备份外流**。所有 Cookie / 账号态存在 SharedPreferences，
   故 `android:allowBackup="false"`，并用 `res/xml/backup_rules.xml`（API ≤30）与
   `res/xml/data_extraction_rules.xml`（API 31+）排除 `sharedpref` 与 `database` 两个 domain。
   `:brotlij` 模块的清单自带 `allowBackup="true"`，因此主清单上必须带
   `tools:replace="android:allowBackup"`，否则清单合并直接失败。

> 组件导出面已收敛：除 `SplashActivity`（LAUNCHER）与 `GetIntentActivity`（外链/分享，
> 二者有 `<intent-filter>`）外，其余 Activity 一律 `android:exported="false"`。
> **新增 Activity 默认写 false**，只有确实要被外部应用拉起时才开，并说明理由。

### 6.5 自建反馈 / 公告 / 匿名统计（26.10.09 新增，动这一块之前必读）

这条链路和上游 `api.biliterminal.cn` 是两套服务器：域名、code 约定、已读计数都分开。

**但公告只有一个源**（26.10.23 起）：客户端不再直连上游公告接口；上游公告由本服务端用
标准库 `urllib.request` 拉取，管理员在控制台**逐条**审核后透传（`source=relay`），
未审核 / 被拒绝的对客户端不可见。客户端侧因此没有「两路合并」，也不再有「部分公告源连不上」的提示。

**服务端**（在本项目自己的 HK 服务器上，不在本仓库运行时里）

- 代码 `server/rebiliterminal_api.py`（FastAPI + SQLite，无 ORM）、`server/console.html`（管理控制台前端）、
  `server/selftest.py`、`server/backup.sh`、`server/requirements.txt`、`server/rebiliterminal-api.service`、
  `server/nginx/rebiliterminal.zsapp.asia.conf`、`server/nginx/console.zsapp.asia.conf`、`server/README.md`
- 部署形态：`/opt/rebiliterminal-api/`，systemd 单元 `rebiliterminal-api`，uvicorn 监听 `127.0.0.1:8788`。
  **两个域名共用一个进程**，靠 nginx 分工：
  - `https://rebiliterminal.zsapp.asia/terminal/` 只给客户端用（证书走 acme.sh，非宝塔站点表）
  - `https://console.zsapp.asia/` 是管理控制台，`/terminal/*` 在这里返回 404
  数据在 `/opt/rebiliterminal-api/data.db`；改服务端代码后要 `systemctl restart rebiliterminal-api`，
  **只改 `console.html` 不用重启**（服务端按 mtime 缓存，覆盖文件即可生效）；改 nginx 后
  `nginx -t && nginx -s reload`。
- 后台入口有三个，凭据任一即可：WebUI 登录（`console.zsapp.asia`，口令换 HttpOnly 会话 Cookie，
  前端零外部依赖、手写内联 SVG 图表）、旧的 `GET /admin?token=<REBILITERMINAL_ADMIN_TOKEN>` 纯 HTML 看板、
  或 `X-Admin-Token` 头。**WebUI 的 CSRF 防线**是 `SameSite=strict` + 状态变更接口强制
  `Content-Type: application/json`（两道互相独立）。
- 反馈 / 崩溃带「已处理」开关（26.10.11 加）：`GET /admin/api/feedback|crash[?pending=1]` 多回一个
  `handled`，`POST /admin/api/{feedback,crash}/handled` ← `{id, handled?}`。
  **它只是后台的阅读状态**——不删数据、不影响客户端上报（`handled` 列一直存在，此前无接口可改）。
- **单独回复**（26.10.14 加）：`POST /admin/api/feedback/reply` ← `{id, content, title?}`。
  做法是**把回复写成一条定向公告**（`announcements.target_install_id` / `target_device_key` 非空），
  并顺手把原反馈标成已处理。**客户端零改动** —— 可见性靠 `X-Install-Id` 头判定，
  而 `TerminalApi.headers()` 本来就一直带着它。
  - **可见性必须下推到 SQL 的 `WHERE`**（见 `_VISIBLE_SQL`）。若捞出来再在 Python 里过滤，
    `LIMIT 100` 会被「对当前用户不可见的定向公告」占满，该用户的公开公告就**永远拉不到**
    —— 游标不推进 → 每次查同一段 → 永久饥饿。`selftest.py` §17 专门钉这条。
  - 定向公告复用同一个 `ANN_ID_OFFSET` 和同一个客户端游标，**不需要独立 id 空间**：
    正因为可见性下推了，被过滤掉的 id 在该用户的查询里「根本不存在」。
- **删除**（26.10.14 加）：`POST /admin/api/{feedback,crash}/delete` ← `{id}`。硬删、不可逆，
  兜底是每日备份（保留 14 天）。**不给 `installs` 开删除口** —— 删安装行会让唯一设备数与
  当日新增回退，那是数据失真，不是清理。
- **设备视图**（26.10.14 加）：`GET /admin/api/devices[?coexist=&version=&brand=&q=&limit=&offset=]`。
  **按 `dev_key` 聚合，一行一台设备** —— 一台双版本手机在 `installs` 里是两行，平铺会被读成两台设备。
- **上游公告透传**（26.10.23 加，`SCHEMA_VERSION=4`）：客户端不再直连上游公告，改由服务端用标准库
  `urllib.request` 拉（`UPSTREAM_ANN_URL`，可用环境变量 `REBILITERMINAL_UPSTREAM_ANN_URL` 覆盖，
  超时 10s，跑在 `run_in_threadpool` 里）。`announcements` 加 `source`（`local`/`relay`，缺省 `local`）、
  `origin_id`（上游 id，仅 relay）、`relay_state`（`pending`/`approved`/`rejected`）；新增 `meta` 键值表存
  `relay_last_pull`。`relay_state='approved'` 才进 `_VISIBLE_SQL`（pending / rejected 对客户端与 `from`
  差量都不可见）；同一上游 `origin_id` 靠部分唯一索引
  `idx_announcements_origin ... WHERE source='relay'` 去重（**不能写进 `SCHEMA`**，老库那时还没有该列）。
  管理接口：`GET /admin/api/announcement/relay`（三组 + `last_pull`）、
  `POST .../relay/refresh`（拉取并 upsert，已存在条目只更新正文/时间、保留审核结论；**上游失败回
  HTTP 200 + `{code:1,msg}`，不抛 500**）、`POST .../relay/decision` ← `{id, decision: approve|reject}`
  （reject 对已透传项即「停止透传」，再次 approve 可恢复；refresh 不会复活 rejected）。
  控制台有独立页签「上游透传」（26.10.23 v5 起从公告页搬出，页签角标 = 待审条数）。
  **拉取不再依赖有人开控制台**：FastAPI `startup` 起一个 daemon 线程，每
  `REBILITERMINAL_RELAY_PULL_INTERVAL` 秒（默认 `3600`，`0` 关闭）拉一次；启动时按
  `relay_last_pull` 倒推等待（重启不多拉），从没拉过则 5s 后补，失败只 warning + 退避至多
  10 分钟、不写脏 `last_pull`。等待时间抽成纯函数 `_relay_wait_seconds(last, interval, now)`
  由 §22.9 钉边界。控制台打开页签时若 `last_pull` 超过 1 小时只是**兜底补拉一次**（前端多为 0 请求）。
  `GET /admin/api/announcements` 仍只回 `source='local'` 的行；
  其中 `reply_feedback_id>0` 的定向回复不再显示在「公告」页（前端过滤），删除入口改到「反馈」对话的
  开发者气泡旁（`POST /admin/api/feedback/reply/delete` ← `{id}`，只删 `reply_feedback_id>0` 的行，
  回复气泡的数据里带内部 `ann_id` 供回传）。`server/selftest.py` §22 钉住可见性/幂等/审核结论保持/
  失败降级/v3→v4 迁移/定时拉取边界，§16b 钉住回复删除与「回复删除接口不能删普通公告」。
  **上线口径（26.10.23）**：生产三件套原本停在 26.10.17 基线（比仓库工作副本多出 `/admin?token=` 303 →
  HttpOnly 会话 Cookie、控制台 UI 打磨等），本次把 relay 改动**合并进生产基线**后再部署；工作副本已同步为
  合并产物。以后改 `server/` 先 `ssh HK` 取生产文件再 diff，**别拿本地旧文件直接覆盖**。部署与冒烟记录见
  `docs/review/fix-progress.md` §3.56。
- 接口（成功一律 `code=0`，HTTP 恒 2xx）：
  - `GET /terminal/health`
  - `GET /terminal/announcement/get_list[?from=<id>]` → `{code:0,data:[{id,ctime,title,content,source}]}`。
    `source` 是 `"local"`（本项目公告）或 `"relay"`（上游透传，缺省按 `local` 解析）。
    `id` 是本地 `announcements` 自增 id **+ 1000000000**（本项公告与透传公告共用这一个 id 空间，
    透传行另存 `origin_id` 去重，id 早已和上游 id 空间错开）；
    不带 `from` 是列表页（倒序），带 `from` 是启动弹窗的差量（只回 `id>from`，正序）。
    **只返回 `relay_state='approved'` 的透传行**，pending / rejected 不可见；
    **会按 `X-Install-Id` 过滤定向公告**（公开公告人人可见）。
  - `POST /terminal/telemetry/ping` ← `{install_id, device_key, version_code, version_name, is_beta, sdk, brand, device, abi, is_coexist}`
  - `POST /terminal/feedback/submit` ← `{install_id, device_key, category, content, contact?, mid?, version_code, version_name, sdk, brand, device}`
  - `POST /terminal/upload/stack` ← 崩溃载荷（见下，含 `device_key`）

**数据与迁移（26.10.14 加）**

- 库结构变更走 `init_db()` 里手写的 `migrate()`（`PRAGMA user_version` 记进度），
  **没有 Alembic**。每步先探测现状再动手，**幂等**，跑一半挂了重跑不炸。
- 迁移前必须 `sqlite3 .backup` 备份（**不能用 `cp`**：WAL 下会丢 `-wal` 里的最近写入）。
- `daily_active` 的主键从 `(install_id, day)` 换成了 `(dev_key, day)`（SQLite 改不了主键，只能
  建新表→搬数据→换名）。搬的时候按当时的 `installs.device_key` 折算，查不到的回落 `install_id`，
  所以**迁移前后行数不变**。
- **统计口径**：版本分布 = **当前在用版本**的快照，升级后旧版本计数减少是**期望行为**。
  26.10.14 用户明确确认过，**不要**再引入 `install_versions` 那类历史版本表。
- 写入一律**空值不覆盖**（`_db_ping` 里的 `CASE WHEN`）：客户端某次上报空 `abi`，
  不该把库里的有效值刷成空 —— 那会让分布图凭空冒出 `(未知)` 分组、原分组同时 -1，
  这正是「统计异常波动」的主要来源。`is_beta` / `is_coexist` 同理用「没报就保留」，
  否则老客户端会把共存版误刷成普通版。

**客户端**

- `api/TerminalApi.java` 是这条链路唯一的出口：`BASE_URL = https://rebiliterminal.zsapp.asia/terminal`。
  请求与解析分离——`buildPingPayload` / `buildFeedbackPayload` / `buildCrashPayload` /
  `parseAnnouncements` 都是可在 JVM 上跑的纯函数，
  单测在 `app/src/test/java/com/RobinNotBad/BiliClient/api/TerminalApiTest.kt`。**改字段先改纯函数和测试。**
- **公告客户端链路**（26.10.23 改为单源）：`TerminalApi.parseAnnouncements` 解析 `source`
  （缺省 `local`）→ `model/Announcement.isRelayed()`；列表页 `activity/settings/AnnouncementsActivity.kt`
  只调 `getOwnAnnouncementList()`；启动弹窗 `TerminalApi.checkAnnouncement()` 仍按
  `SettingsKeys.TERMINAL_ANNOUNCEMENT_LAST` 做差量（两种公告共用该游标）。
  透传公告点开后走 `MsgUtil.showText(title, content, relayed=true)` → `ShowTextActivity`
  顶部多一行来源说明（`text_announcement_relay_source`「此公告来自原版哔哩终端」），
  **只在正文页标注**，列表卡片不标。
- `util/TelemetryReporter.kt`：启动时一天最多 ping 一次，**只有请求成功才写回日期**（断网启动不浪费当天机会）。
- `util/CrashReporter.kt` + `util/CrashTrail.kt`：`ErrorCatch` 在崩溃瞬间把 `exception/message/thread/trail/uptime`
  塞进 Intent → `CatchActivity`（独立进程）在 `isCrashReportAutoEnabled()` 时自动上报，
  **与上游那个手动上传按钮互相独立**（自动上报故意不套上游的「已登录 + 异常类型白名单」两道限制，
  否则最需要看的样本恰好会被挡掉）。
- 关于页 / 设置页入口：`activity/FeedbackActivity.kt` + `res/layout/activity_feedback.xml`。
  入口挂在**关于页**（`activity_setting_about.xml` 的 `@id/feedback_entry`）与设置「关于与帮助」
  分组（`SettingGroupActivity.buildAboutGroup`）。挂关于页是因为**菜单结构对老用户是缓存的，新分组不会自动出现**。
- **「我的反馈」页面（26.10.16 客户端已实现）**：接口 `GET /terminal/feedback/mine`，
  靠 `X-Install-Id` 认领，返回摘要字段 + `handled` 状态 + **整条对话**（`replies`）。另认可选 `X-Mid` 头
  （登录用户换设备 / 重装后仍能找回）——**只在用户勾选了「附带账号 ID」时才带**，
  与提交反馈同一口径，没勾选就只认本机。
  `mid` 走**请求头**不走查询串：uvicorn 的 access log 会记完整 URL，放查询串等于把 UID
  写进服务器日志文件。
  状态文案用「**待受理 / 已受理**」，**不要写「已处理」**：`handled` 是开发者的待办状态，
  「无法复现」「不是 bug」也会被标掉，写「已处理」就是承诺了它做不到的事。
  - **客户端落点**：`api/TerminalApi.getMyFeedback()` + 纯解析 `parseMyFeedback()`、
    `feedbackHeaders()`（`headers()` 的副本，按需追加 `X-Mid`；**必须显式传它，绝不可改用
    `NetWorkUtil.getJson(String)` 单参重载**——那个重载用 `webHeaders`，第一项就是 `Cookie`）；
    `model/FeedbackItem.java`（Parcelable）、`adapter/FeedbackListAdapter.kt`、
    `activity/feedback/FeedbackListActivity.kt`（列表）与 `FeedbackDetailActivity.kt`（对话时间线 + 追加说明）。
    入口挂设置页「关于与帮助 → 反馈与统计」分组（`SettingGroupActivity`）+ `SettingsIndex` 搜索索引
    （**不新增菜单项**——菜单对老用户是缓存的，新菜单 key 不会自动出现）。
  - 服务端实现与字段表见 `server/rebiliterminal_api.py`（注意 `server/` **不入版本库**，
    见 `.gitignore`，只部署在服务器上）。
- **反馈对话：两侧消息合成一条时间线（26.10.16 新增）**。一条反馈下面现在**两侧**都有消息，
  `replies` 里按 `ctime` 交错返回，每项带 `author` 区分：
  - `author = "dev"` → 开发者回复，存在 `announcements`（`reply_feedback_id` 关联）。
    **沿用定向公告这条老路**：它顺带承担**启动弹窗通知**，动它风险大且收益为零。
  - `author = "user"` → 用户追加的说明，存在 `feedback_comment` 表。不对外可见、不参与公告差量。
  - **合并逻辑只有一份**（服务端 `_db_feedback_thread`），客户端 `/feedback/mine` 与管理台
    `/admin/api/feedback/detail` 的 `thread` 共用 —— 两边各写一份必然漂移。
  - **追加接口 `POST /terminal/feedback/reply`**（body `{id, content}`，身份全走请求头）：
    - **归属判据与 `/feedback/mine` 共用同一份 SQL 片段 `_MINE_SQL`**。两边一旦不一致，
      症状是「列表里看得到、却回不了」——用户只会觉得功能坏了，没人会想到是两条 SQL 漂移。
    - **不是自己的反馈与「不存在」返回同一个 `code=404`**：区分开就等于给了一个枚举 id 的探针。
    - **副作用：把 `handled` 置回 0（待受理）**。GitHub 的 reopen 语义 —— 开发者的
      `?pending=1` 列表与「待处理」计数会自动把它顶上来，**不必另建未读 / 提醒机制**。
    - 限流 20 次/时；内容上限 2000 字符（比首条反馈的 4000 小 —— 追加说明是补充，不该另起一篇）。
    - **没有删除接口**：用户删掉自己说过的话会让开发者看到的上下文缺失、排查到一半线索断掉。
  - **客户端**：`TerminalDialog.input()` 负责弹窗输入（多行框 + **固定底栏**按钮，输入框会长高，
    按钮跟着滚会被顶出可视区）；`TerminalApi.submitFeedbackReply()` 走 `postWithHeaders(..., feedbackHeaders())`
    —— **其余接口仍用 `headers()`**，不能把 mid 加进通用请求头（那等于对所有接口默认带 UID）。
    `FeedbackReply.author` / `isFromUser()` 区分两侧；**旧服务端不返回 `author`**，缺失 → 非用户，
    正好等于升级前「只可能有开发者回复」，不需要兼容分支。
  - **失败不能丢内容**：`TerminalDialog.input` 的 `onConfirm` 在 `dismiss()` **之后**才回调，
    拿不到「失败了就别关」这个中间态，所以失败时**重开弹窗并预填**（与 `FeedbackActivity`
    失败保留输入同一原则）。
- **日志能力（26.10.16 新增；崩溃 / 反馈「附带日志」的地基）**：`util/Logu.java` 的**内存环形缓冲**是 release 包唯一能拿到历史日志的手段。
  - **只记 W/E/F 级别**：`Logu.w/e/wtf` 本来就恒执行、本来就调 `getCaller()`，在这条路上做缓冲
    **不新增开关、不新增栈遍历，release 行为零变化**；`v/d/i` 受 `LOGV/LOGD/LOGI_ENABLED` 保护、
    release 下根本不执行——「详细模式」一开它们自动进缓冲，不需要额外逻辑。
  - **不落盘**：release 包 `LOG_TO_FILE = agentMode && …` 恒 false（`BiliTerminal.applyLogSwitches()`），
    磁盘日志只在 debug / agent 模式有；内存缓冲是不改这条前提就能拿到历史日志的唯一途径。
  - **容量上限 `RING_MAX_CHARS = 65536`（约 128KB 内存）**：`ArrayDeque<String>` + 累计字符数，
    超限从头部逐条淘汰（常数时间，不做数组搬移）；读写一律 `synchronized (RING)`
    （全工程 269 处 `Logu.*` 调用点可能来自任意线程）。**上限由 Binder 约束定**：
    崩溃快照要经 Intent extra 跨进程传给 `:error_activity`，Binder 事务上限约 1MB。
    API：`ringSnapshot()` / `ringChars()` / `ringClear()`。
  - **脱敏在 `util/LogSanitizer.kt`**（Kotlin object + `@JvmStatic`）：对任意文本按 `键=值` 模式脱敏
    （敏感键 → `***`，噪音键 → `…`），**不解析 URL**（日志行是任意文本）；有**快速路径**
    （先查 `键名=` 是否出现，没有就直接原样返回、不跑正则）；正则按**键名长度降序**拼接，
    避免 `token` 抢先匹配 `refresh_token` 前缀。键名清单直接复用
    `NetWorkUtil.SENSITIVE_QUERY_KEYS` / `NOISE_QUERY_KEYS`（这两个常量因此由 `private` 改 `public`，
    `maskUrl()` 逻辑一字未动）。
  - **`Logu.toFile()` 的顺序**：先脱敏 → 再 `shorten()` → 同时进内存缓冲与落盘。
    **先截断再脱敏会让超长行的凭证只被截掉一半、脱敏不完整**，别调换。
  - **崩溃路径跨进程那条坑**：`ErrorCatch` 在崩溃瞬间把 `intent.putExtra("ring_log", Logu.ringSnapshot())`
    带过去（与既有 `trail` 同一条路，只取值不做采集）；**`CatchActivity` 跑在 `:error_activity` 独立进程，
    那边的 `Logu` 缓冲恒为空**，所以它只能读 `intent.getStringExtra("ring_log")`、
    **不能**直接调 `ringSnapshot()`。
  - **合并进同一个 `log` 字段**：`util/CrashReporter.kt` 的 `buildPayload` / `upload` 加 `ringLog` 参数，
    用 `mergeLog()` 把页面轨迹与应用日志合并进同一个 `log`，中间是分隔行
    `\n\n--- 应用日志（内存缓冲，最近若干行）---\n`；任一段为空时不留孤零零的分隔符。
    **复用同一字段而非新增** → 服务端 `crash.log` 上限 200000 字符够用、**服务端零改动**，
    老数据（只有轨迹）与新数据在库里同构。
  - **反馈路径**：`TerminalApi.buildFeedbackPayload` / `submitFeedback` 加 `log` 参数
    （服务端该字段**可选**，老客户端不发就是空串）；本地导出走 `util/LogExporter.kt`
    （子线程把 `ringSnapshot()` 写成应用私有 `cache/terminal/rebili-log-yyyyMMdd-HHmmss.txt`，
    经 FileProvider + `ACTION_SEND` 分享）。
  - **`ringClear()` 的取舍**：只在「勾选了附带」且「提交成功」时清空——没勾选就没送出去、
    失败要留着重试；勾选 + 成功说明这份日志已被用户授权送出。
- **隐私同意闸门**（26.10.09 二次确认）：启动时 `SplashActivity.proceedSplashFlowWithPrivacyGate()`
  先看 `TerminalApi.needsPrivacyConsent()`，为真就弹一次 `TerminalDialog.choice`（`setCancelable(false)`，
  两条路都继续启动，不拦人）。状态由两个键**分开**表示：
  `SettingsKeys.PRIVACY_CONSENT_VERSION`（同意到哪个版本 → 决定**能不能上报**）与
  `SettingsKeys.PRIVACY_PROMPTED_VERSION`（表过态到哪个版本 → 决定**还要不要弹**）。
  分开的理由：「不同意」也是一次回答，不该每次启动再问；而同意版本号保持原值，
  就自然实现了「App 照常用，只是不上报」。于是：
  `isTelemetryEnabled() = hasPrivacyConsent() && getBoolean(TELEMETRY_ENABLE, true)`，
  `isCrashReportAutoEnabled()` 同理——**没同意就绝不上报这件事不依赖任何调用点自觉**，闸门收在 `TerminalApi` 里。
  设置页里用户**主动打开**开关时回调 `recordPrivacyConsentFromSettings()`（打开即重新同意，
  且只记版本号、不碰另一个开关）。**改隐私文案就要把 `TerminalApi.PRIVACY_VERSION` +1**，
  否则老用户不会被再问一次。单测：`app/src/test/java/com/RobinNotBad/BiliClient/api/TerminalPrivacyConsentTest.kt`。
  - **设置页开关的写入顺序不能反**：`activity/settings/SettingsAdapter.kt:201-207` 是**先写
    SharedPreferences、再回调 `onChange`**，所以 `onChange` 里读到的已经是新值。依赖这个顺序的代码
    （如 `recordPrivacyConsentFromSettings()`）不要改成「先回调、后写入」。
  - **「不同意」的提示为什么弹在新页面上**：「不同意」的提示走 `MsgUtil.showMsgLong` 的 sticky
    `SnackEvent`：闪屏（`SplashActivity` 是 `Activity`，**不是** `BaseActivity`）不消费事件，
    由下一个 `BaseActivity` 在 `onResume` 取出显示并 `removeStickyEvent`，于是刚好弹一次、弹在新页面上。
    **是有意为之，别改成 Toast。**

**红线（改这里时不要踩）**

1. **绝不复用带 Cookie 的重载**。`NetWorkUtil.getJson(String)` / `postJson(String,String)` 单参重载用的是
   `webHeaders`，第一项就是 `Cookie`，会把 SESSDATA 发到自建服务器。自建请求必须显式传
   `TerminalApi.headers()`（是 `AppInfoApi.customHeaders` 的**副本**，加 `X-Install-Id` 后使用；
   用副本是为了不污染上游那份共享静态列表）。
2. **隐私承诺与开关必须一致**。加遥测时同步改了两处用户可见文案：
   `strings.xml` 的 `about_to_uncle` 与 `text_setup_introduction`（原本写的是「不会收集任何…隐私信息」），
   并且加了一道**可执行的**同意闸门（见上一条「隐私同意闸门」，`TerminalApi.PRIVACY_VERSION`）。
   三个开关 `SettingsKeys.TELEMETRY_ENABLE` / `CRASH_REPORT_AUTO` / `FEEDBACK_ATTACH_MID`
   默认分别是 `true` / `true` / `false`，都能在设置里关。**以后再往上报载荷里加字段，
   先把 `about_to_uncle`、`text_setup_introduction`、`privacy_consent_message`、以及三条
   `desc_setting_*` 说明一起改掉，并把 `PRIVACY_VERSION` +1。**
   26.10.14 加 `device_key` 时就是这么办的（`PRIVACY_VERSION` 1 → 2）：那三处原文写的是
   「不读取 ANDROID_ID 等任何设备唯一标识」，而新方案正是拿它派生哈希，属于实质变化，
   不改文案就是自相矛盾。改完老用户会重看一次同意弹窗，这是**有意的**。
3. **账号 ID 默认不带**。`mid` 只在 `FEEDBACK_ATTACH_MID` 打开时才有值，关掉时传 `0`
   （服务端那条记录里根本不会有这个值，不是「传了但不显示」）。
   **服务端侧同样不存 IP、不存 Cookie**：IP 只用于限流计数、**不入库**。服务端自身的说明在
   `server/README.md`——注意 `server/` 自 26.10.14 起**已移出版本库**（见 `.gitignore`），
   只部署在服务器上，所以改服务端时别指望从仓库里能找到它。
4. `install_id` 是首次调用 `getInstallId()` 时生成的随机 UUID（`SettingsKeys.TELEMETRY_INSTALL_ID`），
   与账号无关，卸载重装即换新。
   **26.10.14 起另有 `device_key`**（`TerminalApi.getDeviceKey()`，存 `SettingsKeys.TELEMETRY_DEVICE_KEY`）：
   它是 `sha256(ANDROID_ID + 固定盐)` 的截断值，**只上报哈希，原始 ANDROID_ID 不上报也不落库**。
   存在的唯一理由是普通版与共存版包名不同、`install_id` 天然是两个值，必须有设备级标识才能合并成一台。
   两个 flavor 用同一签名 → 系统给的 `ANDROID_ID` 相同 → 哈希相同。
   **若将来两个 flavor 换成不同签名，这个去重会立刻失效**，换签名前先想清楚。
   取不到就返回空串，服务端 `dev_key_of()` 回落 `install_id`，统计不中断。
   盐是 `TerminalApi.DEVICE_KEY_SALT`，**改它等于把所有设备变成新设备**（唯一设备数会翻倍）。
   纯函数 `hashDeviceKey()` 可在 JVM 单测里直接验证形状（不碰 Android 运行时）。
5. 启动链路里 `TerminalApi.checkAnnouncement()` 与 `TelemetryReporter.reportIfNeeded()` 都**各自包了异常**：
   自建服务器挂了不能影响启动（`SplashActivity` 那一整段本来就有「任何失败都退化成进本地列表页」的约定）。
6. **服务端别把 sqlite3 直接写在 `async def` 里**。业务函数是 `async def`（要 `await request.body()`），
   但 sqlite3 是阻塞库 —— 所有库访问都走 `run_in_threadpool`（`server/rebiliterminal_api.py` 里
   `_db_*` 那批同步函数就是干这个的）。否则一旦撞上 `busy_timeout=15000`，整个事件循环卡 15 秒，
   连健康检查都不响应。同理 `connect()` 里的 `PRAGMA synchronous=NORMAL` 是写吞吐的关键，别删。
7. **控制台前端不引任何外部资源**（无 CDN、无图表库），否则国内外网一抖后台就打不开；
   口令只出现在 `POST /console/login` 的表单里，会话 Cookie 是 `HttpOnly`，
   前端 JS 读不到、URL 里也不带。旧 `?token=` 入口保留但只作兜底。
8. **公告只有一个源，别再引回上游直连**（26.10.23）：上游公告接口由服务端接、管理员逐条审核后
   透传；客户端 `AppInfoApi` 里的公告方法与 `TerminalApi.mergeAnnouncements` 已全部删除。
   改公告相关代码时**别恢复「两路合并」或「部分公告源连不上」的提示**，那是旧口径。

---

## 7. 改功能前必须知道的坑（按"会不会踩到"排序）

### 7.1 本轮已修复（26.09.08，已验证，别再当 bug 修一遍）

| # | 位置 | 原问题 | 修法 |
| --- | --- | --- | --- |
| 1 | `api/PrivateMsgApi.java` | `!has && isNull` 恒等于 `!has`，`account_info` 为 null 的普通会话被误过滤 | 改用 `isNull("account_info")`；解析抽成纯函数 `parseSessionsList()` 并补单测 |
| 2 | `activity/base/InstanceActivity.kt` | MENU 键打开菜单后继续走 `super`，被 `BaseActivity.onKeyDown` 又 `finish()` 当前页 | 消费按键，`return true` |
| 3 | `activity/base/InstanceActivity.kt` | `from` 参数判的是刚 new 的空 Intent，恒 false | 改判 `getIntent().getStringExtra("from")` |
| 4 | `util/NetWorkUtil.java` | `saveCookiesFromResponse` 无锁，与 `putCookie`/`setCookies` 的 `synchronized` 不对称 | 抽出 `saveCookiesLocked()`，纳入同一把 `NetWorkUtil.class` 锁 |
| 5 | `api/ConfInfoApi.java` | WBI 缓存三个 `volatile` 分开赋值，可能读到"query 已换、签名未换" | 合并为不可变 `WbiCache` 对象，整体替换 |
| 6 | `util/MsgUtil.java` | sticky `SnackEvent` 显示后不移除 → 注册时收一次、`onResume` 再取一次，同页弹两次 | `processSnackEvent` 显示分支同时 `removeStickyEvent` |
| 7 | `util/TerminalContext.java` | 视频缓存 aid/bvid 键不一致，bvid 路径**永远 miss** | 新增 `cacheVideo()` 双写两个键；`LruCache` 10→20 保持等效容量 |

**验证**：`:app:testDebugUnitTest` 38 个测试全绿（新增 `PrivateMsgApiTest` 6 例）+ `:app:assembleDebug` 通过。
顺带修正 `app/src/test/.../ToolsUtilTest.kt` 的期望值笔误（`0x00123456` 剥离 alpha 应为 `0x123456`，原写成 `0x000000`）。

### 7.2 更早的修复（26.08.27 快照之后，别照旧报告去改）

- `RefreshListActivity.kt:356-361`：`setRefreshing(false)` 时复位 `isLoading`（注释说明了原因）。
- `RefreshMainActivity.kt:147-156`：`goOnLoad()` 同步置成员 `isRefreshing`，`onScrolled`（`:61`）用它防重入。
- `ToolsUtil.java:71-73`：`getRgb888` 已改为 `color & 0xFFFFFF`（注释保留旧实现说明）。
- `NetWorkUtil.java:100-113`：重定向前判空 scheme/host、先 `response.close()`。
- `PlayerActivity` / `UpdateManager` / `LocalListActivity` / `VideoInfoFragment` 等见 `docs/archive/review/fix-progress-history.md` 第二节。

**建议**：改任何文件前先 `grep` 一下对应代码是否还是旧报告里描述的样子——旧报告基于 `26.08.27` 快照，之后有 3 轮修复。

### 7.3 结构性风险（改功能时容易放大）

- **巨型类**：`activity/player/PlayerActivity.kt` 166 KB / 3628 行、`activity/video/ShortVideoPlayerActivity.kt` 43 KB / 1017 行（26.10.10 实测）。`service/DownloadService.kt` 曾是第二条 65 KB 的巨型类，26.10.04 批次 8（E2）已拆到 **1284 行**，实现分居 `service/download/` 的 4 个文件（见 §7.27）。改播放相关功能前先想清楚在哪个位置插入；改下载相关功能请先读 §7.27 的锁与状态契约。
- **Application 静态状态已收敛为一套**：26.10.02 起只有 `BiliTerminal.context`（`BiliTerminal.kt:47-48`，伴生对象 `@JvmField`，静态字段）与 `instance`（`:53`，`private WeakReference<InstanceActivity>`，只能经 `setInstance()`(`:85`) / `getInstanceActivityOnTop()`(`:90`) 访问），`BiliTerminalApp` 已整文件删除。**新代码一律用 `BiliTerminal`**。
- **测试覆盖仍偏低**：`app/src/test/` 94 个 Kotlin 测试文件 + 1 个 Java 测试文件（`StringUtilTest.java`）+ 1 个共享假实现 `FakeSharedPreferences.kt`，**104 个测试类**（`CalculatorEngineTest.kt` 里有 `CalculatorEngineTest` 与 `DisguisePrefsTest` 两个类），共 **1108 个用例**（26.10.30 合并上游后实测，两个 flavor 各 1108、0 失败）。已有：`HotSearchApiTest`、`FavoriteApiTest`、`FollowApiTest`、`OpusApiTest`、`PrivateMsgApiTest`、`BangumiApiTest`、`DynamicApiTest`、`TopicApiTest`、`NoteApiTest`、`WatchLaterApiTest`、`PlayerApiPbpTest`、`ReplyApiTest`、`ReplyParseActionTest`、`ReplySortTest`、`ReplyTranslateApiTest`、`ReplyTranslationTest`、`ReportApiTest`、`NetWorkUtilTest`、`PerformanceManagerTest`、`BangumiUpdateCheckerTest`、`MsgNotifierTest`、`ApkVerifierTest`、`SettingsKeysTest`、`UpdateReleaseTest`、`MenuConfigTest`、`MySpaceConfigTest`、`ToolsUtilTest`、`StringUtilTest`（Java）、`HotSearchAdapterTest`、`TutorialDslTest`、`MediaClockSyncTest`（DFM 侧纯函数，源在 `DanmakuFlameMaster`）、`CalculatorEngineTest`、`DisguisePrefsTest`、`SkipCancelPolicyTest`、`PlayerDefaultsTest`、`ViewPointSkipTest`、`ColorSchemeTest`、`CornerStyleTest`、`FontStyleTest`、`AppearanceManagerTest`、`ViewCapabilityProbeTest`、`DownloadPathSpecTest`、`DownloadProgressMathTest`、`DownloadBatchStatsTest`。改动解析/配置/主题/能力探测逻辑时补纯 JVM 单测——注入 `SharedPreferencesUtil.sharedPreferences`，用 `util/FakeSharedPreferences.kt`（26.09.11 从 `NetWorkUtilTest` 的私有内部类提取为共享助手，别再抄一份）。
- **主题色表带缓存，失效点只有一处**：`ColorScheme.getCurrentTheme()`（26.09.11 起）缓存当前色表，**只由 `AppearanceManager.setTheme()` 经 `ColorScheme.invalidateCache()` 置空**。这是刻意的——36 个属性 getter 全走它，而列表滚动时一个 item 要调多次，此前每次都重读 SharedPreferences（热路径重复 IO）。**若将来给主题 key 增加第二个写入路径（比如直接 `SharedPreferencesUtil.putString(SettingsKeys.THEME, …)`），必须同步调用 `ColorScheme.invalidateCache()`，否则改主题后色表不跟着变且在 `onResume` 重建后依然错**。守卫测试：`ColorSchemeTest.themeCache_isInvalidatedOnEverySetTheme`、`colorGetters_doNotTouchSharedPreferencesAfterFirstRead`。
- **主题体系有 3 个"裸 Activity"不参与**：`SplashActivity`、`GetIntentActivity` 不继承 `BaseActivity`（开屏/外链恒定 B站粉），`PlayerActivity` 自己 `setTheme` 但**不调 `applyWindowTheme`、也不参与 `onResume` 主题检测**。改主题相关行为时别以为全局都生效了。
- **文案硬编码**：遗留页面标题/Toast 直接写中文字符串（Manifest 里 `android:label` 也是中文），只有设置页用 `desc_*` 资源。改文案按现有风格来，别顺手抽 `strings.xml`。

### 7.4 两个视频播放器是两套独立实现（改播放前必读）

工程里**没有统一的播放层**，两个播放器各写各的，共用代码只有 `player/IjkPlayerBridge.kt` 这一小块：

| | 普通视频播放器 | 短视频播放器 |
| --- | --- | --- |
| 入口 | `activity/player/PlayerActivity.kt`（**3628 行**） | `activity/video/ShortVideoPlayerActivity.kt`（1017 行） |
| 基类 | **直接 `extends Activity`**，不走 `BaseActivity` 体系（自己实现主题、EventBus、横竖屏） | `InstanceActivity` + `ViewPager2` 竖滑翻页 |
| 播放内核 | **裸 `IjkMediaPlayer`**，`setOption`/`setDataSource`/监听器全部内联在 Activity 里 | `player/IjkPlayerBridge`（Flow 驱动状态）+ `player/DanmakuManager` |
| 弹幕 | `IDanmakuView` 内联处理（`streamDanmaku`/`createParser` 都在 Activity 里） | `DanmakuManager` |
| 轮询 | 3 个 `java.util.Timer` 线程（在线人数 5s / 倍速收起 / surface 就绪）+ 2 个主线程 `Handler` 自循环（播放进度 250ms、缓冲速度 500ms） | `IjkPlayerBridge` 内协程，250ms 更新 StateFlow |
| 预加载 | 无 | `util/VideoPreloadManager`（**只预取播放地址 URL，不预热播放器**） |

`player/` 包里**有一半是死代码**（全工程 0 引用，已核实）：

- `IjkPlayerBridge.kt` — 在用，被短视频的 `PageHolder` 直接使用。
- `DanmakuManager.kt` — 在用，**两个播放器都已接入**（26.09.10 起 `PlayerActivity` 的内联弹幕栈已删除，统一走它）。
- `PlayerSurfaceBinder.kt`（26.09.10 新增）— 在用，Surface 就绪事件回调，取代 200ms 轮询 Timer。
- `VideoPlayerCore.kt`（26.09.10 新增）— **已可用但尚无消费方**，是合并两个播放器的目标内核（多实例安全、不持 Activity、`reload()` 复用播放器）。
- `PlayerIntegrator.kt` — ~~死代码~~ **26.09.11 已删除**（全工程无一处实例化）。
- `PlayerControlDelegate.kt`（含 `GestureHandler`）— **仍未删除，待决策**：`PlayerIntegrator`（它唯一的引用方）已删，此后它全库零外部引用；但播放器合并方案 S6 明确把它列为"**决定去留**"（`docs/archive/superpowers/specs/2026-09-10-player-core-merge.md:63`），且它带着 26.09.10 的手势 bug 修复（`isLongPressing` 复位），**删之前先定方案**。
- `PlayerScaleMode` 枚举（原在 `IjkPlayerBridge.kt`）— **26.09.11 已删除**（无人使用）。
- `DanmakuManager.loadFromProtobuf` / `toggleVisibility` / `setSpeedFactor` / `setTextSizeScale` / `setTransparency` — **26.09.11 已删除**（全库零调用；`loadFromProtobuf` 实现还是坏的：解压后从未把数据交给 parser）。连带 `PlayerSurfaceBinder.cancelAwait`、`DanmakuManager` 的 `Inflater`/`ByteArrayInputStream` import 一并删除。

**改播放相关代码时的判断顺序**：先确认要改的是"普通播放器那套"还是"短视频那套"。合并方案（已归档：`docs/archive/superpowers/specs/2026-09-10-player-core-merge.md`）S1/S2/S3 已完成，S4、S5 后半与 `PlayerControlDelegate` 去留仍未做，待办登记在 `docs/review/fix-progress.md` §2.2；**当前 `player/` 包仍不是公共层** —— `PlayerActivity` 只用了 `DanmakuManager` 与 `PlayerSurfaceBinder`，播放器本身还是裸 `IjkMediaPlayer`。

**弹幕序列化的两个硬约束（踩过坑，别重犯）**：

1. **`DanmakuManager.createParser()` 只能在主线程、且不可并发**。`DanmakuLoaderFactory.create(TAG_BILI)` 返回的是**进程级单例** `BiliDanmakuLoader.instance()`（`BiliDanmakuLoader.java:27-38`），而 `dataSource` 是它的**实例字段**（`:29`），`load()` 写字段 + `loader.dataSource` 读字段是一段 check-then-act。并发时两个 holder 会拿到同一个数据源，表现为弹幕串台或解析为空。
2. **`createParser()` 不做任何 XML 解析**，把它挪到后台线程**没有任何收益**。`AndroidFileSource(InputStream)`（`AndroidFileSource.java:44-46`）、`BiliDanmakuLoader.load()`（`:44-46`）、`BaseDanmakuParser.load()`（`BaseDanmakuParser.java:67-70`）全都只是存引用；真解析在 `getDanmakus()` → `parse()`（`:81-89`）**懒执行**，全工程唯一调用点是 `DrawTask.java:283`，跑在 `DanmakuView` 自己的渲染线程上。所以"短视频滑动卡顿 = 弹幕解析阻塞主线程"是**错误归因**（26.09.10 已核实并回退过一次这样的改动）。

普通播放器与短视频的弹幕栈是两套（前者内联，后者走 `DanmakuManager`），统一它们是合并两个播放器时**风险最低、收益最高的第一步**，但要先解决上面第 1 条的并发约束。

**26.10.13 本地音轨防重（B1 播放侧）**：`PlayerActivity.onPrepared` 仅消费当前 IJK 实例回调，使用其 `trackInfo` 而不是文件存在性判断内嵌音轨，纯判据在 `player/LocalAudioTrackPolicy.kt`。有 audio 轨 → 只播主文件；确实有 video 轨且无 audio 轨、外部文件可读非空 → 用唯一的外部 MediaPlayer；null/空/不可识别轨道 → UNKNOWN，提示而非盲目启动第二路。**不得把解析失败当作「没有音轨」，也不得在播放时删缓存**。`releaseAudioTrack` 先摘引用再释放，重载/切听视频/重试/切页/出错/销毁清理；MediaPlayer 创建后先保存引用再 prepare，初始化或异步错误可释放，切换来源清空旧 `audioTrackUrl`。不依赖 MediaExtractor，不增网络或文件扫描；真实 IJK 轨道解析及双播放器生命周期仍需手表验证。

**26.10.12 双击与旋转解耦**：`PlayerActivity.clickUI()` 与双击快进手势的中间区域统一走 `handleDoubleTapAction()`，判据抽在 `activity/player/PlayerDoubleTapPolicy.kt`（纯 JVM 单测）。只读取 `layout_video.scaleX/scaleY` 的实际比例，**方向不参与判定，复位分支只还原缩放与居中位置，不能写 `screen_landscape` / `requestedOrientation` 或调用 `softwareRotate()`**。`PLAYER_DOUBLETAP_RESTORE_SCREEN` 保留旧存储键，但语义改为“有缩放时，双击优先恢复缩放”；关闭时双击播放/暂停。`ViewScaleGestureListener.can_reset` 已删除，不再维护易过期的复位标志。左右两侧双击快退/快进与直播不暂停的既有行为保留。

**26.10.20 左右滑动调进度的缩放判据**：`PlayerActivity.handleSwipeSeekTouch()` 的「未缩放视频才生效」不能读 `gesture_scaled`——那个标志只表示「本轮触摸里捏合过」，ACTION_UP 一到就复位，捏合放大后松手会把调进度重新放行（26.10.20 修的正是这个 bug）。判据抽在 `activity/player/PlayerGesturePolicies.kt`：`PlayerZoomPolicy.isZoomed(scaleX, scaleY)`（ε=0.001f）是「画面是否仍缩放」的唯一判据；`PlayerSwipeSeekPolicy.isSeekAllowed(...)` 汇总「设置开 + 已准备 + 非直播 + 播放器就绪 + 未缩放 + 未长按加速」；`PlayerDoubleTapPolicy` 也复用 `PlayerZoomPolicy`。`PlayerActivity.isVideoZoomed()` 读 `layout_video.scaleX/scaleY` 的实际比例，单指拖动闸门同样按实际缩放与 `SettingsKeys.PLAYER_DOUBLEMOVE` 判定。`gesture_scaled` 只剩「吞掉手势结束那次点击」一个用途，别给它加语义。

**26.10.30 长按三倍速期间不消费横滑**：`handleSwipeSeekTouch()` 把 `PlayerActivity.onLongClick` 作为 `PlayerSwipeSeekPolicy.isSeekAllowed(...)` 的 `longPressBoost` 参数传入，按住加速时横滑被门闸拒绝并 `cancelSwipeSeekIfActive()`。**`onLongClick` 的复位必须覆盖 ACTION_UP 与 ACTION_CANCEL**（`PlayerActivity.endLongPressBoost()`，两套 `layout_control` 触摸分支都接到）：只按 UP 复位时，触摸被父容器/失焦 CANCEL 后标志卡 true，会把滑动调进度永久锁死、倍速也停在 3 倍。

**26.10.11 起的网络韧性与状态恢复（§四十四，明细见 `docs/review/fix-progress.md` §3.4）**：

- `PlayerActivity.setDisplay()` 的在线流选项现在含 `rw_timeout=10s` / `addrinfo_timeout=5s` / `reconnect_streamed=1` / `reconnect_delay_max=5s`（点播另开 `enable-accurate-seek=1`）。**别把 `rw_timeout` 当可选项删掉**：默认 0 = 读操作可永久阻塞且永不回 `onError`，弱网黑屏的直接原因就是它；`reconnect=1` 单独对 chunked 直播无效（ffmpeg `http.c` 有 `is_streamed && !reconnect_streamed` 就 break 的判定）。
- 新增 `util/live/LiveStreamSelector.kt`（**纯函数 + 14 个 JVM 单测**，放 `util/live/` 而不是 `player/`，因为后者不是公共层）：把「直播取流」从三层硬取 `stream[0].format[0].codec[0]` 改成按「FLV 各线路 → HLS」稳定排序的候选列表；`activity/live/LiveInfoActivity.kt` 三处消费它，候选经 Intent `live_url_candidates`（`model/PlayerData.java` 新增字段 + Parcel）交给 `PlayerActivity` 做运行时线路轮换。
- `PlayerActivity` 新增停滞看门狗 `checkStall()`：挂在 `progressChange()` 的 250ms 循环里判「位置是否 N 秒不前进」，直播 20s / 点播 30s 触发重试（直播换候选线路、点播原地 `retryAfterPlayerError()`），最多 3 次、冷却 15s。`retryAfterPlayerError(resumePositionMs)` 有默认参数，旧调用点无需改动。
- 位置恢复现在有三条入口：`onSaveInstanceState`/`STATE_POSITION`（系统回收后重建）、`onSurfaceReattached` 的 `seekTo(video_now)`（切后台回来 Surface 重建）、`onPrepared()` 的 `resumeTarget`。**三处的共同前提是「seek 目标取 `video_now` 或存档值，绝不取 `seekbar_progress.progress`」**——旧写法在直播下该值恒为 0，等于每次回前台把直播 seek 回起点。

3. **（26.10.22 起口径）弹幕时钟由播放器位置驱动，`DanmakuSync` 与周期性 `seekTo` 已作废**。现行做法见 §7.51：`DanmakuManager.bindMediaClock()` 把 `video_now` 交给 DFM 的 `DrawHandler.syncTimerByMediaClock()`（纯逻辑在 `MediaClockSync`），时间轴全程跟随画面，**不做任何周期性纠偏**；`DanmakuTimer` 仍不许在 `updateTimer` 回调里直接 `timer.update(pos)`（`mTimeBase` 不随之改变，会被下一帧拽回）。任何「跳转视频」的入口都必须同时更新 `video_now`（`PlayerActivity.seekToVideo()` / `markSeekTarget()`），否则媒体时钟会继续按旧位置推进。
   - 仍然成立的那半条：**绝不能在 `DanmakuView` 的渲染线程上读 `IjkMediaPlayer.currentPosition`**（取 native 锁，与主线程 seek/release 并发有卡死风险）——所以对齐一定在主线程做。旧的「必须返回负数守卫」随回调一起删除，但「窗口期不把脏位置灌进 `DanmakuTimer`」这个教训由新写法天然满足（主线程只在 `isPrepared && isPlaying && !isSeeking` 时对齐）。

---

### 7.5 后台播放服务（`service/PlaybackService.kt`，26.09.25 新增）

`PlayerActivity` 退到后台且用户开了设置项 `player_background`（字面量，见 `SettingsKeys.PLAYER_BACKGROUND`）时，
`onPause` 会通过 `PlaybackService.start(this, this)` 起一个 `foregroundServiceType="mediaPlayback"` 的前台服务挂通知栏遥控。

- **播放器实例仍归 `PlayerActivity` 持有**，Service 只通过 `WeakReference<PlayerActivity>` 反查状态与下发指令
  （`serviceTogglePlay`/`serviceStopPlayback`/`serviceReportNow` 等桥接方法定义在 `PlayerActivity` 里）。这不是 MediaSession 方案：
  通知是普通 `NotificationCompat.Builder` + 两个 `PendingIntent.getService` 自定义 action。
- **通知 id 1028（播放）、1027 是 `DownloadService`**；渠道 `playback_channel` 与下载的 `biliterminal_download` 分开。
- **「5 秒义务」是本节最容易踩死的一条（26.10.11 因此崩过线上）**。`Context.startForegroundService()` 一旦调用，
  系统就给这次启动记一笔账：服务必须在 5 秒内 `startForeground()`，否则向主线程抛
  `RemoteServiceException: Context.startForegroundService() did not then call Service.startForeground()`，**整个进程崩掉**。
  两条硬性要求缺一不可：
  1. **`onCreate` 里就要进前台**，不能只在 `onStartCommand` 里。`onCreate` 与 `onStartCommand` 是主线程上两条**独立消息**，
     低内存手表（线上机型 3906MB/2591MB）上创建服务本来就慢，两者可以隔很久。
     `startUpdateTimer()` 也因此必须放在 `onStartCommand` 的进场之后——它 1 秒后就会尝试 `stopSelf()`，
     提前开在主线程忙时会抢在 `startForeground` 前面把义务作废（上游 Java 版就是这个顺序，Kotlin 版曾改错）。
  2. **没进前台之前禁止 `stopService()`**：`PlaybackService.stop()` 由 `onResume`/`onDestroy` 调，和 `onPause` 里的 `start()`
     本来就紧挨着；若 `stopService` 先到，`onStartCommand` 永不执行，义务无人兑现，5 秒后照样崩。
     现在 `stop()` 先看进程级 `foregrounded` 标志：未进前台只置 `stopPending`，由 `onStartCommand` 在 `startForeground` **之后**
     再 `stopSelf()`——结果同样干净（服务与通知都不残留），但不会崩。所以**「先进场、再停止」是本服务的顺序铁律**。
  3. `enterForeground()` 返回 false（Android 14+ 缺权限、厂商拦截、连兜底通知都挂不上）时**必须 `stopSelf()`**：
     服务被销毁，那笔义务才会随 `ServiceRecord` 一起作废；不销毁才是真的会崩。
  4. `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限与 Manifest 里的 `<service ... android:foregroundServiceType="mediaPlayback">`
     是运行期硬性要求（Android 14 起），删任一个都会崩。
- **TimerTask 必须整体 try/catch**：Timer 的 `TimerTask` 抛未捕获异常会**永久终止整个 Timer**，进度通知从此静默失效。
- 已知限制：点通知走 `getLaunchIntentForPackage`（本项目 = `SplashActivity`）+ `FLAG_ACTIVITY_REORDER_TO_FRONT`，
  与上游一致；`PlayerActivity` 没有 `launchMode`，所以 `onNewIntent` 里那句 `finish()` 是死代码，未改动。
  当前版本**没有 wakelock**（`WAKE_LOCK` 权限未声明、`IjkMediaPlayer.setWakeMode` 未调用），熄屏能否持续播放待真机确认。

### 7.6 更新日志页按版本分页（`activity/settings/UpdateHistoryActivity.kt`，26.09.25 新增）

`res/values/strings.xml` 的 `R.array.update_log_current`（首行 = 当前版本）与 `R.array.update_history_log`
（每个 `## YYYY-MM-DD` 段 = 一个历史版本）是**唯一数据源**，不要另建静态日志表（上游曾有两份互相漂移的日志源）。
`SplashActivity` 在覆盖安装后的首次启动会自动打开该页（只弹一次，存 versionCode），
旧「更新公告」全屏弹页（`AppInfoApi.check` 里那句 `MsgUtil.showText`）已删除以避免同一次升级弹两个内容重复的页面。

### 7.7 番剧片头/片尾自动跳过（26.10.04 新增）

> ✅ **26.10.22 已恢复上线（口径改为默认关闭）**：26.10.10 的紧急撤回已解除——`player/SkipOpEdPrefs.kt` 的
> `FEATURE_ENABLED` 回到 `true`，设置项回到 `SettingTerminalPlayerActivity`，关键词回到 `SettingsIndex`，
> `PlayerActivity.needViewPoints()` 的 `PLAYER_SKIP_OP_ED` 一项恢复。
> 撤回期的警告（下面这段）保留作历史依据，其中的常量值已不再是现状。撤回与恢复的完整记录见
> `docs/review/fix-progress.md` **§3.3**。
>
> **`FEATURE_ENABLED` 这个「与存档无关的否决位」必须留着**：它是唯一能整体下线的开关——
> 改 `DEFAULT_ENABLED` 对老用户无效（他们存档里早有值），只加 `isEnabled` 运行期闸门也不够
> （`needViewPoints()` 会继续为「跳过」白拉 `view_points`）。下次再撤仍只改这一个常量。

设计参照 PiliPlus（`bggRGjQaUbCoE/PiliPlus` @ `2515ecf`）的 `pgcSkipType` / `SkipType` 体系。

**数据来源**：`api/PlayerApi.getViewPoints(aid, cid)`（`x/player/wbi/v2` 的 `data.view_points[]`）里的 `type` —— **1 = 片头，2 = 片尾**。
在此之前全库没有一处读取 `type` 做跳转，`view_points` 只被当成「视频分段」给用户手点，所以本功能是「只差一层判定」。

**纯逻辑**：`player/ViewPointSkip.kt`（Kotlin `object`，零 Android 依赖，配 `app/src/test/.../ViewPointSkipTest.kt` 9 例）。

- `Segment(type, fromSec, toSec)` 用**秒**；`buildSegments()` 只留 `type` 为 1/2、`toSec > fromSec`、时长 ≤ `MAX_SEGMENT_SECONDS`(600s) 的段并按起点排序——上限用来挡上游偶尔下发的「0 秒 → 整集」脏区间。
- `segmentAt()` 判定区间是**左闭右开** `[from, to)`。这一点不能改成右闭：跳过后的落点正好等于 `toSec`，右闭会被自己重新命中而反复跳。
- `shouldSkip(pos, segments, handled)` 命中 `handled` 集合即返回 `null` → **每个片段只自动跳一次**。
- `keyForManualSeek()` 专门给「用户自己拖进片段」打标记用。

**接线**（全部在 `activity/player/PlayerActivity.kt`）：

- 判定挂在**既有的 250ms 主线程进度定时器** `progressChange()` 里（`maybeAutoSkipOpEd`），**没有新增 Timer**。
- 跳跃复用 `seekToPosition()`，与手动拖动走同一条路径，弹幕与外部音轨一起同步。
- 反悔：`showSkipUndoSnack()` 用 `MsgUtil.createSnack(anchor, 文案, LENGTH_LONG, MsgUtil.Action("撤回"){...})` 跳回片段起点。`lastSkippedSegment` 用来判断这条 Snackbar 是否已过期，防止「期间又跳过别的片段」后点撤回把人拽回去。
- **用户主动 seek 必须标记为已处理**：`seekToPosition()` 与 seekbar 的 `onStopTrackingTouch` 两条路径都调 `onUserSeekTo()`。不标的话，用户拖进片头想看一眼，下一轮定时器立刻又把他弹走。
- `loadViewPoints()` 的三个调用条件从「显示视频分段开启」放宽为 `needViewPoints()`（显示分段 **或** 自动跳过，任一开启）。
  **26.10.22 恢复时把存档原文判断加回来了**：`needViewPoints()` 里按 `getBoolean(PLAYER_SKIP_OP_ED, DEFAULT_ENABLED)` **原文**判断，
  **不要包一层 `isEnabled()`**——`isEnabled` 带 `FEATURE_ENABLED` 否决位，包上之后再撤一次就又要来改这里（26.10.10 撤回时正是为此多改了一处）。
- 换集/换P 时重置 `skipSegments` / `skipHandled` / `lastSkippedSegment`，不带上一集的进度。

**设置项**（当前默认**关闭**：26.10.04 后续曾改为开启，26.10.10 撤回，**26.10.22 恢复时由用户拍板改为默认关闭**）：

- `SettingsKeys.PLAYER_SKIP_OP_ED`（`player_skip_op_ed`）——开关本体。**默认值只有一个来源**：`player/SkipOpEdPrefs.kt` 的 `DEFAULT_ENABLED = false`，四个调用点全部引用它（`PlayerActivity.needViewPoints()` / `skipOpEdEnabled()` / `SettingTerminalPlayerActivity`），别再散落 `true`/`false` 字面量（原先四处各写 `false`，改默认值时漏一处就会「设置页显示开着、播放器却不跳」）。
- **用户显式选过的那一侧必须保留**：`SharedPreferences.getBoolean(key, def)` 的语义就是「键不存在才用 def」，所以只改默认值天然满足「新装用默认、升级后尊重旧选择」，**不需要任何迁移代码**。因此 26.10.22 把 `DEFAULT_ENABLED` 从 `true` 改成 `false` 也**不影响**存档里显式开过的老用户（他们恢复后仍然是开的）。`SkipOpEdPrefsTest` 对「显式关过→仍关」「显式开过→仍开」两侧都钉了测试。
- `SettingsKeys.PLAYER_SKIP_OP_ED_GUIDED`（`player_skip_op_ed_guided`）——**只是「引导提示已弹过」的记账位，不出现在设置页**，别当成用户可见开关。

**默认值为什么几经变化（别再按旧记忆改回去）**：

| 时间 | 默认值 | 依据 |
| --- | --- | --- |
| 26.10.04 初版 | 关闭 | 初版保守 |
| 26.10.04 后续 | **开启** | 参照 PiliPlus 的 `pgcSkipType` 默认 `SkipType.skipOnce`（`lib/utils/storage_pref.dart:977-979`）与 B 站官方播放器；且跳过有「撤回」兜底，误跳代价小于「用户不知道有这功能」 |
| 26.10.10 | 整体撤回 | 用户报告「乱跳」，改由 `FEATURE_ENABLED` 关掉 |
| **26.10.22（现状）** | **关闭** | 乱跳修掉后恢复，用户重新拍板：不让没主动开启的人被动跳片头 |

**引导**：`SkipOpEdPrefs.shouldShowGuide(hasSegments, alreadyGuided) = hasSegments && !alreadyGuided`。
判据**只看「引导过没有」，与开关当前值解耦**。原实现是「`skipSegments` 空就 return、开关开着就 return、引导过就 return」，一旦默认改成开启，第二条永远命中，**这条引导就变成死代码**（真机清单第 200 条永远测不到）。文案随开关状态分两套（`guideText` / `guideActionText`）：开关已开 → 「已自动跳过片头/片尾，可在下方撤回」+「知道了」（不再劝用户开启）；未开 → 「这个视频有片头片尾，可以自动跳过」+「开启」。写 `PLAYER_SKIP_OP_ED_GUIDED` 后永不再弹（每集都弹会很烦）。

**与 PiliPlus 的差异**：PiliPlus 有 5 档 `SkipType`（`alwaysSkip` / `skipOnce` / `skipManually` / `showOnly` / `disable`，**默认 `skipOnce`**），并把片段画到进度条上（`segment_progress_bar.dart`）。本项目只做「开启 = 跳过一次 + 可撤回」这一档，也没有进度条片段标记。
若将来要补 `alwaysSkip`（用户拖回去也继续跳），把 `maybeAutoSkipOpEd` 里的 `handled` 判断去掉即可——落点是右端点，不会自我循环。

### 7.8 高能进度条 pbp 接口的两代响应结构（26.10.04 修复）

接口 `https://bvc.bilivideo.com/pbp/data`（弹幕密度曲线）。**它改过响应结构，而旧实现不会报错，只会静默画出一条空线**，所以这里的两代结构都必须留着。

- **旧形态**：数据摊在根上 —— `{"step_sec":3,"events":{"default":[...]}}`。
- **新形态**：多包一层 —— `{"modules":[{"params":{"data":{...同上的字段...}}}]}`；有些链路外面还套 `{code,message,data}`。
- **请求侧**（照 PiliPlus `_getDmTrend()` 对齐）：除了 `cid` 还要带 `aid`/`bvid`，并固定带 **`r=loader`**；**Referer 必须落在具体视频页**（`/video/{bvid}`，没有 bvid 就用 `/video/av{aid}`），站点根会被风控挡掉。
- **纯解析**：`api/PlayerApi.parseHighEnergyData(JSONObject)`（static，零网络、**不写日志**——`Logu` 走 `android.util.Log`，在 JVM 单测里会抛 not-mocked）。它按「根上的 modules → data 里的 modules → data 本身 → 根自己」四个候选依次找，**优先返回真正带 `events.default` 的那个**；都不带才退回第一个候选（至少留下 `step_sec`/`debug` 便于排查）。配套 `app/src/test/.../api/PlayerApiPbpTest.kt` 9 例。
- **`buildPbpReferer(bvid, aid)`** 也是纯函数（bvid > av号 > 站点根），单测覆盖。
- 网络侧 `getHighEnergyData(cid, aid, bvid)` 用 `new ArrayList<>(NetWorkUtil.webHeaders)` 复制一份请求头、只替换 `Referer`，**不动全局表**（全局表是 volatile copy-on-write 快照，见 `util/NetWorkUtil.java:550-557` 的注释）。调用点 `activity/player/PlayerActivity.kt` 的 `loadHighEnergyData()` 从 Intent 里取可选的 `bvid`——`PlayerData` 没有该字段，所以多数入口会落到 av 号 Referer。

### 7.9 列表增量刷新：`notifyItemRangeInserted` 的起点由 adapter 的 `getItemCount()` 决定（26.10.04 批次 3）

**核心事实：本项目大量 adapter 的 `getItemCount()` 是 `data.size + 1`（位置 0 塞一个头部），所以通知增量时起点要 `sizeBefore + 1`。看到 `+ 1` 不要条件反射当越界，先读对应 adapter 的 `getItemCount()`。**

- **无头部**（直接用 list 构造）：`adapter/video/VideoCardAdapter.kt:103` `getItemCount() = videoCardList.size`、`adapter/article/ArticleCardAdapter.kt:123`、`adapter/LiveCardAdapter.kt:64` → 起点就是 `lastSize`。26.10.04 修掉的 3 个搜索页（`SearchVideoFragment` / `SearchArticleFragment` / `SearchLiveFragment`）原来写成 `lastSize + 1`，是**真越界**（第 2 页起 `IndexOutOfBounds` / `Inconsistency detected`）。
- **有头部**：`adapter/ReplyAdapter.kt:678-680` `replyList.size + 1`、`adapter/dynamic/UserDynamicAdapter.kt:115-117` `dynamicList.size + 1`、`activity/video/series/SeriesInfoActivity` 的内部 adapter `data.size + 1` → 这些调用点的 `+ 1` 是**对的**（`ReplyFragment.kt:250`、`UserDynamicFragment.kt:89`、`SeriesInfoActivity.kt:86`），已核实、别动。`adapter/user/FollowGroupAdapter.kt:125` 的 `groupPosition + 1` 是分组结构，同理。
- **通知前必须先改数据，且同在主线程**：`ReplyFragment.kt:238` 的注释就是这个约定。反面教材是 `activity/video/series/UserSeriesActivity.kt`——原来只 `notifyItemRangeInserted(oldSize, seasonList.size)` 却**从没 `addAll` 进 adapter 的 list**，报出的数量和真实条数永远对不上；已改成记住第 1 页的 adapter、第 2 页起先 `seasonList.addAll(...)` 再通知（`adapter/video/SeriesCardAdapter.kt` 的 `seasonList` 因此由 `List` 放宽为 `MutableList`）。
- 配套：`activity/RefreshListActivity` 的 `getRecyclerViewCacheSize()` / `getRecyclerViewPrefetchCount()` 来自 `PerformanceManager`，与 `notifyItemRangeInserted` 无关，别混为一谈。

### 7.10 设备档位参数的唯一出口：`PerformanceManager`（26.10.04 批次 3 整理）

- **图片质量/宽度、分页大小都必须从 `PerformanceManager` 取**，不要再在调用点写死：`GlideUtil.url()` 走 `getImageQuality()`/`getImageMaxWidth()`（列表图：低端 320w/50q，其余 512w/60q），`GlideUtil.url_hq()` 走 `getHqImageQuality()`/`getHqImageMaxWidth()`（低端 512w/60q，其余 1024w/80q）。`GlideUtil` 原有的 `QUALITY_*` / `MAX_W_*` 四个常量已删除——**再引入一份常量就等于恢复"两处真相"**。
- 档位计算是 `@JvmStatic` 纯函数（`levelFromScore` / `isLowPerfLevel` / `listImageQuality` / `listImageMaxWidth` / `hqImageQuality` / `hqImageMaxWidth`），接收 `(level, highPerformanceMode)`，因此可被 JVM 单测直接覆盖；对应的"喂当前状态"getter 只是转调。
- **`getPageSize()` 只对 3 个接口生效**（`RecommendApi` popular/precious、`SeriesApi` 用户系列）。其余硬编码 `ps`/`page_size` **是有意为之**（`FavoriteApi.java:106 ps=100` 一次拉全、`MessageApi.java:380 page_size=35` 是 cursor 分页、`EmoteApi` 表情面板等），改它们要先确认分页语义。
- **首次硬件检测不在主线程**：`init()` 无缓存时先置中档返回，检测在 `CenterThreadPool` 上跑完再 `applyPerformanceSettings()`。因此**冷启动最初几十毫秒内读到的是中档参数**，属于预期行为；单测 `PerformanceManagerTest.defaultsBeforeInit_areMedium` 锁住了这套默认值。
- **列表图 / 高清图宽度再按屏幕封顶**（26.10.27）：`getImageMaxWidth()` / `getHqImageMaxWidth()` 会经过 `capWidthToScreen(tierWidth, 屏幕长边)` 处理，只会变小，按 320/480/640/720/1024 分档。图片查看器要放大看，所以走不封顶的 `getZoomImageMaxWidth()` / `GlideUtil.url_zoom()`。不要把查看器改回 `url_hq`。
- **列表图片的 RequestManager 用 `GlideUtil.with(view)`**（26.10.27），**不要**写 `Glide.with(BiliTerminal.context!!)`，也不要写 companion 里的静态 RequestManager：Application 级请求不跟页面生命周期走，`ImageAutoLoadScrollListener` 的 `pauseRequests()` 也管不到。这个监听器只在 DRAGGING → SETTLING 时暂停，表冠的 IDLE → SETTLING 不暂停；装监听器统一用 `installIfEnabled()`。

### 7.11 更新包校验：`util/ApkVerifier.kt`（26.10.04 批次 4 新增）

**任何"把 APK 交给系统安装器"的路径都必须先过 `ApkVerifier.verify(context, apkFile)`。** 目前有两条，都已接线：

| 链路 | 校验落点 | 失败处理 |
| --- | --- | --- |
| `util/UpdateManager.kt` → `downloadApk()` → `installApk()`（更新页走这条） | `writeResponseToFile` 之后、`onComplete` 之前 | **删掉下载的文件** + `onError("安装包校验失败：…")`。删文件是必须的：留残片会被下次 `Range` 续传当成"已下载一部分"，永远拼不出完整包 |
| `activity/DownloadActivity.kt` → `installApk()`（最老的「下成 `.bak` 再改名安装」链路） | 方法开头 | 返回 `false`，由调用方走既有「安装失败，已保存到下载文件夹」分支（该方法**不在主线程**，不要在里面弹 Toast） |

- 判定规则：包名一致 **且** 签名集合与已安装应用一致（`GET_SIGNATURES` + `getPackageArchiveInfo`）。**读不到签名一律判定失败**（失败关闭）——`ApkVerifierTest` 专门锁住这条。
- 校验只防"下载链路被换包/串包/截断"，**防不住"同一签名者发布了坏包"**，也**不校验哈希**：MD5 与安装包来自同一个响应，能改包的人也能改元数据。系统安装器本身也会拒绝换签名包，本校验的价值是**早失败 + 中文原因**。
- 纯逻辑全在 `ApkVerifier` 的 `isSamePackage` / `isSameSignature` / `signatureHex` 三个静态函数里，可 JVM 单测；只有 `verify()` 依赖 Android。

### 7.12 设置 key 的唯一来源：`SettingsKeys`（26.10.04 批次 4）

- **`player` / `play_qn` 两个键现在只有唯一来源：`util/SettingsKeys.kt:90` 的 `PLAYER`、`:92` 的 `PLAY_QN`。** 原来还有第三处定义 `SharedPreferencesUtil.player`（死字段，已删）和 13 处字面量（已全部改调常量）。看见新代码里直接写 `"player"` / `"play_qn"` 就是回退。
- **别被同名假阳性骗了**：`activity/settings/SettingMainActivity.kt:118` 的 `"player"` 是设置页**分组 id / 跳转标记**，与 SharedPreferences 键只是字面量碰巧相同，**不是** `SettingsKeys.PLAYER`（该处已加注释）。
- `SettingsKeysTest` 把两个键名字符串钉死。理由：键名是**磁盘协议**，改了不报错、只让用户设置静默回默认值（覆盖安装升级时才会被发现）。
- 其余键（`mid`、`player_show_viewpoints` 等）仍散着字面量，尚未收敛；新增键请直接加进 `SettingsKeys`。

### 7.13 `session_svr` 会话管理接口（26.10.04 批次 5 新增）

- 私信会话的「移除」与「置顶」都走 `https://api.vc.bilibili.com/session_svr/v1/session_svr/` 下的接口，封装在 `api/PrivateMsgApi.java`：`removeSession(long talkerId, int sessionType)`、`setSessionTop(long talkerId, int sessionType, boolean top)`，两者共用私有 `postSessionAction(url, talkerId, sessionType, extra)`（`talker_id`/`session_type`/`csrf_token`/`csrf`/`build=0`/`mobi_app=web`，csrf 走 `NetWorkUtil.currentCsrf()`）。参数名与响应结构照 `updateAck` 的既有写法。
- **`set_top` 的 `op_type` 与直觉相反：0 = 置顶、1 = 取消置顶。** 已抽成纯函数 `PrivateMsgApi.opTypeForTop(boolean)` 并配单测（`api/PrivateMsgApiTest.kt`），写反的后果是"点置顶实际取消置顶"——不报错、极难发现。
- **服务端没有布尔型"是否置顶"字段**：只有会话项里的 `top_ts`（微秒级时间戳，未置顶为 0）。模型侧是 `model/PrivateMsgSession.topTs` + `isTop()`；`parseSessionsList` 用 `optLong("top_ts", 0)`（缺字段按未置顶），`getNewSessionsList` 同步解析。
- **移除会话 ≠ 删除聊天记录**：`remove_session` 只把会话从列表里拿掉，对方再发消息会重新出现。UI 的二次确认文案必须说清这一点，否则用户以为记录没了。
- 会话类型常量在 `PrivateMsgApi`：`SESSION_TYPE_USER = 1`、`SESSION_TYPE_FAN_GROUP = 2`（本项目目前只处理用户私信，粉丝团未接）。
- 返回码翻译统一走 `PrivateMsgApi.sessionErrorMsg(int)`（0→空串、-101→未登录、-400→"请求错误，会话可能已不存在"、其余保留错误码）。
- UI 落点：`adapter/message/PrivateMsgSessionsAdapter.kt` 的会话项**长按弹 `AlertDialog` 菜单**（置顶/取消置顶、删除会话、查看用户主页）；`activity/message/MessageActivity.kt` 的会话加载已抽成 `loadSessions()`，adapter 通过构造参数 `onSessionsChanged` 回调它重新拉列表（服务端才是唯一真相）。

### 7.14 私信图片消息（`msg_type=2`，26.10.04 批次 5 新增）

- **content 结构是硬约定**（`bilibili-API/docs/message/private_msg_content.md:27-57`）：`url` / `width` / `height` / `imageType` / `original` / `size`。组装入口是纯函数 `PrivateMsgApi.buildImageContent(url, width, height, byteSize, imageType)`，配套 `sizeToKb(long)`（KB，三位小数）与 `imageTypeOf(String)`（去 `image/` 前缀、`jpg`→`jpeg`）。
- **`url` 必须是 B 站图床地址**，否则返回 21037「图片格式不合法，不要调戏接口啦」。所以发图必须先上传：`ImageApi.prepareImage(context, uri)`（最长边 2048 压 JPEG90，GIF/PNG 在阈值内原样透传）→ `ImageApi.uploadImage(data, fileName, mimeType, biz)`（`POST https://api.bilibili.com/x/dynamic/feed/draw/upload_bfs`）。
- **`width`/`height` 不是可选项**：不带会在客户端显示异常（接口不报错），必须用上传返回的 `UploadedImage.width/height`。
- `biz` 没有私信专用值，复用 `ImageApi.BIZ_REPLY`（`new_reply`）——21037 只校验"是不是 B 站图床"，图床本身共用一个 `upload_bfs`。
- UI 落点：`activity/message/PrivateMsgActivity.kt` 的 `imageBtn`（布局 `activity_private_msg.xml` 里是个文字按钮「图」，因为 `res/drawable/` 没有图片类图标，且新增资源文件会踩 build cache 陈旧资源坑）+ `pickImage()`（**`ACTION_GET_CONTENT`**，手表相册不一定支持 `ACTION_PICK`）+ `sendImage(uri)`。
- **已知取舍：不做 EXIF 旋转**（无 `androidx.exifinterface` 依赖、约定不轻易加库），竖拍照片方向可能与相册显示不一致，与 `WriteReplyActivity` 行为一致。

### 7.15 新消息通知（26.10.04 批次 5 的 C14 新增）

- 全库通知现有四处：`DownloadService`（渠道 `biliterminal_download`、通知 ID **1027**）、`PlaybackService`（渠道 `playback_channel`、通知 ID **1028**）、本节的 `util/MsgNotifier.kt` 新消息（渠道 `private_msg_channel`、通知 ID **1029**）、以及同文件里的追番更新（渠道 `bangumi_update_channel`、通知 ID **1030**，见 §7.16）。**新增通知必须先确认 ID 不撞车**，撞了会互相覆盖。
- `MsgNotifier` 的三个成员：`notifyNewMessages(context, privateMsgUnread, otherUnread)`、`cancel(context)`、以及两个纯函数 `shouldNotify(previousUnread, currentUnread, enabled)` / `summaryText(privateMsgUnread, otherUnread)`。纯函数被 `util/MsgNotifierTest.kt` 覆盖。
- **提醒判据是"未读变多"而不是"有未读"**：`shouldNotify` = 开关开 && `currentUnread > 0` && `currentUnread > previousUnread`。否则每次冷启动都会弹，变成骚扰。`previousUnread` 取的是 `SharedPreferencesUtil.MESSAGE_UPDATE_NUM`（上次检查后存下的总未读）。
- **触发点是 `BiliTerminal.onCreate` 里既有的未读检查**（受「消息数量检查」开关注释）：`MessageApi.checkPrivateMsgUnread()` 给 `privateMsgUnread`、`checkMessageUnread()`（at + reply）给 `otherUnread`，**实参顺序别搞反**。项目无 WorkManager / AlarmManager 依赖，也不为此新增，所以没有后台定时提醒。
- **未读检查失败不再把 `MESSAGE_UPDATE_NUM` 清零**（C14 的行为变更）：清零会让下一次成功检查把"老未读"当成新增未读，网络抖一次就重复弹通知；现在只记 `Logu.w`。改动这里前先想清楚通知会不会重复。
- Android 13+ 需要运行时 `POST_NOTIFICATIONS`，在 `activity/message/MessageActivity.kt` 的 `requestNotificationPermissionIfNeeded()` 里申请；未授权时 `notifyNewMessages` 因 `areNotificationsEnabled()` 为 false 静默放弃，**不报错**——调试"通知不弹"时先查权限/系统开关，不要只盯代码。
- 开关键：`SettingsKeys.PRIVATE_MSG_NOTIFY_ENABLE`（`private_msg_notify_enable`，默认开）；**设置入口在第一层级的「通知设置」分组**（`GROUP_NOTIFY`，`SettingGroupActivity.buildNotifyGroup()`），可搜索条目在 `SettingsIndex`（`Entry("新消息通知", …) { openGroup(GROUP_NOTIFY, "通知设置", "新消息通知") }`）。**原先埋在「内容与浏览 → 通用偏好 → 更新提醒」里，已从 `SettingPrefActivity` 移除**——同一开关留两个入口会出现「一处改了另一处不刷新」的假故障（用户会以为设置没生效）。
- **不做 RemoteInput 速回**（用户拍板）：要额外权限 + 跨进程回复广播，手表打字成本高，收益不抵复杂度；点通知 = 打开消息页（`PendingIntent` 用 `NEW_TASK or CLEAR_TOP` + `FLAG_IMMUTABLE`）。
- 两个 `notify*` 里的建渠道重复代码已抽成私有 `ensureChannel(context, id, name, description)`（Android 8.0+ 必须建渠道，重复创建同名渠道是幂等的）；再往这个文件加通知时复用它，别再抄一遍。

### 7.16 追番更新提醒（26.10.04 批次 5 的 C16 新增）

- 链路：`BiliTerminal.onCreate` 第三块（在未读检查之后、`checkAppUpdate()` 之前）→ `util/BangumiUpdateChecker.checkAndNotify(context)` → `api/BangumiApi.getFollowedBangumi()` → 与快照 diff → `util/MsgNotifier.notifyBangumiUpdates(context, titles)`（通知 ID **1030**，`PendingIntent` 指向 `activity/user/FollowingBangumisActivity`）。
- **判据是同一部番的 `new_ep.id` 变了**（`model/FollowedBangumi.newEpId`），不是总集数、也不是服务端的 `is_new`（后者会被用户在别的客户端看过之后清掉）。纯逻辑 `BangumiUpdateChecker.findUpdated(stored, current)` 只认三种情况：快照里存在、`newEpId` 变了、且当前 `newEpId > 0`。
- **首次检查只写快照、不提醒**；**这次新追的番不算更新**；**快照只在成功拉到列表后写回**（失败保持旧快照，否则下次把老集当新集重复提醒，与 §7.15 未读检查失败不清零同理）。快照是 `SharedPreferencesUtil.BANGUMI_UPDATE_SNAPSHOT`（`bangumi_update_snapshot`），内容是 `{"media_id": new_ep_id, …}` 整份替换。
- **拉取上限 10 页 × 30 条 = 300 部**（`FOLLOW_PAGE_SIZE` / `FOLLOW_MAX_PAGES`，`ps` 定义域 1-30）：冷启动不该为一个提醒把流量打满。接口是 `x/space/bangumi/follow/list`（`type=1` 追番、`follow_status=0`），与列表页用的 `getFollowingList(int, List<VideoCard>)` **是两个用途**，别合并。
- 开关键 `SettingsKeys.BANGUMI_UPDATE_NOTIFY_ENABLE`（`bangumi_update_notify_enable`，默认开），设置入口与 §7.15 同在第一层级的「通知设置」分组（同属 `buildNotifyGroup()`）；文案 `desc_bangumi_update_notify_enable`。
- **不做后台定时**（用户拍板）：项目没有也不引入 WorkManager / AlarmManager；`getFollowedBangumi()` 未登录时返回空表，`checkAndNotify` 遇到空表直接返回且**不覆盖快照**。
- 单测：`api/BangumiApiTest.kt`（纯解析）、`util/BangumiUpdateCheckerTest.kt`（快照往返 + 更新判定）、`util/MsgNotifierTest.kt` 的 `bangumiSummaryText`、`util/SettingsKeysTest.kt` 的键名钉子。

### 7.17 评论列表的两种排序：主列表走服务端、楼中楼只能客户端排（26.10.04 批次 6 的 C4）

- **主评论列表**（`activity/reply/ReplyFragment.kt`）用服务端排序：`/x/v2/reply`（翻页）的 `sort`（0=时间/1=点赞数/2=回复数）与 `/x/v2/reply/wbi/main`（懒加载）的 `mode`（0/3=仅热度、1=热度+时间、2=仅时间），由 `api/ReplyApi.getReplies`/`getRepliesLazy` 透传。`ReplyFragment.kt:370` 在 2/3 两档之间切换。
- **楼中楼**（`activity/reply/ReplyInfoActivity.kt`）**没有服务端排序可用**：`/x/v2/reply/reply` 的参数只有 `type`/`oid`/`root`/`ps`/`pn`，**没有 sort / mode**，而且每页最多只返回 20 条。所以详情页的排序开关是**纯客户端**的：`model/Reply.sortReplies(List<Reply>, int sort, int fromIndex)`，`sort` 取 `Reply.SORT_TIME`（0，即服务端返回的回复顺序，直接不动列表）或 `Reply.SORT_LIKE`（1，按 `likeCount` 降序，`Collections.sort` 是稳定排序，同热度保持时间序）。
- **`fromIndex` 必须传 1**：评论详情页的第 0 位是根评论（所有楼中楼回复的父评论），不能被排进子评论里。翻页累积时也要带上新页重新排（`sort == SORT_LIKE` 时整体 `notifyDataSetChanged`，其余情况仍走 `notifyItemRangeInserted`）。
- 排序按钮文案由 `ReplyAdapter.sortLabel()` 给出——详情页是「时间排序/热度排序」（0/1），主列表是服务端的「时间排序/热度排序」（2/3），**两套取值不一样，别共用一张表**（旧代码共用 `sorts[sort]`，导致详情页永远显示「未知排序」；而且旧详情页把这个按钮设成了 `GONE`，等于根本没有排序入口）。
- **「按时间」不用 `floor` 排**：`floor` 在部分评论区不存在（`bilibili-API/docs/comment/readme.md` 的字段表注明「若不支持楼层则无此项」），而接口返回顺序本身就是回复顺序，直接用更稳。`seek_rpid` + `min_floor` 的「定位到某条评论」没做（C4 的另一半，暂不需要）。
- 单测：`app/src/test/java/com/RobinNotBad/BiliClient/model/ReplySortTest.kt`（降序、同热度稳定、`SORT_TIME` 不动列表、`fromIndex` 之前的元素不动、null/空/越界 fromIndex 不抛）。

### 7.18 带图评论的发送闸门（26.10.04 批次 6 的 C6b）

- 带图评论是「选图 → 压缩 → 上传图床（`ReplyApi.uploadReplyImage`）→ 拿 `image_url` → 拼 `pictures` → 发评论」两段式，**选完图界面立刻就有图了，但上传还在后台跑**。`activity/reply/WriteReplyActivity.kt` 用 `pendingUploads` 记「仍在上传的张数」，发送前必须过闸：`ReplyApi.canSendReply(pendingUploads)` 为 false 时只提示 `ReplyApi.uploadPendingTip(k)`，不发送。
- **为什么必须有这道闸**：调用方拼 `pictures` 时只能拿到**已成功**的上传结果，图没传完就发送 = 评论**少图发出且没有任何提示**（本地还因为把后到的 url 补进 `resultReply.pictureList` 而显示得比实际更多）。
- **闸门要在点下的那一刻置位**（`@Volatile sending`），并在后台 `try/finally` 里松开：成功了会 `finish()`，失败/提前 return 也要松，否则用户之后再想发就永远被挡。原实现是等请求返回才置位，等待期连点会发出两条评论。
- 这两个判断是**纯函数**（`api/ReplyApi.java` 的 `canSendReply`/`uploadPendingTip`），与 `actionErrorMsg(int)` 同类；`activity_write_reply.xml` 里没有进度控件，所以「上传中」只用按钮文字（`imageText`）表达，**不做按字节的进度条**（用户拍板只做第 1 步）。

### 7.19 动态的编辑与「管理」菜单（26.10.04 批次 6 的 C7）

- **一条动态能不能编辑/删除，看服务端下发的三点菜单**：`api/DynamicApi.java` 解析 `module_more.three_point_items[].type` 时，`THREE_POINT_DELETE` → `Dynamic.canDelete`、`THREE_POINT_EDIT` → `Dynamic.canEdit`。**两个开关独立**，不能用 `canDelete` 顶替 `canEdit`；`model/Dynamic.java` 的 `canEdit` 是 C7 新增的（`isTop` 早已解析但至今没有 UI 读它）。
- **列表项的管理入口是 AlertDialog 菜单，不再是「两次长按删除」**：`adapter/dynamic/DynamicHolder.kt` 的 `getManageListener(...)`（列表版 4/5 参重载 + 详情版 2/3 参重载）→ `showManageMenu(activity, dynamic, onEdited, onDeleted)`，菜单项按 `canEdit`/`canDelete` 动态拼。三个绑定点：`adapter/dynamic/DynamicAdapter.kt`、`adapter/dynamic/UserDynamicAdapter.kt`、`activity/dynamic/DynamicInfoFragment.kt`；可见条件统一是 `canDelete || canEdit`。
- **`cell_dynamic.xml` 里 `item_dynamic_delete` 的文案已从「删除」改成「管理」**，点一下与长按都弹同一个菜单（`showDynamic` 里把它 `setOnClickListener { performLongClick() }`，不另维护一份回调）。
- **编辑走 PiliPlus 的接口**：`POST https://api.bilibili.com/x/dynamic/feed/edit/dyn`，query 要 `platform=web`、`csrf`、`x-bili-device-req-json`（`{"platform":"web","device":"pc","spmid":"333.1368"}`）、`w_dyn_req.upload_id`（`mid_秒级时间戳_四位随机数`，纯函数 `DynamicApi.buildUploadId(mid, seconds, random)`）、`w_dyn_req.meta`（`{"app_meta":{"from":"create.dynamic.web","mobi_app":"web"}}`），**整条 query 要过 `ConfInfoApi.signWBI`**；body 是 `{"dyn_req":{content:{contents},scene,meta,upload_id,…},"dyn_id_str":"<id>"}`；返回 `code`，0 为成功（`editErrorMsg(code)` 给文案）。**编辑接口没有 `timer_pub_time`**（定时发布只能走发布接口，见 C9）。
- **编辑页与发布页是同一个 Activity**：`activity/dynamic/send/SendDynamicActivity.kt` 读 `intent.getLongExtra("edit_dyn_id", -1L)` 区分；编辑模式下预填 `edit_text`、**不看 `TerminalContext` 的转发内容**（它只在 `onDestroy` 清，会把上一次转发的卡片画到编辑页上）、隐藏投票与带图入口，发送时在页内直接调 `DynamicApi.editDynamic(...)` 并把新正文回传。
- **结果回调借 Activity 上的一个槽位**：`ActivityResultLauncher` 只能在 Activity 上注册一次，没有 per-holder 回调注册点，所以 `BaseActivity` 上留了 `@JvmField var pendingDynamicEdit: ((String) -> Unit)?`，`DynamicActivity.getEditDynamicLauncher(activity)` 在结果回来时**先取走再清空**。发起编辑前由 `DynamicHolder.launchEdit` 把「怎么刷新这一条」放进去。
- **置顶 / 取消置顶**（C8）：`DynamicApi.setDynamicTop(dynId, top)` 走 `POST /x/dynamic/feed/space/set_top` 或 `/x/dynamic/feed/space/rm_top`（路径片段由纯函数 `topPath(top)` 给），正文只有 `{"dyn_str":"<id>"}`、csrf 走 query，且要求 Cookie 里 `buvid3` 非空。**入口门槛用 `canDelete`**（别人的动态不下发 `THREE_POINT_DELETE`），菜单文案按 `dynamic.isTop` 在「置顶动态 / 取消置顶」之间切换；成功后本地翻 `isTop` 并刷新那一条（首页/空间的「置顶」标记来自服务端 `module_tag`，不刷新菜单还是旧文案）。
- **编辑是有损的**：`Dynamic` 没有原始正文，只能用 `content.toString()` 预填；WEB 链接节点只存 `orig_text`（URL 丢了）、@ 与表情会退化成文本形式，**编辑保存后这些结构会按纯文本重新提交**。这是已知取舍，已写进真机清单。

---

### 7.20 定时发布（26.10.04 批次 6 的 C9）

- **字段格式是坑**：`option.timer_pub_time` 要的是**秒级时间戳（整数）**，不是 `yyyy-MM-dd HH:mm` 字符串——`DynamicApi.buildPublishOption(boolean privatePub, Integer closeComment, Integer upChooseComment, Integer timerPubTime)` 的旧注释写的是后者，26.10.04 已改成 `Integer` 并订正 javadoc（上游 web 端与 PiliPlus 传的都是 int）。**编辑接口没有这个字段**，定时只能走发布接口（create/dyn）。
- **`buildPublishOption` 原来零调用点**：C9 之前它一直没人用（死代码），现在由 `activity/dynamic/DynamicActivity.kt` 的 `writeDynamicLauncher` 调用：从结果 intent 读 `getLongExtra("timerPubTime", 0L)`，> 0 才拼 `option`，否则传 `null` 保持四条既有发布链路原样（投票分支 `publishComplex`、带图 `publishImageContent`、纯文本 `publishTextContent(text)`、带 @/表情 `publishTextContent(text, atUids, option, emoteTexts)` 都接收 `option`）。
- **UI 只给固定档**：`activity/dynamic/send/SendDynamicActivity.kt` 的 `add_timer`（布局 `res/layout/activity_send_dynamic.xml` 新增的卡片，文案「定时发布」）点开是 AlertDialog 六项：10/30/60/120 分钟后、明天 12:00、不定时。手表上输入日期时间不现实，项目里也没有 DatePicker/TimePicker 先例，所以不做自由输入；算时间走纯函数 `DynamicApi.timerSecondsAt(nowSeconds, addMinutes)`（可单测），「明天 12:00」用 `Calendar` 在页内算。选中后按钮文案变成「定时：MM-dd HH:mm」。
- **入口只在普通发布时露出**：转发走 `relayDynamic`（没有 option）、编辑接口没有定时字段，所以 `add_timer` 与 `add_pic` 一样按 `normalPublish` 显隐。
- **定时成功后不做本地插入**：动态此刻还没真正发出去，`getDynamic` 拿回的状态不对，插进列表只会显示一条「将来才发」的动态；改为提示「已设置定时发布~」并跳过本地插入，由下拉刷新兜底。
- **真机待验证**：服务端对「离现在太近」的定时（如 10 分钟后）可能直接拒（`publishComplex` 只回 -1，页面显示「发送失败」），所以 C9 的真机清单第一条就是试 10 分钟档；若被拒就把最近档改大或改为草稿箱方案（`x/dynamic/feed/get_drafts` 的 `publish_time`）。

---

### 7.21 话题（26.10.04 批次 6 的 C10）

- **两个接口，一个 `api/TopicApi.java`**：
  - 话题动态列表 `GET https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/topic`（`topic_id` 必、`offset`、`page_size=20`、`source=Web`、`features`），**整条 query 要过 `ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url))`**（与动态首页同一套）。返回 `data.topic_card_list.{has_more, items[], offset}`，其中 **`items[]` 是套壳** `{dynamic_card_item, topic_type}`——必须取内层 `dynamic_card_item` 再交给 `DynamicApi.analyzeDynamic(JSONObject)`（该方法已是 `public static`，话题页直接复用，不必另写解析）。`has_more == false` 时 `getTopicDynamicList` 返回空串表示到底，否则返回新的 `offset`。
  - 话题广场 `GET https://app.bilibili.com/x/topic/web/dynamic/rcmd`（`page_size=9`、`source=Web`、`web_location=333.1365`），返回 `data.topic_items[]`：`id`/`name`/`discuss`/`dynamics`/`view`。这是**唯一的话题 id 来源**：动态正文里的话题节点（`RICH_TEXT_NODE_TYPE_TOPIC`）只带搜索页跳转链接，反推不出话题 id，所以广场入口是必须的，不是可选装饰。
- **一个 Activity 两种形态**：`activity/dynamic/DynamicTopicActivity.kt` 继承 `RefreshListActivity`，读 `intent.getLongExtra("topic_id", 0L)`——没有就是广场（`TopicAdapter` 列表，`bottom = true` 单页），有就是该话题的动态列表（`TopicDynamicAdapter` + `offset` 翻页）。广场点一项**用同一个 Activity 再 start 一次**（把 `topic_id`/`topic_name` 传过去），返回即回广场，不用另写页面也不用 Fragment。
- **入口在动态页动作卡片**：`res/layout/cell_dynamic_action.xml` 新增 `MaterialButton id=topic`（文案「话题广场」，整行），点击在 `adapter/dynamic/DynamicAdapter.kt` 的 `WriteDynamic` 绑定。**没有走新菜单项**——`util/MenuConfig.kt` 的 `loadEnabled` 对老用户已存的 `menu_enabled` 直接返回，新增 key 不会自动出现，加菜单项等于只有新用户能看到。
- **话题动态列表 adpater 必须自己写**：`DynamicAdapter.kt` 硬绑 `context as DynamicActivity`（还用 `writeDynamicLauncher`/`selectTypeLauncher`），`UserDynamicAdapter.kt` 需要 `UserInfo` 且第 0 位是用户信息头，两者都不能复用。`TopicDynamicAdapter` 只复用 `DynamicHolder`（item 布局 `res/layout/cell_dynamic.xml`，长按管理菜单走 `DynamicHolder.getManageListener(activity, dynamicList, realPosition, this)`）。
- **它保留了第 0 位的头部占位**（复用 `res/layout/cell_goto.xml` 的那个 `text`，显示 `#话题名`）：`getItemCount() = dynamicList.size + 1`、`realPosition = position - 1`。不是为了好看——`getManageListener` 列表版在置顶/编辑成功后会**按 `realPosition + 1` 反推要刷新的行**，去掉头部占位会刷错行。翻页通知起点因此是 `lastSize + 1`。
- **发布器不加话题选择**（用户拍板）：发动态要带话题得先有 id，而正文里拿不到 id；既要话题选择器又要话题搜索，属另一件事。

### 7.22 笔记，仅查看（26.10.04 批次 6 的 C27）

- **链路**：视频详情页 `activity/video/info/VideoInfoFragment.kt` 新增整行 `MaterialButton id=note`（布局 `res/layout/fragment_video_info.xml`，文案「笔记」，**未登录时与「稍后再看」「转发」「视频摘要」一起 `GONE`**）→ `activity/note/NoteActivity.kt`（`BaseActivity` 子类，`intent` 只带 `aid`，可选带 `note_id` 直接看指定那篇）。Activity 里先 `SharedPreferencesUtil.mid == 0` → 提示「登录后才能看笔记喵~」；否则 `CenterThreadPool.run` 内先调 `NoteApi.getNoteIdsOfVideo(aid)` 拿私有笔记 id 列表，空则「这个视频还没有笔记」，有则取第一篇调 `NoteApi.getNoteInfo(aid, noteId)`。
- **两个接口，一个 `api/NoteApi.java`**：`GET https://api.bilibili.com/x/note/list/archive?oid=<aid>&oid_type=0&csrf=<NetWorkUtil.currentCsrf()>`（**只能查私有笔记**，无笔记时 `data` 里**没有 `noteIds` 字段**，要当空表处理）→ `data.noteIds[]` 是**字符串数组**；`GET https://api.bilibili.com/x/note/info?oid=<aid>&oid_type=0&note_id=<id>`（不带 csrf）。错误码 `79502` 笔记详情未找到、`79503` 笔记正文未找到、`79514` 公开笔记没找到，另有 `-101`/`-400`，全部走纯函数 `NoteApi.noteErrorMsg(int)`；网络层抛异常时优先用服务端 `message`。
- **`note_id` 必须取 `note_id_str`**：接口文档自己的示例里 `note_id` 是 `24508729145690110` 而 `note_id_str` 是 `"24508729145690112"`——17 位超过 2^53，走 JSON number 已经丢精度。`NoteApi.pickNoteId(JSONObject)` 固定优先 `note_id_str`、回退 `note_id`，并且**全程按字符串传参**（`model/Note.noteId` 是 `String`）。
- **正文是 Quill delta，不是 HTML，也不能复用 opus 解析**：`data.content` 是一段 JSON 序列字符串，根数组元素形如 `{"attributes":{…},"insert": …}`——`insert` 是字符串就是文本，是对象则二选一 `imageUpload`（`url`/`status`/`width`，**`width` 是"图片宽度 - 2"**）或 `tag`（`cid`/`index` 分P/`seconds` 进度/`title` 等）。属性有 `bold`/`underline`/`strike`/`color`/`background`/`list`（`ordered`|`bullet`）/`size`。`model/OpusParagraph.java` + `OpusContentAdapter` 那套处理的是**嵌套**段落结构，这里是**扁平 delta 数组**，结构不兼容，所以另写了 `model/NoteBlock.java` + 纯函数 `NoteApi.parseBlocks(String)`（坏 JSON、根是对象、`insert` 对象里两种 key 都没有——一律安全跳过，不抛）。
- **渲染不用 RecyclerView**：`NoteActivity.renderBlocks` 把 block 列表拼成**一段 `SpannableStringBuilder`** 塞进一个 `TextView`（`res/layout/activity_note.xml` 的 `note_content`），样式用 `StyleSpan`/`UnderlineSpan`/`StrikethroughSpan`/`ForegroundColorSpan`/`BackgroundColorSpan`；图片只渲染 `[图片]` 占位，视频进度 tag 渲染成 `[视频进度 mm:ss]`（有分P索引则 `[分P n mm:ss]`，格式化走纯函数 `NoteApi.formatTagSeconds(long)`）；列表项加 `n.` / `•` 前缀（后面各带一个空格）。脏色值 `Color.parseColor` 包 try/catch 返回 null，不崩。
- **没做的**（用户拍板范围限定「仅查看」）：我的笔记列表页（`x/note/list`）、公开笔记（`cvid` / `x/note/publish/info`）、创建与编辑笔记、图片真实加载、点击 tag 跳转进度，全都没做。

### 7.23 稍后再看「未看完」分类（26.10.04 批次 7 的 C18）

- **判据直接用接口自带的两个字段**：`GET x/v2/history/toview/web` 的 `data.list[]` 里本来就有 `progress`（已看秒数）与 `duration`（总时长秒数），不需要查观看记录。纯函数 `api/WatchLaterApi.isUnfinished(progress, duration)`：`progress <= 0` → 不算「未看完」（那是"没播过"）；`duration <= 0` 视为未知、只看 `progress > 0`；否则 `progress < duration`。注意 `progress` 是**秒**、`VideoCard.progress` 也是秒，而 `PlayerData.progress` 是毫秒，别混。
- **`model/VideoCard.java` 新增 `public long duration = 0;`**：只有稍后再看接口会填，其它来源保持 0（未知）。它是 `Parcelable`，字段**只能追加在末尾**且构造器读取与 `writeToParcel` 写法必须成对（文件里有注释警告：顺序错位会静默串数据）。
- **筛选条是 `res/layout/activity_simple_refresh.xml` 里默认 `gone` 的 `filterBar`**：两个等宽 `TextView`（`filterAll`「全部」/`filterUnfinished`「未看完」）。和 `loadMoreTip` 一样放在 `SwipeRefreshLayout` **外面**，只有 `WatchLaterActivity` 在 `onCreate` 里改成 `VISIBLE`——所以对其它 `RefreshListActivity` 页面零影响。**没有做成 adapter 的头部项**：那会改变所有业务 adapter 的 `viewType`/`adapterPosition` 语义（本项目已有"头部占位导致 `+1` 通知起点"的坑，见 §7.9）。
- **切档不重新请求**：`activity/user/WatchLaterActivity.kt` 里 `allList` 存接口全量、`shownList` 是交给 adapter 的那个引用；切档只按 `WatchLaterApi.filterUnfinished` 重填 `shownList` + `notifyDataSetChanged()`。**删除时两张表都要改**（按对象 `allList.remove(card)`，按位 `shownList.removeAt(position)`），否则切档会"复活"已删条目。选中档位用 `ColorScheme.PRIMARY`，另一档用 `onCreate` 时从 `filterAll.currentTextColor` 抓到的默认色（不写死颜色常量）。
- **删除手势没动**：仍是原来的「4 秒内连点两次长按才删」，C18 只加分类。

### 7.24 收藏夹：排序 + 复制 / 移动（26.10.04 批次 7 的 C19）

- **「收藏夹排序」有两个层面，别混**：① **收藏内容列表**按服务端 `order` 参数重排（本项目已实现）；② **夹内手动拖拽排序**（上游 `POST x/v3/fav/resource/sort`，需 APP 签名）与**收藏夹本身排序**（上游 `POST x/v3/fav/folder/sort`，同样需 APP 签名）**本项目未实现**，已记入 `docs/FEATURES.md` §8.5 待做清单。`x/v3/fav/resource/list` 的 `order` 取值 `mtime`（收藏时间）/`view`（播放量）/`pubtime`（投稿时间）。常量在 `api/FavoriteApi.java`：`ORDER_FAV_TIME`/`ORDER_VIEW`/`ORDER_PUBTIME`。
- **26.10.23 迁移删掉了整条老链路**：`x/space/fav/arc` 的 `getFolderVideos()`、`legacyOrder()`（老接口收藏时间叫 `fav_time`、新接口叫 `mtime` 的映射）都已不存在；唯一入口是 `getFolderVideosNew(mediaId, page, list, order)`，三参旧重载委托 `ORDER_FAV_TIME`。调用点不用再判「有没有 media_id 走哪条接口」。
- **排序必须重发请求，筛选不用**：`sortBar` 切档 = `page = 1` + `bottom = false` + 清 `videoList` + 重拉第一页；这与 C18 的 `filterBar`（纯本地过滤、只 `notifyDataSetChanged`）是两种东西，别抄错。
- **`sortBar` 也是 `res/layout/activity_simple_refresh.xml` 里默认 `gone` 的分组**（三个等宽 chip：`sortFavTime`/`sortView`/`sortPubtime`），只有 `FavoriteVideoListActivity` 会点亮，其它 `RefreshListActivity` 页面零影响。选中档位用 `ColorScheme.PRIMARY`，另两档用 `onCreate` 时从 chip 的 `currentTextColor` 抓到的默认色。
- **收藏夹列表源已换**：`getFavoriteFolders` 改 `GET x/v3/fav/folder/created/list?up_mid=&pn=&ps=100`（`has_more` 分页拉全），一次请求就拿到 `id`（media_id）/`title`/`cover`/`attr`，不再「`getBoxList` + `list-all` 两次请求拼 media_id」；`getUserFavoriteFolders` 走同一接口并跳过 `attr & 1`（私密）的条目。
- **media_id 直接来自服务端**：`FavoriteFolder.mediaId = id`，`FavoriteFolder.id = fid`（仅展示/兜底）。`adapter/favorite/FavoriteFolderAdapter.kt` 点击传 `mediaId` + `readOnly=false`（`UserFavoriteFolderAdapter.kt` 传 `readOnly=true`），不再传 `fid`。页面判定不变：`readOnly = getBooleanExtra("readOnly", mediaId > 0)`、`writable = !readOnly && mediaId > 0`；**`mediaId <= 0` 时 `fetch()` 直接返回 `-1`（按加载失败处理）**，老接口兜底分支与「连点两次长按删除」已一并删除。
- **写操作统一走 `POST x/v3/fav/resource/batch-deal`**：form `resources`（`{avid}:2` 逗号串）+ `add_media_ids` + `del_media_ids` + `csrf`（可同时加/删），私有 `dealFavorites()` 收口；`addFavorite(aid, mediaId)` 只传 `add_media_ids`，`deleteFavorite(aid, mediaId)` 与多选删除 `batchDeleteResources(mediaId, cards)` 只传 `del_media_ids`（`mediaId <= 0` 或资源为空直接返回 `-400` 不发请求）。老 `medialist/gateway/coll/resource/deal`、`medialist/gateway/coll/resource/batch/del`、`x/v3/fav/resource/batch-del` 全部弃用。
- **复制 / 移动仍走 `POST x/v3/fav/resource/copy` ∥ `/move`**：参数 `src_media_id`/`tar_media_id`/`mid`/`resources`/`platform=web`/`csrf`。`resources` 格式是 `{avid}:2`，拼装用纯函数 `FavoriteApi.buildResources(cards)`（跳过 `aid <= 0`），错误码文案用 `FavoriteApi.resourceErrorMsg(code)`（含 `11010` 内容不存在），多选删除复用同一套。
- **长按弹菜单而不是直接删**：`writable` 时弹「复制到…」「移动到…」「取消收藏」；复制/移动的目标收藏夹从 `FavoriteApi.getFavoriteFolders(mid)` 里选并**排除当前这个**（`mediaId` 相同或 `mediaId <= 0` 的不列）。移动成功要把那一条从 `videoList` 移除（`removeItem` 走 `notifyItemRemoved` + `notifyItemRangeChanged`，删空则 `showEmptyView()`）。

---

### 7.25 收藏夹多选删除（26.10.04 批次 7 的 C20）

- **接口（26.10.23 迁移后）**：多选删除与单条取消收藏共用 `POST x/v3/fav/resource/batch-deal`，参数 `resources`（C19 的 `buildResources()`，`{avid}:2` 逗号分隔）+ `del_media_ids=<media_id>` + `csrf`；`api/FavoriteApi.batchDeleteResources(mediaId, cards)` 在 `mediaId <= 0` 或资源为空时**直接返回 `-400` 不发请求**（和 `copyResources`/`moveResources` 一样的前置校验），错误文案复用 `resourceErrorMsg()`。老 `x/v3/fav/resource/batch-del` 已弃用。
- **`manageBar` 是 `res/layout/activity_simple_refresh.xml` 里第三个默认 `gone` 的分组**（前两个是 C18 的 `filterBar`、C19 的 `sortBar`）：两个等宽 chip `manageToggle`（「多选」/「退出多选」）与 `manageDelete`（「删除」/「删除(n)」）。只在 `writable`（自己的收藏夹且有 `media_id`）时点亮；没勾选任何条目时「删除」压暗到 `alpha=0.5`，点了只提示「先选几条吧~」。
- **勾选状态在页面，不在 adapter**：页面持有 `LinkedHashSet<Long> selectedAids` 并把同一个引用交给 `VideoCardAdapter.selectedAids`；`VideoCardAdapter.selectionMode` 只影响 `onBindViewHolder` 末尾那次 `holder.applySelection(selectionMode, selectedAids.contains(aid))`。
- **选中样式不动任何布局 id**：`adapter/video/VideoCardHolder.applySelection` 用「未选中条目 `itemView.alpha = 0.45f`」+「标题前加 2 字符前缀」（`TextUtils.concat`，选中 `"✓ "`、未选中 `"　 "` 全角空格对齐，加之前先 `subSequence(2, …)` 剥旧前缀）。**没有给 `cell_video_list.xml` 加 checkbox**——那里的 id 是跨包事实协议（见第 7 节开头），为画个勾不值得冒险。
- **多选模式下的手势语义**：点条目 = 勾选/取消勾选（覆盖虚拟合集点击与进视频详情页），长按也走勾选、不再弹 C19 的管理菜单。**切排序会先 `exitSelectionMode()`**（`switchSort` 里），否则 `selectedAids` 会指向被清空的旧列表。
- **删除流程**：确认框文案「确定把选中的 N 条从收藏夹里移除吗？」→ `CenterThreadPool.run { batchDeleteResources(mediaId, targets) }` → code 0 时提示「已删除 N 条」+ 本地 `videoList.removeAll(targets)` + `notifyDataSetChanged` + 空则 `showEmptyView()` + 退出多选；非 0 用 `resourceErrorMsg(code)`。

### 7.26 关注分组增删改（26.10.04 批次 7 的 C21）

- **三个写接口**（都在 `api/FollowApi.java`，走 `application/x-www-form-urlencoded` + `csrf`）：创建 `POST x/relation/tag/create` 参数 `tag`（**最长 16 字符**，`TAG_NAME_MAX_LENGTH = 16`）→ `createFollowTag(name)`；重命名 `POST x/relation/tag/update` 参数 `tagid`/`name` → `renameFollowTag(tagid, name)`；删除 `POST x/relation/tag/del` 参数 `tagid` → `deleteFollowTag(tagid)`。三者都经私有 `postTag(url, formData)` 统一取值 `code`（缺 `code` 记 `-1`）。
- **错误码文案集中在 `FollowApi.tagErrorMsg(code)`**：22101 名称有不允许字符 / 22102 分组数量超限 / 22103 分组名过长 / 22104 分组不存在 / 22106 同名分组已存在；本地预校验是纯函数 `FollowApi.checkTagName(name)`（空/全空白 → 「分组名不能为空」，trim 后 > 16 → 「分组名最多 16 个字」），两个纯函数都有 `api/FollowApiTest` 单测。
- **不新建输入页**：创建与重命名都复用 `activity/InputDialogActivity.kt`（extras `title`/`initial_text`/`hint`，回传 `input_text`），调用点照抄 `activity/video/local/LocalListActivity.kt` 的 `pendingInputCallback` + `registerForActivityResult` 范式。改名/删除入口是**长按分组标题**弹 `AlertDialog` 两项菜单（`adapter/user/FollowGroupAdapter.kt` 新增 `setOnGroupLongClickListener`），不是新按钮。
- **`groupBar` 是 `res/layout/activity_simple_refresh.xml` 里第四个默认 `gone` 的分组**（前三个见 §7.23/§7.24/§7.25），只有关注列表的「分组模式」才在 `setupGroupBar()` 里点亮，里面只有一行「+ 新建分组」。
- **系统分组不可编辑**：`isEditableTag(tag) = tag.tagid > 0`——默认分组（`tagid = 0`）与特别关注（`-10`）长按只提示「默认分组和特别关注不能改名或删除」。
- **分组模式不再过滤 `count == 0` 的分组**：原先 `loadGroupMode()` 只 `addGroup` 关注数 > 0 的分组，导致刚建好的空分组在列表里看不到、也就没法改名/删除；现在全部列出（空分组展开为空）。
- 增删改成功后一律**整页重拉**（重新 `loadGroupMode()` 造新 adapter——`RefreshListActivity.setAdapter` 只是赋值，没有「只能设一次」的守卫，安全）。

### 7.27 下载服务分层：`DownloadService` 已拆分（26.10.04 批次 8 的 E2）

`app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadService.kt` 从 **1593 行**拆到 **1284 行**，实现按关注点搬到 `service/download/` 下四个新文件（同一 `service` 包的子包，**不是**新模块）。**对外 API 一字未改**——Java/Kotlin 调用点照旧写 `DownloadService.startDownload(...)` / `DownloadService.getDownloadProgress(...)`，`Companion` 里保留全部 10 个 `@JvmStatic` 字段与 24 个 `@JvmStatic` 函数做转发。

| 文件 | 关注点 | 关键点 |
| --- | --- | --- |
| `service/download/DownloadPathSpec.kt`（84 行） | 文件名 / 下载类型 / 结果码常量（新增 `FILE_VIDEO_MERGING = "video_merging.tmp"`） | 纯常量，无 IO 无 Context；`DownloadPathSpecTest` 72 行 |
| `service/download/DownloadProgressMath.kt`（99 行） | `progressForBytes` / `pseudoProgress` / 批次统计 / **DASH 阶段边界与 `dashMergeProgress`** | 纯函数；`DownloadProgressMathTest` 182 行 + `service/DownloadBatchStatsTest` 136 行 |
| `service/download/DownloadProgressStore.kt`（143 行） | 进程级状态：进度映射、`pausedMap`、累计字节、速度采样 | `internal object`；顶层 `DownloadProgressInfo` 是 **public**（被 public 门面返回） |
| `service/download/DownloadRepository.kt`（157 行） | DB 访问（`DownloadSqlHelper` 的增删查改） | `firstDown` 与视频元信息写入一起搬入 |
| `service/download/DownloadNotifier.kt`（122 行） | 通道、两个 Builder、每秒进度、`notifyExit` / `notifyCompletion` | **普通 class 持 Service 引用**，由 `onCreate` new 出来 |

**改下载相关代码前必读的三条**：

1. **锁：`start(Long)` 一行未搬**，仍是 `Companion` 的 `@JvmStatic @Synchronized`（`javap`：`DownloadService$Companion.start(long)` = `public final synchronized`、静态桥 = `public static synchronized`），`started` 的 check-then-act 全靠它；`batchRunning` 守卫（审计 S6）也在原位。**`speedLock`（`DownloadProgressStore` 内）与 Companion 监视器是两把独立的锁，不得互换或合并**——速度采样全程只认 `speedLock`。
2. **刻意保留的现状，不要"顺手修"**：`speedStr` / `isSpeedMode` / `activeDownloadsCount` **不是** volatile（UI 线程读、下载线程写是既有数据竞争）；`totalBytesDownloaded` 允许被减（失败分片回滚）；死代码 `clear()` 与从未赋值的 `toastTimer` 原样保留。
3. **`pausedMap` 必须还是同一个实例**：`activity/video/local/DownloadListActivity.kt` 直接对它 add/remove，`Companion.pausedMap` 的 getter 直接返回 `DownloadProgressStore.pausedMap`，**不能复制**。同理 `notifyTimer` / `statusBuilder` / `completionBuilder` / `notifyManager` 仍声明在 Service 实例上（notifier 只读写、不另存），因此 `notifyTimer` 由 `private` 放宽为 `internal`。

**行为契约不变的部分**：channel id `biliterminal_download`、前台通知 `1027`、`notifyExit` 的 `2`、`notifyCompletion` 的 `id % 100 + 100`、download 表 11 列 / version 4、SP 键（`aria2_enabled` / `aria2_split` / `parallel_download_videos`）。**已知的可见性变化**：`DownloadProgressInfo` 的 FQN 由 `DownloadService$Companion$DownloadProgressInfo` 变为 `com.RobinNotBad.BiliClient.service.download.DownloadProgressInfo`（全仓无显式引用，按旧 FQN 反射的代码会失配）。

**26.10.13 完整性补丁（C3）**：高速单连接与分片失败后的单连接回退复用 `service/download/DownloadStreamTransfer.kt`（无 Android 依赖的流写入器）。GET 长度优先；HEAD 仅在响应成功且长度有效时作提示；未知长度保留 `-1`（不能伪造为 1）。必须真实 EOF、非空且已知长度精确相等才返回 `NORMAL`；网络读失败归 `ERR_NETWORK`，写入/flush 失败归 `ERR_FILE`。阶段完成只在资源关闭成功后发布，非暂停失败只清理本次目标文件（不删任务目录、不碰旧 `video.mp4`/`audio.m4a`）；建连及关闭异常同样优先保留暂停语义。`DownloadStreamTransferTest` 覆盖真实内存流与异常注入；DASH 阶段进度已在 §7.35 末尾改为 0.12 / 0.52 / 0.88 / 1.00（合并层见 §7.54），续传仍未改；本地音轨防重的播放侧补丁见 §7.4（下载替换事务仍未改）。

**26.10.25 分片看门狗与降级（`docs/review/fix-progress.md` §3.63）**：新增 `service/download/SegmentDownloadPolicy.kt`（73 行，`internal object` 纯逻辑，`SegmentDownloadPolicyTest` 8 例）——阈值 `STALL_TIMEOUT_MS = 12s` / `ABORT_JOIN_TIMEOUT_MS = 3s` / `FINISH_JOIN_TIMEOUT_MS = 30s`，判定 `hasStalled` / `isPartialContent` / `isRangeIgnored` / `parseContentRangeStart` / `isContentRangeMismatch` / `shouldDisableSegmentMode`。`downFileSpeedSeg` 的轮询循环**先判停滞再算进度**：超过 12s 没有任何分片收到新字节就 `Call.cancel()` 中止并回退整文件单连接（join 预算收紧到 3s，线程没退出直接报错，绝不并发重下）；`downloadSegment` 强制 `206` + `Content-Range` 起点校验、按分片边界限制写入量、每写一块喂一次看门狗；companion 的 `@Volatile segmentModeDisabled` 是**本次进程内**的降级记忆，命中后后续任务不再分片。下载专用 OkHttpClient 强制 `HTTP/1.1`（实测同一 CDN 直链：5 条 h1.1 连接聚合 ≈ 10.4MB/s vs 5 条 h2 流 ≈ 4.9MB/s），HEAD 额外加 15s `callTimeout`。**`pausedMap` / `speedLock` / companion 锁的三条约束不变**（见上）。

### 7.28 顶部工具条滚动自动收回（26.10.05 批次 8 新增）

- **能力在基类 `activity/base/RefreshListActivity.kt`，默认关闭**：子类调 `setupAutoHideBars(vararg bars: View?)` 才生效（不调 = 零影响，所以对其它几十个列表页安全）。`activity_simple_refresh.xml` 里的 `filterBar`/`sortBar`/`manageBar`/`groupBar` 是 `SwipeRefreshLayout` **之上的兄弟节点**，不在 RecyclerView 内，**不会随列表滚走**，只能手动改 `layoutParams.height` 做收放。**没做成 adapter 头部项**：那会重映射所有业务 adapter 的 `viewType`/`adapterPosition`（同 §7.23 的理由）。
- **判据抽成纯函数 `util/view/ScrollRetractDecider.kt`**：`action(accumulated: Int, collapsed: Boolean, canScrollUp: Boolean): Int` 返回 `NONE`/`COLLAPSE`/`EXPAND`（`THRESHOLD = 12`）。View 层在 JVM 单测里是 Android 桩，断言写了也是假的（见 §36.7 第 1 条），所以判据必须与 Android 解耦才有真单测。
- **展开只有「回到顶部」一条路（26.10.05 第三批真机反馈后收紧，见 `docs/archive/review/fix-progress-history.md` §三十九）**：`EXPAND` 只可能在 `!canScrollUp && accumulated <= 0` 时返回，列表**中间**向上滚一律 `NONE`。原因是**动画的高度变化会顶动列表内容**——条是列表的兄弟节点、位于列表上方，高度一变可见区域跟着变、内容整体位移；用户正按住屏幕拖动时手指底下的条目被推走，表现为「条和滑动手势打架」。收回则随时可以（内容朝手指方向让位，方向一致不冲突）。**旧口径「向上累计滚过阈值就展开」是错的，不要改回去。**
- **两条反直觉但必须保留的设计**：①**已收回时继续向下滚仍返回 `COLLAPSE`**，作用是让调用方清零累加值——若返回 `NONE`，累加值会一直涨，用户往上滚时先要抵消历史正值，表现为「条收起来以后怎么滚都不回来」；②**`collapsed && !canScrollUp && accumulated <= 0` 强制 `EXPAND`**——已在顶部还收着就必须展开，因为列表到顶后不会再产生负 `dy`，否则永远展不开。
- **`naturalBarHeight` 必须缓存**：条是 `wrap_content`，收回时高度被压成 0，0 高度量不出「原本多高」，否则**收得回去、展不回来**。
- **`animateBars` 的三个易错点**：①收回只处理 `visibility == VISIBLE && naturalBarHeight > 0` 的条——**不可见的条不能收**，否则会被记进 `collapsedBars`，展开时把本该 `GONE` 的条点亮；②`barsAnimator?.cancel()` 后**必须立刻置 `null`**（`cancel()` 会同步回调旧动画的 `onAnimationEnd`，那时若还指着旧动画就会把 height 复位成 `WRAP_CONTENT`），并在 `onAnimationEnd` 里用 `if (barsAnimator !== animation) return` 挡下被取消的动画；③**多条共用一个 `ValueAnimator`**（高度按同一条 0..1 进度算），每条各起一个动画会因启动微差错位。
- **接线点**：`activity/user/WatchLaterActivity.kt` 接 `filterBar`（并在 `reloadForFilter()` 里先 `expandAutoHideBars()`，否则点了「未看完」条还是收着的）；`activity/user/favorite/FavoriteVideoListActivity.kt` 接 `sortBar`（并在 `switchSort()` 里 `expandAutoHideBars()`）。**`manageBar` 故意不接**：多选模式下用户正勾着视频，条收走就没法点删除。（同批次的长按操作面板见 `util/LongPressPrefs.kt` 与 `docs/archive/review/fix-progress-history.md` §37.1。）

### 7.29 okhttp 的 `Response` 到底要不要 `close()`（结论更正，26.10.10 回迁）

**旧结论「所有 API 从不 `close()` `Response`，连接与 fd 被占用」是错的**，别再照着它去「补 close」：
`okhttp3.ResponseBody.string()` / `bytes()` 内部就是 `source().use { … }`（字节码调 `CloseableKt.closeFinally`），**body 读完即把连接归还连接池**。
真正会泄漏的只有「拿到 `Response` 却没把 body 读到底就丢弃」的路径（26.09 那轮共 10 处，已全部修掉）。
原报告点名的 `api/UserInfoApi.java:323` / `:368`、`api/ReplyApi.java:254` 都走 `bytes()` 读完，**不是泄漏**。
判据只有一条：**body 读完了没**——读完了就不用管，没读完才必须自己 `close()`。

### 7.30 `IjkPlayerBridge.release()` 不是终结路径：cancel 之后必须立刻重建 scope

`release()` 会被 `activity/video/ShortVideoPlayerActivity.kt:243`（`onViewRecycled`）调用，**同一个 holder 之后还会 `initPlayer()` → `createPlayer()` 复用**。
只 cancel 顶层 `job` 会让 `startProgressTracking()` 后续 launch 进**已取消的 scope**，表现为**进度条从此静默不动**（不崩、只是不动）；正确做法是 `scope.cancel()` 之后**立即重建** scope（现有实现见 `player/IjkPlayerBridge.kt:41-44` 与 `release()` 末尾）。
⚠️ 这和 `AGENTS.md` 记的 `VideoPlayerCore.release()` 一次性 `released` 标志（复用会泄漏 native 播放器）**是两条不同的坑**，不要合并理解。

### 7.31 重定向的 fail-closed 白名单：第二条凭证外流通道

`NetWorkUtil.isBilibiliHost(host)`（`util/NetWorkUtil.java:179`，配套白名单 `BILIBILI_AKAMAI_MIRROR_HOSTS` `:169`）对手动跟跳做**精确主机校验**（fail-closed，白名单外的 host 一律不跟），并用手动跟跳请求的 `request.tag` **累计跳数**封死「白名单内互相跳转」的无限重定向环（拦截器 `:111-127`，每次跟跳前先 `response.close()`）。
改重定向 / 跟跳相关代码前必读——这是 6.4 之外**另一条凭证外流通道**的封堵，别当成「多此一举的 host 判断」删掉。

### 7.32 `PlayerActivity.onStop` 不停播是 F1 的「有意设计」，别再说成 bug

后台播放（F1）故意不在 `onStop` 停播（见 `activity/video/PlayerActivity.kt:1679-1684`）。26.09 审计曾把它记为「后台耗电缺陷」，复查已撤回该结论。
另外 F1 **当前没有 wakelock**（`WAKE_LOCK` 未声明、也没调 `setWakeMode`），熄屏表现属**待真机确认项**，不是「漏加了」。

### 7.33 `api_retry_max_times` 目前只有读、没有设置项

`util/NetWorkUtil.java:237` / `:300` 会读 `api_retry_max_times` 与 `api_retry_interval_seconds`，但**设置页没有任何写入方**——用户在设置里调不了这两个值。
别把它当成「已可配置」而重复实现；真要开放，得同时改 `util/SettingsKeys.kt` + 设置页 `SettingSection` + `activity/settings/SettingsIndex.kt` 三处。

### 7.34 收藏夹上限与「默认收藏夹」的识别规则

约定值（`api/FavoriteApi.java` 的 `MAX_COUNT_DEFAULT_FOLDER` / `MAX_COUNT_CUSTOM_FOLDER`）：**默认收藏夹 50000、用户自建 1000**；
**默认收藏夹用 `attr & 2 == 0` 识别**（`api/FavoriteApi.isDefaultFolder(int)`）：v3 系的 `created/list` 与 `list-all` 都给 `attr`（bit0=1 私密、bit1=0 才是默认），比「`index == 0`」可靠——`index` 判据只对已废弃的 `getBoxList` 成立。线上自建收藏夹常见值是 `attr=22`（bit1 已置位）。

26.10.23 接口迁移后，上限**不再有任何服务端字段**可读：`x/v3/fav/folder/created/list`（我的收藏夹列表 / 移动目标列表）与 `x/v3/fav/folder/created/list-all`（加收藏时的选择框）**都不返回 `max_count`**，统一用 `FavoriteApi.maxCountOf(isDefault)` 本地算。

历史坑（26.10.16，已随老接口移除而消失）：老 `space.bilibili.com/ajax/fav/getBoxList` 对自建收藏夹恒返回 `max_count = 999`（实际上限 1000），「我的收藏夹」页因此把 1000 条的自建夹显示成 `1000/999`；当时用 `resolveMaxCount()` 过滤 999。现在 `getBoxList` 与 `resolveMaxCount()` 都已删除，别再按"服务端会给上限"写代码。

### 7.35 B 站 `playurl` 与签到类接口的接入常识（26.10.10 回迁）

取流（`playurl`）：

- `fnval` 是**按位或**叠加的格式位（16 / 64 / 256 / 512 / 1024 / 2048 / 4048），`4048` = 全开。
- 流 URL 的防盗链校验要求 **`Referer` 为 `.bilibili.com` 域且 UA 非空**，否则 **`403 Forbidden`**；`platform=html5` 可跳过防盗链。
- 缺 WBI 签名（`w_rid` / `wts`）时接口返回 **`-403`**（与 6.2 的签名实现配套看）。
- 流 URL **有效期约 120 分钟**，过期必须重新取流，**不可缓存进离线队列复用**。
- JSON 里 URL 中的 `&` 被转义成 **`\u0026`**，解析时必须还原。
- **选流优先级**（`model/DashData.java`）：音频优先普通 AAC（`mp4a.*`），杜比 / 无损仅作兜底（`:114-143`）；视频按 **H.264 > H.265 > AV1**（`:74-112`）。**下载侧有合并层**：`util/MediaMerger.kt` 在 DASH 双文件下载完成后把它们 remux 成一个 `video.mp4`（26.10.25，见 §7.54）；播放侧的外部音轨路径仍保留，老缓存双文件照旧能播。

验接口的廉价判据：无 Cookie 调这些接口，**返回 `-101` 即说明接口存在**（`-101` = 未登录，不是「接口没了」）。
已实测地址：直播签到 `POST https://api.bilibili.com/xlive/web-ucenter/v1/sign/DoSign`（配套 `WebGetSignInfo`）、粉丝勋章佩戴 `/xlive/web-room/v1/fansMedal/wear`、漫画打卡 `/twirp/activity.v1.Activity/ClockIn`。
**一处实现分叉**：大会员签到最终**没有走**规划里的 `/pgc/activity/score/task/sign`，而是走 `VipApi.addExperience()`（`POST https://api.bilibili.com/x/vip/experience/add`）。未实现的签到/漫画类功能清单见 `docs/superpowers/plans/2026-08-27-new-features-roadmap.md`。

### 7.36 蓝牙 A/V 同步：未排期，且与前两套偏移机制互不合并

全工程**零实现**（`audioDelay` / `syncOffset` 无命中，清单里**一个蓝牙权限都没有**）。将来若要做，两条硬约束：

1. 接入点是 `PlayerActivity.progressChange()`，**只偏移「显示时间基」，绝不动 `ijkPlayer.currentPosition`**；
2. **语义与既有 `player_subtitle_delta`（字幕校准）不同，不要合并成一个偏移**，也**绝不能套进 DASH 外部音轨的 800 ms 强对齐判断**。

推荐底座是方案 A（`SettingsKeys.PLAYER_AV_SYNC_OFFSET` 手动微调）；方案 B 自动套偏移需要 `BLUETOOTH_CONNECT` 权限。方案 A–D 的完整对比见 `docs/archive/bluetooth-av-sync-report.md`。

### 7.37 共存版：product flavor 拆分（26.10.11 新增）

同一份代码出两个包：**普通版** `standard` 与**共存版** `coexist`，两包可同时安装。`app/build.gradle` 里加了 `flavorDimensions += 'channel'` 与两个 flavor；共存版靠 `applicationIdSuffix ".coexist"` 换包名，两个 flavor 各有一个编译期常量 `BuildConfig.COEXIST`（用来区分更新检查该认哪种附件名）。

改这块必须记住四条：

1. **单测任务名带 flavor**：`testDebugUnitTest` **已不存在**，要写 `testStandardDebugUnitTest` / `testCoexistDebugUnitTest`（CI 与 `AGENTS.md` 已同步改）。产物也按 flavor 分目录：`app/build/outputs/apk/<flavor>/<buildType>/app-<flavor>-<abi>-<buildType>.apk`。
2. **发布附件名不能变**：老客户端的更新检查按**精确文件名**匹配 `app-<abi>-release.apk`（`util/UpdateRelease.kt`），所以 `build-release.yml` 加了「整理发行 APK」一步，把 `app-standard-<abi>-release.apk` 改名回历史名再进 Release；共存版是 `app-coexist-<abi>-release.apk`，由 `UpdateRelease.apkNamePrefix(BuildConfig.COEXIST)` 选中。**共存版拿不到自己的附件时不会退化成装错包**：`UpdateReleaseTest` 锁了「只有普通版附件时返回 null」，真取错了 `util/ApkVerifier.kt` 也会因包名不符拒装。
3. **唯一硬编码包名已消除**：FileProvider 的 `android:authorities` 改成 `${applicationId}.FileProvider`（代码侧本来就是 `packageName + ".FileProvider"`，`DownloadActivity` / `PlayerApi` / `UpdateManager` 三处）。漏改会让下载安装与分享在共存版上崩。
4. **名称两版一致，只靠图标区分**（用户 26.10.11 明确要求）：应用名与桌面名都取 `@string/app_name`（「RE:哔哩终端」），**清单里 `SplashActivity` 的 label 必须继续指向 `@string/app_name`**——桌面图标下显示的是**启动 Activity 的 label**，之前那里写的是字面量「哔哩终端」，所以就算 `app_name` 是对的、桌面名字也是另一个值。共存版不再覆盖任何字符串，只覆盖图标：`app/src/coexist/res/mipmap-nodpi/icon.png`（上游原图标）压过 main 的 `icon.webp`。**别再给共存版加「共存版」后缀的名字**——那是中途做过、已被用户否掉的方案。

数据天然隔离：登录态 / 设置 / 缓存 / `/Android/media/<包名>/` 都分开，**共存版需要重新登录**。外链（http / https / bilibili scheme）两个包都会响应，系统弹选择框——这是明确接受的取舍，别去「优化」掉。

### 7.38 番剧详情页与番剧进度线（26.10.11 新增）

对齐 `cyq114514/Re-BiliTerminal` 后补的三块（**A 详情页 UI + C 进度线 + D 历史卡片续播**；B 进详情页自动定位上次观看、E 实验室诊断页**明确不做**）。远端那个 `activity/video/BangumiActivity.java`（番剧主页：新番/追番/索引）在全部 tag 里都不在 manifest / `MenuActivity`，是死代码，**别照抄**。

- **详情页选集两套 adapter，别合并**：番剧详情页用 `adapter/video/EpisodeCardAdapter.kt`（两行卡片：集号 + `episode_playing` 声波 + 会员/限免角标 + 长标题），直播页的清晰度/线路仍在用 `adapter/video/MediaEpisodeAdapter.kt`（单行 MaterialButton）——语义不同，改一个不要顺手"统一"。季 tab 用 `adapter/video/SeasonTabAdapter.kt` + `res/layout/cell_season_tab.xml`（选中加粗上 `ColorScheme.getPrimary`）。
- **`sections` 是运行期状态，不再直读 `bangumi.info.sectionList`**：`BangumiInfoFragment` 用 `sections`/`currentSeasonId`/`currentSeasonType` 三个字段当"当前展示的季"，切季时整体替换（`seasonCache` 缓存已加载的季，`seasonSelection` 记每季上次看的位置）。**只有一季时不显示季 tab 行**；当前季不在 `info.seasons` 里（剧场版等）时在首位补一个"正片" tab。播放必须用 `episode.toPlayerData(currentSeasonId, currentSeasonType)`——用 `info.season_id` 会把第二季的心跳记到主季上。
- **`getSeasonDetail` 只发 `pgc/view/web/season`**（远端还额外查 `user/status` 拿季级 `last_ep_id` 自动定位，那属于不做的 B 项）。追番按钮的缓存键 **必须是 season_id**（`BangumiApi.FOLLOW_KEY_PREFIX`），远端追番列表页拿 media_id 写同一前缀，是它自己的 bug。
- **进度上报的番剧专属口径**（`activity/player/PlayerActivity.kt` + `api/HistoryApi.java` + `api/PlayerApi.java`）：
  - 心跳间隔按类型分：`progressReportIntervalMs` 是 `get() = if (epid != 0L) 5000L else 15000L`；**投稿视频仍是 15 秒**。
  - `onCompletion` 立刻上报 `HistoryApi.PROGRESS_FINISHED = -1`（服务端约定的"已看完"），且退出路径的补报**必须继续报 -1**——改报当前位置会把服务端的"已看完"覆盖成"看到最后几秒"。`reportHistoryPgc` 的守卫是 `progress == 0`（**不是 `<= 0`**，否则 -1 被吃掉）。
  - `PlayerApi.getLastPlayProgress` 只在 **`last_play_cid == 请求 cid`** 时才采用 `last_play_time`；取不到该字段（0）也按"不属于这一集"处理，由观看记录兜底。这是"看着这一集点开另一集却从上一集位置起播"的闸门。观看记录兜底走 `HistoryApi.findProgressMsByAid(aid, epid)`，解析抽成纯函数 `parseHistoryProgressMs`（有单测）。
- **进投稿视频详情页要"登记"一条观看记录，判据是"这一P是不是本次打开的分P"，不是"有没有进度"**（`activity/video/info/VideoInfoFragment.kt` 的纯函数 `shouldReportHistoryOnEnter(cidHistory, cid)`，有单测）：
  - 详情页本身就是一次"打开观看"。`PlayerApi.getVideo` 之后**无条件不报**会让从没看过的视频永远进不了观看记录（用户 26.10.15 实测：点进详情页历史里什么都不多，只有真的播放了才出现一条——播放器那侧 `PlayerActivity` 自己会上报，所以这个缺口只有详情页这一处）。
  - 但也不能无条件报（26.10.02 的 `if (progress > 0)` 就是拿"有没有进度"当判据，把新视频的登记一起挡掉了）。`progress == 0` 有两种相反语义：`cidHistory == cid` 是"服务端对这个稿件**没有记录**"（getVideo 把 `last_play_cid == 0` 归一化成 `cidHistory = cid`、`progress = 0`），报 0 只是登记、无进度可覆盖；`cidHistory != cid` 则是"进度属于**别的分P**"（多P视频：上次看第 3P，详情页预取第 1P），报 0 会把第 3P 的续播抹成 0。
  - 所以判据收敛成一条 `cid != 0 && cidHistory == cid`：上报的 cid 用 `data.cid`（详情页打开的那一P），不用 `cidHistory`。
- **历史卡片续播**：`adapter/video/VideoCardHolder.kt` 的 `media_bangumi` 分支在 `card.epid > 0` 时先 `BangumiApi.getEpisodeFromEpid`，成功就直接进 `JumpToPlayerActivity`（`card.progress` 是**秒**，要 ×1000 成 `PlayerData.progress` 的**毫秒 Int**），失败才退回详情页并提示。

### 7.39 中央加载遮罩（`activity_simple_viewpager.xml` 的 `loading`）的收起时机（26.10.13）

`res/layout/activity_simple_viewpager.xml` 里的 `loading` 是 **ViewPager 的兄弟节点**（不是页内元素），默认可见、盖在最上层，所以它一旦没收起，**当前页已经加载完的内容会被挡住、触摸也被吃掉**。收起统一走宿主的 `crossFade(...)`。

- **`AnimationUtils.crossFade` 两个重载的参数名有误导**：`crossFade(A, B)`（2 参）内部转调 3 参 `crossFade(toHide, toShow, duration)`，所以 `crossFade(A, B)` 的语义是 **hide A / show B**（先传要隐藏的）。宿主里的 `crossFade(loading, fragmentView)` 即"收起遮罩、亮出页内容"。
- **收起时机只能绑「本页内容装配完成」，绝不能绑任何慢请求**。`VideoInfoFragment.initView()` 曾把 `onFinishLoad()` 放在后台**取流预取**（`PlayerApi.getVideo`）之后 —— 取流是弱网下最慢的一步，而 `viewPager.offscreenPageLimit = fragmentList.size` 会把评论页**一并建好**（评论请求比取流快得多），于是出现"评论已出、加载图还盖在上面且挡触摸"，且带 `seekReply` 时 `currentItem = 1` 直接落在评论页，必然撞上。26.10.13 改为在 `initView()` 末尾收起；预取只填 `playerData`，未就绪时 `playClick`/`startDownloadFlow` 已有"视频信息还在加载中"兜底。
- **每一条提前 `return` 的分支都要自己收尾**。`BangumiInfoFragment.initView()` 的"敬请期待"分支（没有可用季/集）原本直接 return 且没调 `onFinishLoad()` → 遮罩**永不收起**，用户永远停在加载图上。26.10.13 已补。
- 走这个布局的宿主共三个：`VideoInfoActivity`（视频详情与番剧详情共用）、`OpusInfoActivity`、`DynamicInfoActivity`（后两者在 `adapter` 设好后就地收起，无此问题）。`LiveInfoActivity` 是另一套布局，且在数据到达**前**就收起，口径不同。
- 新增宿主 / 新增提前返回分支时，按本条自检：**遮罩是否在所有路径上都会被收起**。

### 7.40 Debug 包的自动化测试基建（26.10.14 新增）

**完整用法见 `docs/review/agent-test-guide.md`**，这里只记改代码时必须知道的取舍。

这一批解决的是「能编译、能跑单测，但没法自动验证真机行为」——`docs/archive/review/device-test-plan.md` 里 88 条用例一条未执行，根因就是 debug 包没给自动化留入口。四个新增物：

- **`debug/DebugBridge`（`app/src/debug/java/...`）**：exported 的 `BroadcastReceiver`，用 `am broadcast -n <包名>/.debug.DebugBridge` 驱动，命令 `ping`/`agent`/`get`/`set`/`remove`/`dump`/`start`/`activities`/`http`/`log`/`logs`/`logclear`/`trail`/`logring`/`top`。**类与清单都在 debug 源集**，release 编译时该类根本不存在（不需要 R8 规则）。
  - **`start` 是它最有价值的命令**：全应用除 `SplashActivity`/`GetIntentActivity` 外都是 `exported="false"`，adb（uid 2000、非 root）拉不起来；而**进程内 `startActivity` 不受 exported 限制**，等于把全部页面开放给 agent。但受 Android 10+ 后台启动限制，应用不可见时会被静默丢弃——需 `adb shell appops set <包名> SYSTEM_ALERT_WINDOW allow`（悬浮窗权限是该限制的豁免条件之一），`start` 的结果里会提示当前是否已授权。
  - **`goAsync()` + `setResultData`**：`onReceive` 在主线程，`http` 命令要发网络请求，直接做会 ANR。`goAsync()` 把广播生命周期延到 `finish()`，真正的活在 `CenterThreadPool` 上跑。**结果同时写 `logs/bridge-last.txt`**——个别 ROM 的 `am` 不转发 receiver 结果，文件是兜底。
- **`BiliTerminal.AGENT_TEST_MODE` + `applyLogSwitches()`**：Agent 测试模式总开关（`SettingsKeys.DEV_AGENT_MODE`）。**它是日志级别、落盘、HTTP 追踪、UETool 跳过四件事的唯一落地处**——改这些开关一律调 `applyLogSwitches()`，别在别处直接改 `Logu.*`。`agentMode = debugBuild && 开关`，`debugBuild` 是编译期常量，release 下 R8 把整段折叠，行为与改动前一致。
  - **改完即生效**是硬要求：原实现里日志开关只在 `Application.onCreate` 读一次，改完要重启——自动化流程里「重启」= 上下文丢失 + 一轮额外等待。所以 `SettingGroupActivity.buildDevGroup()` 的每个开关都带回调。
- **`Logu`**：新增**异步落盘**（业务线程 O(1) 入队，`rebili-log-writer` 守护线程写，队列 4096 满则丢弃并计数）与单文件 2MB 上限（超限砍前半留后半）。落盘位置 `getExternalFilesDir("logs")`，回退内部 `files/logs`。文件行格式 `MM-dd HH:mm:ss.SSS 级别 调用者 | 内容`。
  - **logcat 的 tag 一律不动，仍是 `类名>方法名`**（26.10.14 一度改成统一常量 `ReBili`，已回退）。理由：这个 tag 是**既有诊断契约**——`docs/archive/review/device-test-plan.md` D3-11~D3-14 写死了 `adb logcat -s "PlayerActivity>onPrepared"` / `"PlayerActivity>getExtras"` / `"PlayerActivity>checkStall"`，`api/NoteApi.java` 的 `adb logcat -s BiliNote` 是同一思路，全工程 269 处 `Logu.*` 调用点都吃它。**改 tag 等于一次性作废这批排障指令。**
  - 「一次抓全」用**按进程过滤**而不是改 tag：`adb logcat --pid=$(adb shell pidof -s <包名>)`；真要事后取证就读落盘日志（调试桥 `log` 命令）。
  - **别改成同步写**：`Logu.d` 会被 UI 线程调用，同步写文件等于把卡顿直接带给用户。落盘队列里放的是**任务**而非「文件名+内容」，这样写行 / flush / 清空三种操作全在同一条线程上串行，`BufferedWriter` 不会被跨线程碰。
  - 工程里还有 38 个文件、110 处直接调 `android.util.Log`（tag 五花八门，`"debug"` 占 51 处），**不受 `Logu` 开关控制、也不落盘**，属尚未收口的历史遗留。
- **`SplashActivity.ensureUEToolOverlayPermission()`**：Agent 模式下**整体跳过**。原来未授权时会 `return` 掉启动主流程并跳到系统设置页（agent 看到的就是"应用起不来"），授权后悬浮窗还会盖住界面吞掉点击——对人工是便利，对自动化是灾难。

安全边界（三条，缺一不可）：**只进 debug 源集**（发布包不含）、**挂 `android:permission="android.permission.DUMP"`**（DUMP 是签名级权限，普通第三方应用拿不到，adb shell 持有，本应用天然放行）、**token 校验**（防误投递，不是安全边界——token 写在 APK 里可被反编译读出）。**别为了"方便"把 DUMP 权限摘掉**：那等于让同设备上任何应用都能 dump 登录态。

---

### 7.41 两条「设备能力缺失」型崩溃：WebView 与系统设置页（26.10.14）

手表 ROM 与手机 ROM 的差别不只在屏幕大小，**还在于它可能根本没有某些系统组件**。这两条都是线上实测崩出来的，共同点是「本来可以优雅降级，却把整个应用带崩」。

**① 不要在任何布局里写死 `<WebView>`。**
`WebViewFactory.getProvider()` 在**进程 uid 为 root(0) / system(1000)**（或应用声明了 `android.uid.system` 的 `sharedUserId`）时会直接抛 `UnsupportedOperationException("For security reasons, WebView is not allowed in privileged processes")`——框架层从 5.1 起就禁止特权进程创建 WebView。本工程全仓库无 `sharedUserId` 声明，所以命中的是「那台手表把应用跑在了特权进程里」。
**后果**：布局 inflate 阶段就抛 `InflateException`，异常冒到 `ActivityThread` 直接崩应用。
**正确姿势**（见 `activity/settings/login/CaptchaWebViewActivity.kt`）：布局只放 `FrameLayout` 容器，WebView 运行期创建，创建前先探一次（建一个再 `destroy()`，捕获 `Throwable`），失败就提示 + `finish`，别让它成为致命路径。
**注意 `CookieManager.getInstance()` 同样会触发 provider 创建**，所以它必须放在「确认 WebView 可用」之后。
另：`WebView` 目前全工程**只有 `activity_captcha_webview.xml` 一处**（已改成容器），新增任何 WebView 页面都按上面的姿势来。

**② 跳系统设置页一律要兜底。**
`Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 这类 action **在手表 ROM 上常常没有对应 Activity**，`startActivity` 抛 `ActivityNotFoundException`，从调用页的 `onCreate` 冒出去就是 `Unable to start activity ComponentInfo{…}`。
**正确姿势**（见 `util/FileUtil.requestStoragePermission`）：带包名的专属页 → 通用列表页 → 只弹提示，三级降级；`startActivity` 包在 try/catch 里，且要吞**所有** `Exception`（部分 ROM 该页存在但未导出，抛 `SecurityException`，处理方式相同）。
全工程 `Settings.ACTION_*` 跳转只有两处：`FileUtil`（已兜底）与 `BiliTerminal.requestOverlayPermission`（悬浮窗，**只在 Debug 构建的 UETool 流程里调用**，release 不可达）。
`requestOverlayPermission` 进一步改成**返回 `Boolean`**：拉不起设置页就提示 + `false`，`SplashActivity.ensureUEToolOverlayPermission()` 只在 `true` 时停在启动页等 `onActivityResult`——**别退回「无脑 `return true`」**，那会让启动页卡在一个永远不会回来的回调上（手表 ROM 缺该页时就是死等）。

**③ 存储权限的判据是「这个目录能不能写」，不是「有没有 MANAGE_EXTERNAL_STORAGE」（26.10.18 改）。**
`Environment.isExternalStorageManager()` 只回答「有没有全盘权限」；而默认视频目录 `Android/media/<包名>/` 是**应用专属外部存储**，API 29+ 写它**根本不需要权限**——按授权判会把默认路径下的下载判成「没权限」，手表 ROM 又没有那个设置页，于是**下载被永久挡死**。
**正确姿势**（`util/FileUtil.canWriteDirectory` / `ensureDirectoryWritable`）：先 `mkdirs` 再 `createTempFile(".write_probe")` 试写并删除，写不进去才去申请权限。调用点必须传**真实目标目录**：`activity/video/info/VideoInfoFragment.kt` 的 `downloadClick()`、`activity/video/local/LocalListActivity.kt` 传视频目录，`activity/DownloadActivity.kt` 的图片下载（`type == 0`）传 Intent 里的 `path`（公共 `Pictures/`）。新增任何「写文件前先要权限」的路径都按这套来，**别再加 `checkStoragePermission()` 式的授权判据**。

**④ 默认目录必须「全版本免权限」，全盘权限只是可选项（26.10.22 改）。**
③ 只解决了「怎么判可写」，没解决「判成不可写之后怎么办」：用户一旦把路径改到公共目录、或图片要写公共 `Pictures/`，Android 11+ 就需要「所有文件访问」，而手表 ROM 没有那个设置页 → 试写永远失败 → 下载仍被永久挡死（issue #42 的延伸）。现在整条链路按下面的语义走：

- **默认目录按 API 分档**（`util/StoragePathPolicy.defaultVideoDir`，纯函数、有 JVM 单测）：API ≥ 21 用 `Android/media/<包名>/`（应用专属媒体目录，等价 `getExternalMediaDirs()`，免权限且是老默认值）；API 19/20 用 `getExternalFilesDir("video")`（旧系统上 `Android/media` 仍是公共存储语义），取不到再退内部 `filesDir/video`。
- **老用户不迁移**（`StoragePathPolicy.chooseVideoDir`）：`save_path_video` 没配置过、但老默认目录里还有**非隐藏**文件 → 继续用老目录。判据 `hasRealContent` 把 `.nomedia` / `.DOWNLOADING` / `.write_probe` 这类隐藏标记排除在外，否则一个空标记就能把老用户永远钉在旧目录。
- **写不进去就降级，不挡下载**：`FileUtil.resolveVideoDownloadDir(Context)` / `resolvePictureDownloadDir(Context, File)` 先试写正式目录；失败则改用应用专属目录（`externalFilesDir/video`、`externalFilesDir/Pictures`）继续，首次降级 toast 一次实际保存路径；只有连它都写不了（存储满/只读）才返回 `null`，由调用方停下。
- **降级状态是进程级标记**（`videoFallbackActive` / `pictureFallbackActive`）：`getVideoDownloadPath()` 直接返回降级目录，于是下载服务、缓存列表、元数据读写看到的是**同一个根目录**（各自现算会出现文件一半在正式目录、一半在降级目录，列表/删除逻辑全对不上）。正式目录恢复可写时，下一次 `resolve*` 自动清标记并把降级期间的缓存 `mergeMoveDirectoryContents` 搬回正式目录（只增不覆盖，同名改成 `name (1).ext`，跳过 `.nomedia`）。
- **下载链路不再跳系统设置页**：`requestStoragePermission` 只留给设置页「去系统设置授予「所有文件访问」」这个手动入口（`activity/settings/SettingGroupActivity.kt` 的 `buildDownloadGroup`）；下载/缓存的入口是 `LocalListActivity`（`onCreate` + `onResume` 复检，降级状态变了就重扫列表）、`VideoInfoFragment.downloadClick()`、`DownloadActivity.onCreate`（按 `type` 分图片/视频目标）、`VideoQuickCache.handle`、`DownloadService.startSingle` / `startDownload` / `onStartCommand`（服务可能在新进程里被恢复，标记是空的，必须重新 resolve）。
- **设置页「缓存路径」预填的是 `getConfiguredVideoDownloadPath()`**，不是运行时降级目录：降级时不能让用户以为自己配置的路径被覆盖了。
- **别把降级做成「回退 targetSdk 29 用 requestLegacyExternalStorage」**：targetSdk 34 下它已不生效，且属能力倒退。要「让用户在相册/文件管理器里看到文件」应走 MediaStore 写入（API 29+ 免权限，但只适合媒体单文件，不适合缓存目录）或 SAF 选目录（有 DocumentsUI 时），细节见 `docs/review/fix-progress.md` §3.52。

---

### 7.42 播放列表（pagenames/cids）的两种语义与切页契约（26.10.15）

`PlayerData.pagenames` / `cids` 这一个通道被**两种完全不同的东西**复用，切页逻辑必须知道自己是哪一种：

| | 真实多P | 收藏夹「虚拟合集」 |
| --- | --- | --- |
| 来源 | `VideoInfo.toPlayerData(i)`（`x/web-interface/view` 的 `pages[]`） | `FavoriteVideoListActivity.playFavoriteVirtualCollection` |
| `pagenames[i]` | 第 i 个分P 的标题 | 收藏夹第 i 条视频的标题 |
| `cids[i]` | 第 i 个分P 的 **cid** | 第 i 条视频的 **aid**（切页时现查它的首个 cid） |
| 切页动作 | `playerData.aid` 不变、`cid = cids[i]` | `playerData.aid = cids[i]`，再 `x/web-interface/view` 反查 cid |

- **必须显式标记，禁止靠 `aid != cids[i]` 猜**（26.10.15 修的就是这个）：真实多P 下 av 号与 cid 永不相等，判据恒真 → 把 cid 当 aid 查 → `62002 稿件不可见` → cid 落 0 → 取流 `-400` → **切P 失败、播放器卡死**。标记走 `PlayerData.virtualCollection` → `jumpToPlayer` 的 `virtual_collection` extra → 播放器 `isVirtualCollectionMode`。新增任何"把一串视频当分P 播"的功能，都要顺手打这个标记。
- **切页后 `aid` 与 `cid` 必须一起同步**（`switchToOnlinePage` 的 `runOnUiThread` 块）。只同步 `cid` 会让虚拟合集的进度上报变成 `reportHistory(第一个视频的 aid, 第 N 个视频的 cid)`——**把 N 的进度写进 1 的记录**。
- 本地缓存文件夹的"虚拟合集"走的是另一条路（`switchToLocalPage`，`videoUrlList` 从 Intent 拿），不碰这两个字段，所以不需要标记。
- 真机复现/验证手法与日志见 `docs/review/fix-progress.md` §3.17。

### 7.43 虚拟合集的嵌套层：合集 / 分P（26.10.15 起，26.10.16 改为手动进入）

用户的心智模型是一棵**目录树**，不是"几种并列类型"：

```text
收藏夹（虚拟合集）= 根目录
├── 单视频 / 合集里的单视频.mp4   ← 直接播
├── 分P视频/                     ← 文件夹：进去看各分P，退出来回根目录
└── 合集/                        ← 文件夹：进去看各剧集，退出来回根目录
```

**进入一律手动**（26.10.16 用户明确要求，别改回自动）。选集卡片上一行 `page_selector_nest` 承担进出两个方向：

| 所在层 | 那一行显示 | 点击效果 |
| --- | --- | --- |
| 根目录层，当前条目是"文件夹" | `进入分P（N 个）` / `进入合集 · 标题（N 集）` | 换播放列表进层 |
| 任一嵌套层 | `← 返回主合集` | 弹回根目录层 |

- **为什么不自动**：自动进入会在用户只想顺着收藏夹听的时候，突然被换成一门 105 讲课程的分P 列表，**收藏夹里排在后面的条目全被跳过**。判据抽在 `updateEnterable(videoInfo)`，合集优先于分P（外层"一系列视频" vs 内层"一个视频的段落"）。
- **层状态全在 `PlayerActivity` 内部**（`collectionId`/`collectionTitle`/`multiPageAid`/`multiPageTitle`/`returnPlaylist`），**不走 Intent**；`PlayerData` 只有 `virtualCollection`（cids 装 aid 还是 cid）与 `enterableInfo`（起播那一条的 `VideoInfo`，因为它的播放列表由构建方直接拼、不走 `switchToOnlinePage`）。`enterableInfo` 的 `putExtra` **必须显式转 `Parcelable`**——`VideoInfo` 同时实现 `Parcelable`/`Serializable`，不转会报"引用不明确"。
- **两层的 `cids` 语义不同**：合集层装 **aid**（`virtualCollection = true`，切集时反查 cid）；分P层装**真 cid**（`virtualCollection = false`，切页走普通多P 分支，且 `aid` 要换成该视频的 aid）。
- **零额外请求**：`switchToOnlinePage` 本来就要 `getVideoInfo(aid)` 拿首个 cid，响应里的 `ugc_season` 是白拿的。**别为这件事新增请求**；反过来，改 `getInfoByJson` 时别把 `collection` 的解析去掉。
- 层内播完最后一个分P/剧集 → 自动弹回根目录（受 `auto_next_enabled` 管，与"自动下一P"同一档语义）。
- 根目录层的卡片标题用 `rootTitle`，**不要用 `videoTitle`**：后者会被 `doSwitchPage` 改成当前条目名，从嵌套层返回后会显示成「某条目名（1/20）」，看着像文件夹名其实是列表里的某一条。
- 展平逻辑是纯函数 `model/Collection.toPlaylist`（有单测）：跨节按节顺序、节内按集顺序；`sections` 为空时退回 `cards`。
- 台账与真机记录见 `docs/review/fix-progress.md` §3.18 / §3.24；接口侧结论见 `docs/bilibili-api-notes.md` §5。

### 7.44 线程边界上必须兜住的两类东西：销毁后的 UI 回调 + `Error` 型失败（26.10.19 定稿，崩溃版本 26.10.16，#43–#49）

两条线上崩溃（§3.19 账号切换页、§3.29 用户动态页 + 播放器原生库）根因不同，但**失败位置是同一个：跨线程 / 跨层的入口处没有重新判据**。

1. **后台请求回主线程时必须重新判活**：`CenterThreadPool.run { 网络请求 → runOnUiThread { 用 Activity/View/adapter } }` 是仓库最常见的写法，而请求一起跑，用户随时可能返回、页面随时可能被系统回收。**判活必须写进 `runOnUiThread` 的 Runnable 内部**（判完到 Runnable 真正执行之间，主线程完全可能已经跑完 `onDestroy`），只判 post 之前是错的。`activity/base/BaseActivity.kt` 重写了 `isDestroyed()`（语义含「正在 finish」），所以 Glide 的 `assertNotDestroyed` 在这个窗口里更容易被踩到；`activity/base/BaseFragment.kt` 的 `runOnUiThread` 自 26.10.20（#62）起已在 Runnable 内部判 `isAdded && view != null`（`activity/search/SearchFragment.kt` 里那份重复实现同步改过），但**直接 `CenterThreadPool.runOnUiThread { }` 的地方仍然要自己补**——只判 post 之前一律是错的。判活谓词的落点：Glide 走 `util/GlideUtil.canLoad(View)`，其它统一用 `isDestroyed` / `isAdded`。
   - **fragment 里取界面文案要用 application context**（`getAppContext().getString(...)`）：在 detached fragment 上调 `Fragment.getString(...)` 会抛 `IllegalStateException: Fragment … not attached to a context.`，栈形如 `getString → getResources → requireContext`（#62 的形态）。26.10.19 的文案迁移把一批「detach 后写 `setText("字面量")`」的静默无效改成了 `getString(R.string.…)`，于是登录页扫码轮询在用户点「跳过」后炸出来（`docs/review/fix-progress.md` §3.38）。**纯文案先在后台线程取好再 post**；必须用 activity 的整段（`startActivity(requireContext())` / `requireActivity().finish()`）包进 Runnable 内部的 `if (isAdded)`。
2. **`Error` 不是 `Exception`**：`UnsatisfiedLinkError`、`NoClassDefFoundError`、`NoSuchFieldError` 都继承 `Error`，工程里那些 `catch (e: Exception)` 一律兜不住——它们会直接冒到主线程把进程带走。原生库（ijk 的 `libijkffmpeg.so` / `libijksdl.so` / `libijkplayer.so`、`DanmakuFlameMaster` 的 `ndkbitmap`、`brotlij` 的 `brotli`）都可能在个别设备上 `dlopen` 失败（#48 的 `Bad file descriptor` 就是设备侧文件读不出来）。**新的原生能力入口必须用 `catch (Throwable)` 兜住并降级**，别在业务代码里裸调 `System.loadLibrary` / `loadLibrariesOnce`。
3. **ijk 的专用约束**（`player/IjkNativeLibs.kt`，26.10.19 新增）：`IjkMediaPlayer.loadLibrariesOnce` 三个库全成功才置静态 `mIsLibLoaded`，而 `IjkMediaPlayer()` **构造内部会再调一次** → 只包 `loadLibrariesOnce` 不够，构造本身也要 try/catch；失败后 `IjkNativeLibs` 在本进程锁死（不再重试、不再刷日志），并采集 ABI / `nativeLibraryDir` / 每个 `.so` 的存在性·可读性·大小 / 原始栈进 `Logu`，在用户开启崩溃自动上报时上传一次。**改播放相关代码时一律 `IjkNativeLibs.ensureLoaded(context)` + 判返回值**，不要直接 `IjkMediaPlayer()`。`IjkPlayerBridge.createPlayer` / `VideoPlayerCore.createPlayer` 已改为返回 `Boolean`，失败由调用方提示并退出；`PlayerActivity.onCreate` 失败时 `return`，此时 `setDisplay()` 还没被 `postDelayed` 排上，`ijkPlayer!!` 不会被执行。

全文见 `docs/review/fix-progress.md` §3.19 / §3.29。

### 7.45 26.10.19 审查修复批次留下的新约束

1. **落盘必须先脱敏**：`util/Logu.java` 的 `appendToFile` 现在是「`LogSanitizer.sanitize` → shorten → enqueue」，`util/NetWorkUtil.java` 的 HTTP 追踪进 logcat 前也先脱敏。**新增任何写文件/打日志的通道要复用 `LogSanitizer`**，别再直接 `write` 原始请求体；Agent 测试模式的 `logs/http.log` 是历史泄露点，已闭环。
2. **`LogSanitizer` 的边界**（`util/LogSanitizer.kt`）：覆盖 `键=值`、URL 编码、JSON 键值、裸 11 位手机号四形态；JSON 的数字值、以及任何 `1[3-9]` 开头 11 位数字都会被替换成 `***`（误伤是有意代价）。键表见文件里的 `EXTRA_SENSITIVE_KEYS`。
3. **`.bak` 不是垃圾**：`DownloadService.safeReplaceTemp(..., keepBackup = true)`（`service/DownloadService.kt:798-830`）会在切清晰度重下时把旧成品改名成 `.bak`；`util/FileUtil.cleanDownloadTempFiles`（`util/FileUtil.java:85-111`）必须**成品缺失才用 `.bak` 恢复、成品存在才删**。改清理逻辑时别再一句 `delete("*.bak")`。
4. **`reconnect_at_eof=1` 只放直播分支**：`activity/player/PlayerActivity.kt:1126-1132`，在 `isLiveMode` 内才设；点播设了会在播完后无限重连（见 `docs/archive/superpowers/specs/2026-10-11-player-network-resilience.md` P0-1）。
5. **`NetWorkUtil.post` 非 2xx 现在会抛 `IOException`**（`util/NetWorkUtil.java:443-447`），调用点必须按失败处理；`get` 仍不 `followRedirects`，3xx 原样返回，下载链路要自己判 `code()`（`activity/DownloadActivity.kt`）。
6. **DebugBridge 的 `http` 默认不带应用请求头**（`appheaders` 默认 false，`app/src/debug/.../debug/DebugBridge.kt:521` 附近）：要带登录态必须显式 `--ez appheaders true`，这是为了不再无脑把 Cookie 写进 `logs/`。
7. **`X-Install-Id` 受隐私同意闸控制**（`api/TerminalApi.java:535-547` 的 `headers()`）；反馈链路另走 `feedbackHeaders()`（`:763-778`）在未同意时补回，否则反馈无归属。改这两处前先读注释。
8. **CI 的 debug APK 路径已适配 flavor**：`.github/workflows/ci.yml:106-108` 是 `app/build/outputs/apk/*/debug/*.apk`；再写死 `apk/debug` 会每次 push 必红。
9. **minSdk 19 线上不要用 `String.strip()`**（API 33）：`util/PasswordEncryptUtil.java:18` 从 `strip()` 改回 `trim()`，否则 API 19–32 密码登录静默失败（`NoSuchMethodError` 是 `Error`，外层 `catch (Exception)` 兜不住）。同批把 6 处过时 minSdk 注释（写 21/24）统一到 `app/build.gradle:21` 的 19。

### 7.46 弹幕 XML 解析是**懒执行**的：交给 parser 的流不能在 `prepare` 返回后关掉（26.10.20）

`player/DanmakuManager.kt` 的 `createParser()` 只把 `AndroidFileSource(InputStream)` 存进 parser；**真正的 XML 解析发生在 `DanmakuView` 自己的渲染线程**——`DanmakuFlameMaster/src/main/java/master/flame/danmaku/controller/DrawTask.java:283` 的 `parser.setConfig(...).getDanmakus()` → `BiliDanmukuParser.parse()` → `xmlReader.parse(new InputSource(source.data()))`。所以：

- **不能**把 `FileInputStream` 交给 `createParser` 后在 `configureAndPrepare()` 返回时用 `use {}` / `close()` 关掉——懒解析读到的是已关闭流，`BiliDanmukuParser.parse()` 捕获 `IOException` 后 `return null`，**弹幕为空且没有任何提示**。26.10.02 的审计提交 `11ba14a` 正是这么改的，导致 26.10.19 上「所有本地缓存视频都没有弹幕、点『更新弹幕』重下也没用」（文件本身没问题）。修法：先把文件 `readBytes()` 读进内存，再包成 `ByteArrayInputStream`（`close()` 是空操作，晚读也安全）。
- `DanmakuManager.loadFromXmlInput(inputStream)` 不会关流，短播放器（`activity/video/ShortVideoPlayerActivity.kt` 的 `loadDanmakuFromXml`）一直按这个约定传 `ByteArrayInputStream`，所以只有普通播放器/本地缓存那条路中招。
- 顺带：**同一份文件的注释也可能自相矛盾**（`createParser` 的 doc 早写明了懒执行，`loadFromXmlFile` 的 use{} 注释却断言同步解析）。改这块前以 `DrawTask` 与 `BiliDanmukuParser` 的实体代码为准，别信注释。

### 7.47 选图一律 `ACTION_GET_CONTENT`：`ACTION_PICK` 在部分手表 ROM 上无人处理（26.10.20，崩溃 #63）

- **判据**：`Intent(ACTION_PICK, content://media/external/images/media)` 只由「声明了 `ACTION_PICK` 的系统相册」响应。XTC monaco_go Z10（SDK 30）这类手表 / 定制 ROM 的图片提供方只接 `ACTION_GET_CONTENT` / `ACTION_OPEN_DOCUMENT`，`startActivityForResult` **同步**抛 `ActivityNotFoundException: No Activity found to handle Intent { act=android.intent.action.PICK … }`（线上 #63：`activity/user/EditProfileActivity.kt` 的 `selectImage()`，当时是全仓唯一一处 `ACTION_PICK`）。
- **写法**：`Intent(Intent.ACTION_GET_CONTENT)` + `type = "image/*"` + `addCategory(Intent.CATEGORY_OPENABLE)`，外面 `try/catch (e: Exception)`，设备上一个选图应用都没有时提示 `R.string.msg_no_image_picker`。四个既有入口同写法：`PrivateMsgActivity.kt:203`、`WriteReplyActivity.kt:209`、`SendDynamicActivity.kt:168`、`SettingGroupActivity.kt:374`（后者用 `catch (Throwable)`）。选文件（字体 / 播客）同理，只换 MIME。
- **不要用 `intent.resolveActivity(packageManager)` 判活**：targetSdk ≥ 30 的包可见性限制下，不声明 `<queries>` 时它对真实存在的相册也返回 `null`，等于把崩溃换成「点了没反应」。
- **顺带的语法坑**：Kotlin 块注释**可以嵌套**，在 KDoc / 块注释里原样写 `image/*` 会被当成开了一个新注释，编译器报 `Unclosed comment` 指着文件末尾、真正出错的地方在注释里。写这类 MIME 串时放进行内 `//` 注释或干脆不写。

### 7.48 `TextView` 既要能点链接又要能长按：`OnTouchListener` 不许在 ACTION_DOWN 返回 true（26.10.20）

- **`util/StringUtil.java` 的 `ClickableSpanTouchListener`** 给所有经 `setLink` 处理的 `TextView`（评论正文、动态正文、简介…）接管 `ClickableSpan` 的点击。`OnTouchListener` 由 `View.dispatchTouchEvent` 在 `TextView.onTouchEvent` **之前**调用，**ACTION_DOWN 返回 true 等于抢走整段手势**：View 收不到按下事件就不 post 长按检测（`View.onTouchEvent` 的 DOWN 分支才 `checkForLongClick`），同一个 TextView 上的 `setOnLongClickListener`（长按复制 / 操作面板）**永远不触发**，而 UP 又是我们手动调的 `span.onClick`。线上观感：正文整段是网址（如一条评论只有 `https://www.bilibili.com/`）时**长按复制没反应、抬手反而打开链接**（26.10.20 用户报告，修法见 `docs/review/fix-progress.md` §3.39）。
- **正确分工**：DOWN 只记录（命中哪个 span + `getEventTime()`）并 `return false`；UP 时先判「按住时长 ≥ `ViewConfiguration.getLongPressTimeout()`」（`StringUtil.isLongPressGesture`，纯函数、有 JVM 单测）——是则长按已发生，`return false` 交给 View 收尾；否则补发 `span.onClick(widget)`、消费 UP，并**必须 `cancelLongPress()` + `setPressed(false)`**：UP 被消费后 View 收不到收尾事件，不会自己 remove 长按回调，不取消就变成「抬手之后才弹长按面板」。
- **别用自建 `postDelayed` 长按**（`adapter/video/VideoCardHolder.kt:122-144` 的 200ms 那套还需要配 `suppressClickAfterLongPress` 吞掉补发的 click）：原生长按回调 `return true` 时 View 自己不会补发 click，少一整类竞态；系统「长按延迟」、滑动取消也自动对齐。
- **同源结论**：任何 `setOnTouchListener` 里让 DOWN 返回 true 的地方，都要先问「这个 View 上还有没有人等着收长按 / 点击」；「DOWN 返回 false 后 View 会在 UP 补发 click」那条反过来也有坑，长期结论见 `docs/review/fix-progress.md` §5 的 `suppressClickAfterLongPress` 条目。

### 7.49 用文件路径往公共目录写图片，必须自己登记媒体库（26.10.21）

- **判据**：Android 10+（本应用 `targetSdk 34`）下，应用用裸 `File`/`FileOutputStream` 写进公共 `Pictures/` 的文件**不会被 MediaStore 自动索引**；而相册/图库、文件管理器的「图片/相册」分类**全都只查 MediaStore**。表现为「下载完成提示有了，文件也确实在 `/storage/emulated/0/Pictures/<应用目录>/`，但用户在相册和文件管理器里都找不到」（26.10.21 用户报告，`docs/review/fix-progress.md` §3.44 有复现与反证：写盘成功但 `content://media/external/images/media` 无该行；补一次 `MEDIA_SCANNER_SCAN_FILE` 立刻出现）。
- **写法**：写完调 `util/PictureMediaStore.scan(context, file)`（内部 `MediaScannerConnection.scanFile`，MIME 由纯函数 `mimeTypeOf` 判、认不出就不登记）。新写「保存图片到公共目录」的链路必须走它；更彻底的做法是 API 29+ 直接用 `MediaStore.Images` 的 `IS_PENDING` 插一条再写流（免「所有文件访问」、天然登记），目前没做。
- **提示文案要带目录**：以前图片下载成功只弹通用的「下载完成」（与 APK 更新共用 `text_update_download_complete`），既没说存到哪、也没说去相册看——现在走 `R.string.msg_picture_saved`，`%1$s` 用 `PictureMediaStore.displayFolder(file)` 裁成 `Pictures/哔哩终端`（手表窄屏下绝对路径显示不全）。
- **别用 `Environment.getExternalStorageDirectory()` 判「有没有权限」**：权限判据一律 `FileUtil.canWriteDirectory` 试写（§7.41 同源结论，issue #42 / `docs/review/fix-progress.md` §3.28）。

### 7.50 回车推进焦点：平台会自己抛 ISE，只能在 Activity 的 `dispatchKeyEvent` 兜（26.10.21，崩溃 #66）

- **崩溃是什么**：`TextView.onKeyUp` 收到 `KEYCODE_ENTER`、`event.hasNoModifiers()`、该 TextView **没有 OnClickListener** 时，只要 `(event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0` **或** `shouldAdvanceFocusOnEnter()` 为真（= **单行**，或文本类里 variation 为 `EMAIL_ADDRESS`/`EMAIL_SUBJECT`），就 `focusSearch(FOCUS_DOWN)`，随后 `v != null && !v.requestFocus(FOCUS_DOWN)` → **平台自己 throw** `IllegalStateException("focus search returned a view that wasn't able to take focus!")`（8.1 `TextView.java:7424-7477`，throw 在 `7453`；12 `8662-8716`，throw 在 `8690`；`shouldAdvanceFocusOnEnter()` 8.1 `7145-7171` / 12 `8387-8406` 逐字一致）。线上 #66 的栈正好停在 `TextView.onKeyUp`（版本 26.10.16 / SDK 27 / 设置页，明细见 `docs/review/fix-progress.md` §3.45）。
- **根因是平台前后判据不一致，不是某个输入框写错**：`focusSearch` 侧的候选过滤在触摸模式下只看 `focusableInTouchMode`（`View.addFocusables`，`FOCUSABLES_TOUCH_MODE`），而真正给焦点时 `requestFocusNoSearch()` 还会拒「触摸模式下不是 `focusableInTouchMode`」与「祖先 `descendantFocusability=blocksDescendants` / `shouldBlockFocusForTouchscreen()`」（8.1 `View.java:10850-10870`；12 `13517-13532` 另加 `canTakeFocus()`，连 DISABLED / 零尺寸也拒）。应用侧改不了平台判据 → **只能兜**。
- **兜底落点**：`util/view/EditorEnterKeyGuard.kt`（`FOCUS_SEARCH_FAILURE_MESSAGE` 原文常量 + 纯函数 `isFocusSearchFailure(Throwable?)` 沿 cause 链找「`IllegalStateException` 且 message 逐字等于原文」+ `swallowFocusSearchFailure(tag, e)` 记 `Logu.e` 后返回 true），接在 `activity/base/BaseActivity.kt` 与 `activity/player/PlayerActivity.kt` 的 `dispatchKeyEvent` 覆写里。**只在 `dispatchKeyEvent` 能 catch 到**：崩溃在 `super.dispatchKeyEvent` 往下调视图树时才发生，`onKeyDown`/`onKeyUp` 那时轮不到；`PlayerActivity` 要单独加是因为它**不继承 `BaseActivity`**（`class PlayerActivity : Activity()`）且带单行 `danmaku_send_edit`。新增带输入框的裸 `Activity` 页面同样要照抄这段。
- **只认消息原文，别放大成 `IllegalStateException` 一把抓**：同名 ISE 别处可能是真 bug，吞掉等于藏起来；平台这句话是字面量常量（不本地化），可以精确匹配，`EditorEnterKeyGuardTest` 钉着边界（大小写/空白/少末字符/同文案 `RuntimeException`/cause 深度上限）。
- **不要试图用 `adb shell input keyevent KEYCODE_ENTER` 复现**：物理 ENTER 的 DOWN 会先退出 touch mode（`ViewRootImpl.isNavigationKey` 含 ENTER，`checkForLeavingTouchModeAndConsume` 无 `FLAG_KEEP_TOUCH_MODE` 时 `ensureTouchMode(false)`；12 `7476`/`7497-7532`，8.1 `5816-5874`）→ 到 `onKeyUp` 时两处判据已一致，选中的 view 必能拿到焦点；`focusSearch` 返回 null 时也不抛。380×380 / Android 12 上按此路径试过四个页面全不崩。要人为制造错配只能自己写 `android:nextFocusDown` 指向 `blocksDescendants` 容器里的 view / 零尺寸 view，本仓库 `res/` 里 **0 处** `nextFocus*`/`descendantFocusability`/`touchscreenBlocksFocus`。
- **软键盘的「完成 / 下一项」也走同一条栈**：`BaseInputConnection.performEditorAction` 不直接调 `TextView.onEditorAction(int)`，而是合成一对带 `FLAG_SOFT_KEYBOARD|FLAG_KEEP_TOUCH_MODE|FLAG_EDITOR_ACTION` 的 ENTER（8.1 `569-582`、12 `665-678`）→ `InputMethodManager.dispatchKeyEventFromInputMethod` → `ViewRootImpl.dispatchKeyFromIme` → 与物理键同一条分发栈。所以崩溃栈里**分不出**是手按还是 IME 按的，上述兜底也一并管住它。
- **未覆盖（已知缺口，别当成已修）**：`TerminalDialog.input()` 造的 `AlertDialog` 是独立窗口，按键分发不经过 Activity（`Dialog` 虽是 `Window.Callback`，`DecorView` 先 `superDispatchKeyEvent` 再回调，换 callback 拦不到）；改为给输入框装 `setOnEditorActionListener` 会改变多行输入框的回车行为，属未验证变更。线上从未出现弹窗内回车崩溃（全仓 `TerminalDialog.input()` 仅 2 处调用）。另外 `TextView.onEditorAction(IME_ACTION_NEXT/PREVIOUS)` 里也有同一条 ISE（8.1 `5926`/`5936`、12 `7164`/`7174`），框架内仅无障碍 `accessibilityActionImeEnter` 直接调它，未在范围。

### 7.51 弹幕时间轴必须由播放器位置驱动，不能按墙钟（26.10.22，本周期的教训）

- **判据**：DFM 原生 `DrawHandler.syncTimer()` 的时间源是 `SystemClock.uptimeMillis() - mTimeBase`，即**墙钟**。这只有在「视频恰好按 1.0x 实时播放」时等价于媒体时间。只要播放速率不等于墙钟速率（弱机解码跟不上、2x 倍速、缓冲停顿），弹幕与画面的差距就**按百分比线性增长**：模拟器实测视频跑 0.9x 时，弹幕每 4 秒多跑约 490ms，每 ~12 秒累积到 1.5s 就触发一次吸附。
- **为什么曾经「看着能用」**：24–26.10 那几版用 `DanmakuSync.decide` 做「偏差 >1.5s 才 `seekTo`」的限频吸附，治的是症状。而 `seekTo` 走 `DrawTask.seek() → reset()`，本身就是**清屏 + 重建缓存**，所以限频之后用户看到的是「每隔十来秒弹幕闪一下又没了」。**周期性纠偏本身就是 bug，不要再加回来。**
- **现在的做法**：`DanmakuManager.bindMediaClock(position, isPlaying)` → `DanmakuView.setMediaClock()` → `DrawHandler.syncTimerByMediaClock()`，纯逻辑在 `DanmakuFlameMaster/.../controller/MediaClockSync.java`（`project` 按倍速外推 / `nextTimerValue` 每帧最多挪 40ms / `isDiscontinuity` 位置差 >1s 直接硬对齐）。位置为负数时自动回退原生墙钟分支（直播就不绑）。
- **两个不许改的点**：
  - `MediaClock` 的方法跑在 **DFM 渲染线程**，只能读主线程采样好的 `@Volatile` 内存值（`PlayerActivity.video_now` / `ShortVideoPlayerActivity.videoNow`）。**绝不能**在里调 `IjkMediaPlayer.currentPosition`——取 native 锁，与主线程 seek/release 并发有卡死风险，也是 26.09.10 那版「渲染线程读位置导致弹幕全没」的老坑（`DanmakuManager.updateTimer` 的长注释记着全过程）。
  - 用户拖动/快进后必须更新 `video_now`（走 `seekToVideo()` / `markSeekTarget()`），否则媒体时钟会继续按旧位置推进。
- **不需要额外放大单条弹幕的滚动速度**：`DanmakuFactory` 的 `scrollSpeedFactor` 以媒体时间为单位，倍速下时钟走 2x，弹幕自然也是 2x 划过并保持与画面同步。
- **体检日志**：`PlayerActivity.progressChange` 里只在**偏差超 500ms**（`DANMAKU_DRIFT_ALERT_MS`）时打一条 `弹幕时钟>视频=… 弹幕=… 偏差=…`，且间隔不小于 **5 秒**（`DANMAKU_DRIFT_LOG_INTERVAL_MS`）。健康值来自「250ms 采样 + 40ms/帧蠕动」，即 **±300ms 以内**且不随时间增长；持续超 500ms 说明媒体时钟没接上（走了墙钟回退分支）。**别改回「每 N 秒无条件打一条」或「一超标就立刻打」**：前者健康状态下白烧磁盘，后者在媒体时钟真坏掉时会变成每 250ms（一个采样周期）一条。
- **步进上限必须随倍速放大**（`MediaClockSync.stepFor`）：固定 40ms/帧在 60fps 下只能追 2400ms/s，3× 需要 3000ms/s（30fps 更差），永远追不上；同时误差过半秒（`SNAP_ERROR_MS`）直接吸附，别指望慢慢爬。
- **渲染循环必须显式 `start()`**：DFM 的 UPDATE 只由 START / SEEK_POS / RESUME 三个分支拉起，`prepare()` 只准备数据。`DanmakuManager.configureAndPrepare()` 末尾会自动 `start(已知播放位置)`；**新播放页要么走 `DanmakuManager`，要么自己调 `IDanmakuView.start()`**，否则「数据加载完了但一条不显示」。
- **DFM 是 fork 补丁，不是原版**：`DanmakuFlameMaster` 模块有 4 个文件被我们改过——`controller/MediaClockSync.java`（新增）、`controller/DrawHandler.java`（媒体时钟 + 采样重置）、`controller/IDanmakuView.java`、`ui/widget/DanmakuView.java`（暴露 `setMediaClock`）。将来同步 DFM 上游时这 4 个文件会被覆盖，**要按本节结论重新打补丁**。

### 7.52 播放页必须自己保持亮屏，且用窗口标志而不是 View 级 `keepScreenOn`（26.10.22）

- **现状**：长视频页靠 `res/layout/activity_player.xml` 根布局的 `android:keepScreenOn="true"`；短视频页 (`activity_short_video_player.xml` / `fragment_short_video_page.xml`) 原先**一处都没有**，看到一半就熄屏。**新增任何播放页都要记得这件**，它是布局属性、不会被编译器或单测提醒。
- **要用窗口标志**：`window.addFlags/clearFlags(FLAG_KEEP_SCREEN_ON)`。实测 View 级 `itemView.keepScreenOn` 在 RecyclerView 的 item 上清不干净（暂停后窗口 `fl` 仍带 `KEEP_SCREEN_ON`）。
- **判据要「重算」不要「增量」**：按「当前活跃播放器是否真的在播」整体重算（`ShortVideoPlayerActivity.syncKeepScreenOn()`），并在 `onStop` 兜底清、`onStart` 回前台重算。增量 add/clear 会漏掉「本来就没在播」的路径（`releasePlayer()`），漏一次就是永久亮屏。
- **别拿 `mWakefulness` 当验证判据**：MuMu 模拟器本身不自动熄屏（桌面对照 22 秒后仍 `Awake`），要看 `dumpsys window windows` 里窗口的 `fl` 是否含 `KEEP_SCREEN_ON`。

### 7.53 顶栏双击：必须自己接管触摸，用 `performClick()` 回放原动作（26.10.22，issue #22）

- **场景**：双击顶栏一键切到伪装页（假计算器 / 假「蓝牙已断开」，见 `docs/FEATURES.md` §19、`docs/superpowers/specs/2026-10-22-disguise-mode.md` 与 `docs/review/fix-progress.md` §3.59）。
- **为什么不能只加一个 `OnDoubleTapListener`**：顶栏单击的语义是「返回上一页 / 打开菜单」，长按是「打开菜单列表」。双击的**第一次抬手已经是一次完整 click** —— 若还让 View 自己发 click，页面先被 `finish()`，第二次点击根本落不到同一个 View 上。
- **正确做法**（`BaseActivity.setupTopbarDoubleTapDisguise`）：
  - `OnTouchListener` 返回 `true` 消费全部触摸（View 不再自己发 click/longclick），事件喂给 `GestureDetector`；
  - 单击 → `topBar.performClick()`、长按 → `topBar.performLongClick()`（返回 false 时回落 `performClick()`，与"长按无监听器时抬手变单击"的旧行为一致）、双击 → 打开伪装页。
  - **各页面自己的顶栏监听器一行都不用改**：`performClick()` 调的就是它们装的那个 `OnClickListener`，语义全保留（返回上一页 / 打开菜单 / 视频详情页关闭所有详情页）。
- **代价**：单击要等一个双击判定窗口（约 300ms）。所以这个手势**只在开关打开时安装**，关闭时把触摸监听摘掉即可回到原状（单击/长按监听器从来没被摘过），`onStart` 每次按当前开关重新判断。
- **三个接线点，少一个就有页面没手势**：`BaseActivity`（普通页）、`PlayerActivity.onStart`（它**不继承** `BaseActivity`）、短视频 `PageHolder.syncDisguiseGesture()`（顶栏在分页 item 里，且每次 `onBindViewHolder` 重算）。“关掉开关要 `detach`”三处都要做，否则已装上的触摸层不会自己消失。实现只有一份，在 `util/DisguiseGesture.kt`。
- **延迟装载必须封顶**：`asyncInflate` 页面在 `onStart` 时 `findViewById(R.id.top)` 还是 null，需要重试；但**有 11 个页面布局里根本没有 `R.id.top`**（Download / Catch / Tutorial / TutorialPager / JumpToDialog / InputDialog / CaptchaWebView / QRLoginRegister / ConfirmDialog / Dialog / ListDialog），无上限 `post` 会在这些页面每帧投递一次、耗电到页面结束。现在封顶 20 次 × 50ms，并在放弃时打 `XXX 没有 R.id.top，本页不安装双击伪装`。
- **`OnTouchListener` 里做长按计时，`ACTION_DOWN` 必须返回 `true`**（蓝牙伪装页的教训）：返回 `false` 时 View 没成为触摸目标，系统不再派发 `UP`/`CANCEL`，`removeCallbacks` 永远执行不到 → 轻点一下就会在 N 秒后自己触发退出。返回 `true` 时若要保留点击语义，得自己 `performClick()` 补发。
- **别改成「在"单击监听器"里判断两次点击」**：第一次点击已经执行完了，那只会变成"退出两次"。

### 7.54 DASH 下载完成后合并成单文件：容器 remux，不重新编码（26.10.25，用户要求）

- **为什么会有这一层**：1080P 及以上只有 DASH，`video.m4s` + `audio.m4s` 两条纯流；720P 及以下是 `fnval=1` 的音视频合一 MP4。用户要「和 720P 一样一个文件」。2026-08 曾经删掉过合并（`25893f3`/`183523f`，当时卡在「合并阶段无进度」「先删视频再改名会丢数据」），这两个问题现在分别有「进度分段常量」和「`safeReplaceTemp` 先备份再替换」现成解法，于是重做。
- **实现**：`util/MediaMerger.kt`（`object MediaMerger`，259 行）。两个 `MediaExtractor` 各取第一条 `video/*` / `audio/*` 轨，**按 `sampleTime` 归并**写进 `MediaMuxer(MUXER_OUTPUT_MPEG_4)`（旧实现是「每轮一视频一音频」交替写，不等时间戳，已改掉）。输出非分片标准 MP4，moov 在文件尾；`setOrientationHint` 透传旋转。返回码 `RESULT_OK / NO_TRACK / NO_SPACE / STOPPED / FAILED`，**非 OK 一律删产物、调用方保留双文件**。
- **为什么能直接读 B 站 m4s**：B 站 m4s 是分片 MP4（moov 里采样表为空、采样在 `moof/mdat`）。AOSP `MPEG4Extractor.cpp` 从 Android 4.4（API 19，正是本机 minSdk）起就含 `moof` / `firstMoofOffset` 解析，模拟器（Android 14 x86_64）实测两条轨采样数与源文件完全一致。`MediaMuxer` 写 AVC+AAC 自 API 18 起支持；HEVC 需 API 24+，但 `DashData` 选流优先 AVC，正常路径不碰；杜比 / FLAC 则根本写不进 `MediaMuxer`（选流已优先 `mp4a`）。
- **接在哪**：`DownloadService.processDownloadSection` 两个 DASH 分支（单 P / 多 P），在 `replaceDashPairOrFail` **之后**调 `mergeDashPair`：此时双文件已是本代正式成品，合并只是原地 remux；成功 `safeReplaceTemp(video_merging.tmp, video.mp4)` 后删 `audio.m4a`，失败只记日志、下载照常算成功。
- **进度**：附件 `0.00–0.12` → 视频 `0.12–0.52` → 音频 `0.52–0.88` → 合并 `0.88–1.00`；边界全在 `DownloadProgressMath`（`DASH_ATTACH_END` 等四个常量 + `dashMergeProgress(fraction)`），有单测钉死，别再写回字面量。
- **暂停语义**：合并开始前若已暂停就不合并（走 `runDownloadSection` 的暂停分支）；合并中**不响应暂停**（几秒内能完成，中途停手反而要处理半成品），只有 `started == false`（服务销毁）时中止并按未完成处理。`shouldStop = { !started }`。
- **残留清理**：合并临时文件固定叫 `video_merging.tmp`（`DownloadPathSpec.FILE_VIDEO_MERGING`），**刻意不用 `.mp4` 后缀**——`LocalListActivity.scanVideos` 有对历史残留 `video_merged_temp.mp4` 的「改名成 video.mp4」兼容逻辑，复用那个名字会把半成品认成成品。中断残留由 `FileUtil.cleanDownloadTempFiles` 清（已加该文件名）。
- **播放侧不用动**：合并成功后目录里只剩 `video.mp4`，`LocalListActivity` 天然走单文件路径；老的 DASH 双文件缓存仍然按「`audio.m4a` 当外部音轨」播放（`PlayerActivity.audioTrackUrl` / `LocalAudioTrackPolicy`，见 §7.4）。
- **验证接口**：Debug 包 `DebugBridge` 的 `merge` 命令可直接对两条 m4s 跑正式代码路径（用法与判据见 `docs/review/agent-test-guide.md` §4.8）。

### 7.55 评论区翻译：gRPC `TranslateReply` 的接入约束（26.10.26 新增）

- **当前状态：入口隐藏**：`ReplyTranslateApi.UI_ENABLED = false`，`ReplyAdapter.bindTranslation` 在开关关闭时把 `item_reply_translate` 与 `replyTranslation` 置 `GONE` 并清空点击事件。实现、单测、文档全部保留；外语评论→中文实测通过后改回 `true` 即恢复显示，不需要改 UI 代码。
- **API 类**：`api/ReplyTranslateApi.java`（新建，不走命名空间里的 REST 约定）。请求 `POST https://grpc.biliapi.net/bilibili.main.community.reply.v1.Reply/TranslateReply`，`content-type: application/grpc`，body 是「1 字节 `0x00` + 4 字节大端长度 + protobuf」，即 5 字节头。
- **目标语言靠头传**：`x-bili-locale-bin: base64(Locale{ c_locale=1 { language=1; script=2; region=3 } })`。本项目只写语言，且**固定为简体中文**（`ReplyTranslateApi.TARGET_LOCALE` → `zh`，不跟随系统语言），实测 en=`CgQKAmVu`、ja=`CgQKAmph`、zh=`CgQKAnpo`；**不发这个头服务端必然不返回译文**。
- **成功判据只看 `translated_content`**（`ReplyInfo` field 17 → `Content.message` field 1 非空）：空 map 不是错误（评论已经是目标语言 / 不可译都会这样），`reply_control.translation_switch` 在翻译成功时恒为 3，没有区分度；REST `/x/v2/reply` 返回的 `translation_switch` 恒为 1，**因此列表页无法预判某条评论能不能翻，只能点按钮时再调**。
- **失败判据**：正常响应 HTTP 200；业务错误是 `trailers-only`，`grpc-status`/`bili-status-code` 出现在响应头（例：`bili-status-code=12002` = 当前页面评论功能已关闭）。实现里先查响应头、再 best-effort 查 `Response.trailers()`（OkHttp 4.12 支持）。按产品决定**不重试、不换语言**，失败只提示一次（`ReplyAdapter` 用 `Logu.e` 留诊断，避免和 `MsgUtil.err(IOException)` 的默认网络错误提示叠成两个 toast）。
- **UI 约定**：按钮是操作行的 `item_reply_translate`（文A 图标，contentDescription=`text_reply_translate`），译文写在正文下方的 `replyTranslation`；译文只替换正文，不发新的评论请求。列表的 `realPosition` 与 adapter 下标差 1（`isDetail` 时差 2），点按钮时必须用 `ReplyAdapter` 里同一套映射反推，不要把 position 直接当 `replyList` 下标。
- **限制**：`grpc.biliapi.net` 只有 HTTP/2，API 19/20 没有 ALPN 会直接 `IOException`（OkHttp 不自动降级），按普通网络失败提示。楼中楼内嵌预览（`cell_reply_child` 的纯 TextView）不参与翻译，只有列表行 / 详情页行有按钮。
- **测试**：`app/src/test/java/com/RobinNotBad/BiliClient/api/ReplyTranslateApiTest.kt`（22 例：帧头、hex 黄金样本、packed rpids、int64 负数补位、locale base64（含「目标语言必须是 zh」的护栏）、空/坏数据容错）与 `model/ReplyTranslationTest.kt`（7 例：三种译文状态与 toggle）。

## 8. UI 基建速查（新增页面临摹用）

### 8.1 完整继承体系

```text
AppCompatActivity
  └─ BaseActivity
       ├─ InstanceActivity              ← 一级页（菜单键/顶栏 → MenuActivity）
       │    └─ RefreshMainActivity      ← 一级页 + 分页（布局 activity_simple_main_refresh）
       └─ RefreshListActivity           ← 二级页 + 分页 + 空视图（布局 activity_simple_refresh）

Fragment → BaseFragment → RefreshListFragment   ← Fragment 版列表页
```

`BaseActivity` 提供：主题应用（7 套主题，色表经 `ColorScheme.getCurrentTheme()` 缓存，仅由 `AppearanceManager.setTheme` 失效）、横竖屏、DPI/边距、**系统栏 insets 避让（`applySystemBarInsets`，含刘海）**、`getLayoutManager()`（横屏按「每列 ≥220dp」返回 `CustomGridManager`，下限 2 列）、`asyncInflate`（先显 loading 布局再替换）、`onBackPressed` 受 `back_disable` 开关、EventBus 自动注册/注销 + sticky `SnackEvent` 重放、主题变更 `onResume` 自动 `recreate()`、重写 `isDestroyed()`。

`InstanceActivity` 额外：`onCreate` 里 `BiliTerminal.setInstance(this)`；**顶栏点击不自动绑定**，须手动 `setMenuClick()`。

**顶栏曾是公共组件，但那次收敛被回滚了**：`res/layout/cell_topbar.xml` **文件本身已删除**（26.10.10 复核：`app/src/main/res/layout/` 139 个布局里含 `<include>` 的仍是 7 个——`activity_emote` / `activity_setting_about` / `activity_setting_ui_preview` / `cell_dynamic` / `cell_dynamic_child` / `cell_private_msg` / `cell_video_local`，19 处 `<include>` 中无一指向 `cell_topbar`，全库对它的引用为 0），实际是各页手抄顶栏。历史经过见 `docs/archive/review/fix-progress-history.md:227,252`——「44 个布局换成 `<include>`」那一轮在真机实测顶栏吃满整屏后**已整体还原**。所以本段旧描述（"44 个布局共用"）**与现状相反**，要重做收敛请先读 `docs/archive/visual-experience-report.md` 的方案再动手。

### 8.2 新列表页模板（必须做这 4 步）

`super.onCreate()` 之后：

```kotlin
setPageName("标题")                          // 1. 顶栏标题
setMenuClick()                               // 2. 仅 RefreshMainActivity 需要
setOnRefreshListener { load(1) }             // 3. 不注册则 swipeRefresh 永远转圈（基类 onCreate 里是 isEnabled=false, isRefreshing=true）
setOnLoadMoreListener { page -> load(page) } // 4. page 已由基类自增，别再 ++
```

加载完成**必须**调 `setRefreshing(false)`——它是 `isLoading`/`isRefreshing` 的唯一复位信号（只改 `swipeRefreshLayout.isRefreshing` 不算）。失败走 `loadFail(e)`（会 `page--` 并复位）。到底置 `bottom = true`。

**选型**：菜单入口页 → `RefreshMainActivity`；返回式页面且要空视图 → `RefreshListActivity`。

| | RefreshListActivity | RefreshMainActivity |
| --- | --- | --- |
| 父类/顶栏 | BaseActivity，返回式（自动绑） | InstanceActivity，菜单式（须 `setMenuClick`） |
| 防重入字段 | private `isLoading` | protected `isRefreshing` |
| 并发保护 | 仅 500ms 时间戳 | `synchronized` + 100ms |
| 触发阈值 | `findLastVisibleItemPosition >= itemCount-4`，IDLE 也查 | 完全可见项 `>= itemCount-3` 且 `!canScrollVertically(1)` |
| 空视图 | 有 `showEmptyView/hideEmptyView` | 有（`emptyView` / `showEmptyView` / `hideEmptyView` / `setOnEmptyRetry`，26.10.10 已补） |
| 性能 | `PerformanceManager` 动态缓存 + 独立 RecycledViewPool | 固定 cacheSize 10、pool max 20 |

### 8.3 Adapter 写法

**注意**：`ui/widget/recycler/` 下的 `AbstractAdapter` / `BaseAdapter` / `BaseHolder` 三件套**全工程没有任何子类**，已于 **26.09.11 删除**（连同 `WrapContentLinearLayoutManager`）。现役适配器一律 `extends RecyclerView.Adapter<XxxHolder>()` + Holder 直接继承 `RecyclerView.ViewHolder`。该目录现在只剩 `CustomLinearManager` / `CustomGridManager` 两个 LayoutManager。

**照抄对象**：`adapter/video/VideoCardAdapter.kt` + `adapter/video/VideoCardHolder.kt` 这一对（header/footer 需要时自己写，别找现成基类）。

### 8.4 现成交互工具

`MsgUtil`（任意线程可调）：

- `showMsg(str)` / `showMsgLong(str)` → 走 EventBus sticky `SnackEvent`；`toast/toastLong` 为降级 Toast。
- `err(Throwable)` 按异常类型自动分流文案（IOException→网络错误、JSONException→带详情、IndexOutOfBounds→Adapter 错误、SQLException→SQL）。
- `showText(title, content)` → `ShowTextActivity`；`showDialog(title, content[, wait])` → `DialogActivity`。

弹窗（`util/TerminalDialog.kt`，26.10.05 新增，**唯一入口**）：

四个静态方法，全部返回 `AlertDialog`，调用方自行 `.show()`：

| 方法 | 用途 | 对应旧的裸写法 |
| --- | --- | --- |
| `menu(context, title, items, danger, onPick)` | 菜单型，点一下执行一个动作 | `setItems`（原 7 处） |
| `singleChoice(context, title, items, checked, onPick)` | 单选型，`▸`/`›` 双态引导符 | `setSingleChoiceItems`（原 2 处） |
| `confirm(context, title, message, confirmText, cancelText, confirmIsDanger, onConfirm)` | 确认型，取消=次级灰 / 确认=危险红 | `setMessage`+`setPositiveButton`（原 8 处） |
| `alert(context, title, message, buttonText)` | 纯提示，单按钮 | `setMessage`+单按钮 |

行为约定（改之前先读这几条）：

- `menu` **先 dismiss 再回调**——回调里常会再弹一个框（「删除评论」→ 二次确认），不关会叠在一起。
- `singleChoice` **不自动关闭**——选季场景要先做越界校验再由调用方 `dialog?.dismiss()`；`checked` 越界会被夹到合法范围。
- `confirm` **保留按钮行**（菜单/单选刻意去掉了按钮行，手表纵向空间比一个「确定」值钱）；`cancelText` 传空串则不显示取消按钮。
- 破坏性项（删除/取消收藏）**必须**进 `danger`，别让用户靠文案猜。
- 颜色只读 `?attr/`，文件内无色值常量；`attrColor` 带兜底默认值。

对话框 Activity（Intent extra 传参 + `registerForActivityResult`）：

| Activity | 入参 | 返回 |
| --- | --- | --- |
| `DialogActivity` | title、content、wait_time | 无（只能点按钮） |
| `ConfirmDialogActivity` | title、content | RESULT_OK / RESULT_CANCELED |
| `ListDialogActivity` | title、`items`(StringArrayList) | `selected_position` |
| `InputDialogActivity` | title、initial_text、hint | `input_text`（已 trim，不校验空） |

### 8.4b 重复项合并后的公共落点（26.09.11 新增，别再手抄）

做「重复代码合并」时把 6 类重复收敛成了下列单一落点。**要写这几件事时直接用它们，不要重新手抄一份**。

| 落点 | 取代了 | 说明 |
| --- | --- | --- |
| `adapter/video/VideoQuickCache.handle(context, videoCard)` | 3 份逐字节相同的 `handleQuickCache` | 视频卡列表长按的「快速缓存」，按 `cache_default_quality` 分支 |
| `adapter/LogListAdapter<T>` + `res/layout/cell_log.xml` | 2 份流水 adapter + 2 份 MD5 相同的布局 | 经验 / 硬币变化记录；delta 文案由调用方以 `Row` 映射传入（两者文案不同，刻意未统一） |
| `util/NetWorkUtil.decompress(byte[])` | 3 份 `Inflater(true)` | CDN 裸 deflate 响应；`api/UserInfoApi.decompressResponse` 是 br+gzip，**算法不同不可合并** |
| `util/TimeUtil` | 11 处 `new SimpleDateFormat` | 时间格式化统一入口，ThreadLocal 缓存；`PATTERN_DATE_TIME_12H` 是历史遗留的 12 小时制，勿顺手改 `HH` |
| `BiliTerminal.jumpToUser(context, mid)` | 16 处手抄 Intent | 所有「跳用户主页」；等价于 `Intent().setClass(UserInfoActivity).putExtra("mid", mid)` |
| `ui/widget/RotaryEncoderSupport` | 3 份表冠滚动 + 1 处开关读取 | 三个 `Rotary*` 控件的公共逻辑；控件各自保留事件接入方式（监听器 vs `dispatchGenericMotionEvent`） |
| `util/ViewCapabilityProbe` | 2 处直接调 `View.hasOn*ClickListeners()` | 框架方法「本机可能有、也可能被裁掉」时的反射探测 + 降级，见 8.5 第 11 条 |
| `util/TerminalDialog` | 17 处内联 `AlertDialog.Builder`（7 菜单 + 2 单选 + 8 确认） | 26.10.05 收口；弹窗样式问题在**主题层**不在调用点，根因与迁移清单见 `docs/archive/review/dialog-redesign-progress.md`（技术结论已并入 §8.7.0） |

> `Rotary*` 三件套的差异是**刻意保留**的：`RecyclerView`/`ScrollView` 走
> `setOnGenericMotionListener` 且滚动后抢焦点，`NestedScrollView` 走 `dispatchGenericMotionEvent`
> 覆写且**不**抢焦点。helper 用 `requestFocus` 参数区分，改它等于改手表手感。

### 8.5 这一层额外的坑

1. `InstanceActivity` 不自动绑顶栏 → 子类忘调 `setMenuClick()` 则顶栏点击无反应。
2. `CenterThreadPool.observe(future, consumer)` **空 catch 静默吞异常**（`CenterThreadPool.java:116-122`）。
3. `RefreshMainActivity.kt:58` 把 layoutManager 强转 `LinearLayoutManager`——换 StaggeredGrid 会 CCE（横屏给的 `CustomGridManager` 是 GridLayoutManager 子类，安全）。
4. 主题/密度变化触发 `recreate()`，子类 `onResume` 的一次性逻辑会重跑。
5. 覆写 `eventBusEnabled()` 返回 false 会连 sticky Snackbar 一起失效。
6. `RefreshListFragment` 的 `setRefreshing` 只切 UI，不复位状态；也没有 `hideEmptyView`。
7. `setAdapter/setRefreshing/showEmptyView` 内部已切主线程，但直接 `recyclerView.adapter =` / `notifyItemRangeInserted` 必须自己回主线程。
8. **改 `Guideline.setGuidelinePercent` 不会自动重新布局**：`Guideline.onMeasure` 恒 `setMeasuredDimension(0,0)`，自身尺寸不随 percent 变化，父级若是 `wrap_content` 的 ConstraintLayout 就不会重算，必须手动 `requestLayout()`。登录页二维码缩放（`QRLoginFragment.kt`）踩过这个坑；另外宽度变化要带动高度得靠 `app:layout_constraintDimensionRatio`。
9. **`wrap_content` 的 RelativeLayout 里不能放 `layout_alignParentBottom` 的子元素**：只要有一个"贴底"子元素，RelativeLayout 的 `wrap_content` 就会被撑成父容器高度。公共顶栏 `cell_topbar.xml` 第一版就是这么写的（1dp 分割线贴底），结果**顶栏直接吃满整屏、列表被顶到屏幕外**（真机实测 `top` bounds = `[0,114][1080,2394]`）。现在顶栏根节点是竖向 LinearLayout，内层 RelativeLayout 只放标题/时钟（`BaseActivity.setRound()` 需要 RelativeLayout.LayoutParams）。
10. **`<include>` 建议显式写 `android:layout_width/layout_height`**：不写时行为依赖被包含布局根节点的参数，排查困难。另外注意：`cell_topbar.xml` **文件本身已删除**（那次 44 处替换被整体回滚、模板也已还原并移除，见 8.1 节；26.10.10 复核），未来若重做收敛再照本条办。
11. **别裸调「理论上一定存在」的框架方法——部分手表框架会把它裁掉**（26.09.13 真机崩溃）。`android.view.View.hasOnLongClickListeners()` 是 API 15 就有的公开 API，`android-34` 的 class 文件里也确实有（`javap` 验证），但某手表的 `/system/framework/framework.jar` 里没有，`BaseActivity.setupTopbarLongPressToHome()` 一调就抛
    `NoSuchMethodError: No virtual method hasOnLongClickListeners()Z in class Landroid/view/View;` —— **`onStart` 里崩，页面全打不开**，且编译期、Lint、单测全都查不出来。
    统一走 `util/ViewCapabilityProbe.probeBoolean(view, "方法名") { 名, 异常 -> 日志 }`：能反射调通就走框架，调不通返回 `null` 让调用方走降级分支（自己是标志位即可），探测结论进程级缓存、只失败一次。返回 `null` 是**降级信号**不是错误，调用方必须处理。

---

### 8.6 新增一个设置项的完整链路

设置项 key 有**两套定义处**，新增时统一加在 `util/SettingsKeys.kt`（`SharedPreferencesUtil` 里那 36 个常量是历史遗留，不要重复声明）：

1. `util/SettingsKeys.kt` 加 `const val XXX = "xxx"`。
2. 对应设置页加条目：`activity/settings/SettingGroupActivity.kt`（字符串驱动，按 `desc_*` 惯例加资源；这是**唯一**该动 `strings.xml` 的地方）。
3. `activity/settings/SettingsIndex.kt` 的 `build()` 加一条 `Entry(name, desc) { ... }`——否则全局设置搜索找不到这一项。

**放哪个分组**：界面尺寸类进 `buildUIGroup()`（`group_type = "ui"`）；外观类（配色/圆角/字体）进
`buildAppearanceGroup()`（`GROUP_APPEARANCE = "appearance"`，26.09.11 新增的独立一屏，
由 `SettingGroupActivity` 的 `group_type` 分发，非独立 Activity）。若新设置需要自己的页面，
看 `docs/agents/dev-conventions.md`「新增设置子页面有两种形态」——只是列表项就别新建 Activity。

读写统一走 `SharedPreferencesUtil.getXxx(key, default)` / `putXxx(key, value)`。
**外观类设置例外**：写入必须走 `AppearanceManager.setXxx()`，它负责递增外观版本号（见 §8.7）。

---

### 8.7 外观三模块：配色 / 卡片圆角 / 字体（26.09.11 起）

**位置**：`ui/appearance/`。三个模块**完全独立**（各自一个 key、互不干涉），
由一个门面统一收口读写与「外观已变更」通知。

| 文件 | 角色 | key | 档位 |
| --- | --- | --- | --- |
| `AppearanceManager.kt` | 门面：快照 + 版本号 + **唯一写入入口** | `appearance_version` | — |
| `ColorScheme.kt` | 配色：7 套主题（**只读模块**，原 `ui/theme/ThemeManager.kt`） | `theme_selector` | 7 套 |
| `CornerStyle.kt` | 卡片圆角 | `ui_corner_radius` | `square`（默认）/ `rounded` |
| `FontStyle.kt` | 自定义字体（用户从文件管理器选字体文件） | `ui_font_path` | 有 / 无（默认无） |
| `ColorMode.kt` | 暗色 / 亮色模式：应用自己决定 `uiMode` 夜间位（暗色 `NIGHT_YES`、亮色 `NIGHT_NO`），由 `BiliTerminal.getFitDisplayContext`（Activity base context 与全局 context）和 `AppCompatDelegate.setDefaultNightMode` 两处落实；亮色资源只在 `values-notnight` / `drawable-notnight`。`PlayerActivity` / `SplashActivity` 用 `forceDark` 上下文。`ColorScheme` 亮色表 = 暗色表 + `LightPalette` 覆盖，与 `colors_light.xml` 同源（`tools/appearance/gen_light_mode.py`） | `ui_color_mode` | `dark`（默认）/ `light` |
| `AmbientGlow.kt` | 弥散光晕：7 套主题各一份 `Spec`（顶部底色 + 1～2 团径向光晕），`ColorScheme.applyWindowTheme` 开启时把它设为**窗口背景**、content 背景置空；关闭时走原纯色路径，零开销。无 blur / 噪点层，draw 内零分配。`Spec` 只按顶部写，`placeGlow` / `baseGradient` 按光源位置变换（中间位置底色改径向） | `ui_ambient_glow`、`ui_ambient_glow_source` | 开 / 关（默认关）；`top` / `bottom` / `left` / `right` / `center`（默认 `top`） |
| `GlassStyle.kt` | 玻璃质感：7 份 `GlassOverlay.<X>`（`values/themes_glass.xml` + `colors_glass.xml`）只替换 `materialCardViewStyle` / `materialButtonStyle` 两个**具体样式引用**，`BaseActivity.onCreate` 在 `super.onCreate` 后 `theme.applyStyle(force=true)`；关闭零开销。卡片 / 按钮 elevation 必须为 0（半透明底下阴影会透出）。布局里内联写死底色的选择卡片 / 次要按钮改读 `?attr/choiceCardColor` / `?attr/secondaryButtonTint`（`values/attrs_glass.xml`，主主题默认值 = colorSurface）；视频 / 文章详情的点赞投币收藏卡片读 `?attr/actionCardColor` / `?attr/actionCardStroke`（主主题 = surface + brand 描边，玻璃 = `glass_btn_bg_fill_<x>` / `glass_btn_stroke_<x>`）——**新增会 inflate 布局的主题必须定义这四个属性，否则 inflate 崩**。覆盖层够不着的写死 shape / 代码设色背景（播放器分P / 字幕 / 互动选项、楼中楼预览框、「UP主觉得很赞」）走 `GlassStyle.choiceBackground` / `panelBackground`，色值同源 `colors_glass.xml` | `ui_glass_components` | 开 / 关（默认关） |
| `ThemeTint.kt` | 给 XML 里只能写死颜色的 drawable（API 19 不能用 `?attr/`）在设置图片时按 `ColorScheme` 着色：点赞/投币/收藏激活态、私信发送按钮、投票选中框等 | — | — |
| `RoundListEffect.kt` | 圆屏列表效果（实验性，26.10.31）：「圆屏模式 + 圆屏列表」都开时，给页面里全部竖向滚动容器（`RecyclerView` / `ListView` / `ScrollView` 家族，含 ViewPager2 页内竖向列表）的可见条目按纵向位置做等比缩放（圆弦长比，下限 0.50）与边缘淡出（t³ 曲线，系数 0.85）。`BaseActivity.onCreate` 末尾 `install`；全树容器扫描挂在 `OnGlobalLayoutListener`、变换挂在 `OnScrollChangedListener`，滚动回调零分配，横向容器 / 接近整屏高的内容（≥窗口高）不参与。**不走 AppearanceManager 版本机制**（开关在 `onCreate` 读取，改后重进页面生效；关闭时一个监听都不挂） | `ui_round_list_effect`（并要求圆屏模式 `player_ui_round`） | 开 / 关（默认关） |
| `AppearanceApplier.kt` | 把外观（自定义字体等）落到视图树上：`applyToContentView`，由 `BaseActivity.kt:388` 调用 | — | — |

**分层约定（别打破）**

- **模块**（`CornerStyle`/`FontStyle`）只放：候选值常量、显示名、纯函数（规整、档位→数值）、读取。
  **不放写入**——写入一律走门面，这样「递增版本号」不会漏。
- **门面**只做「汇总快照 + 唯一写入 + 版本号」，**绝不做几何计算**（手表性能优先）。
- **半透明底的组件一律 `elevation=0`**：底色不透明时阴影只是外面的暗晕，底色半透明时阴影会从底**下面**透出来——暗色底下看不出来，亮色纸底上就是沿轮廓一圈「比底色亮的脏边」。玻璃主题（`ButtonStyleTerminal.Glass` 等）从一开始就这么写；主主题的 `ButtonStyleTerminal` 26.10.30 也补上了（见 `docs/review/fix-progress.md` §3.82）。

**版本号机制**：`AppearanceManager.version()` 是一个存在 SharedPreferences 的 Int，
任何外观写入都 +1。Activity 只需记住自己创建时的版本号、`onResume` 比一次，
就知道要不要重建——**不会随模块增加而增加比较项**（新增第 4 个模块不需要改 `BaseActivity`）。
> 现状（26.10.10 复核）：**已接入**——`BaseActivity.kt:104` 在 `onCreate` 记录 `appliedAppearanceVersion = AppearanceManager.version()`（字段声明 `:75`），
> `onResume`（`:351`）发现版本号变化即 `recreate()`。新增外观模块不用改 `BaseActivity`。

**两条不可破坏的性能约定**

1. **未启用自定义字体 = 渲染路径零开销**：`FontStyle.typeface()` 返回 null 时
   `CustomFont.applyToContentView` 立即 return，一次视图树遍历都不做。这是绝大多数用户的状态。
   守卫测试：`FontStyleTest.shouldLoad_isFalseWhenNoFontConfigured`。
2. 圆角**只决定「用哪个主题属性取值」**，不做运行时几何计算，也不在 `RecyclerView` 绑定路径上做额外工作。

### 8.7.0 弹窗的主题属性接入（26.10.05 新增，改弹窗/加主题前必读）

**弹窗样式由主题属性决定，不由调用点决定。** 全工程 17 处裸 `AlertDialog.Builder` 已收口到
`util/TerminalDialog.kt`（见 §8.4），但真正让弹窗「灰底白字 + 粉按钮 + 方角」的是**主题层缺三个属性**。

**三个此前全工程 0 声明的属性**（grep `app/src/main/res` 全域）：

| 属性 | 谁在用 | 缺了会怎样 |
| --- | --- | --- |
| `alertDialogTheme` | AlertDialog 选哪个颜色 overlay | 走 `Theme.MaterialComponents` 自带的 alert overlay，**不读本主题的定制** |
| `colorOnSurface` | Material 正文/标题文字色 | 回退 Material 默认近白 → 「白字」。**`android:colorForeground` 救不了它**（Material 正文不读 colorForeground） |
| `colorError` | 破坏性按钮色 | 删除类操作没有危险语义 |

**「粉按钮」的确切来源**：`setPositiveButton`/`setNegativeButton` 的文字色走 `colorAccent`，
这是 7 族主题里**唯一都设了**的属性，B站粉与经典终端恰好都是 `#FF6699`。
即：弹窗底色跟随 `colorSurface`（半接入），文字与按钮走 Material 默认 overlay（未接入）。

**修复形态**：`themes.xml` 里 7 份 `ThemeOverlay.<X>.Dialog`（parent 一律
`ThemeOverlay.MaterialComponents.Dialog.Alert`），各含**三项**：
`colorSurface` / `colorOnSurface` / `colorError`（**不再含 `android:background`**，见下面的 26.10.25 修订）；
每族主题加一行 `<item name="alertDialogTheme">@style/ThemeOverlay.<X>.Dialog</item>`。
`dialog_background.xml` = `solid ?attr/colorSurface` + `stroke 1dp ?attr/colorOnSurface@32%` + `corners @dimen/card_round`，
**只由 `layout_dialog_terminal.xml` 的根视图**使用（`TerminalDialog.Sheet.init` 里 `content.background = ...`）。

> **26.10.25 修订：面板不再走主题 `android:background`（改弹窗/加主题前必读）**
>
> **现象**：OPPO Watch X2 Mini（ColorOS Watch，Android 11 / OWW242）每次启动的「隐私说明」弹窗
> 只剩一个深色方块 + 1dp 描边，**里面一个字都没有**，且因按钮不可见卡在闪屏。
>
> **根因**：主题的 `android:background` 会被框架/ROM **贴到弹窗 window 里每一个内部视图上**
> （MuMu 上实测 `DecorView` 底下 appcompat subdecor 各层 —— `action_bar_root` / `@id/content` /
> `alertDialogLayout` / `customPanel` / `custom` —— 全是同一份 `dialog_background`）。
> ColorOS Watch 的弹窗 `DecorView` 末尾还多挂一个 `SwipeGlowOverlay`，它也被贴上同一份**不透明**面板，
> 而它排在内容之后 = 画在**最上层**，于是我们 `@id/custom` 里的内容被整块盖住。
> MuMu/Wear OS 没有这个 overlay，所以普通设备一直正常（这也是本 bug「只有部分设备」的原因）。
>
> **修法**（`util/TerminalDialog.kt` + `themes.xml` + `dialog_background.xml`）：
>
> 1. 7 份 overlay 去掉 `android:background`（只留三个颜色属性）——面板不再交给主题；
> 2. `Sheet.init` 里给 content 根视图 `content.background = AppCompatResources.getDrawable(...)`，
>    面板由我们自己的内容树画**一次**；
> 3. `Sheet.stripForeignBackgrounds()`：在 `onShow` 第一帧后遍历 `DecorView`，把**不属于 content 子树**
>    的视图背景一律清成 `null`（兜住 ROM 在 show 之后才挂的装饰层，比如 `SwipeGlowOverlay`）。
>
> **回归验证**：Wear OS 5 圆屏 466×466@320（`uiMode=0x16`）与 MuMu Android 12 1440×2560@640
> 上弹窗外观与修前一致（面板/圆角/描边/正文/按钮/提示齐全）；诊断包视图树里 `android:background=null`、
> 各内部层 `bg=null`、content 根 `bg=GradientDrawable`。证据与完整视图树见 `docs/review/fix-progress.md` §3.67。

> **`Sheet.init` 里 `dialog.window?.setBackgroundDrawable(ColorDrawable(TRANSPARENT))` 不能删**：
> 不做的话 AppCompat 会在面板底下垫一层方角底衬，圆角白做——这正是「像安卓原生的一样」的直接原因。

> **`Sheet` 的固定底栏（`bottomButtonBar`，26.10.09 加，改 `TerminalDialog` 前必读）**：
> `Sheet.addButton` 原来把按钮加进 `terminal_dialog_list`（**在 ScrollView 内部**），而当时的
> `capScrollHeight()` 把滚动区高度**钉死在屏高 45%**、滚动条又是 `scrollbars="none"`；长正文会把按钮顶到看不见，
> 配上必要的 `setCancelable(false)` 就是**用户彻底卡在闪屏上**。修法：`Sheet` 加构造参数
> `bottomButtonBar: Boolean = false`（默认 false = 原行为，既有 12 处 `confirm/alert/menu` 零影响），
> 只有 `choice()` 用 `true`，按钮改挂 `layout_dialog_terminal.xml` 的固定底栏 `@id/terminal_dialog_buttons`。
> **为什么必须新增 `choice()` 而不是复用 `confirm()`**：`confirm()` 的取消按钮写死 `dismiss()`（无回调），
> 且内部是**先 `dismiss()` 再 `onConfirm()`**，`setOnDismissListener` 触发时还拿不到「用户点的其实是确定」，
> 两条路都会走一遍。

> **两个必踩的坑（26.10.20 缓存页长按菜单踩到，改 `TerminalDialog` / 新写弹窗时先看）**：

> 1. **`menu/confirm/alert/choice` 返回 `AlertDialog`，但不会自己 `show()`**。调用方写成
>    `TerminalDialog.menu(context, ...) { which -> ... }` 就是「点了完全没反应」——不崩、logcat 也没异常，
>    只能靠加日志才发现回调已执行、弹窗已建好却没显示。正确写法是链上 `.show()`，参考
>    `adapter/reply/ReplyAdapter.kt`（`TerminalDialog.menu(...) { ... }.show()`）。
> 2. **窗口宽度：`show()` 之前写不进去**。appcompat 的 `AlertDialog.onCreate()` 里 `installContent()` 会把
>    自定义视图**重新** `addView(child, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))`，于是
>    `show()` 之前写进 `content.layoutParams` 的宽度、以及 `window.setLayout()`，统统被覆盖回整屏宽
>    ——26.10.20 实测 `dumpsys window` 里该窗口 `mAttrs` 始终是 `(wrapxwrap)`、`mFrame=[0,0][屏宽,屏高]`。
>    正确时机是 `dialog.setOnShowListener`（窗口 attach 完、第一帧 layout 之前），`Sheet` 在那里再调一次
>    `prepareLayout()` 落到 `content.layoutParams` 上（`init` 里那次保留，作「不 show 也正确」的兜底）。
>    宽度与「点外面」的空白由 `util/view/DialogSizing.kt` 算：`width = 可用宽 × 90%`，可用宽 =
>    屏幕 − 「界面边距」百分比（`SettingsKeys.PADDING_H/PADDING_V`，与宿主页面同一套安全区），
>    380×380 上 = 342px、两侧各 19px。**旧结论「已把窗口宽度限到约 90%、表盘两侧各约 24px」没有核实过**：
>    那版代码在真机/模拟器上从未生效（`dumpsys` 里仍是整屏）。
>    另：`menu` 的提示行本身也接 `dismiss()`，别指望用户去找屏边那二十来像素。
> 3. **小屏高度预算（`DialogSizing.plan`）**：不再「滚动区钉死屏高 45%」——那等于让
>    `45% × 屏高 + 标题 + 分割线 + 底栏 + 提示行` 自己去凑屏高（320×320 需 353px、300×300 需 344px，
>    底部提示行被压到 6px 高，「正文较长，可上下滑动阅读」整行看不见）。现在反过来算：
>    `预算 = 可用高 × 94%`（上下留出能「点外面取消」的空白、圆屏不切角），再实测「标题 / 分割线 /
>    底栏 / 提示行」的真实高度（都随「界面大小」缩放，不能写死 dp），`滚动区 = 预算 − 其它部分`；
>    放不下时**先舍提示行、再舍标题（连分割线）**，滚动区兜底「一行」的最小高度。
>    `naturalScrollHeight()` 让正文短时按内容收着，不为凑预算把弹窗撑高。
>    **按钮行还有一层兜底**：1.8 倍界面下两个三字按钮合计 468px > 窗口 342px，右对齐的「同意」会被
>    整个挤出屏幕（`choice()` 是 `setCancelable(false)`，等于用户卡死在闪屏）——`Sheet.fitButtonBars()`
>    逐级缩字号（下限 9sp，同时松内边距与 44dp 最小宽度），仍放不下就改成均分宽度。
>    实测 380×380@1.8、320×320@1.5 两个按钮都在屏内。

> **教训（判断「某属性全工程缺失」的正确姿势）**：必须**同时 grep 相邻属性做对照组**。
> 本次最初的错误结论是「弹窗层从未接入主题系统」——被「7 族主题都声明了 `colorSurface`」
> 与「XML 侧 71 处 `?attr/colorSurface`/`colorPrimary` 引用」直接推翻。真相是**缺其中三个**，
> 不是整层缺失。只 grep 目标属性会把「半接入」误读成「未接入」，方案方向跟着全偏。

**已知遗留**：`styles.xml` 的 `CardStyle`/`ButtonStyle` 等组件样式内**全是硬编码静态色**
（`@color/card_dark` / `@color/pink` 等），切主题后不跟随；与 `docs/archive/visual-experience-report.md`
§2.12「86 处布局引用静态调色板」同源。本次未动。

### 8.7.1 圆角的生效机制（26.09.11 落地，改圆角前必读）

**结论：圆角靠 XML 里的具体 dimen + 运行时套用，不走主题属性。**

> **走过的弯路（别再走一遍）**：最初用主题属性做运行时切换——`<item name="cardCornerRadius">?attr/appCornerRadius</item>`
> 配合 `theme.applyStyle(覆盖样式, force=true)`。**真机实测半径变成 0**：卡片角完全没被裁，
> 连原来 `@dimen/card_round` 的 6/10dp 都丢了。维度属性的 `?attr/` 间接层在这里没有解析成功。
> 该方案已整体回退（`appCornerRadius` 属性、两个覆盖样式、8 处主题兜底全部删除）。

**实际机制**

1. `CardStyle*`/`ButtonStyle*`（`styles.xml` + `themes.xml` 共 7 套）的 `cardCornerRadius`/`cornerRadius`
   一律写**具体 dimen** `@dimen/card_round`——即默认「方角」档的值。真源是 `dimens.xml`
   - `values-w300dp/dimens.xml`（手表 6dp / 宽屏 10dp）。
2. 只有选「圆角」档时才需要运行时覆盖：`CornerStyle.needsRuntimeOverride()` 为 true 时，
   `AppearanceApplier` 在视图树里把 `card_round_large`（手表 12dp / 宽屏 16dp）套上去。
   **默认档不触发任何遍历**，这就是「零开销」的落点（守卫测试 `needsRuntimeOverride_isFalseForDefaultOnly`）。
3. 覆盖对象**必须包含普通 `androidx.cardview.widget.CardView`**，不能只认 `MaterialCardView`：
   实测 `dumpsys activity top` 里两者同时存在（设置索引页的卡片就是普通 `CardView`），
   而普通 CardView 不读 `materialCardViewStyle`，XML 的 `cardCornerRadius` 对它无效，
   只有 `setRadius()` 能改。漏掉它就会出现「有的卡片跟着档位变、有的不变」。
4. 覆盖必须挂在 `ViewGroup.OnHierarchyChangeListener` 上，不能只在 `onContentChanged` 走一遍：
   `SettingMainActivity` 的卡片是在 `asyncInflate` 回调里**程序化 `addView`** 加进容器的，
   发生在遍历之后。该监听器同时覆盖 `RecyclerView` 的 item 挂载（也走 `addView`）。

**已知未覆盖**

- **10 个 shape drawable 仍直接用 `@dimen/card_round`**，不跟随档位
  （`background_card`、`background_card_borderless`、`background_edittext`×3、
  `background_grey_cardview`、`background_privatemsg_send`、`background_searchbar`、
  `background_searchhistory`）。`shape` 的 `<corners android:radius>` 读不到主题属性，
  也拿不到 Typeface 那样的运行时覆盖入口；要修得改控件（`ShapeableImageView` 等）或另做一次 drawable 遍历。
- **不走 `BaseActivity` 的三个界面**（开屏/外链/播放器）不生效。
- layout 级内联圆角（头像 28dp、投票按钮 18dp、三个 8dp cell、`item_account` 12dp）
  属「尺寸派生圆角」，按设计豁免。

**「方角」的语义（已对上游实测核实）**：上游 BiliClient（gitee `develop`，HEAD `f2b1aca`）
全项目唯一圆角是 `@dimen/card_round` = **6dp**，经主题 `materialCardViewStyle` 全局下发；
**上游没有 0dp 直角外观，也没有任何圆角设置项**。所以 `square` 档的值是 `card_round`
（手表 6dp / 宽屏 `values-w300dp` 10dp），语义是「还原原项目」，不是「做成直角」。
本项目偏离上游之处是给 6 套主题硬编码了 12dp，那是 `rounded` 档。

**默认档位的观感影响（修正早先「零变化」的说法）**
默认 `square`，对**默认主题「经典终端」是零变化**（它本来就走 `@dimen/card_round`）；
但**另外 6 套主题的卡片圆角会从 12dp 变为 6dp**——那 12dp 是主题化改造时复制 `CardStyle`
引入的漂移（5 套主题各抄了一份 12dp），不是刻意的设计取值，本次借模块化收敛回原项目取值。

### 8.7.2 自定义字体的生效机制（26.09.11 落地）

用户在设置 →「界面与外观」→「外观设置」→「自定义字体」里，用文件管理器挑一个
TTF/OTF/TTC，应用把它**拷进私有目录**（`filesDir/custom_font/custom_font.ttf`）并全局应用。

**为什么是「拷贝」而不是记住 URI**

1. `minSdk 19`，`Typeface.Builder(FileDescriptor)` 要 API 26 用不了；安全可用的只有
   `Typeface.createFromFile(File)`，它需要一个**真实路径**。
2. 用户随时可能删除/移动源文件；记 URI 还得处理 `takePersistableUriPermission`。拷一份最稳。

**为什么不用 `LayoutInflater.Factory2`（更漂亮的做法）**
`LayoutInflater.setFactory2()` 只在**从未设过** factory 时可用，否则抛 `IllegalStateException`。
而 `BaseActivity : AppCompatActivity`，AppCompat 已在 `super.onCreate()` 里装好 factory
（还带着 Material 的控件替换，`MaterialButton`/`MaterialTextView` 靠它，**圆角模块也依赖它**）。
用自己的 factory 顶掉它会让 `<Button>` 退回普通 Button、圆角失效——比字体问题严重得多。
公开 API 没有干净办法把两个 factory 串起来，所以走遍历。

**实际机制**

- `BaseActivity.onContentChanged()`（`setContentView` 之后必被触发，同时覆盖普通布局与
  `asyncInflate` 的替换布局）→ `CustomFont.applyToContentView(this)`。
- 遍历静态视图树，只对 `TextView` 调 `setTypeface`，用 `!==` 跳过已套好的。
- 列表项由 `RecyclerView` 复用、绑定发生在遍历之后，故对遍历中遇到的每个 `RecyclerView`
  挂 `OnChildAttachStateChangeListener`，只处理**新挂上来的** item 视图。
- **保留粗体/斜体**：自定义字体按 NORMAL 解析，直接 `setTypeface` 会抹掉 `android:textStyle="bold"`，
  所以按原样式派生（`Typeface.create(base, style)`，按样式缓存）。

**性能代价（手表优先，必须说清楚）**：未启用时零开销；启用后每次 `onContentChanged` 跑一次遍历。
这套代价是**用户主动开启**换来的，不是所有人付。

**已知边界**

- **不走 `BaseActivity` 的界面不生效**：`SplashActivity`、`GetIntentActivity`、`PlayerActivity`
  （三者都不继承 `BaseActivity`）。开屏无正文、外链页极简，播放器以视频为主。
- **晚建的视图**：`OnHierarchyChangeListener` 只能覆盖「父容器已被遍历过」之后加进来的子视图；
  如果整个容器本身也是遍历之后才挂上去的，那一支仍会漏。
- 只改字体外观，**不改字号**。原计划的「字号 4 档 + 字族 2 选」（token 收敛 + `scaleFactor`）
  已按需求变更取消：`ui_font_scale` / `ui_font_family` 两个 key 随之删除。

**自救入口（别删）**：「外观设置」页的「恢复系统字体」按钮是**常驻**的，不藏在「已安装」条件后面。
自定义字体最危险的失败模式是**字体缺中文字形 → 界面全变方框、文字读不了**，那时用户只能靠
这一条固定位置的按钮回到系统字体。把它改成条件显示等于把唯一退路藏起来。
（若连设置页都进不去，最后的退路是系统设置里清除应用数据。）

### 8.7.3 视觉上「已确认不要回退」的实现（26.10.10 由 `docs/archive/visual-experience-report.md` 附录 E 回迁）

这些地方**当前是正确的**，只是可能显得「旧」或「不统一」。别在顺手重构/统一化的过程中改坏它们：

- `util/AnimationUtils.java:43-45` 的 `crossFade`（100ms）与各详情页的 `crossFade` 用法 —— 加载态到内容态的过渡，删了就是「内容突然砸出来」。
- 搜索框滚动隐藏逻辑（`SearchActivity.onScrolled`，26.10.26 按用户要求逐字回退到加排序功能之前）—— 收起就是 `ObjectAnimator.ofFloat(searchBar, "translationY", 0f, -height)` 配 200ms 后置 `GONE`、展开反向，`animate_last` 节流 200ms、`dy < -1` 才展开。26.10.24 那套「动真实高度、结束才 GONE」的 `applyTopChrome` 已随排序入口挪进搜索框内部而整体删除，`ViewPager` 重新锚在 `@id/top` 下方（搜索框盖在列表上方的旧口径）。别再为了「让列表补位」改回高度动画。
- 二维码三档缩放（`QRLoginFragment.kt:124-160`）—— 注意 `Guideline` 改 percent 后**必须 `requestLayout()`**（见 §8.5 第 8 条），这套缩放是踩坑后的产物。
- 圆屏适配（`BaseActivity.kt:143-181`）与 `RotaryRecyclerView` / `RotaryScrollView` 的滚轮输入支持 —— 三件套的差异是刻意保留的（见 §8.4b 末注）。
- `ThemeManager` 集中配色的**设计意图**：7 套主题 41 个字段的抽象是对的，问题只是「另有两套并行表」（见 §8.7）。
- 视频卡片的 `MaterialCardView` + 主题 ripple 组合 —— 这是「有反馈」的那一半，是标准；另一半应向它对齐，**不是反过来**。
- 评论区「UP」身份标签恒定红底 `#F44336`（`ReplyAdapter.UP_BADGE_COLOR`）—— 产品要求的刻意硬编码，不跟主题。
- 弹幕恒定白字 + 黑阴影（`DanmakuManager.kt:157,168`）—— 不受主题控制是**刻意的可读性选择**，不要「顺手主题化」。

**明确「不做」的：裸 `Activity` 收编**（`PlayerActivity` / `ImageViewerActivity` / `SplashActivity` / `GetIntentActivity`）。`PlayerActivity.kt:102` 是 `class PlayerActivity : Activity(), IMediaPlayer.OnPreparedListener`，**不继承 `BaseActivity`**，是全应用唯一这样的核心页。理由：播放器保持黑色系统栏是**有意为之**（继承 `BaseActivity` 会把顶栏与主题窗背景带进来），且该文件 3000+ 行，收益不抵回归风险。26.09.10 视觉报告已评估过一次并记为不做，别当漏改来「顺手统一」。其余视觉技术债（顶栏 45 份手抄、无障碍缺口、播放器热区、自适应图标）见 `docs/review/fix-progress.md` §2.4。

---

## 9. API 层映射表（48 个文件，改功能时定位用）

按功能域分组。`api/` 下 42 个 Java + 6 个 Kotlin（`ApiErrorText.kt`、`HotSearchApi.kt`、`ServerTimeApi.kt`、`ShortVideoFeedApi.kt`、`StoryFeedQuery.kt`、`StorySeenStore.kt`）（26.10.27 实测；`BilibiliIDConverter` 已删除，见下）。`ApiErrorText`（共用错误文案）与 `StorySeenStore`（短视频跨会话去重记忆）不是接口类，未单独列行。

### 视频与播放

| 类 | 职责 | 关键方法 |
| --- | --- | --- |
| `VideoInfoApi` | 视频详情/tag/在看/AI 总结 | `getVideoInfo(String)/(long)`、`getTags`、`getInfoByJson`、`getWatching`、`getVideoConclusion` |
| `PlayerApi` | 播放地址/字幕/下载/跳外部播放器 | `getVideoDash`、`getVideo`、`getBangumi`、`getSubtitleLinks`、`jumpToPlayer` |
| `DanmakuApi` | 弹幕收发 | `sendVideoDanmakuByAid/ByBvid`、`getVideoDanmakuSegment`、`getAllVideoDanmaku` |
| `InteractionVideoApi` | 互动视频分支 | `getEdgeInfo` |
| `RecommendApi` | 推荐/热门/入站必刷/相关 | `getRecommend`、`getPopular`、`getPrecious`、`getRelated` |
| `RankingApi` | 排行榜 | `getRanking` |
| `HotSearchApi.kt` | 热搜 | `getHotSearch`、`parseHotSearch` |
| `SearchApi` | 搜索/建议/默认内容 | `search`、`searchType`、`getBangumiFromSearchResult`、`getSearchSuggestions`、`getDefaultSearchContent` |
| `HistoryApi` | 历史记录 | `getHistory`、`reportHistory`、`deleteHistory` |
| `WatchLaterApi` | 稍后再看 | `getWatchLaterList`、`add`、`delete` |
| `ShortVideoFeedApi.kt` | 短视频 Feed 取流（`StoryFeedPager` 持会话级分页状态） | `fetchVideoUrl`、`StoryFeedPager.fetchPage` |
| `StoryFeedQuery.kt` | 短视频取流纯逻辑：参数拼装 / 降级判定 / 响应解析与去重（有 JVM 单测） | `buildParams`、`buildUrl`、`parseFeed`、`isDegradedTrackId`、`isClearlyHorizontal` |

### 动态、专栏与番剧

| 类 | 职责 | 关键方法 |
| --- | --- | --- |
| `DynamicApi` | 动态列表/详情/发布/转发/点赞/@ | `getDynamicList`、`getDynamic`、`publishComplex`、`relayDynamic`、`analyzeDynamic` |
| `OpusApi` | 图文动态与专栏正文（HTML 抓取） | `getOpus`、`likeOpus`、`analyzeCommentInfo`、`analyzeParagraphs` |
| `ArticleApi` | 专栏 cv | `getArticle`、`like`、`addCoin`、`opusId2cvid` |
| `VoteApi` | 投票 | `createVote`、`doVote`、`getVoteInfo`、`parseVoteInfo` |
| `SeriesApi` | 合集/系列 | `getUserSeries`、`getSeriesInfo`、`getSeriesByJson` |
| `BangumiApi` | 番剧/追番 | `getFollowingList`、`getBangumi`、`getSections`、`getMdidFromEpid`、`getSeasonDetail`（页内切季）、`getEpisodeFromEpid`（历史卡片续播）、`getFollowStatus`/`setFollow`（追番按钮） |
| `TimelineApi` | 番剧时间表 | `getTimeline` |
| `TopicApi` | 话题广场 / 话题下动态列表（26.10.04 批次 6 的 C10） | `getRecommendedTopics`、`searchTopics`、`parseTopics`、`getTopicDynamicList` |
| `NoteApi` | 视频笔记（只读，26.10.04 批次 6 的 C27） | `getNoteIdsOfVideo`、`getNoteInfo`、`parseBlocks`、`parseNoteDetail` |

### 用户与社交

| 类 | 职责 | 关键方法 |
| --- | --- | --- |
| `UserInfoApi` | 用户/空间/关系/资料 | `getUserInfo`、`getUserSpaceInfo`、`followUser`、`updateUserInfo`、`uploadAvatar` |
| `FollowApi` | 关注/粉丝/分组 | `getFollowingList`、`getFollowerList`、`getFollowTags` |
| `ReplyApi` | 评论 | `getReplies`、`getRepliesLazy`、`getRootReply`、`getReplyCount`、`sendReply`、`sendDynamicReply`、`likeReply`、`dislikeReply`、`deleteReply`、`topReply`、`uploadReplyImage`（`actionErrorMsg` 统一错误码文案；`topActionFor` 纯函数） |
| `ReplyTranslateApi` | 评论翻译（gRPC `TranslateReply`，26.10.26 新增） | `translateReplies`、`translateReply`、`encodeTranslateRequest`、`wrapGrpcFrame`、`parseTranslateResponse`、`buildLocaleHeader`（protobuf 手写编解码，全部纯函数可 JVM 单测） |
| `PrivateMsgApi` | 私信 | `getPrivateMsg`、`getSessionsList`、`sendMsg` |
| `MessageApi` | 消息中心/未读/消息设置 | `getUnread`、`checkMessageUnread`、`getLikeMsg/getReplyMsg/getAtMsg`、`getSystemMsg` |
| `EmoteApi` | 表情包 | `getEmotes`、`getEmoteTexts`、`getMyPackages`、`setPackage`、`analyzeEmotePackages` |
| `ImageApi` | 发动态/评论配图（选图压缩 → 上传图床） | `prepareImage`、`uploadImage`、`sniffImageType`（底层复用 `ReplyApi.uploadReplyImage`） |
| `VipApi` | 大会员 | `getVipInfo`、`addExperience` |
| `CreativeCenterApi` | 创作中心 | `getVideoStat`、`getBeUPTime` |
| `ExpLogApi` / `CoinLogApi` / `ElectricApi` / `LoginRecordApi` | 经验/硬币/充电/登录记录流水 | 各自一个 `getXxx()` |

### 收藏与直播

| 类 | 职责 | 关键方法 |
| --- | --- | --- |
| `FavoriteApi` | 收藏夹 | `getFavoriteFolders`、`getFolderVideosNew`、`addFavorite`、`parseFavoriteState` |
| `LikeCoinFavApi` | 视频三连/点赞/投币 | `triple`、`like`、`coin`、`favorite`、`getVideoStats` |
| `LiveApi` | 直播 | `getRecommend`、`getFollowed`、`getRoomInfo`、`getRoomPlayInfo`、`analyzeLiveRooms` |

### 登录、鉴权与基建

| 类 | 职责 | 关键方法 |
| --- | --- | --- |
| `LoginApi` | 登录（扫码/TV/密码/短信/SSO） | `getLoginQR`、`getLoginState`、`passwordLogin`、`smsLogin` |
| `CookiesApi` | Cookie/buvid/ticket | `checkCookies`、`genWebHeaders`、`getWebBuvids`、`genBiliTicket` |
| `CookieRefreshApi` | Web Cookie 刷新 | `cookieInfo`、`getCorrespondPath`、`refreshCookie` |
| `AppTokenRefreshApi` | access_token 续期 | `refreshAppToken` |
| `ConfInfoApi` | **WBI 签名**（全 api 层依赖） | `signWBI`、`getWBIMixinKey`、`sortUrlParams` |
| `AppInfoApi` | 上游服务器的崩溃上报 / 赞助 / 检查更新（公告接口已于 26.10.23 移除） | `check`、`uploadStack`、`getSponsors` |
| `TerminalApi` | **自建**服务器：公告（含上游逐条审核透传）/反馈/匿名统计/崩溃上报（见 §6.5） | `ping`、`submitFeedback`、`uploadCrash`、`checkAnnouncement`、`getOwnAnnouncementList` |
| `ServerTimeApi.kt` | 证书时间校验失败后**明文** http 取 B 站服务器时间，只用于「设备时间错误」提示（26.10.27） | `fetchServerMillis`、`parseNowMillis` |
| `BilibiliIDConverter` | av/bv 互转（纯函数） | ~~`bvtoaid`、`aidtobv`~~ **26.09.11 已删除**（全工程 0 调用） |

### 网络出口的 5 个例外

`api/` 里只有 5 处**绕过** `NetWorkUtil` 的 `get`/`post` 封装自己拼 `Request`，因此不受它的解压/重试/Cookie 管理保护：

1. `ReplyApi.java:190-208` `uploadReplyImage`（multipart，不处理 br/gzip）
2. `UserInfoApi.java:291-303` `updateUserInfo`（自带 `decompressResponse`）
3. `UserInfoApi.java:330-348` `uploadAvatar`
4. `ReplyTranslateApi.java` `translateReplies`（gRPC 二进制 body，手动拼帧与头；复用 `NetWorkUtil.getOkHttpInstance()` 的共享 client，故 CookieSave/HttpTrace 两个拦截器仍生效）
5. `ServerTimeApi.kt` `fetchServerMillis`（明文 http，**独立 client、不带 Cookie、不挂任何拦截器**——绝不能改走主客户端）

前三者均手工拼 Cookie 头；第 4、5 项是游客可用的公开接口，不带 Cookie。改这几个方法时不要假设 NetWorkUtil 的行为。

### 纯函数可测点

已抽成 static/object、只依赖 `org.json`、可直接 JVM 单测：
`HotSearchApi.parseHotSearch`、`FavoriteApi.parseFavoriteState`/`buildMediaId`、`OpusApi.analyzeCommentInfo`/`analyzeParagraphs`、`VoteApi.parseVoteInfo`、`EmoteApi.analyzeEmotePackages`、`LiveApi.analyzeLiveRooms`、`BangumiApi.analyzeSection`/`analyzeEpisode`、`VideoInfoApi.analyzeTags`/`analyzeUgcSeason`/`getInfoByJson`、`ReplyApi.analyzeReplyArray`、`SeriesApi.getSeriesByJson`、`DynamicApi.parseAtContent`、`ConfInfoApi.getWBIMixinKey`/`sortUrlParams`、`CookiesApi.hmacSha256`。（原列表里的 `BilibiliIDConverter.bvtoaid`/`aidtobv` 已于 26.09.11 随死代码删除。）

**不可单测**（内部发网络 / 依赖 Context / 弹 UI）：`DynamicApi.analyzeDynamic`、`MessageApi` 全部解析（SpannableString）、`PrivateMsgApi.getPrivateMsgList`、`LikeCoinFavApi.getVideoStats`。

**测试覆盖现状（26.10.30 实测）**：`app/src/test/` 104 个测试类 / 1108 个用例（两个 flavor 各 1108、0 失败）；api 层有 11 个类的解析函数被覆盖（`HotSearchApiTest`、`FavoriteApiTest`、`FollowApiTest`、`OpusApiTest`、`PrivateMsgApiTest`、`BangumiApiTest`、`DynamicApiTest`、`TopicApiTest`、`NoteApiTest`、`WatchLaterApiTest`、`ReplyTranslateApiTest`，另有 `PlayerApiPbpTest`、`ReplyApiTest`、`ReplySortTest`、`ReplyTranslationTest`、`ReportApiTest` 覆盖部分纯逻辑）；`ui/appearance/` 下有 4 个测试类——`ColorSchemeTest` 覆盖 7 套主题的 `key → style` / `key → 色表` / 中文显示名映射、无 key 时的默认值、以及色表缓存的失效与读取次数；`CornerStyleTest` 覆盖圆角两档与「档位 → 覆盖样式」映射；`FontStyleTest` 覆盖字体文件头校验（含 WOFF 专门拒绝）与「未配置不加载」的性能约定；`AppearanceManagerTest` 覆盖外观版本号与唯一写入入口；`PerformanceManagerTest` 覆盖档位换算与图片/分页参数（见 §7.10）；`ApkVerifierTest` 覆盖更新包校验的判定规则（见 §7.11）；`SettingsKeysTest` 把 `player`/`play_qn`/`bangumi_update_notify_enable` 三个磁盘键名钉死（见 §7.12）；`PrivateMsgApiTest` 另覆盖会话列表解析（含 `top_ts`）、`opTypeForTop` 的 0/1 映射（见 §7.13）与图片消息 content 的字段/单位换算（见 §7.14）；`MsgNotifierTest` 覆盖新消息通知的提醒判据与正文拼装（见 §7.15）以及追番更新的文案；`BangumiApiTest` 覆盖追番列表解析（见 §7.16）；`BangumiUpdateCheckerTest` 覆盖追番快照序列化与更新判定（见 §7.16）。

### API 层的坑

1. **解析里发网络**：`DynamicApi.java:844` 在 `analyzeDynamic` 内调 `BangumiApi.getMdidFromEpid`；`PrivateMsgApi.java:59,75` 在解析里调 `UserInfoApi` → 无法纯测 + N+1 请求。
2. **解析依赖 UI/Context**：`DynamicApi.java:1085`（`BiliTerminal.context`）、`MessageApi.java:128/145/232/320`（SpannableString）、`LikeCoinFavApi.java:71`（`MsgUtil.err` 报错——原先的弹窗已不存在，26.10.10 复核）、`AppInfoApi.java:33-85`（网络 + SharedPreferences + 弹窗混在一起）。
3. **api 类做 UI 跳转/下载**：`PlayerApi.java:48-56`（startActivity，`:55`/`:69`）、`58-100`（DownloadService）、`426-520`（拼 Intent）。
4. **重复实现**：视频卡片解析 **21 处**（`RankingApi`、`RecommendApi`×4、`WatchLaterApi`、`SearchApi`×3、`SeriesApi`、`FavoriteApi`×2、`UserInfoApi`、`HistoryApi`、`BangumiApi`、`MessageApi`×3、`DynamicApi`×2、`VideoInfo.java`。**本节此前写的「7 份」是过时数据**，26.08 快照写的「19 处」也偏低——26.10.04 实测 21 处（`SearchApi.java:177` 的番剧搜索分支与 `DynamicApi.java:826` 是漏计项）；`ReplyApi.sendReply` 两份；`DanmakuApi` 发送两份；`VideoInfoApi.getVideoInfo` 两份；解压逻辑 `NetWorkUtil` 已有一份、`UserInfoApi.java:354` 又写一份。**改一处记得 grep 其余几处。**
5. **参数写错**：`PlayerApi.java:310` `.put("fnvar",0)`（应为 `fnver`）；`DanmakuApi.java:93` `segment_index` 从 1 开始（`:80` 的 javadoc 却说从 0，调用点 `:133`/`:144`）。**（`ReplyApi.likeReply` 硬编码 `type=1` 已于 26.09 修复：`ReplyApi.java:341`/`:356` 改为显式 `REPLY_TYPE_VIDEO` 的兼容重载，真实类型由 `ReplyAdapter.kt:389/414` 传入。）**
6. **硬编码**：弹幕 XML 地址重复 3 处（`PlayerApi.java:112,250,328`）；URL 散落在方法体内，无常量表。**（`AppInfoApi` 的 4 处明文 `http://` 已于 26.09.13 全部改为 `https://`，见 `AppInfoApi.java:143,162,184,207` 与 §6.4。）**
7. **全局可变状态**：`SearchApi.java:34-41` 的 `SEID_BY_KEYWORD`（26.10.10 复核：旧的两个 `static seid/search_keyword` 字段**已删除**，现按关键词隔离并用 LRU 限制 16 条）、`ConfInfoApi.java:42-54` 的 WBI 缓存、`LoginApi.java:31-32` 的 `oauthKey`/`tvAuthCode`。
8. **SharedPreferences key 混用（csrf 部分已于 26.10.04 批次 2 收敛，`player`/`play_qn` 于批次 4 收敛）**：字面量 `"csrf"`（`HistoryApi:38,73`、`WatchLaterApi:118,129`、`DanmakuApi:33`）与常量 `SharedPreferencesUtil.csrf`（`EmoteApi:52`）曾并存，现已统一改调 `NetWorkUtil.currentCsrf()`（见 §6.1）；`"player"`/`"play_qn"` 的 13 处字面量已全部改调 `SettingsKeys.PLAYER`/`PLAY_QN`，第三处定义 `SharedPreferencesUtil.player` 死字段已删（见 §7.12）。其余 key（`mid`、`player_show_viewpoints` 等）仍有字面量，尚未收敛。

---

### 7.56 收藏夹管理页：内容管理入口 + 资源类型 + 清空失效（26.10.29）

- **入口换了语义**：长按收藏夹打开的 `activity/user/favorite/FavoriteFolderEditActivity.kt` 从「编辑收藏夹」升级成「收藏夹管理」（顶栏与 manifest `label` 共用 `text_activityfavoritefolderedit_06`，值已改成「收藏夹管理」）。`adapter/favorite/FavoriteFolderAdapter.kt` 的长按**不再拦默认收藏夹**（原来 `!folder.isDefault` 直接 toast 拒绝）——因为内容管理只在这个页面有入口，而默认收藏夹的内容同样要能管理。默认夹进来只显示 `hintDefault` 提示，隐藏 `groupInfo`（改名/简介/隐私/保存）与 `groupDanger`（删除夹），也**不再弹**「默认收藏夹不能编辑或删除」的 toast（改成常驻文案，少一次打扰）。
- **页面结构（`res/layout/activity_favorite_folder_edit.xml` 整文件重写）**：照 `activity_edit_user_info.xml` 的单卡片编辑页模板——顶栏 `pageName`+`timeText` 不变；`RotaryScrollView` 内依次 `hintDefault` → `groupInfo`（12sp `?android:attr/textColorSecondary` 小标题 + `editTitle`/`editIntro` 裸 `@drawable/background_edittext` + `radioPrivacy` + `btnSave` 卡片行）→ 「内容管理」小标题 → `btnBatchDelete` / `btnCleanInvalid` 两张单行卡片（`xxxTitle` + `xxxDesc` 走 `MarqueeTextView` + `xxxArrow`）→ 「危险操作」小标题 + `btnDelete` 卡片行（`@color/status_error`）。**卡片不写 `cardBackgroundColor`**：7 套主题的 `materialCardViewStyle` 已经统一了底色/圆角/描边，布局里再写死颜色就会在亮色主题下翻车。
- **新增行没有图标**：`drawable/icon_*.xml` 的 stroke/fill 全部硬编码 `#ebe0e2`，亮色主题下对比度极低，且 `cell_setting_nav` 不自带 tint——所以内容管理两行只用「标题 + 描述 + 箭头」，不做「图标 + 标题」。
- **「批量删除内容」= 跳到夹内内容页并直接进多选**：`openContentManager()` 用 `startActivityForResult(..., REQUEST_CONTENT = 3)` 打开 `FavoriteVideoListActivity`，extras 传 `mid`/`name`/`mediaId`/`readOnly=false`/**`selectMode=true`**。内容页新增 `pendingSelectMode`：`loadFirstPage()` 成功回调里若 `writable && videoList.isNotEmpty()` 才 `enterSelectionMode()`，随即清标记——空夹/只读夹不硬进多选，切排序重拉不会重复进，**首屏失败时不标记**（用户下拉重试仍能进多选）。返回时管理页 `setResult(RESULT_OK)`，让收藏夹列表重拉数量。
- **资源的类型位必须按真实类型拼（这是一个真 bug 的修复）**：`resources` 的 `{id}:{type}` 里 `2`=视频稿件（avid）/`12`=音频（auid）/`21`=视频合集（合集 id），原来 `buildResources()` 一律写 `:2`，对音频/合集条目就是错的 id 语义。`model/VideoCard.java` 末尾追加 `public int favType = 2;`（Parcelable **只能追加在末尾**，见 §7.20 的顺序警告），`getFolderVideosNew()` 用 `normalizeFavType(video.optInt("type"))` 填它，`buildResources()` 按卡片类型拼串，`deleteFavorite(aid, favType, mediaId)` 新增三参重载（两参旧重载委托它，`FolderChooseAdapter` 调用点不用改）。**没有引入 `x/v3/fav/resource/batch-del`**：项目既定策略是写操作全部收口 `batch-deal`（见 §7.24），两者能力重叠。
- **「清空失效内容」只认 media_id**：`api/FavoriteApi.cleanInvalidResources(mediaId)` → `POST x/v3/fav/resource/clean`，form 只有 `media_id` + `csrf`，`mediaId <= 0` 直接返回 `-400`；成功 `data` 是 `0` 而非条数，所以**提示只说「清理完成」，不谎报清了几条**，也不做「可清理数量」预检（接口快照里没有 `folder/clean-state`）。UI 侧 `confirmCleanInvalid()` 走 `TerminalDialog.confirm(...)`（确认文案强调不可恢复），成功提示后同样 `setResult(RESULT_OK)`。
- **文档同步**：能力表在 `docs/FEATURES.md` §8.1/§8.2/§8.3/§8.5，接口结论（类型位、clean 的 `data=0`、batch-del 仍不接）在 `docs/bilibili-api-notes.md` §6。

---

### 7.57 播放器内切换 1080P：播放链路必须按清晰度选接口（26.10.30）

- **根因不是 UI，是取流**：播放器内切清晰度原来一律 `PlayerApi.getVideo()`（`fnval=1` 的音视频合一 MP4），这条流**清晰度天花板就是 720P**，`qn=80` 会被服务端降级成 `quality=64`；但响应的 `accept_quality` 里**照样列出 1080P/1080P+**，所以界面能看到 1080P、点了却没有效果（`docs/bilibili-api-notes.md` §3.12 记了实测）。1080P 及以上只有 DASH（`fnval=4048`）拿得到，而 DASH 是「纯视频 + 纯音频」两条流，播放侧必须自己起第二路音频。
- **判据抽成纯函数**：`player/PlayerStreamPolicy.kt` —— `QN_720P = 64`、`requiresDash(qn) = qn > 64`、`isPlayableDash(videoUrl, audioUrl)`（缺任一条就不能当 DASH 播，回退合一 MP4）、`isRemoteAudioTrack(url)`（`http(s)://` 前缀；本地缓存双文件仍是文件路径）。`PlayerStreamPolicyTest` 6 例钉死。
- **取流入口**：`PlayerApi.getPlaybackVideo` / `getPlaybackBangumi` 是播放侧唯一入口——`qn>64` 先试 `getVideoDash` / 新增的 `getBangumiDash`，`isPlayableDash` 不成立就清掉 `dashData/audioUrl` 并回退 `getVideo` / `getBangumi`。`getVideoDash`/`getVideo`/`getBangumi` 的 `accept_*` 解析统一走 `fillQualityLists`，dash 落字段统一走 `applyDashStreams`。**番剧也有 `getBangumiDash`**（`pgc/player/web/playurl` + `fnval=4048&fourk=1`，`result` 下取 `dash`），否则番剧永远拿不到 1080P。
- **播放器侧**：`audioTrackUrl` 现在承载两种东西——本地双文件路径与在线 DASH 音轨 URL；`initAudioTrack()` 先按 URL 形态分流，远程走新增 `initRemoteAudioTrack()`（`android.media.MediaPlayer` + `setDataSource(Context, Uri, headers{Referer/User-Agent/Cookie})` + `prepareAsync()`）。**必须用带 `Context`/`Uri` 的重载**：`MediaPlayer` 没有 `setDataSource(String, Map)`，只用 `setDataSource(String)` 拿不到带 Referer 的 CDN 流（403）。
- **失败兜底只回退一次**：音轨 `onError`/初始化异常 → 先 `retryRemoteAudioTrack()`（600ms 后同 URL 重试，`MAX_DASH_AUDIO_RETRY = 1`，`dashAudioRetryCount` 每次切清晰度/重新载入归零）→ 仍失败才 `onHighQualityAudioFailed()` → `applyQuality(64, allowDash = false)` 切回 720P 合一 MP4；`dashAudioFallbackInFlight` 防重入（否则反复重建播放器）。`applyQuality` 是 `switchQuality` 拆出来的唯一落地路径（`switchQuality` 只负责 `requiresDash` 判据），切P（`switchToOnlinePage`）与互动跳转（`jumpToInteractionPage`）也都改走 `getPlayback*` 并把 `playerData.audioUrl` 带进 `doSwitchPage(..., newAudioTrackUrl)` / `audioTrackUrl`。
- **`-38` 的真正根因：对 preparing 中的音轨 `seekTo`**（26.10.30 模拟器实测钉死）。切清晰度时远程音轨刚 `prepareAsync`，`currentPosition` 固定是 0，而 `video_now` 已经续播到十几秒；`progressChange` 的偏差校正（>800ms 就 seek）因此对**还没 prepared** 的 `MediaPlayer` 调 `seekTo` → 框架以 `INVALID_OPERATION`(-38) 直接回调 `onError` → 刚切好的 1080P 音轨被判死、回退 720P，用户看到的就是「一切 1080P 就被踢回 720P」。修法是加 `audioTrackPrepared` 守卫（远程 `onPrepared` 里置真、`releaseAudioTrack` 置假），校正只在它就绪后生效。
- **只纠「还在变大」的漂移，不纠固定偏差**：两路时钟基准天然差一截（模拟器上稳定 ~850ms，视频渲染慢），拿绝对偏差当判据会每 2~5 秒 seek 一次音轨（听感持续一顿一顿）。`progressChange` 因此先采样一次偏差当基线（`audioDriftBaseline`/`audioDriftSampled`），之后只有偏离基线超过 800ms 才对齐；用户拖动（`seekToVideo`）后基线作废重采样。
- **回退时不要覆盖清晰度列表**：回退成合一 MP4 后服务端 `accept_quality` 只剩 720P/360P，直接覆盖会让用户再也点不到 1080P（连重试入口都没了）。`applyQuality` 只在「新列表最高档不低于旧列表最高档」时才替换 `qnStrList/qnValueList`。
- **倍速同步到音轨（26.10.30 初版，26.10.31 证伪）**：DASH 双流下两路各走各的，当时新增 `applyAudioSpeed()` 用 `PlaybackParams`（API 23+）把倍速同时下发到外部音轨，接在 `applySpeedFromSeekbar` / 倍速滑条 / 长按三倍速 / `endLongPressBoost` / 两处音轨 `onPrepared`。**模拟器实测证明这条路走不通**：`IjkMediaPlayer.setSpeed` 在「没有音轨的纯视频流」上不生效——视频位置 10.6s 墙钟只走 10.6s（恒 1.0000x），而同一次 `MediaPlayer.setPlaybackParams(2.0)` 是生效的 → 音频一路领先，`progressChange` 的 800ms 基线校正每 ~1.3 秒把音轨 seek 回来一次（69.4s 内 47 次、每次回拉 700~1000ms），听感就是「两路声音重叠、有一路跟不上」。根因：ijk 的变速靠音轨时钟 + `soundtouch` 驱动，**纯视频流没有可缩放的音频时钟，倍速必然不生效**（这也是 `setDisplay()` 里 `soundtouch=1` 一直开着的原因）。这是 **stock ijkplayer 的行为**，26.10.31 已用内核补丁修掉，见下一条。
- **26.10.31 最终修法：给 ijkplayer 打内核补丁，让纯视频流也真倍速**。问题在 ijk 内核而不是应用层（见上一条），所以直接改源码：`ijkpatch/0001-ffplay-video-playback-rate.patch` 在 `compute_target_delay()` 里只在「无音轨 + 倍速 != 1」分支把帧间隔除以 `pf_playback_rate`（PTS/时钟不动，进度条、弹幕、seek 都不用改）；`ijkpatch/0002-ffmpeg-hevc-termios-b0.patch` 修 arm 上 NDK `<termios.h>` 的 `B0` 与 HEVC `TAB_MVF_PU(B0)` 撞名导致 ffmpeg 编不过。两个补丁在 `bilibili/ijkplayer 30eb944` + `Bilibili/FFmpeg ff4.0--ijk0.8.8--20210426--001` 上编译通过，**三个 ABI（arm64-v8a / armeabi-v7a / x86）的 `libijk{ffmpeg,player,sdl}.so` 随包发布**（`app/libs/`）。构建要点（NDK **r14b** 而非 r21e、`module-lite-hevc` + flac/vp9/vp8、`--disable-bsf=eac3_core`、Windows 克隆的 CRLF 与符号链接退化）全记在 `ijkpatch/README.md`。
- **应用侧回到「倍速就是真倍速」**：`applyAudioSpeed()` 恢复跟随倍速（`PlaybackParams.setSpeed(speed)`，API 23+；KDoc 说明视频那一路能变速靠的就是上面那个补丁）。新增 `longPressBoostActive` + `effectiveSpeedValue()` + `boostLabel()`：长按 3x 不再直接 `setSpeed(3f)`，而是把「有效倍速」交给 `applySpeedFromSeekbar()` 统一下发，松手即回滑条档位。**已删除**26.10.31 早间的「倍速换合一 MP4」绕行（`player/PlayerSpeedStreamPolicy.kt` 及其单测、`PlayerActivity.syncStreamForSpeed()`、`applyQuality` 的 `fromSpeedSync` 参数、`userQuality`/`streamSyncPending` 字段、三条相关文案）。保留 `PlayerApi.applyActualQuality()`：`getVideo`/`getBangumi` 用响应 `data.quality` 回填 `playerData.qn`，保证界面档位与实际流一致。
- **历史教训（26.10.31 早间那条路，已撤）**：先试过「倍速时把片源换成自带音轨的合一 MP4」（`applyQuality(qn, allowDash = false)`、`syncStreamForSpeed()` 挂在倍速入口），并让 `applyAudioSpeed()` 恒设 1x 以免两路分叉。放弃原因：① **登录态 + 大会员实测 `fnval=1&qn=80` 仍被降级成 `"quality":64,"format":"mp4720"`**，720P 是合一 MP4 的硬天花板，等于强制降清晰度；② 本地老缓存「纯视频 + 独立 m4a」在非 1x 下只能不变速；③ 旧版播放链路（a8dfe06 之前）本就只有一个 `durl[0].url`（合一流），修完不该比旧行为更差。这条弯路也解释了用户「以前 1080P 能倍速」的体感：旧版设置里选 1080P 时实际播的是 720P 合一流。
- **重同步连续 seek 的修法**：`MediaPlayer.seekTo` 是异步的，seek 未落地时 `currentPosition` 仍报旧值，下一拍 `progressChange` 会把「seek 还没生效」当成新漂移而继续 seek（模拟器 1x 下冒出 3 连 seek，听感是音频连跳三下）。所以重同步分支 seek 之后除了 `audioDriftSampled = false`，还要 `audioResyncSuppressMs = nowElapsed + AUDIO_RESYNC_COOLDOWN_MS`（沿用 1200ms 常量）进冷却期。
- **两个仍在的坑**：① `fnval=1` 的合一 MP4 自带音轨，**绝不能再挂外部音轨**（会两轨同时出声，见 §7.4 的 B1）；② DASH 流地址约 120 分钟过期（§7.35），超长视频中途 `reconnect_streamed` 重连用的是同一条过期 URL，可能 403 —— 目前未处理，属已知遗留。
- **实测（26.10.31，`emulator-5554` / AVD `wear5` / Android 14 x86_64，补丁版 `libijk*`）**：① 本地 DASH 双文件夹具 @2x：`本地进度` 每 15.1s 前进 30.3s = **2.00x**，`音频轨道重新同步` 只在起播对齐 1 次（补丁前每 ~1.3s 一次），`dumpsys media.audio_flinger` 同刻只有 1 条活跃 AudioTrack（**没有双路出声**）；@1x：resync 0 次、1.0x。② 在线 DASH 纯视频流（HTTP 夹具）@2x：进度上报 15s→30s→45s 各用时 ≈7.5s = **2x**。③ 合一 MP4 @2x 与补丁前一致 ≈2x（音频主时钟路径未受影响）。
- **验证边界**：模拟器上「在线 DASH 双流 + 外挂音轨」没跑通——宿主 HTTP 夹具只能经 `adb reverse` 给 ijk 用，媒体服务进程连不上；`10.0.2.2` 又被宿主防火墙挡住。所以该组合用「本地双文件夹具」等价验证（同一条 ijk 视频路 + 同一个外挂 MediaPlayer）。**arm 真机（手表）尚未实测**；未登录账号实际只拿到 DASH 的 720P 视频流，1080P 画面本体仍需登录（480P+）与大会员（1080P+/60fps）真机复核。
- **第二轮（26.10.30 晚）补齐 4K 与权限锁定**：
  - 播放链路改为**一律先试 DASH**（`getPlaybackVideo`/`getPlaybackBangumi` 不再用 `requiresDash` 分流）。原因不是低档位只能 DASH 拿，而是清晰度面板要靠 DASH 的完整档位清单：`fnval=1` 会按请求档位裁剪，720P 请求只回 `accept_quality:[64,16]`，1080P60/4K **整个从清单里消失**，面板上连可提示的锁定项都没有。只有 DASH 取流失败/缺音轨时才回退合一 MP4。
  - 取流请求的 `qn` 固定为 `PlayerApi.QN_REQUEST_MAX = 127`（8K 档）：`qn` 在 DASH 下不决定返回哪条流（真正播哪档由 `DashData.getVideoStream(playerData.qn)` 决定），只决定**清单宽度**。用低档请求会让更高档整体消失——下载侧 `QualityChooserActivity` 一直用 `qn = 127` 就是同一个原因。
  - **权限判据抽成 `player/QualityAccessPolicy.kt`**：`maxUnlockedQn(loggedIn, vip)` = 未登录 720P(64) / 非大会员 1080P(80) / 大会员不限；`isLocked(qn, ...)`；`needsLocked4kHint(values, ...)`。`QualityAccessPolicyTest` 9 例。
  - **账号状态缓存 `util/AccountStateStore.kt`**：`State(loggedIn, vip)`，来源是 `x/web-interface/nav` 的 `data.isLogin` 与 `data.vip.status`（项目里此前没有任何地方缓存登录/大会员状态）；`cached()` 读 SharedPreferences，`refresh()` 网络补查并落盘，无本地凭据（cookies/mid）时直接判未登录。`clampQuality(qn)` 把超权请求夹到权限内——服务端对超权请求会降级发流，有时比权限内最高档还低（实测 `play_qn=80` 匿名起播拿到 852x480），夹紧后界面档位与实际流才一致。
  - **播放器清晰度面板**：`QualitySelectorAdapter` 新增 `lockedMaxQuality` 与 `onLockedItemClickListener`，锁定项用 `icon_player_lock` 叠在按钮右侧 + `alpha=0.55`，点击只弹提示不切换；提示按档位分三种——`qn>=120`「4K 超清需要开通大会员」、未登录「登录后可选择 1080P 及以上的清晰度」、非大会员「开通大会员后可选择 1080P60 帧及以上的清晰度」。**4K 档位服务端对没权限的账号根本不下发**（实测匿名 `qn=127&fnval=4048` 只回 `[116,80,64,32,16]`），所以没权限时在列表末尾补一个锁定项当提示位；有权限（大会员）时**不补**——服务端会下发真实 120，硬补会在没有 4K 源的视频上造出假档位。
  - **设置页按权限隐藏而不是标锁**：`SettingQualityActivity.qnMap` 加了 `1080P60→116 / 4K→120`，列表先按 `cached()` 渲染、后台 `refresh()` 后按 `maxUnlockedQn` 过滤重渲染——设置项没有「点一下看提示」的语境，留一堆点不动的选项更迷惑。
- 文档同步：`docs/bilibili-api-notes.md` §3.12（取流结论）、`docs/FEATURES.md`（播放清晰度）、`docs/review/fix-progress.md`（本轮实测结论）。

---

## 10. 动手前的检查清单

1. 确认要改的类属于哪一层：`activity/`（UI）→ `api/`（网络+解析）→ `util/`（基建）→ `model/`（数据）。
2. 网络请求**必须**在 `CenterThreadPool.run {}` 里调，API 方法是阻塞的。
3. 新增页面：选基类（第 3.2 节）→ 注册 Manifest → 如需菜单入口再改 `MenuActivity.btnNames` + `loadMenuEnabled` 默认值。
4. 新增 API：加在 `api/` 下对应类里，用 `NetWorkUtil.getJson/post` + `FormData`，纯解析部分抽成 `static` 函数以便单测。
5. 改动后跑 `./gradlew.bat :app:assembleDebug`（无 CI，编译通过 + 真机手测为准）；解析逻辑改动补 `app/src/test/` 单测。
6. 改完顺手看一眼 `docs/review/fix-progress.md` 的待办，如果修掉了其中的条目就更新它。
