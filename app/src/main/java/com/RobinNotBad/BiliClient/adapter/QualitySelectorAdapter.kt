package com.RobinNotBad.BiliClient.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.google.android.material.button.MaterialButton

class QualitySelectorAdapter : RecyclerView.Adapter<QualitySelectorAdapter.QualityHolder>() {
    private var qualityNames: Array<String>? = null
    private var qualityValues: IntArray? = null
    private var context: Context? = null
    var listener: OnItemClickListener? = null
    var selectedItemIndex: Int = 0
        set(value) {
            val previous = field
            field = value
            notifyItemChanged(previous)
            notifyItemChanged(value)
        }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(qualityNames: Array<String>, qualityValues: IntArray, currentQuality: Int) {
        this.qualityNames = qualityNames
        this.qualityValues = qualityValues
        this.selectedItemIndex = -1

        for (i in qualityValues.indices) {
            if (qualityValues[i] == currentQuality) {
                this.selectedItemIndex = i
                break
            }
        }

        if (this.selectedItemIndex == -1 && qualityValues.isNotEmpty()) {
            this.selectedItemIndex = 0
        }

        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.listener = listener
    }

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): QualityHolder {
        this.context = parent.context
        val contextWrapper = ContextThemeWrapper(this.context, R.style.Theme_BiliClient)
        val view = LayoutInflater.from(contextWrapper)
                .inflate(R.layout.cell_episode, parent, false)
        return QualityHolder(view)
    }

    override fun onBindViewHolder(@NonNull holder: QualityHolder, position: Int) {
        if (position < 0 || qualityNames == null || position >= qualityNames!!.size)
            return
        if (listener != null) {
            holder.listener = listener
        }
        holder.bind(position, selectedItemIndex == position)
    }

    override fun getItemCount(): Int {
        return if (qualityNames != null) qualityNames!!.size else 0
    }

    inner class QualityHolder(view: View) : RecyclerView.ViewHolder(view) {
        var listener: OnItemClickListener? = null
        private val button: MaterialButton = itemView.findViewById(R.id.btn)

        fun bind(currentIndex: Int, isSelected: Boolean) {
            if (currentIndex < 0 || qualityNames == null || currentIndex >= qualityNames!!.size)
                return
            button.text = qualityNames!![currentIndex]
            if (isSelected) {
                button.setTextColor(ThemeManager.ON_PRIMARY)
                button.setBackgroundColor(ThemeManager.PRIMARY)
            } else {
                button.setTextColor(ThemeManager.TEXT_PRIMARY)
                button.setBackgroundColor(ThemeManager.CARD)
            }
            button.setOnClickListener {
                selectedItemIndex = currentIndex
                if (listener != null) {
                    listener!!.onItemClick(currentIndex)
                }
            }
        }
    }
}