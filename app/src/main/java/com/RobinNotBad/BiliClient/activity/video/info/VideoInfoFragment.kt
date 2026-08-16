package com.RobinNotBad.BiliClient.activity.video.info

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.DisplayMetrics
import android.view.Display
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.base.BaseFragment
import com.RobinNotBad.BiliClient.activity.dynamic.send.SendDynamicActivity
import com.RobinNotBad.BiliClient.activity.search.SearchActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingPlayerChooseActivity
import com.RobinNotBad.BiliClient.activity.user.WatchLaterActivity
import com.RobinNotBad.BiliClient.activity.video.MultiPageActivity
import com.RobinNotBad.BiliClient.activity.video.QualityChooserActivity
import com.RobinNotBad.BiliClient.activity.video.collection.CollectionInfoActivity
import com.RobinNotBad.BiliClient.adapter.user.UpListAdapter
import com.RobinNotBad.BiliClient.api.BangumiApi
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.api.HistoryApi
import com.RobinNotBad.BiliClient.api.LikeCoinFavApi
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.api.VideoInfoApi
import com.RobinNotBad.BiliClient.api.WatchLaterApi
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.ui.widget.RadiusBackgroundSpan
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.ui.theme.ThemeUtils
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File

class VideoInfoFragment : BaseFragment() {

    private val RESULT_ADDED = 1
    private val RESULT_DELETED = -1

    private val favLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { o ->
        val code = o.resultCode
        if (code == RESULT_ADDED) {
            fav.setImageResource(R.drawable.icon_fav_1)
        } else if (code == RESULT_DELETED) {
            fav.setImageResource(R.drawable.icon_fav_0)
        }
    }

    private val writeDynamicLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.resultCode
        val data = result.data
        if (code == Activity.RESULT_OK && data != null) {
            val text = data.getStringExtra("text")
            CenterThreadPool.run {
                try {
                    val atUids = HashMap<String, Long>()
                    val pattern = java.util.regex.Pattern.compile("@(\\S+)\\s")
                    val matcher = pattern.matcher(text)
                    while (matcher.find()) {
                        val matchedString = matcher.group(1)
                        val uid = DynamicApi.mentionAtFindUser(matchedString)
                        if (uid != -1L) {
                            atUids[matchedString!!] = uid
                        }
                    }
                    val dynId = DynamicApi.relayVideo(text, if (atUids.isEmpty()) null else atUids, videoInfo!!.aid)

                    if (dynId != -1L) MsgUtil.showMsg("转发成功~")
                    else MsgUtil.showMsg("转发失败")
                } catch (e: Exception) {
                    MsgUtil.err(e)
                }
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startDownloadFlow()
        } else {
            MsgUtil.showMsg("需要通知权限才能进行下载，请前往设置授予权限")
        }
    }

    private var videoInfo: VideoInfo? = null
    private var playerData: PlayerData? = null
    private var aid: Long = 0
    private var bvid: String? = null
    private lateinit var description: TextView
    private lateinit var tagsText: TextView
    private lateinit var like: ImageView
    private lateinit var coin: ImageView
    private lateinit var fav: ImageView
    private var coinAdd = 0
    private var descExpand = false
    private var tagsExpand = false
    private var shakeAnimation: Animation? = null
    private var tripleActionRunnable: Runnable? = null
    private var isTripleInProgress = false

    companion object {
        @JvmStatic
        fun newInstance(aid: Long, bvid: String?): VideoInfoFragment {
            val args = Bundle()
            args.putLong("aid", aid)
            args.putString("bvid", bvid)
            val fragment = VideoInfoFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bundle = arguments
        if (bundle == null) {
            MsgUtil.showMsg("视频详情页：数据为空")
            return
        }
        aid = bundle.getLong("aid")
        bvid = bundle.getString("bvid")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_video_info, container, false)
    }

    override fun onViewCreated(rootview: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootview, savedInstanceState)

