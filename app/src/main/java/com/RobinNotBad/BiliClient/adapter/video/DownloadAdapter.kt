package com.RobinNotBad.BiliClient.adapter.video

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.model.DownloadSection
import com.RobinNotBad.BiliClient.service.DownloadService
import com.RobinNotBad.BiliClient.service.formatDownloadSpeed
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
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

    /** 多选模式：勾选/取消勾选由页面处理，这里只按下面的集合画样式。 */
    var selectionMode: Boolean = false

    /** 已勾选的下载任务 id，页面持有同一个集合引用。 */
    var selectedIds: MutableSet<Long> = HashSet()

    companion object {
        /** 多选前缀：勾上用 ✓，未勾用全角空格占位（与收藏夹/缓存页多选同一套视觉）。 */
        const val PREFIX_SELECTED = "✓ "
        const val PREFIX_UNSELECTED = "　 "
    }

    /**
     * 多选态的暗/亮靠 [DownloadHolder.applySelection] 直接写 `itemView.alpha`，
     * 而勾选切换走 `notifyItemChanged(position)`。默认交叉淡入动画会在收尾把 alpha 改回 1，
     * 表现为「取消勾选后条目不会重新压暗」（收藏夹/缓存页已踩过同一个坑）。
     * 关掉内容变化动画，让 applySelection 写下的 alpha 留到下一帧。
     */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

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
            // 复用到的 holder 可能是上一条被多选压暗的（alpha=0.45），占位行要显式复位
            holder.itemView.alpha = 1f
            holder.showProgress(null, -1f, false)
            holder.itemView.setOnClickListener(null)
            holder.itemView.setOnLongClickListener(null)
            return
        }

        val section = downloadList[position]
        holder.show(section, context)
        holder.applySelection(selectionMode, selectedIds.contains(section.id))

        // 通过进度映射表判断是否在下载中（比 DB 状态更实时）
        val progressInfo = DownloadService.getDownloadProgress(section.id)
        val isDownloading = progressInfo != null

        // 每项显示**自己**的速度（DownloadProgressInfo.speedBps 由该任务自己的采样器给出，
        // 采样器喂的是这条任务的累计字节），不再所有行共用一份全局速度
        val speedBps = progressInfo?.speedBps ?: 0L
        if (isDownloading && speedBps > 0) {
            holder.speed.visibility = View.VISIBLE
            holder.speed.text = formatDownloadSpeed(speedBps)
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
                title.text = UiText.of("没有下载中的项")
                extra.text = UiText.of("点击下面继续下载喵？")
                cover.setImageResource(R.mipmap.placeholder)
                speed.visibility = View.GONE
                return
            }
            title.text = section.name_short

            // 优先用进度映射表的状态，其次用 DB 状态
            val progressInfo = DownloadService.getDownloadProgress(section.id)
            val displayState = progressInfo?.state ?: section.state

            when (displayState) {
                "error" -> extra.text = UiText.of("下载出错")
                "none" -> extra.text = UiText.of("等待下载")
                "paused" -> extra.text = UiText.of("已暂停（点击恢复）")
                "downloading" -> if (DownloadService.started)
                    extra.text = UiText.of("下载中（点击暂停）")
                else
                    extra.text = UiText.of("下载中断")
                else -> extra.text = if (progressInfo != null) displayState else "等待中"
            }

            if (section.url_cover.isNotEmpty())
                GlideUtil.with(cover).asDrawable().load(section.url_cover)
                    .transition(GlideUtil.getTransitionOptions())
                    .placeholder(R.mipmap.placeholder)
                    .error(R.mipmap.placeholder)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .apply(
                        RequestOptions()
                            .transform(CenterCrop(), RoundedCorners(ToolsUtil.dp2px(5f)))
                            .override(320, 180)
                    )
                    .into(cover)
        }

        /**
         * 画多选态：勾中项正常亮度 + 标题前「✓ 」，未勾项压暗 + 全角空格占位。
         *
         * [show] 每次绑定都会先把标题重置成原始名称，所以这里直接加前缀不会越叠越多；
         * 退出多选后重新绑定也会自然去掉前缀。
         */
        fun applySelection(selectionMode: Boolean, selected: Boolean) {
            if (!selectionMode) {
                itemView.alpha = 1f
                return
            }
            itemView.alpha = if (selected) 1f else 0.45f
            title.text = TextUtils.concat(
                if (selected) DownloadAdapter.PREFIX_SELECTED else DownloadAdapter.PREFIX_UNSELECTED,
                title.text
            )
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