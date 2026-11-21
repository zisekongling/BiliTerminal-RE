package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新版播放器三套界面形态的几何规格测试。
 *
 * 核心不变式：**手表形态必须与设计稿逐项一致**（240×280 时 96/140/184、222、252、272），
 * 因为那是设计稿唯一标注过的参考尺寸，其它形态都是从这个基准推出去的。
 */
class PlayerNewUiFormSpecTest {

    private val watch = PlayerNewUiFormSpec.WATCH
    private val phone = PlayerNewUiFormSpec.PHONE
    private val strip = PlayerNewUiFormSpec.STRIP
    private val refW = PlayerNewUiFormSpec.REF_WIDTH
    private val refH = PlayerNewUiFormSpec.REF_HEIGHT

    @Test
    fun `watch form reproduces design spec exactly`() {
        assertEquals(1f, watch.scale, 0f)
        assertFalse(watch.playInRow)
        assertTrue(watch.sideColumns)
        assertEquals(5, watch.bottomMaxSlots)

        // 左右两列中心 y：96 / 140 / 184。
        assertEquals(listOf(96f, 140f, 184f), watch.sideYs(refH))
        assertEquals(26f, watch.sideX, 0f)
        assertEquals(214f, watch.sideRightX(refW), 0f)
        // 进度行中线 222、轨道控件顶 208、时间行顶 215。
        assertEquals(222f, watch.progressCenterY(refH), 0f)
        assertEquals(208f, watch.trackTop(refH), 0f)
        assertEquals(215f, watch.timeTop(refH), 0f)
        // 底栏中线 252、间距 48、细进度线顶 272。
        assertEquals(252f, watch.bottomY(refH), 0f)
        assertEquals(48f, watch.bottomSpacing, 0f)
        assertEquals(272f, watch.idleTop(refH), 0f)
        // 拖动预览三行 100 / 116 / 156。
        assertEquals(listOf(100f, 116f, 156f), watch.seekRows(refH))
        // 热区与图标。
        assertEquals(36f, watch.buttonSize, 0f)
        assertEquals(20f, watch.iconSize, 0f)
        assertEquals(56f, watch.playSize, 0f)
        assertEquals(24f, watch.playIconSize, 0f)
    }

    @Test
    fun `watch bottom row is centred with four default slots`() {
        // 默认布局底栏 4 格：48 / 96 / 144 / 192（设计稿 S_BOT_X）。
        assertEquals(listOf(48f, 96f, 144f, 192f), watch.bottomPlacement(4, 0, refW).slotXs)
        assertNull(watch.bottomPlacement(4, 0, refW).playX)
        // 5 格：24 / 72 / 120 / 168 / 216。
        assertEquals(listOf(24f, 72f, 120f, 168f, 216f), watch.bottomPlacement(5, 0, refW).slotXs)
        // 底栏容量：240 宽 5 格。
        assertEquals(5, watch.bottomSlotCount(refW))
    }

    @Test
    fun `phone form scales every anchor by 14`() {
        assertEquals(1.4f, phone.scale, 0f)
        assertTrue(phone.playInRow)
        assertTrue(phone.sideColumns)

        assertEquals(50, phone.buttonSize.toInt())
        assertEquals(28, phone.iconSize.toInt())
        // 0.5 的浮点误差留一点余量：这些值最终都要过 toInt 换算成 px。
        assertEquals(26f * 1.4f, phone.sideX, 0.001f)
        assertEquals(refH - 58f * 1.4f, phone.progressCenterY(refH), 0.001f)
        assertEquals(refH - 28f * 1.4f, phone.bottomY(refH), 0.001f)
    }

    @Test
    fun `phone play button sits dead centre with slots flanking it`() {
        val width = 411f
        val placement = phone.bottomPlacement(2, 2, width)
        // 播放键固定在屏幕正中，槽位分列两侧。
        assertEquals(width / 2f, placement.playX!!, 0.001f)
        assertEquals(2, placement.leftXs.size)
        assertEquals(2, placement.rightXs.size)
        // 左侧全在中心左边、右侧全在中心右边，且紧邻的一格相距一个间距。
        assertTrue(placement.leftXs.all { it < width / 2f })
        assertTrue(placement.rightXs.all { it > width / 2f })
        assertEquals(width / 2f - phone.bottomSpacing, placement.leftXs.max(), 0.001f)
        assertEquals(width / 2f + phone.bottomSpacing, placement.rightXs.min(), 0.001f)
        // 不会有一格压在播放键上。
        assertFalse(placement.slotXs.contains(placement.playX))
    }

