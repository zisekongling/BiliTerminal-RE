package com.RobinNotBad.BiliClient.util

import org.json.JSONException
import java.io.IOException

/**
 * 「我的」页账号信息加载失败的原因分类与文案（纯逻辑，JVM 单测直接调用）。
 *
 * 背景（反馈 #163）：`MySpaceActivity.loadUserInfo()` 原来只有 `catch (e) { report(e) }`——
 * 请求一失败就永远停在「加载中...」，用户只看到无限转圈，既不知道为什么、也没有重试入口。
 * 现在失败会落到卡片上的「加载失败，点我重试」，这里负责「异常 / 空数据 → 原因」与「原因 → 文案」。
 *
 * 只区分**用户据此能采取不同动作**的几类：网络不通 → 检查网络；接口没给账号数据（未登录 /
 * code≠0 / 风控降级）→ 重新登录；解析失败、其它 → 稍后重试。
 *
 * 不细分 B 站错误码：`UserInfoApi.getCurrentUserInfo()` 不把 code 透出来，data 缺失时返回
 * `mid=0` 的占位对象（`new UserInfo(0, "加载失败", …)`）并且**不抛异常**，所以「没拿到账号数据」
 * 只能用 `mid <= 0` 判断，这里用 `e == null` 表示这种情形。
 */
object MySpaceLoadErrorText {

    /** 失败原因类别。 */
    enum class Reason {
        /** 网络请求失败（`IOException` 一族：超时、DNS、连接被断、HTTP 错误码）。 */
        NETWORK,

        /** 响应解析失败（`JSONException`）。 */
        PARSE,

        /** 请求没抛异常，但接口没给账号数据：未登录（-101）、code≠0 或风控降级。 */
        NO_DATA,

        /** 其它异常（未预期的运行时异常）。 */
        UNKNOWN
    }

    /**
     * 把加载时抛出的异常归类。
     *
     * @param e 加载时的异常；`null` 表示**没抛异常**，即接口返回了没有账号数据的占位对象
     */
    @JvmStatic
    fun reasonOf(e: Throwable?): Reason = when (e) {
        null -> Reason.NO_DATA
        is IOException -> Reason.NETWORK
        is JSONException -> Reason.PARSE
        else -> Reason.UNKNOWN
    }

    /**
     * 失败原因对应的文案（`UiText.of()` 的字面量键，值在 `strings.xml` 的 `text_myspace_load_reason_*`）。
     *
     * 这里返回字面量而不是资源 id，是为了能在 JVM 单测里直接断言：本仓库没有 Robolectric，
     * 纯函数里拿不到 Context。显示时由界面侧 `UiText.of(textOf(reason))` 换成资源里的值。
     */
    @JvmStatic
    fun textOf(reason: Reason): String = when (reason) {
        Reason.NETWORK -> "网络异常"
        Reason.PARSE -> "数据解析失败"
        Reason.NO_DATA -> "登录可能已失效"
        Reason.UNKNOWN -> "未知错误"
    }

    /** 失败态下点卡片该做什么。 */
    enum class TapAction {
        /** 跳登录页。 */
        LOGIN,

        /** 重新请求一次。 */
        RETRY
    }

    /**
     * 失败原因 → 点卡片的动作。
     *
     * 不能一律「点我重试」：真掉登录时（[Reason.NO_DATA]）重试永远不会成功，用户会一直卡在失败页；
     * 而改动前点卡片本来能跳登录页，这条恢复路径不能丢。反过来，网络/解析/其它异常只是「这一次
     * 没成」，重试才有意义。动作与文案必须成对（见 [titleOf]），否则卡片会写着「重试」却跳登录页。
     */
    @JvmStatic
    fun tapActionOf(reason: Reason): TapAction = when (reason) {
        Reason.NO_DATA -> TapAction.LOGIN
        Reason.NETWORK, Reason.PARSE, Reason.UNKNOWN -> TapAction.RETRY
    }

    /**
     * 失败态首行那句动作提示（`UiText.of()` 的字面量键，值在 `strings.xml` 的
     * `text_myspace_load_failed_*`）。与 [tapActionOf] 一一对应，别各写各的。
     */
    @JvmStatic
    fun titleOf(action: TapAction): String = when (action) {
        TapAction.LOGIN -> "加载失败，点我重新登录"
        TapAction.RETRY -> "加载失败，点我重试"
    }
}
