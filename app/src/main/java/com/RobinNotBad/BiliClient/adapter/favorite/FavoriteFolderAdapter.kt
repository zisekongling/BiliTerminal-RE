package com.RobinNotBad.BiliClient.adapter.favorite

import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteVideoListActivity
import com.RobinNotBad.BiliClient.activity.user.favorite.FavouriteOpusListActivity
import com.RobinNotBad.BiliClient.model.FavoriteFolder
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class FavoriteFolderAdapter(
    private val context: Context,
    private val folderList: ArrayList<FavoriteFolder>,
    private val mid: Long
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var onLongClickListener: OnLongClickListener? = null
    private var onCreateClickListener: OnCreateClickListener? = null
    private var onAggregateClickListener: OnAggregateClickListener? = null

    companion object {
        private const val TYPE_CREATE = 0
        private const val TYPE_FOLDER = 1
        private const val TYPE_OPUS = 2

        /**
         * 跨夹「全部收藏」聚合入口（只读视图）。
         *
         * <p>服务端没有跨夹聚合接口：`x/v3/fav/resource/list?type=1`（文档里的「全部收藏夹」）
         * 在未登录 / 访问他人收藏夹时实测返回 `11010 您访问的内容不存在`，所以这个入口进的是
         * 「各夹第一页本地合并」的只读聚合页（实现见 FavoriteAggregatePolicy）。
         */
        private const val TYPE_AGGREGATE = 3
    }

    fun interface OnLongClickListener {
        fun onLongClick(position: Int)
    }

    fun interface OnCreateClickListener {
        fun onCreateClick()
    }

    fun interface OnAggregateClickListener {
        fun onAggregateClick()
    }

    fun setOnLongClickListener(listener: OnLongClickListener?) {
        this.onLongClickListener = listener
    }

    fun setOnCreateClickListener(listener: OnCreateClickListener?) {
        this.onCreateClickListener = listener
    }

    fun setOnAggregateClickListener(listener: OnAggregateClickListener?) {
        this.onAggregateClickListener = listener
    }

    override fun getItemViewType(position: Int): Int {
        if (position == 0) {
            return TYPE_CREATE
        } else if (position == 1) {
            return TYPE_AGGREGATE
        } else if (position == folderList.size + 2) {
            return TYPE_OPUS
        } else {
            return TYPE_FOLDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_CREATE || viewType == TYPE_AGGREGATE) {
            // 聚合入口与「新建收藏夹」同款行：复用现有布局与 holder，不新增 res/layout
            val view = LayoutInflater.from(this.context).inflate(R.layout.cell_create_folder_button, parent, false)
            return CreateButtonHolder(view)
        } else {
            val view = LayoutInflater.from(this.context).inflate(R.layout.cell_favorite_folder_list, parent, false)
            return FavoriteHolder(view)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is CreateButtonHolder) {
            if (position == 1) {
                // 聚合入口：只读视图，点进去是「各夹最近收藏」的合并列表
                holder.text.text = context.getString(R.string.fav_port_aggregate_entry)
                holder.itemView.setOnClickListener {
                    onAggregateClickListener?.onAggregateClick()
                }
            } else {
                holder.text.text = context.getString(R.string.text_cellcreatefolderbutton_01)
                holder.itemView.setOnClickListener {
                    if (onCreateClickListener != null) {
                        onCreateClickListener!!.onCreateClick()
                    }
                }
            }
        } else if (holder is FavoriteHolder) {
            val favoriteHolder = holder
            if (position == folderList.size + 2) {
                favoriteHolder.name.text = UiText.of("图文收藏夹")
                favoriteHolder.count.text = ""
                GlideUtil.with(favoriteHolder.cover).asDrawable()
                    // 封面底色原写死在矢量图里（老版粉 #fb8787），现在矢量图只留白色图标，底色取主题主色
                    .load(LayerDrawable(arrayOf<Drawable>(
                        ColorDrawable(ColorScheme.PRIMARY),
                        StringUtil.getDrawable(context, R.drawable.article_fav_cover)
                    )))
                    .transition(GlideUtil.getTransitionOptions())
                    .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(favoriteHolder.cover)
                favoriteHolder.itemView.setOnClickListener {
                    val intent = Intent(context, FavouriteOpusListActivity::class.java)
                    context.startActivity(intent)
                }
                favoriteHolder.itemView.setOnLongClickListener(null)
            } else if (position > 1 && position <= folderList.size + 1) {
                val folder = folderList[position - 2] ?: return

                favoriteHolder.name.text = StringUtil.htmlToString(folder.name)
                favoriteHolder.count.text = folder.videoCount.toString() + "/" + folder.maxCount
                GlideUtil.with(favoriteHolder.cover).asDrawable().load(GlideUtil.url_hq(folder.cover))
                    .transition(GlideUtil.getTransitionOptions())
                    .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))))
                    .format(DecodeFormat.PREFER_RGB_565)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(favoriteHolder.cover)
                favoriteHolder.itemView.setOnClickListener {
                    val intent = Intent()
                    intent.setClass(context, FavoriteVideoListActivity::class.java)
                    intent.putExtra("mid", mid)
                    intent.putExtra("name", folder.name)
                    // v3 写接口只认 media_id，排序/复制/移动/删除都要用它
                    intent.putExtra("mediaId", folder.mediaId)
                    intent.putExtra("readOnly", false)
                    context.startActivity(intent)
                }
                favoriteHolder.itemView.setOnLongClickListener {
                    // 默认收藏夹也进管理页：改名/删除那块在管理页里自己隐藏，
                    // 但「批量删除内容」「清空失效内容」只在管理页有入口，默认夹最需要它
                    // 列表第 0 行是「新建」、第 1 行是「全部收藏」，文件夹从第 2 行开始
                    onLongClickListener?.onLongClick(position - 2)
                    true
                }
            }
        }
    }

    override fun getItemCount(): Int {
        // 新建 + 全部收藏 + N 个收藏夹 + 图文收藏夹
        return folderList.size + 3
    }

    class FavoriteHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.text_title)
        val count: TextView = itemView.findViewById(R.id.text_itemcount)
        val cover: ImageView = itemView.findViewById(R.id.img_cover)
    }

    class CreateButtonHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.text)
    }
}