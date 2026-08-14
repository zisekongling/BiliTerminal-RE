package com.RobinNotBad.BiliClient.adapter.video

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalPageChooseActivity
import com.RobinNotBad.BiliClient.api.PlayerApi
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.model.LocalVideo
import com.RobinNotBad.BiliClient.model.PlayerData
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import java.util.Locale

class LocalVideoAdapter(
    val context: Context,
    val localVideoList: ArrayList<LocalVideo>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var longClickListener: OnItemLongClickListener? = null

    // 视频操作回调
    var onUpdateDanmaku: ((Int) -> Unit)? = null
    var onSwitchQuality: ((Int) -> Unit)? = null
    var onDeleteVideo: ((Int) -> Unit)? = null
    var onMoveToFolder: ((Int) -> Unit)? = null
    var onRemoveFromFolder: ((Int) -> Unit)? = null

    // 多选模式
    var isMultiSelectMode: Boolean = false
    val selectedPositions = mutableSetOf<Int>()

    // 当前打开的设置面板位置（实际在videoList中的索引）
    private var openSettingsPosition: Int = -1

    // 是否在文件夹视图内（影响"移动到文件夹"按钮显示）
    var isInFolderView: Boolean = false

    fun setOnLongClickListener(listener: OnItemLongClickListener) {
        this.longClickListener = listener
    }

    fun toggleMultiSelectMode() {
        isMultiSelectMode = !isMultiSelectMode
        selectedPositions.clear()
        openSettingsPosition = -1
        notifyDataSetChanged()
    }

    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedPositions.clear()
        openSettingsPosition = -1
        notifyDataSetChanged()
    }

    fun getSelectedVideos(): List<LocalVideo> {
        return selectedPositions.mapNotNull { pos ->
            if (pos >= 0 && pos < localVideoList.size) localVideoList[pos] else null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0 -> {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_video_local, parent, false)
                LocalVideoHolder(view)
            }
            1 -> {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_goto, parent, false)
                GotoHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(context).inflate(R.layout.cell_video_local, parent, false)
                LocalVideoHolder(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position == 0) {
            (holder as GotoHolder).show(context)
            return
        }

        val realPosition = position - 1
        if (realPosition < 0 || realPosition >= localVideoList.size) return

        val localVideo = localVideoList[realPosition]
        val videoHolder = holder as LocalVideoHolder

        videoHolder.showLocalVideo(localVideo, context)

        // 多选模式UI
        if (isMultiSelectMode) {
            videoHolder.checkBox.visibility = View.VISIBLE
            videoHolder.checkBox.isChecked = selectedPositions.contains(realPosition)
            videoHolder.settingsPanel.visibility = View.GONE
        } else {
            videoHolder.checkBox.visibility = View.GONE
        }

        // 设置面板可见性
        val isSettingsOpen = realPosition == openSettingsPosition && !isMultiSelectMode
        videoHolder.settingsPanel.visibility = if (isSettingsOpen) View.VISIBLE else View.GONE

        // 根据是否在文件夹视图中调整移动按钮文本
        if (isInFolderView) {
            videoHolder.btnMoveVideo.text = "移出文件夹"
        } else {
            videoHolder.btnMoveVideo.text = "添加到文件夹"
        }

        // 滑动处理
        var startX = 0f
        var isSwiping = false
        val swipeThreshold = 120f

        videoHolder.itemView.setOnTouchListener { view, event ->
            if (isMultiSelectMode) {
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        if (!isSwiping) {
                            toggleSelection(realPosition)
                        }
                        view.performClick()
                    }
                }
                return@setOnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    isSwiping = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = startX - event.x
                    if (deltaX > 40f && !isSwiping) {
                        isSwiping = true
                    }
                    if (isSwiping) {
                        val newOpenPos = if (deltaX > swipeThreshold) realPosition else -1
                        if (newOpenPos != openSettingsPosition) {
                            val oldOpen = openSettingsPosition
                            openSettingsPosition = newOpenPos
                            notifyItemChanged(position)
                            if (oldOpen >= 0) {
                                notifyItemChanged(oldOpen + 1)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isSwiping) {
                        // 点击
                        if (isSettingsOpen) {
                            openSettingsPosition = -1
                            notifyItemChanged(position)
                        } else {
                            playVideo(localVideo)
                        }
                    } else {
                        if (isSettingsOpen && startX - event.x < swipeThreshold / 2) {
                            openSettingsPosition = -1
                            notifyItemChanged(position)
                        }
                    }
                    view.performClick()
                    true
                }
                else -> false
            }
        }

        // 长按
        videoHolder.itemView.setOnLongClickListener {
            if (longClickListener != null) {
                longClickListener!!.onItemLongClick(realPosition)
                true
            } else false
        }

        // 设置面板按钮事件
        videoHolder.btnUpdateDanmaku.setOnClickListener {
            openSettingsPosition = -1
            notifyItemChanged(position)
            onUpdateDanmaku?.invoke(realPosition)
        }

        videoHolder.layoutQuality.setOnClickListener {
            openSettingsPosition = -1
            notifyItemChanged(position)
            onSwitchQuality?.invoke(realPosition)
        }

        videoHolder.btnMoveVideo.setOnClickListener {
            openSettingsPosition = -1
            notifyItemChanged(position)
            if (isInFolderView) {
                onRemoveFromFolder?.invoke(realPosition)
            } else {
                onMoveToFolder?.invoke(realPosition)
            }
        }

        videoHolder.btnCancelSettings.setOnClickListener {
            openSettingsPosition = -1
            notifyItemChanged(position)
        }
    }

    private fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position + 1)
    }

    private fun playVideo(localVideo: LocalVideo) {
        if (localVideo.videoFileList != null && localVideo.videoFileList!!.size == 1) {
            val playerData = PlayerData(PlayerData.TYPE_LOCAL)
            val mediaPath = localVideo.videoFileList!![0]
            playerData.videoUrl = mediaPath
            if (localVideo.danmakuFileList != null && localVideo.danmakuFileList!!.isNotEmpty()) {
                playerData.danmakuUrl = localVideo.danmakuFileList!![0]
            }
            playerData.title = localVideo.title

            try {
                val player = PlayerApi.jumpToPlayer(playerData)
                if (mediaPath.endsWith("audio.m4a")) {
                    player.putExtra("audio_only", true)
                }
                context.startActivity(player)
            } catch (e: ActivityNotFoundException) {
                MsgUtil.showMsg("跳转失败")
                e.printStackTrace()
            }
        } else if (localVideo.videoFileList != null && localVideo.videoFileList!!.size > 1) {
            val intent = Intent()
            intent.setClass(context, LocalPageChooseActivity::class.java)
            intent.putExtra("pageList", localVideo.pageList)
            intent.putExtra("videoFileList", localVideo.videoFileList)
            intent.putExtra("danmakuFileList", localVideo.danmakuFileList)
            intent.putExtra("title", localVideo.title)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return if (localVideoList != null) localVideoList.size + 1 else 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) 1 else 0
    }

    class GotoHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var textView: TextView = itemView.findViewById(R.id.text)

        fun show(context: Context) {
            textView.text = "下载列表"
            itemView.setOnClickListener {
                context.startActivity(Intent(context, DownloadListActivity::class.java))
            }
        }
    }

    class LocalVideoHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.text_title)
        val extra: TextView = itemView.findViewById(R.id.text_extra)
        val cover: ImageView = itemView.findViewById(R.id.img_cover)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkbox_select)
        val settingsPanel: View = itemView.findViewById(R.id.videoSettingsPanel)

        // 设置面板按钮
        val btnUpdateDanmaku: TextView = itemView.findViewById(R.id.btnUpdateDanmaku)
        val layoutQuality: View = itemView.findViewById(R.id.layoutQuality)
        val textCurrentQuality: TextView = itemView.findViewById(R.id.textCurrentQuality)
        val btnMoveVideo: TextView = itemView.findViewById(R.id.btnMoveVideo)
        val btnCancelSettings: TextView = itemView.findViewById(R.id.btnCancelSettings)

        @SuppressLint("SetTextI18n")
        fun showLocalVideo(videoCard: LocalVideo, context: Context) {
            title.text = videoCard.title
            val size = String.format(Locale.CHINA, "%.2f", videoCard.size / 1000000f) + "MB"
            val qualityTag = if (videoCard.qualityList != null && videoCard.qualityList!!.isNotEmpty()) {
                " [" + videoCard.qualityList!![0] + "]"
            } else ""
            extra.text = size + qualityTag

            // 显示文件夹信息
            if (videoCard.folderName != null && videoCard.folderName!!.isNotEmpty()) {
                extra.text = extra.text.toString() + " | " + videoCard.folderName
            }

            try {
                Glide.with(BiliTerminal.context).asDrawable().load(videoCard.cover)
                    .transition(GlideUtil.getTransitionOptions())
                    .apply(RequestOptions.bitmapTransform(RoundedCorners(ToolsUtil.dp2px(5f))))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(cover)
            } catch (ignored: Exception) {}
        }
    }
}