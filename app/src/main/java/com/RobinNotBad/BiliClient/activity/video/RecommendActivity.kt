package com.RobinNotBad.BiliClient.activity.video

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshMainActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.AppRecommendApi
import com.RobinNotBad.BiliClient.api.FeedbackApi
import com.RobinNotBad.BiliClient.api.RecommendApi
import com.RobinNotBad.BiliClient.api.RecommendTasteApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.RecommendPagePolicy
import com.RobinNotBad.BiliClient.util.RecommendPagePolicy.FIRST_PAGE
import com.RobinNotBad.BiliClient.util.recommend.BlockedAuthorStore
import com.RobinNotBad.BiliClient.util.recommend.RecommendFilter
import com.RobinNotBad.BiliClient.util.recommend.RecommendMixer
import com.RobinNotBad.BiliClient.util.recommend.RecommendPrefs
import com.RobinNotBad.BiliClient.util.recommend.RecommendRerank
import com.RobinNotBad.BiliClient.util.recommend.RecommendSource
import com.RobinNotBad.BiliClient.util.recommend.RecommendSourcePolicy
import com.RobinNotBad.BiliClient.util.recommend.RecommendTasteProfile
import com.RobinNotBad.BiliClient.util.recommend.RecommendTasteSignal
import com.RobinNotBad.BiliClient.util.recommend.RecommendTasteStore
import kotlin.random.Random

