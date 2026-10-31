package com.RobinNotBad.BiliClient.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.RobinNotBad.BiliClient.model.MessageCard

/**
 * 可选私有功能的反射门面。
 *
 * 有一小批功能只存在于私有构建：源码在仓库根目录的 `private/`（已 gitignore，
 * 由 CI 从私有仓库 checkout 进来），公开仓库里只有这个门面与少量条件挂点。
 * 机制说明见 `docs/optional-features.md`。
 *
 * 规则：
 *  - **这里绝不写实现**，只反射转发到 `com.RobinNotBad.BiliClient.feature.FeatureHolder`；
 *  - 所有方法都吞异常：私有代码不在场时返回 null / no-op，公共构建的行为与从前完全一致；
 *  - 于是公开仓库能编、能跑、能发版，私有构建才多出这些功能。
 *
 * 私有侧的 FeatureHolder 用 @JvmStatic 暴露同名静态方法，参数只有框架类型。
 */
object OptionalFeatures {

    private const val HOLDER_CLASS = "com.RobinNotBad.BiliClient.feature.FeatureHolder"

    /** 私有注入是否在场。null = 不在场（公开构建、PR 构建、别人的开发机）。 */
    private val holder: Class<*>? by lazy {
        try {
            Class.forName(HOLDER_CLASS)
        } catch (e: Throwable) {
            null
        }
    }

    val available: Boolean
        get() = holder != null

    /** 菜单项 key，不在场时为 null。 */
    fun menuKey(): String? = invokeString("menuKey")

    /** 菜单项显示名，不在场时为 null。 */
    fun menuLabel(context: Context): String? = try {
        holder?.getMethod("menuLabel", Context::class.java)?.invoke(null, context) as? String
    } catch (e: Throwable) {
        null
    }

    /** 菜单项对应的页面 Class，不在场时为 null。 */
    fun menuActivity(): Class<*>? = try {
        holder?.getMethod("menuActivity")?.invoke(null) as? Class<*>
    } catch (e: Throwable) {
        null
    }

    /**
     * 往视频详情页的动作区插一行按钮（插在 [anchor] 之前）。具体插什么、点了做什么，
     * 全部由私有侧决定；不在场时什么都不做，调用方不用判空。
     */
    fun attachDetailActionRow(
        container: ViewGroup,
        anchor: View,
        videoId: String,
        aid: Long,
        title: String
    ) {
        try {
            holder?.getMethod(
                "attachDetailActionRow",
                ViewGroup::class.java,
                View::class.java,
                String::class.java,
                java.lang.Long.TYPE,
                String::class.java
            )?.invoke(null, container, anchor, videoId, aid, title)
        } catch (e: Throwable) {
            // 私有功能异常绝不能影响详情页正常显示
            Logu.w("OptionalFeatures", "attachDetailActionRow failed: " + e.message)
        }
    }

    /**
     * 评论长按面板里「举报」那一项的文案。
     *
     * 私有构建返回它自己的字符串，公开构建返回 null —— 调用方据此决定**要不要加这一项**，
     * 于是公开构建里别人的评论只剩「复制文字」，与引入私有功能之前的行为一致。
     */
    fun replyReportLabel(context: Context): String? = try {
        holder?.getMethod("replyReportLabel", Context::class.java)?.invoke(null, context) as? String
    } catch (e: Throwable) {
        null
    }

    /**
     * 打开举报面板（选理由 → 需要正文时再补正文 → 提交）。
     *
     * 登录判定、理由表、请求体全在私有侧，公共侧只转交这三个参数：
     * [oid] 评论区 oid、[replyType] 评论区类型（不是稿件类型）、[rpid] 目标评论。
     *
     * 入口对所有人可见（[replyReportLabel] 不看登录态），未登录时由私有侧自己提示。
     */
    fun replyReport(context: Context, oid: Long, replyType: Int, rpid: Long) {
        try {
            holder?.getMethod(
                "replyReport",
                Context::class.java,
                java.lang.Long.TYPE,
                Integer.TYPE,
                java.lang.Long.TYPE
            )?.invoke(null, context, oid, replyType, rpid)
        } catch (e: Throwable) {
            Logu.w("OptionalFeatures", "replyReport failed: " + e.message)
        }
    }

    /**
     * 用私有实现填充一条弹幕类消息卡片（[itemJson] 是消息条目的原始 JSON）。
     *
     * 返回 false 表示私有侧不认识这条消息（或不在场），调用方按「无法识别这个类别」处理。
     * [fromLike] 区分「收到的赞」与「回复我的」两条流——两者弹幕原文放的位置不同。
     */
    fun fillOptionalMessage(card: MessageCard, itemJson: String, fromLike: Boolean): Boolean = try {
        holder?.getMethod(
            "fillOptionalMessage",
            MessageCard::class.java,
            String::class.java,
            java.lang.Boolean.TYPE
        )?.invoke(null, card, itemJson, fromLike) as? Boolean ?: false
    } catch (e: Throwable) {
        Logu.w("OptionalFeatures", "fillOptionalMessage failed: " + e.message)
        false
    }

    /**
     * 点击一条弹幕类消息。返回 true 表示私有侧已经处理（跳到了弹幕所在视频）。
     *
     * [itemType] 与 [bvid] 都由公共侧从消息里取好再传，私有侧不用再碰卡片结构。
     */
    fun openOptionalMessage(context: Context, itemType: String?, bvid: String?): Boolean = try {
        holder?.getMethod(
            "openOptionalMessage",
            Context::class.java,
            String::class.java,
            String::class.java
        )?.invoke(null, context, itemType, bvid) as? Boolean ?: false
    } catch (e: Throwable) {
        Logu.w("OptionalFeatures", "openOptionalMessage failed: " + e.message)
        false
    }

    private fun invokeString(name: String): String? = try {
        holder?.getMethod(name)?.invoke(null) as? String
    } catch (e: Throwable) {
        null
    }
}
