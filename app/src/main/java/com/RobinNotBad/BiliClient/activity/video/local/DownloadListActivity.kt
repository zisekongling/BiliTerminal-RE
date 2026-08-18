package com.RobinNotBad.BiliClient.activity.video.local

import android.annotation.SuppressLint
import android.content.Intent
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
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class DownloadListActivity : RefreshListActivity() {
    companion object {
        @JvmStatic
        var weakRef: WeakReference<DownloadListActivity>? = null
    }

    private var adapter: DownloadAdapter? = null
    private var timer: Timer? = null
    private var emptyTipShown: Boolean = false
    private var firstRefresh = true
    private var created: Boolean = false
    private var sections: ArrayList<DownloadSection>? = null

    // 底栏视图
    private var bottomBar: LinearLayout? = null
    private var barTotalProgress: ProgressBar? = null
    private var textTotalPercent: TextView? = null
    private var textTotalSpeed: TextView? = null
    private var textTotalEta: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setPageName("下载列表")
        setRefreshing(false)
        weakRef = WeakReference(this)

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
            refreshList(false)

            timer = Timer()
            timer!!.schedule(object : TimerTask() {
                override fun run() {
                    if (adapter == null || !created || isDestroyed)
                        return

                    // 同步全局速度和模式到适配器
                    adapter!!.lastSpeedStr = DownloadService.speedStr
                    adapter!!.lastSpeedMode = DownloadService.isSpeedMode

                    if (DownloadService.started) {
                        // 并行模式：找出所有正在下载的项目并刷新
                        val positionsToUpdate = findDownloadingPositions()
                        if (positionsToUpdate.isNotEmpty()) {
                            runOnUiThread {
                                for (pos in positionsToUpdate) {
                                    adapter!!.notifyItemChanged(pos)
                                }
                            }
                        }
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
     * 找出列表中所有正在下载的项目位置
     * 通过检查进度映射表来判断，避免依赖可能过期的内存数据
     */
    private fun findDownloadingPositions(): List<Int> {
        if (sections == null) return emptyList()
        val positions = mutableListOf<Int>()
        for (i in sections!!.indices) {
            val s = sections!![i]
            // 有进度映射即表示正在下载中
            if (DownloadService.getDownloadProgress(s.id) != null) {
                positions.add(i)
            }
        }
        return positions
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshList(fromOutside: Boolean) {
        if (this.isDestroyed || !created)
            return
        Log.d("debug", "刷新下载列表")

        sections = DownloadService.getAll()

        if (sections == null || sections!!.isEmpty()) {
            if (!emptyTipShown) {
                runOnUiThread {
                    MsgUtil.showMsg("下载列表为空")
                    showEmptyView()
                }
                emptyTipShown = true
            }
        } else {
            for (s in sections!!) {
                Log.d("debug-download", s.name_short)
            }

            if (emptyTipShown) {
                emptyTipShown = false
                runOnUiThread { hideEmptyView() }
            }

            if (firstRefresh) {
                adapter = DownloadAdapter(this, sections!!)
                adapter!!.setOnClickListener(object : OnItemClickListener {
                    override fun onItemClick(position: Int) {
                        CenterThreadPool.run {
                            Log.d("debug-download", "click:" + position)
                            if (sections == null || position < 0 || position >= sections!!.size)
                                return@run

                            val section = sections!![position]
                            when (section.state) {
                                // 点击下载中的任务：暂停该任务（不停止其他并行任务）
                                "downloading" -> {
                                    DownloadService.pauseDownload(section.id)
                                    runOnUiThread { MsgUtil.showMsg("已暂停下载") }
                                    refreshList(false)
                                }
                                // 点击已暂停的任务：恢复下载
                                "paused" -> {
                                    DownloadService.resumeDownload(section.id)
                                    runOnUiThread { MsgUtil.showMsg("已恢复下载") }
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

                adapter!!.setOnLongClickListener(object : OnItemLongClickListener {
                    private var longClickPosition = -1
                    private var longClickTimestamp = 0L

                    override fun onItemLongClick(position: Int) {
                        CenterThreadPool.run {
                            if (sections == null || position < 0 || position >= sections!!.size)
                                return@run

                            val now = System.currentTimeMillis()
                            if (longClickPosition == position && now - longClickTimestamp < 4000) {
                                // 第二次长按：确认删除（支持删除正在下载的任务，不停止其他任务）
                                longClickPosition = -1
                                deleteSectionItem(sections!![position])
                            } else {
                                longClickPosition = position
                                longClickTimestamp = now
                                runOnUiThread { MsgUtil.showMsg("再次长按删除") }
                            }
                        }
                    }
                })

                runOnUiThread { setAdapter(adapter!!) }
                firstRefresh = false
            } else {
                adapter!!.downloadList = sections!!
                runOnUiThread { adapter!!.notifyDataSetChanged() }
                Log.d("debug-adapter", adapter!!.itemCount.toString())
            }
        }
    }

    /**
     * 删除下载项（需在后台线程调用）。
     * 正在下载的任务先打暂停标记，让下载线程在下一轮 IO 循环退出，再删除记录与文件；
     * 不会停止整个下载服务，其他并行任务不受影响。
     */
    private fun deleteSectionItem(section: DownloadSection) {
        try {
            if (section.state == "downloading" || DownloadService.getDownloadProgress(section.id) != null) {
                DownloadService.pauseDownload(section.id)
            }

            val folder = section.getPath()
            if (folder != null && folder.exists()) {
                FileUtil.deleteFolder(folder)
            }

            DownloadService.deleteSection(section.id)
            DownloadService.pausedMap.remove(section.id)
            DownloadService.removeDownloadProgress(section.id)

            refreshList(false)
            runOnUiThread { MsgUtil.showMsg("删除成功") }
        } catch (e: Exception) {
            MsgUtil.err(e)
        }
    }

    private fun updateBottomBar() {
        if (bottomBar == null) return

        val currentSections = sections
        if (currentSections == null || currentSections.isEmpty() || !DownloadService.started) {
            bottomBar?.visibility = View.GONE
            return
        }

        // 总体进度 = (已完成 + 失败 + 各下载中项目进度之和) / (已完成 + 失败 + 下载中 + 等待中)
        // 由 DownloadService 依据批次统计与进度映射计算，中途新增任务也会被正确计入
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

        // 更新速度（所有并行下载的聚合速度）
        val speedText = when {
            DownloadService.speedStr.isNotEmpty() -> {
                val prefix = if (DownloadService.isSpeedMode) "高速 " else ""
                prefix + DownloadService.speedStr
            }
            downloadingCount > 0 -> "准备中..."
            else -> "--"
        }
        textTotalSpeed?.text = speedText

        // 更新预估剩余时间：用下载中项目的剩余字节数除以聚合速度
        val speedBytes = parseSpeedToBytes(DownloadService.speedStr)
        val etaStr = when {
            overallProgress >= 1f -> "即将完成"
            speedBytes > 0 && activeRemainingBytes > 0 ->
                formatDuration(activeRemainingBytes * 1000 / speedBytes)
            else -> "计算中..."
        }
        textTotalEta?.text = etaStr

        bottomBar?.visibility = View.VISIBLE
    }

    /**
     * 解析速度字符串为字节/秒
     */
    private fun parseSpeedToBytes(speedStr: String): Long {
        if (speedStr.isEmpty()) return 0
        try {
            val pattern = Regex("([\\d.]+)\\s*(MB/s|KB/s|B/s)")
            val match = pattern.find(speedStr)
            if (match != null) {
                val value = match.groupValues[1].toDouble()
                val unit = match.groupValues[2]
                return when (unit) {
                    "MB/s" -> (value * 1024 * 1024).toLong()
                    "KB/s" -> (value * 1024).toLong()
                    else -> value.toLong()
                }
            }
        } catch (e: Exception) {
            // 解析失败
        }
        return 0
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "即将完成"
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

    override fun onDestroy() {
        if (timer != null)
            timer!!.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        weakRef = null
        super.onDestroy()
    }
}