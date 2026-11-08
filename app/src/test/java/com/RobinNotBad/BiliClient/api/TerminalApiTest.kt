package com.RobinNotBad.BiliClient.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TerminalApi] 里所有**不碰 Android 运行时**的纯函数。
 *
 * 为什么值得写：这几段都直接决定发到自建服务器上的内容——
 * install_id 是否带上、分类是不是白名单里的值、公告 id 是否参与已读差量。
 * 一旦写错，App 编译、安装、界面全都正常，只有服务器侧的数据是脏的（或者干脆没数据），
 * 而那是从用户手机上删不掉的。
 */
class TerminalApiTest {

    // ---------- 分类白名单 ----------

    @Test
    fun normalizeCategory_keepsWhitelistedValues() {
        assertEquals("bug", TerminalApi.normalizeCategory("bug"))
        assertEquals("suggestion", TerminalApi.normalizeCategory("suggestion"))
        assertEquals("content", TerminalApi.normalizeCategory("content"))
        assertEquals("performance", TerminalApi.normalizeCategory("performance"))
        assertEquals("other", TerminalApi.normalizeCategory("other"))
    }

    @Test
    fun normalizeCategory_fallsBackToOther() {
        assertEquals("未知分类应归为 other", "other", TerminalApi.normalizeCategory("BUG"))
        assertEquals("空串应归为 other", "other", TerminalApi.normalizeCategory(""))
        assertEquals("null 应归为 other", "other", TerminalApi.normalizeCategory(null))
        assertEquals("任意脏数据应归为 other", "other", TerminalApi.normalizeCategory("drop table"))
    }

    // ---------- 遥测载荷 ----------

    @Test
    fun buildPingPayload_carriesInstallIdAndDevice() {
        val payload = TerminalApi.buildPingPayload(
            "abc123", 2610090, "26.10.09", false, 34, "Xiaomi", "diting", "arm64-v8a",
            "devkey123", false, "stable"
        )
        assertEquals("abc123", payload.getString("install_id"))
        assertEquals(2610090, payload.getInt("version_code"))
        assertEquals("26.10.09", payload.getString("version_name"))
        assertFalse("非 beta 应为 false", payload.getBoolean("is_beta"))
        assertEquals(34, payload.getInt("sdk"))
        assertEquals("Xiaomi", payload.getString("brand"))
        assertEquals("diting", payload.getString("device"))
        assertEquals("arm64-v8a", payload.getString("abi"))
        assertEquals("设备键必须带上（同机两版去重靠它）", "devkey123", payload.getString("device_key"))
        assertFalse("普通版 is_coexist 应为 false", payload.getBoolean("is_coexist"))
        assertEquals("通道必须原样上报（后台按它分三通道统计）", "stable", payload.getString("channel"))
    }

    @Test
    fun buildPingPayload_nullStringsBecomeEmpty() {
        val payload = TerminalApi.buildPingPayload(null, 0, null, true, 0, null, null, null, null, true, null)
        assertEquals("install_id 为 null 时应落成空串", "", payload.getString("install_id"))
        assertEquals("", payload.getString("version_name"))
        assertEquals("", payload.getString("brand"))
        assertEquals("", payload.getString("device"))
        assertEquals("", payload.getString("abi"))
        assertEquals("device_key 为 null 时应落成空串而不是 null", "", payload.getString("device_key"))
        assertTrue("beta 应为 true", payload.getBoolean("is_beta"))
        assertTrue("共存版 is_coexist 应为 true", payload.getBoolean("is_coexist"))
        assertEquals("channel 为 null 时应落成空串（服务端按「没报就保留」处理）", "", payload.getString("channel"))
    }

    // ---------- 反馈载荷 ----------

