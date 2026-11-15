package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * 「列表 / 缩略图该解多大位图」的纯策略（[ThumbSizePolicy]）。
 *
 * 这里钉三件事，算错任何一条都会回到 26.11.15 那两条线上崩溃：
 * 1. **优先用控件自己的尺寸**（测量值 → 布局参数），不要把缩略图解成屏幕那么大；
 * 2. **拿不到尺寸时不乱猜**（返回 0，交给 Glide 等布局），避免把 40dp 的头像解成整屏；
 * 3. **无论如何不超过屏幕**，并且不小于 [ThumbSizePolicy.MIN_EDGE_PX]。
 */
class ThumbSizePolicyTest {

    private val screenW = 466
    private val screenH = 466

    @Test
    fun 已测量的控件按测量值解码() {
        assertArrayEquals(
            intArrayOf(120, 120),
            ThumbSizePolicy.targetPixels(120, 120, -2, -2, screenW, screenH)
        )
    }

    @Test
    fun 测量值优先于布局参数() {
        // 布局写的是 40dp，实测因为圆角/父容器约束变成了 120px：以实测为准
        assertArrayEquals(
            intArrayOf(120, 90),
            ThumbSizePolicy.targetPixels(120, 90, 40, 40, screenW, screenH)
        )
    }

    @Test
    fun 未测量时退回布局参数里的固定像素() {
        assertArrayEquals(
            intArrayOf(96, 96),
            ThumbSizePolicy.targetPixels(0, 0, 96, 96, screenW, screenH)
        )
    }

    @Test
    fun 未测量且布局是匹配父或包裹内容时不override() {
        // -1 = match_parent，-2 = wrap_content：都定不下来尺寸
        assertArrayEquals(intArrayOf(0, 0), ThumbSizePolicy.targetPixels(0, 0, -1, -1, screenW, screenH))
        assertArrayEquals(intArrayOf(0, 0), ThumbSizePolicy.targetPixels(0, 0, -2, -2, screenW, screenH))
        assertArrayEquals(intArrayOf(0, 0), ThumbSizePolicy.targetPixels(0, 0, 0, 0, screenW, screenH))
    }

    @Test
    fun 只有一个维度定得下来时另一个维度用屏幕兜底() {
        // 覆盖「宽度固定、高度 wrap_content 且尚未测量」的常见行布局
        assertArrayEquals(
            intArrayOf(200, screenH),
            ThumbSizePolicy.targetPixels(200, 0, 200, -2, screenW, screenH)
        )
        assertArrayEquals(
            intArrayOf(screenW, 80),
            ThumbSizePolicy.targetPixels(0, 80, -2, 80, screenW, screenH)
        )
    }

    @Test
    fun 实测尺寸超过屏幕时夹到屏幕() {
        // 崩溃 2 的形态：解码目标比屏幕大得多（这里用 5400 模拟 116MB 那张位图）
        assertArrayEquals(
            intArrayOf(screenW, screenH),
            ThumbSizePolicy.targetPixels(5400, 5400, -2, -2, screenW, screenH)
        )
    }

    @Test
    fun 极小控件不会小于下限() {
        assertArrayEquals(
            intArrayOf(ThumbSizePolicy.MIN_EDGE_PX, ThumbSizePolicy.MIN_EDGE_PX),
            ThumbSizePolicy.targetPixels(2, 2, -2, -2, screenW, screenH)
        )
        // 自定义下限同样生效
        assertArrayEquals(
            intArrayOf(32, 32),
            ThumbSizePolicy.targetPixels(8, 8, -2, -2, screenW, screenH, 32)
        )
    }

    @Test
    fun 屏幕尺寸未知时不override() {
        // Context 还没就绪（displayMetrics 拿不到）时宁可退回 Glide 默认行为
        assertArrayEquals(intArrayOf(0, 0), ThumbSizePolicy.targetPixels(120, 120, 120, 120, 0, screenH))
        assertArrayEquals(intArrayOf(0, 0), ThumbSizePolicy.targetPixels(120, 120, 120, 120, screenW, 0))
        assertArrayEquals(intArrayOf(0, 0), ThumbSizePolicy.targetPixels(120, 120, 120, 120, -1, -1))
    }

    @Test
    fun 结果永远不超过屏幕也不为零() {
        // 随便扫一批组合，确认没有任何一组会给出 0 或超过屏幕的值
        val boxW = 360
        val boxH = 640
        for (measured in intArrayOf(-1, 0, 1, 40, 360, 4000, 5400)) {
            for (layout in intArrayOf(-2, -1, 0, 40, 900)) {
                val size = ThumbSizePolicy.targetPixels(measured, measured, layout, layout, boxW, boxH)
                if (size[0] == 0 || size[1] == 0) continue
                assert(size[0] in 1..boxW) { "宽越界：$measured/$layout -> ${size[0]}" }
                assert(size[1] in 1..boxH) { "高越界：$measured/$layout -> ${size[1]}" }
                assert(size[0] >= ThumbSizePolicy.MIN_EDGE_PX) { "宽小于下限：${size[0]}" }
                assert(size[1] >= ThumbSizePolicy.MIN_EDGE_PX) { "高小于下限：${size[1]}" }
            }
        }
    }
}
