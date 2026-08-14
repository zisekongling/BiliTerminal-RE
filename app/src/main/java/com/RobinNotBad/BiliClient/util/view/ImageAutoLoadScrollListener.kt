package com.RobinNotBad.BiliClient.util.view

import androidx.recyclerview.widget.RecyclerView

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.bumptech.glide.Glide

class ImageAutoLoadScrollListener : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        super.onScrollStateChanged(recyclerView, newState)
        when (newState) {
            RecyclerView.SCROLL_STATE_IDLE -> {
                try {
                    if (recyclerView.context != null)
                        Glide.with(recyclerView.context).resumeRequests()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            RecyclerView.SCROLL_STATE_DRAGGING, RecyclerView.SCROLL_STATE_SETTLING -> {
                try {
                    if (recyclerView.context != null)
                        Glide.with(recyclerView.context).pauseRequests()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun install(recyclerView: RecyclerView) {
            recyclerView.addOnScrollListener(ImageAutoLoadScrollListener())
        }

        @JvmStatic
        fun installIfEnabled(recyclerView: RecyclerView) {
            if (SharedPreferencesUtil.getBoolean("image_no_load_onscroll", true))
                recyclerView.addOnScrollListener(ImageAutoLoadScrollListener())
        }
    }
}