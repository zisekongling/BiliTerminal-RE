package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.util.Log;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.BuildConfig;
import com.RobinNotBad.BiliClient.R;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

//2023-07-25

public class ToolsUtil {

    public static int dp2px(float dpValue) {
        if (BiliTerminal.context == null) {
            return (int) (dpValue * 2 + 0.5f); // 默认值，假设密度为 2
        }
        final float scale = BiliTerminal.context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public static int sp2px(float spValue) {
        if (BiliTerminal.context == null) {
            return (int) (spValue * 2 + 0.5f); // 默认值，假设密度为 2
        }
        final float fontScale = BiliTerminal.context.getResources()
                .getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

    public static String md5(String plainText) {
        byte[] secretBytes;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes());
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
