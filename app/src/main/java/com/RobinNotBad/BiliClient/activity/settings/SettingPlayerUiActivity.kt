package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.player.PlayerButtons
import com.RobinNotBad.BiliClient.player.PlayerSettingsGroups
import com.RobinNotBad.BiliClient.player.PlayerUiLayout
import com.RobinNotBad.BiliClient.ui.widget.PlayerControlsPreview
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil

/**
 * 设置 →「播放与播放器」的第一屏：一张真实播放器界面。
 *
 * 这张界面不是仿画的图，而是把播放页那套控件（[R.layout.activity_player] 的控制层）直接充进
 * 16:9 的框里，按钮的摆放由 [PlayerUiLayout] 决定、由 [com.RobinNotBad.BiliClient.player.PlayerButtonSlots]
 * 渲染——和播放页看到的完全一致。点按钮把「它管得着的那一组设置」从下半屏滑出来；长按某一格
 * 可以换按钮 / 改成一组 / 挪位置 / 移除，点每列末尾的 ＋ 加按钮，长按「更多」编辑这一组。
 *
 * 布局本身存在 [SettingsKeys.PLAYER_UI_LAYOUT]，播放页 [com.RobinNotBad.BiliClient.activity.player.PlayerActivity]
 * 用同一份数据渲染真界面，所以这里看到的就是播放时的样子。
 */
class SettingPlayerUiActivity : BaseActivity() {

    private lateinit var preview: PlayerControlsPreview
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

            preview.onButtonClick = { key -> showButtonCard(key) }
            preview.onButtonLongClick = { key -> onButtonLongClick(key) }
            preview.onMoreLongClick = { region, index ->
                val slot = PlayerUiLayout.slots(layout, region).getOrNull(index) as? PlayerUiLayout.Slot.Group
                if (slot != null) showGroupCard(region, index, slot)
            }
            preview.onAddClick = { region -> showPicker(region, -1, PickMode.AddSlot) }
            preview.onElementClick = { element -> onElementClick(element) }

            // 缩略框的比例直接取**当前设备屏幕分辨率**：PlayerControlsPreview 的画布就是真机分辨率，
            // 框和屏幕同比例缩放才不会变形，也才看得出「真机上就是这个样子」。
            // 第一次布局完成后才知道提示语/按钮行有多高，所以挂在全局布局回调里量一次就摘掉。
            preview.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (fitPreviewToScreen()) {
                        preview.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            })

            reloadLayout()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::preview.isInitialized) reloadLayout()
    }

    /**
     * 按屏幕比例把预览框缩到「提示语 + 底部两个按钮」之上。
     *
     * 比例必须与屏幕一致：`PlayerControlsPreview` 的画布就是真机分辨率，框不同比例会被拉变形。
     * 提示语和按钮行在内容超高时会被量成 0 高（LinearLayout 已经没空间分给它们了），所以这里
     * 不去读它们的高度，直接按固定预算 [BOTTOM_RESERVED_DP] 给它们留位置。
     */
    private fun fitPreviewToScreen(): Boolean {
        val metrics = resources.displayMetrics
        val aspect = metrics.heightPixels.toFloat() / metrics.widthPixels
        val parent = preview.parent as? View ?: return false
        val top = findViewById<View>(R.id.top)
        if (parent.width <= 0 || top.height <= 0) return false
        val maxHeight = metrics.heightPixels - top.height - ToolsUtil.dp2px(BOTTOM_RESERVED_DP)
        if (maxHeight <= 0) return false
        var width = parent.width
        var height = (width * aspect).toInt()
        if (height > maxHeight) {
            height = maxHeight
            width = (height / aspect).toInt()
        }
        if (width <= 0 || height <= 0) return false
        if (preview.layoutParams.width == width && preview.layoutParams.height == height) return true
        preview.layoutParams = preview.layoutParams.apply {
            this.width = width
            this.height = height
        }
        preview.requestLayout()
        return true
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

    // ---- 点按钮 ----

    /** 长按某个按钮：找到它摆在哪一格，弹出那一格的编辑菜单。 */
    private fun onButtonLongClick(key: String) {
        val found = findSlotOf(key)
        if (found == null) {
            // 组内展开出来的成员没有自己的格子，长按就当点了一下（进它的设置）
            showButtonCard(key)
            return
        }
        when (val slot = PlayerUiLayout.slots(layout, found.first).getOrNull(found.second)) {
            is PlayerUiLayout.Slot.Group -> showGroupCard(found.first, found.second, slot)
            is PlayerUiLayout.Slot.Button -> showSlotMenu(found.first, found.second)
            null -> Unit
        }
    }

    /** 某个按钮当前摆在哪一格；没有就返回 null（组内成员没有独立格子）。 */
    private fun findSlotOf(key: String): Pair<PlayerUiLayout.Region, Int>? {
        for (region in PlayerUiLayout.Region.values()) {
            val index = PlayerUiLayout.slots(layout, region)
                .indexOfFirst { it is PlayerUiLayout.Slot.Button && it.key == key }
            if (index >= 0) return region to index
        }
        return null
    }

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
            val name = PlayerButtons.byKey(key)?.let { getString(it.nameRes) } ?: key
            rows += editRow(getString(R.string.setting_player_ui_group_member_settings, name)) {
                showButtonCard(key)
            }
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
            editRow(getString(button.nameRes)) {
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

    /** 布局改了就保存 + 重画 + 收起卡片（手表屏幕小，改完要马上看见效果）。 */
    private fun applyLayout(newLayout: PlayerUiLayout.Layout) {
        if (newLayout == layout) {
            hideCard()
            return
        }
        layout = newLayout
        PlayerUiLayout.save(layout) { key, value -> SharedPreferencesUtil.putString(key, value) }
        preview.render(layout)
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

    private fun dividerSection() = SettingSection("divider", "", "", "", "")

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

    private companion object {
        /** 预览下方「提示语 + 两个按钮」的高度预算（dp），给预览框算可用高度用。 */
        const val BOTTOM_RESERVED_DP = 120f
    }
}
