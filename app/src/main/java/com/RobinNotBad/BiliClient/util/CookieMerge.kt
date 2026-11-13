package com.RobinNotBad.BiliClient.util

import java.util.Locale

/**
 * `Set-Cookie` → 本地 Cookie 串的**鉴权键白名单**层（O5，补在 [CookieJarPolicy] 之上）。
 *
 * <h3>为什么不直接改 CookieJarPolicy</h3>
 * [CookieJarPolicy] 已经是「解析 + 合并 + 拼接」的完整引擎，且被 [NetWorkUtil] 唯一使用、
 * 有自己的一整套单测（`CookieJarPolicyTest`）。它缺两件事：
 *
 * 1. **[CookieJarPolicy.CREDENTIAL_KEYS] 是大小写敏感的字面量集合**：`Set-Cookie` 里来一条
 *    小写 `sessdata=`，在它眼里就是个普通键，会照删不误 —— 而 B 站的响应偶尔就是小写的。
 *    更要紧的是大小写不同的同一个键会在本地串里**各存一份**（`SESSDATA=新; sessdata=旧`），
 *    之后 `getInfoFromCookie` 取到哪一份纯看顺序。
 * 2. **清单里没有 `ac_time_value`**：Cookie 刷新（`/x/passport-login/web/cookie/refresh`）要用它，
 *    被一条空值覆盖掉就等于刷新链路静默坏掉（用户表现为「登录看着还在、但过一段时间就掉」）。
 *
 * 那两处都在 [CookieJarPolicy] 内部，而它不在本任务的写入范围里；复制一份引擎出来又会让
 * 原引擎的合并规则分叉（同一个坑要修两遍）。所以这一层的做法是：**只做键名归一 + 鉴权写入门控，
 * 剩下的合并规则原样交给 [CookieJarPolicy.merge]**。
 *
 * <h3>硬规则</h3>
 * - 键名匹配一律**大小写不敏感**（比对前 `lowercase(Locale.US)`）。
 * - 入站键若与本地某个键仅大小写不同，则**沿用本地已有拼写**写入：本地串里永远不会出现
 *   `SESSDATA` / `sessdata` 两份，也让 [CookieJarPolicy] 里那套大小写敏感的凭据保护仍然命中。
 * - [AUTH_COOKIE_NAMES] 里的键**不接受删除/清空**（`Max-Age<=0`、`Expires` 过期、空值都只记一条
 *   记录到 [Result.protectedKeys]，本地旧值保留）。
 * - 鉴权键的**更新**由 [allowAuthCookieUpdates] 决定：默认放行，因为 B 站会在**任意响应**里通过
 *   `Set-Cookie` 轮换 `bili_jct`（见 [NetWorkUtil.saveCookiesLocked] 的注释），拦掉更新会让
 *   `bili_jct` 与 `csrf` 对不上、所有写操作开始报 -111。
 *
 * <h3>纯逻辑</h3>
 * 只用 `java.util.Locale` 与本包内的纯策略，不碰 Android API，可在 JVM 单测里直接喂头验证
 * （见 `CookieMergeTest`）。
 */
object CookieMerge {

    /**
     * App 视为「登录凭据」的 Cookie 键，**全部小写**（比对时也转小写）。
     *
     * 内容是 [CookieJarPolicy.CREDENTIAL_KEYS] 与本任务要求的 5 个鉴权键的**并集**：
     * - 那 4 个凭据（sessdata / bili_jct / dedeuserid / dedeuserid__ckmd5）+ `sid` 来自
     *   [CookieJarPolicy.CREDENTIAL_KEYS]，取并集是为了**不放松**它已有的保护
     *   （这一层的语义是「更严」，不能把 sid 变回普通键）；
     * - `ac_time_value` 是 Cookie 刷新接口（`/x/passport-login/web/cookie/refresh`）的入场券，
     *   原清单里没有它 —— 被一条空值覆盖掉就等于刷新链路静默坏掉（用户表现为「登录看着还在、
     *   但过一段时间就掉」），这是本层要补的那一格。
     *
     * 新增凭据类 Cookie 时，这里与 [NetWorkUtil.buildGuestCookieString] 的剔除清单要一起改。
     */
    val AUTH_COOKIE_NAMES: Set<String> = linkedSetOf(
        "sessdata",
        "bili_jct",
        "dedeuserid",
        "dedeuserid__ckmd5",
        "sid",
        "ac_time_value"
    )

