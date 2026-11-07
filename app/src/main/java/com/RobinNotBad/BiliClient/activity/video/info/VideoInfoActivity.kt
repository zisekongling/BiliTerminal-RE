package com.RobinNotBad.BiliClient.activity.video.info

import com.RobinNotBad.BiliClient.util.UiText

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
import com.RobinNotBad.BiliClient.util.AnimationUtils
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.OptionalFeatures
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.TopbarLongPressPrefs
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

    /** 番剧详情第 3 页（角色）；可选私有功能不提供时为 null */
    private var actorsFragment: Fragment? = null
    private var seek_reply: Long = -1
    private lateinit var loading: ImageView
    private lateinit var viewPager: ViewPager

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_simple_viewpager)

        Glide.get(BiliTerminal.context!!).clearMemory()
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
        setPageName(UiText.of("番剧详情"))

        fragmentList = ArrayList(3)
        contentFragment = BangumiInfoFragment.newInstance(aid)
        fragmentList!!.add(contentFragment!!)
        replyFragment = ReplyFragment.newInstance(aid, 1, seek_reply == -1L, seek_reply)
        fragmentList!!.add(replyFragment!!)
        // 第 3 页角色：由可选私有功能提供；不在场时保持原来的 2 页
        val actors = OptionalFeatures.mediaActorsFragment(aid)
        if (actors != null) {
            actorsFragment = actors
            fragmentList!!.add(actors)
        }

        viewPager.offscreenPageLimit = fragmentList!!.size
        val vpfAdapter = ViewPagerFragmentAdapter(supportFragmentManager, fragmentList!!)
        viewPager.adapter = vpfAdapter
        registerPageNameListener(UiText.of("番剧详情"))
        if (seek_reply != -1L) viewPager.currentItem = 1
        if (SharedPreferencesUtil.getBoolean("first_videoinfo", true)) {
            MsgUtil.showMsgLong(UiText.of("提示：本页面可以左右滑动"))
            SharedPreferencesUtil.putBoolean("first_videoinfo", false)
        }
    }

    protected fun initVideoInfoView() {
        // 视频详情页的教程与翻页提示都由 BaseActivity 按 Tutorials 注册表集中触发，这里不再手动调用
        setPageName(UiText.of("视频详情"))
        TerminalContext.getInstance().getVideoInfoByAidOrBvId(aid, bvid).observe(this) { result ->
            result.onSuccess { videoInfo ->
                aid = videoInfo.aid
                bvid = videoInfo.bvid
                fragmentList = ArrayList(3)
                contentFragment = VideoInfoFragment.newInstance(videoInfo.aid, bvid!!)
                fragmentList!!.add(contentFragment!!)
                // staff 可能是空列表：联合投稿的 staff 数组为空、或稿件 UP 已注销导致
                // VideoInfoApi 拿不到 owner 时，都会留下空 staff。这里直接取 [0] 会
                // IndexOutOfBoundsException（上游 26f1742 修的"无UP主视频详情页闪退"）。
                // 拿不到 UP 时传 0，ReplyFragment 侧按"没有 UP"处理即可。
                val upMid = if (videoInfo.staff.isNullOrEmpty()) 0L else videoInfo.staff[0].mid
                replyFragment = ReplyFragment.newInstance(videoInfo.aid, 1, videoInfo.stats.reply, seek_reply, upMid)
                replyFragment!!.setManager(videoInfo.staff)
                fragmentList!!.add(replyFragment!!)
                if (SharedPreferencesUtil.getBoolean("related_enable", true)) {
                    val vrFragment = VideoRcmdFragment.newInstance(videoInfo.aid)
                    fragmentList!!.add(vrFragment)
                }
                viewPager.offscreenPageLimit = fragmentList!!.size
                val vpfAdapter = ViewPagerFragmentAdapter(supportFragmentManager, fragmentList!!)
                viewPager.adapter = vpfAdapter
                registerPageNameListener(UiText.of("视频详情"))
                if (seek_reply != -1L) viewPager.currentItem = 1
            }.onFailure { error ->
                loading.setImageResource(R.mipmap.loading_2233_error)
                MsgUtil.showMsg(UiText.of("获取信息失败！\n可能是视频不存在？"))
                CenterThreadPool.runOnUIThreadAfter(5L, TimeUnit.SECONDS) {
                    MsgUtil.err(error)
                }
            }
        }
    }

    /**
     * 标题随页面变化：视频详情-简介 / 视频详情-评论 / 视频详情-相关推荐
     * 番剧详情同理。子标题按当前页的 Fragment 类型判断，兼容相关推荐页可选的情况
     */
    private fun registerPageNameListener(mainTitle: String) {
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                updatePageName(mainTitle, position)
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
        updatePageName(mainTitle, viewPager.currentItem)
    }

    private fun updatePageName(mainTitle: String, position: Int) {
        val list = fragmentList
        if (list == null || position < 0 || position >= list.size) {
            setPageName(mainTitle)
            return
        }
        val sub = when {
            list[position] is ReplyFragment -> UiText.of("评论")
            list[position] is VideoRcmdFragment -> UiText.of("相关推荐")
            // 角色页是可选私有功能提供的，标题也从门面取（公开构建里这一页根本不存在）
            list[position] === actorsFragment ->
                OptionalFeatures.mediaActorsPageTitle(this) ?: UiText.of("角色")
            else -> UiText.of("简介")
        }
        setPageName("$mainTitle-$sub")
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

    /** 顶栏长按由本页自己接管（「关闭所有详情页」），基类不再安装「打开菜单」的通用监听器。 */
    override fun handlesTopbarLongPress(): Boolean = true

    private fun setupLongPressToRoot() {
        val topBar = findViewById<View>(R.id.top)
        topBar?.setOnLongClickListener {
            // 三模式：只有「不响应」模式才吞掉手势。这里同样必须返回 true 而不是 false——
            // 返回 false 时 Android 会在抬手时把长按回落成一次单击，而本页顶栏单击是「退出当前页」，
            // 用户要的是「长按什么都不做」（OPPO 系手表的长按顶部是系统控制中心手势）。
            if (!TopbarLongPressPrefs.isEnabledForVideoPages()) return@setOnLongClickListener true
            MsgUtil.showMsg(UiText.of("已返回初始页面"))
            EventBus.getDefault().post(CloseAllVideoPagesEvent())
            true
        }
    }

}