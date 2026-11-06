package com.RobinNotBad.BiliClient.activity.reply

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.ReplyAdapter
import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.event.ReplyEvent
import com.RobinNotBad.BiliClient.model.ContentType
import com.RobinNotBad.BiliClient.model.Reply
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.ReplyState
import com.RobinNotBad.BiliClient.util.ReplyStateStore
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Collections
import java.util.HashSet

class ReplyInfoActivity : BaseActivity() {

    private var oid: Long = 0
    private var rpid: Long = 0
    private var up_mid: Long = 0
    private var sort: Int = 0
    private var isManager: Boolean = false
    private lateinit var type: ContentType
    private lateinit var recyclerView: RecyclerView
    private lateinit var refreshLayout: SwipeRefreshLayout
    private var replyList: ArrayList<Reply>? = null
    private var replyAdapter: ReplyAdapter? = null
    private var bottom: Boolean = false
    private var page: Int = 1
    private var refreshing: Boolean = false

    // 已经进过列表的 rpid（含根评论与楼中楼）。详情页也会收到 sticky 的 ReplyEvent，
    // 页面重建后 EventBus 会再投一次，不去重就会多出一条一模一样的回复
    private val loadedRpids: MutableSet<Long> = Collections.synchronizedSet(HashSet<Long>())
    private var account: Long = 0L

    // 跨页状态监听：列表页把某条楼中楼点赞/点踩/删掉后，这里立即同步
    private val storeListener: ReplyStateStore.Listener = object : ReplyStateStore.Listener {
        override fun onReplyStateChanged(state: ReplyState) {
            runOnUiThread { applyStateFromStore(state) }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_refresh)

        val intent = intent
        rpid = intent.getLongExtra("rpid", 0)
        oid = intent.getLongExtra("oid", 0)
        try {
            type = ContentType.getContentType(intent.getIntExtra("type", 1))
        } catch (e: ContentType.TerminalIllegalTypeCodeException) {
            throw RuntimeException(e)
        }
        up_mid = intent.getLongExtra("up_mid", -1)
        isManager = intent.getBooleanExtra("is_manager", false)

        account = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L)
        ReplyStateStore.instance.addListener(storeListener)

        refreshLayout = findViewById(R.id.swipeRefreshLayout)
        recyclerView = findViewById(R.id.recyclerView)
        refreshLayout.setOnRefreshListener { refresh() }

        setPageName(UiText.of("评论详情"))

