package com.RobinNotBad.BiliClient.adapter.article

import android.content.Context
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.api.ArticleApi
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.model.ArticleCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import org.json.JSONObject
import java.io.File

class ArticleCardAdapter(
    private val context: Context,
    private val articleCardList: ArrayList<ArticleCard>
) : RecyclerView.Adapter<ArticleCardHolder>() {

    private var longClickListener: OnItemLongClickListener? = null

    fun setOnLongClickListener(listener: OnItemLongClickListener?) {
        this.longClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleCardHolder {
        val view = LayoutInflater.from(this.context).inflate(R.layout.cell_article_list, parent, false)
        return ArticleCardHolder(view)
    }

    override fun onBindViewHolder(holder: ArticleCardHolder, position: Int) {
        if (position < 0 || position >= articleCardList.size)
            return
        val articleCard = articleCardList[position] ?: return

        holder.showArticleCard(articleCard, context)

        holder.itemView.setOnClickListener {
            TerminalContext.getInstance().enterArticleDetailPage(context, articleCard.id)
        }

        var longPressRunnable: Runnable? = null

        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressRunnable = Runnable {
                        val quickMode = SharedPreferencesUtil.getBoolean("cache_quick_mode", false)
                        if (quickMode) {
                            handleArticleCache(articleCard)
                        } else {
                            longClickListener?.onItemLongClick(position)
                        }
                    }
                    v.postDelayed(longPressRunnable, 200)
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { v.removeCallbacks(it) }
                    false
                }
                else -> false
            }
        }
    }

    private fun handleArticleCache(articleCard: ArticleCard) {
        CenterThreadPool.run {
            try {
                val cacheDir = File(BiliTerminal.context.filesDir, "article_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val cacheFile = File(cacheDir, "${articleCard.id}.json")
                if (cacheFile.exists()) {
                    CenterThreadPool.runOnUiThread { MsgUtil.showMsg("该专栏已缓存") }
                    return@run
                }

                val articleInfo = ArticleApi.getArticle(articleCard.id)
                if (articleInfo != null) {
                    val json = JSONObject()
                    json.put("id", articleInfo.id)
                    json.put("title", articleInfo.title)
                    json.put("summary", articleInfo.summary)
                    json.put("banner", articleInfo.banner)
                    json.put("ctime", articleInfo.ctime)
                    json.put("content", articleInfo.content)
                    json.put("wordCount", articleInfo.wordCount)
                    json.put("keywords", articleInfo.keywords)
                    if (articleInfo.upInfo != null) {
                        val author = JSONObject()
                        author.put("mid", articleInfo.upInfo.mid)
                        author.put("name", articleInfo.upInfo.name)
                        author.put("avatar", articleInfo.upInfo.avatar)
                        json.put("author", author)
                    }
                    if (articleInfo.stats != null) {
                        val stats = JSONObject()
                        stats.put("view", articleInfo.stats.view)
                        stats.put("favorite", articleInfo.stats.favorite)
                        stats.put("like", articleInfo.stats.like)
                        stats.put("reply", articleInfo.stats.reply)
                        json.put("stats", stats)
                    }
                    cacheFile.writeText(json.toString())
                    CenterThreadPool.runOnUiThread { MsgUtil.showMsg("专栏已缓存") }
                } else {
                    CenterThreadPool.runOnUiThread { MsgUtil.showMsg("获取专栏信息失败") }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { MsgUtil.showMsg("获取专栏信息失败") }
                e.printStackTrace()
            }
        }
    }

    override fun getItemCount(): Int {
        return articleCardList.size
    }

}