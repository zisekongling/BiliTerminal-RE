package com.RobinNotBad.BiliClient.activity.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerZoomPolicy] 的缩放档位与位移边界（26.11.21 修「锁屏能缩放 / 能把画面缩到屏幕外」）。
 *
 * 只覆盖纯函数：真实触摸链路（双指事件有没有被喂给 detector、锁定态遮罩）仍需真机验证。
 */
class PlayerZoomClampPolicyTest {

    @Test
    fun 缩放档位夹在1到5之间() {
        assertEquals("低于 1 倍要拉回 1 倍", 1f, PlayerZoomPolicy.clampScale(0.2f), 0.0f)
        assertEquals("1 倍原样通过", 1f, PlayerZoomPolicy.clampScale(1f), 0.0f)
        assertEquals("区间内原样通过", 2.5f, PlayerZoomPolicy.clampScale(2.5f), 0.0f)
        assertEquals("超过 5 倍要压回 5 倍", 5f, PlayerZoomPolicy.clampScale(9f), 0.0f)
    }

    @Test
    fun 一倍时没有可移动空间() {
        assertEquals("1 倍时边界半径为 0", 0f, PlayerZoomPolicy.offsetLimit(1000, 1f), 0.0f)
        assertEquals("小于 1 倍按 1 倍算（不留黑边）", 0f, PlayerZoomPolicy.offsetLimit(1000, 0.4f), 0.0f)
    }

    @Test
    fun 放大后边界半径是多余宽度的一半() {
        assertEquals("放大 2 倍：多出一个画面宽，左右各一半", 500f, PlayerZoomPolicy.offsetLimit(1000, 2f), 0.0f)
        assertEquals("放大 5 倍", 2000f, PlayerZoomPolicy.offsetLimit(1000, 5f), 0.0f)
    }

    @Test
    fun 位移被夹在边界内() {
        val center = -120f
        val limit = 500f
        assertEquals("越界往右拉回", center + limit, PlayerZoomPolicy.clampOffset(9_999f, center, limit), 0.0f)
        assertEquals("越界往左拉回", center - limit, PlayerZoomPolicy.clampOffset(-9_999f, center, limit), 0.0f)
        assertEquals("界内不动", center + 100f, PlayerZoomPolicy.clampOffset(center + 100f, center, limit), 0.0f)
    }

    @Test
    fun 缩回一倍时画面必须回到原位() {
        // 反馈原话「能把画面缩到屏幕外」：缩回 1 倍后边界收成 0，任何旧位移都得被拉回中心
        assertEquals("回到原始位置", 321f, PlayerZoomPolicy.clampOffset(999f, 321f, PlayerZoomPolicy.offsetLimit(1080, 1f)), 0.0f)
    }

    @Test
    fun 原始位置本身就是合法边界() {
        val limit = PlayerZoomPolicy.offsetLimit(800, 3f)
        assertTrue("放大后原始位置当然合法", PlayerZoomPolicy.clampOffset(0f, 0f, limit) == 0f)
    }
}
