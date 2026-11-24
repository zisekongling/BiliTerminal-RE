package com.RobinNotBad.BiliClient.api

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 私信会话列表解析测试。
 *
 * 重点覆盖 account_info 过滤：该字段仅在系统会话中出现，
 * 旧实现 `!has && isNull` 恒等于 `!has`，会把 account_info 显式为 null 的普通会话一起丢弃。
 */
class PrivateMsgApiTest {

    private fun sessionJson(
        talkerId: Long,
        unread: Int = 0,
        accountInfo: String = "",
        lastMsg: String = "",
        topTs: Long = -1,
        sessionTs: Long = -1
    ): String = buildString {
        append("""{"talker_id":$talkerId,"unread_count":$unread""")
        if (accountInfo.isNotEmpty()) append(""","account_info":$accountInfo""")
        if (lastMsg.isNotEmpty()) append(""","last_msg":$lastMsg""")
        if (topTs >= 0) append(""","top_ts":$topTs""")
        if (sessionTs >= 0) append(""","session_ts":$sessionTs""")
        append("}")
    }

    private fun rootWith(vararg sessions: String): JSONObject =
        JSONObject("""{"code":0,"data":{"session_list":[${sessions.joinToString(",")}]}}""")

    @Test
    fun parseSessionsList_普通会话_全部保留() {
        val root = rootWith(
            sessionJson(1001L, unread = 2),
            sessionJson(1002L, unread = 0)
        )

        val list = PrivateMsgApi.parseSessionsList(root)

        assertEquals("两个普通会话都应保留", 2, list.size)
        assertEquals(1001L, list[0].talkerUid)
        assertEquals(2, list[0].unread)
    }

    @Test
    fun parseSessionsList_accountInfo为null_应保留() {
        // 回归用例：字段存在但值为 null 的是普通会话，不能被过滤掉
        val root = rootWith(
            sessionJson(2001L, accountInfo = "null"),
            sessionJson(2002L)
        )

        val list = PrivateMsgApi.parseSessionsList(root)

        assertEquals("account_info 为 null 的会话应保留", 2, list.size)
    }

    @Test
    fun parseSessionsList_系统会话_保留并解析账号名与通知未读() {
        // 26.11.20 起系统会话不再过滤：它们的未读（biz_msg_unread_count）本来就算进主菜单角标，
        // 而会话列表里又没有它们，用户没有任何入口能把未读读掉——角标只涨不退。
        val root = JSONObject(
            """{"code":0,"data":{"session_list":[
                {"talker_id":844424930131966,"unread_count":0,"biz_msg_unread_count":3,
                 "system_msg_type":7,
                 "account_info":{"name":"UP主小助手","pic_url":"https://message.biliimg.com/bfs/im/x.png"}},
                {"talker_id":3002}
            ]}}"""
        )

        val list = PrivateMsgApi.parseSessionsList(root)

        assertEquals("系统会话要保留，普通会话照旧", 2, list.size)
        assertTrue(list[0].isSystem)
        assertEquals(7, list[0].systemMsgType)
        assertEquals("UP主小助手", list[0].accountName)
        assertEquals("https://message.biliimg.com/bfs/im/x.png", list[0].accountAvatar)
        assertEquals("系统会话的未读在 biz_msg_unread_count 里", 3, list[0].unreadTotal())
        assertTrue("普通会话不是系统会话", !list[1].isSystem)
        assertEquals(0, list[1].unreadTotal())
    }

    @Test
    fun parseSessionsList_只有account_info没有systemMsgType_也算系统会话() {
        // 老版本会话可能只带 account_info，没有 system_msg_type 字段，同样要按系统会话处理
        val root = rootWith(sessionJson(3001L, accountInfo = """{"name":"系统消息"}"""))

        val list = PrivateMsgApi.parseSessionsList(root)

        assertEquals(1, list.size)
        assertTrue(list[0].isSystem)
        assertEquals("系统消息", list[0].accountName)
    }

    @Test
    fun unreadTotal_私信未读与通知未读相加() {
        val root = rootWith(
            JSONObject("""{"talker_id":3001,"unread_count":2,"biz_msg_unread_count":3}""").toString()
        )

        // 两种未读都可能同时非零（系统会话既可以直接私信、也可以有通知），角标要把它们加起来
        assertEquals(5, PrivateMsgApi.parseSessionsList(root)[0].unreadTotal())
    }

