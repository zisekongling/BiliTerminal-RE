package com.RobinNotBad.BiliClient.activity.message

import com.RobinNotBad.BiliClient.util.UiText

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView

import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.adapter.message.PrivateMsgSessionsAdapter
import com.RobinNotBad.BiliClient.api.MessageApi
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.model.PrivateMsgSession
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.ui.widget.RotaryNestedScrollView
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgNotifier
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SessionSorter
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView

import org.json.JSONObject

class MessageActivity : InstanceActivity() {
    private lateinit var sessionsView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    /** 这一页真正的滚动容器：RecyclerView 在里面被撑成整页高，滚动由它负责（见 onCreate） */
    private lateinit var scrollView: RotaryNestedScrollView

    /** 会话数据源。适配器跟这里共用同一个 ArrayList，追加页时直接往里 add 就行 */
    private var sessionsList = ArrayList<PrivateMsgSession>()
    private var userMap = HashMap<Long, UserInfo>()
    private var sessionAdapter: PrivateMsgSessionsAdapter? = null

    /** 服务端是否表示还有更多会话（`data.has_more`） */
    private var hasMore = false

    /** 正在拉下一页。滚动监听会连着回调很多次，靠它防重复触发 */
    private var isLoadingMore = false

    /** 下一页游标：已加载会话里最老的 session_ts（微秒）。0 表示还没成功拉过第一页 */
    private var nextEndTs = 0L

    /** 「没有更多会话了」只提示一次，别每次滚到底都弹 */
    private var endTipShown = false

    /**
     * 整表重拉的代次。发完私信返回的刷新、下拉刷新、长按置顶/删除后的刷新可能撞车，
     * 晚到的旧响应靠它丢掉，免得把新数据又盖回旧的。
     */
    private var loadGeneration = 0

