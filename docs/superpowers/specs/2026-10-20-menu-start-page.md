# 菜单启动页（打开应用直接进入的页面）与菜单设置说明改造

- **日期**：2026-10-20
- **状态**：已实施；26.10.25 追加「断网启动兜底」（模拟器已验证，见第 6 节）
- **涉及文件**：`util/MenuConfig.kt`、`util/NetWorkUtil.java`（26.10.25 追加）、`util/SharedPreferencesUtil.java`、`adapter/MenuSettingAdapter.kt`、`activity/settings/SettingMenuActivity.kt`、`activity/SplashActivity.kt`、`res/layout/item_menu_setting*.xml`、`res/values/strings.xml`、`test/.../MenuConfigTest.kt`

## 1. 目标

1. 允许用户自定义「打开应用后直接进入的页面」（下称**启动页**），设置入口就放在现有的「菜单设置」页里，不新增菜单项、不新增设置页。
2. 把菜单设置页底部那段长说明改为顶部可折叠的按钮式说明，并支持长按隐藏、留极简入口恢复（手表屏幕小，说明占位太多）。

## 2. 需求（与用户确认后的最终口径）

### 2.1 启动页选择

- 菜单设置页的**每个已启用项**右侧显示一个复选框，用作启动页**单选**标记：
  - 同一时刻**恰好一项**被选中，**不允许取消为全不选**；
  - 短按（点击）某个已启用项＝把该项设为启动页，复选框随之移动；
  - 短按必须有**像按钮点击一样的按压反馈**（水波纹），不是无反馈的静默切换；
  - **长按（拖拽排序）不算点击**，不得改变启动页；
  - 复选框本身不单独接收点击（避免出现"点框没反应/点框和点行行为不同"），点整行即可。
- **默认启动页 = 「推荐」页面**。推荐是固定项，永远在已启用列表里，因此默认值一定可达。
  - 从未设置过（`menu_start` 缺失/空白）→ 用默认值，**不写盘**；
  - 已设置但目标后来被移入「未启用」→ **立即改选回「推荐」并写回**（拖拽离手时执行，不留下指向不可达页面的配置）。
- 启动流程（`SplashActivity`）改为读该配置：进入对应页面，并把该 key 作为 `from` extra 透传给页面/菜单（保持原有"从哪来"的高亮语义）。理论上取不到配置时仍回退到推荐页。

### 2.2 菜单设置页说明块

- 原底部 footer 说明移到列表**最顶部**，做成**按钮样式**（与菜单按钮同族），默认只显示一行标题；
- 短按展开全文 / 再短按收起；
- **长按**：第一次长按仅 Toast 提示「再长按一次即可隐藏说明」；**2 秒内**第二次长按才真正隐藏；
- 隐藏状态**跨重启持久化**；
- 隐藏后顶部保留一个**极简**「显示说明」入口，点它恢复显示；
- 说明正文里要写明该手势（以及原有的拖拽排序 / 移入未启用 / 固定项不可隐藏）。

## 3. 验收标准

功能验收（真机 / Debug 包，手表端优先）：

1. 全新安装（无 `menu_start`）启动 → 直接进入**推荐**页；菜单设置里「推荐」一行复选框为选中态。
2. 在菜单设置里短按「搜索」→ 该行复选框变为选中、「推荐」取消选中，且**有可见的按压反馈**；返回后重新启动应用 → 直接进入**搜索**页。
3. 短按已选中的那一项 → 不取消选中、不弹出任何提示（幂等）。
4. 长按某已启用项仅进入拖拽，离手后启动页不变；把当前启动页拖入「未启用」区并松手 → 复选框自动回到「推荐」；重启后进入推荐页，且 `menu_start` 已在 `SharedPreferences` 中被写回 `recommend`。
5. 把「搜索」移入未启用后再放回已启用 → 启动页仍是「推荐」（不会自动恢复旧的选中项）。
6. 说明块默认一行标题；点一下展开全文、再点收起；展开态下把行数很多的内容滚出屏幕不异常。
7. 连续两次长按说明（间隔 < 2s）→ 首次有提示、第二次隐藏；杀进程重启进入菜单设置 → 说明仍是隐藏态，只显示「显示说明」；点它恢复。
8. 长按一次后等超过 2 秒再长按 → 只再提示一次，不隐藏。
9. 未登录状态下把「我的」设为启动页并重启 → 进入「我的」页面，走既有的未登录引导，不崩溃。
10. 全部菜单项逐一设为启动页并重启，都能进入对应页面、无崩溃。

工程验收：

1. `:app:assembleDebug` 通过（两个 flavor）。
2. `:app:testStandardDebugUnitTest` 与 `:app:testCoexistDebugUnitTest` 通过；新增 `MenuConfigTest` 用纯函数覆盖：默认值、合法沿用、失效回退、极端缺推荐、缺失不写盘、失效写回。
3. 用户可见文案全部进 `strings.xml`，无新增硬编码中文。
4. `docs/FEATURES.md` 同步；无新增第三方库；未触碰工作区里其它未提交改动。

## 4. 设计要点

### 4.1 存储

| SP 键 | 类型 | 含义 |
| --- | --- | --- |
| `menu_start` | String | 启动页菜单 key（缺省=推荐，允许缺失） |
| `menu_help_hidden` | boolean | 菜单设置页说明是否被用户隐藏，缺省 false |

现有 `menu_enabled`（已启用列表，`;` 连接）语义不变。

### 4.2 纯逻辑（可 JVM 单测）

