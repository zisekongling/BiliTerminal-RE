package com.RobinNotBad.BiliClient.adapter.dynamic

import com.RobinNotBad.BiliClient.util.StringUtil

/**
 * 动态卡片操作栏「评论」入口的文案。
 *
 * 抽成纯对象（不 import android）便于 JVM 单测：由 release 日志复查（Revival 1.2.3
 * 「动态新增评论入口和评论数」）补的入口，语义与对方 `DynamicHolder` 对齐——
 * 没有评论时显示标签本身（用户的「评论」），有评论时显示数字。
 *
 * 数字沿用 [StringUtil.toWan] 的 万/亿 分档，与同一行的点赞数**同一套口径**
 * （同一条动态的「赞 1.2万」与「评论 3456」不会出现两种格式）。
 */
object DynamicCommentText {

    /**
     * @param replyCount 服务端 `module_stat.comment.count`；缺失/异常时传 0
     * @param emptyLabel 没有评论时显示的标签（调用方从 `strings.xml` 取，本对象不碰资源）
     */
    fun build(replyCount: Long, emptyLabel: String): String =
        if (replyCount <= 0L) emptyLabel else StringUtil.toWan(replyCount)
}
