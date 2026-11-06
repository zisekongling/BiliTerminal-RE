package com.RobinNotBad.BiliClient.util

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * `Set-Cookie` → 本地 Cookie 串的合并策略（26.11.06.1「升级后掉登录」返工）。
 *
 * <h3>为什么单独抽一层</h3>
 * [NetWorkUtil.saveCookiesLocked] 原来把「解析 Set-Cookie / 合并 / 拼接」三段逻辑写在一个
 * 70 行的 Java 方法里，既没法单测，又踩了两个会**直接抹掉登录态**的坑：
 *
 * 1. **空值删除不看语义**：只要响应里来一条 `SESSDATA=`（不管是 `Max-Age=0` 的删除、还是服务端
 *    清 Cookie），旧实现就把本地 SESSDATA 覆盖成空串。`SplashActivity.hasLocalSession()` 判据
 *    正是「Cookie 里有非空 SESSDATA」，于是下一次冷启动就判定登录信息过期、清空整套凭证 ——
 *    用户看到的就是**掉登录**。Cookie 刷新接口（`/x/passport-login/web/cookie/refresh`）成功后
 *    会下发新的 `SESSDATA`/`bili_jct`/`DedeUserID`，而确认接口会让**旧** Cookie 失效；两边一旦
 *    错位，本地就会停在「凭据被抹掉、但服务端还认旧会话」半坏状态。
 * 2. **属性截断只认 `"; "`**：`Set-Cookie: SESSDATA=x;Path=/;Domain=...`（分号后不带空格）会被
 *    整段当成值存下来，之后按 `;` 重解析时错位。RFC 允许任意空白，所以必须按 `;` 切。
 *
 * <h3>本策略的硬规则</h3>
 * - [CREDENTIAL_KEYS] 里的键是**登录凭据**，任何响应都不得把它们删掉或写成空值（记一条
 *   [MergeResult.protectedKeys] 交给调用方打日志）。凭据只由登录 / 切号 / 退出登录这些
 *   **用户显式动作**（`AccountManager.applyCredentials` / `clearCurrentLogin`）改写。
 * - 非凭据键（`buvid3` / `bili_ticket` / `_uuid` …）正常遵守删除语义：`Max-Age<=0`、
 *   `Expires` 已过期即删除。
 * - 空值但没有过期语义的写入一律忽略（宁可不写，也不留 `key=` 这种半截值）。
 * - 合并保留未提及的旧键与顺序；输出用 `"; "` 连接、**末尾不带分隔符**（旧实现会留下尾随
 *   `"; "`，每轮合并还会多一个空段）。
 *
 * <h3>纯逻辑</h3>
 * 只用 `java.util.Locale` / `java.text.SimpleDateFormat`，不碰任何 Android API，因此可以在
 * JVM 单测里直接喂 `Set-Cookie` 串验证（见 `CookieJarPolicyTest`）。
 */
object CookieJarPolicy {

    /**
     * App 当作「登录凭据」的 Cookie 键。
     *
     * 与 [NetWorkUtil.buildGuestCookieString] 剔除的清单保持同一套语义：这些键一旦消失就等于
     * 掉登录，所以普通响应无权删除它们。新增凭据类 Cookie 时两处一起改。
     */
    val CREDENTIAL_KEYS: Set<String> = setOf(
        "SESSDATA",
        "bili_jct",
        "DedeUserID",
        "DedeUserID__ckMd5",
        "sid"
    )

    /** 一条解析后的 `Set-Cookie`（只保留第一个 `name=value` 与「是否过期」）。 */
    data class IncomingCookie(
        val key: String,
        val value: String,
        val expired: Boolean
    )

    /**
     * 合并结果。
     *
     * @param cookies 合并后的 Cookie 串（`"; "` 连接，末尾无分隔符）；无任何 Cookie 时为空串
     * @param protectedKeys 本次被「保护」掉的凭据键（响应想删/清空、但没有真的删），仅供日志
     */
    data class MergeResult(
        val cookies: String,
        val protectedKeys: List<String>
    )

    /**
     * 解析一条 `Set-Cookie`。
     *
     * @param setCookie 例：`SESSDATA=abc; Path=/; Domain=bilibili.com; Expires=Wed, 15 Nov 2023 …`
     * @return 解析不出 `name=value` 时返回 null（调用方跳过）
     */
    @JvmStatic
    fun parse(setCookie: String?): IncomingCookie? {
        if (setCookie.isNullOrBlank()) return null
        // 头里不可能出现换行；有的话一律按非法处理，避免拼进本地 Cookie 串
        if (setCookie.indexOf('\n') >= 0 || setCookie.indexOf('\r') >= 0) return null
        val parts = setCookie.split(';')
        val first = parts[0].trim()
        if (first.isEmpty()) return null
        val eq = first.indexOf('=')
        // 没有键（`=x` / `x`）直接判非法；值可以为空，那是删除语义
        if (eq <= 0) return null
        val key = first.substring(0, eq).trim()
        if (key.isEmpty()) return null
        val value = first.substring(eq + 1).trim()
        return IncomingCookie(key, value, value.isEmpty() || isExpiredByAttributes(parts))
    }