    @Test
    fun `strip form drops side columns and merges them into the bottom row`() {
        assertEquals(0.8f, strip.scale, 0f)
        assertTrue(strip.playInRow)
        assertFalse(strip.sideColumns)
    }

    @Test
    fun `plan keeps side columns capped at three and bottoms by width`() {
        fun slot(prefix: String, count: Int) = (0 until count).map { PlayerUiLayout.Slot.Button("$prefix$it") }
        val layout = PlayerUiLayout.Layout(
            slot("l", 5),
            slot("r", 5),
            slot("bl", 3),
            slot("br", 3),
        )

        // 手机（411 宽）：左右各 3 格，底栏按容量截断，其余进「更多」。
        val phonePlan = PlayerNewUiFormSpec.plan(phone, layout, 411f, hasGroup = false)
        assertEquals(3, phonePlan.left.size)
        assertEquals(3, phonePlan.right.size)
        assertEquals(phone.bottomSlotCount(411f) - 1, phonePlan.bottom.size) // 借一格给「更多」
        assertTrue(phonePlan.needsMoreCell)
        val allCount = layout.left.size + layout.right.size + layout.bottomLeft.size + layout.bottomRight.size
        assertEquals(allCount, phonePlan.left.size + phonePlan.right.size + phonePlan.bottom.size + phonePlan.overflow.size)
        // 溢出保持原顺序：左列余下在前，右列余下在后，最后才是底栏余下。
        assertEquals(
            listOf("l3", "l4", "r3", "r4"),
            phonePlan.overflow.take(4).map { (it as PlayerUiLayout.Slot.Button).key },
        )

        // 长条屏（320 宽）：左右两列整段并进底栏；16 个槽位塞不进 7 格，放不下的进「更多」。
        val stripPlan = PlayerNewUiFormSpec.plan(strip, layout, 320f, hasGroup = false)
        assertTrue(stripPlan.left.isEmpty())
        assertTrue(stripPlan.right.isEmpty())
        // 放不下时先保右边（用户放在主键右侧的高频键），剩下的容量从左半按原顺序取。
        assertEquals(3, stripPlan.bottomRight.size)
        assertEquals(layout.bottomRight, stripPlan.bottomRight)
        // 容量受 bottomMaxSlots 封顶（长条屏 6），借掉一格给「更多」后剩 5；右边 3 格全留，左边剩 2。
        assertEquals(2, stripPlan.bottomLeft.size)
        // 被并入底栏的两列槽位确实在底栏里（不是被丢掉）。
        assertTrue(stripPlan.bottomLeft.any { (it as? PlayerUiLayout.Slot.Button)?.key == "l0" })
        // 没塞下的一律进「更多」，一个都不丢。
        assertTrue(stripPlan.overflow.isNotEmpty())
        assertEquals(allCount, stripPlan.bottomCount + stripPlan.overflow.size)
    }

    @Test
    fun `plan does not borrow a more cell when a group already exists`() {
        fun slot(prefix: String, count: Int) = (0 until count).map { PlayerUiLayout.Slot.Button("$prefix$it") }
        // 左列 5 个必然溢出，但有显式组时组本身是入口，不该再占底栏一格。
        val layout = PlayerUiLayout.Layout(slot("l", 5), emptyList(), emptyList(), emptyList())
        val withGroup = PlayerNewUiFormSpec.plan(watch, layout, refW, hasGroup = true)
        assertFalse(withGroup.needsMoreCell)
        assertTrue(withGroup.overflow.isNotEmpty())
        // 没有组时要借格。
        assertTrue(PlayerNewUiFormSpec.plan(watch, layout, refW, hasGroup = false).needsMoreCell)
    }

    @Test
    fun `plan needs no more cell when everything fits`() {
        val layout = PlayerUiLayout.Layout(
            listOf(PlayerUiLayout.Slot.Button("a")),
            emptyList(),
            listOf(PlayerUiLayout.Slot.Button("b")),
            emptyList(),
        )
        val plan = PlayerNewUiFormSpec.plan(watch, layout, refW, hasGroup = false)
        assertFalse(plan.needsMoreCell)
        assertTrue(plan.overflow.isEmpty())
    }

    @Test
    fun `anchors stay inside the screen for tall and short screens`() {
        // 手机竖屏（411×731）：所有锚点都要落在屏内，且底栏在进度行下方。
        assertEquals(731f - 28f * 1.4f, phone.bottomY(731f), 0.001f)
        assertTrue(phone.bottomY(731f) > phone.progressCenterY(731f))
        assertTrue(phone.progressCenterY(731f) > phone.sideYs(731f).last())
        // 长条屏（320×100）：底栏仍在地面附近，不越界。
        assertTrue(strip.bottomY(100f) > 0f)
        assertTrue(strip.bottomY(100f) < 100f)
        assertTrue(strip.idleTop(100f) < 100f)
    }

