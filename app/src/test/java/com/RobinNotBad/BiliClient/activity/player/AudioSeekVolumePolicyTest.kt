package com.RobinNotBad.BiliClient.activity.player

import com.RobinNotBad.BiliClient.activity.player.AudioSeekVolumePolicy.AudioAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AudioSeekVolumePolicy] 的纯 JVM 单测（反馈 #41「静音下拖进度会出声一小会」）。
 *
 * 核心不变式：**外挂音轨只在「真的听得见」且「视频在播」时运行；静音时暂停。**
 * 静音时它是暂停的，`MediaPlayer.seekTo` 就没有机会漏出声音——这是这条反馈的修法。
 */
class AudioSeekVolumePolicyTest {

    // ---------- isSilent：两种静音来源 ----------

    @Test
    fun 软件增益为0算静音() {
        assertTrue("无极调音拉到 0%（采样乘 0）时听不见", AudioSeekVolumePolicy.isSilent(0, 15))
    }

    @Test
    fun 系统媒体音量为0算静音() {
        assertTrue("外挂 MediaPlayer 走 STREAM_MUSIC，系统音量 0 时也听不见", AudioSeekVolumePolicy.isSilent(100, 0))
        assertTrue(AudioSeekVolumePolicy.isSilent(50, 0))
    }

    @Test
    fun 两路都有声时不算静音() {
        assertFalse(AudioSeekVolumePolicy.isSilent(100, 15))
        assertFalse(AudioSeekVolumePolicy.isSilent(1, 1))
    }

    @Test
    fun 拿不到系统音量时按不静音处理() {
        // 调用方在 audioManager 不可用时传 Int.MAX_VALUE：宁可多放一路声音，
        // 也不要因为一次读失败就把外挂音轨永久停掉（那会变成"切P 后没声音"的另一个 bug）。
        assertFalse(AudioSeekVolumePolicy.isSilent(100, Int.MAX_VALUE))
    }

    // ---------- actionFor：状态机 ----------

    @Test
    fun 静音时正在跑的音轨要暂停() {
        assertEquals(
            "静音的瞬间必须把外挂音轨停掉，否则 seek 时就有机会漏声",
            AudioAction.PAUSE,
            AudioSeekVolumePolicy.actionFor(silent = true, playbackWanted = true, audioRunning = true)
        )
    }

    @Test
    fun 静音且已经暂停时什么都不做() {
        assertEquals(
            AudioAction.NONE,
            AudioSeekVolumePolicy.actionFor(silent = true, playbackWanted = true, audioRunning = false)
        )
    }

    @Test
    fun 解除静音且视频在播时起播() {
        assertEquals(
            AudioAction.START,
            AudioSeekVolumePolicy.actionFor(silent = false, playbackWanted = true, audioRunning = false)
        )
    }

    @Test
    fun 视频暂停时不起播() {
        assertEquals(
            "视频暂停着，音轨自己跑起来就是画音不同步",
            AudioAction.NONE,
            AudioSeekVolumePolicy.actionFor(silent = false, playbackWanted = false, audioRunning = false)
        )
    }

    @Test
    fun 状态已经一致时不动手() {
        assertEquals(
            AudioAction.NONE,
            AudioSeekVolumePolicy.actionFor(silent = false, playbackWanted = true, audioRunning = true)
        )
        // 暂停态且静音：音轨本来就没跑
        assertEquals(
            AudioAction.NONE,
            AudioSeekVolumePolicy.actionFor(silent = false, playbackWanted = false, audioRunning = true)
        )
    }

    @Test
    fun 幂等_重复调用不会来回切换() {
        // 250ms 定时器每轮都调一次 actionFor，第二次必须还是 NONE
        val first = AudioSeekVolumePolicy.actionFor(silent = true, playbackWanted = true, audioRunning = true)
        assertEquals(AudioAction.PAUSE, first)
        val second = AudioSeekVolumePolicy.actionFor(silent = true, playbackWanted = true, audioRunning = false)
        assertEquals(AudioAction.NONE, second)
    }

    // ---------- shouldResyncBeforeStart：解除静音后的对齐 ----------

    @Test
    fun 音轨落后太多时先对齐再起播() {
        // 静音 5 分钟后解静音：音轨停在原地，视频已经走了 300 秒
        assertTrue(AudioSeekVolumePolicy.shouldResyncBeforeStart(0, 300_000))
        assertTrue(AudioSeekVolumePolicy.shouldResyncBeforeStart(302_000, 300_000))
    }

    @Test
    fun 偏差在容差内不必seek() {
        assertFalse(
            "800ms 以内本来就由 progressChange 的漂移校正覆盖，起播前再 seek 只会多一次停顿",
            AudioSeekVolumePolicy.shouldResyncBeforeStart(300_000, 300_500)
        )
        assertFalse(AudioSeekVolumePolicy.shouldResyncBeforeStart(300_000, 300_000))
    }

    @Test
    fun 容差边界() {
        val tolerance = AudioSeekVolumePolicy.RESYNC_TOLERANCE_MS
        assertFalse(AudioSeekVolumePolicy.shouldResyncBeforeStart(0, tolerance.toInt()))
        assertTrue(AudioSeekVolumePolicy.shouldResyncBeforeStart(0, (tolerance + 1).toInt()))
    }
}
