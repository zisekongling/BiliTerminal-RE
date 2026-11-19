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
        assertEquals(listOf(48f, 96f, 144f, 192f), watch.bottomPlacement(4, refW).slotXs)
        assertNull(watch.bottomPlacement(4, refW).playX)
        // 5 格：24 / 72 / 120 / 168 / 216。
        assertEquals(listOf(24f, 72f, 120f, 168f, 216f), watch.bottomPlacement(5, refW).slotXs)
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
    fun `phone play button takes the leftmost bottom cell and shifts slots right`() {
        val placement = phone.bottomPlacement(4, 411f)
        // 并入播放键后一行共 5 格，播放键占第 0 格，4 个槽位顺延。
        val all = listOfNotNull(placement.playX) + placement.slotXs
        assertEquals(5, all.size)
        assertTrue(all[0] < all[1])
        // 整行仍然居中：最左与最右关于屏幕中线对称。
        assertEquals(411f, all.first() + all.last(), 0.001f)
        // 槽位不会落在播放键那一格上（这是分开返回 playX 的意义）。
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

        // 长条屏（320 宽）：左右两列整段并进底栏，前几个排得下，其余进「更多」。
        val stripPlan = PlayerNewUiFormSpec.plan(strip, layout, 320f, hasGroup = false)
        assertTrue(stripPlan.left.isEmpty())
        assertTrue(stripPlan.right.isEmpty())
        // l0 是被并入底栏的（不是溢出）——这正是「两列并入底栏」的含义。
        assertTrue(stripPlan.bottom.any { (it as? PlayerUiLayout.Slot.Button)?.key == "l0" })
        assertTrue(stripPlan.overflow.any { (it as? PlayerUiLayout.Slot.Button)?.key == "br2" })
        assertEquals(allCount, stripPlan.bottom.size + stripPlan.overflow.size)
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
}
