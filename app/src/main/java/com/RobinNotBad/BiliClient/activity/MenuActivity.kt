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
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicActivity
import com.RobinNotBad.BiliClient.activity.live.RecommendLiveActivity
import com.RobinNotBad.BiliClient.activity.message.MessageActivity
import com.RobinNotBad.BiliClient.activity.search.SearchActivity
import com.RobinNotBad.BiliClient.activity.settings.SettingMainActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.activity.user.MySpaceActivity
import com.RobinNotBad.BiliClient.activity.video.HotSearchActivity
import com.RobinNotBad.BiliClient.activity.video.PopularActivity
import com.RobinNotBad.BiliClient.activity.video.PreciousActivity
import com.RobinNotBad.BiliClient.activity.video.RankingActivity
import com.RobinNotBad.BiliClient.activity.video.RecommendActivity
import com.RobinNotBad.BiliClient.activity.video.ShortVideoPlayerActivity
import com.RobinNotBad.BiliClient.activity.video.TimelineActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.OptionalFeatures
import com.RobinNotBad.BiliClient.util.MenuConfig
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import java.util.LinkedHashMap

class MenuActivity : BaseActivity() {

    private var from: String? = null
    private var dynamicButton: MaterialButton? = null
    private var messageButton: MaterialButton? = null

    companion object {
        val btnNames: Map<String, Pair<String, Class<out InstanceActivity>>> = LinkedHashMap<String, Pair<String, Class<out InstanceActivity>>>().apply {
            put("recommend", Pair(UiText.of("推荐"), RecommendActivity::class.java))
            put("short_video", Pair(UiText.of("短视频"), ShortVideoPlayerActivity::class.java))
            put("popular", Pair(UiText.of("热门"), PopularActivity::class.java))
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

        var btnList: MutableList<String> = SharedPreferencesUtil.loadMenuEnabled().toMutableList()

        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
            btnList.add(0, "login")
            btnList.remove("dynamic")
            btnList.remove("message")
            btnList.remove("myspace")
        }

        val layout = findViewById<LinearLayout>(R.id.menu_layout)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        for (btn in btnList) {
            val materialButton = MaterialButton(this)
            when (btn) {
                "login" -> materialButton.text = UiText.of("登录")
                "dynamic" -> {
                    var btnText = btnNames[btn]!!.first
                    if (btn == "dynamic") {
                        dynamicButton = materialButton
                        val updateNum = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0)
                        if (updateNum > 0) {
                            btnText = "$btnText ($updateNum)"
                        }
                    }
                    materialButton.text = btnText
                }
                "message" -> {
                    var messageBtnText = btnNames[btn]!!.first
                    messageButton = materialButton
                    val messageUpdateNum = SharedPreferencesUtil.getInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0)
                    if (messageUpdateNum > 0) {
                        messageBtnText = "$messageBtnText ($messageUpdateNum)"
                    }
                    materialButton.text = messageBtnText
                }
                else -> materialButton.text = btnNames[btn]!!.first
            }
            materialButton.setOnClickListener { killAndJump(btn) }
            layout.addView(materialButton, params)
        }

        // 「退出」固定按钮：不放进 btnNames——菜单项受「菜单设置」管控且点了要跳页，
        // 退出是常驻入口、点了直接收摊，固定排在菜单最下方（desc_back_disable 的文案
        // 也一直写着"菜单最下方的退出按钮"）。手表上没有其他彻底收摊的入口，这里补上。
        val exitButton = MaterialButton(this)
        exitButton.text = getString(R.string.btn_exit)
        exitButton.setOnClickListener { exitAppCompletely() }
        layout.addView(exitButton, params)

        Log.e("debug", "MenuActivity onCreate in: ${System.currentTimeMillis() - time}")
    }

    override fun onStart() {
        super.onStart()
        Log.e("debug", "MenuActivity onStart in: ${System.currentTimeMillis() - time}")
    }

    override fun onResume() {
        super.onResume()
        Log.e("debug", "MenuActivity onResume in: ${System.currentTimeMillis() - time}")
        if (dynamicButton != null) {
            var btnText = btnNames["dynamic"]!!.first
            val updateNum = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0)
            if (updateNum > 0) {
                btnText = "$btnText ($updateNum)"
            }
            dynamicButton!!.text = btnText
        }
        if (messageButton != null) {
            var messageBtnText = btnNames["message"]!!.first
            val messageUpdateNum = SharedPreferencesUtil.getInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0)
            if (messageUpdateNum > 0) {
                messageBtnText = "$messageBtnText ($messageUpdateNum)"
            }
            messageButton!!.text = messageBtnText
        }
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
                if (instance != null && instance.lifecycle.currentState != Lifecycle.State.DESTROYED)
                    instance.finish()
            }

            val intent = Intent()
            intent.setClass(this@MenuActivity, btnNames[name]!!.second)
            intent.putExtra("from", name)
            startActivity(intent)
            // 在低性能设备上更积极地清理Glide内存
            if (PerformanceManager.isLowPerfDevice()) {
                Glide.get(BiliTerminal.context!!).clearMemory()
            }
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