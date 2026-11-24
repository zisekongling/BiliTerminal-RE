package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 评论正文点击去向（反馈 #236「楼中楼进不去 / 点了没反应 / 叠层」）。
 *
 * 三种结果对应三处缺口：列表页点进楼层页、楼层页里点没有子回复的给提示、
 * 同一条评论的楼层页不叠第二层。
 */
class ReplyTapPolicyTest {

    @Test
    fun 列表页点有楼中楼的评论进楼层页() {
        assertEquals(
            ReplyTapPolicy.TapResult.OPEN_FLOOR,
            ReplyTapPolicy.forBodyTap(childCount = 3, inDetailPage = false, sameRpid = false)
        )
    }

    @Test
    fun 列表页点没有子回复的评论仍进评论详情页() {
        assertEquals(
            ReplyTapPolicy.TapResult.OPEN_FLOOR,
            ReplyTapPolicy.forBodyTap(childCount = 0, inDetailPage = false, sameRpid = false)
        )
    }

    @Test
    fun 楼层页里点有楼中楼的评论进它的楼层页() {
        assertEquals(
            ReplyTapPolicy.TapResult.OPEN_FLOOR,
            ReplyTapPolicy.forBodyTap(childCount = 2, inDetailPage = true, sameRpid = false)
        )
    }

    @Test
    fun 楼层页里点没有子回复的评论给提示() {
        assertEquals(
            ReplyTapPolicy.TapResult.TOAST_NO_CHILD,
            ReplyTapPolicy.forBodyTap(childCount = 0, inDetailPage = true, sameRpid = false)
        )
    }

    @Test
    fun 同一条评论的楼层页不再叠一层() {
        assertEquals(
            ReplyTapPolicy.TapResult.IGNORE,
            ReplyTapPolicy.forBodyTap(childCount = 9, inDetailPage = true, sameRpid = true)
        )
    }
}
