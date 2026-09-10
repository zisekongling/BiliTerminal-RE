# RE:哔哩终端 视觉体验优化报告

> 审计对象：`main` 分支工作区（`versionName 26.09.08`）
> 审计方式：逐文件读源码 + 全量统计 `app/src/main/res/` 与 `app/src/main/java/`（统计口径见文末附录 A）
> 相关文档：`docs/architecture-map.md`（架构通读）、`AGENTS.md`（硬约定）
> 本文只诊断、不修改；改动方案见第 4 节分批计划。

---

## 0. 摘要

### 0.1 一句话结论

**这个项目的视觉问题不是"不好看"，而是"没有设计系统"。** 界面本身有配色、有卡片、有 ripple、有 Glide 渐入、有教程页配图，说明作者在乎观感；但所有视觉决策都散落在十几处互不通信的"真源"里：改动任何一处观感，都要同时改 5～8 个文件，因此实际结果是不一致。

所以第一优先级**不是**"再画好看一点"，而是**收敛设计 token**（颜色、字号、间距、圆角、状态色各一份真源），让后续每处改动一次生效。

### 0.2 三个核心矛盾

| # | 矛盾 | 证据 |
|---|---|---|
| 1 | **颜色有三套并行的值表（四层实现），互相不保证一致** | ① `res/values/colors.xml`（503 行，8 套色板 + 一堆别名）② `res/values/themes.xml`（763 行，`Theme_*` 引用 ①）③ `ui/theme/ThemeManager.kt`（514 行，41 个 Kotlin 常量 getter）④ `ui/theme/BiliColors.kt`（256 行，用 `Color.parseColor` 又抄了一份色板） |
| 2 | **主题切换不彻底，"选了主题但部分页面不跟着变"** | `activity_player.xml:5`、`activity_image_viewer.xml:7`、`cell_episode.xml:4` 硬编码 `android:theme="@style/Theme.BiliClient"`；`ImageViewerActivity.kt:28`、`MediaEpisodeAdapter.kt:48`、`QualitySelectorAdapter.kt:56` 在代码里强制 `Theme_BiliClient` |
| 3 | **没有排版与间距体系，层级全靠加粗和 alpha 硬凑** | 446 处 `textSize` 中 11/12/13sp 占 333 处，全工程最大只有 18sp（3 处）；143 处 `android:alpha` 用了 16 种不同值跨 55 个布局表达"次要文字"，其中 `0.5` 出现 48 次；默认主题（经典终端）把 `TEXT_PRIMARY/TEXT_SECONDARY/TEXT_TERTIARY` 全设成同一个 `#EBE0E2`（`ThemeManager.kt:315-317`）——文字分级在默认主题下**完全不存在** |

### 0.3 数字速览

| 指标 | 数值 | 含义 |
|---|---|---|
| 布局 xml | 149（+2 变体） | 视觉改动的战场；真正的列表卡片是 `cell_*.xml`（55 个），不是 `item_*.xml`（仅 8 个） |
| 含 `@+id/top`（顶栏）的布局 | **47** | 同一段顶栏被手抄 47 遍，改一次要改 47 处 |
| 其中可复用的 `cell_topbar.xml` | 存在，但 **0 处 `<include>`** | 公共顶栏写了没人用 |
| 布局内硬编码颜色 | **137 处 / 51 个布局**（`#fff` 47 次、`#999999` 7 次、`#dd262626` 5 次…） | 这些地方不随主题变化 |
| 引用 `?attr/...` 主题值的布局 | **22 / 151** | 只有 15% 的布局接入了主题系统 |
| 引用**静态** `@color/...`（含 `pink_light`/`card_dark_*`） | **86 处** | 切主题后仍显示 B站粉的根因 |
| `res/values/dimens.xml` | **8 个 token / 10 行** | 间距、圆角、字号、触控区全无 token，全靠字面量 |
| 字号档位 | **11 档**（8～18sp），446 处中 333 处挤在 11-13sp | 没有 typographic scale，层级靠 `textStyle="bold"`；`textSize` 走 `@dimen` 的 **0 处** |
| alpha 档位 | **16 档**（0.1～0.95），143 处 | 次要文字语义化缺失，深色底对比度不可控 |
| 圆角档位 | 1/3/6/8/12/15/20/25/28dp 共 9 档 | 同上 |
| 间距 | margin 970 处中 8dp 倍数仅 **35.8%** | 无栅格节奏 |
| 图片加载 | 55 个调用点，统一封装（`GlideUtil.request*`）**仅 2 处在用**；`.error()` **0 处** | 加载失败会串上一行的图；画质被双重限死（见 2.7） |
| 布局高度用 `sp` 当单位 | **36 处**（32 处为 `35sp`） | 系统字体放大即压扁，功能性缺陷 |
| 主题色值一致性 | 7 套主题里发现 **5 处矛盾**（默认主题缺 4 个 item、知乎蓝 ripple 是粉的、Rainbow 两色互换、`ON_PRIMARY` 6/7 套不一致、`BiliColors` 常量漂移） | 见 2.22，"切了主题但观感不对"的深层原因 |
| 颜色命名 | `colors.xml` **309 个颜色 / 83.5% 是别名**（`#FF6699` 有 15 个名字），`res/` 内 61 处仍引用旧别名 | 改一处颜色要 grep 十几个名字（见 2.10） |
| 代码层硬编码色 | **68 处 / 26 个文件**（如 `Color.rgb(207,75,95)` 出现在 7 个文件） | 切主题后不变色（见 2.27） |
| 定义了却没人用的 style | **28 个**（含整个 `modern_styles.xml`、6 套主题的 `*.Splash`/`*.NoSwipe`） | 开屏/对话框永远走 B站粉基线（见 2.24） |
| 主题数 | 7 套配色（+1 套浅色主题**不可达**） | `Theme.BiliClient.Light`（`themes.xml:44`）无任何引用 |
| 死视觉代码 | `BiliColors.kt` 实际 2 个调用点、`BiliDimens` 0 调用、`ThemeUtils` 18 个方法用了 1 个、`modern_styles.xml` 3 个样式 0 引用、"modern/classic 外观"开关无 UI 且无效果 | 约 600 行视觉代码不参与渲染，却在误导后来者 |
| 自适应图标 | **缺失**（只有 `res/mipmap-nodpi/icon.webp`，无 `mipmap-anydpi-v26`） | Android 8+ 启动器里图标被缩小加底板 |

### 0.4 建议的动作顺序

**A. 先做（工作量 S、风险低、用户立刻能感知）**

1. **修 `applyWindowTheme` 的标志位 bug**（`ThemeManager.kt:405-407`，2.4）—— 一行改动、7 套主题全局见效：现在导航栏图标是黑的且底色是黑的、状态栏白图标压在亮色品牌底上。
2. **图片画质与失败兜底**：`GlideUtil.url()` 参数化 + 删掉 `VideoCardHolder.kt:147-154` 的 `override(400,225)`/`sizeMultiplier(0.85)` + 全部加载点补 `.error()`（2.7）。
3. **修掉 `sp` 当高度**（36 处，2.11）与**触控目标 <48dp / 零反馈点击**（2.14、2.17）。
4. **三连卡对比度**（`fragment_video_info.xml:200-282`，2.25）—— 全报告唯一可量化的 AA 不合规项。
5. **一级页补空态**（`RefreshMainActivity` 照抄 `RefreshListActivity`，2.18）。
6. **默认主题三档文字色拆开 + 列表次要文字 `alpha 0.5→0.7`、11sp→12sp + 分割线可见化 + `#fff` 语义化**（2.3、2.12、2.7c）。
7. **主题色值 5 处矛盾**（默认主题缺 4 个 item、知乎蓝粉色 ripple、Rainbow 互换、`ON_PRIMARY` 不一致、BiliColors 漂移）（2.22）。

**B. 随后（结构，是后续所有改动的基座）**

8. **建立设计 token 并让 `ThemeManager` 成为唯一颜色真源**（2.2）：删 `BiliColors`/`BiliDimens`/`ThemeUtils`/`modern_styles.xml`/`APPEARANCE_*` 死代码。
9. **修主题泄漏**（2.1、2.12、2.23）：3 处布局硬编码 theme、3 处代码强制 theme、86 处静态色引用、3 个裸 `Activity` 页面。
10. **顶栏收敛**（2.5）：47 份复制品 → `cell_topbar.xml`（`minHeight=48dp`）。
11. **卡片规格与封面比例统一**（2.6、2.19、2.20），再谈骨架屏与转场动效（2.8、2.26）。

---

## 1. 现状盘点：视觉基建地图

### 1.1 颜色：四层并行，无单一真源

```
res/values/colors.xml          503 行：8 套色板（B站粉/知乎蓝/爱奇艺绿/紫色空灵/
                              五彩斑斓/经典灰/经典终端）+ 大量旧别名（pink/bgblack/
                              textwhite/color_ripple/purple_* 等）
        ↓ 被引用
res/values/themes.xml          763 行：7 族 Theme_* + 每族 7 个组件样式
                              （TextView/EditText/Card/Button/Switch/Radio/List）
        ↓ 运行时被 BaseActivity 选中
activity/base/BaseActivity.kt:65-75   setTheme(R.style.Theme_*)
        ↓ 另一套完全独立的常量表
ui/theme/ThemeManager.kt       514 行：7 个 ThemeColors object + 41 个 getter
ui/theme/BiliColors.kt         256 行：又抄一份色板（Color.parseColor 硬编码）
ui/theme/ThemeUtils.kt         116 行：BiliColors 的静态转发
```

关键事实（均已 grep 核实）：

- `BiliColors` 的真实调用点只有 **3 处**：`adapter/favorite/FolderChooseAdapter.kt:58,82`，以及 `ThemeUtils.getInfoColor()` 被 `activity/video/info/VideoInfoFragment.kt:603` 调用一次。
- `ThemeUtils` 的 18 个 getter 里 **17 个是死代码**，`createButtonBackground`/`setViewBackground` 也无人调用。
- `BiliDimens`（`BiliColors.kt:222-256`，含 6 档间距、4 档圆角、4 档图标、6 档字号）**0 处引用**。
- `getCardBackgroundColor` / `getButtonBackgroundColor` / `getColorScheme` / `setAppearanceStyle` **0 处外部调用**；`getAppearanceStyle` 只被 ThemeManager 内部那 2 个死方法调用。
- 因此"外观风格（modern/classic）"这个开关**既没有设置入口**（`APPEARANCE_STYLE` 只在 `ThemeManager.kt:479` 被读），**也没有任何渲染效果**——是一个完整的幽灵功能。
- `modern_styles.xml`（`ModernWindowAnimation`/`ModernButton`/`ModernCard`）**0 引用**。

★ **这是全项目最值得先解决的结构问题**：两套颜色表并存时，"改 xml 主题"和"改 Kotlin 主题"各改一半，观感必然漂移。

### 1.2 主题切换链路的实际覆盖

- 入口：`activity/settings/SettingGroupActivity.kt:233-253`「主题配色」7 选 1，写 `SettingsKeys.THEME` 后 `recreate()`。
- 应用：`BaseActivity.kt:65-75`（onCreate 里 `setTheme`）；`BaseActivity.kt:253-257`（onResume 发现主题变化就 `recreate()`）；`SplashActivity.kt:116` 与 `PlayerActivity.kt:353-361` 各自又抄了一遍相同映射。
- 缺口（见 2.1 详述）：3 个布局 + 3 处代码把主题钉死在 `Theme.BiliClient`；`AndroidManifest.xml:34` 的默认主题是 B站粉暗色，而代码默认主题是**经典终端**，两者并不一致。

### 1.3 尺寸与排版

`res/values/dimens.xml` 全文只有 8 个 token：

```xml
activity_padding_horizontal 6dp   list_margin_vertical 2dp
linearlayout_divider 4dp          grid_spacing 6dp
card_round 6dp                    card_stroke 0dp        round_small 4dp
```

其余全部是字面量：`textSize` 440 处、固定 `layout_width/height` 的 dp 值 235 处以 `0dp`（`0dp` 是约束占位，正常）、`28dp`(29)、`32dp`(24)、`37dp`(12)、`30dp`(12)…

- **字号**：8sp×1、9×1、10×6、**11×85**、**12×118**、**13×128**、14×73、15×4、16×13、17×8、18×3。没有 20sp+ 的标题档，最大的 3 个 18sp 出现在少数页面里。
- **间距**：`6dp`/`4dp`/`2dp`/`10dp`/`8dp`/`12dp` 混杂，偶数栅格 ≈ 4dp 而不是常见的 8dp；`activity_padding_horizontal=6dp` 让卡片左右只留 6dp，视觉上贴边。
- **圆角**：12dp（35 处，主流）、`@dimen/card_round`=6dp（16 处，**只有经典终端主题**用它，见 `themes.xml:731`）、8dp（4 处，`cell_coin_log.xml:8` 等）、28dp（头像）、以及 1/3/15/20/25dp 各 1 处。
  → 结果是：默认主题（经典终端）圆角 6dp，其余主题 12dp，同一屏内还有硬编码 8dp 的卡片，**用户看到的圆角随机**。

### 1.4 列表 / 图片 / 状态

- 图片：Glide 4.16 全项目在用，统一入口 `util/GlideUtil.java`，但**列表图默认走低画质**：`GlideUtil.url()` 追加 `@0e_25q_512w.webp`（`QUALITY_LOW=25`，`MAX_W_LOW=512`，`GlideUtil.java:17-20,36-42`）；且统一 `DecodeFormat.PREFER_RGB_565`（`GlideUtil.java:66,75,86`），在深色渐变封面上容易出现色带。1080p 手机上 512w 封面会被放大，观感偏糊。
- 加载中/加载更多：`RefreshMainActivity.kt:26`、`RefreshListActivity.kt:32` 用 `swipeRefreshLayout.isRefreshing = true` 表示"加载更多"，于是**滑到底部时顶部弹出下拉刷新转圈**，而不是底部出现 footer；两者都**没有 `setColorScheme`**（全项目 0 处），转圈颜色与主题无关。
- 空态：`activity_simple_refresh.xml:52-59` 等 5 个布局里是一个纯文字全屏 TextView `啥都木有~`，无图标、无色、无重试按钮；一级页基类 `RefreshMainActivity` **完全不管** `emptyTip`（`activity_simple_main_refresh.xml:59-66` 里的 `emptyTip` 无人调用）。
- 加载态：`activity_loading.xml` 是一个 128dp 的 `loading_2233` 图片铺满全屏（`activity_loading.xml:39-46`），`asyncInflate` 用它顶替真实布局再替换——没有骨架屏，长列表页首屏是"一张大图闪一下"。
- 转场：全项目只有 `InstanceActivity.kt:21` 一处 `overridePendingTransition(R.anim.anim_activity_in_down, 0)`；`ModernWindowAnimation` 定义了却没人用 → 一级页有下滑入场，二级页是系统默认，动效不统一。

