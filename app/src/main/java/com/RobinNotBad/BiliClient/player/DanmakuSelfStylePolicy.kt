package com.RobinNotBad.BiliClient.player

import java.util.Locale
import java.util.zip.CRC32

/**
 * 「本人弹幕边框」的纯判定逻辑（批次 3）。
 *
 * ## 背景
 *
 * B 站弹幕的发送者标识有两种下发形态，两种都是 **mid 的 CRC32 值的字符串形式**：
 * - 分段接口（protobuf 的 `DanmakuElem.midHash`）给的是**小写十六进制、无前导零**（如 `"83dcefb7"`）；
 * - 旧 XML 接口（`<d p="...">` 的第 6 段，见参考实现）给的是**十六进制、可能带前导零**（如 `"057075e9"`）。
 *
 * 所以判定不能直接字符串相等：要先把双方归一化（统一小写、去前导零、只接受十六进制字符），
 * 再拿本人 mid 的 CRC32 的**十进制**与**十六进制**两种写法分别比一次。
 *
 * 本对象只做纯计算：不读 SharedPreferences、不碰 Android 类型、不持有弹幕对象，
 * 因此可以直接被 JVM 单测覆盖（见 `app/src/test/.../player/DanmakuSelfStylePolicyTest.kt`）。
 * 取本人 mid、读开关、取主题色这些副作用都留在调用方（[DanmakuManager]）。
 */
object DanmakuSelfStylePolicy {

    /**
     * 归一化后允许的最大长度。
     *
     * CRC32 是无符号 32 位：十进制最多 10 位（`4294967295`）、十六进制最多 8 位。
     * 取 10 是为兼容「十进制写法」；超过这个长度的一定不是合法 hash，直接判为无效，
     * 避免把用户的正常文本误当成 hash 去比对。
     */
    private const val MAX_HASH_LENGTH = 10

    /** 十六进制字符集（归一化后只允许出现这些字符）。 */
    private const val HEX_CHARS = "0123456789abcdef"

    /**
     * 计算 `mid` 的 CRC32（B 站下发的 `userHash`/`midHash` 就是它的字符串形式）。
     *
     * 计算口径与 B 站一致：把 mid 的**十进制字符串**逐字符（ASCII 字节）喂进标准 CRC-32
     * （IEEE 802.3，即 `java.util.zip.CRC32`），结果是**无符号 32 位**值。
     *
     * @param mid 用户 mid；`<= 0`（未登录/无效）返回 0，调用方应据此直接跳过
     */
    @JvmStatic
    fun crc32Of(mid: Long): Long {
        if (mid <= 0L) return 0L
        val crc = CRC32()
        for (ch in mid.toString()) {
            // CRC32.update(int) 只取低 8 位；mid 的十进制字符都在 ASCII 范围内
            crc.update(ch.code)
        }
        return crc.value
    }

    /**
     * 本人 mid 的 CRC32 的**十进制**写法（如 mid=1 → `"2212294583"`）。
     *
     * 分段接口（protobuf）实际下发的是十六进制，这里保留十进制写法是因为：算法兼容层
     * 可能把同一个 CRC32 值序列化成十进制，多比一种写法不增加误判（见 [matchesOwnHash]）。
     */
    @JvmStatic
    fun decimalHash(mid: Long): String = if (mid <= 0L) "" else crc32Of(mid).toString()

    /**
     * 本人 mid 的 CRC32 的**十六进制**写法（无前导零、小写，如 mid=1 → `"83dcefb7"`）。
     *
     * 这是 B 站 protobuf `DanmakuElem.midHash` 与 XML `p` 字段第 6 段的真实形态。
     */
    @JvmStatic
    fun hexHash(mid: Long): String = if (mid <= 0L) "" else java.lang.Long.toHexString(crc32Of(mid))

    /**
     * 把弹幕下发的发送者 hash 归一化成可比较的形态。
     *
     * 规则：去首尾空白 → 转小写 → 只允许 `0-9a-f` → 去前导零（至少保留 1 位）。
     * 任何一条不满足（null、空串、超长、含非十六进制字符）一律返回空串，表示「无法识别」。
     *
     * 这么做的原因：XML 侧可能给 `"057075e9"`（带前导零）、某些实现会给大写十六进制；
     * 而 `"0"`、`""`、`"unknown"` 这类值必须判为无效，否则会误给普通弹幕加边框。
     */
    @JvmStatic
    fun normalize(raw: String?): String {
        if (raw == null) return ""
        val trimmed = raw.trim().lowercase(Locale.US)
        if (trimmed.isEmpty() || trimmed.length > MAX_HASH_LENGTH) return ""
        for (ch in trimmed) {
            if (HEX_CHARS.indexOf(ch) < 0) return ""
        }
        // 去前导零："057075e9" → "57075e9"；"000" → "0"
        var start = 0
        while (start < trimmed.length - 1 && trimmed[start] == '0') start++
        return trimmed.substring(start)
    }

    /**
     * 判断一条弹幕是否由本人发送。
     *
     * @param ownMid 本人 mid；`<= 0`（未登录）直接 false —— 未登录时任何弹幕都不加边框
     * @param senderHash 弹幕自带的发送者 hash（[master.flame.danmaku.danmaku.model.BaseDanmaku.userHash]）
     */
    @JvmStatic
    fun matchesOwnHash(ownMid: Long, senderHash: String?): Boolean {
        if (ownMid <= 0L) return false
        val canonical = normalize(senderHash)
        if (canonical.isEmpty()) return false
        return canonical == decimalHash(ownMid) || canonical == hexHash(ownMid)
    }

    /**
     * 是否应该给这条弹幕加边框 = 开关打开且是本人弹幕。
     *
     * 开关读取放在调用方（`DanmakuManager` 在 prepare 时把
     * `SettingsKeys.PLAYER_DANMAKU_SELF_BORDER` 快照成布尔值传进来），
     * 这样本函数在单测里可以不依赖 SharedPreferences。
     */
    @JvmStatic
    fun shouldApplyBorder(ownMid: Long, senderHash: String?, enabled: Boolean): Boolean =
        enabled && matchesOwnHash(ownMid, senderHash)

    /**
     * 把主题主色转成边框色：强制不透明（`or 0xFF000000`）。
     *
     * 为什么不用写死的白色：边框的意义是「一眼认出自己发的弹幕」，用主题主色既能跟随用户
     * 换肤，也和弹幕区其余颜色不冲突；不透明是硬要求 —— DFM 画的是描边矩形，半透明色在
     * 深色视频背景上几乎看不见。
     *
     * @param primary 主题主色（调用方传 `ColorScheme.PRIMARY`）
     */
    @JvmStatic
    fun borderColorFor(primary: Int): Int = primary or 0xFF000000.toInt()
}
