package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FeedbackImage] 里能脱离 Android 跑的那部分：解码采样率与几个上限常量。
 *
 * <p>采样率看起来只是「除个 2」，但它决定了一张 4K 截图会不会被整张读进内存——
 * 手表设备内存紧，算错一档就可能 OOM，所以钉住。
 */
class FeedbackImageTest {

    @Test
    fun sampleSize_smallImageIsNotSampled() {
        assertEquals(1, FeedbackImage.sampleSizeFor(800, 600, FeedbackImage.MAX_EDGE))
    }

    @Test
    fun sampleSize_edgeEqualToLimitIsNotSampled() {
        // 长边正好等于上限时不该再缩：缩一档就只剩一半分辨率了
        assertEquals(1, FeedbackImage.sampleSizeFor(1600, 1200, 1600))
    }

    @Test
    fun sampleSize_halvesUntilLongEdgeFits() {
        assertEquals(2, FeedbackImage.sampleSizeFor(3200, 1600, 1600))
    }

    @Test
    fun sampleSize_fourKPhotoNeedsTwoHalvings() {
        assertEquals(4, FeedbackImage.sampleSizeFor(6400, 4800, 1600))
    }

    @Test
    fun sampleSize_usesLongEdgeNotShortEdge() {
        // 超宽长图：短边很小，但长边仍然超限，必须缩
        assertEquals(2, FeedbackImage.sampleSizeFor(4000, 100, 1600))
    }

    @Test
    fun sampleSize_invalidInputFallsBackToOne() {
        assertEquals(1, FeedbackImage.sampleSizeFor(0, 0, 1600))
        assertEquals(1, FeedbackImage.sampleSizeFor(-1, 500, 1600))
        assertEquals(1, FeedbackImage.sampleSizeFor(1600, 1600, 0))
    }

    @Test
    fun limitsMatchServerContract() {
        // 这两个数字与服务端 FEEDBACK_IMAGE_MAX_COUNT / FEEDBACK_IMAGE_MAX_BYTES 一一对应，
        // 改一边就必须改另一边，所以在这里钉住
        assertEquals(3, FeedbackImage.MAX_COUNT)
        assertEquals(2 * 1024 * 1024, FeedbackImage.MAX_BYTES)
    }
}
