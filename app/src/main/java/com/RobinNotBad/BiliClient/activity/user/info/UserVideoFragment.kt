package com.RobinNotBad.BiliClient.activity.user.info

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

import androidx.annotation.NonNull
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.widget.ImageViewCompat

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListFragment
import com.RobinNotBad.BiliClient.adapter.video.UserVideoAdapter
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.google.android.material.appbar.AppBarLayout

/** 空间投稿的一个排序档位：[labelRes] 是弹窗里的文案，[order] 直接就是接口参数值。 */
private data class SpaceVideoSortOption(@StringRes val labelRes: Int, val order: String)

/**
 * 用户主页「TA 的投稿」。
 *
 * <p>列表走 [RefreshListFragment] 那套刷新/翻页/空态，本类只多两件事：
 * <b>空间内搜索</b>（关键词直传 `space/wbi/arc/search`，服务端过滤）和<b>排序</b>
 * （order=pubdate 最新发布 / click 最多播放 / stow 最多收藏）。
 *
 * <p>搜索与排序都会重拉第一页，所以加载结果必须按「代数」丢弃过期响应：连点搜索/排序时
 * 先发的请求可能后回来，不拦的话旧关键词的结果会把新结果覆盖掉。
 */
class UserVideoFragment : RefreshListFragment() {

    private var mid: Long = 0
    private var videoList: ArrayList<VideoCard> = ArrayList()
    private var adapter: UserVideoAdapter? = null

    private var keywordInput: EditText? = null
    private var sortButton: AppCompatImageButton? = null
    private var appBar: AppBarLayout? = null

    /** 当前关键词（已 trim）：空串 = 不过滤，等于「全部投稿」 */
    private var keyword = ""

    /** 当前排序档位。**@Volatile**：主线程写、加载线程读，避免后台读到旧档位拉回旧排序 */
    @Volatile
    private var sortOrder: String = UserInfoApi.ORDER_PUBDATE

    /**
     * 每次重拉第一页自增，迟到的旧响应直接丢弃。
     *
     * <p>自增必须发生在进后台线程**之前**：调用方（搜索/排序/下拉）一进来就先作废所有在途请求。
     */
    private var loadGeneration = 0

