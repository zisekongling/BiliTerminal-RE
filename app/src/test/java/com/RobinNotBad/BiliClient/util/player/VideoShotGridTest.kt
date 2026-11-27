package com.RobinNotBad.BiliClient.util.player

import com.RobinNotBad.BiliClient.model.VideoShot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快照拼版取格逻辑。
 *
 * 测试数据用 **26.11.27 无 Cookie 实测 `aid=2&cid=62131` 的真实响应**：
 * `img_x_len=10 / img_y_len=10 / img_x_size=160 / img_y_size=90`，
 * `image` 两张（`62131.jpg`、`62131-1.jpg`），`index` 从 0 开始且**有重复值**。
 */
class VideoShotGridTest {

    private val realIndex = listOf(0, 0, 16, 33, 50, 66, 83, 100, 116, 133, 136, 136, 153, 165, 165, 182)

    private fun shot(
        images: List<String> = listOf("https://i0.hdslb.com/bfs/videoshot/62131.jpg"),
        index: List<Int> = realIndex,
        xLen: Int = 10,
        yLen: Int = 10,
        xSize: Int = 160,
        ySize: Int = 90
    ) = VideoShot(images, index, xLen, yLen, xSize, ySize)

    // ==================== 可用性 ====================

    @Test
    fun isUsable_trueForRealResponse() {
        assertTrue("真实响应可用", VideoShotGrid.isUsable(shot()))
    }

    @Test
    fun isUsable_falseWhenAnyPartMissing() {
        assertFalse("null 不可用", VideoShotGrid.isUsable(null))
        assertFalse("没图不可用", VideoShotGrid.isUsable(shot(images = emptyList())))
        assertFalse("没时间表不可用（服务端可能不给 index）", VideoShotGrid.isUsable(shot(index = emptyList())))
        assertFalse("格宽为 0 不可用", VideoShotGrid.isUsable(shot(xSize = 0)))
        assertFalse("格高为 0 不可用", VideoShotGrid.isUsable(shot(ySize = 0)))
        assertFalse("列数为 0 不可用", VideoShotGrid.isUsable(shot(xLen = 0)))
        assertFalse("行数为 0 不可用", VideoShotGrid.isUsable(shot(yLen = 0)))
    }

    @Test
    fun cellsPerSheet_multipliesGrid() {
        assertEquals("10×10 网格", 100, VideoShotGrid.cellsPerSheet(shot()))
        assertEquals("非法尺寸返回 0", 0, VideoShotGrid.cellsPerSheet(shot(xLen = -1)))
    }

    // ==================== 时间 → 格子 ====================

    @Test
    fun indexAt_emptyTable_returnsMinusOne() {
        assertEquals(-1, VideoShotGrid.indexAt(shot(index = emptyList()), 5000L))
    }

    @Test
    fun indexAt_beforeFirstOrNegative_returnsFirstCell() {
        assertEquals("0ms 落在第一格", 0, VideoShotGrid.indexAt(shot(), 0L))
        assertEquals("负数按第一格算", 0, VideoShotGrid.indexAt(shot(), -5000L))
    }

    /**
     * 取「最后一个不晚于目标」的格子：10 秒时表里前两项都是 0、第三项才 16，
     * 所以应停在**下标 1**（重复值里的后一个），而不是跨到 16 秒那格。
     */
    @Test
    fun indexAt_betweenRepeatedZeros_returnsLastOfThem() {
        assertEquals(1, VideoShotGrid.indexAt(shot(), 10_000L))
        assertEquals("16 秒整应落到那格", 2, VideoShotGrid.indexAt(shot(), 16_000L))
    }

    @Test
    fun indexAt_repeatedSeconds_returnsLaterCell() {
        assertEquals("136 秒在表里出现两次（下标 10、11）", 11, VideoShotGrid.indexAt(shot(), 136_000L))
        assertEquals("165 秒同样重复（下标 13、14）", 14, VideoShotGrid.indexAt(shot(), 165_000L))
    }

    @Test
    fun indexAt_beyondLast_returnsLastCell() {
        assertEquals(realIndex.size - 1, VideoShotGrid.indexAt(shot(), 99_999_000L))
    }

    // ==================== 格子 → 裁剪矩形 ====================

    @Test
    fun cropFor_firstCell_isTopLeft() {
        val crop = VideoShotGrid.cropFor(shot(), 0L)!!
        assertEquals("第一张拼版", 0, crop.sheetIndex)
        assertEquals(VideoShotGrid.CropRect(0, 0, 160, 90), crop.rect)
    }

    /** 第 11 格（0 基）在 10 列网格里是第 2 行第 2 列，左上角就是 (160, 90)。 */
    @Test
    fun cropFor_secondRow_offsetsByCellSize() {
        val crop = VideoShotGrid.cropFor(shot(), 136_000L)!!
        assertEquals("136 秒落在下标 11", 0, crop.sheetIndex)
        assertEquals(VideoShotGrid.CropRect(160, 90, 320, 180), crop.rect)
    }

    @Test
    fun cropFor_lastCellOfSheet_isBottomRight() {
        // 下标 99 = 第 10 行第 10 列（0 基 9,9）；造一份时间表把它精确指到那格
        val table = MutableList(100) { it }
        val crop = VideoShotGrid.cropFor(shot(index = table), 99_000L)!!
        assertEquals("仍第一张拼版", 0, crop.sheetIndex)
        assertEquals(VideoShotGrid.CropRect(1440, 810, 1600, 900), crop.rect)
    }

    /** 第 100 格已经跨到第二张拼版，且要回到该拼版的左上角。 */
    @Test
    fun cropFor_secondSheet_restartsAtOrigin() {
        val table = MutableList(200) { it }
        val crop = VideoShotGrid.cropFor(
            shot(images = listOf("https://x/0.jpg", "https://x/1.jpg"), index = table),
            100_000L
        )!!
        assertEquals("第二张拼版", 1, crop.sheetIndex)
        assertEquals(VideoShotGrid.CropRect(0, 0, 160, 90), crop.rect)
    }

    /** 时间表比「拼版数 × 每张格数」还长时，拼版下标要夹在已有图里，不能越界。 */
    @Test
    fun cropFor_tableLongerThanImages_clampsSheetIndex() {
        val table = MutableList(500) { it }
        val crop = VideoShotGrid.cropFor(
            shot(images = listOf("https://x/0.jpg", "https://x/1.jpg"), index = table),
            400_000L
        )!!
        assertEquals("只有两张图，夹到最后一张", 1, crop.sheetIndex)
    }

    @Test
    fun cropFor_unusableShot_returnsNull() {
        assertNull("null 入参", VideoShotGrid.cropFor(null, 1000L))
        assertNull("空时间表", VideoShotGrid.cropFor(shot(index = emptyList()), 1000L))
        assertNull("没图", VideoShotGrid.cropFor(shot(images = emptyList()), 1000L))
    }
}
