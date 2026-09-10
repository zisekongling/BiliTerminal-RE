package com.RobinNotBad.BiliClient.adapter.video

import android.content.Context
import android.content.Intent
import com.RobinNotBad.BiliClient.activity.video.QualityChooserActivity
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.api.VideoInfoApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 列表项长按「快速缓存」的公共实现。
 *
 * 合并自 [VideoCardAdapter]、[HistoryVideoCardAdapter]、[UserVideoAdapter] 中三份逐字节相同的副本
 * （原为各 100 行的私有方法 `handleQuickCache` + `fetchVideoInfo`）。
 * 三个适配器都属于本包，卡片数据结构一致，因此无需参数化差异。
 */
object VideoQuickCache {

    /**
     * 按设置项 `cache_default_quality` 决定缓存行为：
     * `dialog` 弹清晰度选择页、`highest` 取最高可用清晰度、`audio_only` 仅缓存音频、
     * 其余值按整数清晰度直接缓存。
     */
    fun handle(context: Context, videoCard: VideoCard) {
        val qualitySetting = SharedPreferencesUtil.getString("cache_default_quality", "dialog")
        when (qualitySetting) {
            "dialog" -> {
                CenterThreadPool.run {
                    try {
                        val videoInfo = fetchVideoInfo(videoCard)
                        if (FileUtil.isVideoCached(videoInfo.title)) {
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("该视频已缓存，请先删除原缓存文件") }
                            return@run
                        }
                        CenterThreadPool.runOnUiThread {
                            val intent = Intent(context, QualityChooserActivity::class.java)
                            intent.putExtra("aid", videoCard.aid)
                            intent.putExtra("bvid", videoCard.bvid)
                            intent.putExtra("page", 0)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("获取视频信息失败") }
                        e.printStackTrace()
                    }
                }
            }
            "highest" -> {
                CenterThreadPool.run {
                    try {
                        val videoInfo = fetchVideoInfo(videoCard)
                        if (FileUtil.isVideoCached(videoInfo.title)) {
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("该视频已缓存，请先删除原缓存文件") }
                            return@run
                        }
                        val playerData = videoInfo.toPlayerData(0)
                        PlayerApi.getVideo(playerData, true)
                        val qnValues = playerData.qnValueList
                        if (qnValues != null && qnValues.isNotEmpty()) {
                            val highestQn = qnValues.maxOrNull() ?: qnValues[0]
                            PlayerApi.startDownloading(videoInfo, 0, highestQn)
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("已开始缓存") }
                        }
                    } catch (e: Exception) {
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("获取视频信息失败") }
                        e.printStackTrace()
                    }
                }
            }
            "audio_only" -> {
                CenterThreadPool.run {
                    try {
                        val videoInfo = fetchVideoInfo(videoCard)
                        if (FileUtil.isVideoCached(videoInfo.title)) {
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("该视频已缓存，请先删除原缓存文件") }
                            return@run
                        }
                        val playerData = videoInfo.toPlayerData(0)
                        PlayerApi.getVideo(playerData, true)
                        val qnValues = playerData.qnValueList
                        if (qnValues != null && qnValues.isNotEmpty()) {
                            PlayerApi.getVideoDash(playerData)
                            if (playerData.audioUrl == null || playerData.audioUrl!!.isEmpty()) {
                                CenterThreadPool.runOnUiThread { MsgUtil.showMsg("该视频没有可用的音频流") }
                                return@run
                            }
                            PlayerApi.startDownloadingAudioOnly(videoInfo, 0, qnValues[0], playerData.audioUrl!!)
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("已开始缓存音频") }
                        }
                    } catch (e: Exception) {
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("获取视频信息失败") }
                        e.printStackTrace()
                    }
                }
            }
            else -> {
                val qn = try { qualitySetting.toInt() } catch (e: Exception) { 64 }
                CenterThreadPool.run {
                    try {
                        val videoInfo = fetchVideoInfo(videoCard)
                        if (FileUtil.isVideoCached(videoInfo.title)) {
                            CenterThreadPool.runOnUiThread { MsgUtil.showMsg("该视频已缓存，请先删除原缓存文件") }
                            return@run
                        }
                        PlayerApi.startDownloading(videoInfo, 0, qn)
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("已开始缓存") }
                    } catch (e: Exception) {
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("获取视频信息失败") }
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    /** aid 优先，为 0 时回退用 bvid 查询。 */
    private fun fetchVideoInfo(videoCard: VideoCard): VideoInfo {
        return if (videoCard.aid != 0L) {
            VideoInfoApi.getVideoInfo(videoCard.aid)
        } else {
            VideoInfoApi.getVideoInfo(videoCard.bvid)
        } ?: throw Exception("无法获取视频信息")
    }
}
