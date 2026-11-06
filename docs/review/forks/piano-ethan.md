# 与 PianoEthan/BiliTerminal（v3.1.3-Qx）分叉对比报告

- 报告日期：2026-11-06
- 我方基线：`D:\Users\ASUS\Desktop\DEVELOP\ReBiliClient`，`main` @ `1c404c3`，版本 26.11.06.1，minSdk 19 / compileSdk 34
- 对比基线：`D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\BiliTerminal-PianoEthan`，`main` @ `f24a392`（2026-11-01），tag `v3.1.3-Qx`，versionCode 20261101
- 预计算差异清单：`fork-analysis/cmp/BiliTerminal-PianoEthan/{only-fork,only-ours,modified}.txt`（git blob 哈希口径）
- 一句话结论：**对方在功能增量上几乎只有一个域有货——主题引擎（M3 动态色板 + `.btheme` 主题包 + 背景图 + 封面取色）；其余 60 个提交大多是「基于上游 2.9.8 的纯 Java 老设备兼容补丁 + Gson 反序列化迁移」。我们已经在绝大多数域领先。推荐只搬「主题」这一块里成本可控的 4 个切片，外加一条真实的文件句柄泄漏修复。**

## 1. 方法与边界

### 1.1 证据口径

- 每条结论标【事实】（代码/命令可复核）或【推测】（由代码推得、未真机验证）。
- 路径前缀 `[ours]` = 我方仓库根，`[theirs]` = 对方克隆根；行号均为报告成文前用 `Select-String`/`Get-Content` 逐条复核过的真实行号。
- 「对方有、我方无」的判定**只用路径 + 语义双证据**：先按 blob 哈希得到差异清单，再按 basename 过滤掉「文件改名/语言迁移」噪声（见 §1.3），最后逐条读对方源码确认功能语义，再到我方代码核实对应能力。

### 1.2 明确没做的事（避免过度声明）

1. **可读范围受任务限制**：报告只依据**公开仓库**的现有文件（`app/`、`docs/`、构建脚本）与对方公开克隆；未读取、也未引用任何非公开内容。因此凡涉及我方能力的描述，均以公开仓库为准；若某能力不在公开部分，本报告的「我方现状」会写得比实际弱，属口径限制而非错误。
2. **没有构建/真机运行对方**：对方 minSdk 14、构建脚本用旧 Gradle 与阿里云镜像仓库，未在本次分析中编译或装表；凡涉及「对方某功能是否真好用」的结论，只到代码层，不外推运行时表现。
3. **没做逐文件穷举**：对方 781 个 `app/src/main` 文件 vs 我方 1515 个，差异面 298/1032/433 三条清单，按要求用**目录聚合 + 关键字定位 + 提交信息**收敛到域级结论。

### 1.3 差异面口径

【事实】对方仓库 `only-fork.txt` 298 条，其中 `app/src/main/java` 202 条、`material-color-utilities` 40 条、`app/src/main/res` 33 条、`app/src/test` 5 条、docs 样例 7 条。

【事实】`only-fork` 里 202 个 java 路径中，**绝大多数不是「新功能」**，而是「我方已改用 Kotlin / 已重构删除」的同名类：例如 `activity/base/BaseActivity.java`、`activity/search/SearchActivity.java`、`api/*Api.java`——对方全仓库是纯 Java，我方同名逻辑在 `.kt` 里，blob 哈希口径必然把两边都算成「独有」。

【事实】按 basename 去重后（只比 `app/src/main/java` 下的 202 条），对方**真正没有对应物**的只有 **37 个主代码文件**（另有 5 个 `app/src/test` 文件）：

| 组 | 数量 | 内容 |
| --- | --- | --- |
| 主题引擎本体 | 12 | `theme/` 包 9 个 + `theme/model/` 2 个（`ThemeManifest`、`InstalledTheme`）+ `event/ThemeChangedEvent` |
| 主题设置 UI | 2 | `activity/settings/ThemeSettingsActivity`、`adapter/ThemeListAdapter` |
| 收藏 v3 契约层 | 2 | `api/FavoriteContract`、`model/FavoriteSelectionState` |
| `material-color-utilities` 模块 | 40（独立 module，不计入 37） | 上游 Apache-2.0 源码 vendored |
| 上游遗留工具/类 | 21 | `util/{GsonUtil,CookieGenerator,CompatUtil,SSLSocketFactoryCompat,ImageViewer,ViewCache}`、`ui/widget/{CustomListView,recycler/AbstractAdapter,recycler/BaseAdapter,recycler/BaseHolder}`、`adapter/{CoinLogAdapter,ExpLogAdapter,article/ArticleContentAdapter,video/LocalVideoAdapter}`、`api/BilibiliIDConverter`、`model/{ApiResponse,OpusCard}`、`activity/settings/{SettingLaboratoryActivity,SettingUIActivity,SortSettingActivity}`——我方在架构精简/Kotlin 迁移中删掉或重写 |
| 单元测试（`app/src/test`） | 5 | 主题 3 个（`TintRulesTest`、`SemanticTokensTest`、`TintViewBranchOrderTest`）+ 收藏 2 个（`FavoriteContractTest`、`FavoriteSelectionStateTest`） |

即：**「对方有货」的域只有主题、以及一小撮工具类整理**。这一条决定了本报告的篇幅分配。

## 2. 身份与基线

### 2.1 血缘与自称

【事实】`[theirs] readme.md:9-10`：「原项目已停更并归档存档。此分支为基于最新 2.9.8 版本的二次开发版本」；根提交 `86280ff`（2026-06-05）信息为「Initialize project: BiliClient 2.9.8 with DynamicApi fix」。

【事实】`[theirs] readme.md:23`：「本分支将继续对老旧设备优化…**不会**增加莫名其妙的功能，**不会**对UI布局和界面进行大改，**不会**引入任何非B站官方的在线服务（如公告、更新等）」。

【事实】对方 60 个提交、9 个 tag（`2.9.8fix`、`v3.0.0`、`v3.0.2-Qx` … `v3.1.3-Qx`），时间跨度 2026-06-05 → 2026-11-01。

【事实】我方与对方**同源不同支**：我方是上游的 Kotlin 重构分支（`docs/architecture-map.md:19` 记录 `ui/*`、`api/`、`activity/` 的全量重构），对方是上游 2.9.8 的纯 Java 延续。`applicationId` 两者都是 `com.RobinNotBad.BiliClient`（`[theirs] app/build.gradle:27`），即**两者不能在同一台手表上共存**——这与我方「普通版/共存版」双 flavor 的解法不同（`docs/FEATURES.md:62`）。【推测】因此对方用户装我方需要先卸载，这对「对比报告评价值」不构成影响，但影响我们做兼容/迁移提示时的文案假设。

### 2.2 技术栈与构建参数

