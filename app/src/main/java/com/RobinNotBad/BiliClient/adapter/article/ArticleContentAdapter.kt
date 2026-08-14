package com.RobinNotBad.BiliClient.adapter.article

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
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
import com.RobinNotBad.BiliClient.model.ArticleInfo
import com.RobinNotBad.BiliClient.model.ArticleLine
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
import java.text.SimpleDateFormat
import java.util.ArrayList

class ArticleContentAdapter(
    private val context: Activity,
    private val articleInfo: ArticleInfo,
    private val article: ArrayList<ArticleLine>
) : RecyclerView.Adapter<ArticleContentAdapter.ArticleLineHolder>() {

    private var coinAdd = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleLineHolder {
        val view: View
        when (viewType) {
            1 -> view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_image, parent, false)
            -1 -> view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_head, parent, false)
            -2 -> view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_end, parent, false)
            2 -> view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_heading, parent, false)
            3 -> view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_blockquote, parent, false)
            5 -> view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_code, parent, false)
            6 -> view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_hr, parent, false)
            else -> view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_textview, parent, false)
        }
        return ArticleLineHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ArticleLineHolder, position: Int) {
        if (article == null || position < 0)
            return
        val realPosition = position - 1
        if (realPosition >= 0 && realPosition >= article.size)
            return

        when (getItemViewType(position)) {
            1 -> {
                if (realPosition < 0 || realPosition >= article.size)
                    return
                val imageView = holder.itemView as ImageFilterView
                val line = article[realPosition]
                if (line == null || line.content == null)
                    return

                val url = GlideUtil.url(line.content)
                if (url != holder.lastImageUrl) {
                    holder.lastImageUrl = url
                    Glide.with(BiliTerminal.context).asDrawable().load(url)
                        .placeholder(R.mipmap.placeholder)
                        .transition(GlideUtil.getTransitionOptions())
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(imageView)
                }

                imageView.setOnClickListener {
                    val intent = Intent()
                    intent.setClass(context, ImageViewerActivity::class.java)
                    val imageList = ArrayList<String>()
                    imageList.add(url)
                    intent.putExtra("imageList", imageList)
                    context.startActivity(intent)
                }
            }

            2 -> {
                if (realPosition < 0 || realPosition >= article.size)
                    return
                val line = article[realPosition]
                if (line == null || line.content == null)
                    return
                val headingView = holder.itemView.findViewById<TextView>(R.id.text_heading)
                headingView.text = line.content
                StringUtil.setCopy(headingView)
            }

            3 -> {
                if (realPosition < 0 || realPosition >= article.size)
                    return
                val line = article[realPosition]
                if (line == null || line.content == null)
                    return
                val quoteView = holder.itemView.findViewById<TextView>(R.id.text_blockquote)
                quoteView.text = line.content
                StringUtil.setCopy(quoteView)
                StringUtil.setLink(quoteView)
            }

            5 -> {
                if (realPosition < 0 || realPosition >= article.size)
                    return
                val line = article[realPosition]
                if (line == null || line.content == null)
                    return
                val codeView = holder.itemView.findViewById<TextView>(R.id.text_code)
                codeView.text = line.content
                StringUtil.setCopy(codeView)
            }

            6 -> {
            }

            -1 -> {
                val title = holder.itemView.findViewById<TextView>(R.id.text_title)
                val cover = holder.itemView.findViewById<ImageView>(R.id.img_cover)
                val upIcon = holder.itemView.findViewById<ImageView>(R.id.upInfo_Icon)
                val upName = holder.itemView.findViewById<TextView>(R.id.upInfo_Name)
                val upCard = holder.itemView.findViewById<MaterialCardView>(R.id.upInfo)

                StringUtil.setCopy(title)

                upName.text = articleInfo.upInfo.name
                if (articleInfo.banner.isEmpty())
                    cover.visibility = View.GONE
                else {
                    val bannerUrl = GlideUtil.url(articleInfo.banner)
                    if (bannerUrl != holder.lastTopImageUrl) {
                        holder.lastTopImageUrl = bannerUrl
                        Glide.with(BiliTerminal.context).asDrawable().load(bannerUrl)
                            .placeholder(R.mipmap.placeholder)
                            .transition(GlideUtil.getTransitionOptions())
                            .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(4f))))
                            .format(DecodeFormat.PREFER_RGB_565)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .into(cover)
                    }
                }

                val avatarUrl = GlideUtil.url(articleInfo.upInfo.avatar)
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
                    intent.putExtra("mid", articleInfo.upInfo.mid)
                    context.startActivity(intent)
                }
                val like = holder.itemView.findViewById<ImageButton>(R.id.btn_like)
                val coin = holder.itemView.findViewById<ImageButton>(R.id.btn_coin)
                val likeLabel = holder.itemView.findViewById<TextView>(R.id.like_label)
                val coinLabel = holder.itemView.findViewById<TextView>(R.id.coin_label)
                val favLabel = holder.itemView.findViewById<TextView>(R.id.fav_label)
                val fav = holder.itemView.findViewById<ImageButton>(R.id.btn_fav)

                likeLabel.text = StringUtil.toWan(articleInfo.stats.like.toLong())
                coinLabel.text = StringUtil.toWan(articleInfo.stats.coin.toLong())
                favLabel.text = StringUtil.toWan(articleInfo.stats.favorite.toLong())

                like.setOnClickListener {
                    CenterThreadPool.run {
                        try {
                            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                                context.runOnUiThread { MsgUtil.showMsg("还没有登录喵~") }
                                return@run
                            }
                            val result = ArticleApi.like(articleInfo.id, !articleInfo.stats.liked)
                            if (result == 0) {
                                articleInfo.stats.liked = !articleInfo.stats.liked
                                context.runOnUiThread {
                                    MsgUtil.showMsg((if (articleInfo.stats.liked) "点赞成功" else "取消成功"))

                                    if (articleInfo.stats.liked)
                                        likeLabel.text = StringUtil.toWan((++articleInfo.stats.like).toLong())
                                    else
                                        likeLabel.text = StringUtil.toWan((--articleInfo.stats.like).toLong())
                                    like.setImageResource(
                                        if (articleInfo.stats.liked) R.drawable.icon_like_1 else R.drawable.icon_like_0
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
                        if (articleInfo.stats.coined < articleInfo.stats.coin_limit) {
                            try {
                                if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                                    context.runOnUiThread { MsgUtil.showMsg("还没有登录喵~") }
                                    return@run
                                }
                                val result = ArticleApi.addCoin(articleInfo.id, articleInfo.upInfo.mid, 1)
                                if (result == 0) {
                                    if (++coinAdd <= 2)
                                        articleInfo.stats.coined++
                                    context.runOnUiThread {
                                        MsgUtil.showMsg("投币成功！")
                                        coinLabel.text = StringUtil.toWan((++articleInfo.stats.coin).toLong())
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
                            if (articleInfo.stats.favoured) {
                                if (ArticleApi.delFavorite(articleInfo.id) == 0) {
                                    context.runOnUiThread { fav.setImageResource(R.drawable.icon_fav_0) }
                                    articleInfo.stats.favorite--
                                }
                            } else {
                                if (ArticleApi.favorite(articleInfo.id) == 0) {
                                    context.runOnUiThread { fav.setImageResource(R.drawable.icon_fav_1) }
                                    articleInfo.stats.favorite++
                                }
                            }
                            articleInfo.stats.favoured = !articleInfo.stats.favoured
                            context.runOnUiThread {
                                favLabel.text = StringUtil.toWan(articleInfo.stats.favorite.toLong())
                                MsgUtil.showMsg("操作成功~")
                            }
                        } catch (e: Exception) {
                            context.runOnUiThread { MsgUtil.err(e) }
                        }
                    }
                }

                CenterThreadPool.run {
                    try {
                        val viewInfo = ArticleApi.getArticleViewInfo(articleInfo.id)
                        if (viewInfo != null) {
                            articleInfo.stats = viewInfo.stats
                            articleInfo.stats.coin_limit = 1
                            context.runOnUiThread {
                                if (articleInfo.stats.coined >= 1)
                                    coin.setImageResource(R.drawable.icon_coin_1)
                                if (articleInfo.stats.liked)
                                    like.setImageResource(R.drawable.icon_like_1)
                                if (articleInfo.stats.favoured)
                                    fav.setImageResource(R.drawable.icon_fav_1)
                                notifyItemChanged(article.size + 1)
                            }
                        }
                    } catch (e: Exception) {
                        context.runOnUiThread { MsgUtil.err(e) }
                    }
                }

                title.text = articleInfo.title
            }

            -2 -> {
                val views = holder.itemView.findViewById<TextView>(R.id.viewCount)
                val timeText = holder.itemView.findViewById<TextView>(R.id.timeText)
                val cvidText = holder.itemView.findViewById<TextView>(R.id.cvidText)
                val viewIcon = holder.itemView.findViewById<ImageView>(R.id.viewIcon)
                val timeIcon = holder.itemView.findViewById<ImageView>(R.id.timeIcon)
                val cvidIcon = holder.itemView.findViewById<ImageView>(R.id.cvidIcon)

                if (articleInfo.id > 0) {
                    cvidText.text = "cv" + articleInfo.id + " | " + articleInfo.wordCount + "字"
                    StringUtil.setCopy(cvidText, "cv" + articleInfo.id)
                    cvidText.visibility = View.VISIBLE
                    cvidIcon.visibility = View.VISIBLE
                } else {
                    cvidText.visibility = View.GONE
                    cvidIcon.visibility = View.GONE
                }

                if (articleInfo.stats != null) {
                    views.text = StringUtil.toWan(articleInfo.stats.view.toLong()) + "阅读"
                    views.visibility = View.VISIBLE
                    viewIcon.visibility = View.VISIBLE
                } else {
                    views.visibility = View.GONE
                    viewIcon.visibility = View.GONE
                }

                if (articleInfo.ctime > 0) {
                    @SuppressLint("SimpleDateFormat")
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    timeText.text = sdf.format(articleInfo.ctime * 1000)
                    timeText.visibility = View.VISIBLE
                    timeIcon.visibility = View.VISIBLE
                } else {
                    timeText.visibility = View.GONE
                    timeIcon.visibility = View.GONE
                }
            }

            else -> {
                if (realPosition >= 0 && realPosition < article.size) {
                    val textLine = article[realPosition]
                    if (textLine != null && textLine.content != null) {
                        val textView = holder.itemView.findViewById<TextView>(R.id.textView)
                        textView.text = textLine.content
                        when (textLine.extra) {
                            "strong" -> textView.alpha = 0.92f
                            "br" -> textView.minimumHeight = ToolsUtil.dp2px(6f)
                            else -> textView.alpha = 0.85f
                        }
                        StringUtil.setCopy(textView)
                        StringUtil.setLink(textView)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return article.size + 2
    }

    override fun onViewRecycled(holder: ArticleLineHolder) {
        holder.lastImageUrl = null
        holder.lastTopImageUrl = null
        holder.lastAvatarUrl = null
        super.onViewRecycled(holder)
    }

    override fun getItemViewType(position: Int): Int {
        if (article == null)
            return -1
        if (position == 0)
            return -1
        else if (position == article.size + 1)
            return -2
        else {
            val realPosition = position - 1
            if (realPosition >= 0 && realPosition < article.size) {
                return article[realPosition].type
            }
            return 0
        }
    }

    class ArticleLineHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var lastImageUrl: String? = null
        var lastTopImageUrl: String? = null
        var lastAvatarUrl: String? = null
    }
}