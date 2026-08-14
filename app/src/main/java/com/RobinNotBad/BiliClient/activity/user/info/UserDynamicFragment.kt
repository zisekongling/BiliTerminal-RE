package com.RobinNotBad.BiliClient.activity.user.info

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View

import androidx.annotation.NonNull

import com.RobinNotBad.BiliClient.activity.base.RefreshListFragment
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder
import com.RobinNotBad.BiliClient.adapter.dynamic.UserDynamicAdapter
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.model.Dynamic
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil

class UserDynamicFragment : RefreshListFragment() {

    private var mid: Long = 0
    private var dynamicList: ArrayList<Dynamic> = ArrayList()
    private var adapter: UserDynamicAdapter? = null
    private var offset: Long = 0

    companion object {
        fun newInstance(mid: Long): UserDynamicFragment {
            val fragment = UserDynamicFragment()
            val args = Bundle()
            args.putLong("mid", mid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            mid = arguments!!.getLong("mid")
        }
    }

    override fun onViewCreated(@NonNull view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dynamicList = ArrayList()
        setOnLoadMoreListener { continueLoading() }

        CenterThreadPool.run {
            try {
                val userInfo = UserInfoApi.getUserInfo(mid)
                if (userInfo == null) {
                    runOnUiThread {
                        MsgUtil.showMsg("用户不存在")
                        requireActivity().finish()
                    }
                    return@run
                }
                Log.e("debug", "获取到用户信息")

                try {
                    offset = DynamicApi.getDynamicList(dynamicList, offset, mid, null)
                    bottom = (offset == -1L)
                    Log.e("debug", "获取到用户动态")
                } catch (e: Exception) {
                    loadFail(e)
                }

                if (isAdded) {
                    adapter = UserDynamicAdapter(requireContext(), dynamicList, userInfo)
                    setAdapter(adapter!!)
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun continueLoading() {
        CenterThreadPool.run {
            try {
                val list: MutableList<Dynamic> = ArrayList()
                offset = DynamicApi.getDynamicList(list, offset, mid, null)
                runOnUiThread {
                    dynamicList.addAll(list)
                    adapter!!.notifyItemRangeInserted(dynamicList.size - list.size + 1, list.size)
                }
                bottom = (offset == -1L)
                setRefreshing(false)
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    fun onDynamicRemove(position: Int) {
        try {
            DynamicHolder.removeDynamicFromList(dynamicList, position, adapter!!)
        } catch (ignored: Throwable) {
        }
    }
}