    @Test
    fun parseSessionsList_解析lastMsg与未读数() {
        val root = rootWith(
            sessionJson(4001L, unread = 5, lastMsg = """{"msg_type":1,"content":"你好"}"""),
            sessionJson(4002L, unread = 0, lastMsg = """{"msg_type":7,"content":"{\"text\":\"卡片\"}"}""")
        )

        val list = PrivateMsgApi.parseSessionsList(root)

        assertEquals(1, list[0].contentType)
        assertEquals(5, list[0].unread)
        assertNull("纯文本内容不应解析成 JSONObject", list[0].content)
        assertEquals(7, list[1].contentType)
        assertEquals("卡片", list[1].content?.optString("text"))
    }

    @Test
    fun parseSessionsList_data为null_返回空列表() {
        val root = JSONObject("""{"code":0,"data":null}""")

        assertTrue("data 为 null 时应返回空列表", PrivateMsgApi.parseSessionsList(root).isEmpty())
    }

    @Test
    fun parseSessionsList_null输入_返回空列表() {
        assertTrue("null 输入应返回空列表", PrivateMsgApi.parseSessionsList(null).isEmpty())
    }

    @Test
    fun parseSessionsList_解析置顶时间() {
        val root = rootWith(
            sessionJson(5001L, topTs = 1780000000000000L),
            sessionJson(5002L, topTs = 0L),
            sessionJson(5003L)
        )

        val list = PrivateMsgApi.parseSessionsList(root)

        assertEquals(1780000000000000L, list[0].topTs)
        assertTrue("top_ts 非零 = 已置顶", list[0].isTop)
        assertEquals(0L, list[1].topTs)
        assertTrue("top_ts 为 0 = 未置顶", !list[1].isTop)
        assertEquals("缺字段按未置顶处理", 0L, list[2].topTs)
        assertTrue(!list[2].isTop)
    }

    @Test
    fun opTypeForTop_0是置顶_1是取消置顶() {
        // 接口的 op_type 与直觉相反，写反的后果是"点置顶实际取消置顶"，不报错、极难发现
        assertEquals("置顶必须传 0", 0, PrivateMsgApi.opTypeForTop(true))
        assertEquals("取消置顶必须传 1", 1, PrivateMsgApi.opTypeForTop(false))
    }

    @Test
    fun sessionErrorMsg_成功为空串_其余给出可读提示() {
        assertEquals("", PrivateMsgApi.sessionErrorMsg(0))
        assertTrue(PrivateMsgApi.sessionErrorMsg(-101).contains("登录"))
        assertTrue(PrivateMsgApi.sessionErrorMsg(-400).contains("请求错误"))
        assertTrue(PrivateMsgApi.sessionErrorMsg(12345).contains("12345"))
    }

    // ---------- C12 图片消息 ----------

    @Test
    fun sizeToKb_按千字节保留三位小数() {
        assertEquals(0.0, PrivateMsgApi.sizeToKb(0L), 0.0)
        assertEquals("负数按 0 处理，不能发出 NaN/负数", 0.0, PrivateMsgApi.sizeToKb(-1L), 0.0)
        assertEquals(1.0, PrivateMsgApi.sizeToKb(1024L), 0.0)
        // 接口文档示例值就是 55.443 这种三位小数
        assertEquals(55.443, PrivateMsgApi.sizeToKb(56774L), 0.001)
        // 小于 1KB 也要保留小数，不能被截成 0
        assertEquals(0.5, PrivateMsgApi.sizeToKb(512L), 0.0)
    }

    @Test
    fun imageTypeOf_去掉mime前缀并把jpg归一成jpeg() {
        assertEquals("jpeg", PrivateMsgApi.imageTypeOf("image/jpeg"))
        assertEquals("jpeg", PrivateMsgApi.imageTypeOf("image/jpg"))
        assertEquals("png", PrivateMsgApi.imageTypeOf("image/png"))
        assertEquals("gif", PrivateMsgApi.imageTypeOf("image/gif"))
        assertEquals("大小写不敏感，统一小写输出", "webp", PrivateMsgApi.imageTypeOf("IMAGE/WEBP"))
    }

