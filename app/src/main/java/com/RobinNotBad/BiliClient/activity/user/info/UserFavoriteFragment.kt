package com.RobinNotBad.BiliClient.activity.user.info

import android.os.Bundle
import android.view.View

import androidx.annotation.NonNull

import com.RobinNotBad.BiliClient.activity.base.RefreshListFragment
import com.RobinNotBad.BiliClient.adapter.favorite.UserFavoriteFolderAdapter
import com.RobinNotBad.BiliClient.api.FavoriteApi
import com.RobinNotBad.BiliClient.model.FavoriteFolder
import com.RobinNotBad.BiliClient.util.CenterThreadPool

/**
 * 用户主页-收藏夹页：查看目标用户的公开收藏夹列表（私密收藏夹已在 API 层自动过滤）
 */
class UserFavoriteFragment : RefreshListFragment() {

    private var mid: Long = 0
    private var folderList: ArrayList<FavoriteFolder> = ArrayList()
    private var adapter: UserFavoriteFolderAdapter? = null

    companion object {
        fun newInstance(mid: Long): UserFavoriteFragment {
            val fragment = UserFavoriteFragment()
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

        folderList = ArrayList()

        CenterThreadPool.run {
            try {
                folderList.addAll(FavoriteApi.getUserFavoriteFolders(mid))
                if (isAdded) {
                    adapter = UserFavoriteFolderAdapter(requireContext(), mid, folderList)
                    setAdapter(adapter!!)
                    setRefreshing(false)
                    if (folderList.isEmpty()) showEmptyView()
                }
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }
}
