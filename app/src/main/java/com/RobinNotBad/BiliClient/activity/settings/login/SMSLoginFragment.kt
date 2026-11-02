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
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.SplashActivity
import com.RobinNotBad.BiliClient.activity.base.BaseFragment
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.api.CookiesApi
import com.RobinNotBad.BiliClient.api.LoginApi
import com.RobinNotBad.BiliClient.util.AccountManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class SMSLoginFragment : BaseFragment() {
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
            } else if (result.resultCode == CaptchaWebViewActivity.RESULT_UNAVAILABLE) {
                // 同 PasswordLoginFragment：把「设备不支持」和「用户取消」分开报，并给出可走的路
                setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_001))
            } else {
                setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_002))
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
                setStatusText(getAppContext().getString(R.string.msg_login_status_smsloginfragment_001))
                return@setOnClickListener
            }
            getCaptchaAndVerify()
        }

        confirmBtn.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            val code = smsCodeInput.text.toString().trim()
            if (phone.isEmpty() || code.isEmpty()) {
                setStatusText(getAppContext().getString(R.string.msg_login_status_smsloginfragment_002))
                return@setOnClickListener
            }
            if (!captchaReady) {
                setStatusText(getAppContext().getString(R.string.msg_login_status_smsloginfragment_003))
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
        setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_004))
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
                    val msg = captchaResp.optString("message", getAppContext().getString(R.string.msg_unknown_error))
                    setStatusText(getAppContext().getString(R.string.err_sms_api, msg))
                    return@run
                }
                val data = captchaResp.getJSONObject("data")
                captchaToken = data.getString("token")
                val geetest = data.getJSONObject("geetest")
                val gt = geetest.getString("gt")
                this.captchaChallenge = geetest.getString("challenge")

                CenterThreadPool.runOnUiThread {
                    // 判活必须在 Runnable 内部：投递到真正执行之间用户可能已经离开登录页
                    if (!isAdded) return@runOnUiThread
                    setStatusText("")
                    val intent = Intent(requireContext(), CaptchaWebViewActivity::class.java)
                    intent.putExtra(CaptchaWebViewActivity.EXTRA_GT, gt)
                    intent.putExtra(CaptchaWebViewActivity.EXTRA_CHALLENGE, captchaChallenge)
                    captchaLauncher.launch(intent)
                }
            } catch (e: IOException) {
                setStatusText(getAppContext().getString(R.string.msg_network_error_retry))
            } catch (e: JSONException) {
                setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_005))
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun doSendSms() {
        val seccode = if (geetestSeccode != null && geetestSeccode!!.isNotEmpty() && geetestSeccode!!.contains("|jordan"))
            geetestSeccode!!
        else
            geetestValidate + "|jordan"

        setStatusText(getAppContext().getString(R.string.msg_login_status_smsloginfragment_004))
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
                        setStatusText(getAppContext().getString(R.string.msg_login_status_smsloginfragment_005))
                        startCountDown()
                    }
                } else if (code == -105) {
                    setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_011))
                    captchaReady = false
                } else {
                    val message = result.optString("message", getAppContext().getString(R.string.text_sms_send_failed_default))
                    setStatusText(getAppContext().getString(R.string.err_sms_send_failed, message))
                    captchaReady = false
                }
            } catch (e: IOException) {
                setStatusText(getAppContext().getString(R.string.msg_network_error_retry))
                captchaReady = false
            } catch (e: JSONException) {
                setStatusText(getAppContext().getString(R.string.msg_login_status_smsloginfragment_006))
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
                        sendSmsText.text = getAppContext().getString(R.string.text_sms_resend, millisUntilFinished / 1000)
                        sendSmsText.isEnabled = false
                    }
                }
            }

            override fun onFinish() {
                if (isAdded) {
                    CenterThreadPool.runOnUiThread {
                        sendSmsText.text = getAppContext().getString(R.string.text_sms_get_code)
                        sendSmsText.isEnabled = true
                    }
                }
            }
        }
        countDownTimer!!.start()
    }

    @SuppressLint("SetTextI18n")
    private fun doSmsLogin(phone: String, code: String) {
        setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_008))
        CenterThreadPool.run {
            try {
                val result = LoginApi.smsLogin(phone, code, captchaKey)
                val respCode = result.optInt("code", -1)

                if (respCode == 0) {
                    handleLoginSuccess(result)
                } else if (respCode == 1006) {
                    setStatusText(getAppContext().getString(R.string.msg_login_status_smsloginfragment_007))
                } else if (respCode == 1007) {
                    setStatusText(getAppContext().getString(R.string.msg_login_status_smsloginfragment_008))
                } else {
                    val message = result.optString("message", getAppContext().getString(R.string.text_sms_login_failed_default))
                    setStatusText(getAppContext().getString(R.string.err_sms_login_failed, message))
                }
            } catch (e: IOException) {
                setStatusText(getAppContext().getString(R.string.msg_network_error_retry))
            } catch (e: JSONException) {
                setStatusText(getAppContext().getString(R.string.msg_login_status_smsloginfragment_006))
            }
        }
    }

    private fun handleLoginSuccess(loginJson: JSONObject) {
        // 原实现把整段收尾（含 requestSSOs 网络请求）放在主线程 Runnable 里，既卡 UI，又让
        // requireContext() 暴露在「Runnable 真正执行时 fragment 已 detach」的崩溃路径上（#62 同族）。
        // 现在落盘/网络放后台线程，只有 startActivity/finish 回主线程，并在 Runnable 内部判活。
        CenterThreadPool.run {
            try {
                val cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")

                val loginData = loginJson.optJSONObject("data")
                val tokenInfo = if (loginData != null) loginData.optJSONObject("token_info") else null
                var refreshToken = if (tokenInfo != null) tokenInfo.optString("refresh_token", "") else ""
                if (refreshToken.isEmpty() && loginData != null) {
                    refreshToken = loginData.optString("refresh_token", "")
                }

                // 短信登录同样走 App 接口，响应里带 access_token；以前只存 refresh_token，
                // 把 App 令牌丢了（详见 PasswordLoginFragment 同处注释）。
                var accessKey = if (tokenInfo != null) tokenInfo.optString("access_token", "") else ""
                if (accessKey.isEmpty() && loginData != null) {
                    accessKey = loginData.optString("access_token", "")
                }

                // mid 从 Cookie 读，取不到就按失败处理：原来直接 .toLong() 抛异常后
                // 会被外层 catch 成「登录成功但处理异常」，而凭证已经写了一半
                val mid = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies).toLongOrNull()
                if (mid == null || mid <= 0L) {
                    setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_013))
                    return@run
                }

                // 统一入口：整套凭证一次性替换（缺失字段显式写空 + 升账号代际）
                AccountManager.applyCredentials(mid, "", cookies, refreshToken, accessKey, "")
                AccountManager.saveCurrentAccount()

                try {
                    LoginApi.requestSSOs()
                } catch (e: Exception) {
                    Logu.e("SMSLogin", "上报 SSO 失败：${e.message}")
                }

                CenterThreadPool.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    val instance: InstanceActivity? = BiliTerminal.getInstanceActivityOnTop()
                    if (instance != null && !instance.isDestroyed) instance.finish()

                    startActivity(Intent(requireContext(), SplashActivity::class.java))
                    requireActivity().finish()
                }
            } catch (e: Exception) {
                setStatusText(getAppContext().getString(R.string.err_sms_login_handle, e.message))
            }
        }
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
}