package com.RobinNotBad.BiliClient.util

import android.content.Context
import com.RobinNotBad.BiliClient.R

/**
 * 订阅类接口（收藏夹订阅 / 合集订阅 / 番剧追番）失败时的错误码文案，全工程只此一处。
 *
 * 26.11.07 之前这段 `when` 在四个页面里逐份抄了四遍（收藏夹页、合集页、订阅列表页、番剧详情页），
 * 服务端加一个码就得改四处、漏一处就有一边显示「操作失败（xxxxx）」。文案本体仍在
 * `strings.xml` 的 `msg_subscription_*` 里，这里只做「码 → 键」的映射。
 *
 * 为什么是 `Context` 扩展而不是纯函数：这几句是用户可见文案，必须走资源（多语言 / 术语统一），
 * 而纯函数在 JVM 单测里拿不到 `Context`（见 `api/ApiErrorText.kt` 的同类取舍——那边是各接口
 * 共用、且不依赖资源的那几句）。
 */
fun Context.subscriptionErrorText(code: Int): String = when (code) {
    -101 -> getString(R.string.msg_subscription_need_login)
    -111 -> getString(R.string.msg_subscription_csrf_invalid)
    -400 -> getString(R.string.msg_subscription_bad_request)
    11010 -> getString(R.string.msg_subscription_content_gone)
    else -> getString(R.string.msg_subscription_failed_with_code, code)
}
