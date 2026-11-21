package com.RobinNotBad.BiliClient.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayList

/**
 * [LiveDanmakuApi] 的纯 JVM 单测（反馈 #156）。
 *
 * 只测**不碰网络、不碰 Android** 的那部分：字数校验、返回码映射、请求体拼装、
 * header 替换、本地节流。真正的「弹幕有没有出现在直播间」只能人看，见交接单。
 *
 * 为什么这些值得测：
 * - 字数按 UTF-16 char 计还是按码点计，差一个 emoji 就是「明明没超却发不出去」；
 * - 返回码映射错一格，用户看到的就是「发送失败：10031」这种裸数字，而不是「等一会儿再试」；
 * - 请求体拼装错了（少 csrf、msg 没编码）在真机上表现为「永远是 -400」，很难复盘。
 */
class LiveDanmakuApiTest {

    // ---------------------------------------------------------------- 字数校验

    @Test
    fun emptyOrBlank_isEmpty() {
        assertEquals(LiveDanmakuApi.TextCheck.EMPTY, LiveDanmakuApi.checkText(null))
        assertEquals(LiveDanmakuApi.TextCheck.EMPTY, LiveDanmakuApi.checkText(""))
        assertEquals(LiveDanmakuApi.TextCheck.EMPTY, LiveDanmakuApi.checkText("   "))
        assertEquals(LiveDanmakuApi.TextCheck.EMPTY, LiveDanmakuApi.checkText("\n\t "))
    }

    @Test
    fun normalText_isOk() {
        assertEquals(LiveDanmakuApi.TextCheck.OK, LiveDanmakuApi.checkText("主播好厉害"))
        assertEquals(LiveDanmakuApi.TextCheck.OK, LiveDanmakuApi.checkText(" 前后有空格也算 ")) 
    }

    @Test
    fun exactlyTwentyChars_isOk() {
        val twenty = "一".repeat(20)
        assertEquals(20, twenty.length)
        assertEquals(LiveDanmakuApi.TextCheck.OK, LiveDanmakuApi.checkText(twenty))
    }

    @Test
    fun twentyOneChars_isTooLong() {
        assertEquals(LiveDanmakuApi.TextCheck.TOO_LONG, LiveDanmakuApi.checkText("一".repeat(21)))
    }

    @Test
    fun emoji_countsAsOneCharacter() {
        // 每个 emoji 占 2 个 UTF-16 char，但直播间按「20 个字」算，所以 20 个 emoji 应当放行
        val twentyEmoji = "😀".repeat(20)
        assertEquals("20 个 emoji 是 40 个 char，但只有 20 个字", 40, twentyEmoji.length)
        assertEquals(LiveDanmakuApi.TextCheck.OK, LiveDanmakuApi.checkText(twentyEmoji))
        assertEquals(LiveDanmakuApi.TextCheck.TOO_LONG, LiveDanmakuApi.checkText("😀".repeat(21)))
    }

    @Test
    fun trimBeforeCounting() {
        // 两侧空白不该把一条 20 字的弹幕顶成 22 字
        assertEquals(LiveDanmakuApi.TextCheck.OK, LiveDanmakuApi.checkText("  " + "一".repeat(20) + "  "))
    }

    // ---------------------------------------------------------------- 返回码映射

    @Test
    fun documentedCodes_mapToKinds() {
        assertEquals(LiveDanmakuApi.Kind.SUCCESS, LiveDanmakuApi.mapCode(0))
        assertEquals(LiveDanmakuApi.Kind.NOT_LOGGED_IN, LiveDanmakuApi.mapCode(-101))
        assertEquals(LiveDanmakuApi.Kind.CSRF_INVALID, LiveDanmakuApi.mapCode(-111))
        assertEquals(LiveDanmakuApi.Kind.RATE_LIMITED, LiveDanmakuApi.mapCode(10031))
        assertEquals(LiveDanmakuApi.Kind.TOO_LONG, LiveDanmakuApi.mapCode(1003212))
        // 文档写的是「请求错误」，没有单独的「参数缺失」类别，归到通用失败
        assertEquals(LiveDanmakuApi.Kind.ERROR, LiveDanmakuApi.mapCode(-400))
    }