| 维度 | 对方（`[theirs] app/build.gradle`） | 我方（`[ours] app/build.gradle`） |
| --- | --- | --- |
| 语言 | 纯 Java（`sourceCompatibility 11`，`build.gradle:93-96`） | Kotlin 为主 + 少量 Java（`compileOptions` Java 17，`build.gradle:174-176`） |
| compileSdk / minSdk / targetSdk | 33 / **14** / 26（`:11`、`:28`、`:30`） | 34 / 19 / 34（`:22`、`:43`、`:44`） |
| 版本 | 3.1.3-Qx（`:32`，versionCode 20261101 `:31`） | 26.11.06（`:46`） |
| ABI | `armeabi-v7a` / `x86` / **`mips`**（`ndk.abiFilters`） | arm64-v8a / armeabi-v7a / x86 分包（见 `AGENTS.md`） |
| 模块 | app + `ijkplayer-java` + `DanmakuFlameMaster` + `brotlij` + **`material-color-utilities`**（`settings.gradle:26-30`） | 单 app 模块 + `app/libs/*.so` |
| 新增依赖 | `gson:2.8.9`（`app/build.gradle:143`）、`material-color-utilities`（`:135`）；其余 okhttp 3.12.1 / glide 4.13.2 / eventbus / material 1.9.0 与上游同代 | okhttp 4.12.0 / glide 4.16.0 / eventbus 3.3.1 等现代版 |

【事实】对方的 `material-color-utilities` 是 **vendored 源码模块**（`[theirs] material-color-utilities/build.gradle:1-3` 注释写明「Vendored from material-foundation/material-color-utilities, commit 03336bf6 (2024-06)，官方 Java 库没有独立 Maven 坐标」），自身 `minSdk 14`、`compileOptions Java 1.8`。这一点直接影响移植结论（§3.8）。

### 2.3 发布节奏与工程化

【事实】对方 2026-08 之前基本是「一次性合并上游 PR」（`Merge pull request #2/#4/#5/#7/#9/#10/#11`），2026-10 之后节奏变密：10-18 发 3.1.0-Qx（一次 40+ 文件的「低版本兼容 + 稳定性/安全」大补丁 `33090d9`），10-31 加 CI（`8f0179d`：推 `v*` tag 自动 `assembleRelease` + 签名 + GitHub Release），11-01 收尾 3.1.3-Qx。

【事实】对方有 1 个工作流 `[theirs] .github/workflows/build-release.yml`；签名走 Secrets（`KEYSTORE_BASE64` 等，见 `8f0179d` 提交信息）。但我们仍应记两笔账：

1. `[theirs] app/build.gradle:63-66`：无本地 `local.properties` 时 **fallback 到仓库内 `app/testkey.jks` + `storePassword 'android'`**（该 jks 确实被提交进仓库）。
2. 【事实】历史提交 `a2583fe`（删除 `app/build.gradle`）+ `c9fde83`（提交信息自述「把签名密钥露出来了」）出现过一次签名配置误入仓库的事故。

这两条对我们**不是可借鉴项，而是反面教材**：我方 Release 签名只在 CI Secrets，且 `docs/agents/release.md` 有发版校验（`app/build.gradle` 的 `verifyVersionConsistency`）。

【事实】安全姿态对比：对方清单里 `android:exported="true"` **22 处**，我方 **2 处**（仅 `SplashActivity` / `GetIntentActivity`，见 `AGENTS.md` 红线）。对方 3.1.0 提交信息自称「收紧导出组件」，但绝对数量仍高。

### 2.4 许可证与目标设备

【事实】`[theirs] LICENSE` 为 GPL-3.0，与我方一致；`material-color-utilities` 子模块自带 Apache-2.0 `LICENSE`（vendored 三方库保留原许可，属正常做法）。

【事实】目标设备：对方 readme 明写「最低支持**安卓4.0**」（`readme.md:13`）、「对老旧设备优化」（`:23`），`minSdk 14` + `targetSdk 26` + `abiFilters` 含 `mips`。我方 readme/`AGENTS.md` 是「安卓手表优先，minSdk 19」。**两者对「老设备」的下限定义不同**：14 vs 19，中间差 Android 4.0–4.3 一整代。这解释了对方 60 个提交里为什么有大量我们根本不需要的兼容补丁（§5.4）。

## 3. 主题引擎（核心，对方唯一成体系的功能增量）

### 3.1 两套架构的正面碰撞

| 维度 | 对方 | 我方 |
| --- | --- | --- |
| 数量 | 1 套基础主题 + 2 个内置预设 + 任意 `.btheme` | **7 套编译期主题** + 各自暗/亮两版 |
| 颜色来源 | 种子色 → MCU 生成（运行时计算） | `res/values/colors.xml` 写死（329 个 `<color>`） |
| 主题资源 | `[theirs] res/values/themes.xml` **4 个 `<style>`**、`colors.xml` **10 个色** | `[ours] res/values/themes.xml` **86 个 `<style>`**、`colors.xml` **329 个色**、4 个 `-notnight` 覆盖目录 |
| 落地方式 | **程序化遍历视图树染色**（`ThemeApplier`，85 处调用点 / 18 个文件） | XML `?attr/` + 资源限定符（我方布局里 **78 处** `?attr/`） |
| 入口 | 新设置页 `ThemeSettingsActivity` | 外观设置分组 `[ours] activity/settings/SettingGroupActivity.kt:347 buildAppearanceGroup()` |
| 变更通知 | EventBus `ThemeChangedEvent` + generation | `AppearanceManager.version()` + `BaseActivity.onResume` 比对重建（`[ours] BaseActivity.kt:560`） |

【事实】这两套是**互斥范式**：对方把所有颜色改成运行时算出来的，代价是必须自己遍历视图树补色；我方把颜色编进主题资源，代价是**编译期就固定**、无法支持任意种子色。

【事实】我方并非没有「运行时染色」的先例：`[ours] ui/appearance/ThemeTint.kt`（`object ThemeTint`，XML 写死颜色的 drawable 按当前主题着色）、`AppearanceApplier.kt:39`（字体+圆角的视图树遍历，由 `[ours] BaseActivity.kt:597` 在 `onContentChanged` 调用）。**也就是说我方已经具备「遍历视图树 + 外观版本号」这两块地基，缺的只是一遍「颜色 pass」。** 这是本报告对移植成本判断的关键依据。

### 3.2 `.btheme` 文件格式（format 1）

【事实】`[theirs] docs/sample-theme/README.md:3-4` 定义：`.btheme` 是 **ZIP 容器（format 1）**，条目白名单 `theme.json`（必需 ≤64KB）、`preview.png`（≤1MB）、`background[_dark|_light].png`（≤6MB）；整包 ≤8MB、≤12 条目。

【事实】`theme.json` 的真实样例（从 `docs/sample-theme/aurora.btheme` 解出，单行压缩 JSON）：

```json
{"format":1,"id":"example.aurora","meta":{"name":"极光","author":"…","version":1,"description":"…"},
 "preview":"preview.png","colors":{"mode":"auto","seed":"#008080",
 "source":{"image":"background.png","fallback":"#008080"},"contrast":0.0,"blend":40,
 "override":{"link":"#00E5FF","danger":"#FF5252","selected":"#64FFDA"}},
 "background":{"image":"background.png","fit":"centerCrop","scrim":0.5,"scrimColor":"#000000"}}
```

字段与代码一一对应（`[theirs] theme/model/ThemeManifest.java`）：

