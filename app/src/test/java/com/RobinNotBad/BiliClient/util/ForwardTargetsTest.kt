package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `util/ForwardTargets.kt` 的取值单测。
 *
 * <p>这些数字本身没法从返回结果反推对错——写错了只会表现为「转发出去的动态卡片不对」，
 * 所以逐个钉死：改映射必须同时改这里，改动才会被看见。
 */
class ForwardTargetsTest {

    @Test
    fun 视频的转发类型是8() {
        assertEquals(8, ForwardTargets.videoDynType())
        assertEquals(8, ForwardTargets.DYN_TYPE_VIDEO)
    }

    @Test
    fun 收藏夹的转发类型是4300() {
        assertEquals(4300, ForwardTargets.favDynType())
        assertEquals(4300, ForwardTargets.DYN_TYPE_FAV)
    }

    /** 番剧 / 电影 / 纪录片 / 国创 / 电视剧 / 综艺逐个对表。 */
    @Test
    fun 番剧每个已知类型都有对应值() {
        assertEquals(4097, ForwardTargets.bangumiDynType(1))
        assertEquals(4098, ForwardTargets.bangumiDynType(2))
        assertEquals(4101, ForwardTargets.bangumiDynType(3))
        assertEquals(4100, ForwardTargets.bangumiDynType(4))
        assertEquals(4099, ForwardTargets.bangumiDynType(5))
        assertEquals(4099, ForwardTargets.bangumiDynType(7))
    }

    /** 电视剧(5)与综艺(7)在服务端是同一档，这里也必须是同一个值。 */
    @Test
    fun 电视剧与综艺同档() {
        assertEquals(ForwardTargets.bangumiDynType(5), ForwardTargets.bangumiDynType(7))
    }

    /** 没登记过的 pgc_type 必须返回 -1，让发布页拦下来，而不是发出去一张错卡片。 */
    @Test
    fun 未知类型一律返回负一() {
        for (pgcType in intArrayOf(0, 6, 99, -1)) {
            assertEquals("pgc_type=$pgcType 应返回 -1", -1, ForwardTargets.bangumiDynType(pgcType))
        }
    }

    @Test
    fun 已知类型都不是负一() {
        for (pgcType in intArrayOf(1, 2, 3, 4, 5, 7)) {
            assertTrue("pgc_type=$pgcType 不该返回 -1", ForwardTargets.bangumiDynType(pgcType) > 0)
        }
    }
}
