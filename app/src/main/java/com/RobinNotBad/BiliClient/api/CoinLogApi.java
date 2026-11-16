package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.CoinLog;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CoinLogApi {

    public static List<CoinLog> getCoinLog() throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/member/web/coin/log";
        JSONObject result = NetWorkUtil.getJson(url);
        List<CoinLog> logs = new ArrayList<>();

        if (result.getInt("code") == 0) {
            JSONObject data = result.getJSONObject("data");
            JSONArray list = data.getJSONArray("list");
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                String time = item.getString("time");
                // delta 是 num：打赏类记录里有 0.1 / 0.2 这种小数，getInt 会截成 0
                double delta = item.getDouble("delta");
                String reason = item.getString("reason");
                logs.add(new CoinLog(time, delta, reason));
            }
        }

        return logs;
    }
}

