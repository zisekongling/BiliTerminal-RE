package com.RobinNotBad.BiliClient.activity.dynamic

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.reply.ReplyFragment
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter
import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.event.ReplyEvent
import com.RobinNotBad.BiliClient.helper.TutorialHelper
import com.RobinNotBad.BiliClient.util.AnimationUtils
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class DynamicInfoActivity : BaseActivity() {

    private var rFragment: ReplyFragment? = null
    private var seek_reply: Long = -1

    @SuppressLint("MissingInflatedId", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_viewpager)
        seek_reply = intent.getLongExtra("seekReply", -1)

        val intent = intent
        val id = intent.getLongExtra("id", 0)

        val pageName = findViewById<TextView>(R.id.pageName)
        pageName.text = "动态详情"

        TutorialHelper.showTutorialList(this, R.array.tutorial_dynamic_info, 6)
        TerminalContext.getInstance().getDynamicById(id)
            .observe(this) { dynamicResult ->
                dynamicResult.onSuccess { dynamic ->
                    val fragmentList = ArrayList<androidx.fragment.app.Fragment>()
                    val diFragment = DynamicInfoFragment.newInstance(id)
                    fragmentList.add(diFragment)
                    rFragment = ReplyFragment.newInstance(dynamic.comment_id, dynamic.comment_type, dynamic.stats.reply, seek_reply, dynamic.userInfo.mid)
                    rFragment!!.setManager(dynamic.userInfo)
                    rFragment!!.replyType = ReplyApi.REPLY_TYPE_DYNAMIC
                    fragmentList.add(rFragment!!)
                    val vpfAdapter = ViewPagerFragmentAdapter(supportFragmentManager, fragmentList)
                    val viewPager = findViewById<ViewPager>(R.id.viewPager)
                    viewPager.adapter = vpfAdapter
                    val view = diFragment.view
                    if (view != null) view.visibility = View.GONE
                    if (seek_reply != -1L) viewPager.currentItem = 1

                    AnimationUtils.crossFade(findViewById(R.id.loading), diFragment.view)
                    diFragment.view!!.post {
                        val scrollView = diFragment.view!!.findViewById<View>(R.id.scrollView)
                        scrollView.isFocusable = true
                        scrollView.isFocusableInTouchMode = true
                        scrollView.requestFocus()
                    }
                    TutorialHelper.showPagerTutorial(this, 2)
                }.onFailure { e ->
                    MsgUtil.err(e)
                    (findViewById<View>(R.id.loading) as ImageView).setImageResource(R.mipmap.loading_2233_error)
                }
            }
    }

    override fun eventBusEnabled(): Boolean {
        return true
    }

    @Subscribe(threadMode = ThreadMode.ASYNC, sticky = true, priority = 1)
    fun onEvent(event: ReplyEvent) {
        rFragment!!.notifyReplyInserted(event)
    }

    override fun onDestroy() {
        TerminalContext.getInstance().leaveDetailPage()
        super.onDestroy()
    }
}