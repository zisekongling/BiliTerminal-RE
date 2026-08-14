package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.util.Pair
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.MenuActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.adapter.DragAdapter
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.util.Collections

class SortSettingActivity : BaseActivity() {

    private val data: ArrayList<String> = ArrayList()
    private val displayKeyMap: MutableMap<String, String> = HashMap()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_list)

        setPageName("菜单排序")

        val sortConf = SharedPreferencesUtil.getString(SharedPreferencesUtil.MENU_SORT, "")
        var splitName: List<String>? = null
        if (!TextUtils.isEmpty(sortConf) && sortConf.split(";").also { splitName = it }.size == MenuActivity.btnNames.size) {
            for (name in splitName!!) {
                if (!MenuActivity.btnNames.containsKey(name)) {
                    data.clear()
                    displayKeyMap.clear()
                    for (entry in MenuActivity.btnNames.entries) {
                        val displayText = entry.value.first
                        data.add(displayText)
                        displayKeyMap[displayText] = entry.key
                    }
                    break
                } else {
                    val displayText = MenuActivity.btnNames[name]!!.first
                    data.add(displayText)
                    displayKeyMap[displayText] = name
                }
            }
        } else {
            for (entry in MenuActivity.btnNames.entries) {
                val key = entry.key
                val displayText = entry.value.first
                data.add(displayText)
                displayKeyMap[displayText] = key
            }
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val dragAdapter = DragAdapter(this, data)
        recyclerView.adapter = dragAdapter

        val dragCallBack = DragCallBack(dragAdapter, data)
        val itemTouchHelper = ItemTouchHelper(dragCallBack)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        dragAdapter.setOnItemClickListener(object : DragAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
            }

            override fun onItemLongClick(holder: DragAdapter.ViewHolder) {
                if (holder.adapterPosition != dragAdapter.getFixedPosition()) {
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
        MsgUtil.showMsg("拖动以排序~")
    }

    override fun onPause() {
        super.onPause()
        save()
        MsgUtil.showMsg("已保存")
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
        SharedPreferencesUtil.putString(SharedPreferencesUtil.MENU_SORT, sb.toString())
    }

    class DragCallBack(
        private val mAdapter: DragAdapter,
        private val mData: ArrayList<String>
    ) : ItemTouchHelper.Callback() {

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val dragFlags: Int
            val swipeFlags: Int = 0
            val layoutManager = recyclerView.layoutManager
            if (layoutManager is GridLayoutManager) {
                dragFlags = ItemTouchHelper.LEFT or ItemTouchHelper.UP or ItemTouchHelper.RIGHT or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, swipeFlags)
            } else if (layoutManager is LinearLayoutManager) {
                dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                val swipeFlags2 = ItemTouchHelper.START or ItemTouchHelper.END
                return makeMovementFlags(dragFlags, swipeFlags2)
            } else {
                return 0
            }
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.adapterPosition
            val toPosition = target.adapterPosition
            if (fromPosition == mAdapter.getFixedPosition() || toPosition == mAdapter.getFixedPosition()) {
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
            val position = viewHolder.adapterPosition
            mData.removeAt(position)
            mAdapter.notifyItemRemoved(position)
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
            when (recyclerView.layoutManager!!.javaClass.simpleName) {
                "GridLayoutManager", "LinearLayoutManager" -> {
                    ViewCompat.animate(viewHolder.itemView).setDuration(200).scaleX(1F).scaleY(1F).start()
                }
            }
            super.clearView(recyclerView, viewHolder)
        }

        override fun isLongPressDragEnabled(): Boolean {
            return super.isLongPressDragEnabled()
        }

        override fun isItemViewSwipeEnabled(): Boolean {
            return super.isItemViewSwipeEnabled()
        }
    }
}