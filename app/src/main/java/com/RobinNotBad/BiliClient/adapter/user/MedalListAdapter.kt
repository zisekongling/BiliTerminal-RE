package com.RobinNotBad.BiliClient.adapter.user

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.model.MedalInfo
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions

class MedalListAdapter(
    val context: Context,
    val medalList: List<MedalInfo>
) : RecyclerView.Adapter<MedalListAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_medal_list, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position < 0 || position >= medalList.size)
            return
        val medal = medalList[position] ?: return

        holder.medalName.text = medal.medal_name
        holder.targetName.text = medal.target_name

        var levelText = "Lv." + medal.level
        if (medal.wearing_status == 1) {
            levelText += " (佩戴中)"
        }
        holder.level.text = levelText

        var intimacyText = "亲密度: " + medal.intimacy
        if (medal.next_intimacy > 0) {
            intimacyText += " / " + medal.next_intimacy
        }
        holder.intimacy.text = intimacyText

        if (medal.target_icon != null && medal.target_icon.isNotEmpty()) {
            Glide.with(BiliTerminal.context).asDrawable().load(GlideUtil.url(medal.target_icon))
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(R.mipmap.akari)
                .apply(RequestOptions.circleCropTransform())
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(holder.avatar)
            holder.avatar.visibility = View.VISIBLE
        } else {
            holder.avatar.visibility = View.GONE
        }

        if (medal.target_id > 0) {
            holder.itemView.setOnClickListener {
                val intent = Intent()
                    .setClass(context, UserInfoActivity::class.java)
                    .putExtra("mid", medal.target_id)
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int {
        return medalList.size
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var medalName: TextView
        lateinit var targetName: TextView
        lateinit var level: TextView
        lateinit var intimacy: TextView
        lateinit var avatar: ImageView

        init {
            medalName = itemView.findViewById(R.id.medalName)
            targetName = itemView.findViewById(R.id.targetName)
            level = itemView.findViewById(R.id.level)
            intimacy = itemView.findViewById(R.id.intimacy)
            avatar = itemView.findViewById(R.id.avatar)
        }
    }
}