# 开发硬约定（界面 / 菜单 / 设置 / 弹窗 / 文案 / 伪装页）

> 本册是写界面与功能时的实现细则；`AGENTS.md` 只放每次都要遵守的最短约束。
> **做到哪节先读哪节**，不要凭记忆。改任何非平凡代码前还要读 `docs/architecture-map.md`。

## 列表页

- 列表页继承 `RefreshMainActivity`（菜单入口页）或 `RefreshListActivity`（返回式）；加载完必须 `setRefreshing(false)`，否则翻页永久卡死。`onLoad(page)` 的 page 已自增。
- `activity_simple_refresh.xml` 里的 `filterBar`/`sortBar`/`manageBar`/`groupBar` 都是列表的**兄弟节点**（不是列表项），不会随列表滚走。想让某条**滚动时自动收回**，在子类里调 `setupAutoHideBars(条…)`（`RefreshListActivity`，**默认不启用**）；**别把「可见性在运行时才变化」的条放进去**（如收藏夹的多选条 `manageBar`）——展开时会把它强行点亮。判据抽在 `util/view/ScrollRetractDecider.kt`（纯函数，有单测）：**收回随时可以，展开只允许在列表到顶（`!canScrollUp`）时**——条的高度动画会顶动列表内容，中途展开会和滑动手势打架（真机反馈过，别把「向上滚就展开」的旧口径改回去）。

## 新增菜单页

- 改三处：`MenuActivity.btnNames`、`MenuConfig.ALL_ITEMS`、`AndroidManifest.xml`。
- **新菜单 key 对老用户不会自动出现**——`MenuConfig.loadEnabled` 对已存的 `menu_enabled` 直接返回，只能靠「菜单设置」手动开。
- 想让所有人都能立刻用到入口，别加菜单项，挂到既有页面（如动态页动作卡片）。

## 竖向列表里的横滑翻页

- **别让外层 `ViewPager2` 自己抢手势**：它判横向的门槛是 `dx > dy`（1:1），竖向滑动稍一倾斜就被它 intercept 成翻页（短视频页真机实测「几乎每次滑动都翻页」）。
- 正确做法是 `pager.isUserInputEnabled = false`，每页根布局用 `ui/widget/SwipePageLayout`（`|dx| > |dy| * 2` 才算横滑，之后 `beginFakeDrag`/`fakeDragBy` 跟手、松手 `endFakeDrag` 吸附；超过 18% 屏宽还没吸附则补 `setCurrentItem`）。
- 反编译结论：`beginFakeDrag()` 不受 `isUserInputEnabled` 影响；`fakeDragBy(f)` 内部是 `mRequestedDragDistance -= f` 再 `RecyclerView.scrollBy(...)`，**传负值 = 向后一页**（手指左移时 `ev.x - lastX` 正好为负，直接用这个差值即可）。`endFakeDrag()` 的吸附是异步的，松手后立刻读 `currentItem` 还是旧页。细节见 `docs/review/fix-progress.md` §3.48 / §3.49。

## 新增设置项与设置子页面

- 新增设置项改三处：`util/SettingsKeys.kt`、设置页 `SettingSection`、`activity/settings/SettingsIndex.kt`。
- 新增设置子页面有两种形态，**先想清楚用哪种**：
  1. **独立 Activity**（如「菜单设置」「我的页面设置」）——有自定义交互/独立布局时用，改三处：`SettingGroupActivity` 对应分组加 `nav`、`SettingsIndex` 加可搜索条目、`AndroidManifest.xml` 注册。
  2. **本页的另一个 `group_type` 分组**——只是若干列表项、无自定义交互时用（如 `GROUP_APPEARANCE`「外观设置」），只需：`buildContent` 加分支 + 写一个 `buildXxxGroup()` + 在上级分组加 `nav` + `SettingsIndex` 加条目。**不需要新 Activity、manifest、布局**。对用户同样是独立一屏。

## 按钮文字与行内对齐（26.11.02 起，写按钮必读）

