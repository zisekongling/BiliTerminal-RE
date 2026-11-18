# 伪装与安全：双击顶栏一键切到假计算器 / 假蓝牙提示

- **日期**：2026-10-22
- **状态**：已实现。模拟器实测通过；手表端由维护者人工实测通过（26.10.23，自述）。本文是需求与验收的唯一口径。
- **需求来源**：issue #22。该 issue 建在 Gitee 侧（`zisekongling/BiliTerminal-RE`），GitHub 镜像上**没有对应 issue**，代码注释里的「issue #22」指的是前者，别去 GitHub 搜。
- **涉及文件**：
  - `util/DisguisePrefs.kt`、`util/CalculatorEngine.kt`、`util/DisguiseGesture.kt`、`util/SettingsKeys.kt`
  - `activity/disguise/{DisguiseLauncher,CalculatorDisguiseActivity,BluetoothDisguiseActivity,DisguiseRoundPadding}.kt`
  - `activity/base/BaseActivity.kt`、`activity/player/PlayerActivity.kt`、`activity/video/ShortVideoPlayerActivity.kt`、`adapter/ShortVideoPagerAdapter`
  - `activity/settings/{SettingGroupActivity,SettingMainActivity,SettingsIndex}.kt`
  - `res/layout/activity_disguise_{calculator,bluetooth}.xml`、`res/values/{strings,colors_disguise,styles_disguise}.xml`、`res/drawable/disguise_*`、`AndroidManifest.xml`
  - `test/.../util/CalculatorEngineTest.kt`（含 `DisguisePrefsTest`）

## 1. 目标

被人走近（上课、通勤）时，需要一个**比「退出应用」更快**的动作把界面换成不会引人注意的页面。要求三点：

1. 触发要快，手上不离开屏幕，不能是「退到桌面」或「锁屏」这种一看就是在藏东西的动作；
2. 伪装页要**经得起上手指**——旁观者点两下也不能露馅，更不能一按就跳回 B 站；
3. **默认关闭**：它会改变顶栏单击的响应时序（见 §5），不该强迫所有用户承担。

## 2. 需求（与用户确认后的最终口径）

### 2.1 触发方式

- **双击顶栏**切到伪装页。顶栏指页面顶部那条标题栏（`R.id.top`）。
- 顶栏原本的单击（返回上一页 / 打开菜单）与长按（打开菜单列表）**语义不能变**，只是单击会多等一个双击判定窗口（约 0.3 秒）。
- 只在**开关打开**时安装手势；开关关掉后单击顶栏必须立刻恢复成改动前的时序，不需要重进页面。

### 2.2 两种样式（设置里二选一）

| 样式 | 长什么样 | 退出方式 |
| --- | --- | --- |
| 计算器（默认） | 真的能算的加减乘除计算器，深灰底 + 橙色运算符 | 输入暗号后按 `=` |
| 蓝牙已断开 | 半透明盖在当前画面上的假系统提示，「重新连接」只换一行字 | 长按标题 2 秒 |

两款共同的硬要求：

- **不能出现任何指向哔哩终端的元素**：没有本站顶栏、没有菜单键、没有教程气泡、没有 EventBus 弹窗；
- **返回键不能退出**：计算器页的返回键只当 `C`（清空），蓝牙页的返回键与 ESC 完全吃掉。手势手表用「模拟返回键」实现右滑返回，同样被吃掉；
- 圆屏留白要跟随用户设置的内边距，否则圆屏最外圈的按键会被切掉。

### 2.3 计算器页：必须是真计算器

- 求值走真四则运算：先乘除后加减、支持小数与一元负号（`-3`、`5×-2`）、整除不带 `.0`、除零显示「错误」、末尾多余的运算符忽略；
- **绝不抛异常**（抛了就是崩溃，比不伪装更显眼）；
- 暗号判定放在求值**之前**：暗号本身是纯数字，先算的话等于把它原样显示在屏幕上。

### 2.4 暗号

