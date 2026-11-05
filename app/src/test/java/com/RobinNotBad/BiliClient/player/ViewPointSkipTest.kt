package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.player.ViewPointSkip.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 注（26.11.05，#99 修复口径「一道闸」）：本文件的用例一律用**三参 Segment**（无 content）造片头片尾，
// 这是有意的——[ViewPointSkip.buildSegments] 的单参版本是**旧口径**：只看 type + 区间 + 时长上限，
// **不看 content**，所以空 content 的段照样保留，这些用例全绿。
//
// 真正拦反馈 #99（UGC 的 view_points 是普通分段却被当片尾）的闸**不在 ViewPointSkip**，而在
// `PlayerActivity.isPgcPlayback()`：判定为 UGC 时传 `allowAutoSkip = false` 拿到空表。
// 本文件只测「调用方给了结论之后怎么执行」，闸本身与 #99 样本见 [ViewPointSkipChapterTest]。

/**
 * [ViewPointSkip] 的纯 JVM 单测。
 *
 * 钉住的是三条真实会翻车的边界：
 * 1. 区间必须**左闭右开**——跳过之后位置正好落在 `toSec`，右闭会让定时器每轮都再跳一次；
 * 2. 每个片段**只跳一次**——否则用户点了「撤回」往回拖，下一轮又会被弹走；
 * 3. 用户**手动**拖进片段内要算「已处理」——他主动看片头，不该被自动逻辑打断。
 */
class ViewPointSkipTest {

