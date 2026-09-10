package com.RobinNotBad.BiliClient.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.MySpaceConfig

/**
 * 「我的页面设置」适配器：上下两个分区（主列表 / 更多列表），两区都可拖拽排序。
 *
 * 交互：
 * - 长按任一项开始拖拽：同区内调整顺序；拖入另一区则移入该区，落在拖到的位置；
 * - 点击更多区项目，将其放回主列表末尾。
 *
 * 用户卡片固定在最顶部、不参与本列表；「更多」按钮与「退出登录」由页面固定排在最后两位，
 * 因此这里不出现，也没有「固定」标记。
 */
class MySpaceSettingAdapter(
    layout: MySpaceConfig.Layout,
    private val titleOf: (String) -> String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onChanged(main: List<String>, more: List<String>)
    }

    var listener: Listener? = null
    var touchHelper: ItemTouchHelper? = null

    private val main = ArrayList(layout.main)
    private val more = ArrayList(layout.more)

    companion object {
        private const val TYPE_HEADER_MAIN = 0
        private const val TYPE_ITEM_MAIN = 1
        private const val TYPE_HEADER_MORE = 2
        private const val TYPE_ITEM_MORE = 3
        private const val TYPE_FOOTER = 4
    }

    // ---- 行号 <-> 数据映射（两个分区标题与底部说明始终渲染，行结构稳定）----
    // 0: 主列表标题；1..main.size: 主列表项；main.size+1: 更多标题；
    // 其后是更多项；最后一行: 底部说明。
    private fun mainStart() = 1
    private fun moreStart() = main.size + 2

    override fun getItemCount(): Int = main.size + more.size + 3

    override fun getItemViewType(position: Int): Int {
        val n = main.size
        return when (position) {
            0 -> TYPE_HEADER_MAIN
            in 1..n -> TYPE_ITEM_MAIN
            n + 1 -> TYPE_HEADER_MORE
            in n + 2..n + 1 + more.size -> TYPE_ITEM_MORE
            else -> TYPE_FOOTER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER_MAIN, TYPE_HEADER_MORE ->
                HeaderHolder(inflater.inflate(R.layout.item_menu_setting_header, parent, false))

            TYPE_ITEM_MAIN, TYPE_ITEM_MORE ->
                ItemHolder(inflater.inflate(R.layout.item_menu_setting, parent, false))

            else ->
                FooterHolder(inflater.inflate(R.layout.item_menu_setting_footer, parent, false))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderHolder -> holder.title.setText(
                if (position == 0) R.string.desc_myspace_main else R.string.desc_myspace_more
            )

            is ItemHolder ->
                if (getItemViewType(position) == TYPE_ITEM_MAIN) bindMain(holder, position) else bindMore(holder, position)

            is FooterHolder -> holder.text.setText(R.string.desc_myspace_footer)
        }
    }

    private fun bindMain(holder: ItemHolder, position: Int) {
        holder.name.text = titleOf(main[position - mainStart()])
        holder.fixed.visibility = View.GONE
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener {
            touchHelper?.startDrag(holder)
            true
        }
    }

    private fun bindMore(holder: ItemHolder, position: Int) {
        holder.name.text = titleOf(more[position - moreStart()])
        holder.fixed.visibility = View.GONE
        holder.itemView.setOnLongClickListener {
            touchHelper?.startDrag(holder)
            true
        }
        holder.itemView.setOnClickListener {
            val index = holder.bindingAdapterPosition - moreStart()
            if (index !in more.indices) return@setOnClickListener
            val key = more.removeAt(index)
            main.add(key)
            notifyDataSetChanged()
            notifyChanged()
        }
    }

    private fun notifyChanged() {
        listener?.onChanged(main.toList(), more.toList())
    }

    val dragCallback: ItemTouchHelper.Callback = object : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled() = false

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val type = getItemViewType(viewHolder.bindingAdapterPosition)
            if (type != TYPE_ITEM_MAIN && type != TYPE_ITEM_MORE) return 0
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0 || from == to) return false

            val fromType = getItemViewType(from)
            val isMainSource = fromType == TYPE_ITEM_MAIN
            if (!isMainSource && fromType != TYPE_ITEM_MORE) return false

            val toType = getItemViewType(to)
            val toMain = toType == TYPE_ITEM_MAIN || toType == TYPE_HEADER_MAIN
            val toMore = toType == TYPE_ITEM_MORE || toType == TYPE_HEADER_MORE
            if (!toMain && !toMore) return false

            val srcList = if (isMainSource) main else more
            val srcIndex = if (isMainSource) from - mainStart() else from - moreStart()
            if (srcIndex !in srcList.indices) return false

            // 目标区内的插入位置（拖到分区标题即插到该区头部）
            val dstIndex = when (toType) {
                TYPE_HEADER_MAIN, TYPE_HEADER_MORE -> 0
                TYPE_ITEM_MAIN -> to - mainStart()
                else -> to - moreStart()
            }

            val key = srcList[srcIndex]

            // 移动后该项应处的绝对行号：
            // 源在主列表时该区会少一项，更多区整体前移一行，其余情况行偏移不变。
            val toFinal = when {
                isMainSource && toMore -> moreStart() - 1 + dstIndex
                isMainSource -> mainStart() + dstIndex
                toMain -> mainStart() + dstIndex
                else -> moreStart() + dstIndex
            }

            srcList.removeAt(srcIndex)
            val dstList = if (toMain) main else more
            dstList.add(dstIndex.coerceIn(0, dstList.size), key)

            notifyItemMoved(from, toFinal.coerceIn(0, itemCount - 1))
            notifyChanged()
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.section_title)
    }

    class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.item_name)
        val fixed: TextView = view.findViewById(R.id.item_fixed)
    }

    class FooterHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.footer_text)
    }
}
