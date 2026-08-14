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
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions

open class UserListAdapter(
    val context: Context,
    val userList: List<UserInfo>
) : RecyclerView.Adapter<UserListAdapter.Holder>() {

    companion object {
        // 复用静态 RequestOptions，避免每次 bind 都创建新对象
        private val AVATAR_OPTIONS: RequestOptions = RequestOptions()
            .circleCrop()
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .placeholder(R.mipmap.akari)
            .override(128) // 头像只需要小尺寸，限制解码大小减少内存占用
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_user_list, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position < 0 || position >= userList.size)
            return
        val user = userList[position] ?: return

        holder.name.text = user.name
        // 缓存解析后的颜色值，避免重复 parseColor
        holder.name.setTextColor(holder.getCachedColor(user.vip_nickname_color))

        holder.desc.text = user.sign

        if (user.avatar.isNullOrEmpty()) {
            holder.avatar.visibility = View.GONE
            holder.desc.isSingleLine = false
        } else {
            // 使用 holder.itemView 作为 Glide 的 lifecycle 绑定，确保 View 回收时自动取消请求
            Glide.with(holder.itemView.context)
                .asDrawable()
                .load(GlideUtil.url(user.avatar))
                .skipMemoryCache(false)
                .apply(AVATAR_OPTIONS)
                .into(holder.avatar)
            holder.avatar.visibility = View.VISIBLE
            holder.desc.isSingleLine = true
        }
    }

    override fun getItemCount(): Int {
        return userList.size
    }

    open inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.userName)
        val desc: TextView = itemView.findViewById(R.id.userDesc)
        val avatar: ImageView = itemView.findViewById(R.id.userAvatar)

        // 缓存上次解析的颜色值和原始字符串，避免重复调用 Color.parseColor
        private var cachedColorString: String? = null
        private var cachedColor: Int = Color.WHITE

        fun getCachedColor(colorString: String?): Int {
            if (colorString.isNullOrEmpty()) {
                return Color.WHITE
            }
            if (colorString == cachedColorString) {
                return cachedColor
            }
            cachedColorString = colorString
            cachedColor = try {
                Color.parseColor(colorString)
            } catch (e: IllegalArgumentException) {
                Color.WHITE
            }
            return cachedColor
        }

        init {
            // 点击事件只在创建时设置一次，避免 onBindViewHolder 中重复创建 lambda
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                val user = userList.getOrNull(position) ?: return@setOnClickListener
                if (user.mid != -1L) {
                    val intent = Intent(context, UserInfoActivity::class.java)
                        .putExtra("mid", user.mid)
                    context.startActivity(intent)
                }
            }
        }
    }
}