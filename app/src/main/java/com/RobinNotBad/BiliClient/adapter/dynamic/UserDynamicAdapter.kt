package com.RobinNotBad.BiliClient.adapter.dynamic

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.message.PrivateMsgActivity
import com.RobinNotBad.BiliClient.activity.user.FollowUsersActivity
import com.RobinNotBad.BiliClient.activity.user.MedalWallActivity
import com.RobinNotBad.BiliClient.adapter.user.ElectricUserAdapter
import com.RobinNotBad.BiliClient.api.ApiErrorText
import com.RobinNotBad.BiliClient.api.ElectricApi
import com.RobinNotBad.BiliClient.api.FollowApi
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.model.Dynamic
import com.RobinNotBad.BiliClient.model.ElectricPanel
import com.RobinNotBad.BiliClient.model.FollowTag
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.ui.widget.RadiusBackgroundSpan
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import org.json.JSONObject
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme

class UserDynamicAdapter(
    private val context: Context,
    private val dynamicList: ArrayList<Dynamic>,
    private val userInfo: UserInfo
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == 0) {
            val view = LayoutInflater.from(context).inflate(R.layout.cell_user_info, parent, false)
            return UserInfoHolder(view)
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.cell_dynamic, parent, false)
            return DynamicHolder(view, context as BaseActivity, false)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is DynamicHolder) {
            val realPosition = position - 1
            if (realPosition < 0 || realPosition >= dynamicList.size)
                return

            val dynamic = dynamicList[realPosition] ?: return

            val dynamicHolder = holder
            dynamicHolder.showDynamic(context, dynamic, true)

            if (dynamic.dynamic_forward != null) {
                val childCard = dynamicHolder.cell_dynamic_child
                if (dynamicHolder.childDynamicHolder == null) {
                    dynamicHolder.childDynamicHolder =
                        DynamicHolder(childCard, context as BaseActivity, true)
                }
                dynamicHolder.childDynamicHolder!!.showDynamic(context, dynamic.dynamic_forward!!, true)
                dynamicHolder.cell_dynamic_child.visibility = View.VISIBLE
            } else {
                dynamicHolder.cell_dynamic_child.visibility = View.GONE
            }

            // 用 setManageAction 注入动作而非覆盖 setOnLongClickListener，
            // 否则「点击」那条路仍指向 DynamicHolder 内部，会出现两套行为。
            dynamicHolder.setManageAction(
                DynamicHolder.getManageAction(
                    context as BaseActivity,
                    dynamicList, realPosition, this
                )
            )
            if (dynamic.canDelete || dynamic.canEdit)
                dynamicHolder.item_dynamic_delete!!.visibility = View.VISIBLE
        }
        if (holder is UserInfoHolder) {
            (holder).bind(context, userInfo)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        // 复用前清掉「同 URL 跳过加载」的缓存，否则 recycled 的 holder 会残留上一个动态的头图/配图
        if (holder is DynamicHolder) {
            holder.clearImageCache()
        }
    }

    override fun getItemCount(): Int {
        return dynamicList.size + 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) 0 else 1
    }

    class UserInfoHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.userName)
        val userFollowings: TextView = itemView.findViewById(R.id.userFollowings)
        val userLevel: TextView = itemView.findViewById(R.id.userLevel)
        val userFans: TextView = itemView.findViewById(R.id.userFollowers)
        val userMedal: TextView = itemView.findViewById(R.id.userMedal)
        val userDesc: TextView = itemView.findViewById(R.id.userDesc)
        val userNotice: TextView = itemView.findViewById(R.id.userNotice)
        val userGenderIp: TextView = itemView.findViewById(R.id.userGenderIp)
        val userOfficial: TextView = itemView.findViewById(R.id.userOfficial)
        val exclusiveTipLabel: TextView = itemView.findViewById(R.id.exclusiveTipLabel)
        val liveRoomLabel: TextView = itemView.findViewById(R.id.liveRoomLabel)
        val electricPanelHeader: TextView = itemView.findViewById(R.id.electricPanelHeader)
        val exclusiveTip: MaterialCardView = itemView.findViewById(R.id.exclusiveTip)
        val liveRoom: MaterialCardView = itemView.findViewById(R.id.liveRoom)
        val electricPanel: MaterialCardView = itemView.findViewById(R.id.electricPanel)
        val userAvatar: ImageView = itemView.findViewById(R.id.userAvatar)
        val officialIcon: ImageView = itemView.findViewById(R.id.officialIcon)
        val uidTv: TextView = itemView.findViewById(R.id.uidText)
        val followBtn: MaterialButton = itemView.findViewById(R.id.followBtn)
        val msgBtn: MaterialButton = itemView.findViewById(R.id.msgBtn)
        val contractBtn: MaterialButton = itemView.findViewById(R.id.contractBtn)
        val electricUserList: RecyclerView = itemView.findViewById(R.id.electricUserList)
        val electricPanelDivider: View = itemView.findViewById(R.id.electricPanelDivider)
        val divider: View = itemView.findViewById(R.id.divider)

        var notice_expand: Boolean = false
        var desc_expand: Boolean = false
        var electric_expand: Boolean = false

        init {
            StringUtil.setCopy(userDesc, userNotice)
        }

        fun setFollowed(followed: Boolean) {
            msgBtn.visibility = if (followed) View.VISIBLE else View.GONE
            followBtn.backgroundTintList = ColorStateList
                .valueOf(
                    // 原写死 #F05D8E / #DD262626：未关注 = 主题主色，已关注 = 主题卡片色
                    if (followed) ColorScheme.CARD_WITH_ALPHA else ColorScheme.PRIMARY
                )
            followBtn.text = if (followed) UiText.of("已关注") else UiText.of("关注")
        }

        @SuppressLint("SetTextI18n")
        fun bind(context: Context, userInfo: UserInfo) {
            val lvStr = SpannableStringBuilder("Lv" + userInfo.level)
            lvStr.setSpan(
                StringUtil.getLevelBadge(context, userInfo), 0, lvStr.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
            if (userInfo.vip_role > 0) {
                val vipTypeMap: LinkedHashMap<Int, String> = linkedMapOf(
                    1 to UiText.of("月度大会员"),
                    3 to UiText.of("年度大会员"),
                    7 to UiText.of("十年大会员"),
                    15 to UiText.of("百年大会员")
                )
                lvStr.append("  ").append(vipTypeMap[userInfo.vip_role]).append(" ")
                lvStr.setSpan(
                    RadiusBackgroundSpan(
                        1, context.resources.getDimension(R.dimen.card_round).toInt(),
                        Color.WHITE, ColorScheme.PRIMARY
                    ),
                    ("Lv" + userInfo.level).length + 1, lvStr.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE
                )
            }
            this.userLevel.text = lvStr
            if (!userInfo.vip_nickname_color.isEmpty())
                this.userName.setTextColor(Color.parseColor(userInfo.vip_nickname_color))
            this.userName.text = userInfo.name
            this.userDesc.text = userInfo.sign
            if (!userInfo.notice.isEmpty())
                this.userNotice.text = userInfo.notice
            else
                this.userNotice.visibility = View.GONE
            this.uidTv.text = userInfo.mid.toString()
            StringUtil.setCopy(this.uidTv)
            StringUtil.setLink(this.userDesc, this.userNotice)
            this.userFans.text = StringUtil.toWan(userInfo.fans.toLong()) + UiText.of("粉丝")
            this.userFans.setOnClickListener {
                it.context.startActivity(
                    Intent(it.context, FollowUsersActivity::class.java)
                        .putExtra("mode", 1).putExtra("mid", userInfo.mid)
                )
            }

            this.userMedal.setOnClickListener {
                it.context.startActivity(
                    Intent(it.context, MedalWallActivity::class.java)
                        .putExtra("mid", userInfo.mid)
                )
            }

            this.userFollowings.text = StringUtil.toWan(userInfo.following.toLong()) + UiText.of("关注")
            this.userFollowings.setOnClickListener {
                it.context.startActivity(
                    Intent(it.context, FollowUsersActivity::class.java)
                        .putExtra("mode", 0).putExtra("mid", userInfo.mid)
                )
            }

            if (userInfo.official != 0) {
                this.officialIcon.visibility = View.VISIBLE
                this.userOfficial.visibility = View.VISIBLE
                val official_signs = arrayOf(
                    UiText.of("哔哩哔哩不知名UP主"), UiText.of("哔哩哔哩知名UP主"), UiText.of("哔哩哔哩大V达人"), UiText.of("哔哩哔哩企业认证"),
                    UiText.of("哔哩哔哩组织认证"), UiText.of("哔哩哔哩媒体认证"), UiText.of("哔哩哔哩政府认证"), UiText.of("哔哩哔哩高能主播"), UiText.of("社会不知名人士"), UiText.of("社会知名人士")
                )
                // 服务端的 official 取值并不保证落在文案表范围内（实测见过 10），
                // 直接下标会 ArrayIndexOutOfBoundsException；夹到合法区间再取。
                // 上游 26f1742 用的是 Math.max(0, Math.min(official, len - 1))。
                val officialIdx = userInfo.official.coerceIn(0, official_signs.size - 1)
                this.userOfficial.text = official_signs[officialIdx] +
                        (if (userInfo.officialDesc.isEmpty()) "" else ("\n" + userInfo.officialDesc))
            } else {
                this.officialIcon.visibility = View.GONE
                this.userOfficial.visibility = View.GONE
            }

            GlideUtil.with(this.userAvatar).asDrawable().load(GlideUtil.url(userInfo.avatar))
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(R.mipmap.akari)
                .error(R.mipmap.akari)
                .apply(RequestOptions.circleCropTransform())
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(this.userAvatar)

            this.userAvatar.setOnClickListener {
                val intent = Intent()
                intent.setClass(context, ImageViewerActivity::class.java)
                val imageList = ArrayList<String>()
                imageList.add(userInfo.avatar)
                intent.putExtra("imageList", imageList)
                context.startActivity(intent)
            }

            if (!userInfo.sys_notice.isEmpty()) {
                this.exclusiveTip.visibility = View.VISIBLE
                val spannableString = SpannableString("!:" + userInfo.sys_notice)
                val drawable: Drawable? = StringUtil.getDrawable(context, R.drawable.icon_warning)
                drawable?.setBounds(0, 0, 30, 30)
                spannableString.setSpan(ImageSpan(drawable!!), 0, 2, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                this.exclusiveTipLabel.text = spannableString
            } else
                this.exclusiveTip.visibility = View.GONE

            if (userInfo.live_room != null) {
                this.liveRoom.visibility = View.VISIBLE
                this.liveRoomLabel.text = userInfo.live_room!!.title
                this.liveRoom.setOnClickListener {
                    TerminalContext.getInstance().enterLiveDetailPage(context, userInfo.live_room!!.roomid)
                }
            } else
                this.liveRoom.visibility = View.GONE

            if ((userInfo.mid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
                || (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) || (userInfo.mid == 0L)
            )
                this.followBtn.visibility = View.GONE
            else
                this.followBtn.isChecked = userInfo.followed
            this.followBtn.setOnClickListener {
                followBtn.isEnabled = false
                this.setFollowed(!(userInfo.followed))
                CenterThreadPool.run {
                    try {
                        val result = UserInfoApi.followUser(userInfo.mid, !(userInfo.followed))
                        val msg: String
                        if (result == 0) {
                            userInfo.followed = !(userInfo.followed)
                            msg = UiText.of("操作成功喵~")
                        } else {
                            CenterThreadPool.runOnUiThread { this.setFollowed(userInfo.followed) }
                            if (result == 22015)
                                msg = UiText.of("被B站风控系统拦截了\n（无法解决，详见公告）")
                            else
                                msg = UiText.of("操作失败（原因未知）：") + result
                        }
                        MsgUtil.showMsg(msg)
                    } catch (e: Exception) {
                        MsgUtil.err(e)
                    }
                    CenterThreadPool.runOnUiThread { followBtn.isEnabled = true }
                }
            }

            this.setFollowed(userInfo.followed)

            // 反馈 #88：已关注时，长按「已关注」可以把他加进关注分组。
            // 未关注 / 未登录时长按没有意义（还没关注就谈不上分组归属），交回系统默认处理。
            this.followBtn.setOnLongClickListener {
                if (!userInfo.followed) return@setOnLongClickListener false
                if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L)
                    return@setOnLongClickListener false
                showFollowGroupPicker(context, userInfo)
                true
            }

            this.msgBtn.setOnClickListener {
                val intent = Intent(context, PrivateMsgActivity::class.java)
                intent.putExtra("uid", userInfo.mid)
                context.startActivity(intent)
            }

            val currentMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
            if (userInfo.mid != currentMid && currentMid != 0L && userInfo.mid != 0L && userInfo.is_follow_display) {
                this.contractBtn.visibility = View.VISIBLE
                val layout = itemView as ConstraintLayout
                val constraintSet = ConstraintSet()
                constraintSet.clone(layout)
                constraintSet.connect(R.id.divider, ConstraintSet.TOP, R.id.contractBtn, ConstraintSet.BOTTOM)
                constraintSet.applyTo(layout)
                this.contractBtn.setOnClickListener {
                    contractBtn.isEnabled = false
                    CenterThreadPool.run {
                        try {
                            val result = UserInfoApi.addContract(userInfo.mid)
                            val msg: String
                            if (result == 0) {
                                msg = UiText.of("加入成功")
                            } else if (result == 158001) {
                                msg = UiText.of("不满足条件")
                            } else {
                                msg = ApiErrorText.opFailedWithDetail(result)
                            }
                            MsgUtil.showMsg(msg)
                        } catch (e: Exception) {
                            MsgUtil.err(e)
                        }
                        CenterThreadPool.runOnUiThread { contractBtn.isEnabled = true }
                    }
                }
            } else {
                this.contractBtn.visibility = View.GONE
                val layout = itemView as ConstraintLayout
                val constraintSet = ConstraintSet()
                constraintSet.clone(layout)
                constraintSet.connect(R.id.divider, ConstraintSet.TOP, R.id.followBtn, ConstraintSet.BOTTOM)
                constraintSet.applyTo(layout)
            }

            this.userDesc.setOnClickListener {
                if (desc_expand)
                    this.userDesc.maxLines = 2
                else
                    this.userDesc.maxLines = 32
                desc_expand = !desc_expand
            }

            this.userNotice.setOnClickListener {
                if (notice_expand)
                    this.userNotice.maxLines = 2
                else
                    this.userNotice.maxLines = 32
                notice_expand = !notice_expand
            }

            loadElectricPanel(context, userInfo)

            // 性别 / IP 属地（批次 1）：数据在 UserInfo（`x/space/wbi/acc/info` 的 sex / ip_location），
            // 「什么情况显示、顺序、分隔符」由 UserInfoApi.buildGenderIpLine 统一决定（纯函数，有 JVM 单测）。
            // 每次都重设 text 与 visibility，避免 Holder 复用后残留上一条用户的行。
            val genderIpLine = UserInfoApi.buildGenderIpLine(
                userInfo,
                context.getString(R.string.social_user_sex_format),
                context.getString(R.string.social_user_ip_format),
                context.getString(R.string.social_user_meta_separator)
            )
            this.userGenderIp.text = genderIpLine
            this.userGenderIp.visibility = if (genderIpLine.isEmpty()) View.GONE else View.VISIBLE

        }

        // ==================== 反馈 #88：长按「已关注」→ 加入关注分组 ====================

        /**
         * 拉分组与「他已经在哪些分组里」，然后回主线程弹菜单。
         *
         * 两个请求都必须在后台线程跑：这是列表头部的绑定路径，主线程发网络请求会卡住整页。
         * 失败也要回主线程提示，否则用户长按后「什么都没发生」。
         */
        private fun showFollowGroupPicker(context: Context, userInfo: UserInfo) {
            CenterThreadPool.run {
                try {
                    // 只列自建分组：默认分组（0）与特别关注（-10）不是「加入分组」的语义，
                    // 特别关注还有单独的接口与入口（关注列表的分组模式）。
                    val tags = FollowApi.getFollowTags().filter { it.tagid > 0 }
                    val joined = FollowApi.getUserFollowTags(userInfo.mid).map { it.tagid }.toSet()
                    CenterThreadPool.runOnUiThread {
                        showFollowGroupMenu(context, userInfo, tags, joined)
                    }
                } catch (e: Exception) {
                    CenterThreadPool.runOnUiThread { MsgUtil.err(UiText.of("加入分组失败"), e) }
                }
            }
        }

        private fun showFollowGroupMenu(
            context: Context,
            userInfo: UserInfo,
            tags: List<FollowTag>,
            joined: Set<Int>
        ) {
            val items = ArrayList<String>(tags.size + 1)
            for (tag in tags) {
                items.add(
                    if (tag.tagid in joined) UiText.fmt("%1\$s（已加入）", tag.name)
                    else tag.name
                )
            }
            items.add(UiText.of("新建分组"))
            val newGroupIndex = items.size - 1

            TerminalDialog.menu(
                context = context,
                title = UiText.of("加入分组"),
                items = items
            ) { which ->
                if (which == newGroupIndex) {
                    showCreateGroupThenJoin(context, userInfo)
                    return@menu
                }
                val tag = tags.getOrNull(which) ?: return@menu
                // 已经在里面就不必再发一次请求，直接告诉用户（服务端对重复加入也不报错，但白跑一趟）
                if (tag.tagid in joined) {
                    MsgUtil.showMsg(UiText.fmt("已经在「%1\$s」分组里了", tag.name))
                } else {
                    CenterThreadPool.run { joinFollowGroupNow(userInfo, tag.tagid, tag.name) }
                }
            }.show()
        }

        /** 新建一个分组，建好后直接把当前用户加进去（省得用户再长按一次）。 */
        private fun showCreateGroupThenJoin(context: Context, userInfo: UserInfo) {
            TerminalDialog.input(
                context = context,
                title = UiText.of("新建分组"),
                hint = UiText.fmt("请输入分组名（最多 %1\$d 个字）", FollowApi.TAG_NAME_MAX_LENGTH),
                maxLength = FollowApi.TAG_NAME_MAX_LENGTH,
                confirmText = UiText.of("创建"),
                onConfirm = { name ->
                    val check = FollowApi.checkTagName(name)
                    if (check.isNotEmpty()) {
                        // 本地预校验失败不关窗、不回调：分组名太长的提示就在输入框上方弹一下
                        MsgUtil.showMsg(check)
                    } else {
                        CenterThreadPool.run { createGroupThenJoin(userInfo, name) }
                    }
                }
            ).show()
        }

        /** 后台线程：建分组 → 回查 tagid → 把用户加进去。 */
        private fun createGroupThenJoin(userInfo: UserInfo, name: String) {
            try {
                val createCode = FollowApi.createFollowTag(name)
                if (createCode != 0) {
                    CenterThreadPool.runOnUiThread { MsgUtil.showMsg(FollowApi.tagErrorMsg(createCode)) }
                    return
                }
                // create 接口只回 code（`data.tagid` 文档里写了但当前实现没取），
                // 按名字回查一次最稳：分组名服务端保证唯一（重名会回 22106）。
                val created = FollowApi.getFollowTags().firstOrNull { it.name == name.trim() }
                if (created == null) {
                    CenterThreadPool.runOnUiThread {
                        MsgUtil.showMsg(UiText.of("分组已创建，但没找到它，请稍后刷新再试"))
                    }
                    return
                }
                joinFollowGroupNow(userInfo, created.tagid, created.name)
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { MsgUtil.err(UiText.of("创建分组失败"), e) }
            }
        }

        /** 后台线程：调用加分组接口，结果回主线程提示。 */
        private fun joinFollowGroupNow(userInfo: UserInfo, tagid: Int, tagName: String) {
            try {
                val code = FollowApi.addFollowTagUsers(listOf(tagid), userInfo.mid)
                CenterThreadPool.runOnUiThread {
                    if (code == 0) MsgUtil.showMsg(UiText.fmt("已加入「%1\$s」", tagName))
                    else MsgUtil.showMsg(FollowApi.tagErrorMsg(code))
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { MsgUtil.err(UiText.of("加入分组失败"), e) }
            }
        }

        private fun loadElectricPanel(context: Context, userInfo: UserInfo) {
            this.electricPanel.visibility = View.GONE

            CenterThreadPool.run {
                try {
                    val panel = ElectricApi.getElectricPanel(userInfo.mid)

                    if (panel != null && panel.hasData()) {
                        CenterThreadPool.runOnUiThread {
                            this.electricPanel.visibility = View.VISIBLE

                            this.electricPanelHeader.text = UiText.fmt(UiText.of("充电公示（本月%1\$s人）"), panel.count)

                            this.electricUserList.layoutManager = LinearLayoutManager(context)
                            val adapter = ElectricUserAdapter(context, panel.list)
                            this.electricUserList.adapter = adapter

                            this.electricPanelHeader.setOnClickListener {
                                electric_expand = !electric_expand
                                if (electric_expand) {
                                    this.electricUserList.visibility = View.VISIBLE
                                    this.electricPanelDivider.visibility = View.VISIBLE
                                    this.electricPanelHeader.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                        0, 0, R.drawable.arrow_up, 0
                                    )
                                } else {
                                    this.electricUserList.visibility = View.GONE
                                    this.electricPanelDivider.visibility = View.GONE
                                    this.electricPanelHeader.setCompoundDrawablesRelativeWithIntrinsicBounds(
                                        0, 0, R.drawable.arrow_down, 0
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}