package com.RobinNotBad.BiliClient.activity.reply

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListFragment
import com.RobinNotBad.BiliClient.adapter.ReplyAdapter
import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.event.ReplyEvent
import com.RobinNotBad.BiliClient.model.Reply
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.LocalReplyCache
import com.RobinNotBad.BiliClient.util.ReplyFetchPolicy
import com.RobinNotBad.BiliClient.util.ReplyState
import com.RobinNotBad.BiliClient.util.ReplyStateStore
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.util.Collections
import java.util.HashSet

class ReplyFragment : RefreshListFragment() {

    private var dontload: Boolean = false
    var aid: Long = 0
    var mid: Long = 0
    var sort: Int = 3
    var type: Int = 0
    var count: Int = 0
    var replyList: ArrayList<Reply>? = null
    var replyAdapter: ReplyAdapter? = null
    var replyType: Int = ReplyApi.REPLY_TYPE_VIDEO
    private var seek: Long = 0

    // 翻页游标会被后台线程读写，标 @Volatile 保证可见性：
    // 否则主线程刚清空的游标，后台线程可能还拿着旧值去请求上一页
    @Volatile
    private var pagination: String = ""

    private var isManager: Boolean = false

    // 加载世代号：每次 refresh（下拉刷新、换排序、换视频）都自增。
    // 所有异步回调都要跟出发时记下的世代号比对，不是当前世代就直接丢弃；
    // 否则旧请求的响应会覆盖新请求刚拉到的列表，评论区表现为"跳来跳去"
    @Volatile
    private var loadGeneration: Int = 0

    // 是否真的拉到评论末尾。用 @Volatile 是因为它会在后台线程的重试循环里被反复读、在主线程被写；
    // 父类的 bottom 是给滚动监听用的界面状态，不能拿来当线程间的同步标志
    @Volatile
    private var isEnd: Boolean = false

    // 已经进过列表的 rpid。翻页/下拉刷新时服务端会把同一条评论再吐一遍（置顶、游标回退、
    // 刚发完评论服务端列表还没稳定都会出现），不去重就会出现两条一样的评论、位号整体错位。
    // 用同步 Set：写入在主线程，翻页过滤在后台线程。
    private val loadedRpids: MutableSet<Long> = Collections.synchronizedSet(HashSet<Long>())

    // 本页自己刚发出去的评论。分页失败/首屏刷不出来时拿它把评论补回列表，
    // 不让"我明明发出去了"变成一片空白（详见 util/ReplyFetchPolicy.kt 的 LocalReplyCache）
    private val localReplies = LocalReplyCache()

    private var account: Long = 0L

    // 跨页状态监听：别的页面点赞/点踩/删除后，这里立即把对应条目刷新掉。
    // 必须在 onDestroyView 里摘掉，否则页面销毁后仍被 Store 持有着，回调里碰 view 会崩、页面也回收不了
    private val storeListener: ReplyStateStore.Listener = object : ReplyStateStore.Listener {
        override fun onReplyStateChanged(state: ReplyState) {
            runOnUiThread { applyStateFromStore(state) }
        }
    }

