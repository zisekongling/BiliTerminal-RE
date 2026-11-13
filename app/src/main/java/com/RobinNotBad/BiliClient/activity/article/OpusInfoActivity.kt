package com.RobinNotBad.BiliClient.activity.article

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicInfoActivity
import com.RobinNotBad.BiliClient.activity.reply.ReplyFragment
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter
import com.RobinNotBad.BiliClient.api.OpusApi
import com.RobinNotBad.BiliClient.event.ReplyEvent
import com.RobinNotBad.BiliClient.model.Opus
import com.RobinNotBad.BiliClient.model.Stats
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.AnimationUtils
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class OpusInfoActivity : BaseActivity() {
    private var oid: Long = 0

    private var replyFragment: ReplyFragment? = null
    private var seek_reply: Long = -1

    private lateinit var loadingView: ImageView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_viewpager)
        val intent = intent
        oid = intent.getLongExtra("id", 114514)
        seek_reply = intent.getLongExtra("seekReply", -1)

        setPageName(UiText.of("文章详情"))
        loadingView = findViewById(R.id.loading)

        val viewPager = findViewById<ViewPager>(R.id.viewPager)

        TerminalContext.getInstance().getOpusById(oid)
            .observe(this) { result ->
                result.onSuccess { opus ->
                    if (opus.type == Opus.TYPE_DYNAMIC_OLD_STYLE) {
                        val intent1 = Intent(this, DynamicInfoActivity::class.java)
                        intent1.putExtra("id", oid)
                        intent1.putExtra("seekReply", seek_reply)
                        startActivity(intent1)
                        finish()
                        return@onSuccess
                    }

                    // 防御：抓取/解析失败时 opus.stats / opus.upInfo 可能为 null，避免 NPE 崩溃
                    if (opus.stats == null) opus.stats = Stats()
                    if (opus.upInfo == null) opus.upInfo = UserInfo()

                    val fragmentList = ArrayList<androidx.fragment.app.Fragment>()

                    val oiFragment = OpusInfoFragment.newInstance(oid)
                    oiFragment.opus = opus
                    fragmentList.add(oiFragment)

                    replyFragment = ReplyFragment.newInstance(opus.commentId, opus.commentType, opus.stats.reply, seek_reply, opus.upInfo.mid)
                    replyFragment!!.setManager(opus.upInfo)
                    fragmentList.add(replyFragment!!)

                    val vpfAdapter = ViewPagerFragmentAdapter(supportFragmentManager, fragmentList)
                    viewPager.adapter = vpfAdapter
                    if (seek_reply != -1L) viewPager.currentItem = 1

                    AnimationUtils.crossFade(loadingView, oiFragment.view)
                }.onFailure { error ->
                    loadingView.setImageResource(R.mipmap.loading_2233_error)
                    // #142：「被风控拦截」和「这篇确实没有正文」要分开说——前者稍后重试有意义，
                    // 后者重试多少次都还是这样，给同一句话只会让人反复点重试。
                    when (error) {
                        is OpusApi.ArticleBlockedException ->
                            MsgUtil.showMsg(getString(R.string.msg_opus_article_blocked))

                        is OpusApi.ArticleEmptyException ->
                            MsgUtil.showMsg(getString(R.string.msg_opus_article_empty))

                        else -> MsgUtil.err(error)
                    }
                }
            }
    }

    override fun eventBusEnabled(): Boolean {
        return true
    }

    @Subscribe(threadMode = ThreadMode.ASYNC, sticky = true, priority = 1)
    fun onEvent(event: ReplyEvent) {
        // 与 activity/dynamic/DynamicInfoActivity.kt 对齐：发评论后把新评论插进评论列表。
        // 此前这里是空函数体，文章/Opus 页发完评论看不到自己的评论（动态页可以）。
        // ReplyFragment.notifyReplyInserted 内部会按 oid 过滤 + 切主线程，重复插入不是问题。
        replyFragment?.notifyReplyInserted(event)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}