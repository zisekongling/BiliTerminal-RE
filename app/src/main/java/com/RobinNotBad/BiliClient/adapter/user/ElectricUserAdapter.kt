package com.RobinNotBad.BiliClient.adapter.user

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.model.ElectricUser
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions

class ElectricUserAdapter(
    private val context: Context,
    private val userList: List<ElectricUser>
) : RecyclerView.Adapter<ElectricUserAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_electric_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = userList[position]

        holder.userName.text = user.uname

        Glide.with(context)
            .asDrawable()
            .load(GlideUtil.url(user.avatar))
            .transition(GlideUtil.getTransitionOptions())
            .placeholder(R.mipmap.akari)
            .apply(RequestOptions.circleCropTransform())
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(holder.userAvatar)

        if (user.message != null && user.message.isNotEmpty()) {
            holder.userMessage.visibility = View.VISIBLE
            holder.userMessage.text = user.message
        } else {
            holder.userMessage.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, UserInfoActivity::class.java)
            intent.putExtra("mid", user.pay_mid)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return userList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var userAvatar: ImageView
        lateinit var userName: TextView
        lateinit var userMessage: TextView

        init {
            userAvatar = itemView.findViewById(R.id.userAvatar)
            userName = itemView.findViewById(R.id.userName)
            userMessage = itemView.findViewById(R.id.userMessage)
        }
    }
}