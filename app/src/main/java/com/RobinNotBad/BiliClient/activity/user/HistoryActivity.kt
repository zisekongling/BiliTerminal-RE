package com.RobinNotBad.BiliClient.activity.user

import com.RobinNotBad.BiliClient.util.UiText

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.video.HistoryVideoCardAdapter
import com.RobinNotBad.BiliClient.api.HistoryApi
import com.RobinNotBad.BiliClient.model.ApiResult
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FavoriteSearchPolicy
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog

/**
 * 观看记录页。
 *
 * <p>批次 1 起，长按条目不再是「长按两次删除」的隐藏手势，而是弹出菜单：
 * 「删除这条记录」/「多选删除」。多选删除用终端弹窗自己拼的勾选列表，
 * 上半屏是已加载的记录、底部是「删除已选」；一次最多删 [HistoryApi.MAX_BATCH_DELETE] 条。
 *
 * <p>删除只复用既有的单条删除接口 [HistoryApi.deleteHistory]（批量是循环调用），
 * 没有新增任何写接口——B 站没有批量删历史的公开接口，硬造一个只会多一处不可用代码。
 */
class HistoryActivity : RefreshListActivity() {

    private var lastResult = ApiResult()
    private var videoList: ArrayList<VideoCard> = ArrayList()
    private var videoCardAdapter: HistoryVideoCardAdapter? = null

    /**
     * 已从服务端加载的全部记录；[videoList] 只是它的「搜索结果视图」。
     *
     * <p>历史接口（`x/web-interface/history/cursor`）的参数只有 max/business/view_at/type/ps，
     * **没有 keyword/q**（见 `bilibili-API/docs/historytoview/history.md:17-23`），
     * 所以搜索只能在已加载记录里做客户端过滤，界面上要如实说明搜索范围。
     */
    private val allHistory: ArrayList<VideoCard> = ArrayList()
    private var historyKeyword: String = ""
    private var historySearchBar: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPageName(UiText.of("历史记录"))

        recyclerView.setHasFixedSize(true)

        videoList = ArrayList()
        setupSearchBar()

