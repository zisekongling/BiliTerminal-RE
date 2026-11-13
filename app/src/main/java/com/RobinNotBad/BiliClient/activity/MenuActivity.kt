package com.RobinNotBad.BiliClient.activity

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicActivity
import com.RobinNotBad.BiliClient.activity.live.RecommendLiveActivity
import com.RobinNotBad.BiliClient.activity.message.MessageActivity
import com.RobinNotBad.BiliClient.activity.search.SearchActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingMainActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.activity.user.MySpaceActivity
import com.RobinNotBad.BiliClient.activity.video.BangumiActivity
import com.RobinNotBad.BiliClient.activity.video.HotSearchActivity
import com.RobinNotBad.BiliClient.activity.video.PopularActivity
import com.RobinNotBad.BiliClient.activity.video.PreciousActivity
import com.RobinNotBad.BiliClient.activity.video.RankingActivity
import com.RobinNotBad.BiliClient.activity.video.RecommendActivity
import com.RobinNotBad.BiliClient.activity.video.ShortVideoPlayerActivity
import com.RobinNotBad.BiliClient.activity.video.TimelineActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity
import com.RobinNotBad.BiliClient.util.OptionalFeatures
import com.RobinNotBad.BiliClient.util.MenuConfig
import com.RobinNotBad.BiliClient.util.MessageUnreadRefresh
import com.RobinNotBad.BiliClient.util.MessageUnreadRefresher
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.button.MaterialButton
import java.util.LinkedHashMap

class MenuActivity : BaseActivity() {

    private var from: String? = null
    private var dynamicButton: MaterialButton? = null
    private var messageButton: MaterialButton? = null

    /**
     * 未读刷新回到主线程后的回调。
     *
     * <p>必须是**稳定的同一个对象**：它既是回调也可能是观察者表里的键，
     * `MessageUnreadRefresher.unobserve` 按标识移除它。写成字段而不是每次现造 lambda，
     * 就是为了 onPause 里能撤干净（否则静态表会留住已销毁的 Activity）。
     */
    private val unreadObserver: (Int) -> Unit = { total -> applyMessageButtonText(total) }

    companion object {
        val btnNames: Map<String, Pair<String, Class<out InstanceActivity>>> = LinkedHashMap<String, Pair<String, Class<out InstanceActivity>>>().apply {
            put("recommend", Pair(UiText.of("推荐"), RecommendActivity::class.java))
            put("short_video", Pair(UiText.of("短视频"), ShortVideoPlayerActivity::class.java))
            put("popular", Pair(UiText.of("热门"), PopularActivity::class.java))
            // 番剧分区（推荐 / 追番 / 索引 + 动态筛选，26.11.07 新增）
            put(MenuConfig.BANGUMI, Pair(UiText.of("番剧"), BangumiActivity::class.java))
            put("precious", Pair(UiText.of("入站必刷"), PreciousActivity::class.java))
            put("ranking", Pair(UiText.of("全站排行榜"), RankingActivity::class.java))
            put("hotsearch", Pair(UiText.of("热搜"), HotSearchActivity::class.java))
            put("live", Pair(UiText.of("直播"), RecommendLiveActivity::class.java))
            put("timeline", Pair(UiText.of("时间线"), TimelineActivity::class.java))
            put("search", Pair(UiText.of("搜索"), SearchActivity::class.java))
            put("dynamic", Pair(UiText.of("动态"), DynamicActivity::class.java))
            put("myspace", Pair(UiText.of("我的"), MySpaceActivity::class.java))
            put("message", Pair(UiText.of("消息"), MessageActivity::class.java))
            put("local", Pair(UiText.of("缓存"), LocalListActivity::class.java))
            put("settings", Pair(UiText.of("设置"), SettingMainActivity::class.java))
        }

        /**
         * 反射注入可选的私有菜单项（没有私有注入时一项都不加）。
         *
         * 公共构建里 OptionalFeatures.menuKey() 为 null，这里**一项都不加**，
         * 于是 btnNames 与从前的 14 项完全一致；菜单设置页（SettingMenuActivity）和
         * 启动页（SplashActivity）都从 btnNames 取显示名与 Class，所以这里注入一次即可全链路生效。
         * 机制见 `docs/optional-features.md`。
         */
        init {
            try {
                val context = BiliTerminal.context
                val key = OptionalFeatures.menuKey()
                val label = if (context != null) OptionalFeatures.menuLabel(context) else null
                @Suppress("UNCHECKED_CAST")
                val page = OptionalFeatures.menuActivity() as? Class<out InstanceActivity>
                if (key != null && label != null && page != null) {
                    @Suppress("UNCHECKED_CAST")
                    (btnNames as MutableMap<String, Pair<String, Class<out InstanceActivity>>>)[key] = Pair(label, page)
                }
            } catch (e: Throwable) {
                // 私有功能不可用绝不能影响菜单本身
            }
        }
    }

