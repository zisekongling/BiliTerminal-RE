# 设计文档：菜单设置界面重构（已启用/未启用双分区）

- 日期：2026-08-16
- 项目：ReBiliClient
- 状态：已获用户确认

## 背景与目标

当前菜单配置割裂在三个地方：

1. 菜单界面 `activity/MenuActivity.kt`：由 `btnNames`（14 项）+ `MENU_SORT`（全量排序）+ 6 个 `menu_*` 布尔（显隐）共同驱动，末尾固定追加"退出"按钮。
2. 菜单设置界面 `activity/settings/SettingMenuActivity.kt`：6 个开关 + "调整排序"按钮（跳 `SortSettingActivity` 拖拽排序）。
3. `activity/settings/SortSettingActivity.kt`：拖拽排序页，其滑动删除逻辑实际是坏的（删项后 `MENU_SORT` 数量对不上 `btnNames.size`，下次进入会静默重置为默认排序）。

用户要求把菜单设置界面重构为上下两个分区：上方"已启用"列表（可排序、可禁用），下方"未启用"列表（点击启用），最底部为用法说明文字。删除菜单里的"退出"按钮。推荐、设置、缓存、搜索四项固定不可隐藏。

## 用户已确认的决策

- 布局上下排列（手表端竖屏，不能左右分屏）。
- **方案 A**：单页整合 + 新增单一配置项 `menu_enabled`，替代 `MENU_SORT` + 6 个 `menu_*` 布尔，旧数据自动迁移。
- "长按两次"手势 = **长按-松开-再长按**（600ms 窗口内同一项）。
- 除推荐/设置/缓存/搜索外，**全部菜单项**（含热搜、动态、我的、消息）都可移入未启用列表。

## 数据模型

### 菜单项（key 沿用 `MenuActivity.btnNames`）

| key | 显示名 | 是否固定不可隐藏 |
| --- | --- | --- |
| `recommend` | 推荐 | 固定 |
| `short_video` | 短视频 | 可禁用 |
| `popular` | 热门 | 可禁用 |
| `precious` | 入站必刷 | 可禁用 |
| `ranking` | 全站排行榜 | 可禁用 |
| `hotsearch` | 热搜 | 可禁用 |
| `live` | 直播 | 可禁用 |
| `timeline` | 时间线 | 可禁用 |
| `search` | 搜索 | 固定 |
| `dynamic` | 动态 | 可禁用 |
| `myspace` | 我的 | 可禁用 |
| `message` | 消息 | 可禁用 |
| `local` | 缓存 | 固定 |
| `settings` | 设置 | 固定 |

默认已启用（与现有默认一致）：`recommend, short_video, popular, hotsearch, search, dynamic, myspace, message, local, settings`。
默认未启用：`precious, ranking, live, timeline`。

### 新配置项

`SharedPreferencesUtil` 新增常量 `MENU_ENABLED = "menu_enabled"`，值为分号连接的有序已启用 key 列表，同时承担**排序**与**显隐**两个职责。旧的 `MENU_SORT` 与 `menu_*` 布尔保留不再写入，仅用于迁移读取。

### 迁移逻辑（纯函数，可单测）

`MenuConfig.resolveEnabledList(oldSort: String?, oldSwitches: Map<String, Boolean>): List<String>`

1. 若 `menu_enabled` 已存在且合法（每个 key 都在 `btnNames` 内、无重复、含全部固定项）→ 直接使用。
2. 否则以旧 `MENU_SORT` 的顺序为基准（非法/缺失则用全量默认顺序），按旧 `menu_*` 开关过滤六个可开关项，无开关项保留；最后把缺失的固定项按默认顺序补到末尾。
3. 结果写回 `menu_enabled`。

## 改动内容

### 1. 新增 `util/MenuConfig.kt`

纯 Kotlin 对象，无 Android 框架依赖（供 JVM 单测）：
- 全量 key 顺序常量 `ALL_ITEMS`、默认启用列表 `DEFAULT_ENABLED`、固定项集合 `FIXED_ITEMS`。
- `resolveEnabledList(...)`：迁移/校验逻辑。
- `serialize(list)` / `parse(str)`：配置读写序列化。
- `disabledFrom(enabled: List<String>)`：由已启用列表派生未启用列表（`ALL_ITEMS` 顺序减去已启用项）。

### 2. 重写 `activity/settings/SettingMenuActivity.kt`

- 顶部保留现有返回栏（`pageName` + `TextClock`），下方改用单个竖向 `RotaryRecyclerView`。
- 新建 `adapter/MenuSettingAdapter.kt`，单 adapter 多 view 类型，从上到下依次：
  1. "已启用"分区标题；
  2. 已启用项（可拖拽排序；固定项带"固定"小标签）；
  3. "未启用"分区标题；
  4. 未启用项（灰色弱化显示）；
  5. 底部说明文字（普通灰色小字，内容：长按一次后移动可调整顺序；长按两次移入未启用；点击未启用项追加到已启用末尾；推荐、设置、缓存、搜索固定不可隐藏）。
- 交互：
  - 已启用项**长按一次** → ItemTouchHelper 开始拖拽，仅限已启用区内部排序。
  - 固定项（推荐/设置/缓存/搜索）**可参与排序**，但不可移入未启用区。
  - 已启用项**长按-松开-再长按**（600ms 窗口内同一项）→ 移入未启用区；固定项弹 toast"此项不可隐藏"。
  - **点击未启用项** → 追加到已启用列表末尾。
  - 所有变更即时写回 `menu_enabled`。
- `activity_setting_menu.xml` 重写为顶栏 + `RotaryRecyclerView` 结构。

### 3. 改 `activity/MenuActivity.kt`

- 读取 `menu_enabled`（含迁移回退）渲染按钮；`btnNames` 中不在列表内的 key 不显示。
- 删除 `mutableBtnList.add("exit")` 及 `killAndJump` 的 `"exit"` 分支，清理 `Process` import。
- 登录态逻辑保留：未登录时顶部加"登录"、隐藏 `dynamic`/`message`/`myspace`。

### 4. 改 `activity/SplashActivity.kt`

- 把读 `MENU_SORT` 取首项改为读 `menu_enabled` 取首项（决定直达页面）。

### 5. 删除 `activity/settings/SortSettingActivity.kt`

- `DragAdapter`、`activity_simple_list.xml` 保留（`SearchSortActivity` 等仍在使用）。

## 测试

- `app/src/test/` 新增 `MenuConfigTest`，纯 JVM 单测：`resolveEnabledList` 各类入参（无旧数据/合法旧排序/坏排序/固定项补全/开关过滤）、序列化往返、`disabledFrom` 派生。参考 `NetWorkUtilTest` 的 FakeSharedPreferences 手法，避免 Android 框架依赖。
- 无集成测试；以 `./gradlew :app:assembleDebug` 编译通过 + 真机手测为准。

## 不在本次范围

- 不触碰 `ModernMenuActivity` / `MainMenuViewModel`（新版菜单尚在开发中，非当前入口）。
- 不改 `SearchSortActivity` 及其排序逻辑。
- 不改登录态相关行为。
