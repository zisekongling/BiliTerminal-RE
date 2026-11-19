# devtest：给 agent 用的设备驱动

`adb.mjs` 是一层很薄的封装：**只管「动作」和「取数」**（点、输入、跳页面、读控件树、读偏好、读日志），不做断言、不做业务判断——判定逻辑留在调用方（通常是 `run_code` 里的一段程序）。设计取舍与实测账单见 [`docs/review/agent-test-guide.md`](../../docs/review/agent-test-guide.md) §4.10。

## 为什么要有它

设备侧每条 adb 命令只有 100–200 ms，真正贵的是**进程启动 + 模型往返**：经工具层调一次 adb ≈ 1.6 s，一步一次工具调用会把一条 8 步流程拖到十几秒。所以：**一条流程写成一个程序跑完**，这个文件只是把那段程序里重复的部分固定下来。

## 用法

模块（在 `run_code` 里，推荐）：

```js
const { makeDriver } = await import("file:///D:/…/scripts/devtest/adb.mjs");
const d = makeDriver({ serial: "emulator-5554" }); // 省略 serial：ANDROID_SERIAL > 唯一在线设备
d.amstart(".activity.settings.SettingGroupActivity");
const sw = d.find({ rid: "setting_switch" });
if (sw) d.tap(sw);
console.log(d.top(), d.get("dev_logv"));
console.log(d.report());
```

命令行（临时查一眼）：

```bash
node scripts/devtest/adb.mjs help
node scripts/devtest/adb.mjs devices
node scripts/devtest/adb.mjs top --serial emulator-5554
node scripts/devtest/adb.mjs find --text 标题        # 找不到退出码 1
node scripts/devtest/adb.mjs tap --text 允许Logu     # 先 find 再点
node scripts/devtest/adb.mjs dump --filter 设置      # 控件树（默认走进程内，0.12–0.2 s）
node scripts/devtest/adb.mjs view --json             # 只要进程内那棵，取不到退出码 1
```

## 约束

- 需要 Node 18+（本机 `D:\Program Files\nodejs\node.exe`）；adb 路径取 `ADB_PATH`，默认 `D:\Program Files\android-sdk\platform-tools\adb.exe`。
- 多台设备同时在线时**必须显式给 `--serial`**：不猜，猜错会点错机器。
- 控件树有两种读法，`dump()` 默认先试快的：
  - **进程内**（调试桥 `view` 命令）：**0.12–0.2 s**，读的是当前页面的 View 树，已展开的列表条目全在。界面是 Compose / WebView / 纯自绘时读不到（这类界面本来就没有子 View）。
  - **uiautomator**：**2.5 s**，靠无障碍语义，快的那种读不到时才回落到它。`dump({ mode: "fast" })` 只走快通道、`{ mode: "uiauto" }` 只走慢通道。
- 应用退到后台后，进程内那条**仍然读得到上一次的窗口**（`nodes.fg === false`）。要确认「这一页真的在屏幕上」就得看 `fg`，或者用 `top`。
- 列表里滚出屏幕的节点带 `off: true`（中心点不在屏幕上），`tap()` 遇到它会直接报错，逼你先滚动——静默点空比报错难查得多。
- `resource-id` 会被列表复用（同一个 id 可能对应多个条目），要精确到位就用 `--text`，或者在两三个候选里按文本挑。
- 这个脚本只给 **Debug 包**用（调试桥只在 debug 构建里），Release 包投不通。
