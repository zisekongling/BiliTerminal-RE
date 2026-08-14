package com.RobinNotBad.BiliClient.activity.user.favorite

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
        setPageName("收藏")

        mid = SharedPreferencesUtil.getLong("mid", 0)
        folderList = ArrayList()

        loadFolders()
    }

    private fun loadFolders() {
        CenterThreadPool.run {
            try {
                folderList.clear()
                folderList.addAll(FavoriteApi.getFavoriteFolders(mid))
                adapter = FavoriteFolderAdapter(this@FavoriteFolderListActivity, folderList, mid)
                adapter!!.setOnCreateClickListener { showCreateDialog() }
                adapter!!.setOnLongClickListener { position ->
                    if (position >= 0 && position < folderList.size) {
                        val folder = folderList[position]
                        if (folder.mediaId == 0L) {
                            MsgUtil.showMsg("无法获取收藏夹信息，请稍后重试")
                            return@setOnLongClickListener
                        }
                        val intent = Intent(this, FavoriteFolderEditActivity::class.java)
                        intent.putExtra("mediaId", folder.mediaId)
                        intent.putExtra("title", folder.name)
                        intent.putExtra("intro", "")
                        intent.putExtra("isDefault", folder.isDefault)
                        startActivityForResult(intent, 1)
                    }
                }
                setAdapter(adapter!!)
                setRefreshing(false)
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