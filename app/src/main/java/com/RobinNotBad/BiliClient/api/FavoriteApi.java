package com.RobinNotBad.BiliClient.api;

import android.util.Log;
import android.util.Pair;

import com.RobinNotBad.BiliClient.model.Collection;
import com.RobinNotBad.BiliClient.model.FavoriteFolder;
import com.RobinNotBad.BiliClient.model.Opus;
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

//收藏API

public class FavoriteApi {
    // TODO 合集收藏

    /** 排序：按收藏时间（x/v3/fav/resource/list 的 order 参数） */
    public static final String ORDER_FAV_TIME = "mtime";
    /** 排序：按播放量 */
    public static final String ORDER_VIEW = "view";
    /** 排序：按投稿时间 */
    public static final String ORDER_PUBTIME = "pubtime";

    /** 默认收藏夹的视频上限（v3 接口不返回上限，按服务端约定） */
    public static final int MAX_COUNT_DEFAULT_FOLDER = 50000;
    /** 用户自建收藏夹的视频上限（v3 接口不返回上限，按服务端约定） */
    public static final int MAX_COUNT_CUSTOM_FOLDER = 1000;

    /** 收藏内容类型：视频稿件。resources 拼成 {@code av:2}，内容 id 是 avid */
    public static final int FAV_TYPE_VIDEO = 2;
    /** 收藏内容类型：音频。resources 拼成 {@code au:12}，内容 id 是 auid */
    public static final int FAV_TYPE_AUDIO = 12;
    /** 收藏内容类型：视频合集。resources 拼成 {@code ss:21}，内容 id 是合集 id */
    public static final int FAV_TYPE_COLLECTION = 21;

    /**
     * 只认 2/12/21 三种收藏内容类型，其它（含 0、缺失、脏数据）一律按视频稿件 2 处理。
     *
     * <p>类型定义见 {@code bilibili-API/docs/fav/action.md}（resources 参数：
     * {@code {内容id}:{内容类型}}，2=视频稿件 / 12=音频 / 21=视频合集）。
     * 拼 resources 前必须过一遍这个函数：类型值写错不会报错，只会把音频 auid、
     * 合集 id 当成视频 avid 发过去，服务端静默按错误内容处理。
     *
     * <p>纯函数，方便单测。
     */
    public static int normalizeFavType(int type) {
        if (type == FAV_TYPE_AUDIO || type == FAV_TYPE_COLLECTION) return type;
        return FAV_TYPE_VIDEO;
    }

    /**
     * 收藏夹的默认标记：{@code attr} 二进制第 1 位为 0 表示默认收藏夹。
     *
     * <p>v3 接口（created/list、list-all）只给 {@code attr}，没有 getBoxList 的 {@code fav_box=0}
     * 可用；纯函数，供单测直接调用。
     */
    public static boolean isDefaultFolder(int attr) {
        return (attr & 2) == 0;
    }

    /**
     * 收藏夹是否私密：{@code attr} 二进制第 0 位为 1 表示私有收藏夹。
     *
     * <p>v3 接口没有独立的 {@code privacy} 字段，位定义见
     * {@code bilibili-API/docs/fav/info.md} 的「attr 属性位二进制值表」（bit0：0 公开 / 1 私有，
     * bit1：0 默认 / 1 其他）。该位表写在 created/list-all 小节，folder/info 只标注 attr 为
     * 「属性位」，两者是同一个 attr 字段；按同一语义解析，不依赖未文档化的 privacy 字段
     * （字段缺失时会被当成 0=公开，反而把私密夹误判成公开）。
     *
     * <p>纯函数，供单测直接调用。
     */
    public static boolean isPrivateFolder(int attr) {
        return (attr & 1) != 0;
    }

    /** 收藏夹视频上限：默认 50000，自建 1000（v3 接口不返回 max_count） */
    public static int maxCountOf(boolean isDefault) {
        return isDefault ? MAX_COUNT_DEFAULT_FOLDER : MAX_COUNT_CUSTOM_FOLDER;
    }

