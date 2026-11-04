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
    private lateinit var searchLive: SwitchMaterial
    private lateinit var searchBangumi: SwitchMaterial

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_setting_search) { _, _ ->
            // 视频搜索没有开关可言（SearchActivity 里 "video" 恒为 true），这一项只是把
            // 「始终启用」摆给用户看：布局里已 enabled=false + checked=true 锁成开，
            // 这里再兜一次，免得个别 AppCompat 版本忽略 xml 的 android:checked、又显示成关
            findViewById<SwitchMaterial>(R.id.search_video).isChecked = true

            // 读取已保存的设置状态
            searchArticle = findViewById(R.id.search_article)
            searchArticle.isChecked = SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.SEARCH_CATEGORY_ARTICLE_SHOW, true)

            searchUser = findViewById(R.id.search_user)
            searchUser.isChecked = SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.SEARCH_CATEGORY_USER_SHOW, true)

            searchLive = findViewById(R.id.search_live)
            searchLive.isChecked = SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.SEARCH_CATEGORY_LIVE_SHOW, true)

            searchBangumi = findViewById(R.id.search_bangumi)
            searchBangumi.isChecked = SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.SEARCH_CATEGORY_BANGUMI_SHOW, true)

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
        searchLive.isChecked = true
        searchBangumi.isChecked = true

        // 重置排序为默认顺序
        SharedPreferencesUtil.putString(SharedPreferencesUtil.SEARCH_CATEGORY_SORT, "")

        MsgUtil.showMsg(getString(R.string.msg_settingsearchactivity_001))
    }

    /**
     * 持久化保存当前设置
     */
    private fun save() {
        // asyncInflate 是异步的：布局还没膨胀完就退出本页时这些字段尚未初始化，
        // 直接读会抛 UninitializedPropertyAccessException 崩在 onDestroy。
        if (!::searchArticle.isInitialized || !::searchUser.isInitialized
            || !::searchLive.isInitialized || !::searchBangumi.isInitialized) {
            return
        }
        SharedPreferencesUtil.putBoolean(
            SharedPreferencesUtil.SEARCH_CATEGORY_ARTICLE_SHOW, searchArticle.isChecked)
        SharedPreferencesUtil.putBoolean(
            SharedPreferencesUtil.SEARCH_CATEGORY_USER_SHOW, searchUser.isChecked)
        SharedPreferencesUtil.putBoolean(
            SharedPreferencesUtil.SEARCH_CATEGORY_LIVE_SHOW, searchLive.isChecked)
        SharedPreferencesUtil.putBoolean(
            SharedPreferencesUtil.SEARCH_CATEGORY_BANGUMI_SHOW, searchBangumi.isChecked)
    }

    override fun onDestroy() {
        save()
        super.onDestroy()
    }
}