package com.RobinNotBad.BiliClient.adapter.video

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalPageChooseActivity
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.LocalVideo
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.VideoFolder
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.RobinNotBad.BiliClient.util.VirtualCollectionPrefs
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import java.util.Locale

/**
 * 统一缓存列表适配器，管理以下项目类型：
 * - 下载列表入口
 * - 文件夹区域标题
 * - 文件夹项（点击进入、长按弹功能菜单）
 * - 新建文件夹按钮
 * - 未分类视频区域标题
 * - 视频项（点击播放、长按弹功能菜单）
 * - 空态提示行（某个分区一个视频都没有时占位，替代盖住整页的居中空态）
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
        private const val TYPE_EMPTY_TIP = 6
    }

    // 数据
    private var folderList: ArrayList<VideoFolder> = ArrayList()
    private var videoList: ArrayList<LocalVideo> = ArrayList()

    var currentFolderName: String = ""

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
    var onBackFromFolder: (() -> Unit)? = null
    var onVideoPlayInVirtualCollection: ((String, Int) -> Unit)? = null

    /** 长按文件夹：由 Activity 弹功能菜单（重命名 / 拆散）。 */
    var onFolderMenu: ((Int) -> Unit)? = null

    /** 长按视频：由 Activity 弹功能菜单（更新弹幕 / 切换分辨率 / 移动文件夹 / 详情 / 删除）。 */
    var onVideoMenu: ((Int) -> Unit)? = null

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
            var uncategorizedCount = 0
            for (i in videoList.indices) {
                if (videoList[i].folderName.isNullOrEmpty()) {
                    flatItems.add(FlatItem(TYPE_VIDEO_ITEM, videoIndex = i))
                    uncategorizedCount++
                }
            }
            // 空态放在「未分类视频」标题下面当列表项，而不是盖在整页中间的浮层：
            // 浮层会和「新建文件夹」按钮重合（26.10.20 用户反馈），而且它不参与滚动。
            if (uncategorizedCount == 0) {
                flatItems.add(FlatItem(TYPE_EMPTY_TIP))
            }
        } else {
            flatItems.add(FlatItem(TYPE_SECTION_VIDEO_HEADER))
            var folderVideoCount = 0
            for (i in videoList.indices) {
                if (videoList[i].folderName == currentFolderName) {
                    flatItems.add(FlatItem(TYPE_VIDEO_ITEM, videoIndex = i))
                    folderVideoCount++
                }
            }
            if (folderVideoCount == 0) {
                flatItems.add(FlatItem(TYPE_EMPTY_TIP))
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < flatItems.size) flatItems[position].type else TYPE_EMPTY_TIP
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
                val view = LayoutInflater.from(context).inflate(R.layout.cell_empty_tip, parent, false)
                EmptyTipHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position >= flatItems.size) return
        val item = flatItems[position]

        when (item.type) {
            TYPE_GOTO_DOWNLOAD -> {
                (holder as GotoHolder).show(context)
            }
            TYPE_SECTION_FOLDER_HEADER -> {
                (holder as SectionHeaderHolder).apply {
                    sectionTitle.text = UiText.of("文件夹")
                    sectionCount.text = "${folderList.size}个"
                }
            }
            TYPE_SECTION_VIDEO_HEADER -> {
                (holder as SectionHeaderHolder).apply {
                    if (currentFolderName.isEmpty()) {
                        sectionTitle.text = UiText.of("未分类视频")
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
                bindFolderItem(holder as FolderItemHolder, item.folderIndex)
            }
            TYPE_ACTION_NEW_FOLDER -> {
                (holder as ActionButtonHolder).apply {
                    text.text = UiText.of("新建文件夹")
                    text.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    text.compoundDrawablePadding = 0
                    itemView.setOnClickListener { onNewFolderClick?.invoke() }
                }
            }
            TYPE_VIDEO_ITEM -> {
                bindVideoItem(holder as VideoItemHolder, item.videoIndex)
            }
        }
    }

    private fun bindFolderItem(holder: FolderItemHolder, folderIndex: Int) {
        if (folderIndex < 0 || folderIndex >= folderList.size) return
        val folder = folderList[folderIndex]

        holder.folderName.text = folder.name
        holder.folderCount.text = "${folder.getVideoCount()}个视频"

        holder.itemView.setOnClickListener { onFolderClick?.invoke(folderIndex) }
        holder.itemView.setOnLongClickListener {
            onFolderMenu?.invoke(folderIndex)
            true
        }
    }

    private fun bindVideoItem(holder: VideoItemHolder, videoIndex: Int) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]

        holder.showVideo(video)

        holder.itemView.setOnClickListener { playVideo(video) }
        holder.itemView.setOnLongClickListener {
            onVideoMenu?.invoke(videoIndex)
            true
        }
    }

    private fun playVideo(video: LocalVideo) {
        // 缓存页「点了就播」：功能一（虚拟合集）只决定播单条还是整夹连播。
        // 功能二（收藏夹视频点击即播）只管收藏夹——本地文件没有「视频详情页」这个主流程，
        // 点缓存视频却弹出详情页/什么都不做都很怪，所以这里不看功能二。
        val clickAction = VirtualCollectionPrefs.cacheClickAction(
            inFolder = currentFolderName.isNotEmpty(),
            collectionEnabled = VirtualCollectionPrefs.collectionEnabled()
        )
        if (clickAction == VirtualCollectionPrefs.ClickAction.PLAY_COLLECTION) {
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
                MsgUtil.showMsg(UiText.of("跳转失败"))
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
                MsgUtil.showMsg(UiText.of("跳转失败"))
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
            textView.text = UiText.of("下载列表")
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

    /** 空态提示行。文案写在布局里（@string/text_list_empty），不用绑定。 */
    class EmptyTipHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class FolderItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderContent: View = itemView.findViewById(R.id.folderContent)
        val folderName: TextView = itemView.findViewById(R.id.folderName)
        val folderCount: TextView = itemView.findViewById(R.id.folderCount)
    }

    class VideoItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.text_title)
        val extra: TextView = itemView.findViewById(R.id.text_extra)
        val cover: ImageView = itemView.findViewById(R.id.img_cover)

        @SuppressLint("SetTextI18n")
        fun showVideo(video: LocalVideo) {
            title.text = video.title
            val size = String.format(Locale.CHINA, "%.1f", video.size / 1000000f) + "MB"
            val qualityTag = if (video.qualityList != null && video.qualityList!!.isNotEmpty())
                " [" + video.qualityList!![0] + "]" else ""
            extra.text = size + qualityTag
            try {
                GlideUtil.with(cover).asDrawable().load(video.cover)
                    .transition(GlideUtil.getTransitionOptions())
                    .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(cover)
            } catch (_: Exception) {}
        }
    }
}