- **所有主题按钮样式统一继承 `ButtonStyleBase`**（`res/values/styles.xml`；`ButtonStyle` / `ButtonStyleLight` / `ButtonStyleZhihu` / `ButtonStyleIQIYI` / `ButtonStylePurpleFantasy` / `ButtonStyleRainbow` / `ButtonStyleClassicGray` / `ButtonStyleTerminal`，玻璃变体随父样式一起继承）：**强制单行 + `ellipsize=end` + AppCompat 自动字号（7～14sp，1sp 步进）**。半宽按钮里的长标签（「从剪贴板粘贴」「稍后再看」这类）在系统大字体下自动缩字号，不再折行。
- **需要多行文本的「按钮」必须在布局里覆盖**：`android:maxLines="10"`（或所需行数）+ `app:autoSizeTextType="none"`。现有两处：`cell_episode.xml` / `cell_item_vertical.xml`（番剧标题、看点的「内容\n时间段」）、`item_menu_setting_help.xml`（展开后的多行说明）。新增此类按钮时照抄这两处的写法。
- **写死字号的小按钮**用 `app:autoSizeMaxTextSize="<原字号>"` 把上限钉回原值（`activity_captcha_webview.xml` 的 16/9/16sp 缩放键、`activity_image_picker.xml` 与 `activity_send_dynamic.xml` 的 12sp、`cell_subtitle.xml` 与 `activity_feedback.xml` 的 13sp）；**程序化创建的按钮**用 `setAutoSizeTextTypeUniformWithConfiguration(7, 原字号, 1, TypedValue.COMPLEX_UNIT_SP)`（见 `TutorialManagerActivity` 的 11sp 按钮）。
- **同一行放两个以上按钮的横向 `LinearLayout` 必须写 `android:baselineAligned="false"`**：默认按文字基线对齐，一旦两颗按钮字号/行数不同就会整体上下错开，看起来就是「按钮对不齐」。
- 自测口径：系统「字体大小」调到最大（或 `adb shell settings put system font_scale 2.0`）后，按钮文字要么单行、要么按上面的例外显式多行；同一行按钮的上下边缘必须齐平。

## 外观设置

- 外观设置（配色 / 卡片圆角 / 字体）走 `ui/appearance/` 三模块：**模块只放候选值与纯函数，写入一律走 `AppearanceManager`**（它负责递增外观版本号）；模块里别做几何计算，也别在热路径上缓存。细则见 `docs/architecture-map.md` §8.7。

## 暗色 / 亮色模式（26.10.26 起，写界面必读）

- `values/`、`drawable/` 是**暗色**；亮色只放 `values-notnight/`、`drawable-notnight/`。同名资源两份一起改。
- **布局里别再写死浅色字面量**（`#80ffffff`、`#ebe0e2` 之类）：要么用主题属性（`?android:attr/textColorPrimary`、`?attr/colorSurface`），
  要么在 `values/colors_mode.xml` 加语义色、并在 `values-notnight/colors_light.xml` 给亮色值。
- **亮色用米白，不用纯白**：背景 / 表面 / 卡片是暖调米白（CIELAB L\* 约 92 / 95 / 97、b\* 约 +4～+7）。与暗色 6 套主题共用 `#1B1B24` 系对称，亮色 6 套主题共用同一套米白、只按主题色相微偏；经典终端是更深一档的「旧纸」。数值只在 `tools/appearance/gen_light_mode.py` 的 `CREAM` / `TINT` / `OLD_PAPER` 里改，改完重跑脚本并重算 `AmbientGlow.lightSpecFor` 的 `top`。新增亮色颜色别写 `#FFFFFF`（二维码底板 `qr_plate`、主色按钮上的白字例外）。
- **改亮色光晕先量色差**：浅底上同样的色差人眼更难察觉，亮色光晕按 CIELAB ΔE 对齐暗色（中心 ≈ 32～42、顶部 ≈ 10～16），见 `AmbientGlow.lightSpecFor` 注释。
- **新增浅色图标**：跑 `python tools/appearance/gen_light_mode.py` 生成亮色版（`drawable-notnight/` 中性深灰 + `drawable-notnight-v21/` 按主题正文色着色）；压在主色 / 视频 / 通知上、两种模式都必须是白色的图标加进脚本的 `EXCLUDE`。
- **压在视频 / 图片上的图标与文字**用 `*_overlay` 图标与 `@color/overlay_icon` / `overlay_text`（没有亮色覆盖，永远浅色）。
- 代码设色用 `ColorScheme` 的 getter（已按模式切换）；**播放器里**（`PlayerActivity` 是 `forceDark` 上下文）用 `ColorScheme.darkColors()`。
- **`?attr` 不能写进「颜色状态列表 / drawable XML」里**：两者都只在 **API 23+** 才解析主题属性，API 19–22 的机器上会静默退成框架哨兵红 `#FFFF0000`（`ColorStateList.DEFAULT_COLOR`，`android/content/res/ColorStateList.java:151`），表现是控件整块变纯红、文字与底色同色看不见（26.11.09 的弹窗按钮事故就是这么来的；`<shape>` 里的 `?attr` 在 API 19 上更直接抛 `UnsupportedOperationException`，见 `docs/review/fix-progress.md` §1）。要用主题色只有两条路：布局里直接写 `android:textColor="?attr/colorPrimary"`（属性位上的 `?attr` 各版本都能解析），或代码里 `theme.resolveAttribute` 取单色再 `setTextColor` / `backgroundTintList`。Material 文字按钮样式（M2 / M3 一样）自带的就是 attr 状态列表，老设备必须用这两条兜住，封装见 `util/view/TextButtonColors`。
- 不要在 `values-notnight` 覆盖 `terminal_bgblack` / `bgblack`：冷启动窗口按**系统** uiMode 绘制，覆盖会让暗色用户在手表上闪白。
- **只做手动切换，不做「跟随系统」**（产品决定）：手表系统几乎固定报非夜间，跟随系统在手表上等于永远亮色。别加 `MODE_NIGHT_FOLLOW_SYSTEM` 或按时间自动切换。
- 切换模式只走设置页的「颜色模式」：`AppearanceManager.setColorMode` → 刷新 `BiliTerminal.context` → `ColorMode.applyGlobal()`（AppCompat 自动重建页面）。

