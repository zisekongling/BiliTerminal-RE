package com.RobinNotBad.BiliClient.adapter.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class VideoCardHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
    lateinit var title: TextView
    lateinit var upName: TextView
    lateinit var viewCount: TextView
    lateinit var cover: ImageView
    private var lastCoverUrl: String? = null
    private var boundVideoCard: VideoCard? = null
    private var boundContext: Context? = null

    init {
        title = itemView.findViewById(R.id.text_title)
        upName = itemView.findViewById(R.id.text_upname)
        viewCount = itemView.findViewById(R.id.text_viewcount)
        cover = itemView.findViewById(R.id.img_cover)
    }

    private var longPressRunnable: Runnable? = null
    private var customClickCallback: (() -> Unit)? = null

    fun bindClick(videoCard: VideoCard, context: Context, position: Int, longClickListener: View.OnLongClickListener?) {
        this.boundVideoCard = videoCard
        this.boundContext = context

        // 先清除旧的触摸检测
        longPressRunnable?.let { itemView.removeCallbacks(it) }
        customClickCallback = null

        // 设置点击事件
        itemView.setOnClickListener {
            if (customClickCallback != null) {
                customClickCallback!!.invoke()
            } else if (boundVideoCard != null && boundContext != null) {
                when (boundVideoCard!!.type) {
                    "video" -> TerminalContext.getInstance().enterVideoDetailPage(boundContext!!, boundVideoCard!!.aid, boundVideoCard!!.bvid, "video")
                    "media_bangumi" -> TerminalContext.getInstance().enterVideoDetailPage(boundContext!!, boundVideoCard!!.aid, null, "media")
                }
            }
        }

        if (longClickListener != null) {
            itemView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        longPressRunnable = Runnable { longClickListener.onLongClick(v) }
                        v.postDelayed(longPressRunnable, 200)
                        false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { v.removeCallbacks(it) }
                        longPressRunnable = null
                        false
                    }
                    else -> false
                }
            }
        } else {
            itemView.setOnTouchListener(null)
        }
    }

    /**
     * 设置自定义点击回调，覆盖默认的视频详情页跳转
     */
    fun setCustomClickCallback(callback: () -> Unit) {
        this.customClickCallback = callback
    }

    @SuppressLint("SetTextI18n")
    fun showVideoCard(videoCard: VideoCard, context: Context) {
        val strUpName = videoCard.upName
        if (strUpName == null || strUpName.isEmpty()) {
            upName.visibility = View.GONE
        } else
            upName.text = strUpName

        val strViewCount = videoCard.view
        if (strViewCount == null || strViewCount.isEmpty()) {
            viewCount.visibility = View.GONE
        } else {
            viewCount.text = strViewCount
        }

        try {
            val coverUrl = GlideUtil.url(videoCard.cover)
            if (coverUrl != lastCoverUrl) {
                lastCoverUrl = coverUrl
                requestManager.asDrawable().load(coverUrl)
                    .transition(GlideUtil.getTransitionOptions())
                    .placeholder(R.mipmap.placeholder)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .apply(getRequestOptions())
                    .into(cover)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        when (videoCard.type) {
            "live" -> {
                val sstrLive = SpannableString("[直播]" + StringUtil.htmlToString(videoCard.title))
                sstrLive.setSpan(TITLE_COLOR_SPAN, 0, 4,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                title.text = sstrLive
            }
            "series" -> {
                val sstrSeries = SpannableString("[系列]" + StringUtil.htmlToString(videoCard.title))
                sstrSeries.setSpan(TITLE_COLOR_SPAN, 0, 4,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                title.text = sstrSeries
            }
            else -> title.text = StringUtil.htmlToString(videoCard.title)
        }
    }

    companion object {
        private val requestManager = Glide.with(BiliTerminal.context)
        private val TITLE_COLOR_SPAN = ForegroundColorSpan(Color.rgb(207, 75, 95))
        private var mobileOptions: RequestOptions? = null
        private var normalOptions: RequestOptions? = null

        @JvmStatic
        fun getRequestOptions(): RequestOptions {
            val isMobile = SharedPreferencesUtil.getBoolean("ui_mobile_mode", false)
            var options = if (isMobile) mobileOptions else normalOptions
            if (options == null) {
                options = buildRequestOptions(isMobile)
                if (isMobile) mobileOptions = options else normalOptions = options
            }
            return options
        }

        private fun buildRequestOptions(isMobile: Boolean): RequestOptions {
            val cornerRadius = if (isMobile) ToolsUtil.dp2px(10f) else ToolsUtil.dp2px(5f)
            return RequestOptions()
                .transform(CenterCrop(), RoundedCorners(cornerRadius))
                .sizeMultiplier(0.85f)
                .override(400, 225)
        }
    }
}