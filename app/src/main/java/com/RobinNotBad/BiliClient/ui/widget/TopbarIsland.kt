package com.RobinNotBad.BiliClient.ui.widget

import android.app.Activity
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.IslandSnapshot
import com.RobinNotBad.BiliClient.util.IslandState
import com.RobinNotBad.BiliClient.util.IslandStatus
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TimeUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import java.util.WeakHashMap

/**
 * 顶栏灵动岛（26.11.07；26.11.08 与「圆屏适配」解耦成独立开关）。
 *
 * ## 只接管 46 个活动页的顶栏时钟位
 * 方屏与圆屏都能单独打开（`SettingsKeys.TOPBAR_ISLAND`）；播放器页与短视频页有自己的顶栏，不接。
 * 圆屏适配把时钟居中时胶囊满宽、标题下移 3dp；方屏时钟靠右时只按标题剩余空档加宽。
 *
 * ## 不新增 View、不改 49 份手抄布局
 * 直接装饰 `R.id.top` 里的 `@id/timeText`（`TextClock`）：
 * - 宽度上限 72dp（按屏幕宽度封顶），高度保持文字行高，不改变顶栏高度；
 * - **只有时钟居中（圆屏适配）时**标题才下移 3dp，避免实心胶囊压住标题顶；
 * - 不装任何触摸监听器：点胶囊仍会冒泡到整条顶栏，单击/长按/双击三套手势全部保留。
 *
 * ## 线程与生命周期
 * 每个 Activity 一份 [Binding]，`onStart` 里 attach、`onStop` 里 detach；
 * 定时器 1 秒一次，页面不可见时立即停，不留后台唤醒。
 *
 * 文案和颜色只在这里映射；状态机和轮换策略在 [IslandPolicy]，状态读取在 [IslandStatus]。
 */
object TopbarIsland {

    private const val TAG = "TopbarIsland"

    /** 胶囊宽度上限（dp）。圆屏顶边可见弦宽约 76dp，72dp 留 4dp 余量。 */
    private const val WIDTH_DP = 72f

    /** 按屏幕宽度封顶：233dp 屏的 31% ≈ 72dp，小屏表盘也不会被圆盘切角。 */
    private const val WIDTH_SCREEN_RATIO = 0.31f

    /** 圆屏下标题避让量。 */
    private const val TITLE_GAP_DP = 3f

    /** 加宽至少要抢到这么多（dp），否则不如保持原时钟宽度——只多几个像素会让标题莫名缩字。 */
    private const val MIN_GROW_DP = 8f

    private const val TICK_MS = 1_000L

    private val bindings = WeakHashMap<Activity, Binding>()

    /** 在 `BaseActivity.onStart` 的 `setRound()` 之后调用。 */
    fun attach(activity: BaseActivity) {
        if (!isEnabled()) {
            detach(activity)
            return
        }
        val top = activity.findViewById<View>(R.id.top)
        if (top == null) {
            detach(activity)
            return
        }
        // 只认顶栏里的 timeText：视频详情页的发布时间、文章末尾时间戳也叫 @id/timeText，
        // 全局 findViewById 会给它们套上胶囊。
        val clock = top.findViewById<TextClock>(R.id.timeText)
        if (clock == null) {
            detach(activity)
            return
        }
        val existing = bindings[activity]
        if (existing != null && existing.clock === clock) {
            existing.resume()
            return
        }
        existing?.detach()
        val binding = Binding(activity, top, clock)
        bindings[activity] = binding
        binding.attach()
    }

    /** 在 `onStop` / `onDestroy` 里调用；页面不可见后必须停掉定时器。 */
    fun detach(activity: Activity) {
        bindings.remove(activity)?.detach()
    }

