package com.RobinNotBad.BiliClient.activity.video

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshMainActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.RecommendApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.RecommendPagePolicy
import com.RobinNotBad.BiliClient.util.RecommendPagePolicy.FIRST_PAGE

//推荐页面
//2023-07-13
//26.11.06 批次 3（task-16）：补上分页游标（ps + fresh_idx），
//否则「参数不变 → 服务端返回同一批」会让下拉加载更多变成重复追加。

class RecommendActivity : RefreshMainActivity() {

    private var videoCardList: MutableList<VideoCard>? = null
    private var videoCardAdapter: VideoCardAdapter? = null
    private var firstRefresh = true
    private var freshType = RecommendPagePolicy.DEFAULT_FRESH_TYPE
    private val loadedBvids = mutableSetOf<String>()

    /** 下一页要请求的页码（对应接口的 fresh_idx）；下拉刷新时归 1。 */
    private var nextPage = FIRST_PAGE

    /** 正在请求时不再接受新的加载请求，避免快速滑动把同一页连请求两次。 */
    @Volatile
    private var loading = false

    /**
     * 刷新代数：刷新/重进页面时 +1，旧请求的回调发现代数不匹配就丢弃。
     *
     * 场景：下拉刷新时上一次「加载更多」还没回来，两者都会写列表；
     * 不判代数就会出现「刷新后的第 1 页」被「旧的第 N 页」追加在后面。
     */
    @Volatile
    private var loadGeneration = 0

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setMenuClick()

        setOnRefreshListener { refreshRecommend() }
        setOnLoadMoreListener { addRecommend() }
        // 空数据时可点击重试，而不是只能退出重进
        setOnEmptyRetry { refreshRecommend() }

        setPageName(UiText.of("推荐"))

        recyclerView.setHasFixedSize(true)

        // 教程（推荐页引导 + 主题说明）改由 BaseActivity 按 Tutorials 注册表集中触发

        refreshRecommend()
    }

    // 26.10.04 批次 3（B5）：本页早已改用 notifyItemRangeRemoved/Inserted，
    // 原来的 @SuppressLint("NotifyDataSetChanged") 与两处 Log.e("debug") 都是残留，已清掉。
    private fun refreshRecommend() {
        freshType = RecommendPagePolicy.DEFAULT_FRESH_TYPE
        nextPage = FIRST_PAGE
        loadedBvids.clear()
        // 刷新等于从第 1 页重新开始：解除「没有更多了」，否则刷完还是翻不动
        bottom = false
        ++loadGeneration
        // 上一轮请求即使还在飞，也已经被代数判据作废了；这里必须清掉 loading，
        // 否则刷新会被当成「正在加载」直接丢掉，转圈再也停不下来
        loading = false
        // 列表实例必须复用：VideoCardAdapter 持有的是同一个 list 引用，换实例会让 adapter 指向旧数据
        if (videoCardList == null) videoCardList = ArrayList()
        val last = videoCardList!!.size
        if (last > 0) {
            videoCardList!!.clear()
            videoCardAdapter?.notifyItemRangeRemoved(0, last)
        }

        addRecommend()
    }

    private fun addRecommend() {
        if (loading) return
        loading = true
        val generation = loadGeneration
        val requestPage = nextPage
        val pageSize = PerformanceManager.getPageSize()
        val requestFreshType = freshType
        freshType = if (freshType == 3) 4 else 3
        CenterThreadPool.run {
            try {
                val list = ArrayList<VideoCard>()
                RecommendApi.getRecommend(list, requestFreshType, requestPage, pageSize)

                runOnUiThread {
                    if (generation != loadGeneration) return@runOnUiThread
                    // 按 bvid 去重：服务端偶尔会给回已出现过的卡片（翻页边界/推荐池重叠）
                    val newItems = RecommendPagePolicy.filterNewCards(list, loadedBvids)
                    if (newItems.isEmpty()) {
                        // 去重后没有任何新内容：这一页到头了，别再让父类继续触发加载
                        bottom = true
                        loading = false
                        setRefreshing(false)
                        // 首屏就拉不到内容时给出空态，否则用户只看到"转圈消失 + 一片空白"
                        if (videoCardList!!.isEmpty()) showEmptyView()
                        return@runOnUiThread
                    }
                    hideEmptyView()
                    nextPage = RecommendPagePolicy.nextPage(requestPage)
                    videoCardList!!.addAll(newItems)
                    if (firstRefresh) {
                        firstRefresh = false
                        videoCardAdapter = VideoCardAdapter(this, videoCardList!!)
                        setAdapter(videoCardAdapter!!)
                    } else {
                        videoCardAdapter!!.notifyItemRangeInserted(videoCardList!!.size - newItems.size, newItems.size)
                    }
                    // 本页不足一页（或去重后没新增）说明没有下一页了；
                    // 实测深翻不会返回空页，只能靠这个判据，等空页会无限翻下去。
                    if (RecommendPagePolicy.shouldStop(list.size, pageSize, newItems.size)) {
                        bottom = true
                    }
                    loading = false
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (generation != loadGeneration) return@runOnUiThread
                    loading = false
                    loadFail(e)
                }
            }
        }
    }
}
