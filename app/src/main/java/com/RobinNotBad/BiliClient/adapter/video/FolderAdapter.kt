package com.RobinNotBad.BiliClient.adapter.video

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener
import com.RobinNotBad.BiliClient.model.VideoFolder

class FolderAdapter(
    private val context: Context,
    private var folderList: ArrayList<VideoFolder>
) : RecyclerView.Adapter<FolderAdapter.FolderHolder>() {

    var clickListener: OnItemClickListener? = null
    var longClickListener: OnItemLongClickListener? = null
    var renameListener: ((Int) -> Unit)? = null
    var disbandListener: ((Int) -> Unit)? = null

    // 多选模式
    var isMultiSelectMode: Boolean = false
    val selectedPositions = mutableSetOf<Int>()

    // 当前打开的设置面板位置，-1表示没有打开
    private var openSettingsPosition: Int = -1

    fun setOnClickListener(listener: OnItemClickListener) {
        this.clickListener = listener
    }

    fun setOnLongClickListener(listener: OnItemLongClickListener) {
        this.longClickListener = listener
    }

    fun updateList(newList: ArrayList<VideoFolder>) {
        folderList = newList
        notifyDataSetChanged()
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
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.cell_video_folder, parent, false)
        return FolderHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: FolderHolder, position: Int) {
        val folder = folderList[position]
        holder.folderName.text = folder.name
        holder.folderCount.text = "${folder.getVideoCount()}个视频"

        val isSettingsOpen = position == openSettingsPosition

        holder.folderContent.visibility = if (isSettingsOpen) View.INVISIBLE else View.VISIBLE
        holder.folderSettingsPanel.visibility = if (isSettingsOpen) View.VISIBLE else View.GONE

        // 滑动检测变量
        var startX = 0f
        var isSwiping = false
        val swipeThreshold = 80f

        holder.itemView.setOnTouchListener { view, event ->
            if (isMultiSelectMode) {
                // 多选模式下不处理滑动
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        if (!isSwiping) {
                            toggleSelection(position)
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
                    if (deltaX > 30f && !isSwiping) {
                        isSwiping = true
                    }
                    if (isSwiping) {
                        val newOpenPos = if (deltaX > swipeThreshold) position else -1
                        if (newOpenPos != openSettingsPosition) {
                            val oldOpen = openSettingsPosition
                            openSettingsPosition = newOpenPos
                            notifyItemChanged(position)
                            if (oldOpen >= 0 && oldOpen < folderList.size) {
                                notifyItemChanged(oldOpen)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isSwiping) {
                        // 点击事件
                        if (isSettingsOpen) {
                            // 如果设置面板已打开，点击关闭
                            openSettingsPosition = -1
                            notifyItemChanged(position)
                        } else {
                            clickListener?.onItemClick(position)
                        }
                    } else {
                        // 滑动结束，判断是否满足阈值
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

        // 设置面板按钮
        holder.btnRename.setOnClickListener {
            openSettingsPosition = -1
            notifyItemChanged(position)
            renameListener?.invoke(position)
        }

        holder.btnDisband.setOnClickListener {
            openSettingsPosition = -1
            notifyItemChanged(position)
            disbandListener?.invoke(position)
        }

        holder.btnCancel.setOnClickListener {
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
        notifyItemChanged(position)
    }

    override fun getItemCount(): Int = folderList.size

    inner class FolderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderContent: View = itemView.findViewById(R.id.folderContent)
        val folderSettingsPanel: View = itemView.findViewById(R.id.folderSettingsPanel)
        val folderName: TextView = itemView.findViewById(R.id.folderName)
        val folderCount: TextView = itemView.findViewById(R.id.folderCount)
        val btnRename: TextView = itemView.findViewById(R.id.btnFolderRename)
        val btnDisband: TextView = itemView.findViewById(R.id.btnFolderDisband)
        val btnCancel: TextView = itemView.findViewById(R.id.btnFolderCancel)
    }
}