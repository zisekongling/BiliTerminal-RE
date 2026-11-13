package com.RobinNotBad.BiliClient.util.view

/**
 * 弹窗尺寸预算：算出窗口多宽、滚动区多高、哪几行该让位。
 *
 * <h3>为什么单独抽成一个对象</h3>
 * 与 [ScrollRetractDecider] 同一个理由：真正难写的是**判断**（预算够不够、先舍提示行还是先舍
 * 标题行），而 `View.measure()` / `layoutParams` 在 JVM 单测里全是被桩掉的 Android 类，
 * 断言写了也是假的。把算术挪到这里，`TerminalDialog.Sheet` 只做「把 View 量一遍、把结果填回去」
 * 的机械翻译，剩下的边界可以在 [DialogSizingTest] 里钉死。
 *
 * <h3>要解决的现场问题（26.10.20 用户反馈「低分辨率设备上弹窗显示不全」）</h3>
 * 图里正文与按钮都被切掉，成因其实有两条，互相独立：
 *
 * 1. **宽度**：`Window.setLayout()` 在 `show()` 之前调**不落地**——appcompat 的 `AlertDialog`
 *    要到 `onCreate` 才 `installContent()`，会把窗口属性重置回 `wrap × wrap`（380×380 上
 *    `dumpsys window` 实测 `mAttrs=(wrapxwrap)`）。而自定义视图是在 `inflate(…, null)` 里建的，
 *    根本没有 `layoutParams`，`FrameLayout.addView` 于是给它补了一份 `MATCH_PARENT × MATCH_PARENT`
 *    → 窗口被撑成**整屏宽**（26.10.20 想留的「两侧点空白取消」一像素都没有），高度也被撑满。
 *    只把宽度写到 content 的 `layoutParams` 上**也不够**：`installContent()` 会重新
 *    `addView(child, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))`，覆盖掉它
 *    （380×380 实测：content 仍是 380 宽）。真正生效的时机是 `show()` 之后——
 *    `Sheet` 在 `onShowListener` 里再落一次同样的尺寸，第一帧就是对的。
 *
 * 2. **高度**：滚动区曾被硬钉成屏高的 45%，可「标题 + 45% + 按钮栏 + 提示行」四段相加在
 *    小屏上必然超过屏高（320×320 需要 353px、300×300 需要 344px），最后一段直接落到屏幕外。
 *    在设置里把「界面大小」调大会把 dp 全部放大（`BaseActivity.setDensity`），溢出更夸张：
 *    380×380 上按 1.8 倍放大后仅「标题 142 + 底栏 158 + 提示 108」就有 412px > 380px。
 *
 * 现在的做法：**先量、再算、最后按优先级舍弃**。带宽度的相对关系（谁先让位）都定义在这里。
 */
object DialogSizing {

    /** 弹窗宽度占可用宽度的比例。剩下的留成真正能点的「窗口外」空白。 */
    const val WIDTH_RATIO = 0.9f

    /**
     * 弹窗高度占可用高度的比例。
     *
     * 不占满是有实际用途的：上下各留一点空白，用户才有地方「点外面取消」
     * （`FLAG_WATCH_OUTSIDE_TOUCH` 只送窗口外的触摸，窗口贴满上下边就只剩左右两条缝）。
     */
    const val HEIGHT_RATIO = 0.94f

    /**
     * 圆屏（`player_ui_round`）安全区的收缩余量：按 `屏幕短边 × 余量` 当有效直径去算内接框。
     *
     * <h3>为什么圆屏不能直接用 [WIDTH_RATIO] / [HEIGHT_RATIO]</h3>
     * 那两个比例是给矩形屏幕留的：弹窗居中、`0.9 × 0.94` 的框在矩屏上四角都在屏内，
     * 换成圆形表盘就落到圆**外面**了。圆上「宽度给足了高度就得收」是硬约束：
     * 居中的 `w × h` 矩形要整个落在直径 `D` 的圆内，必须满足 `w² + h² ≤ D²`。
     * 380×380 上按 `0.9 × 0.94` 算是 `342 × 336`，而圆在 `y` 方向离中心 168px 处只剩
     * `±88px` 宽——弹窗上下两端的左右边缘**整段在圆外**，条目文字被表盘切掉。
     * 条目越多、弹窗越高，切掉的越多；两三条的短弹窗却看不出问题。这正是用户反馈的
     * 「部分长按二级菜单没有圆屏适配、显示不全」：长按缓存视频那条菜单是
     * 标题 + 5 条 + 提示行，属最高的那一个（见 [roundBox]）。
     */
    const val ROUND_FIT_MARGIN = 0.97f