    @Test
    fun riskControlCodes_mapToRiskControl() {
        assertEquals(LiveDanmakuApi.Kind.RISK_CONTROL, LiveDanmakuApi.mapCode(-352))
        assertEquals(LiveDanmakuApi.Kind.RISK_CONTROL, LiveDanmakuApi.mapCode(-412))
    }

    @Test
    fun unknownCode_isError() {
        assertEquals(LiveDanmakuApi.Kind.ERROR, LiveDanmakuApi.mapCode(1))
        assertEquals(LiveDanmakuApi.Kind.ERROR, LiveDanmakuApi.mapCode(-1))
        assertEquals(LiveDanmakuApi.Kind.ERROR, LiveDanmakuApi.mapCode(Int.MIN_VALUE))
    }

    // ---------------------------------------------------------------- 请求体

    @Test
    fun body_containsAllRequiredParams() {
        val body = LiveDanmakuApi.buildBody(12345L, "你好", "csrfvalue", 1730000000L)
        assertTrue("缺 roomid：$body", body.contains("roomid=12345"))
        assertTrue("缺 csrf：$body", body.contains("csrf=csrfvalue"))
        assertTrue("缺 csrf_token：$body", body.contains("csrf_token=csrfvalue"))
        assertTrue("缺 rnd：$body", body.contains("rnd=1730000000"))
        assertTrue("缺 fontsize：$body", body.contains("fontsize=25"))
        assertTrue("缺 color：$body", body.contains("color=16777215"))
        assertTrue("缺 mode：$body", body.contains("mode=1"))
        assertTrue("不该带 access_key：$body", !body.contains("access_key"))
    }

    @Test
    fun body_carriesWebLikeParamsLikePiliPlus() {
        // 对齐 PiliPlus 的 LiveHttp.sendLiveMsg：缺这些字段请求就不像 web 端，更容易被风控挡
        val body = LiveDanmakuApi.buildBody(1L, "hi", "c", 0L)
        assertTrue("缺 room_type：$body", body.contains("room_type=0"))
        assertTrue("缺 jumpfrom：$body", body.contains("jumpfrom=0"))
        assertTrue("缺 reply_mid：$body", body.contains("reply_mid=0"))
        assertTrue("缺 reply_attr：$body", body.contains("reply_attr=0"))
        assertTrue("缺 replay_dmid：$body", body.contains("replay_dmid=&"))
        assertTrue("缺 reply_type：$body", body.contains("reply_type=0"))
        assertTrue("缺 reply_uname：$body", body.contains("reply_uname="))
        // statistics 是 JSON，必须整串编码后再放进来，否则 & 与引号会把 body 拆坏
        assertTrue(
            "statistics 未编码：$body",
            body.contains("statistics=%7B%22appId%22%3A100%2C%22platform%22%3A5%7D")
        )
    }

    @Test
    fun body_hasNoWbiParams() {
        // w_rid / wts 属于 query 上的签名参数，混进 body 只会让服务端多认两个无用键
        val body = LiveDanmakuApi.buildBody(1L, "hi", "c", 0L)
        assertTrue("body 里不该出现 w_rid：$body", !body.contains("w_rid"))
        assertTrue("body 里不该出现 wts：$body", !body.contains("wts"))
        assertTrue("body 里不该出现 web_location：$body", !body.contains("web_location"))
    }

    @Test
    fun body_urlEncodesChineseMsg() {
        val body = LiveDanmakuApi.buildBody(1L, "你好", "c", 0L)
        // 「你好」的 UTF-8 百分号编码；未编码会被服务端当成非法参数
        assertTrue("msg 未编码：$body", body.contains("msg=%E4%BD%A0%E5%A5%BD"))
        assertTrue("不该出现裸中文：$body", !body.contains("你好"))
    }

