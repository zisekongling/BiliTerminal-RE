package com.RobinNotBad.BiliClient.ui.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 圆屏列表效果的判定数学。
 *
 * 重点是 26.11.01 的反馈 #79：「评论区遇到长图时会把整个评论缩小到很小，且透明度很高」。
 * 根因是判定用了**条目中心**——带长图的评论很高，中心点已经贴到屏缘，于是整条被当成边缘条目。
 * 这里把「跨度覆盖屏幕中心 → 不缩放」「完全在中心一侧 → 按最近边算」钉死。
 */
class RoundListEffectMathTest {

    // 手表圆屏的典型数值：466×466 屏、中心 233、半径 233
    private val winH = 466
    private val centerY = 233f
    private val radius = 233f

    @Test
    fun tallRowCoveringCenter_isNotShrunk() {
        // 高 400 的长图评论，纵向从 50 跨到 450，把屏幕中心包在里面
        val t = RoundListEffectMath.tFor(50f, 400, centerY, radius, winH)

        assertNotNull("跨度覆盖中心的高条目必须参与判定（不是直接跳过）", t)
        assertEquals("覆盖屏幕中心 → t = 0", 0f, t!!, 0.0001f)
        assertEquals("不缩小", 1f, RoundListEffectMath.scaleFor(t), 0.0001f)
        assertEquals("不淡化", 1f, RoundListEffectMath.alphaFor(t), 0.0001f)
    }

    @Test
    fun tallRowBelowCenter_usesNearestEdgeNotCenter() {
        // 高 400 的条目整块在中心下方（300~700），中心点 500 离屏幕中心 267（> 半径）
        val t = RoundListEffectMath.tFor(300f, 400, centerY, radius, winH)!!

        // 最近边 300 距中心 67 → t = 67/233 ≈ 0.2876；按中心点算会是 1.0（缩到 0.5）
        assertEquals(0.2876f, t, 0.001f)
        assertTrue("长条目只是略微缩小，不应缩成一小团", RoundListEffectMath.scaleFor(t) > 0.95f)
        assertTrue("长条目不应被淡到看不清", RoundListEffectMath.alphaFor(t) > 0.95f)
    }

    @Test
    fun smallRowAtTopEdge_stillShrinksAndFades() {
        // 高 60 的小卡片贴在顶部（0~60），最近边距中心 173
        val t = RoundListEffectMath.tFor(0f, 60, centerY, radius, winH)!!

        assertEquals(0.7425f, t, 0.001f)
        assertTrue("边缘小条目照旧缩小", RoundListEffectMath.scaleFor(t) < 0.86f)
        assertTrue("边缘小条目照旧淡出", RoundListEffectMath.alphaFor(t) < 0.70f)
    }

    @Test
    fun smallRowInScreenCenter_isUntouched() {
        val t = RoundListEffectMath.tFor(203f, 60, centerY, radius, winH)!!

        assertEquals(0f, t, 0.0001f)
        assertEquals(1f, RoundListEffectMath.scaleFor(t), 0.0001f)
        assertEquals(1f, RoundListEffectMath.alphaFor(t), 0.0001f)
    }

    @Test
    fun nearFullScreenContent_isSkipped() {
        assertNull("整屏高的内容不是列表条目", RoundListEffectMath.tFor(0f, winH, centerY, radius, winH))
        assertNull("比整屏还高也不参与", RoundListEffectMath.tFor(0f, winH + 40, centerY, radius, winH))
    }

    @Test
    fun invalidInputs_areSkipped() {
        assertNull("高度为 0", RoundListEffectMath.tFor(0f, 0, centerY, radius, winH))
        assertNull("半径非法", RoundListEffectMath.tFor(0f, 100, centerY, 0f, winH))
        assertNull("屏高非法", RoundListEffectMath.tFor(0f, 100, centerY, radius, 0))
    }

    @Test
    fun tIsClampedToScreenEdge() {
        // 整块在屏幕上方外面：最近边距中心 283 > 半径
        val t = RoundListEffectMath.tFor(-100f, 50, centerY, radius, winH)!!

        assertEquals("超出半径按 1 处理", 1f, t, 0.0001f)
        assertEquals("缩放下限", RoundListEffectMath.MIN_SCALE, RoundListEffectMath.scaleFor(t), 0.0001f)
        assertEquals("透明度下限", 0.15f, RoundListEffectMath.alphaFor(t), 0.001f)
    }

    @Test
    fun curvesAreMonotonic() {
        var lastScale = Float.MAX_VALUE
        var lastAlpha = Float.MAX_VALUE
        for (i in 0..20) {
            val t = i / 20f
            val scale = RoundListEffectMath.scaleFor(t)
            val alpha = RoundListEffectMath.alphaFor(t)
            assertTrue("缩放必须随 t 单调不增", scale <= lastScale + 0.0001f)
            assertTrue("透明度必须随 t 单调不增", alpha <= lastAlpha + 0.0001f)
            lastScale = scale
            lastAlpha = alpha
        }
    }

    @Test
    fun oldCenterBasedMetricWouldHaveShrunkTheTallRow() {
        // 反面钉：旧实现直接拿中心点算，这个 400 高的评论会被缩到 0.5、淡到 0.15。
        // 如果将来有人把判定改回中心点，这条断言会先炸。
        val rowTop = 300f
        val rowHeight = 400
        val oldT = (((rowTop + rowHeight * 0.5f) - centerY) / radius).coerceIn(0f, 1f)
        assertEquals(1f, oldT, 0.0001f)
        assertEquals(0.5f, RoundListEffectMath.scaleFor(oldT), 0.0001f)
        assertEquals(0.15f, RoundListEffectMath.alphaFor(oldT), 0.001f)

        val fixedT = RoundListEffectMath.tFor(rowTop, rowHeight, centerY, radius, winH)!!
        assertTrue("修好后同一条目不再被当成贴边", RoundListEffectMath.scaleFor(fixedT) > 0.95f)
    }
}
