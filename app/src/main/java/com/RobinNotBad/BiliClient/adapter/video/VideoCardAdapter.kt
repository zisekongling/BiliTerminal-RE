package com.RobinNotBad.BiliClient.adapter.video

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.OptionalFeatures
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class VideoCardAdapter(
    val context: Context,
    val videoCardList: List<VideoCard>
) : RecyclerView.Adapter<VideoCardHolder>() {

    var longClickListener: OnItemLongClickListener? = null
    /** 自定义点击监听器，如果设置了则覆盖默认的视频详情页跳转行为 */
    var onItemClickListener: ((Int, VideoCard) -> Unit)? = null

    /**
     * 多选模式：进入后列表项可以勾选/取消（收藏夹多选删除用）。
     * 勾选状态由页面持有，这里只按 [selectedAids] 画样式。
     */
    var selectionMode: Boolean = false

    /** 已勾选的 aid 集合（页面持有同一个引用） */
    var selectedAids: MutableSet<Long> = HashSet()

    init {
        setHasStableIds(true)
    }

    /**
     * 关掉「同一项内容变化」的动画。
     *
     * <h3>为什么必须关</h3>
     * 多选态的暗/亮是靠 [VideoCardHolder.applySelection] 直接写 `itemView.alpha` 实现的
     * （选中 1f、未选中 0.45f），而勾选/取消勾选走的是 `notifyItemChanged(position)`。
     * `RecyclerView` 默认挂 `DefaultItemAnimator`，它处理 `notifyItemChanged` 的方式是
     * **另建一个 ViewHolder 做交叉淡入**（`canReuseUpdatedViewHolder` 在
     * `supportsChangeAnimations = true` 时返回 false），并在动画收尾把新布局的 alpha 设成 1。
     * 于是：勾选（目标本来就是 1f）看不出问题，**取消勾选（目标是 0.45f）会被静默改回 1f**——
     * 表现出来正是真机上反馈的「取消选择后条目不会再次暗下去」。
     *
     * `supportsChangeAnimations = false` 让 `RecyclerView` 复用原 ViewHolder、不做交叉淡入，
     * `applySelection` 写下的 alpha 才能留到下一帧。只影响「内容变化」这一类动画，
     * 增删/移动动画不受影响。
     */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    fun setOnLongClickListener(listener: OnItemLongClickListener) {
        this.longClickListener = listener
    }

    override fun getItemId(position: Int): Long {
        val card = videoCardList[position]
        return if (card.aid != 0L) card.aid else card.bvid.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoCardHolder {
        val view = LayoutInflater.from(this.context).inflate(R.layout.cell_video_list, parent, false)
        return VideoCardHolder(view)
    }

    override fun onBindViewHolder(holder: VideoCardHolder, position: Int) {
        if (position < 0 || position >= videoCardList.size)
            return
        val videoCard = videoCardList[position]

        holder.showVideoCard(videoCard, context)
        holder.bindClick(videoCard, context, position, object : View.OnLongClickListener {
            override fun onLongClick(v: View): Boolean {
                // 页面显式注册的长按行为优先于「快速缓存」这个全局便利功能。
                // 稍后再看 / 收藏夹等页面的「长按两次删除」是原版终端就有的功能，之前被快速缓存顶掉，
                // 表现为长按只会弹出缓存清晰度选择页、删除功能消失（issue #2）。
                if (longClickListener != null) {
                    longClickListener!!.onItemLongClick(position)
                    return true
                }
                // 没有自定义长按的浏览类列表（推荐 / 排行 / 搜索等）才回落到快速缓存。
                // 私有功能产出的卡片（没有 B 站 aid/cid）由门面判断跳过。
                val quickMode = SharedPreferencesUtil.getBoolean("cache_quick_mode", true)
                if (quickMode && videoCard.type != "live" && !OptionalFeatures.skipQuickCache(videoCard)) {
                    VideoQuickCache.handle(context, videoCard)
                    return true
                }
                return false
            }
        })
        // 如果设置了自定义点击监听器，覆盖默认行为
        if (onItemClickListener != null) {
            holder.setCustomClickCallback { onItemClickListener!!.invoke(position, videoCard) }
        }

        holder.applySelection(selectionMode, selectedAids.contains(videoCard.aid))
    }

    override fun getItemCount(): Int {
        return if (videoCardList != null) videoCardList.size else 0
    }
}
