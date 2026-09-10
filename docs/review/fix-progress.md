# ReBiliClient 修复进度报告

> 更新日期：2026-09-10
> 基线：`docs/review/00-summary.md`（基于 26.08.27 快照，共 286 个问题）
> 状态：Critical 抽查项已全部确认/修复，High/Medium 待继续

---

## 一、修复总览

| 严重度 | 基线数量 | 已确认修复 | 待处理 |
|---|---|---|---|
| Critical | 23 | 17 | 剩余 6 待排查 |
| High | 52 | 4（分页 2 + 菜单键/Cookie 锁 2） | 48 待处理 |
| Medium | 105 | 0 | 105 待处理 |
| Low/Info | 106 | 0 | 106 待处理 |

---

## 二、已修复问题明细

### 第一轮（构建前修复）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `RefreshListActivity.kt` | `isLoading` 加载成功后永不复位，导致 10+ 页面翻页卡死 | `setRefreshing(false)` 时复位 `isLoading` |
| `RefreshMainActivity.kt` | `goOnLoad()` 未置成员 `isRefreshing`，滚动持续触发并发分页 | `goOnLoad()` 同步置 `isRefreshing = true` |

### 第二轮（审查后修复）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `QRLoginFragment.kt:309,310,383,384` | access_token / refresh_token / 完整 Cookie 明文打 logcat | 删除 4 行敏感日志 |
| `CaptchaWebViewActivity.kt:93-112` | WebView 开启全部危险开关 + JS 桥 | 关闭文件访问 4 项开关，混合内容改 NEVER_ALLOW |
| `LocalListActivity.kt:513` | 虚拟合集用全局索引访问过滤列表导致越界 | 改用 `videoList[startVideoIdx]` + 边界检查 |
| `VideoInfoFragment.kt:522-524` | 三连成功回调在 IO 线程 setImageResource | 包裹 `runOnUiThread { }` |

### 第三轮（剩余 Critical 排查）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `PrivateMsgActivity.kt:139` | 私信 JSON 注入（用户输入直接拼 JSON 字符串） | 改用 `JSONObject().put("content", content)` 安全构造 |
| `PrivateMsgActivity.kt:200` | 空会话列表 `list[list.size - 1]` 越界崩溃 | 加 `if (list.isEmpty()) return` 防护 |
| `NetWorkUtil.java:96` | 相对路径 Location 时 `getScheme()` NPE | scheme/host 判空后再比较 |
| `NetWorkUtil.java:100-109` | 手动重定向未 close 原响应导致连接泄漏 | 重定向前 `response.close()` |

### 第四轮（26.09.08 架构通读后修复）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `PrivateMsgApi.java:159` | `!has && isNull` 恒等于 `!has`，account_info 为 null 的普通会话被误过滤 | 改用 `isNull`；解析抽成 `parseSessionsList()` 纯函数 + 新增 `PrivateMsgApiTest`（6 例） |
| `InstanceActivity.kt:32` | MENU 键打开菜单后继续走 super，被 BaseActivity 又 finish 当前页 | 消费按键 `return true` |
| `InstanceActivity.kt:16` | `from` 参数判的是新建空 Intent，恒 false | 改判 `getIntent().getStringExtra("from")` |
| `NetWorkUtil.java:403` | `saveCookiesFromResponse` 无锁，Cookie 并发丢失 | 抽 `saveCookiesLocked()` 纳入 `NetWorkUtil.class` 锁 |
| `ConfInfoApi.java:41-43` | WBI 缓存三字段非原子写 | 合并为不可变 `WbiCache`，整体替换 |
| `MsgUtil.java:89-94` | sticky SnackEvent 显示后不移除，同页弹两次 | 显示分支同时 `removeStickyEvent` |
| `TerminalContext.java:118-133` | 视频缓存 aid/bvid 键不一致，bvid 路径永远 miss | 新增 `cacheVideo()` 双写；LruCache 10→20 |
| `ToolsUtilTest.kt:33` | 期望值笔误（`0x00123456` 剥离 alpha 写成 `0x000000`） | 改为 `0x123456` |

> 验证：`:app:testDebugUnitTest` 38 个测试全绿 + `:app:assembleDebug` 通过。

### 第五轮（登录页二维码缩放 bug）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `res/layout/fragment_qr_login.xml:34-53` | 二维码卡片 `layout_height="wrap_content"` 且无底部约束，改宽度后高度不跟着变（配合 `adjustViewBounds`+`scaleType=fitXY` 甚至可能整体尺寸不变） | 加 `app:layout_constraintDimensionRatio="1:1"`，ImageView 改 `match_parent` + `fitCenter` |
| `QRLoginFragment.kt:113-148` | 点二维码只改 `setGuidelinePercent` 不生效：Guideline 自身 `onMeasure` 恒 `setMeasuredDimension(0,0)`、尺寸不参与布局变化，外层 ConstraintLayout 又是 `wrap_content`，不触发父级重新测量；且 `findViewById` 用非空 `view` 但无判空、Toast 放在分支末尾，异常时既不变大也不提示 | 改 percent 后显式 `view?.requestLayout()`；Guideline 判空早退；提示文案统一在末尾触发 |

> 根因用 `constraintlayout-2.1.4.aar` 内 `Guideline.class` 字节码核实：`setGuidelinePercent` 仅写 `guidePercent` 字段并 `setLayoutParams`，`onMeasure` 第 2315 字节处为 `setMeasuredDimension(0,0)`。

### 第六轮（视觉体验优化 · 批次 0，见 `docs/visual-experience-report.md`）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `ThemeManager.kt:405-407` | `flags or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()` 等于把 `0xFFFFDFFF` 全部置位：清掉 `LIGHT_STATUS_BAR`（状态栏图标恒浅色，压在亮色品牌底上对比度不足）、打开 `LIGHT_NAVIGATION_BAR`（导航栏图标恒深色，压在纯黑导航栏上不可见），并附带无条件打开 `HIDE_NAVIGATION`/`IMMERSIVE`/`IMMERSIVE_STICKY` | 删掉整段，改用 `WindowInsetsControllerCompat.isAppearanceLightStatusBars/NavigationBars`，按系统栏底色亮度决定图标深浅 |
| `BaseActivity.kt` | `setDecorFitsSystemWindows(false)` 后全工程零 insets 处理，贴底控件与列表最后一项被导航栏压住 | 新增 `applySystemBarInsets()`，把 `systemBars()` inset 叠加到根布局已有 padding 上（保留用户的界面边距设置） |
| `GlideUtil.java:32-41` | 列表图一律 `25q/512w`，1080p 屏上封面放大后发虚、渐变出现色带 | `QUALITY_LOW` 25→60；新增 `url_hq()` 用于大封面（16 处调用点从 `url()` 切到 `url_hq()`） |
| `VideoCardHolder.kt:147-154` | `.override(400,225)` + `.sizeMultiplier(0.85)` 把解码锁在约 340×191 绝对像素，而显示区约 519px，必然上采样糊掉 | 删除两个调用，交给 Glide 按 ImageView 实测尺寸解码；封面改 `PREFER_ARGB_8888` |
| `GlideUtil.java` + 21 个 adapter/activity | 全工程 `.error()` **0 处**，图片加载失败时 RecyclerView 复用会显示上一项的封面（串图） | 三个封装方法补 `.error(safePlaceholder(placeholder))`；21 个链式调用点批量补 `.error()` |
| `GlideUtil.java:92-103` | `transitionEnabled` 用 static 缓存，设置里改「加载渐入渐出动画」必须重启才生效 | 去掉缓存，每次读 SharedPreferences |
| 19 个布局 | 36 处把 `sp` 当 `layout_width/layout_height`（32 处 `35sp` + 40sp/30sp），系统字体放大即压扁/溢出 | 全部改为 dp（`fragment_qr_login.xml`、`activity_write_reply.xml`、`activity_send_dynamic.xml` 等） |
| `layout-v17/`、`layout-v22/` | minSdk 24 下永不被选用（`item_hot_search.xml` 死变体） | 删除两个目录，并把 v22 里唯一生效的 `layout_marginEnd="8dp"` 合并进 `layout/item_hot_search.xml` |
| `cell_dynamic.xml`、`cell_reply_list.xml`、`cell_user_list.xml` | 根布局不是 CardView 却在代码里设了点击，完全没有按压反馈（"点了没反应"） | 补 `android:foreground="?attr/selectableItemBackground"`（用户列表另加 `clipToOutline`） |
| `RefreshMainActivity.kt` | 一级页完全没有空态能力，`activity_simple_main_refresh.xml` 里的 `emptyTip` 是死视图，空数据时用户看到纯黑列表 | 补 `emptyView` + `showEmptyView()/hideEmptyView()`（对齐 `RefreshListActivity`）；`RecommendActivity`/`DynamicActivity`/`RecommendLiveActivity`/`HotSearchActivity` 四个子类接线（`RecommendActivity` 此前还会静默吞掉空结果） |
| 3 个 `<emptyTip>` 布局 + `item_hot_search.xml` | 空态/热搜条目字号与颜色未走语义色 | 空态文字改 14sp + `?android:attr/textColorSecondary`；热搜排名色改主题色 |
| `activity_player.xml`、`bottom_bar_multi_select.xml`、`fragment_qr_login.xml`、`dialog_new_folder.xml`、`dialog_folder_settings.xml`、`bar_quality_select.xml` | 播放器 28dp 按钮 ×11 + 弹幕发送 35dp + 进度条 20dp；多选底栏 32dp ×4；二维码帮助键 28dp；弹窗按钮 36dp —— 均低于 48dp 最小触控规范 | 保留视觉尺寸，补 `minWidth/minHeight="48dp"`（共 28 个控件）；进度条高度 20dp→48dp + `paddingVertical="14dp"` 保住原波形区 |
| `themes.xml`（默认主题） | `Theme.ClassicTerminal` 是 7 套里唯一缺 `colorSurface` / `android:colorBackground` / `colorSecondary` / `colorPrimaryVariant` 的主题，Material 控件回退默认值；`colorPrimaryDark` 用 `terminal_pink(#FF6699)` 与 Kotlin 表的 `#E84B85` 不一致 | 补齐 4 个 item（新增 `terminal_deep`）；`colorOnPrimary` 统一到新增的 `text_on_primary` |
| `themes.xml`（知乎蓝） | `colorControlNormal/Highlight` 误用粉色 `@color/color_ripple`，其余 5 套都用了各自的 `*_color_ripple` | 改 `@color/zhihu_color_ripple` |
| `ThemeManager.kt`（五彩斑斓/经典终端） | Rainbow 的 `PRIMARY_LIGHT`/`SECONDARY` 与 xml 的 `colorPrimaryVariant`/`colorSecondary` 恰好互换；终端主题 `CARD` 丢了 alpha（`#FF262626` vs xml `#CC262626`） | 按「PRIMARY_LIGHT == colorPrimaryVariant、SECONDARY == colorSecondary」约定对齐 |
| `ThemeManager.kt` + `colors.xml`（终端文字层级） | 默认主题把三档文字色设成同一个 `#EBE0E2`，层级只能靠 alpha 硬凑 | 拆为 `#EBE0E2` / `#B8AEB2`(≈9:1) / `#8C868A`(≈6:1) |
| 36 个 `cell_*`/`item_*`/详情布局 | 次要文字 `alpha=0.5`（对卡片底约 4.24:1，不到 AA）20 处；11sp 及以下小字 46 处；分割线写死 `#318C8C8C` 9 处（对比度约 1.3:1，近乎不可见） | alpha → 0.7（约 7:1）；11sp→12sp、≤10sp 提一档；分割线统一 `@color/list_divider`（对卡片底约 3:1+） |
| `fragment_video_info.xml` + `VideoInfoFragment.kt` | 三连卡用 `?attr/colorPrimary` 亮粉打底 + 主题近白文字，对比度约 2.1:1（低于 AA）；且只在经典终端主题下用代码改暗底打补丁 | 改为「`?attr/colorSurface` 底 + `?attr/colorPrimary` 1dp 描边」，删掉主题特判代码 |
| 6 个 Kotlin/Java 文件 + `HighEnergyProgressBar`/`HotSearchAdapter`/`PageSelectorAdapter`/`DynamicHolder` | 跨文件写死的强调色：`Color.rgb(207,75,95)` ×7、`#FE679A`、`#FB7299`、`0xffff6699`、`#88FFFFFF` 等，切主题不变 | 收敛到 `ThemeManager.PRIMARY` / `LIKE_COLOR` / `TEXT_*`；高能进度条按主题主色 + 原不透明度合成 |

> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过（`--no-build-cache`；详见下方构建陷阱）。
> ⚠️ 陷阱：改 `res/` 的文件**集合**（增删/移动）后，Gradle build cache 可能回放陈旧的 merged-resources，表现为莫名其妙的 `Unresolved reference 'R.layout.xxx'`。必须 `:app:clean` + `--no-build-cache` 才能恢复。

### 第七轮（视觉体验优化 · 批次 1 + 批次 2 前半）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `ui/theme/BiliColors.kt`、`ui/theme/ThemeUtils.kt` | 与 `ThemeManager` 并行的第二套色板：`BiliColors` 152 处 `parseColor` 里实际只有 3 个调用点；`BiliDimens` 全工程 0 引用；`ThemeUtils` 18 个方法只用 1 个 | 把 3 个真实调用点迁到 `ThemeManager`（新增 `withPrimaryAlpha()`），删除两个文件 |
| `res/values/modern_styles.xml` + `styles.xml` | `ModernWindowAnimation`/`ModernButton`/`ModernCard` 与 `ButtonSecondaryStyle`/`ButtonSecondaryStyleLight`/`ButtonDangerStyle`/`TextViewSytle` 全部 0 引用 | 删除整个 `modern_styles.xml` 与 4 个死样式 |
| `ThemeManager.kt` + `SharedPreferencesUtil.java` | "外观风格 modern/classic"开关：无设置入口、两个消费方法 0 调用，纯幽灵功能 | 删除 `APPEARANCE_*`/`CLASSIC_CARD_BG`/4 个方法/`BiliColorScheme`/`APPEARANCE_STYLE` |
| `SettingsKeys.kt` + `ThemeManager.kt` + `SettingGroupActivity.kt` | `theme_selector` 在 `SettingsKeys.THEME` 与 `ThemeManager.PREF_KEY_THEME` 各定义一次；设置页绕过 `ThemeManager.setTheme()` 直接写 SharedPreferences（且用 `commit()`） | 常量合并为单一真源；`setTheme()` 改 `putStringSync`；设置页改调 `ThemeManager.setTheme()` |
| `BaseActivity.kt`、`PlayerActivity.kt`、`BiliTerminalApp.kt` | 三处各复制一份相同的「主题 key → style」`when` | 收敛为 `ThemeManager.themeResId()` |
| `activity_player.xml:5`、`activity_image_viewer.xml:7`、`cell_episode.xml:4` | 布局级 `android:theme="@style/Theme.BiliClient"` 覆盖运行时主题 —— 播放器/图片查看器/选集按钮无论选什么主题都渲染成 B站粉 | 移除三处 `android:theme` |
| `ImageViewerActivity.kt:28` | `setTheme(Theme_BiliClient)` 覆盖基类已按用户选择设置的主题 | 删除该调用 |
| `MediaEpisodeAdapter.kt:48`、`QualitySelectorAdapter.kt:56` | `ContextThemeWrapper(Theme_BiliClient)` 强制把卡片/按钮渲染成 B站粉主题 | 改用 `parent.context` 直接 inflate |
| `RefreshMainActivity.kt`、`RefreshListActivity.kt` | 下拉刷新转圈用 SwipeRefreshLayout 默认色，与 7 套主题都无关 | 补 `setColorSchemeColors(ThemeManager.PRIMARY)` |
| `res/values/dimens.xml` | 只有 8 个 token，字面量遍地（446 处 textSize 无一条走 dimen、圆角 9 档） | 扩到 30 个语义 token（间距/圆角/字号/触控/卡片/顶栏/行高/图标/分割线）；批次 0 的 56 处 48dp 改用 `@dimen/touch_min` |

> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过（`--offline --no-build-cache`）。
> 结果：`ui/theme/` 从 3 个文件收敛为 1 个（`ThemeManager.kt`）；`BiliColors|ThemeUtils|BiliDimens|ModernCard|ModernButton|APPEARANCE_STYLE|BiliColorScheme` 全工程 0 命中。

### 第八轮（视觉体验优化 · 批次 2：主题切换全应用生效）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| 22 个布局（`panel_video_settings`/`cell_video_folder`/`dialog_new_folder`/`activity_update`/`activity_player` 等） | 76 处引用**静态**调色板（`pink_light`/`card_dark*`/`divider_dark`/`bgblack`…），切到知乎蓝/爱奇艺绿/紫色空灵时这些元素仍是 B站粉 | 按语义映射到 `?attr/colorPrimary`/`?attr/colorSurface`/`?android:attr/textColor*`/`?android:attr/colorBackground`/`?attr/colorAccent`/`@color/list_divider` |
| 21 个布局 | 56 处硬编码 `#fff`/`#ffffff`/`@android:color/white` 文字色（浅色主题下的白底白字地雷） | 改 `?android:attr/textColorPrimary` |
| 23 个布局 | 33 处杂项 hex：`#999999`、`#dd262626`、`#FE679A`、`#00000000`、`#6000`、`#FF5722`、`#74a864`、播放器进度条 `#aa44aaff`/`#eeFEFEFE`、二维码 `#fff` | 分别改主题属性/新增 `@color/scrim`、`@color/qr_plate`/语义状态色/`@color/player_progress_bg` |
| `drawable/zoom_btn_bg.xml` | 写死 B站旧蓝 `#00a1d6`，与 7 套主题都不一致 | 改 `?attr/colorPrimary`，按钮文字改 `?attr/colorOnPrimary` |
| `drawable/splash_text.xml` | 冷启动窗口底色 `#1B1B24` 与默认主题纯黑不一致，可见"深蓝灰→纯黑"跳变 | 改纯黑（对 6 套深色主题都最不突兀，默认主题完全一致） |
| `BaseActivity.applySystemBarInsets()` | 只消费 `systemBars()`，横屏挖孔机型内容可能压在刘海下 | 扩为 `systemBars() or displayCutout()` |

> 量化：布局内硬编码 hex **137 → 49**（剩余全是压在视频画面上的叠层/蒙层/验证码页，不应随主题变）；静态 `@color/*` 95 → 29（剩 `list_divider`、`status_*`、专栏代码块色）。
> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过。
> ⚠️ 事故：`activity_vote_info.xml` 曾被 PowerShell 文本往返写成乱码，已 `git checkout` 恢复重做。**含中文的源码/资源一律不要用 PowerShell 读写。**

### 第九轮（视觉体验优化 · 批次 3：文字层级 / 卡片规格 / 封面比例）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `cell_setting_nav.xml` | 设置条目标题 12sp 比分组标题 14sp 还小（层级倒置）；描述与标题同字号同色；图标 `layout_height=0dp` + `constraintHeight_percent=0.4` 挂在 `wrap_content` 父级上（循环依赖） | 标题 → 14sp、描述 → 12sp + `?android:attr/textColorSecondary`、图标 → `@dimen/icon_lg`(24dp) |
| `cell_setting_title.xml` | 分组标题与条目标题无区分 | 改 `?attr/colorPrimary` + 加粗 |
| `fragment_video_info.xml` | 视频标题 13sp **小于 UP 主名 14sp**；简介折叠外无展开入口（后者留批次 4） | 标题 → `@dimen/text_title`(16sp) + 加粗 |
| `cell_video_list.xml`、`cell_video_local.xml` | 封面行的 `app:layout_constraintDimensionRatio` 写在 **LinearLayout** 里完全失效 → 行高由每张图自身比例决定，列表滚动持续抖动（CLS）；本地卡还有 3 行标题、11–12sp 小字、写死的紫色速度文字 | 封面行重构为 ConstraintLayout：48% 宽 + 16:9 + `centerCrop`；标题 → 13sp/2 行；速度色 → `?attr/colorPrimary` |
| `cell_user_info.xml`、`cell_up_list.xml` | 头像 `0dp` 无基准 / 「父高 70% + 1:1」约束环，尺寸不可预期 | 分别固定 56dp / 40dp + `centerCrop` |
| `cell_favorite_folder_list.xml` | 内层 `match_parent` 挂在 `wrap_content` 卡片上；两个 Guideline 的 id 与实际方向相反；收藏夹名只给 1 行 | `match_parent` → `wrap_content`；Guideline 按实际方向改名（`guide_cover_end`/`guide_title_bottom`，代码无引用）；标题改 2 行 + 13sp |
| `cell_recent_up_list.xml` | `android:orientation` 写在 RecyclerView 上（无效属性） | 删除并加注释说明由 LayoutManager 决定 |
| `cell_article_head.xml` | 子元素挂在 LinearLayout 上的一堆 `app:layout_constraint*` 全部无效；引用了本文件不存在的视图 id | 删除死约束；标题 15sp → `@dimen/text_title` |
| `cell_private_msg.xml` | include `cell_video_list` 时宽 `wrap_content`，而内部按父宽百分比布局 → 失去基准 | 改 `match_parent` |
| `dimens.xml` | `card_round` 6dp 与 CardView 的 12dp 并存；列表左右仅 6dp、卡片间距 2dp | `card_round` → 12dp、`activity_padding_horizontal` → 12dp、`list_margin_vertical` → 6dp |
| `activity_search.xml`、`background_searchbar.xml` | 搜索框 42dp 高、6dp 圆角（非胶囊）、底/描边写死 `#80242424`/`#cc808080` | 48dp + 24dp 胶囊圆角 + 主题表面色/描边色，字号 14sp、内边距 16dp |

> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过。
> 施工中一次失误：`cell_video_local.xml` 重构时漏删原 `</LinearLayout>`，AAPT 报「必须由匹配的结束标记终止」，已修。

### 第十轮（视觉体验优化 · 批次 4 前半：公共顶栏 / 空态 / 菜单）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `cell_topbar.xml` | 名为公共顶栏但 **0 处 `<include>`**，47 个布局各自手抄；顶栏整条约 23dp 高（不足 48dp 的一半），而 `BaseActivity.setTopbarExit()` 让**整条顶栏**点击就 `finish()`；标题仅 14sp、无背景、无分割线、时钟与标题同字号同字重 | 重写为真实公共组件：`minHeight=48dp`、标题 16sp、时钟 12sp 次要色、`?attr/colorSurface` 背景 + 1dp 分割线 |
| `activity_simple_refresh/main_refresh/list/viewpager/text`、`activity_loading`（6 个） | 手抄顶栏 | 改为 `<include layout="@layout/cell_topbar" />`；`@id/top`、`@id/pageName`、`@id/timeText` 三个 id 保持不变，基类 `setPageName()`/`setTopbarExit()`/`setRound()` 无需改动 |
| `BaseActivity.kt`、`InstanceActivity.kt` | 顶栏左侧箭头（`arrow_back` / `arrow_up`）写死在每个布局里，47 份各写各的 | 新增 `BaseActivity.setTopbarIcon()`：二级页由 `setTopbarExit()` 设 `arrow_back`、一级页由 `setMenuClick()` 设 `arrow_up` |
| 5 个空态布局（`activity_simple_refresh/main_refresh/main_list`、`fragment_simple_refresh/list`） | 空态是"一行纯文字 `啥都木有~`"，无图标、无色（一级页此前根本没接线，见第六轮） | 加 `drawableTop="@mipmap/loading_2233_error"` + 间距 + 内边距；不动 id/层级，基类逻辑不变 |
| `MenuActivity.kt` | 14 个菜单按钮**完全紧贴**成一整块，无间隔、无左对齐 | 加 8dp 按钮间隔 + 2dp 上边距、文字左对齐、`minHeight=48dp` |
| `cell_reply_list.xml` | 评论头像 35dp 与用户列表/UP 列表的 40dp 不一致 | 统一 40dp + `centerCrop` |

> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过。
> 顶栏改动**建议优先真机确认**：这是每个页面都会看到的组件，且顺带把热区从 ~23dp 提到 48dp。

### 第十一轮（视觉体验优化 · 批次 4 后半：闪屏 / 转场 / 占位图 / 二维码 / 横屏）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `activity_splash.xml` | `splash_text.xml`（窗口背景）已经把 128dp 应用图标居中铺好，布局里的启动文字也是整屏居中 → **文字正好压在图标上** | 文字改贴底居中（`paddingBottom=96dp`），形成"图标居中 + 文案在下" |
| `anim_activity_out_down.xml`（新增）、`styles.xml`、`themes.xml` | 全工程只有 `InstanceActivity.kt` 一处 `overridePendingTransition`，其它页面各用系统默认动画，进出场不一致 | 新增退场动画 + `BiliWindowAnimation`（开/退场各两个），挂到 **8 个主题基类**的 `android:windowAnimationStyle` |
| `mipmap-nodpi/placeholder*.png`、`article_placeholder.png` | 占位图平均色 #DFDFDF（专栏版还是浅蓝 #90E4FD），在纯黑默认主题上每次进列表都闪一块白 | 用自写 PNG 编码脚本重做为深色中性占位（底色与卡片一致的 #2A2A35 + 略亮图形），4.7KB → 0.6KB，**无需改任何代码**（覆盖同名资源） |
| `QRLoginFragment.kt`、`fragment_qr_login.xml` | 默认档 `0.01/0.99` 卡片几乎占满屏宽，而二维码卡片是 1:1 正方形 → 横屏/平板上高度超出视口把状态文字挤出屏；且档位命名与百分比矛盾（LARGE 比 SMALL 小） | 默认改中档 `0.15/0.85`；三档重排为 50%/70%/90% 并同步 Guideline 初值；卡片加 `layout_constraintWidth_max="280dp"` |
| `BaseActivity.kt` | 横屏固定 3 列：窄屏每列不到 210dp（卡片被压扁），大屏又太空 | 按「每列 ≥220dp」换算列数，下限 2 列 |

> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过。
> 说明：占位图是覆盖同名二进制资源，AAPT2 对 PNG 的校验由资源合并任务通过佐证；如对观感不满意，`git checkout app/src/main/res/mipmap-nodpi/placeholder*.png` 即可回退。

### 第十二轮（视觉体验优化 · 批次 4 收尾：加载更多 footer / 菜单当前页高亮）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `adapter/LoadMoreFooterAdapter.kt`（新增）、`RefreshMainActivity.kt`、`RefreshListActivity.kt` | 翻页加载复用了 `swipeRefreshLayout.isRefreshing = true`，于是"加载下一页"表现为**顶部弹出下拉刷新转圈**：既误导（看着像在刷新），也完全看不出还有没有更多 | 新增 footer adapter，基类用 `ConcatAdapter(业务 adapter, footer)` 包装；`goOnLoad()` → 「正在加载…」、`setRefreshing(false)` → 按 `bottom` 显示「没有更多了」/ 不占位。footer 在末尾，业务 `adapterPosition` 语义不变；`notifyItemChanged` 统一判定线程（`setRefreshing(false)` 常来自后台线程） |
| `MenuActivity.kt` | 打开菜单看不出当前在哪一页 | 用 `from` extra 给对应当前页的按钮加主色描边 + 主色文字 |

> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过。
> 风险提示：`ConcatAdapter` 会给 `itemCount` +1，翻页触发阈值（`itemCount - 4`/`itemCount - 3`）会比原来早一项触发，属可接受偏差；直接 `recyclerView.adapter = x` 的页面（如热搜）不会显示 footer（不影响功能）。

### 第十三轮（视觉体验优化 · 公共顶栏全量推广）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| 38 个布局（`activity_search`/`activity_myspace`/`activity_message`/`activity_vote_info`/`activity_setting_*`/`activity_update`/`activity_menu` 等） | 顶栏仍是各自手抄的副本：约 23dp 高、标题 14sp、无背景无分割线；改一次要改 38 处 | 用结构探测脚本（仅当顶栏块内除 `top`/`pageName`/`timeText`(`menuArea`) 之外没有其它控件时才替换）批量改为 `<include layout="@layout/cell_topbar" />`；共 **44 个布局**共用公共顶栏 |
| `MenuActivity.kt` | 菜单页顶栏改用公共顶栏后，图标需要是"收起菜单"的箭头（它不走 `InstanceActivity.setMenuClick`） | 显式 `setTopbarIcon(R.drawable.arrow_up)` |

> 保留自有顶栏的仅剩 2 个：`activity_player.xml`（顶栏含电量/时钟/标题）、`fragment_short_video_page.xml`（含标题）——属合理差异。
> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过（XML 结构正确性由 AAPT2 解析通过佐证）。

### 第十四轮（真机验证发现回归：顶栏吃满整屏）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `cell_topbar.xml` | 第十三轮把 44 个布局换成 `<include layout="@layout/cell_topbar" />` 后，**真机实测顶栏占满整个内容区**（`top` bounds = `[0,114][1080,2394]`，标题被挤到屏幕正中、列表被顶到屏幕外）。根因：顶栏根节点是 `RelativeLayout`，而我给它加的 1dp 分割线用了 `android:layout_alignParentBottom="true"` —— **RelativeLayout 只要含"贴底子元素"，自身 `wrap_content` 就会被撑成父容器高度** | 顶栏根节点改为**竖向 LinearLayout**（高度 `wrap_content`，天然不依赖父容器类型），内层保留一个 RelativeLayout 放标题与时钟（`BaseActivity.setRound()` 会把 `pageName` 的 layoutParams 强转为 `RelativeLayout.LayoutParams`）。同时给 44 个 `<include>` 补上显式 `layout_width/layout_height` 参数 |
| 44 个布局 | `<include>` 未写布局参数（Android 最佳实践要求显式声明） | 批量补 `layout_width="match_parent"` / `layout_height="wrap_content"` |

> **真机验证证据**（设备 `10AC9S2M4L000QL`，1080×2520 / density 480）：
> - 修复后 `top` = `[0,114][1080,261]` → **147px = 49dp** ✓（顶栏 `topbar_height` 48dp + 1dp 分割线）
> - 推荐页：`swipeRefreshLayout` 紧接顶栏（`[0,261]` 起）；视频卡左右留白 36px = 12dp、卡间距 36px = 12dp；`img_cover` = 449×253px → **16:9** ✓；标题 2 行 ✓
> - 视频详情页顶栏同为 49dp ✓（include 跨页面生效）
> - 安装方式：`adb install -r app-arm64-v8a-debug.apk`（覆盖安装，登录状态保留）

### 第十五轮（用户反馈回滚：顶栏 / 占位图 / 转场动画 / 尺寸基准）

