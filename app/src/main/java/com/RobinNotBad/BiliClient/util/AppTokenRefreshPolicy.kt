package com.RobinNotBad.BiliClient.util

/**
 * APP（TV 登录）access_token 的**刷新频率**策略（26.11.22「老是掉登录」返工，Step 3）。
 *
 * <h3>为什么要限制频率</h3>
 * /api/v2/oauth2/refresh_token 的 refresh_token 是**一次性轮换**口令：每刷一次，服务端就作废
 * 一个旧口令、发一个新手口令。旧实现「每次冷启动刷一次 + 每 30 分钟刷一次」，一天要轮换几十次——
 * 每一次轮换都是一个「本地还没来得及落盘就掉电 / 被杀进程」的丢凭据窗口，也是「两台设备（或一份
 * 备份）拿着同一个口令互相踢」的概率放大器。
 *
 * <p>兄弟分支（BiliTerminal-Revival / PianoEthan / cyq114514）**根本不刷 APP token**，只在服务端
 * 说「该刷了」时才走 web Cookie 刷新（半年一两次）。本策略把这个思路搬到 APP 链路：平时不刷，
 * 只有超过 [MIN_INTERVAL_MS] 才例行续期一次；真正「access_token 过期」的现场（请求层收到 -101）
 * 由调用方用 force = true 立即刷新，不受本间隔约束。
 *
 * <p>**纯逻辑**：不读 SharedPreferences、不碰 Android API，时间由调用方注入。
 */
object AppTokenRefreshPolicy {

    /**
     * 例行刷新间隔：7 天。
     *
     * <p>依据：TV 登录的 access_token 有效期约 30 天、refresh_token 约 180 天。7 天一次既保证
     * access_token 永远不会自然过期（即使 App 三个月不冷启动也只轮换十几次），又把「一次性口令
     * 被轮换」的次数压到旧实现的约 1/7（旧实现是每次冷启动 + 每天 48 次）。
     */
    const val MIN_INTERVAL_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** 「从来没刷过」的哨兵值。 */
    const val NO_REFRESH: Long = 0L

    /**
     * 现在该不该例行刷新一次 APP token。
     *
     * @param nowWallMs         当前墙上时钟毫秒（System.currentTimeMillis()）
     * @param lastRefreshWallMs 上一次刷新成功的时刻；[NO_REFRESH] 表示没记录（立即允许）
     * @return true 表示到点了
     */
    @JvmStatic
    fun isDue(nowWallMs: Long, lastRefreshWallMs: Long): Boolean {
        if (lastRefreshWallMs <= NO_REFRESH) return true
        // 系统时间被往回调（手表对时、用户手动改）时差值会是负数：这种时候宁可刷一次、
        // 用新时间重新起算，也不要被一个「未来的时间戳」冻住一整年
        if (nowWallMs < lastRefreshWallMs) return true
        return nowWallMs - lastRefreshWallMs >= MIN_INTERVAL_MS
    }
}
