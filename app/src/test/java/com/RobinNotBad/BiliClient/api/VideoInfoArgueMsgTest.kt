package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.VideoInfo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VideoInfoApi.getInfoByJson] 中「判了外层非空却用 getXxx」这类写法的 JVM 单测（task-8）。
 *
 * 缘起（release 日志复查 D9）：Revival 1.2.5-BETA 修过「视频详情缺少争议信息时崩溃」。
 * 我方原实现是 `if (optJSONObject("argue_info") != null) getJSONObject(...).getString("argue_msg")`，
 * 服务端出现「有 argue_info 对象、没有 argue_msg 字段」时 `getString` 抛 JSONException，
 * 整个详情页加载失败；而 `VideoInfoFragment` 又直接对 `argueMsg` 调 `isNotEmpty()`，null 会 NPE，
 * 所以这里**缺字段必须返回空串**。
 *
 * 同一批顺手把联合投稿 `staff`、成员 `official`、`owner` 与 `ugc_season.stat` 的同类写法 opt 化，
 * 对应测试放在同一个文件里，构造的都是「字段缺失但外层存在」的畸形 JSON。
 */
class VideoInfoArgueMsgTest {

    // region argue_info

    @Test
    fun noArgueInfo_returnsEmptyString() {
        assertEquals("", VideoInfoApi.parseArgueMsg(JSONObject()))
    }

    @Test
    fun nullArgueInfo_returnsEmptyString() {
        val data = JSONObject().put("argue_info", JSONObject.NULL)
        assertEquals("", VideoInfoApi.parseArgueMsg(data))
    }

    @Test
    fun argueInfoMissingArgueMsg_returnsEmptyString() {
        // 就是线上打挂详情页的那种形态：对象在，字段不在
        val data = JSONObject().put("argue_info", JSONObject())
        assertEquals("", VideoInfoApi.parseArgueMsg(data))
    }

    @Test
    fun argueInfoWithText_returnsText() {
        val argue = JSONObject().put("argue_msg", "该视频存在争议")
        val data = JSONObject().put("argue_info", argue)
        assertEquals("该视频存在争议", VideoInfoApi.parseArgueMsg(data))
    }

    @Test
    fun getInfoByJson_missingArgueMsg_doesNotThrow() {
        val data = minimalVideoJson().put("argue_info", JSONObject())
        val info: VideoInfo = VideoInfoApi.getInfoByJson(data)
        // 旧实现在这一行之前就抛 JSONException 了
        assertEquals("", info.argueMsg)
    }

    // endregion

    // region 联合投稿 / owner / ugc_season.stat

    @Test
    fun cooperationWithoutStaffArray_doesNotThrow() {
        // is_cooperation=1 但 staff 完全不返回：旧实现 getJSONArray("staff") 直接抛
        val data = minimalVideoJson().put("rights", JSONObject().put("is_cooperation", 1))
        val info = VideoInfoApi.getInfoByJson(data)
        assertTrue(info.isCooperation)
        assertTrue("缺 staff 时走空列表兜底", info.staff.isEmpty())
    }

    @Test
    fun staffMemberWithoutOfficial_doesNotThrow() {
        val staff = JSONArray().put(
            JSONObject()
                .put("mid", 42L)
                .put("name", "共同投稿UP")
                .put("face", "https://example.com/face.jpg")
                .put("title", "参与的事")
                .put("follower", 10)
        )
        val data = minimalVideoJson()
            .put("rights", JSONObject().put("is_cooperation", 1))
            .put("staff", staff)
        val info = VideoInfoApi.getInfoByJson(data)
        assertEquals(1, info.staff.size)
        assertEquals("共同投稿UP", info.staff[0].name)
        // official 缺失时保持默认 0 / null，不让整页崩
        assertEquals(0, info.staff[0].official)
        assertNull(info.staff[0].officialDesc)
    }

    @Test
    fun ugcSeasonWithoutStat_doesNotThrow() {
        val data = minimalVideoJson().put("ugc_season", JSONObject().put("id", 1).put("title", "合集"))
        val info = VideoInfoApi.getInfoByJson(data)
        assertEquals("0", info.collection.view)
    }

    // endregion

    private fun minimalVideoJson(): JSONObject {
        val stat = JSONObject()
            .put("view", 1)
            .put("like", 2)
            .put("coin", 3)
            .put("reply", 4)
            .put("danmaku", 5)
        return JSONObject()
            .put("title", "测试视频")
            .put("pic", "https://example.com/cover.jpg")
            .put("desc", "简介")
            .put("aid", 1L)
            .put("pubdate", 0L)
            .put("duration", 60)
            .put("stat", stat)
    }
}