    /**
     * 获取某个用户（自己或他人）的收藏夹列表。
     *
     * <p>数据源已从 {@code space.bilibili.com/ajax/fav/getBoxList} 迁到
     * {@code x/v3/fav/folder/created/list}：v3 直接返回 {@code id}（即 media_id）与封面，
     * 不再需要「老接口 + list-all 两次请求拼 media_id」；按 {@code has_more} 分页拉全。
     *
     * @param mid 目标用户 mid（自己取本地保存的 mid）
     */
    public static ArrayList<FavoriteFolder> getFavoriteFolders(long mid) throws IOException, JSONException {
        ArrayList<FavoriteFolder> folderList = new ArrayList<>();
        int pn = 1;
        while (true) {
            String url = "https://api.bilibili.com/x/v3/fav/folder/created/list" + new NetWorkUtil.FormData()
                    .setUrlParam(true)
                    .put("up_mid", mid)
                    .put("pn", pn)
                    .put("ps", 100);
            JSONObject result = NetWorkUtil.getJson(url);
            if (result.optInt("code", -1) != 0) break;
            JSONObject data = result.optJSONObject("data");
            if (data == null) break;
            JSONArray list = data.optJSONArray("list");
            if (list == null || list.length() == 0) break;
            for (int i = 0; i < list.length(); i++) {
                JSONObject folder = list.getJSONObject(i);

                FavoriteFolder favoriteFolder = new FavoriteFolder();

                favoriteFolder.id = folder.optLong("fid", 0);
                // v3 直接给 media_id，不再按「fid + mid 后两位」手拼
                favoriteFolder.mediaId = folder.optLong("id", favoriteFolder.id);
                favoriteFolder.name = folder.optString("title");
                favoriteFolder.cover = folder.optString("cover", "");
                favoriteFolder.videoCount = folder.optInt("media_count", 0);
                favoriteFolder.isDefault = isDefaultFolder(folder.optInt("attr", 0));
                favoriteFolder.maxCount = maxCountOf(favoriteFolder.isDefault);

                Log.e("debug-收藏夹ID", String.valueOf(favoriteFolder.id));
                Log.e("debug-收藏夹mediaId", String.valueOf(favoriteFolder.mediaId));
                Log.e("debug-收藏夹名称", favoriteFolder.name);
                Log.e("debug-收藏夹封面", favoriteFolder.cover);
                Log.e("debug-收藏夹视频数量", String.valueOf(favoriteFolder.videoCount));
                Log.e("debug-收藏夹视频上限", String.valueOf(favoriteFolder.maxCount));
                Log.e("debug-收藏夹", "----------------");
                folderList.add(favoriteFolder);
            }
            if (!data.optBoolean("has_more", false)) break;
            pn++;
        }
        return folderList;
    }

    /**
     * 获取指定用户创建的所有公开收藏夹（自动过滤私密收藏夹）
     * 使用 x/v3/fav/folder/created/list 分页接口，可拿到封面（list-all 无封面字段）
     *
     * @param mid 目标用户mid
     * @return 公开收藏夹列表（含mediaId与封面），私密收藏夹已被跳过
     */
    public static ArrayList<FavoriteFolder> getUserFavoriteFolders(long mid) throws IOException, JSONException {
        ArrayList<FavoriteFolder> folderList = new ArrayList<>();
        int pn = 1;
        boolean hasMore = true;
        while (hasMore) {
            String url = "https://api.bilibili.com/x/v3/fav/folder/created/list" + new NetWorkUtil.FormData()
                    .setUrlParam(true)
                    .put("up_mid", mid)
                    .put("pn", pn)
                    .put("ps", 100);
            JSONObject result = NetWorkUtil.getJson(url);
            if (result.optInt("code", -1) != 0) break;
            JSONObject data = result.optJSONObject("data");
            if (data == null) break;
            JSONArray list = data.optJSONArray("list");
            if (list == null || list.length() == 0) break;
            for (int i = 0; i < list.length(); i++) {
                JSONObject folder = list.getJSONObject(i);
                int attr = folder.optInt("attr", 0);
                // attr 二进制第 0 位：1=私有收藏夹。他人视角直接跳过，等价于"自动处理私密收藏夹"
                if ((attr & 1) != 0) continue;
                FavoriteFolder favoriteFolder = new FavoriteFolder();
                favoriteFolder.id = folder.optLong("fid", 0);        // 原始id
                favoriteFolder.mediaId = folder.optLong("id", 0);    // 完整id（供 x/v3/fav/resource/list 使用）
                favoriteFolder.name = folder.optString("title");
                favoriteFolder.cover = folder.optString("cover", "");
                favoriteFolder.videoCount = folder.optInt("media_count", 0);
                favoriteFolder.maxCount = 0;                          // 他人收藏夹无上限概念
                favoriteFolder.isDefault = isDefaultFolder(attr);     // attr 位1：0=默认收藏夹
                folderList.add(favoriteFolder);
            }
            hasMore = data.optBoolean("has_more", false);
            pn++;
        }
        return folderList;
    }

