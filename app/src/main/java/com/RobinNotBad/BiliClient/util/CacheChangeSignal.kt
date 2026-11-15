package com.RobinNotBad.BiliClient.util

import java.util.concurrent.atomic.AtomicLong

/**
 * 进程级「缓存文件已经被动过」的信号（反馈 #162）。
 *
 * 详情页长按清缓存删的是磁盘文件，此时最上层是详情页，缓存列表页还压在返回栈下面——
 * `BiliTerminal.getInstanceActivityOnTop()` 找不到它，`refreshAfterExternalChange()` 也够不着。
 * 而缓存列表页的 `onResume` 只认三个变化（降级状态、缓存根目录、文件夹入口开关），文件变了不会重扫，
 * 于是条目、计数、封面全是旧的：条目赖着不走、`getVideoCount` 虚高、封面文件已经删了显示成黑块。
 *
 * 这里只存一个递增版本号：谁改了缓存文件就 [markChanged]，列表页在 `onResume` 里把
 * [snapshot] 和自己上次扫描时记下的值比一比，不一致就重扫。不持有 Activity / Context，不会泄漏。
 */
object CacheChangeSignal {

    private val version = AtomicLong(0)

    /** 缓存文件被改动过（删除、替换、外部搬走）——让还活着的列表页下次回来时重扫。 */
    @JvmStatic
    fun markChanged() {
        version.incrementAndGet()
    }

    /** 当前版本号。列表页扫描完记一份，`onResume` 里拿来比对。 */
    @JvmStatic
    fun snapshot(): Long = version.get()
}
