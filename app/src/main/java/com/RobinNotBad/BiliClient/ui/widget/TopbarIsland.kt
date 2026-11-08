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
 * 顶栏灵动岛（26.11.07）。
 *
 * ## 只做「圆屏适配」打开时的 46 个活动页
 * 非圆屏顶栏保持原样（时钟右对齐）；播放器页与短视频页有自己的顶栏，不接。
 *
 * ## 不新增 View、不改 49 份手抄布局
 * 直接装饰 `R.id.top` 里的 `@id/timeText`（`TextClock`）：
 * - 宽度固定 72dp（按屏幕宽度封顶），高度保持文字行高，不改变顶栏高度；
 * - 圆屏模式下标题再下移 3dp，避免实心胶囊压住标题顶；
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

    private const val TICK_MS = 1_000L

    private val bindings = WeakHashMap<Activity, Binding>()

    /** 在 `BaseActivity.onStart` 的 `setRound()` 之后调用。 */
    fun attach(activity: BaseActivity) {
        if (!SharedPreferencesUtil.getBoolean(SettingsKeys.UI_ROUND, false)) {
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
                    render()
                } catch (e: Throwable) {
                    Logu.e(TAG, "灵动岛渲染失败：${e.message}")
                }
                handler.postDelayed(this, TICK_MS)
            }
        }

        fun attach() {
            saveOriginals()
            running = true
            titleNudged = false
            lastState = null
            lastDotColor = null
            applyStyle()
            nudgeTitle()
            handler.removeCallbacks(tick)
            handler.post(tick)
            Logu.d(TAG, "灵动岛已挂载：${activity.javaClass.simpleName} width=${islandWidth()}px")
        }

        fun resume() {
            if (!originalsSaved) saveOriginals()
            running = true
            titleNudged = false
            lastState = null
            lastDotColor = null
            applyStyle()
            nudgeTitle()
            handler.removeCallbacks(tick)
            handler.post(tick)
        }

        fun detach() {
            running = false
            handler.removeCallbacks(tick)
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

        private fun islandWidth(): Int {
            val wanted = ToolsUtil.dp2px(WIDTH_DP)
            val byScreen = (activity.window_width * WIDTH_SCREEN_RATIO).toInt()
            val width = if (byScreen > 0) minOf(wanted, byScreen) else wanted
            return width.coerceAtLeast(ToolsUtil.dp2px(48f))
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
                islandWidth(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.width = islandWidth()
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
