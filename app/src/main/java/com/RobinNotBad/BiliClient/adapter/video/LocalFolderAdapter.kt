package com.RobinNotBad.BiliClient.adapter.video

import android.content.Context
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
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.RobinNotBad.BiliClient.util.UiText
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

/**
 * 独立文件夹列表页（`activity/video/local/LocalFolderListActivity`）的 adapter。
 *
 * <h3>行结构</h3>
 * 第 0 行固定是「新建文件夹」，之后每行一个文件夹，文件夹为空时末尾补一行空态提示。
 * 卡片视觉照收藏夹列表页（`cell_favorite_folder_list.xml`）：左侧 16:9 封面（取夹内第一条缓存视频
 * 目录里的 `cover.png`），右侧文件夹名 + 「N 个视频」。
 *
 * <h3>为什么数据是算好的行、不是现读</h3>
 * 文件夹数量、每个夹的视频数、封面路径都要读盘（`FolderManager` 的 json + 视频目录）。
 * 放到 `bindViewHolder` 里做会在每次滑动时读一遍盘；页面加载时算好一次性交进来，
 * 拖动排序只重排这个列表，不触发任何 IO。
 *
 * <h3>多选 / 排序</h3>
 * 与缓存页顶层文件夹分区同一套：多选用「▸ + 主色 / › + 半透明」引导符（`CacheListAdapter.applySelection`），
 * 排序时点条目无效、长按开始拖动。`isLongPressDragEnabled = false`——拖动入口放在长按里，
 * 否则多选态长按也会被当成拖动。
 */
