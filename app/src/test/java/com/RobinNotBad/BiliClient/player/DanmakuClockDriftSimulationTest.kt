package com.RobinNotBad.BiliClient.player

import master.flame.danmaku.controller.MediaClockSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「弹幕一跳一跳」的确定性复现与回归（26.11.14）。
 *
 * 现象（用户反馈原话）：「弹幕依旧会一跳一跳的」。链路是
 * `DanmakuManager.bindMediaClock` → DFM `DrawHandler.syncTimerByMediaClock` →
 * [MediaClockSync.nextTimerValue]：每帧朝「采样位置 + 墙钟外推」的理想值蠕动，
 * 误差超过 [MediaClockSync.SNAP_ERROR_MS] 就**硬吸附**一次——那就是肉眼看到的「跳」。
 *
 * 这个测试用纯数值模拟 DFM 的渲染循环，把所有变量都变成可控数字：
 * 帧率（`fps`）、倍速、250ms 的位置采样间隔。它不依赖设备，所以能在 CI 上稳定复现
 * 「低帧率 → 周期性吸附」这个机制，并钉住修复后的行为。
 *
 * 为什么不用模拟器量：模拟器上 yuv 软解 + 秒级卡顿，帧率本身就掉到个位数，
 * 量出来的数字代表不了手表真机（见 `docs/agents/testing.md` 的 L1/L2 判据）。
 */
class DanmakuClockDriftSimulationTest {

    /** 一次模拟的结果：硬吸附次数 + 过程中出现的最大瞬时偏差。 */
    private data class Result(val snaps: Int, val maxErrMs: Long)

    /**
     * 按 DFM 的渲染循环一帧一帧算一遍。
     *
     * @param fps        弹幕渲染帧率（DFM 的渲染线程，不是视频帧率）
     * @param frameAware 是否把真实帧间隔交给 [MediaClockSync.nextTimerValue]（修复后的行为）
     */
    private fun simulate(
        fps: Int,
        seconds: Int = 10,
        speed: Float = 1.0f,
        frameAware: Boolean
    ): Result {
        val interval = 1000L / fps
        val t0 = 1_000_000L
        var timer = 0L
        var samplePos = -1L
        var sampleAt = 0L
        var lastFrameAt = -1L
        var snaps = 0
        var maxErr = 0L

        var now = t0
        while (now - t0 <= seconds * 1000L) {
            // 播放器位置来自 250ms 一次的轮询（IjkPlayerBridge.startProgressTracking）：
            // 只有采样值**变化**时 DFM 才刷新基准，与 syncTimerByMediaClock 完全一致
            val sampledPos = (((now - t0) / 250L) * 250L * speed).toLong()
            if (sampledPos != samplePos) {
                samplePos = sampledPos
                sampleAt = now
            }
            val ideal = MediaClockSync.project(samplePos, sampleAt, now, speed)
            val err = ideal - timer
            if (err > maxErr) maxErr = err
            if (err > MediaClockSync.SNAP_ERROR_MS || err < -MediaClockSync.SNAP_ERROR_MS) {
                snaps++
            }
            val frameInterval =
                if (!frameAware || lastFrameAt < 0) 0L else now - lastFrameAt
            lastFrameAt = now
            timer = if (frameAware) {
                MediaClockSync.nextTimerValue(timer, ideal, speed, frameInterval)
            } else {
                MediaClockSync.nextTimerValue(timer, ideal, speed)
            }
            now += interval
        }
        return Result(snaps, maxErr)
    }

    @Test
    fun `旧行为在10fps下周期性硬吸附`() {
        // 10fps = 每帧真实过去 100ms 媒体时间，而蠕动上限固定 40ms：
        // 每帧落后 60ms，约 0.8 秒攒到 500ms 就吸一次 —— 这就是「一跳一跳」
        val r = simulate(fps = 10, frameAware = false)
        assertTrue("10fps 下 10 秒内应至少吸附 8 次，实际 ${r.snaps}", r.snaps >= 8)
    }

    @Test
    fun `旧行为在20fps下也会吸附`() {
        val r = simulate(fps = 20, frameAware = false)
        assertTrue("20fps 下 10 秒内应至少吸附 2 次，实际 ${r.snaps}", r.snaps >= 2)
    }

    @Test
    fun `旧行为在60fps下不吸附`() {
        // 这条是「为什么只在某些设备/某些视频上抖」的答案：帧率够高就不抖
        assertEquals(0, simulate(fps = 60, frameAware = false).snaps)
    }

    @Test
    fun `帧间隔感知后低帧率不再吸附`() {
        // 修复：步进上限抬到「本帧真实过去的媒体时间」以上，蠕动只负责抹平采样抖动
        assertEquals(0, simulate(fps = 10, frameAware = true).snaps)
        assertEquals(0, simulate(fps = 20, frameAware = true).snaps)
        assertEquals(0, simulate(fps = 60, frameAware = true).snaps)
    }

    @Test
    fun `帧间隔感知后三倍速也不吸附`() {
        // 3x 下媒体时间每秒推进 3000ms，低帧率时更需要按帧间隔放大步进
        assertEquals(0, simulate(fps = 20, speed = 3.0f, frameAware = true).snaps)
        assertEquals(0, simulate(fps = 10, speed = 3.0f, frameAware = true).snaps)
    }

    @Test
    fun `帧间隔感知后瞬时偏差仍被限制在半秒内`() {
        // 蠕动依然限幅：正常推进下偏差绝不该超过吸附阈值，否则又变成「跳」
        val r = simulate(fps = 10, frameAware = true)
        assertTrue("最大偏差 ${r.maxErrMs}ms 应小于 ${MediaClockSync.SNAP_ERROR_MS}ms",
            r.maxErrMs < MediaClockSync.SNAP_ERROR_MS)
    }

    @Test
    fun `帧间隔未知时退回固定步进`() {
        // 首帧 / 刚硬对齐过 / 卡顿后（间隔超过吸附阈值）调用方会传 0，
        // 此时行为必须与修复前完全一致，不能因为新参数抖起来
        assertEquals(
            MediaClockSync.nextTimerValue(1_000L, 1_400L, 1.0f),
            MediaClockSync.nextTimerValue(1_000L, 1_400L, 1.0f, 0L)
        )
        assertEquals(MediaClockSync.MAX_STEP_MS, MediaClockSync.stepFor(1.0f, 0L))
        assertEquals(MediaClockSync.MAX_STEP_MS, MediaClockSync.stepFor(1.0f, -5L))
    }

    @Test
    fun `步进上限不小于本帧过去的媒体时间`() {
        // 100ms 的帧间隔在 1.0x 下必须允许至少 100ms 的步进，否则必然落后
        assertTrue(MediaClockSync.stepFor(1.0f, 100L) >= 100L)
        assertTrue(MediaClockSync.stepFor(3.0f, 100L) >= 300L)
        // 60fps（16ms）时仍不低于原来的 40ms 基准，保持原来的抹平手感
        assertEquals(MediaClockSync.MAX_STEP_MS, MediaClockSync.stepFor(1.0f, 16L))
    }
}
