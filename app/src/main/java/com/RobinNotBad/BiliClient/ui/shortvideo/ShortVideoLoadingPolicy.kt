package com.RobinNotBad.BiliClient.ui.shortvideo

/**
 * 信息流加载层（loadingLayout）该不该继续盖在画面上（纯函数，可单测）。
 *
 * 只有「一条内容都没有、本次加载没失败、也还没出过帧」时才盖：
 * 有内容时一次失败的刷新不该把转圈盖回正在播的画面上；
 * 出帧即消，不依赖列表回调是否准时到达。
 */
object ShortVideoLoadingPolicy {

    fun keepLoading(itemCount: Int, loadFailed: Boolean, firstFrameOut: Boolean): Boolean =
        itemCount <= 0 && !loadFailed && !firstFrameOut
}