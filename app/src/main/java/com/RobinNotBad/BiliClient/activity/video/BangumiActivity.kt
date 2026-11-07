package com.RobinNotBad.BiliClient.activity.video

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.BangumiApi
import com.RobinNotBad.BiliClient.api.BangumiIndexApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.ui.widget.RotaryRecyclerView
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener

/**
 * 番剧分区页：**推荐 / 追番 / 索引（动态筛选）** 三个页签共用一页。
 *
 * 三个页签的数据源与分页规则都不一样，所以没有继承 `RefreshMainActivity`（那套基类只服务
 * 单个列表），而是自己管三份「页码 + 到底 + 在飞 + 代际」状态：
 *
 * | 页签 | 数据源 | 分页 |
 * | --- | --- | --- |
 * | 推荐 | [BangumiIndexApi.getIndex]，排序固定「最多追番」、无筛选 | `page` 递增，`has_next` 判到底 |
 * | 追番 | [BangumiApi.getFollowingList] | `page` 递增，返回 1 表示到底 |
 * | 索引 | [BangumiIndexApi.getIndex]，条件来自筛选弹窗 | 同推荐 |
 *
 * 三处「代际」(`generation`) 是必须的：快速切页签 / 连点筛选项时，旧请求回来会把新条件的
 * 结果覆盖掉（表现为「选了日本却显示全部」），代际不匹配的回调直接丢弃。
 *
 * 筛选刻意**不做整排 chip**：`condition` 接口下发 9 组条件（风格一组就有 45 个选项），
 * 手表宽度下横滑到底也看不全，且会吃掉列表本来就不多的竖向空间。改成一行
 * 「筛选：当前条件」+ [TerminalDialog] 分组单选，与本项目「搜索排序」同一套交互。
 */
class BangumiActivity : InstanceActivity() {

    private companion object {
        const val TAB_RECOMMEND = 0
        const val TAB_FOLLOWING = 1
        const val TAB_INDEX = 2
        const val TAB_COUNT = 3

        /** 「全部」在 condition 接口里的通用 keyword。 */
        const val FILTER_ALL = "-1"
    }

    private lateinit var tabs: Array<TextView>
    private lateinit var refreshes: Array<SwipeRefreshLayout>
    private lateinit var recyclers: Array<RotaryRecyclerView>
    private lateinit var emptyTip: TextView
    private lateinit var filterEntry: TextView

    private var currentTab = TAB_RECOMMEND
    private val loaded = BooleanArray(TAB_COUNT)

    // ===== 推荐 =====
    private val recommendList = ArrayList<VideoCard>()
    private var recommendAdapter: VideoCardAdapter? = null
    private var recommendPage = 1
    private var recommendEnd = false
    private var recommendLoading = false
    private var recommendError = false
    private var recommendGeneration = 0

    // ===== 追番 =====
    private val followingList = ArrayList<VideoCard>()
    private var followingAdapter: VideoCardAdapter? = null
    private var followingPage = 1
    private var followingEnd = false
    private var followingLoading = false
    private var followingError = false
    private var followingNotLogin = false
    private var followingGeneration = 0

    // ===== 索引 =====
    private val indexList = ArrayList<VideoCard>()
    private var indexAdapter: VideoCardAdapter? = null
    private var indexPage = 1
    private var indexEnd = false
    private var indexLoading = false
    private var indexError = false
    private var indexGeneration = 0

    /** condition 接口下发的排序项 / 筛选组；为 null 表示还没拿到（或失败）。 */
    private var indexCondition: BangumiIndexApi.Condition? = null

    /** 选中排序 field，空串 = 用接口默认值。 */
    private var selectedOrder = ""

