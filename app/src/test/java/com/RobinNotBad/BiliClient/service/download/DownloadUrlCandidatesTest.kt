package com.RobinNotBad.BiliClient.service.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 候选线路组装的单测。
 *
 * 这份列表决定下载失败时按什么顺序换线（反馈 #136），顺序错了就会在坏节点上反复重试，
 * 所以「主线路必须排第一」「空串与重复项要剔掉」这两个性质必须锁死。
 */
class DownloadUrlCandidatesTest {

    @Test
    fun `主线路排在最前，备用线路按原顺序跟随`() {
        val result = DownloadUrlCandidates.of(
            "https://upos-sz-mirror.example/a.m4s",
            listOf(
                "https://upos-hz-mirror.example/a.m4s",
                "https://upos-bstar.example/a.m4s"
            )
        )
        assertEquals(
            listOf(
                "https://upos-sz-mirror.example/a.m4s",
                "https://upos-hz-mirror.example/a.m4s",
                "https://upos-bstar.example/a.m4s"
            ),
            result
        )
    }

    @Test
    fun `没有备用线路时只有主线路`() {
        assertEquals(listOf("https://u.example/a.m4s"), DownloadUrlCandidates.of("https://u.example/a.m4s", null))
        assertEquals(listOf("https://u.example/a.m4s"), DownloadUrlCandidates.of("https://u.example/a.m4s", emptyList()))
    }

    @Test
    fun `主线路为空时备用线路仍然可用`() {
        // 纯音频下载用的用户自选地址可能为空，此时只剩接口给的备用线路，不能整条丢弃
        assertEquals(
            listOf("https://u2.example/a.m4s"),
            DownloadUrlCandidates.of("", listOf("https://u2.example/a.m4s"))
        )
        assertEquals(
            listOf("https://u2.example/a.m4s"),
            DownloadUrlCandidates.of(null, listOf("https://u2.example/a.m4s"))
        )
    }

    @Test
    fun `空串被剔除，重复线路只留第一次出现`() {
        val result = DownloadUrlCandidates.of(
            "https://u.example/a.m4s",
            listOf("", "https://u.example/a.m4s", "https://u2.example/a.m4s", "https://u2.example/a.m4s")
        )
        assertEquals(
            listOf("https://u.example/a.m4s", "https://u2.example/a.m4s"),
            result
        )
    }

    @Test
    fun `全都为空时返回空列表`() {
        assertTrue(DownloadUrlCandidates.of(null, null).isEmpty())
        assertTrue(DownloadUrlCandidates.of("", listOf("", "")).isEmpty())
    }
}