    @Test
    fun imageTypeOf_空值兜底为jpeg() {
        assertEquals("jpeg", PrivateMsgApi.imageTypeOf(null))
        assertEquals("jpeg", PrivateMsgApi.imageTypeOf(""))
    }

    @Test
    fun buildImageContent_字段齐全且是图片消息规格() {
        val content = PrivateMsgApi.buildImageContent(
            "https://message.biliimg.com/bfs/im_new/xxx.jpg", 300, 400, 56774L, "jpeg"
        )

        assertEquals("https://message.biliimg.com/bfs/im_new/xxx.jpg", content.getString("url"))
        assertEquals(300, content.getInt("width"))
        assertEquals(400, content.getInt("height"))
        assertEquals("jpeg", content.getString("imageType"))
        assertEquals("1 表示 APP 显示「下载原图」", 1, content.getInt("original"))
        assertEquals(55.443, content.getDouble("size"), 0.001)
    }

    @Test
    fun buildImageContentOfKb_不再次除以1024() {
        // 回归用例：图床上传返回的 img_size 已经是千字节（官方示例 "img_size": 6.261，
        // bilibili-API/docs/dynamic/publish.md:40,66），直接交给接口即可。
        // 历史上调用方把它当字节数传给 buildImageContent，被 sizeToKb 再除一次 1024，
        // 发出去的 size 只有真实值的 1/1024。
        val content = PrivateMsgApi.buildImageContentOfKb(
            "https://message.biliimg.com/bfs/im_new/xxx.jpg", 300, 400, 6.261, "jpeg"
        )

        assertEquals("KB 进就要 KB 出，不能被再除一次 1024", 6.261, content.getDouble("size"), 0.000001)
    }

    @Test
    fun buildImageContentOfKb_与字节版对同一个值给出一致结果() {
        // 同一个大小，两条入口必须等价：56774 字节 == 55.443KB
        val byKb = PrivateMsgApi.buildImageContentOfKb("u", 1, 1, 55.443, "jpeg")
        val byByte = PrivateMsgApi.buildImageContent("u", 1, 1, 56774L, "jpeg")

        assertEquals(byByte.getDouble("size"), byKb.getDouble("size"), 0.001)
    }

    @Test
    fun buildImageContentOfKb_非正数按0处理() {
        // 不能让 0 / 负数变成 NaN 或负的 size 发出去
        assertEquals(0.0, PrivateMsgApi.buildImageContentOfKb("u", 1, 1, 0.0, "jpeg").getDouble("size"), 0.0)
        assertEquals(0.0, PrivateMsgApi.buildImageContentOfKb("u", 1, 1, -5.0, "jpeg").getDouble("size"), 0.0)
    }

    // ---------- 视频转发到私信（反馈 #133）：消息体组装与降级 ----------

    @Test
    fun buildVideoShareText_标题加完整链接() {
        assertEquals(
            "测试视频\nhttps://www.bilibili.com/video/BV1xx411c7mD",
            PrivateMsgApi.buildVideoShareText("测试视频", "BV1xx411c7mD")
        )
    }

    @Test
    fun buildVideoShareText_超长标题截断到60字加省略号() {
        // 私信正文有 2000 字节上限，长标题必须截断，否则整条发不出去
        val firstLine = PrivateMsgApi.buildVideoShareText("视".repeat(61), "BV1xx411c7mD").substringBefore("\n")
        assertEquals("视".repeat(PrivateMsgApi.VIDEO_SHARE_TITLE_MAX) + "…", firstLine)
    }

    @Test
    fun buildVideoShareText_正好60字不截断() {
        val title = "字".repeat(PrivateMsgApi.VIDEO_SHARE_TITLE_MAX)
        assertEquals(title, PrivateMsgApi.buildVideoShareText(title, ""))
    }

    @Test
    fun buildVideoShareText_没有bvid时只发标题() {
        // 拿不到 bvid 时不能拼出半截链接（.../video/）
        assertEquals("测试视频", PrivateMsgApi.buildVideoShareText("测试视频", ""))
        assertEquals("测试视频", PrivateMsgApi.buildVideoShareText("测试视频", null))
    }

