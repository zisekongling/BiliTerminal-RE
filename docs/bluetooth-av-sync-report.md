# 蓝牙耳机音画同步补偿 —— 可行性报告与实现方案

> 项目：RE:哔哩终端（ReBiliClient）
> 范围：内置播放器（`PlayerActivity` / ijkplayer）在连接蓝牙耳机时做音画同步补偿
> 版本基线：`versionCode 2608140` / `versionName 26.08.14`
> 文档日期：本文档为设计/可行性报告，尚未改动任何源码

---

## 一、背景与问题定义

### 1.1 现象
用户用蓝牙耳机看视频时，普遍遇到「画面比声音快」或「声音比画面快」的问题，尤其：
- 蓝牙协议栈 / 编解码器（SBC / AAC / aptX / LDAC）本身引入额外延迟（几十到两百多毫秒不等）；
- 耳机内部 DSP 缓冲、降噪处理进一步叠加延迟；
- 不同耳机、不同系统版本延迟差异大，无法用单一固定值修正。

### 1.2 本项目的现状
经代码核查，内置播放器（`PlayerActivity.kt`）当前：
1. **完全没有蓝牙相关的监听或权限**：`AndroidManifest.xml` 中没有任何 `BLUETOOTH` / `BLUETOOTH_CONNECT` 权限；
2. **音频输出走 ijkplayer 内部**：`setDisplay()` 中通过 `opensles` 选项切换 OpenSL ES / AudioTrack（`PlayerActivity.kt:780`），声音与画面由同一个 ijkplayer 时间基驱动；
3. 有一个「DASH 分离音频轨道 fallback」`initAudioTrack()`（`PlayerActivity.kt:1891`），用 Android `MediaPlayer` 单独播音频、与视频 `currentPosition` 对齐，但其同步逻辑是**偏差 >800ms 才 seek 强制对齐**（`progressChange()`，`PlayerActivity.kt:1006`），不适用于亚秒级、动态变化的蓝牙延迟补偿；
4. 设置基建成熟：`SettingTerminalPlayerActivity.kt` + `SettingsKeys.kt` + `strings.xml`（`desc_*` 资源），适合新增开关与微调项。

---

## 二、蓝牙音画不同步的技术成因

| 环节 | 引入的延迟量级 | 说明 |
|---|---|---|
| 蓝牙编码（SBC/AAC/aptX） | 20~60ms | 编码缓冲，SBC 较大、aptX/LL 较小 |
| 无线传输 | 5~20ms | 受距离/干扰影响 |
| 接收端解码 + DAC | 10~50ms | 耳机端 DSP 与数模转换 |
| 耳机缓冲/降噪（ANC/ENC） | 10~150ms | 主动降噪、空间音频会明显增大延迟 |
| 系统混音/路由 | 5~30ms | AudioFlinger 到蓝牙输出通道 |
| **合计（典型）** | **≈ 40~250ms** | 普通 SBC 耳机常见 100~200ms，游戏模式可低至 40ms |

**结论**：延迟随「耳机型号、编解码器、是否降噪、系统版本、当前信号质量」实时变化，**固定值补偿不靠谱**，需要「可手动微调 + 有条件时自动测量」。

---

## 三、可选技术方案对比

### 方案 A：手动偏移微调（推荐起步）
给播放器加一个「音画同步偏移」设置（单位 ms），对**视频渲染时间基**做整体偏移：画面提前/退后固定量，让用户对着画面听声音微调到贴合。

- 优点：实现简单、零额外权限、全耳机通用、无需测量；复用现成的 `SettingTerminalPlayerActivity` + `SettingsKeys` 基建。
- 缺点：手动、一次设定对当前耳机有效；换耳机/降噪开关变化后需重调。
- 定位：**必须做的底座**，也是后续自动化的基础。

### 方案 B：基于蓝牙连接信息自动选偏移
监听蓝牙 A2DP 设备连接，读取编码类型/采样率，按预设映射表自动套用偏移，仍可手动微调覆盖。

- 优点：半自动，体验好。
- 缺点：需要蓝牙权限（Android 12+ 要运行时权限 `BLUETOOTH_CONNECT`）；编码类型与真实延迟仍非强对应（同 aptX 不同耳机延迟差很大）；映射表要长期调优。
- 定位：在方案 A 之上增强。

### 方案 C：精确测量（A/V 时钟校准）
在系统层面做「声画时钟对齐」：
1. 播放一段已知画面的测试信号，用麦克风/视觉回采校准延迟（一般播放器 APP 不做，需要耳返链路）；
2. 或依赖 `AudioTrack.getTimestamp()` / 蓝牙的 `PresentationPosition`（`AudioPresentation`）读取实际播放位置，反推延迟。

- 优点：理论最准。
- 缺点：ijkplayer 内部音频栈拿不到可靠 presentation timestamp；蓝牙 sink 不保证上报 `AudioPresentation`；工程量大、真机差异大。
- 定位：**短期不建议**，可作为长期研究项。

