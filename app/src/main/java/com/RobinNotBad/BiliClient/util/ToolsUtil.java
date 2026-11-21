package com.RobinNotBad.BiliClient.util;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.BuildConfig;
import com.RobinNotBad.BiliClient.R;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

//2023-07-25

public class ToolsUtil {

    /**
     * 取「当前真正生效」的 DisplayMetrics：优先当前页面，其次全局 context，都没有返回 null。
     *
     * <p>为什么不能只用 {@link BiliTerminal#context}：那是 Application context，它的密度只在进程
     * 启动时算过一次（BiliTerminal.kt 的 Application.onCreate），而页面密度是每个 Activity 在
     * attachBaseContext 时算的。两边一旦不同源，同一屏里「代码算出来的尺寸」就和「布局里的
     * dp/sp」差一截 —— 反馈 #225（OPPO Watch 3，应用内 DPI 220 / 系统 189）报的「字体大小
     * 不统一」就是这个差值（220/189 ≈ 1.16，代码算的那部分会小 14%）。
     *
     * <p>刻意**不做静态缓存**：密度会随设置变更、页面切换而变，缓存一次就固化成老值，
     * 而「缓存了旧密度」本身正是这类 bug 的经典来源（同一个进程里两套答案）。
     * 每次多一次 WeakReference.get() + getResources()，对手表来说是零成本。
     */
    private static DisplayMetrics fitMetrics() {
        Activity activity = BiliTerminal.getCurrentActivity();
        if (activity != null) {
            try {
                return activity.getResources().getDisplayMetrics();
            } catch (Exception ignored) {
                // 页面正在销毁 / Resources 不可用时退回全局 context，不让取尺寸把异常抛进业务逻辑
            }
        }
        if (BiliTerminal.context != null) {
            return BiliTerminal.context.getResources().getDisplayMetrics();
        }
        return null;
    }

    public static int dp2px(float dpValue) {
        DisplayMetrics metrics = fitMetrics();
        if (metrics == null) {
            return (int) (dpValue * 2 + 0.5f); // 默认值，假设密度为 2
        }
        return (int) (dpValue * metrics.density + 0.5f);
    }

    public static int sp2px(float spValue) {
        DisplayMetrics metrics = fitMetrics();
        if (metrics == null) {
            return (int) (spValue * 2 + 0.5f); // 默认值，假设密度为 2
        }
        return (int) (spValue * metrics.scaledDensity + 0.5f);
    }

    public static String md5(String plainText) {
        byte[] secretBytes;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes(StandardCharsets.UTF_8));
            secretBytes = md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("没有md5这个算法！");
        }
        StringBuilder md5code = new StringBuilder(new BigInteger(1, secretBytes).toString(16));
        int pad = 32 - md5code.length();
        if (pad > 0) {
            md5code.insert(0, "00000000000000000000000000000000", 0, pad);
        }
        return md5code.toString();
    }


    public static String getUpdateLog(Context context) {
        StringBuilder str = new StringBuilder();
        String[] logItems = context.getResources().getStringArray(R.array.update_log_items);
        // 条目自带分类标题与编号，直接按行拼接
        for (String item : logItems)
            str.append("\n").append(item);
        return str.toString();
    }

    public static boolean isDebugBuild() {
        return BuildConfig.BETA;
    }

    /**
     * 取颜色的 RGB888 值（0xRRGGBB 十进制）。
     * 旧实现把三个通道的十进制数字符串拼接再 parseInt（白色得到 255255255 而非 16777215），
     * 导致每一条发送弹幕的颜色都是错的。
     */
    public static int getRgb888(int color) {
        return color & 0xFFFFFF;
    }

}
