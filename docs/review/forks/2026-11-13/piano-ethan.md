# BiliTerminal-PianoEthan 功能差距清单

> 分析对象：`PianoEthan/BiliTerminal`（本地克隆 `fork-analysis/BiliTerminal-PianoEthan`，origin/main HEAD `3a6083e`，2026-11-07，版本 `3.1.3-Qx`）
> 对比基准：我方 `ReBiliClient`（RE:哔哩终端 Kotlin 重构线，HEAD `965e9781`，版本 `26.11.13`）
> 标注约定：`[sib] 相对路径:行号` = 兄弟库证据；`[ours] 相对路径:行号` = 我方证据；【事实】= 已回代码核实；【推测】= 未逐行验证。
> 范围：只做「功能性差距 + 功能优化」，不做安全/规范审计。

---

## 0. 基线

| 项 | 兄弟库 BiliTerminal-PianoEthan | 我方 ReBiliClient |
| --- | --- | --- |
| 版本 / HEAD | `3.1.3-Qx` / `3a6083e`（2026-11-07） | `26.11.13` / `965e9781` |
| 语言 / 栈 | 纯 Java 319 个文件，XML 布局 + 手写 Adapter | Kotlin 424 + Java 145 = 569 个文件，XML + 手写 Kotlin/Java |
| activity 文件数 | 109 | 166 |
| SDK | compile 33 / min **14** / target 26 | compile 34 / min **19** / target 34 |
| 密钥依赖 | `[sib] app/build.gradle:132-135` 本地模块：ijkplayer-java、DanmakuFlameMaster、brotlij、**material-color-utilities** | 无 material-color-utilities；playerdeps 走已提交 `app/libs/*.so` |
| 分支来源 | 【事实】`[sib] 86280ff` = "Initialize project: BiliClient 2.9.8 with DynamicApi fix"（2026-06-05）；全库仅 **61 个 commit** | 上游 26.x 延续线，功能面远超 2.9.8 |

### 0.1 结论先行

1. **兄弟库不是"更全的分支"，而是"更年轻的分叉 + 一处深挖"。** 【事实】对两侧源码树做全量文件名比对，兄弟库**独有文件只有 43 个**，其中 12 个集中在新建的 `theme/` 包，其余是设置页、教程、Adapter/基类、`util` 工具与几个 Model；动态/视频/收藏/直播/搜索/下载这些业务域**没有任何兄弟库独有的实现文件**。
2. **兄弟库对我方唯一的实质功能增量 = 运行时主题系统（Material You 动态配色 + `.btheme` 主题包 + 封面取色）。** 我方 `app/src/main/java/com/RobinNotBad/BiliClient/theme/` 目录不存在，配色是**手写 XML 静态风**（`[ours] app/src/main/res/values/themes.xml` 共 86 个 `<style>`，= 6 套主题 × 7 组控件样式）。
3. **反方向差距更大**：兄弟库多数"上游功能"（收藏 v3、评论标准分页、隐私模式、评论带图、个人资料编辑、界面缩放/圆屏/跑马灯/开屏文字）**我方都已具备**，部分实现更完善（见 §2、§3）。
4. 所以本次移植窗口很窄：**值得拿的是"外观"这一块的做法，而不是"功能"本身**。

### 0.2 核实方法（可复核）

- 【事实】`Compare-Object` 全量比对两侧 `app/src/main/java` 下所有 `.java`/`.kt` 相对路径 → 兄弟库独有 43 个文件（清单见 §1 各条 / §3）。
- 【事实】`git log --oneline -61` 逐条读 message + 逐条看 `--stat` 与关键 diff；对 8 个提交做了整体 diff 阅读（主题层 6 个 fix、直播修复、私信 PR、评论图 PR、低版本兼容）。
- 【事实】抽查回代码核实自我宣称（不只信 readme/commit message）：
  - `readme.md:9-11,22-23` 自述"基于 2.9.8、不加莫名其妙功能、不大改 UI" → 与 `git log` 首提交一致 ✅；
  - `e441330` "THEME SYSTEM(almost done)" 的 "almost" 回代码核实：主题在 `[sib] activity/base/BaseActivity.java:107`（`setupWindow`）+ `:113-127`（三个 `setContentView` 重载全部追加 `ThemeApplier.applyContent`）挂进基类，全库 `ThemeApplier.|ThemeManager.` 调用点 **156 处**、覆盖 18 个文件（含 Adapter/Holder/Fragment/PlayerActivity）→ 不是半成品演示 ✅；
  - `470047f` "直播弹幕回调切主线程" 的 diff 落在 `PlayerActivity.java` 而非 `LivePlayerActivity.java`（兄弟库无独立直播播放页）→ message 与代码一致，但**影响面只覆盖他们自己的播放器结构**。

---

## 1. 对方有、我方没有的功能

### 1.1 设置 · 外观 · 可访问性（唯一大块：运行时主题系统）

> 我方现状总述：我方外观是 `ui/appearance/` 下 13 个模块（`AppearanceManager`/`ColorScheme`/`AppearanceApplier`/`ColorMode`/`CornerStyle`/`FontStyle`/`GlassStyle`/`RoundListEffect`/`SegmentedBarStyle`/`ThemeTint`/`AmbientGlow`/`BackgroundImage`），**令牌取自 6 套手写主题的 XML**；`[ours] .../ui/appearance/ColorScheme.kt:57-93` 定义约 40 个令牌（含 `PLAYER_*`、`LIKE_COLOR`、`STATUS_BAR_COLOR`、`CORNER_RADIUS`），但**没有任何"从种子色生成整板颜色"的运行时引擎，也没有第三方配色库**。兄弟库这一块是"引擎 + 包格式 + 渲染层"三层，我方三层都没有对应物。

#### F1. Material You 动态配色引擎（种子色 → 整板令牌）

