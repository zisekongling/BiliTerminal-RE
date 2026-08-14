package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.google.android.material.button.MaterialButton

class InputDialogActivity : BaseActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_dialog)

        val intent = intent
        findViewById<TextView>(R.id.tip_title).text = intent.getStringExtra("title")

        val editText = findViewById<EditText>(R.id.editText)
        val textError = findViewById<TextView>(R.id.textError)

        // 设置初始文本和提示
        val initialText = intent.getStringExtra("initial_text")
        val hint = intent.getStringExtra("hint")
        if (!initialText.isNullOrEmpty()) {
            editText.setText(initialText)
            editText.setSelection(initialText.length)
        }
        if (!hint.isNullOrEmpty()) {
            editText.hint = hint
        }

        // 错误文本颜色
        val errorColor = intent.getIntExtra("error_color", -1)
        if (errorColor != -1) {
            try {
                textError.setTextColor(errorColor)
            } catch (_: Exception) {}
        }

        // 监听文本变化隐藏错误
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                textError.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<MaterialButton>(R.id.btn_confirm).setOnClickListener {
            val text = editText.text.toString().trim()
            val result = Intent()
            result.putExtra("input_text", text)
            setResult(RESULT_OK, result)
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