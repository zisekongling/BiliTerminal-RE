package com.RobinNotBad.BiliClient.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 看图页双击缩放的档位判定（反馈 #14 / #54）。
 *
 * 这里钉的是「一次双击放大、再一次更大、第三次回原比例」这条循环，以及
 * 缩放手势留下的浮点误差不能把双击判成「没反应」。
 */
class ImageViewerZoomPolicyTest {

    private val min = ImageViewerZoomPolicy.DEFAULT_MIN_SCALE
    private val mid = ImageViewerZoomPolicy.DEFAULT_MID_SCALE
    private val max = ImageViewerZoomPolicy.DEFAULT_MAX_SCALE

    private fun next(current: Float): Float =
        ImageViewerZoomPolicy.nextScale(current, min, mid, max)

    @Test
    fun 原比例双击放大到中间档() {
        assertEquals(mid, next(min), 0.0001f)
    }

    @Test
    fun 中间档再双击放到最大档() {
        assertEquals(max, next(mid), 0.0001f)
    }

    @Test
    fun 最大档再双击回原比例() {
        assertEquals(min, next(max), 0.0001f)
    }

    @Test
    fun 双指捏放在两档之间时双击仍按档推进() {
        // 1.2 倍（中间档以下）双击 → 中间档；3.2 倍（两档之间）双击 → 最大档
        assertEquals(mid, next(1.2f), 0.0001f)
        assertEquals(max, next(3.2f), 0.0001f)
    }

    @Test
    fun 缩放手势的浮点误差不会让双击失效() {
        // 已经停在中间档（差 EPSILON 以内）→ 应该继续推到最大档，而不是又「放大到中间档」看不出变化
        assertEquals(max, next(mid - ImageViewerZoomPolicy.EPSILON / 2), 0.0001f)
        // 已经停在最大档（差 EPSILON 以内）→ 应该回原比例
        assertEquals(min, next(max - ImageViewerZoomPolicy.EPSILON / 2), 0.0001f)
        // 略高于中间档 → 最大档
        assertEquals(max, next(mid + ImageViewerZoomPolicy.EPSILON * 2), 0.0001f)
    }

    @Test
    fun 读不到当前倍率时按原比例处理() {
        // getScale() 理论上不会返回这些值；真返回了也不能让双击变成「什么都不做」
        assertEquals(mid, next(Float.NaN), 0.0001f)
        assertEquals(mid, next(Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(mid, next(Float.NEGATIVE_INFINITY), 0.0001f)
    }

    @Test
    fun 档位参数异常时回落到默认三档() {
        // 全 0（例如把坏参数读给了 setScaleLevels）时不能返回 0 倍率——setScale(0) 会抛
        // IllegalArgumentException（PhotoView 要求落在 [min, max] 内）
        val target = ImageViewerZoomPolicy.nextScale(min, 0f, 0f, 0f)
        assertEquals(mid, target, 0.0001f)
        assertTrue(target >= ImageViewerZoomPolicy.DEFAULT_MIN_SCALE)
        assertTrue(target <= ImageViewerZoomPolicy.DEFAULT_MAX_SCALE)
    }

    @Test
    fun 目标倍率永远落在合法区间() {
        listOf(0f, 0.5f, 1f, 1.99f, 2f, 4f, 6.24f, 6.25f, 10f).forEach { current ->
            val target = next(current)
            assertTrue("current=$current target=$target", target >= min - 0.0001f)
            assertTrue("current=$current target=$target", target <= max + 0.0001f)
        }
    }
}
