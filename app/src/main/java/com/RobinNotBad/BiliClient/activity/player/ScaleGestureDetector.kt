package com.RobinNotBad.BiliClient.activity.player

import android.content.Context
import android.os.Build
import android.os.Handler
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class ScaleGestureDetector {

    interface OnScaleGestureListener {
        fun onScale(detector: ScaleGestureDetector): Boolean
        fun onScaleBegin(detector: ScaleGestureDetector): Boolean
        fun onScaleEnd(detector: ScaleGestureDetector)
    }

    open class SimpleOnScaleGestureListener : OnScaleGestureListener {
        override fun onScale(detector: ScaleGestureDetector): Boolean = false
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true
        override fun onScaleEnd(detector: ScaleGestureDetector) {}
    }

    private val mContext: Context
    private val mListener: OnScaleGestureListener

    @JvmField var mFocusX: Float = 0f
    @JvmField var mFocusY: Float = 0f

    private var mQuickScaleEnabled: Boolean = false

    @JvmField var mCurrSpan: Float = 0f
    @JvmField var mPrevSpan: Float = 0f
    @JvmField var mInitialSpan: Float = 0f
    @JvmField var mCurrSpanX: Float = 0f
    @JvmField var mCurrSpanY: Float = 0f
    @JvmField var mPrevSpanX: Float = 0f
    @JvmField var mPrevSpanY: Float = 0f
    @JvmField var mCurrTime: Long = 0
    @JvmField var mPrevTime: Long = 0
    @JvmField var mInProgress: Boolean = false
    @JvmField val mSpanSlop: Int
    @JvmField val mMinSpan: Int

    private val mHandler: Handler?

    private var mAnchoredScaleStartX: Float = 0f
    private var mAnchoredScaleStartY: Float = 0f
    private var mAnchoredScaleMode = ANCHORED_SCALE_MODE_NONE

    private var mGestureDetector: GestureDetector? = null

    private var mEventBeforeOrAboveStartingGestureEvent: Boolean = false

    @JvmOverloads
    constructor(context: Context, listener: OnScaleGestureListener, handler: Handler? = null) {
        mContext = context
        mListener = listener
        val viewConfiguration = ViewConfiguration.get(context)
        mSpanSlop = viewConfiguration.scaledTouchSlop * 2
        mMinSpan = 0
        mHandler = handler

        val targetSdkVersion = context.applicationInfo.targetSdkVersion
        if (targetSdkVersion > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setQuickScaleEnabled(true)
        }
    }

    fun onTouchEvent(event: MotionEvent) {
        mCurrTime = event.eventTime

        val action = event.actionMasked

        if (mQuickScaleEnabled) {
            mGestureDetector!!.onTouchEvent(event)
        }

        val count = event.pointerCount

        val anchoredScaleCancelled = mAnchoredScaleMode == ANCHORED_SCALE_MODE_STYLUS
        val streamComplete = action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL || anchoredScaleCancelled

        if (action == MotionEvent.ACTION_DOWN || streamComplete) {
            if (mInProgress) {
                mListener.onScaleEnd(this)
                mInProgress = false
                mInitialSpan = 0f
                mAnchoredScaleMode = ANCHORED_SCALE_MODE_NONE
            } else if (inAnchoredScaleMode() && streamComplete) {
                mInitialSpan = 0f
                mAnchoredScaleMode = ANCHORED_SCALE_MODE_NONE
            }

            if (streamComplete) {
                return
            }
        }

        val configChanged = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_POINTER_DOWN

        val pointerUp = action == MotionEvent.ACTION_POINTER_UP
        val skipIndex = if (pointerUp) event.actionIndex else -1

        var sumX = 0f
        var sumY = 0f
        val div = if (pointerUp) count - 1 else count
        val focusX: Float
        val focusY: Float
        if (inAnchoredScaleMode()) {
            focusX = mAnchoredScaleStartX
            focusY = mAnchoredScaleStartY
            mEventBeforeOrAboveStartingGestureEvent = event.y < focusY
        } else {
            for (i in 0 until count) {
                if (skipIndex == i) continue
                sumX += event.getX(i)
                sumY += event.getY(i)
            }

            focusX = sumX / div
            focusY = sumY / div
        }

        var devSumX = 0f
        var devSumY = 0f
        for (i in 0 until count) {
            if (skipIndex == i) continue

            devSumX += abs(event.getX(i) - focusX)
            devSumY += abs(event.getY(i) - focusY)
        }
        val devX = devSumX / div
        val devY = devSumY / div

        val spanX = devX * 2
        val spanY = devY * 2
        val span: Float = if (inAnchoredScaleMode()) {
            spanY
        } else {
            hypot(spanX.toDouble(), spanY.toDouble()).toFloat()
        }

        val wasInProgress = mInProgress
        mFocusX = focusX
        mFocusY = focusY
        if (!inAnchoredScaleMode() && mInProgress && (span < mMinSpan || configChanged)) {
            mListener.onScaleEnd(this)
            mInProgress = false
            mInitialSpan = span
        }
        if (configChanged) {
            mPrevSpanX = spanX
            mCurrSpanX = spanX
            mPrevSpanY = spanY
            mCurrSpanY = spanY
            mInitialSpan = span
            mPrevSpan = span
            mCurrSpan = span
        }

        val minSpan = if (inAnchoredScaleMode()) mSpanSlop else mMinSpan
        if (!mInProgress && span >= minSpan &&
            (wasInProgress || abs(span - mInitialSpan) > mSpanSlop)) {
            mPrevSpanX = spanX
            mCurrSpanX = spanX
            mPrevSpanY = spanY
            mCurrSpanY = spanY
            mPrevSpan = span
            mCurrSpan = span
            mPrevTime = mCurrTime
            mInProgress = mListener.onScaleBegin(this)
        }

        if (action == MotionEvent.ACTION_MOVE) {
            mCurrSpanX = spanX
            mCurrSpanY = spanY
            mCurrSpan = span

            var updatePrev = true

            if (mInProgress) {
                updatePrev = mListener.onScale(this)
            }

            if (updatePrev) {
                mPrevSpanX = mCurrSpanX
                mPrevSpanY = mCurrSpanY
                mPrevSpan = mCurrSpan
                mPrevTime = mCurrTime
            }
        }
    }

    private fun inAnchoredScaleMode(): Boolean {
        return mAnchoredScaleMode != ANCHORED_SCALE_MODE_NONE
    }

    fun setQuickScaleEnabled(scales: Boolean) {
        mQuickScaleEnabled = scales
        if (mQuickScaleEnabled && mGestureDetector == null) {
            val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    mAnchoredScaleStartX = e.x
                    mAnchoredScaleStartY = e.y
                    mAnchoredScaleMode = ANCHORED_SCALE_MODE_DOUBLE_TAP
                    return true
                }
            }
            mGestureDetector = GestureDetector(mContext, gestureListener, mHandler)
        }
    }

    fun getScaleFactor(): Float {
        if (inAnchoredScaleMode()) {
            val scaleUp =
                (mEventBeforeOrAboveStartingGestureEvent && (mCurrSpan < mPrevSpan)) ||
                        (!mEventBeforeOrAboveStartingGestureEvent && (mCurrSpan > mPrevSpan))
            val spanDiff = (abs(1 - (mCurrSpan / mPrevSpan)) * SCALE_FACTOR)
            return if (mPrevSpan <= mSpanSlop) 1f else if (scaleUp) (1 + spanDiff) else (1 - spanDiff)
        }
        return if (mPrevSpan > 0) mCurrSpan / mPrevSpan else 1f
    }

    companion object {
        private const val SCALE_FACTOR = .5f
        private const val ANCHORED_SCALE_MODE_NONE = 0
        private const val ANCHORED_SCALE_MODE_DOUBLE_TAP = 1
        private const val ANCHORED_SCALE_MODE_STYLUS = 2
    }
}