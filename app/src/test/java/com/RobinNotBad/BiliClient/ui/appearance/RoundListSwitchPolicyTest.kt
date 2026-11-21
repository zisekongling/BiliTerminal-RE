package com.RobinNotBad.BiliClient.ui.appearance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「圆屏列表」联动「圆屏模式」的判据（反馈 #172）。
 *
 * 用户原话「实验室那个圆屏列表开了感觉和没开一样」：只开了圆屏列表、没开圆屏模式，
 * 而 [RoundListEffect.isEnabled] 要求两者同时为真，效果恒不生效。
 *
 * 这里钉死三条：开列表要顺带开模式；模式已开时不重复提示；关列表**不回退**模式。
 */
class RoundListSwitchPolicyTest {

    @Test
    fun bothOff_enableRoundList_turnsOnRoundModeToo() {
        // 反馈现场：两个都关着，用户打开「圆屏列表」→ 圆屏模式必须跟着打开
        assertTrue("开圆屏列表后圆屏模式应为开", RoundListSwitchPolicy.roundModeAfter(false, true))
        assertTrue("由关变开，应该提示用户", RoundListSwitchPolicy.turnsOnRoundMode(false, true))
    }

    @Test
    fun roundModeAlreadyOn_enableRoundList_keepsOnWithoutNotice() {
        // 圆屏模式早就开着（用户自己开的），再开圆屏列表：值不变，也不该再弹一次提示
        assertTrue("圆屏模式保持开", RoundListSwitchPolicy.roundModeAfter(true, true))
        assertFalse("没有发生变化，不要再提示", RoundListSwitchPolicy.turnsOnRoundMode(true, true))
    }

    @Test
    fun disableRoundList_keepsRoundModeOn() {
        // 关掉圆屏列表：圆屏模式是用户自己的设置，不替他关（他可能为了边距一直在用）
        assertTrue("关圆屏列表不回退圆屏模式", RoundListSwitchPolicy.roundModeAfter(true, false))
        assertFalse("关列表不涉及提示", RoundListSwitchPolicy.turnsOnRoundMode(true, false))
    }

    @Test
    fun bothOff_disableRoundList_staysOff() {
        assertFalse("两个都关着时关列表仍是关", RoundListSwitchPolicy.roundModeAfter(false, false))
        assertFalse("没有变化，不提示", RoundListSwitchPolicy.turnsOnRoundMode(false, false))
    }
}
