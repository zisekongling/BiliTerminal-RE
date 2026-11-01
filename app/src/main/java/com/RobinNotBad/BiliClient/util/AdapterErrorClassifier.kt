package com.RobinNotBad.BiliClient.util

import java.util.Locale

/**
 * 判断一条异常信息到底像不像 RecyclerView / Adapter 内部的「列表状态不一致」。
 *
 * <p><b>背景（反馈 #84）</b>：[MsgUtil] 曾经把所有 `IndexOutOfBoundsException` 都当成 Adapter 错误，
 * 显示「遇到Adapter错误：无需上报」。但消息页打开时，空会话列表会让
 * `PrivateMsgApi.getUsersInfo` 拼出 `substring(0, -1)` 抛 `StringIndexOutOfBoundsException`——
 * 用户看到的是「Adapter错误」，开发者看到的「无需上报」也把它藏了，真正的问题被掩盖了几个月。
 *
 * <p>现在改成：只有异常信息里真的带 RecyclerView / Adapter 特征时才按 Adapter 错误提示；
 * 其余的 `IndexOutOfBoundsException` 按普通错误显示（带异常原文，便于反馈时定位）。
 *
 * <p>**局限**：只看异常消息，不看堆栈。适配器内部的越界（消息只有 `Index: 1, Size: 1`）会被归到
 * 「普通错误」——这是刻意的：宁可多显示一条真实错误，也不要把陌生异常一律标成「无需上报」。
 *
 * <p>纯逻辑，不碰 Android API，JVM 单测直接调用。
 */
object AdapterErrorClassifier {

    /** 命中的特征串（全部小写后做子串匹配）。 */
    private val MARKERS = arrayOf(
        "recyclerview",
        "inconsistency detected",
        "invalid item position",
        "invalid view holder adapter position",
        "adapter position",
        "notifyitem",
        "adapter"
    )

    /**
     * @param message 异常信息（通常传 `e.toString()`，含异常类名与 message）
     * @return true 表示信息里带 RecyclerView / Adapter 特征，可以按「列表报错」处理
     */
    @JvmStatic
    fun looksLikeAdapterError(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val lower = message.lowercase(Locale.ROOT)
        for (marker in MARKERS) {
            if (lower.contains(marker)) return true
        }
        return false
    }
}
