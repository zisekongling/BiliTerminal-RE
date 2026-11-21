package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 新版播放器的总闸。
 *
 * 26.11.22 起新版播放器进入「下线观察」：设置入口已隐藏，并且这里把总闸写死成 false——
 * 即使设备上 `player_ui_new` 还留着 true（测试同学忘了关、设置备份还原回来的旧存档、
 * 或者实验包把配置一起带过来），普通播放器与短视频播放器也一律走旧播放器路径。
 *
 * 重新开实验：把 [EXPERIMENT_ENABLED] 改回 true，并把设置入口加回去即可，各调用点不用动。
 *
 * 这是**唯一**的新版播放器判据入口：别在别处直接读 [SettingsKeys.PLAYER_UI_NEW]，
 * 否则总闸就漏了。
 */
object PlayerUiSwitch {

    /** 实验总闸：false 时新版播放器整体不可达。 */
    const val EXPERIMENT_ENABLED = false

    /** 新版播放器是否生效（总闸 + 用户开关同时为真）。 */
    fun isNewUiEnabled(): Boolean =
        isNewUiEnabled(SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_UI_NEW, false))

    /**
     * 判据的纯函数形式，只吃「存档里的开关值」，不碰 SharedPreferences。
     *
     * 拆出来是为了让 [com.RobinNotBad.BiliClient.player.PlayerUiSwitchTest] 能在纯 JVM 单测里
     * 锁住两条契约：总闸关时存档为 true 也返回 false；总闸开时结果才跟着存档走。
     */
    fun isNewUiEnabled(switchStoredValue: Boolean): Boolean = EXPERIMENT_ENABLED && switchStoredValue
}
