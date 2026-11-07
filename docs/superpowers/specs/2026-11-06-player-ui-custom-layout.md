# 播放器界面设置（假播放器）与播放器按钮自定义布局

- **日期**：2026-11-06
- **状态**：设计已与用户确认（三轮问答），**未实施**
- **涉及文件**：`activity/settings/SettingPlayerUiActivity.kt`（新建）、`res/layout/activity_setting_player_ui.xml`（新建）、`res/layout/item_player_ui_card.xml`（新建）、`player/PlayerButtons.kt`（新建）、`player/PlayerUiLayout.kt`（新建）、`player/PlayerSettingsGroups.kt`（新建）、`activity/player/PlayerActivity.kt`、`res/layout/activity_player.xml`、`activity/settings/SettingTerminalPlayerActivity.kt`、`activity/settings/SettingMainActivity.kt`、`activity/settings/SettingPlayerChooseActivity.kt`、`activity/settings/SettingsIndex.kt`、`util/SettingsKeys.kt`、`app/src/main/AndroidManifest.xml`、`res/values/strings.xml`、`test/.../PlayerUiLayoutTest.kt`（新建）

## 1. 目标

1. 新增「播放器界面」设置页：页面主体是一块 **16:9 的假播放器画面**，位置与真播放器一一对应；**点哪个按钮，就弹出哪个按钮相关的设置**。
2. 播放器控件改为「槽位」模型：左列、右列、底部两侧的按钮都可以自由增删、排序、替换；一个槽位可以放**单个按钮**，也可以放一个**组**（点一下横向展开一排）。
3. 现有「内置播放器设置」的 48 项**降级**为假界面顶栏的「完整设置」二级入口，并借这次改动重新分组（修掉 5 条无标题 divider）。
4. 删掉 5 个「显示××按钮」开关，由布局取代；新增「恢复默认布局」。
5. 顶部标题栏新增「显示标题 / 显示电量 / 显示时钟」三个开关，让顶部区域也可点可调。
6. 顺手补齐 `SettingsIndex.kt` 漏掉的 7 条搜索索引（见 §7）。

## 2. 已确认口径（三轮问答的结论，勿再反复）

| # | 问题 | 结论 |
| --- | --- | --- |
| 1 | 假界面与 48 项列表的关系 | **并存**：假界面当第一屏，48 项降为顶栏「完整设置」入口 |
| 2 | 点按钮后设置以什么形态出现 | **下半屏滑出卡片，盖在假界面上** |
| 3 | 一个槽位装几个 | **一格 = 一个按钮，或一格 = 一个组**（组 = 一排可横向滚动的按钮） |
| 4 | 倍速文字算不算一格 | **算**：格子里放「倍速」，平时显示 `×1.0`，点开调速度 |
| 5 | 底部默认布局 | **默认 = 现状**，用户可改 |
| 6 | 候选按钮 | 见 §3.1 全表（16 项勾选 + 漏掉的「发弹幕」待确认，见 §7） |
| 7 | 同一按钮能否重复 | **允许随便放几份，状态实时同步** |
| 8 | 5 个「显示××按钮」开关 | **删掉**，由布局取代，另加「恢复默认布局」 |
| 9 | 编辑入口 | **第一版只在设置里的假界面**，真播放器里不做编辑模式 |
| 10 | 非按钮元素 | **全部可点**，映射见 §4 |
| 11 | 假界面朝向 | **固定横向 16:9 缩略，水平居中** |
| 12 | 组的展开方式 | **像现在的「更多」**：点一下横向展开一排 |
| 13 | 组内上限 | **不限，横向滚动** |
| 14 | 没有可调项的按钮点进去 | **一行说明 + 「换一个 / 移除」两个动作** |
| 15 | 没有按钮对应的设置 | **只放在「完整设置」里**，另外假界面角落加一个「其他设置」入口直达 |
| 16 | 容量 | 左列、右列各最多 **5** 格；底部每侧最多 **3** 格；装不下就横向滚动 |
| 17 | 默认布局 | 左 `[锁定][倍速]`，右 `[弹幕][旋转][组=第二排 9 个]`，底部 `[清晰度][音量-] 播放暂停 [音量+][分P]` |

