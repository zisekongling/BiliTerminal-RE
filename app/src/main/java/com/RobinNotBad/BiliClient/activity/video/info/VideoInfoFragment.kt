package com.RobinNotBad.BiliClient.activity.video.info

import com.RobinNotBad.BiliClient.ui.appearance.ThemeTint
import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
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
import com.RobinNotBad.BiliClient.activity.message.ChooseFriendActivity
import com.RobinNotBad.BiliClient.activity.note.NoteActivity
import com.RobinNotBad.BiliClient.activity.search.SearchActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingPlayerChooseActivity
import com.RobinNotBad.BiliClient.activity.user.WatchLaterActivity
import com.RobinNotBad.BiliClient.activity.video.MultiPageActivity
import com.RobinNotBad.BiliClient.activity.video.MultiPageResumePolicy
import com.RobinNotBad.BiliClient.activity.video.QualityChooserActivity
import com.RobinNotBad.BiliClient.activity.video.collection.CollectionInfoActivity
import com.RobinNotBad.BiliClient.adapter.user.UpListAdapter
import com.RobinNotBad.BiliClient.api.ApiErrorText
import com.RobinNotBad.BiliClient.api.BangumiApi
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.api.EmoteApi
import com.RobinNotBad.BiliClient.api.HistoryApi
import com.RobinNotBad.BiliClient.api.LikeCoinFavApi
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.api.SponsorBlockApi
import com.RobinNotBad.BiliClient.api.VideoInfoApi
import com.RobinNotBad.BiliClient.api.WatchLaterApi
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.PrivateMessage
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.player.CollectionResumeStore
import com.RobinNotBad.BiliClient.player.SponsorSkipPolicy
import com.RobinNotBad.BiliClient.player.SponsorSkipPrefs
import com.RobinNotBad.BiliClient.ui.widget.RadiusBackgroundSpan
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.DetailEntryPrefs
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.OptionalFeatures
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import java.io.File

class VideoInfoFragment : BaseFragment() {

    private val RESULT_ADDED = 1
    private val RESULT_DELETED = -1

    private val favLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { o ->
        val code = o.resultCode
        if (code == RESULT_ADDED) {
            ThemeTint.setActiveIcon(fav, R.drawable.icon_fav_1)
        } else if (code == RESULT_DELETED) {
            ThemeTint.setInactiveIcon(fav, R.drawable.icon_fav_0)
        }
    }

    private val writeDynamicLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.resultCode
        val data = result.data
        if (code == Activity.RESULT_OK && data != null) {
            val text = data.getStringExtra("text") ?: ""
            val picsJson = data.getStringExtra("pics")
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
                    val emoteTexts = EmoteApi.getEmoteTexts(EmoteApi.BUSINESS_DYNAMIC)

                    // 转发界面本该隐藏"添加图片"（normalPublish=false），pics 正常不会回来。
                    // 万一回来了也不能静默丢掉——relayVideo 没有 pics 位，直接提示用户。
                    if (!picsJson.isNullOrEmpty()) {
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg(UiText.of("视频转发不支持带图，请去掉图片后重试")) }
                        return@run
                    }

                    // 带上原作者，服务端才能在动态里渲染出 "//@UP主:标题"
                    val author = videoInfo?.staff?.firstOrNull()
                    val dynId = DynamicApi.relayVideo(
                        text,
                        if (atUids.isEmpty()) null else atUids,
                        videoInfo!!.aid,
                        author?.name,
                        author?.mid ?: 0L,
                        videoInfo!!.title,
                        emoteTexts
                    )

