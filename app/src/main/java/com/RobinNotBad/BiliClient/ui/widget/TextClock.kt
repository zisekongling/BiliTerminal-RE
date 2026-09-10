package com.RobinNotBad.BiliClient.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.RobinNotBad.BiliClient.util.TimeUtil

@SuppressLint("AppCompatCustomView")
class TextClock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : TextView(context, attrs, defStyleAttr, defStyleRes) {

    init {
        init()
    }

    private var stopped = false

    fun init() {
        typeface = Typeface.DEFAULT_BOLD
    }

    private val ticker = object : Runnable {
        override fun run() {
            removeCallbacks(this)
            if (stopped) return

            val now = System.currentTimeMillis()
            text = TimeUtil.formatTime(now)

            val next = 60000 - now % 60000
            postDelayed(this, next)
        }
    }

    fun startTick() {
        stopped = false
        ticker.run()
    }

    fun stopTick() {
        stopped = true
        removeCallbacks(ticker)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTick()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopTick()
    }

    override fun onScreenStateChanged(screenState: Int) {
        super.onScreenStateChanged(screenState)

        if (screenState == SCREEN_STATE_ON) startTick()
        else stopTick()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)

        if (isVisible) startTick()
        else stopTick()
    }
}