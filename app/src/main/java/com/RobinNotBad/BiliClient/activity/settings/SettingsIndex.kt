package com.RobinNotBad.BiliClient.activity.settings

import android.app.Activity
import android.content.Intent
import androidx.annotation.StringRes
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.FeedbackActivity
import com.RobinNotBad.BiliClient.activity.feedback.FeedbackListActivity
import com.RobinNotBad.BiliClient.activity.settings.login.AccountSwitchActivity
import com.RobinNotBad.BiliClient.player.SponsorCategoryLabels
import com.RobinNotBad.BiliClient.ui.widget.IslandPolicy
import com.RobinNotBad.BiliClient.util.FeedbackUnread
import com.RobinNotBad.BiliClient.util.OptionalFeatures

/**
 * 全局设置搜索索引：集中维护「分组 / 子页面 / 内联设置项 / 叶子设置项」的可搜索条目。
 *
 * 条目 name 与各设置页中 SettingSection 的 name 保持一致，供搜索命中后跳转定位。
 */
object SettingsIndex {

    /**
     * 一条可搜索的设置。
     *
     * [keywords] 只参与匹配、不显示在结果里：给「用户嘴里的叫法」与「界面上的叫法」不一致的
     * 项目补别名（例如「播放器界面」也叫「播放器按钮」），避免为了可搜性去改界面文案。
     */
    data class Entry(
        val name: String,
        val desc: String = "",
        val keywords: String = "",
        val open: (Activity) -> Unit
    )

    /** 取界面文案资源。本索引只在 Activity 里构建，此时 BiliTerminal.context 必然已就绪。 */
    private fun text(@StringRes id: Int): String = BiliTerminal.context!!.getString(id)

