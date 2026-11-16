package com.RobinNotBad.BiliClient.activity.feedback

import com.RobinNotBad.BiliClient.util.UiText

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.FeedbackListAdapter
import com.RobinNotBad.BiliClient.api.TerminalApi
import com.RobinNotBad.BiliClient.model.FeedbackItem
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FeedbackUnread
import com.RobinNotBad.BiliClient.util.MsgUtil

/**
 * 「我的反馈」列表（26.10.16 新增）。
 *
 * <p>身份靠 `X-Install-Id`（`TerminalApi.headers()` 一直带着）；勾选「附带账号 ID」后还会带上
 * `X-Mid`，换设备 / 重装后仍能找回自己发过的反馈。详见 [TerminalApi.getMyFeedback]。
 *
 * <h3>本页只负责「看」</h3>
 * 列表本身不做筛选 / 标签 / 投票；**追加说明在详情页**（[FeedbackDetailActivity]）。
 * 详情页发送成功会 `setResult(RESULT_OK)`，本页据此重拉一次 —— 服务端在追加时会把该反馈
 * 打回「待受理」，列表上那份快照的状态就过期了。
 *
 * <h3>为什么继承 RefreshListActivity 而不是自己写布局</h3>
 * 二级页 + 分页的既有模板（`docs/architecture-map.md` §8.2）：它已经在 onCreate 里
 * `setContentView(activity_simple_refresh)` 并绑好了 swipeRefreshLayout / recyclerView /
 * emptyTip / loadMoreTip，子类只负责 `setPageName` + 注册刷新 + 加载。照抄 `AnnouncementsActivity`。
 */
class FeedbackListActivity : RefreshListActivity() {

    /**
     * 从详情页回来时，**只有真的改动过**（追加了说明）才重拉。
     *
     * <p>不无条件在 `onResume` 里重拉：那会让每次从详情页返回都多一次请求，而绝大多数返回
     * 什么都没改。用结果码只在必要时拉。
     *
     * <p>注册成属性初始化（与本工程 `DynamicActivity` / `SendDynamicActivity` 一致）——
     * 必须在 Activity 进入 STARTED 之前完成注册，放 `onCreate` 里也安全，但字段更不容易漏。
     */
    /** 最近一次拉到的列表与本页的适配器：从详情页回来时只重算「新回复」，不再发一次请求。 */
    private var items: ArrayList<FeedbackItem>? = null
    private var adapter: FeedbackListAdapter? = null

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPageName(UiText.of("我的反馈"))

        setOnRefreshListener { load() }
        // 空态也要能重试：空态下基类会把 recyclerView 置成 GONE，SwipeRefreshLayout 没有可滑动的
        // 子视图、下拉刷新点不动，只能靠点空态文案恢复。文案在 load() 里按需覆盖。
        setOnEmptyRetry { load() }
        load()
    }

    override fun onResume() {
        super.onResume()
        // 从详情页返回：详情页已把这条标记成「看过」（FeedbackUnread.markSeen），
        // 列表内容本身没变，只需重算未读数并重绑一遍「新回复」标签。
        val current = items ?: return
        FeedbackUnread.refreshCount(current)
        adapter?.notifyDataSetChanged()
    }

    private fun load() {
        setRefreshing(true)
        CenterThreadPool.run {
            try {
                val list = TerminalApi.getMyFeedback()
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (list.isEmpty()) {
                        emptyView?.text = UiText.of("还没有提交过反馈\n可在「设置 - 关于与帮助 - 反馈与建议」里发一条\n点我重试")
                        showEmptyView()
                    } else {
                        hideEmptyView()
                    }
                    items = list
                    // 26.11.16：顺手用这份列表重算「新回复」条数（设置页入口要靠它显示 N 条）
                    FeedbackUnread.refreshCount(list)
                    val listAdapter = FeedbackListAdapter(this@FeedbackListActivity, list) { openDetail(it) }
                    adapter = listAdapter
                    setAdapter(listAdapter)
                    // 加载完必须复位：它是 isLoading / isRefreshing 的唯一复位信号，漏了翻页会永久卡死
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    setRefreshing(false)
                    MsgUtil.err(e)
                }
            }
        }
    }

    /** 整条（含对话）直接传给详情页，详情页不必再发一次请求。走 launcher 是为了拿结果码。 */
    private fun openDetail(item: FeedbackItem) {
        detailLauncher.launch(Intent(this, FeedbackDetailActivity::class.java).apply {
            putExtra("feedback", item)
        })
    }
}