用户在真机上确认：**这是手表端应用**，且更满意修改前的观感。按反馈回滚：

| 回滚项 | 处理 | 证据 |
|---|---|---|
| **顶栏恢复修改前样式** | 44 个布局的顶栏块用 `git archive HEAD` 导出的原始文件逐块还原（只替换顶栏块，本轮其它改动保留）；`cell_topbar.xml` 也还原为原来的未被使用模板；移除上一轮新增的 `BaseActivity.setTopbarIcon()` 与各页调用（箭头重新由各布局自己的 `drawableStartCompat` 决定） | `activity_menu.xml` 相对 HEAD **零差异**；设置页真机实测 `top` = `[0,114][1080,190]` → **76px = 25dp**（与原来一致） |
| **占位图恢复原图** | `placeholder.png` / `placeholder_noround.png` / `article_placeholder.png` 全部 `git checkout` 还原 | 字节数回到 4756 / 4327 / 5589，`git status` 该目录无改动 |
| **砍掉页面切换动画** | 移除 8 个主题基类的 `android:windowAnimationStyle`、删除 `BiliWindowAnimation` 样式与 `anim/anim_activity_out_down.xml` | 全工程 grep `BiliWindowAnimation\|anim_activity_out_down\|windowAnimationStyle` 仅命中注释 |
| **菜单恢复居中与原始间隔** | 撤销菜单按钮的 8dp/2dp 间隔与左对齐、去掉强制 `minHeight`（保留"当前页高亮"这一项） | `MenuActivity.kt` |
| **尺寸基准改为手表紧凑** | 基础 token 按手表给（顶栏 30dp、列表留白 6dp、卡片间距 2dp、圆角 6dp、标题 14/15sp）；新增 `values-w300dp/dimens.xml` 给宽屏放大一档（顶栏 36dp、留白 8dp、间距 3dp） | 真机（360dp 宽，命中 w300dp）：顶栏 36dp + 分割线、卡片左右 8dp |

> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过；安装后无崩溃日志。
> 教训：顶栏这类"每页都有"的组件，在没有真机确认前不该做视觉改版；`wrap_content` 的 RelativeLayout 不能放 `layout_alignParentBottom` 子元素（会把高度撑满父容器，见 `docs/architecture-map.md` 第 8.5 节）。

### 第十六轮（两个播放器的性能与逻辑优化）

普通播放器（`activity/player/PlayerActivity.kt`）：

| 位置 | 问题 | 修复方式 |
|---|---|---|
| `:955` | `showLoadingSpeed()` 每次缓冲开始都 `new Timer()` 且不 cancel 旧的，连续缓冲会叠加多个 Timer 线程同时写同一个 `loading_text1`，旧线程永不退出 | 改主线程 `Handler` 自循环（500ms），重复调用先 `removeCallbacks` |
| `:1015` | `progressChange()` 用 `java.util.Timer` 每 250ms tick，tick 内再 `runOnUiThread`（两层跨线程投递）；且每 250ms 一次 `MediaSession.setPlaybackState` 的 Binder IPC | 改主线程 `Handler` 自循环（250ms），去掉全部 `runOnUiThread`；MediaSession 改为**整秒去重**上报 |
| `:1560` | `cancelAllTimers()` 漏 `speedTimer`，退出页面后该 Timer 线程仍在 | 补 `speedTimer?.cancel()`；显式清理 progress/loading/resize 三个 Runnable |
| `:812` | `setDisplay()` 两个分支各自 `new Timer()` 都不 cancel 旧的，旧 timer 会和新的一起调 `MPPrepare` → 重复 `setDataSource`/`prepareAsync` | 分支前统一 `surfaceTimer?.cancel()` |
| `:1114` | `showSubtitle()` 每 tick 都 `setText`/`setVisibility`（各自还带 `runOnUiThread`） | 用 `subtitle_shown_index` 去重；切换字幕轨时复位该标记；去掉 `runOnUiThread`；补循环内 `subtitleCurr` 收敛赋值 |
| `:1005` | `changeVideoSize()` 的 `postDelayed(…, 60)` 无 `removeCallbacks`，快速旋转会叠加 | 改用可复用 `resizePostRunnable` + 先移除（移除时用同一 View） |
| `:1856` | `onProgressChanged` 里包了多余的 `runOnUiThread`（回调本就在主线程） | 去掉包装 |

短视频播放器（`activity/video/ShortVideoPlayerActivity.kt` + `util/VideoPreloadManager.kt`）：

| 位置 | 问题 | 修复方式 |
|---|---|---|
| `:233` | `holders` map 只增不减：holder 被复用到新 position 后旧 key 仍指向它，`pausePlayer(旧pos)` 会暂停错对象（表现为上一页不停、两路声音） | `onViewRecycled` 摘除 map 条目 + 复位 `boundPosition`；回收的恰是活跃页时同步清 `activeHolder` |
| `:245` | `setupPlayerAtPosition` 不更新 `activeHolder`，`pauseCurrent()`/`isCurrentPlaying()` 可能指向过期页面 | 补 `activeHolder` 归位，并先停掉旧活跃页 |
| `:103` | 追加分页用 `notifyDataSetChanged()`：ViewPager2 不保证保持当前页（可能跳页）且会重建全部页面 | 改 `notifyItemRangeInserted(start, items.size)` |
| `:172` | `onStop` 只在 `isFinishing` 时释放播放器，被系统因内存压力回收时播放器泄漏 | `onDestroy` 补 `releaseAll()` 兜底 |
| `:282` | `releaseAll()` 不清 `activeHolder`/`lastVisiblePosition`，释放后仍指向已释放的 holder | 一并复位 |
| `VideoPreloadManager.kt:11` | `allItems` 是普通 `mutableListOf`，后台 `addAll` 与主线程读并存（数据竞争）；`isLoading` 非 `volatile` 导致 `loadMore()` 可能重复拉取；`preloadedItems` 是死字段 | 改 `CopyOnWriteArrayList`；`isLoading` 加 `@Volatile` 并把"检查+置位"前移到调用方线程；删死字段；载体加 `try/finally`（否则 fetch 抛异常会让 `isLoading` 永久为 `true`，之后再也拉不到数据）；`release()` 置 `released` 拦住回流 |

`player/PlayerControlDelegate.kt`：

| 位置 | 问题 | 修复方式 |
|---|---|---|
| `:215` | **长按后手势全失效**（原 P0 待办）：`onLongPress` 置 `isLongPressing = true` 后无任何地方复位，`onScroll` 首行直接 return，音量/亮度/进度手势再也无法恢复 | `onDown` 里复位；顺带去掉 6 处冗余 `abs(...).toFloat()` |

> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过（11 个测试类 / **59 个用例 / 0 失败**）；产出 4 个 debug APK。

> **本轮有一条假设被推翻，记录以免后人重犯**：起初判断"短视频弹幕 XML 解析跑在主线程、是滑动卡顿主因"，并据此把 `DanmakuManager.createParser()` 挪到了后台线程。追库源码后确认该判断**错误**——
> - `BiliDanmakuLoader.load(InputStream)` 只做 `new AndroidFileSource(stream)`（`BiliDanmakuLoader.java:44-46`）；
> - `AndroidFileSource(InputStream)` 只存引用（`AndroidFileSource.java:44-46`）；
> - `BaseDanmakuParser.load(IDataSource)` 只存 `mDataSource`（`BaseDanmakuParser.java:67-70`）；
> - 真解析在 `getDanmakus()` → `parse()`（`BaseDanmakuParser.java:81-89`）**懒执行**，调用点全工程只有 `DrawTask.java:283`，跑在 `DanmakuView` 自己的渲染线程上。
>
> 也就是说 `createParser()` 里根本没有耗时操作，挪到后台**零收益**，反而引入新竞态：`DanmakuLoaderFactory.create(TAG_BILI)` 返回的是**进程级单例** `BiliDanmakuLoader.instance()`，`dataSource` 是它的**实例字段**，`load()` 写字段 + `loader.dataSource` 读字段是一段 check-then-act，并发时两个 holder 会拿到同一个数据源（弹幕串台/解析为空）。
> **已回退该改动**，并在 `DanmakuManager.createParser()` 上补了中文警示注释说明"只能主线程、不可并发"及其原因。

### 第十七轮（播放器性能优化 + 播放内核合并，方案 A + 方案 B）

本轮目标：**以保证播放性能为最高优先级**，把两个播放器的播放内核与弹幕栈合并为一套。
设计文档：`docs/superpowers/specs/2026-09-10-player-core-merge.md`。

**S1 · Surface 就绪事件化（新增 `player/PlayerSurfaceBinder.kt`）**

| 位置 | 问题 | 修复方式 |
|---|---|---|
| `PlayerActivity.kt` `setDisplay()` | 用 `java.util.Timer` 每 200ms 轮询 surface 是否就绪。首播/切清晰度/切分页/切听视频模式**每次都新建一个 Timer 线程**；surface 未就绪时最长等满 200ms 才开始 `prepareAsync`，**直接拖慢首帧** | 新增 `PlayerSurfaceBinder`，改用 `SurfaceTextureListener` / `SurfaceHolder.Callback` 事件回调；已就绪则同步立即回调。删除 `surfaceTimer`、`mSurfaceTexture` 字段与 `Surface`/`SurfaceHolder`/`SurfaceTexture` 三个 import |
| 同上（隐性 bug） | 旧代码是**轮询命中之后**才 `holder.addCallback(...)`，若 surface 早已 created，`surfaceCreated` 再不会触发 —— 那段"重建时重新 setDisplay"的逻辑实际是死的 | 回调在 `initUI` 就注册，现在能正常触发 |

> **线程决策（有意为之）**：`PlayerSurfaceBinder` 保证 `await()` 与所有回调都在主线程执行（`setDisplay()` 会从 onCreate 的 `CenterThreadPool.run` 块里被调用，而 View 状态只能主线程读）。因此 `MPPrepare()` 从 Timer 线程变为**主线程**。保留该选择是因为主线程执行**天然串行化**，能避免用户快速连点切清晰度时两路并发对同一个 `IjkMediaPlayer` 调 `setDataSource`/`prepareAsync`；而 `ijkplayer-java` 的 `setDataSource` 最终只走 native `_setDataSource`（`IjkMediaPlayer.java:400-403`），短视频播放器本来也就在主线程这么做。