### 1.5 系统栏与安全区（沉浸式）

`ThemeManager.applyWindowTheme()`（`ThemeManager.kt:395-414`）：

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)   // 内容延伸到系统栏下方
window.statusBarColor = STATUS_BAR_COLOR
window.navigationBarColor = NAV_BAR_COLOR
val flags = window.decorView.systemUiVisibility
window.decorView.systemUiVisibility = flags or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()   // ← 问题所在
```

- `SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()` = `0xFFFFDFFF`，与 `flags` 相或等于**把几乎所有 systemUiVisibility 位都置 1**，同时**恰好没置** `LIGHT_STATUS_BAR`。意图明显是"清除 LIGHT_STATUS_BAR"，写法却写成了"设置除它以外的全部标志"。实测置位/清除结果：

  ```
  置位: LOW_PROFILE, HIDE_NAVIGATION, FULLSCREEN, LIGHT_NAVIGATION_BAR,
        LAYOUT_STABLE, LAYOUT_HIDE_NAVIGATION, LAYOUT_FULLSCREEN, IMMERSIVE, IMMERSIVE_STICKY
  清除: LIGHT_STATUS_BAR
  ```

  由此产生三个真实后果：
  1. **状态栏图标恒为浅色（白）**，而 7 套主题的状态栏底色全是品牌色（`ThemeManager.kt:103,142,181,220,259,298,340`）——爱奇艺绿 `#00DC5A` 上白图标对比度约 **1.6:1**，经典灰 `#8787FB`、B站粉 `#FF6699` 同样偏低。
  2. **导航栏图标被强行置为深色**（`LIGHT_NAVIGATION_BAR` 被打开），而导航栏底色是 `#000000`/`#1B1B24` → **导航栏图标等于看不见**。
  3. 附带无条件打开 `HIDE_NAVIGATION`/`IMMERSIVE`/`IMMERSIVE_STICKY`/`FULLSCREEN` 等沉浸式标志 —— 这不该由"主题代码"顺手决定。
- 全工程**没有任何 insets 处理**（grep `WindowInsets|fitsSystemWindows|setOnApplyWindowInsetsListener` 除 `ThemeManager.kt` 自身外 0 命中）。`setDecorFitsSystemWindows(false)` + 无 insets + `android:windowFullscreen=true`（每套主题都有，`themes.xml:5,45,79,94,137,209,252,324,367,439,482,554,597,671,705`）叠加的结果：**播放器底部进度条（`activity_player.xml:188-196` 贴 `alignParentBottom`）被三键导航栏压住**、手势导航设备上列表最后一项被压住、刘海/挖孔屏横向无避让、也没有 `clipToPadding=false` + 底部 padding 的保护。
- 启动闪屏：`AndroidManifest.xml:567` 用 `Theme.BiliClient.Splash`，`splash_text.xml` 的 windowBackground 是 `@color/background_dark`（`#1B1B24` 深蓝灰），而默认主题经典的背景是纯黑 `#000000` → **冷启动会看到"深蓝灰 → 纯黑"的颜色跳变**；另外 6 套非粉色主题的 `*.Splash` style 全部零引用（见 2.24）→ 开屏永远走 B站粉暗底。
- 无 `res/values-night/`，全代码库 `uiMode|MODE_NIGHT|isNightMode` 零命中 → **无法跟随系统深色模式**。

### 1.6 可维护性对视觉的间接伤害

- 47 个布局各自手写顶栏；`activity_simple_*.xml` 之间只有 `arrow_back`/`arrow_up` 一个属性之差。
- `BaseActivity.setPageName()` 依赖每个布局都有 `R.id.pageName`，`setTopbarExit()` 依赖 `R.id.top` —— 47 份复制品任何一个写错 id，页面就"点了没反应"。
- 新增一套主题的当前成本：`colors.xml` 加 ~40 色 → `themes.xml` 加 ~60 行 → `ThemeManager.kt` 加一个 41 字段 object → `BiliColors.kt` 再抄一遍 → 加 4 个 `gradient_button_bg_*`/`background_card_*` drawable → `BaseActivity`/`PlayerActivity`/`BiliTerminalApp` 三处映射。任何一步漏了就是一处观感 bug。

---

## 2. 问题清单（按严重程度）

> 每条给出【证据】【影响】【建议改法】【工作量 S/M/L】。S ≈ 半小时内、M ≈ 半天、L ≈ 一天以上。

### 2.1 【高】主题切换不生效 / 部分页面永远是 B站粉

- 证据
  - `app/src/main/res/layout/activity_player.xml:5` `android:theme="@style/Theme.BiliClient"`
  - `app/src/main/res/layout/activity_image_viewer.xml:7` 同上
  - `app/src/main/res/layout/cell_episode.xml:4` 同上
  - `activity/ImageViewerActivity.kt:28` `setTheme(R.style.Theme_BiliClient)`（在 `super.onCreate()` 之后，且覆盖了基类已按用户选择设置的主题）
  - `adapter/video/MediaEpisodeAdapter.kt:48`、`adapter/QualitySelectorAdapter.kt:56` `ContextThemeWrapper(context, R.style.Theme_BiliClient)`
- 影响：用户在设置里选"紫色空灵/经典终端/五彩斑斓"，**图片查看器、播放器内的选集与画质弹窗、番剧选集**仍然是 B站粉配色。这是"自定义主题"这一功能最重要的体验承诺，也是最容易被用户发现的破绽。
- 建议：删除这 3 处布局级 `android:theme` 与 3 处代码强制主题；`cell_episode.xml` 需要的颜色改用 `?attr/colorPrimary`、`?attr/colorSurface` 等主题属性；`ImageViewerActivity` 删掉 `setTheme`（基类已处理）。**这项工作与 2.4 的"语义色"改造是同一条链路，建议合并做。**
- 工作量：S（改 6 个文件）

### 2.2 【高】颜色/尺寸没有单一真源（`BiliColors`/`BiliDimens`/`ThemeUtils`/`modern_styles` 是死代码）

- 证据：见 1.1 全部条目；`BiliDimens` 0 引用、`ThemeUtils` 18 个方法只用 1 个、`modern_styles.xml` 0 引用、`getAppearanceStyle` 链路无 UI 无效果。
- 影响：任何"统一调整视觉"的改动都要先分辨"这套常量到底生效吗"，实际生效的是 `ThemeManager` + xml 主题两套；新写页面时照抄哪一套完全取决于作者当时看到了哪个文件 → 漂移持续放大。
- 建议：
  1. 保留 `ThemeManager` 作为**唯一**运行时常量真源；
  2. 把 `BiliColors`/`ThemeUtils` 的 3 个真实调用点改为 `ThemeManager`，然后删掉这两个文件（用 grep 收尾确认 0 引用）；
  3. 删除 `BiliDimens`、`modern_styles.xml`；
  4. 给"外观风格"一个决定：要么在设置页加上入口并真正生效（把 `getCardBackgroundColor` 接到卡片背景上），要么把 `APPEARANCE_*`/`CLASSIC_CARD_BG` 一并删掉。**建议先删**，等 token 体系稳定后作为新功能加回来。
- 工作量：M

### 2.3 【高】默认主题的文字层级为零 + 对比度不可控

- 证据
  - `ui/theme/ThemeManager.kt:315-317`：经典终端 `TEXT_PRIMARY = TEXT_SECONDARY = TEXT_TERTIARY = 0xFFEBE0E2`
  - 143 处 `android:alpha`（16 档），`0.5` 出现 48 次、`0.7` 40 次，分布在 55 个布局
  - 例：`res/layout/cell_video_list.xml:59,74` 播放量与 UP 主名 `android:alpha="0.5"` + `textSize="11sp"`，而标题只有 `12sp`（`cell_video_list.xml:45`）
  - 字号分布：75% 落在 11-13sp，最大 18sp 仅 3 处
- 影响：默认主题下"标题、播放量、UP 主"三者颜色完全相同，只靠 1sp 字号差和 alpha 区分。按 WCAG 2.1 公式实测：`#EBE0E2 @ alpha 0.5` 叠在卡片等效底色 `#1E1E1E` 上合成 `#847F80`，对比度 **≈4.24:1**，低于 AA 的 4.5:1（直接叠纯黑窗口底时 ≈4.33:1，同样不及格）—— **这就是"看着糊"的直接原因**。
- 建议：建立 4 档字号（16/14/13/12sp）+ 3 档语义文字色（`text_primary` / `text_secondary` 约 70% 亮度 / `text_tertiary` 约 50%），禁止用 `alpha` 表达层级；先把默认主题的三档文字色拆开（`#EBE0E2` / `#B9B0B3` / `#8C868A`），再批量替换高频布局里的 `alpha=0.5`。**这是全项目"观感提升/改动量"比最高的一项。**
- 工作量：L（语义色 M + 布局替换 M）

### 2.4 【高】系统栏与安全区：沉浸式副作用 + 无 insets 保护

- 证据：`ThemeManager.kt:397,405-407`（`setDecorFitsSystemWindows(false)` + `flags or LIGHT_STATUS_BAR.inv()`）；全工程 0 处 `WindowInsets`/`fitsSystemWindows`；每套主题都有 `android:windowFullscreen=true`
- 影响：① **导航栏图标被强制置深色而导航栏底色是黑的 → 图标看不见**；② **状态栏图标恒白，压在亮色品牌底上**（爱奇艺绿 `#00DC5A` 上约 1.6:1）；③ 无条件下打开沉浸式/隐藏导航栏标志；④ **播放器底部进度条（`activity_player.xml:188-196`）与列表最后一项被导航栏压住**，拖动进度条会触发系统手势；⑤ 刘海/挖孔屏横向无避让（`cell_episode`/播放器/顶栏都可能被压）。
- 建议：删掉 `ThemeManager.kt:405-407`，改用已在依赖里的 androidx API —— `WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = ColorUtils.calculateLuminance(STATUS_BAR_COLOR) > 0.5`（导航栏同理）；在 `BaseActivity`/`applyWindowTheme` 里用 `ViewCompat.setOnApplyWindowInsetsListener` 取 `systemBars`/`navigationBars` inset 做根布局 padding（`RecyclerView` 配 `clipToPadding=false`）；顺带评估 `android:windowFullscreen=true` 是否还需要（与 `setDecorFitsSystemWindows(false)` 语义重叠）。参考 `activity_player.xml:188-196` 与 `:121-130` 两处贴底元素作为验证点。
- 工作量：S（标志位修正）+ M（insets padding）
- 备注：这是**唯一一处"一行改动、全局可见"**的问题，建议排在所有视觉改动最前面。

### 2.5 【中】顶栏：47 份复制品，只有约 23dp 高，却是 45 个页面的唯一返回入口

- 证据：47 个布局含 `@+id/top`、45 个含 `@+id/pageName`；`res/layout/cell_topbar.xml` 已有公共实现但 **0 处 `<include>`**；顶栏根是 `wrap_content` + `android:layout_marginVertical="3dp"`（`activity_simple_main_refresh.xml:19`、`activity_simple_refresh.xml:19`），标题走全局 `TextViewStyle` 14sp（`styles.xml:131-133`），实测整条**约 23dp**；返回箭头 `drawable/arrow_back.xml:3-4` 仅 11dp。已漂移的例子：`activity_search.xml:99-111` 的 `pageName` 缺 `lines="1"`，长标题会换行撑高顶栏。
- 影响：`BaseActivity.kt:132-141 setTopbarExit()` 让**整条顶栏**点击就 `finish()`，即 45 个页面共用约 23dp 的热区（不到 48dp 标准的一半）；而列表紧贴其下（`layout_below`），极易误触进内容。同时标题与时钟同为 14sp 加粗（`TextClock.kt:32` `Typeface.DEFAULT_BOLD`），层级只能靠位置区分，且 `arrow_down` 只是图标、没有任何点击态/ripple。
- 建议：把 `cell_topbar.xml` 补全为 `minHeight=48dp` + `gravity=center_vertical`、标题 16sp、箭头 16–20dp、底部 1dp 分割线、`?attr/selectableItemBackground`，再按批替换 47 处 `<include>`（先 `activity_simple_*` 七个基类布局）。
- 工作量：S（基类 7 处）+ L（全量 47 处）

### 2.6 【中】间距/圆角/内边距无节奏

- 证据：`dimens.xml` 仅 8 个 token（`activity_padding_horizontal=6dp`、`list_margin_vertical=2dp`）；圆角 9 档（12dp/6dp/8dp/28dp/1/3/15/20/25dp）；卡片内边距 `cell_video_list.xml:12-14` 为 `paddingHorizontal=10dp` + `paddingVertical=8dp`，而间距又在 4/6/8/10/12dp 之间摇摆
- 影响：同一列表里不同卡片的留白不一致，滚动时"卡片高低不齐"的观感；圆角在经典终端主题（6dp）和其它主题（12dp）之间跳变。
- 建议：把栅格定为 4dp 基准，扩 `dimens.xml` 为语义 token（`space_xs=4 / sm=8 / md=12 / lg=16 / xl=24`、`radius_card=12 / radius_chip=20 / radius_small=8`、`touch_min=48`），卡片左右统一 `16dp`、卡片内边距统一 `12dp`；统一圆角为 12dp（经典终端也改 12dp，或明确接受 6dp 并同步到所有主题）。
- 工作量：M

### 2.7 【高】列表图片：画质被双重限死、失败无兜底、占位图闪白