    companion object {
        // 单次翻页的总超时上限。没有这个兜底时，只要服务端把连接吊住，
        // isLoading / isRefreshing 就永远不复位，评论区从此彻底卡死（再也上拉不动）
        private const val LOAD_DEADLINE_MS = 30_000L

        // 连续空页的容忍次数。服务端偶发返回空页就判定"到底了"会丢内容，
        // 但无限重试又会造成请求风暴，所以两者取一个上限
        private const val MAX_EMPTY_PAGES = 5
        fun newInstance(aid: Long, type: Int): ReplyFragment {
            val fragment = ReplyFragment()
            val args = Bundle()
            args.putLong("aid", aid)
            args.putInt("type", type)
            fragment.arguments = args
            return fragment
        }

        fun newInstance(aid: Long, type: Int, dontload: Boolean): ReplyFragment {
            val fragment = ReplyFragment()
            val args = Bundle()
            args.putLong("aid", aid)
            args.putInt("type", type)
            args.putBoolean("dontload", dontload)
            fragment.arguments = args
            return fragment
        }

        fun newInstance(aid: Long, type: Int, seek_rpid: Long): ReplyFragment {
            val fragment = ReplyFragment()
            val args = Bundle()
            args.putLong("aid", aid)
            args.putInt("type", type)
            args.putLong("seek", seek_rpid)
            fragment.arguments = args
            return fragment
        }

        fun newInstance(aid: Long, type: Int, dontload: Boolean, seek_rpid: Long): ReplyFragment {
            val fragment = ReplyFragment()
            val args = Bundle()
            args.putLong("aid", aid)
            args.putInt("type", type)
            args.putBoolean("dontload", dontload)
            args.putLong("seek", seek_rpid)
            fragment.arguments = args
            return fragment
        }

        fun newInstance(aid: Long, type: Int, count: Int, seek_rpid: Long, up_mid: Long): ReplyFragment {
            val fragment = ReplyFragment()
            val args = Bundle()
            args.putLong("aid", aid)
            args.putInt("count", count)
            args.putInt("type", type)
            args.putLong("seek", seek_rpid)
            args.putLong("mid", up_mid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            aid = arguments!!.getLong("aid", 0)
            count = arguments!!.getInt("count", 0)
            type = arguments!!.getInt("type", 0)
            replyType = type
            dontload = arguments!!.getBoolean("dontload", false)
            seek = arguments!!.getLong("seek", -1)
            mid = arguments!!.getLong("mid", -1)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setForceSingleColumn()
        super.onViewCreated(view, savedInstanceState)

        if (SharedPreferencesUtil.getBoolean("ui_landscape", false)) {
            val windowManager = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= 17) display.getRealMetrics(metrics)
            else display.getMetrics(metrics)
            val paddings = metrics.widthPixels / 6
            recyclerView.setPadding(paddings, 0, paddings, 0)
        }

        setOnRefreshListener { refresh(aid) }
        setOnLoadMoreListener { continueLoading(it) }

        Log.e("debug-av号", aid.toString())

        replyList = ArrayList()

        account = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L)
        ReplyStateStore.instance.addListener(storeListener)

        if (!dontload) refresh(aid)
    }

    override fun onDestroyView() {
        // 必须在 view 销毁时摘监听：Store 是进程内的，留着这个 fragment 就回收不掉，
        // 回调里再碰 recyclerView 就是 null/已销毁的 view
        ReplyStateStore.instance.removeListener(storeListener)
        super.onDestroyView()
    }

    fun setManager(source: Any?) {
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) return

