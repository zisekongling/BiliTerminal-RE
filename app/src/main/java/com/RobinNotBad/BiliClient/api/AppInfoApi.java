package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.UiText;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.BuildConfig;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.TimeUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Objects;

public class AppInfoApi {

    /**
     * 启动通知：免责声明（仅首次）与「夜深了」提醒。
     *
     * **必须由 UI 线程在首屏 Activity 启动之后调用。** 这两个通知各自是一个
     * {@code DialogActivity}，而它们原来是在 {@link #check(Context)} 里由后台线程弹的——
     * 那条线程与「启动首屏」的 `runOnUiThread` 互相竞争，谁后到谁在上面；
     * 结果是弹窗常常先弹、随后被首屏盖住（现象：弹窗被主界面压在后面）。
     *
     * 两者也不同时弹：先弹免责声明，用户关掉之后再弹夜深了。
     * 否则两层对话框叠在一起，要连关两次。
     */
    public static void showStartupNotices(Context context) {
        boolean nightNow = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NIGHT_REMINDER_ENABLE, true)
                && isNightNow();

        // 讲真这免责声明没啥卵用，写这个也就是半开玩笑的，难道免责声明能挡住律师函吗
        if (!SharedPreferencesUtil.getBoolean("disclaimer_shown", false)) {
            // 正文很长，给 5 秒再允许关闭
            MsgUtil.showDialog("免责声明", "使用前请先阅读：\n" + context.getString(R.string.about_to_uncle), 5);
            SharedPreferencesUtil.putBoolean("disclaimer_shown", true);
            if (nightNow) {
                MsgUtil.runAfterDialogClosed(() ->
                        MsgUtil.showDialog(UiText.of("温馨提醒"), UiText.of("夜深了，要注意休息呐~"), 3));
            }
        } else if (nightNow) {
            MsgUtil.showDialog(UiText.of("温馨提醒"), UiText.of("夜深了，要注意休息呐~"), 3);
        }
    }

    /** 23:00 ～ 次日 03:59 视为「夜深」。 */
    private static boolean isNightNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 23 || hour <= 3;
    }

    public static void check(Context context) {
        try {
            int version = BiliTerminal.getVersion();
            int curr = ConfInfoApi.getDateCurr();

            int last_ver = SharedPreferencesUtil.getInt("app_version_last", 0);
            if (last_ver < version) {
                String update_apk = SharedPreferencesUtil.getString("terminal_update_pkg", "");
                if (!TextUtils.isEmpty(update_apk)) {
                    File file = new File(update_apk);
                    if (file.exists()) {
                        if (file.delete()) {
                            SharedPreferencesUtil.putString("terminal_update_pkg", "");
                            MsgUtil.showMsg(UiText.of("更新包已删除"));
                        } else MsgUtil.showMsg(UiText.of("更新包删除失败"));
                    } else SharedPreferencesUtil.putString("terminal_update_pkg", "");
                }

                // 这里原来会再弹一个「更新公告」全屏页（内容就是 update_log_current 全文）。
                // 现在版本升级后的当版日志由 SplashActivity 打开专门的更新日志页展示（按版本分选项卡），
                // 两处会同时弹、内容完全重复，且全屏页会盖在日志页上面，所以这里只保留后面
                // 「删除已下载的旧更新包」与「debug 包警告」两件事，不再弹公告。
                if (ToolsUtil.isDebugBuild())
                    MsgUtil.showDialog("警告", context.getString(R.string.warning_debug));
                SharedPreferencesUtil.putInt("app_version_last", version);
            }

            if (SharedPreferencesUtil.getInt("app_version_check", 0) < curr) {    //限制一天一次
                Logu.e("检查更新");
                SharedPreferencesUtil.putInt("app_version_check", curr);
            }
        } catch (Exception e) {
            Logu.e(e.toString());
            MsgUtil.err(UiText.of("终端接口出现问题（不影响软件内容）"), e);
        }
    }

    public static final ArrayList<String> customHeaders = new ArrayList<>() {{
        add("User-Agent");
        add(NetWorkUtil.USER_AGENT_WEB);    //防止携带b站cookies导致可能存在的开发者盗号问题（
        add("App-Info");
        try {
            add(new JSONObject()
                    .put("versionName", BuildConfig.VERSION_NAME)
                    .put("versionCode", BuildConfig.VERSION_CODE)
                    .put("isBeta", BuildConfig.BETA)
                    // 三通道：报的是这个包的通道（stable/beta/exp），与 TelemetryApi 的 ping 字段一致
                    .put("channel", BuildConfig.UPDATE_CHANNEL)
                    .put("applicationId", BuildConfig.APPLICATION_ID)
                    .put("buildType", BuildConfig.BUILD_TYPE)
                    .put("debugEnabled", BuildConfig.DEBUG)
                    .toString());
        } catch (JSONException e) {
            MsgUtil.showMsg("版本信息json生成出错\n无影响，正常情况下你应该不会遇到");
        }
        add("Device-Info");
        try {
            add(new JSONObject()
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("release", Build.VERSION.RELEASE)
                    .put("product", Build.PRODUCT)
                    .put("brand", Build.BRAND)
                    .put("device", Build.DEVICE)
                    .put("type", Build.TYPE)
                    .put("id", Build.ID)
                    .toString());
        } catch (JSONException e) {
            MsgUtil.showMsg("设备信息json生成出错\n无影响，正常情况下你应该不会遇到");
        }
    }};

    public static ApiResult uploadStack(String stack, Context context) {
        //上传崩溃堆栈
        try {
            String url = "https://api.biliterminal.cn/terminal/upload/stack";

            JSONObject post_data = new JSONObject();
            post_data.put("stack", stack);
            post_data.put("client_version", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
            post_data.put("device_sdk", Build.VERSION.SDK_INT);
            post_data.put("device_product", Build.PRODUCT);
            post_data.put("device_brand", Build.BRAND);

            JSONObject res = new JSONObject(Objects.requireNonNull(NetWorkUtil.postJson(url, post_data.toString(), customHeaders).body()).string());
            String msg = (res.getInt("code") == 200) ? "" : res.getString("msg");
            int id = res.optInt("id", -1);
            return new ApiResult(id, msg);
        } catch (IOException e) {
            return new ApiResult(-1, context.getString(R.string.err_network));
        } catch (JSONException e) {
            return new ApiResult(-514, context.getString(R.string.err_crash_upload_json));
        } catch (PackageManager.NameNotFoundException e) {
            return new ApiResult(-1919, "");
        }
    }

    public static int getSponsors(ArrayList<UserInfo> list, int page) throws Exception {
        String url = "https://api.biliterminal.cn/terminal/afdian/get_sponsor?page=" + page;
        JSONObject result = NetWorkUtil.getJson(url, customHeaders);

        if (result.getInt("code") != 200) throw new Exception("获取失败");
        JSONArray data = result.getJSONArray("data");

        if (data.length() == 0) return 1;

        for (int i = 0; i < data.length(); i++) {
            JSONObject sponsor = data.getJSONObject(i);

            UserInfo user = new UserInfo();

            user.name = sponsor.getString("name");
            user.avatar = sponsor.getString("avatar");
            user.sign = "总金额：" + sponsor.getInt("sum_amount") + "r | 捐赠时间：" + TimeUtil.format(sponsor.getLong("last_time") * 1000, TimeUtil.PATTERN_DATE_TIME_12H);
            user.mid = -1;
            user.fans = 0;
            user.followed = true;
            user.level = 6;
            user.notice = "";
            user.official = 0;
            user.officialDesc = "";

            list.add(user);
        }
        return 0;
    }
}
