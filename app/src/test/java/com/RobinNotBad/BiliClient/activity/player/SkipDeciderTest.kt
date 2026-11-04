package com.RobinNotBad.BiliClient.activity.player

import com.RobinNotBad.BiliClient.player.ViewPointSkip
import com.RobinNotBad.BiliClient.player.ViewPointSkip.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SkipDecider] 的纯 JVM 单测（反馈 #34「跳过片头片尾会因为视频分段也跳过」）。
 *
 * 钉住的是**片段表/已处理集合的生命周期**——这几条错了不会报错，只会在不该跳的时候跳：
 * 1. 换P/换集必须**立刻**清空旧片段表（不能等新一P 的 view_points 回来再覆盖）；
 * 2. 迟到的拉取响应必须整条丢弃（既不能写片段表，也不能清空已处理集合）；
 * 3. 重复拉到同一批片段时保住"已处理"（26.10.22「乱跳」的成因，别因为重构又退回去）；
 * 4. 每个片段只自动跳一次；用户主动拖进片段同样算已处理。
 */
class SkipDeciderTest {

    private val op = Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0)
    private val ed = Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0)

    private fun opSegments() = ViewPointSkip.buildSegments(listOf(op))
    private fun opEdSegments() = ViewPointSkip.buildSegments(listOf(op, ed))

    // ---------- 换P/换集：立刻清空 ----------

    @Test
    fun 换P后旧片段不能作用于新的一P() {
        val decider = SkipDecider()
        decider.applyLoaded(opSegments())
        // 上一P 的片头还没跳（用户手动拖到 20 秒"看过了"）
        assertEquals(op, decider.decide(20.0))

        decider.reset()

        assertEquals("reset 之后片段表必须为空", emptyList<Segment>(), decider.segments)
        assertNull(
            "新的一P 在第 20 秒时不该再命中上一P 的片头——这正是「因为视频分段也跳过」的现象",
            decider.decide(20.0)
        )
    }

    @Test
    fun 换P后已处理集合也清空() {
        val decider = SkipDecider()
        decider.applyLoaded(opSegments())
        decider.decide(20.0) // 跳过一次，op 进已处理
        decider.reset()
        decider.applyLoaded(opSegments())
        assertEquals(
            "换P 后同一批区间（比如每集片头都是 15~95s）在这一P 里必须还能跳",
            op,
            decider.decide(20.0)
        )
    }

    @Test
    fun reset清空撤回位() {
        val decider = SkipDecider()
        decider.applyLoaded(opSegments())
        val skipped = decider.decide(20.0)!!
        assertTrue(decider.isUndoValid(skipped))
        decider.reset()
        assertNull(decider.lastSkipped)
        assertFalse(decider.isUndoValid(skipped))
    }

    // ---------- 迟到响应隔离 ----------

    @Test
    fun 换P之后旧请求的响应整条丢弃() {
        val decider = SkipDecider()
        val token = decider.beginLoad(1001L, 2002L) // 上一P
        decider.reset() // 用户切到了下一P
        assertTrue(
            "换P 期间上一P 的 view_points 回来时，绝不能把它套到当前这一P 上",
            decider.isStale(token, 1001L, 3003L)
        )
        assertTrue(decider.isStale(token, 1001L, 2002L))
    }

    @Test
    fun 又发起一次拉取时前一次响应过期() {
        val decider = SkipDecider()
        val first = decider.beginLoad(1001L, 2002L)
        val second = decider.beginLoad(1001L, 2002L)
        assertTrue("同内容重拉也要让旧代次作废，否则两份响应会互相覆盖", decider.isStale(first, 1001L, 2002L))
        assertFalse(decider.isStale(second, 1001L, 2002L))
    }

    @Test
    fun 当前内容的响应不算过期() {
        val decider = SkipDecider()
        val token = decider.beginLoad(1001L, 2002L)
        assertFalse(decider.isStale(token, 1001L, 2002L))
        // aid 变了（连播进了另一个稿件）也算过期
        assertTrue(decider.isStale(token, 1002L, 2002L))
    }

    // ---------- applyLoaded：重复拉取不得重置已处理 ----------

    @Test
    fun 重复拉到同一批片段不清空已处理() {
        val decider = SkipDecider()
        decider.applyLoaded(opSegments())
        decider.decide(20.0) // 片头进已处理
        decider.applyLoaded(opSegments()) // 迟到的重复响应
        assertNull(
            "同一次播放里重复拉取不能清空已处理，否则片头会被再跳一次",
            decider.decide(30.0)
        )
    }

    @Test
    fun 片段内容变了要清空已处理() {
        val decider = SkipDecider()
        decider.applyLoaded(opSegments())
        decider.decide(20.0)
        decider.applyLoaded(opEdSegments()) // 换集了，多出片尾
        assertEquals("换集后新的片段表要生效", opEdSegments(), decider.segments)
        assertEquals("换集后片头还能再跳一次", op, decider.decide(20.0))
    }

    @Test
    fun 上游失败返回空表时不留下脏片段() {
        val decider = SkipDecider()
        decider.applyLoaded(opSegments())
        decider.applyLoaded(emptyList())
        assertEquals(emptyList<Segment>(), decider.segments)
        assertNull(decider.decide(20.0))
    }

    // ---------- 判定：每个片段只跳一次 ----------

    @Test
    fun 每个片段只自动跳一次() {
        val decider = SkipDecider()
        decider.applyLoaded(opSegments())
        assertEquals("首次进入片头应当触发跳过", op, decider.decide(20.0))
        assertNull("跳过一次后不能再跳，否则「撤回」就失效了", decider.decide(30.0))
    }

    @Test
    fun 片头与片尾各自只跳一次() {
        val decider = SkipDecider()
        decider.applyLoaded(opEdSegments())
        assertEquals(op, decider.decide(20.0))
        assertNull(decider.decide(30.0))
        assertEquals("片尾是另一个片段，不该被片头的已处理吃掉", ed, decider.decide(1400.0))
        assertNull(decider.decide(1410.0))
    }

    @Test
    fun 区间外不跳() {
        val decider = SkipDecider()
        decider.applyLoaded(opSegments())
        assertNull(decider.decide(0.0))
        assertNull("片头终点是右开区间，落在终点上不算在片段内", decider.decide(95.0))
        assertNull(decider.decide(600.0))
    }

    // ---------- 用户反悔 ----------

    @Test
    fun 用户主动拖进片头算已处理() {
        val decider = SkipDecider()
        decider.applyLoaded(opSegments())
        assertTrue(decider.onUserSeek(30_000L))
        assertNull("用户主动看片头，下一轮定时器不能再把他弹走", decider.decide(31.0))
    }

    @Test
    fun 用户拖到片段外不需要标记() {
        val decider = SkipDecider()
        decider.applyLoaded(opSegments())
        assertFalse(decider.onUserSeek(300_000L))
        assertEquals("没跳进片段，片头仍然应该被自动跳过", op, decider.decide(20.0))
    }

    @Test
    fun 用户拖进别的片段后撤回位失效() {
        val decider = SkipDecider()
        decider.applyLoaded(opEdSegments())
        val skipped = decider.decide(20.0)!!
        assertTrue(decider.isUndoValid(skipped))
        // 用户自己拖到片尾（另一个还没处理过的片段）：那条「撤回」就过期了，别把人拽回片头
        assertTrue(decider.onUserSeek(1_400_000L))
        assertFalse(decider.isUndoValid(skipped))
    }

    @Test
    fun 撤回后片段仍算已处理() {
        val decider = SkipDecider()
        decider.applyLoaded(opSegments())
        val skipped = decider.decide(20.0)!!
        decider.clearUndo()
        assertFalse(decider.isUndoValid(skipped))
        assertNull(
            "撤回只是把进度拉回片段起点，不能又触发一次自动跳过",
            decider.decide(16.0)
        )
    }

    @Test
    fun 未加载片段时一切不生效() {
        val decider = SkipDecider()
        assertNull(decider.decide(20.0))
        assertFalse(decider.onUserSeek(20_000L))
        assertEquals(emptyList<Segment>(), decider.segments)
    }
}
