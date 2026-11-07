package com.RobinNotBad.BiliClient.tutorial

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.Logu

/**
 * 页面滑动提示（[TutorialKind.HINT]）的渲染。
 *
 * 宿主页（视频详情 / 用户主页 / 专栏详情 / 动态详情 / 搜索）的布局里都有一层
 * `R.id.text_tutorial_pager`，平时 `visibility="gone"`；这里把它显示成
 * 「←这个页面可以左右翻页喵→（共有 N 页）」并在用户第一次翻页后收起。
 *
 * 与旧 `TutorialHelper.showPagerTutorial(activity, 页数)` 的区别：
 * 1. 宿主页不再手写调用、也不传页数——教程在 [Tutorials] 注册表里声明，由 `BaseActivity` 统一触发；
 * 2. 页数取宿主 ViewPager / ViewPager2 适配器的真实数量（旧实现的数字是写死的，
 *    视频详情页分包里有没有「演职人员」那一页它并不知道）；
 * 3. 旧实现只认 `androidx.viewpager.widget.ViewPager`，搜索页（ViewPager2）只能自己抄一份
 *    显隐逻辑，两边行为还不一致；现在两种分页控件都由这里接管。
 *
 * 时序（旧实现踩过的坑，别改回去）：
 * - `onStart` 时异步 inflate 的页面可能还没有内容，`R.id.text_tutorial_pager` 取不到；
 * - 详情页的 ViewPager 适配器要等数据回来才装，页数为 0 时不能先露出来；
 * - 教程分页页盖在上面时**既不能显示、更不能写「已展示」标记**，否则用户永远看不到这条提示。
 * 因此这里以「视图存在 + 适配器有页数 + 教程页不在前台」为条件做有限次重试。
 */
object TutorialHints {

    /** 视图 / 适配器未就绪时的重试间隔。 */
    private const val RETRY_MS = 100L

    /** 教程分页页在前台时的避让间隔（与旧实现一致）。 */
    private const val PAGER_RETRY_MS = 500L

    /** 等待视图与适配器的最大重试次数（100ms × 300 ≈ 30 秒，足够慢网加载详情页）。 */
    private const val MAX_RETRIES = 300

    /**
     * 在 [activity] 上展示 [tutorial] 对应的滑动提示。
     *
     * @param initialDelayMs 首次尝试前的延迟。宿主页同时有全屏教程要弹时传一个正值，
     *                       让教程分页页先起来，提示会一直避让到教程关闭之后再显示。
     */
    @JvmStatic
    fun show(activity: Activity, tutorial: Tutorial, initialDelayMs: Long = 0L) {
        if (tutorial.kind != TutorialKind.HINT) return
        schedule(activity, tutorial, initialDelayMs, 0)
    }

    private fun schedule(activity: Activity, tutorial: Tutorial, delayMs: Long, attempt: Int) {
        val decor = activity.window?.decorView ?: return
        decor.postDelayed({ tryShow(activity, tutorial, attempt) }, delayMs)
    }

    private fun tryShow(activity: Activity, tutorial: Tutorial, attempt: Int) {
        if (activity.isFinishing || activity.isDestroyed) return

        // 教程分页页正在前台：整个页面被它盖住，此时写掉「已展示」标记用户就再也看不到了。
        // 这类等待不走次数上限——用户在教程页停留多久都可能，活动销毁时循环自然停止。
        if (TutorialPagerActivity.isShowing) {
            schedule(activity, tutorial, PAGER_RETRY_MS, attempt)
            return
        }

        val textView = tutorial.hintViewId?.let { activity.findViewById<TextView>(it) }
        if (textView == null || pageCountOf(activity) <= 0) {
            if (attempt < MAX_RETRIES) {
                schedule(activity, tutorial, RETRY_MS, attempt + 1)
            } else {
                Logu.d("tutorial", "${tutorial.id} 的宿主页没等到可显示的内容，跳过本次提示")
            }
            return
        }

        val pages = pageCountOf(activity)
        textView.text = activity.getString(tutorial.hintTextRes ?: R.string.tutorial_pager, pages)
        textView.visibility = View.VISIBLE
        // 与旧实现一致：一露头就算「已展示过」，用户不滑动也不会再来烦他。
        TutorialStore.markRead(tutorial)
        hideOnPageChange(activity, textView)
        Logu.d("tutorial", "已展示页面滑动提示：${tutorial.id}（$pages 页）")
    }

    /** 宿主页的页数：ViewPager / ViewPager2 都支持，其它情况返回 0 表示还没准备好。 */
    private fun pageCountOf(activity: Activity): Int {
        return when (val pager = activity.findViewById<View>(R.id.viewPager)) {
            is ViewPager -> pager.adapter?.count ?: 0
            is ViewPager2 -> pager.adapter?.itemCount ?: 0
            else -> 0
        }
    }

    /** 用户第一次翻页后收起提示（旧实现同样只在 `position != 0` 时收起）。 */
    private fun hideOnPageChange(activity: Activity, textView: TextView) {
        when (val pager = activity.findViewById<View>(R.id.viewPager)) {
            is ViewPager -> pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    if (position != 0) textView.visibility = View.GONE
                }

                override fun onPageSelected(position: Int) {}

                override fun onPageScrollStateChanged(state: Int) {}
            })

            is ViewPager2 -> pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    if (position != 0) textView.visibility = View.GONE
                }
            })
        }
    }
}