- 证据（**画质**）
  - `GlideUtil.java:32-41`：`url()` 给**每一张**图无条件追加 `@0e_25q_512w.webp`（质量 25、宽上限 512px）；高质量版 `url_hq()`（`GlideUtil.java:45-61`，80q/1024w）**只被 `ImageViewerActivity.kt:61` 调用**一次。
  - `adapter/video/VideoCardHolder.kt:147-154`：`.override(400, 225)` + `.sizeMultiplier(0.85f)` → 实际解码 ≈340×191 **绝对像素**，而列表封面显示区约 519px 宽 → 必然 1.5× 上采样。同类硬编码还有 `DownloadAdapter.kt:144`（320×180）、`DynamicHolder.kt:492,576`、`PrivateMsgAdapter.kt:146,256`。
- 证据（**统一封装形同虚设**）：全工程 55 个图片加载点里，只有 **2 处**（`RecentUpAdapter.kt:28`、`HotSearchAdapter.kt:53`）走 `GlideUtil.request*`，其余 ≈50 处各写各的链式 Glide，`GlideUtil` 实际只统一了 URL 归一化。
- 证据（**失败无兜底**）：全工程 `.error(` **0 匹配**；`GlideUtil.java:63-90` 三个方法只设 `placeholder`。`HotSearchAdapter.kt:53` 甚至传入 `placeholder=0`（等于无占位）。
- 证据（**占位图与背景冲突**）：`placeholder.png` 实测 256×144、平均色 **#DFDFDF**；`akari.png` 256×256、平均色 #B7B7B7。而默认主题窗口底是**纯黑 `#000000`**、卡片 `#cc262626`。占位图比例 16:9，而 `cell_opus.xml:27`、`cell_dynamic_video.xml:26`、`cell_article_list.xml:28` 是 16:11 → 占位期与真图比例不一致。
- 影响：① 封面普遍发虚、色带（`GlideUtil.java:66,75,86` 统一 `DecodeFormat.PREFER_RGB_565`）；② 图片请求失败时 RecyclerView 复用会**显示上一个 item 的封面**（串图，属 bug 级观感问题）；③ 每次进列表/滚动都在纯黑底上闪一块近白矩形，是"廉价感"的首要来源；④ 占位比例不符会导致真实图片落位时再跳一次。
- 建议：`url()` 参数化（列表封面 80q/1024w，头像单独低值）；删掉 `override` 的硬编码像素，交回 Glide 按 ImageView 实测尺寸解码；`GlideUtil` 三个方法内统一补 `.error(placeholder)`；出一套暗色中性占位图（`#2A2A2A`~`#333`）并补齐 16:9 / 16:11 / 1:1 三种比例。
- 工作量：S（画质与 error 兜底）+ M（重做占位图资源）

### 2.7b 【中】列表文字层级的真实取值：同类卡片横跨 11–15sp、副标题 alpha 有 6 种

- 证据（标题字号）：`cell_video_list.xml:43-45` 12sp/3 行 ／ `cell_video_local.xml:56-58` **11sp**/3 行 ／ `cell_dynamic_video.xml:101-103` 12sp ／ `cell_article_list.xml:106-109` 12sp ／ `cell_opus.xml:83-85` 12sp/2 行 ／ `cell_dynamic.xml:46-54` 13sp 加粗不限行 ／ `cell_favorite_folder_list.xml:55-57` 11sp **单行** ／ `cell_timeline_episode.xml:33-34` 14sp ／ `cell_article_head.xml:94-95` 15sp 加粗不限行 ／ `cell_user_info.xml:20-30` **未设** ／ `item_account.xml:30-33` 15sp ／ `item_menu_setting.xml:21` 15sp
- 证据（副标题 alpha 6 种）：0.5（`cell_video_list.xml:59,74`、`cell_opus.xml:60`、`cell_article_list.xml:75,87`）／0.7（`cell_dynamic.xml:39`、`cell_message.xml:35`、`cell_reply_list.xml:37`）／0.75（`cell_video_local.xml:64`）／0.8（`cell_up_list.xml:51`）／0.86（`cell_user_info.xml:40`）／0.9（`cell_message_reply.xml:40`）
- 证据（头像 4 种策略）：35dp（`cell_reply_list.xml:11-12`）／40dp（`cell_user_list.xml:21-22`）／56dp（`item_account.xml:17-18`）／百分比（`cell_up_list.xml:25`、`cell_article_head.xml:33`）／**宽度高度都是 0dp 且无基准**（`cell_user_info.xml:9-18`）
- 影响：同一屏内同类内容字号忽大忽小；`cell_favorite_folder_list` 收藏夹名被限成 1 行而信息重要，`cell_article_head`/`cell_dynamic` 标题无行数上限会把卡片撑高破坏节奏。
- 建议：定 `textAppearance` 层级（标题 13sp/2 行、次级 12sp、辅助 12sp 起）并统一 `@dimen`；标题统一 `maxLines=2` + `ellipsize=end`。
- 工作量：L（20+ 布局，分步做）

### 2.7c 【中】分割线近乎不可见，且同页两个值

- 证据：`cell_dynamic.xml:182`、`cell_reply_list.xml:200`、`cell_message.xml:44`、`cell_message_reply.xml:32` 用写死的 `#318C8C8C`；`cell_user_info.xml:81` 却是 `#818C8C8C`，同文件 `:215,:327` 又是 `#318C8C8C`
- 量化：`#318C8C8C` 叠卡片底 `#1E1E1E` → 合成 `#333333`，对比度 **≈1.32:1**（非文本 UI 组件 WCAG 要求 ≥3:1）
- 影响：长列表失去节奏；同页深浅不一的线显得脏。主题里其实有 `DIVIDER` token（`ThemeManager.kt:336`）没人用。
- 建议：统一到一个语义 divider（暗色下透明度提到 0x50 左右），优先用主题 token。
- 工作量：S

### 2.8 【中】状态反馈：空态/加载态/加载更多/刷新色/转场

- 证据
  - 空态：`activity_simple_refresh.xml:52-59`、`activity_simple_main_refresh.xml:59-66`、`activity_simple_main_list.xml:46`、`fragment_simple_list.xml:19`、`fragment_simple_refresh.xml:19` 五处手写 `啥都木有~`；`RefreshMainActivity` 不管理 `emptyTip`；`RefreshListActivity.showEmptyView()` 只切 `visibility`
  - 加载更多：`RefreshMainActivity.kt:82`、`RefreshListActivity.kt:138` 复用 `isRefreshing` 表示翻页 → 顶部转圈；无 footer、无"没有更多了"
  - 刷新色：全项目 0 处 `setColorScheme` → 转圈颜色与主题无关
  - 加载态：`activity_loading.xml:39-46` 只有一张 128dp 大图
  - 转场：仅 `InstanceActivity.kt:21` 一处 `overridePendingTransition`
- 影响：这是"完成度"观感的来源。现在：空列表是空白、翻页是顶部莫名转圈、下拉刷新是系统蓝色、页面切换动效一半有一半没有。
- 建议：做一个 `EmptyStateView`（图标 + 主文案 + 次要说明 + 可选重试按钮）替换 5 处并接进两个基类；底部 footer（加载中 / 没有更多了）；`setColorScheme(PRIMARY)`；`activity_loading.xml` 换成细进度条或骨架；给 `RefreshListActivity`/`BaseActivity` 统一补上转场 anim。
- 工作量：L

### 2.9 【低】启动闪屏与图标

- 证据：`AndroidManifest.xml:567` + `splash_text.xml:3` = `@color/background_dark`（`#1B1B24`）对比默认主题纯黑 `#000000`；**文字与图标重叠**——`activity_splash.xml:8-14` 的 TextView 是 `match_parent + gravity=center`，而 `drawable/splash_text.xml:3-8` 又把 128dp 图标居中铺在同一位置；`AndroidManifest.xml:29` `@mipmap/icon`，`res/` 下只有 `mipmap-nodpi/icon.webp`，**无 `mipmap-anydpi-v26` 自适应图标**
- 影响：冷启动有一次轻微颜色跳变、首屏"图标被字压住"；启动器图标在 Android 8+ 上被缩放到白色/系统底板里，第一眼印象差。
- 建议：`splash_text.xml` 背景改为运行时可达的主题色（或改用 `windowSplashScreenBackground` 兼容写法），图标与文字错开（图标上移或文字加 `paddingTop`）；补一套自适应图标（前景 + 背景 + `mipmap-anydpi-v26/ic_launcher.xml`），复用现有 `icon.webp` 生成前景。
- 工作量：S

### 2.10 【低】颜色命名体系：309 个颜色里 83.5% 是别名

- 证据：`colors.xml` 共 **309 个** `<color>`，按值分组后 **60 个值组有 ≥2 个名字，涉及 258 个颜色名**（只有 51 个唯一）。同一个 `#FF6699` 有 **15 个名字**（`pink_brand`/`pink`/`link`/`theme_color`/`colorPrimary`/`bili_primary`/`purple_primary`/`terminal_accent`…），`#FF8CB0` 11 个，`#056DE8`/`#FF6B6B`/`#FFB800` 各 10 个；`res/` 内仍有 **61 处**引用旧别名（`bgblack` 16 次、`pink` 8 次、`color_ripple` 5 次、`textwhite` 5 次…）。另有 6 个**零引用**颜色：`terminal_surface`、`terminal_text_primary`、`terminal_divider`、`terminal_warning`、`terminal_gold`、`terminal_vip`。
- 影响：改一处颜色常要 grep 十几个名字，极易漏改；"值相同 ≠ 语义相同"（`code_bg` 与 `background_dark` 同为 `#1B1B24` 但用途不同），随手复用会埋暗雷。
- 建议：分三步，(a) 给旧别名加 `@deprecated 迁移到 X` 注释 → (b) 逐文件替换引用为语义色并编译 → (c) 引用清零后再删定义。**不要直接删**：`themes.xml:80,138,253,368,483`（5 套 `NoSwipe` 的 `windowBackground`）用 `bgblack`、`styles.xml:29,43` 用 `pink` 作 `ButtonStyle.backgroundTint`，直接删会编译失败。
- 工作量：L

### 2.11 【高】用 `sp` 当布局高度（36 处）

- 证据：`fragment_qr_login.xml:88,128,150,172`、`activity_write_reply.xml:73,92,110`、`activity_send_dynamic.xml:73,91,165`、`fragment_video_info.xml:74,106`、`activity_player.xml` 之外的 `activity_setting_player_choose.xml:229,239` 等，共 32 处 `layout_height="35sp"`（另 `activity_tutorial_manager.xml:63,79,103,121` 有 40sp/30sp）
- 影响：高度随系统字体缩放变化 —— 用户把字体调大，输入框/按钮被压扁或文字溢出。这是**功能性缺陷**（不只是观感），也是全项目最容易修的一类问题。
- 建议：`dimens.xml` 加 `row_height=35dp`，批量替换为 dp。
- 工作量：S

### 2.12 【高】静态色引用与硬编码色导致主题"只换了一半"

- 证据：布局里引用**静态**调色板（不随主题变）共 86 处：`panel_video_settings.xml:8,27,60,79,98`（`card_dark_secondary`/`divider_dark`）、`:18,45,70,89`（`pink_light`）；`cell_video_folder.xml:68,79,88,107`；`dialog_new_folder.xml`、`dialog_folder_settings.xml`（各 4～5 处）；`activity_update.xml`（8 处）；`activity_vote_info.xml`（6 处）；`bottom_bar_multi_select.xml`、`bar_quality_select.xml`、`item_account.xml`、`activity_player.xml:96,438,473,487,514,542,563` 等
- 影响：切到知乎蓝/爱奇艺绿/紫色空灵/经典灰时，**这些元素仍是 B站粉**（`pink_light=#FFB3CA`、`card_dark_secondary=#30303D`）。用户对"主题"的第一印象就毁在这里。
- 建议：改 `?attr/colorPrimary`/`?attr/colorSurface`/`?attr/colorOnSurface`（`background_card.xml:4-6` 已是正确示范）；先攻上述 5 个文件（占粉色残留的大头）。
- 工作量：M

### 2.13 【高】无障碍：117 个图片控件里 112 个缺 `contentDescription`

- 证据：`ImageView` 91 个缺 86；`ImageButton` 26 个缺 26；全仓库仅 5 处有（`activity_loading.xml:46`、`cell_loading.xml:9`、`cell_follow_group.xml:46`、`fragment_qr_login.xml:55`、`item_vote_option.xml:20` 为 `@null`）。最集中：`activity_player.xml`（16 个）、`activity_message.xml`（10 个）、`fragment_short_video_page.xml`（8 个）
- 影响：TalkBack 只读"按钮/图片"，语音用户无法使用播放器控制栏。属于"视觉/交互体验"的一部分，也是上架合规项。
- 建议：装饰图统一 `contentDescription="@null"` + `importantForAccessibility="no"`；功能图补中文描述（照 `fragment_qr_login.xml:55` 的写法）。
- 工作量：M（机械但量大）

### 2.14 【高】触控目标低于 48dp（12+ 处）与零反馈点击（5 处）

- 证据·过小：`activity_player.xml:311,320,329,338,348,358,368,389,398,407,418`（28dp × 11）、**进度条 `activity_player.xml:198-210`（`layout_height=20dp`，第 202 行）**、**弹幕发送 `activity_player.xml:643-651`（35dp）**、`bottom_bar_multi_select.xml:26,39,52,65`（32dp × 4）、`fragment_qr_login.xml:105`（28dp）、`activity_captcha_webview.xml:18,31,44`（28dp）、`dialog_new_folder.xml:51,64` / `dialog_folder_settings.xml:76`（36dp）、`bar_quality_select.xml:13-20`（40dp）；顶栏整体约 23dp 见 2.5。
- 证据·无反馈：`activity_live_info.xml:54`、`cell_collection_info.xml:37-44`、`fragment_video_info.xml:21`（`ImageView` 可点击但无 foreground）、`cell_action_button.xml:2-6`（背景是纯 shape，无 ripple）、`fragment_qr_login.xml:90-94`
- 影响：播放器控制按钮是最高频操作，28dp 触摸区在移动端必然误触；进度条 20dp 几乎无法精确拖动（且它还被导航栏压住，见 2.4）；点按无反馈会被当成"卡了"。
- 建议：保留视觉尺寸，加 `android:minWidth/minHeight="48dp"`（进度条改 `height=48dp` + `paddingVertical=14dp`）或 `TouchDelegate`；无反馈处加 `?attr/selectableItemBackground`，纯 shape 背景改成 `<ripple>` 包 shape。
- 工作量：S

