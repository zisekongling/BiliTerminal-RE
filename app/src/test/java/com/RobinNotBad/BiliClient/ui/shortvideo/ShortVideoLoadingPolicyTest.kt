package com.RobinNotBad.BiliClient.ui.shortvideo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 加载层显隐：只有「空列表、没失败、没出帧」才盖转圈。 */
class ShortVideoLoadingPolicyTest {

    @Test
    fun emptyFeedWithoutFailureKeepsSpinner() {
        assertTrue(ShortVideoLoadingPolicy.keepLoading(0, loadFailed = false, firstFrameOut = false))
    }

    @Test
    fun contentDismissesSpinnerEvenBeforeFirstFrame() {
        assertFalse(ShortVideoLoadingPolicy.keepLoading(1, loadFailed = false, firstFrameOut = false))
        assertFalse(ShortVideoLoadingPolicy.keepLoading(10, loadFailed = false, firstFrameOut = false))
    }

    @Test
    fun failedRefreshWithContentNeverCoversPictureAgain() {
        // 本轮缺口：已有内容时 onLoadError 只弹提示，加载层却留在 VISIBLE
        assertFalse(ShortVideoLoadingPolicy.keepLoading(3, loadFailed = true, firstFrameOut = false))
    }

    @Test
    fun failedEmptyFeedShowsErrorNotSpinner() {
        assertFalse(ShortVideoLoadingPolicy.keepLoading(0, loadFailed = true, firstFrameOut = false))
    }

    @Test
    fun firstFrameDismissesSpinnerRegardlessOfListCallbacks() {
        assertFalse(ShortVideoLoadingPolicy.keepLoading(0, loadFailed = false, firstFrameOut = true))
        assertFalse(ShortVideoLoadingPolicy.keepLoading(5, loadFailed = true, firstFrameOut = true))
    }
}