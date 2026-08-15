package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.HotSearchCard
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotSearchApiTest {

    @Test
    fun parseHotSearch_validResponse_fillsList() {
        val json = """{"code":0,"data":{"trending":{"list":[
            {"keyword":"测试热词1","show_name":"测试热词1","icon":"","position":1,"heat_score":12345},
            {"keyword":"测试热词2","show_name":"测试热词2","icon":"https://i0.hdslb.com/x.jpg","position":2,"heat_score":678}
            ]}}}"""
        val list = mutableListOf<HotSearchCard>()
        val result = HotSearchApi.parseHotSearch(JSONObject(json), list)

        assertTrue("应解析成功", result)
        assertEquals("应解析出2条", 2, list.size)
        assertEquals("keyword", "测试热词1", list[0].keyword)
        assertEquals("showName", "测试热词2", list[1].showName)
        assertEquals("heatScore", 678L, list[1].heatScore)
        assertEquals("position", 2, list[1].position)
        assertTrue("应解析icon", list[1].icon.isNotEmpty())
    }

    @Test
    fun parseHotSearch_showNameMissing_fallsBackToKeyword() {
        val json = """{"code":0,"data":{"trending":{"list":[{"keyword":"无show_name"}]}}}"""
        val list = mutableListOf<HotSearchCard>()
        val result = HotSearchApi.parseHotSearch(JSONObject(json), list)

        assertTrue(result)
        assertEquals("应回退到keyword", "无show_name", list[0].showName)
    }

    @Test
    fun parseHotSearch_errorCode_returnsFalse() {
        val json = """{"code":-412,"message":"请求被拦截"}"""
        val list = mutableListOf<HotSearchCard>()
        assertFalse("错误码应返回false", HotSearchApi.parseHotSearch(JSONObject(json), list))
    }

    @Test
    fun parseHotSearch_nullInput_returnsFalse() {
        val list = mutableListOf<HotSearchCard>()
        assertFalse("null应返回false", HotSearchApi.parseHotSearch(null, list))
    }
}
