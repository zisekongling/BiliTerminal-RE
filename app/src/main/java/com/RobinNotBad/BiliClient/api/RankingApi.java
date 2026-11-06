package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public class RankingApi {

    public static void getRanking(List<VideoCard> videoCardList, int rid, String type) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/ranking/v2";
        url += new NetWorkUtil.FormData().setUrlParam(true)
                .put("rid", rid)
                .put("type", type)
                .put("web_location", "333.934");

        JSONObject result = NetWorkUtil.getJson(ConfInfoApi.signWBI(url));

        // 排行榜接口偶尔会在 list 里塞进结构不完整的卡片（owner/stat 缺失、bvid 为空）。
        // 早先这里一律用 getXxx，缺一个字段就整页抛 JSONException，用户看到的是
        // 「数据解析错误」而不是排行榜。统一改成 opt 取值：缺字段就留空或跳过这张卡。
        if (result.optInt("code", -1) != 0) return;

        JSONObject data = result.optJSONObject("data");
        if (data == null) return;

        JSONArray list = data.optJSONArray("list");
        if (list == null) return;

        for (int i = 0; i < list.length(); i++) {
            JSONObject card = list.optJSONObject(i);
            if (card == null) continue;

            long aid = card.optLong("aid", 0);
            String bvid = card.optString("bvid", "");
            if (aid == 0 && bvid.isEmpty()) continue; // 主键都没有，点进去只会是空白页

            VideoCard videoCard = new VideoCard();
            videoCard.aid = aid;
            videoCard.bvid = bvid;
            videoCard.cover = card.optString("pic", "");
            videoCard.title = card.optString("title", "");
            JSONObject owner = card.optJSONObject("owner");
            videoCard.upName = owner == null ? "" : owner.optString("name", "");
            JSONObject stat = card.optJSONObject("stat");
            long view = stat == null ? 0 : stat.optLong("view", 0);
            videoCard.view = view > 0 ? StringUtil.toWan(view) + "观看" : "";
            videoCardList.add(videoCard);
        }
    }
}