    /**
     * 灵动岛自己的开关口径（26.11.08 与「圆屏适配」解耦）。
     *
     * 自己的键设过就完全听用户的；没设过才继承旧行为（跟随圆屏适配），并把继承结果落盘一次——
     * 之后关掉圆屏适配不会连带关掉灵动岛。判据本身在 `IslandPolicy.resolveEnabled`。
     */
    private fun isEnabled(): Boolean {
        // 总闸关着时一律不挂载，并把残留的 true 落回 false，避免存档状态与界面不一致。
        if (!IslandPolicy.FEATURE_ENABLED) {
            SharedPreferencesUtil.putBoolean(SettingsKeys.TOPBAR_ISLAND, false)
            return false
        }
        val prefs = SharedPreferencesUtil.getSharedPreferences()
        val explicit = if (prefs.contains(SettingsKeys.TOPBAR_ISLAND)) {
            prefs.getBoolean(SettingsKeys.TOPBAR_ISLAND, false)
        } else {
            null
        }
        val enabled = IslandPolicy.resolveEnabled(
            explicit,
            prefs.getBoolean(SettingsKeys.UI_ROUND, false)
        )
        // 继承结果无论真假都落盘一次：只落 true 的话，圆屏关着的新用户这里永远没有显式值，
        // 以后自己打开圆屏适配时会被「继承」逻辑顺手打开灵动岛，与「两个开关互不影响」矛盾。
        if (explicit == null) {
            SharedPreferencesUtil.putBoolean(SettingsKeys.TOPBAR_ISLAND, enabled)
        }
        return enabled
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    private class Binding(
        private val activity: BaseActivity,
        private val top: View,
        val clock: TextClock
    ) {
        private val handler = Handler(Looper.getMainLooper())

        private var running = false
        private var lastState: IslandState? = null
        private var lastDotColor: Int? = null
        private var showTime = false
        private var phaseStart = SystemClock.uptimeMillis()
        private var titleNudged = false
        /** 实测后的胶囊宽度（px，null = 不接管）；首帧布局后由 [settleWidth] 定一次。 */
        private var widthOverride: Int? = null
        private var widthSettled = false

        private var originalsSaved = false
        private var origBackground: Drawable? = null
        private var origWidth = ViewGroup.LayoutParams.WRAP_CONTENT
        private var origHeight = ViewGroup.LayoutParams.WRAP_CONTENT
        private var origGravity = Gravity.START or Gravity.TOP
        private var origTextColor = 0
        private var origPadL = 0
        private var origPadT = 0
        private var origPadR = 0
        private var origPadB = 0
        private var origCompoundPadding = 0
        private var origAlpha = 1f
        private var origTitleTopMargin: Int? = null

        private val tick = object : Runnable {
            override fun run() {
                if (!running) return
                if (activity.isFinishing || activity.isDestroyed) {
                    running = false
                    return
                }
                try {
                    settleWidth()
                    render()
                } catch (e: Throwable) {
                    Logu.e(TAG, "灵动岛渲染失败：${e.message}")
                }
                handler.postDelayed(this, TICK_MS)
            }
        }

        /** 首帧布局一完成就量一次宽度，别等 1 秒后那次 tick（否则会先窄一下再变宽）。 */
        private val settleListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (running) settleWidth()
            }

        fun attach() {
            saveOriginals()
            running = true
            titleNudged = false
            widthSettled = false
            widthOverride = null
            lastState = null
            lastDotColor = null
            applyStyle()
            nudgeTitle()
            clock.removeOnLayoutChangeListener(settleListener)
            clock.addOnLayoutChangeListener(settleListener)
            handler.removeCallbacks(tick)
            handler.post(tick)
            Logu.d(
                TAG,
                "灵动岛已挂载：${activity.javaClass.simpleName} " +
                    "width=${widthOverride ?: "待实测"}px（首帧布局后按标题空档定一次）"
            )
        }

        fun resume() {
            if (!originalsSaved) saveOriginals()
            running = true
            titleNudged = false
            widthSettled = false
            widthOverride = null
            lastState = null
            lastDotColor = null
            applyStyle()
            nudgeTitle()
            clock.removeOnLayoutChangeListener(settleListener)
            clock.addOnLayoutChangeListener(settleListener)
            handler.removeCallbacks(tick)
            handler.post(tick)
        }

        fun detach() {
            running = false
            handler.removeCallbacks(tick)
            clock.removeOnLayoutChangeListener(settleListener)
            clock.animate().cancel()
            restoreClock()
            restoreTitle()
            lastState = null
            lastDotColor = null
        }

        private fun saveOriginals() {
            if (originalsSaved) return
            originalsSaved = true
            origBackground = clock.background
            val lp = clock.layoutParams
            origWidth = lp?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT
            origHeight = lp?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
            origGravity = clock.gravity
            origTextColor = clock.currentTextColor
            origPadL = clock.paddingLeft
            origPadT = clock.paddingTop
            origPadR = clock.paddingRight
            origPadB = clock.paddingBottom
            origCompoundPadding = clock.compoundDrawablePadding
            origAlpha = clock.alpha
            val title = top.findViewById<TextView>(R.id.pageName)
            val titleParams = title?.layoutParams as? RelativeLayout.LayoutParams
            if (titleParams != null) origTitleTopMargin = titleParams.topMargin
        }

        /**
         * 胶囊宽度（px）；返回 null 表示**不接管宽度**，直接用时钟原来的宽度。
         *
         * 首帧布局之后用 [settleWidth] 实测一次标题空档，结果缓存进 [widthOverride]，
         * 之后的 `applyStyle()` 都用缓存值——否则加宽后标题已经被挤窄，再量一次会越量越小。
         */
        private fun islandWidth(): Int? = widthOverride ?: computeWidth()

        /**
         * 按「标题还剩多少没用上的空档」决定加宽多少（px；null = 不加宽，用回时钟原宽度）。
         *
         * 顶栏是「标题 match_parent + 时钟贴右（圆屏适配下贴中）」，**时钟一加宽就压标题**：
         * - 时钟**居中**（圆屏适配把时钟挪到正中）：加宽是左右对称的，标题挤压是老口径，沿用满宽；
         * - 时钟**靠右**（方屏 / 手机）：只往左长，抢到多少空档就长多少，抢不到 8dp 就不接管，
         *   免得标题莫名缩字。
         */
        private fun computeWidth(): Int? {
            // 基准取时钟实测宽度：wrap_content 是 -2，拿布局参数当基准会低估方屏还能长多少。
            // attach / resume 时 applyStyle 会先把宽度还原成 origWidth，布局后这里读到的才是
            // 自然宽度；未布局（width <= 0）一律不接管宽度，applyStyle 继续用 origWidth。
            val base = clock.width
            if (base <= 0) return null
            val wanted = ToolsUtil.dp2px(WIDTH_DP)
            val byScreen = (activity.window_width * WIDTH_SCREEN_RATIO).toInt()
            val desired = if (byScreen > 0) minOf(wanted, byScreen) else wanted
            if (desired <= base) return null
            if (isClockCentered()) return desired
            val extra = desired - base
            val usable = minOf(extra, titleSlack())
            return if (usable >= ToolsUtil.dp2px(MIN_GROW_DP)) base + usable else null
        }

        /** 时钟是否被「圆屏适配」挪到了顶栏正中（这个形态下加宽是左右对称的）。 */
        private fun isClockCentered(): Boolean {
            if (clock.width <= 0 || top.width <= 0) return false
            val centerGap = clock.left + clock.width / 2 - top.width / 2
            return kotlin.math.abs(centerGap) <= ToolsUtil.dp2px(4f)
        }

        /**
         * 标题框里还没用上的宽度（px），没法量（布局未算过）时返回 0。
         *
         * 用标题**文字实际需要的宽度**反推：宁可算宽也不冒把标题挤花的风险（算宽 = 少加宽）。
         */
        private fun titleSlack(): Int {
            val title = top.findViewById<TextView>(R.id.pageName) ?: return 0
            if (title.width <= 0) return 0
            val textWidth = title.paint?.measureText(title.text?.toString().orEmpty()) ?: 0f
            val needed = textWidth.toInt() +
                title.paddingLeft + title.paddingRight +
                (title.compoundDrawables.firstOrNull()?.intrinsicWidth ?: 0) +
                title.compoundDrawablePadding
            return title.width - needed
        }

        /** 首帧布局之后才知道标题还剩多少空档，按实测定一次宽度（只定一次）。 */
        private fun settleWidth() {
            if (widthSettled) return
            val title = top.findViewById<TextView>(R.id.pageName) ?: return
            if (clock.width <= 0 || title.width <= 0) return
            widthSettled = true
            clock.removeOnLayoutChangeListener(settleListener)
            widthOverride = computeWidth()
            applyStyle()
        }

        private fun pill(): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ToolsUtil.dp2px(999f).toFloat()
            setColor(withAlpha(ColorScheme.CARD, 0xEA))
            setStroke(
                maxOf(1, ToolsUtil.dp2px(1f)),
                withAlpha(ColorScheme.BORDER, 0xCC)
            )
        }