**S2 · 新增公共播放内核 `player/VideoPlayerCore.kt`（纯新增，零回归风险）**

多实例安全（非单例、不持有 Activity、`release()` 幂等）、内部自管 Surface、`reload()` 复用同一个 `IjkMediaPlayer` 只 `reset()`+重设 options（省掉切清晰度/切分页的一次 native 播放器创建）、`onPosition(pos, duration)` 高频回调。**目前尚无消费方**，价值待 S4 接入后兑现。

**S3 · 弹幕栈合并（方案 B）**

| 位置 | 问题 | 修复方式 |
|---|---|---|
| `DanmakuManager.kt` | 新版 protobuf 分段弹幕能力只内联在 `PlayerActivity.downdanmuNew()` 里；两个播放器各有一套弹幕配置与回调 | 新增 `loadFromProtobufSegments()`、`prepareEmpty()`（直播空 parser）；`addDanmaku` 扩成与原 `PlayerActivity.addDanmaku(text,color,textSize,type,backgroundColor)` **完全一致**的签名（`PlayerDanmuClientListener` 依赖该签名，一致才能零改动迁移） |
| `PlayerActivity.kt` | 内联 `createParser`×2 + `streamDanmaku`×2 + `mContext` 字段，与 `DanmakuManager` 重复实现同一套 `DanmakuContext` 配置 | 全部删除（含 12 个已无用的 parser import），改为 `bindDanmakuView()` / `releaseDanmaku()` / `prepareDanmaku {}` 三个小助手委托给 `DanmakuManager` |

> **行为保持**：`addDanmaku` 的 time/priority/textSize 公式、六项 `DanmakuContext` 配置、`setMaximumLines`/`preventOverlapping` 映射均与原实现逐项对齐。
> **刻意差异**：`prepared()` 系统提示文案由"弹幕君准备完毕～(是新来的哦～)/(*≧ω≦)"统一为"弹幕准备完毕"。纯文案。

**S5 · 短视频滑动期开销（部分完成）**

| 位置 | 问题 | 修复方式 |
|---|---|---|
| `ShortVideoPlayerActivity.kt` `bind()` | 每次绑定都重建 `GestureDetector` + `ScaleGestureDetector`、重设 `setOnTouchListener` 与全部按钮/SeekBar 监听器 ——滑动时纯浪费的分配 | 全部移到 `PageHolder` 的 `init {}` 一次性注册，`bind()` 只做条目数据绑定 |
| `IjkPlayerBridge.release()` | `stop()` → `reset()` → `release()`，而 `stop()` 是等待型调用，这段跑在主线程的 `onViewRecycled` 上，每滑一页阻塞一次 | 去掉 `stop()`（`reset()` 已足够） |
| `ShortVideoPlayerActivity` | 缓冲指示器每次 state 发射都无条件 `setVisibility`；进度文本每 250ms 都拼字符串 + `setText`（而显示值一秒才变一次） | 按"目标可见性是否变化"/"整秒是否变化"才刷；`releasePlayer()` 一并复位这些标记 |

> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过（11 个测试类 / **59 个用例 / 0 失败**），APK 与 class 时间戳均为当次构建产物。

### 第十八轮（第十七轮引入的弹幕回归修复 + 短视频弹幕接入新版接口）

**回归根因（真机复现："普通视频的弹幕间歇性不出现"）**

S3 把 `PlayerActivity` 内联的弹幕栈换成 `DanmakuManager` 时，我删掉了一个**载荷性的守卫**：

```kotlin
// 改前（PlayerActivity 内联）：播放器未就绪时根本不更新 timer
override fun updateTimer(timer: DanmakuTimer) {
    if (ijkPlayer != null && isPrepared) timer.update(ijkPlayer!!.currentPosition)
}

// 改后（DanmakuManager）：无条件读位置
override fun updateTimer(timer: DanmakuTimer) { timer.update(onCurrentPositionMs()) }
// 而 lambda 写成了 { ijkPlayer?.currentPosition ?: 0L }
```

`updateTimer` 运行在 **DanmakuView 的渲染线程**上，而主线程在切清晰度/切分页/切听视频模式时会 `release()` 并重建 `IjkMediaPlayer`。`IjkMediaPlayer` 的 native 层**不是线程安全的**，在那个窗口期从渲染线程读 `currentPosition` 可能拿到脏值；一旦脏值被灌进 `DanmakuTimer`，整批弹幕会被判定为"已过期"而**一条都不显示**。因为是竞态，所以表现为**间歇性**——这也是为什么中途某次重装后"看起来修好了"，其实从未修复。

| 位置 | 修复方式 |
|---|---|
| `DanmakuManager.updateTimer` | 回调返回负数时**跳过**本次更新；KDoc 里写明"播放器未就绪/正在重建必须返回负数" |
| `PlayerActivity.bindDanmakuView` | 位置回调改为 `if (isPrepared) ijkPlayer?.currentPosition ?: -1L else -1L` |
| `ShortVideoPlayerActivity` 的 `danmakuManager` 位置回调 | 同样加 `if (isPrepared) ... else -1L` 守卫 |
| `PlayerActivity.isPrepared` / `PageHolder.isPrepared` | 加 `@Volatile`（主线程写、弹幕渲染线程读，原代码就缺这个可见性保证） |
| `DanmakuManager.configureAndPrepare` 的 `prepared()` 回调 | 包 try/catch：该回调同样在渲染线程上，异常逸出会打断渲染线程，症状同样是"一条弹幕都不显示"且上层无任何报错 |

**短视频"一直没有弹幕"的根因（既有问题，非本轮引入）**

`PlayerApi` 给短视频设的弹幕地址是 `https://comment.bilibili.com/{cid}.xml`（**旧版 XML 接口**，B站已基本停用），而普通播放器早已切到 `DanmakuApi.getAllVideoDanmaku()` 的 **protobuf 分段接口**（`NEW_DANMAKU_API` 默认开启），短视频从未跟上。

| 位置 | 修复方式 |
|---|---|
| `ShortVideoPlayerActivity.loadDanmaku` | 改为优先走 `DanmakuApi.getAllVideoDanmaku(aid, cid, 时长)` + `loadFromProtobufSegments()`；分段接口确实拿不到数据时才回退旧版 XML（回退逻辑抽成 `loadDanmakuFromXml`） |

> 这正是"合并弹幕栈（方案 B）"应该带来的收益：能力合并之后，短视频补新版弹幕只需要换一个调用。

**本轮其他真机发现（未修，另案）**：短视频 story feed 接口在真机上返回 `code=-400 请求错误`（`ShortVideoFeedApi.fetchStoryFeed`），目前靠 `fetchIndexFeed` 兜底才有内容。

> 验证：`:app:assembleDebug` + `:app:testDebugUnitTest` 通过（59 个用例 / 0 失败），已安装到真机 `10AC9S2M4L000QL`。

### 第十九轮（设置页二级列表崩溃 + 视觉批次遗留项按用户反馈回滚）

**1. 设置页二级列表必崩（用户报"修复设置页面第二级列表报错"）**

| 位置 | 问题 | 修复方式 |
|---|---|---|
| `adapter/SettingsAdapter.kt` | 第十二轮为做"加载更多 footer"给基类套了 `ConcatAdapter`。`ConcatAdapter` 会按**包装顺序重新映射** `getItemViewType()` 的返回值，导致原本的 `-1`/`-2` 负值 viewType 在 `onBindViewHolder` 里落到错误的 Holder 上，抛 `ClassCastException: SettingsAdapter$NavHolder cannot be cast to SettingsAdapter$SwitchHolder`（`SettingsAdapter.kt:131`，由 `CustomLinearManager.onLayoutChildren` 触发） | 去掉基类的 `ConcatAdapter` 包装；`viewType` 改为非负常量 `TYPE_SWITCH=0 … TYPE_TITLE=9`；`onBindViewHolder` 改为 `when (holder) { is NavHolder -> … }` **按类型分派**，不再盲转 |
| `activity/base/RefreshListActivity.kt`、`RefreshMainActivity.kt` | 去掉 footer adapter 后，"没有更多了"没有承载者 | 改为布局里放一个 `@+id/loadMoreTip` TextView（`gone` / 11sp / 次要色），`setRefreshing(false)` 按 `bottom` 置「没有更多了」，`goOnLoad()` 置「正在加载…」 |
| `adapter/LoadMoreFooterAdapter.kt` | 已无引用 | 删除 |

> 顺带修掉 `ConcatAdapter` 的两个副作用：`itemCount` 不再 +1（翻页触发阈值回到原语义）、直接 `recyclerView.adapter = x` 的页面也不再被排除在 footer 之外。
> 用户真机验证：「测试 ok」。

**2. 播放器进度条与周边控件间距变大（用户报"间距太大，还原原来的间距"）**

批次 0 曾把进度条 `layout_height` 由 `20dp` 抬到 `48dp` 并加 `paddingVertical="14dp"`，同时给播放器 30 个按钮补 `minWidth/minHeight="@dimen/touch_min"`。真机上表现为进度条与上下控件之间多出明显空隙。

| 位置 | 处理 |
|---|---|
| `layout/activity_player.xml` | 进度条还原为 `layout_height="20dp"`、去掉 `paddingVertical`；移除全部 30 行 `minWidth/minHeight="@dimen/touch_min"`。相对 HEAD 仅剩主题色差异（`@color/player_progress_bg` 等） |

> 注：48dp 最小触控仍在 `bottom_bar_multi_select.xml` / `fragment_qr_login.xml` / `bar_quality_select.xml` / 两个 dialog 中保留（用户本次只指出播放器，且手表端以小屏可点为准）。

**3. 菜单页"当前所在页面"按钮变色（用户报"选择某个页面，页面按钮会变色"）**

第十二轮加的「当前页高亮」（`btn == from` 时设主色描边 + 主色文字）在实际使用中被判定为 bug：从某页返回菜单时，该页按钮**长期带色**，看着像被选中/坏掉。

| 位置 | 处理 |
|---|---|
| `activity/MenuActivity.kt` | 删除 `if (btn == from)` 整段（`strokeWidth` / `strokeColor` / `setTextColor`）及相关 `ThemeManager`、`ToolsUtil` import；菜单恢复"零间隔 + 文字居中 + 无高亮"的原始观感，`MenuActivity.kt` 相对 HEAD **零差异** |

