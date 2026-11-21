package com.RobinNotBad.BiliClient.util.recommend

import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 「推荐源」判定的单测。
 *
 * 这里固化的是用户本轮明确要求的口径：**没有 APP 推荐流凭据时，无论设置选什么都降级网页源**——
 * 因为匿名调 APP 推荐流拿到的是与本账号无关的兜底内容，宁可不用。
 */
class RecommendSourcePolicyTest {

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    @Test
    fun decide_withoutCredential_alwaysFallsBackToWeb() {
        assertEquals(RecommendSource.WEB, RecommendSourcePolicy.decide(SharedPreferencesUtil.RECOMMEND_SOURCE_APP, false))
        assertEquals(RecommendSource.WEB, RecommendSourcePolicy.decide(SharedPreferencesUtil.RECOMMEND_SOURCE_BOTH, false))
        assertEquals(RecommendSource.WEB, RecommendSourcePolicy.decide(SharedPreferencesUtil.RECOMMEND_SOURCE_WEB, false))
        assertEquals(RecommendSource.WEB, RecommendSourcePolicy.decide(null, false))
    }

    @Test
    fun decide_withCredential_followsSetting() {
        assertEquals(RecommendSource.APP, RecommendSourcePolicy.decide(SharedPreferencesUtil.RECOMMEND_SOURCE_APP, true))
        assertEquals(RecommendSource.MIXED, RecommendSourcePolicy.decide(SharedPreferencesUtil.RECOMMEND_SOURCE_BOTH, true))
        assertEquals(RecommendSource.WEB, RecommendSourcePolicy.decide(SharedPreferencesUtil.RECOMMEND_SOURCE_WEB, true))
    }

    @Test
    fun decide_unknownValueFallsBackToWeb() {
        assertEquals(RecommendSource.WEB, RecommendSourcePolicy.decide("garbage", true))
        assertEquals(RecommendSource.WEB, RecommendSourcePolicy.decide("", true))
    }

    @Test
    fun configured_defaultsToWeb() {
        assertEquals(SharedPreferencesUtil.RECOMMEND_SOURCE_WEB, RecommendSourcePolicy.configured())
        SharedPreferencesUtil.putString(SharedPreferencesUtil.RECOMMEND_SOURCE, SharedPreferencesUtil.RECOMMEND_SOURCE_APP)
        assertEquals(SharedPreferencesUtil.RECOMMEND_SOURCE_APP, RecommendSourcePolicy.configured())
    }

    @Test
    fun resolve_readsSettingFromSharedPreferences() {
        SharedPreferencesUtil.putString(SharedPreferencesUtil.RECOMMEND_SOURCE, SharedPreferencesUtil.RECOMMEND_SOURCE_BOTH)
        assertEquals(RecommendSource.WEB, RecommendSourcePolicy.resolve(false))
        assertEquals(RecommendSource.MIXED, RecommendSourcePolicy.resolve(true))
    }
}
