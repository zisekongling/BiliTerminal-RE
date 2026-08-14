package com.RobinNotBad.BiliClient.activity.player

import android.view.View
import kotlin.math.max
import kotlin.math.min

class ViewScaleGestureListener(private val view: View) : ScaleGestureDetector.SimpleOnScaleGestureListener() {

    @JvmField var scaling: Boolean = false
    @JvmField var can_reset: Boolean = false

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        if (view.visibility == View.GONE) {
            return false
        }
        val scaleFactor = detector.getScaleFactor()

        val currentScale = view.scaleX
        var newScale = currentScale * scaleFactor
        newScale = max(1f, min(5f, newScale))
        view.scaleX = newScale
        view.scaleY = newScale

        can_reset = newScale != 1.0f
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        scaling = true
        return super.onScaleBegin(detector)
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        scaling = false
        super.onScaleEnd(detector)
    }
}