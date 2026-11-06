package com.RobinNotBad.BiliClient.player

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 弹幕数量保护的纯状态机单测。
 *
 * 阈值边界（等于 5000 是否算超限）、手动强制、数量查询失败的「失败开放」这几条
 * 写错了会直接改变用户看到的结果（该有弹幕的没有 / 该保护的没保护），必须在单测里钉死。
 */
class DanmakuLoadPolicyTest {

    private fun policy(
        requested: Boolean = true,
        protect: Boolean = true
    ) = DanmakuLoadPolicy(requested, protect)

    @Test
    fun `关闭保护时不需要查数量且直接加载`() {
        val policy = policy(protect = false)
        assertFalse(policy.needsCount())
        assertTrue(policy.shouldLoad())
        assertFalse(policy.isSuppressed())
    }

    @Test
    fun `开启保护但还没查数量时先要求查`() {
        val policy = policy()
        assertTrue(policy.needsCount())
        // 失败开放：查数量之前不阻塞加载
        assertTrue(policy.shouldLoad())
        assertFalse(policy.isSuppressed())
    }

    @Test
    fun `超过阈值时抑制自动加载`() {
        val policy = policy()
        policy.setCount(DanmakuLoadPolicy.AUTO_LOAD_LIMIT + 1)
        assertFalse(policy.needsCount())
        assertFalse(policy.shouldLoad())
        assertTrue(policy.isSuppressed())
    }

    @Test
    fun `恰好等于阈值不算超限`() {
        val policy = policy()
        policy.setCount(DanmakuLoadPolicy.AUTO_LOAD_LIMIT)
        assertTrue(policy.shouldLoad())
        assertFalse(policy.isSuppressed())
    }

    @Test
    fun `数量查询失败按失败开放处理`() {
        val policy = policy()
        policy.setCountUnavailable()
        assertFalse(policy.needsCount())
        assertTrue(policy.shouldLoad())
        assertFalse(policy.isSuppressed())
        assertEquals(DanmakuLoadPolicy.COUNT_UNKNOWN, policy.knownCount())
    }

    @Test
    fun `负数数量按未知处理`() {
        val policy = policy()
        policy.setCount(-5)
        assertTrue(policy.shouldLoad())
        assertFalse(policy.isSuppressed())
        assertEquals(DanmakuLoadPolicy.COUNT_UNKNOWN, policy.knownCount())
    }

    @Test
    fun `手动强制加载优先于数量保护`() {
        val policy = policy()
        policy.setCount(DanmakuLoadPolicy.AUTO_LOAD_LIMIT * 4)
        assertFalse(policy.shouldLoad())
        policy.forceLoad()
        assertTrue(policy.shouldLoad())
        assertFalse(policy.isSuppressed())
        assertFalse(policy.needsCount())
    }

    @Test
    fun `换视频重置数量但保留手动强制`() {
        val policy = policy()
        policy.setCount(DanmakuLoadPolicy.AUTO_LOAD_LIMIT + 100)
        policy.resetCount()
        assertTrue(policy.needsCount())
        policy.forceLoad()
        policy.resetCount()
        assertTrue(policy.shouldLoad())
        assertFalse(policy.needsCount())
    }

    @Test
    fun `没有弹幕时不查也不加载`() {
        val policy = policy(requested = false)
        assertFalse(policy.needsCount())
        assertFalse(policy.shouldLoad())
        policy.setRequested(true)
        assertTrue(policy.needsCount())
    }

    @Test
    fun `解析稿件信息里的弹幕总数`() {
        val body = JSONObject()
            .put("code", 0)
            .put("data", JSONObject().put("stat", JSONObject().put("danmaku", 12345)))
        assertEquals(12345L, DanmakuLoadPolicy.parseCount(body))
    }

    @Test
    fun `解析不到弹幕总数时返回未知`() {
        assertEquals(DanmakuLoadPolicy.COUNT_UNKNOWN, DanmakuLoadPolicy.parseCount(null))
        assertEquals(
            DanmakuLoadPolicy.COUNT_UNKNOWN,
            DanmakuLoadPolicy.parseCount(JSONObject().put("code", -404))
        )
        assertEquals(
            DanmakuLoadPolicy.COUNT_UNKNOWN,
            DanmakuLoadPolicy.parseCount(JSONObject().put("code", 0).put("data", JSONObject()))
        )
        assertEquals(
            DanmakuLoadPolicy.COUNT_UNKNOWN,
            DanmakuLoadPolicy.parseCount(
                JSONObject().put("code", 0)
                    .put("data", JSONObject().put("stat", JSONObject().put("danmaku", -1)))
            )
        )
    }
}
