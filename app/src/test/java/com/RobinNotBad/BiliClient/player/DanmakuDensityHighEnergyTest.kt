package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G07「高能进度条本地兜底曲线」的纯判据单测。
 *
 * 官方 `pbp/data` 返回空时播放页会用这批逻辑算一条密度曲线；这里钉死
 * 「样本不足不画」「桶数按时长算」「密集段高于稀疏段」「值域夹在 0..1」四件事。
 */
class DanmakuDensityHighEnergyTest {

    /** 往 [bucket] 这个 10 秒桶里塞 [count] 条弹幕（每 100ms 一条，保证不越界）。 */
    private fun fill(times: MutableList<Long>, bucket: Int, count: Int, stepSec: Int = 10) {
        val base = bucket.toLong() * stepSec * 1000L
        for (i in 0 until count) times.add(base + i * 100L)
    }

    @Test
    fun `时长未知或过短一律不画`() {
        val times = (1..100).map { it * 1000L }
        assertNull(DanmakuDensityHighEnergy.build(times, 0))
        assertNull(DanmakuDensityHighEnergy.build(times, -1))
        assertNull(
            DanmakuDensityHighEnergy.build(times, DanmakuDensityHighEnergy.MIN_DURATION_SEC - 1)
        )
    }

    @Test
    fun `弹幕样本不足不画`() {
        val times = (1..(DanmakuDensityHighEnergy.MIN_DANMAKU - 1)).map { it * 1000L }
        assertNull(DanmakuDensityHighEnergy.build(times, 600))
        // 正好到下限：可以画
        val enough = (1..DanmakuDensityHighEnergy.MIN_DANMAKU).map { it * 1000L }
        assertNotNull(DanmakuDensityHighEnergy.build(enough, 600))
    }

    @Test
    fun `超出时长的弹幕被丢弃 桶数按时长向上取整`() {
        val times = ArrayList<Long>()
        fill(times, 0, 40)
        // 落在时长之外的样本不计入
        times.add(600_000L)
        times.add(99_999_999L)
        val curve = DanmakuDensityHighEnergy.build(times, 600)
        assertNotNull(curve)
        assertEquals(60, curve!!.size)

        val odd = DanmakuDensityHighEnergy.build(times, 605)
        assertNotNull(odd)
        assertEquals(61, odd!!.size)
    }

    @Test
    fun `曲线值域在 0 到 1 之间`() {
        val times = ArrayList<Long>()
        for (bucket in 0 until 60) fill(times, bucket, 1 + bucket % 7)
        val curve = DanmakuDensityHighEnergy.build(times, 600)
        assertNotNull(curve)
        curve!!.forEach { value ->
            assertTrue("曲线值越界: $value", value >= 0f && value <= 1f)
        }
    }

    @Test
    fun `密集段明显高于稀疏段`() {
        val times = ArrayList<Long>()
        // 前 25 桶与后 25 桶稀疏（每桶 2 条），中间 10 桶密集（每桶 20 条）
        for (bucket in 0 until 25) fill(times, bucket, 2)
        for (bucket in 25 until 35) fill(times, bucket, 20)
        for (bucket in 35 until 60) fill(times, bucket, 2)

        val curve = DanmakuDensityHighEnergy.build(times, 600)
        assertNotNull(curve)
        val dense = curve!![30]
        val sparse = curve[5]
        assertTrue("密集段应接近 1.0，实际 $dense", dense > 0.9f)
        assertTrue("稀疏段应明显更低，实际 $sparse", sparse < 0.5f)
        assertTrue("密集段必须高于稀疏段", dense > sparse)
        // 对称分布：首尾稀疏段的取值应当一致
        assertEquals(curve[5], curve[55], 0.0001f)
    }

    @Test
    fun `完全没有弹幕的桶是 0`() {
        val times = ArrayList<Long>()
        for (bucket in 0 until 10) fill(times, bucket, 5)
        val curve = DanmakuDensityHighEnergy.build(times, 600)
        assertNotNull(curve)
        // 0..9 桶有数据，后面 50 桶全空
        assertEquals(0f, curve!![30], 0.0001f)
        assertTrue(curve[0] > 0f)
    }

    @Test
    fun `非法采样间隔回退到默认值`() {
        val times = ArrayList<Long>()
        for (bucket in 0 until 60) fill(times, bucket, 3)
        val fallback = DanmakuDensityHighEnergy.build(times, 600, 0)
        val normal = DanmakuDensityHighEnergy.build(times, 600, DanmakuDensityHighEnergy.STEP_SEC)
        assertNotNull(fallback)
        assertArrayEquals(normal, fallback, 0.0001f)
    }
}
