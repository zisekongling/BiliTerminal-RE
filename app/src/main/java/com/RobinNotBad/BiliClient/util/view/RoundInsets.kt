package com.RobinNotBad.BiliClient.util.view

/**
 * 圆屏适配（`player_ui_round`）与「界面边距」（`paddingH_percent` / `paddingV_percent`）
 * 的统一算法：把屏幕尺寸算成根布局该留的一圈内边距。
 *
 * <h3>为什么抽出来</h3>
 * 这段算术原先只写在 `BaseActivity.onCreate` 里，于是**只有继承 BaseActivity 的页面**才有圆屏
 * 适配——伪装页（`BluetoothDisguiseActivity` / `CalculatorDisguiseActivity` 继承
 * `AppCompatActivity`）用的是另抄一份、还把百分比当 dp 用的实现，效果差了十几倍。
 * 同一个设置项必须只有一份算法，这里就是那份；Android 侧取屏幕尺寸见 [physicalScreenSizePx]。
 *
 * 纯算术、无 Android 依赖，边界在 [RoundInsetsTest] 里钉死。
 */
object RoundInsets {

    /**
     * 圆屏适配时底部额外让出的屏高比例。
     *
     * 圆形表盘下方常有一条被表冠/下巴压住的区域，正文贴底会被挡住；[INSETS] 之外再让 3%。
     */
    const val ROUND_EXTRA_BOTTOM_RATIO = 0.03f

    /** 「界面边距」百分比的合法性上限，与设置页输入框的限制一致（0~30）。 */
    const val MAX_PADDING_PERCENT = 30

    /** `√2`：圆的内接正方形边长 = `直径 / √2`。 */
    private const val SQRT_2 = 1.4142135f

    /** 一圈内边距（px）。 */
    data class Insets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        /** 四边都是 0。 */
        val isEmpty: Boolean
            get() = left <= 0 && top <= 0 && right <= 0 && bottom <= 0
    }

    /**
     * 算出该留的一圈内边距。
     *
     * 与 `BaseActivity.onCreate` 原先那段**逐字一致**（整数运算）：横向 `屏宽 × 横边距% / 100`，
     * 纵向 `屏高 × 纵边距% / 100`，圆屏时底部再加 [ROUND_EXTRA_BOTTOM_RATIO]。
     * 多出来的一点是把百分比夹到 `0~30`：设置页只允许 0~30，但恢复备份/改库可能塞进更离谱的值
     * （50% 会让内容宽度算成负数），这里统一按上限收口。
     *
     * @param round 圆屏适配开关（`SettingsKeys.UI_ROUND`）
     */
    fun compute(
        screenWidthPx: Int,
        screenHeightPx: Int,
        padHPercent: Int,
        padVPercent: Int,
        round: Boolean
    ): Insets {
        val h = screenWidthPx * padHPercent.coerceIn(0, MAX_PADDING_PERCENT) / 100
        val v = screenHeightPx * padVPercent.coerceIn(0, MAX_PADDING_PERCENT) / 100
        val extraBottom = if (round) (screenHeightPx * ROUND_EXTRA_BOTTOM_RATIO).toInt() else 0
        return Insets(left = h, top = v, right = h, bottom = v + extraBottom)
    }

    /**
     * 圆的内接正方形要求四边各留多少（px）：`(1 - 1/√2) / 2 × 直径 ≈ 0.1464 × 直径`。
     *
     * 380 的表盘上是 55px——比「界面边距」5% 的 19px 大得多。整屏铺满的伪装界面（计算器那种
     * 4×4 键盘）只有收进这个方框里，四个角的按键才真的落在圆内、点得到。
     */
    fun inscribedInsetPx(diameterPx: Int): Int =
        kotlin.math.ceil(diameterPx.coerceAtLeast(0) * (1f - 1f / SQRT_2) / 2f).toInt()
}
