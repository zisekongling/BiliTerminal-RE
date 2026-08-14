package com.RobinNotBad.BiliClient.adapter.video

import android.annotation.SuppressLint
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.model.Bangumi
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.google.android.material.button.MaterialButton

class MediaEpisodeAdapter() : RecyclerView.Adapter<MediaEpisodeAdapter.EpisodeHolder>() {

    constructor(useVerticalLayout: Boolean) : this() {
        this.useVerticalLayout = useVerticalLayout
    }

    private var episodeList: List<Bangumi.Episode>? = null
    private var context: Context? = null

    var listener: OnItemClickListener? = null
    var selectedItemIndex: Int = 0
        set(value) {
            val previous = field
            field = value
            notifyItemChanged(previous)
            notifyItemChanged(value)
        }
    private var useVerticalLayout: Boolean = false

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.listener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(episodeList: List<Bangumi.Episode>) {
        this.episodeList = episodeList
        selectedItemIndex = 0
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeHolder {
        this.context = parent.context
        val contextWrapper = ContextThemeWrapper(this.context, R.style.Theme_BiliClient)
        val view = LayoutInflater.from(contextWrapper)
            .inflate(if (useVerticalLayout) R.layout.cell_item_vertical else R.layout.cell_episode, parent, false)
        return EpisodeHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeHolder, position: Int) {
        if (position < 0 || episodeList == null || position >= episodeList!!.size)
            return
        if (listener != null) {
            holder.listener = listener
        }
        holder.bind(position, selectedItemIndex == position)
    }

    override fun getItemCount(): Int {
        return if (episodeList != null) episodeList!!.size else 0
    }

    inner class EpisodeHolder(view: View) : RecyclerView.ViewHolder(view) {

        var listener: OnItemClickListener? = null
        private val button: MaterialButton = itemView.findViewById(R.id.btn)

        fun bind(currentIndex: Int, isSelected: Boolean) {
            if (currentIndex < 0 || episodeList == null || currentIndex >= episodeList!!.size)
                return
            button.text = episodeList!![currentIndex].title
            if (isSelected) {
                button.setTextColor(ThemeManager.getOnPrimary(context!!))
                button.setBackgroundColor(ThemeManager.getPrimary(context!!))
            } else {
                button.setTextColor(ThemeManager.getTextPrimary(context!!))
                button.setBackgroundColor(ThemeManager.getCard(context!!))
            }
            button.setOnClickListener {
                selectedItemIndex = currentIndex
                listener?.onItemClick(currentIndex)
            }
        }
    }
}