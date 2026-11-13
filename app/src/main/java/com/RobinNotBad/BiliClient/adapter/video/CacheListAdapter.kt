package com.RobinNotBad.BiliClient.adapter.video

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalFolderListActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalPageChooseActivity
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.LocalVideo
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.VideoFolder
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.FolderPagePrefs
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
 * - 「文件夹 N」入口（仅设置里打开 `FolderPagePrefs.ENABLE` 时出现，点它进独立文件夹页）
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
 * 手动排序（26.11.05，反馈 #94 返工）：用户要的是「缓存自己排序」，所以排序不再按字段排，
 * 而是进 [sortMode] 后长按条目拖动，顺序即时写回 Model，松手由 Activity 落盘。这里只保存
 * 「原始下标」的显示顺序（[flatItems]），Activity 的多选 / 删除 / 刷新逻辑一行都不用改。
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

        /**
         * 「文件夹 N」入口行（26.11.13，设置里打开 `FolderPagePrefs.ENABLE` 后才出现）。
         *
         * 加在末尾而不是插在中间：中间那些 0~6 的类型序是历史约定，别动。
         */
        private const val TYPE_GOTO_FOLDERS = 7

        /** 多选态标题前的引导符，与 `TerminalDialog` / `VideoCardHolder` / `DownloadAdapter` 同一套（反馈 #140）。 */
        private const val GLYPH_SELECTED = "▸"
        private const val GLYPH_UNSELECTED = "›"
    }

    // 数据
    private var folderList: ArrayList<VideoFolder> = ArrayList()
    private var videoList: ArrayList<LocalVideo> = ArrayList()

    var currentFolderName: String = ""

    /**
     * 排序模式：打开后条目可以长按拖动；关闭时点击播放 / 进夹，长按弹功能菜单。
     *
     * 只影响手势，不需要 notifyDataSetChanged：绑定时的点击/长按回调每次都读这个字段，
     * 进入/退出排序模式不会重绑整屏封面（封面用 DiskCacheStrategy.NONE，整屏重绑会明显闪）。
     */
    var sortMode: Boolean = false

    /** 由 Activity 在 attach ItemTouchHelper 后回填，长按条目时用它启动拖动（同 MenuSettingAdapter）。 */
    var touchHelper: ItemTouchHelper? = null

    /** 已归一化的搜索词；空串 = 不过滤。同时匹配视频标题与文件夹名（忽略大小写）。 */
    var searchKeyword: String = ""
        private set

    /**
     * 未分类视频的持久化顺序（标题列表），由 Activity 从 [com.RobinNotBad.BiliClient.util.CacheListOrderStore] 读出来交进来。
     *
     * 拖动时会就地更新这份内存态（[moveVideo]），保证重新进/出文件夹后还是用户拖出来的顺序；
     * 真正落盘由 Activity 在拖动结束时做，适配器不碰文件、也不读 SharedPreferences。
     */
    private var unclassifiedOrder: List<String> = emptyList()

    /** 当前未分类视频的顺序，拖动结束后 Activity 拿去落盘。 */
    val unclassifiedVideoOrder: List<String>
        get() = unclassifiedOrder

    /** 一次拖动结束（离手）时回调，参数表示这次拖过哪一类。Activity 收到后把当前顺序落盘。 */
    var onOrderDragFinished: ((foldersChanged: Boolean, videosChanged: Boolean) -> Unit)? = null

    private var folderOrderDirty = false
    private var videoOrderDirty = false

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

    /**
     * 点「文件夹 N」入口行（仅 [FolderPagePrefs] 开关打开时有这一行）。
     *
     * 不接的话（默认 null）适配器自己 `startActivity` 去独立文件夹页；接了就完全交给 Activity，
     * 由它用 `registerForActivityResult` 起页并在回来时重扫——缓存页的未分类分区也可能被改到。
     */
    var onFolderListClick: (() -> Unit)? = null
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
     * 换搜索词后重建列表。
     *
     * 必须整体重建而不是逐条 notifyItemXXX：搜索可能把整个文件夹区域藏掉，条目集合和分组数量都会变，
     * 逐条刷得自己算一大堆位移。勾选状态按标题记（[selectedVideoTitles] / [selectedFolderNames]），
     * 重建后由 [applySelection] 重新画出来，过滤不会丢勾选。
     */
    fun setSearchKeyword(keyword: String) {
        val normalized = LocalListSort.normalizeKeyword(keyword)
        if (searchKeyword == normalized) return
        searchKeyword = normalized
        rebuildFlatList()
        notifyDataSetChanged()
    }

    /**
     * 设置未分类视频的持久化顺序（进页面 / 重扫后由 Activity 调，拖动中途不要调）。
     *
     * 顺序变化后要重建 flatItems，因为顶层「未分类视频」区就是按它画的。
     */
    fun setUnclassifiedOrder(order: List<String>) {
        if (unclassifiedOrder == order) return
        unclassifiedOrder = order
        if (currentFolderName.isEmpty()) {
            rebuildFlatList()
            notifyDataSetChanged()
        }
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

        // 显示顺序 = 用户手动拖出来的顺序：
        // 夹与夹之间看 folderList 的顺序（已持久化在 .folders_info.json），
        // 视频看各自存着的标题序（夹内 = VideoFolder.videoTitles，未分类 = unclassifiedOrder）。
        // flatItems 里仍然只放「原始下标」，Activity 侧的下标语义不用换。
        val visibleFolders = ArrayList<Int>()
        if (currentFolderName.isEmpty()) {
            for (i in folderList.indices) {
                if (LocalListSort.matches(folderList[i].name, searchKeyword)) visibleFolders.add(i)
            }
        }

        // 先把当前 scope 的扫描下标收齐，再按持久化的标题序排；没记录的新条目由
        // orderByStoredTitles 按扫描顺序补到最后，不会因为「记录里没有」就消失。
        val scopeIndices = ArrayList<Int>()
        if (currentFolderName.isEmpty()) {
            for (i in videoList.indices) {
                // 文件夹内的视频在文件夹里显示
                if (videoList[i].folderName.isNullOrEmpty()) scopeIndices.add(i)
            }
        } else {
            for (i in videoList.indices) {
                if (videoList[i].folderName == currentFolderName) scopeIndices.add(i)
            }
        }
        val storedTitles: List<String> = if (currentFolderName.isEmpty()) {
            unclassifiedOrder
        } else {
            folderList.find { it.name == currentFolderName }?.videoTitles ?: emptyList()
        }
        val videoOrder = LocalListSort.orderByStoredTitles(scopeIndices, storedTitles) { videoList[it].title }

        // 再把「这一屏真的要画的」筛出来，然后才决定分区标题 / 空态画不画。
        // 不能边扫边画：搜索只命中文件夹时，未分类视频是 0 条，边扫边判断会多补一句
        // 「没有匹配的缓存」，可实际上列表里明明有命中的文件夹。
        val visibleVideos = ArrayList<Int>()
        for (i in videoOrder) {
            if (!LocalListSort.matches(videoList[i].title, searchKeyword)) continue
            visibleVideos.add(i)
        }
        visibleFolderCount = visibleFolders.size
        visibleVideoCount = visibleVideos.size

        if (currentFolderName.isEmpty()) {
            // 「缓存页文件夹入口」开关（默认关）：开 = 顶部只留一行「文件夹 N」，文件夹分区与
            // 「新建文件夹」全搬到独立文件夹页去；关 = 下面这堆一行都不变，与改动前完全一致。
            // 每次重建都现读开关，改完设置回来（重扫时）就生效，不需要重启。
            val folderPageEnabled = FolderPagePrefs.enabled()
            if (folderPageEnabled) {
                flatItems.add(FlatItem(TYPE_GOTO_FOLDERS))
            }
            flatItems.add(FlatItem(TYPE_GOTO_DOWNLOAD))

            if (!folderPageEnabled) {
                // 文件夹区域：标题只在这一区至少有一条命中时才画
                if (visibleFolders.isNotEmpty()) {
                    flatItems.add(FlatItem(TYPE_SECTION_FOLDER_HEADER))
                    for (i in visibleFolders) flatItems.add(FlatItem(TYPE_FOLDER_ITEM, folderIndex = i))
                }

                flatItems.add(FlatItem(TYPE_ACTION_NEW_FOLDER))
            }

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
            // 开关打开时文件夹不在本页画，搜索是否命中文件夹也就跟本页无关了：
            // 只看视频有没有命中，免得「只命中文件夹」时补一句误导人的「没有匹配的缓存」。
            val nothingMatched = if (folderPageEnabled) {
                visibleVideos.isEmpty()
            } else {
                visibleFolders.isEmpty() && visibleVideos.isEmpty()
            }
            if (searchKeyword.isNotEmpty() && nothingMatched) {
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

    /**
     * 拖动排序的实现。
     *
     * 复用菜单设置页那套写法（`MenuSettingAdapter.dragCallback`）：
     * `isLongPressDragEnabled = false` + 条目长按里手动 `startDrag`，这样常态长按仍然是功能菜单，
     * 只有排序模式才变成拖动手势。
     *
     * 拖动范围只到「同一分区内」：分区标题 / 新建文件夹 / 下载列表入口 / 空态都不是可拖类型，
     * `onMove` 直接返回 false，ItemTouchHelper 会停在原地，不用自己算上下边界。
     */
    val dragCallback: ItemTouchHelper.Callback = object : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled(): Boolean = false

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            if (!sortMode) return 0
            val position = viewHolder.bindingAdapterPosition
            if (position < 0 || position >= flatItems.size) return 0
            return when (flatItems[position].type) {
                TYPE_FOLDER_ITEM, TYPE_VIDEO_ITEM ->
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                else -> 0
            }
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            if (!sortMode) return false
            // 搜索过滤后拖动只会在「可见子集」里排，落盘时无法表达被隐藏条目的相对位置；
            // Activity 进排序模式前会先清空搜索词，这里再兜一层底。
            if (searchKeyword.isNotEmpty()) return false
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0 || from == to) return false
            val fromItem = flatItems.getOrNull(from) ?: return false
            val toItem = flatItems.getOrNull(to) ?: return false
            if (fromItem.type != toItem.type) return false
            return when (fromItem.type) {
                TYPE_FOLDER_ITEM -> moveFolder(from, to, fromItem, toItem)
                TYPE_VIDEO_ITEM -> moveVideo(from, to)
                else -> false
            }
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (!folderOrderDirty && !videoOrderDirty) return
            val foldersChanged = folderOrderDirty
            val videosChanged = videoOrderDirty
            folderOrderDirty = false
            videoOrderDirty = false
            // 与 MenuSettingAdapter.clearView 同一个理由：这里还在 RecyclerView 的 scroll/layout 回调里，
            // 当场写文件 / notify 会撞上 "Cannot call this method while RecyclerView is computing a layout
            // or scrolling"（线上踩过 6 例），post 到下一帧再交给 Activity 落盘。
            recyclerView.post { onOrderDragFinished?.invoke(foldersChanged, videosChanged) }
        }
    }

    /**
     * 把文件夹从 [from] 拖到 [to]。
     *
     * 顺序载体就是 folderList 本身（对应 .folders_info.json 的数组顺序），所以直接在上面做
     * removeAt/add。FlatItem.folderIndex 存的是 folderList 下标，列表挪过之后旧值会整体错位，
     * 这里按文件夹条目当前的先后重编号一次（排序模式下不搜索，文件夹条目就是全部文件夹）。
     */
    private fun moveFolder(from: Int, to: Int, fromItem: FlatItem, toItem: FlatItem): Boolean {
        val fromIndex = fromItem.folderIndex
        val toIndex = toItem.folderIndex
        if (fromIndex !in folderList.indices || toIndex !in folderList.indices) return false
        val moved = folderList.removeAt(fromIndex)
        folderList.add(toIndex, moved)

        flatItems.add(to, flatItems.removeAt(from))
        var index = 0
        for (i in flatItems.indices) {
            val item = flatItems[i]
            if (item.type != TYPE_FOLDER_ITEM) continue
            if (item.folderIndex != index) {
                flatItems[i] = FlatItem(TYPE_FOLDER_ITEM, folderIndex = index)
            }
            index++
        }
        notifyItemMoved(from, to)
        folderOrderDirty = true
        return true
    }

    /**
     * 把视频从 [from] 拖到 [to]。
     *
     * 视频相对顺序没有「列表本身」可写，所以拖完从 flatItems 里按出现顺序取标题，
     * 合并进持久化顺序的内存态（未分类写 [unclassifiedOrder]，夹内写 VideoFolder.videoTitles）。
     * 真正落盘在 [onOrderDragFinished] 里由 Activity 做，避免 onMove 高频写文件。
     */
    private fun moveVideo(from: Int, to: Int): Boolean {
        flatItems.add(to, flatItems.removeAt(from))
        notifyItemMoved(from, to)

        val titles = ArrayList<String>(flatItems.size)
        for (item in flatItems) {
            if (item.type != TYPE_VIDEO_ITEM) continue
            val index = item.videoIndex
            if (index in videoList.indices) titles.add(videoList[index].title)
        }
        if (currentFolderName.isEmpty()) {
            unclassifiedOrder = LocalListSort.mergeOrder(titles, unclassifiedOrder)
        } else {
            val folder = folderList.find { it.name == currentFolderName }
            if (folder != null) {
                // 夹内顺序的唯一载体就是 videoTitles，内存态要同步改，否则退出再进夹会跳回旧顺序；
                // 旧记录里可能还有这次没显示出来的标题，mergeOrder 会按原相对顺序排在后面。
                folder.videoTitles = ArrayList(LocalListSort.mergeOrder(titles, folder.videoTitles ?: emptyList()))
            }
        }
        videoOrderDirty = true
        return true
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
            TYPE_GOTO_FOLDERS -> {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_goto, parent, false)
                FolderEntryHolder(view)
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
                // 排序模式是「专心的整理态」：入口类点击先不响应，免得拖到一半跳去下载列表
                (holder as GotoHolder).show(context) { !sortMode }
            }
            TYPE_GOTO_FOLDERS -> {
                // 文件夹数量取的是「全部文件夹」，不是搜索命中数：这一行是进独立页的入口，
                // 搜索的是视频/夹名，入口上的总数不该跟着过滤变。
                val entry = holder as FolderEntryHolder
                entry.show(context.getString(R.string.text_cache_folder_entry, folderList.size))
                entry.itemView.setOnClickListener {
                    // 排序模式里入口先不响应（与「下载列表」入口同一口径），免得拖到一半跳页
                    if (sortMode) return@setOnClickListener
                    val callback = onFolderListClick
                    if (callback != null) {
                        // 接了回调就完全交给 Activity：它用 launcher 起页并在回来时重扫
                        callback.invoke()
                    } else {
                        context.startActivity(Intent(context, LocalFolderListActivity::class.java))
                    }
                }
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
                        // 这个 viewType 夹内也在用：从文件夹退到顶层时复用的 holder 可能还带着「点击返回」，
                        // 不清掉的话点「未分类视频」标题会把用户又送回文件夹
                        itemView.setOnClickListener(null)
                    } else {
                        sectionTitle.text = currentFolderName
                        sectionCount.text = "${visibleVideoCount}个"
                        // 点击标题返回；排序模式下列表正被整理，不响应返回，避免拖到一半退出文件夹
                        itemView.setOnClickListener { if (!sortMode) onBackFromFolder?.invoke() }
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
                    itemView.setOnClickListener { if (!sortMode) onNewFolderClick?.invoke() }
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

        // 多选 / 排序两种「临时态」进入和退出都只翻字段、不重绑列表（重绑会重读封面、明显闪一下），
        // 所以监听器里要在点击发生的那一刻再读状态，不能把当前状态烤进闭包：
        // - 多选：点击 / 长按都是勾选（避免「长按弹菜单、松手又跳进文件夹」）
        // - 排序：点击不动作（防误触进夹），长按抓起拖动
        // - 常态：点击进夹，长按弹功能菜单
        holder.itemView.setOnClickListener {
            when {
                selectionMode -> onFolderToggle?.invoke(folderIndex)
                sortMode -> Unit
                else -> onFolderClick?.invoke(folderIndex)
            }
        }
        holder.itemView.setOnLongClickListener {
            when {
                selectionMode -> onFolderToggle?.invoke(folderIndex)
                sortMode -> touchHelper?.startDrag(holder)
                else -> onFolderMenu?.invoke(folderIndex)
            }
            true
        }
    }

    private fun bindVideoItem(holder: VideoItemHolder, videoIndex: Int) {
        if (videoIndex < 0 || videoIndex >= videoList.size) return
        val video = videoList[videoIndex]

        holder.showVideo(video)
        applySelection(holder, holder.title, selectionMode && selectedVideoTitles.contains(video.title))

        // 同文件夹条目：监听器里现读多选 / 排序状态（理由见 bindFolderItem）
        holder.itemView.setOnClickListener {
            when {
                selectionMode -> onVideoToggle?.invoke(videoIndex)
                sortMode -> Unit
                else -> playVideo(video)
            }
        }
        holder.itemView.setOnLongClickListener {
            when {
                selectionMode -> onVideoToggle?.invoke(videoIndex)
                sortMode -> touchHelper?.startDrag(holder)
                else -> onVideoMenu?.invoke(videoIndex)
            }
            true
        }
    }

    /**
     * 每个标题 / 文件夹名 TextView 的「常态文字色」。
     *
     * 多选时会被改成主色，退出多选要还原；不能现读 `currentTextColor`（会读到上一次留下的主色），
     * 而这两类行各用各的 TextView（文件夹行、视频行），所以按 view 各记一份。
     * 用弱引用键：holder 被回收后条目自然消失，不会把 View 一直吊着。
     */
    private val normalLabelColors = java.util.WeakHashMap<TextView, Int>()

    private fun normalColorOf(label: TextView): Int =
        normalLabelColors.getOrPut(label) { label.currentTextColor }

    /**
     * 画勾选态：与 `TerminalDialog` 单/多选列表同一套视觉——标题前一个引导符，
     * 选中是 `▸` 且标题走主色，未选中是 `›` 且标题保持常态色（反馈 #140）。
     *
     * 不用复选框：`cell_video_local.xml` 里那个 `checkbox_select` 会把内容整体右移，
     * 而视频列表多选是「引导符 + 主色」这一套，几个页面用同一种视觉更不容易看懵。
     *
     * 也不再整卡压暗：那会把封面和时长一起弄灰，暗色主题下更是糊成一片。
     *
     * 前缀通常不需要剥离（每次绑定都先 setText 原始标题：`VideoItemHolder.showVideo` / 文件夹那行），
     * 但列表局部刷新时可能复用 holder 而没重绑，这里先剥一次兜底。
     */
    private fun applySelection(holder: RecyclerView.ViewHolder, label: TextView, selected: Boolean) {
        val normal = normalColorOf(label)
        if (label.text?.startsWith(GLYPH_SELECTED) == true || label.text?.startsWith(GLYPH_UNSELECTED) == true) {
            label.text = label.text.subSequence(2, label.text.length)
        }
        holder.itemView.alpha = 1f
        if (!selectionMode) {
            label.setTextColor(normal)
            return
        }

        val glyph = if (selected) GLYPH_SELECTED else GLYPH_UNSELECTED
        // 未选中引导符按常态色压到 70% 不透明度，等价于 TerminalDialog 的 secondaryColor()
        val glyphColor = if (selected) {
            ColorScheme.PRIMARY
        } else {
            Color.argb(179, Color.red(normal), Color.green(normal), Color.blue(normal))
        }
        val builder = SpannableStringBuilder(glyph).append(' ').append(label.text ?: "")
        builder.setSpan(
            ForegroundColorSpan(glyphColor),
            0,
            glyph.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        label.text = builder
        label.setTextColor(if (selected) ColorScheme.PRIMARY else normal)
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

    /**
     * 下载列表入口。
     *
     * @param clickEnabled 排序模式下传 `{ !sortMode }`：整理过程中不跳页，避免拖到一半滑走。
     */
    class GotoHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.text)
        fun show(context: Context, clickEnabled: () -> Boolean = { true }) {
            textView.text = UiText.of("下载列表")
            itemView.setOnClickListener {
                if (clickEnabled()) context.startActivity(Intent(context, DownloadListActivity::class.java))
            }
        }
    }

    /**
     * 「文件夹 N」入口行（设置里打开 [FolderPagePrefs.ENABLE] 才有）。
     *
     * 复用 `cell_goto` 的样式：与下面那条「下载列表」入口同一行观感，只换文案；
     * 点它进独立文件夹页，本页就不再铺文件夹分区。
     */
    class FolderEntryHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.text)

        fun show(label: String) {
            textView.text = label
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
