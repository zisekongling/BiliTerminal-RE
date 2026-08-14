package com.RobinNotBad.BiliClient.activity.user

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.TextView

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.api.CreativeCenterApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.StringUtil

import org.json.JSONException
import org.json.JSONObject

class CreativeCenterActivity : BaseActivity() {
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        asyncInflate(R.layout.activity_creative_center) { _, _ ->
            CenterThreadPool.run {
                try {
                    val stats = CreativeCenterApi.getVideoStat()
                    val beUPTime = CreativeCenterApi.getBeUPTime().result as String

                    runOnUiThread {
                        try {
                            if (stats == null) {
                                MsgUtil.showMsg("先去成为UP主吧~")
                                finish()
                            } else {
                                setStatsText(R.id.totalFans_number, stats, "total_fans", "incr_fans")
                                setStatsText(R.id.totalClick_number, stats, "total_click", "incr_click")
                                setStatsText(R.id.totalLike_number, stats, "total_like", "inc_like")
                                setStatsText(R.id.totalCoin_number, stats, "total_coin", "inc_coin")
                                setStatsText(R.id.totalFavourite_number, stats, "total_fav", "inc_fav")
                                setStatsText(R.id.totalShare_number, stats, "total_share", "inc_share")
                                setStatsText(R.id.totalReply_number, stats, "total_reply", "incr_reply")
                                setStatsText(R.id.totalDm_number, stats, "total_dm", "incr_dm")
                                val textBeUpTime = findViewById<TextView>(R.id.beUpTime)
                                textBeUpTime.text = beUPTime
                            }
                        } catch (e: Exception) {
                            runOnUiThread { MsgUtil.err(e) }
                        }

                        val scrollView = findViewById<View>(R.id.scrollView)
                        scrollView.isFocusable = true
                        scrollView.isFocusableInTouchMode = true
                        scrollView.requestFocus()
                    }
                } catch (e: Exception) {
                    runOnUiThread { MsgUtil.err(e) }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @Throws(JSONException::class)
    private fun setStatsText(viewId: Int, jsonObject: JSONObject, totalKey: String, incrKey: String) {
        val textView = findViewById<TextView>(viewId)
        val totalValue = jsonObject.getInt(totalKey)
        val incrValue = jsonObject.getInt(incrKey)

        val totalText = StringUtil.toWan(totalValue.toLong())
        val incrSymbol = if (incrValue < 0) "" else "+"
        val incrText = StringUtil.toWan(incrValue.toLong())

        textView.text = totalText + incrSymbol + incrText
    }
}