package com.RobinNotBad.BiliClient.activity.settings

import com.RobinNotBad.BiliClient.ui.appearance.ColorMode
import com.RobinNotBad.BiliClient.util.UiText

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.os.Bundle
import android.view.View
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.FeedbackActivity
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.activity.feedback.FeedbackListActivity
import com.RobinNotBad.BiliClient.activity.settings.login.AccountSwitchActivity
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.activity.settings.login.SpecialLoginActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.api.TerminalApi
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.ui.appearance.AmbientGlow
import com.RobinNotBad.BiliClient.ui.appearance.AppearanceManager
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.ui.appearance.CornerStyle
import com.RobinNotBad.BiliClient.ui.appearance.FontStyle
import com.RobinNotBad.BiliClient.ui.appearance.GlassStyle
import com.RobinNotBad.BiliClient.util.Aria2Util
import com.RobinNotBad.BiliClient.util.BetaUpdate
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.DisguisePrefs
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.PerformanceManager
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.TerminalReportPrefs

/** 「外观设置」分组的 group_type 取值。`SettingsIndex` 里跳转该分组时也用这个常量。 */
const val GROUP_APPEARANCE = "appearance"

/**
 * 「反馈与统计」分组的 group_type 取值。`SettingsIndex` 里跳转该分组时也用这个常量。
 *
 * <p>26.10.16：这 5 个项原先**平铺在「关于与帮助」页上**，把那一页撑得很长。
 * 改成与「外观设置」同样的形态——本页的另一个 `group_type` 分组，
 * 上级只留一个入口卡片，点进去才是这些项（对用户同样是独立一屏）。
 */
const val GROUP_FEEDBACK = "feedback"

/** 「通知设置」分组的 group_type 取值。`SettingsIndex` 里跳转该分组时也用这个常量。 */
const val GROUP_NOTIFY = "notify"

