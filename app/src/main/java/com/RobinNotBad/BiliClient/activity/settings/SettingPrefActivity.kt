package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.ui.widget.RotaryEncoderSupport
import com.RobinNotBad.BiliClient.util.LongPressPrefs
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TopbarLongPressPrefs

class SettingPrefActivity : RefreshListActivity() {

    /** 设置项全表。`list_choose` 的选择结果要靠它按 position 反查回写（见 [onActivityResult]）。 */
    private var sections: List<SettingSection> = emptyList()
    private var settingsAdapter: SettingsAdapter? = null

    /** 长按顶部标题栏三模式的显示名与取值，顺序一一对应。显示名只是「键」，真正的文案在 strings.xml。 */
    private val topbarModeNames = listOf("不响应", "仅视频详情页", "全部页面")
    private val topbarModeValues = listOf(
        TopbarLongPressPrefs.MODE_OFF,
        TopbarLongPressPrefs.MODE_VIDEO_ONLY,
        TopbarLongPressPrefs.MODE_ALL
    )

    /**
     * 造一个「点进去选一项」的设置行。
     *
     * 用 `list_choose` 而不是 `choose`：后者是两选一的 RadioButton、存 Boolean，承载不了三态，
     * 也存不了字符串值。回写走 [applyListChooseResult]，和分组页共用同一套。
     */
    private fun listChoose(
        name: String,
        key: String,
        desc: String,
        default: String,
        displayNames: List<String>,
        actualValues: List<String>
    ) = SettingSection(
        "list_choose", name, key, desc, default,
        SettingsAdapter.ListChooseHolder.ListChooseExtra(displayNames, actualValues)
    )