## 图标 / 矢量图（26.11.09 起，写界面必读）

`app/build.gradle` 有 `vectorDrawables { useSupportLibrary true }`（`minSdk 19` 必须这样：关掉它就会因为 `drawable/` 与 `drawable-v21/` 同名双份撞 `Duplicate resources` 编不过），所以 AAPT **不生成** pre-21 的 PNG 兜底，`<vector>` 只能由 AppCompat 的 `VectorDrawableCompat` 解析。由此三条硬规矩：

- **布局里引用矢量图只能用 Compat 属性**：`app:srcCompat`（`ImageView` / `ImageButton`）、`app:drawableStartCompat` / `drawableEndCompat` / `drawableLeftCompat` / …（`TextView` 家族）。**绝不能写** `android:src` / `android:drawableStart` / `android:background` / `android:thumb` / selector 里的 `android:drawable`——这些在 API<21 由控件构造函数原生加载，直接抛 `Resources$NotFoundException: … invalid drawable tag vector`（26.11.09 扫码登录页事故：`fragment_qr_login.xml` 的 `android:src="@drawable/icon_help"`，教程走完进登录页必崩）。
- **半自定义控件的基类要选 AppCompat 的**（`AppCompatTextView` / `AppCompatImageView`…），否则 Compat 属性被控制器静默忽略、图标根本不显示（`ui/widget/MarqueeTextView` 26.11.09 就因此换了基类）。
- **原生 `Activity` 没有 AppCompat 控件工厂**（`activity/player/PlayerActivity` 是 `Activity()` + `android:Theme.Black`）：Compat 属性被静默忽略、原生属性 pre-21 会崩 ⇒ 那里的图标控件用 `ui/widget/VectorImageView.kt` 的 `VectorImageView` / `VectorImageButton`（自己读 `app:srcCompat`，并把 `setImageResource` 覆写成走 `AppCompatResources`），新增图标按钮沿用它们。

代码侧同理：加载图标用 `AppCompatResources.getDrawable`（统一入口是 `util/StringUtil.java` 的 `getDrawable`），**不要用** `ContextCompat.getDrawable` / `ResourcesCompat.getDrawable`（pre-21 走原生路径会抛）；给 TextView 设左右图标用 `TextViewCompat.setCompoundDrawables*WithIntrinsicBounds(view, drawable…)`，别传资源 id。守这条线的是单测 `app/src/test/java/com/RobinNotBad/BiliClient/ui/VectorDrawableCompatTest.kt`（白名单只剩 `drawable/player_selector_progress`，它只在 API≥21 才加载）。

## 弹窗

**统一走 `util/TerminalDialog.kt`，禁止在业务代码里裸写 `AlertDialog.Builder`。**（26.10.05 起完成迁移，见 `docs/archive/review/dialog-redesign-progress.md`；技术结论已并进 `docs/architecture-map.md` §8.7.0）

- **禁止的写法**：`AlertDialog.Builder(context).setItems(...)` / `.setSingleChoiceItems(...)` / `.setMultiChoiceItems(...)` / `.setMessage(...).setPositiveButton(...)`，以及任何自己 `inflate` 一个带选项列表的弹窗布局。全工程现有 17 处此类调用已在 26.10.05 全部迁移，`app/src/main/java` 下除 `TerminalDialog.kt` 自身外**不应再出现 `AlertDialog` 字样**（用 `grep -r "AlertDialog" app/src/main/java` 自检）。
- **为什么**：弹窗的花色不只取决于调用点，更取决于主题层。7 族主题此前都没声明 `alertDialogTheme`，于是弹窗走 MaterialComponents 自带的 alert overlay，正文色取自全工程 0 处声明的 `colorOnSurface`（回退近白 → 用户看到的「灰底白字」），破坏性按钮缺 `colorError` 语义，而**唯一跟随主题的 `colorAccent` 恰好是 `#FF6699` 荧光粉**（→「粉按钮」），背景又是方角无描边（→「像安卓原生的一样」）。裸 Builder 无论怎么写都会踩这一套。
- **正确写法**：菜单型 `TerminalDialog.menu(context, title, items, danger)`、单选型 `TerminalDialog.singleChoice(context, title, items, checked)`、确认型 `TerminalDialog.confirm(context, title, message, confirmText, cancelText, confirmIsDanger)`、纯提示 `TerminalDialog.alert(context, title, message)`。破坏性项（删除/取消收藏之类）**必须**进 `danger`，别让用户靠文案猜。
- **给新主题补 overlay**：`ThemeOverlay.<X>.Dialog` 里 `colorSurface` / `colorOnSurface` / `colorError` / `android:background=@drawable/dialog_background` 四项缺一不可，并在该主题里加 `<item name="alertDialogTheme">`。漏写不会崩（`TerminalDialog` 取属性带兜底默认值），但弹窗会退回 Material 默认配色。
- 颜色**只读 `?attr/`**（`colorPrimary` / `colorOnSurface` / `colorError`），`TerminalDialog.kt` 内不写任何色值常量。

