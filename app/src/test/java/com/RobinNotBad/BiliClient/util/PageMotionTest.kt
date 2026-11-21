package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PageMotion] 里可以脱离 Android 验证的判据（O7）。
 *
 * `apply()` 本身要窗口和 Activity，只能在真机上看；但「该不该改窗口动画、该改成什么值、
 * 什么时候不该动」全是纯判断，而这一层最容易写错的地方恰恰是**记录/还原的时机**：
 * 记早了会把「已经压成 0 的值」当原值，记晚了就永远还原不回去。
 */
class PageMotionTest {

    @Test
    fun windowAnimationsFor_关闭转场且没记过原值_返回0() {
        // 第一次关闭：记原值 + 压成 0
        assertEquals(0, PageMotion.windowAnimationsFor(disabled = true, remembered = null))
    }

    @Test
    fun windowAnimationsFor_关闭转场且已经记过原值_不再重复写() {
        // 已经压过一次：这次什么都不用做（省一次 setAttributes）
        assertNull(PageMotion.windowAnimationsFor(disabled = true, remembered = 8))
    }

    @Test
    fun windowAnimationsFor_开关打开始还原原值() {
        assertEquals(8, PageMotion.windowAnimationsFor(disabled = false, remembered = 8))
    }

    @Test
    fun windowAnimationsFor_开关打开且从没动过就不碰() {
        // 用户从没开过这个开关：一个属性都不该改
        assertNull(PageMotion.windowAnimationsFor(disabled = false, remembered = null))
    }

    @Test
    fun windowAnimationsFor_原值为0时还原也是0() {
        // 页面主题本来就是「无动画」：记下 0，关掉开关后还原成 0，不该变成别的值
        assertEquals(0, PageMotion.windowAnimationsFor(disabled = false, remembered = 0))
    }

    @Test
    fun shouldSuppressTransition_跟开关一致() {
        assertTrue(PageMotion.shouldSuppressTransition(disabled = true))
        assertFalse(PageMotion.shouldSuppressTransition(disabled = false))
    }

    @Test
    fun windowAnimationsFor_是纯函数_同一入参永远同一结果() {
        // 记录状态在 apply 的 WeakHashMap 里，不在判据里：重复调用不会互相影响
        for (i in 0 until 3) {
            assertEquals(0, PageMotion.windowAnimationsFor(disabled = true, remembered = null))
            assertNull(PageMotion.windowAnimationsFor(disabled = true, remembered = 8))
            assertEquals(8, PageMotion.windowAnimationsFor(disabled = false, remembered = 8))
        }
    }
}
