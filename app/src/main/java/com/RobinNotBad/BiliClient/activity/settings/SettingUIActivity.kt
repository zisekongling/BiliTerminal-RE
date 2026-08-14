package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.widget.EditText
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingUIActivity : BaseActivity() {

    private var uiScaleInput: EditText? = null
    private var uiPaddingH: EditText? = null
    private var uiPaddingV: EditText? = null
    private var densityInput: EditText? = null

    @SuppressLint("MissingInflatedId", "SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_setting_ui) { _, _ ->
            uiScaleInput = findViewById(R.id.ui_scale_input)
            uiScaleInput!!.setText(SharedPreferencesUtil.getFloat("dpi", 1.0F).toString())

            uiPaddingH = findViewById(R.id.ui_padding_horizontal)
            uiPaddingH!!.setText(SharedPreferencesUtil.getInt("paddingH_percent", 0).toString())
            uiPaddingV = findViewById(R.id.ui_padding_vertical)
            uiPaddingV!!.setText(SharedPreferencesUtil.getInt("paddingV_percent", 0).toString())

            densityInput = findViewById(R.id.density_input)
            val density = SharedPreferencesUtil.getInt("density", -1)
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            densityInput!!.setText((if (density == -1) "${displayMetrics.densityDpi}(默认)" else density).toString())

            val round = findViewById<SwitchMaterial>(R.id.switch_round)
            round.isChecked = SharedPreferencesUtil.getBoolean("player_ui_round", false)
            round.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    uiPaddingH!!.setText("5")
                    uiPaddingV!!.setText("3")
                    SharedPreferencesUtil.putBoolean("player_ui_round", true)
                    MsgUtil.showMsg("界面边距已更改\n可以手动微调喵")
                } else {
                    uiPaddingH!!.setText("0")
                    uiPaddingV!!.setText("0")
                    SharedPreferencesUtil.putBoolean("player_ui_round", false)
                }
            }

            findViewById<View>(R.id.preview).setOnClickListener {
                save()
                val intent = Intent()
                intent.setClass(this@SettingUIActivity, UIPreviewActivity::class.java)
                startActivity(intent)
            }
            findViewById<View>(R.id.reset).setOnClickListener {
                SharedPreferencesUtil.putInt("paddingH_percent", 0)
                SharedPreferencesUtil.putInt("paddingV_percent", 0)
                SharedPreferencesUtil.putFloat("dpi", 1.0f)
                SharedPreferencesUtil.putInt("density", -1)
                SharedPreferencesUtil.putBoolean("player_ui_round", false)
                uiScaleInput!!.setText("1.0")
                uiPaddingH!!.setText("0")
                uiPaddingV!!.setText("0")
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                densityInput!!.setText("${displayMetrics.densityDpi}(默认)")
                round.isChecked = false
                MsgUtil.showMsg("恢复完成")
            }

            val scrollView = findViewById<View>(R.id.scrollView)
            scrollView.isFocusable = true
            scrollView.isFocusableInTouchMode = true
            scrollView.requestFocus()
        }
    }

    private fun save() {
        if (!uiScaleInput!!.text.toString().isEmpty()) {
            val dpiScale = uiScaleInput!!.text.toString().toFloat()
            if (dpiScale >= 0.25F && dpiScale <= 5.0F) {
                SharedPreferencesUtil.putFloat("dpi", dpiScale)
                BiliTerminal.DPI_FORCE_CHANGE = true
            }
            Logu.i("dpi", uiScaleInput!!.text.toString())
        }

        if (!uiPaddingH!!.text.toString().isEmpty()) {
            val paddingH = uiPaddingH!!.text.toString().toInt()
            if (paddingH <= 30) SharedPreferencesUtil.putInt("paddingH_percent", paddingH)
            Logu.i("paddingH", uiPaddingH!!.text.toString())
        }

        if (!uiPaddingV!!.text.toString().isEmpty()) {
            val paddingV = uiPaddingV!!.text.toString().toInt()
            if (paddingV <= 30) SharedPreferencesUtil.putInt("paddingV_percent", paddingV)
            Logu.i("paddingV", uiPaddingV!!.text.toString())
        }

        if (!densityInput!!.text.toString().isEmpty()) {
            try {
                val density = densityInput!!.text.toString().toInt()
                if (density >= 72) SharedPreferencesUtil.putInt("density", density)
            } catch (ignored: Throwable) {
            }
        }
    }

    override fun onDestroy() {
        if (uiScaleInput != null) save()
        super.onDestroy()
    }
}