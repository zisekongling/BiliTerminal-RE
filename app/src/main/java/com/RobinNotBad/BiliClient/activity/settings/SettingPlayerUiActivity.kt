package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.player.PlayerButtons
import com.RobinNotBad.BiliClient.player.PlayerSettingsGroups
import com.RobinNotBad.BiliClient.player.PlayerUiLayout
import com.RobinNotBad.BiliClient.ui.widget.FakePlayerPreview
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 设置 →「播放与播放器」的第一屏：一张假播放器界面。
 *
 * 假界面上的每个按钮、每块地方都是入口——点它就把「它管得着的那一组设置」从下半屏滑出来。
 * 这一版是**只读**的：只展示当前布局、改设置项，还不能加/删/挪槽位（那是下一步）。
 *
 * 布局本身存在 [SettingsKeys.PLAYER_UI_LAYOUT]，播放页 [com.RobinNotBad.BiliClient.activity.player.PlayerActivity]
 * 用同一份数据渲染真界面，所以这里看到的就是播放时的样子。
 */
class SettingPlayerUiActivity : BaseActivity() {

    private lateinit var preview: FakePlayerPreview
    private lateinit var cardPanel: LinearLayout
    private lateinit var cardTitle: TextView
    private lateinit var cardList: RecyclerView

    private var cardSections: List<SettingSection> = emptyList()
    private var cardAdapter: SettingsAdapter? = null

