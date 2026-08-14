package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.LoginRecord;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LoginRecordApi {

    public static List<LoginRecord> getLoginRecord(long mid, String buvid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/safecenter/login_notice?mid=" + mid;
        if (buvid != null && !buvid.isEmpty()) {
            url += "&buvid=" + buvid;
        }

        JSONObject result = NetWorkUtil.getJson(url);
        List<LoginRecord> records = new ArrayList<>();

        if (result.getInt("code") == 0) {
            JSONObject data = result.getJSONObject("data");
            long recordMid = data.getLong("mid");
            String deviceName = data.optString("device_name", "未知设备");
            String loginType = data.optString("login_type", "未知方式");
            String loginTime = data.optString("login_time", "");
            String location = data.optString("location", "未知位置");
            String ip = data.optString("ip", "");

            records.add(new LoginRecord(recordMid, deviceName, loginType, loginTime, location, ip));
        }

        return records;
    }
}

