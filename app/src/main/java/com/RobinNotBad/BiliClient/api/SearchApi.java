package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ArticleCard;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//搜索API 自己写的
//逐渐感觉拆json是个很爽的事（
//2023-07-14

public class SearchApi {

    /**
     * seid 是 B 站为一次搜索会话下发的分页标记，翻页时要原样带回。
     * <p>
     * 早先它是两个全局静态字段（seid + search_keyword）：同时打开两个搜索页时，
     * 后发起的那一页会把关键词和 seid 顶掉，前一页再翻页就会带着别人的关键词/seid 去请求（结果串页，
     * 而且 seid 与关键词不匹配时 B 站可能直接返回空列表）。现在按关键词隔离，
     * 并用访问序 LRU 限制条目数，避免用户搜过多少词就常驻多少条。
     */
    private static final int MAX_SEID_ENTRIES = 16;
    private static final LinkedHashMap<String, String> SEID_BY_KEYWORD =
            new LinkedHashMap<String, String>(MAX_SEID_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_SEID_ENTRIES;
                }
            };

    private static synchronized String getSeid(String keyword) {
        String seid = SEID_BY_KEYWORD.get(keyword);
        return seid == null ? "" : seid;
    }

    private static synchronized void putSeid(String keyword, String seid) {
        if (seid != null && !seid.isEmpty()) SEID_BY_KEYWORD.put(keyword, seid);
    }

    public static JSONArray search(String keyword, int page) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/wbi/search/all/v2?";
        url += "page=" + page +
                "&keyword=" + URLEncoder.encode(keyword, "UTF-8") + "&seid=" + getSeid(keyword);
        String legacyUrl = legacySearchAllUrl(keyword, page);

        JSONObject all;
        try {
            all = NetWorkUtil.getJson(ConfInfoApi.signWBI(url)); // 得到一整个json
        } catch (IOException | JSONException e) {
            // 风控有时直接回一张 HTML 拦截页，请求层就失败了（见 #fallbackToLegacy），
            // 这时同样要兜一次旧接口，不能把"被风控"报成"网络错误"
            Object legacyResult = fallbackToLegacy(keyword, legacyUrl, e);
            return legacyResult == null ? null : (JSONArray) legacyResult;
        }

        if (shouldFallback(all)) {
            // 风控只拦 wbi 那条，退到旧接口（不带签名）再试一次。
            // 用 getJsonOnce：已经确认处在风控态，再走通用重试（5 次）只会把限流拖得更久。
            Object legacyResult = fallbackToLegacy(keyword, legacyUrl, null);
            return legacyResult == null ? null : (JSONArray) legacyResult;
        }

        throwIfServerError(all);
        if (all.isNull("data"))
            return null;
        JSONObject data = all.getJSONObject("data"); // 搜索列表中的data项又是一个json，把它提出来

        // optString 而非 getString：个别风控/降级响应里没有 seid 字段，getString 会抛 JSONException
        // 把整页搜索结果一起带走
        putSeid(keyword, data.optString("seid", ""));

        if (hasResult(data))
            return data.getJSONArray("result"); // 其实这还不是我们要的结果，下面的函数对它进行再次拆解 这里做了判空
        else
            return null;
    }

    public static Object searchType(String keyword, int page, String type) throws IOException, JSONException {
        return searchType(keyword, page, type, null);
    }

    /**
     * 分类搜索（带排序）。
     * <p>
     * 排序参数只在这条接口上有——聚合搜索（{@link #search}）的 {@code all/v2} 只接受 keyword，
     * 所以视频分类一旦选了「综合」以外的排序就必须改走这里。
     *
     * @param order 结果排序方式，null 或空表示用服务端默认（综合 totalrank）。视频与专栏可用
     *              totalrank / click / pubdate / dm / stow / scores（专栏另有 attention）；
     *              取值定义见 {@code bilibili-API/docs/search/search_request.md}「分类搜索」一节。
     */
    public static Object searchType(String keyword, int page, String type, String order)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/wbi/search/type?";
        url += "page=" + page +
                "&keyword=" + URLEncoder.encode(keyword, "UTF-8") + "&search_type=" + type + "&seid=" + getSeid(keyword);
        if (order != null && !order.isEmpty()) url += "&order=" + order;
        String legacyUrl = legacySearchTypeUrl(keyword, page, type, order);

        JSONObject all;
        try {
            all = NetWorkUtil.getJson(ConfInfoApi.signWBI(url)); // 得到一整个json
        } catch (IOException | JSONException e) {
            // 同 search()：风控直接回 HTML 时请求层就失败了，也要兜一次旧接口
            return fallbackToLegacy(keyword, legacyUrl, e);
        }

        if (shouldFallback(all)) {
            // 风控只拦 wbi 那条，退到旧接口（不带签名）再试一次。
            // 用 getJsonOnce：已经确认处在风控态，再走通用重试（5 次）只会把限流拖得更久。
            return fallbackToLegacy(keyword, legacyUrl, null);
        }

        throwIfServerError(all);
        if (all.isNull("data"))
            return null;
        JSONObject data = all.getJSONObject("data"); // 搜索列表中的data项又是一个json，把它提出来

        putSeid(keyword, data.optString("seid", ""));

        if (hasResult(data))
            return data.get("result"); // 其实这还不是我们要的结果，下面的函数对它进行再次拆解 这里做了判空
        else
            return null;
    }

    /**
     * 被风控挡下后改打旧接口（不带 wbi 签名）。
     *
     * <h3>为什么请求层失败也算风控</h3>
     * 风控不只表现为「合法的 JSON 空壳」——B 站被拦时还可能直接回一张 **HTML 拦截页**，
     * 那会让 {@link NetWorkUtil#getJson} 的 doctype 重试耗尽后抛 {@code IOException}，
     * 或者内容不是 JSON 而抛 {@code JSONException}。旧实现里这两种情况在 {@code shouldFallback}
     * 之前就抛出去了，页面只会显示「网络错误」、排序按钮也进不了风控态
     * （26.10.25 审查指出；实测响应记录见 `docs/review/agent-test-guide.md`）。
     *
     * <h3>两边都失败时报什么</h3>
     * 主链路是**网络层失败**（真断网）→ 如实上抛它的异常，别诬陷成风控；
     * 主链路是**合法 JSON 的风控降级**（{@code primary == null}）→ 抛
     * {@link RiskControlException}，让用户看到「过一会儿再试」并拿到重试入口。
     *
     * @param primary 主链路的失败原因；{@code null} 表示主链路请求成功、只是响应是被风控降级的空壳
     */
    private static Object fallbackToLegacy(String keyword, String legacyUrl, Exception primary)
            throws IOException, JSONException {
        try {
            return resultOrThrow(keyword, NetWorkUtil.getJsonOnce(legacyUrl));
        } catch (SearchException e) {
            // 旧接口已经给出明确结论（风控 / 服务端错误码），比网络层异常更有信息量
            throw e;
        } catch (IOException | JSONException e) {
            if (primary instanceof IOException) throw (IOException) primary;
            if (primary instanceof JSONException) throw (JSONException) primary;
            throw new RiskControlException(ApiErrorText.searchRejected());
        }
    }

    /**
     * 回退到旧接口时把 wbi 那条拿到的 seid 一起带上。
     *
     * seid 是「本次搜索会话」的凭据：不带它时服务端每次翻页都当成一轮全新搜索，
     * 同一批结果会在页与页之间反复出现（26.11.11 反馈 #148 的重复来源之一）。
     * 拿不到 seid 就原样返回 URL。纯函数，见 SearchApiTest。
     */
    static String appendSeid(String url, String seid) {
        if (url == null) return null;
        if (seid == null || seid.isEmpty()) return url;
        return url + "&seid=" + seid;
    }

    /** 旧接口（非 wbi 签名）。风控只拦 wbi 那条，退到这里一般还能拿到结果——26.10.24 实测。 */
    private static String legacySearchTypeUrl(String keyword, int page, String type, String order)
            throws UnsupportedEncodingException {
        String url = "https://api.bilibili.com/x/web-interface/search/type?";
        url += "page=" + page +
                "&keyword=" + URLEncoder.encode(keyword, "UTF-8") + "&search_type=" + type;
        if (order != null && !order.isEmpty()) url += "&order=" + order;
        return appendSeid(url, getSeid(keyword));
    }

    private static String legacySearchAllUrl(String keyword, int page) throws UnsupportedEncodingException {
        return appendSeid("https://api.bilibili.com/x/web-interface/search/all/v2?page=" + page +
                "&keyword=" + URLEncoder.encode(keyword, "UTF-8"), getSeid(keyword));
    }

    private static boolean hasResult(JSONObject data) {
        return data.has("result") && !data.isNull("result");
    }

    /**
     * 要不要走旧接口回退。**两种风控形态都要认**（26.10.24 审查发现漏了第二种）：
     * <ul>
     *   <li>`code` 是 `-412`（IP 被风控）/ `-352`（wbi 校验失败）——这类响应往往连 `data` 都没有，
     *       以前会在 {@code if (all.isNull("data")) return null;} 处静默返回 null，页面只剩空列表；</li>
     *   <li>`code` 是 0，但 `data` 里只有 `v_voucher`（既无 `result` 也无 `seid`）。</li>
     * </ul>
     */
    public static boolean shouldFallback(JSONObject all) {
        int code = all.optInt("code", 0);
        if (code == -412 || code == -352) return true;
        JSONObject data = all.optJSONObject("data");
        return data != null && isRiskControl(data);
    }

    /**
     * 搜索结果的风控降级**内容**判据：`data` 里**既没有 `result` 也没有 `seid`**，只有一个
     * `v_voucher`——`{"code":0,"message":"OK","data":{"v_voucher":"voucher_…"}}`。
     * <p>
     * 判据只认 `v_voucher`：真正"没有匹配结果"的搜索会返回带 `result` 的响应（数组可能为空），
     * 不能因为 `result` 为空就当成风控。
     */
    public static boolean isRiskControl(JSONObject data) {
        return !hasResult(data) && data.has("v_voucher");
    }

    /**
     * 旧接口的响应清洗：风控（`-412`/`-352`/`v_voucher`）抛 {@link RiskControlException}，
     * 其它非零 `code` 抛 {@link SearchException}（带服务端错误码，别伪装成"没有结果"），
     * 只有 `code == 0` 且拿不到 `result` 才返回 null——那才是真的"没有匹配结果"。
     *
     * <p>26.10.24 审查指出：以前其它错误码也返回 null，页面会显示成「啥都木有」。
     */
    private static Object resultOrThrow(String keyword, JSONObject all) throws JSONException, SearchException {
        if (all == null) return null;
        int code = all.optInt("code", 0);
        if (code == -412 || code == -352) throw new RiskControlException(ApiErrorText.searchRejected());
        if (code != 0) throw new SearchException(ApiErrorText.opFailed(code));
        if (all.isNull("data")) return null;
        JSONObject data = all.getJSONObject("data");
        putSeid(keyword, data.optString("seid", ""));
        if (isRiskControl(data)) throw new RiskControlException(ApiErrorText.searchRejected());
        return hasResult(data) ? data.get("result") : null;
    }

    /**
     * 主链路（wbi）响应的错误码检查：非零且不是风控就抛出去，别让页面把"请求失败"显示成"没有结果"。
     */
    private static void throwIfServerError(JSONObject all) throws SearchException {
        int code = all.optInt("code", 0);
        if (code != 0 && code != -412 && code != -352) {
            throw new SearchException(ApiErrorText.opFailed(code));
        }
    }

    /**
     * 搜索请求失败、且原因要原样告诉用户的异常基类。
     * <p>
     * 为什么不直接用 {@link IOException}：`MsgUtil.err` 对任何 `IOException` 一律换成
     * 「网络错误」，带 message 也进不了界面（26.10.24 审查发现）。页面在
     * `SearchFragment.report()` 里认这个基类，直接把 `message` 弹出来。
     */
    public static class SearchException extends IOException {
        public SearchException(String message) {
            super(message);
        }
    }

    /**
     * 搜索被风控挡下、且旧接口也没拿到结果时抛这个。
     * <p>
     * 页面据此把排序按钮画成「因B站风控不可用」（见 `SearchFragment.isSortBlocked`），
     * 不能靠比对异常文案。
     */
    public static class RiskControlException extends SearchException {
        public RiskControlException(String message) {
            super(message);
        }
    }

    public static void getVideosFromSearchResult(JSONArray input, ArrayList<VideoCard> videoCardList, boolean first)
            throws JSONException {
        for (int i = 0; i < input.length(); i++) { // 遍历所有的分类，找到视频那一项
            JSONObject typecard = input.getJSONObject(i);
            String type = typecard.getString("result_type");
            if (type.equals("video")) {
                JSONArray data = typecard.getJSONArray("data"); // 把这个列表提出来，接着拆
                for (int j = 0; j < data.length(); j++) {
                    parseVideoEntry(data.getJSONObject(j), videoCardList);
                }
            } else if (type.equals("media_bangumi") && first) {
                // 聚合搜索里的番剧条目与 search_type=media_bangumi 的 data.result 同构，
                // 不再保留第二套解析：旧实现用 getString 取字段（缺 index_show 时整页抛 JSONException）、
                // 封面也没补 scheme。两处统一走 getBangumiFromSearchResult，容错与封面规则只维护一份。
                JSONArray data = typecard.getJSONArray("data");
                getBangumiFromSearchResult(data, videoCardList);
            }
        }
    }

    /**
     * 解析分类搜索（{@code search_type=video}）的视频结果。
     * <p>
     * 与 {@link #getVideosFromSearchResult} 的区别只在输入形状：分类搜索返回的 {@code data.result}
     * 直接就是视频条目数组，外面没有 {@code result_type} 那一层，也不会混入番剧。
     * 条目字段与聚合搜索里的视频条目同构，故共用 {@link #parseVideoEntry}。
     */
    public static void getVideosFromTypeResult(JSONArray input, ArrayList<VideoCard> videoCardList)
            throws JSONException {
        if (input == null) return;
        for (int i = 0; i < input.length(); i++) {
            JSONObject card = input.optJSONObject(i);
            if (card != null) parseVideoEntry(card, videoCardList);
        }
    }

    /**
     * 单条视频搜索结果 → VideoCard。聚合搜索与分类搜索共用这一份，改字段只改这里。
     */
    private static void parseVideoEntry(JSONObject card, ArrayList<VideoCard> videoCardList) throws JSONException {
        if (!card.optString("type", "").equals("video"))
            return; // 警惕虚假视频卡片（阿B为什么要把直播间放进视频结果里）

        String title = card.getString("title");
        title = title.replace("<em class=\"keyword\">", "").replace("</em>", "");
        // 标题里的红字，知道怎么显示但因为懒所以直接删（//显示方式可以用imagespan，参照表情包那部分程序（EmoteUtil），期待后人补齐（
        title = StringUtil.htmlToString(title);

        String bvid = card.getString("bvid");
        long aid = card.getLong("aid");
        String cover = httpsUrl(card.optString("pic", ""));
        String upName = card.getString("author");

        long play = card.getLong("play");
        String playTimesStr = StringUtil.toWan(play) + "观看";

        videoCardList.add(new VideoCard(title, upName, playTimesStr, cover, aid, bvid, "video"));
    }

    /**
     * 搜索结果里的图片都是协议相对地址（{@code //i0.hdslb.com/...}），不补 scheme 的话
     * Glide 不认、{@code GlideUtil} 也不会加尺寸后缀，封面必然加载失败。
     * <p>
     * 统一补 <b>https</b>（与 {@link #getBangumiFromSearchResult} 同一规则）：本应用只走 https，
     * 明文白名单只用于兜底，不该在客户端主动拼 http 地址。
     */
    private static String httpsUrl(String url) {
        if (url == null) return "";
        return url.startsWith("//") ? "https:" + url : url;
    }

    /**
     * 番剧条目解析：聚合搜索与单独搜索共用这一份。
     * <p>
     * 单独搜索（search_type=media_bangumi）时 input 就是 data.result 本身（一个番剧条目数组），
     * 外面没有再套一层 result_type；{@link #getVideosFromSearchResult} 的 media_bangumi 分支
     * 则把 type 卡里的 data 数组传进来，两者条目同构，故复用同一套容错与封面规则。
     * <p>
     * 番剧条目里没有 aid/bvid，借用 VideoCard 的已有字段承载：
     * aid=media_id（剧集 mdid，番剧详情页正是用它拉取信息）、bvid=season_id（ssid，仅作稳定 id 用）、
     * type 固定 "media_bangumi"（VideoCardHolder 已按该 type 跳番剧详情页）。
     *
     * @param input           search_type=media_bangumi 返回的 data.result
     * @param videoCardList   结果追加到这里
     */
    public static void getBangumiFromSearchResult(JSONArray input, ArrayList<VideoCard> videoCardList)
            throws JSONException {
        if (input == null) return;

        for (int i = 0; i < input.length(); i++) {
            JSONObject card = input.optJSONObject(i);
            if (card == null) continue;

            String title = card.optString("title", "");
            title = title.replace("<em class=\"keyword\">", "").replace("</em>", "");
            title = StringUtil.htmlToString(title);

            // 搜索结果里的封面是协议相对地址（//i0.hdslb.com/...），而 GlideUtil 对不以 http 开头的
            // url 会原样返回、不加尺寸后缀，Glide 本身也不认无 scheme 的地址 —— 不补 scheme 封面必然失败
            String cover = card.optString("cover", "");
            if (cover.startsWith("//")) cover = "https:" + cover;

            String areas = card.optString("areas", "");          // 地区，卡片第二行
            String indexShow = card.optString("index_show", ""); // 更新进度（如"全14话"），卡片第三行
            // index_show 是聚合搜索（all/v2）才稳定给出的字段，单独的 search_type=media_bangumi
            // 接口文档里没有它，实测可能为空；为空时退用 styles（风格，如"原创/科幻/推理"），
            // 免得卡片第三行整行消失（VideoCardHolder 对空 view 是直接隐藏）
            if (indexShow.isEmpty()) indexShow = card.optString("styles", "");
            long mediaId = card.optLong("media_id", 0);
            String seasonId = card.optString("season_id", "");

            videoCardList.add(new VideoCard(title, areas, indexShow, cover, mediaId, seasonId, "media_bangumi"));
        }
    }

    public static void getUsersFromSearchResult(JSONArray input, List<UserInfo> userInfoList) throws JSONException {
        for (int i = 0; i < input.length(); i++) {
            JSONObject card = input.getJSONObject(i); // 获得用户卡片

            long mid = card.getLong("mid");
            String name = card.getString("uname");
            String avatar = httpsUrl(card.getString("upic"));
            String sign = card.getString("usign");
            int fans = card.getInt("fans");
            int level = card.getInt("level");

            userInfoList.add(new UserInfo(mid, name, avatar, sign, fans, 0, level, false, "", 0, "", 0));
        }
    }

    public static void getArticlesFromSearchResult(JSONArray input, ArrayList<ArticleCard> articleCardList)
            throws JSONException {
        for (int i = 0; i < input.length(); i++) {
            ArticleCard articleCard = new ArticleCard();
            JSONObject card = input.getJSONObject(i); // 获得专栏卡片

            articleCard.id = card.getLong("id");
            if (card.getJSONArray("image_urls").length() > 0)
                articleCard.cover = httpsUrl(card.getJSONArray("image_urls").getString(0));
            else
                articleCard.cover = "";
            articleCard.upName = card.getString("category_name");
            articleCard.title = StringUtil.htmlReString(card.getString("title"));
            articleCard.view = StringUtil.toWan(card.getInt("view")) + "阅读";

            articleCardList.add(articleCard);
        }
    }

    /**
     * 获取搜索建议
     *
     * @param keyword 搜索关键词
     * @return 返回搜索建议列表，最多10条
     * @throws IOException   网络请求异常
     * @throws JSONException JSON解析异常
     */
    public static ArrayList<String> getSearchSuggestions(String keyword) throws IOException, JSONException {
        ArrayList<String> suggestions = new ArrayList<>();

        if (keyword == null || keyword.trim().isEmpty()) {
            return suggestions;
        }

        String url = "https://s.search.bilibili.com/main/suggest?term=" + URLEncoder.encode(keyword, "UTF-8");

        JSONObject response = NetWorkUtil.getJson(url);

        if (response.getInt("code") == 0 && response.has("result")) {
            JSONObject result = response.getJSONObject("result");
            if (result.has("tag") && !result.isNull("tag")) {
                JSONArray tagArray = result.getJSONArray("tag");
                for (int i = 0; i < tagArray.length(); i++) {
                    JSONObject tagObj = tagArray.getJSONObject(i);
                    String value = tagObj.getString("value");
                    suggestions.add(value);
                }
            }
        }

        return suggestions;
    }

    public static String getDefaultSearchContent() throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/wbi/search/default";
        JSONObject response = NetWorkUtil.getJson(ConfInfoApi.signWBI(url));
        if (response.getInt("code") == 0 && response.has("data")) {
            JSONObject data = response.getJSONObject("data");
            if (data.has("show_name") && !data.isNull("show_name")) {
                return data.getString("show_name");
            }
        }
        return null;
    }
}