## 3. 数据结构

### 3.1 按钮注册表 `player/PlayerButtons.kt`（26.11.06 已实现）

`object PlayerButtons` + `data class Button(key, nameRes, iconRes, settingsGroup, action, textOnly)`。落地时比原设计少两个字段：

- `scene`（可用场景）**不进货架**：什么时候能点、能不能显示，现有代码里已有一整套判据（`hasDanmaku`、多分P、是否直播……），第 3 步渲染时按 key 沿用原判据，不在注册表里再记一份，避免两处不一致。
- `defaultWidthDp` 也不需要：槽位宽度由假界面/播放器的容器权重决定，不按按钮单独配。

17 个可配按钮（key 即存储串里的 token，一旦发布不能改）：

| key | 现有 view id | 名称（`strings.xml`） | 图标 | 点它弹哪组 |
| --- | --- | --- | --- | --- |
| `lock` | `lock_btn` | 锁定 | `icon_player_lock` | 无（说明卡） |
| `speed` | `text_speed` | 倍速 | `ic_player_speed`（自绘） | 播放默认值组 |
| `danmaku` | `danmaku_btn` | 弹幕 | `danmakuoff` | 弹幕 |
| `rotate` | `rotate_btn` | 旋转 | `rotate` | 无（说明卡） |
| `danmaku_send` | `danmaku_send_btn` | 发弹幕 | `send` | 弹幕 |
| `subtitle` | `subtitle_btn` | 字幕 | `subtitle_off` | 字幕 |
| `loop` | `loop_btn` | 循环播放 | `loopoff` | 播放默认值组 |
| `audio_only` | `audio_only_btn` | 听视频 | `icon_audio_only_off` | 播放默认值组 |
| `auto_next` | `auto_next_btn` | 自动连播 | `icon_auto_next_off` | 播放默认值组 |
| `viewpoint` | `viewpoint_btn` | 看点 | `icon_star`（借） | 无（说明卡） |
| `video_info` | `video_info_btn` | 视频信息 | `icon_info` | 无（说明卡） |
| `jump_time` | `jump_time_btn` | 跳转时间 | `icon_player_clock_fading` | 无（说明卡） |
| `queue_mode` | `queue_mode_btn` | 连播队列 | `icon_player_corner_down_right` | 无（说明卡） |
| `quality` | `button_quality` | 清晰度 | `icon_menu` | 跳 `SettingQualityActivity` |
| `sound_cut` | `button_sound_cut` | 音量减 | `soundcut` | 无（说明卡） |
| `sound_add` | `button_sound_add` | 音量加 | `soundadd` | 无（说明卡） |
| `page_selector` | `button_page_selector` | 分P | `icon_folder` | 无（说明卡） |

- 「更多 / 组」**不占 key**：它是槽位的一种形态（`Slot.Group`），渲染时用 `mipmap/morehide`、`mipmap/moreshow` 两个现成图标。
- 图标缺口：只剩 `viewpoint` 还借 `icon_star`，等用户补图后改 `PlayerButtons.ALL` 里对应那一行即可。
  `jump_time` / `queue_mode` 26.11.06 已换成用户给的 lucide 图（`clock-fading` / `corner-down-right`），
  播放页里这两个按钮本体也从文字改成了 `ImageButton`。
- `textOnly = true` 只有「倍速」一项：它在槽位里显示 `×1.0` 文字，不画图标。

### 3.2 槽位与整份布局 `player/PlayerUiLayout.kt`（26.11.06 已实现）

```text
Slot = Button(key) | Group(keys: List<String>)
Layout = left: List<Slot>          // 上限 5
         right: List<Slot>         // 上限 5
         bottomLeft: List<Slot>    // 上限 3
         bottomRight: List<Slot>   // 上限 3
```

