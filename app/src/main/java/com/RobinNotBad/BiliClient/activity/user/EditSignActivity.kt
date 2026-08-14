package com.RobinNotBad.BiliClient.activity.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView

import org.json.JSONException

import java.io.IOException

class EditSignActivity : BaseActivity() {

    private lateinit var editText: EditText
    private lateinit var charCount: TextView
    private lateinit var submit: MaterialCardView
    private var isSubmitting = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_sign)

        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
            MsgUtil.showMsg("还没有登录喵~")
            finish()
            return
        }

        val intent = intent
        var currentSign = intent.getStringExtra("currentSign")
        if (currentSign == null) {
            currentSign = ""
        }

        editText = findViewById(R.id.editText)
        charCount = findViewById(R.id.charCount)
        submit = findViewById(R.id.submit)

        editText.setText(currentSign)
        editText.filters = arrayOf(InputFilter.LengthFilter(70))
        editText.setSelection(editText.text.length)

        updateCharCount(editText.text.toString().length)

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                updateCharCount(s.length)
            }
        })

        submit.setOnClickListener {
            if (isSubmitting) {
                MsgUtil.showMsg("正在提交中...")
                return@setOnClickListener
            }

            if (!SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.cookie_refresh, true)) {
                MsgUtil.showDialog("无法提交", "上一次的Cookie刷新失败了，\n您可能需要重新登录以进行敏感操作", -1)
                return@setOnClickListener
            }

            val newSign = editText.text.toString()
            isSubmitting = true
            submit.isEnabled = false

            CenterThreadPool.run {
                try {
                    val result = UserInfoApi.updateUserSign(newSign)
                    val code = result.getInt("code")
                    val message = result.optString("message", "")

                    if (!this.isDestroyed) {
                        runOnUiThread {
                            isSubmitting = false
                            submit.isEnabled = true

                            if (code == 0) {
                                MsgUtil.showMsg("修改成功，等待审核")
                                setResult(RESULT_OK)
                                finish()
                            } else {
                                var errorMsg = "修改失败"
                                if (code == -101) {
                                    errorMsg = "账号未登录"
                                } else if (code == -111) {
                                    errorMsg = "CSRF校验失败"
                                } else if (code == 40015) {
                                    errorMsg = "签名包含敏感词"
                                } else if (code == 40021) {
                                    errorMsg = "签名不能包含表情图片"
                                } else if (code == 40022) {
                                    errorMsg = "签名过长"
                                } else if (message.isNotEmpty()) {
                                    errorMsg = message
                                }
                                MsgUtil.showMsg(errorMsg)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is IOException || e is JSONException) {
                        if (!this.isDestroyed) {
                            runOnUiThread {
                                isSubmitting = false
                                submit.isEnabled = true
                                MsgUtil.err("修改个人描述失败", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateCharCount(count: Int) {
        charCount.text = "$count/70"
    }
}