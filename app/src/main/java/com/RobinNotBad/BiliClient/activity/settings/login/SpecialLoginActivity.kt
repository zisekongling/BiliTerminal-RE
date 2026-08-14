package com.RobinNotBad.BiliClient.activity.settings.login

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.SplashActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.AccountManager
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView
import org.json.JSONException
import org.json.JSONObject

class SpecialLoginActivity : BaseActivity() {

    private lateinit var textInput: EditText

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_special)
        Logu.i("debug", "使用特殊登录方式")

        textInput = findViewById(R.id.loginInput)
        val confirm = findViewById<MaterialCardView>(R.id.confirm)
        val refuse = findViewById<MaterialCardView>(R.id.refuse)
        val copy = findViewById<MaterialCardView>(R.id.copy)
        val desc = findViewById<TextView>(R.id.desc)

        val intent = intent

        if (intent.getBooleanExtra("login", true)) {
            refuse.setOnClickListener {
                if (intent.getBooleanExtra("from_setup", false))
                    startActivity(Intent(this, SplashActivity::class.java))
                else finish()
            }

            confirm.setOnClickListener {
                val loginInfo = textInput.text.toString()
                try {
                    val jsonObject = JSONObject(loginInfo)
                    val cookies = jsonObject.getString("cookies")
                    SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, NetWorkUtil.getInfoFromCookie("DedeUserID", cookies).toLong())
                    SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, NetWorkUtil.getInfoFromCookie("bili_jct", cookies))
                    NetWorkUtil.setCookiesString(cookies)
                    SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, jsonObject.getString("refresh_token"))
                    if (jsonObject.has("access_key")) {
                        SharedPreferencesUtil.putString(SharedPreferencesUtil.access_key, jsonObject.getString("access_key"))
                    }
                    runOnUiThread { MsgUtil.showMsg("登录成功！") }
                    SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.setup, true)

                    AccountManager.saveCurrentAccount()

                    val intent1 = Intent()
                    intent1.setClass(this@SpecialLoginActivity, SplashActivity::class.java)
                    startActivity(intent1)
                    finish()
                } catch (e: JSONException) {
                    runOnUiThread { MsgUtil.showMsg("请检查输入的内容，不要有多余空格或字符") }
                }
            }
        } else {
            desc.setText(R.string.special_login_export)

            val jsonObject = JSONObject()
            try {
                jsonObject.put("cookies", SharedPreferencesUtil.getString("cookies", ""))
                jsonObject.put("refresh_token", SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""))
                jsonObject.put("access_key", SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, ""))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            textInput.setText(jsonObject.toString())
            textInput.clearFocus()

            refuse.visibility = View.GONE
            if (BiliTerminal.isDebugBuild()) {
                confirm.setOnClickListener {
                    try {
                        val input = JSONObject(textInput.text.toString())
                        val cookies = input.getString("cookies")
                        NetWorkUtil.setCookiesString(cookies)
                        if (input.has("refresh_token")) {
                            SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, input.getString("refresh_token"))
                        }
                        if (input.has("access_key")) {
                            SharedPreferencesUtil.putString(SharedPreferencesUtil.access_key, input.getString("access_key"))
                        }
                        runOnUiThread { MsgUtil.showMsg("导入成功") }

                        NetWorkUtil.refreshHeaders()
                    } catch (e: JSONException) {
                        runOnUiThread { MsgUtil.showMsg("请检查输入的内容，不要有多余空格或字符") }
                    }
                }
            } else confirm.visibility = View.GONE
            copy.setOnClickListener {
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("label", textInput.text)
                clipboardManager.setPrimaryClip(clipData)
                MsgUtil.showMsg("已复制")
            }
        }
    }

}