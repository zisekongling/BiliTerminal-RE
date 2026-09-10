package com.RobinNotBad.BiliClient.activity.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.os.Bundle
import android.view.View
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.activity.settings.login.AccountSwitchActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.activity.settings.login.SpecialLoginActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.ui.appearance.AppearanceManager
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.ui.appearance.CornerStyle
import com.RobinNotBad.BiliClient.ui.appearance.FontStyle
import com.RobinNotBad.BiliClient.util.Aria2Util
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/** 「外观设置」分组的 group_type 取值。`SettingsIndex` 里跳转该分组时也用这个常量。 */
const val GROUP_APPEARANCE = "appearance"

/**
 * 分组设置页：统一使用声明式 SettingSection 列表 + SettingsAdapter 渲染。
 * 各分组的设置项全部以数据驱动，与详情页/评论区/偏好等设置页共用同一套渲染体系。
 */
class SettingGroupActivity : RefreshListActivity() {

    private var eggClick: Int = 0
    private var adapter: SettingsAdapter? = null
    private val sections = ArrayList<SettingSection>()

    /**
     * 自定义字体：从文件管理器挑一个字体文件。
     *
     * 用 `ACTION_GET_CONTENT` 而不是 `ACTION_OPEN_DOCUMENT`：手表上各家文件管理器对 SAF
     * 文档提供方的支持差异较大，`GET_CONTENT` 兼容性更好；而且我们选完立刻把文件拷进私有目录，
     * **不需要**持久化 URI 权限。MIME 用通配（不限定 `font` 前缀）——很多文件管理器不认字体 MIME，
     * 限定后会让用户看不到任何文件；真正的合法性由 [FontStyle.rejectReason] 按文件头判定。
     */
    private val fontPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@registerForActivityResult
        // 拷贝 + 校验放后台：字体文件可能几 MB，手表上不能占主线程
        CenterThreadPool.run {
            val error = FontStyle.install(this, uri)
            runOnUiThread {
                if (error != null) {
                    MsgUtil.showMsg("字体未应用：$error")
                } else {
                    MsgUtil.showMsg("自定义字体已应用")
                    rebuild(GROUP_APPEARANCE)
                    recreate()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val groupType = intent.getStringExtra("group_type")
        val groupTitle = intent.getStringExtra("group_title") ?: ""
        if (groupType == null) {
            finish()
            return
        }
        setPageName(groupTitle)

        buildContent(groupType)

        recyclerView.setHasFixedSize(true)
        adapter = SettingsAdapter(this, sections)
        setAdapter(adapter!!)
        setRefreshing(false)

        // 从全局搜索跳转进来时，滚动定位到目标设置项
        val highlight = intent.getStringExtra("highlight")
        if (!highlight.isNullOrEmpty()) {
            val index = sections.indexOfFirst { it.name == highlight }
            if (index >= 0) {
                recyclerView.post { recyclerView.scrollToPosition(index) }
            }
        }
    }

    private fun buildContent(groupType: String) {
        when (groupType) {
            "account" -> buildAccountGroup()
            "ui" -> buildUIGroup()
            GROUP_APPEARANCE -> buildAppearanceGroup()
            "content" -> buildContentGroup()
            "download" -> buildDownloadGroup()
            "lab" -> buildLabGroup()
            "about" -> buildAboutGroup()
            "dev" -> buildDevGroup()
        }
    }

    private fun refresh() {
        adapter?.notifyDataSetChanged()
    }

    /** 清空并重建当前分组（用于开关联动显示/隐藏其它设置项）。 */
    private fun rebuild(groupType: String) {
        sections.clear()
        buildContent(groupType)
        refresh()
    }

    // ==================== 单元格构造（薄封装，写入 SettingSection 列表） ====================

    private fun nav(
        iconRes: Int,
        title: String,
        desc: String,
        onLongClick: ((View) -> Boolean)? = null,
        onClick: (View) -> Unit
    ) {
        sections.add(SettingSection("nav", title, "", desc, "", SettingsAdapter.NavExtra(iconRes, onClick, onLongClick)))
    }

    private fun title(text: String) {
        sections.add(SettingSection("title", text, "", "", ""))
    }

    private fun switch(name: String, desc: String?, key: String, default: Boolean, onChange: ((Boolean) -> Unit)? = null) {
        val extra = if (onChange != null) SettingsAdapter.SwitchExtra(onChange) else null
        sections.add(SettingSection("switch", name, key, desc ?: "", default.toString(), extra))
    }

    private fun input(name: String, desc: String?, key: String, type: String, default: String, save: ((String) -> Unit)? = null) {
        val extra = if (save != null) SettingsAdapter.InputExtra(save) else null
        sections.add(SettingSection(type, name, key, desc ?: "", default, extra))
    }

    private fun listChoose(
        name: String,
        desc: String?,
        key: String,
        default: String,
        displayNames: List<String>,
        actualValues: List<String>,
        onSelect: ((String, String) -> Unit)? = null
    ) {
        sections.add(
            SettingSection(
                "list_choose", name, key, desc ?: "", default,
                SettingsAdapter.ListChooseHolder.ListChooseExtra(displayNames, actualValues, onSelect)
            )
        )
    }

    private fun button(text: String, onClick: (View) -> Unit) {
        sections.add(SettingSection("button", text, "", "", "", SettingsAdapter.ButtonExtra(onClick)))
    }

    // ==================== 各分组构建方法 ====================

    private fun buildAccountGroup() {
        val mid = SharedPreferencesUtil.getLong(SettingsKeys.MID, 0)
        if (mid == 0L) {
            nav(R.drawable.icon_person, "登录", "使用哔哩哔哩账号登录") {
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
        nav(R.drawable.icon_followings, "账号切换", "管理已保存的账号，快速切换登录") {
            startActivity(Intent(this, AccountSwitchActivity::class.java))
        }
        if (mid != 0L) {
            nav(R.drawable.icon_uid, "查看登录信息", "查看当前账号的登录信息，用于在另外的哔哩终端中特殊登录") {
                val intent = Intent()
                intent.setClass(this, SpecialLoginActivity::class.java)
                intent.putExtra("login", false)
                startActivity(intent)
            }
        }
        nav(R.drawable.icon_info, "登录凭证状态", "查看 Cookie、Token 等登录凭证状态") {
            showCredentialsStatus()
        }
    }

    /** 以对话框展示当前登录凭证状态（调试信息，原在个人页底部，已迁移至此）。 */
    private fun showCredentialsStatus() {
        val cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")
        val accessToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "")
        val mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
        val csrf = SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "")
        val refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "")

        val sb = StringBuilder()
        sb.append(if (cookies.isNotEmpty()) "Cookie：已获取" + if (cookies.contains("SESSDATA=")) "（含SESSDATA）" else "" else "Cookie：未获取").append("\n")
        sb.append(if (accessToken.isNotEmpty()) "Access Token：已获取（${accessToken.take(8)}...）" else "Access Token：未获取（请使用TV端扫码登录）").append("\n")
        sb.append(if (mid > 0) "UID：$mid" else "UID：未登录").append("\n")
        sb.append(if (csrf.isNotEmpty()) "CSRF Token：已获取" else "CSRF Token：未获取").append("\n")
        sb.append(if (refreshToken.isNotEmpty()) "Refresh Token：已获取（${refreshToken.take(8)}...）" else "Refresh Token：未获取")
        MsgUtil.showText("登录凭证状态", sb.toString())
    }

    private fun buildUIGroup() {
        button("恢复默认") {
            SharedPreferencesUtil.putInt(SettingsKeys.PADDING_H, 0)
            SharedPreferencesUtil.putInt(SettingsKeys.PADDING_V, 0)
            SharedPreferencesUtil.putFloat(SettingsKeys.DPI, 1.0f)
            SharedPreferencesUtil.putInt(SettingsKeys.DENSITY, -1)
            SharedPreferencesUtil.putBoolean(SettingsKeys.UI_ROUND, false)
            refresh()
            MsgUtil.showMsg("恢复完成")
        }
        button("查看预览") {
            startActivity(Intent(this, UIPreviewActivity::class.java))
        }

        title("布局适配")
        switch("圆屏适配", getString(R.string.desc_setting_ui_round), SettingsKeys.UI_ROUND, false) { isChecked ->
            SharedPreferencesUtil.putInt(SettingsKeys.PADDING_H, if (isChecked) 5 else 0)
            SharedPreferencesUtil.putInt(SettingsKeys.PADDING_V, if (isChecked) 3 else 0)
            refresh()
            if (isChecked) MsgUtil.showMsg("界面边距已更改\n可以手动微调喵")
        }
        input("界面大小", getString(R.string.desc_setting_ui), SettingsKeys.DPI, "input_float", "1.0") { value ->
            try {
                val dpiScale = value.toFloat()
                if (dpiScale in 0.25f..5.0f) {
                    SharedPreferencesUtil.putFloat(SettingsKeys.DPI, dpiScale)
                    BiliTerminal.DPI_FORCE_CHANGE = true
                }
            } catch (ignored: Throwable) {
            }
        }
        input("界面边距（横向）", getString(R.string.desc_setting_ui_padding), SettingsKeys.PADDING_H, "input_int", "0") { value ->
            try {
                val paddingH = value.toInt()
                if (paddingH <= 30) SharedPreferencesUtil.putInt(SettingsKeys.PADDING_H, paddingH)
            } catch (ignored: Throwable) {
            }
        }
        input("界面边距（纵向）", getString(R.string.desc_setting_ui_padding), SettingsKeys.PADDING_V, "input_int", "0") { value ->
            try {
                val paddingV = value.toInt()
                if (paddingV <= 30) SharedPreferencesUtil.putInt(SettingsKeys.PADDING_V, paddingV)
            } catch (ignored: Throwable) {
            }
        }
        input("设置Density", getString(R.string.desc_setting_ui_density), SettingsKeys.DENSITY, "input_int", "-1") { value ->
            try {
                val density = value.toInt()
                if (density >= 72) SharedPreferencesUtil.putInt(SettingsKeys.DENSITY, density)
            } catch (ignored: Throwable) {
            }
        }

        // 配色与圆角已迁到独立的「外观设置」页（本页的另一个 group_type 分组）
        nav(R.drawable.icon_ui, "外观设置", "主题配色、卡片圆角（字体待接入）") {
            startActivity(Intent(this, SettingGroupActivity::class.java).apply {
                putExtra("group_type", GROUP_APPEARANCE)
                putExtra("group_title", "外观设置")
            })
        }
        switch("横屏模式", getString(R.string.setting_lab_ui_landscape), SettingsKeys.UI_LANDSCAPE, false)
        input("开屏文字", getString(R.string.setting_lab_splashtext), SettingsKeys.SPLASH_TEXT, "input_string", "欢迎使用\nRE:哔哩终端")
        switch("文字跑马灯", getString(R.string.setting_lab_marquee), SettingsKeys.MARQUEE_ENABLE, true)
        switch("加载渐入渐出动画", getString(R.string.desc_load_transition), SharedPreferencesUtil.LOAD_TRANSITION, true)
    }

    /**
     * 「外观设置」分组：外观三模块的设置项集中在此。
     *
     * **为什么不做成独立 Activity**：本仓库的「独立设置子页面」有两种形态——
     * ① 独立 Activity（如「菜单设置」`SettingMenuActivity`），按约定要改三处（含 manifest 注册）；
     * ② 本页的另一个 `group_type` 分组，由 [buildContent] 分发，不需要新 Activity/manifest/布局。
     * 外观设置只有若干列表项、无自定义交互，故走 ②；两种形态对用户都是独立一屏。
     *
     * 字体模块（`FontStyle` 的字号 4 档 + 字族 2 选）**尚未接入渲染路径**，故此处不放设置项——
     * 不给出点了没反应的开关。接入后在此追加 `title("字体")` 一段即可。
     */
    private fun buildAppearanceGroup() {
        title("配色")
        listChoose(
            "主题配色",
            "选择应用的主题配色方案",
            SettingsKeys.THEME,
            ColorScheme.THEME_DEFAULT,
            listOf("B站粉", "知乎蓝", "爱奇艺绿", "紫色空灵", "五彩斑斓", "经典灰", "经典终端"),
            listOf(
                ColorScheme.THEME_BILIBILI_PINK,
                ColorScheme.THEME_ZHIHU_BLUE,
                ColorScheme.THEME_IQIYI_GREEN,
                ColorScheme.THEME_PURPLE_FANTASY,
                ColorScheme.THEME_RAINBOW_FANTASY,
                ColorScheme.THEME_CLASSIC_GRAY,
                ColorScheme.THEME_CLASSIC_TERMINAL
            )
        ) { oldValue, newValue ->
            if (oldValue != newValue) {
                // 统一走外观门面的写入点（此前直接操作 SharedPreferences，绕过了单一入口）
                AppearanceManager.setTheme(newValue)
                recreate()
            }
        }

        title("圆角")
        listChoose(
            "卡片圆角",
            "「方角」还原原项目 BiliClient 的圆角；「圆角」为本项目主题化时的取值",
            CornerStyle.KEY,
            CornerStyle.DEFAULT,
            CornerStyle.DISPLAY_NAMES,
            CornerStyle.VALUES
        ) { oldValue, newValue ->
            if (oldValue != newValue) {
                // 走门面写入：落盘 + 递增外观版本号，其它页面在 onResume 时自动重建
                AppearanceManager.setCornerRadius(newValue)
                recreate()
            }
        }

        title("自定义字体")
        nav(
            R.drawable.icon_folder,
            "选择字体文件",
            FontStyle.currentFileName()?.let { "当前：$it" }
                ?: "当前：系统默认（支持 TTF / OTF / TTC）"
        ) { pickFontFile() }
        if (FontStyle.hasCustomFont()) {
            button("恢复系统字体") { restoreSystemFont() }
        }
    }

    /** 打开文件管理器挑选字体文件。 */
    private fun pickFontFile() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            fontPickerLauncher.launch(intent)
        } catch (e: Throwable) {
            // 手表上不一定装了文件管理器，也可能没有 activity 能处理该 intent
            MsgUtil.showMsg("没有可用的文件管理器")
        }
    }

    /** 删除已安装字体并回到系统字体。 */
    private fun restoreSystemFont() {
        FontStyle.clear(this)
        MsgUtil.showMsg("已恢复系统字体")
        rebuild(GROUP_APPEARANCE)
        recreate()
    }

    private fun buildContentGroup() {
        nav(R.drawable.icon_menu, "菜单设置", "调整菜单顺序、将菜单项移入未启用") {
            startActivity(Intent(this, SettingMenuActivity::class.java))
        }
        nav(R.drawable.icon_info, "我的页面设置", "调整「我的」页面入口顺序、将入口移入更多") {
            startActivity(Intent(this, SettingMySpaceActivity::class.java))
        }
        nav(R.drawable.icon_search, "搜索设置", "搜索类别显示与排序") {
            startActivity(Intent(this, SettingSearchActivity::class.java))
        }
        nav(R.drawable.icon_settings_videoinfo, "详情页设置", "视频详情页、专栏详情页等相关设置") {
            startActivity(Intent(this, SettingInfoActivity::class.java))
        }
        nav(R.drawable.icon_reply, "评论区设置", "关于评论区的一些设置选项") {
            startActivity(Intent(this, SettingRepliesActivity::class.java))
        }
        nav(R.drawable.icon_creative_center, "通用偏好", "一些特殊适配和特殊需求选项") {
            startActivity(Intent(this, SettingPrefActivity::class.java))
        }
    }

    private fun buildDownloadGroup() {
        title("下载引擎")
        switch(getString(R.string.aria2_enable), getString(R.string.aria2_enable_desc), SettingsKeys.ARIA2_ENABLED, true)
        switch("使用旧版下载器", getString(R.string.setting_lab_download_old), SettingsKeys.DEV_DOWNLOAD_OLD, false)
        input(getString(R.string.aria2_split), getString(R.string.aria2_split_desc), SettingsKeys.ARIA2_SPLIT, "input_int", "5")

        title("缓存选项")
        switch(getString(R.string.cache_quick_mode), getString(R.string.cache_quick_mode_desc), SettingsKeys.CACHE_QUICK_MODE, true) {
            rebuild("download")
        }
        input("并行下载视频数", getString(R.string.desc_parallel_download), SettingsKeys.PARALLEL_DOWNLOAD_VIDEOS, "input_int", Aria2Util.DEFAULT_PARALLEL_DOWNLOAD_VIDEOS.toString())
        if (SharedPreferencesUtil.getBoolean(SettingsKeys.CACHE_QUICK_MODE, true)) {
            listChoose(
                getString(R.string.cache_default_quality),
                getString(R.string.cache_default_quality_desc),
                SettingsKeys.CACHE_DEFAULT_QUALITY,
                "dialog",
                listOf(
                    getString(R.string.cache_quality_highest),
                    getString(R.string.cache_quality_720p),
                    getString(R.string.cache_quality_360p),
                    getString(R.string.cache_quality_audio_only),
                    getString(R.string.cache_quality_dialog)
                ),
                listOf("highest", "64", "16", "audio_only", "dialog")
            )
        }
        switch("强制高分辨率选项", getString(R.string.desc_force_high_quality), SettingsKeys.FORCE_HIGH_QUALITY, true)

        title("存储路径")
        input("缓存路径", getString(R.string.setting_lab_path_video), SettingsKeys.SAVE_PATH_VIDEO, "input_string", FileUtil.getVideoDownloadPath().toString())
        input("图片下载路径", getString(R.string.setting_lab_path_pictures), SettingsKeys.SAVE_PATH_PICTURES, "input_string", FileUtil.getPicturePath().toString())
    }

    private fun buildLabGroup() {
        title("性能")
        switch(
            "高性能模式",
            getString(R.string.desc_high_performance),
            PerformanceManager.KEY_HIGH_PERFORMANCE_MODE,
            false
        ) { isChecked ->
            PerformanceManager.setHighPerformanceMode(isChecked)
            if (isChecked && PerformanceManager.getCurrentPerfLevel() != PerformanceManager.PERF_LEVEL_HIGH) {
                MsgUtil.showMsg("高性能模式已开启，部分优化将在下次启动应用时生效")
            } else if (!isChecked) {
                MsgUtil.showMsg("已切换到性能优先模式")
            }
        }

        title("推荐")
        switch("推荐视频去重", getString(R.string.desc_recommend_dedup), SharedPreferencesUtil.RECOMMEND_DEDUP_ENABLE, true)
        listChoose(
            "推荐源",
            "选择使用哪个API获取推荐视频",
            SharedPreferencesUtil.RECOMMEND_SOURCE,
            SharedPreferencesUtil.RECOMMEND_SOURCE_WEB,
            listOf("网页源", "APP源", "混合使用"),
            listOf(
                SharedPreferencesUtil.RECOMMEND_SOURCE_WEB,
                SharedPreferencesUtil.RECOMMEND_SOURCE_APP,
                SharedPreferencesUtil.RECOMMEND_SOURCE_BOTH
            )
        )

        title("功能开关")
        switch("新版弹幕获取方式", getString(R.string.desc_new_danmaku_api), SharedPreferencesUtil.NEW_DANMAKU_API, true)
        switch("私信未读标记", getString(R.string.desc_private_msg_unread_badge_enable), SharedPreferencesUtil.PRIVATE_MSG_UNREAD_BADGE_ENABLE, false)
        switch("虚拟合集", getString(R.string.desc_virtual_collection), SharedPreferencesUtil.VIRTUAL_COLLECTION_ENABLE, true)

        title("播放器兼容")
        switch("播放器旋屏兼容方案", getString(R.string.desc_player_rotate_software), SettingsKeys.DEV_PLAYER_ROTATE_SOFTWARE, false)
        switch("显示视频分段", getString(R.string.desc_player_viewpoints), SettingsKeys.PLAYER_SHOW_VIEWPOINTS, true)
        switch("系统媒体控件", getString(R.string.setting_lab_media_session), SharedPreferencesUtil.PLAYER_MEDIA_SESSION_ENABLE, false)
        switch("互动视频调试", getString(R.string.desc_player_interaction_debug), SettingsKeys.PLAYER_INTERACTION_DEBUG, false)
    }

    private fun buildAboutGroup() {
        nav(
            R.drawable.icon_info,
            "关于",
            "版本号、开发团队、联系方式、开源信息",
            onClick = { startActivity(Intent(this, AboutActivity::class.java)) },
            onLongClick = {
                val eggList = resources.getStringArray(R.array.eggs)
                MsgUtil.showText("回声洞", eggList[eggClick])
                if (eggClick < eggList.size - 1) eggClick++
                true
            }
        )
        nav(R.drawable.icon_update, "检查更新", "检查新版本并更新") {
            startActivity(Intent(this, UpdateActivity::class.java))
        }
        nav(R.drawable.icon_announcement, "公告列表", "查看哔哩终端发布公告") {
            startActivity(Intent(this, AnnouncementsActivity::class.java))
        }
        nav(R.drawable.icon_help, "教程管理", "管理各页面的新手教程进度") {
            startActivity(Intent(this, TutorialManagerActivity::class.java))
        }
    }

    /**
     * 开发者工具分组 —— 仅在 Debug 构建下显示
     */
    private fun buildDevGroup() {
        nav(R.drawable.icon_laboratory, "功能测试", "测试各项功能是否正常") {
            startActivity(Intent(this, TestActivity::class.java))
        }
        nav(R.drawable.icon_time, "TO DO清单", "开发者的愿望清单") {
            startActivity(Intent(this, TodoListActivity::class.java))
        }

        title("调试日志")
        val debugBuild = BiliTerminal.isDebugBuild()
        switch("允许Logu.v", getString(R.string.setting_lab_logv), SettingsKeys.DEV_LOGV, debugBuild)
        switch("允许Logu.d", "", SettingsKeys.DEV_LOGD, debugBuild)
        switch("允许Logu.i", "", SettingsKeys.DEV_LOGI, debugBuild)
        switch("详细显示数据解析报错", getString(R.string.setting_lab_jsonerr_detailed), SettingsKeys.DEV_JSONERR_DETAILED, debugBuild)
        switch("详细显示列表报错", getString(R.string.setting_lab_recyclererr_detailed), SettingsKeys.DEV_RECYCLERERR_DETAILED, debugBuild)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val position = data.getIntExtra("position", -1)
            val value = data.getStringExtra("value") ?: return
            if (position < 0 || position >= sections.size) return
            val section = sections[position]
            val oldValue = SharedPreferencesUtil.getString(section.id, section.defaultValue)
            SharedPreferencesUtil.putString(section.id, value)
            adapter?.notifyItemChanged(position)
            (section.extra as? SettingsAdapter.ListChooseHolder.ListChooseExtra)?.onSelect?.invoke(oldValue, value)
        }
    }
}
