package com.RobinNotBad.BiliClient.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SponsorBlockApi] 的纯 JVM 单测（只测纯函数，不发请求）。
 *
 * 两个钉子：
 * 1. [SponsorBlockApi.hashPrefix] 必须与上游算法一致（SHA-256 十六进制前 8 位）——
 *    算错了不是"查不到"，而是**查到别的视频的片段**；
 * 2. [SponsorBlockApi.parseSegments] 必须按 `videoID` 把同前缀的无关视频滤掉
 *    （实测 `d5c2` 前缀一次回了 7 个 BVID），否则会把别人的广告秒数套到正在看的视频上。
 */
class SponsorBlockApiTest {

    private val bvid = "BV1GJ411x7h7"

    /** 实测响应形状：`[{videoID, segments:[…]}, …]`，cid 可能是字符串。 */
    private val realWorldBody = """
        [
          {
            "videoID": "BV11Kc2zZEDK",
            "segments": [
              {"cid":"36656057057","category":"intro","actionType":"skip","segment":[0,20.934],
               "UUID":"5ba43c749b5dcc94","videoDuration":231,"locked":0,"votes":0}
            ]
          },
          {
            "videoID": "$bvid",
            "segments": [
              {"cid":"32175295832","category":"sponsor","actionType":"skip","segment":[88.5,124.25],
               "UUID":"61bbb9f0","videoDuration":169,"locked":0,"votes":3},
              {"cid":"32175295832","category":"sponsor","actionType":"full","segment":[0,0],
               "UUID":"61bbb9f1","videoDuration":169,"locked":0,"votes":1}
            ]
          }
        ]
    """.trimIndent()

    // ---------- 哈希 ----------

    @Test
    fun `哈希前缀与上游算法一致`() {
        // sha256("BV1GJ411x7h7") = d5c2bf68f837…90cf，实测这个前缀能查到数据
        assertEquals("d5c2bf68", SponsorBlockApi.hashPrefix(bvid))
        assertEquals(8, SponsorBlockApi.hashPrefix(bvid).length)
        assertEquals(
            "同一 BVID 必须稳定",
            SponsorBlockApi.hashPrefix(bvid),
            SponsorBlockApi.hashPrefix(bvid)
        )
        assertNotEquals(
            "不同 BVID 不能撞同一个前缀",
            SponsorBlockApi.hashPrefix(bvid),
            SponsorBlockApi.hashPrefix("BV1GJ411x7h8")
        )
    }

    // ---------- 解析 ----------

    @Test
    fun `同前缀的别的视频要整条滤掉`() {
        val segments = SponsorBlockApi.parseSegments(realWorldBody, bvid)
        assertEquals(2, segments.size)
        assertTrue(
            "BV11Kc2zZEDK 的 intro 片段不能混进来",
            segments.none { it.category == "intro" }
        )
    }

    @Test
    fun `videoID 大小写不敏感`() {
        val body = """
            [{"videoID":"bv1gj411x7h7","segments":[
              {"cid":"1","category":"sponsor","actionType":"skip","segment":[1,2],"videoDuration":10}
            ]}]
        """.trimIndent()
        assertEquals(1, SponsorBlockApi.parseSegments(body, bvid).size)
    }

    @Test
    fun `字段翻译正确`() {
        val sponsor = SponsorBlockApi.parseSegments(realWorldBody, bvid).first()
        assertEquals(32175295832L, sponsor.cid)
        assertEquals("sponsor", sponsor.category)
        assertEquals("skip", sponsor.actionType)
        assertEquals(88.5, sponsor.fromSec, 0.001)
        assertEquals(124.25, sponsor.toSec, 0.001)
        assertEquals(169.0, sponsor.videoDuration, 0.001)
        assertEquals(3, sponsor.votes)
    }

    @Test
    fun `按 cid 过滤分 P`() {
        // 传了另一个 cid：这个视频的两条标注都属于 32175295832，应当全被滤掉
        assertTrue(
            SponsorBlockApi.parseSegments(realWorldBody, bvid, cid = 999L).isEmpty()
        )
        assertEquals(
            2,
            SponsorBlockApi.parseSegments(realWorldBody, bvid, cid = 32175295832L).size
        )
    }

    @Test
    fun `上游没给 cid 时放行`() {
        val body = """
            [{"videoID":"$bvid","segments":[
              {"category":"sponsor","actionType":"skip","segment":[5,15],"videoDuration":100}
            ]}]
        """.trimIndent()
        val segments = SponsorBlockApi.parseSegments(body, bvid, cid = 123L)
        assertEquals(1, segments.size)
        assertEquals(0L, segments[0].cid)
    }

    @Test
    fun `缺字段的片段跳过而不是造一条零区间`() {
        val body = """
            [{"videoID":"$bvid","segments":[
              {"cid":"1","category":"sponsor","actionType":"skip"},
              {"cid":"1","category":"sponsor","actionType":"skip","segment":[]},
              {"cid":"1","category":"sponsor","actionType":"skip","segment":[1]},
              {"cid":"1","category":"sponsor","actionType":"skip","segment":["x","y"]},
              {"cid":"1","category":"sponsor","actionType":"skip","segment":[5,15],"videoDuration":100}
            ]}]
        """.trimIndent()
        val segments = SponsorBlockApi.parseSegments(body, bvid)
        assertEquals(1, segments.size)
        assertEquals(5.0, segments[0].fromSec, 0.001)
    }

    @Test
    fun `空数组与非 JSON 都返回空表`() {
        assertTrue(SponsorBlockApi.parseSegments("[]", bvid).isEmpty())
        assertTrue(SponsorBlockApi.parseSegments("", bvid).isEmpty())
        // 上游改口径/被 nginx 换成 HTML 时不能抛给播放器
        assertTrue(SponsorBlockApi.parseSegments("<html>502 Bad Gateway</html>", bvid).isEmpty())
        assertTrue(SponsorBlockApi.parseSegments("""{"error":"nope"}""", bvid).isEmpty())
    }

    @Test
    fun `空 bvid 直接返回空表`() {
        assertTrue(SponsorBlockApi.fetchSegments("", 1L).isEmpty())
        // 会抛异常的版本也一样：bvid 为空不是「没查到」，不该让调用方去重试
        assertTrue(SponsorBlockApi.fetchSegmentsOrThrow("", 1L).isEmpty())
    }
}
