package com.RobinNotBad.BiliClient.activity.message

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.adapter.message.PrivateMsgSessionsAdapter
import com.RobinNotBad.BiliClient.api.MessageApi
import com.RobinNotBad.BiliClient.api.PrivateMsgApi
import com.RobinNotBad.BiliClient.helper.TutorialHelper
import com.RobinNotBad.BiliClient.model.PrivateMsgSession
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView

import org.json.JSONObject
import java.util.Collections

class MessageActivity : InstanceActivity() {
    private lateinit var sessionsView: RecyclerView

    @SuppressLint("SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_message) { _, _ ->
            val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
            swipeRefreshLayout.isEnabled = false
            swipeRefreshLayout.isRefreshing = true

            val settingBtn = findViewById<MaterialCardView>(R.id.setting_btn)
            settingBtn.setOnClickListener {
                val intent = Intent(this, MessageSettingsActivity::class.java)
                startActivity(intent)
            }

            val reply = findViewById<MaterialCardView>(R.id.reply)
            reply.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, NoticeActivity::class.java)
                intent.putExtra("type", "reply")
                startActivity(intent)
                (findViewById<TextView>(R.id.reply_text)).text = "回复我的"
            }

            val like = findViewById<MaterialCardView>(R.id.like)
            like.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, NoticeActivity::class.java)
                intent.putExtra("type", "like")
                startActivity(intent)
                (findViewById<TextView>(R.id.like_text)).text = "收到的赞"
            }

            val at = findViewById<MaterialCardView>(R.id.at)
            at.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, NoticeActivity::class.java)
                intent.putExtra("type", "at")
                startActivity(intent)
                (findViewById<TextView>(R.id.at_text)).text = "@我"
            }

            val system = findViewById<MaterialCardView>(R.id.system)
            system.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, NoticeActivity::class.java)
                intent.putExtra("type", "system")
                startActivity(intent)
            }

            sessionsView = findViewById(R.id.sessions_list)
            sessionsView.isNestedScrollingEnabled = false

            CenterThreadPool.run {
                try {
                    val stats = MessageApi.getUnread()
                    val sessionsList = PrivateMsgApi.getSessionsList(20)
                    Collections.sort(sessionsList) { o1, o2 ->
                        val o1Unread = o1.unread > 0
                        val o2Unread = o2.unread > 0
                        if (o1Unread && !o2Unread) {
                            -1
                        } else if (!o1Unread && o2Unread) {
                            1
                        } else {
                            0
                        }
                    }
                    val uidList = ArrayList<Long>()
                    for (item in sessionsList) {
                        uidList.add(item.talkerUid)
                    }
                    val userMap = PrivateMsgApi.getUsersInfo(uidList)
                    val adapter = PrivateMsgSessionsAdapter(this, sessionsList, userMap)
                    runOnUiThread {
                        swipeRefreshLayout.isRefreshing = false
                        try {
                            (findViewById<TextView>(R.id.reply_text)).text = "回复我的" +
                                    (if ((stats.getInt("reply") > 0)) ("(" + stats.getInt("reply") + "未读)") else "")
                            (findViewById<TextView>(R.id.like_text)).text =
                                "收到的赞" + (if ((stats.getInt("like") > 0)) ("(" + stats.getInt("like") + "未读)") else "")
                            (findViewById<TextView>(R.id.at_text)).text =
                                "@我" + (if ((stats.getInt("at") > 0)) ("(" + stats.getInt("at") + "未读)") else "")
                            sessionsView.layoutManager = CustomLinearManager(this)
                            sessionsView.adapter = adapter
                            SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0)
                        } catch (e: Exception) {
                            MsgUtil.err(e)
                        }

                        val scrollView = findViewById<View>(R.id.scrollView)
                        scrollView.isFocusable = true
                        scrollView.isFocusableInTouchMode = true
                        scrollView.requestFocus()
                    }
                } catch (e: Exception) {
                    runOnUiThread { MsgUtil.err(e) }
                }
            }

            TutorialHelper.showTutorialList(this, R.array.tutorial_message, 5)
        }
    }
}