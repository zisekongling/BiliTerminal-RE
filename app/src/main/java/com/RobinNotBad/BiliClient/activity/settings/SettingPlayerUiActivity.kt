package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.player.PlayerButtons
import com.RobinNotBad.BiliClient.player.PlayerSettingsGroups
import com.RobinNotBad.BiliClient.player.PlayerUiLayout
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 设置 →「播放与播放器」的第一屏：播放器按钮的排布。
 *
 * 这里不做图形预览，就是一类「点一下改一下」的设置：左边一列、右边一列、底部两侧各一节，
 * 每一格一行，点一行能换按钮 / 挪位置 / 改成一组 / 移除；每列末尾一行「加一个按钮」。
 * 「画面元素」那一节是进度条、在线人数、播放手势、标题栏、播放与暂停这些没有自己按钮、
 * 但点哪都能调一堆设置的地方。
 *
 * 排布本身存在 [SettingsKeys.PLAYER_UI_LAYOUT]，播放页 [com.RobinNotBad.BiliClient.activity.player.PlayerActivity]
 * 用同一份数据渲染真界面，所以这里改完播放时就是这个样子。
 */
class SettingPlayerUiActivity : BaseActivity() {

    private lateinit var contentList: RecyclerView
    private lateinit var cardPanel: LinearLayout
    private lateinit var cardScrim: View
    private lateinit var cardTitle: TextView
    private lateinit var cardList: RecyclerView

    private var cardSections: List<SettingSection> = emptyList()
    private var cardAdapter: SettingsAdapter? = null