### 2.15 【中】同构布局重复，改一处要改多份

- 证据（骨架完全相同）：`cell_coin_log.xml` = `cell_exp_log.xml`；`cell_article_hr.xml` = `cell_divider.xml`；`cell_choose.xml` = `cell_create_folder_button.xml` = `cell_goto.xml`；`activity_setting_menu.xml` = `activity_simple_list.xml`；`cell_article_image.xml` = `cell_dynamic_image.xml`；`cell_episode.xml` = `cell_item_vertical.xml`
- 证据（同构 9 份、仅 id 不同）：`activity_player_jump.xml`、`cell_action_button.xml`、`cell_article_heading.xml`、`cell_article_textview.xml`、`cell_interaction_choice.xml`、`cell_lyric_line.xml`、`cell_reply_child.xml`、`item_menu_setting_footer.xml`、`item_menu_setting_header.xml`
- 证据（视频卡三兄弟风格已漂移）：`cell_video_list.xml`（标题 12sp、副标题单行）vs `cell_video_local.xml`（标题 11sp、10sp 小字）vs `cell_video_folder.xml`（文件夹名 14sp bold）
- 影响：与已知的"视频卡片解析重复 7 份"同源 —— 视觉改动的成本被平方放大。
- 建议：保留一个基线 + `<include>`/`style`；`layout-v17/`、`layout-v22/item_hot_search.xml` 在 minSdk 24 下**是死资源**，可直接清理。
- 工作量：M–L

### 2.16 【低】杂项：单位、透明色、旧站蓝、全局 clickable

- `layout_height="35sp"` 之外还有 px/0px 单位混用：`cell_video_local.xml:84` `0px`、`activity_download.xml:23` `0px`、`activity_player.xml:17,18` `1px`、`cell_article_hr.xml:4` `1px`、`cell_date_divider.xml:11,27` `1px`
- 透明色三种写法：`#0000`×6、`#6000`×2（**是半透明黑，不是透明**：`activity_search.xml:129`、`activity_simple_viewpager.xml:58`）、`#00000000`×8
- 旧站蓝残留：`drawable/zoom_btn_bg.xml:4` `#00a1d6`，被 `activity_captcha_webview.xml:22,48` 使用，与 7 套主题的强调色都不匹配
- `styles.xml:10-11` 给**所有** 139 个 `MaterialCardView` 强制 `clickable/focusable=true`，纯展示卡片也会抢焦点、显示 ripple、被 TalkBack 报成可点击
- 占位文案残留：`cell_video_list.xml:44`、`cell_dynamic.xml:52`、`cell_opus.xml:85` 等 11 处 `android:text="标题标题…"`（建议改 `tools:text`）
- 工作量：S

### 2.17 【高】高频列表"点下去零反馈"

- 证据（根不是 `CardView`、又在代码里设了点击 → 完全没有前景/背景反馈）
  - `cell_dynamic.xml:2`（根 ConstraintLayout，全文件无 `foreground`/ripple）+ `adapter/dynamic/DynamicHolder.kt:607-620`
  - `cell_reply_list.xml:2` + `adapter/ReplyAdapter.kt:289`
  - `cell_user_list.xml:3-10`（只用 `bg_card_rounded`，非 ripple）+ `adapter/user/UserListAdapter.kt:99`
  - `cell_recent_up_list.xml:2` + `adapter/dynamic/RecentUpAdapter.kt:39`
- 对照：`MaterialCardView` 作根的卡片能从 `styles.xml:6`（`android:foreground="?android:attr/selectableItemBackground"`）拿到 ripple → **同一个 App 里一半列表有反馈、一半没有**
- 影响：动态页、评论页、用户列表、最近更新这四个高频列表点下去毫无反应，用户会认为"卡了/没点中"。
- 建议：给这四个根布局加 `android:foreground="?android:attr/selectableItemBackground"`（`cell_user_list.xml` 有圆角背景，需配 `clipToOutline`）。
- 工作量：S

### 2.18 【高】一级页空数据时是"纯黑无字"

- 证据：`activity/base/RefreshMainActivity.kt` 全文 101 行**没有 `emptyView` 字段、没有 `showEmptyView()`**；而它的布局 `activity_simple_main_refresh.xml:59-66` 里**有** `@+id/emptyTip`（写着"啥都木有~"）却无人 `findViewById` → 死视图。对照二级页 `RefreshListActivity.kt:19,29,94-110` 是正确接线。
- 证据（空结果被静默吞掉）：`activity/video/RecommendActivity.kt:69-74` 空数组直接 `setRefreshing(false); return`。
- 证据（受影响的一级页）：`RecommendActivity.kt:17`、`DynamicActivity.kt:25`、`RecommendLiveActivity.kt:12`、`HotSearchActivity.kt:13`（后两者全文无任何空态处理）。
- 证据（无骨架屏）：`grep Skeleton|Shimmer` **0 匹配**；`activity_loading.xml:39-46` 只有一张整屏大图。
- 影响：推荐 / 动态 / 直播推荐 / 热搜在无数据时用户看到纯黑列表，无法区分"加载完了但没内容"和"卡死了"。而首页正是用户最先看到的页面。
- 建议：把 `RefreshListActivity` 的空态实现照抄进 `RefreshMainActivity`，4 个子类补调用；空态组件统一为"图标 + 主文案 + 次要说明 + 可选重试"。
- 工作量：S

### 2.19 【高】视频卡片封面比例声明失效 → 滚动时行高持续抖动

- 证据：`cell_video_list.xml:23-33` 的封面 `app:layout_constraintDimensionRatio="16:9"`（`:33`）写在 **LinearLayout 父容器**（`:16-21`）里 —— **ConstraintLayout 专属属性在 LinearLayout 下完全失效**；同时 `layout_height="wrap_content"` + `adjustViewBounds="true"` + `scaleType="fitCenter"` 让行高由图片自身比例决定。`cell_video_local.xml:41` 同病。
- 生效与失效对照：`cell_dynamic_video.xml:26`、`cell_opus.xml:27`、`cell_article_list.xml:28`、`cell_dynamic_article.xml:26` 为 16:11（有效）；`cell_favorite_folder_list.xml:29` 16:9（有效）；`cell_timeline_episode.xml:16-17` 固定 80×60dp（4:3）
- 影响：图片解码完成后行高突变、整屏弹跳；且不同视频封面比例不一 → **每一行高度都可能不同**，长列表滚动像"呼吸"。这是最典型的 CLS（布局偏移）。
- 建议：封面一律放进 ConstraintLayout 用 ratio 表达，或固定高度 + `centerCrop`；统一视频 16:9、专栏/动态 16:11。
- 工作量：M

### 2.20 【中】失效的属性声明与死约束（技术债，会让后续视觉改动"改了没用"）

- `cell_article_head.xml:2` 根是 LinearLayout，但子元素仍带 `app:layout_constraint*`（`:16-18,:96-98`），并引用**本文件未定义**的 `@+id/img_cover`（`:18`）、`@+id/like_coin_fav`（`:98`）
- `cell_recent_up_list.xml:11` 把 `android:orientation="horizontal"` 写在 `RecyclerView` 上（非该控件属性）
- `cell_user_info.xml:9-18` 头像宽高都是 `0dp` + 仅 1:1 比例，**没有基准尺寸**
- `cell_favorite_folder_list.xml:15` 内层 `layout_height="match_parent"` 而根 `CardView` 是 `wrap_content`；`:34-46` 的 Guideline 命名与属性相反
- `cell_up_list.xml:25` `layout_constraintHeight_percent="0.7"` + 1:1 与"卡片高度依赖文字"形成约束环
- `cell_private_msg.xml:33-38` 用 `layout_width="wrap_content"` include `cell_video_list`，而后者内部封面用 `0dp` + `weight 0.48`（依赖父为 `match_parent`）→ 私信里的视频卡宽度失去基准
- 建议：逐条改为有效容器内的声明；头像/封面改固定 dp；`include` 改 `match_parent`。
- 工作量：M

### 2.21 【低】layout-v17 / layout-v22 是死资源

- 证据：`res/layout-v17/item_hot_search.xml`、`res/layout-v22/item_hot_search.xml` 与 `res/layout/item_hot_search.xml` 仅差一个 margin/padding，且三份都硬编码 `#fb7299`；minSdk 24 → 两个变体目录永远不会被选用
- 建议：删除两个目录，统一到 `layout/`，顺带把 `#fb7299` 换成主题色。
- 工作量：S

### 2.22 【高】主题色值本身有 5 处互相矛盾（"切了主题但观感不对"的深层原因）

逐项把 `ThemeManager.kt`、`themes.xml`、`colors.xml` 归一化成 ARGB 比对后的结果：

| # | 问题 | 证据 | 影响 |
|---|---|---|---|
| a | **默认主题 `Theme.ClassicTerminal` 是全项目定义最残缺的一套**：唯一没有 `colorSurface` / `android:colorBackground` / `colorSecondary` / `colorPrimaryVariant` 的主题；`colors.xml:488 terminal_surface` 定义了却零引用；`CardStyleTerminal`（`themes.xml:727-734`）**没有 `strokeColor/strokeWidth`**，其余 6 套都有 1dp 描边 | `themes.xml:670-695`、`:727-734`、`colors.xml:488` | 默认主题下 Material 控件 surface、按钮选中态回退到 Material 默认值，卡片没有边框 —— 用户最先看到的皮肤反而是最"糙"的 |
| b | `colorPrimaryDark` 与 Kotlin 表不符：`terminal_pink`(`#FF6699`) vs `ThemeManager.kt:308` `#E84B85`；卡片色丢 alpha：`terminal_card_bg`(`#CC262626`) vs `ThemeManager.kt:312` `#FF262626` | `themes.xml:677,730` | 同一张"卡片"走 xml 与走代码两条路径观感不同 |
| c | **知乎蓝主题的涟漪色是粉色**：`themes.xml:119-120` 用 `@color/color_ripple`（`#30FF6699`），而 `colors.xml:186 zhihu_color_ripple`（`#30056DE8`）已定义却没用；其余 5 套都正确用了各自的 `*_color_ripple` | `themes.xml:119-120` vs `:234-235,349-350,464-465,579-580,680-681` | 知乎蓝下所有开关/单选/涟漪都是粉色 |
| d | **五彩斑斓主题的 `PRIMARY_LIGHT` 与 `SECONDARY` 在两张表里互换** | `ThemeManager.kt:228-229` vs `themes.xml:446,448` | 代码路径与 xml 控件的强调色相反 |
| e | `colorOnPrimary`（xml，带色偏的 `*_text_primary_dark`）与 `ON_PRIMARY`（Kotlin，纯白 `#FFFFFF`）在 **6/7 套主题**下不一致 | `ThemeManager.kt:81,120,159,198,237,276` vs `themes.xml:9,98,213,328,443,558` | 品牌色按钮上的文字发灰，两条渲染路径白度不同 |

- 另：`BiliColors.kt` 与 `ThemeManager.kt` 的常量也不一致 —— `SurfaceDark` `#24242E` vs `#262626`、`CardDark` `#2A2A35` vs `#262626`、`BackgroundDark` `#1B1B24` vs `#000000`、`CoinColor` `#FFE66D` vs `#FFB800`、`PlayerControlBg` `#33000000` vs `colors.xml:64 #4D000000`（详见 2.2）。
- 建议：随批次 1/2 一起修：补 `Theme.ClassicTerminal` 缺的 4 个 item + `CardStyleTerminal` 描边；`themes.xml:119-120` 改 `zhihu_color_ripple`；统一 Rainbow 的映射；补 `*_on_primary = #FFFFFF` 并改 6 处 xml。
- 工作量：S

### 2.23 【高】播放器 / 开屏 / 外链入口是"裸 Activity"，完全不在主题体系内

- 证据
  - `activity/player/PlayerActivity.kt:112` `class PlayerActivity : Activity()`（**不是** `BaseActivity`），manifest 主题 `AndroidManifest.xml:165` `Theme.NoSwipe`（parent `android:Theme.Black`）；它在 `:353-363` **复制粘贴**了 `BaseActivity.kt:65-75` 的同一份主题 `when`，且**不调用** `applyWindowTheme` → 状态栏/导航栏不受主题控制；它也不参与 `BaseActivity.kt:253-257` 的"主题变了就 `recreate()`"检测。
  - `activity/SplashActivity.kt:34` 同为裸 `Activity`，manifest 用 `Theme.BiliClient.Splash`。
  - `activity/GetIntentActivity.kt:11` 裸 `Activity`、无 manifest 主题 → 落到应用级 `Theme.BiliClient`（B站粉），而 `ThemeManager.THEME_DEFAULT` 是经典终端 → **默认就不一致**。
  - `ImageViewerActivity`（`AndroidManifest.xml:370`）用 `Theme.NoSwipe`（`android:Theme.Black`）→ 其中的 Material 控件退化为默认外观。
- 影响：切主题后播放器、图片查看器、开屏、外链入口观感不变；播放器是使用时长最长的页面。
- 建议：三者改继承 `BaseActivity`（或抽一个 `ThemeAwareActivity` 基类），删掉 `PlayerActivity.kt:353-363` 的重复 `when`，manifest 的 `Theme.NoSwipe` 换成**已定义好却零引用**的 `Theme.*.NoSwipe.AppCompat`。
- 工作量：M

### 2.24 【中】28 个 style 定义了没人用；6 套主题的 Splash/NoSwipe 变体从未接线

- 证据：`modern_styles.xml` **整个文件**（`ModernWindowAnimation`/`ModernButton`/`ModernCard`）零引用；`styles.xml:56,70,84,140` 四个未引用 style（含拼写错误的 `TextViewSytle`）；主题侧 30 个未引用 style，全部是 6 套非粉色主题的 `*.Splash` / `*.Dark` / `*.NoSwipe` / `*.NoSwipe.AppCompat`（`themes.xml:40,44,125,129,133,143,240,244,248,258,355,359,363,373,470,474,478,488,585,589,593,603,697,701,711`）。另 `Theme.ClassicTerminal` 连 `.Dark` 都没定义（其余 6 套都有）。
- 影响：开屏、对话框窗口主题永远走 B站粉基线；`modern_styles.xml` 会让人误以为存在一套"现代动效/按钮"规范。
- 建议：与 2.23 一起接线或删除；`modern_styles.xml` 可直接删。
- 工作量：S