| 字段 | 行 | 语义（源码注释原意） |
| --- | --- | --- |
| `format` | `:10`、`:12` | 固定 1；`> FORMAT` 直接拒绝 |
| `id` | `:13` | 目录名 + 唯一键；非法字符拒绝 |
| `meta.{name,author,version,description}` | `:19-24` | 列表展示用 |
| `colors.mode` | `:28` | `auto` / `dark` / `light` |
| `colors.seed` | `:30` | 显式种子色；存在则忽略 `source` |
| `colors.source.image` + `.fallback` | `:40-45` | 背景图取色，失败回落 fallback |
| `colors.contrast` | `:33` | −1.0…1.0（MCU 对比度） |
| `colors.blend` | `:35` | 0–100 中性色染色强度；缺省 −1 表示取用户设置 |
| `colors.override` | `:37` | 令牌覆盖表（`primary`/`surfaceCard`/`link`/`danger`/`selected` …） |
| `background.{fit,scrim,scrimColor,image,image_dark,image_light}` | `:47-56` | `centerCrop`/`centerInside`/`fitXY`/`tile`；scrim 0.0–0.9 |

【事实】**对方没有「导出」和「App 内生成」**：`theme/` 包内 `grep export|ZipOutputStream` **0 命中**，`ThemeSettingsActivity` 只有导入入口（`:163-173` 选文件 / `:85-101` 手输路径）。样例包靠仓库里的 Python 脚本 `docs/sample-theme/make_samples.py`（依赖 Pillow）在开发期生成。**所以任务描述里的「导出/生成」在对方并不存在，只能写主题包的生产流程是「外部脚本」——这一点是对方的缺口，反而给我们留了一个低成本差异化点（§7 T5 备注）。**

### 3.3 安装流程与校验（`theme/BThemeInstaller.java`，240 行）

【事实】`install()`（`:97`）是一条**全程不抛异常、错误信息面向用户**的流水线：

1. 流 → cache 临时文件，边拷边计数，超 `MAX_PACKAGE_BYTES`（`:28`，8MB）即拒（`:104`）；
2. 第一遍扫 `ZipInputStream`：条目数 > `MAX_ENTRIES`（`:29`，12）拒绝；条目名不在 `WHITELIST`（`:34`：仅 `theme.json` / `preview.png` / `background[_dark|_light].png`）直接拒（`:124`）——**这是天然免疫 zip-slip 的写法**（不落地任何未在白名单的路径，`../evil.png` 连写都写不出去）；
3. 单独限流读 `theme.json`（`MAX_THEME_JSON` 64KB，`:30`、`:130`），Gson 解析（`GsonUtil.fromJson`）→ `format > 1` 报「主题版本过新」(`:150`)，`id` 正则 `^[a-zA-Z0-9._-]{1,64}$`（`:63`）不过报「主题 ID 不合法」；
4. 第二遍解压到 cache 临时目录（每张图分别按 6MB / 1MB 限流，`:177`）；
5. **原子替换**：`deleteFolder(finalDir)` + `renameTo`，失败则清临时目录；
6. 卸载（`BThemeInstaller.java:217-226`）：若正被使用先 `setTheme("")` 回退内置，内置预设不可删。

【事实】配套的**负样例**（`docs/sample-theme/`）是这套代码的测试契约：`zip-slip.btheme`、`oversize.btheme`、`entry-too-big.btheme`、`format99.btheme`、`no-json.btheme`，README 表格逐条写明「预期拒绝原因」（`README.md:16-20`）。

【推断→不采纳为事实】`BThemeInstaller` 没有对应的 JVM 单测（`app/src/test` 里只有 5 个测试文件，全部是 theme 的染色规则与收藏契约，没有安装器）。所以「负样例」目前是**手工回归清单**而不是自动化测试。这对我们是反面参考：我们搬这套时必须补 `ZipOutputStream` 现造假 zip 的单测。

### 3.4 色板生成（`theme/SchemeEngine.java`，130 行 + MCU）

【事实】核心链路（`SchemeEngine.generate`，`:21`）：

1. `Hct.fromInt(seed)` → `SchemeContent(seedHct, dark, contrast)`（`:32`，M3 Content 方案）；
2. **中性色按 blend 强度染色**：`TonalPalette.fromHueAndChroma(seed.hue, seed.chroma * blend/100)`（`:36`），再塞进 `DynamicScheme`（Variant.CONTENT）；
3. 取 9 个令牌：`onSurface`/`onSurfaceVariant`/`surfaceContainer`/`surfaceContainerHigh`/`primary`/`tertiary`/`outline`，加 alpha 后落到 `ThemePalette` 的 18 个字段（`textPrimary`/`accent`/`surfaceCard`/`ripple`/`snackBg`/`highEnergy*`…）；
4. **功能色 harmonize**：`link`/`success`/`danger`/`selected` 四个语义色用 `Blend.harmonize` 向种子色相靠拢（`:48-51`）；浅色档额外把 tone 压到 40 保证白底可读（`:85-92`，源码注释记录了「历史上直接拿深色字面量导致白底对比度只剩 1.2~1.8」的教训）；
5. `applyOverrides`（`:94`）：`colors.override` 里 19 个键（`textPrimary`/`textSecondary`/`ripple`/`windowBackground`/`primary`/`accent`/`gray`/`accentLow`/`success`/`link`/`surfaceCard`/`snackBg`/`highEnergyLine`/`highEnergyFill`/`buttonTint`/`selectedText`/`danger`/`selected`）逐个覆盖；未知键忽略。

【事实】另有两个内置预设（`theme/ThemeManager.java:40-43`）：`builtin.summer_green`（种子 `#00C853`）、`builtin.ice_blue`（`#4FC3F7`）。预设**不落盘**，只存 `theme_id = "builtin.*"`。

【事实】种子优先级明确（`ThemeManager.resolveSeed()`，`:286`）：显式 `seed` > 背景图提取种子（异步提取后持久化到 `theme_extracted_seed_<id>`）> `source.fallback` > 默认中性灰 `0xFF9E9E9E`。

【事实】切换主题时 `postChanged()`（`:299`）带 **2 秒防抖 + 500ms 二次抑制**，把 `ThemeChangedEvent(generation)` post 到 EventBus，避免「冷启动提取种子 + Activity recreate」双重触发。

### 3.5 背景图

【事实】`ThemeManager.getActiveBackgroundFile()`（`:323`）按当前明暗取 `image_dark`/`image_light`，回落 `image`，受 `theme_bg_enable` 门控；`getBackgroundFit()` 缺省 `centerCrop`。

【事实】`ThemeApplier.setupWindow()`（`:66`）负责把背景图铺到窗口：解码限制（≤2× 屏幕、`RGB_565`、`SoftReference` 缓存，`:206`），按 `fit` 适配（`:229`），叠加 `scrim` 遮罩，再 `setBackgroundDrawable` 到窗口；同时做 edge-to-edge（`:136`，含状态栏隐藏时避免「双重下移」的 insets 判定 `:185`）。

### 3.6 内容取色（封面 → 强调色）

【事实】`theme/ContentTintHelper.java`（113 行）：封面 URL → Glide 64px/`RGB_565` 取图（`:74-80`，8s 超时）→ `ColorExtractor.extractFromBitmap`（`QuantizerCelebi` 128 色 + `Score.score`，`ColorExtractor.java:28-30`）→ 得到种子 → `Hct.from(hue, chroma×1.2, tone)` 派生 4 档小色板（`accent`/`onAccent`/`container`/`stroke`，tone 深 80/20/30/60、浅 40/100/90/50，`:104-107`），两级 LruCache（URL→seed 64 条、seed+明暗→tint 32 条）。

