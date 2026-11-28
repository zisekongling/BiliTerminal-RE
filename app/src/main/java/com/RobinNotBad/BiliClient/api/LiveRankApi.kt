package com.RobinNotBad.BiliClient.api

import com.RobinNotBad.BiliClient.util.NetWorkUtil
import org.json.JSONObject

/**
 * 直播间榜单三件套（26.11.26 新增）。
 *
 * 接口出处（本仓库快照，非逆向）：
 * - 大航海成员：`bilibili-API/docs/live/guard.md` §查询大航海成员（`xlive/app-room/v2/guardTab/topListNew`）
 * - 粉丝团成员：同文件 §查询粉丝团成员（`xlive/general-interface/v1/rank/getFansMembersRank`）
 * - 礼物价目：`bilibili-API/docs/live/gift.md` §获取直播间内礼物（`xlive/web-room/v1/giftPanel/roomGiftList`）
 *
 * 三条都**不需要 Cookie**，所以未登录也能看，适合做成直播间信息区的只读卡片。
 */
object LiveRankApi {

    private const val GUARD_URL =
        "https://api.live.bilibili.com/xlive/app-room/v2/guardTab/topListNew"
    private const val FANS_URL =
        "https://api.live.bilibili.com/xlive/general-interface/v1/rank/getFansMembersRank"
    private const val GIFT_URL =
        "https://api.live.bilibili.com/xlive/web-room/v1/giftPanel/roomGiftList"

    /** 一次取多少条。两个榜单接口的 page_size 上限都是 30，超了服务端会当成 10，所以别写大。 */
    private const val PAGE_SIZE = 20

    /** 大航海成员。 */
    data class GuardMember(
        val uid: Long,
        val name: String,
        val face: String,
        val rank: Int,
        /** 陪伴天数；服务端给 0 是常态，不要当异常。 */
        val accompanyDays: Int,
    )

    /** 粉丝团成员。 */
    data class FanMember(
        val uid: Long,
        val name: String,
        val face: String,
        val rank: Int,
        val score: Long,
        val medalName: String,
        val medalLevel: Int,
        /** 1 总督 / 2 提督 / 3 舰长 / 0 未上舰。 */
        val guardLevel: Int,
    )

    /**
     * 礼物价目。
     *
     * @param priceCoin 原始值，**除以 1000 才是元**（见快照 gift.md 的 price 备注）。
     */
    data class Gift(
        val id: Long,
        val name: String,
        val priceCoin: Long,
        val img: String,
        val desc: String,
    )

    /**
     * 大航海成员列表。
     *
     * @param typ 排序：3 按周 / 4 按月 / 5 按总亲密度；传 0 用服务端默认。
     */
    fun getGuardMembers(roomId: Long, ruid: Long, page: Int = 1, typ: Int = 0): List<GuardMember> {
        if (roomId <= 0L || ruid <= 0L) return emptyList()
        val form = NetWorkUtil.FormData().setUrlParam(true)
            .put("roomid", roomId)
            .put("ruid", ruid)
            .put("page", page)
            .put("page_size", PAGE_SIZE)
        if (typ > 0) form.put("typ", typ)
        val body = request(GUARD_URL + form) ?: return emptyList()
        val data = body.optJSONObject("data") ?: return emptyList()
        val out = ArrayList<GuardMember>(PAGE_SIZE + 3)
        // 坑：`data.list` 是**从第 4 名开始**的，前 3 名单独放在 `data.top3`。
        // 只读 list 会静默吞掉榜首三人（快照 guard.md 有写，实测 21452505 房间 list[0].rank == 4）。
        if (page == 1) appendGuards(out, data.optJSONArray("top3"))
        appendGuards(out, data.optJSONArray("list"))
        return out
    }