### 2.25 【中】视频详情页：三连卡对比度约 2.1:1，且字号层级倒置

- 证据
  - 三连卡：`fragment_video_info.xml:200-212`（`cardBackgroundColor="?attr/colorPrimary"` 第 205 行）+ `:229-234,252-258,276-282`（标签 12sp 无显式 `textColor`，走主题近白 `#ebe0e2`）→ `#EBE0E2` 叠 `#FF6699` **对比度约 2.1:1**（AA 要求 4.5:1），是详情页最高频操作区。
  - 层级倒置：全局正文 14sp（`styles.xml:131-133`）导致"页面标题 = 正文"；视频标题 13sp（`fragment_video_info.xml:51-57`）小于 UP 主名 14sp 加粗（`cell_up_list.xml:29-41`）；设置条目 12sp（`cell_setting_nav.xml:26-39`）小于分组头 14sp 加粗（`cell_setting_title.xml:9-16`）。
  - 简介折叠没有可发现入口：`fragment_video_info.xml:180-191` 只有 `maxLines=3` + `ellipsize`，靠 `VideoInfoFragment.kt:330-335` 直接改 `maxLines`，界面上没有"展开 ∨"提示。
  - 提示卡高度写错：`fragment_video_info.xml:59-81` 在垂直 LinearLayout 里用了 `layout_height="match_parent"`（第 62 行），应为 `wrap_content`。
  - 次要文字：`:127,139,152,163,174` 用 `alpha=0.5`，而同页 `:131` 又硬编码 `#fff` —— 同一组信息两种写法。
- 影响：这是全报告**唯一可量化的 AA 合规问题**（2.1:1），且详情页是核心页面。
- 建议：三连卡改 CARD 底 + 1dp 主色描边（或标签文字改深色）；建立 `TextAppearance` token 修层级；补"展开 ∨"与 150ms 高度过渡。
- 工作量：S（对比度）+ L（层级体系）

### 2.26 【中】其余重点页面的具体问题

| 页面 | 问题 | 证据 |
|---|---|---|
| 播放器 | 控件热区远小于 48dp：进度条 `layout_height=20dp`、弹幕开关/旋转/菜单 `28dp`、发送 `35dp`；进度条颜色写死 `#aa44aaff`/`#eeFEFEFE`，而 `ThemeManager.PLAYER_PROGRESS_FILL`（`:334`）无人用 | `activity_player.xml:198-210,224-241,308-315,387-411,643-651`、`:206-207` |
| 播放器 | 控制条显示/隐藏是硬切（`setVisibility`），没有淡入淡出 | `PlayerActivity.kt:740-761,765-780` |
| 播放器 | 时钟 8sp 且 `TextClock.kt:13-18` 的 `defStyleAttr=0` → 拿不到主题 `textViewStyle` | `activity_player.xml:178-183`、`TextClock.kt:13-18,31-33` |
| 搜索 | 搜索框 `minHeight=42dp`、非 pill（`background_searchbar.xml` 6dp 圆角）、隐含点区 35dp 且与图标错位；面板色写死 `#cd000000`、`cell_choose.xml:8 #dd262626` | `activity_search.xml:63-89`、`background_searchbar.xml:3-7`、`background_searchhistory.xml:3-7` |
| 搜索 | 滚动隐藏动画被截断：`ObjectAnimator` 未设 `duration`，随后 `postDelayed(200ms)` 就 `GONE` | `SearchActivity.kt:495-518`（尤其 `:503-505`） |
| 设置 | 图标尺寸不可预期：`0dp` + `constraintHeightPercent=0.4` 挂在 `wrap_content` 父级上（循环依赖）；`SwitchMaterial` 用 `match_parent` 且无 `maxLines` | `cell_setting_nav.xml:13-24`、`cell_setting_switch.xml:14-19` |
| 菜单 | 14 个满宽按钮、零间距、无图标、无选中态；横屏仍是满宽单列 | `activity_menu.xml:46-51`、`MenuActivity.kt:93-124` |
| 二维码登录 | 默认 LARGE 档 + Guideline `0.01/0.99` → 正方形容器占宽 98%，横屏/平板上超出视口高，状态文字被挤出屏 | `fragment_qr_login.xml:21-48`、`QRLoginFragment.kt:49-53` |
| 启动页 | 文字与图标重叠：`activity_splash.xml` 的 TextView 是 `match_parent + gravity=center`，而 `splash_text.xml` 又把 128dp 图标居中铺在同一位置 | `activity_splash.xml:8-14`、`drawable/splash_text.xml:3-8` |
| 横屏 | `getLayoutManager()` 固定 3 列；菜单不做横屏分支 | `BaseActivity.kt:326-331`、`MenuActivity.kt:93-124` |
| 挖孔屏 | 无 `windowLayoutInDisplayCutoutMode`、无 `displayCutout` inset 消费，顶栏从 y=0 起 | `ThemeManager.kt:397-414`（全仓 0 处 cutout 处理） |

- 建议：按批次 0（播放器热区、三连卡对比度）与批次 3/4（搜索框、设置页、二维码、横屏）分批处理；动画统一复用已有的 `AnimationUtils.fadeIn/fadeOut`（`AnimationUtils.java:14-41`），搜索页补 `duration=250` + `DecelerateInterpolator`。
- 工作量：M（分散）

### 2.27 【中】代码层硬编码颜色：68 处 / 26 个文件（切主题不变色的直接来源）

- 证据（排除 `ui/theme` 两个颜色表文件后的真实硬编码）：`Color.parseColor` + `Color.rgb/argb` 等**56 行**、8 位 hex 字面量 **12 行**。热点：

| 硬编码 | 位置 | 说明 |
|---|---|---|
| `Color.rgb(207,75,95)` ×7 | `VideoCardHolder.kt:145`、`ReplyAdapter.kt:151,225`、`StringUtil.java:282`、`VideoInfoFragment.kt:583`、`UserDynamicAdapter.kt:180`、`PrivateMsgSessionsAdapter.kt:41` | 旧版 B 站玫红，7 套主题下恒不变 |
| `#FE679A` ×3 | `DynamicHolder.kt:379,658,681` | 动态点赞激活色恒为 B站粉 |
| `0xFFFB7299` / `0xFF999999` | `HotSearchAdapter.kt:42`、`item_hot_search.xml:15` | 热搜排名色恒为官方粉 |
| `0xA8FB7299` / `0x33FB7299` | `HighEnergyProgressBar.kt:27,35` | 自定义 View 写死旧粉 |
| `#ffffff #aaaaaa #888888 #27ae60 #e67e22` | `TutorialManagerActivity.kt:177,194-199,214,230` | 教程管理页与主题完全脱钩 |
| `Color.argb(0x85,…)` / `Color.rgb(0xeb,0xe0,0xe2)` | `MsgUtil.java:135-136` | Snackbar 底色/文字写死 |
| `Color.argb(160,255,80,80)` 等 | `PlayerDanmuClientListener.kt:180,184,187` | 直播间入场/上舰弹幕背景 |
| `0xffff6699` / `0xffebe0e2` | `PageSelectorAdapter.kt:60,63` | 选集高亮 |

- 另有**布局/drawable 层硬编码 hex 251 处 / 120 个文件**（`activity_player.xml` 14、`tutorial_*.xml` 12 个文件合计 **67**、`cell_dynamic_vote.xml` 5、`cell_user_info.xml` 6、`player_tv_white.xml` 6、`player_tv_blue.xml` 5、`activity_captcha_webview.xml` 5）。
- 建议：把复用度最高的 `Color.rgb(207,75,95)` 抽到 `ThemeManager.getAccentColor()`（`ThemeManager.kt:463` 已有该方法）；教程页改语义色（`TutorialRenderer.kt:47-53` 的解析失败兜底 `Color.WHITE` 也要改）；Snackbar 与高能进度条改主题色。弹幕白字/黑阴影可保留，但需加注释说明是刻意为之。
- 工作量：M

### 2.28 【低】两个悬空功能需要给结论

- **浅色主题**：`themes.xml:44` 定义的 `Theme.BiliClient.Light` **全仓库零引用**，`BaseActivity.kt:65-75` 的 `when` 只有 7 个暗色分支；`colors.xml:27-30,39-42` 的浅色值与 `BiliColors` 的 5 个浅色常量只被这棵死子树消费。→ 用户**不可能**获得浅色界面，也无法跟随系统深色（无 `values-night`）。要么接线（Light 分支 + 设置项 + `values-night`），要么删除，别悬着。
- **"外观风格 modern/classic"**：`getCardBackgroundColor`/`getButtonBackgroundColor`（`ThemeManager.kt:457-461`）**零调用点**，`APPEARANCE_STYLE` 只被 `ThemeManager.kt:479,483` 读写，设置页无入口，`strings.xml` 搜"外观"零命中 → 这个开关**影响面为 0**。同 2.2 建议一并删除。
- **弹幕颜色**：`DanmakuManager.kt:157,168` 恒定白字黑阴影，全代码库搜 `danmaku_*color` 零命中 → 既无"弹幕颜色"设置项，也不受主题影响（白字黑边本身对可读性是合理的，可保留但不该被误认为"已跟主题"）。
- 另外两处小的单一真源问题：`SettingsKeys.kt:21` 与 `ThemeManager.kt:20` 是同一个字符串 `theme_selector` 的两个常量；`SettingGroupActivity.kt:250` 绕过 `ThemeManager.setTheme()` 直接写 SharedPreferences。
- 工作量：S

### 2.29 【低】已排除的怀疑项（避免后续重复排查）

- **WebView 白底闪屏不存在**：全项目唯一的 `WebView` 是极验验证码页（`CaptchaWebViewActivity.kt:31,114,119-132`），属第三方页面；专栏正文、动态、AI 总结都是原生 `Spannable` 渲染，没有 `Html.fromHtml`。若将来接浅色主题，该验证码页会是唯一的白底页，需要注入 `prefers-color-scheme`。
- **菜单按钮触控达标**：`MaterialButton` 默认 `minHeight=48dip`（appcompat `Base.Widget.AppCompat.Button`），所以菜单项没有 48dp 问题，真正的问题是零间距/无图标/无选中态/横屏满宽（见 2.26）。
- **`ui/widget/recycler/` 的 `AbstractAdapter`/`BaseAdapter`/`BaseHolder`、`res/layout/fragment_empty.xml`、`BiliTerminalApp.kt`** 均为死代码或空壳，与视觉无关，但会干扰阅读。

---

## 3. 为什么建议"先修基建"

若直接跳去调某个具体页面的观感，会遇到三个现实问题：

1. **同一个视觉缺陷在几十处重复**（顶栏 47 处、`啥都木有~` 5 处、`alpha=0.5` 48 处、11sp 小字 85 处、硬编码 `#fff` 47 处）——单点修不完。
2. **改了的可能没生效**（`BiliColors`/`BiliDimens`/`modern_styles` 死代码；被硬编码 theme 覆盖的 3 处页面）。
3. **没有验收标准**：现在没有任何"设计规格"可以作为 code review 依据，改完只能凭截图争论。

因此本报告把工作切成 5 批（0～4），每批独立可验证（`./gradlew.bat :app:assembleDebug` 通过 + 关键页面真机截图对比），任何一批做完都能单独交付。

---

## 4. 分批实施计划

### 批次 0：零风险速修（工作量 S、用户立刻能感知）

| # | 改动 | 对应问题 | 影响面 |
|---|---|---|---|
| 0-0 | **`ThemeManager.kt:405-407` 的 `or … .inv()` 改正确写法**（`WindowInsetsControllerCompat` 按背景亮度设 `isAppearanceLightStatusBars/NavigationBars`） | 2.4 | 一行改动、7 套主题全局可见：状态栏/导航栏图标亮度 |
| 0-1 | `GlideUtil.url()` 参数化（列表封面 80q/1024w）、删除 `override(400,225)`/`sizeMultiplier(0.85)`、所有加载点补 `.error()` | 2.7 | 全列表观感 + 修掉串图 |
| 0-2 | 36 处 `sp` 当布局高度改 dp | 2.11 | 大字号用户不再被压扁 |
| 0-3 | `activity_player.xml` 11 个 28dp 按钮 + 20dp 进度条 + 35dp 发送键、`bottom_bar_multi_select.xml` 4 个 32dp 按钮等补 48dp 热区 | 2.14 | 播放器手感 |
| 0-4 | 动态/评论/用户列表/最近更新四个根布局补 `selectableItemBackground` | 2.17 | 四个高频列表"点了没反应" |
| 0-5 | `RefreshMainActivity` 补 `emptyView/showEmptyView`，4 个一级页子类调用 | 2.18 | 首页空数据不再纯黑 |
| 0-6 | 默认主题 `TEXT_SECONDARY/TERTIARY` 拆开；列表次要文字 `alpha 0.5→0.7`、11sp→12sp；分割线统一替换；`#fff` 白字改语义色 | 2.3、2.7c、2.12 | 默认皮肤可读性 |
| 0-7 | 三连卡对比度修复（`fragment_video_info.xml:200-282`）；`Color.rgb(207,75,95)`/`#FE679A`/`#FB7299` 等高频硬编码色收敛到 `ThemeManager` | 2.25、2.27 | 详情页 + 动态/评论/热搜 |
| 0-8 | 删除 `layout-v17/`、`layout-v22/` 死资源目录 | 2.21 | 清理 |
| 0-9 | 主题色值 5 处矛盾：`Theme.ClassicTerminal` 补 4 个 item + `CardStyleTerminal` 描边、知乎蓝 ripple 改蓝、Rainbow 映射统一、`ON_PRIMARY` 对齐 | 2.22 | 主题正确性 |

- 验证：`assembleDebug` + 单测；真机看视频列表、动态、评论、播放器、首页空态、7 套主题的系统栏。
- 风险：低（0-0 是行为变更但方向明确，逐主题截图即可确认）。
- 基线：改动前 `./gradlew.bat :app:assembleDebug` 已通过（本次审计中实测，exit 0）。

