package com.RobinNotBad.BiliClient.activity.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnLoadMoreListener
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomGridManager
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener

open class RefreshListFragment : BaseFragment() {
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var recyclerView: RecyclerView
    var emptyView: TextView? = null
    var listener: OnLoadMoreListener? = null

    /**
     * 空态的默认文案（布局里写的那句）。
     *
     * 页面可以按当前状态换成别的文案（如「TA 还没有投稿过视频」/「没有搜到相关稿件」），
     * 换过之后要能回到默认这句，所以在这里留一份。
     */
    private var emptyText: CharSequence? = null

    /**
     * 是否已到列表底部（没有更多数据）。
     *
     * 子类在网络回调（[com.RobinNotBad.BiliClient.util.CenterThreadPool] 的后台线程）里赋值，
     * 而 [onViewCreated] 注册的滚动监听在**主线程**读它来决定要不要继续翻页。普通 `Boolean`
     * 字段没有 happens-before 边，主线程可能长期读到陈旧的 false，表现为"到底了还无限翻页"。
     * 故必须 @Volatile 保证可见性（与 RefreshListActivity.bottom 保持一致）。
     */
    @Volatile
    var bottom: Boolean = false
    var page: Int = 1
    var lastLoadTimestamp: Long = 0
    var forceSingleColumn: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_simple_refresh, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView = view.findViewById(R.id.emptyTip)
        emptyText = emptyView?.text
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.isEnabled = false
        swipeRefreshLayout.isRefreshing = true
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(10)
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 20)
        recyclerView.layoutManager = getLayoutManager()
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (listener != null && !recyclerView.canScrollVertically(1) && !isRefreshing() && newState == RecyclerView.SCROLL_STATE_DRAGGING && !bottom) {
                    goOnLoad()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (listener != null) {
                    val manager = recyclerView.layoutManager as LinearLayoutManager
                    val lastItemPosition = manager.findLastVisibleItemPosition()
                    val itemCount = manager.itemCount
                    if (lastItemPosition >= (itemCount - 3) && dy > 0 && !isRefreshing() && !bottom) {
                        goOnLoad()
                    }
                }
            }
        })
        ImageAutoLoadScrollListener.installIfEnabled(recyclerView)
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>) {
        runOnUiThread { recyclerView.adapter = adapter }
    }

    fun setOnRefreshListener(listener: SwipeRefreshLayout.OnRefreshListener) {
        swipeRefreshLayout.setOnRefreshListener(listener)
        swipeRefreshLayout.isEnabled = true
    }

    fun setRefreshing(bool: Boolean) {
        runOnUiThread { swipeRefreshLayout.isRefreshing = bool }
    }

    fun setOnLoadMoreListener(loadMore: OnLoadMoreListener) {
        listener = loadMore
    }

    private fun goOnLoad() {
        val timeCurrent = System.currentTimeMillis()
        if (timeCurrent - lastLoadTimestamp > 100) {
            swipeRefreshLayout.isRefreshing = true
            page++
            listener!!.onLoad(page)
            lastLoadTimestamp = timeCurrent
        }
    }

    /** 空态：隐藏列表、显示默认文案（布局里的那句「啥都木有~」）。 */
    fun showEmptyView() {
        showEmptyView(emptyText)
    }

    /**
     * 带文案的空态。
     *
     * 有的页面得按状态换说法——「TA 一条投稿都没有」和「这个关键词没搜到」不是一回事，
     * 用默认文案会让用户以为这个 UP 主没投过稿。文案由调用方在 UI 线程取（fragment 用 getString）。
     */
    fun showEmptyView(message: CharSequence?) {
        emptyView?.let { tip ->
            runOnUiThread {
                recyclerView.visibility = View.GONE
                if (message != null && tip.text != message) tip.text = message
                tip.visibility = View.VISIBLE
            }
        }
    }

    /**
     * 收起空态、把列表放回来。
     *
     * 搜索这类「列表可能从空变回有数据」的页面必须调：空态是把 recyclerView 设成 GONE 顶上去的，
     * 只换 adapter 不还原可见性，用户会一直看着「没有搜到相关稿件」。
     */
    fun hideEmptyView() {
        emptyView?.let { tip ->
            runOnUiThread {
                if (tip.visibility == View.VISIBLE) tip.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    fun isRefreshing(): Boolean {
        return swipeRefreshLayout.isRefreshing
    }

    fun report(e: Throwable) {
        MsgUtil.err(e)
    }

    fun loadFail() {
        page--
        // 用 application context 取文案：失败回调常常发生在 fragment 已经 detach 之后，
        // 直接 getString 会抛 IllegalStateException: Fragment ... not attached to a context.
        MsgUtil.showMsgLong(getAppContext().getString(R.string.text_load_failed))
        setRefreshing(false)
    }

    fun loadFail(e: Throwable) {
        page--
        report(e)
        setRefreshing(false)
    }

    fun getLayoutManager(): RecyclerView.LayoutManager {
        return if (SharedPreferencesUtil.getBoolean("ui_landscape", false) && !forceSingleColumn)
            CustomGridManager(requireContext(), 3)
        else
            CustomLinearManager(requireContext())
    }

    fun setForceSingleColumn() {
        forceSingleColumn = true
    }
}