    companion object {
        /** 排序档位：与 web 端空间投稿的排序一致（顺序即弹窗里的顺序） */
        private val SORT_OPTIONS = listOf(
            SpaceVideoSortOption(R.string.text_space_video_sort_pubdate, UserInfoApi.ORDER_PUBDATE),
            SpaceVideoSortOption(R.string.text_space_video_sort_click, UserInfoApi.ORDER_CLICK),
            SpaceVideoSortOption(R.string.text_space_video_sort_stow, UserInfoApi.ORDER_STOW)
        )

        /**
         * 排序档位的视图状态键。
         *
         * 主题 / 密度变化会重建页面，`EditText` 的文字由视图状态自动恢复，字段不会；排序档位
         * 不一起恢复的话，重建后会出现「排序按钮显示最新发布、列表还是按播放排序」。
         */
        private const val STATE_SORT_ORDER = "space_video_sort_order"

        fun newInstance(mid: Long): UserVideoFragment {
            val fragment = UserVideoFragment()
            val args = Bundle()
            args.putLong("mid", mid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            mid = arguments!!.getLong("mid")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 比基类多一条搜索栏；列表仍是 fragment_simple_refresh（include 进去），
        // 基类 onViewCreated 按 id 找 emptyTip / swipeRefreshLayout / recyclerView 不受影响
        return inflater.inflate(R.layout.fragment_user_video, container, false)
    }

    override fun onViewCreated(@NonNull view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        videoList = ArrayList()
        appBar = view.findViewById(R.id.videoSearchAppBar)
        initSearchBar(view)
        setOnLoadMoreListener { page -> continueLoading(page) }
        // 注册下拉刷新：基类默认禁用且此前未注册，失败后只能退出重进；空态/失败态靠它重试
        setOnRefreshListener { loadFirstPage() }
        loadFirstPage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SORT_ORDER, sortOrder)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // 主题 / 密度变化会重建页面：EditText 的文字由视图状态自动恢复，`keyword` / `sortOrder`
        // 这两个字段不会。不同步的话会出现「输入框里是关键词、列表却是全部投稿」，
        // 或者「排序按钮回到最新发布、列表还是按播放排序」。
        // onViewCreated 那次首屏请求已经发出，这里发现条件不一致就按恢复后的条件重拉一次——
        // loadFirstPage 会自增代次，旧请求的结果会被丢掉，不会覆盖新结果。
        val restoredKeyword = keywordInput?.text?.toString()?.trim().orEmpty()
        val restoredOrder = UserInfoApi.normalizeOrder(savedInstanceState?.getString(STATE_SORT_ORDER))
        val keywordChanged = restoredKeyword != keyword
        val orderChanged = restoredOrder != sortOrder
        keyword = restoredKeyword
        if (orderChanged) {
            sortOrder = restoredOrder
            updateSortButton()
        }
        if (keywordChanged || orderChanged) loadFirstPage()
    }

    // ==================== 搜索栏与排序入口 ====================

    private fun initSearchBar(view: View) {
        keywordInput = view.findViewById(R.id.keywordInput)
        sortButton = view.findViewById(R.id.sortButton)

        // 软键盘右下角就是「搜索」：回车即搜，不用先点图标再收键盘
        keywordInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                submitSearch()
                true
            } else false
        }
        view.findViewById<View>(R.id.searchButton)?.setOnClickListener { submitSearch() }
        sortButton?.setOnClickListener { showSortDialog() }
        updateSortButton()
    }

    /** 取输入框当前内容当关键词；同一个词再点一次不重复请求（列表还在那儿） */
    private fun submitSearch() {
        val newKeyword = keywordInput?.text?.toString()?.trim().orEmpty()
        hideKeyboard()
        if (newKeyword == keyword) return
        keyword = newKeyword
        loadFirstPage()
    }

    private fun hideKeyboard() {
        val input = keywordInput ?: return
        input.clearFocus()
        val host = context ?: return
        val imm = host.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    /**
     * 排序按钮：图标没有文字，用 contentDescription 把「排序：最多播放」读给读屏；
     * 非默认档位时图标染成主题主色，一眼能看出当前不是「最新发布」。
     */
    private fun updateSortButton() {
        val button = sortButton ?: return
        val current = SORT_OPTIONS.firstOrNull { it.order == sortOrder } ?: SORT_OPTIONS[0]
        button.contentDescription =
            getString(R.string.text_space_video_sort_button, getString(current.labelRes))
        ImageViewCompat.setImageTintList(
            button,
            if (sortOrder == UserInfoApi.ORDER_PUBDATE) null
            else ColorStateList.valueOf(ColorScheme.PRIMARY)
        )
    }

    /**
     * 排序选择弹窗：与搜索页同一款 [TerminalDialog.singleChoice]（`▸` 引导符单选列表）。
     *
     * <p>这个弹窗**不会自动关闭**（选集场景要在回调里做越界校验），所以这里自己 dismiss。
     */
    private fun showSortDialog() {
        val host = context ?: return
        val labels = SORT_OPTIONS.map { getString(it.labelRes) }
        val checked = SORT_OPTIONS.indexOfFirst { it.order == sortOrder }.coerceAtLeast(0)
        TerminalDialog.singleChoice(
            context = host,
            title = getString(R.string.text_space_video_sort_title),
            items = labels,
            checked = checked
        ) { sheet, which ->
            sheet.dialog.dismiss()
            val picked = SORT_OPTIONS.getOrNull(which) ?: return@singleChoice
            if (picked.order == sortOrder) return@singleChoice
            sortOrder = picked.order
            updateSortButton()
            loadFirstPage()
        }.show()
    }

    // ==================== 列表 ====================

    /** 重拉第一页：换关键词 / 换排序 / 下拉刷新都走这里。 */
    private fun loadFirstPage() {
        val generation = ++loadGeneration
        val requestKeyword = keyword
        val requestOrder = sortOrder
        page = 1
        bottom = false
        setRefreshing(true)
        CenterThreadPool.run {
            val list = ArrayList<VideoCard>()
            try {
                val result = UserInfoApi.getUserVideos(mid, 1, requestKeyword, requestOrder, list)
                runOnUiThread {
                    // 期间又换了关键词/排序：这批是过期数据，丢掉（新请求会自己收尾）
                    if (generation != loadGeneration) return@runOnUiThread
                    if (result == -1) {
                        // 业务失败（接口没给 data / 没有 vlist）：与网络异常同口径，**不能**像成功
                        // 那样清空并重设列表——下拉刷新或换排序失败时上一批数据还在屏幕上，
                        // 清掉等于「一次抖动整页消失」。页码也留在第 1 页（见下面异常分支的说明）。
                        setRefreshing(false)
                        page = 1
                        if (videoList.isEmpty()) {
                            showEmptyView(getString(R.string.text_space_video_empty_failed))
                        } else {
                            MsgUtil.showMsgLong(getString(R.string.text_load_failed))
                        }
                        return@runOnUiThread
                    }
                    setRefreshing(false)
                    page = 1
                    videoList.clear()
                    videoList.addAll(list)
                    bottom = result == 1
                    adapter = UserVideoAdapter(requireContext(), mid, videoList)
                    setAdapter(adapter!!)
                    // 搜索/排序后要看到的一定是第一条结果，别停在上一批的滚动位置；
                    // 列表回到顶部后搜索栏也要跟着展开（AppBar 的收起状态是滚出来的，
                    // 程序滚回顶部不会自动展开，不展开的话搜索栏就一直看不见）
                    recyclerView.scrollToPosition(0)
                    appBar?.setExpanded(true, false)
                    if (videoList.isEmpty()) showEmptyView(emptyMessage(result, requestKeyword))
                    else hideEmptyView()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    // 旧请求的异常不要弹给用户
                    if (generation != loadGeneration) return@runOnUiThread
                    loadFail(e)
                    // 基类 loadFail 会 page--（它假设失败的是 goOnLoad 刚自增过的翻页请求）。
                    // 这里是「重拉第一页」，列表里留着的仍是第 1 页的内容，所以页码必须回到 1：
                    // 不然 page 变 0，之后触底时 goOnLoad 自增到 1，会把第 1 页整页**再追加一遍**。
                    page = 1
                    // 只有「数据没上屏」时才能换成失败空态：下拉刷新 / 换排序失败时 videoList 里还留着
                    // 上一批数据，无条件 showEmptyView 会把 recyclerView 设成 GONE，一次网络抖动
                    // 就让整页投稿消失（参考实现同一位置同样以 videoList.isEmpty() 为守卫）。
                    if (videoList.isEmpty()) showEmptyView(getString(R.string.text_space_video_empty_failed))
                }
            }
        }
    }

    private fun continueLoading(requestPage: Int) {
        // 参数名不能叫 page：会遮蔽基类的 page 字段，而这里要改的是字段（失败回退页码）
        val generation = loadGeneration
        val requestKeyword = keyword
        val requestOrder = sortOrder
        CenterThreadPool.run {
            val list = ArrayList<VideoCard>()
            try {
                val result = UserInfoApi.getUserVideos(mid, requestPage, requestKeyword, requestOrder, list)
                runOnUiThread {
                    if (generation != loadGeneration) return@runOnUiThread
                    if (result == -1) {
                        // 翻页失败：与基类 loadFail 同口径回退页码，滑一下重试同一页，不会跳过这一页
                        page--
                    } else if (list.isEmpty()) {
                        // 这一页空 = 到底了。不置 bottom 的话滚到底会反复请求同一个空页
                        bottom = true
                    } else {
                        videoList.addAll(list)
                        val current = adapter
                        if (current == null) {
                            // 首屏失败过（adapter 从未赋值）之后翻页又成功：补上 adapter 并收起空态
                            adapter = UserVideoAdapter(requireContext(), mid, videoList)
                            setAdapter(adapter!!)
                            hideEmptyView()
                        } else {
                            // UserVideoAdapter 带 header（position-1 才是数据下标）：
                            // 通知范围必须 +1 偏移，否则插入范围错位一格，出现重复/缺失 item
                            current.notifyItemRangeInserted(videoList.size - list.size + 1, list.size)
                        }
                        if (result == 1) bottom = true
                    }
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (generation == loadGeneration) loadFail(e)
                }
            }
        }
    }

    /** 空态/失败态文案：搜过和没搜过不是一回事，别让用户以为 TA 一条投稿都没有。 */
    private fun emptyMessage(result: Int, requestKeyword: String): CharSequence = when {
        result == -1 -> getString(R.string.text_space_video_empty_failed)
        requestKeyword.isNotEmpty() -> getString(R.string.text_space_video_empty_search)
        else -> getString(R.string.text_space_video_empty)
    }
}
