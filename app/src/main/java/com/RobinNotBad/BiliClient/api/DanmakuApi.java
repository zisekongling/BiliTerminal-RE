package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.DmSegMobileReply;
import com.RobinNotBad.BiliClient.player.DanmakuLoadPolicy;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.ProtobufParser;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.Response;

//弹幕api

public class DanmakuApi {

    // 发送弹幕
    public static int sendVideoDanmakuByBvid(long cid, String msg, String bvid, long progress, int color, int mode)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/dm/post";
        String arg = "type=1&oid=" + cid + "&msg=" + URLEncoder.encode(msg, "UTF-8") + "&bvid=" + bvid + "&progress=" + progress + "&color=" + color
                + "&mode=" + mode + "&rnd=" + (System.currentTimeMillis() * 1000000) + "&csrf="
                + NetWorkUtil.currentCsrf();
        JSONObject result = new JSONObject(
                Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Logu.i(result.toString());
        return result.getInt("code"); // https://socialsisteryi.github.io/bilibili-API-collect/docs/danmaku/action.html#%E5%8F%91%E9%80%81%E8%A7%86%E9%A2%91%E5%BC%B9%E5%B9%95
    }

    public static int sendVideoDanmakuByAid(long cid, String msg, long aid, long progress, int color, int mode)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/dm/post";
        String arg = "type=1&oid=" + cid + "&msg=" + URLEncoder.encode(msg, "UTF-8") + "&aid=" + aid + "&progress=" + progress + "&color=" + color
                + "&mode=" + mode + "&rnd=" + (System.currentTimeMillis() * 1000000) + "&csrf="
                + NetWorkUtil.currentCsrf();
        JSONObject result = new JSONObject(
                Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Logu.i(result.toString());
        return result.getInt("code"); // https://socialsisteryi.github.io/bilibili-API-collect/docs/danmaku/action.html#%E5%8F%91%E9%80%81%E8%A7%86%E9%A2%91%E5%BC%B9%E5%B9%95
    }

    // 点赞弹幕
    public static int likeDanmaku(long dmid, long cid, int op) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/dm/thumbup/add";
        String arg = "oid=" + cid + "&dmid=" + dmid + "&op=" + op + "&platform=web_player" + "&csrf="
                + NetWorkUtil.currentCsrf();
        JSONObject result = new JSONObject(
                Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Logu.i(result.toString());
        return result.getInt("code");
    }

