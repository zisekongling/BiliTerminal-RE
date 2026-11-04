package com.RobinNotBad.BiliClient.activity.video

import com.RobinNotBad.BiliClient.util.UiText

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
        textView.text = UiText.of("请选择分页")

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
                            MsgUtil.showMsg(if (fileSign.exists()) UiText.of("已在下载队列") else UiText.of("已下载完成"))
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
                        // #83：无论点的是不是「上次看的那个P」，都必须按**点中的分P** 重建 PlayerData。
                        // 旧实现写的是「点中的 cid != cidHistory 才重建」，于是点上次那一P 时会沿用
                        // 详情页预取的 P1，播出来是第 1P（cidHistory 现在会随 Intent 传过来了，这个坑更容易踩到）。
                        // 进度归属仍然只由 PlayerApi 按服务端 last_play_cid 判定，这里不插手。
                        if (MultiPageResumePolicy.isValidPageIndex(videoInfo.cids, position)) {
                            val lastPlayTimeMs = playerData?.lastPlayTimeMs ?: 0L
                            val pageData = videoInfo.toPlayerData(position)
                            pageData.lastPlayTimeMs = lastPlayTimeMs
                            // 归零：让 JumpToPlayerActivity 重拉 playurl，续播位置按这个分P 现算
                            pageData.timeStamp = 0
                            PlayerApi.startGettingUrl(pageData)
                        }
                    }
                }

                recyclerView.layoutManager = CustomLinearManager(this)
                recyclerView.adapter = adapter

                // 「进分P 选择层默认选中上次的P」：把这个列表滚到上次观看的分P 上，
                // 用户一进来就看得见自己上次看到哪儿（真正起播由详情页「播放」直接走，见 VideoInfoFragment）。
                val resumeIndex = MultiPageResumePolicy.resumePageIndex(
                    videoInfo.cids,
                    playerData?.cidHistory ?: 0L,
                    playerData?.lastPlayTimeMs ?: 0L
                )
                if (resumeIndex > 0) recyclerView.scrollToPosition(resumeIndex)
            }
        }
    }
}