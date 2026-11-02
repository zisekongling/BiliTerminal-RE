package com.RobinNotBad.BiliClient.activity.video.local

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.RobinNotBad.BiliClient.R
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
import com.RobinNotBad.BiliClient.service.download.DownloadPathSpec
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.FolderManager
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.VideoMetaManager
import com.RobinNotBad.BiliClient.util.VirtualCollectionStart
import java.io.File

class LocalListActivity : InstanceActivity() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView

    private val folderList = ArrayList<VideoFolder>()
    private val videoList = ArrayList<LocalVideo>()
    private var adapter: CacheListAdapter? = null

    // 对话框回调存储
    private var pendingListCallback: ((Int) -> Unit)? = null
    private var pendingInputCallback: ((String) -> Unit)? = null

    // Activity 结果启动器
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

    private var started: Boolean = false

    /** 进页面时用的目录是否处于降级状态；onResume 据此判断「用户刚授完权回来，要换回正式目录」。 */
    private var fallbackAtLoad: Boolean = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_main_refresh)

        recyclerView = findViewById(R.id.recyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener { refresh() }

        val pageName = findViewById<TextView>(R.id.pageName)
        pageName.text = UiText.of("缓存")

        findViewById<View>(R.id.top).setOnClickListener {
            if (adapter?.currentFolderName?.isNotEmpty() == true) {
                exitFolder()
            } else {
                menuClick.run()
            }
        }

        // 判据是「真正写入时目录能不能写」，不是「有没有 MANAGE_EXTERNAL_STORAGE」：
        // 默认目录按 API 分档（高版本 Android/media/<包名>/、低版本 getExternalFilesDir），都免权限；
        // 用户把路径改到公共目录后写不进去时自动降级到应用专属目录，不再跳系统设置页
        //（手表 ROM 没有「所有文件访问」设置页，跳过去只会把下载挡死，issue #42）。
        if (FileUtil.resolveVideoDownloadDir(this) == null) {
            MsgUtil.showMsg(UiText.of("存储目录当前不可写，下载会失败"))
        }
        fallbackAtLoad = FileUtil.isVideoFallbackActive()

        CenterThreadPool.run {
            runOnUiThread { swipeRefreshLayout.setRefreshing(true) }
            loadData()
            setupAdapter()
            started = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (!started) return
        // 用户可能刚从系统设置里授权回来：重新判一次可写性，降级状态变了就重扫（根目录换了）。
        FileUtil.resolveVideoDownloadDir(this)
        val fallback = FileUtil.isVideoFallbackActive()
        if (fallback != fallbackAtLoad) {
            fallbackAtLoad = fallback
            refresh()
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
                localVideo.cover = File(video, DownloadPathSpec.FILE_COVER).toString()
                localVideo.pageList = ArrayList()
                localVideo.danmakuFileList = ArrayList()
                localVideo.videoFileList = ArrayList()
                localVideo.sizeList = ArrayList()
                localVideo.qualityList = ArrayList()

                val qualityFile = File(video, DownloadPathSpec.FILE_QUALITY)
                val qualityStr = if (qualityFile.exists()) {
                    try { qualityFile.readText().trim() } catch (e: Exception) { "" }
                } else ""

                val videoFile = File(video, DownloadPathSpec.FILE_VIDEO)
                val audioFile = File(video, DownloadPathSpec.FILE_AUDIO)
                val danmakuFile = File(video, DownloadPathSpec.FILE_DANMAKU)
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
                    val mark = File(video, DownloadPathSpec.FILE_DOWNLOADING)
                    if (mark.exists()) continue

                    localVideo.sizeList.add(mediaFile.length())
                    localVideo.videoFileList.add(mediaFile.toString())
                    localVideo.danmakuFileList.add(danmakuFile.toString())

                    // 如果合并失败，video.mp4和audio.m4a同时存在，把audio.m4a也加入列表供播放器使用
                    if (videoFile.exists() && audioFile.exists() && mediaFile == videoFile) {
                        localVideo.videoFileList.add(audioFile.toString())
                    }

                    if (qualityStr == DownloadPathSpec.TYPE_AUDIO_ONLY || (audioFile.exists() && !videoFile.exists())) {
                        localVideo.qualityList.add(UiText.of("仅音频"))
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
                                val mark = File(page, DownloadPathSpec.FILE_DOWNLOADING)
                                if (mark.exists()) continue

                                val pageVideoFile = File(page, DownloadPathSpec.FILE_VIDEO)
                                val pageAudioFile = File(page, DownloadPathSpec.FILE_AUDIO)
                                val pageDanmakuFile = File(page, DownloadPathSpec.FILE_DANMAKU)
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

                                    val pageQualityFile = File(page, DownloadPathSpec.FILE_QUALITY)
                                    if (pageQualityFile.exists()) {
                                        try {
                                            val pqnStr = pageQualityFile.readText().trim()
                                            if (pqnStr == DownloadPathSpec.TYPE_AUDIO_ONLY) {
                                                localVideo.qualityList.add(UiText.of("仅音频"))
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
        adapter!!.onFolderMenu = { idx -> showFolderMenu(idx) }
        adapter!!.onBackFromFolder = { exitFolder() }

        adapter!!.onVideoMenu = { idx -> showVideoMenu(idx) }
        adapter!!.onVideoPlayInVirtualCollection = { folderName, videoIdx -> playVirtualCollection(folderName, videoIdx) }

        adapter!!.setData(folderList, videoList)

        runOnUiThread {
            recyclerView.layoutManager = getLayoutManager()
            recyclerView.adapter = adapter
            swipeRefreshLayout.setRefreshing(false)
        }
    }

    /**
     * 弹窗标题用的短标题。
     *
     * 表盘上一行只放得下十来个字，视频标题动辄三四十字，直接当标题会把整个弹窗撑到屏幕外
     * （菜单项被挤到看不见）。这里只截前 12 个字，超出的用省略号。
     */
    private fun menuTitle(title: String): String =
        if (title.length <= 12) title else title.take(12) + "…"

    /** 长按文件夹：重命名 / 拆散。 */
    private fun showFolderMenu(folderIndex: Int) {
        if (folderIndex < 0 || folderIndex >= folderList.size) return
        val folder = folderList[folderIndex]
        val items = listOf(UiText.of("重命名"), UiText.of("拆散文件夹"))
        TerminalDialog.menu(this, menuTitle(folder.name), items) { which ->
            when (which) {
                0 -> showRenameDialog(folderIndex)
                1 -> showDisbandConfirmDialog(folderIndex)
            }
        }.show()
    }

    /** 长按视频：原来的右滑设置面板改成一次长按弹出功能菜单，删除也并进菜单。 */
    private fun showVideoMenu(videoIndex: Int) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]
        val isInFolder = adapter?.currentFolderName?.isNotEmpty() == true
        val items = listOf(
            UiText.of("更新弹幕"),
            UiText.of("切换分辨率"),
            if (isInFolder) UiText.of("移出文件夹") else UiText.of("添加到文件夹"),
            UiText.of("查看详情"),
            UiText.of("删除视频")
        )
        TerminalDialog.menu(this, menuTitle(video.title), items, danger = setOf(items.lastIndex)) { which ->
            when (which) {
                0 -> updateDanmaku(videoIndex)
                1 -> switchQuality(videoIndex)
                2 -> if (isInFolder) removeVideoFromFolder(videoIndex) else showMoveToFolderDialog(videoIndex)
                3 -> viewVideoDetail(videoIndex)
                4 -> deleteVideo(videoIndex)
            }
        }.show()
    }

    // ===================== 文件夹操作 =====================

    private fun enterFolder(folderIndex: Int) {
        if (folderIndex < 0 || folderIndex >= folderList.size) return
        val folder = folderList[folderIndex]
        adapter?.setData(folderList, videoList, folder.name)
        val pageName = findViewById<TextView>(R.id.pageName)
        pageName?.text = folder.name
        // 进文件夹后顶栏那个箭头点下去是"退出文件夹"，用返回箭头表达。
        pageName?.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.arrow_back, 0, 0, 0)
    }

    private fun exitFolder() {
        adapter?.setData(folderList, videoList, "")
        val pageName = findViewById<TextView>(R.id.pageName)
        pageName?.text = UiText.of("缓存")
        // 回到顶层：箭头点下去打开菜单，恢复成朝下的箭头。
        pageName?.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.arrow_down, 0, 0, 0)
    }

    private fun showNewFolderDialog() {
        val intent = Intent(this, InputDialogActivity::class.java)
            .putExtra("title", UiText.of("新建文件夹"))
            .putExtra("hint", UiText.of("请输入文件夹名称"))
        pendingInputCallback = { name ->
            if (!FolderManager.isValidFolderName(name)) {
                MsgUtil.showMsg(UiText.of("名称无效（不能为空、超过30字符或包含特殊字符）"))
            } else if (FolderManager.createFolder(name)) {
                MsgUtil.showMsg(UiText.of("文件夹创建成功"))
                refresh()
            } else {
                MsgUtil.showMsg(UiText.of("文件夹名称已存在或创建失败"))
            }
        }
        inputLauncher.launch(intent)
    }

    private fun showRenameDialog(folderIndex: Int) {
        if (folderIndex < 0 || folderIndex >= folderList.size) return
        val folder = folderList[folderIndex]
        val intent = Intent(this, InputDialogActivity::class.java)
            .putExtra("title", UiText.of("重命名文件夹"))
            .putExtra("initial_text", folder.name)
        pendingInputCallback = { newName ->
            if (!FolderManager.isValidFolderName(newName)) {
                MsgUtil.showMsg(UiText.of("名称无效"))
            } else if (FolderManager.renameFolder(folder.name, newName)) {
                MsgUtil.showMsg(UiText.of("重命名成功"))
                refresh()
            } else {
                MsgUtil.showMsg(UiText.of("重命名失败（名称可能已存在）"))
            }
        }
        inputLauncher.launch(intent)
    }

    private fun showDisbandConfirmDialog(folderIndex: Int) {
        if (folderIndex < 0 || folderIndex >= folderList.size) return
        TerminalDialog.confirm(
            this,
            UiText.of("拆散文件夹"),
            "视频会移回未分类区域。",
            UiText.of("拆散")
        ) {
            disbandFolder(folderIndex)
        }.show()
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
                    MsgUtil.showMsg(UiText.of("文件夹已拆散"))
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
            MsgUtil.showMsg(UiText.of("缺少视频信息，无法更新弹幕"))
            return
        }

        MsgUtil.showMsg(UiText.of("正在更新弹幕..."))
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
                    val danmakuFile = File(videoDir, DownloadPathSpec.FILE_DANMAKU)
                    downloadDanmakuFile(danmakuUrl, danmakuFile)

                    // 更新合集内的每个分页
                    if (video.pageList != null && video.pageList!!.isNotEmpty()) {
                        for (i in 0 until video.pageList!!.size) {
                            val pageName = video.pageList!![i]
                            val pageDir = File(videoDir, pageName)
                            val pageDanmakuFile = File(pageDir, DownloadPathSpec.FILE_DANMAKU)

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
                    runOnUiThread { MsgUtil.showMsg(UiText.of("弹幕更新成功")) }
                } else {
                    runOnUiThread { MsgUtil.showMsg(UiText.of("弹幕地址获取失败")) }
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.showMsg("弹幕更新失败: ${e.message}") }
            }
        }
    }

    private fun downloadDanmakuFile(url: String, file: File) {
        NetWorkUtil.get(url).use { response ->
            // 不判状态码时，404/412 的错误页会被当成弹幕正文写进 danmaku.xml，
            // 用户看到的是「更新成功」但播放时依旧没有弹幕 —— 和下载服务里的
            // 审计 M11-f 是同一类坑，改成失败即抛出、由上层提示「弹幕更新失败」。
            if (!response.isSuccessful) throw java.io.IOException("HTTP " + response.code)
            val body = response.body ?: throw java.io.IOException("empty body")
            val data = NetWorkUtil.decompress(body.bytes())
            if (data.isEmpty()) throw java.io.IOException("empty danmaku")
            file.writeBytes(data)
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
            MsgUtil.showMsg(UiText.of("缺少视频信息，无法切换分辨率"))
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
            MsgUtil.showMsg(UiText.of("缺少视频信息，无法跳转详情页"))
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
        // 构建合集的分页数据
        val pagenames = ArrayList<String>()
        val videoUrlList = ArrayList<String>()
        val danmakuUrlList = ArrayList<String>()
        // 与 videoUrlList 一一对应的外部音轨（DASH 双文件的 audio.m4a；单文件为空串）。
        // 原先只取 mp4 主文件，没合并过的 DASH 缓存在文件夹里播放就没有声音（反馈 #51）。
        val audioUrlList = ArrayList<String>()
        // 合集各条在 videoList（全量列表）里的下标，顺序即合集顺序
        val collectionIndices = ArrayList<Int>()
        var folderCount = 0

        // 一次遍历同时决定"合集有哪些条"与"四条列表的第几项"：
        // 没有文件的条目整条不进合集。原先只跳过 videoUrlList、pagenames 照加，
        // 四条列表长度就不一致了，起播下标会指到别的视频上（反馈 #104）。
        for ((i, v) in videoList.withIndex()) {
            if (v.folderName != folderName) continue
            folderCount++

            val files = v.videoFileList
            if (files.isNullOrEmpty()) continue

            collectionIndices.add(i)
            pagenames.add(v.title)

            // 单文件或 DASH 双文件
            if (files.size == 1) {
                videoUrlList.add(files[0])
                audioUrlList.add("")
            } else {
                // 取第一个 mp4 作为主文件，同目录的 m4a 作为外部音轨
                videoUrlList.add(files.firstOrNull { it.endsWith(".mp4") } ?: files[0])
                audioUrlList.add(files.firstOrNull { it.endsWith(".m4a") } ?: "")
            }

            // 弹幕文件
            val danmakuFiles = v.danmakuFileList
            if (!danmakuFiles.isNullOrEmpty()) {
                danmakuUrlList.add(danmakuFiles[0])
            } else {
                danmakuUrlList.add("")
            }
        }

        if (folderCount == 0) {
            MsgUtil.showMsg(getString(R.string.uitext_locallistactivity_004))
            return
        }
        if (collectionIndices.isEmpty()) {
            MsgUtil.showMsg(getString(R.string.uitext_locallistactivity_024))
            return
        }

        // 起播位置：startVideoIdx 是 videoList 的全局索引，必须换算成合集下标。
        // 换算不出来就明说，不再静默从第一条开始（反馈 #104）
        val startPageIndex = VirtualCollectionStart.resolveLocal(startVideoIdx, collectionIndices)
        if (startPageIndex < 0) {
            MsgUtil.showMsg(getString(R.string.uitext_locallistactivity_025))
            return
        }

        // 构建 PlayerData，传入所有分页信息
        val playerData = PlayerData(PlayerData.TYPE_LOCAL)
        playerData.title = getString(R.string.uitext_locallistactivity_026, folderName)
        // 起播地址必须跟 currentPageIndex 走同一个下标（反馈 #51）：原先固定取 [0]，
        // 点 P2 时选集高亮在 P2、实际播的却是 P1
        playerData.videoUrl = videoUrlList[startPageIndex]
        playerData.danmakuUrl = danmakuUrlList[startPageIndex]
        playerData.pagenames = pagenames
        playerData.cids = ArrayList() // 本地视频不需要cid
        playerData.currentPageIndex = startPageIndex

        try {
            val player = PlayerApi.jumpToPlayer(playerData)
            // 额外传入视频 / 弹幕 / 外部音轨列表，用于本地分页切换
            player.putExtra("videoFileList", videoUrlList)
            player.putExtra("danmakuFileList", danmakuUrlList)
            player.putExtra("audioFileList", audioUrlList)
            if (audioUrlList[startPageIndex].isNotEmpty()) {
                player.putExtra("audio_track_url", audioUrlList[startPageIndex])
            }
            startActivity(player)
            MsgUtil.showMsg(getString(R.string.uitext_locallistactivity_023, folderName, collectionIndices.size))
        } catch (e: Exception) {
            MsgUtil.err(e)
        }
    }

    private fun deleteVideo(videoIndex: Int) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]

        TerminalDialog.confirm(
            this,
            UiText.of("删除视频"),
            "此操作不可恢复。",
            UiText.of("删除")
        ) {
            performDelete(video)
        }.show()
    }

    private fun performDelete(video: LocalVideo) {
        CenterThreadPool.run {
            val videoDir = File(FileUtil.getVideoDownloadPath(), video.title)
            FileUtil.deleteFolder(videoDir)

            // 从文件夹中移除
            FolderManager.removeVideoFromFolder(video.title)

            runOnUiThread {
                MsgUtil.showMsg(UiText.of("删除成功"))
                refresh()
            }
        }
    }

    private fun showMoveToFolderDialog(videoIndex: Int) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]

        val folders = FolderManager.getAllFolders()
        if (folders.isEmpty()) {
            MsgUtil.showMsg(UiText.of("请先创建文件夹"))
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
                    MsgUtil.showMsg(UiText.of("已移出文件夹"))
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

    fun refresh() {
        if (!started) return
        CenterThreadPool.run {
            runOnUiThread { swipeRefreshLayout.setRefreshing(true) }
            loadData()

            val currentFolder = adapter?.currentFolderName ?: ""
            runOnUiThread {
                adapter?.setData(folderList, videoList, currentFolder)
                swipeRefreshLayout.setRefreshing(false)
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

    private fun showListDialog(title: String, items: List<String>, onSelect: (Int) -> Unit) {
        pendingListCallback = onSelect
        val intent = Intent(this, ListDialogActivity::class.java)
            .putExtra("title", title)
            .putStringArrayListExtra("items", ArrayList(items))
        listLauncher.launch(intent)
    }
}