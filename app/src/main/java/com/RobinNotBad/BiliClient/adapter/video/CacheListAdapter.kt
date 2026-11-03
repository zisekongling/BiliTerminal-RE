package com.RobinNotBad.BiliClient.adapter.video

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalPageChooseActivity
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.LocalVideo
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.VideoFolder
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.LocalListSort
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
 *
 * 多选：底部那条「多选 / 已选 N 项 / 操作 / 退出」由 Activity 提供（见 activity_simple_main_refresh.xml），
 * 适配器只负责两件事：勾选态怎么画（[applySelection]）、点了哪一项（[onVideoToggle] / [onFolderToggle]）。
 * [selectionMode] 打开后，条目点击与长按都变成勾选，不再播放 / 弹菜单。
 *
 * 排序 / 搜索（26.11.02，反馈 #94）：只决定「画哪些、按什么顺序画」，[folderList] / [videoList]
 * 本身始终是扫描原序，回调里传出去的也一直是原始下标，Activity 的多选 / 删除 / 刷新逻辑不用改。
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

        /** 多选前缀：勾上用 ✓，未勾用全角空格占位（与收藏夹多选同一套视觉，见 VideoCardHolder）。 */
        private const val PREFIX_SELECTED = "✓ "
        private const val PREFIX_UNSELECTED = "　 "
    }

    // 数据
    private var folderList: ArrayList<VideoFolder> = ArrayList()
    private var videoList: ArrayList<LocalVideo> = ArrayList()

    var currentFolderName: String = ""

    /**
     * 排序档位与方向，默认「默认（扫描顺序）」。
     *
     * 为什么不先把列表排好再 setData：Activity 里二十多处逻辑（切分辨率、移动文件夹、删除、
     * 虚拟合集连播）都直接用 videoList / folderList 的下标。一旦把列表换成「过滤 + 排序后的副本」，
     * 那些回调的下标就得再映射一遍，很容易把多选和删除改坏。这里只改「画哪些、按什么顺序画」，
     * 回调仍传原始下标，Activity 侧零改动。
     */
    var sortKey: LocalListSort.Key = LocalListSort.Key.DEFAULT
        private set

    var sortAscending: Boolean = true
        private set

    /** 已归一化的搜索词；空串 = 不过滤。同时匹配视频标题与文件夹名（忽略大小写）。 */
    var searchKeyword: String = ""
        private set

    /** 当前实际画出来的文件夹 / 视频条数。搜索时分区标题上的「N个」要跟着变，不能再直接数原始列表。 */
    private var visibleFolderCount = 0
    private var visibleVideoCount = 0

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

    /** 多选模式：打开后条目点击/长按都变成勾选，勾中项加前缀、未勾项压暗。 */
    var selectionMode: Boolean = false

    /**
     * 勾中的视频，按标题记。
     *
     * 标题就是缓存目录名，同一次扫描里唯一；不用下标是因为刷新（删除 / 移动）后下标会整体挪位，
     * 用标题才能在重扫后仍然对得上，[setData] 里也靠它重新画出勾选态。
     */
    var selectedVideoTitles: Set<String> = emptySet()

    /** 勾中的文件夹，按名称记。 */
    var selectedFolderNames: Set<String> = emptySet()

    /** 多选态点了视频：由 Activity 翻转勾选。 */
    var onVideoToggle: ((Int) -> Unit)? = null

    /** 多选态点了文件夹：由 Activity 翻转勾选。 */
    var onFolderToggle: ((Int) -> Unit)? = null

    fun setData(folders: ArrayList<VideoFolder>, videos: ArrayList<LocalVideo>, currentFolder: String = "") {
        folderList = folders
        videoList = videos
        currentFolderName = currentFolder
        rebuildFlatList()
        notifyDataSetChanged()
    }

    /**
     * 换排序档位或搜索词后重建列表。
     *
     * 必须整体重建而不是逐条 notifyItemXXX：搜索可能把整个文件夹区域藏掉，条目集合和分组数量都会变，
     * 逐条刷得自己算一大堆位移。勾选状态按标题记（[selectedVideoTitles] / [selectedFolderNames]），
     * 重建后由 [applySelection] 重新画出来，重排不会丢勾选。
     */
    fun applyViewOptions(key: LocalListSort.Key, ascending: Boolean, keyword: String) {
        val normalized = LocalListSort.normalizeKeyword(keyword)
        if (sortKey == key && sortAscending == ascending && searchKeyword == normalized) return
        sortKey = key
        sortAscending = ascending
        searchKeyword = normalized
        rebuildFlatList()
        notifyDataSetChanged()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // 勾选态是用 notifyItemChanged + alpha 画的：默认的交叉淡入动画会在动画期间把 alpha
        // 复位成 1f，取消勾选看起来像没生效（收藏夹多选踩过同一个坑，见 VideoCardAdapter）。
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    /**
     * 勾选状态变了，只重绘这一条。
     *
     * 不能整体 `notifyDataSetChanged()`：封面加载用的是 `DiskCacheStrategy.NONE`，整体重绑会让
     * 每勾一下都把整屏封面重新读一遍，手表上肉眼可见地闪。
     */
    fun notifyVideoSelectionChanged(videoIndex: Int) {
        val position = flatItems.indexOfFirst { it.type == TYPE_VIDEO_ITEM && it.videoIndex == videoIndex }
        if (position >= 0) notifyItemChanged(position)
    }

    fun notifyFolderSelectionChanged(folderIndex: Int) {
        val position = flatItems.indexOfFirst { it.type == TYPE_FOLDER_ITEM && it.folderIndex == folderIndex }
        if (position >= 0) notifyItemChanged(position)
    }

    private fun rebuildFlatList() {
        flatItems.clear()

        // 排序结果全是「原始下标」，所以 flatItems 里的 folderIndex / videoIndex 与扫描顺序一一对应，
        // 多选集合、删除、移动这些按下标回查的逻辑完全不用换语义。
        val folderOrder = LocalListSort.folderOrder(folderList, videoList, sortKey, sortAscending)
        val videoOrder = LocalListSort.videoOrder(videoList, sortKey, sortAscending)

        // 先把「这一屏真的要画的」筛出来，再决定分区标题 / 空态画不画。
        // 不能边扫边画：搜索只命中文件夹时，未分类视频是 0 条，边扫边判断会多补一句
        // 「没有匹配的缓存」，可实际上列表里明明有命中的文件夹。
        val visibleFolders = ArrayList<Int>()
        if (currentFolderName.isEmpty()) {
            for (i in folderOrder) {
                if (LocalListSort.matches(folderList[i].name, searchKeyword)) visibleFolders.add(i)
            }
        }
        val visibleVideos = ArrayList<Int>()
        for (i in videoOrder) {
            val folderName = videoList[i].folderName
            if (currentFolderName.isEmpty()) {
                if (!folderName.isNullOrEmpty()) continue       // 文件夹内的视频在文件夹里显示
            } else if (folderName != currentFolderName) continue
            if (!LocalListSort.matches(videoList[i].title, searchKeyword)) continue
            visibleVideos.add(i)
        }
        visibleFolderCount = visibleFolders.size
        visibleVideoCount = visibleVideos.size

        if (currentFolderName.isEmpty()) {
            flatItems.add(FlatItem(TYPE_GOTO_DOWNLOAD))

            // 文件夹区域：标题只在这一区至少有一条命中时才画
            if (visibleFolders.isNotEmpty()) {
                flatItems.add(FlatItem(TYPE_SECTION_FOLDER_HEADER))
                for (i in visibleFolders) flatItems.add(FlatItem(TYPE_FOLDER_ITEM, folderIndex = i))
            }

            flatItems.add(FlatItem(TYPE_ACTION_NEW_FOLDER))

            // 「未分类视频」：不搜索时保持原样（标题 + 标题下的空态行，空态放这里是因为浮层
            // 会和「新建文件夹」按钮重合，且不参与滚动，见 26.10.20 用户反馈）；
            // 搜索时 0 条就先不画这个分区，整页都没命中时再在末尾补一句「没有匹配的缓存」。
            if (visibleVideos.isNotEmpty()) {
                flatItems.add(FlatItem(TYPE_SECTION_VIDEO_HEADER))
                for (i in visibleVideos) flatItems.add(FlatItem(TYPE_VIDEO_ITEM, videoIndex = i))
            } else if (searchKeyword.isEmpty()) {
                flatItems.add(FlatItem(TYPE_SECTION_VIDEO_HEADER))
                flatItems.add(FlatItem(TYPE_EMPTY_TIP))
            }
            if (searchKeyword.isNotEmpty() && visibleFolders.isEmpty() && visibleVideos.isEmpty()) {
                flatItems.add(FlatItem(TYPE_EMPTY_TIP))
            }
        } else {
            flatItems.add(FlatItem(TYPE_SECTION_VIDEO_HEADER))
            for (i in visibleVideos) flatItems.add(FlatItem(TYPE_VIDEO_ITEM, videoIndex = i))
            if (visibleVideos.isEmpty()) {
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
                    sectionCount.text = "${visibleFolderCount}个"
                }
            }
            TYPE_SECTION_VIDEO_HEADER -> {
                (holder as SectionHeaderHolder).apply {
                    if (currentFolderName.isEmpty()) {
                        sectionTitle.text = UiText.of("未分类视频")
                        sectionCount.text = "${visibleVideoCount}个"
                    } else {
                        sectionTitle.text = currentFolderName
                        sectionCount.text = "${visibleVideoCount}个"
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
            TYPE_EMPTY_TIP -> {
                // 搜索没命中时给一句「没有匹配的缓存」，别让用户以为缓存被清空了
                (holder as EmptyTipHolder).apply {
                    text.text = context.getString(
                        if (searchKeyword.isEmpty()) R.string.text_list_empty
                        else R.string.text_cache_search_empty
                    )
                }
            }
        }
    }

    private fun bindFolderItem(holder: FolderItemHolder, folderIndex: Int) {
        if (folderIndex < 0 || folderIndex >= folderList.size) return
        val folder = folderList[folderIndex]

        holder.folderName.text = folder.name
        holder.folderCount.text = "${folder.getVideoCount()}个视频"
        applySelection(holder, holder.folderName, selectionMode && selectedFolderNames.contains(folder.name))

        if (selectionMode) {
            // 多选态：点一下 = 勾选/取消，不再进文件夹；长按也走勾选，避免「长按弹出菜单、松手又跳进文件夹」。
            holder.itemView.setOnClickListener { onFolderToggle?.invoke(folderIndex) }
            holder.itemView.setOnLongClickListener {
                onFolderToggle?.invoke(folderIndex)
                true
            }
            return
        }

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
        applySelection(holder, holder.title, selectionMode && selectedVideoTitles.contains(video.title))

        if (selectionMode) {
            holder.itemView.setOnClickListener { onVideoToggle?.invoke(videoIndex) }
            holder.itemView.setOnLongClickListener {
                onVideoToggle?.invoke(videoIndex)
                true
            }
            return
        }

        holder.itemView.setOnClickListener { playVideo(video) }
        holder.itemView.setOnLongClickListener {
            onVideoMenu?.invoke(videoIndex)
            true
        }
    }

    /**
     * 画勾选态：勾中项正常亮度 + 前缀「✓ 」，未勾项压暗 + 全角空格占位。
     *
     * 不用复选框：`cell_video_local.xml` 里那个 `checkbox_select` 会把内容整体右移，
     * 而收藏夹多选是「压暗 + 勾号」这一套，两个页面用同一种视觉更不容易看懵。
     *
     * 前缀不需要剥离：每次绑定都先 setText 原始标题（[VideoItemHolder.showVideo] / 上面文件夹那行），
     * 再进来加前缀，holder 复用不会把前缀叠起来。
     */
    private fun applySelection(holder: RecyclerView.ViewHolder, label: TextView, selected: Boolean) {
        if (!selectionMode) {
            holder.itemView.alpha = 1f
            return
        }
        holder.itemView.alpha = if (selected) 1f else 0.5f
        label.text = TextUtils.concat(if (selected) PREFIX_SELECTED else PREFIX_UNSELECTED, label.text)
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

    /**
     * 空态提示行。布局里的默认文案是 @string/text_list_empty，
     * 搜索无结果时由 [onBindViewHolder] 换成「没有匹配的缓存」（反馈 #94）。
     */
    class EmptyTipHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.text)
    }

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
