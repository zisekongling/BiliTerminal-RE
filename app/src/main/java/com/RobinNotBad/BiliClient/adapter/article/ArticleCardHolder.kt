package com.RobinNotBad.BiliClient.adapter.article

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.ArticleCard
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class ArticleCardHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    lateinit var title: TextView
    lateinit var upName: TextView
    lateinit var readTimes: TextView
    lateinit var cover: ImageView
    lateinit var readIcon: ImageView
    lateinit var upIcon: ImageView

    init {
        title = itemView.findViewById(R.id.listArticleTitle)
        upName = itemView.findViewById(R.id.text_upname)
        readTimes = itemView.findViewById(R.id.listReadTimes)
        cover = itemView.findViewById(R.id.img_cover)
        readIcon = itemView.findViewById(R.id.imageView3)
        upIcon = itemView.findViewById(R.id.avatarIcon)
    }

    fun showArticleCard(articleCard: ArticleCard, context: Context) {
        title.text = StringUtil.htmlToString(articleCard.title)
        val upNameStr = articleCard.upName
        if (upNameStr.isEmpty()) {
            upName.visibility = View.GONE
            upIcon.visibility = View.GONE
        } else upName.text = upNameStr

        if (articleCard.view.isEmpty()) {
            readIcon.visibility = View.GONE
            readTimes.visibility = View.GONE
        } else readTimes.text = articleCard.view

        Glide.with(BiliTerminal.context).asDrawable()
            .load(if (!TextUtils.isEmpty(articleCard.cover)) GlideUtil.url(articleCard.cover) else R.mipmap.article_placeholder)
            .placeholder(R.mipmap.placeholder)
            .transition(GlideUtil.getTransitionOptions())
            .format(DecodeFormat.PREFER_RGB_565)
            .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))))
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(cover)
    }
}