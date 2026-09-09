package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.StringUtil

/**
 * 开源信息页。
 *
 * 从「关于」页拆出来的三段长文本：开源协议、借鉴的项目与第三方库、开源图标许可。
 * 关于页原来把它们直接铺在卡片里，一屏全是字；现在单独成页并按卡片分区。
 */
class OpenSourceActivity : BaseActivity() {

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_opensource)

        setPageName("开源信息")

        // 借鉴的项目与第三方库
        val sourceView = findViewById<TextView>(R.id.source_content)
        val sourceText = getString(R.string.about_source)
        sourceView.text = sourceText
        StringUtil.setCopy(sourceView, sourceText)

        // 开源图标许可（逐条编号）
        val iconView = findViewById<TextView>(R.id.icon_license_content)
        val iconItems = resources.getStringArray(R.array.icon_license)
        val iconText = StringBuilder(getString(R.string.desc_icon_license))
        for (i in iconItems.indices) {
            iconText.append('\n').append(i + 1).append('.').append(iconItems[i])
        }
        iconView.text = iconText.toString()
        StringUtil.setCopy(iconView, iconText.toString())

        val scrollView = findViewById<View>(R.id.scrollView)
        scrollView.isFocusable = true
        scrollView.isFocusableInTouchMode = true
        scrollView.requestFocus()
    }
}
