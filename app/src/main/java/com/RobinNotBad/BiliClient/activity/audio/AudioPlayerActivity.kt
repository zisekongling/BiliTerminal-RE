package com.RobinNotBad.BiliClient.activity.audio

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.api.AudioApi
import com.RobinNotBad.BiliClient.model.AudioInfo
import com.RobinNotBad.BiliClient.model.AudioStream
import com.RobinNotBad.BiliClient.model.Lyric
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.NetWorkUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import tv.danmaku.ijk.media.player.IjkMediaPlayer

import java.io.File
import java.io.FileOutputStream

class AudioPlayerActivity : InstanceActivity() {

    private var ijkPlayer: IjkMediaPlayer? = null
    private var currentStream: AudioStream? = null
    private var currentAudio: AudioInfo? = null
    private var currentLyric: Lyric? = null
    /** 当前正在播放/尝试播放的 CDN 链接，用于出错时切换备用源 */
    private var currentUrl: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    private var playMode = 0
    private var isPlaying = false
    private var sid: Long = 0
    private var songTitle: String = ""
    private var songAuthor: String = ""
    private var songCover: String = ""

    /** 播放列表：上一首/下一首用。为空数组表示单曲（无列表上下文） */
    private var sidList = LongArray(0)
    /** 当前歌曲在 sidList 中的下标 */
    private var currentIndex = -1

    private lateinit var titleView: TextView
    private lateinit var authorView: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnFav: ImageButton
    private lateinit var btnMode: ImageButton
    private lateinit var btnDownload: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var lyricContainer: LinearLayout
    private lateinit var lyricScroll: ScrollView
    private lateinit var indicator0: android.view.View
    private lateinit var indicator1: android.view.View

    private var lyricViews = ArrayList<TextView>()
    private var currentLyricIndex = -1

    /** 当前歌曲收藏状态：null=未知，true=已收藏，false=未收藏 */
    private var favState: Boolean? = null

    private var isSeeking = false
    private var isSeekFromUser = false

    /** 网络播放超时检测（10 秒未进入播放则兜底） */
    private var prepareTimeoutRunnable: Runnable? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sid = intent.getLongExtra("sid", 0)
        songTitle = intent.getStringExtra("title") ?: ""
        songAuthor = intent.getStringExtra("author") ?: ""
        songCover = intent.getStringExtra("cover") ?: ""

        // 播放列表上下文：歌单/搜索页传入，用于上一首/下一首
        sidList = intent.getLongArrayExtra("sid_list") ?: LongArray(0)
        currentIndex = intent.getIntExtra("index", -1)
        if (sidList.isEmpty()) {
            // 单曲入口：只有当前 sid
            sidList = longArrayOf(sid)
            currentIndex = 0
        } else if (currentIndex < 0 || currentIndex >= sidList.size) {
            currentIndex = 0
        }

