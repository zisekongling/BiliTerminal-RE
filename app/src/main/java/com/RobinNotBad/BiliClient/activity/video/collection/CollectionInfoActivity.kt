package com.RobinNotBad.BiliClient.activity.video.collection

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
import com.RobinNotBad.BiliClient.model.Collection
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class CollectionInfoActivity : RefreshListActivity() {
    private var collection: Collection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fromAid = intent.getLongExtra("fromVideo", -1)
        val seasonId = intent.getIntExtra("season_id", -1)
        val mid = intent.getLongExtra("mid", -1)
        setPageName("合集详情")

        TerminalContext.getInstance().getVideoInfoByAidOrBvId(fromAid, null).observe(this) { result ->
            result.onSuccess { videoInfo ->
                collection = videoInfo.collection

                val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
                if (collection!!.sections == null && collection!!.cards != null) {
                    adapter = CardAdapter(this, collection!!)
                } else if (collection!!.sections != null) {
                    adapter = SectionAdapter(this, collection!!, recyclerView)
                    val sections = collection!!.sections!!
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

                setAdapter(adapter)
                setRefreshing(false)
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
                holder.desc.text = if (TextUtils.isEmpty(collection.intro)) "这里没有简介哦" else collection.intro
                holder.playTimes.text = "共" + collection.view
                Glide.with(context).asDrawable().load(GlideUtil.url(collection.cover))
                    .transition(GlideUtil.getTransitionOptions())
                    .placeholder(R.mipmap.placeholder)
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
                holder.desc.text = if (TextUtils.isEmpty(collection.intro)) "这里没有简介哦" else collection.intro
                holder.playTimes.text = "共" + collection.view
                Glide.with(context).asDrawable().load(GlideUtil.url(collection.cover))
                    .transition(GlideUtil.getTransitionOptions())
                    .placeholder(R.mipmap.placeholder)
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