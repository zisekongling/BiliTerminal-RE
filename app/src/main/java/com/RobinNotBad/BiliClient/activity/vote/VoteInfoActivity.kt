package com.RobinNotBad.BiliClient.activity.vote

import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.VoteApi
import com.RobinNotBad.BiliClient.model.VoteInfo
import com.RobinNotBad.BiliClient.model.VoteOption
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog

import org.json.JSONException

import java.io.IOException

/**
 * 投票详情页面
 * 显示完整投票信息，支持投票操作
 */
class VoteInfoActivity : BaseActivity() {

    companion object {
        const val EXTRA_VOTE_ID = "vote_id"
    }

    private lateinit var voteTitle: TextView
    private lateinit var voteDesc: TextView
    private lateinit var voteJoinNum: TextView
    private lateinit var voteStatus: TextView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var voteCard: MaterialCardView
    private lateinit var voteHint: TextView
    private lateinit var voteSubmitBtn: com.google.android.material.button.MaterialButton
    private lateinit var voteDeleteBtn: com.google.android.material.button.MaterialButton

    private var voteId: Long = 0
    private var voteInfo: VoteInfo? = null
    private var selectedOptions = mutableListOf<Int>()
    private var isVoting = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vote_info)

        voteId = intent.getLongExtra(EXTRA_VOTE_ID, 0)
        if (voteId == 0L) {
            MsgUtil.showMsg(UiText.of("投票ID无效"))
            finish()
            return
        }

        setPageName(UiText.of("投票详情"))

        // 初始化视图
        voteTitle = findViewById(R.id.vote_title)
        voteDesc = findViewById(R.id.vote_desc)
        voteJoinNum = findViewById(R.id.vote_join_num)
        voteStatus = findViewById(R.id.vote_status)
        optionsContainer = findViewById(R.id.vote_options_container)
        voteCard = findViewById(R.id.vote_card)
        voteHint = findViewById(R.id.vote_hint)
        voteSubmitBtn = findViewById(R.id.vote_submit_btn)
        voteDeleteBtn = findViewById(R.id.vote_delete_btn)

        // 删除投票按钮：仅投票发布者可删除
        voteDeleteBtn.setOnClickListener {
            if (isVoting) return@setOnClickListener
            confirmDeleteVote()
        }

        // 投票按钮：提交多选结果
        voteSubmitBtn.setOnClickListener {
            if (isVoting) return@setOnClickListener
            val info = voteInfo ?: return@setOnClickListener
            if (selectedOptions.isEmpty()) {
                MsgUtil.showMsg(UiText.of("请先选择选项"))
                return@setOnClickListener
            }
            if (selectedOptions.size < info.choice_cnt) {
                MsgUtil.showMsg(UiText.fmt("还需选择 %1\$d 项", info.choice_cnt - selectedOptions.size))
                return@setOnClickListener
            }
            doVote(info, ArrayList(selectedOptions))
        }

        // 加载投票信息
        loadVoteInfo()
    }

    /**
     * 从网络加载投票信息
     */
    private fun loadVoteInfo() {
        CenterThreadPool.run {
            try {
                val info = VoteApi.getVoteInfo(voteId)
                if (info == null) {
                    runOnUiThread {
                        MsgUtil.showMsg(UiText.of("投票信息获取失败"))
                        finish()
                    }
                    return@run
                }
                voteInfo = info
                runOnUiThread {
                    bindVoteInfo(info)
                }
            } catch (e: IOException) {
                runOnUiThread {
                    MsgUtil.err(UiText.of("网络错误"), e)
                    finish()
                }
            } catch (e: JSONException) {
                runOnUiThread {
                    MsgUtil.err(UiText.of("数据解析错误"), e)
                    finish()
                }
            }
        }
    }

    /**
     * 绑定投票信息到UI
     */
    @SuppressLint("SetTextI18n")
    private fun bindVoteInfo(info: VoteInfo) {
        voteTitle.text = info.title
        if (info.desc.isNotEmpty()) {
            voteDesc.text = info.desc
            voteDesc.visibility = View.VISIBLE
        } else {
            voteDesc.visibility = View.GONE
        }

        // 参与人数
        voteJoinNum.text = UiText.fmt("%1\$s 人参与", info.join_num)
        if (info.isExpired()) {
            voteJoinNum.append(UiText.of(" · 已结束"))
        }

        // 状态标签
        val hasVoted = info.hasVoted()
        when {
            info.isExpired() -> voteStatus.text = UiText.of("已结束")
            hasVoted -> voteStatus.text = UiText.of("已投票")
            else -> voteStatus.text = UiText.of("投票中")
        }
        voteStatus.setTextColor(
            when {
                hasVoted || info.isExpired() -> ColorScheme.TEXT_TERTIARY
                else -> ColorScheme.PRIMARY
            }
        )

        // 已投票：恢复选中项；未投票：清空选中
        selectedOptions.clear()
        if (hasVoted) {
            selectedOptions.addAll(info.my_votes)
        }

        // 渲染选项
        renderOptions(info)
    }

    /**
     * 渲染投票选项列表
     */
    private fun renderOptions(info: VoteInfo) {
        optionsContainer.removeAllViews()

        val canVote = !info.isExpired() && !info.hasVoted()
        // 已投过：从 my_votes 恢复选中项
        val votedIdx = if (info.hasVoted()) info.my_votes.toSet() else emptySet()
        // 多选模式
        val multiChoice = info.choice_cnt > 1
        // 计算总得票数（用于比例显示）
        val totalCnt = info.options.sumOf { it.cnt }

        info.options.forEach { option ->
            val optionView = LayoutInflater.from(this)
                .inflate(R.layout.item_vote_option, optionsContainer, false)
            val indicator = optionView.findViewById<TextView>(R.id.option_indicator)
            val textView = optionView.findViewById<TextView>(R.id.option_text)

            textView.text = option.opt_desc

            // 已投票或投票结束后显示各选项得票数和比例
            val showResult = !canVote && totalCnt > 0
            if (showResult) {
                val percent = (option.cnt * 100f / totalCnt).toInt()
                textView.text = UiText.fmt("%1\$s  %2\$s票 (%3\$s%%)", option.opt_desc, option.cnt, percent)
            }

            // 已选中状态（已投 / 多选时临时选中）
            val isSelected = votedIdx.contains(option.opt_idx) || selectedOptions.contains(option.opt_idx)
            if (isSelected) {
                indicator.text = if (multiChoice) "☑" else "●"
                indicator.setTextColor(ColorScheme.PRIMARY)
                textView.setTextColor(ColorScheme.PRIMARY)
            }

            // 图片投票 - 裁剪成小缩略图，与文字、按钮并排显示
            if (!option.img_url.isNullOrEmpty()) {
                val thumb = optionView.findViewById<ImageView>(R.id.option_thumb)
                thumb.visibility = View.VISIBLE
                Glide.with(this)
                    .load(option.img_url)
                    .apply(GlideUtil.thumbOptionsCover(thumb))
                    .apply(RequestOptions().format(DecodeFormat.PREFER_RGB_565).centerCrop())
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(thumb)
            }

            // 点击选项：单选/多选统一 toggle 选中，由确认按钮提交
            if (canVote) {
                optionView.isClickable = true
                optionView.isFocusable = true
                optionView.setBackgroundResource(R.drawable.item_vote_bg_selector)
                optionView.setOnClickListener {
                    if (isVoting) return@setOnClickListener
                    if (selectedOptions.contains(option.opt_idx)) {
                        selectedOptions.remove(option.opt_idx)
                    } else {
                        if (selectedOptions.size >= info.choice_cnt) {
                            MsgUtil.showMsg(UiText.fmt("最多选择 %1\$d 项", info.choice_cnt))
                            return@setOnClickListener
                        }
                        selectedOptions.add(option.opt_idx)
                    }
                    renderOptions(info)
                }
            }

            optionsContainer.addView(optionView)
        }

        // 是否为自己发布的投票（只能看结果，可删除，不能投）
        val isOwner = info.vote_publisher == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
        voteDeleteBtn.visibility = if (isOwner) View.VISIBLE else View.GONE

        // 单选/多选统一显示提示和确认按钮（自己发布的投票不显示投票按钮）
        if (isOwner) {
            voteHint.visibility = View.GONE
            voteSubmitBtn.visibility = View.GONE
        } else if (canVote) {
            voteHint.visibility = View.VISIBLE
            voteHint.text = if (selectedOptions.isEmpty()) {
                UiText.fmt("请选择 %1\$d 项", info.choice_cnt)
            } else {
                UiText.fmt("请选择 %1\$d 项（已选 %2\$d）", info.choice_cnt, selectedOptions.size)
            }
            voteSubmitBtn.visibility = View.VISIBLE
            voteSubmitBtn.isEnabled = true
            voteSubmitBtn.text = UiText.of("确认投票")
        } else if (info.hasVoted()) {
            voteHint.visibility = View.GONE
            voteSubmitBtn.visibility = View.VISIBLE
            voteSubmitBtn.isEnabled = false
            voteSubmitBtn.text = UiText.of("已投票")
        } else {
            voteHint.visibility = View.GONE
            voteSubmitBtn.visibility = View.VISIBLE
            voteSubmitBtn.isEnabled = false
            voteSubmitBtn.text = UiText.of("已结束")
        }
    }

    /**
     * 执行投票
     *
     * @param info   投票信息
     * @param votes  选中的选项索引列表（opt_idx）
     */
    private fun doVote(info: VoteInfo, votes: List<Int>) {
        // 未登录时提示
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
            MsgUtil.showMsg(UiText.of("请先登录"))
            return
        }
        isVoting = true
        CenterThreadPool.run {
            try {
                val result = VoteApi.doVote(voteId, votes)
                if (!isDestroyed) {
                    runOnUiThread {
                        if (result == 0) {
                            MsgUtil.showMsg(UiText.of("投票成功~"))
                            // 本地立即标记已投，避免依赖接口返回 my_votes 导致可重复投票
                            info.my_votes.clear()
                            info.my_votes.addAll(votes)
                            runOnUiThread { bindVoteInfo(info) }
                        } else {
                            val msg = when (result) {
                                -111 -> UiText.of("需要重新登录")
                                else -> UiText.fmt("投票失败：%1\$s", result)
                            }
                            MsgUtil.showMsg(msg)
                        }
                        isVoting = false
                    }
                }
            } catch (e: Exception) {
                if (!isDestroyed) {
                    runOnUiThread {
                        MsgUtil.err(e)
                        isVoting = false
                    }
                }
            }
        }
    }

    /**
     * 删除投票（仅投票发布者）
     */
    private fun confirmDeleteVote() {
        TerminalDialog.confirm(
            context = this,
            title = UiText.of("删除投票"),
            message = UiText.of("确定删除这个投票吗？删除后不可恢复。"),
            confirmText = UiText.of("删除")
        ) {
            isVoting = true
            CenterThreadPool.run {
                try {
                    val result = VoteApi.deleteVote(voteId)
                    if (!isDestroyed) {
                        runOnUiThread {
                            if (result == 0) {
                                MsgUtil.showMsg(UiText.of("删除成功"))
                                finish()
                            } else {
                                MsgUtil.showMsg(UiText.fmt("删除失败：%1\$s", result))
                            }
                            isVoting = false
                        }
                    }
                } catch (e: Exception) {
                    if (!isDestroyed) {
                        runOnUiThread {
                            MsgUtil.err(e)
                            isVoting = false
                        }
                    }
                }
            }
        }.show()
    }
}