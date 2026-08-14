package com.RobinNotBad.BiliClient.ui.mobile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.RobinNotBad.BiliClient.R

/**
 * 移动端首页Fragment
 * 管理内部ViewPager2承载3个频道子页面（推荐/直播/热门），与Activity的频道切换栏联动
 */
class MobileHomeFragment : Fragment() {

    lateinit var channelPager: ViewPager2
    private val channelFragments = listOf(
        MobileRecommendPageFragment(),
        MobileLivePageFragment(),
        MobilePopularPageFragment()
    )

    /**
     * 频道切换回调接口，由Activity实现
     */
    interface ChannelSwitchListener {
        fun onChannelSelected(position: Int)
    }

    private var channelListener: ChannelSwitchListener? = null

    fun setChannelSwitchListener(listener: ChannelSwitchListener) {
        channelListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mobile_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        channelPager = view.findViewById(R.id.channel_pager)
        channelPager.offscreenPageLimit = 1
        channelPager.adapter = ChannelPagerAdapter(this)

        channelPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                channelListener?.onChannelSelected(position)
            }
        })
    }

    /**
     * 通过Activity的频道切换栏切换到指定频道
     */
    fun selectChannel(position: Int) {
        if (position in 0 until channelFragments.size) {
            channelPager.setCurrentItem(position, true)
        }
    }

    /**
     * ViewPager2适配器
     */
    private inner class ChannelPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = channelFragments.size

        override fun createFragment(position: Int): Fragment = channelFragments[position]
    }
}