package com.RobinNotBad.BiliClient.activity.dynamic.send

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImagePickerActivity
import com.RobinNotBad.BiliClient.activity.EmoteActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder
import com.RobinNotBad.BiliClient.adapter.video.VideoCardHolder
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.api.EmoteApi
import com.RobinNotBad.BiliClient.api.ImageApi
import com.RobinNotBad.BiliClient.model.Dynamic
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.model.VoteDraft
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.ScheduleTimePolicy
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.TimeUtil
import com.RobinNotBad.BiliClient.util.view.TextButtonColors
import com.RobinNotBad.BiliClient.util.view.TextInsertion
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import org.json.JSONArray
import java.io.Serializable
import java.util.Calendar
import java.util.HashMap
import java.util.regex.Pattern

class SendDynamicActivity : BaseActivity() {

    private lateinit var editText: EditText
    private lateinit var voteEditArea: LinearLayout
    private lateinit var voteTitleEdit: EditText
    private lateinit var voteOptionsList: LinearLayout
    private lateinit var addOptionBtn: MaterialButton
    private lateinit var removeVoteBtn: MaterialButton
    private lateinit var addPicText: TextView
    private lateinit var addTimerText: TextView
    /** 「仅自己可见」勾选框（发布选项，只在普通发布时露出）。 */
    private lateinit var optPrivatePub: MaterialCheckBox
    /** 「关闭评论」勾选框（发布选项，只在普通发布时露出）。 */
    private lateinit var optCloseComment: MaterialCheckBox
    private lateinit var picsPreview: LinearLayout
    private var voteDraft: VoteDraft? = null
    private val optionEditTexts = mutableListOf<EditText>()
    private var hasVote: Boolean = false

    /**
     * 定时发布的目标时间戳（秒级）；0 表示不定时。
     *
     * <p>这里只负责收集用户意图，真正拼进 {@code option.timer_pub_time} 是在
     * `DynamicActivity.writeDynamicLauncher` 里（发布链路统一在那边）。
     */
    private var timerPubTime: Long = 0L

    /** 已选待上传的图片。 */
    private val imageUris = mutableListOf<Uri>()
    /** 防止连点发送导致重复上传/重复发布。 */
    private var sending: Boolean = false

    /**
     * 编辑模式的目标动态 id；> 0 表示这条动态是「编辑」而不是「发布」。
     *
     * <p>编辑与发布共用这个页面：编辑只改正文（B 站的编辑接口只吃 text/pic，
     * 投票与转发卡片都改不了），成功后把新正文回传给发起方刷新列表。
     */
    private var editDynId: Long = -1L

