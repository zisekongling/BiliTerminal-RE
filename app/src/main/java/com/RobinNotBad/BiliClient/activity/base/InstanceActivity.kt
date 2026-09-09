package com.RobinNotBad.BiliClient.activity.base

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.MenuActivity

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

    fun setMenuClick() {
        findViewById<android.view.View>(R.id.top).setOnClickListener { menuClick.run() }
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