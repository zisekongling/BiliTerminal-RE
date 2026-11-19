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
```

## 约束

- 需要 Node 18+（本机 `D:\Program Files\nodejs\node.exe`）；adb 路径取 `ADB_PATH`，默认 `D:\Program Files\android-sdk\platform-tools\adb.exe`。
- 多台设备同时在线时**必须显式给 `--serial`**：不猜，猜错会点错机器。
- 控件树 2.5 s 一次，是全套里最贵的动作；一条流程里控制在 1–2 次，能靠 `top` / `get` / `log` 拿到结论就别 dump。
- `resource-id` 会被列表复用（同一个 id 可能对应多个条目），要精确到位就用 `--text`，或者在两三个候选里按文本挑。
- 这个脚本只给 **Debug 包**用（调试桥只在 debug 构建里），Release 包投不通。
