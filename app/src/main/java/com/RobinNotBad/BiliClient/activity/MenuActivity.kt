package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.text.TextUtils
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
import com.RobinNotBad.BiliClient.activity.video.PopularActivity
import com.RobinNotBad.BiliClient.activity.video.PreciousActivity
import com.RobinNotBad.BiliClient.activity.video.RankingActivity
import com.RobinNotBad.BiliClient.activity.video.RecommendActivity
import com.RobinNotBad.BiliClient.activity.video.ShortVideoPlayerActivity
import com.RobinNotBad.BiliClient.activity.video.TimelineActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity
import com.RobinNotBad.BiliClient.ui.mobile.MobileShellActivity
import com.RobinNotBad.BiliClient.util.PerformanceManager
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
            put("recommend", Pair("推荐", RecommendActivity::class.java))
            put("short_video", Pair("短视频", ShortVideoPlayerActivity::class.java))
            put("popular", Pair("热门", PopularActivity::class.java))
            put("precious", Pair("入站必刷", PreciousActivity::class.java))
            put("ranking", Pair("全站排行榜", RankingActivity::class.java))
            put("live", Pair("直播", RecommendLiveActivity::class.java))
            put("timeline", Pair("时间线", TimelineActivity::class.java))
            put("search", Pair("搜索", SearchActivity::class.java))
            put("dynamic", Pair("动态", DynamicActivity::class.java))
            put("myspace", Pair("我的", MySpaceActivity::class.java))
            put("message", Pair("消息", MessageActivity::class.java))
            put("local", Pair("缓存", LocalListActivity::class.java))
            put("settings", Pair("设置", SettingMainActivity::class.java))
        }
    }

    private var time: Long = 0

    @SuppressLint("MissingInflatedId", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isMobileMode()) {
            setupMobileMode()
            return
        }

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

        var btnList: List<String>

        val sortConf = SharedPreferencesUtil.getString(SharedPreferencesUtil.MENU_SORT, "")
        Log.e("debug_sort", sortConf)

        if (!TextUtils.isEmpty(sortConf)) {
            val splitName = sortConf.split(";")
            if (splitName.size != btnNames.size) {
                btnList = getDefaultSortList()
            } else {
                btnList = ArrayList()
                for (name in splitName) {
                    if (!btnNames.containsKey(name)) {
                        btnList = getDefaultSortList()
                        break
                    } else {
                        (btnList as ArrayList).add(name)
                    }
                }
            }
        } else {
            btnList = getDefaultSortList()
        }

        val mutableBtnList = btnList.toMutableList()
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
            mutableBtnList.add(0, "login")
            mutableBtnList.remove("dynamic")
            mutableBtnList.remove("message")
            mutableBtnList.remove("myspace")
        }

        if (!SharedPreferencesUtil.getBoolean("menu_popular", true)) mutableBtnList.remove("popular")
        if (!SharedPreferencesUtil.getBoolean("menu_short_video", true)) mutableBtnList.remove("short_video")
        if (!SharedPreferencesUtil.getBoolean("menu_precious", false)) mutableBtnList.remove("precious")
        if (!SharedPreferencesUtil.getBoolean("menu_ranking", false)) mutableBtnList.remove("ranking")
        if (!SharedPreferencesUtil.getBoolean("menu_live", false)) mutableBtnList.remove("live")
        if (!SharedPreferencesUtil.getBoolean("menu_timeline", false)) mutableBtnList.remove("timeline")

        mutableBtnList.add("exit")

        val layout = findViewById<LinearLayout>(R.id.menu_layout)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        for (btn in mutableBtnList) {
            val materialButton = MaterialButton(this)
            when (btn) {
                "exit" -> materialButton.text = "退出"
                "login" -> materialButton.text = "登录"
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
            val instance = BiliTerminal.getInstanceActivityOnTop()
            if (instance != null && instance.lifecycle.currentState != Lifecycle.State.DESTROYED)
                instance.finish()

            val intent = Intent()
            intent.setClass(this@MenuActivity, btnNames[name]!!.second)
            intent.putExtra("from", name)
            startActivity(intent)
            // 在低性能设备上更积极地清理Glide内存
            if (PerformanceManager.isLowPerfDevice()) {
                Glide.get(BiliTerminal.context).clearMemory()
            }
        } else {
            when (name) {
                "exit" -> {
                    val instance = BiliTerminal.getInstanceActivityOnTop()
                    if (instance != null && !instance.isDestroyed) instance.finish()
                    Process.killProcess(Process.myPid())
                }
                "login" -> {
                    val intent = Intent()
                    intent.setClass(this@MenuActivity, LoginActivity::class.java)
                    startActivity(intent)
                }
            }
        }
        finish()
    }

    private fun getDefaultSortList(): MutableList<String> {
        return ArrayList<String>().apply {
            add("recommend")
            add("short_video")
            add("popular")
            add("precious")
            add("ranking")
            add("live")
            add("timeline")
            add("search")
            add("dynamic")
            add("myspace")
            add("message")
            add("local")
            add("settings")
        }
    }

    /**
     * 设置手机模式UI：启动MobileShellActivity
     */
    private fun setupMobileMode() {
        val intent = Intent(this, MobileShellActivity::class.java)
        intent.putExtra("from", intent.getStringExtra("from"))
        startActivity(intent)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) finish()
        return super.onKeyDown(keyCode, event)
    }
}