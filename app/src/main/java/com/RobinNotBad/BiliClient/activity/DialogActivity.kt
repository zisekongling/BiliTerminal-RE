package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.google.android.material.button.MaterialButton
import java.util.Timer
import java.util.TimerTask

class DialogActivity : BaseActivity() {

    private var waitTime: Int = 0

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialog)

        val closeBtn = findViewById<MaterialButton>(R.id.close_btn)

        val intent = intent

        (findViewById<TextView>(R.id.tip_title)).text = intent.getStringExtra("title")
        (findViewById<TextView>(R.id.content)).text = intent.getStringExtra("content")

        if (intent.getIntExtra("wait_time", -1) > 0) {
            closeBtn.isEnabled = false
            waitTime = intent.getIntExtra("wait_time", 0)
            val timer = Timer()
            timer.scheduleAtFixedRate(object : TimerTask() {
                @SuppressLint("SetTextI18n")
                override fun run() {
                    runOnUiThread {
                        if (waitTime-- > 0) {
                            closeBtn.text = "知道了(${waitTime}s)"
                            closeBtn.isEnabled = false
                        } else {
                            closeBtn.text = "知道了"
                            closeBtn.isEnabled = true
                            timer.cancel()
                        }
                    }
                }
            }, 0, 1000)
        } else closeBtn.isEnabled = true
        closeBtn.setOnClickListener { finish() }
    }

    override fun onBackPressed() {
    }
}