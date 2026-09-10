package com.RobinNotBad.BiliClient.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ListChooseActivity
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.Serializable

class SettingsAdapter(
    val context: Context,
    val list: List<SettingSection>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var listChooseLauncher: ActivityResultLauncher<Intent>? = null
    var onSettingChanged: ((String, Boolean) -> Unit)? = null

    fun setListChooseLauncher(launcher: ActivityResultLauncher<Intent>) {
        this.listChooseLauncher = launcher
    }

    // ---- 自定义单元格附加数据（通过 SettingSection.extra 传递）----
    class NavExtra(
        val iconRes: Int,
        val onClick: (View) -> Unit,
        val onLongClick: ((View) -> Boolean)? = null
    )

    class ButtonExtra(val onClick: (View) -> Unit)

    class SwitchExtra(val onChange: (Boolean) -> Unit)

    class InputExtra(val save: (String) -> Unit)

    override fun getItemViewType(position: Int): Int {
        if (list.isEmpty() || position < 0 || position >= list.size) {
            return 0
        }
        val section = list[position]
        val type = Companion.typeMap[section.type]
        return type ?: 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            TYPE_CHOOSE -> return ChooseHolder(
                LayoutInflater.from(this.context).inflate(R.layout.cell_setting_choose, parent, false)
            )
            TYPE_INPUT_INT, TYPE_INPUT_FLOAT, TYPE_INPUT_STRING -> return InputHolder(
                LayoutInflater.from(this.context).inflate(R.layout.cell_setting_input, parent, false)
            )
            TYPE_LIST_CHOOSE -> return ListChooseHolder(
                LayoutInflater.from(this.context).inflate(R.layout.cell_setting_list_choose, parent, false)
            )
            TYPE_NAV -> return NavHolder(
                LayoutInflater.from(this.context).inflate(R.layout.cell_setting_nav, parent, false)
            )
            TYPE_BUTTON -> {
                val button = MaterialButton(this.context)
                val lp = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                val density = button.resources.displayMetrics.density
                lp.setMargins((6 * density).toInt(), (3 * density).toInt(), (6 * density).toInt(), (3 * density).toInt())
                button.layoutParams = lp
                return ButtonHolder(button)
            }
            TYPE_DIVIDER -> return DividerHolder(
                LayoutInflater.from(this.context).inflate(R.layout.cell_divider, parent, false)
            )
            TYPE_TITLE -> return TitleHolder(
                LayoutInflater.from(this.context).inflate(R.layout.cell_setting_title, parent, false)
            )
            else -> return SwitchHolder(
                LayoutInflater.from(this.context).inflate(R.layout.cell_setting_switch, parent, false)
            )
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position < 0 || position >= list.size)
            return
        val settingSection = list[position]

        // 按 Holder 实际类型分派，不做盲转。
        // 之前是 `when (holder.itemViewType) { ... else -> holder as SwitchHolder }`：
        // 一旦 viewType 与 Holder 不同步（例如列表被 ConcatAdapter 包装后 viewType 被重映射，
        // 而 SettingsAdapter 的 viewType 是负数 -1/-2），就会抛
        // ClassCastException: NavHolder cannot be cast to SwitchHolder 并让设置页报错。
        when (holder) {
            is TitleHolder -> holder.bind(settingSection)
            is DividerHolder -> {}
            is ChooseHolder -> holder.bind(settingSection)
            is InputHolder -> holder.bind(settingSection)
            is ListChooseHolder -> holder.bind(settingSection, position)
            is NavHolder -> holder.bind(settingSection)
            is ButtonHolder -> holder.bind(settingSection)
            is SwitchHolder -> {
                holder.adapter = this
                holder.bind(settingSection)
            }
            else -> {}
        }
    }

    override fun getItemCount(): Int {
        return if (list != null) list.size else 0
    }

    companion object {
        // viewType 全部取非负值：负数 viewType（原为 -1 分割线 / -2 标题）一旦被
        // ConcatAdapter 之类的包装 adapter 重映射就会错配 Holder，见 onBindViewHolder 注释
        const val TYPE_SWITCH = 0
        const val TYPE_CHOOSE = 1
        const val TYPE_INPUT_INT = 2
        const val TYPE_INPUT_FLOAT = 3
        const val TYPE_INPUT_STRING = 4
        const val TYPE_LIST_CHOOSE = 5
        const val TYPE_NAV = 6
        const val TYPE_BUTTON = 7
        const val TYPE_DIVIDER = 8
        const val TYPE_TITLE = 9

        val typeMap: Map<String, Int> = mapOf(
            "switch" to TYPE_SWITCH,
            "choose" to TYPE_CHOOSE,
            "input_int" to TYPE_INPUT_INT,
            "input_float" to TYPE_INPUT_FLOAT,
            "input_string" to TYPE_INPUT_STRING,
            "list_choose" to TYPE_LIST_CHOOSE,
            "nav" to TYPE_NAV,
            "button" to TYPE_BUTTON,
            "divider" to TYPE_DIVIDER,
            "title" to TYPE_TITLE
        )
    }

    class NavHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.nav_icon)
        val title: TextView = itemView.findViewById(R.id.nav_title)
        val desc: TextView = itemView.findViewById(R.id.nav_desc)

        fun bind(settingSection: SettingSection) {
            val extra = settingSection.extra as? NavExtra ?: return
            icon.setImageResource(extra.iconRes)
            title.text = settingSection.name
            desc.text = settingSection.desc
            itemView.setOnClickListener { extra.onClick(it) }
            itemView.setOnLongClickListener { extra.onLongClick?.invoke(it) ?: false }
        }
    }

    class ButtonHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: MaterialButton = itemView as MaterialButton

        fun bind(settingSection: SettingSection) {
            button.text = settingSection.name
            button.setOnClickListener { (settingSection.extra as? ButtonExtra)?.onClick?.invoke(it) }
        }
    }

    class SwitchHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val desc: TextView = itemView.findViewById(R.id.setting_switch_desc)
        val switchMaterial: SwitchMaterial = itemView.findViewById(R.id.setting_switch)
        private var settingSection: SettingSection? = null
        var adapter: SettingsAdapter? = null

        fun bind(settingSection: SettingSection) {
            this.settingSection = settingSection
            if (settingSection.desc.isNullOrEmpty())
                desc.visibility = View.GONE
            else {
                desc.text = settingSection.desc
                desc.visibility = View.VISIBLE
            }
            switchMaterial.text = settingSection.name
            switchMaterial.setOnCheckedChangeListener(null)
            switchMaterial.isChecked = SharedPreferencesUtil.getBoolean(
                settingSection.id,
                settingSection.defaultValue.toBoolean()
            )
            switchMaterial.setOnCheckedChangeListener { _, isChecked ->
                SharedPreferencesUtil.putBoolean(settingSection.id, isChecked)
                if (isChecked && settingSection.oppositeKey != null) {
                    SharedPreferencesUtil.putBoolean(settingSection.oppositeKey, false)
                }
                adapter?.onSettingChanged?.invoke(settingSection.id, isChecked)
                (settingSection.extra as? SwitchExtra)?.onChange?.invoke(isChecked)
            }
        }
    }

    class ChooseHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chocola: RadioButton = itemView.findViewById(R.id.setting_choose_chocola)
        val vanilla: RadioButton = itemView.findViewById(R.id.setting_choose_vanilla)
        val name: TextView = itemView.findViewById(R.id.setting_choose_name)
        val desc: TextView = itemView.findViewById(R.id.setting_choose_desc)

        fun bind(settingSection: SettingSection) {
            if (settingSection.desc.isNullOrEmpty())
                desc.visibility = View.GONE
            else {
                desc.text = settingSection.desc
                desc.visibility = View.VISIBLE
            }
            name.text = settingSection.name
            val strings = settingSection.extra as Array<String>
            chocola.text = strings[0]
            vanilla.text = strings[1]

            val value = SharedPreferencesUtil.getBoolean(
                settingSection.id,
                settingSection.defaultValue.toBoolean()
            )
            chocola.isChecked = value
            vanilla.isChecked = !value

            chocola.setOnCheckedChangeListener { _, isChecked ->
                SharedPreferencesUtil.putBoolean(settingSection.id, isChecked)
            }
        }
    }

    class InputHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val input: EditText = itemView.findViewById(R.id.setting_input_edittext)
        val name: TextView = itemView.findViewById(R.id.setting_input_name)
        val desc: TextView = itemView.findViewById(R.id.setting_input_desc)

        fun bind(settingSection: SettingSection) {
            if (settingSection.desc.isNullOrEmpty())
                desc.visibility = View.GONE
            else {
                desc.text = settingSection.desc
                desc.visibility = View.VISIBLE
            }
            name.text = settingSection.name
            when (settingSection.type) {
                "input_int" -> {
                    val intValue = SharedPreferencesUtil.getInt(
                        settingSection.id,
                        settingSection.defaultValue.toInt()
                    )
                    input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                    input.setText(intValue.toString())
                    input.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                        override fun afterTextChanged(editable: Editable) {
                            val custom = settingSection.extra as? InputExtra
                            if (custom != null) {
                                custom.save(editable.toString())
                            } else {
                                try {
                                    SharedPreferencesUtil.putInt(settingSection.id, editable.toString().toInt())
                                } catch (ignored: Exception) {
                                }
                            }
                        }
                    })
                }
                "input_float" -> {
                    val floatValue = SharedPreferencesUtil.getFloat(
                        settingSection.id,
                        settingSection.defaultValue.toFloat()
                    )
                    input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    input.setText(floatValue.toString())
                    input.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                        override fun afterTextChanged(editable: Editable) {
                            val custom = settingSection.extra as? InputExtra
                            if (custom != null) {
                                custom.save(editable.toString())
                            } else {
                                try {
                                    SharedPreferencesUtil.putFloat(
                                        settingSection.id,
                                        editable.toString().toFloat()
                                    )
                                } catch (ignored: Exception) {
                                }
                            }
                        }
                    })
                }
                else -> {
                    val strValue = SharedPreferencesUtil.getString(settingSection.id, settingSection.defaultValue)
                    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    input.setText(strValue)
                    input.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
                        override fun afterTextChanged(editable: Editable) {
                            val custom = settingSection.extra as? InputExtra
                            if (custom != null) {
                                custom.save(editable.toString())
                            } else {
                                SharedPreferencesUtil.putString(settingSection.id, editable.toString())
                            }
                        }
                    })
                }
            }
        }
    }

    class DividerHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView)

    class TitleHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.setting_title_text)

        fun bind(settingSection: SettingSection) {
            titleText.text = settingSection.name
        }
    }

    class ListChooseHolder(@androidx.annotation.NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.setting_list_choose_name)
        val value: TextView = itemView.findViewById(R.id.setting_list_choose_value)
        val desc: TextView = itemView.findViewById(R.id.setting_list_choose_desc)

        class ListChooseExtra(
            var displayNames: List<String>,
            var actualValues: List<String>,
            var onSelect: ((String, String) -> Unit)? = null
        ) : Serializable {
            companion object {
                private const val serialVersionUID = 1L
            }
        }

        @SuppressLint("SetTextI18n")
        fun bind(settingSection: SettingSection, position: Int) {
            if (settingSection.desc.isNullOrEmpty())
                desc.visibility = View.GONE
            else {
                desc.text = settingSection.desc
                desc.visibility = View.VISIBLE
            }
            name.text = settingSection.name

            val extra = settingSection.extra as ListChooseExtra
            if (extra.displayNames != null && extra.actualValues != null) {
                val currentValue = SharedPreferencesUtil.getString(settingSection.id, settingSection.defaultValue)
                val currentIndex = extra.actualValues.indexOf(currentValue)
                if (currentIndex >= 0 && currentIndex < extra.displayNames.size) {
                    value.text = extra.displayNames[currentIndex]
                } else {
                    value.text = "点击选择"
                }

                itemView.setOnClickListener {
                    if (it.context is ComponentActivity) {
                        val activity = it.context as ComponentActivity
                        val intent = Intent(activity, ListChooseActivity::class.java)
                        intent.putExtra("title", settingSection.name)
                        intent.putExtra("items", ArrayList(extra.displayNames))
                        intent.putExtra("values", ArrayList(extra.actualValues))
                        intent.putExtra("position", position)
                        activity.startActivityForResult(intent, 1001)
                    }
                }
            }
        }
    }
}
