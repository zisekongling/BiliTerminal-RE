package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter
import com.RobinNotBad.BiliClient.model.SettingSection
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class SettingRepliesActivity : RefreshListActivity() {

    @SuppressLint("MissingInflatedId", "SetTextI18n", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName("评论区设置")
        Log.e("debug", "进入评论区设置")

        val sectionList: List<SettingSection> = ArrayList<SettingSection>().apply {
            add(SettingSection("switch", "众生平等", SharedPreferencesUtil.NO_VIP_COLOR, getString(R.string.desc_no_vip_color), "false"))
            add(SettingSection("switch", "粉丝铭牌消失术", SharedPreferencesUtil.NO_MEDAL, getString(R.string.desc_no_medal), "false"))
            add(SettingSection("switch", "昵称不换行显示", SharedPreferencesUtil.REPLY_MARQUEE_NAME, getString(R.string.desc_reply_marquee_name), "true"))
        }

        recyclerView.setHasFixedSize(true)

        val adapter = SettingsAdapter(this, sectionList)
        setAdapter(adapter)

        setRefreshing(false)

        scrollToHighlight(sectionList, intent.getStringExtra("highlight"))
    }
}