- **允许同一个 key 出现在多个槽位、也允许在同一个组里重复**（用户明确要求）；状态同步靠现有按钮状态本来就是全局的（`isDanmakuVisible`、`loop_enabled` 等），渲染时每个副本都调用同一个刷新函数。
- 存储：`SharedPreferencesUtil` 一个键 `player_ui_layout`，值是一段**分隔符串**（不是 JSON——见下）：

```text
1;lock,speed;danmaku,rotate,{danmaku_send+subtitle+loop+audio_only+auto_next+viewpoint+video_info+jump_time+queue_mode};quality,sound_cut;sound_add,page_selector
```

五段用 `;` 分隔：版本号、左、右、底部左、底部右；段内用 `,` 分隔槽位；组用 `{}` 包起来、组内用 `+` 分隔。

- **为什么不用 JSON**：JVM 单测里 `org.json` 是 Android 的桩实现（`testOptions.unitTests.returnDefaultValues = true`，只返回默认值、不真解析），JSON 化的布局无法在单测里验证；仓库里同类的「用户自定义布局」`util/MySpaceConfig.kt` 用的也是分隔符串，格式风格保持一致。也因此 key 里**不允许出现 `; , + { }`**（单测里有断言钉着）。
- **默认值 = 上面那一行**（即现状外观，升级零感知）。
- 容错口径：
  - 结构非法（段数不对、版本不认识、花括号不配对、出现空 token）→ **整体回退默认**（宁可回默认，也不要把用户按钮丢一半，与 `MySpaceConfig` 一致）；
  - 单个 key 没登记（旧版本删掉的按钮、手改脏数据）→ **只丢这个 key**，其余保留；
  - 组里有效 key 只剩 1 个 → **仍保留成组**，不降级成单按钮（用户是先建组再往里放成员，
    中途必然出现单成员组；悄悄拆掉组会让他刚建的组消失）；一个不剩 → 整组丢弃；
  - 超出容量 → 截到 5/5/3/3。
- `load()` 会把「读到的东西 ≠ 规范化结果」的情况顺手写回规范串，于是默认布局、旧格式、脏数据都在第一次读取时被修好。

### 3.3 纯函数（可 JVM 单测，26.11.06 已实现）

`PlayerUiLayout` 里只放无 Android 依赖的函数，`PlayerUiLayoutTest`（30 个用例）覆盖：

- `default(): Layout` / `DEFAULT` / `isDefault(layout)`
- `parse(raw: String?): Layout`（容错规则见上）/ `encode(layout): String`
- `slots(layout, region)` / `withSlots(layout, region, slots)` / `isFull(layout, region)`
- `add(layout, region, slot)` / `removeAt(layout, region, index)` / `replaceAt(...)` / `move(layout, region, from, to)`（越界一律原样返回，不抛）
- `uses(layout, key)`（候选列表标注「已放置」用）
- `load(read, write)` / `save(layout, write)`（读写注入成函数，这层不碰 Android）
- `Region` 枚举自带 `capacity`（5/5/3/3）

### 3.4 播放器侧渲染（`PlayerActivity`）

- **改 XML 结构**：`activity_player.xml` 现有 17 个按钮全部保持原 id，但统一挪进一个隐藏的「按钮池」容器 `button_pool`（`visibility="gone"`）；三个真正渲染的位置改成空容器：
  - `left_control`（已存在，清空子元素）
  - `right_column_slots`（给现有那列无名 LinearLayout 加 id，清空子元素）
  - `bottom_buttons` 内改成横向滚动容器：`bottom_left_slots` + `button_video`(固定) + `bottom_right_slots`
