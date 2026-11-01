package com.RobinNotBad.BiliClient.activity.user.favorite

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter
import com.RobinNotBad.BiliClient.api.FavoriteApi
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.api.VideoInfoApi
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.VirtualCollectionPrefs

class FavoriteVideoListActivity : RefreshListActivity() {

    private var mid: Long = 0
    private var mediaId: Long = 0
    private var folderName: String = UiText.of("收藏夹")

    /** 别人的收藏夹：不能复制/移动/删除 */
    private var readOnly: Boolean = false

    /** 自己的收藏夹且拿得到 media_id：长按弹管理菜单 */
    private var writable: Boolean = false

    /**
     * 从「收藏夹管理页」带 {@code selectMode=true} 进来时置位：首屏加载完成后自动进多选，
     * 省掉「长按条目 → 菜单里点『多选』」这一步。
     * 只用一次——排序切换会重拉第一页，届时不能第二次自动进多选。
     */
    private var pendingSelectMode: Boolean = false

    private var videoList: ArrayList<VideoCard> = ArrayList()
    private var videoCardAdapter: VideoCardAdapter? = null

    /** 当前排序（x/v3/fav/resource/list 的 order 参数） */
    private var sortOrder: String = FavoriteApi.ORDER_FAV_TIME

    private var sortFavTime: TextView? = null
    private var sortView: TextView? = null
    private var sortPubtime: TextView? = null

    /** 多选删除：模式开关与已勾选的 aid（adapter 持有同一个集合引用） */
    private var selectionMode = false
    private val selectedAids = LinkedHashSet<Long>()
    private var manageToggle: TextView? = null
    private var manageDelete: TextView? = null

    /** 未选中档位的颜色，创建时从布局里的默认文字色抓一次 */
    private var unselectedColor: Int = 0

    /**
     * 长按菜单里的一项。
     *
     * 以前是 `Pair<String, () -> Unit>`，破坏性项只能靠显示文案反查（`it.first == "取消收藏"`）。
     * 文案一改（迁 strings.xml / 本地化）危险色就静默丢失，所以把身份与展示分开。
     */
    private class FavoriteAction(val text: String, val danger: Boolean, val action: () -> Unit)

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        mid = intent.getLongExtra("mid", 0)
        mediaId = intent.getLongExtra("mediaId", 0)
        folderName = intent.getStringExtra("name") ?: UiText.of("收藏夹")

        setPageName(folderName)

        videoList = ArrayList()

        // 自己的收藏夹也带 mediaId（排序/复制/移动/删除都要它）；
        // readOnly 由调用方显式指定，没指定时按「有 media_id 就是他人收藏夹」兜底
        readOnly = intent.getBooleanExtra("readOnly", mediaId > 0)
        writable = !readOnly && mediaId > 0
        pendingSelectMode = intent.getBooleanExtra("selectMode", false)

