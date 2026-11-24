package com.RobinNotBad.BiliClient.util

/**
 * MediaMuxer 对 MP4 里 B 帧的支持边界。
 *
 * Android 官方文档写明：MediaMuxer 从 **Android 7.1（API 25，Nougat MR1）** 起才支持
 * muxing B-frames in MP4。更早的版本把带 B 帧的 H.264 写进 MediaMuxer，会在 native
 * 层直接崩溃（SIGSEGV / abort），Java 的 try-catch 接不住，也不会走 MediaMuxer 的
 * Java 异常路径。
 *
 * 抽成纯函数是为了让调用点语义明确，并能用 JVM 单测覆盖边界；真正的判定只看系统 API。
 */
internal object MediaMuxerSupport {

    /** 第一个支持 MP4 B 帧 muxing 的 API level。 */
    const val MIN_SDK_B_FRAME_MUXING = 25

    fun supportsBFrameMuxing(sdkInt: Int): Boolean = sdkInt >= MIN_SDK_B_FRAME_MUXING
}
