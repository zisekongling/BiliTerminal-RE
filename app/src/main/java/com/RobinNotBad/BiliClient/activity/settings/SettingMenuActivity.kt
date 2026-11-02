package com.RobinNotBad.BiliClient.activity.settings

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.MenuActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.MenuSettingAdapter
import com.RobinNotBad.BiliClient.util.MenuConfig
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class SettingMenuActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_menu)

        findViewById<View>(R.id.top).setOnClickListener { finish() }

        val enabled = SharedPreferencesUtil.loadMenuEnabled()
        // 启动页：缺省为「推荐」；已存值若已被移入未启用，会在读取时立即改回默认并写回
        val start = SharedPreferencesUtil.loadMenuStart(enabled)
        val helpHidden = SharedPreferencesUtil.getBoolean(
            SharedPreferencesUtil.MENU_HELP_HIDDEN, false
        )

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = MenuSettingAdapter(enabled, start, helpHidden) { key ->
            when (key) {
                // 「退出」不在 btnNames 里（它是动作项不是页面），名字单独取
                MenuConfig.EXIT -> getString(R.string.btn_exit)
                else -> MenuActivity.btnNames[key]?.first ?: key
            }
        }
        adapter.listener = object : MenuSettingAdapter.Listener {
            override fun onChanged(enabled: List<String>) {
                SharedPreferencesUtil.saveMenuEnabled(enabled)
            }

            override fun onStartChanged(key: String) {
                SharedPreferencesUtil.saveMenuStart(key)
            }

            override fun onHelpHiddenChanged(hidden: Boolean) {
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.MENU_HELP_HIDDEN, hidden)
            }
        }
        recyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(adapter.dragCallback)
        touchHelper.attachToRecyclerView(recyclerView)
        adapter.touchHelper = touchHelper
    }
}
