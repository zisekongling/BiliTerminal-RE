package com.RobinNotBad.BiliClient.adapter.video

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.Bangumi
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme

/**
 * 番剧缓存页的「按季折叠 + 逐集勾选」列表。
 *
 * 为什么单独一个适配器、而不是复用 [EpisodeCardAdapter]：那个是详情页的横滑卡片，
 * 语义是「点一下播放这一集」，没有勾选态也没有折叠分组；这里要的是纵向分组树
 * （季 = 可折叠的组头，集 = 组内可勾的行），两者视图结构完全不同。
 *
 * 列表是**扁平的一维行数组**：组头一行 + （展开时才有的）该季每集一行。折叠/展开/勾选
 * 全部由宿主（[com.RobinNotBad.BiliClient.activity.video.BangumiCacheActivity]）改完状态后
 * 调 [rebuild] 重算行数组 —— 行数会变，用 notifyDataSetChanged 最省心，列表规模也就几十行。
 *
 * 季的剧集是**按需拉取**的：[SeasonItem.entries] 为 null 表示还没拉到，此时组头显示
 * 「正在获取剧集…」（[SeasonItem.loading]）或「加载失败，点此重试」（[SeasonItem.failed]）。
 */
class BangumiCacheAdapter(
    private val onToggleSeason: (SeasonItem) -> Unit,
    private val onSelectAllClick: (SeasonItem) -> Unit,
    private val onToggleEpisode: (SeasonItem, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** 一集在季内的位置：带上分区名，多分区（正片 + PV/花絮）时才需要前缀。 */
    class EpisodeEntry(@JvmField val sectionTitle: String, @JvmField val episode: Bangumi.Episode)

    /** 一季的缓存状态：剧集、折叠、勾选都在这里，宿主直接读写。 */
    class SeasonItem(
        @JvmField val seasonId: Long,
        @JvmField val title: String,
        @JvmField val cover: String,
        @JvmField var seasonType: Int
    ) {
        /** null = 还没拉过；空 list = 拉到了但这季没有剧集。 */
        @JvmField var entries: List<EpisodeEntry>? = null
        @JvmField var expanded: Boolean = false
        @JvmField var loading: Boolean = false
        @JvmField var failed: Boolean = false
        @JvmField var checked: BooleanArray = BooleanArray(0)

        fun episodeCount(): Int = entries?.size ?: 0

        fun selectedCount(): Int = checked.count { it }

        fun allSelected(): Boolean = checked.isNotEmpty() && checked.all { it }
    }

    private sealed class Row {
        class Season(val item: SeasonItem) : Row()
        class Episode(val item: SeasonItem, val index: Int) : Row()
    }

    private val seasons = ArrayList<SeasonItem>()
    private val rows = ArrayList<Row>()

    @SuppressLint("NotifyDataSetChanged")
    fun setSeasons(list: List<SeasonItem>) {
        seasons.clear()
        seasons.addAll(list)
        rebuild()
    }

    /** 宿主改完任意状态后调用：重算扁平行数组并整表刷新。 */
    @SuppressLint("NotifyDataSetChanged")
    fun rebuild() {
        rows.clear()
        for (item in seasons) {
            rows.add(Row.Season(item))
            if (!item.expanded) continue
            val entries = item.entries ?: continue
            for (index in entries.indices) rows.add(Row.Episode(item, index))
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Season) TYPE_SEASON else TYPE_EPISODE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SEASON)
            SeasonHolder(inflater.inflate(R.layout.cell_bangumi_cache_season, parent, false))
        else
            EpisodeHolder(inflater.inflate(R.layout.cell_bangumi_cache_episode, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows.getOrNull(position)) {
            is Row.Season -> (holder as? SeasonHolder)?.bind(row.item)
            is Row.Episode -> (holder as? EpisodeHolder)?.bind(row.item, row.index)
            null -> Unit
        }
    }

    private inner class SeasonHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val arrow: TextView = view.findViewById(R.id.season_arrow)
        private val title: TextView = view.findViewById(R.id.season_title)
        private val count: TextView = view.findViewById(R.id.season_count)
        private val action: TextView = view.findViewById(R.id.season_action)

        fun bind(item: SeasonItem) {
            val context = itemView.context
            arrow.text = if (item.expanded) "▾" else "▸"
            arrow.setTextColor(ColorScheme.getTextSecondary(context))

            title.text = item.title
            title.setTextColor(ColorScheme.getTextPrimary(context))

            count.text = when {
                item.loading -> context.getString(R.string.uitext_bangumiinfofragment_037)
                item.failed -> context.getString(R.string.uitext_bangumicache_003)
                item.entries == null -> ""
                item.episodeCount() == 0 -> context.getString(R.string.uitext_bangumicache_007)
                item.selectedCount() > 0 -> context.getString(
                    R.string.uitext_bangumicache_005, item.selectedCount(), item.episodeCount()
                )
                else -> context.getString(R.string.uitext_bangumicache_004, item.episodeCount())
            }
            count.setTextColor(ColorScheme.getTextSecondary(context))

            // 没拉到剧集时「全选本季」无从下手：先露出重试/展开的入口，别给一个点了没反应的按钮
            action.visibility = if (item.entries.isNullOrEmpty()) View.GONE else View.VISIBLE
            action.text = context.getString(
                if (item.allSelected()) R.string.uitext_bangumiinfofragment_031
                else R.string.uitext_bangumiinfofragment_030
            )
            action.setTextColor(ColorScheme.getPrimary(context))
            action.setOnClickListener { onSelectAllClick(item) }

            itemView.setOnClickListener { onToggleSeason(item) }
        }
    }

    private inner class EpisodeHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.episode_title)
        private val badge: TextView = view.findViewById(R.id.episode_badge)
        private val check: TextView = view.findViewById(R.id.episode_check)

        fun bind(item: SeasonItem, index: Int) {
            val entry = item.entries?.getOrNull(index) ?: return
            val context = itemView.context
            val episode = entry.episode
            val isChecked = item.checked.getOrNull(index) == true

            title.text = episodeName(entry, multiSection = item.entries.orEmpty().any {
                it.sectionTitle.isNotEmpty() && it.sectionTitle != entry.sectionTitle
            })
            title.setTextColor(
                if (isChecked) ColorScheme.getTextPrimary(context)
                else ColorScheme.getTextSecondary(context)
            )

            // 会员 / 限免角标与详情页同一套判定与配色（EpisodeCardAdapter.badgeKind），
            // 会员集不静默跳过：勾得上，失败原因交给下载列表显示
            val badgeText = episode.badge
            when (EpisodeCardAdapter.badgeKind(badgeText)) {
                EpisodeCardAdapter.BadgeKind.NONE -> badge.visibility = View.GONE
                else -> {
                    badge.visibility = View.VISIBLE
                    badge.text = badgeText
                    badge.setTextColor(
                        when (EpisodeCardAdapter.badgeKind(badgeText)) {
                            EpisodeCardAdapter.BadgeKind.VIP -> ColorScheme.getVipPink(context)
                            EpisodeCardAdapter.BadgeKind.FREE -> ColorScheme.getSuccessColor(context)
                            else -> ColorScheme.getTextTertiary(context)
                        }
                    )
                }
            }

            check.text = if (isChecked) GLYPH_CHECKED else GLYPH_UNCHECKED
            check.setTextColor(
                if (isChecked) ColorScheme.getPrimary(context) else ColorScheme.getTextTertiary(context)
            )

            itemView.setOnClickListener { onToggleEpisode(item, index) }
        }

        /** 「第 1 话 各自的答案」：短标题打头，长标题跟在后面；多分区时再套一层【分区】。 */
        private fun episodeName(entry: EpisodeEntry, multiSection: Boolean): String {
            val episode = entry.episode
            val short = episode.title.orEmpty()
            val long = episode.title_long.orEmpty()
            val body = if (long.isEmpty() || long == short) short else "$short $long"
            return if (multiSection && entry.sectionTitle.isNotEmpty())
                itemView.context.getString(R.string.uitext_bangumiinfofragment_023, entry.sectionTitle, body)
            else body
        }
    }

    companion object {
        private const val TYPE_SEASON = 0
        private const val TYPE_EPISODE = 1

        /** 勾选标记：与详情页/弹窗里的双态引导符保持同一套字符。 */
        private const val GLYPH_CHECKED = "✓"
        private const val GLYPH_UNCHECKED = "○"
    }
}