    /** 选中的筛选：field → keyword（"-1" 等于不筛）。 */
    private val selectedFilters = LinkedHashMap<String, String>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bangumi)
        setMenuClick()

        setPageName(getString(R.string.pagename_bangumi))

        tabs = arrayOf(
            findViewById(R.id.tabRecommend),
            findViewById(R.id.tabFollowing),
            findViewById(R.id.tabIndex)
        )
        refreshes = arrayOf(
            findViewById(R.id.refreshRecommend),
            findViewById(R.id.refreshFollowing),
            findViewById(R.id.refreshIndex)
        )
        recyclers = arrayOf(
            findViewById(R.id.recyclerRecommend),
            findViewById(R.id.recyclerFollowing),
            findViewById(R.id.recyclerIndex)
        )
        emptyTip = findViewById(R.id.emptyTip)
        filterEntry = findViewById(R.id.filterEntry)

        for (i in 0 until TAB_COUNT) {
            // 下拉刷新转圈统一到主题主色（SwipeRefreshLayout 默认色与主题无关）
            refreshes[i].setColorSchemeColors(ColorScheme.PRIMARY)
            refreshes[i].isRefreshing = false
            recyclers[i].layoutManager = CustomLinearManager(this)
            recyclers[i].setHasFixedSize(true)
            ImageAutoLoadScrollListener.installIfEnabled(recyclers[i])
            val tab = i
            tabs[i].setOnClickListener { switchTab(tab) }
        }

        refreshes[TAB_RECOMMEND].setOnRefreshListener { loadRecommend(true) }
        refreshes[TAB_FOLLOWING].setOnRefreshListener { loadFollowing(true) }
        refreshes[TAB_INDEX].setOnRefreshListener { loadIndex(true) }

        bindLoadMore(recyclers[TAB_RECOMMEND]) { loadRecommend(false) }
        bindLoadMore(recyclers[TAB_FOLLOWING]) { loadFollowing(false) }
        bindLoadMore(recyclers[TAB_INDEX]) { loadIndex(false) }

        filterEntry.setOnClickListener { openFilterDialog() }

        // 筛选条件先拉一次：失败不阻塞索引页（索引仍按「全部 + 默认排序」可用），
        // 只是筛选入口打开时给出「稍后重试」的提示。
        loadCondition()

        switchTab(TAB_RECOMMEND)
    }

    /**
     * 从登录页回来时，「追番」页此前停在「未登录」空态，登录成功后要能自己恢复，
     * 而不是逼用户手动下拉刷新（手表上很容易漏掉这一步）。
     */
    override fun onResume() {
        super.onResume()
        if (followingNotLogin && SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L) != 0L) {
            followingNotLogin = false
            loaded[TAB_FOLLOWING] = false
            if (currentTab == TAB_FOLLOWING) loadFollowing(true)
        }
    }

    /**
     * 触底续拉：`dy > 0` 才触发（往回滚不该再请求），离底部 3 项内就预取。
     * 重入、到底、代际都在各自的 load 方法里判，这里不重复。
     */
    private fun bindLoadMore(recycler: RecyclerView, loadMore: () -> Unit) {
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val manager = rv.layoutManager as? LinearLayoutManager ?: return
                if (manager.itemCount == 0) return
                if (manager.findLastVisibleItemPosition() >= manager.itemCount - 3) loadMore()
            }
        })
    }

    // ===================== 页签 =====================

    private fun switchTab(tab: Int) {
        currentTab = tab
        // 换页时先收起上一个页签留下的空态/失败态提示，避免「切过来先看到别人的报错」
        hideEmptyState()
        for (i in 0 until TAB_COUNT) {
            val selected = i == tab
            // 选中态：主题主色 + 加粗；未选中：正文次级灰。色值走 ColorScheme 而不是布局字面量，
            // 切主题 / 亮暗模式时与全应用一致。
            tabs[i].setTextColor(if (selected) ColorScheme.PRIMARY else ColorScheme.TEXT_SECONDARY)
            tabs[i].setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            refreshes[i].visibility = if (selected) View.VISIBLE else View.GONE
        }
        filterEntry.visibility = if (tab == TAB_INDEX) View.VISIBLE else View.GONE

        when (tab) {
            TAB_RECOMMEND -> if (!loaded[TAB_RECOMMEND]) loadRecommend(true) else renderEmpty()
            TAB_FOLLOWING -> if (!loaded[TAB_FOLLOWING]) loadFollowing(true) else renderEmpty()
            TAB_INDEX -> if (!loaded[TAB_INDEX]) loadIndex(true) else renderEmpty()
        }
        refreshFilterEntry()
    }

    // ===================== 空态 / 失败态 =====================

    /** 按当前页签的数据状态决定要不要显示提示块；`retry` 非空时提示可点重试。 */
    private fun setEmptyState(text: String, retry: (() -> Unit)?) {
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            emptyTip.text = text
            emptyTip.isClickable = retry != null
            emptyTip.setOnClickListener { retry?.invoke() }
            emptyTip.visibility = View.VISIBLE
        }
    }

    private fun hideEmptyState() {
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            emptyTip.visibility = View.GONE
        }
    }

    /**
     * 重建当前页签的空态。
     *
     * 只在「已加载完且确实没数据」或「加载失败」时显示：还在加载时显示提示块会跟转圈叠在一起。
     */
    private fun renderEmpty() {
        when (currentTab) {
            TAB_RECOMMEND -> when {
                recommendError && recommendList.isEmpty() ->
                    setEmptyState(getString(R.string.text_bangumi_empty_recommend)) { retryRecommend() }
                loaded[TAB_RECOMMEND] && recommendList.isEmpty() ->
                    setEmptyState(getString(R.string.text_bangumi_empty_recommend_empty), null)
                else -> hideEmptyState()
            }
            TAB_FOLLOWING -> when {
                followingNotLogin -> setEmptyState(
                    getString(R.string.text_bangumi_empty_following_login)
                ) { openLogin() }
                followingError && followingList.isEmpty() ->
                    setEmptyState(getString(R.string.text_bangumi_empty_following_failed)) { retryFollowing() }
                loaded[TAB_FOLLOWING] && followingList.isEmpty() ->
                    setEmptyState(getString(R.string.text_bangumi_empty_following), null)
                else -> hideEmptyState()
            }
            TAB_INDEX -> when {
                indexError && indexList.isEmpty() ->
                    setEmptyState(getString(R.string.text_bangumi_empty_index_failed)) { retryIndex() }
                loaded[TAB_INDEX] && indexList.isEmpty() ->
                    setEmptyState(getString(R.string.text_bangumi_empty_index), null)
                else -> hideEmptyState()
            }
            else -> hideEmptyState()
        }
    }

    private fun openLogin() {
        startActivity(android.content.Intent(this, LoginActivity::class.java))
    }

    private fun retryRecommend() {
        hideEmptyState()
        refreshes[TAB_RECOMMEND].isRefreshing = true
        loadRecommend(true)
    }

    private fun retryFollowing() {
        hideEmptyState()
        refreshes[TAB_FOLLOWING].isRefreshing = true
        loadFollowing(true)
    }

    private fun retryIndex() {
        hideEmptyState()
        refreshes[TAB_INDEX].isRefreshing = true
        loadIndex(true)
    }

    // ===================== 推荐 =====================

    @SuppressLint("NotifyDataSetChanged")
    private fun loadRecommend(reset: Boolean) {
        if (isFinishing) return
        if (recommendLoading) {
            refreshes[TAB_RECOMMEND].isRefreshing = false
            return
        }
        if (reset) {
            recommendPage = 1
            recommendEnd = false
        } else if (recommendEnd) {
            return
        }
        recommendLoading = true
        val generation = ++recommendGeneration
        val page = recommendPage
        if (reset) refreshes[TAB_RECOMMEND].isRefreshing = true

        CenterThreadPool.run {
            var cards: List<VideoCard>? = null
            var hasNext = false
            var error: Exception? = null
            try {
                // 推荐 = 索引接口 + 固定「最多追番」排序、不带任何筛选条件
                val result = BangumiIndexApi.getIndex(null, BangumiIndexApi.ORDER_MOST_FOLLOWED, page)
                cards = result.cards
                hasNext = result.hasNext
            } catch (e: Exception) {
                error = e
            }
            runOnUiThread {
                // 代际不匹配：这条响应已经过期（下拉刷新 / 重进页签），直接丢弃
                if (isFinishing || generation != recommendGeneration) return@runOnUiThread
                recommendLoading = false
                refreshes[TAB_RECOMMEND].isRefreshing = false
                val err = error
                if (err != null) {
                    recommendError = true
                    report(err)
                    MsgUtil.showMsgLong(getString(R.string.msg_bangumi_recommend_failed))
                    loaded[TAB_RECOMMEND] = true
                    renderEmpty()
                    return@runOnUiThread
                }
                recommendError = false
                val items = cards ?: emptyList()
                if (reset) recommendList.clear()
                recommendList.addAll(items)
                recommendPage = page + 1
                recommendEnd = !hasNext || items.isEmpty()
                loaded[TAB_RECOMMEND] = true
                if (recommendAdapter == null) {
                    recommendAdapter = VideoCardAdapter(this, recommendList)
                    recyclers[TAB_RECOMMEND].adapter = recommendAdapter
                } else {
                    recommendAdapter?.notifyDataSetChanged()
                }
                renderEmpty()
            }
        }
    }

    // ===================== 追番 =====================

    @SuppressLint("NotifyDataSetChanged")
    private fun loadFollowing(reset: Boolean) {
        if (isFinishing) return
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L) == 0L) {
            // 未登录：不请求接口（会拿到 code=-101 的失败），直接给可点去登录的空态
            followingNotLogin = true
            loaded[TAB_FOLLOWING] = true
            refreshes[TAB_FOLLOWING].isRefreshing = false
            if (currentTab == TAB_FOLLOWING) renderEmpty()
            return
        }
        followingNotLogin = false
        if (followingLoading) {
            refreshes[TAB_FOLLOWING].isRefreshing = false
            return
        }
        if (reset) {
            followingPage = 1
            followingEnd = false
        } else if (followingEnd) {
            return
        }
        followingLoading = true
        val generation = ++followingGeneration
        val page = followingPage
        if (reset) refreshes[TAB_FOLLOWING].isRefreshing = true

        CenterThreadPool.run {
            var cards: List<VideoCard>? = null
            var atEnd = false
            var error: Exception? = null
            try {
                val list = ArrayList<VideoCard>()
                atEnd = BangumiApi.getFollowingList(page, list) == 1
                cards = list
            } catch (e: Exception) {
                error = e
            }
            runOnUiThread {
                if (isFinishing || generation != followingGeneration) return@runOnUiThread
                followingLoading = false
                refreshes[TAB_FOLLOWING].isRefreshing = false
                val err = error
                if (err != null) {
                    followingError = true
                    report(err)
                    MsgUtil.showMsgLong(getString(R.string.msg_bangumi_following_failed))
                    loaded[TAB_FOLLOWING] = true
                    renderEmpty()
                    return@runOnUiThread
                }
                followingError = false
                val items = cards ?: emptyList()
                if (reset) followingList.clear()
                followingList.addAll(items)
                followingPage = page + 1
                followingEnd = atEnd || items.isEmpty()
                loaded[TAB_FOLLOWING] = true
                if (followingAdapter == null) {
                    followingAdapter = VideoCardAdapter(this, followingList)
                    recyclers[TAB_FOLLOWING].adapter = followingAdapter
                } else {
                    followingAdapter?.notifyDataSetChanged()
                }
                renderEmpty()
            }
        }
    }

    // ===================== 索引 =====================

    @SuppressLint("NotifyDataSetChanged")
    private fun loadIndex(reset: Boolean) {
        if (isFinishing) return
        if (indexLoading) {
            refreshes[TAB_INDEX].isRefreshing = false
            return
        }
        if (reset) {
            indexPage = 1
            indexEnd = false
        } else if (indexEnd) {
            return
        }
        indexLoading = true
        // 代际：连点筛选项时只认最后一轮的结果
        val generation = ++indexGeneration
        val page = indexPage
        val filters = LinkedHashMap(selectedFilters)
        val order = selectedOrder
        if (reset) refreshes[TAB_INDEX].isRefreshing = true

        CenterThreadPool.run {
            var cards: List<VideoCard>? = null
            var hasNext = false
            var error: Exception? = null
            try {
                val result = BangumiIndexApi.getIndex(filters, order, page)
                cards = result.cards
                hasNext = result.hasNext
            } catch (e: Exception) {
                error = e
            }
            runOnUiThread {
                if (isFinishing || generation != indexGeneration) return@runOnUiThread
                indexLoading = false
                refreshes[TAB_INDEX].isRefreshing = false
                val err = error
                if (err != null) {
                    // 失败与「没有下一页」严格区分：不能把错误当成到底，否则翻页永久停在这一页
                    indexError = true
                    report(err)
                    MsgUtil.showMsgLong(getString(R.string.msg_bangumi_index_failed))
                    loaded[TAB_INDEX] = true
                    renderEmpty()
                    return@runOnUiThread
                }
                indexError = false
                val items = cards ?: emptyList()
                if (reset) indexList.clear()
                indexList.addAll(items)
                indexPage = page + 1
                indexEnd = !hasNext || items.isEmpty()
                loaded[TAB_INDEX] = true
                if (indexAdapter == null) {
                    indexAdapter = VideoCardAdapter(this, indexList)
                    recyclers[TAB_INDEX].adapter = indexAdapter
                } else {
                    indexAdapter?.notifyDataSetChanged()
                }
                renderEmpty()
            }
        }
    }

    // ===================== 筛选 =====================

    private fun loadCondition() {
        CenterThreadPool.run {
            var condition: BangumiIndexApi.Condition? = null
            try {
                condition = BangumiIndexApi.getCondition()
            } catch (e: Exception) {
                report(e)
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val loadedCondition = condition ?: return@runOnUiThread
                indexCondition = loadedCondition
                // 默认排序取接口给的第一个（综合排序），筛选默认全部
                if (selectedOrder.isEmpty() && loadedCondition.orders.isNotEmpty()) {
                    selectedOrder = loadedCondition.orders[0].value
                }
                for (group in loadedCondition.filters) {
                    if (!selectedFilters.containsKey(group.field)) {
                        selectedFilters[group.field] = FILTER_ALL
                    }
                }
                refreshFilterEntry()
            }
        }
    }

    /**
     * 筛选入口的文案：`筛选：排序名 · 组名值 …`。
     *
     * 只列**非全部**的组，全部选中时退回单纯一个「筛选」，避免这一行被 9 个「全部」刷满。
     */
    private fun refreshFilterEntry() {
        val condition = indexCondition
        if (condition == null) {
            filterEntry.text = getString(R.string.text_bangumi_filter)
            return
        }
        val parts = ArrayList<String>()
        parts.add(orderName(condition))
        for (group in condition.filters) {
            val value = selectedFilters[group.field] ?: FILTER_ALL
            if (value == FILTER_ALL) continue
            val option = group.options.firstOrNull { it.value == value } ?: continue
            parts.add(option.name)
        }
        filterEntry.text = getString(R.string.text_bangumi_filter_summary, parts.joinToString(" · "))
    }

    private fun orderName(condition: BangumiIndexApi.Condition): String {
        val order = condition.orders.firstOrNull { it.value == selectedOrder }
        return order?.name ?: getString(R.string.text_bangumi_filter_order)
    }

    /**
     * 点筛选入口：先弹「排序 + 各组」的菜单，选一组再进单选。
     *
     * 走 `TerminalDialog` 而不是自绘 chip 行：手表上 9 组 × 最多 45 个选项无法在一屏里铺开，
     * 弹窗自带滚动区，且「菜单 → 单选」两级正好对应用户的两次决策（选哪组、选哪个值）。
     */
    private fun openFilterDialog() {
        val condition = indexCondition
        if (condition == null) {
            // 条件还没拿到：这里顺手重试一次，别让用户以为筛选坏了
            MsgUtil.showMsg(getString(R.string.text_bangumi_condition_failed))
            loadCondition()
            return
        }
        val entries = ArrayList<String>()
        entries.add(getString(R.string.text_bangumi_filter_order) + "：" + orderName(condition))
        for (group in condition.filters) {
            val value = selectedFilters[group.field] ?: FILTER_ALL
            val current = group.options.firstOrNull { it.value == value }?.name ?: ""
            entries.add(if (current.isEmpty()) group.name else group.name + "：" + current)
        }

        TerminalDialog.menu(
            this,
            getString(R.string.text_bangumi_filter_title),
            entries
        ) { which ->
            if (which == 0) openOrderChooser(condition) else openGroupChooser(condition, which - 1)
        }.show()
    }

    private fun openOrderChooser(condition: BangumiIndexApi.Condition) {
        if (condition.orders.isEmpty()) return
        val items = condition.orders.map { it.name }
        val checked = condition.orders.indexOfFirst { it.value == selectedOrder }
        var picked = false
        TerminalDialog.singleChoice(
            this,
            getString(R.string.text_bangumi_filter_order),
            items,
            if (checked < 0) 0 else checked
        ) { sheet, index ->
            if (picked) return@singleChoice
            picked = true
            sheet.dialog.dismiss()
            selectedOrder = condition.orders[index].value
            refreshFilterEntry()
            reloadIndex()
        }.show()
    }

    private fun openGroupChooser(condition: BangumiIndexApi.Condition, groupIndex: Int) {
        val group = condition.filters.getOrNull(groupIndex) ?: return
        if (group.options.isEmpty()) return
        val items = group.options.map { it.name }
        val current = selectedFilters[group.field] ?: FILTER_ALL
        val checked = group.options.indexOfFirst { it.value == current }
        var picked = false
        TerminalDialog.singleChoice(
            this,
            group.name,
            items,
            if (checked < 0) 0 else checked
        ) { sheet, index ->
            if (picked) return@singleChoice
            picked = true
            sheet.dialog.dismiss()
            selectedFilters[group.field] = group.options[index].value
            refreshFilterEntry()
            reloadIndex()
        }.show()
    }

    /** 条件变了：清空当前结果、回到第一页重拉（代际在 [loadIndex] 里递增，旧响应自然作废）。 */
    private fun reloadIndex() {
        indexPage = 1
        indexEnd = false
        indexError = false
        val size = indexList.size
        indexList.clear()
        if (size > 0) indexAdapter?.notifyItemRangeRemoved(0, size)
        loaded[TAB_INDEX] = false
        refreshFilterEntry()
        if (currentTab == TAB_INDEX) {
            hideEmptyState()
            refreshes[TAB_INDEX].isRefreshing = true
            loadIndex(true)
        }
    }
}
