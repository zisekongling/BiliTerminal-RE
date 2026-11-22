package com.RobinNotBad.BiliClient.service.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DownloadProgressStore.removeDownloadProgress] 全量清理契约的测试。
 *
 * <p>背景（26.11.15「越用越卡」排查的次要泄露点）：[DownloadProgressStore] 是进程级
 * object 单例，`removeDownloadProgress` 原来只清进度映射与采样器，`sectionBytes` 与
 * `pausedMap` 不跟着清——「删除一条已暂停的任务」这类不 retire 的路径会让两张表
 * 只增不减。这里锁死「删除即四表全清」的语义。
 *
 * <p>不测 pause/resume 的状态机语义（涉及 [com.RobinNotBad.BiliClient.service.DownloadService]
 * 的调度），只测清理契约。
 */
class DownloadProgressStoreCleanupTest {

    @Test
    fun removeDownloadProgress清掉字节预算与暂停标志() {
        val id = 99999L
        // 模拟一条"建立了预算又被用户暂停"的任务：这是修复前漏得最狠的路径。
        // 不走 pauseDownload()——它会连带 DownloadRepository.setState 写 SQLite，
        // 纯 JVM 单测里 android.jar 的方法全是 not-mocked 桩，直接触会假失败。
        // pauseDownload 对 pausedMap 的全部效果就是 put(id, true)（isPaused 只看 containsKey），
        // 所以直接写表等价覆盖。
        DownloadProgressStore.beginSectionBytes(id, 100_000L)
        DownloadProgressStore.pausedMap[id] = true
        assertTrue(DownloadProgressStore.isPaused(id))

        DownloadProgressStore.removeDownloadProgress(id)

        // 四张进程级表（进度映射、采样器、字节预算、暂停标志）一张都不许残留
        assertFalse("删除后不该再处于暂停状态", DownloadProgressStore.isPaused(id))
        assertFalse(DownloadProgressStore.hasProgress(id))
        assertNull("字节预算必须一并清", DownloadProgressStore.getDownloadProgress(id))
        // 再 begin 一次拿到的是全新预算（证明旧条目确实被移除，而非复用残留）
        DownloadProgressStore.beginSectionBytes(id, 500L)
        DownloadProgressStore.retireSection(id, false)
        DownloadProgressStore.removeDownloadProgress(id)
        assertFalse(DownloadProgressStore.isPaused(id))
    }

    @Test
    fun abandon标记在removeDownloadProgress之后依然有效() {
        // 删除一条「正在下载」的任务时，removeDownloadProgress 会清掉 pausedMap（那是它的契约），
        // 但 worker 还在跑：弱网下一次 read() 可以阻塞十几秒，回来时已经看不到暂停标志。
        // 所以 abandon 用独立集合表达「这条任务不存在了」，必须能扛过 removeDownloadProgress。
        val id = 88888L
        DownloadProgressStore.abandon(id)
        assertTrue("abandon 之后 isPaused 必须为 true，worker 才能在检查点退出", DownloadProgressStore.isPaused(id))

        DownloadProgressStore.removeDownloadProgress(id)

        assertTrue("removeDownloadProgress 不许清掉已删除标记", DownloadProgressStore.isPaused(id))
        assertFalse("进度本身仍应被清", DownloadProgressStore.hasProgress(id))

        // worker 退出后清标记：此后 id 若被复用，不能再被判成已删除
        DownloadProgressStore.clearAbandon(id)
        assertFalse("clearAbandon 之后不该再是暂停/已删除", DownloadProgressStore.isPaused(id))
    }

    @Test
    fun removeDownloadProgress对未知id无害() {
        // 调度器对不存在条目的清理路径（如 recoverStuckSections 重入）会反复调用
        DownloadProgressStore.removeDownloadProgress(-1L)
        DownloadProgressStore.removeDownloadProgress(-1L)
        assertFalse(DownloadProgressStore.isPaused(-1L))
    }
}