    fun build(): List<Entry> {
        val list = ArrayList<Entry>()

        // 分组标题：搜索条目名与 openGroup 的 group_title 用的是同一份文案，
        // 抽成局部变量，免得同一个资源键在下面重复书写。
        val groupAccount = text(R.string.settingtext_settingmainactivity_003) // 账号与登录
        val groupUi = text(R.string.settingtext_settingmainactivity_005) // 界面与外观
        val groupLayout = text(R.string.settingtext_settinggroupactivity_015) // 布局适配
        val groupContent = text(R.string.settingtext_settingmainactivity_007) // 内容与浏览
        val groupNotify = text(R.string.settingtext_settingmainactivity_009) // 通知设置
        val groupDownload = text(R.string.settingtext_settingmainactivity_011) // 缓存与下载
        val groupLab = text(R.string.settingtext_settingmainactivity_013) // 高级与实验
        val groupAbout = text(R.string.settingtext_settingmainactivity_015) // 关于与帮助
        val groupAppearance = text(R.string.settingtext_settinggroupactivity_019) // 外观设置
        val groupFeedback = text(R.string.settingtext_settinggroupactivity_082) // 反馈与统计

        // ---- 一级分组 ----
        list += Entry(text(R.string.settingtext_settingmainactivity_001), text(R.string.settingtext_settingmainactivity_002)) { a ->
            a.startActivity(Intent(a, SettingPlayerChooseActivity::class.java))
        }
        list += Entry(groupAccount, text(R.string.settingtext_settingmainactivity_004)) { a -> openGroup(a, "account", groupAccount) }
        list += Entry(groupUi, text(R.string.settingtext_settingmainactivity_006)) { a -> openGroup(a, "ui", groupUi) }
        list += Entry(groupLayout, text(R.string.settingtext_setting_layout_group_desc)) { a -> openGroup(a, "layout", groupLayout) }
        list += Entry(groupContent, text(R.string.settingtext_settingmainactivity_008)) { a -> openGroup(a, "content", groupContent) }
        list += Entry(groupNotify, text(R.string.settingtext_settingmainactivity_010)) { a -> openGroup(a, GROUP_NOTIFY, groupNotify) }
        list += Entry(
            text(R.string.text_setting_disguise_group),
            text(R.string.desc_setting_disguise_index)
        ) { a ->
            openGroup(a, GROUP_DISGUISE, text(R.string.text_setting_disguise_group))
        }
        list += Entry(groupDownload, text(R.string.settingtext_settingmainactivity_012)) { a -> openGroup(a, "download", groupDownload) }
        list += Entry(groupLab, text(R.string.settingtext_settingsindex_001)) { a -> openGroup(a, "lab", groupLab) }
        list += Entry(groupAbout, text(R.string.settingtext_settingmainactivity_016)) { a -> openGroup(a, "about", groupAbout) }
        // 「番剧设置」（26.11.07）：组内两页都在 private/ 里，与设置页那张卡片用同一条判断。
        // 不存在的组不收录，否则搜索会把用户送进一个点了没反应的页面。
        if (OptionalFeatures.thirdPartySettingsAvailable) {
            val groupBangumi = text(R.string.setting_bangumi_group)
            list += Entry(groupBangumi, text(R.string.desc_setting_bangumi_group)) { a ->
                openGroup(a, GROUP_BANGUMI, groupBangumi)
            }
            list += Entry(
                text(R.string.setting_bangumi_source_entry),
                text(R.string.desc_setting_bangumi_source_entry)
            ) { a ->
                openGroup(
                    a, GROUP_BANGUMI, groupBangumi,
                    text(R.string.setting_bangumi_source_entry)
                )
            }
            list += Entry(
                text(R.string.setting_bangumi_danmaku_entry),
                text(R.string.desc_setting_bangumi_danmaku_entry)
            ) { a ->
                openGroup(
                    a, GROUP_BANGUMI, groupBangumi,
                    text(R.string.setting_bangumi_danmaku_entry)
                )
            }
        }
        // 「数据迁移」（26.11）：整页在 private/ 里，不存在时不收录，理由同上。
        if (OptionalFeatures.migrationAvailable) {
            list += Entry(
                text(R.string.setting_migration_entry),
                text(R.string.desc_setting_migration_entry),
                "迁移 导入 旧版 换版本 分支 搬数据 备份 下载"
            ) { a ->
                OptionalFeatures.openMigration(a)
            }
        }

        // ---- 子页面 ----
        list += Entry(text(R.string.pagename_setting_player_choose), text(R.string.settingtext_settingsindex_002)) { a ->
            a.startActivity(Intent(a, SettingPlayerChooseActivity::class.java))
        }
        // 「播放器界面」（自定义播放器按钮布局）入口：26.11.06 曾藏起来，26.11.09 已恢复，
        // 入口在「选择播放器」页的卡片；这里同样保留可搜索条目。
        // 26.11.15：标题加「（实验性）」，并补「播放器按钮」这类用户口语别名（keywords 只参与匹配）。
        list += Entry(
            text(R.string.pagename_setting_player_ui),
            text(R.string.setting_player_ui_entry_desc),
            "播放器按钮 按钮设置 按钮布局 界面按钮 播放界面布局 实验 实验性"
        ) { a ->
            a.startActivity(Intent(a, SettingPlayerUiActivity::class.java))
        }
        list += Entry(text(R.string.settingtext_settingsindex_003), text(R.string.settingtext_settingsindex_004)) { a ->
            a.startActivity(Intent(a, SettingQualityActivity::class.java))
        }
        list += Entry(
            text(R.string.net_quality_wifi_title),
            text(R.string.net_quality_wifi_desc)
        ) { a ->
            a.startActivity(Intent(a, SettingQualityActivity::class.java))
        }
        list += Entry(
            text(R.string.net_quality_mobile_title),
            text(R.string.net_quality_mobile_desc)
        ) { a ->
            a.startActivity(Intent(a, SettingQualityActivity::class.java))
        }
        list += Entry(
            text(R.string.storage_entry_title),
            text(R.string.storage_entry_desc)
        ) { a ->
            a.startActivity(Intent(a, StorageActivity::class.java))
        }
        list += Entry(text(R.string.pagename_settingterminalplayeractivity_001), text(R.string.settingtext_settingsindex_005)) { a ->
            a.startActivity(Intent(a, SettingTerminalPlayerActivity::class.java))
        }
        list += Entry(
            text(R.string.btn_login_confirm),
            text(R.string.settingtext_settinggroupactivity_001)
        ) { a -> openGroup(a, "account", groupAccount, text(R.string.btn_login_confirm)) }
        list += Entry(text(R.string.pagename_account_switch), text(R.string.settingtext_settinggroupactivity_002)) { a ->
            a.startActivity(Intent(a, AccountSwitchActivity::class.java))
        }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_003),
            text(R.string.settingtext_settingsindex_006)
        ) { a -> openGroup(a, "account", groupAccount, text(R.string.settingtext_settinggroupactivity_003)) }
        // 「登录凭证状态」（26.11.15 补收录）：弹窗类条目，名字与页内 SettingSection.name 严格一致。
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_005),
            text(R.string.settingtext_settingsindex_083)
        ) { a -> openGroup(a, "account", groupAccount, text(R.string.settingtext_settinggroupactivity_005)) }
        list += Entry(text(R.string.pagename_setting_menu), text(R.string.settingtext_settinggroupactivity_041)) { a ->
            a.startActivity(Intent(a, SettingMenuActivity::class.java))
        }
        list += Entry(text(R.string.pagename_settingmyspaceactivity_001), text(R.string.settingtext_settingsindex_007)) { a ->
            a.startActivity(Intent(a, SettingMySpaceActivity::class.java))
        }
        list += Entry(text(R.string.pagename_setting_search), text(R.string.settingtext_settinggroupactivity_043)) { a ->
            a.startActivity(Intent(a, SettingSearchActivity::class.java))
        }
        list += Entry(text(R.string.pagename_settinginfoactivity_001), text(R.string.settingtext_settinggroupactivity_044)) { a ->
            a.startActivity(Intent(a, SettingInfoActivity::class.java))
        }
        list += Entry(text(R.string.pagename_settingrepliesactivity_001), text(R.string.settingtext_settinggroupactivity_045)) { a ->
            a.startActivity(Intent(a, SettingRepliesActivity::class.java))
        }
        list += Entry(text(R.string.settingtext_settinggroupactivity_046), text(R.string.settingtext_settinggroupactivity_047)) { a ->
            a.startActivity(Intent(a, SettingPrefActivity::class.java))
        }
        list += Entry(text(R.string.pagename_about), text(R.string.settingtext_settinggroupactivity_077)) { a ->
            a.startActivity(Intent(a, AboutActivity::class.java))
        }
        list += Entry(text(R.string.pagename_update_check), text(R.string.settingtext_settinggroupactivity_079)) { a ->
            a.startActivity(Intent(a, UpdateActivity::class.java))
        }
        // 「更新通道」（26.11.15 补收录）：在「关于与帮助」页里，是稳定 / 测试 / 实验三档下拉。
        list += Entry(
            text(R.string.setting_update_channel),
            text(R.string.settingtext_settingsindex_094)
        ) { a -> openGroup(a, "about", groupAbout, text(R.string.setting_update_channel)) }
        // 「静默下载更新」（26.11.16）：开关在「关于与帮助」页里，靠 highlight 定位到那一项
        list += Entry(
            text(R.string.setting_silent_update),
            text(R.string.desc_setting_silent_update)
        ) { a -> openGroup(a, "about", groupAbout, text(R.string.setting_silent_update)) }
        list += Entry(text(R.string.pagename_announcementsactivity_001), text(R.string.settingtext_settinggroupactivity_080)) { a ->
            a.startActivity(Intent(a, AnnouncementsActivity::class.java))
        }
        // 「反馈与统计」（26.10.16 起独立成屏）：入口 + 叶子项，全局搜索都要能找到。
        // 叶子项靠 highlight 按项名滚动定位，所以名字必须与 buildFeedbackGroup() 里的
        // SettingSection.name 严格一致（同「通知设置」那两条的做法）。
        list += Entry(groupFeedback, text(R.string.settingtext_settingsindex_008)) { a ->
            openGroup(a, GROUP_FEEDBACK, groupFeedback)
        }
        list += Entry(text(R.string.pagename_feedback), text(R.string.feedback_entry_desc)) { a ->
            a.startActivity(Intent(a, FeedbackActivity::class.java))
        }
        // 「我的反馈」（26.10.16 新增）：独立 Activity 子页面，按 AGENTS 约定要进搜索索引
        list += Entry(
            text(R.string.feedback_mine_entry_title),
            text(R.string.feedback_mine_entry_desc) + FeedbackUnread.badgeSuffix()
        ) { a ->
            a.startActivity(Intent(a, FeedbackListActivity::class.java))
        }
        list += Entry(text(R.string.setting_telemetry), text(R.string.settingtext_settingsindex_009)) { a ->
            openGroup(a, GROUP_FEEDBACK, groupFeedback, text(R.string.setting_telemetry))
        }
        list += Entry(text(R.string.setting_crash_auto), text(R.string.settingtext_settingsindex_010)) { a ->
            openGroup(a, GROUP_FEEDBACK, groupFeedback, text(R.string.setting_crash_auto))
        }
        list += Entry(text(R.string.setting_feedback_attach_mid), text(R.string.settingtext_settingsindex_011)) { a ->
            openGroup(a, GROUP_FEEDBACK, groupFeedback, text(R.string.setting_feedback_attach_mid))
        }
        list += Entry(text(R.string.pagename_tutorial_manager), text(R.string.settingtext_settinggroupactivity_081)) { a ->
            a.startActivity(Intent(a, TutorialManagerActivity::class.java))
        }

        // ---- 界面与外观 内联项 ----
        list += Entry(
            text(R.string.section_ui_round),
            text(R.string.settingtext_settingsindex_012)
        ) { a -> openGroup(a, "layout", groupLayout, text(R.string.section_ui_round)) }
        // 沉浸式全屏（26.11.14）：highlight 按 SettingSection.name 逐字定位，这里的 name 必须与
        // 「布局适配」分组里那一行的 name（同一个 string 资源）完全一致。
        list += Entry(
            text(R.string.setting_immersive_fullscreen),
            text(R.string.desc_setting_immersive_fullscreen)
        ) { a -> openGroup(a, "layout", groupLayout, text(R.string.setting_immersive_fullscreen)) }
        // 顶栏灵动岛：26.11.08 曾暂时关闭并隐藏入口，26.11.09 恢复，
        // 26.11.15 用户要求「隐藏并永久关闭」——总闸在 IslandPolicy.FEATURE_ENABLED，这里跟着一起消失。
        // 26.11.14 起设置项在「外观 → 灵动岛设置」，索引这里指过去（含 5 个筛选开关与轮换间隔）。
        if (IslandPolicy.FEATURE_ENABLED) {
            val islandTitle = text(R.string.setting_topbar_island)
            list += Entry(
                islandTitle,
                text(R.string.island_setting_entrance_desc)
            ) { a -> openGroup(a, GROUP_ISLAND, islandTitle) }
            list += Entry(
                text(R.string.island_setting_playing),
                text(R.string.island_setting_playing_desc)
            ) { a -> openGroup(a, GROUP_ISLAND, islandTitle, text(R.string.island_setting_playing)) }
            list += Entry(
                text(R.string.island_setting_downloading),
                text(R.string.island_setting_downloading_desc)
            ) { a -> openGroup(a, GROUP_ISLAND, islandTitle, text(R.string.island_setting_downloading)) }
            list += Entry(
                text(R.string.island_setting_unread),
                text(R.string.island_setting_unread_desc)
            ) { a -> openGroup(a, GROUP_ISLAND, islandTitle, text(R.string.island_setting_unread)) }
            list += Entry(
                text(R.string.island_setting_battery),
                text(R.string.island_setting_battery_desc)
            ) { a -> openGroup(a, GROUP_ISLAND, islandTitle, text(R.string.island_setting_battery)) }
            list += Entry(
                text(R.string.island_setting_announcement),
                text(R.string.island_setting_announcement_desc)
            ) { a -> openGroup(a, GROUP_ISLAND, islandTitle, text(R.string.island_setting_announcement)) }
            list += Entry(
                text(R.string.island_setting_rotate_name),
                text(R.string.island_setting_rotate_desc)
            ) { a -> openGroup(a, GROUP_ISLAND, islandTitle, text(R.string.island_setting_rotate_name)) }
        }
        list += Entry(
            text(R.string.setting_topbar_height),
            text(R.string.desc_setting_topbar_height)
        ) { a -> openGroup(a, "layout", groupLayout, text(R.string.setting_topbar_height)) }
        list += Entry(
            text(R.string.section_ui_size),
            text(R.string.settingtext_settingsindex_013)
        ) { a -> openGroup(a, "layout", groupLayout, text(R.string.section_ui_size)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_016),
            text(R.string.settingtext_settingsindex_014)
        ) { a -> openGroup(a, "layout", groupLayout, text(R.string.settingtext_settinggroupactivity_016)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_017),
            text(R.string.settingtext_settingsindex_014)
        ) { a -> openGroup(a, "layout", groupLayout, text(R.string.settingtext_settinggroupactivity_017)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_018),
            text(R.string.settingtext_settingsindex_015)
        ) { a -> openGroup(a, "layout", groupLayout, text(R.string.settingtext_settinggroupactivity_018)) }
        list += Entry(
            groupAppearance,
            text(R.string.settingtext_settinggroupactivity_020)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance) }
        list += Entry(
            text(R.string.setting_index_color_mode),
            text(R.string.desc_setting_index_color_mode)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.setting_index_color_mode)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_026),
            text(R.string.settingtext_settinggroupactivity_027)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.settingtext_settinggroupactivity_026)) }
        list += Entry(
            text(R.string.setting_index_ambient_glow),
            text(R.string.desc_setting_index_ambient_glow)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.setting_index_ambient_glow)) }
        list += Entry(
            text(R.string.text_setting_ambient_glow_source),
            text(R.string.desc_setting_ambient_glow_source_index)
        ) { a ->
            openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.text_setting_ambient_glow_source))
        }
        list += Entry(
            text(R.string.setting_index_glass_components),
            text(R.string.desc_setting_index_glass_components)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.setting_index_glass_components)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_035),
            text(R.string.settingtext_settingsindex_016)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.settingtext_settinggroupactivity_035)) }
        // 批次 3（26.11.06）：背景图三项。highlight 用页内 SettingSection.name，
        // 分别是标题「背景图」/ 导航「选择背景图」/ 两个 listChoose 的名字。
        list += Entry(
            text(R.string.setting_index_background_image),
            text(R.string.desc_setting_index_background_image)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.setting_index_background_image)) }
        list += Entry(
            text(R.string.setting_index_background_fit),
            text(R.string.desc_setting_index_background_fit)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.appearance_background_fit_title)) }
        list += Entry(
            text(R.string.setting_index_background_scrim),
            text(R.string.desc_setting_index_background_scrim)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.appearance_background_scrim_title)) }
        // 26.11.15 补收录的 4 条外观项：字体两行（选择 / 恢复系统字体）、转场动画开关、
        // 清除背景图。名字必须与 buildAppearanceGroup() 里 SettingSection.name 逐字一致——
        // 字体两项页内用的是中文字面量，经 SettingText 映射到下面这两个资源。
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_038),
            text(R.string.settingtext_settingsindex_084)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.settingtext_settinggroupactivity_038)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_040),
            text(R.string.settingtext_settingsindex_085)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.settingtext_settinggroupactivity_040)) }
        list += Entry(
            text(R.string.appearance_page_motion_name),
            text(R.string.settingtext_settingsindex_087)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.appearance_page_motion_name)) }
        list += Entry(
            text(R.string.appearance_background_clear),
            text(R.string.settingtext_settingsindex_086)
        ) { a -> openGroup(a, GROUP_APPEARANCE, groupAppearance, text(R.string.appearance_background_clear)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_021),
            text(R.string.settingtext_settingsindex_017)
        ) { a -> openGroup(a, "layout", groupLayout, text(R.string.settingtext_settinggroupactivity_021)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_022),
            text(R.string.settingtext_settingsindex_018)
        ) { a -> openGroup(a, "ui", groupUi, text(R.string.settingtext_settinggroupactivity_022)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_023),
            text(R.string.settingtext_settingsindex_019)
        ) { a -> openGroup(a, "ui", groupUi, text(R.string.settingtext_settinggroupactivity_023)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_024),
            text(R.string.settingtext_settingsindex_020)
        ) { a -> openGroup(a, "ui", groupUi, text(R.string.settingtext_settinggroupactivity_024)) }

        // ---- 伪装与安全 内联项（26.11.15 补收录）----
        // 三处名字与 buildDisguiseGroup() 里的 SettingSection.name 逐字一致。
        list += Entry(
            text(R.string.text_setting_disguise_enabled),
            text(R.string.settingtext_settingsindex_088)
        ) { a -> openGroup(a, GROUP_DISGUISE, text(R.string.text_setting_disguise_group), text(R.string.text_setting_disguise_enabled)) }
        list += Entry(
            text(R.string.text_setting_disguise_style),
            text(R.string.settingtext_settingsindex_089)
        ) { a -> openGroup(a, GROUP_DISGUISE, text(R.string.text_setting_disguise_group), text(R.string.text_setting_disguise_style)) }
        list += Entry(
            text(R.string.text_setting_disguise_code),
            text(R.string.settingtext_settingsindex_090)
        ) { a -> openGroup(a, GROUP_DISGUISE, text(R.string.text_setting_disguise_group), text(R.string.text_setting_disguise_code)) }

        // ---- 缓存与下载 内联项 ----
        list += Entry(
            text(R.string.aria2_enable),
            text(R.string.settingtext_settingsindex_021)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.aria2_enable)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_052),
            text(R.string.settingtext_settingsindex_022)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.settingtext_settinggroupactivity_052)) }
        list += Entry(
            text(R.string.settingtext_settingsindex_023),
            text(R.string.settingtext_settingsindex_024)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.settingtext_settingsindex_023)) }
        list += Entry(
            text(R.string.aria2_split),
            text(R.string.settingtext_settingsindex_025)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.aria2_split)) }
        list += Entry(
            text(R.string.cache_quick_mode),
            text(R.string.settingtext_settingsindex_026)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.cache_quick_mode)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_054),
            text(R.string.settingtext_settingsindex_027)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.settingtext_settinggroupactivity_054)) }
        list += Entry(
            text(R.string.settingtext_settingsindex_028),
            text(R.string.settingtext_settingsindex_029)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.settingtext_settingsindex_028)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_055),
            text(R.string.settingtext_settingsindex_030)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.settingtext_settinggroupactivity_055)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_057),
            text(R.string.settingtext_settingsindex_031)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.settingtext_settinggroupactivity_057)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_058),
            text(R.string.settingtext_settingsindex_032)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.settingtext_settinggroupactivity_058)) }
        // 26.11.15 补收录：默认缓存画质（长按缓存 / 批量下载都读它）与存储权限授权入口。
        list += Entry(
            text(R.string.cache_default_quality),
            text(R.string.settingtext_settingsindex_091)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.cache_default_quality)) }
        list += Entry(
            text(R.string.setting_btn_storage_permission),
            text(R.string.settingtext_settingsindex_092)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.setting_btn_storage_permission)) }
        // 反馈 #211：缓存页自动刷新开关（默认开）。设置页里的总搜索也要能找到它，
        // 否则关了开关的人下次想开回去只能凭记忆翻分组。
        list += Entry(
            text(R.string.storage_refresh_auto_title),
            text(R.string.storage_refresh_auto_desc)
        ) { a -> openGroup(a, "download", groupDownload, text(R.string.storage_refresh_auto_title)) }

        // ---- 高级与实验 内联项 ----
        list += Entry(
            text(R.string.setting_player_ui_new),
            text(R.string.desc_player_ui_new),
            "播放器 新版 老版 布局 沉浸 实验 实验性"
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.setting_player_ui_new)) }
        list += Entry(
            text(R.string.setting_player_ui_new_idle_progress),
            text(R.string.desc_player_ui_new_idle_progress)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.setting_player_ui_new_idle_progress)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_060),
            text(R.string.settingtext_settingsindex_033)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.settingtext_settinggroupactivity_060)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_062),
            text(R.string.settingtext_settingsindex_034)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.settingtext_settinggroupactivity_062)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_063),
            text(R.string.settingtext_settingsindex_035)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.settingtext_settinggroupactivity_063)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_069),
            text(R.string.settingtext_settingsindex_036)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.settingtext_settinggroupactivity_069)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_070),
            text(R.string.settingtext_settinggroupactivity_070)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.settingtext_settinggroupactivity_070)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_071),
            text(R.string.settingtext_settingsindex_037)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.settingtext_settinggroupactivity_071)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_094),
            text(R.string.settingtext_settingsindex_082)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.settingtext_settinggroupactivity_094)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_073),
            text(R.string.settingtext_settingsindex_038)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.settingtext_settinggroupactivity_073)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_074),
            text(R.string.settingtext_settingsindex_039)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.settingtext_settinggroupactivity_074)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_075),
            text(R.string.settingtext_settingsindex_040)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.settingtext_settinggroupactivity_075)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_076),
            text(R.string.settingtext_settingsindex_041)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.settingtext_settinggroupactivity_076)) }
        list += Entry(
            text(R.string.setting_index_round_list),
            text(R.string.desc_setting_index_round_list)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.setting_index_round_list)) }
        // 「缓存页文件夹入口」（26.11.15 补收录）：页内名字是尚未进 strings.xml 的中文字面量，
        // 这里必须用同一个字面量当匹配键（SettingText 没有映射，两边显示的就是这个字面量）。
        list += Entry(
            "缓存页文件夹入口",
            text(R.string.settingtext_settingsindex_093)
        ) { a -> openGroup(a, "lab", groupLab, "缓存页文件夹入口") }
        // 「弹窗按钮并排」（26.11.17，默认开，反馈 #199）：设置页那行的 name 取的就是
        // R.string.setting_dialog_button_row，这边同源，highlight 逐字比对才落得到那一行上。
        list += Entry(
            text(R.string.setting_dialog_button_row),
            text(R.string.desc_dialog_button_row)
        ) { a -> openGroup(a, "lab", groupLab, text(R.string.setting_dialog_button_row)) }

        // ---- 叶子设置项（独立设置页）----
        // 26.11.07：详情页动作区两个默认隐藏的入口。名字必须与 SettingInfoActivity 里
        // SettingSection.name 严格一致——那两处都取同一批字符串资源，改名只需改资源。
        val detailLeaves = mutableListOf(
            text(R.string.settingtext_settinginfoactivity_001),
            text(R.string.settingtext_settinginfoactivity_002),
            text(R.string.settingtext_settinginfoactivity_003),
            text(R.string.settingtext_settinginfoactivity_004),
            text(R.string.settingtext_settinginfoactivity_005),
            text(R.string.settingtext_settinginfoactivity_006),
            text(R.string.settingtext_settinginfoactivity_007),
            text(R.string.setting_detail_note_entry),
            text(R.string.setting_detail_like_card_ring)
        )
        // 「分享到其他设备」只在带私有功能的构建里存在（设置页那条也一样），
        // 公开构建不收录，否则搜索会把用户送进一个点了没反应的开关。
        if (OptionalFeatures.available) {
            detailLeaves += text(R.string.setting_detail_share_row_entry)
        }
        addLeafItems(list, text(R.string.pagename_settinginfoactivity_001), SettingInfoActivity::class.java, detailLeaves)
        addLeafItems(list, text(R.string.pagename_settingrepliesactivity_001), SettingRepliesActivity::class.java, listOf(
            text(R.string.settingtext_settingrepliesactivity_001),
            text(R.string.settingtext_settingrepliesactivity_002),
            text(R.string.settingtext_settingrepliesactivity_003)
        ))
        addLeafItems(list, text(R.string.settingtext_settinggroupactivity_046), SettingPrefActivity::class.java, listOf(
            text(R.string.settingtext_settingprefactivity_002),
            text(R.string.settingtext_settingprefactivity_003),
            text(R.string.settingtext_settingprefactivity_029),
            text(R.string.pagename_creative_center),
            text(R.string.settingtext_settingprefactivity_004),
            text(R.string.settingtext_settingprefactivity_005),
            text(R.string.settingtext_settingprefactivity_006),
            text(R.string.settingtext_settingprefactivity_007),
            text(R.string.settingtext_settingprefactivity_009),
            text(R.string.settingtext_settingprefactivity_010),
            text(R.string.settingtext_settingprefactivity_011),
            text(R.string.settingtext_settingprefactivity_012),
            text(R.string.settingtext_settingprefactivity_013),
            text(R.string.text_update_background_check),
            text(R.string.settingtext_settingprefactivity_015),
            text(R.string.settingtext_settingprefactivity_016),
            text(R.string.settingtext_settingprefactivity_017),
            text(R.string.settingtext_settingprefactivity_018),
            text(R.string.settingtext_settingprefactivity_019),
            text(R.string.settingtext_settingprefactivity_020),
            text(R.string.settingtext_settingprefactivity_023),
            text(R.string.settingtext_settingprefactivity_025),
            text(R.string.settingtext_settingprefactivity_026),
            text(R.string.settingtext_settingprefactivity_027),
            // 「私信拦截提示」（26.11.15 补收录）：在「更新提醒」段里，此前漏收。
            text(R.string.settingtext_settingprefactivity_033)
        ))
        // 「通知设置」分组的叶子项：全局搜索要能找到这两个开关。
        // 跳转目标是 SettingGroupActivity（分组页），靠 highlight 按项名滚动定位——
        // 所以这里的名字必须与 buildNotifyGroup() 里的 SettingSection.name 严格一致。
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_049),
            text(R.string.settingtext_settingsindex_042)
        ) { a -> openGroup(a, GROUP_NOTIFY, groupNotify, text(R.string.settingtext_settinggroupactivity_049)) }
        list += Entry(
            text(R.string.settingtext_settinggroupactivity_050),
            text(R.string.settingtext_settingsindex_043)
        ) { a -> openGroup(a, GROUP_NOTIFY, groupNotify, text(R.string.settingtext_settinggroupactivity_050)) }

        addLeafItems(list, text(R.string.pagename_settingterminalplayeractivity_001), SettingTerminalPlayerActivity::class.java, listOf(
            // 组标题（页内按八组带标题展开，点组名直接滚到那一组开头）
            text(R.string.setting_player_group_gesture),
            text(R.string.setting_player_group_skip),
            text(R.string.setting_player_group_display),
            text(R.string.setting_player_group_decode),
            text(R.string.setting_player_group_toolbar),
            text(R.string.settingtext_settingsindex_044),
            text(R.string.setting_player_longpress_speed_name),
            text(R.string.settingtext_settingsindex_045),
            text(R.string.settingtext_settingsindex_046),
            text(R.string.settingtext_settingsindex_047),
            text(R.string.settingtext_settingsindex_048),
            // 手势组里此前漏收录的三项（26.11.06 补）：名字与页内 SettingSection.name 严格一致
            text(R.string.settingtext_settingterminalplayeractivity_004),
            text(R.string.setting_player_swipe_seek_scale_name),
            // 「播放默认值」分组：开播时自动应用，各项名字与页内 SettingSection.name 严格一致（搜索靠名字定位）
            text(R.string.settingtext_settingsindex_049),
            text(R.string.settingtext_settingsindex_050),
            text(R.string.settingtext_settingsindex_051),
            text(R.string.settingtext_settingsindex_052),
            text(R.string.settingtext_settingsindex_053),
            text(R.string.settingtext_settingsindex_054),
            text(R.string.settingtext_settingsindex_055),
            text(R.string.settingtext_settingsindex_056),
            text(R.string.settingtext_settingsindex_057),
            text(R.string.settingtext_settingsindex_058),
            text(R.string.settingtext_settingsindex_059),
            text(R.string.settingtext_settingsindex_060),
            text(R.string.settingtext_settingsindex_061),
            text(R.string.settingtext_settingsindex_062),
            text(R.string.settingtext_settingsindex_063),
            // 「自动跳过片头片尾」26.10.10 撤回时摘掉过，26.10.22 恢复（默认关闭）。
            // 关键词必须与页内 SettingSection.name 严格一致，否则搜索命中定位不到。
            text(R.string.settingtext_settingsindex_064),
            text(R.string.player_s_skip_mode_name),

            text(R.string.settingtext_settingsindex_065),
            text(R.string.settingtext_settingsindex_066),
            text(R.string.settingtext_settingsindex_067),
            text(R.string.settingtext_settingsindex_068),
            text(R.string.settingtext_settingsindex_069),
            text(R.string.settingtext_settingsindex_070),
            text(R.string.settingtext_settingsindex_071),
            text(R.string.settingtext_settingsindex_072),
            // 弹幕组里此前漏收录的三项（26.11.06 补）
            text(R.string.player_port_danmaku_guard_name),
            text(R.string.player_port_live_danmaku_factor_name),
            text(R.string.danmaku_cache_limit_name),
            // 批次 3（26.11.06）新增的三项：名字必须与页内 SettingSection.name 严格一致
            text(R.string.danmaku_self_border_name),
            text(R.string.player_s_longpress_switch_name),
            text(R.string.player_s_hint_scale_name),
            text(R.string.settingtext_settingsindex_073),
            text(R.string.settingtext_settingsindex_074),
            text(R.string.settingtext_settingsindex_075),
            text(R.string.settingtext_settingsindex_081),
            text(R.string.settingtext_settingsindex_080),
            // 顶部标题栏三件套（26.11.06 新增，名字与页内 SettingSection.name 严格一致）
            text(R.string.setting_player_ui_show_title),
            text(R.string.setting_player_ui_show_battery),
            text(R.string.setting_player_ui_show_clock),
            // 26.11.15 补收录的 7 项：返回键退出、快进步长、弹幕屏蔽词、短视频弹幕三项、弹幕停留时长。
            // 名字与 PlayerSettingsGroups.kt 里的 SettingSection.name 严格一致（都取同一批资源）。
            // 注意「复制播放器诊断信息」不收录：它只在 PLAYER_INTERACTION_DEBUG 打开时才出现，
            // 收进来会让搜索把人送进一个点了没反应的项。
            text(R.string.setting_player_back_key_exit),
            text(R.string.player_port_seek_step_name),
            text(R.string.player_port_danmaku_block_words_name),
            text(R.string.setting_player_danmaku_shortvideo_separate),
            text(R.string.setting_player_danmaku_shortvideo_maxline),
            text(R.string.setting_player_danmaku_shortvideo_allow_overlap),
            text(R.string.setting_player_danmaku_speed)
        ))
        // 「跳过设置」二级页（26.11.14）：片头片尾 + 广告片段逐类别三档 + 进度条标记 + 「不准」记录。
        // 名字必须与页内 SettingSection.name 严格一致（类别名直接取 SponsorCategoryLabels）。
        val skipLeaves = ArrayList<String>(18)
        skipLeaves.add(text(R.string.player_skip_page_name))
        skipLeaves.add(text(R.string.player_s_skip_mode_name))
        skipLeaves.add(text(R.string.player_sb_clear_ignored_name))
        skipLeaves.add(text(R.string.player_skip_marks_body_name))
        skipLeaves.add(text(R.string.player_skip_marks_poi_name))
        for (category in SponsorCategoryLabels.selectable) {
            skipLeaves.add(text(SponsorCategoryLabels.nameRes(category)))
        }
        addLeafItems(list, text(R.string.player_skip_page_name), SettingSkipActivity::class.java, skipLeaves)
        list += Entry(
            text(R.string.player_skip_page_name),
            text(R.string.player_skip_page_desc),
            // 26.11.15：标题加「（实验性）」后仍要能被「跳广告」这类口语搜到。
            "跳广告 广告片段 跳过广告 广告 实验 实验性"
        ) { a -> a.startActivity(Intent(a, SettingSkipActivity::class.java)) }
        // 「标记哪些类别」：进度条条子的独立勾选页（与三档无关），从「跳过设置」进去。
        val markLeaves = ArrayList<String>(11)
        markLeaves.add(text(R.string.player_mark_page_name))
        for (category in SponsorCategoryLabels.selectable) {
            markLeaves.add(text(SponsorCategoryLabels.nameRes(category)))
        }
        addLeafItems(
            list,
            text(R.string.player_mark_page_name),
            SettingMarkCategoryActivity::class.java,
            markLeaves
        )
        list += Entry(
            text(R.string.player_mark_page_name),
            text(R.string.player_skip_marks_cats_name)
        ) { a -> a.startActivity(Intent(a, SettingMarkCategoryActivity::class.java)) }
        // 注：原来「显示旋转/弹幕/清晰度/分P/锁定按钮」五个开关已在 26.11.06 删除，
        // 按钮显不显示改由「播放界面布局」决定（放没放进槽位就是开关），所以这里不再收录。

        // ---- 开发者工具（仅 Debug）----
        // 用 BuildConfig.DEBUG（编译期常量）而不是 BiliTerminal.isDebugBuild()（运行期判断）：
        // release 下 R8 会把这一整个分支折叠掉，TestActivity 随之成为无用类被 strip。
        if (BuildConfig.DEBUG) {
            list += Entry(
                text(R.string.settingtext_settingmainactivity_017),
                text(R.string.settingtext_settingmainactivity_018)
            ) { a -> openGroup(a, "dev", text(R.string.settingtext_settingmainactivity_017)) }
            list += Entry(text(R.string.settingtext_settinggroupactivity_084), text(R.string.settingtext_settinggroupactivity_085)) { a ->
                a.startActivity(Intent(a, TestActivity::class.java))
            }
            list += Entry(text(R.string.settingtext_settinggroupactivity_086), text(R.string.settingtext_settinggroupactivity_087)) { a ->
                a.startActivity(Intent(a, TodoListActivity::class.java))
            }
            // 26.11.15 补收录的开发者项：三个 Logu 开关在页内是尚未进 strings.xml 的中文字面量，
            // 索引必须用同一字面量当匹配键；其余四项都取资源，页内也是同一批资源。
            val devGroupTitle = text(R.string.settingtext_settingmainactivity_017)
            for (logu in listOf("允许Logu.v", "允许Logu.d", "允许Logu.i")) {
                list += Entry(logu, text(R.string.settingtext_settingsindex_096)) { a ->
                    openGroup(a, "dev", devGroupTitle, logu)
                }
            }
            list += Entry(
                text(R.string.settingtext_settinggroupactivity_092),
                text(R.string.settingtext_settingsindex_097)
            ) { a -> openGroup(a, "dev", devGroupTitle, text(R.string.settingtext_settinggroupactivity_092)) }
            list += Entry(
                text(R.string.settingtext_settinggroupactivity_093),
                text(R.string.settingtext_settingsindex_098)
            ) { a -> openGroup(a, "dev", devGroupTitle, text(R.string.settingtext_settinggroupactivity_093)) }
            list += Entry(
                text(R.string.settingtext_settinggroupactivity_089),
                text(R.string.settingtext_settingsindex_099)
            ) { a -> openGroup(a, "dev", devGroupTitle, text(R.string.settingtext_settinggroupactivity_089)) }
            list += Entry(
                text(R.string.settingtext_settinggroupactivity_090),
                text(R.string.settingtext_settingsindex_100)
            ) { a -> openGroup(a, "dev", devGroupTitle, text(R.string.settingtext_settinggroupactivity_090)) }
        }

        return list
    }

    private fun openGroup(activity: Activity, type: String, title: String, highlight: String? = null) {
        val intent = Intent(activity, SettingGroupActivity::class.java)
        intent.putExtra("group_type", type)
        intent.putExtra("group_title", SettingText.resolve(activity, title))
        if (highlight != null) intent.putExtra("highlight", highlight)
        activity.startActivity(intent)
    }

    private fun openPage(activity: Activity, cls: Class<*>, highlight: String) {
        val intent = Intent(activity, cls)
        intent.putExtra("highlight", highlight)
        activity.startActivity(intent)
    }

    private fun addLeafItems(list: MutableList<Entry>, page: String, cls: Class<*>, items: List<String>) {
        for (name in items) {
            list += Entry(name, page) { a -> openPage(a, cls, name) }
        }
    }
}
