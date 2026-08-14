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

        if (result.has("data") && !result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            if (data.has("list") && !data.isNull("list")) {
                JSONArray list = data.getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject card = list.getJSONObject(i);
                    VideoCard videoCard = new VideoCard();
                    videoCard.aid = card.getLong("aid");
                    videoCard.bvid = card.getString("bvid");
                    videoCard.cover = card.getString("pic");
                    videoCard.title = card.getString("title");
                    videoCard.upName = card.getJSONObject("owner").getString("name");
                    videoCard.view = StringUtil.toWan(card.getJSONObject("stat").getLong("view")) + "观看";
                    videoCardList.add(videoCard);
                }
            }
        }
    }
}

