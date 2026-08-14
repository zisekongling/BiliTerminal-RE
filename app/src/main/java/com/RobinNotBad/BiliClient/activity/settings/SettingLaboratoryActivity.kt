package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class SettingLaboratoryActivity : RefreshListActivity() {

    private var adapter: SettingsAdapter? = null
    private var sectionList: List<SettingSection>? = null
    private lateinit var recommendSourceDisplayNames: List<String>
    private lateinit var recommendSourceActualValues: List<String>
    private lateinit var themeDisplayNames: List<String>
    private lateinit var themeActualValues: List<String>

    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName("实验室")

        val debugBuild = BiliTerminal.isDebugBuild()

        recommendSourceDisplayNames = listOf("网页源", "APP源", "混合使用")
        recommendSourceActualValues = listOf(
            SharedPreferencesUtil.RECOMMEND_SOURCE_WEB,
            SharedPreferencesUtil.RECOMMEND_SOURCE_APP,
            SharedPreferencesUtil.RECOMMEND_SOURCE_BOTH
        )

        themeDisplayNames = listOf("B站粉", "知乎蓝", "爱奇艺绿", "紫色空灵", "五彩斑斓", "经典灰")
        themeActualValues = listOf(
            "theme_bilibili_pink",
            "theme_zhihu_blue",
            "theme_iqiyi_green",
            "theme_purple_fantasy",
            "theme_rainbow_fantasy",
            "theme_classic_gray"
        )

        sectionList = ArrayList<SettingSection>().apply {
            add(SettingSection("title", "性能优化", "", "", ""))
            add(SettingSection("switch", "高性能模式", PerformanceManager.KEY_HIGH_PERFORMANCE_MODE,
                "开启后将使用更多系统资源以提升运行速度。高性能手机自动启用；低性能手表建议关闭以获得更好的续航和稳定性。", "false"))

            add(SettingSection("title", "推荐", "", "", ""))
            add(SettingSection("switch", "推荐视频去重", SharedPreferencesUtil.RECOMMEND_DEDUP_ENABLE,
                "过滤掉已经显示过的推荐视频，避免重复", "true"))
            add(SettingSection("list_choose", "推荐源", SharedPreferencesUtil.RECOMMEND_SOURCE,
                "选择使用哪个API获取推荐视频", SharedPreferencesUtil.RECOMMEND_SOURCE_WEB,
                SettingsAdapter.ListChooseHolder.ListChooseExtra(recommendSourceDisplayNames, recommendSourceActualValues)))

            add(SettingSection("title", "可用性", "", "", ""))
            add(SettingSection("switch", "新版弹幕获取方式", "new_danmaku_api",
                getString(R.string.desc_new_danmaku_api), "true"))
            add(SettingSection("switch", "私信未读标记", SharedPreferencesUtil.PRIVATE_MSG_UNREAD_BADGE_ENABLE,
                getString(R.string.desc_private_msg_unread_badge_enable), "false"))

            add(SettingSection("title", "下载", "", "", ""))
            add(SettingSection("switch", "使用旧版下载器", "dev_download_old",
                getString(R.string.setting_lab_download_old), "false"))
            add(SettingSection("switch", "强制高分辨率选项", "force_high_quality_options",
                "强制在缓存分辨率选择中显示4K、1080P高码率、1080P等选项，即使该视频可能不支持这些分辨率", "false"))
            add(SettingSection("input_string", "缓存路径", "save_path_video",
                getString(R.string.setting_lab_path_video), FileUtil.getVideoDownloadPath().toString()))
            add(SettingSection("input_string", "图片下载路径", "save_path_pictures",
                getString(R.string.setting_lab_path_pictures), FileUtil.getPicturePath().toString()))

            add(SettingSection("title", "UI", "", "", ""))
            add(SettingSection("list_choose", "主题配色", "theme_selector",
                "选择应用的主题配色方案", "theme_bilibili_pink",
                SettingsAdapter.ListChooseHolder.ListChooseExtra(themeDisplayNames, themeActualValues)))
            add(SettingSection("switch", "横屏模式", "ui_landscape", getString(R.string.setting_lab_ui_landscape),
                "false", null, "ui_mobile_mode"))
            add(SettingSection("switch", "手机模式", "ui_mobile_mode", getString(R.string.setting_lab_ui_mobile_mode),
                "false", null, "ui_landscape"))
            add(SettingSection("input_string", "开屏文字", "ui_splashtext",
                getString(R.string.setting_lab_splashtext), "欢迎使用\nRE:哔哩终端"))
            add(SettingSection("switch", "文字跑马灯", "marquee_enable", getString(R.string.setting_lab_marquee),
                "true"))

            add(SettingSection("title", "合集", "", "", ""))
            add(SettingSection("switch", "虚拟合集", SharedPreferencesUtil.VIRTUAL_COLLECTION_ENABLE,
                "开启后，在播放本地缓存视频或收藏夹视频时，会将同一文件夹/收藏夹的视频自动组成合集，支持切换和自动联播", "true"))

            add(SettingSection("title", "播放器", "", "", ""))
            add(SettingSection("switch", "播放器旋屏兼容方案", "dev_player_rotate_software",
                "在极少数手表上（如小米手表），系统旋屏存在显示不全的问题。打开此开关，播放器将会使用软件旋屏方法。", "false"))
            add(SettingSection("switch", "显示视频分段", "player_show_viewpoints",
                "显示视频的章节看点信息，可快速跳转到指定章节", "false"))
            add(SettingSection("switch", "系统媒体控件", SharedPreferencesUtil.PLAYER_MEDIA_SESSION_ENABLE,
                getString(R.string.setting_lab_media_session), "false"))
            add(SettingSection("switch", "互动视频调试", "player_interaction_debug",
                "在互动视频播放时，在左侧倍速按钮上方显示调试按钮，可以查看和修改互动视频的变量", "false"))

            add(SettingSection("title", "调试", "", "", ""))
            add(SettingSection("switch", "允许Logu.v", "dev_logv", getString(R.string.setting_lab_logv),
                debugBuild.toString()))
            add(SettingSection("switch", "允许Logu.d", "dev_logd", "", debugBuild.toString()))
            add(SettingSection("switch", "允许Logu.i", "dev_logi", "", debugBuild.toString()))
            add(SettingSection("switch", "详细显示数据解析报错", "dev_jsonerr_detailed",
                getString(R.string.setting_lab_jsonerr_detailed), debugBuild.toString()))
            add(SettingSection("switch", "详细显示列表报错", "dev_recyclererr_detailed",
                getString(R.string.setting_lab_recyclererr_detailed), debugBuild.toString()))
        }

        recyclerView.setHasFixedSize(true)

        adapter = SettingsAdapter(this, sectionList!!)
        adapter?.onSettingChanged = { key, value ->
            if (key == PerformanceManager.KEY_HIGH_PERFORMANCE_MODE) {
                PerformanceManager.setHighPerformanceMode(value)
                // 高性能模式变更时提示重启应用以完全生效
                if (value && PerformanceManager.getCurrentPerfLevel() != PerformanceManager.PERF_LEVEL_HIGH) {
                    com.RobinNotBad.BiliClient.util.MsgUtil.showMsg("高性能模式已开启，部分优化将在下次启动应用时生效")
                } else if (!value) {
                    com.RobinNotBad.BiliClient.util.MsgUtil.showMsg("已切换到性能优先模式")
                }
            }
        }
        setAdapter(adapter!!)

        setRefreshing(false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val selectedItem = data.getStringExtra("item")
            val position = data.getIntExtra("position", -1)
            if (position >= 0 && selectedItem != null) {
                val recommendIndex = recommendSourceDisplayNames.indexOf(selectedItem)
                if (recommendIndex >= 0 && recommendIndex < recommendSourceActualValues.size) {
                    SharedPreferencesUtil.putString(SharedPreferencesUtil.RECOMMEND_SOURCE, recommendSourceActualValues[recommendIndex])
                    adapter?.notifyItemChanged(position)
                }

                val themeIndex = themeDisplayNames.indexOf(selectedItem)
                if (themeIndex >= 0 && themeIndex < themeActualValues.size) {
                    val currentTheme = SharedPreferencesUtil.getString("theme_selector", "theme_bilibili_pink")
                    val newTheme = themeActualValues[themeIndex]
                    if (currentTheme != newTheme) {
                        SharedPreferencesUtil.getSharedPreferences().edit().putString("theme_selector", newTheme).commit()
                        restartApp()
                    }
                }
            }
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}