【事实】**而且范围收得很窄**：全仓唯一调用点是 `[theirs] PlayerActivity.java:1747`（播放器进度条/高能曲线），10-18 的提交 `b106b31` 标题就叫「内容取色收窄到播放器，详情页图标不再跟随封面」。这条「先做窄、再评估扩散」的节奏对我们很有参考价值。

### 3.7 UI 入口与交互

【事实】入口：`[theirs] activity/settings/SettingMainActivity.java:85-90` 的「主题设置」卡片 → `ThemeSettingsActivity`（`app/src/main/res/layout/activity_theme_settings.xml`），页面结构：

| 区块 | 控件 | 行为 |
| --- | --- | --- |
| 导入 | `importTheme` 按钮（`layout:59`） | `ACTION_GET_CONTENT` 选任意文件（`ThemeSettingsActivity.java:163-173`，`ACTION_GET_CONTENT` 在 `:165`）；`API<19` 提示改用手输路径（`:172`） |
| 导入（老设备） | `themePathInput` + `themePathInstall`（`layout:84`、`:93`） | 手输绝对路径导入，先查存储权限（`:92`） |
| 列表 | `RecyclerView themeList`（`layout:121`）+ `ThemeListAdapter` | 行 = 默认（暗黑）/ 2 内置预设 / 已装 `.btheme`；显示 `preview.png`（72×72，Glide）、作者、描述；点击切换，**3 秒内长按两次删除**（`ThemeSettingsActivity.java:54-70`），预设与默认不可删 |
| 开关 | `switchDark` / `switchBg` / `switchExtract` / `switchContentTint`（`layout:141-166`） | 深色、显示背景图、背景取色、播放器封面取色 |
| 数值 | `blendInput` + `blendApply`（`layout:189`、`:197`） | 0–100 中性色染色强度 |

【事实】文案集中进 `strings.xml:231-241`（`pagename_setting_theme`/`theme_import`/`theme_import_path`/`theme_list_title`/`theme_dark`/`theme_bg_enable`/`theme_blend`…），这一点和我方规范一致。

### 3.8 可移植性结论

这是本报告最需要给出可执行判断的地方。三条关键事实：

**（1）MCU 的 minSdk 阻塞点只有一个文件、一个类型。** 【事实】`material-color-utilities` 全模块中，`java.util.function.Function` / `stream` 只出现在 `dynamiccolor/DynamicColor.java`（1 个文件），其余 39 个文件不依赖 Java 8 库。对方因此在提交 `3d79357` 里退回「API 24 才允许用主题」，之后 `f577723` 又放开。**也就是说：把 `Function` 换成自建的小接口（约 10 行机械改写），整套 M3 方案在 minSdk 19 上是可跑的，不需要 `coreLibraryDesugaring`。** 我方 `app/build.gradle` 目前**没有** `coreLibraryDesugaring`（grep `desugar` 0 命中），所以这条替代路线是有实际意义的，不要照抄对方「API 24 门禁」的做法。

**（2）我方 78 处 `?attr/` 是硬约束。** 任意种子的运行时色板**进不了编译期主题属性**。要让它全局生效，要么走对方的程序化染色（L），要么接受「只对代码侧颜色生效、XML 控件不跟随」的不一致。我方 `AppearanceApplier`（视图树遍历 + version 机制）已经存在，是最现实的挂载点，但要注意：`[ours] AppearanceApplier.kt:16-20` 记录了「用主题属性 `?attr/` 做圆角运行时切换在真机上半径变 0」的实测坑，说明**属性间接层在这套 minSdk 19 + AppCompat 组合上不可靠**，程序化遍历是唯一稳妥路线。

**（3）我方的主题数据结构比对方大。** 我方 `ColorScheme.ThemeColors` 有 **36 个字段**（`[ours] ColorScheme.kt:55-98`），对方 `ThemePalette` 18 个字段，且我方另有亮色 `LightPalette` 覆盖表（`:391`、`:454`）。若要「种子色生成」，需要先把 36 字段映射到 M3 令牌（`PRIMARY`→`primary`、`SURFACE`/`CARD`→`surfaceContainer` 系列、`LIKE/COIN/FAV/SHARE`→业务色保持写死、`PLAYER_*`→恒深色套…）。这个映射表是移植的真正工作量所在，不是 MCU 本身。

**结论（推荐路线）**：

| 路线 | 内容 | 成本 | 建议 |
| --- | --- | --- | --- |
| A | 只搬「封面取色」+「背景图」两个自包含切片，颜色体系不动 | S–M | **推荐先做**（§7 T2/T3） |
| B | 搬 MCU 子集 + 给 7 套内置主题换成「7 个种子 + 生成」，保留主题名 | M | **推荐第二步**（§7 T4），但要先用单测锁住渲染色 |
| C | 完整复刻 `.btheme` 包 + 任意种子 + 程序化全树染色 | L–XL | 需要先做 B，再做 T5/T6；**不建议一次做完** |

## 4. 功能增量清单（按域）

> 判定规则：只有「对方有且我方无/明显更弱」才进本表；对方有而我方更强/等价的一律在备注里点明，避免把对岸的旧实现误当增量。

