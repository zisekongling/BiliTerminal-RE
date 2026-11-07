package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 周期性 Cookie 刷新节流策略的 JVM 单测（反馈 #60）。
 *
 * 覆盖三件事：正常间隔、失败指数退避（含封顶）、-101 提示的提前重试边界。
 * 这些都写在纯逻辑里，正是为了让「会不会刷屏 / 会不会一直不刷」不靠真机看日志验证。
 */
class CookieRefreshPolicyTest {

    private val min = 60_000L

    // ---------------- minGapMs：正常间隔 + 指数退避 ----------------

    @Test
    fun minGapMs_没有失败时是正常间隔() {
        assertEquals(CookieRefreshPolicy.DEFAULT_INTERVAL_MS, CookieRefreshPolicy.minGapMs(0))
        // 负数（异常输入）按健康处理，不能算出 0 导致立刻重试
        assertEquals(CookieRefreshPolicy.DEFAULT_INTERVAL_MS, CookieRefreshPolicy.minGapMs(-1))
    }

    @Test
    fun minGapMs_失败一次后按首次退避() {
        assertEquals(CookieRefreshPolicy.FIRST_RETRY_MS, CookieRefreshPolicy.minGapMs(1))
        // 换一个口径：失败一次后的重试间隔必须**短于**健康间隔——失败后应当更早重试而不是更晚。
        // （不再重复断言 minGapMs(1) 的具体数值，那条由下面的「每次失败翻倍」钉住。）
        assertTrue(
            "首次退避不能长于健康间隔，否则偶发失败一次就会让刷新比正常时更稀疏",
            CookieRefreshPolicy.minGapMs(1) < CookieRefreshPolicy.minGapMs(0)
        )
    }

    @Test
    fun minGapMs_每次失败翻倍() {
        assertEquals(5 * min, CookieRefreshPolicy.minGapMs(1))
        assertEquals(10 * min, CookieRefreshPolicy.minGapMs(2))
        assertEquals(20 * min, CookieRefreshPolicy.minGapMs(3))
        assertEquals(40 * min, CookieRefreshPolicy.minGapMs(4))
    }

    @Test
    fun minGapMs_退避封顶() {
        assertEquals(
            "退避不能无限涨：封顶 4 小时，避免整整一天都不再刷新",
            CookieRefreshPolicy.MAX_BACKOFF_MS,
            CookieRefreshPolicy.minGapMs(7)
        )
    }

    @Test
    fun minGapMs_失败次数极端大也不会溢出成负数() {
        val gap = CookieRefreshPolicy.minGapMs(Int.MAX_VALUE)
        assertEquals(CookieRefreshPolicy.MAX_BACKOFF_MS, gap)
        assertTrue("溢出成负数会让 shouldAttempt 永远返回 true", gap > 0)
    }

    // ---------------- shouldAttempt：没试过 / 间隔不足 / 到点 ----------------

    @Test
    fun shouldAttempt_从未尝试过时立即允许() {
        assertTrue(
            CookieRefreshPolicy.shouldAttempt(
                now = 1_000_000L,
                lastAttemptMs = CookieRefreshPolicy.NO_ATTEMPT,
                consecutiveFailures = 0,
                loginExpiredAtMs = CookieRefreshPolicy.NO_ATTEMPT
            )
        )
    }

    @Test
    fun shouldAttempt_健康时不到30分钟不重复请求() {
        val last = 10_000_000L
        assertFalse(
            CookieRefreshPolicy.shouldAttempt(
                now = last + 1 * min,
                lastAttemptMs = last,
                consecutiveFailures = 0,
                loginExpiredAtMs = CookieRefreshPolicy.NO_ATTEMPT
            )
        )
        // 边界：正好到 30 分钟就该刷
        assertTrue(
            CookieRefreshPolicy.shouldAttempt(
                now = last + CookieRefreshPolicy.DEFAULT_INTERVAL_MS,
                lastAttemptMs = last,
                consecutiveFailures = 0,
                loginExpiredAtMs = CookieRefreshPolicy.NO_ATTEMPT
            )
        )
    }

    @Test
    fun shouldAttempt_失败后按退避间隔判断() {
        val last = 10_000_000L
        // 失败 1 次时，3 分钟还不够，5 分钟才允许
        assertFalse(
            CookieRefreshPolicy.shouldAttempt(last + 3 * min, last, 1, CookieRefreshPolicy.NO_ATTEMPT)
        )
        assertTrue(
            CookieRefreshPolicy.shouldAttempt(last + 5 * min, last, 1, CookieRefreshPolicy.NO_ATTEMPT)
        )
        // 失败 2 次时 5 分钟又不够了（退避翻倍到 10 分钟）
        assertFalse(
            CookieRefreshPolicy.shouldAttempt(last + 5 * min, last, 2, CookieRefreshPolicy.NO_ATTEMPT)
        )
        assertTrue(
            CookieRefreshPolicy.shouldAttempt(last + 10 * min, last, 2, CookieRefreshPolicy.NO_ATTEMPT)
        )
    }

