package com.RobinNotBad.BiliClient.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [DurationText] 的单测：覆盖四种接口口径（数值秒 / `"MM:SS"` / `duration_text` / `length`）
 * 与各种残缺输入。
 */
class DurationTextTest {

    @Test
    fun secondsOf_readsNumericSeconds() {
        // 热门 / 排行 / 推荐这一批：duration 是数值秒
        assertEquals(754L, DurationText.secondsOf(JSONObject("""{"duration":754}""")))
    }

    @Test
    fun secondsOf_readsSearchStyleClock() {
        // 搜索：duration 是字符串，且不保证补零
        assertEquals(754L, DurationText.secondsOf(JSONObject("""{"duration":"12:34"}""")))
        assertEquals(242L, DurationText.secondsOf(JSONObject("""{"duration":"4:2"}""")))
        assertEquals(13027L, DurationText.secondsOf(JSONObject("""{"duration":"217:7"}""")))
    }

    @Test
    fun secondsOf_readsDurationTextAndLength() {
        assertEquals(314L, DurationText.secondsOf(JSONObject("""{"duration_text":"05:14"}""")))
        assertEquals(314L, DurationText.secondsOf(JSONObject("""{"length":"05:14"}""")))
    }

    @Test
    fun secondsOf_prefersDurationThenFallsBack() {
        // duration 是 0 或缺失时继续往后找，不能让一个占位 0 把后面的真值挡掉
        assertEquals(314L, DurationText.secondsOf(JSONObject("""{"duration":0,"duration_text":"05:14"}""")))
        assertEquals(314L, DurationText.secondsOf(JSONObject("""{"length":"05:14"}""")))
        assertEquals(0L, DurationText.secondsOf(JSONObject("""{"title":"x"}""")))
    }

    @Test
    fun secondsOf_toleratesMissingAndNull() {
        assertEquals(0L, DurationText.secondsOf(null))
        assertEquals(0L, DurationText.secondsOf(JSONObject("""{"duration":null}""")))
        assertEquals(0L, DurationText.secondsOf(JSONObject("""{"duration":""}""")))
        // 消息卡片那种只有 uri / title / image 的响应
        assertEquals(0L, DurationText.secondsOf(JSONObject("""{"uri":"BV1xx","title":"t","image":"c"}""")))
    }

    @Test
    fun parse_handlesClockAndPlainNumbers() {
        assertEquals(754L, DurationText.parse("12:34"))
        assertEquals(3723L, DurationText.parse("1:02:03"))
        assertEquals(45L, DurationText.parse(" 45 "))
        assertEquals(0L, DurationText.parse(""))
        assertEquals(0L, DurationText.parse(null))
        assertEquals(0L, DurationText.parse("abc"))
        assertEquals(0L, DurationText.parse("1:-2"))
    }

    @Test
    fun format_usesMinutesBelowOneHour() {
        assertEquals("12:34", DurationText.format(754L))
        assertEquals("00:09", DurationText.format(9L))
        assertEquals("", DurationText.format(0L))
        assertEquals("", DurationText.format(-5L))
    }

    @Test
    fun format_usesCompactHoursFromOneHour() {
        assertEquals("1:02:03", DurationText.format(3723L))
        // 正好一小时：不能退化成 "60:00"
        assertEquals("1:00:00", DurationText.format(3600L))
    }
}
