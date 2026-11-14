package com.RobinNotBad.BiliClient.activity.settings

import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.player.SkipOpEdPolicy
import com.RobinNotBad.BiliClient.player.SkipOpEdPrefs
import com.RobinNotBad.BiliClient.player.SponsorCategoryLabels
import com.RobinNotBad.BiliClient.player.SponsorSkipPolicy
import com.RobinNotBad.BiliClient.player.SponsorSkipPrefs
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.TerminalDialog

/**
 * 「跳过设置」二级页（26.11.14 起）：片头片尾 + 逐类别三档 + 「不准」记录。
 *
 * 为什么把它从「完整设置」里搬出来单独一页：片头片尾与广告片段是同一种事的两个数据源
 * （都是"到点跳走"），放在一起才看得出区别；而 SponsorBlock 从「六档 + 自定义勾选」改成
 * **逐类别三档**（关闭 / 询问我 / 自动跳转）之后，行数从 2 行变成 10 行，塞在长列表里
 * 会淹没其它播放器设置。入口在「选择播放器」页的「播放器界面」与「清晰度」之间。
 *
 * 三档的语义与判据在 [SponsorSkipPolicy]（`MODE_OFF` / `MODE_ASK` / `MODE_AUTO`），
 * 存档读写与老存档迁移在 [SponsorSkipPrefs]；本页只负责把每类一行摆出来。
 *
 * 页面上**不出现** `poi_highlight`（精彩时刻）：它是跳转点、区间为空，"跳过它"没有意义，
 * 这类标注走播放页「看点」里的跳转项（见 [SponsorSkipPolicy.highlights]）。
 */
class SettingSkipActivity : RefreshListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.player_skip_page_name))

        recyclerView.setHasFixedSize(true)
        setAdapter(SettingsAdapter(this, buildSections()))
        setRefreshing(false)
    }

    private fun buildSections(): List<SettingSection> {
        val out = ArrayList<SettingSection>(SponsorCategoryLabels.selectable.size + 5)

        // ---- 片头片尾：番剧与普通投稿都生效，判据在 SkipOpEdPolicy ----
        out.add(SettingSection("title", getString(R.string.player_skip_section_op_ed), "", "", ""))
        if (SkipOpEdPrefs.FEATURE_ENABLED) {
            out.add(
                listChoose(
                    getString(R.string.player_s_skip_mode_name),
                    SettingsKeys.PLAYER_SKIP_OP_ED_MODE,
                    getString(R.string.player_s_skip_mode_desc),
                    SkipOpEdPrefs.currentMode().toString(),
                    listOf(
                        getString(R.string.player_s_skip_mode_off),
                        getString(R.string.player_s_skip_mode_op),
                        getString(R.string.player_s_skip_mode_ed),
                        getString(R.string.player_s_skip_mode_both),
                        getString(R.string.player_s_skip_mode_ask)
                    ),
                    listOf(
                        SkipOpEdPolicy.MODE_OFF.toString(),
                        SkipOpEdPolicy.MODE_OP.toString(),
                        SkipOpEdPolicy.MODE_ED.toString(),
                        SkipOpEdPolicy.MODE_BOTH.toString(),
                        SkipOpEdPolicy.MODE_ASK.toString()
                    )
                )
            )
        }

        // ---- 广告片段：逐类别三档，仅普通投稿（番剧没有 bvid，链路根本不发请求）----
        out.add(SettingSection("title", getString(R.string.player_skip_section_sponsor), "", "", ""))
        if (SponsorSkipPrefs.FEATURE_ENABLED) {
            for (category in SponsorCategoryLabels.selectable) {
                val descRes = SponsorCategoryLabels.descRes(category)
                out.add(
                    listChoose(
                        getString(SponsorCategoryLabels.nameRes(category)),
                        SponsorSkipPrefs.modeKey(category),
                        if (descRes == 0) "" else getString(descRes),
                        SponsorSkipPrefs.storedMode(category).toString(),
                        listOf(
                            getString(R.string.player_sb_mode_off),
                            getString(R.string.player_sb_mode_ask),
                            getString(R.string.player_sb_mode_auto)
                        ),
                        listOf(
                            SponsorSkipPolicy.MODE_OFF.toString(),
                            SponsorSkipPolicy.MODE_ASK.toString(),
                            SponsorSkipPolicy.MODE_AUTO.toString()
                        )
                    )
                )
            }
            // 「不准」记录：本地忽略表，条数是一条条攒出来的、界面上看不到明细，误按也没有
            // 单条撤销入口——所以给一个整表清空。即时生效且不可恢复，要过一道确认框。
            out.add(
                SettingSection(
                    "button",
                    getString(R.string.player_sb_clear_ignored_name),
                    "", "", "",
                    SettingsAdapter.ButtonExtra { view ->
                        val count = SponsorSkipPrefs.ignoredVideoCount()
                        if (count <= 0) {
                            MsgUtil.showMsg(getString(R.string.player_sb_clear_ignored_empty))
                            return@ButtonExtra
                        }
                        TerminalDialog.confirm(
                            view.context,
                            getString(R.string.player_sb_clear_ignored_name),
                            getString(R.string.player_sb_clear_ignored_confirm, count)
                        ) {
                            SponsorSkipPrefs.clearAllIgnored()
                            MsgUtil.showMsg(getString(R.string.player_sb_clear_ignored_done))
                        }.show()
                    }
                )
            )
        }

        out.add(SettingSection("title", getString(R.string.player_skip_page_note), "", "", ""))
        return out
    }

    /** 与 `PlayerSettingsGroups.listChoose` 同构：一行「点进去选一项」，值按字符串存档。 */
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
}
