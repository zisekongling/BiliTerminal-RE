package com.RobinNotBad.BiliClient.activity.video

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshMainActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.FeedbackApi
import com.RobinNotBad.BiliClient.api.RecommendApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.RecommendPagePolicy
import com.RobinNotBad.BiliClient.util.RecommendPagePolicy.FIRST_PAGE

//推荐页面
//2023-07-13
//26.11.06 批次 3（task-16）：补上分页游标（ps + fresh_idx），
//否则「参数不变 → 服务端返回同一批」会让下拉加载更多变成重复追加。
//26.11.07：游标保留，但本页按**无底流**处理——不再判「到底」、不再置 bottom。
//原因：26.11.06 的到底判据拿「过滤掉广告/番剧卡之后的可用条数」跟「请求页大小」比，
//一页 20 条里只要有一张非 av 卡就判成到底；父类收到 bottom = true 后会弹
//「没有更多了」，并让滚动监听里的 !bottom 守卫永久拦住翻页。

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
        // 兜底：本页不判到底，但父类的 bottom 可能是上一版本/别处留下的
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
                    // 游标无条件前进：即使这一页全是已出现过的卡片，下一页也要换一批，
                    // 否则会永远卡在同一个 fresh_idx 上反复拿到同一批内容。
                    nextPage = RecommendPagePolicy.nextPage(requestPage)
                    // 按 bvid 去重：服务端偶尔会给回已出现过的卡片（翻页边界/推荐池重叠）
                    val newItems = RecommendPagePolicy.filterNewCards(list, loadedBvids)
                    if (newItems.isEmpty()) {
                        // 去重后没有新内容：这一页大概率是回绕到旧内容了。
                        // 26.11.07 起**不**把它当成「到底」——游标已经在上面前进过，
                        // 下次滑到底会自动请求下一批，既不会卡死也不会弹「没有更多了」。
                        loading = false
                        setRefreshing(false)
                        // 首屏就拉不到内容时给出空态，否则用户只看到"转圈消失 + 一片空白"
                        if (videoCardList!!.isEmpty()) showEmptyView()
                        return@runOnUiThread
                    }
                    hideEmptyView()
                    videoCardList!!.addAll(newItems)
                    if (firstRefresh) {
                        firstRefresh = false
                        videoCardAdapter = VideoCardAdapter(this, videoCardList!!)
                        // 长按菜单里的「减少推荐（不感兴趣）」：菜单只在回调非空时才显示该项，
                        // 所以本页必须注册（其它页面不注册就没有这一项）
                        videoCardAdapter!!.onReduceRecommend = { card -> reduceRecommend(card) }
                        setAdapter(videoCardAdapter!!)
                    } else {
                        videoCardAdapter!!.notifyItemRangeInserted(videoCardList!!.size - newItems.size, newItems.size)
                    }
                    // 这里刻意不判「到底」：接口深翻（fresh_idx≈120）仍是满页、没有空页信号，
                    // 而推荐流本来就没有尽头，置 bottom = true 只会永久掐断翻页并弹「没有更多了」。
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

    /**
     * 长按菜单里的「减少推荐（不感兴趣）」：调 [FeedbackApi.dislike]，成功后把这条从本页列表移除。
     *
     * <p>用 [FeedbackApi.REASON_CONTENT]（对内容不感兴趣）；「不想看此 UP 主」是 4，
     * 本页暂时不提供该入口。参数里 aid 与 trackId/upMid 都来自 [RecommendApi] 填好的卡片。
     *
     * <p>服务端就算回了 code=0，卡片也可能已经被下拉刷新换掉了：所以移除前按「对象仍在列表里」
     * 重新取下标（[MutableList.indexOf] 对同一实例生效），取不到（-1）就只提示成功、不动列表，
     * 绝不按旧下标调 notifyItemRemoved 硬删。
     */
    private fun reduceRecommend(card: VideoCard) {
        CenterThreadPool.run {
            try {
                val code = FeedbackApi.dislike(card.aid, card.upMid, card.trackId, FeedbackApi.REASON_CONTENT)
                CenterThreadPool.runOnUiThread {
                    if (code == 0) {
                        val list = videoCardList
                        if (list != null) {
                            val index = list.indexOf(card)
                            if (index in 0 until list.size) {
                                list.removeAt(index)
                                videoCardAdapter?.notifyItemRemoved(index)
                            }
                        }
                        MsgUtil.showMsg(getString(R.string.msg_video_card_menu_reduce_ok))
                    } else {
                        MsgUtil.showMsg(getString(R.string.msg_video_card_menu_reduce_failed, code.toString()))
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { MsgUtil.err(e) }
            }
        }
    }
}
