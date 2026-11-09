package com.RobinNotBad.BiliClient.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 看图页「该不该 clear 这一页的整图请求」的判据。
 *
 * 钉的是两件事：**没发起过的请求不要去 clear**（空转），**同一次请求只 clear 一次**。
 * 背景是线上崩溃 `Cannot recycle a resource while it is still acquired`
 * （`ResourceRecycler$ResourceRecyclerCallback.handleMessage` → `EngineResource.recycle`）：
 * 那条崩溃只能从「少制造回收/释放流量」这一侧收敛，所以这里不能退化成无条件 clear。
 */
class ImageViewerClearPolicyTest {

    private fun shouldClear(loadStarted: Boolean, cleared: Boolean): Boolean =
        ImageViewerClearPolicy.shouldClearPhoto(loadStarted, cleared)

    @Test
    fun `发起过且没清过时应该 clear`() {
        assertTrue(
            "这一页确实加载过整图、又还没清过，必须清掉省内存",
            shouldClear(loadStarted = true, cleared = false)
        )
    }

    @Test
    fun `同一次请求清理第二次要拦掉`() {
        assertFalse(
            "已经清过的请求再 clear 一次不进第二次释放，但会多跑一遍释放/淘汰的投递，必须拦掉",
            shouldClear(loadStarted = true, cleared = true)
        )
    }

    @Test
    fun `从未发起过加载时不要 clear`() {
        assertFalse(
            "探测失败或页从未轮到，整图请求根本不存在，clear 是空转",
            shouldClear(loadStarted = false, cleared = false)
        )
    }

    @Test
    fun `两个标记都为真时也不要 clear`() {
        assertFalse(shouldClear(loadStarted = false, cleared = true))
    }

    @Test
    fun `新一次加载复位标记后可以再清一次`() {
        // attachPhoto 每次发起加载都会把 photoCleared 置回 false，这里模拟「清过一次 → 重新加载 → 再清」
        var cleared = false
        assertTrue(ImageViewerClearPolicy.shouldClearPhoto(true, cleared))
        cleared = true
        assertFalse(ImageViewerClearPolicy.shouldClearPhoto(true, cleared))
        // 重新发起加载（attachPhoto 里 photoCleared = false）
        cleared = false
        assertTrue(
            "新请求是新的 EngineResource，必须允许再清一次",
            ImageViewerClearPolicy.shouldClearPhoto(true, cleared)
        )
    }
}
