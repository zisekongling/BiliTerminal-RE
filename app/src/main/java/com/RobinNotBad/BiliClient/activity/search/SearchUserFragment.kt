package com.RobinNotBad.BiliClient.activity.search

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View

import androidx.annotation.NonNull

import com.RobinNotBad.BiliClient.adapter.user.UserListAdapter
import com.RobinNotBad.BiliClient.api.SearchApi
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool

import org.json.JSONArray

class SearchUserFragment : SearchFragment() {

    private val userInfoList = ArrayList<UserInfo>()
    private lateinit var userInfoAdapter: UserListAdapter

    companion object {
        fun newInstance(): SearchUserFragment {
            return SearchUserFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }


    @SuppressLint("SetTextI18n")
    override fun onViewCreated(@NonNull view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userInfoAdapter = UserListAdapter(requireContext(), userInfoList)
        setAdapter(userInfoAdapter)

        setOnRefreshListener { refreshInternal() }
        setOnLoadMoreListener { page -> continueLoading(page) }
    }

    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            Log.e("debug", "加载下一页")
            try {
                val result = SearchApi.searchType(keyword, page, "bili_user") as JSONArray?
                if (result != null) {
                    if (page == 1) showEmptyView(false)
                    val list: MutableList<UserInfo> = ArrayList()
                    SearchApi.getUsersFromSearchResult(result, list)
                    if (list.isEmpty()) bottom = true
                    CenterThreadPool.runOnUiThread {
                        val lastSize = userInfoList.size
                        userInfoList.addAll(list)
                        userInfoAdapter.notifyItemRangeInserted(lastSize, userInfoList.size - lastSize)
                    }
                } else bottom = true
            } catch (e: Exception) {
                loadFail(e)
            }
            setRefreshing(false)
        }
    }

    override fun refreshInternal() {
        page = 1
        CenterThreadPool.runOnUiThread {
            val sizeOld = this.userInfoList.size
            this.userInfoList.clear()
            if (sizeOld != 0) this.userInfoAdapter.notifyItemRangeRemoved(0, sizeOld)
            CenterThreadPool.run { continueLoading(page) }
        }
    }


}