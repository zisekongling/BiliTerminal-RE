package com.RobinNotBad.BiliClient.activity.video

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.video.BangumiCacheAdapter
import com.RobinNotBad.BiliClient.adapter.video.EpisodeCardAdapter
import com.RobinNotBad.BiliClient.api.BangumiApi
import com.RobinNotBad.BiliClient.api.VipApi
import com.RobinNotBad.BiliClient.model.LocalVideo
import com.RobinNotBad.BiliClient.service.DownloadService
import com.RobinNotBad.BiliClient.service.download.BangumiCacheDownloadIndex
import com.RobinNotBad.BiliClient.service.download.BangumiCacheSeason
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetworkPlaybackQuality

/**
 * 番剧缓存页（26.11.15 重做，取代详情页里「选季 → 选集 → 清晰度」的三连弹窗）。
 *
 * 版式来自用户对交互的要求：**顶部一行清晰度入口**（点开就是投稿视频那套全屏清晰度页，
 * 只是选完回填、不当场下载）、**下面是按季折叠的下拉分组**（默认全部折叠）、
 * 每个季行右侧一个「全选本季」、展开后每集一行「集名 + 会员/限免角标 + 勾选标记」。
 *
 * 跨季混选是允许的：勾选状态按「季」各自维护，入队时再按季分组拼成多条任务。
 *
 * 默认行为：进来就把**当前季**拉下来并勾上它的「正片」分区（与详情页第一屏看到的一致），
 * 用户想改就改，不改可以直接按底部「开始缓存」。
 *
 * 剧集按季懒加载：只有展开或点「全选本季」时才去拉那一季的 [BangumiApi.getSeasonDetail]，
 * 一部番五六季也不会一进页面就打五六个请求。
 *
 * 三种集是**勾不动**的（行尾直接写状态字，不摆假勾选圈）：
 * - 「已下载」：缓存目录里已经有成品了（[BangumiCacheDownloadIndex]）
 * - 「队列中」：下载表里还挂着这集的任务
 * - 「大会员」：这集标着会员，而当前账号不是大会员（[queryVipState]）
 * 默认勾选与「全选本季」都会跳过这三类，只勾真能下的集。
 */
class BangumiCacheActivity : BaseActivity() {

    private lateinit var adapter: BangumiCacheAdapter
    private lateinit var textQuality: TextView
    private lateinit var textSelected: TextView
    private lateinit var btnStart: View

    private val seasons = ArrayList<BangumiCacheAdapter.SeasonItem>()

    private var bangumiCover = ""
    private var currentSeasonId = 0L
    private var currentSeasonType = 0
    private var qualityQn = 0
    private var qualityLabel = ""

    /** 当前账号是不是大会员：非大会员时「大会员」集锁住不给勾（用户 26.11.15 的要求）。 */
    @Volatile
    private var isVip = false

    /** 会员状态是否已经问到了；没问到之前一律按非大会员画（宁可少勾，不可让用户勾了必失败）。 */
    @Volatile
    private var isVipResolved = false

