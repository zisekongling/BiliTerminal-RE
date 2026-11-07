package com.RobinNotBad.BiliClient.activity.video.local

import com.RobinNotBad.BiliClient.util.UiText

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
import com.RobinNotBad.BiliClient.util.LocalPageFileIndexPolicy
import com.RobinNotBad.BiliClient.util.MsgUtil
import java.io.File

class LocalPageChooseActivity : BaseActivity() {

    private var longClickPosition = -1
    private var longClickTimestamp: Long = 0

    /**
     * 是否真的删掉过分 P：见 [onDestroy]，用来通知缓存页重扫。
     *
     * 现在由后台删除线程写、[onDestroy] 主线程读，所以加 `@Volatile`（原实现是在主线程先置真再删，
     * 不删掉也照样置真——正是"提示删了、文件还在"的一部分）。
     */
    @Volatile
    private var deleted = false

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_list)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        findViewById<View>(R.id.top).setOnClickListener { finish() }

        val textView = findViewById<TextView>(R.id.pageName)
        textView.text = UiText.of("请选择分页")

        val intent = intent
        val title = intent.getStringExtra("title")!!
        val pageList = intent.getStringArrayListExtra("pageList")!!
        val videoFileList = intent.getStringArrayListExtra("videoFileList")!!
        val danmakuFileList = intent.getStringArrayListExtra("danmakuFileList")!!

        // 构建每个分页的播放文件映射，处理DASH双文件情况。
        // 必须可变：删掉一个分 P 后列表会变短，映射要跟着重建，否则点剩下的分 P 会播到已删除的文件。
        var pageMediaFiles = buildPageMediaFiles(pageList, videoFileList, title)

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
                MsgUtil.showMsg(UiText.of("没有找到播放器，请检查是否安装"))
            } catch (e: Exception) {
                MsgUtil.err(e)
            }
        }
        adapter.onItemLongClickListener = OnItemLongClickListener { position ->
            val timestamp = System.currentTimeMillis()
            if (longClickPosition == position && timestamp - longClickTimestamp < 4000) {
                longClickPosition = -1
                // 要删的目录名用点击这一刻的快照：删除跑在后台线程，期间列表可能已被刷新
                val pageName = pageList.getOrNull(position)
                if (pageName != null) {
                    CenterThreadPool.run {
                        val videoPath = File(FileUtil.getVideoDownloadPath(), title)
                        val pagePath = File(videoPath, pageName)

                        // 分 P 目录和（删掉最后一个分 P 后的）合集目录都要按返回值判断成败。
                        // 旧实现不看结果就把它从列表里摘掉并提示「删除成功」——删不掉的目录下次
                        // 进来又"复活"，与缓存页 #58「提示删了、文件还在」是同一类问题。
                        val pageDeleted = FileUtil.deleteFolderRecursively(pagePath)
                        val lastPage = pageList.size <= 1
                        // 合集根目录里还有封面、元数据、空目录等残留，删完最后一个分 P 一起清；
                        // 它删不掉同样算失败（否则合集目录原地留着，用户看到的还是"没删干净"）
                        val parentDeleted = if (pageDeleted && lastPage) {
                            FileUtil.deleteFolderRecursively(videoPath)
                        } else {
                            true
                        }
                        val failure: File? = when {
                            !pageDeleted -> pagePath
                            !parentDeleted -> videoPath
                            else -> null
                        }

                        // 只有在磁盘上真的删掉了才从模型里摘：失败时保持列表原样，用户能看到它确实还在
                        if (failure == null) {
                            // 双文件页（video.mp4 + audio.m4a 同时存在）在 videoFileList 里占两个下标，
                            // 旧实现只 removeAt(position) 一项：删中间页会把 audio.m4a 留给下一页、
                            // 点剩下的分 P 就播到已删文件。这里按目录名找出该页的全部下标，
                            // 再从大到小删（调研清单第 7 条 B2）。
                            val fileIndices = LocalPageFileIndexPolicy.indicesOfPage(videoFileList, pageName)
                                .ifEmpty {
                                    val fallback = LocalPageFileIndexPolicy.fallbackStartIndex(
                                        position, pageList.size, videoFileList.size
                                    )
                                    if (fallback >= 0) listOf(fallback) else emptyList()
                                }
                            for (i in LocalPageFileIndexPolicy.removalOrder(fileIndices)) {
                                if (i in videoFileList.indices) videoFileList.removeAt(i)
                            }
                            if (position in pageList.indices) pageList.removeAt(position)
                            if (position in danmakuFileList.indices) danmakuFileList.removeAt(position)
                            deleted = true
                        }

                        runOnUiThread {
                            if (failure == null) {
                                adapter.notifyItemRemoved(position)
                                adapter.notifyItemRangeChanged(0, (pageList.size - position).coerceAtLeast(0))
                                // 列表短了一项，分页→播放文件的映射要一起重建
                                pageMediaFiles = buildPageMediaFiles(pageList, videoFileList, title)
                                MsgUtil.showMsg(UiText.of("删除成功"))
                                // 缓存页还停在旧列表上（它是本页下面那层），直接让它重扫——别只靠
                                // onDestroy 的通知：用户删完立刻退出时后台删除可能还没跑完，
                                // 通知就漏了，缓存页会一直显示已经删掉的条目（与 #58 同一类现象）。
                                val listPage = BiliTerminal.getInstanceActivityOnTop()
                                if (listPage is LocalListActivity && !listPage.isDestroyed) {
                                    listPage.refresh()
                                    // 已经直接通知过上层了，onDestroy 里不必再刷一次
                                    deleted = false
                                }
                            } else {
                                // 手表屏幕窄，只报目录名不报完整路径；与缓存页删除失败共用同一条文案
                                MsgUtil.showMsgLong(getString(R.string.msg_cache_delete_failed, failure.name))
                            }
                        }
                    }
                }
            } else {
                longClickPosition = position
                longClickTimestamp = timestamp
                MsgUtil.showMsg(UiText.of("再次长按删除"))
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
                // 兼容旧格式：直接从列表取。优先按目录名反查（双文件页占两项也不会错位），
                // 匹配不到才退回「第几页 = 第几项」的老口径（调研清单第 7 条 B2）
                val idx = LocalPageFileIndexPolicy.indicesOfPage(videoFileList, pageName).firstOrNull()
                    ?: result.size
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
