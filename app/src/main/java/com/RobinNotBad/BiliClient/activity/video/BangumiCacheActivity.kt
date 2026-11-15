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
import com.RobinNotBad.BiliClient.api.BangumiApi
import com.RobinNotBad.BiliClient.model.LocalVideo
import com.RobinNotBad.BiliClient.service.DownloadService
import com.RobinNotBad.BiliClient.service.download.BangumiCacheSeason
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
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

        // 首屏预热当前季：一进来就勾好「正片」，底部计数立刻有值
        seasons.firstOrNull { it.seasonId == currentSeasonId }
            ?.let { loadSeason(it, selectMainSection = true) }
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
        adapter.rebuild()
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
        for (index in item.checked.indices) item.checked[index] = target
        adapter.rebuild()
        updateSelectedText()
    }

    private fun toggleEpisode(item: BangumiCacheAdapter.SeasonItem, index: Int) {
        if (index !in item.checked.indices) return
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
        adapter.rebuild()
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
                val checked = BooleanArray(entries.size) { index ->
                    when {
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
            val picked = entries.filterIndexed { index, _ -> item.checked.getOrNull(index) == true }
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
