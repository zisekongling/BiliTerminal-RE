package com.RobinNotBad.BiliClient.activity.player

/**
 * 播放页「连蓝牙后音量只有别家一半」的诊断拼装（反馈 #182）——**只观测，不改任何音量行为**。
 *
 * 反馈原话是「仅音频模式连蓝牙音响放歌，音量只有其他音乐软件的不到一半」。代码里查不到确定成因：
 * 软件增益默认 100%（[PlayerVolumePolicy.DEFAULT_PERCENT]），音量键走的是系统
 * `STREAM_MUSIC`（`PlayerControlDelegate`），外挂音轨在 prepare 前就按同一增益 setVolume。
 * 也就是说「软件这一层没砍半」，剩下的只可能在**系统媒体音量档位、蓝牙绝对音量、音频焦点被抢**
 * 这几层，而这些都只能在现场读到。
 *
 * 所以这一轮只做「下次有人再报，日志里直接有现场」：
 *
 * - 开关沿用已有的 [com.RobinNotBad.BiliClient.util.SettingsKeys.PLAYER_INTERACTION_DEBUG]，
 *   **不新造开关**（与 [PlayerZoomDiagnostics] 一致），关掉时一行都不打；
 * - 这里只放**纯函数**：设备类型名、输出设备清单、判读（[verdict]）与日志行（[line]）；
 *   播放页跑不了普通 JVM 单测，抽到这里才能把各种组合固定下来（见 `PlayerAudioDiagnosticsTest`）；
 * - 本文件**不 import 任何 android.* 类型**，现场值全由调用方读好后传进来
 *   （设备类型是 `AudioDeviceInfo.getType()` 的**整数常量**，这里按 AOSP 的取值翻译成名字）。
 */
object PlayerAudioDiagnostics {

    /** 「没有」的占位：现场读不到时用它，免得诊断文本里出现空段让人以为漏打了。 */
    const val NONE = "—"

    // AudioDeviceInfo 的类型常量（AOSP 定值）。这里只写字面量、不 import android.media，
    // 单测才能在纯 JVM 上跑；名字只收会影响「外放/耳机/蓝牙」判断的那几个，其余打 TYPE(n)。
    private const val TYPE_BUILTIN_EARPIECE = 1
    private const val TYPE_BUILTIN_SPEAKER = 2
    private const val TYPE_WIRED_HEADSET = 3
    private const val TYPE_WIRED_HEADPHONES = 4
    private const val TYPE_BLUETOOTH_SCO = 7
    private const val TYPE_BLUETOOTH_A2DP = 8
    private const val TYPE_HDMI = 9
    private const val TYPE_USB_DEVICE = 11
    private const val TYPE_USB_HEADSET = 22
    private const val TYPE_BLE_HEADSET = 25
    private const val TYPE_BLE_SPEAKER = 26

    /**
     * 现场状态：全部由调用方读好后传进来，这里不碰 AudioManager、不碰 View。
     *
     * [gainPercent] 是「无极调音」那一层（0~100，与系统音量相乘）；[systemVolume] 是
     * `STREAM_MUSIC` 的档位，[audioFocus] 是本次播放有没有真的拿到音频焦点——
     * 被别的应用顶掉时听感正是「比别家小声」，这条最容易被忽略。
     */
    data class Snapshot(
        /** 软件增益百分比（内存里的当前值，不是存档值）。 */
        val gainPercent: Int,
        /** 系统媒体音量档位；读不到时为负数。 */
        val systemVolume: Int,
        /** 系统媒体音量最大档；读不到时为负数。 */
        val systemVolumeMax: Int,
        /** 是否持有音频焦点。 */
        val audioFocus: Boolean,
        /** 听视频（仅音频）模式。 */
        val audioOnly: Boolean,
        /** 是否起了外挂 `android.media.MediaPlayer` 音轨（本地 DASH 才有）。 */
        val externalTrack: Boolean,
        /** 当前输出设备类型（`AudioDeviceInfo.getType()`）；读不到时为空。 */
        val outputTypes: List<Int>
    )

