package com.RobinNotBad.BiliClient.util.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「界面边距」+ 圆屏适配的留白算法（26.11.02：伪装页未做圆屏适配）。
 *
 * 断言里用的 380×380 / 320×320 与 [DialogSizingTest] 一致，都是圆形手表上的常见面板。
 * 这套算术原先只存在于 `BaseActivity.onCreate`，伪装页另抄了一份还把百分比当 dp——
 * 这里把它钉死，两边以后都只能走 [RoundInsets]。
 */
class RoundInsetsTest {

    @Test
    fun 界面边距按百分比算而不是按dp() {
        // 380 屏、横 5% / 纵 3%：19 / 11（伪装页原来算的是 5 / 3）
        assertEquals(
            RoundInsets.Insets(left = 19, top = 11, right = 19, bottom = 11),
            RoundInsets.compute(380, 380, 5, 3, round = false)
        )
    }

    @Test
    fun 圆屏适配在底部多让三个百分点() {
        // 圆形表盘底部被表冠/下巴压住一段：380 × 3% = 11，叠在纵边距 11 之上
        assertEquals(
            RoundInsets.Insets(left = 19, top = 11, right = 19, bottom = 22),
            RoundInsets.compute(380, 380, 5, 3, round = true)
        )
        // 只开了圆屏、没设边距：底部仍然让（这一档原先被 `边距全 0 就整段跳过` 挡掉了）
        assertEquals(
            RoundInsets.Insets(left = 0, top = 0, right = 0, bottom = 11),
            RoundInsets.compute(380, 380, 0, 0, round = true)
        )
    }

    @Test
    fun 边距百分比越界时按上下限夹() {
        // 负值当 0；超过 30% 按 30% 算（设置页输入框上限 30，改库/恢复备份可能塞进更大的值）
        assertEquals(
            RoundInsets.Insets(left = 0, top = 0, right = 0, bottom = 0),
            RoundInsets.compute(380, 380, -5, -1, round = false)
        )
        assertEquals(
            RoundInsets.Insets(left = 114, top = 114, right = 114, bottom = 114),
            RoundInsets.compute(380, 380, 200, 200, round = false)
        )
    }

    @Test
    fun 全零边距与非零边距的判据() {
        assertTrue(RoundInsets.compute(380, 380, 0, 0, round = false).isEmpty)
        assertTrue(!RoundInsets.compute(380, 380, 5, 0, round = false).isEmpty)
        // 只有圆屏底部那 3%：也不能当成"全零"跳过（伪装页/页面的门槛就靠它）
        assertTrue(!RoundInsets.compute(380, 380, 0, 0, round = true).isEmpty)
    }

    @Test
    fun 内接正方形边距能把整屏内容收进圆内() {
        // 380 表盘：内接正方形边长 380 / √2 = 268.7，边距要 ceil 到 56 才不会差那 0.3px
        assertEquals(56, RoundInsets.inscribedInsetPx(380))
        for (d in intArrayOf(320, 360, 380, 400, 450, 466)) {
            val edge = RoundInsets.inscribedInsetPx(d)
            val side = d - edge * 2
            assertTrue(
                "直径 $d：收进来的 ${side}×${side} 仍落在圆外（边距 $edge）",
                DialogSizing.fitsInRoundScreen(side, side, d)
            )
            // 收得进来，但也不能收得过分：至少留 0.68 × 直径
            assertTrue("直径 $d：边距 $edge 把内容收得太小", side >= (d * 0.68f).toInt())
        }
        // 退化尺寸不能算出负数边距
        assertEquals(0, RoundInsets.inscribedInsetPx(0))
        assertEquals(0, RoundInsets.inscribedInsetPx(-100))
    }
}