    @Test
    fun buildFeedbackPayload_normalizesCategoryAndDefaults() {
        val payload = TerminalApi.buildFeedbackPayload(
            "abc123", "devkey123", "BUG", "播放器闪退", null, null, 0, 2610090, "26.10.09", 34, "Xiaomi", "diting"
        )
        assertEquals("非法分类应被收敛", "other", payload.getString("category"))
        assertEquals("content 不可以被裁剪或改写", "播放器闪退", payload.getString("content"))
        assertEquals("未勾选附带日志时 log 应为空串", "", payload.getString("log"))
        assertEquals("未填联系方式应为空串", "", payload.getString("contact"))
        assertEquals("未勾选附带账号时 mid 应为 0", 0L, payload.getLong("mid"))
        assertEquals("devkey123", payload.getString("device_key"))
    }

    @Test
    fun buildFeedbackPayload_keepsSelectedMid() {
        val payload = TerminalApi.buildFeedbackPayload(
            "abc123", "devkey123", "bug", "内容", null, "qq:123", 123456L, 1, "v", 34, "b", "d"
        )
        assertEquals(123456L, payload.getLong("mid"))
        assertEquals("qq:123", payload.getString("contact"))
    }

    /**
     * 勾选「附带应用日志」时，快照原样进 `log` 字段；未勾选（null / 空串）时必须是**空串**
     * 而不是 JSON `null` —— 服务端按字符串落库，`null` 会污染数据。
     */
    @Test
    fun buildFeedbackPayload_logFallsBackToEmptyString() {
        val attached = TerminalApi.buildFeedbackPayload(
            "abc123", "devkey123", "bug", "内容", "12:00:00 E A>b | 崩了", "", 0, 1, "v", 34, "b", "d"
        )
        assertEquals("日志快照应原样带上", "12:00:00 E A>b | 崩了", attached.getString("log"))

        for (blank in listOf<String?>(null, "")) {
            val payload = TerminalApi.buildFeedbackPayload(
                "abc123", "devkey123", "bug", "内容", blank, "", 0, 1, "v", 34, "b", "d"
            )
            assertFalse("log 不该是 JSON null", payload.isNull("log"))
            assertEquals("log 为空时应落成空串", "", payload.getString("log"))
            assertFalse("序列化文本里不该出现 \"log\":null", payload.toString().contains("\"log\":null"))
        }
    }

    /**
     * 追加回复的载荷（26.10.16）：**只有 id 与 content 两个字段**。
     *
     * 身份全部走请求头，body 里不该再出现 install_id / mid —— 多一份就多一处可能不一致，
     * 也多一条把身份写进请求体的路径。
     */
    @Test
    fun buildFeedbackReplyPayload_hasOnlyIdAndContent() {
        val payload = TerminalApi.buildFeedbackReplyPayload(42, "补充：点下一集才复现")

        assertEquals(42, payload.getInt("id"))
        assertEquals("补充：点下一集才复现", payload.getString("content"))
        assertEquals("载荷应当只有两个字段", 2, payload.length())
        for (forbidden in listOf("install_id", "mid", "device_key", "log", "category")) {
            assertFalse("追加回复的载荷里不该有 $forbidden", payload.has(forbidden))
        }
    }

    /** 内容为 null / 空串时落成**空串**而不是 JSON null（服务端按字符串落库）。 */
    @Test
    fun buildFeedbackReplyPayload_contentFallsBackToEmptyString() {
        for (blank in listOf<String?>(null, "")) {
            val payload = TerminalApi.buildFeedbackReplyPayload(1, blank)
            assertFalse("content 不该是 JSON null", payload.isNull("content"))
            assertEquals("", payload.getString("content"))
            assertFalse(
                "序列化文本里不该出现 \"content\":null",
                payload.toString().contains("\"content\":null")
            )
        }
    }

    // ---------- 崩溃载荷 ----------

