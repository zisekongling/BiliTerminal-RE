package com.RobinNotBad.BiliClient.util

/**
 * 「布局适配」页三个输入框的解析与范围校验。
 *
 * <p>为什么要单独抽出来：这三个框都是 `EditText`，`inputType=numberDecimal` / `number`
 * **只约束软键盘**，粘贴、IME 直接提交、无障碍服务都能把任意文字塞进去。旧代码直接
 * `text.toFloat()` / `toInt()`，于是线上真出现过两次 `NumberFormatException`
 * （`For input string: "界面大小"` 和 `"确认设置"`——都是页面自己的文案，疑似被粘贴进去的），
 * 每次都在点「确认设置」时整页崩掉。
 *
 * <p>这里用 `toFloatOrNull` / `toIntOrNull`：非法内容返回 `null`，调用方跳过写盘，
 * 页面照常关闭。范围校验与旧代码完全一致（DPI 0.1~10，边距 ≤30），不做额外收紧。
 *
 * <p>本文件还持有**密度决策**（[resolveDensityDpi] / [DENSITY_MIN]，反馈 #225）：它和这里的
 * 输入框解析是同一件事的两半——输入框决定「倍数是多少」，决策函数决定「最终用哪个 DPI」。
 * 两者都是纯逻辑、都能在 JVM 上单测，所以放同一个文件，不再散进 Activity。
 */
object UiScaleInput {

    const val DPI_MIN = 0.1f
    const val DPI_MAX = 10.0f

    /** 边距只卡上限，与旧代码一致（负值照旧放行，避免顺手改行为）。 */
    const val PADDING_MAX = 30

    /** 解析界面缩放倍数；非数字或超出 0.1~10 都返回 `null`。 */
    @JvmStatic
    fun parseDpi(text: CharSequence?): Float? =
        text?.toString()?.trim()?.toFloatOrNull()?.takeIf { it >= DPI_MIN && it <= DPI_MAX }

    /** 解析边距百分比；非数字或大于 30 返回 `null`。 */
    @JvmStatic
    fun parsePadding(text: CharSequence?): Int? =
        text?.toString()?.trim()?.toIntOrNull()?.takeIf { it <= PADDING_MAX }

    /**
     * 解析「顶栏高度」（dp）；非数字返回 `null`。
     *
     * 这里**只判能不能解析**，上下限交给 `ui/widget/TopbarHeightPolicy.clampDp`：
     * 读侧（`BaseActivity`）本来就要再钳一次，上限只该有一个定义处，否则两处漂移。
     */
    @JvmStatic
    fun parseTopbarHeight(text: CharSequence?): Int? =
        text?.toString()?.trim()?.toIntOrNull()

    // ---- 密度（反馈 #225：字体大小不统一） ----

    /**
     * 「绝对 DPI」的有效下限：低于它就当没设置。
     *
     * 这个 72 以前在 `BaseActivity.kt:201` 与设置页写盘处（`SettingGroupActivity`「设置Density」
     * 分支）各写一遍字面量，两边一旦漂移就会出现「存进去了但不生效 / 明明没存却生效」。
     * 现在只在这里定义一次，读侧统一引用。
     */
    const val DENSITY_MIN = 72

    /**
     * 「当前该用多少 DPI」的**唯一**决策点。
     *
     * <p>为什么必须收成一处：密度原先有两条互不相干的算法——
     * `BiliTerminal.getFitDisplayContext` 只认**倍数**（`dpi` 偏好，默认 1.0，见 BiliTerminal.kt:208 与
     * 那里的 `densityDpi * dpiTimes`），`BaseActivity.setDensity` 只认**绝对值**（`density` 偏好，默认 -1，
     * 见 BaseActivity.kt:200-203）。于是同一个进程里页面走绝对值、Application context（Toast、
     * `ToolsUtil.dp2px/sp2px`）走倍数，两边算出来的像素天然对不上；用户在设置里改了其中一个，
     * 另一个也不会跟着动。反馈 #225 里「有的字大、有的字小」正是这种多答案的直接后果。
     *
     * <p>优先级：绝对 DPI（≥[DENSITY_MIN]）优先，否则 `baseDensityDpi × times`。
     * 倍数非法（NaN / 0 / 负数 / 超出 0.1~10）时回退 [baseDensityDpi]——旧代码会直接
     * `(dpi × 0.0).toInt() = 0` 并把 `densityDpi` 写成 0（`DENSITY_DPI_UNDEFINED`），
     * 那等于把整屏压成 0 像素，宁可不动。
     *
     * <p>返回值的整数化与旧代码一致（截断），所以合法倍数下的结果和改造前逐像素相同。
     *
     * @param baseDensityDpi 系统基线 DPI，**必须**是未被本应用覆写过的值
     *   （`BiliTerminal.getFitDisplayContext` 传的是入参 context 自己的 `displayMetrics.densityDpi`，
     *   重复调用不会把倍数乘两次）
     * @param absoluteDensityDpi `density` 偏好原值，未设置传 -1
     * @param times `dpi` 偏好（界面缩放倍数）
     */
    @JvmStatic
    fun resolveDensityDpi(baseDensityDpi: Int, absoluteDensityDpi: Int, times: Float): Int {
        if (absoluteDensityDpi >= DENSITY_MIN) return absoluteDensityDpi
        if (baseDensityDpi <= 0) return baseDensityDpi
        if (times.isNaN() || times < DPI_MIN || times > DPI_MAX) return baseDensityDpi
        return (baseDensityDpi * times).toInt()
    }
}