> 顶栏上的 `setPageName(from)` 保留（它不是按钮变色，只是标题跟着当前页走）。
> 真机验证（设备 `10AC9S2M4L000QL`，以 `--es from recommend` 冷启动）：菜单 12 个按钮正常渲染、`推荐` 按钮无描边、无 `FATAL EXCEPTION` / `ClassCastException`。

> 构建提示：本轮踩到 AGENTS.md 记录的 build cache 坑两次——一次是 `:app:clean` 与 `:app:assembleDebug` **写在同一次 Gradle 调用**里会因配置缓存复用出现 `navigation.json NoSuchFileException` 竞态；一次是资源合并 `FROM-CACHE` 回放陈旧结果，报一片 `Unresolved reference 'R.layout.xxx'`。可行命令是**分两次调用 + 关掉配置缓存**：
> ```bash
> ./gradlew.bat :app:clean --offline --no-configuration-cache
> ./gradlew.bat :app:assembleDebug --offline --no-build-cache --no-configuration-cache
> ```

**4. 搜索框样式（用户报"搜索框的样式给我完全回退"）**

批次 3 把搜索框改成了"胶囊形 + 主题色"的新样式，真机上用户要求完全回退。搜索框**只出现在搜索页**（全工程 grep `background_searchbar` / `keywordInput` 仅命中 `activity_search.xml`），涉及两个文件：

| 位置 | 我改成了 | 回退为（= HEAD） |
|---|---|---|
| `drawable/background_searchbar.xml` | `<solid ?attr/colorSurface>` + `1dp ?attr/colorControlNormal` 描边 + `corners 24dp`（胶囊） | `#80242424` 填充 + `2dp #cc808080` 描边 + `corners @dimen/card_round`（圆角矩形） |
| `layout/activity_search.xml` 的 `keywordInput` | `minHeight=@dimen/touch_min`(48dp)、`paddingStart/End=16dp`、`textSize=@dimen/text_subtitle`(13/14sp) | `minHeight=42dp`、`paddingStart/End=8dp`、`textSize=13sp` |

> `card_round` 在 HEAD 与当前 `values/dimens.xml` 中都是 6dp（`values-w300dp` 的 10dp 只在手机生效），所以回退后手表端观感与改动前完全一致。
> 同文件里 `#6000` → `@color/scrim` 那处**一并回退**（属于"完全回退"范围）。两者视觉等价：`#6000` 是 `#ARGB` 写法 = 40% 黑，`@color/scrim` = `#60000000` = 37.6% 黑，差值不可辨。
> 真机验证：`keywordInput` bounds 高 **126px = 42dp**（改后为 48dp=144px），左边距 18px = 6dp，无异常日志。

> 本轮**未动**这三处（不属"搜索框"本身）：`activity_setting_search.xml`（4 处 `#00000000`→`@android:color/transparent`，颜色等价、零视觉差异）、`item_hot_search.xml`（热搜条目的榜单序号色改主题主色 + 图标加 8dp 右间距）、`layout-v17/-v22/item_hot_search.xml`（我删掉了这两个旧副本，而 `layout-v22` 在 API≥22 的机器上**优先级高于 `layout/`**，等于让热搜条目换了实现——如需还原请告知）。

### 第二十轮（手表端启动路径优化 + MultiDex 死代码清理）· 26.09.10

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `PerformanceManager.kt:136` | `Logu.i(..., "…score=${getHardwareScore()}")` 的开关在 `Logu` **函数体内**判断，参数先求值——release 版日志已关闭，每次冷启动仍在主线程重跑 `getHardwareScore()`（`RandomAccessFile` 读 `/sys/.../cpuinfo_max_freq`，失败再读 `/proc/cpuinfo` 全文并现场 `Pattern.compile`），而该结果只进日志、不参与任何逻辑 | 包一层 `if (Logu.LOGI_ENABLED)`，让求值真正惰性；debug 诊断信息不变 |
| `app/build.gradle`、`BiliTerminal.java`、`BiliTerminalApp.kt` | `minSdk 24` 下 ART 原生支持 multidex，`MultiDex.install()` 在 API 21+ 首行即返回；依赖与覆写均为死代码 | 移除 `androidx.multidex` 依赖、两处 `MultiDex.install` 及对应 `attachBaseContext` 覆写 |

> `multiDexEnabled true` 保留未动（minSdk ≥ 21 下无副作用，删它没有收益）。
> 同轮做了后续 `BiliTerminal.java → BiliTerminal.kt` 迁移的前置改动：22 个 Kotlin 文件共 35 处 `BiliTerminal.context` 补 `!!`（`Glide.with/get` 的参数带 `@NonNull`，字段改可空后会全部编译失败）。Java 侧 20 处字段访问保持不变。（Kotlin 转换见第二十一轮。）

### 第二十一轮（`BiliTerminal.java` → `BiliTerminal.kt`）· 26.09.10

| 项 | 处理方式 |
|---|---|
| `context` / `DPI_FORCE_CHANGE` | companion + `@JvmField` → 仍生成真正的 `public static` 字段，Java 侧 20 处 `BiliTerminal.context` 字段访问**零改动**；不引入 getter 包装层，避免每次访问多一次静态方法调用（`@JvmField` 与 `lateinit` 互斥，编译器明确报 `JvmField cannot be applied to lateinit property`，故选可空字段 + Kotlin 侧 `!!`） |
| `forceUpdateBlocking` 等 5 个可变状态 | companion 内 `@Volatile private var`（原为 `private static volatile`） |
| `getVersion()` | 加 `@Throws(PackageManager.NameNotFoundException::class)` 保留受检异常签名；`versionCode` 加 `@Suppress("DEPRECATION")` 消除每次构建的告警 |
| `getFitDisplayContext(old)` | 签名改为 `(Context?) -> Context?`——旧 Java 无注解属平台类型，而 `SplashActivity.attachBaseContext` 的入参声明为 `Context?`，写成非空会编译失败；内部 `old!!` 仍在 `try` 内，NPE 被 `catch (e: Exception)` 吞掉后返回 `old`，与 Java 行为逐字一致 |
| `registerActivityLifecycleCallbacks` | 改为 `object : Application.ActivityLifecycleCallbacks`（8 个方法须全实现，不能用 SAM lambda） |
| `checkAppUpdate` | 改为 `UpdateManager.checkUpdate(onResult = …, onError = {})`，`kotlin.Unit.INSTANCE` 互操作样板消失；空 `catch (Exception)` 原样保留 |
| 文件 | 新增 `BiliTerminal.kt` 并删除 `BiliTerminal.java`（同名同类不可并存，否则 duplicate class） |

**互操作契约用 `javap` 核实**（`app/build/tmp/kotlin-classes/debug`）：`public static android.content.Context context;`、`public static boolean DPI_FORCE_CHANGE;`、`getVersion() throws PackageManager$NameNotFoundException`、`jumpToVideo/jumpToArticle/jumpToUser/getFitDisplayContext/setInstance/getInstanceActivityOnTop/clearForceUpdate/isDebugBuild` 全部为 `public static final`。

### 第二十二轮（主题系统：色表缓存 + 主题单测网）· 26.09.11

主题系统重构（拆成「配色 / 卡片圆角 / 字体」三个独立模块）的**前置两步**。只做两件小步、可独立验证的事，**不改任何视觉**。

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `ui/theme/ThemeManager.kt:351` | `getCurrentTheme()` 每次调用都读一遍 SharedPreferences 再跑一遍 `when`，而 `PRIMARY` 等 **36 个属性 getter 全部走它**；列表滚动时一个 item 就要调多次（`setTextColor` 40 处、其中 26 处取自 ThemeManager）——热路径上的重复 IO | 加 `@Volatile` 色表缓存；主题 key 的**唯一写入点** `setTheme()` 里置 null 失效；进程被杀后缓存为 null 自动重算 |
| `ui/theme/ThemeManager.kt:496` | 注释称「用 `apply()` 存在读到旧值的窗口」——**该说法不成立**：`SharedPreferences.Editor.apply()` 会**同步更新内存映射**，只把落盘放到异步 | 仅订正注释，**不改行为**（保留 `commit()`；代价是每个用户动作一次同步写盘，可忽略）。订正理由与代价已写进注释 |
| `app/src/test/…/util/NetWorkUtilTest.kt` | 私有内部类 `FakeSharedPreferences` 即将被第二个测试复用，否则又是一份拷贝 | 提取为共享助手 `app/src/test/…/util/FakeSharedPreferences.kt`，并加 `stringReadCount` 计数供性能断言 |

**新增 `app/src/test/java/com/RobinNotBad/BiliClient/ui/theme/ThemeManagerTest.kt`（14 个用例）**：此前主题/配色相关单测为 **0** 个，而该系统的历史 bug 全是「改一处漏一处」型（4 份 `key → 值` 的映射各写各的）。钉住的事：

| 钉住的事 | 为什么值钱 |
|---|---|
| 7 套 key → 7 个**互不相同**的 style | `themeResId` 漏一条分支会**静默回落到 else 的 B站粉**，不报任何错 |
| 7 套 key → 各自色表（比 PRIMARY/BACKGROUND/TEXT_PRIMARY/CARD/STATUS_BAR_COLOR） | 同上，`getCurrentTheme` 的静默回落 |
| 7 套中文显示名逐条比对 | 防分支互换（知乎蓝被标成「爱奇艺绿」） |
| 无 key 时默认值 = **经典终端** | **关键**：经典终端与 B站粉的 `PRIMARY` 恰巧都是 `0xFFFF6699`，只比 PRIMARY 发现不了「默认值静默变成 B站粉」，必须比 `BACKGROUND` |
| 7 份色表两两不同 | 防「复制一个 object 却忘了改值」 |
| `PREF_KEY_THEME == SettingsKeys.THEME`、`THEME_DEFAULT ∈ 可选主题` | key 单一真源；默认主题必须能被选回来 |
| 换主题后色表跟着变且已落盘 | 步骤 1 缓存失效的守卫 |
| 501 次 getter 只读 **1** 次 SharedPreferences | 把步骤 1 的性能目标本身变成断言，防将来被改回直读 |

> 缓存失效的**不变量**已写进 `docs/architecture-map.md` §7.3：将来若给主题 key 增加第二个写入路径，必须同步失效缓存，否则改主题后色表不跟着变、且在 `onResume` 重建后依然错。