    /** 合并结果：与 [CookieJarPolicy.MergeResult] 同形，另加本层拦下的鉴权键。 */
    data class Result(
        /** 合并后的 Cookie 串（`"; "` 连接，末尾无分隔符）；无任何 Cookie 时为空串 */
        val cookies: String,
        /** 本次被「保护」掉的鉴权键（响应想删/清空或无权更新），仅供日志 */
        val protectedKeys: List<String>
    )

    /** 是否是需要保护的鉴权键（大小写不敏感）。 */
    @JvmStatic
    fun isAuthCookie(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        return AUTH_COOKIE_NAMES.contains(key.trim().lowercase(Locale.US))
    }

    /**
     * 把已解析的 [incoming] 合并进 [current]。
     *
     * @param current 本地现存的 Cookie 串（可能为 null / 空）
     * @param incoming 已解析的 `Set-Cookie` 列表（[CookieJarPolicy.parseAll] 的产物）
     * @param allowAuthCookieUpdates 是否允许响应**改写**鉴权键的值；false 时鉴权键只保护、不更新
     */
    @JvmStatic
    fun merge(
        current: String?,
        incoming: List<CookieJarPolicy.IncomingCookie>?,
        allowAuthCookieUpdates: Boolean
    ): Result {
        // 本地已有的键：小写 → 原拼写。只收非空值（parseCookieString 已过滤空值），
        // 因此「表里没有这个键」等价于「本地没有可丢的登录态」。
        val localSpelling = LinkedHashMap<String, String>()
        for ((key, _) in CookieJarPolicy.parseCookieString(current)) {
            val lower = key.lowercase(Locale.US)
            if (!localSpelling.containsKey(lower)) localSpelling[lower] = key
        }

        val protectedKeys = LinkedHashSet<String>()
        val prepared = ArrayList<CookieJarPolicy.IncomingCookie>(incoming?.size ?: 0)
        if (incoming != null) {
            for (cookie in incoming) {
                if (cookie.key.isBlank()) continue
                val lower = cookie.key.lowercase(Locale.US)
                val hasLocal = localSpelling.containsKey(lower)
                // 大小写不同也当同一个 Cookie：沿用本地拼写，避免一份凭据存两个键
                val key = if (hasLocal) localSpelling.getValue(lower) else cookie.key
                if (isAuthCookie(cookie.key)) {
                    if (cookie.expired || cookie.value.isEmpty()) {
                        // 删除/清空鉴权键：只记录，不写（本地旧值原样保留）
                        if (hasLocal) protectedKeys.add(key)
                        continue
                    }
                    if (!allowAuthCookieUpdates) {
                        protectedKeys.add(key)
                        continue
                    }
                }
                prepared.add(if (key == cookie.key) cookie else cookie.copy(key = key))
            }
        }

        // 剩下的过期 / 空值 / 拼接规则全交给 CookieJarPolicy，避免同一套规则两处维护
        val merged = CookieJarPolicy.merge(current, prepared)
        protectedKeys.addAll(merged.protectedKeys)
        return Result(merged.cookies, protectedKeys.toList())
    }

    /**
     * 便捷重载：直接喂原始 `Set-Cookie` 头（生产调用走这条，省掉调用方自己 parseAll）。
     */
    @JvmStatic
    fun mergeHeaders(
        current: String?,
        headers: List<String>?,
        allowAuthCookieUpdates: Boolean
    ): Result = merge(current, CookieJarPolicy.parseAll(headers), allowAuthCookieUpdates)
}