    /** 批量解析，解析失败的条目直接丢弃。 */
    @JvmStatic
    fun parseAll(setCookies: List<String>?): List<IncomingCookie> {
        if (setCookies.isNullOrEmpty()) return emptyList()
        val out = ArrayList<IncomingCookie>(setCookies.size)
        for (header in setCookies) {
            val parsed = parse(header) ?: continue
            out.add(parsed)
        }
        return out
    }

    /**
     * 把 [incoming] 合并进 [current]，返回新的 Cookie 串。
     *
     * 规则见类注释；结果与 [current] 语义相同（键值全等）时字符串也相同，调用方可以靠
     * `equals` 判断「这次响应没有改动 Cookie」而跳过落盘。
     */
    @JvmStatic
    fun merge(current: String?, incoming: List<IncomingCookie>?): MergeResult {
        val order = ArrayList<String>()
        val store = LinkedHashMap<String, String>()
        for ((key, value) in parseCookieString(current)) {
            if (!store.containsKey(key)) order.add(key)
            store[key] = value
        }

        val protectedKeys = ArrayList<String>(0)
        if (incoming != null) {
            for (cookie in incoming) {
                val key = cookie.key
                if (cookie.expired) {
                    if (CREDENTIAL_KEYS.contains(key)) {
                        // 凭据不接受删除/清空：保留旧值，才能保证「服务端让旧 Cookie 失效」这一步
                        // 不会先把本地登录态弄丢（真正失效时接口会回 -101，走登录失效流程）
                        if (store[key].orEmpty().isNotEmpty()) protectedKeys.add(key)
                        continue
                    }
                    store.remove(key)
                    order.remove(key)
                    continue
                }
                if (cookie.value.isEmpty()) {
                    if (CREDENTIAL_KEYS.contains(key)) protectedKeys.add(key)
                    continue
                }
                if (!store.containsKey(key)) order.add(key)
                store[key] = cookie.value
            }
        }

        val sb = StringBuilder()
        for (key in order) {
            val value = store[key] ?: continue
            if (value.isEmpty()) continue
            if (sb.isNotEmpty()) sb.append("; ")
            sb.append(key).append('=').append(value)
        }
        return MergeResult(sb.toString(), protectedKeys)
    }

    /**
     * 解析本地 Cookie 串为有序键值表：按 `;` 切、按**第一个** `=` 取值（Cookie 值本身允许含 `=`，
     * 如 `buvid3` / `bili_ticket` 的 base64 填充）；空键与空值一律跳过（空值留着只会让
     * `getInfoFromCookie` 拿到空串，等价于「没有」）。
     */
    @JvmStatic
    fun parseCookieString(cookies: String?): List<Pair<String, String>> {
        if (cookies.isNullOrBlank()) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (raw in cookies.split(';')) {
            val item = raw.trim()
            if (item.isEmpty()) continue
            val eq = item.indexOf('=')
            if (eq <= 0) continue
            val key = item.substring(0, eq).trim()
            val value = item.substring(eq + 1).trim()
            if (key.isEmpty() || value.isEmpty()) continue
            out.add(key to value)
        }
        return out
    }

    /** `Max-Age<=0` 或 `Expires` 已过期。解析不出来就当**没有**过期（fail-safe：宁可不删）。 */
    private fun isExpiredByAttributes(parts: List<String>): Boolean {
        var expiresPast = false
        for (i in 1 until parts.size) {
            val item = parts[i].trim()
            if (item.isEmpty()) continue
            val eq = item.indexOf('=')
            if (eq <= 0) continue
            val name = item.substring(0, eq).trim()
            var raw = item.substring(eq + 1).trim()
            if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') {
                raw = raw.substring(1, raw.length - 1).trim()
            }
            when {
                name.equals("max-age", ignoreCase = true) -> {
                    val seconds = raw.toLongOrNull() ?: continue
                    if (seconds <= 0L) return true
                }
                name.equals("expires", ignoreCase = true) -> {
                    val millis = parseHttpDate(raw) ?: continue
                    if (millis <= System.currentTimeMillis()) expiresPast = true
                }
            }
        }
        return expiresPast
    }

    /** HTTP 日期（RFC 1123 / RFC 850 / asctime）尽力解析；失败返回 null。 */
    private fun parseHttpDate(raw: String): Long? {
        val patterns = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy"
        )
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = false
                val parsed = format.parse(raw) ?: continue
                return parsed.time
            } catch (_: Throwable) {
                // 换下一种格式
            }
        }
        return null
    }
}