### 批次 1：给视觉建 token（不改变观感，纯基建）

- 扩 `res/values/dimens.xml`：间距 5 档、圆角 3 档、字号 4 档、触控最小尺寸、卡片内边距、顶栏高度、`row_height`。
- `colors.xml` 增补语义色：`text_on_primary`、`text_secondary`、`text_tertiary`、`divider`、`ripple`、`placeholder_bg`（保留旧名做别名，逐步替换）。
- `ThemeManager` 增补对应的语义 getter；把 `BiliColors`/`ThemeUtils` 的 3 个调用点迁移过来。
- 删除 `BiliColors.kt`、`ThemeUtils.kt`、`BiliDimens`、`modern_styles.xml`、`APPEARANCE_*` 死开关（grep 确认 0 引用）；合并 `SettingsKeys.THEME` 与 `ThemeManager.PREF_KEY_THEME`，设置页改调 `ThemeManager.setTheme()`。
- 验证：`assembleDebug` + 单测；真机确认无观感变化（这一步**不应**有视觉差异）。
- 风险：低。

### 批次 2：修主题不生效（用户可感知的"功能修复"）

- 删 3 处布局硬编码 `android:theme`、3 处代码强制主题（2.1）。
- 86 处静态色引用改主题属性，先攻 `panel_video_settings.xml`、`cell_video_folder.xml`、`dialog_new_folder.xml`、`dialog_folder_settings.xml`、`activity_update.xml`（2.12）。
- 布局/drawable 层 251 处硬编码 hex 收敛（优先 `activity_player.xml` 14 处、`cell_dynamic_vote.xml`、`cell_user_info.xml`、`item_hot_search.xml`；`tutorial_*.xml` 67 处单列一批）（2.16、2.27）。
- `SwipeRefreshLayout` 上主题色；`activity_loading.xml` 与 `splash_text.xml` 改为跟随主题。
- `applyWindowTheme` 的 insets padding（0-0 已修标志位）+ `windowLayoutInDisplayCutoutMode`（2.4、2.26）。
- 验证：7 套主题各截图一遍，重点看播放器（含选集/画质弹窗/进度条与导航栏）、图片查看器、教程页、下拉刷新、底部列表项。
- 风险：中（insets 改动会影响播放器/短视频等自绘界面，需可回滚）。

### 批次 3：文字层级与卡片规格（观感提升最明显）

- 4 档字号 + 3 档语义文字色落地；修 3 处层级倒置（页面标题=正文、视频标题 13sp<UP 名 14sp、设置条目 12sp<分组头 14sp）；替换高频布局里的 `alpha` 硬凑与 11sp 小字（2.3、2.7b、2.25）。
- 标题行数统一为 2 行 + `ellipsize=end`；头像尺寸统一（2.7b）。
- 圆角统一 12dp、卡片内边距 12dp、列表左右 16dp、卡片间距 8dp（2.6）。
- 修正失效声明与死约束（2.20），统一封面比例（2.19）。
- 页面级：搜索框改 pill + 48dp、设置页图标尺寸、二维码默认档位与最大宽、菜单间距/图标/选中态、横屏列数与菜单两列（2.26）。
- 验证：改造前后截图并排对比；真机滚动检查文字截断/行高抖动。
- 风险：中（字号变大可能造成标题截断、卡片变高）。

### 批次 4：统一组件与状态（完成度）

- `cell_topbar.xml` 公共顶栏落地（`minHeight=48dp` + 标题 16sp + 分割线 + ripple），替换 `activity_simple_*` 等基类布局（7 处），再逐步替换其余 40 处（2.5）。
- 空态组件（图标 + 文案 + 重试）替换 5 处手写 `啥都木有~`；加载更多 footer；加载态改细进度条/骨架（2.8、2.18）。
- 转场动效统一到 `windowAnimationStyle` + 复用 `AnimationUtils.fadeIn/fadeOut`（播放器控制条、搜索框滚动隐藏补 duration）（2.8、2.26）。
- 裸 `Activity` 收进主题体系（`PlayerActivity`/`SplashActivity`/`GetIntentActivity`），接线或删除 `*.Splash`/`*.NoSwipe` 未引用 style（2.23、2.24）。
- 自适应图标 + 闪屏跟随主题（2.9）；重做暗色中性占位图（16:9 / 16:11 / 1:1）（2.7）。
- 给"浅色主题 / 外观风格 / 弹幕颜色"三个悬空功能一个结论（2.28）。
- 验证：`assembleDebug` + 单测 + 全页面走查（按 `MenuActivity.btnNames` 的 14 个入口逐个走）。
- 风险：中（裸 Activity 收回基类涉及播放器生命周期，需重点回归）。

> 每批结束后更新本报告末尾的"实施记录"，并按 `AGENTS.md` 的要求同步 `docs/architecture-map.md`（若涉及基建）与本文件。

---

## 附录 A：审计口径

| 统计项 | 命令口径 |
|---|---|
| 布局内硬编码颜色 | `Get-ChildItem res/layout* -File \| Select-String '"#[0-9a-fA-F]{3,8}"'` |
| 字号分布 | `Select-String 'android:textSize="([0-9.]+)sp"'` 聚合 |
| alpha 分布 | `Select-String 'android:alpha="([0-9.]+)"'` 聚合 |
| 顶栏复制 | `Select-String 'android:id="@\+id/top"'` 唯一文件名计数 |
| 代码层颜色 | `Select-String 'Color\.parseColor\|Color\.rgb\(|0x[0-9a-fA-F]{8}\.toInt\(\)\|setBackgroundColor\(\|setTextColor\('` |
| 死代码判定 | grep 类名/方法名，仅命中定义处即为 0 调用 |
| 颜色别名规模 | 解析 `colors.xml` 全部 `<color>`，按归一化 ARGB 分组统计值组与名字数 |
| 主题一致性 | 把 `ThemeManager.kt` 的 `ThemeColors` 与 `themes.xml`/`colors.xml` 归一化为 8 位 ARGB 后逐项比对 |
| 对比度 | WCAG 2.1 相对亮度公式（sRGB 线性化），半透明色先按背景合成再计算 |
| 未引用 style | 遍历 `res/values/*.xml` 的 `<style name>`，在全部 xml/kt/java 中搜索 `@style/` 引用 |

本次审计由 4 个方向的专项排查汇总而成，明细（含逐条 `file:line` 证据）保留在报告正文与附录 B–D；所有结论均在改动前的工作区实测，未修改任何源码。

## 附录 B：同类卡片规格对照表（批次 3 的施工图）

| 布局 | 封面比例 | 标题 | 副标题 | 头像 | 根外边距 | 分割线 | 圆角 |
|---|---|---|---|---|---|---|---|
| `cell_video_list.xml` | 16:9 声明**失效**(`:33`) | 12sp/3 行/不加粗(`:43-45`) | 11sp α.5 ×2(`:59,74`) | — | 6dp/2dp(`:6-7`) | 无 | 主题（默认 6dp） |
| `cell_video_local.xml` | 16:9 声明**失效**(`:41`) | **11sp**/3 行(`:58`) | 11sp α.75 + **10sp** α.9(`:64-73`) | — | 6dp/2dp(`:6-7`) | 无 | 主题 |
| `cell_dynamic_video.xml` | 16:11 ✅(`:26`) | 12sp/3 行(`:103`) | 11sp α.5 ×2(`:73,84`) | 图标 α.5 | 内层 2/8dp | 无 | 主题 |
| `cell_opus.xml` | 16:11 ✅(`:27`) | 12sp/2 行/`#fff`(`:75-85`) | 11sp α.5 `#fff`(`:61-62`) | — | 6dp/2dp(`:6-8`) | 无 | 主题 |
| `cell_article_list.xml` | 16:11 ✅(`:28`) | 12sp/3 行/`#fff`(`:107-109`) | 11sp α.5 `#fff` ×2(`:75-77,87-92`) | — | 6dp/2dp(`:6-7`) | 无 | 主题 |
| `cell_favorite_folder_list.xml` | 16:9 ✅(`:29`) | 11sp/**1 行**(`:55-57`) | 11sp α.7(`:68-70`) | — | 6dp/2dp(`:6-7`) | 无 | 主题 |
| `cell_timeline_episode.xml` | 固定 80×60dp(`:16-17`) | 14sp/2 行(`:33-34`) | 12sp α.7 + 11sp α.5(`:43,52`) | — | 4dp(`:6`) | 无 | 主题 |
| `cell_article_head.xml` | **无比例**(`:62-72`) | 15sp/**不限行**/**加粗**(`:94-95`) | 13sp `#fff` 无 α(`:45-46`) | 0dp+1:1+70%(`:24-35`) | 6dp 水平(`:7`) | 无 | `@dimen/card_round`(6dp) |
| `cell_dynamic.xml` | 头像 1:1(`:16`) | 13sp/**不限行**/**加粗**(`:52-54`) | 11sp α.7(`:39-41`) | 0dp/1:1(`:9-19`) | 6dp 水平(`:5`) | `#318C8C8C`(`:182`) | 无卡片 |
| `cell_reply_list.xml` | 图片无比例(`:100-113`) | — | 11sp α.7(`:37-39`) | **35dp**(`:11-12`) | 6dp 水平(`:5`) | `#318C8C8C`(`:200`) | 无卡片 |
| `cell_user_list.xml` | — | 14sp/1 行/**加粗**/白(`:38-42`) | 11sp α.7(`:49-52`) | **40dp**(`:21-22`) | 6dp/2dp(`:6-7`) | 无 | `bg_card_rounded` |
| `cell_up_list.xml` | — | 默认/1 行/加粗(`:36-38`) | 11sp α.8(`:51-55`) | **70% 高**(`:25`) | 仅底 2dp(`:6`) | 无 | 主题 |
| `cell_user_info.xml` | — | **未设**/1 行(`:20-30`) | 11-13sp，α.7/.86/无(`:40,52,107,120`) | **0dp 无基准**(`:9-18`) | 6dp 水平(`:5`) | `#818C8C8C`+`#318C8C8C`(`:81,215,327`) | 无卡片 |
| `item_account.xml` | — | 15sp/1 行/加粗(`:30-33`) | 12sp 主题副色(`:46-47`) | **56dp**(`:17-18`) | 底 8dp(`:6`) | 无 | 主题 |
| `item_menu_setting.xml` | — | 15sp(`:21`) | 12sp `#999999`(`:29-30`) | — | 6dp/2dp 字面量(`:5-6`) | 无 | 主题 |
| `cell_message.xml` | — | — | 13sp(`:21`) + 11sp α.7(`:35-37`) | 32dp 行高(`:12`) | 6dp 水平(`:3`) | `#318C8C8C`(`:44`) | 无卡片 |
| `cell_message_reply.xml` | — | — | 13sp(`:22`) + 11sp α.9(`:40-41`) | — | 无(`:20-21`) | `#318C8C8C`(`:32`) | 主题 |
| `cell_private_msg.xml` | include 被挤压(`:33-38`) | **10sp**(`:11`) | **10sp** α.7(`:52-53`) | — | 无 | 无 | 主题 |

## 附录 C：空态接线现状（批次 0 第 5 项的施工图）

| 页面 | 基类 | 空态 | 证据 |
|---|---|---|---|
| 推荐 | `RefreshMainActivity` | ❌ 无（空结果还静默返回） | `RecommendActivity.kt:17`、`:69-74` |
| 动态 | `RefreshMainActivity` | ❌ 无 | `DynamicActivity.kt:25` |
| 直播推荐 | `RefreshMainActivity` | ❌ 无 | `RecommendLiveActivity.kt:12` |
| 热搜 | `RefreshMainActivity` | ❌ 无 | `HotSearchActivity.kt:13` |
| 22 个二级页 | `RefreshListActivity` | ⚠️ 基类具备，需子类显式调用 | `RefreshListActivity.kt:19,29,94-110` |
| 已正确接线 | — | ✅ | `FollowLiveActivity.kt:33`、`SeriesInfoActivity.kt:66,94`、`UserSeriesActivity.kt:37,68`、`MedalWallActivity.kt:109`、`DownloadListActivity.kt:134-148`、`ExpLogActivity.kt:48,59`、`CoinLogActivity.kt:48,59`、`LoginRecordActivity.kt:52,63`、`UserVideoFragment.kt:52`、`UserArticleFragment.kt:52`、`UserFavoriteFragment.kt:52`、`Search*Fragment.kt` |
| 空态样式 | — | ⚠️ 纯文字 `啥都木有~`，无图标/无色/无重试 | `activity_simple_refresh.xml:52-59`、`fragment_simple_refresh.xml:19-25` |

## 附录 D：布局层明细（按需展开）

- **同构布局清单**（骨架完全相同）：`cell_coin_log`≡`cell_exp_log`；`cell_article_hr`≡`cell_divider`；`cell_choose`≡`cell_create_folder_button`≡`cell_goto`；`activity_setting_menu`≡`activity_simple_list`；`cell_article_image`≡`cell_dynamic_image`；`cell_episode`≡`cell_item_vertical`。
- **同构 9 份（仅 id 不同）**：`activity_player_jump`、`cell_action_button`、`cell_article_heading`、`cell_article_textview`、`cell_interaction_choice`、`cell_lyric_line`、`cell_reply_child`、`item_menu_setting_footer`、`item_menu_setting_header`。
- **间距统计**：margin 970 处中 8dp 倍数仅 347（35.8%）；padding 289 处中 8dp 倍数 79（27.3%）；高频值 `8dp×325 / 4dp×185 / 6dp×113 / 3dp×104 / 2dp×102 / 1dp×32 / 5dp×26 / 10dp×22`。
- **过小字号位置**：8sp `activity_player.xml:182`；9sp `activity_captcha_webview.xml:33`；10sp `cell_private_msg.xml:11,53`、`cell_video_local.xml:74`、`activity_player.xml:480,494`、`panel_video_settings.xml:52`。
- **px / 0px 单位**：`cell_video_local.xml:84`、`activity_download.xml:23`、`activity_player.xml:17,18`、`cell_article_hr.xml:4`、`cell_date_divider.xml:11,27`。
- **透明色三种写法**：`#0000`×6、`#6000`×2（实为半透明黑：`activity_search.xml:129`、`activity_simple_viewpager.xml:58`）、`#00000000`×8。
- **`sp` 当高度**：32 处 `35sp` + `activity_tutorial_manager.xml` 的 40sp/30sp，分布见 2.11。
- **卡片强制可点击**：`styles.xml:10-11` 作用于全部 139 个 `MaterialCardView`。
- **占位文案**：`cell_video_list.xml:44`、`cell_video_local.xml:57`、`cell_article_list.xml:107`、`cell_collection_info.xml:30`、`cell_dynamic.xml:52`、`cell_dynamic_article.xml:101`、`cell_dynamic_child.xml:47`、`cell_dynamic_video.xml:102`、`cell_opus.xml:85`、`cell_reply_list.xml:50`、`cell_user_info.xml:106`。