| 项 | 内容 |
| --- | --- |
| 功能名 | 运行时用 M3 `Hct`/`SchemeContent` 从一个种子色生成完整的浅色/深色两套色板 |
| 对方实现 | `[sib] app/src/main/java/com/RobinNotBad/BiliClient/theme/SchemeEngine.java:21-78`：`Hct.fromInt(seed)` → `new SchemeContent(seedHct, dark, contrast)` → 派生 `textPrimary=onSurface`、`surfaceCard=surfaceContainer@0xCC`、`accent=primary`、`accentLow=tertiary@0x99`、`gray=outline@0x70`；`[sib] theme/ThemeManager.java:259-273` `computePalette(boolean)` 深浅两套**各自独立可算**，`:279-283` `getPaletteDark()` 供播放器/闪屏这类永久深色场景单取深色板 |
| 行为 | 用户在主题页换一个种子色（或换背景图自动提取种子），全 App 令牌整体重算；内置两个预设 `[sib] theme/ThemeManager.java:40-50` `PRESET_SUMMER_GREEN`=`0xFF00C853`「夏天绿」/`PRESET_ICE_BLUE`=`0xFF4FC3F7`「冰雪蓝」 |
| 我方现状 | **缺**。`[ours] app/src/main/res/values/themes.xml` 是 6 套固定主题（ClassicGray / ClassicTerminal / IQIYIGreen / PurpleFantasy / RainbowFantasy / ZhihuBlue）× Light/Dark 的静态 XML；选择入口在 `[ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:414-449`「外观设置 → 配色」，是一个 7 选 1 的单选（默认 B 站粉 + 上述 6 套），**只能选、不能自定义颜色**；`[ours] .../ui/appearance/ColorScheme.kt` 只做"按主题名取色表 + 缓存" |
| 成本 | 中（1–3 天）：引入 `material-color-utilities`（纯 Java 库，无 Android 依赖）≈ 半天；`SchemeEngine` 翻成 Kotlin ≈ 半天；剩下是把我方 40 个令牌映射到 M3 语义（`SURFACE/CARD/PLAYER_*` 与 M3 的 `surfaceContainer*` 不是一对一） |
| 冲突点 | 我方现有 6 套主题的**全部色值硬编码在 XML**（86 个 style + 各 layout 里的 `#RRGGBB`），token 化后必须同时改 XML 与 `ColorScheme`，否则出现"一半跟主题、一半不跟"的花斑。**这是本项真正的成本，不在引擎本身** |
| 价值 | 高。手表端屏幕小、主题审美差异大，"一套配色用到底"是常见抱怨；动态配色能让每次换背景图/换种子色都得到协调配色，比再手写第 7、8 套主题划算 |

#### F2. 中性色"染色强度"（blend）与对比度（contrast）

| 项 | 内容 |
| --- | --- |
| 功能名 | 用 0–100 的 blend 把中性色（背景/卡片/分隔线）按比例向强调色染色；contrast −1..1 走 M3 对比度 |
| 对方实现 | `[sib] theme/SchemeEngine.java:21-78` 里 `TonalPalette.fromHueAndChroma(seedHue, seedChroma * blend / 100)` 重造 dynamic scheme；blend 默认 35（`[sib] theme/ThemeManager.java` 状态字段），主题包可写 `colors.blend`（-1 表示沿用用户设置） |
| 行为 | blend=0 得到纯灰阶（"黑白极简"），blend=100 得到整屏同色系（"糖果色"）；同一份 UI 靠一个滑杆覆盖两类审美 |
| 我方现状 | **缺**。我方 `ColorMode`（`[ours] .../ui/appearance/ColorMode.kt`，56 行）只有 亮/暗/跟随 三态，中性色由主题 XML 定死 |
| 成本 | 小（<半天，依赖 F1 先落地） |
| 冲突点 | 无（纯计算） |
| 价值 | 中高。手表端用户群对"灰阶省电/护眼"与"彩色好看"两极分化，一个滑杆比 6 个固定主题更能覆盖 |

#### F3. 功能色的"只换色相、不动明度"修正（浅色模式对比度补丁）

| 项 | 内容 |
| --- | --- |
| 功能名 | 语义功能色（成功/警告/危险/链接/选中）只 `Blend.harmonize` 色相，明度按明暗模式各自取档；浅色模式强制 tone=40 |
| 对方实现 | `[sib] theme/SchemeEngine.java:85-92` `functionalTone()` + `:80-84` 注释记录的**历史 bug**：早期直接套深色档字面量，浅色白底上对比度只剩 1.2–1.8（"等于看不见"），修法是用常量 `LIGHT_FUNCTIONAL_TONE = 40.0` |
| 行为 | 换主题不会让"投币成功/已失效/超链接"这类颜色变得看不清 |
| 我方现状 | **缺**（我方功能色写死在每套主题 XML 里，靠人肉挑色，因此不存在"动态生成后看不清"的问题；但也因此无法动态化） |
| 成本 | 小（<半天，属 F1 的子模块） |
| 冲突点 | 无 |
| 价值 | 中。**若移植 F1 必须连它一起移植**，否则动态主题在浅色下必然出现低对比文字——这条是"照抄别人做法会踩的坑"清单里最有价值的一条 |

#### F4. 令牌级覆盖表（theme.json 19 个键）

| 项 | 内容 |
| --- | --- |
| 功能名 | 主题包可以逐键覆盖 19 个令牌：`textPrimary/textSecondary/textTransparent/ripple/windowBackground/accent/gray/accentLow/success/link/surfaceCard/snackBg/highEnergyLine/highEnergyFill/buttonTint/selectedText/danger/selected` 等 |
| 对方实现 | `[sib] theme/SchemeEngine.java:94-142` `applyOverrides()`；键解析在 `[sib] theme/model/ThemeManifest.java`（`parseColor()` 支持 `#abc`/`#rrggbb`/`#aarrggbb`，坏值返回 `null` 不崩） |
| 行为 | 主题作者可以让"生成色板 + 少量手工指定"混合——例如只钉住品牌粉 `#FB7299`，其余交给算法 |
| 我方现状 | **缺**（我方"覆盖"就是"整套手写"） |
| 成本 | 小（<半天，F1 的附属） |
| 冲突点 | 我方令牌名与兄弟库不同名（我方 `PLAYER_PROGRESS_FILL` 对应其 `highEnergyFill`…），需要一张映射表 |
| 价值 | 中。它把"再也不能自定义"和"全部手写"之间的空档补上了 |

#### F5. `.btheme` 主题包格式 + 白名单安装器

| 项 | 内容 |
| --- | --- |
| 功能名 | 一个 zip（后缀 `.btheme`）就是一套主题：`theme.json` + 预览图 + 背景图（深浅两套可选） |
| 对方实现 | `[sib] theme/BThemeInstaller.java:26-226`：白名单**只认** `theme.json`/`preview.png`/`background.png`/`background_dark.png`/`background_light.png`（`:34-36`，天然免疫 zip-slip）；上限 `MAX_PACKAGE_BYTES=8MB`、`MAX_ENTRIES=12`、`MAX_THEME_JSON=64KB`、`MAX_PREVIEW=1MB`、`MAX_BACKGROUND=6MB`（`:28-32`）；id 校验 `^[a-zA-Z0-9._-]{1,64}$`（`:64`）；**两遍读 zip**（第一遍只取 `theme.json` 并校验条目数/白名单，第二遍按 cap 逐条落盘）→ 解压到 cache 临时目录 → `deleteFolder(finalDir)` + `renameTo` **原子替换**（`:163-204`）；`uninstall()` 若删的是当前主题，先 `setTheme("")` 回落内置 |
| 行为 | 用户从文件管理器选一个 `.btheme` 即装即用；错误文案面向用户直说（`主题包过大（>8MB）`/`主题包条目过多（>12）`/`主题包包含非法文件`/`主题版本过新，请升级哔哩终端`/`theme.json 缺失或损坏`/`主题 ID 不合法`） |
| 我方现状 | **缺**。我方自定义外观只有"背景图 + 圆角 + 字体 + 光晕开关"（`[ours] .../util/SettingsKeys.kt:47-64`），没有可分发的外观包概念 |
| 成本 | 中（1–3 天）。安装器本身不难（兄弟库 267 行含注释），难在**要先把 F1/F6/F7 做成"可替换"结构**，否则包没东西可换 |
| 冲突点 | 我方主题是"编译期 XML"，主题包要求"运行期换色板"，等于要求 F1 先落地 |
| 价值 | **低（对手表端）**。手表上从文件管理器选 zip、管理已装主题列表都是重交互；兄弟库自己也只能在 API≥19 走系统选择器、更老的系统退回"手输文件路径"（`[sib] activity/settings/ThemeSettingsActivity.java:79-98`）。建议只在"想要外观生态"时才做 |

