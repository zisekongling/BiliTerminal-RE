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
import com.RobinNotBad.BiliClient.util.PasswordEncryptUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class PasswordLoginFragment : BaseFragment() {
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
            } else if (result.resultCode == CaptchaWebViewActivity.RESULT_UNAVAILABLE) {
                // 设备建不出 WebView，人机验证根本没打开。报「验证已取消」会让用户以为是自己点错了，
                // 这里直接指一条走得通的路（扫码登录不需要 WebView）。
                setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_001))
            } else {
                setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_002))
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
                setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_003))
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
        setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_004))
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
                    val msg = captchaResp.optString("message", getAppContext().getString(R.string.msg_unknown_error))
                    setStatusText("验证码接口异常: $msg")
                    return@run
                }
                val data = captchaResp.getJSONObject("data")
                captchaToken = data.getString("token")
                val geetest = data.getJSONObject("geetest")
                captchaGt = geetest.getString("gt")
                captchaChallenge = geetest.getString("challenge")

                CenterThreadPool.runOnUiThread {
                    // 判活必须在 Runnable 内部：投递到真正执行之间用户可能已经离开登录页
                    if (!isAdded) return@runOnUiThread
                    setStatusText("")
                    val intent = Intent(requireContext(), CaptchaWebViewActivity::class.java)
                    intent.putExtra(CaptchaWebViewActivity.EXTRA_GT, captchaGt)
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
    private fun doPasswordLogin(geetestValidate: String, geetestSeccode: String) {
        val seccode = if (geetestSeccode.isNotEmpty() && geetestSeccode.contains("|jordan"))
            geetestSeccode
        else
            geetestValidate + "|jordan"

        setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_006))
        CenterThreadPool.run {
            try {
                val keyResp = LoginApi.getWebKey()
                val keyData = keyResp.getJSONObject("data")
                val hash = keyData.getString("hash")
                val pubKey = keyData.getString("key")

                val username = usernameInput.text.toString().trim()
                val password = passwordInput.text.toString()

                setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_007))
                val encryptedPassword = PasswordEncryptUtil.encryptPassword(password, hash, pubKey)

                setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_008))
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
                    setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_009))
                } else if (code == -662) {
                    setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_010))
                } else if (code == -105) {
                    setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_011))
                } else {
                    val message = loginResp.optString("message", getAppContext().getString(R.string.msg_unknown_error))
                    setStatusText("登录失败: $message")
                }
            } catch (e: IOException) {
                setStatusText(getAppContext().getString(R.string.msg_network_error_retry))
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
                setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_012))
            }
        } else {
            CenterThreadPool.runOnUiThread {
                setStatusText(if (message.isEmpty()) "登录环境存在风险" else message)
            }
        }
    }

    private fun handleLoginSuccess(loginJson: JSONObject) {
        // 审计 S8：登录成功后的收尾里夹着同步阻塞网络请求（LoginApi.requestSSOs 直接走 OkHttp
        // execute()），原来整段包在 runOnUiThread 里，等于在 UI 线程上做网络 + 磁盘 IO，
        // 弱网下界面卡死甚至 ANR。现在把 IO 放工作线程，只有真正触碰 UI 的部分
        // （startActivity / finish）回到主线程；setStatusText 内部已自带 runOnUiThread。
        CenterThreadPool.run {
            try {
                val cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")

                val loginData = loginJson.optJSONObject("data")
                val tokenInfo = if (loginData != null) loginData.optJSONObject("token_info") else null
                var refreshToken = if (tokenInfo != null) tokenInfo.optString("refresh_token", "") else ""
                if (refreshToken.isEmpty() && loginData != null) {
                    refreshToken = loginData.optString("refresh_token", "")
                }

                // 账号密码登录走的是 App 接口，响应 data/token_info 里本来就带 access_token（App 令牌）。
                // 以前只取了 refresh_token 就丢掉它，导致「没走网页登录就一直没有 access_key」，
                // 依赖 App 令牌的接口只能再让用户去网页登录一次。
                var accessKey = if (tokenInfo != null) tokenInfo.optString("access_token", "") else ""
                if (accessKey.isEmpty() && loginData != null) {
                    accessKey = loginData.optString("access_token", "")
                }

                // mid 从 Cookie 里读：原来直接 .toLong()，Cookie 缺 DedeUserID 时抛
                // NumberFormatException，异常被外层 catch 掉，用户看到的是「登录成功但处理异常」
                // 而登录态其实只写了一半。这里取不到就按失败处理，不写任何东西。
                val mid = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies).toLongOrNull()
                if (mid == null || mid <= 0L) {
                    setStatusText(getAppContext().getString(R.string.msg_login_status_passwordloginfragment_013))
                    return@run
                }

                // 统一入口：整套凭证一次性替换（缺失字段显式写空 + 升账号代际）
                AccountManager.applyCredentials(mid, "", cookies, refreshToken, accessKey, "")
                AccountManager.saveCurrentAccount()

                // SSO 上报失败不影响登录结果，但不能再用空 catch 静默吞掉（异常连日志都没有）
                try {
                    LoginApi.requestSSOs()
                } catch (e: Exception) {
                    Logu.e("PasswordLogin", "上报 SSO 失败：${e.message}")
                }

                CenterThreadPool.runOnUiThread {
                    val instance: InstanceActivity? = BiliTerminal.getInstanceActivityOnTop()
                    if (instance != null && !instance.isDestroyed) instance.finish()

                    if (isAdded) {
                        startActivity(Intent(requireContext(), SplashActivity::class.java))
                        requireActivity().finish()
                    }
                }
            } catch (e: Exception) {
                setStatusText("登录成功但处理异常: " + e.message)
            }
        }
    }
}