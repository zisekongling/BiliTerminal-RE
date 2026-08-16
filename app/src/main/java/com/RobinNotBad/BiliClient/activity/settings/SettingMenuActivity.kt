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
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class SettingMenuActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_menu)

        findViewById<View>(R.id.top).setOnClickListener { finish() }

        val enabled = SharedPreferencesUtil.loadMenuEnabled()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = MenuSettingAdapter(enabled) { key ->
            MenuActivity.btnNames[key]?.first ?: key
        }
        adapter.listener = object : MenuSettingAdapter.Listener {
            override fun onChanged(enabled: List<String>) {
                SharedPreferencesUtil.saveMenuEnabled(enabled)
            }
        }
        recyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(adapter.dragCallback)
        touchHelper.attachToRecyclerView(recyclerView)
        adapter.touchHelper = touchHelper
    }
}
