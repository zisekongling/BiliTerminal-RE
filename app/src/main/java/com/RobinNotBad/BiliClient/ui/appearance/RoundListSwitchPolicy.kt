package com.RobinNotBad.BiliClient.ui.appearance

/**
 * 「圆屏列表」与「圆屏模式」两个开关的联动判据（纯函数，无 Android 依赖，可直接 JVM 单测）。
 *
 * <h3>为什么需要联动（反馈 #172）</h3>
 * [RoundListEffect.isEnabled] 要求两个开关同时为真：`ui_round_list_effect`（实验室 → 圆屏列表）
 * 与 `player_ui_round`（布局适配 → 圆屏适配）。用户只打开了前者，效果判定恒为 false，看起来就是
 * 「开了和没开一样」。两个开关还分处两个不相邻的设置页，描述里也没提这层前置条件，
 * 用户基本不可能自己猜到。
 *
 * <h3>联动方向只有一条</h3>
 * 打开「圆屏列表」→ 顺带把「圆屏模式」打开；**关掉「圆屏列表」不回退「圆屏模式」**——
 * 用户本来就可能是为了边距 / 底部补偿单独开着圆屏模式的，替他关掉属于越权。
 */
object RoundListSwitchPolicy {

    /**
     * 这次开关动作之后，「圆屏模式」应该落成什么值。
     *
     * 开「圆屏列表」时恒为 true（它是效果生效的前置条件）；关「圆屏列表」时保持原值不动。
     *
     * @param roundMode 当前「圆屏模式」的值（`SettingsKeys.UI_ROUND`）
     * @param roundListEnabled 用户刚把「圆屏列表」切到的值
     */
    fun roundModeAfter(roundMode: Boolean, roundListEnabled: Boolean): Boolean =
        if (roundListEnabled) true else roundMode

    /**
     * 这次动作是否真的把「圆屏模式」由关变开——只有这种时候才值得弹一句提示，
     * 用户本来已经开着圆屏模式时再弹一次就是噪音。
     */
    fun turnsOnRoundMode(roundMode: Boolean, roundListEnabled: Boolean): Boolean =
        roundModeAfter(roundMode, roundListEnabled) && !roundMode
}
