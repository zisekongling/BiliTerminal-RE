package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.player.PlayerButtons
import com.RobinNotBad.BiliClient.player.PlayerUiLayout

/**
 * 设置里那张「假播放器」缩略图。
 *
 * 它只是按 [PlayerUiLayout.Layout] 把三个区的槽位摆一遍，**不参与任何播放逻辑**：
 * 槽位里的按钮是照着 [PlayerButtons] 注册表现画的 ImageView，不是真按钮，也不镜像真按钮的状态。
 * 谁要响应点击就挂 [onSlotClick] / [onElementClick]。
 *
 * 布局照抄播放页的相对关系：标题栏贴顶、在线人数在右侧上部、左右两列竖直居中、
 * 进度条和底部一排贴底。缩略图固定横向 16:9，由外部容器给尺寸。
 */
class FakePlayerPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : RelativeLayout(context, attrs) {

    /** 点了某个槽位：第一个参数是区，第二个是该区槽位下标。 */
    var onSlotClick: ((PlayerUiLayout.Region, Int) -> Unit)? = null

    /**
     * 点了没有按钮对应的元素。取值：
     * `progress` 进度条、`online` 在线人数、`title` 标题栏、`blank` 画面空白、`play` 播放暂停。
     */
    var onElementClick: ((String) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val cellSize = dp(20f)

    private val titleRow: LinearLayout
    private val onlineText: TextView
    private val progressBar: View
    private val playButton: ImageView
    private val leftColumn: LinearLayout
    private val rightColumn: LinearLayout
    private val bottomLeft: LinearLayout
    private val bottomRight: LinearLayout

    init {
        setBackgroundColor(0xFF000000.toInt())
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6f), dp(4f), dp(6f), 0)
            id = generateViewId()
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.setting_player_ui_title_sample)
            textSize = 9f
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 1
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val clock = TextView(context).apply {
            text = "20:00"
            textSize = 8f
            setTextColor(0xCCFFFFFF.toInt())
        }
        titleRow.addView(clock, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        titleRow.setOnClickListener { onElementClick?.invoke("title") }

        onlineText = TextView(context).apply {
            text = context.getString(R.string.setting_player_ui_online_sample)
            textSize = 8f
            setTextColor(0xCCFFFFFF.toInt())
            setOnClickListener { onElementClick?.invoke("online") }
        }

        leftColumn = columnContainer()
        rightColumn = columnContainer()

        progressBar = View(context).apply {
            setBackgroundColor(0x66FFFFFF)
            setOnClickListener { onElementClick?.invoke("progress") }
        }

        playButton = ImageView(context).apply {
            setImageResource(R.drawable.btn_player_play)
            contentDescription = context.getString(R.string.setting_player_ui_label_play)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { onElementClick?.invoke("play") }
        }

        bottomLeft = rowContainer()
        bottomRight = rowContainer()

        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            id = generateViewId()
        }
        bottomBar.addView(bottomLeft, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        bottomBar.addView(playButton, LinearLayout.LayoutParams(dp(22f), dp(22f)).apply {
            marginStart = dp(4f)
            marginEnd = dp(4f)
        })
        bottomBar.addView(bottomRight, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        addView(titleRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            addRule(ALIGN_PARENT_TOP)
        })
        addView(onlineText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            addRule(ALIGN_PARENT_RIGHT)
            addRule(BELOW, titleRow.id)
            setMargins(0, dp(2f), dp(6f), 0)
        })
        addView(leftColumn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            addRule(ALIGN_PARENT_LEFT)
            addRule(CENTER_VERTICAL)
            setMargins(dp(4f), 0, 0, 0)
        })
        addView(rightColumn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            addRule(ALIGN_PARENT_RIGHT)
            addRule(CENTER_VERTICAL)
            setMargins(0, 0, dp(4f), 0)
        })
        addView(progressBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(2f)).apply {
            addRule(ALIGN_PARENT_BOTTOM)
            setMargins(dp(6f), 0, dp(6f), dp(26f))
        })
        addView(bottomBar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            addRule(ALIGN_PARENT_BOTTOM)
            addRule(CENTER_HORIZONTAL)
            setMargins(0, 0, 0, dp(4f))
        })

        // 空白画面：点按钮以外的区域
        setOnClickListener { onElementClick?.invoke("blank") }
    }

    /** 按布局重画一遍。只读展示，不写存档。 */
    fun render(layout: PlayerUiLayout.Layout) {
        fill(PlayerUiLayout.Region.LEFT, leftColumn, layout)
        fill(PlayerUiLayout.Region.RIGHT, rightColumn, layout)
        fill(PlayerUiLayout.Region.BOTTOM_LEFT, bottomLeft, layout)
        fill(PlayerUiLayout.Region.BOTTOM_RIGHT, bottomRight, layout)
    }

    private fun fill(region: PlayerUiLayout.Region, container: LinearLayout, layout: PlayerUiLayout.Layout) {
        container.removeAllViews()
        PlayerUiLayout.slots(layout, region).forEachIndexed { index, slot ->
            val view = when (slot) {
                is PlayerUiLayout.Slot.Button -> slotView(slot.key)
                is PlayerUiLayout.Slot.Group -> iconView(R.mipmap.morehide, R.string.desc_player_group_expand)
            }
            view.setOnClickListener { onSlotClick?.invoke(region, index) }
            // 图标统一成方形单元格；文字类（倍速 ×1.0）宽度随字号
            val cellWidth = if (view is ImageView) cellSize else LinearLayout.LayoutParams.WRAP_CONTENT
            container.addView(
                view,
                LinearLayout.LayoutParams(cellWidth, cellSize).apply {
                    if (container.orientation == LinearLayout.VERTICAL) topMargin = dp(2f) else marginEnd = dp(2f)
                }
            )
        }
    }

    private fun slotView(key: String): View {
        val button = PlayerButtons.byKey(key)
            ?: return iconView(R.mipmap.morehide, R.string.desc_player_group_expand)
        if (button.textOnly) {
            return TextView(context).apply {
                text = if (key == PlayerButtons.SPEED) context.getString(R.string.setting_player_ui_speed_sample)
                else context.getString(button.nameRes)
                textSize = 9f
                setTextColor(0xFFFFFFFF.toInt())
                maxLines = 1
                minWidth = cellSize
                gravity = Gravity.CENTER
            }
        }
        return iconView(button.iconRes, button.nameRes)
    }

    private fun iconView(iconRes: Int, descRes: Int): ImageView = ImageView(context).apply {
        setImageResource(iconRes)
        contentDescription = context.getString(descRes)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }.also { it.layoutParams = LinearLayout.LayoutParams(cellSize, cellSize) }

    private fun columnContainer() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun rowContainer() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun dp(value: Float): Int = (value * density + 0.5f).toInt()
}
