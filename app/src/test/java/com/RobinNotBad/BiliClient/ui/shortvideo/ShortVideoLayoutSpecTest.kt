package com.RobinNotBad.BiliClient.ui.shortvideo

import com.RobinNotBad.BiliClient.activity.video.ShortVideoLayoutMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 短视频新版布局规格测试。
 *
 * 核心不变式：设计稿只标注了方屏 240×280 与圆屏 R=116.5 两个参考尺寸，
 * 其它分辨率必须由同一套比例推出来——**任何尺寸下元素都要落在屏内、互不重叠、
 * 且相对位置与设计稿一致**。这里逐条守住这些。
 */
class ShortVideoLayoutSpecTest {

    private val ref = ShortVideoLayoutSpec.Metrics(240f, 280f, round = false)

    /** 常见屏幕尺寸：手表方屏/圆屏、手机竖屏、手机横屏、长条屏。 */
    private val screens = listOf(
        Triple(240f, 280f, false),
        Triple(233f, 233f, true),
        Triple(466f, 466f, true),
        Triple(411f, 848f, false),
        Triple(806f, 394f, false),
        Triple(320f, 100f, false),
    )

    @Test
    fun `design reference reproduces the spec numbers`() {
        // 方屏 240×280：顶部中线 24、右侧动作条 y=110/154/198、UP 行 y=212、
        // 标题 228、播放量 246、进度条 266。
        assertEquals(24f, ShortVideoLayoutSpec.topRowCenterY(ref), 0.01f)
        assertEquals(listOf(110f, 154f, 198f), ShortVideoLayoutSpec.actionCenterYs(ref).map { it })
        assertEquals(212f, ShortVideoLayoutSpec.authorCenterY(ref), 0.01f)
        assertEquals(228f, ShortVideoLayoutSpec.titleTop(ref), 0.01f)
        assertEquals(246f, ShortVideoLayoutSpec.viewsTop(ref), 0.01f)
        assertEquals(266f, ShortVideoLayoutSpec.progressCenterY(ref), 0.01f)
        // 左右锚点。
        assertEquals(24f, ShortVideoLayoutSpec.backCenterX(ref), 0.01f)
        assertEquals(216f, ShortVideoLayoutSpec.moreCenterX(ref), 0.01f)
        assertEquals(214f, ShortVideoLayoutSpec.actionCenterX(ref), 0.01f)
        // 中心键在正中。
        assertEquals(120f, ShortVideoLayoutSpec.playCenterX(ref), 0.01f)
        assertEquals(140f, ShortVideoLayoutSpec.playCenterY(ref), 0.01f)
    }

    @Test
    fun `every anchor stays inside the screen on all shapes`() {
        for ((w, h, round) in screens) {
            val m = ShortVideoLayoutSpec.Metrics(w, h, round)
            val top = ShortVideoLayoutSpec.topRowCenterY(m)
            val progress = ShortVideoLayoutSpec.progressCenterY(m)
            // 顶部在进度条上方，且都在屏内。
            assertTrue("$w x $h: 顶部越界", top in 0f..h)
            assertTrue("$w x $h: 进度条越界", progress in 0f..h)
            assertTrue("$w x $h: 顶部应在进度条上方", top < progress)
            // 底部信息从上往下：UP 行 < 标题 < 播放量 < 进度条（屏幕坐标 y 越大越靠下）。
            assertTrue(
                "$w x $h: 底部信息顺序错乱",
                ShortVideoLayoutSpec.authorCenterY(m) < ShortVideoLayoutSpec.titleTop(m) &&
                    ShortVideoLayoutSpec.titleTop(m) < ShortVideoLayoutSpec.viewsTop(m) &&
                    ShortVideoLayoutSpec.viewsTop(m) < ShortVideoLayoutSpec.progressCenterY(m),
            )
            // 右侧动作条在屏内、且在中心键右侧。
            ShortVideoLayoutSpec.actionCenterYs(m).forEach { y ->
                assertTrue("$w x $h: 动作条越界", y in 0f..h)
            }
            assertTrue(ShortVideoLayoutSpec.actionCenterX(m) <= w)
            // 顶部三个热区不重叠。
            val half = ShortVideoLayoutSpec.touchSize(m) / 2f
            assertTrue(
                "$w x $h: 返回与 ⋯ 重叠",
                ShortVideoLayoutSpec.backCenterX(m) + half < ShortVideoLayoutSpec.moreCenterX(m) - half,
            )
        }
    }

