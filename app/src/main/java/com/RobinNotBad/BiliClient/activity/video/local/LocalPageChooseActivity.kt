package com.RobinNotBad.BiliClient.activity.video.local

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.adapter.video.PageChooseAdapter
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import java.io.File

class LocalPageChooseActivity : BaseActivity() {

    private var longClickPosition = -1
    private var longClickTimestamp: Long = 0
    private var deleted = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_list)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        findViewById<View>(R.id.top).setOnClickListener { finish() }

        val textView = findViewById<TextView>(R.id.pageName)
        textView.text = "请选择分页"

        val intent = intent
        val title = intent.getStringExtra("title")!!
        val pageList = intent.getStringArrayListExtra("pageList")!!
        val videoFileList = intent.getStringArrayListExtra("videoFileList")!!
        val danmakuFileList = intent.getStringArrayListExtra("danmakuFileList")!!

        // 构建每个分页的播放文件映射，处理DASH双文件情况
        val pageMediaFiles = buildPageMediaFiles(pageList, videoFileList, title)

        val adapter = PageChooseAdapter(this, pageList)
        adapter.onItemClickListener = OnItemClickListener { position ->
            val playerData = PlayerData(PlayerData.TYPE_LOCAL)
            val mediaInfo = pageMediaFiles[position]
            playerData.videoUrl = mediaInfo.first
            if (position < danmakuFileList.size) {
                playerData.danmakuUrl = danmakuFileList[position]
            }
            playerData.title = pageList[position]
            try {
                val player = PlayerApi.jumpToPlayer(playerData)
                if (mediaInfo.first.endsWith("audio.m4a")) {
                    player.putExtra("audio_only", true)
                }
                // DASH双文件：传递外部音频轨道
                if (mediaInfo.second.isNotEmpty()) {
                    player.putExtra("audio_track_url", mediaInfo.second)
                }
                startActivity(player)
            } catch (e: ActivityNotFoundException) {
                MsgUtil.showMsg("没有找到播放器，请检查是否安装")
            } catch (e: Exception) {
                MsgUtil.err(e)
            }
        }
        adapter.onItemLongClickListener = OnItemLongClickListener { position ->
            val timestamp = System.currentTimeMillis()
            if (longClickPosition == position && timestamp - longClickTimestamp < 4000) {
                CenterThreadPool.run {
                    val workPath = FileUtil.getVideoDownloadPath()
                    val videoPath = File(workPath, title)
                    val pagePath = File(videoPath, pageList[position])

                    FileUtil.deleteFolder(pagePath)
                    pageList.removeAt(position)
                    videoFileList.removeAt(position)
                    danmakuFileList.removeAt(position)
                    if (pageList.isEmpty())
                        FileUtil.deleteFolder(videoPath)
                }

                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(0, pageList.size - position)

                MsgUtil.showMsg("删除成功")
                longClickPosition = -1

                deleted = true
            } else {
                longClickPosition = position
                longClickTimestamp = timestamp
                MsgUtil.showMsg("再次长按删除")
            }
        }

        recyclerView.layoutManager = CustomLinearManager(this)
        recyclerView.adapter = adapter
    }

    /**
     * 构建分页媒体文件映射，处理DASH双文件（video.mp4 + audio.m4a）情况
     * @return 每个分页对应的 Pair<主文件路径, 音频文件路径(可能为空)>
     */
    private fun buildPageMediaFiles(
        pageList: ArrayList<String>,
        videoFileList: ArrayList<String>,
        title: String
    ): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val workPath = FileUtil.getVideoDownloadPath()

        for (pageName in pageList) {
            val pageDir = File(File(workPath, title), pageName)
            val videoFile = File(pageDir, "video.mp4")
            val audioFile = File(pageDir, "audio.m4a")

            if (videoFile.exists() && audioFile.exists()) {
                // DASH双文件：主文件为视频，音频作为外部轨道
                result.add(Pair(videoFile.toString(), audioFile.toString()))
            } else if (videoFile.exists()) {
                result.add(Pair(videoFile.toString(), ""))
            } else if (audioFile.exists()) {
                result.add(Pair(audioFile.toString(), ""))
            } else {
                // 兼容旧格式：直接从列表取
                val idx = result.size
                if (idx < videoFileList.size) {
                    result.add(Pair(videoFileList[idx], ""))
                } else {
                    result.add(Pair("", ""))
                }
            }
        }
        return result
    }

    override fun onDestroy() {
        val instance = BiliTerminal.getInstanceActivityOnTop()
        if (deleted && instance is LocalListActivity && !instance.isDestroyed)
            instance.refresh()
        super.onDestroy()
    }
}
