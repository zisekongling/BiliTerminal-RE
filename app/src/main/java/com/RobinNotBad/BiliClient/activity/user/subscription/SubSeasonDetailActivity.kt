package com.RobinNotBad.BiliClient.activity.user.subscription

import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.SubscriptionApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalContext

/**
 * 订阅的合集详情：一个 season_id 下的视频列表。
 *
 * <p>为什么不是 `CollectionInfoActivity`：那个页面读的是「视频所属的 UP 主合集」
 * （`ugc_season`，从 aid 反查），而订阅的合集是独立的 season_id，
 * 只能走 [SubscriptionApi.getSeasonVideos]（`x/space/fav/season/list`）。
 *
 * <p>翻页判定优先用接口给的 `info.media_count`：到底了就不再发请求；
 * 接口没给总数（返回 0）时退化成「本页不满 20 条即到底」。
 */
class SubSeasonDetailActivity : RefreshListActivity() {

    private var adapter: VideoCardAdapter? = null
    private var videoList = ArrayList<VideoCard>()
    private var seasonId = 0L
    private var total = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        seasonId = intent.getLongExtra("seasonId", 0)
        val title = intent.getStringExtra("title").orEmpty()
        total = intent.getIntExtra("mediaCount", 0)
        setPageName(title.ifEmpty { getString(R.string.pagename_subscription_season) })

        if (seasonId == 0L) {
            showEmptyView()
            setRefreshing(false)
            return
        }

        setOnEmptyRetry { loadFirstPage() }
        setOnRefreshListener { loadFirstPage() }
        setOnLoadMoreListener { continueLoading(it) }
        loadFirstPage()
    }

    private fun loadFirstPage() {
        setRefreshing(true)
        CenterThreadPool.run {
            try {
                val newList = ArrayList<VideoCard>()
                val code = fetch(1, newList)
                if (isFinishing || isDestroyed) return@run
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (code != 0) {
                        MsgUtil.showMsg(errorText(code))
                        showEmptyView()
                        setRefreshing(false)
                        return@runOnUiThread
                    }
                    page = 1
                    videoList = newList
                    bottom = isEnd(newList.size)
                    // 与收藏夹列表一致：换整份 list + 新 adapter，避免挂着的旧 list 被后台改写
                    val newAdapter = VideoCardAdapter(this@SubSeasonDetailActivity, videoList)
                    newAdapter.onItemClickListener = { _, card ->
                        TerminalContext.getInstance().enterVideoDetailPage(this, card.aid, card.bvid, "video")
                    }
                    adapter = newAdapter
                    setAdapter(newAdapter)
                    if (videoList.isEmpty()) showEmptyView() else hideEmptyView()
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                if (isFinishing || isDestroyed) return@run
                loadFail(e)
            }
        }
    }

    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val added = ArrayList<VideoCard>()
                val code = fetch(page, added)
                if (isFinishing || isDestroyed) return@run
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (code != 0) {
                        this.page--
                        onLoadComplete()
                        setRefreshing(false)
                        MsgUtil.showMsg(errorText(code))
                        return@runOnUiThread
                    }
                    val lastSize = videoList.size
                    videoList.addAll(added)
                    bottom = isEnd(videoList.size)
                    if (added.isNotEmpty()) adapter?.notifyItemRangeInserted(lastSize, added.size)
                    onLoadComplete()
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                if (isFinishing || isDestroyed) return@run
                loadFail(e)
            }
        }
    }

    /** 拉一页，顺带把接口返回的总集数同步到 [total]。 */
    private fun fetch(page: Int, out: ArrayList<VideoCard>): Int {
        val result = SubscriptionApi.getSeasonVideos(seasonId, page, out)
        if (result.first == 0 && result.second > 0) total = result.second
        return result.first
    }

    /**
     * 是否到底。
     *
     * 有总数时按总数判断（接口可能把本页填满但总数已到）；没有总数时按「本页不满一页」判断。
     * 两个判据都要留 20 的余量判断：`media_count` 与 `medias` 偶尔会不一致（失效视频被剔除）。
     */
    private fun isEnd(size: Int): Boolean {
        if (total > 0) return size >= total
        // 空列表必须判到底，否则滚动监听会一直触发翻页（0 % 20 == 0）
        return size == 0 || size % SubscriptionApi.PS != 0
    }

    private fun errorText(code: Int): String = when (code) {
        -101 -> getString(R.string.msg_subscription_need_login)
        -111 -> getString(R.string.msg_subscription_csrf_invalid)
        -400 -> getString(R.string.msg_subscription_bad_request)
        11010 -> getString(R.string.msg_subscription_content_gone)
        else -> getString(R.string.msg_subscription_failed_with_code, code)
    }
}