    @Test
    fun buildCrashPayload_hasRicherFieldsThanUpstream() {
        val payload = TerminalApi.buildCrashPayload(
            /* installId    = */ "abc123",
            /* deviceKey    = */ "devkey123",
            /* mid          = */ 0L,
            /* exception    = */ "java.lang.NullPointerException",
            /* message      = */ "Attempt to invoke virtual method",
            /* thread       = */ "main",
            /* stack        = */ "at com.example.A.b(A.java:1)",
            /* log          = */ "最近页面：视频详情 -> 播放器",
            /* extra        = */ "{\"page\":\"player\"}",
            /* versionCode  = */ 2610090,
            /* versionName  = */ "26.10.09",
            /* sdk          = */ 34,
            /* release      = */ "14",
            /* brand        = */ "Xiaomi",
            /* device       = */ "diting",
            /* product      = */ "diting",
            /* model        = */ "Redmi Note 12T Pro",
            /* abi          = */ "arm64-v8a",
            /* ramLevel     = */ TerminalApi.RAM_LEVEL_MID,
            /* uptimeSec    = */ 3600L
        )
        assertEquals("abc123", payload.getString("install_id"))
        assertEquals("devkey123", payload.getString("device_key"))
        assertEquals("java.lang.NullPointerException", payload.getString("exception"))
        assertEquals("Attempt to invoke virtual method", payload.getString("message"))
        assertEquals("出错线程必须带上", "main", payload.getString("thread"))
        assertTrue("堆栈必须原样带上", payload.getString("stack").contains("com.example.A.b"))
        assertEquals("最近页面：视频详情 -> 播放器", payload.getString("log"))
        assertEquals("内存只报档位", TerminalApi.RAM_LEVEL_MID, payload.getInt("ram_level"))
        assertEquals("精确内存不再采集", 0L, payload.getLong("ram_total"))
        assertEquals("精确内存不再采集", 0L, payload.getLong("ram_avail"))
        assertEquals(3600L, payload.getLong("uptime_sec"))
        assertEquals("Redmi Note 12T Pro", payload.getString("model"))
        assertEquals("14", payload.getString("release"))
    }

    @Test
    fun ramLevel_bucketsByWholeGiB() {
        val gb = 1024L * 1024L * 1024L
        assertEquals("取不到内存应为未知档", TerminalApi.RAM_LEVEL_UNKNOWN, TerminalApi.ramLevel(0))
        assertEquals("负数应为未知档", TerminalApi.RAM_LEVEL_UNKNOWN, TerminalApi.ramLevel(-1))
        assertEquals("1 GiB 属低档", TerminalApi.RAM_LEVEL_LOW, TerminalApi.ramLevel(gb))
        assertEquals("2 GiB 属中档", TerminalApi.RAM_LEVEL_MID, TerminalApi.ramLevel(2 * gb))
        assertEquals("4 GiB 属高档", TerminalApi.RAM_LEVEL_HIGH, TerminalApi.ramLevel(4 * gb))
        assertEquals("8 GiB 属高档", TerminalApi.RAM_LEVEL_HIGH, TerminalApi.ramLevel(8 * gb))
        assertEquals("不足 2 GiB 仍属低档", TerminalApi.RAM_LEVEL_LOW, TerminalApi.ramLevel((1.8 * gb).toLong()))
        assertEquals("不足 4 GiB 仍属中档", TerminalApi.RAM_LEVEL_MID, TerminalApi.ramLevel((3.9 * gb).toLong()))
    }

    // ---------- 公告解析 ----------

