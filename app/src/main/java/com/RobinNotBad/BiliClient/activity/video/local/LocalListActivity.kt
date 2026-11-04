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
import com.RobinNotBad.BiliClient.util.CachePathResolver
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.FolderManager
import com.RobinNotBad.BiliClient.util.LocalListSort
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
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

    /**
     * 最近一次重扫时用的缓存根目录。
     *
     * 光看 [fallbackAtLoad] 不够：用户在设置里把「缓存路径」改到另一个可写目录时，降级标记不会变，
     * 但根目录已经换了。这时列表里显示的仍是旧根目录扫出来的条目，而删除会按新根目录拼路径，
     * 就去删一个不存在的路径——删除静默失败、列表再把整批旧缓存"刷没"（反馈 #58 的现场之一）。
     * 所以根目录路径本身也要参与「要不要重扫」的判定。
     */
    private var rootAtLoad: String? = null

    /**
     * 最近一次扫描用的根目录对象。
     *
     * 删除必须按「这个条目是哪个根扫出来的」下手，而不是「当前 getVideoDownloadPath() 是哪个」：
     * 用户刚改过缓存路径 / 降级标记刚翻转、列表还没重扫时，两者会不一致，按当前根删就又回到
     * 「删一个不存在的路径」的老坑（反馈 #58）。
     */
    private var scannedRoot: File? = null

    // ===================== 多选（26.11.02） =====================

    /** 是否处于多选模式。打开后列表点击/长按都变成勾选，底部条换成「已选 N 项 / 操作 / 退出」。 */
    private var selectionMode = false

    /** 勾中的视频，按标题（= 缓存目录名，同一次扫描里唯一）记，刷新后仍能对上。 */
    private val selectedVideoTitles = LinkedHashSet<String>()

    /** 勾中的文件夹，按名称记。 */
    private val selectedFolderNames = LinkedHashSet<String>()

    private lateinit var multiSelectBar: View
    private lateinit var textSelectedCount: TextView
    private lateinit var btnMultiSelect: TextView
    private lateinit var btnMultiOperate: TextView
    private lateinit var btnMultiExit: TextView

    // ===================== 排序与搜索（26.11.02，反馈 #94） =====================

    /** 排序档位与方向：进页面时从 SharedPreferences 读（[loadViewOptions]），改一次存一次。 */
    private var sortKey = LocalListSort.Key.DEFAULT
    private var sortAscending = true

    /** 当前搜索词（已归一化，空串 = 不过滤）。不持久化：下次进页面先看到完整缓存更符合预期。 */
    private var searchKeyword = ""

    private lateinit var btnSort: TextView
    private lateinit var btnSearch: TextView
    private lateinit var btnSearchClear: TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_main_refresh)

        recyclerView = findViewById(R.id.recyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener { refresh() }

        val pageName = findViewById<TextView>(R.id.pageName)
        pageName.text = UiText.of("缓存")

        setupSelectionBar()
        // 排序档位要在第一次 setData 之前读出来，否则进页面会先按默认档画一遍再跳一下
        loadViewOptions()

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
        // 用户可能刚从系统设置里授权回来、或刚把「缓存路径」改到别处：重新判一次可写性，
        // 降级状态或根目录变了就重扫——否则列表里的条目和当前根目录对不上，删除会删错路径。
        FileUtil.resolveVideoDownloadDir(this)
        val fallback = FileUtil.isVideoFallbackActive()
        val rootNow = FileUtil.getVideoDownloadPath().path
        if (fallback != fallbackAtLoad || rootNow != rootAtLoad) {
            fallbackAtLoad = fallback
            rootAtLoad = rootNow
            refresh()
        }
    }

    private fun loadData() {
        // 加载文件夹
        folderList.clear()
        folderList.addAll(FolderManager.getAllFolders())

        // 扫描视频（根目录只取一次：getVideoDownloadPath 会顺带维护 .nomedia，扫描和后续解析必须同一个根）
        val root = FileUtil.getVideoDownloadPath()
        // 记下这次真正扫描的是哪个根，onResume 才能判断「根目录换了要重扫」
        rootAtLoad = root.path
        scannedRoot = root
        videoList.clear()
        scanVideos(root)

        // 加载每个视频的元数据（目录要按实际落盘位置解析，不能假设「根/标题」）
        for (video in videoList) {
            try {
                val videoDir = resolveVideoDir(root, video)
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
                    // 「按更新时间」排序用（反馈 #94）：没有独立的更新时间字段，取媒体文件 mtime；
                    // 0 表示未知，排序时会被当成最旧，不会让新缓存排到后面
                    localVideo.lastModified = mediaFile.lastModified()
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
                                    // 合集取「最近修改的那一页」当更新时间（反馈 #94 的「按更新时间」排序）
                                    if (pageMediaFile.lastModified() > localVideo.lastModified) {
                                        localVideo.lastModified = pageMediaFile.lastModified()
                                    }
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
        adapter!!.onVideoToggle = { idx -> toggleVideoSelection(idx) }
        adapter!!.onFolderToggle = { idx -> toggleFolderSelection(idx) }

        // 先把持久化的排序 / 搜索状态交给适配器：setData 重建列表时就直接按这个档位画，
        // 不会出现「先按默认顺序画一遍、再跳成排序后的顺序」的闪动
        adapter!!.applyViewOptions(sortKey, sortAscending, searchKeyword)
        adapter!!.setData(folderList, videoList)

        runOnUiThread {
            recyclerView.layoutManager = getLayoutManager()
            recyclerView.adapter = adapter
            updateSelectionBar()
            swipeRefreshLayout.setRefreshing(false)
        }
    }

    // ===================== 排序与搜索（26.11.02，反馈 #94） =====================

    /** 读回上次的排序档位；没存过 / 值损坏时回落「默认（扫描顺序）」。 */
    private fun loadViewOptions() {
        sortKey = LocalListSort.keyOf(SharedPreferencesUtil.getString(SettingsKeys.CACHE_LIST_SORT_KEY, ""))
        sortAscending = SharedPreferencesUtil.getBoolean(SettingsKeys.CACHE_LIST_SORT_ASCENDING, true)
    }

    /**
     * 排序菜单：七档一次选完（默认 / 标题↑ / 标题↓ / 时间新→旧 / 时间旧→新 / 大小大→小 / 大小小→大）。
     *
     * 用小屏友好的 TerminalDialog.singleChoice：不再弹第二个「方向」窗，手表上少点一次。
     */
    private fun showSortDialog() {
        val items = listOf(
            getString(R.string.text_cache_sort_default),
            getString(R.string.text_cache_sort_title_asc),
            getString(R.string.text_cache_sort_title_desc),
            getString(R.string.text_cache_sort_time_desc),
            getString(R.string.text_cache_sort_time_asc),
            getString(R.string.text_cache_sort_size_desc),
            getString(R.string.text_cache_sort_size_asc)
        )
        TerminalDialog.singleChoice(
            this,
            getString(R.string.text_cache_sort_title),
            items,
            LocalListSort.optionIndex(sortKey, sortAscending)
        ) { sheet, option ->
            // singleChoice 自己不关闭（给需要多选的场景留的），这里选一次就关掉再改档位
            sheet.dialog.dismiss()
            applySortOption(option)
        }.show()
    }

    /** 记下档位并立即重画；下一次进页面沿用（见 [loadViewOptions]）。 */
    private fun applySortOption(option: Int) {
        val key = LocalListSort.keyAt(option)
        val ascending = LocalListSort.ascendingAt(option)
        if (key == sortKey && ascending == sortAscending) return
        sortKey = key
        sortAscending = ascending
        SharedPreferencesUtil.putString(SettingsKeys.CACHE_LIST_SORT_KEY, LocalListSort.nameOf(key))
        SharedPreferencesUtil.putBoolean(SettingsKeys.CACHE_LIST_SORT_ASCENDING, ascending)
        adapter?.applyViewOptions(sortKey, sortAscending, searchKeyword)
    }

    /** 搜索：复用「新建文件夹」那套输入页；留空 = 清除（清空即恢复全量）。 */
    private fun showSearchDialog() {
        val intent = Intent(this, InputDialogActivity::class.java)
            .putExtra("title", getString(R.string.text_cache_search_title))
            .putExtra("hint", getString(R.string.text_cache_search_hint))
            // 带出当前词，用户想在原词上改一个字时不用重新敲
            .putExtra("initial_text", searchKeyword)
        pendingInputCallback = { text -> applySearchKeyword(text) }
        inputLauncher.launch(intent)
    }

    private fun applySearchKeyword(raw: String) {
        searchKeyword = LocalListSort.normalizeKeyword(raw)
        adapter?.applyViewOptions(sortKey, sortAscending, searchKeyword)
        // 搜出 0 条时列表里只剩一句「没有匹配的缓存」，但底部条必须留着，
        // 否则「清除」入口消失，用户只能退页面再进来才能恢复全量
        updateSelectionBar()
    }

    private fun clearSearch() {
        if (searchKeyword.isEmpty()) return
        applySearchKeyword("")
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
        if (selectionMode) exitSelectionMode()
        // 顶层搜索匹配的是「文件夹名」，把词带进夹内就变成按视频标题过滤，用户看到空列表会以为
        // 文件夹是空的。想搜夹内视频，进来后再点「搜索」。
        if (searchKeyword.isNotEmpty()) applySearchKeyword("")
        val folder = folderList[folderIndex]
        adapter?.setData(folderList, videoList, folder.name)
        val pageName = findViewById<TextView>(R.id.pageName)
        pageName?.text = folder.name
        // 进文件夹后顶栏那个箭头点下去是"退出文件夹"，用返回箭头表达。
        pageName?.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.arrow_back, 0, 0, 0)
        updateSelectionBar()
    }

    private fun exitFolder() {
        if (selectionMode) exitSelectionMode()
        adapter?.setData(folderList, videoList, "")
        val pageName = findViewById<TextView>(R.id.pageName)
        pageName?.text = UiText.of("缓存")
        // 回到顶层：箭头点下去打开菜单，恢复成朝下的箭头。
        pageName?.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.arrow_down, 0, 0, 0)
        updateSelectionBar()
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
                if (updateDanmakuFor(video)) {
                    runOnUiThread { MsgUtil.showMsg(UiText.of("弹幕更新成功")) }
                } else {
                    runOnUiThread { MsgUtil.showMsg(UiText.of("弹幕地址获取失败")) }
                }
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.showMsg("弹幕更新失败: ${e.message}") }
            }
        }
    }

    /**
     * 把一条缓存的弹幕拉到本地（合集连每个分 P 一起更新），成功返回 true。
     *
     * 单条菜单与批量更新共用：自身不切线程、不弹提示，异常原样抛给调用方决定怎么报。
     * 返回 false 只有一种情况——线上取不到弹幕地址（对应旧实现的「弹幕地址获取失败」）。
     */
    private fun updateDanmakuFor(video: LocalVideo): Boolean {
        val meta = readVideoMeta(video)
        if (meta.aid <= 0 || meta.cid <= 0) {
            throw IllegalStateException(UiText.of("缺少视频信息，无法更新弹幕"))
        }

        val playerData = PlayerData()
        playerData.aid = meta.aid
        playerData.cid = meta.cid
        playerData.qn = meta.qn
        PlayerApi.getVideo(playerData, true)

        val danmakuUrl = playerData.danmakuUrl
        if (danmakuUrl.isEmpty()) return false

        // 下载弹幕到本地
        val videoDir = resolveVideoDir(currentScanRoot(), video)
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
        return true
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

    /**
     * 真正把缓存从磁盘上删掉，并把结果**如实**反馈给用户（反馈 #58）。
     *
     * 旧实现的两处硬伤：
     * 1. 路径靠 `File(根目录, 标题)` 硬拼——只要目录名和标题不一致（下载侧清洗过标题、多 P 合集
     *    的子目录、根目录被用户改过），删的就是一个不存在的路径，`deleteFolder` 第一行
     *    `if (!folder.exists()) return;` 直接返回，什么也没发生；
     * 2. `deleteFolder` 是 void、所有 `delete()` 的返回值都被丢弃，于是无论删没删掉都提示
     *    「删除成功」，列表刷新后视频原样还在——这就是用户看到的「视频没消失，文件也没删」。
     *
     * 现在：候选目录来自 [CachePathResolver]（优先用模型里 `videoFileList` 的真实文件路径反推），
     * 逐个删；只要还有候选目录残留就明确提示失败（含目录名），成功才提示成功；两种情况都强制重扫。
     */
    private fun performDelete(video: LocalVideo) {
        CenterThreadPool.run {
            val failed = deleteVideoFiles(video)

            runOnUiThread {
                if (failed == null) {
                    MsgUtil.showMsg(UiText.of("删除成功"))
                } else {
                    // 手表屏幕窄，只报目录名不报完整路径；原因给最常见的两种（Android 11+ 公共目录
                    // 文件不属于本应用 / SD 卡只读 / 文件被占用），让用户自己去文件管理器动手
                    MsgUtil.showMsgLong(getString(R.string.msg_cache_delete_failed, failed.name))
                }
                // 不管成败都重扫：成功要让条目立刻消失，失败要让用户看到它确实还在（而不是骗他删掉了）
                refresh()
            }
        }
    }

    /**
     * 真的把一条缓存从磁盘上删掉，返回第一个没删掉的候选目录（全删掉返回 null）。
     *
     * 单条删除与批量删除共用：自身不切线程、不弹提示。路径一律按「这条条目被扫出来时用的根」算，
     * 只删真实存在的**目录**；只有真的删掉了才摘文件夹记录（失败时视频还在磁盘上，
     * 顺手把它移出文件夹等于又加一次意外）。
     */
    private fun deleteVideoFiles(video: LocalVideo): File? {
        val root = scannedRoot ?: FileUtil.getVideoDownloadPath()
        val existingDirs = videoDirCandidates(root, video).filter { it.isDirectory }
        var remaining: File? = null
        for (dir in existingDirs) {
            // 一个删不掉也继续删其它候选（同一部视频可能同时存在清洗前/后的两份目录）
            if (!FileUtil.deleteFolderRecursively(dir) && remaining == null) remaining = dir
        }

        // 用 removeVideoFromAllFolders 而不是 removeVideoFromFolder——文件夹里存的标题可能是
        // 早期版本写入的原始标题，与磁盘目录名（清洗过）只差几个全角符号，按原文匹配会漏。
        if (remaining == null) FolderManager.removeVideoFromAllFolders(video.title)
        return remaining
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

    // ===================== 多选（26.11.02） =====================
    //
    // 用户诉求：缓存页（含文件夹页）最底部放按钮，支持缓存页菜单里的所有批量功能。
    // 拍板结果：底部条非多选态只有一个「多选」，多选态是「已选 N 项 / 操作 / 退出」；
    // 批量操作只做 删除视频 / 更新弹幕 / 移动到文件夹（文件夹内是移出）/ 拆散文件夹；
    // 文件夹与视频可以分别勾选，但一次只做同类操作（混选直接提示先统一类型）。

    private fun setupSelectionBar() {
        multiSelectBar = findViewById(R.id.multiSelectBar)
        textSelectedCount = findViewById(R.id.textSelectedCount)
        btnMultiSelect = findViewById(R.id.btnMultiSelect)
        btnMultiOperate = findViewById(R.id.btnMultiOperate)
        btnMultiExit = findViewById(R.id.btnMultiExit)
        btnSort = findViewById(R.id.btnSort)
        btnSearch = findViewById(R.id.btnSearch)
        btnSearchClear = findViewById(R.id.btnSearchClear)

        btnMultiSelect.setOnClickListener { enterSelectionMode() }
        btnMultiExit.setOnClickListener { exitSelectionMode() }
        btnMultiOperate.setOnClickListener { showBatchMenu() }
        btnSort.setOnClickListener { showSortDialog() }
        btnSearch.setOnClickListener { showSearchDialog() }
        btnSearchClear.setOnClickListener { clearSearch() }
    }

    /**
     * 按当前数据、多选态、搜索状态刷新底部条。
     *
     * - 多选态但已经没得勾了（比如在文件夹里删完最后一条）：先退出多选。
     * - 没有任何可选项、也没在搜索：整条隐藏——否则「多选」是个点了没反应的死按钮。
     * - 搜索中即使 0 条也要显示：列表里只剩「没有匹配的缓存」，底部「清除」是唯一出口。
     * - 非多选：多选 / 排序 / 搜索 / 清除（只有搜索中才亮）同排；多选态把它们全藏起来，避免误触。
     */
    private fun updateSelectionBar() {
        val canSelect = hasSelectableItems()
        if (selectionMode && !canSelect) {
            exitSelectionMode()
            return
        }
        if (!canSelect && searchKeyword.isEmpty()) {
            multiSelectBar.visibility = View.GONE
            return
        }

        multiSelectBar.visibility = View.VISIBLE
        if (!selectionMode) {
            textSelectedCount.visibility = View.GONE
            btnMultiOperate.visibility = View.GONE
            btnMultiExit.visibility = View.GONE
            btnMultiSelect.visibility = if (canSelect) View.VISIBLE else View.GONE
            btnSort.visibility = if (canSelect) View.VISIBLE else View.GONE
            btnSearch.visibility = if (canSelect) View.VISIBLE else View.GONE
            btnSearchClear.visibility = if (searchKeyword.isNotEmpty()) View.VISIBLE else View.GONE
            return
        }

        val count = selectedVideoTitles.size + selectedFolderNames.size
        textSelectedCount.visibility = View.VISIBLE
        textSelectedCount.text = UiText.fmt("已选 %1\$d 项", count)
        btnMultiSelect.visibility = View.GONE
        btnMultiOperate.visibility = View.VISIBLE
        btnMultiExit.visibility = View.VISIBLE
        btnSort.visibility = View.GONE
        btnSearch.visibility = View.GONE
        btnSearchClear.visibility = View.GONE
        // 一条都没勾时「操作」压暗，点了也只会提示「先选几条吧~」（收藏夹多选的同款处理）
        btnMultiOperate.alpha = if (count == 0) 0.5f else 1f
    }

    private fun hasSelectableItems(): Boolean {
        val inFolder = adapter?.currentFolderName ?: ""
        return if (inFolder.isEmpty()) {
            folderList.isNotEmpty() || videoList.any { it.folderName.isNullOrEmpty() }
        } else {
            videoList.any { it.folderName == inFolder }
        }
    }

    private fun enterSelectionMode() {
        if (selectionMode) return
        selectionMode = true
        selectedVideoTitles.clear()
        selectedFolderNames.clear()
        adapter?.let { ad ->
            ad.selectionMode = true
            // 直接把 Activity 的集合交给适配器，勾选时两边看到的是同一份数据，不用来回同步
            ad.selectedVideoTitles = selectedVideoTitles
            ad.selectedFolderNames = selectedFolderNames
            ad.notifyDataSetChanged()
        }
        updateSelectionBar()
        MsgUtil.showMsg(UiText.of("点条目勾选，再点「操作」"))
    }

    private fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        selectedVideoTitles.clear()
        selectedFolderNames.clear()
        adapter?.let { ad ->
            ad.selectionMode = false
            ad.notifyDataSetChanged()
        }
        updateSelectionBar()
    }

    private fun toggleVideoSelection(videoIndex: Int) {
        if (!selectionMode) return
        val video = videoList.getOrNull(videoIndex) ?: return
        if (!selectedVideoTitles.remove(video.title)) selectedVideoTitles.add(video.title)
        adapter?.notifyVideoSelectionChanged(videoIndex)
        updateSelectionBar()
    }

    private fun toggleFolderSelection(folderIndex: Int) {
        if (!selectionMode) return
        val folder = folderList.getOrNull(folderIndex) ?: return
        if (!selectedFolderNames.remove(folder.name)) selectedFolderNames.add(folder.name)
        adapter?.notifyFolderSelectionChanged(folderIndex)
        updateSelectionBar()
    }

    /** 重扫后把已经不在列表里的勾选项摘掉（删掉 / 移走 / 改名之后集合不能留着旧名字）。 */
    private fun pruneSelection() {
        if (!selectionMode) return
        selectedVideoTitles.retainAll(videoList.map { it.title }.toSet())
        selectedFolderNames.retainAll(folderList.map { it.name }.toSet())
    }

    private fun selectedVideos(): List<LocalVideo> =
        videoList.filter { selectedVideoTitles.contains(it.title) }

    /** 「操作」菜单：按勾中的是文件夹还是视频给不同入口；混选直接拦下来。 */
    private fun showBatchMenu() {
        val count = selectedVideoTitles.size + selectedFolderNames.size
        if (count == 0) {
            MsgUtil.showMsg(UiText.of("先选几条吧~"))
            return
        }
        if (selectedVideoTitles.isNotEmpty() && selectedFolderNames.isNotEmpty()) {
            MsgUtil.showMsg(UiText.of("文件夹和视频不能一起操作，先取消一种"))
            return
        }

        val inFolder = adapter?.currentFolderName?.isNotEmpty() == true
        val items = if (selectedFolderNames.isNotEmpty()) {
            listOf(UiText.of("拆散文件夹"))
        } else {
            listOf(
                UiText.of("删除视频"),
                UiText.of("更新弹幕"),
                if (inFolder) UiText.of("移出文件夹") else UiText.of("添加到文件夹")
            )
        }
        val danger = if (selectedFolderNames.isEmpty()) setOf(0) else emptySet<Int>()

        TerminalDialog.menu(
            this,
            UiText.fmt("已选 %1\$d 项", count),
            items,
            danger = danger
        ) { which ->
            when {
                selectedFolderNames.isNotEmpty() -> confirmBatchDisband()
                which == 0 -> confirmBatchDelete()
                which == 1 -> performBatchUpdateDanmaku()
                inFolder -> performBatchRemoveFromFolder()
                else -> showBatchMoveDialog()
            }
        }.show()
    }

    // ---------- 批量删除 ----------

    private fun confirmBatchDelete() {
        val count = selectedVideoTitles.size
        if (count == 0) return
        TerminalDialog.confirm(
            this,
            UiText.of("删除视频"),
            UiText.fmt("确定删除选中的 %1\$d 个视频吗？此操作不可恢复。", count),
            UiText.of("删除")
        ) {
            performBatchDelete()
        }.show()
    }

    private fun performBatchDelete() {
        val videos = selectedVideos()
        if (videos.isEmpty()) return
        CenterThreadPool.run {
            var ok = 0
            var failed = 0
            for (video in videos) {
                if (deleteVideoFiles(video) == null) ok++ else failed++
            }
            runOnUiThread {
                exitSelectionMode()
                refresh()
                if (failed == 0) {
                    MsgUtil.showMsg(UiText.fmt("已删除 %1\$d 条", ok))
                } else {
                    MsgUtil.showMsgLong(UiText.fmt("已删除 %1\$d 条，%2\$d 条失败", ok, failed))
                }
            }
        }
    }

    // ---------- 批量更新弹幕 ----------

    private fun performBatchUpdateDanmaku() {
        val videos = selectedVideos()
        if (videos.isEmpty()) return
        MsgUtil.showMsg(UiText.of("正在更新弹幕..."))
        CenterThreadPool.run {
            var ok = 0
            var failed = 0
            for (video in videos) {
                try {
                    if (updateDanmakuFor(video)) ok++ else failed++
                } catch (_: Exception) {
                    failed++
                }
            }
            runOnUiThread {
                exitSelectionMode()
                refresh()
                if (failed == 0) {
                    MsgUtil.showMsg(UiText.of("弹幕更新成功"))
                } else {
                    MsgUtil.showMsgLong(UiText.fmt("弹幕更新完成：成功 %1\$d 个，失败 %2\$d 个", ok, failed))
                }
            }
        }
    }

    // ---------- 批量移动到文件夹 / 移出文件夹 ----------

    private fun showBatchMoveDialog() {
        val titles = selectedVideoTitles.toList()
        if (titles.isEmpty()) return

        val folders = FolderManager.getAllFolders()
        if (folders.isEmpty()) {
            MsgUtil.showMsg(UiText.of("请先创建文件夹"))
            return
        }

        val folderNames = folders.map { "${it.name} (${it.getVideoCount()}个视频)" }
        showListDialog("移动到文件夹", folderNames) { which ->
            val target = folders.getOrNull(which) ?: return@showListDialog
            CenterThreadPool.run {
                for (title in titles) FolderManager.addVideoToFolder(title, target.name)
                runOnUiThread {
                    exitSelectionMode()
                    refresh()
                    MsgUtil.showMsg(UiText.fmt("已移动到「%1\$s」", target.name))
                }
            }
        }
    }

    private fun performBatchRemoveFromFolder() {
        val titles = selectedVideoTitles.toList()
        if (titles.isEmpty()) return
        CenterThreadPool.run {
            for (title in titles) FolderManager.removeVideoFromFolder(title)
            runOnUiThread {
                exitSelectionMode()
                refresh()
                MsgUtil.showMsg(UiText.of("已移出文件夹"))
            }
        }
    }

    // ---------- 批量拆散文件夹 ----------

    private fun confirmBatchDisband() {
        val count = selectedFolderNames.size
        if (count == 0) return
        TerminalDialog.confirm(
            this,
            UiText.of("拆散文件夹"),
            UiText.fmt("确定拆散选中的 %1\$d 个文件夹吗？视频会移回未分类区域。", count),
            UiText.of("拆散")
        ) {
            performBatchDisband()
        }.show()
    }

    private fun performBatchDisband() {
        val names = selectedFolderNames.toList()
        if (names.isEmpty()) return
        CenterThreadPool.run {
            for (name in names) FolderManager.deleteFolder(name)
            runOnUiThread {
                exitSelectionMode()
                // 文件夹行只在顶层出现，拆散期间不可能正停在某个被拆的夹里，这里不用 exitFolder
                refresh()
                MsgUtil.showMsg(UiText.of("文件夹已拆散"))
            }
        }
    }

    // ===================== 辅助方法 =====================

    /**
     * 这个缓存条目所有可能的落盘目录（按可信度排序，见 [CachePathResolver]）。
     *
     * 优先用 `videoFileList[0]` 这个**真实绝对路径**反推：多 P 合集的文件在
     * `<根>/<标题>/<分P>/video.mp4`，反推出来是合集根 `<根>/<标题>`，正好是列表里那一条。
     */
    private fun videoDirCandidates(root: File, video: LocalVideo): List<File> =
        CachePathResolver
            .videoDirCandidates(root.path, video.title, video.videoFileList?.firstOrNull())
            .map { File(it) }

    /** 列表里这些条目是哪个根扫出来的：元数据读写/删除都该按它走，而不是「此刻的根」。 */
    private fun currentScanRoot(): File = scannedRoot ?: FileUtil.getVideoDownloadPath()

    /**
     * 解析这个缓存条目的真实目录：优先返回真实存在的那个候选目录。
     *
     * 都不存在时（目录刚被删掉、或旧数据里根本没有这个目录）退回下载侧的拼法 `根/清洗(标题)`，
     * 让 `VideoMetaManager.readMeta` / `updateFolderName` 在不存在时走它们自己的空实现。
     */
    private fun resolveVideoDir(root: File, video: LocalVideo): File {
        return videoDirCandidates(root, video).firstOrNull { it.isDirectory }
            ?: File(root, FileUtil.stringToFile(video.title))
    }

    private fun readVideoMeta(video: LocalVideo): VideoMeta {
        return VideoMetaManager.readMeta(resolveVideoDir(currentScanRoot(), video))
    }

    fun refresh() {
        if (!started) return
        CenterThreadPool.run {
            runOnUiThread { swipeRefreshLayout.setRefreshing(true) }
            loadData()

            val currentFolder = adapter?.currentFolderName ?: ""
            runOnUiThread {
                adapter?.setData(folderList, videoList, currentFolder)
                // 重扫后可能有条目已经不在了（删掉 / 被移走）：勾选集合跟着收缩，底部计数才不会虚高
                pruneSelection()
                updateSelectionBar()
                swipeRefreshLayout.setRefreshing(false)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 多选态下按返回只退出多选，不离开缓存页——和收藏夹多选的行为一致
        if (selectionMode) {
            exitSelectionMode()
            return
        }
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