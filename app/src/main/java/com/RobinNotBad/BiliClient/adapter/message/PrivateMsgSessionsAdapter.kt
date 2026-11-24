package com.RobinNotBad.BiliClient.adapter.message

import com.RobinNotBad.BiliClient.util.UiText

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.message.ChatSettingsActivity
import com.RobinNotBad.BiliClient.activity.message.PrivateMsgActivity
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.model.PrivateMessage
import com.RobinNotBad.BiliClient.model.PrivateMsgSession
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.ui.widget.RadiusBackgroundSpan
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import org.json.JSONException
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.ui.appearance.GlassStyle

class PrivateMsgSessionsAdapter(
    val context: Context,
    val sessionsList: ArrayList<PrivateMsgSession>,
    val userMap: HashMap<Long, UserInfo>?,
    /**
     * 选中回调（反馈 #133「把视频发给好友」）。
     *
     * 非 null 时这是「选好友」模式：点条目把选中结果交给调用方，而不是进会话页，
     * 长按菜单也一并关掉（这里只是挑个人，置顶/删除会话都没意义）。
     * 必须放在 [onSessionsChanged] 之前——它是尾随 lambda 的绑定位，挪到后面会让
     * 现有的 `PrivateMsgSessionsAdapter(ctx, list, map) { … }` 调用点静默绑错参数。
     */
    private val onSessionClick: ((PrivateMsgSession) -> Unit)? = null,
    /**
     * 打开会话页的回调（反馈 #166）。为 null 时退回 `context.startActivity`。
     * 传整条会话而不是 uid：系统会话（26.11.20）没有真实 mid，页面要用它自带的名字做标题。
     * 消息页传进来是为了拿到「发过私信」的结果，好把刚聊过的会话刷到最上面。
     * 必须放在 [onSessionsChanged] 之前——尾随 lambda 绑的是最后一个参数，插到后面会让
     * 现有 `PrivateMsgSessionsAdapter(ctx, list, map) { … }` 调用点静默绑错。
     */
    private val onOpenSession: ((PrivateMsgSession) -> Unit)? = null,
    private val onSessionsChanged: (() -> Unit)? = null,
    /**
     * 打开单会话「聊天设置」的回调（26.11.23 批次②-2）。为 null 时退回
     * [ChatSettingsActivity.start]，只是拿不到返回结果、改完不会立刻重拉列表。
     * 加在最后而不是 [onSessionsChanged] 之前：现有两个调用点都用具名参数，
     * 放末尾不会让任何一处绑错。
     */
    private val onOpenChatSettings: ((PrivateMsgSession) -> Unit)? = null
) : RecyclerView.Adapter<PrivateMsgSessionsAdapter.PrivateMsgSessionsHolder>() {

    private val cardRoundRadius: Int = context.resources.getDimension(R.dimen.card_round).toInt()

    companion object {
        private const val BADGE_TEXT_COLOR = Color.WHITE
        private val BADGE_BG_COLOR = ColorScheme.PRIMARY
        private const val BADGE_TEXT = "  未读 "
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrivateMsgSessionsHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_user_list, parent, false)
        // 玻璃质感：本 cell 是单层 FrameLayout + 写死的 bg_card_rounded（不透明 shape），
        // 不读 materialCardViewStyle，主题覆盖层够不着 → 建 holder 时换成玻璃面板（关闭时不动）。
        // 消息页「私信列表」与「选好友」页共用这个适配器，两处一并生效。
        GlassStyle.applyCardBackground(view)
        return PrivateMsgSessionsHolder(view)
    }

    override fun onBindViewHolder(holder: PrivateMsgSessionsHolder, position: Int) {
        if (position < 0 || position >= sessionsList.size)
            return
        val msgContent = sessionsList[position] ?: return

        try {
            if (msgContent.content != null)
                when (msgContent.contentType) {
                    PrivateMessage.TYPE_TEXT -> {
                        holder.contentText.text = msgContent.content.getString("content")
                    }
                    PrivateMessage.TYPE_PIC -> {
                        holder.contentText.text = UiText.of("[图片消息]")
                    }
                    PrivateMessage.TYPE_VIDEO, PrivateMessage.TYPE_PIC_CARD, PrivateMessage.TYPE_NOMAL_CARD -> {
                        holder.contentText.text = msgContent.content.getString("title")
                    }
                    PrivateMessage.TYPE_TEXT_WITH_VIDEO -> {
                        holder.contentText.text = msgContent.content.getString("reply_content")
                    }
                    PrivateMessage.TYPE_RETRACT -> {
                        holder.contentText.text = UiText.of("[撤回消息]")
                    }
                    else -> {
                        holder.contentText.text = ""
                    }
                }
            else
                holder.contentText.text = ""

            holder.contentText.ellipsize = TextUtils.TruncateAt.END

            // 名字/头像的来源：系统会话（UP主小助手 / 系统通知 / 客服…）的 talker_id 是服务端合成的
            // id，查 user/cards 查不到，只能用会话自带的 account_info（26.11.20 放开系统会话时补的）
            val isSystem = msgContent.isSystem()
            // 粉丝团会话（26.11.23）：talker_id 是粉丝团 id，user/cards 查不到，
            // 名字头像都来自会话自带的 group_name / group_cover
            val isGroup = msgContent.isFanGroup()
            val user = if (isSystem || isGroup) null else userMap?.get(msgContent.talkerUid)
            val baseName = when {
                isSystem -> msgContent.accountName.ifEmpty { UiText.of("B站官方消息") }
                isGroup -> msgContent.groupName.ifEmpty { UiText.of("应援团") }
                user != null -> user.name
                // 普通会话但没查到用户信息：留空，别把上一条的名字留在复用的 View 上
                else -> ""
            }
            // 置顶态没有布尔字段，靠 top_ts 是否非零判断
            val displayName = buildString {
                if (msgContent.isTop()) append("[置顶] ")
                append(baseName)
                // 免打扰标记放在名字后面：手表上名字行是单行省略，标记在后才不至于把名字挤没
                if (msgContent.isDnd()) {
                    append(' ')
                    append(UiText.of("[免打扰]"))
                }
            }
            // 系统会话的未读记在 biz_msg_unread_count 里，只看 unread 的话永远显示 0 条未读
            val unread = msgContent.unreadTotal()
            if (unread > 0 && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVATE_MSG_UNREAD_BADGE_ENABLE, false)) {
                val nameStr = SpannableStringBuilder(displayName)
                val nameLength = displayName.length
                nameStr.append(BADGE_TEXT)
                nameStr.setSpan(
                    RadiusBackgroundSpan(1, cardRoundRadius, BADGE_TEXT_COLOR, BADGE_BG_COLOR),
                    nameLength + 1, nameStr.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )
                holder.nameText.text = nameStr
            } else {
                holder.nameText.text = displayName
            }

            val avatarUrl = when {
                isSystem -> msgContent.accountAvatar
                isGroup -> msgContent.groupCover
                else -> user?.avatar
            }
            if (avatarUrl.isNullOrEmpty()) {
                // 没有头像时也要把占位图塞回去：否则复用的 View 会继续显示上一条会话的头像
                holder.avatarView.setImageResource(R.mipmap.akari)
            } else {
                GlideUtil.with(holder.avatarView).asDrawable().load(GlideUtil.url(avatarUrl))
                    .apply(GlideUtil.thumbOptionsCover(holder.avatarView))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(GlideUtil.placeholderDrawable(holder.avatarView.context, R.mipmap.akari))
                    .error(GlideUtil.placeholderDrawable(holder.avatarView.context, R.mipmap.akari))
                    .apply(RequestOptions.circleCropTransform())
                    .into(holder.avatarView)
            }

            holder.itemView.setOnClickListener {
                // 选好友模式：只把结果交回调用方，不进会话页（反馈 #133）
                val pick = onSessionClick
                if (pick != null) {
                    pick(msgContent)
                } else {
                    val open = onOpenSession
                    if (open != null) open(msgContent)
                    else {
                        val intent = Intent(context, PrivateMsgActivity::class.java)
                        intent.putExtra("uid", msgContent.talkerUid)
                        intent.putExtra(PrivateMsgActivity.EXTRA_MAX_SEQNO, msgContent.maxSeqno)
                        intent.putExtra(PrivateMsgActivity.EXTRA_SESSION_TYPE, msgContent.sessionType)
                        // 系统会话的标题只能用会话自带的账号名（没有真实 mid 可查）
                        if (isSystem) intent.putExtra(PrivateMsgActivity.EXTRA_SYSTEM_NAME, msgContent.accountName)
                        else if (isGroup) intent.putExtra(PrivateMsgActivity.EXTRA_SESSION_NAME, msgContent.groupName)
                        context.startActivity(intent)
                    }
                }
            }
            holder.itemView.setOnLongClickListener {
                // 选好友模式下不给长按菜单：置顶/删除会话对「挑个人发消息」没有意义；
                // 系统会话同样不给：talker_id 是合成的、没有主页可跳，删除还会让官方消息彻底没入口。
                // 粉丝团会话给菜单（26.11.23）：它的未读原来在 App 里清不掉，靠「标为已读」补上
                if (onSessionClick != null || isSystem) {
                    false
                } else {
                    showSessionMenu(msgContent)
                    true
                }
            }
        } catch (err: JSONException) {
            Log.e("PrivateMsgUserAdapter", err.toString())
        }
    }

    /**
     * 会话项长按菜单：置顶/取消置顶、免打扰开关、标为已读、聊天设置、删除会话、查看用户主页。
     *
     * 这几个接口都只改服务端的会话状态（删除不会清聊天记录），成功后统一回调和刷新。
     * 26.11.23 加了免打扰：会话里的 is_dnd 只有服务端知道，本地改完要立刻重绑这一项，
     * 否则要等下一次整表刷新才看得到标记。同日加「聊天设置」：置顶/免打扰/标为已读
     * 这几件常用的留在这里一键切换，推送、拦截、拉黑这类不常动的收进设置页。
     */
    private fun showSessionMenu(session: PrivateMsgSession) {
        // 26.11.23：条目改成「先拼标签再配动作」。原先按下标硬编码 when(0/1/2/else)，
        // 现在「标为已读」只在有未读时出现、粉丝团会话又少两项，按下标会全部错位。
        val labels = ArrayList<String>(6)
        val actions = ArrayList<() -> Unit>(6)

        // 粉丝团会话没有「置顶 / 免打扰 / 主页」这三件事：接口都只支持用户会话
        if (!session.isFanGroup()) {
            labels.add(if (session.isTop()) UiText.of("取消置顶") else UiText.of("置顶会话"))
            actions.add { setSessionTop(session, !session.isTop()) }
            labels.add(if (session.isDnd()) UiText.of("取消免打扰") else UiText.of("消息免打扰"))
            actions.add { setSessionDnd(session, !session.isDnd()) }
        }
        // 有未读才给「标为已读」：没未读时点它没有意义，还多一次请求
        if (session.unreadTotal() > 0) {
            labels.add(UiText.of("标为已读"))
            actions.add { markSessionRead(session) }
        }
        // 单会话的聊天设置（推送 / 置顶 / 免打扰 / 拦截 / 拉黑）。粉丝团会话没有这些接口：
        // get_session_ss、set_push_ss、set_msg_dnd、update_intercept 都写明「仅支持用户会话」。
        if (!session.isFanGroup()) {
            labels.add(UiText.of("聊天设置"))
            actions.add { openChatSettings(session) }
        }
        labels.add(UiText.of("删除会话"))
        actions.add { confirmRemoveSession(session) }
        val dangerIndex = labels.size - 1
        if (!session.isFanGroup()) {
            labels.add(UiText.of("查看用户主页"))
            actions.add { BiliTerminal.jumpToUser(context, session.talkerUid) }
        }

        TerminalDialog.menu(
            context = context,
            title = UiText.of("会话操作"),
            items = labels,
            danger = setOf(dangerIndex)
        ) { which ->
            actions.getOrNull(which)?.invoke()
        }.show()
    }

    /**
     * 打开单会话的「聊天设置」（26.11.23 批次②-2）。
     *
     * 有回调就交给调用方（消息页要用返回结果重拉列表），没有就直接开页。
     */
    private fun openChatSettings(session: PrivateMsgSession) {
        val callback = onOpenChatSettings
        if (callback != null) callback(session) else ChatSettingsActivity.start(context, session)
    }

    /**
     * 把一个会话标为已读（26.11.23）。
     *
     * <p>和会话页里的自动已读是同一个接口：update_ack 的 ack_seqno 取会话的 max_seqno
     * （服务端给的「已读到哪一条」），粉丝团会话要连带 session_type=2，否则服务端不认。
     * 成功后本地把未读清零并只重绑这一项，不必整表重拉。
     */
    private fun markSessionRead(session: PrivateMsgSession) {
        // max_seqno 缺失时退回 ack_seqno（已经在读的那条）；两个都是 0 就没法标
        val seqno = if (session.maxSeqno > 0) session.maxSeqno else session.ackSeqno
        if (seqno <= 0) {
            MsgUtil.showMsg(UiText.of("这条会话没有可标记的消息"))
            return
        }
        CenterThreadPool.run {
            try {
                val result = PrivateMsgApi.updateAck(session.talkerUid, session.sessionType, seqno)
                CenterThreadPool.runOnUiThread {
                    if (result.optInt("code", -1) == 0) {
                        session.unread = 0
                        session.bizUnread = 0
                        val index = sessionsList.indexOfFirst { it.talkerUid == session.talkerUid }
                        if (index >= 0) notifyItemChanged(index)
                        MsgUtil.showMsg(UiText.of("已标为已读"))
                        onSessionsChanged?.invoke()
                    } else {
                        MsgUtil.showMsg(PrivateMsgApi.sessionErrorMsg(result.optInt("code", -1)))
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { MsgUtil.err(e) }
            }
        }
    }

    private fun confirmRemoveSession(session: PrivateMsgSession) {
        TerminalDialog.confirm(
            context = context,
            title = UiText.of("删除会话"),
            message = UiText.of("只会把会话从列表里移除，不会删除聊天记录。"),
            confirmText = UiText.of("删除"),
            onConfirm = { removeSession(session) }
        ).show()
    }

    private fun setSessionTop(session: PrivateMsgSession, top: Boolean) {
        CenterThreadPool.run {
            try {
                val code = PrivateMsgApi.setSessionTop(
                    session.talkerUid, session.sessionType, top
                )
                CenterThreadPool.runOnUiThread {
                    val error = PrivateMsgApi.sessionErrorMsg(code)
                    if (error.isEmpty()) {
                        MsgUtil.showMsg(if (top) UiText.of("已置顶") else UiText.of("已取消置顶"))
                        onSessionsChanged?.invoke()
                    } else {
                        MsgUtil.showMsg(error)
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { MsgUtil.err(e) }
            }
        }
    }

    /**
     * 开关会话免打扰。
     *
     * 接口是 link_setting/set_msg_dnd（不是 session_svr 那一族），参数要自己的 mid 与对方 mid，
     * 口径见 PrivateMsgApi.setMsgDnd。成功后直接改本地对象并只重绑这一项：
     * 省掉一次整表重拉，标记立刻可见。
     */
    private fun setSessionDnd(session: PrivateMsgSession, dnd: Boolean) {
        CenterThreadPool.run {
            try {
                val ownUid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L)
                val code = PrivateMsgApi.setMsgDnd(ownUid, session.talkerUid, dnd)
                CenterThreadPool.runOnUiThread {
                    if (code == 0) {
                        session.dnd = dnd
                        val index = sessionsList.indexOfFirst { it.talkerUid == session.talkerUid }
                        if (index >= 0) notifyItemChanged(index)
                        MsgUtil.showMsg(
                            if (dnd) UiText.of("已开启免打扰") else UiText.of("已取消免打扰")
                        )
                        onSessionsChanged?.invoke()
                    } else {
                        MsgUtil.showMsg(PrivateMsgApi.sessionErrorMsg(code))
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { MsgUtil.err(e) }
            }
        }
    }

    private fun removeSession(session: PrivateMsgSession) {
        CenterThreadPool.run {
            try {
                val code = PrivateMsgApi.removeSession(
                    session.talkerUid, session.sessionType
                )
                CenterThreadPool.runOnUiThread {
                    val error = PrivateMsgApi.sessionErrorMsg(code)
                    if (error.isEmpty()) {
                        val index = sessionsList.indexOfFirst { it.talkerUid == session.talkerUid }
                        if (index >= 0) {
                            sessionsList.removeAt(index)
                            notifyItemRemoved(index)
                        }
                        MsgUtil.showMsg(UiText.of("已从会话列表移除"))
                        onSessionsChanged?.invoke()
                    } else {
                        MsgUtil.showMsg(error)
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { MsgUtil.err(e) }
            }
        }
    }

    override fun getItemCount(): Int {
        return sessionsList.size
    }

    /**
     * 追加一页会话（反馈 #166 上拉加载更多）。
     *
     * 只往后加、不重排已有条目：整表替换会把已显示的项全部重绑一次，滚动位置和图片都要重来一遍。
     * 排序交给调用方先对新页单独做完再调这里，跨页的全局顺序不会因此变化。
     */
    fun appendSessions(more: List<PrivateMsgSession>) {
        if (more.isEmpty()) return
        val start = sessionsList.size
        sessionsList.addAll(more)
        notifyItemRangeInserted(start, more.size)
    }

    class PrivateMsgSessionsHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var avatarView: ImageView
        lateinit var nameText: TextView
        lateinit var contentText: TextView

        init {
            avatarView = itemView.findViewById(R.id.userAvatar)
            nameText = itemView.findViewById(R.id.userName)
            contentText = itemView.findViewById(R.id.userDesc)
        }
    }
}