    private var layout: PlayerUiLayout.Layout = PlayerUiLayout.DEFAULT

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_setting_player_ui) { _, _ ->
            contentList = findViewById(R.id.contentList)
            cardPanel = findViewById(R.id.cardPanel)
            cardTitle = findViewById(R.id.cardTitle)
            cardList = findViewById(R.id.cardList)
            cardScrim = findViewById(R.id.cardScrim)
            cardScrim.setOnClickListener { hideCard() }

            findViewById<View>(R.id.pageName).setOnClickListener { finish() }
            findViewById<View>(R.id.btnFull).setOnClickListener { openFullSettings() }

            contentList.layoutManager = getLayoutManager()
            cardList.layoutManager = getLayoutManager()

            reloadLayout()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::contentList.isInitialized) reloadLayout()
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
        rebuildRows()
    }

    // ---- 主列表 ----

    /**
     * 把当前排布摊成一张列表：四个区各一节，每格一行，末尾一行是「加一个按钮」。
     *
     * 列标题里带上容量（「左列（最多 5 格）」），行里的 desc 写第几格，全部走 [R.string]。
     */
    private fun rebuildRows() {
        val rows = ArrayList<SettingSection>()
        for (region in PlayerUiLayout.Region.values()) {
            rows += noteSection(
                getString(
                    R.string.setting_player_ui_region_title,
                    getString(regionNameRes(region)),
                    region.capacity
                )
            )
            val slots = PlayerUiLayout.slots(layout, region)
            if (slots.isEmpty()) {
                rows += noteSection(getString(R.string.setting_player_ui_region_empty))
            }
            slots.forEachIndexed { index, slot ->
                rows += when (slot) {
                    is PlayerUiLayout.Slot.Button -> {
                        val button = PlayerButtons.byKey(slot.key)
                        navRow(
                            button?.let { getString(it.nameRes) } ?: slot.key,
                            slotDesc(region, index, button),
                            button?.iconRes ?: R.drawable.icon_menu
                        ) { showSlotMenu(region, index) }
                    }
                    is PlayerUiLayout.Slot.Group -> navRow(
                        getString(R.string.setting_player_ui_group_count, slot.keys.size),
                        slot.keys.joinToString("、") { key ->
                            PlayerButtons.byKey(key)?.let { getString(it.nameRes) } ?: key
                        },
                        R.mipmap.morehide
                    ) { showGroupCard(region, index, slot) }
                }
            }
            if (!PlayerUiLayout.isFull(layout, region)) {
                rows += navRow(
                    getString(R.string.setting_player_ui_add_button),
                    getString(R.string.setting_player_ui_region_capacity, region.capacity),
                    android.R.drawable.ic_input_add
                ) { showPicker(region, -1, PickMode.AddSlot) }
            }
        }

        rows += noteSection(getString(R.string.setting_player_ui_label_elements))
        rows += navRow(
            getString(R.string.setting_player_ui_label_progress),
            getString(R.string.setting_player_ui_element_desc),
            R.drawable.icon_menu
        ) { onElementClick("progress") }
        rows += navRow(
            getString(R.string.setting_player_ui_label_online),
            getString(R.string.setting_player_ui_element_desc),
            R.drawable.icon_menu
        ) { onElementClick("online") }
        rows += navRow(
            getString(R.string.setting_player_ui_label_blank),
            getString(R.string.setting_player_ui_element_desc),
            R.drawable.icon_menu
        ) { onElementClick("blank") }
        rows += navRow(
            getString(R.string.setting_player_ui_label_play),
            getString(R.string.setting_player_ui_element_desc),
            R.drawable.icon_menu
        ) { onElementClick("play") }

        rows += navRow(
            getString(R.string.setting_player_ui_choose_player),
            "",
            R.drawable.icon_menu
        ) { startActivity(Intent(this, SettingPlayerChooseActivity::class.java)) }
        rows += navRow(
            getString(R.string.setting_player_ui_reset),
            "",
            android.R.drawable.ic_menu_revert
        ) { resetLayout() }

        contentList.adapter = SettingsAdapter(this, rows)
    }

    private fun regionNameRes(region: PlayerUiLayout.Region) = when (region) {
        PlayerUiLayout.Region.LEFT -> R.string.setting_player_ui_region_left
        PlayerUiLayout.Region.RIGHT -> R.string.setting_player_ui_region_right
        PlayerUiLayout.Region.BOTTOM_LEFT -> R.string.setting_player_ui_region_bottom_left
        PlayerUiLayout.Region.BOTTOM_RIGHT -> R.string.setting_player_ui_region_bottom_right
    }

    private fun resetLayout() {
        PlayerUiLayout.save(PlayerUiLayout.DEFAULT) { key, value ->
            SharedPreferencesUtil.putString(key, value)
        }
        hideCard()
        reloadLayout()
    }

    private fun openFullSettings() {
        startActivity(Intent(this, SettingTerminalPlayerActivity::class.java))
    }

    // ---- 点按钮 ----

    private fun showSlotMenu(region: PlayerUiLayout.Region, index: Int) {
        val slot = PlayerUiLayout.slots(layout, region).getOrNull(index) ?: return
        val key = (slot as? PlayerUiLayout.Slot.Button)?.key ?: return
        showCard(
            PlayerButtons.byKey(key)?.let { getString(it.nameRes) } ?: getString(R.string.setting_player_ui_group_title),
            listOf(
                editRow(getString(R.string.setting_player_ui_replace)) {
                    showPicker(region, index, PickMode.ReplaceSlot)
                },
                editRow(getString(R.string.setting_player_ui_make_group)) {
                    applyLayout(PlayerUiLayout.replaceAt(layout, region, index, PlayerUiLayout.Slot.Group(listOf(key))))
                },
                editRow(getString(R.string.setting_player_ui_move_up)) {
                    applyLayout(PlayerUiLayout.move(layout, region, index, index - 1))
                },
                editRow(getString(R.string.setting_player_ui_move_down)) {
                    applyLayout(PlayerUiLayout.move(layout, region, index, index + 1))
                },
                editRow(getString(R.string.setting_player_ui_remove)) {
                    applyLayout(PlayerUiLayout.removeAt(layout, region, index))
                }
            )
        )
    }

    /** 一组按钮的管理卡：点成员进它的设置，另有加成员、清成员、挪位置、整组移除。 */
    private fun showGroupCard(region: PlayerUiLayout.Region, index: Int, slot: PlayerUiLayout.Slot.Group) {
        val rows = ArrayList<SettingSection>()
        rows += noteSection(getString(R.string.setting_player_ui_group_members))
        slot.keys.forEach { key ->
            val button = PlayerButtons.byKey(key)
            val name = button?.let { getString(it.nameRes) } ?: key
            rows += navRow(
                getString(R.string.setting_player_ui_group_member_settings, name),
                if (button?.mayHide == true) getString(R.string.setting_player_ui_may_hide) else "",
                button?.iconRes ?: R.drawable.icon_menu
            ) { showButtonCard(key) }
        }
        rows += dividerSection()
        rows += editRow(getString(R.string.setting_player_ui_group_add_member)) {
            showPicker(region, index, PickMode.AddGroupMember)
        }
        rows += editRow(getString(R.string.setting_player_ui_group_drop_member)) {
            showGroupRemoveCard(region, index, slot)
        }
        rows += editRow(getString(R.string.setting_player_ui_move_up)) {
            applyLayout(PlayerUiLayout.move(layout, region, index, index - 1))
        }
        rows += editRow(getString(R.string.setting_player_ui_move_down)) {
            applyLayout(PlayerUiLayout.move(layout, region, index, index + 1))
        }
        rows += editRow(getString(R.string.setting_player_ui_remove)) {
            applyLayout(PlayerUiLayout.removeAt(layout, region, index))
        }
        showCard(getString(R.string.setting_player_ui_group_title), rows)
    }

    /** 组内成员逐条移除（单独一张卡，免得和「点进去看设置」抢同一条）。 */
    private fun showGroupRemoveCard(region: PlayerUiLayout.Region, index: Int, slot: PlayerUiLayout.Slot.Group) {
        val rows = slot.keys.map { key ->
            val name = PlayerButtons.byKey(key)?.let { getString(it.nameRes) } ?: key
            editRow(getString(R.string.setting_player_ui_group_remove_member, name)) {
                val left = slot.keys - key
                applyLayout(
                    if (left.isEmpty()) PlayerUiLayout.removeAt(layout, region, index)
                    else PlayerUiLayout.replaceAt(layout, region, index, PlayerUiLayout.Slot.Group(left))
                )
            }
        }
        showCard(getString(R.string.setting_player_ui_group_drop_member), rows)
    }

    /** 选按钮卡：加空槽位 / 换掉某一格 / 往组里加成员，三种模式共用一张卡。 */
    private fun showPicker(region: PlayerUiLayout.Region, index: Int, mode: PickMode) {
        if (mode == PickMode.AddSlot && PlayerUiLayout.isFull(layout, region)) {
            toast(getString(R.string.setting_player_ui_region_full))
            return
        }
        val title = when (mode) {
            PickMode.AddSlot -> getString(R.string.setting_player_ui_pick_button)
            PickMode.ReplaceSlot -> getString(R.string.setting_player_ui_pick_button_replace)
            PickMode.AddGroupMember -> getString(R.string.setting_player_ui_group_add_member)
        }
        val rows = PlayerButtons.ALL.map { button ->
            navRow(
                getString(button.nameRes),
                if (button.mayHide) getString(R.string.setting_player_ui_may_hide) else "",
                button.iconRes
            ) {
                val picked = PlayerUiLayout.Slot.Button(button.key)
                applyLayout(
                    when (mode) {
                        PickMode.AddSlot -> PlayerUiLayout.add(layout, region, picked)
                        PickMode.ReplaceSlot -> PlayerUiLayout.replaceAt(layout, region, index, picked)
                        PickMode.AddGroupMember -> {
                            val group = PlayerUiLayout.slots(layout, region).getOrNull(index) as? PlayerUiLayout.Slot.Group
                            if (group == null) layout
                            else PlayerUiLayout.replaceAt(
                                layout, region, index, PlayerUiLayout.Slot.Group(group.keys + button.key)
                            )
                        }
                    }
                )
            }
        }
        showCard(title, rows)
    }

    /** 布局改了就保存 + 重排列表 + 收起卡片（手表屏幕小，改完要马上看见结果）。 */
    private fun applyLayout(newLayout: PlayerUiLayout.Layout) {
        if (newLayout == layout) {
            hideCard()
            return
        }
        layout = newLayout
        PlayerUiLayout.save(layout) { key, value -> SharedPreferencesUtil.putString(key, value) }
        rebuildRows()
        hideCard()
    }

    private enum class PickMode { AddSlot, ReplaceSlot, AddGroupMember }

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

    private fun noteSection(text: String) = SettingSection("title", text, "", "", "")

    private fun dividerSection() = SettingSection("divider", "", "", "", "")

    /** 一行「位置 → 按钮」：左边图标、上面标题、下面一行说明，点整行进它自己的卡。 */
    private fun navRow(title: String, desc: String, @DrawableRes iconRes: Int, onClick: () -> Unit) =
        SettingSection("nav", title, "", desc, "", SettingsAdapter.NavExtra(iconRes, { onClick() }))

    /** 槽位行的副标题：先写「哪一列第几格」，按钮在某些视频里会被收起时再加一句注意。 */
    private fun slotDesc(region: PlayerUiLayout.Region, index: Int, button: PlayerButtons.Button?): String {
        val base = getString(R.string.setting_player_ui_region_desc, getString(regionNameRes(region)), index + 1)
        return if (button?.mayHide == true) {
            base + " · " + getString(R.string.setting_player_ui_may_hide)
        } else {
            base
        }
    }

    /** 一行可点的动作（`button` 类型，标题就是动作名）。 */
    private fun editRow(text: String, onClick: () -> Unit) =
        SettingSection("button", text, "", "", "", SettingsAdapter.ButtonExtra { onClick() })

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    // ---- 卡片 ----

    private fun showCard(title: String, sections: List<SettingSection>) {
        cardTitle.text = title
        cardSections = sections
        val adapter = SettingsAdapter(this, sections)
        cardAdapter = adapter
        cardList.adapter = adapter
        // 上一次收起动画可能还在跑，它的 withEndAction 会把卡片设成 GONE，必须先掐掉
        cardPanel.animate().cancel()
        cardPanel.visibility = View.VISIBLE
        cardScrim.visibility = View.VISIBLE
        cardScrim.bringToFront()
        cardPanel.bringToFront()
        // 卡片初始在屏幕外，量到高度后滑上来（GONE 期间 height 是 0，不能在 showCard 之前算）
        cardPanel.post {
            if (cardPanel.visibility != View.VISIBLE) return@post
            cardPanel.translationY = cardPanel.height.toFloat()
            cardPanel.animate().translationY(0f).setDuration(180L).start()
        }
    }

    private fun hideCard() {
        if (cardPanel.visibility != View.VISIBLE) return
        cardPanel.animate().cancel()
        cardScrim.visibility = View.GONE
        cardPanel.animate().translationY(cardPanel.height.toFloat()).setDuration(160L)
            .withEndAction {
                cardPanel.visibility = View.GONE
                cardList.adapter = null
                cardAdapter = null
                cardSections = emptyList()
            }.start()
    }


}
