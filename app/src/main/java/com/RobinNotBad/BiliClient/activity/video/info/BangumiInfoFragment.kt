package com.RobinNotBad.BiliClient.activity.video.info

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingPlayerChooseActivity
import com.RobinNotBad.BiliClient.activity.video.JumpToPlayerActivity
import com.RobinNotBad.BiliClient.adapter.video.EpisodeCardAdapter
import com.RobinNotBad.BiliClient.adapter.video.SeasonTabAdapter
import com.RobinNotBad.BiliClient.api.BangumiApi
import com.RobinNotBad.BiliClient.model.Bangumi
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.OptionalFeatures
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class BangumiInfoFragment : Fragment() {
    private var mediaId: Long = 0

    /** 从历史列表进来时要定位的剧集 id（epid）；0 表示不定位，正常从第 1 集开始 */
    private var targetEpid: Long = 0

    /** 上面那一集的续播进度，单位秒（跟历史接口、VideoCard.progress 一致）；0 表示没有 */
    private var targetProgressSec: Int = 0
    private var selectedSection = 0
    private var selectedEpisode = 0
    private var dialog: Dialog? = null
    private var rootView: View? = null
    private var episodeRecyclerView: RecyclerView? = null
    private var seasonTabsRecyclerView: RecyclerView? = null
    private var seasonTabAdapter: SeasonTabAdapter? = null
    private var episodeChoose: TextView? = null
    private var followButton: Button? = null

    /** 追番按钮的当前状态；点击时按「取反」发请求，成功后原地翻转 */
    private var followed = false

    /**
     * 追番状态查询的世代号：每次 setupFollowButton（首屏 + 每次切季）或用户点按钮都 +1，
     * 回到主线程时值变了说明这份响应已经过期，直接丢掉。
     * 否则慢到的旧响应会把用户刚改的状态覆盖回去（切季尤甚：两季的 season_id 不同，状态却写进同一个按钮）。
     */
    private var followFetchToken = 0
    private var bangumi: Bangumi? = null

    /** 当前展示的季；首屏是 info.season_id，页内切季后会变，播放上报必须用它 */
    private var currentSeasonId: Long = 0

    /** 当前展示的季类型（心跳上报的 sub_type）；info.type 只对首屏那一季有效 */
    private var currentSeasonType: Int = 0

    /**
     * 当前展示的季的分区列表。首屏是 `bangumi.sectionList`，切季后整体替换。
     * 所有按下标取剧集的地方都必须读它（而不是 bangumi.sectionList），否则切季后下标会错位。
     */
    private var sections: List<Bangumi.Section> = emptyList()

    /** 切季缓存：season_id → 该季数据（切走再切回不用重新请求） */
    private val seasonCache = HashMap<Long, BangumiApi.SeasonMeta>()

    /** 各季「上次离开时选中的集」记忆：(分区下标, 集下标)，切回来时恢复 */
    private val seasonSelection = HashMap<Long, IntArray>()

    /** 切季请求进行中：期间忽略重复点击，避免两个响应交错把页面切成半更新状态 */
    private var switchingSeason = false

    companion object {
        @JvmStatic
        fun newInstance(mediaId: Long): BangumiInfoFragment = newInstance(mediaId, 0L, 0)

        /**
         * @param mediaId       番剧 media_id
         * @param epid          上次观看的剧集 id，0 表示不定位
         * @param progressSec   上次观看进度，单位秒（历史接口的单位），0 表示没有
         */
        @JvmStatic
        fun newInstance(mediaId: Long, epid: Long, progressSec: Int): BangumiInfoFragment {
            val args = Bundle()
            args.putLong("media_id", mediaId)
            args.putLong("epid", epid)
            args.putInt("progress_sec", progressSec)
            val fragment = BangumiInfoFragment()
            fragment.arguments = args
            return fragment
        }

        /** 状态无从得知时只说"选集"（与远端同口径） */
        private const val STATUS_TEXT_FALLBACK = "选集"

        /**
         * 选集标题行的状态文案（纯函数，便于单测）。
         *
         * 优先级对齐远端：当前季的 new_ep.desc → 详情的 new_ep.desc → 「已完结/连载中，共N话」→「选集」。
         * 季级的 desc 更贴近"这一季更新到哪了"；info 上的 desc 只描述主季，
         * 切到剧场版/OVA 时拿它显示会出现"剧场版显示连载中"这种错。
         */
        fun episodeStatusText(
            seasonDesc: String?,
            infoDesc: String?,
            isFinish: Int,
            mainEpCount: Int
        ): String {
            if (!seasonDesc.isNullOrEmpty()) return seasonDesc
            if (!infoDesc.isNullOrEmpty()) return infoDesc
            if (mainEpCount > 0) {
                return (if (isFinish == 1) "已完结" else "连载中") + "，共" + mainEpCount + "话"
            }
            return STATUS_TEXT_FALLBACK
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val arguments = arguments
        if (arguments != null) {
            mediaId = arguments.getLong("media_id")
            targetEpid = arguments.getLong("epid", 0L)
            targetProgressSec = arguments.getInt("progress_sec", 0)
        }
        rootView = inflater.inflate(R.layout.fragment_media_info, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.visibility = View.GONE
        episodeRecyclerView = rootView!!.findViewById(R.id.rv_episode_list)

        // 兼容宿主把 epid/progress 直接塞进 Activity Intent 的用法（本类自己的 newInstance 走 arguments）
        val hostIntent = requireActivity().intent
        if (hostIntent != null) {
            if (targetEpid <= 0L) targetEpid = hostIntent.getLongExtra("epid", 0L)
            if (targetProgressSec <= 0) targetProgressSec = hostIntent.getIntExtra("progress_sec", 0)
        }

        CenterThreadPool
            .supplyAsyncWithLiveData {
                // 历史记录里的番剧卡片带的是 epid，传进来的 aid 其实是剧集 avid 而不是 media_id。
                // 直接拿 avid 当 media_id 请求，接口同样返回 code=0，但 season_id 是 0、标题为空，
                // 详情页会变成一个空壳，所以这里先用 epid 反查真正的 media_id，查不到再退回原值。
                var id = mediaId
                if (targetEpid > 0L) {
                    // getMdidFromEpid 的返回类型是装箱 Long，这里顺手挡一次 null，
                    // 免得以后有人改了那个方法让这里变成拆箱 NPE
                    val mdId = BangumiApi.getMdidFromEpid(targetEpid)
                    if (mdId != null && mdId > 0L) id = mdId
                }
                BangumiApi.getBangumi(id)
            }
            .observe(viewLifecycleOwner) { result ->
                result.onSuccess { bangumi ->
                    this.bangumi = bangumi
                    initView()
                }.onFailure { error -> MsgUtil.err(UiText.of("番剧详情："), error) }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun initView() {
        val imageMediaCover = rootView!!.findViewById<ImageView>(R.id.image_media_cover)
        val playButton = rootView!!.findViewById<Button>(R.id.btn_play)
        val title = rootView!!.findViewById<TextView>(R.id.text_title)
        val subtitle = rootView!!.findViewById<TextView>(R.id.text_subtitle)
        val areaType = rootView!!.findViewById<TextView>(R.id.text_area_type)
        val rating = rootView!!.findViewById<TextView>(R.id.text_rating)
        val pubTime = rootView!!.findViewById<TextView>(R.id.text_pub_time)
        val stats = rootView!!.findViewById<TextView>(R.id.text_stats)
        val styles = rootView!!.findViewById<TextView>(R.id.text_styles)
        val evaluateHeader = rootView!!.findViewById<View>(R.id.layout_evaluate_header)
        val evaluateArrow = rootView!!.findViewById<ImageView>(R.id.icon_evaluate_arrow)
        val evaluate = rootView!!.findViewById<TextView>(R.id.text_evaluate)
        val staffHeader = rootView!!.findViewById<View>(R.id.layout_staff_header)
        val staffArrow = rootView!!.findViewById<ImageView>(R.id.icon_staff_arrow)
        val staff = rootView!!.findViewById<TextView>(R.id.text_staff)
        val record = rootView!!.findViewById<TextView>(R.id.text_record)
        seasonTabsRecyclerView = rootView!!.findViewById(R.id.rv_season_tabs)
        episodeChoose = rootView!!.findViewById(R.id.episode_choose)
        followButton = rootView!!.findViewById(R.id.btn_follow)
        selectedSection = 0

        rootView!!.visibility = View.GONE

        Glide.with(requireContext())
            .load(GlideUtil.url_hq(bangumi!!.info.cover_horizontal))
            .transition(GlideUtil.getTransitionOptions())
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(R.mipmap.placeholder)
            .error(R.mipmap.placeholder)
            .into(imageMediaCover)
        imageMediaCover.setOnClickListener {
            startActivity(Intent(it.context, ImageViewerActivity::class.java).putExtra("imageList", ArrayList(listOf(bangumi!!.info.cover_horizontal))))
        }
        title.text = bangumi!!.info.title

        if (bangumi!!.info.subtitle != null && bangumi!!.info.subtitle!!.isNotEmpty()) {
            subtitle.text = bangumi!!.info.subtitle
            subtitle.visibility = View.VISIBLE
        } else {
            subtitle.visibility = View.GONE
        }

        val areaTypeText = (if (bangumi!!.info.area_name != null) bangumi!!.info.area_name else "") +
                (if (bangumi!!.info.type_name != null) " | " + bangumi!!.info.type_name else "")
        if (areaTypeText.trim().isNotEmpty()) {
            areaType.text = areaTypeText.trim()
            areaType.visibility = View.VISIBLE
        } else {
            areaType.visibility = View.GONE
        }

        if (bangumi!!.info.score > 0) {
            rating.text = String.format(UiText.of("评分：%.1f (%d人)"), bangumi!!.info.score, bangumi!!.info.count)
            rating.visibility = View.VISIBLE
        } else {
            rating.visibility = View.GONE
        }

        if (bangumi!!.info.publish != null && bangumi!!.info.publish!!.pub_time_show != null && bangumi!!.info.publish!!.pub_time_show!!.isNotEmpty()) {
            val status = if (bangumi!!.info.publish!!.is_finish == 1) UiText.of("已完结") else UiText.of("连载中")
            pubTime.text = bangumi!!.info.publish!!.pub_time_show + " " + status
            pubTime.visibility = View.VISIBLE
        } else {
            pubTime.visibility = View.GONE
        }

        if (bangumi!!.info.stat != null) {
            val statBuilder = StringBuilder()
            if (bangumi!!.info.stat!!.views > 0) {
                statBuilder.append(UiText.of("播放：")).append(formatNumber(bangumi!!.info.stat!!.views))
            }
            if (bangumi!!.info.stat!!.favorites > 0) {
                if (statBuilder.isNotEmpty()) statBuilder.append(" ")
                statBuilder.append(UiText.of("收藏：")).append(formatNumber(bangumi!!.info.stat!!.favorites))
            }
            if (bangumi!!.info.stat!!.series_follow > 0) {
                if (statBuilder.isNotEmpty()) statBuilder.append(" ")
                statBuilder.append(UiText.of("追番：")).append(formatNumber(bangumi!!.info.stat!!.series_follow))
            }
            if (statBuilder.isNotEmpty()) {
                stats.text = statBuilder.toString()
                stats.visibility = View.VISIBLE
            } else {
                stats.visibility = View.GONE
            }
        } else {
            stats.visibility = View.GONE
        }

        if (bangumi!!.info.styles != null && bangumi!!.info.styles!!.isNotEmpty()) {
            val styleText = UiText.of("标签：") + bangumi!!.info.styles!!.joinToString(" ")
            styles.text = styleText
            styles.visibility = View.VISIBLE
        } else {
            styles.visibility = View.GONE
        }

        // Bangumi 评分：B 站没有这套口碑数据，由可选私有功能在页面上用代码补一行（公共布局不能改）。
        // 取不到 / 低于匹配阈值时整行隐藏，不占位、不报错；私有功能不在场时这里是空操作。
        val detailRoot = rootView
        if (detailRoot is ViewGroup) {
            OptionalFeatures.attachBangumiDetails(
                detailRoot,
                bangumi?.info?.title.orEmpty(),
                bangumi?.info?.season_id ?: 0L,
                mediaId
            )
        }

        if (bangumi!!.info.evaluate != null && bangumi!!.info.evaluate!!.trim().isNotEmpty()) {
            evaluate.text = bangumi!!.info.evaluate!!.trim()
            evaluateHeader.visibility = View.VISIBLE
            evaluate.visibility = View.GONE
            evaluateHeader.setOnClickListener {
                val isExpanded = evaluate.visibility == View.VISIBLE
                evaluate.visibility = if (isExpanded) View.GONE else View.VISIBLE
                evaluateArrow.animate().rotation(if (isExpanded) 0f else 180f).setDuration(200).start()
            }
        } else {
            evaluateHeader.visibility = View.GONE
            evaluate.visibility = View.GONE
        }

        if (bangumi!!.info.staff != null && bangumi!!.info.staff!!.trim().isNotEmpty()) {
            staff.text = bangumi!!.info.staff!!.trim()
            staffHeader.visibility = View.VISIBLE
            staff.visibility = View.GONE
            staffHeader.setOnClickListener {
                val isExpanded = staff.visibility == View.VISIBLE
                staff.visibility = if (isExpanded) View.GONE else View.VISIBLE
                staffArrow.animate().rotation(if (isExpanded) 0f else 180f).setDuration(200).start()
            }
        } else {
            staffHeader.visibility = View.GONE
            staff.visibility = View.GONE
        }

        if (bangumi!!.info.record != null && bangumi!!.info.record!!.trim().isNotEmpty()) {
            record.text = UiText.of("备案号：") + bangumi!!.info.record!!.trim()
            record.visibility = View.VISIBLE
        } else {
            record.visibility = View.GONE
        }

        val indexShow = rootView!!.findViewById<TextView>(R.id.indexShow)
        indexShow.text = bangumi!!.info.indexShow

        // 追番按钮要在"有没有剧集"的分支之前接线：还没开播的番（敬请期待）恰恰是最需要点追番的时候
        setupFollowButton()

        // 首屏这一季就是 info 里那一季；把它的数据放进缓存，切走再切回时无需重新请求
        currentSeasonId = bangumi!!.info.season_id
        currentSeasonType = bangumi!!.info.type
        sections = bangumi!!.sectionList ?: emptyList()
        seasonCache[currentSeasonId] = BangumiApi.SeasonMeta().apply {
            sectionList = ArrayList(sections)
            seasonType = currentSeasonType
            statusDesc = bangumi!!.info.newEpDesc ?: ""
        }

        // 首个"有剧集"的季才是可用的起始季：番剧数据里第一项常常是空的 PV/预告季，
        // 直接用它会让选集区空白，而且后面按下标取剧集时必然越界
        val firstSection = firstSectionWithEpisodes()
        if (firstSection < 0) {
            // 还没上线的番剧：没有季可切也没有集可选，选集区只留一行状态文案
            episodeChoose!!.text = UiText.of("敬请期待")
            seasonTabsRecyclerView!!.visibility = View.GONE
            episodeRecyclerView!!.visibility = View.GONE
            playButton.visibility = View.GONE
            val activity = requireActivity()
            if (activity is VideoInfoActivity) {
                activity.replyFragment?.setRefreshing(false)
            }
            // 这个分支是提前 return 的，必须自己收尾：否则宿主 activity_simple_viewpager.xml 的
            // 中央加载遮罩永远不会被收起，"敬请期待"的番剧会一直停在加载图上（还挡触摸）。
            onFinishLoad()
            return
        }

        selectedSection = firstSection
        selectedEpisode = 0
        // 从历史列表进来时带着上次观看的 epid：定位到就直接选中那一集；
        // 定位失败（换季/下架/数据异常）保持第 1 集，不崩也不白屏
        if (locateEpisodeByEpid(targetEpid) && (selectedSection != firstSection || selectedEpisode != 0)) {
            MsgUtil.showMsg(UiText.fmt(UiText.of("已定位到上次观看的「%1\$s」"), currentEpisode()?.title ?: ""))
        }

        setupSeasonTabs()

        episodeChoose!!.setOnClickListener { getEposideChooseDialog().show() }

        val adapter = EpisodeCardAdapter()

        adapter.setOnItemClickListener { index ->
            selectedEpisode = index
            refreshReplies()
        }

        adapter.setData(sections[selectedSection].episodeList)
        episodeRecyclerView!!.layoutManager = CustomLinearManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        episodeRecyclerView!!.adapter = adapter
        // setData 会把高亮重置到第 0 项；定位成功时要把高亮和滚动位置一起挪到目标集
        adapter.selectedItemIndex = selectedEpisode
        episodeRecyclerView!!.scrollToPosition(selectedEpisode)
        updateEpisodeStatusText()

        playButton.setOnClickListener {
            // 用安全取值代替 direct 下标：选中的季为空/下标越界时返回 null 而不是抛 IndexOutOfBoundsException
            val episode = currentEpisode() ?: return@setOnClickListener
            startPlay(episode)
        }
        playButton.setOnLongClickListener {
            val intent = Intent(it.context, SettingPlayerChooseActivity::class.java)
            startActivity(intent)
            true
        }
        onFinishLoad()

        // 这一季存在「会员」集时才需要备用片源：提前让可选私有功能预热解析，
        // 点播放时能直接命中缓存。账号态与是否需要换源的判断都在私有侧，公开侧只报「有会员集」。
        val seasonEpisodes = if (selectedSection in sections.indices) {
            sections[selectedSection].episodeList
        } else {
            null
        }
        if (!seasonEpisodes.isNullOrEmpty() &&
            seasonEpisodes.any { EpisodeCardAdapter.badgeKind(it.badge) == EpisodeCardAdapter.BadgeKind.VIP }
        ) {
            OptionalFeatures.warmUpBangumiSource(bangumi?.info?.title.orEmpty(), true)
        }

        refreshReplies()
    }

    /**
     * 播放按钮的统一入口：先问可选私有功能要不要接管（例如把会员集静默换到备用片源），
     * 不接管时走原来的 B 站播放链路。
     */
    private fun startPlay(episode: Bangumi.Episode) {
        val title = bangumi?.info?.title.orEmpty()
        // 把这一集的真实集名（「第5话」）一起交给私有侧：它既要拿去显示标题，也要按集号
        // 对齐采集站的集列表——只传列表下标会在两边列表长度/顺序不一致时串集（26.11.05）
        if (title.isNotEmpty() &&
            OptionalFeatures.interceptBangumiPlay(
                requireContext(),
                title,
                selectedEpisode,
                episode.badge.orEmpty(),
                episode.title.orEmpty()
            )
        ) {
            return
        }
        playOnBili(episode)
    }

    /** 原 B 站播放链路（保持既有的进度/上报语义不变）。 */
    private fun playOnBili(episode: Bangumi.Episode) {
        Glide.get(requireContext()).clearMemory()
        val intent = Intent(requireContext(), JumpToPlayerActivity::class.java)
        // 必须带上 season_id 与季类型：番剧的观看进度要走心跳接口上报，
        // 只有 epid 而没有 sid/sub_type 时服务端不会把它记成番剧记录。
        // 用 currentSeasonId/currentSeasonType（页内切季后的那一季）而不是 info 上的值，
        // 否则从第二季点播放会上报成第一季的记录
        val data = episode.toPlayerData(currentSeasonId, currentSeasonType)
        // 历史来源的进度单位是秒，PlayerData.progress 要毫秒；
        // 只有当前这一集就是历史里记的那一集时才带，避免把别集的进度套到这一集上
        if (episode.id == targetEpid && targetProgressSec > 0) {
            data.progress = targetProgressSec * 1000
        }
        intent.putExtra("data", data)
        startActivity(intent)
    }

    /**
     * 初始化季 tab：数据源是 `info.seasons`（同系列所有季，与官方客户端的选集 tab 同源）。
     *
     * 个别番剧的 seasons 里不含当前季，这时把当前季补成第一个 tab（标题「正片」）。
     * **只有 tab 数 > 1 才显示整行**（对齐 PiliPlus EpisodePanel 的规则）：
     * 只有一季的番剧给一行 tab 没有任何信息量，还白占手表屏首屏高度。
     */
    private fun setupSeasonTabs() {
        val tabs = ArrayList<Bangumi.Season>()
        bangumi?.info?.seasons?.let { tabs.addAll(it) }

        var currentIndex = tabs.indexOfFirst { it.season_id == currentSeasonId }
        if (currentIndex < 0) {
            val current = Bangumi.Season()
            current.season_id = currentSeasonId
            current.season_title = UiText.of("正片")
            current.seasonType = currentSeasonType
            current.statusDesc = bangumi?.info?.newEpDesc
            tabs.add(0, current)
            currentIndex = 0
        }

        // 当前季的元数据回填到 tab 上：切走再切回时要能看到这一季的状态文案与季类型
        tabs[currentIndex].let { tab ->
            if (tab.seasonType == 0) tab.seasonType = currentSeasonType
            if (tab.statusDesc.isNullOrEmpty()) tab.statusDesc = bangumi?.info?.newEpDesc
        }

        val recyclerView = seasonTabsRecyclerView ?: return
        if (tabs.size <= 1) {
            recyclerView.visibility = View.GONE
            return
        }

        val adapter = SeasonTabAdapter(tabs)
        adapter.selectedIndex = currentIndex
        adapter.setOnTabClickListener { switchSeason(it) }
        recyclerView.layoutManager = CustomLinearManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        seasonTabAdapter = adapter
    }

    /**
     * 切季：缓存命中直接应用，否则后台拉取 `getSeasonDetail`（一次带回该季分区/季类型/状态文案）。
     *
     * 目标季**至少要有一个非空分区**才允许切换：空季一旦应用，会留下 tab 高亮/列表/选中下标
     * 半更新的状态，用户再点集卡片就会按下标越界崩溃。
     */
    private fun switchSeason(tabIndex: Int) {
        if (switchingSeason) return
        val adapter = seasonTabAdapter ?: return
        val target = adapter.getItem(tabIndex) ?: return
        if (target.season_id == currentSeasonId) return

        val cached = seasonCache[target.season_id]
        if (cached != null && cached.sectionList.isNotEmpty()) {
            applySeason(tabIndex, target, cached)
            return
        }

        switchingSeason = true
        CenterThreadPool.run {
            try {
                val meta = BangumiApi.getSeasonDetail(target.season_id)
                if (meta.sectionList.none { !it.episodeList.isNullOrEmpty() }) {
                    CenterThreadPool.runOnUiThread {
                        switchingSeason = false
                        if (isAdded) MsgUtil.showMsg(UiText.of("该季暂无内容"))
                    }
                    return@run
                }
                seasonCache[target.season_id] = meta
                CenterThreadPool.runOnUiThread {
                    switchingSeason = false
                    if (!isAdded) return@runOnUiThread
                    applySeason(tabIndex, target, meta)
                }
            } catch (e: Exception) {
                MsgUtil.err(UiText.of("番剧切季："), e)
                CenterThreadPool.runOnUiThread {
                    switchingSeason = false
                    if (isAdded) MsgUtil.showMsg(UiText.of("切换失败，请稍后重试"))
                }
            }
        }
    }

    /**
     * 应用切季结果（主线程）：记住离开那一季的选中位置 → 换掉分区列表 → 恢复目标季的选中位置。
     *
     * 选中位置优先级：上次离开这一季时记下的下标 → 首个非空分区的第 1 集。
     * 这里不做「上次看到第几集」的网络定位（那是另一个功能的口径），所以不会有额外请求。
     */
    private fun applySeason(tabIndex: Int, tab: Bangumi.Season, meta: BangumiApi.SeasonMeta) {
        seasonSelection[currentSeasonId] = intArrayOf(selectedSection, selectedEpisode)

        currentSeasonId = tab.season_id
        currentSeasonType = if (meta.seasonType != 0) meta.seasonType else (bangumi?.info?.type ?: 0)
        tab.seasonType = currentSeasonType
        tab.statusDesc = meta.statusDesc

        // 追番状态是"季级"的：currentSeasonId 一换就重画按钮、并按本季 season_id 重新校正。
        // 放在换分区之前，下面「空分区提前 return」时也不会把上一季的按钮状态留在屏幕上。
        setupFollowButton()

        sections = meta.sectionList

        // 越界兜底：服务端数据随时可能变，记忆里的下标不能直接信
        val remembered = seasonSelection[currentSeasonId] ?: IntArray(2)
        var sectionIndex = remembered[0].coerceIn(0, maxOf(sections.size - 1, 0))
        var section = sections.getOrNull(sectionIndex)
        if (section == null || section.episodeList.isNullOrEmpty()) {
            sectionIndex = firstSectionWithEpisodes()
            section = sections.getOrNull(sectionIndex)
        }
        if (section == null || section.episodeList.isNullOrEmpty()) {
            // 理论到不了（switchSeason 已经挡掉空季）；真出现就保持现状，不要把下标改坏
            return
        }
        // 记忆的分区已经变空、回落到别的分区时，集下标就不再适用了，从第 1 集开始
        var episodeIndex = if (remembered[0] == sectionIndex) remembered[1] else 0
        episodeIndex = episodeIndex.coerceIn(0, section.episodeList.size - 1)

        selectedSection = sectionIndex
        selectedEpisode = episodeIndex

        seasonTabAdapter?.selectedIndex = tabIndex

        val adapter = episodeRecyclerView?.adapter as? EpisodeCardAdapter
        adapter?.setData(section.episodeList)
        adapter?.selectedItemIndex = episodeIndex
        episodeRecyclerView?.scrollToPosition(episodeIndex)

        // 状态文案是"季级"的：切季后要换成这一季的 new_ep.desc（剧场版/OVA 与主季的更新状态不同）
        updateEpisodeStatusText()
        refreshReplies()
    }

    /**
     * 追番按钮：先用本地缓存点亮首帧，再异步用服务端状态校正。
     *
     * 缓存（{@code bangumi_follow_<season_id>}）只是为了让按钮别闪一下"追番"，
     * 真相以 {@code pgc/view/web/season/user/status} 为准；查询失败时保持缓存值不动。
     * 未登录不查询也不发请求（接口会返回 -101），直接提示。
     */
    @SuppressLint("SetTextI18n")
    private fun setupFollowButton() {
        val button = followButton ?: return
        // 必须用当前展示的季：首屏调用时 currentSeasonId 还没赋值（:0），此时退回 info 上的主季；
        // 切季后 currentSeasonId 已是目标季，继续用 info 会拿主季的状态去点亮别的季的按钮
        val seasonId = currentSeasonId.takeIf { it > 0L } ?: (bangumi?.info?.season_id ?: 0L)
        if (seasonId <= 0L) {
            // season_id 都没有的壳数据（反查失败等情况）不该给一个点了会失败的按钮
            button.visibility = View.GONE
            return
        }

        val loggedIn = SharedPreferencesUtil.getLong("mid", 0) != 0L

        fun applyFollowed(value: Boolean) {
            followed = value
            button.text = if (value) UiText.of("已追番") else UiText.of("追番")
        }

        button.visibility = View.VISIBLE
        applyFollowed(SharedPreferencesUtil.getBoolean(BangumiApi.FOLLOW_KEY_PREFIX + seasonId, false))

        button.setOnClickListener {
            if (!loggedIn) {
                MsgUtil.showMsg(UiText.of("未登录，无法追番"))
                return@setOnClickListener
            }
            val target = !followed
            // 用户已经明确表态，正在路上的状态查询（首屏/切季那次）不能再回写覆盖它
            followFetchToken++
            button.isEnabled = false
            CenterThreadPool.run {
                try {
                    val code = BangumiApi.setFollow(seasonId, target)
                    CenterThreadPool.runOnUiThread {
                        button.isEnabled = true
                        if (code == 0) {
                            // 缓存按发起请求时的季写，换季后也不能丢；
                            // 但按钮只在仍停在这一季时才翻转——切季在途时把上一季的结果画到本季上会误导用户
                            SharedPreferencesUtil.putBoolean(BangumiApi.FOLLOW_KEY_PREFIX + seasonId, target)
                            val shownSeasonId = currentSeasonId.takeIf { it > 0L } ?: (bangumi?.info?.season_id ?: 0L)
                            if (shownSeasonId == seasonId) applyFollowed(target)
                            MsgUtil.showMsg(if (target) UiText.of("已追番") else UiText.of("已取消追番"))
                        } else {
                            MsgUtil.showMsg(UiText.of("操作失败，请稍后重试"))
                        }
                    }
                } catch (e: Exception) {
                    // 失败不能翻转按钮状态，否则用户以为追上了、重进页面又变回来
                    CenterThreadPool.runOnUiThread {
                        button.isEnabled = true
                        MsgUtil.showMsg(UiText.of("操作失败，请稍后重试"))
                    }
                }
            }
        }

        if (!loggedIn) return

        val token = ++followFetchToken
        CenterThreadPool.run {
            try {
                val remote = BangumiApi.getFollowStatus(seasonId)
                CenterThreadPool.runOnUiThread {
                    // 期间切过季、或用户点过按钮：这份响应说的是旧季/旧状态，丢弃
                    if (token != followFetchToken || !isAdded) return@runOnUiThread
                    applyFollowed(remote)
                    SharedPreferencesUtil.putBoolean(BangumiApi.FOLLOW_KEY_PREFIX + seasonId, remote)
                }
            } catch (ignored: Exception) {
                // 拉取失败就保持缓存值，不要把一个未知状态写成"未追番"
            }
        }
    }

    /** 当前季的分区列表里第一个真正含有剧集的分区下标；全都为空（或没有分区）时返回 -1。 */
    private fun firstSectionWithEpisodes(): Int {
        for (i in sections.indices) {
            if (!sections[i].episodeList.isNullOrEmpty()) return i
        }
        return -1
    }

    /**
     * 在当前季的分区里找 epid 对应的一集并选中。
     *
     * 只改 selectedSection / selectedEpisode 这两个下标，最终取集仍然走 currentEpisode()，
     * 这样即使数据里有空分区也不会选中一个取不出剧集的季（P19 修的越界崩溃就是这么来的）。
     * 找不到（epid 为 0、不在当前季里、或取出来是 null）时不动下标并返回 false，
     * 调用方保持第 1 集即可。
     */
    private fun locateEpisodeByEpid(epid: Long): Boolean {
        if (epid <= 0L) return false
        for (s in sections.indices) {
            val episodes = sections[s].episodeList ?: continue
            for (e in episodes.indices) {
                if (episodes[e].id != epid) continue
                if (s == selectedSection && e == selectedEpisode) return true
                selectedSection = s
                selectedEpisode = e
                // 能通过既有安全取值拿到这一集才算定位成功，否则让调用方退回第 1 集
                return currentEpisode() != null
            }
        }
        return false
    }

    /**
     * 当前选中的剧集；选中分区为空或下标越界时返回 null。
     * 所有需要按下标取剧集的地方都必须走这里，否则空分区数据会直接抛 IndexOutOfBoundsException。
     */
    private fun currentEpisode(): Bangumi.Episode? {
        if (selectedSection !in sections.indices) return null
        val episodes = sections[selectedSection].episodeList ?: return null
        if (selectedEpisode !in episodes.indices) return null
        return episodes[selectedEpisode]
    }

    /**
     * 选集标题行的状态文案：优先当前季的 new_ep.desc，其次详情上的 desc，最后按完结状态 + 正片集数拼。
     *
     * 两种情况会没有 desc：接口没下发（老番/预告），以及当前展示的是附加季（剧场版没有 new_ep）。
     */
    @SuppressLint("SetTextI18n")
    private fun updateEpisodeStatusText() {
        val seasonDesc = seasonCache[currentSeasonId]?.statusDesc
        // info 上的 desc 只描述主季；当前展示的不是主季时必须丢掉，否则剧场版会显示主季的"连载中"
        val infoDesc =
            if (currentSeasonId == bangumi?.info?.season_id) bangumi?.info?.newEpDesc else null
        val isFinish = bangumi?.info?.publish?.is_finish ?: 0
        // 正片集数：优先后端标成"正片"的分区，没有就退回第一个非空分区
        val mainSection = sections.firstOrNull { it.title == "正片" }
            ?: sections.firstOrNull { !it.episodeList.isNullOrEmpty() }
        val mainEpCount = mainSection?.episodeList?.size ?: 0

        val status = episodeStatusText(seasonDesc, infoDesc, isFinish, mainEpCount)
        episodeChoose?.text =
            if (status == STATUS_TEXT_FALLBACK) STATUS_TEXT_FALLBACK else UiText.fmt("选集 · %1\$s", status)
    }

    /**
     * 把选中的集切到 (分区下标, 集下标)。
     *
     * 横滑列表只显示当前分区的剧集，所以跨分区选集必须连列表数据一起换，
     * 只改 selectedEpisode 会让高亮和列表内容对不上。
     */
    private fun selectEpisode(sectionIndex: Int, episodeIndex: Int) {
        if (sectionIndex !in sections.indices) return
        val episodes = sections[sectionIndex].episodeList ?: return
        if (episodes.isEmpty()) return

        selectedSection = sectionIndex
        selectedEpisode = episodeIndex.coerceIn(0, episodes.size - 1)

        val adapter = episodeRecyclerView?.adapter as? EpisodeCardAdapter
        adapter?.setData(episodes)
        adapter?.selectedItemIndex = selectedEpisode
        episodeRecyclerView?.scrollToPosition(selectedEpisode)
        refreshReplies()
    }

    /**
     * 选集弹窗：把**当前季全部分区**的剧集拍平列出（多分区时前缀【分区名】）。
     *
     * 只靠横滑列表选不到"花絮/PV/特别篇"这些不在正片分区里的集，而它们恰恰是会被特意去找的
     * （对齐远端 getEpisodeChooseDialog 的全量口径）。
     */
    private fun getEposideChooseDialog(): Dialog {
        val labels = ArrayList<String>()
        val positions = ArrayList<IntArray>()
        // 只有多个分区时才加前缀：单分区加前缀纯属噪音
        val multiSection = sections.size > 1

        for (s in sections.indices) {
            val episodes = sections[s].episodeList ?: continue
            for (e in episodes.indices) {
                val episode = episodes[e]
                val name = episode.title + "." + episode.title_long
                labels.add(
                    if (multiSection) UiText.fmt("【%1\$s】%2\$s", sections[s].title ?: "", name)
                    else name
                )
                positions.add(intArrayOf(s, e))
            }
        }

        // 双保险：数据刷新后仍可能落到空季上，空列表既不能建下标也不能建选择项
        if (labels.isEmpty()) {
            MsgUtil.showMsg(UiText.of("该季暂无剧集"))
            dialog = TerminalDialog.alert(requireContext(), message = UiText.of("该季暂无剧集"))
            return dialog!!
        }

        val checked = positions.indexOfFirst {
            it[0] == selectedSection && it[1] == selectedEpisode
        }.coerceAtLeast(0)

        dialog = TerminalDialog.singleChoice(
            context = requireContext(),
            title = UiText.of("选择剧集"),
            items = labels,
            checked = checked
        ) { _, which ->
            positions.getOrNull(which)?.let { selectEpisode(it[0], it[1]) }
            dialog?.dismiss()
        }

        return dialog!!
    }

    private fun refreshReplies() {
        val episode = currentEpisode() ?: return
        val activity = activity
        if (activity is VideoInfoActivity) {
            activity.setCurrentAid(episode.aid)
        }
    }

    fun onFinishLoad() {
        try {
            val activity = requireActivity()
            if (activity is VideoInfoActivity) {
                activity.crossFade(view)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun formatNumber(num: Int): String {
        return when {
            num >= 100000000 -> String.format(UiText.of("%.1f亿"), num / 100000000.0)
            num >= 10000 -> String.format(UiText.of("%.1f万"), num / 10000.0)
            else -> num.toString()
        }
    }
}