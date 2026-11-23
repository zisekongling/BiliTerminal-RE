package com.RobinNotBad.BiliClient.activity.note

import android.os.Bundle
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.note.NoteContentAdapter
import com.RobinNotBad.BiliClient.api.NoteApi
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.api.VideoInfoApi
import com.RobinNotBad.BiliClient.model.Note
import com.RobinNotBad.BiliClient.model.NoteContentItem
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil

/**
 * 笔记详情页（26.11.23 新增）。
 *
 * <p>正文按「一行一项」渲染（{@link NoteContentItem}）：文字段带样式、图片真加载、标签可点跳进度。
 * 页面复用 {@link RefreshListActivity}，所以表冠滚动、下拉刷新、空态都是现成的；
 * 笔记标题/作者/视频信息做成列表的第 0 项（适配器里的 header），不额外占一块固定区域。
 *
 * <p>入参二选一：私有笔记 {@code aid + note_id}（要登录），公开笔记 {@code cvid}（免登录）。
 */
class NoteDetailActivity : RefreshListActivity() {

    private var aid = 0L
    private var noteId = ""
    private var cvid = 0L

    private var note: Note? = null
    private var items: List<NoteContentItem> = emptyList()
    private var contentAdapter: NoteContentAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.uitext_noteactivity_001))

        aid = intent.getLongExtra("aid", 0L)
        noteId = intent.getStringExtra("note_id").orEmpty()
        cvid = intent.getLongExtra("cvid", 0L)

        if (noteId.isEmpty() && cvid <= 0L) {
            emptyView?.text = getString(R.string.uitext_noteactivity_003)
            showEmptyView()
            setRefreshing(false)
            return
        }

        setOnRefreshListener { load() }
        setOnEmptyRetry(Runnable { load() })
        load()
    }

    private fun load() {
        setRefreshing(true)
        CenterThreadPool.run {
            val loaded: Note = try {
                if (noteId.isNotEmpty()) NoteApi.getNoteInfo(aid, noteId)
                else NoteApi.getPublicNoteInfo(cvid)
            } catch (e: Exception) {
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    emptyView?.text = NoteApi.failureText(e) + "\n" +
                        getString(R.string.note_list_retry_tail)
                    showEmptyView()
                    setRefreshing(false)
                }
                return@run
            }

            val content = NoteApi.buildContent(loaded.blocks)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (content.isEmpty()) {
                    emptyView?.text = getString(R.string.uitext_noteactivity_007)
                    showEmptyView()
                    setRefreshing(false)
                    return@runOnUiThread
                }
                note = loaded
                items = content
                val adapter = NoteContentAdapter(this@NoteDetailActivity, loaded, content) { jumpTo(it) }
                contentAdapter = adapter
                setAdapter(adapter)
                hideEmptyView()
                setRefreshing(false)
            }
        }
    }

    /**
     * 点跳转标签：按标签的 cid 定位分P，带上秒数直接起播放器。
     *
     * {@code PlayerData.progress} 的单位是**毫秒**（见 PlayerData 与 JumpToPlayerActivity），
     * 而标签给的是秒，这里必须 *1000——少乘或错乘的表现都是「跳到了视频开头/某个奇怪的位置」。
     */
    private fun jumpTo(item: NoteContentItem) {
        val current = note ?: return
        val targetAid = if (current.aid > 0L) current.aid else aid
        if (targetAid <= 0L) {
            MsgUtil.showMsg(getString(R.string.note_tag_no_video))
            return
        }
        CenterThreadPool.run {
            try {
                val videoInfo = VideoInfoApi.getVideoInfo(targetAid)
                var index = videoInfo.cids.indexOf(item.tagCid)
                if (index < 0) index = 0
                val data: PlayerData? = videoInfo.toPlayerData(index)
                if (data == null) {
                    runOnUiThread {
                        if (!isDestroyed) MsgUtil.showMsg(getString(R.string.note_tag_no_video))
                    }
                    return@run
                }
                data.progress = (item.tagSeconds * 1000L).toInt()
                if (isFinishing || isDestroyed) return@run
                PlayerApi.startGettingUrl(data)
            } catch (e: Exception) {
                runOnUiThread { if (!isDestroyed) MsgUtil.err(e) }
            }
        }
    }
}