### 第二十三轮（外观三模块重构 · 步骤 2：`ui/appearance/` 门面骨架）· 26.09.11

外观系统拆成「配色 / 卡片圆角 / 字体」三个独立模块的第 2 步。**本步只新增文件与 key，不移动任何既有逻辑、不改任何视觉**——圆角与字体尚无消费方，配色仍由 `ThemeManager` 拥有。

| 文件 | 内容 |
|---|---|
| `ui/appearance/AppearanceManager.kt`（新增） | 门面：`Appearance` 快照 + 外观版本号（`appearance_version`）+ **唯一写入入口**（`setTheme`/`setCornerRadius`/`setFontScale`/`setFontFamily`，各自递增版本号）。**刻意不缓存快照**——见下 |
| `ui/appearance/CornerStyle.kt`（新增） | 圆角两档 `square`（默认）/ `rounded` + 候选值/显示名/`normalize`/`current`。只放纯逻辑与读取，**不放写入** |
| `ui/appearance/FontStyle.kt`（新增） | 字号 4 档 + 字族 2 选，两个独立 key；`scaleFactor`/`fontFamilyValue` 为纯函数 |
| `util/SettingsKeys.kt` | 加 `UI_CORNER_RADIUS` / `UI_FONT_SCALE` / `UI_FONT_FAMILY`（配色沿用既有 `THEME`，不另开 key 以保持存档向前兼容） |
| `ui/theme/ThemeManager.kt:517` | `setTheme` 改为转发给 `AppearanceManager.setTheme`（落盘 + 递增版本号），自己仍负责清色表缓存 |

**分层约定（已写进 `docs/architecture-map.md` §8.7 与 `AGENTS.md`）**：模块只放候选值常量/显示名/纯函数/读取，**写入一律走门面**——否则「递增版本号」迟早漏一处；门面只做「快照 + 写入 + 版本号」，**绝不做几何计算**。

**三个刻意的设计决定**

1. **`snapshot()` 不缓存**。它是「每次 Activity 创建读一次」的冷路径，缓存收益为零；而缓存失效点会随模块增加而变多（配色已有 `setTheme`，圆角/字体还会有各自的写入点），是一类只会引入 bug 的复杂度。热路径的重复读取问题已由第二十二轮的色表缓存单独解决。守卫测试：`snapshot_isNotCached_staleReadsAreImpossible`。
2. **版本号用 Int 而非「每模块一个字段」**。现有 `BaseActivity` 只记一个 `appliedTheme` 字符串，加到第 4 个模块时那种写法必然要改 `BaseActivity`；版本号让 Activity 只比一个 Int，新增模块不需要动基类。
3. **`FontStyle.scaleFactor(SCALE_DEFAULT)` 恒为 1.0f**。下游靠这个短路来兑现「默认档位零运行时开销（不遍历视图树）」；这个不变量一旦被破坏不会有任何报错，故单独设守卫测试 `scaleFactor_standardIsExactlyOne`。

**新增测试 30 个用例**（`CornerStyleTest` 7 / `FontStyleTest` 11 / `AppearanceManagerTest` 12）。重点钉住：档位与显示名逐项对应（错位会让用户选「方角」得到「圆角」）、未知存档值回落默认（否则设置页显示空白）、**每个写入点都必须递增版本号**（漏了就是「改了设置但页面不刷新」，且手工测试时容易被设置页自身的 `recreate()` 掩盖）。

> 尚未接入：`BaseActivity` 仍在比 `appliedTheme` 字符串，**未使用版本号**；接入随圆角模块落地一起做（届时一次改动即可）。

### 第二十四轮（外观三模块重构 · 步骤 3：配色模块迁入 `ui/appearance/`）· 26.09.11

把配色从 `ui/theme/ThemeManager.kt` 迁到 `ui/appearance/ColorScheme.kt`（用 `git mv`，历史保留），并确立**「模块只读、门面写入」**的分工。**不改任何视觉**。

| 项 | 处理方式 |
|---|---|
| 文件迁移 | `ui/theme/ThemeManager.kt` → `ui/appearance/ColorScheme.kt`，`object ThemeManager` → `object ColorScheme`；`ui/theme/` 目录删除 |
| 写入收口 | `ColorScheme.setTheme()` **删除**，写入统一走 `AppearanceManager.setTheme()`（落盘 + `ColorScheme.invalidateCache()` + 递增版本号）——避免「模块自己写、门面不知道」导致版本号漏记 |
| 缓存失效 | 新增 `ColorScheme.invalidateCache()`，唯一调用者是 `AppearanceManager.setTheme()`；不变量已写进 `architecture-map.md` §7.3 |
| 依赖方向 | `AppearanceManager.snapshot()` 改读 `ColorScheme.getCurrentThemeName()`，此前对 `ThemeManager` 的反向引用消失 |
| 调用点迁移 | 27 个文件的 import 与引用由 `ui.theme.ThemeManager` 改为 `ui.appearance.ColorScheme`；`StringUtil.java` 的 `ThemeManager.INSTANCE.` 一并处理；`SettingGroupActivity` 的主题写入改调 `AppearanceManager.setTheme()` |
| 测试 | `ThemeManagerTest.kt` → `ui/appearance/ColorSchemeTest.kt`（类名与包名同步） |

> 「配色模块」的**功能**部分（把 7 套重复的 `themes.xml` 组件样式合并为一份 `?attr/` 版本）尚未开始，留待后续，需真机逐套验证。
> 注意：本轮**没有**采用「保留 `ThemeManager` 作转发壳」的方案——转发需要手写 ~70 个成员且易错，直接迁移调用点由编译器兜底，且不留过渡代码。

### 第二十五轮（外观三模块重构 · 步骤 4：圆角模块落地）· 26.09.11

**第一个用户可见的外观模块**。「方角 / 圆角」两档可在设置页切换并即时生效。

**机制（为什么走主题属性）**：`dimen` 编译期固定、`shape drawable` 读不到主题，
**只有主题属性 `?attr/` 能被 `theme.applyStyle()` 覆盖**。所以：

| 文件 | 改动 |
|---|---|
| `res/values/styles.xml` | 声明 `<attr name="appCornerRadius" format="dimension"/>`；定义两个覆盖样式 `Appearance_CornerSquare`/`Appearance_CornerRounded`；`CardStyle`/`CardStyleLight`/`ButtonStyle`/`ButtonStyleLight` 的圆角改引用 `?attr/appCornerRadius`（放在已有文件里，**不新增 res 文件**，避免 build cache 回放坑） |
| `res/values/themes.xml` | 5 套主题的 10 处硬编码 `12dp` + `CardStyleTerminal` 的 2 处 `@dimen/card_round` 全部改为 `?attr/appCornerRadius`；**每套主题补一条 `appCornerRadius=@dimen/card_round` 作兜底**（8 处），使不走 `BaseActivity` 的裸 Activity 拿到「方角」而非解析失败的 0dp |
| `res/values/dimens.xml`、`values-w300dp/dimens.xml` | 新增 `card_round_large`（手表 12dp / 宽屏 16dp）；**删除三个 0 引用的死 token** `radius_card`/`radius_small`/`radius_chip`（含宽屏覆盖） |
| `ui/appearance/CornerStyle.kt` | 新增 `overlayStyleResId()`：档位 → 覆盖样式 |
| `activity/base/BaseActivity.kt` | `setTheme()` 之后、inflate 之前 `theme.applyStyle(CornerStyle.overlayStyleResId(), true)`（`force=true` 必需，属性已在主题里定义过）；**`appliedTheme` 字符串换成 `appliedAppearanceVersion` Int**，`onResume` 只比一个 Int |
| `SettingGroupActivity` + `SettingsIndex` | 新增「卡片圆角」设置行（两处；独立「外观设置」页面留待步骤 6） |
| `CornerStyleTest` | 新增 3 个用例覆盖 `overlayStyleResId` 的映射/回落/跟随设置 |

**性能**：圆角模块的运行时成本是 `applyStyle` **一次 O(1) 调用**，无任何视图遍历，
不碰 `RecyclerView` 绑定路径（手表性能优先）。

**默认档位的观感影响（订正早先「零变化」的说法）**：默认 `square` 对**默认主题「经典终端」零变化**；
但**另外 6 套主题的卡片圆角会从 12dp 变为 6dp**——那 12dp 是主题化改造时各抄一份 `CardStyle`
引入的漂移，不是刻意取值，本次借模块化收敛回原项目取值。

**遗留（明确登记，见 `architecture-map.md` §8.7.1）**：
- **10 个 shape drawable 仍直接用 `@dimen/card_round`，不跟随档位**（搜索框、输入框、灰卡、私信发送框等）。
  `shape` 的 `<corners>` 读不到主题属性；修法需先真机验证（改控件 or 一次条件性遍历），本轮不做。
- layout 级内联圆角（头像 28dp、投票按钮 18dp、三个 8dp cell、`item_account` 12dp）按设计豁免，不跟随档位。

### 第二十六轮（外观三模块重构 · 步骤 6：独立「外观设置」页面）· 26.09.11

把外观设置从「界面与外观」分组里拆出来，成为独立一屏。

**形态选择**：本仓库的「独立设置子页面」有两种形态——
① 独立 Activity（「菜单设置」`SettingMenuActivity`，改三处含 manifest 注册）；
② **`SettingGroupActivity` 的另一个 `group_type` 分组**。
外观设置只有若干列表项、无自定义交互，故走 ②：**不需要新 Activity、manifest、布局**，
对用户同样是独立一屏。这是本轮唯一需要判断的地方。

| 文件 | 改动 |
|---|---|
| `activity/settings/SettingGroupActivity.kt` | 新增文件级常量 `GROUP_APPEARANCE = "appearance"`；`buildContent` 加分支；新增 `buildAppearanceGroup()`（`title("配色")` + 主题配色、`title("圆角")` + 卡片圆角）；`buildUIGroup()` 里原来那两处 `listChoose` **删除**，改为一个 `nav(R.drawable.icon_ui, "外观设置", …)` 跳转本页的 appearance 分组 |
| `activity/settings/SettingsIndex.kt` | 新增「外观设置」条目；「主题配色」「卡片圆角」两项由 `openGroup("ui", …)` 改指 `openGroup(GROUP_APPEARANCE, …)`，全局搜索仍能直接定位到项 |
| `AGENTS.md` | 「新增设置子页面」约定补充为**两种形态**，并写明选择依据 |
| `docs/architecture-map.md` §8.6 | 补充「放哪个分组」与「外观类设置的写入必须走门面」 |

