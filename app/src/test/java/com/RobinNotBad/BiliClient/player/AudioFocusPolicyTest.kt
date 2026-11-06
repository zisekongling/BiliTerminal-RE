package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音频焦点判据的单测。
 *
 * 焦点丢失的分支只能在真机（来电/导航）复现，这里把「什么时候暂停、什么时候放弃焦点」钉死，
 * 避免以后改播放器生命周期时不小心破坏后台播放。
 */
class AudioFocusPolicyTest {

    @Test
    fun `长期丢失焦点要暂停`() {
        assertTrue(AudioFocusPolicy.shouldPause(AudioFocusPolicy.LOSS))
        assertEquals(AudioFocusPolicy.Action.PAUSE, AudioFocusPolicy.actionFor(AudioFocusPolicy.LOSS))
    }

    @Test
    fun `短暂丢失焦点也暂停且不自动续播`() {
        assertTrue(AudioFocusPolicy.shouldPause(AudioFocusPolicy.LOSS_TRANSIENT))
        // 拿回焦点这个事件本身不产生任何动作——不自动续播由「无动作」表达
        assertEquals(AudioFocusPolicy.Action.NONE, AudioFocusPolicy.actionFor(AudioFocusPolicy.GAIN))
    }

    @Test
    fun `允许降音量的场景不做duck也不暂停`() {
        assertFalse(AudioFocusPolicy.shouldPause(AudioFocusPolicy.LOSS_TRANSIENT_CAN_DUCK))
        assertEquals(
            AudioFocusPolicy.Action.NONE,
            AudioFocusPolicy.actionFor(AudioFocusPolicy.LOSS_TRANSIENT_CAN_DUCK)
        )
    }

    @Test
    fun `未知焦点值按不动作处理`() {
        assertEquals(AudioFocusPolicy.Action.NONE, AudioFocusPolicy.actionFor(0))
        assertEquals(AudioFocusPolicy.Action.NONE, AudioFocusPolicy.actionFor(99))
    }

    @Test
    fun `后台播放中离开页面要保留焦点`() {
        assertFalse(
            AudioFocusPolicy.shouldAbandonOnStop(
                backgroundPlaybackEnabled = true,
                isPlaying = true,
                isFinishing = false
            )
        )
    }

    @Test
    fun `没开后台播放时离开页面要放弃焦点`() {
        assertTrue(
            AudioFocusPolicy.shouldAbandonOnStop(
                backgroundPlaybackEnabled = false,
                isPlaying = true,
                isFinishing = false
            )
        )
    }

    @Test
    fun `后台播放开关开着但已暂停也要放弃焦点`() {
        assertTrue(
            AudioFocusPolicy.shouldAbandonOnStop(
                backgroundPlaybackEnabled = true,
                isPlaying = false,
                isFinishing = false
            )
        )
    }

    @Test
    fun `正在退出时一定放弃焦点`() {
        assertTrue(
            AudioFocusPolicy.shouldAbandonOnStop(
                backgroundPlaybackEnabled = true,
                isPlaying = true,
                isFinishing = true
            )
        )
    }
}
