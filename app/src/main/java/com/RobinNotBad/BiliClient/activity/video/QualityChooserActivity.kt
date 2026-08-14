package com.RobinNotBad.BiliClient.activity.video

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import java.util.Arrays

class QualityChooserActivity : BaseActivity() {

    private var qns: IntArray? = null
    private var isAudioOnlyOption = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // 强制高分辨率选项：qn值 -> 显示名称
    private val forcedQualityMap = linkedMapOf(
        120 to "4K 超清",
        112 to "1080P 高码率",
        80 to "1080P 高清"
    )

    // 每个强制选项对应的 fnval（4048: DASH|HDR|4K|杜比全景声|杜比视界|8K|AV1）
    private val forcedQualityFnval = mapOf(
        120 to 4048,
        112 to 4048,
        80 to 4048
    )

    // 标记哪些位置是强制添加的（非API返回）
    private val forcedPositions = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_simple_list)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        findViewById<View>(R.id.top).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        (findViewById<TextView>(R.id.pageName)).text = "请选择清晰度"

        val aid = intent.getLongExtra("aid", 0)
        val bvid = intent.getStringExtra("bvid")

        TerminalContext.getInstance().getVideoInfoByAidOrBvId(aid, bvid).observe(this) { result ->
            result.onSuccess { videoInfo ->
                val adapter = QualityChooseAdapter(this)
                val page = intent.getIntExtra("page", 0)
                CenterThreadPool.run {
                    try {
                        val playerData = videoInfo.toPlayerData(page)
                        // 以最高画质请求，确保1080P等高清选项出现在列表中
                        playerData.qn = 127
                        PlayerApi.getVideo(playerData, true)
                        qns = playerData.qnValueList

                        val qualityList = ArrayList(Arrays.asList(*playerData.qnStrList))

                        // 如果开启了强制高分辨率选项，添加API未返回的高分辨率选项
                        val forceHighQuality = SharedPreferencesUtil.getBoolean(
                            "force_high_quality_options", false
                        )
                        forcedPositions.clear()
                        if (forceHighQuality) {
                            val existingQns = qns?.toSet() ?: emptySet()
                            for ((qn, name) in forcedQualityMap) {
                                if (qn !in existingQns) {
                                    qualityList.add("$name [强制]")
                                    forcedPositions.add(qualityList.size - 1)
                                }
                            }
                        }

                        qualityList.add("仅音频")
                        isAudioOnlyOption = true

                        runOnUiThread { adapter.nameList = qualityList }
                    } catch (e: Exception) {
                        runOnUiThread { MsgUtil.showMsg("清晰度列表获取失败！") }
                        e.printStackTrace()
                    }
                }
                adapter.onItemClickListener = OnItemClickListener { position ->
                    if (qns == null) return@OnItemClickListener

                    if (isAudioOnlyOption && position == qns!!.size + forcedPositions.size) {
                        // 仅音频选项
                        CenterThreadPool.run {
                            try {
                                val playerData = videoInfo.toPlayerData(page)
                                playerData.qn = qns!![0]
                                PlayerApi.getVideoDash(playerData)

                                if (playerData.audioUrl == null || playerData.audioUrl!!.isEmpty()) {
                                    runOnUiThread { MsgUtil.showMsg("该视频没有可用的音频流") }
                                    return@run
                                }

                                PlayerApi.startDownloadingAudioOnly(videoInfo, page, qns!![0], playerData.audioUrl!!)
                                mainHandler.postDelayed({ finish() }, 200)
                            } catch (e: Exception) {
                                runOnUiThread {
                                    MsgUtil.showMsg("获取音频信息失败：" + e.message)
                                    e.printStackTrace()
                                }
                            }
                        }
                    } else if (position in forcedPositions) {
                        // 强制高分辨率选项：需要先验证该分辨率是否可用
                        val forcedIndex = forcedPositions.toList().indexOf(position)
                        val forcedEntry = forcedQualityMap.entries.elementAtOrNull(forcedIndex)
                        if (forcedEntry == null) return@OnItemClickListener

                        val targetQn = forcedEntry.key
                        val fnval = forcedQualityFnval[targetQn] ?: 16
                        val qualityName = forcedEntry.value

                        CenterThreadPool.run {
                            try {
                                val playerData = videoInfo.toPlayerData(page)
                                playerData.qn = targetQn

                                val success = PlayerApi.tryGetVideoWithFnval(playerData, fnval)
                                if (success) {
                                    // 获取成功，使用DASH格式下载
                                    PlayerApi.startDownloading(videoInfo, page, targetQn)
                                    mainHandler.postDelayed({ finish() }, 200)
                                } else {
                                    runOnUiThread {
                                        MsgUtil.showMsg("该视频不支持${qualityName}分辨率，缓存失败")
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    MsgUtil.showMsg("获取${qualityName}失败：${e.message}")
                                    e.printStackTrace()
                                }
                            }
                        }
                    } else {
                        // 正常API返回的分辨率选项
                        val qn = qns!![position]
                        PlayerApi.startDownloading(videoInfo, page, qn)
                        mainHandler.postDelayed({ finish() }, 200)
                    }
                }

                recyclerView.layoutManager = CustomLinearManager(this)
                recyclerView.adapter = adapter
            }
        }
    }
}