package com.RobinNotBad.BiliClient.activity.user

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 「我的」页面的更多列表：承载被移入「更多」的功能入口。
 *
 * 复用 `activity_myspace.xml`（隐藏用户卡片），入口顺序与显示与否由 `MySpaceConfig` 决定，
 * 与主列表共用 [MySpaceMenu] 的定义，避免跳转逻辑重复。
 *
 * 这是二级页，继承 `BaseActivity`（顶栏点击 = 返回），不是一级页。
 */
class MySpaceMoreActivity : BaseActivity() {

    private lateinit var menuContainer: LinearLayout

    @SuppressLint("SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_myspace) { _, _ ->
            setPageName("更多")
            // 更多页没有用户卡片
            findViewById<View>(R.id.myinfo).visibility = View.GONE
            menuContainer = findViewById(R.id.menuContainer)

            for (key in SharedPreferencesUtil.loadMySpaceLayout().more) addMenuCell(key)

            val scrollView = findViewById<View>(R.id.scrollView)
            scrollView.isFocusable = true
            scrollView.isFocusableInTouchMode = true
            scrollView.requestFocus()
        }
    }

    private fun addMenuCell(key: String) {
        // 创作中心受通用偏好开关控制，关闭时整行不渲染（设置页里仍可排序）
        if (key == "creative" && !SharedPreferencesUtil.getBoolean(SettingsKeys.CREATIVE_ENABLE, true)) return

        val item = MySpaceMenu.itemOf(key) ?: return
        val cell = layoutInflater.inflate(R.layout.cell_myspace_item, menuContainer, false)
        cell.findViewById<ImageView>(R.id.item_icon).setImageResource(item.iconRes)
        cell.findViewById<TextView>(R.id.item_label).text = item.label
        cell.setOnClickListener {
            MySpaceMenu.open(this, key, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
        }
        menuContainer.addView(cell)
    }
}
