package com.RobinNotBad.BiliClient.activity.user.info

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View

import androidx.annotation.NonNull

import com.RobinNotBad.BiliClient.activity.base.RefreshListFragment
import com.RobinNotBad.BiliClient.adapter.article.ArticleCardAdapter
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.model.ArticleCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool

class UserArticleFragment : RefreshListFragment() {

    private var mid: Long = 0
    private var articleList: ArrayList<ArticleCard> = ArrayList()
    private var adapter: ArticleCardAdapter? = null

    companion object {
        fun newInstance(mid: Long): UserArticleFragment {
            val fragment = UserArticleFragment()
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

        articleList = ArrayList()
        setOnLoadMoreListener { page -> continueLoading(page) }

        CenterThreadPool.run {
            try {
                bottom = (UserInfoApi.getUserArticles(mid, page, articleList) == 1)
                if (isAdded) {
                    adapter = ArticleCardAdapter(requireContext(), articleList)
                    setAdapter(adapter!!)
                    setRefreshing(false)
                    if (bottom && articleList.isEmpty()) showEmptyView()
                }
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val list: MutableList<ArticleCard> = ArrayList()
                val result = UserInfoApi.getUserArticles(mid, page, list)
                if (result != -1) {
                    Log.e("debug", "下一页")
                    if (isAdded) requireActivity().runOnUiThread {
                        articleList.addAll(list)
                        adapter!!.notifyItemRangeInserted(articleList.size - list.size, list.size)
                    }
                    if (result == 1) {
                        Log.e("debug", "到底了")
                        bottom = true
                    }
                }
                setRefreshing(false)
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }
}