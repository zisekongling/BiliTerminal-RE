package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「硬币变化记录」变化量文本。
 *
 * 守的是用户直接看得见的错：打赏收入被截成 0（`0.1` / `0.2`）、支出少了符号、零多出小数点，
 * 以及「负小数按原值显示、不按正负推 0.1 硬币单位」这条口径别被人改回去。
 */
class CoinLogDeltaTest {

    // ---------- 整数：与旧行为一致 ----------

    @Test
    fun 整数收入前置加号支出直接带负号() {
        assertEquals("+1", CoinLogDelta.text(1.0))
        assertEquals("+5", CoinLogDelta.text(5.0))
        assertEquals("+20", CoinLogDelta.text(20.0))
        assertEquals("-1", CoinLogDelta.text(-1.0))
        assertEquals("-2", CoinLogDelta.text(-2.0))
    }

    @Test
    fun 零不带符号() {
        assertEquals("0", CoinLogDelta.text(0.0))
        // -0.0 不能漏成 "-0" 或 "0.0"
        assertEquals("0", CoinLogDelta.text(-0.0))
    }

    // ---------- 小数：以前一律截断成 0 ----------

    @Test
    fun 打赏收入的小数原样显示() {
        // 接口文档 x/member/web/coin/log 的样本里就有 0.1 / 0.2 这种打赏收入
        assertEquals("+0.1", CoinLogDelta.text(0.1))
        assertEquals("+0.2", CoinLogDelta.text(0.2))
        assertEquals("+0.5", CoinLogDelta.text(0.5))
        assertEquals("+0.25", CoinLogDelta.text(0.25))
    }

    @Test
    fun 负的小数按原值显示不做单位推断() {
        // 接口样本里没有任何负小数（支出全是 -1 这种整数），所以真出现也照原样显示，
        // 不能像 26.11.14 第一版那样乘 10 编出一个 -1 / -2 来
        assertEquals("-0.1", CoinLogDelta.text(-0.1))
        assertEquals("-0.2", CoinLogDelta.text(-0.2))
        assertEquals("-0.15", CoinLogDelta.text(-0.15))
    }

    // ---------- 边界 ----------

    @Test
    fun 异常数值不崩() {
        // getDouble 正常不会给出这些值，但纯函数不该因为一个 NaN 把整页流水打挂
        assertEquals("0", CoinLogDelta.text(Double.NaN))
        assertEquals("0", CoinLogDelta.text(Double.POSITIVE_INFINITY))
        assertEquals("0", CoinLogDelta.text(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun 十进制精确不出浮点尾巴() {
        // 0.1 * 10 在二进制浮点里是 1.0000000000000002 这类值，这里走 BigDecimal 精确算术
        assertEquals("+0.3", CoinLogDelta.text(0.3))
        assertEquals("-0.3", CoinLogDelta.text(-0.3))
        assertEquals("+1.1", CoinLogDelta.text(1.1))
    }
}
