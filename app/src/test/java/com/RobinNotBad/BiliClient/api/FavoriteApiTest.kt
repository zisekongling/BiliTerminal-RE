package com.RobinNotBad.BiliClient.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteApiTest {

    @Test
    fun parseFavoriteState_validResponse_fillsAllLists() {
        val json = """{"count":2,"list":[
            {"id":44233921,"fid":442339,"title":"默认收藏夹","fav_state":1,"media_count":85},
            {"id":936347621,"fid":9363476,"title":"自建收藏夹","fav_state":0,"media_count":2}
        ]}"""
        val folderList = ArrayList<String>()
        val fidList = ArrayList<Long>()
        val stateList = ArrayList<Boolean>()
        val countList = ArrayList<Int>()
        val maxCountList = ArrayList<Int>()

        FavoriteApi.parseFavoriteState(JSONObject(json), folderList, fidList, stateList, countList, maxCountList)

        assertEquals("应解析出2条", 2, folderList.size)
        assertEquals("默认收藏夹", folderList[0])
        assertEquals("自建收藏夹", folderList[1])
        assertEquals("fid", 442339L, fidList[0])
        assertEquals("fid", 9363476L, fidList[1])
        assertTrue("fav_state=1 已收藏", stateList[0])
        assertFalse("fav_state=0 未收藏", stateList[1])
        assertEquals("media_count", 85, countList[0])
        assertEquals("默认收藏夹上限", 50000, maxCountList[0])
        assertEquals("自建收藏夹上限", 1000, maxCountList[1])
    }

    @Test
    fun parseFavoriteState_mediaCountMissing_defaultsZero() {
        val json = """{"list":[{"fid":1,"title":"a","fav_state":0}]}"""
        val folderList = ArrayList<String>()
        val fidList = ArrayList<Long>()
        val stateList = ArrayList<Boolean>()
        val countList = ArrayList<Int>()
        val maxCountList = ArrayList<Int>()

        FavoriteApi.parseFavoriteState(JSONObject(json), folderList, fidList, stateList, countList, maxCountList)

        assertEquals("media_count 缺失兜底0", 0, countList[0])
        assertEquals("index0 即默认收藏夹", 50000, maxCountList[0])
    }

    @Test
    fun parseFavoriteState_nullOrNoList_leavesListsEmpty() {
        val folderList = ArrayList<String>()
        val fidList = ArrayList<Long>()
        val stateList = ArrayList<Boolean>()
        val countList = ArrayList<Int>()
        val maxCountList = ArrayList<Int>()

        FavoriteApi.parseFavoriteState(null, folderList, fidList, stateList, countList, maxCountList)
        assertTrue("data=null 不崩溃", folderList.isEmpty())

        val noList = JSONObject("""{"count":0}""")
        FavoriteApi.parseFavoriteState(noList, folderList, fidList, stateList, countList, maxCountList)
        assertTrue("无list字段不崩溃", folderList.isEmpty())
    }
}