    private val emoteLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.resultCode
        val data = result.data
        if (code == RESULT_OK && data != null && data.hasExtra("text")) {
            editText.append(data.getStringExtra("text"))
        }
    }

    /**
     * 选图。沿用本项目既有的图片选择方式（ACTION_GET_CONTENT 单选），
     * 不学上游的 GetMultipleContents：手表上一次选多张的体验与兼容性都没把握。
     */
    private val pickImageLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            if (imageUris.size >= ImageApi.MAX_IMAGE_COUNT) {
                MsgUtil.showMsg(UiText.fmt("最多%1\$d张图片", ImageApi.MAX_IMAGE_COUNT))
                return@registerForActivityResult
            }
            imageUris.add(uri)
            renderPicsPreview()
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_send_dynamic) { layoutView, resId ->

            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                setResult(RESULT_CANCELED)
                finish()
                MsgUtil.showMsg(UiText.of("还没有登录喵~"))
            }

            editText = findViewById(R.id.editText)
            val send = findViewById<MaterialCardView>(R.id.send)
            val addVote = findViewById<MaterialCardView>(R.id.add_vote)
            val addPic = findViewById<MaterialCardView>(R.id.add_pic)
            val addTimer = findViewById<MaterialCardView>(R.id.add_timer)
            // 发布选项（仅自己可见 / 关闭评论）：不收进 addTimer 那张卡片，
            // 单独一张卡，避免"定时"与"可见性"混在一起让用户以为是同一个开关
            val publishOptions = findViewById<MaterialCardView>(R.id.publish_options)
            optPrivatePub = findViewById(R.id.opt_private_pub)
            optCloseComment = findViewById(R.id.opt_close_comment)
            addPicText = findViewById(R.id.add_pic_text)
            addTimerText = findViewById(R.id.add_timer_text)
            picsPreview = findViewById(R.id.pics_preview)

            // 手表输入法把回车当「完成 / 下一项」，打不出换行，只能靠这张卡往光标处插一个
            findViewById<View>(R.id.newline).setOnClickListener {
                TextInsertion.insertNewline(editText)
            }

            // 投票编辑区
            voteEditArea = findViewById(R.id.vote_edit_area)
            voteTitleEdit = findViewById(R.id.vote_title_edit)
            voteOptionsList = findViewById(R.id.vote_options_list)
            addOptionBtn = findViewById(R.id.add_option_btn)
            removeVoteBtn = findViewById(R.id.remove_vote_btn)
            // TextButton 状态列表里的 ?attr 在 API 21/22 解析不了（会退成框架哨兵红），在这里统一兜住
            TextButtonColors.apply(addOptionBtn, removeVoteBtn)

            // 编辑模式：预填原正文，并且不理会 TerminalContext——它只在 onDestroy 清，
            // 里面可能残留上一次转发的内容，会把旧卡片画到编辑页上
            editDynId = intent.getLongExtra("edit_dyn_id", -1L)
            val editing = editDynId > 0L
            if (editing) {
                editText.setText(intent.getStringExtra("edit_text").orEmpty())
                editText.setSelection(editText.text.length)
            }

            val extraCard = findViewById<FrameLayout>(R.id.forwardCard)
            var video: VideoInfo? = null
            var forward: Dynamic? = null
            val forwardContent = if (editing) null else TerminalContext.getInstance().getForwardContent()
            if (forwardContent is VideoInfo) {
                video = forwardContent
            } else {
                forward = forwardContent as Dynamic?
            }
            if (forward != null) {
                val childCard = View.inflate(this, R.layout.cell_dynamic, extraCard)
                val holder = DynamicHolder(childCard, this, false)
                holder.showDynamic(this, forward, false)
            } else if (video != null) {
                val holder = VideoCardHolder(LayoutInflater.from(this).inflate(R.layout.cell_video_list, extraCard))
                holder.showVideoCard(video.toCard(), this)
            }

            // 转发场景不允许带图：转发的是别人的内容，再挂自己的图语义不成立，B 站也不接受
            val normalPublish = !editing && forward == null && video == null
            addPic.visibility = if (normalPublish) View.VISIBLE else View.GONE
            // 定时发布只在普通发布链路上有意义：转发走 relayDynamic（没有 option），编辑接口也没有定时字段
            addTimer.visibility = if (normalPublish) View.VISIBLE else View.GONE
            // 可见范围/关闭评论同理：转发与编辑都没有 option 字段，露出来只会让用户以为生效了
            publishOptions.visibility = if (normalPublish) View.VISIBLE else View.GONE
            // 编辑不带投票：编辑接口改不了 attach_card，留着入口只会让用户以为能改
            if (editing) addVote.visibility = View.GONE

            addPic.setOnClickListener {
                // 内置选图页（反馈 #53），页内可退回系统选择器
                pickImageLauncher.launch(ImagePickerActivity.intent(this))
            }

            // 定时发布：手表上不方便输入日期时间，只给几个固定档 + 明天中午 + 不定时
            addTimer.setOnClickListener { showTimerPicker() }

            // 添加投票按钮点击
            addVote.setOnClickListener {
                if (!hasVote) {
                    hasVote = true
                    voteEditArea.visibility = View.VISIBLE
                    addVote.visibility = View.GONE
                    // 默认添加两个选项
                    if (optionEditTexts.isEmpty()) {
                        addOptionEdit()
                        addOptionEdit()
                    }
                }
            }

            // 添加选项按钮
            addOptionBtn.setOnClickListener {
                if (optionEditTexts.size < 10) {
                    addOptionEdit()
                } else {
                    MsgUtil.showMsg(UiText.of("最多10个选项"))
                }
            }

            // 移除投票按钮
            removeVoteBtn.setOnClickListener {
                clearVoteEdit()
                hasVote = false
                voteEditArea.visibility = View.GONE
                addVote.visibility = View.VISIBLE
            }

            send.setOnClickListener {
                if (!SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.cookie_refresh, true)) {
                    MsgUtil.showDialog(getString(R.string.text_cannot_send), getString(R.string.msg_cookie_refresh_failed), -1)
                    return@setOnClickListener
                }
                if (sending) {
                    MsgUtil.showMsg(UiText.of("正在发送中"))
                    return@setOnClickListener
                }
                val text = editText.text.toString()

                // 发布选项：在本线程（UI 线程）快照，带图链路的上传在后台线程跑，别在那边读 View
                val sendPrivatePub = optPrivatePub.isChecked
                val sendCloseComment = optCloseComment.isChecked

                // 编辑模式：直接在页内调编辑接口，不走 DynamicActivity 的发布链路
                if (editing) {
                    submitEdit(text)
                    return@setOnClickListener
                }

                // 定时发布：提交前再校验一次窗口（+6 分钟 ~ +7 天）。
                // 选档时已经校验过，但用户可能选完又放了很久才点发送，时间会滑出窗口。
                if (!ensureTimerValid()) return@setOnClickListener

                // 处理投票草稿：投票区开着但内容不合法时，collectVoteDraft 已经提示过了，
                // 这里必须中止发送——否则会把投票悄悄丢掉、只发出正文
                val draft = collectVoteDraft()
                if (hasVote && draft == null) return@setOnClickListener

                // 带图与投票不能同时发：本项目的投票走 attach_card，与 pics 叠加未经验证，宁可不发
                if (imageUris.isNotEmpty() && draft != null) {
                    MsgUtil.showMsg(UiText.of("带图动态暂不支持同时发投票"))
                    return@setOnClickListener
                }

                if (imageUris.isEmpty()) {
                    val result = Intent()
                    val bundle = this@SendDynamicActivity.intent.extras
                    if (bundle != null) result.putExtras(bundle)
                    result.putExtra("text", text)
                    putPublishOptions(result, sendPrivatePub, sendCloseComment)
                    if (draft != null) {
                        result.putExtra("voteDraft", draft as Serializable)
                    }
                    if (timerPubTime > 0) result.putExtra("timerPubTime", timerPubTime)
                    setResult(RESULT_OK, result)
                    finish()
                    return@setOnClickListener
                }

                // 带图：图片必须先上传图床换成 URL，再回传给 DynamicActivity 组装 pics
                sending = true
                MsgUtil.showMsg(UiText.of("正在上传图片..."))
                val toUpload = ArrayList(imageUris)   // 快照，避免上传期间用户又删图导致并发修改
                CenterThreadPool.run {
                    try {
                        val pics = JSONArray()
                        for (uri in toUpload) {
                            val prepared = ImageApi.prepareImage(this@SendDynamicActivity, uri)
                            val uploaded = ImageApi.uploadImage(
                                prepared.data, prepared.fileName, prepared.mimeType, ImageApi.BIZ_DYNAMIC
                            ).getOrThrow()
                            pics.put(uploaded.toDynamicPicJson())
                        }
                        val result = Intent()
                        val bundle = this@SendDynamicActivity.intent.extras
                        if (bundle != null) result.putExtras(bundle)
                        result.putExtra("text", text)
                        result.putExtra("pics", pics.toString())
                        putPublishOptions(result, sendPrivatePub, sendCloseComment)
                        if (timerPubTime > 0) result.putExtra("timerPubTime", timerPubTime)
                        runOnUiThread {
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            sending = false
                            MsgUtil.err(e)
                        }
                    }
                }
            }

            findViewById<View>(R.id.emote).setOnClickListener {
                emoteLauncher.launch(Intent(this, EmoteActivity::class.java).putExtra("from", EmoteApi.BUSINESS_DYNAMIC))
            }
        }
    }

    /**
     * 把两个发布选项开关的状态塞进回传 Intent。
     *
     * <p>只在用户真的勾上时才带 {@code true}：`DynamicActivity.writeDynamicLauncher`
     * 按「缺省即不勾」读取，再交给 `DynamicApi.buildPublishOption` 决定是否下发
     * `private_pub` / `close_comment`。两个都不勾时整条链路与改造前完全一致。
     *
     * <p>状态在 UI 线程快照后传进来：带图链路的上传在后台线程跑，不该在那边再读 View。
     *
     * <p>不做状态记忆：私密/关评论是「这一条」的属性，跨会话记住会让用户
     * 在下次发动态时不知不觉又发成仅自己可见（或关掉评论）。
     */
    private fun putPublishOptions(result: Intent, privatePub: Boolean, closeComment: Boolean) {
        if (privatePub) result.putExtra("privatePub", true)
        if (closeComment) result.putExtra("closeComment", true)
    }

    /**
     * 定时发布的时间选择。
     *
     * <p>手表屏幕小、没有键盘友好的日期输入，项目里也没有 DatePicker/TimePicker 的先例，
     * 所以默认只给「几分钟后」这种固定档 + 明天 12:00，点一下就定。
     *
     * <p>26.11.06 批次 3（task-16）：固定档之外补一个「自定义时间…」入口（日期+时间文本输入），
     * 并且所有入口都过一遍 [ScheduleTimePolicy] 的窗口校验——B 站只接受
     * 「当前时间 +6 分钟 ~ +7 天」，窗口外的定时会被服务端拒绝。
     */
    private fun showTimerPicker() {
        val now = System.currentTimeMillis() / 1000
        val items = arrayOf(
            UiText.of("10 分钟后"), UiText.of("30 分钟后"), UiText.of("1 小时后"),
            UiText.of("2 小时后"), UiText.of("明天 12:00"),
            getString(R.string.data_s_timer_custom), UiText.of("不定时")
        )
        TerminalDialog.menu(
            context = this,
            title = UiText.of("定时发布"),
            items = items.toList()
        ) { which ->
            when (which) {
                0 -> applyTimer(DynamicApi.timerSecondsAt(now, 10))
                1 -> applyTimer(DynamicApi.timerSecondsAt(now, 30))
                2 -> applyTimer(DynamicApi.timerSecondsAt(now, 60))
                3 -> applyTimer(DynamicApi.timerSecondsAt(now, 120))
                4 -> applyTimer(tomorrowNoonSeconds())
                5 -> showCustomTimerInput()
                else -> applyTimer(0L)
            }
        }.show()
    }

    /**
     * 自定义定时时间：文本输入「yyyy-MM-dd HH:mm」（也接受 `:` 秒与 `/` 日期分隔符）。
     *
     * <p>解析用 [ScheduleTimePolicy.parse]，越界日期（如 13 月 40 日）与格式错都归到具体错误，
     * 选中时立刻给反馈，而不是等到发送才发现。
     */
    private fun showCustomTimerInput() {
        val now = System.currentTimeMillis() / 1000
        val initial = ScheduleTimePolicy.format(
            if (timerPubTime > 0) timerPubTime else ScheduleTimePolicy.defaultInputSeconds(now)
        )
        TerminalDialog.input(
            context = this,
            title = getString(R.string.data_s_timer_custom_title),
            hint = getString(R.string.data_s_timer_input_hint),
            initialText = initial,
            confirmText = UiText.of("确定")
        ) { text ->
            val validation = ScheduleTimePolicy.parse(text, System.currentTimeMillis() / 1000)
            if (!validation.valid) {
                val reason = scheduleErrorText(validation.error)
                if (reason.isNotEmpty()) MsgUtil.showMsg(reason)
                return@input
            }
            // 成功提示由 applyTimer 统一给出（与固定档一致），这里不再重复弹
            applyTimer(validation.targetSeconds)
        }.show()
    }

    /** 明天 12:00 的秒级时间戳。 */
    private fun tomorrowNoonSeconds(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / 1000
    }

    private fun applyTimer(seconds: Long) {
        timerPubTime = seconds
        updateTimerText()
        if (seconds > 0) MsgUtil.showMsg(UiText.of("已设为定时发布"))
    }

    /**
     * 提交前的定时窗口校验。
     *
     * <p>不合法时提示具体原因并返回 false——调用方据此直接 return，**不发起任何请求**。
     */
    private fun ensureTimerValid(): Boolean {
        if (ScheduleTimePolicy.isUnset(timerPubTime)) return true
        val validation = ScheduleTimePolicy.validate(timerPubTime, System.currentTimeMillis() / 1000)
        if (validation.valid) return true
        val reason = scheduleErrorText(validation.error)
        if (reason.isNotEmpty()) MsgUtil.showMsg(reason)
        return false
    }

    /** 把校验失败原因翻成用户可读的一句话；合法/未设置返回空串。 */
    private fun scheduleErrorText(error: ScheduleTimePolicy.Error): String = when (error) {
        ScheduleTimePolicy.Error.INVALID_FORMAT -> getString(R.string.data_s_timer_invalid_format)
        ScheduleTimePolicy.Error.PAST -> getString(R.string.data_s_timer_past)
        ScheduleTimePolicy.Error.TOO_SOON -> getString(R.string.data_s_timer_too_soon)
        ScheduleTimePolicy.Error.TOO_LATE -> getString(R.string.data_s_timer_too_late)
        else -> ""
    }

    @SuppressLint("SetTextI18n")
    private fun updateTimerText() {
        addTimerText.text = if (timerPubTime > 0) {
            UiText.of("定时：") + TimeUtil.format(timerPubTime * 1000, "MM-dd HH:mm")
        } else {
            UiText.of("定时发布")
        }
    }

    /**
     * 编辑模式下的提交。
     *
     * <p>只提交正文：B 站的动态编辑接口接受 content/pic，但本项目里投票走 attach_card、
     * 图片要重新上传（编辑接口的 pics 语义与发布不完全一致），都不在本次范围内，
     * 所以编辑页把这两个入口藏掉了。
     *
     * <p>@ 与表情的组装方式与发布完全一致，直接复用 [DynamicApi.buildContents]。
     */
    private fun submitEdit(text: String) {
        if (text.isBlank()) {
            MsgUtil.showMsg(UiText.of("正文不能为空"))
            return
        }
        val dynId = editDynId
        sending = true
        MsgUtil.showMsg(UiText.of("正在保存…"))
        CenterThreadPool.run {
            try {
                val atUids = HashMap<String, Long>()
                val matcher = Pattern.compile("@(\\S+)\\s").matcher(text)
                while (matcher.find()) {
                    val matchedString = matcher.group(1)
                    val uid: Long
                    if (DynamicApi.mentionAtFindUser(matchedString).also { uid = it } != -1L) {
                        atUids[matchedString] = uid
                    }
                }
                val contents = DynamicApi.buildContents(text, atUids.ifEmpty { null },
                    EmoteApi.getEmoteTexts(EmoteApi.BUSINESS_DYNAMIC))
                val code = DynamicApi.editDynamic(dynId, contents, null, null, null, 1)
                runOnUiThread {
                    if (code == 0) {
                        val result = Intent()
                        result.putExtra("edit_dyn_id", dynId)
                        result.putExtra("text", text)
                        setResult(RESULT_OK, result)
                        MsgUtil.showMsg(UiText.of("修改成功~"))
                        finish()
                    } else {
                        sending = false
                        MsgUtil.showMsg(DynamicApi.editErrorMsg(code).ifEmpty { UiText.fmt("修改失败（%1\$s）", code) })
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    sending = false
                    MsgUtil.err(e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TerminalContext.getInstance().setForwardContent(null)
    }

    /**
     * 重绘已选图片预览。点缩略图可以移除该图。
     */
    private fun renderPicsPreview() {
        picsPreview.removeAllViews()
        if (imageUris.isEmpty()) {
            picsPreview.visibility = View.GONE
        } else {
            picsPreview.visibility = View.VISIBLE
            val size = (resources.displayMetrics.density * 56).toInt()
            for (uri in imageUris) {
                val imageView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = 4 }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setOnClickListener {
                        imageUris.remove(uri)
                        renderPicsPreview()
                    }
                }
                Glide.with(this).load(uri).centerCrop().into(imageView)
                picsPreview.addView(imageView)
            }
        }
        addPicText.text =
            if (imageUris.isEmpty()) UiText.of("添加图片") else UiText.fmt("添加图片(%1\$d)", imageUris.size)
    }

    /**
     * 添加一个选项编辑输入框
     */
    private fun addOptionEdit() {
        val index = optionEditTexts.size + 1
        val editText = EditText(this).apply {
            hint = UiText.fmt("选项 %1\$d", index)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setBackgroundResource(R.drawable.background_edittext)
            setPadding(8, 4, 8, 4)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 4
            }
        }
        voteOptionsList.addView(editText)
        optionEditTexts.add(editText)
    }

    /**
     * 收集投票草稿
     */
    private fun collectVoteDraft(): VoteDraft? {
        if (!hasVote) return null
        val title = voteTitleEdit.text.toString().trim()
        val options = optionEditTexts.map { it.text.toString().trim() }.filter { it.isNotEmpty() }
        if (title.isEmpty()) {
            MsgUtil.showMsg(UiText.of("请填写投票标题"))
            return null
        }
        if (options.size < 2) {
            MsgUtil.showMsg(UiText.of("至少需要2个选项"))
            return null
        }
        val draft = VoteDraft()
        draft.title = title
        draft.options = options.toMutableList()
        return draft
    }

    /**
     * 清空投票编辑区
     */
    private fun clearVoteEdit() {
        voteTitleEdit.text.clear()
        voteOptionsList.removeAllViews()
        optionEditTexts.clear()
        voteDraft = null
    }
}