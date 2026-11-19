package com.RobinNotBad.BiliClient.player

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.ui.widget.HighEnergyProgressBar
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil

/**
 * 新版播放器控制层。按钮仍使用原 View，重复槽位与网格项才创建镜像并转发点击。
 * Activity 只提供原业务回调；本类不持有播放器、网络请求或分 P 数据。
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class PlayerNewUiController(
    private val activity: Activity,
    private val root: ViewGroup,
    private val pool: ViewGroup,
    private val isRound: Boolean,
    private val oldControl: View,
    private val top: View,
    private val oldBottom: View,
    private val oldLeft: View,
    private val oldRight: View,
    private val play: ImageButton,
    private val seekbar: HighEnergyProgressBar,
    private val progressText: TextView,
    private val primaryOf: (String) -> View?,
    private val showQuality: () -> Unit,
    private val showPages: () -> Unit,
    /** 清晰度槽位要显示的文字（「720P」）；拿不到时退回原图标。 */
    private val qualityLabel: () -> String?,
    /** 某个按钮当前是否处于「开启」态，「更多」面板据此把那一格染成 #FF8CB0。 */
    private val slotIsOn: (String) -> Boolean,
    /** 整屏弹层开合：弹幕是独立 surface，普通 View 的遮罩盖不住它，只能让它自己藏起来。 */
    private val onPanelShown: (Boolean) -> Unit,
    /** 弧形进度条拖动开始：置 isSeeking，压住进度回刷。 */
    private val onArcSeekStart: () -> Unit,
    /** 弧形进度条拖动结束：走与原 SeekBar 松手完全相同的 seek 链路。 */
    private val onArcSeekStop: (Int) -> Unit,
    /** 当前是否在播放：中心键据此在「播放/暂停」两个设计稿图标之间切换。 */
    private val playingNow: () -> Boolean,
    /** 弹幕是否可见：可见时弹幕按钮像设计稿那样染成 #FF8CB0。 */
    private val danmakuNow: () -> Boolean,
) {
    private val overlay = FrameLayout(activity)
    private val buttons = FrameLayout(activity)
    private val gridHost = FrameLayout(activity)
    private val grid = LinearLayout(activity)
    private val gridTitle = TextView(activity)
    private val gridHint = TextView(activity)
    private val gridDots = FrameLayout(activity)
    /** 翻页手势刚翻过页时置真：随后的那次 click 不该把面板关掉。 */
    private var gridFlinged = false
    private val listHost = FrameLayout(activity)
    private val arc: PlayerArcProgressView?
    private val idleTrack: View
    private val idleFill: View
    private val elapsed = TextView(activity)
    private val duration = TextView(activity)
    /** 锁定态整层（设计稿 lockView）：顶部胶囊提示 + 锁图标底下的 34dp 圆底。 */
    private val lockLayer = FrameLayout(activity)
    private val lockHint = TextView(activity)
    private val lockRing = View(activity)
    /** 拖动进度时的预览层（设计稿 seek 态）：压暗铺在最底，三行文字浮在最上。 */
    private val seekDim = View(activity)
    private val seekText = FrameLayout(activity)
    private val seekDelta = TextView(activity)
    private val seekMain = TextView(activity)
    private val seekTotal = TextView(activity)
    private val mirrors = ArrayList<Pair<View, View>>()

    /**
     * 设计稿强调色 #FF8CB0。**必须声明在这里**：init 块里的 buildSeekText() 要用它，
     * 而 Kotlin 的字段按源码顺序初始化，声明在后面的属性那时还是 0（等于全透明）。
     */
    private val accentSoft = 0xFFFF8CB0.toInt()

    /** 槽位按钮 → key。Activity 会随时改这些按钮的图标（弹幕开关、锁定、播放暂停），
     * 靠这张表把它们重新拉回设计稿那一套矢量图标。 */
    private val designViews = LinkedHashMap<View, String>()

    /** 上一次由本类设置的 drawable 实例，用来分辨「还是我设的」与「Activity 又改回去了」。 */
    private val appliedIcon = HashMap<View, android.graphics.drawable.Drawable?>()
    private var layout = PlayerUiLayout.DEFAULT
    private var round = isRound
    private var shown = true
    private var gridKeys = listOf<String>()
    private var gridPage = 0
    private var arcDragging = false
    private var density = activity.resources.displayMetrics.density

    init {
        // 设计稿的画面外是纯黑：Activity 本身是沉浸式透明背景，不铺一层黑就会透出桌面壁纸。
        root.setBackgroundColor(Color.BLACK)
        oldBottom.visibility = View.GONE
        oldLeft.visibility = View.GONE
        oldRight.visibility = View.GONE
        // 新版的控件全由 overlay 管理，不能再让旧父容器一起 GONE。
        detach(top)
        detach(play)
        detach(seekbar)
        // 圆屏顶栏是「时钟一行 + 标题一行」，36dp 装不下，按设计稿给到 46dp。
        overlay.addView(top, FrameLayout.LayoutParams(-1, dp(if (round) 46 else 36), Gravity.TOP))
        top.setBackgroundColor(Color.TRANSPARENT)
        oldControl.visibility = View.VISIBLE
        arrangeTop()

        // 设计稿的压暗范围：圆屏顶 76 / 底 96，方屏顶 66 / 底 110。
        overlay.addView(gradient(true), FrameLayout.LayoutParams(-1, dp(if (round) 76 else 66), Gravity.TOP))
        overlay.addView(gradient(false), FrameLayout.LayoutParams(-1, dp(if (round) 96 else 110), Gravity.BOTTOM))
        // 渐变必须在顶栏下面：刚搬进来的顶栏目前在末尾，调整绘制顺序。
        top.bringToFront()
        overlay.addView(buttons, FrameLayout.LayoutParams(-1, -1))
        buttons.bringToFront()

        val playSize = if (round) 52 else 56
        play.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x6b0a080c)
            setStroke(dp(1), 0x24ffffff)
        }
        play.scaleType = ImageView.ScaleType.CENTER_INSIDE
        play.setPadding(dp(if (round) 15 else 16), dp(if (round) 15 else 16), dp(if (round) 15 else 16), dp(if (round) 15 else 16))
        buttons.addView(play, centered(playSize, playSize, 0f, 0f))

        arc = if (round) PlayerArcProgressView(activity).also {
            it.bind { fraction, finished ->
                if (!finished) {
                    if (!arcDragging) onArcSeekStart()
                    arcDragging = true
                    seekbar.progress = (fraction * seekbar.max).toInt()
                } else {
                    // 拖动结束走 onArcSeekStop：与原 SeekBar 松手同一 seek 链路
                    // （同步弹幕、外部音轨与媒体时钟，见 PlayerActivity.onStopTrackingTouch）。
                    seekbar.progress = (fraction * seekbar.max).toInt()
                    onArcSeekStop(seekbar.progress)
                    arcDragging = false
                }
            }
            overlay.addView(it, FrameLayout.LayoutParams(-1, -1))
        } else null

        elapsed.textSize = 10f
        duration.textSize = 10f
        elapsed.setTextColor(Color.WHITE)
        duration.setTextColor(Color.WHITE)
        if (!round) {
            detach(progressText)
            progressText.visibility = View.GONE
            // 设计稿 sProg：左侧时间 left12 top215 宽36 右对齐，右侧 left192 左对齐且 75% 不透明，
            // 行高 14dp、字号 10；轨道两端各留 54dp（132dp 轨道），热度波形带在轨道上方 8dp。
            elapsed.setTextSize(10f)
            duration.setTextSize(10f)
            duration.alpha = 0.75f
            overlay.addView(elapsed, FrameLayout.LayoutParams(dp(36), dp(14), Gravity.START).apply { topMargin = dp(215); leftMargin = dp(12) })
            overlay.addView(duration, FrameLayout.LayoutParams(dp(36), dp(14), Gravity.END).apply { topMargin = dp(215); rightMargin = dp(12) })
            elapsed.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            duration.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            // 整行改成自绘：旧样式（波形压在轨道上、滑块是小电视）与设计稿对不上。
            seekbar.setPadding(0, 0, 0, 0)
            seekbar.customRenderer = PlayerNewProgressRenderer(seekbar)::draw
            overlay.addView(seekbar, FrameLayout.LayoutParams(-1, dp(24)).apply { leftMargin = dp(54); rightMargin = dp(54); topMargin = dp(208) })
        } else {
            detach(progressText)
            progressText.gravity = Gravity.CENTER
            progressText.textSize = 10f
            progressText.setPadding(0, 0, 0, 0)
            // 设计稿：中心键下方一行「当前 / 总长」，top 72、行高 13、字号 10。
            overlay.addView(progressText, FrameLayout.LayoutParams(dp(130), dp(13), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(72) })
            seekbar.visibility = View.GONE
        }

        // 沉浸态细进度线（设计稿 squareNew 的 immerse 分支）：left 30、宽 180、top 272、高 2、圆角 2，
        // 底色白 18%、已播 #FF6699。注意它不是贴屏幕底边的那条 —— 底下还留 6dp。
        idleTrack = View(activity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(0x2effffff) }
        }
        idleFill = View(activity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(0xffff6699.toInt()) }
        }
        overlay.addView(idleTrack, FrameLayout.LayoutParams(dp(180), dp(2)).apply { leftMargin = dp(30); topMargin = dp(272) })
        // 细进度线给满宽度、靠 scaleX 表达进度（见 syncProgress），不再每次改 layoutParams。
        idleFill.pivotX = 0f
        idleFill.scaleX = 0f
        overlay.addView(idleFill, FrameLayout.LayoutParams(dp(180), dp(2)).apply { leftMargin = dp(30); topMargin = dp(272) })

        buildGrid()
        // 设计稿 seek 态：整屏压暗 38%（顶栏与渐变要盖在它上面，所以插到最底层），
        // 中间三行「+02:16 / 目标时间 / 共 08:41」浮在最上层。
        seekDim.setBackgroundColor(0x61000000)
        seekDim.visibility = View.GONE
        overlay.addView(seekDim, 0, FrameLayout.LayoutParams(-1, -1))
        buildSeekText()
        overlay.addView(seekText, FrameLayout.LayoutParams(-1, -1))
        seekText.visibility = View.GONE
        top.bringToFront()
        buttons.bringToFront()
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        // 设计稿 lockView：顶部居中一行「已锁定 · 点锁图标解锁」胶囊 + 锁图标底下 34dp 圆底
        // （黑 30% 填充 + 白 40% 内描边）。锁定时 overlay 整个收起，这一层单独顶上来；
        // 加在 overlay 之后、lock_holder 之前，Activity 的 bringToFront 会把锁定按钮压在最上层。
        lockHint.apply {
            text = activity.getString(R.string.player_lock_hint)
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(2), dp(9), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor(0x80000000.toInt())
            }
        }
        lockLayer.addView(lockHint, FrameLayout.LayoutParams(-2, dp(18), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(if (round) 28 else 16)
        })
        lockRing.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x4d000000.toInt())
            setStroke(dp(1), 0x66ffffff)
        }
        lockLayer.addView(lockRing, FrameLayout.LayoutParams(dp(34), dp(34)))
        lockLayer.visibility = View.GONE
        root.addView(lockLayer, FrameLayout.LayoutParams(-1, -1))
        // 图标校准不再挂每帧的 OnPreDrawListener（那会每帧遍历全部按钮并 setColorFilter，
        // 是新版卡顿的主因之一）。改成只在状态可能变化时校准：重摆、显隐、镜像同步、面板开合。
        // 保持原锁定遮罩与落脚点在新版层之上；列表层同理不能挡住解锁。
        root.findViewById<View>(R.id.lock_overlay).bringToFront()
        root.findViewById<View>(R.id.lock_holder).bringToFront()
        root.addView(listHost, FrameLayout.LayoutParams(-1, -1))
        listHost.visibility = View.GONE
        root.findViewById<View>(R.id.lock_overlay).bringToFront()
        root.findViewById<View>(R.id.lock_holder).bringToFront()
    }

    /** seek 预览当前是否开着：设计稿 seek 态不画弹幕（vidLayer 的 noDm），这里顺手把弹幕层让开。 */
    private var seekPreviewShown = false

    private fun buildSeekText() {
        seekDelta.apply {
            setTextColor(accentSoft)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        seekMain.apply {
            setTextColor(Color.WHITE)
            textSize = if (round) 28f else 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        seekTotal.apply {
            setTextColor(Color.WHITE)
            textSize = 10f
            alpha = 0.7f
            gravity = Gravity.CENTER
        }
        // 设计稿：圆屏 top 78 / 94 / 132，方屏 100 / 116 / 156。
        val y = if (round) intArrayOf(78, 94, 132) else intArrayOf(100, 116, 156)
        seekText.addView(seekDelta, FrameLayout.LayoutParams(-1, dp(14)).apply { topMargin = dp(y[0]) })
        seekText.addView(seekMain, FrameLayout.LayoutParams(-1, dp(if (round) 34 else 36)).apply { topMargin = dp(y[1]) })
        seekText.addView(seekTotal, FrameLayout.LayoutParams(-1, dp(13)).apply { topMargin = dp(y[2]) })
    }

    /**
     * 拖动进度时的预览（设计稿 seek 态）：按钮与中心键让位，只留顶栏、进度弧与中间三行文字。
     * 由 Activity 的 SeekBar 回调驱动，弧形拖动同样经过那条回调。
     */
    fun setSeekPreview(visible: Boolean, positionMs: Long, currentMs: Long, totalMs: Long) {
        if (!visible) {
            seekDim.visibility = View.GONE
            seekText.visibility = View.GONE
            buttons.visibility = if (shown) View.VISIBLE else View.GONE
            if (seekPreviewShown) {
                seekPreviewShown = false
                // 松手：弹幕回来，同时重置自动隐藏计时（面板/拖动都不该被 4 秒自动收掉）。
                onPanelShown(false)
            }
            return
        }
        if (!seekDim.isAttachedToWindow) return
        val total = if (totalMs > 0) totalMs else 0L
        val pos = positionMs.coerceIn(0L, if (total > 0) total else Long.MAX_VALUE)
        val diff = ((pos - currentMs) / 1000L).toInt()
        seekDelta.text = (if (diff < 0) "-" else "+") + StringUtil.toTime(kotlin.math.abs(diff))
        seekMain.text = StringUtil.toTime((pos / 1000L).toInt())
        seekTotal.text = activity.getString(R.string.player_seek_total, StringUtil.toTime((total / 1000L).toInt()))
        buttons.visibility = View.GONE
        seekDim.visibility = View.VISIBLE
        seekText.visibility = View.VISIBLE
        if (!seekPreviewShown) {
            seekPreviewShown = true
            onPanelShown(true)
        }
    }

    /**
     * 顶栏排布。两稿的顶栏结构完全不同，而老播放器的圆屏重排被开关挡掉了
     * （[com.RobinNotBad.BiliClient.activity.player.PlayerActivity] 的 initUI 里那条件是
     * screen_round 且非新版），所以新版在这里自己排。
     *
     * 圆屏（设计稿 rTop）：时钟一行居中、中线 y=16（时间在左、电池在右）；
     * 标题一行居中、中线 y=37（返回箭头贴标题前，整组居中，最宽 150dp）。
     * 方屏（设计稿 sTop）：返回箭头独立 36dp 热区、中心 (24,24)、图标 20dp；
     * 标题左对齐 left 44 top 15 宽 134 高 18；时钟贴右上 top 16 right 14（电池在左、时间在右）。
     */
    /** 方屏顶栏的返回箭头；重摆时复用，避免每轮叠一个新图标。 */
    private var backButton: ImageView? = null

    /** 已经解析到的槽位按钮本体，见 [slotOf]。 */
    private val resolvedSlots = HashMap<String, View>()

    /**
     * 取槽位的按钮本体，并记住第一次取到的实例。
     *
     * [render] 会先 [ViewGroup.removeAllViews] 再重新摆放，按钮一旦离开视图树，
     * Activity 那边的 findViewById（音量两个按钮就是现取的，没有成员变量）就再也找不到它，
     * 第二次重摆会摆出一个空 View —— 画面上按钮直接消失。所以第一次解析到就把引用留下。
     */
    private fun slotOf(key: String): View? =
        resolvedSlots[key] ?: primaryOf(key)?.also { resolvedSlots[key] = it }

    private fun arrangeTop() {
        val clockLayout = activity.findViewById<LinearLayout>(R.id.clock_layout) ?: return
        val clock = activity.findViewById<TextView>(R.id.clock)
        val battery = activity.findViewById<View>(R.id.battery)
        val title = activity.findViewById<TextView>(R.id.text_title)

        (top as? ViewGroup)?.setPadding(0, 0, 0, 0)
        (top.layoutParams as? FrameLayout.LayoutParams)?.height = dp(if (round) 46 else 44)

        clockLayout.orientation = LinearLayout.HORIZONTAL
        clockLayout.gravity = Gravity.CENTER_VERTICAL
        clock.textSize = 11f

        if (round) {
            // 时间在左、电池在右
            clockLayout.removeAllViews()
            clockLayout.addView(clock)
            clockLayout.addView(battery, LinearLayout.LayoutParams(dp(14), dp(7)).apply { leftMargin = dp(4) })
            clockLayout.layoutParams = RelativeLayout.LayoutParams(-2, dp(15)).apply {
                addRule(RelativeLayout.CENTER_HORIZONTAL)
                topMargin = dp(8)
            }
            title.layoutParams = RelativeLayout.LayoutParams(dp(150), dp(18)).apply {
                addRule(RelativeLayout.CENTER_HORIZONTAL)
                addRule(RelativeLayout.BELOW, R.id.clock_layout)
                topMargin = dp(5)
            }
            title.gravity = Gravity.CENTER
        } else {
            // 电池在左、时间在右
            clockLayout.removeAllViews()
            clockLayout.addView(battery, LinearLayout.LayoutParams(dp(14), dp(7)).apply { rightMargin = dp(4) })
            clockLayout.addView(clock)
            clockLayout.layoutParams = RelativeLayout.LayoutParams(-2, dp(16)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                topMargin = dp(16)
                rightMargin = dp(14)
            }
            title.layoutParams = RelativeLayout.LayoutParams(dp(134), dp(18)).apply {
                leftMargin = dp(44)
                topMargin = dp(15)
            }
            title.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            // 方屏的箭头是独立按钮，标题本身不再带 drawable。
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            // 重摆时复用同一个箭头：每次 arrangeTop 都 new 一个的话，顶栏里会一层层叠上去。
            val arrow = backButton ?: ImageView(activity).apply {
                setImageResource(R.drawable.ic_player_v2_back)
                contentDescription = activity.getString(R.string.desc_player_new_ui_back)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { activity.finish() }
            }.also { backButton = it }
            (arrow.parent as? ViewGroup)?.removeView(arrow)
            (top as? ViewGroup)?.addView(arrow, RelativeLayout.LayoutParams(dp(36), dp(36)).apply {
                leftMargin = dp(6)
                topMargin = dp(6)
            })
        }
        title.textSize = 12f
        top.post {
            val location = IntArray(2)
            clockLayout.getLocationOnScreen(location)
            val clockText = StringBuilder("时钟(")
                .append(location[0]).append(',').append(location[1]).append(") ")
                .append(clockLayout.width).append('x').append(clockLayout.height)
            title.getLocationOnScreen(location)
            clockText.append(" 标题(").append(location[0]).append(',').append(location[1]).append(") ")
                .append(title.width).append('x').append(title.height)
                .append(" 文本=").append(title.text)
                .append(" 顶栏高=").append(top.height)
            Logu.d("新版顶栏>" + clockText)
        }
    }

    fun render(newLayout: PlayerUiLayout.Layout, isRound: Boolean) {
        if (round != isRound) return // 屏形在 Activity 生命周期内不变，构造后无需重建。
        // 顶栏的排布在 onCreate 里做一次还不够：Activity 的 initUI 会再按屏宽/开关改一轮
        // （padding、最大宽度之类），每次重摆都纠正回来。
        arrangeTop()
        layout = newLayout
        closeGrid()
        buttons.removeAllViews()
        buttons.addView(play, centered(if (round) 52 else 56, if (round) 52 else 56, 0f, 0f))
        mirrors.clear()
        val (_, overflow) = PlayerNewUiGeometry.visibleAndOverflow(layout, round)
        val left = PlayerNewUiGeometry.sideSpec(layout.left, round)
        val right = PlayerNewUiGeometry.mirrorX(PlayerNewUiGeometry.sideSpec(layout.right, round), widthDp())
        val bottom = PlayerNewUiGeometry.bottomSpec(layout.bottomLeft + layout.bottomRight, round, widthDp())
        var remainingOverflow = overflow
        if (placeSpec(left, remainingOverflow)) remainingOverflow = emptyList()
        if (placeSpec(right, remainingOverflow)) remainingOverflow = emptyList()
        placeSpec(bottom, remainingOverflow)
        sync()
        applyDesignIcons()
        // 没有显式组时，溢出仍必须有入口，不能静默丢按钮。
        if (overflow.isNotEmpty() && !containsGroup(layout)) {
            val more = moreButton(overflow.flatMap { keysOf(it) })
            placeAt(more, if (round) PlayerNewUiGeometry.polar(88f, 305f) else PlayerNewUiGeometry.Point(120f, 204f))
        }
    }

    /** 圆屏弧形进度（方屏为 null）：高能数据加载后由 Activity 把真实曲线同步过来。 */
    fun arcProgress(): PlayerArcProgressView? = arc

    fun setControlsVisible(visible: Boolean) {
        shown = visible
        overlay.visibility = View.VISIBLE
        top.visibility = if (visible) View.VISIBLE else View.GONE
        buttons.visibility = if (visible) View.VISIBLE else View.GONE
        elapsed.visibility = if (visible && !round) View.VISIBLE else View.GONE
        duration.visibility = elapsed.visibility
        seekbar.visibility = if (visible && !round) View.VISIBLE else View.GONE
        // 整屏手势层（layout_control）永远不能 GONE：单击显隐、双击暂停、长按倍速、
        // 滑动 seek 全挂在它的监听器上，一旦隐藏就再没有 View 能接住触摸，
        // 控制层收起后单击唤不回来（死锁）。旧实现同样只隐藏它的子层，从不隐藏它本身。
        oldControl.visibility = View.VISIBLE
        if (round) progressText.visibility = if (visible) View.VISIBLE else View.GONE
        arc?.visibility = if (round && (visible || idleEnabled())) View.VISIBLE else View.GONE
        arc?.setIdle(!visible)
        arc?.isEnabled = visible
        idleTrack.visibility = if (!round && !visible && idleEnabled()) View.VISIBLE else View.GONE
        idleFill.visibility = idleTrack.visibility
        if (!visible) {
            closeGrid()
            closeList()
        }
        applyDesignIcons()
        syncProgress()
    }

    /** 设计稿 IC 表里对应的主界面图标（0 = 该槽位本来就是文字，比如清晰度与倍速）。 */
    private fun designIcon(key: String, view: View): Int = when (key) {
        PlayerButtons.LOCK -> if (view.contentDescription?.toString() ==
            activity.getString(R.string.desc_player_lock_locked)
        ) R.drawable.ic_player_v2_lock else R.drawable.ic_player_v2_unlock
        PlayerButtons.DANMAKU -> R.drawable.ic_player_v2_danmaku
        PlayerButtons.ROTATE -> R.drawable.ic_player_v2_rotate
        PlayerButtons.SOUND_CUT -> R.drawable.ic_player_v2_voldown
        PlayerButtons.SOUND_ADD -> R.drawable.ic_player_v2_volup
        PlayerButtons.PAGE_SELECTOR -> R.drawable.ic_player_v2_pages
        else -> 0
    }

    /**
     * 把主界面按钮统一换成设计稿的图标，开着的染成 #FF8CB0。
     *
     * 图标归属仍在 Activity：弹幕开关、锁定/解锁、播放/暂停都会 setImageResource，一次交互就把
     * 设计稿图标顶掉了。所以在每次绘制前对一遍——实例还是我们设的那个就什么都不做，
     * 稳定后这一遍只是几张引用比较，不会反复 setImageResource 造成重绘循环。
     */
    private fun applyDesignIcons() {
        val iterator = designViews.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val view = entry.key
            if (!isDescendant(view, buttons)) {
                iterator.remove()
                continue
            }
            val image = view as? ImageView ?: continue
            val res = designIcon(entry.value, view)
            if (res == 0) continue
            if (appliedIcon[view] !== image.drawable) {
                image.setImageResource(res)
                appliedIcon[view] = image.drawable
            }
            val on = slotIsOn(entry.value) || (entry.value == PlayerButtons.DANMAKU && danmakuNow())
            if (on) image.setColorFilter(accentSoft) else image.clearColorFilter()
        }
        if (appliedIcon[play] !== play.drawable) {
            play.setImageResource(if (playingNow()) R.drawable.ic_player_v2_pause else R.drawable.ic_player_v2_play)
            appliedIcon[play] = play.drawable
        }
        play.clearColorFilter()
    }

    fun sync() {
        for ((primary, mirror) in mirrors) copyAppearance(primary, mirror)
        // 倍速按钮的文案由 Activity 写成「x 1.0」，设计稿要「1.0×」，统一在这里改写，
        // 真身与镜像都过一遍（真身不在 mirrors 里，所以直接扫 buttons 的子层）。
        for (index in 0 until buttons.childCount) {
            val label = buttons.getChildAt(index) as? TextView ?: continue
            val fixed = designSpeedLabel(label.text)
            if (fixed != null && fixed != label.text) label.text = fixed
        }
        // 镜像同步由 Activity 的定时器驱动（约 400ms 一次），顺手在这里校准图标，
        // 取代原先每帧都跑的 OnPreDrawListener。
        applyDesignIcons()
        syncProgress()
    }

    /**
     * 把 Activity 写在倍速按钮上的「x 1.5」改成设计稿的「1.5×」。
     * 只认「x + 数字」这一种形状，其余文本（清晰度的 720P 之类）原样返回 null 表示不用改。
     */
    private fun designSpeedLabel(value: CharSequence?): CharSequence? {
        val text = value?.toString() ?: return null
        val match = SPEED_LABEL.find(text) ?: return null
        return match.groupValues[1] + "×"
    }

    /**
     * 圆屏时间行（设计稿 roundNew 那一行）：`03:12` 后接 ` / 08:41`，斜杠起后半段 60% 不透明。
     *
     * 设计稿是 `fmt(NOW)<span style="opacity:.6">&nbsp;/ fmt(TOTAL)</span>`；这里用只改透明度的
     * span 而不是换颜色 —— 画面亮的时候换灰会发脏，改 alpha 才是设计稿的 opacity。
     */
    fun roundProgressText(current: String, total: String, suffix: String = ""): CharSequence {
        val tail = " / " + total + suffix
        val text = SpannableString(current + tail)
        text.setSpan(ProgressAlphaSpan(153), current.length, current.length + tail.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    /** 只把绘制透明度乘一个系数的 span（设计稿的 opacity）。 */
    private class ProgressAlphaSpan(private val alpha: Int) : CharacterStyle() {
        override fun updateDrawState(tp: TextPaint) {
            tp.alpha = tp.alpha * alpha / 255
        }
    }

    fun syncProgress() {
        val value = seekbar.progress
        val max = seekbar.max
        arc?.setProgress(value, max)
        if (!round) {
            elapsed.text = StringUtil.toTime(value / 1000)
            duration.text = StringUtil.toTime(max / 1000)
            if (idleFill.visibility == View.VISIBLE) {
                // 用 scaleX 而不是改 layoutParams：后者每次都触发 requestLayout，
                // 进度回调约 1Hz、拖动时更密，是新版卡顿的另一个来源。
                val fraction = if (max <= 0) 0f else value.toFloat() / max
                idleFill.pivotX = 0f
                idleFill.scaleX = fraction
            }
        }
    }

    /** 把任意整行文本列表装进设计稿的新版列表层；点击仍由 Activity 的原业务处理。 */
    fun showList(title: CharSequence, items: List<String>, selected: Int = -1, onClick: (Int) -> Unit) {
        closeGrid()
        listHost.removeAllViews()
        listHost.setBackgroundColor(0xa0000000.toInt())
        listHost.setOnClickListener { closeList() }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xf0181818.toInt()) }
            setOnClickListener { }
        }
        card.addView(TextView(activity).apply {
            text = title
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
        }, LinearLayout.LayoutParams(-1, dp(28)))
        val scroll = ScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
        val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        items.forEachIndexed { index, text ->
            rows.addView(TextView(activity).apply {
                this.text = text
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                setPadding(dp(12), 0, dp(12), 0)
                setTextColor(if (index == selected) com.RobinNotBad.BiliClient.ui.appearance.ColorScheme.PRIMARY else Color.WHITE)
                setTypeface(typeface, if (index == selected) Typeface.BOLD else Typeface.NORMAL)
                setOnClickListener { closeList(); onClick(index) }
            }, LinearLayout.LayoutParams(-1, dp(36)))
        }
        scroll.addView(rows, ViewGroup.LayoutParams(-1, -2))
        card.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val lp = FrameLayout.LayoutParams(dp(if (round) 184 else 216), dp(if (round) 178 else 214), if (round) Gravity.CENTER else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        if (!round) lp.bottomMargin = dp(8)
        listHost.addView(card, lp)
        // 弹幕是独立绘制层，普通 View 的压暗盖不住它，列表打开期间让它自己藏起来
        onPanelShown(true)
        listHost.visibility = View.VISIBLE
    }

    /**
     * 清晰度面板（设计稿 qualityPanel）。和通用 [showList] 只差排版：
     * 圆屏是一列直接浮在压暗画面上的选项（选中行背后一条 168×35 粉色胶囊），
     * 方屏是从 y=96 到底的抽屉（圆角 18、深底 + 顶部 1px 分隔线）。
     */
    fun showQualityPanel(title: CharSequence, items: List<String>, selected: Int, onClick: (Int) -> Unit) {
        closeGrid()
        listHost.removeAllViews()
        listHost.setOnClickListener { closeList() }
        if (round) {
            // 设计稿：dim .86 + noDm，没有卡片底。
            listHost.setBackgroundColor(0xdb000000.toInt())
            listHost.addView(TextView(activity).apply {
                text = title
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                alpha = 0.65f
            }, FrameLayout.LayoutParams(-1, dp(14)).apply { topMargin = dp(26) })
            // 设计稿四行中线 82.5 / 116.5 / 150.5 / 182.5；清晰度项多于四个时按 34 等距续排。
            fun rowY(index: Int) = if (items.size <= 4) {
                ROUND_QUALITY_Y[index.coerceIn(0, ROUND_QUALITY_Y.lastIndex)]
            } else {
                82.5f + index * 34f
            }
            // 胶囊框住选中行，所以跟着选中项走：设计稿示例恰好选中第二行，静态 top 99 就是 116.5-17.5。
            val selectedY = rowY(selected.coerceIn(0, items.lastIndex.coerceAtLeast(0)))
            listHost.addView(View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(0x29ff6699)
                }
            }, FrameLayout.LayoutParams(dp(168), dp(35)).apply {
                topMargin = dp(selectedY - 17.5f)
                leftMargin = dp(32.5f)
            })
            items.forEachIndexed { index, text ->
                val y = rowY(index)
                listHost.addView(qualityRow(text, index == selected, 12f, dp(14), dp(6), true, index, items.lastIndex) {
                    closeList(); onClick(index)
                }, FrameLayout.LayoutParams(-1, dp(20)).apply { topMargin = dp(y - 10f) })
            }
            listHost.addView(TextView(activity).apply {
                text = activity.getString(R.string.player_quality_panel_hint)
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                alpha = 0.55f
            }, FrameLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(204) })
        } else {
            // 设计稿：dim .4 + 底部抽屉，圆角只在上沿。
            listHost.setBackgroundColor(0x66000000.toInt())
            val r18 = dp(18).toFloat()
            val sheet = FrameLayout(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadii = floatArrayOf(r18, r18, 0f, 0f, 0f, 0f, 0f, 0f)
                    setColor(0xf7161319.toInt())
                }
                setOnClickListener { }
            }
            sheet.addView(View(activity).apply { setBackgroundColor(0x14ffffff) }, FrameLayout.LayoutParams(-1, dp(1)))
            sheet.addView(TextView(activity).apply {
                text = title
                textSize = 11f
                setTextColor(Color.WHITE)
                alpha = 0.6f
            }, FrameLayout.LayoutParams(-2, dp(16)).apply { topMargin = dp(12); leftMargin = dp(18) })
            items.forEachIndexed { index, text ->
                val row = qualityRow(text, index == selected, 13f, dp(16), dp(6), false, index, items.lastIndex) {
                    closeList(); onClick(index)
                }
                if (index == selected) row.background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(0x24ff6699)
                }
                row.setPadding(dp(8), 0, dp(8), 0)
                sheet.addView(row, FrameLayout.LayoutParams(-1, dp(34)).apply {
                    topMargin = dp(32 + index * 36)
                    leftMargin = dp(12)
                    rightMargin = dp(12)
                })
            }
            listHost.addView(sheet, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(96) })
        }
        onPanelShown(true)
        // 设计稿 quality 态整屏只有「压暗后的画面 + 面板」：顶栏、按钮、弧、时间行一律不画，
        // 所以整层控制层收起来，只留 listHost 这一层压暗。
        overlay.visibility = View.GONE
        listHost.visibility = View.VISIBLE
    }

    /** 清晰度面板的一行：圆屏居中（文字 + 选中勾连排），方屏左对齐、勾贴右边。 */
    private fun qualityRow(
        text: String,
        selected: Boolean,
        textSize: Float,
        checkSize: Int,
        checkMargin: Int,
        centered: Boolean,
        index: Int,
        lastIndex: Int,
        onClick: () -> Unit,
    ): View {
        // 设计稿未选中项：白；第四项（大会员档）再淡到 45%，其余 80%。
        val alpha = if (selected) 1f else if (index == 3 && index == lastIndex) 0.45f else 0.8f
        val row = FrameLayout(activity).apply {
            this.alpha = alpha
            setOnClickListener { onClick() }
        }
        val label = TextView(activity).apply {
            this.text = text
            // 圆屏设计稿：选中 15sp 粗体、未选 12sp；方屏统一 13sp。
            this.textSize = if (selected && centered) textSize + 3f else textSize
            setTextColor(if (selected) accentSoft else Color.WHITE)
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            maxLines = 1
            gravity = if (centered) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
        }
        val check = if (selected) ImageView(activity).apply {
            setImageResource(R.drawable.ic_player_v2_check)
            setColorFilter(accentSoft)
        } else null
        if (centered) {
            // 设计稿里「文字 + 勾」是一个整体居中的 flex 行（gap 6）——勾紧跟文字，
            // 而不是钉在屏幕右边缘：把勾放进水平 LinearLayout 再整体居中。
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                isBaselineAligned = false
            }
            line.addView(label, LinearLayout.LayoutParams(-2, -1))
            if (check != null) {
                line.addView(check, LinearLayout.LayoutParams(checkSize, checkSize).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = checkMargin
                })
            }
            row.addView(line, FrameLayout.LayoutParams(-1, -1))
        } else {
            row.addView(label, FrameLayout.LayoutParams(-1, -1).apply { leftMargin = dp(8) })
            if (check != null) {
                // 方屏抽屉：勾贴右，行内已有 8dp 内边距
                row.addView(check, FrameLayout.LayoutParams(checkSize, checkSize, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                    rightMargin = dp(8)
                })
            }
        }
        return row
    }

    fun closeList() {
        listHost.visibility = View.GONE
        // showQualityPanel 期间整层控制层是藏起来的（设计稿 quality 态不画顶栏与按钮），关面板时按当前显隐态还原
        overlay.visibility = if (shown) View.VISIBLE else View.GONE
        // 「更多」网格也开着时不能把弹幕放回来
        if (gridHost.visibility != View.VISIBLE) onPanelShown(false)
    }

    fun closeTransientPanels() { closeGrid(); closeList() }

    /** 锁定时藏掉整个新版控制层、顶出锁定层；解锁时反过来，避免控制层压住 lock_holder。 */
    fun setLocked(locked: Boolean) {
        if (locked) closeTransientPanels()
        overlay.visibility = if (locked) View.GONE else View.VISIBLE
        lockLayer.visibility = if (locked) View.VISIBLE else View.GONE
        if (locked) positionLockRing()
    }

    /**
     * 把 34dp 圆底摆到锁定按钮中心。锁定期间按钮被 Activity 搬到了 lock_holder，
     * 位置只能运行时取（槽位可被用户改），所以这里用屏幕坐标差算，而不是复用槽位常量。
     */
    private fun positionLockRing() {
        val button = primaryOf(PlayerButtons.LOCK) ?: return
        lockRing.post {
            if (lockLayer.visibility != View.VISIBLE) return@post
            val base = IntArray(2)
            val point = IntArray(2)
            lockLayer.getLocationOnScreen(base)
            button.getLocationOnScreen(point)
            val size = dp(34)
            val params = lockRing.layoutParams as FrameLayout.LayoutParams
            params.leftMargin = point[0] - base[0] + button.width / 2 - size / 2
            params.topMargin = point[1] - base[1] + button.height / 2 - size / 2
            lockRing.layoutParams = params
        }
    }

    /**
     * 「更多」分页网格（设计稿 morePanel）：不留卡片底，格子直接浮在画面上。
     *
     * 圆屏：3×3 格 46×46、间距 4，整块 146×146 居中，标题「更多」在 y20、提示在 y196，
     * 页码点竖排在右侧 x=204。方屏：格 64×58、间距 6、整块 204×186、标题左对齐 y16。
     * 翻页只走上下滑（播放页有右滑返回，横向翻页会抢手势）。
     */
    private fun buildGrid() {
        // 设计稿 morePanel 底下压着 dim 0.84 的视频层：整屏压暗，只剩面板。
        gridHost.setBackgroundColor(0xd6000000.toInt())
        gridHost.setOnClickListener {
            if (gridFlinged) { gridFlinged = false; return@setOnClickListener }
            closeGrid()
        }
        gridTitle.apply {
            text = activity.getString(R.string.player_more_panel_title)
            setTextColor(Color.WHITE)
            textSize = if (round) 12f else 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = if (round) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
        }
        gridHost.addView(gridTitle, if (round) FrameLayout.LayoutParams(-2, dp(16)).apply {
            // 圆屏标题按文字宽度居中；写成 -1 加左右 margin 时文字会偏左半个 margin。
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(20)
        } else FrameLayout.LayoutParams(-1, dp(18)).apply {
            topMargin = dp(16); leftMargin = dp(18); rightMargin = dp(18)
        })
        grid.orientation = LinearLayout.VERTICAL
        grid.gravity = Gravity.CENTER
        gridHost.addView(grid, FrameLayout.LayoutParams(
            dp(gridWidthDp()), dp(gridHeightDp())
        ).apply {
            topMargin = dp(if (round) 42 else 46)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        })
        gridHint.apply {
            textSize = 9f
            setTextColor(0x99ffffff.toInt())
            gravity = Gravity.CENTER
        }
        gridHost.addView(gridHint, FrameLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(if (round) 196 else 244) })
        // 页码点：设计稿 dx=208 / dy=116.5（方屏 231 / 139），每颗顶边 = dy-10+i*12。
        gridHost.addView(gridDots, FrameLayout.LayoutParams(dp(8), dp(62)).apply {
            topMargin = dp(if (round) 106.5f else 129f)
            leftMargin = dp(if (round) 204 else 227)
        })
        // 方屏顶栏右侧的收起按钮（设计稿 ib(214,25)：中心 (214,25)、36dp 热区、图标 more）。
        if (!round) {
            gridHost.addView(ImageButton(activity).apply {
                // 设计稿 ib(214,25,'more')：三点图标，不是旧的方框三横线（morehide）
                setImageResource(R.drawable.ic_player_v2_more)
                contentDescription = activity.getString(R.string.player_more_panel_collapse)
                background = null
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { closeGrid() }
            }, FrameLayout.LayoutParams(dp(36), dp(36)).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(196); topMargin = dp(7)
            })
        }
        overlay.addView(gridHost, FrameLayout.LayoutParams(-1, -1))
        gridHost.visibility = View.GONE
        gridHost.setOnTouchListener { _, event ->
            gridGesture.onTouchEvent(event)
            false
        }
    }

    private fun openGrid(keys: List<String>) {
        gridKeys = keys
        gridPage = 0
        // 设计稿的「更多」是整屏态：常规控件与顶栏都让位，屏幕上只剩面板。
        top.visibility = View.GONE
        buttons.visibility = View.GONE
        onPanelShown(true)
        gridHost.visibility = View.VISIBLE
        renderGridPage()
    }

    private fun closeGrid() {
        gridHost.visibility = View.GONE
        onPanelShown(false)
        // 只有控制层本身还显示时才把顶栏与按钮层让回来。收起控制层时 setControlsVisible(false)
        // 也会走到这里，无条件恢复会把刚藏好的 top/buttons 又打开——实测症状就是这样：
        // 控制层收起后按钮一直挂屏幕上、底部细进度线也跟着露出来。
        if (shown) {
            top.visibility = View.VISIBLE
            buttons.visibility = View.VISIBLE
        }
    }

    private val gridGesture by lazy {
        android.view.GestureDetector(activity, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent): Boolean = true
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                val pages = PlayerNewUiGeometry.pages(gridKeys)
                if (pages.size < 2 || kotlin.math.abs(vy) < kotlin.math.abs(vx)) return false
                gridPage = if (vy < 0) (gridPage + 1) % pages.size else (gridPage - 1 + pages.size) % pages.size
                gridFlinged = true
                renderGridPage()
                return true
            }
        })
    }

    private fun gridCellWidth() = if (round) 46 else 64
    private fun gridCellHeight() = if (round) 46 else 58
    private fun gridGap() = if (round) 4 else 6
    private fun gridWidthDp() = gridCellWidth() * 3 + gridGap() * 2
    private fun gridHeightDp() = gridCellHeight() * 3 + gridGap() * 2

    private fun renderGridPage() {
        grid.removeAllViews()
        gridDots.removeAllViews()
        val pages = PlayerNewUiGeometry.pages(gridKeys)
        if (pages.isEmpty()) { closeGrid(); return }
        gridPage = gridPage.coerceIn(0, pages.lastIndex)
        val cellW = gridCellWidth()
        val cellH = gridCellHeight()
        val gap = gridGap()
        for (rowIndex in 0 until 3) {
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (col in 0 until 3) {
                val key = pages[gridPage].getOrNull(rowIndex * 3 + col)
                val primary = key?.let { slotOf(it) }
                val cell: View = if (primary != null && key != null) gridCell(primary, key) else View(activity)
                line.addView(cell, LinearLayout.LayoutParams(dp(cellW), dp(cellH)).apply {
                    if (col > 0) marginStart = dp(gap)
                })
            }
            grid.addView(line, LinearLayout.LayoutParams(-2, dp(cellH)).apply { if (rowIndex > 0) topMargin = dp(gap) })
        }
        // 页码点：设计稿竖排在网格右侧，选中那颗拉长成 12dp。
        pages.indices.forEach { index ->
            val dot = View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(if (index == gridPage) Color.WHITE else 0x59ffffff)
                }
            }
            gridDots.addView(dot, FrameLayout.LayoutParams(dp(8), dp(if (index == gridPage) 12 else 8)).apply {
                topMargin = dp(index * 12)
            })
        }
        gridHint.text = if (pages.size > 1) {
            activity.getString(R.string.player_more_panel_hint, "${gridPage + 1}/${pages.size}")
        } else ""
        sync()
    }

    /** 「更多」面板的图标：直接照搬设计稿的 24 视口描边图标，而不是复用播放页按钮的图标。 */
    private fun moreIconRes(key: String): Int = when (key) {
        PlayerButtons.DANMAKU_SEND -> R.drawable.ic_player_more_send
        PlayerButtons.SUBTITLE -> R.drawable.ic_player_more_cc
        PlayerButtons.LOOP -> R.drawable.ic_player_more_loop
        PlayerButtons.AUDIO_ONLY -> R.drawable.ic_player_more_audio
        PlayerButtons.AUTO_NEXT -> R.drawable.ic_player_more_autonext
        PlayerButtons.VIEWPOINT -> R.drawable.ic_player_more_chapter
        PlayerButtons.VIDEO_INFO -> R.drawable.ic_player_more_info
        PlayerButtons.JUMP_TIME -> R.drawable.ic_player_more_clock
        PlayerButtons.QUEUE_MODE -> R.drawable.ic_player_more_queue
        PlayerButtons.LIKE -> R.drawable.ic_player_more_like
        PlayerButtons.COIN -> R.drawable.ic_player_more_coin
        PlayerButtons.FAVORITE -> R.drawable.ic_player_more_star
        PlayerButtons.DOWNLOAD -> R.drawable.ic_player_more_download
        PlayerButtons.SLEEP_TIMER -> R.drawable.ic_player_more_moon
        else -> 0
    }

    /** 面板里的名字用设计稿的短标签（「循环」而不是「循环播放」），与播放页按钮名各说各的。 */
    private fun moreLabelRes(key: String): Int = when (key) {
        PlayerButtons.DANMAKU_SEND -> R.string.player_more_send
        PlayerButtons.SUBTITLE -> R.string.player_more_cc
        PlayerButtons.LOOP -> R.string.player_more_loop
        PlayerButtons.AUDIO_ONLY -> R.string.player_more_audio
        PlayerButtons.AUTO_NEXT -> R.string.player_more_autonext
        PlayerButtons.VIEWPOINT -> R.string.player_more_chapter
        PlayerButtons.VIDEO_INFO -> R.string.player_more_info
        PlayerButtons.JUMP_TIME -> R.string.player_more_clock
        PlayerButtons.QUEUE_MODE -> R.string.player_more_queue
        PlayerButtons.LIKE -> R.string.player_more_like
        PlayerButtons.COIN -> R.string.player_more_coin
        PlayerButtons.FAVORITE -> R.string.player_more_star
        PlayerButtons.DOWNLOAD -> R.string.player_more_download
        PlayerButtons.SLEEP_TIMER -> R.string.player_more_moon
        PlayerButtons.SEEK_BACK -> R.string.player_more_seek_back
        PlayerButtons.SEEK_FORWARD -> R.string.player_more_seek_forward
        else -> 0
    }

    /** 一格：图标 20dp + 名称 9sp（设计稿 .cell）。原按钮可能是文字按钮（±N 秒），那就把文字当图标位。 */
    private fun gridCell(primary: View, key: String): View {
        val cell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0x12ffffff) }
            // 设计稿的格子带上 data-act="close"：点一格就执行并收起面板。
            setOnClickListener { primary.performClick(); closeGrid() }
            setOnLongClickListener { primary.performLongClick() }
        }
        val on = slotIsOn(key)
        val iconRes = moreIconRes(key)
        val glyph: View = if (iconRes != 0) ImageView(activity).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            if (on) setColorFilter(accentSoft)
        } else TextView(activity).apply {
            // ±N 秒在设计稿里就是文字图标（「-10s」「+10s」）
            text = activity.getString(
                if (key == PlayerButtons.SEEK_BACK) R.string.player_more_seek_back_glyph
                else R.string.player_more_seek_forward_glyph
            )
            textSize = 9f
            setTextColor(if (on) accentSoft else Color.WHITE)
            gravity = Gravity.CENTER
        }
        cell.addView(glyph, LinearLayout.LayoutParams(dp(20), dp(20)))
        val labelRes = moreLabelRes(key)
        cell.addView(TextView(activity).apply {
            text = if (labelRes != 0) activity.getString(labelRes)
            else PlayerButtons.byKey(key)?.let { activity.getString(it.nameRes) } ?: ""
            textSize = 9f
            setTextColor(if (on) accentSoft else Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-2, dp(15)).apply { topMargin = dp(4) })
        cell.contentDescription = primary.contentDescription
        return cell
    }

    private fun placeSpec(spec: PlayerNewUiGeometry.Spec, overflow: List<PlayerUiLayout.Slot> = emptyList()): Boolean {
        var consumedOverflow = false
        spec.slots.forEachIndexed { index, slot ->
            when (slot) {
                is PlayerUiLayout.Slot.Button -> placeAt(button(slot.key), spec.positions[index])
                is PlayerUiLayout.Slot.Group -> {
                    val keys = (if (slot.keys.isEmpty()) PlayerUiLayout.unplacedKeys(layout) else slot.keys) + overflow.flatMap { keysOf(it) }
                    consumedOverflow = overflow.isNotEmpty()
                    placeAt(moreButton(keys), spec.positions[index])
                }
            }
        }
        return consumedOverflow
    }

    private fun button(key: String): View {
        val primary = slotOf(key) ?: return View(activity)
        // 清晰度在设计稿里是一段「720P」文字而不是图标：另造一个文字按钮顶上，
        // 点击仍转发给原按钮（打开清晰度弹窗），状态也仍由 Activity 的 qn 决定。
        if (key == PlayerButtons.QUALITY) {
            val label = qualityLabel()?.takeIf { it.isNotEmpty() }
            if (label != null) return TextView(activity).apply {
                text = label
                textSize = 11f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER
                contentDescription = activity.getString(R.string.setting_player_btn_quality)
                setOnClickListener { primary.performClick() }
            }
        }
        // 真身已在别处时复制；这样原状态更新仍只写一处。
        if (isDescendant(primary, buttons) || isDescendant(primary, grid)) return mirror(primary, 36, 20, key)
        detach(primary)
        styleButton(primary, 36, 20)
        designViews[primary] = key
        return primary
    }

    private fun moreButton(keys: List<String>): View = ImageButton(activity).apply {
        // 设计稿 ic(214,184,'more')：三点图标；旧位图 morehide 是方框三横线，不符
        setImageResource(R.drawable.ic_player_v2_more)
        contentDescription = activity.getString(R.string.desc_player_group_expand)
        background = null
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(8), dp(8), dp(8), dp(8))
        // 不能按 visibility 过滤：这些按钮平时就被 Activity 设成 GONE（它们本来不在界面上），
        // 只有「存在但当前视频用不了」的（如单 P 视频的分 P）才该缺席，那类按钮本体往往是 null。
        setOnClickListener { openGrid(keys.filter { slotOf(it) != null }) }
    }

    private fun mirror(primary: View, size: Int, icon: Int, key: String): View {
        val view: View = if (primary is ImageButton) ImageButton(activity).apply {
            setImageDrawable(primary.drawable?.constantState?.newDrawable(activity.resources)?.mutate())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = null
        } else TextView(activity).apply {
            text = (primary as? TextView)?.text
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        view.contentDescription = primary.contentDescription
        view.setOnClickListener {
            when {
                primary.tag == NEW_QUALITY_TAG -> showQuality()
                primary.tag == NEW_PAGES_TAG -> showPages()
                else -> primary.performClick()
            }
        }
        view.setOnLongClickListener { primary.performLongClick() }
        mirrors += primary to view
        styleButton(view, size, icon)
        designViews[view] = key
        return view
    }

    private fun placeAt(view: View, point: PlayerNewUiGeometry.Point) {
        val p = if (round) PlayerNewUiGeometry.scalePoint(point, widthDp()) else point
        val lp = FrameLayout.LayoutParams(dp(36), dp(36))
        lp.leftMargin = (p.x * density - dp(18)).toInt()
        lp.topMargin = (p.y * density - dp(18)).toInt()
        buttons.addView(view, lp)
    }

    private fun styleButton(view: View, size: Int, icon: Int) {
        view.background = null
        if (view is ImageButton) {
            view.scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp((size - icon) / 2)
            view.setPadding(pad, pad, pad, pad)
        } else if (view is TextView) {
            view.gravity = Gravity.CENTER
            view.setTextColor(Color.WHITE)
            view.textSize = 12f
        }
        view.layoutParams = ViewGroup.LayoutParams(dp(size), dp(size))
    }

    private fun copyAppearance(primary: View, mirror: View) {
        mirror.visibility = primary.visibility
        mirror.isEnabled = primary.isEnabled
        if (primary is ImageButton && mirror is ImageButton) {
            // 只在真身换了图时才复制：每 400ms 无条件 newDrawable().mutate() 会反复触发
            // 镜像的重绘与布局，是新版卡顿的来源之一。
            val source = primary.drawable
            if (mirror.tag !== source) {
                mirror.setImageDrawable(source?.constantState?.newDrawable(activity.resources)?.mutate())
                mirror.tag = source
            }
            mirror.contentDescription = primary.contentDescription
        } else if (primary is TextView && mirror is TextView && mirror.text != primary.text) {
            mirror.text = primary.text
        }
    }

    private fun centered(width: Int, height: Int, xDp: Float, yDp: Float) =
        FrameLayout.LayoutParams(dp(width), dp(height), Gravity.CENTER).apply {
            leftMargin = dp(xDp); topMargin = dp(yDp)
        }

    private fun gradient(top: Boolean) = View(activity).apply {
        background = GradientDrawable(
            if (top) GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.BOTTOM_TOP,
            if (top) intArrayOf(0xa8000000.toInt(), 0x47000000, 0) else intArrayOf(0xc7000000.toInt(), 0x5c000000, 0)
        )
    }

    private fun textButton(text: String, click: () -> Unit) = TextView(activity).apply {
        this.text = text; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { click() }
    }

    private fun keysOf(slot: PlayerUiLayout.Slot): List<String> = when (slot) {
        is PlayerUiLayout.Slot.Button -> listOf(slot.key)
        is PlayerUiLayout.Slot.Group -> if (slot.keys.isEmpty()) PlayerUiLayout.unplacedKeys(layout) else slot.keys
    }

    private fun containsGroup(value: PlayerUiLayout.Layout) = (value.left + value.right + value.bottomLeft + value.bottomRight).any { it is PlayerUiLayout.Slot.Group }
    private fun idleEnabled() = SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_UI_NEW_IDLE_PROGRESS, true)
    private fun widthDp() = if (round) PlayerNewUiGeometry.REFERENCE_SIZE_DP else
        (root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels) / density
    private fun dp(value: Int) = (value * density).toInt()
    private fun dp(value: Float) = (value * density).toInt()
    private fun detach(view: View) { (view.parent as? ViewGroup)?.removeView(view) }
    private fun isDescendant(view: View, parent: ViewGroup): Boolean {
        var current = view.parent
        while (current is View) { if (current === parent) return true; current = current.parent }
        return false
    }

    companion object {
        const val NEW_QUALITY_TAG = "player_new_quality"
        const val NEW_PAGES_TAG = "player_new_pages"

        /** 倍速按钮的文案形状：「x 1.0」，见 Activity 里的 speed_strs。 */
        internal val SPEED_LABEL = Regex("^x\\s*(\\d+(?:\\.\\d+)?)$")

        /** 设计稿 qualityPanel 圆屏四行的中线（dp）：82.5 / 116.5 / 150.5 / 182.5。 */
        private val ROUND_QUALITY_Y = floatArrayOf(82.5f, 116.5f, 150.5f, 182.5f)
    }
}
