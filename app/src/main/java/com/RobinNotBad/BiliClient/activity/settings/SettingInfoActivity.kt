package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.util.DetailEntryPrefs
import com.RobinNotBad.BiliClient.util.OptionalFeatures
import com.RobinNotBad.BiliClient.util.SettingsKeys

class SettingInfoActivity : RefreshListActivity() {

    @SuppressLint("MissingInflatedId", "SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.pagename_settinginfoactivity_001))
        Log.e("debug", "进入详情页设置")

        val sectionList: MutableList<SettingSection> = ArrayList<SettingSection>().apply {
            add(SettingSection("switch", "收藏夹单选", SettingsKeys.FAV_SINGLE, getString(R.string.desc_fav_single), "false"))
            add(SettingSection("switch", "收藏成功提示", SettingsKeys.FAV_NOTICE, getString(R.string.desc_fav_notice), "true"))
            add(SettingSection("switch", "点击封面播放", SettingsKeys.COVER_PLAY_ENABLE, getString(R.string.desc_cover_play), "true"))
            add(SettingSection("switch", "显示视频标签", SettingsKeys.TAGS_ENABLE, getString(R.string.desc_tags_enable), "true"))
            add(SettingSection("switch", "视频相关推荐", SettingsKeys.RELATED_ENABLE, getString(R.string.desc_related_enable), "true"))
            add(SettingSection("switch", "以游客方式观看直播", SettingsKeys.LIVE_BY_GUEST, getString(R.string.desc_live_by_guest), "false"))
            add(SettingSection("switch", "一键三连", SettingsKeys.LIKE_ONE_TRIPLE, getString(R.string.desc_one_triple), "true"))
            // 反馈 #112：动作区卡片那一圈主色描边有人觉得扎眼，给一个能关掉的开关（默认开）。
            add(
                SettingSection(
                    "switch",
                    getString(R.string.setting_detail_like_card_ring),
                    SettingsKeys.DETAIL_LIKE_CARD_RING,
                    getString(R.string.desc_setting_detail_like_card_ring),
                    DetailEntryPrefs.DEFAULT_LIKE_CARD_RING.toString()
                )
            )

            // 26.11.07：详情页动作区的两个可选入口，默认都关（= 默认不显示）。
            // 用户要求默认把「视频笔记」和「分享到其他设备」藏起来，想用再自己打开。
            add(
                SettingSection(
                    "switch",
                    getString(R.string.setting_detail_note_entry),
                    SettingsKeys.DETAIL_NOTE_ENTRY,
                    getString(R.string.desc_setting_detail_note_entry),
                    DetailEntryPrefs.DEFAULT_NOTE_ENABLED.toString()
                )
            )
            // 「分享到其他设备」是私有功能（`private/`），公开构建里既没有这一行也没有
            // 对应的 Activity——给人一个点了没有任何效果的开关只会变成一条反馈，
            // 所以只在装了私有功能的构建里显示（判据同 OptionalFeatures.available）。
            if (OptionalFeatures.available) {
                add(
                    SettingSection(
                        "switch",
                        getString(R.string.setting_detail_share_row_entry),
                        SettingsKeys.DETAIL_SHARE_ROW,
                        getString(R.string.desc_setting_detail_share_row_entry),
                        DetailEntryPrefs.DEFAULT_SHARE_ROW_ENABLED.toString()
                    )
                )
            }
        }

        recyclerView.setHasFixedSize(true)

        val adapter = SettingsAdapter(this, sectionList)
        setAdapter(adapter)

        setRefreshing(false)

        scrollToHighlight(sectionList, intent.getStringExtra("highlight"))
    }
}
