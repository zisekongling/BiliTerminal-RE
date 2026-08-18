package com.RobinNotBad.BiliClient.activity.user.info

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View

import androidx.annotation.Nullable
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter
import com.RobinNotBad.BiliClient.helper.TutorialHelper

class UserInfoActivity : BaseActivity() {

    lateinit var udFragment: UserDynamicFragment

    @SuppressLint("MissingInflatedId", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_viewpager)

        val intent = intent
        val mid = intent.getLongExtra("mid", 114514)

        setPageName("用户信息")

        TutorialHelper.showTutorialList(this, R.array.tutorial_space, 2)

        val viewPager = findViewById<ViewPager>(R.id.viewPager)

        val fragmentList: MutableList<Fragment> = ArrayList()
        udFragment = UserDynamicFragment.newInstance(mid)
        fragmentList.add(udFragment)
        val uvFragment = UserVideoFragment.newInstance(mid)
        fragmentList.add(uvFragment)
        val acFragment = UserArticleFragment.newInstance(mid)
        fragmentList.add(acFragment)
        val ufFragment = UserFavoriteFragment.newInstance(mid)
        fragmentList.add(ufFragment)
        viewPager.offscreenPageLimit = fragmentList.size

        val vpfAdapter = ViewPagerFragmentAdapter(supportFragmentManager, fragmentList)

        viewPager.adapter = vpfAdapter

        // 标题随页面变化：用户信息-动态 / 用户信息-视频 / 用户信息-专栏 / 用户信息-收藏夹
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                updatePageName(position)
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
        updatePageName(viewPager.currentItem)

        findViewById<View>(R.id.loading).visibility = View.GONE

        TutorialHelper.showPagerTutorial(this, 4)

    }

    private fun updatePageName(position: Int) {
        val sub = when (position) {
            0 -> "动态"
            1 -> "视频"
            2 -> "专栏"
            3 -> "收藏夹"
            else -> ""
        }
        setPageName(if (sub.isEmpty()) "用户信息" else "用户信息-$sub")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DynamicHolder.GO_TO_INFO_REQUEST && resultCode == RESULT_OK) {
            if (data != null) {
                udFragment.onDynamicRemove(data.getIntExtra("position", 0) - 1)
            }
        }
    }
}