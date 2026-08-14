package com.RobinNotBad.BiliClient.activity.reply

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Pair
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.RobinNotBad.BiliClient.activity.base.RefreshListFragment
import com.RobinNotBad.BiliClient.adapter.ReplyAdapter
import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.event.ReplyEvent
import com.RobinNotBad.BiliClient.model.Reply
import com.RobinNotBad.BiliClient.model.UserInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class ReplyFragment : RefreshListFragment() {

    private var dontload: Boolean = false
    var aid: Long = 0
    var mid: Long = 0
    var sort: Int = 3
    var type: Int = 0
    var count: Int = 0
    var replyList: ArrayList<Reply>? = null
    var replyAdapter: ReplyAdapter? = null
    var replyType: Int = ReplyApi.REPLY_TYPE_VIDEO
    private var seek: Long = 0
    private var pagination: String = ""
    private var isManager: Boolean = false

    companion object {
        fun newInstance(aid: Long, type: Int): ReplyFragment {
            val fragment = ReplyFragment()
            val args = Bundle()
            args.putLong("aid", aid)
            args.putInt("type", type)
            fragment.arguments = args
            return fragment
        }

        fun newInstance(aid: Long, type: Int, dontload: Boolean): ReplyFragment {
            val fragment = ReplyFragment()
            val args = Bundle()
            args.putLong("aid", aid)
            args.putInt("type", type)
            args.putBoolean("dontload", dontload)
            fragment.arguments = args
            return fragment
        }

        fun newInstance(aid: Long, type: Int, seek_rpid: Long): ReplyFragment {
            val fragment = ReplyFragment()
            val args = Bundle()
            args.putLong("aid", aid)
            args.putInt("type", type)
            args.putLong("seek", seek_rpid)
            fragment.arguments = args
            return fragment
        }

        fun newInstance(aid: Long, type: Int, dontload: Boolean, seek_rpid: Long): ReplyFragment {
            val fragment = ReplyFragment()
            val args = Bundle()
            args.putLong("aid", aid)
            args.putInt("type", type)
            args.putBoolean("dontload", dontload)
            args.putLong("seek", seek_rpid)
            fragment.arguments = args
            return fragment
        }

        fun newInstance(aid: Long, type: Int, count: Int, seek_rpid: Long, up_mid: Long): ReplyFragment {
            val fragment = ReplyFragment()
            val args = Bundle()
            args.putLong("aid", aid)
            args.putInt("count", count)
            args.putInt("type", type)
            args.putLong("seek", seek_rpid)
            args.putLong("mid", up_mid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            aid = arguments!!.getLong("aid", 0)
            count = arguments!!.getInt("count", 0)
            type = arguments!!.getInt("type", 0)
            replyType = type
            dontload = arguments!!.getBoolean("dontload", false)
            seek = arguments!!.getLong("seek", -1)
            mid = arguments!!.getLong("mid", -1)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setForceSingleColumn()
        super.onViewCreated(view, savedInstanceState)

        if (SharedPreferencesUtil.getBoolean("ui_landscape", false) && !SharedPreferencesUtil.getBoolean("ui_mobile_mode", false)) {
            val windowManager = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= 17) display.getRealMetrics(metrics)
            else display.getMetrics(metrics)
            val paddings = metrics.widthPixels / 6
            recyclerView.setPadding(paddings, 0, paddings, 0)
        }

        setOnRefreshListener { refresh(aid) }
        setOnLoadMoreListener { continueLoading(it) }

        Log.e("debug-av号", aid.toString())

        replyList = ArrayList()

        if (!dontload) refresh(aid)
    }

    fun setManager(source: Any?) {
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) return

        try {
            if (source != null) {
                if (source is List<*>) {
                    val staffs = source as List<UserInfo>
                    for (userInfo in staffs) {
                        if (userInfo.mid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) {
                            isManager = true
                            break
                        }
                    }
                } else if (source is UserInfo) {
                    isManager = source.mid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
                }
            }
        } catch (e: Exception) {
            MsgUtil.err(e)
        }
    }

    private fun createReplyAdapter(): ReplyAdapter {
        return ReplyAdapter(requireContext(), replyList!!, aid, mid, 0L, replyType, sort, replyType)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun continueLoading(page: Int) {
        CenterThreadPool.run {
            try {
                val list = ArrayList<Reply>()
                val pageState = ReplyApi.getRepliesLazy(aid, 0, pagination, type, sort, list)
                val result = pageState.first
                this.pagination = pageState.second
                setRefreshing(false)
                if (result != -1) {
                    Log.e("debug", "下一页")
                    runOnUiThread {
                        replyList!!.addAll(list)
                        if (replyAdapter != null)
                            replyAdapter!!.notifyItemRangeInserted(replyList!!.size - list.size + 1, list.size)
                    }
                    if (result == 1) {
                        Log.e("debug", "到底了")
                        bottom = true
                    }
                }
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    fun notifyReplyInserted(replyEvent: ReplyEvent) {
        if (replyEvent.oid != aid) return
        val reply = replyEvent.message
        if (reply.root == 0L) {
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            var pos = layoutManager.findFirstCompletelyVisibleItemPosition()
            pos = Math.max(pos, 0)
            replyList!!.add(pos, reply)
            val finalPos = pos
            runOnUiThread {
                replyAdapter!!.notifyItemInserted(finalPos)
                replyAdapter!!.notifyItemRangeChanged(finalPos, replyList!!.size - finalPos + 1)
                layoutManager.scrollToPositionWithOffset(finalPos + 1, 0)
            }
        } else if (replyEvent.pos >= 0) {
            replyList!![replyEvent.pos].childMsgList.add(reply)
            replyList!![replyEvent.pos].childCount++
            runOnUiThread { replyAdapter!!.notifyItemChanged(replyEvent.pos + 1) }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refresh(aid: Long) {
        pagination = ""
        this.aid = aid
        setRefreshing(true)
        CenterThreadPool.run {
            try {
                val list = ArrayList<Reply>()
                val pageState = ReplyApi.getRepliesLazy(aid, seek, pagination, type, sort, list)
                val result = pageState.first
                this.pagination = pageState.second
                setRefreshing(false)
                if (result != -1 && isAdded) {
                    runOnUiThread {
                        if (!isAdded) return@runOnUiThread
                        if (replyList != null) replyList!!.clear()
                        else replyList = ArrayList()
                        replyList!!.addAll(list)
                        if (replyAdapter == null) {
                            val adapter = createReplyAdapter()
                            replyAdapter = adapter
                            adapter.count = count.toLong()
                            adapter.isManager = isManager
                            setOnSortSwitch()
                            setAdapter(adapter)
                        } else {
                            replyAdapter!!.notifyDataSetChanged()
                        }
                    }
                    if (result == 1) {
                        Log.e("debug", "到底了")
                        bottom = true
                    } else bottom = false
                }
            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    private fun setOnSortSwitch() {
        replyAdapter!!.setOnSortSwitchListener {
            sort = if (sort == 2) 3 else 2
            replyAdapter!!.sort = this.sort
            refresh(aid)
        }
    }
}