package com.RobinNotBad.BiliClient.adapter.video

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
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
        /**
         * 多选态标题前的引导符，与 `TerminalDialog` 的 `GLYPH_ACTIVE` / `GLYPH_IDLE`、
         * `VideoCardHolder` 的多选样式同一套（反馈 #140）。1 个字符 + 1 个半角空格 = 2 个字符宽，
         * 剥前缀时直接 `subSequence(2, …)`。
         */
        private const val GLYPH_SELECTED = "▸"
        private const val GLYPH_UNSELECTED = "›"
    }

    /**
     * 多选态的样式靠 [DownloadHolder.applySelection] 直接改标题文字（引导符 + 主色），
     * 勾选切换走 `notifyItemChanged(position)`。默认交叉淡入动画会让新旧两句标题叠着淡入淡出，
     * 手表屏小、条目又高，看上去就是拖影。关掉内容变化动画，让样式当帧生效。
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
            // 复用到的 holder 可能停在多选态（标题主色已由 show(null) 复位），整卡透明度也显式复位
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

        /**
         * 标题的「常态颜色」，进多选时标题会被改成主色，退出多选要还原，所以这里先存一份。
         *
         * 必须在构造时读一次：`applySelection` 里现读 `currentTextColor` 会把上一次多选
         * 留下的主色当成常态色，一路污染下去（同 `VideoCardHolder`）。
         */
        private val normalTitleColor: Int = title.currentTextColor

        fun show(section: DownloadSection?, context: Context) {
            if (section == null) {
                title.text = UiText.of("没有下载中的项")
                // 复用到的 holder 可能停在多选态，占位行要把标题色也复位
                title.setTextColor(normalTitleColor)
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
                "error" -> {
                    // 失败要说清原因（反馈 #87/#90）：HTTP 403 / 第 2/3 段下载失败 / 拼接校验不过，
                    // 只显示一句「下载出错」用户既不知道能不能重试、也报不出有效信息。
                    // 原因由 DownloadService 落库到 download.fail_reason，这里只负责显示。
                    val reason = section.failReason
                    extra.text = if (reason.isEmpty()) UiText.of("下载出错")
                    else context.getString(R.string.text_download_fail_prefix, reason)
                }
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
         * 画多选态：与 `TerminalDialog` 单/多选列表同一套视觉——标题前一个引导符，
         * 选中是 `▸` 且标题走主色，未选中是 `›` 且标题保持常态色（反馈 #140）。
         *
         * 不再用 `itemView.alpha` 压暗整卡：那会把封面和进度一起弄灰，
         * 与缓存页 / 视频列表的多选观感对不上。
         *
         * [show] 每次绑定都会先把标题重置成原始名称，所以这里直接加引导符不会越叠越多；
         * 仍然先剥一次前缀，防止列表局部刷新时复用 holder 而没重绑。
         */
        fun applySelection(selectionMode: Boolean, selected: Boolean) {
            if (title.text?.startsWith(GLYPH_SELECTED) == true || title.text?.startsWith(GLYPH_UNSELECTED) == true) {
                title.text = title.text.subSequence(2, title.text.length)
            }
            // 兜底清掉旧样式可能留下的整卡压暗
            itemView.alpha = 1f
            if (!selectionMode) {
                title.setTextColor(normalTitleColor)
                return
            }

            val glyph = if (selected) GLYPH_SELECTED else GLYPH_UNSELECTED
            // 未选中引导符按常态色压到 70% 不透明度，等价于 TerminalDialog 的 secondaryColor()
            val glyphColor = if (selected) {
                ColorScheme.PRIMARY
            } else {
                Color.argb(179, Color.red(normalTitleColor), Color.green(normalTitleColor), Color.blue(normalTitleColor))
            }
            val builder = SpannableStringBuilder(glyph).append(' ').append(title.text ?: "")
            builder.setSpan(
                ForegroundColorSpan(glyphColor),
                0,
                glyph.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            title.text = builder
            title.setTextColor(if (selected) ColorScheme.PRIMARY else normalTitleColor)
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