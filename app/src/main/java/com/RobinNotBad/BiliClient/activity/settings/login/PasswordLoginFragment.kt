package com.RobinNotBad.BiliClient.activity.settings.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.SplashActivity
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.api.CookiesApi
import com.RobinNotBad.BiliClient.api.LoginApi
import com.RobinNotBad.BiliClient.util.AccountManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.PasswordEncryptUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class PasswordLoginFragment : Fragment() {
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView
    private var from_setup: Boolean = false
    private var captchaToken: String? = null
    private var captchaGt: String? = null
    private var captchaChallenge: String? = null

    private lateinit var captchaLauncher: ActivityResultLauncher<Intent>

    companion object {
        fun newInstance(from_setup: Boolean): PasswordLoginFragment {
            val args = Bundle()
            args.putBoolean("from_setup", from_setup)
            val fragment = PasswordLoginFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bundle = arguments
        if (bundle != null) from_setup = bundle.getBoolean("from_setup", false)

        captchaLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val geetestChallenge = result.data!!.getStringExtra(CaptchaWebViewActivity.RESULT_CHALLENGE)
                val validate = result.data!!.getStringExtra(CaptchaWebViewActivity.RESULT_VALIDATE)
                val seccode = result.data!!.getStringExtra(CaptchaWebViewActivity.RESULT_SECCODE)
                doPasswordLogin(validate!!, seccode!!)
            } else {
                setStatusText("验证已取消")
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_password_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        usernameInput = view.findViewById(R.id.usernameInput)
        passwordInput = view.findViewById(R.id.passwordInput)
        statusText = view.findViewById(R.id.statusText)
        val confirmBtn = view.findViewById<MaterialCardView>(R.id.confirmBtn)
        val switchToSmsBtn = view.findViewById<MaterialCardView>(R.id.switchToSmsBtn)

        confirmBtn.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (username.isEmpty() || password.isEmpty()) {
                setStatusText("请输入账号和密码")
                return@setOnClickListener
            }
            startCaptcha()
        }

        switchToSmsBtn.setOnClickListener {
            if (activity != null) {
                val viewPager = activity!!.findViewById<ViewPager>(R.id.viewPager)
                viewPager?.currentItem = 2
            }
        }
    }

    private fun setStatusText(text: String) {
        if (!isAdded) return
        CenterThreadPool.runOnUiThread {
            statusText.visibility = View.VISIBLE
            statusText.text = text
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startCaptcha() {
        setStatusText("正在获取验证码...")
        CenterThreadPool.run {
            try {
                // checkCookies 失败不应阻塞登录，内部捕获异常
                try {
                    CookiesApi.checkCookies()
                } catch (e: IOException) {
                    android.util.Log.e("PasswordLogin", "checkCookies网络请求失败: ${e.message}")
                } catch (e: JSONException) {
                    android.util.Log.e("PasswordLogin", "checkCookies数据解析失败: ${e.message}")
                }
                val captchaResp = LoginApi.getCaptcha()
                if (captchaResp.optInt("code", -1) != 0) {
                    val msg = captchaResp.optString("message", "未知错误")
                    setStatusText("验证码接口异常: $msg")
                    return@run
                }
                val data = captchaResp.getJSONObject("data")
                captchaToken = data.getString("token")
                val geetest = data.getJSONObject("geetest")
                captchaGt = geetest.getString("gt")
                captchaChallenge = geetest.getString("challenge")

                CenterThreadPool.runOnUiThread {
                    setStatusText("")
                    val intent = Intent(requireContext(), CaptchaWebViewActivity::class.java)
                    intent.putExtra(CaptchaWebViewActivity.EXTRA_GT, captchaGt)
                    intent.putExtra(CaptchaWebViewActivity.EXTRA_CHALLENGE, captchaChallenge)
                    captchaLauncher.launch(intent)
                }
            } catch (e: IOException) {
                setStatusText("网络错误，请重试")
            } catch (e: JSONException) {
                setStatusText("验证码接口异常")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun doPasswordLogin(geetestValidate: String, geetestSeccode: String) {
        val seccode = if (geetestSeccode.isNotEmpty() && geetestSeccode.contains("|jordan"))
            geetestSeccode
        else
            geetestValidate + "|jordan"

        setStatusText("正在获取密钥...")
        CenterThreadPool.run {
            try {
                val keyResp = LoginApi.getWebKey()
                val keyData = keyResp.getJSONObject("data")
                val hash = keyData.getString("hash")
                val pubKey = keyData.getString("key")

                val username = usernameInput.text.toString().trim()
                val password = passwordInput.text.toString()

                setStatusText("正在加密密码...")
                val encryptedPassword = PasswordEncryptUtil.encryptPassword(password, hash, pubKey)

                setStatusText("正在登录...")
                val loginResp = LoginApi.passwordLogin(username, encryptedPassword, captchaToken, captchaChallenge, geetestValidate, seccode)
                val code = loginResp.optInt("code", -1)

                if (code == 0) {
                    val loginData = loginResp.optJSONObject("data")
                    if (loginData != null && loginData.optInt("status") == 2) {
                        handleRiskVerification(loginData)
                    } else {
                        handleLoginSuccess(loginResp)
                    }
                } else if (code == -629) {
                    setStatusText("账号或密码错误")
                } else if (code == -662) {
                    setStatusText("提交超时，请重试")
                } else if (code == -105) {
                    setStatusText("验证码错误，请重试")
                } else {
                    val message = loginResp.optString("message", "未知错误")
                    setStatusText("登录失败: $message")
                }
            } catch (e: IOException) {
                setStatusText("网络错误，请重试")
            } catch (e: Exception) {
                setStatusText("登录失败: " + e.message)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleRiskVerification(loginData: JSONObject) {
        val message = loginData.optString("message", "")
        if (message.contains("手机号")) {
            CenterThreadPool.runOnUiThread {
                setStatusText("需要手机号验证，请使用短信验证码登录")
            }
        } else {
            CenterThreadPool.runOnUiThread {
                setStatusText(if (message.isEmpty()) "登录环境存在风险" else message)
            }
        }
    }

    private fun handleLoginSuccess(loginJson: JSONObject) {
        CenterThreadPool.runOnUiThread {
            try {
                val cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")

                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, NetWorkUtil.getInfoFromCookie("DedeUserID", cookies).toLong())
                SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, NetWorkUtil.getInfoFromCookie("bili_jct", cookies))

                val loginData = loginJson.optJSONObject("data")
                val tokenInfo = if (loginData != null) loginData.optJSONObject("token_info") else null
                var refreshToken = if (tokenInfo != null) tokenInfo.optString("refresh_token", "") else ""
                if (refreshToken.isEmpty() && loginData != null) {
                    refreshToken = loginData.optString("refresh_token", "")
                }
                SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, refreshToken)
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.cookie_refresh, true)
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.setup, true)

                AccountManager.saveCurrentAccount()
                NetWorkUtil.refreshHeaders()

                try {
                    LoginApi.requestSSOs()
                } catch (ignored: Exception) {
                }

                val instance: InstanceActivity? = BiliTerminal.getInstanceActivityOnTop()
                if (instance != null && !instance.isDestroyed) instance.finish()

                startActivity(Intent(requireContext(), SplashActivity::class.java))
                if (isAdded) requireActivity().finish()
            } catch (e: Exception) {
                setStatusText("登录成功但处理异常: " + e.message)
            }
        }
    }
}