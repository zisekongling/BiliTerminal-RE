package com.RobinNotBad.BiliClient.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.Announcement
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.google.android.material.card.MaterialCardView
import java.util.ArrayList

class AnnouncementAdapter(
    val context: Context,
    val list: ArrayList<Announcement>
) : RecyclerView.Adapter<AnnouncementAdapter.Holder>() {

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(this.context).inflate(R.layout.cell_announcement_list, parent, false)
        return Holder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(@NonNull holder: Holder, position: Int) {
        if (position < 0 || position >= list.size)
            return
        val announcement = list[position] ?: return

        holder.name.text = announcement.title
        val extra_start = if (announcement.content != null) announcement.content!!.indexOf("<extra_insert>") else -1
        holder.content.text =
            if (extra_start == -1) announcement.content else announcement.content!!.substring(0, extra_start) + "[附加内容]"
        holder.info.text = "ID:" + announcement.id + " | " + announcement.ctime

        holder.cardView.setOnClickListener { MsgUtil.showText(announcement.title, announcement.content) }
    }

    override fun getItemCount(): Int {
        return if (list != null) list.size else 0
    }

    class Holder(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.name)
        val content: TextView = itemView.findViewById(R.id.content)
        val info: TextView = itemView.findViewById(R.id.info)
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
    }
}