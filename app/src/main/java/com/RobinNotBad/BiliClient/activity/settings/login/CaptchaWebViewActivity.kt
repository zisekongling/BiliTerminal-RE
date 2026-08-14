package com.RobinNotBad.BiliClient.activity.settings.login

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.MsgUtil

class CaptchaWebViewActivity : BaseActivity() {

    companion object {
        const val EXTRA_GT = "gt"
        const val EXTRA_CHALLENGE = "challenge"
        const val RESULT_CHALLENGE = "geetest_challenge"
        const val RESULT_VALIDATE = "geetest_validate"
        const val RESULT_SECCODE = "geetest_seccode"
        private const val ZOOM_STEP = 25
        private const val ZOOM_MIN = 50
        private const val ZOOM_MAX = 300
        private const val ZOOM_DEFAULT = 100
    }

    private var webView: WebView? = null
    private var mGt: String? = null
    private var mChallenge: String? = null
    private var currentZoom = ZOOM_DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_captcha_webview)
        setPageName("人机验证")

        mGt = intent.getStringExtra(EXTRA_GT)
        mChallenge = intent.getStringExtra(EXTRA_CHALLENGE)

        if (mGt == null || mChallenge == null) {
            MsgUtil.showMsg("验证参数错误")
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        initZoomControls()
        initWebView()
    }

    private fun initZoomControls() {
        val btnZoomIn = findViewById<Button>(R.id.btnZoomIn)
        val btnZoomOut = findViewById<Button>(R.id.btnZoomOut)
        val btnZoomReset = findViewById<Button>(R.id.btnZoomReset)

        btnZoomIn.setOnClickListener { zoomIn() }
        btnZoomOut.setOnClickListener { zoomOut() }
        btnZoomReset.setOnClickListener { resetZoom() }
    }

    private fun zoomIn() {
        if (currentZoom >= ZOOM_MAX) return
        currentZoom += ZOOM_STEP
        applyZoom()
    }

    private fun zoomOut() {
        if (currentZoom <= ZOOM_MIN) return
        currentZoom -= ZOOM_STEP
        applyZoom()
    }

    private fun resetZoom() {
        currentZoom = ZOOM_DEFAULT
        applyZoom()
    }

    private fun applyZoom() {
        webView?.evaluateJavascript(
            "document.querySelector('meta[name=\"viewport\"]').setAttribute('content', 'width=device-width, initial-scale=${currentZoom / 100.0}, maximum-scale=${ZOOM_MAX / 100.0}, user-scalable=yes');",
            null
        )
    }

    @Suppress("SetJavaScriptEnabled")
    private fun initWebView() {
        webView = findViewById(R.id.captchaWebView)

        val settings = webView!!.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Mobile Safari/537.36"

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView!!.addJavascriptInterface(CaptchaJsInterface(), "Android")
        webView!!.webViewClient = CustomWebViewClient()

        val html = buildCaptchaHtml()
        webView!!.loadDataWithBaseURL("https://www.bilibili.com/", html, "text/html", "UTF-8", null)
    }

    private fun buildCaptchaHtml(): String {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta charset=\"utf-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes\">\n" +
                "<style>\n" +
                "  * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "  body { display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #f5f5f5; padding: 16px; }\n" +
                "  #captcha { width: 100%; max-width: 320px; }\n" +
                "  .loading { text-align: center; color: #666; padding: 40px 0; font-size: 14px; }\n" +
                "  .error { text-align: center; color: #e74c3c; padding: 40px 0; font-size: 14px; }\n" +
                "  .retry-btn { margin-top: 12px; padding: 10px 24px; background: #00a1d6; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; }\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div id=\"captcha\"><div class=\"loading\">正在加载验证码...</div></div>\n" +
                "<script>\n" +
                "var gt = '" + mGt + "';\n" +
                "var challenge = '" + mChallenge + "';\n" +
                "var captchaEl = document.getElementById('captcha');\n" +
                "\n" +
                "function loadScript(url, callback) {\n" +
                "  var script = document.createElement('script');\n" +
                "  script.type = 'text/javascript';\n" +
                "  script.src = url;\n" +
                "  script.onload = function() { callback(null); };\n" +
                "  script.onerror = function() { callback(new Error('Load failed')); };\n" +
                "  document.body.appendChild(script);\n" +
                "}\n" +
                "\n" +
                "function initCaptcha() {\n" +
                "  captchaEl.innerHTML = '<div class=\"loading\">正在加载验证码...</div>';\n" +
                "  \n" +
                "  loadScript('https://static.geetest.com/static/js/gt.0.4.9.js', function(err) {\n" +
                "    if (err) {\n" +
                "      captchaEl.innerHTML = '<div class=\"error\">加载核心库失败<br><button class=\"retry-btn\" onclick=\"initCaptcha()\">重试</button></div>';\n" +
                "      return;\n" +
                "    }\n" +
                "    \n" +
                "    loadScript('https://static.geetest.com/static/js/click.3.1.2.js', function(err2) {\n" +
                "      if (err2) {\n" +
                "        captchaEl.innerHTML = '<div class=\"error\">加载验证库失败<br><button class=\"retry-btn\" onclick=\"initCaptcha()\">重试</button></div>';\n" +
                "        return;\n" +
                "      }\n" +
                "      \n" +
                "      if (typeof initGeetest === 'undefined') {\n" +
                "        captchaEl.innerHTML = '<div class=\"error\">初始化失败<br><button class=\"retry-btn\" onclick=\"initCaptcha()\">重试</button></div>';\n" +
                "        return;\n" +
                "      }\n" +
                "      \n" +
                "      initGeetest({\n" +
                "        gt: gt,\n" +
                "        challenge: challenge,\n" +
                "        new_captcha: true,\n" +
                "        product: 'popup',\n" +
                "        offline: false,\n" +
                "        type: 'click',\n" +
                "        https: true\n" +
                "      }, function(captchaObj) {\n" +
                "        captchaEl.innerHTML = '';\n" +
                "        captchaObj.appendTo('#captcha');\n" +
                "        \n" +
                "        captchaObj.onReady(function() {\n" +
                "          console.log('Captcha ready');\n" +
                "        });\n" +
                "        \n" +
                "        captchaObj.onSuccess(function() {\n" +
                "          var result = captchaObj.getValidate();\n" +
                "          if (result && result.geetest_validate) {\n" +
                "            var seccode = result.geetest_seccode || (result.geetest_validate + '|jordan');\n" +
                "            Android.onCaptchaResult(\n" +
                "              result.geetest_challenge || challenge,\n" +
                "              result.geetest_validate,\n" +
                "              seccode\n" +
                "            );\n" +
                "          } else {\n" +
                "            Android.onCaptchaError('验证结果为空');\n" +
                "          }\n" +
                "        });\n" +
                "        \n" +
                "        captchaObj.onError(function(err) {\n" +
                "          Android.onCaptchaError('验证失败: ' + (err && err.msg || '网络错误'));\n" +
                "        });\n" +
                "        \n" +
                "        captchaObj.verify();\n" +
                "      });\n" +
                "    });\n" +
                "  });\n" +
                "}\n" +
                "\n" +
                "initCaptcha();\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>"
    }

    private inner class CustomWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return false
        }
    }

    private inner class CaptchaJsInterface {
        @JavascriptInterface
        fun onCaptchaResult(challenge: String, validate: String, seccode: String) {
            runOnUiThread {
                val data = Intent()
                data.putExtra(RESULT_CHALLENGE, challenge)
                data.putExtra(RESULT_VALIDATE, validate)
                data.putExtra(RESULT_SECCODE, seccode)
                setResult(RESULT_OK, data)
                finish()
            }
        }

        @JavascriptInterface
        fun onCaptchaError(errorMsg: String) {
            runOnUiThread {
                MsgUtil.showMsg(errorMsg)
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    override fun onDestroy() {
        webView?.let {
            it.destroy()
            webView = null
        }
        super.onDestroy()
    }
}