    /** 清晰度选择页的「只挑不下载」回值。 */
    private val qualityPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val qn = data.getIntExtra(QualityChooserActivity.EXTRA_PICK_QN, 0)
            if (qn <= 0) return@registerForActivityResult
            qualityQn = qn
            qualityLabel = data.getStringExtra(QualityChooserActivity.EXTRA_PICK_LABEL)
                ?.takeIf { it.isNotEmpty() } ?: LocalVideo.getQualityLabel(qn)
            renderQuality()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bangumi_cache)
        setPageName(getString(R.string.uitext_bangumicache_001))
        findViewById<View>(R.id.top).setOnClickListener { finish() }

        textQuality = findViewById(R.id.text_quality)
        textSelected = findViewById(R.id.text_selected)
        btnStart = findViewById(R.id.btn_start)

        qualityQn = NetworkPlaybackQuality.defaultQn(this)
        qualityLabel = LocalVideo.getQualityLabel(qualityQn)
        renderQuality()
        findViewById<View>(R.id.quality_row).setOnClickListener { openQualityPicker() }

        bangumiCover = intent.getStringExtra(EXTRA_BANGUMI_COVER).orEmpty()
        currentSeasonId = intent.getLongExtra(EXTRA_CURRENT_SEASON_ID, 0L)
        currentSeasonType = intent.getIntExtra(EXTRA_CURRENT_SEASON_TYPE, 0)
        collectSeasons()

        adapter = BangumiCacheAdapter(
            onToggleSeason = { item -> toggleSeason(item) },
            onSelectAllClick = { item -> onSelectAllClicked(item) },
            onToggleEpisode = { item, index -> toggleEpisode(item, index) }
        )
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = CustomLinearManager(this)
        recyclerView.adapter = adapter
        adapter.setSeasons(seasons)

        btnStart.setOnClickListener { enqueue() }
        updateSelectedText()

        // 先问会员状态、再预热当前季：默认勾选要按「这集我到底能不能下」来定，
        // 顺序反了就会出现「先勾上大会员集、拿到会员状态后又取消」的闪烁
        CenterThreadPool.run {
            queryVipState()
            val target = seasons.firstOrNull { it.seasonId == currentSeasonId }
            target?.let { loadSeason(it, selectMainSection = true) }
        }
    }

    /** 问一次会员状态：失败按非大会员处理，不弹红字（缓存页其余部分照常可用）。 */
    private fun queryVipState() {
        isVip = try {
            VipApi.getVipInfo().isVip
        } catch (e: Exception) {
            Logu.e("BangumiCache", "读取会员状态失败，按非大会员处理：" + e.message)
            false
        }
        isVipResolved = true
    }

    private fun collectSeasons() {
        val ids = intent.getLongArrayExtra(EXTRA_SEASON_IDS) ?: LongArray(0)
        val titles = intent.getStringArrayListExtra(EXTRA_SEASON_TITLES) ?: arrayListOf()
        val covers = intent.getStringArrayListExtra(EXTRA_SEASON_COVERS) ?: arrayListOf()
        val fallbackTitle = getString(R.string.uitext_bangumiinfofragment_033)
        for (index in ids.indices) {
            seasons.add(
                BangumiCacheAdapter.SeasonItem(
                    ids[index],
                    titles.getOrNull(index).orEmpty().ifEmpty { fallbackTitle },
                    covers.getOrNull(index).orEmpty(),
                    if (ids[index] == currentSeasonId) currentSeasonType else 0
                )
            )
        }
        if (seasons.isEmpty()) {
            MsgUtil.showMsg(getString(R.string.uitext_bangumiinfofragment_034))
            finish()
        }
    }

    private fun renderQuality() {
        textQuality.text = getString(R.string.uitext_bangumicache_002, qualityLabel)
    }

    /** 打开清晰度页：复用投稿视频那套全屏清晰度列表，只是回填而不下载。 */
    private fun openQualityPicker() {
        val holder = seasons.firstOrNull { item ->
            item.entries?.any { it.episode.aid > 0 && it.episode.cid > 0 } == true
        }
        val sample = holder?.entries?.firstOrNull { it.episode.aid > 0 && it.episode.cid > 0 }

        val intent = Intent(this, QualityChooserActivity::class.java)
            .putExtra(QualityChooserActivity.EXTRA_PICK_ONLY, true)
            .putExtra(QualityChooserActivity.EXTRA_PICK_QN, qualityQn)
        if (holder != null && sample != null) {
            intent.putExtra(QualityChooserActivity.EXTRA_BGM_AID, sample.episode.aid)
                .putExtra(QualityChooserActivity.EXTRA_BGM_CID, sample.episode.cid)
                .putExtra(QualityChooserActivity.EXTRA_BGM_EPID, sample.episode.id)
                .putExtra(QualityChooserActivity.EXTRA_BGM_SEASON_ID, holder.seasonId)
                .putExtra(
                    QualityChooserActivity.EXTRA_BGM_SEASON_TYPE,
                    if (holder.seasonType > 0) holder.seasonType else currentSeasonType
                )
        }
        qualityPicker.launch(intent)
    }

    private fun toggleSeason(item: BangumiCacheAdapter.SeasonItem) {
        // 上一次拉取失败：这一下当成「重试」，别顺势把它折叠掉
        if (item.failed) {
            item.expanded = true
            loadSeason(item, selectMainSection = false)
            return
        }
        item.expanded = !item.expanded
        // 第一次展开才去拉这一季的剧集，拉完由 loadSeason 自己刷新
        if (item.expanded && item.entries == null && !item.loading) {
            loadSeason(item, selectMainSection = false)
            return
        }
        // 已经拉过：重新展开时再问一遍磁盘——用户可能刚在下载列表里下完几集又回来了
        if (item.expanded) refreshDownloadState(item)
        adapter.rebuild()
    }

    /**
     * 重新核对「已下载 / 队列中」。
     *
     * 只改标记、不动勾选之外的东西；顺手把已经锁定上的集从勾选里摘掉，
     * 否则底部「已选 N 集」会和真能入队的条数对不上。
     */
    private fun refreshDownloadState(item: BangumiCacheAdapter.SeasonItem) {
        val entries = item.entries ?: return
        if (entries.isEmpty()) return
        CenterThreadPool.run {
            // 会员状态还没问到就在这儿补问：展开时才知道自己是不是大会员也不能算错
            if (!isVipResolved) queryVipState()
            markDownloadState(item, entries)
            runOnUiThread {
                for (index in entries.indices) {
                    if (entries[index].locked() && index in item.checked.indices) {
                        item.checked[index] = false
                    }
                }
                adapter.rebuild()
                updateSelectedText()
            }
        }
    }

    /**
     * 给每一集打上「已下载 / 队列中」标记（必须在后台线程调用：有磁盘 stat 与一次查库）。
     *
     * 目录名靠 [BangumiCacheSeason.childNameOf] 与下载服务算出的 child 保持一致，
     * 两边写两套规则的话，就会出现「明明下过了却还让你勾」。
     */
    private fun markDownloadState(
        item: BangumiCacheAdapter.SeasonItem,
        entries: List<BangumiCacheAdapter.EpisodeEntry>
    ) {
        val queued = BangumiCacheDownloadIndex.queuedCids()
        val unknown = getString(R.string.text_download_bangumi_episode_unknown)
        for (entry in entries) {
            entry.queued = queued.contains(entry.episode.cid)
            entry.downloaded = !entry.queued && BangumiCacheDownloadIndex.isDownloaded(
                item.title,
                BangumiCacheSeason.childNameOf(entry.episode, unknown)
            )
            // 非大会员：标着「会员」的集直接锁住——勾了也只会下到失败，
            // 不如当场说明白。大会员账号则全部放开（用户 26.11.15 的要求）。
            entry.vipLocked = !isVip && !entry.downloaded && !entry.queued &&
                EpisodeCardAdapter.badgeKind(entry.episode.badge) == EpisodeCardAdapter.BadgeKind.VIP
        }
    }

    private fun onSelectAllClicked(item: BangumiCacheAdapter.SeasonItem) {
        if (item.loading) return
        if (item.entries == null) {
            // 还没拉过：拉完再全选，别让用户点两次
            loadSeason(item, selectMainSection = false, selectAll = true)
            return
        }
        if (item.entries.isNullOrEmpty()) return
        val target = !item.allSelected()
        val entries = item.entries.orEmpty()
        for (index in item.checked.indices) {
            // 已下载 / 队列中的集勾不动，全选也得绕开它们
            if (entries.getOrNull(index)?.locked() == true) continue
            item.checked[index] = target
        }
        adapter.rebuild()
        updateSelectedText()
    }

    private fun toggleEpisode(item: BangumiCacheAdapter.SeasonItem, index: Int) {
        if (index !in item.checked.indices) return
        // 已下载 / 队列中的集点了就当没点（适配器那边也没挂点击，这里再兜一层）
        if (item.entries?.getOrNull(index)?.locked() == true) return
        item.checked[index] = !item.checked[index]
        adapter.rebuild()
        updateSelectedText()
    }

    private fun updateSelectedText() {
        val total = seasons.sumOf { it.selectedCount() }
        textSelected.text = getString(R.string.uitext_bangumiinfofragment_029, total)
        textSelected.setTextColor(
            if (total > 0) ColorScheme.getTextPrimary(this) else ColorScheme.getTextSecondary(this)
        )
        // 按钮不置灰：一集没勾时点它会给出「请先勾选」的提示，比一个灰按钮更好解释
    }

    /**
     * 拉一季的剧集。
     *
     * @param selectMainSection 勾上「正片」分区（没有正片分区时全勾）——首屏预热当前季用
     * @param selectAll         勾上这一季全部剧集——「全选本季」在未加载时用
     */
    private fun loadSeason(
        item: BangumiCacheAdapter.SeasonItem,
        selectMainSection: Boolean,
        selectAll: Boolean = false
    ) {
        if (item.loading) return
        item.loading = true
        item.failed = false
        // 刷新视图**必须回主线程**：本方法有两条调用路径——用户点开某一季（主线程）、
        // 首屏预热当前季（onCreate 里的 CenterThreadPool，IO 线程）。在 IO 线程上
        // notifyDataSetChanged 会触发 ViewRootImpl 的线程检查抛 CalledFromWrongThreadException，
        // 而 CenterThreadPool 把它吞掉只记一条日志，于是 loading 永远停在 true、entries 永远是 null：
        // 那一季的行一直显示「正在获取剧集…」，再怎么点也被上面的 `if (item.loading) return` 挡住。
        // 这正是反馈 #179「点开缓存页时播放页停在哪一季，哪一季就加载不出来」（只有预热的那一季中招）。
        runOnUiThread { adapter.rebuild() }
        CenterThreadPool.run {
            try {
                val meta = BangumiApi.getSeasonDetail(item.seasonId)
                val entries = ArrayList<BangumiCacheAdapter.EpisodeEntry>()
                var hasMainSection = false
                for (section in meta.sectionList.orEmpty()) {
                    val sectionTitle = section.title.orEmpty()
                    if (sectionTitle == MAIN_SECTION_TITLE) hasMainSection = true
                    for (episode in section.episodeList.orEmpty()) {
                        entries.add(BangumiCacheAdapter.EpisodeEntry(sectionTitle, episode))
                    }
                }
                // 哪些集已经下好了 / 已经在队列里，得问磁盘和下载表（都在后台线程里问）
                markDownloadState(item, entries)
                val checked = BooleanArray(entries.size) { index ->
                    val entry = entries[index]
                    // 已下载/队列中的集不给默认勾上：勾了也入不了队，只会让计数虚高
                    !entry.locked() && when {
                        selectAll -> true
                        selectMainSection && hasMainSection ->
                            entries[index].sectionTitle == MAIN_SECTION_TITLE
                        selectMainSection -> true
                        else -> false
                    }
                }
                runOnUiThread {
                    item.loading = false
                    item.seasonType = meta.seasonType
                    item.entries = entries
                    item.checked = checked
                    adapter.rebuild()
                    updateSelectedText()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    item.loading = false
                    item.failed = true
                    adapter.rebuild()
                }
                MsgUtil.err(e)
            }
        }
    }

    /** 勾好的集按季分组入队；一季一条任务，最后只出汇总提示。 */
    private fun enqueue() {
        val targets = ArrayList<BangumiCacheSeason>()
        for (item in seasons) {
            val entries = item.entries ?: continue
            val picked = entries.filterIndexed { index, entry ->
                !entry.locked() && item.checked.getOrNull(index) == true
            }
                .map { it.episode }
                .filter { it.aid > 0 && it.cid > 0 }
            if (picked.isEmpty()) continue
            targets.add(
                BangumiCacheSeason(
                    item.title,
                    item.cover.ifEmpty { bangumiCover },
                    item.seasonId,
                    if (item.seasonType > 0) item.seasonType else currentSeasonType,
                    picked
                )
            )
        }
        if (targets.isEmpty()) {
            MsgUtil.showMsg(getString(R.string.uitext_bangumicache_006))
            return
        }
        DownloadService.startDownloadBangumi(qualityQn, targets)
        finish()
    }

    companion object {
        const val EXTRA_BANGUMI_TITLE = "bangumiTitle"
        const val EXTRA_BANGUMI_COVER = "bangumiCover"
        const val EXTRA_CURRENT_SEASON_ID = "currentSeasonId"
        const val EXTRA_CURRENT_SEASON_TYPE = "currentSeasonType"
        const val EXTRA_SEASON_IDS = "seasonIds"
        const val EXTRA_SEASON_TITLES = "seasonTitles"
        const val EXTRA_SEASON_COVERS = "seasonCovers"

        /** 服务端的正片分区名，比较用（与详情页同一个协议值）。 */
        private const val MAIN_SECTION_TITLE = "正片"
    }
}