    @Test
    fun `scale stays within bounds and grows with the screen`() {
        val watch = ShortVideoLayoutSpec.Metrics(240f, 280f, round = false)
        val phone = ShortVideoLayoutSpec.Metrics(411f, 848f, round = false)
        val tablet = ShortVideoLayoutSpec.Metrics(1200f, 1600f, round = false)
        assertEquals(1f, watch.scale, 0.01f)
        assertTrue(phone.scale > watch.scale)
        // 上限生效，不会无限放大。
        assertEquals(ShortVideoLayoutSpec.MAX_SCALE, tablet.scale, 0.001f)
        // 极小的屏也不会缩到按不着。
        val tiny = ShortVideoLayoutSpec.Metrics(120f, 120f, round = false)
        assertEquals(ShortVideoLayoutSpec.MIN_SCALE, tiny.scale, 0.001f)
    }

    @Test
    fun `bottom anchors keep the same proportion on a taller screen`() {
        // 手机竖屏：信息仍在底部，且不会与动作条重叠（动作条贴右侧，内容右侧有留白）。
        val phone = ShortVideoLayoutSpec.Metrics(411f, 848f, round = false)
        val bottomGap = phone.heightDp - ShortVideoLayoutSpec.authorCenterY(phone)
        assertTrue("手机：UP 行应贴底", bottomGap > 0f && bottomGap < phone.heightDp * 0.2f)
        val contentRight = phone.widthDp - ShortVideoLayoutSpec.contentRightInset(phone)
        val actionLeft = ShortVideoLayoutSpec.actionCenterX(phone) -
            ShortVideoLayoutSpec.actionCellWidth(phone) / 2f
        assertTrue("手机：内容区与动作条不应重叠", contentRight <= actionLeft)
    }

    @Test
    fun `progress touch area never swallows the text above it`() {
        for ((w, h, round) in screens) {
            val m = ShortVideoLayoutSpec.Metrics(w, h, round)
            val touch = ShortVideoLayoutSpec.progressTouchHeight(m)
            val gap = ShortVideoLayoutSpec.titleOffsetFromBottom(m) -
                ShortVideoLayoutSpec.progressOffsetFromBottom(m)
            assertTrue("$w x $h: 进度热区盖住标题", touch <= gap)
            assertTrue("$w x $h: 进度热区过小", touch > 0f)
        }
    }

    @Test
    fun `panels never exceed screen height`() {
        for ((w, h, round) in screens) {
            val m = ShortVideoLayoutSpec.Metrics(w, h, round)
            ShortVideoLayoutSpec.PanelKind.values().forEach { kind ->
                val panelH = ShortVideoLayoutSpec.panelHeight(m, kind)
                assertTrue("$w x $h $kind: 面板过高", panelH <= h)
                assertTrue("$w x $h $kind: 面板过矮", panelH > 0f)
            }
            assertTrue(ShortVideoLayoutSpec.panelMaxWidth(m) <= w)
        }
    }

    @Test
    fun `round action points keep the cell inside the circle`() {
        for (d in listOf(233f, 466f)) {
            val m = ShortVideoLayoutSpec.Metrics(d, d, round = true)
            val r = ShortVideoLayoutSpec.roundActionRadius(m)
            val halfCell = ShortVideoLayoutSpec.actionCellWidth(m) / 2f
            // 最外那一格的中心到圆心距离 + 半格宽，必须小于半径。
            assertTrue("直径 $d: 动作条贴出表圈", r + halfCell <= d / 2f)
            ShortVideoLayoutSpec.ROUND_ACTION_ANGLES.forEach { deg ->
                val (x, y) = ShortVideoLayoutSpec.roundPoint(m, r, deg)
                val dist = Math.hypot((x - m.centerX).toDouble(), (y - m.centerY).toDouble())
                assertTrue("直径 $d: 落点不在圆周上", kotlin.math.abs(dist - r) < 0.01)
                assertTrue("直径 $d: 落点越界", x in 0f..d && y in 0f..d)
            }
            // 顶部返回 / ⋯ 在圆内。
            listOf(true, false).forEach { right ->
                val (x, y) = ShortVideoLayoutSpec.roundTopPoint(m, right)
                val dist = Math.hypot((x - m.centerX).toDouble(), (y - m.centerY).toDouble())
                assertTrue("直径 $d: 顶部按钮贴出表圈", dist + ShortVideoLayoutSpec.touchSize(m) / 2f <= d / 2f)
            }
        }
    }

