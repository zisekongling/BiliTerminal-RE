package com.RobinNotBad.BiliClient.activity.player

import android.view.View

/**
 * 双指捏合缩放。
 *
 * [onScaleChanged] 在每次缩放比例真的变了之后回调一次：缩放会改变画面可移动的边界
 * （见 [PlayerZoomPolicy.offsetLimit]），宿主必须跟着把位移重新夹一次，否则放大后捏回原大小，
 * 画面会留在屏幕外的位置（26.11.21 反馈：锁屏也能缩、还能把画面缩到屏幕外）。
 */
class ViewScaleGestureListener(
    private val view: View,
    private val onScaleChanged: () -> Unit = {},
    /**
     * 当前是否允许缩放。默认永远允许；播放页传「是否未锁定」。
     *
     * 26.11.21 反馈：锁定后仍能缩放——先堵掉两条触摸投喂路径，再把判据下沉到这里，
     * 以后投喂路径再多一条也绕不过同一道门。
     */
    private val allowed: () -> Boolean = { true }
) : ScaleGestureDetector.SimpleOnScaleGestureListener() {

    @JvmField var scaling: Boolean = false

    // ---- 缩放诊断计数（反馈 #128 / #102）----
    // 「有时候捏合没反应」最需要的一条证据是：这三个回调到底有没有被调用、onScale 有没有因为
    // view 被 GONE（听视频模式）而直接放弃。计数只是自增，不影响任何行为；播放页每次手势开始时
    // 清零，开关（PLAYER_INTERACTION_DEBUG）关掉时没人读也没人打日志。
    @JvmField var diagBeginCount: Int = 0
    @JvmField var diagScaleCount: Int = 0
    @JvmField var diagEndCount: Int = 0
    /** onScale 因 `view.visibility == GONE` 放弃的次数。 */
    @JvmField var diagIgnoredByGoneCount: Int = 0

    /** 播放页在 ACTION_DOWN 时调用，让计数只反映这一次手势。 */
    fun resetDiagCounters() {
        diagBeginCount = 0
        diagScaleCount = 0
        diagEndCount = 0
        diagIgnoredByGoneCount = 0
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        diagScaleCount++
        if (view.visibility == View.GONE) {
            diagIgnoredByGoneCount++
            return false
        }
        // 锁定态：既不改比例也不回报 onScaleChanged，画面保持原样
        if (!allowed()) return false
        val scaleFactor = detector.getScaleFactor()

        val currentScale = view.scaleX
        val newScale = PlayerZoomPolicy.clampScale(currentScale * scaleFactor)
        view.scaleX = newScale
        view.scaleY = newScale
        onScaleChanged()

        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        diagBeginCount++
        // 不许缩放时连进「缩放中」都不要认，后面的 MOVE 也不会走到 onScale
        if (!allowed()) return false
        scaling = true
        return super.onScaleBegin(detector)
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        diagEndCount++
        scaling = false
        super.onScaleEnd(detector)
    }
}