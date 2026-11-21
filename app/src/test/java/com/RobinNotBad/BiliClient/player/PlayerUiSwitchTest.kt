package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 新版播放器总闸的判据回归（26.11.22 新增，配合「新版播放器下线观察」）。
 *
 * 只锁判据本身：总闸 + 存档开关同时为真才启用。真机上的界面差异证明不了这条契约，
 * 反过来这条契约成立时，存档里残留的 player_ui_new=true（测试同学忘关、备份还原、
 * 实验包带配置）也不会把线上用户切到新版界面。
 */
class PlayerUiSwitchTest {

    @Test
    fun storedTrue_followsExperimentGate() {
        // 不把总闸的值写死进断言：将来复开实验时这条测试不用改，语义仍是「存档 true 时跟总闸一致」
        assertEquals(PlayerUiSwitch.EXPERIMENT_ENABLED, PlayerUiSwitch.isNewUiEnabled(true))
    }

    @Test
    fun storedFalse_neverEnabled() {
        assertFalse(PlayerUiSwitch.isNewUiEnabled(false))
    }

    @Test
    fun experimentCurrentlyOff() {
        // 下线观察期：总闸必须是关的。复开实验时把这条断言连同 EXPERIMENT_ENABLED 一起改
        assertFalse("下线观察期总闸必须关闭", PlayerUiSwitch.EXPERIMENT_ENABLED)
    }
}
