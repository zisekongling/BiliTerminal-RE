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
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PlayerApi {
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

        playerData.cidHistory = data.optLong("last_play_cid", 0);
        playerData.progress = data.optInt("last_play_time", 0);

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

        boolean html5 = !download && SharedPreferencesUtil.getString("player", "").equals("mtvPlayer");
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
        playerData.cidHistory = data.optLong("last_play_cid", 0);
        playerData.progress = data.optInt("last_play_time", 0);

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
        switch (SharedPreferencesUtil.getString("player", "null")) {
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
                }
                break;

            case "mtvPlayer":
                intent.setClassName(context.getString(R.string.player_package_mtv),
                        "com.xinxiangshicheng.wearbiliplayer.cn.player.PlayerActivity");
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra("cookie", SharedPreferencesUtil.getString("cookies", ""));
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

                intent.setData(Uri.parse(playerData.videoUrl));

                if (!playerData.isLocal()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Cookie", SharedPreferencesUtil.getString("cookies", ""));
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

    public static Uri getVideoUri(Context context, String path) {
        File file = new File(path);
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);

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
     */
    public static HighEnergyData getHighEnergyData(long cid, long aid) {
        try {
            String url = "https://bvc.bilivideo.com/pbp/data?cid=" + cid;
            if (aid > 0) {
                url += "&aid=" + aid;
            }

            JSONObject response = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);

            if (response == null) {
                Logu.w("高能进度条", "响应为空");
                return null;
            }

            int code = response.optInt("code", -1);
            if (code != 0 && code != -1) {
                Logu.w("高能进度条", "API返回错误码: " + code);
                return null;
            }

            HighEnergyData data = new HighEnergyData();
            data.stepSec = response.optInt("step_sec", 10);
            data.tagStr = response.optString("tagstr", "");
            data.debug = response.optString("debug", "");

            JSONObject events = response.optJSONObject("events");
            if (events != null) {
                JSONArray defaultArray = events.optJSONArray("default");
                if (defaultArray != null && defaultArray.length() > 0) {
                    float[] eventData = new float[defaultArray.length()];
                    for (int i = 0; i < defaultArray.length(); i++) {
                        eventData[i] = (float) defaultArray.optDouble(i, 0.0);
                    }
                    data.events = eventData;
                    Logu.d("高能进度条", "成功获取 " + eventData.length + " 个数据点，采样间隔: " + data.stepSec + "秒");
                } else {
                    Logu.w("高能进度条", "default数组为空或不存在");
                    data.events = new float[0];
                }
            } else {
                Logu.w("高能进度条", "events对象不存在");
                data.events = new float[0];
            }

            return data;
        } catch (Exception e) {
            Logu.e("高能进度条", "获取失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