    /**
     * 解析 {@code x/v3/fav/folder/info} 的响应（纯函数，供单测直接调用）。
     *
     * <p>只取编辑页需要预填的 title/intro/privacy，以及详情页要用的 id/mediaId/media_count/attr。
     * 字段依据：{@code bilibili-API/docs/fav/info.md} 的 data 对象表（`:31-51`，含
     * attr/title/intro/media_count，<b>没有 privacy 字段</b>）；privacy 由 attr 的 bit0 解析
     * （同文档「attr 属性位二进制值表」，`:185-191`）。
     *
     * @param result 接口根对象（含 code/message/data）
     * @return 解析结果（intro 缺失时为空串，privacy 只会是 0/1）
     * @throws JSONException code!=0、data 为空或字段类型异常时抛出——调用方不得用默认值兜底覆盖
     */
    public static FavoriteFolder parseFolderInfo(JSONObject result) throws JSONException {
        if (result == null) throw new JSONException("收藏夹信息为空");
        int code = result.optInt("code", -1);
        if (code != 0) {
            String message = result.optString("message", "");
            throw new JSONException("获取收藏夹信息失败 code=" + code
                    + (message.isEmpty() ? "" : " message=" + message));
        }
        JSONObject data = result.optJSONObject("data");
        if (data == null) throw new JSONException("收藏夹信息为空：data=null");

        int attr = data.optInt("attr", 0);
        FavoriteFolder folder = new FavoriteFolder();
        folder.id = data.optLong("fid", 0);
        // v3 的 data.id 就是 media_id（mlid），优先用它；缺省时退回 fid
        folder.mediaId = data.optLong("id", folder.id);
        folder.name = data.optString("title", "");
        folder.cover = data.optString("cover", "");
        folder.intro = data.optString("intro", "");
        folder.videoCount = data.optInt("media_count", 0);
        folder.isDefault = isDefaultFolder(attr);
        folder.maxCount = maxCountOf(folder.isDefault);
        folder.privacy = isPrivateFolder(attr) ? 1 : 0;
        return folder;
    }

