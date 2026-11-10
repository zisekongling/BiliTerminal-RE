package com.RobinNotBad.BiliClient.service.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [SpeedSampler] 的纯逻辑测试。
 *
 * 这个采样器算出来的数字会直接显示成"1.2 MB/s"，所以语义要钉死：
 * 时间窗不够时不许给数（返回 null，让 UI 保持上一次的值），
 * 卡住时不许给负数，停更太久要归零。
 */
class SpeedSamplerTest {

    @Test
    fun 首次采样只记基准返回null() {
        val sampler = SpeedSampler()
        sampler.reset(1_000L)
        // reset 之后立刻上报：间隔 0，不足最小时间窗
        assertNull(sampler.sample(0L, 1_000L))
        // 攒够 1 秒后才给数
        assertEquals(1000L, sampler.sample(1000L, 2_000L))
    }

    @Test
    fun 间隔不足最小时间窗返回null() {
        val sampler = SpeedSampler()
        sampler.reset(0L)
        sampler.sample(1000L, 1_000L)
        assertNull("200ms 的间隔会把浮点噪声放大成跳变", sampler.sample(2000L, 1_200L))
        assertEquals("0.8s 可用：(3000-1000)/1.0s = 2000", 2000L, sampler.sample(3000L, 2_000L))
    }

    @Test
    fun 速度用实测间隔而不是名义一秒() {
        val sampler = SpeedSampler()
        sampler.reset(0L)
        sampler.sample(0L, 1_000L)
        // 名义上是"每秒一次"，实际隔了 2 秒：1MB/2s = 0.5MB/s，不能算成 1MB/s
        assertEquals(512L * 1024L, sampler.sample(1024L * 1024L, 3_000L))
    }

    @Test
    fun 结果做指数移动平均压住抖动() {
        val sampler = SpeedSampler()
        sampler.reset(0L)
        sampler.sample(0L, 1_000L)
        // 第一次：1MB/s
        assertEquals(1024L * 1024L, sampler.sample(1024L * 1024L, 2_000L))
        // 第二次真实速度掉到 0（卡住）：EMA = 0.4×0 + 0.6×1MB/s = 0.6MB/s，不直接跳 0
        assertEquals((1024L * 1024L * 0.6).toLong(), sampler.sample(1024L * 1024L, 3_000L))
    }

    @Test
    fun 字节数倒退时不给负速度() {
        val sampler = SpeedSampler()
        sampler.reset(0L)
        sampler.sample(5_000L, 1_000L)
        assertEquals("回滚到更小的字节数时按 0 处理", 0L, sampler.sample(100L, 2_000L))
    }

    @Test
    fun 停更超过stale时间后当前速度为0() {
        val sampler = SpeedSampler()
        sampler.reset(0L)
        sampler.sample(0L, 1_000L)
        sampler.sample(1024L, 2_000L)
        assertEquals(1024L, sampler.currentSpeed(2_100L))
        assertEquals("2.5 秒没有任何新字节，说明卡住或已结束", 0L, sampler.currentSpeed(5_000L))
    }

    @Test
    fun reset后需要重新攒够时间窗() {
        val sampler = SpeedSampler()
        sampler.reset(0L)
        sampler.sample(0L, 1_000L)
        sampler.sample(1024L, 2_000L)
        sampler.reset(3_000L)
        assertEquals("reset 清掉旧速度", 0L, sampler.currentSpeed(3_000L))
        assertNull("reset 后基准重新开始", sampler.sample(2_048L, 3_200L))
    }
}
