package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.Bangumi;
import com.RobinNotBad.BiliClient.model.FollowedBangumi;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BangumiApi {
    /** 追番列表每页项数，接口定义域 1-30。 */
    private static final int FOLLOW_PAGE_SIZE = 30;
    /** C16 追番更新检查最多翻多少页：追番再多也不该在冷启动时把流量打满。 */
    private static final int FOLLOW_MAX_PAGES = 10;
    /**
     * 追番状态缓存键前缀，完整键 {@code "bangumi_follow_<season_id>"}（boolean）。
     * 只用来让详情页按钮首帧就有大致状态，随后由 {@link #getFollowStatus(long)} 校正。
     * 键里的 id 必须是 <b>season_id</b>：详情页读写都用 season_id，
     * 一头写 media_id、一头读 season_id 会让缓存永远命不中。
     */
    public static final String FOLLOW_KEY_PREFIX = "bangumi_follow_";

    public static int getFollowingList(int page, List<VideoCard> cardList) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/space/bangumi/follow/list?type=1&follow_status=0&pn=" + page
                + "&ps=15&vmid=" + SharedPreferencesUtil.getLong("mid", 0);

        JSONObject all = NetWorkUtil.getJson(url);
        if (all.getInt("code") != 0) throw new JSONException(all.getString("message"));

        JSONObject data = all.getJSONObject("data");
        if (!data.has("list") || data.isNull("list")) return 1;

        JSONArray list = data.getJSONArray("list");
        if (list.length() == 0) return 1;

        for (int i = 0; i < list.length(); i++) {
            JSONObject bangumi = list.getJSONObject(i);
            VideoCard card = new VideoCard();
            card.type = "media_bangumi";
            card.aid = bangumi.getLong("media_id");
            card.title = bangumi.getString("title");
            card.cover = bangumi.getString("cover");
            card.view = StringUtil.toWan(bangumi.getJSONObject("stat").optInt("view"));

            cardList.add(card);
        }
        return 0;
    }

    /**
     * 拉取"我的追番"全部条目，供 C16 的更新检查用（与 {@link #getFollowingList} 是两个用途：
     * 那个是给列表页展示卡片，翻页由 RecyclerView 驱动，字段取舍不同）。
     *
     * 未登录返回空列表；逐页拉到"不满一页"为止，最多 {@link #FOLLOW_MAX_PAGES} 页。
     */
    public static ArrayList<FollowedBangumi> getFollowedBangumi() throws JSONException, IOException {
        ArrayList<FollowedBangumi> result = new ArrayList<>();
        long mid = SharedPreferencesUtil.getLong("mid", 0);
        if (mid == 0) return result;

        for (int page = 1; page <= FOLLOW_MAX_PAGES; page++) {
            String url = "https://api.bilibili.com/x/space/bangumi/follow/list?type=1&follow_status=0&pn=" + page
                    + "&ps=" + FOLLOW_PAGE_SIZE + "&vmid=" + mid;
            ArrayList<FollowedBangumi> pageList = parseFollowingList(NetWorkUtil.getJson(url));
            result.addAll(pageList);
            if (pageList.size() < FOLLOW_PAGE_SIZE) break;
        }
        return result;
    }

    /**
     * 纯解析：把追番列表响应转成 {@link FollowedBangumi} 列表（抽出来是为了能被 JVM 单测覆盖，
     * 参照 {@code HotSearchApi.parseHotSearch} 的做法）。
     */
    public static ArrayList<FollowedBangumi> parseFollowingList(JSONObject all) throws JSONException {
        ArrayList<FollowedBangumi> list = new ArrayList<>();

        int code = all.optInt("code", -1);
        if (code != 0) {
            String message = all.optString("message", "");
            throw new JSONException(message.isEmpty() ? ("错误码：" + code) : message);
        }

        JSONObject data = all.optJSONObject("data");
        if (data == null) return list;

        JSONArray array = data.optJSONArray("list");
        if (array == null) return list;

        for (int i = 0; i < array.length(); i++) {
            JSONObject bangumi = array.optJSONObject(i);
            if (bangumi == null) continue;

            FollowedBangumi item = new FollowedBangumi();
            item.mediaId = bangumi.optLong("media_id", 0);
            item.title = bangumi.optString("title", "");

            JSONObject newEp = bangumi.optJSONObject("new_ep");
            if (newEp != null) {
                item.newEpId = newEp.optLong("id", 0);
                item.newEpIndexShow = newEp.optString("index_show", "");
            }

            // 没有 media_id 的项无法参与快照比对，直接跳过（不污染快照）
            if (item.mediaId == 0) continue;

            list.add(item);
        }
        return list;
    }


    /**
     * 查询某一季的追番状态。
     *
     * <p>用 {@code season_id} 而不是 {@code media_id} 查询：一部番的多季是各自独立的追番对象，
     * 只看 media_id 会把"追了第二季没追第一季"混成一个状态。
     *
     * @return true 表示已追番
     */
    public static boolean getFollowStatus(long seasonId) throws IOException, JSONException {
        String url = "https://api.bilibili.com/pgc/view/web/season/user/status?season_id=" + seasonId;
        return parseFollowStatus(NetWorkUtil.getJson(url));
    }

    /**
     * 纯解析：user/status 响应里 {@code result.follow == 1} 即已追番（抽出来给 JVM 单测覆盖）。
     * 错误码一律按"未追番"处理：调用方只用它校正按钮显示，失败时保持原状更安全。
     */
    public static boolean parseFollowStatus(JSONObject all) {
        if (all == null || all.optInt("code", -1) != 0) return false;
        JSONObject result = all.optJSONObject("result");
        return result != null && result.optInt("follow", 0) == 1;
    }

    /**
     * 追番 / 取消追番（{@code pgc/web/follow/add|del}）。
     *
     * @param follow true 追番，false 取消
     * @return 服务端 code，0 为成功
     */
    public static int setFollow(long seasonId, boolean follow) throws IOException, JSONException {
        String url = follow
                ? "https://api.bilibili.com/pgc/web/follow/add"
                : "https://api.bilibili.com/pgc/web/follow/del";
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("season_id", seasonId)
                .put("csrf", NetWorkUtil.currentCsrf());
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders).body()).string());
        return result.optInt("code", -1);
    }


    //获取番剧信息, 详情页需要有基本的cover, 信息等
    public static Bangumi getBangumi(long mediaId) throws JSONException, IOException {
        Bangumi bangumi = new Bangumi();
        bangumi.info = getInfo(mediaId);
        bangumi.sectionList = getSections(bangumi.info.season_id);
        return bangumi;
    }

    public static Long getMdidFromEpid(long epid) {
        try {
            String url = "https://api.bilibili.com/pgc/view/web/season?ep_id=" + epid;
            JSONObject all = NetWorkUtil.getJson(url);

            int code = all.getInt("code");
            if (code != 0) return 0L;

            return all.getJSONObject("result").getLong("media_id");
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public static Bangumi.Info getInfo(long mediaId) throws IOException, JSONException {
        // 先获取基本信息
        String url1 = "https://api.bilibili.com/pgc/review/user?media_id=" + mediaId;
        JSONObject all1 = NetWorkUtil.getJson(url1);

        int code1 = all1.getInt("code");
        if (code1 != 0) {
            throw new JSONException("错误码：" + code1);
        }

        JSONObject media = all1.getJSONObject("result").getJSONObject("media");

        Bangumi.Info info = new Bangumi.Info();
        info.media_id = media.getLong("media_id");
        info.season_id = media.getLong("season_id");
        info.title = media.getString("title");
        info.cover = media.getString("cover");
        info.cover_horizontal = media.getString("horizontal_picture");
        info.type = media.getInt("type");
        info.type_name = media.getString("type_name");

        JSONObject new_ep = media.optJSONObject("new_ep");
        if (new_ep != null) {
            info.indexShow = new_ep.optString("index_show", "敬请期待");
            info.newEpDesc = new_ep.optString("desc", "");
        }

        JSONObject rating = media.optJSONObject("rating");
        if (rating != null) {
            info.count = rating.optInt("count");
            info.score = (float) rating.optDouble("score", 0);
        }

        JSONArray areas = media.getJSONArray("areas");
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < areas.length(); i++) {
            stringBuilder.append(areas.getJSONObject(i).getString("name"));
            if (i < areas.length() - 1) {
                stringBuilder.append(" | ");
            }
        }
        info.area_name = stringBuilder.toString();

        // 获取详细信息
        String url2 = "https://api.bilibili.com/pgc/view/web/season?season_id=" + info.season_id;
        JSONObject all2 = NetWorkUtil.getJson(url2);

        int code2 = all2.getInt("code");
        if (code2 == 0) {
            JSONObject result = all2.getJSONObject("result");

            info.evaluate = result.optString("evaluate", "");
            info.staff = result.optString("staff", "");
            // 角色/声优是"角色：CV"多行文本（不是数组），角色页的第二级数据源
            info.actors = result.optString("actors", "");
            info.record = result.optString("record", "");
            info.subtitle = result.optString("subtitle", "");

            // 更新状态文案（"已完结，全12话"这种）优先用季详情页的 new_ep.desc：
            // review/user 里的 new_ep 只有 index_show，desc 常常是空的
            JSONObject seasonNewEp = result.optJSONObject("new_ep");
            if (seasonNewEp != null) {
                String desc = seasonNewEp.optString("desc", "");
                if (!desc.isEmpty()) info.newEpDesc = desc;
            }

            // 发布时间信息
            JSONObject publish = result.optJSONObject("publish");
            if (publish != null) {
                info.publish = new Bangumi.Publish();
                info.publish.is_finish = publish.optInt("is_finish", 0);
                info.publish.is_started = publish.optInt("is_started", 0);
                info.publish.pub_time = publish.optString("pub_time", "");
                info.publish.pub_time_show = publish.optString("pub_time_show", "");
            }

            // 标签
            JSONArray styles = result.optJSONArray("styles");
            if (styles != null) {
                info.styles = new ArrayList<>();
                for (int i = 0; i < styles.length(); i++) {
                    info.styles.add(styles.getString(i));
                }
            }

            // 状态数
            JSONObject stat = result.optJSONObject("stat");
            if (stat != null) {
                info.stat = new Bangumi.Stat();
                info.stat.favorites = stat.optInt("favorites", 0);
                info.stat.series_follow = stat.optInt("series_follow", 0);
                info.stat.views = stat.optInt("views", 0);
                info.stat.vt = stat.optInt("vt", 0);
            }

            // UP主信息
            JSONObject upInfo = result.optJSONObject("up_info");
            if (upInfo != null) {
                info.up_info = new Bangumi.UpInfo();
                info.up_info.mid = upInfo.optLong("mid", 0);
                info.up_info.name = upInfo.optString("name", "");
                info.up_info.avatar = upInfo.optString("avatar", "");
            }

            // 系列信息
            JSONObject series = result.optJSONObject("series");
            if (series != null) {
                info.series = new Bangumi.Series();
                info.series.series_id = series.optLong("series_id", 0);
                info.series.series_title = series.optString("series_title", "");
            }

            // 同系列所有季信息
            JSONArray seasons = result.optJSONArray("seasons");
            if (seasons != null) {
                info.seasons = new ArrayList<>();
                for (int i = 0; i < seasons.length(); i++) {
                    JSONObject seasonObj = seasons.getJSONObject(i);
                    Bangumi.Season season = new Bangumi.Season();
                    season.media_id = seasonObj.optLong("media_id", 0);
                    season.season_id = seasonObj.optLong("season_id", 0);
                    season.season_title = seasonObj.optString("season_title", "");
                    season.cover = seasonObj.optString("cover", "");
                    season.badge = seasonObj.optString("badge", "");
                    info.seasons.add(season);
                }
            }
        }

        return info;
    }

    /**
     * 某一季的剧集数据，供番剧详情页的"季 tab"页内切季使用。
     *
     * <p>季与季之间的集列表/类型/状态文案都不同，所以三者要作为一个整体传递，
     * 别让调用方自己去拼（拼出来的半更新状态最容易让选集下标越界）。
     */
    public static class SeasonMeta {
        public ArrayList<Bangumi.Section> sectionList = new ArrayList<>();
        /** 季类型（心跳上报的 sub_type）；0 表示没取到，调用方要回退 {@link Bangumi.Info#type} */
        public int seasonType;
        /** 该季的选集状态文案（new_ep.desc，如"已完结，全12话"），可能为空 */
        public String statusDesc = "";
    }

    /**
     * 拉取任意一季的剧集数据（番剧详情页切季用）。
     *
     * <p>走 {@code pgc/view/web/season} 而不是轻量的 {@code pgc/web/season/section}：
     * 同一个请求就同时带回正片集列表（{@code result.episodes}）、附加分区（{@code result.section}）、
     * 季类型（{@code result.type}）与状态文案（{@code new_ep.desc}）。
     */
    public static SeasonMeta getSeasonDetail(long seasonId) throws IOException, JSONException {
        String url = "https://api.bilibili.com/pgc/view/web/season?season_id=" + seasonId;
        JSONObject all = NetWorkUtil.getJson(url);
        int code = all.optInt("code", -1);
        if (code != 0) throw new JSONException("错误码：" + code);
        return parseSeasonDetail(all.getJSONObject("result"));
    }

    /**
     * 纯解析：把 {@code pgc/view/web/season} 的 {@code result} 转成 {@link SeasonMeta}
     * （抽出来是为了能被 JVM 单测覆盖，参照 {@code HotSearchApi.parseHotSearch} 的做法）。
     *
     * <p>正片（{@code episodes}）在这个响应里是平铺数组、没有 section 外壳，
     * 这里补一个标题为"正片"的分区，让详情页能用统一的"分区列表"结构消费。
     */
    public static SeasonMeta parseSeasonDetail(JSONObject result) throws JSONException {
        SeasonMeta meta = new SeasonMeta();
        if (result == null) return meta;

        meta.seasonType = result.optInt("type", 0);
        JSONObject newEp = result.optJSONObject("new_ep");
        if (newEp != null) meta.statusDesc = newEp.optString("desc", "");

        JSONArray episodes = result.optJSONArray("episodes");
        if (episodes != null && episodes.length() > 0) {
            Bangumi.Section main = new Bangumi.Section();
            main.title = "正片";
            main.episodeList = new ArrayList<>();
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject episode = episodes.optJSONObject(i);
                if (episode != null) main.episodeList.add(analyzeEpisode(episode));
            }
            meta.sectionList.add(main);
        }

        JSONArray sections = result.optJSONArray("section");
        if (sections != null) {
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.optJSONObject(i);
                if (section != null) meta.sectionList.add(analyzeSection(section));
            }
        }

        return meta;
    }

    /**
     * 单个剧集的播放入口信息（历史番剧卡片一键续播用）。
     */
    public static class EpisodeResult {
        public Bangumi.Episode episode;
        /** 该剧集所属季 id（心跳上报的 sid） */
        public long seasonId;
        /** 该季类型（心跳上报的 sub_type）；0 表示没取到 */
        public int seasonType;
    }

    /**
     * 用 epid 反查剧集的播放数据（aid/cid/所属季）。
     *
     * <p>历史记录里的番剧卡片只有 epid，没有 aid/cid，也没有所属季，
     * 不补齐这三个值就没法直接起播：播放器要 aid/cid 取播放地址，进度心跳要 sid/sub_type 才记成番剧记录。
     */
    public static EpisodeResult getEpisodeFromEpid(long epid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/pgc/view/web/season?ep_id=" + epid;
        JSONObject all = NetWorkUtil.getJson(url);
        int code = all.optInt("code", -1);
        if (code != 0) throw new JSONException("错误码：" + code);
        return parseEpisodeResult(all.getJSONObject("result"), epid);
    }

    /**
     * 纯解析：在季详情的 {@code result} 里找出 epid 对应的那一集（抽出来是为了能被 JVM 单测覆盖）。
     *
     * <p>只解析命中的那一条，不整季解析：附加分区里偶尔有字段不全的集（缺 badge/long_title），
     * 整季解析会因为它抛异常，把本来能播的这一集一起拖死。
     *
     * <p>找不到时抛 {@link JSONException} 而不是返回 null——调用方只能回退到详情页，
     * 静默返回 null 会让上层把空播放数据交给播放器。
     */
    public static EpisodeResult parseEpisodeResult(JSONObject result, long epid) throws JSONException {
        if (result == null) throw new JSONException("获取剧集信息失败");

        JSONObject target = findEpisodeJson(result.optJSONArray("episodes"), epid);
        if (target == null) {
            JSONArray sections = result.optJSONArray("section");
            if (sections != null) {
                for (int i = 0; i < sections.length() && target == null; i++) {
                    JSONObject section = sections.optJSONObject(i);
                    if (section != null) target = findEpisodeJson(section.optJSONArray("episodes"), epid);
                }
            }
        }
        if (target == null) throw new JSONException("剧集中未找到 epid=" + epid);

        EpisodeResult episodeResult = new EpisodeResult();
        episodeResult.episode = analyzeEpisode(target);
        episodeResult.seasonId = result.optLong("season_id", 0);
        episodeResult.seasonType = result.optInt("type", 0);
        return episodeResult;
    }

    private static JSONObject findEpisodeJson(JSONArray episodes, long epid) {
        if (episodes == null) return null;
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject episode = episodes.optJSONObject(i);
            if (episode != null && episode.optLong("id", 0) == epid) return episode;
        }
        return null;
    }

    public static ArrayList<Bangumi.Section> getSections(long season_id) throws IOException, JSONException {
        String url = "https://api.bilibili.com/pgc/web/season/section?season_id=" + season_id;
        JSONObject all = new JSONObject(Objects.requireNonNull(Objects.requireNonNull(NetWorkUtil.get(url)).body()).string());  //得到一整个json
        JSONObject result = all.getJSONObject("result");
        ArrayList<Bangumi.Section> sectionList = new ArrayList<>();

        JSONObject main_section = result.optJSONObject("main_section");
        if (main_section != null)
            sectionList.add(analyzeSection(result.getJSONObject("main_section")));

        JSONArray other_sections = result.optJSONArray("section");
        if (other_sections != null) {
            for (int i = 0; i < other_sections.length(); i++) {
                sectionList.add(analyzeSection(other_sections.getJSONObject(i)));
            }
        }

        return sectionList;
    }

    public static Bangumi.Section analyzeSection(JSONObject jsonObject) throws JSONException {
        Bangumi.Section section = new Bangumi.Section();
        section.id = jsonObject.getLong("id");
        section.title = jsonObject.getString("title");
        section.type = jsonObject.getInt("type");

        JSONArray episodes = jsonObject.getJSONArray("episodes");
        ArrayList<Bangumi.Episode> episodeList = new ArrayList<>();
        for (int i = 0; i < episodes.length(); i++) {
            episodeList.add(analyzeEpisode(episodes.getJSONObject(i)));
        }
        section.episodeList = episodeList;

        return section;
    }

    public static Bangumi.Episode analyzeEpisode(JSONObject jsonObject) throws JSONException {
        Bangumi.Episode episode = new Bangumi.Episode();
        episode.id = jsonObject.getLong("id");
        episode.aid = jsonObject.getLong("aid");
        episode.cid = jsonObject.getLong("cid");
        episode.cover = jsonObject.getString("cover");
        episode.badge = jsonObject.getString("badge");
        episode.title = jsonObject.getString("title");
        episode.title_long = jsonObject.getString("long_title");

        return episode;
    }
}

