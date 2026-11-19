package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 新版播放器的坐标、溢出与九宫格分页纯 JVM 测试。 */
class PlayerNewUiGeometryTest {
    @Test
    fun `square coordinates match design`() {
        assertEquals(
            listOf(
                PlayerNewUiGeometry.Point(26f, 96f),
                PlayerNewUiGeometry.Point(26f, 140f),
                PlayerNewUiGeometry.Point(26f, 184f),
            ),
            PlayerNewUiGeometry.sideSpec(emptyList(), round = false).positions,
        )
        // 底栏 5 格整行居中、中心间距 48：240 宽屏上就是 24/72/120/168/216。
        val five = (0 until 5).map { PlayerUiLayout.Slot.Button("b$it") }
        assertEquals(
            listOf(24f, 72f, 120f, 168f, 216f).map { PlayerNewUiGeometry.Point(it, 252f) },
            PlayerNewUiGeometry.bottomSpec(five, round = false).positions,
        )
        // 4 格（默认布局）与设计稿 S_BOT_X 完全一致。
        assertEquals(
            listOf(48f, 96f, 144f, 192f).map { PlayerNewUiGeometry.Point(it, 252f) },
            PlayerNewUiGeometry.bottomSpec(five.take(4), round = false).positions,
        )
    }

    @Test
    fun `round coordinates match radius angles and mirror`() {
        val left = PlayerNewUiGeometry.sideSpec(emptyList(), round = true).positions
        // polar(94, 155/180/205)：cos155=-cos25=-0.90631、sin155=sin25=0.42262
        assertPoint(31.31f, 76.77f, left[0])
        assertPoint(22.50f, 116.50f, left[1])
        assertPoint(31.31f, 156.23f, left[2])
        val mirrored = PlayerNewUiGeometry.mirrorX(
            PlayerNewUiGeometry.Spec(emptyList(), left),
            PlayerNewUiGeometry.REFERENCE_SIZE_DP,
        )
        mirrored.positions.forEachIndexed { index, point ->
            assertEquals(233f - left[index].x, point.x, 0.001f)
            assertEquals(left[index].y, point.y, 0f)
        }
        val bottom = PlayerNewUiGeometry.bottomSpec(emptyList(), round = true).positions
        // polar(88, 235) 与 polar(88, 305)
        assertPoint(66.03f, 188.59f, bottom[0])
        assertPoint(166.97f, 188.59f, bottom[3])
    }

    @Test
    fun `non reference round screen scales both axes`() {
        val scaled = PlayerNewUiGeometry.scalePoint(
            PlayerNewUiGeometry.Point(26.27f, 76.85f),
            466f,
        )
        assertPoint(52.54f, 153.70f, scaled)
    }

    @Test
    fun `overflow keeps order and never duplicates visible slots`() {
        fun buttons(prefix: String, count: Int): List<PlayerUiLayout.Slot> =
            (0 until count).map { PlayerUiLayout.Slot.Button("$prefix$it") }

        val layout = PlayerUiLayout.Layout(
            buttons("left", 5),
            buttons("right", 5),
            buttons("bl", 3),
            buttons("br", 3),
        )
        val (visible, overflow) = PlayerNewUiGeometry.visibleAndOverflow(layout, round = true)
        assertEquals(10, visible.size)
        // 圆屏底栏上限 4：bottom = bl0,bl1,bl2,br0,br1,br2 → 可见 4（含 br0），溢出 br1、br2
        assertEquals(listOf("left3", "left4", "right3", "right4", "br1", "br2"), overflow.map {
            (it as PlayerUiLayout.Slot.Button).key
        })
    }

    @Test
    fun `more pages are stable three by three chunks`() {
        val keys = (0 until 20).map { "key$it" }
        val pages = PlayerNewUiGeometry.pages(keys)
        assertEquals(listOf(9, 9, 2), pages.map { it.size })
        assertEquals(keys, pages.flatten())
        assertTrue(PlayerNewUiGeometry.pages(emptyList()).isEmpty())
    }

    @Test
    fun `grid capacity grows with available height`() {
        // 手机横屏放得下更多行，就不该为翻页而翻页。
        val cellH = 58f
        val gap = 6f
        // 刚好 3 行：3*58 + 2*6 = 186。
        assertEquals(9, PlayerNewUiGeometry.gridCapacity(cellH, gap, 186f))
        // 5 行：5*58 + 4*6 = 314。
        assertEquals(15, PlayerNewUiGeometry.gridCapacity(cellH, gap, 314f))
        // 高度不足一行时也至少给一行，不会算出 0 格。
        assertEquals(3, PlayerNewUiGeometry.gridCapacity(cellH, gap, 10f))
        // 非法输入退回设计稿的 9 格。
        assertEquals(9, PlayerNewUiGeometry.gridCapacity(0f, gap, 200f))
    }

    @Test
    fun `pages honours an explicit page size`() {
        val keys = (0 until 20).map { "key$it" }
        assertEquals(listOf(15, 5), PlayerNewUiGeometry.pages(keys, 15).map { it.size })
        assertEquals(keys, PlayerNewUiGeometry.pages(keys, 15).flatten())
        assertTrue(PlayerNewUiGeometry.pages(keys, 0).isEmpty())
    }

    private fun assertPoint(x: Float, y: Float, actual: PlayerNewUiGeometry.Point) {
        assertEquals(x, actual.x, 0.02f)
        assertEquals(y, actual.y, 0.02f)
    }
}
