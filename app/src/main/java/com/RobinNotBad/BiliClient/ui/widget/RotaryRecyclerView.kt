package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.ViewConfigurationCompat
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import kotlin.math.roundToInt

class RotaryRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var scrollMultiple = 0f
    private var rotaryEnabled = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        initRotaryScroll()
    }

    private fun initRotaryScroll() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            rotaryEnabled = SharedPreferencesUtil.getBoolean("ui_rotatory_enable", false)
            scrollMultiple = SharedPreferencesUtil.getFloat("ui_rotatory_recycler", 0f)

            if (rotaryEnabled && scrollMultiple > 0) {
                setOnGenericMotionListener { _: android.view.View, ev: MotionEvent ->
                    if (ev.action == MotionEvent.ACTION_SCROLL &&
                        ev.source == InputDevice.SOURCE_ROTARY_ENCODER
                    ) {
                        val delta = -ev.getAxisValue(MotionEvent.AXIS_SCROLL) *
                                ViewConfigurationCompat.getScaledVerticalScrollFactor(
                                    ViewConfiguration.get(context), context
                                ) * 2
                        smoothScrollBy(0, (delta * scrollMultiple).roundToInt())
                        requestFocus()
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }
}