| 域 | 功能 | 用户价值 | 证据（对方） | 我方现状 | 难度 | 依赖与风险 |
| --- | --- | --- | --- | --- | --- | --- |
| 外观 | **`.btheme` 主题包导入/切换/删除** | 用户可装第三方主题、分享主题 | `theme/BThemeInstaller.java:97`、`activity/settings/ThemeSettingsActivity.java:31` | 无（7 套编译期主题，`[ours] ColorScheme.kt:41-54`） | L | 需 MCU 或自有色板映射；有 zip 解析攻击面（须照抄白名单 + 限额） |
| 外观 | **任意种子色 → M3 色板**（含深/浅两套、blend、contrast） | 一套主题无限派生，不再只有 7 套 | `theme/SchemeEngine.java:21-92`、`ThemeManager.java:268` | 无（7 套写死表 + 7 张亮色覆盖表 `ColorScheme.kt:454`） | M（仅接入生成）/ L（若要 XML 也跟随） | 需 vendored MCU；minSdk 19 需处理 `DynamicColor.java` 的 `Function` |
| 外观 | **主题背景图**（fit / scrim / 深浅各一张 / 原子安装） | 观感提升最大的单点；我方只有「弥散光晕/玻璃质感」两种程序化装饰 | `theme/ThemeManifest.java:47-56`、`ThemeApplier.java:206-268`、`ThemeManager.java:323` | 无背景图（`[ours] docs/FEATURES.md:866-877` 只有光晕/玻璃） | S–M | 手表内存与滚动性能；须做解码采样 + SoftReference |
| 外观 | **播放器封面取色**（进度条/高能曲线随封面变色） | 播放页个性化 | `theme/ContentTintHelper.java:60`、`[theirs] PlayerActivity.java:1747` | 无（我方进度条固定主题色 `[ours] ColorScheme.PLAYER_PROGRESS_FILL`） | M | 需 MCU 的 `quantize`+`hct`（不含 `dynamiccolor`，**无 Java 8 库依赖**）；须缓存与超时 |
| 外观 | **主题设置独立页 + 预览图列表 + 长按删除** | 主题可管理 | `activity/settings/ThemeSettingsActivity.java`、`res/layout/activity_theme_settings.xml` | 无（我方外观设置是设置分组里的若干行，`[ours] SettingGroupActivity.kt:347`） | S–M | 需先有主题包能力；我方设置页有 `SettingSection` DSL，接入成本低 |
| 外观 | 语义令牌 `danger`/`selected`/`link` 独立于装饰色 | 删除等破坏性操作不会被主题色冲淡 | `theme/ThemePalette.java:26-35`、`SchemeEngine.java:94` | **我方已有且更细**（`ERROR`/`SUCCESS`/`WARNING`/`LINK` 等 36 字段） | — | 无增量，**不要照抄 18 字段模型** |
| 外观 | 深/浅两套由同一主题派生 | 切模式色彩一致 | `ThemeManager.computePalette` | **我方已有**（`-notnight` 资源覆盖 + `LightPalette`，`[ours] ColorMode.kt:34`） | — | 等价，我方做法更省运行时开销 |
| 播放器 | 直播弹幕回调切主线程 + 销毁顺序 | 稳定性 | `470047f`（`PlayerActivity.java` 17 行改动） | **我方已更强**：`destroyed` 守卫 + `volatile` 字段（`[ours] PlayerActivity.kt:131`、`:234`、`:388`、`:1698`、`:1706`） | — | 不建议搬（会与我们的事件时序打架） |
| 评论 | 评论带图发送 | 可发图评论 | `277f356`（`ReplyApi.uploadReplyImage` + `WriteReplyActivity`） | **我方已有且更完整**：`uploadReplyImage`、`biz=new_dyn`、上传未完成的竞态提示、`buildPictures` 纯函数化+单测（`[ours] WriteReplyActivity.kt:102`、`:163`、`:248`、`:385`） | — | 明确不搬 |
| 收藏 | 收藏 v3 接口 + 选择态机 | 收藏可用 | `415edc6`：`api/FavoriteContract.java`(266 行) + `model/FavoriteSelectionState.java`(34 行) + 2 个单测 | **我方远超**：`folder/{add,edit,del,info}`、`resource/{list,batch-deal,copy,move,clean}`、订阅体系（`[ours] docs/FEATURES.md:475-513`） | — | 只可借鉴「契约层 + 纯状态机 + 单测」的写法 |
| 私信 | 私信 PiliPlus 兜底（`a012365` 合并 PR #7） | 私信可用性 | 上游 PR 合并，见提交 `a012365` | 我方有独立 `PrivateMsgApi`/会话体系（`docs/FEATURES.md` 第 9 章） | — | 未见增量证据，建议不追 |
| 搜索 | 表冠（旋钮）滚动修复 | 手表可用性 | `c87e2ce`（`SearchFragment` 12 行：LayoutManager 类型判断 + ViewPager2 换 ViewPager） | **我方体系更强**：全局 `RotaryEncoderSupport`（`[ours] ui/widget/RotaryEncoderSupport.kt:40`，`BaseActivity.kt:275` 统一分发，可配置倍数） | — | 不搬；我方是全局方案，对方是控件级复制三份 |
| 启动/更新 | 删掉检查更新/公告/在线服务 | （对对方）极简 | `2ec3fb8`，删 `UpdateInfoActivity`(233 行)/`UpdateInstallActivity`(138 行)/`AnnouncementsActivity`/`SponsorActivity` | **我方保留且是优势**（`docs/FEATURES.md:839`、第 18/20 章） | — | 见 §6 |

## 5. 优化点（共有模块里对方更好的实现）

### 5.1 【推荐】`FileUtil.readString` 未关闭文件流（对方修了，我方没有）

【事实】对方 `5267f8e` 把 `readString` 改成 try-with-resources：

```java
// [theirs] util/FileUtil.java:60-62
try (FileInputStream inputStream = new FileInputStream(file)) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
```

【事实】我方同一函数（`[ours] util/FileUtil.java:192-210`）**仍是手开流、不关闭**：

```java
// [ours] util/FileUtil.java:192-208（节选：保留签名与开流两行，省略 buffer 循环）
public static String readString(File file) {
    ...
    try {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        FileInputStream inputStream = new FileInputStream(file);   // 没有 close / 没有 try-with-resources
        FileChannel channel = inputStream.getChannel();
```

【判断】**值得搬，S 工作量、零风险、收益真实**：`readString` 是配置/主题/JSON 的通用读取入口，热路径上反复调用会累积 FileDescriptor；minSdk 19 的老手表 fd 上限本来就紧。【推测】实际线上是否已因此崩溃未见证据，所以按「预防性修复」定性，不夸大成 P0。

同提交的另一半（删除目录时逐条 `Log.e(file)`）对我方**不适用**：我方 `deleteFolder` 已改为统一走 `deleteFolderRecursively`（`[ours] util/FileUtil.java:56-68` 的 `DELETE_MAX_DEPTH` 与注释），没有逐条日志。

### 5.2 【不整体搬】Gson 类型化反序列化，替代手工 `JSONObject`

【事实】对方最大的一次改动 `c87e2ce` 把 30 个 `api/*.java` 从手拆 `JSONObject/JSONArray` 改成 Gson 反序列化（`api/DynamicApi` −1137 行区间的重写、新增 `util/GsonUtil.java` 133 行、`model/*` 加字段），提交信息自述动机是「原版大量使用手拆 json 导致接口稍微变动就崩整个页面」。之后 `415edc6` 又引入 `api/FavoriteContract.java`（266 行）+ 361 行契约单测，把「解析契约」单独测试。

【判断】**不整体搬**：我方 26.09.11 已把 kotlinx-serialization / Retrofit 全部移除，`api/` 用静态方法 + `org.json` 是**刻意的架构决定**（`AGENTS.md`「没有 DI / Retrofit / ViewModel」），整体切 Gson 等于重写 API 层，且与「不轻易引入新第三方库」冲突。**但有两点可借**：

1. **契约层单测**的写法（`FavoriteContractTest` 361 行断言异常 JSON / 缺字段 / 类型漂移），我方 `api/` 测试已有不少（`HotSearchApiTest`、`DynamicApiTest`、`ReplyApiTest`…），可对照补「畸形 JSON 不崩」的用例；
2. `GsonUtil` 那层「解析失败返回 null 而不是抛」的兜底姿态，我方 `JsonUtil` 已有等价物。

### 5.3 【可借鉴】纯函数 + JVM 单测的分层姿势

【事实】对方把染色判定抽成不 import android 的纯函数类 `theme/TintRules.java`（`:3-7` 注释明写「只依赖 ARGB 整数的纯函数集合，不 import android，可靠 JVM 单测覆盖」），并配 3 个测试：`TintRulesTest`、`SemanticTokensTest`、`TintViewBranchOrderTest`（连「分支顺序」这种易回归点都单测）。

【判断】这正是我方 `ui/appearance` 已在用的姿势（`[ours] app/src/test/.../ui/appearance/` 下已有 `AmbientGlowTest`、`AppearanceManagerTest`、`ColorSchemeTest`、`CornerStyleTest`、`FontStyleTest`、`RoundListEffectMathTest` 6 个），**不必搬代码，但值得学它的测试命名粒度**：把「哪个规则先命中」单独钉住（我方 `ColorSchemeTest` 目前偏「getter 一致 / 缓存失效」）。

### 5.4 【不适用】一堆面向 API 14–18 的兼容补丁

