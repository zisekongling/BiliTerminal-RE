package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.util.UiText;
import com.RobinNotBad.BiliClient.R;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Pair;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.activity.search.SearchActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;
import okhttp3.ResponseBody;

public class LinkUrlUtil {
    public static final int TYPE_USER = -1;
    public static final int TYPE_WEB_URL = 0;
    public static final int TYPE_BVID = 1;
    public static final int TYPE_AVID = 2;
    public static final int TYPE_CVID = 3;
    public static final int TYPE_UID = 4;
    /** 话题（{@code #话题名#}）。val 形如 {@code https://m.bilibili.com/topic-detail?topic_id=1305890}。 */
    public static final int TYPE_TOPIC = 5;
    public static final Pattern BV_PATTERN = Pattern.compile("BV[A-Za-z0-9]{10}");
    public static final Pattern AV_PATTERN = Pattern.compile("av\\d{1,10}");
    public static final Pattern CV_PATTERN = Pattern.compile("cv\\d{1,10}");
    public static final Pattern UID_PATTERN = Pattern.compile("^(?i)uid\\d+$");
    public static final Pattern OPUS_PATTERN = Pattern.compile("^/opus/(\\d+)/?$");
    /** 从话题跳转链接里抠出 topic_id，形如 {@code .../topic-detail?topic_id=1305890&topic_name=xx}。 */
    public static final Pattern TOPIC_ID_PATTERN = Pattern.compile("[?&]topic_id=(\\d+)");
    /** 动态正文话题节点的跳转域名：26.10.19 实测全站动态列表/动态详情下发的都是这个站内搜索链接。 */
    public static final String TOPIC_SEARCH_DOMAIN = "search.bilibili.com";
    /** 从站内搜索链接里抠出 keyword 参数（未解码，形如 {@code keyword=%E7%BB%9D%E5%8C%BA%E9%9B%B6}）。 */
    public static final Pattern SEARCH_KEYWORD_PATTERN = Pattern.compile("[?&]keyword=([^&]*)");

