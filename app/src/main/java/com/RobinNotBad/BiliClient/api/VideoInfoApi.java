package com.RobinNotBad.BiliClient.api;

import android.annotation.SuppressLint;
import android.text.SpannableStringBuilder;
import android.util.Pair;

import com.RobinNotBad.BiliClient.model.At;
import com.RobinNotBad.BiliClient.model.Collection;
import com.RobinNotBad.BiliClient.model.Stats;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.RobinNotBad.BiliClient.util.TimeUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


//视频信息API 自己写的


public class VideoInfoApi {
    public static VideoInfo getVideoInfo(String bvid) throws IOException, JSONException {  //通过bvid获取json
        String url = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
        boolean privacyMode = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVACY_MODE, false);
        JSONObject result = privacyMode ? NetWorkUtil.getJsonPrivacy(url) : NetWorkUtil.getJson(url);
        if (!result.has("data")) return null;
        VideoInfo videoInfo = getInfoByJson(result.getJSONObject("data"));
        LikeCoinFavApi.getVideoStats(videoInfo);
        return videoInfo;
    }

    public static VideoInfo getVideoInfo(long aid) throws IOException, JSONException {  //通过aid获取json
        String url = "https://api.bilibili.com/x/web-interface/view?aid=" + aid;
        boolean privacyMode = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVACY_MODE, false);
        JSONObject result = privacyMode ? NetWorkUtil.getJsonPrivacy(url) : NetWorkUtil.getJson(url);
        if (!result.has("data")) return null;
        VideoInfo videoInfo = getInfoByJson(result.getJSONObject("data"));
        LikeCoinFavApi.getVideoStats(videoInfo);
        return videoInfo;
    }


    public static String getTags(String bvid) throws IOException, JSONException {  //通过bvid获取tag
        String url = "https://api.bilibili.com/x/tag/archive/tags?bvid=" + bvid;
        JSONObject result = NetWorkUtil.getJson(url);
        return analyzeTags(result.getJSONArray("data"));
    }

    public static String getTags(long aid) throws IOException, JSONException {  //通过aid获取tag
        String url = "https://api.bilibili.com/x/tag/archive/tags?aid=" + aid;
        JSONObject result = NetWorkUtil.getJson(url);
        return analyzeTags(result.getJSONArray("data"));
    }

    public static String analyzeTags(JSONArray tagJson) throws JSONException {
        StringBuilder tags = new StringBuilder();
        for (int i = 0; i < tagJson.length(); i++) {
            if (i > 0) tags.append("/");
            tags.append(((JSONObject) tagJson.get(i)).getString("tag_name"));
        }
        return tags.toString();
    }

    public static Collection analyzeUgcSeason(JSONObject json) throws JSONException {
        Collection collection = new Collection();
        collection.id = json.optInt("id", -1);
        collection.title = json.optString("title");
        collection.intro = json.optString("intro");
        collection.cover = json.optString("cover");
        collection.mid = json.optLong("mid");
        // stat 缺失时以前直接 getJSONObject 抛异常；播放量只用于显示，降级成 0 即可
        JSONObject seasonStat = json.optJSONObject("stat");
        collection.view = StringUtil.toWan(seasonStat == null ? 0L : seasonStat.optLong("view"));
        JSONArray sections = json.optJSONArray("sections");
        if (sections != null) {
            List<Collection.Section> sectionList = new ArrayList<>();
            for (int i = 0; i < sections.length(); i++) {
                JSONObject sectionJson = sections.getJSONObject(i);
                Collection.Section section = new Collection.Section();
                section.season_id = sectionJson.optInt("season_id", -1);
                section.id = sectionJson.optInt("id", -1);
                section.title = sectionJson.optString("title");
                JSONArray episodes = sectionJson.optJSONArray("episodes");
                if (episodes != null) {
                    List<Collection.Episode> episodeList = new ArrayList<>();
                    for (int j = 0; j < episodes.length(); j++) {
                        JSONObject episodeJson = episodes.getJSONObject(j);
                        Collection.Episode episode = new Collection.Episode();
                        episode.season_id = episodeJson.optInt("season_id", -1);
                        episode.section_id = episodeJson.optInt("section_id", -1);
                        episode.id = episodeJson.optInt("id", -1);
                        episode.aid = episodeJson.optLong("aid", -1);
                        episode.cid = episodeJson.optLong("cid", -1);
                        episode.title = episodeJson.optString("title");
                        JSONObject arc = episodeJson.optJSONObject("arc");
                        if (arc != null) {
                            episode.arc = getInfoByJson(arc);
                        }
                        episode.bvid = episodeJson.optString("bvid");
                        episodeList.add(episode);
                    }
                    section.episodes = episodeList;
                }
                sectionList.add(section);
            }
            collection.sections = sectionList;
        }
        return collection;
    }

    public static VideoInfo getInfoByJson(JSONObject data) throws JSONException {  //项目实在太多qwq 拆就完事了
        VideoInfo videoInfo = new VideoInfo();
        Logu.v("视频信息", "--------");
        videoInfo.title = data.getString("title");
        Logu.v("标题", videoInfo.title);
        videoInfo.cover = data.getString("pic");
        Logu.v("封面", videoInfo.cover);
        if (data.has("desc_v2") && !data.isNull("desc_v2")) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            JSONArray descArray = data.getJSONArray("desc_v2");
            ArrayList<At> ats = new ArrayList<>();
            for (int i = 0; i < descArray.length(); i++) {
                JSONObject curObj = descArray.getJSONObject(i);
                int type = curObj.getInt("type");
                switch (type) {
                    case 2:
                        Pair<Integer, Integer> indexs = StringUtil.appendString(sb, "@" + curObj.getString("raw_text"));
                        ats.add(new At(curObj.getLong("biz_id"), indexs.first, indexs.second));
                        break;
                    default:
                        sb.append(curObj.getString("raw_text"));
                        break;
                }
            }
            videoInfo.description = sb.toString();
            videoInfo.descAts = ats;
        } else {
            videoInfo.description = data.getString("desc");
        }
        Logu.v("简介", videoInfo.description);

        videoInfo.bvid = data.optString("bvid");
        videoInfo.aid = data.getLong("aid");

        videoInfo.timeDesc = TimeUtil.formatDateTimeSec(data.getLong("pubdate") * 1000);
        Logu.v("发布时间", String.valueOf(videoInfo.timeDesc));

        videoInfo.duration = StringUtil.toTime(data.getInt("duration"));
        Logu.v("视频时长", videoInfo.duration);

        if (data.has("copyright") && !data.isNull("copyright"))
            videoInfo.copyright = data.getInt("copyright");

        JSONObject stat = data.getJSONObject("stat");
        Stats stats = new Stats();
        stats.view = stat.getInt("view");
        stats.like = stat.getInt("like");
        stats.coin = stat.getInt("coin");
        stats.reply = stat.getInt("reply");
        stats.danmaku = stat.getInt("danmaku");
        stats.favorite = stat.optInt("favorite", -1);
        stats.coin_limit = (videoInfo.copyright == VideoInfo.COPYRIGHT_REPRINT) ? 1 : 2;
        videoInfo.stats = stats;

        JSONArray pages = data.optJSONArray("pages");
        if (pages != null) {
            ArrayList<String> pagenames = new ArrayList<>();
            ArrayList<Long> cids = new ArrayList<>();
            for (int i = 0; i < pages.length(); i++) {
                JSONObject page = pages.getJSONObject(i);
                // part 只用于显示分P标题：缺字段没必要让整个详情页抛异常
                String pagename = page.optString("part");
                pagenames.add(pagename);
                Logu.v("第" + i + "个视频的标题", pagename);
                long cid = page.getLong("cid");
                cids.add(cid);
                Logu.v("第" + i + "个视频的cid", String.valueOf(cid));
            }
            videoInfo.pagenames = pagenames;
            videoInfo.cids = cids;
        }

        videoInfo.upowerExclusive = data.optBoolean("is_upower_exclusive", true);

        JSONObject rights = data.optJSONObject("rights");
        if (rights != null) {
            videoInfo.isCooperation = (rights.optInt("is_cooperation", 0) == 1);
            videoInfo.isSteinGate = (rights.optInt("is_stein_gate", 0) == 1);
            videoInfo.is360 = (rights.optInt("is_360", 0) == 1);
        }

        ArrayList<UserInfo> staff_list = new ArrayList<>();
        if (videoInfo.isCooperation) { //如果是联合投稿就存储联合UP列表
            // 「is_cooperation=1 但 staff 缺失/为 null」的响应出现过：getJSONArray 会直接抛，
            // 把整个详情页打挂。缺列表按「没有联合 UP」处理，页面本来就有空列表兜底。
            JSONArray staff = data.optJSONArray("staff");
            if (staff != null) {
                for (int i = 0; i < staff.length(); i++) {
                    JSONObject staff_info = staff.optJSONObject(i);
                    if (staff_info == null) continue;
                    UserInfo staff_member = new UserInfo();

                    staff_member.mid = staff_info.optLong("mid");
                    staff_member.sign = staff_info.optString("title"); //卡片的简介用来显示参与的事物
                    staff_member.name = staff_info.optString("name");
                    staff_member.avatar = staff_info.optString("face");
                    staff_member.fans = staff_info.optInt("follower");
                    staff_member.level = 6;
                    staff_member.followed = false;
                    staff_member.notice = "";
                    // official 在部分联合投稿成员上缺失；以前这里两次 getJSONObject 都会抛
                    JSONObject official = staff_info.optJSONObject("official");
                    if (official != null) {
                        staff_member.official = official.optInt("role");
                        staff_member.officialDesc = official.optString("title");
                    }

                    staff_list.add(staff_member);
                }
            }
        } else {
            JSONObject owner = data.optJSONObject("owner");
            if (owner != null) {
                UserInfo userInfo = new UserInfo();
                userInfo.name = owner.optString("name");
                userInfo.avatar = owner.optString("face");
                userInfo.mid = owner.optLong("mid");
                userInfo.sign = "UP主";
                staff_list.add(userInfo);
            }
        }
        videoInfo.staff = staff_list;

        videoInfo.argueMsg = parseArgueMsg(data);

        try {
            if (data.has("redirect_url") && (!data.getString("redirect_url").isEmpty()) && (data.getString("redirect_url").contains("bangumi")))
                videoInfo.epid = Long.parseLong(data.getString("redirect_url").replace("https://www.bilibili.com/bangumi/play/ep", ""));
            else videoInfo.epid = -1;
        } catch (Exception e) {
            e.printStackTrace();
            videoInfo.epid = -1;
        }

        JSONObject ugc_season = data.optJSONObject("ugc_season");
        if (ugc_season != null) {
            videoInfo.collection = analyzeUgcSeason(ugc_season);
        }

        return videoInfo;
    }

    /**
     * 纯解析「争议信息」。
     *
     * <p>原实现是「判了 {@code argue_info} 非空，却用 {@code getString("argue_msg")}」：
     * 服务端出现过「有 {@code argue_info} 对象但缺 {@code argue_msg}」的形态，这时
     * {@code getString} 抛 {@code JSONException}，整页详情直接加载失败（Revival 1.2.5-BETA
     * 修过同一处崩溃）。这里缺字段一律按「没有争议信息」处理，返回空串而不是 null——
     * {@code VideoInfoFragment} 直接对 {@code argueMsg} 调 {@code isNotEmpty()}，null 会 NPE。
     *
     * @return 争议信息正文；无争议信息或字段缺失时返回空串（永不为 null）
     */
    public static String parseArgueMsg(JSONObject data) {
        if (data == null) return "";
        JSONObject argueInfo = data.optJSONObject("argue_info");
        if (argueInfo == null) return "";
        return argueInfo.optString("argue_msg");
    }

    public static String getWatching(long aid, long cid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/player/online/total?aid=" + aid + "&cid=" + cid;
        JSONObject result = NetWorkUtil.getJson(url);
        if (result.has("data") && !result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            if (data.has("total") && !data.isNull("total")) {
                if (data.get("total") instanceof String) return data.getString("total");
                else return StringUtil.toWan(data.getLong("total"));
            }
        }
        return "";
    }

    public static String getVideoConclusion(long aid, String bvid, long cid, long upMid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/view/conclusion/get?aid=" + aid + "&bvid=" + bvid + "&cid=" + cid + "&up_mid=" + upMid;
        JSONObject result = NetWorkUtil.getJson(ConfInfoApi.signWBI(url), NetWorkUtil.webHeaders);
        if (result.has("data") && !result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            int code = data.optInt("code", -1);
            if (code == 0 && data.has("model_result") && !data.isNull("model_result")) {
                JSONObject modelResult = data.getJSONObject("model_result");
                int resultType = modelResult.optInt("result_type", 0);
                if (resultType >= 1) {
                    return modelResult.optString("summary", null);
                }
            }
        }
        return null;
    }

}