- 进入播放页时按布局 **re-parent**（`removeView` + `addView`）池子里的按钮到目标容器，并统一设 `LayoutParams`（侧列 28dp、底部 32dp、间距 6dp）。**不 new 新按钮**，`setOnClickListener` 那些既有绑定逻辑全部照旧。
- 组槽位：动态建 `HorizontalScrollView` + `LinearLayout`，把组内按钮挂进去；点组格切换展开/收起（图标 `morehide`/`moreshow`，行为同现在的 `menu_btn`）。
- 场景不适用：`isUsable` 返回 false 的槽位整个不挂载，后面的自动往前补位。
- 删掉 5 处 `getBoolean("player_ui_showXxxBtn")` 判据（`PlayerActivity.kt:4051`、`:6576`、`:6647`、`:6654`、`:1581`），改为「在布局里就在，不在就不在」。
- `alignLeftControlToRight()`（`PlayerActivity.kt:1598-1607`）用 translationY 对齐左右列中线的做法，在列高可变后要改成按容器中线对齐或直接放弃 translationY 改用 `layout_centerVertical`。
- 锁定态：锁定时 `control_layout` 与 `left_control` 一起隐藏，锁定按钮由 `PlayerActivity.parkLockButton()` 从它所在的槽位 **re-parent 到 root 层、压在 `lock_overlay` 之上**，所以「用户把 `lock` 放进了会被隐藏的容器」也能点得到；解锁时 `unparkLockButton()` 先按用户布局摆回原槽位再显示控制栏，避免图标在原位闪一下。用户没放 `lock` 就点不到、进不了锁定态，不存在解不开的死锁 → 见 §7 风险 4。

### 3.5 设置分组的来源 `player/PlayerSettingsGroups.kt`

把 `SettingTerminalPlayerActivity.kt:68-269` 那 48 项**抽成数据**：`group → List<SettingSection>`，组带标题。列表页与假界面卡片共用同一份定义。

| 组 key | 组标题 | 内容 |
| --- | --- | --- |
| `gesture` | 手势 | 长按倍速 / 长按倍速档位 / 双击快进快退 / 长按上下划切视频 / 长按提示尺寸 / 左右滑动控制进度 / 滑动灵敏度 / 有缩放时双击优先恢复缩放 / 快进快退秒数 / 后台熄屏继续播放（10） |
| `defaults` | 播放默认值 | 弹幕 / 听视频模式 / 循环播放 / 自动连播 / 倍速 / 字幕 / 屏幕方向 / 从历史位置播放（8） |
| `display` | 画面与进度 | 显示高能进度条 / 显示实时人数 / 视频可缩放 / 缩放时可移动 / 自动跳过片头片尾（5） |
| `decode` | 渲染与解码 | 显示方式 / 解码方式 / 音频输出（3） |
| `danmaku` | 弹幕 | 弹幕允许重叠 / 合并重复弹幕 / 弹幕数量保护 / 强制为滚动弹幕 / 显示直播弹幕发送者 / 本人弹幕边框 / 直播弹幕停留倍率 / 弹幕最大行数 / 弹幕字号大小 / 弹幕不透明度 / 弹幕速度 / 弹幕缓存上限（12） |
| `subtitle` | 字幕 | 自动弹出字幕选择 / 允许仅AI字幕 / 字幕校准 / 字幕字号大小（4） |
| `toolbar` | 界面工具栏 | 显示标题 / 显示电量 / 显示时钟（新增 3）/ 互动选项字体大小（共 4） |

- 原 5 个「显示××按钮」开关从 `toolbar` 组删除。
- **没有按钮对应的设置**（`decode` 全组、后台熄屏、自动跳过片头片尾、互动选项字体大小、以及仍在「高级与实验」的旋屏兼容方案 / 显示视频分段 / 系统媒体控件 / 互动视频调试）→ **只出现在「完整设置」**；假界面角落的「其他设置」入口直接打开完整设置页。

### 3.6 新增设置键（`util/SettingsKeys.kt`）

| 键 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| `player_ui_layout` | String（分隔符串，格式见 §3.2） | 空 → 用 `PlayerUiLayout.default()` | 整份槽位布局 |
| `player_ui_show_title` | boolean | `true` | 顶部标题显示 |
| `player_ui_show_battery` | boolean | `true` | 电量显示 |
| `player_ui_show_clock` | boolean | `true` | 时钟显示 |

> 后三个开关的位置：**用户在 m02770 明确要求放进「完整设置」（内置播放器设置 → 画面与显示组），不进「播放器界面」页**。所以 `SettingPlayerUiActivity` 里不再有「标题栏」那一行；播放页由 `PlayerActivity.applyTopBarSwitches()` 读，`applyPlayerButtonLayout()` 末尾调用（`onResume` 会重跑）。

