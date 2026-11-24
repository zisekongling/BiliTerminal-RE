package com.RobinNotBad.BiliClient.util.view

/**
 * 铺满全屏的页面（看图页这类）底部两枚角标该留的外边距。
 *
 * <h3>为什么不能直接用布局里的 8dp</h3>
 * 方形表盘上贴角没问题，圆形表盘的左右下角是**切掉的**：底边往上 8dp 处，圆的弦宽只剩
 * `2·√(R² − (R−8dp)²)`（半径 113dp 的表盘 ≈ 84dp），角标会被圆弧压住甚至点不到。
 *
 * <h3>为什么用「内接正方形」那一圈</h3>
 * [RoundInsets.inscribedInsetPx] 给的正是「离边 0.1464×直径」这个点，它在圆上；再叠上布局
 * 原本的基准边距，角标的外角就严格落在圆内，而且横向内缩与纵向抬高是同一个数——两者一起
 * 表现成「角标沿圆弧往中间收」，不需要为横竖各编一个系数。表盘越大，这一圈按比例越大。
 *
 * 纯算术、无 Android 依赖，边界在 [CornerLabelInsetsTest] 里钉死。
 */
object CornerLabelInsets {

    /**
     * 算出底部角标要留的（横向, 纵向）外边距，单位 px。
     *
     * 方屏原样返回 `baseMarginPx`（与布局里的值一致，不做任何改动）；圆屏在基准值上叠加
     * 内接正方形那一圈，横竖同值。
     *
     * @param diameterPx 表盘直径（min(屏宽, 屏高)）；量不出来时传 0，此时等价于方屏
     * @param baseMarginPx 布局里给的基准边距（方屏用的 8dp 换算值）
     * @param round 圆屏适配开关（`SettingsKeys.UI_ROUND`）
     */
    @JvmStatic
    fun marginsPx(diameterPx: Int, baseMarginPx: Int, round: Boolean): IntArray {
        val base = baseMarginPx.coerceAtLeast(0)
        if (!round || diameterPx <= 0) return intArrayOf(base, base)
        val inset = RoundInsets.inscribedInsetPx(diameterPx) + base
        return intArrayOf(inset, inset)
    }
}
