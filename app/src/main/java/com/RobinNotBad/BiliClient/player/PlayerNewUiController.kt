package com.RobinNotBad.BiliClient.player

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
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
import com.RobinNotBad.BiliClient.activity.player.PlayerLockPolicy
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.ui.widget.HighEnergyProgressBar
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.RobinNotBad.BiliClient.util.player.VideoShotGrid

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
    /** 某个按钮当前是否处于「开启」态，「更多」面板据此把那一格染成主题强调色（设计稿 #FF8CB0）。 */
    private val slotIsOn: (String) -> Boolean,
    /** 整屏弹层开合：弹幕是独立 surface，普通 View 的遮罩盖不住它，只能让它自己藏起来。 */
    private val onPanelShown: (Boolean) -> Unit,
    /** 弧形进度条拖动开始：置 isSeeking，压住进度回刷。 */
    private val onArcSeekStart: () -> Unit,
    /** 弧形进度条拖动结束：走与原 SeekBar 松手完全相同的 seek 链路。 */
    private val onArcSeekStop: (Int) -> Unit,
    /** 当前是否在播放：中心键据此在「播放/暂停」两个设计稿图标之间切换。 */
    private val playingNow: () -> Boolean,
    /** 弹幕是否可见：可见时弹幕按钮像设计稿那样染成主题强调色（#FF8CB0）。 */
    private val danmakuNow: () -> Boolean,
    /**
     * 某个槽位在**这条视频上**是否可用（与该按钮此刻显示/隐藏无关）。
     *
     * 不能拿 `view.visibility` 当判据：控制层自动收起时 `hidecon` 会把 lock_btn / text_speed
     * 直接置 GONE，若此时正好重摆（旋转、回前台），这些按钮就会被误判成「用不上」、
     * 从布局里永久摘掉，表现就是**圆屏按钮凭空消失**。判据必须来自业务状态。
     */
    private val slotAvailable: (String) -> Boolean = { true },
    /**
     * 直播在看人数的原始串（Activity 的 `online_number`，由直播 WS 的 WATCHED_CHANGE 写入，
     * 可能已带「万/亿」）。只在直播态由 [sync] 的定时镜像顺手刷新；非直播返回 null 即可。
     */
    private val liveOnlineRaw: () -> String? = { null },
) {
    private val overlay = FrameLayout(activity)
    private val buttons = FrameLayout(activity)
    private val gridHost = FrameLayout(activity)
    private val grid = LinearLayout(activity)
    private val gridTitle = TextView(activity)
    private val gridHint = TextView(activity)
    private val gridDots = FrameLayout(activity)
    /** 「更多」面板的居中容器（网格[+点列行] + 提示），落点由 [PlayerNewUiFormSpec.gridFit] 算。 */
    private var gridCenterBlock: LinearLayout? = null
    /** 方屏面板右上角收起按钮（圆屏没有，设计稿 morePanel 口径）。 */
    private var gridCollapse: ImageButton? = null
    /** 最近一次算出的网格自适应几何；面板开着时尺寸变化要按它重摆。 */
    private var currentFit: PlayerNewUiFormSpec.GridFit? = null
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

    /** 拖动预览的缩略图（26.11.27）：贴在文字上方，内容由 [updateSeekThumb] 按当前位置换。 */
    private val seekThumb = SeekThumbView(activity)

    /** 最近一次的拖动位置（ms）；拼版加载回来后按它重算该显示哪一格。 */
    private var seekThumbPositionMs = 0L

    /**
     * 拖动预览的缩略图数据，由 `PlayerActivity` 取到后设入；null = 这个视频没有快照。
     * 后台线程写、主线程读，所以 `@Volatile`。
     */
    @Volatile
    var videoShot: com.RobinNotBad.BiliClient.model.VideoShot? = null

    private val mirrors = ArrayList<Pair<View, View>>()

    /**
     * 设计稿强调色（开关「已开启」）= [ColorScheme.PRIMARY_LIGHT]，跟主题走。
     * **必须声明在这里**：init 块里的 buildSeekText() 要用它，
     * 而 Kotlin 的字段按源码顺序初始化，声明在后面的属性那时还是 0（等于全透明）。
     */
    private val accentSoft = ColorScheme.PRIMARY_LIGHT

    /** 已播进度色：封面取色优先（G06），没取到就用主题色。同样必须声明在 init 之前。 */
    private var progressAccent = ColorScheme.PLAYER_PROGRESS_FILL

    /** 方屏进度行的自绘渲染器；封面取色回来时要改它的已播色，所以留引用。 */
    private var progressRenderer: PlayerNewProgressRenderer? = null

    /**
     * 直播态（设计稿 A2）：直播没有进度概念——圆屏的弧、方屏的进度行整行让位给
     * 「直播 pill + 在看人数」，中心键保留。由 Activity 在直播分支调 [setLiveMode] 开启，
     * 本页生命周期内不会退出直播态，标志只置真。
     */
    private var liveNow = false
    private val liveRow = LinearLayout(activity)
    private val livePill = LinearLayout(activity)
    private val livePillDot = View(activity)
    private val livePillText = TextView(activity)
    private val liveOnline = TextView(activity)
    private var livePillBg: GradientDrawable? = null
    private var liveDotBg: GradientDrawable? = null

    /** 当前是否锁定：锁定期间 closeList/closeGrid 不许把控制层又放出来。 */
    private var lockedNow = false

    /** 用户主动收起（单击 / 手势）待生效一次；暂停时自动隐藏被拒，但主动收起照收。 */
    private var manualHidePending = false

    /** 槽位按钮 → key。Activity 会随时改这些按钮的图标（弹幕开关、锁定、播放暂停），
     * 靠这张表把它们重新拉回设计稿那一套矢量图标。 */
    private val designViews = LinkedHashMap<View, String>()

    /** 上一次由本类设置的 drawable 实例，用来分辨「还是我设的」与「Activity 又改回去了」。 */
    private val appliedIcon = HashMap<View, android.graphics.drawable.Drawable?>()
    private var layout = PlayerUiLayout.DEFAULT

    /**
     * 本次进播放器时的界面形态（设置 → 播放器界面形态，手动切换、重进生效）。
     * 圆屏的几何由 [PlayerNewUiGeometry] 的极坐标单独负责，形态只作用于方屏。
     */
    private val form = PlayerNewUiFormSpec.of(
        SharedPreferencesUtil.getString(SettingsKeys.PLAYER_UI_FORM, SettingsKeys.PLAYER_FORM_WATCH)
    )

    /** 方屏按形态取的几何规格；圆屏一律回落到手表基准（圆屏只存在于手表形态）。 */
    private val spec: PlayerNewUiFormSpec.Spec
        get() = if (round) PlayerNewUiFormSpec.WATCH else form

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
        overlay.addView(top, FrameLayout.LayoutParams(-1, dp(if (round) 46f else spec.topBarHeight), Gravity.TOP))
        top.setBackgroundColor(Color.TRANSPARENT)
        oldControl.visibility = View.VISIBLE
        arrangeTop()

        // 设计稿的压暗范围：圆屏顶 76 / 底 96，方屏顶 66 / 底 110。
        overlay.addView(gradient(true), FrameLayout.LayoutParams(-1, dp(if (round) 76f else spec.scrimTop), Gravity.TOP))
        overlay.addView(gradient(false), FrameLayout.LayoutParams(-1, dp(if (round) 96f else spec.scrimBottom), Gravity.BOTTOM))
        // 渐变必须在顶栏下面：刚搬进来的顶栏目前在末尾，调整绘制顺序。
        top.bringToFront()
        overlay.addView(buttons, FrameLayout.LayoutParams(-1, -1))
        buttons.bringToFront()

        val playSize = playSizeDp().toFloat()
        play.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x6b0a080c)
            setStroke(dp(1), 0x24ffffff)
        }
        play.scaleType = ImageView.ScaleType.CENTER_INSIDE
        // 内边距取「热区减图标」的一半，放大后图标才跟着变大（手表 56-24 → 16，与设计稿一致）。
        val playPad = ((playSize - (if (round) 22f else spec.playIconSize)) / 2f).coerceAtLeast(0f)
        play.setPadding(dp(playPad), dp(playPad), dp(playPad), dp(playPad))
        buttons.addView(play, centered(playSize.toInt(), playSize.toInt(), 0f, 0f))

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
            // 换成「距底」锚定后，屏幕高矮都不会把这一行挤出画面。
            elapsed.setTextSize(spec.textSmall)
            duration.setTextSize(spec.textSmall)
            duration.alpha = 0.75f
            elapsed.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            duration.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            // 先按当前能量到的屏幕高度摆一次；root 还没测量时这只是个临时值，
            // root 布局完成后 placeBottomAnchored() 会再按真实宽高摆一遍（见 overlay 的 post）。
            overlay.addView(elapsed, FrameLayout.LayoutParams(dp(spec.timeWidth), dp(spec.timeHeight), Gravity.START))
            overlay.addView(duration, FrameLayout.LayoutParams(dp(spec.timeWidth), dp(spec.timeHeight), Gravity.END))
            // 整行改成自绘：旧样式（波形压在轨道上、滑块是小电视）与设计稿对不上。
            seekbar.setPadding(0, 0, 0, 0)
            progressRenderer = PlayerNewProgressRenderer(seekbar, spec.scale)
            seekbar.customRenderer = progressRenderer!!::draw
            overlay.addView(seekbar, FrameLayout.LayoutParams(-1, dp(spec.trackRowHeight)))
        } else {
            detach(progressText)
            progressText.gravity = Gravity.CENTER
            progressText.setPadding(0, 0, 0, 0)
            // 设计稿：中心键下方一行「当前 / 总长」，top 72、行高 13、字号 10。
            // 落点与字号都按实际直径缩放，否则小表盘上这一行会压到拖动预览三行。
            placeRoundText()
            seekbar.visibility = View.GONE
        }

        // 沉浸态细进度线（设计稿 squareNew 的 immerse 分支）：left 30、宽 180、top 272、高 2、圆角 2，
        // 底色白 18%、已播跟主题进度色（封面取色回来由 setCoverAccent 覆盖）。
        // 注意它不是贴屏幕底边的那条 —— 底下还留 6dp。
        idleTrack = View(activity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(0x2effffff) }
        }
        idleFill = View(activity).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(progressAccent) }
        }
        overlay.addView(idleTrack, FrameLayout.LayoutParams(0, dp(spec.idleHeight)))
        // 细进度线给满宽度、靠 scaleX 表达进度（见 syncProgress），不再每次改 layoutParams。
        idleFill.pivotX = 0f
        idleFill.scaleX = 0f
        overlay.addView(idleFill, FrameLayout.LayoutParams(0, dp(spec.idleHeight)))
        // 初始态是「控制层显示」：这两条线属于沉浸态，必须一开始就藏起来。
        // 只靠 setControlsVisible 会漏掉第一次起播——那时还没人调它，addView 的默认
        // VISIBLE 会直接画在底边，看起来就像多了一条来路不明的进度条。
        idleTrack.visibility = View.GONE
        idleFill.visibility = View.GONE

        // 直播态那行「直播 pill + 在看人数」（设计稿 A2 / live）：pill 是强调色 20% 底 +
        // 强调色小圆点 + 「直播」二字，右边跟人数。落点与字号在 placeLiveRow() 里按屏形缩放，
        // 这里只搭结构；非直播永远 GONE，老播放器分支根本不会走到本类。
        liveRow.orientation = LinearLayout.HORIZONTAL
        liveRow.gravity = Gravity.CENTER
        liveRow.isBaselineAligned = false
        livePill.orientation = LinearLayout.HORIZONTAL
        livePill.gravity = Gravity.CENTER_VERTICAL
        livePill.isBaselineAligned = false
        livePillBg = GradientDrawable().apply {
            cornerRadius = dp(999).toFloat()
            setColor(accentWithAlpha(0x33))
        }
        livePill.background = livePillBg
        liveDotBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(progressAccent)
        }
        livePillDot.background = liveDotBg
        livePillText.apply {
            text = activity.getString(R.string.player_live_pill)
            setTextColor(accentSoft)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }
        livePill.addView(livePillDot, LinearLayout.LayoutParams(dp(5), dp(5)).apply { marginEnd = dp(4) })
        livePill.addView(livePillText, LinearLayout.LayoutParams(-2, -2))
        liveOnline.apply {
            setTextColor(Color.WHITE)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }
        liveRow.addView(livePill, LinearLayout.LayoutParams(-2, -2))
        liveRow.addView(liveOnline, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
        liveRow.visibility = View.GONE
        overlay.addView(liveRow, FrameLayout.LayoutParams(-2, dp(16), Gravity.TOP or Gravity.CENTER_HORIZONTAL))

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
        // root 在 init 期间还没测量（height 为 0），贴底的那些元素必须等真实宽高出来再摆一次，
        // 否则会按 displayMetrics 的全屏高度算，被状态栏/导航栏顶掉一段。
        overlay.post { placeSizeDependent() }
        // 旋转屏幕 / 分屏改变尺寸时，锚在底边的东西也要跟着重摆一次。
        // 这里只补「贴底元素」这一半；按钮的落位由 Activity 在 onConfigurationChanged 里
        // 调 applyPlayerButtonLayout() → render() 负责（那边还要重算槽位容量）。
        overlay.addOnLayoutChangeListener { _, left, top, right, bottom, oL, oT, oR, oB ->
            if (right - left != oR - oL || bottom - top != oB - oT) placeSizeDependent()
        }

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
            topMargin = dp(if (round) 28f else spec.lockHintTop)
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
        root.addView(listHost, FrameLayout.LayoutParams(-1, -1))
        listHost.visibility = View.GONE
        // 锁定三层的 z 序必须固定成「控制层 < 列表层 < lock_overlay < lockLayer < lock_holder」：
        // lock_overlay 是吞触摸的透明遮罩，必须压在控制层/列表层之上，否则锁定期间还能点到按钮；
        // lockLayer（顶部提示胶囊 + 锁底圆）要压在 lock_overlay 之上，提示才画得出来；
        // lock_holder 里的锁按钮最后顶上来，点它才解锁得了。这三句不能删，删了锁定就失效。
        root.findViewById<View>(R.id.lock_overlay).bringToFront()
        lockLayer.bringToFront()
        root.findViewById<View>(R.id.lock_holder).bringToFront()
    }

    /** seek 预览当前是否开着：设计稿 seek 态不画弹幕（vidLayer 的 noDm），这里顺手把弹幕层让开。 */
    private var seekPreviewShown = false

    private fun buildSeekText() {
        seekDelta.apply {
            setTextColor(accentSoft)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        seekMain.apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            // 28sp 默认还带 ~4.5dp 的字体内边距，34dp 的行会把它裁掉一截；关掉才装得下。
            includeFontPadding = false
        }
        seekTotal.apply {
            setTextColor(Color.WHITE)
            alpha = 0.7f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        seekText.addView(seekDelta, FrameLayout.LayoutParams(-1, 0))
        seekText.addView(seekMain, FrameLayout.LayoutParams(-1, 0))
        seekText.addView(seekTotal, FrameLayout.LayoutParams(-1, 0))
        // 缩略图最后加（压在文字之下也无所谓，两者纵向不重叠），尺寸与落点在 placeSeekRows 里摆
        seekText.addView(seekThumb, FrameLayout.LayoutParams(0, 0))
        placeSeekRows()
    }

    /**
     * 三行预览文字的字号与落点：尺寸变了要重摆，所以与样式分开，可反复调用。
     * 圆屏落点交给纯函数按实际直径算（设计稿 233dp 上是 87 / 103 / 141，
     * 原来写死 78 / 94 / 132 会压住中心键下方 top 72 的「当前 / 总长」行）；
     * 方屏按屏幕中心 ±偏移锚定。字号必须跟着行高一起缩，否则小表盘上行矮了字没矮，照样裁。
     */
    private fun placeSeekRows() {
        val bands = if (round) PlayerNewUiGeometry.roundSeekRows(widthDp()) else null
        val heights = floatArrayOf(
            bands?.get(0)?.height ?: spec.seekDeltaHeight,
            bands?.get(1)?.height ?: spec.seekMainHeight,
            bands?.get(2)?.height ?: spec.seekTotalHeight,
        )
        val tops = bands?.map { it.top } ?: spec.seekRows(heightDp())
        val f = roundScale()
        seekDelta.textSize = if (round) 11f * f else spec.textSmall + 1f
        seekMain.textSize = if (round) 28f * f else spec.seekMainText
        seekTotal.textSize = if (round) 10f * f else spec.textSmall
        listOf(seekDelta, seekMain, seekTotal).forEachIndexed { index, view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.height = dp(heights[index])
                it.topMargin = dp(tops[index])
                view.layoutParams = it
            }
        }
        placeSeekThumb()
    }

    /**
     * 缩略图的落点与大小。
     *
     * 圆屏放在三行文字**上方**、方屏同理，比例各按实测余量取：
     * 圆屏直径 28%（40% 宽的图会压到标题），方屏屏宽 40%（间隙只留 2dp，否则顶进顶栏）。
     * 判断「这块空间到底放不放得下」交给 [PlayerNewUiFormSpec.Spec.seekThumbFits]：
     * 200×200 的小方屏与高约 100dp 的长条屏都放不下，此时整块隐藏，
     * 预览退回到「只有三行文字」——不能画到屏幕外或压到顶栏上。
     */
    private fun placeSeekThumb() {
        val lp = seekThumb.layoutParams as? FrameLayout.LayoutParams ?: return
        val width = widthDp()
        val height = heightDp()
        if (round) {
            val band = PlayerNewUiGeometry.roundSeekThumbBand(width)
            lp.width = dp(PlayerNewUiGeometry.roundSeekThumbWidth(width))
            lp.height = dp(band.height)
            lp.topMargin = dp(band.top)
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            seekThumb.cornerRadius = dp(4f).toFloat()
            seekThumb.visibility = View.VISIBLE
        } else {
            if (!spec.seekThumbFits(width, height)) {
                seekThumb.visibility = View.GONE
                return
            }
            lp.width = dp(spec.seekThumbWidth(width))
            lp.height = dp(spec.seekThumbHeight(width))
            lp.topMargin = dp(spec.seekThumbTop(width, height))
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            seekThumb.cornerRadius = dp(3f).toFloat()
            seekThumb.visibility = View.VISIBLE
        }
        seekThumb.layoutParams = lp
    }

    /** 上一次打日志的格子，避免拖动时每帧刷 logcat。 */
    private var loggedCropKey = -1

    /** 换到新格子时留一条 D 级日志，「缩略图不出现」时能一眼看出卡在哪一步。 */
    private fun logCropOnce(crop: VideoShotGrid.Crop) {
        val key = crop.sheetIndex * 100000 + crop.rect.top * 1000 + crop.rect.left
        if (key == loggedCropKey) return
        loggedCropKey = key
        Logu.d(
            "缩略图预览",
            "裁剪 拼版#${crop.sheetIndex} (${crop.rect.left},${crop.rect.top})-(${crop.rect.right},${crop.rect.bottom})"
        )
    }

    /**
     * 按当前位置换缩略图。
     *
     * 拖动是高频事件，这里只做「查缓存 → 设一格」，没缓存就画底衬并触发一次异步加载；
     * 回包时按 [seekThumbPositionMs] 重算，避免用户已经拖到别处了画面却被旧回包盖回去。
     */
    private fun updateSeekThumb(positionMs: Long) {
        seekThumbPositionMs = positionMs
        val shot = videoShot
        val crop = if (shot == null) null else VideoShotGrid.cropFor(shot, positionMs)
        if (shot == null || crop == null) {
            seekThumb.show(null, null)
            return
        }
        val url = shot.images.getOrNull(crop.sheetIndex) ?: run {
            seekThumb.show(null, null)
            return
        }
        val bitmap = VideoShotLoader.cached(url)
        if (bitmap != null) {
            logCropOnce(crop)
            seekThumb.show(bitmap, crop.rect)
            return
        }
        seekThumb.show(null, null)
        VideoShotLoader.load(activity, url) { loadedUrl, loaded ->
            val current = videoShot ?: return@load
            val currentUrl = current.images.getOrNull(
                VideoShotGrid.cropFor(current, seekThumbPositionMs)?.sheetIndex ?: -1
            )
            if (loadedUrl != currentUrl) return@load
            seekThumb.show(loaded, VideoShotGrid.cropFor(current, seekThumbPositionMs)?.rect)
        }
    }

    /**
     * 圆屏「当前 / 总长」那一行：落点、行高、字号全按实际直径缩放。
     * init 时 root 还没测量，只能先摆个临时值；layout 变化后再摆一次（见 placeRoundText 的调用点）。
     */
    private fun placeRoundText() {
        if (!round) return
        val size = widthDp()
        val band = PlayerNewUiGeometry.roundProgressTextBand(size)
        progressText.textSize = 10f * roundScale()
        progressText.includeFontPadding = false
        val width = (130f * size / PlayerNewUiGeometry.REFERENCE_SIZE_DP).toInt().coerceAtLeast(1)
        val params = FrameLayout.LayoutParams(dp(width), dp(band.height), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        params.topMargin = dp(band.top)
        if (progressText.parent === overlay) progressText.layoutParams = params
        else overlay.addView(progressText, params)
    }

    /**
     * 进入直播态（设计稿 A2）：进度类元素全部让位，中心键下方（圆屏）/ 进度行位置（方屏）
     * 换成「直播 pill + 在看人数」。
     *
     * 根因：旧实现 [setControlsVisible] 无条件把弧放出来，直播没有进度，弧就是一条空弧；
     * 方屏则留着一条画不出进度的轨道。这里把弧 / 沉浸细线 / 进度行 / 圆屏时间行一起藏掉，
     * 并在显隐切换里用 [liveNow] 守卫，自动隐藏再也不会把它们放回来。
     *
     * 中心键保留（设计稿 A2 有暂停键）：Activity 直播分支把 btn_control 置 GONE 发生在
     * 控制器构造**之后**，所以本方法必须在那条分支里调，才能把中心键重新放出来。
     */
    fun setLiveMode(live: Boolean) {
        if (liveNow == live) return
        liveNow = live
        if (!live) {
            liveRow.visibility = View.GONE
            return
        }
        arc?.visibility = View.GONE
        idleTrack.visibility = View.GONE
        idleFill.visibility = View.GONE
        seekbar.visibility = View.GONE
        elapsed.visibility = View.GONE
        duration.visibility = View.GONE
        progressText.visibility = View.GONE
        play.visibility = View.VISIBLE
        refreshLiveOnline()
        placeLiveRow()
        liveRow.visibility = if (shown) View.VISIBLE else View.GONE
    }

    /**
     * 直播行的落点与字号：圆屏按 [PlayerNewUiGeometry.roundLiveBand]（设计 top70 行高14）
     * 随直径缩；方屏整行替换进度行——行高 16、中线仍在 progressCenterY（距底 58），
     * 底部按钮不跳。pill 字号 9、人数 10，都乘形态/直径系数。
     */
    private fun placeLiveRow() {
        if (!liveNow) return
        val f = if (round) roundScale() else spec.scale
        val band = if (round) PlayerNewUiGeometry.roundLiveBand(widthDp()) else null
        val rowH = band?.height ?: spec.s(16f)
        val top = band?.top ?: (spec.progressCenterY(heightDp()) - spec.s(8f))
        livePillText.textSize = 9f * f
        liveOnline.textSize = 10f * f
        livePill.setPadding(dp(6f * f), dp(1f * f), dp(6f * f), dp(1f * f))
        (livePillDot.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.width = dp(5f * f)
            it.height = dp(5f * f)
            it.marginEnd = dp(4f * f)
            livePillDot.layoutParams = it
        }
        (liveOnline.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.marginStart = dp((if (round) 4f else 6f) * f)
            liveOnline.layoutParams = it
        }
        (liveRow.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.width = -2
            it.height = dp(rowH)
            it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            it.topMargin = dp(top)
            liveRow.layoutParams = it
        }
    }

    /**
     * 刷新在看人数：WS 的 WATCHED_CHANGE 把已格式化的 text_large 写进 online_number，
     * 这里经 [PlayerLivePolicy.onlineText] 整理（纯数字走 toWan）；拿不到 / 为 0 用兜底文案。
     * 由 [sync] 的 400ms 镜像定时器驱动，直播没有 onlineTimer，不另起定时器。
     */
    private fun refreshLiveOnline() {
        val formatted = PlayerLivePolicy.onlineText(liveOnlineRaw())
        liveOnline.text = if (formatted == null) {
            activity.getString(R.string.player_live_online_unknown)
        } else {
            activity.getString(R.string.player_live_online, formatted)
        }
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
            // 圆屏那行「当前 / 总长」在 seek 态被三行预览替掉了，松手要还回来（直播态没有这行）。
            if (round) progressText.visibility = if (shown && !liveNow) View.VISIBLE else View.GONE
            if (liveNow) liveRow.visibility = if (shown) View.VISIBLE else View.GONE
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
        // 设计稿 roundNew 的 seek 态不画中心键下方那行时间：留着会与「+mm:ss」挤在一起。
        if (round) progressText.visibility = View.GONE
        // 直播态理论上不会触发拖动预览（没有进度），真触发了也让直播行先让位。
        if (liveNow) liveRow.visibility = View.GONE
        seekDim.visibility = View.VISIBLE
        seekText.visibility = View.VISIBLE
        updateSeekThumb(pos)
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
        // 先把「这个视频用不上」的槽位摘掉（单 P 视频没有分P按钮、本地视频没有清晰度…），
        // 否则它们虽然 GONE，却仍然占着摆位的一格，底栏右边就会空出一块。
        // 三种形态共用这一道过滤，圆屏同样受益。
        layout = PlayerNewUiFormSpec.visibleOnly(newLayout) { key -> slotIsPresent(key) }
        closeGrid()
        buttons.removeAllViews()
        val playSize = playSizeDp()
        buttons.addView(play, centered(playSize, playSize, 0f, 0f))
        mirrors.clear()
        if (round) {
            placeRound()
        } else if (root.height > 0) {
            placeSquare()
        } else {
            // root 还没测量（首帧之前就被 render 调到了）：这时按 displayMetrics 摆出来的
            // 「距底」坐标是错的，等真实宽高出来再摆一次，垫上第一步的空视图。
            overlay.post {
                if (!round) {
                    buttons.removeAllViews()
                    buttons.addView(play, centered(playSizeDp(), playSizeDp(), 0f, 0f))
                    mirrors.clear()
                    placeSquare()
                    sync()
                    applyDesignIcons()
                }
            }
        }
        sync()
        applyDesignIcons()
    }

    /** 圆屏：沿用设计稿的极坐标，左右各 3 格、底栏 4 格，其余进「更多」。 */
    private fun placeRound() {
        val (_, overflow) = PlayerNewUiGeometry.visibleAndOverflow(layout, true)
        val left = PlayerNewUiGeometry.sideSpec(layout.left, true)
        val right = PlayerNewUiGeometry.mirrorX(PlayerNewUiGeometry.sideSpec(layout.right, true), widthDp())
        val bottom = PlayerNewUiGeometry.bottomSpec(layout.bottomLeft + layout.bottomRight, true, widthDp())
        var remaining = overflow
        if (placeSpec(left, remaining)) remaining = emptyList()
        if (placeSpec(right, remaining)) remaining = emptyList()
        placeSpec(bottom, remaining)
        // 没有显式组时，溢出仍必须有入口，不能静默丢按钮。
        if (overflow.isNotEmpty() && !containsGroup(layout)) {
            placeAt(moreButton(overflow.flatMap { keysOf(it) }), PlayerNewUiGeometry.polar(88f, 305f))
        }
    }

    /** 方屏：坐标一律由 [PlayerNewUiFormSpec] 按边缘锚定，屏幕高矮/宽窄都不会越界。 */
    private fun placeSquare() {
        val width = widthDp()
        val height = heightDp()
        val bottomY = spec.bottomY(height)
        // 摆法只由 PlayerNewUiFormSpec.plan 决定，这里只负责照单落位。
        val plan = PlayerNewUiFormSpec.plan(spec, layout, width, containsGroup(layout))
        var overflow = plan.overflow
        fun place(slot: PlayerUiLayout.Slot, x: Float, y: Float) {
            overflow = placeSquareSlot(slot, x, y, overflow)
        }
        if (spec.sideColumns) {
            val sideYs = spec.sideYs(height)
            plan.left.forEachIndexed { i, s -> place(s, spec.sideX, sideYs[i]) }
            plan.right.forEachIndexed { i, s -> place(s, spec.sideRightX(width), sideYs[i]) }
        }
        // 底栏：播放键占正中一格，槽位按存档分列两侧；手表形态播放键仍在画面中心、整行居中。
        val placement = spec.bottomPlacement(
            plan.bottomLeft.size,
            plan.bottomRight.size + if (plan.needsMoreCell) 1 else 0,
            width,
        )
        plan.bottomLeft.forEachIndexed { i, s -> place(s, placement.leftXs[i], bottomY) }
        plan.bottomRight.forEachIndexed { i, s -> place(s, placement.rightXs[i], bottomY) }
        // 播放键并入底栏正中（手机 / 长条屏）；手表仍留在画面中心，由 init 摆放。
        if (spec.playInRow) placement.playX?.let { placePlay(it, bottomY) }
        // 「更多」借格时排在最右（它是放不下才出现的兜底入口）。
        if (plan.needsMoreCell) {
            val moreX = placement.rightXs.lastOrNull() ?: (width / 2f)
            placeAt(moreButton(overflow.flatMap { keysOf(it) }), PlayerNewUiGeometry.Point(moreX, bottomY))
            overflow = emptyList()
        }
        // 有显式组却没被摆到（槽位全塞满了）时，溢出的按钮也不能丢。
        if (overflow.isNotEmpty()) {
            val moreX = placement.rightXs.lastOrNull() ?: placement.playX ?: (width / 2f)
            placeAt(moreButton(overflow.flatMap { keysOf(it) }), PlayerNewUiGeometry.Point(moreX, bottomY))
        }
    }

    /**
     * 播放键并入底栏。**不能走 [placeAt]**：那会把已经 `addView` 过的 play 再加一次，
     * `ViewGroup.addView` 对已在树上的子 View 会抛 IllegalStateException。
     * 这里只改它自己的 layoutParams。
     */
    private fun placePlay(x: Float, y: Float) {
        val size = playSizeDp()
        val half = dp(size / 2f)
        val lp = play.layoutParams as FrameLayout.LayoutParams
        lp.width = dp(size)
        lp.height = dp(size)
        lp.gravity = 0
        lp.leftMargin = (x * density).toInt() - half
        lp.topMargin = (y * density).toInt() - half
        play.layoutParams = lp
    }

    /**
     * 方屏落一个槽位，返回仍然没被接收的溢出。
     *
     * 「更多」按钮会把溢出并进自己的键表（之后就不必再补一个入口），其余槽位原样把溢出传回去。
     */
    private fun placeSquareSlot(
        slot: PlayerUiLayout.Slot,
        x: Float,
        y: Float,
        overflow: List<PlayerUiLayout.Slot> = emptyList(),
    ): List<PlayerUiLayout.Slot> = when (slot) {
        is PlayerUiLayout.Slot.Button -> {
            placeAt(button(slot.key), PlayerNewUiGeometry.Point(x, y))
            overflow
        }
        is PlayerUiLayout.Slot.Group -> {
            val keys = (if (slot.keys.isEmpty()) PlayerUiLayout.unplacedKeys(layout) else slot.keys) +
                overflow.flatMap { keysOf(it) }
            placeAt(moreButton(keys), PlayerNewUiGeometry.Point(x, y))
            emptyList()
        }
    }

    /** 圆屏弧形进度（方屏为 null）：高能数据加载后由 Activity 把真实曲线同步过来。 */
    fun arcProgress(): PlayerArcProgressView? = arc

    /**
     * 封面取色（G06）：取到的颜色优先于主题色，喂给弧形进度、方屏自绘进度行与沉浸细线。
     *
     * 方屏的 [seekbar] 被 customRenderer 接管后，它自己的 setAccentColor 只改旧画笔、画不出来，
     * 所以必须在这里把颜色转交给渲染器。取不到封面色时 Activity 不调这里，进度色留在主题色。
     * 轨道 / 缓冲 / 热度按设计稿是白 22%/38%/30%，不跟主题也不跟封面，这里不动它们。
     */
    fun setCoverAccent(color: Int) {
        progressAccent = color
        arc?.setAccentColor(color)
        progressRenderer?.setAccentColor(color)
        (idleFill.background as? GradientDrawable)?.setColor(color)
        idleFill.invalidate()
        // 直播 pill 的底色（强调色 20%）与内点也跟封面取色走，设计稿 .pill 口径。
        livePillBg?.setColor(accentWithAlpha(0x33))
        liveDotBg?.setColor(color)
        liveRow.invalidate()
    }

    /**
     * 用户主动收起控制层（单击画面 / 手势）。必须与自动隐藏分开：
     * 暂停时自动隐藏被设计稿禁掉了，但用户单击还是得能收起来，否则暂停后控件再也关不掉。
     * 只对下一次 [setControlsVisible] 生效一次。
     */
    fun requestManualHide() {
        manualHidePending = true
    }

    /**
     * 显隐控制层，返回**是否真的执行了**。
     * 设计稿 NOTES.paused：暂停时控件常驻，所以自动隐藏会被拒（返回 false），
     * 调用方据此跳过连带的收尾动作；用户主动收起（[requestManualHide]）不受此限。
     */
    fun setControlsVisible(visible: Boolean): Boolean {
        val manual = manualHidePending
        manualHidePending = false
        if (!visible && !PlayerNewUiControlPolicy.autoHideAllowed(playingNow(), manual)) return false
        shown = visible
        overlay.visibility = View.VISIBLE
        top.visibility = if (visible) View.VISIBLE else View.GONE
        buttons.visibility = if (visible) View.VISIBLE else View.GONE
        // 直播态：进度行 / 弧 / 沉浸细线 / 圆屏时间行一律不放出来（设计稿 A2），
        // 那行位置由「直播 pill + 人数」顶着，见 setLiveMode。
        elapsed.visibility = if (visible && !round && !liveNow) View.VISIBLE else View.GONE
        duration.visibility = elapsed.visibility
        seekbar.visibility = if (visible && !round && !liveNow) View.VISIBLE else View.GONE
        // 整屏手势层（layout_control）永远不能 GONE：单击显隐、双击暂停、长按倍速、
        // 滑动 seek 全挂在它的监听器上，一旦隐藏就再没有 View 能接住触摸，
        // 控制层收起后单击唤不回来（死锁）。旧实现同样只隐藏它的子层，从不隐藏它本身。
        oldControl.visibility = View.VISIBLE
        if (round) progressText.visibility = if (visible && !seekPreviewShown && !liveNow) View.VISIBLE else View.GONE
        arc?.visibility = if (round && !liveNow && (visible || idleEnabled())) View.VISIBLE else View.GONE
        arc?.setIdle(!visible)
        arc?.isEnabled = visible && !liveNow
        idleTrack.visibility = if (!round && !visible && idleEnabled() && !liveNow) View.VISIBLE else View.GONE
        idleFill.visibility = idleTrack.visibility
        liveRow.visibility = if (liveNow && visible) View.VISIBLE else View.GONE
        if (!visible) {
            closeGrid()
            closeList()
        }
        applyDesignIcons()
        syncProgress()
        return true
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
     * 把主界面按钮统一换成设计稿的图标，开着的染成主题强调色（设计稿 #FF8CB0）。
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
        // 直播人数没有独立定时器（直播 onlineTimer=null），借这条镜像心跳刷新。
        if (liveNow) refreshLiveOnline()
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
        val lp = FrameLayout.LayoutParams(
            dp(if (round) 184f else spec.s(216f)),
            dp(if (round) 178f else spec.s(214f)),
            if (round) Gravity.CENTER else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        if (!round) lp.bottomMargin = dp(spec.s(8f))
        listHost.addView(card, lp)
        // 弹幕是独立绘制层，普通 View 的压暗盖不住它，列表打开期间让它自己藏起来
        onPanelShown(true)
        listHost.visibility = View.VISIBLE
    }

    /**
     * 清晰度面板（设计稿 qualityPanel）。和通用 [showList] 只差排版：
     * 圆屏是一列直接浮在压暗画面上的选项（选中行背后一条 168×35 粉色胶囊），
     * 方屏是从 y=96 到底的抽屉（圆角 18、深底 + 顶部 1px 分隔线）。
     *
     * [vipFlags] 与 [items] 一一对应，标记哪些档位要大会员徽标（判据见 QualityAccessPolicy.requiresVip）。
     */
    fun showQualityPanel(
        title: CharSequence,
        items: List<String>,
        selected: Int,
        vipFlags: List<Boolean> = emptyList(),
        onClick: (Int) -> Unit,
    ) {
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
                    setColor(accentWithAlpha(0x29))
                }
            }, FrameLayout.LayoutParams(dp(168), dp(35)).apply {
                topMargin = dp(selectedY - 17.5f)
                leftMargin = dp(32.5f)
            })
            items.forEachIndexed { index, text ->
                val y = rowY(index)
                listHost.addView(qualityRow(text, index == selected, 12f, dp(14), dp(6), true, vipFlags.getOrElse(index) { false }, index, items.lastIndex) {
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
                textSize = if (round) 11f else spec.textSmall + 1f
                setTextColor(Color.WHITE)
                alpha = 0.6f
            }, FrameLayout.LayoutParams(-2, dp(spec.s(16f))).apply {
                topMargin = dp(spec.s(12f)); leftMargin = dp(spec.s(18f))
            })
            items.forEachIndexed { index, text ->
                val row = qualityRow(text, index == selected, if (round) 13f else spec.textSmall + 2f, dp(16), dp(6), false, vipFlags.getOrElse(index) { false }, index, items.lastIndex) {
                    closeList(); onClick(index)
                }
                if (index == selected) row.background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(accentWithAlpha(0x24))
                }
                row.setPadding(dp(8), 0, dp(8), 0)
                sheet.addView(row, FrameLayout.LayoutParams(-1, dp(spec.s(34f))).apply {
                    topMargin = dp(spec.s(32f) + index * spec.s(36f))
                    leftMargin = dp(spec.s(12f))
                    rightMargin = dp(spec.s(12f))
                })
            }
            listHost.addView(sheet, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(spec.sheetTop(heightDp())) })
        }
        onPanelShown(true)
        // 设计稿 quality 态整屏只有「压暗后的画面 + 面板」：顶栏、按钮、弧、时间行一律不画，
        // 所以整层控制层收起来，只留 listHost 这一层压暗。
        overlay.visibility = View.GONE
        listHost.visibility = View.VISIBLE
    }

    /** 当前进度强调色带上指定 alpha：胶囊底、选中行底都用它，跟着主题/封面取色走。 */
    private fun accentWithAlpha(alpha: Int) = (progressAccent and 0x00ffffff) or (alpha shl 24)

    /**
     * 大会员徽标（设计稿 .vip）：8sp、左右 4dp、圆角 3、1dp 描边，跟在档位名后面而不是单独一行。
     * 尺寸只走 spec（不乘 roundScale）：圆屏清晰度行高固定 20dp，放大就溢出了。
     */
    private fun vipBadge(): TextView = TextView(activity).apply {
        text = activity.getString(R.string.player_quality_vip_badge)
        textSize = spec.qualityBadgeText
        setTextColor(ColorScheme.VIP_COLOR)
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        setPadding(dp(spec.qualityBadgePadH), 0, dp(spec.qualityBadgePadH), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(spec.qualityBadgeRadius).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), (ColorScheme.VIP_COLOR and 0x00ffffff) or 0x99000000.toInt())
        }
    }

    /** 清晰度面板的一行：圆屏居中（文字 + 徽标 + 选中勾连排），方屏左对齐、勾贴右边。 */
    private fun qualityRow(
        text: String,
        selected: Boolean,
        textSize: Float,
        checkSize: Int,
        checkMargin: Int,
        centered: Boolean,
        vip: Boolean,
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
            // 加了徽标之后档位名可能被挤出去：限宽 + 尾部省略，别让整行截断或把勾顶出屏幕。
            maxWidth = dp(spec.qualityLabelMaxWidth(widthDp(), vip, selected, centered))
            ellipsize = TextUtils.TruncateAt.END
            gravity = if (centered) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
        }
        val check = if (selected) ImageView(activity).apply {
            setImageResource(R.drawable.ic_player_v2_check)
            setColorFilter(accentSoft)
        } else null
        val badge = if (vip) vipBadge() else null
        val badgeParams = LinearLayout.LayoutParams(-2, dp(spec.qualityBadgeHeight)).apply {
            gravity = Gravity.CENTER_VERTICAL
            marginStart = dp(spec.qualityBadgeMargin)
        }
        if (centered) {
            // 设计稿里「文字 + 勾」是一个整体居中的 flex 行（gap 6）——勾紧跟文字，
            // 而不是钉在屏幕右边缘：把勾放进水平 LinearLayout 再整体居中。徽标插在两者之间。
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                isBaselineAligned = false
            }
            line.addView(label, LinearLayout.LayoutParams(-2, -1))
            if (badge != null) line.addView(badge, badgeParams)
            if (check != null) {
                line.addView(check, LinearLayout.LayoutParams(checkSize, checkSize).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = checkMargin
                })
            }
            row.addView(line, FrameLayout.LayoutParams(-1, -1))
        } else {
            // 方屏抽屉：一行水平排「档位名 + 徽标 + 弹性空白 + 勾」，勾仍贴右。
            // 原来用 FrameLayout 把 label 铺满整行，徽标没地方插，只能改成 LinearLayout。
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isBaselineAligned = false
            }
            line.addView(label, LinearLayout.LayoutParams(-2, -1).apply { leftMargin = dp(8) })
            if (badge != null) line.addView(badge, badgeParams)
            line.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
            if (check != null) {
                line.addView(check, LinearLayout.LayoutParams(checkSize, checkSize).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = checkMargin
                    marginEnd = dp(8)
                })
            }
            row.addView(line, FrameLayout.LayoutParams(-1, -1))
        }
        return row
    }

    fun closeList() {
        listHost.visibility = View.GONE
        // showQualityPanel 期间整层控制层是藏起来的（设计稿 quality 态不画顶栏与按钮），关面板时按当前显隐态还原。
        // 锁定期间一律不许还原：parkLockButton() 会走到这里，无条件置 VISIBLE 会把刚藏好的控制层又放出来。
        overlay.visibility = if (shown && !lockedNow) View.VISIBLE else View.GONE
        // 「更多」网格也开着时不能把弹幕放回来
        if (gridHost.visibility != View.VISIBLE) onPanelShown(false)
    }

    fun closeTransientPanels() { closeGrid(); closeList() }

    /** 锁定时藏掉整个新版控制层、顶出锁定层；解锁时反过来，避免控制层压住 lock_holder。 */
    fun setLocked(locked: Boolean) {
        val entering = locked && !lockedNow
        lockedNow = locked
        if (locked) closeTransientPanels()
        overlay.visibility = if (locked) View.GONE else View.VISIBLE
        lockLayer.visibility = if (locked) View.VISIBLE else View.GONE
        if (locked) {
            positionLockRing()
            if (entering) showLockHint()
        } else {
            lockHint.removeCallbacks(lockHintFade)
            lockHint.animate().cancel()
            lockHint.alpha = 1f
        }
    }

    /**
     * 顶部「已锁定」胶囊提示（设计稿 NOTES.lock）：出现 [PlayerLockPolicy.EXIT_HINT_MS] 后淡出。
     * 淡出走 alpha 动画而不是直接 GONE —— 硬切在手表上看着像闪了一下就没了。
     * 解锁时取消计时并复原不透明度，下次锁定还能再提示一遍。
     */
    fun showLockHint() {
        if (!lockedNow) return
        lockHint.removeCallbacks(lockHintFade)
        lockHint.animate().cancel()
        lockHint.alpha = 1f
        lockHint.visibility = View.VISIBLE
        lockHint.postDelayed(lockHintFade, PlayerLockPolicy.EXIT_HINT_MS)
    }

    private val lockHintFade = Runnable {
        lockHint.animate().alpha(0f).setDuration(LOCK_HINT_FADE_MS).withEndAction {
            lockHint.visibility = View.GONE
        }.start()
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
     * 这里只搭**结构**，所有落点/字号/格尺寸由 [PlayerNewUiFormSpec.gridFit] 在
     * [renderGridPage] 里按真实屏幕算（见 [applyGridChrome]）——192dp 小方屏整块等比缩、
     * 圆屏整块落内接正方形且四角不切，热区恒 ≥24dp。结构沿用上一轮的居中容器：
     *
     * - 方屏：centerBlock(竖直, 内部 CENTER) 装 gridRow(网格 + 页码点横排) + 提示行；
     *   收起按钮是 gridHost 的绝对定位子层（顶栏时钟下方，极矮屏挪右下角）。
     * - 圆屏：centerBlock 装网格 + 提示行；页码点列直接挂 gridHost，绝对定位在网格右侧、
     *   对**屏幕中心**竖直居中（设计稿 dy116.5），不跟着网格块走。
     *
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
            typeface = Typeface.DEFAULT_BOLD
            gravity = if (round) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
        }
        // 初值只是占位：面板打开前 gridHost 是 GONE，首次 renderGridPage 会按 fit 全部重设。
        gridHost.addView(gridTitle, FrameLayout.LayoutParams(-2, dp(16)))
        grid.orientation = LinearLayout.VERTICAL
        grid.gravity = Gravity.CENTER
        val centerBlock = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            // 容器自身高度 = 可用区域（标题区以下、底边距以上），内部 CENTER 让
            // 「网格 + 提示」整组居中：行数变化只动网格顶边，整块中心不漂移。
            gravity = Gravity.CENTER
        }
        if (round) {
            centerBlock.addView(grid, LinearLayout.LayoutParams(-2, -2))
            gridHost.addView(gridDots, FrameLayout.LayoutParams(dp(24), dp(24)))
        } else {
            val gridRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isBaselineAligned = false
            }
            gridRow.addView(grid, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_VERTICAL })
            gridRow.addView(gridDots, LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                marginStart = dp(2)
                gravity = Gravity.CENTER_VERTICAL
            })
            centerBlock.addView(gridRow, LinearLayout.LayoutParams(-2, -2))
        }
        gridHint.apply {
            setTextColor(0x99ffffff.toInt())
            gravity = Gravity.CENTER
        }
        centerBlock.addView(gridHint, LinearLayout.LayoutParams(-2, dp(12)))
        gridHost.addView(centerBlock, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        })
        gridCenterBlock = centerBlock
        // 方屏收起按钮（设计稿 ib(214,25)：三点图标 more）；落点/热区由 fit 算，
        // 热区 ≥24dp 不缩，顶边压在顶栏时钟底边（32dp）以下，不压时钟。圆屏没有这个按钮。
        if (!round) {
            gridCollapse = ImageButton(activity).apply {
                setImageResource(R.drawable.ic_player_v2_more)
                contentDescription = activity.getString(R.string.player_more_panel_collapse)
                background = null
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setOnClickListener { closeGrid() }
            }
            gridHost.addView(gridCollapse, FrameLayout.LayoutParams(dp(24), dp(24)).apply {
                gravity = Gravity.TOP or Gravity.START
            })
        }
        overlay.addView(gridHost, FrameLayout.LayoutParams(-1, -1))
        gridHost.visibility = View.GONE
        gridHost.setOnTouchListener { _, event -> handleGridTouch(event) }
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
        // 控制层收起后按钮一直挂屏幕上、底部细进度线也跟着露出来。锁定期间同理不许恢复。
        if (shown && !lockedNow) {
            top.visibility = View.VISIBLE
            buttons.visibility = View.VISIBLE
        }
    }

    /**
     * 「更多」面板的翻页手势。
     *
     * 原来只挂 `onFling`——它要求手指**甩得够快**（速度阈值），慢慢拖、短距离拖都判不出来，
     * 用户体感就是「划不动」。这里补两条更宽容的判据：
     * - 快速甩动（沿用 onFling）；
     * - **按下到抬起纵向位移超过阈值**（慢速长拖也算），按方向翻页。
     * 两者都不满足才交回给点击（点空白关闭面板）。
     */
    private var gridDownX = 0f
    private var gridDownY = 0f
    private var gridDragged = false

    private fun handleGridTouch(event: android.view.MotionEvent): Boolean {
        gridGesture.onTouchEvent(event)
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                gridDownX = event.x
                gridDownY = event.y
                gridDragged = false
            }
            android.view.MotionEvent.ACTION_UP -> {
                val dy = event.y - gridDownY
                val dx = event.x - gridDownX
                val minDrag = dp(gridDragThresholdDp())
                if (!gridDragged && kotlin.math.abs(dy) >= minDrag &&
                    kotlin.math.abs(dy) > kotlin.math.abs(dx)
                ) {
                    flipGridPage(if (dy < 0) 1 else -1)
                    gridDragged = true
                }
                if (gridDragged) {
                    // 这一串手势已经被判成翻页：吞掉随后的 click，别顺手把面板关掉。
                    gridFlinged = true
                }
            }
        }
        return false
    }

    /** 判定一次「拖动翻页」所需的纵向位移（dp）：取屏幕高度的 6%，再夹在 24~56dp 之间。 */
    private fun gridDragThresholdDp(): Float =
        (heightDp() * 0.06f).coerceIn(24f, 56f)

    private fun flipGridPage(delta: Int) {
        val pages = PlayerNewUiGeometry.pages(gridKeys, gridPageSize())
        if (pages.size < 2) return
        gridPage = ((gridPage + delta) % pages.size + pages.size) % pages.size
        gridFlinged = true
        renderGridPage()
    }

    private val gridGesture by lazy {
        android.view.GestureDetector(activity, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent): Boolean = true
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                val pages = PlayerNewUiGeometry.pages(gridKeys, gridPageSize())
                if (pages.size < 2 || kotlin.math.abs(vy) < kotlin.math.abs(vx)) return false
                gridPage = if (vy < 0) (gridPage + 1) % pages.size else (gridPage - 1 + pages.size) % pages.size
                gridFlinged = true
                renderGridPage()
                return true
            }
        })
    }

    /**
     * 页数估计：按每页 9 格估，喂给 [PlayerNewUiFormSpec.gridFit] 约束点列。
     * 实际行数 ≥3（fit 保证 3 行放得下），所以实际页数 ≤ 估计值，点列约束偏保守、安全。
     */
    private fun gridPageEstimate(): Int =
        ((gridKeys.size + PlayerNewUiGeometry.GRID_PAGE_SIZE - 1) / PlayerNewUiGeometry.GRID_PAGE_SIZE).coerceAtLeast(1)

    /** 当前网格几何：面板开着用最近一次算好的，没开过就按当前屏幕现算。 */
    private fun gridFitNow(): PlayerNewUiFormSpec.GridFit =
        currentFit ?: PlayerNewUiFormSpec.gridFit(spec, round, widthDp(), heightDp(), gridPageEstimate())

    /**
     * 这一页最多放几格：按 fit 给出的可用高度（标题区以下、提示区以上）算行数，3 列铺开。
     *
     * 用户要求「手机播放器一页展示全部的更多按钮」——固定 9 格分页在横屏/长屏上会把
     * 明明放得下的按钮硬拆成两页。改成按实际空间算：放得下就一页全给。
     */
    private fun gridPageSize(): Int {
        val fit = gridFitNow()
        return PlayerNewUiGeometry.gridCapacity(fit.cellH, fit.gap, fit.availH)
    }

    /**
     * 把 fit 算出的面板「外壳」参数一次性落到视图上：标题、居中容器、网格本体、提示、
     * 页码点列、收起按钮。格子内容由 [renderGridPage] 逐格重建。
     *
     * [gridH] 是**当前页实际行数**的网格高（可能不足 3 行）；居中容器会把「网格 + 提示」
     * 整组在可用区域里居中，行数变化只动顶边、中心不漂移。
     */
    private fun applyGridChrome(fit: PlayerNewUiFormSpec.GridFit, pageCount: Int, gridH: Float) {
        gridTitle.textSize = fit.titleText
        (gridTitle.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.height = dp(fit.titleH)
            it.topMargin = dp(fit.titleTop)
            if (fit.round) {
                // 圆屏标题按文字宽度居中；写成 -1 加左右 margin 时文字会偏左半个 margin。
                it.width = -2
                it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                it.leftMargin = 0
            } else {
                // 方屏标题左对齐网格左缘，右缘不压收起按钮（fit 已扣好）。
                it.width = dp(fit.titleWidth)
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = dp(fit.titleLeft)
            }
            gridTitle.layoutParams = it
        }
        gridCenterBlock?.let { block ->
            (block.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.width = -2
                it.height = dp(fit.regionH)
                it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                it.topMargin = dp(fit.regionTop)
                block.layoutParams = it
            }
        }
        (grid.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.width = dp(fit.gridW)
            it.height = dp(gridH)
            grid.layoutParams = it
        }
        gridHint.textSize = fit.hintText
        (gridHint.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.height = dp(fit.hintH)
            it.topMargin = dp(fit.hintGap)
            gridHint.layoutParams = it
        }
        val dots = fit.dotsFor(pageCount)
        if (fit.round) {
            // 圆屏点列：gridHost 绝对定位，网格右侧、对屏幕中心竖直居中，四角不出表圈。
            (gridDots.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.width = dp(dots.hot)
                it.height = dp(dots.height)
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = dp(fit.dotsLeft)
                it.topMargin = dp(fit.dotsTopFor(pageCount))
                gridDots.layoutParams = it
            }
        } else {
            // 方屏点列：行内 CENTER_VERTICAL，被收起按钮顶下去时靠 topMargin 额外下移。
            (gridDots.layoutParams as? LinearLayout.LayoutParams)?.let {
                it.width = dp(dots.hot)
                it.height = dp(dots.height)
                it.marginStart = dp(2f * fit.unit)
                it.topMargin = dp(fit.dotsShiftFor(pageCount))
                it.gravity = Gravity.CENTER_VERTICAL
                gridDots.layoutParams = it
            }
        }
        gridCollapse?.let { collapse ->
            val icon = 20f * fit.unit
            val pad = dp(((fit.collapseSize - icon) / 2f).coerceAtLeast(0f))
            collapse.setPadding(pad, pad, pad, pad)
            (collapse.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.width = dp(fit.collapseSize)
                it.height = dp(fit.collapseSize)
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = dp(fit.collapseLeft)
                it.topMargin = dp(fit.collapseTop)
                collapse.layoutParams = it
            }
        }
    }

    private fun renderGridPage() {
        // 每次渲染都按真实屏幕重算 fit：旋转 / 分屏 / 小屏都不会再拿旧落点硬摆。
        val fit = PlayerNewUiFormSpec.gridFit(spec, round, widthDp(), heightDp(), gridPageEstimate())
        currentFit = fit
        val pageSize = PlayerNewUiGeometry.gridCapacity(fit.cellH, fit.gap, fit.availH)
        val pages = PlayerNewUiGeometry.pages(gridKeys, pageSize)
        if (pages.isEmpty()) { closeGrid(); return }
        gridPage = gridPage.coerceIn(0, pages.lastIndex)
        val pageKeys = pages[gridPage]
        val rows = ((pageKeys.size + PlayerNewUiGeometry.GRID_COLUMNS - 1) / PlayerNewUiGeometry.GRID_COLUMNS).coerceAtLeast(1)
        val gridH = fit.cellH * rows + fit.gap * (rows - 1)
        applyGridChrome(fit, pages.size, gridH)
        grid.removeAllViews()
        gridDots.removeAllViews()
        for (rowIndex in 0 until rows) {
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val perRow = PlayerNewUiGeometry.GRID_COLUMNS
            for (col in 0 until perRow) {
                val key = pageKeys.getOrNull(rowIndex * perRow + col)
                val primary = key?.let { slotOf(it) }
                val cell: View = if (primary != null && key != null) gridCell(primary, key, fit.unit) else View(activity)
                line.addView(cell, LinearLayout.LayoutParams(dp(fit.cellW), dp(fit.cellH)).apply {
                    if (col > 0) marginStart = dp(fit.gap)
                })
            }
            grid.addView(line, LinearLayout.LayoutParams(-2, dp(fit.cellH)).apply { if (rowIndex > 0) topMargin = dp(fit.gap) })
        }
        // 页码点：设计稿竖排在网格右侧，选中那颗拉长。视觉点随 fit 缩，
        // 但每颗仍套 ≥24dp 的可点热区（fit.dots.hot），spacing ≥ hot 保证热区互不重叠。
        val dots = fit.dotsFor(pages.size)
        pages.indices.forEach { index ->
            val active = index == gridPage
            val dot = View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(dots.dot / 2f).toFloat()
                    setColor(if (active) Color.WHITE else 0x59ffffff)
                }
            }
            val hot = FrameLayout(activity).apply {
                isClickable = true
                contentDescription = activity.getString(R.string.player_more_panel_page_dot, index + 1)
                addView(dot, FrameLayout.LayoutParams(dp(dots.dot), dp(if (active) dots.dotActive else dots.dot), Gravity.CENTER))
                setOnClickListener {
                    if (gridPage == index) return@setOnClickListener
                    gridPage = index
                    renderGridPage()
                }
            }
            gridDots.addView(hot, FrameLayout.LayoutParams(dp(dots.hot), dp(dots.hot)).apply {
                topMargin = dp(index * dots.spacing)
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

    /**
     * 一格：图标 20dp + 名称 9sp（设计稿 .cell），内部尺寸统一乘 [unit]（形态系数 × 网格 fit）。
     * 原按钮可能是文字按钮（±N 秒），那就把文字当图标位。
     */
    private fun gridCell(primary: View, key: String, unit: Float): View {
        val cell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(10f * unit).toFloat(); setColor(0x12ffffff) }
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
            textSize = 9f * unit
            setTextColor(if (on) accentSoft else Color.WHITE)
            gravity = Gravity.CENTER
        }
        cell.addView(glyph, LinearLayout.LayoutParams(dp(20f * unit), dp(20f * unit)))
        val labelRes = moreLabelRes(key)
        cell.addView(TextView(activity).apply {
            text = if (labelRes != 0) activity.getString(labelRes)
            else PlayerButtons.byKey(key)?.let { activity.getString(it.nameRes) } ?: ""
            textSize = 9f * unit
            setTextColor(if (on) accentSoft else Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-2, dp(15f * unit)).apply { topMargin = dp(4f * unit) })
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
        if (isDescendant(primary, buttons) || isDescendant(primary, grid)) {
            return mirror(primary, buttonDp(), iconDp(), key)
        }
        detach(primary)
        styleButton(primary, buttonDp(), iconDp())
        designViews[primary] = key
        return primary
    }

    /** 槽位热区与图标边长（dp）：圆屏按真实表盘缩放，方屏按形态。 */
    private fun buttonDp() = if (round) (36f * roundScale()).toInt().coerceAtLeast(1)
        else spec.buttonSize.toInt().coerceAtLeast(1)
    private fun iconDp() = if (round) (20f * roundScale()).toInt().coerceAtLeast(1)
        else spec.iconSize.toInt().coerceAtLeast(1)

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

    /**
     * 尺寸相关的落点重摆：方屏走贴底锚定，圆屏走「中心键下方时间行 + 拖动预览三行」。
     * init 时 root 还没测量，两边都得等真实宽高出来再摆一次。
     */
    private fun placeSizeDependent() {
        if (round) {
            placeRoundText()
            placeSeekRows()
        } else {
            placeBottomAnchored()
        }
        if (liveNow) placeLiveRow()
        // 「更多」面板开着时尺寸变了（旋转 / 分屏）：重算 fit 整套重摆；
        // 关着则下次打开时 renderGridPage 自然会按新尺寸算。
        if (gridHost.visibility == View.VISIBLE) renderGridPage()
    }

    /**
     * 把贴底锚定的元素按**真实**宽高摆一遍：进度行两端的两个时间、进度条本身、沉浸细进度线。
     *
     * 只在构造末尾（overlay 已挂上、root 完成布局后）调用一次。设计稿把这一整块都锚在底边上
     * （时间距底 58、底栏距底 28、细线距底 8），屏幕一高一矮都跟着走；若留在 init 里按
     * displayMetrics 摆，手机上的全屏高度比可视区大，这些元素会整体下沉到屏幕外。
     */
    private fun placeBottomAnchored() {
        if (round) return // 圆屏：时间行在中心键下方、进度是弧形，不参与。
        val height = heightDp()
        val width = widthDp()
        val rowTop = spec.timeTop(height)
        val rowInset = spec.timeLeft
        (elapsed.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.width = dp(spec.timeWidth)
            it.height = dp(spec.timeHeight)
            it.gravity = Gravity.START
            it.leftMargin = dp(rowInset)
            it.rightMargin = 0
            it.topMargin = dp(rowTop)
            elapsed.layoutParams = it
        }
        (duration.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.width = dp(spec.timeWidth)
            it.height = dp(spec.timeHeight)
            it.gravity = Gravity.END
            it.rightMargin = dp(rowInset)
            it.leftMargin = 0
            it.topMargin = dp(rowTop)
            duration.layoutParams = it
        }
        (seekbar.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.leftMargin = dp(spec.trackInset)
            it.rightMargin = dp(spec.trackInset)
            it.topMargin = dp(spec.trackTop(height))
            seekbar.layoutParams = it
        }
        val idleWidth = spec.idleWidth(width)
        val idleTop = spec.idleTop(height)
        for (view in arrayOf(idleTrack, idleFill)) {
            (view.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.width = dp(idleWidth)
                it.leftMargin = dp(spec.idleInset)
                it.topMargin = dp(idleTop)
                view.layoutParams = it
            }
        }
    }

    /**
     * 把一个按钮中心摆到 dp 坐标上。热区尺寸按形态放大（手表 36、手机 50、长条屏 29），
     * 归位时减的是热区的一半，所以放大后中心仍然对得上。
     */
    private fun placeAt(view: View, point: PlayerNewUiGeometry.Point) {
        val p = if (round) PlayerNewUiGeometry.scalePoint(point, widthDp()) else point
        val size: Float = if (round) 36f * roundScale() else spec.buttonSize
        val half = dp(size / 2f)
        val lp = FrameLayout.LayoutParams(dp(size), dp(size))
        lp.leftMargin = (p.x * density).toInt() - half
        lp.topMargin = (p.y * density).toInt() - half
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

    /**
     * 这个槽位的按钮在**这条视频上**是否可用。
     *
     * 判据只看 [primaryOf] 拿不拿得到本体（拿不到＝这条视频根本没这个按钮，例如本地视频的清晰度），
     * 以及 Activity 给出的业务判定 [slotAvailable]。**绝不能读 `view.visibility`**：
     * 控制层自动收起时 `hidecon` 会把 lock_btn / text_speed 置 GONE，那是「暂时不显示」，
     * 不是「这条视频用不上」；拿它当判据会让按钮在一次重摆后永久消失（圆屏上就是整列空掉）。
     */
    private fun slotIsPresent(key: String): Boolean {
        if (primaryOf(key) == null) return false
        return slotAvailable(key)
    }

    private fun idleEnabled() = SharedPreferencesUtil.getBoolean(SettingsKeys.PLAYER_UI_NEW_IDLE_PROGRESS, true)

    /**
     * 屏幕短边（dp）——圆屏与方屏都取真实值。
     *
     * **圆屏这里曾经直接返回参考值 233**，于是 `scalePoint` 的缩放系数恒为 1，
     * 233 和 466 的表盘上按钮落点一模一样：466 上所有按钮挤在圆心附近、外圈全是空的。
     * 设计稿的极坐标只是「233 参考系下的比例」，必须乘上真实尺寸才是本机坐标。
     */
    private fun widthDp() = (root.width.takeIf { it > 0 }
        ?: activity.resources.displayMetrics.widthPixels) / density

    /**
     * 屏幕高度（dp）。**优先用 root 的实测高度**：displayMetrics 给的是含状态栏/导航栏的
     * 全屏高度，比播放页的可视区大，拿它去算「距底 N」会让底栏整体沉到屏幕外。
     * 还没测量时退回 displayMetrics（此时摆的位置只是临时的，[overlay] 的 post 会重摆一次）。
     */
    private fun heightDp() = (root.height.takeIf { it > 0 }
        ?: activity.resources.displayMetrics.heightPixels) / density

    /**
     * 圆屏相对设计稿 233dp 参考系的缩放比（方屏恒为 1，形态规格自己管尺寸）。
     * 按钮热区、图标、中心键、字号都乘它，才不会在 466 的表盘上还是 233 的大小。
     */
    private fun roundScale() =
        if (round) (widthDp() / PlayerNewUiGeometry.REFERENCE_SIZE_DP).coerceAtLeast(0.5f) else 1f

    /** 播放键热区：圆屏 52×缩放；方屏按形态（手表 56、手机 78、长条屏并入底栏后与普通按钮同大）。 */
    private fun playSizeDp() = (if (round) 52f * roundScale() else spec.playSize).toInt()
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

        /** 锁定提示淡出动画时长：设计稿只说「1.5 秒后淡出」，淡出本身给 240ms，不拖泥带水。 */
        private const val LOCK_HINT_FADE_MS = 240L

        /** 倍速按钮的文案形状：「x 1.0」，见 Activity 里的 speed_strs。 */
        internal val SPEED_LABEL = Regex("^x\\s*(\\d+(?:\\.\\d+)?)$")

        /** 设计稿 qualityPanel 圆屏四行的中线（dp）：82.5 / 116.5 / 150.5 / 182.5。 */
        private val ROUND_QUALITY_Y = floatArrayOf(82.5f, 116.5f, 150.5f, 182.5f)
    }
}