    /** 设备类型 → 名字。只翻会影响判读的那几个，其它给 `TYPE(n)` 便于回来查表。 */
    fun deviceTypeName(type: Int): String = when (type) {
        TYPE_BUILTIN_EARPIECE -> "EARPIECE"
        TYPE_BUILTIN_SPEAKER -> "SPEAKER"
        TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        TYPE_BLUETOOTH_SCO -> "BT_SCO"
        TYPE_BLUETOOTH_A2DP -> "BT_A2DP"
        TYPE_HDMI -> "HDMI"
        TYPE_USB_DEVICE -> "USB"
        TYPE_USB_HEADSET -> "USB_HEADSET"
        TYPE_BLE_HEADSET -> "BLE_HEADSET"
        TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
        else -> "TYPE(" + type + ")"
    }

    /** 输出设备清单。读不到（低版本 / 服务异常）时给 [NONE]，不留空段。 */
    fun outputsLine(types: List<Int>): String =
        if (types.isEmpty()) NONE else types.joinToString(",") { deviceTypeName(it) }

    /** 是否有蓝牙输出：`BT_SCO` / `BT_A2DP` / `BLE_HEADSET` / `BLE_SPEAKER`。 */
    fun isBluetooth(types: List<Int>): Boolean = types.any {
        it == TYPE_BLUETOOTH_SCO || it == TYPE_BLUETOOTH_A2DP ||
            it == TYPE_BLE_HEADSET || it == TYPE_BLE_SPEAKER
    }

    /** 系统媒体音量是否已经拉满：没拉满时「比别家小声」首先该看这一项。 */
    fun systemVolumeAtMax(snapshot: Snapshot): Boolean =
        snapshot.systemVolumeMax > 0 && snapshot.systemVolume >= snapshot.systemVolumeMax

    /**
     * 一句人话判读。**只指出已知的可疑层，说「没发现可疑项」不等于音量正常**——
     * 那正是还需要现场字段（尤其是输出设备与 `STREAM_MUSIC` 档位）的情形。
     *
     * 顺序就是排查顺序：软件增益 → 系统音量 → 蓝牙未拉满 → 音频焦点 → 增益不满。
     */
    fun verdict(snapshot: Snapshot): String {
        if (snapshot.gainPercent <= 0) return "软件增益 0%，等于静音（无极调音被拖到底）"
        if (snapshot.systemVolume == 0) return "系统媒体音量为 0"
        if (isBluetooth(snapshot.outputTypes) && !systemVolumeAtMax(snapshot)) {
            return "蓝牙输出但系统媒体音量没拉满（" + snapshot.systemVolume + "/" +
                snapshot.systemVolumeMax + "），先拉满再看"
        }
        if (!snapshot.audioFocus) return "没拿到音频焦点，可能被其它应用压低或顶掉"
        if (snapshot.gainPercent < 100) {
            return "软件增益只有 " + snapshot.gainPercent + "%，无极调音没到 100%"
        }
        return "增益 100%、音量已满且持有焦点，问题在设备/蓝牙绝对音量那一层"
    }

    /**
     * 一条诊断日志。字段全用 ASCII 键名，方便线上直接 grep；末尾跟 [verdict] 的一句判读。
     *
     * 例：`audio_diag prepared gain=100% vol=15/15 focus=1 onlyAudio=0 extTrack=0 bt=1
     * out=BT_A2DP,SPEAKER | 增益 100%…`
     */
    fun line(reason: String, snapshot: Snapshot): String {
        val builder = StringBuilder("audio_diag ")
        builder.append(if (reason.isEmpty()) NONE else reason)
        builder.append(" gain=").append(snapshot.gainPercent).append('%')
        builder.append(" vol=").append(snapshot.systemVolume)
        builder.append('/').append(snapshot.systemVolumeMax)
        builder.append(" focus=").append(if (snapshot.audioFocus) 1 else 0)
        builder.append(" onlyAudio=").append(if (snapshot.audioOnly) 1 else 0)
        builder.append(" extTrack=").append(if (snapshot.externalTrack) 1 else 0)
        builder.append(" bt=").append(if (isBluetooth(snapshot.outputTypes)) 1 else 0)
        builder.append(" out=").append(outputsLine(snapshot.outputTypes))
        builder.append(" | ").append(verdict(snapshot))
        return builder.toString()
    }
}
