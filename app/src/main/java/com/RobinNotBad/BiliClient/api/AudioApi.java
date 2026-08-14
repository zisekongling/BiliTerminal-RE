package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.AudioInfo;
import com.RobinNotBad.BiliClient.model.AudioStream;
import com.RobinNotBad.BiliClient.model.Lyric;
import com.RobinNotBad.BiliClient.model.Playlist;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioApi {

    public static AudioInfo getAudioInfo(long sid) throws IOException, JSONException {
        String url = "https://www.bilibili.com/audio/music-service-c/web/song/info?sid=" + sid;
        JSONObject response = NetWorkUtil.getJson(url);

        if (response.getInt("code") != 0) return null;

        JSONObject data = response.getJSONObject("data");
        AudioInfo info = new AudioInfo();
        info.sid = data.getLong("id");
        info.title = data.optString("title", "");
        info.author = data.optString("author", "");
        info.cover = data.optString("cover", "");
        info.intro = data.optString("intro", "");
        info.duration = data.optInt("duration", 0);
        info.passtime = data.optLong("passtime", 0);
        info.coinNum = data.optLong("coin_num", 0);
        info.lyricUrl = data.optString("lyric", "");

        JSONObject stat = data.optJSONObject("statistic");
        if (stat != null) {
            info.playCount = stat.optLong("sid", 0) > 0 ? stat.optLong("play", 0) : 0;
            info.collectCount = stat.optLong("collect", 0);
            info.commentCount = stat.optLong("comment", 0);
            info.shareCount = stat.optLong("share", 0);
        }

        return info;
    }

    public static AudioStream getAudioStream(long songId, int quality) throws IOException, JSONException {
        String url = "https://api.bilibili.com/audio/music-service-c/url" +
                "?songid=" + songId +
                "&quality=" + quality +
                "&privilege=2" +
                "&platform=android" +
                "&mid=0";

        JSONObject response = NetWorkUtil.getJson(url);

        if (response.getInt("code") != 0) return null;

        JSONObject data = response.getJSONObject("data");
        AudioStream stream = new AudioStream();
        stream.sid = data.getLong("sid");
        stream.type = data.optInt("type", 0);
        stream.timeout = data.optInt("timeout", 0);
        stream.size = data.optLong("size", 0);
        stream.title = data.optString("title", "");
        stream.cover = data.optString("cover", "");

        JSONArray cdnsArr = data.optJSONArray("cdns");
        if (cdnsArr != null) {
            stream.cdns = new ArrayList<>();
            for (int i = 0; i < cdnsArr.length(); i++) {
                stream.cdns.add(cdnsArr.optString(i, ""));
            }
        }

        JSONArray qualitiesArr = data.optJSONArray("qualities");
        if (qualitiesArr != null) {
            stream.qualities = new ArrayList<>();
            for (int i = 0; i < qualitiesArr.length(); i++) {
                JSONObject q = qualitiesArr.getJSONObject(i);
                AudioStream.AudioQuality aq = new AudioStream.AudioQuality();
                aq.type = q.optInt("type", 0);
                aq.desc = q.optString("desc", "");
                aq.size = q.optLong("size", 0);
                aq.bps = q.optString("bps", "");
                aq.tag = q.optString("tag", "");
                aq.require = q.optInt("require", 0);
                aq.requireDesc = q.optString("requiredesc", "");
                stream.qualities.add(aq);
            }
        }

        return stream;
    }

    public static Lyric getLyric(long sid) throws IOException, JSONException {
        String url = "https://www.bilibili.com/audio/music-service-c/web/song/lyric?sid=" + sid;
        JSONObject response = NetWorkUtil.getJson(url);

        if (response.getInt("code") != 0) return null;

        String rawLyric = response.optString("data", "");
        return new Lyric(sid, rawLyric);
    }

    public static List<Playlist> getMyPlaylists(int page) throws IOException, JSONException {
        String url = "https://www.bilibili.com/audio/music-service-c/web/collections/list?pn=" + page + "&ps=20";
        JSONObject response = NetWorkUtil.getJson(url);

        if (response.getInt("code") != 0) return null;

        JSONObject data = response.getJSONObject("data");
        JSONArray arr = data.getJSONArray("data");
        List<Playlist> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(parsePlaylist(arr.getJSONObject(i)));
        }
        return list;
    }

    public static Playlist getPlaylistDetail(long mlid) throws IOException, JSONException {
        String url = "https://www.bilibili.com/audio/music-service-c/web/collections/info?sid=" + mlid;
        JSONObject response = NetWorkUtil.getJson(url);

        if (response.getInt("code") != 0) return null;

        JSONObject data = response.getJSONObject("data");
        return parsePlaylist(data);
    }

    public static List<Playlist> getHotPlaylists(int page) throws IOException, JSONException {
        String url = "https://www.bilibili.com/audio/music-service-c/web/menu/hit?pn=" + page + "&ps=20";
        JSONObject response = NetWorkUtil.getJson(url);

        if (response.getInt("code") != 0) return null;

        JSONObject data = response.getJSONObject("data");
        JSONArray arr = data.getJSONArray("data");
        List<Playlist> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(parseHotPlaylist(arr.getJSONObject(i)));
        }
        return list;
    }

    public static List<Playlist> getRankPlaylists(int page) throws IOException, JSONException {
        String url = "https://www.bilibili.com/audio/music-service-c/web/menu/rank?pn=" + page + "&ps=20";
        JSONObject response = NetWorkUtil.getJson(url);

        if (response.getInt("code") != 0) return null;

        JSONObject data = response.getJSONObject("data");
        JSONArray arr = data.getJSONArray("data");
        List<Playlist> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(parseHotPlaylist(arr.getJSONObject(i)));
        }
        return list;
    }

    private static Playlist parsePlaylist(JSONObject json) throws JSONException {
        Playlist p = new Playlist();
        p.id = json.getLong("id");
        p.uid = json.getLong("uid");
        p.uname = json.optString("uname", "");
        p.title = json.optString("title", "");
        p.type = json.optInt("type", 0);
        p.published = json.optInt("published", 0);
        p.cover = json.optString("cover", "");
        p.ctime = json.optLong("ctime", 0);
        p.songCount = json.optInt("song", 0);
        p.desc = json.optString("desc", "");
        p.menuId = json.optLong("menuId", 0);

        JSONArray sidsArr = json.optJSONArray("sids");
        if (sidsArr != null) {
            p.sids = new ArrayList<>();
            for (int i = 0; i < sidsArr.length(); i++) {
                p.sids.add(sidsArr.optLong(i, 0));
            }
        }

        JSONObject stat = json.optJSONObject("statistic");
        if (stat != null) {
            p.playCount = stat.optLong("play", 0);
            p.collectCount = stat.optLong("collect", 0);
            p.shareCount = stat.optLong("share", 0);
        }

        return p;
    }

    private static Playlist parseHotPlaylist(JSONObject json) throws JSONException {
        Playlist p = new Playlist();
        p.menuId = json.getLong("menuId");
        p.uid = json.getLong("uid");
        p.uname = json.optString("uname", "");
        p.title = json.optString("title", "");
        p.cover = json.optString("cover", "");
        p.desc = json.optString("intro", "");
        p.type = json.optInt("type", 0);
        p.published = json.optInt("off", 0) == 0 ? 1 : 0;
        p.ctime = json.optLong("ctime", 0);
        p.songCount = json.optInt("snum", 0);

        JSONObject stat = json.optJSONObject("statistic");
        if (stat != null) {
            p.playCount = stat.optLong("play", 0);
            p.collectCount = stat.optLong("collect", 0);
            p.shareCount = stat.optLong("share", 0);
        }

        return p;
    }
}