    /**
     * 从话题跳转链接里解析 topic_id。
     *
     * 话题的 {@code jump_url} 形如
     * {@code https://m.bilibili.com/topic-detail?topic_id=1305890&topic_name=%E5%A4%A9%E4%BE%9D...}，
     * 我们只需要 {@code topic_id} 就能进站内话题页（{@code DynamicTopicActivity}）。
     * 解析不出来返回 0，调用方据此退化处理。
     */
    public static long parseTopicId(String jumpUrl) {
        if (jumpUrl == null) return 0L;
        Matcher matcher = TOPIC_ID_PATTERN.matcher(jumpUrl);
        if (!matcher.find()) return 0L;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 从话题节点的 jump_url 里解析「去搜索」用的关键词。
     *
     * <p><b>为什么需要它</b>：26.10.19 用真实登录态把 {@code feed/all} 与 {@code detail} 两条接口
     * 的富文本节点全扫了一遍，{@code RICH_TEXT_NODE_TYPE_TOPIC} 的 jump_url **只有一种形态**
     * {@code //search.bilibili.com/all?keyword=%E7%BB%9D%E5%8C%BA%E9%9B%B6}——既没有 topic_id
     * 也没有 rid。所以 {@link #parseTopicId} 必然返回 0，光靠它话题永远点不动。
     * 取不出 topic_id 时按服务端给的语义去站内搜索，是唯一与网页行为一致的落点
     * （"话题名反查 topic_id"实测不可行：{@code x/topic/pub/search} 只有模糊搜索，绝区零/星塔旅人
     * 这类主话题都搜不到精确同名；{@code x/topic/web/details/top} 必须传 id）。
     *
     * <p>解析不出（空串、不是 search.bilibili.com 的链接、没有 keyword、解码失败）统一返回空串，
     * 调用方据此退化成「只上色不可点」。
     */
    public static String parseTopicSearchKeyword(String jumpUrl) {
        if (jumpUrl == null || jumpUrl.isEmpty()) return "";
        if (!jumpUrl.contains(TOPIC_SEARCH_DOMAIN)) return "";
        Matcher matcher = SEARCH_KEYWORD_PATTERN.matcher(jumpUrl);
        if (!matcher.find()) return "";
        try {
            String keyword = URLDecoder.decode(matcher.group(1), "UTF-8").trim();
            return keyword;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 解析网页链接并跳进站内页面；解析不出来时交给系统浏览器（页面内点击的既有行为）。
     *
     * @see #handleWebURL(Context, String, boolean)
     */
    public static void handleWebURL(Context context, String text) {
        handleWebURL(context, text, true);
    }

    /**
     * 解析网页链接并跳进站内页面。
     *
     * @param fallbackToBrowser 解析不出来时是否退化成「交给系统浏览器打开」。
     *                          页面内点击（私信、评论、搜索框）要 true；**导出的深链入口必须 false**——
     *                          那边本来就在响应 ACTION_VIEW，再 startActivity 一次会把我们自己的
     *                          intent-filter 又命中一遍，用户看到的只会是「点了没反应」（反馈 #322）。
     * @return true = 已跳进站内页面，或已交给短链解析（稍后在回调里再跳）；false = 这个链接没能识别
     */
    public static boolean handleWebURL(Context context, String text, boolean fallbackToBrowser) {
        try {
            text = (text.startsWith("http://") || text.startsWith("https://") ? text : (text.startsWith("//") ? "http:" + text : "http://" + text));
            // 很傻逼的一系列解析
            URL url = new URL(text);
            String path = url.getPath();
            int index = path.indexOf('?');
            if (index != -1) {
                path = path.substring(0, index);
            }
            String domain = url.getHost();
            if ("b23.tv".equals(domain)) {
                handleShortUrl(context, text);
                return true;
            }
            if (isBilibiliDomain(domain)) {
                if (!path.isEmpty()) {
                    Matcher opusMatcher = OPUS_PATTERN.matcher(path);
                    if (opusMatcher.matches()) {
                        TerminalContext.getInstance().enterOpusDetailPage(context, Long.parseLong(opusMatcher.group(1)));
                        return true;
                    }
                    String lastPathItem = path.replaceAll(".*/([^/]+)/?$", "$1");
                    if ("space.bilibili.com".equals(domain)) {
                        lastPathItem = "UID" + lastPathItem;
                    }
                    if ("search.bilibili.com".equals(domain)) {
                        // getQuery() 在 ?keyword=x 之外（例如 /search?page=2）也可能为 null，
                        // 旧写法直接 .replace() 会 NPE，被外层 catch 吞掉后仍落到浏览器
                        String query = url.getQuery();
                        Intent intent = new Intent(context, SearchActivity.class);
                        intent.putExtra("keyword", URLDecoder.decode(query == null ? "" : query.replace("keyword=", "")));
                        context.startActivity(intent);
                        return true;
                    }
                    Pair<String, Integer> parse = parseId(lastPathItem);
                    if (parse != null) {
                        String val = parse.first;
                        int type = parse.second;
                        switch (type) {
                            case TYPE_BVID:
                                TerminalContext.getInstance().enterVideoDetailPage(context, val);
                                return true;
                            case TYPE_AVID:
                                TerminalContext.getInstance().enterVideoDetailPage(context, Long.parseLong(val.replace("av", "")));
                                return true;
                            case TYPE_CVID:
                                TerminalContext.getInstance().enterArticleDetailPage(context, Long.parseLong(val.replace("cv", "")));
                                return true;
                            case TYPE_UID:
                                BiliTerminal.jumpToUser(context, Long.parseLong(val.replaceFirst("(?i)^uid", "")));
                                return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            MsgUtil.err(e);
        }
        if (!fallbackToBrowser) return false;
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(text)));
        } catch (ActivityNotFoundException e) {
            MsgUtil.showMsg(UiText.of("没有可处理此链接的应用！"));
        } catch (Throwable th) {
            MsgUtil.err(th);
        }
        return true;
    }

    /**
     * B 站域名判定。
     *
     * 通配符只匹配子域，裸域 {@code bilibili.com} 要单独写——主清单里的 intent-filter 踩过同一个坑
     * （见 app/src/main/AndroidManifest.xml 里 GetIntentActivity 那段注释）。
     */
    private static boolean isBilibiliDomain(String host) {
        return "bilibili.com".equals(host) || host.matches(".*\\.bilibili\\.com$");
    }

    public static void handleId(Context context, String text) {
        Pair<String, Integer> parse = parseId(text);
        if (parse != null) {
            String val = parse.first;
            int type = parse.second;
            switch (type) {
                case TYPE_BVID:
                    TerminalContext.getInstance().enterVideoDetailPage(context, val);
                    break;
                case TYPE_AVID:
                    TerminalContext.getInstance().enterVideoDetailPage(context, Long.parseLong(val.replace("av", "")));
                    break;
                case TYPE_CVID:
                    TerminalContext.getInstance().enterArticleDetailPage(context, Long.parseLong(val.replace("cv", "")));
                    break;
                case TYPE_UID:
                    BiliTerminal.jumpToUser(context, Long.parseLong(val.replaceFirst("(?i)^uid", "")));
                    break;
                case TYPE_WEB_URL:
                    handleWebURL(context, val);
                    break;
            }
        }
    }

    private static void handleShortUrl(Context context, String url) {
        CenterThreadPool.run(() -> {
            // 必须 try-with-resources：code() != 200（短链 302、白名单外 3xx、404/412）时
            // 下面根本不读 body，而 OkHttp 只在 body 读到 EOF 时才把连接还给连接池。
            try (Response response = NetWorkUtil.get(url, NetWorkUtil.webHeaders, location -> handleWebURL(context, location))) {
                ResponseBody body;
                if (response.code() == 200 && (body = response.body()) != null) {
                    JSONObject json = new JSONObject(body.string());
                    if (json.has("code") && json.getInt("code") == -404) {
                        MsgUtil.showMsg(context.getString(R.string.text_list_empty));
                    }
                }
            } catch (IOException | JSONException e) {
                MsgUtil.showMsg(context.getString(R.string.text_parse_failed));
            }
        });
    }

    private static Pair<String, Integer> parseId(String item) {
        Matcher matcher;

        matcher = BV_PATTERN.matcher(item);
        if (matcher.find()) {
            return new Pair<>(matcher.group(), TYPE_BVID);
        }

        matcher = AV_PATTERN.matcher(item);
        if (matcher.find()) {
            return new Pair<>(matcher.group(), TYPE_AVID);
        }

        matcher = CV_PATTERN.matcher(item);
        if (matcher.find()) {
            return new Pair<>(matcher.group(), TYPE_CVID);
        }

        matcher = UID_PATTERN.matcher(item);
        if (matcher.find()) {
            return new Pair<>(matcher.group(), TYPE_UID);
        }

        matcher = Pattern.compile("(https?|ftp|file)://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]").matcher(item);
        if (matcher.find()) {
            return new Pair<>(matcher.group(), TYPE_WEB_URL);
        }

        return null;
    }
}