    @Test
    fun buildVideoShareText_没有标题时只发链接() {
        assertEquals(
            "https://www.bilibili.com/video/BV1xx411c7mD",
            PrivateMsgApi.buildVideoShareText("   ", "BV1xx411c7mD")
        )
    }

    @Test
    fun buildVideoLink_没有bvid就返回空串() {
        // 发送失败时的兜底是「复制链接」：没有 bvid 就没得复制，不能复制出 .../video/null
        assertEquals("", PrivateMsgApi.buildVideoLink(null))
        assertEquals("", PrivateMsgApi.buildVideoLink(""))
        assertEquals(
            "https://www.bilibili.com/video/BV1xx411c7mD",
            PrivateMsgApi.buildVideoLink("BV1xx411c7mD")
        )
    }

    @Test
    fun buildTextContent_引号与换行被转义后仍可解析回原文() {
        val raw = "标题\"带引号\"\n第二行"
        assertEquals(raw, JSONObject(PrivateMsgApi.buildTextContent(raw)).getString("content"))
    }

    @Test
    fun buildTextContent_null按空串发出() {
        assertEquals("", JSONObject(PrivateMsgApi.buildTextContent(null)).getString("content"))
    }

    // ==================== 会话翻页（反馈 #166） ====================

    @Test
    fun parseSessionsList_解析sessionTs作为翻页游标() {
        val list = PrivateMsgApi.parseSessionsList(
            rootWith(sessionJson(1001L, sessionTs = 1712305278098351L))
        )

        assertEquals(1712305278098351L, list[0].sessionTs)
    }

    @Test
    fun parseSessionsList_缺sessionTs时游标为0() {
        // 拿不到时间戳就没法翻页，调用方据此停止加载（不能拿 0 反复拉同一页）
        assertEquals(0L, PrivateMsgApi.parseSessionsList(rootWith(sessionJson(1001L)))[0].sessionTs)
    }

    @Test
    fun parseSessionsList_sessionList为null_返回空列表() {
        // 翻到最后一页时服务端会把 session_list 直接给 null，getJSONArray 会抛
        val root = JSONObject("""{"code":0,"data":{"session_list":null,"has_more":0}}""")

        assertTrue(PrivateMsgApi.parseSessionsList(root).isEmpty())
    }

    @Test
    fun parseSessionsHasMore_读data里的has_more() {
        assertTrue(PrivateMsgApi.parseSessionsHasMore(JSONObject("""{"data":{"has_more":1}}""")))
        assertEquals(false, PrivateMsgApi.parseSessionsHasMore(JSONObject("""{"data":{"has_more":0}}""")))
        // 字段缺失按「没有更多」处理，避免无休止地翻
        assertEquals(false, PrivateMsgApi.parseSessionsHasMore(JSONObject("""{"data":{}}""")))
    }

    /** max_seqno 是字符串型，update_ack 要拿它当 ack_seqno（#281）。 */
    @Test
    fun parseSession_解析字符串max_seqno() {
        val session = PrivateMsgApi.parseSession(
            JSONObject("""{"talker_id":1001,"max_seqno":"1700000000123456"}""")
        )
        assertEquals(1700000000123456L, session.maxSeqno)
    }

    @Test
    fun parseSession_缺max_seqno按0() {
        assertEquals(0L, PrivateMsgApi.parseSession(JSONObject("""{"talker_id":1001}""")).maxSeqno)
        assertEquals(0L, PrivateMsgApi.parseSession(JSONObject("""{"talker_id":1001,"max_seqno":""}""")).maxSeqno)
    }

    @Test
    fun buildSessionsUrl_第一页不带end_ts_后续页带上游标() {
        val first = PrivateMsgApi.buildSessionsUrl(20, 0)
        assertTrue(first.contains("session_type=1"))
        assertTrue(first.contains("size=20"))
        assertEquals("第一页不该带翻页游标", false, first.contains("end_ts"))

        val next = PrivateMsgApi.buildSessionsUrl(20, 1712305278098351L)
        assertTrue(next.contains("size=20"))
        assertTrue(next.contains("end_ts=1712305278098351"))
    }

