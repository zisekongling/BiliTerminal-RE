package com.RobinNotBad.BiliClient.activity.settings.setup

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.settings.UIPreviewActivity
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.switchmaterial.SwitchMaterial

class SetupUIActivity : BaseActivity() {

    private lateinit var uiScaleInput: EditText
    private lateinit var uiPaddingH: EditText
    private lateinit var uiPaddingV: EditText

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_ui)

        uiScaleInput = findViewById(R.id.ui_scale_input)
        uiScaleInput.setText(SharedPreferencesUtil.getFloat("dpi", 1.0F).toString())

        uiPaddingH = findViewById(R.id.ui_padding_horizontal)
        uiPaddingH.setText(SharedPreferencesUtil.getInt("paddingH_percent", 0).toString())
        uiPaddingV = findViewById(R.id.ui_padding_vertical)
        uiPaddingV.setText(SharedPreferencesUtil.getInt("paddingV_percent", 0).toString())

        val round = findViewById<SwitchMaterial>(R.id.switch_round)
        round.isChecked = SharedPreferencesUtil.getBoolean("player_ui_round", false)
        round.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                uiPaddingH.setText("5")
                uiPaddingV.setText("3")
                SharedPreferencesUtil.putBoolean("player_ui_round", true)
                MsgUtil.showMsg("界面边距已更改\n可以手动微调喵")
            } else {
                uiPaddingH.setText("0")
                uiPaddingV.setText("0")
                SharedPreferencesUtil.putBoolean("player_ui_round", false)
            }
        }

        findViewById<android.view.View>(R.id.preview).setOnClickListener {
            save()
            val intent = Intent()
            intent.setClass(this@SetupUIActivity, UIPreviewActivity::class.java)
            startActivity(intent)
        }

        findViewById<android.view.View>(R.id.confirm).setOnClickListener {
            save()
            val intent = Intent()
            intent.setClass(this@SetupUIActivity, IntroductionActivity::class.java)
            startActivity(intent)
            finish()
        }

        findViewById<android.view.View>(R.id.reset).setOnClickListener {
            SharedPreferencesUtil.putInt("paddingH_percent", 0)
            SharedPreferencesUtil.putInt("paddingV_percent", 0)
            SharedPreferencesUtil.putFloat("dpi", 1.0f)
            SharedPreferencesUtil.putBoolean("player_ui_round", false)
            uiScaleInput.setText("1.0")
            uiPaddingH.setText("0")
            uiPaddingV.setText("0")
            round.isChecked = false
            MsgUtil.showMsg("恢复完成")
        }
    }

    private fun save() {
        if (uiScaleInput.text.toString().isNotEmpty()) {
            val dpiTimes = uiScaleInput.text.toString().toFloat()
            if (dpiTimes >= 0.1F && dpiTimes <= 10.0F)
                SharedPreferencesUtil.putFloat("dpi", dpiTimes)
            Logu.i("dpi", uiScaleInput.text.toString())
        }

        if (uiPaddingH.text.toString().isNotEmpty()) {
            val paddingH = uiPaddingH.text.toString().toInt()
            if (paddingH <= 30) SharedPreferencesUtil.putInt("paddingH_percent", paddingH)
            Logu.i("paddingH", uiPaddingH.text.toString())
        }

        if (uiPaddingV.text.toString().isNotEmpty()) {
            val paddingV = uiPaddingV.text.toString().toInt()
            if (paddingV <= 30) SharedPreferencesUtil.putInt("paddingV_percent", paddingV)
            Logu.i("paddingV", uiPaddingV.text.toString())
        }
    }
}