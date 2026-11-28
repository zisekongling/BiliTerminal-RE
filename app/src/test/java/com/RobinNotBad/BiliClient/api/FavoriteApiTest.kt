package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.VideoCard
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FavoriteApiTest {

    @Test
    fun parseFavoriteState_validResponse_fillsAllLists() {
        val json = """{"count":2,"list":[
            {"id":44233921,"fid":442339,"title":"默认收藏夹","fav_state":1,"media_count":85,"attr":0},
            {"id":936347621,"fid":9363476,"title":"自建收藏夹","fav_state":0,"media_count":2,"attr":22}
        ]}"""
        val folderList = ArrayList<String>()
        val fidList = ArrayList<Long>()
        val mediaIdList = ArrayList<Long>()
        val stateList = ArrayList<Boolean>()
        val countList = ArrayList<Int>()
        val maxCountList = ArrayList<Int>()

        FavoriteApi.parseFavoriteState(
            JSONObject(json), folderList, fidList, mediaIdList,
            stateList, countList, maxCountList
        )

        assertEquals("应解析出2条", 2, folderList.size)
        assertEquals("默认收藏夹", folderList[0])
        assertEquals("自建收藏夹", folderList[1])
        assertEquals("fid", 442339L, fidList[0])
        assertEquals("fid", 9363476L, fidList[1])
        assertEquals("media_id 取 id 字段", 44233921L, mediaIdList[0])
        assertEquals("media_id 取 id 字段", 936347621L, mediaIdList[1])
        assertTrue("fav_state=1 已收藏", stateList[0])
        assertFalse("fav_state=0 未收藏", stateList[1])
        assertEquals("media_count", 85, countList[0])
        assertEquals("默认收藏夹上限", 50000, maxCountList[0])
        assertEquals("自建收藏夹上限", 1000, maxCountList[1])
    }

    /** v3 接口不再返回 max_count，默认收藏夹只能靠 attr 的 bit1 判断（bit0 只管公开/私密） */
    @Test
    fun parseFavoriteState_defaultFolderDetectedByAttrNotIndex() {
        val json = """{"list":[
            {"id":2,"fid":3,"title":"自建","fav_state":0,"media_count":1,"attr":22},
            {"id":1,"fid":2,"title":"默认","fav_state":1,"media_count":999,"attr":0}
        ]}"""
        val folderList = ArrayList<String>()
        val fidList = ArrayList<Long>()
        val mediaIdList = ArrayList<Long>()
        val stateList = ArrayList<Boolean>()
        val countList = ArrayList<Int>()
        val maxCountList = ArrayList<Int>()

        FavoriteApi.parseFavoriteState(
            JSONObject(json), folderList, fidList, mediaIdList,
            stateList, countList, maxCountList
        )

        assertEquals("先出现的是自建收藏夹", 1000, maxCountList[0])
        assertEquals("默认收藏夹不靠下标判断", 50000, maxCountList[1])
    }

    @Test
    fun parseFavoriteState_mediaCountMissing_defaultsZero() {
        val json = """{"list":[{"id":9,"fid":1,"title":"a","fav_state":0,"attr":0}]}"""
        val folderList = ArrayList<String>()
        val fidList = ArrayList<Long>()
        val mediaIdList = ArrayList<Long>()
        val stateList = ArrayList<Boolean>()
        val countList = ArrayList<Int>()
        val maxCountList = ArrayList<Int>()

        FavoriteApi.parseFavoriteState(
            JSONObject(json), folderList, fidList, mediaIdList,
            stateList, countList, maxCountList
        )

        assertEquals("media_count 缺失兜底0", 0, countList[0])
        assertEquals("attr=0 即默认收藏夹", 50000, maxCountList[0])
        assertEquals("id 字段即 media_id", 9L, mediaIdList[0])
    }

    @Test
    fun parseFavoriteState_nullOrNoList_leavesListsEmpty() {
        val folderList = ArrayList<String>()
        val fidList = ArrayList<Long>()
        val mediaIdList = ArrayList<Long>()
        val stateList = ArrayList<Boolean>()
        val countList = ArrayList<Int>()
        val maxCountList = ArrayList<Int>()

        FavoriteApi.parseFavoriteState(
            null, folderList, fidList, mediaIdList, stateList, countList, maxCountList
        )
        assertTrue("data=null 不崩溃", folderList.isEmpty())
        assertTrue("data=null 不塞 media_id", mediaIdList.isEmpty())

        val noList = JSONObject("""{"count":0}""")
        FavoriteApi.parseFavoriteState(
            noList, folderList, fidList, mediaIdList, stateList, countList, maxCountList
        )
        assertTrue("无list字段不崩溃", folderList.isEmpty())
        assertTrue("无list字段不塞 media_id", mediaIdList.isEmpty())
    }

    /**
     * attr 位定义（bilibili-API/docs/fav/info.md）：bit0=1 私有、bit1=1 非默认。
     * 真实值：默认夹 0 / 自建夹 22（bit0=0 公开 + bit1=1 非默认 + 其它位），所以只看 bit1。
     */
    @Test
    fun isDefaultFolder_checksAttrBit1() {
        assertTrue("attr=0 是默认收藏夹", FavoriteApi.isDefaultFolder(0))
        assertFalse("attr=2 的 bit1 已置位，不是默认收藏夹", FavoriteApi.isDefaultFolder(2))
        assertFalse("attr=22（线上自建夹真实值）不是默认收藏夹", FavoriteApi.isDefaultFolder(22))
        assertFalse("attr=3 私密自建，bit1 已置位", FavoriteApi.isDefaultFolder(3))
    }

    @Test
    fun maxCountOf_followsConvention() {
        assertEquals(50000, FavoriteApi.maxCountOf(true))
        assertEquals(1000, FavoriteApi.maxCountOf(false))
    }

    /** 单测里别调 VideoCard 的 android 相关成员，只 new + 塞 aid */
    private fun card(aid: Long): VideoCard {
        val card = VideoCard()
        card.aid = aid
        card.title = "t$aid"
        return card
    }

    @Test
    fun buildResources_joinsAidAndType() {
        val cards = listOf(card(1L), card(2L))
        assertEquals("1:2,2:2", FavoriteApi.buildResources(cards))
    }

    @Test
    fun buildResources_skipsNullAndBadAid() {
        @Suppress("UNCHECKED_CAST")
        val cards = listOf<VideoCard?>(card(0L), null, card(-5L), card(3L)) as List<VideoCard>
        assertEquals("跳过 aid<=0 与 null 后只剩一条", "3:2", FavoriteApi.buildResources(cards))
    }

    @Test
    fun buildResources_nullOrEmpty_returnsEmpty() {
        assertEquals("", FavoriteApi.buildResources(null))
        assertEquals("", FavoriteApi.buildResources(emptyList()))
    }

    // ==================== 收藏内容类型（favType） ====================

    /**
     * 类型定义见 bilibili-API/docs/fav/action.md 的 resources 参数：
     * 2=视频稿件 / 12=音频 / 21=视频合集。只有这三种合法，其它一律落回视频稿件。
     */
    @Test
    fun normalizeFavType_keepsKnownTypes() {
        assertEquals("视频稿件", 2, FavoriteApi.normalizeFavType(2))
        assertEquals("音频", 12, FavoriteApi.normalizeFavType(12))
        assertEquals("视频合集", 21, FavoriteApi.normalizeFavType(21))
        assertEquals("常量与字面量必须一致", FavoriteApi.FAV_TYPE_VIDEO, 2)
        assertEquals("常量与字面量必须一致", FavoriteApi.FAV_TYPE_AUDIO, 12)
        assertEquals("常量与字面量必须一致", FavoriteApi.FAV_TYPE_COLLECTION, 21)
    }

    /** 0（字段缺失的默认值）、1/3（别的接口里的 type 值）、99（脏数据）都必须回退 2 */
    @Test
    fun normalizeFavType_fallsBackToVideo() {
        assertEquals("0 回退视频稿件", 2, FavoriteApi.normalizeFavType(0))
        assertEquals("1 回退视频稿件", 2, FavoriteApi.normalizeFavType(1))
        assertEquals("3 回退视频稿件", 2, FavoriteApi.normalizeFavType(3))
        assertEquals("99 回退视频稿件", 2, FavoriteApi.normalizeFavType(99))
    }

    /** 音频的 auid、合集的 id 必须带对自己的类型，否则会被当成视频 avid 发出去 */
    @Test
    fun buildResources_keepsMixedTypes() {
        val cards = listOf(card(1L), card(2L).also { it.favType = 12 }, card(3L).also { it.favType = 21 })
        assertEquals("1:2,2:12,3:21", FavoriteApi.buildResources(cards))
    }

    @Test
    fun buildResources_dirtyTypeFallsBackToVideo() {
        val cards = listOf(card(4L).also { it.favType = 7 })
        assertEquals("4:2", FavoriteApi.buildResources(cards))
    }

    // ==================== 整夹全选用的 id 列表（x/v3/fav/resource/ids） ====================

    /**
     * 「全选整个收藏夹」拿到的是 `{id, type}` 而不是完整卡片，批量写操作要的
     * `resources` 串格式与 [FavoriteApi.buildResources] 完全一致，所以这两者必须对得上。
     */
    @Test
    fun buildResourcesFromIds_joinsIdAndType() {
        val ids = listOf(FavoriteApi.FavId(371494037L, 2), FavoriteApi.FavId(328991940L, 2))
        assertEquals("371494037:2,328991940:2", FavoriteApi.buildResourcesFromIds(ids))
    }

    @Test
    fun buildResourcesFromIds_keepsMixedTypes() {
        val ids = listOf(
            FavoriteApi.FavId(1L, FavoriteApi.FAV_TYPE_VIDEO),
            FavoriteApi.FavId(2L, FavoriteApi.FAV_TYPE_AUDIO),
            FavoriteApi.FavId(3L, FavoriteApi.FAV_TYPE_COLLECTION)
        )
        assertEquals("1:2,2:12,3:21", FavoriteApi.buildResourcesFromIds(ids))
    }

    @Test
    fun buildResourcesFromIds_skipsNullAndBadId() {
        @Suppress("UNCHECKED_CAST")
        val ids = listOf<FavoriteApi.FavId?>(FavoriteApi.FavId(0L, 2), null, FavoriteApi.FavId(-5L, 2), FavoriteApi.FavId(3L, 2)) as List<FavoriteApi.FavId>
        assertEquals("跳过 id<=0 与 null 后只剩一条", "3:2", FavoriteApi.buildResourcesFromIds(ids))
    }

    @Test
    fun buildResourcesFromIds_nullOrEmpty_returnsEmpty() {
        assertEquals("", FavoriteApi.buildResourcesFromIds(null))
        assertEquals("", FavoriteApi.buildResourcesFromIds(emptyList()))
    }

    /** ids 接口的 type 同样是脏的（0 / 未知值），构造时就归一化，不能等到拼串再兜 */
    @Test
    fun favId_normalizesTypeOnConstruction() {
        assertEquals("未知类型回退视频稿件", FavoriteApi.FAV_TYPE_VIDEO, FavoriteApi.FavId(1L, 99).type)
        assertEquals("音频保持", FavoriteApi.FAV_TYPE_AUDIO, FavoriteApi.FavId(1L, 12).type)
        assertEquals("合集保持", FavoriteApi.FAV_TYPE_COLLECTION, FavoriteApi.FavId(1L, 21).type)
    }

    /**
     * 真实响应片段（26.11.26 无 Cookie 实测公开收藏夹 media_id=1052622027，
     * 依据 bilibili-API/docs/fav/list.md §获取收藏夹全部内容id）。
     * 注意公开收藏夹**不需要登录**也能读到，私密夹才要。
     */
    @Test
    fun parseFolderAllIds_realResponse_parsesIdsInOrder() {
        val json = JSONObject(
            """
            {"code":0,"message":"OK","ttl":1,"data":[
              {"id":371494037,"type":2,"bv_id":"BV1CZ4y1T7gC","bvid":"BV1CZ4y1T7gC"},
              {"id":328991940,"type":2,"bv_id":"BV1oA411a72k","bvid":"BV1oA411a72k"},
              {"id":884042215,"type":2,"bv_id":"BV1fK4y1e7Yj","bvid":"BV1fK4y1e7Yj"}
            ]}
            """.trimIndent()
        )
        val page = FavoriteApi.parseFolderAllIds(json)
        assertEquals("成功码", 0, page.code)
        assertEquals("条数", 3, page.ids.size)
        assertEquals("顺序与原响应一致", 371494037L, page.ids[0].id)
        assertEquals("视频稿件类型", FavoriteApi.FAV_TYPE_VIDEO, page.ids[0].type)
        assertEquals("拼串可直接喂给批量接口", "371494037:2,328991940:2,884042215:2",
            FavoriteApi.buildResourcesFromIds(page.ids))
    }

    /** 未登录访问私密夹会回非 0，必须能被识别成「失败」而不是「空夹」 */
    @Test
    fun parseFolderAllIds_codeNonZero_returnsFailure() {
        val page = FavoriteApi.parseFolderAllIds(JSONObject("""{"code":-403,"message":"访问权限不足"}"""))
        assertEquals("失败码统一归一成 -1", -1, page.code)
        assertTrue("失败时不带回半截数据", page.ids.isEmpty())
    }

    @Test
    fun parseFolderAllIds_dataNull_returnsFailure() {
        val page = FavoriteApi.parseFolderAllIds(JSONObject("""{"code":0,"message":"OK","data":null}"""))
        assertEquals(-1, page.code)
        assertTrue(page.ids.isEmpty())
    }

    @Test
    fun parseFolderAllIds_skipsBadIdAndNormalizesType() {
        val json = JSONObject(
            """{"code":0,"data":[{"id":0,"type":2},{"id":-3,"type":2},{"id":7,"type":99},{"id":8,"type":21}]}"""
        )
        val page = FavoriteApi.parseFolderAllIds(json)
        assertEquals("只剩两条合法 id", 2, page.ids.size)
        assertEquals("脏类型回退视频稿件后再拼串", "7:2,8:21", FavoriteApi.buildResourcesFromIds(page.ids))
    }

    @Test
    fun resourceErrorMsg_mapsKnownCodes() {
        assertEquals("成功无文案", "", FavoriteApi.resourceErrorMsg(0))
        assertEquals("还没有登录喵~", FavoriteApi.resourceErrorMsg(-101))
        assertEquals("登录凭证已失效，请重新登录", FavoriteApi.resourceErrorMsg(-111))
        assertEquals("请求出错了，请稍后再试", FavoriteApi.resourceErrorMsg(-400))
        assertEquals("内容不存在，可能已经被删除了", FavoriteApi.resourceErrorMsg(11010))
        assertEquals("未知码带原码", "操作失败（错误码 7）", FavoriteApi.resourceErrorMsg(7))
    }

    // ==================== folder/info 编辑页预填 ====================

    /**
     * attr 位定义见 bilibili-API/docs/fav/info.md（attr 属性位表）：bit0=1 私有、bit1=1 非默认。
     * 公开示例取文档 folder/info 响应里的 attr=54（bit0=0 公开、bit1=1 非默认），
     * 并带真实字段 id/fid/title/intro/media_count。
     */
    @Test
    fun parseFolderInfo_publicAttr_privacyIsPublic() {
        val json = JSONObject(
            """{"code":0,"message":"0","ttl":1,"data":{
                "id":1052622027,"fid":10526220,"mid":7792521,
                "attr":54,"title":"猛 男 生 存","cover":"http://i0.hdslb.com/bfs/xxx.jpg",
                "intro":"猛 男 生 存","media_count":28
            }}"""
        )

        val folder = FavoriteApi.parseFolderInfo(json)

        assertEquals("attr bit0=0 是公开", 0, folder.privacy)
        assertEquals("简介取自 intro 字段", "猛 男 生 存", folder.intro)
        assertEquals("标题取自 title 字段", "猛 男 生 存", folder.name)
        assertEquals("mediaId 取 data.id（mlid）", 1052622027L, folder.mediaId)
        assertEquals("id 取 fid", 10526220L, folder.id)
        assertEquals("media_count", 28, folder.videoCount)
        assertFalse("attr=54 的 bit1=1，不是默认收藏夹", folder.isDefault)
    }

    @Test
    fun parseFolderInfo_privateAttrBit0_privacyIsPrivate() {
        // attr=23 = 0b10111：bit0=1 私有、bit1=1 非默认（自建私密收藏夹的典型值）
        val json = JSONObject(
            """{"code":0,"message":"0","data":{
                "id":936347621,"fid":9363476,"mid":7792521,
                "attr":23,"title":"私密收藏夹","intro":"只有我能看","media_count":2
            }}"""
        )

        val folder = FavoriteApi.parseFolderInfo(json)

        assertEquals("attr bit0=1 是私密，编辑页保存时必须原样带回", 1, folder.privacy)
        assertTrue("isDefaultFolder 只看 bit1", !folder.isDefault)
    }

    @Test
    fun parseFolderInfo_introMissing_defaultsEmpty() {
        // 旧收藏夹/接口裁剪时可能没有 intro、media_count 字段，不能让异常冒到编辑页
        val json = JSONObject(
            """{"code":0,"message":"0","data":{"id":9,"fid":1,"attr":0,"title":"默认收藏夹"}}"""
        )

        val folder = FavoriteApi.parseFolderInfo(json)

        assertEquals("intro 缺失兜底空串（保存时不会塞 null）", "", folder.intro)
        assertEquals("media_count 缺失兜底 0", 0, folder.videoCount)
        assertEquals("attr=0 的 bit0=0 是公开", 0, folder.privacy)
        assertTrue("attr=0 是默认收藏夹", folder.isDefault)
    }

    @Test
    fun parseFolderInfo_codeNonZero_throws() {
        // 11010 = 内容不存在（bilibili-API/docs/fav/info.md 的 code 表）
        val json = JSONObject("""{"code":11010,"message":"内容不存在","data":null}""")

        try {
            FavoriteApi.parseFolderInfo(json)
            fail("code!=0 必须抛异常，调用方据此禁用保存，绝不能用默认值覆盖")
        } catch (e: JSONException) {
            assertTrue("异常信息应带 code 方便排查，实际：${e.message}", e.message!!.contains("11010"))
        }
    }

    @Test
    fun parseFolderInfo_dataNull_throws() {
        try {
            FavoriteApi.parseFolderInfo(JSONObject("""{"code":0,"message":"0","data":null}"""))
            fail("data=null 时应抛异常")
        } catch (e: JSONException) {
            assertTrue("异常信息应指明 data 为空，实际：${e.message}", e.message!!.contains("data"))
        }
    }

    /**
     * upper 只在 folder/info 里下发（bilibili-API/docs/fav/info.md:53-62），
     * 收藏夹详情页「分享至动态」用它的 name/mid 填卡片作者行。
     */
    @Test
    fun parseFolderInfo_upper_解析作者() {
        val json = JSONObject(
            """{"code":0,"message":"0","data":{
                "id":1052622027,"fid":10526220,"mid":7792521,
                "attr":54,"title":"猛 男 生 存","cover":"http://i0.hdslb.com/bfs/xxx.jpg",
                "media_count":28,
                "upper":{"mid":686127,"name":"籽岷","face":"http://i0.hdslb.com/bfs/face/xxx.jpg"}
            }}"""
        )

        val folder = FavoriteApi.parseFolderInfo(json)

        assertEquals("upper.name 是 UP 主昵称", "籽岷", folder.upperName)
        assertEquals("upper.mid 是 UP 主 mid", 686127L, folder.upperMid)
    }

    @Test
    fun parseFolderInfo_upperMissing_留空不抛() {
        // 他人收藏夹 / 旧接口可能没有 upper，分享面板要能照常打开，只是卡片少一行作者
        val json = JSONObject(
            """{"code":0,"message":"0","data":{"id":9,"fid":1,"attr":0,"title":"默认收藏夹"}}"""
        )

        val folder = FavoriteApi.parseFolderInfo(json)

        assertEquals("upper 缺失时昵称兜底空串", "", folder.upperName)
        assertEquals("upper 缺失时 mid 兜底 0", 0L, folder.upperMid)
    }

    /** bit0 语义与 isDefaultFolder 的 bit1 分开校验，防止有人把两个位混用 */
    @Test
    fun isPrivateFolder_checksAttrBit0() {
        assertFalse("attr=0 公开", FavoriteApi.isPrivateFolder(0))
        assertFalse("attr=54 公开", FavoriteApi.isPrivateFolder(54))
        assertTrue("attr=1 私密", FavoriteApi.isPrivateFolder(1))
        assertTrue("attr=23 私密", FavoriteApi.isPrivateFolder(23))
        assertTrue("attr=3 私密+非默认", FavoriteApi.isPrivateFolder(3))
    }
}
