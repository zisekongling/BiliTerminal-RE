package com.RobinNotBad.BiliClient.activity.settings

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.user.MySpaceMenu
import com.RobinNotBad.BiliClient.adapter.MySpaceSettingAdapter
import com.RobinNotBad.BiliClient.util.MySpaceConfig
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 「我的页面设置」：调整「我的」页面功能入口的顺序与分区。
 *
 * 交互与菜单设置一致（双分区 + 长按拖拽），区别是更多列表本身也可以排序。
 * 用户卡片固定在页面顶部不参与配置，「更多」与「退出登录」固定排最后两位，因此都不在这里出现。
 */
class SettingMySpaceActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_menu)

        findViewById<View>(R.id.top).setOnClickListener { finish() }
        setPageName("我的页面设置")

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val adapter = MySpaceSettingAdapter(SharedPreferencesUtil.loadMySpaceLayout()) { key ->
            MySpaceMenu.labelOf(key)
        }
        adapter.listener = object : MySpaceSettingAdapter.Listener {
            override fun onChanged(main: List<String>, more: List<String>) {
                SharedPreferencesUtil.saveMySpaceLayout(MySpaceConfig.Layout(main, more))
            }
        }
        recyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(adapter.dragCallback)
        touchHelper.attachToRecyclerView(recyclerView)
        adapter.touchHelper = touchHelper
    }
}
