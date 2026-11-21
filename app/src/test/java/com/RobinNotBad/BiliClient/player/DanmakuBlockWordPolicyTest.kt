package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G01「弹幕屏蔽词」的纯判据单测。
 *
 * 只钉解析口径与命中口径两件事——真正的过滤接线在 `DanmakuManager`（解析层包装 +
 * 窗口追加入口），那两处靠真机验证，不在单测范围。
 */
class DanmakuBlockWordPolicyTest {

    @Test
    fun `空输入解析成空快照且不拦任何弹幕`() {
        assertTrue(DanmakuBlockWordPolicy.parse(null).isEmpty)
        assertTrue(DanmakuBlockWordPolicy.parse("").isEmpty)
        assertTrue(DanmakuBlockWordPolicy.parse("   ").isEmpty)
        assertTrue(DanmakuBlockWordPolicy.parse("，，、 ；").isEmpty)

        val none = DanmakuBlockWordPolicy.Snapshot.NONE
        assertFalse(none.isBlocked("随便一句话"))
        assertFalse(none.isBlocked(null))
    }

    @Test
    fun `四种分隔符都能切词且连续分隔不产生空词`() {
        val snapshot = DanmakuBlockWordPolicy.parse("前面  后面,中英、顿号;分号；全角 ")
        assertEquals(listOf("前面", "后面", "中英", "顿号", "分号", "全角"), snapshot.words)
    }

    @Test
    fun `重复词去重且保持首次出现的顺序`() {
        val snapshot = DanmakuBlockWordPolicy.parse("b,a,b,a")
        assertEquals(listOf("b", "a"), snapshot.words)
    }

    @Test
    fun `大小写不敏感`() {
        val snapshot = DanmakuBlockWordPolicy.parse("Sc")
        assertEquals(listOf("sc"), snapshot.words)
        assertTrue(snapshot.isBlocked("这集真是 SC 高能"))
        assertTrue(snapshot.isBlocked("sc"))
        // 朴素「包含」语义：更长的英文词里带这个片段同样算命中（与兄弟分支一致，刻意不做分词）
        assertTrue(snapshot.isBlocked("script"))
    }

    @Test
    fun `命中即拦 未命中放行`() {
        val snapshot = DanmakuBlockWordPolicy.parse("剧透,广告")
        assertTrue(snapshot.isBlocked("前面剧透了"))
        assertTrue(snapshot.isBlocked("这是广告"))
        assertFalse(snapshot.isBlocked("真好听"))
        assertFalse(snapshot.isBlocked(""))
        assertFalse(snapshot.isBlocked(null))
    }

    @Test
    fun `单词语超长被截断`() {
        val long = "长".repeat(DanmakuBlockWordPolicy.MAX_WORD_LENGTH + 20)
        val snapshot = DanmakuBlockWordPolicy.parse(long)
        assertEquals(DanmakuBlockWordPolicy.MAX_WORD_LENGTH, snapshot.words.single().length)
        // 截断后仍能命中前缀相同的弹幕
        assertTrue(snapshot.isBlocked(long))
    }

    @Test
    fun `词数超过上限时丢弃尾部`() {
        // 定宽编号：避免「w201」因为包含「w20」被判命中（朴素包含语义的副作用）
        val raw = (1..(DanmakuBlockWordPolicy.MAX_WORDS + 30)).joinToString(",") { "w%04d".format(it) }
        val snapshot = DanmakuBlockWordPolicy.parse(raw)
        assertEquals(DanmakuBlockWordPolicy.MAX_WORDS, snapshot.words.size)
        assertEquals("w0001", snapshot.words.first())
        assertEquals("w${"%04d".format(DanmakuBlockWordPolicy.MAX_WORDS)}", snapshot.words.last())
        assertFalse(snapshot.isBlocked("w${"%04d".format(DanmakuBlockWordPolicy.MAX_WORDS + 1)}"))
    }

    @Test
    fun `回显文本可被再次解析成同一份快照`() {
        val original = DanmakuBlockWordPolicy.parse("剧透 广告,Sc")
        val text = DanmakuBlockWordPolicy.toEditableText(original)
        assertEquals("剧透、广告、sc", text)
        assertEquals(original.words, DanmakuBlockWordPolicy.parse(text).words)
    }
}