        setupSortBar()
        setupManageBar()
        setOnLoadMoreListener { page -> continueLoading(page) }
        loadFirstPage()
    }

    private fun setupSortBar() {
        findViewById<View>(R.id.sortBar).visibility = View.VISIBLE
        // 列表一滚动就把排序行收回去（手表屏小，收起来多露出一行视频）。
        // 多选条（manageBar）故意不在这里：用户正勾着视频呢，条自己收走就没法点删除了。
        setupAutoHideBars(findViewById(R.id.sortBar))
        sortFavTime = findViewById(R.id.sortFavTime)
        sortView = findViewById(R.id.sortView)
        sortPubtime = findViewById(R.id.sortPubtime)
        unselectedColor = sortFavTime?.currentTextColor ?: unselectedColor

        sortFavTime?.setOnClickListener { switchSort(FavoriteApi.ORDER_FAV_TIME) }
        sortView?.setOnClickListener { switchSort(FavoriteApi.ORDER_VIEW) }
        sortPubtime?.setOnClickListener { switchSort(FavoriteApi.ORDER_PUBTIME) }
        updateSortColors()
    }

    private fun switchSort(order: String) {
        if (order == sortOrder) return
        sortOrder = order
        updateSortColors()
        // 排序是服务端参数，必须从第一页重拉；重拉前先退出多选，避免勾选状态指向旧列表
        if (selectionMode) exitSelectionMode()
        // 列表马上要回到第一页，用户已经滚不回去了，排序行必须回到位
        expandAutoHideBars()
        page = 1
        bottom = false
        videoList.clear()
        videoCardAdapter?.notifyDataSetChanged()
        loadFirstPage()
    }

    private fun updateSortColors() {
        sortFavTime?.setTextColor(if (sortOrder == FavoriteApi.ORDER_FAV_TIME) ColorScheme.PRIMARY else unselectedColor)
        sortView?.setTextColor(if (sortOrder == FavoriteApi.ORDER_VIEW) ColorScheme.PRIMARY else unselectedColor)
        sortPubtime?.setTextColor(if (sortOrder == FavoriteApi.ORDER_PUBTIME) ColorScheme.PRIMARY else unselectedColor)
    }

    /**
     * 多选条：只在「能写」且**已经进入多选**时才出现。
     *
     * 它不进 [setupAutoHideBars]，可见性完全由多选态决定——这一点很关键：
     * 自动收回只认「可见且自然高度 > 0」的条，如果这条平时也常亮，滚动时会把它一起收走，
     * 多选时就点不到「删除」了。
     *
     * 入口改为「长按条目 → 菜单里选『多选』」（见 [showManageMenu]）：常亮的一整行
     * 只为了一个偶尔才用的功能占掉手表上宝贵的竖向空间，不划算。
     */
    private fun setupManageBar() {
        if (!writable) return
        manageToggle = findViewById(R.id.manageToggle)
        manageDelete = findViewById(R.id.manageDelete)
        if (unselectedColor == 0) unselectedColor = manageToggle?.currentTextColor ?: unselectedColor
        manageToggle?.setOnClickListener { if (selectionMode) exitSelectionMode() else enterSelectionMode() }
        manageDelete?.setOnClickListener { confirmBatchDelete() }
        updateManageBar()
    }

    private fun enterSelectionMode() {
        selectionMode = true
        selectedAids.clear()
        videoCardAdapter?.let {
            it.selectionMode = true
            it.notifyDataSetChanged()
        }
        updateManageBar()
        MsgUtil.showMsg(UiText.of("点条目勾选，再点「删除」"))
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedAids.clear()
        videoCardAdapter?.let {
            it.selectionMode = false
            it.notifyDataSetChanged()
        }
        updateManageBar()
    }

    private fun toggleSelected(position: Int) {
        val card = videoList.getOrNull(position) ?: return
        if (!selectedAids.add(card.aid)) selectedAids.remove(card.aid)
        videoCardAdapter?.notifyItemChanged(position)
        updateManageBar()
    }

    private fun updateManageBar() {
        findViewById<View>(R.id.manageBar).visibility = if (selectionMode) View.VISIBLE else View.GONE
        manageToggle?.text = if (selectionMode) UiText.of("退出多选") else UiText.of("多选")
        manageToggle?.setTextColor(if (selectionMode) ColorScheme.PRIMARY else unselectedColor)
        manageDelete?.text =
            if (selectedAids.isEmpty()) UiText.of("删除") else UiText.fmt("删除(%1\$d)", selectedAids.size)
        // 没选东西时把「删除」压暗，点了给提示而不是弹一个空确认框
        manageDelete?.alpha = if (selectedAids.isEmpty()) 0.5f else 1f
        manageDelete?.setTextColor(if (selectedAids.isEmpty()) unselectedColor else ColorScheme.PRIMARY)
    }

    private fun confirmBatchDelete() {
        if (selectedAids.isEmpty()) {
            MsgUtil.showMsg(UiText.of("先选几条吧~"))
            return
        }
        val targets = videoList.filter { selectedAids.contains(it.aid) }
        if (targets.isEmpty()) {
            MsgUtil.showMsg(UiText.of("先选几条吧~"))
            return
        }
        TerminalDialog.confirm(
            context = this,
            title = UiText.of("删除收藏内容"),
            message = UiText.fmt("确定把选中的 %1\$d 条从收藏夹里移除吗？", targets.size),
            confirmText = UiText.of("删除")
        ) {
            CenterThreadPool.run {
                try {
                    val code = FavoriteApi.batchDeleteResources(mediaId, targets)
                    runOnUiThread {
                        if (code == 0) {
                            MsgUtil.showMsg(UiText.fmt("已删除 %1\$d 条", targets.size))
                            applyRemoved(targets)
                            exitSelectionMode()
                        } else {
                            MsgUtil.showMsg(FavoriteApi.resourceErrorMsg(code))
                        }
                    }
                } catch (e: Exception) {
                    report(e)
                }
            }
        }.show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun applyRemoved(removed: List<VideoCard>) {
        videoList.removeAll(removed)
        videoCardAdapter?.notifyDataSetChanged()
        if (videoList.isEmpty()) showEmptyView()
    }

    /** 按当前排序拉一页（v3 接口只认 media_id，没有 media_id 就没法取内容） */
    private fun fetch(page: Int): Int =
        if (mediaId > 0) FavoriteApi.getFolderVideosNew(mediaId, page, videoList, sortOrder) else -1

    private fun loadFirstPage() {
        CenterThreadPool.run {
            try {
                val result = fetch(page)
                if (result == -1) {
                    loadFail()
                    return@run
                }
                if (result == 1) bottom = true
                runOnUiThread {
                    if (videoCardAdapter == null) {
                        videoCardAdapter = VideoCardAdapter(this@FavoriteVideoListActivity, videoList).also { bindAdapter(it) }
                        setAdapter(videoCardAdapter!!)
                    } else {
                        videoCardAdapter!!.notifyDataSetChanged()
                    }
                    if (videoList.isEmpty()) showEmptyView() else hideEmptyView()
                    // 带 selectMode 进来：首屏有内容就直接进多选。空收藏夹保持空态提示，别进一个没东西可选的多选态。
                    // 标记在这里就清掉（无论是否真的进了多选），排序切换重拉第一页时才不会再次自动进多选。
                    if (pendingSelectMode) {
                        pendingSelectMode = false
                        if (writable && videoList.isNotEmpty()) enterSelectionMode()
                    }
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    private fun bindAdapter(adapter: VideoCardAdapter) {
        adapter.selectedAids = selectedAids
        // 点击行为由两个独立开关决定（设置 → 高级与实验 → 功能开关）：
        // 「虚拟合集」只负责组合集，「收藏夹视频点击即播」负责点卡片是不是直接播；
        // 两个都关时回到默认行为——进视频详情页。
        // 开关在**点击时**读，不在绑定适配器时缓存一次：否则本页开着去设置里改完回来不生效。
        adapter.onItemClickListener = { position, videoCard ->
            when (VirtualCollectionPrefs.favoriteClickAction(
                selectionMode = selectionMode,
                collectionEnabled = VirtualCollectionPrefs.collectionEnabled(),
                clickPlayEnabled = VirtualCollectionPrefs.clickPlayEnabled()
            )) {
                // 多选模式下点击 = 勾选/取消勾选
                VirtualCollectionPrefs.ClickAction.SELECT -> toggleSelected(position)
                VirtualCollectionPrefs.ClickAction.PLAY_COLLECTION ->
                    playFavoriteVirtualCollection(position, videoCard.aid, mediaId, folderName)
                VirtualCollectionPrefs.ClickAction.PLAY_SINGLE -> playSingleFavoriteVideo(videoCard)
                // 功能二关闭（默认）：进视频详情页，想整夹连播走长按菜单
                VirtualCollectionPrefs.ClickAction.OPEN_DETAIL ->
                    TerminalContext.getInstance().enterVideoDetailPage(this, videoCard.aid, videoCard.bvid, "video")
            }
        }

        adapter.setOnLongClickListener { position -> onItemLongClick(position) }
    }

    /**
     * 功能一开着、但点卡片不会连播时，长按菜单补一个「以虚拟合集连播」入口。
     *
     * 功能二开着时点击本身就是整夹连播，菜单里再给一项只是重复；功能一关着时连播本身就不该提供。
     */
    private fun shouldOfferCollectionPlay(): Boolean =
        VirtualCollectionPrefs.collectionEnabled() && !VirtualCollectionPrefs.clickPlayEnabled()

    private fun onItemLongClick(position: Int): Boolean {
        // 多选模式下长按也是勾选，不再弹管理菜单
        if (selectionMode) {
            toggleSelected(position)
            return true
        }
        // 自己的收藏夹：长按弹管理菜单（详情/复制/移动/多选/取消收藏）
        if (writable) {
            showManageMenu(position)
            return true
        }
        // 别人的收藏夹（只读）：写操作一概不给，但**看信息是只读的**，所以只弹「视频详情」。
        // 之前这里直接 return false，长按回落成"快速缓存"，用户在这个页面连详情页都进不去
        // （点击又会被虚拟合集连播接管）。
        if (readOnly) {
            showReadOnlyMenu(position)
            return true
        }
        // 兜底：拿不到 media_id 时按只读处理（v3 接口下正常不会走到这里，
        // 老 x/space/fav/arc 链路已随接口迁移整体移除）
        showReadOnlyMenu(position)
        return true
    }

    /** 别人收藏夹（只读）的长按菜单：视频详情 +（功能一开着时）以虚拟合集连播。 */
    private fun showReadOnlyMenu(position: Int) {
        val card = videoList.getOrNull(position) ?: return
        val actions = ArrayList<FavoriteAction>()
        actions.add(FavoriteAction(UiText.of("视频详情"), false) {
            TerminalContext.getInstance().enterVideoDetailPage(this, card.aid, card.bvid, "video")
        })
        if (shouldOfferCollectionPlay()) {
            actions.add(FavoriteAction(UiText.of("以虚拟合集连播"), false) {
                playFavoriteVirtualCollection(position, card.aid, mediaId, folderName)
            })
        }
        TerminalDialog.menu(
            context = this,
            title = card.title,
            items = actions.map { it.text }
        ) { which -> actions[which].action() }.show()
    }

    private fun showManageMenu(position: Int) {
        val card = videoList.getOrNull(position) ?: return
        val actions = ArrayList<FavoriteAction>()
        // 默认点击在虚拟合档下是「整个收藏夹连播」，用户想去某一条的详情页时没有入口，
        // 只能关掉虚拟合集开关——所以这里补一个直达详情页的项。
        // 放第一位：它是唯一「看信息」而非「改收藏夹结构」的动作。
        actions.add(FavoriteAction(UiText.of("视频详情"), false) {
            TerminalContext.getInstance().enterVideoDetailPage(this, card.aid, card.bvid, "video")
        })
        // 功能二关闭（默认）时点击不连播，这里补第二个播放入口：整夹连播。
        // 功能二开着时点击本身就是连播，不必重复给。
        if (shouldOfferCollectionPlay()) {
            actions.add(FavoriteAction(UiText.of("以虚拟合集连播"), false) {
                playFavoriteVirtualCollection(position, card.aid, mediaId, folderName)
            })
        }
        actions.add(FavoriteAction(UiText.of("复制到…"), false) { showTargetFolderPicker(card, false) })
        actions.add(FavoriteAction(UiText.of("移动到…"), false) { showTargetFolderPicker(card, true) })
        // 多选的入口。原来靠列表顶上一整行常亮的「多选」按钮，用户反馈那一行平时白占地方，
        // 所以把入口收进长按菜单、并把那一行改成只在多选态出现（见 setupManageBar）。
        if (writable) actions.add(FavoriteAction(UiText.of("多选"), false) { enterSelectionMode() })
        // 「取消收藏」是破坏性操作，用 danger 标出危险色（不再按显示文案找出它）
        actions.add(FavoriteAction(UiText.of("取消收藏"), true) { confirmRemoveFavorite(card) })
        TerminalDialog.menu(
            context = this,
            title = card.title,
            items = actions.map { it.text },
            danger = actions.indices.filter { actions[it].danger }.toSet()
        ) { which -> actions[which].action() }.show()
    }

    /** 拉自己的收藏夹列表让用户选目标（排除当前这个） */
    private fun showTargetFolderPicker(card: VideoCard, move: Boolean) {
        CenterThreadPool.run {
            try {
                val targets = FavoriteApi.getFavoriteFolders(mid).filter { it.mediaId > 0 && it.mediaId != mediaId }
                runOnUiThread {
                    if (targets.isEmpty()) {
                        MsgUtil.showMsg(UiText.of("没有别的收藏夹可以放喵~"))
                        return@runOnUiThread
                    }
                    TerminalDialog.menu(
                        context = this@FavoriteVideoListActivity,
                        title = if (move) UiText.of("移动到…") else UiText.of("复制到…"),
                        items = targets.map { it.name }
                    ) { which -> transfer(card, targets[which].mediaId, move) }.show()
                }
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    private fun transfer(card: VideoCard, targetMediaId: Long, move: Boolean) {
        CenterThreadPool.run {
            try {
                val code = if (move) FavoriteApi.moveResources(mediaId, targetMediaId, listOf(card))
                else FavoriteApi.copyResources(mediaId, targetMediaId, listOf(card))
                runOnUiThread {
                    if (code == 0) {
                        MsgUtil.showMsg(if (move) UiText.of("已移动") else UiText.of("已复制"))
                        if (move) removeItem(card)
                    } else {
                        MsgUtil.showMsg(FavoriteApi.resourceErrorMsg(code))
                    }
                }
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    private fun confirmRemoveFavorite(card: VideoCard) {
        TerminalDialog.confirm(
            context = this,
            title = UiText.of("取消收藏"),
            message = UiText.fmt("确定把《%1\$s》从收藏夹里去掉吗？", card.title),
            confirmText = UiText.of("确定")
        ) {
            CenterThreadPool.run {
                try {
                    // 带上 favType：收藏夹里可能有音频/视频合集，写死 2 会把它们的 id 当成 avid 发出去
                    val code = FavoriteApi.deleteFavorite(card.aid, card.favType, mediaId)
                    runOnUiThread {
                        if (code == 0) {
                            MsgUtil.showMsg(UiText.of("已取消收藏"))
                            removeItem(card)
                        } else {
                            MsgUtil.showMsg(UiText.fmt("取消失败，错误码：%1\$s", code))
                        }
                    }
                } catch (e: Exception) {
                    report(e)
                }
            }
        }.show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun removeItem(card: VideoCard) {
        // 按 aid 反查而不是 indexOf：列表可能已经刷新过，对象相等性不可靠，aid 才是这条稿件的身份
        val index = videoList.indexOfFirst { it.aid == card.aid }
        if (index < 0) return
        videoList.removeAt(index)
        videoCardAdapter?.notifyItemRemoved(index)
        videoCardAdapter?.notifyItemRangeChanged(index, videoList.size - index)
        if (videoList.isEmpty()) showEmptyView()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val lastSize = videoList.size
                val result = fetch(page)
                if (result != -1) {
                    Log.e("debug", "下一页")
                    runOnUiThread { videoCardAdapter!!.notifyItemRangeInserted(lastSize, videoList.size - lastSize) }
                    if (result == 1) {
                        Log.e("debug", "到底了")
                        bottom = true
                    }
                }
                setRefreshing(false)
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    /**
     * 只播这一条视频（功能二「点卡片直接播放」开着、功能一「虚拟合集」关着）。
     *
     * <p>播放列表用 `VideoInfo` 自己的分P：多P 视频会带上它的各分P，和详情页点播放的效果一致；
     * 不组成收藏夹合集，所以不带 `virtualCollection` 标记。
     *
     * <p>`enterableInfo` 仍要带过去：这条要是多P 或属于某个真实合集，播放器的选集卡片上
     * 才会有「进入分P / 进入合集」那一行（否则播放器拿不到它的 videoInfo）。
     */
    private fun playSingleFavoriteVideo(card: VideoCard) {
        CenterThreadPool.run {
            try {
                val videoInfo = VideoInfoApi.getVideoInfo(card.aid)
                if (videoInfo == null || videoInfo.cids.isEmpty()) {
                    runOnUiThread { MsgUtil.showMsg(UiText.of("获取视频信息失败")) }
                    return@run
                }
                val playerData = videoInfo.toPlayerData(0)
                playerData.enterableInfo = videoInfo
                runOnUiThread {
                    try {
                        PlayerApi.startGettingUrl(playerData)
                    } catch (e: Exception) {
                        MsgUtil.err(e)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
            }
        }
    }

    /**
     * 播放收藏夹虚拟合集：将收藏夹内的所有视频组成合集，传入播放器实现连续播放
     * @param startPosition 点击的视频在列表中的位置
     * @param startAid 点击的视频aid（作为起始播放位置）
     * @param folderId 收藏夹ID
     * @param folderName 收藏夹名称
     */
    private fun playFavoriteVirtualCollection(startPosition: Int, startAid: Long, folderId: Long, folderName: String) {
        if (videoList.isEmpty()) {
            MsgUtil.showMsg(UiText.of("收藏夹为空"))
            return
        }

        // 在后台线程获取起始视频的cid
        CenterThreadPool.run {
            try {
                // 构建合集的pagenames和cids
                val pagenames = ArrayList<String>()
                val cids = ArrayList<Long>()
                var startPageIndex = 0
                var firstCid: Long = 0

                for ((i, v) in videoList.withIndex()) {
                    pagenames.add(v.title)
                    cids.add(v.aid) // 使用aid作为标识，实际播放时会重新获取cid
                    if (v.aid == startAid) {
                        startPageIndex = i
                    }
                }

                // 获取起始视频的cid
                val firstAid = if (startPosition < videoList.size) videoList[startPosition].aid else startAid
                val videoInfo = VideoInfoApi.getVideoInfo(firstAid)
                if (videoInfo != null && videoInfo.cids.isNotEmpty()) {
                    firstCid = videoInfo.cids[0]
                }

                if (firstCid <= 0) {
                    runOnUiThread { MsgUtil.showMsg(UiText.of("获取视频信息失败")) }
                    return@run
                }

                // 构建PlayerData
                val playerData = PlayerData(PlayerData.TYPE_VIDEO)
                playerData.title = UiText.fmt("%1\$s（虚拟合集）", folderName)
                playerData.aid = firstAid
                playerData.cid = firstCid
                playerData.pagenames = pagenames
                playerData.cids = cids
                playerData.currentPageIndex = startPageIndex
                // 标记"cids 里装的是 aid 不是 cid"：播放器切集时必须靠这个标志走对分支，
                // 否则会把 aid 当 cid（或反过来）去取流
                playerData.virtualCollection = true

                // **点开一条不等于进入它的"文件夹"**（26.10.16 用户明确要求改成手动）：
                // 这里只起播收藏夹这条列表。若这一条是多P 视频或属于某个真实合集，
                // 播放器的选集卡片上会多出一行「进入分P / 进入合集」，由用户点了才换列表。
                // 自动进入会在你只想顺着收藏夹听的时候，突然被换成一门 105 讲课程的分P 列表，
                // 收藏夹里后面的条目全被跳过——所以不做。
                //
                // 但"这一条能不能进得去"得让播放器知道：起播这条不走播放器的 switchToOnlinePage，
                // 它拿不到 videoInfo。上面这次请求已经拿到了，顺手带过去（省掉播放器再请求一次）。
                playerData.enterableInfo = videoInfo

                // 在主线程启动播放
                runOnUiThread {
                    try {
                        PlayerApi.startGettingUrl(playerData)
                    } catch (e: Exception) {
                        MsgUtil.err(e)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.err(e) }
            }
        }
    }
}