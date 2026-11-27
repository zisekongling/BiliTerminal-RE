package com.RobinNotBad.BiliClient.util

/**
 * APP（TV 登录）access_token 的**刷新时机**策略（26.11.22「老是掉登录」返工，Step 3；26.11.26 改为按到期时间）。
 *
 * <h3>为什么不每轮都刷</h3>
 * 刷新成功后服务端会下发一整套新凭据（access_token / refresh_token / cookies），客户端必须把它们
 * 整份落盘。刷得越勤，「写了一半被杀进程」与「两台设备拿着同一份凭据互相顶」的窗口就越多。
 * 旧实现「每次冷启动刷一次 + 每 30 分钟刷一次」，一天要轮换几十次；而兄弟分支
 * （BiliTerminal-Revival / PianoEthan / cyq114514）根本不刷 APP token，只在服务端说「该刷了」
 * 时才走 web Cookie 刷新（半年一两次）。
 *
 * <h3>现在怎么判断「该刷了」</h3>
 * 优先用**服务端给的到期时间**（刷新响应里的有效期字段，换算成墙上时钟存下来，见
 * [SharedPreferencesUtil.app_token_expires_at]）：只在「离到期不足 [RENEW_AHEAD_MS]」时续一次，
 * 于是 180 天有效期的 token 大约每 150 天才刷一次，既不会自然过期，又几乎不产生轮换窗口。
 * 拿不到到期时间（老版本升级上来、响应没带该字段）时，退回固定 [MIN_INTERVAL_MS] 兜底；
 * 两次例行刷新之间另有 [MIN_GAP_MS] 防抖，避免「服务端给了很短的有效期」时每次冷启动都刷一遍。
 *
 * <p>真正「access_token 已经过期」的现场（请求层收到失效码）由调用方用 force = true 立即刷新，
 * 不受本策略约束。
 *
 * <p>**纯逻辑**：不读 SharedPreferences、不碰 Android API，时间由调用方注入。
 */
object AppTokenRefreshPolicy {

    /**
     * 兜底的例行刷新间隔：7 天。
     *
     * <p>只在拿不到到期时间时使用。有到期时间时，真正的依据是 [RENEW_AHEAD_MS]。
     */
    const val MIN_INTERVAL_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** 「从来没刷过 / 不知道到期时间」的哨兵值。 */
    const val NO_REFRESH: Long = 0L

    /**
     * 到期前多久开始续期：30 天。
     *
     * <p>取值考虑：access_token 的有效期按服务端下发的为准（常见 180 天）。提前 30 天续期，
     * 既有整月的余量吸收「手表长期没网 / 进程被回收」的偏差，又让例行刷新频率降到半年一次量级。
     */
    const val RENEW_AHEAD_MS: Long = 30L * 24 * 60 * 60 * 1000

    /** 两次例行刷新之间的最小间隔：1 天。防止短有效期凭据在每次冷启动时都被重刷。 */
    const val MIN_GAP_MS: Long = 24L * 60 * 60 * 1000

    /**
     * 现在该不该例行刷新一次 APP token（不知道到期时间时的老口径：固定间隔）。
     *
     * @param nowWallMs         当前墙上时钟毫秒（System.currentTimeMillis()）
     * @param lastRefreshWallMs 上一次刷新成功的时刻；[NO_REFRESH] 表示没记录（立即允许）
     * @return true 表示到点了
     */
    @JvmStatic
    fun isDue(nowWallMs: Long, lastRefreshWallMs: Long): Boolean =
        isDue(nowWallMs, lastRefreshWallMs, NO_REFRESH)

    /**
     * 现在该不该例行刷新一次 APP token。
     *
     * @param nowWallMs         当前墙上时钟毫秒（System.currentTimeMillis()）
     * @param lastRefreshWallMs 上一次刷新成功的时刻；[NO_REFRESH] 表示没记录
     * @param expiresAtWallMs   当前 access_token 的到期时刻；[NO_REFRESH] 表示不知道（退回固定间隔）
     * @return true 表示到点了
     */
    @JvmStatic
    fun isDue(nowWallMs: Long, lastRefreshWallMs: Long, expiresAtWallMs: Long): Boolean {
        // 系统时间被往回调（手表对时、用户手动改）时差值会是负数：这种时候宁可刷一次、
        // 用新时间重新起算，也不要被一个「未来的时间戳」冻住一整年
        if (lastRefreshWallMs > NO_REFRESH && nowWallMs < lastRefreshWallMs) return true

        if (expiresAtWallMs > NO_REFRESH) {
            // 还没进入续期窗口：再久也先不动（180 天有效期的 token 因此约 150 天才刷一次）
            if (nowWallMs < expiresAtWallMs - RENEW_AHEAD_MS) return false
            // 已进入窗口或已经过期：仍要过 MIN_GAP_MS 防抖，否则每次冷启动都会再刷一遍
            return lastRefreshWallMs <= NO_REFRESH || nowWallMs - lastRefreshWallMs >= MIN_GAP_MS
        }

        if (lastRefreshWallMs <= NO_REFRESH) return true
        return nowWallMs - lastRefreshWallMs >= MIN_INTERVAL_MS
    }
}
