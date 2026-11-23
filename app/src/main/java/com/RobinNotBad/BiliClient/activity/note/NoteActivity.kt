package com.RobinNotBad.BiliClient.activity.note

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.note.NoteSummaryAdapter
import com.RobinNotBad.BiliClient.api.NoteApi
import com.RobinNotBad.BiliClient.model.NoteListPage
import com.RobinNotBad.BiliClient.model.NoteSummary
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 笔记列表页（26.11.23 改造）。
 *
 * <p>此前这一页只显示「自己的私有笔记」，且入口默认关闭，未登录直接一句「登录后才能看笔记喵~」。
 * 现在改成列表：第一行是「我的笔记」（登录且自己给这个稿件写过笔记时才有），
 * 后面是**公开笔记**——公开笔记接口免登录可看，所以未登录进来也有内容。
 *
 * <p>公开笔记是**逐视频开关**，关掉的视频接口不报错而是回 `show_public_note=false`，
 * 所以空态要分「功能已关闭 / 还没有人发布 / 请求失败」三种，不能一句「啥都木有」了事。
 */
class NoteActivity : RefreshListActivity() {

    companion object {
        private const val TAG = "BiliNote"
        private const val PAGE_SIZE = 10
    }

    private var aid = 0L
    private var items = ArrayList<NoteSummary>()
    private var noteAdapter: NoteSummaryAdapter? = null

    /**
     * 代次 token：下拉刷新会 ++，让在途的旧翻页请求作废，
     * 免得旧响应晚到后把刚刷出来的新列表又接在后面。
     */
    @Volatile
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.uitext_noteactivity_001))

        aid = intent.getLongExtra("aid", 0L)
        if (aid <= 0L) {
            emptyView?.text = getString(R.string.uitext_noteactivity_003)
            showEmptyView()
            setRefreshing(false)
            return
        }

        setOnRefreshListener { loadFirstPage() }
        setOnEmptyRetry(Runnable { loadFirstPage() })
        setOnLoadMoreListener { p -> loadMore(p) }
        loadFirstPage()
    }

    /**
     * 首屏 / 下拉刷新。
     *
     * 两个容易踩的点：
     * 1. 无论成功失败都要 setRefreshing(false) 复位，否则基类的翻页入口守卫会一直卡住；
     * 2. 失败**不等于到底**：只回 bottom = false + 可重试空态，别让一次断网就把翻页锁死。
     */
    private fun loadFirstPage() {
        val gen = ++loadGeneration
        setRefreshing(true)
        CenterThreadPool.run {
            val ownNote = loadOwnNote()

            val result: NoteListPage = try {
                NoteApi.getPublicNoteList(aid, 1, PAGE_SIZE)
            } catch (e: Exception) {
                runOnUiThread {
                    if (isDestroyed || gen != loadGeneration) return@runOnUiThread
                    // 不调 loadFail()：它会把 page--，而首屏 page 还是 1，会退成 0
                    bottom = false
                    showEmptyWith(NoteApi.failureText(e))
                    setRefreshing(false)
                    MsgUtil.err(e)
                }
                return@run
            }

            val fresh = ArrayList<NoteSummary>()
            ownNote?.let { fresh.add(it) }
            fresh.addAll(result.items)

            runOnUiThread {
                if (isDestroyed || gen != loadGeneration) return@runOnUiThread
                // 就地替换：适配器持有的是同一个 list 引用，换引用会继续显示旧数据
                items.clear()
                items.addAll(fresh)
                page = 1
                bottom = !result.hasMore
                if (noteAdapter == null) {
                    noteAdapter = NoteSummaryAdapter(this, items) { openNote(it) }
                    setAdapter(noteAdapter!!)
                } else {
                    noteAdapter!!.notifyDataSetChanged()
                }
                if (items.isEmpty()) showEmptyWith(emptyReason(result)) else hideEmptyView()
                setRefreshing(false)
            }
        }
    }

    /**
     * 「我的笔记」那一行。未登录、这个稿件没写过、接口报错都返回 null——
     * 少一行而已，不能连累免登录就能看的公开笔记列表。
     */
    private fun loadOwnNote(): NoteSummary? {
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L) == 0L) return null

        val noteId = try {
            NoteApi.getNoteIdsOfVideo(aid).firstOrNull().orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "读取我的笔记 id 失败：" + e.javaClass.simpleName + " " + e.message)
            return null
        }
        if (noteId.isEmpty()) return null

        val summary = NoteSummary()
        summary.own = true
        summary.noteId = noteId
        // 顺手取一次详情只为拿标题：列表里一行「无标题笔记」用户根本认不出是哪篇。
        // 失败仍然返回这一行（标题走兜底文案），笔记本身还是能点开的。
        try {
            val detail = NoteApi.getNoteInfo(aid, noteId)
            summary.title = detail.title
            summary.summary = detail.summary
        } catch (e: Exception) {
            Log.w(TAG, "读取我的笔记详情失败：" + e.javaClass.simpleName + " " + e.message)
        }
        return summary
    }

    private fun loadMore(p: Int) {
        val gen = loadGeneration
        CenterThreadPool.run {
            try {
                val result = NoteApi.getPublicNoteList(aid, p, PAGE_SIZE)
                runOnUiThread {
                    if (isDestroyed || gen != loadGeneration) return@runOnUiThread
                    val start = items.size
                    if (result.items.isNotEmpty()) {
                        items.addAll(result.items)
                        noteAdapter?.notifyItemRangeInserted(start, result.items.size)
                    }
                    bottom = !result.hasMore
                    setRefreshing(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isDestroyed || gen != loadGeneration) return@runOnUiThread
                    // 失败只说明这一页没拿到，不是到底：退页码复位刷新，用户滑到底还能再试
                    page--
                    setRefreshing(false)
                    MsgUtil.err(e)
                }
            }
        }
    }

    /** 空态原因：功能被 UP 主关了 / 服务端给了说明 / 反正就是没有。 */
    private fun emptyReason(result: NoteListPage): String {
        if (!result.showPublicNote) return getString(R.string.note_list_public_off)
        if (result.message.isNotEmpty()) return result.message
        return getString(R.string.uitext_noteactivity_004)
    }

    /** 空态带上「点我重试」尾巴：setOnEmptyRetry 设过点击监听，但文案此时已被我们换掉了。 */
    private fun showEmptyWith(reason: String) {
        emptyView?.text = reason + "\n" + getString(R.string.note_list_retry_tail)
        showEmptyView()
    }

    private fun openNote(item: NoteSummary) {
        val intent = Intent(this, NoteDetailActivity::class.java)
        intent.putExtra("aid", aid)
        if (item.own) {
            intent.putExtra("note_id", item.noteId)
        } else {
            intent.putExtra("cvid", item.cvid)
        }
        startActivity(intent)
    }
}
