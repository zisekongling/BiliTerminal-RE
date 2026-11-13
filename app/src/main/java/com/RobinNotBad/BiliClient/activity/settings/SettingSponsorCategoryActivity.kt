package com.RobinNotBad.BiliClient.activity.settings

import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.player.SponsorCategoryLabels
import com.RobinNotBad.BiliClient.player.SponsorSkipPolicy
import com.RobinNotBad.BiliClient.player.SponsorSkipPrefs

/**
 * 「要跳的类别」：SponsorBlock 自定义档的逐类别勾选页。
 *
 * 为什么要有这一页：五档预设只能覆盖"少/中/多"三种粗粒度取舍，但类别本身差异极大——
 * 有人只想跳恰饭广告（`sponsor`）却想留着片头，有人受得了自我推广却受不了三连提醒。
 * 逐项勾选后档位自动切成「自定义」，播放页按勾选算实际集合（见 `SponsorSkipPrefs.effectiveCategories`）。
 *
 * 页面上不出现 `poi_highlight`（精彩时刻）：它是**跳转点**不是一段可跳区间，
 * 勾了也不会生效，写进列表只会让人以为坏了。
 *
 * 每个开关直接落到 `SharedPreferencesUtil` 的布尔键（见 `SponsorSkipPrefs.categoryKey`），
 * 这里只负责两件额外的事：进入时记快照、离开时若勾选变了就把档位切成「自定义」。
 */
class SettingSponsorCategoryActivity : RefreshListActivity() {

    private var sections: List<SettingSection> = emptyList()

    /**
     * 进入本页时的勾选快照。
     *
     * 开关是"改了立刻落盘"的，所以要判断"用户是不是动过"，只能跟进来时比一次；
     * 动过就把档位切到「自定义」——否则用户在「推荐」档里勾来勾去，回去发现毫无变化。
     */
    private var categoriesAtEnter: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.player_sb_cat_page_name))

        categoriesAtEnter = SponsorSkipPrefs.customCategories()
        sections = buildSections()

        recyclerView.setHasFixedSize(true)
        setAdapter(SettingsAdapter(this, sections))
        setRefreshing(false)
    }

    override fun onPause() {
        super.onPause()
        val now = SponsorSkipPrefs.customCategories()
        if (now != categoriesAtEnter) {
            SponsorSkipPrefs.storeLevel(SponsorSkipPolicy.LEVEL_CUSTOM)
            categoriesAtEnter = now
        }
    }

    private fun buildSections(): List<SettingSection> {
        val out = ArrayList<SettingSection>(SponsorCategoryLabels.selectable.size + 1)
        for (category in SponsorCategoryLabels.selectable) {
            val descRes = SponsorCategoryLabels.descRes(category)
            out.add(
                SettingSection(
                    "switch",
                    getString(SponsorCategoryLabels.nameRes(category)),
                    SponsorSkipPrefs.categoryKey(category),
                    if (descRes == 0) "" else getString(descRes),
                    SponsorSkipPrefs.categoryDefaultEnabled(category).toString()
                )
            )
        }
        // 末尾一行说明：讲清"哪些不在这页、也不需要在这页"。
        out.add(SettingSection("title", getString(R.string.player_sb_cat_page_note), "", "", ""))
        return out
    }
}