    /**
     * Android 13+ 弹通知需要 POST_NOTIFICATIONS 运行时权限；没授权时
     * [MsgNotifier.notifyNewMessages] 会因 `areNotificationsEnabled()` 为 false 直接放弃。
     * 放在消息页请求最自然：用户此刻正在看消息，能理解为什么要通知。
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * 会话页返回（反馈 #166：给某人发完私信退出后，那个人该重新排到最上面）。
     *
     * 只在会话页回 RESULT_OK 时重拉：会话页只在"真的发出消息"时才置结果
     * （见 PrivateMsgActivity.writeMsgLauncher 与 WriteReplyActivity 的发送成功分支），
     * 单纯点进去看一眼就返回不会触发，所以不用无条件 onResume 重拉——那种写法每次返回
     * 都要整表替换一次（闪一下、丢滚动位置、多一个网络往返），代价白付。
     */
    private val sessionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) loadSessions(scrollToListTop = true)
        }

    @SuppressLint("SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        asyncInflate(R.layout.activity_message) { _, _ ->
            swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
            // 下拉刷新只作为失败后的重试入口：首屏加载期间禁用，加载失败时才打开（见 loadSessions）。
            // 没有这个监听时，isEnabled 打开也拉不动，用户只能退出去重进。
            swipeRefreshLayout.setOnRefreshListener { loadSessions() }
            swipeRefreshLayout.isEnabled = false
            swipeRefreshLayout.isRefreshing = true

            val settingBtn = findViewById<MaterialCardView>(R.id.setting_btn)
            settingBtn.setOnClickListener {
                val intent = Intent(this, MessageSettingsActivity::class.java)
                startActivity(intent)
            }

            val reply = findViewById<MaterialCardView>(R.id.reply)
            reply.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, NoticeActivity::class.java)
                intent.putExtra("type", "reply")
                startActivity(intent)
                (findViewById<TextView>(R.id.reply_text)).text = UiText.of("回复我的")
            }

            val like = findViewById<MaterialCardView>(R.id.like)
            like.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, NoticeActivity::class.java)
                intent.putExtra("type", "like")
                startActivity(intent)
                (findViewById<TextView>(R.id.like_text)).text = UiText.of("收到的赞")
            }

            val at = findViewById<MaterialCardView>(R.id.at)
            at.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, NoticeActivity::class.java)
                intent.putExtra("type", "at")
                startActivity(intent)
                (findViewById<TextView>(R.id.at_text)).text = UiText.of("@我")
            }

            val system = findViewById<MaterialCardView>(R.id.system)
            system.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, NoticeActivity::class.java)
                intent.putExtra("type", "system")
                startActivity(intent)
            }

            sessionsView = findViewById(R.id.sessions_list)
            sessionsView.isNestedScrollingEnabled = false

            // 这一页实际的滚动容器是外层 NestedScrollView：里面的 RecyclerView 高度是
            // match_parent，被 ScrollView 用 UNSPECIFIED 量成整页高，自己滚不动。
            // 所以「加载更多」挂在它的滚动上，而不是 RecyclerView 上（反馈 #166）。
            scrollView = findViewById(R.id.scrollView)
            scrollView.isFocusable = true
            scrollView.isFocusableInTouchMode = true
            scrollView.requestFocus()
            scrollView.setOnScrollChangeListener(
                NestedScrollView.OnScrollChangeListener { _: NestedScrollView, _: Int, _: Int, _: Int, _: Int ->
                    maybeLoadMoreIfNearBottom()
                }
            )

            loadSessions()

            // 教程改由 BaseActivity 按 Tutorials 注册表集中触发
        }
    }

    /**
     * 请求通知权限（仅 Android 13+）。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * 拉第一页会话并刷新界面。置顶/删除会话后由适配器回调再次调用（服务端才是唯一真相）。
     *
     * @param scrollToListTop 刷新后是否滚回列表顶部。只在「发完私信返回」时为 true，
     *                        让刚聊过的那个人露在最上面（反馈 #166）。
     */
    private fun loadSessions(scrollToListTop: Boolean = false) {
        val generation = ++loadGeneration
        CenterThreadPool.run {
            // #177：四个请求以前挤在同一个 try 里，任何一个挂了整页就是空白。
            // 现在按「通知计数 / 会话列表 / 头像昵称 / 私信未读」四段各自兜底：
            // 计数与昵称失败都还能出内容，只有会话列表本身失败才整体报错。
            val stats: JSONObject? = try {
                MessageApi.getUnread()
            } catch (e: Exception) {
                // 顶部括号里的数字而已，拉不到就不画——写 0 会让用户以为真没有新提醒
                Logu.w("MessageActivity", "通知计数加载失败：" + e.message)
                null
            }
            val page = try {
                PrivateMsgApi.getSessionsPage(PrivateMsgApi.SESSIONS_PAGE_SIZE, 0)
            } catch (e: Exception) {
                Logu.w("MessageActivity", "会话列表加载失败：" + e.message)
                onSessionsFailed(e)
                return@run
            }
            val list = page.sessions
            // 应援团 / 官方群（26.11.23）：它们在另一份列表里（get_sessions 的请求参数
            // session_type=3），用户会话那一页根本不含它们，所以原来一条都看不到。
            // 用户拍板「有未读才混排进主列表」：只并未读>0 的，免得一堆老群把手表那一屏塞满。
            // 这一份拉失败不影响主列表——群消息少看一次比整页空白好。
            val groupSessions = try {
                PrivateMsgApi.getSessionsPage(
                    PrivateMsgApi.SESSIONS_PAGE_SIZE, 0, PrivateMsgApi.LIST_TYPE_FAN_GROUP
                ).sessions.filter { it.unreadTotal() > 0 }
            } catch (e: Exception) {
                Logu.w("MessageActivity", "应援团会话加载失败：" + e.message)
                emptyList<PrivateMsgSession>()
            }
            for (group in groupSessions) {
                // talker_id 是粉丝团 id，和用户 mid 不会撞，但仍去一次重更稳妥
                if (list.none { it.talkerUid == group.talkerUid }) list.add(group)
            }
            // 置顶优先 → 未读优先 → 维持服务端原序。原先只按「未读优先」排，
            // 会把置顶但没未读的会话挤到下面去（见 SessionSorter 的类注释）。
            SessionSorter.sort(list)
            val uidList = ArrayList<Long>()
            for (item in list) {
                // 系统会话（UP主小助手 / 系统通知 / 客服…）的 talker_id 是服务端合成的，
                // 粉丝团会话的 talker_id 是粉丝团 id——两者都查不到 user/cards，
                // 塞进去还会让这个批量接口整单失败、连累正常会话的昵称头像
                if (!item.isSystem() && !item.isFanGroup()) uidList.add(item.talkerUid)
            }
            // 查不到名字头像不该让整页消失：适配器会回落到会话自带的名字与 mid
            val users = try {
                PrivateMsgApi.getUsersInfo(uidList)
            } catch (e: Exception) {
                Logu.w("MessageActivity", "会话用户信息加载失败：" + e.message)
                HashMap<Long, UserInfo>()
            }
            // 角标只写回「私信未读的真值」：通知类（回复/赞/@）在这一页顶部就有计数、
            // 点进去就是全量列表，按老语义视为已看；私信未读则要靠会话页的 update_ack 逐条消，
            // 在这里清零就会「清零→回菜单又被刷新器恢复」地反复闪（26.11.20）。
            // 取不到真值时留 -1，回主线程就不写，保留原值。
            val privateUnread = try {
                MessageApi.checkPrivateMsgUnread()
            } catch (e: Exception) {
                MsgUtil.err(e)
                -1
            }
            val adapter = PrivateMsgSessionsAdapter(
                this, list, users,
                onSessionsChanged = { loadSessions() },
                onOpenSession = { session -> openSession(session) }
            )
            runOnUiThread {
                // 晚到的旧响应直接丢，别把刚刷新出来的新顺序又盖回去
                if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                swipeRefreshLayout.isRefreshing = false
                // #177：计数拉不到就保持按钮原样，不画「(0 未读)」
                val unreadStats = stats
                if (unreadStats != null) try {
                    (findViewById<TextView>(R.id.reply_text)).text = UiText.of("回复我的") +
                            (if ((unreadStats.getInt("reply") > 0)) ("(" + unreadStats.getInt("reply") + UiText.of("未读)")) else "")
                    (findViewById<TextView>(R.id.like_text)).text =
                        UiText.of("收到的赞") + (if ((unreadStats.getInt("like") > 0)) ("(" + unreadStats.getInt("like") + "未读)") else "")
                    (findViewById<TextView>(R.id.at_text)).text =
                        UiText.of("@我") + (if ((unreadStats.getInt("at") > 0)) ("(" + unreadStats.getInt("at") + "未读)") else "")
                } catch (e: Exception) {
                    MsgUtil.err(e)
                }
                sessionsList = list
                userMap = users
                hasMore = page.hasMore
                nextEndTs = oldestSessionTs(list)
                isLoadingMore = false
                endTipShown = false
                sessionAdapter = adapter
                sessionsView.layoutManager = CustomLinearManager(this)
                sessionsView.adapter = adapter
                // 只清「确实看完了」的部分：通知类已视为已看，私信未读按服务端真值写回，
                // 用户每读完一条会话再回来，这个数就会降一格，直到 0
                if (privateUnread >= 0) {
                    SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, privateUnread)
                    // 真清零了才撤通知（否则"看过了通知还在"）
                    if (privateUnread == 0) MsgNotifier.cancel(this)
                }
                // 发完私信回来：滚回列表顶部，让刚聊过的那个人露出来（#166）
                if (scrollToListTop) sessionsView.post {
                    scrollView.smoothScrollTo(0, sessionsView.top)
                }
                // 第一页撑不满一屏就先续下一页：内容不够高时滚不动，
                // 光靠滚动监听永远触发不了加载（#166）
                sessionsView.post { maybeLoadMoreIfNearBottom() }
            }
        }
    }

    /**
     * 会话列表这一跳失败时复位界面并给出提示（反馈 #177）。
     *
     * 不复位的话下拉刷新会一直转圈、SwipeRefreshLayout 还停在禁用态，用户除了杀进程没有别的办法；
     * 也不清空已有列表——已经看到的内容比空白页有用，下拉一次就是重试。
     */
    private fun onSessionsFailed(e: Exception) {
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            swipeRefreshLayout.isRefreshing = false
            swipeRefreshLayout.isEnabled = true
            MsgUtil.err(e)
        }
    }

    /** 打开某个会话页。走 launcher 而不是直接 startActivity，是为了收到「发过私信」的结果（#166）。 */
    private fun openSession(session: PrivateMsgSession) {
        val intent = Intent(this, PrivateMsgActivity::class.java)
        intent.putExtra("uid", session.talkerUid)
        // 已读到哪一条（26.11.23）：会话页拿它当 update_ack 的 ack_seqno。
        // 之前这里什么也没传，会话页只能 ack 0，服务端不认，未读永远消不掉（#281）。
        intent.putExtra(PrivateMsgActivity.EXTRA_MAX_SEQNO, session.maxSeqno)
        // 系统会话没有真实 mid，标题只能用会话自带的账号名（26.11.20）
        if (session.isSystem()) {
            intent.putExtra(PrivateMsgActivity.EXTRA_SYSTEM_NAME, session.accountName)
        } else if (session.isFanGroup()) {
            // 粉丝团会话的 talker_id 是粉丝团 id，同样查不到用户资料，标题用团名兜底（26.11.23）
            intent.putExtra(PrivateMsgActivity.EXTRA_SESSION_NAME, session.groupName)
        }
        // 拉消息与已读都要按会话类型走：粉丝团是 session_type=2（26.11.23）
        intent.putExtra(PrivateMsgActivity.EXTRA_SESSION_TYPE, session.sessionType)
        sessionLauncher.launch(intent)
    }

    /** 列表里最老的 session_ts，作为下一页的 end_ts 游标；都没有时间戳时返回 0。 */
    private fun oldestSessionTs(list: List<PrivateMsgSession>): Long {
        var oldest = 0L
        for (item in list) {
            if (item.sessionTs > 0 && (oldest == 0L || item.sessionTs < oldest)) oldest = item.sessionTs
        }
        return oldest
    }

    /**
     * 加载下一页会话（反馈 #166：原来只拉一页 size=20，看着就是「十几个就不加载了」）。
     *
     * 拿已加载会话里最老的 session_ts 当 `end_ts` 往更早翻，接口说明见
     * [PrivateMsgApi.getSessionsPage]。新页只在页内排一次序后追加，不整表重排——
     * 服务端已经把「置顶 → 时间倒序」给好了，跨页顺序不会有实质变化。
     */
    private fun loadMoreSessions() {
        if (isLoadingMore || !hasMore) return
        val cursor = nextEndTs
        if (cursor <= 0L) {
            // 一个 session_ts 都没拿到就没法翻页：宁可停下并提示，也别拿 0 反复拉同一页
            markNoMore()
            return
        }
        isLoadingMore = true
        val generation = loadGeneration
        // 主线程先拍一份已显示会话的 uid 快照，后台线程只拿它过滤，不去读会变的 sessionsList
        val knownUids = HashSet<Long>()
        for (item in sessionsList) knownUids.add(item.talkerUid)
        MsgUtil.showMsg(UiText.of("加载更多中..."))
        CenterThreadPool.run {
            try {
                val page = PrivateMsgApi.getSessionsPage(PrivateMsgApi.SESSIONS_PAGE_SIZE, cursor)
                val fresh = ArrayList<PrivateMsgSession>()
                for (item in page.sessions) {
                    if (knownUids.add(item.talkerUid)) fresh.add(item)
                }
                SessionSorter.sort(fresh)
                val uidList = ArrayList<Long>()
                for (item in fresh) if (!item.isSystem()) uidList.add(item.talkerUid)
                val users =
                    if (uidList.isEmpty()) HashMap<Long, UserInfo>() else PrivateMsgApi.getUsersInfo(uidList)
                runOnUiThread {
                    isLoadingMore = false
                    if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                    if (fresh.isEmpty()) {
                        // 一趟没捞到新的：要么真到底了，要么服务端忽略了 end_ts（防御写法），
                        // 两种情况都该停下——继续拿同一个游标请求只会原地打转
                        markNoMore()
                        return@runOnUiThread
                    }
                    userMap.putAll(users)
                    sessionAdapter?.appendSessions(fresh)
                    val freshOldest = oldestSessionTs(fresh)
                    if (freshOldest > 0) nextEndTs = freshOldest
                    hasMore = page.hasMore
                    if (hasMore) sessionsView.post { maybeLoadMoreIfNearBottom() } else markNoMore()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    // 失败要复位，滚到底再触发一次就能重试
                    isLoadingMore = false
                    MsgUtil.err(e)
                }
            }
        }
    }

    /** 到底了：关掉继续加载，并且只提示一次。 */
    private fun markNoMore() {
        hasMore = false
        if (endTipShown) return
        endTipShown = true
        MsgUtil.showMsg(UiText.of("没有更多会话了"))
    }

    /**
     * 内容不满一屏、或已滚到离底部不到半屏时，接着拉下一页。
     *
     * 用半屏当阈值而不是固定像素：手表屏幕差得多，固定值在小屏上等于到底才拉（要空等一次
     * 网络），在大屏上又太早。这里没有现成的 dp 换算工具，按屏幕比例最稳。
     */
    private fun maybeLoadMoreIfNearBottom() {
        if (!hasMore || isLoadingMore) return
        val content = scrollView.getChildAt(0)?.height ?: return
        val remaining = content - (scrollView.scrollY + scrollView.height)
        if (remaining <= scrollView.height / 2) loadMoreSessions()
    }
}