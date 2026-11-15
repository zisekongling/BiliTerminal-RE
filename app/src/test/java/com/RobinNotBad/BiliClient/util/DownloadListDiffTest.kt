package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.DownloadSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载列表「换表之后该发哪种通知」的纯逻辑单测（[DownloadListDiff]）。
 *
 * 背景：下载列表页的 adapter 是同一个实例复用到底的（Timer 每 400ms 要靠它在原实例上逐条
 * `notifyItemChanged` 推进度），所以每次刷新都得判断「这次只是某几行状态变了，还是条目被
 * 增删/换了顺序」——判错的后果是按下标通知一个搬走了的位置，或者该整表重绑却只通知了个别行
 * （线上 26.11.09.02 那条 `Inconsistency detected. Invalid view holder adapter position` 就是
 * 数据与通知没在同一个主线程 turn 里对上号的同源问题）。
 *
 * 这套判定只读 [DownloadSection] 的字段、不碰 Android，直接在 JVM 上跑，不需要 Robolectric。
 */
class DownloadListDiffTest {

    /** 造一条下载记录：只填本页列表行会读的字段，其余留默认值。 */
    private fun section(
        id: Long,
        state: String = "none",
        name: String = "视频$id",
        cover: String = "cover$id",
        failReason: String = ""
    ): DownloadSection = DownloadSection().apply {
        this.id = id
        this.state = state
        this.name_short = name
        this.url_cover = cover
        this.failReason = failReason
    }

    private fun list(vararg sections: DownloadSection): List<DownloadSection> = sections.toList()

    // ==================== 结构判定 ====================

    @Test
    fun `条数不变_位置与id都不变_结构相同`() {
        val before = list(section(1), section(2), section(3))
        val after = list(section(1), section(2), section(3))
        assertTrue(DownloadListDiff.sameStructure(before, after))
    }

    @Test
    fun `条数变了就算结构变了`() {
        assertFalse(DownloadListDiff.sameStructure(list(section(1)), list(section(1), section(2))))
        assertFalse(DownloadListDiff.sameStructure(list(section(1), section(2)), list(section(1))))
        // 一条都没有 → 冒出一条：空态那一行占位也要整表重绑
        assertFalse(DownloadListDiff.sameStructure(emptyList(), list(section(1))))
    }

    @Test
    fun `条数相同但顺序变了算结构变了`() {
        val before = list(section(1), section(2))
        val after = list(section(2), section(1))
        assertFalse(DownloadListDiff.sameStructure(before, after))
    }

    @Test
    fun `条数相同但换了任务算结构变了`() {
        val before = list(section(1), section(2))
        val after = list(section(1), section(3))
        assertFalse(DownloadListDiff.sameStructure(before, after))
    }

    @Test
    fun `两次空列表_结构相同_不需要发任何通知`() {
        // DownloadAdapter.getItemCount 在空表时也返回 1（占位提示行），但两次空表之间结构没动
        assertTrue(DownloadListDiff.sameStructure(emptyList(), emptyList()))
        assertTrue(DownloadListDiff.changedPositions(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `只有状态变了_结构仍然相同`() {
        val before = list(section(1, state = "downloading"), section(2, state = "paused"))
        val after = list(section(1, state = "done"), section(2, state = "downloading"))
        assertTrue(DownloadListDiff.sameStructure(before, after))
    }

    // ==================== 变化位置 ====================

    @Test
    fun `内容完全一样时没有任何变化位置`() {
        val before = list(section(1), section(2))
        val after = list(section(1), section(2))
        assertEquals(emptyList<Int>(), DownloadListDiff.changedPositions(before, after))
    }

    @Test
    fun `按位置比字段而不是比引用_重新取数得到的新对象依然算没变化`() {
        // DownloadService.getAll() 每次都从库里 new 一批对象，引用必然不同；
        // 若按引用比较，每次刷新都会整屏重绑。
        val before = list(section(1), section(2))
        val after = list(section(1), section(2))
        assertFalse(before[0] === after[0])
        assertEquals(emptyList<Int>(), DownloadListDiff.changedPositions(before, after))
    }

    @Test
    fun `只有状态变化的那一行被点名`() {
        val before = list(section(1, state = "none"), section(2, state = "none"), section(3, state = "none"))
        val after = list(section(1, state = "none"), section(2, state = "downloading"), section(3, state = "none"))
        assertEquals(listOf(1), DownloadListDiff.changedPositions(before, after))
    }

    @Test
    fun `多行同时变化时按位置升序给出`() {
        val before = list(section(1, state = "none"), section(2, state = "none"), section(3, state = "none"))
        val after = list(section(1, state = "none"), section(2, state = "paused"), section(3, state = "error"))
        assertEquals(listOf(1, 2), DownloadListDiff.changedPositions(before, after))
    }

    @Test
    fun `标题_封面_失败原因变化都算需要重绑`() {
        val before = list(section(1, name = "老标题"), section(2, cover = "c1"), section(3, failReason = ""))
        val after = list(section(1, name = "新标题"), section(2, cover = "c2"), section(3, failReason = "服务器返回 HTTP 403"))
        assertEquals(listOf(0, 1, 2), DownloadListDiff.changedPositions(before, after))
    }

    @Test
    fun `结构变了时不再给逐条位置_交给整表重绑`() {
        val before = list(section(1, state = "none"), section(2, state = "none"))
        val after = list(section(2, state = "done"), section(1, state = "none"), section(3))
        // 结构一变，下标本身就换了含义：逐个通知覆盖不到增删，返回空表让调用方 notifyDataSetChanged
        assertEquals(emptyList<Int>(), DownloadListDiff.changedPositions(before, after))
    }
}
