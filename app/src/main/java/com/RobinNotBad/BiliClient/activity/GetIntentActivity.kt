package com.RobinNotBad.BiliClient.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.RobinNotBad.BiliClient.BiliTerminal
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
                else MsgUtil.showMsgLong("链接无效：缺少视频参数")
            }
            "article" -> BiliTerminal.jumpToArticle(this, intent.getLongExtra("content", 0))
            "user" -> BiliTerminal.jumpToUser(this, intent.getLongExtra("content", 0))
            else -> MsgUtil.showMsgLong("不支持打开：$type")
        }

        val uri: Uri? = intent.data
        if (uri != null) {
            val host = uri.host
            Log.e("debug-host", host ?: "null")

            val segment = uri.lastPathSegment
            val id = segment?.toLongOrNull()
            when (host) {
                "video" -> if (id != null) BiliTerminal.jumpToVideo(this, id)
                    else MsgUtil.showMsgLong("不支持打开：$segment")
                "article" -> if (id != null) BiliTerminal.jumpToArticle(this, id)
                    else MsgUtil.showMsgLong("不支持打开：$segment")
                else -> MsgUtil.showMsgLong("不支持打开：$host")
            }
        }

        finish()
    }
}