                    if (dynId != -1L) MsgUtil.showMsg(UiText.of("转发成功~"))
                    else MsgUtil.showMsg(UiText.of("转发失败"))
                } catch (e: Exception) {
                    MsgUtil.err(e)
                }
            }
        }
    }

    /**
     * 选好友页的回传（反馈 #133）：拿到 uid 就把视频当文本私信发出去。
     *
     * 只认 RESULT_OK + data，用户直接返回就当没选，不弹任何提示。
     */
    private val chooseFriendLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val uid = data.getLongExtra(ChooseFriendActivity.EXTRA_UID, 0L)
        if (uid <= 0L) return@registerForActivityResult
        // 昵称只用于「已发送给 xxx」，拿不到时留空也不影响发送
        sendVideoToFriend(uid, data.getStringExtra(ChooseFriendActivity.EXTRA_NAME).orEmpty())
    }

    /**
     * 转发到动态：让发送页知道这是一次「转发视频」——显示视频预览卡、隐藏「添加图片」入口，
     * 否则 normalPublish 会判定为普通动态，用户选的图会被静默丢弃。
     * SendDynamicActivity.onDestroy 会把该上下文清回 null。
     */
    private fun relayToDynamic() {
        val info = videoInfo ?: return
        TerminalContext.getInstance().setForwardContent(info)
        val intent = Intent()
        intent.setClass(requireContext(), SendDynamicActivity::class.java)
        writeDynamicLauncher.launch(intent)
    }

    /** 转发到好友：先选人，选完由 [chooseFriendLauncher] 回传 uid 再发。 */
    private fun relayToFriend() {
        val intent = Intent()
        intent.setClass(requireContext(), ChooseFriendActivity::class.java)
        chooseFriendLauncher.launch(intent)
    }

    /**
     * 把视频以「标题 + 链接」文本私信发给好友（反馈 #133）。
     *
     * 发文本而不是分享卡片：接口只接受 `msg_type` 1/2/5，卡片是 7（会被回 21035），
     * 见 [PrivateMsgApi.buildVideoShareText]。失败不静默——能拼出链接就先复制，
     * 用户至少还有一条能把视频发出去的路。
     */
    private fun sendVideoToFriend(uid: Long, name: String) {
        val info = videoInfo ?: return
        CenterThreadPool.run {
            try {
                val result = PrivateMsgApi.sendMsg(
                    SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 114514),
                    uid,
                    PrivateMessage.TYPE_TEXT,
                    System.currentTimeMillis() / 1000,
                    PrivateMsgApi.buildTextContent(
                        PrivateMsgApi.buildVideoShareText(info.title, info.bvid)
                    )
                )
                CenterThreadPool.runOnUiThread {
                    if (result.optInt("code", -1) == 0) {
                        MsgUtil.showMsg(UiText.of(getString(R.string.msg_video_relay_to_friend_success, name)))
                    } else {
                        copyVideoLinkAndHint(info.bvid)
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { copyVideoLinkAndHint(info.bvid) }
                MsgUtil.err(e)
            }
        }
    }

    /** 发送失败的回退：复制视频链接 + 提示（链接拼不出来就只提示）。 */
    private fun copyVideoLinkAndHint(bvid: String?) {
        val link = PrivateMsgApi.buildVideoLink(bvid)
        if (link.isNotEmpty()) StringUtil.copyText(requireContext(), link)
        MsgUtil.showMsg(UiText.of(getString(R.string.msg_video_relay_to_friend_failed_copied)))
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 下载已在 downloadClick() 里无条件开始，这里只补一句非阻断提示：
        // 未授权时进度通知不可见，但下载与前台服务照常跑，不再回头阻断下载。
        if (!isGranted) {
            MsgUtil.showMsg(UiText.of("已开始下载；未授予通知权限，本次进度通知不可见"))
        }
    }

    private var videoInfo: VideoInfo? = null

    /**
     * 当前视频的播放数据（aid / cid / 清晰度 / 续播进度等）。
     *
     * 它在 [initView] 里由 [CenterThreadPool] 的**后台线程**赋值，而 [playClick] /
     * [startDownloadFlow] 在**主线程**读取。没有 @Volatile 时两者之间没有 happens-before 边，
     * 主线程可能读到非 null 但字段尚未写完的半成品对象（点播放拿到上一个视频 / 空 url）。
     * 写方是"一次性发布完整对象"、读方只读不写，所以 @Volatile 足够，无需加锁。
     */
    @Volatile
    private var playerData: PlayerData? = null

    /**
     * 取流预取失败的具体原因（反馈 #80）。
     *
     * 详情页的预取只是「顺带填好 playerData」，失败不该用报错弹窗打断阅读；
     * 真正点播放时再把这句原因提示出来。
     */
    private var playUrlError: String? = null
    private var aid: Long = 0
    private var bvid: String? = null
    private lateinit var description: TextView
    private lateinit var tagsText: TextView
    private lateinit var like: ImageView
    private lateinit var coin: ImageView
    private lateinit var fav: ImageView
    /**
     * 标题前那个「广告」胶囊的文字；null 表示这条视频没有广告标注（或还没查出来）。
     *
     * 详情页的标注是**后到**的（要额外发一次 SponsorBlock 请求），所以不能在
     * `getTitleSpan()` 里现算，只能先渲染原样标题、拿到结果再重渲染一次。
     */
    private var sponsorBadge: String? = null

    /** 标题 TextView 的字段引用：`getTitleSpan()` 的第一次渲染在 `initView` 里，之后胶囊到货还要再渲染一次。 */
    private var titleView: TextView? = null

    private var coinAdd = 0
    private var descExpand = false
    private var tagsExpand = false
    private var shakeAnimation: Animation? = null
    private var tripleActionRunnable: Runnable? = null
    private var isTripleInProgress = false

    // 点赞/投币/三连的"请求进行中"标志。这三个接口都不是幂等的（重复投币会重复扣币、
    // 重复点赞会撞 65006 把本地状态位改错），而按钮是可以被连点的，所以点击路径上必须互斥。
    // 标志由主线程（点击）写、由子线程（请求结束的 finally）写、由主线程读，
    // 跨线程可见性靠 @Volatile 保证；请求一结束就复位，用户不需要为下一次操作等待。
    @Volatile
    private var isLikeRequesting = false

    @Volatile
    private var isCoinRequesting = false

    @Volatile
    private var isTripleRequesting = false

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

        /**
         * 进详情页时，要不要把这条观看记录上报给服务端。
         *
         * 详情页本身就是一次"打开观看"，必须登记，否则从没看过的视频永远进不了观看记录——
         * 用户实测过：点进详情页历史里什么都不多，只有真的播了才出现一条。
         *
         * 判据只有一条：**这次上报的目标是不是正在打开的这一个分P**。被 26.10.02 的
         * `if (progress > 0)` 挡掉登记，就是因为拿"有没有进度"当判据，而"没有进度"
         * 恰好分两种完全相反的语义：
         *
         * - cidHistory == cid：这个稿件服务端没有记录（getVideo 把 `last_play_cid == 0`
         *   归一化成 `cidHistory = cid`、`progress = 0`），报 0 只是登记，没有进度可覆盖；
         *   这个分P本来就有进度时同理，原样回写，幂等无害。
         * - cidHistory != cid：进度属于**别的分P**（多P视频：上次看第 3P，详情页预取第 1P），
         *   报 0 会把第 3P 的续播抹成 0，用户下次打开从头看。必须跳过。
         *
         * 抽成纯函数是为了能被 JVM 单测盯住：真机上很难复现"多P视频续播被清零"。
         */
        @JvmStatic
        fun shouldReportHistoryOnEnter(cidHistory: Long, cid: Long): Boolean =
            cid != 0L && cidHistory == cid
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bundle = arguments
        if (bundle == null) {
            MsgUtil.showMsg(UiText.of("视频详情页：数据为空"))
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
        titleView = title
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
        val note = rootview.findViewById<MaterialButton>(R.id.note)
        val bvidText = rootview.findViewById<TextView>(R.id.bvidText)
        val danmakuCount = rootview.findViewById<TextView>(R.id.danmakuCount)
        like = rootview.findViewById(R.id.btn_like)
        coin = rootview.findViewById(R.id.btn_coin)
        fav = rootview.findViewById(R.id.btn_fav)
        val likeLabel = rootview.findViewById<TextView>(R.id.like_label)
        val coinLabel = rootview.findViewById<TextView>(R.id.coin_label)
        val favLabel = rootview.findViewById<TextView>(R.id.fav_label)
        val collectionCard = rootview.findViewById<MaterialCardView>(R.id.collection)
        val likeCoinFavCard = rootview.findViewById<MaterialCardView>(R.id.like_coin_fav)
        // 反馈 #112：动作区卡片外面那圈 1dp 描边取 ?attr/actionCardStroke，B站粉与经典终端主题下
        // 都是 #FF6699，有用户觉得扎眼。开关关掉时把描边宽度归零——底色与文字色都不动。
        // 描边是 XML 属性，只能在运行时改，所以这里必须有引用。
        if (!DetailEntryPrefs.isLikeCardRingEnabled()) likeCoinFavCard.strokeWidth = 0

        // 点赞/投币/收藏卡片改为「surface 底 + 主色 1dp 描边」：
        // 原布局用 ?attr/colorPrimary 亮粉打底，标签文字走主题近白色，对比度约 2.1:1（低于 AA 4.5:1）；
        // 此前只在经典终端主题下用代码改回暗底打补丁，其余 6 套主题仍然看不清，这里统一到布局里。

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

        Glide.with(getAppContext()).asDrawable().load(GlideUtil.url_hq(videoInfo!!.cover)).placeholder(R.mipmap.placeholder)
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
            ThemeTint.setActiveIcon(coin, R.drawable.icon_coin_1)
        if (videoInfo!!.stats.liked)
            ThemeTint.setActiveIcon(like, R.drawable.icon_like_1)
        if (videoInfo!!.stats.favoured)
            ThemeTint.setActiveIcon(fav, R.drawable.icon_fav_1)

        CenterThreadPool.run {
            try {
                // 用局部变量完成"装配"，最后再整体发布给 playerData 字段：
                // 这样主线程读到的 playerData 要么是 null（还显示"加载中"），要么是 aid/cid
                // 都已经填好、PlayerApi.getVideo 也已经补全 url/qn/续播进度的完整对象，
                // 不会出现"读到上一个视频的数据"。原来的 `playerData = ...; playerData!!; if (playerData == null)`
                // 是自相矛盾的死代码，这里一并去掉。
                val data = videoInfo!!.toPlayerData(0)
                PlayerApi.getVideo(data, false)
                // 进详情页要"登记"一条观看记录，否则从没看过的视频永远进不了观看记录
                // （表现就是"点进详情页历史里什么都没有，只有真的播了才出现一条"）。
                // 但不能无条件上报：cidHistory 指向**别的分P**时，报 0 会把那一P的续播抹掉。
                // 判据见 shouldReportHistoryOnEnter。
                if (shouldReportHistoryOnEnter(data.cidHistory, data.cid))
                    HistoryApi.reportHistory(videoInfo!!.aid, data.cid, (data.progress / 1000).toLong())
                playUrlError = null
                playerData = data
            } catch (e: PlayerApi.NoPlayUrlException) {
                // 反馈 #80：付费/充电专属、地区限制、需登录这类稿件本来就取不到播放地址。
                // 预取失败只记日志，不弹 "No value for durl" 那种库内部报错；
                // 用户点播放时 playClick 会把同一句原因再提示一次。
                playUrlError = e.message
                Logu.e("VideoInfo", "预取播放地址失败：" + e.message)
            } catch (e: Exception) {
                MsgUtil.err(e)
            }
            // 注意：这里**不**收起加载遮罩。取流预取是弱网下最慢的一步，而 ViewPager 会
            // 把评论页一并建好（offscreenPageLimit = size），若等它才收起遮罩，用户切到评论
            // 就会看到"评论已出、加载图还盖在上面且挡触摸"。遮罩由 initView 末尾统一收起。
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
        loadSponsorBadge()

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
            MsgUtil.showMsg(UiText.of("BV号已复制"))
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
            // 请求进行中时忽略重复点击：点赞接口不幂等，弱网下用户连点会在上一次返回前发出多个请求，
            // 后来的 "65006 已经点赞过了" 分支还会把本地状态位改掉，导致图标与实际状态不一致。
            // 复位必须放在 finally，否则任何一条异常路径都会让按钮永久失效。
            if (isLikeRequesting) {
                MsgUtil.showMsg(UiText.of("正在处理中…"))
                return@setOnClickListener
            }
            isLikeRequesting = true
            CenterThreadPool.run {
                try {
                    if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                        MsgUtil.showMsg(UiText.of("还没有登录喵~"))
                        return@run
                    }
                    val result = LikeCoinFavApi.like(videoInfo!!.aid, if (videoInfo!!.stats.liked) 2 else 1)
                    if (result == 0) {
                        videoInfo!!.stats.liked = !videoInfo!!.stats.liked
                        runOnUiThread {
                            MsgUtil.showMsg(if (videoInfo!!.stats.liked) UiText.of("点赞成功") else UiText.of("取消成功"))

                            if (videoInfo!!.stats.liked)
                                likeLabel.text = StringUtil.toWan((++videoInfo!!.stats.like).toLong())
                            else likeLabel.text = StringUtil.toWan((--videoInfo!!.stats.like).toLong())
                            ThemeTint.setToggleIcon(like, videoInfo!!.stats.liked, R.drawable.icon_like_1, R.drawable.icon_like_0)
                        }
                    } else if (isAdded) {
                        var msg = ApiErrorText.opFailedWithDetail(result)
                        when (result) {
                            -403 -> msg = UiText.of("当前请求触发B站风控")
                            65006 -> {
                                msg = UiText.of("已经点赞过了喵~")
                                videoInfo!!.stats.liked = true
                                runOnUiThread { ThemeTint.setActiveIcon(like, R.drawable.icon_like_1) }
                            }
                        }
                        MsgUtil.showMsg(msg)
                    }
                } catch (e: Exception) {
                    MsgUtil.err(e)
                } finally {
                    isLikeRequesting = false
                }
            }
        }

        rootview.findViewById<View>(R.id.layout_coin).setOnClickListener {
            // 同点赞：投币接口不幂等，连点会真的重复扣币，必须做进行中互斥（复位同样放 finally）。
            if (isCoinRequesting) {
                MsgUtil.showMsg(UiText.of("正在处理中…"))
                return@setOnClickListener
            }
            isCoinRequesting = true
            CenterThreadPool.run {
                try {
                    if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                        MsgUtil.showMsg(UiText.of("还没有登录喵~"))
                        return@run
                    }
                    if (videoInfo!!.stats.coined < videoInfo!!.stats.coin_limit) {
                        val result = LikeCoinFavApi.coin(videoInfo!!.aid, 1)
                        if (result == 0) {
                            if (++coinAdd <= 2) videoInfo!!.stats.coined++
                            runOnUiThread {
                                MsgUtil.showMsg(UiText.of("投币成功"))
                                coinLabel.text = StringUtil.toWan((++videoInfo!!.stats.coin).toLong())
                                ThemeTint.setActiveIcon(coin, R.drawable.icon_coin_1)
                            }
                        } else if (isAdded) {
                            var msg = UiText.of("投币失败：") + result
                            if (result == -403) {
                                msg = UiText.of("当前请求触发B站风控")
                            } else if (result == 34002) {
                                msg = UiText.of("不能给自己投币哦")
                            }
                            MsgUtil.showMsg(msg)
                        }
                    } else {
                        MsgUtil.showMsg(UiText.of("投币数量到达上限"))
                    }
                } catch (e: Exception) {
                    MsgUtil.err(e)
                } finally {
                    isCoinRequesting = false
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
                    if (result == 0) MsgUtil.showMsg(UiText.of("添加成功"))
                    else MsgUtil.showMsg(UiText.fmt(UiText.of("添加失败，错误码：%1\$s"), result))
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
                MsgUtil.showMsg(UiText.of("已清除此视频的缓存文件夹"))
            }
            true
        }

        relay.setOnClickListener {
            // 反馈 #133：转发有两个去处，先问一句再办事（原先点一下直接进动态发送页）
            TerminalDialog.menu(
                requireContext(),
                title = getString(R.string.text_video_relay_title),
                items = listOf(
                    getString(R.string.text_video_relay_to_dynamic),
                    getString(R.string.text_video_relay_to_friend)
                )
            ) { index ->
                if (index == 0) relayToDynamic() else relayToFriend()
            }
        }
        relay.setOnLongClickListener {
            StringUtil.copyText(requireContext(), "https://www.bilibili.com/" + videoInfo!!.bvid)
            MsgUtil.showMsg(UiText.of("视频完整链接已复制"))
            true
        }

        videoSummary.setOnClickListener {
            CenterThreadPool.run {
                try {
                    val cid = if (videoInfo!!.cids != null && videoInfo!!.cids!!.isNotEmpty()) videoInfo!!.cids!![0] else 0L
                    val upMid = if (videoInfo!!.staff != null && videoInfo!!.staff!!.isNotEmpty()) videoInfo!!.staff!![0].mid else 0L
                    val conclusion = VideoInfoApi.getVideoConclusion(videoInfo!!.aid, videoInfo!!.bvid, cid, upMid)
                    if (conclusion != null) {
                        MsgUtil.showText(UiText.of("视频摘要 - ") + videoInfo!!.title, conclusion)
                    } else {
                        MsgUtil.showMsg(UiText.of("获取视频摘要失败"))
                    }
                } catch (e: Exception) {
                    MsgUtil.err(e)
                }
            }
        }

        note.setOnClickListener {
            // 私有笔记要 SESSDATA，未登录时按钮已经隐藏（见下面那段 visibility）
            startActivity(
                Intent(requireContext(), NoteActivity::class.java)
                    .putExtra("aid", videoInfo!!.aid)
            )
        }

        // 可选私有功能的挂点：在动作区插一整行按钮（插在「笔记」之前）。
        // 公共构建里 OptionalFeatures 是空操作，这里连按钮都不会出现；私有构建才插入。
        // 不跟下面的未登录隐藏逻辑走：这个入口不依赖登录态。
        //
        // 26.11.07：做成开关（用户要求藏起来）——只有设置里「分享到其他设备」开关打开时才挂，
        // 否则连反射都不做（公开构建读到的这份配置永远不会被用到）。
        // 26.11.08：默认值翻成开（用户要求改回默认显示），开关仍在，关掉即不显示。
        if (DetailEntryPrefs.isShareRowEnabled()) {
            OptionalFeatures.attachDetailActionRow(
                rootview.findViewById(R.id.video_actions),
                note,
                videoInfo!!.bvid ?: "",
                videoInfo!!.aid,
                videoInfo!!.title ?: ""
            )
        }

        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
            addWatchlater.visibility = View.GONE
            relay.visibility = View.GONE
            videoSummary.visibility = View.GONE
            note.visibility = View.GONE
        } else if (!DetailEntryPrefs.isNoteEntryEnabled()) {
            // 26.11.07：登录态下也默认不显示「笔记」——用户要求把这个入口藏起来，
            // 想用就在「设置 → 内容与浏览 → 详情页设置」里打开（默认关）。
            // 点击监听与 NoteActivity 都还在，只是没有入口。
            note.visibility = View.GONE
        }

        if (videoInfo!!.collection != null) {
            val collectionTitle = rootview.findViewById<TextView>(R.id.collectionText)
            collectionTitle.text = String.format(UiText.of("合集 · %s"), videoInfo!!.collection!!.title)
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

                    // 三连 = 一键点赞 + 投币（都不幂等）。延迟任务在一次长按内只会触发一次，
                    // 但用户能在上一次请求返回前再次长按，所以这里仍要做进行中互斥；
                    // 注意不能复用 isTripleInProgress——它在长按按下时就已置 true，会把请求本身挡掉。
                    if (!isTripleRequesting) {
                        isTripleRequesting = true
                        CenterThreadPool.run {
                            try {
                                val code = LikeCoinFavApi.triple(aid)
                                if (code == 0) {
                                    runOnUiThread {
                                        ThemeTint.setActiveIcon(coin, R.drawable.icon_coin_1)
                                        ThemeTint.setActiveIcon(like, R.drawable.icon_like_1)
                                        ThemeTint.setActiveIcon(fav, R.drawable.icon_fav_1)
                                    }
                                    MsgUtil.showMsg(UiText.of("三连成功"))
                                } else MsgUtil.showMsg(UiText.fmt(UiText.of("三连失败，错误码：%1\$s"), code))
                            } catch (e: Exception) {
                                MsgUtil.err(UiText.of("三连失败"), e)
                            } finally {
                                isTripleRequesting = false
                            }
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

        // 本页内容装配完成即可收起中央加载遮罩——**不等**后台的取流预取（PlayerApi.getVideo）。
        // 弱网下取流可能耗时很久甚至超时重试，而 ViewPager 会把评论页一起创建好，
        // 遮罩是 ViewPager 的兄弟节点、盖在最上层，会把已经加载完的评论挡住并吃掉触摸。
        // 预取只负责填 playerData，没拿到时 playClick / startDownloadFlow 已有
        // "视频信息还在加载中"的兜底，不会崩也不会把上一个视频的数据交出去。
        onFinishLoad()
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

    /**
     * 标题前那个胶囊：**只放一个**。
     *
     * 「广告」优先于「充电专属 / 互动视频 / 全景视频 / 联合投稿」——它回答的是"进去会不会
     * 被喂广告"，比那四个都更影响"点不点播放"这个决定；手表屏一行也塞不下两个胶囊。
     * 文字由 [loadSponsorBadge] 异步填进来，没查到就是 null，走原来的那四个。
     */
    private fun getTitleSpan(): SpannableString {
        var string: String? = sponsorBadge

        if (string == null && videoInfo!!.upowerExclusive) string = UiText.of("充电专属")
        else if (string == null && videoInfo!!.isSteinGate) string = UiText.of("互动视频")
        else if (string == null && videoInfo!!.is360) string = UiText.of("全景视频")
        else if (string == null && videoInfo!!.isCooperation) string = UiText.of("联合投稿")

        if (string == null) return SpannableString(videoInfo!!.title)

        val titleStr = SpannableString(" " + string + " " + videoInfo!!.title)
        val badgeBG = RadiusBackgroundSpan(0, resources.getDimension(R.dimen.card_round).toInt(), Color.WHITE, ColorScheme.PRIMARY)
        titleStr.setSpan(badgeBG, 0, string.length + 2, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        return titleStr
    }

    /**
     * 查一次这条视频的广告标注，给标题前补「广告」/「含广告」胶囊。
     *
     * 只在用户还想要这个功能时才请求（[SponsorSkipPrefs.needsSegments]：三档、条子、三角
     * 任一项开着）。26.11.14 起标记与跳过解耦，所以判据从「开着自动跳过」放宽成「还开着
     * 任何一项」——只显示条子不跳的人，详情页也该看到这颗胶囊。
     *
     * 失败、没标注、上游返回空——一律静默：详情页的首要任务是让人读到简介，不该为一个
     * 装饰性标记弹错。
     *
     * cid 传 0（任意分 P 的标注都算数）、总时长传 0（不校验版本）：这里回答的是"这条视频
     * 有没有广告"，而不是"这个 P 从第几秒跳到第几秒"。
     */
    private fun loadSponsorBadge() {
        val info = videoInfo ?: return
        val bvidOfVideo = info.bvid ?: ""
        if (bvidOfVideo.isEmpty()) return
        if (!SponsorSkipPrefs.needsSegments()) return
        CenterThreadPool.run {
            val badge = try {
                SponsorSkipPolicy.adBadge(SponsorBlockApi.fetchSegments(bvidOfVideo), 0L, 0.0)
            } catch (e: Exception) {
                Logu.w("SponsorBlock", "详情页广告标注查询失败: " + e.message)
                SponsorSkipPolicy.BADGE_NONE
            }
            if (badge == SponsorSkipPolicy.BADGE_NONE) return@run
            CenterThreadPool.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                sponsorBadge = when (badge) {
                    SponsorSkipPolicy.BADGE_FULL -> getString(R.string.player_sb_badge_full)
                    else -> getString(R.string.player_sb_badge_partial)
                }
                titleView?.text = getTitleSpan()
            }
        }
    }

    private fun getDescSpan(tags: String): SpannableStringBuilder {
        val tagStr = SpannableStringBuilder(UiText.of("标签："))
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
                    ds.color = ColorScheme.INFO
                }
            }, oldLen, tagStr.length - 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        return tagStr
    }

    private fun playClick() {
        if (SharedPreferencesUtil.getBoolean("first_play", true)) {
            SharedPreferencesUtil.putBoolean("first_play", false)

            if (SharedPreferencesUtil.getBoolean("cover_play_enable", true))
                MsgUtil.showDialog(UiText.of("播放视频"), getString(R.string.tutorial_cover_play_enabled))
            else
                MsgUtil.showDialog(UiText.of("播放视频"), getString(R.string.tutorial_cover_play_disabled))

            return
        }

        // playerData 由后台线程的历史上报任务赋值，任务未完成时它是 null，原来直接 !! 会 NPE；
        // 再用 aid 校验一次，确保交给播放器的不是"上一个视频"残留的数据。
        val data = playerData
        if (data == null || data.aid != videoInfo!!.aid) {
            // 预取失败过的情形要说出具体原因，而不是笼统的「还在加载中」（反馈 #80）
            MsgUtil.showMsg(UiText.of(playUrlError ?: "视频信息还在加载中"))
            return
        }

        // 26.11.13：删掉原先这一行无条件的 `Glide.get(getAppContext()).clearMemory()`（起播前腾内存）。
        // 清空内存缓存会让 Glide 把「仍被请求占用」的资源也排队回收，线上 02 版（26110902）
        // 「Cannot recycle a resource while it is still acquired」占了崩溃的大头；
        // 26.11.09 之后的 460736b0 已按同样理由删掉另外三处，这是同类里最后两处之一。
        // Glide 内存缓存本身容量有限（按设备档位算，几 MB 量级），腾出来的那点内存不抵这条触发路径的风险。
        // #83：多P 视频「续到上次那个P」——服务端 last_play_cid 指向本稿件的某个分P、
        // 且 last_play_time > 0 时，直接把那一P 交给播放器起播：进度由 PlayerApi 按
        // last_play_cid 采纳（见 adoptLastPlayTime），不再让用户每次都重选一遍。
        // 没有观看记录、或上次看的就是 P1 时仍走原来的分P 选择页——别把"能选P"的入口吃掉。
        // 判据（含"cid 必须属于本稿件"）收敛在 MultiPageResumePolicy。
        val resumeIndex = MultiPageResumePolicy.resumePageIndex(
            videoInfo!!.cids, data.cidHistory, data.lastPlayTimeMs
        )

        // 记下「这个合集上次看的是哪一集」，供合集详情页进入时自动定位（反馈 #105 / #108）。
        // 位置选在这里：首播教程、数据未就绪等早退分支都已经 return 过了，没真起播就不该留记录；
        // 三条起播分支（直起、续到上次那P、进分P 选择页）也都在下面，一次覆盖。
        // seasonId 取 ugc_season.id（= Collection.id），不要拿 PlayerData.seasonId——那是番剧的季。
        videoInfo!!.collection?.let { collection ->
            // 拿不到 ugc_season.id 时 save 会写「最近一次」降级槽，不在这里拦掉
            CollectionResumeStore.save(collection.id.toLong(), videoInfo!!.aid)
            // 把 videoInfo 一起交给播放器：多P 时选集卡片靠它显示「进入合集」那一行，
            // 跳转页也靠它写合集续看记录（只有一P 的一集同样属于合集）。
            data.enterableInfo = videoInfo
        }

        when {
            videoInfo!!.pagenames.size == 1 -> PlayerApi.startGettingUrl(data)
            resumeIndex > 0 -> {
                val resumeData = videoInfo!!.toPlayerData(resumeIndex)
                // 带上"有没有上次记录"的原值：播放器之外还要用它（当前没有消费方，但别丢信息）
                resumeData.lastPlayTimeMs = data.lastPlayTimeMs
                // 与 direct 分支同理：属于合集的这一集要让播放器能「进入合集」
                if (videoInfo!!.collection != null) resumeData.enterableInfo = videoInfo
                // timeStamp 归零：让 JumpToPlayerActivity 真正重拉一次 playurl，
                // 续播位置由服务端按这个分P 现算（缓存十分钟的旧进度不能用）
                resumeData.timeStamp = 0
                PlayerApi.startGettingUrl(resumeData)
                MsgUtil.showMsg(getString(R.string.msg_player_resume_page, resumeIndex + 1))
            }
            else -> startActivity(Intent(requireContext(), MultiPageActivity::class.java).putExtra("data", data))
        }

        data.timeStamp = 0
    }

    private fun downloadClick() {
        // 判据是「目标目录真的能不能写」，不是「有没有 MANAGE_EXTERNAL_STORAGE」：
        // 默认下载目录按 API 分档、都免权限；写不进去时 resolve 会降级到应用专属目录，
        // 只有连降级目录都写不了（存储满/只读）才返回 null 挡下这次下载。
        // 手表 ROM 往往没有「所有文件访问」设置页（issue #42），按授权判会把下载永久挡死。
        if (FileUtil.resolveVideoDownloadDir(requireActivity()) == null) return

        // 通知权限只决定「进度通知看不看得见」，前台服务在未授权时照常运行，
        // 所以它绝不能当下载的前置条件——旧行为是拒绝一次就永远下不了（手表上也找不到恢复入口）。
        // 这里仍请求一次权限，但请求与下载**并行**：不等回调结果，立刻进入下载流程。
        DownloadNotificationGate.requestOnce(requireActivity(), notificationPermissionLauncher)
        startDownloadFlow()
    }

    private fun startDownloadFlow() {
        val downPath = FileUtil.getVideoDownloadPath(videoInfo!!.title, null)

        if (downPath.exists() && videoInfo!!.pagenames.size == 1) {
            val fileSign = File(downPath, ".DOWNLOADING")
            MsgUtil.showMsg(
                if (fileSign.exists()) UiText.of("已在下载队列\n如有异常，长按可清空文件")
                else UiText.of("已下载完成")
            )
        } else {
            if (videoInfo!!.pagenames.size > 1) {
                // 与 playClick 同理：多P下载要把当前分P数据交给 MultiPageActivity，
                // 后台任务未就绪时 playerData 为 null，原来直接塞进去会 NullPointerException。
                val data = playerData
                if (data == null || data.aid != videoInfo!!.aid) {
                    MsgUtil.showMsg(UiText.of("视频信息还在加载中"))
                    return
                }
                val intent = Intent()
                intent.setClass(requireContext(), MultiPageActivity::class.java)
                    .putExtra("download", 1)
                    .putExtra("data", data)
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
            } else {
                // 直接嵌在别的宿主里（例如短视频页右侧的详情页）时没有 VideoInfoActivity 的
                // crossFade 可调：initView 里为配合转场把根布局设成了 GONE，这里必须自己显示，
                // 否则整页只有背景色（26.10.21 真机踩过）。
                view?.apply {
                    alpha = 1f
                    visibility = View.VISIBLE
                }
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