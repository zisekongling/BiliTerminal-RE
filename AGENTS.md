# AGENTS.md

## 语言约定

**与用户交流、代码注释、文档一律中文。**

## 先读架构地图

改任何非平凡代码前先读 **`docs/architecture-map.md`**：真实分层、基类体系、40 个 API 类映射、UI 模板、逐条坑点。本文件只放每次都要遵守的约定。

其他参考：`bilibili-API/`（B 站接口文档快照，改 API 时查）、`docs/superpowers/specs/`（功能设计文档，写新功能前读）、`docs/tutorial-system-redesign.md`（教程系统重做中——**改教程前先读它**，旧链路正在被替换）。

## 项目概况

- RE:哔哩终端（ReBiliClient），第三方 B 站**手表**安卓客户端，`main` 分支。
- 典型 vibe coding 产物：代码大量由 AI 生成。**改动前先读源码核实，注释和文档都可能是错的。**
- 版本号按 YY.MM.DD（`app/build.gradle`）。

## 架构（三条与直觉相反的）

1. **入口是 `BiliTerminal.kt`**（26.09.10 由 `BiliTerminal.java` 迁移而来），不是 `BiliTerminalApp.kt`（后者从未被实例化，死代码）。全局 Context 取 `BiliTerminal.context`——它在 companion 里用 `@JvmField` 暴露成**静态字段**，所以 Java 侧仍是 `BiliTerminal.context` 的字段读法，Kotlin 侧按需 `!!`。
2. **没有 DI / Retrofit / ViewModel**——26.09.11 死代码清理后这已是**事实**而非"死依赖残留"：Hilt、Retrofit、kotlinx-serialization、Jetpack Navigation、protobuf-javalite、geetest sensebot、asynclayoutinflater、cardview、lifecycle-viewmodel-ktx 等**已全部从 `app/build.gradle` 移除**，`ksp` 与 `kotlin.plugin.serialization` 两个插件也一并去掉（`@HiltAndroidApp` 随之从 `BiliTerminalApp.kt` 摘除）。原先 24 个空目录（`di/`、`network/`、`data/`、`ui/base` 等）已删除。新功能写进 `api/` + `activity/`，沿用静态方法 + `org.json`。
3. **只有一条链**：`activity/` → `api/`（全同步阻塞）→ `util/` → `model/`。导航由 `MenuActivity.btnNames` + `util/MenuConfig.kt` 决定。

## 硬约定

- 网络请求包在 `CenterThreadPool.run { }` 里。
- 列表页继承 `RefreshMainActivity`（菜单入口页）或 `RefreshListActivity`（返回式）；加载完必须 `setRefreshing(false)`，否则翻页永久卡死。`onLoad(page)` 的 page 已自增。
- 新增菜单页改三处：`MenuActivity.btnNames`、`MenuConfig.ALL_ITEMS`、`AndroidManifest.xml`。
- 新增设置项改三处：`util/SettingsKeys.kt`、设置页 `SettingSection`、`activity/settings/SettingsIndex.kt`。
- 外观设置（配色 / 卡片圆角 / 字体）走 `ui/appearance/` 三模块：**模块只放候选值与纯函数，写入一律走 `AppearanceManager`**（它负责递增外观版本号）；模块里别做几何计算，也别在热路径上缓存。细则见 `docs/architecture-map.md` §8.7。
- 新增设置子页面（独立 Activity，如「菜单设置」「我的页面设置」）也改三处：`SettingGroupActivity` 对应分组加 `nav`、`SettingsIndex` 加可搜索条目、`AndroidManifest.xml` 注册。
- 页面级排序/分区配置沿两个既有范式：`MenuConfig`（启用/未启用）与 `MySpaceConfig`（主列表/更多列表）——都是纯 Kotlin 单一数据源 + JVM 单测 + 复用 `MenuSettingAdapter` 式双分区拖拽写法。
- 请求与解析分离，纯解析抽成 `static`/`object` 函数并补 JVM 单测（参考 `HotSearchApi.parseHotSearch`；需要 SharedPreferences 时注入 `SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()`——该假实现是共享助手 `app/src/test/…/util/FakeSharedPreferences.kt`，**别再抄一份**）。
- **小步提交**：把改动切成小步，每步都能独立验证（`assembleDebug` + 单测通过）并说清楚改了什么，再进入下一步；不要一次性堆大量改动。
- 文案硬编码中文，只有设置页用 `strings.xml`（`desc_*` 惯例）。
- 不轻易引入新第三方库。
- **一切功能优先考虑手表端**，手机等设备只是顺便适配

## 每次改完必须同步文档

- **修 bug** → 在 `docs/review/fix-progress.md` 加记录并勾掉待办。
- **改架构 / 基建 / 新增基类** → 更新 `docs/architecture-map.md` 对应章节。
- **本文件描述的内容变了**（入口、约定、坑清单）→ 顺手改 `AGENTS.md`，别留过时描述。

