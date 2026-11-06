package com.RobinNotBad.BiliClient.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「互关」徽章的判据单测（批次 1「社交与信息」）。
 *
 * <p>这两个纯函数是整条链上最容易被写错的一环：
 * 列表主体的 mid 与当前登录 mid 的比对（未登录/看别人列表都必须为 false），
 * 以及 B 站 `attribute == 6` 的语义（1 已关注 / 2 被关注 / 6 互相关注）。
 */
class MutualFollowPolicyTest {

    @Test
    fun isSelfList_requiresSamePositiveMid() {
        assertTrue(FollowApi.isSelfList(100L, 100L))
        assertFalse(FollowApi.isSelfList(100L, 200L))
        // 未登录时两边都是 0，不能算「自己的列表」
        assertFalse(FollowApi.isSelfList(0L, 0L))
        // 看别人列表（列表主体未知用 -1 占位）一律不是自己
        assertFalse(FollowApi.isSelfList(-1L, 100L))
        assertFalse(FollowApi.isSelfList(100L, 0L))
    }

    @Test
    fun isMutualFollow_requiresSelfListAndAttribute6() {
        assertTrue(FollowApi.isMutualFollow(true, 6))
        // 看别人的列表时 attribute==6 只代表「那个人与这条互关」，与当前用户无关
        assertFalse(FollowApi.isMutualFollow(false, 6))
        // 1 已关注 / 2 被关注 都不是互关
        assertFalse(FollowApi.isMutualFollow(true, 1))
        assertFalse(FollowApi.isMutualFollow(true, 2))
        assertFalse(FollowApi.isMutualFollow(true, 0))
        assertFalse(FollowApi.isMutualFollow(true, 7))
    }
}
