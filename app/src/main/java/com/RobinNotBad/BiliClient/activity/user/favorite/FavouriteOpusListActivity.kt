package com.RobinNotBad.BiliClient.activity.user.favorite

import android.os.Bundle
import android.util.Log

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.article.OpusAdapter
import com.RobinNotBad.BiliClient.api.FavoriteApi
import com.RobinNotBad.BiliClient.model.Opus
import com.RobinNotBad.BiliClient.util.CenterThreadPool

class FavouriteOpusListActivity : RefreshListActivity() {
    var list: ArrayList<Opus> = ArrayList()
    var adapter: OpusAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName("图文收藏夹")

        list = ArrayList()

        CenterThreadPool.run {
            try {
                FavoriteApi.getFavouriteOpus(list, page)
                adapter = OpusAdapter(this, list)
                Log.e("", "amount:" + list.size)
                setAdapter(adapter!!)
                setRefreshing(false)
            } catch (e: Exception) {
                loadFail(e)
            }
        }

        setOnLoadMoreListener { page -> loadMore(page) }
    }

    fun loadMore(page: Int) {
        CenterThreadPool.run {
            try {
                val lastSize = list.size
                bottom = !FavoriteApi.getFavouriteOpus(list, page)
                runOnUiThread { adapter!!.notifyItemRangeInserted(lastSize, list.size - lastSize) }
                setRefreshing(false)
            } catch (e: Exception) {
                loadFail(e)
            }

        }
    }
}