package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity

class UIPreviewActivity : BaseActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_ui_preview)
    }
}