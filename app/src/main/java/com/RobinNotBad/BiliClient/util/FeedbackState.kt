package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.FeedbackItem
import com.RobinNotBad.BiliClient.model.FeedbackReply

/**
 * 一条反馈对用户来说「现在轮到谁」的三态（26.11.27 新增）。
 *
 * <h3>为什么不用 handled</h3>
 * [FeedbackItem.handled] 是开发者的内部待办标记：线上 263 条反馈里 232 条（88%）都停在同一个值上，
 * 用户看它得不到任何信息；「无法复现」「不是 bug」同样会被标掉。三态是服务端按回复时间线
 * **派生**出来的，只回答一个问题——最后说话的是我还是开发者。
 *
 * <h3>为什么不兼容成四态</h3>
 * 「开发者判定已修复 / 无法复现」属于开发者的结论，不是用户视角的状态，本轮不下发客户端
 * （服务端存在 outcome 列，只给控制台看）。用户侧只关心「还没回」「回了」「结束了」。
 */
object FeedbackState {

    /** 还没开发者回复（或用户追了一句、开发者还没接话）——现在轮到开发者。 */
    const val WAITING_DEV = "waiting_dev"

    /** 开发者已经回复——现在轮到用户，用户可以直接结案。 */
    const val REPLIED = "replied"

    /** 用户自己结案了；之后追加说明会自动重开。 */
    const val CLOSED = "closed"

    /** 服务端下发的值是否是合法三态之一。 */
    @JvmStatic
    fun isValid(state: String?): Boolean =
        state == WAITING_DEV || state == REPLIED || state == CLOSED

    /** 这条反馈下面有没有开发者回复（[FeedbackReply.isFromUser] 为 false 的都算）。 */
    @JvmStatic
    fun hasDevReply(replies: List<FeedbackReply>?): Boolean {
        if (replies.isNullOrEmpty()) return false
        for (reply in replies) {
            if (reply != null && !reply.isFromUser()) return true
        }
        return false
    }

    /**
     * 取用户视角的状态。
     *
     * 服务端给了合法值就直接用；**老服务端不返回 state**（这个字段是 26.11.27 才加的），
     * 此时按回复情况回落成两态：有开发者回复→[REPLIED]，否则→[WAITING_DEV]。
     *
     * <p>回落**故意不看 handled**：handled 只是开发者内部待办（见类注释），拿它判状态会说谎
     * ——比如「无法复现」被标掉时，用户看到的应该是「已回复」而不是「已受理」。
     *
     * <p>纯函数，单测直接调。
     */
    @JvmStatic
    fun resolve(state: String?, replies: List<FeedbackReply>?): String {
        if (isValid(state)) return state!!
        return if (hasDevReply(replies)) REPLIED else WAITING_DEV
    }

    /** [resolve] 的整条重载，省得调用方每次手写三个参数。 */
    @JvmStatic
    fun resolve(item: FeedbackItem?): String = resolve(item?.state, item?.replies)

    /**
     * 状态的显示名。走 [UiText] 是为了让中英文案能统一收进 strings.xml
     * （「已结束」复用投票页已有的那条，不另建重复文案）。
     */
    @JvmStatic
    fun label(state: String?): String = when (state) {
        CLOSED -> UiText.of("已结束")
        REPLIED -> UiText.of("已回复")
        else -> UiText.of("待回复")
    }
}
