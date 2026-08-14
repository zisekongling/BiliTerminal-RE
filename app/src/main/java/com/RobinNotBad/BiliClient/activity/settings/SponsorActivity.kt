package com.RobinNotBad.BiliClient.activity.settings

import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.user.UserListAdapter
import com.RobinNotBad.BiliClient.api.AppInfoApi
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil

class SponsorActivity : RefreshListActivity() {

    private lateinit var userList: ArrayList<UserInfo>
    private lateinit var adapter: UserListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPageName("捐赠列表")

        userList = ArrayList()
        userList.add(UserInfo(-1, getString(R.string.donate_title), "",
            getString(R.string.donate_desc),
            -1, -1, 6, true, "", 0, "", 0))

        CenterThreadPool.run {
            try {
                val result = AppInfoApi.getSponsors(userList, this.page)
                adapter = UserListAdapter(this, userList)
                setOnLoadMoreListener { continueLoading(it) }
                setRefreshing(false)
                setAdapter(adapter)

                if (result == 1) bottom = true
            } catch (e: Exception) {
                report(e)
                runOnUiThread { MsgUtil.showMsg("连接到哔哩终端接口时发生错误") }
                setRefreshing(false)
            }
        }
    }

    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val lastSize = userList.size
                val result = AppInfoApi.getSponsors(userList, page)
                runOnUiThread { adapter.notifyItemRangeInserted(lastSize, userList.size - lastSize) }
                setRefreshing(false)

                if (result == 1) bottom = true
            } catch (e: Exception) {
                runOnUiThread { MsgUtil.showMsg("连接到哔哩终端接口时发生错误") }
                loadFail(e)
            }
        }
    }
}