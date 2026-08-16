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
        if (intent.hasExtra("from"))
            intent.putExtra("from", getIntent().getStringExtra("from"))
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
        if (keyCode == KeyEvent.KEYCODE_MENU) menuClick.run()
        return super.onKeyDown(keyCode, event)
    }
}