package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三通道选版规则的纯函数单测（口径见 `docs/version-channels.md`）。
 *
 * 这里守的是**最容易静默出错**的一条：装 beta 包的人能收到稳定版，装稳定版的人收不到 beta 包；
 * 以及不能依赖接口返回顺序（Gitee 升序、GitHub 降序，两个方向相反）。
 */
class UpdateChannelTest {

    // ---------- tag → 渠道 ----------

    @Test
    fun channelOfTag_readsSuffix() {
        assertEquals("无后缀就是稳定版", UpdateChannel.STABLE, UpdateChannel.channelOfTag("26.11.08"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.channelOfTag("26.11.08.1"))
        assertEquals(UpdateChannel.BETA, UpdateChannel.channelOfTag("26.11.08-beta.1"))
        assertEquals(UpdateChannel.EXP, UpdateChannel.channelOfTag("26.11.08-exp.2"))
    }

    @Test
    fun channelOfTag_rejectsUnknownSuffix() {
        assertEquals(
            "认不出的后缀要返回空串，让调用方一律不接收",
            "",
            UpdateChannel.channelOfTag("26.09.24-BETA1")
        )
        assertEquals("", UpdateChannel.channelOfTag("26.11.08-security"))
        assertEquals("", UpdateChannel.channelOfTag(""))
        assertEquals("", UpdateChannel.channelOfTag(null))
    }

    // ---------- 渠道收哪些版本 ----------

    @Test
    fun accepts_matrix() {
        assertTrue(UpdateChannel.accepts(UpdateChannel.STABLE, "26.11.08"))
        assertFalse("稳定版不该收到 beta 包", UpdateChannel.accepts(UpdateChannel.STABLE, "26.11.08-beta.1"))
        assertFalse(UpdateChannel.accepts(UpdateChannel.STABLE, "26.11.08-exp.1"))

        assertTrue("测试版能收到稳定版", UpdateChannel.accepts(UpdateChannel.BETA, "26.11.08"))
        assertTrue(UpdateChannel.accepts(UpdateChannel.BETA, "26.11.08-beta.2"))
        assertFalse("测试版不该收到实验版", UpdateChannel.accepts(UpdateChannel.BETA, "26.11.08-exp.1"))

        assertTrue(UpdateChannel.accepts(UpdateChannel.EXP, "26.11.08"))
        assertTrue(UpdateChannel.accepts(UpdateChannel.EXP, "26.11.08-beta.1"))
        assertTrue(UpdateChannel.accepts(UpdateChannel.EXP, "26.11.08-exp.1"))
    }

    @Test
    fun accepts_neverTakesUnreadableTag() {
        // 老 tag 读不出渠道，任何渠道都不该拿到它——否则一边推送一边版本比较会打架
        assertFalse(UpdateChannel.accepts(UpdateChannel.EXP, "26.09.24-BETA1"))
    }

    // ---------- 一组候选里挑哪个 ----------

    @Test
    fun bestOf_ignoresListOrder() {
        // Gitee 升序、GitHub 降序，两种顺序都要挑出同一个
        val asc = listOf(
            UpdateChannel.Candidate("26.11.05", 26110500),
            UpdateChannel.Candidate("26.11.06", 26110600),
            UpdateChannel.Candidate("26.11.07", 26110700)
        )
        assertEquals("26.11.07", UpdateChannel.bestOf(asc, UpdateChannel.STABLE)?.tag)
        assertEquals("26.11.07", UpdateChannel.bestOf(asc.reversed(), UpdateChannel.STABLE)?.tag)
    }

    @Test
    fun bestOf_respectsChannel() {
        // 同一天发过 beta，稳定版仍只挑无后缀那条
        val list = listOf(
            UpdateChannel.Candidate("26.11.08", 26110800),
            UpdateChannel.Candidate("26.11.08-beta.1", 26110811),
            UpdateChannel.Candidate("26.11.08-exp.1", 26110821)
        )
        assertEquals("26.11.08", UpdateChannel.bestOf(list, UpdateChannel.STABLE)?.tag)
        assertEquals("26.11.08-beta.1", UpdateChannel.bestOf(list, UpdateChannel.BETA)?.tag)
        assertEquals("26.11.08-exp.1", UpdateChannel.bestOf(list, UpdateChannel.EXP)?.tag)
    }

    @Test
    fun bestOf_skipsDirtyCandidates() {
        val list = listOf(
            UpdateChannel.Candidate("26.11.09", 0),        // 解析不出元数据
            UpdateChannel.Candidate("26.11.09", -1),
            UpdateChannel.Candidate("26.11.09-security", 26110999)
        )
        assertNull("全是脏数据时应返回 null，让上层报「没有可用版本」", UpdateChannel.bestOf(list, UpdateChannel.EXP))
        assertNull(UpdateChannel.bestOf(emptyList(), UpdateChannel.STABLE))
    }

    // ---------- 渠道名归一化 / 渠道码 ----------

    @Test
    fun normalize_fallsBackToStable() {
        assertEquals(UpdateChannel.BETA, UpdateChannel.normalize("beta"))
        assertEquals(UpdateChannel.BETA, UpdateChannel.normalize(" BETA "))
        assertEquals(UpdateChannel.EXP, UpdateChannel.normalize("exp"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.normalize("aloha"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.normalize(""))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.normalize(null))
    }

    @Test
    fun codeOf_matchesVersionCodeLayout() {
        assertEquals(0, UpdateChannel.codeOf(UpdateChannel.STABLE))
        assertEquals(1, UpdateChannel.codeOf(UpdateChannel.BETA))
        assertEquals(2, UpdateChannel.codeOf(UpdateChannel.EXP))
        // 同一天：稳定 < 测试 < 实验，客户端「code > 当前才算有更新」才能照旧用
        val day = 26110800
        assertTrue(day + UpdateChannel.codeOf(UpdateChannel.BETA) * 10 > day)
        assertTrue(
            day + UpdateChannel.codeOf(UpdateChannel.EXP) * 10 >
                day + UpdateChannel.codeOf(UpdateChannel.BETA) * 10
        )
    }

    @Test
    fun nameRes_mapsEachChannel() {
        assertEquals(R.string.update_channel_stable, UpdateChannel.nameRes(null))
        assertEquals(R.string.update_channel_stable, UpdateChannel.nameRes(UpdateChannel.STABLE))
        assertEquals(R.string.update_channel_beta, UpdateChannel.nameRes(UpdateChannel.BETA))
        assertEquals(R.string.update_channel_exp, UpdateChannel.nameRes(UpdateChannel.EXP))
    }
}