删除：`PLAYER_UI_SHOW_ROTATE_BTN` / `PLAYER_UI_SHOW_DANMAKU_BTN` / `PLAYER_UI_SHOW_QUALITY_BTN` / `PLAYER_UI_SHOW_PAGE_BTN` / `PLAYER_UI_SHOW_LOCK_BTN`（键留在 `SettingsKeys` 里标 legacy，不做迁移，直接忽略旧值；默认布局已等于"全开"）。

## 4. 假界面交互（`SettingPlayerUiActivity`）

页面结构：顶栏（返回 / 标题「播放器界面」/ 「完整设置」入口）+ 16:9 假播放器（水平居中整宽）+ 底部两个常驻动作（「恢复默认布局」「其他设置」）。

点哪 → 弹什么（用户已确认这张映射）：

| 点击位置 | 弹出内容 |
| --- | --- |
| 已有按钮格 | 该按钮所属设置组的卡片（§3.1 最后一列）；无设置项 → 说明 + 「换一个 / 移除」 |
| 组格 | 横向展开/收起组内按钮（在假界面上就地展开） |
| 虚线空格（`+`） | 候选按钮列表（全部 17 项，**不因已用而禁用**——允许重复） |
| 进度条 | 显示高能进度条 / 左右滑动控制进度 / 滑动灵敏度 |
| 在线人数 | 显示实时人数 |
| 画面空白处 | 手势组 10 项 + 视频可缩放 / 缩放时可移动 |
| 顶部标题栏 | 显示标题 / 显示电量 / 显示时钟（新增） |
| 播放/暂停（固定，不可配） | 播放默认值 8 项 |
| 长按任意已有按钮格 | 「换一个 / 插入组 / 移除」菜单 |

卡片：从下往上滑出，高度约半屏，内部直接复用 `adapter/SettingsAdapter` 的 `SettingSection` 渲染积木（switch / choose / list_choose / input_*），底部固定「换一个 / 移除」两个按钮。`list_choose` 的回写沿用 `SettingChooseResult.applyListChooseResult`（请求码 1001）。

## 5. 实施步骤（每步独立可验证，能单独提交）

1. **抽数据**：新建 `PlayerSettingsGroups`，`SettingTerminalPlayerActivity` 改为按组渲染（组标题 + divider 自动生成），补 `SettingsIndex.kt` 漏掉的 7 条索引。→ 视觉与原 48 项一致，`assembleDebug` + 单测 + 模拟器点一遍。
2. **纯逻辑层**：`PlayerButtons` + `PlayerUiLayout`（默认值 = 现状）+ `PlayerUiLayoutTest`。→ 只有单测，不动 UI。
3. **播放器渲染**：改 `activity_player.xml` 为「按钮池 + 三个空容器」，`PlayerActivity` 按布局挂载，删 5 处开关判据，修 `alignLeftControlToRight`。→ 默认布局下外观必须与现在一模一样（模拟器逐项对照）。
4. **假界面只读版**：`SettingPlayerUiActivity` 画出假播放器与按钮位置，点击弹卡片（复用 SettingsAdapter），全部为只读展示。→ 设置主页「播放与播放器」的 `target` 改为它，48 项挂在顶栏「完整设置」。
5. **编辑能力**：空格加按钮、长按换/移、组的新建与编辑、容量上限、恢复默认布局。→ 改完进播放页验证。
6. **顶部三开关 + 其余映射**：进度条/在线/画面/标题四处卡片，`PlayerActivity` 读取三个新键控制 `text_title` / `battery` / `clock`。
7. **文档与收尾**：`docs/FEATURES.md`、`docs/architecture-map.md` §7.12/§8.6、`docs/copy-inventory/04-settings.md`、`docs/review/fix-progress.md`；`npx --yes markdownlint-cli2` 0 错误。

## 6. 验收标准

功能（MuMu 模拟器 + Debug 包，手表端优先）：