#### F6. `theme.json` 声明式主题描述（含背景图/裁剪/遮罩）

| 项 | 内容 |
| --- | --- |
| 功能名 | 一套主题的完整声明：格式版本、id、meta（名/作者/版本/描述）、预览、`colors{mode,seed,source{image,fallback},contrast,blend,override}`、`background{fit,scrim,scrimColor,image,image_dark,image_light}` |
| 对方实现 | `[sib] theme/model/ThemeManifest.java:9-83`；背景适配四档 `centerCrop/centerInside/fitXY/tile`，`scrim` 默认 0.55、上限 0.9，`scrimColor` 可指定遮罩色 |
| 行为 | 背景图**跟随主题包**、且深浅各一张（`image_dark`/`image_light`，缺省回落 `image`，见 `[sib] theme/ThemeManager.java:335-344`）；遮罩色/强度也随主题走 |
| 我方现状 | **部分有、弱**：我方已有背景图 + 适配（`[ours] .../ui/appearance/BackgroundImage.kt:54-66` `FIT_COVER/FIT_CONTAIN`、`DEFAULT_SCRIM=50`、`SCRIM_PERCENTS=[0,25,50,75]`），但**只有用户自选的单张图、单档遮罩、遮罩色固定**，没有"深浅双图""按主题切换""fitXY/tile""遮罩色" |
| 成本 | 小（<半天）：在我方 `BackgroundImage` 上加"深/浅两套路径 + tile/fitXY 两档 + 遮罩色"即可，**不依赖 F1** |
| 冲突点 | 我方背景图是全局单一设置；加"深浅双图"要改 `AppearanceManager.Appearance`（`[ours] .../AppearanceManager.kt:49-60` 的快照结构）与设置页 |
| 价值 | 中。手表多为 OLED/AMOLED，深色背景图 + 合适遮罩对观感与耗电都直接影响，且成本极低——**这是本清单里性价比最高的一条** |

#### F7. 主题设置页（列表切换 / 双击删除 / 导入 / 4 开关 + blend 滑杆）

| 项 | 内容 |
| --- | --- |
| 功能名 | 主题选择与管理页 |
| 对方实现 | `[sib] activity/settings/ThemeSettingsActivity.java:31-209`：`refreshList()`（`:142-161`）列「默认（暗黑）」+ 2 内置预设 + 所有已装包；行点击切换、**行长按两次（3 秒内）才删除**（`:54-70`，沿用"再点一次"惯例，适配手表误触）；导入两条路：API≥19 `ACTION_GET_CONTENT`（`:163-172`），更低版本**手输文件路径**（`:79-98`，默认填 `FileUtil.getDownloadPath()`）；4 个开关 `switchDark`/`switchBg`/`switchExtract`（背景取色，需重启）/`switchContentTint`（播放器封面取色）+ `blendInput` 0–100 |
| 行为 | 一个页面容纳"选主题/删主题/装主题/四个行为开关/一个强度滑杆"，用小屏 + 少量控件完成 |
| 我方现状 | **缺**（我方没有"主题包列表"概念，选主题是 `[ours] .../activity/settings/SettingGroupActivity.kt:414-449` 的一个 7 选 1 单选；外观相关能力散在「外观设置」与「布局适配」两个二级页里，`[ours] .../activity/settings/SettingGroupActivity.kt:296-297`）。另外我方 `[ours] .../activity/settings/SettingGroupActivity.kt:410-411` 自述**字体模块尚未接入渲染路径**，所以连"字体"这一项目前也只是半接通 |
| 成本 | 中（1–3 天，且依赖 F5/F6 才有内容可管理；若只做 F1+F2，则只需一个"选种子色/选预设"的极小页面 ≈ 小） |
| 冲突点 | 我方设置页是 `SettingSection` 声明式 DSL（`[ours] .../activity/settings/SettingPrefActivity.kt:74`），主题列表是自定义列表页，得单开 Activity |
| 价值 | 低-中。"双击删除"这个交互惯例**值得抄**（手表误触是真问题），主题包管理本身价值低 |

#### F8. 主题化渲染层：遍历视图树按令牌染色（含单色图标识别 / 列表滚动重绑 / Dialog）

| 项 | 内容 |
| --- | --- |
| 功能名 | 把"静态 XML 里写死的颜色"在运行期统一映射到当前色板，逐控件染色 |
| 对方实现 | `[sib] theme/ThemeApplier.java`（832 行）：`tintView(View, ThemePalette)`（`:343-517`）按控件类型分派；`tintIconIfMonochrome`（`:703-733`）+ `classifyDrawable`（`:734-768`）+ `LruCache<Drawable.ConstantState,int[]>`（`:518`，命中在 `:773-777`）**只染单色图标**、跳过彩色图标（避免把番剧 logo 染灰）；`retintBackground`（`:642`）保留原 drawable 的状态化行为；`tintButton`（`:792`）；`tintDialog`（`:635`）给 Dialog 也染色；`hookRecyclerView`（`:320-342`）挂 `OnChildAttachStateChangeListener` 对**滚动进来/后 inflate 的 item 重新染色**（否则主题在列表里只生效首屏）；`setSemanticText`/`semanticTag`（`:599-615`）用 View tag 记住"这个颜色是语义令牌"，避免二次遍历时把 `selected` 粉错染成 accent |
| 行为 | 换主题后**所有已存在的 Activity 控件**跟着变，而不是只有新布局才跟着变 |
| 我方现状 | **根本不需要**：我方颜色在 XML 里按主题 `style` 静态引用，主题切换走"存 SP + recreate"，控件天然一致；`[ours] .../ui/appearance/AppearanceManager.kt:30-46` 正是为此设计了 `appearance_version` 版本号让 Activity 只比一个 Int |
| 成本 | **大（>3 天）**：832 行核心 + 295 行规则 + 我方 86 个手写 style/大量 layout 内联色值都要清洗；且必须与 `ColorScheme` 的 40 令牌对齐 |
| 冲突点 | 与我方"XML 静态主题 + recreate"路线**直接冲突**。两条路只能选一条：要么继续静态 XML（那就放弃 F1），要么整体转向运行期染色（那就是一次外观层重写） |
| 价值 | 中。它解决的问题（运行期换色）手表端确实需要，但**代价是把我方已经稳定的外观层推倒重来**；建议只在"确定要做动态主题"时才考虑，且优先用一条**更省的替代路线**（见 §4 建议 2） |

#### F9. 染色守卫规则（语义令牌 / hint 透明度 / 近白跳过）

