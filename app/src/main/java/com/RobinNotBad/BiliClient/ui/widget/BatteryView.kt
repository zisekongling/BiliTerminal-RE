package com.RobinNotBad.BiliClient.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class BatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var mPower = 100
    private var mCharging = false

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val powerPercent = mPower / 100.0f

        val padding = (width * 0.05f).toInt()

        val batteryWidth = (width * 0.9f).toInt()
        val batteryHeight = height - padding

        val strokeWidth = (width * 0.05f).toInt()

        val headWidth = (width * 0.08f).toInt()
        val headHeight = (height * 0.4f).toInt()
        val headTop = (batteryHeight - headHeight + padding) / 2
        val headRight = batteryWidth + headWidth
        val headBottom = headTop + headHeight

        val insidePadding = (width * 0.08f).toInt()

        val fillLeft = padding + insidePadding
        val fillTop = padding + insidePadding
        val fillRight = ((batteryWidth - insidePadding) * powerPercent).toInt()
        val fillBottom = batteryHeight - insidePadding

        val paint = Paint()
        paint.color = Color.WHITE
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth.toFloat()

        val stroke = Rect(padding, padding, batteryWidth, batteryHeight)
        canvas.drawRect(stroke, paint)

        paint.style = Paint.Style.FILL
        val head = Rect(batteryWidth, headTop, headRight, headBottom)
        canvas.drawRect(head, paint)

        paint.strokeWidth = 0f
        paint.color = if (mCharging) Color.GREEN
        else if (mPower <= 20) Color.RED
        else Color.WHITE

        if (powerPercent != 0f) {
            val fill = Rect(fillLeft, fillTop, fillRight, fillBottom)
            canvas.drawRect(fill, paint)
        }
    }

    fun setPower(power: Int) {
        mPower = power
        if (mPower < 0) {
            mPower = 0
        }
        invalidate()
    }

    fun setCharging(charging: Boolean) {
        mCharging = charging
        invalidate()
    }
}