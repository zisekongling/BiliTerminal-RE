package com.RobinNotBad.BiliClient.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FollowApi.joinTagIds] / [FollowApi.addUsersFields] 的单测（反馈 #88：长按已关注加入分组）。
 *
 * 这两段是「组装 tags/addUsers 请求参数」的纯逻辑：字段名写错时服务端只回一个 -400，
 * 真机上看起来像「请求出错了」，根本猜不到是拼写问题，所以字段名必须钉死。
 */
class FollowApiAddUsersTest {

    // ==================== joinTagIds：拼 tagids 参数 ====================

    @Test
    fun `单个分组 id 不加分隔符`() {
        assertEquals("207542", FollowApi.joinTagIds(listOf(207542)))
    }

    @Test
    fun `多个分组 id 用逗号连接`() {
        assertEquals("207542,23130", FollowApi.joinTagIds(listOf(207542, 23130)))
    }

    @Test
    fun `空列表返回空串`() {
        assertEquals("", FollowApi.joinTagIds(emptyList()))
    }

    @Test
    fun `null 返回空串而不是崩`() {
        assertEquals("", FollowApi.joinTagIds(null))
    }

    // ==================== addUsersFields：请求字段名与取值 ====================

    @Test
    fun `字段名必须是接口规定的三个`() {
        // tags/addUsers 用的是复数 tagids / fids（单分组、单用户也只传单值）
        val fields = FollowApi.addUsersFields(listOf(207542), listOf(319214221L), "csrf-token")
        assertEquals(setOf("tagids", "fids", "csrf"), fields.keys)
    }

    @Test
    fun `tagids 与 fids 都是逗号分隔串`() {
        val fields = FollowApi.addUsersFields(listOf(207542, 23130), listOf(319214221L), "t")
        assertEquals("207542,23130", fields["tagids"])
        assertEquals("319214221", fields["fids"])
    }

    @Test
    fun `csrf 为 null 时退化成空串而不是 null 字面量`() {
        val fields = FollowApi.addUsersFields(listOf(1), listOf(2L), null)
        assertEquals("", fields["csrf"])
    }

    @Test
    fun `加入与移动是两组不同的字段名`() {
        // 最容易混的一对：addUsers 用 tagids（复数、无 before/after），
        // moveUsers 用 beforeTagids/afterTagids。两边写反 = 用户以为加了分组、其实没加。
        val add = FollowApi.addUsersFields(listOf(1), listOf(2L), "t")
        val move = FollowApi.moveUsersFields(1, 2, listOf(3L), "t")
        assertTrue("addUsers 不该出现 beforeTagids", "beforeTagids" !in add)
        assertTrue("moveUsers 不该出现 tagids", "tagids" !in move)
        assertEquals(setOf("tagids", "fids", "csrf"), add.keys)
        assertEquals(setOf("beforeTagids", "afterTagids", "fids", "csrf"), move.keys)
    }
}
