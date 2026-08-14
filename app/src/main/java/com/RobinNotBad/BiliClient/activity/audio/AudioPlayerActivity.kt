package com.RobinNotBad.BiliClient.activity.audio

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity
import com.RobinNotBad.BiliClient.api.AudioApi
import com.RobinNotBad.BiliClient.model.AudioInfo
import com.RobinNotBad.BiliClient.model.AudioStream
import com.RobinNotBad.BiliClient.model.Lyric
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

class AudioPlayerActivity : InstanceActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentStream: AudioStream? = null
    private var currentAudio: AudioInfo? = null
    private var currentLyric: Lyric? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    private var playMode = 0
    private var isPlaying = false
    private var sid: Long = 0
    private var songTitle: String = ""
    private var songAuthor: String = ""
    private var songCover: String = ""

    private lateinit var titleView: TextView
    private lateinit var authorView: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnMode: ImageButton
    private lateinit var btnDownload: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var coverPlaceholder: TextView

    private var isSeeking = false
    private var isSeekFromUser = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sid = intent.getLongExtra("sid", 0)
        songTitle = intent.getStringExtra("title") ?: ""
        songAuthor = intent.getStringExtra("author") ?: ""
        songCover = intent.getStringExtra("cover") ?: ""

        asyncInflate(R.layout.activity_audio_player) { _, _ ->
            setMenuClick()

            titleView = findViewById(R.id.song_title)
            authorView = findViewById(R.id.song_author)
            btnPlayPause = findViewById(R.id.btn_play_pause)
            btnPrev = findViewById(R.id.btn_prev)
            btnNext = findViewById(R.id.btn_next)
            btnMode = findViewById(R.id.btn_mode)
            btnDownload = findViewById(R.id.btn_download)
            seekBar = findViewById(R.id.seek_bar)
            tvCurrentTime = findViewById(R.id.tv_current_time)
            tvTotalTime = findViewById(R.id.tv_total_time)
            coverPlaceholder = findViewById(R.id.cover_placeholder)

            titleView.text = songTitle
            authorView.text = songAuthor
            titleView.isSelected = true

            playMode = SharedPreferencesUtil.getInt("audio_play_mode", 0)
            updateModeIcon()

            btnPlayPause.setOnClickListener { togglePlayPause() }
            btnMode.setOnClickListener { cyclePlayMode() }
            btnDownload.setOnClickListener { downloadCurrent() }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        isSeekFromUser = true
                        updateTimeText(progress * 1000L / 1000)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    isSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    isSeeking = false
                    val progress = seekBar.progress.toLong()
                    val duration = mediaPlayer?.duration?.toLong() ?: return
                    val target = progress * duration / 1000L
                    mediaPlayer?.seekTo(target.toInt())
                    isSeekFromUser = false
                }
            })

            loadAudio(sid)
        }
    }

    private fun loadAudio(sid: Long) {
        CenterThreadPool.run {
            try {
                val audio = AudioApi.getAudioInfo(sid)
                currentAudio = audio
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
                    }
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

    private fun startPlayback(stream: AudioStream) {
        releasePlayer()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build())

            try {
                setDataSource(stream.cdns!![0])
                prepareAsync()
            } catch (e: Exception) {
                if (stream.cdns!!.size > 1) {
                    try {
                        reset()
                        setDataSource(stream.cdns!![1])
                        prepareAsync()
                    } catch (e2: Exception) {
                        MsgUtil.showMsg("播放失败")
                        return
                    }
                } else {
                    MsgUtil.showMsg("播放失败")
                    return
                }
            }

            setOnPreparedListener { mp ->
                mp.start()
                this@AudioPlayerActivity.isPlaying = true
                btnPlayPause.setImageResource(R.drawable.btn_player_pause)
                tvTotalTime.text = formatTime(mp.duration.toLong())
                seekBar.max = 1000
                startProgressUpdates()
            }

            setOnCompletionListener {
                onPlaybackComplete()
            }

            setOnErrorListener { _, _, _ ->
                tryNextSource()
                true
            }
        }
    }

    private fun onPlaybackComplete() {
        when (playMode) {
            0 -> {
                isPlaying = false
                btnPlayPause.setImageResource(R.drawable.btn_player_play)
                stopProgressUpdates()
            }
            1 -> {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
            }
            2 -> {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
            }
        }
    }

    private fun togglePlayPause() {
        mediaPlayer?.let { mp ->
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
            0 -> R.drawable.icon_play_12
            1 -> R.drawable.icon_audio_only_on
            2 -> R.drawable.icon_audio_only_off
            else -> R.drawable.icon_play_12
        })
    }

    private fun downloadCurrent() {
        currentStream?.let { stream ->
            if (stream.cdns.isNullOrEmpty()) {
                MsgUtil.showMsg("无可下载的音频流")
                return
            }
            MsgUtil.showMsg("下载功能开发中")
        }
    }

    private fun tryNextSource() {
        currentStream?.cdns?.let { cdns ->
            val currentUrl = ""
            val idx = cdns.indexOfFirst { it == currentUrl }
            if (idx >= 0 && idx < cdns.size - 1) {
                try {
                    mediaPlayer?.reset()
                    mediaPlayer?.setDataSource(cdns[idx + 1])
                    mediaPlayer?.prepareAsync()
                } catch (_: Exception) {}
            }
        }
    }

    private fun startProgressUpdates() {
        progressRunnable = object : Runnable {
            override fun run() {
                if (!isSeeking && mediaPlayer != null && mediaPlayer!!.isPlaying) {
                    val current = mediaPlayer!!.currentPosition.toLong()
                    val duration = mediaPlayer!!.duration.toLong()
                    if (duration > 0) {
                        val progress = (current * 1000L / duration).toInt()
                        seekBar.progress = progress
                        if (!isSeekFromUser) {
                            updateTimeText(current)
                        }
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

    private fun updateTimeText(currentMs: Long) {
        tvCurrentTime.text = formatTime(currentMs)
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%d:%02d", min, sec)
    }

    private fun releasePlayer() {
        stopProgressUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}