    /** 跑一段应当抛 JSONException 的逻辑，返回异常 message（没抛就是空串）。 */
    private fun messageOf(block: () -> Unit): String = try {
        block()
        ""
    } catch (e: JSONException) {
        e.message ?: ""
    }

    @Test
    fun requireOk_业务码为0时放行() {
        PrivateMsgApi.requireOk(JSONObject("""{"code":0,"data":{"session_list":[]}}"""))
        // 老响应可能压根没有 code 字段，别把它当失败
        PrivateMsgApi.requireOk(JSONObject("""{"data":{"session_list":[]}}"""))
    }

    @Test
    fun requireOk_掉登录时给出带code的登录提示() {
        val message = messageOf { PrivateMsgApi.requireOk(JSONObject("""{"code":-101,"message":"账号未登录"}""")) }
        assertTrue(message.contains("-101"))
        assertTrue(message.contains("登录"))
    }

    @Test
    fun requireOk_风控与频繁请求各自给出可操作提示() {
        assertTrue(
            "code=-352 要能看出是被风控拦了",
            messageOf { PrivateMsgApi.requireOk(JSONObject("""{"code":-352,"message":"风控校验失败"}""")) }.contains("风控")
        )
        assertTrue(
            "code=-412 要能看出是太频繁",
            messageOf { PrivateMsgApi.requireOk(JSONObject("""{"code":-412,"message":"请求过于频繁"}""")) }.contains("频繁")
        )
    }

    @Test
    fun requireOk_未知业务码保留code与服务端message() {
        val message = messageOf { PrivateMsgApi.requireOk(JSONObject("""{"code":-500,"message":"服务器错误"}""")) }
        assertTrue(message.contains("-500"))
        assertTrue(message.contains("服务器错误"))
    }

    @Test
    fun requireOk_空响应也抛而不是静默当成空列表() {
        assertTrue(messageOf { PrivateMsgApi.requireOk(null) }.isNotEmpty())
    }

    // ==================== 会话免打扰（26.11.23 批次①） ====================

    @Test
    fun settingForDnd_开是1关是0() {
        // 官方口径（private_msg.md:1210）：setting 0 取消免打扰、1 开启免打扰。
        // 写反的后果和置顶 op_type 一样：点「免打扰」实际是取消，不报错、极难发现。
        assertEquals("开启免打扰必须传 1", 1, PrivateMsgApi.settingForDnd(true))
        assertEquals("取消免打扰必须传 0", 0, PrivateMsgApi.settingForDnd(false))
        assertEquals(PrivateMsgApi.DND_ON, PrivateMsgApi.settingForDnd(true))
        assertEquals(PrivateMsgApi.DND_OFF, PrivateMsgApi.settingForDnd(false))
    }

    @Test
    fun parseDndCode_正常响应取code() {
        assertEquals(0, PrivateMsgApi.parseDndCode("""{"code":0,"message":"0","ttl":1}"""))
        assertEquals(-101, PrivateMsgApi.parseDndCode("""{"code":-101,"message":"账号未登录"}"""))
    }

    @Test
    fun parseDndCode_缺code按失败处理() {
        // 没有 code 说明不是预期的 JSON 结构，不能当成功
        assertEquals(-1, PrivateMsgApi.parseDndCode("""{"message":"0"}"""))
    }

    @Test
    fun parseDndCode_非JSON与空值给负一() {
        // 412 风控时 body 是 HTML，解析不出来要给 -1 让调用方翻成能看懂的话
        assertEquals(-1, PrivateMsgApi.parseDndCode("<html><body>412</body></html>"))
        assertEquals(-1, PrivateMsgApi.parseDndCode(""))
        assertEquals(-1, PrivateMsgApi.parseDndCode(null))
    }

    @Test
    fun parseSession_解析is_dnd() {
        assertTrue(
            "is_dnd 为 1 = 已开启免打扰",
            PrivateMsgApi.parseSession(JSONObject("""{"talker_id":1001,"is_dnd":1}""")).isDnd
        )
        assertEquals(
            false,
            PrivateMsgApi.parseSession(JSONObject("""{"talker_id":1001,"is_dnd":0}""")).isDnd
        )
        assertEquals(
            "缺字段按未开启处理",
            false,
            PrivateMsgApi.parseSession(JSONObject("""{"talker_id":1001}""")).isDnd
        )
    }

