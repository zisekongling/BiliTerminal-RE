package com.RobinNotBad.BiliClient.activity.settings.login

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class LoginActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_viewpager)
        Log.e("debug", "进入登录页面")
        setPageName("登录")

        val from_setup = intent.getBooleanExtra("from_setup", false)

        val viewPager = findViewById<ViewPager>(R.id.viewPager)
        val fragmentList = ArrayList<androidx.fragment.app.Fragment>()
        fragmentList.add(QRLoginFragment.newInstance(from_setup))
        fragmentList.add(PasswordLoginFragment.newInstance(from_setup))
        fragmentList.add(SMSLoginFragment.newInstance(from_setup))

        viewPager.offscreenPageLimit = fragmentList.size
        val vpfAdapter = ViewPagerFragmentAdapter(supportFragmentManager, fragmentList)
        viewPager.adapter = vpfAdapter

        findViewById<View>(R.id.loading).visibility = View.GONE
        if (fragmentList.size > 1 && SharedPreferencesUtil.getBoolean("first_" + LoginActivity::class.java.simpleName, true)) {
            MsgUtil.showMsgLong("提示：本页面可以左右滑动切换登录方式")
            SharedPreferencesUtil.putBoolean("first_" + LoginActivity::class.java.simpleName, false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}