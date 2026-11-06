package com.RobinNotBad.BiliClient.util

import android.os.SystemClock
import com.RobinNotBad.BiliClient.api.AppTokenRefreshApi
import com.RobinNotBad.BiliClient.api.CookieRefreshApi
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * 进程级周期性 Cookie / access_token 刷新（反馈 #60 老是掉登录）。
 *
 * <p><b>问题</b>：原先只有 [com.RobinNotBad.BiliClient.activity.SplashActivity] 在启动时刷一次；
 * 手表常年不冷启动（进程一直活着），SESSDATA 到期后没有任何东西去续期，表现就是「用着用着突然掉登录」。
 *
 * <p><b>做法</b>：在 [com.RobinNotBad.BiliClient.BiliTerminal] 初始化后起一条后台调度线程
 * （每 [TICK_MS] 扫一次），是否真正发请求由纯策略 [CookieRefreshPolicy] 决定：
 * 正常 30 分钟一次，失败按指数退避封顶 4 小时；未登录 / 没有 refresh_token 直接跳过。
 * <b>不用 WorkManager / AlarmManager</b>——手表上进程被回收后本来就得靠下次冷启动的启动页兜底，
 * 这里要解决的只是「进程活着但一直不重启」这一段。
 *
 * <p><b>线程</b>：调度线程只做「到点没有」的轻量判定，真正的刷新请求走项目统一的
 * [CenterThreadPool]；[start] 只创建调度器、不做任何网络请求，不阻塞主线程。
 * 刷新动作本身是同步阻塞的（项目 API 层全部如此），所以也不能放到主线程。
 *
 * <p><b>和启动页的关系</b>：第一次尝试刻意排在一个完整间隔（30 分钟）之后，启动页那一次刷新
 * 已经覆盖了进程刚起来的时段，不必重复请求；两处不会在同一时刻发请求。
 */
object CookieRefreshScheduler {

    private const val TAG = "CookieRefresh"

    /** 扫描间隔：只看「到点没有」，真正的尝试间隔由 [CookieRefreshPolicy] 决定。 */
    private const val TICK_MS: Long = 5L * 60 * 1000

    @Volatile
    private var started = false

    /** 上一次尝试的时刻（`SystemClock.elapsedRealtime()`，进程内单调、不受系统时间影响）。 */
    @Volatile
    private var lastAttemptMs = CookieRefreshPolicy.NO_ATTEMPT

    /** 连续失败次数：成功一次清零，失败一次 +1，用来算退避。 */
    @Volatile
    private var consecutiveFailures = 0

    /** 最近一次收到 `code=-101` 的时刻（由 [NetWorkUtil] 的统一响应入口标记）。 */
    @Volatile
    private var loginExpiredAtMs = CookieRefreshPolicy.NO_ATTEMPT

