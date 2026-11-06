package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.google.android.material.card.MaterialCardView

class SettingMainActivity : InstanceActivity() {

    private lateinit var settingsContainer: LinearLayout
    private lateinit var searchInput: EditText
    private val entries: List<SettingsIndex.Entry> = SettingsIndex.build()

    @SuppressLint("MissingInflatedId", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_setting_main) { _, _ ->
            settingsContainer = findViewById(R.id.settings_container)
            searchInput = findViewById(R.id.search_input)
            searchInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    filter(s.toString())
                }
            })
            buildGroups()
            findViewById<View>(R.id.scrollView).requestFocus()
        }
    }

    /** 根据搜索词重新渲染设置项列表：空则显示分组，否则显示匹配项。 */
    private fun filter(query: String) {
        settingsContainer.removeAllViews()
        val q = query.trim()
        if (q.isEmpty()) {
            buildGroups()
            return
        }
        for (entry in entries) {
            if (entry.name.contains(q) || entry.desc.contains(q)) {
                addResult(entry)
            }
        }
    }

    /** 添加一条搜索结果卡片，点击跳转到对应设置页/分组并定位。 */
    private fun addResult(entry: SettingsIndex.Entry) {
        val header = layoutInflater.inflate(R.layout.cell_group_header, settingsContainer, false) as MaterialCardView
        header.findViewById<ImageView>(R.id.group_icon).setImageResource(R.drawable.icon_search)
        header.findViewById<TextView>(R.id.group_title).text = SettingText.resolve(this, entry.name)
        header.findViewById<TextView>(R.id.group_desc).text = SettingText.resolve(this, entry.desc)
        header.findViewById<ImageView>(R.id.group_arrow).setImageResource(R.drawable.arrow_forward)
        header.setOnClickListener { entry.open(this) }
        settingsContainer.addView(header)
    }

    private fun buildGroups() {
        buildPlayerGroup()
        buildAccountGroup()
        buildUIGroup()
        buildContentGroup()
        buildNotifyGroup()
        buildDisguiseGroup()
        buildDownloadGroup()
        buildLabGroup()
        buildAboutGroup()
        buildDevGroup()
    }

    /**
     * 添加第一层级分组入口 —— 点击后跳转到 SettingGroupActivity
     */
    private fun addGroup(
        iconRes: Int,
        title: String,
        desc: String,
        groupType: String,
        target: Class<*>? = null
    ) {
        val header = layoutInflater.inflate(R.layout.cell_group_header, settingsContainer, false) as MaterialCardView
        header.findViewById<ImageView>(R.id.group_icon).setImageResource(iconRes)
        header.findViewById<TextView>(R.id.group_title).text = SettingText.resolve(this, title)
        header.findViewById<TextView>(R.id.group_desc).text = desc
        // 箭头始终向前，表示跳转
        header.findViewById<ImageView>(R.id.group_arrow).setImageResource(R.drawable.arrow_forward)

        header.setOnClickListener {
            if (target != null) {
                startActivity(Intent(this, target))
            } else {
                val intent = Intent(this, SettingGroupActivity::class.java)
                intent.putExtra("group_type", groupType)
                intent.putExtra("group_title", SettingText.resolve(this, title))
                startActivity(intent)
            }
        }

        settingsContainer.addView(header)
    }

    // ==================== 各分组入口构建 ====================

    private fun buildPlayerGroup() {
        addGroup(
            R.drawable.icon_player,
            "播放与播放器",
            "选择播放器、清晰度与内置播放器设置",
            // 注意：这个 "player" 是分组 id / 跳转标记，**不是** SharedPreferences 的
            // SettingsKeys.PLAYER 键，两者只是字面量碰巧相同，别顺手替换。
            "player",
            SettingPlayerChooseActivity::class.java
        )
    }

    private fun buildAccountGroup() {
        addGroup(
            R.drawable.icon_person,
            "账号与登录",
            "登录、切换账号与查看登录信息",
            "account"
        )
    }

    private fun buildUIGroup() {
        addGroup(
            R.drawable.icon_ui,
            "界面与外观",
            getString(R.string.settingtext_settingmainactivity_006),
            "ui"
        )
    }

    private fun buildContentGroup() {
        addGroup(
            R.drawable.icon_home,
            "内容与浏览",
            "菜单、搜索、详情页、评论区与偏好",
            "content"
        )
    }

    /**
     * 「通知设置」分组。
     *
     * 用户反馈「各种新加的通知应该在设置里新增一个大类-通知设置来关闭他」。原先这些开关埋在
     * 「内容与浏览 → 通用偏好 → 更新提醒」深处，等于没有入口。这里提到第一层级。
     */
    private fun buildNotifyGroup() {
        addGroup(
            R.drawable.icon_announcement,
            "通知设置",
            "新消息、追番更新等通知栏提醒的开关",
            GROUP_NOTIFY
        )
    }

    /**
     * 「伪装与安全」分组（issue #22）。
     *
     * 放第一层级而不是塞进「高级与实验」：这是给"被人走近"这种**需要立刻找到**的场景用的，
     * 埋太深等于没有。
     */
    private fun buildDisguiseGroup() {
        addGroup(
            R.drawable.icon_person,
            getString(R.string.text_setting_disguise_group),
            getString(R.string.desc_setting_disguise_index),
            GROUP_DISGUISE
        )
    }

    private fun buildDownloadGroup() {
        addGroup(
            R.drawable.icon_download,
            "缓存与下载",
            "下载引擎、缓存选项与存储路径",
            "download"
        )
    }

    private fun buildLabGroup() {
        addGroup(
            R.drawable.icon_laboratory,
            "高级与实验",
            "不保证能用或者用于开发调试的功能",
            "lab"
        )
    }

    private fun buildAboutGroup() {
        addGroup(
            R.drawable.icon_info,
            "关于与帮助",
            "版本信息、更新、公告与教程",
            "about"
        )
    }

    /**
     * 开发者工具分组 —— 仅在 Debug 构建下显示
     */
    private fun buildDevGroup() {
        if (!BiliTerminal.isDebugBuild()) return

        addGroup(
            R.drawable.icon_laboratory,
            "开发者工具",
            "功能测试、待办清单与调试日志",
            "dev"
        )
    }
}
