package com.RobinNotBad.BiliClient.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 消息条目的 `business_id` 读取。
 *
 * 这个字段**不是每条都有**：有一类消息条目（解析实现不在公开仓库）根本不带它。
 * 三个消息流原来都用 `getInt`，遇到那种条目直接抛 JSONException，把整页消息带崩
 * （不是少一条，是整页空白），所以统一收敛到这个缺省 -1 的读法。
 */
class MessageApiBusinessIdTest {

    @Test
    fun parseBusinessId_readsValueWhenPresent() {
        assertEquals(1, MessageApi.parseBusinessId(JSONObject("""{"business_id":1}""")))
        assertEquals(11, MessageApi.parseBusinessId(JSONObject("""{"business_id":11}""")))
    }

    @Test
    fun parseBusinessId_missingFieldFallsBackToMinusOne() {
        assertEquals(-1, MessageApi.parseBusinessId(JSONObject("""{"type":"any"}""")))
    }

    @Test
    fun parseBusinessId_nullIsTolerated() {
        assertEquals(-1, MessageApi.parseBusinessId(null))
    }
}
