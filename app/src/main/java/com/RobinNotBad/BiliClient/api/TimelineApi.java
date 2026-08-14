package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.Timeline;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TimelineApi {
    public static List<Timeline.DayInfo> getTimeline(String types, int before, int after) throws JSONException, IOException {
        String url = "https://api.bilibili.com/pgc/web/timeline?types=" + types + "&before=" + before + "&after=" + after;
        
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.getInt("code") != 0) {
            throw new JSONException(all.optString("message", "请求失败"));
        }

        JSONArray result = all.getJSONArray("result");
        List<Timeline.DayInfo> dayInfoList = new ArrayList<>();

        for (int i = 0; i < result.length(); i++) {
            JSONObject dayObj = result.getJSONObject(i);
            Timeline.DayInfo dayInfo = new Timeline.DayInfo();
            dayInfo.date = dayObj.getString("date");
            dayInfo.date_ts = dayObj.getLong("date_ts");
            dayInfo.day_of_week = dayObj.getInt("day_of_week");
            dayInfo.is_today = dayObj.getInt("is_today");

            JSONArray episodesArray = dayObj.getJSONArray("episodes");
            dayInfo.episodes = new ArrayList<>();
            for (int j = 0; j < episodesArray.length(); j++) {
                JSONObject epObj = episodesArray.getJSONObject(j);
                Timeline.Episode episode = new Timeline.Episode();
                episode.cover = epObj.optString("cover", "");
                episode.delay = epObj.optInt("delay", 0);
                episode.delay_id = epObj.optLong("delay_id", 0);
                episode.delay_index = epObj.optString("delay_index", "");
                episode.delay_reason = epObj.optString("delay_reason", "");
                episode.ep_cover = epObj.optString("ep_cover", "");
                episode.episode_id = epObj.optLong("episode_id", 0);
                episode.pub_index = epObj.optString("pub_index", "");
                episode.pub_time = epObj.optString("pub_time", "");
                episode.pub_ts = epObj.optLong("pub_ts", 0);
                episode.published = epObj.optInt("published", 0);
                episode.follows = epObj.optString("follows", "");
                episode.plays = epObj.optString("plays", "");
                episode.season_id = epObj.optLong("season_id", 0);
                episode.square_cover = epObj.optString("square_cover", "");
                episode.title = epObj.optString("title", "");
                dayInfo.episodes.add(episode);
            }

            dayInfoList.add(dayInfo);
        }

        return dayInfoList;
    }
}