    @Test
    fun `of falls back to watch for unknown or null forms`() {
        assertTrue(PlayerNewUiFormSpec.of(null) === watch)
        assertTrue(PlayerNewUiFormSpec.of("nonsense") === watch)
        assertTrue(PlayerNewUiFormSpec.of("phone") === phone)
        assertTrue(PlayerNewUiFormSpec.of("strip") === strip)
    }

    @Test
    fun `invisible slots are dropped so the bottom row does not leave a gap`() {
        // 默认布局底栏是 [清晰度][音量-][音量+] + 播放键；单 P 视频时分P 不在布局里，
        // 但用户的存档里可能留着它——那一格必须被摘掉，否则右边空出一块。
        val layout = PlayerUiLayout.Layout(
            left = listOf(PlayerUiLayout.Slot.Button("lock")),
            right = emptyList(),
            bottomLeft = listOf(
                PlayerUiLayout.Slot.Button("quality"),
                PlayerUiLayout.Slot.Button("vol_down"),
            ),
            bottomRight = listOf(
                PlayerUiLayout.Slot.Button("vol_up"),
                PlayerUiLayout.Slot.Button("pages"),
            ),
        )
        val visible = PlayerNewUiFormSpec.visibleOnly(layout) { key -> key != "pages" }
        assertEquals(3, visible.bottomLeft.size + visible.bottomRight.size)
        assertTrue(visible.bottomRight.none { (it as PlayerUiLayout.Slot.Button).key == "pages" })
        // 左列不受影响。
        assertEquals(1, visible.left.size)
    }

    @Test
    fun `group slots survive the visibility filter`() {
        // 组是「更多」的入口，判断可见性得逐格看它里面，不能整组摘掉。
        val layout = PlayerUiLayout.Layout(
            emptyList(),
            listOf(PlayerUiLayout.Slot.Group(listOf("a", "b"))),
            emptyList(),
            emptyList(),
        )
        val visible = PlayerNewUiFormSpec.visibleOnly(layout) { false }
        assertEquals(1, visible.right.size)
        assertEquals(emptyList<PlayerUiLayout.Slot>(), visible.left)
    }

    @Test
    fun `dropping an invisible slot does not leave a gap around the play key`() {
        // 摘掉一格后，播放键仍在正中，剩下的槽位仍然紧贴着它按固定间距排开——
        // 不会在某一侧留出一个空格子。
        val width = 411f
        for (left in 0..3) {
            for (right in 0..3) {
                val p = phone.bottomPlacement(left, right, width)
                assertEquals(width / 2f, p.playX!!, 0.001f)
                assertEquals("left=$left 应全部在中心左侧", left, p.leftXs.count { it < width / 2f })
                assertEquals("right=$right 应全部在中心右侧", right, p.rightXs.count { it > width / 2f })
                // 相邻中心距恒定。
                p.leftXs.zipWithNext { a, b -> assertEquals(phone.bottomSpacing, b - a, 0.001f) }
                p.rightXs.zipWithNext { a, b -> assertEquals(phone.bottomSpacing, b - a, 0.001f) }
            }
        }
    }

    @Test
    fun `round geometry scales with the real dial size`() {
        // 233 参考系下的落点，在 466 的表盘上必须整体放大一倍——
        // 这一条守着「widthDp() 不许对圆屏直接返回参考值」这个曾经存在的 bug。
        val ref = PlayerNewUiGeometry.sideSpec(emptyList(), round = true).positions[0]
        val scaled = PlayerNewUiGeometry.scalePoint(ref, 466f)
        assertEquals(ref.x * 2f, scaled.x, 0.001f)
        assertEquals(ref.y * 2f, scaled.y, 0.001f)
        // 233 的表盘上不缩放，与设计稿逐项一致。
        val same = PlayerNewUiGeometry.scalePoint(ref, PlayerNewUiGeometry.REFERENCE_SIZE_DP)
        assertEquals(ref.x, same.x, 0.001f)
        assertEquals(ref.y, same.y, 0.001f)
    }

