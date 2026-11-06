package com.RobinNotBad.BiliClient.adapter.user

import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.ui.appearance.GlassStyle
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
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
            .error(R.mipmap.akari)
            .override(128) // 头像只需要小尺寸，限制解码大小减少内存占用
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_user_list, parent, false)
        // 玻璃质感：本 cell 是单层 FrameLayout + 写死的 bg_card_rounded（不透明 shape），
        // 不读 materialCardViewStyle，主题覆盖层够不着 → 建 holder 时换成玻璃面板（关闭时不动）。
        GlassStyle.applyCardBackground(view)
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

        // 互关徽章：数据层（FollowApi）已经把「是不是自己的列表」折进 mutualFollow，
        // 这里只负责显隐；Holder 复用必须每次重置，否则上一条的徽章会残留到非互关项。
        // cell_up_list 没有这个控件（UpListAdapter 复用本 Holder），所以用可空引用。
        holder.badge?.visibility = if (user.mutualFollow) View.VISIBLE else View.GONE

        if (user.avatar.isNullOrEmpty()) {
            holder.avatar.visibility = View.GONE
            holder.desc.isSingleLine = false
        } else {
            // 使用 holder.itemView 作为 Glide 的 lifecycle 绑定，确保 View 回收时自动取消请求。
            // 但 Glide.with(context) 仍会在宿主 Activity 销毁时抛
            // "You cannot start a load for a destroyed activity"（线上实测 2 例：列表回到前台
            // 时网络数据才回来，onBind 撞上正在 finish 的页面），所以先过 canLoad。
            if (GlideUtil.canLoad(holder.itemView)) {
                Glide.with(holder.itemView.context)
                    .asDrawable()
                    .load(GlideUtil.url(user.avatar))
                    .skipMemoryCache(false)
                    .apply(AVATAR_OPTIONS)
                    .into(holder.avatar)
            }
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

        /** 互关徽章；只有 cell_user_list 有这个控件，子类换布局（cell_up_list）时为 null。 */
        val badge: TextView? = itemView.findViewById(R.id.mutualBadge)

        // 缓存上次解析的颜色值和原始字符串，避免重复调用 Color.parseColor
        private var cachedColorString: String? = null
        private var cachedColor: Int = ColorScheme.TEXT_PRIMARY

        fun getCachedColor(colorString: String?): Int {
            // 非大会员昵称走主题正文色（原写死纯白，在暖白 / 冷白主题里都显得突兀）
            if (colorString.isNullOrEmpty()) {
                return ColorScheme.TEXT_PRIMARY
            }
            if (colorString == cachedColorString) {
                return cachedColor
            }
            cachedColorString = colorString
            cachedColor = try {
                Color.parseColor(colorString)
            } catch (e: IllegalArgumentException) {
                ColorScheme.TEXT_PRIMARY
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
                    BiliTerminal.jumpToUser(context, user.mid)
                }
            }
        }
    }
}