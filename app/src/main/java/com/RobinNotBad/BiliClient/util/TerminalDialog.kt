package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.util.UiText

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.util.TypedValue
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.view.DialogSizing
import com.RobinNotBad.BiliClient.util.view.physicalScreenSizePx

/**
 * 弹窗的唯一构造入口（终端列表方案 B，设计稿 `docs/archive/design/dialog-redesign-v2.html`）。
 *
 * <h3>为什么有这个文件</h3>
 * 全工程曾有 **17 处**裸 `AlertDialog.Builder(...)` 直接内联在业务代码里（7 个 `setItems` 菜单型、
 * 2 个 `setSingleChoiceItems` 单选型、8 个 `setMessage` 确认型）。样式问题不在调用点，而在主题层：
 * 7 族主题此前**都没有** `alertDialogTheme`，于是弹窗走 `Theme.MaterialComponents` 自带的
 * alert overlay，而不是本主题的颜色 overlay。后果三连：
 * - 正文/标题色取自 `colorOnSurface` —— 全工程 **0 处**声明，回退到 Material 默认近白 → 「白字」
 *   （主题里的 `android:colorForeground` 救不了它，Material 正文不读 colorForeground）
 * - 破坏性按钮色取自 `colorError` —— 同样 **0 处**声明 → 删除类操作没有危险语义
 * - 按钮文字色取自 `colorAccent` —— 这是**唯一**跟随主题的一项，恰好是 `#FF6699` 荧光粉 → 「粉按钮」
 * - 背景是方角无描边 → 「像安卓原生的一样」
 *
 * <h3>修复分两层，本文件是第二层</h3>
 * 1. `themes.xml` 给 7 族主题各加 `alertDialogTheme` → `ThemeOverlay.<X>.Dialog`，补上
 *    `colorSurface`/`colorOnSurface`/`colorError` 与圆角描边背景。这一层让**已有的** 17 个裸
 *    Builder 立刻变正常，调用点零改动。
 * 2. 本文件提供统一的**终端列表**外观（等宽引导符 `›`/`▸`、无按钮行、点空白处关闭），
 *    并把「二次确认」「纯提示」也收口进来。新代码一律走这里。
 *
 * <h3>颜色从哪来</h3>
 * 全部读 `?attr/`（`colorPrimary` / `colorOnSurface` / `colorError`），由当前主题的 dialog
 * overlay 解析，因此 7 套主题各自成立，**本文件不写任何色值常量**。
 *
 * <h3>不要做的事</h3>
 * - 不要在业务代码里裸写 `AlertDialog.Builder(...).setItems(...)`，见 `AGENTS.md` 禁令。
 * - 不要把按钮行加回菜单/单选：手表 300×300 表盘上纵向空间比一个「确定」值钱。
 *
 * 纯视图构造，无网络、不持有 Context 引用（Dialog 自己持有）。会**读**（不写）用户的两项界面
 * 设置：`SettingsKeys.PADDING_H/PADDING_V`（「界面边距」，与宿主页面同一套安全区，见
 * [Sheet.prepareLayout]）。尺寸预算的算术在 `util/view/DialogSizing.kt`（纯函数，有 JVM 单测）
 * ——「低分辨率设备上弹窗显示不全」（26.10.20 用户反馈）的根因就在那几行。
 */
object TerminalDialog {

    /** 未选中项的引导符。 */
    const val GLYPH_IDLE = "›"

    /** 选中项的引导符。与未选中项区分，让「当前选的是哪个」一眼可见。 */
    const val GLYPH_ACTIVE = "▸"

    /** 单选型右侧的选中标记。与左侧引导符构成双重指示。 */
    const val GLYPH_CHECK = "✓"

    /**
     * 菜单型：一串「点一下执行一个动作」的条目。
     *
     * 对应原来的 `AlertDialog.Builder(context).setItems(...)` 7 处调用点。
     *
     * @param title  标题；null 或空则不显示标题行
     * @param items  条目文字，保持调用方原有顺序与措辞，不要在这里改写业务文案
     * @param danger 哪些下标是破坏性操作（删除/取消收藏之类），用 `colorError` 着色
     * @param onPick 选中回调，参数是条目下标
     */
    fun menu(
        context: Context,
        title: String? = null,
        items: List<String>,
        danger: Set<Int> = emptySet(),
        onPick: (Int) -> Unit
    ): AlertDialog {
        val sheet = Sheet(context, title)

        items.forEachIndexed { index, text ->
            val on = index in danger
            val row = sheet.addItem(text, danger = on) {
                // 先关再回调：回调里常会再弹一个框（「删除评论」→ 二次确认），
                // 不关的话两个弹窗会叠在一起。原裸 Builder 由系统自动 dismiss，行为一致。
                sheet.dialog.dismiss()
                onPick(index)
            }
            // 菜单项没有「选中」概念，引导符一律用未选中态：次级灰。
            // 不能沿用布局默认的 ?attr/colorPrimary，否则每一项都挂个主题色箭头，比原来还花。
            row.glyph.text = GLYPH_IDLE
            if (!on) {
                row.glyph.setTextColor(sheet.secondaryColor())
                row.text.setTextColor(sheet.onSurfaceColor())
            }
        }

        sheet.setHint(UiText.of("点空白处取消"))
        sheet.prepareLayout()
        return sheet.dialog
    }

