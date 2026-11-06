package com.RobinNotBad.BiliClient.player

/**
 * 「强制为滚动弹幕」的纯判据（E2：设置项存在但此前全仓无消费方）。
 *
 * 语义：开启后，**顶部/底部固定弹幕**按「右→左滚动弹幕」渲染，避免固定弹幕长期遮挡画面；
 * 高级弹幕（`TYPE_SPECIAL`）与左右滚动弹幕保持原样。
 *
 * 为什么要单独一层「类型换算」：
 * - 纯逻辑可单测：真机上要构造一份含顶部/底部弹幕的弹幕源才能看到效果；
 * - DFM 的 `type` 由各弹幕子类的 `getType()` 常量决定，**光改 type 字段没用**，
 *   必须在解析/插入时换成 `R2LDanmaku`（见 [ForceScrollParser] 与 `DanmakuManager.addDanmaku`）。
 *
 * 数值与 `master.flame.danmaku.danmaku.model.BaseDanmaku` 的常量一一对应（这里不 import 模块类，
 * 保持纯 JVM 可测）。
 */
object DanmakuStylePolicy {

    /** 右→左滚动（DFM `TYPE_SCROLL_RL`）。 */
    const val TYPE_SCROLL_RL = 1

    /** 底部固定（DFM `TYPE_FIX_BOTTOM`）。 */
    const val TYPE_FIX_BOTTOM = 4

    /** 顶部固定（DFM `TYPE_FIX_TOP`）。 */
    const val TYPE_FIX_TOP = 5

    /** 左→右滚动（DFM `TYPE_SCROLL_LR`）。 */
    const val TYPE_SCROLL_LR = 6

    /** 高级弹幕（DFM `TYPE_SPECIAL`），强制滚动时**不动**它。 */
    const val TYPE_SPECIAL = 7

    /** 是否是会被强制转换的固定弹幕类型。 */
    @JvmStatic
    fun isFixedType(type: Int): Boolean = type == TYPE_FIX_TOP || type == TYPE_FIX_BOTTOM

    /** 这条弹幕在「强制为滚动弹幕」开启时是否要转换。 */
    @JvmStatic
    fun shouldForceScroll(forceR2L: Boolean, type: Int): Boolean = forceR2L && isFixedType(type)

    /** 换算后的实际类型：开启强制滚动时，顶部/底部 → 右→左滚动；其余原样。 */
    @JvmStatic
    fun effectiveType(forceR2L: Boolean, type: Int): Int =
        if (shouldForceScroll(forceR2L, type)) TYPE_SCROLL_RL else type
}
