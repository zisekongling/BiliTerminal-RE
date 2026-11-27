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
import com.RobinNotBad.BiliClient.util.FeedbackState
import com.RobinNotBad.BiliClient.util.FeedbackUnread
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.TimeUtil
import com.google.android.material.card.MaterialCardView

/**
 * 「我的反馈」详情（26.10.16 新增）。
 *
 * <p>展示单条反馈的完整正文、三态（待回复 / 已回复 / 已结束），以及**一条按时间交错的对话时间线**：
 * 开发者回复（服务端写成定向公告）与用户自己追加的说明（`feedback_comment` 表）按时间排在一起。
 * 整条数据由列表页通过 Intent 的 Parcelable extra 传入（[FeedbackItem]），**本页不再发请求拉列表**。
 *
 * <h3>追加说明（26.10.16 新增）</h3>
 * 底部「追加说明」卡片走 [TerminalDialog.input] 弹窗输入，发到服务端的
 * `POST /terminal/feedback/reply`。服务端会校验归属（不是自己的反馈一律 404），
 * 并把该反馈**重新打回「待受理」**——GitHub 的 reopen 语义：你补充了内容，就该重新进开发者的待办。
 *
 * <p>所以本页在发送成功后会同时做三件事：本地追加这条消息、把状态改回「待回复」、滚到末尾。
 * 并 `setResult(RESULT_OK)` 通知列表页重拉（状态变了，列表上那份缓存就过期了）。
 *
 * <h3>状态与结案（26.11.27 新增）</h3>
 * 状态由服务端按对话派生下发（`waiting_dev` / `replied` / `closed`，见 [FeedbackState]），
 * 与开发者内部的 `handled` 正交——`handled` 只是他的待办标记，不代表问题解决了。
 * 「已回复」时状态行下方给一颗「问题已解决？」胶囊，点了调 `POST /terminal/feedback/close`
 * 把这条标成「已结束」；之后只要再追加一条说明，服务端会把它自动重新打开。
 *
 * <h3>长线程折叠（26.11.27 新增）</h3>
 * 消息超过 [COLLAPSE_THRESHOLD] 条时只渲染最后 [COLLAPSED_TAIL] 条，上面留一行「展开全部 N 条」。
 *
 * <h3>为什么继承 BaseActivity 而不是 RefreshListActivity</h3>
 * 这是「二级页面（普通）」——内容一次性给全、不需要下拉刷新与分页，用不上列表模板
 * （`docs/architecture-map.md` §3.2 的选型表）。布局照 `activity_feedback.xml` 的顶栏 + 滚动体。
 */
class FeedbackDetailActivity : BaseActivity() {

    companion object {
        /** 追加说明的长度上限。与服务端 `feedback_reply` 里的 `as_str(..., 2000)` 保持一致。 */
        private const val MAX_NOTE = 2000

        /**
         * 超过这么多条就先折叠（26.11.27 新增）。
         *
         * 手表一屏只装得下三四条消息，长线程（线上最长 19 条）会把「追加说明」卡片顶到很远，
         * 而真正需要看的往往只是最后几句。折叠只影响**显示**，不影响数据。
         */
        private const val COLLAPSE_THRESHOLD = 5

        /** 折叠时保留最后几条。3 条通常正好够看清「我上次说了什么、开发者怎么回的」。 */
        private const val COLLAPSED_TAIL = 3
    }

    /**
     * 当前这条反馈。列表页传进来的 Parcelable 副本，追加成功后只改这一份。
     *
     * 注意 `replies` 在 Parcelable 读回来时已保证非空（见 [FeedbackItem] 的 `CREATOR`），
     * 但仍然在 onCreate 里兜一次 —— 下面直接往里 `add`。
     */
    private var item: FeedbackItem? = null

    /** 发送中。挡住连点造成的重复提交（服务端另有 20 次/小时的限流兜底）。结案也复用这个标志。 */
    private var sending: Boolean = false

    /** 长线程是否已展开。只在本次页面生命周期内有效——退出重进又回到折叠态，符合「只想看最新」的预期。 */
    private var threadExpanded: Boolean = false

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
     * 分类 + 三态那一行，以及它下面那颗动作胶囊。
     *
     * 单独抽出来是因为追加说明与结案都会改状态，成功之后要重画 ——
     * 不重画的话，用户刚说完话却看到旧状态，会以为自己被跳过了。
     */
    private fun renderMeta(data: FeedbackItem) {
        val state = FeedbackState.resolve(data)
        findViewById<TextView>(R.id.detail_meta).text =
            "${categoryLabel(data.category)} · ${FeedbackState.label(state)}"
        renderStateAction(data, state)
    }