    @Test
    fun `video area still fits inside whatever box it gets`() {
        // 布局规格不接管画面，但要求画面区域在各尺寸下都能算出合法尺寸。
        for ((w, h, round) in screens) {
            val fitted = if (round) {
                ShortVideoLayoutMath.fitRound(9f / 16f, minOf(w, h).toInt(), w.toInt(), h.toInt())
            } else {
                ShortVideoLayoutMath.fit(9f / 16f, w.toInt(), h.toInt())
            }
            assertTrue("$w x $h: 画面尺寸非法", fitted != null && fitted[0] > 0 && fitted[1] > 0)
            assertTrue("$w x $h: 画面超出可用区", fitted!![0] <= w && fitted[1] <= h)
        }
    }

    // ==================== 圆屏参考值（设计稿圆屏规格表 @ 直径 233）====================

    private val roundRef = ShortVideoLayoutSpec.Metrics(233f, 233f, round = true)

    @Test
    fun `round reference reproduces the spec numbers`() {
        // 返回 / ⋯ 中心 (58,42) / (175,42)。
        val (bx, by) = ShortVideoLayoutSpec.roundTopPoint(roundRef, right = false)
        assertEquals(58f, bx, 0.5f)
        assertEquals(42f, by, 0.5f)
        val (mx, my) = ShortVideoLayoutSpec.roundTopPoint(roundRef, right = true)
        assertEquals(175f, mx, 0.5f)
        assertEquals(42f, my, 0.5f)
        // 时钟中线 16、顶部压暗 64、底部压暗 96。
        assertEquals(16f, ShortVideoLayoutSpec.roundClockCenterY(roundRef), 0.01f)
        assertEquals(64f, ShortVideoLayoutSpec.roundTopBarHeight(roundRef), 0.01f)
        assertEquals(96f, ShortVideoLayoutSpec.roundBottomScrimHeight(roundRef), 0.01f)
        // 底部三行中线 170 / 186 / 202，头像 18。
        assertEquals(170f, ShortVideoLayoutSpec.roundAuthorCenterY(roundRef), 0.01f)
        assertEquals(186f, ShortVideoLayoutSpec.roundTitleCenterY(roundRef), 0.01f)
        assertEquals(202f, ShortVideoLayoutSpec.roundViewsCenterY(roundRef), 0.01f)
        assertEquals(18f, ShortVideoLayoutSpec.roundAvatarSize(roundRef), 0.01f)
        // 动作条 r≈90、相邻中心距 42；进度弧 r=111。
        assertEquals(90f, ShortVideoLayoutSpec.roundActionRadius(roundRef), 0.5f)
        val r = ShortVideoLayoutSpec.roundActionRadius(roundRef)
        val (x0, y0) = ShortVideoLayoutSpec.roundPoint(roundRef, r, 27f)
        val (x1, y1) = ShortVideoLayoutSpec.roundPoint(roundRef, r, 0f)
        assertEquals(42f, Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat(), 0.5f)
        assertEquals(111f, ShortVideoLayoutSpec.roundArcRadius(roundRef), 0.01f)
        // 面板内缩 26 → 181×181、圆角 28。
        assertEquals(26f, ShortVideoLayoutSpec.roundPanelInset(roundRef), 0.5f)
        assertEquals(181f, ShortVideoLayoutSpec.roundPanelSize(roundRef), 1f)
        assertEquals(28f, ShortVideoLayoutSpec.roundPanelCorner(roundRef), 0.01f)
    }

