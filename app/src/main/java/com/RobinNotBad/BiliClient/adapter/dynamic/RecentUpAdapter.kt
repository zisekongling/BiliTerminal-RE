package com.RobinNotBad.BiliClient.adapter.dynamic

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.util.GlideUtil

class RecentUpAdapter(
    private val context: Context,
    var upList: List<DynamicApi.UpInfo>
) : RecyclerView.Adapter<RecentUpAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_up_avatar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val upInfo = upList[position]

        GlideUtil.requestRound(holder.avatar, upInfo.face, R.mipmap.akari)
        holder.name.text = upInfo.uname

        if (upInfo.has_update) {
            holder.updateIndicator.visibility = View.VISIBLE
        } else {
            holder.updateIndicator.visibility = View.GONE
        }

        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.setOnClickListener {
            BiliTerminal.jumpToUser(context, upInfo.mid)
        }
        holder.avatar.setOnClickListener {
            BiliTerminal.jumpToUser(context, upInfo.mid)
        }
    }

    override fun getItemCount(): Int {
        return upList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var avatar: ImageView
        lateinit var name: TextView
        lateinit var updateIndicator: View

        init {
            avatar = itemView.findViewById(R.id.avatar)
            name = itemView.findViewById(R.id.name)
            updateIndicator = itemView.findViewById(R.id.updateIndicator)
        }
    }
}