package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AppTokenRefreshPolicy] 与 [RefreshGuard.isRefreshEndpoint] 的纯逻辑单测（26.11.22 掉登录返工）。
 *
 * <p>这两个函数决定了「一次性 refresh_token 一天被轮换几次」和「哪些响应不许走通用 Cookie 落盘」，
 * 都是「猜错就掉登录」的地方，所以单独锁行为。
 */
class AppTokenRefreshPolicyTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun isDue_从没刷过时立即到点() {
        assertTrue(AppTokenRefreshPolicy.isDue(1_700_000_000_000L, AppTokenRefreshPolicy.NO_REFRESH))
    }

    @Test
    fun isDue_不足7天不刷() {
        val now = 1_700_000_000_000L
        val last = now - 6 * day
        assertFalse("旧实现每次冷启动都刷，一天几十次轮换；现在 6 天之内不碰口令", AppTokenRefreshPolicy.isDue(now, last))
        assertFalse(AppTokenRefreshPolicy.isDue(now, now))
        assertFalse(AppTokenRefreshPolicy.isDue(now, now - 1))
    }

    @Test
    fun isDue_满7天就到点() {
        val now = 1_700_000_000_000L
        assertTrue(AppTokenRefreshPolicy.isDue(now, now - 7 * day))
        assertTrue(AppTokenRefreshPolicy.isDue(now, now - 7 * day - 1))
    }

    @Test
    fun isDue_系统时间回拨时宁可刷一次也不要被冻住() {
        val now = 1_700_000_000_000L
        assertTrue(
            "记录的时刻在「未来」说明时钟被改过，冻结一整年比多刷一次糟得多",
            AppTokenRefreshPolicy.isDue(now, now + 30 * day)
        )
    }

    @Test
    fun isDue_间隔常量是7天() {
        assertTrue(
            "间隔决定「一次性口令」被轮换的频率，改它要连带改文档与这里的断言",
            AppTokenRefreshPolicy.MIN_INTERVAL_MS == 7 * day
        )
    }

    @Test
    fun isRefreshEndpoint_只认刷新与确认两个接口() {
        assertTrue(RefreshGuard.isRefreshEndpoint("/x/passport-login/web/cookie/refresh"))
        assertTrue(RefreshGuard.isRefreshEndpoint("/x/passport-login/web/confirm/refresh"))
        assertFalse("cookie/info 只是问「要不要刷」，它的 Set-Cookie 走通用落盘", RefreshGuard.isRefreshEndpoint("/x/passport-login/web/cookie/info"))
        assertFalse(RefreshGuard.isRefreshEndpoint("/x/web-interface/nav"))
        assertFalse(RefreshGuard.isRefreshEndpoint(""))
        assertFalse(RefreshGuard.isRefreshEndpoint(null))
    }
}