## 构建

```bash
./gradlew.bat :app:assembleDebug      # 编译验证（无 CI，以此为准）
./gradlew.bat :app:testDebugUnitTest  # 纯 JVM 单测
./gradlew.bat :app:assembleRelease    # 正式包（R8 + ABI 分包）
```

多模块 `:app` / `:ijkplayer-java` / `:DanmakuFlameMaster` / `:brotlij`（后三个别乱动）。Gradle 8.11.1、AGP 8.5.2、Kotlin 2.0.0、JDK 17、minSdk 24 / compileSdk 34、`resConfigs 'zh'`。

坑：`gradle.properties` 第 9 行硬编码 `org.gradle.java.home`（Windows 路径），代理配置在第 88-92 行**是注释状态**；release 签名读 gitignore 的 `local.properties`；`copyApkToDesktop` 未挂在 `assembleRelease` 上，需手动跑。

**更容易踩的坑（build cache 回放陈旧资源）**：`gradle.properties` 开着 `org.gradle.configuration-cache` 与 build cache。只要你改动 `res/` 的**文件集合**（新增/删除/移动资源文件），`:app:mergeDebugResources` 可能 `FROM-CACHE` 回放一份旧结果，症状是编译报莫名其妙的 `Unresolved reference 'R.layout.xxx'` 或新的 string/color 找不到，而源文件明明存在。此时必须：

```bash
./gradlew.bat :app:clean --offline --no-configuration-cache
./gradlew.bat :app:assembleDebug --offline --no-build-cache --no-configuration-cache
```

判断依据：`app/build/intermediates/merged_res/debug/**/` 里找不到该资源对应的 `.flat` 文件，但 `aapt2 compile` 单独编译它又是成功的。

注意两点：
1. **`clean` 和 `assemble` 必须分两次 Gradle 调用**。写在同一次调用里（哪怕顺序正确）可能因配置缓存复用而报 `ManifestMerger2$MergeFailureException: NoSuchFileException: .../navigation_json/debug/extractDeepLinksDebug/navigation.json`。
2. 光加 `--no-build-cache` 不够时，把 `--no-configuration-cache` 也带上——配置缓存会复用过期的任务图，让上面那条 `Unresolved reference 'R.layout.xxx'` 继续复现。

增量编译还可能单独坏掉，报 `org.jetbrains.kotlin.util.FileAnalysisException … FileNotFoundException: app\build\tmp\kotlin-classes\debug\…\Xxx.class`（某个 class 被删了但增量状态没更新）。同样是 `clean` 后重建解决。

**量体积必须先 clean**：debug 包在**增量**构建下会产生大量 `classesN.dex` 分片，体积可以虚高 3~4 MB（实测同一份代码增量构建 19.96 MB、clean 构建 15.44 MB）。任何"改动前后比体积"的结论都必须在 `:app:clean` 之后分别测量。

**APK 体积构成（26.09.11 实测，release arm64 9.73 MB）**：native `.so` **67.3%**（`libijkffmpeg.so` 单文件就占一半）、dex 17.9%、`res/` 8.2%、`resources.arsc` 5.0%。**死代码删除对 release 体积几乎无影响**——R8 本来就会 strip 掉未引用的类；实测整轮死代码+死依赖清理只减了约 0.22 MB（2.2%）。真正的大头是 `libijkffmpeg.so`（5.16 MB），要动它得用 NDK 重编精简编解码器，属于另一件事。

## 改前必查的已知坑

完整清单见 `docs/architecture-map.md` 第 7 节。仍存在的：

- 视频卡片解析**重复 19 处**（`RankingApi`/`RecommendApi`×4/`WatchLaterApi`/`SearchApi`×2/`SeriesApi`/`FavoriteApi`×2/`UserInfoApi`/`HistoryApi`/`BangumiApi`/`MessageApi`×3/`DynamicApi`/`VideoInfo.java`），改一处要 grep 其余。旧文档写"7 份"，实测 19 处（见 `docs/review/cleanup-scan.md`）。
- `AppInfoApi` 有 4 处明文 `http://api.biliterminal.cn`。
- `PlayerApi.java:301` 的 `fnvar` 应为 `fnver`；`ReplyApi.java:245` 的 `likeReply` 硬编码 `type=1`。
- `DownloadService.start()` 无同步，可并发写同一文件。
- `docs/review/00-summary.md` 基于 26.08.27 快照（已过时），动手前先 grep 现状。

## 原生库

`app/libs/{arm64-v8a,armeabi-v7a,mips,x86}/` 是已提交的 `.so`，日常别动；重建需 NDK r21e（见 `rebuild_all.sh` 等脚本）。
