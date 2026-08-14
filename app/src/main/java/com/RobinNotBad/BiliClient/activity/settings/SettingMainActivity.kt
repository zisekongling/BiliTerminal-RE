package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.activity.settings.login.AccountSwitchActivity
import com.RobinNotBad.BiliClient.activity.settings.login.SpecialLoginActivity
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.card.MaterialCardView

class SettingMainActivity : InstanceActivity() {

    private var eggClick: Int = 0

    @SuppressLint("MissingInflatedId", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_setting_main) { _, _ ->
            Log.e("debug", "进入设置页")

            val loginCookie = findViewById<MaterialCardView>(R.id.login_cookie)
            loginCookie.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, SpecialLoginActivity::class.java)
                intent.putExtra("login", false)
                startActivity(intent)
            }

            val accountSwitch = findViewById<MaterialCardView>(R.id.accountSwitch)
            accountSwitch.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, AccountSwitchActivity::class.java)
                startActivity(intent)
            }

            val login = findViewById<MaterialCardView>(R.id.login)
            if (SharedPreferencesUtil.getLong("mid", 0) == 0L) {
                loginCookie.visibility = View.GONE
                login.visibility = View.VISIBLE
                login.setOnClickListener {
                    val intent = Intent()
                    if (Build.VERSION.SDK_INT >= 19)
                        intent.setClass(this, LoginActivity::class.java)
                    else {
                        intent.setClass(this, SpecialLoginActivity::class.java)
                        intent.putExtra("login", true)
                    }
                    startActivity(intent)
                }
            }

            val playerSetting = findViewById<MaterialCardView>(R.id.playerSetting)
            playerSetting.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, SettingPlayerChooseActivity::class.java)
                startActivity(intent)
            }


            val clientPlayerSetting = findViewById<MaterialCardView>(R.id.terminalPlayerSetting)
            clientPlayerSetting.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, SettingTerminalPlayerActivity::class.java)
                startActivity(intent)
            }

            val uiSetting = findViewById<MaterialCardView>(R.id.uiSetting)
            uiSetting.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, SettingUIActivity::class.java)
                startActivity(intent)
            }

            val menuSetting = findViewById<MaterialCardView>(R.id.menuSetting)
            menuSetting.setOnClickListener { startActivity(Intent(this, SettingMenuActivity::class.java)) }

            val searchSetting = findViewById<MaterialCardView>(R.id.searchSetting)
            searchSetting.setOnClickListener { startActivity(Intent(this, SettingSearchActivity::class.java)) }

            val prefSetting = findViewById<MaterialCardView>(R.id.prefSetting)
            prefSetting.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, SettingPrefActivity::class.java)
                startActivity(intent)
            }

            val repliesSetting = findViewById<MaterialCardView>(R.id.repliesSetting)
            repliesSetting.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, SettingRepliesActivity::class.java)
                startActivity(intent)
            }

            val infoSetting = findViewById<MaterialCardView>(R.id.infoSetting)
            infoSetting.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, SettingInfoActivity::class.java)
                startActivity(intent)
            }

            val laboratorySetting = findViewById<MaterialCardView>(R.id.laboratory)
            laboratorySetting.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, SettingLaboratoryActivity::class.java)
                startActivity(intent)
            }

            val downloadSetting = findViewById<MaterialCardView>(R.id.downloadSetting)
            downloadSetting.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, SettingDownloadActivity::class.java)
                startActivity(intent)
            }

            val checkUpdate = findViewById<MaterialCardView>(R.id.checkUpdate)
            checkUpdate.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, UpdateActivity::class.java)
                startActivity(intent)
            }

            val about = findViewById<MaterialCardView>(R.id.about)
            about.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, AboutActivity::class.java)
                startActivity(intent)
            }

            val eggList = resources.getStringArray(R.array.eggs)
            about.setOnLongClickListener {
                MsgUtil.showText("回声洞", eggList[eggClick])
                if (eggClick < eggList.size - 1) eggClick++
                true
            }

            val announcement = findViewById<MaterialCardView>(R.id.announcement)
            announcement.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, AnnouncementsActivity::class.java)
                startActivity(intent)
            }

            val refreshTutorial = findViewById<MaterialCardView>(R.id.refresh_tutorial)
            refreshTutorial.setOnClickListener {
                val intent = Intent()
                intent.setClass(this, TutorialManagerActivity::class.java)
                startActivity(intent)
            }

            val test = findViewById<MaterialCardView>(R.id.test)
            test.visibility = if (SharedPreferencesUtil.getBoolean("developer", false)) View.VISIBLE else View.GONE
            test.setOnClickListener {
                val intent = Intent(this, TestActivity::class.java)
                startActivity(intent)
            }

            val todoList = findViewById<MaterialCardView>(R.id.todoList)
            todoList.setOnClickListener {
                val intent = Intent(this, TodoListActivity::class.java)
                startActivity(intent)
            }

            findViewById<View>(R.id.scrollView).requestFocus()
        }
    }
}