    @Test
    fun parseAnnouncements_parsesSecondsAndSkipsIdlessItems() {
        val json = """
            [
              {"id":1000000002,"ctime":1700000000,"title":"第二条","content":"内容二","source":"relay"},
              {"id":1000000001,"ctime":1690000000,"title":"第一条","content":"内容一"},
              {"ctime":1600000000,"title":"没有 id","content":"应被跳过"}
            ]
        """
        val list = TerminalApi.parseAnnouncements(JSONArray(json))
        assertEquals("缺 id 的条目应被跳过", 2, list.size)
        assertEquals(1000000002, list[0].id)
        assertEquals("第二条", list[0].title)
        assertEquals("内容一", list[1].content)
        assertTrue("source=relay 应识别为透传公告", list[0].isRelayed())
        assertFalse("缺省 source 应按本项目公告处理", list[1].isRelayed())
        assertFalse("ctime 秒应被换算成可读日期", list[0].ctime.isEmpty())
        assertTrue("应格式化为 yyyy-MM-dd", list[0].ctime.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        // 秒当毫秒用会得到 1970 年，只断形状抓不到这个错，必须断年份区间：
        // 1700000000 秒 = 2023-11，若漏掉 *1000 则变成 1970-01。
        val ctimeYear = list[0].ctime.substring(0, 4).toInt()
        assertTrue("ctime 应落在合理年份（漏乘 1000 会变成 1970），实际 ${list[0].ctime}", ctimeYear in 2000..2100)
    }

    @Test
    fun parseAnnouncements_nullArrayYieldsEmptyList() {
        assertTrue(TerminalApi.parseAnnouncements(null).isEmpty())
        assertTrue(TerminalApi.parseAnnouncements(JSONArray()).isEmpty())
    }

    // ---------- 日期键 ----------

    @Test
    fun dayKey_isIsoDate() {
        assertTrue(TerminalApi.dayKey(1700000000000L).matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun dayKey_changesAcrossLocalDays() {
        // 同一本地日的两个时刻（相差 17 分钟）必须相同；跨一天必须不同。
        // 只断 yyyy-MM-dd 形状的话，实现返回常量也能通过。
        val base = 1700000000000L
        assertEquals(TerminalApi.dayKey(base), TerminalApi.dayKey(base + 17 * 60 * 1000L))
        assertNotEquals(TerminalApi.dayKey(base), TerminalApi.dayKey(base + 24 * 60 * 60 * 1000L))
    }

    // ---------- 安全：载荷里不得出现账号凭据 ----------

    @Test
    fun feedbackPayload_containsNoCookieOrToken() {
        val payload = TerminalApi.buildFeedbackPayload(
            "abc123", "devkey123", "bug", "内容", null, "", 0, 1, "v", 34, "b", "d"
        )
        val text = payload.toString().lowercase()
        for (forbidden in listOf("sessdata", "bili_jct", "dedeuserid", "cookie", "csrf")) {
            assertFalse("载荷里不应出现 $forbidden", text.contains(forbidden))
        }
    }

    /**
     * 设备键必须是**哈希**而不是原始 ANDROID_ID。
     *
     * 这里直接钉住实现契约：载荷里只出现 sha256 的前 32 位十六进制，
     * 既不是 16 位十六进制的裸 ANDROID_ID，也不长于 32 位。
     */
    @Test
    fun deviceKeyShape_looksLikeTruncatedSha256() {
        val raw = "9774d56d682e549c"          // 典型的裸 ANDROID_ID 形状
        val key = TerminalApi.hashDeviceKey(raw)
        assertEquals("应为 sha256 截断后的 32 位", 32, key.length)
        assertTrue("应为小写十六进制", key.matches(Regex("[0-9a-f]{32}")))
        assertFalse("绝不能是原始 ANDROID_ID", key.contains(raw))
        assertEquals("同输入必须稳定复现", key, TerminalApi.hashDeviceKey(raw))
        assertTrue("不同输入应得到不同键",
            key != TerminalApi.hashDeviceKey("9774d56d682e549d"))
    }

    /** 拿不到 ANDROID_ID 时必须落成空串，服务端才会回落到 install_id 计数。 */
    @Test
    fun deviceKey_blankInputYieldsEmpty() {
        assertEquals("", TerminalApi.hashDeviceKey(null))
        assertEquals("", TerminalApi.hashDeviceKey(""))
    }
}
