package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.google.android.material.button.MaterialButton
import java.util.Timer
import java.util.TimerTask

class DialogActivity : BaseActivity() {

    private var waitTime: Int = 0
    private var countdownTimer: Timer? = null

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialog)

        val closeBtn = findViewById<MaterialButton>(R.id.close_btn)

        findViewById<TextView>(R.id.tip_title).text = intent.getStringExtra("title")
        findViewById<TextView>(R.id.content).text = intent.getStringExtra("content")

        val waitSeconds = intent.getIntExtra("wait_time", -1)
        if (waitSeconds > 0) {
            waitTime = waitSeconds
            closeBtn.isEnabled = false
            // 立刻渲染首个秒数，计时器延迟 1 秒才首次触发，这样显示的正好是 waitSeconds 秒
            closeBtn.text = "知道了(${waitTime}s)"
            countdownTimer = Timer().apply {
                scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        // 递减放在 UI 线程：与 onDestroy 里的 cancel 处于同一线程，不会竞争
                        runOnUiThread {
                            if (waitTime > 1) {
                                waitTime--
                                closeBtn.text = "知道了(${waitTime}s)"
                            } else {
                                closeBtn.text = "知道了"
                                closeBtn.isEnabled = true
                                cancelCountdown()
                            }
                        }
                    }
                }, 1000, 1000)
            }
        } else closeBtn.isEnabled = true

        closeBtn.setOnClickListener { finish() }
    }

    /** 停止倒计时（幂等）。 */
    private fun cancelCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
    }

    override fun onDestroy() {
        // 弹窗被提前关掉时必须一起停掉计时器：否则 Timer 线程会继续持有本 Activity，
        // 并对已销毁的界面 runOnUiThread
        cancelCountdown()
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        // 通知 MsgUtil 可以弹下一个了（「免责声明 → 夜深了」的串行化靠这个回调）
        MsgUtil.onDialogActivityClosed()
    }

    override fun onBackPressed() {
    }
}
