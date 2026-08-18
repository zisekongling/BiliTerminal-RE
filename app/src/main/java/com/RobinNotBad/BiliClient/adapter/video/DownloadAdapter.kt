package com.RobinNotBad.BiliClient.adapter.video

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.model.DownloadSection
import com.RobinNotBad.BiliClient.service.DownloadService
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import java.util.Locale

class DownloadAdapter(
    var context: Context,
    var downloadList: ArrayList<DownloadSection>
) : RecyclerView.Adapter<DownloadAdapter.DownloadHolder>() {

    var longClickListener: OnItemLongClickListener? = null
    var clickListener: OnItemClickListener? = null
    var lastSpeedStr: String? = null
    var lastSpeedMode: Boolean = false

    fun setOnLongClickListener(listener: OnItemLongClickListener) {
        this.longClickListener = listener
    }

    fun setOnClickListener(listener: OnItemClickListener) {
        this.clickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadHolder {
        val view = LayoutInflater.from(this.context).inflate(R.layout.cell_video_local, parent, false)
        return DownloadHolder(view)
    }

    override fun onBindViewHolder(holder: DownloadHolder, position: Int) {
        if (downloadList.isEmpty()) {
            holder.show(null, context)
            holder.showProgress(null, -1f, false)
            holder.itemView.setOnClickListener(null)
            holder.itemView.setOnLongClickListener(null)
            return
        }

        val section = downloadList[position]
        holder.show(section, context)

        // 通过进度映射表判断是否在下载中（比 DB 状态更实时）
        val progressInfo = DownloadService.getDownloadProgress(section.id)
        val isDownloading = progressInfo != null

        // 显示每项速度（所有下载中的项目都显示全局速度）
        if (isDownloading && lastSpeedStr != null && lastSpeedStr!!.isNotEmpty()) {
            holder.speed.visibility = View.VISIBLE
            if (lastSpeedMode) {
                holder.speed.text = "高速 " + lastSpeedStr
            } else {
                holder.speed.text = lastSpeedStr
            }
        } else {
            holder.speed.visibility = View.GONE
        }

        // 显示每项进度条
        if (isDownloading) {
            holder.showProgress(progressInfo!!.state, progressInfo.progress, true)
        } else {
            holder.showProgress(null, -1f, false)
        }

        holder.itemView.setOnClickListener {
            clickListener?.onItemClick(position)
        }

        holder.itemView.setOnLongClickListener {
            if (longClickListener != null) {
                longClickListener!!.onItemLongClick(position)
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int {
        return if (downloadList.isNotEmpty()) downloadList.size else 1
    }

    class DownloadHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.text_title)
        val extra: TextView = itemView.findViewById(R.id.text_extra)
        val cover: ImageView = itemView.findViewById(R.id.img_cover)
        val progress: View = itemView.findViewById(R.id.progress)
        val speed: TextView = itemView.findViewById(R.id.text_speed)

        fun show(section: DownloadSection?, context: Context) {
            if (section == null) {
                title.text = "没有下载中的项"
                extra.text = "点击下面继续下载喵？"
                cover.setImageResource(R.mipmap.placeholder)
                speed.visibility = View.GONE
                return
            }
            title.text = section.name_short

            // 优先用进度映射表的状态，其次用 DB 状态
            val progressInfo = DownloadService.getDownloadProgress(section.id)
            val displayState = progressInfo?.state ?: section.state

            when (displayState) {
                "error" -> extra.text = "下载出错"
                "none" -> extra.text = "等待下载"
                "paused" -> extra.text = "已暂停（点击恢复）"
                "downloading" -> if (DownloadService.started)
                    extra.text = "下载中（点击暂停）"
                else
                    extra.text = "下载中断"
                else -> extra.text = if (progressInfo != null) displayState else "等待中"
            }

            if (section.url_cover.isNotEmpty())
                Glide.with(BiliTerminal.context).asDrawable().load(section.url_cover)
                    .transition(GlideUtil.getTransitionOptions())
                    .placeholder(R.mipmap.placeholder)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .apply(
                        RequestOptions()
                            .transform(CenterCrop(), RoundedCorners(ToolsUtil.dp2px(5f)))
                            .override(320, 180)
                    )
                    .into(cover)
        }

        @SuppressLint("SetTextI18n")
        fun showProgress(state: String?, percent: Float, isDownloading: Boolean) {
            if (!isDownloading || state == null || percent == -1f) {
                progress.visibility = View.GONE
                // 不隐藏 extra，让 show() 控制文本
                return
            }
            progress.visibility = View.VISIBLE
            extra.visibility = View.VISIBLE
            extra.text = state + "：" + String.format(Locale.CHINA, "%.1f", percent * 100) + "%"
            val width = itemView.measuredWidth
            if (width > 0) {
                val layoutParams = progress.layoutParams
                layoutParams.width = (width * percent).toInt()
                progress.layoutParams = layoutParams
            } else {
                progress.post {
                    val measuredWidth = itemView.measuredWidth
                    if (measuredWidth > 0) {
                        val layoutParams = progress.layoutParams
                        layoutParams.width = (measuredWidth * percent).toInt()
                        progress.layoutParams = layoutParams
                    }
                }
            }
        }
    }
}