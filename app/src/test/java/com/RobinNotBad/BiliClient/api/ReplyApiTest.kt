package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.model.Reply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评论点赞/点踩的纯逻辑单测。
 *
 * 这两块逻辑不碰网络，但错了的后果是"用户看不见的错"：
 * 评论的 action 字段判错 → 已踩的评论重进页面显示成没操作过；
 * 错误码不翻译 → 用户点了没反应却不知道原因（没登录 / csrf 失效 / 限流被当成同一种失败）。
 */
class ReplyApiTest {

    // ---- action 字段：0=无 1=已赞 2=已踩（bilibili-API/docs/comment/readme.md） ----

    @Test
    fun isLikedAction_onlyOneCountsAsLiked() {
        assertTrue("action==1 才是已点赞", ReplyApi.isLikedAction(1))
        assertFalse("action==0 是无操作，不能当成已赞", ReplyApi.isLikedAction(0))
        assertFalse("action==2 是已点踩，绝不能当成已赞", ReplyApi.isLikedAction(2))
    }

    @Test
    fun isDislikedAction_onlyTwoCountsAsDisliked() {
        assertTrue("action==2 才是已点踩", ReplyApi.isDislikedAction(2))
        assertFalse("action==0 是无操作", ReplyApi.isDislikedAction(0))
        assertFalse("action==1 是已点赞，不能当成已踩", ReplyApi.isDislikedAction(1))
    }

    @Test
    fun likedAndDislikedAreMutuallyExclusive() {
        // 服务端保证同一时刻只会是 0/1/2 中的一个；
        // 这条测试防止将来有人"顺手"把两个判定都改成 >= 之类而破坏互斥性
        for (action in 0..2) {
            assertFalse(
                "action=$action 不能同时是已赞和已踩",
                ReplyApi.isLikedAction(action) && ReplyApi.isDislikedAction(action)
            )
        }
    }

    @Test
    fun unknownActionIsTreatedAsNoOperation() {
        // 服务端新增状态时不能崩，也不能误判成已赞/已踩
        assertFalse(ReplyApi.isLikedAction(99))
        assertFalse(ReplyApi.isDislikedAction(-1))
    }

    // ---- 错误码翻译：这些接口失败只回 code，不抛异常 ----

    @Test
    fun actionErrorMsg_successIsSilent() {
        assertEquals("code==0 表示成功，不该弹任何提示", "", ReplyApi.actionErrorMsg(0))
    }

    @Test
    fun actionErrorMsg_distinguishesNotLoggedInFromCsrfExpired() {
        // 这两条原来都只显示"失败"，但用户要做的事完全不同：一个去登录，一个重新登录刷新凭证
        assertTrue("未登录要说清是没登录", ReplyApi.actionErrorMsg(-101).contains("登录"))
        assertTrue("csrf 失效要说是凭证失效", ReplyApi.actionErrorMsg(-111).contains("失效"))
        assertFalse(
            "-101 与 -111 不能给出同一句提示，否则用户无从区分",
            ReplyApi.actionErrorMsg(-101) == ReplyApi.actionErrorMsg(-111)
        )
    }

    @Test
    fun actionErrorMsg_rateLimitedIsExplicit() {
        assertTrue("被限流要提示稍后再试，而不是让用户反复点", ReplyApi.actionErrorMsg(-509).contains("频繁"))
    }

    @Test
    fun actionErrorMsg_knownCommentErrorsAreNotGeneric() {
        val known = listOf(-102, -400, -404, 12002, 12004, 12006, 12009, 12011, 12029, 12030, 65004, 65005, 65006, 65007)
        for (code in known) {
            val msg = ReplyApi.actionErrorMsg(code)
            assertTrue("code=$code 应有专属中文提示", msg.isNotEmpty())
            assertFalse("code=$code 不应落到兜底文案", msg.startsWith("操作失败（错误码"))
        }
    }

    // ---- 置顶评论（bilibili-API/docs/comment/action.md:411-418） ----

    @Test
    fun topActionFor_oneMeansTopAndZeroMeansCancel() {
        // 接口的 action 语义反直觉：1=设为置顶、0=取消置顶。
        // 写反了不会报错，只会静默把"置顶"变成"取消"，所以必须钉死。
        assertEquals("true 对应 action=1（设为置顶）", 1, ReplyApi.topActionFor(true))
        assertEquals("false 对应 action=0（取消置顶）", 0, ReplyApi.topActionFor(false))
    }

    @Test
    fun actionErrorMsg_explainsExistingTopAndNonRootReply() {
        // 12029/12030 是置顶接口最容易撞到的两个码，必须给出可操作的中文提示
        val existing = ReplyApi.actionErrorMsg(12029)
        assertTrue("12029 要告诉用户已有置顶", existing.contains("置顶"))
        assertTrue("12029 还要说明该怎么办（先取消原置顶）", existing.contains("取消"))
        assertTrue("12030 要说明只能置顶一级评论", ReplyApi.actionErrorMsg(12030).contains("一级评论"))
    }