        private fun applyStyle() {
            clock.externallyManaged = true
            clock.stopTick()
            val lp = clock.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.width = islandWidth() ?: origWidth
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            clock.layoutParams = lp
            clock.gravity = Gravity.CENTER
            clock.setTextColor(ColorScheme.TEXT_PRIMARY)
            clock.background = pill()
            clock.setPadding(0, 0, 0, 0)
            clock.compoundDrawablePadding = ToolsUtil.dp2px(4f)
            clock.alpha = 1f
        }

        private fun nudgeTitle() {
            if (titleNudged) return
            // 只有圆屏适配把时钟居中、胶囊与标题上下叠在一起时才需要避让；
            // 方屏时钟靠右，标题与胶囊在同一行不重叠，别平白把顶栏抬高 3dp。
            if (!SharedPreferencesUtil.getBoolean(SettingsKeys.UI_ROUND, false)) return
            val title = top.findViewById<TextView>(R.id.pageName) ?: return
            val params = title.layoutParams as? RelativeLayout.LayoutParams ?: return
            if (origTitleTopMargin == null) origTitleTopMargin = params.topMargin
            // setRound() 每次 onStart 都会把 topMargin 重算回基准值，所以这里以"当前值"为基准，
            // 只加一次；titleNudged 防止同一次 onStart 里重复加。
            params.topMargin = params.topMargin + ToolsUtil.dp2px(TITLE_GAP_DP)
            title.layoutParams = params
            titleNudged = true
        }

