package com.RobinNotBad.BiliClient.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.model.ViewPoint
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.util.StringUtil
import com.google.android.material.button.MaterialButton

class ViewPointAdapter : RecyclerView.Adapter<ViewPointAdapter.ViewPointHolder>() {
    private var viewPoints: List<ViewPoint>? = null
    private var context: Context? = null
    var listener: OnItemClickListener? = null
    private var currentPosition: Int = -1
    private var lastCurrentIndex: Int = -1

    @SuppressLint("NotifyDataSetChanged")
    fun setData(viewPoints: List<ViewPoint>) {
        this.viewPoints = viewPoints
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.listener = listener
    }

    fun updateCurrentPosition(positionInSeconds: Int) {
        if (this.currentPosition == positionInSeconds) {
            return
        }

        this.currentPosition = positionInSeconds

        val newCurrentIndex = getCurrentSegmentIndex()
        if (newCurrentIndex != lastCurrentIndex) {
            val oldIndex = lastCurrentIndex
            lastCurrentIndex = newCurrentIndex

            if (oldIndex != -1 && oldIndex < itemCount) {
                notifyItemChanged(oldIndex)
            }
            if (newCurrentIndex != -1 && newCurrentIndex < itemCount) {
                notifyItemChanged(newCurrentIndex)
            }
        }
    }

    private fun getCurrentSegmentIndex(): Int {
        if (viewPoints == null || currentPosition < 0) {
            return -1
        }
        for (i in viewPoints!!.indices) {
            val vp = viewPoints!![i]
            if (currentPosition >= vp.from && currentPosition < vp.to) {
                return i
            }
        }
        return -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewPointHolder {
        this.context = parent.context
        val view = LayoutInflater.from(this.context)
            .inflate(R.layout.cell_episode, parent, false)
        return ViewPointHolder(view)
    }

    override fun onBindViewHolder(holder: ViewPointHolder, position: Int) {
        if (position < 0 || viewPoints == null || position >= viewPoints!!.size)
            return
        if (listener != null) {
            holder.listener = listener
        }
        holder.bind(position)
    }

    override fun getItemCount(): Int {
        return if (viewPoints != null) viewPoints!!.size else 0
    }

    inner class ViewPointHolder(view: View) : RecyclerView.ViewHolder(view) {
        var listener: OnItemClickListener? = null
        private val button: MaterialButton = itemView.findViewById(R.id.btn)

        fun bind(currentIndex: Int) {
            if (currentIndex < 0 || viewPoints == null || currentIndex >= viewPoints!!.size)
                return
            val viewPoint = viewPoints!![currentIndex]
            val timeStr = StringUtil.toTime(viewPoint.from) + "-" + StringUtil.toTime(viewPoint.to)
            val displayText = viewPoint.content + "\n" + timeStr
            button.text = displayText

            val isCurrent = currentPosition >= viewPoint.from && currentPosition < viewPoint.to

            if (isCurrent) {
                button.setTextColor(ThemeManager.getOnPrimary(context!!))
                button.setBackgroundColor(ThemeManager.getPrimary(context!!))
            } else {
                button.setTextColor(ThemeManager.getTextPrimary(context!!))
                button.setBackgroundColor(ThemeManager.getCard(context!!))
            }

            button.setOnClickListener {
                listener?.onItemClick(currentIndex)
            }
        }
    }
}