| 对方提交 | 修什么 | 我方为何不适用 |
| --- | --- | --- |
| `3512984` | Android ≤4.2 不支持完整 WebP → 默认请求 JPG | 我方 minSdk 19（4.4），且已有图片请求策略（`[ours] util/ImageRequestPolicy.kt:53-78`） |
| `5c480df` | Android 5.0 以下 `MediaSession` 字段导致 `PlayerActivity` 类加载崩溃 | 我方用 `android.media.session.MediaSession`（API 21+，`[ours] PlayerActivity.kt:21`）——**需注意**：我方 minSdk 19 却直接 import 该类，属同一类风险的**潜在镜像问题**，见 §9 风险提醒 |
| `9c9fea0` | Android 4.0 硬件加速 + `clipPath` 崩溃 | 我方最低 4.4，`clipPath` 行为已稳 |
| `9d28c3c` | 补 `drawable-v23/splash_text.xml` 等旧版本资源 | 我方 minSdk 19，不存在 4.0–4.2 资源选择问题 |
| `33090d9` | 移除 `Map.of/List.of/String.join/Objects` 等高版本 API | 我方 `compileOptions Java 17` + minSdk 19，同类问题要靠 AGP desugaring 与 lint 兜底；该提交**提醒**我们：`Map.of` 这类在 minSdk 19 上会 `NoSuchMethodError`，值得在 CI 里对 `api/` 新代码做一次静态排查 |

### 5.5 【不搬，但要记】我方明显更强的共有面（避免误判为「对岸优化」）

- 表冠/旋钮：我方全局 `RotaryEncoderSupport` vs 对方 3 份控件级 `RotaryScrollView`/`RotaryRecyclerView`/`RotaryNestedScrollView`（`ui/widget/Rotary*View.java:46` 三处同构代码）。
- 播放器：我方 `destroyed` 守卫体系（`PlayerActivity.kt:131/234/388/1698/1706`）已覆盖对方 `470047f` 想解决的问题面。
- 收藏/订阅：见 §4 表。
- 深色/浅色：我方资源限定符方案零运行时成本，对方全树染色每次 `onContentChanged` 都要走一遍树。

## 6. 对方砍掉的东西：对我们是风险还是借鉴

【事实】`2ec3fb8`（2026-06-05，根提交后第 2 个提交）一次性删掉了：`activity/update/UpdateInfoActivity`（233 行）、`activity/update/UpdateInstallActivity`（138 行）、`activity/settings/AnnouncementsActivity`（41 行）、`activity/settings/SponsorActivity`（63 行）、`AppInfoApi` 196 行、两个 update 布局（322 行），并「删除了硬控倒计时」（`readme.md:23` 的「不会引入任何非 B 站官方在线服务」即此事的自我说明）。

分三类判断：

1. **检查更新 / 更新安装 UI：对我们是风险提示，不是借鉴。** 手表生态没有应用商店自动更新，我方「自更新 + Release 直链 + `ApkVerifier`（sha256）」（`docs/FEATURES.md:839`、`util/UpdateManager.kt`、`util/ApkVerifier.kt`）是**核心分发能力**。对方删掉它的代价是用户永远停在装的版本——这是路线选择，不是优化。可借鉴的只有一点：**在线服务的失败路径必须不阻塞启动**。我方已是「可关 + 非强制 + 一版一次自动打开」，达标。
2. **公告 / 赞助页：可借鉴「不引入第三方在线服务」的克制，但不该砍。** 我方公告/反馈/赞助都在自有后端（`rebili-console`），不依赖第三方 BaaS；对方的理由是「非 B 站官方服务一律不要」，我方与它立场不同（我方有自建运营面）。
3. **硬控倒计时：纯正面，值得作为我们的对照检查项。** 【事实】我方 `SplashActivity.kt` 的启动动画是打字机 + `postDelayed`（`:56`、`:259`、`:274`），**没有强制等待倒计时**，用户可立即跳过。这一项我们不欠账；报告记下来是为了将来做启动页重设计时别加回去。

## 7. 推荐移植 Top 8（按「用户价值 / 实现成本」排序）

> 全部以「不引入新第三方库」为前提；MCU 按 vendored 源码子集处理（Apache-2.0，与 GPL-3.0 兼容性需在 `NOTICE`/README 记录来源）。
> 单测均落在 `[ours] app/src/test/java/com/RobinNotBad/BiliClient/`（JVM，`testStandardDebugUnitTest` / `testCoexistDebugUnitTest`）。

### T1. `FileUtil.readString` 关闭文件流（S，零依赖）

- 来源：`[theirs] util/FileUtil.java:60-73`（提交 `5267f8e`）
- 我方改动：`[ours] app/src/main/java/com/RobinNotBad/BiliClient/util/FileUtil.java:192-210`，换 try-with-resources（保持原返回语义与异常吞掉策略）。
- 单测要点：新建临时文件 → `readString` 返回内容一致；**Windows 上不关闭的流会阻止 `File.delete()`**，故断言「读完后立刻 `delete()` 成功」即可在纯 JVM 捕获回退；异常路径（不存在/不可读）返回 null。
- 风险：极低；注意别顺手把 `outputStream.toString()` 改成显式 UTF-8（会改变既有行为，单独评估）。

### T2. 播放器进度条按封面取色（M，需 MCU 子集）

- 来源：`[theirs] theme/ContentTintHelper.java:60-126` + `theme/ColorExtractor.java:20-61` + `[theirs] PlayerActivity.java:1747`
- 我方改动：新增 `[ours] app/src/main/java/com/RobinNotBad/BiliClient/ui/appearance/ContentTint.kt`（纯计算 + LruCache）+ 在 `[ours] activity/player/PlayerActivity.kt`（`seekbar_progress` 声明在 `:193`，高能加载在 `:4369`）与 `[ours] ui/widget/HighEnergyProgressBar.kt` 接一次 `setTint`。
- 依赖：MCU **只需** `hct/`、`quantize/`、`score/`、`palettes/`、`blend/`、`utils/`（这些**不含** `java.util.function`，minSdk 19 直接可用），不要带 `dynamiccolor/`+`scheme/`。
- 单测要点：给定固定 ARGB 像素数组 → 断言提取的种子色色相落在预期区间、饱和度下限；配色派生 `Hct.from(hue,chroma,tone)×4` 断言 tone 单调；缓存命中不重复计算；`bitmap==null` / 解码失败返回 0 而不是抛。
- 风险：手表上多一次 64px 解码 + 一次量化（`QuantizerCelebi` 128 色，kmeans 有 CPU 成本）→ 必须放到 `CenterThreadPool` 且带 8s 超时（照抄对方的超时+`recycle`）；另外我方 `player_high_energy` 默认关（`PlayerActivity.kt:4370`），高能曲线不是主要收益面，**收益主要在进度条**。

### T3. 主题背景图（S–M，零新依赖）

