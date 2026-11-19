package com.RobinNotBad.BiliClient.ui.shortvideo

import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
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
        /** 当前倍速（「更多」网格里要显示成「1.0×」）。 */
        fun currentSpeed(): Float
        /** 当前音量百分比（「更多」底注里显示）。 */
        fun currentVolumePercent(): Int
    }

    private val state = ShortVideoOverlayState()
    private val handler = Handler(Looper.getMainLooper())
    private val controls = root.findViewById<View>(R.id.shortNewControls)
    private val topScrim = root.findViewById<View>(R.id.shortNewTopScrim)
    private val bottomScrim = root.findViewById<View>(R.id.shortNewBottomScrim)
    private val backButton = root.findViewById<View>(R.id.shortNewBack)
    private val moreButton = root.findViewById<View>(R.id.shortNewMore)
    private val actionBar = root.findViewById<View>(R.id.shortNewRight)
    private val authorRow = root.findViewById<View>(R.id.shortNewAuthorRow)
    private val playCenter = root.findViewById<View>(R.id.playIcon)
    private val panel = root.findViewById<View>(R.id.shortNewPanel)
    private val moreLayer = root.findViewById<View>(R.id.shortNewMoreLayer)
    private val moreDim = root.findViewById<View>(R.id.shortNewMoreDim)
    private val moreTitle = root.findViewById<TextView>(R.id.shortNewMoreTitle)
    private val moreClose = root.findViewById<View>(R.id.shortNewMoreClose)
    private val moreGrid = root.findViewById<LinearLayout>(R.id.shortNewMoreGrid)
    private val moreFoot = root.findViewById<TextView>(R.id.shortNewMoreFoot)
    private val title = root.findViewById<TextView>(R.id.text_title)
    private val author = root.findViewById<TextView>(R.id.shortNewAuthor)
    private val views = root.findViewById<TextView>(R.id.shortNewViews)
    private val progress = root.findViewById<SeekBar>(R.id.videoprogress)
    private val time = root.findViewById<TextView?>(R.id.text_progress)
    private val favourite = root.findViewById<View>(R.id.shortNewFavourite)
    private val favouriteIcon = root.findViewById<ImageView>(R.id.shortNewFavouriteIcon)
    private val descriptionCell = root.findViewById<View>(R.id.shortNewDescription)
    private val commentsCell = root.findViewById<View>(R.id.shortNewComments)
    private val descriptionCount = root.findViewById<TextView>(R.id.shortNewDescriptionCount)
    private val descriptionIcon = root.findViewById<ImageView>(R.id.shortNewDescriptionIcon)
    private val commentsIcon = root.findViewById<ImageView>(R.id.shortNewCommentsIcon)
    private val commentsCount = root.findViewById<TextView>(R.id.shortNewCommentsCount)
    private val favouriteCount = root.findViewById<TextView>(R.id.shortNewFavouriteCount)
    private val scrim = root.findViewById<View>(R.id.shortNewPanelScrim)
    private val panelTitle = root.findViewById<TextView>(R.id.shortNewPanelTitle)
    private val panelContent = root.findViewById<LinearLayout>(R.id.shortNewPanelContent)
    private val clock = root.findViewById<TextView>(R.id.shortNewClock)
    private val accent: Int
    private val accentLine: Int
    private var item: ShortVideoItem? = null
    private var followed = false
    private var favouriteFolderId = 0L
    private var resumeAfterPanel = false
    private var generation = 0
    private var durationMs = 0L
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** 当前屏的度量：宽度、高度与屏形。每次布局完成都会重算。 */
    private var metrics = ShortVideoLayoutSpec.Metrics(ShortVideoLayoutSpec.REF_WIDTH, ShortVideoLayoutSpec.REF_HEIGHT, false)

    private fun dp(value: Float): Int = (value * root.resources.displayMetrics.density).toInt()

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
        // 设计稿：细线/小字对黑底不够 3:1 时自动退到浅一档，两档都不够才用白色。
        accentLine = ShortVideoTheme.lineColor(accent, ShortVideoTheme.lighten(accent))
        views.setTextColor(ShortVideoTheme.TEXT_SECONDARY)
        progress.progressTintList = android.content.res.ColorStateList.valueOf(accentLine)
        progress.progressBackgroundTintList =
            android.content.res.ColorStateList.valueOf(ShortVideoTheme.trackColor(accent))
        backButton.setOnClickListener { activity.onBackPressed() }
        moreButton.setOnClickListener { showMore() }
        // 「更多」是整屏弹层，设计稿规定「点空白收起」（方屏另有右上角 ⋯）。
        // 早先这两处都没装监听，打开后只能靠退出页面收场，返回键还会直接退页。
        moreDim.setOnClickListener { hideMore() }
        moreClose.setOnClickListener { hideMore() }
        descriptionCell.setOnClickListener { showDescription() }
        commentsCell.setOnClickListener { showComments() }
        root.findViewById<View>(R.id.shortNewAuthorRow).setOnClickListener { showAuthor() }
        root.findViewById<View>(R.id.shortNewAvatar).setOnClickListener { showAuthor() }
        scrim.setOnClickListener { closePanel() }
        favourite.setOnClickListener { toggleFavourite() }
        favourite.setOnLongClickListener { chooseFolder(); true }
        // 面板外的空白也要能关闭
        scrim.setOnClickListener { closePanel() }
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    state.onInteraction(System.currentTimeMillis())
                    time?.text = StringUtil.toTime(value / 1000) + "/" + StringUtil.toTime(durationMs.toInt() / 1000)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                host.seekTo(seekBar.progress.toLong())
                state.onInteraction(System.currentTimeMillis())
            }
        })
        // 屏幕尺寸一变就按规格重摆；首次布局完成时也会触发一次。
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oL, oT, oR, oB ->
            if (right - left != oR - oL || bottom - top != oB - oT) {
                updateMetrics(right - left, bottom - top)
                applyLayout()
            }
        }
        handler.post(tick)
    }

    /**
     * 重算屏形度量。圆屏判定沿用 Activity 的「圆屏适配」开关，
     * 与播放器/短视频其它地方同一口径。
     */
    private fun updateMetrics(widthPx: Int, heightPx: Int) {
        val density = root.resources.displayMetrics.density
        if (widthPx <= 0 || heightPx <= 0 || density <= 0f) return
        metrics = ShortVideoLayoutSpec.Metrics(
            widthDp = widthPx / density,
            heightDp = heightPx / density,
            round = activity.roundAdapted,
        )
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
        // 动作条上的计数：设计稿要求三格都带数字。评论/收藏数来自详情接口，
        // 拿不到时留空（不显示 0，避免看起来像"没有"）。
        descriptionCount.text = ""
        commentsCount.text = ""
        favouriteCount.text = ""
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
            val reply = info.stats.reply
            val fav = info.stats.favorite
            handler.post {
                if (this@ShortVideoNewUi.item?.aid == item.aid) {
                    state.setFavourite(selected)
                    renderFavourite()
                    commentsCount.text = if (reply > 0) StringUtil.toWan(reply.toLong()) else ""
                    favouriteCount.text = if (fav > 0) StringUtil.toWan(fav.toLong()) else ""
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
        // 只在播放状态**真的变了**时才告诉状态机：onProgress 由播放位置变化驱动（约 250ms 一次），
        // 而 onPlayingChanged 会把 lastInteractionAt 刷成当前时刻——无条件调用等于每 250ms
        // 重置一次静置计时器，「静置 3 秒自动进沉浸」永远不可能触发。
        if (playing != state.playing) state.onPlayingChanged(playing, System.currentTimeMillis())
        if (!progress.isPressed) {
            progress.max = duration.coerceAtLeast(0)
            progress.progress = positionMs.coerceIn(0, duration.coerceAtLeast(0))
            time?.text = StringUtil.toTime(positionMs / 1000) + "/" + StringUtil.toTime(duration / 1000)
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

    private fun openPanel(name: String, kind: ShortVideoLayoutSpec.PanelKind) {
        if (!state.panelVisible) {
            resumeAfterPanel = host.isPlaying()
            if (resumeAfterPanel) host.pause()
        }
        state.showPanel(System.currentTimeMillis())
        panelTitle.text = name
        panelContent.removeAllViews()
        scrim.visibility = View.VISIBLE
        // 面板高度按类型算，宽度/圆角由规格决定；圆屏内缩避免四角被表圈切掉。
        applyPanelHeight(kind)
        (panel.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.width = dp(ShortVideoLayoutSpec.panelMaxWidth(metrics))
            it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            panel.layoutParams = it
        }
        if (metrics.round) {
            val inset = ShortVideoLayoutSpec.roundPanelInset(metrics)
            scrim.setPadding(dp(inset), dp(inset), dp(inset), dp(inset))
        } else {
            scrim.setPadding(0, 0, 0, 0)
        }
        renderControls()
    }

    private fun renderControls() {
        controls.visibility = if (state.controlsVisible) View.VISIBLE else View.GONE
        // 设计稿：中心键仅在暂停时显示，且与常驻层同进退（沉浸态也显示播放按钮）。
        playCenter.visibility = if (!state.playing) View.VISIBLE else View.GONE
        topScrim.visibility = controls.visibility
        bottomScrim.visibility = controls.visibility
    }

    /**
     * 按 [ShortVideoLayoutSpec] 把常驻层各元素摆到位。屏幕尺寸一变就重摆。
     *
     * 所有坐标都从规格算出来，**不写死任何 dp**：换分辨率只换 [metrics]。
     */
    private fun applyLayout() {
        val m = metrics
        // 顶部一行：返回贴左、时钟居中、⋯ 贴右，纵向都在中线。
        placeCentered(backButton, ShortVideoLayoutSpec.backCenterX(m), ShortVideoLayoutSpec.topRowCenterY(m),
            ShortVideoLayoutSpec.touchSize(m))
        placeCentered(moreButton, ShortVideoLayoutSpec.moreCenterX(m), ShortVideoLayoutSpec.topRowCenterY(m),
            ShortVideoLayoutSpec.touchSize(m))
        placeCentered(clock, ShortVideoLayoutSpec.clockCenterX(m), ShortVideoLayoutSpec.topRowCenterY(m), -1f)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShortVideoLayoutSpec.clockTextSize(m))

        // 压暗渐变的高度。
        setHeight(topScrim, ShortVideoLayoutSpec.topScrimHeight(m))
        setHeight(bottomScrim, ShortVideoLayoutSpec.bottomScrimHeight(m))

        // 右侧动作条：整条竖直排布，三格中心对齐规格给的三个 y。
        val cellH = ShortVideoLayoutSpec.actionCellHeight(m)
        val actionYs = ShortVideoLayoutSpec.actionCenterYs(m)
        val barTop = actionYs.first() - cellH / 2f
        (actionBar.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.gravity = Gravity.TOP or Gravity.END
            it.topMargin = dp(barTop)
            it.rightMargin = dp(m.widthDp - ShortVideoLayoutSpec.actionCenterX(m) -
                ShortVideoLayoutSpec.actionCellWidth(m) / 2f)
            actionBar.layoutParams = it
        }
        listOf(descriptionCell, commentsCell, favourite).forEachIndexed { index, cell ->
            val h = if (index == 0) cellH else cellH
            cell.layoutParams = (cell.layoutParams as? LinearLayout.LayoutParams)?.apply {
                height = dp(h)
                width = dp(ShortVideoLayoutSpec.actionCellWidth(m))
            } ?: LinearLayout.LayoutParams(dp(ShortVideoLayoutSpec.actionCellWidth(m)), dp(h))
        }
        listOf(descriptionCount, commentsCount, favouriteCount).forEach {
            it.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShortVideoLayoutSpec.actionLabelSize(m))
        }
        // 图标尺寸同样按规格缩放：设计稿在 240 宽上是 20dp 图标，
        // 手机上必须跟着放大，否则相对画面显得过小。
        val actionIcon = dp(ShortVideoLayoutSpec.iconSize(m))
        listOf(descriptionIcon, commentsIcon, favouriteIcon).forEach { icon ->
            icon.layoutParams = (icon.layoutParams as? LinearLayout.LayoutParams)?.apply {
                width = actionIcon; height = actionIcon
            } ?: LinearLayout.LayoutParams(actionIcon, actionIcon)
        }

        // 底部四行各自绝对定位：y 全部由规格给出（距底锚定），不靠容器堆叠。
        val leftInset = ShortVideoLayoutSpec.contentInset(m)
        val rightInset = ShortVideoLayoutSpec.contentRightInset(m)
        placeRow(authorRow, ShortVideoLayoutSpec.authorCenterY(m), leftInset, rightInset)
        placeRow(title, ShortVideoLayoutSpec.titleTop(m), leftInset, rightInset, topAligned = true)
        placeRow(views, ShortVideoLayoutSpec.viewsTop(m), leftInset, rightInset, topAligned = true)
        placeRow(
            progress,
            ShortVideoLayoutSpec.progressCenterY(m) - ShortVideoLayoutSpec.progressTrackHeight(m) / 2f,
            ShortVideoLayoutSpec.progressInset(m),
            ShortVideoLayoutSpec.progressInset(m),
            topAligned = true,
        )
        author.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShortVideoLayoutSpec.authorTextSize(m))
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShortVideoLayoutSpec.titleTextSize(m))
        views.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShortVideoLayoutSpec.viewsTextSize(m))
        (root.findViewById<View>(R.id.shortNewAvatar).layoutParams as? LinearLayout.LayoutParams)?.let {
            val s = dp(ShortVideoLayoutSpec.avatarSize(m))
            it.width = s
            it.height = s
            root.findViewById<View>(R.id.shortNewAvatar).layoutParams = it
        }
        // 进度条：高度按规格。外层 FrameLayout 也跟着一起定高，否则轨道会被容器撑开。
        (progress.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.height = dp(ShortVideoLayoutSpec.progressTrackHeight(m))
            progress.layoutParams = it
        }
        // 新布局没有独立的时长文字（设计稿把时长并进播放量那一行），这里按可空处理。
        time?.setTextSize(TypedValue.COMPLEX_UNIT_SP, ShortVideoLayoutSpec.viewsTextSize(m))

        // 中心播放键。
        placeCentered(playCenter, ShortVideoLayoutSpec.playCenterX(m), ShortVideoLayoutSpec.playCenterY(m),
            ShortVideoLayoutSpec.playSize(m))

        // 面板：贴底、高度按规格、圆角。
        (panel.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.height = dp(ShortVideoLayoutSpec.panelHeight(m, ShortVideoLayoutSpec.PanelKind.DETAIL))
            it.width = dp(ShortVideoLayoutSpec.panelMaxWidth(m))
            it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            panel.layoutParams = it
        }
        pendingPanelKind?.let { applyPanelHeight(it) }
    }

    /** 当前面板类型：openPanel 时记下来，尺寸变化时按同一档重算高度。 */
    private var pendingPanelKind: ShortVideoLayoutSpec.PanelKind? = null

    private fun applyPanelHeight(kind: ShortVideoLayoutSpec.PanelKind) {
        pendingPanelKind = kind
        (panel.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.height = dp(ShortVideoLayoutSpec.panelHeight(metrics, kind))
            panel.layoutParams = it
        }
    }

    /** 把一个视图的中心摆到 (x, y)；[size] 为负表示只摆中心、不改尺寸。 */
    private fun placeCentered(view: View, x: Float, y: Float, size: Float) {
        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.gravity = Gravity.TOP or Gravity.START
        if (size > 0f) {
            lp.width = dp(size)
            lp.height = dp(size)
            lp.leftMargin = dp(x - size / 2f)
            lp.topMargin = dp(y - size / 2f)
        } else {
            // 尺寸未知（wrap_content）：先量一次再居中，避免文字被截。
            view.post {
                val p = view.layoutParams as? FrameLayout.LayoutParams ?: return@post
                p.leftMargin = dp(x) - view.width / 2
                p.topMargin = dp(y) - view.height / 2
                view.layoutParams = p
            }
            return
        }
        view.layoutParams = lp
    }

    /**
     * 把一行内容摆到给定的 y。三件事：横向从 [leftInset] 到屏宽减 [rightInset]，
     * 纵向中心（或顶边）落在 [y]。
     *
     * 之所以每一行单独定位而不是塞进一个 LinearLayout：设计稿标注的是每行的绝对 y
     * （212 / 228 / 246 / 266），用容器堆叠时实际位置由文字高度决定，永远对不上。
     */
    private fun placeRow(
        view: View,
        y: Float,
        leftInset: Float,
        rightInset: Float,
        topAligned: Boolean = false,
    ) {
        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.gravity = Gravity.TOP or Gravity.START
        lp.leftMargin = dp(leftInset)
        lp.rightMargin = dp(rightInset)
        val apply = {
            lp.topMargin = if (topAligned) dp(y) else dp(y) - view.height / 2
            lp.width = dp(metrics.widthDp - leftInset - rightInset)
            view.layoutParams = lp
        }
        if (view.height > 0) apply() else view.post { apply() }
    }

    private fun setHeight(view: View, h: Float) {
        (view.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.height = dp(h)
            view.layoutParams = it
        }
    }

    private fun renderFavourite() {
        favouriteIcon.setImageResource(if (state.favourite) R.drawable.icon_fav_1 else R.drawable.icon_fav_0)
        favouriteIcon.imageTintList = android.content.res.ColorStateList.valueOf(
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
        openPanel(UiText.of("作者"), ShortVideoLayoutSpec.PanelKind.AUTHOR)
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
        openPanel(UiText.of("简介"), ShortVideoLayoutSpec.PanelKind.DETAIL)
        panelContent.addView(text(current.title, 16f))
        panelContent.addView(text(current.desc.ifBlank { UiText.of("这个视频没有简介") }))
    }

    private fun showComments() {
        val current = item ?: return
        openPanel(UiText.of("评论"), ShortVideoLayoutSpec.PanelKind.DETAIL)
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

    /**
     * 「更多」面板——整屏压暗 + 3x3 网格，与播放器 v2 稿的 morePanel 同一个控件形态。
     *
     * 设计稿 moreBody 的注释：「与 v2 稿 morePanel 同尺寸（圆屏格 46x46 / 间距 4，
     * 方屏格 64x58 / 间距 6）；短视频只有 9 项，一页放完，不要页码点」。
     * 所以它不是底部弹层，也不分页。
     */
    private fun showMore() {
        moreLayer.visibility = View.VISIBLE
        // 打开弹层时「只留画面」：常驻层整层不画（设计稿 feedLayer 里的 showUI 判定）。
        controls.visibility = View.GONE
        topScrim.visibility = View.GONE
        bottomScrim.visibility = View.GONE
        moreFoot.text = UiText.of("音量 " + host.currentVolumePercent() + "% · 点空白处收起")
        buildMoreGrid()
    }

    private fun hideMore() {
        moreLayer.visibility = View.GONE
        renderControls()
    }

    /** 网格一格：图标或文字（倍速/清晰度在设计稿里就是文字），加名称与开启态。 */
    private class MoreEntry(
        val icon: Int,
        val glyph: String?,
        val label: CharSequence,
        val on: Boolean,
        val action: () -> Boolean,
    )

    private fun moreEntries(): List<MoreEntry> = listOf(
        MoreEntry(R.drawable.ic_sv_dm, null, UiText.of("弹幕"), activity.isDanmakuVisible) {
            host.toggleDanmaku(); true
        },
        MoreEntry(0, fmtSpeed(host.currentSpeed()), UiText.of("倍速"), host.currentSpeed() != 1f) {
            showSpeed(); true
        },
        MoreEntry(0, currentQualityLabel(), UiText.of("清晰度"), false) {
            showQuality(); true
        },
        MoreEntry(R.drawable.ic_sv_vm, null, UiText.of("音量 -"), false) {
            host.changeVolume(false); false
        },
        MoreEntry(R.drawable.ic_sv_vp, null, UiText.of("音量 +"), false) {
            host.changeVolume(true); false
        },
        MoreEntry(R.drawable.ic_sv_later, null, UiText.of("稍后再看"), false) {
            addWatchLater(); false
        },
        MoreEntry(R.drawable.ic_sv_dl, null, UiText.of("缓存"), false) {
            download(); false
        },
        MoreEntry(R.drawable.ic_sv_ext, null, UiText.of("完整详情"), false) {
            openFullDetail(); false
        },
        MoreEntry(R.drawable.ic_sv_hand, null, UiText.of("手势说明"), false) {
            showGestureHelp(); true
        },
    )

    private fun fmtSpeed(value: Float): String =
        if (value % 1f == 0f) String.format(Locale.US, "%.1fx", value)
        else String.format(Locale.US, "%.2fx", value)

    private fun currentQualityLabel(): String {
        val current = item ?: return "--"
        val names = current.qnStrList ?: return "--"
        val values = current.qnValueList ?: return "--"
        val index = values.indexOf(current.currentQuality)
        if (index !in names.indices) return "--"
        return names[index].substringBefore(' ').trim().ifEmpty { "--" }
    }

    /** 画 3x3 网格。格子尺寸按规格缩放：方屏 64x58 / 间距 6，圆屏 46x46 / 间距 4。 */
    private fun buildMoreGrid() {
        val m = metrics
        val round = metrics.round
        val cellW = if (round) m.s(46f) else m.s(64f)
        val cellH = if (round) m.s(46f) else m.s(58f)
        val gap = if (round) m.s(4f) else m.s(6f)
        val gridW = cellW * 3 + gap * 2
        val gridH = cellH * 3 + gap * 2
        moreGrid.removeAllViews()

        moreEntries().chunked(3).forEachIndexed { rowIndex, row ->
            val line = LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            row.forEachIndexed { col, entry ->
                line.addView(
                    buildMoreCell(entry, round),
                    LinearLayout.LayoutParams(dp(cellW), dp(cellH)).apply {
                        if (col > 0) marginStart = dp(gap)
                    },
                )
            }
            repeat(3 - row.size) {
                line.addView(View(root.context), LinearLayout.LayoutParams(dp(cellW), dp(cellH)).apply {
                    marginStart = dp(gap)
                })
            }
            moreGrid.addView(line, LinearLayout.LayoutParams(-2, dp(cellH)).apply {
                if (rowIndex > 0) topMargin = dp(gap)
            })
        }

        // 标题：圆屏居中 y=20；方屏左对齐 18,16（与设计稿 moreBody 一致）。
        val titleTop = if (round) m.s(20f) else m.s(16f)
        (moreTitle.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.gravity = if (round) Gravity.TOP or Gravity.CENTER_HORIZONTAL
            else Gravity.TOP or Gravity.START
            it.topMargin = dp(titleTop)
            it.leftMargin = if (round) 0 else dp(m.s(18f))
            moreTitle.layoutParams = it
        }
        moreTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, m.scale * (if (round) 12f else 13f))

        // 收起按钮只在方屏显示（圆屏点空白收起）。
        moreClose.visibility = if (round) View.GONE else View.VISIBLE
        (moreClose.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.gravity = Gravity.TOP or Gravity.END
            it.rightMargin = dp(m.s(8f))
            it.topMargin = dp(m.s(7f))
            moreClose.layoutParams = it
        }

        (moreGrid.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.gravity = Gravity.CENTER
            it.width = dp(gridW)
            it.height = dp(gridH)
            moreGrid.layoutParams = it
        }
        val footTop = if (round) m.s(196f) else m.s(244f)
        (moreFoot.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            it.topMargin = dp(footTop)
            it.width = dp(metrics.widthDp)
            moreFoot.layoutParams = it
        }
        moreFoot.setTextSize(TypedValue.COMPLEX_UNIT_SP, m.scale * 9f)
    }

    /** 一格：图标/文字在上、名称在下；开启态染强调色（设计稿：只有「已开启」上色）。 */
    private fun buildMoreCell(entry: MoreEntry, round: Boolean): View {
        val m = metrics
        val tint = if (entry.on) accentLine else ShortVideoTheme.WHITE
        val cell = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(m.s(if (round) 8f else 10f)).toFloat()
                setColor(0x12FFFFFF)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val keepOpen = entry.action()
                if (keepOpen) buildMoreGrid() else hideMore()
            }
        }
        val glyph = m.s(if (round) 18f else 20f)
        if (entry.glyph != null) {
            cell.addView(TextView(root.context).apply {
                text = entry.glyph
                setTextColor(tint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, m.scale * (if (round) 10f else 12f))
                gravity = Gravity.CENTER
                maxLines = 1
            }, LinearLayout.LayoutParams(-2, dp(glyph)))
        } else {
            cell.addView(ImageView(root.context).apply {
                setImageResource(entry.icon)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setColorFilter(tint)
            }, LinearLayout.LayoutParams(dp(glyph), dp(glyph)))
        }
        cell.addView(TextView(root.context).apply {
            text = entry.label
            setTextColor(tint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, m.scale * (if (round) 8f else 9f))
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-2, dp(m.s(if (round) 13f else 15f))).apply {
            topMargin = dp(m.s(if (round) 2f else 4f))
        })
        return cell
    }

    private fun addWatchLater() {
        val current = item ?: return
        load {
            val code = WatchLaterApi.add(current.aid)
            handler.post { MsgUtil.showMsg(UiText.of(if (code == 0) "已加入稍后再看" else "操作失败（$code）")) }
        }
    }

    private fun openFullDetail() {
        val current = item ?: return
        activity.startActivity(Intent(activity, VideoInfoActivity::class.java)
            .putExtra("aid", current.aid).putExtra("bvid", current.bvid))
    }

    /**
     * 手势说明：写进**面板层**，所以必须先把「更多」弹层收掉。
     *
     * 「更多」是独立的一层（整屏压暗 + 网格），面板层在它下面且此刻是 GONE。
     * 之前只往 panelContent 里塞文字而没人把面板置为可见，点了这一格什么也不会发生。
     */
    private fun showGestureHelp() {
        hideMore()
        openPanel("手势说明", ShortVideoLayoutSpec.PanelKind.DETAIL)
        panelContent.removeAllViews()
        panelContent.addView(text(UiText.of(
            "单击：进出沉浸（显隐全部常驻层）\n" +
                "双击：暂停 / 继续\n" +
                "按住：3 倍速，松手还原\n" +
                "上下滑 / 表冠：换条\n" +
                "双指：缩放与拖动\n" +
                "静置 3 秒：播放时自动进沉浸"
        ), 12f))
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
