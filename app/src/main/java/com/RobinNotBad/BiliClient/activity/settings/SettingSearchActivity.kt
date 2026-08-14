package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * 搜索设置页面
 * 功能：控制搜索类别的显示/隐藏，调整搜索类别排序，重置为默认设置
 */
class SettingSearchActivity : BaseActivity() {

    private lateinit var searchArticle: SwitchMaterial
    private lateinit var searchUser: SwitchMaterial
    private lateinit var searchAudio: SwitchMaterial
    private lateinit var searchLive: SwitchMaterial

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_setting_search) { _, _ ->
            // 读取已保存的设置状态
            searchArticle = findViewById(R.id.search_article)
            searchArticle.isChecked = SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.SEARCH_CATEGORY_ARTICLE_SHOW, true)

            searchUser = findViewById(R.id.search_user)
            searchUser.isChecked = SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.SEARCH_CATEGORY_USER_SHOW, true)

            searchAudio = findViewById(R.id.search_audio)
            searchAudio.isChecked = SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.SEARCH_CATEGORY_AUDIO_SHOW, true)

            searchLive = findViewById(R.id.search_live)
            searchLive.isChecked = SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.SEARCH_CATEGORY_LIVE_SHOW, true)

            // 调整排序按钮
            val sortBtn = findViewById<MaterialButton>(R.id.sort)
            sortBtn.setOnClickListener {
                val intent = Intent(this@SettingSearchActivity, SearchSortActivity::class.java)
                startActivity(intent)
            }

            // 重置为默认设置按钮
            val resetBtn = findViewById<MaterialButton>(R.id.reset)
            resetBtn.setOnClickListener {
                resetToDefault()
            }
        }
    }

    /**
     * 重置所有搜索设置为默认值
     */
    private fun resetToDefault() {
        // 视频始终启用，无需重置
        searchArticle.isChecked = true
        searchUser.isChecked = true
        searchAudio.isChecked = true
        searchLive.isChecked = true

        // 重置排序为默认顺序
        SharedPreferencesUtil.putString(SharedPreferencesUtil.SEARCH_CATEGORY_SORT, "")

        MsgUtil.showMsg("已重置为默认设置")
    }

    /**
     * 持久化保存当前设置
     */
    private fun save() {
        SharedPreferencesUtil.putBoolean(
            SharedPreferencesUtil.SEARCH_CATEGORY_ARTICLE_SHOW, searchArticle.isChecked)
        SharedPreferencesUtil.putBoolean(
            SharedPreferencesUtil.SEARCH_CATEGORY_USER_SHOW, searchUser.isChecked)
        SharedPreferencesUtil.putBoolean(
            SharedPreferencesUtil.SEARCH_CATEGORY_AUDIO_SHOW, searchAudio.isChecked)
        SharedPreferencesUtil.putBoolean(
            SharedPreferencesUtil.SEARCH_CATEGORY_LIVE_SHOW, searchLive.isChecked)
    }

    override fun onDestroy() {
        save()
        super.onDestroy()
    }
}