    @Test
    fun `grid dots keep a finger sized hot zone that never overlaps`() {
        // 视觉点只有 8dp，手指点不中；热区必须 ≥24dp，且相邻热区不许叠在一起。
        // 这条与屏幕宽度无关，所以 233dp 圆屏与 192dp 方屏都成立。
        for (spec in listOf(watch, phone, strip)) {
            for (count in 1..6) {
                for (availableH in listOf(146f, 100f, 60f, 24f)) {
                    val dots = spec.gridDots(count, availableH)
                    assertTrue("spec=${spec.form} count=$count 热区不足 24dp", dots.hot >= PlayerNewUiFormSpec.MIN_TOUCH_DP)
                    assertTrue("spec=${spec.form} count=$count 间距小于热区", dots.spacing >= dots.hot)
                    assertEquals(dots.spacing * count, dots.height, 0.001f)
                    // 视觉点仍是设计稿的 8dp / 当前页 12dp，且都装得进热区。
                    assertEquals(spec.s(8f), dots.dot, 0.001f)
                    assertEquals(spec.s(12f), dots.dotActive, 0.001f)
                    assertTrue(dots.dotActive <= dots.hot)
                    // 逐颗按 topMargin = index * spacing 落位，任意两颗不重叠。
                    val bands = (0 until count).map { PlayerNewUiGeometry.Band(it * dots.spacing, dots.hot) }
                    for (i in bands.indices) {
                        for (j in i + 1 until bands.size) {
                            assertTrue("spec=${spec.form} count=$count 第 $i 与第 $j 颗热区重叠", !bands[i].overlaps(bands[j]))
                        }
                    }
                    // 整列高度不超过 spacing*count，最后一颗不会伸出列外。
                    assertTrue(bands.last().bottom <= dots.height + 0.001f)
                }
            }
        }
        // 页数非法时按一页算，不会算出 0 或负高度。
        assertEquals(watch.gridDots(1, 146f).height, watch.gridDots(0, 146f).height, 0.001f)
    }

    @Test
    fun `grid plus dots still fit inside the round bezel`() {
        // 圆屏：网格 146dp + 留白 + 24dp 热区，整块居中后最外侧角点仍要在表圈（r=116.5）内。
        val size = PlayerNewUiGeometry.REFERENCE_SIZE_DP
        val gridW = 46f * 3 + 4f * 2
        val dots = watch.gridDots(6, 146f)
        val total = gridW + watch.gridDotsMargin + dots.hot
        assertTrue("圆屏上网格+页码点整块宽 $total 超出直径", total <= size)
        val left = (size - total) / 2f
        val dotsRight = left + gridW + watch.gridDotsMargin + dots.hot
        val top = (size - dots.height) / 2f
        val bottom = top + dots.height
        val cx = size / 2f
        val cy = size / 2f
        for (y in listOf(top, bottom)) {
            val dx = dotsRight - cx
            val dy = y - cy
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            assertTrue("页码点角点半径 $r 顶出表圈", r < PlayerNewUiGeometry.ROUND_RADIUS_DP)
        }
        // 方屏参考尺寸 240dp：网格 204 + 留白 2 + 热区 24 = 230，仍装得下。
        assertTrue(watch.gridW + watch.gridDotsMargin + watch.gridDots(3, 186f).hot <= refW)
    }

    @Test
    fun `quality badge fits both the round row and the square sheet row`() {
        for (spec in listOf(watch, phone, strip)) {
            // 徽标文字 8sp（关字体内边距后约 1.1719em）必须装得进徽标高度。
            assertTrue(spec.qualityBadgeText * 1.1719f <= spec.qualityBadgeHeight)
            // 方屏抽屉行高 34dp、圆屏清晰度行高固定 20dp，徽标都不能超。
            assertTrue(spec.qualityBadgeHeight <= spec.s(34f))
            assertTrue(spec.qualityBadgeHeight <= 20f)
            assertTrue(spec.qualityBadgeMargin > 0f)
            assertTrue(spec.qualityBadgePadH > 0f)
        }
    }

    @Test
    fun `quality label width leaves room for the badge and the check`() {
        // 方屏抽屉：屏宽扣掉两侧留白 40、徽标 40、勾 32。
        assertEquals(128f, watch.qualityLabelMaxWidth(240f, hasBadge = true, hasCheck = true, centered = false), 0.001f)
        // 圆屏居中滚轮：留白 24、徽标 40、勾 20。
        assertEquals(149f, watch.qualityLabelMaxWidth(233f, hasBadge = true, hasCheck = true, centered = true), 0.001f)
        // 没徽标没勾时名字可以占满。
        assertEquals(209f, watch.qualityLabelMaxWidth(233f, hasBadge = false, hasCheck = false, centered = true), 0.001f)
        // 手机形态按 1.4 倍扣。
        assertEquals(411f - 56f - 56f - 44.8f, phone.qualityLabelMaxWidth(411f, hasBadge = true, hasCheck = true, centered = false), 0.01f)
        // 屏再窄也保底 48dp，不会算出 0 或负宽把名字整段吃掉。
        assertEquals(48f, watch.qualityLabelMaxWidth(60f, hasBadge = true, hasCheck = true, centered = true), 0.001f)
    }
}
