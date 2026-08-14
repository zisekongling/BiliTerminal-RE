package com.RobinNotBad.BiliClient.adapter.viewpager

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter

class ViewPagerViewAdapter(private val viewList: List<View>) : PagerAdapter() {

    override fun getCount(): Int {
        return if (viewList != null) viewList.size else 0
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        if (viewList.isEmpty() || position < 0 || position >= viewList.size) {
            return View(container.context)
        }
        val view = viewList[position]
        if (view.parent == null) {
            container.addView(view)
        }
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        if (`object` is View) {
            container.removeView(`object` as View)
        }
    }
}