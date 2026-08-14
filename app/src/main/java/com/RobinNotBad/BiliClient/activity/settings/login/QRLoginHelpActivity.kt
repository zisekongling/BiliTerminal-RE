package com.RobinNotBad.BiliClient.activity.settings.login

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity

class QRLoginHelpActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_qr_login_help) { _, _ ->
            setPageName("登录方式说明")

            val contentText = findViewById<TextView>(R.id.content)
            contentText.text = getHelpContent()

            findViewById<View>(R.id.close_btn).setOnClickListener {
                finish()
            }

            val scrollView = findViewById<View>(R.id.scrollView)
            scrollView.isFocusable = true
            scrollView.isFocusableInTouchMode = true
            scrollView.requestFocus()
        }
    }

    private fun getHelpContent(): String {
        return """
            【WEB端扫码登录】
            • 使用B站官方手机客户端扫描二维码
            • 登录后将获得完整的Cookie凭证（含SESSDATA、bili_jct等）
            • Cookie会自动保存在请求拦截器中，用于后续API调用
            • 适用场景：大多数需要Cookie认证的接口（如评论、收藏、关注等）
            
            【TV端扫码登录】
            • 同样使用B站手机客户端扫描二维码
            • 登录后除Cookie外，还会直接获得access_token
            • access_token是OAuth2.0标准的访问令牌
            • 可用于短视频API等需要token认证的接口
            • 参照云视听小电视（BiliTV）的登录方式实现
            
            【Access Token说明】
            • 存储在本地SharedPreferences中（key: access_key）
            • 获取后会在"我的"页面显示获取状态
            • 在调用需要token认证的API时自动携带
            • TV端登录会自动获取，WEB端登录需要额外授权流程
            
            【两种方式的区别】
            • WEB端登录：Cookie认证，适用于Web接口
            • TV端登录：Cookie + Token双重认证
            • TV端登录比WEB端多一个access_token
            • 推荐使用TV端登录以获得更完整的凭证
        """.trimIndent()
    }
}