    private var layout: PlayerUiLayout.Layout = PlayerUiLayout.DEFAULT

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_setting_player_ui) { _, _ ->
            preview = findViewById(R.id.preview)
            cardPanel = findViewById(R.id.cardPanel)
            cardTitle = findViewById(R.id.cardTitle)
            cardList = findViewById(R.id.cardList)

            findViewById<View>(R.id.pageName).setOnClickListener { finish() }
            findViewById<View>(R.id.btnFull).setOnClickListener { openFullSettings() }
            findViewById<View>(R.id.btnChoosePlayer).setOnClickListener {
                startActivity(Intent(this, SettingPlayerChooseActivity::class.java))
            }
            findViewById<View>(R.id.btnReset).setOnClickListener { resetLayout() }

            cardList.layoutManager = getLayoutManager()

            preview.onSlotClick = { region, index -> onSlotClick(region, index) }
            preview.onElementClick = { element -> onElementClick(element) }

            // 16:9 缩略：高 = 宽 × 9 / 16。宽取预览自己的宽度——父容器带着左右 margin/padding，
            // 用父宽算出来会偏大一圈。
            preview.post {
                val width = preview.width
                if (width > 0) {
                    preview.layoutParams = preview.layoutParams.apply {
                        height = width * 9 / 16
                    }
                    preview.requestLayout()
                }
            }

            reloadLayout()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::preview.isInitialized) reloadLayout()
    }

    override fun onBackPressed() {
        if (::cardPanel.isInitialized && cardPanel.visibility == View.VISIBLE) {
            hideCard()
            return
        }
        super.onBackPressed()
    }

    /** 承接 `list_choose`（下拉选择项）的回写。与「完整设置」页共用同一个顶层函数。 */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        applyListChooseResult(requestCode, resultCode, data, cardSections) { cardAdapter?.notifyItemChanged(it) }
    }

    private fun reloadLayout() {
        layout = PlayerUiLayout.load(
            { key -> SharedPreferencesUtil.getString(key, "") },
            { key, value -> SharedPreferencesUtil.putString(key, value) }
        )
        preview.render(layout)
    }

    private fun resetLayout() {
        PlayerUiLayout.save(PlayerUiLayout.DEFAULT) { key, value ->
            SharedPreferencesUtil.putString(key, value)
        }
        reloadLayout()
        hideCard()
    }

    private fun openFullSettings() {
        startActivity(Intent(this, SettingTerminalPlayerActivity::class.java))
    }

    // ---- 点槽位 ----

    private fun onSlotClick(region: PlayerUiLayout.Region, index: Int) {
        val slot = PlayerUiLayout.slots(layout, region).getOrNull(index) ?: return
        when (slot) {
            is PlayerUiLayout.Slot.Group -> {
                // 组：先弹组里第一个按钮的设置（组本身的编辑在下一步做）
                val first = slot.keys.firstOrNull() ?: return
                showButtonCard(first)
            }
            is PlayerUiLayout.Slot.Button -> showButtonCard(slot.key)
        }
    }

    private fun showButtonCard(key: String) {
        val button = PlayerButtons.byKey(key) ?: return
        if (button.action == PlayerButtons.Action.OPEN_QUALITY) {
            startActivity(Intent(this, SettingQualityActivity::class.java))
            return
        }
        val groupKey = button.settingsGroup
        val sections = if (groupKey != null) {
            PlayerSettingsGroups.group(this, groupKey)?.sections.orEmpty()
        } else {
            emptyList()
        }
        showCard(
            getString(button.nameRes),
            sections.ifEmpty { listOf(noteSection(getString(R.string.setting_player_btn_nosettings))) }
        )
    }

    // ---- 点没有按钮对应的元素 ----

    private fun onElementClick(element: String) {
        when (element) {
            "progress" -> showCard(
                getString(R.string.setting_player_ui_label_progress),
                pick("显示高能进度条", "左右滑动控制进度", "滑动调进度灵敏度")
            )
            "online" -> showCard(
                getString(R.string.setting_player_ui_label_online),
                pick("显示实时人数")
            )
            "blank" -> showCard(
                getString(R.string.setting_player_ui_label_blank),
                PlayerSettingsGroups.group(this, PlayerSettingsGroups.GESTURE)?.sections.orEmpty() +
                    pick("视频可缩放", "缩放时可移动")
            )
            "title" -> showCard(
                getString(R.string.setting_player_ui_label_title),
                listOf(
                    switchSection(SettingsKeys.PLAYER_UI_SHOW_TITLE, getString(R.string.setting_player_ui_show_title)),
                    switchSection(SettingsKeys.PLAYER_UI_SHOW_BATTERY, getString(R.string.setting_player_ui_show_battery)),
                    switchSection(SettingsKeys.PLAYER_UI_SHOW_CLOCK, getString(R.string.setting_player_ui_show_clock))
                )
            )
            "play" -> showCard(
                getString(R.string.setting_player_ui_label_play),
                PlayerSettingsGroups.group(this, PlayerSettingsGroups.DEFAULTS)?.sections.orEmpty()
            )
        }
    }

    /** 从全部设置项里按名字挑几项（名字就是 PlayerSettingsGroups 里的中文字面量）。 */
    private fun pick(vararg names: String): List<SettingSection> {
        val all = PlayerSettingsGroups.flatten(this)
        return names.mapNotNull { name -> all.firstOrNull { it.name == name } }
    }

    private fun switchSection(key: String, name: String) =
        SettingSection("switch", name, key, "", "true")

    private fun noteSection(text: String) = SettingSection("title", text, "", "", "")

    // ---- 卡片 ----

    private fun showCard(title: String, sections: List<SettingSection>) {
        cardTitle.text = title
        cardSections = sections
        val adapter = SettingsAdapter(this, sections)
        cardAdapter = adapter
        cardList.adapter = adapter
        cardPanel.visibility = View.VISIBLE
        // 卡片初始在屏幕外，量到高度后滑上来（GONE 期间 height 是 0，不能在 showCard 之前算）
        cardPanel.post {
            if (cardPanel.visibility != View.VISIBLE) return@post
            cardPanel.translationY = cardPanel.height.toFloat()
            cardPanel.animate().translationY(0f).setDuration(180L).start()
        }
    }

    private fun hideCard() {
        if (cardPanel.visibility != View.VISIBLE) return
        cardPanel.animate().translationY(cardPanel.height.toFloat()).setDuration(160L)
            .withEndAction {
                cardPanel.visibility = View.GONE
                cardList.adapter = null
                cardAdapter = null
                cardSections = emptyList()
            }.start()
    }
}