- 只允许 **4~8 位纯数字**，默认 `1234`。
- 设成非数字（或长度不对）→ 提示并不保存：计算器上敲不出字母，配了 `abc` 等于把自己锁在外面，只能强杀应用。
- 读取侧再兜一次底（存档被手改时不合法就回退默认值），理由同上。

### 2.5 设置入口

新增设置分组「伪装与安全」，三项：

1. `双击顶栏伪装`（开关，默认关）——打开时 Toast 提示怎么退出；
2. `伪装样式`（二选一，默认计算器）；
3. `退出暗号`（输入框，失焦/完成时校验，数字键盘）。

## 3. 验收标准

### 3.1 功能验收（手表优先，Debug 包）

1. 开关**关闭**时：双击顶栏无反应；单击顶栏**立即**返回上一页 / 打开菜单（时序与开启前一致）。
2. 打开开关后：在有顶栏的页面上双击顶栏 → 打开伪装页；单击仍是原来的行为（返回 / 菜单），长按仍打开菜单列表。
3. 在设置里**关掉开关**、不重进页面、立刻双击顶栏 → 无反应；单击恢复即时响应。
4. 计算器：`1+2=` → `3`；`2+3×4=` → `14`；`5÷2=` → `2.5`；`3-6=` → `-3`，继续按 `×2=` → `-6`；`1÷0=` → `错误`；连按运算符不产生两个运算符。
5. 计算器输入暗号（默认 `1234`）后按 `=` → 回到进来之前的页面；输入错误数字后按 `=` → 照常算给你看，不退出。
6. 计算器页按返回键 → 只清空输入，**不退出**。
7. 蓝牙样式：轻点标题（1 次 / 连点 5 次）→ 不退出；长按标题 2.5 秒 → 退出；按返回键 → 无反应；点「重新连接」→ 正文变「正在重新连接…」，1.5 秒后变回「连接失败」。
8. 把手表内边距设成 40 / 40 后进两个伪装页 → 最外圈的按键与文字完整可见，不被圆屏切掉。
9. 打开开关后依次进入 **没有顶栏**的页面（下载、崩溃页、教程、输入对话框、验证码等），停留 30 秒 → 不卡顿、不持续耗电；日志里每条这类页面只出现**一次**「没有 R.id.top，本页不安装双击伪装」。
10. 短视频页（顶栏在分页 item 里）双击顶栏 → 能打开伪装页；关掉开关后同一页双击 → 无反应。

### 3.2 工程验收

1. `:app:assembleDebug` 通过（`standard` + `coexist` 两个 flavor）。
2. `:app:testStandardDebugUnitTest` 与 `:app:testCoexistDebugUnitTest` 通过；伪装相关用例 **21 例**（`CalculatorEngineTest` 14 + `DisguisePrefsTest` 7）。
3. 用户可见文案全部进 `strings.xml`，无新增硬编码中文。
4. 两个 Activity 均 `android:exported="false"`，各带专用主题；无新增权限（**不申请蓝牙权限**）、无新依赖、无新增明文域名。
5. `docs/FEATURES.md` §19 与 `docs/architecture-map.md` §7.53 同步。

## 4. 设计要点

### 4.1 存储（`SharedPreferencesUtil` + `SettingsKeys`）

| SP 键 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| `disguise_enabled` | boolean | `false` | 是否安装双击手势 |
| `disguise_style` | String | `calculator` | `calculator` / `bluetooth`，脏值回退默认 |
| `disguise_code` | String | `1234` | 4~8 位纯数字，读取侧再校验一次 |

默认值、样式取值与暗号校验**只有 `DisguisePrefs` 一处**，别在别处写死。

### 4.2 纯逻辑（可 JVM 单测）

- `DisguisePrefs`：`isEnabled()` / `style()` / `code()` / `normalizeCode(raw)` / `matchesSecret(input, code)` / `isValidStyle(value)`。后三个是纯函数，不读资源（本仓库单测无 Context）。
- `CalculatorEngine.evaluate(expression): String`：**永不抛异常**，出错返回 `ERROR`（`"错误"`，这里破例在纯函数里放了一句用户可见文案——它是结果值而不是可替换的界面文案，改动要同时改 `CalculatorDisguiseActivity` 的展示预期）。

