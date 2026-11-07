package com.RobinNotBad.BiliClient.activity.video.collection

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardHolder
import com.RobinNotBad.BiliClient.api.SubscriptionApi
import com.RobinNotBad.BiliClient.model.Collection
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.subscriptionErrorText
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class CollectionInfoActivity : RefreshListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fromAid = intent.getLongExtra("fromVideo", -1)
        setPageName(UiText.of("合集详情"))
        // 失败后可点空态重试（失败结果不入缓存，重建即重新请求）
        setOnEmptyRetry { recreate() }

        TerminalContext.getInstance().getVideoInfoByAidOrBvId(fromAid, null).observe(this) { result ->
            result.onSuccess { videoInfo ->
                val collection = videoInfo.collection
                if (collection == null) {
                    // 视频不属于任何合集时 collection 为 null，此前直接 collection!! 会在这里崩溃
                    showEmptyView()
                    setRefreshing(false)
                    return@onSuccess
                }

                val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
                if (collection.sections == null && collection.cards != null) {
                    adapter = CardAdapter(this, collection)
                } else if (collection.sections != null) {
                    adapter = SectionAdapter(this, collection, recyclerView)
                    val sections = collection.sections!!
                    var pos = 1
                    for (section in sections) {
                        pos++
                        val episodes = section.episodes
                        for (episode in episodes) {
                            pos++
                            if (episode.aid == fromAid) {
                                recyclerView.layoutManager?.scrollToPosition(--pos)
                            }
                        }
                    }
                } else {
                    finish()
                    return@onSuccess
                }

                hideEmptyView()
                setAdapter(adapter)
                setRefreshing(false)
                setupSubscribeBar(collection)
            }.onFailure { error ->
                // 此前只写了 onSuccess：请求失败时既没有提示，setRefreshing(false) 也永不执行，
                // 页面会一直转圈。注意原代码还多读了 season_id / mid 两个 extra，
                // 但全库没有任何地方 putExtra 它们（唯一入口只传 fromVideo），属于死变量，已删除。
                showEmptyView()
                setRefreshing(false)
                MsgUtil.err(error)
            }
        }
    }

    // ==================== 合集订阅 ====================

    private var subToggle: TextView? = null
    private var isSubscribed = false
    private var seasonId = 0L

    /**
     * 合集详情页的「订阅 / 已订阅」条。
     *
     * <p>订阅的是**视频合集**（`season_id` = `ugc_season.id`），接口
     * `x/v3/fav/season/fav`；状态查 `x/space/fav/season/list` 的 `info.fav_state`。
     * 自己的合集、未登录、拿不到 season_id 时不显示。
     */
    private fun setupSubscribeBar(collection: Collection) {
        seasonId = collection.id.toLong()
        if (seasonId <= 0) return
        val myMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
        if (myMid == 0L) return
        if (collection.mid == myMid) return

        findViewById<View>(R.id.subBar).visibility = View.VISIBLE
        subToggle = findViewById<TextView>(R.id.subToggle).also { toggle ->
            toggle.text = getString(R.string.text_sub_toggle_loading)
            toggle.setOnClickListener { onToggleSubscribe() }
        }

        CenterThreadPool.run {
            try {
                val res = SubscriptionApi.getSeasonFavState(seasonId)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (res.first == 0) {
                        isSubscribed = res.second == 1
                        updateSubToggle()
                    } else {
                        findViewById<View>(R.id.subBar).visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    findViewById<View>(R.id.subBar).visibility = View.GONE
                }
            }
        }
    }

    private fun updateSubToggle() {
        subToggle?.text = getString(
            if (isSubscribed) R.string.msg_sub_subscribed else R.string.text_sub_toggle_subscribe
        )
    }

    private fun onToggleSubscribe() {
        if (isSubscribed) {
            TerminalDialog.confirm(
                context = this,
                title = getString(R.string.pagename_dialog),
                message = getString(R.string.msg_sub_confirm_unsub_season),
                onConfirm = { doSubscribeAction(false) }
            ).show()
        } else {
            doSubscribeAction(true)
        }
    }

    private fun doSubscribeAction(subscribe: Boolean) {
        val toggle = subToggle ?: return
        toggle.isEnabled = false
        CenterThreadPool.run {
            try {
                val code = if (subscribe) {
                    SubscriptionApi.subscribeSeason(seasonId)
                } else {
                    SubscriptionApi.cancelSeasonSubscription(seasonId)
                }
                runOnUiThread {
                    toggle.isEnabled = true
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (code == 0) {
                        isSubscribed = subscribe
                        updateSubToggle()
                        MsgUtil.showMsg(
                            getString(if (subscribe) R.string.msg_sub_subscribed else R.string.msg_subscription_cancelled)
                        )
                    } else {
                        MsgUtil.showMsg(subscriptionErrorText(code))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { toggle.isEnabled = true }
                report(e)
            }
        }
    }

    class CardAdapter(
        private val context: Context,
        private val collection: Collection
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val data: List<VideoCard> = collection.cards!!

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) -1 else 0
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == -1) {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_collection_info, parent, false)
                CollectionInfoHolder(view)
            } else {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_video_list, parent, false)
                VideoCardHolder(view)
            }
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is VideoCardHolder) {
                var pos = position - 1
                val videoCard = data[pos]
                holder.itemView.setOnClickListener {
                    TerminalContext.getInstance().enterVideoDetailPage(context, videoCard.aid, videoCard.bvid)
                }
                holder.showVideoCard(videoCard, context)
            } else if (holder is CollectionInfoHolder) {
                holder.name.text = collection.title
                holder.desc.text = if (TextUtils.isEmpty(collection.intro)) UiText.of("这里没有简介哦") else collection.intro
                holder.playTimes.text = UiText.fmt(UiText.of("共%1\$s"), collection.view)
                Glide.with(context).asDrawable().load(GlideUtil.url_hq(collection.cover))
                    .transition(GlideUtil.getTransitionOptions())
                    .placeholder(R.mipmap.placeholder)
                    .error(R.mipmap.placeholder)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))).sizeMultiplier(0.85f).dontAnimate())
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(holder.cover)
                holder.cover.setOnClickListener {
                    context.startActivity(Intent(context, ImageViewerActivity::class.java).putExtra("imageList", ArrayList(listOf(collection.cover))))
                }
                StringUtil.setCopy(holder.name, holder.desc)
                StringUtil.setLink(holder.desc)
            }
        }

        override fun getItemCount(): Int {
            return data.size + 1
        }

        class CollectionInfoHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.name)
            val desc: TextView = itemView.findViewById(R.id.desc)
            val playTimes: TextView = itemView.findViewById(R.id.playTimes)
            val cover: ImageView = itemView.findViewById(R.id.img_cover)
        }
    }

    class SectionAdapter(
        private val context: Context,
        private val collection: Collection,
        private val recyclerView: RecyclerView
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val data: List<Collection.Section> = collection.sections!!
        private val types = ArrayList<Int>()

        override fun getItemViewType(position: Int): Int {
            if (position == 0) return -1
            return getTypes()[position - 1]
        }

        private fun getTypes(): List<Int> {
            synchronized(this) {
                types.clear()
                for (section in data) {
                    types.add(1)
                    for (i in section.episodes.indices) {
                        types.add(0)
                    }
                }
                return types
            }
        }

        private fun getSectionPos(pos: Int): Int {
            val list = getTypes()
            var sectionPos = -1
            for (i in 0..pos) {
                if (list[i] == 1) sectionPos++
            }
            return sectionPos
        }

        private fun getEpisodePos(pos: Int): Int {
            val list = getTypes()
            var episodePos = -1
            for (i in pos downTo 0) {
                if (list[i] == 1) return episodePos
                episodePos++
            }
            return 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                -1 -> {
                    val view = LayoutInflater.from(context).inflate(R.layout.cell_collection_info, parent, false)
                    CollectionInfoHolder(view)
                }
                0 -> {
                    val view = LayoutInflater.from(context).inflate(R.layout.cell_video_list, parent, false)
                    VideoCardHolder(view)
                }
                else -> SectionHolder(TextView(context))
            }
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is SectionHolder) {
                var pos = position - 1
                holder.item.text = data[getSectionPos(pos)].title
            } else if (holder is VideoCardHolder) {
                var pos = position - 1
                val videoInfo = data[getSectionPos(pos)].episodes[getEpisodePos(pos)].arc
                val videoCard = VideoCard(videoInfo.title, "", StringUtil.toWan(videoInfo.stats.view.toLong()), videoInfo.cover, videoInfo.aid, videoInfo.bvid)
                holder.itemView.setOnClickListener {
                    TerminalContext.getInstance().enterVideoDetailPage(context, videoCard.aid, videoCard.bvid)
                }
                holder.showVideoCard(videoCard, context)
            } else if (holder is CollectionInfoHolder) {
                holder.name.text = collection.title
                holder.desc.text = if (TextUtils.isEmpty(collection.intro)) UiText.of("这里没有简介哦") else collection.intro
                holder.playTimes.text = UiText.fmt(UiText.of("共%1\$s"), collection.view)
                Glide.with(context).asDrawable().load(GlideUtil.url_hq(collection.cover))
                    .transition(GlideUtil.getTransitionOptions())
                    .placeholder(R.mipmap.placeholder)
                    .error(R.mipmap.placeholder)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))).sizeMultiplier(0.85f).dontAnimate())
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(holder.cover)
                holder.cover.setOnClickListener {
                    context.startActivity(Intent(context, ImageViewerActivity::class.java).putExtra("imageList", ArrayList(listOf(collection.cover))))
                }
            }
        }

        override fun getItemCount(): Int {
            var count = 0
            for (section in data) {
                count++
                count += section.episodes.size
            }
            return ++count
        }

        class SectionHolder(itemView: TextView) : RecyclerView.ViewHolder(itemView) {
            val item: TextView = itemView

            init {
                item.left = 5
            }
        }

        class CollectionInfoHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.name)
            val desc: TextView = itemView.findViewById(R.id.desc)
            val playTimes: TextView = itemView.findViewById(R.id.playTimes)
            val cover: ImageView = itemView.findViewById(R.id.img_cover)
        }
    }
}