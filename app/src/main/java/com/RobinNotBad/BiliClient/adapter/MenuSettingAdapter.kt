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
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox

/**
 * 菜单设置页适配器：顶部使用说明 + 上下两个分区（已启用/未启用）。
 *
 * 交互：
 * - 已启用项短按 = 选中为「启动页」（单选，整行像按钮一样点一下即生效，带水波纹反馈）；
 * - 已启用项长按一次开始拖拽，在已启用区内调整顺序；
 * - 拖拽到未启用区域（未启用标题或未启用项）将其移入未启用区（固定项提示不可隐藏）；
 * - 拖入未启用区的若是当前启动页，离手时自动改选默认启动页并回调写回；
 * - 点击未启用项追加到已启用列表末尾；
 * - 使用说明点按展开/收起，长按两次隐藏（第一次长按只提示）。
 */
class MenuSettingAdapter(
    enabledList: List<String>,
    startKey: String,
    helpHidden: Boolean,
    private val titleOf: (String) -> String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        /** 已启用列表变化（顺序或成员），需要持久化。 */
        fun onChanged(enabled: List<String>)

        /** 启动页被用户改选（或失效回退），需要持久化。 */
        fun onStartChanged(key: String)

        /** 使用说明的显隐被用户切换，需要持久化。 */
        fun onHelpHiddenChanged(hidden: Boolean)
    }

    var listener: Listener? = null
    var touchHelper: ItemTouchHelper? = null

    private val enabled = ArrayList(enabledList)
    private val disabled = ArrayList(MenuConfig.disabledFrom(enabledList))

    /** 当前选中的启动页 key（始终指向已启用列表内的一项）。 */
    private var startKey: String = startKey

    /** 使用说明是否已被长按隐藏。 */
    private var helpHidden: Boolean = helpHidden

    /** 使用说明当前是否展开（纯界面状态，不持久化）。 */
    private var helpExpanded: Boolean = false

    /** 固定项移入未启用区的提示去重时间戳。 */
    private var lastFixedHintTime: Long = 0

    /** 首次长按说明后，允许「再长按一次即隐藏」的截止时刻。 */
    private var hideHintDeadline: Long = 0

    companion object {
        private const val TYPE_HELP = 0
        private const val TYPE_HELP_RESTORE = 1
        private const val TYPE_HEADER_ENABLED = 2
        private const val TYPE_ITEM_ENABLED = 3
        private const val TYPE_HEADER_DISABLED = 4
        private const val TYPE_ITEM_DISABLED = 5
        private const val FIXED_HINT_INTERVAL_MS = 800L
        private const val HIDE_HINT_WINDOW_MS = 2000L
    }

    // ---- 行号 <-> 数据映射（说明行 + 两个分区标题始终渲染，行结构稳定）----
    // 0: 使用说明（或隐藏后的「显示说明」）；1: 已启用标题；2..n+1: 已启用项；
    // n+2: 未启用标题；n+3 .. n+2+d: 未启用项。
    private fun enabledStart() = 2
    private fun disabledStart() = enabled.size + 3

    override fun getItemCount(): Int = enabled.size + disabled.size + 3

    override fun getItemViewType(position: Int): Int {
        val n = enabled.size
        val d = disabled.size
        return when (position) {
            0 -> if (helpHidden) TYPE_HELP_RESTORE else TYPE_HELP
            1 -> TYPE_HEADER_ENABLED
            in 2..n + 1 -> TYPE_ITEM_ENABLED
            n + 2 -> TYPE_HEADER_DISABLED
            in n + 3..n + 2 + d -> TYPE_ITEM_DISABLED
            else -> TYPE_HEADER_DISABLED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HELP ->
                HelpHolder(inflater.inflate(R.layout.item_menu_setting_help, parent, false))
            TYPE_HELP_RESTORE ->
                RestoreHolder(inflater.inflate(R.layout.item_menu_setting_help_restore, parent, false))
            TYPE_HEADER_ENABLED, TYPE_HEADER_DISABLED ->
                HeaderHolder(inflater.inflate(R.layout.item_menu_setting_header, parent, false))
            TYPE_ITEM_ENABLED, TYPE_ITEM_DISABLED ->
                ItemHolder(inflater.inflate(R.layout.item_menu_setting, parent, false))
            else ->
                HelpHolder(inflater.inflate(R.layout.item_menu_setting_help, parent, false))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HelpHolder -> bindHelp(holder)
            is RestoreHolder -> bindRestore(holder)
            is HeaderHolder -> holder.title.setText(
                if (position == 1) R.string.desc_menu_enabled else R.string.desc_menu_disabled
            )
            is ItemHolder -> {
                if (getItemViewType(position) == TYPE_ITEM_ENABLED) bindEnabled(holder, position)
                else bindDisabled(holder, position)
            }
        }
    }

    // ---- 顶部使用说明 ----

    private fun bindHelp(holder: HelpHolder) {
        holder.button.setText(
            if (helpExpanded) R.string.desc_menu_help_body else R.string.desc_menu_help_title
        )
        holder.button.setOnClickListener {
            helpExpanded = !helpExpanded
            notifyItemChanged(0)
        }
        holder.button.setOnLongClickListener {
            handleHelpLongPress(holder.itemView)
            true
        }
    }

    private fun bindRestore(holder: RestoreHolder) {
        holder.text.setOnClickListener {
            helpHidden = false
            helpExpanded = false
            notifyItemChanged(0)
            listener?.onHelpHiddenChanged(false)
        }
    }

    /** 长按说明：第一次只提示，2 秒内再长按一次才真正隐藏。 */
    private fun handleHelpLongPress(view: View) {
        val now = SystemClock.uptimeMillis()
        if (now <= hideHintDeadline) {
            hideHintDeadline = 0
            helpHidden = true
            helpExpanded = false
            notifyItemChanged(0)
            listener?.onHelpHiddenChanged(true)
            MsgUtil.showMsg(view.context.getString(R.string.msg_menu_help_hidden))
        } else {
            hideHintDeadline = now + HIDE_HINT_WINDOW_MS
            MsgUtil.showMsg(view.context.getString(R.string.msg_menu_help_hide_hint))
        }
    }

    // ---- 菜单项 ----

    private fun bindEnabled(holder: ItemHolder, position: Int) {
        val key = enabled[position - enabledStart()]
        holder.name.text = titleOf(key)
        holder.fixed.visibility = if (key in MenuConfig.FIXED_ITEMS) View.VISIBLE else View.GONE
        holder.name.alpha = 1f
        holder.check.visibility = View.VISIBLE
        holder.check.isChecked = key == startKey
        holder.itemView.setOnClickListener {
            // 拖拽后 holder 的绑定位置可能已变，按当前实际位置取 key，别用 bind 时捕获的 position
            val cur = holder.bindingAdapterPosition
            if (cur < 0 || getItemViewType(cur) != TYPE_ITEM_ENABLED) return@setOnClickListener
            val curIndex = cur - enabledStart()
            if (curIndex !in enabled.indices) return@setOnClickListener
            val curKey = enabled[curIndex]
            // 动作项（「退出」「继续播放」）不能当启动页：一个开机就收摊，一个开机直接进播放器
            if (curKey in MenuConfig.ACTION_ITEMS) {
                MsgUtil.showMsg(
                    holder.itemView.context.getString(
                        if (curKey == MenuConfig.RESUME) R.string.msg_menu_resume_not_start
                        else R.string.msg_menu_exit_not_start
                    )
                )
                return@setOnClickListener
            }
            if (startKey == curKey) return@setOnClickListener
            val oldIndex = enabled.indexOf(startKey)
            startKey = curKey
            if (oldIndex >= 0) notifyItemChanged(oldIndex + enabledStart())
            notifyItemChanged(cur)
            listener?.onStartChanged(curKey)
        }
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
        holder.check.visibility = View.GONE
        holder.itemView.setOnLongClickListener(null)
        holder.itemView.setOnClickListener {
            disabled.remove(key)
            enabled.add(key)
            notifyDataSetChanged()
            listener?.onChanged(enabled)
        }
    }

    /**
     * 拖拽离手时校正启动页：选中项被拖入未启用区后自动改选默认启动页并回调写回。
     *
     * <p>调用时机见 [dragCallback] 的 `clearView`：那里必须 post 一次再调，
     * 否则 `notifyItemChanged` 会落在 RecyclerView 的 layout/scroll 里。
     */
    private fun ensureStartValid() {
        if (startKey in enabled) return
        val resolved = MenuConfig.resolveStart(enabled, startKey)
        startKey = resolved
        val index = enabled.indexOf(resolved)
        if (index >= 0) notifyItemChanged(index + enabledStart())
        listener?.onStartChanged(resolved)
    }

    val dragCallback: ItemTouchHelper.Callback = object : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled() = false

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val pos = viewHolder.bindingAdapterPosition
            if (pos < 0 || getItemViewType(pos) != TYPE_ITEM_ENABLED) return 0
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0) return false
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

        override fun clearView(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ) {
            super.clearView(recyclerView, viewHolder)
            // 这里的 notify 必须延后一拍：ItemTouchHelper 是在 RecyclerView 的
            // scrollBy/layout 过程中回调 clearView 的，当场 notifyItemChanged 会撞上
            // "Cannot call this method while RecyclerView is computing a layout or scrolling"
            // （线上实测 6 例，全部落在本方法）。post 到下一帧既避开 layout pass，
            // 也不会打断 ItemTouchHelper 的手势——手势此时已经结束了。
            recyclerView.post { ensureStartValid() }
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }

    class HelpHolder(view: View) : RecyclerView.ViewHolder(view) {
        val button: MaterialButton = view.findViewById(R.id.help_button)
    }

    class RestoreHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.help_restore_text)
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.section_title)
    }

    class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.item_name)
        val fixed: TextView = view.findViewById(R.id.item_fixed)
        val check: MaterialCheckBox = view.findViewById(R.id.item_check)
    }
}
