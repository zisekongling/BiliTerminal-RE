package com.RobinNotBad.BiliClient.activity.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerScreenMetricsPolicy] 的纯 JVM 单测（反馈 #31「载入中切后台再回来画面比例奇怪」）。
 *
 * 两条要在计算层钉死的规则：
 * 1. **只有软件旋屏**才互换宽高。真旋转后 `DisplayMetrics` 自己已经是横屏值，再换一次就转回去了；
 * 2. 播放器重建窗口期读到的 0 尺寸不能当成"没有视频"直接放弃，要退回上一次已知的有效值，
 *    否则画面区域会一直停在歪掉的旧布局上。
 */
class PlayerScreenMetricsPolicyTest {

    // ---------- screenSize ----------

    @Test
    fun 竖屏时屏幕尺寸就是窗口尺寸() {
        val size = PlayerScreenMetricsPolicy.screenSize(466, 466, softwareLandscape = false)
        assertEquals(466, size.width)
        assertEquals(466, size.height)
        val rect = PlayerScreenMetricsPolicy.screenSize(360, 640, softwareLandscape = false)
        assertEquals(360, rect.width)
        assertEquals(640, rect.height)
    }

    @Test
    fun 软件旋屏时宽高互换() {
        val size = PlayerScreenMetricsPolicy.screenSize(360, 640, softwareLandscape = true)
        assertEquals("软件旋屏靠 rotation=90 摆画面，逻辑宽高必须互换", 640, size.width)
        assertEquals(360, size.height)
    }

    // ---------- changed ----------

    @Test
    fun 尺寸没变不要重排() {
        val size = PlayerScreenMetricsPolicy.screenSize(466, 466, softwareLandscape = false)
        assertFalse(
            "回前台每次都重排布局会闪，尺寸一致时应当什么都不做",
            PlayerScreenMetricsPolicy.changed(466, 466, size)
        )
    }

    @Test
    fun 宽或高任一变化都要重排() {
        val size = PlayerScreenMetricsPolicy.screenSize(360, 640, softwareLandscape = false)
        assertTrue(PlayerScreenMetricsPolicy.changed(360, 641, size))
        assertTrue(PlayerScreenMetricsPolicy.changed(361, 640, size))
        assertTrue("首次（0x0）也要算变化", PlayerScreenMetricsPolicy.changed(0, 0, size))
    }

    // ---------- resolveSourceSize ----------

    @Test
    fun 优先使用播放器实时尺寸() {
        val size = PlayerScreenMetricsPolicy.resolveSourceSize(1920, 1080, 1280, 720)
        assertEquals(1920, size!!.width)
        assertEquals(1080, size.height)
    }

    @Test
    fun 实时尺寸读到0时退回缓存值() {
        // 重建窗口期 videoWidth/videoHeight 可能就是 0——这是 #31 画面比例变怪的直接触发条件
        val size = PlayerScreenMetricsPolicy.resolveSourceSize(0, 0, 1920, 1080)
        assertEquals("不能因为一次读到 0 就放弃重排，否则歪掉的旧布局会留在屏幕上", 1920, size!!.width)
        assertEquals(1080, size.height)
    }

    @Test
    fun 只有一边为0也算无效() {
        val size = PlayerScreenMetricsPolicy.resolveSourceSize(1920, 0, 1280, 720)
        assertEquals(1280, size!!.width)
        assertEquals(720, size.height)
        assertNull(PlayerScreenMetricsPolicy.resolveSourceSize(-1, 1080, 0, 0))
    }

    @Test
    fun 两边都没有有效值时返回空() {
        assertNull(
            "既没有实时值也没有缓存值时才该跳过尺寸调整（例如听视频模式）",
            PlayerScreenMetricsPolicy.resolveSourceSize(0, 0, 0, 0)
        )
        assertNull(PlayerScreenMetricsPolicy.resolveSourceSize(0, 0, -1, -1))
    }
}
