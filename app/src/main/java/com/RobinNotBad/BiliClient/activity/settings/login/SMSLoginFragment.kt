package com.RobinNotBad.BiliClient.activity.settings.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
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
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class SMSLoginFragment : Fragment() {
    private lateinit var phoneInput: EditText
    private lateinit var smsCodeInput: EditText
    private lateinit var sendSmsText: TextView
    private lateinit var statusText: TextView
    private var from_setup: Boolean = false
    private var countDownTimer: CountDownTimer? = null

    private var captchaToken: String? = null
    private var captchaChallenge: String? = null
    private var geetestChallenge: String? = null
    private var geetestValidate: String? = null
    private var geetestSeccode: String? = null
    private var captchaKey: String? = null
    private var captchaReady: Boolean = false

    private lateinit var captchaLauncher: ActivityResultLauncher<Intent>

    companion object {
        fun newInstance(from_setup: Boolean): SMSLoginFragment {
            val args = Bundle()
            args.putBoolean("from_setup", from_setup)
            val fragment = SMSLoginFragment()
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
                geetestChallenge = result.data!!.getStringExtra(CaptchaWebViewActivity.RESULT_CHALLENGE)
                geetestValidate = result.data!!.getStringExtra(CaptchaWebViewActivity.RESULT_VALIDATE)
                geetestSeccode = result.data!!.getStringExtra(CaptchaWebViewActivity.RESULT_SECCODE)
                captchaReady = true
                doSendSms()
            } else {
                setStatusText("验证已取消")
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_sms_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        phoneInput = view.findViewById(R.id.phoneInput)
        smsCodeInput = view.findViewById(R.id.smsCodeInput)
        sendSmsText = view.findViewById(R.id.sendSmsText)
        statusText = view.findViewById(R.id.statusText)
        val sendSmsBtn = view.findViewById<MaterialCardView>(R.id.sendSmsBtn)
        val confirmBtn = view.findViewById<MaterialCardView>(R.id.confirmBtn)
        val switchToPwdBtn = view.findViewById<MaterialCardView>(R.id.switchToPwdBtn)

        sendSmsBtn.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            if (phone.isEmpty() || phone.length < 11) {
                setStatusText("请输入正确的手机号")
                return@setOnClickListener
            }
            getCaptchaAndVerify()
        }

        confirmBtn.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            val code = smsCodeInput.text.toString().trim()
            if (phone.isEmpty() || code.isEmpty()) {
                setStatusText("请输入手机号和验证码")
                return@setOnClickListener
            }
            if (!captchaReady) {
                setStatusText("请先完成人机验证")
                return@setOnClickListener
            }
            doSmsLogin(phone, code)
        }

        switchToPwdBtn.setOnClickListener {
            if (activity != null) {
                val viewPager = activity!!.findViewById<ViewPager>(R.id.viewPager)
                viewPager?.currentItem = 1
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
    private fun getCaptchaAndVerify() {
        setStatusText("正在获取验证码...")
        captchaReady = false
        CenterThreadPool.run {
            try {
                // checkCookies 失败不应阻塞登录，内部捕获异常
                try {
                    CookiesApi.checkCookies()
                } catch (e: IOException) {
                    android.util.Log.e("SMSLogin", "checkCookies网络请求失败: ${e.message}")
                } catch (e: JSONException) {
                    android.util.Log.e("SMSLogin", "checkCookies数据解析失败: ${e.message}")
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
                val gt = geetest.getString("gt")
                this.captchaChallenge = geetest.getString("challenge")

                CenterThreadPool.runOnUiThread {
                    setStatusText("")
                    val intent = Intent(requireContext(), CaptchaWebViewActivity::class.java)
                    intent.putExtra(CaptchaWebViewActivity.EXTRA_GT, gt)
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
    private fun doSendSms() {
        val seccode = if (geetestSeccode != null && geetestSeccode!!.isNotEmpty() && geetestSeccode!!.contains("|jordan"))
            geetestSeccode!!
        else
            geetestValidate + "|jordan"

        setStatusText("正在发送验证码...")
        val phone = phoneInput.text.toString().trim()

        CenterThreadPool.run {
            try {
                val result = LoginApi.smsSend(phone, captchaToken, captchaChallenge, geetestValidate, seccode)
                val code = result.optInt("code", -1)
                if (code == 0) {
                    val data = result.optJSONObject("data")
                    if (data != null) {
                        captchaKey = data.optString("captcha_key", "")
                    }
                    CenterThreadPool.runOnUiThread {
                        setStatusText("验证码已发送，请查收短信")
                        startCountDown()
                    }
                } else if (code == -105) {
                    setStatusText("验证码错误，请重试")
                    captchaReady = false
                } else {
                    val message = result.optString("message", "发送失败")
                    setStatusText("发送失败: $message")
                    captchaReady = false
                }
            } catch (e: IOException) {
                setStatusText("网络错误，请重试")
                captchaReady = false
            } catch (e: JSONException) {
                setStatusText("接口异常")
                captchaReady = false
            }
        }
    }

    private fun startCountDown() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (isAdded) {
                    CenterThreadPool.runOnUiThread {
                        sendSmsText.text = (millisUntilFinished / 1000).toString() + "s后重发"
                        sendSmsText.isEnabled = false
                    }
                }
            }

            override fun onFinish() {
                if (isAdded) {
                    CenterThreadPool.runOnUiThread {
                        sendSmsText.text = "获取验证码"
                        sendSmsText.isEnabled = true
                    }
                }
            }
        }
        countDownTimer!!.start()
    }

    @SuppressLint("SetTextI18n")
    private fun doSmsLogin(phone: String, code: String) {
        setStatusText("正在登录...")
        CenterThreadPool.run {
            try {
                val result = LoginApi.smsLogin(phone, code, captchaKey)
                val respCode = result.optInt("code", -1)

                if (respCode == 0) {
                    handleLoginSuccess(result)
                } else if (respCode == 1006) {
                    setStatusText("请输入正确的短信验证码")
                } else if (respCode == 1007) {
                    setStatusText("短信验证码已过期")
                } else {
                    val message = result.optString("message", "登录失败")
                    setStatusText("登录失败: $message")
                }
            } catch (e: IOException) {
                setStatusText("网络错误，请重试")
            } catch (e: JSONException) {
                setStatusText("接口异常")
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

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
}