package com.RobinNotBad.BiliClient.util;

import android.content.Context;

import com.RobinNotBad.BiliClient.BiliTerminalApp;
import com.RobinNotBad.BiliClient.service.SpeedDownloadService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Aria2Util {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String PREF_ARIA2_ENABLED = "aria2_enabled";
    private static final String PREF_ARIA2_BUILTIN = "aria2_builtin";
    private static final String PREF_ARIA2_RPC_URL = "aria2_rpc_url";
    private static final String PREF_ARIA2_SECRET = "aria2_secret";
    private static final String PREF_ARIA2_MAX_CONCURRENT = "aria2_max_concurrent";
    private static final String PREF_ARIA2_MAX_CONNECTION = "aria2_max_connection";
    private static final String PREF_ARIA2_SPLIT = "aria2_split";
    private static final String PREF_ARIA2_DIR = "aria2_dir";
    private static final String PREF_PARALLEL_DOWNLOAD_VIDEOS = "parallel_download_videos";

    public static final int DEFAULT_PARALLEL_DOWNLOAD_VIDEOS = 3;

    private static volatile Context appContext;

    private static OkHttpClient client;

    static {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static boolean isEnabled() {
        return SharedPreferencesUtil.getBoolean(PREF_ARIA2_ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        SharedPreferencesUtil.putBoolean(PREF_ARIA2_ENABLED, enabled);
    }

    public static boolean isBuiltin() {
        return SharedPreferencesUtil.getBoolean(PREF_ARIA2_BUILTIN, true);
    }

    public static void setBuiltin(boolean builtin) {
        SharedPreferencesUtil.putBoolean(PREF_ARIA2_BUILTIN, builtin);
    }

    public static String getRpcUrl() {
        return SharedPreferencesUtil.getString(PREF_ARIA2_RPC_URL, "http://127.0.0.1:6800/jsonrpc");
    }

    public static void setRpcUrl(String url) {
        SharedPreferencesUtil.putString(PREF_ARIA2_RPC_URL, url);
    }

    public static String getSecret() {
        return SharedPreferencesUtil.getString(PREF_ARIA2_SECRET, "");
    }

    public static void setSecret(String secret) {
        SharedPreferencesUtil.putString(PREF_ARIA2_SECRET, secret);
    }

    public static int getMaxConcurrent() {
        return SharedPreferencesUtil.getInt(PREF_ARIA2_MAX_CONCURRENT, 5);
    }

    public static void setMaxConcurrent(int max) {
        SharedPreferencesUtil.putInt(PREF_ARIA2_MAX_CONCURRENT, max);
    }

    public static int getMaxConnection() {
        return SharedPreferencesUtil.getInt(PREF_ARIA2_MAX_CONNECTION, 16);
    }

    public static void setMaxConnection(int max) {
        SharedPreferencesUtil.putInt(PREF_ARIA2_MAX_CONNECTION, max);
    }

    public static int getSplit() {
        return SharedPreferencesUtil.getInt(PREF_ARIA2_SPLIT, 5);
    }

    public static void setSplit(int split) {
        SharedPreferencesUtil.putInt(PREF_ARIA2_SPLIT, split);
    }

    public static int getParallelDownloadVideos() {
        return SharedPreferencesUtil.getInt(PREF_PARALLEL_DOWNLOAD_VIDEOS, DEFAULT_PARALLEL_DOWNLOAD_VIDEOS);
    }

    public static void setParallelDownloadVideos(int count) {
        SharedPreferencesUtil.putInt(PREF_PARALLEL_DOWNLOAD_VIDEOS, count);
    }

    public static String getDownloadDir() {
        return FileUtil.getVideoDownloadPath().toString();
    }

    public static String addDownloadUri(String url, String fileName) {
        return addDownloadUri(url, fileName, getDownloadDir());
    }

    public static String addDownloadUri(String url, String fileName, String dir) {
        if (isBuiltin()) {
            return addBuiltin(url, fileName, dir);
        }
        return addExternal(url, fileName, dir);
    }

    private static String addBuiltin(String url, String fileName, String dir) {
        try {
            Context ctx = BiliTerminalApp.context;
            SpeedDownloadService.startDownload(ctx, url, fileName, dir);
            return "{\"result\":\"builtin\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String addExternal(String url, String fileName, String dir) {
        try {
            JSONArray params = new JSONArray();
            params.put("token:" + secretToken());
            JSONArray uris = new JSONArray();
            uris.put(url);
            params.put(uris);

            JSONObject options = new JSONObject();
            if (fileName != null && !fileName.isEmpty()) {
                options.put("out", fileName);
            }
            if (dir != null && !dir.isEmpty()) {
                options.put("dir", dir);
            }
            options.put("split", String.valueOf(getSplit()));
            options.put("max-connection-per-server", String.valueOf(getMaxConnection()));
            options.put("max-concurrent-downloads", String.valueOf(getMaxConcurrent()));
            params.put(options);

            return callRpc("aria2.addUri", params);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String callRpc(String method, JSONArray params) throws IOException {
        String rpcJson = buildJsonRpc(method, params);
        if (rpcJson == null) return null;

        String secret = getSecret();
        Request.Builder builder = new Request.Builder()
                .url(getRpcUrl())
                .post(RequestBody.create(rpcJson, JSON));
        if (secret != null && !secret.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + secret);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.body() != null) {
                return response.body().string();
            }
        }
        return null;
    }

    private static String buildJsonRpc(String method, JSONArray params) {
        try {
            JSONObject rpc = new JSONObject();
            rpc.put("jsonrpc", "2.0");
            rpc.put("id", "bili_" + System.currentTimeMillis());
            rpc.put("method", method);
            rpc.put("params", params);
            return rpc.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean isAria2Running() {
        if (isBuiltin()) {
            return SpeedDownloadService.getInstance() != null
                    && SpeedDownloadService.getInstance().getActiveCount() > 0;
        }
        try {
            String result = callRpc("aria2.getVersion", new JSONArray());
            return result != null && result.contains("result");
        } catch (IOException e) {
            return false;
        }
    }

    public static String getDownloadStatus() {
        if (isBuiltin()) {
            SpeedDownloadService service = SpeedDownloadService.getInstance();
            if (service == null) return "内置引擎未启动";

            StringBuilder sb = new StringBuilder();
            sb.append("引擎模式: 内置引擎\n");
            sb.append("活动任务: ").append(service.getActiveCount()).append("\n");
            sb.append("等待任务: ").append(service.getWaitingCount()).append("\n");
            sb.append("总任务数: ").append(service.getTotalCount());
            return sb.toString();
        }

        try {
            JSONArray params = new JSONArray();
            String result = callRpc("aria2.getGlobalStat", params);
            if (result != null) {
                JSONObject json = new JSONObject(result);
                if (json.has("result")) {
                    JSONObject stat = json.getJSONObject("result");
                    StringBuilder sb = new StringBuilder();
                    sb.append("引擎模式: 外部Aria2\n");
                    sb.append("下载速度: ").append(formatSpeed(stat.optString("downloadSpeed", "0")));
                    sb.append("\n活动任务: ").append(stat.optString("numActive", "0"));
                    sb.append("\n等待任务: ").append(stat.optString("numWaiting", "0"));
                    sb.append("\n已停止: ").append(stat.optString("numStopped", "0"));
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "无法获取下载状态";
    }

    private static String formatSpeed(String speedStr) {
        try {
            long speed = Long.parseLong(speedStr);
            if (speed >= 1048576) return String.format("%.1f MB/s", speed / 1048576.0);
            if (speed >= 1024) return String.format("%.1f KB/s", speed / 1024.0);
            return speed + " B/s";
        } catch (NumberFormatException e) {
            return speedStr + " B/s";
        }
    }

    public static boolean downloadVideo(String url, String title, String quality) {
        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_") + "_" + quality + ".mp4";
        File dir = new File(getDownloadDir(), FileUtil.stringToFile(title));
        String result = addDownloadUri(url, safeName, dir.getAbsolutePath());
        return result != null && result.contains("result");
    }

    public static boolean downloadAudio(String url, String title) {
        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".m4a";
        File dir = new File(getDownloadDir(), FileUtil.stringToFile(title));
        String result = addDownloadUri(url, safeName, dir.getAbsolutePath());
        return result != null && result.contains("result");
    }

    public static boolean downloadImage(String url, String filename) {
        String safeName = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!safeName.contains(".")) safeName += ".jpg";
        String result = addDownloadUri(url, safeName,
                FileUtil.getPicturePath().getAbsolutePath());
        return result != null && result.contains("result");
    }

    private static String secretToken() {
        return "bili_" + System.currentTimeMillis();
    }

    public static void applyGlobalOptions() {
        try {
            JSONObject options = new JSONObject();
            options.put("max-concurrent-downloads", String.valueOf(getMaxConcurrent()));
            options.put("max-connection-per-server", String.valueOf(getMaxConnection()));
            options.put("split", String.valueOf(getSplit()));

            JSONArray params = new JSONArray();
            params.put(options);
            callRpc("aria2.changeGlobalOption", params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}