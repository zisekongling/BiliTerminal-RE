package com.RobinNotBad.BiliClient.adapter.message

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.message.MessageSettingItem
import com.google.android.material.switchmaterial.SwitchMaterial

class MessageSettingsAdapter(
    private val context: Context,
    private val items: List<MessageSettingItem>,
    private val listener: OnSettingChangedListener?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun interface OnSettingChangedListener {
        fun onSettingChanged(key: String, value: Boolean)
    }

    override fun getItemViewType(position: Int): Int {
        return items[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == MessageSettingItem.TYPE_CHOOSE) {
            val view = LayoutInflater.from(context).inflate(R.layout.cell_setting_choose, parent, false)
            return ChooseHolder(view)
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.cell_setting_switch, parent, false)
            return SwitchHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is SwitchHolder) {
            holder.bind(item, listener)
        } else if (holder is ChooseHolder) {
            holder.bind(item, listener)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class SwitchHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var desc: TextView
        lateinit var switchMaterial: SwitchMaterial

        init {
            desc = itemView.findViewById(R.id.setting_switch_desc)
            switchMaterial = itemView.findViewById(R.id.setting_switch)
        }

        fun bind(item: MessageSettingItem, listener: OnSettingChangedListener?) {
            if (item.desc.isNullOrEmpty()) {
                desc.visibility = View.GONE
            } else {
                desc.text = item.desc
                desc.visibility = View.VISIBLE
            }

            switchMaterial.text = item.title
            switchMaterial.setOnCheckedChangeListener(null)
            switchMaterial.isChecked = item.value
            switchMaterial.setOnCheckedChangeListener { _, isChecked ->
                item.value = isChecked
                listener?.onSettingChanged(item.key, isChecked)
            }
        }
    }

    class ChooseHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var chocola: RadioButton
        lateinit var vanilla: RadioButton
        lateinit var name: TextView
        lateinit var desc: TextView

        init {
            chocola = itemView.findViewById(R.id.setting_choose_chocola)
            vanilla = itemView.findViewById(R.id.setting_choose_vanilla)
            desc = itemView.findViewById(R.id.setting_choose_desc)
            name = itemView.findViewById(R.id.setting_choose_name)
        }

        fun bind(item: MessageSettingItem, listener: OnSettingChangedListener?) {
            if (item.desc.isNullOrEmpty()) {
                desc.visibility = View.GONE
            } else {
                desc.text = item.desc
                desc.visibility = View.VISIBLE
            }

            name.text = item.title

            if (item.options != null && item.options.size >= 2) {
                chocola.text = item.options[0]
                vanilla.text = item.options[1]
            }

            chocola.setOnCheckedChangeListener(null)
            vanilla.setOnCheckedChangeListener(null)

            chocola.isChecked = item.value
            vanilla.isChecked = !item.value

            chocola.setOnCheckedChangeListener { _, isChecked ->
                item.value = isChecked
                listener?.onSettingChanged(item.key, isChecked)
            }
        }
    }
}