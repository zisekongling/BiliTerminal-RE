package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 弹幕窗口区间的纯逻辑单测（批次 2）。
 *
 * 这些断言之所以重要：窗口算错的表现是**静默的** —— 少一段会让整个 6 分钟没有弹幕，
 * 多一段会白发一次带 WBI 签名的请求，段号越界则服务端直接回空包。
 * 所以边界（0 段、末段、负数位置、半径 0、半径越界）全部在这里钉死。
 */
class DanmakuWindowTest {

    // ------------------------------------------------------------ segmentIndexAt

    @Test
    fun `分段号从 1 开始且按 360 秒切分`() {
        assertEquals("0ms 属于第 1 段", 1, DanmakuWindow.segmentIndexAt(0L))
        assertEquals("第 1 段内任意位置都还是第 1 段", 1, DanmakuWindow.segmentIndexAt(359_999L))
        assertEquals("正好 360000ms 进入第 2 段（区间左闭右开）", 2, DanmakuWindow.segmentIndexAt(360_000L))
        assertEquals("还是第 2 段", 2, DanmakuWindow.segmentIndexAt(719_999L))
        assertEquals("720000ms 进入第 3 段", 3, DanmakuWindow.segmentIndexAt(720_000L))
    }

    @Test
    fun `负数位置按第 1 段处理而不是算出越界段号`() {
        // 播放器在 onPrepared 之前可能拿到 -1 之类的哨兵值；这里若不钳住会算出 0 段，
        // 而 segment_index=0 在服务端是无效值（请求白费）。
        assertEquals(1, DanmakuWindow.segmentIndexAt(-1L))
        assertEquals(1, DanmakuWindow.segmentIndexAt(Long.MIN_VALUE / 2))
    }

    @Test
    fun `分段时长可配且非法值回落 360 秒`() {
        assertEquals("60 秒分段：59999ms 仍是第 1 段", 1, DanmakuWindow.segmentIndexAt(59_999L, 60))
        assertEquals("60 秒分段：60000ms 是第 2 段", 2, DanmakuWindow.segmentIndexAt(60_000L, 60))
        assertEquals("非法分段时长（<=0）回落到默认 360 秒", 1, DanmakuWindow.segmentIndexAt(1_000L, 0))
    }

    // ------------------------------------------------------------ rangeFor

    @Test
    fun `开头窗口不会请求第 0 段`() {
        // 位置在视频开头时，中心段 1 的前 2 段并不存在，必须裁到 1。
        assertEquals(1..3, DanmakuWindow.rangeFor(0L, 2, 10))
    }

    @Test
    fun `中间窗口是前后各 radius 段`() {
        assertEquals("第 5 段（从 0 数第 4 段）居中时窗口 3..7", 3..7, DanmakuWindow.rangeFor(360_000L * 4, 2, 10))
    }

    @Test
    fun `结尾窗口不会请求超出总段数的段`() {
        // 总段数 10、中心段 10（位置 9*360s）⇒ 尾部裁到 10。
        assertEquals(8..10, DanmakuWindow.rangeFor(360_000L * 9, 2, 10))
    }

    @Test
    fun `单段视频与未知时长的窗口只有一个段`() {
        assertEquals(1..1, DanmakuWindow.rangeFor(0L, 2, 1))
        assertEquals("总段数 <=0 按 1 段处理（时长未知）", 1..1, DanmakuWindow.rangeFor(0L, 2, 0))
        assertEquals(1..1, DanmakuWindow.rangeFor(0L, 2, -3))
    }

    @Test
    fun `半径 0 只包含当前段而负半径按 0 处理`() {
        assertEquals(5..5, DanmakuWindow.rangeFor(360_000L * 4, 0, 10))
        assertEquals("负半径不应让 from > to（会得到空区间、永远缺段）", 5..5, DanmakuWindow.rangeFor(360_000L * 4, -2, 10))
    }

    @Test
    fun `位置超出总时长时钳到最后一段`() {
        // 进度回调偶尔会比真实时长多报一点（尤其直播转点播 / 拖动越界），不能因此算出第 100 段。
        assertEquals(1..3, DanmakuWindow.rangeFor(360_000L * 100, 2, 3))
    }

    @Test
    fun `窗口随位置推进而移动`() {
        val total = 20
        val a = DanmakuWindow.rangeFor(0L, 2, total)
        val b = DanmakuWindow.rangeFor(360_000L * 6, 2, total)
        assertEquals(1..3, a)
        assertEquals(5..9, b)
        assertTrue("窗口中心必须随播放位置前进", b.first > a.first)
    }

    // ------------------------------------------------------------ missing

    @Test
    fun `missing 只返回窗口内尚未加载的段`() {
        assertEquals(listOf(1, 2, 3), DanmakuWindow.missing(emptySet(), 1..3))
        assertEquals(listOf(2), DanmakuWindow.missing(setOf(1, 3), 1..3))
        assertTrue(DanmakuWindow.missing(setOf(1, 2, 3), 1..3).isEmpty())
        assertTrue("窗口外的已加载段不影响结果", DanmakuWindow.missing(setOf(1, 2, 3, 99), 1..3).isEmpty())
    }

    // ------------------------------------------------------------ needsTimelineRebuild

    @Test
    fun `同段内的小幅跳转只增量补窗口`() {
        // 双击快进 10 秒、拖一点点：旧窗口的段还有效，重建会白闪一下。
        assertFalse(DanmakuWindow.needsTimelineRebuild(0L, 30_000L))
        assertFalse(DanmakuWindow.needsTimelineRebuild(350_000L, 359_999L))
    }

    @Test
    fun `跨段的跳转必须重建时间轴`() {
        assertTrue("359999ms → 360000ms 已进第 2 段", DanmakuWindow.needsTimelineRebuild(359_999L, 360_000L))
        assertTrue("片头拖到片尾", DanmakuWindow.needsTimelineRebuild(0L, 1_800_000L))
        assertTrue("往回拖同样重建", DanmakuWindow.needsTimelineRebuild(1_800_000L, 0L))
    }

    @Test
    fun `分段时长可配且未知的前位置按第 1 段处理`() {
        assertFalse("负值按 0 处理，不会误判成跨段", DanmakuWindow.needsTimelineRebuild(-1L, 10_000L))
        assertTrue(DanmakuWindow.needsTimelineRebuild(0L, 120_000L, 60))
        assertFalse(DanmakuWindow.needsTimelineRebuild(0L, 59_999L, 60))
    }

    @Test
    fun `常量与 DanmakuApi 的契约一致`() {
        // DanmakuWindow 故意不依赖 api 层（保持纯逻辑可单测），所以这里手工钉住两边的一致性。
        assertEquals("窗口默认分段时长必须与 DanmakuApi.SEGMENT_SECONDS 相同", 360, DanmakuWindow.SEGMENT_SECONDS)
        assertEquals(360, DanmakuStreamLoader.DEFAULT_SEGMENT_SECONDS)
        assertEquals("默认前后各 2 段", 2, DanmakuStreamLoader.DEFAULT_WINDOW_RADIUS)
    }
}
