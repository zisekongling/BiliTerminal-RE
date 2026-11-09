package com.RobinNotBad.BiliClient.activity.base

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.MenuActivity
import com.RobinNotBad.BiliClient.util.StringUtil

open class InstanceActivity : BaseActivity() {

    @JvmField
    var menuClick: Runnable = Runnable {
        val intent = Intent()
        intent.setClass(this, MenuActivity::class.java)
        // 从当前页自己的 Intent 取 from；旧代码判的是刚 new 出来的空 Intent，恒为 false
        val from = getIntent().getStringExtra("from")
        if (from != null)
            intent.putExtra("from", from)
        startActivity(intent)
        overridePendingTransition(R.anim.anim_activity_in_down, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        BiliTerminal.setInstance(this)
        super.onCreate(savedInstanceState)
    }

    /**
     * 装载「点顶栏打开菜单」，并把顶栏图标统一成朝下的菜单箭头。
     *
     * 图标必须和点击行为一致：[BaseActivity] 对 [InstanceActivity] 装的是「打开菜单」，
     * 对其它页面装的才是「返回」。但布局是共享的——`activity_myspace.xml` 同时被
     * `MySpaceActivity`（菜单级）和 `MySpaceMoreActivity`（返回级，`BaseActivity`）复用，
     * 布局里只能写一个箭头。所以角色判定放在这里：菜单级一律覆盖成 `arrow_down`，
     * 返回级那个布局里的 `arrow_back` 保持不动。（26.10.25 用户反馈「我的」页左上箭头方向错误）
     */
    fun setMenuClick() {
        findViewById<android.view.View>(R.id.top).setOnClickListener { menuClick.run() }
        // 图标走 TextViewCompat + AppCompatResources（见 StringUtil.getDrawable）：传资源 id 的
        // setCompoundDrawablesRelativeWithIntrinsicBounds 是原生加载，API<21 遇到矢量图会崩
        findViewById<TextView>(R.id.pageName)?.let {
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                it, StringUtil.getDrawable(it.context, R.drawable.arrow_down), null, null, null
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            menuClick.run()
            // 必须消费掉 MENU 键：否则继续走 super 会命中 BaseActivity.onKeyDown 把当前页 finish 掉
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}