    @Test
    fun body_hasFixedOrder() {
        // 固定顺序是为了可断言：HashMap 版（NetWorkUtil.FormData）每次顺序都可能不同
        val body = LiveDanmakuApi.buildBody(7L, "hi", "c", 9L, LiveDanmakuApi.DEFAULT_FONT_SIZE,
                LiveDanmakuApi.DEFAULT_COLOR, LiveDanmakuApi.DEFAULT_MODE)
        assertEquals(
            "bubble=0&msg=hi&color=16777215&mode=1&room_type=0&jumpfrom=0&reply_mid=0&reply_attr=0" +
                "&replay_dmid=&statistics=%7B%22appId%22%3A100%2C%22platform%22%3A5%7D" +
                "&reply_type=0&reply_uname=&fontsize=25&rnd=9&roomid=7&csrf=c&csrf_token=c",
            body
        )
    }

    @Test
    fun body_nullCsrfBecomesEmptyButStillPresent() {
        val body = LiveDanmakuApi.buildBody(1L, "hi", null, 0L)
        assertTrue("csrf 键必须存在（服务端按缺参数报 -400）：$body", body.contains("csrf=&"))
    }

    // ---------------------------------------------------------------- header

    @Test
    fun liveHeaders_replacesRefererOnly() {
        val base = ArrayList<String>()
        base.add("Cookie"); base.add("SESSDATA=abc")
        base.add("Referer"); base.add("https://www.bilibili.com/")
        base.add("User-Agent"); base.add("ua")

        val headers = LiveDanmakuApi.liveHeaders(base)
        assertEquals("长度应当不变", base.size, headers.size)
        assertEquals("Cookie", headers[0])
        assertEquals("SESSDATA=abc", headers[1])
        assertEquals("Referer", headers[2])
        assertEquals("直播页的 Referer 必须是 live.bilibili.com", LiveDanmakuApi.LIVE_REFERER, headers[3])
        assertEquals("ua", headers[5])
        // 不能改到入参本身（webHeaders 是全局共享的）
        assertEquals("https://www.bilibili.com/", base[3])
    }

    // ---------------------------------------------------------------- 节流

    @Test
    fun throttle_rejectsWithinInterval() {
        val throttle = LiveDanmakuApi.Throttle(3000L)
        assertTrue("第一次必须放行", throttle.tryAcquire(1000L))
        assertTrue("同一毫秒连点必须被拦", !throttle.tryAcquire(1000L))
        assertTrue("2.9 秒仍算太快", !throttle.tryAcquire(3999L))
        assertTrue("满 3 秒放行", throttle.tryAcquire(4000L))
        assertTrue("刚放行完又被拦", !throttle.tryAcquire(4001L))
    }

    @Test
    fun throttle_remainingMs() {
        val throttle = LiveDanmakuApi.Throttle(3000L)
        assertEquals("还没发过，不该有等待", 0L, throttle.remainingMs(500L))
        throttle.tryAcquire(1000L)
        assertEquals(2000L, throttle.remainingMs(2000L))
        assertEquals("已经过点，等待为 0", 0L, throttle.remainingMs(5000L))
    }

    @Test
    fun throttle_toleratesClockGoingBackwards() {
        val throttle = LiveDanmakuApi.Throttle(3000L)
        assertTrue(throttle.tryAcquire(10_000L))
        // 用户改系统时间 / NTP 校时把钟往回调：不能因此永久卡死
        assertTrue(throttle.tryAcquire(9_000L))
        assertEquals("回拨后以回调后的时刻作为新基准重新计时", 3000L, throttle.remainingMs(9_000L))
    }

    @Test
    fun throttle_resetAllowsImmediately() {
        val throttle = LiveDanmakuApi.Throttle(3000L)
        assertTrue(throttle.tryAcquire(1000L))
        throttle.reset()
        assertTrue("reset 之后视同从未发送", throttle.tryAcquire(1001L))
    }
}
