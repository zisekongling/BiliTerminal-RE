package com.RobinNotBad.BiliClient.activity.article

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.message.ChooseFriendActivity
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
import com.RobinNotBad.BiliClient.util.ShareSheet
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.view.ShareTexts
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class OpusInfoActivity : BaseActivity() {
    private var oid: Long = 0

    private var replyFragment: ReplyFragment? = null
    private var seek_reply: Long = -1

    private lateinit var loadingView: ImageView

    /** 已加载的详情：分享要用它的标题和（专栏的）cv 号；数据回来之前点分享只提示链接还没准备好。 */
    private var opus: Opus? = null

    /**
     * 「分享至消息」选好友页的回传（26.11.24）。
     *
     * 与视频页那条路一致：只认 RESULT_OK + data，用户直接返回就当没选、不弹任何提示；
     * uid 拿不到也不发。区别只在于这里是 Activity（[ShareSheet.requestFriend] 只收 Fragment）。
     */
    private val chooseFriendLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val uid = data.getLongExtra(ChooseFriendActivity.EXTRA_UID, 0L)
            if (uid <= 0L) return@registerForActivityResult
            val url = shareUrl()
            if (url.isEmpty()) {
                MsgUtil.showMsg(UiText.of(getString(R.string.msg_share_url_missing)))
                return@registerForActivityResult
            }
            // 昵称只用于「已发送给 xxx」的提示，拿不到留空不影响发送
            ShareSheet.sendLinkToFriend(
                this,
                uid,
                data.getStringExtra(ChooseFriendActivity.EXTRA_NAME).orEmpty(),
                shareTitle(),
                url
            )
        }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 与共用的 activity_simple_viewpager 只差一个顶栏分享按钮（见 activity_opus_info.xml 的说明）
        setContentView(R.layout.activity_opus_info)
        val intent = intent
        oid = intent.getLongExtra("id", 114514)
        seek_reply = intent.getLongExtra("seekReply", -1)

        setPageName(UiText.of("文章详情"))
        loadingView = findViewById(R.id.loading)

        findViewById<View>(R.id.btn_share).setOnClickListener { showSharePanel() }

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

                    this.opus = opus
                    // 顶栏标题换成文章标题；标题为空时保留「文章详情」这个占位
                    val pageTitle = StringUtil.removeHtml(opus.title) ?: ""
                    if (pageTitle.isNotBlank()) setPageName(pageTitle)

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

    /**
     * 顶栏「分享」面板（26.11.24）——四项：复制链接 / 其它app打开 / 分享文章 / 分享至消息。
     *
     * 专栏页**没有**「分享至动态」：文章转发成动态要走 SendDynamicActivity 的文章上下文，
     * 那一层还没做（现有 setForwardContent 只认视频），按「没有实现依据的条目直接不显示」省掉。
     */
    private fun showSharePanel() {
        val url = shareUrl()
        if (url.isEmpty()) {
            // 详情还没回来（或 id 异常）时拼不出链接：只提示，不弹一个点了全失败的半空菜单
            MsgUtil.showMsg(UiText.of(getString(R.string.msg_share_url_missing)))
            return
        }
        ShareSheet.panel(this)
            .copyLink(url)
            .openInOtherApp(url)
            .shareText(R.string.share_article, ShareTexts.messageText(shareTitle(), url), url)
            .toMessage {
                val intent = Intent()
                intent.setClass(this, ChooseFriendActivity::class.java)
                chooseFriendLauncher.launch(intent)
            }
            .show()
    }

    /**
     * 这篇内容对应的网页链接。
     *
     * cv 号的判断依据（Opus 里没有单独的 cvId 字段，专栏的 cv 号就落在 Opus.id 上）：
     * 1. OpusApi.getOpus 一开始把 opus.id 设成传入的 id，只有 id <= 1 亿才会判成 Opus.TYPE_ARTICLE，
     *    超过 1 亿那个是图文动态的 opus id，不是 cv 号；
     * 2. 走正文兜底时 OpusApi 内部 applyArticleInfo 会用 article.id 覆盖 opus.id，
     *    而 ArticleApi.getArticle(id) 读的就是 read/cv{id}，这条路上落下来的同样是 cv 号。
     *
     * 所以 Opus.TYPE_ARTICLE 时可以把 opus.id 直接当 cv 号拼 read/cv…；
     * 图文动态则退到 opus/{id}——链接至少打得开，不会分享出一个不存在的 cv 地址。
     */
    private fun shareUrl(): String {
        val page = opus ?: return ""
        if (page.type == Opus.TYPE_ARTICLE) {
            val articleUrl = ShareTexts.articleUrl(page.id)
            if (articleUrl.isNotEmpty()) return articleUrl
        }
        return ShareTexts.opusUrl(oid.toString())
    }

    /** 分享文本里的标题：去掉 HTML 标签/实体，没拿到详情时为空串（此时只发链接）。 */
    private fun shareTitle(): String {
        val page = opus ?: return ""
        return StringUtil.removeHtml(page.title) ?: ""
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