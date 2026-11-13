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
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class VideoCardAdapter(
    val context: Context,
    val videoCardList: List<VideoCard>
) : RecyclerView.Adapter<VideoCardHolder>() {

    var longClickListener: OnItemLongClickListener? = null
    /** 自定义点击监听器，如果设置了则覆盖默认的视频详情页跳转行为 */
    var onItemClickListener: ((Int, VideoCard) -> Unit)? = null

    /**
     * 「减少推荐」动作本体，由信息流页面注入（[VideoCardMenu.Action.REDUCE_RECOMMEND]）。
     *
     * 为什么不在这个适配器里实现：这个动作依赖「这条卡片在信息流里的位置」与 track 信息
     * （同一条卡片在别的地方出现时带的 track 参数并不相同），适配器只拿得到 [VideoCard]，
     * 拿不到那些上下文。目前只有 [com.RobinNotBad.BiliClient.activity.video.RecommendActivity]
     * 的首页推荐流会注入；不设置（null）时菜单里不出现这一项——排行、搜索、UP 主空间
     * 这类列表本来也没有推荐可减。
     */
    var onReduceRecommend: ((VideoCard) -> Unit)? = null

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
     * 多选态的样式靠 [VideoCardHolder.applySelection] 直接改标题文字（引导符 + 主色），
     * 勾选/取消勾选走的是 `notifyItemChanged(position)`。`RecyclerView` 默认挂
     * `DefaultItemAnimator`，它处理 `notifyItemChanged` 的方式是**另建一个 ViewHolder 做交叉淡入**
     * （`canReuseUpdatedViewHolder` 在 `supportsChangeAnimations = true` 时返回 false）。
     * 于是每次勾选都是一次「旧样式渐隐 + 新样式渐显」：手表屏小、条目又高，淡入期间能明显看到
     * 两句标题叠在一起，快速连点几下更是拖影；而弹窗里的多选是点击即变，两边观感对不上。
     *
     * `supportsChangeAnimations = false` 让 `RecyclerView` 复用原 ViewHolder、不做交叉淡入，
     * `applySelection` 写下的样式直接落到这一帧。只影响「内容变化」这一类动画，
     * 增删/移动动画不受影响。
     *
     * （26.11.11 反馈 #140 之前这里是 `itemView.alpha` 压暗，会被动画收尾改回 1f；
     * 改成 glyph + 主色之后 alpha 这条因果没了，但「交叉淡入导致样式滞后」的理由仍然成立。）
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
                // 页面显式注册的长按行为优先于「长按快捷操作」这个全局便利功能。
                // 稍后再看 / 收藏夹等页面的「长按两次删除」是原版终端就有的功能，之前被快速缓存顶掉，
                // 表现为长按只会弹出缓存清晰度选择页、删除功能消失（issue #2）。
                if (longClickListener != null) {
                    longClickListener!!.onItemLongClick(position)
                    return true
                }
                // 没有自定义长按的浏览类列表（推荐 / 排行 / 搜索等）才回落到「操作菜单」。
                // 26.11.08 起这条回落路径不再是「直接快速缓存」：长按先弹菜单，
                // 缓存只是菜单里的一项（VideoCardMenu.Action.CACHE），旧链路本身没变。
                // 私有功能产出的卡片（没有 B 站 aid/cid）由门面判断跳过；直播间卡片没有投稿 aid。
                if (!SharedPreferencesUtil.getBoolean(SettingsKeys.CACHE_QUICK_MODE, true)) return false
                if (videoCard.type == "live" || OptionalFeatures.skipQuickCache(videoCard)) return false
                return VideoCardMenu.show(context, videoCard, onReduceRecommend != null) {
                    onReduceRecommend?.invoke(videoCard)
                }
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