    /**
     * 获取收藏夹详情：编辑页用它预填标题/简介/隐私，避免拿列表数据（或 Intent）里的空值覆盖服务端数据。
     *
     * <p>接口：{@code GET https://api.bilibili.com/x/v3/fav/folder/info?media_id=}，
     * 依据 {@code bilibili-API/docs/fav/info.md:5-51}；Cookie 认证，查询私密收藏夹必须本人登录（`:13`）。
     *
     * @param mediaId 收藏夹完整 id（media_id）
     * @throws JSONException code!=0（如 11010 内容不存在、-403 权限不足）时抛出，调用方应禁用保存并提示
     */
    public static FavoriteFolder getFolderInfo(long mediaId) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/info" + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("media_id", mediaId);
        return parseFolderInfo(NetWorkUtil.getJson(url));
    }

    /**
     * 使用新接口获取收藏夹内容（支持他人收藏夹的 media_id，替代已停用的 x/space/fav/arc）
     *
     * @param mediaId   收藏夹完整id（media_id）
     * @param page      页码（从1开始）
     * @param videoList 输出列表
     * @return 0：成功 1：已到底 -1：失败
     */
    public static int getFolderVideosNew(long mediaId, int page, ArrayList<VideoCard> videoList) throws IOException, JSONException {
        return getFolderVideosNew(mediaId, page, videoList, ORDER_FAV_TIME);
    }

    /**
     * 带排序的收藏夹内容（新接口）。
     *
     * @param order {@link #ORDER_FAV_TIME} / {@link #ORDER_VIEW} / {@link #ORDER_PUBTIME}
     */
    public static int getFolderVideosNew(long mediaId, int page, ArrayList<VideoCard> videoList, String order) throws IOException, JSONException {
        return getFolderVideosNew(mediaId, page, videoList, order, null);
    }

    /**
     * 带排序 + 服务端关键词搜索的收藏夹内容（新接口）。
     *
     * <p>搜索走服务端 {@code keyword} 参数（依据 {@code bilibili-API/docs/fav/list.md:19}，
     * 该参数标注「非必要」；实测标题与 UP 名都参与匹配、可与 order 同时生效、分页正常），
     * 因此调用方不需要再自己过滤一遍。空串/null 等同不传；中文由
     * {@link NetWorkUtil.FormData} 统一 URL encode，安全。
     *
     * @param keyword 搜索关键词，null/空串表示不过滤
     * @return 0：成功 1：已到底（含无匹配结果） -1：失败
     */
    public static int getFolderVideosNew(long mediaId, int page, ArrayList<VideoCard> videoList, String order, String keyword) throws IOException, JSONException {
        FavMediaPage result = fetchFolderMedias(mediaId, page, order, keyword);
        if (result.code < 0) return -1;
        for (FavMedia media : result.items) videoList.add(media.card);
        return result.code;
    }

    /**
     * 收藏夹内容条目：视频卡 + 收藏时间。
     *
     * <p>{@link VideoCard} 没有收藏时间字段，而跨夹「全部收藏」聚合要按最近收藏排序，
     * 所以这里包一层。{@code favTimeSec} 来自 {@code medias[].fav_time}（**单位秒**，
     * 接口未下发时为 0），只在聚合页参与排序，不影响普通列表。
     */
    public static final class FavMedia {
        public final VideoCard card;
        public final long favTimeSec;

        public FavMedia(VideoCard card, long favTimeSec) {
            this.card = card;
            this.favTimeSec = favTimeSec;
        }
    }

    /**
     * 收藏夹一页内容 + 返回码，供聚合页复用（{@link FavMediaPage#code} 语义同
     * {@link #getFolderVideosNew(long, int, ArrayList)}：0 成功 / 1 已到底或无内容 / -1 失败）。
     */
    public static final class FavMediaPage {
        public final int code;
        public final ArrayList<FavMedia> items;

        FavMediaPage(int code, ArrayList<FavMedia> items) {
            this.code = code;
            this.items = items;
        }
    }

    /**
     * 拉取收藏夹一页内容（带收藏时间），供跨夹聚合与搜索复用。
     *
     * <p>依据 {@code bilibili-API/docs/fav/list.md:5-21}：
     * {@code media_id}/{@code platform}/{@code pn}/{@code ps}/{@code order}/{@code keyword}。
     * 注意实测的一个服务端怪癖：不带 keyword 时 order=mtime 返回升序、带 keyword 时返回降序，
     * 所以客户端不要依赖 mtime 的方向，本地排序只在聚合页自己做。
     */
    public static FavMediaPage fetchFolderMedias(long mediaId, int page, String order, String keyword) throws IOException, JSONException {
        NetWorkUtil.FormData params = new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("media_id", mediaId)
                .put("platform", "web")
                .put("pn", page)
                .put("ps", 20)
                .put("order", order == null ? ORDER_FAV_TIME : order);
        if (keyword != null && !keyword.isEmpty()) params.put("keyword", keyword);
        String url = "https://api.bilibili.com/x/v3/fav/resource/list" + params;
        JSONObject result = NetWorkUtil.getJson(url);
        if (result.optInt("code", -1) != 0) return new FavMediaPage(-1, new ArrayList<>());
        JSONObject data = result.optJSONObject("data");
        if (data == null) return new FavMediaPage(-1, new ArrayList<>());
        ArrayList<FavMedia> items = new ArrayList<>();
        JSONArray medias = data.optJSONArray("medias");
        if (medias == null || medias.length() == 0) return new FavMediaPage(1, items);
        for (int i = 0; i < medias.length(); i++) {
            JSONObject video = medias.getJSONObject(i);
            String title = video.optString("title");
            String cover = video.optString("cover");
            long aid = video.optLong("id", 0);

            String upName = "";
            JSONObject upper = video.optJSONObject("upper");
            if (upper != null) upName = upper.optString("name");

            String view = "";
            JSONObject cntInfo = video.optJSONObject("cnt_info");
            if (cntInfo != null) {
                view = StringUtil.toWan(cntInfo.optLong("play", 0)) + "观看";
            }
            VideoCard card = new VideoCard(title, upName, view, cover, aid, video.optString("bvid", ""));
            // id 可能是 avid / auid / 合集 id，类型决定后续批量操作怎么拼 resources
            card.favType = normalizeFavType(video.optInt("type", FAV_TYPE_VIDEO));
            // fav_time 是收藏时间（秒）；聚合页按它排序，普通列表不读
            items.add(new FavMedia(card, video.optLong("fav_time", 0)));
        }
        return new FavMediaPage(data.optBoolean("has_more", false) ? 0 : 1, items);
    }

    /**
     * 获取收藏的合集
     *
     * @param mid            目标用户
     * @param page           页数
     * @param collectionList collection对象List
     * @return 返回码与has_more
     */
    public static Pair<Integer, Boolean> getFavoritedCollections(long mid, int page, List<Collection> collectionList) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/collected/list" + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("platform", "web")
                .put("up_mid", mid)
                .put("pn", page)
                .put("ps", 10);
        JSONObject result = NetWorkUtil.getJson(url);
        JSONObject data = result.optJSONObject("data");
        boolean has_more = false;
        if (data != null) {
            has_more = data.optBoolean("has_more", false);
            JSONArray list = data.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    Collection collection = new Collection();
                    collection.id = item.optInt("id", -1);
                    collection.mid = item.optLong("mid", -1);
                    collection.title = item.optString("title");
                    collection.cover = item.optString("cover");
                    collection.intro = item.optString("intro");
                    collection.view = StringUtil.toWan(item.optInt("view_count", -1));
                    collectionList.add(collection);
                }
            }
        }
        return new Pair<>(result.optInt("code", -1), has_more);
    }

    public static boolean getFavouriteOpus(ArrayList<Opus> list, int page) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/opus/feed/fav" + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("page_size", 20)
                .put("page", page);
        JSONObject result = NetWorkUtil.getJson(url);
        JSONObject data = result.optJSONObject("data");
        // 新接口在部分账号/风控下会返回非 0，回退旧 favlist（旧接口 page_size 上限 10）
        if (result.optInt("code", -1) != 0 || data == null) return getFavouriteOpusLegacy(list, page);

        boolean hasMore = data.optBoolean("has_more", false);
        JSONArray items = data.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                Opus opus = new Opus();
                opus.content = item.optString("content");
                opus.cover = parseOpusCover(item);
                opus.title = item.optString("title");
                if (opus.title.isEmpty()) opus.title = opus.content;
                String opusId = item.optString("opus_id", item.optString("id", ""));
                try {
                    opus.id = Long.parseLong(opusId);
                } catch (NumberFormatException e) {
                    continue;   // 没有 id 的脏条目跳过，别让整页失败
                }
                opus.pubTime = item.optString("pub_time", item.optString("time_text", ""));
                list.add(opus);
            }
        }
        return hasMore;
    }

    /** 图文封面：新接口给 {@code {"url": ...}} 对象，旧接口给字符串，两代都兼容 */
    private static String parseOpusCover(JSONObject item) {
        JSONObject cover = item.optJSONObject("cover");
        return cover != null ? cover.optString("url", "") : item.optString("cover", "");
    }

    /** 旧图文收藏接口（{@code opus/favlist}），仅在新接口不可用时兜底 */
    private static boolean getFavouriteOpusLegacy(ArrayList<Opus> list, int page) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/opus/favlist?page_size=10&page=" + page;
        JSONObject result = NetWorkUtil.getJson(url);
        JSONObject data = result.optJSONObject("data");
        if (data == null) return false;
        JSONArray items = data.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                Opus opus = new Opus();
                opus.content = item.optString("content");
                opus.cover = parseOpusCover(item);
                opus.title = item.optString("title");
                if (opus.title.isEmpty()) opus.title = opus.content;
                try {
                    opus.id = Long.parseLong(item.optString("opus_id", ""));
                } catch (NumberFormatException e) {
                    continue;
                }
                opus.pubTime = item.optString("time_text", item.optString("pub_time", ""));
                list.add(opus);
            }
        }
        return data.optBoolean("has_more", false);
    }

    /**
     * 查询「这个视频被收藏在哪些收藏夹里」。
     *
     * @param mediaIdList 与服务端 {@code list} 一一对应的 media_id：点选后的写操作要用它，v3 不再手拼
     */
    public static void getFavoriteState(long aid, ArrayList<String> folderList, ArrayList<Long> fidList, ArrayList<Long> mediaIdList, ArrayList<Boolean> stateList, ArrayList<Integer> countList, ArrayList<Integer> maxCountList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?type=2&jsonp=jsonp&rid=" + aid + "&up_mid=" + SharedPreferencesUtil.getLong("mid", 0);
        JSONObject result = NetWorkUtil.getJson(url);
        JSONObject data = result.getJSONObject("data");
        parseFavoriteState(data, folderList, fidList, mediaIdList, stateList, countList, maxCountList);
    }

    public static void parseFavoriteState(JSONObject data, ArrayList<String> folderList, ArrayList<Long> fidList, ArrayList<Long> mediaIdList, ArrayList<Boolean> stateList, ArrayList<Integer> countList, ArrayList<Integer> maxCountList) throws JSONException {
        if (data == null) return;
        if (!data.has("list") || data.isNull("list")) return;
        JSONArray list = data.getJSONArray("list");
        for (int i = 0; i < list.length(); i++) {
            JSONObject folder = list.getJSONObject(i);
            folderList.add(folder.getString("title"));
            fidList.add(folder.getLong("fid"));
            mediaIdList.add(folder.optLong("id", 0));
            stateList.add(folder.getInt("fav_state") == 1);
            countList.add(folder.optInt("media_count", 0));
            // attr 缺失时退回老判据「第一条是默认收藏夹」
            int attr = folder.optInt("attr", -1);
            maxCountList.add(maxCountOf(attr >= 0 ? isDefaultFolder(attr) : i == 0));
        }
    }

    /** 收藏内容统一写入口（x/v3/fav/resource/batch-deal） */
    private static final String URL_RESOURCE_BATCH_DEAL = "https://api.bilibili.com/x/v3/fav/resource/batch-deal";

    /**
     * 收藏内容批量写操作：{@code add_media_ids} 是要加的收藏夹，{@code del_media_ids} 是要删的收藏夹，
     * 两者可同时传（服务端一次完成）；{@code resources} 是 {@code {内容id}:{内容类型}} 逗号串
     * （类型 2=视频稿件 / 12=音频 / 21=视频合集，见 {@link #buildResources(List)}）。
     *
     * <p>替代已废弃的 {@code medialist/gateway/coll/resource/deal}（老「收藏」）与
     * {@code medialist/gateway/coll/resource/batch/del}（老「取消收藏」）。
     */
    private static int dealFavorites(long mediaId, String resources, String addIds, String delIds) throws IOException, JSONException {
        if (mediaId <= 0 || resources == null || resources.isEmpty()) return -400;
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("resources", resources)
                .put("add_media_ids", addIds == null ? "" : addIds)
                .put("del_media_ids", delIds == null ? "" : delIds)
                .put("csrf", NetWorkUtil.currentCsrf());
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(URL_RESOURCE_BATCH_DEAL, formData.toString(), NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-收藏操作", result.toString());
        return result.getInt("code");
    }

    /**
     * 收藏视频到指定收藏夹。
     *
     * @param mediaId 目标收藏夹 media_id（来自 v3 列表接口，不再按 fid 手拼）
     */
    public static int addFavorite(long aid, long mediaId) throws IOException, JSONException {
        if (aid <= 0) return -400;
        return dealFavorites(mediaId, aid + ":2", String.valueOf(mediaId), "");
    }

    /**
     * 从指定收藏夹取消收藏（按视频稿件类型）。
     *
     * @param mediaId 目标收藏夹 media_id（来自 v3 列表接口，不再按 fid 手拼）
     * @see #deleteFavorite(long, int, long)
     */
    public static int deleteFavorite(long aid, long mediaId) throws IOException, JSONException {
        return deleteFavorite(aid, FAV_TYPE_VIDEO, mediaId);
    }

    /**
     * 按内容类型从指定收藏夹取消收藏。
     *
     * <p>收藏夹里可能混着音频（id 是 auid）和视频合集（id 是合集 id），
     * 只传 aid 会让它们被当成视频 avid 发出去，所以要把 {@link VideoCard#favType} 带上。
     *
     * @param aid     收藏内容 id（视频稿件为 avid，音频为 auid，合集为合集 id）
     * @param favType 收藏内容类型，取 {@link #FAV_TYPE_VIDEO} / {@link #FAV_TYPE_AUDIO} /
     *                {@link #FAV_TYPE_COLLECTION}，其它值按视频稿件处理
     * @param mediaId 目标收藏夹 media_id（来自 v3 列表接口，不再按 fid 手拼）
     */
    public static int deleteFavorite(long aid, int favType, long mediaId) throws IOException, JSONException {
        if (aid <= 0) return -400;
        return dealFavorites(mediaId, aid + ":" + normalizeFavType(favType), "", String.valueOf(mediaId));
    }

    /**
     * 清空收藏夹里所有失效内容（已被删除/不可见的稿件、音频、合集）。
     *
     * <p>接口：{@code POST https://api.bilibili.com/x/v3/fav/resource/clean}，
     * 正文只有 {@code media_id} + {@code csrf}，依据
     * {@code bilibili-API/docs/fav/action.md} 的「清空所有失效内容」小节。
     *
     * @param mediaId 目标收藏夹 media_id
     * @return 服务端 code（0 成功；-400 表示参数不合法，未发请求）
     */
    public static int cleanInvalidResources(long mediaId) throws IOException, JSONException {
        if (mediaId <= 0) return -400;
        String url = "https://api.bilibili.com/x/v3/fav/resource/clean";
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("media_id", mediaId)
                .put("csrf", NetWorkUtil.currentCsrf());
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-收藏操作", result.toString());
        return result.getInt("code");
    }

    public static int addFolder(String title, String intro, int privacy) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/add";
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("title", title)
                .put("intro", intro != null ? intro : "")
                .put("privacy", privacy)
                .put("csrf", NetWorkUtil.currentCsrf());
        String data = formData.toString();
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, data, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-新建收藏夹", result.toString());
        return result.getInt("code");
    }

    public static int editFolder(long mediaId, String title, String intro, int privacy) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/edit";
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("media_id", mediaId)
                .put("title", title)
                .put("intro", intro != null ? intro : "")
                .put("privacy", privacy)
                .put("csrf", NetWorkUtil.currentCsrf());
        String data = formData.toString();
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, data, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-修改收藏夹", result.toString());
        return result.getInt("code");
    }

    public static int deleteFolder(long mediaId) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/del";
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("media_ids", String.valueOf(mediaId))
                .put("platform", "web")
                .put("csrf", NetWorkUtil.currentCsrf());
        String data = formData.toString();
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, data, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-删除收藏夹", result.toString());
        return result.getInt("code");
    }

    /**
     * 拼批量操作要的 resources 参数：{@code {内容id}:{内容类型}}，多项用英文逗号连接。
     *
     * <p>类型取自 {@link VideoCard#favType}（经 {@link #normalizeFavType(int)} 归一化，
     * 只会是 2/12/21）；只有视频稿件是 {@code aid:2}，音频是 {@code auid:12}，
     * 视频合集是 {@code 合集id:21}。跳过 aid &lt;= 0 的脏数据；空表返回空串。纯函数，方便单测。
     */
    public static String buildResources(List<VideoCard> cards) {
        if (cards == null || cards.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (VideoCard card : cards) {
            if (card == null || card.aid <= 0) continue;
            if (builder.length() > 0) builder.append(',');
            builder.append(card.aid).append(':').append(normalizeFavType(card.favType));
        }
        return builder.toString();
    }

    /**
     * 批量把收藏内容复制到另一个收藏夹。
     *
     * @param srcMediaId 源收藏夹 media_id
     * @param tarMediaId 目标收藏夹 media_id
     * @param cards      要复制的内容
     * @return 服务端 code（0 成功）
     */
    public static int copyResources(long srcMediaId, long tarMediaId, List<VideoCard> cards) throws IOException, JSONException {
        return dealResources("https://api.bilibili.com/x/v3/fav/resource/copy", srcMediaId, tarMediaId, cards);
    }

    /**
     * 批量把收藏内容移动到另一个收藏夹。
     *
     * @see #copyResources(long, long, List)
     */
    public static int moveResources(long srcMediaId, long tarMediaId, List<VideoCard> cards) throws IOException, JSONException {
        return dealResources("https://api.bilibili.com/x/v3/fav/resource/move", srcMediaId, tarMediaId, cards);
    }

    /**
     * 批量把收藏内容从收藏夹里移除（多选删除）。
     *
     * @param mediaId 收藏夹 media_id（不能是 0，接口只认这个）
     * @param cards   要删除的内容
     * @return 服务端 code（0 成功）
     */
    public static int batchDeleteResources(long mediaId, List<VideoCard> cards) throws IOException, JSONException {
        return dealFavorites(mediaId, buildResources(cards), "", String.valueOf(mediaId));
    }

    private static int dealResources(String url, long srcMediaId, long tarMediaId, List<VideoCard> cards) throws IOException, JSONException {
        long mid = SharedPreferencesUtil.getLong("mid", 0);
        String resources = buildResources(cards);
        if (srcMediaId <= 0 || tarMediaId <= 0 || resources.isEmpty()) return -400;
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("src_media_id", srcMediaId)
                .put("tar_media_id", tarMediaId)
                .put("mid", mid)
                .put("resources", resources)
                .put("platform", "web")
                .put("csrf", NetWorkUtil.currentCsrf());
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders).body()).string());
        return result.getInt("code");
    }

    /** 收藏内容批量操作的错误码文案（batch-deal / copy / move 通用） */
    public static String resourceErrorMsg(int code) {
        switch (code) {
            case 0:
                return "";
            case -101:
                return ApiErrorText.loginRequired();
            case -111:
                return ApiErrorText.tokenExpired();
            case -400:
                return ApiErrorText.requestIllegal();
            case 11010:
                return "内容不存在，可能已经被删除了";
            default:
                return ApiErrorText.opFailed(code);
        }
    }

}
