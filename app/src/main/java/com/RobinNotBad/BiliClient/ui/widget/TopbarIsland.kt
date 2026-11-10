package com.RobinNotBad.BiliClient.ui.widget

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
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
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
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
 *
 * ## 宽度全局固定（26.11.10 用户拍板）
 * 胶囊宽度恒为 72dp（小屏按屏宽 31% 封顶），**所有页面、所有状态一致**；
 * 不再按「标题还剩多少空档」自适应——那样同一设备上不同页面宽度会不一样（26.11.10 用户报的 bug）。
 * 方屏长标题由顶栏自己省略号截断（`BaseActivity.setRound()` 已把 `pageName` 设成单行 + END 省略）。
 *
 * ## 左侧状态图标（26.11.10 用户要求）
 * 文字前加 12dp 矢量小图标，颜色取主题状态色；「1」「15%」这类裸数字/百分比靠图标说明含义
 * （未读 / 下载进度 / 电量）。图标只做「复用现有 + 新画四个」的最小集合：
 * 播放复用 `icon_play_12`，公告/更新复用 `icon_announcement` / `icon_update`，
 * 新画下载 / 未读 / 电池 / 闪电四个。
 *
 * ## 不新增 View、不改 49 份手抄布局
 * 直接装饰 `R.id.top` 里的 `@id/timeText`（`TextClock`）：
 * - 高度保持文字行高，不改变顶栏高度；
 * - **只有时钟居中（圆屏适配）时**标题才下移 3dp，避免实心胶囊压住标题顶；
 * - 不装任何触摸监听器：点胶囊仍会冒泡到整条顶栏，单击/长按/双击三套手势全部保留。
 *
 * ## 线程与生命周期
 * 每个 Activity 一份 [Binding]，`onStart` 里 attach、`onStop` 里 detach；
 * 定时器 1 秒一次，页面不可见时立即停，不留后台唤醒。
 *
 * 文案、颜色、图标只在这里映射；状态机和轮换策略在 [IslandPolicy]，状态读取在 [IslandStatus]。
 */
object TopbarIsland {

    private const val TAG = "TopbarIsland"

    /** 胶囊宽度（dp）。圆屏顶边可见弦宽约 76dp，72dp 留 4dp 余量。 */
    private const val WIDTH_DP = 72f

    /** 状态图标边长（dp），与 12sp 文字同高。 */
    private const val ICON_DP = 12f