        try {
            if (source != null) {
                if (source is List<*>) {
                    val staffs = source as List<UserInfo>
                    for (userInfo in staffs) {
                        if (userInfo.mid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) {
                            isManager = true
                            break
                        }
                    }
                } else if (source is UserInfo) {
                    isManager = source.mid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
                }
            }
        } catch (e: Exception) {
            MsgUtil.err(e)
        }
    }

    /**
     * 保证 adapter 存在并跟当前的列表/类型/计数同步。
     *
     * <p>首屏数据、失败兜底、自己发评论三条路都可能先于首屏响应到达界面，统一走这里，
     * 避免每条路径各写一份"先 new adapter 再 setAdapter"（写漏一处就是空列表或类型错）。
     */
    private fun ensureReplyAdapter() {
        val list = replyList ?: return
        val existing = replyAdapter
        // 评论来源变了（换视频、或首屏兜底换了评论区类型）就必须重建：
        // oid/type 是 adapter 的 val，留着旧值会把后续点赞发到错的评论区
        if (existing != null && (existing.oid != aid || existing.replyType != replyType)) {
            replyAdapter = null
        }
        if (replyAdapter == null) {
            val adapter = ReplyAdapter(requireContext(), list, aid, mid, 0L, replyType, sort, replyType)
            adapter.count = count.toLong()
            adapter.isManager = isManager
            replyAdapter = adapter
            setOnSortSwitch()
            setAdapter(adapter)
        } else {
            existing!!.sort = sort
            // 计数可能是 getReplyCount 补回来的，adapter 上的旧值要跟着更新（"共 N 条评论"）
            existing.count = count.toLong()
            existing.notifyDataSetChanged()
        }
    }

    /**
     * 把本页自己刚发出去的评论补回列表。
     *
     * <p>只在「服务端这一页没给全」时才有内容可补：正常刷新会先 clear 再 addAll，
     * 补回的是本地 cache 里还没过期、且服务端列表里确实没有的那些。
     */
    private fun mergeLocalReplies() {
        val list = replyList ?: return
        val merged = localReplies.mergeInto(list, System.currentTimeMillis())
        if (merged.isEmpty()) return
        for (reply in merged) {
            if (reply.rpid > 0) loadedRpids.add(reply.rpid)
        }
        replyAdapter?.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun continueLoading(page: Int) {
        // 已经到底或不再允许上拉，直接复位并撤退：这一步避免了"到底后每次滚动都再发一轮请求"
        if (isEnd || bottom) {
            setRefreshing(false)
            return
        }

        // 记下本次加载的世代号与截止时间。世代号用于丢弃过期响应，截止时间用于给重试兜底，
        // 两者共同保证既不会用旧数据覆盖新列表，也不会因为重试把请求打爆
        val generation = loadGeneration
        val deadline = System.currentTimeMillis() + LOAD_DEADLINE_MS

        CenterThreadPool.run {
            var emptyPages = 0
            try {
                while (!isEnd && !bottom && emptyPages < MAX_EMPTY_PAGES && System.currentTimeMillis() < deadline) {
                    // 每轮开始前先确认自己还是当前世代，过期就立刻撤退，绝不碰数据源
                    if (generation != loadGeneration) return@run

                    // 发请求前记下修订号：响应回来合并状态时用它判断"这次读是不是已经过时"
                    val read = ReplyStateStore.instance.beginRead()

                    val list = ArrayList<Reply>()
                    val pageState = ReplyApi.getRepliesLazy(aid, 0, pagination, type, sort, list)
                    val result = pageState.first

                    if (generation != loadGeneration) return@run

                    if (result == ReplyApi.PAGE_ERROR) {
                        // 失败也必须复位加载状态，否则下拉/上拉会永远卡在转圈
                        runOnUiThread {
                            if (generation == loadGeneration) loadFail()
                        }
                        return@run
                    }

                    if (result == ReplyApi.PAGE_EMPTY) {
                        // 服务端偶发空页：带着原游标重试同一页，重试次数和总时长都受上面的循环条件约束
                        emptyPages++
                        continue
                    }

                    val nextPagination = pageState.second
                    val finalEnd = result == ReplyApi.PAGE_END

                    // 第 1 层兜底：跨页去重。置顶/游标回退/刚发完评论时服务端会把同一 rpid 再吐一遍，
                    // 不去重列表里就会同时出现两条一样的评论，后面"按可见位置插入"的位号也会整体错位。
                    ReplyApi.filterDuplicateReplies(list, loadedRpids)

                    if (list.isEmpty()) {
                        // 空列表且已到底，直接收尾
                        if (finalEnd) {
                            runOnUiThread {
                                if (generation != loadGeneration) return@runOnUiThread
                                pagination = nextPagination
                                isEnd = true
                                bottom = true
                                setRefreshing(false)
                            }
                            return@run
                        }
                        // 整页都是重复项（或服务端就是空页）：游标必须推进，
                        // 否则会卡在同一页上反复拉同一批重复数据
                        pagination = nextPagination
                        emptyPages++
                        continue
                    }

                    // 数据改动与 notify 必须同在主线程执行，否则列表下标会错位甚至越界崩溃
                    runOnUiThread {
                        if (generation != loadGeneration) return@runOnUiThread
                        Log.e("debug", if (finalEnd) "到底了" else "下一页")
                        // 只有真正拿到响应才推进游标，避免空页把游标冲掉导致整段内容缺失
                        pagination = nextPagination
                        if (finalEnd) {
                            isEnd = true
                            bottom = true
                        }
                        // 服务端快照先入库（供其它页面合并），再把已在别处删掉的条目挡在列表外
                        ReplyStateStore.instance.observeReplies(list, account, replyType, aid, read)
                        val visible = ArrayList<Reply>(list.size)
                        for (item in list) {
                            if (item == null) continue
                            if (ReplyStateStore.instance.applyTo(item, account, replyType, aid)) continue
                            visible.add(item)
                        }
                        if (visible.isEmpty()) {
                            // 整页都被"已在别处删除"过滤掉了：当成空页处理，别把界面刷成空的
                            setRefreshing(false)
                            return@runOnUiThread
                        }
                        val startIndex = replyList!!.size
                        replyList!!.addAll(visible)
                        replyAdapter?.notifyItemRangeInserted(startIndex + 1, visible.size)
                        setRefreshing(false)
                    }
                    return@run
                }

                // 走到这里说明是空页用尽或超时退出。这里一定要复位加载状态并封口，
                // 否则滚动监听会不停地再次触发加载，形成请求风暴
                runOnUiThread {
                    if (generation != loadGeneration) return@runOnUiThread
                    isEnd = true
                    bottom = true
                    setRefreshing(false)
                }
                if (generation == loadGeneration && emptyPages >= MAX_EMPTY_PAGES)
                    MsgUtil.showMsgLong(UiText.of("暂时没有更多评论了"))
            } catch (e: Exception) {
                // 异常路径同样要复位加载状态并回退页码，这是父类"加载完必须 setRefreshing(false)"的硬约定
                runOnUiThread {
                    if (generation == loadGeneration) loadFail(e)
                }
            }
        }
    }

    /**
     * 收到「新评论发出去了」的事件（父 Activity 的 EventBus 订阅转进来，或评论详情页自己发）。
     *
     * <p>两点必须守住：
     * ① **按 rpid 去重**：EventBus 订阅是 sticky=true，页面重建后会再投递一次同一个事件，
     *    不挡就会出现两条一样的评论；
     * ② 楼中楼按 `root`（父评论 rpid）找父行，不能用事件里的 pos——翻页/插队后
     *    那个位置早就不对了，会把回复挂到别的评论下面。
     */
    fun notifyReplyInserted(replyEvent: ReplyEvent) {
        if (replyEvent.oid != aid) return
        val reply = replyEvent.message ?: return
        val rpid = reply.rpid

        // 已经在列表里（sticky 重投递 / 同时收到两次）：直接忽略，绝不重复插入
        val current = replyList
        if (rpid > 0 && current != null && ReplyApi.containsReplyId(current, rpid)) return

        // 这个回调来自 EventBus 的 ThreadMode.ASYNC，实际跑在后台线程上。
        // 因此数据的增删和 RecyclerView 的通知必须一起丢到主线程执行：
        // 后台改数据源、主线程读数据源会出现"数据改了但通知的位号对不上"，
        // 轻则插入位置错乱，重则下标越界直接崩
        runOnUiThread {
            val list = replyList ?: return@runOnUiThread
            if (rpid > 0 && ReplyApi.containsReplyId(list, rpid)) return@runOnUiThread

            if (replyEvent.isRootReply()) {
                // 记住自己刚发的评论：后续下拉刷新失败/服务端列表没立刻包含它时还能补回来
                localReplies.remember(reply, System.currentTimeMillis())
                if (rpid > 0) loadedRpids.add(rpid)

                val wasEmpty = list.isEmpty()
                val adapter = replyAdapter
                if (adapter == null) {
                    // 首屏还没回来就在空评论区发出了第一条：先建适配器，整表刷新
                    list.add(0, reply)
                    ensureReplyAdapter()
                    scrollToFirstDataRow()
                    return@runOnUiThread
                }

                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                val firstVisible = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
                // adapter 第 0 位是"写评论"头部，所以可见位号减 1 才是数据下标
                val dataIndex = if (firstVisible > 1) firstVisible - 1 else 0
                val insertIndex = dataIndex.coerceIn(0, list.size)
                list.add(insertIndex, reply)
                val adapterPos = insertIndex + 1
                if (wasEmpty) {
                    adapter.notifyDataSetChanged()
                    scrollToFirstDataRow()
                } else {
                    adapter.notifyItemInserted(adapterPos)
                    adapter.notifyItemRangeChanged(adapterPos, list.size + 1 - adapterPos)
                    layoutManager?.scrollToPositionWithOffset(adapterPos, 0)
                }
            } else {
                val parentIndex = list.indexOfFirst { it.rpid == reply.root }
                if (parentIndex < 0) return@runOnUiThread
                val parent = list[parentIndex]
                if (parent.childMsgList == null) parent.childMsgList = ArrayList()
                parent.childMsgList!!.add(reply)
                parent.childCount++
                val adapterPos = if (replyAdapter?.isDetail == true && parentIndex == 0) 0 else parentIndex + 1
                replyAdapter?.notifyItemChanged(adapterPos)
            }
        }
    }

    private fun scrollToFirstDataRow() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        layoutManager.scrollToPositionWithOffset(0, 0)
    }

    /**
     * 把另一页写下的状态变化应用到本页：找到那条评论就地改赞/踩，或在别处被删时把它从列表摘掉。
     */
    private fun applyStateFromStore(state: ReplyState) {
        if (!isAdded) return
        val adapter = replyAdapter ?: return
        if (!adapter.applyState(state)) return
        // 删除后列表可能空了，收起"正在加载"并给一句提示，别让用户对着空白页以为崩了
        if (replyList?.isEmpty() == true) {
            showEmptyView()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refresh(aid: Long) {
        // 进入新世代：此前所有在途请求的响应都会被下面的世代号比对丢弃
        val generation = ++loadGeneration
        pagination = ""
        isEnd = false
        bottom = false
        this.aid = aid
        account = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L)
        // 整表刷新：上一轮见过的 rpid 全部作废，否则第一页会被自己的历史记录判成"重复"
        loadedRpids.clear()
        setRefreshing(true)
        CenterThreadPool.run {
            try {
                // 发请求前记下修订号，供合并状态时判断"这次读是不是已经过时"
                val read = ReplyStateStore.instance.beginRead()

                // 评论区不可用的兜底：没有 oid 时接口必然失败，先把本地能显示的显示出来，
                // 再给一句明确的话，别让用户看着转圈等一个永远不会成功的请求
                if (aid <= 0) {
                    runOnUiThread {
                        if (generation != loadGeneration) return@runOnUiThread
                        if (!isAdded) {
                            setRefreshing(false)
                            return@runOnUiThread
                        }
                        if (replyList == null) replyList = ArrayList()
                        replyList!!.clear()
                        mergeLocalReplies()
                        ensureReplyAdapter()
                        setRefreshing(false)
                        MsgUtil.showMsgLong(getString(R.string.reply_port_empty_retry))
                    }
                    return@run
                }

                var workingType = type
                var workingSort = sort
                var list = ArrayList<Reply>()
                var pageState = ReplyApi.getRepliesLazy(aid, seek, "", workingType, workingSort, list)
                var result = pageState.first

                // 计数兜底：列表接口不一定给出总数，补一次用来判断"空列表到底是真没有还是没刷出来"
                if (count <= 0 && seek <= 0) {
                    try {
                        val declared = ReplyApi.getReplyCount(aid, workingType)
                        if (declared > 0) count = declared.toInt()
                    } catch (e: Exception) {
                        Log.e("ReplyFragment", "getReplyCount failed: ${e.message}")
                    }
                }

                // 第 3 层兜底：主路径"成功但没内容、而服务端说有评论"时，换排序 → 换评论区类型重拉。
                // 单次失败不换、有内容不换——换类型会改变这份列表的语义，不能随便动
                if (result != ReplyApi.PAGE_ERROR &&
                    ReplyFetchPolicy.shouldTryFirstPageFallback(list.size, count, seek)
                ) {
                    for (attempt in ReplyFetchPolicy.firstPageFallbackPlan(type, sort)) {
                        if (attempt.type == workingType && attempt.sort == workingSort) continue
                        val retryList = ArrayList<Reply>()
                        val retryState = ReplyApi.getRepliesLazy(aid, seek, "", attempt.type, attempt.sort, retryList)
                        if (retryState.first != ReplyApi.PAGE_ERROR && retryList.isNotEmpty()) {
                            workingType = attempt.type
                            workingSort = attempt.sort
                            list = retryList
                            pageState = retryState
                            result = retryState.first
                            Log.e("ReplyFragment", "first page fallback hit: type=$workingType sort=$workingSort")
                            break
                        }
                    }
                }

                if (generation != loadGeneration) return@run
                val nextPagination = pageState.second

                if (result == ReplyApi.PAGE_ERROR) {
                    // 第 2 层兜底：这一页彻底拉不到时，至少把本页自己刚发的评论显示出来，
                    // 用户看到的是"我发的还在"，而不是一片空白
                    runOnUiThread {
                        if (generation != loadGeneration) return@runOnUiThread
                        if (!isAdded) {
                            setRefreshing(false)
                            return@runOnUiThread
                        }
                        if (replyList == null) replyList = ArrayList()
                        replyList!!.clear()
                        mergeLocalReplies()
                        ensureReplyAdapter()
                        setRefreshing(false)
                        if (replyList!!.isEmpty()) MsgUtil.showMsgLong(getString(R.string.reply_port_empty_retry))
                        else MsgUtil.showMsgLong(getString(R.string.reply_port_local_only))
                    }
                    return@run
                }

                val finalEnd = result == ReplyApi.PAGE_END
                runOnUiThread {
                    // 不是当前世代说明又发起了新的刷新，转圈由新世代负责，这里不能去动它
                    if (generation != loadGeneration) return@runOnUiThread
                    // 已经不在界面上也要复位加载状态，守住"任何路径都必须 setRefreshing(false)"这条约定
                    if (!isAdded) {
                        setRefreshing(false)
                        return@runOnUiThread
                    }
                    Log.e("debug", if (finalEnd) "到底了" else "下一页")

                    // 换类型/换排序成功时把界面与 adapter 一起切过去，否则后续翻页会按错的类型去拉
                    if (workingType != type || workingSort != sort) {
                        type = workingType
                        replyType = workingType
                        sort = workingSort
                    }

                    pagination = nextPagination
                    if (finalEnd) {
                        isEnd = true
                        bottom = true
                    }

                    ReplyStateStore.instance.observeReplies(list, account, replyType, aid, read)
                    if (replyList == null) replyList = ArrayList()
                    replyList!!.clear()
                    for (item in list) {
                        if (item == null) continue
                        // 已在别处被删的评论不再回到列表（本地删除优先于服务端列表）
                        if (ReplyStateStore.instance.applyTo(item, account, replyType, aid)) continue
                        replyList!!.add(item)
                        if (item.rpid > 0) loadedRpids.add(item.rpid)
                    }
                    mergeLocalReplies()
                    ensureReplyAdapter()
                    if (replyList!!.isEmpty() && count > 0)
                        MsgUtil.showMsgLong(getString(R.string.reply_port_empty_retry))
                    // 列表已经交给界面了，加载状态必须复位，否则翻页会永久卡死
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (generation == loadGeneration) loadFail(e)
                }
            }
        }
    }

    private fun setOnSortSwitch() {
        replyAdapter!!.setOnSortSwitchListener {
            sort = if (sort == 2) 3 else 2
            replyAdapter!!.sort = this.sort
            refresh(aid)
        }
    }
}