`MenuConfig` 新增：

- `DEFAULT_START = "recommend"`
- `resolveStart(enabled, stored)`：`stored` 仍在 `enabled` 中则沿用；否则默认 `recommend`；再兜底 `enabled.first()`，最后 `DEFAULT_START`。
- `OFFLINE_START = "local"`（26.10.25 追加）：断网时的目标页固定为缓存页（固定项，一定可达）。
- `resolveLaunchStart(enabled, stored, online)`（26.10.25 追加）：`online == true` 时走 `resolveStart`，否则返回 `OFFLINE_START`。只决定**本次启动**去哪里，不写 `menu_start`。
- `loadStart(enabled, readString, writeString)`：缺失（null/空白）→ 返回默认值且**不写**；存在但已失效 → 写回默认值。
- `saveStart(key, writeString)`

`SharedPreferencesUtil` 只做注入与落盘：`MENU_START`、`MENU_HELP_HIDDEN`、`loadMenuStart(enabled)`、`saveMenuStart(key)`。

### 4.3 行结构（RecyclerView）

0 = 使用说明（或隐藏后的「显示说明」）、1 = 已启用标题、2..n+1 = 已启用项、n+2 = 未启用标题、n+3.. = 未启用项；`itemCount = enabled + disabled + 3`。拖拽的位置换算沿用原公式（`enabledStart=2`、`disabledStart=size+3`）。

启动页失效回退放在 `ItemTouchHelper.Callback.clearView`（离手时）而不是 `onMove`：`onMove` 里 `notifyItemChanged` 会打断正在进行的拖拽手势。

### 4.4 不做的

- 不新增设置页/菜单项（老用户不需要手动开菜单项）。
- 不引入 WorkManager / 新库 / 新 Activity / 新权限。
- 不做"启动页也影响从后台回前台"之类的行为。

## 5. 真机验收步骤（交给用户，本机 `adb devices` 无设备）

```bash
./gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/standard/debug/app-standard-*-debug.apk
# 1) 菜单设置：短按改选搜索 -> 有按压反馈、复选框移动
# 2) adb shell am force-stop com.RobinNotBad.BiliClient && adb shell am start -n com.RobinNotBad.BiliClient/.activity.SplashActivity
#    期望直接进搜索页
# 3) 把搜索拖入未启用 -> 复选框回到推荐；重启进推荐
# 4) 说明：点按展开/收起；2 秒内连按两次长按 -> 隐藏；重启后仍是隐藏态；点「显示说明」恢复
# 5) adb shell run-as com.RobinNotBad.BiliClient cat shared_prefs/*.xml | grep -E 'menu_start|menu_help_hidden'
```

## 6. 26.10.25 追加：断网启动兜底（已实施，模拟器已验证）

### 6.1 缘起

启动页上线后，用户「314」反馈（QQ 截图）：改完打开界面逻辑之后，无网络时打开应用会跳到自定义打开界面；希望改回「无网络时打开自动进缓存」，能正常加载时再进自定义界面。

根因：`SplashActivity` 现在只按 `menu_start` 选目标页，全流程没有网络可用性判断；而 `menu_start` 缺省是「推荐」，推荐 / 动态 / 消息等页面断网时只能看到加载失败，启动页设置反而把原本能离线使用的缓存页挡在了后面。

### 6.2 口径

- 启动时做一次**本地**连通性判断（`ConnectivityManager.getActiveNetworkInfo()`，不发请求、毫秒级；判断不出来一律按「有网」处理，fail-open）。
- **无网 → 进缓存页（`local`）**，并提示一句「无网络，已进入缓存页」。
- **有网 → 仍按 `menu_start` 进用户设置的启动页**（原有行为不变）。
- 只影响本次启动，**不改写 `menu_start`**：网络恢复后照旧。
- 断网时**不发起启动网络请求**（token / Cookie 刷新、`AppInfoApi.check`、公告、匿名统计），避免一路超时和「终端接口出现问题」的提示；更新日志页与启动提示都走本地判断，保留。

### 6.3 实现

- `util/NetWorkUtil.java:693` 新增 `isNetworkAvailable(Context)`：故意用已废弃的 `getActiveNetworkInfo()`（API 1 起可用，`minSdk 19` 上不用 `NetworkCapabilities`），异常一律返回 `true`。
- `util/MenuConfig.kt:107/146` 新增 `OFFLINE_START` 与 `resolveLaunchStart(enabled, stored, online)`（纯函数，JVM 单测）。
- `activity/SplashActivity.kt:341-363`：`online = NetWorkUtil.isNetworkAvailable(this)` → `startActivityKey = MenuConfig.resolveLaunchStart(...)`；断网提示在 `finish()` 之后 `MsgUtil.showMsg(...)`（缓存页 `BaseActivity` 注册 EventBus 后收到 sticky 消息）；四处启动网络任务整体包进 `if (online)`。
- `res/values/strings.xml` 新增 `msg_splash_offline_to_cache`。

### 6.4 验收（新增）

1. 断网启动 → 直接进缓存页，底部提示「无网络，已进入缓存页」；`menu_start` 保持原值。
2. 恢复网络启动 → 按 `menu_start` 进自定义启动页；从未设置过时进「推荐」。
3. 断网启动的 logcat 不应出现「终端接口出现问题」。
4. 纯逻辑单测 `MenuConfigTest.resolveLaunchStart_*`：在线沿用用户设置、断网无论设置成什么都进 `local`、`local` 必为固定项。
