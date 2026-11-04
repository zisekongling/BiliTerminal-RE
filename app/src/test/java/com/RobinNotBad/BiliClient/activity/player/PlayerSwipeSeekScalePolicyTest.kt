package com.RobinNotBad.BiliClient.activity.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerSwipeSeekScalePolicy] 的纯 JVM 单测。
 *
 * 覆盖反馈 #94a 的三个要害：档位读写的容错、显示名与存档值一一对应、
 * 换算结果必须落进 `[0, duration]`（否则会 seek 到视频外）。
 */
class PlayerSwipeSeekScalePolicyTest {

    @Test
    fun 没有存档时用默认的1倍率() {
        assertEquals("空存档必须退回 1.0x，保证老用户手感不变", 1.0f, PlayerSwipeSeekScalePolicy.fromStored(null), 0.0f)
        assertEquals("空白存档同样退回默认", 1.0f, PlayerSwipeSeekScalePolicy.fromStored("  "), 0.0f)
    }

    @Test
    fun 六档倍率都能原样读回() {
        val expected = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f)
        expected.forEach { scale ->
            assertEquals("档位 ${PlayerSwipeSeekScalePolicy.format(scale)} 读回不一致", scale,
                PlayerSwipeSeekScalePolicy.fromStored(PlayerSwipeSeekScalePolicy.format(scale)), 0.0f)
        }
    }

    @Test
    fun 脏存档退回默认倍率() {
        assertEquals("非数字退回默认", 1.0f, PlayerSwipeSeekScalePolicy.fromStored("abc"), 0.0f)
        assertEquals("档位外的数字退回默认", 1.0f, PlayerSwipeSeekScalePolicy.fromStored("10"), 0.0f)
        assertEquals("负数退回默认", 1.0f, PlayerSwipeSeekScalePolicy.fromStored("-2"), 0.0f)
    }

    @Test
    fun 显示名与存档值一一对应且顺序一致() {
        assertEquals("显示名顺序", listOf("0.25x", "0.5x", "1.0x", "1.5x", "2.0x", "3.0x"),
            PlayerSwipeSeekScalePolicy.displayNames)
        assertEquals("存档值顺序", listOf("0.25", "0.5", "1.0", "1.5", "2.0", "3.0"),
            PlayerSwipeSeekScalePolicy.storedValues)
        assertEquals("两列必须等长", PlayerSwipeSeekScalePolicy.displayNames.size,
            PlayerSwipeSeekScalePolicy.storedValues.size)
    }

    @Test
    fun 整数倍率补一位小数() {
        assertEquals("1x 应显示成 1.0", "1.0", PlayerSwipeSeekScalePolicy.format(1.0f))
        assertEquals("2x 应显示成 2.0", "2.0", PlayerSwipeSeekScalePolicy.format(2.0f))
        assertEquals("0.25x 保留两位", "0.25", PlayerSwipeSeekScalePolicy.format(0.25f))
    }

    @Test
    fun 只有列出的档位算合法() {
        assertTrue("1x 是合法档位", PlayerSwipeSeekScalePolicy.isSupported(1.0f))
        assertTrue("0.25x 是合法档位", PlayerSwipeSeekScalePolicy.isSupported(0.25f))
        assertFalse("1.2x 不在档位里", PlayerSwipeSeekScalePolicy.isSupported(1.2f))
    }

    @Test
    fun 换算默认倍率时位移比例与进度一致() {
        // 宽 1000px、时长 100 秒、起点 10 秒，1x 时向右划 250px 正好 +25 秒
        val target = PlayerSwipeSeekScalePolicy.targetMs(
            startMs = 10_000L, deltaX = 250f, widthPx = 1000f, durationMs = 100_000L, scale = 1.0f
        )
        assertEquals("1x 应等于原来的 1:1 换算", 35_000L, target)
    }

    @Test
    fun 半倍率时同样的位移只走一半() {
        val target = PlayerSwipeSeekScalePolicy.targetMs(
            startMs = 10_000L, deltaX = 500f, widthPx = 1000f, durationMs = 100_000L, scale = 0.5f
        )
        assertEquals("0.5x 应只走一半进度", 35_000L, target)
    }

    @Test
    fun 三倍率时同样的位移走三倍() {
        val target = PlayerSwipeSeekScalePolicy.targetMs(
            startMs = 10_000L, deltaX = 100f, widthPx = 1000f, durationMs = 100_000L, scale = 3.0f
        )
        assertEquals("3x 应走 30 秒", 40_000L, target)
    }

    @Test
    fun 高倍率冲过头时钳到末尾() {
        val target = PlayerSwipeSeekScalePolicy.targetMs(
            startMs = 10_000L, deltaX = 600f, widthPx = 1000f, durationMs = 100_000L, scale = 2.0f
        )
        assertEquals("超出时长必须钳到 duration", 100_000L, target)
    }

    @Test
    fun 反向冲过头时钳到开头() {
        val target = PlayerSwipeSeekScalePolicy.targetMs(
            startMs = 10_000L, deltaX = -600f, widthPx = 1000f, durationMs = 100_000L, scale = 3.0f
        )
        assertEquals("小于 0 必须钳到 0", 0L, target)
    }

    @Test
    fun 非法倍率或宽度为0时退回安全值() {
        val fallbackScale = PlayerSwipeSeekScalePolicy.targetMs(
            startMs = 10_000L, deltaX = 250f, widthPx = 1000f, durationMs = 100_000L, scale = 1.7f
        )
        assertEquals("档位外的倍率按 1x 算", 35_000L, fallbackScale)
        val zeroWidth = PlayerSwipeSeekScalePolicy.targetMs(
            startMs = 10_000L, deltaX = 250f, widthPx = 0f, durationMs = 100_000L, scale = 1.0f
        )
        assertEquals("宽度为 0 时按 1px 兜底不除零，结果会被时长钳住", 100_000L, zeroWidth)
    }

    @Test
    fun 时长无效时不动位置() {
        assertEquals("直播/未 prepare 时保持起点", 8_000L,
            PlayerSwipeSeekScalePolicy.targetMs(8_000L, 500f, 1000f, 0L, 1.0f))
        assertEquals("起点为负时归零", 0L,
            PlayerSwipeSeekScalePolicy.targetMs(-5_000L, 500f, 1000f, 0L, 1.0f))
    }
}
