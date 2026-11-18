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
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.UiScaleInput
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
                MsgUtil.showMsg(getString(R.string.msg_settinggroupactivity_003))
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
            // 「重置」必须连绝对 DPI 一起清（反馈 #225）：密度现在只有一个决策点
            // （UiScaleInput.resolveDensityDpi），绝对 DPI 在那里是**优先于**倍数的。
            // 只清倍数的结果是：点了重置、框里也显示 1.0，界面却仍按老 DPI 走，
            // 用户只会得到「重置没用」的结论。
            SharedPreferencesUtil.putInt(SettingsKeys.DENSITY, -1)
            SharedPreferencesUtil.putBoolean("player_ui_round", false)
            uiScaleInput.setText("1.0")
            uiPaddingH.setText("0")
            uiPaddingV.setText("0")
            round.isChecked = false
            MsgUtil.showMsg(getString(R.string.msg_settinggroupactivity_002))
        }
    }

    /**
     * 写盘。这里只管「界面大小」倍数与两个边距，**不碰**绝对 DPI（`SettingsKeys.DENSITY`）。
     *
     * 注意两者的关系：密度由 [com.RobinNotBad.BiliClient.util.UiScaleInput.resolveDensityDpi]
     * 统一裁决，绝对 DPI 一旦设置就优先于这里的倍数。也就是说在「设置 → 界面适配」里把倍数
     * 从 1.0 改成 1.2，对「设置Density」已经设过绝对 DPI 的用户**不会生效**——那是既有的
     * 优先级设计，本轮没有改（改成「倍数优先」会让老用户界面突然变大小，属于产品取舍）。
     */
    private fun save() {
        // 三个框都可能装着非数字内容（粘贴 / IME 直接提交都能绕过 inputType），
        // 解析与范围校验统一走 UiScaleInput：非法就跳过写盘，页面照常关。
        // 旧代码直接 toFloat()/toInt()，线上因此崩过两次（"界面大小" / "确认设置"）。
        UiScaleInput.parseDpi(uiScaleInput.text)?.let { dpiTimes ->
            SharedPreferencesUtil.putFloat("dpi", dpiTimes)
            Logu.i("dpi", dpiTimes.toString())
        }

        UiScaleInput.parsePadding(uiPaddingH.text)?.let { paddingH ->
            SharedPreferencesUtil.putInt("paddingH_percent", paddingH)
            Logu.i("paddingH", paddingH.toString())
        }

        UiScaleInput.parsePadding(uiPaddingV.text)?.let { paddingV ->
            SharedPreferencesUtil.putInt("paddingV_percent", paddingV)
            Logu.i("paddingV", paddingV.toString())
        }
    }
}