    private val op = Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0)
    private val ed = Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0)

    @Test
    fun onlyOpeningAndEndingAreSkippable() {
        assertTrue("片头(type=1)必须可跳", ViewPointSkip.isSkippable(ViewPointSkip.TYPE_OP))
        assertTrue("片尾(type=2)必须可跳", ViewPointSkip.isSkippable(ViewPointSkip.TYPE_ED))
        // type=0 是普通章节看点（用户点着看的），type=3 是预留值，都不能自动跳
        assertFalse("普通看点不是片头片尾，不能自动跳", ViewPointSkip.isSkippable(0))
        assertFalse(ViewPointSkip.isSkippable(3))
        assertFalse(ViewPointSkip.isSkippable(-1))
    }

    @Test
    fun buildSegments_keepsOnlySkippableValidOnes() {
        val raw = listOf(
            Segment(0, 100.0, 200.0),   // 普通看点
            Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0),
            Segment(ViewPointSkip.TYPE_ED, 95.0, 95.0),   // 空区间
            Segment(ViewPointSkip.TYPE_ED, 200.0, 100.0), // 反向区间
            Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0)
        )
        assertEquals(
            "只剩两条合法片头片尾，脏区间必须被丢掉",
            listOf(op, ed),
            ViewPointSkip.buildSegments(raw)
        )
    }

    @Test
    fun buildSegments_sortsByStart() {
        val late = Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0)
        val early = Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0)
        assertEquals(
            "必须按起点排序，segmentAt 依赖「第一个命中」的语义",
            listOf(early, late),
            ViewPointSkip.buildSegments(listOf(late, early))
        )
    }

    @Test
    fun buildSegments_dropsAbsurdlyLongSegment() {
        // 上游一旦下发「0 秒 → 整集」，自动跳过会把用户直接甩到片尾，这是兜底
        val wholeEpisode = Segment(ViewPointSkip.TYPE_OP, 0.0, 1440.0)
        assertTrue(
            "整集长度的片段必须被当成脏数据丢掉",
            ViewPointSkip.buildSegments(listOf(wholeEpisode)).isEmpty()
        )
        // 边界：刚好等于上限仍保留
        val atLimit = Segment(ViewPointSkip.TYPE_OP, 0.0, ViewPointSkip.MAX_SEGMENT_SECONDS)
        assertEquals(1, ViewPointSkip.buildSegments(listOf(atLimit)).size)
    }

    @Test
    fun segmentAt_isLeftClosedRightOpen() {
        val segments = ViewPointSkip.buildSegments(listOf(op))
        assertNull("还没到片头时不能命中", ViewPointSkip.segmentAt(14.9, segments))
        assertEquals("起点必须算在片段内", op, ViewPointSkip.segmentAt(15.0, segments))
        assertEquals("区间中间命中", op, ViewPointSkip.segmentAt(50.0, segments))
        assertNull("终点必须不算在片段内", ViewPointSkip.segmentAt(95.0, segments))
        assertNull("越过终点后不再命中", ViewPointSkip.segmentAt(95.1, segments))
    }

    @Test
    fun segmentAt_ignoresUntrustedPosition() {
        val segments = ViewPointSkip.buildSegments(listOf(op))
        // 播放器未就绪/重建窗口期可能给出 -1；NaN 来自 0/0 的时长换算
        assertNull(ViewPointSkip.segmentAt(-1.0, segments))
        assertNull(ViewPointSkip.segmentAt(Double.NaN, segments))
    }

    @Test
    fun shouldSkip_returnsSegmentOnceThenStaysQuiet() {
        val segments = ViewPointSkip.buildSegments(listOf(op))
        val first = ViewPointSkip.shouldSkip(20.0, segments, emptySet())
        assertEquals("首次进入片头应当触发跳过", op, first)

        val handled = setOf(ViewPointSkip.keyOf(first!!))
        assertNull("已经跳过一次后不能再跳，否则撤回就失效了", ViewPointSkip.shouldSkip(30.0, segments, handled))
    }

    @Test
    fun keyForManualSeek_marksSegmentUserDraggedInto() {
        val segments = ViewPointSkip.buildSegments(listOf(op))
        assertEquals(
            "用户手动拖进片头，必须标记为已处理，否则下一轮定时器又把他弹走",
            ViewPointSkip.keyOf(op),
            ViewPointSkip.keyForManualSeek(30.0, segments, emptySet())
        )
        assertNull("没拖进任何片段时不需要标记", ViewPointSkip.keyForManualSeek(300.0, segments, emptySet()))
        assertNull(
            "已经处理过的片段不必重复标记",
            ViewPointSkip.keyForManualSeek(30.0, segments, setOf(ViewPointSkip.keyOf(op)))
        )
    }

    @Test
    fun keyOf_distinguishesTypesAndRanges() {
        val opAgain = Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0)
        val edSameRange = Segment(ViewPointSkip.TYPE_ED, 15.0, 95.0)
        assertEquals("同一片段必须得到同一个 key", ViewPointSkip.keyOf(op), ViewPointSkip.keyOf(opAgain))
        assertFalse(
            "片头和片尾哪怕区间相同也不能共用 key",
            ViewPointSkip.keyOf(op) == ViewPointSkip.keyOf(edSameRange)
        )
    }

    // ================= #99 修复后新增：content 不参与过滤 + UGC 闸 =================

    @Test
    fun buildSegments_singleArgIgnoresContentEntirely() {
        // 用带 content 的同一批数据复钉一遍区间校验：content 写什么都无所谓，
        // 结果只由 type / 区间 / 时长决定——「区间校验」这一层不许被别的东西带偏。
        val raw = listOf(
            Segment(0, 100.0, 200.0, "片头"),                     // 类型不对，写「片头」也没用
            Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "片头"),
            Segment(ViewPointSkip.TYPE_ED, 95.0, 95.0, "片尾"),   // 空区间
            Segment(ViewPointSkip.TYPE_ED, 200.0, 100.0, "片尾"), // 反向区间
            Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0, "片尾")
        )
        assertEquals(
            "content 只是随段携带的数据，筛选结果与它无关",
            listOf(
                Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "片头"),
                Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0, "片尾")
            ),
            ViewPointSkip.buildSegments(raw)
        )
    }

    @Test
    fun 空content的片头片尾必须保留() {
        // 三参构造（content 默认空串）不是"语义闸的后门"，而是**真实数据形态**：
        // 番剧 EP 的 view_points 里 content 常常是空串、「1-1」「正片」。
        // 如果哪天有人把 isOpEdLabel 白名单接回 buildSegments 当闸，这条会立刻红——
        // 那时真片头片尾会被挡掉，用户反而觉得"开了没用"。别接回来。
        val raw = listOf(
            Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0),
            Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0)
        )
        assertEquals(
            "content 为空不影响取样：只要 type 对、区间合法就必须保留 2 条",
            2,
            ViewPointSkip.buildSegments(raw).size
        )
    }

    @Test
    fun 纯空白content的段也保留() {
        // 同上：空白标题也只是"没给标题"，不是"不是片头片尾"的证据
        val raw = listOf(Segment(ViewPointSkip.TYPE_ED, 0.0, 7.0, "   \t\u3000"))
        assertEquals(
            "空白 content 同样不参与过滤",
            1,
            ViewPointSkip.buildSegments(raw).size
        )
        assertTrue(
            "唯一的闸在 allowAutoSkip：调用方判定是 UGC 时一律拿空表",
            ViewPointSkip.buildSegments(raw, allowAutoSkip = false).isEmpty()
        )
    }

    @Test
    fun allowAutoSkip为false时无论内容如何都是空表() {
        val raw = listOf(
            Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "片头"),
            Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0, "片尾")
        )
        assertTrue(
            "这是 #99 真正生效的那道闸（UGC 由 PlayerActivity.isPgcPlayback() 判定后传入 false）",
            ViewPointSkip.buildSegments(raw, allowAutoSkip = false).isEmpty()
        )
        assertTrue(
            "allowAutoSkip=true 时与单参版本完全一致",
            ViewPointSkip.buildSegments(raw, allowAutoSkip = true) == ViewPointSkip.buildSegments(raw)
        )
    }
}
