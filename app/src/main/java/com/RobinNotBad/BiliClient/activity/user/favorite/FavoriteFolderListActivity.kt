package com.RobinNotBad.BiliClient.activity.user.favorite

import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.favorite.FavoriteFolderAdapter
import com.RobinNotBad.BiliClient.api.FavoriteApi
import com.RobinNotBad.BiliClient.model.FavoriteFolder
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class FavoriteFolderListActivity : RefreshListActivity() {

    private var adapter: FavoriteFolderAdapter? = null
    private var folderList: ArrayList<FavoriteFolder> = ArrayList()
    private var mid: Long = 0

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(UiText.of("收藏"))

        mid = SharedPreferencesUtil.getLong("mid", 0)
        folderList = ArrayList()

        loadFolders()
    }

    private fun loadFolders() {
        CenterThreadPool.run {
            try {
                // 先在外面把新数据装好，别去动已经挂在 RecyclerView 上的那份 list：
                // 后台线程改同一个 ArrayList 时旧 adapter 还挂着（onActivityResult 回到本页会立刻重载），
                // itemCount 在「有一堆条目 → 0 → 一堆条目」之间跳变却没有任何 notify，
                // 预取线程按旧位置取 holder 就会抛
                // IndexOutOfBoundsException: Inconsistency detected. Invalid view holder adapter position
                // （控制台崩溃 #160）。改成换一份全新 list + 全新 adapter，整体在 UI 线程一次性替换。
                val folders = ArrayList(FavoriteApi.getFavoriteFolders(mid))
                if (isFinishing || isDestroyed) return@run
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    folderList = folders
                    val newAdapter = FavoriteFolderAdapter(this@FavoriteFolderListActivity, folders, mid)
                    newAdapter.setOnCreateClickListener { showCreateDialog() }
                    newAdapter.setOnLongClickListener { position ->
                        if (position >= 0 && position < folders.size) {
                            val folder = folders[position]
                            if (folder.mediaId == 0L) {
                                MsgUtil.showMsg(UiText.of("无法获取收藏夹信息，请稍后重试"))
                                return@setOnLongClickListener
                            }
                            val intent = Intent(this, FavoriteFolderEditActivity::class.java)
                            intent.putExtra("mediaId", folder.mediaId)
                            intent.putExtra("title", folder.name)
                            // 简介/隐私由编辑页按 mediaId 拉 folder/info 回填：列表接口没有这两个字段，
                            // 在这里传空串会让保存把简介清空、隐私重置成公开
                            intent.putExtra("isDefault", folder.isDefault)
                            startActivityForResult(intent, 1)
                        }
                    }
                    adapter = newAdapter
                    newAdapter.setOnAggregateClickListener {
                        // 跨夹「全部收藏」：服务端没有聚合接口，进的是只读的本地合并页
                        // （夹数与条数上限见 util/FavoriteAggregatePolicy）
                        val intent = Intent(this, FavoriteVideoListActivity::class.java)
                        intent.putExtra("mid", mid)
                        intent.putExtra("aggregate", true)
                        intent.putExtra("readOnly", true)
                        intent.putExtra("name", getString(R.string.fav_port_aggregate_title))
                        startActivity(intent)
                    }
                    setAdapter(newAdapter)
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    private fun showCreateDialog() {
        val intent = Intent(this, FavoriteFolderCreateActivity::class.java)
        startActivityForResult(intent, 2)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == 1 || requestCode == 2) && resultCode == RESULT_OK) {
            loadFolders()
        }
    }
}
