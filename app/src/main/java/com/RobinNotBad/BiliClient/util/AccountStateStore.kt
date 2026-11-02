package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.player.QualityAccessPolicy
import org.json.JSONObject

/**
 * 账号状态（是否登录、是否大会员）的读取与缓存。
 *
 * 用途：清晰度列表要按账号权限上锁（见 [com.RobinNotBad.BiliClient.player.QualityAccessPolicy]）。
 * 判据来自 `x/web-interface/nav`：`data.isLogin` 是登录态，`data.vip.status == 1` 是大会员
 * （`vip.type` 只区分月度/年度，都是大会员，所以不看它）。
 *
 * 为什么要缓存：
 * - 播放器每打开一次清晰度面板就查一次接口太浪费，且面板是同步弹出、不该等网络；
 * - 断网时接口会失败，这时只能退回上次的结果，而不是把「已登录的大会员」误锁成未登录。
 *
 * 保守原则：本机连 Cookie 都没有时直接判定未登录，不看缓存（避免退出登录后仍解锁）。
 */
object AccountStateStore {
    /** 账号状态快照。 */
    data class State(val loggedIn: Boolean, val vip: Boolean)

    private const val KEY_LOGGED_IN = "account_state_logged_in"
    private const val KEY_VIP = "account_state_vip"
    private const val KEY_TIME = "account_state_time"

    private val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"

    /**
     * 本机是否还留着登录凭据。
     *
     * 只证明「有 Cookie」，不证明服务端仍认这个登录态 —— 所以它只用于「没有就必须算未登录」
     * 这一个方向，另一个方向要靠 [refresh]。
     */
    private fun hasLocalCredentials(): Boolean {
        val cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
        val mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L)
        return cookies.isNotEmpty() && mid > 0
    }

    /** 读缓存（不发网络请求）。没有登录凭据时一律返回未登录。 */
    @JvmStatic
    fun cached(): State {
        if (!hasLocalCredentials()) return State(loggedIn = false, vip = false)
        val loggedIn = SharedPreferencesUtil.getBoolean(KEY_LOGGED_IN, true)
        val vip = loggedIn && SharedPreferencesUtil.getBoolean(KEY_VIP, false)
        return State(loggedIn, vip)
    }

    /**
     * 查一次真实账号状态并写缓存；网络/解析失败时退回 [cached]。
     *
     * 会在后台线程调用（播放器里用 `CenterThreadPool.run { }`），不要放主线程。
     */
    @JvmStatic
    fun refresh(): State {
        if (!hasLocalCredentials()) {
            save(State(loggedIn = false, vip = false))
            return State(loggedIn = false, vip = false)
        }

        return try {
            val json: JSONObject = NetWorkUtil.getJson(NAV_URL)
            val data = json.optJSONObject("data")
            if (data == null) {
                Logu.w("AccountState", "账号状态缺失 data 字段，沿用缓存")
                return cached()
            }
            val loggedIn = data.optBoolean("isLogin", false)
            val vip = loggedIn && data.optJSONObject("vip")?.optInt("status", 0) == 1
            val state = State(loggedIn, vip)
            save(state)
            Logu.d("AccountState", "账号状态刷新：登录=$loggedIn 大会员=$vip")
            state
        } catch (e: Exception) {
            Logu.w("AccountState", "账号状态获取失败，沿用缓存：${e.message}")
            cached()
        }
    }

    private fun save(state: State) {
        SharedPreferencesUtil.putBoolean(KEY_LOGGED_IN, state.loggedIn)
        SharedPreferencesUtil.putBoolean(KEY_VIP, state.vip)
        SharedPreferencesUtil.putLong(KEY_TIME, System.currentTimeMillis())
    }

    /**
     * 账号全局登录态被替换（登录完成 / 切号 / 退出登录）时调用：作废上一任账号的缓存。
     *
     * <p>缓存里只有「是否登录 / 是否大会员」，都是按账号算的。不清的话，从大会员账号切到
     * 普通账号，[cached] 仍会返回 `vip=true`，新账号照样能点开 4K，实际请求到的是降级流——
     * 这就是「切号后画质/权限混乱」的直接来源。清空后 `cached()` 退回默认（已登录、非大会员），
     * 等下一次 [refresh] 拿到真实状态。
     */
    @JvmStatic
    fun onAccountChanged() {
        SharedPreferencesUtil.removeValue(KEY_LOGGED_IN)
        SharedPreferencesUtil.removeValue(KEY_VIP)
        SharedPreferencesUtil.removeValue(KEY_TIME)
    }

    /**
     * 把起播/切档请求的清晰度按账号权限夹紧。
     *
     * 超出权限时服务端不会报错，而是**降级发流**：比如未登录请求 4K(qn=120) 拿到的是
     * 720P 甚至更低，请求 1080P 有时连 720P 的 DASH 流都不给。所以宁可直接请求
     * 权限内的最高档，免得「设置了 4K 反而比 720P 更糊」。
     */
    @JvmStatic
    fun clampQuality(qn: Int): Int {
        val state = cached()
        return qn.coerceAtMost(QualityAccessPolicy.maxUnlockedQn(state.loggedIn, state.vip))
    }
}
