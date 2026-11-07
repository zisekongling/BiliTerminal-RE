package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 连播队列纯逻辑的单测：顺序/倒序/随机/单曲循环、历史栈、token 编解码。
 *
 * 这些规则直接决定「看完一集自动播哪一集」和「耳机上一首/下一首落到哪」，
 * 真机上很难把四种模式 × 边界都点一遍，所以在这里钉死。
 */
class PlayQueueTest {

    private fun items(vararg titles: String): List<PlayQueueItem> =
        titles.mapIndexed { index, title -> PlayQueueItem(100L + index, 0L, title) }

    /** 一路「自动连播」到底，收集途经的下标（不含起点）。 */
    private fun drain(queue: PlayQueue): List<Int> {
        val visited = ArrayList<Int>()
        var step = queue.advance(true)
        while (step != null) {
            visited.add(step.index)
            step = queue.advance(true)
        }
        return visited
    }

    @Test
    fun `顺序模式逐项前进并在末尾停住`() {
        val queue = PlayQueue.create(items("P1", "P2", "P3"), 0, PlayQueueMode.SEQUENTIAL)
        assertEquals(1, queue.nextIndex(auto = true))
        assertEquals(PlayQueueMode.SEQUENTIAL, queue.mode)
        assertEquals(1, queue.advance(true)?.index)
        assertEquals(2, queue.advance(true)?.index)
        assertNull(queue.advance(true))
        assertFalse(queue.canNext(true))
    }

    @Test
    fun `倒序模式从后往前`() {
        val queue = PlayQueue.create(items("P1", "P2", "P3"), 2, PlayQueueMode.REVERSE)
        assertEquals(1, queue.nextIndex(auto = true))
        queue.advance(true)
        assertEquals(1, queue.currentIndex)
        queue.advance(true)
        assertEquals(0, queue.currentIndex)
        assertNull(queue.advance(true))
    }

