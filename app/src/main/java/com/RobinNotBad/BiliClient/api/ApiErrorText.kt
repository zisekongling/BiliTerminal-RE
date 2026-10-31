package com.RobinNotBad.BiliClient.api

/**
 * API 层「操作失败 / 登录态」类文案的唯一出处（26.10.19 建立）。
 *
 * 为什么不放 `strings.xml`：API 层这些函数是**纯函数**，跟随 app/src/test 下的 JVM 单测一起跑，
 * 单测环境没有 Context，`BiliTerminal.context!!.getString(...)` 会直接空指针。
 * 所以服务端错误码 → 人话 的映射留在代码里，全工程只此一处；界面上要改这几句就改这里。
 *
 * 只收**各接口共用**的那几句（未登录 / 凭证失效 / 请求非法 / 操作失败兜底）；
 * `xxxErrorMsg(code)` 里**该接口特有**的错误码文案（"分组名里有不允许的字符" 之类）仍留在各自 API 类，
 * 它们不重复，搬过来反而让这张表失真。
 */
object ApiErrorText {

    /** 「操作失败（错误码 10001）」——把服务端错误码原样带上，用户报障时能对上日志。 */
    @JvmStatic
    fun opFailed(code: Any?): String = "操作失败（错误码 $code）"

    /** 「操作失败（-101）」——已知码已被前面分支吃掉，兜底时只带码本身。 */
    @JvmStatic
    fun opFailedBrief(code: Any?): String = "操作失败（$code）"

    /** 「操作失败：你的账号已被封禁」——接口给了可读原因时用它。 */
    @JvmStatic
    fun opFailedWithDetail(detail: Any?): String = "操作失败：$detail"

    /** 「还没有登录喵~」——服务端 -101（未登录）。各接口文案原本逐份抄，现统一在此。 */
    @JvmStatic
    fun loginRequired(): String = "还没有登录喵~"

    /** 「登录凭证已失效，请重新登录」——-111（csrf/凭证失效）；-102（账号封停）也走这句。 */
    @JvmStatic
    fun tokenExpired(): String = "登录凭证已失效，请重新登录"

    /** 「请求出错了，请稍后再试」——-400（请求非法/参数错误）。 */
    @JvmStatic
    fun requestIllegal(): String = "请求出错了，请稍后再试"

    /**
     * 「搜索被风控了，过一会儿再试」——搜索接口的**降级响应**：`code` 仍是 0，但 `data` 里
     * 既没有 `result` 也没有 `seid`，只有一个 `v_voucher`（见 `docs/bilibili-api-notes.md` §3.13）。
     *
     * 这种响应**不能当成"没有结果"**：以前 `SearchApi` 对它返回 null，页面就只剩一个空列表，
     * 用户完全不知道发生了什么（26.10.24 用户反馈「有时候搜出来什么都没有」就是这么来的）。
     * 现在抛出去让页面把这句话弹给用户。
     */
    @JvmStatic
    fun searchRejected(): String = "搜索被风控，请稍后再试"
}