> **字体模块的设置项刻意没放**：`FontStyle`（字号 4 档 + 字族 2 选）尚未接入渲染路径，
> 放出来就是一个点了没反应的开关。接入后在本页追加 `title("字体")` 一段即可。
> 第一版按设计**不做实时预览**——预览必须复用与真实页面同一套应用逻辑，否则会骗人。

---

## 三、审查前已修复（本次核查确认，无需改动）

| 文件 | 问题 | 防御措施 |
|---|---|---|
| `GetIntentActivity.kt` | 外部 Intent 直接 `!!`/`toLong()` 崩溃 | `toLongOrNull()` + `getLongExtra` 默认值 |
| `ImageViewerActivity.kt:31` | 空 Intent 崩溃 | `isNullOrEmpty()` 检查 |
| `CookieRefreshApi.java:99` | parseLong 空值崩溃 | 空值回退旧 mid |
| `FavoriteApi.java:285` | 未登录收藏 substring 越界 | 长度检查 |
| `LikeCoinFavApi.java:48` | 同上（重复实现） | 已修复 |
| `Reply.java:75` | location substring(5) 越界 | `length() > 5` 判断 |
| `OpusParagraph.java:98-100` | 空 blockquote setSpan 越界 | 长度检查 |
| `PlayerActivity.kt:1496` | onDestroy 提前 return 跳过清理 | 无条件清理 |
| `UpdateManager.kt:141-158` | 断点续传 200 响应追加损坏 | 丢弃旧残片从头写 |
| `LocalPageChooseActivity.kt:78-97` | 后台线程改列表 + 主线程 notify | 分离数据操作与 UI notify |
| `ToolsUtil.java:71` | 弹幕颜色错误（字符串拼接而非 RGB888） | `color & 0xFFFFFF` |
| `DanmakuApi.java:31,43` | 弹幕内容未 URL 编码 | `URLEncoder.encode(msg, "UTF-8")` |
| `PrivateMsgApi.java:219` | 私信内容未 URL 编码 | `URLEncoder.encode(content, "UTF-8")` |
| `ReplyApi.java:153,231,232` | 评论内容未 URL 编码 | `URLEncoder.encode(text/pictures, "UTF-8")` |

---

## 四、构建验证状态

| 时间 | 任务 | 结果 |
|---|---|---|
| 2026-09-07 15:45 | `:app:assembleDebug` | ✅ 成功（产出 4 个 APK） |
| 2026-09-07 16:01 | `:app:assembleDebug`（第二轮修复后） | ✅ BUILD SUCCESSFUL in 36s |
| 2026-09-07 16:20 | `:app:assembleDebug`（第三轮修复后） | ✅ BUILD SUCCESSFUL in 37s |
| 2026-09-07 16:45 | `:app:assembleRelease`（R8 压缩 + 签名） | ✅ BUILD SUCCESSFUL |
| 2026-09-10 | `:app:assembleDebug` + `:app:testDebugUnitTest`（视觉批次 0 前半） | ✅ 通过（需 `--no-build-cache`） |
| 2026-09-10 | `:app:assembleDebug` + `:app:testDebugUnitTest`（第十六轮：两个播放器性能与逻辑优化） | ✅ BUILD SUCCESSFUL in 1m 4s；11 个测试类 / 59 个用例 / 0 失败；4 个 debug APK |
| 2026-09-10 | `:app:assembleDebug` + `:app:testDebugUnitTest`（第十七轮：播放内核合并 S1/S2/S3 + 短视频 S5 部分） | ✅ BUILD SUCCESSFUL；59 个用例 / 0 失败；APK 29.85 MB |
| 2026-09-10 | `:app:assembleDebug` + `:app:testDebugUnitTest`（第二十轮步骤 1：惰性日志 + MultiDex 清理） | ✅ BUILD SUCCESSFUL in 1m 26s；59 用例 / 0 失败 |
| 2026-09-10 | `:app:assembleDebug` + `:app:testDebugUnitTest`（第二十轮步骤 2：35 处 `BiliTerminal.context!!`） | ✅ BUILD SUCCESSFUL in 47s；11 个测试类 / 59 用例 / 0 失败 |
| 2026-09-10 | `:app:assembleDebug` + `:app:testDebugUnitTest`（第二十一轮：Application 入口转 Kotlin） | ✅ BUILD SUCCESSFUL in 55s；11 个测试类 / 59 用例 / 0 失败（`--no-build-cache` 实跑） |
| 2026-09-10 | `:app:assembleRelease`（R8 + ABI 分包，验证入口类 keep 规则） | ✅ BUILD SUCCESSFUL in 2m 29s；universal 23.04 MB / arm64 9.97 / armeabi-v7a 8.54 / x86 10.96；`seeds.txt` 含 `BiliTerminal`，`mapping.txt` 中该类未被重命名 |
| 2026-09-11 | `:app:testDebugUnitTest`（第二十二轮步骤 0：主题单测网） | ✅ 12 个测试类 / 72 用例 / 0 失败；`ThemeManagerTest` 13 用例新通过 |
| 2026-09-11 | `:app:testDebugUnitTest` + `:app:assembleDebug`（第二十二轮步骤 1：色表缓存） | ✅ BUILD SUCCESSFUL；12 个测试类 / 73 用例 / 0 失败（`ThemeManagerTest` 增至 14 用例，含「501 次 getter 只读 1 次 SharedPreferences」的性能断言）；行为与视觉无变化 |
| 2026-09-11 | `:app:testDebugUnitTest` + `:app:assembleDebug`（第二十三轮：外观三模块门面骨架） | ✅ BUILD SUCCESSFUL in 11s / 6s；15 个测试类 / 103 用例 / 0 失败（新增 `CornerStyleTest` 7、`FontStyleTest` 11、`AppearanceManagerTest` 12）；无 `res/` 改动，圆角与字体尚无消费方 → UI 零变化 |
| 2026-09-11 | `:app:testDebugUnitTest` + `:app:assembleDebug`（第二十四轮：配色模块迁入 `ui/appearance/`） | ✅ BUILD SUCCESSFUL in 1m 16s（编译）/ 13s（测试+打包）；15 个测试类 / 103 用例 / 0 失败；27 个调用点迁移，`ui/theme/` 目录删除 |
| 2026-09-11 | `:app:assembleDebug` + `:app:testDebugUnitTest`（第二十五轮：圆角模块落地） | ✅ BUILD SUCCESSFUL in 1m 56s；15 个测试类 / 106 用例 / 0 失败（`CornerStyleTest` 7→10）；`R.txt` 已生成 `attr appCornerRadius` 与两个覆盖样式；16 处圆角定义全部改为 `?attr/` 引用，0 残留硬编码；**待真机验证两档切换** |
| 2026-09-11 | `:app:assembleDebug` + `:app:testDebugUnitTest`（第二十六轮：独立「外观设置」页面） | ✅ BUILD SUCCESSFUL in 12s；15 个测试类 / 106 用例 / 0 失败；纯设置页重组，无 `res/` 改动、无新 Activity/manifest 变更 |

> 第二轮修复的 4 个文件（QRLoginFragment/CaptchaWebViewActivity/LocalListActivity/VideoInfoFragment）已重新编译验证通过。APK 时间戳更新至 16:01:21，universal 包 31.04 MB。
> 第三轮修复的 2 个文件（PrivateMsgActivity/NetWorkUtil）已重新编译验证通过。构建仅有 1 个 Hilt 处理选项无关警告，不影响产物。
> Release 构建成功：universal 22.94 MB / arm64-v8a 9.87 MB / armeabi-v7a 8.44 MB / x86 10.86 MB。

### 设备安装

| 操作 | 结果 |
|---|---|
| 卸载旧版（签名不一致） | ✅ Success |
| 安装 debug universal 版 | ✅ Success（设备 10AC9S2M4L000QL） |

---

## 五、待处理问题（按优先级）

### P0 剩余 Critical（待排查）

- [x] 私信会话列表逻辑写反：`PrivateMsgApi.java:157`（26.09.08 已修，第四轮）
- [ ] 下载并发竞态：`DownloadService.start()` 无同步（改动面大，需单独评估）
- [ ] 分片 join 超时并发写：`DownloadService`（同上）

### P0 功能正确性（High）

- [x] 播放器长按后手势全失效：`PlayerControlDelegate.kt:215`（26.09.10 已修，第十六轮）
- [x] 设置页二级列表崩溃：`SettingsAdapter` 负值 viewType + `ConcatAdapter` 重映射（26.09.10 已修，第十九轮）
- [x] Menu 键同时打开菜单并关闭当前页（26.09.08 已修，第四轮）
- [ ] WebView 输入校验缺失：`SetupUIActivity.kt:79,86,92`

### P1 架构清理

- [ ] 删除 `BiliTerminalApp.kt` 与 Hilt 死依赖（或真正启用）
- [ ] 清空 23 个空目录（di/data/network/ui 等）
- [ ] 删除幻觉方法（`SharedPreferencesUtil.beginBatchEdit` 等）
- [x] 统一 Cookie 写入锁（26.09.08 已修，第四轮）

### P1 安全

- [ ] AppInfoApi 升级 https（4 处明文 HTTP）
- [ ] 敏感日志清理（PrivateMsgApi/NetWorkUtil.post）
- [ ] 更新 APK 签名/哈希校验
- [ ] Manifest 权限收敛

### P2 工程化

- [ ] 拆分 PlayerActivity（3090 行）
- [ ] 拆分 DownloadService（65KB）
- [ ] 补单元测试（当前 15 个测试类 / 106 用例覆盖 363 个源文件；26.09.11 新增 `ColorSchemeTest` 14、`CornerStyleTest` 10、`FontStyleTest` 11、`AppearanceManagerTest` 12）

---

## 六、下一步建议

1. 立即重新构建验证第二轮 4 处修改
2. 继续排查剩余 Critical（私信/下载/网络）
3. 转向 High 功能正确性修复（弹幕颜色/URL 编码收益高、改动小）
4. 架构清理与安全加固（P1）可安排到后续迭代
