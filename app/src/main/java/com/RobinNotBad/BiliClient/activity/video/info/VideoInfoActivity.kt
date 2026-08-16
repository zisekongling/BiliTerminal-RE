package com.RobinNotBad.BiliClient.activity.video.info

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.reply.ReplyFragment
import com.RobinNotBad.BiliClient.activity.video.info.BangumiInfoFragment
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter
import com.RobinNotBad.BiliClient.event.CloseAllVideoPagesEvent
import com.RobinNotBad.BiliClient.event.ReplyEvent
import com.RobinNotBad.BiliClient.helper.TutorialHelper
import com.RobinNotBad.BiliClient.util.AnimationUtils
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.bumptech.glide.Glide
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.TimeUnit

class VideoInfoActivity : BaseActivity() {

    private var aid: Long = 0
    private var bvid: String? = null

    private var fragmentList: MutableList<Fragment>? = null
    var replyFragment: ReplyFragment? = null
    var contentFragment: Fragment? = null
    private var seek_reply: Long = -1
    private lateinit var loading: ImageView
    private lateinit var viewPager: ViewPager

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_simple_viewpager)

        Glide.get(BiliTerminal.context).clearMemory()
        val intent = intent
        var type = intent.getStringExtra("type")
        if (type == null) type = "video"
        this.aid = intent.getLongExtra("aid", 114514)
        this.bvid = intent.getStringExtra("bvid")
        this.seek_reply = intent.getLongExtra("seekReply", -1)

        viewPager = findViewById(R.id.viewPager)
        loading = findViewById(R.id.loading)
        setupLongPressToRoot()
        if (type == "media") initMediaInfoView()
        else initVideoInfoView()
    }

    fun initMediaInfoView() {
        setPageName("番剧详情")

        fragmentList = ArrayList(2)
        contentFragment = BangumiInfoFragment.newInstance(aid)
        fragmentList!!.add(contentFragment!!)
        replyFragment = ReplyFragment.newInstance(aid, 1, seek_reply == -1L, seek_reply)
        fragmentList!!.add(replyFragment!!)

        viewPager.offscreenPageLimit = fragmentList!!.size
        val vpfAdapter = ViewPagerFragmentAdapter(supportFragmentManager, fragmentList!!)
        viewPager.adapter = vpfAdapter
        if (seek_reply != -1L) viewPager.currentItem = 1
        if (SharedPreferencesUtil.getBoolean("first_videoinfo", true)) {
            MsgUtil.showMsgLong("提示：本页面可以左右滑动")
            SharedPreferencesUtil.putBoolean("first_videoinfo", false)
        }
    }

    protected fun initVideoInfoView() {
        TutorialHelper.showTutorialList(this, R.array.tutorial_video, 1)
        TutorialHelper.showPagerTutorial(this, 3)

        setPageName("视频详情")
        TerminalContext.getInstance().getVideoInfoByAidOrBvId(aid, bvid).observe(this) { result ->
            result.onSuccess { videoInfo ->
                aid = videoInfo.aid
                bvid = videoInfo.bvid
                fragmentList = ArrayList(3)
                contentFragment = VideoInfoFragment.newInstance(videoInfo.aid, bvid!!)
                fragmentList!!.add(contentFragment!!)
                replyFragment = ReplyFragment.newInstance(videoInfo.aid, 1, videoInfo.stats.reply, seek_reply, videoInfo.staff[0].mid)
                replyFragment!!.setManager(videoInfo.staff)
                fragmentList!!.add(replyFragment!!)
                if (SharedPreferencesUtil.getBoolean("related_enable", true)) {
                    val vrFragment = VideoRcmdFragment.newInstance(videoInfo.aid)
                    fragmentList!!.add(vrFragment)
                }
                viewPager.offscreenPageLimit = fragmentList!!.size
                val vpfAdapter = ViewPagerFragmentAdapter(supportFragmentManager, fragmentList!!)
                viewPager.adapter = vpfAdapter
                if (seek_reply != -1L) viewPager.currentItem = 1
            }.onFailure { error ->
                loading.setImageResource(R.mipmap.loading_2233_error)
                MsgUtil.showMsg("获取信息失败！\n可能是视频不存在？")
                CenterThreadPool.runOnUIThreadAfter(5L, TimeUnit.SECONDS) {
                    MsgUtil.err(error)
                }
            }
        }
    }

    fun setCurrentAid(aid: Long) {
        if (replyFragment != null) runOnUiThread { replyFragment!!.refresh(aid) }
    }

    fun crossFade(fragmentView: View?) {
        AnimationUtils.crossFade(loading, fragmentView)
        fragmentView?.post {
            val scrollView = fragmentView.findViewById<View>(R.id.scrollView)
            if (scrollView != null) {
                scrollView.isFocusable = true
                scrollView.isFocusableInTouchMode = true
                scrollView.requestFocus()
            }
        }
    }

    override fun eventBusEnabled(): Boolean {
        return true
    }

    @Subscribe(threadMode = ThreadMode.ASYNC, sticky = true, priority = 1)
    fun onEvent(event: ReplyEvent) {
        replyFragment!!.notifyReplyInserted(event)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCloseAllVideoPages(event: CloseAllVideoPagesEvent) {
        if (!isFinishing && !isDestroyed) {
            finish()
        }
    }

    private fun setupLongPressToRoot() {
        val topBar = findViewById<View>(R.id.top)
        topBar?.setOnLongClickListener {
            MsgUtil.showMsg("已返回初始页面")
            EventBus.getDefault().post(CloseAllVideoPagesEvent())
            true
        }
    }

}