package com.RobinNotBad.BiliClient.activity.feedback

import com.RobinNotBad.BiliClient.util.UiText

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.TerminalApi
import com.RobinNotBad.BiliClient.model.FeedbackItem
import com.RobinNotBad.BiliClient.model.FeedbackReply
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FeedbackUnread
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.TimeUtil
import com.google.android.material.card.MaterialCardView

/**
 * 「我的反馈」详情（26.10.16 新增）。
 *
 * <p>展示单条反馈的完整正文、状态（待受理 / 已受理），以及**一条按时间交错的对话时间线**：
 * 开发者回复（服务端写成定向公告）与用户自己追加的说明（`feedback_comment` 表）按时间排在一起。
 * 整条数据由列表页通过 Intent 的 Parcelable extra 传入（[FeedbackItem]），**本页不再发请求拉列表**。
 *
 * <h3>追加说明（26.10.16 新增）</h3>
 * 底部「追加说明」卡片走 [TerminalDialog.input] 弹窗输入，发到服务端的
 * `POST /terminal/feedback/reply`。服务端会校验归属（不是自己的反馈一律 404），
 * 并把该反馈**重新打回「待受理」**——GitHub 的 reopen 语义：你补充了内容，就该重新进开发者的待办。
 *
 * <p>所以本页在发送成功后会同时做三件事：本地追加这条消息、把状态改回「待受理」、滚到末尾。
 * 并 `setResult(RESULT_OK)` 通知列表页重拉（状态变了，列表上那份缓存就过期了）。
 *
 * <h3>为什么继承 BaseActivity 而不是 RefreshListActivity</h3>
 * 这是「二级页面（普通）」——内容一次性给全、不需要下拉刷新与分页，用不上列表模板
 * （`docs/architecture-map.md` §3.2 的选型表）。布局照 `activity_feedback.xml` 的顶栏 + 滚动体。
 */
class FeedbackDetailActivity : BaseActivity() {

    companion object {
        /** 追加说明的长度上限。与服务端 `feedback_reply` 里的 `as_str(..., 2000)` 保持一致。 */
        private const val MAX_NOTE = 2000
    }

    /**
     * 当前这条反馈。列表页传进来的 Parcelable 副本，追加成功后只改这一份。
     *
     * 注意 `replies` 在 Parcelable 读回来时已保证非空（见 [FeedbackItem] 的 `CREATOR`），
     * 但仍然在 onCreate 里兜一次 —— 下面直接往里 `add`。
     */
    private var item: FeedbackItem? = null

    /** 发送中。挡住连点造成的重复提交（服务端另有 20 次/小时的限流兜底）。 */
    private var sending: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.getParcelableExtra<FeedbackItem>("feedback")
        if (data == null) {
            // 没有数据就没有可展示的内容（正常入口一定会带），直接退出而不是留一个空壳页
            finish()
            return
        }
        if (data.replies == null) data.replies = ArrayList()
        item = data
        // 26.11.16：打开详情就算「看过」这条反馈的开发者回复——水位线推到最新一条，
        // 「我的反馈」列表上的「新回复」标签与设置入口的计数随之下一次刷新消失。
        FeedbackUnread.markSeen(data)