    // 撤回弹幕
    public static int recallDanmaku(long dmid, long cid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/dm/recall";
        // 文档里就是cid，不是oid
        String arg = "cid=" + cid + "&dmid=" + dmid + "&csrf=" + NetWorkUtil.currentCsrf();
        JSONObject result = new JSONObject(
                Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Logu.i(result.toString());
        return result.getInt("code");
    }

    /**
     * 查视频弹幕总数（只读统计，**不下载任何弹幕内容**）。
     *
     * 取的是稿件信息接口的 `data.stat.danmaku`——与详情页显示的弹幕数是同一个值，
     * 但播放页不会去拉详情，所以这里单独查一次轻量接口。
     *
     * 供「弹幕数量保护」在加载分段前判断是否跳过（见
     * {@link com.RobinNotBad.BiliClient.player.DanmakuLoadPolicy}）。
     *
     * @param aid 稿件 avid
     * @return 弹幕总数；接口失败/未登录/字段缺失返回
     *         {@link com.RobinNotBad.BiliClient.player.DanmakuLoadPolicy#COUNT_UNKNOWN}，
     *         调用方按「数量未知」失败开放处理（照常加载）
     */
    public static long getVideoDanmakuCount(long aid) {
        if (aid <= 0) return DanmakuLoadPolicy.COUNT_UNKNOWN;
        try {
            String url = "https://api.bilibili.com/x/web-interface/view?aid=" + aid;
            JSONObject body = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
            long count = DanmakuLoadPolicy.parseCount(body);
            Logu.d("弹幕数量", "aid=" + aid + " count=" + count);
            return count;
        } catch (Exception e) {
            Logu.w("弹幕数量", "查询失败 aid=" + aid + "：" + e.getMessage());
            return DanmakuLoadPolicy.COUNT_UNKNOWN;
        }
    }

    /**
     * 每段弹幕覆盖的时长（秒）：B 站 `seg.so` 一段 6 分钟。
     * `segment_index` **从 1 开始**（0 是无效值，服务端返回空）。
     */
    public static final int SEGMENT_SECONDS = 360;

    /**
     * 计算需要请求的分段数（纯函数，便于单测）。
     *
     * 必须是 `ceil(duration / 360)`：旧实现写 `duration / 360 + 1`，时长恰好是 360 的倍数时
     * 会多发一次必然为空的请求；而时长统计本身也会因为 `duration` 未就绪回落成 3600 秒
     * （11 段），在手表上等于每次播放都多出十几个请求。
     *
     * @param maxDurationSec 视频时长（秒）。&lt;= 0 表示未知，按 1 段处理。
     */
    public static int segmentCountFor(int maxDurationSec) {
        if (maxDurationSec <= 0) return 1;
        return (maxDurationSec + SEGMENT_SECONDS - 1) / SEGMENT_SECONDS;
    }

    /**
     * 获取视频弹幕（新版 API，返回 protobuf 格式）
     * 使用分段获取方式，每段 6 分钟
     *
     * @param aid          稿件 avid
     * @param cid          视频 cid
     * @param segmentIndex 分包索引（6min 一包，**从 1 开始**，见 {@link #SEGMENT_SECONDS}）
     * @return 弹幕分段响应
     * @throws IOException   IO异常
     * @throws JSONException JSON异常
     */
    public static DmSegMobileReply getVideoDanmakuSegment(long aid, long cid, int segmentIndex)
            throws IOException, JSONException {
        byte[] data = getVideoDanmakuSegmentBytes(aid, cid, segmentIndex);

        // 解析 protobuf 数据
        DmSegMobileReply reply = ProtobufParser.parseDmSegMobileReply(data);
        Logu.d("新版弹幕API", "分段 " + segmentIndex + " 获取到 " + reply.elems.size() + " 条弹幕");

        return reply;
    }

    /**
     * 拉取单个分段的**原始 protobuf 字节**（不解码）。
     *
     * 给弹幕窗口加载用（`player/DanmakuStreamLoader`）：同一份字节既可以被
     * {@link #parseDanmakuSegment(byte[])} 解析，也能原样落进
     * `util/DanmakuDiskCache` 的磁盘缓存（按 cid + segmentIndex 存），下次播放直接读盘，
     * 不再走 WBI 签名请求。语义与 {@link #getVideoDanmakuSegment} 完全一致（同 URL、同签名）。
     *
     * @param aid          稿件 avid
     * @param cid          视频 cid
     * @param segmentIndex 分包索引（**从 1 开始**）
     * @return protobuf 原始字节
     * @throws IOException   IO异常
     * @throws JSONException JSON异常
     */
    public static byte[] getVideoDanmakuSegmentBytes(long aid, long cid, int segmentIndex)
            throws IOException, JSONException {
        String url = buildSegmentUrl(aid, cid, segmentIndex);

        // 发送请求获取 protobuf 数据
        Response response = NetWorkUtil.get(url, NetWorkUtil.webHeaders);
        byte[] data = Objects.requireNonNull(response.body()).bytes();
        Logu.d("新版弹幕API", "分段 " + segmentIndex + " 下载 " + data.length + " 字节");
        return data;
    }

    /**
     * 拉一个分段并重试一次，返回原始字节；**失败返回 null**（不抛异常）。
     *
     * 窗口加载器要的是「这一批段里能拿到几段就先喂几段」，单个分段失败绝不能把整批拖垮，
     * 所以这里把异常收成 null，由调用方决定是留空还是等 15 秒后重试。
     */
    public static byte[] getVideoDanmakuSegmentBytesWithRetry(long aid, long cid, int segmentIndex) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return getVideoDanmakuSegmentBytes(aid, cid, segmentIndex);
            } catch (Exception e) {
                Logu.e("新版弹幕API", "获取分段 " + segmentIndex + " 第 " + attempt + " 次失败: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * 解析一段弹幕 protobuf 字节（纯解码，不发请求）。
     *
     * 抽出来的原因：磁盘缓存命中时手里只有字节，没有 {@link DmSegMobileReply}。
     */
    public static DmSegMobileReply parseDanmakuSegment(byte[] data) throws IOException {
        return ProtobufParser.parseDmSegMobileReply(data);
    }

    /** 拼 `seg.so` 请求 URL 并做 WBI 签名（分段接口的唯一入口，别再复制粘贴 URL）。 */
    private static String buildSegmentUrl(long aid, long cid, int segmentIndex)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/dm/wbi/web/seg.so" + "?type=1" +
                "&oid=" + cid +
                "&pid=" + aid +
                "&segment_index=" + segmentIndex;

        // 使用 WBI 签名
        url = ConfInfoApi.signWBI(url);

        Logu.d("新版弹幕API", "URL: " + url);
        return url;
    }

    /**
     * 获取视频所有弹幕（新版 API）
     * 自动分段获取所有弹幕
     *
     * @param aid         稿件 avid
     * @param cid         视频 cid
     * @param maxDuration 视频最大时长（秒），用于计算需要获取的分段数
     * @return 所有弹幕分段的列表
     * @throws IOException   IO异常
     * @throws JSONException JSON异常
     */
    public static List<DmSegMobileReply> getAllVideoDanmaku(long aid, long cid, int maxDuration)
            throws IOException, JSONException {
        final int segmentCount = segmentCountFor(maxDuration);

        Logu.d("新版弹幕API", "视频时长: " + maxDuration + "秒, 需要获取 " + segmentCount + " 个分段");

        // 并发获取各分段，按顺序汇总（网络 IO 是主要耗时，串行等待多个 RTT 太慢）
        DmSegMobileReply[] segments = new DmSegMobileReply[segmentCount];
        if (segmentCount <= 1) {
            segments[0] = fetchSegmentWithRetry(aid, cid, 1);
        } else {
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(segmentCount, 4));
            List<Future<DmSegMobileReply>> futures = new ArrayList<>(segmentCount);
            for (int i = 0; i < segmentCount; i++) {
                final int index = i;
                futures.add(executor.submit(() -> fetchSegmentWithRetry(aid, cid, index + 1)));
            }
            executor.shutdown();
            for (int i = 0; i < segmentCount; i++) {
                try {
                    segments[i] = futures.get(i).get();
                } catch (Exception e) {
                    Logu.e("新版弹幕API", "获取分段 " + (i + 1) + " 失败: " + e.getMessage());
                }
            }
        }

        List<DmSegMobileReply> allSegments = new ArrayList<>();
        for (DmSegMobileReply segment : segments) {
            if (segment != null && !segment.elems.isEmpty()) {
                allSegments.add(segment);
            }
        }

        // **「拉取失败」和「成功但为空」是两件事**，不能混在一起报：
        // 分段数按兜底时长（`duration` 未就绪时 3600 秒）算，短 P 的 2~10 段本来就没有弹幕，
        // 那是正常结果；只有 `segments[i] == null`（重试后仍失败）才是真缺口。
        // 混报会让"缺口"日志天天刷，真出问题时反而没人信。
        int missingFrom = -1;
        for (int i = 0; i <= segmentCount; i++) {
            boolean failed = i < segmentCount && segments[i] == null;
            if (failed && missingFrom < 0) {
                missingFrom = i;
            } else if (!failed && missingFrom >= 0) {
                Logu.e("新版弹幕API", "弹幕缺口: 分段 " + (missingFrom + 1) + "~" + i
                        + " 拉取失败（" + (missingFrom * SEGMENT_SECONDS) + "~" + (i * SEGMENT_SECONDS) + " 秒无弹幕）");
                missingFrom = -1;
            }
        }

        int totalDanmaku = 0;
        for (DmSegMobileReply segment : allSegments) {
            totalDanmaku += segment.elems.size();
        }
        Logu.d("新版弹幕API", "共获取到 " + totalDanmaku + " 条弹幕（" + allSegments.size() + "/" + segmentCount + " 段非空）");

        return allSegments;
    }

    /**
     * 拉一个分段，失败重试一次。
     *
     * 重试的价值：这是大并发 + WBI 签名的请求，偶发 `-352`/超时很常见，而**任何一段失败都会
     * 在时间轴上留下 6 分钟的空洞**（不是"少几条"，是整段消失），用户只会描述成"弹幕突然没了"。
     */
    private static DmSegMobileReply fetchSegmentWithRetry(long aid, long cid, int segmentIndex) {
        byte[] data = getVideoDanmakuSegmentBytesWithRetry(aid, cid, segmentIndex);
        if (data == null) return null;
        try {
            return ProtobufParser.parseDmSegMobileReply(data);
        } catch (Exception e) {
            Logu.e("新版弹幕API", "解析分段 " + segmentIndex + " 失败: " + e.getMessage());
            return null;
        }
    }
}
