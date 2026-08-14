package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ElectricPanel;
import com.RobinNotBad.BiliClient.model.ElectricUser;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * 充电公示API
 */
public class ElectricApi {

    /**
     * 获取空间充电公示列表
     *
     * @param up_mid 目标用户mid
     * @return 充电公示数据，如果未开通充电或出错则返回null
     * @throws IOException   网络异常
     * @throws JSONException JSON解析异常
     */
    public static ElectricPanel getElectricPanel(long up_mid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/ugcpay-rank/elec/month/up?up_mid=" + up_mid;

        JSONObject result = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);

        int code = result.optInt("code", -1);

        // code为0表示成功
        // code为-400表示请求错误
        // code为88214表示up主未开通充电
        if (code != 0) {
            return null;
        }

        if (!result.has("data") || result.isNull("data")) {
            return null;
        }

        JSONObject data = result.getJSONObject("data");

        ElectricPanel panel = new ElectricPanel();
        panel.count = data.optInt("count", 0);
        panel.total_count = data.optInt("total_count", 0);
        panel.total = data.optInt("total", 0);
        panel.special_day = data.optInt("special_day", 0);

        if (data.has("list") && !data.isNull("list")) {
            JSONArray list = data.getJSONArray("list");
            for (int i = 0; i < list.length(); i++) {
                JSONObject userJson = list.getJSONObject(i);
                ElectricUser user = parseElectricUser(userJson);
                if (user != null) {
                    panel.list.add(user);
                }
            }
        }

        return panel;
    }

    /**
     * 解析充电用户JSON数据
     *
     * @param json 用户JSON对象
     * @return 充电用户对象
     * @throws JSONException JSON解析异常
     */
    private static ElectricUser parseElectricUser(JSONObject json) throws JSONException {
        ElectricUser user = new ElectricUser();

        user.uname = json.optString("uname", "");
        user.avatar = json.optString("avatar", "");
        user.mid = json.optLong("mid", 0);
        user.pay_mid = json.optLong("pay_mid", 0);
        user.rank = json.optInt("rank", 0);
        user.trend_type = json.optInt("trend_type", 0);
        user.message = json.optString("message", "");
        user.msg_hidden = json.optInt("msg_hidden", 0);

        if (json.has("vip_info") && !json.isNull("vip_info")) {
            JSONObject vipJson = json.getJSONObject("vip_info");
            ElectricUser.VipInfo vipInfo = new ElectricUser.VipInfo();
            vipInfo.vipDueMsec = vipJson.optLong("vipDueMsec", 0);
            vipInfo.vipStatus = vipJson.optInt("vipStatus", 0);
            vipInfo.vipType = vipJson.optInt("vipType", 0);
            user.vip_info = vipInfo;
        }

        return user;
    }
}