/** 「伪装与安全」分组的 group_type 取值（issue #22）。`SettingsIndex` / `SettingMainActivity` 共用。 */
const val GROUP_DISGUISE = "disguise"

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
                    MsgUtil.showMsg(getString(R.string.msg_font_apply_failed, error))
                } else {
                    MsgUtil.showMsg(getString(R.string.msg_settinggroupactivity_001))
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
        setPageName(SettingText.resolve(this, groupTitle))

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
            "layout" -> buildLayoutGroup()
            GROUP_APPEARANCE -> buildAppearanceGroup()
            GROUP_NOTIFY -> buildNotifyGroup()
            GROUP_DISGUISE -> buildDisguiseGroup()
            GROUP_FEEDBACK -> buildFeedbackGroup()
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
        // 显示"当前有效"的 csrf，而不是登录时的快照，否则这里的自检结果会误导排查
        val csrf = NetWorkUtil.currentCsrf()
        val refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "")

        val sb = StringBuilder()
        sb.append(if (cookies.isNotEmpty()) "Cookie：已获取" + if (cookies.contains("SESSDATA=")) "（含SESSDATA）" else "" else "Cookie：未获取").append("\n")
        sb.append(if (accessToken.isNotEmpty()) "Access Token：已获取（${accessToken.take(8)}...）" else "Access Token：未获取（请使用TV端扫码登录）").append("\n")
        sb.append(if (mid > 0) "UID：$mid" else "UID：未登录").append("\n")
        sb.append(if (csrf.isNotEmpty()) "CSRF Token：已获取" else "CSRF Token：未获取").append("\n")
        sb.append(if (refreshToken.isNotEmpty()) "Refresh Token：已获取（${refreshToken.take(8)}...）" else "Refresh Token：未获取")
        MsgUtil.showText(SettingText.resolve(this, "登录凭证状态"), sb.toString())
    }

    private fun buildUIGroup() {
        // 两个二级页：「外观设置」管配色/字体，「布局适配」管尺寸/边距/圆屏/横屏
        nav(R.drawable.icon_ui, "外观设置", "主题配色、卡片圆角、自定义字体") {
            startActivity(Intent(this, SettingGroupActivity::class.java).apply {
                putExtra("group_type", GROUP_APPEARANCE)
                putExtra("group_title", "外观设置")
            })
        }
        nav(
            R.drawable.icon_ui,
            getString(R.string.settingtext_setting_layout_group),
            getString(R.string.settingtext_setting_layout_group_desc)
        ) {
            startActivity(Intent(this, SettingGroupActivity::class.java).apply {
                putExtra("group_type", "layout")
                putExtra("group_title", getString(R.string.settingtext_setting_layout_group))
            })
        }
        input("开屏文字", getString(R.string.setting_lab_splashtext), SettingsKeys.SPLASH_TEXT, "input_string", "欢迎使用\nRE:哔哩终端")
        switch("文字跑马灯", getString(R.string.setting_lab_marquee), SettingsKeys.MARQUEE_ENABLE, true)
        switch("加载渐入渐出动画", getString(R.string.desc_load_transition), SharedPreferencesUtil.LOAD_TRANSITION, true)
    }

    /**
     * 「布局适配」二级页（入口在「界面与外观」里）：界面大小、边距、圆屏、密度、横屏等「屏幕如何排布」相关项。
     *
     * 由「界面与外观」拆出（原 `buildUIGroup` 中非外观的那一半），不在设置主页单列。首次启动引导页
     * `SetupUIActivity` 本就是纯布局项（界面大小 / 边距 / 圆屏），语义与本分组一致，无需改动。
     */
    private fun buildLayoutGroup() {
        button("恢复默认") {
            SharedPreferencesUtil.putInt(SettingsKeys.PADDING_H, 0)
            SharedPreferencesUtil.putInt(SettingsKeys.PADDING_V, 0)
            SharedPreferencesUtil.putFloat(SettingsKeys.DPI, 1.0f)
            SharedPreferencesUtil.putInt(SettingsKeys.DENSITY, -1)
            SharedPreferencesUtil.putBoolean(SettingsKeys.UI_ROUND, false)
            refresh()
            MsgUtil.showMsg(getString(R.string.msg_settinggroupactivity_002))
        }
        button("查看预览") {
            startActivity(Intent(this, UIPreviewActivity::class.java))
        }

        switch("圆屏适配", getString(R.string.desc_setting_ui_round), SettingsKeys.UI_ROUND, false) { isChecked ->
            SharedPreferencesUtil.putInt(SettingsKeys.PADDING_H, if (isChecked) 5 else 0)
            SharedPreferencesUtil.putInt(SettingsKeys.PADDING_V, if (isChecked) 3 else 0)
            refresh()
            if (isChecked) MsgUtil.showMsg(getString(R.string.msg_settinggroupactivity_003))
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
        switch("横屏模式", getString(R.string.setting_lab_ui_landscape), SettingsKeys.UI_LANDSCAPE, false)
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
            "颜色模式",
            getString(R.string.desc_setting_color_mode),
            ColorMode.KEY,
            ColorMode.DEFAULT,
            ColorMode.DISPLAY_NAMES,
            ColorMode.VALUES
        ) { oldValue, newValue ->
            if (oldValue != newValue) {
                AppearanceManager.setColorMode(newValue)
                // 全局 context（Toast / Glide 占位图等用它取资源）按新模式重建
                BiliTerminal.context = BiliTerminal.getFitDisplayContext(applicationContext)
                // 通知 AppCompat 切换夜间位：所有 AppCompat 页面（含本页）会被它自动重建，这里不再手动 recreate
                ColorMode.applyGlobal()
            }
        }
        listChoose(
            "主题配色",
            "选择应用的主题配色方案",
            SettingsKeys.THEME,
            ColorScheme.THEME_DEFAULT,
            listOf(UiText.of("B站粉"), "知乎蓝", "爱奇艺绿", "紫色空灵", "五彩斑斓", "经典灰", "经典终端"),
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
        // 开关本身由 SettingsAdapter 落盘；这里再走门面写一次，为的是递增外观版本号，
        // 让返回栈里的其它页面在 onResume 时一起重建
        switch("弥散光晕", getString(R.string.desc_setting_ambient_glow), SettingsKeys.UI_AMBIENT_GLOW, AmbientGlow.DEFAULT) { isChecked ->
            AppearanceManager.setAmbientGlow(isChecked)
            recreate()
        }
        listChoose(
            getString(R.string.text_setting_ambient_glow_source),
            getString(R.string.desc_setting_ambient_glow_source),
            AmbientGlow.KEY_SOURCE,
            AmbientGlow.DEFAULT_SOURCE,
            resources.getStringArray(R.array.ambient_glow_source_names).toList(),
            AmbientGlow.SOURCE_VALUES
        ) { oldValue, newValue ->
            if (oldValue != newValue) {
                AppearanceManager.setAmbientGlowSource(newValue)
                recreate()
            }
        }
        switch("玻璃质感", getString(R.string.desc_setting_glass_components), SettingsKeys.UI_GLASS_COMPONENTS, GlassStyle.DEFAULT) { isChecked ->
            AppearanceManager.setGlassComponents(isChecked)
            recreate()
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
        // **常驻**，不藏在「已安装」条件后面：自定义字体出问题时（最典型的是字体缺中文字形，
        // 界面全变方框、文字读不了）这一条是唯一能自救的入口，必须一直在、位置稳定。
        button("恢复系统字体") { restoreSystemFont() }
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
            MsgUtil.showMsg(getString(R.string.msg_settinggroupactivity_004))
        }
    }

    /** 删除已安装字体并回到系统字体。未装字体时只提示，不做多余的重建。 */
    private fun restoreSystemFont() {
        if (!FontStyle.hasCustomFont()) {
            MsgUtil.showMsg(getString(R.string.msg_settinggroupactivity_005))
            return
        }
        FontStyle.clear(this)
        MsgUtil.showMsg(getString(R.string.msg_settinggroupactivity_006))
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

    /**
     * 通知设置。
     *
     * 用户反馈「各种新加的通知应该在设置里新增一个大类-通知设置来关闭他」——原先这两项埋在
     * 「内容与浏览 → 通用偏好 → 更新提醒」里，用户根本找不到，于是以为「新加的通知没法关」。
     *
     * **两个开关的键是既有的，没有新增 SP 键**（`private_msg_notify_enable` /
     * `bangumi_update_notify_enable`，两者默认值本来就是 `true`），所以老用户升级后不需要任何迁移：
     * 既不会丢设置，也不会被重新打开。旧位置（`SettingPrefActivity` 的「更新提醒」段）
     * 已同步移除，避免两个入口指向同一开关、状态还不同步。
     *
     * 这里只放「会让通知栏响」的开关；「新动态数量检查」「消息数量检查」等只影响应用内红点、
     * 不产生系统通知的项仍留在通用偏好，不搬过来——搬了会让用户以为关掉它们就不会有通知了。
     */
    private fun buildNotifyGroup() {
        title("通知开关")
        switch("新消息通知", getString(R.string.desc_private_msg_notify_enable),
            SettingsKeys.PRIVATE_MSG_NOTIFY_ENABLE, true)
        switch("追番更新提醒", getString(R.string.desc_bangumi_update_notify_enable),
            SettingsKeys.BANGUMI_UPDATE_NOTIFY_ENABLE, true)
    }

    /**
     * 伪装与安全（issue #22）。
     *
     * 三件事：总开关、样式、退出暗号。**默认全关**——开启后顶栏单击要等一个双击判定窗口
     * （见 `BaseActivity.setupTopbarDoubleTapDisguise`），不该让所有用户承担这个代价。
     *
     * 暗号必须走 `input_string` + 自校验后再落盘：`input_int` 会把 `1234` 存成 Int、
     * 读的时候又按 String 取，取不到就回退默认值——设置页显示的和实际用的会悄悄不一致。
     */
    private fun buildDisguiseGroup() {
        title(getString(R.string.text_setting_disguise_group))

        switch(
            getString(R.string.text_setting_disguise_enabled),
            getString(R.string.desc_setting_disguise_enabled),
            SettingsKeys.DISGUISE_ENABLED,
            DisguisePrefs.DEFAULT_ENABLED
        ) { isChecked ->
            if (isChecked) MsgUtil.showMsg(getString(R.string.msg_setting_disguise_hint))
        }

        listChoose(
            getString(R.string.text_setting_disguise_style),
            getString(R.string.desc_setting_disguise_style),
            SettingsKeys.DISGUISE_STYLE,
            DisguisePrefs.DEFAULT_STYLE,
            listOf(
                getString(R.string.text_setting_disguise_style_calculator),
                getString(R.string.text_setting_disguise_style_bluetooth)
            ),
            listOf(DisguisePrefs.STYLE_CALCULATOR, DisguisePrefs.STYLE_BLUETOOTH)
        )

        input(
            getString(R.string.text_setting_disguise_code),
            getString(
                R.string.desc_setting_disguise_code,
                DisguisePrefs.CODE_MIN_LENGTH,
                DisguisePrefs.CODE_MAX_LENGTH
            ),
            SettingsKeys.DISGUISE_CODE,
            "input_digits",
            DisguisePrefs.DEFAULT_CODE
        ) { value ->
            // 只在合法时落盘；**不弹 toast**：这个回调是逐键触发的（SettingsAdapter 的 TextWatcher），
            // 每敲一下弹一次提示会糊满屏幕。非法输入的纠正靠说明文案 + 不回写。
            val normalized = DisguisePrefs.normalizeCode(value)
            if (normalized != null) {
                SharedPreferencesUtil.putString(SettingsKeys.DISGUISE_CODE, normalized)
            }
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
        // 这里显示「用户配置/按 API 分档的默认目录」，不是运行时降级目录：降级时设置页
        // 不该预填一个临时的应用专属路径，否则用户会以为自己配置的路径被覆盖了。
        input("缓存路径", getString(R.string.setting_lab_path_video), SettingsKeys.SAVE_PATH_VIDEO, "input_string", FileUtil.getConfiguredVideoDownloadPath().toString())
        // 图片默认目录在 Android 10+ 是应用专属（安全审计 P2-2：公共 Pictures/ 要全盘权限，
        // 手表 ROM 往往又打不开授权页），下载完成会经 MediaStore 发布进相册；
        // 这里展示的是「配置/默认」，用户手动改成公共目录时仍可走下面的授权页。
        input("图片下载路径", getString(R.string.setting_lab_path_pictures), SettingsKeys.SAVE_PATH_PICTURES, "input_string", FileUtil.getConfiguredPictureDownloadPath().toString())
        // 公共目录在 Android 11+ 需要「所有文件访问」权限；手机一般能直达授权页，
        // 手表 ROM 往往没有该页面（issue #42），打不开时会提示，而下载链路已能自动降级。
        nav(
            R.drawable.icon_folder,
            getString(R.string.setting_btn_storage_permission),
            getString(R.string.setting_desc_storage_permission)
        ) { FileUtil.requestStoragePermission(this) }
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
                MsgUtil.showMsg(getString(R.string.msg_settinggroupactivity_007))
            } else if (!isChecked) {
                MsgUtil.showMsg(getString(R.string.msg_settinggroupactivity_008))
            }
        }

        title("推荐")
        switch("推荐视频去重", getString(R.string.desc_recommend_dedup), SharedPreferencesUtil.RECOMMEND_DEDUP_ENABLE, true)
        listChoose(
            "推荐源",
            "选择使用哪个API获取推荐视频",
            SharedPreferencesUtil.RECOMMEND_SOURCE,
            SharedPreferencesUtil.RECOMMEND_SOURCE_WEB,
            listOf(UiText.of("网页源"), "APP源", "混合使用"),
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
        switch("圆屏列表", getString(R.string.desc_ui_round_list_effect), SettingsKeys.UI_ROUND_LIST_EFFECT, false)

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
                MsgUtil.showText(SettingText.resolve(this, "回声洞"), eggList[eggClick])
                if (eggClick < eggList.size - 1) eggClick++
                true
            }
        )
        nav(
            R.drawable.icon_update,
            "检查更新",
            "检查新版本并更新",
            onClick = { startActivity(Intent(this, UpdateActivity::class.java)) },
            onLongClick = {
                showBetaCodeDialog()
                true
            }
        )
        nav(R.drawable.icon_announcement, "公告列表", "查看哔哩终端发布公告") {
            startActivity(Intent(this, AnnouncementsActivity::class.java))
        }
        nav(R.drawable.icon_help, "教程管理", "管理各页面的新手教程进度") {
            startActivity(Intent(this, TutorialManagerActivity::class.java))
        }

        // 26.10.09：自建反馈通道 + 匿名统计/崩溃上报。
        // 26.10.16：这 5 项原先平铺在本页，把「关于与帮助」撑得很长。改成与「外观设置」
        // 同样的形态——上级只留一个入口卡片，点进去才是那些项（对用户同样是独立一屏）。
        nav(
            R.drawable.icon_help,
            "反馈与统计",
            "反馈与建议、我的反馈、匿名统计与崩溃上报开关"
        ) {
            startActivity(Intent(this, SettingGroupActivity::class.java).apply {
                putExtra("group_type", GROUP_FEEDBACK)
                putExtra("group_title", "反馈与统计")
            })
        }
    }

    /**
     * 26.10.21：长按「检查更新」输入内测码，直接从内测分享站下载内测包。
     *
     * 内测码是纯数字（校验 + 数字键盘 + InputFilter 三层），拼成
     * `https://share.lanol.cn/#/?code=<码>` 这种分享链接后由 [UpdateManager.resolveBetaShare]
     * 解析出真实下载地址。解析、下载、安装都在 [UpdateActivity] 的 `beta_code` 模式里完成。
     */
    private fun showBetaCodeDialog() {
        TerminalDialog.input(
            this,
            title = getString(R.string.text_update_beta_code_title),
            hint = getString(R.string.text_update_beta_code_hint),
            maxLength = 20,
            digitsOnly = true,
            confirmText = getString(R.string.btn_update_beta_download)
        ) { raw ->
            val code = BetaUpdate.normalizeCode(raw)
            if (code == null) {
                MsgUtil.showMsg(getString(R.string.msg_update_beta_code_invalid))
            } else {
                startActivity(
                    Intent(this, UpdateActivity::class.java)
                        .putExtra("beta_code", code)
                )
            }
        }.show()
    }

    /**
     * 「反馈与统计」分组：自建反馈通道 + 匿名统计 / 崩溃上报的三个开关。
     *
     * <p>26.10.16 从 [buildAboutGroup] 整体迁来。**三个开关都能在这里关掉**——默认开是
     * 用户拍板的取舍（真实留存数据），但「默认开」必须配一条明确的关闭路径，
     * 否则就是偷偷摸摸收集。
     */
    private fun buildFeedbackGroup() {
        nav(
            R.drawable.icon_help,
            getString(R.string.feedback_entry_title),
            getString(R.string.feedback_entry_desc)
        ) {
            startActivity(Intent(this, FeedbackActivity::class.java))
        }
        // 26.10.16：「我的反馈」——看自己发过的反馈、与开发者一来一回的对话，并可追加说明。
        // 挂在这里而不是加菜单项：菜单结构对老用户是缓存的，新菜单项不会自动出现
        // （见 AGENTS.md「新增菜单页改三处」那条）。
        nav(
            R.drawable.icon_announcement,
            getString(R.string.feedback_mine_entry_title),
            getString(R.string.feedback_mine_entry_desc)
        ) {
            startActivity(Intent(this, FeedbackListActivity::class.java))
        }
        switch(
            getString(R.string.setting_telemetry),
            getString(R.string.desc_setting_telemetry),
            SettingsKeys.TELEMETRY_ENABLE,
            TerminalReportPrefs.DEFAULT_TELEMETRY_ENABLED
        ) { on ->
            // 用户主动打开开关就是一次明确的意思表示，等同于重新同意隐私说明——
            // 否则会出现「开关打开着、但因为之前点过不同意而一直不上报」这种说不通的状态。
            // 只在打开时记；关掉开关纯粹是撤回，不该改变同意记录（关了就自然不再上报）。
            if (on) TerminalApi.recordPrivacyConsentFromSettings()
        }
        switch(
            getString(R.string.setting_crash_auto),
            getString(R.string.desc_setting_crash_auto),
            SettingsKeys.CRASH_REPORT_AUTO,
            TerminalReportPrefs.DEFAULT_CRASH_REPORT_ENABLED
        ) { on ->
            if (on) TerminalApi.recordPrivacyConsentFromSettings()
        }
        switch(
            getString(R.string.setting_feedback_attach_mid),
            getString(R.string.desc_setting_feedback_attach_mid),
            SettingsKeys.FEEDBACK_ATTACH_MID,
            false
        )
    }

    /**
     * 开发者工具分组 —— 仅在 Debug 构建下显示
     */
    private fun buildDevGroup() {
        // TestActivity 只应存在于 Debug 包：这里用编译期常量而非运行期判断，
        // release 下 R8 会把整个分支折叠掉，类随之被 strip（清单声明在 src/debug）。
        if (BuildConfig.DEBUG) {
            nav(R.drawable.icon_laboratory, "功能测试", "测试各项功能是否正常") {
                startActivity(Intent(this, TestActivity::class.java))
            }
        }
        nav(R.drawable.icon_time, "TO DO清单", "开发者的愿望清单") {
            startActivity(Intent(this, TodoListActivity::class.java))
        }

        title("调试日志")
        val debugBuild = BiliTerminal.isDebugBuild()
        switch("允许Logu.v", getString(R.string.setting_lab_logv), SettingsKeys.DEV_LOGV, debugBuild) { applyLogSwitchesNow() }
        switch("允许Logu.d", "", SettingsKeys.DEV_LOGD, debugBuild) { applyLogSwitchesNow() }
        switch("允许Logu.i", "", SettingsKeys.DEV_LOGI, debugBuild) { applyLogSwitchesNow() }
        switch("详细显示数据解析报错", getString(R.string.setting_lab_jsonerr_detailed), SettingsKeys.DEV_JSONERR_DETAILED, debugBuild)
        switch("详细显示列表报错", getString(R.string.setting_lab_recyclererr_detailed), SettingsKeys.DEV_RECYCLERERR_DETAILED, debugBuild)

        // 26.10.14：Agent 测试模式。此前日志开关改完要重启应用才生效（applyLogSwitches 只在
        // Application.onCreate 跑一次），人工调试能忍、自动化不能忍——所以这里的每个开关都
        // 回调 applyLogSwitchesNow() 立即生效。
        title("Agent 测试")
        switch("Agent 测试模式", getString(R.string.desc_dev_agent_mode), SettingsKeys.DEV_AGENT_MODE, false) { applyLogSwitchesNow() }
        switch("日志落盘", getString(R.string.desc_dev_log_file), SettingsKeys.DEV_LOG_FILE, true) { applyLogSwitchesNow() }
    }

    /**
     * 立即把日志/追踪开关写进运行期状态。
     *
     * 让「改设置」与「生效」之间不需要重启：agent 通过调试桥改完开关后同样会调 [BiliTerminal.applyLogSwitches]。
     */
    private fun applyLogSwitchesNow() {
        BiliTerminal.applyLogSwitches()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 回写逻辑与播放器设置页共用 applyListChooseResult：各页只提供自己的适配器刷新方式
        applyListChooseResult(requestCode, resultCode, data, sections) { adapter?.notifyItemChanged(it) }
    }
}
