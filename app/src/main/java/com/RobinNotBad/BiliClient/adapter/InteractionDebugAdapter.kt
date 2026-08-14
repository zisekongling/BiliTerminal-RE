package com.RobinNotBad.BiliClient.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.InteractionVideoData
class InteractionDebugAdapter(
    private val varList: MutableList<InteractionVideoData.InteractionHiddenVar>
) : RecyclerView.Adapter<InteractionDebugAdapter.VarHolder>() {

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): VarHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.cell_interaction_debug_var, parent, false)
        return VarHolder(view)
    }

    override fun onBindViewHolder(@NonNull holder: VarHolder, position: Int) {
        val `var` = varList[position]
        val varName = if (`var`.name != null && !`var`.name!!.isEmpty()) `var`.name else `var`.id
        holder.nameText.text = varName + " (" + `var`.id + ")"
        holder.valueEdit.setText(`var`.value.toString())

        holder.valueEdit.removeTextChangedListener(holder.textWatcher)
        holder.textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                try {
                    if (!s.toString().isEmpty()) {
                        val newValue = s.toString().toLong()
                        `var`.value = newValue
                    }
                } catch (ignored: NumberFormatException) {
                }
            }
        }
        holder.valueEdit.addTextChangedListener(holder.textWatcher)
    }

    override fun getItemCount(): Int {
        return if (varList != null) varList.size else 0
    }

    class VarHolder(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.debug_var_name)
        val valueEdit: EditText = itemView.findViewById(R.id.debug_var_value)
        var textWatcher: TextWatcher? = null
    }
}