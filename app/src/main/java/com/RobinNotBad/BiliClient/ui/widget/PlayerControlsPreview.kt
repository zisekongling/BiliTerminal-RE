package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.player.PlayerButtonSlots
import com.RobinNotBad.BiliClient.player.PlayerButtons
import com.RobinNotBad.BiliClient.player.PlayerUiLayout
import com.RobinNotBad.BiliClient.util.ToolsUtil

/**
 * 设置页里的「真实播放器」预览。
 *
 * 这里不是仿画的图，而是把播放页的布局 [R.layout.activity_player] 整个充气出来、藏掉只在播放时
 * 才有意义的部件（弹幕层、字幕层、各种弹层、锁定遮罩），剩下的控制层就是播放页那一套真控件；
 * 按钮摆放同样交给 [PlayerButtonSlots]，所以「更多」展开、影子同步、单元格尺寸全都和播放页一致。
 *
 * 唯一不同的是**交互**：播放页里点按钮是执行动作，这里点按钮是打开它的设置卡——所以本类把
 * 每个真身的点击/长按换成了自己的回调（`onButtonClick` / `onButtonLongClick`），并额外接上
 * 「没有按钮对应的那块地方」（进度条、在线人数、标题栏、播放键、空白处）。
 */
class PlayerControlsPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** 点了一个按钮（真身或影子都走这里）。 */
    var onButtonClick: ((String) -> Unit)? = null

    /** 长按了一个按钮。 */
    var onButtonLongClick: ((String) -> Unit)? = null

    /** 长按了「更多」（编辑这一组）。 */
    var onMoreLongClick: ((PlayerUiLayout.Region, Int) -> Unit)? = null

    /** 点了某一列末尾的空格子。 */
    var onAddClick: ((PlayerUiLayout.Region) -> Unit)? = null

    /** 点了没有按钮对应的元素，取值 `progress` / `online` / `title` / `blank` / `play`。 */
    var onElementClick: ((String) -> Unit)? = null

    private val root: View
    private val slots: PlayerButtonSlots

    init {
        setBackgroundColor(Color.BLACK)
        root = LayoutInflater.from(context).inflate(R.layout.activity_player, this, false)
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        hidePlayerOnlyParts()
        bindSampleTexts()
        relaxRightColumn()
        bindSampleProgress()

        slots = PlayerButtonSlots(context, root.findViewById(R.id.button_pool)) { key -> buttonView(key) }
        for (region in PlayerUiLayout.Region.values()) {
            slots.bind(
                region,
                slotContainer(region),
                groupStrip(region),
                cellWidthDp(region),
                cellHeightDp(region)
            )
        }
        slots.trailingViewFactory = { region -> addCell(region) }
        slots.onMoreLongClick = { region, index, _ -> onMoreLongClick?.invoke(region, index) }

        wireButtons()
        wireElements()
    }

    /** 按用户布局把按钮摆好。整份重摆，调用方不用自己清场。 */
    fun render(layout: PlayerUiLayout.Layout) {
        slots.render(layout)
    }

    // ==================== 初始化 ====================

    /**
     * 藏掉播放页专用部件。
     *
     * 全部设成 GONE 而不只是透明：`sv_danmaku` 是 match_parent 的弹幕层、`card_bg` 是全屏弹层遮罩、
     * `lock_overlay` 是全屏吞触摸的覆盖层，留在那儿要么挡住点击，要么抢走我们的手势。
     */
    private fun hidePlayerOnlyParts() {
        val ids = intArrayOf(
            R.id.sv_danmaku,
            R.id.audio_only_layout,
            R.id.text_subtitle,
            R.id.lock_overlay,
            R.id.btn_debug,
            R.id.loading_info,
            R.id.layout_speed,
            R.id.showsound,
            R.id.layout_volume,
            R.id.card_bg,
            R.id.swipe_seek_overlay,
            R.id.text_progress
        )
        for (id in ids) root.findViewById<View>(id)?.visibility = View.GONE
    }

    /**
     * 让整棵控制层按 `1 / PREVIEW_SCALE` 的尺寸排版，再整体缩回框里。
     *
     * 播放页的控件是按「整块屏幕」画的（底部按钮行、左右两列都按屏幕高度摆），直接按缩略框
     * 的高度排版会挤在一起；放大画布再缩回来，等于把控件按比例缩小，占框的比例才和播放时一致。
     *
     * **必须在 onMeasure/onLayout 里做**：在 onSizeChanged 里改 root 的 layoutParams，父容器
     * 会用同一次布局里过期的测量值，整棵树会错位（进度条被顶到顶上就是这么来的）。
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (measuredWidth <= 0 || measuredHeight <= 0) return
        val innerWidth = (measuredWidth / PREVIEW_SCALE).toInt()
        val innerHeight = (measuredHeight / PREVIEW_SCALE).toInt()
        root.measure(
            MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(innerHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (root.width <= 0 || root.height <= 0) return
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        root.pivotX = 0f
        root.pivotY = 0f
        root.scaleX = PREVIEW_SCALE
        root.scaleY = PREVIEW_SCALE
    }

    /**
     * 播放页里右列是 `match_parent` 高 + 上下贴边，缩略框比较矮时 RelativeLayout 会算出负高度
     * （列直接不出现）。缩略里改成 `wrap_content` + 垂直居中，并且**必须把 ABOVE/BELOW 规则删掉**，
     * 否则它们仍然生效，负高度照旧。
     */
    private fun relaxRightColumn() {
        val column = root.findViewById<View>(R.id.right_control) ?: return
        val params = column.layoutParams as? RelativeLayout.LayoutParams ?: return
        params.removeRule(RelativeLayout.BELOW)
        params.removeRule(RelativeLayout.ABOVE)
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        params.addRule(RelativeLayout.CENTER_VERTICAL)
        column.layoutParams = params
    }

    /** 播放页的进度条是运行时喂数据的；预览给一个「播到一半」的样子，并把拖动的圆点去掉。 */
    private fun bindSampleProgress() {
        val bar = root.findViewById<SeekBar>(R.id.videoprogress) ?: return
        bar.thumb = null
        bar.progress = 35
        bar.secondaryProgress = 55
    }

    /** 给一个「样例」的样子：标题、时钟、在线人数、倍速文字——播放页里这些是运行时填的。 */
    private fun bindSampleTexts() {
        (root.findViewById<View>(R.id.text_title) as? TextView)?.text =
            context.getString(R.string.setting_player_ui_title_sample)
        (root.findViewById<View>(R.id.clock) as? TextView)?.text = SAMPLE_CLOCK
        (root.findViewById<View>(R.id.text_online) as? TextView)?.text =
            context.getString(R.string.setting_player_ui_online_sample)
        (root.findViewById<View>(R.id.text_speed) as? TextView)?.text =
            context.getString(R.string.setting_player_ui_speed_sample)
    }

    /** 每个按钮真身的点击/长按换成「打开它的设置」。 */
    private fun wireButtons() {
        for (button in PlayerButtons.ALL) {
            val view = buttonView(button.key) ?: continue
            // 播放页里 text_speed / viewpoint_btn / button_page_selector 由 showcon()/onPrepared()
            // 才置为可见，预览里没人做这件事，不显式打开它们放进槽位也是空的。
            view.visibility = View.VISIBLE
            view.setOnClickListener { onButtonClick?.invoke(button.key) }
            view.setOnLongClickListener {
                onButtonLongClick?.invoke(button.key)
                true
            }
        }
    }

    /** 没有按钮对应的那几块地方。 */
    private fun wireElements() {
        root.findViewById<View>(R.id.top)?.setOnClickListener { onElementClick?.invoke("title") }
        root.findViewById<View>(R.id.text_online)?.setOnClickListener { onElementClick?.invoke("online") }
        root.findViewById<View>(R.id.videoprogress)?.setOnClickListener { onElementClick?.invoke("progress") }
        root.findViewById<View>(R.id.button_video)?.setOnClickListener { onElementClick?.invoke("play") }
        root.isClickable = true
        root.setOnClickListener { onElementClick?.invoke("blank") }
        isClickable = true
        setOnClickListener { onElementClick?.invoke("blank") }
    }

    // ==================== 小工具 ====================

    /** 每列末尾的「＋」空格子：点它选一个按钮放进这一列。 */
    private fun addCell(region: PlayerUiLayout.Region): View = TextView(context).apply {
        text = context.getString(R.string.setting_player_ui_add_slot)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        contentDescription = context.getString(R.string.setting_player_ui_add_slot)
        layoutParams = LinearLayout.LayoutParams(
            ToolsUtil.dp2px(cellWidthDp(region)),
            ToolsUtil.dp2px(cellHeightDp(region))
        )
        setOnClickListener { onAddClick?.invoke(region) }
    }

    private fun cellWidthDp(region: PlayerUiLayout.Region): Float = when (region) {
        PlayerUiLayout.Region.LEFT, PlayerUiLayout.Region.RIGHT -> 22f
        else -> 26f
    }

    private fun cellHeightDp(region: PlayerUiLayout.Region): Float = when (region) {
        PlayerUiLayout.Region.LEFT, PlayerUiLayout.Region.RIGHT -> 22f
        else -> 30f
    }

    /** 某个区放槽位的容器（左列、右列、底部左右各一个）。 */
    private fun slotContainer(region: PlayerUiLayout.Region): ViewGroup = when (region) {
        PlayerUiLayout.Region.LEFT -> root.findViewById(R.id.left_column_slots)
        PlayerUiLayout.Region.RIGHT -> root.findViewById(R.id.right_column_slots)
        PlayerUiLayout.Region.BOTTOM_LEFT -> root.findViewById(R.id.bottom_left_slots)
        PlayerUiLayout.Region.BOTTOM_RIGHT -> root.findViewById(R.id.bottom_right_slots)
    }

    /** 某个区展开组按钮用的横向条；底部左右共用一条。 */
    private fun groupStrip(region: PlayerUiLayout.Region): ViewGroup = when (region) {
        PlayerUiLayout.Region.LEFT -> root.findViewById(R.id.left_group_slots)
        PlayerUiLayout.Region.RIGHT -> root.findViewById(R.id.right_group_slots)
        else -> root.findViewById(R.id.bottom_group_slots)
    }

    /** 槽位 key → 播放页里的那个真按钮。 */
    private fun buttonView(key: String): View? {
        val id = when (key) {
            PlayerButtons.LOCK -> R.id.lock_btn
            PlayerButtons.SPEED -> R.id.text_speed
            PlayerButtons.DANMAKU -> R.id.danmaku_btn
            PlayerButtons.ROTATE -> R.id.rotate_btn
            PlayerButtons.DANMAKU_SEND -> R.id.danmaku_send_btn
            PlayerButtons.SUBTITLE -> R.id.subtitle_btn
            PlayerButtons.LOOP -> R.id.loop_btn
            PlayerButtons.AUDIO_ONLY -> R.id.audio_only_btn
            PlayerButtons.AUTO_NEXT -> R.id.auto_next_btn
            PlayerButtons.VIEWPOINT -> R.id.viewpoint_btn
            PlayerButtons.VIDEO_INFO -> R.id.video_info_btn
            PlayerButtons.JUMP_TIME -> R.id.jump_time_btn
            PlayerButtons.QUEUE_MODE -> R.id.queue_mode_btn
            PlayerButtons.QUALITY -> R.id.button_quality
            PlayerButtons.SOUND_CUT -> R.id.button_sound_cut
            PlayerButtons.SOUND_ADD -> R.id.button_sound_add
            PlayerButtons.PAGE_SELECTOR -> R.id.button_page_selector
            else -> return null
        }
        return root.findViewById(id)
    }

    private companion object {
        /** 播放页里时钟是运行时更新的，预览给个固定值就够了。 */
        const val SAMPLE_CLOCK = "20:00"

        /** 控制层按 `1 / PREVIEW_SCALE` 排版后缩放回来，等于控件缩小到这么点。 */
        const val PREVIEW_SCALE = 0.72f
    }
}
