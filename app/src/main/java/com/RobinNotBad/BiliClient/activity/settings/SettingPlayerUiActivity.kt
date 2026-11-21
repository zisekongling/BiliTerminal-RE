package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
 * 每一格一行，点一行能换按钮 / 改成一组 / 挪位置 / 移除；每列末尾一行「加一组按钮」。
 * 收纳格不是另一种东西——它就是**一个没加成员的组**（26.11.15 统一）。
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
            findViewById<View>(R.id.cardClose).setOnClickListener { hideCard() }

            findViewById<View>(R.id.pageName).setOnClickListener { finish() }
            findViewById<View>(R.id.btnFull).setOnClickListener { openFullSettings() }

            contentList.layoutManager = getLayoutManager()
            cardList.layoutManager = getLayoutManager()
            limitCardHeight()

            reloadLayout()
        }
    }

    /**
     * 卡片限高：布局里写死 240dp，可手表屏幕本来就矮，240dp 能直接顶到状态栏、
     * 把遮罩区整块盖掉——那样手指没地方点、只剩返回键一条退路。
     * 这里取「240dp 与屏高六成」的较小值，上方永远留出能看见也能点掉的遮罩。
     */
    private fun limitCardHeight() {
        val metrics = resources.displayMetrics
        val maxByDp = (240 * metrics.density).toInt()
        val maxByScreen = (metrics.heightPixels * 0.6f).toInt()
        val height = maxByDp.coerceAtMost(maxByScreen)
        if (height <= 0) return
        val params = cardPanel.layoutParams ?: return
        params.height = height
        cardPanel.layoutParams = params
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
            // 底部两区紧贴着中间那个播放/暂停键，光看「中间暂停键左侧」这几个字还是容易懵，
            // 所以区首再补一句它到底贴在哪儿（26.11.15）
            regionNoteRes(region)?.let { rows += hintSection(getString(it)) }
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
                    is PlayerUiLayout.Slot.Group -> if (slot.keys.isEmpty()) {
                        // 空组＝收纳格（26.11.15 统一）：成员是「没摆进布局的按钮」，布局一变
                        // 内容就变，所以副标题写它的性质，而不是列成员。
                        navRow(
                            getString(R.string.setting_player_ui_group_empty),
                            getString(R.string.setting_player_ui_more_desc),
                            R.mipmap.morehide
                        ) { showGroupCard(region, index, slot) }
                    } else {
                        navRow(
                            getString(R.string.setting_player_ui_group_count, slot.keys.size),
                            slot.keys.joinToString("、") { key ->
                                PlayerButtons.byKey(key)?.let { getString(it.nameRes) } ?: key
                            },
                            R.mipmap.morehide
                        ) { showGroupCard(region, index, slot) }
                    }
                }
            }
            if (!PlayerUiLayout.isFull(layout, region)) {
                // 26.11.15 统一：每区末尾只剩这一个入口。加「一组按钮」就是加一格容器——
                // 不选成员时它就是收纳格（自动收没摆进布局的按钮），选了成员就是固定组；
                // 想摆单个按钮也是走这里（组里只放 1 个，照样不拆散）。
                rows += navRow(
                    getString(R.string.setting_player_ui_add_group),
                    getString(R.string.setting_player_ui_add_group_desc),
                    R.mipmap.morehide
                ) { showPicker(region, -1, PickMode.AddGroup) }
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

    /** 只有底部两区需要额外解释「它相对中间暂停键在哪儿」。 */
    private fun regionNoteRes(region: PlayerUiLayout.Region): Int? = when (region) {
        PlayerUiLayout.Region.BOTTOM_LEFT -> R.string.setting_player_ui_region_note_bottom_left
        PlayerUiLayout.Region.BOTTOM_RIGHT -> R.string.setting_player_ui_region_note_bottom_right
        else -> null
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
        val rows = ArrayList<SettingSection>()
        rows += editRow(getString(R.string.setting_player_ui_replace)) {
            showPicker(region, index, PickMode.ReplaceSlot)
        }
        rows += editRow(getString(R.string.setting_player_ui_make_group)) {
            applyLayout(PlayerUiLayout.replaceAt(layout, region, index, PlayerUiLayout.Slot.Group(listOf(key))))
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
        showCard(
            PlayerButtons.byKey(key)?.let { getString(it.nameRes) } ?: getString(R.string.setting_player_ui_group_title),
            rows
        )
    }

    /**
     * 一组按钮的管理卡：点成员进它的设置，另有加成员、移成员、挪位置、整组移除。
     *
     * 组成员为空时这一格就是**收纳格**（成员＝没摆进布局的按钮，跟着布局自动变），
     * 所以空组不给「移成员」（没成员可移），改成提示它的性质。
     */
    private fun showGroupCard(region: PlayerUiLayout.Region, index: Int, slot: PlayerUiLayout.Slot.Group) {
        val rows = ArrayList<SettingSection>()
        if (slot.keys.isEmpty()) {
            rows += hintSection(getString(R.string.setting_player_ui_more_desc))
        } else {
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
        }
        rows += dividerSection()
        rows += editRow(getString(R.string.setting_player_ui_group_add_member)) {
            showPicker(region, index, PickMode.AddGroupMember)
        }
        if (slot.keys.isNotEmpty()) {
            rows += editRow(getString(R.string.setting_player_ui_group_drop_member)) {
                showGroupRemoveCard(region, index, slot)
            }
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

    /**
     * 组内成员逐条移除（单独一张卡，免得和「点进去看设置」抢同一条）。
     *
     * 移掉最后一个成员时**这一格留着**、变成空组＝收纳格（26.11.15，用户定下的：
     * 组里 0 个按钮也不拆散）。用户想彻底删掉这一格，回上一张卡点「移除这一格」。
     */
    private fun showGroupRemoveCard(region: PlayerUiLayout.Region, index: Int, slot: PlayerUiLayout.Slot.Group) {
        val rows = slot.keys.map { key ->
            val name = PlayerButtons.byKey(key)?.let { getString(it.nameRes) } ?: key
            editRow(getString(R.string.setting_player_ui_group_remove_member, name)) {
                applyLayout(
                    PlayerUiLayout.replaceAt(
                        layout, region, index,
                        PlayerUiLayout.Slot.Group(slot.keys - key)
                    )
                )
            }
        }
        showCard(getString(R.string.setting_player_ui_group_drop_member), rows)
    }

    /** 选按钮卡：加一格（组 / 收纳格）/ 换掉某一格 / 往组里加成员，三种模式共用一张卡。 */
    private fun showPicker(region: PlayerUiLayout.Region, index: Int, mode: PickMode) {
        // 只有「新加一格」才会多占一格；换按钮、往已有组里加成员都不占新格子，
        // 所以那一列满了也不该挡住它们（26.11.15 修：原写法会把满列上的加成员一并挡掉）。
        if (mode == PickMode.AddGroup && PlayerUiLayout.isFull(layout, region)) {
            toast(getString(R.string.setting_player_ui_region_full))
            return
        }
        val title = when (mode) {
            PickMode.AddGroup -> getString(R.string.setting_player_ui_pick_button)
            PickMode.ReplaceSlot -> getString(R.string.setting_player_ui_pick_button_replace)
            PickMode.AddGroupMember -> getString(R.string.setting_player_ui_group_add_member)
        }
        val rows = ArrayList<SettingSection>()
        // 加一格时第一行是「先不放按钮」：这一格就是收纳格，空着也有用（自动收没摆进布局的按钮）
        if (mode == PickMode.AddGroup) {
            rows += editRow(getString(R.string.setting_player_ui_add_group_empty)) {
                applyLayout(PlayerUiLayout.add(layout, region, PlayerUiLayout.Slot.Group(emptyList())))
            }
            rows += dividerSection()
        }
        PlayerButtons.ALL.map { button ->
            navRow(
                getString(button.nameRes),
                if (button.mayHide) getString(R.string.setting_player_ui_may_hide) else "",
                button.iconRes
            ) {
                when (mode) {
                    PickMode.AddGroup -> applyLayout(
                        PlayerUiLayout.add(layout, region, PlayerUiLayout.Slot.Group(listOf(button.key)))
                    )
                    PickMode.ReplaceSlot -> applyLayout(
                        PlayerUiLayout.replaceAt(layout, region, index, PlayerUiLayout.Slot.Button(button.key))
                    )
                    PickMode.AddGroupMember -> {
                        val group = PlayerUiLayout.slots(layout, region).getOrNull(index) as? PlayerUiLayout.Slot.Group
                        when {
                            group == null -> hideCard()
                            // 同一按钮在一个组里只留一份：多份会渲染出多个影子，点了互相抢状态
                            button.key in group.keys -> toast(
                                getString(R.string.setting_player_ui_group_member_exists, getString(button.nameRes))
                            )
                            else -> applyLayout(
                                PlayerUiLayout.replaceAt(
                                    layout, region, index,
                                    PlayerUiLayout.Slot.Group(group.keys + button.key)
                                )
                            )
                        }
                    }
                }
            }
        }.forEach { rows += it }
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

    private enum class PickMode { AddGroup, ReplaceSlot, AddGroupMember }

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
                pick(
                    R.string.setting_player_high_energy,
                    R.string.setting_player_swipe_seek,
                    R.string.setting_player_swipe_seek_scale_name
                )
            )
            "online" -> showCard(
                getString(R.string.setting_player_ui_label_online),
                pick(R.string.setting_player_show_online)
            )
            "blank" -> showCard(
                getString(R.string.setting_player_ui_label_blank),
                PlayerSettingsGroups.group(this, PlayerSettingsGroups.GESTURE)?.sections.orEmpty() +
                    pick(R.string.setting_player_scale, R.string.setting_player_double_move)
            )
            "play" -> showCard(
                getString(R.string.setting_player_ui_label_play),
                PlayerSettingsGroups.group(this, PlayerSettingsGroups.DEFAULTS)?.sections.orEmpty()
            )
        }
    }

    /**
     * 从全部设置项里挑几项，返回它们**所在的整个分组**——即定位到某个设置项后，
     * 把它的上下文（同组其它项）一并显示出来，用户改完还能看到相关项。
     *
     * <p>按字符串资源定位而不是按中文字面量：`SettingSection.name` 本来就是落地文案，
     * 拿它当键的话，改一个字就静默匹配不上、卡片变成空的。
     */
    private fun pick(@StringRes vararg nameRes: Int): List<SettingSection> {
        val all = PlayerSettingsGroups.flatten(this)
        return nameRes.flatMap { res ->
            val name = getString(res)
            all.filter { it.name == name }
        }
    }

    private fun noteSection(text: String) = SettingSection("title", text, "", "", "")

    /** 一段灰字说明（不是分组标题）：用在区首解释、收纳格性质这种地方。 */
    private fun hintSection(text: String) = SettingSection("note", "", "", text, "")

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
