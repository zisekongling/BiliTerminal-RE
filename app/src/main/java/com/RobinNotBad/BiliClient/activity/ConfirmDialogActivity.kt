package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.google.android.material.button.MaterialButton

class ConfirmDialogActivity : BaseActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_dialog)

        val intent = intent
        findViewById<TextView>(R.id.tip_title).text = intent.getStringExtra("title")
        findViewById<TextView>(R.id.content).text = intent.getStringExtra("content")

        findViewById<MaterialButton>(R.id.btn_confirm).setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
        findViewById<MaterialButton>(R.id.btn_cancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        findViewById<TextView>(R.id.tip_title).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }
}