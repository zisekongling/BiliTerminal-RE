package com.RobinNotBad.BiliClient.activity.settings.setup

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.activity.settings.login.SpecialLoginActivity
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView

class IntroductionActivity : BaseActivity() {

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_introduction)

        val confirm = findViewById<MaterialCardView>(R.id.confirm)

        confirm.setOnClickListener {
            SharedPreferencesUtil.putBoolean("setup", true)

            val intent = Intent()
            intent.putExtra("from_setup", true)
            if (Build.VERSION.SDK_INT >= 19) {
                intent.setClass(this@IntroductionActivity, LoginActivity::class.java)
            } else {
                intent.setClass(this@IntroductionActivity, SpecialLoginActivity::class.java)
            }
            startActivity(intent)
            finish()
        }
    }

}