    @Test
    fun actionErrorMsg_unknownCodeKeepsTheNumber() {
        // 遇到没见过的码，提示里必须带上原始数字，否则用户/开发者无法据此定位
        val msg = ReplyApi.actionErrorMsg(99999)
        assertTrue("兜底文案要带上错误码本身", msg.contains("99999"))
    }

    // ---- 带图评论的发送闸门（WriteReplyActivity） ----

    @Test
    fun canSendReply_blocksWhileImagesAreStillUploading() {
        // 允许的条件只有一个：没有图片还在上传。
        // 图没传完就放行 = 评论少图发出且无提示，这是这条闸门存在的唯一理由。
        assertTrue("没有图片在上传时才能发", ReplyApi.canSendReply(0))
        assertTrue("负数按没有处理", ReplyApi.canSendReply(-1))
        assertFalse("还有 1 张在上传时必须拦住", ReplyApi.canSendReply(1))
        assertFalse("还有 3 张在上传时必须拦住", ReplyApi.canSendReply(3))
    }

    @Test
    fun uploadPendingTip_countsAndIsEmptyWhenClear() {
        assertEquals("没有图片在上传时不该有提示", "", ReplyApi.uploadPendingTip(0))
        assertEquals("", ReplyApi.uploadPendingTip(-1))

        val tip = ReplyApi.uploadPendingTip(2)
        assertTrue("提示要带上还没传完的张数", tip.contains("2"))
        assertTrue("提示要说清是图片还在上传", tip.contains("上传"))
        // 文案必须能直接丢给 MsgUtil.showMsg，所以连完整文案一起钉死
        assertEquals("还有 3 张图片正在上传，请稍候", ReplyApi.uploadPendingTip(3))
    }

    // ---- pictures 组装（ReplyApi.buildPictures，原先内联在 WriteReplyActivity 里） ----

    private fun image(url: String, w: Int, h: Int, size: Double) =
        ReplyApi.UploadImageData().apply {
            image_url = url
            image_width = w
            image_height = h
            img_size = size
        }

    @Test
    fun buildPictures_keepsFractionalKb() {
        // 服务端返回的 img_size 是小数 KB（官方示例 "img_size": 6.261，
        // bilibili-API/docs/dynamic/publish.md:66）。历史上这里用 long + optLong 存，
        // 会把 6.261 截成 6、662.6005859375 截成 662。
        // 这条测试就是钉死"不能截断"。
        val json = ReplyApi.buildPictures(listOf(image("http://i0.hdslb.com/a.png", 73, 71, 6.261)))
        assertTrue("小数 KB 必须原样保留，不能被截成整数：$json", json.contains("6.261"))
        assertFalse("6.261 不能被截断成 6：$json", json.contains("\"img_size\":6,"))
    }

    @Test
    fun buildPictures_hasAllFourDocumentedFields() {
        // 字段名以服务端文档为准（bilibili-API/docs/comment/readme.md:289-296）：
        // img_src / img_width / img_height / img_size，缺一个服务端就不认这张图。
        val json = ReplyApi.buildPictures(listOf(image("http://i0.hdslb.com/a.png", 73, 71, 6.261)))
        for (field in listOf("img_src", "img_width", "img_height", "img_size")) {
            assertTrue("pictures 里必须有 $field：$json", json.contains("\"$field\""))
        }
        assertTrue("图片地址要原样带上：$json", json.contains("http://i0.hdslb.com/a.png"))
        assertTrue("宽度要带上：$json", json.contains("\"img_width\":73"))
        assertTrue("高度要带上：$json", json.contains("\"img_height\":71"))
    }

    @Test
    fun buildPictures_emptyMeansNoParameter() {
        // 没有图片时必须返回空串：sendReply 据此判断"不带 pictures 参数"，
        // 若返回 "[]" 会被服务端当成一次非法带图评论。
        assertEquals("没有图片时返回空串", "", ReplyApi.buildPictures(emptyList()))
        assertEquals("null 按空列表处理", "", ReplyApi.buildPictures(null))
    }

    @Test
    fun buildPictures_skipsNullEntriesButKeepsTheRest() {
        // 列表里的 null 是脏数据（例如上传失败留下的占位），
        // 跳过它而不是让整条评论发不出去。
        val json = ReplyApi.buildPictures(listOf(null, image("http://i0.hdslb.com/b.png", 10, 20, 1.5)))
        assertTrue("有效的那张要在：$json", json.contains("http://i0.hdslb.com/b.png"))
        assertEquals("只应剩一张图", 1, org.json.JSONArray(json).length())
    }

    @Test
    fun buildPictures_keepsOrderAndCount() {
        // 多图顺序要与用户选的顺序一致（服务端按数组顺序展示）
        val json = ReplyApi.buildPictures(
            listOf(
                image("http://i0.hdslb.com/1.png", 1, 1, 1.0),
                image("http://i0.hdslb.com/2.png", 2, 2, 2.5),
                image("http://i0.hdslb.com/3.png", 3, 3, 3.75)
            )
        )
        val array = org.json.JSONArray(json)
        assertEquals("三张图都要在", 3, array.length())
        assertEquals("http://i0.hdslb.com/1.png", array.getJSONObject(0).getString("img_src"))
        assertEquals("http://i0.hdslb.com/2.png", array.getJSONObject(1).getString("img_src"))
        assertEquals("http://i0.hdslb.com/3.png", array.getJSONObject(2).getString("img_src"))
        assertEquals("2.5 不能被截断", 2.5, array.getJSONObject(1).getDouble("img_size"), 0.000001)
    }