- 来源：`[theirs] theme/ThemeApplier.java:66-133`（窗口背景 + scrim + fit）、`:206-268`（解码/缓存）、`ThemeManager.java:323-345`
- 我方改动：新增 `[ours] ui/appearance/BackgroundImage.kt`（读取 + 采样解码 + `SoftReference` 缓存 + fit 计算纯函数）；在 `[ours] ui/appearance/ColorScheme.kt:680 applyWindowTheme()` 里按开关改用 `setBackgroundDrawable(...)`；设置项加到 `[ours] activity/settings/SettingGroupActivity.kt:347 buildAppearanceGroup()`（走 `AppearanceManager.setXxx()` 递增版本号，见 `AppearanceManager.kt:88`）。
- 单测要点：`centerCrop`/`centerInside`/`tile` 的目标矩形计算（纯函数，边界：图小于屏、宽高比极端）；`scrim` alpha 合成；未启用时 `applyWindowTheme` 行为与现在**逐像素等价**（用现有 `AppearanceManagerTest` 的 fake prefs 断言）。
- 风险：与「弥散光晕」同开时的层级（我方 `ColorScheme.kt:708-716` 已有一套「光晕画窗口背景 / content 不铺纯色」的约定，必须先读再改）；圆屏边缘裁切；大图 OOM（照抄 ≤2× 屏幕 + `RGB_565`）。

### T4. 种子色 → 色板生成，替换 7 张写死表（M，需 vendored MCU 全量 + 1 处 Java 8 改写）

- 来源：`[theirs] theme/SchemeEngine.java:21-94` + `material-color-utilities` 全模块（`applyOverrides` 在 `:94`）
- 我方改动：`[ours] ui/appearance/ColorScheme.kt`（保留 7 个 `ThemeColors` 类型作为「预设名」，改为由种子生成的实例；`darkTableOf`(`:602`)/`LIGHT_PALETTES`(`:454`)/`getCurrentTheme` 缓存逻辑不动）；新增 `[ours] ui/appearance/SchemeEngine.kt`（36 字段 ↔ M3 令牌映射）。
- 依赖：vendor `material-color-utilities`（新 module 或 `app/src/main/java` 下复制包名 `com.google.material.color`）；**必须**把 `dynamiccolor/DynamicColor.java` 的 `java.util.function.Function` 改成自建接口（否则 minSdk 19 需 `coreLibraryDesugaring`）。
- 单测要点：对 7 个种子 × 深/浅两套断言**可读性**（`LINK` 对 `BACKGROUND` ≥4.5:1、`TEXT_PRIMARY` 对 `BACKGROUND` ≥7:1、`PRIMARY` 对 `ON_PRIMARY` ≥4.5:1），用 `androidx.core.graphics.ColorUtils.calculateContrast`（注意：JVM 单测里 `androidx.core` 的 `ColorUtils` 依赖 `Color` 类，需用 `calculateContrast` 的纯数学替身或直接手写相对亮度公式，参考我方现有 `ColorSchemeTest` 的做法）；断言非法 `seed`/`contrast` 越界回落到默认。
- 风险：**这是唯一会「看起来没坏但处处偏色」的改动**——我方主题同时被 `?attr/`（78 处）与 `ColorScheme` getter 消费，生成色只能进后者，二者不同步就是花屏级体验问题。建议按路线 B 只在「代码侧颜色」生效，并在 `docs/FEATURES.md` 第 17 章明确「内置 7 套仍是编译期固定色」。

### T5. `.btheme` 导入容器（M，依赖 T4 的色板才能有意义）

- 来源：`[theirs] theme/BThemeInstaller.java:97-222`、`theme/model/ThemeManifest.java:10-73`、`docs/sample-theme/README.md:1-25`
- 我方改动：新增 `[ours] theme/` 包（Kotlin）：`BThemeInstaller.kt`（白名单 + 限额 + `format`/`id` 校验 + 临时目录原子替换）、`ThemeManifest.kt`、`InstalledTheme.kt`；存储落在 `filesDir/themes/<id>/`；入口挂到 T6 的页面或设置分组。
- 依赖：ZIP 用 `java.util.zip`（无新库）；JSON 解析用我方 `JsonUtil`/`org.json`（**不要引入 Gson**，与架构决定冲突；`override` 字段手工解析成 `Map<String,String>` 即可）。
- 单测要点（**这是本项最重要的部分**）：用 `ZipOutputStream` 在测试里现造 5 个负样例并断言错误码——`../evil.png` 条目、整包 >8MB、单图 >6MB、`format:99`、缺 `theme.json`；再断言正样例安装后目录结构正确、重复导入幂等、非法 `id`（`../`、`a/b`、超 64 字符）被拒。
- 风险：ZIP 炸弹 / 路径穿越（必须照抄白名单式解压，**不要**先解压再校验）；`getFilesDir` 空间不足时的原子替换失败要回滚；`.btheme` 没有签名，第三方主题等于可执行资源注入面（只解图与 JSON，禁止任何代码/`dex`/`xml` 条目）。
- 差异化机会：对方**没有导出**能力。我方若顺手加一个「把当前主题导出成 `.btheme`」（`ZipOutputStream` 写 `theme.json` + 可选背景图），成本极低（S），是对方没有的功能。

### T6. 主题设置页（S–M，依赖 T5）

- 来源：`[theirs] activity/settings/ThemeSettingsActivity` + `res/layout/activity_theme_settings.xml` + `adapter/ThemeListAdapter`
- 我方改动：新增 `[ours] activity/settings/ThemeStoreActivity.kt`（沿用我方 `BaseActivity` + `RecyclerView` + `SettingSection` 风格，`strings.xml` 收文案）；入口加到 `[ours] SettingGroupActivity.kt:347` 的外观分组；列表项显示 `preview.png`（Glide 72×72）、点击切换、3 秒内长按两次删除（沿用我方「再点一次确认」惯例）。
- 单测要点：列表行组装（默认/预设/已装去重、选中态标记）纯函数化后可 JVM 测；收藏夹管理页已有「二次确认删除」的可复用模式。
- 风险：圆屏列表可交互性（我方已有 `RoundListEffect` 经验，`ui/appearance/RoundListEffect.kt`；注意别与它冲突）；`API<19` 无选文件能力——我方 minSdk 19，正好在边界上，保留「手输路径」旁路即可（对方 `:119` 同款处理）。

### T7. 程序化颜色 pass（L，不建议近期做）

- 来源：`[theirs] theme/ThemeApplier.java`（784 行，85 处调用点 / 18 个文件）+ `theme/ThemeCompat.java` + `theme/TintRules.java`
- 我方落点：`[ours] ui/appearance/AppearanceApplier.kt:39`（已有遍历与 `hookedGroups` 去重）+ `[ours] BaseActivity.kt:597`（`onContentChanged` 唯一入口）+ `BaseActivity.kt:560`（版本比对，天然给出「何时重染」）。
- 判断：**先做 T4 + T5，只有当「自定义主题的颜色必须覆盖 XML 控件」被确认是硬需求时才做**。理由是它把「颜色正确性」从编译期保证降级成运行时启发式（对方自己也在 `TintRules` 里靠「等于历史默认值才染」的保守规则躲误伤，`TintRules.java:12-17` 写死了 3 个默认值常量——这类规则一旦我方资源色改动就会整片失准）。
- 若做，必须先抽纯函数层再写视图层：单测覆盖 `TintRules` 等价判定（用我方 `ThemeColors` 36 字段，而不是对方的 18 个），视图层只留「遍历 + 应用」不留判断。

### T8. 测试姿势对齐：把「分支顺序」钉住（S）

- 来源：`[theirs] app/src/test/.../theme/TintViewBranchOrderTest.java`、`SemanticTokensTest.java`
- 我方改动：扩 `[ours] app/src/test/.../ui/appearance/ColorSchemeTest.kt`：对 7 套主题 × 深/浅断言 `ThemeTint.setActiveIcon` 走的分支（激活色 vs 未激活色）、`LINK` 与 `INFO` 的语义不串、亮色表覆盖字段完整（`LightPalette` 已列字段 vs `LightColors` 构造参数，防止将来加字段漏映射——这类「漏一个字段」的回归现在没有任何测试能拦）。
- 风险：无（纯测试）。