    /** `√2`：圆的内接正方形边长 = `直径 / √2`。 */
    private const val SQRT_2 = 1.4142135f

    /** 「界面边距」百分比的合法性上限，与设置页输入框的限制一致（0~30）。 */
    const val MAX_PADDING_PERCENT = 30

    /** 一屏可用区域（px）。 */
    data class Frame(val width: Int, val height: Int)

    /** 除滚动区以外的固定部分（px）。看不见的行传 0。 */
    data class Parts(
        /** 布局容器自身的上下 padding。 */
        val padding: Int,
        /** 标题行。 */
        val title: Int,
        /** 标题下的分隔线。 */
        val divider: Int,
        /**
         * 按钮区（26.11.13 起所有弹窗都有、且**永远在滚动区外**；没有按钮的菜单 / 单选型传 0）。
         *
         * 每个按钮独占一行（`TerminalDialog.Sheet.addButton` 里是整行宽 TextView），所以这里要按
         * 「按钮个数 × 行高」算，别当成一条固定高度。
         */
        val buttonBar: Int,
        /** 底部提示行。 */
        val hint: Int,
        /** 滚动区至少留这么多才值得留（一个条目高 ≈ 一行文字 + 上下留白）。 */
        val minScroll: Int
    )

    /** 尺寸方案。 */
    data class Plan(
        /** 窗口宽度（px），落在 content 的 `layoutParams.width` 上。 */
        val width: Int,
        /** 提示行让位（`true` → `View.GONE`）。 */
        val hideHint: Boolean,
        /** 标题行与分隔线让位。 */
        val hideTitle: Boolean,
        /**
         * 滚动区高度（px）；`null` = 用 `wrap_content`。
         *
         * `null` 不是「不给高度」而是「按内容自然高度收着」：以前的实现把滚动区**固定**
         * 撑到屏高的 45%，三五个条目时下方留一大片空白，也是这次一并修掉的。
         */
        val scrollHeight: Int?
    )

    /**
     * 可用区域 = 物理屏幕扣掉用户设置的「界面边距」。
     *
     * 百分比算法与 `BaseActivity.onCreate` 里那段一致（整数运算，`scrW * percent / 100`），
     * 这样弹窗和在它后面那层页面用的是同一个安全区。不过只缩边距**不等于**四角避开圆形
     * 表盘——那是 [roundBox] 的事（圆屏适配打开时才会走）。
     */
    fun available(widthPx: Int, heightPx: Int, padHPercent: Int, padVPercent: Int): Frame {
        val padH = widthPx * padHPercent.coerceIn(0, MAX_PADDING_PERCENT) / 100
        val padV = heightPx * padVPercent.coerceIn(0, MAX_PADDING_PERCENT) / 100
        return Frame(
            width = (widthPx - padH * 2).coerceAtLeast(1),
            height = (heightPx - padV * 2).coerceAtLeast(1)
        )
    }

    /** 窗口宽度：可用宽度的 [WIDTH_RATIO]，且永远不超过可用宽度。 */
    fun width(frame: Frame): Int =
        (frame.width * WIDTH_RATIO).toInt().coerceIn(1, frame.width.coerceAtLeast(1))

    /**
     * 高度预算：可用高度的 [HEIGHT_RATIO]，且永远不超过可用高度。
     *
     * [plan] 拿它当「最多能占多高」，于是算出来的滚动区高度直接让弹窗整体收在屏内。
     */
    fun heightBudget(frame: Frame): Int =
        (frame.height * HEIGHT_RATIO).toInt().coerceIn(1, frame.height.coerceAtLeast(1))

    /** 窗口框：宽 + 高度预算（px）。 */
    data class Box(val width: Int, val heightBudget: Int)

