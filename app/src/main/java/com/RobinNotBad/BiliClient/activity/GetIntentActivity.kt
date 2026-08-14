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

        if (type != null) when (type) {
            "video_av" -> BiliTerminal.jumpToVideo(this, intent.getLongExtra("content", 0))
            "video_bv" -> BiliTerminal.jumpToVideo(this, intent.getStringExtra("content")!!)
            "article" -> BiliTerminal.jumpToArticle(this, intent.getLongExtra("content", 0))
            "user" -> BiliTerminal.jumpToUser(this, intent.getLongExtra("content", 0))
            else -> MsgUtil.showMsgLong("不支持打开：$type")
        }

        val uri: Uri? = intent.data
        if (uri != null) {
            val host = uri.host
            Log.e("debug-host", host ?: "null")

            when (host) {
                "video" -> BiliTerminal.jumpToVideo(this, uri.lastPathSegment!!.toLong())
                "article" -> BiliTerminal.jumpToArticle(this, uri.lastPathSegment!!.toLong())
                else -> MsgUtil.showMsgLong("不支持打开：$host")
            }
        }

        finish()
    }
}