package com.RobinNotBad.BiliClient.activity

import com.RobinNotBad.BiliClient.util.UiText

import android.app.Activity
import android.os.Bundle
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.util.DeepLinkPolicy
import com.RobinNotBad.BiliClient.util.LinkUrlUtil
import com.RobinNotBad.BiliClient.util.MsgUtil

class GetIntentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val type = intent.getStringExtra("type")

        // 本页面 exported=true 且注册了 scheme，外部可传任意 Intent，所有解析必须防崩溃
        if (type != null) when (type) {
            "video_av" -> BiliTerminal.jumpToVideo(this, intent.getLongExtra("content", 0))
            "video_bv" -> {
                val bvid = intent.getStringExtra("content")
                if (bvid != null) BiliTerminal.jumpToVideo(this, bvid)
                else MsgUtil.showMsgLong(UiText.of("链接无效：缺少视频参数"))
            }
            "article" -> BiliTerminal.jumpToArticle(this, intent.getLongExtra("content", 0))
            "user" -> BiliTerminal.jumpToUser(this, intent.getLongExtra("content", 0))
            else -> MsgUtil.showMsgLong("不支持打开：$type")
        }

        val uri = intent.data
        if (uri != null) {
            // 审计 P2-7：scheme/host 都由调用方决定，不可信。先过 DeepLinkPolicy 白名单
            // （http/https 只认 bilibili.com 及其子域、b23.tv；bilibili:// 自定义 scheme 放行），
            // 不通过就提示后直接结束——不能再像以前那样把任意 host 当路径段分派。
            when (DeepLinkPolicy.decisionOf(uri.scheme, uri.host)) {
                DeepLinkPolicy.Decision.REJECTED -> MsgUtil.showMsgLong(UiText.of("不支持打开该链接"))

                DeepLinkPolicy.Decision.CUSTOM_SCHEME -> {
                    // 自定义 scheme 下 host 是路径里的第一段：bilibili://video/123 → "video"
                    val segment = uri.lastPathSegment
                    val id = segment?.toLongOrNull()
                    when (uri.host) {
                        "video" -> if (id != null) BiliTerminal.jumpToVideo(this, id)
                            else MsgUtil.showMsgLong("不支持打开：$segment")
                        "article" -> if (id != null) BiliTerminal.jumpToArticle(this, id)
                            else MsgUtil.showMsgLong("不支持打开：$segment")
                        else -> MsgUtil.showMsgLong("不支持打开：${uri.host}")
                    }
                }

                // 网页链接（/video/BV…、/read/cv…、space.bilibili.com/UID、b23.tv 短链）的解析器
                // util/LinkUrlUtil.handleWebURL 早就有了，消息列表、评论、搜索框都在用，只有这个入口
                // 当时（审计 P2-7）只做到白名单就停在门口，于是用户点分享链接只能跳浏览器（反馈 #322）。
                // 这里补上解析；**必须关掉浏览器兜底**——本页面自己就注册了 ACTION_VIEW，
                // 兜底那次 startActivity 会把我们自己的 intent-filter 再命中一遍，等于什么都没发生。
                DeepLinkPolicy.Decision.WEB_LINK -> {
                    val handled = LinkUrlUtil.handleWebURL(this, uri.toString(), false)
                    if (!handled) MsgUtil.showMsgLong(UiText.of("暂时无法识别这个链接"))
                }
            }
        }

        finish()
    }
}
