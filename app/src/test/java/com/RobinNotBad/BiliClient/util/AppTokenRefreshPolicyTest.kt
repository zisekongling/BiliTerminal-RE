package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AppTokenRefreshPolicy] 的纯函数单测（不碰 Android：时间全部注入）。
 *
 * 钉四件事：
 * 1. **知道到期时间时按到期时间续**：离到期还早就不刷，进入 `(到期 - 30 天)` 才刷；
 * 2. **不知道到期时间时退回老的固定间隔**：7 天一次，行为与 26.11.22 那版一致；
 * 3. **防抖**：刚刷过（不足 1 天）一律不再刷，哪怕服务端给的有效期很短；
 * 4. **时钟回拨**：上次刷新时间在未来时宁可刷一次，也不要被冻住。
 */
class AppTokenRefreshPolicyTest {

    private val day = 24L * 60 * 60 * 1000

    /** 一个固定的「现在」，避免用真实时间做基准。 */
    private val now = 1_800_000_000_000L

    // ---------- 不知道到期时间（老口径） ----------

    @Test
    fun noExpiry_noRecord_isDue() {
        assertTrue(AppTokenRefreshPolicy.isDue(now, AppTokenRefreshPolicy.NO_REFRESH))
    }

    @Test
    fun noExpiry_justRefreshed_notDue() {
        assertFalse(AppTokenRefreshPolicy.isDue(now, now - 1 * day))
    }

    @Test
    fun noExpiry_afterInterval_isDue() {
        assertTrue(AppTokenRefreshPolicy.isDue(now, now - AppTokenRefreshPolicy.MIN_INTERVAL_MS))
    }

    // ---------- 知道到期时间 ----------

    @Test
    fun farFromExpiry_notDueEvenIfLongSinceRefresh() {
        // 还有 100 天到期、上次刷新是 30 天前：旧口径（7 天）会刷，新口径不该刷
        assertFalse(
            AppTokenRefreshPolicy.isDue(now, now - 30 * day, now + 100 * day)
        )
    }

    @Test
    fun insideRenewWindow_isDue() {
        // 还有 20 天到期（已进入 30 天窗口），上次刷新是 2 天前
        assertTrue(
            AppTokenRefreshPolicy.isDue(now, now - 2 * day, now + 20 * day)
        )
    }

    @Test
    fun insideRenewWindow_butJustRefreshed_notDue() {
        // 服务端给的有效期很短时，窗口一直开着；这时靠 MIN_GAP_MS 防抖，避免每次冷启动都刷
        assertFalse(
            AppTokenRefreshPolicy.isDue(now, now - 60_000L, now + 2 * day)
        )
    }

    @Test
    fun alreadyExpired_isDueWhenNoRecord() {
        assertTrue(
            AppTokenRefreshPolicy.isDue(now, AppTokenRefreshPolicy.NO_REFRESH, now - 1 * day)
        )
    }

    @Test
    fun alreadyExpired_butJustRefreshed_notDue() {
        // 刚刷过又过期（服务端给了 0 秒有效期之类的怪响应）：至少隔 1 天才再试，避免死循环刷
        assertFalse(
            AppTokenRefreshPolicy.isDue(now, now - 60_000L, now - 1 * day)
        )
    }

    // ---------- 时钟回拨 ----------

    @Test
    fun clockRollback_isDue() {
        assertTrue(
            AppTokenRefreshPolicy.isDue(now, now + 10 * day, now + 100 * day)
        )
    }

    // ---------- RefreshGuard.isRefreshEndpoint（与刷新策略同一批，26.11.22 加的，别一起删） ----------

    @Test
    fun isRefreshEndpoint_只认刷新与确认两个接口() {
        assertTrue(RefreshGuard.isRefreshEndpoint("/x/passport-login/web/cookie/refresh"))
        assertTrue(RefreshGuard.isRefreshEndpoint("/x/passport-login/web/confirm/refresh"))
        assertFalse("cookie/info 只是问「要不要刷」，它的 Set-Cookie 走通用落盘", RefreshGuard.isRefreshEndpoint("/x/passport-login/web/cookie/info"))
        assertFalse(RefreshGuard.isRefreshEndpoint("/x/web-interface/nav"))
        assertFalse(RefreshGuard.isRefreshEndpoint(""))
        assertFalse(RefreshGuard.isRefreshEndpoint(null))
    }

    @Test
    fun isDue_间隔常量是7天() {
        assertTrue(
            "兜底间隔决定「凭据被换掉」的频率上限，改它要连带改文档与这里的断言",
            AppTokenRefreshPolicy.MIN_INTERVAL_MS == 7 * day
        )
    }
}
