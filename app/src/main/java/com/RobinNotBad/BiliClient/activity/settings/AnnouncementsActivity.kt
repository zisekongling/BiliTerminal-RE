package com.RobinNotBad.BiliClient.activity.settings

import android.os.Bundle
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.AnnouncementAdapter
import com.RobinNotBad.BiliClient.api.AppInfoApi
import com.RobinNotBad.BiliClient.model.Announcement
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil

class AnnouncementsActivity : RefreshListActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPageName("公告列表")

        CenterThreadPool.run {
            try {
                val announcements: ArrayList<Announcement> = AppInfoApi.getAnnouncementList()
                setRefreshing(false)

                val adapter = AnnouncementAdapter(this, announcements)

                setAdapter(adapter)

            } catch (e: Exception) {
                report(e)
                runOnUiThread { MsgUtil.showMsg("连接到哔哩终端接口时发生错误") }
            }
        }
    }
}