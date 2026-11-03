package com.RobinNotBad.BiliClient.util

/**
 * 下载唤醒锁的引用计数（纯逻辑，不碰 `android.*`；JVM 单测见 `DownloadWakeLockCounterTest`）。
 *
 * 为什么需要计数：同一个「下载期间不休眠」锁有两个来源——
 * - 后台批量下载：`service/DownloadService.kt` 的前台服务线程；
 * - 前台单文件下载：`activity/DownloadActivity.kt`（图片保存、播放页的「前台下载」）。
 * 两者可以同时进行（用户一边下视频，一边存图片）。共用一个系统锁时只能靠计数判断
 * 「最后一个持有者什么时候退出」，否则图片页一结束就会把后台视频下载的锁一起放掉，
 * 视频照样被休眠挂住 —— 这正是反馈 #91 的原始现象之一。
 *
 * 设计取舍：[release] 在计数为 0 时返回 false 而不是抛异常。服务被系统提前销毁、
 * 下载线程被中断这类路径上「多释放一次」在所难免；宁可忽略，也不能把计数减成负数
 * （减成负数后下一次 [retain] 就永远等不到 1，等于功能静默失效）。
 */
class DownloadWakeLockCounter {

    /** 当前持有者数量，只读暴露给调用方与测试观察。 */
    var count: Int = 0
        private set

    /**
     * 申请一次持有。
     *
     * @return true 表示这是**第一个**持有者（调用方据此真正去申请系统锁）；
     *         false 表示锁已在别处生效，本次只是计数 +1。
     */
    fun retain(): Boolean {
        count++
        return count == 1
    }

    /**
     * 释放一次持有。
     *
     * @return true 表示这是**最后一个**持有者（调用方据此真正去释放系统锁）；
     *         false 表示还有别人在用，或计数本来就是 0（未配对的多余释放，按忽略处理）。
     */
    fun release(): Boolean {
        if (count <= 0) return false
        count--
        return count == 0
    }
}