1. 升级后首次进入播放页，控件位置与现在**逐个一致**（左列锁定+倍速、右列弹幕/旋转/更多、底部五个）。
2. 设置 → 播放与播放器 → 进入假播放器页：点「弹幕」弹出弹幕组卡片，改一项后返回播放页生效。
3. 把「音量加」从底部拖/换到左列第 3 格 → 播放页左列出现音量加，底部让位。
4. 删掉左列「锁定」→ 播放页左侧不再有锁按钮；锁定手势（若有）不崩、不出现无法解锁的死锁。
5. 同一个按钮放两处（如「弹幕」左右各一个）→ 点任一格都能切弹幕，两格图标同步变化。
6. 新建一个组放「字幕/循环/听视频」→ 播放页该格显示「更多」图标，点一下横向展开 3 个，再点收起。
7. 往右列塞 5 个按钮 → 显示完整不重叠；第 6 个放不进去（或横向滚动，按 §5 实测结论固化）。
8. 直播里布局含「分P/清晰度」→ 自动隐藏且后面的按钮补位，不留空洞。
9. 「恢复默认布局」→ 回到验收 1 的状态。
10. 「完整设置」里 48 项仍在、分组有标题、无空 divider。
11. 顶部三个开关关闭后，播放页对应元素消失。
12. 设置页搜索「左右滑动控制进度」「弹幕缓存上限」等原先漏掉的 7 项，能搜到并跳转命中。

工程：

1. `:app:assembleDebug` 通过（两个 flavor）。
2. `:app:testStandardDebugUnitTest` 与 `:app:testCoexistDebugUnitTest` 通过；新增 `PlayerUiLayoutTest` 覆盖默认值、坏串回落、未知 key 丢弃、超容量截断、重复 key、单成员组保留与空组丢弃。
3. 用户可见文案全部进 `strings.xml`，无新增硬编码中文。
4. 无新增第三方库；新增 Activity `android:exported="false"` + `android:screenOrientation="locked"`。

## 7. 未决 / 风险

1. **「发弹幕」按钮**：我给的候选表漏了它（`danmaku_send_btn`）。按"第二排那 9 个都在"的默认布局它必须存在，所以先按"进候选"实现，用户若不要再删。
2. **图标**：`speed` 仍是文字 `×1.0`；组格用 `morehide`/`moreshow`；`viewpoint` 借 `icon_star`。
   `jump_time` / `queue` 26.11.06 已换成用户给的 lucide 图、播放页本体从文字改成 `ImageButton`，
   连播按钮因此不再常显模式文字（模式只在点按 toast 与 `contentDescription` 里给出）。
3. **re-parent 风险**：同一 View 只能有一个 parent，`activity_player.xml` 改动较大（17 个按钮搬家），任何遗漏都会直接崩或按钮消失——第 3 步必须模拟器逐项对照。
4. **锁定态**：现有逻辑依赖"锁定按钮一定存在"（`refreshLockButtonSetting`、`:1560` 注释），布局允许删掉它 → **已按「锁定时把按钮搬出隐藏容器」实现**（26.11.07）：`parkLockButton` 把它 re-parent 到 root 层压在 `lock_overlay` 之上，`unparkLockButton` 解锁时摆回原槽位；用户没放 `lock` 就进不了锁定态，故无需额外的强制出口或缺省手势。
5. **`alignLeftControlToRight`**：列高可变后 translationY 对齐会错位，需要重做。→ **已按「两条列中线差」实现**（26.11.07）：比较的是**窗口坐标**里的中线（两列不在同一 parent 下，raw `top` 不可比，原实现跨 parent 直接相减），并在两列的 `addOnLayoutChangeListener` 里随布局变化重算，不再是一次性量取。
6. **搜索索引**：`SettingsIndex.highlight` 靠 name 逐字比对定位，48 项重分组后 `SettingSection.name` 不能改字面量，否则高亮失效。
7. **假界面与真播放器的尺寸比例**：380×380 方形屏幕上 16:9 的假画面只有约 214px 高，左右列各 5 格、底部 7 格在缩略图上会挤在一起——第 4 步实测后可能要放大假界面（允许上下滚动）或给每个格子一个最小点击热区（≥40px）。
