package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载唤醒锁引用计数的纯逻辑测试。
 *
 * 为什么钉这几条：这个计数决定「什么时候真的放掉系统锁」。算错只有两个后果，
 * 而且都很难在真机上发现——
 * - 提前归零：另一个下载入口还在跑，锁却被放掉，熄屏后下载又被挂住（反馈 #91 复发）；
 * - 计数被减成负数：之后 retain 永远等不到 1，锁再也不会被申请，功能静默失效。
 */
class DownloadWakeLockCounterTest {

    @Test
    fun retain_onlyFirstHolderGetsTrue() {
        val counter = DownloadWakeLockCounter()
        assertTrue("第一个持有者要去申请系统锁", counter.retain())
        assertFalse("锁已生效，第二个只计数", counter.retain())
        assertFalse(counter.retain())
        assertEquals(3, counter.count)
    }

    @Test
    fun release_onlyLastHolderGetsTrue() {
        val counter = DownloadWakeLockCounter()
        counter.retain()
        counter.retain()

        assertFalse("还有别人在下载，不能真释放", counter.release())
        assertTrue("最后一个退出才真释放", counter.release())
        assertEquals(0, counter.count)
    }

    @Test
    fun releaseWithoutRetain_isIgnoredNotNegative() {
        val counter = DownloadWakeLockCounter()
        assertFalse("未配对的多余释放按忽略处理", counter.release())
        assertEquals("计数不能被减成负数", 0, counter.count)

        // 多余的 release 之后，下一轮下载仍然必须能正常拿到锁
        assertTrue(counter.retain())
        assertTrue(counter.release())
    }

    @Test
    fun twoDownloadEntries_overlapThenRelease() {
        // 模拟「后台批量下载」与「前台图片保存」同时进行
        val counter = DownloadWakeLockCounter()
        assertTrue("后台下载先拿锁", counter.retain())
        assertFalse("图片保存加入，只是计数 +1", counter.retain())
        assertFalse("图片保存先结束，锁要留着给后台下载", counter.release())
        assertTrue("后台下载结束，这时才真正放锁", counter.release())
        assertEquals(0, counter.count)
    }
}
