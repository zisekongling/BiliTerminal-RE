package com.RobinNotBad.BiliClient.ui.appearance

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import kotlin.math.sqrt

/**
 * 圆屏列表效果（实验性，26.10.31）。
 *
 * 「圆屏模式 + 圆屏列表」同时开启时，页面里所有竖向滚动列表的可见条目会按其在屏幕上的
 * 纵向位置做等比缩放与边缘淡出：越靠近屏幕上下边缘越小、越透明，模拟圆屏弧面对内容的
 * 避让（曲率避让），列表条目不再顶着圆形屏面的边界滚动。横向容器（横向 RecyclerView，
 * 含 ViewPager2 内部的翻页 RV）不参与效果；ViewPager2 页内的竖向列表（搜索结果、教程
 * 翻页这类）照常参与。只有接近整屏高的内容（文章整页块、图片查看器这类）不参与效果——
 * 手表上比屏幕半径高的普通卡片（视频卡、合集卡）仍参与，否则效果会在最常见的界面上
 * 整段消失。
 *
 * <h3>实现约束（性能红线，改这里之前先读）</h3>
 * - 效果只写 View 的变换属性（scaleX/scaleY/alpha），不 requestLayout、不加层，
 *   属性 setter 内部有等值短路，连续写同一个值几乎没有开销；
 * - 滚动回调里只做数学变换，全程零对象分配（坐标数组、扫描栈都在 State 里复用）；
 * - 容器扫描（全树走一遍）只挂在 OnGlobalLayoutListener 上，布局变化本来就不频繁；
 * - 总开关关闭时 [install] 直接 return，一个监听都不挂，零开销。
 *
 * <p>设置在 [com.RobinNotBad.BiliClient.activity.base.BaseActivity.onCreate] 末尾读取，
 * 开关后重新进入页面生效（与外观模块的"重建或重进生效"一致）。
 */
object RoundListEffect {

    /** 效果总开关：实验开关与圆屏模式（player_ui_round）都开才生效。 */
    fun isEnabled(): Boolean =
        SharedPreferencesUtil.getBoolean(SettingsKeys.UI_ROUND_LIST_EFFECT, false)
                && SharedPreferencesUtil.getBoolean(SettingsKeys.UI_ROUND, false)

    /**
     * 在 Activity 的窗口上挂效果。在 BaseActivity.onCreate 末尾调用（此时 decor 已就绪，
     * 子类稍后的 setContentView / 异步填充都会触发 onGlobalLayout 重扫）。
     */
    fun install(activity: Activity) {
        if (!isEnabled()) return
        val decor = activity.window?.decorView ?: return
        val state = State(decor)
        val observer = decor.viewTreeObserver
        observer.addOnScrollChangedListener { apply(state) }
        observer.addOnGlobalLayoutListener {
            scan(state)
            apply(state)
        }
        // onCreate 时可能已有部分内容就绪（非异步页），先扫一轮；其余交给 onGlobalLayout
        scan(state)
        apply(state)
    }

    // ---------------- 内部状态与扫描 ----------------

    private const val KIND_RECYCLER = 0
    private const val KIND_LIST = 1
    private const val KIND_SCROLL = 2

    /** 一个竖向滚动容器：view + 分类。Recycler/List 的条目直接是孩子；ScrollView 还要剥一层内容容器。 */
    private class Container(val view: View, val kind: Int)

    /**
     * 每个窗口一份的可复用状态。坐标数组与扫描栈全程复用，
     * 保证滚动回调路径上没有任何对象分配。
     */
    private class State(val decor: View) {
        val loc = IntArray(2)
        val stack = ArrayList<View>(48)
        val containers = ArrayList<Container>(4)
    }

