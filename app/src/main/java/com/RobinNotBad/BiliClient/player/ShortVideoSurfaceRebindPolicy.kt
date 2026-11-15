package com.RobinNotBad.BiliClient.player

/**
 * 短视频页把 TextureView 的 Surface 交给播放器之后要做什么。
 *
 * 见 [ShortVideoSurfaceRebindPolicy] 与反馈 #167（短视频回滑黑屏）。
 */
enum class ShortVideoSurfaceAction {
    /** 没有可挂的 surface，或播放器已经释放：什么都不用做。 */
    NONE,

    /** 只把 surface 挂上去，出帧与起播交给 `onPrepared`（首次挂载、prepare 中重挂都走这里）。 */
    ATTACH_ONLY,

    /**
     * 已经 prepared 的播放器换了 surface：挂上之后必须 seek 回当前进度强制出一帧，
     * 否则画面停在黑帧（mediacodec 换 surface 后不会主动重绘上一帧，暂停中更不解新帧）。
     */
    ATTACH_REPAINT,

    /** 同 [ATTACH_REPAINT]，并且这一页正是用户在看的那一页：出帧后继续播。 */
    ATTACH_REPAINT_AND_START
}

/**
 * 短视频页 surface 重新可用时的处置策略（纯逻辑，便于 JVM 单测）。
 *
 * 背景（反馈 #167：下滑之后再上滑，划过的视频就黑了）：
 * 竖直分页把一页滑出屏幕时，RecyclerView 会把这页的 TextureView 从窗口上摘下来，
 * `onSurfaceTextureDestroyed` 随即把播放器的 surface 置空，而播放器对象本身还活着（只是被
 * `pausePlayer` 暂停了）。滑回来时拿到的是一个**新的** SurfaceTexture：只 `setSurface` 不会
 * 让画面回来，于是整页停在纯黑（封面在 prepare 时已隐藏、页底是黑色）。
 *
 * 判定只看这几件事：有没有 surface、这一页是否需要重新出帧、播放器是否 prepared、
 * 这一页现在是不是活跃、宿主是否停在短视频流上。每种组合各对应一个动作，避免「第一次挂载
 * 也去 seek」这种多余的扰动。
 */
object ShortVideoSurfaceRebindPolicy {

    /**
     * @param hasTexture 当前是否有可用的 SurfaceTexture（View 还没上屏时为 false）
     * @param needsRepaint 这一页需要重新出帧：surface 丢过（页被摘下来）或 prepare 完成时还没有 surface
     * @param isPrepared 播放器已经 prepared，可以出帧
     * @param isActive 这一页是当前活跃页（滑走时会被暂停，滑回来才是活跃页）
     * @param feedVisible 宿主停在第 0 页（短视频流）；横滑到详情页时为 false，不允许自动起播
     */
    fun decide(
        hasTexture: Boolean,
        needsRepaint: Boolean,
        isPrepared: Boolean,
        isActive: Boolean,
        feedVisible: Boolean
    ): ShortVideoSurfaceAction {
        if (!hasTexture) return ShortVideoSurfaceAction.NONE
        // 第一次挂载（或 prepare 中挂载）：onPrepared 会按 isActive 起播，不需要额外 seek
        if (!needsRepaint) return ShortVideoSurfaceAction.ATTACH_ONLY
        // 播放器还在 prepare：surface 只要挂上去就行，出帧由 onPrepared 之后开始
        if (!isPrepared) return ShortVideoSurfaceAction.ATTACH_ONLY
        // 丢过 surface 且已经 prepared：必须强制出帧，否则回来就是黑屏
        return if (isActive && feedVisible) ShortVideoSurfaceAction.ATTACH_REPAINT_AND_START
        else ShortVideoSurfaceAction.ATTACH_REPAINT
    }
}