    @Test
    fun `单曲循环自动停在当前项但手动切歌往后走`() {
        val queue = PlayQueue.create(items("P1", "P2"), 0, PlayQueueMode.REPEAT_ONE)
        val auto = queue.advance(true)
        assertNotNull(auto)
        assertTrue(auto!!.replayCurrent)
        assertEquals(0, queue.currentIndex)

        val manual = queue.advance(false)
        assertNotNull(manual)
        assertFalse(manual!!.replayCurrent)
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `随机模式同一 seed 顺序稳定且覆盖全部项`() {
        val a = PlayQueue.create(items("a", "b", "c", "d", "e"), 0, PlayQueueMode.SHUFFLE, seed = 42L)
        val b = PlayQueue.create(items("a", "b", "c", "d", "e"), 0, PlayQueueMode.SHUFFLE, seed = 42L)
        val visitedA = ArrayList<Int>()
        var step = a.advance(true)
        while (step != null) {
            visitedA.add(step.index)
            step = a.advance(true)
        }
        val visitedB = ArrayList<Int>()
        var stepB = b.advance(true)
        while (stepB != null) {
            visitedB.add(stepB.index)
            stepB = b.advance(true)
        }
        assertEquals(visitedA, visitedB)
        assertEquals(4, visitedA.size)
        assertEquals(listOf(0, 1, 2, 3, 4).toSet(), (visitedA + 0).toSet())
    }

    @Test
    fun `随机模式在 8 项队列上不等于顺序模式`() {
        // 上面那条用例的三条断言（同 seed 稳定 / 覆盖全部项 / 长度为 n）在「shuffle 退化成不洗牌」
        // 时**同样全绿**——那三条只证明「是个排列」，不证明「真的打乱了」。
        // 所以这里补上只有真随机才成立的口径：随机序必须与顺序序不同，且随机序要随 seed 变化。
        // n 取 8：排列数 8! = 40320，真洗牌恰好洗回顺序的概率可以忽略；
        // 断言写成「有一个 seed 与顺序不同」而不是「每个 seed 都不同」，是为了不依赖某个具体
        // seed 恰好洗出非顺序排列（那会变成一条固定失败的用例）。
        val titles = (1..8).map { "P" + it }
        val sequential = drain(PlayQueue.create(items(*titles.toTypedArray()), 0, PlayQueueMode.SEQUENTIAL))
        assertEquals("顺序模式从 0 开始应逐项前进", (1..7).toList(), sequential)

        val seeds = listOf(1L, 7L, 42L, 20260315L)
        val shuffled = seeds.map { seed ->
            drain(PlayQueue.create(items(*titles.toTypedArray()), 0, PlayQueueMode.SHUFFLE, seed = seed))
        }
        shuffled.forEachIndexed { i, visited ->
            assertEquals(
                "随机模式必须覆盖全部 ${titles.size} 项（seed=${seeds[i]}）",
                (0..7).toSet(),
                (visited + 0).toSet()
            )
            assertEquals("随机模式不能少项（seed=${seeds[i]}）", 7, visited.size)
        }
        assertTrue(
            "随机序必须与顺序序不同，否则 shuffle 等于没打乱：$shuffled",
            shuffled.any { it != sequential }
        )
        assertTrue(
            "随机序必须随 seed 变化，否则 seed 被忽略、每次都放同一个顺序：$shuffled",
            shuffled.toSet().size > 1
        )
        // 同 seed 下确定性成立：再建一次队列，结果必须与上面完全相同
        assertEquals(
            shuffled[2],
            drain(PlayQueue.create(items(*titles.toTypedArray()), 0, PlayQueueMode.SHUFFLE, seed = seeds[2]))
        )
    }

    @Test
    fun `随机模式历史栈支持原路返回`() {
        val queue = PlayQueue.create(items("a", "b", "c", "d"), 1, PlayQueueMode.SHUFFLE, seed = 7L)
        val first = queue.advance(true)
        assertNotNull(first)
        assertEquals(1, queue.previousIndex())
        val back = queue.retreat()
        assertNotNull(back)
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `手动选集走历史栈且越界返回空`() {
        val queue = PlayQueue.create(items("P1", "P2", "P3"), 0)
        assertEquals("P3", queue.select(2)?.title)
        assertEquals(2, queue.currentIndex)
        assertNull(queue.select(3))
        val prev = queue.retreat()
        assertNotNull(prev)
        assertEquals(0, prev!!.index)
    }

    @Test
    fun `模式循环顺序固定`() {
        assertEquals(PlayQueueMode.REVERSE, PlayQueueMode.SEQUENTIAL.cycle())
        assertEquals(PlayQueueMode.SHUFFLE, PlayQueueMode.REVERSE.cycle())
        assertEquals(PlayQueueMode.REPEAT_ONE, PlayQueueMode.SHUFFLE.cycle())
        assertEquals(PlayQueueMode.SEQUENTIAL, PlayQueueMode.REPEAT_ONE.cycle())
    }

    @Test
    fun `token 编解码保留来源模式下标与标题特殊字符`() {
        val queue = PlayQueue.create(
            listOf(
                PlayQueueItem(11L, 22L, "第 1 集\t带制表符"),
                PlayQueueItem(33L, 44L, "第 2 集\\反斜杠\n换行")
            ),
            currentIndex = 1,
            mode = PlayQueueMode.SHUFFLE,
            source = PlayQueueSource.COLLECTION,
            seed = 123456789L
        )
        val decoded = PlayQueue.decode(queue.encode())
        assertNotNull(decoded)
        assertEquals(2, decoded!!.size)
        assertEquals(1, decoded.currentIndex)
        assertEquals(PlayQueueMode.SHUFFLE, decoded.mode)
        assertEquals(PlayQueueSource.COLLECTION, decoded.source)
        assertEquals(123456789L, decoded.seed)
        assertEquals("第 1 集\t带制表符", decoded.itemAt(0)!!.title)
        assertEquals("第 2 集\\反斜杠\n换行", decoded.itemAt(1)!!.title)
        assertEquals(33L, decoded.current()!!.aid)
    }

    @Test
    fun `非法 token 一律返回空而不抛异常`() {
        assertNull(PlayQueue.decode(null))
        assertNull(PlayQueue.decode(""))
        assertNull(PlayQueue.decode("garbage"))
        assertNull(PlayQueue.decode("PLAYQUEUE1\tx"))
        assertNull(PlayQueue.decode("PLAYQUEUE1\tsingle\tsequential\t1\t0\t\nbad\tline"))
        assertNull(PlayQueue.decode("PLAYQUEUE2\tsingle\tsequential\t1\t0\t"))
    }

    @Test
    fun `空队列不崩且没有下一步`() {
        val queue = PlayQueue.create(emptyList())
        assertTrue(queue.isEmpty())
        assertNull(queue.advance(true))
        assertNull(queue.retreat())
        assertFalse(queue.canNext(true))
    }

    @Test
    fun `reset 换层后清空历史并夹紧下标`() {
        val queue = PlayQueue.create(items("a", "b", "c"), 2)
        queue.reset(items("x", "y"), 5)
        assertEquals(2, queue.size)
        assertEquals(1, queue.currentIndex)
        // 历史被清空后，上一首按顺序模式回落为「列表里的前一项」
        assertTrue(queue.canPrevious())
        assertEquals(0, queue.previousIndex())
    }
}
