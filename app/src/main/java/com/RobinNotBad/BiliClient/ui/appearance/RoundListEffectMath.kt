package com.RobinNotBad.BiliClient.ui.appearance

import kotlin.math.sqrt

/**
 * 圆屏列表效果的纯数学部分（无 Android 依赖，可直接 JVM 单测）。
 *
 * 效果本身见 [RoundListEffect]：条目按「离屏幕中心多远」做等比缩放与淡出。
 * 这里把判定与曲线抽出来，是因为其中有两条容易踩的边界：
 *
 * 1. **用「条目离屏幕中心最近的一点」而不是条目中心**（反馈 #79）：评论区带长图的条目、
 *    长正文这类高条目，中心点往往已经贴到屏缘，按中心算会把整条内容缩到 0.5、淡到 0.15；
 *    但它们明明有一部分（甚至整屏跨度）就在屏幕中间。改判最近点后，跨度覆盖屏幕中心的高条目
 *    距离为 0，等于不缩放——正是「中间的内容不该被边缘规则带小」。
 * 2. **接近整屏高的内容整体不参与**：文章整页块、图片查看器这类不是列表条目，
 *    给它们加缩放只会让正文糊掉。
 *
 * 缩放曲线取圆的弦长比（中心 1.0 → 屏缘 0.50 封底），淡出取 t 的三次方
 * （中心四分之一段几乎不动，屏缘降到 0.15）。
 */
object RoundListEffectMath {

    /** 缩放下限：屏缘条目缩到一半，曲率一眼可见但还能看清。 */
    const val MIN_SCALE = 0.50f

    /** 屏缘条目最多淡掉多少（1.0 - 0.85 = 0.15）。 */
    const val ALPHA_FADE = 0.85f

    /**
     * 条目纵向跨度离 [centerY] 的最近距离；跨度覆盖中心返回 0。
     *
     * @param rowTop 条目顶边（窗口坐标，像素）
     * @param rowHeight 条目高度（像素）
     */
    fun nearestVerticalDistance(rowTop: Float, rowHeight: Int, centerY: Float): Float {
        val bottom = rowTop + rowHeight
        return when {
            centerY < rowTop -> rowTop - centerY
            centerY > bottom -> centerY - bottom
            else -> 0f
        }
    }

    /**
     * 归一化的纵向距离 `t`（0 = 屏幕中心，1 = 屏缘）。
     *
     * 返回 null 表示这条内容不参与效果：高度非法、或接近整屏高（不是列表条目）。
     *
     * @param radius 屏幕半径（短边的一半，像素），为 0 时不参与
     */
    fun tFor(rowTop: Float, rowHeight: Int, centerY: Float, radius: Float, winH: Int): Float? {
        if (rowHeight <= 0 || radius <= 0f || winH <= 0) return null
        if (rowHeight >= winH) return null
        val distance = nearestVerticalDistance(rowTop, rowHeight, centerY)
        return (distance / radius).coerceIn(0f, 1f)
    }

    /** 缩放系数：圆的弦长比，中心 1.0 → 屏缘 [MIN_SCALE]。 */
    fun scaleFor(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return MIN_SCALE + (1f - MIN_SCALE) * sqrt(1f - clamped * clamped)
    }

    /** 透明度：t 的三次方曲线，中心 1.0 → 屏缘 1 - [ALPHA_FADE]。 */
    fun alphaFor(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return 1f - ALPHA_FADE * clamped * clamped * clamped
    }
}
