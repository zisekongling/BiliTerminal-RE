package com.RobinNotBad.BiliClient.util

/**
 * 「列表 / 缩略图该解多大位图」的**纯策略**（不碰 Android API，见 `ThumbSizePolicyTest`）。
 *
 * <h3>为什么需要这个换算</h3>
 * 26.11.15 线上两条崩溃都指向「解码尺寸不受控」：
 * 1. `java.lang.OutOfMemoryError: Failed to allocate a 262156 byte allocation`，
 *    栈是 `RecyclerView.onTouchEvent` → `onCreateViewHolder` → `ImageView.<init>` →
 *    `TypedArray.getDrawable` → `Resources.loadDrawable` → `BitmapFactory.decodeStream`，
 *    当时进程只剩 209KB 可用。262156 ≈ 256×256×4B —— 行布局里那张 256px 的占位头像
 *    在资源缓存被系统回收后，**每建一行就重新按原分辨率解一次**。
 * 2. `Canvas: trying to draw too large(116761824 bytes) bitmap`，栈在 `TransitionDrawable.draw`
 *    （Glide crossfade 的双图叠加）——解码出来的位图没按控件尺寸收敛，一张 5400×5400 的位图
 *    （≈116MB）被直接拿去画。
 *
 * Glide 里唯一能钉死「解码出多大位图」的开关是 `override(宽, 高)`：不给它，解码尺寸就取决于
 * target 解析结果（控件已布局 → 控件尺寸；布局参数是 `wrap_content` 且尚未测量时 → 原图）。
 * 本函数只负责「控件尺寸 → 解码目标像素」这一步换算，并把结果夹在屏幕尺寸以内。
 *
 * <h3>取哪个尺寸</h3>
 * 依次取：**实测尺寸**（`getMeasuredWidth/Height`）→ **布局参数里的固定 px**（`layout_width`
 * 写成具体 dp/px 时，还没测量也能知道）→ 该维度未知则用屏幕对应边兜底。
 * 两个维度都拿不到（既没测量、布局又是 `match_parent` / `wrap_content`）时返回 0，
 * 表示**不要 override**：那种情况交给 Glide 等布局完成，比赌一个尺寸更不容易解大。
 */
object ThumbSizePolicy {

    /**
     * 解码目标的下限（px）。
     *
     * 控件只有几个像素宽（分隔用的小图标）时没必要按 0 去请求；给一个下限既避免
     * `override(0, 0)` 被 Glide 当成「原尺寸」，又不至于把内存浪费在没人看的地方。
     */
    const val MIN_EDGE_PX = 64

    /**
     * 算出这次请求该 `override` 成多大。
     *
     * @param measuredWidth  `View#getMeasuredWidth()`，未布局时 ≤ 0
     * @param measuredHeight `View#getMeasuredHeight()`，未布局时 ≤ 0
     * @param layoutWidth    `layout_width` 的像素值；`MATCH_PARENT`(-1) / `WRAP_CONTENT`(-2) 视为未知
     * @param layoutHeight   `layout_height` 的像素值，同上
     * @param boxWidth       硬上限（一般传屏幕宽），≤ 0 表示屏幕尺寸拿不到
     * @param boxHeight      硬上限（一般传屏幕高），≤ 0 表示屏幕尺寸拿不到
     * @param minEdge        单边下限，默认 [MIN_EDGE_PX]
     * @return `[宽, 高]`；返回 `[0, 0]` 表示**不要 override**（尺寸定不下来，交给 Glide）
     */
    @JvmStatic
    @JvmOverloads
    fun targetPixels(
        measuredWidth: Int,
        measuredHeight: Int,
        layoutWidth: Int,
        layoutHeight: Int,
        boxWidth: Int,
        boxHeight: Int,
        minEdge: Int = MIN_EDGE_PX
    ): IntArray {
        // 屏幕尺寸都拿不到（Context 还没就绪）就不猜了，保持 Glide 的默认行为。
        if (boxWidth <= 0 || boxHeight <= 0) return intArrayOf(0, 0)

        val width = resolve(measuredWidth, layoutWidth, boxWidth)
        val height = resolve(measuredHeight, layoutHeight, boxHeight)
        // 两个维度都定不下来：不 override（见类注释「取哪个尺寸」）。
        if (width == 0 && height == 0) return intArrayOf(0, 0)

        return intArrayOf(clampEdge(width, boxWidth, minEdge), clampEdge(height, boxHeight, minEdge))
    }

    /** 单个维度：实测 → 布局参数 → 屏幕兜底；都拿不到返回 0。 */
    private fun resolve(measured: Int, layout: Int, box: Int): Int = when {
        measured > 0 -> measured
        layout > 0 -> layout
        else -> box
    }

    /** 夹在「不小于 [minEdge]、不大于屏幕对应边」之间，防止解出比屏幕还大的位图。 */
    private fun clampEdge(size: Int, box: Int, minEdge: Int): Int =
        minOf(maxOf(size, if (minEdge > 0) minEdge else 1), box)
}
