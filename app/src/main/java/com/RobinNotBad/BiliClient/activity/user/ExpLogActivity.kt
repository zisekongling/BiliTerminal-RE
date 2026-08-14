package com.RobinNotBad.BiliClient.activity.user

import android.os.Bundle
import android.view.View

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.ExpLogAdapter
import com.RobinNotBad.BiliClient.api.ExpLogApi
import com.RobinNotBad.BiliClient.model.ExpLog
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil

class ExpLogActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var logList: ArrayList<ExpLog> = arrayListOf()
    private var adapter: ExpLogAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_refresh)

        setPageName("经验变化记录")

        recyclerView = findViewById(R.id.recyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        swipeRefreshLayout.isEnabled = false
        swipeRefreshLayout.isRefreshing = true

        recyclerView.layoutManager = LinearLayoutManager(this)

        logList = ArrayList()

        CenterThreadPool.run {
            try {
                logList = ArrayList(ExpLogApi.getExpLog())

                runOnUiThread {
                    if (logList.isEmpty()) {
                        MsgUtil.showMsg("暂无经验变化记录")
                        findViewById<View>(R.id.emptyTip).visibility = View.VISIBLE
                    } else {
                        adapter = ExpLogAdapter(this, logList)
                        recyclerView.adapter = adapter
                    }
                    swipeRefreshLayout.isRefreshing = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    MsgUtil.showMsg("加载失败：" + e.message)
                    swipeRefreshLayout.isRefreshing = false
                    findViewById<View>(R.id.emptyTip).visibility = View.VISIBLE
                }
                e.printStackTrace()
            }
        }
    }
}