class LocalFolderAdapter(private val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CREATE = 0
        private const val TYPE_FOLDER = 1
        private const val TYPE_EMPTY = 2

        /** 多选引导符：与 `TerminalDialog` 单/多选列表一致。 */
        private const val GLYPH_SELECTED = "▸"
        private const val GLYPH_UNSELECTED = "›"
    }

    /**
     * 一行文件夹。
     *
     * @param videoCount 夹内视频条数（页面加载时按 `FolderManager.getFolderVideos(name).size` 算好）
     * @param cover 夹内第一条视频目录里的 `cover.png` 绝对路径；算不出（空夹 / 视频目录已被删）时为空
     */
    data class FolderRow(val name: String, val videoCount: Int, val cover: String?)

    /** 当前显示顺序的文件夹（拖动排序直接改它）。 */
    private val items = ArrayList<FolderRow>()

    var sortMode = false

    /** 由页面挂上的 ItemTouchHelper：排序态长按要走它开始拖。 */
    var touchHelper: ItemTouchHelper? = null

    var selectionMode = false

    /** 已选文件夹名。页面持有同一份集合，这里只读。 */
    var selectedNames: Set<String> = emptySet()

    var onFolderClick: ((FolderRow) -> Unit)? = null
    var onFolderMenu: ((FolderRow) -> Unit)? = null
    var onCreateClick: (() -> Unit)? = null
    var onFolderToggle: ((FolderRow) -> Unit)? = null

    /** 拖动结束时回传新顺序（只在顺序真的变过时回调，落盘由页面做）。 */
    var onOrderDragFinished: ((List<String>) -> Unit)? = null

    /** 拖动中是否已经挪过行；没挪过就不必落盘。 */
    private var orderDirty = false

    fun setData(rows: List<FolderRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    /** 当前顺序的文件夹名（落盘 / 空态判断用）。 */
    fun currentOrder(): List<String> = items.map { it.name }

    /** 按名字刷一行（多选勾选态变化时用，避免整表重绑）。 */
    fun notifyRowChanged(name: String) {
        val index = items.indexOfFirst { it.name == name }
        if (index >= 0) notifyItemChanged(index + 1)
    }

    /**
     * 每个标题 TextView 的「常态文字色」。
     *
     * 多选时标题会被改成主色，退出多选要还原；不能现读 `currentTextColor`（会读到上一次留下的主色），
     * 所以按 view 记一份。弱引用键：holder 被回收后条目自然消失，不会把 View 一直吊着。
     */
    private val normalLabelColors = java.util.WeakHashMap<TextView, Int>()

    private fun normalColorOf(label: TextView): Int =
        normalLabelColors.getOrPut(label) { label.currentTextColor }

    // ==================== Adapter 本体 ====================

    override fun getItemCount(): Int = 1 + items.size + if (items.isEmpty()) 1 else 0

    override fun getItemViewType(position: Int): Int = when {
        position == 0 -> TYPE_CREATE
        position <= items.size -> TYPE_FOLDER
        else -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return when (viewType) {
            // 「新建文件夹」沿用缓存页那一行（cell_action_button）：主色文字 + 圆角底板
            TYPE_CREATE -> ActionHolder(inflater.inflate(R.layout.cell_action_button, parent, false))
            TYPE_FOLDER -> FolderRowHolder(inflater.inflate(R.layout.cell_local_folder_list, parent, false))
            else -> EmptyHolder(inflater.inflate(R.layout.cell_empty_tip, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ActionHolder -> bindCreate(holder)
            is FolderRowHolder -> bindFolder(holder, position)
            is EmptyHolder -> holder.text.text = UiText.of("还没有文件夹")
        }
    }

    private fun bindCreate(holder: ActionHolder) {
        holder.text.text = UiText.of("新建文件夹")
        holder.itemView.setOnClickListener {
            // 排序 / 多选中不跳新建流程：整理到一半弹输入框很容易误触
            if (sortMode || selectionMode) return@setOnClickListener
            onCreateClick?.invoke()
        }
    }

    private fun bindFolder(holder: FolderRowHolder, position: Int) {
        val row = items.getOrNull(position - 1) ?: return
        holder.title.text = row.name
        holder.count.text = context.getString(R.string.text_local_folder_video_count, row.videoCount)
        loadCover(holder.cover, row.cover)
        applySelection(holder.title, selectionMode && selectedNames.contains(row.name))

        // 监听器里现读多选 / 排序状态：拖动排序后 holder 会被复用重绑，闭包里存下标会取到旧位置
        holder.itemView.setOnClickListener {
            when {
                selectionMode -> onFolderToggle?.invoke(row)
                sortMode -> Unit
                else -> onFolderClick?.invoke(row)
            }
        }
        holder.itemView.setOnLongClickListener {
            when {
                selectionMode -> onFolderToggle?.invoke(row)
                sortMode -> touchHelper?.startDrag(holder)
                else -> onFolderMenu?.invoke(row)
            }
            true
        }
    }

    /**
     * 封面：本地文件路径，直接用 Glide 读盘。
     *
     * 与缓存页视频卡片同一口径用 `DiskCacheStrategy.NONE`——夹内视频删掉后封面文件也没了，
     * 走磁盘缓存会把已删除的封面继续画出来。
     */
    private fun loadCover(image: ImageView, path: String?) {
        if (path.isNullOrEmpty()) {
            // 空文件夹：布局里本来就是占位图，这里显式设一次，防止 holder 复用留下上一个夹的封面
            image.setImageResource(R.mipmap.placeholder)
            return
        }
        try {
            GlideUtil.with(image).asDrawable().load(path)
                .transition(GlideUtil.getTransitionOptions())
                .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(image)
        } catch (_: Exception) {
            image.setImageResource(R.mipmap.placeholder)
        }
    }

    /**
     * 画勾选态：与缓存页顶层文件夹多选同一套视觉——标题前一个引导符，
     * 选中是 `▸` 且标题走主色，未选中是 `›` 且标题保持常态色。
     *
     * 前缀通常不需要剥离（每次绑定都先 setText 文件夹名），但局部刷新可能复用 holder
     * 而没重绑，这里先剥一次兜底。
     */
    private fun applySelection(label: TextView, selected: Boolean) {
        val normal = normalColorOf(label)
        if (label.text?.startsWith(GLYPH_SELECTED) == true || label.text?.startsWith(GLYPH_UNSELECTED) == true) {
            label.text = label.text.subSequence(2, label.text.length)
        }
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

    // ==================== 拖动排序 ====================

    /**
     * 长按拖动重排文件夹。
     *
     * 与 `CacheListAdapter.dragCallback` 同一套做法：`isLongPressDragEnabled = false`（拖动入口在长按里），
     * 只有文件夹行能拖，落盘放到 [clearView] 里 `recyclerView.post` —
     * 拖完那一刻 RecyclerView 可能还在算布局，直接 notify / 写盘会撞
     * "Cannot call this method while RecyclerView is computing a layout or scrolling"。
     */
    val dragCallback: ItemTouchHelper.Callback = object : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled(): Boolean = false

        override fun isItemViewSwipeEnabled(): Boolean = false

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            if (!sortMode || viewHolder.itemViewType != TYPE_FOLDER) return makeMovementFlags(0, 0)
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            // 0 是「新建文件夹」行、末尾可能是空态行，两头都不参与排序
            if (from <= 0 || to <= 0 || from > items.size || to > items.size) return false
            val moved = items.removeAt(from - 1)
            items.add(to - 1, moved)
            notifyItemMoved(from, to)
            orderDirty = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (!orderDirty) return
            orderDirty = false
            val order = currentOrder()
            recyclerView.post { onOrderDragFinished?.invoke(order) }
        }
    }

    // ==================== ViewHolder ====================

    class ActionHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.text)
    }

    class FolderRowHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.text_title)
        val count: TextView = itemView.findViewById(R.id.text_itemcount)
        val cover: ImageView = itemView.findViewById(R.id.img_cover)
    }

    class EmptyHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.text)
    }
}