    /**
     * 启动进程级调度。可重复调用，只有第一次生效。
     *
     * <p>必须等 `SharedPreferencesUtil.sharedPreferences` 就绪（[com.RobinNotBad.BiliClient.BiliTerminal.onCreate]
     * 里已经设好）之后再调，否则 tick 里的登录态判断会读到空配置。
     */
    @JvmStatic
    fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            try {
                val executor = Executors.newSingleThreadScheduledExecutor(ThreadFactory { runnable ->
                    Thread(runnable, "cookie-refresh").apply {
                        isDaemon = true
                        priority = Thread.MIN_PRIORITY
                    }
                })
                // 首次延迟一个完整间隔：启动页已经刷过一次，别在进程刚起来时再发一遍
                executor.scheduleWithFixedDelay(
                    { tick() },
                    CookieRefreshPolicy.DEFAULT_INTERVAL_MS,
                    TICK_MS,
                    TimeUnit.MILLISECONDS
                )
                Logu.i(TAG, "周期刷新已启动：每 ${TICK_MS / 60_000} 分钟检查一次，正常间隔 "
                        + "${CookieRefreshPolicy.DEFAULT_INTERVAL_MS / 60_000} 分钟")
            } catch (e: Exception) {
                // 起线程失败（极低概率）只记日志：绝不能把异常抛回 Application.onCreate
                Logu.e(TAG, "周期刷新启动失败：" + e.message)
            }
        }
    }

    /**
     * 统一响应入口（[NetWorkUtil]）识别到 `code=-101` 时调用。
     *
     * <p>**只做标记**，不在这里发请求：这里正处在调用方的请求线程上，嵌套刷新会拖慢当前调用，
     * 也会让同一个一次性 refresh_token 被并发轮换。真正刷新由调度线程按节流策略执行。
     */
    @JvmStatic
    fun onLoginExpired() {
        loginExpiredAtMs = SystemClock.elapsedRealtime()
    }

    /**
     * 账号全局登录态被替换（登录完成 / 切号 / 退出登录）时调用：把退避状态清零。
     *
     * <p>退避是按「上一个账号的失败次数」算的。不清的话，切到一个 Cookie 刚好过期的账号，
     * 会先等上最长 4 小时的退避才去刷新，表现就是「切完号一直提示登录失效，要等很久才恢复」。
     */
    @JvmStatic
    fun onAccountChanged() {
        lastAttemptMs = CookieRefreshPolicy.NO_ATTEMPT
        consecutiveFailures = 0
        loginExpiredAtMs = CookieRefreshPolicy.NO_ATTEMPT
    }

    private fun tick() {
        try {
            if (!isLoggedIn()) {
                // 未登录 / 没有 refresh_token：直接跳过，并清空节流状态，
                // 这样用户重新登录后不用等退避走完就能检查一次
                lastAttemptMs = CookieRefreshPolicy.NO_ATTEMPT
                consecutiveFailures = 0
                loginExpiredAtMs = CookieRefreshPolicy.NO_ATTEMPT
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (!CookieRefreshPolicy.shouldAttempt(now, lastAttemptMs, consecutiveFailures, loginExpiredAtMs)) {
                return
            }
            // 先记「已尝试」再发请求：刷新很慢（两三个串行请求），这期间绝不能有第二次进来
            lastAttemptMs = now
            // 真正的请求交给项目统一的后台线程池（网络请求一律走 CenterThreadPool），
            // 但异常必须在本任务内部兜住：直接抛出去会被 CenterThreadPool 转给 MsgUtil.err，
            // 那是给用户看的弹窗——后台每 30 分钟弹一次「网络错误」纯属骚扰。
            CenterThreadPool.run {
                val ok = try {
                    refreshNow()
                } catch (t: Throwable) {
                    Logu.w(TAG, "周期刷新异常：" + (t.message ?: t.javaClass.simpleName))
                    false
                }
                if (ok) {
                    consecutiveFailures = 0
                    loginExpiredAtMs = CookieRefreshPolicy.NO_ATTEMPT
                } else {
                    consecutiveFailures++
                    Logu.w(TAG, "周期刷新未成功，连续失败 $consecutiveFailures 次，"
                            + "${CookieRefreshPolicy.minGapMs(consecutiveFailures) / 60_000} 分钟后再试")
                }
            }
        } catch (t: Throwable) {
            // scheduleWithFixedDelay 里抛出异常会**永久终止**后续调度，所以整个 tick 必须兜住
            Logu.e(TAG, "周期刷新调度异常：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    /**
     * 本地是否还具备「值得去刷新」的登录态。
     *
     * <p>两道判据：必须有 refresh_token（刷新接口的必需参数），且必须有 mid；
     * 凭证形式二选一——TV/APP 登录有 access_key，网页登录有 SESSDATA。
     * 缺任何一个都说明这次轮不到刷新（可能是真没登录，也可能是登录态已经被清掉）。
     */
    private fun isLoggedIn(): Boolean {
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L) == 0L) return false
        if (SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "").isEmpty()) return false
        if (SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "").isNotEmpty()) return true
        val cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
        return NetWorkUtil.getInfoFromCookie("SESSDATA", cookies).isNotEmpty()
    }

    /**
     * 执行一次刷新流程。
     *
     * @return true 表示这次流程正常走完（含服务端说「不需要刷新」）；false 表示失败、需要走退避
     */
    private fun refreshNow(): Boolean {
        // TV / APP 登录：refresh_token 同时能续 access_token 与 cookies，走统一的 APP 刷新
        if (SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "").isNotEmpty()) {
            val refreshed = AppTokenRefreshApi.refreshAppToken()
            if (refreshed) {
                Logu.i(TAG, "access_token 已刷新")
            } else {
                Logu.w(TAG, "access_token 刷新未成功")
            }
            return refreshed
        }

        // 网页登录：先问服务端到底需不需要刷新，不需要就别去动 Cookie
        val cookieInfo = try {
            CookieRefreshApi.cookieInfo()
        } catch (e: Exception) {
            // 网络抖动 / 返回体缺 data / 登录态彻底过期都可能走到这里，都不能算成功
            Logu.w(TAG, "cookieInfo 获取失败：" + e.message)
            return false
        }
        if (!cookieInfo.optBoolean("refresh")) return true

        val correspondPath = CookieRefreshApi.getCorrespondPath(cookieInfo.optLong("timestamp", 0L))
        val refreshCsrf = CookieRefreshApi.getRefreshCsrf(correspondPath)
        if (refreshCsrf.isEmpty()) {
            Logu.w(TAG, "没能取到 refresh_csrf，本次不刷新（保留登录态，稍后重试）")
            return false
        }
        // 26.11.06.1：刷新结果分三档——只有 OK 才算成功；RETRY 保留登录态等退避重试；
        // NEED_LOGIN 才是服务端明确说会话没了（这时也不清本地凭证，交给启动页的登录失效流程）
        val result = try {
            CookieRefreshApi.refreshCookie(refreshCsrf)
        } catch (e: Exception) {
            Logu.w(TAG, "Cookie 刷新异常（保留登录态）：" + e.message)
            return false
        }
        if (result == CookieRefreshApi.RefreshResult.OK) {
            // 账号管理里存的是登录快照，Cookie 轮换后要同步，否则「切换账号」会把旧 Cookie 写回去
            try {
                AccountManager.saveCurrentAccount()
            } catch (e: Exception) {
                Logu.w(TAG, "刷新后同步账号快照失败：" + e.message)
            }
            // applyCredentials 会重置本调度器的节流状态（切号语义），刷新成功后要把「刚试过」
            // 重新记上，否则下一次 tick 会立刻再刷一遍，变成每 5 分钟轮换一次口令
            lastAttemptMs = SystemClock.elapsedRealtime()
            Logu.i(TAG, "Cookie 已刷新")
            return true
        }
        when (result) {
            CookieRefreshApi.RefreshResult.NEED_LOGIN ->
                Logu.w(TAG, "服务端判定登录态失效（-101），等用户重新登录；本地凭证保留")
            else ->
                Logu.w(TAG, "Cookie 刷新未成功（保留登录态，稍后重试）")
        }
        return false
    }
}