| 项 | 内容 |
| --- | --- |
| 功能名 | 决定"哪些颜色该染、哪些必须原样保留"的规则表 |
| 对方实现 | `[sib] theme/TintRules.java`（295 行）：`DEFAULT_TEXT=0xFFEBE0E2`/`DEFAULT_BUTTON_BG=0xCC262626`/`DEFAULT_BUTTON_BG_ALT=0xDD262626` 作为"历史默认值"守卫；`hintTextColor()` **只换 RGB、保留布局自己写的 alpha**（注释解释了 `styles.xml:27` 的 `#80ffffff` 与 5 处 `#80ffffff`/1 处 `#88ffffff` 的实测差异）；图标采样分四类 `ICON_SKIP/ICON_NEUTRAL/ICON_SATURATED/ICON_GRAYSCALE`；`semanticToken()/semanticColor()` 用 tag 记录语义 |
| 行为 | 换主题后：hint 文字不会突然变实心、品牌图标不会被染成单色、选中态仍是 B 站粉 |
| 我方现状 | **缺**（同样因为静态 XML 无需这套规则） |
| 成本 | 中（1–3 天）——**这 295 行是兄弟库 6 个 fix commit 的沉淀**（`54a527e` 补语义令牌与灰阶层次、`520b12a` 默认深色恢复历史配色、`b106b31` 内容取色收窄到播放器、`a4241e8` 按钮不再吃封面取色、`ef802f1` theme leftover old colors and late-inflated views、`0ff36ce`/`b8d1a33` some place not themed），**照抄实现容易，照抄"该守哪些颜色"难** |
| 冲突点 | 同 F8 |
| 价值 | 中。**若 Lead 决定不移植 F1/F8，这条也有独立价值**：它是一份"我方 86 个 style 里哪些色值不能 token 化"的坑点参考 |

#### F10. 内容取色：按视频封面动态染播放器（进度条 / 高能曲线）

| 项 | 内容 |
| --- | --- |
| 功能名 | 取当前视频封面主色，实时给播放器进度条与高能曲线换色 |
| 对方实现 | `[sib] theme/ContentTintHelper.java:16-127`：封面 URL → Glide `override(64,64)` + `DecodeFormat.PREFER_RGB_565` → `ColorExtractor`（`[sib] theme/ColorExtractor.java`，QuantizerCelebi）取种子 → `Hct` 派生 4 色小色板 `accent/onAccent/container/stroke`（`:98-120`，`chroma = clamp(hct.getChroma()*1.2, 32, 96)`，深/浅各自取 tone 80/40、20/100、30/90、60/50）；**双层缓存** `SEED_CACHE`（LruCache 64，按 url）与 `TINT_CACHE`（LruCache 32，按 `seed<<1 \| dark`）；8 秒`future.get` 超时；回调切主线程；pref 门控 |
| 行为 | 每个视频的播放器控制条颜色都不一样，和封面同色系；`b106b31` 之后**已收窄为只染播放器**（详情页图标不再跟随封面，避免整页花） |
| 我方现状 | **缺**。`[ours] app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/HighEnergyProgressBar.kt:33-38` 取色固定来自 `ColorScheme.PRIMARY`（静态主题色），`linePaint.color = 0xA8000000.toInt() or accentRgb`——**完全不看封面** |
| 成本 | **小-中（1–2 天）**：我方已有 `HighEnergyProgressBar` 且已参数化取色源头；需要新增的只有"封面 → 主色"（Glide + 量化器）+ 24 行缓存 + 播放器里一次异步回调。**不需要 F1/F8 的任何基础设施** |
| 冲突点 | 会引入 `material-color-utilities`（若不想引库，可用简易"量化取主色 + HSV 定 tone"替代，效果略差） |
| 价值 | **高**。手表屏幕小、播放器 UI 元素少，进度条/高能条是仅有的几处"可视化装饰"，跟着封面变色是低成本高感知的差异化；且它与主题系统解耦，可独立上线 |

#### F11. 主题作者工具链（示例包生成 + 恶意包测试集）

| 项 | 内容 |
| --- | --- |
| 功能名 | 给主题作者的脚手架：一键生成示例 `.btheme`，以及一组故意构造的坏包 |
| 对方实现 | `[sib] docs/sample-theme/README.md` + `[sib] docs/sample-theme/make_samples.py`；同目录已提交 `aurora.btheme`（正常示例）、`entry-too-big.btheme`、`oversize.btheme`、`format99.btheme`、`no-json.btheme`、`zip-slip.btheme`（六个**边界/攻击样本**，正好对应 `BThemeInstaller` 的每一条校验） |
| 行为 | 作者照着 README 打包；维护者用手上的坏包回归安装器 |
| 我方现状 | **缺**（我方无外观包概念，自然无工具链） |
| 成本 | 小（<半天，随 F5 一起做才有意义） |
| 冲突点 | 无 |
| 价值 | 低（对手表端用户），中（对维护者：这种"坏包测试集"的工程习惯值得学） |

#### F12. 主题的 API 门槛封装

| 项 | 内容 |
| --- | --- |
| 功能名 | material-color-utilities / 主题层在低版本上的降级策略 |
| 对方实现 | `[sib] theme/ThemeCompat.java`（69 行）`setBackgroundTintList`/`tintDrawable`/`tintCompoundDrawables`/`checkedStateList`；配合提交 `3d79357`（"material-color-utilities 需要 API 24，旧系统不再可用主题"）与 `f577723`（"允许低版本系统切换主题"）——即**先硬门槛、后放开** |
| 行为 | 低版本设备不会因主题层崩溃 |
| 我方现状 | 我方 minSdk **19**（比兄弟库的 14 高，但比 24 低），若引入 MCU 同样要处理 19–23 的降级 |
| 成本 | 小（<半天） |
| 冲突点 | 无 |
| 价值 | 中。**这是移植 F1 时必须先解决的门槛问题**（MCU 的 `Hct` 用 `Color` 的重载在 24+） |

### 1.2 播放器与弹幕

| 结论 | 证据 |
| --- | --- |
| 【事实】**无功能增量**。兄弟库 `PlayerActivity.java` 只做两件事：接上主题染色、接上内容取色；弹幕引擎仍用 `DanmakuFlameMaster`（我方同源）。 | `[sib] 470047f` 仅 27 行（直播弹幕回调主线程 + 销毁顺序），`[sib] b106b31`/`a4241e8` 均为主题相关 |
| 唯一可归入播放器的新东西是"进度条/高能条按封面取色"，已在 §1.1 F10 详述（跨域，实现属 `theme/`）。 | `[sib] theme/ContentTintHelper.java:16-19` 自述"当前只染播放器进度条与高能曲线" |

### 1.3 视频与详情页

