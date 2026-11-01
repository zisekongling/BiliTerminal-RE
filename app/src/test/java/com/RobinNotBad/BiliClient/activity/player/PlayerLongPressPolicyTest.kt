package com.RobinNotBad.BiliClient.activity.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerLongPressPolicy] 的纯 JVM 单测（反馈 #61「自定义长按倍速」）。
 *
 * 钉住三件事：
 * 1. **默认值必须还是 3.0**——没动过设置的用户长按行为要与改造前完全一致；
 * 2. **脏存档不能直接进 `setSpeed`**——0 / 负数 / 手改的 2.5 都退回默认，否则播放器不是慢放而是卡住；
 * 3. **提示文字与实际倍率一致**——播放页的 `text_speed` 原来写死 `x 3.0`。
 */
class PlayerLongPressPolicyTest {

    @Test
    fun 没有存档时用默认的3倍速() {
        assertEquals(
            "改造前长按写死 3.0F，没动过设置的用户必须保持这个行为",
            PlayerLongPressPolicy.DEFAULT_SPEED,
            PlayerLongPressPolicy.fromStored(null),
            0.0f
        )
        assertEquals(3.0f, PlayerLongPressPolicy.DEFAULT_SPEED, 0.0f)
        assertEquals("3.0", PlayerLongPressPolicy.DEFAULT_STORED)
    }

    @Test
    fun 四个档位都能读回来() {
        assertEquals(1.5f, PlayerLongPressPolicy.fromStored("1.5"), 0.0f)
        assertEquals(2.0f, PlayerLongPressPolicy.fromStored("2.0"), 0.0f)
        assertEquals(3.0f, PlayerLongPressPolicy.fromStored("3.0"), 0.0f)
        assertEquals(5.0f, PlayerLongPressPolicy.fromStored("5.0"), 0.0f)
        // 设置页落盘的是不带 x 的纯数字，带空格也要能读
        assertEquals(2.0f, PlayerLongPressPolicy.fromStored(" 2.0 "), 0.0f)
    }

    @Test
    fun 脏存档一律退回默认() {
        assertEquals("空串", PlayerLongPressPolicy.DEFAULT_SPEED, PlayerLongPressPolicy.fromStored(""), 0.0f)
        assertEquals("非数字", PlayerLongPressPolicy.DEFAULT_SPEED, PlayerLongPressPolicy.fromStored("fast"), 0.0f)
        assertEquals("档位外的手改值", PlayerLongPressPolicy.DEFAULT_SPEED, PlayerLongPressPolicy.fromStored("2.5"), 0.0f)
        assertEquals("0 会让播放器不动", PlayerLongPressPolicy.DEFAULT_SPEED, PlayerLongPressPolicy.fromStored("0"), 0.0f)
        assertEquals("负数", PlayerLongPressPolicy.DEFAULT_SPEED, PlayerLongPressPolicy.fromStored("-1"), 0.0f)
        assertEquals("超大值", PlayerLongPressPolicy.DEFAULT_SPEED, PlayerLongPressPolicy.fromStored("99"), 0.0f)
    }

    @Test
    fun 落盘与显示格式() {
        assertEquals("1.5", PlayerLongPressPolicy.format(1.5f))
        assertEquals("2.0", PlayerLongPressPolicy.format(2.0f))
        assertEquals("3.0", PlayerLongPressPolicy.format(3.0f))
        assertEquals("5.0", PlayerLongPressPolicy.format(5.0f))
        // 播放页提示沿用既有 speed_strs 的写法（"x 3.0"）
        assertEquals("x 3.0", PlayerLongPressPolicy.labelOf(3.0f))
        assertEquals("x 1.5", PlayerLongPressPolicy.labelOf(1.5f))
    }

    @Test
    fun 设置页的选项与落盘值一一对应() {
        assertEquals(4, PlayerLongPressPolicy.displayNames.size)
        assertEquals(PlayerLongPressPolicy.displayNames.size, PlayerLongPressPolicy.storedValues.size)
        assertEquals(listOf("1.5", "2.0", "3.0", "5.0"), PlayerLongPressPolicy.storedValues)
        assertEquals(listOf("1.5x", "2.0x", "3.0x", "5.0x"), PlayerLongPressPolicy.displayNames)
        // 每个落盘值都必须能被读回同一个倍率，否则"设置页选了 2x、长按还是 3x"
        for (i in PlayerLongPressPolicy.storedValues.indices) {
            val speed = PlayerLongPressPolicy.fromStored(PlayerLongPressPolicy.storedValues[i])
            assertEquals(
                "落盘值 " + PlayerLongPressPolicy.storedValues[i] + " 读回来必须还是它自己",
                PlayerLongPressPolicy.displayNames[i],
                PlayerLongPressPolicy.format(speed) + "x"
            )
            assertTrue(PlayerLongPressPolicy.isSupported(speed))
        }
    }

    @Test
    fun 档位表之外的值不算受支持() {
        assertFalse(PlayerLongPressPolicy.isSupported(1.0f))
        assertFalse(PlayerLongPressPolicy.isSupported(2.5f))
        assertTrue(PlayerLongPressPolicy.isSupported(5.0f))
    }
}