    /** 圆屏下标题避让量。 */
    private const val TITLE_GAP_DP = 3f

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
        /** 当前图标的「资源 id:颜色」签名；换图标 / 换色才重建，避免每秒 new Drawable。 */
        private var lastIconKey: String? = null
        /** 充电闪电的闪烁相位：每秒在亮 / 暗之间切一次（像充电宝指示灯）。 */
        private var iconBlinkOn = true
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
            lastIconKey = null
            iconBlinkOn = true
            applyStyle()
            nudgeTitle()
            handler.removeCallbacks(tick)
            handler.post(tick)
            Logu.d(
                TAG,
                "灵动岛已挂载：${activity.javaClass.simpleName} width=${pillWidthPx()}px（全局固定）"
            )
        }

        fun resume() {
            if (!originalsSaved) saveOriginals()
            running = true
            titleNudged = false
            lastState = null
            lastIconKey = null
            iconBlinkOn = true
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
            lastIconKey = null
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

        /** 胶囊宽度（px）：全局固定 72dp，小屏按屏宽 31% 封顶；与状态、页面、标题长短无关。 */
        private fun pillWidthPx(): Int =
            IslandPolicy.pillWidthPx(ToolsUtil.dp2px(WIDTH_DP), activity.window_width)

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
            lp.width = pillWidthPx()
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
            if (clock.text?.toString() != text.toString()) clock.text = text

            val icon = iconFor(state, snapshot)
            val key = icon?.let { "${it.resId}:${it.tint}" }
            val iconChanged = key != lastIconKey
            if (iconChanged) {
                clock.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    icon?.let {
                        IslandIconDrawable(activity, it.resId, it.tint, ToolsUtil.dp2px(ICON_DP))
                    },
                    null,
                    null,
                    null
                )
                lastIconKey = key
                // 新建的那一帧先亮着，下一帧再开始闪，避免刚切进充电就突然变暗。
                iconBlinkOn = true
            }
            // 充电时闪电像充电宝指示灯一样慢闪：亮 1 秒、暗 1 秒交替（只在有图标时）。
            if (state == IslandState.BATTERY && snapshot.charging) {
                if (!iconChanged) iconBlinkOn = !iconBlinkOn
                clock.compoundDrawablesRelative.firstOrNull()?.alpha = if (iconBlinkOn) 255 else 110
                clock.invalidate()
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
                // 26.11.10：电量提醒直接给百分比（"15%"），充电时由左侧闪电图标说明状态；
                // 只有读不到电量时才退回「充电」「低电」两个字。
                snapshot.batteryPercent >= 0 ->
                    activity.getString(R.string.island_battery, snapshot.batteryPercent)
                snapshot.charging -> activity.getString(R.string.island_charging)
                else -> activity.getString(R.string.island_battery_unknown)
            }
            IslandState.ANNOUNCEMENT -> if (snapshot.burstIsUpdate) {
                activity.getString(R.string.island_update)
            } else {
                activity.getString(R.string.island_announcement)
            }
        }

        /** 状态图标：资源 + 颜色。文字左前方那枚 12dp 小图标，说明「这个数字/词是什么意思」。 */
        private data class IslandIcon(@DrawableRes val resId: Int, @ColorInt val tint: Int)

        private fun iconFor(state: IslandState, snapshot: IslandSnapshot): IslandIcon? = when (state) {
            IslandState.TIME -> null
            IslandState.PLAYING -> IslandIcon(R.drawable.icon_play_12, ColorScheme.SUCCESS)
            IslandState.DOWNLOADING -> IslandIcon(R.drawable.island_icon_download, ColorScheme.INFO)
            IslandState.UNREAD -> IslandIcon(R.drawable.island_icon_unread, ColorScheme.WARNING)
            IslandState.BATTERY -> if (snapshot.charging) {
                IslandIcon(R.drawable.island_icon_charging, ColorScheme.SUCCESS)
            } else {
                IslandIcon(R.drawable.island_icon_battery, ColorScheme.ERROR)
            }
            IslandState.ANNOUNCEMENT -> if (snapshot.burstIsUpdate) {
                IslandIcon(R.drawable.icon_update, ColorScheme.PRIMARY)
            } else {
                IslandIcon(R.drawable.icon_announcement, ColorScheme.PRIMARY)
            }
        }
    }

    /**
     * 12dp 状态图标：包一层现有矢量资源，缩放到统一边长并按状态着色。
     *
     * 现有图标边长不统一（`icon_play_12` 是 12dp，`icon_announcement` / `icon_update` 是 24dp），
     * 直接塞进 compound drawable 会一个高一个矮，所以这里统一成 12dp 再画。
     * 颜色用 SRC_IN 覆盖资源自带的 tint / 写死色，不依赖 XML 里的主题色。
     */
    private class IslandIconDrawable(
        context: Context,
        @DrawableRes resId: Int,
        @ColorInt tint: Int,
        private val sizePx: Int
    ) : Drawable() {
        private val inner: Drawable =
            AppCompatResources.getDrawable(context, resId)?.mutate() ?: ColorDrawable(0x00000000)

        init {
            // 用 ColorFilter 而非已废弃的 setColorFilter(int, Mode)（API 29 起标记废弃）。
            inner.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
            inner.setBounds(0, 0, sizePx, sizePx)
        }

        override fun getIntrinsicWidth(): Int = sizePx

        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            inner.setBounds(bounds)
            inner.draw(canvas)
        }

        override fun setAlpha(alpha: Int) {
            inner.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            inner.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