        TerminalContext.getInstance().getVideoInfoByAidOrBvId(aid, bvid).observe(viewLifecycleOwner) { videoInfoResult ->
            videoInfoResult.onSuccess { videoInfo ->
                if (lifecycle.currentState == Lifecycle.State.DESTROYED) return@onSuccess
                this.videoInfo = videoInfo

                if (videoInfo == null) {
                    val activity = activity
                    if (activity == null) return@onSuccess
                    activity.finish()
                    return@onSuccess
                }

                initView(rootview)
            }.onFailure { }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initView(rootview: View) {
        if (SharedPreferencesUtil.getBoolean("ui_landscape", false)) {
            val windowManager = rootview.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= 17) display.getRealMetrics(metrics)
            else display.getMetrics(metrics)
            val paddings = metrics.widthPixels / 6
            rootview.setPadding(paddings, 0, paddings, 0)
        }

        val cover = rootview.findViewById<ImageView>(R.id.img_cover)
        val title = rootview.findViewById<TextView>(R.id.text_title)
        description = rootview.findViewById(R.id.description)
        tagsText = rootview.findViewById(R.id.tags)
        val exclusiveTip = rootview.findViewById<MaterialCardView>(R.id.exclusiveTip)
        val upRecyclerView = rootview.findViewById<RecyclerView>(R.id.up_recyclerView)
        val exclusiveTipLabel = rootview.findViewById<TextView>(R.id.exclusiveTipLabel)
        val viewCount = rootview.findViewById<TextView>(R.id.viewCount)
        val timeText = rootview.findViewById<TextView>(R.id.timeText)
        val durationText = rootview.findViewById<TextView>(R.id.durationText)
        val play = rootview.findViewById<MaterialButton>(R.id.play)
        val addWatchlater = rootview.findViewById<MaterialButton>(R.id.addWatchlater)
        val download = rootview.findViewById<MaterialButton>(R.id.download)

        val relay = rootview.findViewById<MaterialButton>(R.id.relay)
        val videoSummary = rootview.findViewById<MaterialButton>(R.id.video_summary)
        val bvidText = rootview.findViewById<TextView>(R.id.bvidText)
        val danmakuCount = rootview.findViewById<TextView>(R.id.danmakuCount)
        like = rootview.findViewById(R.id.btn_like)
        coin = rootview.findViewById(R.id.btn_coin)
        fav = rootview.findViewById(R.id.btn_fav)
        val likeLabel = rootview.findViewById<TextView>(R.id.like_label)
        val coinLabel = rootview.findViewById<TextView>(R.id.coin_label)
        val favLabel = rootview.findViewById<TextView>(R.id.fav_label)
        val collectionCard = rootview.findViewById<MaterialCardView>(R.id.collection)

        rootview.visibility = View.GONE

        if (videoInfo!!.epid != -1L) {
            val context = rootview.context ?: return
            CenterThreadPool.run {
                TerminalContext.getInstance()
                    .enterVideoDetailPage(context, BangumiApi.getMdidFromEpid(videoInfo!!.epid), null, "media")
                val activity = activity ?: return@run
                activity.finish()
            }
            return
        }

        Glide.with(getAppContext()).asDrawable().load(GlideUtil.url(videoInfo!!.cover)).placeholder(R.mipmap.placeholder)
            .transition(GlideUtil.getTransitionOptions())
            .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(4f))).sizeMultiplier(0.85f).skipMemoryCache(true).dontAnimate())
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(cover)

        if (SharedPreferencesUtil.getBoolean("tags_enable", true)) {
            CenterThreadPool.run {
                try {
                    val tagsSpannable = getDescSpan(VideoInfoApi.getTags(videoInfo!!.aid))

                    runOnUiThread {
                        tagsText.movementMethod = LinkMovementMethod.getInstance()
                        tagsText.text = tagsSpannable.toString()
                        tagsText.setOnClickListener {
                            tagsExpand = !tagsExpand
                            if (tagsExpand) {
                                tagsText.maxLines = 233
                                tagsText.text = tagsSpannable
                            } else {
                                tagsText.maxLines = 1
                                tagsText.text = tagsSpannable.toString()
                            }
                        }
                    }
                } catch (e: Exception) {
                    MsgUtil.err(e)
                }
            }
        } else tagsText.visibility = View.GONE

        if (videoInfo!!.stats.coined != 0)
            coin.setImageResource(R.drawable.icon_coin_1)
        if (videoInfo!!.stats.liked)
            like.setImageResource(R.drawable.icon_like_1)
        if (videoInfo!!.stats.favoured)
            fav.setImageResource(R.drawable.icon_fav_1)

        CenterThreadPool.run {
            try {
                playerData = videoInfo!!.toPlayerData(0)
                PlayerApi.getVideo(playerData!!, false)
                if (playerData == null) return@run
                HistoryApi.reportHistory(videoInfo!!.aid, playerData!!.cidHistory, (playerData!!.progress / 1000).toLong())
            } catch (e: Exception) {
                MsgUtil.err(e)
            }
            onFinishLoad()
        }

        cover.requestFocus()
        cover.setOnClickListener {
            if (SharedPreferencesUtil.getBoolean("cover_play_enable", true)) playClick()
            else showCover()
        }
        cover.setOnLongClickListener {
            showCover()
            true
        }

        title.text = getTitleSpan()
        StringUtil.setCopy(title, videoInfo!!.title)

        if (videoInfo!!.argueMsg.isNotEmpty()) {
            exclusiveTipLabel.text = videoInfo!!.argueMsg
            exclusiveTip.visibility = View.VISIBLE
        }

        val adapter = UpListAdapter(requireContext(), videoInfo!!.staff)
        upRecyclerView.setHasFixedSize(true)
        upRecyclerView.layoutManager = CustomLinearManager(requireContext())
        upRecyclerView.adapter = adapter

        viewCount.text = StringUtil.toWan(videoInfo!!.stats.view.toLong())
        likeLabel.text = StringUtil.toWan(videoInfo!!.stats.like.toLong())
        coinLabel.text = StringUtil.toWan(videoInfo!!.stats.coin.toLong())
        favLabel.text = StringUtil.toWan(videoInfo!!.stats.favorite.toLong())

        danmakuCount.text = videoInfo!!.stats.danmaku.toString()
        bvidText.text = videoInfo!!.bvid
        timeText.text = videoInfo!!.timeDesc
        durationText.text = videoInfo!!.duration

        description.text = videoInfo!!.description
        description.setOnClickListener {
            if (descExpand) description.maxLines = 3
            else description.maxLines = 512
            descExpand = !descExpand
        }
        StringUtil.setLink(description)
        StringUtil.setAtLink(videoInfo!!.descAts, description)
        StringUtil.setCopy(description)

        bvidText.setOnLongClickListener {
            val ctx = context
            if (ctx == null) {
                return@setOnLongClickListener true
            }
            StringUtil.copyText(ctx, videoInfo!!.bvid)
            MsgUtil.showMsg("BV号已复制")
            true
        }

        play.setOnClickListener { playClick() }
        play.setOnLongClickListener {
            val ctx = context
            if (ctx != null)
                startActivity(Intent(ctx, SettingPlayerChooseActivity::class.java))
            true
        }

        rootview.findViewById<View>(R.id.layout_like).setOnClickListener {
            CenterThreadPool.run {
                if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                    MsgUtil.showMsg("还没有登录喵~")
                    return@run
                }
                try {
                    val result = LikeCoinFavApi.like(videoInfo!!.aid, if (videoInfo!!.stats.liked) 2 else 1)
                    if (result == 0) {
                        videoInfo!!.stats.liked = !videoInfo!!.stats.liked
                        runOnUiThread {
                            MsgUtil.showMsg(if (videoInfo!!.stats.liked) "点赞成功" else "取消成功")

                            if (videoInfo!!.stats.liked)
                                likeLabel.text = StringUtil.toWan((++videoInfo!!.stats.like).toLong())
                            else likeLabel.text = StringUtil.toWan((--videoInfo!!.stats.like).toLong())
                            like.setImageResource(if (videoInfo!!.stats.liked) R.drawable.icon_like_1 else R.drawable.icon_like_0)
                        }
                    } else if (isAdded) {
                        var msg = "操作失败：" + result
                        when (result) {
                            -403 -> msg = "当前请求触发B站风控"
                            65006 -> {
                                msg = "已经点赞过了喵~"
                                videoInfo!!.stats.liked = true
                                runOnUiThread { like.setImageResource(R.drawable.icon_like_1) }
                            }
                        }
                        MsgUtil.showMsg(msg)
                    }
                } catch (e: Exception) {
                    MsgUtil.err(e)
                }
            }
        }

        rootview.findViewById<View>(R.id.layout_coin).setOnClickListener {
            CenterThreadPool.run {
                if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                    MsgUtil.showMsg("还没有登录喵~")
                    return@run
                }
                if (videoInfo!!.stats.coined < videoInfo!!.stats.coin_limit) {
                    try {
                        val result = LikeCoinFavApi.coin(videoInfo!!.aid, 1)
                        if (result == 0) {
                            if (++coinAdd <= 2) videoInfo!!.stats.coined++
                            runOnUiThread {
                                MsgUtil.showMsg("投币成功")
                                coinLabel.text = StringUtil.toWan((++videoInfo!!.stats.coin).toLong())
                                coin.setImageResource(R.drawable.icon_coin_1)
                            }
                        } else if (isAdded) {
                            var msg = "投币失败：" + result
                            if (result == -403) {
                                msg = "当前请求触发B站风控"
                            } else if (result == 34002) {
                                msg = "不能给自己投币哦"
                            }
                            MsgUtil.showMsg(msg)
                        }
                    } catch (e: Exception) {
                        MsgUtil.err(e)
                    }
                } else {
                    MsgUtil.showMsg("投币数量到达上限")
                }
            }
        }

        rootview.findViewById<View>(R.id.layout_fav).setOnClickListener {
            val intent = Intent()
            intent.setClass(requireContext(), AddFavoriteActivity::class.java)
            intent.putExtra("aid", videoInfo!!.aid)
            intent.putExtra("bvid", videoInfo!!.bvid)
            favLauncher.launch(intent)
        }

        addWatchlater.setOnClickListener {
            CenterThreadPool.run {
                try {
                    val result = WatchLaterApi.add(videoInfo!!.aid)
                    if (result == 0) MsgUtil.showMsg("添加成功")
                    else MsgUtil.showMsg("添加失败，错误码：" + result)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        addWatchlater.setOnLongClickListener {
            val intent = Intent()
            intent.setClass(requireContext(), WatchLaterActivity::class.java)
            startActivity(intent)
            true
        }

        download.setOnClickListener { downloadClick() }
        download.setOnLongClickListener {
            CenterThreadPool.run {
                val downPath = FileUtil.getVideoDownloadPath(videoInfo!!.title, null)
                FileUtil.deleteFolder(downPath)
                MsgUtil.showMsg("已清除此视频的缓存文件夹")
            }
            true
        }

        relay.setOnClickListener {
            val intent = Intent()
            intent.setClass(requireContext(), SendDynamicActivity::class.java)
            writeDynamicLauncher.launch(intent)
        }
        relay.setOnLongClickListener {
            StringUtil.copyText(requireContext(), "https://www.bilibili.com/" + videoInfo!!.bvid)
            MsgUtil.showMsg("视频完整链接已复制")
            true
        }

        videoSummary.setOnClickListener {
            CenterThreadPool.run {
                try {
                    val cid = if (videoInfo!!.cids != null && videoInfo!!.cids!!.isNotEmpty()) videoInfo!!.cids!![0] else 0L
                    val upMid = if (videoInfo!!.staff != null && videoInfo!!.staff!!.isNotEmpty()) videoInfo!!.staff!![0].mid else 0L
                    val conclusion = VideoInfoApi.getVideoConclusion(videoInfo!!.aid, videoInfo!!.bvid, cid, upMid)
                    if (conclusion != null) {
                        MsgUtil.showText("视频摘要 - " + videoInfo!!.title, conclusion)
                    } else {
                        MsgUtil.showMsg("获取视频摘要失败")
                    }
                } catch (e: Exception) {
                    MsgUtil.err(e)
                }
            }
        }

        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
            addWatchlater.visibility = View.GONE
            relay.visibility = View.GONE
            videoSummary.visibility = View.GONE
        }

        if (videoInfo!!.collection != null) {
            val collectionTitle = rootview.findViewById<TextView>(R.id.collectionText)
            collectionTitle.text = String.format("合集 · %s", videoInfo!!.collection!!.title)
            collectionCard.setOnClickListener {
                startActivity(Intent(requireContext(), CollectionInfoActivity::class.java)
                    .putExtra("fromVideo", videoInfo!!.aid))
            }
        } else {
            collectionCard.visibility = View.GONE
        }

        rootview.findViewById<View>(R.id.layout_like).setOnLongClickListener {
            if (SharedPreferencesUtil.getBoolean("like_one_triple", true) &&
                SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0L) {

                if (shakeAnimation == null) {
                    shakeAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.shake)
                    shakeAnimation!!.repeatCount = Animation.INFINITE
                }

                like.startAnimation(shakeAnimation)
                coin.startAnimation(shakeAnimation)
                fav.startAnimation(shakeAnimation)

                isTripleInProgress = true
                tripleActionRunnable = Runnable {
                    like.clearAnimation()
                    coin.clearAnimation()
                    fav.clearAnimation()

                    CenterThreadPool.run {
                        try {
                            val code = LikeCoinFavApi.triple(aid)
                            if (code == 0) {
                                coin.setImageResource(R.drawable.icon_coin_1)
                                like.setImageResource(R.drawable.icon_like_1)
                                fav.setImageResource(R.drawable.icon_fav_1)
                                MsgUtil.showMsg("三连成功")
                            } else MsgUtil.showMsg("三连失败，错误码：" + code)
                        } catch (e: Exception) {
                            MsgUtil.err("三连失败", e)
                        }
                    }
                }
                like.postDelayed(tripleActionRunnable, 2000)

                return@setOnLongClickListener true
            }
            return@setOnLongClickListener false
        }

        rootview.findViewById<View>(R.id.layout_like).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP ||
                event.action == MotionEvent.ACTION_CANCEL) {
                cancelTripleAction()
            }
            false
        }
    }

    private fun cancelTripleAction() {
        if (isTripleInProgress) {
            isTripleInProgress = false
            if (tripleActionRunnable != null) {
                like.removeCallbacks(tripleActionRunnable!!)
            }
            like.clearAnimation()
            coin.clearAnimation()
            fav.clearAnimation()
        }
    }

    private fun getTitleSpan(): SpannableString {
        var string: String? = null

        if (videoInfo!!.upowerExclusive) string = "充电专属"
        else if (videoInfo!!.isSteinGate) string = "互动视频"
        else if (videoInfo!!.is360) string = "全景视频"
        else if (videoInfo!!.isCooperation) string = "联合投稿"

        if (string == null) return SpannableString(videoInfo!!.title)

        val titleStr = SpannableString(" " + string + " " + videoInfo!!.title)
        val badgeBG = RadiusBackgroundSpan(0, resources.getDimension(R.dimen.card_round).toInt(), Color.WHITE, Color.rgb(207, 75, 95))
        titleStr.setSpan(badgeBG, 0, string.length + 2, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        return titleStr
    }

    private fun getDescSpan(tags: String): SpannableStringBuilder {
        val tagStr = SpannableStringBuilder("标签：")
        for (str in tags.split("/").dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val oldLen = tagStr.length
            tagStr.append(str).append("/")
            tagStr.setSpan(object : ClickableSpan() {
                override fun onClick(arg0: View) {
                    val intent = Intent(requireContext(), SearchActivity::class.java)
                    intent.putExtra("keyword", str)
                    requireContext().startActivity(intent)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                    ds.color = ThemeUtils.getInfoColor()
                }
            }, oldLen, tagStr.length - 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        return tagStr
    }

    private fun playClick() {
        if (SharedPreferencesUtil.getBoolean("first_play", true)) {
            SharedPreferencesUtil.putBoolean("first_play", false)

            if (SharedPreferencesUtil.getBoolean("cover_play_enable", true))
                MsgUtil.showDialog("播放视频", getString(R.string.tutorial_cover_play_enabled))
            else
                MsgUtil.showDialog("播放视频", getString(R.string.tutorial_cover_play_disabled))

            return
        }

        Glide.get(getAppContext()).clearMemory()
        if (videoInfo!!.pagenames.size == 1) PlayerApi.startGettingUrl(playerData!!)
        else
            startActivity(Intent(requireContext(), MultiPageActivity::class.java).putExtra("data", playerData))

        playerData!!.timeStamp = 0
    }

    private fun downloadClick() {
        if (!FileUtil.checkStoragePermission()) {
            FileUtil.requestStoragePermission(requireActivity())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startDownloadFlow()
        }
    }

    private fun startDownloadFlow() {
        val downPath = FileUtil.getVideoDownloadPath(videoInfo!!.title, null)

        if (downPath.exists() && videoInfo!!.pagenames.size == 1) {
            val fileSign = File(downPath, ".DOWNLOADING")
            MsgUtil.showMsg(if (fileSign.exists()) "已在下载队列\n如有异常，长按可清空文件" else "已下载完成")
        } else {
            if (videoInfo!!.pagenames.size > 1) {
                val intent = Intent()
                intent.setClass(requireContext(), MultiPageActivity::class.java)
                    .putExtra("download", 1)
                    .putExtra("data", playerData)
                startActivity(intent)
            } else {
                startActivity(Intent(requireContext(), QualityChooserActivity::class.java)
                    .putExtra("page", 0)
                    .putExtra("aid", videoInfo!!.aid)
                    .putExtra("bvid", videoInfo!!.bvid)
                )
            }
        }
    }

    private fun showCover() {
        try {
            val intent = Intent()
            intent.setClass(requireContext(), ImageViewerActivity::class.java)
            val imageList = ArrayList<String>()
            imageList.add(videoInfo!!.cover)
            intent.putExtra("imageList", imageList)
            requireContext().startActivity(intent)
        } catch (ignored: Exception) {
        }
    }

    fun onFinishLoad() {
        try {
            val activity = activity
            if (activity is VideoInfoActivity) {
                activity.crossFade(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        Logu.d("onDestroy")
        cancelTripleAction()
        super.onDestroy()
    }
}