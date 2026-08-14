package com.RobinNotBad.BiliClient.adapter.viewpager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter

class ViewPagerFragmentAdapter(fm: FragmentManager, private val fragmentList: List<Fragment>) :
    FragmentStatePagerAdapter(fm) {

    val fm: FragmentManager = fm

    override fun getItem(position: Int): Fragment {
        if (position < 0 || position >= fragmentList.size) {
            return Fragment()
        }
        return fragmentList[position]
    }

    override fun getCount(): Int {
        return if (fragmentList != null) fragmentList.size else 0
    }
}