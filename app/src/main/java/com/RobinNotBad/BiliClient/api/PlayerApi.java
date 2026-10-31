package com.RobinNotBad.BiliClient.api;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.player.PlayerActivity;
import com.RobinNotBad.BiliClient.activity.settings.SettingPlayerChooseActivity;
import com.RobinNotBad.BiliClient.activity.video.JumpToPlayerActivity;
import com.RobinNotBad.BiliClient.model.DashAudioStream;
import com.RobinNotBad.BiliClient.model.DashData;
import com.RobinNotBad.BiliClient.model.DashVideoStream;
import com.RobinNotBad.BiliClient.model.HighEnergyData;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.model.Subtitle;
import com.RobinNotBad.BiliClient.model.SubtitleLink;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.service.DownloadService;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SettingsKeys;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PlayerApi {
    //续播进度的合理上限（24h）：超过它一定是脏数据/单位错误，宁可从头播也不能跳到离谱位置
    private static final long MAX_PROGRESS_MS = 24L * 60 * 60 * 1000;

    public static void startGettingUrl(PlayerData playerData) {
        Context context = BiliTerminal.context;

        Intent intent = new Intent()
                .setClass(context, JumpToPlayerActivity.class)
                .putExtra("data", playerData)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void startDownloading(VideoInfo videoInfo, int page, int qn) {
        if (SharedPreferencesUtil.getBoolean("dev_download_old", false)) {
            Context context = BiliTerminal.context;

            Intent intent = new Intent(context, JumpToPlayerActivity.class)
                    .putExtra("data", videoInfo.toPlayerData(page))
                    .putExtra("download", (videoInfo.pagenames.size() == 1 ? 1 : 2)) // 1：单页 2：分页
                    .putExtra("cover", videoInfo.cover)
                    .putExtra("parent_title", videoInfo.title)
                    .putExtra("qn", qn)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }

        if (videoInfo.cids.size() == 1)
            DownloadService.startDownload(videoInfo.title,
                    videoInfo.aid, videoInfo.cids.get(0),
                    videoInfo.cover,
                    qn, "video", "");
        else
            DownloadService.startDownload(videoInfo.title, videoInfo.pagenames.get(page),
                    videoInfo.aid, videoInfo.cids.get(page),
                    videoInfo.cover,
                    qn, "video", "");
    }

    /**
     * 开始仅音频下载
     *
     * @param videoInfo 视频信息
     * @param page      页码
     * @param qn        清晰度
     * @param audioUrl  音频流URL
     */
    public static void startDownloadingAudioOnly(VideoInfo videoInfo, int page, int qn, String audioUrl) {
        if (videoInfo.cids.size() == 1)
            DownloadService.startDownload(videoInfo.title,
                    videoInfo.aid, videoInfo.cids.get(0),
                    videoInfo.cover,
                    qn, "audio_only", audioUrl);
        else
            DownloadService.startDownload(videoInfo.title, videoInfo.pagenames.get(page),
                    videoInfo.aid, videoInfo.cids.get(page),
                    videoInfo.cover,
                    qn, "audio_only", audioUrl);
    }

    /**
     * 解析视频（DASH格式）
     *
     * @param playerData 传入aid、cid、qn等必要数据
     */
    public static void getVideoDash(PlayerData playerData) throws JSONException, IOException {
        playerData.danmakuUrl = "https://comment.bilibili.com/" + playerData.cid + ".xml";

        String url = "https://api.bilibili.com/x/player/wbi/playurl?"
                + "avid=" + playerData.aid
                + "&cid=" + playerData.cid
                + "&qn=" + playerData.qn
                + "&fnval=4048&fnver=0" // 4048: DASH|HDR|4K|杜比全景声|杜比视界|8K|AV1
                + "&platform=pc"
                + "&fourk=1"
                + "&voice_balance=1"
                + "&gaia_source=pre-load"
                + "&isGaiaAvoided=true";

        url = ConfInfoApi.signWBI(url);

        JSONObject body = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        JSONObject data = body.getJSONObject("data");

        // 解析DASH数据
        if (data.has("dash")) {
            JSONObject dashJson = data.getJSONObject("dash");
            playerData.dashData = DashData.fromJson(dashJson);

            // 设置视频URL（选择指定清晰度的视频流）
            DashVideoStream videoStream = playerData.dashData.getVideoStream(playerData.qn);
            if (videoStream != null) {
                playerData.videoUrl = videoStream.baseUrl;
            }

            // 设置音频URL（选择最高质量的音频流）
            DashAudioStream audioStream = playerData.dashData.getBestAudioStream();
            if (audioStream != null) {
                playerData.audioUrl = audioStream.baseUrl;
            }
        } else {
            getVideo(playerData, true);
            return;
        }

        //last_play_cid/last_play_time 是 aid 级"上次播放"数据：进度只属于 last_play_cid 那一P。
        //请求的 cid 与上次播放的 cid 不一致时（多P视频换P续播）不能把别的P的进度套在本P上，应从 0 开始
        playerData.cidHistory = data.optLong("last_play_cid", 0);
        playerData.progress = adoptLastPlayTime(playerData.cidHistory, playerData.cid, data.optLong("last_play_time", 0));

        if (playerData.cidHistory == 0) {
            playerData.cidHistory = playerData.cid;
            playerData.progress = 0;
        }
        Logu.d("history", playerData.progress + "," + playerData.cidHistory);

        JSONArray accept_description = data.getJSONArray("accept_description");
        JSONArray accept_quality = data.getJSONArray("accept_quality");
        String[] qnStrList = new String[accept_description.length()];
        int[] qnValueList = new int[accept_description.length()];
        for (int i = 0; i < qnStrList.length; i++) {
            qnStrList[i] = accept_description.optString(i);
            qnValueList[i] = accept_quality.optInt(i);
        }
        Logu.d("qn_str", Arrays.toString(qnStrList));
        Logu.d("qn_val", Arrays.toString(qnValueList));
        playerData.qnStrList = qnStrList;
        playerData.qnValueList = qnValueList;
    }

    /**
     * 尝试以指定清晰度和fnval获取视频URL，用于强制高分辨率下载
     * @param playerData 传入aid、cid、qn等必要数据
     * @param fnval 视频流格式标识（如 16=DASH, 144=DASH+4K）
     * @return 是否成功获取到视频URL
     */
    public static boolean tryGetVideoWithFnval(PlayerData playerData, int fnval) {
        try {
            String url = "https://api.bilibili.com/x/player/wbi/playurl?"
                    + "avid=" + playerData.aid
                    + "&cid=" + playerData.cid
                    + "&qn=" + playerData.qn
                    + "&fnval=" + fnval + "&fnver=0"
                    + "&platform=pc"
                    + "&fourk=1"
                    + "&voice_balance=1"
                    + "&gaia_source=pre-load"
                    + "&isGaiaAvoided=true";

            url = ConfInfoApi.signWBI(url);

            JSONObject body = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
            JSONObject data = body.getJSONObject("data");

            // 尝试解析DASH数据
            if (data.has("dash")) {
                JSONObject dashJson = data.getJSONObject("dash");
                playerData.dashData = DashData.fromJson(dashJson);

                DashVideoStream videoStream = playerData.dashData.getVideoStream(playerData.qn);
                if (videoStream != null) {
                    playerData.videoUrl = videoStream.baseUrl;
                }

                DashAudioStream audioStream = playerData.dashData.getBestAudioStream();
                if (audioStream != null) {
                    playerData.audioUrl = audioStream.baseUrl;
                }

                if (playerData.videoUrl != null && !playerData.videoUrl.isEmpty()) {
                    return true;
                }
            }

            // 回退：尝试MP4格式
            JSONArray durl = data.getJSONArray("durl");
            if (durl != null && durl.length() > 0) {
                JSONObject videoUrlObj = durl.getJSONObject(0);
                playerData.videoUrl = videoUrlObj.getString("url");
                if (playerData.videoUrl != null && !playerData.videoUrl.isEmpty()) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            Logu.e("PlayerApi", "tryGetVideoWithFnval error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 解析视频
     *
     * @param playerData 传入aid、cid、qn等必要数据，可以使用VideoInfo.toPlayerData
     * @param download   是否下载
     */
    public static void getVideo(PlayerData playerData, boolean download) throws JSONException, IOException {
        // 如果上一次获取在十分钟内就无需再次获取了
        if (System.currentTimeMillis() - playerData.timeStamp < 600000)
            return;

        playerData.timeStamp = System.currentTimeMillis();

        playerData.danmakuUrl = "https://comment.bilibili.com/" + playerData.cid + ".xml";

        boolean html5 = !download && SharedPreferencesUtil.getString(SettingsKeys.PLAYER, "").equals("mtvPlayer");
        // html5方式现在已经仅对小电视播放器保留了

        String url = "https://api.bilibili.com/x/player/wbi/playurl?"
                + "avid=" + playerData.aid
                + "&cid=" + playerData.cid
                + (html5 ? "&high_quality=1" : "")
                + "&qn=" + playerData.qn
                + "&fnval=1&fnver=0"
                + "&platform=" + (html5 ? "html5" : "pc")
                + "&voice_balance=1"
                + "&gaia_source=pre-load"
                + "&isGaiaAvoided=true";

        url = ConfInfoApi.signWBI(url);

        JSONObject body = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        JSONObject data = body.getJSONObject("data");
        JSONArray durl = data.getJSONArray("durl");
        JSONObject video_url = durl.getJSONObject(0);
        playerData.videoUrl = video_url.getString("url");
        //last_play_cid/last_play_time 是 aid 级"上次播放"数据：进度只属于 last_play_cid 那一P。
        //请求的 cid 与上次播放的 cid 不一致时（多P视频换P续播）不能把别的P的进度套在本P上，应从 0 开始
        playerData.cidHistory = data.optLong("last_play_cid", 0);
        playerData.progress = adoptLastPlayTime(playerData.cidHistory, playerData.cid, data.optLong("last_play_time", 0));

        if (playerData.cidHistory == 0) {
            playerData.cidHistory = playerData.cid;
            playerData.progress = 0;
        }
        Logu.d("history", playerData.progress + "," + playerData.cidHistory);

        JSONArray accept_description = data.getJSONArray("accept_description");
        JSONArray accept_quality = data.getJSONArray("accept_quality");
        String[] qnStrList = new String[accept_description.length()];
        int[] qnValueList = new int[accept_description.length()];
        for (int i = 0; i < qnStrList.length; i++) {
            qnStrList[i] = accept_description.optString(i);
            qnValueList[i] = accept_quality.optInt(i);
        }
        Logu.d("qn_str", Arrays.toString(qnStrList));
        Logu.d("qn_val", Arrays.toString(qnValueList));
        playerData.qnStrList = qnStrList;
        playerData.qnValueList = qnValueList;

    }

    /**
     * 解析番剧，和普通视频的api不一样
     *
     * @param playerData 传入aid、cid、qn等必要数据
     */
    public static void getBangumi(PlayerData playerData) throws JSONException, IOException {
        NetWorkUtil.FormData reqData = new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("aid", playerData.aid)
                .put("cid", playerData.cid)
                .put("fnval", 1)
                .put("fnvar", 0)
                .put("qn", playerData.qn)
                .put("season_type", 1)
                .put("session",
                        ToolsUtil.md5(
                                String.valueOf(System.currentTimeMillis() - SystemClock.currentThreadTimeMillis())))
                .put("platform", "pc");

        String url = "https://api.bilibili.com/pgc/player/web/playurl" + reqData.toString();

        JSONObject body = NetWorkUtil.getJson(url);
        Logu.v(body.toString());

        JSONObject data = body.getJSONObject("result");
        JSONArray durl = data.getJSONArray("durl");
        JSONObject video_url = durl.getJSONObject(0);
        playerData.videoUrl = video_url.getString("url");

        playerData.danmakuUrl = "https://comment.bilibili.com/" + playerData.cid + ".xml";

        //番剧取流接口(pgc/player/web/playurl)的 result 不返回 last_play_*，续播进度必须单独查询，
        //否则 playerData.progress 一直是 0 —— 这正是"番剧每次进去都从头播"的原因
        playerData.cidHistory = playerData.cid;
        long lastProgress = getLastPlayProgress(playerData.aid, playerData.cid);
        if (lastProgress <= 0) {
            //WBI 接口(密钥/风控/未登录)取不到时兜底走观看记录列表，否则续播会永远从 0 开始。
            //带上 epid 一起校验：观看记录里的 oid 只是剧集 avid，光按 aid 匹配会把别的集的进度套过来
            lastProgress = HistoryApi.findProgressMsByAid(playerData.aid, playerData.epid);
            if (lastProgress > 0) Logu.w("history-last", "WBI 进度不可用，使用观看记录兜底: " + lastProgress + "ms");
        }
        //注意不能无条件赋值：调用方（番剧详情页从历史列表进来时）可能已经把已知进度塞进
        //playerData.progress，若这里查不到就赋 0，会把那份已知进度抹掉，用户又从头看起。
        //查到就用服务端的（按 aid+cid 查，粒度就是这个剧集），查不到才沿用调用方传入的值。
        int resolvedProgress = normalizeProgress(lastProgress, data.optLong("timelength", 0));
        if (resolvedProgress > 0) {
            playerData.progress = resolvedProgress;
        } else if (playerData.progress > 0) {
            Logu.w("history-last", "服务端番剧进度不可用，沿用调用方传入的 " + playerData.progress + "ms");
        } else {
            playerData.progress = 0;
        }

        JSONArray accept_description = data.getJSONArray("accept_description");
        JSONArray accept_quality = data.getJSONArray("accept_quality");
        String[] qnStrList = new String[accept_description.length()];
        int[] qnValueList = new int[accept_description.length()];
        for (int i = 0; i < qnStrList.length; i++) {
            qnStrList[i] = accept_description.optString(i);
            qnValueList[i] = accept_quality.optInt(i);
        }
        playerData.qnStrList = qnStrList;
        playerData.qnValueList = qnValueList;
    }

    /**
     * 查询某个稿件/剧集的"上次播放进度"（毫秒）。
     *
     * 为什么番剧要单独查：pgc/player/web/playurl 的 result 不返回 last_play_*，
     * 沿用投稿视频那套读法只会永远得到 0。
     * 这是"锦上添花"的查询，任何异常都退化为 0，由调用方再用观看记录兜底。
     */
    public static long getLastPlayProgress(long aid, long cid) {
        try {
            String url = "https://api.bilibili.com/x/player/wbi/v2?aid=" + aid + "&cid=" + cid;
            url = ConfInfoApi.signWBI(url);
            JSONObject body = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
            JSONObject data = body.optJSONObject("data");
            if (data == null) return 0;
            long lastPlayTime = data.optLong("last_play_time", 0);
            if (lastPlayTime <= 0) return 0;
            //last_play_time 与 last_play_cid 是一对：番剧按"季"维护这份记录，last_play_cid 才代表
            //它属于哪一集。请求的 cid 与它不一致时必须丢弃，否则"看着这一集点开另一集"会从上一集的
            //位置开始播（远端 v1.2.0fix 修的就是这个）。取不到该字段(0)时也按"不属于这一集"处理，
            //由调用方回落到观看记录兜底——宁可查一次列表，也不能给一个错的续播位置
            long lastPlayCid = data.optLong("last_play_cid", 0);
            if (lastPlayCid != cid) {
                Logu.w("history-last", "aid=" + aid + " 丢弃跨集的 last_play_time=" + lastPlayTime
                        + "ms（last_play_cid=" + lastPlayCid + " 请求 cid=" + cid + "）");
                return 0;
            }
            Logu.d("history-last", "aid=" + aid + " cid=" + cid + " last_play_time=" + lastPlayTime);
            return lastPlayTime;
        } catch (Exception e) {
            Logu.e("history-last", "查询上次播放进度失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * last_play_time 是与 last_play_cid 配对的"上次播放"进度，只属于那一 P。
     * 请求的 cid 与 last_play_cid 不一致时必须丢弃：否则在 P3 上会从 P2 的进度位置继续播。
     */
    private static int adoptLastPlayTime(long lastPlayCid, long requestCid, long lastPlayTime) {
        if (lastPlayCid != requestCid || lastPlayTime <= 0) return 0;
        return (int) lastPlayTime;
    }

    /**
     * 归一化续播进度。
     * last_play_time 官方文档标注为毫秒，但该字段单位并未被文档确证，
     * 因此按"明显超过视频时长"来探测秒单位；最后再挡掉越界值，
     * 避免把 -1(已看完) 或脏数据当进度传给播放器。
     */
    private static int normalizeProgress(long raw, long durationMs) {
        if (raw <= 0) return 0;
        long ms = raw;
        if (durationMs > 0 && ms > durationMs) {
            long asSeconds = raw * 1000L;
            if (asSeconds <= durationMs) {
                ms = asSeconds;
                Logu.d("history-last", "last_play_time 疑似单位为秒：" + raw);
            } else {
                Logu.e("history-last", "last_play_time 越界已丢弃：" + raw + " / " + durationMs);
                return 0;
            }
        }
        if (ms > MAX_PROGRESS_MS) {
            Logu.e("history-last", "last_play_time 超出合理范围已丢弃：" + ms);
            return 0;
        }
        return (int) ms;
    }

    /**
     * 跳转到播放器
     *
     * @param playerData 传入aid、cid、qn等必要数据
     * @return 播放器跳转Intent
     */
    public static Intent jumpToPlayer(PlayerData playerData) {
        Context context = BiliTerminal.context;
        Logu.v("准备跳转", "--------");
        Logu.v("视频标题", playerData.title);
        Logu.v("视频地址", playerData.videoUrl);
        Logu.v("弹幕地址", playerData.danmakuUrl);
        Logu.v("准备跳转", "--------");

        Intent intent = new Intent();
        switch (SharedPreferencesUtil.getString(SettingsKeys.PLAYER, "null")) {
            case "terminalPlayer":
                intent.setClass(context, PlayerActivity.class);
                intent.putExtra("url", playerData.videoUrl);
                intent.putExtra("danmaku", playerData.danmakuUrl);
                intent.putExtra("title", playerData.title);
                intent.putExtra("aid", playerData.aid);
                intent.putExtra("cid", playerData.cid);
                intent.putExtra("mid", playerData.mid);
                intent.putExtra("progress", playerData.progress);
                intent.putExtra("live_mode", playerData.isLive());
                // 直播的候选地址表随 Intent 一起进播放器：弱网/线路故障时播放器要能在不改请求的前提下换线路。
                if (playerData.liveUrlCandidates != null && !playerData.liveUrlCandidates.isEmpty()) {
                    intent.putStringArrayListExtra("live_url_candidates", playerData.liveUrlCandidates);
                }
                //番剧维度必须随 Intent 一起进播放器：缺了这三个值，播放器只能按投稿视频上报，
                //服务端的观看记录与续播进度都不会更新。放在这里而不是各调用点，是为了让
                //所有入口（详情页、历史列表、缓存列表等）都自动带上。
                if (playerData.epid != 0) {
                    intent.putExtra("epid", playerData.epid);
                    intent.putExtra("seasonId", playerData.seasonId);
                    intent.putExtra("seasonType", playerData.seasonType);
                }
                if (playerData.qnStrList != null && playerData.qnValueList != null) {
                    intent.putExtra("qnStrList", playerData.qnStrList);
                    intent.putExtra("qnValueList", playerData.qnValueList);
                    intent.putExtra("currentQuality", playerData.qn);
                }
                if (playerData.pagenames != null && playerData.pagenames.size() > 1) {
                    intent.putStringArrayListExtra("pagenames", playerData.pagenames);
                    if (playerData.cids != null) {
                        long[] cidArray = new long[playerData.cids.size()];
                        for (int i = 0; i < playerData.cids.size(); i++) {
                            cidArray[i] = playerData.cids.get(i);
                        }
                        intent.putExtra("cids", cidArray);
                    }
                    intent.putExtra("currentPageIndex", playerData.currentPageIndex);
                    // cids 里装的是 cid 还是 aid 必须显式告诉播放器：两种模式的切P 逻辑完全不同，
                    // 靠 aid != cids[i] 猜在真实多P 下恒真（av 号永不等于 cid），会把 cid 当 aid 查
                    intent.putExtra("virtual_collection", playerData.virtualCollection);
                    // 起播那一条的 videoInfo：播放器靠它决定选集卡片上要不要显示「进入分P/进入合集」
                    // 必须显式转 Parcelable——VideoInfo 同时实现了 Parcelable 和 Serializable，
                    // 不转的话 putExtra 两个重载都匹配，编译报"引用不明确"
                    intent.putExtra("enterable_info", (android.os.Parcelable) playerData.enterableInfo);
                }
                break;

            case "mtvPlayer":
                intent.setClassName(context.getString(R.string.player_package_mtv),
                        "com.xinxiangshicheng.wearbiliplayer.cn.player.PlayerActivity");
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra("cookie", buildPlayerCookie());
                intent.putExtra("mode", (playerData.isLocal() ? "2" : "0"));
                intent.putExtra("url", playerData.videoUrl);
                intent.putExtra("danmaku", playerData.danmakuUrl);
                intent.putExtra("title", playerData.title);
                intent.putExtra("live_mode", playerData.isLive());
                break;

            case "aliangPlayer":
                intent.setClassName(context.getString(R.string.player_package_aliang),
                        "com.aliangmaker.media.PlayVideoActivity");
                intent.putExtra("name", playerData.title);
                intent.putExtra("danmaku", playerData.danmakuUrl);
                intent.putExtra("live_mode", playerData.isLive());

                if (playerData.isLocal()) {
                    //本地文件不能直接 Uri.parse(裸路径)：scoped storage 下外部播放器读不到文件，
                    //必须走 FileProvider 的 content:// 并显式授予一次性只读权限，否则对方打开就报错
                    intent.setData(getVideoUri(context, playerData.videoUrl));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    intent.setData(Uri.parse(playerData.videoUrl));

                    Map<String, String> headers = new HashMap<>();
                    headers.put("Cookie", buildPlayerCookie());
                    headers.put("Referer", "https://www.bilibili.com/");
                    intent.putExtra("cookie", (Serializable) headers);
                    intent.putExtra("agent", NetWorkUtil.USER_AGENT_WEB);
                    intent.putExtra("progress", playerData.progress * 1000L);
                }
                intent.setAction(Intent.ACTION_VIEW);

                break;

            default:
                intent.setClass(context, SettingPlayerChooseActivity.class);
                break;
        }
        return intent;
    }

    /**
     * 允许跨进程传给外部播放器的 Cookie 键（白名单）。
     *
     * 审计 S9：原来直接把 SharedPreferences 里的完整 Cookie 串塞进 Intent extra，
     * 而 Intent extra 会跨进程交给用户安装的任意第三方播放器。完整 Cookie 里含
     * · {@code SESSDATA} —— 长期有效的账号凭证；
     * · {@code bili_jct} —— 写操作（发评论、改收藏、改设置）用的 CSRF token。
     * 也就是说任何被选作播放器的应用读一下 extra 就能完整接管账号。
     * 这与本项目 {@code allowBackup="false"}` + 备份规则专门排除 SharedPreferences
     * （理由写明"一旦导出等于账号被接管"）的安全约定自相矛盾。
     *
     * 播放只需要过 CDN 鉴权，下列几个键足够；{@code bili_jct} 与 {@code DedeUserID}
     * 对播放没有任何作用，一律不外传。
     */
    private static final String[] PLAYBACK_COOKIE_KEYS = {
            "SESSDATA", "buvid3", "buvid4", "bili_ticket"
    };

    /** 按白名单裁剪出最小播放凭证串（见 {@link #PLAYBACK_COOKIE_KEYS}）。 */
    private static String buildPlayerCookie() {
        String all = SharedPreferencesUtil.getString("cookies", "");
        StringBuilder sb = new StringBuilder();
        for (String key : PLAYBACK_COOKIE_KEYS) {
            String value = NetWorkUtil.getInfoFromCookie(key, all);
            if (value.isEmpty()) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(key).append('=').append(value);
        }
        return sb.toString();
    }

    public static Uri getVideoUri(Context context, String path) {
        File file = new File(path);
        //authority 必须与 AndroidManifest.xml 里声明的完全一致（大写 F 的 .FileProvider），
        //老代码写死的 ".fileprovider" 与之不符，调用即抛 IllegalArgumentException 找不到 provider
        return FileProvider.getUriForFile(context, context.getPackageName() + ".FileProvider", file);

        // 因为在文件夹里放了.nomedia标识，现在不能用这个了
        /*
         * Cursor cursor = context.getContentResolver().query(MediaStore.Video.Media.
         * EXTERNAL_CONTENT_URI,
         * new String[]{MediaStore.Video.Media._ID},
         * MediaStore.Video.Media.DATA + "=? ",
         * new String[]{path}, null);
         * if (cursor != null && cursor.moveToFirst()) {
         *
         * @SuppressLint("Range") int id =
         * cursor.getInt(cursor.getColumnIndex(MediaStore.Video.VideoColumns._ID));
         * Uri baseUri = Uri.parse("content://media/external/video/media");
         * cursor.close();
         * return Uri.withAppendedPath(baseUri, String.valueOf(id));
         * } else {
         * if (cursor != null) cursor.close();
         * ContentValues values = new ContentValues();
         * values.put(MediaStore.Video.Media.DATA, path);
         * return context.getContentResolver().insert(MediaStore.Video.Media.
         * EXTERNAL_CONTENT_URI, values);
         * }
         */
    }

    /**
     * 通过本地文件获取字幕
     *
     * @param folder 字幕文件夹
     * @return 字幕列表
     */
    public static SubtitleLink[] getSubtitleLinks(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        SubtitleLink[] links = new SubtitleLink[files != null ? (files.length + 1) : 1];
        if (files != null)
            for (int i = 0; i < files.length; i++) {
                links[i] = new SubtitleLink(i, files[i].getName(), files[i].toString(), false);
            }
        links[links.length - 1] = new SubtitleLink(-1, "不显示字幕", "null", false);
        return links;
    }

    /**
     * 获取视频的字幕链接列表
     *
     * @param aid aid
     * @param cid cid
     * @return 链接列表
     */
    public static SubtitleLink[] getSubtitleLinks(long aid, long cid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/player/wbi/v2?aid=" + aid
                + "&cid=" + cid;
        url = ConfInfoApi.signWBI(url);
        JSONObject data = NetWorkUtil.getJson(url).getJSONObject("data");

        JSONArray subtitles = data.getJSONObject("subtitle").getJSONArray("subtitles");
        Log.d("subtitle", subtitles.toString());

        SubtitleLink[] links = new SubtitleLink[subtitles.length() + 1];
        for (int i = 0; i < subtitles.length(); i++) {
            JSONObject subtitle = subtitles.getJSONObject(i);

            long id = subtitle.getLong("id");
            boolean isAI = subtitle.getInt("type") == 1;
            String lang = subtitle.getString("lan_doc");
            String subtitle_url = "https:" + subtitle.getString("subtitle_url");

            SubtitleLink link = new SubtitleLink(id, lang, subtitle_url, isAI);
            links[i] = link;
        }
        links[subtitles.length()] = new SubtitleLink(-1, "不显示字幕", "null", false);
        return links;
    }

    public static java.util.List<com.RobinNotBad.BiliClient.model.ViewPoint> getViewPoints(long aid, long cid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/player/wbi/v2?aid=" + aid
                + "&cid=" + cid;
        url = ConfInfoApi.signWBI(url);
        JSONObject data = NetWorkUtil.getJson(url).getJSONObject("data");

        java.util.List<com.RobinNotBad.BiliClient.model.ViewPoint> viewPoints = new java.util.ArrayList<>();
        
        if (data.has("view_points")) {
            JSONArray viewPointsArray = data.getJSONArray("view_points");
            for (int i = 0; i < viewPointsArray.length(); i++) {
                JSONObject vp = viewPointsArray.getJSONObject(i);
                String content = vp.optString("content", "");
                int from = vp.optInt("from", 0);
                int to = vp.optInt("to", 0);
                int type = vp.optInt("type", 0);
                String imgUrl = vp.optString("imgUrl", "");
                String logoUrl = vp.optString("logoUrl", "");
                
                viewPoints.add(new com.RobinNotBad.BiliClient.model.ViewPoint(content, from, to, type, imgUrl, logoUrl));
            }
        }
        
        return viewPoints;
    }

    public static long getInteractionGraphVersion(long aid, long cid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/player/wbi/v2?aid=" + aid
                + "&cid=" + cid;
        url = ConfInfoApi.signWBI(url);
        JSONObject data = NetWorkUtil.getJson(url).getJSONObject("data");
        
        if (data.has("interaction") && !data.isNull("interaction")) {
            JSONObject interaction = data.getJSONObject("interaction");
            if (interaction.has("graph_version")) {
                return interaction.getLong("graph_version");
            }
        }
        
        return 0;
    }

    /**
     * 通过链接获取字幕
     *
     * @param url 传入链接，可通过getSubtitleLinks()获取
     * @return 逐条字幕的列表，每条包含文本和始末时间，时间以秒为单位
     */
    public static Subtitle[] getSubtitle(String url) throws JSONException, IOException {
        JSONArray body = NetWorkUtil.getJson(url).getJSONArray("body");
        Subtitle[] subtitles = new Subtitle[body.length()];
        for (int i = 0; i < body.length(); i++) {
            JSONObject single = body.getJSONObject(i);
            subtitles[i] = new Subtitle(
                    single.getString("content"),
                    single.getDouble("from"),
                    single.getDouble("to"));
        }
        return subtitles;
    }

    /**
     * 通过本地文件获取字幕
     *
     * @param file 传入json文件
     * @return 逐条字幕的列表，每条包含文本和始末时间，时间以秒为单位
     */
    public static Subtitle[] getSubtitle(File file) throws JSONException {
        String str = FileUtil.readString(file);
        if (str == null)
            return null;

        JSONArray body = new JSONObject(str).getJSONArray("body");
        Subtitle[] subtitles = new Subtitle[body.length()];
        for (int i = 0; i < body.length(); i++) {
            JSONObject single = body.getJSONObject(i);
            subtitles[i] = new Subtitle(
                    single.getString("content"),
                    single.getDouble("from"),
                    single.getDouble("to"));
        }
        return subtitles;
    }

    /**
     * 获取高能进度条数据
     *
     * @param cid  视频分P的cid，全局唯一，接口靠它定位
     * @param aid  稿件avid；番剧、直播等场景可能为0
     * @param bvid 稿件bvid；拿不到就传空串，Referer 会退回 av 号
     */
    public static HighEnergyData getHighEnergyData(long cid, long aid, String bvid) {
        try {
            StringBuilder url = new StringBuilder("https://bvc.bilivideo.com/pbp/data?cid=").append(cid);
            if (aid > 0) url.append("&aid=").append(aid);
            if (bvid != null && !bvid.isEmpty()) url.append("&bvid=").append(bvid);
            // r=loader 是 web 播放器加载本接口时固定带的标记。
            // 2026-10 前后服务端改了行为：少了它（以及落在视频页上的 Referer），
            // 返回体里不再有 events 段，曲线就是空白的。
            url.append("&r=loader");

            JSONObject response = NetWorkUtil.getJson(url.toString(), pbpHeaders(bvid, aid));

            HighEnergyData data = parseHighEnergyData(response);
            if (data == null) {
                Logu.w("高能进度条", "未取得响应 code 或响应为空");
            } else if (!data.hasValidData()) {
                Logu.w("高能进度条", "返回体里没有 events.default");
            } else {
                Logu.d("高能进度条", "成功获取 " + data.events.length + " 个数据点，采样间隔: " + data.stepSec + "秒");
            }
            return data;
        } catch (Exception e) {
            Logu.e("高能进度条", "获取失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /** 兼容旧调用点：不知道 bvid 时用 av 号拼 Referer */
    public static HighEnergyData getHighEnergyData(long cid, long aid) {
        return getHighEnergyData(cid, aid, "");
    }

    /**
     * 纯解析：把 pbp 接口响应转成 {@link HighEnergyData}，不发请求、不打日志。
     * <p>
     * 响应体有两个已知形态：
     * 旧形态把数据直接摊在根上（{@code {"step_sec":3,"events":{"default":[...]}}}）；
     * 新形态多包了一层 {@code {"modules":[{"params":{"data":{...}}}]}}。
     * 这里两种都认，并且按"谁真的带 events.default"来挑层，
     * 免得结构猜错就静默画出一条空曲线。
     *
     * @return 响应为 null、或 code 非 0/缺失时返回 null；其余情况返回对象（可能没有 events）
     */
    public static HighEnergyData parseHighEnergyData(JSONObject response) {
        if (response == null) return null;

        int code = response.optInt("code", -1);
        if (code != 0 && code != -1) return null;

        HighEnergyData data = new HighEnergyData();
        JSONObject payload = pickPbpPayload(response);
        if (payload == null) {
            data.events = new float[0];
            return data;
        }

        data.stepSec = payload.optInt("step_sec", 10);
        data.tagStr = payload.optString("tagstr", "");
        data.debug = payload.optString("debug", "");

        JSONObject events = payload.optJSONObject("events");
        JSONArray defaultArray = events == null ? null : events.optJSONArray("default");
        if (defaultArray == null || defaultArray.length() == 0) {
            data.events = new float[0];
            return data;
        }

        float[] eventData = new float[defaultArray.length()];
        for (int i = 0; i < defaultArray.length(); i++) {
            eventData[i] = (float) defaultArray.optDouble(i, 0.0);
        }
        data.events = eventData;
        return data;
    }

    /**
     * 依次尝试四种位置：根上的 modules、data 里的 modules、data 本身、根自己。
     * 挑到多个候选时优先返回真正带 events.default 的那个，都不带才退回第一个候选
     * （这样至少能把 step_sec / debug 取出来，便于排查）。
     */
    private static JSONObject pickPbpPayload(JSONObject root) {
        if (root == null) return null;
        JSONObject data = root.optJSONObject("data");
        JSONObject[] candidates = {
                modulePayload(root),
                modulePayload(data),
                data,
                root
        };
        JSONObject first = null;
        for (JSONObject candidate : candidates) {
            if (candidate == null) continue;
            if (first == null) first = candidate;
            if (hasDefaultEvents(candidate)) return candidate;
        }
        return first;
    }

    /** 新形态 {"modules":[{"params":{"data":{...}}}] }，取第一个带 data 的模块 */
    private static JSONObject modulePayload(JSONObject obj) {
        if (obj == null) return null;
        JSONArray modules = obj.optJSONArray("modules");
        if (modules == null) return null;
        for (int i = 0; i < modules.length(); i++) {
            JSONObject module = modules.optJSONObject(i);
            if (module == null) continue;
            JSONObject params = module.optJSONObject("params");
            if (params == null) continue;
            JSONObject nested = params.optJSONObject("data");
            if (nested != null) return nested;
        }
        return null;
    }

    private static boolean hasDefaultEvents(JSONObject obj) {
        if (obj == null) return false;
        JSONObject events = obj.optJSONObject("events");
        if (events == null) return false;
        JSONArray defaultArray = events.optJSONArray("default");
        return defaultArray != null && defaultArray.length() > 0;
    }

    /**
     * pbp 接口要求 Referer 落在具体视频页上（站点根会被风控挡掉）。
     * 这里在全局请求头快照上复制一份，只替换 Referer——不改动全局表。
     */
    private static ArrayList<String> pbpHeaders(String bvid, long aid) {
        ArrayList<String> headers = new ArrayList<>(NetWorkUtil.webHeaders);
        String referer = buildPbpReferer(bvid, aid);
        for (int i = 0; i + 1 < headers.size(); i += 2) {
            if ("Referer".equalsIgnoreCase(headers.get(i))) {
                headers.set(i + 1, referer);
                return headers;
            }
        }
        headers.add("Referer");
        headers.add(referer);
        return headers;
    }

    /** 视频页 Referer，优先级：bvid &gt; av号 &gt; 站点根 */
    public static String buildPbpReferer(String bvid, long aid) {
        if (bvid != null && !bvid.isEmpty()) return "https://www.bilibili.com/video/" + bvid;
        if (aid > 0) return "https://www.bilibili.com/video/av" + aid;
        return "https://www.bilibili.com/";
    }
}
