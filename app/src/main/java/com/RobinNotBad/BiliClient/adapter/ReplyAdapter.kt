package com.RobinNotBad.BiliClient.adapter

import com.RobinNotBad.BiliClient.ui.appearance.GlassStyle
import com.RobinNotBad.BiliClient.ui.appearance.ThemeTint
import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.button.MaterialButton
import com.RobinNotBad.BiliClient.activity.vote.VoteInfoActivity
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.api.ApiErrorText
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.reply.ReplyInfoActivity
import com.RobinNotBad.BiliClient.activity.reply.WriteReplyActivity
import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.api.ReplyTranslateApi
import com.RobinNotBad.BiliClient.api.VoteApi
import com.RobinNotBad.BiliClient.model.Reply
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.LongPressPrefs
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.OptionalFeatures
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.ui.widget.RadiusBackgroundSpan
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import java.io.IOException
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet
import org.json.JSONException

/**
 * 评论区「UP」身份标签底色：**刻意写死红色、不跟主题**（产品要求：UP 身份在任何主题下都一眼可辨）。
 * 别当作漏改的硬编码去「顺手主题化」。
 */
private const val UP_BADGE_COLOR = 0xFFF44336.toInt()

@SuppressLint("ClickableViewAccessibility")
class ReplyAdapter(
    val context: Context,
    val replyList: ArrayList<Reply>,
    val oid: Long,
    val up_mid: Long,
    val root: Long,
    val type: Int,
    var sort: Int,
    val replyType: Int
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var isDetail: Boolean = false
    var isManager: Boolean = false
    var count: Long = -1
    var listener: OnItemClickListener? = null

    private val roundSmallPx = context.resources.getDimension(R.dimen.round_small).toInt()
    private val likeDrawable0 = ContextCompat.getDrawable(context, R.drawable.icon_reply_like0)
    // 已赞图标原写死 #fb6789；与同一行「已赞」数字（ColorScheme.LIKE_COLOR）同色
    private val likeDrawable1 = ThemeTint.tinted(context, R.drawable.icon_reply_like1, ColorScheme.LIKE_COLOR)

    // 点赞去重：记录正在请求中的评论 rpid。
    // 不加这个的话用户连点会连发好几次点赞/取消请求，服务端按最后一次算而界面按每次算，
    // 结果就是点赞状态和服务端对不上。用同步 Set 是因为它会被点击线程和请求线程同时访问。
    private val likingRpids: MutableSet<Long> = Collections.synchronizedSet(HashSet<Long>())

    fun setOnSortSwitchListener(listener: OnItemClickListener) {
        this.listener = listener
    }

    // 主评论列表（非详情页）的排序名。0/1 位置写「未知排序」是因为主列表走服务端排序的 2/3 两档。
    // 四项都走 UiText：这组值只用于展示（判据是 sort 的整数），所以能安全迁移。
    private val sortNames = arrayOf(
        UiText.of("未知排序"), UiText.of("未知排序"), UiText.of("时间排序"), UiText.of("热度排序")
    )

    /**
     * 排序按钮上的文案。
     *
     * 详情页（楼中楼）用 [Reply.SORT_TIME]/[Reply.SORT_LIKE] 这两档本地排序，
     * 主列表用服务端的 2/3 两档，取值不一样必须分开取名——否则详情页永远显示「未知排序」。
     */
    private fun sortLabel(): String =
        if (isDetail) (if (sort == Reply.SORT_TIME) UiText.of("时间排序") else UiText.of("热度排序"))
        else sortNames.getOrElse(sort) { UiText.of("未知排序") }

    /**
     * 把「点踩/取消点踩」的结果落到本地状态与视图上。
     *
     * 服务端点踩成功会同时消去该评论的点赞，反向亦然（见 bilibili-API/docs/comment/action.md）。
     * 所以这里必须把 liked/disliked 两个互斥状态一起改，否则界面上会出现「已赞 + 已踩」并存的假象，
     * 下次重新拉取列表时又突然变回去，用户会以为按钮坏了。
     */
    private fun applyDislikeResult(holder: ReplyHolder, reply: Reply, disliked: Boolean) {
        if (disliked && reply.liked) {
            // 本地把点赞撤掉时，计数和图标要同步回落到未赞状态
            reply.liked = false
            reply.likeCount = (reply.likeCount - 1).coerceAtLeast(0)
            holder.likeCount.text = StringUtil.toWan(reply.likeCount.toLong())
            holder.likeCount.setTextColor(ColorScheme.TEXT_PRIMARY)
            holder.likeCount.setCompoundDrawablesWithIntrinsicBounds(likeDrawable0, null, null, null)
        }
        reply.disliked = disliked
        if (disliked) holder.dislikeBtn.setColorFilter(ColorScheme.LIKE_COLOR)
        else holder.dislikeBtn.clearColorFilter()
    }

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == 0) {
            val view = LayoutInflater.from(this.context).inflate(R.layout.cell_reply_action, parent, false)
            return WriteReply(view)
        } else {
            val view = LayoutInflater.from(this.context).inflate(R.layout.cell_reply_list, parent, false)
            return ReplyHolder(view)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(@NonNull holder: RecyclerView.ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        if (holder is WriteReply) {
            val writeReply = holder as WriteReply
            writeReply.write_reply.setOnClickListener {
                val intent = Intent()
                intent.setClass(context, WriteReplyActivity::class.java)
                intent.putExtra("oid", oid)
                intent.putExtra("rpid", root)
                intent.putExtra("parent", root)
                intent.putExtra("parentSender", "")
                intent.putExtra("replyType", replyType)
                context.startActivity(intent)
            }
            if (isDetail) {
                // 评论详情页也要能排序。服务端的楼中楼接口（/x/v2/reply/reply）没有 sort 参数，
                // 传了也不生效，所以这里的排序完全在客户端做（Reply.sortReplies），
                // 点一下即时重排、不重新请求，见 ReplyInfoActivity.setOnSortSwitch。
                writeReply.sort.visibility = View.VISIBLE
                writeReply.count_label.visibility = View.GONE
                writeReply.sort.text = sortLabel()
                writeReply.sort.setOnClickListener {
                    listener?.onItemClick(0)
                    writeReply.sort.text = sortLabel()
                }
            } else {
                writeReply.sort.text = sortLabel()
                writeReply.sort.setOnClickListener {
                    listener?.onItemClick(0)
                    writeReply.sort.text = sortLabel()
                }
                writeReply.count_label.text = UiText.fmt(UiText.of("共%1\$s条评论"), count)
            }
        }
        if (holder is ReplyHolder) {
            val realPosition: Int
            if (isDetail) {
                realPosition = if (position != 0) position - 1 else 0
            } else {
                realPosition = position - 1
            }
            if (realPosition < 0 || realPosition >= replyList.size)
                return

            val replyHolder = holder as ReplyHolder
            val reply = replyList[realPosition]
            if (reply == null || reply.sender == null)
                return

            if (GlideUtil.url(reply.sender!!.avatar) != replyHolder.lastAvatarUrl) {
                replyHolder.lastAvatarUrl = GlideUtil.url(reply.sender!!.avatar)
                GlideUtil.with(replyHolder.replyAvatar).asDrawable().load(GlideUtil.url(reply.sender!!.avatar))
                        .transition(GlideUtil.getTransitionOptions())
                        .placeholder(R.mipmap.akari)
                        .error(R.mipmap.akari)
                        .apply(RequestOptions.circleCropTransform())
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(replyHolder.replyAvatar)
            }

            val sender = reply.sender!!
            val name_str = SpannableStringBuilder()

            if (!TextUtils.isEmpty(sender.vip_nickname_color)
                    && !SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_VIP_COLOR, false))
                replyHolder.userName.setTextColor(Color.parseColor(sender.vip_nickname_color))

            if (sender.mid == up_mid) {
                name_str.append(" UP ")
                name_str.append(reply.sender!!.name)
                name_str.setSpan(
                        RadiusBackgroundSpan(2, roundSmallPx,
                                Color.WHITE, UP_BADGE_COLOR),
                        0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                name_str.setSpan(RelativeSizeSpan(0.8f), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else
                name_str.append(sender.name)
            var last_length = name_str.length
            name_str.append(" ").append(sender.level.toString())
            if (sender.is_senior_member == 1)
                name_str.append("+")
            name_str.setSpan(StringUtil.getLevelBadge(context, sender), last_length + 1, name_str.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (!TextUtils.isEmpty(sender.medal_name)) {
                last_length = name_str.length
                name_str.append("  ").append(sender.medal_name).append("Lv").append(sender.medal_level.toString())
                        .append(" ")
                name_str.setSpan(
                        RadiusBackgroundSpan(2, roundSmallPx,
                                Color.WHITE, ColorScheme.withPrimaryAlpha(140)),
                        last_length + 1, name_str.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                name_str.setSpan(RelativeSizeSpan(0.8f), last_length + 1, name_str.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            replyHolder.userName.text = name_str

            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.REPLY_MARQUEE_NAME, true)) {
                replyHolder.userName.setSingleLine(true)
                replyHolder.userName.maxLines = 1
            } else {
                replyHolder.userName.setSingleLine(false)
                replyHolder.userName.maxLines = 3
            }

            replyHolder.message.text = reply.message
            // 长按正文：开关打开时弹「操作面板」，「复制」就在面板里（见 [showManageMenu]），
            // 长按不再直接把用户丢进复制界面；开关关闭时才退回旧行为。
            // 之前这里是「可管理才挂监听、不可管理交给 setCopy」——两条路各弹各的，
            // 所以在自己的评论上长按，用户分不清到底会复制还是弹菜单。
            if (LongPressPrefs.isEnabled()) {
                replyHolder.message.setOnLongClickListener {
                    showManageMenu(reply)
                    true
                }
            } else {
                StringUtil.setCopy(replyHolder.message)
            }
            replyHolder.message.setOnTouchListener(StringUtil.ClickableSpanTouchListener.getInstance())

            // 译文与「文A」按钮的绑定：必须放在 message 赋值之后（同一条评论的译文挂在原文下面）。
            // 楼中楼子评论走的是 childReplies 里的独立 TextView，不经过这个 holder，所以这里只覆盖主评论。
            bindTranslation(reply, replyHolder)

            replyHolder.likeCount.text = StringUtil.toWan(reply.likeCount.toLong())

            if (reply.liked) {
                replyHolder.likeCount.setTextColor(ColorScheme.LIKE_COLOR)
                replyHolder.likeCount.setCompoundDrawablesWithIntrinsicBounds(
                        likeDrawable1, null, null, null)
            } else {
                // 「未点赞」的文字色跟主题走，不再写死纯白：浅色主题下纯白会看不见
                replyHolder.likeCount.setTextColor(ColorScheme.TEXT_PRIMARY)
                replyHolder.likeCount.setCompoundDrawablesWithIntrinsicBounds(
                        likeDrawable0, null, null, null)
            }

            // 已踩状态：icon_dislike 是单色矢量，用同一套高亮色即可，不必再配一个图标资源。
            // 这一步原来完全没做，所以「我踩过的评论」重进页面后看起来像没操作过。
            if (reply.disliked) {
                replyHolder.dislikeBtn.setColorFilter(ColorScheme.LIKE_COLOR)
            } else {
                replyHolder.dislikeBtn.clearColorFilter()
            }

            if (reply.childCount != 0 && !(realPosition == 0 && isDetail)) {
                replyHolder.childReplyCard.visibility = View.VISIBLE
                replyHolder.childCount.setTextColor(ColorScheme.PRIMARY)

                if (reply.upReplied)
                    replyHolder.childCount.text = UiText.fmt(UiText.of("UP主在内 共%1\$s条回复"), reply.childCount)
                else
                    replyHolder.childCount.text = UiText.fmt(UiText.of("共%1\$s条回复"), reply.childCount)

                if (reply.childMsgList != null && replyHolder.childReplies != null) {
                    val childCount = reply.childMsgList!!.size
                    val existingViewCount = replyHolder.childReplies!!.childCount

                    for (i in 0 until childCount) {
                        val child = reply.childMsgList!![i]
                        if (child == null || child.sender == null)
                            continue

                        val childMsg = SpannableStringBuilder()
                        if (child.sender!!.mid == up_mid) {
                            childMsg.append(" UP ")
                            childMsg.append(child.sender!!.name)
                            childMsg.setSpan(RadiusBackgroundSpan(2,
                                    roundSmallPx, Color.WHITE,
                                    UP_BADGE_COLOR), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            childMsg.setSpan(RelativeSizeSpan(0.8f), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        } else
                            childMsg.append(child.sender!!.name)

                        childMsg.append("：").append(child.message)

                        val textView: TextView
                        if (i < existingViewCount) {
                            textView = replyHolder.childReplies!!.getChildAt(i) as TextView
                            textView.visibility = View.VISIBLE
                        } else {
                            @SuppressLint("InflateParams")
                            val newTextView = LayoutInflater.from(context)
                                    .inflate(R.layout.cell_reply_child, null) as TextView
                            replyHolder.childReplies!!.addView(newTextView)
                            textView = newTextView
                        }
                        textView.text = childMsg
                    }

                    for (i in childCount until existingViewCount) {
                        replyHolder.childReplies!!.getChildAt(i).visibility = View.GONE
                    }
                }
            } else
                replyHolder.childReplyCard.visibility = View.GONE

            if (reply.upLiked)
                replyHolder.upLiked.visibility = View.VISIBLE
            else
                replyHolder.upLiked.visibility = View.GONE

            replyHolder.pubDate.text = reply.pubTime

            if (reply.pictureList != null && !reply.pictureList!!.isEmpty()) {
                replyHolder.imageCard.visibility = View.VISIBLE
                replyHolder.imageCount.visibility = View.VISIBLE

                val firstImageUrl = GlideUtil.url(reply.pictureList!![0])
                if (firstImageUrl != replyHolder.lastImageUrl) {
                    replyHolder.lastImageUrl = firstImageUrl
                    GlideUtil.with(replyHolder.imageCard).asDrawable().load(firstImageUrl)
                            .transition(GlideUtil.getTransitionOptions())
                            .placeholder(R.mipmap.placeholder)
                            .error(R.mipmap.placeholder)
                            .format(DecodeFormat.PREFER_RGB_565)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .into(replyHolder.imageCard)
                }

                replyHolder.imageCount.text = UiText.fmt(UiText.of("共%1\$s张图片"), reply.pictureList!!.size)
                replyHolder.imageCard.setOnClickListener {
                    val intent = Intent()
                    intent.setClass(context, ImageViewerActivity::class.java)
                    intent.putExtra("imageList", reply.pictureList)
                    context.startActivity(intent)
                }
            } else {
                replyHolder.imageCount.visibility = View.GONE
                replyHolder.imageCard.visibility = View.GONE
            }

            replyHolder.childReplyCard.setOnClickListener { startReplyInfoActivity(reply) }
            if (!isDetail) {
                replyHolder.itemView.setOnClickListener { startReplyInfoActivity(reply) }
                replyHolder.message.setOnClickListener {
                    if (reply.voteId > 0) {
                        // 点击投票标签，展示投票信息
                        showVoteDialog(reply.voteId, replyHolder.message)
                    } else {
                        startReplyInfoActivity(reply)
                    }
                }
            }

            replyHolder.replyAvatar.setOnClickListener {
                BiliTerminal.jumpToUser(context, reply.sender!!.mid)
            }

            replyHolder.likeCount.setOnClickListener {
                // 客户端去重：同一条评论的点赞请求还没回来之前，后续连点直接忽略，
                // 否则会连发好几次点赞/取消，服务端只认最后一次，界面状态就和服务端对不上了
                if (!likingRpids.add(reply.rpid)) {
                    MsgUtil.showMsg(UiText.of("正在处理中"))
                    return@setOnClickListener
                }
                CenterThreadPool.run {
                    try {
                        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                            (context as Activity).runOnUiThread { MsgUtil.showMsg(UiText.of("还没有登录喵~")) }
                            return@run
                        }
                        if (!reply.liked) {
                            try {
                                // 点赞必须带上评论所属的评论区类型：动态/专栏的评论传 1 会被服务端拒绝
                                val likeCode = ReplyApi.likeReply(oid, reply.rpid, replyType, true)
                                if (likeCode == 0) {
                                    reply.liked = true
                                    // 点赞会同时消去点踩（服务端语义），本地状态必须一起改
                                    reply.disliked = false
                                    (context as Activity).runOnUiThread {
                                        MsgUtil.showMsg(UiText.of("点赞成功"))
                                        replyHolder.dislikeBtn.clearColorFilter()
                                        replyHolder.likeCount.text = StringUtil.toWan((++reply.likeCount).toLong())
                                        replyHolder.likeCount.setTextColor(ColorScheme.LIKE_COLOR)
                                        replyHolder.likeCount.setCompoundDrawablesWithIntrinsicBounds(
                                                likeDrawable1, null, null,
                                                null)
                                    }
                                } else
                                    (context as Activity).runOnUiThread {
                                        MsgUtil.showMsg(ReplyApi.actionErrorMsg(likeCode).ifEmpty { UiText.of("点赞失败") })
                                    }
                            } catch (e: IOException) {
                                e.printStackTrace()
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                        } else {
                            try {
                                val unlikeCode = ReplyApi.likeReply(oid, reply.rpid, replyType, false)
                                if (unlikeCode == 0) {
                                    reply.liked = false
                                    (context as Activity).runOnUiThread {
                                        MsgUtil.showMsg(UiText.of("取消成功"))
                                        replyHolder.likeCount.text = StringUtil.toWan((--reply.likeCount).toLong())
                                        replyHolder.likeCount.setTextColor(ColorScheme.TEXT_PRIMARY)
                                        replyHolder.likeCount.setCompoundDrawablesWithIntrinsicBounds(
                                                likeDrawable0, null, null,
                                                null)
                                    }
                                } else
                                    (context as Activity).runOnUiThread {
                                        MsgUtil.showMsg(ReplyApi.actionErrorMsg(unlikeCode).ifEmpty { UiText.of("取消失败") })
                                    }
                            } catch (e: IOException) {
                                e.printStackTrace()
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                        }
                    } finally {
                        // 成功、失败、未登录、抛异常都必须摘掉标志，
                        // 否则这条评论会永久卡在"正在处理中"，再也点不动
                        likingRpids.remove(reply.rpid)
                    }
                }
            }

            replyHolder.dislikeBtn.setOnClickListener {
                // 复用点赞那把锁：赞和踩在服务端互斥，两个请求并发会互相抵消，
                // 所以「这条评论有反馈请求在飞」时，赞和踩都要一起挡住
                if (!likingRpids.add(reply.rpid)) {
                    MsgUtil.showMsg(UiText.of("正在处理中"))
                    return@setOnClickListener
                }
                CenterThreadPool.run {
                    try {
                        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                            (context as Activity).runOnUiThread { MsgUtil.showMsg(UiText.of("还没有登录喵~")) }
                            return@run
                        }
                        val target = !reply.disliked
                        try {
                            val hateCode = ReplyApi.dislikeReply(oid, reply.rpid, replyType, target)
                            if (hateCode == 0) {
                                (context as Activity).runOnUiThread {
                                    applyDislikeResult(replyHolder, reply, target)
                                    MsgUtil.showMsg(if (target) UiText.of("已点踩") else UiText.of("已取消"))
                                }
                            } else {
                                val msg = ReplyApi.actionErrorMsg(hateCode)
                                (context as Activity).runOnUiThread {
                                    MsgUtil.showMsg(msg.ifEmpty { UiText.of("操作失败") })
                                }
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    } finally {
                        likingRpids.remove(reply.rpid)
                    }
                }
            }

            if (canManage(reply)) {
                replyHolder.item_reply_delete.visibility = View.VISIBLE
                // 点一下、长按，都弹同一个操作菜单（弹窗在 300x300 表盘上也能点得到）。
                replyHolder.item_reply_delete.setOnClickListener { showManageMenu(reply) }
                replyHolder.item_reply_delete.setOnLongClickListener {
                    showManageMenu(reply)
                    true
                }
            } else
                replyHolder.item_reply_delete.visibility = View.GONE

            replyHolder.replyBtn.setOnClickListener {
                val noParent = isDetail && realPosition == 0
                val intent = Intent()
                intent.setClass(context, WriteReplyActivity::class.java)
                intent.putExtra("oid", oid)
                intent.putExtra("rpid", if (noParent) root else reply.rpid)
                intent.putExtra("parent", if (noParent) root else reply.rpid)
                intent.putExtra("replyType", replyType)
                intent.putExtra("pos", realPosition)
                if (root != 0L && !noParent)
                    intent.putExtra("parentSender", reply.sender!!.name)
                else
                    intent.putExtra("parentSender", "")
                context.startActivity(intent)
            }
        }
    }

    /**
     * 这条评论当前用户能不能操作（置顶 / 删除）。
     *
     * <p>评论区的管理员（视频 UP 主 / 合作稿 staff）能管所有评论；普通用户只能管自己发的。
     * 集中成一个判断，是因为「操作按钮要不要显示」和「长按正文弹不弹菜单」必须用同一套标准，
     * 否则会出现「按钮看得见但长按没反应」这种自相矛盾的状态。
     */
    private fun canManage(reply: Reply): Boolean {
        return isManager || reply.sender?.mid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
    }

    /**
     * 操作面板里的一项。
     *
     * [kind] 是本项的身份、[danger] 是「要不要用危险色」——两者都不能从 [label] 反推：
     * 展示文案会跟着翻译/措辞改动，拿它当判据（原先的 `first == "复制文字"` / `startsWith("删除")`）
     * 会在文案一改时静默失配，表现是「只有一项时不直接复制」「删除项不再是红色」。
     *
     * [EXTRA] 给可选私有功能（能在公开构建里缺席的项）用：它既不是「复制」，也不该被当成
     * 破坏性操作染色，所以单独一个身份，别去复用 [COPY] —— 那会让「只剩一项就直连」的捷径误判。
     */
    private class ManageAction(
        val kind: Kind,
        val label: String,
        val danger: Boolean,
        val run: () -> Unit
    ) {
        enum class Kind { COPY, TOP, UNTOP, DELETE, EXTRA }
    }

    /**
     * 评论操作面板（长按正文、点击/长按末列操作按钮，都走这里）。
     *
     * <p>「复制」从「注册一个长按监听」改成面板里的一项：长按正文时用户先看到有哪些操作，
     * 而不是被直接送进复制界面——那正是「和长按复制冲突」的观感来源。
     *
     * <p>置顶项只在 [isManager] 为 true（视频 UP 主 / 合作稿 staff）时出现。
     * 服务端对置顶的硬性要求是「本评论区的一级评论」（错误码 12030）且一个区只有一个
     * 置顶位（12029），最终以服务端判定为准；这里不展示必然失败的入口只是为了少打扰，
     * 评论区管理员（非 UP）的场景留给以后，判定逻辑与权限完全分开。
     *
     * <p>「举报评论」是**可选私有功能**：实现与理由表都在私有构建里，公共侧只有一条
     * [OptionalFeatures.replyReportLabel] 门面。私有注入不在场时本项根本不出现，
     * 于是公开构建里别人的评论只剩「复制文字」一项——这是加入私有功能之前的既有行为。
     */
    private fun showManageMenu(reply: Reply) {
        val actions = ArrayList<ManageAction>()
        // 复制项受 copy_enable 约束，正文为空时也不列（点了只会看到一个空界面）。
        // reply.message 在 Java 侧是 CharSequence，复制界面只收 String，这里统一转一次。
        val content = reply.message?.toString().orEmpty()
        if (LongPressPrefs.shouldOfferCopy(StringUtil.isCopyEnabled(), content))
            actions.add(ManageAction(ManageAction.Kind.COPY, UiText.of("复制文字"), false) {
                StringUtil.openCopyPage(context, content)
            })
        if (isManager) {
            actions.add(
                if (reply.isTop)
                    ManageAction(ManageAction.Kind.UNTOP, UiText.of("取消置顶"), false) { setReplyTop(reply, false) }
                else
                    ManageAction(ManageAction.Kind.TOP, UiText.of("置顶评论"), false) { setReplyTop(reply, true) }
            )
        }
        // 「删除评论」的门槛必须在这里再判一次：本方法原来只被管理按钮调用（按钮本身就
        // 只在 canManage 时可见），现在别人的评论长按也会进来，漏判就会给出一个必然失败的删除项。
        if (canManage(reply)) actions.add(ManageAction(ManageAction.Kind.DELETE, UiText.of("删除评论"), true) {
            confirmDeleteReply(reply)
        })

        // 「举报评论」只对「不是自己、也管不了」的评论出现：自己的评论举报自己没意义，
        // 管理员的处置手段是删除而不是举报。
        // **不按登录态隐藏**：藏起来会让别人的评论只剩「复制文字」一项，从而命中下面那条
        // 「只剩复制就直接复制」的捷径——重装后丢了登录态的用户会以为举报功能不存在。
        // 未登录时照常列出，点了由私有侧提示「请先登录后再举报」。
        // 文案由私有侧给：null = 私有注入不在场（公开构建），本项不出现。
        val reportLabel = if (canManage(reply)) null else OptionalFeatures.replyReportLabel(context)
        if (reportLabel != null)
            actions.add(ManageAction(ManageAction.Kind.EXTRA, reportLabel, false) {
                OptionalFeatures.replyReport(context, oid, replyType, reply.rpid)
            })

        if (actions.isEmpty()) {
            MsgUtil.showMsg(UiText.of("没有可操作的项"))
            return
        }

        // 只剩「复制文字」一项时，不必弹一个只有单选项的菜单——直接进复制界面。
        // 公开构建里别人的评论正是这个组合（没有举报项），行为与引入私有功能之前一致；
        // 动态那条 `DynamicHolder.showManageMenu` 也在用同一套判据。
        if (actions.size == 1 && actions[0].kind == ManageAction.Kind.COPY) {
            actions[0].run()
            return
        }

        // 「删除评论」是破坏性操作，用危险色标出来（原实现里它和「置顶评论」同色）
        val dangerIndex = actions.indexOfFirst { it.danger }
        TerminalDialog.menu(
            context = context,
            items = actions.map { it.label },
            danger = if (dangerIndex >= 0) setOf(dangerIndex) else emptySet()
        ) { which -> actions[which].run() }.show()
    }

    /**
     * 置顶 / 取消置顶一条评论。
     *
     * <p>两个容易踩的点：
     * ① 服务端一个评论区只有一个置顶位（再置顶别的会回 12029），而 [Reply.isTop] 是逐条布尔，
     * 所以置顶成功后必须把本地其它条目的标记一起清掉，否则列表里会同时出现两条「[置顶]」；
     * ② `[置顶]` 前缀是构造 [Reply] 时拼进显示文本的，只改布尔值界面不会变，
     * 因此统一走 [Reply.setTopFlag] 让它同步增删前缀，再 notify 重新绑定。
     */
    private fun setReplyTop(reply: Reply, top: Boolean) {
        CenterThreadPool.run {
            try {
                val code = ReplyApi.topReply(oid, reply.rpid, replyType, top)
                (context as Activity).runOnUiThread {
                    if (code == 0) {
                        if (top) Reply.clearTopFlags(replyList)
                        reply.setTopFlag(top)
                        notifyItemRangeChanged(0, itemCount)
                        MsgUtil.showMsg(if (top) UiText.of("置顶成功~") else UiText.of("已取消置顶"))
                    } else {
                        MsgUtil.showMsg(ReplyApi.actionErrorMsg(code).ifEmpty { ApiErrorText.opFailedBrief(code) })
                    }
                }
            } catch (e: Exception) {
                (context as Activity).runOnUiThread { MsgUtil.err(e) }
            }
        }
    }

    private fun confirmDeleteReply(reply: Reply) {
        TerminalDialog.confirm(
            context = context,
            title = UiText.of("删除评论"),
            message = UiText.of("删除后无法恢复，确定删除这条评论吗？"),
            confirmText = UiText.of("删除"),
            onConfirm = { deleteReply(reply) }
        ).show()
    }

    private fun deleteReply(reply: Reply) {
        CenterThreadPool.run {
            try {
                val result = ReplyApi.deleteReply(oid, reply.rpid, replyType)
                (context as Activity).runOnUiThread {
                    if (result == 0) {
                        // 数据改动必须和 notify 一样在主线程执行：原来 removeAt 在后台线程做、
                        // notify 在主线程做，主线程读到的可能还是旧列表，会删错行甚至下标越界崩溃。
                        // 位置现取现用（列表可能在弹窗打开期间被刷新过），不再用绑定时捕获的下标。
                        val index = replyList.indexOf(reply)
                        if (index >= 0 && index < replyList.size) {
                            replyList.removeAt(index)
                            // adapter 第 0 位是"写评论"头部（详情页里是根评论占第 0 位），
                            // 列表项下标换算成 adapter 下标要 +1
                            val adapterPosition = if (isDetail && index == 0) 0 else index + 1
                            notifyItemRemoved(adapterPosition)
                            notifyItemRangeChanged(adapterPosition, replyList.size + 1 - adapterPosition)
                        }
                        MsgUtil.showMsg(UiText.of("删除成功~"))
                        if (index == 0 && isDetail) {
                            (context as Activity).finish()
                        }
                    } else {
                        val msg = when (result) {
                            -404 -> UiText.of("没有这条评论！")
                            -403 -> UiText.of("权限不足！")
                            else -> ApiErrorText.opFailedWithDetail(result)
                        }
                        MsgUtil.showMsg(msg)
                    }
                }
            } catch (e: Exception) {
                (context as Activity).runOnUiThread { MsgUtil.err(e) }
            }
        }
    }

    /**
     * 把一条评论的译文状态刷到它当前的 holder 上（列表绑定与点击后的即时反馈都走这里）。
     *
     * <p>为什么每次绑定都要重设点击监听：RecyclerView 的 holder 是复用的，同一条评论在不同时刻
     * 可能落在不同 holder 上，而监听闭包捕获的是「reply + 当时那个 holder」。不重设的话，
     * 列表滚动后点「文A」可能改到别的评论，或者把译文刷到已经回收给其他评论的行上。
     *
     * <p>为什么用色滤而不是再配一个高亮图标：icon_translate 是单色矢量，
     * [PorterDuff.Mode.SRC_IN] 能直接把整套 path 染成主题点赞色，和点踩高亮
     *（dislikeBtn.setColorFilter）是同一套做法，少一个资源文件。译文显示中染色 =「再点一下收起」；
     * 不显示时清掉色滤、回到矢量原色。
     *
     * <p>请求在途时禁用点击并把 alpha 压到 0.35，避免用户在响应回来之前连点、重复发翻译请求。
     */
    private fun bindTranslation(reply: Reply, holder: ReplyHolder) {
        // 入口暂时隐藏（ReplyTranslateApi.UI_ENABLED=false）：按钮与译文区一起收起来，
        // 也不挂点击事件，界面上不出现任何翻译痕迹；开关打开后下面的绑定逻辑原样生效。
        if (!ReplyTranslateApi.UI_ENABLED) {
            holder.item_reply_translate.visibility = View.GONE
            holder.replyTranslation.visibility = View.GONE
            holder.item_reply_translate.setOnClickListener(null)
            return
        }
        holder.replyTranslation.text = reply.translatedMessage
        holder.replyTranslation.visibility =
            if (reply.translationShown && reply.hasTranslation()) View.VISIBLE else View.GONE
        holder.item_reply_translate.isEnabled = !reply.translationLoading
        holder.item_reply_translate.alpha = if (reply.translationLoading) 0.35f else 0.7f
        if (reply.translationShown && reply.hasTranslation()) {
            holder.item_reply_translate.colorFilter =
                PorterDuffColorFilter(ColorScheme.LIKE_COLOR, PorterDuff.Mode.SRC_IN)
        } else {
            holder.item_reply_translate.colorFilter = null
        }
        holder.item_reply_translate.setOnClickListener { toggleTranslation(reply, holder) }
    }

    /**
     * 点「文A」：已经有译文就纯本地切换显示/隐藏；没有译文才发一次 gRPC 翻译请求。
     *
     * <p>请求放在 [CenterThreadPool] 的后台协程里跑——[ReplyTranslateApi.translateReply] 是同步阻塞的
     * 网络调用，放主线程会 ANR。回主线程后不直接改 holder，而是走 [notifyReplyChanged] 重绑整行：
     * 网络往返期间列表可能滚动、holder 已被回收给别的评论，直接写 holder 会把译文串到别人身上。
     *
     * <p>「没拿到译文」和「请求失败」要分开提示：接口对不可译/已是目标语言的评论返回 null 或空串，
     * 这不是故障，提示「暂无译文」比「失败」更准确，也就不会让用户反复重试。
     */
    private fun toggleTranslation(reply: Reply, holder: ReplyHolder) {
        if (reply.translationLoading) return
        if (reply.hasTranslation()) {
            reply.toggleTranslationShown()
            bindTranslation(reply, holder)
            return
        }
        // 只有列表里真实存在的评论才值得发请求；列表刚被清空/刷新过时这条可能已经不在了
        val index = replyList.indexOf(reply)
        if (index < 0) return

        reply.translationLoading = true
        bindTranslation(reply, holder)

        CenterThreadPool.run {
            try {
                // 固定译成简体中文：App 只有中文界面，不跟随系统语言（见 ReplyTranslateApi.TARGET_LOCALE）
                val header = ReplyTranslateApi.buildLocaleHeader(ReplyTranslateApi.TARGET_LOCALE)
                val text = ReplyTranslateApi.translateReply(type, oid, reply.rpid, header)
                CenterThreadPool.runOnUiThread {
                    reply.translationLoading = false
                    if (text.isNullOrEmpty())
                        MsgUtil.showMsg(context.getString(R.string.msg_reply_translate_none))
                    else
                        reply.applyTranslation(text)
                    notifyReplyChanged(reply)
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread {
                    reply.translationLoading = false
                    // 只弹一次「翻译失败」：MsgUtil.err 对 IOException 会再弹一次「网络错误」，
                    // 用户看到两个先后出现的 toast，且服务端业务错误（如 12002）被误报成网络问题。
                    // 诊断信息走 Logu（落盘 + 可被 DebugBridge 读取），不打扰用户。
                    MsgUtil.showMsg(context.getString(R.string.msg_reply_translate_failed))
                    Logu.e("ReplyAdapter>toggleTranslation", e.toString())
                    notifyReplyChanged(reply)
                }
            }
        }
    }

    /**
     * 只刷新一条评论对应的 adapter 位置。
     *
     * <p>位置映射必须和 [deleteReply] 里的 adapterPosition 规则一致，否则会刷错行：
     * 主列表第 0 位是「写评论」头部，所以 reply 下标要 +1；
     * 详情页第 0 位就是根评论本身（replyList 下标 0），只有它之后的才 +1。
     */
    private fun notifyReplyChanged(reply: Reply) {
        val index = replyList.indexOf(reply)
        if (index < 0) return
        notifyItemChanged(if (isDetail && index == 0) 0 else index + 1)
    }

    override fun onViewRecycled(@NonNull holder: RecyclerView.ViewHolder) {
        if (holder is ReplyHolder) {
            val replyHolder = holder as ReplyHolder
            replyHolder.lastAvatarUrl = null
            replyHolder.lastImageUrl = null
        }
        super.onViewRecycled(holder)
    }

    fun startReplyInfoActivity(reply: Reply?) {
        if (reply == null)
            return
        val rpid = reply.rpid
        val oid = reply.oid
        val intent = Intent()
        intent.setClass(context, ReplyInfoActivity::class.java)
        intent.putExtra("rpid", rpid)
        intent.putExtra("oid", oid)
        intent.putExtra("type", replyType)
        intent.putExtra("up_mid", up_mid)
        intent.putExtra("is_manager", isManager)
        context.startActivity(intent)
    }

    /**
     * 跳转到投票详情页
     */
    private fun showVoteDialog(voteId: Long, anchor: View) {
        val intent = Intent(context, VoteInfoActivity::class.java)
        intent.putExtra(VoteInfoActivity.EXTRA_VOTE_ID, voteId)
        context.startActivity(intent)
    }

    override fun getItemCount(): Int {
        return if (replyList != null) replyList.size + 1 else 1
    }

    override fun getItemViewType(position: Int): Int {
        if (isDetail && position == 1) {
            return 0
        } else if (!isDetail && position == 0) {
            return 0
        }
        return 1
    }

    class ReplyHolder(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val replyAvatar: ImageView = itemView.findViewById(R.id.replyAvatar)
        val dislikeBtn: ImageView = itemView.findViewById(R.id.dislikeBtn)
        val childReplies: LinearLayout = itemView.findViewById(R.id.repliesList)
        val message: TextView = itemView.findViewById(R.id.replyText)
        val userName: TextView = itemView.findViewById(R.id.replyUsername)
        val pubDate: TextView = itemView.findViewById(R.id.replyPubDate)
        val childCount: TextView = itemView.findViewById(R.id.repliesControl)
        val likeCount: TextView = itemView.findViewById(R.id.likes)
        val replyBtn: TextView = itemView.findViewById(R.id.replyBtn)
        val upLiked: TextView = itemView.findViewById(R.id.upLiked)
        val imageCount: TextView = itemView.findViewById(R.id.imageCount)
        val item_reply_delete: TextView = itemView.findViewById(R.id.item_reply_delete)
        val item_reply_translate: ImageView = itemView.findViewById(R.id.item_reply_translate)
        val replyTranslation: TextView = itemView.findViewById(R.id.replyTranslation)
        val childReplyCard: LinearLayout = itemView.findViewById(R.id.repliesCard)
        val imageCard: ImageView = itemView.findViewById(R.id.imageCard)
        var lastAvatarUrl: String? = null
        var lastImageUrl: String? = null

        init {
            // 玻璃质感：楼中楼预览框与「UP主觉得很赞」标签原是写死 #dd262626 的 shape，换成玻璃面板（只在建 holder 时做一次）
            if (GlassStyle.isEnabled()) {
                childReplyCard.background = GlassStyle.panelBackground(itemView.context)
                upLiked.background = GlassStyle.panelBackground(itemView.context)
            }
        }
    }

    class WriteReply(@NonNull itemView: View) : RecyclerView.ViewHolder(itemView) {
        val write_reply: MaterialButton = itemView.findViewById(R.id.write_reply)
        val sort: MaterialButton = itemView.findViewById(R.id.sort)
        val count_label: TextView = itemView.findViewById(R.id.count_label)
    }
}