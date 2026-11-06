package com.RobinNotBad.BiliClient.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表情面板「回落 reply」判据的单测（批次 1「社交与信息」）。
 *
 * <p>`business=im`（私信）面板不保证对网页 Cookie 开放，拉空时要回落到 reply 面板；
 * 但 reply 自己拉空时不能再「回落给 reply」——那会变成自我递归。
 * 这个判定只有一行逻辑，越简单越该用单测钉住。
 */
class EmoteApiFallbackTest {

    @Test
    fun shouldFallbackWhenImPanelIsEmpty() {
        assertTrue(EmoteApi.shouldFallbackToReply(EmoteApi.BUSINESS_IM, 0))
        // 负数没有实际来源，但语义上也属于「没拿到包」
        assertTrue(EmoteApi.shouldFallbackToReply(EmoteApi.BUSINESS_IM, -1))
    }

    @Test
    fun shouldNotFallbackWhenImPanelHasPackages() {
        assertFalse(EmoteApi.shouldFallbackToReply(EmoteApi.BUSINESS_IM, 1))
        assertFalse(EmoteApi.shouldFallbackToReply(EmoteApi.BUSINESS_IM, 5))
    }

    @Test
    fun replyNeverFallsBackToItself() {
        assertFalse(EmoteApi.shouldFallbackToReply(EmoteApi.BUSINESS_REPLY, 0))
        assertFalse(EmoteApi.shouldFallbackToReply(EmoteApi.BUSINESS_REPLY, 3))
    }

    @Test
    fun otherBusinessesAlsoFallBackToReply() {
        // 动态面板同理：拉空时用 reply 面板兜底，总比一个空弹窗好
        assertTrue(EmoteApi.shouldFallbackToReply(EmoteApi.BUSINESS_DYNAMIC, 0))
        assertFalse(EmoteApi.shouldFallbackToReply(EmoteApi.BUSINESS_DYNAMIC, 2))
    }
}