### 4.3 手势层（`util/DisguiseGesture.kt`）

**必须自己接管触摸**，不能只挂 `OnDoubleTapListener`：双击的第一次抬手已经是一次完整 click，页面会先 `finish()`。所以 `OnTouchListener` 返回 `true` 消费全部触摸，事件交给 `GestureDetector`，再把单击 / 长按用 `performClick()` / `performLongClick()` **回放**给各页面自己装的监听器——各页面一行都不用改。

完整技术结论（为什么这样、代价、别改成什么）见 `docs/architecture-map.md` §7.53，本文不复述。

### 4.4 接线点（少一个就有页面没手势）

| 位置 | 覆盖 |
| --- | --- |
| `BaseActivity.setupTopbarDoubleTapDisguise()` | 所有继承 `BaseActivity` 的普通页；`asyncInflate` 页面**有上限重试**（20 次 × 50 ms），放弃时打日志 |
| `PlayerActivity.onStart()` | 长视频播放页——它**不继承** `BaseActivity` |
| `ShortVideoPlayerActivity.PageHolder.syncDisguiseGesture()` | 短视频页，顶栏在分页 item 里，每次绑定重算 |

三处都要**装/摘都做**（`install` / `detach`），否则关掉开关后已装上的触摸层不会自己消失。

### 4.5 伪装页本身

- **刻意不继承 `BaseActivity`**：主题、DPI 适配、圆屏 `setRound`、教程触发、EventBus、顶栏手势，任何一项都可能把「这是 B 站客户端」暴露出来。直接用 `AppCompatActivity` + 专用主题（`Theme.BiliClient.Disguise` / `Theme.BiliClient.Disguise.Dialog`）。
- 代价：`BaseActivity.setRound()` 那套留白用不上，改由 `DisguiseRoundPadding.applyDisguiseRoundPadding(root)` 读同一组内边距设置并换算 px。
- 入口只有 `DisguiseLauncher.launch(context)` 一个（按设置选样式）；启动失败只记日志、**不弹 Toast**——弹一句「打开伪装页失败」比不伪装更显眼。

### 4.6 不做的

- 不做「锁屏 / 伪装成桌面」，也不改桌面图标与最近任务卡片——计算器样式在最近任务里显示成「计算器」比整条消失更自然；
- 不申请蓝牙权限，蓝牙页是纯 UI 假象；
- 不做开机自启、不做定时切换、不做「检测到摄像头就自动伪装」这类需要额外权限的方案。

## 5. 已知取舍与遗留

- **单击延迟约 0.3 秒**：这是自接管触摸换来的必然代价，也是默认关闭的原因。判据是「开关关着时双击无反应、单击与改动前一致」（见验收 3.1.1 / 3.1.3）。
- **忘了暗号只能强杀应用**：计算器页除了暗号没有别的退出路径（返回键被吃掉），这是伪装能生效的前提，设置页文案里已写明，**改文案时别删这句**。
- **`DisguiseGesture` 用 `WeakHashMap` 记已安装的 View**：目的是 RecyclerView item 顶栏被回收后能自然释放；别换成强引用集合，会泄漏。
- **11 个页面布局里没有 `R.id.top`**（下载 / 崩溃页 / 教程 / 教程分页 / 跳转播放 / 输入对话框 / 验证码 / 扫码登录 / 确认对话框 / 通用对话框 / 列表对话框），这些页面上没有该手势，`FEATURES.md` 与设置文案都写的是「有顶栏的页面」而不是「任意页面」。
- **仍未做**：手表上「单击多等的 0.3 秒」与「半透明蓝牙页在圆屏上的布局」只有维护者肉眼结论，没有截图/日志证据；如需补齐，按 `docs/review/agent-test-guide.md` 抓 `dumpsys` 与截图后写进 `docs/review/fix-progress.md`。
