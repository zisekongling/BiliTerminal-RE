package com.RobinNotBad.BiliClient.activity.search

import android.os.Bundle
import android.view.View

import androidx.annotation.NonNull

import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.SearchApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.OptionalFeatures

import org.json.JSONArray
import org.json.JSONObject

// 搜索结果-番剧页
class SearchBangumiFragment : SearchFragment() {
    private var bangumiCardList = ArrayList<VideoCard>()
    private var bangumiCardAdapter: VideoCardAdapter? = null

    companion object {
        fun newInstance(): SearchBangumiFragment {
            return SearchBangumiFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(@NonNull view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bangumiCardList = ArrayList()
        bangumiCardAdapter = VideoCardAdapter(requireContext(), bangumiCardList)
        setAdapter(bangumiCardAdapter!!)

        setOnRefreshListener { refreshInternal() }
        setOnLoadMoreListener { page -> continueLoading(page) }
    }

    /**
     * 取一页番剧搜索结果。
     *
     * <p>26.11.05 提速：B 站结果先渲染，Bangumi 独有条目（可选私有功能）**后台补**、回来再追加。
     * 以前两者串在同一个后台任务里，`appendBangumiSearchExtras` 里的搜索 + 最多 8 条详情补全
     * 会把整个页面的首屏拖到 8~10s（手机实测），而它对用户只是「末尾多几张 B 站没有的番剧」。
     */
    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                // 番剧走独立的 search_type=media_bangumi，data.result 就是一个番剧条目数组
                val result = SearchApi.searchType(keyword, page, "media_bangumi")
                // 同一个接口不同 search_type 的 result 形态并不统一（本项目 SearchLiveFragment 里
                // live 就是 {"live_room":[...]}），所以不用 as 强转 —— 强转失败会抛
                // ClassCastException 把整页打成"加载失败"；这里逐个 is 兜住，拿不到数组就当到底了
                val resultArray: JSONArray? = when (result) {
                    is JSONArray -> result
                    is JSONObject -> result.optJSONArray("media_bangumi")
                    else -> null
                }
                val list = ArrayList<VideoCard>()
                if (resultArray != null) SearchApi.getBangumiFromSearchResult(resultArray, list)
                // 第一步：先把 B 站自己的结果贴出来，并立即停掉下拉/刷新圈——BGM 补充只影响
                // 列表末尾多几张卡片，不该让用户一直看着转圈。
                if (list.isNotEmpty()) appendToAdapter(page, list)
                CenterThreadPool.runOnUiThread { if (isAdded) setRefreshing(false) }

                // 第二步：后台补 Bangumi 独有条目（B 站没有的番剧）；翻页不追加，避免越滚越多。
                // 注意：B 站搜不到时 data 里连 result 字段都没有（numResults=0），
                // 所以这一步必须在 resultArray 判空之外——否则「B 站没有、Bangumi 有」的番剧
                // 会整页空白。具体实现在可选私有功能里；不在场时什么也不加。
                var extrasCount = 0
                val ctx = context
                if (page == 1 && ctx != null) {
                    // 私有实现用传入的已有卡片做去重与封面借用，所以先把 B 站结果拷一份进去
                    val seed = ArrayList<VideoCard>(list)
                    try {
                        OptionalFeatures.appendBangumiSearchExtras(ctx, keyword.orEmpty(), seed)
                    } catch (e: Exception) {
                        Logu.w("SearchBangumi", "Bangumi 补充失败：" + e.message)
                    }
                    if (seed.size > list.size) {
                        val extras = ArrayList(seed.subList(list.size, seed.size))
                        extrasCount = extras.size
                        appendToAdapter(page, extras)
                    }
                }
                if (list.isEmpty() && extrasCount == 0) bottom = true
            } catch (e: Exception) {
                loadFail(e)
            }
            // 无论成功失败都必须复位下拉刷新，否则刷新圈会一直转（见 docs/archive/review/fork-fix-worklog.md P18）
            setRefreshing(false)
        }
    }

    /** 把一批卡片追加进列表（主线程）；分两次调用，保证 B 站结果先上屏。 */
    private fun appendToAdapter(page: Int, items: List<VideoCard>) {
        CenterThreadPool.runOnUiThread {
            if (!isAdded || view == null) return@runOnUiThread
            if (page == 1) showEmptyView(false)
            val lastSize = bangumiCardList.size
            bangumiCardList.addAll(items)
            bangumiCardAdapter?.notifyItemRangeInserted(lastSize, items.size)
        }
    }

    override fun refreshInternal() {
        CenterThreadPool.runOnUiThread {
            if (!isAdded || view == null) return@runOnUiThread
            page = 1
            if (this.bangumiCardAdapter == null)
                this.bangumiCardAdapter = VideoCardAdapter(this.requireContext(), this.bangumiCardList)
            val size_old = this.bangumiCardList.size
            this.bangumiCardList.clear()
            if (size_old != 0) this.bangumiCardAdapter!!.notifyItemRangeRemoved(0, size_old)
            CenterThreadPool.run { continueLoading(page) }
        }
    }
}
