package com.RobinNotBad.BiliClient.activity.video.local

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.video.DownloadAdapter
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.model.DownloadSection
import com.RobinNotBad.BiliClient.service.DownloadService
import com.RobinNotBad.BiliClient.service.formatDownloadSpeed
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.DownloadListDiff
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class DownloadListActivity : RefreshListActivity() {
    companion object {
        @JvmStatic
        var weakRef: WeakReference<DownloadListActivity>? = null
    }

    /**
     * 列表 adapter：**整个页面只建一个实例**，之后每次刷新只换它手里的 list。
     *
     * 标记 @Volatile 是因为 Timer 线程（[onCreate] 里那个）会读它来跳过「还没建好」的 tick，
     * 而写它的是主线程（[render]）——没有 happens-before 边的话，Timer 可能长期读到 null。
     */
    @Volatile
    private var adapter: DownloadAdapter? = null
    private var timer: Timer? = null
    private var emptyTipShown: Boolean = false
    @Volatile
    private var created: Boolean = false

    /**
     * 当前这一批下载记录。与 [DownloadAdapter.downloadList] 是**同一个实例**，且只在主线程
     * （[render]）里一起换掉；后台线程（Timer / 条目点击）先取一份局部快照再用，
     * 不要在同一个方法里两次读这个字段。
     */
    @Volatile
    private var sections: ArrayList<DownloadSection>? = null

    // 多选删除（26.11.09）：长按条目进入多选，勾选后走顶部 manageBar 的「删除(n)」。
    private var selectionMode = false
    private val selectedIds = LinkedHashSet<Long>()
    private var manageToggle: TextView? = null
    private var manageSelectAll: TextView? = null
    private var manageDelete: TextView? = null
    private var unselectedColor = 0

    // 底栏视图
    private var bottomBar: LinearLayout? = null
    private var barTotalProgress: ProgressBar? = null
    private var textTotalPercent: TextView? = null
    private var textTotalSpeed: TextView? = null
    private var textTotalEta: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setPageName(UiText.of("下载列表"))
        setRefreshing(false)
        weakRef = WeakReference(this)

        // 26.11.16 崩溃 #353（OWW263 / sdk30）：本页 Timer 每 400ms 逐行 notifyItemChanged，
        // 下载结束或增删任务时还会整表 notifyDataSetChanged；这个组合进行到一半（mState 还是
        // 上一帧的条数）时，GapWorker 仍按旧位置预取，tryGetViewHolderForPositionByDeadline
        // 取不到 holder 就直接抛「Inconsistency detected. Invalid item position 3(offset:3).state:4」。
        // 它崩在 GapWorker 的预取回调里，CustomLinearManager.onLayoutChildren 的 try/catch 兜不到。
        // 本页列表只有几行、又靠 notify 高频刷新，预取本来就没有收益，索性关掉这条路径。
        recyclerView.layoutManager?.isItemPrefetchEnabled = false

        setupSelectionBar()

        // 添加底栏到根布局
        val rootLayout = findViewById<View>(R.id.swipeRefreshLayout).parent as? android.view.ViewGroup
        if (rootLayout != null) {
            val barView = LayoutInflater.from(this).inflate(R.layout.download_bottom_bar, rootLayout, false)
            bottomBar = barView.findViewById(R.id.download_bottom_bar)
            barTotalProgress = barView.findViewById(R.id.bar_total_progress)
            textTotalPercent = barView.findViewById(R.id.text_total_percent)
            textTotalSpeed = barView.findViewById(R.id.text_total_speed)
            textTotalEta = barView.findViewById(R.id.text_total_eta)
            rootLayout.addView(barView)
            // 调整 SwipeRefreshLayout 使其在底栏上方
            val swipeLp = swipeRefreshLayout.layoutParams as? RelativeLayout.LayoutParams
            swipeLp?.addRule(RelativeLayout.ABOVE, R.id.download_bottom_bar)
        }

        CenterThreadPool.run {
            created = true
            // 顺手收拾历史失败任务的目录残留（反馈 #136）：升级前失败的任务会留下
            // `.DOWNLOADING`，视频详情页看到目录就只弹「已在下载队列」，于是永远点不进下载。
            DownloadService.cleanupFailedResidues()
            refreshList(false)

            timer = Timer()
            timer!!.schedule(object : TimerTask() {
                override fun run() {
                    if (adapter == null || !created || isDestroyed)
                        return

                    if (DownloadService.started) {
                        // 逐条进度：位置必须按「主线程上 adapter 此刻真正挂着的那份 list」算。
                        // 原来是在 Timer 线程先算好位置再 post notifyItemChanged，那条路和
                        // 「后台换表 + 主线程 notifyDataSetChanged」交错时，算出的位置会落在
                        // 已经换掉的新表之外（详见 docs/agents/pitfalls.md 里同一主线程 turn 那条）。
                        runOnUiThread { refreshProgressRows() }
                    } else {
                        // 下载已结束，隐藏底栏
                        runOnUiThread { bottomBar?.visibility = View.GONE }
                    }

                    // 更新底栏
                    runOnUiThread { updateBottomBar() }
                }
            }, 300, 400)
        }
    }

    /**
     * 主线程：把「正在下载」的那几行重绑一次。
     *
     * 这条路径每 400ms 跑一趟，只走逐条 `notifyItemChanged`，绝不整表重建：重建会把滚动位置、
     * 正在推进的进度条和封面一起弄没。位置按 [DownloadAdapter.downloadList] 现算，
     * 因此永远落在这个 adapter 真正持有、并且已经通知过的主线程数据上。
     */
    private fun refreshProgressRows() {
        if (!created || isDestroyed) return
        val current = adapter ?: return
        for (position in downloadingPositions(current.downloadList)) {
            current.notifyItemChanged(position)
        }
    }

    /**
     * 找出列表中所有正在下载的项目位置。
     * 通过检查进度映射表来判断，避免依赖可能过期的内存数据。
     */
    private fun downloadingPositions(list: List<DownloadSection>): List<Int> =
        list.indices.filter { DownloadService.getDownloadProgress(list[it].id) != null }

    /**
     * 刷新下载列表。取数在调用线程（后台）跑，落地一律回主线程。
     *
     * **取数可以留在后台，换表与通知必须一起回主线程**：`DownloadService.getAll()` 是 DB IO，
     * 不能挪到主线程；而「把新 list 赋给 [DownloadAdapter.downloadList]」会让 `getItemCount()`
     * 立刻变成新长度，此时 ViewHolder 还按旧数据挂着，主线程下一趟 layout 就会抛
     * `IndexOutOfBoundsException: Inconsistency detected. Invalid view holder adapter position`
     * （线上 26.11.09.02 / OPPO OWW211 那条崩溃，与 docs/agents/pitfalls.md 记的是同一个坑）。
     */
    fun refreshList(fromOutside: Boolean) {
        if (this.isDestroyed || !created)
            return
        Log.d("debug", "刷新下载列表")

        val fetched = DownloadService.getAll()
        runOnUiThread { render(fetched) }
    }

    /**
     * 主线程：把一批取数结果整份落到界面（换表 + 通知 + 勾选集 + 空态 + 底栏）。
     *
     * <h3>为什么不像收藏夹列表页那样「换 list 就换 adapter」</h3>
     * 收藏夹那页换 adapter 是硬要求（它的 adapter 开了 stable id 并按位置取卡片）；本页不同：
     * adapter 由 Timer 每 400ms 逐条 `notifyItemChanged` 推进度，整表换实例会把这条高频路径
     * 打断（新实例上还得重挂两个监听器、重带多选态），而且 `setAdapter()` 会把列表滚回顶部——
     * 用户「边下边滚」时会莫名其妙跳回去。所以这里**同一个 adapter 实例**换表，只要
     * 「换 `getItemCount()` 的数据」与「通知」落在同一个主线程 turn 里（本方法整段都在主线程
     * 消息里跑，中间不会被 layout 插进来），RecyclerView 的一致性校验就看不到中间态。
     *
     * 结构没变时只逐条通知状态变了的行（见 [com.RobinNotBad.BiliClient.util.DownloadListDiff]），
     * 免得每次状态回包都把整屏重绑一遍。
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun render(fetched: ArrayList<DownloadSection>?) {
        if (isDestroyed || !created) return
        val list = fetched ?: ArrayList()
        val previous: List<DownloadSection> = sections ?: emptyList()

        // 换表：sections 与 adapter.downloadList 始终是同一个实例，且只在主线程一起换
        sections = list
        val current = adapter
        if (current == null) {
            // 首屏：adapter 在主线程建并挂上，别在后台建好、过一会儿再 setAdapter
            adapter = DownloadAdapter(this, list).also {
                it.selectedIds = selectedIds
                it.selectionMode = selectionMode
                it.setOnClickListener(object : OnItemClickListener {
                    override fun onItemClick(position: Int) {
                        // 多选态下点击 = 勾选/取消勾选，不再暂停/恢复任务
                        if (selectionMode) {
                            runOnUiThread { toggleSelected(position) }
                            return
                        }
                        CenterThreadPool.run {
                            Log.d("debug-download", "click:" + position)
                            // 位置是按绑定时的数据算的：这里取一份快照再校验，
                            // 别在「判越界」和「取条目」之间让后台换掉这张表
                            val snapshot = sections ?: return@run
                            if (position < 0 || position >= snapshot.size)
                                return@run

                            val section = snapshot[position]
                            when (section.state) {
                                // 点击下载中的任务：暂停该任务（不停止其他并行任务）
                                "downloading" -> {
                                    DownloadService.pauseDownload(section.id)
                                    runOnUiThread { MsgUtil.showMsg(UiText.of("已暂停下载")) }
                                    refreshList(false)
                                }
                                // 点击已暂停的任务：恢复下载
                                "paused" -> {
                                    DownloadService.resumeDownload(section.id)
                                    runOnUiThread { MsgUtil.showMsg(UiText.of("已恢复下载")) }
                                    refreshList(false)
                                }
                                "error" -> {
                                    DownloadService.setState(section.id, "none")
                                    DownloadService.start(section.id)
                                }
                                else -> DownloadService.start(section.id)
                            }
                        }
                    }
                })

                it.setOnLongClickListener(object : OnItemLongClickListener {
                    override fun onItemLongClick(position: Int) {
                        // 长按回调本身就在主线程：sections 只在主线程换，读一次就够
                        val snapshot = sections ?: return
                        if (position < 0 || position >= snapshot.size)
                            return
                        // 长按进入多选并勾中这一条（取代原来的「再长按一次删除」）；
                        // 多选态下长按同样是勾选/取消勾选。
                        if (selectionMode) toggleSelected(position)
                        else enterSelectionMode(position)
                    }
                })
            }
            setAdapter(adapter!!)
        } else {
            current.downloadList = list
            // 「换 getItemCount() 的数据」与「通知」就是这两行，同一个主线程 turn 里做完
            if (DownloadListDiff.sameStructure(previous, list)) {
                for (position in DownloadListDiff.changedPositions(previous, list)) {
                    current.notifyItemChanged(position)
                }
            } else {
                current.notifyDataSetChanged()
            }
        }

        // 已删除/已消失的任务不能继续留在勾选里，否则「删除(n)」会拿旧 id 去删一个不存在的项。
        // 列表空了就没得选；只是「勾选项没了」则留在多选里，让用户重新勾。
        if (selectionMode) {
            val alive = list.mapTo(HashSet(list.size * 2)) { it.id }
            selectedIds.retainAll(alive)
            if (alive.isEmpty()) exitSelectionMode()
        }
        updateSelectionBar()

        if (list.isEmpty()) {
            if (!emptyTipShown) {
                emptyTipShown = true
                MsgUtil.showMsg(UiText.of("下载列表为空"))
                showEmptyView()
            }
        } else {
            if (emptyTipShown) {
                emptyTipShown = false
                hideEmptyView()
            }
            for (s in list) {
                Log.d("debug-download", s.name_short)
            }
        }
        Log.d("debug-adapter", (adapter?.itemCount ?: 0).toString())
    }

    // ==================== 多选删除（26.11.09） ====================

    /**
     * 复用 activity_simple_refresh 的 manageBar：非多选态整条隐藏，长按条目进入多选后才出现
     * 「退出多选 / 全选 / 删除(n)」。下载列表底部另有一条常驻的下载总进度条，不抢它的位置。
     */
    private fun setupSelectionBar() {
        manageToggle = findViewById(R.id.manageToggle)
        manageSelectAll = findViewById(R.id.manageSelectAll)
        manageDelete = findViewById(R.id.manageDelete)
        if (unselectedColor == 0) unselectedColor = manageToggle?.currentTextColor ?: 0
        manageToggle?.setOnClickListener { exitSelectionMode() }
        manageSelectAll?.setOnClickListener { toggleSelectAll() }
        manageDelete?.setOnClickListener { confirmDeleteSelected() }
        updateSelectionBar()
    }

    private fun updateSelectionBar() {
        val bar = findViewById<View>(R.id.manageBar) ?: return
        if (!selectionMode) {
            bar.visibility = View.GONE
            return
        }
        bar.visibility = View.VISIBLE
        manageToggle?.text = getString(R.string.uitext_favoritevideolistactivity_013)
        manageToggle?.setTextColor(ColorScheme.PRIMARY)
        // 全选 / 全不选（反馈 #94）：已经全勾中时文字变「全不选」，再点一次取消全部
        val total = sections?.size ?: 0
        val allSelected = total > 0 && selectedIds.size >= total
        manageSelectAll?.visibility = View.VISIBLE
        manageSelectAll?.text =
            if (allSelected) getString(R.string.btn_unselect_all) else getString(R.string.btn_select_all)
        manageSelectAll?.setTextColor(ColorScheme.PRIMARY)
        manageDelete?.text =
            if (selectedIds.isEmpty()) getString(R.string.accountswitch_delete_confirm)
            else getString(R.string.uitext_favoritevideolistactivity_017, selectedIds.size)
        manageDelete?.alpha = if (selectedIds.isEmpty()) 0.5f else 1f
        manageDelete?.setTextColor(if (selectedIds.isEmpty()) unselectedColor else ColorScheme.PRIMARY)
    }

    private fun enterSelectionMode(initialPosition: Int) {
        if (selectionMode) return
        selectionMode = true
        selectedIds.clear()
        sections?.getOrNull(initialPosition)?.let { selectedIds.add(it.id) }
        adapter?.let {
            it.selectionMode = true
            it.selectedIds = selectedIds
            it.notifyDataSetChanged()
        }
        updateSelectionBar()
        MsgUtil.showMsg(getString(R.string.msg_download_select_hint))
    }

    private fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        selectedIds.clear()
        adapter?.let {
            it.selectionMode = false
            it.notifyDataSetChanged()
        }
        updateSelectionBar()
    }

    private fun toggleSelected(position: Int) {
        val section = sections?.getOrNull(position) ?: return
        if (!selectedIds.remove(section.id)) selectedIds.add(section.id)
        adapter?.notifyItemChanged(position)
        updateSelectionBar()
    }

    /**
     * 全选 / 全不选（反馈 #94）：一键勾中当前下载列表的全部任务；已经全勾中时再点一次取消全部。
     *
     * 只动内存里的勾选集，不碰任何任务状态（暂停 / 恢复仍走条目的点击）；列表刷新时
     * [refreshList] 会自己把已经失效的 id 摘掉，所以这里不需要校验 id 是否还活着。
     */
    private fun toggleSelectAll() {
        if (!selectionMode) return
        val all = sections ?: return
        if (all.isEmpty()) {
            MsgUtil.showMsg(getString(R.string.uitext_downloadlistactivity_003))
            return
        }
        val allSelected = selectedIds.size >= all.size
        selectedIds.clear()
        if (!allSelected) for (section in all) selectedIds.add(section.id)
        adapter?.notifyDataSetChanged()
        updateSelectionBar()
    }

    private fun selectedSections(): List<DownloadSection> {
        val current = sections ?: return emptyList()
        return current.filter { selectedIds.contains(it.id) }
    }

    private fun confirmDeleteSelected() {
        val targets = selectedSections()
        if (targets.isEmpty()) {
            MsgUtil.showMsg(getString(R.string.uitext_favoritevideolistactivity_005))
            return
        }
        TerminalDialog.confirm(
            context = this,
            title = getString(R.string.text_download_delete_title),
            message = getString(R.string.text_download_delete_message, targets.size),
            confirmText = getString(R.string.accountswitch_delete_confirm),
            cancelText = getString(R.string.btn_cancel),
            confirmIsDanger = true
        ) {
            CenterThreadPool.run { deleteSections(targets) }
        }.show()
    }

    /**
     * 批量删除（需在后台线程调用）。
     * 正在下载的任务先打暂停标记，让下载线程在下一轮 IO 循环退出，再删除记录与文件；
     * 不会停止整个下载服务，其他并行任务不受影响。
     * 逐条删除成功与否只影响汇总提示，不中断整批。
     */
    private fun deleteSections(targets: List<DownloadSection>) {
        var deleted = 0
        var failed = 0
        var fileLeft = 0
        for (section in targets) {
            val result = deleteSectionData(section)
            if (result.deleted) deleted++
            else failed++
            if (result.fileLeft) fileLeft++
        }
        runOnUiThread { exitSelectionMode() }
        refreshList(false)
        runOnUiThread {
            val msg = when {
                failed > 0 && fileLeft > 0 ->
                    getString(R.string.msg_download_delete_partial_all, deleted, failed, fileLeft)
                failed > 0 -> getString(R.string.uitext_locallistactivity_030, deleted, failed)
                fileLeft > 0 -> getString(R.string.msg_download_delete_file_left, deleted, fileLeft)
                else -> getString(R.string.uitext_favoritevideolistactivity_019, deleted)
            }
            MsgUtil.showMsgLong(msg)
        }
    }

    /** 删一条：先暂停，再删文件与记录；返回记录是否删掉、文件是否没删干净。 */
    private fun deleteSectionData(section: DownloadSection): DeleteResult {
        return try {
            if (section.state == "downloading" || DownloadService.getDownloadProgress(section.id) != null) {
                DownloadService.pauseDownload(section.id)
            }

            // 用 peekPath() 而不是 getPath()：后者会对不存在的视频目录顺手 mkdirs，
            // 先建一个空目录再 exists() 必然为真，就判不出文件原本在不在、也报不出「文件未删干净」。
            val folder = section.peekPath()
            val fileGone = !folder.exists() || FileUtil.deleteFolderRecursively(folder)

            DownloadService.deleteSection(section.id)
            DownloadService.pausedMap.remove(section.id)
            DownloadService.removeDownloadProgress(section.id)
            DeleteResult(true, !fileGone)
        } catch (e: Exception) {
            MsgUtil.err(e)
            DeleteResult(false, false)
        }
    }

    private data class DeleteResult(val deleted: Boolean, val fileLeft: Boolean)

    private fun updateBottomBar() {
        if (bottomBar == null) return

        val currentSections = sections
        if (currentSections == null || currentSections.isEmpty() || !DownloadService.started) {
            bottomBar?.visibility = View.GONE
            return
        }

        // 总体进度按**字节**算：已下字节 / 全批次总字节。已完成的任务出队后由 batchStats
        // 继续贡献字节，所以中途新增/结束任务都不会让进度条往回掉（见 DownloadSectionBytes）
        val overallProgress = DownloadService.computeOverallProgress(currentSections)
        val activeRemainingBytes = DownloadService.getActiveRemainingBytes(currentSections)

        // 统计下载中/等待中项目数（失败项目计入已结束，不参与分母之外的进度）
        var downloadingCount = 0
        var activeCount = 0
        var waitingCount = 0
        for (s in currentSections) {
            if (DownloadService.getDownloadProgress(s.id) != null) {
                downloadingCount++
                activeCount++
            } else if (s.state == "none") {
                waitingCount++
            }
        }

        // 更新进度条
        barTotalProgress?.let {
            it.max = 1000
            it.progress = (overallProgress * 1000).toInt()
        }

        // 更新进度文本：已完成/总任务 | X% | 并行数
        val doneUnits = DownloadService.batchStats.completed + DownloadService.batchStats.failed
        val totalUnits = doneUnits + activeCount + waitingCount
        val parallelInfo = if (downloadingCount > 1) " ${downloadingCount}并行" else ""
        textTotalPercent?.text = String.format(Locale.CHINA, "%d/%d%s (%.1f%%)",
            doneUnits, totalUnits, parallelInfo, overallProgress * 100)

        // 更新总速度：各活跃任务速度之和（字节/秒），0 表示还没测出来（刚开下或都卡住了）
        val speedBytes = DownloadService.getTotalSpeedBps()
        textTotalSpeed?.text = when {
            speedBytes > 0 -> formatDownloadSpeed(speedBytes)
            downloadingCount > 0 -> UiText.of("准备中...")
            else -> "--"
        }

        // 更新预估剩余时间：下载中项目的剩余字节数 / 总速度
        val etaStr = when {
            overallProgress >= 1f -> UiText.of("即将完成")
            speedBytes > 0 && activeRemainingBytes > 0 ->
                formatDuration(activeRemainingBytes * 1000 / speedBytes)
            else -> UiText.of("计算中...")
        }
        textTotalEta?.text = etaStr

        bottomBar?.visibility = View.VISIBLE
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return UiText.of("即将完成")
        val seconds = ms / 1000
        return when {
            seconds < 60 -> "${seconds}秒"
            seconds < 3600 -> {
                val min = seconds / 60
                val sec = seconds % 60
                "${min}分${sec}秒"
            }
            else -> {
                val hour = seconds / 3600
                val min = (seconds % 3600) / 60
                "${hour}时${min}分"
            }
        }
    }

    override fun onBackPressed() {
        // 多选态下按返回只退出多选，不直接离开下载列表（与缓存页/收藏夹多选一致）
        if (selectionMode) {
            exitSelectionMode()
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (timer != null)
            timer!!.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        weakRef = null
        super.onDestroy()
    }
}