        CenterThreadPool.run {
            try {
                val list: ArrayList<VideoCard> = ArrayList()
                lastResult = HistoryApi.getHistory(lastResult, list)
                if (lastResult.code == 0) {
                    runOnUiThread {
                        allHistory.addAll(list)
                        // 适配器在 UI 线程创建：它要绑的 videoList 刚被过滤视图填过
                        applyHistoryFilter()
                        videoCardAdapter = HistoryVideoCardAdapter(this, videoList)
                        videoCardAdapter!!.setOnLongClickListener { position ->
                            showRecordMenu(position)
                        }
                        setOnLoadMoreListener { page -> continueLoading(page) }
                        setRefreshing(false)
                        setAdapter(videoCardAdapter!!)
                        if (lastResult.isBottom) bottom = true
                    }
                } else {
                    MsgUtil.showMsg(lastResult.message)
                }

            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val list: MutableList<VideoCard> = ArrayList()
                lastResult = HistoryApi.getHistory(lastResult, list)
                if (lastResult.code == 0) {
                    runOnUiThread {
                        allHistory.addAll(list)
                        applyHistoryFilter()
                    }
                    if (lastResult.isBottom) {
                        bottom = true
                    }
                }
                setRefreshing(false)
                onLoadComplete()
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    // ==================== 历史记录搜索（客户端过滤已加载记录） ====================

    /**
     * 搜索入口：复用公共布局的 `filterBar`（隐藏它自带的三项「稍后再看」筛选）。
     *
     * <p>不新增 res/layout：写权限里没有布局目录，且手表屏多一行就少一条记录。
     */
    private fun setupSearchBar() {
        val filterBar = findViewById<LinearLayout>(R.id.filterBar) ?: return
        filterBar.visibility = View.VISIBLE
        findViewById<View>(R.id.filterAll)?.visibility = View.GONE
        findViewById<View>(R.id.filterUnfinished)?.visibility = View.GONE
        findViewById<View>(R.id.clearWatched)?.visibility = View.GONE

        val bar = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
            minHeight = resources.getDimensionPixelSize(R.dimen.touch_min)
            val padV = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
            setPadding(0, padV, 0, padV)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isSingleLine = true
            setOnClickListener { promptSearch() }
        }
        historySearchBar = bar
        filterBar.addView(bar)
        updateSearchBar()
    }

    private fun updateSearchBar() {
        val bar = historySearchBar ?: return
        bar.text =
            if (historyKeyword.isEmpty()) getString(R.string.fav_port_history_search_entry)
            else getString(R.string.fav_port_history_search_active, historyKeyword)
    }

    private fun promptSearch() {
        if (historyKeyword.isEmpty()) showSearchInput() else showSearchMenu()
    }

    private fun showSearchInput() {
        TerminalDialog.input(
            context = this,
            title = getString(R.string.fav_port_history_search_dialog_title),
            hint = getString(R.string.fav_port_history_search_hint),
            initialText = historyKeyword,
            confirmText = getString(R.string.fav_port_search_confirm),
            cancelText = getString(R.string.fav_port_search_cancel)
        ) { keyword -> applyKeyword(keyword) }.show()
    }

    /** 已有关键词时的菜单：修改 / 清除（输入框留空不会回调，清除必须走这里）。 */
    private fun showSearchMenu() {
        TerminalDialog.menu(
            context = this,
            title = getString(R.string.fav_port_history_search_current, historyKeyword),
            items = listOf(
                getString(R.string.fav_port_history_search_edit),
                getString(R.string.fav_port_history_search_clear)
            )
        ) { which ->
            if (which == 0) showSearchInput() else applyKeyword("")
        }.show()
    }

    private fun applyKeyword(keyword: String) {
        val normalized = keyword.trim()
        if (normalized == historyKeyword) return
        historyKeyword = normalized
        updateSearchBar()
        // 历史搜索没有服务端接口，过滤范围就是「已加载的这么多条」，说清楚免得用户以为搜了全部
        if (historyKeyword.isNotEmpty()) {
            MsgUtil.showMsg(getString(R.string.fav_port_history_search_scope, allHistory.size))
        }
        applyHistoryFilter()
    }

    /** 用 [historyKeyword] 重建 [videoList]（显示视图），并同步空态文案。 */
    private fun applyHistoryFilter() {
        val filtered = FavoriteSearchPolicy.filter(allHistory, historyKeyword)
        videoList.clear()
        videoList.addAll(filtered)
        videoCardAdapter?.updateList(videoList)
        if (videoList.isEmpty()) {
            emptyView?.text =
                if (historyKeyword.isEmpty()) getString(R.string.text_list_empty)
                else getString(R.string.fav_port_history_search_empty)
            showEmptyView()
        } else {
            hideEmptyView()
        }
    }

    // ==================== 长按菜单（批次 1） ====================

    private fun showRecordMenu(position: Int) {
        if (position < 0 || position >= videoList.size) return
        val card = videoList[position]
        TerminalDialog.menu(
            context = this,
            title = card.title,
            items = listOf(
                getString(R.string.social_history_delete_single),
                getString(R.string.social_history_delete_multi)
            ),
            // 0 = 删除这条记录，破坏性操作走危险色
            danger = setOf(0)
        ) { which ->
            when (which) {
                0 -> confirmDeleteSingle(card)
                1 -> showBatchDeleteDialog(position)
            }
        }.show()
    }

    /** 删除单条：先确认（记录删除不可恢复），成功后直接本地移除，不整页重拉。 */
    private fun confirmDeleteSingle(card: VideoCard) {
        TerminalDialog.confirm(
            context = this,
            title = getString(R.string.social_history_delete_title),
            message = getString(R.string.social_history_delete_single_message, card.title),
            confirmText = getString(R.string.social_history_delete_confirm),
            cancelText = getString(R.string.social_history_cancel)
        ) {
            CenterThreadPool.run {
                try {
                    val code = HistoryApi.deleteHistory(card.aid, card.bvid ?: "")
                    runOnUiThread {
                        if (code == 0) {
                            MsgUtil.showMsg(getString(R.string.social_history_deleted_ok, 1))
                            removeDeletedFromList(listOf(card.aid))
                        } else {
                            MsgUtil.showMsg(getString(R.string.social_history_delete_failed_code, code))
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { report(e) }
                }
            }
        }.show()
    }

    // ==================== 多选删除（批次 1） ====================

    /**
     * 多选删除面板：终端弹窗的勾选列表。
     *
     * <p>为什么不用系统 `AlertDialog.setMultiChoiceItems`：本工程的弹窗统一走 [TerminalDialog]
     * （裸 `AlertDialog.Builder` 是 AGENTS.md 明令禁止的写法），而 [TerminalDialog] 目前只有
     * 菜单/单选/确认/输入四种形态，没有多选。这里直接用它的 [TerminalDialog.Sheet] 拼一个：
     * 行是 record，右侧 `✓` 表示已勾选，点行切换；顶部一行显示「已选 N 条」与「全选/取消全选」。
     */
    private fun showBatchDeleteDialog(initialPosition: Int) {
        // 快照：按 aid 去重、跳过脏条目、最多 MAX_BATCH_DELETE 条
        val snapshot = HistoryApi.buildSelectableSnapshot(videoList, HistoryApi.MAX_BATCH_DELETE)
        if (snapshot.isEmpty()) {
            MsgUtil.showMsg(getString(R.string.social_history_batch_empty))
            return
        }
        // 长按的那条默认勾上，省一次点击
        val initialAid = if (initialPosition >= 0 && initialPosition < videoList.size) videoList[initialPosition].aid else 0
        val checked = BooleanArray(snapshot.size) { index -> snapshot[index].aid == initialAid }

        // 第三个参数 bottomButtonBar：勾选列表可能很长（最多 100 条），按钮必须钉在滚动区外面，
        // 否则用户要一路划到底才能看到「删除已选」，等于没有按钮
        val sheet = TerminalDialog.Sheet(this, getString(R.string.social_history_delete_title), true)

        val countRow = sheet.addItem(
            getString(R.string.social_history_selected_count, HistoryApi.countChecked(checked)), onClick = {}
        )
        // 计数行只读：撤掉点击回调与引导符，别让它看着像能点
        countRow.root.setOnClickListener(null)
        countRow.root.isClickable = false
        countRow.root.isFocusable = false
        countRow.glyph.visibility = View.GONE

        val selectAllRow = sheet.addItem(getString(R.string.social_history_select_all), onClick = {})

        val rows = ArrayList<TerminalDialog.Row>()
        snapshot.forEach { card ->
            rows.add(sheet.addItem(batchRowTitle(card), onClick = {}))
        }

        /** 重绘某一行：选中态用与单选一致的「▸ + 主色 + 右侧 ✓」。 */
        fun paintRow(index: Int) {
            val row = rows[index]
            val on = checked[index]
            row.glyph.text = if (on) TerminalDialog.GLYPH_ACTIVE else TerminalDialog.GLYPH_IDLE
            row.glyph.setTextColor(if (on) sheet.primaryColor() else sheet.secondaryColor())
            row.text.setTextColor(if (on) sheet.primaryColor() else sheet.onSurfaceColor())
            row.state.text = if (on) TerminalDialog.GLYPH_CHECK else ""
        }

        /** 重绘全部：行状态 + 计数 + 全选按钮文案。 */
        fun refresh() {
            for (i in rows.indices) paintRow(i)
            val count = HistoryApi.countChecked(checked)
            countRow.text.text = getString(R.string.social_history_selected_count, count)
            selectAllRow.text.text = getString(
                if (HistoryApi.isAllSelected(checked)) R.string.social_history_deselect_all
                else R.string.social_history_select_all
            )
        }

        /** 切换单行：超上限时不动（提示由调用方在本函数里弹）。 */
        fun toggle(index: Int) {
            if (!checked[index] && HistoryApi.exceedsBatchLimit(HistoryApi.countChecked(checked) + 1)) {
                MsgUtil.showMsg(getString(R.string.social_history_batch_limit, HistoryApi.MAX_BATCH_DELETE))
                return
            }
            checked[index] = !checked[index]
            refresh()
        }

        /** 全选/取消全选：全选时也只勾到上限，剩下的留给下一批。 */
        fun toggleAll() {
            val target = !HistoryApi.isAllSelected(checked)
            var selected = 0
            for (i in checked.indices) {
                if (!target) {
                    checked[i] = false
                } else {
                    if (selected >= HistoryApi.MAX_BATCH_DELETE) {
                        checked[i] = false
                    } else {
                        checked[i] = true
                        selected++
                    }
                }
            }
            refresh()
            if (target && snapshot.size > HistoryApi.MAX_BATCH_DELETE) {
                MsgUtil.showMsg(getString(R.string.social_history_batch_limit, HistoryApi.MAX_BATCH_DELETE))
            }
        }

        // 行点击在函数都就绪后再挂，避免闭包捕获到未初始化的局部函数
        rows.forEachIndexed { index, row -> row.root.setOnClickListener { toggle(index) } }
        selectAllRow.root.setOnClickListener { toggleAll() }

        sheet.addButton(getString(R.string.social_history_cancel), sheet.secondaryColor()) {
            sheet.dialog.dismiss()
        }
        sheet.addButton(getString(R.string.social_history_delete_selected), sheet.errorColor()) {
            val aids = HistoryApi.collectSelectedAids(snapshot, checked)
            if (aids.isEmpty()) {
                // 一条没勾就不关窗：关掉再重选更烦
                MsgUtil.showMsg(getString(R.string.social_history_batch_empty))
                return@addButton
            }
            sheet.dialog.dismiss()
            confirmDeleteBatch(aids)
        }

        refresh()
        sheet.setHint(getString(R.string.social_history_batch_hint))
        sheet.prepareLayout()
        sheet.dialog.show()
    }

    /** 多选条目太长会把表盘整屏占满，按 40 字截断（只是展示，删除仍按 aid）。 */
    private fun batchRowTitle(card: VideoCard): String {
        val title = card.title ?: ""
        return if (title.length > 40) title.substring(0, 40) + "…" else title
    }

    private fun confirmDeleteBatch(aids: List<Long>) {
        if (HistoryApi.exceedsBatchLimit(aids.size)) {
            MsgUtil.showMsg(getString(R.string.social_history_batch_limit, HistoryApi.MAX_BATCH_DELETE))
            return
        }
        TerminalDialog.confirm(
            context = this,
            title = getString(R.string.social_history_delete_title),
            message = getString(R.string.social_history_delete_batch_message, aids.size),
            confirmText = getString(R.string.social_history_delete_confirm),
            cancelText = getString(R.string.social_history_cancel)
        ) {
            CenterThreadPool.run {
                val succeeded = HistoryApi.deleteHistoryBatch(aids)
                val okCount = succeeded.size
                val failCount = aids.size - okCount
                runOnUiThread {
                    val msg = when {
                        okCount == 0 -> getString(R.string.social_history_deleted_none)
                        failCount == 0 -> getString(R.string.social_history_deleted_ok, okCount)
                        else -> getString(R.string.social_history_deleted_partial, okCount, failCount)
                    }
                    MsgUtil.showMsg(msg)
                    if (okCount > 0) removeDeletedFromList(succeeded)
                }
            }
        }.show()
    }

    /** 把已删除的记录从本地列表移除：服务端已经是删除态，本地再等一次刷新没有意义。 */
    private fun removeDeletedFromList(deletedAids: List<Long>) {
        if (deletedAids.isEmpty()) return
        val deleted = HashSet(deletedAids)
        // 全量列表同步移除：否则清掉搜索词后，已删除的记录会「复活」
        allHistory.removeAll { deleted.contains(it.aid) }
        applyHistoryFilter()
    }
}
