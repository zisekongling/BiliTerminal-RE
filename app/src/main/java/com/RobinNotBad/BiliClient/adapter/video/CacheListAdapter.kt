package com.RobinNotBad.BiliClient.adapter.video

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalPageChooseActivity
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.LocalVideo
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.VideoFolder
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import java.util.Locale

/**
 * 统一缓存列表适配器，管理以下项目类型：
 * - 下载列表入口
 * - 文件夹区域标题
 * - 文件夹项（带滑动设置）
 * - 新建文件夹按钮
 * - 未分类视频区域标题
 * - 视频项（带滑动设置面板）
 * - 多选按钮（底部）
 */
class CacheListAdapter(
    private val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_GOTO_DOWNLOAD = 0
        private const val TYPE_SECTION_FOLDER_HEADER = 1
        private const val TYPE_FOLDER_ITEM = 2
        private const val TYPE_ACTION_NEW_FOLDER = 3
        private const val TYPE_SECTION_VIDEO_HEADER = 4
        private const val TYPE_VIDEO_ITEM = 5
    }

    // 数据
    private var folderList: ArrayList<VideoFolder> = ArrayList()
    private var videoList: ArrayList<LocalVideo> = ArrayList()

    var currentFolderName: String = ""

    private var openVideoSettingsIdx: Int = -1
    private var openFolderSettingsIdx: Int = -1
    private var waitingSecondLongPressIdx: Int = -1
    private var justDeletedIdx: Int = -1

    /**
     * 获取所有items的扁平化列表类型
     */
    private data class FlatItem(
        val type: Int,
        val folderIndex: Int = -1,    // 只在TYPE_FOLDER_ITEM时有效
        val videoIndex: Int = -1       // 只在TYPE_VIDEO_ITEM时有效
    )

    private val flatItems = mutableListOf<FlatItem>()

    var onFolderClick: ((Int) -> Unit)? = null
    var onNewFolderClick: (() -> Unit)? = null
    var onFolderRename: ((Int) -> Unit)? = null
    var onFolderDisband: ((Int) -> Unit)? = null
    var onVideoUpdateDanmaku: ((Int) -> Unit)? = null
    var onVideoSwitchQuality: ((Int) -> Unit)? = null
    var onVideoDelete: ((Int) -> Unit)? = null
    var onVideoMoveToFolder: ((Int) -> Unit)? = null
    var onVideoRemoveFromFolder: ((Int) -> Unit)? = null
    var onVideoLongClick: ((Int) -> Unit)? = null
    var onVideoViewDetail: ((Int) -> Unit)? = null
    var onBackFromFolder: (() -> Unit)? = null
    var onVideoPlayInVirtualCollection: ((String, Int) -> Unit)? = null
    var isRecyclerViewScrolling: (() -> Boolean)? = null

    fun setData(folders: ArrayList<VideoFolder>, videos: ArrayList<LocalVideo>, currentFolder: String = "") {
        folderList = folders
        videoList = videos
        currentFolderName = currentFolder
        rebuildFlatList()
        notifyDataSetChanged()
    }

    private fun rebuildFlatList() {
        flatItems.clear()

        if (currentFolderName.isEmpty()) {
            flatItems.add(FlatItem(TYPE_GOTO_DOWNLOAD))

            if (folderList.isNotEmpty()) {
                flatItems.add(FlatItem(TYPE_SECTION_FOLDER_HEADER))
                for (i in folderList.indices) {
                    flatItems.add(FlatItem(TYPE_FOLDER_ITEM, folderIndex = i))
                }
            }
            flatItems.add(FlatItem(TYPE_ACTION_NEW_FOLDER))

            flatItems.add(FlatItem(TYPE_SECTION_VIDEO_HEADER))
            for (i in videoList.indices) {
                if (videoList[i].folderName.isNullOrEmpty()) {
                    flatItems.add(FlatItem(TYPE_VIDEO_ITEM, videoIndex = i))
                }
            }
        } else {
            flatItems.add(FlatItem(TYPE_SECTION_VIDEO_HEADER))
            for (i in videoList.indices) {
                if (videoList[i].folderName == currentFolderName) {
                    flatItems.add(FlatItem(TYPE_VIDEO_ITEM, videoIndex = i))
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < flatItems.size) flatItems[position].type else TYPE_GOTO_DOWNLOAD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_GOTO_DOWNLOAD -> {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_goto, parent, false)
                GotoHolder(view)
            }
            TYPE_SECTION_FOLDER_HEADER, TYPE_SECTION_VIDEO_HEADER -> {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_section_header, parent, false)
                SectionHeaderHolder(view)
            }
            TYPE_FOLDER_ITEM -> {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_video_folder, parent, false)
                FolderItemHolder(view)
            }
            TYPE_ACTION_NEW_FOLDER -> {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_action_button, parent, false)
                ActionButtonHolder(view)
            }
            TYPE_VIDEO_ITEM -> {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_video_local, parent, false)
                VideoItemHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_video_local, parent, false)
                VideoItemHolder(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position >= flatItems.size) return
        val item = flatItems[position]

        when (item.type) {
            TYPE_GOTO_DOWNLOAD -> {
                (holder as GotoHolder).show(context)
            }
            TYPE_SECTION_FOLDER_HEADER -> {
                (holder as SectionHeaderHolder).apply {
                    sectionTitle.text = "文件夹"
                    sectionCount.text = "${folderList.size}个"
                }
            }
            TYPE_SECTION_VIDEO_HEADER -> {
                (holder as SectionHeaderHolder).apply {
                    if (currentFolderName.isEmpty()) {
                        sectionTitle.text = "未分类视频"
                        sectionCount.text = "${videoList.count { it.folderName.isNullOrEmpty() }}个"
                    } else {
                        sectionTitle.text = currentFolderName
                        sectionCount.text = "${videoList.count { it.folderName == currentFolderName }}个"
                        // 点击标题返回
                        itemView.setOnClickListener { onBackFromFolder?.invoke() }
                    }
                }
            }
            TYPE_FOLDER_ITEM -> {
                bindFolderItem(holder as FolderItemHolder, position, item.folderIndex)
            }
            TYPE_ACTION_NEW_FOLDER -> {
                (holder as ActionButtonHolder).apply {
                    text.text = "新建文件夹"
                    text.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    text.compoundDrawablePadding = 0
                    itemView.setOnClickListener { onNewFolderClick?.invoke() }
                }
            }
            TYPE_VIDEO_ITEM -> {
                bindVideoItem(holder as VideoItemHolder, position, item.videoIndex)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindFolderItem(holder: FolderItemHolder, adapterPos: Int, folderIndex: Int) {
        if (folderIndex < 0 || folderIndex >= folderList.size) return
        val folder = folderList[folderIndex]

        holder.folderName.text = folder.name
        holder.folderCount.text = "${folder.getVideoCount()}个视频"

        val isSettingsOpen = folderIndex == openFolderSettingsIdx
        holder.folderContent.visibility = if (isSettingsOpen) View.INVISIBLE else View.VISIBLE
        holder.folderSettingsPanel.visibility = if (isSettingsOpen) View.VISIBLE else View.GONE

        var startX = 0f
        var startY = 0f
        var isSwiping = false
        var hasMoved = false
        val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()

        holder.itemView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x; startY = event.y
                    isSwiping = false; hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = startX - event.x
                    val deltaY = Math.abs(startY - event.y)

                    // 任意方向移动超过touchSlop即标记为"已移动"，防止触发点击
                    if (!hasMoved && (Math.abs(deltaX) > touchSlop || deltaY > touchSlop)) {
                        hasMoved = true
                    }

                    // 水平滑动打开设置面板
                    if (deltaX > 30f && deltaY < 50f && !isSwiping) isSwiping = true
                    if (isSwiping) {
                        val newIdx = if (deltaX > 80f) folderIndex else -1
                        if (newIdx != openFolderSettingsIdx) {
                            val old = openFolderSettingsIdx
                            openFolderSettingsIdx = newIdx
                            notifyItemChanged(adapterPos)
                            if (old >= 0) {
                                val oldPos = flatItems.indexOfFirst { it.type == TYPE_FOLDER_ITEM && it.folderIndex == old }
                                if (oldPos >= 0) notifyItemChanged(oldPos)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // RecyclerView拦截了触摸（正在滚动中），不触发点击
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 检查RecyclerView是否正在滚动，避免滑动时误触文件夹
                    val isScrolling = isRecyclerViewScrolling?.invoke() == true

                    if (!isSwiping && !hasMoved && !isScrolling) {
                        if (isSettingsOpen) {
                            openFolderSettingsIdx = -1
                            notifyItemChanged(adapterPos)
                        } else {
                            onFolderClick?.invoke(folderIndex)
                        }
                    }
                    view.performClick()
                    true
                }
                else -> false
            }
        }

        holder.btnRename.setOnClickListener {
            openFolderSettingsIdx = -1
            notifyItemChanged(adapterPos)
            onFolderRename?.invoke(folderIndex)
        }
        holder.btnDisband.setOnClickListener {
            openFolderSettingsIdx = -1
            notifyItemChanged(adapterPos)
            onFolderDisband?.invoke(folderIndex)
        }
        holder.btnCancel.setOnClickListener {
            openFolderSettingsIdx = -1
            notifyItemChanged(adapterPos)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindVideoItem(holder: VideoItemHolder, adapterPos: Int, videoIndex: Int) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]

        holder.showVideo(video)

        val isSettingsOpen = videoIndex == openVideoSettingsIdx
        holder.settingsPanel.visibility = if (isSettingsOpen) View.VISIBLE else View.GONE

        val isInFolder = currentFolderName.isNotEmpty()
        holder.btnMoveVideo.text = if (isInFolder) "移出文件夹" else "添加到文件夹"

        var startX = 0f
        var startY = 0f
        var isSwiping = false
        var swipeThreshold = 80f
        var longPressRunnable: Runnable? = null
        var longPressFiredThisTouch = false

        holder.itemView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    isSwiping = false
                    longPressFiredThisTouch = false
                    justDeletedIdx = -1

                    longPressRunnable = Runnable {
                        if (!isSwiping) {
                            longPressFiredThisTouch = true
                            if (waitingSecondLongPressIdx == videoIndex) {
                                onVideoDelete?.invoke(videoIndex)
                                waitingSecondLongPressIdx = -1
                                justDeletedIdx = videoIndex
                            } else {
                                waitingSecondLongPressIdx = videoIndex
                                onVideoLongClick?.invoke(videoIndex)
                            }
                        }
                    }
                    view.postDelayed(longPressRunnable, 200)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = startX - event.x
                    val deltaY = Math.abs(startY - event.y)

                    // 检测是否开始滑动
                    if (deltaX > 20f && deltaY < 50f && !isSwiping) {
                        isSwiping = true
                        // 滑动开始时取消长按任务
                        longPressRunnable?.let { view.removeCallbacks(it) }
                        longPressRunnable = null
                        // 滑动时重置二次删除状态
                        waitingSecondLongPressIdx = -1
                    }

                    if (isSwiping) {
                        val newIdx = if (deltaX > swipeThreshold) videoIndex else -1
                        if (newIdx != openVideoSettingsIdx) {
                            val old = openVideoSettingsIdx
                            openVideoSettingsIdx = newIdx
                            notifyItemChanged(adapterPos)
                            if (old >= 0) {
                                val oldPos = flatItems.indexOfFirst { it.type == TYPE_VIDEO_ITEM && it.videoIndex == old }
                                if (oldPos >= 0) notifyItemChanged(oldPos)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 取消长按任务
                    longPressRunnable?.let { view.removeCallbacks(it) }
                    longPressRunnable = null

                    // 如果RecyclerView正在滚动，不触发播放
                    val isScrolling = isRecyclerViewScrolling?.invoke() == true

                    if (!isSwiping && !isScrolling) {
                        if (isSettingsOpen) {
                            openVideoSettingsIdx = -1
                            notifyItemChanged(adapterPos)
                        } else if (!longPressFiredThisTouch && waitingSecondLongPressIdx == videoIndex) {
                            // 用户短按取消二次删除等待状态
                            waitingSecondLongPressIdx = -1
                        } else if (justDeletedIdx == videoIndex) {
                            // 刚触发删除，不播放视频
                        } else if (waitingSecondLongPressIdx != videoIndex) {
                            // 正常点击，播放视频
                            playVideo(video)
                        }
                        // 如果处于等待状态且是长按触发的，不做任何操作
                    } else {
                        if (isSettingsOpen && Math.abs(startX - event.x) < swipeThreshold / 2) {
                            openVideoSettingsIdx = -1
                            notifyItemChanged(adapterPos)
                        }
                    }
                    view.performClick()
                    true
                }
                else -> false
            }
        }

        // 设置面板按钮
        holder.btnUpdateDanmaku.setOnClickListener {
            openVideoSettingsIdx = -1; notifyItemChanged(adapterPos)
            onVideoUpdateDanmaku?.invoke(videoIndex)
        }
        holder.layoutQuality.setOnClickListener {
            openVideoSettingsIdx = -1; notifyItemChanged(adapterPos)
            onVideoSwitchQuality?.invoke(videoIndex)
        }
        holder.btnMoveVideo.setOnClickListener {
            openVideoSettingsIdx = -1; notifyItemChanged(adapterPos)
            if (isInFolder) onVideoRemoveFromFolder?.invoke(videoIndex)
            else onVideoMoveToFolder?.invoke(videoIndex)
        }
        holder.btnViewDetail.setOnClickListener {
            openVideoSettingsIdx = -1; notifyItemChanged(adapterPos)
            onVideoViewDetail?.invoke(videoIndex)
        }
        holder.btnCancelSettings.setOnClickListener {
            openVideoSettingsIdx = -1; notifyItemChanged(adapterPos)
        }
    }

    private fun playVideo(video: LocalVideo) {
        // 虚拟合集模式：在文件夹内播放时，将文件夹内所有视频组成为合集
        if (currentFolderName.isNotEmpty() && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.VIRTUAL_COLLECTION_ENABLE, true)) {
            onVideoPlayInVirtualCollection?.invoke(currentFolderName, videoList.indexOf(video))
            return
        }

        if (video.videoFileList != null && video.videoFileList!!.size == 1) {
            val playerData = PlayerData(PlayerData.TYPE_LOCAL)
            playerData.videoUrl = video.videoFileList!![0]
            if (video.danmakuFileList != null && video.danmakuFileList!!.isNotEmpty()) {
                playerData.danmakuUrl = video.danmakuFileList!![0]
            }
            playerData.title = video.title
            try {
                val player = PlayerApi.jumpToPlayer(playerData)
                if (playerData.videoUrl.endsWith("audio.m4a")) {
                    player.putExtra("audio_only", true)
                }
                context.startActivity(player)
            } catch (e: ActivityNotFoundException) {
                MsgUtil.showMsg("跳转失败")
            }
        } else if (video.videoFileList != null && video.videoFileList!!.size == 2) {
            // DASH双文件：video.mp4 + audio.m4a
            val videoUrl = video.videoFileList!!.firstOrNull { it.endsWith(".mp4") } ?: video.videoFileList!![0]
            val audioUrl = video.videoFileList!!.firstOrNull { it.endsWith(".m4a") } ?: ""
            val playerData = PlayerData(PlayerData.TYPE_LOCAL)
            playerData.videoUrl = videoUrl
            if (video.danmakuFileList != null && video.danmakuFileList!!.isNotEmpty()) {
                playerData.danmakuUrl = video.danmakuFileList!![0]
            }
            playerData.title = video.title
            try {
                val player = PlayerApi.jumpToPlayer(playerData)
                if (audioUrl.isNotEmpty()) {
                    player.putExtra("audio_track_url", audioUrl)
                }
                context.startActivity(player)
            } catch (e: ActivityNotFoundException) {
                MsgUtil.showMsg("跳转失败")
            }
        } else if (video.videoFileList != null && video.videoFileList!!.size > 2) {
            val intent = Intent(context, LocalPageChooseActivity::class.java)
            intent.putExtra("pageList", video.pageList)
            intent.putExtra("videoFileList", video.videoFileList)
            intent.putExtra("danmakuFileList", video.danmakuFileList)
            intent.putExtra("title", video.title)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = flatItems.size

    // ========== ViewHolder Classes ==========

    class GotoHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.text)
        fun show(context: Context) {
            textView.text = "下载列表"
            itemView.setOnClickListener {
                context.startActivity(Intent(context, DownloadListActivity::class.java))
            }
        }
    }

    class SectionHeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sectionTitle: TextView = itemView.findViewById(R.id.sectionTitle)
        val sectionCount: TextView = itemView.findViewById(R.id.sectionCount)
    }

    class ActionButtonHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.text)
    }

    class FolderItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderContent: View = itemView.findViewById(R.id.folderContent)
        val folderSettingsPanel: View = itemView.findViewById(R.id.folderSettingsPanel)
        val folderName: TextView = itemView.findViewById(R.id.folderName)
        val folderCount: TextView = itemView.findViewById(R.id.folderCount)
        val btnRename: TextView = itemView.findViewById(R.id.btnFolderRename)
        val btnDisband: TextView = itemView.findViewById(R.id.btnFolderDisband)
        val btnCancel: TextView = itemView.findViewById(R.id.btnFolderCancel)
    }

    class VideoItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.text_title)
        val extra: TextView = itemView.findViewById(R.id.text_extra)
        val cover: ImageView = itemView.findViewById(R.id.img_cover)
        val settingsPanel: View = itemView.findViewById(R.id.videoSettingsPanel)
        val btnUpdateDanmaku: TextView = itemView.findViewById(R.id.btnUpdateDanmaku)
        val layoutQuality: View = itemView.findViewById(R.id.layoutQuality)
        val btnMoveVideo: TextView = itemView.findViewById(R.id.btnMoveVideo)
        val btnCancelSettings: TextView = itemView.findViewById(R.id.btnCancelSettings)
        val btnViewDetail: TextView = itemView.findViewById(R.id.btnViewDetail)

        @SuppressLint("SetTextI18n")
        fun showVideo(video: LocalVideo) {
            title.text = video.title
            val size = String.format(Locale.CHINA, "%.1f", video.size / 1000000f) + "MB"
            val qualityTag = if (video.qualityList != null && video.qualityList!!.isNotEmpty())
                " [" + video.qualityList!![0] + "]" else ""
            extra.text = size + qualityTag
            try {
                Glide.with(BiliTerminal.context).asDrawable().load(video.cover)
                    .transition(GlideUtil.getTransitionOptions())
                    .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(cover)
            } catch (_: Exception) {}
        }
    }
}