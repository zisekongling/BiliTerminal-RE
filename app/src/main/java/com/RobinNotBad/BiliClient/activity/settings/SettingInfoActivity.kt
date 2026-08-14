package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection

class SettingInfoActivity : RefreshListActivity() {

    @SuppressLint("MissingInflatedId", "SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName("详情页设置")
        Log.e("debug", "进入详情页设置")

        val sectionList: List<SettingSection> = ArrayList<SettingSection>().apply {
            add(SettingSection("switch", "收藏夹单选", "fav_single", getString(R.string.desc_fav_single), "false"))
            add(SettingSection("switch", "收藏成功提示", "fav_notice", getString(R.string.desc_fav_notice), "true"))
            add(SettingSection("switch", "点击封面播放", "cover_play_enable", getString(R.string.desc_cover_play), "true"))
            add(SettingSection("switch", "显示视频标签", "tags_enable", getString(R.string.desc_tags_enable), "true"))
            add(SettingSection("switch", "视频相关推荐", "related_enable", getString(R.string.desc_related_enable), "true"))
            add(SettingSection("switch", "以游客方式观看直播", "live_by_guest", getString(R.string.desc_live_by_guest), "false"))
            add(SettingSection("switch", "一键三连", "like_one_triple", getString(R.string.desc_one_triple), "true"))
        }

        recyclerView.setHasFixedSize(true)

        val adapter = SettingsAdapter(this, sectionList)
        setAdapter(adapter)

        setRefreshing(false)
    }
}