## 附录 E：已确认值得保留、不要回退的实现

优化过程中这些地方**不能顺手改坏**（它们目前是正确的，只是可能看起来"旧"）：

- `util/AnimationUtils.java:43-45` 的 `crossFade`（100ms）与各详情页的 `crossFade` 用法 —— 加载态到内容态的过渡。
- 搜索框滚动隐藏逻辑（`SearchActivity.kt:495-518`，只是缺 `duration`）。
- 二维码三档缩放（`QRLoginFragment.kt:124-160`，注意 `Guideline` 改 percent 后必须 `requestLayout()`）。
- 圆屏适配（`BaseActivity.kt:143-181`）与 `RotaryRecyclerView`/`RotaryScrollView` 的滚轮输入支持。
- `ThemeManager` 集中配色的**设计意图**（7 套主题 41 个字段的抽象是对的，问题只是"另有两套并行表"）。
- 视频卡片的 `MaterialCardView` + 主题 ripple 组合（有反馈的那一半，是标准，另一半应向它对齐而不是反过来）。
- 弹幕恒定白字 + 黑阴影（`DanmakuManager.kt:157,168`）：不受主题控制是**刻意的可读性选择**，不要"顺手主题化"。

## 附录 F：实施记录

（每批完成后在此追加：日期 / 改动范围 / 验证命令与结果 / 遗留问题）

### 2026-09-10 审计基线

- `./gradlew.bat :app:assembleDebug` 通过（exit 0），本报告为改动前快照。

### 2026-09-10 批次 0（10/10 完成）

| 项 | 状态 | 改动 |
|---|---|---|
| 0-0 系统栏标志位修复 | ✅ | `ThemeManager.applyWindowTheme` 改用 `WindowInsetsControllerCompat` 按底色亮度设图标；`BaseActivity` 新增 `applySystemBarInsets()` 做 insets 避让 |
| 0-1 图片画质与兜底 | ✅ | `GlideUtil.QUALITY_LOW` 25→60、大封面走 `url_hq`（16 处）、`VideoCardHolder` 去掉 `override(400,225)`/`sizeMultiplier(0.85)`、22 个文件补 `.error()`、`getTransitionOptions()` 去掉静态缓存 |
| 0-2 `sp` 当尺寸 | ✅ | 19 个布局 36 处 `layout_width/height` 的 sp → dp |
| 0-3 触控目标 48dp | ✅ | 播放器 11 个 28dp 按钮 + 弹幕发送 35dp + 进度条 20dp→48dp、多选底栏 32dp×4、二维码帮助键 28dp、弹窗按钮 36dp 等共 **28 个控件**补 `minWidth/minHeight`（保留视觉尺寸）；`activity_captcha_webview.xml` 的缩放按钮原本就显式 `minWidth=0dp`，保持原样 |
| 0-4 列表点击反馈 | ✅ | `cell_dynamic`/`cell_reply_list`/`cell_user_list` 补 `selectableItemBackground`（`cell_up_avatar` 原本已有） |
| 0-5 一级页空态 | ✅ | `RefreshMainActivity` 补 `showEmptyView/hideEmptyView`，4 个一级页接线；3 个 `emptyTip` 布局改 14sp + 次要色 |
| 0-6 文字层级/分割线 | ✅ | 默认主题三档文字色拆开（`#EBE0E2`/`#B8AEB2`/`#8C868A`）；36 个布局里 `alpha 0.5→0.7`（20 处）、11sp→12sp 与 ≤10sp 提档（46 处）；分割线 `#318C8C8C`→`@color/list_divider`（9 处） |
| 0-7 三连卡 + 硬编码色 | ✅ | 三连卡改 surface 底 + 主色描边（对比度 2.1:1 → 约 12:1），删掉 `VideoInfoFragment` 里的主题特判；`Color.rgb(207,75,95)`/`#FE679A`/`#FB7299`/`0xffff6699`/`#88FFFFFF` 等收敛到 `ThemeManager` |
| 0-8 死资源 | ✅ | 删除 `layout-v17`/`layout-v22`，v22 里唯一生效的 `layout_marginEnd="8dp"` 合入 `layout/item_hot_search.xml` |
| 0-9 主题色值矛盾 | ✅ | 默认主题补齐 4 个缺失 item（新增 `terminal_deep`，`CARD` 对齐 alpha）；7 套 `colorOnPrimary` 统一 `@color/text_on_primary`；知乎蓝 ripple 改蓝；Rainbow 两色与 xml 对齐 |

- 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过（`--offline --no-build-cache`），共 105 个文件改动（+465/−294）。
- 新增坑（已写入 `AGENTS.md`）：改 `res/` 的文件集合后，Gradle build cache 会回放陈旧 merged-resources，表现为 `Unresolved reference 'R.layout.xxx'`；须 `:app:clean` + `--no-build-cache`。
- **遗留：未做真机截图验证**（本机无设备/模拟器）。0-0（系统栏图标亮度 + insets）与 0-3（进度条加高到 48dp）属于行为/尺寸变更，建议装一次 debug 包分别在**手势导航**与**三键导航**下确认播放器底栏与列表末项。
### 2026-09-10 批次 1（设计 token 化 + 删死代码，10/10）

| 项 | 改动 |
|---|---|
| 设计 token | `dimens.xml` 从 8 个扩到 30 个：间距 5 档（4/8/12/16/24）、圆角 3 档、字号 4 档（16/14/13/12sp）、`touch_min`、卡片内边距、顶栏高度、行高 3 档、图标 4 档、`divider_thin` |
| token 立即生效 | 批次 0 里补的 56 处 `minWidth/minHeight="48dp"` 换成 `@dimen/touch_min`（8 个布局） |
| 删死代码 | 删除 `ui/theme/BiliColors.kt`（含 `BiliDimens`，0 引用）、`ui/theme/ThemeUtils.kt`（18 个方法只用 1 个）、`res/values/modern_styles.xml`（3 个样式 0 引用，整个文件死） |
| 删死样式 | `styles.xml` 移除 `ButtonSecondaryStyle`/`ButtonSecondaryStyleLight`/`ButtonDangerStyle`/`TextViewSytle`（拼写错误的兼容别名） |
| 删幽灵开关 | `ThemeManager` 的 `APPEARANCE_MODERN/CLASSIC`、`CLASSIC_CARD_BG`、`getCardBackgroundColor`/`getButtonBackgroundColor`/`getColorScheme`/`getAppearanceStyle`/`setAppearanceStyle`、`BiliColorScheme`，以及 `SharedPreferencesUtil.APPEARANCE_STYLE` —— 这个"外观风格"开关既无设置入口也无任何渲染效果 |
| 迁移真实调用点 | `FolderChooseAdapter` 的 2 处 → `ThemeManager.withPrimaryAlpha(0x99)`（新增）与 `ThemeManager.DIVIDER`；`VideoInfoFragment` 的 `ThemeUtils.getInfoColor()` → `ThemeManager.INFO` |
| 单一真源 | `ThemeManager.PREF_KEY_THEME` 指向 `SettingsKeys.THEME`（此前同一字符串两处常量）；`setTheme()` 改 `putStringSync`（紧接 `recreate()`，避免 apply 的读旧值窗口）；设置页改调 `ThemeManager.setTheme()` 而不是直接写 SharedPreferences |
| 主题映射收敛 | 新增 `ThemeManager.themeResId()`，`BaseActivity` 与 `PlayerActivity` 各自复制的 `when` 分支合并为一处 |

- 结果：`ui/theme/` 从 3 个文件收敛为 **1 个**（`ThemeManager.kt`）；全工程 grep `BiliColors|ThemeUtils|BiliDimens|ModernCard|ModernButton|APPEARANCE_STYLE|BiliColorScheme` **0 命中**。
- 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过。

### 2026-09-10 批次 2（10/10 完成 —— 主题切换现在真的"全应用生效"）

| 项 | 状态 | 改动 |
|---|---|---|
| 3 处布局硬编码主题 | ✅ | 移除 `activity_player.xml`、`activity_image_viewer.xml`、`cell_episode.xml` 的 `android:theme="@style/Theme.BiliClient"` —— 播放器此前无论选什么主题都渲染成 B站粉 |
| 3 处代码强制主题 | ✅ | 删除 `ImageViewerActivity.setTheme(Theme_BiliClient)`、`MediaEpisodeAdapter`/`QualitySelectorAdapter` 的 `ContextThemeWrapper(Theme_BiliClient)` |
| 静态 `@color/*` → 主题属性 | ✅ | **76 处**（22 个布局）按语义映射：`pink_light`/`pink`/`pink_brand`/`theme_color` → `?attr/colorPrimary`；`text_*_dark`/`textwhite` → `?android:attr/textColorPrimary/Secondary/Tertiary`；`card_dark*`/`surface_dark`/`pink_very_light` → `?attr/colorSurface`；`divider_dark`/`divider_color` → `@color/list_divider`；`bgblack`/`background_dark` → `?android:attr/colorBackground`；`link` → `?attr/colorAccent`；阴影色统一 `#000000` |
| 硬编码白色文字 | ✅ | **56 处**（21 个布局）`#fff`/`#ffffff`/`@android:color/white` → `?android:attr/textColorPrimary` |
| 其余硬编码 hex | ✅ | **33 处**：`#999999`/`#a2a2a2` → 次要文字色；`#dd262626` → `?attr/colorSurface`；`#FE679A` → `?attr/colorPrimary`；`#00000000` → `@android:color/transparent`；`#6000`（其实是半透明黑，易误读成透明）→ 新增 `@color/scrim`；`#FF5722`/`#74a864` → `@color/status_warning`/`status_success`；二维码白底 → `@color/qr_plate`；播放器/短视频进度条 `#aa44aaff`/`#eeFEFEFE` → `?attr/colorPrimary`/`@color/player_progress_bg` |
| 旧站蓝 drawable | ✅ | `zoom_btn_bg.xml` 的 `#00a1d6` → `?attr/colorPrimary`，验证码页按钮文字改 `?attr/colorOnPrimary` |
| 下拉刷新色 | ✅ | `RefreshMainActivity`/`RefreshListActivity` 补 `setColorSchemeColors(ThemeManager.PRIMARY)` |
| 闪屏底色 | ✅ | `splash_text.xml` 从 `@color/background_dark`（#1B1B24，与默认主题纯黑不一致 → 冷启动可见跳变）改为纯黑，对 6 套深色主题都最不突兀、默认主题完全一致 |
| 挖孔屏/系统栏 | ✅ | `applySystemBarInsets()` 的 inset 从 `systemBars()` 扩到 `systemBars() or displayCutout()`，横屏挖孔机型内容不再被刘海压住 |

- 量化结果：布局内硬编码 hex 从 **137 处 → 49 处**（剩余全部是压在产品画面上的视频叠层/蒙层/验证码页，本就不应随主题变）；静态 `@color/*` 从 95 处 → 29 处（剩 `list_divider`、`status_*`、专栏代码/引用块色）。
- 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过。
- ⚠️ 施工事故（已恢复）：`activity_vote_info.xml` 被 PowerShell 文本往返写成乱码（UTF-8 → GBK 误读），已 `git checkout` 恢复并用 UTF-8 安全的方式重做替换。**结论：改含中文的源码/资源一律用文件工具，不要用 PowerShell 读写。**
- 遗留：仍**未做真机截图验证**。本轮改动面积最大的就是主题属性替换，建议**逐套主题过一遍**（重点是播放器、搜索、设置、动态、私信、用户主页、投票页）。

### 2026-09-10 批次 3（文字层级 / 卡片规格 / 封面比例 —— 主体完成）

| 项 | 改动 |
|---|---|
| 修层级倒置 | 设置条目 12sp → `@dimen/text_subtitle`(14sp)、描述改次要色；分组标题改主色加粗；视频详情标题 13sp → `@dimen/text_title`(16sp) + 加粗（此前**比 UP 主名 14sp 还小**）；文章头标题 15sp → 16sp |
| 字号系统落地 | 视频卡/本地卡标题 → `text_body`(13sp)、元信息 → `text_caption`(12sp)；`cell_video_local` 的下载速度色 `#FF8B5CF6` → `?attr/colorPrimary` |
| 标题行数 | 视频卡 3 行 → 2 行、本地卡 3 行 → 2 行、收藏夹名 1 行 → 2 行（统一「重要标题最多 2 行 + `ellipsize=end`」） |
| 圆角/间距收口 | `card_round` 6dp → **12dp**（与 CardView 的 12dp 统一，此前 drawable 卡片 6dp / CardView 12dp 并存）；`activity_padding_horizontal` 6dp → **12dp**；`list_margin_vertical` 2dp → **6dp**；视频卡内边距改用 `@dimen/card_padding` |
| 封面比例（CLS） | `cell_video_list.xml` 与 `cell_video_local.xml` 的封面行从 LinearLayout（`layout_constraintDimensionRatio` **完全失效**）重构为 ConstraintLayout：封面固定 48% 宽 + 16:9 + `centerCrop`，标题区吃剩余宽度 → 列表行高不再随图片比例抖动 |
| 失效声明/死约束 | `cell_user_info` 头像 `0dp` 无基准 → 固定 56dp；`cell_up_list` 头像「父高 70% + 1:1」约束环 → 固定 40dp；`cell_favorite_folder_list` 内层 `match_parent` → `wrap_content`、Guideline 命名与方向相反 → 按实际方向改名、图标 `0dp` → `@dimen/icon_sm`；`cell_recent_up_list` 去掉 RecyclerView 上无效的 `android:orientation`；`cell_article_head` 去掉挂在 LinearLayout 上的 `app:layout_constraint*` 与指向不存在视图的约束；`cell_private_msg` 的 include 宽度 `wrap_content` → `match_parent`（内部按父宽百分比布局，此前失去基准） |
| 搜索框 | `background_searchbar.xml` 底/描边写死色 → `?attr/colorSurface`/`?attr/colorControlNormal`，圆角改 24dp **胶囊形**；输入框 42dp → `@dimen/touch_min`(48dp)、字号 13sp → `text_subtitle`(14sp)、左右内边距 8dp → 16dp |

