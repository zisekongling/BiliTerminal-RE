package com.RobinNotBad.BiliClient.activity.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PlayerJumpTimePolicy] 的纯 JVM 单测。
 *
 * 覆盖 QQ 反馈 #9 的解析规则：纯秒数 / `mm:ss` / `hh:mm:ss`、
 * 非法输入必须返回 null（播放页据此提示后不跳）、越界必须钳到末尾。
 */
class PlayerJumpTimePolicyTest {

    @Test
    fun 纯秒数按秒解析() {
        assertEquals("90 应解析为 90 秒", 90L, PlayerJumpTimePolicy.parseToSeconds("90"))
        assertEquals("前后空白要去掉", 5L, PlayerJumpTimePolicy.parseToSeconds("  5  "))
        assertEquals("0 也是合法输入", 0L, PlayerJumpTimePolicy.parseToSeconds("0"))
    }

    @Test
    fun 分秒格式按分秒解析() {
        assertEquals("1:30 应为 90 秒", 90L, PlayerJumpTimePolicy.parseToSeconds("1:30"))
        assertEquals("10:00 应为 600 秒", 600L, PlayerJumpTimePolicy.parseToSeconds("10:00"))
    }

    @Test
    fun 时分秒格式按时分秒解析() {
        assertEquals("1:02:03 应为 3723 秒", 3723L, PlayerJumpTimePolicy.parseToSeconds("1:02:03"))
        assertEquals("0:00:30 应为 30 秒", 30L, PlayerJumpTimePolicy.parseToSeconds("0:00:30"))
    }

    @Test
    fun 手表输入法打出的全角冒号也能认() {
        assertEquals("全角冒号按半角处理", 90L, PlayerJumpTimePolicy.parseToSeconds("1：30"))
    }

    @Test
    fun 空输入与纯空白返回null() {
        assertNull("null 输入", PlayerJumpTimePolicy.parseToSeconds(null))
        assertNull("空串", PlayerJumpTimePolicy.parseToSeconds(""))
        assertNull("纯空白", PlayerJumpTimePolicy.parseToSeconds("   "))
    }

    @Test
    fun 含非数字字符返回null() {
        assertNull("字母混进来", PlayerJumpTimePolicy.parseToSeconds("1a:30"))
        assertNull("负号不是时间格式", PlayerJumpTimePolicy.parseToSeconds("-30"))
        assertNull("小数点不支持", PlayerJumpTimePolicy.parseToSeconds("1.5"))
    }

    @Test
    fun 秒数达到60判非法() {
        assertNull("1:60 是误输入，不是 120 秒", PlayerJumpTimePolicy.parseToSeconds("1:60"))
        assertNull("1:90 同样非法", PlayerJumpTimePolicy.parseToSeconds("1:90"))
        assertNull("秒段必须小于 60", PlayerJumpTimePolicy.parseToSeconds("0:00:60"))
    }

    @Test
    fun 分段过多或存在空段判非法() {
        assertNull("四段不认", PlayerJumpTimePolicy.parseToSeconds("1:2:3:4"))
        assertNull("尾部空段", PlayerJumpTimePolicy.parseToSeconds("1:"))
        assertNull("头部空段", PlayerJumpTimePolicy.parseToSeconds(":30"))
        assertNull("双冒号", PlayerJumpTimePolicy.parseToSeconds("1::30"))
    }

    @Test
    fun 超长数字导致溢出时判非法() {
        assertNull("溢出后不能送一个负数位置过去", PlayerJumpTimePolicy.parseToSeconds("999999999999999999:00"))
    }

    @Test
    fun 目标在时长内时原样返回() {
        assertEquals("5 秒在 100 秒内", 5_000L, PlayerJumpTimePolicy.clampMs(5_000L, 100_000L))
    }

    @Test
    fun 超过时长钳到末尾() {
        assertEquals("超过时长就必须钳到 duration", 100_000L, PlayerJumpTimePolicy.clampMs(999_000L, 100_000L))
    }

    @Test
    fun 负目标钳到开头() {
        assertEquals("负数目标钳到 0", 0L, PlayerJumpTimePolicy.clampMs(-1L, 100_000L))
    }

    @Test
    fun 时长无效时不可跳() {
        assertNull("直播 duration 为 0", PlayerJumpTimePolicy.clampMs(5_000L, 0L))
        assertNull("未 prepare 时 duration 为负", PlayerJumpTimePolicy.clampMs(5_000L, -1L))
    }

    @Test
    fun 预填文本按播放位置格式化() {
        assertNull("位置 0 不预填（空框只留提示语）", PlayerJumpTimePolicy.prefillText(0L))
        assertNull("负数位置不预填", PlayerJumpTimePolicy.prefillText(-1L))
        assertEquals("90 秒写成 01:30", "01:30", PlayerJumpTimePolicy.prefillText(90_000L))
        assertEquals("毫秒零头直接截掉", "01:30", PlayerJumpTimePolicy.prefillText(90_999L))
        assertEquals("不足一分钟也要补零", "00:05", PlayerJumpTimePolicy.prefillText(5_000L))
        assertEquals("超过一小时带小时段", "1:02:03", PlayerJumpTimePolicy.prefillText(3_723_000L))
    }

    @Test
    fun 预填文本能被原样解析回同一秒() {
        // 「不改直接确定」必须等于原地不动：预填文本与解析器必须同源，不能出现格式对不上的非法输入
        val positions = listOf(1_000L, 59_999L, 60_000L, 3_599_000L, 3_600_000L, 7_384_000L)
        for (positionMs in positions) {
            val text = PlayerJumpTimePolicy.prefillText(positionMs)!!
            assertEquals(
                "预填「$text」应解析回 ${positionMs / 1000} 秒",
                positionMs / 1000,
                PlayerJumpTimePolicy.parseToSeconds(text)
            )
        }
    }
}
