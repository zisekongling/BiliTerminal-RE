package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「普通档走合一 MP4、高档走 DASH」取流策略的单测。
 *
 * 这些判据直接决定在线播放的清晰度与音画同步：普通档留 DASH 会重新引入音画漂移，
 * 高档被切到合一 MP4 会静默丢 1080P（实测 durl 接口最高只回 720P），
 * 而无统一内核时保留纯 DASH 视频流就是「有画面没声音」。
 */
class PlaybackStrategyPolicyTest {

    @Test
    fun `普通档位走合一MP4`() {
        assertTrue(PlaybackStrategyPolicy.usesProgressivePlayback(6))   // 240P
        assertTrue(PlaybackStrategyPolicy.usesProgressivePlayback(16))  // 360P
        assertTrue(PlaybackStrategyPolicy.usesProgressivePlayback(32))  // 480P
        assertTrue(PlaybackStrategyPolicy.usesProgressivePlayback(64))  // 720P
    }

    @Test
    fun `1080P及以上必须走DASH`() {
        // 80 不放进普通档：实测合一 MP4 接口请求 1080P 只回 720P，切过去就是静默降清晰度
        assertFalse(PlaybackStrategyPolicy.usesProgressivePlayback(80))   // 1080P
        assertFalse(PlaybackStrategyPolicy.usesProgressivePlayback(112))  // 1080P+
        assertFalse(PlaybackStrategyPolicy.usesProgressivePlayback(116))  // 1080P60
        assertFalse(PlaybackStrategyPolicy.usesProgressivePlayback(120))  // 4K
        assertFalse(PlaybackStrategyPolicy.usesProgressivePlayback(126))  // 杜比视界
        assertFalse(PlaybackStrategyPolicy.usesProgressivePlayback(127))  // 8K
        assertFalse(PlaybackStrategyPolicy.usesProgressivePlayback(0))    // 自动档
    }

    @Test
    fun `合一流档位不低于DASH时选合一流`() {
        assertEquals(
            PlaybackStrategyPolicy.Source.MUXED,
            PlaybackStrategyPolicy.chooseSource(64, 64, true)
        )
        assertEquals(
            PlaybackStrategyPolicy.Source.MUXED,
            PlaybackStrategyPolicy.chooseSource(32, 64, true)
        )
        // 无统一内核时只要能拿到合一流就选合一流（DASH 纯视频等于无声）
        assertEquals(
            PlaybackStrategyPolicy.Source.MUXED,
            PlaybackStrategyPolicy.chooseSource(64, 64, false)
        )
    }

    @Test
    fun `durl被降级时保留DASH以免丢1080P`() {
        // 请求 1080P，DASH 给 80、合一接口只给 64：有统一内核就保 DASH
        assertEquals(
            PlaybackStrategyPolicy.Source.DASH,
            PlaybackStrategyPolicy.chooseSource(80, 64, true)
        )
        // 清晰度差一档也一样：只要合一流更低就保 DASH
        assertEquals(
            PlaybackStrategyPolicy.Source.DASH,
            PlaybackStrategyPolicy.chooseSource(116, 112, true)
        )
    }

    @Test
    fun `没有统一内核时降级为合一MP4`() {
        // 有 DASH 但播放器内核不支持纯粹的双流播放：宁可 720P 有声，也不要 1080P 无声
        assertEquals(
            PlaybackStrategyPolicy.Source.MUXED,
            PlaybackStrategyPolicy.chooseSource(80, 64, false)
        )
    }

    @Test
    fun `合一MP4没拿到有效档位时留在DASH`() {
        assertEquals(
            PlaybackStrategyPolicy.Source.DASH,
            PlaybackStrategyPolicy.chooseSource(64, 0, true)
        )
        assertEquals(
            PlaybackStrategyPolicy.Source.DASH,
            PlaybackStrategyPolicy.chooseSource(64, -1, false)
        )
    }

    @Test
    fun `1080P60的降级序列从高到低`() {
        assertEquals(
            listOf(116, 112, 80, 74, 64, 32, 16, 6),
            PlaybackStrategyPolicy.fallbackQualities(116)
        )
    }

    @Test
    fun `720P的降级序列不包含更高档`() {
        assertEquals(
            listOf(64, 32, 16, 6),
            PlaybackStrategyPolicy.fallbackQualities(64)
        )
    }

    @Test
    fun `自动档的降级序列从720P开始`() {
        // qn<=0 表示自动：合一 MP4 接口最高只给 720P，不必从 1080P 白试一趟
        assertEquals(
            listOf(64, 32, 16, 6),
            PlaybackStrategyPolicy.fallbackQualities(0)
        )
        assertEquals(
            listOf(64, 32, 16, 6),
            PlaybackStrategyPolicy.fallbackQualities(-1)
        )
    }

    @Test
    fun `序列外档位先按原档位试一次`() {
        // 100=智能修复，不在标准序列里：先用原档位请求（服务端自行决定怎么降），再走标准序列
        assertEquals(
            listOf(100, 80, 74, 64, 32, 16, 6),
            PlaybackStrategyPolicy.fallbackQualities(100)
        )
    }

    @Test
    fun `最低档位的降级序列只有它自己`() {
        assertEquals(listOf(6), PlaybackStrategyPolicy.fallbackQualities(6))
    }
}
