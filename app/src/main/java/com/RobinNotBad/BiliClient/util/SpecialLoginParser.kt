package com.RobinNotBad.BiliClient.util

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 「特殊登录」登录信息的纯解析 / 校验逻辑，与 UI 无关，便于 JVM 单测。
 *
 * 支持两种输入，都是为了「换设备搬登录态」这一个目的：
 *
 * 1. **单账号**（旧格式，继续兼容）：
 *    `{"cookies":"...","refresh_token":"...","access_key":"..."}`
 * 2. **多账号**（本机导出的全部账号）：
 *    `{"format":"rebili.accounts","version":1,"accounts":[{...},{...}]}`
 *    也接受裸数组 `[{...},{...}]`，方便用户手工增删。
 *
 * 单条记录的校验规则（任一不满足即整体失败，宁可拒绝也不写半截登录态）：
 * 1. 必须带 `cookies` 且非空；
 * 2. 必须带 `refresh_token`；
 * 3. `cookies` 里必须能解析出 `DedeUserID`（否则连登录的是谁都不知道）；
 * 4. 记录里若带 `mid`，必须与 `DedeUserID` 一致——两条粘串了、数组整体挪了一位，
 *    都靠这条拦住，不然就会把 A 的 Cookie 和 B 的 mid 拼成一个"混合账号"。
 *
 * 多账号会按 mid 去重：同一 mid 只保留**最后**一条（视作更新的那份）。重复条目留着不管，
 * 切号时可能拿到其中旧的一条，表现就是「切过去又掉登录」。
 */
object SpecialLoginParser {

    /** 多账号导出格式标识。导入时看到 `accounts` 字段就当多账号处理，不看这个值也能进。 */
    const val EXPORT_FORMAT = "rebili.accounts"

    /** 多账号导出格式版本号，当前只有 1。 */
    const val EXPORT_VERSION = 1

    sealed class Result {
        data class Success(
            val cookies: String,
            val refreshToken: String,
            /** 可选字段，缺失时为 null */
            val accessKey: String?,
            val mid: Long,
            /** 导出文件里带的昵称，可能为空 */
            val name: String = "",
            /** 导出文件里头像地址，可能为空 */
            val avatar: String = "",
        ) : Result()

        data class Failure(val message: String) : Result()
    }

    /** [parseAny] 的结果：单账号、多账号、失败三种。 */
    sealed class ParseResult {
        data class Single(val account: Result.Success) : ParseResult()

        /** [deduped] 是被合并掉的重复条目数量，用于给用户一句真实的提示。 */
        data class Batch(val accounts: List<Result.Success>, val deduped: Int) : ParseResult()

        data class Failure(val message: String) : ParseResult()
    }

    /**
     * 只按「单账号」解析。多账号数据在这里会失败（没有 cookies 字段），
     * 需要两种都认时用 [parseAny]。
     */
    fun parse(raw: String?): Result {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Result.Failure(UiText.of("没有读取到登录信息，剪贴板是空的"))

        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return Result.Failure(UiText.of("登录信息不是合法的 JSON，请检查剪贴板内容"))
        }

        return parseObject(json, "登录信息")
    }

    /**
     * 统一入口：单账号 / 多账号 / 裸数组三种输入都认。
     *
     * 判断顺序有意固定：先看是不是裸数组，再看有没有 `accounts` 字段，最后才当单账号——
     * 导出的多账号 JSON 里没有 `cookies` 字段，按单账号解析必然失败，顺序弄反就等于不支持批量导入。
     */
    fun parseAny(raw: String?): ParseResult {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ParseResult.Failure(UiText.of("没有读取到登录信息，剪贴板是空的"))

        if (text.startsWith("[")) {
            val array = try {
                JSONArray(text)
            } catch (e: JSONException) {
                return ParseResult.Failure(UiText.of("登录信息不是合法的 JSON，请检查剪贴板内容"))
            }
            return parseAccounts(array)
        }

        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return ParseResult.Failure(UiText.of("登录信息不是合法的 JSON，请检查剪贴板内容"))
        }

        if (json.has("accounts")) {
            val array = json.optJSONArray("accounts")
                ?: return ParseResult.Failure(UiText.of("多账号登录信息里的 accounts 字段不是数组"))
            return parseAccounts(array)
        }

        return when (val single = parseObject(json, "登录信息")) {
            is Result.Success -> ParseResult.Single(single)
            is Result.Failure -> ParseResult.Failure(single.message)
        }
    }

    private fun parseAccounts(array: JSONArray): ParseResult {
        if (array.length() == 0) return ParseResult.Failure(UiText.of("多账号登录信息里没有任何账号"))

        // LinkedHashMap 保留导出顺序，同时也天然完成按 mid 去重
        val byMid = LinkedHashMap<Long, Result.Success>()
        var deduped = 0
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
                ?: return ParseResult.Failure(UiText.of("第 ${i + 1} 个账号不是 JSON 对象"))
            when (val parsed = parseObject(item, "第 ${i + 1} 个账号")) {
                is Result.Failure -> return ParseResult.Failure(parsed.message)
                is Result.Success -> if (byMid.put(parsed.mid, parsed) != null) deduped++
            }
        }
        if (byMid.isEmpty()) return ParseResult.Failure(UiText.of("多账号登录信息里没有任何可用的账号"))
        return ParseResult.Batch(byMid.values.toList(), deduped)
    }

    /**
     * 校验单条记录。[where] 只用于拼错误提示（"登录信息"/"第 2 个账号"）。
     */
    private fun parseObject(json: JSONObject, where: String): Result {
        if (!json.has("cookies")) return Result.Failure(UiText.of(where + "缺少 cookies 字段"))
        val cookies = json.optString("cookies").trim()
        if (cookies.isEmpty()) return Result.Failure(UiText.of(where + "的 cookies 是空的"))

        if (!json.has("refresh_token")) return Result.Failure(UiText.of(where + "缺少 refresh_token 字段"))

        val mid = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies).toLongOrNull()
            ?: return Result.Failure(UiText.of(where + "的 Cookie 中缺少用户 ID（DedeUserID），请检查复制的内容是否完整"))

        if (json.has("mid")) {
            val declared = json.optLong("mid", -1L)
            if (declared != mid) {
                return Result.Failure(UiText.of(where + "的 mid 与 Cookie 里的用户 ID 不一致，数据可能串行了"))
            }
        }

        return Result.Success(
            cookies = cookies,
            refreshToken = json.optString("refresh_token"),
            // JSON null 在部分 org.json 版本里 optString 会给出字符串 "null"，必须显式排除，
            // 否则会把 "null" 当成 access_key 存进去，请求全部鉴权失败
            accessKey = if (json.has("access_key") && !json.isNull("access_key")) {
                json.optString("access_key")
            } else null,
            mid = mid,
            // 显式 null 同样要挡掉：org.json 的 optString 对 JSON null 会返回字符串 "null"
            name = if (json.isNull("name")) "" else json.optString("name", ""),
            avatar = if (json.isNull("avatar")) "" else json.optString("avatar", ""),
        )
    }
}
