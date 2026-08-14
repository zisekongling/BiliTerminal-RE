package com.RobinNotBad.BiliClient.adapter.article

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.api.ArticleApi
import com.RobinNotBad.BiliClient.model.Opus
import com.RobinNotBad.BiliClient.model.OpusParagraph
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView
import java.util.ArrayList
import java.util.Arrays
import java.util.Locale

class OpusContentAdapter(
    private val context: Activity,
    private val article: Opus
) : RecyclerView.Adapter<OpusContentAdapter.ArticleLineHolder>() {

    private val paragraphs: Array<OpusParagraph>? = article.paragraphs
    private var coinAdd = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleLineHolder {
        val view: View
        when (viewType) {
            -1 -> view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_head, parent, false)
            -2 -> view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_end, parent, false)
            OpusParagraph.TYPE_PIC, OpusParagraph.TYPE_DIVIDER -> view =
                LayoutInflater.from(this.context).inflate(R.layout.cell_article_image, parent, false)
            OpusParagraph.TYPE_ARTICLE -> view =
                LayoutInflater.from(this.context).inflate(R.layout.cell_article_list, parent, false)
            OpusParagraph.TYPE_VIDEO -> view =
                LayoutInflater.from(this.context).inflate(R.layout.cell_dynamic_video, parent, false)
            OpusParagraph.TYPE_DYNAMIC -> view =
                LayoutInflater.from(this.context).inflate(R.layout.cell_dynamic_child, parent, false)
            else ->
                view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_textview, parent, false)
        }
        return ArticleLineHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ArticleLineHolder, position: Int) {
        if (paragraphs == null || position < 0)
            return
        val realPosition = position - 1
        if (realPosition >= 0 && realPosition >= paragraphs.size)
            return

        when (getItemViewType(position)) {
            OpusParagraph.TYPE_PIC, OpusParagraph.TYPE_DIVIDER -> {
                if (realPosition < 0 || realPosition >= paragraphs.size)
                    return
                val imageView = holder.itemView.findViewById<ImageFilterView>(R.id.imageView)
                val imageCount = holder.itemView.findViewById<TextView>(R.id.imageCount)

                if (paragraphs[realPosition].content is Array<*>) {
                    val urls = paragraphs[realPosition].content as Array<*>
                    val length = urls.size
                    if (length > 0 && urls[0] != null) {
                        val imageUrl = GlideUtil.url(urls[0] as String)
                        if (imageUrl != holder.lastImageUrl) {
                            holder.lastImageUrl = imageUrl
                            Glide.with(BiliTerminal.context).asDrawable().load(imageUrl)
                                .placeholder(R.mipmap.placeholder)
                                .transition(GlideUtil.getTransitionOptions())
                                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                .into(imageView)
                        }

                        imageView.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(context, ImageViewerActivity::class.java)
                            intent.putExtra("imageList", ArrayList(Arrays.asList(*urls)))
                            context.startActivity(intent)
                        }

                        if (length > 1)
                            imageCount.text = String.format(Locale.CHINA, "共%d张图片", length)
                    }
                }
            }

            -1 -> {
                val title = holder.itemView.findViewById<TextView>(R.id.text_title)
                val topImage = holder.itemView.findViewById<ImageView>(R.id.topImage)
                val topCount = holder.itemView.findViewById<TextView>(R.id.topCount)
                val upIcon = holder.itemView.findViewById<ImageView>(R.id.upInfo_Icon)
                val upName = holder.itemView.findViewById<TextView>(R.id.upInfo_Name)
                val upCard = holder.itemView.findViewById<MaterialCardView>(R.id.upInfo)

                if (!TextUtils.isEmpty(article.title)) {
                    title.visibility = View.VISIBLE
                    title.text = article.title
                    StringUtil.setCopy(title)
                } else
                    title.visibility = View.GONE

                if (!TextUtils.isEmpty(article.cover)) {
                    val coverUrl = GlideUtil.url(article.cover)
                    if (coverUrl != holder.lastTopImageUrl) {
                        holder.lastTopImageUrl = coverUrl
                        Glide.with(BiliTerminal.context).asDrawable().load(coverUrl)
                            .placeholder(R.mipmap.placeholder)
                            .transition(GlideUtil.getTransitionOptions())
                            .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(4f))))
                            .format(DecodeFormat.PREFER_RGB_565)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .into(topImage)
                    }
                    topCount.visibility = View.GONE
                } else if (article.topImages != null && article.topImages!!.size > 0) {
                    val firstImageUrl = GlideUtil.url(article.topImages!![0])
                    if (firstImageUrl != holder.lastTopImageUrl) {
                        holder.lastTopImageUrl = firstImageUrl
                        Glide.with(BiliTerminal.context).asDrawable().load(firstImageUrl)
                            .placeholder(R.mipmap.placeholder)
                            .transition(GlideUtil.getTransitionOptions())
                            .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(4f))))
                            .format(DecodeFormat.PREFER_RGB_565)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .into(topImage)
                    }
                    topImage.setOnClickListener {
                        val intent = Intent()
                        intent.setClass(context, ImageViewerActivity::class.java)
                        intent.putExtra("imageList", article.topImages)
                        context.startActivity(intent)
                    }
                    if (article.topImages!!.size > 1) {
                        topCount.text = String.format(Locale.CHINA, "共%d张图片", article.topImages!!.size)
                        topCount.visibility = View.VISIBLE
                    } else
                        topCount.visibility = View.GONE
                } else
                    holder.itemView.findViewById<View>(R.id.topImageLayout).visibility = View.GONE

                upName.text = article.upInfo.name
                val avatarUrl = GlideUtil.url(article.upInfo.avatar)
                if (avatarUrl != holder.lastAvatarUrl) {
                    holder.lastAvatarUrl = avatarUrl
                    Glide.with(BiliTerminal.context).asDrawable().load(avatarUrl)
                        .placeholder(R.mipmap.akari)
                        .transition(GlideUtil.getTransitionOptions())
                        .apply(RequestOptions.circleCropTransform())
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(upIcon)
                }
                upCard.setOnClickListener {
                    val intent = Intent()
                    intent.setClass(context, UserInfoActivity::class.java)
                    intent.putExtra("mid", article.upInfo.mid)
                    context.startActivity(intent)
                }

            }

            -2 -> {
                val viewCount = holder.itemView.findViewById<TextView>(R.id.viewCount)
                val timeText = holder.itemView.findViewById<TextView>(R.id.timeText)
                val cvidText = holder.itemView.findViewById<TextView>(R.id.cvidText)
                val viewIcon = holder.itemView.findViewById<ImageView>(R.id.viewIcon)
                val timeIcon = holder.itemView.findViewById<ImageView>(R.id.timeIcon)
                val cvidIcon = holder.itemView.findViewById<ImageView>(R.id.cvidIcon)

                if (article.id > 0 && article.type != Opus.TYPE_DYNAMIC) {
                    cvidText.text = "cv" + article.id
                    StringUtil.setCopy(cvidText, "cv" + article.id)
                    cvidText.visibility = View.VISIBLE
                    cvidIcon.visibility = View.VISIBLE
                } else {
                    cvidText.visibility = View.GONE
                    cvidIcon.visibility = View.GONE
                }

                if (article.stats != null) {
                    viewCount.text = StringUtil.toWan(article.stats.view.toLong()) + "阅读"
                    viewCount.visibility = View.VISIBLE
                    viewIcon.visibility = View.VISIBLE
                } else {
                    viewCount.visibility = View.GONE
                    viewIcon.visibility = View.GONE
                }

                if (article.pubTime != null && !article.pubTime!!.isEmpty()) {
                    timeText.text = article.pubTime
                    timeText.visibility = View.VISIBLE
                    timeIcon.visibility = View.VISIBLE
                } else {
                    timeText.visibility = View.GONE
                    timeIcon.visibility = View.GONE
                }

                val like = holder.itemView.findViewById<ImageButton>(R.id.btn_like)
                val coin = holder.itemView.findViewById<ImageButton>(R.id.btn_coin)
                val likeLabel = holder.itemView.findViewById<TextView>(R.id.like_label)
                val coinLabel = holder.itemView.findViewById<TextView>(R.id.coin_label)
                val favLabel = holder.itemView.findViewById<TextView>(R.id.fav_label)
                val fav = holder.itemView.findViewById<ImageButton>(R.id.btn_fav)

                likeLabel.text = StringUtil.toWan(article.stats.like.toLong())
                coinLabel.text = StringUtil.toWan(article.stats.coin.toLong())
                favLabel.text = StringUtil.toWan(article.stats.favorite.toLong())

                if (article.stats.liked)
                    like.setImageResource(R.drawable.icon_like_1)
                if (article.stats.coined >= 1)
                    coin.setImageResource(R.drawable.icon_coin_1)
                if (article.stats.favoured)
                    fav.setImageResource(R.drawable.icon_fav_1)

                like.setOnClickListener {
                    CenterThreadPool.run {
                        try {
                            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                                context.runOnUiThread { MsgUtil.showMsg("还没有登录喵~") }
                                return@run
                            }
                            val result = ArticleApi.like(article.id, !article.stats.liked)
                            if (result == 0) {
                                article.stats.liked = !article.stats.liked
                                context.runOnUiThread {
                                    MsgUtil.showMsg((if (article.stats.liked) "点赞成功" else "取消成功"))

                                    if (article.stats.liked)
                                        likeLabel.text = StringUtil.toWan((++article.stats.like).toLong())
                                    else
                                        likeLabel.text = StringUtil.toWan((--article.stats.like).toLong())
                                    like.setImageResource(
                                        if (article.stats.liked) R.drawable.icon_like_1 else R.drawable.icon_like_0
                                    )
                                }
                            } else {
                                context.runOnUiThread { MsgUtil.showMsg("操作失败：" + result) }
                            }
                        } catch (e: Exception) {
                            context.runOnUiThread { MsgUtil.err(e) }
                        }
                    }
                }

                coin.setOnClickListener {
                    CenterThreadPool.run {
                        if (article.stats.coined < article.stats.coin_limit) {
                            try {
                                if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                                    context.runOnUiThread { MsgUtil.showMsg("还没有登录喵~") }
                                    return@run
                                }
                                val result = ArticleApi.addCoin(article.id, article.upInfo.mid, 1)
                                if (result == 0) {
                                    if (++coinAdd <= 2)
                                        article.stats.coined++
                                    context.runOnUiThread {
                                        MsgUtil.showMsg("投币成功！")
                                        coinLabel.text = StringUtil.toWan((++article.stats.coin).toLong())
                                        coin.setImageResource(R.drawable.icon_coin_1)
                                    }
                                } else {
                                    var msg = "投币失败：" + result
                                    if (result == 34002) {
                                        msg = "不能给自己投币哦！"
                                    }
                                    val finalMsg = msg
                                    context.runOnUiThread { MsgUtil.showMsg(finalMsg) }
                                }
                            } catch (e: Exception) {
                                context.runOnUiThread { MsgUtil.err(e) }
                            }
                        } else {
                            context.runOnUiThread { MsgUtil.showMsg("投币数量到达上限") }
                        }
                    }
                }

                fav.setOnClickListener {
                    CenterThreadPool.run {
                        try {
                            if (article.stats.favoured) {
                                if (ArticleApi.delFavorite(article.id) == 0) {
                                    context.runOnUiThread { fav.setImageResource(R.drawable.icon_fav_0) }
                                    article.stats.favorite--
                                }
                            } else {
                                if (ArticleApi.favorite(article.id) == 0) {
                                    context.runOnUiThread { fav.setImageResource(R.drawable.icon_fav_1) }
                                    article.stats.favorite++
                                }
                            }
                            article.stats.favoured = !article.stats.favoured
                            context.runOnUiThread {
                                favLabel.text = StringUtil.toWan(article.stats.favorite.toLong())
                                MsgUtil.showMsg("操作成功~")
                            }
                        } catch (e: Exception) {
                            context.runOnUiThread { MsgUtil.err(e) }
                        }
                    }
                }
            }

            OpusParagraph.TYPE_VIDEO -> {
            }

            OpusParagraph.TYPE_ARTICLE -> {
            }

            else -> {
                if (realPosition >= 0 && realPosition < paragraphs.size && paragraphs[realPosition].content != null) {
                    val textView = holder.itemView.findViewById<TextView>(R.id.textView)
                    if (paragraphs[realPosition].content is CharSequence) {
                        textView.text = paragraphs[realPosition].content as CharSequence
                        StringUtil.setCopy(textView)
                        StringUtil.setLink(textView)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        if (paragraphs == null)
            return 2
        return paragraphs.size + 2
    }

    override fun onViewRecycled(holder: ArticleLineHolder) {
        holder.lastTopImageUrl = null
        holder.lastAvatarUrl = null
        holder.lastImageUrl = null
        super.onViewRecycled(holder)
    }

    override fun getItemViewType(position: Int): Int {
        if (paragraphs == null)
            return -1
        if (position == 0)
            return -1
        else if (position == paragraphs.size + 1)
            return -2
        else {
            val realPosition = position - 1
            if (realPosition >= 0 && realPosition < paragraphs.size) {
                return paragraphs[realPosition].type
            }
            return -1
        }
    }

    class ArticleLineHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var lastTopImageUrl: String? = null
        var lastAvatarUrl: String? = null
        var lastImageUrl: String? = null
    }
}