    @Test
    fun shouldAttempt_时钟回拨时不被冻住() {
        val last = 10_000_000L
        assertTrue(
            "elapsed 为负（时钟/计时器异常）时允许重试，否则会被永久跳过",
            CookieRefreshPolicy.shouldAttempt(last - 5 * min, last, 0, CookieRefreshPolicy.NO_ATTEMPT)
        )
    }

    // ---------------- shouldAttempt：-101 提示的提前重试 ----------------

    @Test
    fun shouldAttempt_收到101且无失败时可提前重试() {
        val last = 10_000_000L
        // 上次尝试之后才收到 -101，间隔已超过提示下限（2 分钟）→ 提前刷新
        assertTrue(
            CookieRefreshPolicy.shouldAttempt(
                now = last + 3 * min,
                lastAttemptMs = last,
                consecutiveFailures = 0,
                loginExpiredAtMs = last + 30_000L
            )
        )
    }

    @Test
    fun shouldAttempt_101提示也要等最小间隔() {
        val last = 10_000_000L
        // 距离上次尝试只有 1 分钟：连提示下限（2 分钟）都没到，不能立刻打请求
        assertFalse(
            CookieRefreshPolicy.shouldAttempt(
                now = last + 1 * min,
                lastAttemptMs = last,
                consecutiveFailures = 0,
                loginExpiredAtMs = last + 10_000L
            )
        )
    }

    @Test
    fun shouldAttempt_有连续失败时不因101提前重试() {
        val last = 10_000_000L
        // 网络一直失败时，-101 不该把退避打穿，否则无网环境下会变成每 2 分钟一次
        assertFalse(
            CookieRefreshPolicy.shouldAttempt(
                now = last + 3 * min,
                lastAttemptMs = last,
                consecutiveFailures = 1,
                loginExpiredAtMs = last + 30_000L
            )
        )
    }

    @Test
    fun shouldAttempt_101标记早于上次尝试时不生效() {
        val last = 10_000_000L
        // 标记是上一次尝试之前收到的（那次尝试之后成功过），说明已经处理过了
        assertFalse(
            CookieRefreshPolicy.shouldAttempt(
                now = last + 3 * min,
                lastAttemptMs = last,
                consecutiveFailures = 0,
                loginExpiredAtMs = last - 1
            )
        )
    }

    // ---------------- checkRefreshedCredentials：确认刷新前的新凭据校验（26.11.06.1） ----------------
    // 刷新接口下发新 Cookie、确认接口作废旧 Cookie。确认之前必须确认新凭据真的落到了本地，
    // 否则「旧会话被作废 + 本地还是旧的」= 用户彻底掉登录。

    @Test
    fun checkRefreshedCredentials_齐全时为READY() {
        assertEquals(
            CookieRefreshPolicy.RefreshCheck.READY,
            CookieRefreshPolicy.checkRefreshedCredentials(
                refreshTokenNew = "new_token",
                sessData = "sess_value",
                cookieMid = 123L,
                expectedMid = 123L
            )
        )
    }

    @Test
    fun checkRefreshedCredentials_缺新refresh_token时拒绝() {
        for (token in listOf(null, "", "   ")) {
            assertEquals(
                "没有新口令就不能继续（一次性轮换，丢了就再也续不上）",
                CookieRefreshPolicy.RefreshCheck.NO_REFRESH_TOKEN,
                CookieRefreshPolicy.checkRefreshedCredentials(token, "sess", 123L, 123L)
            )
        }
    }

    @Test
    fun checkRefreshedCredentials_新Cookie里没有SESSDATA时拒绝() {
        assertEquals(
            "新会话没落盘就绝不能 confirm，否则旧 Cookie 被作废本地又没有新的",
            CookieRefreshPolicy.RefreshCheck.NO_SESSION_COOKIE,
            CookieRefreshPolicy.checkRefreshedCredentials("new_token", "", 123L, 123L)
        )
        assertEquals(
            CookieRefreshPolicy.RefreshCheck.NO_SESSION_COOKIE,
            CookieRefreshPolicy.checkRefreshedCredentials("new_token", null, 123L, 123L)
        )
    }

    @Test
    fun checkRefreshedCredentials_mid对不上时拒绝() {
        assertEquals(
            "新 Cookie 属于另一个 mid：不能拿它覆盖当前账号（串号）",
            CookieRefreshPolicy.RefreshCheck.MID_MISMATCH,
            CookieRefreshPolicy.checkRefreshedCredentials("new_token", "sess", 456L, 123L)
        )
        assertEquals(
            CookieRefreshPolicy.RefreshCheck.MID_MISMATCH,
            CookieRefreshPolicy.checkRefreshedCredentials("new_token", "sess", 0L, 123L)
        )
    }

    @Test
    fun checkRefreshedCredentials_本地没有有效mid时拒绝而不是覆盖() {
        assertEquals(
            "本地 mid 本来就是残缺的（<=0）时不能拿 Cookie 里的值去覆盖登录态",
            CookieRefreshPolicy.RefreshCheck.MID_MISMATCH,
            CookieRefreshPolicy.checkRefreshedCredentials("new_token", "sess", 123L, 0L)
        )
    }
}
