package com.RobinNotBad.BiliClient.activity.video.series

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.RobinNotBad.BiliClient.api.SeriesApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class SeriesInfoActivity : RefreshListActivity() {

    private var seriesType: String = ""
    private var seriesMid: Long = 0
    private var seriesId: Int = 0
    private var seriesName: String = ""
    private var seriesCover: String = ""
    private var seriesIntro: String = ""
    private var seriesTotal: String = ""
    private var adapter: SeriesVideoAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        seriesType = intent.getStringExtra("type") ?: "series"
        seriesMid = intent.getLongExtra("mid", 0)
        seriesId = intent.getIntExtra("sid", 0)
        seriesName = intent.getStringExtra("name") ?: UiText.of("系列详情")
        seriesCover = intent.getStringExtra("cover") ?: ""
        seriesIntro = intent.getStringExtra("intro") ?: ""
        seriesTotal = intent.getStringExtra("total") ?: ""

        setPageName(seriesName)

        loadData(1)
        setOnRefreshListener { loadData(1) }
        setOnEmptyRetry { loadData(1) }
        setOnLoadMoreListener {
            loadData(it)
        }
    }

    private fun loadData(page: Int) {
        CenterThreadPool.run {
            try {
                val videoList = ArrayList<VideoCard>()
                val pageInfo = SeriesApi.getSeriesInfo(seriesType, seriesMid, seriesId, page, videoList)

                if (page == 1) {
                    if (videoList.isEmpty()) {
                        runOnUiThread {
                            showEmptyView()
                            setRefreshing(false)
                        }
                        return@run
                    }

                    runOnUiThread {
                        adapter = SeriesVideoAdapter(this@SeriesInfoActivity, videoList)
                        setAdapter(adapter!!)
                        setRefreshing(false)
                        hideEmptyView()
                    }
                } else {
                    runOnUiThread {
                        val oldSize = adapter?.itemCount ?: 0
                        adapter?.addData(videoList)
                        adapter?.notifyItemRangeInserted(oldSize, videoList.size)
                        onLoadComplete()
                        setRefreshing(false)
                    }

                    if (videoList.size < pageInfo.return_ps) {
                        bottom = true
                    }
                }
            } catch (e: Exception) {
                if (page == 1) {
                    runOnUiThread {
                        showEmptyView()
                        setRefreshing(false)
                    }
                } else {
                    loadFail(e)
                }
                e.printStackTrace()
            }
        }
    }

    class SeriesVideoAdapter(
        private val context: Context,
        private val data: MutableList<VideoCard>
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        fun addData(newData: List<VideoCard>) {
            data.addAll(newData)
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) -1 else 0
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == -1) {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_collection_info, parent, false)
                SeriesInfoHolder(view)
            } else {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_video_list, parent, false)
                VideoCardHolder(view)
            }
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is VideoCardHolder) {
                val videoCard = data[position - 1]
                holder.itemView.setOnClickListener {
                    TerminalContext.getInstance().enterVideoDetailPage(context, videoCard.aid, videoCard.bvid)
                }
                holder.showVideoCard(videoCard, context)
            } else if (holder is SeriesInfoHolder) {
                val activity = context as SeriesInfoActivity
                holder.name.text = activity.seriesName
                holder.desc.text = activity.seriesIntro.ifEmpty { UiText.of("这里没有简介哦") }
                holder.playTimes.text = "共${activity.seriesTotal}"
                // 26.11.21（崩溃 #390）：这里原来是 Glide.with(context)，而 context 就是本页 Activity。
                // 页面已经 finish 时 Glide 4 会直接抛 IllegalArgumentException（You cannot start a load for
                // a destroyed activity）——第一次 bind 就可能赶上（从详情页返回、列表在 onDestroy 后还刷新一次）。
                // GlideUtil.with(view) 会判活并在必要时回落 Application 级，同时把请求绑到页面生命周期上。
                GlideUtil.with(holder.cover).asDrawable().load(GlideUtil.url_hq(activity.seriesCover))
                    .apply(GlideUtil.thumbOptions(holder.cover))
                    .transition(GlideUtil.getTransitionOptions())
                    .apply(GlideUtil.withPlaceholder(holder.cover.context, R.mipmap.placeholder))
                    .format(DecodeFormat.PREFER_RGB_565)
                    .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))).sizeMultiplier(0.85f).dontAnimate())
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(holder.cover)
                holder.cover.setOnClickListener {
                    if (activity.seriesCover.isNotEmpty()) {
                        context.startActivity(Intent(context, ImageViewerActivity::class.java).putExtra("imageList", ArrayList(listOf(activity.seriesCover))))
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return data.size + 1
        }

        class SeriesInfoHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.name)
            val desc: TextView = itemView.findViewById(R.id.desc)
            val playTimes: TextView = itemView.findViewById(R.id.playTimes)
            val cover: ImageView = itemView.findViewById(R.id.img_cover)
        }
    }
}