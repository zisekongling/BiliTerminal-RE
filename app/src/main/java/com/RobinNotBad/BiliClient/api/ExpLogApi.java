package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ExpLog;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExpLogApi {

    public static List<ExpLog> getExpLog() throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/member/web/exp/log?jsonp=jsonp&web_location=333.33";
        JSONObject result = NetWorkUtil.getJson(url);
        List<ExpLog> logs = new ArrayList<>();

        if (result.getInt("code") == 0) {
            JSONObject data = result.getJSONObject("data");
            JSONArray list = data.getJSONArray("list");
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                int delta = item.getInt("delta");
                String time = item.getString("time");
                String reason = item.getString("reason");
                logs.add(new ExpLog(delta, time, reason));
            }
        }

        return logs;
    }
}

