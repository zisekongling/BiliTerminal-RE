package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.helper.TutorialHelper
import com.RobinNotBad.BiliClient.model.Tutorial
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.button.MaterialButton
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class TutorialActivity : BaseActivity() {
    private var waitTime: Int = 3

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_tutorial) { _, _ ->
            val intent = intent

            val tutorial: Tutorial = TutorialHelper.loadTutorial(
                resources.getXml(intent.getIntExtra("xml_id", R.xml.tutorial_recommend))
            )!!

            (findViewById<TextView>(R.id.text_title)).text = tutorial.name
            (findViewById<TextView>(R.id.content)).text = TutorialHelper.loadText(tutorial.content)

            try {
                if (tutorial.imgid != null) {
                    val indentify = resources.getIdentifier("$packageName:${tutorial.imgid}", null, null)
                    if (indentify > 0)
                        (findViewById<ImageFilterView>(R.id.image_view)).setImageDrawable(resources.getDrawable(indentify))
                } else findViewById<View>(R.id.image_view).visibility = View.GONE
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val closeBtn = findViewById<MaterialButton>(R.id.close_btn)
            closeBtn.isEnabled = false
            val timer = Timer()
            timer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        if (waitTime > 0) {
                            closeBtn.text = String.format(Locale.getDefault(), "已阅(%ds)", waitTime)
                            closeBtn.isEnabled = false
                            waitTime--
                        } else {
                            closeBtn.text = "已阅"
                            closeBtn.isEnabled = true
                            timer.cancel()
                        }
                    }
                }
            }, 0, 1000)
            closeBtn.setOnClickListener {
                SharedPreferencesUtil.putInt("tutorial_ver_" + intent.getStringExtra("tag"), intent.getIntExtra("version", -1))
                finish()
            }

            val scrollView = findViewById<View>(R.id.scrollView)
            scrollView.isFocusable = true
            scrollView.isFocusableInTouchMode = true
            scrollView.requestFocus()
        }
    }

    override fun onBackPressed() {
    }
}