- 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过。
- 本批**特意留到批次 4**的：全量 `alpha` → 语义文字色替换（目前只剩容器级 `alpha=0.7`，观感已正确，但语义不如颜色 token 清晰）、头像尺寸策略彻底统一（评论头像仍 35dp）、菜单间距/图标/选中态、二维码默认档位、横屏列数。

### 2026-09-10 批次 4（进行中：公共顶栏 + 空态 + 菜单间隔已落地）

| 项 | 状态 | 改动 |
|---|---|---|
| 公共顶栏 | ✅（基类布局 6 处） | `cell_topbar.xml` 重写为真正的公共组件：`minHeight=48dp`（原顶栏整条约 23dp，却是 45 个页面的唯一返回热区）、标题 `@dimen/text_title`(16sp)、时钟 12sp 次要色、`?attr/colorSurface` 背景 + 1dp 分割线。`activity_simple_refresh`/`activity_simple_main_refresh`/`activity_simple_list`/`activity_simple_viewpager`/`activity_simple_text`/`activity_loading` 六处从手抄改为 `<include>` |
| 顶栏箭头语义 | ✅ | 新增 `BaseActivity.setTopbarIcon()`：`setTopbarExit()`（二级页）设 `arrow_back`，`InstanceActivity.setMenuClick()`（一级页）设 `arrow_up` —— 此前箭头写死在每个布局里，47 份各写各的 |
| 空态图标 | ✅ | 5 个 `emptyTip` 布局加 `drawableTop="@mipmap/loading_2233_error"` + `drawablePadding` + `padding`：从"一行纯文字"变成"图标 + 文案"（不改 id/层级，基类逻辑不动） |
| 菜单项间隔 | ✅ | `MenuActivity` 生成的 14 个 `MaterialButton` 此前**完全紧贴**成一块；现加 8dp 间隔 + 2dp 上边距、文字左对齐、`minHeight=48dp` |
| 头像尺寸 | ✅ | 评论头像 35dp → 40dp（与用户列表/UP 主列表统一到 40dp） |
| 新空态组件（图标+主文案+次要说明+重试） | ⏳ | 当前是"图标 + 一行文案"，没有重试按钮 |
| 闪屏 | ✅ | `activity_splash.xml` 的文字原是整屏居中，与 `splash_text.xml` 居中的 128dp 图标**重叠**；改为贴底居中（`paddingBottom=96dp`），形成"图标居中 + 文案在下" |
| 转场动画 | ✅ | 新增 `anim/anim_activity_out_down.xml`（下滑+淡出）与 `styles.xml` 的 `BiliWindowAnimation`（开/退场 4 个动画），挂到 **8 个主题基类**的 `android:windowAnimationStyle` 上 —— 此前全工程只有 `InstanceActivity` 一处 `overridePendingTransition`，其它页面各用系统默认 |
| 暗色中性占位图 | ✅ | `placeholder.png`/`placeholder_noround.png`/`article_placeholder.png` 原为近纯白（平均色 #DFDFDF / 浅蓝 #90E4FD），在纯黑默认主题上每次进列表都闪白块；重做为**深色中性占位**（底色 #2A2A35 与卡片一致 + 略亮的播放三角/文本条图形），体积从 4.7KB 降到 ~0.6KB。用自写的 PNG 编码脚本生成，无新增依赖 |
| 二维码档位 | ✅ | 默认档从 `0.01/0.99`（卡片几乎占满屏宽）改为 `0.15/0.85`，三档重排为 SMALL/MEDIUM/LARGE(50%/70%/90%) 避免命名与百分比矛盾；卡片加 `layout_constraintWidth_max="280dp"` 兜住平板/横屏（正方形不再把状态文字挤出屏） |
| 横屏列数 | ✅ | `BaseActivity.getLayoutManager()` 原固定 3 列 → 按「每列至少 220dp」换算、下限 2 列（窄屏上卡片不再被压扁，大屏不再太空） |
| 加载更多 footer | ✅ | 新增 `adapter/LoadMoreFooterAdapter`：底部显示「正在加载…／没有更多了」。两个列表基类用 `ConcatAdapter(业务 adapter, footer)` 包装，`goOnLoad()` → LOADING、`setRefreshing(false)` → 按 `bottom` 给 END/IDLE。footer 追加在**末尾**，所以业务 adapter 里 `adapterPosition` 的语义不变（`DynamicHolder`/`PrivateMsgAdapter` 依赖它做点击定位）；`notifyItemChanged` 统一判定线程（`setRefreshing(false)` 常从后台线程调用） |
| 菜单当前页高亮 | ✅ | `MenuActivity` 用 `from` extra 给"当前所在页面"那个按钮加主色描边 + 主色文字——此前打开菜单完全看不出自己在哪一页 |
| 新空态组件（图标+主文案+次要说明+重试） | ⏳ | 当前是"图标 + 一行文案"，没有重试按钮（需要基类提供 `setOnEmptyRetry` 钩子） |
| 余下 41 处手抄顶栏 | ✅ | 用结构探测脚本（只替换"块内除 `top`/`pageName`/`timeText`(/`menuArea`) 外没有其它控件"的布局）批量替换 **38 个**布局为 `<include layout="@layout/cell_topbar" />`；加上先前 6 个基类布局，共 **44 个**布局共用公共顶栏。只剩 `activity_player.xml` 与 `fragment_short_video_page.xml` 保留自有顶栏——它们的顶栏里还有电量/时钟/标题等额外控件，属于合理差异。`MenuActivity` 显式调 `setTopbarIcon(arrow_up)`（它不走 `InstanceActivity.setMenuClick`） |
| 自适应图标 | ⏳ | 仍无 `mipmap-anydpi-v26`。**需要设计资产**（前景/背景分层），不适合盲写 |
| 裸 `Activity` 收编 | ⏳ | `PlayerActivity`/`SplashActivity`/`GetIntentActivity`。**决定不做**：`PlayerActivity` 3000 行、生命周期特殊，收编会引入 EventBus/教程触发/insets padding 等副作用，无真机验证时风险大于收益；播放器保持黑色系统栏是有意为之 |
| 全量 `alpha` → 语义色 | ⏳ | 仅剩容器级 `alpha=0.7`（观感已正确，语义不如颜色 token 清晰） |

- 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过（AAPT2 会校验 PNG，占位图有效性由资源合并通过佐证）。

## 附录 G：改造前后对比（批次 0–4 汇总）

| 维度 | 改造前（本报告 §1–2 的审计结果） | 改造后 |
|---|---|---|
| 颜色真源 | 三套并行值表（`colors.xml`/`themes.xml` + `ThemeManager` + `BiliColors`），互相漂移 | **一套**：`ThemeManager` + xml 主题；`BiliColors`/`ThemeUtils`/`BiliDimens` 已删除，`ui/theme/` 只剩 1 个文件 |
| 主题切换 | 播放器/图片查看器/选集弹窗被布局级 `android:theme` 钉死为 B站粉；5 处主题色值互相矛盾 | 3 处硬编码主题 + 3 处代码强制主题已移除；默认主题补齐 4 个缺失 item；知乎蓝 ripple、Rainbow 映射、`ON_PRIMARY` 已对齐 |
| 静态色 | 布局内硬编码 hex **137 处**、静态 `@color/*` 95 处 | hex **49 处**（剩全是视频叠层/蒙层/验证码页）、静态 `@color/*` **29 处**（剩 `list_divider`/`status_*`/专栏代码色） |
| 文字层级 | 默认主题三档文字同色；设置条目 12sp < 分组头 14sp；视频标题 13sp < UP 名 14sp；次级信息 11sp + alpha0.5（对比度 4.24:1） | 三档文字色拆开（≈9:1 / ≈6:1）；三处层级倒置已修；列表次级信息 12sp + alpha0.7（≈7:1） |
| 顶栏 | 47 份手抄、约 23dp 高、标题 14sp、无背景无分割线；箭头写死在每份副本里 | **44 个布局共用 `cell_topbar.xml`**：`minHeight=48dp`、标题 16sp、时钟 12sp 次要色、surface 背景 + 1dp 分割线；箭头由代码按页面类型设置 |
| 圆角/间距 | 圆角 9 档、`card_round` 6dp 与 CardView 12dp 并存；列表左右 6dp、卡片间距 2dp；`dimens.xml` 仅 8 个 token | 圆角统一 12dp；列表左右 12dp、卡片间距 6dp；`dimens.xml` **30 个语义 token** |
| 封面与列表抖动 | 视频卡/本地卡封面比例声明**完全失效**，行高随图片比例抖动；标题 3 行、字号 11–12sp | 重构为 ConstraintLayout 16:9 固定比例 + `centerCrop`；标题 13sp/2 行 |
| 图片加载 | 全工程 `.error()` **0 处**（失败会串上一行封面）；源图 25q/512w 且解码锁死 400×225×0.85 | 三个封装 + 21 个调用点补 `.error()`；质量 60q，大封面 80q/1024w，去掉写死解码尺寸 |
| 占位图 | 近纯白（#DFDFDF），纯黑主题上每次进列表闪白块 | 深色中性占位（#2A2A35 + 图形），4.7KB → 0.6KB |
| 状态反馈 | 一级页空数据为纯黑一片；空态是一行灰字；翻页加载表现为**顶部**转圈；无转场动画 | 一级页接空态并可**点击重试**；空态带图标；底部「正在加载…／没有更多了」footer；8 个主题统一转场动画 |
| 触控目标 | 顶栏 ~23dp、播放器按钮 28dp、进度条 20dp、底栏 32dp | 顶栏 48dp；28 个控件补 `@dimen/touch_min`；进度条 48dp（`paddingVertical` 保住原波形高度） |
| 系统栏 | `flags or LIGHT_STATUS_BAR.inv()` 把所有标志位置 1：导航栏图标不可见；全工程零 insets 处理 | `WindowInsetsControllerCompat` 按底色亮度设图标；根布局消费 `systemBars + displayCutout` inset |
| 死代码 | `BiliColors`/`ThemeUtils`/`BiliDimens`/`modern_styles.xml`/"外观风格"幽灵开关/4 个死样式/`layout-v17`+`layout-v22` | 全部删除（净减 1244 行），`ui/theme/` 3 文件 → 1 文件 |

**仍然遗留（需要外部输入，不是技术阻塞）**：

1. **真机验证** —— 上述所有改动只有"编译 + 单测通过"，无设备实测。
2. **自适应图标** —— 需要前景/背景分层的设计资产。
3. **裸 `Activity` 收编**（`PlayerActivity`/`SplashActivity`/`GetIntentActivity`）—— 风险评估后**决定不做**，理由见批次 4。
4. **菜单图标** —— 14 个 key 到图标的映射需要人工确认语义，盲配会配错。
5. **全量 `alpha` → 语义色** —— 只剩容器级 `alpha=0.7`，观感已正确。

## 附录 H：真机验证清单（按改动风险排序）

> 本报告所有改动只经过"编译 + 单测"，**没有设备实测**。下面按"最可能出问题"排序，每项给出看什么、以及不对时改哪里。

| # | 验证点 | 怎么看 | 不对时改哪里 |
|---|---|---|---|
| 1 | **系统栏/insets**（0-0） | 分别在**手势导航**与**三键导航**下：状态栏/导航栏图标是否清晰；列表最后一项、播放器底部进度条是否被导航栏压住 | `ThemeManager.applyWindowTheme`（图标亮度）/ `BaseActivity.applySystemBarInsets`（避让） |
| 2 | **顶栏**（批次 4 + 第十三轮） | 随便进 10 个页面：顶栏高度、标题字号、背景与分割线；二级页是返回箭头、一级页是上箭头、菜单页是上箭头 | `dimens.xml` 的 `topbar_height`；`cell_topbar.xml` 的背景/分割线 |
| 3 | **列表页翻页 footer**（第十二轮） | 滑到列表底部：应显示「正在加载…」，到底后「没有更多了」，空闲时不占位 | `adapter/LoadMoreFooterAdapter`、两个基类的 `goOnLoad`/`setRefreshing` |
| 4 | **视频卡封面**（批次 3） | 视频列表滚动时行高是否稳定（不再忽高忽低）；封面是否 16:9 填满 | `cell_video_list.xml` / `cell_video_local.xml` 的封面 ConstraintLayout |
| 5 | **占位图**（批次 4） | 冷启动进列表：是否还闪白块 | `git checkout app/src/main/res/mipmap-nodpi/placeholder*.png` 回退 |
| 6 | **7 套主题逐套切换**（批次 2 改了 152 个文件） | 每套主题走一遍：播放器（含选集/画质弹窗）、搜索、设置、动态、私信、用户主页、投票页、热搜 | 哪个元素颜色不对，说明它仍是静态色 → grep 对应布局 |
| 7 | **空态与重试**（第六/十二轮） | 断网或空数据进推荐/动态/热搜/直播：应看到"图标 + 啥都木有~ + 点我重试"，点击能重新加载 | `RefreshMainActivity`/`RefreshListActivity.setOnEmptyRetry` |
| 8 | **播放器触控**（0-3） | 进度条拖动是否更容易命中；控制按钮误触是否减少 | `activity_player.xml` 的 `@dimen/touch_min` 与进度条 padding |
| 9 | **转场动画**（第十一轮） | 一级↔二级页进出场是否统一（下滑进入、下滑淡出返回） | `styles.xml` 的 `BiliWindowAnimation` |
| 10 | **大字体**（0-2） | 系统字体调到最大：登录/发动态/回复等输入框是否还完整（原来用 sp 当高度会被压扁） | 相关布局的 `@dimen/row_height*` |

如果某一条不对，报告附录 F 里每一轮都记录了**文件 + 原因 + 替代方案**，回退单个改动的成本很低。
