package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.DragAdapter
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.util.Collections

/**
 * 搜索类别排序页面
 * 支持拖拽排序，调整ViewPager中搜索类别的展示顺序
 * 视频类别固定在首位，不可拖动
 */
class SearchSortActivity : BaseActivity() {

    private val data: ArrayList<String> = ArrayList()
    private val displayKeyMap: MutableMap<String, String> = HashMap()

    companion object {
        val defaultOrder = arrayOf("video", "article", "user", "audio", "live")
        val categoryNames = mapOf(
            "video" to "视频",
            "article" to "专栏",
            "user" to "用户",
            "audio" to "音频",
            "live" to "直播"
        )
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_list)

        setPageName("搜索类别排序")

        val sortConf = SharedPreferencesUtil.getString(SharedPreferencesUtil.SEARCH_CATEGORY_SORT, "")
        var splitName: List<String>? = null

        if (!TextUtils.isEmpty(sortConf) &&
            sortConf.split(";").also { splitName = it }.size == defaultOrder.size) {
            for (name in splitName!!) {
                if (!categoryNames.containsKey(name)) {
                    loadDefaultOrder()
                    break
                } else {
                    val displayText = categoryNames[name]!!
                    data.add(displayText)
                    displayKeyMap[displayText] = name
                }
            }
        } else {
            loadDefaultOrder()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val dragAdapter = DragAdapter(this, data)
        recyclerView.adapter = dragAdapter

        // 使用自定义拖拽回调：仅支持拖动排序，不支持滑动删除
        val dragCallBack = SearchDragCallBack(dragAdapter, data)
        val itemTouchHelper = ItemTouchHelper(dragCallBack)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        dragAdapter.setOnItemClickListener(object : DragAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {}

            override fun onItemLongClick(holder: DragAdapter.ViewHolder) {
                // 视频固定在首位，不允许拖动
                if (holder.adapterPosition != 0) {
                    itemTouchHelper.startDrag(holder)
                }
            }
        })

        dragAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                save()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        MsgUtil.showMsg("长按拖动以排序（视频始终在第一位）")
    }

    override fun onPause() {
        super.onPause()
        save()
        MsgUtil.showMsg("排序已保存")
    }

    private fun loadDefaultOrder() {
        data.clear()
        displayKeyMap.clear()
        for (key in defaultOrder) {
            val displayText = categoryNames[key]!!
            data.add(displayText)
            displayKeyMap[displayText] = key
        }
    }

    private fun save() {
        val sb = StringBuilder()
        var flag = false
        for (s in data) {
            if (displayKeyMap.containsKey(s)) {
                if (flag) {
                    sb.append(";")
                } else {
                    flag = true
                }
                sb.append(displayKeyMap[s])
            }
        }
        SharedPreferencesUtil.putString(SharedPreferencesUtil.SEARCH_CATEGORY_SORT, sb.toString())
    }

    /**
     * 搜索类别排序专用拖拽回调
     * 仅支持拖动排序，不支持滑动删除
     * 视频类别（位置0）固定在首位，不可移动
     */
    internal class SearchDragCallBack(
        private val mAdapter: DragAdapter,
        private val mData: ArrayList<String>
    ) : ItemTouchHelper.Callback() {

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            // 仅支持上下拖动，不支持滑动删除
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            return makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.adapterPosition
            val toPosition = target.adapterPosition

            // 视频固定在首位，不允许移动到位置0，也不允许移动位置0的视频
            if (fromPosition == 0 || toPosition == 0) {
                return false
            }

            if (fromPosition < toPosition) {
                for (index in fromPosition until toPosition) {
                    Collections.swap(mData, index, index + 1)
                }
            } else {
                for (index in fromPosition downTo toPosition + 1) {
                    Collections.swap(mData, index, index - 1)
                }
            }
            mAdapter.notifyItemMoved(fromPosition, toPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // 不支持滑动删除，不做任何操作
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                viewHolder?.let {
                    ViewCompat.animate(it.itemView).setDuration(200).scaleX(1.3F).scaleY(1.3F).start()
                }
            }
            super.onSelectedChanged(viewHolder, actionState)
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            ViewCompat.animate(viewHolder.itemView).setDuration(200).scaleX(1F).scaleY(1F).start()
            super.clearView(recyclerView, viewHolder)
        }

        override fun isLongPressDragEnabled(): Boolean {
            return true
        }

        override fun isItemViewSwipeEnabled(): Boolean {
            return false  // 禁用滑动删除
        }
    }
}