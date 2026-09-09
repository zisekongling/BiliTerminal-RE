# AGENTS.md

## 语言约定

**与用户交流、代码注释、文档一律中文。**

## 先读架构地图

改任何非平凡代码前先读 **`docs/architecture-map.md`**：真实分层、基类体系、40 个 API 类映射、UI 模板、逐条坑点。本文件只放每次都要遵守的约定。

其他参考：`bilibili-API/`（B 站接口文档快照，改 API 时查）、`docs/superpowers/specs/`（功能设计文档，写新功能前读）、`docs/tutorial-system-redesign.md`（教程系统重做中——**改教程前先读它**，旧链路正在被替换）。

## 项目概况

- RE:哔哩终端（ReBiliClient），第三方 B 站安卓客户端，`main` 分支。
- 典型 vibe coding 产物：代码大量由 AI 生成。**改动前先读源码核实，注释和文档都可能是错的。**
- 版本号按 YY.MM.DD（`app/build.gradle`）。

## 架构（三条与直觉相反的）

1. **入口是 `BiliTerminal.java`**，不是 `BiliTerminalApp.kt`（后者从未被实例化，死代码）。全局 Context 取 `BiliTerminal.context`。
2. **没有 DI / Retrofit / ViewModel**：Hilt、Retrofit、kotlinx-serialization 是死依赖；`di/`、`network/`、`data/`、`ui/base` 等 23 个目录是空的。新功能写进 `api/` + `activity/`，沿用静态方法 + `org.json`。
3. **只有一条链**：`activity/` → `api/`（全同步阻塞）→ `util/` → `model/`。导航由 `MenuActivity.btnNames` + `util/MenuConfig.kt` 决定。

## 硬约定

- 网络请求包在 `CenterThreadPool.run { }` 里。
- 列表页继承 `RefreshMainActivity`（菜单入口页）或 `RefreshListActivity`（返回式）；加载完必须 `setRefreshing(false)`，否则翻页永久卡死。`onLoad(page)` 的 page 已自增。
- 新增菜单页改三处：`MenuActivity.btnNames`、`MenuConfig.ALL_ITEMS`、`AndroidManifest.xml`。
- 新增设置项改三处：`util/SettingsKeys.kt`、设置页 `SettingSection`、`activity/settings/SettingsIndex.kt`。
- 请求与解析分离，纯解析抽成 `static`/`object` 函数并补 JVM 单测（参考 `HotSearchApi.parseHotSearch`、`NetWorkUtilTest` 的 FakeSharedPreferences 手法）。
- **小步提交**：把改动切成小步，每步都能独立验证（`assembleDebug` + 单测通过）并说清楚改了什么，再进入下一步；不要一次性堆大量改动。
- 文案硬编码中文，只有设置页用 `strings.xml`（`desc_*` 惯例）。
- 不轻易引入新第三方库。

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

## 改前必查的已知坑

完整清单见 `docs/architecture-map.md` 第 7 节。仍存在的：

- 视频卡片解析**重复 7 份**（`RankingApi`/`RecommendApi`×3/`WatchLaterApi`/`SearchApi`/`SeriesApi`/`FavoriteApi`/`UserInfoApi`），改一处要 grep 其余。
- `AppInfoApi` 有 4 处明文 `http://api.biliterminal.cn`。
- `PlayerApi.java:301` 的 `fnvar` 应为 `fnver`；`ReplyApi.java:245` 的 `likeReply` 硬编码 `type=1`。
- `DownloadService.start()` 无同步，可并发写同一文件。
- `docs/review/00-summary.md` 基于 26.08.27 快照（已过时），动手前先 grep 现状。

## 原生库

`app/libs/{arm64-v8a,armeabi-v7a,mips,x86}/` 是已提交的 `.so`，日常别动；重建需 NDK r21e（见 `rebuild_all.sh` 等脚本）。
