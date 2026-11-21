package com.RobinNotBad.BiliClient.activity.dynamic

import com.RobinNotBad.BiliClient.util.UiText

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
        pageName.text = UiText.of("动态详情")

        // 教程改由 BaseActivity 按 Tutorials 注册表集中触发
        TerminalContext.getInstance().getDynamicById(id)
            .observe(this) { dynamicResult ->
                dynamicResult.onSuccess { dynamic ->
                    // 防御：动态不存在（DYNAMIC_TYPE_NONE）时 DynamicApi.analyzeDynamic 会在填充
                    // 统计信息之前提前返回，stats 保持 null；部分动态类型本身也没有 module_stat。
                    // DynamicHolder 渲染卡片时对 dynamic.stats 判空（约 :888），这里少了一处，
                    // 直接取 dynamic.stats.reply 会 NPE（26.10.05 线上崩溃）。判据有 JVM 单测。
                    dynamic.ensureDetailFields()

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
                    // 防御：Fragment 的视图由 FragmentPagerAdapter 的 commitNow 同步创建，正常都取得到，
                    // 但上面 :63 已经按可空处理过，这里不该反手用 !!（取不到时静默跳过聚焦即可）。
                    view?.let { diView ->
                        diView.post {
                            val scrollView = diView.findViewById<View>(R.id.scrollView) ?: return@post
                            scrollView.isFocusable = true
                            scrollView.isFocusableInTouchMode = true
                            scrollView.requestFocus()
                        }
                    }
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

}