        asyncInflate(R.layout.activity_audio_player) { _, _ ->
            titleView = findViewById(R.id.song_title)
            authorView = findViewById(R.id.song_author)
            btnPlayPause = findViewById(R.id.btn_play_pause)
            btnPrev = findViewById(R.id.btn_prev)
            btnNext = findViewById(R.id.btn_next)
            btnFav = findViewById(R.id.btn_fav)
            btnMode = findViewById(R.id.btn_mode)
            btnDownload = findViewById(R.id.btn_download)
            seekBar = findViewById(R.id.seek_bar)
            lyricContainer = findViewById(R.id.lyric_container)
            lyricScroll = findViewById(R.id.lyric_scroll)
            indicator0 = findViewById(R.id.indicator_0)
            indicator1 = findViewById(R.id.indicator_1)

            titleView.text = songTitle
            authorView.text = songAuthor

            playMode = SharedPreferencesUtil.getInt("audio_play_mode", 0)
            updateModeIcon()

            btnPlayPause.setOnClickListener { togglePlayPause() }
            btnPrev.setOnClickListener { playPrev() }
            btnNext.setOnClickListener { playNext() }
            btnMode.setOnClickListener { cyclePlayMode() }
            btnFav.setOnClickListener { toggleFav() }
            btnDownload.setOnClickListener { showMoreMenu() }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        isSeekFromUser = true
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    isSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    isSeeking = false
                    val progress = seekBar.progress.toLong()
                    val duration = ijkPlayer?.duration?.toLong() ?: return
                    val target = progress * duration / 1000L
                    ijkPlayer?.seekTo(target)
                    isSeekFromUser = false
                }
            })

            setupPageSlider()
            loadByIndex(currentIndex)
        }
    }

    // ===== 按列表下标加载并播放 =====
    private fun loadByIndex(index: Int) {
        if (index < 0 || index >= sidList.size) return
        currentIndex = index
        sid = sidList[index]
        // 切歌时先清掉旧歌信息，标题等 loadAudio 拿到新歌信息后再更新
        songTitle = ""
        songAuthor = ""
        loadAudio(sid)
    }

    private fun playNext() {
        if (sidList.size <= 1) {
            MsgUtil.showMsg("已是最后一首")
            return
        }
        loadByIndex((currentIndex + 1) % sidList.size)
    }

    private fun playPrev() {
        if (sidList.size <= 1) {
            MsgUtil.showMsg("已是第一首")
            return
        }
        loadByIndex((currentIndex - 1 + sidList.size) % sidList.size)
    }

    // ===== 两页滑动（播放器页 <-> 歌词页）+ 指示器 =====
    private fun setupPageSlider() {
        val slider = findViewById<android.widget.HorizontalScrollView>(R.id.slider_wrapper)
        slider.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            val page = if (scrollX >= 160) 1 else 0
            indicator0.setBackgroundResource(if (page == 0) R.drawable.bg_page_indicator_active else R.drawable.bg_page_indicator)
            indicator1.setBackgroundResource(if (page == 1) R.drawable.bg_page_indicator_active else R.drawable.bg_page_indicator)
        }
    }

    private fun loadAudio(sid: Long) {
        CenterThreadPool.run {
            try {
                val audio = AudioApi.getAudioInfo(sid)
                currentAudio = audio
                if (audio != null) {
                    songTitle = audio.title
                    songAuthor = audio.author
                }
                val stream = AudioApi.getAudioStream(sid, 2)
                if (stream == null || stream.cdns.isNullOrEmpty()) {
                    CenterThreadPool.runOnUiThread {
                        MsgUtil.showMsg("获取音频流失败，尝试其他音质")
                        loadAudioFallback(sid)
                    }
                    return@run
                }
                currentStream = stream

                try {
                    val lyric = AudioApi.getLyric(sid)
                    currentLyric = lyric
                } catch (_: Exception) {}

                CenterThreadPool.runOnUiThread {
                    if (audio != null) {
                        titleView.text = audio.title
                        authorView.text = audio.author
                    } else {
                        titleView.text = songTitle
                        authorView.text = songAuthor
                    }
                    setupLyricViews()
                    loadFavState()
                    startPlayback(stream)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                CenterThreadPool.runOnUiThread {
                    MsgUtil.showMsg("加载失败: ${e.message}")
                }
            }
        }
    }

    private fun loadAudioFallback(sid: Long) {
        CenterThreadPool.run {
            try {
                val stream = AudioApi.getAudioStream(sid, 1)
                if (stream != null && !stream.cdns.isNullOrEmpty()) {
                    currentStream = stream
                    CenterThreadPool.runOnUiThread { startPlayback(stream) }
                } else {
                    val stream0 = AudioApi.getAudioStream(sid, 0)
                    if (stream0 != null && !stream0.cdns.isNullOrEmpty()) {
                        currentStream = stream0
                        CenterThreadPool.runOnUiThread { startPlayback(stream0) }
                    } else {
                        CenterThreadPool.runOnUiThread {
                            MsgUtil.showMsg("无法获取音频流")
                        }
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread {
                    MsgUtil.showMsg("加载失败: ${e.message}")
                }
            }
        }
    }

    // ===== 歌词 =====
    private fun setupLyricViews() {
        lyricContainer.removeAllViews()
        lyricViews.clear()
        currentLyricIndex = -1

        val lines = currentLyric?.lines ?: emptyList()
        if (lines.isEmpty()) {
            val tip = TextView(this).apply {
                text = "暂无歌词"
                setTextSize(13f)
                setTextColor(0xFF666666.toInt())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 20 }
            }
            lyricContainer.addView(tip)
            return
        }

        for (line in lines) {
            val tv = TextView(this).apply {
                text = line.text.ifEmpty { " " }
                setTextSize(13f)
                setTextColor(0xFF888888.toInt())
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8 }
            }
            lyricContainer.addView(tv)
            lyricViews.add(tv)
        }
    }

    private fun syncLyricToPosition(positionMs: Long) {
        val lines = currentLyric?.lines ?: return
        if (lines.isEmpty() || lyricViews.isEmpty()) return

        var index = 0
        for (i in lines.indices) {
            if (lines[i].time <= positionMs) index = i else break
        }
        if (index == currentLyricIndex) return
        currentLyricIndex = index

        for (i in lyricViews.indices) {
            val tv = lyricViews[i]
            if (i == index) {
                tv.setTextColor(0xFFFFFFFF.toInt())
                tv.setTextSize(14f)
            } else {
                tv.setTextColor(0xFF888888.toInt())
                tv.setTextSize(13f)
            }
        }
        lyricScroll.post {
            val target = lyricViews[index]
            if (target.top > 0) {
                lyricScroll.smoothScrollTo(0, target.top - lyricScroll.height / 3)
            }
        }
    }

    // ===== 播放（IjkPlayer 内核，UA 伪装为浏览器，解决 B 站 CDN 403） =====
    private fun startPlayback(stream: AudioStream) {
        releasePlayer()
        currentStream = stream

        // 切歌时重置 UI 状态
        seekBar.progress = 0
        isPlaying = false
        isSeekFromUser = false
        currentLyricIndex = -1
        favState = null
        btnPlayPause.setImageResource(R.drawable.btn_player_play)
        btnFav.setImageResource(R.drawable.ic_audio_heart)

        try {
            IjkMediaPlayer.loadLibrariesOnce(null)
            val player = IjkMediaPlayer().apply {
                // 音频模式：禁用视频解码
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn", 1)
                setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 48)
                setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)
                setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1)
                setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100)

                setOnPreparedListener {
                    prepareTimeoutRunnable?.let { handler.removeCallbacks(it) }
                    prepareTimeoutRunnable = null
                    it.start()
                    this@AudioPlayerActivity.isPlaying = true
                    btnPlayPause.setImageResource(R.drawable.btn_player_pause)
                    seekBar.max = 1000
                    startProgressUpdates()
                }

                setOnCompletionListener {
                    onPlaybackComplete()
                }

                setOnErrorListener { _, frameworkErr, implErr ->
                    Logu.e("AudioPlayer", "IjkPlayer 错误 framework=$frameworkErr impl=$implErr")
                    tryNextSource()
                    true
                }
            }

            // 关键：app 端音频链接（platform=android）要求【无 Referer】+ 非空浏览器 UA。
            // 通过 setDataSource(url, headers) 只传 User-Agent（不传 Referer），
            // 与视频播放器同机制，确保 UA 生效且不带 Referer，避免 CDN 403。
            val audioHeaders = HashMap<String, String>()
            audioHeaders["User-Agent"] = NetWorkUtil.USER_AGENT_WEB

            try {
                currentUrl = stream.cdns!![0]
                player.setDataSource(currentUrl, audioHeaders)
                player.prepareAsync()

                // 超时兜底：10 秒内未进入播放（onPrepared），判定网络播放失败，
                // 走 tryNextSource -> 无备用源时自动下载到本地再播。
                prepareTimeoutRunnable = Runnable {
                    if (!isPlaying && ijkPlayer != null) {
                        Logu.e("AudioPlayer", "播放超时，尝试备用源/本地播放")
                        tryNextSource()
                    }
                }
                handler.postDelayed(prepareTimeoutRunnable!!, 10_000L)
            } catch (e: Exception) {
                if (stream.cdns!!.size > 1) {
                    try {
                        player.reset()
                        currentUrl = stream.cdns!![1]
                        player.setDataSource(currentUrl, audioHeaders)
                        player.prepareAsync()
                    } catch (e2: Exception) {
                        MsgUtil.showMsg("播放失败")
                        return
                    }
                } else {
                    MsgUtil.showMsg("播放失败")
                    return
                }
            }
            ijkPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            MsgUtil.showMsg("播放器初始化失败: ${e.message}")
        }
    }

    private fun onPlaybackComplete() {
        when (playMode) {
            0 -> {
                // 列表循环：自动切下一首（单曲则重播）
                if (sidList.size > 1) playNext() else replayCurrent()
            }
            1 -> replayCurrent()
            2 -> {
                // 随机播放
                if (sidList.size > 1) playRandom() else replayCurrent()
            }
        }
    }

    private fun replayCurrent() {
        ijkPlayer?.seekTo(0)
        ijkPlayer?.start()
    }

    private fun playRandom() {
        if (sidList.size <= 1) return
        var next: Int
        do {
            next = (0 until sidList.size).random()
        } while (next == currentIndex && sidList.size > 1)
        loadByIndex(next)
    }

    private fun togglePlayPause() {
        ijkPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                isPlaying = false
                btnPlayPause.setImageResource(R.drawable.btn_player_play)
                stopProgressUpdates()
            } else {
                mp.start()
                isPlaying = true
                btnPlayPause.setImageResource(R.drawable.btn_player_pause)
                startProgressUpdates()
            }
        }
    }

    private fun cyclePlayMode() {
        playMode = (playMode + 1) % 3
        SharedPreferencesUtil.putInt("audio_play_mode", playMode)
        updateModeIcon()
        MsgUtil.showMsg(when (playMode) {
            0 -> "列表循环"
            1 -> "单曲循环"
            2 -> "随机播放"
            else -> ""
        })
    }

    private fun updateModeIcon() {
        btnMode.setImageResource(when (playMode) {
            0 -> R.drawable.ic_audio_volume
            1 -> R.drawable.icon_audio_only_on
            2 -> R.drawable.icon_audio_only_off
            else -> R.drawable.ic_audio_volume
        })
    }

    private fun toggleFav() {
        if (sid <= 0) return
        val target = favState != true   // 当前未知/未收藏 -> 收藏；已收藏 -> 取消
        CenterThreadPool.run {
            try {
                val code = if (target) AudioApi.addFav(sid) else AudioApi.removeFav(sid)
                CenterThreadPool.runOnUiThread {
                    when (code) {
                        0 -> {
                            favState = target
                            updateFavIcon()
                            MsgUtil.showMsg(if (target) "已收藏" else "已取消收藏")
                        }
                        72010002, 4511003 -> MsgUtil.showMsg("请先登录")
                        else -> MsgUtil.showMsg("操作失败（$code）")
                    }
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { MsgUtil.showMsg("操作失败：${e.message}") }
            }
        }
    }

    private fun loadFavState() {
        if (sid <= 0) return
        CenterThreadPool.run {
            try {
                val state = AudioApi.getFavState(sid)
                CenterThreadPool.runOnUiThread {
                    if (state != null) {
                        favState = state
                        updateFavIcon()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateFavIcon() {
        btnFav.setImageResource(if (favState == true) R.drawable.ic_audio_heart_filled else R.drawable.ic_audio_heart)
    }

    private fun showMoreMenu() {
        val popup = PopupMenu(this, btnDownload)
        popup.menu.add("下载音频")
        popup.menu.add(when (playMode) {
            0 -> "播放模式：列表循环"
            1 -> "播放模式：单曲循环"
            2 -> "播放模式：随机播放"
            else -> ""
        })
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "下载音频" -> downloadCurrent()
                else -> cyclePlayMode()
            }
            true
        }
        popup.show()
    }

    private fun downloadCurrent() {
        currentStream?.let { stream ->
            val cdns = stream.cdns
            if (cdns.isNullOrEmpty()) {
                MsgUtil.showMsg("无可下载的音频流")
                return
            }
            if (!FileUtil.checkStoragePermission()) {
                FileUtil.requestStoragePermission(this)
                return
            }
            // app 端音频 CDN 链接必须不带 Referer，走专用方法下载
            val url = currentUrl.ifEmpty { cdns[0] }
            val title = currentAudio?.title?.takeIf { it.isNotEmpty() } ?: songTitle
            val author = currentAudio?.author?.takeIf { it.isNotEmpty() } ?: songAuthor
            val ext = url.substringBefore('?').substringAfterLast('.', "m4a")
            val fileName = FileUtil.stringToFile(if (title.isEmpty()) "audio_$sid" else "$author - $title") + ".$ext"
            val target = File(FileUtil.getDownloadPath(), fileName)

            CenterThreadPool.run {
                try {
                    val response = NetWorkUtil.getNoReferer(url)
                    if (!response.isSuccessful) {
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("下载失败，HTTP ${response.code}（链接可能已过期）") }
                        response.close()
                        return@run
                    }
                    val body = response.body
                    if (body == null) {
                        CenterThreadPool.runOnUiThread { MsgUtil.showMsg("下载失败：响应为空") }
                        response.close()
                        return@run
                    }
                    body.byteStream().use { input ->
                        FileOutputStream(target).use { output ->
                            val buf = ByteArray(256 * 1024)
                            var len: Int
                            while (input.read(buf).also { len = it } != -1) {
                                output.write(buf, 0, len)
                            }
                        }
                    }
                    response.close()
                    CenterThreadPool.runOnUiThread { MsgUtil.showMsg("已下载：$fileName") }
                } catch (e: Exception) {
                    CenterThreadPool.runOnUiThread { MsgUtil.showMsg("下载失败：${e.message}") }
                }
            }
        }
    }

    private fun tryNextSource() {
        currentStream?.cdns?.let { cdns ->
            val idx = cdns.indexOfFirst { it == currentUrl }
            if (idx >= 0 && idx < cdns.size - 1) {
                try {
                    currentUrl = cdns[idx + 1]
                    val audioHeaders = HashMap<String, String>()
                    audioHeaders["User-Agent"] = NetWorkUtil.USER_AGENT_WEB
                    ijkPlayer?.reset()
                    ijkPlayer?.setDataSource(currentUrl, audioHeaders)
                    ijkPlayer?.prepareAsync()
                } catch (_: Exception) {}
            } else {
                // 已无备用源：尝试下载到本地再播（绕开 CDN 的 UA/Referer 限制）
                downloadAndPlayLocal()
            }
        }
    }

    /**
     * 本地文件播放兜底：先用 getNoReferer 把完整音频流下载到缓存目录，
     * 再用 IjkPlayer 播放本地文件，彻底绕开 CDN 反爬（UA/Referer 校验）。
     */
    private fun downloadAndPlayLocal() {
        if (currentUrl.isEmpty()) return
        val target = File(cacheDir, "audio_$sid.m4a")
        MsgUtil.showMsg("流式播放失败，正在下载后播放…")
        CenterThreadPool.run {
            try {
                val response = NetWorkUtil.getNoReferer(currentUrl)
                if (!response.isSuccessful) {
                    CenterThreadPool.runOnUiThread {
                        isPlaying = false
                        btnPlayPause.setImageResource(R.drawable.btn_player_play)
                        stopProgressUpdates()
                        MsgUtil.showMsg("播放失败：网络错误（HTTP ${response.code}）")
                    }
                    response.close()
                    return@run
                }
                val body = response.body
                if (body == null) {
                    CenterThreadPool.runOnUiThread { MsgUtil.showMsg("播放失败：响应为空") }
                    response.close()
                    return@run
                }
                body.byteStream().use { input ->
                    FileOutputStream(target).use { output ->
                        val buf = ByteArray(256 * 1024)
                        var len: Int
                        while (input.read(buf).also { len = it } != -1) {
                            output.write(buf, 0, len)
                        }
                    }
                }
                response.close()
                if (!target.exists() || target.length() == 0L) {
                    CenterThreadPool.runOnUiThread { MsgUtil.showMsg("播放失败：下载内容为空") }
                    return@run
                }
                CenterThreadPool.runOnUiThread {
                    startLocalPlayback(target.absolutePath)
                }
            } catch (e: Exception) {
                CenterThreadPool.runOnUiThread { MsgUtil.showMsg("播放失败：${e.message}") }
            }
        }
    }

    /** 播放本地文件（无 UA/Referer 限制，必然可播） */
    private fun startLocalPlayback(localPath: String) {
        releasePlayer()
        try {
            IjkMediaPlayer.loadLibrariesOnce(null)
            val player = IjkMediaPlayer().apply {
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn", 1)
                setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 48)
                setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)

                setOnPreparedListener {
                    it.start()
                    this@AudioPlayerActivity.isPlaying = true
                    btnPlayPause.setImageResource(R.drawable.btn_player_pause)
                    seekBar.max = 1000
                    startProgressUpdates()
                }

                setOnCompletionListener {
                    onPlaybackComplete()
                }

                setOnErrorListener { _, frameworkErr, implErr ->
                    Logu.e("AudioPlayer", "本地播放错误 framework=$frameworkErr impl=$implErr")
                    this@AudioPlayerActivity.isPlaying = false
                    btnPlayPause.setImageResource(R.drawable.btn_player_play)
                    stopProgressUpdates()
                    MsgUtil.showMsg("播放失败")
                    true
                }
            }
            currentUrl = localPath
            player.setDataSource(localPath)
            player.prepareAsync()
            ijkPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            MsgUtil.showMsg("播放器初始化失败: ${e.message}")
        }
    }

    private fun startProgressUpdates() {
        progressRunnable = object : Runnable {
            override fun run() {
                if (!isSeeking && ijkPlayer != null && ijkPlayer!!.isPlaying) {
                    val current = ijkPlayer!!.currentPosition
                    val duration = ijkPlayer!!.duration
                    if (duration > 0) {
                        val progress = (current * 1000L / duration).toInt()
                        seekBar.progress = progress
                        syncLyricToPosition(current)
                    }
                }
                handler.postDelayed(this, 200)
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun releasePlayer() {
        stopProgressUpdates()
        prepareTimeoutRunnable?.let { handler.removeCallbacks(it) }
        prepareTimeoutRunnable = null
        try {
            ijkPlayer?.stop()
            ijkPlayer?.release()
        } catch (_: Exception) {}
        ijkPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}
