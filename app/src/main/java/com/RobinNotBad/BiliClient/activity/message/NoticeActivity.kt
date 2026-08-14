package com.RobinNotBad.BiliClient.activity.message

import android.content.Intent
import android.os.Bundle
import android.util.Pair

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.message.NoticeAdapter
import com.RobinNotBad.BiliClient.api.MessageApi
import com.RobinNotBad.BiliClient.model.MessageCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool

class NoticeActivity : RefreshListActivity() {
    private var messageList: MutableList<MessageCard> = mutableListOf()
    private var noticeAdapter: NoticeAdapter? = null
    private var cursor: MessageCard.Cursor? = null
    private var pageType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPageName("详情")

        val intent = intent
        pageType = intent.getStringExtra("type")
        messageList = ArrayList()

        CenterThreadPool.run {
            try {
                var pair: Pair<MessageCard.Cursor, List<MessageCard>>?
                when (pageType) {
                    "like" -> {
                        pair = MessageApi.getLikeMsg(0, 0)
                        cursor = pair!!.first
                        messageList = pair.second as MutableList<MessageCard>
                    }
                    "reply" -> {
                        pair = MessageApi.getReplyMsg(0, 0)
                        cursor = pair!!.first
                        messageList = pair.second as MutableList<MessageCard>
                    }
                    "at" -> {
                        pair = MessageApi.getAtMsg(0, 0)
                        cursor = pair!!.first
                        messageList = pair.second as MutableList<MessageCard>
                    }
                    "system" -> {
                        messageList = MessageApi.getSystemMsg() as MutableList<MessageCard>
                    }
                }

                noticeAdapter = NoticeAdapter(this, messageList)
                runOnUiThread {
                    setAdapter(noticeAdapter!!)
                    setRefreshing(false)
                    setOnLoadMoreListener { i -> continueLoading(i) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun continueLoading(i: Int) {
        CenterThreadPool.run {
            try {
                val lastSize = messageList.size
                var pair: Pair<MessageCard.Cursor, List<MessageCard>>?
                when (pageType) {
                    "like" -> {
                        pair = MessageApi.getLikeMsg(cursor!!.id, cursor!!.time)
                        cursor = pair!!.first
                        messageList.addAll(pair!!.second)
                    }
                    "reply" -> {
                        pair = MessageApi.getReplyMsg(cursor!!.id, cursor!!.time)
                        cursor = pair!!.first
                        messageList.addAll(pair!!.second)
                    }
                    "at" -> {
                        pair = MessageApi.getAtMsg(cursor!!.id, cursor!!.time)
                        cursor = pair!!.first
                        messageList.addAll(pair!!.second)
                    }
                    "system" -> {
                        messageList = MessageApi.getSystemMsg() as MutableList<MessageCard>
                    }
                }
                runOnUiThread { noticeAdapter!!.notifyItemRangeInserted(lastSize, messageList.size - lastSize) }
                bottom = cursor!!.is_end
                setRefreshing(false)
            } catch (e: Exception) {
                e.printStackTrace()
                page--
                setRefreshing(false)
            }
        }
    }
}