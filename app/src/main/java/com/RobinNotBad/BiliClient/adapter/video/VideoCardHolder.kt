package com.RobinNotBad.BiliClient.adapter.video

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.video.JumpToPlayerActivity
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity
import com.RobinNotBad.BiliClient.api.BangumiApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme

class VideoCardHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
    lateinit var title: TextView
    lateinit var upName: TextView
    lateinit var viewCount: TextView
    lateinit var cover: ImageView
    private var lastCoverUrl: String? = null
    private var boundVideoCard: VideoCard? = null
    private var boundContext: Context? = null

    init {
        title = itemView.findViewById(R.id.text_title)
        upName = itemView.findViewById(R.id.text_upname)
        viewCount = itemView.findViewById(R.id.text_viewcount)
        cover = itemView.findViewById(R.id.img_cover)
    }

    private var longPressRunnable: Runnable? = null
    private var customClickCallback: (() -> Unit)? = null

    /**
     * 长按已经处理过这次触摸，抬手时不要再当成点击。
     *
     * <h3>为什么需要它</h3>
     * 下面那个 `setOnTouchListener` 对 `ACTION_DOWN` 返回 `false`——不吞事件，是为了让条目
     * 自己继续处理滚动/点击。代价是 `View.onTouchEvent` 照样会在 `ACTION_UP` 上补发一次
     * `performClick()`。于是同一次长按会走两条路：「200ms 后的长按回调」和「抬手时的点击」。
     *
     * 在多选态里这两条路都是 `toggleSelected(position)`：长按先把它选上，抬手立刻又取消，
     * 净效果是**长按选不中任何条目**。不在多选态时则是「长按弹了菜单，抬手又跳进了视频详情页」。
     * 所以长按回调一旦真的执行，就把这个标记立起来，让紧随其后的那次点击自己吞掉。
     */
    private var suppressClickAfterLongPress = false

    fun bindClick(videoCard: VideoCard, context: Context, position: Int, longClickListener: View.OnLongClickListener?) {
        this.boundVideoCard = videoCard
        this.boundContext = context

        // 先清除旧的触摸检测
        longPressRunnable?.let { itemView.removeCallbacks(it) }
        customClickCallback = null
        // 复用同一个 ViewHolder 绑定新条目时，上一次遗留的标记必须清零
        suppressClickAfterLongPress = false

        // 设置点击事件
        itemView.setOnClickListener {
            if (suppressClickAfterLongPress) {
                suppressClickAfterLongPress = false
                return@setOnClickListener
            }
            if (customClickCallback != null) {
                customClickCallback!!.invoke()
            } else if (boundVideoCard != null && boundContext != null) {
                when (boundVideoCard!!.type) {
                    "video" -> TerminalContext.getInstance().enterVideoDetailPage(boundContext!!, boundVideoCard!!.aid, boundVideoCard!!.bvid, "video")
                    "media_bangumi" -> {
                        val card = boundVideoCard!!
                        val ctx = boundContext!!
                        if (card.epid > 0L) {
                            // 历史记录来源的番剧卡片：aid(history.oid) 是剧集 avid，不能当 media_id。
                            // 这类卡片可以直接续播：拿 epid 反查剧集的 aid/cid/所属季，凑齐播放数据就起播，
                            // 省掉"先进详情页再点播放"的绕路。反查是网络请求，放线程池，别卡主线程
                            CenterThreadPool.run {
                                try {
                                    val result = BangumiApi.getEpisodeFromEpid(card.epid)
                                    val data = result.episode.toPlayerData(result.seasonId, result.seasonType)
                                    // 历史里的进度是秒、PlayerData.progress 是毫秒，换算只做这一次
                                    if (card.progress > 0) data.progress = card.progress * 1000
                                    val intent = Intent(ctx, JumpToPlayerActivity::class.java)
                                    intent.putExtra("data", data)
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    // 反查失败（网络异常/接口变更）时退回老口径：先反查 media_id 进详情页
                                    e.printStackTrace()
                                    openBangumiDetailPage(ctx, card)
                                }
                            }
                        } else {
                            // 追番列表/动态等来源的卡片，aid 已经是 media_id，走原来的跳转
                            TerminalContext.getInstance().enterVideoDetailPage(ctx, card.aid, null, "media")
                        }
                    }
                }
            }
        }

        if (longClickListener != null) {
            itemView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        longPressRunnable = Runnable {
                            // 标记必须在回调之前立起来：回调本身可能同步弹出菜单/切换选中态
                            suppressClickAfterLongPress = true
                            longClickListener.onLongClick(v)
                        }
                        v.postDelayed(longPressRunnable, 200)
                        false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { v.removeCallbacks(it) }
                        longPressRunnable = null
                        // 长按立起的标记只该吞掉「同一次手势抬手时补发的那次点击」。
                        // 以前只在点击分支里清：这次手势若没有补发点击（被滑动取消、或长按回调把事件接管了），
                        // 标记会一直留到下一次真实点击上，表现为「长按之后点条目没反应」。
                        // post 让清理排在本次事件分发结束之后：既不提前清掉要吞的那次点击，又不污染后续点击。
                        if (suppressClickAfterLongPress) v.post { suppressClickAfterLongPress = false }
                        false
                    }
                    else -> false
                }
            }
        } else {
            itemView.setOnTouchListener(null)
        }
    }

    /**
     * 设置自定义点击回调，覆盖默认的视频详情页跳转
     */
    fun setCustomClickCallback(callback: () -> Unit) {
        this.customClickCallback = callback
    }

    /**
     * 多选模式下的选中样式：未选中的条目整体压暗，选中的条目标题前加一个 ✓ 前缀。
     * 不做新控件、也不动 `cell_video_list.xml` 的 id——那些 id 是跨包事实协议
     * （见 `docs/architecture-map.md` 第 7 节），为多选再引入一个 id 不值得。
     */
    fun applySelection(selectionMode: Boolean, selected: Boolean) {
        // 前缀先无条件剥掉：退出多选后标题上的「✓ 」不能留在列表里，
        // 而一旦留下，下次进入多选时 `startsWith` 只能剥掉一层、越叠越多。
        if (title.text?.startsWith(PREFIX_SELECTED) == true || title.text?.startsWith(PREFIX_UNSELECTED) == true) {
            title.text = title.text.subSequence(2, title.text.length)
        }
        if (!selectionMode) {
            itemView.alpha = 1f
            return
        }
        itemView.alpha = if (selected) 1f else 0.45f
        title.text = android.text.TextUtils.concat(if (selected) PREFIX_SELECTED else PREFIX_UNSELECTED, title.text)
    }

    @SuppressLint("SetTextI18n")
    fun showVideoCard(videoCard: VideoCard, context: Context) {
        val strUpName = videoCard.upName
        // 两个分支都要显式设可见性：holder 会被复用，上一条置了 GONE、这一条有值时不恢复就一直不显示
        if (strUpName == null || strUpName.isEmpty()) {
            upName.visibility = View.GONE
        } else {
            upName.visibility = View.VISIBLE
            upName.text = strUpName
        }

        val strViewCount = videoCard.view
        if (strViewCount == null || strViewCount.isEmpty()) {
            viewCount.visibility = View.GONE
        } else {
            viewCount.visibility = View.VISIBLE
            viewCount.text = strViewCount
        }

        try {
            // 封面占半屏以上：用 url_hq（80q/1024w）而不是列表默认的 512w，
            // 否则在 1080p 屏幕上被放大后明显发虚
            val coverUrl = GlideUtil.url_hq(videoCard.cover)
            if (coverUrl != lastCoverUrl) {
                lastCoverUrl = coverUrl
                GlideUtil.with(cover).asDrawable().load(coverUrl)
                    .transition(GlideUtil.getTransitionOptions())
                    .placeholder(R.mipmap.placeholder)
                    .error(R.mipmap.placeholder)
                    .format(DecodeFormat.PREFER_ARGB_8888)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .apply(getRequestOptions())
                    .into(cover)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        when (videoCard.type) {
            "live" -> {
                val sstrLive = SpannableString(UiText.of("[直播]") + StringUtil.htmlToString(videoCard.title))
                sstrLive.setSpan(TITLE_COLOR_SPAN, 0, 4,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                title.text = sstrLive
            }
            "series" -> {
                val sstrSeries = SpannableString(UiText.of("[系列]") + StringUtil.htmlToString(videoCard.title))
                sstrSeries.setSpan(TITLE_COLOR_SPAN, 0, 4,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                title.text = sstrSeries
            }
            else -> title.text = StringUtil.htmlToString(videoCard.title)
        }
    }

    /**
     * 番剧历史卡片的兜底跳转：先把 epid 反查成 media_id，再进详情页。
     *
     * <p>直接续播失败（网络异常/接口变更）时走这条路，等价于改动前的行为，
     * 顺便把 epid 和已看进度带进详情页用于定位上次观看的那一集。
     */
    private fun openBangumiDetailPage(ctx: Context, card: VideoCard) {
        val mediaId = BangumiApi.getMdidFromEpid(card.epid)
        if (mediaId != null && mediaId > 0L) {
            val intent = Intent(ctx, VideoInfoActivity::class.java)
            intent.putExtra("aid", mediaId)
            intent.putExtra("type", "media")
            intent.putExtra("epid", card.epid)
            // 键名带 _sec 后缀：PlayerActivity 读的 extra 也叫 "progress" 但单位是毫秒，
            // 两者若被同一条 Intent 转发会静默差 1000 倍，所以这里用独立键名
            intent.putExtra("progress_sec", card.progress)
            intent.putExtra("seekReply", -1L)
            ctx.startActivity(intent)
        } else MsgUtil.showMsg(UiText.of("番剧信息获取失败"))
    }

    companion object {
        private val TITLE_COLOR_SPAN = ForegroundColorSpan(ColorScheme.PRIMARY)

        /** 多选模式下标题前缀，两者都是 2 个字符宽（后者是全角空格，用来保持对齐） */
        private const val PREFIX_SELECTED = "✓ "
        private const val PREFIX_UNSELECTED = "　 "

        @JvmStatic
        fun getRequestOptions(): RequestOptions = REQUEST_OPTIONS

        /**
         * 列表封面的圆角裁剪参数，进程内缓存一份。
         *
         * <p>此前每次绑定都 new 一个 [RequestOptions]：快速滑动时每个条目都分配一次，
         * Glide 内部还会再拷贝一份，纯粹白烧。圆角换算用的 dp2px 读的是应用级 density
         * （BiliTerminal.context），进程存活期内不会变，缓存是安全的——「设置Density」
         * 只改每个 Activity 自己的 Resources，影响不到这里。
         *
         * <p>不再写死 override(400, 225) + sizeMultiplier(0.85)：
         * 那会把解码尺寸锁在约 340×191 的绝对像素，而列表封面显示区在 1080p 屏上约 519px 宽，
         * 必然被放大糊掉；交给 Glide 按 ImageView 实测尺寸解码即可。
         */
        private val REQUEST_OPTIONS: RequestOptions by lazy {
            RequestOptions()
                .transform(CenterCrop(), RoundedCorners(ToolsUtil.dp2px(5f)))
        }
    }
}