    private fun appendGuards(out: MutableList<GuardMember>, list: org.json.JSONArray?) {
        if (list == null) return
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val base = item.optJSONObject("uinfo")?.optJSONObject("base")
            out.add(
                GuardMember(
                    uid = item.optLong("ruid"),
                    name = base?.optString("name").orEmpty(),
                    face = base?.optString("face").orEmpty(),
                    rank = item.optInt("rank"),
                    accompanyDays = item.optInt("accompany"),
                )
            )
        }
    }

    /**
     * 粉丝团成员列表。
     *
     * @param rankType 1 = 只算粉丝牌还亮着的；2 = 含所有没上过舰的（此时服务端要求带 ts）
     */
    fun getFansMembers(ruid: Long, page: Int = 1, rankType: Int = 1): List<FanMember> {
        if (ruid <= 0L) return emptyList()
        val form = NetWorkUtil.FormData().setUrlParam(true)
            .put("ruid", ruid)
            .put("page", page)
            .put("page_size", PAGE_SIZE)
            .put("rank_type", rankType)
        // rank_type=2 时 ts 是必填，快照里写明「>=1000 即可」——这里直接给当前秒级时间戳，反正合法
        if (rankType == 2) form.put("ts", System.currentTimeMillis())
        val body = request(FANS_URL + form) ?: return emptyList()
        val list = body.optJSONObject("data")?.optJSONArray("item") ?: return emptyList()
        val out = ArrayList<FanMember>(list.length())
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            out.add(
                FanMember(
                    uid = item.optLong("uid"),
                    name = item.optString("name"),
                    face = item.optString("face"),
                    rank = item.optInt("user_rank"),
                    score = item.optLong("score"),
                    medalName = item.optString("medal_name"),
                    medalLevel = item.optInt("level"),
                    guardLevel = item.optInt("guard_level"),
                )
            )
        }
        return out
    }

    /**
     * 直播间礼物价目表。
     *
     * @param areaParentId / areaId 传直播间的分区能多拿到「活动礼物」；不传仍能拿到常驻礼物
     *   （快照原文：不填写可能会获取不到部分活动礼物）。
     */
    fun getGiftList(roomId: Long, areaParentId: Int = 0, areaId: Int = 0): List<Gift> {
        if (roomId <= 0L) return emptyList()
        val form = NetWorkUtil.FormData().setUrlParam(true)
            // 快照的参数表写 "web"，可它自己的示例用的是 "pc" 且能跑通；以示例为准
            .put("platform", "pc")
            .put("room_id", roomId)
        if (areaParentId > 0) form.put("area_parent_id", areaParentId)
        if (areaId > 0) form.put("area_id", areaId)
        val body = request(GIFT_URL + form) ?: return emptyList()
        val list = body.optJSONObject("data")
            ?.optJSONObject("gift_config")
            ?.optJSONObject("base_config")
            ?.optJSONArray("list") ?: return emptyList()
        val out = ArrayList<Gift>(list.length())
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            out.add(
                Gift(
                    id = item.optLong("id"),
                    name = item.optString("name"),
                    priceCoin = item.optLong("price"),
                    img = item.optString("img_basic"),
                    desc = item.optString("desc"),
                )
            )
        }
        // 服务端返回顺序是乱的（100 条里贵的便宜的交错），价目表按价格升序才读得下去
        return out.sortedBy { it.priceCoin }
    }

    /**
     * 三个接口共用的取法：失败返回 null 而不是抛。
     *
     * 榜单是**可选展示**，任何一条取不到都不该把直播页打断——调用方拿到空列表就当没有这一块。
     * 这里仍然把 code != 0 的情况记一条日志，否则「榜单一直空着」会查不出原因。
     */
    private fun request(url: String): JSONObject? = try {
        val result = NetWorkUtil.getJson(url)
        val code = result.optInt("code", -1)
        if (code == 0) result else {
            com.RobinNotBad.BiliClient.util.Logu.d(
                "直播间榜单",
                "code=$code ${result.optString("message")} <- ${url.substringBefore('?')}"
            )
            null
        }
    } catch (e: Exception) {
        com.RobinNotBad.BiliClient.util.Logu.d("直播间榜单", "请求失败: ${e.javaClass.simpleName}")
        null
    }
}