    /**
     * 圆屏窗口框：把 [width] / [heightBudget] 再按「内接矩形」收一次。
     *
     * `frame` 是扣完「界面边距」的可用区域，`screenMinPx` 是物理屏幕短边（≈ 表盘直径）。
     * 先按 `直径 × [ROUND_FIT_MARGIN]` 当有效圆，宽度不超过它的**内接正方形**（`D / √2`），
     * 再把高度收到这个宽度下的圆内上限 `√(D² - w²)`：
     *
     * - 宽高都在圆内 → 上下两端的条目左右边缘不会再被表盘切掉；放不下的部分交给滚动区
     *   （[plan] 的第 3~6 档），总比"看着少半行"强。
     * - 宽度留 0.9 而不是直接取内接正方形，是为了在矩形屏/大表盘上不无谓地变窄。
     *
     * 380×380 + 横 5% / 纵 3% 的实测值：可用 342×357 → 常规 `307 × 336`（上下两端的左右
     * 各 110px 在圆外）→ 圆屏 `260 × 261`（四角都在圆内，余量 3%）。
     */
    fun roundBox(frame: Frame, screenMinPx: Int): Box {
        val d = (screenMinPx.coerceAtLeast(1) * ROUND_FIT_MARGIN).toInt().coerceAtLeast(1)
        val inscribed = (d / SQRT_2).toInt().coerceAtLeast(1)
        val boxWidth = width(frame).coerceAtMost(inscribed)
        val limit = kotlin.math.sqrt(
            (d.toDouble() * d - boxWidth.toDouble() * boxWidth).coerceAtLeast(0.0)
        ).toInt().coerceAtLeast(1)
        return Box(boxWidth, heightBudget(frame).coerceAtMost(limit))
    }

    /**
     * 居中的 `width × height` 矩形是否整个落在直径 `diameter` 的圆内。
     *
     * 真机上看不到圆外的东西，所以圆屏的硬判据就是 `w² + h² ≤ D²`。
     * 抽成函数是为了让 [DialogSizingTest] 能对着一串屏幕尺寸批量钉死「确实在圆内」。
     */
    fun fitsInRoundScreen(width: Int, height: Int, diameter: Int): Boolean {
        val w = width.toLong()
        val h = height.toLong()
        val d = diameter.toLong()
        return w * w + h * h <= d * d
    }

    /**
     * 选一个放得下的方案。
     *
     * <h3>为什么「舍提示行」排在「留着提示行去滚动」前面</h3>
     * 提示行（「正文较长，可上下滑动阅读」「点空白处取消」）只值一行，而差三十来像素时
     * 强行留下它，正文最后一行就会被切掉几像素——**看起来就是 bug**，用户这次反馈的正是这种
     * 「看起来被切掉」。所以只要能靠舍掉提示行换到「一个字都不切、也不用滚」，就舍。
     *
     * 只有在舍了提示行仍然放不下时，才按「提示行 → 标题行」的顺序继续让位；标题行是弹窗的身份
     * （如「隐私说明」），比提示行更该留。
     *
     * @param frame       可用区域（见 [available]）；圆屏该传 [roundBox] 收回来的框
     * @param natural     滚动区内容的自然高度（px）
     * @param parts       除滚动区外的固定部分（px）
     * @param windowWidth 窗口宽度（px），缺省 = [width]`(frame)`。圆屏要显式传 [roundBox] 的宽度，
     *                    否则这里会按 `frame.width` 重新算一遍把收窄丢掉
     */
    fun plan(frame: Frame, natural: Int, parts: Parts, windowWidth: Int = width(frame)): Plan {
        val width = windowWidth.coerceIn(1, frame.width.coerceAtLeast(1))
        val height = frame.height
        val withHint = parts.padding + parts.title + parts.divider + parts.buttonBar + parts.hint
        val withoutHint = withHint - parts.hint
        val minimal = parts.padding + parts.buttonBar

        fun fits(fixed: Int) = natural <= height - fixed
        fun roomOf(fixed: Int) = (height - fixed).coerceAtLeast(0)
        fun scrollFits(fixed: Int) = fixed + parts.minScroll <= height

        return when {
            // 1. 原样全放得下：一行都不舍
            fits(withHint) -> Plan(width, hideHint = false, hideTitle = false, scrollHeight = null)
            // 2. 舍掉提示行就能全放下：不留「差几像素」的半截行
            fits(withoutHint) -> Plan(width, hideHint = true, hideTitle = false, scrollHeight = null)
            // 3. 提示行留着、正文滚动：`choice()` 那类长正文正是提示行存在的意义
            scrollFits(withHint) -> Plan(width, hideHint = false, hideTitle = false, scrollHeight = roomOf(withHint))
            // 4. 提示行让位，才够给正文一个像样的滚动区
            scrollFits(withoutHint) -> Plan(width, hideHint = true, hideTitle = false, scrollHeight = roomOf(withoutHint))
            // 5. 连标题都让位就能全放下
            fits(minimal) -> Plan(width, hideHint = true, hideTitle = true, scrollHeight = null)
            // 6. 怎么都放不下（界面放大到 1.8 倍那种）：标题/提示全让位，正文拿走剩下全部高度
            else -> Plan(width, hideHint = true, hideTitle = true, scrollHeight = roomOf(minimal))
        }
    }
}
