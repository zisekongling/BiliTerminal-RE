package com.RobinNotBad.BiliClient.ui.shortvideo

import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.activity.video.JumpToPlayerActivity
import com.RobinNotBad.BiliClient.activity.video.ShortVideoPlayerActivity
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity
import com.RobinNotBad.BiliClient.api.FavoriteApi
import com.RobinNotBad.BiliClient.api.ReplyApi
import com.RobinNotBad.BiliClient.api.UserInfoApi
import com.RobinNotBad.BiliClient.api.VideoInfoApi
import com.RobinNotBad.BiliClient.api.WatchLaterApi
import com.RobinNotBad.BiliClient.model.ContentType
import com.RobinNotBad.BiliClient.model.FavoriteFolder
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.model.Reply
import com.RobinNotBad.BiliClient.model.ShortVideoItem
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.UiText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 新版短视频的覆盖层与轻量面板。业务操作全部交给原 PageHolder 或已有 API。 */
class ShortVideoNewUi(
    private val root: View,
    private val activity: ShortVideoPlayerActivity,
    private val host: Host
) {
    interface Host {
        fun isPlaying(): Boolean
        fun pause()
        fun resume()
        fun seekTo(positionMs: Long)
        fun cycleSpeed()
        fun setSpeed(speed: Float)
        fun toggleDanmaku(): Boolean
        fun changeVolume(add: Boolean)
        fun changeQuality(quality: Int)
    }

    private val state = ShortVideoOverlayState()
    private val handler = Handler(Looper.getMainLooper())
    private val controls = root.findViewById<View>(R.id.shortNewControls)
    private val title = root.findViewById<TextView>(R.id.text_title)
    private val author = root.findViewById<TextView>(R.id.shortNewAuthor)
    private val views = root.findViewById<TextView>(R.id.shortNewViews)
    private val progress = root.findViewById<SeekBar>(R.id.videoprogress)
    private val time = root.findViewById<TextView>(R.id.text_progress)
    private val favourite = root.findViewById<ImageButton>(R.id.shortNewFavourite)
    private val scrim = root.findViewById<View>(R.id.shortNewPanelScrim)
    private val panelTitle = root.findViewById<TextView>(R.id.shortNewPanelTitle)
    private val panelContent = root.findViewById<LinearLayout>(R.id.shortNewPanelContent)
    private val clock = root.findViewById<TextView>(R.id.shortNewClock)
    private val descriptionIcon = root.findViewById<android.widget.ImageView>(R.id.shortNewDescription)
    private val commentsIcon = root.findViewById<android.widget.ImageView>(R.id.shortNewComments)
    private val accent: Int
    private var item: ShortVideoItem? = null
    private var followed = false
    private var favouriteFolderId = 0L
    private var resumeAfterPanel = false
    private var generation = 0
    private var durationMs = 0L
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val tick = object : Runnable {
        override fun run() {
            clock.text = clockFormat.format(Date())
            state.hideForIdle(System.currentTimeMillis())
            renderControls()
            handler.postDelayed(this, 500L)
        }
    }

    init {
        val typed = root.context.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent))
        accent = typed.getColor(0, ShortVideoTheme.WHITE)
        typed.recycle()
        val secondary = TypedValue()
        root.context.theme.resolveAttribute(android.R.attr.textColorSecondary, secondary, true)
        val textSecondary = if (secondary.data != 0) secondary.data else 0xB3FFFFFF.toInt()
        views.setTextColor(textSecondary)
        listOf(descriptionIcon, commentsIcon).forEach { icon ->
            val tint = if (ShortVideoTheme.prefersDarkIcons(textSecondary)) 0xFF17181C.toInt() else ShortVideoTheme.WHITE
            icon.imageTintList = android.content.res.ColorStateList.valueOf(tint)
        }
        progress.progressTintList = android.content.res.ColorStateList.valueOf(accent)
        progress.progressBackgroundTintList =
            android.content.res.ColorStateList.valueOf(ShortVideoTheme.trackColor(accent))
        root.findViewById<View>(R.id.shortNewBack).setOnClickListener { activity.onBackPressed() }
        root.findViewById<View>(R.id.shortNewMore).setOnClickListener { showMore() }
        root.findViewById<View>(R.id.shortNewDescription).setOnClickListener { showDescription() }
        root.findViewById<View>(R.id.shortNewComments).setOnClickListener { showComments() }
        root.findViewById<View>(R.id.shortNewAuthor).setOnClickListener { showAuthor() }
        root.findViewById<View>(R.id.shortNewAvatar).setOnClickListener { showAuthor() }
        root.findViewById<View>(R.id.shortNewPanelClose).setOnClickListener { closePanel() }
        scrim.setOnClickListener { closePanel() }
        favourite.setOnClickListener { toggleFavourite() }
        favourite.setOnLongClickListener { chooseFolder(); true }
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    state.onInteraction(System.currentTimeMillis())
                    time.text = StringUtil.toTime(value / 1000) + "/" + StringUtil.toTime(durationMs.toInt() / 1000)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                host.seekTo(seekBar.progress.toLong())
                state.onInteraction(System.currentTimeMillis())
            }
        })
        handler.post(tick)
    }

    fun bind(item: ShortVideoItem) {
        generation++
        this.item = item
        followed = false
        favouriteFolderId = 0L
        state.bind(host.isPlaying(), false)
        title.text = item.title
        author.text = item.authorName
        views.text = UiText.of("${item.viewCount} 播放 · ${item.danmakuCount} 弹幕")
        durationMs = item.duration * 1000L
        if (item.authorFace.isNotEmpty() && GlideUtil.canLoad(root)) {
            GlideUtil.requestRound(root.findViewById(R.id.shortNewAvatar), item.authorFace, R.mipmap.placeholder)
        }
        renderFavourite()
        closePanel(resume = false)
        renderControls()
        if (item.aid > 0) load(showError = false) {
            val info = VideoInfoApi.getVideoInfo(item.aid)
            val selected = info.stats.favoured
            handler.post {
                if (this@ShortVideoNewUi.item?.aid == item.aid) {
                    state.setFavourite(selected)
                    renderFavourite()
                }
            }
        }
    }

    fun toggleControls() {
        state.toggleControls(System.currentTimeMillis())
        renderControls()
    }

    fun onProgress(positionMs: Int, duration: Int, playing: Boolean) {
        durationMs = duration.toLong().coerceAtLeast(0L)
        state.onPlayingChanged(playing, System.currentTimeMillis())
        if (!progress.isPressed) {
            progress.max = duration.coerceAtLeast(0)
            progress.progress = positionMs.coerceIn(0, duration.coerceAtLeast(0))
            time.text = StringUtil.toTime(positionMs / 1000) + "/" + StringUtil.toTime(duration / 1000)
        }
    }

    fun closePanel(): Boolean = closePanel(resume = true)

    private fun closePanel(resume: Boolean): Boolean {
        if (!state.closePanel()) return false
        scrim.visibility = View.GONE
        val shouldResume = resumeAfterPanel
        resumeAfterPanel = false
        if (resume && shouldResume && activity.currentShellPage() == 0) host.resume()
        state.onPlayingChanged(host.isPlaying(), System.currentTimeMillis())
        renderControls()
        return true
    }

    private fun openPanel(name: String) {
        if (!state.panelVisible) {
            resumeAfterPanel = host.isPlaying()
            if (resumeAfterPanel) host.pause()
        }
        state.showPanel(System.currentTimeMillis())
        panelTitle.text = name
        panelContent.removeAllViews()
        scrim.visibility = View.VISIBLE
        renderControls()
    }

    private fun renderControls() {
        controls.visibility = if (state.controlsVisible) View.VISIBLE else View.GONE
    }

    private fun renderFavourite() {
        favourite.setImageResource(if (state.favourite) R.drawable.icon_fav_1 else R.drawable.icon_fav_0)
        favourite.imageTintList = android.content.res.ColorStateList.valueOf(
            ShortVideoTheme.favouriteTint(accent, state.favourite)
        )
    }

    private fun text(content: String, size: Float = 13f, color: Int = Color.WHITE): TextView =
        TextView(root.context).apply {
            text = content
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(0f, 1.15f)
        }

    private fun button(label: String, action: () -> Unit): TextView =
        text(label, 14f, accent).apply {
            val p = (10 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
            setOnClickListener { action() }
            foreground = TypedValue().let {
                root.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
                if (it.resourceId != 0) root.context.getDrawable(it.resourceId) else null
            }
        }

    private fun loading(): TextView = text(UiText.of("加载中…"), 13f, 0xB3FFFFFF.toInt()).also {
        panelContent.addView(it)
    }

    private inline fun load(showError: Boolean = true, crossinline block: () -> Unit) {
        val token = generation
        CenterThreadPool.run {
            try {
                block()
            } catch (e: Exception) {
                handler.post {
                    if (showError && token == generation && state.panelVisible) {
                        panelContent.removeAllViews()
                        panelContent.addView(text(UiText.of("加载失败，请稍后重试")))
                    }
                }
                MsgUtil.err(UiText.of("短视频面板："), e)
            }
        }
    }

    private fun showAuthor() {
        val current = item ?: return
        openPanel(UiText.of("作者"))
        panelContent.addView(text(current.authorName.toString(), 18f))
        val followButton: TextView = button(UiText.of("")) {}
        followButton.setOnClickListener { followUser(followButton) }
        refreshFollowButton(followButton)
        panelContent.addView(followButton)
        panelContent.addView(button(UiText.of("只看 TA")) { openAuthor() })
        panelContent.addView(button(UiText.of("作者空间")) { openAuthor() })
        loading()
        if (current.authorMid > 0) load {
            val info = UserInfoApi.getUserInfo(current.authorMid)
            handler.post {
                if (item?.aid == current.aid && state.panelVisible) {
                    followed = info.followed
                    refreshFollowButton(followButton)
                    panelContent.removeViewAt(panelContent.childCount - 1)
                    panelContent.addView(text(UiText.of("${info.fans} 粉丝"), 12f, 0xB3FFFFFF.toInt()))
                }
            }
        }
    }

    private fun refreshFollowButton(button: TextView) {
        button.text = UiText.of(if (followed) "已关注" else "关注")
    }

    private fun followUser(button: TextView) {
        val current = item ?: return
        button.isEnabled = false
        load {
            val code = UserInfoApi.followUser(current.authorMid, !followed)
            handler.post {
                button.isEnabled = true
                if (code == 0) {
                    followed = !followed
                    button.text = if (followed) UiText.of("已关注") else UiText.of("关注")
                } else MsgUtil.showMsg(UiText.of("操作失败（$code）"))
            }
        }
    }

    private fun openAuthor() {
        val current = item ?: return
        activity.startActivity(Intent(activity, UserInfoActivity::class.java).putExtra("mid", current.authorMid))
    }

    private fun showDescription() {
        val current = item ?: return
        openPanel(UiText.of("简介"))
        panelContent.addView(text(current.title, 16f))
        panelContent.addView(text(current.desc.ifBlank { UiText.of("这个视频没有简介") }))
    }

    private fun showComments() {
        val current = item ?: return
        openPanel(UiText.of("评论"))
        panelContent.addView(button(UiText.of("打开完整评论")) {
            activity.startActivity(Intent(activity, VideoInfoActivity::class.java)
                .putExtra("aid", current.aid).putExtra("bvid", current.bvid).putExtra("seekReply", 1L))
        })
        loading()
        load {
            val replies = ArrayList<Reply>()
            ReplyApi.getReplies(current.aid, 0, 1, ContentType.Video, 1, replies)
            handler.post {
                if (item?.aid == current.aid && state.panelVisible) {
                    panelContent.removeViewAt(panelContent.childCount - 1)
                    if (replies.isEmpty()) panelContent.addView(text(UiText.of("还没有评论")))
                    replies.take(10).forEach { reply ->
                        panelContent.addView(text(UiText.of("${reply.sender.name} · ${StringUtil.toWan(reply.likeCount.toLong())} 赞"), 12f, accent))
                        panelContent.addView(text(reply.message.toString()))
                    }
                }
            }
        }
    }

    private fun toggleFavourite() {
        val current = item ?: return
        favourite.isEnabled = false
        load {
            var folderId = favouriteFolderId
            if (folderId == 0L) {
                val folders = FavoriteApi.getFavoriteFolders(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
                folderId = folders.firstOrNull { it.isDefault }?.mediaId ?: folders.firstOrNull()?.mediaId ?: 0L
                favouriteFolderId = folderId
            }
            val code = if (state.favourite) FavoriteApi.deleteFavorite(current.aid, folderId)
            else FavoriteApi.addFavorite(current.aid, folderId)
            handler.post {
                favourite.isEnabled = true
                if (code == 0) {
                    state.setFavourite(!state.favourite)
                    renderFavourite()
                    MsgUtil.showMsg(UiText.of(if (state.favourite) "已收藏" else "已取消收藏"))
                } else MsgUtil.showMsg(UiText.of("操作失败（$code）"))
            }
        }
    }

    private fun chooseFolder() {
        val current = item ?: return
        loadingFolderDialog(current)
    }

    private fun loadingFolderDialog(current: ShortVideoItem) {
        load {
            val folders = FavoriteApi.getFavoriteFolders(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
            handler.post {
                if (item?.aid != current.aid) return@post
                TerminalDialog.singleChoice(root.context, UiText.of("选择收藏夹"), folders.map { it.name }, 0) { sheet, index ->
                    sheet.dialog.dismiss()
                    addToFolder(current, folders[index])
                }.show()
            }
        }
    }

    private fun addToFolder(current: ShortVideoItem, folder: FavoriteFolder) {
        load {
            val code = FavoriteApi.addFavorite(current.aid, folder.mediaId)
            handler.post {
                if (code == 0) {
                    favouriteFolderId = folder.mediaId
                    state.setFavourite(true)
                    renderFavourite()
                    MsgUtil.showMsg(UiText.of("已收藏到${folder.name}"))
                } else MsgUtil.showMsg(UiText.of("操作失败（$code）"))
            }
        }
    }

    private fun showMore() {
        openPanel(UiText.of("更多"))
        val danmakuButton: TextView = button(UiText.of("")) {}
        danmakuButton.setOnClickListener {
            val on = host.toggleDanmaku()
            danmakuButton.text = UiText.of("弹幕：${if (on) "开" else "关"}")
        }
        danmakuButton.text = UiText.of("弹幕：${if (activity.isDanmakuVisible) "开" else "关"}")
        panelContent.addView(danmakuButton)
        panelContent.addView(button(UiText.of("播放速度")) { showSpeed() })
        panelContent.addView(button(UiText.of("清晰度")) { showQuality() })
        panelContent.addView(button(UiText.of("音量 +")) { host.changeVolume(true) })
        panelContent.addView(button(UiText.of("音量 −")) { host.changeVolume(false) })
        panelContent.addView(button(UiText.of("稍后再看")) {
            val current = item ?: return@button
            load {
                val code = WatchLaterApi.add(current.aid)
                handler.post { MsgUtil.showMsg(UiText.of(if (code == 0) "已加入稍后再看" else "操作失败（$code）")) }
            }
        })
        panelContent.addView(button(UiText.of("缓存")) { download() })
        panelContent.addView(button(UiText.of("完整视频详情")) {
            val current = item ?: return@button
            activity.startActivity(Intent(activity, VideoInfoActivity::class.java)
                .putExtra("aid", current.aid).putExtra("bvid", current.bvid))
        })
        panelContent.addView(button(UiText.of("手势说明")) {
            panelContent.removeAllViews()
            panelContent.addView(text(UiText.of("单击：显示 / 隐藏界面\n双击：暂停 / 继续\n长按：3 倍速\n双指：缩放与拖动\n静置 3 秒：播放时自动隐藏界面\n暂停和面板打开时不自动隐藏")))
        })
    }

    private fun showSpeed() {
        val values = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        TerminalDialog.singleChoice(root.context, UiText.of("播放速度"), values.map { "${it}x" }, 2) { sheet, index ->
            host.setSpeed(values[index])
            sheet.dialog.dismiss()
        }.show()
    }

    private fun showQuality() {
        val current = item ?: return
        val names = current.qnStrList
        val values = current.qnValueList
        if (names == null || values == null || names.isEmpty() || values.isEmpty()) {
            MsgUtil.showMsg(UiText.of("清晰度信息尚未加载"))
            return
        }
        TerminalDialog.singleChoice(root.context, UiText.of("清晰度"), names.toList(),
            values.indexOf(current.currentQuality).coerceAtLeast(0)) { sheet, index ->
            sheet.dialog.dismiss()
            if (index in values.indices) host.changeQuality(values[index])
        }.show()
    }

    private fun download() {
        val current = item ?: return
        val data = PlayerData()
        data.title = current.title
        data.aid = current.aid
        data.bvid = current.bvid
        data.cid = current.cid
        data.videoUrl = current.videoUrl
        data.danmakuUrl = current.danmakuUrl
        data.qn = current.currentQuality
        data.qnStrList = current.qnStrList
        data.qnValueList = current.qnValueList
        data.uploaderMid = current.authorMid
        activity.startActivity(Intent(activity, JumpToPlayerActivity::class.java)
            .putExtra("data", data).putExtra("download", 1))
    }

    fun syncPlayback() {
        state.onPlayingChanged(host.isPlaying(), System.currentTimeMillis())
        renderControls()
    }

    fun release() {
        generation++
        handler.removeCallbacksAndMessages(null)
    }
}
