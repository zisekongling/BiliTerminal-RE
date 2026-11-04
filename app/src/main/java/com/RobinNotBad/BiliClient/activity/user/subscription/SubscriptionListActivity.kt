package com.RobinNotBad.BiliClient.activity.user.subscription

import android.content.Intent
import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteVideoListActivity
import com.RobinNotBad.BiliClient.adapter.subscription.SubscriptionFolderAdapter
import com.RobinNotBad.BiliClient.api.SubscriptionApi
import com.RobinNotBad.BiliClient.model.SubscriptionFolder
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog

/**
 * 「我的订阅」：我订阅的收藏夹与合集。
 *
 * <p>数据来自 [SubscriptionApi.getSubscribedFolders]（`x/v3/fav/folder/collected/list`，
 * 带 `platform=web` 才会把合集一起返回）。与「我的收藏夹」是两个接口，别混。
 *
 * <p>点条目：收藏夹（type=11）进只读的收藏夹内容页；合集（type=21）进合集详情页。
 * 长按条目：取消订阅（二次确认，成功后本地移除，不回拉整页）。
 */
class SubscriptionListActivity : RefreshListActivity() {

    private var adapter: SubscriptionFolderAdapter? = null
    private var folderList = ArrayList<SubscriptionFolder>()
    private var mid = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.pagename_subscription))

        mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
        if (mid == 0L) {
            // 订阅列表全部依赖登录态：未登录直接给空态，别发一个注定 -101 的请求
            MsgUtil.showMsg(getString(R.string.msg_subscription_need_login))
            showEmptyView()
            setRefreshing(false)
            return
        }

        setOnEmptyRetry { loadFirstPage() }
        setOnRefreshListener { loadFirstPage() }
        setOnLoadMoreListener { continueLoading(it) }
        loadFirstPage()
    }

    private fun loadFirstPage() {
        setRefreshing(true)
        CenterThreadPool.run {
            try {
                val newList = ArrayList<SubscriptionFolder>()
                val result = SubscriptionApi.getSubscribedFolders(mid, 1, newList)
                if (isFinishing || isDestroyed) return@run
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (result.first != 0) {
                        // -101/-111 等：给明确文案，空态可点重试
                        MsgUtil.showMsg(errorText(result.first))
                        showEmptyView()
                        setRefreshing(false)
                        return@runOnUiThread
                    }
                    page = 1
                    bottom = !result.second
                    folderList = newList
                    // 换一整份 list + 新 adapter：后台线程改挂着的那份 list 会触发
                    // RecyclerView 的 "Inconsistency detected"（收藏夹列表页踩过，见该页注释）
                    val newAdapter = SubscriptionFolderAdapter(this@SubscriptionListActivity, folderList)
                    newAdapter.onItemClickListener = { _, item -> openItem(item) }
                    newAdapter.onItemLongClickListener = { _, item -> confirmCancel(item) }
                    adapter = newAdapter
                    setAdapter(newAdapter)
                    if (folderList.isEmpty()) showEmptyView() else hideEmptyView()
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                if (isFinishing || isDestroyed) return@run
                loadFail(e)
            }
        }
    }

    /** 翻页；`page` 已由 [RefreshListActivity] 的滚动监听自增。 */
    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val added = ArrayList<SubscriptionFolder>()
                val result = SubscriptionApi.getSubscribedFolders(mid, page, added)
                if (isFinishing || isDestroyed) return@run
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (result.first != 0) {
                        // 失败要把页码退回去，否则重试会跳过这一页
                        this.page--
                        onLoadComplete()
                        setRefreshing(false)
                        MsgUtil.showMsg(errorText(result.first))
                        return@runOnUiThread
                    }
                    val lastSize = folderList.size
                    folderList.addAll(added)
                    bottom = !result.second
                    if (added.isNotEmpty()) {
                        adapter?.notifyItemRangeInserted(lastSize, added.size)
                    }
                    onLoadComplete()
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                if (isFinishing || isDestroyed) return@run
                loadFail(e)
            }
        }
    }

    private fun openItem(item: SubscriptionFolder) {
        if (item.isInvalid) {
            MsgUtil.showMsg(getString(R.string.msg_subscription_invalid))
            return
        }
        if (item.isSeason) {
            startActivity(
                Intent(this, SubSeasonDetailActivity::class.java)
                    .putExtra("seasonId", item.id)
                    .putExtra("title", item.title)
                    .putExtra("mediaCount", item.mediaCount)
            )
        } else {
            // type=11 的 id 就是收藏夹的 media_id；订阅的收藏夹不是自己的，只读
            startActivity(
                Intent(this, FavoriteVideoListActivity::class.java)
                    .putExtra("mid", mid)
                    .putExtra("name", item.title)
                    .putExtra("mediaId", item.id)
                    .putExtra("readOnly", true)
            )
        }
    }

    private fun confirmCancel(item: SubscriptionFolder) {
        TerminalDialog.confirm(
            context = this,
            title = getString(R.string.pagename_dialog),
            message = getString(R.string.msg_subscription_cancel_confirm, item.title),
            onConfirm = { cancelSubscription(item) }
        ).show()
    }

    private fun cancelSubscription(item: SubscriptionFolder) {
        CenterThreadPool.run {
            try {
                val code = if (item.isSeason) {
                    SubscriptionApi.cancelSeasonSubscription(item.id)
                } else {
                    SubscriptionApi.cancelFolderSubscription(item.id)
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (code != 0) {
                        MsgUtil.showMsg(errorText(code))
                        return@runOnUiThread
                    }
                    MsgUtil.showMsg(getString(R.string.msg_subscription_cancelled))
                    removeItem(item)
                }
            } catch (e: Exception) {
                if (isFinishing || isDestroyed) return@run
                report(e)
            }
        }
    }

    /** 按 id + type 定位：刷新后对象引用会变，只有这两个字段能当身份。 */
    private fun removeItem(item: SubscriptionFolder) {
        val index = folderList.indexOfFirst { it.id == item.id && it.type == item.type }
        if (index < 0) return
        folderList.removeAt(index)
        adapter?.notifyItemRemoved(index)
        adapter?.notifyItemRangeChanged(index, folderList.size - index)
        if (folderList.isEmpty()) showEmptyView()
    }

    /** 接口错误码 → 可读文案。调用方都在 UI 线程，直接用 Activity 的 getString。 */
    private fun errorText(code: Int): String = when (code) {
        -101 -> getString(R.string.msg_subscription_need_login)
        -111 -> getString(R.string.msg_subscription_csrf_invalid)
        -400 -> getString(R.string.msg_subscription_bad_request)
        11010 -> getString(R.string.msg_subscription_content_gone)
        else -> getString(R.string.msg_subscription_failed_with_code, code)
    }
}
