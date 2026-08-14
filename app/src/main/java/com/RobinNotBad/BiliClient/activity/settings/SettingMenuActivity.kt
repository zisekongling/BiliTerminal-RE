package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingMenuActivity : BaseActivity() {

    private lateinit var menuPopular: SwitchMaterial
    private lateinit var menuShortVideo: SwitchMaterial
    private lateinit var menuLive: SwitchMaterial
    private lateinit var menuPrecious: SwitchMaterial
    private lateinit var menuRanking: SwitchMaterial
    private lateinit var menuTimeline: SwitchMaterial

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_setting_menu) { _, _ ->
            menuPopular = findViewById(R.id.menu_popular)
            menuPopular.isChecked = SharedPreferencesUtil.getBoolean("menu_popular", true)

            menuShortVideo = findViewById(R.id.menu_short_video)
            menuShortVideo.isChecked = SharedPreferencesUtil.getBoolean("menu_short_video", true)

            menuLive = findViewById(R.id.menu_live)
            menuLive.isChecked = SharedPreferencesUtil.getBoolean("menu_live", false)

            menuPrecious = findViewById(R.id.menu_precious)
            menuPrecious.isChecked = SharedPreferencesUtil.getBoolean("menu_precious", false)

            menuRanking = findViewById(R.id.menu_ranking)
            menuRanking.isChecked = SharedPreferencesUtil.getBoolean("menu_ranking", false)

            menuTimeline = findViewById(R.id.menu_timeline)
            menuTimeline.isChecked = SharedPreferencesUtil.getBoolean("menu_timeline", false)

            val sortBtn = findViewById<MaterialButton>(R.id.sort)
            sortBtn.setOnClickListener {
                val intent = Intent(this@SettingMenuActivity, SortSettingActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun save() {
        SharedPreferencesUtil.putBoolean("menu_popular", menuPopular.isChecked)
        SharedPreferencesUtil.putBoolean("menu_short_video", menuShortVideo.isChecked)
        SharedPreferencesUtil.putBoolean("menu_precious", menuPrecious.isChecked)
        SharedPreferencesUtil.putBoolean("menu_ranking", menuRanking.isChecked)
        SharedPreferencesUtil.putBoolean("menu_live", menuLive.isChecked)
        SharedPreferencesUtil.putBoolean("menu_timeline", menuTimeline.isChecked)
    }

    override fun onDestroy() {
        save()
        super.onDestroy()
    }
}