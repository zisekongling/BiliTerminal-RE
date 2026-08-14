package com.RobinNotBad.BiliClient.activity.base

import android.content.Context
import androidx.fragment.app.Fragment
import com.RobinNotBad.BiliClient.BiliTerminal

open class BaseFragment : Fragment() {
    fun runOnUiThread(runnable: Runnable) {
        if (isAdded) requireActivity().runOnUiThread(runnable)
    }

    fun getAppContext(): Context {
        return BiliTerminal.context
    }
}