### 方案 D：音频数据侧延迟（音频时间基后移）
用 `AudioTrack` 播放并主动拉长/缩短音轨，或利用 ijkplayer 的 `soundtouch`/`avdelay` 参数做音画对齐。

- 优点：直接作用于音频。
- 缺点：ijkplayer 官方 `av-delay`（A/V delay）只支持 `[min, max]` 区间微调，且随 `framedrop`、缓冲状态抖动；实际效果不稳定，调参敏感。
- 定位：可作为「自动微调」的补充手段，不单独作为主方案。

---

## 四、推荐方案

**分层落地，先 A 后 B（可选 C 作为长期）**：

1. **第一层（必做）**：方案 A —— 视频时间基整体偏移设置（手动），作为通用底座。
2. **第二层（建议）**：方案 B —— 监听蓝牙连接状态，套用预设偏移，并在界面上提示当前生效值；用户可手动覆盖。
3. **第三层（可选）**：接入 ijkplayer 的 `av-delay` 微调做「播放中自动纠正漂移」，但对蓝牙这种大而动态的延迟帮助有限，谨慎启用。

---

## 五、第一层：手动偏移的落地设计

### 5.1 概念
把「视频画面时间」相对「音频时间」整体平移 `offsetMs`：
- `offsetMs > 0` → 画面**提前** offset 毫秒（声音晚到，画面先播），用于「声音比画面慢」；
- `offsetMs < 0` → 画面**延后** |offset| 毫秒，用于「画面比声音慢」。

注意方向约定要写清楚并给说明文案，避免用户调反。

### 5.2 现有代码接入点（改动最小路径）

**关键改动集中在 `PlayerActivity.kt` 的进度/渲染时间基处**：

1. **新增设置项**
   - `util/SettingsKeys.kt`：新增
     const val PLAYER_AV_SYNC_OFFSET = "player_av_sync_offset" // 单位 ms，正=画面提前
     const val PLAYER_AV_SYNC_BLUETOOTH_ENABLE = "player_av_sync_bluetooth_enable" // 是否启用蓝牙补偿
     
   - `activity/settings/SettingTerminalPlayerActivity.kt`：新增一个 `input_int` 项「音画同步偏移（毫秒，正=画面提前，负=画面延后）」，默认 `0`；再加一个 `switch`「蓝牙耳机音画补偿」开关，默认 `false`。
   - `res/values/strings.xml`：按 `desc_*` 惯例加说明文案（唯一允许改 strings.xml 的是设置页）。

2. **渲染时间基偏移（核心）**
   在 `progressChange()` 里，计算 `currSec` 时套用偏移，让**字幕、弹幕、进度条、seek** 都跟随画面偏移走：
   val offset = if (bluetoothSyncOn) SharedPreferencesUtil.getInt(
       SettingsKeys.PLAYER_AV_SYNC_OFFSET, 0) else 0
   val videoPos = (ijkPlayer!!.currentPosition + offset).coerceAtLeast(0L)
   
   - 进度条 `seekbar_progress.progress = videoPos.toInt()`
   - 字幕 `showSubtitle(videoPos / 1000f + subtitle_delta)`
   - 弹幕、ViewPoint 的 `updateCurrentPosition` 同样用 `videoPos`。

   > 注意：**不修改 `ijkPlayer!!.currentPosition` 本身**，也不 `seekTo` 真实播放器，只偏移「显示/字幕/弹幕」所依据的时间基，避免污染真实播放进度回传（`finish()` 里的 `currentPosition` 仍用真实值）。

3. **（可选）画面渲染帧偏移**
   若想真正让画面帧提前/延后，需要在 `setDisplay()` 给 ijkplayer 设置 `avdelay`：
   ijkPlayer!!.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "avdelay", value)
   
   但如上所述，ijkplayer 的 avdelay 有区间限制且随缓冲抖动，**第一版不建议**，先用时间基偏移保证 UI 与字幕对齐即可。

### 5.3 对 DASH 分离音频轨道的影响
当前 `initAudioTrack()` 用独立 `MediaPlayer` 播音频、靠 `progressChange()` 偏差 >800ms 强对齐。接入偏移后，**不要把偏移套到 `audioPlayer` 的 seek 对齐判断**，否则会引入额外的循环跳动。建议：保持现有 800ms 强对齐逻辑只针对「真实漂移」，偏移仅作用于「显示时间基」。

---

## 六、第二层：蓝牙连接感知（建议，需权限）

### 6.1 权限
`AndroidManifest.xml` 增加：
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

- Android 12+（API 31+）`BLUETOOTH_CONNECT` 是**运行时权限**，需要在 `PlayerActivity` 请求；
- Android 11 及以下用 `BLUETOOTH` 即可，无需运行时。
- 用户拒绝时优雅降级为纯手动模式（第一层仍可用）。

