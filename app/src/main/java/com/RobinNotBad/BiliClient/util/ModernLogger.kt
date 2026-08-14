package com.RobinNotBad.BiliClient.util

import android.util.Log
import com.RobinNotBad.BiliClient.BuildConfig

object ModernLogger {

    private const val TAG = "BiliTerminal"
    private var enabled = BuildConfig.DEBUG

    fun setEnabled(enable: Boolean) {
        enabled = enable
    }

    fun v(message: String) {
        if (enabled) Log.v(TAG, message)
    }

    fun v(tag: String, message: String) {
        if (enabled) Log.v(tag, message)
    }

    fun d(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun d(tag: String, message: String) {
        if (enabled) Log.d(tag, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}

inline fun <T> safeCall(tag: String = "SafeCall", block: () -> T?): T? {
    return try {
        block()
    } catch (e: Exception) {
        ModernLogger.e(tag, "Operation failed: ${e.message}", e)
        null
    }
}

inline fun <T> safeCallOrDefault(tag: String = "SafeCall", default: T, block: () -> T): T {
    return try {
        block()
    } catch (e: Exception) {
        ModernLogger.e(tag, "Operation failed: ${e.message}", e)
        default
    }
}