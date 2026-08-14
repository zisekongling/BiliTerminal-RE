package com.RobinNotBad.BiliClient.activity.settings

import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.util.LinkedHashMap

class SettingQualityActivity : BaseActivity() {
    private lateinit var adapter: QualityChooseAdapter

    companion object {
        @JvmField
        val qnMap: LinkedHashMap<String, Int> = LinkedHashMap<String, Int>().apply {
            put("360P", 16)
            put("720P", 64)
            put("1080P", 80)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_simple_list)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        findViewById<android.view.View>(R.id.top).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        setPageName("请选择清晰度")

        adapter = QualityChooseAdapter(this)
        adapter.nameList = ArrayList(qnMap.keys)
        adapter.onItemClickListener = OnItemClickListener { save(it) }

        recyclerView.layoutManager = CustomLinearManager(this)
        recyclerView.adapter = adapter
    }

    private fun save(position: Int) {
        val str = adapter.getName(position)
        if (qnMap.containsKey(str))
            SharedPreferencesUtil.putInt("play_qn", qnMap[str]!!)
        finish()
    }
}