package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.FollowTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FollowApi.buildJoinGroupMenu] / [FollowApi.joinGroupLabel] 的单测（反馈 #266：长按已关注 → 特别关注）。
 *
 * 菜单是「下标 → 动作」的隐式映射：条目顺序一旦和点击处理里的取法对不上，
 * 用户点「特别关注」会被加进某个自建分组，而且在真机上完全看不出来是哪一步错的，
 * 所以顺序、过滤、-10 这个 id 都必须钉死。
 */
class FollowApiJoinGroupMenuTest {

    private fun tag(tagid: Int, name: String) = FollowTag(tagid, name, 0)

    private fun build(
        tags: List<FollowTag>? = emptyList(),
        joined: Set<Int> = emptySet()
    ) = FollowApi.buildJoinGroupMenu(tags, joined, "特别关注", "%1\$s（已加入）", "新建分组")

    // ==================== 顺序与过滤 ====================

    @Test
    fun `特别关注排最前、新建分组排最后`() {
        val entries = build(listOf(tag(207542, "同学"), tag(23130, "UP")))

        assertEquals(listOf("特别关注", "同学", "UP", "新建分组"), entries.map { it.label })
    }

    @Test
    fun `自建分组保持接口返回的顺序`() {
        val entries = build(listOf(tag(3, "C"), tag(1, "A"), tag(2, "B")))

        assertEquals(listOf(1, 2, 3), entries.drop(1).dropLast(1).map { it.tagid }.sorted())
        assertEquals(listOf("C", "A", "B"), entries.drop(1).dropLast(1).map { it.name })
    }

    @Test
    fun `非正数 id 的分组不进菜单`() {
        // x/relation/tags 理论上只回自建分组，但真回了 0 / -10 也不该和顶部的「特别关注」重复
        val entries = build(listOf(tag(0, "默认分组"), tag(-10, "特别关注"), tag(207542, "同学")))

        assertEquals(listOf("特别关注", "同学", "新建分组"), entries.map { it.label })
        // -10 在菜单里只能出现一次（顶部那一行），接口即便回了也不能再加一条
        assertEquals(1, entries.count { !it.create && it.tagid == FollowApi.TAG_SPECIAL_FOLLOW })
    }

    @Test
    fun `没有自建分组时只剩两项`() {
        val entries = build(null)

        assertEquals(2, entries.size)
        assertEquals("特别关注", entries[0].label)
        assertEquals("新建分组", entries[1].label)
    }

    // ==================== 下标 → 动作的映射 ====================

    @Test
    fun `第一项固定是特别关注且 tagid 为负十`() {
        val entry = build(listOf(tag(207542, "同学")))[0]

        assertEquals(FollowApi.TAG_SPECIAL_FOLLOW, entry.tagid)
        assertEquals(-10, entry.tagid)
        assertFalse("特别关注不是「新建分组」动作", entry.create)
        assertEquals("特别关注", entry.name)
    }

    @Test
    fun `最后一项是新建分组且不带 tagid`() {
        val entry = build(listOf(tag(207542, "同学"))).last()

        assertTrue(entry.create)
        assertEquals(0, entry.tagid)
    }

    @Test
    fun `点击下标取到的就是对应分组`() {
        // 适配器用 entries[which] 直接取，这里按同样的取法验一遍不错位
        val tags = listOf(tag(207542, "同学"), tag(23130, "UP"))
        val entries = build(tags)

        assertEquals(FollowApi.TAG_SPECIAL_FOLLOW, entries[0].tagid)
        assertEquals(207542, entries[1].tagid)
        assertEquals(23130, entries[2].tagid)
        assertEquals("UP", entries[2].name)
        assertTrue(entries[3].create)
    }

    // ==================== 「已加入」文案 ====================

    @Test
    fun `已在分组里的补已加入`() {
        val entries = build(listOf(tag(207542, "同学"), tag(23130, "UP")), setOf(207542))

        assertEquals("同学（已加入）", entries[1].label)
        assertEquals("UP", entries[2].label)
    }

    @Test
    fun `已在特别关注时第一项也标已加入`() {
        val entries = build(joined = setOf(FollowApi.TAG_SPECIAL_FOLLOW))

        assertEquals("特别关注（已加入）", entries[0].label)
    }

    @Test
    fun `新建分组永远不标已加入`() {
        val entries = build(listOf(tag(207542, "同学")), setOf(207542, FollowApi.TAG_SPECIAL_FOLLOW, 0))

        assertEquals("新建分组", entries.last().label)
    }

    @Test
    fun `合并了多条已加入`() {
        val entries = build(
            listOf(tag(1, "A"), tag(2, "B"), tag(3, "C")),
            setOf(FollowApi.TAG_SPECIAL_FOLLOW, 2)
        )

        assertEquals(listOf("特别关注（已加入）", "A", "B（已加入）", "C", "新建分组"), entries.map { it.label })
    }

    // ==================== joinGroupLabel 的退化路径 ====================

    @Test
    fun `未加入时原样返回分组名`() {
        assertEquals("同学", FollowApi.joinGroupLabel("同学", false, "%1\$s（已加入）"))
    }

    @Test
    fun `格式串为空时退化成直接拼已加入`() {
        // 调用方漏传格式串不该让菜单出现一个空字符串项
        assertEquals("同学（已加入）", FollowApi.joinGroupLabel("同学", true, null))
        assertEquals("同学（已加入）", FollowApi.joinGroupLabel("同学", true, ""))
    }

    @Test
    fun `分组名为 null 时不崩`() {
        assertEquals("", FollowApi.joinGroupLabel(null, false, "%1\$s（已加入）"))
        assertEquals("（已加入）", FollowApi.joinGroupLabel(null, true, "%1\$s（已加入）"))
    }

    @Test
    fun `已加入与未加入用同一个分组名格式串不会串味`() {
        // 共用格式串时最容易写反的一对：已加入那条必须真的走格式化
        val label = FollowApi.joinGroupLabel("UP", true, "%1\$s（已加入）")

        assertTrue(label.startsWith("UP"))
        assertTrue(label.endsWith("（已加入）"))
    }
}
