package com.RobinNotBad.BiliClient.adapter

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.MenuConfig
import com.RobinNotBad.BiliClient.util.MsgUtil

/**
 * 菜单设置页适配器：上下两个分区（已启用/未启用）+ 底部说明。
 *
 * 交互：
 * - 已启用项长按一次开始拖拽，在已启用区内调整顺序；
 * - 拖拽到未启用区域（未启用标题或未启用项）将其移入未启用区（固定项提示不可隐藏）；
 * - 点击未启用项追加到已启用列表末尾。
 */
class MenuSettingAdapter(
    enabledList: List<String>,
    private val titleOf: (String) -> String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onChanged(enabled: List<String>)
    }

    var listener: Listener? = null
    var touchHelper: ItemTouchHelper? = null

    private val enabled = ArrayList(enabledList)
    private val disabled = ArrayList(MenuConfig.disabledFrom(enabledList))

    /** 固定项移入未启用区的提示去重时间戳。 */
    private var lastFixedHintTime: Long = 0

    companion object {
        private const val TYPE_HEADER_ENABLED = 0
        private const val TYPE_ITEM_ENABLED = 1
        private const val TYPE_HEADER_DISABLED = 2
        private const val TYPE_ITEM_DISABLED = 3
        private const val TYPE_FOOTER = 4
        private const val FIXED_HINT_INTERVAL_MS = 800L
    }

    // ---- 行号 <-> 数据映射（两个分区标题与底部说明始终渲染，行结构稳定）----
    // 0: 已启用标题；1..enabled.size: 已启用项；enabled.size+1: 未启用标题；
    // enabled.size+2 .. enabled.size+1+disabled.size: 未启用项；最后一行: 底部说明。
    private fun enabledStart() = 1
    private fun disabledHeader() = enabled.size + 1
    private fun disabledStart() = enabled.size + 2

    override fun getItemCount(): Int = enabled.size + disabled.size + 3

    override fun getItemViewType(position: Int): Int {
        val n = enabled.size
        val d = disabled.size
        return when (position) {
            0 -> TYPE_HEADER_ENABLED
            in 1..n -> TYPE_ITEM_ENABLED
            n + 1 -> TYPE_HEADER_DISABLED
            in n + 2..n + 1 + d -> TYPE_ITEM_DISABLED
            else -> TYPE_FOOTER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER_ENABLED, TYPE_HEADER_DISABLED ->
                HeaderHolder(inflater.inflate(R.layout.item_menu_setting_header, parent, false))
            TYPE_ITEM_ENABLED, TYPE_ITEM_DISABLED ->
                ItemHolder(inflater.inflate(R.layout.item_menu_setting, parent, false))
            else ->
                FooterHolder(inflater.inflate(R.layout.item_menu_setting_footer, parent, false))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderHolder -> holder.title.setText(
                if (position == 0) R.string.desc_menu_enabled else R.string.desc_menu_disabled
            )
            is ItemHolder -> {
                if (getItemViewType(position) == TYPE_ITEM_ENABLED) bindEnabled(holder, position)
                else bindDisabled(holder, position)
            }
            is FooterHolder -> holder.text.setText(R.string.desc_menu_footer)
        }
    }

    private fun bindEnabled(holder: ItemHolder, position: Int) {
        val key = enabled[position - enabledStart()]
        holder.name.text = titleOf(key)
        holder.fixed.visibility = if (key in MenuConfig.FIXED_ITEMS) View.VISIBLE else View.GONE
        holder.name.alpha = 1f
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener {
            touchHelper?.startDrag(holder)
            true
        }
    }

    private fun bindDisabled(holder: ItemHolder, position: Int) {
        val key = disabled[position - disabledStart()]
        holder.name.text = titleOf(key)
        holder.fixed.visibility = View.GONE
        holder.name.alpha = 0.5f
        holder.itemView.setOnLongClickListener(null)
        holder.itemView.setOnClickListener {
            disabled.remove(key)
            enabled.add(key)
            notifyDataSetChanged()
            listener?.onChanged(enabled)
        }
    }

    val dragCallback: ItemTouchHelper.Callback = object : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled() = false

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            if (getItemViewType(viewHolder.bindingAdapterPosition) != TYPE_ITEM_ENABLED) return 0
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (getItemViewType(from) != TYPE_ITEM_ENABLED) return false

            val fromIndex = from - enabledStart()
            val key = enabled[fromIndex]

            return when (getItemViewType(to)) {
                TYPE_ITEM_ENABLED -> {
                    // 已启用区内部排序
                    if (from == to) return false
                    enabled.removeAt(fromIndex)
                    enabled.add(to - enabledStart(), key)
                    notifyItemMoved(from, to)
                    listener?.onChanged(enabled)
                    true
                }
                TYPE_HEADER_DISABLED, TYPE_ITEM_DISABLED -> {
                    // 拖入未启用区：固定项不可隐藏
                    if (key in MenuConfig.FIXED_ITEMS) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastFixedHintTime > FIXED_HINT_INTERVAL_MS) {
                            lastFixedHintTime = now
                            MsgUtil.showMsg(
                                viewHolder.itemView.context.getString(R.string.desc_menu_fixed_hint)
                            )
                        }
                        return false
                    }
                    // 计算插入到未启用列表的位置（拖到未启用标题则插入头部）
                    val di: Int
                    val toFinal: Int
                    if (getItemViewType(to) == TYPE_HEADER_DISABLED) {
                        di = 0
                        toFinal = to
                    } else {
                        di = to - disabledStart()
                        toFinal = to - 1
                    }
                    enabled.removeAt(fromIndex)
                    disabled.add(di, key)
                    notifyItemMoved(from, toFinal)
                    listener?.onChanged(enabled)
                    true
                }
                else -> false
            }
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