//推荐页面
//2023-07-13
//26.11.06 批次 3（task-16）：补上分页游标（ps + fresh_idx），
//否则「参数不变 → 服务端返回同一批」会让下拉加载更多变成重复追加。
//26.11.21（反馈 #248）：下拉刷新不再把 fresh_idx 归 1——同一进程里 uniq_id 不变，
//参数一样就返回同一批（实测 30/30 条 bvid 与顺序一致），刷新等于白刷。
//改成把游标往后跳一段（RecommendPagePolicy.refreshPage）；同时按 bvid +（UP、标题）双重判重。
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

    /** 「同 UP + 同标题」的二次判重键，见 [RecommendPagePolicy.titleKey]。 */
    private val loadedTitles = mutableSetOf<String>()

    /** 下一页要请求的页码（对应接口的 fresh_idx）；下拉刷新时归 1。 */
    private var nextPage = FIRST_PAGE

    /**
     * APP 推荐流（app.bilibili.com）的游标：接口是「上一批最后一条的 idx」，跟网页源的
     * fresh_idx 完全是两套东西，所以单独存一份。下拉刷新时归 [AppRecommendApi.CURSOR_START]。
     */
    @Volatile
    private var appCursor: String = AppRecommendApi.CURSOR_START

    /**
     * 当前列表最后几张卡片的 upMid（滑动窗口，最多 [RecommendRerank.RECENT_WINDOW] 个）。
     *
     * 只在开了「X 式重排」时有用：重排是**一批一批**做的，靠它把「上一批最后一个 UP」
     * 带到下一批的惩罚里，否则同一批末尾与下一批开头仍可能连着同一个 UP。
     */
    private val recentUpMids = ArrayDeque<Long>()

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
        // 26.11.21（#248）：刷新要换一批，就不能退回第 1 页——接口对相同参数返回同一批，
        // 刷新退回 1 等于把刚才那批再拿一遍。往后跳一段随机步数。
        nextPage = RecommendPagePolicy.refreshPage(
            nextPage,
            Random.nextInt(1, RecommendPagePolicy.REFRESH_STEP_MAX + 1)
        )
        loadedBvids.clear()
        loadedTitles.clear()
        // APP 源从首页重新开始：它退回游标 0 才叫「换一批」，接着往下翻只会接上刚才那批
        appCursor = AppRecommendApi.CURSOR_START
        recentUpMids.clear()
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
        // 来源与重排开关在请求前各取一次：中途改设置不该让「这一批按新规则、上一批按旧规则」
        val source = RecommendSourcePolicy.resolve(AppRecommendApi.hasCredential())
        val rerank = RecommendPrefs.rerankEnabled()
        // 「上一批最后几张的 UP」必须在追加前拍快照，追加后窗口就变了
        val recent = ArrayList<Long>(recentUpMids)
        CenterThreadPool.run {
            try {
                val list = postProcess(fetchBatch(source, requestFreshType, requestPage, pageSize), rerank, recent)

                runOnUiThread {
                    if (generation != loadGeneration) return@runOnUiThread
                    // 游标无条件前进：即使这一页全是已出现过的卡片，下一页也要换一批，
                    // 否则会永远卡在同一个 fresh_idx 上反复拿到同一批内容。
                    nextPage = RecommendPagePolicy.nextPage(requestPage)
                    // 按 bvid 去重：服务端偶尔会给回已出现过的卡片（翻页边界/推荐池重叠）
                    val newItems = RecommendPagePolicy.filterNewCards(list, loadedBvids, loadedTitles)
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
                        // 「屏蔽作者」：菜单里点完名单就落库了，这里只把本页已显示的该作者卡片清掉
                        videoCardAdapter!!.onBlockAuthor = { card -> blockAuthor(card) }
                        setAdapter(videoCardAdapter!!)
                    } else {
                        videoCardAdapter!!.notifyItemRangeInserted(videoCardList!!.size - newItems.size, newItems.size)
                    }
                    rememberRecentUpMids()
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
     * 按「推荐源」设置拉一批候选。
     *
     * WEB 走网页推荐（[RecommendApi]）；APP 走 app.bilibili.com 的推荐流（[AppRecommendApi]，
     * 需要 access_key）；MIXED 两路都拉再按 [RecommendMixer.WEB_PER_APP]（2 网页 : 1 APP）交替混合。
     *
     * 没有 access_key 的降级不在这一层做：[RecommendSourcePolicy.resolve] 已经把 APP/BOTH 统一
     * 判成 WEB，所以这里可以当「凭据一定在」来写。
     *
     * APP 源失败（凭据过期、风控、接口改版）不报错而是退回网页源——用户选了 APP 源却看到一块
     * 空白比看到网页推荐更糟，而推荐页本身没有「失败」的语义，只有「有没有内容」。
     */
    private fun fetchBatch(
        source: RecommendSource,
        freshType: Int,
        page: Int,
        pageSize: Int
    ): List<VideoCard> = when (source) {
        RecommendSource.MIXED -> fetchMixed(freshType, page, pageSize)
        RecommendSource.APP -> fetchApp().ifEmpty { fetchWeb(freshType, page, pageSize) }
        RecommendSource.WEB -> fetchWeb(freshType, page, pageSize)
    }

    private fun fetchWeb(freshType: Int, page: Int, pageSize: Int): List<VideoCard> {
        val list = ArrayList<VideoCard>()
        RecommendApi.getRecommend(list, freshType, page, pageSize)
        return list
    }

    /** APP 源一批；失败只记日志并返回空，由调用方决定退回网页源还是报错。 */
    private fun fetchApp(): List<VideoCard> {
        val list = ArrayList<VideoCard>()
        try {
            appCursor = AppRecommendApi.getRecommend(list, appCursor)
        } catch (e: Exception) {
            Logu.w("APP 推荐流拉取失败：" + e)
            list.clear()
        }
        return list
    }

    /**
     * 混合源：两路各拉一批再交替合流。
     *
     * 只有「网页源报错 **且** APP 源也没内容」才把异常抛出去，让页面走既有的 loadFail
     * （提示、重试都是现成的）；任何一路有内容就照常出列表——推荐流少一半也比空屏好。
     */
    private fun fetchMixed(freshType: Int, page: Int, pageSize: Int): List<VideoCard> {
        var webError: Exception? = null
        val web: List<VideoCard>? = try {
            fetchWeb(freshType, page, pageSize)
        } catch (e: Exception) {
            webError = e
            Logu.w("混合源的网页一路拉取失败：" + e)
            null
        }
        val app = fetchApp()
        if (webError != null && app.isEmpty()) throw webError!!
        return RecommendMixer.mix(web, app)
    }

    /**
     * 混完之后的统一后处理：屏蔽过滤 → X 式重排（关着时只做前一步）。
     *
     * 顺序不能反：先屏蔽再重排，被屏蔽的 UP 才不会因为「常看」拿到加分又被顶回前面；
     * 重排内部还会用画像里的 watchedBvids 剔掉已看过的稿件。
     */
    private fun postProcess(cards: List<VideoCard>, rerank: Boolean, recent: List<Long>): List<VideoCard> {
        val visible = RecommendFilter.withoutBlocked(cards, BlockedAuthorStore.mids())
        if (!rerank || visible.isEmpty()) return visible
        return RecommendRerank.rerank(visible, tasteSignal(), recent)
    }

    /**
     * 取画像信号；快照过期（或从没建过）就现场拉一次历史重建。
     *
     * 未登录时 [RecommendTasteApi] 返回空列表，结果就是「没有画像分，只做先验 + 多样性」，
     * 比直接放弃重排温和。构建失败不打扰用户（他只是开了个开关），只留一条日志。
     */
    private fun tasteSignal(): RecommendTasteSignal {
        val now = System.currentTimeMillis()
        if (!RecommendTasteStore.hasFreshSnapshot(now)) {
            try {
                val watched = HashSet<String>()
                val entries = RecommendTasteApi.fetchTasteEntries(watched)
                if (entries.isNotEmpty()) {
                    RecommendTasteStore.save(RecommendTasteProfile.build(entries), watched, now)
                }
            } catch (e: Exception) {
                Logu.w("推荐画像构建失败，本批只做先验与多样性：" + e)
            }
        }
        return RecommendTasteStore.signal(now)
    }

    /** 把当前列表末尾几张卡片的 upMid 记进滑动窗口（重排跨批次去重用）。 */
    private fun rememberRecentUpMids() {
        val list = videoCardList ?: return
        recentUpMids.clear()
        var from = list.size - RecommendRerank.RECENT_WINDOW
        if (from < 0) from = 0
        for (i in from until list.size) {
            val upMid = list[i].upMid
            if (upMid != 0L) recentUpMids.addLast(upMid)
        }
    }

    /**
     * 长按菜单里的「屏蔽作者」：名单与「不想看此 UP 主」都在 [com.RobinNotBad.BiliClient.adapter.video.VideoCardMenu]
     * 里做完了，这里只负责把该作者**已经显示出来**的卡片从本页移除（一批里可能有好几张）。
     *
     * 从后往前删：删掉后面的不影响前面的下标，逐张 notifyItemRemoved 都指向当时正确的位置；
     * 不用 notifyDataSetChanged 是为了保住卡片的插入/删除动画与滚动位置。
     */
    private fun blockAuthor(card: VideoCard) {
        val list = videoCardList ?: return
        val upMid = card.upMid
        if (upMid == 0L) return
        for (i in list.indices.reversed()) {
            if (list[i].upMid == upMid) {
                list.removeAt(i)
                videoCardAdapter?.notifyItemRemoved(i)
            }
        }
        rememberRecentUpMids()
    }

    /**
     * 长按菜单里的「减少推荐（不感兴趣）」：调 [FeedbackApi.dislike]，成功后把这条从本页列表移除。
     *
     * <p>用 [FeedbackApi.REASON_CONTENT]（对内容不感兴趣）；「不想看此 UP 主」是 4，
     * 走的是长按菜单里的「屏蔽作者」（[blockAuthor]）。参数里 aid 与 trackId/upMid 都来自
     * [RecommendApi]（网页源）或 [AppRecommendApi]（APP 源）填好的卡片。
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