    /** 全树扫描竖向滚动容器。只在布局变化时调用（布局本身昂贵，这里多花一点无所谓）。 */
    private fun scan(state: State) {
        state.containers.clear()
        state.stack.clear()
        state.stack.add(state.decor)
        while (state.stack.isNotEmpty()) {
            val view = state.stack.removeAt(state.stack.size - 1)
            if (view.visibility != View.VISIBLE) continue
            val kind = classify(view)
            if (kind >= 0) {
                state.containers.add(Container(view, kind))
                // 命中容器就不再下钻：列表嵌列表（如卡片里的横向封面轮播）不该被外层效果带动
                continue
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) state.stack.add(view.getChildAt(i))
            }
        }
    }

    /** 返回容器分类；不是可处理的竖向滚动容器返回 -1。 */
    private fun classify(view: View): Int {
        when (view) {
            is RecyclerView -> {
                val lm = view.layoutManager ?: return -1 // 尚未布局，等下一轮 onGlobalLayout
                if (!lm.canScrollVertically()) return -1 // 横向列表（含 ViewPager2 的内部翻页 RV）不参与
                return KIND_RECYCLER // ViewPager2 页内的竖向列表照常参与（搜索结果、教程翻页等）
            }
            is ListView -> return KIND_LIST
            is ScrollView -> return KIND_SCROLL // android.widget.ScrollView 只能竖向
            is NestedScrollView -> return KIND_SCROLL
        }
        return -1
    }

    // ---------------- 变换 ----------------

    /**
     * 对所有已登记容器的可见条目施加缩放与淡出。
     * 在滚动与布局回调里调用；条目窗口位置 = 容器窗口位置 + 条目在容器内的位置
     * （Recycler/List 的 child.top 已含滚动量，ScrollView 家族要再减 scrollY）。
     */
    private fun apply(state: State) {
        val decor = state.decor
        val loc = state.loc
        decor.getLocationInWindow(loc)
        val winH = if (decor.height > 0) decor.height else return
        val winW = if (decor.width > 0) decor.width else return
        val centerY = loc[1] + winH * 0.5f
        val radius = (if (winW < winH) winW else winH) * 0.5f

        for (i in state.containers.indices) {
            val container = state.containers[i]
            if (container.view.parent == null || container.view.windowVisibility != View.VISIBLE) continue
            container.view.getLocationInWindow(loc)
            val containerTop = loc[1]
            val group = container.view as ViewGroup
            when (container.kind) {
                KIND_RECYCLER, KIND_LIST -> {
                    for (j in 0 until group.childCount) {
                        val row = group.getChildAt(j)
                        transform(row, containerTop + row.top, centerY, radius, winH)
                    }
                }
                else -> { // KIND_SCROLL：内容是唯一子 View，条目是它的孩子
                    if (group.childCount == 0) continue
                    val content = group.getChildAt(0)
                    if (content !is ViewGroup) continue
                    val contentTop = containerTop + content.top - group.scrollY
                    for (j in 0 until content.childCount) {
                        val row = content.getChildAt(j)
                        transform(row, contentTop + row.top, centerY, radius, winH)
                    }
                }
            }
        }
    }

    /**
     * 单条目的变换。缩放曲线取圆的弦长比（中心 1.0 → 屏缘 0），下限收到 0.50——边缘条目
     * 缩到一半，曲率一眼可见；淡出取 t 的三次方曲线（中心四分之一段几乎不动），
     * 系数 0.85 让边缘条目降到 0.15 透明度。只跳过接近整屏高的内容：比屏幕半径高的
     * 普通卡片照常参与，否则手表上最常见的视频卡（高 250~320px，半径才 233px）会被
     * 整段跳过，效果等于没开。
     */
    private fun transform(row: View, topInWindow: Int, centerY: Float, radius: Float, winH: Int) {
        if (row.visibility != View.VISIBLE || row.height <= 0) return
        if (row.height >= winH) return // 接近整屏的内容（文章整页块 / 图片查看器）不是列表条目，不做缩放
        val rowCenterY = topInWindow + row.height * 0.5f
        val f = (rowCenterY - centerY) / radius
        var t = if (f < 0f) -f else f
        if (t > 1f) t = 1f
        val chord = sqrt(1f - t * t)
        val scale = 0.50f + 0.50f * chord
        row.scaleX = scale
        row.scaleY = scale
        row.alpha = 1f - 0.85f * t * t * t
    }
}