    // ============ 会话类型打通 / 标为已读（26.11.23 批次②-1） ============

    @Test
    fun buildSessionsUrl_用户列表用请求枚举1() {
        val url = PrivateMsgApi.buildSessionsUrl(20, 0, PrivateMsgApi.LIST_TYPE_USER)
        assertTrue("用户会话列表的请求参数 session_type 是 1：$url", url.contains("session_type=1"))
        assertTrue(url.contains("size=20"))
    }

    @Test
    fun buildSessionsUrl_粉丝团用请求枚举3而不是2() {
        // 踩过的坑：请求参数和会话对象里的 session_type 不是同一套枚举。
        // 请求侧粉丝团是 3（private_msg.md:254），会话对象侧粉丝团才是 2（同文件第 10 行）。
        val url = PrivateMsgApi.buildSessionsUrl(20, 0, PrivateMsgApi.LIST_TYPE_FAN_GROUP)
        assertTrue("粉丝团列表的请求参数 session_type 必须是 3：$url", url.contains("session_type=3"))
        assertEquals(3, PrivateMsgApi.LIST_TYPE_FAN_GROUP)
        assertTrue("不能把会话对象的 2 当请求参数", !url.contains("session_type=2"))
    }

    @Test
    fun buildSessionsUrl_带翻页游标() {
        val url = PrivateMsgApi.buildSessionsUrl(20, 1234567890123L, PrivateMsgApi.LIST_TYPE_FAN_GROUP)
        assertTrue(url.contains("end_ts=1234567890123"))
    }

    @Test
    fun buildFetchMsgsUrl_按会话类型拼session_type() {
        // 原来写死 session_type=1，粉丝团会话一条消息都拉不出来
        val userUrl = PrivateMsgApi.buildFetchMsgsUrl(1001L, PrivateMsgApi.SESSION_TYPE_USER, 50, 0, 0)
        assertTrue(userUrl.contains("session_type=1"))
        assertTrue(userUrl.contains("talker_id=1001"))
        assertTrue(userUrl.contains("size=50"))

        val groupUrl = PrivateMsgApi.buildFetchMsgsUrl(844424930131966L, PrivateMsgApi.SESSION_TYPE_FAN_GROUP, 15, 0, 99)
        assertTrue("粉丝团会话拉消息要带 session_type=2：$groupUrl", groupUrl.contains("session_type=2"))
        assertTrue(groupUrl.contains("talker_id=844424930131966"))
        assertTrue(groupUrl.contains("end_seqno=99"))
    }

    @Test
    fun parseSession_解析粉丝团字段() {
        val session = PrivateMsgApi.parseSession(
            JSONObject(
                """{"talker_id":844424930131966,"session_type":2,"group_name":"某应援团",
                   "group_cover":"https://i0.hdslb.com/bfs/face/x.jpg","group_type":0,
                   "unread_count":3,"max_seqno":"88"}"""
            )
        )
        assertTrue("session_type=2 是粉丝团会话", session.isFanGroup)
        assertEquals("某应援团", session.groupName)
        assertEquals("https://i0.hdslb.com/bfs/face/x.jpg", session.groupCover)
        assertEquals(0, session.groupType)
        assertEquals(3, session.unreadTotal())
        assertEquals("已读游标是字符串字段", 88L, session.maxSeqno)
        assertEquals("粉丝团会话的显示名用团名", "某应援团", session.sessionName())
    }

    @Test
    fun parseSession_用户会话默认不是粉丝团() {
        val session = PrivateMsgApi.parseSession(JSONObject("""{"talker_id":1001}"""))
        assertEquals("缺字段按用户会话处理", PrivateMsgApi.SESSION_TYPE_USER, session.sessionType)
        assertEquals(false, session.isFanGroup)
    }

