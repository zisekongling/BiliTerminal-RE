package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.UserInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分组视图的「互关」徽章（26.11.08 修 bug 时补）。
 *
 * <p>背景：`x/relation/tag`（分组明细）的 `attribute` **恒为 0**，原来按它判互关等于永远
 * 判 false——分组模式下一个徽章都不出现，而普通关注/粉丝列表是正常的。现在改由
 * `GET /x/relation/relations?fids=` 批量补。这里钉住三件事：`fids` 的逗号拼法、
 * 只有 `attribute == 6` 才亮、以及**绝不误动 `specialFollow`**（特别关注本身就是一个分组，
 * 分组视图里再标一遍是重复）。
 */
class FollowGroupMutualFollowTest {

    private fun user(mid: Long) = UserInfo(mid, "u$mid", "", "", 0, 0, 0, true, "", 0, "", 0)

    @Test
    fun relationBatchUrl_joinsPositiveMidsWithComma() {
        val url = FollowApi.relationBatchUrl(listOf(user(100), user(200), user(300)))
        assertEquals("https://api.bilibili.com/x/relation/relations?fids=100,200,300", url)
    }

    @Test
    fun relationBatchUrl_skipsNonPositivePlaceholders() {
        // 本地占位行（mid = -1）不该被拼进请求，否则服务端只会回 -400
        val url = FollowApi.relationBatchUrl(listOf(user(-1), user(100)))
        assertEquals("https://api.bilibili.com/x/relation/relations?fids=100", url)
    }

    @Test
    fun relationBatchUrl_isEmptySafe() {
        assertEquals("https://api.bilibili.com/x/relation/relations?fids=", FollowApi.relationBatchUrl(emptyList()))
        assertEquals("https://api.bilibili.com/x/relation/relations?fids=", FollowApi.relationBatchUrl(null))
    }

    @Test
    fun applyRelationAttributes_onlyAttribute6LightsMutual() {
        val users = listOf(user(1), user(2), user(3), user(4))
        val data = JSONObject(
            """{"1":{"mid":1,"attribute":6},"2":{"mid":2,"attribute":2},
               "3":{"mid":3,"attribute":0},"4":{"mid":4,"attribute":128}}"""
        )

        val applied = FollowApi.applyRelationAttributes(users, data)

        assertEquals("四条都命中了 attribute", 4, applied)
        assertTrue("attribute == 6 是互关", users[0].mutualFollow)
        assertFalse("已关注但对方没关注我，不是互关", users[1].mutualFollow)
        assertFalse("未关注", users[2].mutualFollow)
        assertFalse("128 是已拉黑，不是互关", users[3].mutualFollow)
    }

    @Test
    fun applyRelationAttributes_keepsUnknownMidsFalse() {
        val users = listOf(user(1), user(999))
        val data = JSONObject("""{"1":{"mid":1,"attribute":6}}""")

        val applied = FollowApi.applyRelationAttributes(users, data)

        assertEquals("只命中 1 条", 1, applied)
        assertTrue(users[0].mutualFollow)
        assertFalse("接口没回这一条，保持不亮", users[1].mutualFollow)
    }

    @Test
    fun applyRelationAttributes_neverTouchesSpecial() {
        val users = listOf(user(1))
        users[0].specialFollow = true
        val data = JSONObject("""{"1":{"mid":1,"attribute":6,"special":0}}""")

        FollowApi.applyRelationAttributes(users, data)

        assertTrue("互关要亮", users[0].mutualFollow)
        assertTrue("special 不归这个方法管，不能被打回 false", users[0].specialFollow)
    }

    @Test
    fun applyRelationAttributes_handlesMissingData() {
        val users = listOf(user(1))
        assertEquals(0, FollowApi.applyRelationAttributes(users, null))
        assertFalse(users[0].mutualFollow)

        val empty = JSONObject("""{"code":-101}""")
        assertEquals(0, FollowApi.applyRelationAttributes(users, empty))
        assertFalse(users[0].mutualFollow)
    }

    @Test
    fun applyRelationAttributes_skipsNonNumericKeyAndNonObjectValue() {
        val users = listOf(user(1))
        val data = JSONObject().apply {
            put("not_a_mid", JSONObject().put("mid", 1).put("attribute", 6))
            put("1", "just a string")
        }

        assertEquals("键不是 mid、值不是对象都要跳过", 0, FollowApi.applyRelationAttributes(users, data))
        assertFalse(users[0].mutualFollow)
    }
}
