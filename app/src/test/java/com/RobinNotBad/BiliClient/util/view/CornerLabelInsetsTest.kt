package com.RobinNotBad.BiliClient.util.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 看图页底部角标的外边距算法（反馈 #280：页数在左下、下载在右下，圆屏不许被圆弧压住）。
 *
 * 数字都用圆形手表上的真实量级：380×380（大表盘）、320×320（42mm），基准边距取 8dp 在
 * 常见密度下的换算值 21px。
 */
class CornerLabelInsetsTest {

    @Test
    fun 方屏原样返回基准边距() {
        assertEquals(listOf(21, 21), CornerLabelInsets.marginsPx(380, 21, round = false).toList())
        // 基准值为 0 时也还是 0，不许被算成负数或凭空多出一圈
        assertEquals(listOf(0, 0), CornerLabelInsets.marginsPx(380, 0, round = false).toList())
    }

    @Test
    fun 圆屏在基准值上叠内接正方形那一圈() {
        // 380 直径的内接圈 = 56px，叠 21 基准 = 77
        assertEquals(listOf(77, 77), CornerLabelInsets.marginsPx(380, 21, round = true).toList())
        // 42mm 的 320 屏：内接圈 47，叠 21 = 68（比大表盘小，比例一致）
        assertEquals(listOf(68, 68), CornerLabelInsets.marginsPx(320, 21, round = true).toList())
    }

    @Test
    fun 圆屏横竖同值且随直径单调变大() {
        val small = CornerLabelInsets.marginsPx(320, 21, round = true)
        val large = CornerLabelInsets.marginsPx(454, 21, round = true)
        assertEquals(small[0], small[1])
        assertTrue("表盘越大内缩越多", large[0] > small[0])
    }

    @Test
    fun 量不出直径时退回方屏() {
        // 布局还没量完（直径 0）或负值：按方屏处理，别把角标推到屏幕中央
        assertEquals(listOf(21, 21), CornerLabelInsets.marginsPx(0, 21, round = true).toList())
        assertEquals(listOf(21, 21), CornerLabelInsets.marginsPx(-100, 21, round = true).toList())
    }

    @Test
    fun 负的基准边距当零() {
        assertEquals(listOf(56, 56), CornerLabelInsets.marginsPx(380, -30, round = true).toList())
    }
}