    @Test
    fun parseSession_解析推送设置与拦截标记() {
        val receive = PrivateMsgApi.parseSession(JSONObject("""{"talker_id":1001,"setting":0}"""))
        assertTrue("setting=0 是接收推送", receive.isPushOn)
        val reject = PrivateMsgApi.parseSession(JSONObject("""{"talker_id":1001,"setting":1}"""))
        assertEquals("setting=1 是不接收推送", false, reject.isPushOn)
        assertEquals(
            "缺 setting 字段按接收处理",
            true,
            PrivateMsgApi.parseSession(JSONObject("""{"talker_id":1001}""")).isPushOn
        )
        assertTrue(
            "is_intercept=1 表示被拦截",
            PrivateMsgApi.parseSession(JSONObject("""{"talker_id":1001,"is_intercept":1}""")).intercept
        )
        assertEquals(
            false,
            PrivateMsgApi.parseSession(JSONObject("""{"talker_id":1001,"is_intercept":0}""")).intercept
        )
    }

    @Test
    fun parseSession_解析ack_seqno() {
        val session = PrivateMsgApi.parseSession(
            JSONObject("""{"talker_id":1001,"ack_seqno":"42","max_seqno":"45"}""")
        )
        assertEquals(42L, session.ackSeqno)
        assertEquals(45L, session.maxSeqno)
    }

    // ---- 单会话聊天设置（26.11.23 批次②-2）----

    @Test
    fun pushSettingFor_接收是0不接收是1() {
        // 口径见 private_msg.md:1259-1318：setting 0 接收推送、1 不接收
        assertEquals(0, PrivateMsgApi.pushSettingFor(true))
        assertEquals(1, PrivateMsgApi.pushSettingFor(false))
        assertEquals(PrivateMsgApi.PUSH_ON, PrivateMsgApi.pushSettingFor(true))
        assertEquals(PrivateMsgApi.PUSH_OFF, PrivateMsgApi.pushSettingFor(false))
    }

    @Test
    fun buildSessionSsUrl_带talker_uid与必要参数() {
        val url = PrivateMsgApi.buildSessionSsUrl(12345L)
        assertTrue(url, url.startsWith("https://api.vc.bilibili.com/link_setting/v1/link_setting/get_session_ss?"))
        assertTrue(url, url.contains("talker_uid=12345"))
        assertTrue(url, url.contains("mobi_app=web"))
        // 这个接口没有 session_type：仅支持用户会话（private_msg.md:941-1011）
        assertFalse(url, url.contains("session_type"))
    }

    @Test
    fun parseSessionSs_解析推送显示开关与关系状态() {
        val ss = PrivateMsgApi.parseSessionSs(
            JSONObject("""{"code":0,"data":{"push_setting":1,"show_push_setting":0,"follow_status":128}}""")
        )
        assertFalse("push_setting=1 是不接收", ss.pushEnabled)
        assertFalse("show_push_setting=0 表示不显示这个开关", ss.showPushSetting)
        assertEquals(128, ss.followStatus)
        assertEquals(PrivateMsgApi.FOLLOW_STATUS_BLOCKED, ss.followStatus)
    }

    @Test
    fun parseSessionSs_接收推送与缺字段兜底() {
        val receive = PrivateMsgApi.parseSessionSs(
            JSONObject("""{"code":0,"data":{"push_setting":0,"show_push_setting":1}}""")
        )
        assertTrue(receive.pushEnabled)
        assertTrue(receive.showPushSetting)
        assertEquals(0, receive.followStatus)

        // 缺 data（风控时只有 code/message）按「接收推送 + 不显示开关」，页面就不渲染这一项
        val missing = PrivateMsgApi.parseSessionSs(JSONObject("""{"code":-352,"message":"风控校验失败"}"""))
        assertTrue(missing.pushEnabled)
        assertFalse(missing.showPushSetting)
        assertEquals(0, missing.followStatus)
    }

    @Test
    fun statusForIntercept_拦是1取消是0() {
        // 口径见 private_msg.md:1320-1379：status 1 设置拦截、0 取消拦截
        assertEquals(1, PrivateMsgApi.statusForIntercept(true))
        assertEquals(0, PrivateMsgApi.statusForIntercept(false))
        assertEquals(PrivateMsgApi.INTERCEPT_ON, PrivateMsgApi.statusForIntercept(true))
        assertEquals(PrivateMsgApi.INTERCEPT_OFF, PrivateMsgApi.statusForIntercept(false))
    }
}
