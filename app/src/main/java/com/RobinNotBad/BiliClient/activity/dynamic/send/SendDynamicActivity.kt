package com.RobinNotBad.BiliClient.activity.dynamic.send

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.EmoteActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder
import com.RobinNotBad.BiliClient.adapter.video.VideoCardHolder
import com.RobinNotBad.BiliClient.api.EmoteApi
import com.RobinNotBad.BiliClient.model.Dynamic
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.model.VoteDraft
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.Serializable

class SendDynamicActivity : BaseActivity() {

    private lateinit var editText: EditText
    private lateinit var voteEditArea: LinearLayout
    private lateinit var voteTitleEdit: EditText
    private lateinit var voteOptionsList: LinearLayout
    private lateinit var addOptionBtn: MaterialButton
    private lateinit var removeVoteBtn: MaterialButton
    private var voteDraft: VoteDraft? = null
    private val optionEditTexts = mutableListOf<EditText>()
    private var hasVote: Boolean = false

    private val emoteLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.resultCode
        val data = result.data
        if (code == RESULT_OK && data != null && data.hasExtra("text")) {
            editText.append(data.getStringExtra("text"))
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_send_dynamic) { layoutView, resId ->

            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                setResult(RESULT_CANCELED)
                finish()
                MsgUtil.showMsg("还没有登录喵~")
            }

            editText = findViewById(R.id.editText)
            val send = findViewById<MaterialCardView>(R.id.send)
            val addVote = findViewById<MaterialCardView>(R.id.add_vote)

            // 投票编辑区
            voteEditArea = findViewById(R.id.vote_edit_area)
            voteTitleEdit = findViewById(R.id.vote_title_edit)
            voteOptionsList = findViewById(R.id.vote_options_list)
            addOptionBtn = findViewById(R.id.add_option_btn)
            removeVoteBtn = findViewById(R.id.remove_vote_btn)

            val extraCard = findViewById<FrameLayout>(R.id.forwardCard)
            var video: VideoInfo? = null
            var forward: Dynamic? = null
            if (TerminalContext.getInstance().getForwardContent() is VideoInfo) {
                video = TerminalContext.getInstance().getForwardContent() as VideoInfo
            } else {
                forward = TerminalContext.getInstance().getForwardContent() as Dynamic?
            }
            if (forward != null) {
                val childCard = View.inflate(this, R.layout.cell_dynamic, extraCard)
                val holder = DynamicHolder(childCard, this, false)
                holder.showDynamic(this, forward, false)
            } else if (video != null) {
                val holder = VideoCardHolder(LayoutInflater.from(this).inflate(R.layout.cell_video_list, extraCard))
                holder.showVideoCard(video.toCard(), this)
            }

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
                    MsgUtil.showMsg("最多10个选项")
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
                if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.cookie_refresh, true)) {
                    val text = editText.text.toString()
                    val result = Intent()
                    val bundle = this@SendDynamicActivity.intent.extras
                    if (bundle != null) result.putExtras(bundle)
                    result.putExtra("text", text)

                    // 处理投票草稿
                    val draft = collectVoteDraft()
                    if (draft != null) {
                        result.putExtra("voteDraft", draft as Serializable)
                    }

                    setResult(RESULT_OK, result)
                    finish()
                } else
                    MsgUtil.showDialog("无法发送", "上一次的Cookie刷新失败了，\n您可能需要重新登录以进行敏感操作", -1)
            }

            findViewById<View>(R.id.emote).setOnClickListener {
                emoteLauncher.launch(Intent(this, EmoteActivity::class.java).putExtra("from", EmoteApi.BUSINESS_DYNAMIC))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TerminalContext.getInstance().setForwardContent(null)
    }

    /**
     * 添加一个选项编辑输入框
     */
    private fun addOptionEdit() {
        val index = optionEditTexts.size + 1
        val editText = EditText(this).apply {
            hint = "选项 $index"
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
            MsgUtil.showMsg("请填写投票标题")
            return null
        }
        if (options.size < 2) {
            MsgUtil.showMsg("至少需要2个选项")
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