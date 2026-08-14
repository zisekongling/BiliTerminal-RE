package com.RobinNotBad.BiliClient.activity.video.local

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ConfirmDialogActivity
import com.RobinNotBad.BiliClient.activity.InputDialogActivity
import com.RobinNotBad.BiliClient.activity.ListDialogActivity
import com.RobinNotBad.BiliClient.activity.video.QualityChooserActivity
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.adapter.video.CacheListAdapter
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.LocalVideo
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.VideoMeta
import com.RobinNotBad.BiliClient.model.VideoFolder
import com.RobinNotBad.BiliClient.service.DownloadService
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.FolderManager
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.VideoMetaManager
import java.io.File

class LocalListActivity : InstanceActivity() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTip: TextView

    private val folderList = ArrayList<VideoFolder>()
    private val videoList = ArrayList<LocalVideo>()
    private var adapter: CacheListAdapter? = null

    // 对话框回调存储
    private var pendingConfirmCallback: (() -> Unit)? = null
    private var pendingListCallback: ((Int) -> Unit)? = null
    private var pendingInputCallback: ((String) -> Unit)? = null

    // Activity 结果启动器
    private val confirmLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = pendingConfirmCallback
        pendingConfirmCallback = null
        if (result.resultCode == RESULT_OK) cb?.invoke()
    }
    private val listLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = pendingListCallback
        pendingListCallback = null
        if (result.resultCode == RESULT_OK) {
            val pos = result.data?.getIntExtra("selected_position", -1) ?: -1
            cb?.invoke(pos)
        }
    }
    private val inputLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = pendingInputCallback
        pendingInputCallback = null
        if (result.resultCode == RESULT_OK) {
            val text = result.data?.getStringExtra("input_text") ?: ""
            cb?.invoke(text)
        }
    }

    // RecyclerView滚动状态
    var isRecyclerViewScrolling: Boolean = false
        private set

    private var started: Boolean = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_main_refresh)

        recyclerView = findViewById(R.id.recyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener { refresh() }
        emptyTip = findViewById(R.id.emptyTip)

        val pageName = findViewById<TextView>(R.id.pageName)
        pageName.text = "缓存"

        findViewById<View>(R.id.top).setOnClickListener {
            if (adapter?.currentFolderName?.isNotEmpty() == true) {
                exitFolder()
            } else {
                menuClick.run()
            }
        }

        if (!FileUtil.checkStoragePermission()) {
            FileUtil.requestStoragePermission(this)
        }

        CenterThreadPool.run {
            runOnUiThread { swipeRefreshLayout.setRefreshing(true) }
            loadData()
            setupAdapter()
            started = true
        }
    }

    private fun loadData() {
        // 加载文件夹
        folderList.clear()
        folderList.addAll(FolderManager.getAllFolders())

        // 扫描视频
        videoList.clear()
        scanVideos(FileUtil.getVideoDownloadPath())

        // 加载每个视频的元数据
        for (video in videoList) {
            try {
                val videoDir = File(FileUtil.getVideoDownloadPath(), video.title)
                val meta = VideoMetaManager.readMeta(videoDir)
                video.folderName = meta.folderName
                video.aid = meta.aid
                video.cid = meta.cid
            } catch (_: Exception) {}
        }
    }

    private fun scanVideos(folder: File) {
        val files = folder.listFiles() ?: return

        for (video in files) {
            if (video.isDirectory) {
                val localVideo = LocalVideo()
                localVideo.title = video.name
                localVideo.cover = File(video, "cover.png").toString()
                localVideo.pageList = ArrayList()
                localVideo.danmakuFileList = ArrayList()
                localVideo.videoFileList = ArrayList()
                localVideo.sizeList = ArrayList()
                localVideo.qualityList = ArrayList()

                val qualityFile = File(video, ".quality")
                val qualityStr = if (qualityFile.exists()) {
                    try { qualityFile.readText().trim() } catch (e: Exception) { "" }
                } else ""

                val videoFile = File(video, "video.mp4")
                val audioFile = File(video, "audio.m4a")
                val danmakuFile = File(video, "danmaku.xml")
                val tempMergeFile = File(video, "video_merged_temp.mp4")

                // 优先使用合并后的视频文件，其次视频流，最后音频流
                val mediaFile = if (videoFile.exists()) videoFile
                    else if (tempMergeFile.exists()) {
                        // 合并临时文件存在，重命名为正式文件
                        if (tempMergeFile.renameTo(videoFile)) videoFile
                        else tempMergeFile
                    }
                    else (if (audioFile.exists()) audioFile else null)

                if (mediaFile != null) {
                    val mark = File(video, ".DOWNLOADING")
                    if (mark.exists()) continue

                    localVideo.sizeList.add(mediaFile.length())
                    localVideo.videoFileList.add(mediaFile.toString())
                    localVideo.danmakuFileList.add(danmakuFile.toString())

                    // 如果合并失败，video.mp4和audio.m4a同时存在，把audio.m4a也加入列表供播放器使用
                    if (videoFile.exists() && audioFile.exists() && mediaFile == videoFile) {
                        localVideo.videoFileList.add(audioFile.toString())
                    }

                    if (qualityStr == "audio_only" || (audioFile.exists() && !videoFile.exists())) {
                        localVideo.qualityList.add("仅音频")
                    } else if (qualityStr.isNotEmpty()) {
                        val qn = try { qualityStr.toInt() } catch (e: Exception) { 0 }
                        val label = LocalVideo.getQualityLabel(qn)
                        if (label.isNotEmpty()) localVideo.qualityList.add(label)
                    }

                    localVideo.calcTotalSize()
                    videoList.add(localVideo)
                } else {
                    // 合集视频：扫描子目录
                    val pages = video.listFiles()
                    if (pages != null) {
                        for (page in pages) {
                            if (page.isDirectory) {
                                val mark = File(page, ".DOWNLOADING")
                                if (mark.exists()) continue

                                val pageVideoFile = File(page, "video.mp4")
                                val pageAudioFile = File(page, "audio.m4a")
                                val pageDanmakuFile = File(page, "danmaku.xml")
                                val pageTempMergeFile = File(page, "video_merged_temp.mp4")
                                val pageMediaFile = if (pageVideoFile.exists()) pageVideoFile
                                    else if (pageTempMergeFile.exists()) {
                                        if (pageTempMergeFile.renameTo(pageVideoFile)) pageVideoFile
                                        else pageTempMergeFile
                                    }
                                    else (if (pageAudioFile.exists()) pageAudioFile else null)

                                if (pageMediaFile != null) {
                                    localVideo.pageList.add(page.name)
                                    localVideo.sizeList.add(pageMediaFile.length())
                                    localVideo.videoFileList.add(pageMediaFile.toString())
                                    localVideo.danmakuFileList.add(pageDanmakuFile.toString())

                                    // 如果合并失败，video.mp4和audio.m4a同时存在，把audio.m4a也加入列表
                                    if (pageVideoFile.exists() && pageAudioFile.exists() && pageMediaFile == pageVideoFile) {
                                        localVideo.videoFileList.add(pageAudioFile.toString())
                                    }

                                    val pageQualityFile = File(page, ".quality")
                                    if (pageQualityFile.exists()) {
                                        try {
                                            val pqnStr = pageQualityFile.readText().trim()
                                            if (pqnStr == "audio_only") {
                                                localVideo.qualityList.add("仅音频")
                                            } else {
                                                val pqn = pqnStr.toInt()
                                                val label = LocalVideo.getQualityLabel(pqn)
                                                if (label.isNotEmpty()) localVideo.qualityList.add(label)
                                                else localVideo.qualityList.add("")
                                            }
                                        } catch (_: Exception) { localVideo.qualityList.add("") }
                                    } else { localVideo.qualityList.add("") }
                                }
                            }
                        }
                        localVideo.calcTotalSize()
                        if (localVideo.videoFileList.size > 0) videoList.add(localVideo)
                    }
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupAdapter() {
        adapter = CacheListAdapter(this)

        // 文件夹操作回调
        adapter!!.onFolderClick = { idx -> enterFolder(idx) }
        adapter!!.onNewFolderClick = { showNewFolderDialog() }
        adapter!!.onFolderRename = { idx -> showRenameDialog(idx) }
        adapter!!.onFolderDisband = { idx -> showDisbandConfirmDialog(idx) }
        adapter!!.onBackFromFolder = { exitFolder() }

        adapter!!.onVideoUpdateDanmaku = { idx -> updateDanmaku(idx) }
        adapter!!.onVideoSwitchQuality = { idx -> switchQuality(idx) }
        adapter!!.onVideoDelete = { idx -> deleteVideo(idx, false) }
        adapter!!.onVideoMoveToFolder = { idx -> showMoveToFolderDialog(idx) }
        adapter!!.onVideoRemoveFromFolder = { idx -> removeVideoFromFolder(idx) }
        adapter!!.onVideoLongClick = { idx -> showDeleteHint() }
        adapter!!.onVideoViewDetail = { idx -> viewVideoDetail(idx) }
        adapter!!.onVideoPlayInVirtualCollection = { folderName, videoIdx -> playVirtualCollection(folderName, videoIdx) }
        adapter!!.isRecyclerViewScrolling = { isRecyclerViewScrolling }

        adapter!!.setData(folderList, videoList)

        runOnUiThread {
            recyclerView.layoutManager = getLayoutManager()
            recyclerView.adapter = adapter
            
            // 添加滚动监听器
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    isRecyclerViewScrolling = when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> false
                        RecyclerView.SCROLL_STATE_DRAGGING, RecyclerView.SCROLL_STATE_SETTLING -> true
                        else -> false
                    }
                }
            })
            
            swipeRefreshLayout.setRefreshing(false)
            checkEmpty()
        }
    }

    // ===================== 文件夹操作 =====================

    private fun enterFolder(folderIndex: Int) {
        if (folderIndex < 0 || folderIndex >= folderList.size) return
        val folder = folderList[folderIndex]
        adapter?.setData(folderList, videoList, folder.name)
        val pageName = findViewById<TextView>(R.id.pageName)
        pageName?.text = folder.name
    }

    private fun exitFolder() {
        adapter?.setData(folderList, videoList, "")
        val pageName = findViewById<TextView>(R.id.pageName)
        pageName?.text = "缓存"
    }

    private fun showNewFolderDialog() {
        val intent = Intent(this, InputDialogActivity::class.java)
            .putExtra("title", "新建文件夹")
            .putExtra("hint", "请输入文件夹名称")
        pendingInputCallback = { name ->
            if (!FolderManager.isValidFolderName(name)) {
                MsgUtil.showMsg("名称无效（不能为空、超过30字符或包含特殊字符）")
            } else if (FolderManager.createFolder(name)) {
                MsgUtil.showMsg("文件夹创建成功")
                refresh()
            } else {
                MsgUtil.showMsg("文件夹名称已存在或创建失败")
            }
        }
        inputLauncher.launch(intent)
    }

    private fun showRenameDialog(folderIndex: Int) {
        if (folderIndex < 0 || folderIndex >= folderList.size) return
        val folder = folderList[folderIndex]
        val intent = Intent(this, InputDialogActivity::class.java)
            .putExtra("title", "重命名文件夹")
            .putExtra("initial_text", folder.name)
        pendingInputCallback = { newName ->
            if (!FolderManager.isValidFolderName(newName)) {
                MsgUtil.showMsg("名称无效")
            } else if (FolderManager.renameFolder(folder.name, newName)) {
                MsgUtil.showMsg("重命名成功")
                refresh()
            } else {
                MsgUtil.showMsg("重命名失败（名称可能已存在）")
            }
        }
        inputLauncher.launch(intent)
    }

    private fun showDisbandConfirmDialog(folderIndex: Int) {
        if (folderIndex < 0 || folderIndex >= folderList.size) return
        val folder = folderList[folderIndex]
        showConfirmDialog("拆散文件夹", "确定要拆散「${folder.name}」吗？\n文件夹内的视频将移回未分类区域。") {
            disbandFolder(folderIndex)
        }
    }

    private fun disbandFolder(folderIndex: Int) {
        if (folderIndex < 0 || folderIndex >= folderList.size) return
        val folder = folderList[folderIndex]
        CenterThreadPool.run {
            if (FolderManager.deleteFolder(folder.name)) {
                // 如果正在查看该文件夹内部，退出
                if (adapter?.currentFolderName == folder.name) {
                    runOnUiThread { exitFolder() }
                }
                runOnUiThread {
                    MsgUtil.showMsg("文件夹已拆散")
                    refresh()
                }
            }
        }
    }

    // ===================== 视频操作 =====================

    private fun updateDanmaku(videoIndex: Int) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]

        val meta = readVideoMeta(video)
        if (meta.aid <= 0 || meta.cid <= 0) {
            MsgUtil.showMsg("缺少视频信息，无法更新弹幕")
            return
        }

        MsgUtil.showMsg("正在更新弹幕...")
        CenterThreadPool.run {
            try {
                val playerData = PlayerData()
                playerData.aid = meta.aid
                playerData.cid = meta.cid
                playerData.qn = meta.qn
                PlayerApi.getVideo(playerData, true)

                val danmakuUrl = playerData.danmakuUrl
                if (danmakuUrl.isNotEmpty()) {
                    // 下载弹幕到本地
                    val videoDir = File(FileUtil.getVideoDownloadPath(), video.title)
                    val danmakuFile = File(videoDir, "danmaku.xml")
                    downloadDanmakuFile(danmakuUrl, danmakuFile)

                    // 更新合集内的每个分页
                    if (video.pageList != null && video.pageList!!.isNotEmpty()) {
                        for (i in 0 until video.pageList!!.size) {
                            val pageName = video.pageList!![i]
                            val pageDir = File(videoDir, pageName)
                            val pageDanmakuFile = File(pageDir, "danmaku.xml")

                            val pagePlayerData = PlayerData()
                            pagePlayerData.aid = meta.aid
                            // 使用cid列表（如果有的话）
                            pagePlayerData.cid = meta.cid
                            pagePlayerData.qn = meta.qn
                            PlayerApi.getVideo(pagePlayerData, true)

                            if (pagePlayerData.danmakuUrl.isNotEmpty()) {
                                downloadDanmakuFile(pagePlayerData.danmakuUrl, pageDanmakuFile)
                            }
                        }
                    }
                    runOnUiThread { MsgUtil.showMsg("弹幕更新成功") }
                } else {
                    runOnUiThread { MsgUtil.showMsg("弹幕地址获取失败") }
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.showMsg("弹幕更新失败: ${e.message}") }
            }
        }
    }

    private fun downloadDanmakuFile(url: String, file: File) {
        try {
            val response = NetWorkUtil.get(url)
            val body = response.body
            if (body != null) {
                val data = DownloadService.decompress(body.bytes())
                file.writeBytes(data)
            }
            response.close()
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * 切换视频清晰度，参照 QualityChooserActivity 的实现，直接启动该 Activity 完成清晰度选择与下载
     */
    private fun switchQuality(videoIndex: Int) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]
        val meta = readVideoMeta(video)

        if (meta.aid <= 0) {
            MsgUtil.showMsg("缺少视频信息，无法切换分辨率")
            return
        }

        val intent = Intent(this, QualityChooserActivity::class.java)
            .putExtra("aid", meta.aid)
        startActivity(intent)
    }

    /**
     * 跳转到视频详情页
     */
    private fun viewVideoDetail(videoIndex: Int) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]
        val meta = readVideoMeta(video)

        if (meta.aid <= 0) {
            MsgUtil.showMsg("缺少视频信息，无法跳转详情页")
            return
        }

        val intent = Intent(this, VideoInfoActivity::class.java)
            .putExtra("aid", meta.aid)
        startActivity(intent)
    }

    /**
     * 虚拟合集播放：将文件夹内所有视频组成为合集，传入播放器实现连续播放
     * @param folderName 文件夹名称
     * @param startVideoIdx 点击的视频在 videoList 中的索引（作为合集的起始播放位置）
     */
    private fun playVirtualCollection(folderName: String, startVideoIdx: Int) {
        // 收集该文件夹内的所有视频
        val folderVideos = videoList.filter { it.folderName == folderName }
        if (folderVideos.isEmpty()) {
            MsgUtil.showMsg("该文件夹内没有视频")
            return
        }

        // 构建合集的分页数据
        val pagenames = ArrayList<String>()
        val videoUrlList = ArrayList<String>()
        val danmakuUrlList = ArrayList<String>()
        var startPageIndex = 0

        for ((i, v) in folderVideos.withIndex()) {
            pagenames.add(v.title)

            // 单文件或DASH双文件
            if (v.videoFileList != null) {
                if (v.videoFileList!!.size == 1) {
                    videoUrlList.add(v.videoFileList!![0])
                } else {
                    // 取第一个mp4文件作为主文件
                    val videoFile = v.videoFileList!!.firstOrNull { it.endsWith(".mp4") }
                        ?: v.videoFileList!![0]
                    videoUrlList.add(videoFile)
                }
            }

            // 弹幕文件
            if (v.danmakuFileList != null && v.danmakuFileList!!.isNotEmpty()) {
                danmakuUrlList.add(v.danmakuFileList!![0])
            } else {
                danmakuUrlList.add("")
            }

            // 记录起始播放位置
            if (v == folderVideos[startVideoIdx]) {
                startPageIndex = i
            }
        }

        // 构建 PlayerData，传入所有分页信息
        val playerData = PlayerData(PlayerData.TYPE_LOCAL)
        playerData.title = "$folderName（虚拟合集）"
        playerData.videoUrl = videoUrlList[0]
        playerData.danmakuUrl = danmakuUrlList[0]
        playerData.pagenames = pagenames
        playerData.cids = ArrayList() // 本地视频不需要cid
        playerData.currentPageIndex = startPageIndex

        try {
            val player = PlayerApi.jumpToPlayer(playerData)
            // 额外传入视频文件和弹幕文件列表，用于本地分页切换
            player.putExtra("videoFileList", videoUrlList)
            player.putExtra("danmakuFileList", danmakuUrlList)
            startActivity(player)
            MsgUtil.showMsg("已进入虚拟合集：$folderName（共${folderVideos.size}个视频）")
        } catch (e: Exception) {
            MsgUtil.err(e)
        }
    }

    private fun showDeleteHint() {
        MsgUtil.showMsg("再长按一次删除")
    }

    private fun deleteVideo(videoIndex: Int, showConfirm: Boolean = true) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]

        if (showConfirm) {
            showConfirmDialog("删除视频", "确定要删除「${video.title}」吗？\n此操作不可恢复。") {
                performDelete(video)
            }
        } else {
            // 二次长按确认，直接删除
            performDelete(video)
        }
    }

    private fun performDelete(video: LocalVideo) {
        CenterThreadPool.run {
            val videoDir = File(FileUtil.getVideoDownloadPath(), video.title)
            FileUtil.deleteFolder(videoDir)

            // 从文件夹中移除
            FolderManager.removeVideoFromFolder(video.title)

            runOnUiThread {
                MsgUtil.showMsg("删除成功")
                refresh()
            }
        }
    }

    private fun showMoveToFolderDialog(videoIndex: Int) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]

        val folders = FolderManager.getAllFolders()
        if (folders.isEmpty()) {
            MsgUtil.showMsg("请先创建文件夹")
            return
        }

        val folderNames = folders.map { "${it.name} (${it.getVideoCount()}个视频)" }.toTypedArray()

        showListDialog("移动到文件夹", folderNames.toList()) { which ->
            CenterThreadPool.run {
                if (FolderManager.addVideoToFolder(video.title, folders[which].name)) {
                    runOnUiThread {
                        MsgUtil.showMsg("已移动到「${folders[which].name}」")
                        refresh()
                    }
                }
            }
        }
    }

    private fun removeVideoFromFolder(videoIndex: Int) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]

        CenterThreadPool.run {
            if (FolderManager.removeVideoFromFolder(video.title)) {
                runOnUiThread {
                    MsgUtil.showMsg("已移出文件夹")
                    refresh()
                }
            }
        }
    }

    // ===================== 辅助方法 =====================

    private fun readVideoMeta(video: LocalVideo): VideoMeta {
        val videoDir = File(FileUtil.getVideoDownloadPath(), video.title)
        return VideoMetaManager.readMeta(videoDir)
    }

    private fun checkEmpty() {
        runOnUiThread {
            if (videoList.isEmpty() && folderList.isEmpty()) {
                emptyTip.visibility = View.VISIBLE
            } else {
                emptyTip.visibility = View.GONE
            }
        }
    }

    fun refresh() {
        if (!started) return
        CenterThreadPool.run {
            runOnUiThread { swipeRefreshLayout.setRefreshing(true) }
            loadData()

            val currentFolder = adapter?.currentFolderName ?: ""
            runOnUiThread {
                adapter?.setData(folderList, videoList, currentFolder)
                swipeRefreshLayout.setRefreshing(false)
                checkEmpty()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (adapter?.currentFolderName?.isNotEmpty() == true) {
            exitFolder()
        } else {
            super.onBackPressed()
        }
    }

    // ===================== 对话框辅助方法 =====================

    private fun showConfirmDialog(title: String, message: String, onConfirm: () -> Unit) {
        pendingConfirmCallback = onConfirm
        val intent = Intent(this, ConfirmDialogActivity::class.java)
            .putExtra("title", title)
            .putExtra("content", message)
        confirmLauncher.launch(intent)
    }

    private fun showListDialog(title: String, items: List<String>, onSelect: (Int) -> Unit) {
        pendingListCallback = onSelect
        val intent = Intent(this, ListDialogActivity::class.java)
            .putExtra("title", title)
            .putStringArrayListExtra("items", ArrayList(items))
        listLauncher.launch(intent)
    }
}