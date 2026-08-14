package com.RobinNotBad.BiliClient.activity

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.InteractionDebugAdapter
import com.RobinNotBad.BiliClient.model.InteractionVideoData

class InteractionDebugActivity : BaseActivity() {

    companion object {
        private var staticInteractionData: InteractionVideoData? = null

        @JvmStatic
        fun setInteractionData(data: InteractionVideoData?) {
            staticInteractionData = data
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interaction_debug)

        setPageName("互动视频变量调试")

        if (staticInteractionData == null || staticInteractionData!!.hiddenVars == null || staticInteractionData!!.hiddenVars.isEmpty()) {
            finish()
            return
        }

        val recyclerView = findViewById<RecyclerView>(R.id.debug_var_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = InteractionDebugAdapter(staticInteractionData!!.hiddenVars)
        recyclerView.adapter = adapter
    }

    override fun onDestroy() {
        super.onDestroy()
        staticInteractionData = null
    }
}