    @Test
    fun `round bottom rows fit inside the chord on real watch sizes`() {
        for (d in listOf(192f, 233f, 466f)) {
            val m = ShortVideoLayoutSpec.Metrics(d, d, round = true)
            val ys = listOf(
                ShortVideoLayoutSpec.roundAuthorCenterY(m) to ShortVideoLayoutSpec.roundAuthorMaxWidth(m),
                ShortVideoLayoutSpec.roundTitleCenterY(m) to ShortVideoLayoutSpec.roundTitleMaxWidth(m),
                ShortVideoLayoutSpec.roundViewsCenterY(m) to ShortVideoLayoutSpec.roundViewsMaxWidth(m),
            )
            var prev = 0f
            for ((y, width) in ys) {
                assertTrue("直径 $d: 底部行顺序错乱", y > prev)
                prev = y
                // 行宽不得超过该 y 处的圆内弦宽（否则文字被表圈切掉）。
                val radius = d / 2f
                val dy = kotlin.math.abs(y - m.centerY)
                val chord = 2f * kotlin.math.sqrt((radius * radius - dy * dy).toDouble()).toFloat()
                assertTrue("直径 $d: y=$y 行宽 $width 超出弦宽 $chord", width <= chord)
            }
            assertTrue(prev < d)
            // 顶部热区在 192dp 小表盘上也放得下。
            listOf(true, false).forEach { right ->
                val (x, y) = ShortVideoLayoutSpec.roundTopPoint(m, right)
                val dist = Math.hypot((x - m.centerX).toDouble(), (y - m.centerY).toDouble())
                assertTrue("直径 $d: 顶部按钮贴出表圈", dist + ShortVideoLayoutSpec.touchSize(m) / 2f <= d / 2f)
            }
        }
    }

    @Test
    fun `more grid cells follow the spec on both shapes`() {
        assertEquals(64f, ShortVideoLayoutSpec.moreCellWidth(ref), 0.01f)
        assertEquals(58f, ShortVideoLayoutSpec.moreCellHeight(ref), 0.01f)
        assertEquals(6f, ShortVideoLayoutSpec.moreCellGap(ref), 0.01f)
        assertEquals(46f, ShortVideoLayoutSpec.moreCellWidth(roundRef), 0.01f)
        assertEquals(46f, ShortVideoLayoutSpec.moreCellHeight(roundRef), 0.01f)
        assertEquals(4f, ShortVideoLayoutSpec.moreCellGap(roundRef), 0.01f)
    }

    @Test
    fun `progress and center key spec values`() {
        assertEquals(2f, ShortVideoLayoutSpec.progressTrackHeight(ref), 0.01f)
        assertEquals(4f, ShortVideoLayoutSpec.progressTrackHeightDragging(ref), 0.01f)
        assertEquals(1.5f, ShortVideoLayoutSpec.progressTrackHeightIdle(ref), 0.01f)
        assertEquals(24f, ShortVideoLayoutSpec.progressTouchHeight(ref), 0.01f)
        assertEquals(6.25f, ShortVideoLayoutSpec.progressIdleOffsetFromBottom(ref), 0.01f)
        assertEquals(56f, ShortVideoLayoutSpec.playSize(ref), 0.01f)
        assertEquals(24f, ShortVideoLayoutSpec.playIconSize(ref), 0.01f)
        assertEquals(48f, ShortVideoLayoutSpec.topBarHeight(ref), 0.01f)
        assertEquals(18f, ShortVideoLayoutSpec.panelCorner(ref), 0.01f)
    }

    @Test
    fun `arc fraction maps touch points along the 90 degree arc`() {
        // 触点用屏幕坐标（Canvas 角：0° 正右、顺时针为正）。弧从 Canvas 135°（左下）
        // 扫到 45°（右下），经过 90°（正下方）。
        val cx = 116.5f
        val cy = 116.5f
        val r = 111f
        fun at(deg: Float): Pair<Float, Float> {
            val rad = Math.toRadians(deg.toDouble())
            return (cx + r * kotlin.math.cos(rad)).toFloat() to (cy + r * kotlin.math.sin(rad)).toFloat()
        }
        fun fraction(deg: Float): Float {
            val (x, y) = at(deg)
            return ShortVideoLayoutSpec.arcFraction(cx, cy, x, y)
        }
        assertEquals(0f, fraction(135f), 0.001f)
        assertEquals(0.25f, fraction(112.5f), 0.001f)
        assertEquals(0.5f, fraction(90f), 0.001f)
        assertEquals(1f, fraction(45f), 0.001f)
        // 缺口区按最近端点吸附：正右离终点近 → 1，正左离起点近 → 0。
        assertEquals(1f, fraction(0f), 0.001f)
        assertEquals(0f, fraction(180f), 0.001f)
    }
}