    // ---- 图床 biz：评论图必须与动态图同一个目录 ----

    @Test
    fun imageBiz_commentSharesDynamicDirectoryAndMessageUsesIm() {
        // 图床 biz 只决定图片落在哪个 BFS 目录：
        // - 动态图与评论图都是 /bfs/new_dyn/（评论的 pictures 只认这个目录下的图；
        //   26.10.02 曾自造 new_reply，图能传上去但发评论被服务端以 12066 拒绝）
        // - 私信图是 /bfs/im/（上游 PiliPlus 同样传 biz:'im'）
        assertEquals("评论图与动态图必须共用 new_dyn", "new_dyn", ReplyApi.BIZ_DYNAMIC)
        assertEquals("私信图走 IM 图床目录", "im", ReplyApi.BIZ_MESSAGE)
    }

    // ---- 发送后「评论区是否已能查到」（反馈 #85） ----

    private fun reply(rpid: Long) = Reply().apply { this.rpid = rpid }

    @Test
    fun containsReplyId_findsTopLevelReply() {
        val list = listOf(reply(100), reply(200))
        assertTrue("一级评论就在列表里", ReplyApi.containsReplyId(list, 200))
    }

    @Test
    fun containsReplyId_findsReplyInsideChildList() {
        // 楼中楼里新发的回复同样要能认出来，不能只看顶层
        val parent = reply(100).apply { childMsgList.add(reply(101)) }
        assertTrue("楼中楼里的 rpid 也算查到", ReplyApi.containsReplyId(listOf(parent), 101))
    }

    @Test
    fun containsReplyId_absentReturnsFalse() {
        assertFalse("列表里没有就是没查到", ReplyApi.containsReplyId(listOf(reply(100)), 999))
    }

    @Test
    fun containsReplyId_toleratesNullAndInvalidInput() {
        // 脏数据 / 非法入参不能崩：这条判定跑在"发送评论"的主路径上
        assertFalse("null 列表", ReplyApi.containsReplyId(null, 100))
        assertFalse("rpid 非法时不查", ReplyApi.containsReplyId(listOf(reply(100)), 0))
        assertFalse("空列表", ReplyApi.containsReplyId(emptyList(), 100))
    }

    // ---- 跨页去重（ReplyApi.filterDuplicateReplies，三层兜底的第 1 层） ----

    private fun seenOf(vararg rpids: Long): MutableSet<Long> {
        val seen = java.util.HashSet<Long>()
        for (rpid in rpids) if (rpid > 0) seen.add(rpid)
        return seen
    }

    @Test
    fun filterDuplicateReplies_removesRepeatsAndKeepsFirstOccurrenceOrder() {
        val seen = seenOf(100)
        val incoming = arrayListOf(reply(100), reply(200), reply(200), reply(300))
        assertEquals("删掉 2 条重复", 2, ReplyApi.filterDuplicateReplies(incoming, seen))
        assertEquals("seen 里已有的 100 与同页重复的第二个 200 都被删掉，剩下保持首次出现顺序", listOf(200L, 300L), incoming.map { it.rpid })
    }

    @Test
    fun filterDuplicateReplies_registersFirstTimersInSeen() {
        val seen = java.util.HashSet<Long>()
        val incoming = arrayListOf(reply(1), reply(2))
        ReplyApi.filterDuplicateReplies(incoming, seen)
        assertEquals("首次出现的 rpid 要登记，翻页时才不会再放进来", setOf(1L, 2L), seen)
    }

    @Test
    fun filterDuplicateReplies_dropsNullEntries() {
        val incoming = arrayListOf(reply(1), null, reply(2))
        assertEquals("null 是脏数据，必须一起清掉（后面按字段访问会 NPE）", 1, ReplyApi.filterDuplicateReplies(incoming, java.util.HashSet()))
        assertEquals(listOf(1L, 2L), incoming.map { it!!.rpid })
    }

    @Test
    fun filterDuplicateReplies_keepsInvalidRpidAndDoesNotRegisterIt() {
        val seen = java.util.HashSet<Long>()
        val incoming = arrayListOf(reply(0), reply(-5), reply(7))
        assertEquals("rpid 非法既不删也不登记", 0, ReplyApi.filterDuplicateReplies(incoming, seen))
        assertEquals("非法 rpid 不能进 seen，否则以后所有脏数据都被判成重复", setOf(7L), seen)
    }

    @Test
    fun filterDuplicateReplies_toleratesEmptyInput() {
        assertEquals("null 列表", 0, ReplyApi.filterDuplicateReplies(null, java.util.HashSet()))
        assertEquals("空列表", 0, ReplyApi.filterDuplicateReplies(ArrayList(), java.util.HashSet()))
    }
}