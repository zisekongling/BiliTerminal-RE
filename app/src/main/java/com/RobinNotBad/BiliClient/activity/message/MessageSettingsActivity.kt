package com.RobinNotBad.BiliClient.activity.message

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.TextView

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.message.MessageSettingsAdapter
import com.RobinNotBad.BiliClient.api.MessageApi
import com.RobinNotBad.BiliClient.model.message.MessageSettingItem
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil

import org.json.JSONObject

class MessageSettingsActivity : BaseActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var emptyView: TextView
    private var adapter: MessageSettingsAdapter? = null
    private var settingsList: MutableList<MessageSettingItem> = mutableListOf()
    private var currentSettings: JSONObject? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_refresh)

        setPageName("消息设置")

        emptyView = findViewById(R.id.emptyTip)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.isEnabled = false
        swipeRefreshLayout.isRefreshing = true
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        settingsList = ArrayList()
        loadSettings()
    }

    private fun loadSettings() {
        CenterThreadPool.run {
            try {
                val response = MessageApi.getMsgSettings()
                if (response.getInt("code") == 0) {
                    currentSettings = response.getJSONObject("data")
                    buildSettingsList()
                    runOnUiThread {
                        adapter = MessageSettingsAdapter(this, settingsList) { key, value -> onSettingChanged(key, value) }
                        recyclerView.adapter = adapter
                        swipeRefreshLayout.isRefreshing = false
                    }
                } else {
                    runOnUiThread {
                        MsgUtil.showMsg("获取设置失败: " + response.optString("message", "未知错误"))
                        swipeRefreshLayout.isRefreshing = false
                        emptyView.text = "加载失败，请重试"
                        emptyView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    report(e)
                    swipeRefreshLayout.isRefreshing = false
                    emptyView.text = "加载失败，请重试"
                    emptyView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun buildSettingsList() {
        settingsList.clear()

        if (currentSettings!!.has("msg_notify")) {
            val value = currentSettings!!.optInt("msg_notify", 1)
            settingsList.add(MessageSettingItem(
                "msg_notify",
                "消息提醒",
                "是否接收消息提醒",
                MessageSettingItem.TYPE_CHOOSE,
                value == 1,
                arrayOf("接收", "不接收")))
        }

        if (currentSettings!!.has("show_unfollowed_msg")) {
            val value = currentSettings!!.optInt("show_unfollowed_msg", 0)
            settingsList.add(MessageSettingItem(
                "show_unfollowed_msg",
                "收起未关注人消息",
                "收起来自未关注用户的消息",
                MessageSettingItem.TYPE_SWITCH,
                value == 1,
                null))
        }

        if (currentSettings!!.has("is_group_fold")) {
            val value = currentSettings!!.optInt("is_group_fold", 0)
            settingsList.add(MessageSettingItem(
                "is_group_fold",
                "收起应援团消息",
                "折叠应援团相关消息",
                MessageSettingItem.TYPE_SWITCH,
                value == 1,
                null))
        }

        if (currentSettings!!.has("should_receive_group")) {
            val value = currentSettings!!.optInt("should_receive_group", 1)
            settingsList.add(MessageSettingItem(
                "should_receive_group",
                "接收应援团消息",
                "是否接收应援团消息",
                MessageSettingItem.TYPE_SWITCH,
                value == 1,
                null))
        }

        if (currentSettings!!.has("receive_unfollow_msg")) {
            val value = currentSettings!!.optInt("receive_unfollow_msg", 1)
            settingsList.add(MessageSettingItem(
                "receive_unfollow_msg",
                "接收未关注人消息",
                "是否接收未关注用户的消息",
                MessageSettingItem.TYPE_SWITCH,
                value == 1,
                null))
        }

        if (currentSettings!!.has("ai_intercept")) {
            val value = currentSettings!!.optInt("ai_intercept", 0)
            settingsList.add(MessageSettingItem(
                "ai_intercept",
                "私信智能拦截",
                "使用AI智能过滤骚扰私信",
                MessageSettingItem.TYPE_SWITCH,
                value == 1,
                null))
        }
    }

    private fun onSettingChanged(key: String, value: Boolean) {
        CenterThreadPool.run {
            try {
                val settings = JSONObject()

                if (key == "msg_notify") {
                    settings.put(key, if (value) 1 else 3)
                } else {
                    settings.put(key, if (value) 1 else 0)
                }

                val response = MessageApi.setMsgSettings(settings)
                if (response.getInt("code") == 0) {
                    runOnUiThread { MsgUtil.showMsg("设置已保存") }
                } else {
                    runOnUiThread {
                        val message = response.optString("message", "未知错误")
                        MsgUtil.showMsg("保存失败: $message")
                        loadSettings()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    MsgUtil.showMsg("保存失败")
                    loadSettings()
                }
            }
        }
    }
}