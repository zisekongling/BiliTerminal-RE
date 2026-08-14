package com.RobinNotBad.BiliClient.activity.video

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.adapter.video.PageChooseAdapter
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.VideoInfo
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import java.io.File

class MultiPageActivity : BaseActivity() {
    private var videoInfo: VideoInfo? = null
    private var playerData: PlayerData? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_list)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        findViewById<View>(R.id.top).setOnClickListener { finish() }

        val textView = findViewById<TextView>(R.id.pageName)
        textView.text = "请选择分页"

        val intent = intent
        playerData = intent.getParcelableExtra("data")

        TerminalContext.getInstance().getVideoInfoByAidOrBvId(playerData!!.aid, "").observe(this) { result ->
            result.onSuccess { videoInfo ->
                this.videoInfo = videoInfo
                val adapter = PageChooseAdapter(this, videoInfo.pagenames)

                if (intent.getIntExtra("download", 0) == 1) {
                    adapter.onItemClickListener = OnItemClickListener { position ->
                        val rootPath = File(FileUtil.getVideoDownloadPath(), FileUtil.stringToFile(videoInfo.title))
                        val downPath = File(rootPath, FileUtil.stringToFile(videoInfo.pagenames[position]))
                        if (downPath.exists()) {
                            val fileSign = File(downPath, ".DOWNLOADING")
                            MsgUtil.showMsg(if (fileSign.exists()) "已在下载队列" else "已下载完成")
                        } else {
                            startActivity(
                                Intent()
                                    .putExtra("page", position)
                                    .setClass(this, QualityChooserActivity::class.java)
                                    .putExtra("aid", videoInfo.aid)
                                    .putExtra("bvid", videoInfo.bvid)
                            )
                        }
                    }
                } else {
                    adapter.onItemClickListener = OnItemClickListener { position ->
                        val cidCurr = videoInfo.cids[position]
                        if (cidCurr != playerData!!.cidHistory) {
                            playerData = videoInfo.toPlayerData(position)
                            playerData!!.cidHistory = cidCurr
                            playerData!!.timeStamp = 0
                        }

                        PlayerApi.startGettingUrl(playerData!!)
                        playerData!!.timeStamp = 0
                    }
                }

                recyclerView.layoutManager = CustomLinearManager(this)
                recyclerView.adapter = adapter
            }
        }
    }
}