        asyncInflate(R.layout.activity_feedback_detail) { _, _ ->
            setPageName(UiText.of("反馈详情"))

            renderMeta(data)
            findViewById<TextView>(R.id.detail_time).text = data.ctime
            findViewById<TextView>(R.id.detail_content).text = data.content

            val contactView = findViewById<TextView>(R.id.detail_contact)
            if (data.contact.isNullOrEmpty()) {
                contactView.visibility = View.GONE
            } else {
                contactView.visibility = View.VISIBLE
                contactView.text = "联系方式：${data.contact}"
            }

            findViewById<View>(R.id.add_note_card).setOnClickListener { openNoteDialog("") }

            renderThread()
        }
    }

    /**
     * 分类 + 状态那一行。
     *
     * 单独抽出来是因为追加说明会把状态打回「待受理」，发送成功后要重画这一行 ——
     * 不重画的话，用户刚说完话却看到「已受理」，会以为自己被跳过了。
     */
    private fun renderMeta(data: FeedbackItem) {
        findViewById<TextView>(R.id.detail_meta).text =
            "${categoryLabel(data.category)} · ${if (data.handled) UiText.of("已受理") else UiText.of("待受理")}"
    }

    /** 重画整条对话时间线。消息很少（多为 0~5 条），直接清空重建，不必为它单写一个 adapter。 */
    private fun renderThread() {
        val data = item ?: return
        val section = findViewById<LinearLayout>(R.id.reply_section)
        val container = findViewById<LinearLayout>(R.id.reply_container)
        val replies = data.replies.orEmpty()

        container.removeAllViews()
        if (replies.isEmpty()) {
            // 一条都没有时整块隐藏，别留一个空白标题
            section.visibility = View.GONE
            return
        }
        section.visibility = View.VISIBLE
        // 只有开发者回复时「开发者回复」更具体；一旦有来回，「对话记录」才准确
        findViewById<TextView>(R.id.reply_header).text =
            if (replies.any { it.isFromUser() }) UiText.of("对话记录") else UiText.of("开发者回复")

        val inflater = LayoutInflater.from(this)
        for (reply in replies) {
            container.addView(buildReplyView(inflater, container, reply))
        }
    }

    /**
     * 一条消息。两侧共用 `cell_feedback_reply`，靠标题文字 + 用户侧的描边区分：
     *
     * - **文字**是主判据（「我的补充」vs 开发者的标题），它在任何主题下都读得出来；
     * - **描边**只是让人扫一眼就分得清哪几句是自己说的 —— 时间线一长，纯文字要逐行读才知道是谁。
     */
    private fun buildReplyView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        reply: FeedbackReply
    ): View {
        val view = inflater.inflate(R.layout.cell_feedback_reply, parent, false)
        val fromUser = reply.isFromUser()

        view.findViewById<TextView>(R.id.reply_title).text =
            if (fromUser) UiText.of("我的补充") else reply.title.ifEmpty { UiText.of("开发者回复") }
        view.findViewById<TextView>(R.id.reply_time).text = reply.ctime
        view.findViewById<TextView>(R.id.reply_content).text = reply.content

        if (fromUser) {
            (view as MaterialCardView).apply {
                strokeWidth = (resources.displayMetrics.density * 1.5f).toInt()
                strokeColor = themeColor(androidx.appcompat.R.attr.colorPrimary)
            }
        }
        return view
    }

    /**
     * 弹出「追加说明」输入框。
     *
     * @param initial 预填内容。用于**发送失败后重开**：弹窗的 onConfirm 是在关闭之后才回调的
     * （见 [TerminalDialog.input]），拿不到「失败了就别关」这个中间态，所以只能用
     * 「重开 + 原样填回」来保证用户写的内容不丢 —— 与 `FeedbackActivity` 失败时保留输入框内容
     * 是同一个原则。
     */
    private fun openNoteDialog(initial: String) {
        val data = item ?: return
        if (sending) return
        TerminalDialog.input(
            context = this,
            title = UiText.of("追加说明"),
            hint = UiText.of("补充复现步骤或说明…"),
            initialText = initial,
            maxLength = MAX_NOTE,
            confirmText = UiText.of("发送"),
            cancelText = UiText.of("取消")
        ) { text -> sendNote(data, text) }.show()
    }

    /**
     * 发送一条追加说明。
     *
     * 成功后**本地追加**而不是重新拉一遍：省一次往返，而且用户刚打完字，立刻看到它出现在
     * 时间线末尾才符合预期（重拉期间会有一段空白）。本地这条用服务端返回的真实 id，
     * 时间用本机当前时间、格式与服务端同一口径（`yyyy-MM-dd HH:mm`），下次进页面以服务端为准。
     */
    private fun sendNote(data: FeedbackItem, text: String) {
        sending = true
        MsgUtil.showMsg(UiText.of("正在发送…"))
        CenterThreadPool.run {
            val result = TerminalApi.submitFeedbackReply(data.id, text)
            runOnUiThread {
                sending = false
                if (isDestroyed) return@runOnUiThread
                if (result.code < 0) {
                    MsgUtil.showMsg("发送失败：${result.message}")
                    openNoteDialog(text)
                    return@runOnUiThread
                }

                data.replies.add(
                    FeedbackReply(
                        result.code,
                        "",
                        text,
                        TimeUtil.formatDateTime(System.currentTimeMillis()),
                        FeedbackReply.AUTHOR_USER
                    )
                )
                // 服务端把这条反馈重新打回「待受理」（reopen 语义），页面上的状态必须跟着变，
                // 否则用户会以为自己刚说的话被当成「已受理」跳过了。
                data.handled = false
                renderMeta(data)
                renderThread()
                scrollToBottom()
                // 列表页那份是进详情前的快照，状态已经变了，让它回来时重拉一次
                setResult(Activity.RESULT_OK)
                MsgUtil.showMsg(UiText.of("已发送，开发者会看到"))
            }
        }
    }

    /** 滚到时间线末尾，让刚发出去的那条进入视野。等一帧再滚，否则新加的 View 还没测量完。 */
    private fun scrollToBottom() {
        val scroll = findViewById<ScrollView>(R.id.detail_scroll) ?: return
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    /**
     * 取当前主题的属性色。
     *
     * 写法与 `TerminalDialog.Sheet.attrColor` 一致（`obtainStyledAttributes` + 兜底色）：
     * 万一日后新增主题漏了某个 attr，拿不到值时退回可读的兜底色，而不是抛异常。
     */
    private fun themeColor(attr: Int): Int =
        obtainStyledAttributes(intArrayOf(attr)).let { ta ->
            val color = ta.getColor(0, Color.WHITE)
            ta.recycle()
            color
        }

    /** 与 FeedbackListAdapter 同一份映射（复用 `strings.xml` 的 `feedback_category_*`）。 */
    private fun categoryLabel(value: String): String = when (value) {
        TerminalApi.CATEGORY_BUG -> getString(R.string.feedback_category_bug)
        TerminalApi.CATEGORY_SUGGESTION -> getString(R.string.feedback_category_suggestion)
        TerminalApi.CATEGORY_CONTENT -> getString(R.string.feedback_category_content)
        TerminalApi.CATEGORY_PERFORMANCE -> getString(R.string.feedback_category_performance)
        else -> getString(R.string.feedback_category_other)
    }
}