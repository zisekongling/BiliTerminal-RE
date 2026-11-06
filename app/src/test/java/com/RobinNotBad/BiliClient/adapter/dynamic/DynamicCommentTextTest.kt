package com.RobinNotBad.BiliClient.adapter.dynamic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [DynamicCommentText.build] 的 JVM 单测（task-8，来源 release 日志复查 D2）。
 *
 * 锁住两件事：
 * 1. **0 条时不显示 "0"**：显示「评论」标签（与 DynamicHolder 里点赞数为 0 时不显示假数据的口径一致）；
 * 2. **大数复用 StringUtil.toWan 的 万/亿 分档**：与同一行的点赞数同格式，
 *    避免同一条动态出现「赞 1.2万」旁边写「评论 12345」。
 */
class DynamicCommentTextTest {

    private val label = "评论"

    @Test
    fun zeroComments_showsLabelInsteadOfZero() {
        assertEquals(label, DynamicCommentText.build(0L, label))
    }

    @Test
    fun negativeCount_showsLabel() {
        // 服务端理论上不会给负数；万一给了也不该渲染 "-5"
        assertEquals(label, DynamicCommentText.build(-1L, label))
    }

    @Test
    fun belowWan_keepsRawNumber() {
        assertEquals("1", DynamicCommentText.build(1L, label))
        assertEquals("9999", DynamicCommentText.build(9999L, label))
    }

    @Test
    fun wanBoundary_usesWanSuffix() {
        assertEquals("1.0万", DynamicCommentText.build(10000L, label))
        assertEquals("1.2万", DynamicCommentText.build(12345L, label))
    }

    @Test
    fun yiBoundary_usesYiSuffix() {
        assertEquals("1.0亿", DynamicCommentText.build(100000000L, label))
        assertEquals("1.2亿", DynamicCommentText.build(123456789L, label))
    }
}
