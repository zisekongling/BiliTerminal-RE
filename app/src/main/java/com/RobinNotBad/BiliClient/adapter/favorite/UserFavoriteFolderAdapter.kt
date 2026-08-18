package com.RobinNotBad.BiliClient.adapter.favorite

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteVideoListActivity
import com.RobinNotBad.BiliClient.model.FavoriteFolder
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

/**
 * 他人公开收藏夹列表适配器（只读视角）
 * 与“我的收藏夹”不同：没有新建按钮、没有图文收藏夹入口，也不支持编辑/删除
 *
 * 封面说明：list-all 接口不返回封面字段，为空时展示占位图
 */
class UserFavoriteFolderAdapter(
    private val context: Context,
    private val mid: Long,
    private val folderList: ArrayList<FavoriteFolder>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_favorite_folder_list, parent, false)
        return FavoriteHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position < 0 || position >= folderList.size) return
        val folder = folderList[position] ?: return
        val favoriteHolder = holder as FavoriteHolder

        favoriteHolder.name.text = StringUtil.htmlToString(folder.name)
        // 他人收藏夹没有 maxCount，直接显示视频数
        favoriteHolder.count.text = if (folder.maxCount > 0) "${folder.videoCount}/${folder.maxCount}" else "${folder.videoCount}个视频"

        if (folder.cover.isNullOrEmpty()) {
            // list-all 接口不返回封面，用占位图
            favoriteHolder.cover.setImageResource(R.mipmap.placeholder)
        } else {
            Glide.with(BiliTerminal.context).asDrawable().load(GlideUtil.url(folder.cover))
                .transition(GlideUtil.getTransitionOptions())
                .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))))
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(favoriteHolder.cover)
        }

        favoriteHolder.itemView.setOnClickListener {
            val intent = Intent(context, FavoriteVideoListActivity::class.java)
            intent.putExtra("mediaId", folder.mediaId)
            intent.putExtra("mid", mid)
            intent.putExtra("name", folder.name)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return folderList.size
    }

    class FavoriteHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.text_title)
        val count: TextView = itemView.findViewById(R.id.text_itemcount)
        val cover: ImageView = itemView.findViewById(R.id.img_cover)
    }
}