| 结论 | 证据 |
| --- | --- |
| 【事实】**无功能增量**。兄弟库对 `VideoInfoApi`/`VideoActivity` 的唯一改动是"把手工 `JSONObject` 拆解换成 Gson 反序列化"（纯内部重构，用户不可见）与接主题。 | `[sib] c87e2ce`（"替换大多数手动 JSONObject/JSONArray 拆解为 Gson 反序列化"）、`[sib] 3685a0e` 中 `VideoInfoApi.java` 仅 +7 行（隐私模式分支） |
| 兄弟库独有 `model/OpusCard`、`adapter/article/ArticleContentAdapter`、`model/CustomText` 等是"图文动态/专栏"重构产物，**我方已有更完整的一套**（`OpusApi/OpusRichTextParser/OpusContentAdapter/OpusInfoActivity` 等 9 个文件）。 | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/` 下 `Opus*` 9 文件 |

### 1.4 动态与社交

| 结论 | 证据 |
| --- | --- |
| 【事实】**无功能增量**。兄弟库无独有动态页文件；动态相关的自有改动是修 bug（`438324f` 动态详情页 crash）与主题染色（`DynamicHolder`）。 | 43 个独有文件中无 `activity/dynamic/*`；`[sib] activity/dynamic/DynamicHolder.java` 只在染色彩色时被 `ThemeApplier` 调用 |
| 评论"带图"（兄弟库引以为傲的 PR #10）**我方已有且更稳**：见 §3。 | `[sib] 277f356`/`8ade4a1` vs `[ours] .../activity/reply/WriteReplyActivity.kt:75-105` |

### 1.5 私信与消息

| 结论 | 证据 |
| --- | --- |
| 【事实】**无独有页面**，但会话列表解析有一处**我方没有的字段合并**（详见 §2 优化 1）。 | `[sib] api/PrivateMsgApi.java:161-186` `parseSession()`；`[sib] model/PrivateMsgSession.java:14-18` 新增 `talkerName/talkerFace/maxSeqno/ackSeqno/lastMsgTimestamp` |

### 1.6 空间与用户

| 结论 | 证据 |
| --- | --- |
| 【事实】**无功能增量**。"个人资料编辑"（`a78e877` + PR #2）我方已有，且我方有更细的拆分： | `[sib] activity/user/EditProfileActivity.java`（+`activity_edit_profile.xml` 209 行）vs `[ours] .../activity/user/EditProfileActivity.kt` + `[ours] .../activity/user/EditUserInfoActivity.kt` |

### 1.7 收藏与稍后再看

| 结论 | 证据 |
| --- | --- |
| 【事实】**无功能增量**。兄弟库 `415edc6`「收藏接口更新至 v3」，我方**早已是 v3**： | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/api/FavoriteApi.java:105,157,237,330,371,576,589`（`x/v3/fav/folder/created/list`、`x/v3/fav/folder/info`、`x/v3/fav/resource/list`、`x/v3/fav/resource/batch-deal`、`x/v3/fav/folder/add \| edit`） |
| 其独有文件 `model/FavoriteSelectionState`、`api/FavoriteContract` 是内部状态对象抽象，非用户可见功能。 | 43 个独有文件清单 |

### 1.8 直播

| 结论 | 证据 |
| --- | --- |
| 【事实】**无功能增量**。兄弟库没有独立直播播放页，直播相关改动是修自己的并发/生命周期 bug（`470047f`：`addDanmaku` 统一投主线程 + `destroyed` 守卫、`onDestroy` 先 cancel WebSocket 再释放弹幕面与播放器、`OkHttpClient` 改类内单例、`online_number` 加 `volatile`）。 | `[sib] 470047f` diff 落在 `activity/player/PlayerActivity.java`（+17/−10） |
| 我方直播文件更全（`LiveApi` 358 行 / `LiveDanmakuApi` 280 行 / `LiveRoom` 375 行 / `LiveStreamSelector` 105 行 / `LiveDanmakuFactorPolicy` 等）。 | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/` 下 `Live*` 文件清单 |

### 1.9 搜索

| 结论 | 证据 |
| --- | --- |
| 【事实】**无功能增量**。兄弟库只有"搜索界面表冠问题修复"（`c87e2ce`）与"搜索黑屏修复"（`b303c5b`），都是他们自己 2.9.8 基线的问题；我方表冠控件体系已系统化（`RotaryRecyclerView`/`RotaryScrollView`/`RotaryNestedScrollView`/`RotaryEncoderSupport`/`RotaryEventPolicy`）。 | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/Rotary*.kt` |
| 其独有 `activity/settings/SortSettingActivity.java` 是**主菜单拖拽排序**（`MENU_SORT` + `DragAdapter` + `ItemTouchHelper`），**我方已有等价且更强**（一条 `menu_enabled` 同时管顺序与显隐，含 `menu_sort` 旧数据迁移）。 | `[sib] activity/settings/SortSettingActivity.java:37-70` vs `[ours] app/src/main/java/com/RobinNotBad/BiliClient/util/MenuConfig.kt:4-11` |

### 1.10 下载与缓存

| 结论 | 证据 |
| --- | --- |
| 【事实】**无功能增量**。独有 `adapter/video/LocalVideoAdapter`、`ui/widget/CustomListView`、`util/ViewCache`、`ui/widget/recycler/{AbstractAdapter,BaseAdapter,BaseHolder}` 是内部 Adapter/ViewHolder 重构骨架，不是新能力；其"实验室 → 旧版下载器 / 缓存路径 / 图片下载路径"三个开关我方设置项已覆盖。 | 43 个独有文件清单；`[ours] .../activity/video/local/LocalListActivity.kt` 等 |

### 1.11 通知与后台

| 结论 | 证据 |
| --- | --- |
| 【事实】**无功能增量**。兄弟库没有独有 Service/通知实现文件；其 `<sib> 33090d9</sib>` 系列是低版本兼容崩溃修复（MediaSession 字段类加载、clipPath 硬件加速、WebP→JPG、`splash_text` 低版本尺寸），与我方 minSdk 19 关系不大。 | `[sib] 5c480df`/`9c9fea0`/`3512984`/`9d28c3c`；我方已具备"请求 JPG"开关（`image_request_jpg`） |

### 1.12 杂项工具

#### F13. 游客身份 Cookie 自造（未登录也能看视频）

| 项 | 内容 |
| --- | --- |
| 功能名 | 本地生成一套"游客身份"Cookie，让未登录/无网络账号的设备也能正常取流 |
| 对方实现 | `[sib] util/CookieGenerator.java:18-45` `ensureCookies()`：缺则生成 `buvid3`/`buvid4`、`bili_ticket`（HMAC）、`_uuid`、`b_lsid`、`buvid_fp`、`b_nut`；`getCookieString(boolean)` 供播放器/直播/详情页使用；调用点 `[sib] util/NetWorkUtil.java:442` |
| 行为 | 首次启动无账号时，播放器请求带的是自造游客 Cookie，而不是空 Cookie（空 Cookie 取流易被风控拒） |
| 我方现状 | **部分覆盖**：我方已有官方取 buvid 的接口 `[ours] app/src/main/java/com/RobinNotBad/BiliClient/api/CookiesApi.java:89-96`（`x/web-frontend/getbuvid`），且**隐私模式已实现"剔除登录 Cookie、保留游客身份 Cookie"** `[ours] app/src/main/java/com/RobinNotBad/BiliClient/util/NetWorkUtil.java:348-356`（`getJsonPrivacy` → `buildGuestCookieString`，剔除 `SESSDATA/bili_jct/DedeUserID/DedeUserID__ckMd5/sid`）。**但没有任何"本地自造"通道**：全新设备、且从未成功请求过 `getbuvid` 时，游客请求仍可能是空身份 |
| 成本 | 小（<半天）。我方缺的只是"缺失时兜底生成"这一步；`buvid3` 可用现成官方接口补，`_uuid`/`b_lsid`/`b_nut` 生成逻辑约 40 行 |
| 冲突点 | 自造 `bili_ticket` 属"模拟客户端凭证"性质，**建议只补 `buvid3`（官方接口）+ `_uuid`/`b_lsid`/`b_nut`（纯本地随机），不自造 `bili_ticket`** —— 既拿到收益又不碰风控红线 |
| 价值 | 中。手表端"先能看，再登录"是真实路径；首次安装未登录直接播放是留存关键一步 |

#### F14. BV ↔ AV 号互转工具

| 项 | 内容 |
| --- | --- |
| 功能名 | `bvid` 与 `aid` 的离线互转（不请求接口） |
| 对方实现 | `[sib] api/BilibiliIDConverter.java:6-30`：标准 58 进制表 `fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF`、位序 `{11,10,3,8,4,6}`、`xor=177451812`、`add=8728348608`；`bvtoaid`/`aidtobv` |
| 行为 | 粘贴 `BV1xx411c7mD` 直接跳转/播放，无需先请求 `view` 接口换 aid |
| 我方现状 | **缺**（我方 `[ours] .../api/RecommendApi.java:146` 等处直接用接口返回的 `aid`/`bvid`；无本地互转工具类）。用户从外部拿到 BV 号时仍可用，但"离线换算/校验输入的 BV 号是否合法"没有 |
| 成本 | 小（<半天，40 行纯函数 + 单测） |
| 冲突点 | 无 |
| 价值 | 低-中。属"锦上添花"；若我方有"输入 BV/AV/短链打开"入口，则能省一次网络往返并允许离线校验 |

#### F15. 实验室设置页（实验性开关集中管理）

| 项 | 内容 |
| --- | --- |
| 功能名 | 把实验性开关集中到一个"实验室"页面 |
| 对方实现 | `[sib] activity/settings/SettingLaboratoryActivity.java:27-77`，分组：可用性（新版弹幕获取 `new_danmaku_api`、私信未读标记）、下载（旧版下载器 `dev_download_old`、缓存路径、图片下载路径）、UI（横屏 `ui_landscape`、开屏文字、跑马灯）、播放器（**软件旋屏方案 `dev_player_rotate_software`**、视频分段、系统媒体控件、互动视频调试）、网络（**接口重试间隔/重试次数**，默认 0.1s / 5 次）、调试（`Logu.v/d/i`、详细解析报错） |
| 行为 | 实验能力不污染主设置页，出问题可单独关 |
| 我方现状 | **组织形态不同，能力不缺（逐项核实过）**：开屏文字、跑马灯、横屏、媒体控件、视频分段（`VIEWPOINT`）、重试策略、调试日志开关我方均已存在（`[ours] .../activity/settings/SettingText.kt:178,271`、`[ours] .../activity/settings/SettingGroupActivity.kt:313-314`、`[ours] .../util/SettingsKeys.kt:39`）；**"软件旋屏兼容方案"我方也有**：`[ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:805`「播放器旋屏兼容方案」→ `[ours] .../util/SettingsKeys.kt:173` `DEV_PLAYER_ROTATE_SOFTWARE`=`dev_player_rotate_software`，实现见 `[ours] .../activity/player/PlayerActivity.kt:3686-3702` `applySoftwareRotationLayout()`（`rootLayout.rotation = 90f` + pivot 归零 + `x = screen_height`）+ `:1103,2403,4430` 三处调用点；「新版弹幕获取方式」`[ours] .../activity/settings/SettingGroupActivity.kt:785` + `[ours] .../util/SharedPreferencesUtil.java:50`；「使用旧版下载器」`[ours] .../activity/settings/SettingGroupActivity.kt:699` + `[ours] .../api/PlayerApi.java:70`。**甚至连 key 名都是同一个 `dev_player_rotate_software`。** |
| 成本 | 不适用（无缺口） |
| 冲突点 | 无 |
| 价值 | 低（页面收纳形态可借鉴：我方实验项散在设置页各处，兄弟库集中一页；但这是组织偏好，不是能力） |

---

## 2. 我方有、对方做得更好的功能优化

> 说明：本节列"同一功能双方都有，但兄弟库在某处更优/更稳"的点。每条都标了我方对应实现，便于 Lead 判断是"抄做法"还是"抄代码"。

### O1. 私信会话列表：`account_info` 里直接拿昵称/头像，省一次批量用户信息请求

- 【事实】兄弟库把会话解析统一到 `[sib] api/PrivateMsgApi.java:161-186` `parseSession()`，在同一个响应里取 `talkerName = account_info.name`、`talkerFace = account_info.face`，并补齐 `maxSeqno/ackSeqno/lastMsgTimestamp`（`[sib] model/PrivateMsgSession.java:14-18`）；`getSessionsList` 与 `getNewSessionsList` 共用它（`:150`、`:216`）。
- 【事实】我方 `[ours] app/src/main/java/com/RobinNotBad/BiliClient/api/PrivateMsgApi.java:164-205` 已修掉同源 bug（`:176` 注释明确指出旧写法 `!has && isNull` 恒等于 `!has`，会把 `account_info` 为 `null` 的普通会话一起丢弃），**但只在 `account_info` 为 null 时才把会话收下，不解析其中的 name/face**；我方 `[ours] .../model/PrivateMsgSession.java:5-18` 字段里也只有 `talkerUid/unread/contentType/content/topTs`。
- 收益：会话列表首屏少一次"批量拉用户信息"的请求（手表端弱网下这一跳往往就是首屏 1–2 秒）；另外 `max_seqno/ack_seqno` 是"已读到哪"的正确来源，比只靠 `unread_count` 更可靠。
- 成本：**小（<半天）**。改 `parseSession` + 补 4 个字段 + Adapter 优先用 `talkerName` 回落用户信息即可。
- 价值：中高（弱网首屏 + 未读准确性）。

### O2. 隐私模式：把"不带账号 Cookie"做成请求级能力，而不只是"不上报进度"

- 【事实】兄弟库 `[sib] util/NetWorkUtil.java` 新增 `getJsonPrivacy(url)`（把 `webHeaders` 的 Cookie 位置换成 `CookieGenerator.getCookieString(false)`，即只带游客身份），并在 `[sib] api/VideoInfoApi.java:202-220` 的两个 `getVideoInfo` 里按 `PRIVACY_MODE` 分流。
- 【事实】我方**已实现同款思路且更完整**：`[ours] .../util/NetWorkUtil.java:348-356` `getJsonPrivacy` → `buildGuestCookieString(getCachedCookies())`（剔除 `SESSDATA/bili_jct/DedeUserID/DedeUserID__ckMd5/sid`，保留 `buvid3/buvid4/bili_ticket/_uuid`），`[ours] .../api/VideoInfoApi.java:33,43` 按 `PRIVACY_MODE` 分流，`[ours] .../api/HistoryApi.java:47,77` 双保险直接不上报。
- 兄弟库比我们多的一点：`[sib] theme/ThemeManager.java` 之外的**开关联动范围**——它把隐私模式也铺到直播/播放器请求头（`[sib] 3685a0e` 中 `PlayerActivity` 三处 header 改成 `CookieGenerator.getCookieString(true)`，注意 `true` = 含完整 Cookie，属"非隐私"分支）。这不是优化，反而说明它是"一处开关、多处手改"。
- 结论：**这项我方已经更好，无需移植**；但可借鉴它"隐私模式需覆盖播放器/直播请求头"的检查清单，核一遍我方 `PlayerActivity`/直播取流是否全部走 `getJsonPrivacy`（我方 `[ours] .../activity/player/PlayerActivity.kt` 未逐行核实，见 §5）。
- 成本：小（自查）；价值：中（隐私承诺的完整性）。

### O3. 染色守卫清单（`TintRules`）作为"我方 86 个 style 的清点依据"

- 【事实】即使我方不移植主题系统，`[sib] theme/TintRules.java`（295 行）本身就是一份"哪些颜色动不得"的清单：布局里 hint 的 alpha 档（`#80ffffff` ×5、`#88ffffff` ×1）、历史默认文字色 `0xFFEBE0E2`、按钮底色 `0xCC262626`/`0xDD262626`、B 站粉选中态 `#FB7299`、单色 vs 彩色图标分类。
- 我方对应物：`[ours] app/src/main/res/values/themes.xml` 86 个 style + 各 layout 内联色值。
- 收益：给未来任何"深色/主题改造"提供已知雷区，避免重蹈兄弟库 6 个 fix commit（`54a527e`/`520b12a`/`b106b31`/`a4241e8`/`ef802f1`/`0ff36ce`）。
- 成本：小（读一遍 + 记坑）；价值：中。

### O4. "列表滚动进来/后 inflate 的 item 也要染色"（`hookRecyclerView`）

- 【事实】`[sib] theme/ThemeApplier.java:320-342` 给 RecyclerView 挂 `OnChildAttachStateChangeListener`，对每个 attach 的 child 重新 `apply`，解决"主题只在首屏生效"。
- 我方对应：我方主题切换靠 `recreate()`，不存在此问题；但**我方 `RoundListEffect`/`SegmentedBarStyle` 这类"运行期改 item 外观"的模块**（`[ours] app/src/main/java/com/RobinNotBad/BiliClient/ui/appearance/RoundListEffect.kt` 159 行、`SegmentedBarStyle.kt` 394 行）若走"只处理首屏"的写法会有同类坑，值得按这条自查。
- 成本：小（自查）；价值：低-中。

### O5. 设置项输入校验："界面边距超过 30% 不保存"

- 【事实】`[sib] activity/settings/SettingUIActivity` 的边距输入超过 30 拒绝保存（对应 layout `activity_setting_ui.xml` 的 `ui_margin_*`）。我方 `[ours] .../activity/base/BaseActivity.kt:230` `applyUserPadding()` 是"用户边距加在内容容器上"，但输入上限约束未核实。
- 收益：避免用户把手表 UI 挤成一团后无法返回设置（圆屏设备尤其致命）。
- 成本：小（<半天）；价值：中。

---

## 3. 不建议移植

| # | 项 | 理由 |
| --- | --- | --- |
| N1 | **`.btheme` 主题包生态（F5 + F7 的包管理 + F11 工具链）** | ①手表端"从文件管理器装 zip / 列表管理主题"交互极重；②兄弟库自己都只能在 API≥19 用系统选择器、更老的系统退回"手输文件路径"；③一旦开放包格式就是长期兼容负担（他们已经写好了 `format` 版本号与"主题版本过新，请升级"文案，说明已经预见到）。**收益/成本比最差的一项** |
| N2 | **主题化渲染层 ThemeApplier 全量移植（F8）** | 832 行 + 与我方"XML 静态主题 + recreate"路线正面冲突；我方 `AppearanceManager.appearance_version` 机制（`[ours] .../ui/appearance/AppearanceManager.kt:30-46`）已经用极小成本解决了"外观变更后 Activity 重建"。除非决定彻底转向运行期配色，否则不要引入 |
| N3 | **Gson 全量反序列化重构（`c87e2ce`）** | 纯内部重构，用户零感知；我方已有 `org.json` 直解与 `model/` 契约，混入 Gson 只会让两条解析风格并存 |
| N4 | **低版本兼容层（`util/CompatUtil`、`util/SSLSocketFactoryCompat`、WebP→JPG、`splash_text` 低版本、clipPath 兜底）** | 兄弟库为 minSdk **14** 服务（`[sib] app/build.gradle:28`）；我方 minSdk **19**，且"请求 JPG"开关已有（`image_request_jpg`）。逐条搬过来只是死代码 |
| N5 | **自研 Adapter/ViewHolder 骨架（`ui/widget/recycler/{AbstractAdapter,BaseAdapter,BaseHolder}`、`util/ViewCache`、`ui/widget/CustomListView`）** | 与我方 `RefreshListActivity`/`RefreshListFragment` 体系重复；移植等于重写所有列表页 |
| N6 | **"精简在线服务"路线（`2ec3fb8` 删除公告/赞助/更新检查/更新安装 + 硬控倒计时）** | 兄弟库动机是"不引入非 B 站官方在线服务"（`[sib] readme.md:22-23`）。我方有公告、检查更新、反馈、发版通道（我方独有 `Announcements/UpdateActivity/Feedback*/Disguise*` 等一大批活动页），是产品能力不是负担。**不要跟着删** |
| N7 | **三连图标重绘（`icon_coin_*/icon_fav_*/icon_like_*`，`3685a0e` 里的"纯个人喜好"改动）** | 作者自述"纯个人喜好"；我方有自己的视觉体系（`GlassStyle`/`SegmentedBarStyle`），换图标是审美分叉不是功能 |
| N8 | **"实验室"页面原样照搬（F15 的页面形态）** | 开关本身我方都有；把实验项堆一页会让我方设置结构变乱。值得学的只有"软件旋屏方案"一个具体能力（且见 §5 存疑） |
| N9 | **`SortSettingActivity`（菜单拖拽排序独立页）** | 我方 `[ours] .../util/MenuConfig.kt:4-11` 用一条 `menu_enabled` 同时管顺序与显隐，还带旧 `menu_sort` 迁移，比兄弟库"另开一页只管排序、显隐另有开关"更一致 |

---

## 4. 优先建议（Top 6）

按"价值 ÷（成本 × 冲突）"排序。

1. **封面取色染播放器（F10）— 成本小-中，价值高，零冲突**
   我方 `[ours] .../ui/widget/HighEnergyProgressBar.kt:33-38` 已经把取色源头参数化，只需接"封面 → 主色"（Glide 64px + 量化器 + 两层 LruCache，参考 `[sib] theme/ContentTintHelper.java:60-120`）。不触碰主题层，不改 XML，独立可上线，观感差异立刻可见。**唯一决策点是"引不引 `material-color-utilities`"**（不引则用 HSV/中位色裁量替代，效果约七成）。

2. **深浅双套背景图 + 遮罩色 + 更多适配档（F6 的可拆部分）— 成本小，价值中高**
   我方 `[ours] .../ui/appearance/BackgroundImage.kt:54-66` 只差"深/浅两套路径 + fitXY/tile + 遮罩色"。**可以完全不碰主题系统**，只扩 `AppearanceManager.Appearance` 快照与设置页；OLED 手表收益直接。

3. **动态配色引擎 + 自定义种子色 + blend（F1+F2+F3）— 成本中，价值高，冲突中**
   引入 MCU → `SchemeEngine`（Kotlin 化）→ 映射到我方 40 个令牌。**必须先解决两件事**：①API 19–23 降级（F12）；②把"只换色板、不改 XML"这条路走通——即保留我方静态 XML 主题作为兜底，动态配色作为"第 7 套：自定义"叠加在 `ColorScheme` 层。**这样可以绕开 F8 的 832 行渲染层**。

4. **私信会话 `account_info` 合并解析（O1）— 成本小，价值中高**
   少一跳请求 + 补 `max_seqno/ack_seqno`，弱网首屏直接受益。

5. **游客身份兜底（F13 的收敛版）— 成本小，价值中**
   只补 `buvid3`（走我方现成的 `[ours] .../api/CookiesApi.java:89-96` 官方接口）+ `_uuid`/`b_lsid`/`b_nut` 本地生成，**不自造 `bili_ticket`**。目标是"首次安装未登录也能播"。

6. **实验室开关的收纳形态（F15 的可借鉴部分）— 成本小，价值低-中**
   能力我方全有（含软件旋屏，`[ours] .../activity/player/PlayerActivity.kt:3686-3702`），值得学的只是"实验项集中一页、出问题单独关"的组织方式。**纯整理工作，随时可做，不做也不影响任何功能。**

> 若只能做一件：**建议 1（封面取色）**；若能做两件：**1 + 2**。这两件都不动主题架构。
> 排序口径：外观类改动里，**价值 ÷（成本 × 与我方静态 XML 主题路线的冲突）**——F10/F6 冲突为 0，F1 冲突中，F8 冲突最大。

---

## 5. 存疑

1. ~~"软件旋屏兼容方案"我方是否缺失~~ → **已核实，我方有，且 key 同名 `dev_player_rotate_software`**
   核实过程（留档以免重复劳动）：`[ours] .../activity/player/PlayerActivity.kt:3686-3702` `applySoftwareRotationLayout()` 做 `pivotX/Y=0` + `x = screen_height` + `rotation = 90f` 的根布局旋转（并给 TextureView 覆盖 `Matrix`），调用点 `:1103`（手动旋转按钮）、`:2403`、`:4430 softwareRotate()`、`:3721 reapplyVideoGeometry()`（回前台重放）；设置开关 `[ours] .../activity/settings/SettingGroupActivity.kt:805`、key 常量 `[ours] .../util/SettingsKeys.kt:173`。**结论：F15 整条无缺口，已从 §4 移除。**

2. **隐私模式是否覆盖播放器/直播取流头**
   【推测】我方 `getJsonPrivacy` 已存在（`[ours] .../util/NetWorkUtil.java:348-356`），但播放器/直播的 ijkplayer header 是否也走"游客 Cookie"，未逐行核实。若不覆盖，则"隐私模式"在播放器上可能仍带账号 Cookie（服务端仍能记观看行为）——这是**承诺与实现不一致**的风险，比任何功能差距都更该先查。

3. **兄弟库那批 2.9.8 时代的 bug 修复我方是否都独立修过**
   已确认必修的：热门 `data.list`（我方 `[ours] .../api/RecommendApi.java:141-142` 正确读 `list` ✅）、私信 `account_info` 条件取反（我方 ✅）、评论少图发送（我方 `pendingUploads` 守卫 ✅，`[ours] .../activity/reply/WriteReplyActivity.kt:101-105`）、`FileUtil.readString` 不关流（我方 try-with-resources ✅，`[ours] .../util/FileUtil.java`）。未核实的：`回复我的页面无昵称`（`d1d85a0`）、`回复消息显示错乱`（`ab9c070`）、`动态详情 crash`（`438324f`）、`CatchActivity span 越界`（`9a13aa4`）、`ImageViewerActivity 打开崩溃`（`322a0be`）。**这些是我方可能仍在踩的坑，建议另开一轮"按 commit message 关键词 grep 我方代码"的核对**（成本约半天）。

4. **兄弟库"内容取色"最终形态是否仍值得抄**
   【事实】`b106b31` 已把它从"详情页图标 + 播放器"收窄为"只染播放器"。这说明**大范围按封面取色在他们自己手上就是失败路线**（整页花）。抄的时候只抄"播放器"这一处，别抄早期版本。

5. **我方 6 套主题的真实使用分布**
   【推测】若绝大多数用户只用 1–2 套，则 F1（动态配色）的价值判断应下调，反之则上调。无用户侧数据，只能由 Lead 用人气/反馈面判断。

6. **`material-color-utilities` 的引入成本**
   【事实】兄弟库是把它当**本地 Gradle 模块**引入的（`[sib] app/build.gradle:132-135` 与仓库根同名目录）。我方若引入需评估：模块体积（含 `Hct`/`Scheme`/`QuantizerCelebi`，纯 Java 无依赖）、以及是否与 R8/多 flavor 构建冲突。未实测。

---

## 附：兄弟库独有文件全清单（43 个，供复核）

`activity/settings/{SettingLaboratoryActivity, SettingUIActivity, SortSettingActivity, ThemeSettingsActivity}`、`activity/TutorialActivity`、`adapter/{article/ArticleContentAdapter, CoinLogAdapter, ExpLogAdapter, ThemeListAdapter, video/LocalVideoAdapter}`、`api/{BilibiliIDConverter, FavoriteContract, ReplyPageContract}`、`event/ThemeChangedEvent`、`helper/TutorialHelper`、`model/{ApiResponse, CustomText, FavoriteSelectionState, OpusCard, ReplyPagination, Tutorial}`、`theme/{BThemeInstaller, ColorExtractor, ContentTintHelper, SchemeEngine, ThemeApplier, ThemeCompat, ThemeManager, ThemePalette, TintRules}`、`theme/model/{InstalledTheme, ThemeManifest}`、`ui/widget/CustomListView`、`ui/widget/recycler/{AbstractAdapter, BaseAdapter, BaseHolder}`、`util/{CompatUtil, CookieGenerator, GeetestUtil, GsonUtil, ImageViewer, SSLSocketFactoryCompat, ViewCache}`。

> 其中 **12 个属主题包**，**6 个属列表骨架/工具重构**，**5 个属 Model/Contract 抽象**，**4 个是设置/教程页**，**3 个是 Adapter**；真正带新用户能力的只有 `theme/*`（外观，F1–F12）、`util/CookieGenerator`（游客身份，F13）、`api/BilibiliIDConverter`（ID 换算，F14）。`SettingLaboratoryActivity`（F15）只是把开关收了收，逐项核实后**其内部能力我方全部已有**（含软件旋屏、新版弹幕、旧版下载器）。
