package com.RobinNotBad.BiliClient.activity.settings

import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.player.SponsorCategoryLabels
import com.RobinNotBad.BiliClient.player.SponsorSkipPrefs

/**
 * 「标记哪些类别」（26.11.14 起）：进度条条子按哪些类别画。
 *
 * 为什么要单独一页：用户要求标记显示与跳过策略解耦——「能只显示条子不跳、或者反过来」。
 * 于是这里是一个**独立的勾选集合**（存档键 `player_sponsor_block_mark_cat_<类别>`），
 * 与「跳过设置」里的三档（`…skip_mode_<类别>`）互不影响：
 *
 * - 三档全关、这里全勾 = 只显示条子不跳（看得出这片哪儿有广告，但自己不跳）；
 * - 三档开着、这里全不勾 = 只跳不显示（不想被进度条上的色块打扰）。
 *
 * 页面上不出现 `poi_highlight`（精彩时刻）：三角由「跳过设置」里那个独立开关管。
 * 每个开关直接写 `SharedPreferencesUtil` 的布尔键（见 [SponsorSkipPrefs.markCategoryKey]），
 * 播放页每 250ms 重算一次签名，改完立刻生效，不需要重进播放页。
 */
class SettingMarkCategoryActivity : RefreshListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.player_mark_page_name))

        recyclerView.setHasFixedSize(true)
        setAdapter(SettingsAdapter(this, buildSections()))
        setRefreshing(false)
    }

    private fun buildSections(): List<SettingSection> {
        val out = ArrayList<SettingSection>(SponsorCategoryLabels.selectable.size + 1)
        for (category in SponsorCategoryLabels.selectable) {
            val descRes = SponsorCategoryLabels.descRes(category)
            out.add(
                SettingSection(
                    "switch",
                    getString(SponsorCategoryLabels.nameRes(category)),
                    SponsorSkipPrefs.markCategoryKey(category),
                    if (descRes == 0) "" else getString(descRes),
                    "true"
                )
            )
        }
        out.add(SettingSection("title", getString(R.string.player_mark_page_note), "", "", ""))
        return out
    }
}
