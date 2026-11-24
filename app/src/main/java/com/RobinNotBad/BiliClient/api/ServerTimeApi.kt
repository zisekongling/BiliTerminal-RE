package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.util.Logu
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 走明文 http 取 B 站服务器时间（26.10.27），专给「系统时间错误」提示用。
 *
 * <p><b>为什么是 http</b>：设备时间错了时 HTTPS 证书全部校验失败，拿不到任何 https 响应；
 * http 不校验证书，能问到「现在几点」。`bilibili.com` 本就在明文白名单里，不用新加域名。
 *
 * <p><b>三条底线</b>（见 `docs/agents/security.md`）：
 * <ul>
 *   <li>用独立的 [client]，<b>不带 Cookie / 不加任何拦截器</b>——绝不能走 `NetWorkUtil` 主客户端，
 *       明文带上登录 Cookie 等于把账号送人。</li>
 *   <li>拿到的时间可被中间人篡改，<b>只用于显示提示</b>，不参与证书校验、不「修正」时间后放行连接。</li>
 *   <li>只在证书时间校验失败后才调用（见 `MsgUtil.err`），正常使用不产生这次请求。</li>
 * </ul>
 */
object ServerTimeApi {

    private const val TAG = "ServerTimeApi"

    /** 接口文档：`bilibili-API/docs/misc/time_stamp.md`，返回 `{"code":0,"data":{"now":秒}}`。 */
    private const val URL = "http://api.bilibili.com/x/report/click/now"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            // 不跟随跳转：万一被重定向到 https，这里本来就连不上，也不该跟去别的域名
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /**
     * 阻塞请求，返回服务器时间（毫秒）；任何失败返回 null。必须在后台线程调用。
     */
    @JvmStatic
    fun fetchServerMillis(): Long? {
        return try {
            client.newCall(Request.Builder().url(URL).get().build()).execute().use { resp ->
                val body = resp.body()?.string() ?: return null
                parseNowMillis(JSONObject(body))
            }
        } catch (e: Exception) {
            Logu.w(TAG, "取服务器时间失败：" + e.message)
            null
        }
    }

    /** 纯解析：`data.now`（秒）→ 毫秒。code 非 0、缺字段、值不合理（<= 0）都返回 null。 */
    @JvmStatic
    fun parseNowMillis(json: JSONObject?): Long? {
        if (json == null || json.optInt("code", -1) != 0) return null
        val now = json.optJSONObject("data")?.optLong("now", 0L) ?: return null
        return if (now > 0) now * 1000 else null
    }
}
