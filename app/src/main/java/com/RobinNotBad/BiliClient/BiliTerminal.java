package com.RobinNotBad.BiliClient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.multidex.MultiDex;

import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.activity.settings.UpdateActivity;
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity;
import com.RobinNotBad.BiliClient.api.DynamicApi;
import com.RobinNotBad.BiliClient.api.MessageApi;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.PerformanceManager;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;
import com.RobinNotBad.BiliClient.util.UpdateManager;

import org.json.JSONException;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class BiliTerminal extends Application {

    @SuppressLint("StaticFieldLeak")
    public static Context context;

    public static boolean DPI_FORCE_CHANGE = false;

    private static WeakReference<InstanceActivity> instance = new WeakReference<>(null);

    private static volatile boolean forceUpdateBlocking = false;

    private static volatile int forceUpdateVersionCode = 0;
    private static volatile String forceUpdateVersionName = null;
    private static volatile String forceUpdateDescription = null;
    private static volatile String forceUpdateDownloadUrl = null;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (context == null) {
            SharedPreferencesUtil.sharedPreferences = getSharedPreferences("default", MODE_PRIVATE);
            context = getFitDisplayContext(this);

            // 初始化性能管理器 - 设备检测与自适应优化
            PerformanceManager.INSTANCE.init(this);

            forceUpdateBlocking = SharedPreferencesUtil.getBoolean("force_update_required", false);
            if (forceUpdateBlocking) {
                forceUpdateVersionCode = SharedPreferencesUtil.getInt("force_update_version_code", 0);
                forceUpdateVersionName = SharedPreferencesUtil.getString("force_update_version_name", null);
                forceUpdateDescription = SharedPreferencesUtil.getString("force_update_description", null);
                forceUpdateDownloadUrl = SharedPreferencesUtil.getString("force_update_download_url", null);
                // 已更新到强制要求的版本（安装完成重启后），解除拦截
                try {
                    if (forceUpdateVersionCode > 0 && getVersion() >= forceUpdateVersionCode) {
                        clearForceUpdate();
                    }
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }

            registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
                @Override
                public void onActivityPreCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                    if (forceUpdateBlocking && !(activity instanceof UpdateActivity)) {
                        Intent intent = new Intent(activity, UpdateActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        intent.putExtra("has_config", true);
                        intent.putExtra("version_code", forceUpdateVersionCode);
                        intent.putExtra("version_name", forceUpdateVersionName);
                        intent.putExtra("description", forceUpdateDescription);
                        intent.putExtra("download_url", forceUpdateDownloadUrl);
                        intent.putExtra("force_update", true);
                        activity.startActivity(intent);
                        activity.finish();
                    }
                }

                @Override
                public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) { }
                @Override
                public void onActivityStarted(@NonNull Activity activity) { }
                @Override
                public void onActivityResumed(@NonNull Activity activity) { }
                @Override
                public void onActivityPaused(@NonNull Activity activity) { }
                @Override
                public void onActivityStopped(@NonNull Activity activity) { }
                @Override
                public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
                @Override
                public void onActivityDestroyed(@NonNull Activity activity) { }
            });
            ErrorCatch errorCatch = ErrorCatch.getInstance();
            errorCatch.init(context);

            boolean debugBuild = isDebugBuild();
            Logu.LOGV_ENABLED = SharedPreferencesUtil.getBoolean("dev_logv", debugBuild);
            Logu.LOGD_ENABLED = SharedPreferencesUtil.getBoolean("dev_logd", debugBuild);
            Logu.LOGI_ENABLED = SharedPreferencesUtil.getBoolean("dev_logi", debugBuild);

            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.DYNAMIC_UPDATE_CHECK_ENABLE, true) && SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0) {
                CenterThreadPool.run(() -> {
                    try {
                        long updateBaseline = SharedPreferencesUtil.getLong("dynamic_update_baseline", 0);
                        int updateNum = DynamicApi.checkDynamicUpdate("all", updateBaseline);
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, updateNum);
                    } catch (IOException | JSONException e) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0);
                    }
                });
            }

            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE, true) && SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0) {
                CenterThreadPool.run(() -> {
                    try {
                        int messageUnread = MessageApi.checkMessageUnread();
                        int privateMsgUnread = MessageApi.checkPrivateMsgUnread();
                        int totalUnread = messageUnread + privateMsgUnread;
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, totalUnread);
                    } catch (IOException | JSONException e) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0);
                    }
                });
            }

            checkAppUpdate();
        }
    }

    private void checkAppUpdate() {
        if (!SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.AUTO_UPDATE_CHECK_ENABLE, true)) {
            return;
        }
        // 强制更新生效中：不重复检查更新，避免网络返回异常或配置变更时误解除拦截
        if (forceUpdateBlocking) {
            return;
        }
        CenterThreadPool.run(() -> {
            try {
                UpdateManager.INSTANCE.checkUpdate(
                    config -> {
                        if (UpdateManager.INSTANCE.hasUpdate(config)) {
                            if (config.isForceUpdate()) {
                                forceUpdateVersionCode = config.getVersionCode();
                                forceUpdateVersionName = config.getVersionName();
                                forceUpdateDescription = config.getDescription();
                                forceUpdateDownloadUrl = config.getDownloadUrl();
                                forceUpdateBlocking = true;
                                SharedPreferencesUtil.putBoolean("force_update_required", true);
                                SharedPreferencesUtil.putInt("force_update_version_code", config.getVersionCode());
                                SharedPreferencesUtil.putString("force_update_version_name", config.getVersionName());
                                SharedPreferencesUtil.putString("force_update_description", config.getDescription());
                                SharedPreferencesUtil.putString("force_update_download_url", config.getDownloadUrl());
                                CenterThreadPool.runOnUiThread(() -> {
                                    Intent intent = new Intent(context, UpdateActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    intent.putExtra("has_config", true);
                                    intent.putExtra("version_code", config.getVersionCode());
                                    intent.putExtra("version_name", config.getVersionName());
                                    intent.putExtra("description", config.getDescription());
                                    intent.putExtra("download_url", config.getDownloadUrl());
                                    intent.putExtra("force_update", true);
                                    context.startActivity(intent);
                                });
                            } else {
                                clearForceUpdate();
                                int lastNewVersion = SharedPreferencesUtil.getInt("update_last_new_version", 0);
                                if (config.getVersionCode() != lastNewVersion) {
                                    CenterThreadPool.runOnUiThread(() ->
                                        MsgUtil.showMsg("发现新版本 " + config.getVersionName())
                                    );
                                }
                            }
                        } else {
                            clearForceUpdate();
                        }
                        return kotlin.Unit.INSTANCE;
                    },
                    error -> kotlin.Unit.INSTANCE
                );
            } catch (Exception e) {
            }
        });
    }

    public static void clearForceUpdate() {
        forceUpdateBlocking = false;
        forceUpdateVersionCode = 0;
        forceUpdateVersionName = null;
        forceUpdateDescription = null;
        forceUpdateDownloadUrl = null;
        SharedPreferencesUtil.removeValue("force_update_required");
        SharedPreferencesUtil.removeValue("force_update_version_code");
        SharedPreferencesUtil.removeValue("force_update_version_name");
        SharedPreferencesUtil.removeValue("force_update_description");
        SharedPreferencesUtil.removeValue("force_update_download_url");
    }

    public static void setInstance(InstanceActivity instanceActivity) {
        instance = new WeakReference<>(instanceActivity);
    }

    @Nullable
    public static InstanceActivity getInstanceActivityOnTop() {
        return instance.get();
    }

    /**
     * 重写attachBaseContext方法，用于调整应用内dpi
     * 尝试下这种风格代码是否会导致低版本设备异常
     *
     * @param old The origin context.
     */
    public static Context getFitDisplayContext(Context old) {
        float dpiTimes = SharedPreferencesUtil.getFloat("dpi", 1.0F);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return old;
        if (!DPI_FORCE_CHANGE && dpiTimes == 1.0F) return old;
        try {
            DisplayMetrics displayMetrics = old.getResources().getDisplayMetrics();
            Configuration configuration = old.getResources().getConfiguration();
            configuration.densityDpi = (int) (displayMetrics.densityDpi * dpiTimes);
            return old.createConfigurationContext(configuration);
        } catch (Exception e) {
            //MsgUtil.err(e,old);
            return old;
        }
    }

    public static int getVersion() throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
    }

    public static boolean isDebugBuild() {
        return "debug".equals(BuildConfig.BUILD_TYPE);
    }

    public static void jumpToVideo(Context context, long aid) {
        TerminalContext.getInstance().enterVideoDetailPage(context, aid);
    }

    public static void jumpToVideo(Context context, String bvid) {
        TerminalContext.getInstance().enterVideoDetailPage(context, bvid);
    }

    public static void jumpToArticle(Context context, long cvid) {
        TerminalContext.getInstance().enterArticleDetailPage(context, cvid);
    }

    public static void jumpToUser(Context context, long mid) {
        Intent intent = new Intent();
        intent.setClass(context, UserInfoActivity.class);
        intent.putExtra("mid", mid);
        context.startActivity(intent);
    }

}
