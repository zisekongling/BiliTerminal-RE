package com.RobinNotBad.BiliClient.util

/**
 * 周期性 Cookie 刷新「该不该刷」的判定（反馈 #60 老是掉登录）。
 *
 * <p><b>为什么需要</b>：手表常年不冷启动，`SplashActivity.checkCookieRefresh()` 只在启动时跑一次，
 * 进程一直活着就再也不会续期 SESSDATA。本策略负责决定「现在要不要发起一次刷新尝试」，
 * 真正发请求的是 [CookieRefreshScheduler]。
 *
 * <p><b>纯逻辑</b>：不读 SharedPreferences、不碰任何 Android API，时间由调用方注入
 * （进程内传 `SystemClock.elapsedRealtime()`，单测传任意值），因此 JVM 单测可直接调用。
 *
 * <p><b>两档节流</b>：
 * <ul>
 *   <li>正常间隔 [DEFAULT_INTERVAL_MS]：登录态健康时每 30 分钟问一次服务端「需不需要刷新」；</li>
 *   <li>失败退避：连续失败时按 [FIRST_RETRY_MS] 起指数翻倍，封顶 [MAX_BACKOFF_MS]。
 *       没网时刷屏既费电又白跑流量，退避到最多 4 小时一次足够覆盖「手表放着没动」的场景。</li>
 * </ul>
 *
 * <p>另外支持一条「轻提示」：请求层统一入口（[NetWorkUtil]）识别到 `code=-101`（账号未登录/登录态过期）时
 * 只做标记，本策略允许在最近一次尝试 [HINT_MIN_GAP_MS] 之后提前重试一次——**只在没有连续失败时生效**，
 * 免得网络本身不通时被 -101 带成不停重试。
 */
object CookieRefreshPolicy {

    /** 正常间隔：登录态健康时每 30 分钟检查一次是否需要刷新。 */
    const val DEFAULT_INTERVAL_MS: Long = 30L * 60 * 1000

    /** 首次失败后的等待时间（之后每失败一次翻倍）。 */
    const val FIRST_RETRY_MS: Long = 5L * 60 * 1000

    /** 退避上限：无网 / 服务端一直失败时，最多 4 小时再试一次。 */
    const val MAX_BACKOFF_MS: Long = 4L * 60 * 60 * 1000

    /** 收到 -101 标记后，最早可以提前重试的间隔。 */
    const val HINT_MIN_GAP_MS: Long = 2L * 60 * 1000

    /**
     * 连续失败 [failures] 次之后，距离上一次尝试至少要隔多久。
     *
     * 0 次（健康）→ [DEFAULT_INTERVAL_MS]；1 次 → [FIRST_RETRY_MS]；之后每次翻倍，
     * 封顶 [MAX_BACKOFF_MS]。
     */
    @JvmStatic
    fun minGapMs(failures: Int): Long {
        if (failures <= 0) return DEFAULT_INTERVAL_MS
        var wait = FIRST_RETRY_MS
        var level = 1
        // 用循环而不是 shl：failures 可能被外部写成很大（如 1000），移位会溢出成负数
        while (level < failures) {
            if (wait >= MAX_BACKOFF_MS) return MAX_BACKOFF_MS
            wait *= 2
            level++
        }
        return if (wait > MAX_BACKOFF_MS) MAX_BACKOFF_MS else wait
    }

    /**
     * 现在该不该发起一次刷新尝试。
     *
     * @param now                当前单调时钟毫秒（进程内传 `SystemClock.elapsedRealtime()`）
     * @param lastAttemptMs      上一次尝试的时刻；[NO_ATTEMPT] 表示从没试过（立即允许）
     * @param consecutiveFailures 当前连续失败次数；成功一次后由调用方清零
     * @param loginExpiredAtMs   最近一次收到 `code=-101` 的时刻；[NO_ATTEMPT] 表示没收到
     * @return true 表示现在可以发起尝试
     */
    @JvmStatic
    fun shouldAttempt(
        now: Long,
        lastAttemptMs: Long,
        consecutiveFailures: Int,
        loginExpiredAtMs: Long
    ): Boolean {
        if (lastAttemptMs <= NO_ATTEMPT) return true
        val elapsed = now - lastAttemptMs
        // 时钟回拨 / 计时器异常（elapsedRealtime 正常不会倒退，这里是防御）：不要因此被冻住
        if (elapsed < 0L) return true
        if (elapsed >= minGapMs(consecutiveFailures)) return true
        // 只有「最近一次尝试之后才收到 -101」且当前没有连续失败时，才允许提前重试一次
        return consecutiveFailures <= 0 &&
            loginExpiredAtMs > lastAttemptMs &&
            elapsed >= HINT_MIN_GAP_MS
    }

    /** 「没有记录」的哨兵值（`elapsedRealtime()` 从开机起算，恒 >= 0，0 不会与真实时刻冲突）。 */
    const val NO_ATTEMPT: Long = 0L
}