## 伪装页（双击顶栏切过去的那两个页面）

> 需求与验收见 `docs/superpowers/specs/2026-10-22-disguise-mode.md`；手势技术结论见 `docs/architecture-map.md` §7.53。

- **伪装页刻意不继承 `BaseActivity`**：主题、DPI 适配、圆屏 `setRound`、教程触发、EventBus、顶栏手势，任何一项都可能把「这是 B 站客户端」暴露出来。用 `AppCompatActivity` + 专用主题（`Theme.BiliClient.Disguise` / `Theme.BiliClient.Disguise.Dialog`）。
- **但圆屏留白是设备适配、不是外观**：不继承 `BaseActivity` 就拿不到 `setRound()`，改由 `activity/disguise/DisguiseRoundPadding.applyDisguiseRoundPadding(root)` 读同一组 `PADDING_H` / `PADDING_V` 换算。**新增伪装页必须调它**，否则圆屏最外圈的按键会被切掉。
- **返回键必须吃掉**（计算器只当 `C`，蓝牙页完全不响应），并且要一起拦 `KEYCODE_ESCAPE`：用「模拟返回键」实现右滑的手表 ROM 到应用侧就是一个 `KEYCODE_BACK`，不拦就等于给伪装页开了个后门。
- **入口只写 `DisguiseLauncher.launch(context)`**，别在调用点自己 `startActivity`——样式判定只该有一份。启动失败只记日志、**不弹 Toast**：弹一句「打开伪装页失败」比不伪装更显眼。
- **`util/` 里那三个纯逻辑文件不许读资源**：`DisguisePrefs` 与 `CalculatorEngine` 会被 JVM 单测直接调用（本仓库无 Robolectric，单测里没有 Context），`getString` 必然空指针。`CalculatorEngine` 的 `ERROR = "错误"` 是结果值，属于已知破例。

## 页面级排序 / 分区配置

- 沿两个既有范式：`MenuConfig`（启用/未启用）与 `MySpaceConfig`（主列表/更多列表）——都是纯 Kotlin 单一数据源 + JVM 单测 + 复用 `MenuSettingAdapter` 式双分区拖拽写法。

## 请求与解析分离

- 请求与解析分离，纯解析抽成 `static`/`object` 函数并补 JVM 单测（参考 `HotSearchApi.parseHotSearch`；需要 SharedPreferences 时注入 `SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()`——该假实现是共享助手 `app/src/test/…/util/FakeSharedPreferences.kt`，**别再抄一份**）。

## 用户可见文案

- **用户可见文案一律进 `app/src/main/res/values/strings.xml`**（26.10.19 起从「只有设置页用」改为全仓迁移，台账与规范见 `docs/copy-inventory/STRINGS-MIGRATION.md`）。命名沿用既有前缀：`pagename_` 页面标题 / `btn_` 按钮 / `text_` 界面文本 / `desc_` 设置说明 / `msg_` 一次性提示。**新代码不要再硬编码中文**；迁移未覆盖的老代码可以留着，但改到哪块顺手迁哪块，别新增第二份。写法：Kotlin `getString(R.string.x)`；`companion object` / `object` 里用 `BiliTerminal.context!!.getString(...)`；Java 用 `BiliTerminal.context.getString(...)`（别忘了 import `R` 与 `BiliTerminal`）。
- **唯一例外：会被 JVM 单测直接调用的纯函数不要读资源**。本仓库不用 Robolectric，单测里没有 Context，`getString` 必然空指针（26.10.19 B6 一次挂掉 6 个用例）。这类「服务端错误码 → 人话」的映射统一放 `api/ApiErrorText.kt`（`object` + `@JvmStatic`，同包 Java 直接调），要改那几句改它一处。
