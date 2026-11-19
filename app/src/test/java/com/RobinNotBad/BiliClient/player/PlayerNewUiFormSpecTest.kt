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
}
