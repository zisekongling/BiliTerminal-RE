package com.RobinNotBad.BiliClient.activity.dynamic

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.base.RefreshMainActivity
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicAdapter
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.helper.TutorialHelper
import com.RobinNotBad.BiliClient.model.Dynamic
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.util.regex.Pattern

class DynamicActivity : RefreshMainActivity() {

    private var dynamicList: ArrayList<Dynamic>? = null
    private var dynamicAdapter: DynamicAdapter? = null
    var recentUpList: List<DynamicApi.UpInfo>? = null
    private var offset: Long = 0
    private var firstRefresh: Boolean = true
    private var type: String = "all"

    companion object {
        private val typeNameMap = mapOf(
            "全部" to "all",
            "视频投稿" to "video",
            "追番" to "pgc",
            "专栏" to "article"
        )

        fun getRelayDynamicLauncher(activity: BaseActivity): ActivityResultLauncher<Intent> {
            return activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val code = result.resultCode
                val data = result.data
                if (code == RESULT_OK && data != null) {
                    var text = data.getStringExtra("text")
                    if (TextUtils.isEmpty(text)) text = "转发动态"
                    val dynamicId = data.getLongExtra("dynamicId", -1)
                    val finalText = text!!
                    CenterThreadPool.run {
                        try {
                            val atUids = HashMap<String, Long>()
                            val pattern = Pattern.compile("@(\\S+)\\s")
                            val matcher = pattern.matcher(finalText)
                            while (matcher.find()) {
                                val matchedString = matcher.group(1)
                                val uid: Long
                                if (DynamicApi.mentionAtFindUser(matchedString).also { uid = it } != -1L) {
                                    atUids[matchedString] = uid
                                }
                            }
                            val dynId = DynamicApi.relayDynamic(finalText, atUids.ifEmpty { null }, dynamicId)
                            if (dynId != -1L) {
                                activity.runOnUiThread { MsgUtil.showMsg("转发成功~") }
                            } else {
                                activity.runOnUiThread { MsgUtil.showMsg("转发失败") }
                            }
                        } catch (e: Exception) {
                            activity.runOnUiThread { MsgUtil.err(e) }
                        }
                    }
                }
            }
        }
    }

    val selectTypeLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.resultCode
        val data = result.data
        if (code == RESULT_OK && data != null && data.getStringExtra("item") != null) {
            val type = typeNameMap[data.getStringExtra("item")]
            if (type != null) {
                if (isRefreshing) {
                    MsgUtil.showMsg("还在加载中OvO")
                } else {
                    this.type = type
                    setRefreshing(true)
                    refreshDynamic()
                }
            }
        }
    }

    val writeDynamicLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.resultCode
        val data = result.data
        if (code == RESULT_OK && data != null) {
            val text = data.getStringExtra("text")
            CenterThreadPool.run {
                try {
                    val atUids = HashMap<String, Long>()
                    val pattern = Pattern.compile("@(\\S+)\\s")
                    val matcher = pattern.matcher(text)
                    while (matcher.find()) {
                        val matchedString = matcher.group(1)
                        val uid: Long
                        if (DynamicApi.mentionAtFindUser(matchedString).also { uid = it } != -1L) {
                            atUids[matchedString] = uid
                        }
                    }
                    val dynId = if (atUids.isEmpty()) {
                        DynamicApi.publishTextContent(text)
                    } else {
                        DynamicApi.publishTextContent(text, atUids)
                    }
                    if (dynId != -1L) {
                        runOnUiThread { MsgUtil.showMsg("发送成功~") }
                        CenterThreadPool.run {
                            try {
                                val dynamic = DynamicApi.getDynamic(dynId)
                                dynamicList!!.add(0, dynamic)
                                runOnUiThread {
                                    if (type == "all") {
                                        dynamicAdapter!!.notifyItemInserted(0)
                                        dynamicAdapter!!.notifyItemRangeChanged(0, dynamicList!!.size)
                                    }
                                }
                            } catch (e: Exception) {
                                MsgUtil.err(e)
                            }
                        }
                    } else {
                        runOnUiThread { MsgUtil.showMsg("发送失败") }
                    }
                } catch (e: Exception) {
                    runOnUiThread { MsgUtil.err(e) }
                }
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMenuClick()
        Log.e("debug", "进入动态页")

        setOnRefreshListener { refreshDynamic() }
        setOnLoadMoreListener { page -> addDynamic(type) }

        setPageName("动态")

        TutorialHelper.showTutorialList(this, R.array.tutorial_dynamic, 6)

        loadRecentUpList()
        refreshDynamic()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refreshDynamic() {
        Log.e("debug", "刷新")
        if (firstRefresh) {
            dynamicList = ArrayList()
        } else {
            offset = 0
            bottom = false
            dynamicList!!.clear()
            dynamicAdapter!!.notifyDataSetChanged()
        }

        loadRecentUpList()
        addDynamic(type, true)
    }

    private fun addDynamic(type: String) {
        addDynamic(type, false)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun addDynamic(type: String, refresh: Boolean) {
        Log.e("debug", "加载下一页")
        CenterThreadPool.run {
            try {
                val list = ArrayList<Dynamic>()
                offset = DynamicApi.getDynamicList(list, offset, 0, type)
                bottom = (offset == -1L)
                setRefreshing(false)

                runOnUiThread {
                    dynamicList!!.addAll(list)
                    if (firstRefresh) {
                        firstRefresh = false
                        dynamicAdapter = DynamicAdapter(this, dynamicList!!, recyclerView, recentUpList)
                        setAdapter(dynamicAdapter!!)
                    } else {
                        if (refresh) {
                            dynamicAdapter!!.notifyDataSetChanged()
                        } else {
                            val offset = if (showRecentUp()) 2 else 1
                            dynamicAdapter!!.notifyItemRangeInserted(dynamicList!!.size - list.size + offset, list.size)
                        }
                    }
                    if (refresh) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0)
                    }
                }

            } catch (e: Exception) {
                loadFail(e)
            }
        }
    }

    private fun loadRecentUpList() {
        CenterThreadPool.run {
            try {
                recentUpList = DynamicApi.getRecentUpList()
                runOnUiThread {
                    if (dynamicAdapter != null) {
                        dynamicAdapter!!.recentUpList = recentUpList
                        val shouldShow = showRecentUp()
                        val currentItemCount = dynamicAdapter!!.itemCount
                        val newItemCount = (if (dynamicList != null) dynamicList!!.size + 1 else 1) + (if (shouldShow) 1 else 0)
                        if (currentItemCount != newItemCount) {
                            if (shouldShow) {
                                dynamicAdapter!!.notifyItemInserted(1)
                            } else {
                                dynamicAdapter!!.notifyItemRemoved(1)
                            }
                        } else if (shouldShow) {
                            dynamicAdapter!!.notifyItemChanged(1)
                        }
                    }
                }
            } catch (e: Exception) {
                recentUpList = null
            }
        }
    }

    private fun showRecentUp(): Boolean {
        return SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.RECENT_UP_DISPLAY_ENABLE, true)
                && recentUpList != null && recentUpList!!.isNotEmpty()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DynamicHolder.GO_TO_INFO_REQUEST && resultCode == RESULT_OK) {
            try {
                if (data != null && !isRefreshing) {
                    val adapterPosition = data.getIntExtra("position", 0)
                    val offset = if (showRecentUp()) 2 else 1
                    val realPosition = adapterPosition - offset
                    if (realPosition >= 0 && realPosition < dynamicList!!.size) {
                        DynamicHolder.removeDynamicFromList(dynamicList!!, realPosition, dynamicAdapter!!, showRecentUp())
                    }
                }
            } catch (ignored: Throwable) {
            }
        }
    }
}