### 6.2 监听与判断
- 用 `registerReceiver` 监听 `BluetoothDevice.ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED` 和 `AudioManager.ACTION_A2DP_PROFILE_CONNECTED`；
- 判断当前音频路由是否走蓝牙：`audioManager.isBluetoothA2dpOn`（或用 `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` 查 `TYPE_BLUETOOTH_A2DP`）；
- 读取编解码信息（`BluetoothCodecConfig` 需额外 API，Android 11+ 可通过 `BluetoothA2dp.getCodecStatus` 获取，但需要连接权限且非所有设备支持），第一版可只用「是否蓝牙 + 手动偏移」。

### 6.3 预设映射（可配置，非硬编码）
在设置页提供「蓝牙补偿值（ms）」默认值（如 120ms），监听生效时自动写入 `PLAYER_AV_SYNC_OFFSET`，用户仍可手动覆盖；断开蓝牙时恢复用户手动值。映射表存 `SharedPreferences`，后续可按耳机/编码扩展。

---

## 七、实现文件清单（改动范围）

| 文件 | 改动 |
|---|---|
| `app/src/main/AndroidManifest.xml` | 新增蓝牙权限（第二层用） |
| `app/src/main/java/.../util/SettingsKeys.kt` | 新增 2 个 key 常量 |
| `app/src/main/java/.../activity/settings/SettingTerminalPlayerActivity.kt` | 新增「音画同步偏移」输入项 + 「蓝牙补偿」开关 |
| `app/src/main/res/values/strings.xml` | 按 `desc_*` 惯例加文案（仅设置页） |
| `app/src/main/java/.../activity/player/PlayerActivity.kt` | `progressChange()` 套用偏移；`onCreate/onResume/onDestroy` 里注册/注销蓝牙 receiver（第二层）；请求 `BLUETOOTH_CONNECT` 权限 |
| （可选）`app/src/test/` | 把「偏移计算 + 蓝牙偏移合并」抽成纯 JVM 单测函数，避免 Android 依赖 |

> 不需要改动 `app/libs` 下的 ijkplayer `.so`（AGENTS.md 明确不要动原生库）；`PlayerIntegrator.kt` / `IjkPlayerBridge.kt` 为新层封装，本功能在 `PlayerActivity` 遗留层实现即可，避免跨层重复。

---

## 八、风险与注意事项

1. **方向约定**：偏移正负极易搞混，设置项说明与默认值必须严谨，建议加「视频提前/延后」两个明确方向。
2. **不动真实播放进度**：偏移只改显示时间基，避免影响 `finish()` 进度回传、历史续播、进度条拖动。
3. **与现有字幕校准共存**：已有 `player_subtitle_delta`（字幕校准，`PlayerActivity.kt:1135`），两者语义不同（一个是字幕偏移、一个是整体音画偏移），注意别混用。
4. **与 DASH 外部音频轨道逻辑隔离**：800ms 强对齐逻辑保持只针对真实漂移。
5. **权限拒绝降级**：`BLUETOOTH_CONNECT` 被拒时，蓝牙自动补偿不生效，但手动偏移仍可用。
6. **ijkplayer avdelay 不稳定**：不建议第一版启用；若要自动漂移纠正，需真机大量测试。
7. **延迟随场景变化**：降噪开关、编解码切换会改变延迟，补偿值并非永久准确，需提示用户可在设置中调整。

---

## 九、验证方案

1. **编译**：`./gradlew.bat :app:assembleDebug`（或 `:app:assembleRelease`）通过；
2. **单元测试**：若抽出偏移计算函数，用纯 JVM 单测覆盖「正偏移/负偏移/蓝牙合并/边界钳制」；
3. **真机手测（关键）**：
   - 连不同蓝牙耳机（SBC/AAC/aptX 各测），在「说话/打点/翻页」场景确认音画贴合；
   - 分别测试偏移为正、为负时画面相对声音的移动方向正确；
   - 断连/重连蓝牙、开关降噪后，确认补偿值切换与 UI 提示正确；
   - 全屏、切 P、切清晰度、进听视频模式后偏移仍生效且不丢进度。

---

## 十、工作量评估（估算）

| 层 | 内容 | 工作量 |
|---|---|---|
| 第一层（手动偏移） | 设置项 + `progressChange` 偏移 + 单测 | 0.5~1 人日 |
| 第二层（蓝牙感知） | 权限 + receiver + 预设偏移 + 请求权限 | 1~2 人日（含真机调试） |
| 第三层（avdelay 自动纠正） | 调研 + 调参 + 真机验证 | 3~5 人日（可选，建议后置） |

**建议首发范围**：第一层必做 + 第二层按需，第三层列为远期。

---

## 十一、结论

- 蓝牙音画不同步是普遍且延迟动态变化的问题，**固定值无法根治**；
- 推荐「**手动偏移底座（第一层）+ 蓝牙连接感知自动套用（第二层）**」的组合，改动集中在 `PlayerActivity.kt` 与设置基建，**不触碰原生 .so**，风险可控；
- 精确测量（第三层）工程量大、依赖设备能力，列为远期研究；
- 落地后可显著改善蓝牙观看体验，且完全可回退（新增开关默认关闭）。
```