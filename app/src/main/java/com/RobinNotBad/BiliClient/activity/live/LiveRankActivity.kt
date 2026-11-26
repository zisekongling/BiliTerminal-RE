package com.RobinNotBad.BiliClient.activity.live

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.api.LiveRankApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool

/**
 * 直播间榜单（26.11.26 新增）：大航海 / 粉丝团 / 礼物价目三合一。
 *
 * 三个榜单的数据形状差得远，但**展示只需要两行字**，所以不各做一个 Activity，
 * 用 [Kind] 区分、统一塞进一个两行 adapter。做成独立页而不是对话框，
 * 是因为手表上的对话框只有 319×322，20 行榜单在那儿根本没法看。
 *
 * 三个接口都不需要 Cookie（见 [LiveRankApi] 注释），未登录也能看。
 *
 * <p>基类必须是 [RefreshListActivity]（二级页）：它挂在直播间详情之下，顶栏是「返回」。
 * 一度错用了 [com.RobinNotBad.BiliClient.activity.base.RefreshMainActivity]——那是一级菜单页
 * 的基类（走 `InstanceActivity`），会把顶栏点击换成「打开菜单」、图标覆盖成朝下的
 * `arrow_down`，还复用 `activity_simple_main_refresh`（不叫 pageName 的那个布局），
 * 表现就是用户反馈的「返回键点了没反应，方向也不对」。
 */
class LiveRankActivity : RefreshListActivity() {

    enum class Kind { GUARD, FANS, GIFT }

    /** 一行榜单：主行 + 副行。三榜共用，字段含义按 [kind] 解释。 */
    private data class Row(val title: String, val subtitle: String)

    private var kind = Kind.GUARD
    private var roomId = 0L
    private var ruid = 0L
    private var areaParentId = 0
    private var areaId = 0

    private val rows = ArrayList<Row>()
    private var adapter: RankAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // hasFixedSize / RecycledViewPool / 缓存大小都由基类统一设了，这里不再重复
        RankAdapter(rows).also {
            adapter = it
            setAdapter(it)
        }
        setOnRefreshListener { load() }
        applyIntent(intent)
    }

    /**
     * 同一个 Activity 顶着重启时（`am start` 同组件、某些第三方入口连点）Android 会把新
     * Intent 直接投给已存在的实例，不重走 onCreate。不接这一下就会出现「选了粉丝团却还
     * 停在大航海」——旧页面看着正常，只是内容错了。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    /** 读 extra → 定标题 → 拉数据。onCreate 与 onNewIntent 共用。 */
    private fun applyIntent(source: Intent) {
        kind = readKind(source)
        roomId = source.getLongExtra(EXTRA_ROOM_ID, 0L)
        ruid = source.getLongExtra(EXTRA_RUID, 0L)
        areaParentId = source.getIntExtra(EXTRA_AREA_PARENT_ID, 0)
        areaId = source.getIntExtra(EXTRA_AREA_ID, 0)
        setPageName(getString(titleRes(kind)))
        load()
    }

    private fun load() {
        setRefreshing(true)
        CenterThreadPool.run {
            val list: List<Row> = try {
                when (kind) {
                    Kind.GUARD -> LiveRankApi.getGuardMembers(roomId, ruid).map { member ->
                        Row(
                            title = "${member.rank}. ${member.name}",
                            subtitle = if (member.accompanyDays > 0) {
                                "陪伴 ${member.accompanyDays} 天"
                            } else {
                                getString(R.string.live_rank_guard)
                            }
                        )
                    }

                    Kind.FANS -> LiveRankApi.getFansMembers(ruid).map { member ->
                        Row(
                            title = "${member.rank}. ${member.name}",
                            subtitle = buildString {
                                if (member.medalName.isNotEmpty()) {
                                    append(member.medalName)
                                    append(' ')
                                    append(member.medalLevel)
                                    append(" · ")
                                }
                                guardLevelText(member.guardLevel)?.let {
                                    append(it)
                                    append(" · ")
                                }
                                append("亲密度 ")
                                append(member.score)
                            }
                        )
                    }

                    Kind.GIFT -> LiveRankApi.getGiftList(roomId, areaParentId, areaId).map { gift ->
                        Row(
                            title = gift.name,
                            subtitle = buildString {
                                append(yuanText(gift.priceCoin))
                                if (gift.desc.isNotEmpty()) {
                                    append(" · ")
                                    append(gift.desc)
                                }
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { loadFail(e) }
                return@run
            }
            runOnUiThread {
                rows.clear()
                rows.addAll(list)
                adapter?.notifyDataSetChanged()
                setRefreshing(false)
                if (rows.isEmpty()) showEmptyView() else hideEmptyView()
            }
        }
    }

    /** price 的单位是金瓜子，除以 1000 才是元（见 [LiveRankApi.Gift] 注释）。 */
    private fun yuanText(priceCoin: Long): String {
        val tenthYuan = Math.round(priceCoin / 100.0)
        return if (tenthYuan % 10 == 0L) {
            "${tenthYuan / 10} 元"
        } else {
            "${tenthYuan / 10}.${tenthYuan % 10} 元"
        }
    }

    private fun guardLevelText(level: Int): String? = when (level) {
        1 -> "总督"
        2 -> "提督"
        3 -> "舰长"
        else -> null
    }

    private class RankAdapter(private val rows: List<Row>) :
        RecyclerView.Adapter<RankAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.rankTitle)
            val subtitle: TextView = view.findViewById(R.id.rankSubtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_live_rank, parent, false))

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.title.text = row.title
            holder.subtitle.text = row.subtitle
        }
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ROOM_ID = "roomId"
        private const val EXTRA_RUID = "ruid"
        private const val EXTRA_AREA_PARENT_ID = "areaParentId"
        private const val EXTRA_AREA_ID = "areaId"

        @Suppress("DEPRECATION")
        private fun readKind(intent: Intent): Kind =
            Kind.values().getOrElse(intent.getIntExtra(EXTRA_KIND, 0)) { Kind.GUARD }

        private fun titleRes(kind: Kind): Int = when (kind) {
            Kind.GUARD -> R.string.live_rank_guard
            Kind.FANS -> R.string.live_rank_fans
            Kind.GIFT -> R.string.live_rank_gift
        }

        fun intentOf(
            context: Context,
            kind: Kind,
            roomId: Long,
            ruid: Long,
            areaParentId: Int,
            areaId: Int,
        ): Intent = Intent(context, LiveRankActivity::class.java)
            .putExtra(EXTRA_KIND, kind.ordinal)
            .putExtra(EXTRA_ROOM_ID, roomId)
            .putExtra(EXTRA_RUID, ruid)
            .putExtra(EXTRA_AREA_PARENT_ID, areaParentId)
            .putExtra(EXTRA_AREA_ID, areaId)
    }
}