## 8. 不建议移植 / 容易误判的点

1. **`exported="true"` 22 处**（对方清单）——不要因为「对方这么做也能上架」而放松我方 2 处的红线。
2. **`testkey.jks` 兜底签名**（`[theirs] app/build.gradle:63-66`）——反面教材，我方 CI Secrets + `verifyVersionConsistency` 是正确形态。
3. **全量 Gson 迁移**（§5.2）——与「无 Retrofit/无序列化框架」的架构决定冲突，收益不足以抵消。
4. **API 14–18 兼容补丁**（§5.4）——我方 minSdk 19，绝大多数不适用；唯一有镜像价值的是 `MediaSession`（见 §9 风险 1）。
5. **收藏 v3 / 评论带图 / 播放器释放顺序 / 表冠**——**我方已经更强**，这四条是最容易被「对岸有个提交」误导而重复造轮子的地方，已在 §4/§5.5 逐条给证据。
6. **对方 `ThemePalette` 的 18 令牌模型**——不要用它替换我方 36 字段 `ThemeColors`，那会丢 `LIKE/COIN/FAV/SHARE`、`PLAYER_*`、`CORNER_RADIUS` 等本项目已依赖的语义。
7. **对方 readme 不写新功能**（`grep 主题|theme` 在 `[theirs] readme.md` **0 命中**，主题系统只在 `docs/sample-theme/README.md` 里有说明）——这是文档反面教材：一个 2300 行的新子系统，用户侧零说明。我方新增外观能力时必须同步 `docs/FEATURES.md` 第 17 章。

## 9. 风险提醒（对方给我们的预警，不涉及代码移植）

1. **`MediaSession` 与 minSdk 19 的类加载风险（值得单独开一条 issue 核实）。** 【事实】对方为 API 14 做了专门修复（`5c480df`：「修复 Android 5.0 以下 MediaSession 字段导致 PlayerActivity 类加载崩溃」）；【事实】我方 `PlayerActivity.kt:21` 直接 `import android.media.session.MediaSession`，而 `MediaSession` 是 **API 21** 类，我方 minSdk 19。【推测】若 ART 在验证 `PlayerActivity` 时解析该类（老版 Dalvik/ART 对未执行分支的类型引用处理不同），可能出现与对方同款 `NoClassDefFoundError` 类加载崩溃；我方目前没有「Android 4.4 真机/模拟器播放页」的实测记录（`docs/review/fix-progress.md` 未见）。**建议**：在 API 19 模拟器上跑一次播放页冒烟，或把 `MediaSession` 访问整体收进一个 `@RequiresApi(21)` 的内部类（与对方 `CompatUtil`/`callMethodIfExists` 的隔离思路同源）。
2. **在线服务不可用时的降级**：对方靠「全删」规避（§6），我方靠「可关 + 非强制」；建议顺手复核一遍「弱网/服务端 5xx 时启动不卡住」这条链路（`UpdateManager` / 未读检查），这是本次对比里唯一对我方有现实风险的运营面问题。
3. **第三方主题包是新的攻击面**：若采纳 T5，必须坚持「白名单条目 + 单条目限额 + 只解图与 JSON + 不解析任何 `xml`/`dex`」，并给安装器补 §7 T5 的 5 条负样例单测。

## 10. 结论

- **对方的定位值得尊重但不必追随**：它把「老设备（4.0）+ 纯 Java + 不改 UI」做到极致，代价是自己止步于 2.9.8 的功能面，且用「程序化染色 + 生成色板」换掉了我方的资源化主题体系。
- **对我方的真实价值只有两块**：① 主题引擎里的「种子色 → 色板」「背景图」「封面取色」三个切片，可以用 S–M 成本接进我方 `ui/appearance`（我方已有 `AppearanceApplier` 遍历与版本号机制，是现成地基）；② `FileUtil.readString` 的流泄漏修复（S，唯一的「直接抄就对」项）。
- **不要被提交数量误导**：60 个提交里 30+ 个是 API 14–18 兼容与 Gson 迁移，`2ec3fb8` 还砍掉了在线服务；真正成体系的增量只有一个主题引擎，且它在我们已知的收藏/评论/播放器/表冠四个域都**弱于**我方。【事实】
- **下一步建议**：按 T1 → T2 → T3 → T4 →（T5/T6）顺序推进，T7 除非确认「XML 控件必须跟随自定义主题」否则不做；并单独核实 §9 风险 1 的 `MediaSession` 兼容问题（这一条与本报告主线无关，但可能比整份报告里任何移植项都更紧急）。

---

### 附录 A：本报告引用的对方文件（便于复核）

`readme.md`、`LICENSE`、`app/build.gradle`、`settings.gradle`、`AndroidManifest.xml`、`docs/sample-theme/{README.md,make_samples.py,aurora.btheme}`、`theme/{ThemeManager,ThemeApplier,SchemeEngine,ThemePalette,ThemeCompat,TintRules,BThemeInstaller,ColorExtractor,ContentTintHelper}.java`、`theme/model/{ThemeManifest,InstalledTheme}.java`、`activity/settings/{ThemeSettingsActivity,SettingMainActivity}.java`、`adapter/ThemeListAdapter.java`、`material-color-utilities/build.gradle`、`api/{ReplyApi,FavoriteContract}.java`、`model/FavoriteSelectionState.java`、`util/FileUtil.java`、`activity/player/PlayerActivity.java`、`res/values/{themes,colors,strings}.xml`、`res/layout/activity_theme_settings.xml`、`app/src/test/.../{TintRulesTest,SemanticTokensTest,TintViewBranchOrderTest}.java`、`.github/workflows/build-release.yml`。

### 附录 B：本报告引用的我方文件

`app/build.gradle`、`app/src/main/java/com/RobinNotBad/BiliClient/{util/FileUtil.java,activity/base/BaseActivity.kt,activity/settings/SettingGroupActivity.kt,ui/appearance/*.kt,activity/player/PlayerActivity.kt,activity/reply/WriteReplyActivity.kt,ui/widget/RotaryEncoderSupport.kt,util/ImageRequestPolicy.kt}`、`app/src/test/java/com/RobinNotBad/BiliClient/ui/appearance/*`、`docs/{FEATURES.md,architecture-map.md}`、`app/src/main/res/values/{themes,colors}.xml`。

### 附录 C：核验记录

- 差异清单统计：`only-fork` 298 / `only-ours` 1032 / `modified` 433（逐文件计数）。
- basename 去重：`only-fork` 中 `app/src/main/java` 202 条 → **37 个**无对应物 basename（另有 5 个 `app/src/test` 文件；命令见 §1.3 口径，`GetFileNameWithoutExtension` 双向比对）。
- 行号复核：本报告全部 `文件:行` 均在成文前用 `Select-String` / 按行号取行复核过；`ThemeApplier`、`BThemeInstaller`、`SchemeEngine`、`ColorScheme.kt`、`FileUtil.java` 的行号为逐条打印确认，不是估算。
- 未复核项：对方运行时行为（未构建/未装机）、`MediaSession` 在 API 19 的实际崩溃表现（标【推测】）。
