package com.RobinNotBad.BiliClient.ui.mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicActivity
import com.RobinNotBad.BiliClient.activity.message.MessageActivity
import com.RobinNotBad.BiliClient.activity.search.SearchActivity
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions

/**
 * 移动端主Activity
 * 统一管理顶栏、频道切换栏、ViewPager2内容区、底部导航栏
 */
class MobileShellActivity : BaseActivity(),
    MobileHomeFragment.ChannelSwitchListener {

    companion object {
        private const val TAB_HOME = 0
        private const val TAB_MINE = 1
        private const val TAB_SETTINGS = 2
    }

    private lateinit var mainPager: ViewPager2
    private lateinit var channelBar: LinearLayout
    private lateinit var channelIndicator: View
    private lateinit var avatarBtn: ImageView

    private lateinit var homeFragment: MobileHomeFragment
    private lateinit var mineFragment: MobileMySpaceFragment
    private lateinit var settingsFragment: MobileSettingsFragment

    private val channelTabs = listOf(R.id.channel_recommend, R.id.channel_live, R.id.channel_popular, R.id.channel_dynamic)
    private val channelTabTexts = mutableListOf<TextView>()
    private var currentChannel = 0
    private var currentTab = TAB_HOME

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_shell)

        homeFragment = MobileHomeFragment()
        mineFragment = MobileMySpaceFragment()
        settingsFragment = MobileSettingsFragment()

        initViews()
        initTopBar()
        initChannelBar()
        initBottomNav()
        initMainPager()
        loadUserAvatar()
    }

    private fun initViews() {
        mainPager = findViewById(R.id.main_pager)
        channelBar = findViewById(R.id.channel_bar)
        channelIndicator = findViewById(R.id.channel_indicator)
        avatarBtn = findViewById(R.id.avatar_btn)
    }

    /**
     * 初始化顶部导航栏
     */
    private fun initTopBar() {
        // 头像点击 → 切换到"我的"Tab
        avatarBtn.setOnClickListener {
            selectBottomTab(TAB_MINE)
        }

        // 搜索框点击 → 启动搜索页
        findViewById<View>(R.id.search_box).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        // 消息图标点击 → 启动消息页
        findViewById<View>(R.id.message_btn).setOnClickListener {
            startActivity(Intent(this, MessageActivity::class.java))
        }
    }

    /**
     * 初始化频道切换栏
     */
    private fun initChannelBar() {
        for (tabId in channelTabs) {
            val tv = findViewById<TextView>(tabId)
            channelTabTexts.add(tv)
            tv.setOnClickListener {
                val index = channelTabs.indexOf(tabId)
                if (index >= 0) {
                    selectChannel(index)
                }
            }
        }

        findViewById<TextView>(R.id.channel_dynamic).setOnClickListener {
            startActivity(Intent(this, DynamicActivity::class.java).apply {
                putExtra("from", "dynamic")
            })
        }

        updateChannelIndicator(0)
    }

    /**
     * 初始化底部导航栏
     */
    private fun initBottomNav() {
        val navItems = listOf(
            Triple(R.id.nav_home, R.id.nav_home_icon, R.id.nav_home_text),
            Triple(R.id.nav_mine, R.id.nav_mine_icon, R.id.nav_mine_text),
            Triple(R.id.nav_settings, R.id.nav_settings_icon, R.id.nav_settings_text)
        )

        for ((index, item) in navItems.withIndex()) {
            val (layoutId, iconId, textId) = item
            findViewById<LinearLayout>(layoutId).setOnClickListener {
                selectBottomTab(index)
            }
        }

        updateBottomNavSelection(TAB_HOME)
    }

    /**
     * 初始化主ViewPager2
     */
    private fun initMainPager() {
        mainPager.offscreenPageLimit = 2
        mainPager.isUserInputEnabled = true

        mainPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3

            override fun createFragment(position: Int): androidx.fragment.app.Fragment {
                return when (position) {
                    TAB_HOME -> homeFragment.also {
                        it.setChannelSwitchListener(this@MobileShellActivity)
                    }
                    TAB_MINE -> mineFragment
                    TAB_SETTINGS -> settingsFragment
                    else -> homeFragment
                }
            }
        }

        mainPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentTab = position
                updateBottomNavSelection(position)
                // 频道切换栏仅在首页Tab可见
                channelBar.visibility = if (position == TAB_HOME) View.VISIBLE else View.GONE
                channelIndicator.visibility = if (position == TAB_HOME) View.VISIBLE else View.GONE
            }
        })
    }

    /**
     * 选择底部导航Tab
     */
    private fun selectBottomTab(index: Int) {
        if (index != currentTab) {
            mainPager.setCurrentItem(index, false)
        }
    }

    /**
     * 更新底部导航选中状态
     */
    private fun updateBottomNavSelection(selectedIndex: Int) {
        val navInfos = listOf(
            Triple(R.id.nav_home_icon, R.id.nav_home_text, "首页"),
            Triple(R.id.nav_mine_icon, R.id.nav_mine_text, "我的"),
            Triple(R.id.nav_settings_icon, R.id.nav_settings_text, "设置")
        )

        for ((index, info) in navInfos.withIndex()) {
            val (iconId, textId, _) = info
            val icon = findViewById<ImageView>(iconId)
            val text = findViewById<TextView>(textId)
            val isSelected = index == selectedIndex
            val color = if (isSelected) {
                getThemePrimaryColor()
            } else {
                Color.GRAY
            }
            icon.setColorFilter(color)
            text.setTextColor(color)
        }
    }

    /**
     * 选择频道（推荐/直播/热门）
     */
    private fun selectChannel(position: Int) {
        if (position < 3) {
            // 前3个频道（推荐/直播/热门）通过Fragment切换
            currentChannel = position
            homeFragment.selectChannel(position)
            updateChannelIndicator(position)
        }
        // 第4个频道（动态）通过点击事件启动Activity
    }

    /**
     * 更新频道指示器位置
     */
    private fun updateChannelIndicator(position: Int) {
        val tabTexts = channelTabTexts.toList()
        if (position in tabTexts.indices) {
            val selectedTab = tabTexts[position]
            val parent = channelBar
            selectedTab.post {
                val params = channelIndicator.layoutParams as LinearLayout.LayoutParams
                params.width = selectedTab.width
                params.leftMargin = selectedTab.left
                channelIndicator.layoutParams = params

                // 更新文字颜色
                for ((index, tab) in tabTexts.withIndex()) {
                    tab.setTextColor(if (index == position) {
                        getThemePrimaryColor()
                    } else {
                        Color.GRAY
                    })
                }
            }
        }
    }

    /**
     * 加载用户头像
     */
    private fun loadUserAvatar() {
        val mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
        if (mid > 0) {
            CenterThreadPool.run {
                try {
                    val userInfo = UserInfoApi.getCurrentUserInfo()
                    runOnUiThread {
                        if (!isDestroyed) {
                            Glide.with(this@MobileShellActivity)
                                .load(GlideUtil.url(userInfo.avatar))
                                .apply(RequestOptions.circleCropTransform())
                                .placeholder(R.mipmap.akari)
                                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                .into(avatarBtn)
                        }
                    }
                } catch (_: Exception) {
                    // 加载失败，保持默认头像
                }
            }
        }
    }

    /**
     * 频道切换回调（从MobileHomeFragment的ViewPager2滑动触发）
     */
    override fun onChannelSelected(position: Int) {
        if (position < 3) {
            currentChannel = position
            updateChannelIndicator(position)
        }
    }

    /**
     * 获取主题主色
     */
    private fun getThemePrimaryColor(): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        return typedValue.data
    }

    override fun onBackPressed() {
        if (currentTab != TAB_HOME) {
            // 不在首页Tab，切回首页
            selectBottomTab(TAB_HOME)
        } else {
            // 在首页Tab，退出
            super.onBackPressed()
        }
    }
}