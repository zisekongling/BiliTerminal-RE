package com.RobinNotBad.BiliClient.activity.player

import android.view.View
import kotlin.math.max
import kotlin.math.min

class ViewScaleGestureListener(private val view: View) : ScaleGestureDetector.SimpleOnScaleGestureListener() {

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
        val scaleFactor = detector.getScaleFactor()

        val currentScale = view.scaleX
        var newScale = currentScale * scaleFactor
        newScale = max(1f, min(5f, newScale))
        view.scaleX = newScale
        view.scaleY = newScale

        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        diagBeginCount++
        scaling = true
        return super.onScaleBegin(detector)
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        diagEndCount++
        scaling = false
        super.onScaleEnd(detector)
    }
}