    @SuppressLint("MissingInflatedId", "SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.pagename_settingprefactivity_001))
        Log.e("debug", "进入偏好设置")

        sections = ArrayList<SettingSection>().apply {
            add(SettingSection("title", "功能", "", "", ""))
            add(SettingSection("switch", "长按复制", SettingsKeys.COPY_ENABLE, getString(R.string.desc_copy_enable), "true"))
            // 默认值引用 LongPressPrefs.DEFAULT_ENABLED，别再写死字面量：这个默认值一旦
            // 与适配器里读的默认值不一致，症状是「设置页显示开着、长按却不是面板」。
            add(SettingSection("switch", "长按打开操作面板", SettingsKeys.LONG_PRESS_PANEL_ENABLE,
                getString(R.string.desc_long_press_panel_enable), LongPressPrefs.DEFAULT_ENABLED.toString()))
            // 长按顶部标题栏三模式：不响应 / 仅视频详情页 / 全部页面。
            // 默认值取 TopbarLongPressPrefs.mode()——它已经处理了 26.10.19 旧布尔开关的迁移，
            // 老用户升级后设置页显示的仍是「实际生效」的那一档，不会出现「显示开着、实际关着」。
            add(listChoose("长按顶部标题栏", SettingsKeys.TOPBAR_LONG_PRESS_MODE,
                getString(R.string.desc_topbar_long_press_mode),
                TopbarLongPressPrefs.mode(), topbarModeNames, topbarModeValues))
            add(SettingSection("switch", "创作中心", SettingsKeys.CREATIVE_ENABLE, getString(R.string.desc_creative_enable), "true"))
            add(SettingSection("switch", "搜索建议", SettingsKeys.SEARCH_SUGGESTIONS_ENABLE,
                getString(R.string.desc_search_suggestions_enable), "true"))
            // 26.11.21 反馈：没有关闭搜索历史的入口。26.11.23 起这个开关只控制**显示**
            // （用户口径：关掉仅隐藏历史区，该记录的还是记录），所以这里不再挂 clear() 回调——
            // 早先「关掉即清空」的做法已随 SearchHistoryStore 一起撤掉。
            add(SettingSection("switch", "搜索历史", SettingsKeys.SEARCH_HISTORY_ENABLE,
                getString(R.string.desc_search_history_enable), "true"))
            add(SettingSection("switch", "默认搜索内容", SharedPreferencesUtil.SEARCH_DEFAULT_CONTENT_ENABLE,
                getString(R.string.desc_search_default_content_enable), "false"))
            add(SettingSection("switch", "识别链接", SharedPreferencesUtil.LINK_ENABLE, getString(R.string.desc_link_enable), "true"))
            add(SettingSection("switch", "隐私模式", SharedPreferencesUtil.PRIVACY_MODE,
                getString(R.string.desc_privacy_mode), "false"))

            add(SettingSection("title", "更新提醒", "", "", ""))
            add(SettingSection("switch", "新动态数量检查", SharedPreferencesUtil.DYNAMIC_UPDATE_CHECK_ENABLE,
                getString(R.string.desc_dynamic_update_check_enable), "true"))
            add(SettingSection("switch", "消息数量检查", SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE,
                getString(R.string.desc_message_update_check_enable), "true"))
            // 注意：「新消息通知」「追番更新提醒」两项已移到第一层级的「通知设置」分组
            // （`SettingGroupActivity.buildNotifyGroup()`）。别在这里加回来——两个入口指向
            // 同一个键，用户在一处改了、另一处不会刷新，看起来就像设置没生效。
            add(SettingSection("switch", "最近更新的UP主", SharedPreferencesUtil.RECENT_UP_DISPLAY_ENABLE,
                getString(R.string.desc_recent_up_display_enable), "true"))
            add(SettingSection("switch", "私信自动已读", SharedPreferencesUtil.PRIVATE_MSG_AUTO_READ_ENABLE,
                getString(R.string.desc_private_msg_auto_read_enable), "true"))
            // 反馈 #121：进私信页那句「私信有可能被拦截」的提示，默认仍然显示
            add(SettingSection("switch", getString(R.string.settingtext_settingprefactivity_033),
                SettingsKeys.PRIVATE_MSG_INTERCEPT_TIP_ENABLE,
                getString(R.string.desc_private_msg_intercept_tip), "true"))
            add(SettingSection("switch", "夜深了", SharedPreferencesUtil.NIGHT_REMINDER_ENABLE,
                getString(R.string.desc_night_reminder), "true"))
            add(SettingSection("switch", "后台自动检查更新", SharedPreferencesUtil.AUTO_UPDATE_CHECK_ENABLE,
                getString(R.string.text_update_background_check_desc), "true"))

            add(SettingSection("title", "交互优化", "", "", ""))
            add(SettingSection("switch", "禁用返回键", SettingsKeys.BACK_DISABLE, getString(R.string.desc_back_disable), "false"))
            add(SettingSection("switch", "禁止视频在相册中显示", SettingsKeys.SAVE_BAN_GALLERY, getString(R.string.desc_ban_gallery), "true"))
            add(SettingSection("switch", "请求JPG格式图片", SettingsKeys.IMAGE_REQUEST_JPG,
                getString(R.string.desc_img_request_jpg), "false"))
            // 默认值必须与消费方 ImageAutoLoadScrollListener 一致（那边是 getBoolean(..., true)）：
            // 写 "false" 时界面显示「关」、实际却按「开」在省图，用户会以为图片加载不出来（26.11.08 修）。
            add(SettingSection("switch", "翻动时不加载图片", SettingsKeys.IMAGE_NO_LOAD_ONSCROLL,
                getString(R.string.desc_img_no_load_onscroll), "true"))
            add(SettingSection("switch", "异步加载布局", SharedPreferencesUtil.ASYNC_INFLATE_ENABLE,
                getString(R.string.desc_async_inflate_enable), "true"))
            add(SettingSection("switch", "新提示信息显示方式", SharedPreferencesUtil.SNACKBAR_ENABLE,
                "打开此选项，会启用新提示信息显示方式", "true"))

            add(SettingSection("title", "视觉", "", "", ""))
            add(SettingSection("switch", "我的关注列表分组", SharedPreferencesUtil.FOLLOW_GROUP_MODE,
                getString(R.string.desc_follow_group_mode), "false"))

            add(SettingSection("title", "表冠适配", "", "", ""))
            add(SettingSection("switch", "启用表冠适配", SettingsKeys.UI_ROTATORY_ENABLE,
                getString(R.string.setting_lab_ui_rotatory), "false"))
            // 26.11.18（反馈 #235 复测）：旧基准被 ROM 压成 1px 时，用户会把灵敏度填到几百才转得动；
            // 基准修回 64dp 之后同一个值就变成「每格满屏」（#235「一滑到底」）。这里先把越界的历史值
            // 就地收回 0.2–8，保证设置页显示的数字就是真正生效的数字（读取侧也收，见 sensitivityOf）。
            RotaryEncoderSupport.normalizeSensitivity(SettingsKeys.UI_ROTATORY_RECYCLER)
            RotaryEncoderSupport.normalizeSensitivity(SettingsKeys.UI_ROTATORY_SCROLL)
            // 反馈 #243：OPPO Watch 4 Pro 那类「表冠被报成鼠标滚轮」的机器，App 侧的手感怎么调都是猜；
            // 这个开关把表冠整体退回 26.11.16.4（最后一个正式版）的旧行为。26.11.23 起默认开启
            // （默认值必须与 RotaryEncoderSupport.isCompatMode() 里那个 `true` 一致）。
            add(SettingSection("switch", "表冠兼容模式", SettingsKeys.UI_ROTATORY_COMPAT,
                getString(R.string.desc_ui_rotatory_compat), "true"))
            add(SettingSection("input_float", "表冠适配灵敏度（Recycler）", SettingsKeys.UI_ROTATORY_RECYCLER,
                getString(R.string.desc_ui_rotatory_sensitivity), RotaryEncoderSupport.DEFAULT_MULTIPLE))
            add(SettingSection("input_float", "表冠适配灵敏度（Scroll）", SettingsKeys.UI_ROTATORY_SCROLL,
                getString(R.string.desc_ui_rotatory_sensitivity), RotaryEncoderSupport.DEFAULT_MULTIPLE))
        }

        recyclerView.setHasFixedSize(true)

        val adapter = SettingsAdapter(this, sections)
        settingsAdapter = adapter
        setAdapter(adapter)

        setRefreshing(false)

        scrollToHighlight(sections, intent.getStringExtra("highlight"))
    }

    /**
     * 承接 list_choose 的选择结果（本页以前没有 `list_choose`，所以没这个回调；不写就是「点了没反应」）。
     *
     * SettingsAdapter 用 `REQUEST_CODE_LIST_CHOOSE` 打开 [com.RobinNotBad.BiliClient.activity.ListChooseActivity]，
     * 回写逻辑与分组页共用 [applyListChooseResult]。
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        applyListChooseResult(requestCode, resultCode, data, sections) { settingsAdapter?.notifyItemChanged(it) }
    }
}
