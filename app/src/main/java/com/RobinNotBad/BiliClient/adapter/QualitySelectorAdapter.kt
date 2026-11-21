package com.RobinNotBad.BiliClient.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.NonNull
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.listener.OnItemClickListener
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.StringUtil
import com.google.android.material.button.MaterialButton

class QualitySelectorAdapter : RecyclerView.Adapter<QualitySelectorAdapter.QualityHolder>() {
    private var qualityNames: Array<String>? = null
    private var qualityValues: IntArray? = null
    private var context: Context? = null
    var listener: OnItemClickListener? = null

    /**
     * 高于这个 qn 的清晰度显示成「上锁」态并拦截点击（权限不够）。
     *
     * 由调用方按账号状态算出来（见 `player/QualityAccessPolicy`）：未登录 64、非大会员 80、
     * 大会员 `Int.MAX_VALUE`（全解锁）。锁的只是一个「好看但点了没用」的入口——服务端按账号
     * 权限发流，不会因为点了高档位就真给高档位。
     */
    var lockedMaxQuality: Int = Int.MAX_VALUE

    /** 点到被锁住的清晰度时回调，参数是那条的 qn（由调用方决定提示什么）。 */
    var onLockedItemClickListener: ((Int) -> Unit)? = null

    var selectedItemIndex: Int = 0
        set(value) {
            val previous = field
            field = value
            notifyItemChanged(previous)
            notifyItemChanged(value)
        }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(qualityNames: Array<String>, qualityValues: IntArray, currentQuality: Int) {
        this.qualityNames = qualityNames
        this.qualityValues = qualityValues
        this.selectedItemIndex = -1

        for (i in qualityValues.indices) {
            if (qualityValues[i] == currentQuality) {
                this.selectedItemIndex = i
                break
            }
        }

        if (this.selectedItemIndex == -1 && qualityValues.isNotEmpty()) {
            this.selectedItemIndex = 0
        }

        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.listener = listener
    }

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): QualityHolder {
        this.context = parent.context
        // 不再用 ContextThemeWrapper 强制 B站粉主题：cell_episode 已去掉布局级 android:theme，
        // 让按钮跟随当前主题（选中色由下面 bind() 用 ColorScheme 设）
        val view = LayoutInflater.from(this.context)
                .inflate(R.layout.cell_episode, parent, false)
        return QualityHolder(view)
    }

    override fun onBindViewHolder(@NonNull holder: QualityHolder, position: Int) {
        if (position < 0 || qualityNames == null || position >= qualityNames!!.size)
            return
        if (listener != null) {
            holder.listener = listener
        }
        holder.bind(position, selectedItemIndex == position)
    }

    override fun getItemCount(): Int {
        return if (qualityNames != null) qualityNames!!.size else 0
    }

    inner class QualityHolder(view: View) : RecyclerView.ViewHolder(view) {
        var listener: OnItemClickListener? = null
        private val button: MaterialButton = itemView.findViewById(R.id.btn)

        fun bind(currentIndex: Int, isSelected: Boolean) {
            if (currentIndex < 0 || qualityNames == null || currentIndex >= qualityNames!!.size)
                return
            val values = qualityValues ?: return
            val quality = if (currentIndex < values.size) values[currentIndex] else Int.MAX_VALUE
            val locked = quality > lockedMaxQuality
            button.text = qualityNames!![currentIndex]
            if (locked) {
                // 锁图标复用播放器锁屏按钮那张，整格再压暗一点：一眼能看出「有这个档但选不了」。
                // 锁住的项永远不会是当前档（当前档一定是能播的），所以不用管选中配色冲突。
                // 锁图标是矢量，传资源 id 的重载在 API<21 是原生加载会崩，走 Compat
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    button, null, null, StringUtil.getDrawable(button.context, R.drawable.icon_player_lock), null
                )
                button.compoundDrawablePadding =
                    (6 * button.resources.displayMetrics.density).toInt()
                button.alpha = 0.55f
            } else {
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                button.alpha = 1f
            }
            if (isSelected) {
                button.setTextColor(ColorScheme.ON_PRIMARY)
                button.setBackgroundColor(ColorScheme.PRIMARY)
            } else {
                button.setTextColor(ColorScheme.TEXT_PRIMARY)
                button.setBackgroundColor(ColorScheme.CARD)
            }
            button.setOnClickListener {
                if (locked) {
                    // 被锁住的档位不切换，交给调用方弹提示（未登录 / 非大会员）
                    onLockedItemClickListener?.invoke(quality)
                    return@setOnClickListener
                }
                selectedItemIndex = currentIndex
                if (listener != null) {
                    listener!!.onItemClick(currentIndex)
                }
            }
        }
    }
}