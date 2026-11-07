package com.RobinNotBad.BiliClient.adapter.user

import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.ui.appearance.GlassStyle
import com.RobinNotBad.BiliClient.util.UiText

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.model.FollowTag
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions

class FollowGroupAdapter(
    val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_GROUP = 0
        private const val TYPE_USER = 1
    }

    val groupList: MutableList<GroupItem> = ArrayList()
    val expandedMap: MutableMap<Int, Boolean> = HashMap()
    private var expandListener: OnGroupExpandListener? = null
    private var groupLongClickListener: ((FollowTag) -> Unit)? = null
    private var userLongClickListener: ((FollowTag, UserInfo) -> Unit)? = null

    fun interface OnGroupExpandListener {
        fun onGroupExpand(tagid: Int)
    }

    fun setOnGroupExpandListener(listener: OnGroupExpandListener?) {
        this.expandListener = listener
    }

    /** 长按分组标题（分组增删改的入口，26.10.04 批次 7 的 C21） */
    fun setOnGroupLongClickListener(listener: ((FollowTag) -> Unit)?) {
        this.groupLongClickListener = listener
    }

    /**
     * 长按分组里的成员（把成员移到其它分组的入口，26.10.04 批次 7 的 C22）。
     *
     * 回调带上成员所在的 [FollowTag]，因为移动接口必须知道**原分组 id**
     * （`beforeTagids`），而成员项自身不记得自己在哪个分组里。
     */
    fun setOnUserLongClickListener(listener: ((FollowTag, UserInfo) -> Unit)?) {
        this.userLongClickListener = listener
    }

    fun addGroup(tag: FollowTag, users: MutableList<UserInfo>) {
        groupList.add(GroupItem(tag, users))
        expandedMap[tag.tagid] = false
    }

    fun updateGroupUsers(tagid: Int, users: List<UserInfo>) {
        for (group in groupList) {
            if (group.tag.tagid == tagid) {
                group.users.clear()
                group.users.addAll(users)
                notifyDataSetChanged()
                break
            }
        }
    }

    fun addGroupUsers(tagid: Int, users: List<UserInfo>) {
        for (group in groupList) {
            if (group.tag.tagid == tagid) {
                group.users.addAll(users)
                notifyDataSetChanged()
                break
            }
        }
    }

    fun toggleGroup(tagid: Int) {
        val expanded = expandedMap[tagid] ?: return
        val newExpanded = !expanded
        expandedMap[tagid] = newExpanded

        if (newExpanded) {
            for (group in groupList) {
                if (group.tag.tagid == tagid && group.users.isEmpty() && expandListener != null) {
                    expandListener!!.onGroupExpand(tagid)
                    break
                }
            }
        }

        var groupPosition = -1
        var currentPos = 0
        for (group in groupList) {
            if (group.tag.tagid == tagid) {
                groupPosition = currentPos
                break
            }
            currentPos++
            val isExpanded = expandedMap[group.tag.tagid]
            if (isExpanded != null && isExpanded) {
                currentPos += group.users.size
            }
        }

        if (groupPosition >= 0) {
            notifyItemChanged(groupPosition)
            if (newExpanded) {
                var group: GroupItem? = null
                for (g in groupList) {
                    if (g.tag.tagid == tagid) {
                        group = g
                        break
                    }
                }
                if (group != null && group.users.isNotEmpty()) {
                    notifyItemRangeInserted(groupPosition + 1, group.users.size)
                }
            } else {
                var group: GroupItem? = null
                for (g in groupList) {
                    if (g.tag.tagid == tagid) {
                        group = g
                        break
                    }
                }
                if (group != null && group.users.isNotEmpty()) {
                    notifyItemRangeRemoved(groupPosition + 1, group.users.size)
                }
            }
        } else {
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        var currentPos = 0

        for (group in groupList) {
            if (currentPos == position) {
                return TYPE_GROUP
            }
            currentPos++

            val expanded = expandedMap[group.tag.tagid]
            if (expanded != null && expanded) {
                val userCount = group.users.size
                if (position >= currentPos && position < currentPos + userCount) {
                    return TYPE_USER
                }
                currentPos += userCount
            }
        }

        return TYPE_GROUP
    }

    private fun getGroupForPosition(position: Int): GroupItem? {
        var currentPos = 0
        for (group in groupList) {
            if (currentPos == position) {
                return group
            }
            currentPos++

            val expanded = expandedMap[group.tag.tagid]
            if (expanded != null && expanded) {
                val userCount = group.users.size
                if (position >= currentPos && position < currentPos + userCount) {
                    return group
                }
                currentPos += userCount
            }
        }
        if (groupList.isNotEmpty()) {
            return groupList[groupList.size - 1]
        }
        return null
    }

    private fun getUserForPosition(position: Int): UserInfo? {
        var currentPos = 0
        for (group in groupList) {
            currentPos++

            val expanded = expandedMap[group.tag.tagid]
            if (expanded != null && expanded) {
                val userCount = group.users.size
                if (position >= currentPos && position < currentPos + userCount) {
                    val userIndex = position - currentPos
                    return group.users[userIndex]
                }
                currentPos += userCount
            }
        }
        return null
    }

    /**
     * 成员项 [position] 属于哪个分组。
     *
     * 与 [getGroupForPosition] 的区别：后者对**成员位置**也会返回该组（用于取分组头本身），
     * 而这里只在位置确实落在某个展开组的成员区间时才返回。移动成员需要的就是后者——
     * 必须知道原分组 id（`beforeTagids`）。
     */
    private fun getUserOwnerGroup(position: Int): GroupItem? {
        var currentPos = 0
        for (group in groupList) {
            currentPos++

            val expanded = expandedMap[group.tag.tagid]
            if (expanded != null && expanded) {
                val userCount = group.users.size
                if (position >= currentPos && position < currentPos + userCount) {
                    return group
                }
                currentPos += userCount
            }
        }
        return null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_GROUP) {
            val view = LayoutInflater.from(context).inflate(R.layout.cell_follow_group, parent, false)
            return GroupHolder(view)
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.cell_user_list, parent, false)
            // 与 UserListAdapter 同一个 cell：写死的 bg_card_rounded 不读 materialCardViewStyle，
            // 玻璃质感下要自己换成玻璃面板（关闭时不动）。
            GlassStyle.applyCardBackground(view)
            return UserHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position < 0 || position >= itemCount)
            return

        if (holder is GroupHolder) {
            val group = getGroupForPosition(position)
            if (group != null) {
                holder.groupName.text = group.tag.name
                holder.groupCount.text = group.tag.count.toString() + UiText.of(" 位成员")

                val expanded = expandedMap[group.tag.tagid]
                val targetRotation = if (expanded != null && expanded) 90f else 0f
                animateRotation(holder.expandIcon, targetRotation)

                holder.itemView.setOnClickListener { toggleGroup(group.tag.tagid) }
                holder.itemView.setOnLongClickListener {
                    groupLongClickListener?.invoke(group.tag)
                    true
                }
            }
        } else if (holder is UserHolder) {
            val user = getUserForPosition(position)
            if (user != null) {
                holder.name.text = user.name
                if (user.vip_nickname_color != null && user.vip_nickname_color.isNotEmpty()) {
                    try {
                        holder.name.setTextColor(Color.parseColor(user.vip_nickname_color))
                    } catch (e: IllegalArgumentException) {
                        holder.name.setTextColor(ColorScheme.TEXT_PRIMARY)
                    }
                }
                holder.desc.text = user.sign
                // 分组列表主体就是「我自己关注的人」，互关徽章语义成立；每次 bind 重置，
                // 避免 Holder 复用后残留上一个成员的徽章（cell_user_list 的默认值也是 gone）
                holder.mutualBadge?.visibility = if (user.mutualFollow) View.VISIBLE else View.GONE
                // 分组明细接口的 special 恒为 0，分组模式实际不会亮；绑定仍然写全，防止 Holder 复用残留
                holder.specialBadge?.visibility = if (user.specialFollow) View.VISIBLE else View.GONE

                if (user.avatar == null || user.avatar.isEmpty()) {
                    holder.avatar.visibility = View.GONE
                    holder.desc.isSingleLine = false
                } else {
                    GlideUtil.with(holder.avatar).asDrawable().load(GlideUtil.url(user.avatar))
                        .transition(GlideUtil.getTransitionOptions())
                        .placeholder(R.mipmap.akari)
                        .error(R.mipmap.akari)
                        .apply(RequestOptions.circleCropTransform())
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(holder.avatar)
                    holder.avatar.visibility = View.VISIBLE
                    holder.desc.isSingleLine = true
                }

                if (user.mid != -1L) {
                    holder.itemView.setOnClickListener {
                        BiliTerminal.jumpToUser(context, user.mid)
                    }
                }

                // 长按成员 = 把 TA 移到别的分组（C22）。必须找回成员所在的分组，
                // 因为移动接口要传原分组 id（beforeTagids）。
                holder.itemView.setOnLongClickListener {
                    val owner = getUserOwnerGroup(position)
                    if (owner != null) {
                        userLongClickListener?.invoke(owner.tag, user)
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        var count = groupList.size
        for (group in groupList) {
            val expanded = expandedMap[group.tag.tagid]
            if (expanded != null && expanded) {
                count += group.users.size
            }
        }
        return count
    }

    class GroupHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var groupName: TextView
        lateinit var groupCount: TextView
        lateinit var expandIcon: ImageView

        init {
            groupName = itemView.findViewById(R.id.groupName)
            groupCount = itemView.findViewById(R.id.groupCount)
            expandIcon = itemView.findViewById(R.id.expandIcon)
        }
    }

    class UserHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var name: TextView
        lateinit var desc: TextView
        lateinit var avatar: ImageView

        /** 互关徽章；只有 cell_user_list 有该控件，换布局时为 null。 */
        val mutualBadge: TextView? = itemView.findViewById(R.id.mutualBadge)

        /** 特别关注徽章；只有 cell_user_list 有该控件，换布局时为 null。 */
        val specialBadge: TextView? = itemView.findViewById(R.id.specialBadge)

        init {
            // 金色跟着亮/暗模式走（亮色模式用更深的金），与 UserListAdapter 保持一致
            specialBadge?.setTextColor(ColorScheme.getGold(itemView.context))
            name = itemView.findViewById(R.id.userName)
            desc = itemView.findViewById(R.id.userDesc)
            avatar = itemView.findViewById(R.id.userAvatar)
        }
    }

    private fun animateRotation(imageView: ImageView, targetRotation: Float) {
        val animator = ObjectAnimator.ofFloat(imageView, "rotation", imageView.rotation, targetRotation)
        animator.duration = 200
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    class GroupItem(
        val tag: FollowTag,
        val users: MutableList<UserInfo>
    )
}