    /**
     * 单选型：带 `▸`/`›` 双态引导符 + 主色文字的选择列表。
     *
     * 对应原来的 `setSingleChoiceItems` 2 处调用点（选季 / 选集）。系统单选样式在这个深色主题下
     * 选中态几乎不可见，这里改成两重指示。
     *
     * **不自动关闭**：选集场景的回调要先做越界校验再自行决定关不关
     * （见 `BangumiInfoFragment` 拒绝切到空季的逻辑），所以由调用方 dismiss。
     *
     * @param checked 初始选中下标，越界会被夹到合法范围
     * @param onPick  选中回调；如需改变选中态，调 [Sheet.setChecked] 或重新 [paintSelection]
     */
    fun singleChoice(
        context: Context,
        title: String? = null,
        items: List<String>,
        checked: Int = 0,
        onPick: (Sheet, Int) -> Unit
    ): AlertDialog {
        val sheet = Sheet(context, title)
        items.forEach { text -> sheet.addItem(text) {} }
        sheet.rows.forEachIndexed { index, row ->
            row.root.setOnClickListener { onPick(sheet, index) }
        }
        sheet.paintSelection(checked.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        sheet.setHint(UiText.of("点空白处取消"))
        sheet.prepareLayout()
        return sheet.dialog
    }

    /**
     * 确认型：一句话 + 危险操作。
     *
     * 对应原来的 8 处 `setMessage` + `setPositiveButton`。原实现里「取消」与「删除」同为
     * `colorAccent` 粉，误触代价看起来一样；这里取消走次级灰、确认走 `colorError`。
     *
     * **保留按钮行**：终端列表方案在菜单/单选上去掉了按钮行，但确认框没有按钮就没有明确的
     * 「确认/放弃」语义，必须留。两个按钮都撑到 44dp 触控下限。
     *
     * @param confirmIsDanger true 时确认按钮用危险色；纯提示型传 false
     */
    fun confirm(
        context: Context,
        title: String? = null,
        message: CharSequence,
        confirmText: String = UiText.of("确定"),
        cancelText: String = UiText.of("取消"),
        confirmIsDanger: Boolean = true,
        onConfirm: () -> Unit
    ): AlertDialog {
        val sheet = Sheet(context, title)
        sheet.addMessage(message)

        if (cancelText.isNotEmpty()) {
            sheet.addButton(cancelText, sheet.secondaryColor()) { sheet.dialog.dismiss() }
        }
        sheet.addButton(
            confirmText,
            if (confirmIsDanger) sheet.errorColor() else sheet.primaryColor()
        ) {
            sheet.dialog.dismiss()
            onConfirm()
        }

        sheet.prepareLayout()
        return sheet.dialog
    }

    /**
     * 两选一，**两个按钮各带自己的回调**。
     *
     * 与 [confirm] 的唯一区别就在这里：[confirm] 的「取消」被写死成单纯 `dismiss()`，
     * 调用方没法在「用户选了取消」时做别的事。用 `setOnDismissListener` 也补不了——
     * `confirm` 内部是先 `dismiss()` 再 `onConfirm()`，dismiss 回调触发时还拿不到
     * 「用户其实点了确定」这个信息，两边都会走一遍。
     *
     * 场景：启动时的隐私说明确认。「不同意」不是「取消这个操作」，而是一次同样有效的
     * 回答（要继续启动流程、只是不上报），所以必须能挂回调。
     *
     * 按钮顺序与 [confirm] 一致：[secondaryText] 在左、[primaryText] 在右。
     * 调用方若想禁止返回键/点空白关掉，自己 `setCancelable(false)`。
     *
     * <p>按钮用**固定底栏**（滚动区外面）：这个方法的正文通常偏长，按钮跟着正文滚就会被顶到
     * 看不见的地方。配上 `setCancelable(false)` 就是死锁——用户既关不掉弹窗也点不到按钮。
     * 滚动区的高度由 [Sheet.prepareLayout] 按「屏幕装得下」算（26.10.20 之前是硬钉屏高 45%，
     * 小屏上标题 + 45% + 底栏 + 提示行必然超屏，正是「低分辨率设备弹窗显示不全」的成因之一）。
     */
    fun choice(
        context: Context,
        title: String? = null,
        message: CharSequence,
        primaryText: String,
        secondaryText: String,
        hint: String? = null,
        onPrimary: () -> Unit,
        onSecondary: () -> Unit
    ): AlertDialog {
        val sheet = Sheet(context, title, bottomButtonBar = true)
        sheet.addMessage(message)
        sheet.addButton(secondaryText, sheet.secondaryColor()) {
            sheet.dialog.dismiss()
            onSecondary()
        }
        sheet.addButton(primaryText, sheet.primaryColor()) {
            sheet.dialog.dismiss()
            onPrimary()
        }
        // 内容超出滚动区时给一句提示：滚动条是隐藏的（scrollbars="none"），
        // 不提示的话用户只会看到一段被截断的文字。
        if (!hint.isNullOrEmpty()) sheet.setHint(hint)
        sheet.prepareLayout()
        return sheet.dialog
    }

    /**
     * 输入型：一个多行输入框 + 「取消 / 发送」按钮（26.10.16 新增）。
     *
     * 场景：反馈详情页的「追加说明」。收在这里而不是让调用方裸写
     * `AlertDialog.Builder(...).setView(EditText(...))` —— 那样又要各自处理主题色、键盘弹出、
     * 按钮位置这三件重复的事，正是本文件存在的意义（见类注释）。
     *
     * **按钮必须走固定底栏**（[Sheet] 的 `bottomButtonBar = true`）：输入框会随内容长高，
     * 按钮跟在正文后面会被顶出可视区，而这时用户正打着字。
     *
     * @param maxLength 0 表示不限
     * @param digitsOnly 只允许输入数字（内测码等纯数字输入用）；会同时换数字键盘并过滤粘贴内容
     * @param onConfirm 回调拿到的是**已 trim 过**的内容；内容为空时既不回调也不关窗
     */
    fun input(
        context: Context,
        title: String? = null,
        hint: String? = null,
        initialText: String = "",
        maxLength: Int = 0,
        digitsOnly: Boolean = false,
        confirmText: String = UiText.of("发送"),
        cancelText: String = UiText.of("取消"),
        onConfirm: (String) -> Unit
    ): AlertDialog {
        val sheet = Sheet(context, title, bottomButtonBar = true)
        val edit = sheet.addInput(hint, initialText, maxLength)

        if (digitsOnly) {
            // 数字键盘只能挡住软键盘，粘贴/外接键盘照样能进非数字字符，所以要再加一层 InputFilter。
            edit.inputType = InputType.TYPE_CLASS_NUMBER
            val filters = ArrayList<InputFilter>(2)
            if (maxLength > 0) filters.add(InputFilter.LengthFilter(maxLength))
            filters.add(InputFilter { source, start, end, _, _, _ ->
                val kept = StringBuilder(end - start)
                for (i in start until end) {
                    val c = source[i]
                    if (c in '0'..'9') kept.append(c)
                }
                // 返回 null 表示原文照收；有字符被过滤掉才返回新串
                if (kept.length == end - start) null else kept.toString()
            })
            edit.filters = filters.toTypedArray()
        }

        if (cancelText.isNotEmpty()) {
            sheet.addButton(cancelText, sheet.secondaryColor()) { sheet.dialog.dismiss() }
        }
        sheet.addButton(confirmText, sheet.primaryColor()) {
            val text = edit.text?.toString()?.trim().orEmpty()
            // 空内容不关窗也不回调：用户多半是手滑点了发送，把输入框一起关掉就得重打一遍
            if (text.isEmpty()) return@addButton
            sheet.dialog.dismiss()
            onConfirm(text)
        }

        sheet.prepareLayout()
        // 输入型不走「钉高度」那套：内容只有一个输入框，钉死会在下面留一大片空白。
        // prepareForTyping 会把滚动区改回 wrap_content（输入框自身 maxLines = 6 兜底）。
        sheet.prepareForTyping(edit)
        return sheet.dialog
    }

    /** 纯提示型（只有一个按钮），如「该季暂无剧集」。 */
    fun alert(
        context: Context,
        title: String? = null,
        message: CharSequence,
        buttonText: String = UiText.of("知道了")
    ): AlertDialog = confirm(
        context = context,
        title = title,
        message = message,
        confirmText = buttonText,
        cancelText = "",
        confirmIsDanger = false,
        onConfirm = {}
    )

    /**
     * 一个弹窗的可变状态。
     *
     * 公开出来只为让 [singleChoice] 的调用方能在回调里改选中态 —— 其余场景不需要碰它。
     */
    class Sheet internal constructor(
        context: Context,
        title: String?,
        /**
         * 按钮是否放到滚动区**外面**的固定底栏。
         *
         * <p>默认 false = 老行为：按钮跟在正文末尾、随正文一起滚。菜单/单选/确认框的正文
         * 都很短，用不着改。true 给 [TerminalDialog.choice] 这类「正文很长 + 按钮必须能点到」
         * 的场景用——长正文会把按钮顶到看不见的地方，而这类弹窗又不允许返回键关掉，
         * 点不到按钮就是用户彻底卡住。
         */
        private val bottomButtonBar: Boolean = false
    ) {

        val dialog: AlertDialog = AlertDialog.Builder(context).create()
        val rows = ArrayList<Row>()

        private val inflater = LayoutInflater.from(context)
        private val content: LinearLayout =
            inflater.inflate(R.layout.layout_dialog_terminal, null) as LinearLayout
        private val container: LinearLayout = content.findViewById(R.id.terminal_dialog_list)
        private val scroll: ScrollView = content.findViewById(R.id.terminal_dialog_scroll)
        private val titleView: TextView = content.findViewById(R.id.terminal_dialog_title)
        private val divider: View = content.findViewById(R.id.terminal_dialog_divider)
        private val hintView: TextView = content.findViewById(R.id.terminal_dialog_hint)
        private val buttonBar: LinearLayout = content.findViewById(R.id.terminal_dialog_buttons)

        /** 输入型弹窗的输入框；非空 = 「打字模式」，滚动区不钉高度（见 [prepareForTyping]）。 */
        private var typingEdit: EditText? = null

        init {
            // 面板（圆角 + 1dp 描边）只由 content 根视图画**一次**：
            // - 窗口自带的那层底衬必须清成透明，否则 AppCompat 会垫一层方角底衬，把圆角整个盖住
            //   （这正是用户说的「像安卓原生的一样」的直接原因）；
            // - 不再走主题 overlay 的 android:background：那个属性会被框架贴到 window 里的
            //   **每一个**内部视图上。ColorOS Watch（OPPO 手表）的弹窗 DecorView 末尾还有一个
            //   SwipeGlowOverlay，它也被贴上同一份面板 → 整块面板盖在内容**上面**，用户看到的
            //   就是「只有一个方块、一个字都没有」（26.10.25 OPPO Watch X2 Mini 反馈，
            //   诊断包视图树见 docs/review/fix-progress.md）。
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            content.background =
                AppCompatResources.getDrawable(dialog.context, R.drawable.dialog_background)
            dialog.setView(content, 0, 0, 0, 0)
            dialog.setCanceledOnTouchOutside(true)
            // 只调 setCanceledOnTouchOutside 不够：窗口除了 DIM_BEHIND 没有 WATCH_OUTSIDE_TOUCH，
            // 系统根本不会把「点窗口外」的 ACTION_OUTSIDE 送进来，等于点空白处永远没反应。
            // 这句有意义的前提是**窗口真的比屏幕窄**——宽度由 prepareLayout() 负责收窄。
            // （26.10.20 加了这句，可当时窗口仍是整屏宽，提示行「点空白处取消」成了空话。）
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            buttonBar.visibility = if (bottomButtonBar) View.VISIBLE else View.GONE

            if (title.isNullOrEmpty()) {
                titleView.visibility = View.GONE
                divider.visibility = View.GONE
            } else {
                titleView.text = "$GLYPH_IDLE $title"
                titleView.visibility = View.VISIBLE
                divider.visibility = View.VISIBLE
            }
            // 先按「空列表」算一次：宽度立刻落地；工厂方法加完条目后会再算一次高度。
            prepareLayout()

            // **真正生效的是 show() 之后这一次**：appcompat 的 AlertDialog 在 onCreate 里
            // installContent() 会把自定义视图重新 addView 进 customPanel（MATCH_PARENT×MATCH_PARENT），
            // 把 init 里落的宽度覆盖回整屏宽 —— 380×380 实测 show() 之前 `dumpsys window` 一直是
            // `mAttrs=(wrapxwrap)`、content 仍是 380 宽，`setLayout()` 写了等于没写。
            // onShowListener 在窗口 attach 完、第一帧 layout 之前回调，这里再落一次同一个尺寸，
            // 第一帧就是对的（不会先画一帧整屏宽再跳回来）。
            dialog.setOnShowListener {
                prepareLayout()
                // 输入型：弹窗显示前 View 还没 attach，init 里那次 requestFocus 不一定生效，
                // 表现为「弹窗出来了但键盘不弹、光标也不在框里」。
                typingEdit?.let { edit ->
                    edit.requestFocus()
                    edit.setSelection(edit.text?.length ?: 0)
                }
                // 等第一帧的窗口层次齐了再清：ROM 有时在 show 之后才往 DecorView 里挂
                // SwipeGlowOverlay 这类层（见 stripForeignBackgrounds 的说明）。
                dialog.window?.decorView?.post { stripForeignBackgrounds() }
            }
        }

        /**
         * 面板只允许由 [content]（`layout_dialog_terminal` 根视图）画**一次**。
         *
         * <p>把弹窗窗口里**不属于我们内容树**的视图背景一律清成 null。原因是 ColorOS Watch
         * （OPPO 手表）会往弹窗 DecorView 末尾挂一个 `SwipeGlowOverlay`，而框架把主题的
         * `android:background` 贴到了窗口里每一个内部视图上 —— 那个 overlay 于是也画一份不透明
         * 面板，且排在内容之后（= 画在最上层），用户看到的就是「只有一个方块、一个字都没有」。
         *
         * <p>清掉之后：内容树自己的背景（[content] 的面板 + 各按钮的 Ripple 等）不受影响，
         * 后面那些装饰层变成纯透明，不再遮挡。
         */
        private fun stripForeignBackgrounds() {
            val decor = dialog.window?.decorView as? ViewGroup ?: return
            fun walk(v: View) {
                if (v === content) return
                if (v is ViewGroup) {
                    for (i in 0 until v.childCount) walk(v.getChildAt(i))
                }
                v.background = null
            }
            for (i in 0 until decor.childCount) walk(decor.getChildAt(i))
        }

        /**
         * 量一次、把尺寸落下去：窗口宽度 + 滚动区高度 + 该舍哪几行。
         *
         * **调用时机**：`TerminalDialog` 的每个工厂方法在 `return` 前都会调一次（条目、按钮、
         * 提示行都加完之后）。`init` 里也先调一次，只为让宽度立刻生效。
         *
         * **为什么不等 `show()` 之后再调**：那时窗口早已被系统裁到屏幕内，量出来的固定行高度是
         * 被挤过的（380×380 上提示行只剩 6px 那种），按它算预算必然少算；而且第一帧会先画出被切
         * 的界面，低端手表上那一帧能停几百毫秒。View 在 attach 之前就能 `measure()`，不必等 show。
         */
        internal fun prepareLayout() {
            val screen = physicalScreenSizePx(dialog.context)
            val frame = DialogSizing.available(
                screen[0],
                screen[1],
                SharedPreferencesUtil.getInt(SettingsKeys.PADDING_H, 0),
                SharedPreferencesUtil.getInt(SettingsKeys.PADDING_V, 0)
            )
            // 圆屏适配打开时再收一次：0.9 × 0.94 的框在圆形表盘上四角落在圆外，
            // 条目越多、弹窗越高，被表盘切掉的越多（见 DialogSizing.roundBox）。
            val box = if (SharedPreferencesUtil.getBoolean(SettingsKeys.UI_ROUND, false))
                DialogSizing.roundBox(frame, minOf(screen[0], screen[1]))
            else
                DialogSizing.Box(DialogSizing.width(frame), DialogSizing.heightBudget(frame))
            // 高度只给预算（可用高度 × 94%），上下各留一点能「点外面取消」的空白。
            val budget = DialogSizing.Frame(box.width, box.heightBudget)
            applyPlan(
                box.width,
                DialogSizing.plan(budget, naturalScrollHeight(box.width), measureParts(box.width), box.width)
            )
        }

        private fun applyPlan(winW: Int, plan: DialogSizing.Plan) {
            // 宽度**必须**落在 content 自己的 layoutParams 上：窗口是 WRAP_CONTENT，会跟着收窄。
            // 光调 `window.setLayout()` 不够 —— appcompat 会把它重置回 wrap×wrap（见 init 里的说明），
            // 所以 init 里落一次、`onShowListener` 里再落一次，两次都是同一个宽度。
            val current = content.layoutParams
            if (current is FrameLayout.LayoutParams) {
                current.width = winW
                current.height = ViewGroup.LayoutParams.WRAP_CONTENT
            } else {
                content.layoutParams =
                    FrameLayout.LayoutParams(winW, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            content.requestLayout()
            // 双保险：show() 之后 `setLayout()` 是有效的，窗口不会再被撑成整屏宽。
            dialog.window?.setLayout(winW, ViewGroup.LayoutParams.WRAP_CONTENT)

            if (plan.hideHint) hintView.visibility = View.GONE
            if (plan.hideTitle) {
                titleView.visibility = View.GONE
                divider.visibility = View.GONE
            }
            // 打字模式下滚动区按内容收着（输入框自己 maxLines = 6 兜底），不钉高度。
            val target = if (typingEdit != null) {
                ViewGroup.LayoutParams.WRAP_CONTENT
            } else {
                plan.scrollHeight ?: ViewGroup.LayoutParams.WRAP_CONTENT
            }
            scroll.layoutParams = scroll.layoutParams.apply { height = target }
            scroll.requestLayout()
            fitButtonBars(winW)
        }

        /**
         * 按钮行的兜底：放不下就把按钮缩回去。
         *
         * 这不是「难看」问题而是「点不到」问题——每个按钮都带 15dp 左右内边距与 44dp 的
         * `minimumWidth`，把「界面大小」调到 1.8 倍后单个三字按钮就 234px 宽，两个「不同意 /
         * 同意」合计 468px > 窗口 342px，右对齐的那个会被整个挤出屏幕。隐私说明弹窗
         * `setCancelable(false)`，主按钮点不到 = 用户在闪屏彻底卡死（26.10.20 实测）。
         *
         * 只对**按钮行**生效，不动列表项与正文：那两处的字号是排版的一部分，缩了就难读。
         */
        private fun fitButtonBars(winW: Int) {
            val bars = ArrayList<LinearLayout>(2)
            if (bottomButtonBar) bars.add(buttonBar)
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is LinearLayout && child.tag == BUTTON_BAR_TAG) bars.add(child)
            }
            for (bar in bars) fitButtonBar(bar, winW)
        }

        private fun fitButtonBar(bar: LinearLayout, winW: Int) {
            if (bar.childCount == 0) return
            // 两条按钮行的横向内边距都是 `dialog_item_padding_h`（底栏在 XML 里、滚动区内的行在
            // 代码里）。**不能读 `paddingLeft`**：`onShowListener` 跑在第一次测量之前，XML 里的
            // `paddingStart` 还没解析到 `paddingLeft` 上，读到 0 就会少算 30dp、按钮缩不到位。
            val inner = winW - dimen(R.dimen.dialog_item_padding_h) * 2
            if (inner <= 0) return
            // 下限 9sp：再小就点不准了（按钮高度仍是 44dp 级，触控区不受影响）。
            val floor = 9f * dialog.context.resources.displayMetrics.scaledDensity
            var relaxed = false
            repeat(6) {
                if (barDesiredWidth(bar) <= inner) return
                var shrunk = false
                for (i in 0 until bar.childCount) {
                    val label = bar.getChildAt(i) as? TextView ?: continue
                    if (!relaxed) {
                        // 内边距与 44dp 最小宽度也得一起松：只缩字号的话，这两项还把宽度钉在原地。
                        val pad = dimen(R.dimen.dialog_item_padding_h) / 2
                        label.setPadding(pad, 0, pad, 0)
                        label.minimumWidth = 0
                    }
                    // 注意 `TextView.textSize` 的 setter 走的是 **sp**（`setTextSize(float)` 的隐藏语义），
                    // 而 getter 返回 **px** —— 想按像素缩必须显式指定 COMPLEX_UNIT_PX。
                    if (label.textSize > floor) {
                        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, label.textSize * 0.9f)
                        shrunk = true
                    }
                }
                relaxed = true
                if (!shrunk) {
                    // 字号到了 9sp 还放不下（1.8 倍界面 + 两个三字按钮就是这种）：改成均分宽度。
                    // 比继续压字号好读，也不会像「把内边距清零」那样让两个按钮贴在一起。
                    shareBarWidth(bar)
                    return
                }
            }
            shareBarWidth(bar)
        }

        /** 按钮行里所有按钮按单行排开需要的宽度之和（px）。 */
        private fun barDesiredWidth(bar: LinearLayout): Int {
            var total = 0
            for (i in 0 until bar.childCount) {
                val child = bar.getChildAt(i)
                child.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                total += child.measuredWidth
            }
            return total
        }

        /** 放不下时的最终兜底：按钮均分整行宽度，内边距清零（否则半个格子的宽度还不够三个字）。 */
        private fun shareBarWidth(bar: LinearLayout) {
            for (i in 0 until bar.childCount) {
                val child = bar.getChildAt(i)
                val lp = child.layoutParams as? LinearLayout.LayoutParams ?: continue
                if (lp.width == 0 && lp.weight == 1f) continue
                lp.width = 0
                lp.weight = 1f
                (child as? TextView)?.setPadding(0, 0, 0, 0)
            }
            bar.requestLayout()
        }

        /** 滚动区内容按最终宽度量出来的自然高度（px）。 */
        private fun naturalScrollHeight(winW: Int): Int {
            val inner = (winW - content.paddingLeft - content.paddingRight -
                scroll.paddingLeft - scroll.paddingRight).coerceAtLeast(0)
            container.measure(
                View.MeasureSpec.makeMeasureSpec(inner, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            return container.measuredHeight + scroll.paddingTop + scroll.paddingBottom
        }

        /** 除滚动区以外的固定行各有多高（px）。GONE 的一律算 0。 */
        private fun measureParts(winW: Int): DialogSizing.Parts {
            val inner = (winW - content.paddingLeft - content.paddingRight).coerceAtLeast(0)
            val widthSpec = View.MeasureSpec.makeMeasureSpec(inner, View.MeasureSpec.EXACTLY)
            fun heightOf(view: View): Int {
                if (view.visibility == View.GONE) return 0
                view.measure(
                    widthSpec,
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                return view.measuredHeight
            }
            return DialogSizing.Parts(
                padding = content.paddingTop + content.paddingBottom,
                title = heightOf(titleView),
                divider = heightOf(divider),
                buttonBar = heightOf(buttonBar),
                hint = heightOf(hintView),
                minScroll = dimen(R.dimen.dialog_item_min_height)
            )
        }

        /** 加一个条目。返回行对象，方便调用方改引导符/文字。 */
        internal fun addItem(
            text: CharSequence,
            danger: Boolean = false,
            onClick: () -> Unit
        ): Row {
            val root = inflater.inflate(R.layout.item_dialog_terminal, container, false)
            val row = Row(
                root,
                root.findViewById(R.id.terminal_item_glyph),
                root.findViewById(R.id.terminal_item_text),
                root.findViewById(R.id.terminal_item_state)
            )
            row.text.text = text
            if (danger) {
                row.text.setTextColor(errorColor())
                row.glyph.setTextColor(errorColor())
            }
            root.setOnClickListener { onClick() }
            container.addView(root)
            rows.add(row)
            return row
        }

        /** 加一段说明文字（确认型用）。不显示引导符。 */
        internal fun addMessage(message: CharSequence) {
            val row = addItem(message, onClick = {})
            row.glyph.visibility = View.GONE
            row.text.maxLines = Int.MAX_VALUE
            row.text.setTextColor(secondaryColor())
            // 说明文字不是可选项：撤掉点击回调与按压反馈，避免看起来像能点
            row.root.setOnClickListener(null)
            row.root.isClickable = false
            row.root.isFocusable = false
            row.root.background = null
        }

        /**
         * 加一个多行输入框。给 [TerminalDialog.input] 用。
         *
         * 外观自己画而不是沿用主题的 `editTextStyle`：7 套主题各有一份
         * `ThemeOverlay.<X>.Dialog`，但都**没有**声明 `editTextBackground`，交给框架解析会落到
         * Material 的默认值上（深色主题里就是一条近白的下划线，与本文件的整体观感不搭）。
         * 这里只从 `?attr/colorOnSurface` 取色，与文件其它部分同一约定。
         */
        internal fun addInput(hint: String?, initial: String, maxLength: Int): EditText {
            val edit = EditText(dialog.context).apply {
                setText(initial)
                this.hint = hint
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                gravity = Gravity.TOP or Gravity.START
                textSize = 13f
                // minLines 给足打字时的视野；maxLines 是**弹窗不被撑出屏幕**的保证
                // （配合 prepareForTyping 把滚动区改回 wrap_content，见那里的说明）
                minLines = 3
                maxLines = 6
                setTextColor(onSurfaceColor())
                setHintTextColor(secondaryColor())
                background = inputBackground()
                val pad = dimen(R.dimen.dialog_item_padding_h)
                setPadding(pad, pad, pad, pad)
            }
            if (maxLength > 0) edit.filters = arrayOf(InputFilter.LengthFilter(maxLength))
            container.addView(edit)
            return edit
        }

        /** 输入框底：`colorOnSurface` 压到 10% 透明度的圆角块（深浅主题各自成立）。 */
        private fun inputBackground(): Drawable {
            val base = onSurfaceColor()
            val alpha = (Color.alpha(base) * 0.10f).toInt().coerceIn(0, 255)
            return GradientDrawable().apply {
                setColor((base and 0x00FFFFFF) or (alpha shl 24))
                cornerRadius = 8f * dialog.context.resources.displayMetrics.density
            }
        }

        /**
         * 打字前的准备：把滚动区改回 `wrap_content` + 弹起软键盘 + 聚焦到文末。
         *
         * **为什么改回 wrap**：[prepareLayout] 会把滚动区钉成「屏幕装得下」的固定高度，那是为
         * 「条目/正文多到撑破表盘」准备的；输入型弹窗的内容只有一个输入框，钉死会在下面留一大片
         * 空白。改回 `wrap_content` 的安全性由输入框自身的 `maxLines = 6` 保证 —— 内容再多也是
         * 输入框内部滚动，不会把弹窗撑出屏幕。
         */
        internal fun prepareForTyping(edit: EditText) {
            typingEdit = edit
            // 重新落一次尺寸：此时 typingEdit 非空，applyPlan 会把滚动区改回 wrap_content。
            prepareLayout()
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            edit.requestFocus()
            edit.setSelection(edit.text?.length ?: 0)
            // show() 之后的那次再补一遍焦点，在 init 的 onShowListener 里统一处理（见那里）。
        }

        /** 加一个右对齐的文字按钮（确认型用）。 */
        internal fun addButton(label: String, color: Int, onClick: () -> Unit) {
            val target = if (bottomButtonBar) {
                // 固定底栏：滚动区外面，永远可见（见构造参数 bottomButtonBar 的说明）
                buttonBar
            } else {
                val bar = container.getChildAt(container.childCount - 1) as? LinearLayout
                if (bar?.tag == BUTTON_BAR_TAG) {
                    bar
                } else {
                    LinearLayout(dialog.context).apply {
                        tag = BUTTON_BAR_TAG
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.END
                        setPadding(
                            dimen(R.dimen.dialog_item_padding_h), 0,
                            dimen(R.dimen.dialog_item_padding_h), 0
                        )
                    }.also { container.addView(it) }
                }
            }

            target.addView(
                TextView(dialog.context).apply {
                    text = label
                    setTextColor(color)
                    textSize = 12.5f
                    gravity = Gravity.CENTER
                    minHeight = dimen(R.dimen.dialog_item_min_height)
                    minimumWidth = dimen(R.dimen.dialog_item_min_height)
                    setPadding(
                        dimen(R.dimen.dialog_item_padding_h), 0,
                        dimen(R.dimen.dialog_item_padding_h), 0
                    )
                    isClickable = true
                    isFocusable = true
                    background = selectableItemBackground()
                    setOnClickListener { onClick() }
                }
            )
        }

        internal fun setHint(hint: String) {
            hintView.text = hint
            hintView.visibility = View.VISIBLE
            // 提示行本身也点得动：表盘上弹窗左右两侧留给「点外面」的空白只有二十来像素，
            // 用户更可能直接点这句「点空白处取消」，点它就当取消。
            hintView.setOnClickListener { dialog.dismiss() }
        }

        /** 重绘单选态：选中项 `▸` + 主色 + 右侧 ✓，未选中 `›` + 次级灰。 */
        fun paintSelection(selected: Int) {
            rows.forEachIndexed { index, row ->
                val on = index == selected
                row.glyph.text = if (on) GLYPH_ACTIVE else GLYPH_IDLE
                if (on) {
                    row.glyph.setTextColor(primaryColor())
                    row.text.setTextColor(primaryColor())
                } else {
                    row.glyph.setTextColor(secondaryColor())
                    row.text.setTextColor(onSurfaceColor())
                }
                // 右侧 ✓：双重指示，扫一眼就知道选的是哪个（设计稿方案 B 改法）
                row.state.text = if (on) GLYPH_CHECK else ""
            }
        }

        internal fun primaryColor() = attrColor(androidx.appcompat.R.attr.colorPrimary)

        internal fun onSurfaceColor() = attrColor(com.google.android.material.R.attr.colorOnSurface)

        internal fun errorColor() = attrColor(com.google.android.material.R.attr.colorError)

        /**
         * 次级文字色：Material 没给 dialog 专用的次级色，用 onSurface 压到 70% 透明度得到。
         * 对深色底仍远高于 AA 的 4.5:1。
         */
        internal fun secondaryColor(): Int {
            val base = onSurfaceColor()
            val alpha = (Color.alpha(base) * 0.7f).toInt().coerceIn(0, 255)
            return (base and 0x00FFFFFF) or (alpha shl 24)
        }

        private fun dimen(resId: Int) = dialog.context.resources.getDimensionPixelSize(resId)

        private fun selectableItemBackground() = dialog.context.obtainStyledAttributes(
            intArrayOf(android.R.attr.selectableItemBackground)
        ).let { ta ->
            val drawable = ta.getDrawable(0)
            ta.recycle()
            drawable
        }

        /**
         * 取主题属性色。
         *
         * **必须给默认值**：`colorOnSurface` / `colorError` 是本次新补的属性，万一日后新增主题
         * 漏了 overlay，拿不到值时抛异常不如退回可读的兜底色。
         */
        private fun attrColor(attr: Int): Int {
            val ta = dialog.context.obtainStyledAttributes(intArrayOf(attr))
            val color = ta.getColor(0, Color.WHITE)
            ta.recycle()
            return color
        }

        private companion object {
            const val BUTTON_BAR_TAG = "terminal_dialog_button_bar"
        }
    }

    /** 一个条目的三个可变部位。 */
    class Row internal constructor(
        val root: View,
        val glyph: TextView,
        val text: TextView,
        val state: TextView
    )
}