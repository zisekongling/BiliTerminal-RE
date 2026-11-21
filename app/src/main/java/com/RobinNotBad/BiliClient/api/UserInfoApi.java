package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ArticleCard;
import com.RobinNotBad.BiliClient.model.LiveRoom;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.DmImgParamUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Response;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

//用户信息API

public class UserInfoApi {

    public static UserInfo getUserInfo(long mid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/card?mid=" + mid;
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject notice_all = NetWorkUtil.getJson("https://api.bilibili.com/x/space/notice?mid=" + mid);
            String notice;
            if (notice_all.has("data") && !notice_all.isNull("data"))
                notice = notice_all.getString("data");
            else notice = "";
            JSONObject data = all.getJSONObject("data");
            boolean followed = data.getBoolean("following");
            int fans = data.getInt("follower");

            JSONObject card = data.getJSONObject("card");
            String name = card.getString("name");
            String avatar = card.getString("face");
            String sign = card.getString("sign");
            JSONObject levelInfo = card.getJSONObject("level_info");
            int level = levelInfo.getInt("current_level");
            int attention = card.getInt("attention");

            JSONObject official_data = card.getJSONObject("Official");
            int official = official_data.getInt("role");
            String officialDesc = official_data.getString("title");

            String sys_notice = "";
            LiveRoom liveroom = null;
            boolean is_follow_display = false;
            // 批次 1：性别 / IP 属地只在 wbi/acc/info 里返回（card 接口没有这两个字段），
            // 隐私设置为「保密」/隐藏时服务端给空串，展示层按空串隐藏该行
            String userSex = "";
            String userIpLocation = "";
            try {
                JSONObject spaceInfo = getUserSpaceInfo(mid);
                if (spaceInfo != null) {
                    if (!spaceInfo.isNull("sys_notice")) {
                        sys_notice = spaceInfo.getJSONObject("sys_notice").optString("content");
                        if (sys_notice == null) sys_notice = "";
                        else sys_notice = sys_notice.replace("请点此查看纪念账号相关说明", "");
                    }
                    if (!spaceInfo.isNull("live_room")) {
                        JSONObject live_room = spaceInfo.getJSONObject("live_room");
                        if (live_room.getInt("roomStatus") == 1 && live_room.getInt("liveStatus") == 1) {
                            liveroom = new LiveRoom();
                            liveroom.title = "直播中：" + live_room.getString("title");
                            liveroom.user_cover = live_room.getString("cover");
                            liveroom.roomid = live_room.getLong("roomid");
                        }
                    }
                    if (!spaceInfo.isNull("contract")) {
                        JSONObject contract = spaceInfo.getJSONObject("contract");
                        is_follow_display = contract.optBoolean("is_follow_display", false);
                    }
                    userSex = spaceInfo.optString("sex", "");
                    userIpLocation = spaceInfo.optString("ip_location", "");
                }
            } catch (Exception ignore) {
            }

            JSONObject vip = card.getJSONObject("vip");
            if (vip.getInt("status") == 1) {
                UserInfo result = new UserInfo(mid, name, avatar, sign, fans, attention, level, followed, notice, official, officialDesc, vip.getInt("role"), sys_notice, liveroom, card.getInt("is_senior_member"));
                result.vip_nickname_color = vip.optString("nickname_color", "");
                result.is_follow_display = is_follow_display;
                result.sex = userSex;
                result.ipLocation = userIpLocation;
                return result;
            } else {
                UserInfo result = new UserInfo(mid, name, avatar, sign, fans, attention, level, followed, notice, official, officialDesc, sys_notice, liveroom, card.getInt("is_senior_member"));
                result.is_follow_display = is_follow_display;
                result.sex = userSex;
                result.ipLocation = userIpLocation;
                return result;
            }
        } else return null;
    }

    public static JSONObject getUserSpaceInfo(long mid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/space/wbi/acc/info?";
        url += "mid=" + mid;
        JSONObject all = NetWorkUtil.getJson(ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url)));
        if (all.has("data") && !all.isNull("data")) {
            return all.getJSONObject("data");
        }
        return null;
    }

    /**
     * 纯函数：把「性别 / IP 属地」拼成一行展示文本；两项都不可展示时返回空串（调用方据此隐藏整行）。
     *
     * <p>放在这里而不是 adapter 里，是为了让「什么情况显示、顺序怎么排、分隔符谁加」
     * 这三件事只存在一份实现，并能被 JVM 单测直接覆盖（adapter 那层碰 Android，测不了）。
     *
     * @param userInfo      用户信息
     * @param sexFormat     性别文案模板，形如 {@code 性别：%1$s}
     * @param ipFormat      IP 属地文案模板，形如 {@code IP属地：%1$s}
     * @param separator     两项之间插入的分隔符
     * @return 展示文本；空串表示整行不显示
     */
    public static String buildGenderIpLine(UserInfo userInfo, String sexFormat, String ipFormat, String separator) {
        if (userInfo == null) return "";
        String sex = userInfo.displaySex();
        String ip = userInfo.displayIpLocation();
        if (sex.isEmpty() && ip.isEmpty()) return "";
        StringBuilder line = new StringBuilder();
        if (!sex.isEmpty()) line.append(String.format(java.util.Locale.US, sexFormat == null ? "%s" : sexFormat, sex));
        if (!ip.isEmpty()) {
            if (line.length() > 0) line.append(separator == null ? "" : separator);
            line.append(String.format(java.util.Locale.US, ipFormat == null ? "%s" : ipFormat, ip));
        }
        return line.toString();
    }

    public static UserInfo getCurrentUserInfo() throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/space/myinfo";
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            long mid = data.getLong("mid");
            String name = data.getString("name");
            String avatar = data.getString("face");
            String sign = data.getString("sign");
            int fans = data.getInt("follower");
            int level = data.getInt("level");

            JSONObject official_data = data.getJSONObject("official");
            int official = official_data.getInt("role");
            String officialDesc = official_data.getString("desc");

            JSONObject level_exp = data.getJSONObject("level_exp");
            long current_exp = level_exp.getLong("current_exp");
            long next_exp = level_exp.getLong("next_exp");

            return new UserInfo(mid, name, avatar, sign, fans, 0, level, false, "", official, officialDesc, current_exp, next_exp, data.getInt("is_senior_member"));
        } else return new UserInfo(0, "加载失败", "", "", 0, 0, 0, false, "", 0, "", 0);
    }

    public static int getCurrentUserCoin() {
        try {
            String url = "https://account.bilibili.com/site/getCoin";
            JSONObject all = NetWorkUtil.getJson(url);
            if (all.has("data") && !all.isNull("data")) {
                JSONObject data = all.getJSONObject("data");
                return data.has("money") ? data.getInt("money") : 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }


    /** 空间投稿排序档位：与 web 端 `order` 参数一致 */
    public static final String ORDER_PUBDATE = "pubdate";   // 最新发布
    public static final String ORDER_CLICK = "click";       // 最多播放
    public static final String ORDER_STOW = "stow";         // 最多收藏

    /** 空间投稿：保持旧行为（按发布时间倒序、不带关键词） */
    public static int getUserVideos(long mid, int page, String searchKeyword, List<VideoCard> videoList) throws IOException, JSONException {
        return getUserVideos(mid, page, searchKeyword, ORDER_PUBDATE, videoList);
    }

    /**
     * 空间投稿：空间内关键词搜索 + 指定排序。
     *
     * <p>order 只认白名单（见 [normalizeOrder]）：它直接拼进 WBI 签名串，
     * 放开任意输入等于让调用方改请求结构。
     */
    public static int getUserVideos(long mid, int page, String searchKeyword, String order, List<VideoCard> videoList) throws IOException, JSONException {
        String url = buildUserVideoUrl(mid, page, searchKeyword, order);
        JSONObject all = NetWorkUtil.getJson(ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url)));
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            JSONObject list = data.getJSONObject("list");
            if (list.has("vlist") && !list.isNull("vlist")) {
                JSONArray vlist = list.getJSONArray("vlist");
                if (vlist.length() == 0) return 1;
                for (int i = 0; i < vlist.length(); i++) {
                    JSONObject card = vlist.getJSONObject(i);
                    String cover = card.getString("pic");
                    long play = card.getLong("play");
                    String playStr = StringUtil.toWan(play) + "观看";
                    long aid = card.getLong("aid");
                    String bvid = card.getString("bvid");
                    String upName = card.getString("author");
                    String title = card.getString("title");

                    videoList.add(new VideoCard(title, upName, playStr, cover, aid, bvid));
                }
                return 0;
            } else return -1;
        } else return -1;
    }

    /**
     * 排序参数白名单：只放行「最多播放 / 最多收藏」，其余（含 null / 空串 / 用户乱传）一律回落
     * 到「最新发布」。这个值会被拼进 WBI 签名串，不能让它承载任意内容。
     *
     * <p>纯函数，见 `UserInfoApiTest`。
     */
    public static String normalizeOrder(String order) {
        if (ORDER_CLICK.equals(order) || ORDER_STOW.equals(order)) return order;
        return ORDER_PUBDATE;
    }

    /**
     * 空间投稿列表的请求地址。
     *
     * <p>keyword **必须在签名前先编码**：含 `&` / `=` 的关键词会破坏 query 结构，也会让 WBI
     * 签名与服务端重算的参数串对不上（服务端校验的就是编码后的参数串，与 web 端一致）。
     *
     * <p>纯函数，见 `UserInfoApiTest`。
     */
    public static String buildUserVideoUrl(long mid, int page, String searchKeyword, String order) throws IOException {
        String safeOrder = normalizeOrder(order);
        return "https://api.bilibili.com/x/space/wbi/arc/search?"
                + "keyword=" + encodeKeyword(searchKeyword)
                + "&mid=" + mid
                + "&order_avoided=true&order=" + safeOrder
                + "&pn=" + page
                + "&ps=40&tid=0&web_location=333.999";
    }

    /**
     * 关键词编码，口径对齐 WBI 签名要的 `encodeURIComponent`，**不是**表单编码。
     *
     * <p>为什么不能直接用 `URLEncoder.encode`：它是 `application/x-www-form-urlencoded`——
     * 空格编成 `+`、`~ ! ' ( )` 也各自编成 `%XX`，而 WBI 的服务端是「解码出原值 →
     * `encodeURIComponent` 重编码 → 比 md5」。差异项会让签名串对不上，服务端回 -352
     * 「风控校验失败」，用户看到的就是「加载失败」。26.11.07 用脚本按仓库 signWBI 的
     * 算法对 `space/wbi/arc/search` 实测：`a~b` 用 `a%7Eb` 签 → -352，用 `a~b` 签 → 签名通过
     * （服务端返回业务码而非风控码）。
     *
     * <p>`URLEncoder` 编出来的其余部分（中文 / 结构字符 / 大写十六进制）与 `encodeURIComponent`
     * 一致，所以这里只把差异项掰回来即可。
     *
     * <p>纯函数，见 `UserInfoApiTest`。
     */
    public static String encodeKeyword(String keyword) throws IOException {
        if (keyword == null || keyword.isEmpty()) return "";
        return URLEncoder.encode(keyword, "UTF-8")
                .replace("+", "%20")     // 表单编码的空格 -> encodeURIComponent 的 %20
                .replace("%7E", "~")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")");
    }


    public static int getUserArticles(long mid, int page, List<ArticleCard> articleList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/space/wbi/article?";
        url += "mid=" + mid + "&order_avoided=true&order=pubdate&pn=" + page
                + "&ps=30&tid=0";
        JSONObject all = NetWorkUtil.getJson(ConfInfoApi.signWBI(url), NetWorkUtil.webHeaders);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            if (data.has("articles")) {
                JSONArray list = data.getJSONArray("articles");
                if (list.length() == 0) return 1;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject card = list.getJSONObject(i);

                    ArticleCard articleCard = new ArticleCard();
                    articleCard.id = card.getLong("id");
                    articleCard.title = card.getString("title");
                    JSONObject stats = card.getJSONObject("stats");
                    articleCard.view = StringUtil.toWan(stats.getInt("view")) + "阅读";
                    articleCard.cover = card.getString("banner_url");
                    JSONObject author = card.getJSONObject("author");
                    articleCard.upName = author.getString("name");
                    articleList.add(articleCard);
                }
                return 0;
            } else return 1;
        } else return -1;
    }

    public static int followUser(long mid, boolean isFollow) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/modify?";
        String arg = "fid=" + mid + "&csrf=" + NetWorkUtil.getInfoFromCookie("bili_jct", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        if (isFollow) arg += "&act=1"; //关注
        else arg += "&act=2"; //取消关注
        JSONObject all = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        return all.getInt("code");
    }

    /**
     * 退出登录：请求服务端注销当前会话。
     *
     * 为什么必须是 POST + csrf：正式的注销端点是
     * `POST https://passport.bilibili.com/login/exit/v2`，且校验 csrf（bili_jct）。
     * 之前用不带参数的 GET 根本调不动它 —— 服务端会话（含 refresh_token）不会被失效，
     * 等于「退出登录」只清了本地 Cookie，Token 一旦泄漏攻击者仍然能用，是安全问题。
     *
     * @return 服务端是否确认注销成功（code == 0）。网络异常、csrf 缺失、返回非 0 都返回 false，
     * 调用方据此提示用户，但**无论成功与否都应清理本地登录态**，不能把用户卡在登录页面。
     */
    public static boolean exitLogin() {
        try {
            // csrf 必须从「还没被清掉的」Cookie 里读，所以调用方要在清本地登录态之前调这个方法
            String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
            if (csrf.isEmpty()) return false; // 没有 csrf 时服务端必然拒绝，不必白跑一次网络请求
            String url = "https://passport.bilibili.com/login/exit/v2";
            // try-with-resources：body 为 null 或 isSuccessful() 为 false 时下面提前 return，
            // 而 OkHttp 只在 body 读到 EOF 时才归还连接（412 风控页/5xx 很常见）。
            try (Response response = NetWorkUtil.post(url, "csrf=" + csrf, NetWorkUtil.webHeaders)) {
                if (!response.isSuccessful() || response.body() == null) return false;
                // 服务端成功时返回 {"code":0,...}；返回非 0 说明会话没被失效，按失败处理
                return new JSONObject(response.body().string()).optInt("code", -1) == 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static int addContract(long upMid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v1/contract/add_contract";
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        String arg = "aid=&up_mid=" + upMid + "&source=4&scene=105&platform=web&mobi_app=pc&csrf=" + csrf;
        JSONObject all = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        return all.getInt("code");
    }

    public static JSONObject getMedalWall(long targetId) throws IOException, JSONException {
        String url = "https://api.live.bilibili.com/xlive/web-ucenter/user/MedalWall?target_id=" + targetId;
        JSONObject all = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (all.has("data") && !all.isNull("data")) {
            return all.getJSONObject("data");
        }
        return null;
    }

    public static JSONObject updateUserSign(String userSign) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/member/web/sign/update";
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        String arg = "csrf=" + csrf;
        if (userSign != null) {
            arg += "&user_sign=" + java.net.URLEncoder.encode(userSign, "UTF-8");
        }
        JSONObject all = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        return all;
    }

    /**
     * 修改昵称/生日/性别（可只传要修改的项，其余传null）
     *
     * @param uname    新昵称，null表示不修改
     * @param birthday 新生日，格式YYYY-MM-DD，null表示不修改
     * @param sex      新性别，1=男 2=女，null表示不修改
     * @param usersign 新签名，null表示不修改
     */
    public static JSONObject updateUserInfo(String uname, String birthday, String sex, String usersign) throws IOException, JSONException {
        String cookiesStr = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookiesStr);
        String sessdata = NetWorkUtil.getInfoFromCookie("SESSDATA", cookiesStr);
        String dedeUserId = NetWorkUtil.getInfoFromCookie("DedeUserID", cookiesStr);
        String buvid3 = NetWorkUtil.getInfoFromCookie("buvid3", cookiesStr);

        StringBuilder arg = new StringBuilder();
        arg.append("csrf=").append(csrf);
        arg.append("&x-bili-redirect=1");
        if (uname != null && !uname.isEmpty()) {
            arg.append("&uname=").append(java.net.URLEncoder.encode(uname, "UTF-8"));
        }
        if (birthday != null && !birthday.isEmpty()) {
            if (!birthday.matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw new IllegalArgumentException("生日格式必须为YYYY-MM-DD");
            }
            arg.append("&birthday=").append(java.net.URLEncoder.encode(birthday, "UTF-8"));
        }
        if (sex != null && !sex.isEmpty()) {
            String sexStr = "1".equals(sex) ? "男" : ("2".equals(sex) ? "女" : sex);
            arg.append("&sex=").append(java.net.URLEncoder.encode(sexStr, "UTF-8"));
        }
        if (usersign != null) {
            arg.append("&usersign=").append(java.net.URLEncoder.encode(usersign, "UTF-8"));
        }

        String url = "https://api.bilibili.com/x/member/web/update";
        StringBuilder cookieBuilder = new StringBuilder();
        if (sessdata != null && !sessdata.isEmpty()) cookieBuilder.append("SESSDATA=").append(sessdata).append("; ");
        if (csrf != null && !csrf.isEmpty()) cookieBuilder.append("bili_jct=").append(csrf).append("; ");
        if (dedeUserId != null && !dedeUserId.isEmpty()) cookieBuilder.append("DedeUserID=").append(dedeUserId).append("; ");
        if (buvid3 != null && !buvid3.isEmpty()) cookieBuilder.append("buvid3=").append(buvid3);

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/x-www-form-urlencoded; charset=utf-8"), arg.toString());
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Cookie", cookieBuilder.toString())
                .addHeader("Referer", "https://www.bilibili.com/")
                .addHeader("Origin", "https://account.bilibili.com")
                .addHeader("User-Agent", NetWorkUtil.USER_AGENT_WEB)
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build();
        return new JSONObject(decompressResponse(NetWorkUtil.getOkHttpInstance().newCall(request).execute()));
    }

    /**
     * 上传头像
     *
     * @param imageData 已压缩的JPEG图片数据
     * @param fileName  文件名（含扩展名）
     */
    public static JSONObject uploadAvatar(byte[] imageData, String fileName) throws IOException, JSONException {
        String cookiesStr = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookiesStr);
        String sessdata = NetWorkUtil.getInfoFromCookie("SESSDATA", cookiesStr);
        String dedeUserId = NetWorkUtil.getInfoFromCookie("DedeUserID", cookiesStr);
        String buvid3 = NetWorkUtil.getInfoFromCookie("buvid3", cookiesStr);
        String buvid4 = NetWorkUtil.getInfoFromCookie("buvid4", cookiesStr);
        String biliTicket = NetWorkUtil.getInfoFromCookie("bili_ticket", cookiesStr);

        String url = "https://api.bilibili.com/x/member/web/face/update";
        StringBuilder cookieBuilder = new StringBuilder();
        if (sessdata != null && !sessdata.isEmpty()) cookieBuilder.append("SESSDATA=").append(sessdata).append("; ");
        if (csrf != null && !csrf.isEmpty()) cookieBuilder.append("bili_jct=").append(csrf).append("; ");
        if (dedeUserId != null && !dedeUserId.isEmpty()) cookieBuilder.append("DedeUserID=").append(dedeUserId).append("; ");
        if (buvid3 != null && !buvid3.isEmpty()) cookieBuilder.append("buvid3=").append(buvid3).append("; ");
        if (buvid4 != null && !buvid4.isEmpty()) cookieBuilder.append("buvid4=").append(buvid4).append("; ");
        if (biliTicket != null && !biliTicket.isEmpty()) cookieBuilder.append("bili_ticket=").append(biliTicket);

        okhttp3.RequestBody fileBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/jpeg"), imageData);
        okhttp3.MultipartBody multipartBody = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("csrf", csrf)
                .addFormDataPart("face", fileName, fileBody)
                .addFormDataPart("platform", "pc")
                .addFormDataPart("csrf_token", csrf)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .post(multipartBody)
                .addHeader("Cookie", cookieBuilder.toString())
                .addHeader("Referer", "https://account.bilibili.com/home")
                .addHeader("Origin", "https://account.bilibili.com")
                .addHeader("User-Agent", NetWorkUtil.USER_AGENT_WEB)
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build();
        return new JSONObject(decompressResponse(NetWorkUtil.getOkHttpInstance().newCall(request).execute()));
    }

    /**
     * 读取响应体并处理br/gzip压缩，返回字符串
     */
    private static String decompressResponse(okhttp3.Response response) throws IOException {
        okhttp3.ResponseBody respBody = response.body();
        if (respBody == null) throw new IOException("响应体为空");
        String contentEncoding = response.header("Content-Encoding");
        byte[] bodyBytes = respBody.bytes();
        if ("br".equalsIgnoreCase(contentEncoding)) {
            try {
                return new String(com.netease.hearttouch.brotlij.Brotli.decompress(bodyBytes), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                return new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        } else if ("gzip".equalsIgnoreCase(contentEncoding)) {
            try (GZIPInputStream gzipStream = new GZIPInputStream(new ByteArrayInputStream(bodyBytes));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(gzipStream, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        }
        return new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