    /**
     * 状态行下面那颗胶囊（26.11.27 新增），三种状态三副面孔：
     *
     * - **已回复** → 可点：「问题已解决？」点了弹确认框再结案；
     * - **已结束** → 只作提示、不可点，顺便把「追加说明会重新打开」这句话先说清；
     * - **待回复** → 整块隐藏：开发者还没回，这时劝用户结案只会让人困惑。
     *
     * 描边用主题色手画（工程里没有可复用的「胶囊」drawable，为它新增一套 drawable
     * 要覆盖 7 套主题，不划算）。
     */
    private fun renderStateAction(data: FeedbackItem, state: String) {
        val card = findViewById<MaterialCardView>(R.id.detail_state_action)
        val text = findViewById<TextView>(R.id.detail_state_action_text)
        when (state) {
            FeedbackState.REPLIED -> {
                card.visibility = View.VISIBLE
                card.isClickable = true
                card.strokeWidth = (resources.displayMetrics.density * 1.5f).toInt()
                card.strokeColor = themeColor(androidx.appcompat.R.attr.colorPrimary)
                text.alpha = 1f
                text.text = getString(R.string.feedback_close_entry)
                card.setOnClickListener { confirmClose(data) }
            }

            FeedbackState.CLOSED -> {
                card.visibility = View.VISIBLE
                card.isClickable = false
                card.strokeWidth = 0
                text.alpha = 0.7f
                text.text = getString(R.string.feedback_closed_hint)
                card.setOnClickListener(null)
            }

            else -> {
                card.visibility = View.GONE
                card.setOnClickListener(null)
            }
        }
    }

    /** 结案前的确认框。写成「问题已经解决的话」，是因为「已结束」也可以只是「不想再跟了」。 */
    private fun confirmClose(data: FeedbackItem) {
        TerminalDialog.confirm(
            context = this,
            title = getString(R.string.msg_feedback_close_title),
            message = getString(R.string.msg_feedback_close_body),
            confirmText = getString(R.string.btn_feedback_close_ok),
            cancelText = UiText.of("取消"),
            confirmIsDanger = false
        ) { closeFeedback(data) }.show()
    }

    /**
     * 调 `POST /terminal/feedback/close` 把这条标成「已结束」。
     *
     * 成功后只改本地这份 [FeedbackItem] 的 state 并重画状态行 —— 服务端另有幂等保证，
     * 重复点不会出错；同时 `setResult(RESULT_OK)` 让列表页重拉，列表上的三态跟着变。
     */
    private fun closeFeedback(data: FeedbackItem) {
        if (sending) return
        sending = true
        CenterThreadPool.run {
            val result = TerminalApi.closeFeedback(data.id)
            CenterThreadPool.runOnUiThread {
                sending = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.code < 0) {
                    // 老服务端没有这个路由（404 会被 ApiResult 归一成 -1），这里只提示、不改状态
                    MsgUtil.showMsg(getString(R.string.msg_feedback_close_fail, result.message))
                    return@runOnUiThread
                }
                data.state = FeedbackState.CLOSED
                renderMeta(data)
                setResult(Activity.RESULT_OK)
                MsgUtil.showMsg(getString(R.string.msg_feedback_close_ok))
            }
        }
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

        // 长线程先折叠：手表一屏装不下十几条，而真正要看的是最后几句
        val collapsed = !threadExpanded && replies.size > COLLAPSE_THRESHOLD
        val shown = if (collapsed) replies.takeLast(COLLAPSED_TAIL) else replies
        if (collapsed) container.addView(buildExpandRow(replies.size))

        val inflater = LayoutInflater.from(this)
        for (reply in shown) {
            container.addView(buildReplyView(inflater, container, reply))
        }
    }

    /**
     * 「展开全部 N 条」那一行，只在折叠时出现在时间线最上面。
     *
     * 用纯 [TextView] 而不是卡片：它只是一个开关，不该和消息抢视觉重量。
     * 展开状态只活到本页销毁，退出重进又回到折叠态 —— 符合「只想看最新」的预期。
     */
    private fun buildExpandRow(total: Int): View {
        val view = TextView(this)
        view.text = getString(R.string.feedback_expand_replies, total)
        view.textSize = 12f
        view.setTextColor(themeColor(androidx.appcompat.R.attr.colorPrimary))
        val pad = (resources.displayMetrics.density * 6f).toInt()
        view.setPadding(0, pad, 0, pad)
        view.setOnClickListener {
            threadExpanded = true
            renderThread()
        }
        return view
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
                // 服务端把这条反馈重新打回「待回复」并把 user_state 清零（reopen 语义），
                // 页面上的状态必须跟着变，否则用户会以为自己刚说的话被跳过了。
                data.handled = false
                data.state = FeedbackState.WAITING_DEV
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