        private fun restoreTitle() {
            val title = top.findViewById<TextView>(R.id.pageName) ?: return
            val params = title.layoutParams as? RelativeLayout.LayoutParams ?: return
            val base = origTitleTopMargin ?: return
            params.topMargin = base
            title.layoutParams = params
            titleNudged = false
        }

        private fun restoreClock() {
            clock.background = origBackground
            val lp = clock.layoutParams ?: ViewGroup.LayoutParams(origWidth, origHeight)
            lp.width = origWidth
            lp.height = origHeight
            clock.layoutParams = lp
            clock.gravity = origGravity
            clock.setTextColor(origTextColor)
            clock.setPadding(origPadL, origPadT, origPadR, origPadB)
            clock.compoundDrawablePadding = origCompoundPadding
            clock.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            clock.alpha = origAlpha
            clock.externallyManaged = false
            // 页面正在销毁时不能在 detached view 上重新起 ticker：onDetachedFromWindow 已经跑过，
            // 再 startTick 会把一个每秒/每分钟的 Runnable 永久留在 Handler 里。
            if (clock.isAttachedToWindow) clock.startTick() else clock.stopTick()
        }

        private fun render() {
            val now = SystemClock.uptimeMillis()
            val snapshot = IslandStatus.read(activity)
            val decision = IslandPolicy.decide(
                IslandInput(
                    nowMs = now,
                    playing = snapshot.playing,
                    downloading = snapshot.downloading,
                    unread = snapshot.unread,
                    burst = snapshot.burst,
                    showTime = showTime,
                    phaseStartMs = phaseStart
                )
            )
            showTime = decision.showTime
            phaseStart = decision.phaseStartMs
            applyState(decision.state, snapshot)
        }

        private fun applyState(state: IslandState, snapshot: IslandSnapshot) {
            val text = textFor(state, snapshot)
            val dot = dotFor(state)
            if (clock.text?.toString() != text.toString()) clock.text = text
            // 每秒 new 一个 DotDrawable 会白吃 GC、还会让 TextView 每秒重绘一次；只在换色时设。
            if (dot != lastDotColor) {
                clock.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    dot?.let { DotDrawable(it, ToolsUtil.dp2px(6f)) },
                    null,
                    null,
                    null
                )
                lastDotColor = dot
            }
            if (state != lastState) {
                clock.animate().cancel()
                clock.alpha = 0.25f
                clock.animate().alpha(1f).setDuration(150L).start()
                Logu.d(TAG, "灵动岛 → $state (${activity.javaClass.simpleName})")
                lastState = state
            }
        }

        private fun textFor(state: IslandState, snapshot: IslandSnapshot): CharSequence = when (state) {
            IslandState.TIME -> TimeUtil.formatTime(System.currentTimeMillis())
            IslandState.PLAYING -> activity.getString(R.string.island_playing)
            IslandState.DOWNLOADING -> if (snapshot.downloadPercent in 0..100) {
                activity.getString(R.string.island_downloading, snapshot.downloadPercent)
            } else {
                activity.getString(R.string.island_downloading_unknown)
            }
            IslandState.UNREAD -> if (snapshot.unread > 99) {
                activity.getString(R.string.island_unread_many)
            } else {
                activity.getString(R.string.island_unread, snapshot.unread)
            }
            IslandState.BATTERY -> when {
                snapshot.charging -> activity.getString(R.string.island_charging)
                snapshot.batteryPercent >= 0 ->
                    activity.getString(R.string.island_battery, snapshot.batteryPercent)
                else -> activity.getString(R.string.island_battery_unknown)
            }
            IslandState.ANNOUNCEMENT -> if (snapshot.burstIsUpdate) {
                activity.getString(R.string.island_update)
            } else {
                activity.getString(R.string.island_announcement)
            }
        }

        private fun dotFor(state: IslandState): Int? = when (state) {
            IslandState.TIME -> null
            IslandState.PLAYING -> ColorScheme.SUCCESS
            IslandState.DOWNLOADING -> ColorScheme.INFO
            IslandState.UNREAD -> ColorScheme.WARNING
            IslandState.BATTERY -> ColorScheme.ERROR
            IslandState.ANNOUNCEMENT -> ColorScheme.PRIMARY
        }
    }

    /** 状态点：6dp 实心圆，按状态换色。 */
    private class DotDrawable(
        private val fillColor: Int,
        private val sizePx: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }

        override fun getIntrinsicWidth(): Int = sizePx

        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            val radius = minOf(bounds.width(), bounds.height()) / 2f
            if (radius <= 0f) return
            canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
