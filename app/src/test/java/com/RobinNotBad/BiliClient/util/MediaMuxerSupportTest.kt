package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMuxerSupportTest {

    @Test
    fun `API 24 及以下不支持 MP4 B 帧 muxing`() {
        assertFalse(MediaMuxerSupport.supportsBFrameMuxing(19))
        assertFalse(MediaMuxerSupport.supportsBFrameMuxing(22))
        assertFalse(MediaMuxerSupport.supportsBFrameMuxing(24))
    }

    @Test
    fun `API 25 起支持 MP4 B 帧 muxing`() {
        assertTrue(MediaMuxerSupport.supportsBFrameMuxing(25))
        assertTrue(MediaMuxerSupport.supportsBFrameMuxing(34))
    }
}
