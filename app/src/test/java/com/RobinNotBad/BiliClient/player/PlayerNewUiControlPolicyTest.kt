package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新版播放器「暂停时控件常驻」的判据单测。
 *
 * 设计稿 NOTES.paused 只禁掉**自动**隐藏；用户单击/手势收起必须照样生效，
 * 否则暂停之后控件就再也关不掉了。这两种情形在设备上很难分辨（都是画面变干净），
 * 所以把判据钉在纯函数层。
 */
class PlayerNewUiControlPolicyTest {

    @Test
    fun `播放中自动隐藏照常生效`() {
        assertTrue(PlayerNewUiControlPolicy.autoHideAllowed(playing = true, manual = false))
    }

    @Test
    fun `暂停时拒绝自动隐藏`() {
        assertFalse(PlayerNewUiControlPolicy.autoHideAllowed(playing = false, manual = false))
    }

    @Test
    fun `暂停时用户主动收起仍然生效`() {
        assertTrue(PlayerNewUiControlPolicy.autoHideAllowed(playing = false, manual = true))
    }

    @Test
    fun `播放中用户主动收起也生效`() {
        assertTrue(PlayerNewUiControlPolicy.autoHideAllowed(playing = true, manual = true))
    }
}