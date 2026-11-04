package com.RobinNotBad.BiliClient.adapter.subscription

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.SubscriptionFolder
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

/**
 * 「我的订阅」列表适配器。
 *
 * 只负责画卡片；点击/长按交给 Activity 决定（进详情 or 弹取消订阅菜单），
 * 与 [com.RobinNotBad.BiliClient.adapter.favorite.FavoriteFolderAdapter] 的分工一致。
 */
class SubscriptionFolderAdapter(
    private val context: Context,
    private val folderList: ArrayList<SubscriptionFolder>
) : RecyclerView.Adapter<SubscriptionFolderAdapter.SubscriptionHolder>() {

    var onItemClickListener: ((Int, SubscriptionFolder) -> Unit)? = null
    var onItemLongClickListener: ((Int, SubscriptionFolder) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_subscription_folder, parent, false)
        return SubscriptionHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: SubscriptionHolder, position: Int) {
        val item = folderList.getOrNull(position) ?: return

        holder.title.text = StringUtil.htmlToString(item.title)
        // UP 主名缺失时不要留一行空白把卡片撑高，直接隐藏
        if (item.upperName.isEmpty()) {
            holder.upper.visibility = View.GONE
        } else {
            holder.upper.visibility = View.VISIBLE
            holder.upper.text = item.upperName
        }
        holder.count.text = context.getString(
            R.string.text_subscription_meta,
            context.getString(
                if (item.isSeason) R.string.text_subscription_type_season
                else R.string.text_subscription_type_folder
            ),
            item.mediaCount
        )
        // 失效条目压暗但保留可点击：点进去给「该收藏夹已失效」提示，比直接消失更好解释
        holder.itemView.alpha = if (item.isInvalid) 0.45f else 1f

        GlideUtil.with(holder.cover).asDrawable()
            .load(GlideUtil.url_hq(item.cover))
            .transition(GlideUtil.getTransitionOptions())
            .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))))
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(holder.cover)

        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(holder.bindingAdapterPosition, item)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClickListener?.invoke(holder.bindingAdapterPosition, item)
            true
        }
    }

    override fun getItemCount(): Int = folderList.size

    class SubscriptionHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.text_title)
        val upper: TextView = itemView.findViewById(R.id.text_upper)
        val count: TextView = itemView.findViewById(R.id.text_count)
        val cover: ImageView = itemView.findViewById(R.id.img_cover)
    }
}
