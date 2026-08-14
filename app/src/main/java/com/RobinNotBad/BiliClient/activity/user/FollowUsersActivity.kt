package com.RobinNotBad.BiliClient.activity.user

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.user.FollowGroupAdapter
import com.RobinNotBad.BiliClient.adapter.user.UserListAdapter
import com.RobinNotBad.BiliClient.api.FollowApi
import com.RobinNotBad.BiliClient.model.FollowTag
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class FollowUsersActivity : RefreshListActivity() {

    private var mid: Long = 0
    private var userList: ArrayList<UserInfo> = ArrayList()
    private var adapter: UserListAdapter? = null
    private var groupAdapter: FollowGroupAdapter? = null
    private var mode: Int = 0
    private var groupMode: Boolean = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mode = intent.getIntExtra("mode", 0)
        mid = intent.getLongExtra("mid", -1)

        if (mode < 0 || mode > 1 || mid == -1L) {
            finish()
            return
        }

        val currentUserMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
        groupMode = mode == 0 && mid == currentUserMid && SharedPreferencesUtil.getBoolean("follow_group_mode", false)

        setPageName(if (mode == 0) "关注列表" else "粉丝列表")

        recyclerView.setHasFixedSize(true)

        userList = ArrayList()

        if (groupMode) {
            loadGroupMode()
        } else {
            loadNormalMode()
        }
    }

    private fun loadNormalMode() {
        CenterThreadPool.run {
            try {
                val result = if (mode == 0) FollowApi.getFollowingList(mid, page, userList) else FollowApi.getFollowerList(mid, page, userList)
                adapter = UserListAdapter(this, userList)
                setOnLoadMoreListener { page -> continueLoading(page) }
                setRefreshing(false)
                setAdapter(adapter!!)

                if (result == 1) {
                    Log.e("debug", "到底了")
                    bottom = true
                }
            } catch (e: Exception) {
                if (e.message != null && (e.message!!.startsWith("22115") || e.message!!.startsWith("22118"))) {
                    finish()
                    MsgUtil.showMsg(e.message!!)
                } else {
                    loadFail(e)
                }
            }
        }
    }

    private fun loadGroupMode() {
        CenterThreadPool.run {
            try {
                val tagList = FollowApi.getFollowTags()
                runOnUiThread {
                    groupAdapter = FollowGroupAdapter(this@FollowUsersActivity)
                    groupAdapter!!.setOnGroupExpandListener { tagid -> loadGroupUsers(tagid) }
                    setAdapter(groupAdapter!!)
                    for (tag in tagList) {
                        if (tag.count > 0) {
                            groupAdapter!!.addGroup(tag, ArrayList())
                        }
                    }
                    groupAdapter!!.notifyDataSetChanged()
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                if (e.message != null && (e.message!!.startsWith("22115") || e.message!!.startsWith("22118"))) {
                    finish()
                    MsgUtil.showMsg(e.message!!)
                } else {
                    loadFail(e)
                }
            }
        }
    }

    fun loadGroupUsers(tagid: Int) {
        CenterThreadPool.run {
            try {
                val tagUsers: MutableList<UserInfo> = ArrayList()
                val result = FollowApi.getFollowTagUsers(tagid, 1, tagUsers)
                runOnUiThread {
                    groupAdapter!!.updateGroupUsers(tagid, tagUsers)
                }
                if (result == 0 && tagUsers.size == 20) {
                    loadMoreGroupUsers(tagid, tagUsers.size)
                }
            } catch (e: Exception) {
                Log.e("debug", "加载分组用户失败", e)
            }
        }
    }

    private fun loadMoreGroupUsers(tagid: Int, currentCount: Int) {
        CenterThreadPool.run {
            try {
                val page = (currentCount / 20) + 1
                val tagUsers: MutableList<UserInfo> = ArrayList()
                val result = FollowApi.getFollowTagUsers(tagid, page, tagUsers)
                runOnUiThread {
                    groupAdapter!!.addGroupUsers(tagid, tagUsers)
                }
                if (result == 0 && tagUsers.size == 20) {
                    loadMoreGroupUsers(tagid, currentCount + tagUsers.size)
                }
            } catch (e: Exception) {
                Log.e("debug", "加载分组用户失败", e)
            }
        }
    }

    private fun continueLoading(page: Int) {
        if (groupMode) {
            setRefreshing(false)
            return
        }
        CenterThreadPool.run {
            try {
                val list: MutableList<UserInfo> = ArrayList()
                val result = if (mode == 0) FollowApi.getFollowingList(mid, page, list) else FollowApi.getFollowerList(mid, page, list)
                Log.e("debug", "下一页")
                runOnUiThread {
                    userList.addAll(list)
                    adapter!!.notifyItemRangeInserted(userList.size - list.size, list.size)
                }
                if (result == 1) {
                    Log.e("debug", "到底了")
                    bottom = true
                }
                setRefreshing(false)
            } catch (e: Exception) {
                if (e.message != null && (e.message!!.startsWith("22115") || e.message!!.startsWith("22118"))) {
                    finish()
                    MsgUtil.showMsg(e.message!!)
                } else {
                    loadFail(e)
                }
            }
        }
    }
}