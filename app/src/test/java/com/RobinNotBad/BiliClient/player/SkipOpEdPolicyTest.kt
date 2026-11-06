package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 五档片头片尾策略的纯逻辑单测。
 *
 * 重点覆盖两件最容易改错的事：
 * 1. 旧布尔键 → 新档位的迁移（升级用户不能因为改档位而丢掉「都跳」）；
 * 2. 「仅头/仅尾」不能误跳另一类片段（跳过是主动改变播放位置，误跳很难被用户原谅）。
 */
class SkipOpEdPolicyTest {

    @Test
    fun `档位收敛——越界与默认值`() {
        assertEquals(SkipOpEdPolicy.MODE_OFF, SkipOpEdPolicy.DEFAULT_MODE)
        assertEquals(SkipOpEdPolicy.MODE_OFF, SkipOpEdPolicy.fromInt(-1))
        assertEquals(SkipOpEdPolicy.MODE_OFF, SkipOpEdPolicy.fromInt(5))
        assertEquals(SkipOpEdPolicy.MODE_ASK, SkipOpEdPolicy.fromInt(4))
        assertEquals(SkipOpEdPolicy.MODE_OFF, SkipOpEdPolicy.fromStored(null))
        assertEquals(SkipOpEdPolicy.MODE_OFF, SkipOpEdPolicy.fromStored(""))
        assertEquals(SkipOpEdPolicy.MODE_OFF, SkipOpEdPolicy.fromStored("abc"))
        assertEquals(3, SkipOpEdPolicy.fromStored(" 3 "))
    }

    @Test
    fun `迁移——新键优先`() {
        assertEquals(SkipOpEdPolicy.MODE_ED, SkipOpEdPolicy.migrate("2", legacyEnabled = true))
        assertEquals(SkipOpEdPolicy.MODE_OFF, SkipOpEdPolicy.migrate("0", legacyEnabled = true))
        assertEquals(SkipOpEdPolicy.MODE_ASK, SkipOpEdPolicy.migrate("4", legacyEnabled = false))
    }

    @Test
    fun `迁移——旧键 true 变都跳、false 与缺失变关闭`() {
        assertEquals(SkipOpEdPolicy.MODE_BOTH, SkipOpEdPolicy.migrate(null, legacyEnabled = true))
        assertEquals(SkipOpEdPolicy.MODE_OFF, SkipOpEdPolicy.migrate(null, legacyEnabled = false))
        assertEquals(SkipOpEdPolicy.MODE_OFF, SkipOpEdPolicy.migrate(null, legacyEnabled = null))
    }

    @Test
    fun `迁移——新键是脏值时同样回落旧键`() {
        assertEquals(SkipOpEdPolicy.MODE_BOTH, SkipOpEdPolicy.migrate("9", legacyEnabled = true))
        assertEquals(SkipOpEdPolicy.MODE_OFF, SkipOpEdPolicy.migrate("", legacyEnabled = false))
        assertEquals(SkipOpEdPolicy.MODE_OFF, SkipOpEdPolicy.migrate("x", legacyEnabled = null))
    }

    @Test
    fun `仅头只跳片头`() {
        val mode = SkipOpEdPolicy.MODE_OP
        assertTrue(SkipOpEdPolicy.shouldAutoSkip(mode, ViewPointSkip.TYPE_OP))
        assertFalse(SkipOpEdPolicy.shouldAutoSkip(mode, ViewPointSkip.TYPE_ED))
        assertFalse(SkipOpEdPolicy.shouldAsk(mode, ViewPointSkip.TYPE_OP))
    }

    @Test
    fun `仅尾只跳片尾`() {
        val mode = SkipOpEdPolicy.MODE_ED
        assertFalse(SkipOpEdPolicy.shouldAutoSkip(mode, ViewPointSkip.TYPE_OP))
        assertTrue(SkipOpEdPolicy.shouldAutoSkip(mode, ViewPointSkip.TYPE_ED))
    }

    @Test
    fun `都跳两类都跳、关与询问都不自动跳`() {
        assertTrue(SkipOpEdPolicy.shouldAutoSkip(SkipOpEdPolicy.MODE_BOTH, ViewPointSkip.TYPE_OP))
        assertTrue(SkipOpEdPolicy.shouldAutoSkip(SkipOpEdPolicy.MODE_BOTH, ViewPointSkip.TYPE_ED))
        assertFalse(SkipOpEdPolicy.shouldAutoSkip(SkipOpEdPolicy.MODE_OFF, ViewPointSkip.TYPE_OP))
        assertFalse(SkipOpEdPolicy.shouldAutoSkip(SkipOpEdPolicy.MODE_ASK, ViewPointSkip.TYPE_OP))
        assertFalse(SkipOpEdPolicy.shouldAutoSkip(SkipOpEdPolicy.MODE_ASK, ViewPointSkip.TYPE_ED))
    }

    @Test
    fun `询问档只对片头片尾询问`() {
        val mode = SkipOpEdPolicy.MODE_ASK
        assertTrue(SkipOpEdPolicy.shouldAsk(mode, ViewPointSkip.TYPE_OP))
        assertTrue(SkipOpEdPolicy.shouldAsk(mode, ViewPointSkip.TYPE_ED))
        // 其它类型（普通分段）既不自动跳也不问
        assertFalse(SkipOpEdPolicy.shouldAsk(mode, 0))
        assertFalse(SkipOpEdPolicy.shouldAutoSkip(mode, 0))
    }

    @Test
    fun `启用判定——关是唯一停用档`() {
        assertFalse(SkipOpEdPolicy.isEnabled(SkipOpEdPolicy.MODE_OFF))
        assertTrue(SkipOpEdPolicy.isEnabled(SkipOpEdPolicy.MODE_OP))
        assertTrue(SkipOpEdPolicy.isEnabled(SkipOpEdPolicy.MODE_ED))
        assertTrue(SkipOpEdPolicy.isEnabled(SkipOpEdPolicy.MODE_BOTH))
        assertTrue(SkipOpEdPolicy.isEnabled(SkipOpEdPolicy.MODE_ASK))
    }
}