    private var time: Long = 0

    @SuppressLint("MissingInflatedId", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_menu)

        time = System.currentTimeMillis()
        Log.e("debug", "MenuActivity onCreate: $time")

        val intent = intent
        from = intent.getStringExtra("from")
        if (from != null) {
            Log.d("debug-menu", from!!)
            if (btnNames.containsKey(from))
                setPageName(btnNames[from]!!.first)
        }

        findViewById<android.view.View>(R.id.top).setOnClickListener { finish() }

        menuLayout = findViewById(R.id.menu_layout)
        menuScroll = findViewById(R.id.menu_scroll)
        hiddenGroup = findViewById(R.id.menu_hidden_group)
        hiddenButtons = findViewById(R.id.menu_hidden_buttons)
        menuFooter = findViewById(R.id.menu_footer)

        buildMenu()

        Log.e("debug", "MenuActivity onCreate in: ${System.currentTimeMillis() - time}")
    }

    // ==================== 菜单按钮构建 ====================

    private lateinit var menuLayout: LinearLayout
    private lateinit var menuScroll: com.RobinNotBad.BiliClient.ui.widget.RotaryScrollView
    private lateinit var hiddenGroup: LinearLayout
    private lateinit var hiddenButtons: LinearLayout
    private lateinit var menuFooter: LinearLayout

    /** 上次构建用的「配置指纹」。onResume 时指纹变了才重建，避免无谓的闪烁与动画。 */
    private var builtSignature: String? = null

    /** 本轮的「继续播放」意图（构建按钮与点击回调共用一份，避免每次点都重算）。 */
    private var resumeIntent: Intent? = null

    /** 底部「更多」展开区当前是否展开（纯界面状态，不持久化；重建按钮时保留）。 */
    private var hiddenExpanded = false

    /**
     * 按当前配置重建整套按钮：主列表 + 底部「更多」+ 折叠的未启用项。
     *
     * <p>为什么要重建而不是只在 onCreate 建一次：菜单排序页保存后回到菜单，若栈里这个
     * MenuActivity 实例还活着（长按顶栏回菜单用的是 CLEAR_TOP|SINGLE_TOP，不会重建），
     * 页面上留着的还是旧顺序/旧显隐——用户改完编排回来一看「没变」，就是反馈 #86 那种现象。
     * 所以 onResume 里比一次指纹（已启用列表 + 更多开关 + 登录态 + 会话），变了就重建。
     */
    private fun buildMenu() {
        val enabledMenu = SharedPreferencesUtil.loadMenuEnabled()
        val loggedIn = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0L
        resumeIntent = PlayerApi.buildResumeIntent(this)

        // 会话哨兵：上次有没看完的视频时，主页顶部给一个「继续播放」入口。
        // 这里刻意不做「一启动就续播」——手表上打开应用往往是想去别处，被播放器抢前台很烦；
        // 菜单项既不打扰又比藏在二级页好找。会话在 PlayerApi.buildResumeIntent 里做过期/过短过滤。
        //
        // 26.11.07：这一项和「退出」同待遇，进菜单配置（见 MenuConfig.RESUME）——默认不启用，
        // 用户想留就在「菜单设置」里打开；启用且有会话时依旧置顶，没会话时连空位都不留。
        val btnList = ArrayList(enabledMenu)
        btnList.remove(MenuConfig.RESUME)
        if (resumeIntent != null && MenuConfig.RESUME in enabledMenu) btnList.add(0, MenuConfig.RESUME)

        if (!loggedIn) {
            btnList.add(0, "login")
            btnList.remove("dynamic")
            btnList.remove("message")
            btnList.remove("myspace")
        }

        val showMore = SharedPreferencesUtil.loadMenuMoreButton()
        val hidden = hiddenCandidates(enabledMenu, loggedIn)

        builtSignature = signature(enabledMenu, showMore, loggedIn, resumeIntent != null)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dynamicButton = null
        messageButton = null

        menuLayout.removeAllViews()
        for (key in btnList) menuLayout.addView(createButton(key, hiddenItem = false), params)

        // 折叠区放在主列表后面（同一滚动区里），展开时滚到它
        if (hidden.isEmpty()) hiddenExpanded = false
        hiddenButtons.removeAllViews()
        for (key in hidden) hiddenButtons.addView(createButton(key, hiddenItem = true), params)
        hiddenGroup.visibility = if (hiddenExpanded) View.VISIBLE else View.GONE
        menuLayout.addView(hiddenGroup)

        // 底部固定区：只有一个「更多」按钮，按开关与「有没有东西可折叠」决定显示
        menuFooter.removeAllViews()
        if (showMore && hidden.isNotEmpty()) {
            val more = MaterialButton(this)
            more.text = getString(if (hiddenExpanded) R.string.btn_menu_more_collapse else R.string.btn_menu_more)
            more.setOnClickListener { toggleHidden() }
            menuFooter.addView(more, params)
            menuFooter.visibility = View.VISIBLE
        } else {
            menuFooter.visibility = View.GONE
        }
    }

    /**
     * 可折叠的隐藏项：配置里未启用、且当前确实能渲染出来的菜单 key。
     *
     * <p>过滤口径与主列表一致——没登录时「动态/消息/我的」进不去，不该在折叠区里出现；
     * 「继续播放」只有存在会话时才有意义；固定项本来就不会被藏，天然不在其中。
     */
    private fun hiddenCandidates(enabledMenu: List<String>, loggedIn: Boolean): List<String> =
        MenuConfig.disabledFrom(enabledMenu).filter { key ->
            when (key) {
                MenuConfig.EXIT -> true
                MenuConfig.RESUME -> resumeIntent != null
                "dynamic", "message", "myspace" -> loggedIn && btnNames.containsKey(key)
                else -> btnNames.containsKey(key)
            }
        }

    private fun signature(enabled: List<String>, showMore: Boolean, loggedIn: Boolean, hasResume: Boolean): String =
        enabled.joinToString(";") + "|" + showMore + "|" + loggedIn + "|" + hasResume

    /** 展开 / 收起未启用项。展开后滚到折叠区顶部，否则手表上一屏只四个按钮，用户看不到变化。 */
    private fun toggleHidden() {
        hiddenExpanded = !hiddenExpanded
        hiddenGroup.visibility = if (hiddenExpanded) View.VISIBLE else View.GONE
        (menuFooter.getChildAt(0) as? MaterialButton)?.text =
            getString(if (hiddenExpanded) R.string.btn_menu_more_collapse else R.string.btn_menu_more)
        if (hiddenExpanded) {
            hiddenGroup.post {
                if (!isFinishing && !isDestroyed) menuScroll.smoothScrollTo(0, hiddenGroup.top)
            }
        }
    }

    /**
     * 造一个菜单按钮。「退出」「继续播放」是动作项，不走 [killAndJump]（它要往页面栈里跳）。
     *
     * @param hiddenItem 折叠区里的按钮：不接管动态/消息的未读文案刷新（那两个只能有一个实例）
     */
    private fun createButton(btn: String, hiddenItem: Boolean): MaterialButton {
        val materialButton = MaterialButton(this)
        when (btn) {
            "login" -> materialButton.text = UiText.of("登录")
            MenuConfig.EXIT -> materialButton.text = getString(R.string.btn_exit)
            MenuConfig.RESUME -> materialButton.text = getString(R.string.player_queue_resume_last)
            "dynamic" -> {
                var btnText = btnNames[btn]!!.first
                if (!hiddenItem) {
                    dynamicButton = materialButton
                    val updateNum = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0)
                    if (updateNum > 0) {
                        btnText = "$btnText ($updateNum)"
                    }
                }
                materialButton.text = btnText
            }
            "message" -> {
                if (!hiddenItem) messageButton = materialButton
                materialButton.text =
                    messageLabel(SharedPreferencesUtil.getInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0))
            }
            else -> materialButton.text = btnNames[btn]?.first ?: btn
        }
        materialButton.setOnClickListener {
            when {
                btn == MenuConfig.EXIT -> exitAppCompletely()
                btn == MenuConfig.RESUME -> {
                    resumeIntent?.let { startActivity(it) }
                    finish()
                }
                else -> killAndJump(btn)
            }
        }
        return materialButton
    }

    override fun onStart() {
        super.onStart()
        Log.e("debug", "MenuActivity onStart in: ${System.currentTimeMillis() - time}")
    }

    override fun onResume() {
        super.onResume()
        Log.e("debug", "MenuActivity onResume in: ${System.currentTimeMillis() - time}")
        // 从「菜单设置」改完编排回来：实例还活着就别拿旧按钮糊弄人，配置变了就整页重建
        if (configChanged()) buildMenu()
        if (dynamicButton != null) {
            var btnText = btnNames["dynamic"]!!.first
            val updateNum = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0)
            if (updateNum > 0) {
                btnText = "$btnText ($updateNum)"
            }
            dynamicButton!!.text = btnText
        }
        if (messageButton != null) {
            updateMessageButtonText()
        }
        // 未读提示（E）：回到菜单时做一次性的增量刷新——先把 SP 里的值画上（同步、无闪烁），
        // 再让刷新器决定这次要不要真的发请求（最小间隔 5 秒 + 单飞，判据在 MessageUnreadRefresh 里）。
        // 未登录 / 关了「消息数量检查」时刷新器内部直接返回；全程没有后台定时器。
        MessageUnreadRefresher.refreshOnMenuResume(unreadObserver)
    }

    override fun onPause() {
        // 刷新是异步回来的，本页不再前台就撤掉回调，别让静态观察者表留住已销毁的 Activity
        MessageUnreadRefresher.unobserve(unreadObserver)
        super.onPause()
    }

    /** 当前配置算出来的指纹与本页已构建的那份是否不同（同 onResume 的重建判据）。 */
    private fun configChanged(): Boolean {
        if (builtSignature == null) return true
        val current = signature(
            SharedPreferencesUtil.loadMenuEnabled(),
            SharedPreferencesUtil.loadMenuMoreButton(),
            SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0L,
            PlayerApi.buildResumeIntent(this) != null
        )
        return current != builtSignature
    }

    /** 「消息」按钮的文案：无未读时是按钮名，有未读时是「消息 (3)」。 */
    private fun messageLabel(count: Int): String = MessageUnreadRefresh.formatCountLabel(
        btnNames["message"]!!.first,
        count,
        getString(R.string.social_menu_unread_label)
    )

    /** 把未读数画到按钮上。异步回调进来时页面可能已经在收尾，先判活再写 View。 */
    private fun applyMessageButtonText(count: Int) {
        if (isFinishing || isDestroyed) return
        messageButton?.text = messageLabel(count)
    }

    /** 按 SP 里当前的未读数刷新按钮文案（进消息页会清零，所以返回菜单要重读）。 */
    private fun updateMessageButtonText() {
        applyMessageButtonText(SharedPreferencesUtil.getInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0))
    }

    private fun killAndJump(name: String) {
        if (btnNames.containsKey(name) && name != from) {
            // 可选菜单项（目前只有私有功能「扫一扫」这类工具页）跳转前不杀下层页面。
            // 这类页是用完就退的工具，用户从它（或它扫出来的页面）返回时应该回到原来在看的
            // 那一页；一旦把下层 finish 掉，工具页就成了任务栈里唯一的一页，它一退出整个任务
            // 就结束，用户看到的是「按返回直接退出应用」。
            // 其余菜单项仍按既有语义当成主页切换：旧主页不再保留。
            if (name !in MenuConfig.OPTIONAL_ITEMS) {
                val instance = BiliTerminal.getInstanceActivityOnTop()
                if (instance != null && instance.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                    // 缓存页 → 文件夹页是一条链，切栏目要把整条链收掉，否则文件夹页下面那张
                    // 缓存页会留在返回栈里（父子关系与判据都在 LocalListActivity 内部）。
                    if (!LocalListActivity.finishLocalCacheChain(instance)) instance.finish()
                }
            }

            val intent = Intent()
            intent.setClass(this@MenuActivity, btnNames[name]!!.second)
            intent.putExtra("from", name)
            startActivity(intent)
            // 26.11.09：这里原来在低性能设备上 `Glide.get(...).clearMemory()`（「更积极地清理Glide内存」）。
            // 已去掉，与 VideoInfoActivity 那次是同一个理由：
            // 1) 真正占内存的是正在显示 / 还在请求里的位图，它们在 ActiveResources 与请求里（acquired > 0），
            //    MemoryCache 清不到；MemoryCache 本身也有 LruResourceCache 上限，不会无界增长；
            // 2) 清空的时机最糟：紧接着 startActivity 开的这一页就要加载一批图片，缓存被清空只会让它们
            //    重新下载解码，淘汰（onItemEvicted → Engine.onResourceRemoved → forceNextFrame 投递）
            //    反而更密；
            // 3) 这条分支只在低性能设备上走，而低性能手表正是崩溃
            //    `Cannot recycle a resource while it is still acquired` 的集中人群。
            // 旧页面已经在上面被 finish，它的位图会随请求清理自然释放；系统内存紧张时 Glide 自己会走
            // LruResourceCache.trimMemory。
        } else {
            when (name) {
                "login" -> {
                    val intent = Intent()
                    intent.setClass(this@MenuActivity, LoginActivity::class.java)
                    startActivity(intent)
                }
            }
        }
        finish()
    }

    /**
     * 完全关闭应用：先收掉本任务栈里的所有 Activity，再结束进程兜底。
     *
     * 只 [finishAffinity] 会留下一个空转的后台进程——线程池、Glide、播放器引擎都没有
     * 统一的停机口，手表上表现为"退出了还在耗电"。与 [com.RobinNotBad.BiliClient.activity.settings.UpdateActivity]
     * 强制更新页的退出写法一致。
     */
    private fun exitAppCompletely() {
        finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) finish()
        return super.onKeyDown(keyCode, event)
    }
}