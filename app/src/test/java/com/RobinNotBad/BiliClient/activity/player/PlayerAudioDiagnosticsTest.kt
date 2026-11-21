package com.RobinNotBad.BiliClient.activity.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerAudioDiagnostics] 的纯 JVM 单测。
 *
 * 覆盖反馈 #182（仅音频 + 蓝牙，音量只有别家一半）：这一轮只加观测，所以这里固定的是
 * **诊断说了什么**——增益被拖到 0、系统音量为 0、蓝牙没拉满、焦点被抢、增益不满、
 * 以及什么都正常这六种现场各自的结论，以及日志行必须带齐关键字段。
 *
 * 注意：断言的是「有没有指出某个可疑层」，不是「音量正不正常」。
 * 最后那个「看起来都正常」的用例同时测了另一件事——结论不能冒充「音量正常」。
 */
class PlayerAudioDiagnosticsTest {

    /** 默认现场：增益 100%、系统音量满格、持有焦点、普通模式、无外挂音轨、扬声器输出。 */
    private fun snapshot(
        gainPercent: Int = 100,
        systemVolume: Int = 15,
        systemVolumeMax: Int = 15,
        audioFocus: Boolean = true,
        audioOnly: Boolean = false,
        externalTrack: Boolean = false,
        outputTypes: List<Int> = listOf(2),
    ) = PlayerAudioDiagnostics.Snapshot(
        gainPercent, systemVolume, systemVolumeMax, audioFocus, audioOnly, externalTrack, outputTypes
    )

    @Test
    fun deviceTypeName_coversOutputTypesThatMatter() {
        assertEquals("SPEAKER", PlayerAudioDiagnostics.deviceTypeName(2))
        assertEquals("BT_A2DP", PlayerAudioDiagnostics.deviceTypeName(8))
        assertEquals("BT_SCO", PlayerAudioDiagnostics.deviceTypeName(7))
        assertEquals("WIRED_HEADPHONES", PlayerAudioDiagnostics.deviceTypeName(4))
        assertEquals("TYPE(99)", PlayerAudioDiagnostics.deviceTypeName(99))
    }

    @Test
    fun outputsLine_marksEmptyAndJoinsTheRest() {
        assertEquals(PlayerAudioDiagnostics.NONE, PlayerAudioDiagnostics.outputsLine(emptyList()))
        assertEquals("SPEAKER,BT_A2DP", PlayerAudioDiagnostics.outputsLine(listOf(2, 8)))
    }

    @Test
    fun isBluetooth_recognisesA2dpScoAndBle() {
        assertTrue(PlayerAudioDiagnostics.isBluetooth(listOf(8)))
        assertTrue(PlayerAudioDiagnostics.isBluetooth(listOf(7)))
        assertTrue(PlayerAudioDiagnostics.isBluetooth(listOf(25)))
        assertFalse(PlayerAudioDiagnostics.isBluetooth(listOf(2, 3)))
        assertFalse(PlayerAudioDiagnostics.isBluetooth(emptyList()))
    }

    @Test
    fun systemVolumeAtMax_needsReadableMax() {
        assertTrue(PlayerAudioDiagnostics.systemVolumeAtMax(snapshot(systemVolume = 15)))
        assertFalse(PlayerAudioDiagnostics.systemVolumeAtMax(snapshot(systemVolume = 9)))
        // 读不到最大档（负数）时不能判成「已拉满」
        assertFalse(PlayerAudioDiagnostics.systemVolumeAtMax(snapshot(systemVolume = 0, systemVolumeMax = -1)))
    }

    @Test
    fun verdict_gainZeroIsSilence() {
        assertTrue(PlayerAudioDiagnostics.verdict(snapshot(gainPercent = 0)).contains("增益 0%"))
    }

    @Test
    fun verdict_systemVolumeZero() {
        assertTrue(PlayerAudioDiagnostics.verdict(snapshot(systemVolume = 0)).contains("系统媒体音量为 0"))
    }

    @Test
    fun verdict_bluetoothNotMaxedPointsAtVolume() {
        val text = PlayerAudioDiagnostics.verdict(snapshot(systemVolume = 6, outputTypes = listOf(8)))
        assertTrue(text.contains("蓝牙"))
        assertTrue(text.contains("6/15"))
    }

    @Test
    fun verdict_bluetoothMaxedGoesOnToOtherLayers() {
        val text = PlayerAudioDiagnostics.verdict(snapshot(outputTypes = listOf(8)))
        assertFalse(text.contains("没拉满"))
        assertTrue(text.contains("绝对音量"))
    }

    @Test
    fun verdict_noAudioFocus() {
        assertTrue(PlayerAudioDiagnostics.verdict(snapshot(audioFocus = false)).contains("音频焦点"))
    }

    @Test
    fun verdict_gainBelowFull() {
        assertTrue(PlayerAudioDiagnostics.verdict(snapshot(gainPercent = 50)).contains("50%"))
    }

    @Test
    fun verdict_whenEverythingLooksFine_doesNotClaimNormal() {
        val text = PlayerAudioDiagnostics.verdict(snapshot())
        assertFalse(text.contains("正常"))
        assertTrue(text.contains("绝对音量"))
    }

    @Test
    fun line_carriesEveryKeyField() {
        val text = PlayerAudioDiagnostics.line(
            "prepared",
            snapshot(gainPercent = 100, systemVolume = 8, outputTypes = listOf(8, 2), audioOnly = true)
        )
        assertTrue(text.startsWith("audio_diag prepared"))
        assertTrue(text.contains("gain=100%"))
        assertTrue(text.contains("vol=8/15"))
        assertTrue(text.contains("focus=1"))
        assertTrue(text.contains("onlyAudio=1"))
        assertTrue(text.contains("extTrack=0"))
        assertTrue(text.contains("bt=1"))
        assertTrue(text.contains("out=BT_A2DP,SPEAKER"))
        assertTrue(text.contains("| "))
    }

    @Test
    fun line_emptyReasonFallsBackToDash() {
        assertTrue(PlayerAudioDiagnostics.line("", snapshot()).startsWith("audio_diag " + PlayerAudioDiagnostics.NONE))
    }
}