        if (SharedPreferencesUtil.getBoolean("ui_landscape", false)) {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= 17) display.getRealMetrics(metrics)
            else display.getMetrics(metrics)
            val paddings = metrics.widthPixels / 6
            recyclerView.setPadding(paddings, 0, paddings, 0)
        }

        refreshLayout.isRefreshing = true
        TerminalContext.getInstance().getReply(type, oid, rpid).observe(this) { rootReplyResult ->
            replyList = ArrayList()
            rootReplyResult.onSuccess { rootReply ->
                val read = ReplyStateStore.instance.beginRead()
                val fetched = ArrayList<Reply>()
                val future = CenterThreadPool.supplyAsyncWithFuture { ReplyApi.getReplies(oid, rpid, page, type, sort, fetched) }
                CenterThreadPool.observe(future, { result ->
                    if (result != -1) {
                        ReplyStateStore.instance.observeReplies(listOf(rootReply), account, type.typeCode, oid, read)
                        val visible = ArrayList<Reply>()
                        visible.add(rootReply)
                        if (rootReply.rpid > 0) loadedRpids.add(rootReply.rpid)
                        for (item in fetched) {
                            if (item == null) continue
                            // 已在别处被删除的楼中楼不再显示（本地删除优先于服务端列表）
                            if (ReplyStateStore.instance.applyTo(item, account, type.typeCode, oid)) continue
                            visible.add(item)
                            if (item.rpid > 0) loadedRpids.add(item.rpid)
                        }
                        replyList!!.clear()
                        replyList!!.addAll(visible)
                        replyAdapter = ReplyAdapter(this, replyList!!, oid, up_mid, rpid, type.typeCode, sort, type.typeCode)
                        replyAdapter!!.isManager = isManager
                        replyAdapter!!.isDetail = true
                        setOnSortSwitch()
                        recyclerView.layoutManager = CustomLinearManager(this)
                        recyclerView.adapter = replyAdapter
                        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                                super.onScrollStateChanged(recyclerView, newState)
                            }

                            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                                super.onScrolled(recyclerView, dx, dy)
                                val manager = recyclerView.layoutManager as LinearLayoutManager
                                val lastItemPosition = manager.findLastVisibleItemPosition()
                                val itemCount = manager.itemCount
                                if (lastItemPosition >= (itemCount - 3) && dy > 0 && !refreshing && !bottom) {
                                    refreshing = true
                                    CenterThreadPool.run { continueLoading() }
                                }
                            }
                        })
                        refreshLayout.isRefreshing = false
                        if (result == 1) {
                            bottom = true
                        }
                    }
                }, { error -> onPullDataFailed(Exception(error)) })
            }.onFailure { error -> onPullDataFailed(Exception(error)) }
        }
    }

    override fun onDestroy() {
        // 必须摘监听：Store 是进程内单例，留着这个 Activity 就回收不掉，
        // 回调里再碰已销毁的 view 会崩
        ReplyStateStore.instance.removeListener(storeListener)
        super.onDestroy()
    }

    private fun onPullDataFailed(e: Exception) {
        MsgUtil.err(e)
        refreshLayout.isRefreshing = false
    }

    /**
     * 收到别的页面的状态变化。
     *
     * <p>本页的**根评论**被别人删掉时直接退出：详情页剩下的全是它的楼中楼，
     * 根没了留一个空壳页面没有意义。楼中楼的变化交给 adapter 就地刷新。
     */
    private fun applyStateFromStore(state: ReplyState) {
        if (isFinishing) return
        if (state.deleted && state.rpid == rpid) {
            MsgUtil.showMsg(getString(R.string.reply_port_deleted))
            finish()
            return
        }
        replyAdapter?.applyState(state)
    }

    private fun continueLoading() {
        runOnUiThread { refreshLayout.isRefreshing = true }
        page++
        try {
            val read = ReplyStateStore.instance.beginRead()
            val list = ArrayList<Reply>()
            val result = ReplyApi.getReplies(oid, rpid, page, type, sort, list)
            if (result != -1) {
                // 跨页去重：楼中楼接口在翻页时也会重复吐同一条（尤其排序/游标回退时）
                ReplyApi.filterDuplicateReplies(list, loadedRpids)
                val visible = ArrayList<Reply>(list.size)
                for (item in list) {
                    if (item == null) continue
                    if (ReplyStateStore.instance.applyTo(item, account, type.typeCode, oid)) continue
                    visible.add(item)
                    if (item.rpid > 0) loadedRpids.add(item.rpid)
                }
                runOnUiThread {
                    if (visible.isEmpty()) {
                        refreshLayout.isRefreshing = false
                        return@runOnUiThread
                    }
                    val startIndex = replyList!!.size
                    ReplyStateStore.instance.observeReplies(visible, account, type.typeCode, oid, read)
                    replyList!!.addAll(visible)
                    if (sort == Reply.SORT_LIKE) {
                        // 热度排序下新一页要并进全局排序，插入位置不再是「接在末尾」，只能整体重排
                        applySort()
                    } else {
                        // 数据下标 i >= 1 的楼中楼在 adapter 里的位置是 i + 1
                        // （第 0 位是根评论、第 1 位是"写评论"头部）
                        val firstAdapterPos = startIndex + 1
                        replyAdapter!!.notifyItemRangeInserted(firstAdapterPos, visible.size)
                    }
                    refreshLayout.isRefreshing = false
                }
                if (result == 1) {
                    bottom = true
                }
            }
            refreshing = false
        } catch (e: Exception) {
            runOnUiThread {
                MsgUtil.err(e)
                refreshLayout.isRefreshing = false
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refresh() {
        page = 1
        refreshLayout.isRefreshing = true
        // 整表刷新：上一轮的 rpid 全部作废，否则第一页会被自己历史记录判成"重复"
        loadedRpids.clear()

        TerminalContext.getInstance().getReply(type, oid, rpid).observe(this) { rootReplyResult ->
            rootReplyResult.onSuccess { rootReply ->
                val read = ReplyStateStore.instance.beginRead()
                val list = ArrayList<Reply>()
                val future = CenterThreadPool.supplyAsyncWithFuture { ReplyApi.getReplies(oid, rpid, page, type, sort, list) }
                CenterThreadPool.observe(future, { result ->
                    if (result != -1) {
                        runOnUiThread {
                            ReplyStateStore.instance.observeReplies(listOf(rootReply), account, type.typeCode, oid, read)
                            val visible = ArrayList<Reply>(list.size + 1)
                            visible.add(rootReply)
                            if (rootReply.rpid > 0) loadedRpids.add(rootReply.rpid)
                            ReplyApi.filterDuplicateReplies(list, loadedRpids)
                            for (item in list) {
                                if (item == null) continue
                                if (ReplyStateStore.instance.applyTo(item, account, type.typeCode, oid)) continue
                                visible.add(item)
                            }
                            replyList!!.clear()
                            replyList!!.addAll(visible)
                            if (replyAdapter == null) {
                                replyAdapter = ReplyAdapter(this, replyList!!, oid, up_mid, rpid, type.typeCode, sort, type.typeCode)
                                replyAdapter!!.isDetail = true
                                setOnSortSwitch()
                                recyclerView.adapter = replyAdapter
                            }
                            // 刷新拿回来的是服务端顺序，但用户可能正选着「热度排序」，要重排一次
                            applySort()
                            refreshLayout.isRefreshing = false
                        }
                        if (result == 1) {
                            bottom = true
                        } else bottom = false
                    }
                }, { error ->
                    this.onPullDataFailed(Exception(error))
                })
            }.onFailure { error ->
                this.onPullDataFailed(Exception(error))
            }
        }
    }

    /**
     * 楼中楼排序开关。
     *
     * 服务端 {@code /x/v2/reply/reply} 没有 sort 参数（见 api/ReplyApi.java 的 getReplies），
     * 传了也不生效，所以这里**不重新请求**，只对已加载的评论重排：
     * 0=按时间（就是服务端返回的回复顺序）、1=按点赞数降序。
     * 翻页时新数据会并进来重新排（见 continueLoading）。
     */
    private fun setOnSortSwitch() {
        replyAdapter!!.setOnSortSwitchListener {
            sort = if (sort == Reply.SORT_TIME) Reply.SORT_LIKE else Reply.SORT_TIME
            // 适配器里另有一份 sort，用来显示排序按钮的文案，必须一起改，否则按钮文字不变
            replyAdapter!!.sort = sort
            applySort()
        }
    }

    /**
     * 按当前 [sort] 重排已加载的评论并刷新列表。
     *
     * 第 0 位是根评论（所有楼中楼回复的父评论），不能参与排序，所以 fromIndex = 1。
     * 重排后整体重绑：同一条评论会换行，用局部 notify 会错行。
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun applySort() {
        val list = replyList ?: return
        Reply.sortReplies(list, sort, 1)
        replyAdapter?.notifyDataSetChanged()
    }

    override fun eventBusEnabled(): Boolean {
        return true
    }

    /**
     * 收到「有新回复发出来了」的事件。
     *
     * <p>和列表页一样，这个订阅是 sticky 的，页面重建后会重投一次，所以必须按 rpid 去重；
     * 另外本页只关心「根评论自己」和「挂在根评论下的楼中楼」，别的评论区的回复直接忽略。
     * 事件来自 EventBus 的 ASYNC 线程，数据改动与 notify 全部在主线程做。
     */
    @Subscribe(threadMode = ThreadMode.ASYNC, sticky = true, priority = 1)
    fun onEvent(event: ReplyEvent) {
        if (event.oid != oid) return
        val message = event.message ?: return
        // 本页只关心「这个根评论自己」和「挂在它下面的楼中楼」，别的评论区的回复直接忽略
        if (message.rpid != rpid && message.root != rpid) return
        val newRpid = message.rpid

        runOnUiThread {
            val list = replyList ?: return@runOnUiThread
            val adapter = replyAdapter ?: return@runOnUiThread
            // sticky 重投递 / 同时收到两次：已在列表里就不再插一遍
            if (newRpid > 0 && ReplyApi.containsReplyId(list, newRpid)) return@runOnUiThread
            list.add(message)
            if (newRpid > 0) loadedRpids.add(newRpid)
            if (sort == Reply.SORT_LIKE) {
                applySort()
            } else {
                // 数据下标 i >= 1 → adapter 位置 i + 1（第 0 位根评论、第 1 位写评论头部）
                val adapterPos = list.size
                adapter.notifyItemInserted(adapterPos)
                adapter.notifyItemRangeChanged(adapterPos, list.size + 1 - adapterPos)
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(adapterPos, 0)
            }
        }
    }
}
