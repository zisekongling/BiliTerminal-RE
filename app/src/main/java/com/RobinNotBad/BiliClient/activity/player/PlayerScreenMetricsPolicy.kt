package com.RobinNotBad.BiliClient.activity.player

/**
 * 播放页「画面区域尺寸」的纯计算（零 Android 依赖，可直接 JVM 单测）。
 *
 * **为什么需要它**（反馈 #31「载入中切后台再回来画面比例奇怪」）：
 * 播放页的画面区域不是靠布局 wrap，而是用 `ijkPlayer.videoWidth/videoHeight` + 屏幕尺寸
 * 手算出 `layout_video` 的宽高，只在 `onPrepared` / `onConfigurationChanged` / 手动旋转时算一次。
 * 于是有两条会算出怪比例的路径：
 *
 * 1. **回前台不重算**：`onResume` 以往只停/启后台服务、应用字幕字号，从不重新应用画面尺寸。
 *    载入中切后台再回来，Surface 重建 / 窗口尺寸与 `screen_width/height` 可能已经不是
 *    `onPrepared` 当时那一份，而布局还停在旧计算结果上——横竖比例、居中位置都会歪。
 * 2. **重算时播放器恰好报 0**：重建窗口期 `videoWidth/videoHeight` 可能还是 0，
 *    原来的 `changeVideoSize()` 直接 early-return，等于把"歪掉的旧布局"留在屏幕上。
 *    所以要把**上一次已知的源尺寸**缓存起来兜底（与短视频 `applyVideoSizeFromState`
 *    用 `PlayerState` 里缓存的宽高同一个思路）。
 *
 * 本文件只放这两件纯计算；什么时候调、调完怎么摆位置仍由 `PlayerActivity` 决定。
 */
object PlayerScreenMetricsPolicy {

    /** 一对像素尺寸。 */
    data class Size(val width: Int, val height: Int)

    /**
     * 由窗口尺寸算出「画面坐标系」的屏幕宽高。
     *
     * 只有**软件旋屏**（`dev_player_rotate_software` 且要横屏）才需要宽高互换：
     * 那种模式下窗口本身没有真的转，画面靠 `root_layout.rotation = 90f` 摆出来。
     * 真旋转（`requestedOrientation`）之后 `DisplayMetrics` 已经是横屏值，**不能**再换一次。
     *
     * @param displayWidth `DisplayMetrics.widthPixels`
     * @param displayHeight `DisplayMetrics.heightPixels`
     * @param softwareLandscape 是否处于软件旋屏横屏态
     */
    fun screenSize(displayWidth: Int, displayHeight: Int, softwareLandscape: Boolean): Size =
        if (softwareLandscape) Size(displayHeight, displayWidth)
        else Size(displayWidth, displayHeight)

    /** 尺寸是否变了；没变就不要重排布局（回前台每帧都重排会闪）。 */
    fun changed(currentWidth: Int, currentHeight: Int, next: Size): Boolean =
        currentWidth != next.width || currentHeight != next.height

    /**
     * 找出这次可以用哪个「源视频尺寸」来算布局。
     *
     * 优先用播放器刚报的实时值；它无效（0 / 负数，重建窗口期常见）时退回上一次已知的有效值。
     * 两者都无效返回 `null`——此时**不要**动布局，也不要把它当成 1:1。
     *
     * @param currentWidth `ijkPlayer.videoWidth`
     * @param currentHeight `ijkPlayer.videoHeight`
     * @param cachedWidth 上一次已知有效的源宽（0 = 还没拿到过）
     * @param cachedHeight 上一次已知有效的源高
     */
    fun resolveSourceSize(
        currentWidth: Int,
        currentHeight: Int,
        cachedWidth: Int,
        cachedHeight: Int
    ): Size? = when {
        currentWidth > 0 && currentHeight > 0 -> Size(currentWidth, currentHeight)
        cachedWidth > 0 && cachedHeight > 0 -> Size(cachedWidth, cachedHeight)
        else -> null
    }
}
