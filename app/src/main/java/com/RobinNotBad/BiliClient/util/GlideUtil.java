package com.RobinNotBad.BiliClient.util;


import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.RobinNotBad.BiliClient.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.TransitionOptions;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;

public class GlideUtil {
    public static final int QUALITY_HIGH = 80;

    /**
     * 列表/卡片图的压缩质量。
     *
     * 原值为 25，配合 512w 的宽度上限，在 1080p 屏幕上封面被放大后明显发虚、渐变出现色带。
     * 这里提到 60，体积仍然可控（webp + 512w），观感回归正常。
     */
    public static final int QUALITY_LOW = 60;
    public static final int MAX_W_HIGH = 1024;
    public static final int MAX_W_LOW = 512;

    private static final DrawableCrossFadeFactory CROSS_FADE_FACTORY =
            new DrawableCrossFadeFactory.Builder(300).setCrossFadeEnabled(true).build();

    public static String url(String url) {
        if (!url.startsWith("http") || url.endsWith("gif") || url.contains("@") || url.contains("afdian"))
            return url;
        if (SharedPreferencesUtil.getBoolean("image_request_jpg", false)) {
            if (url.endsWith("jpeg") || url.endsWith("jpg")) return url;
            return url + "@0e_"
                    + QUALITY_LOW + "q_"
                    //+ MAX_H_LOW + "h_"
                    + MAX_W_LOW + "w.jpeg";
        } else {
            if (url.endsWith("webp")) return url;
            return url + "@0e_"
                    + QUALITY_LOW + "q_"
                    //+ MAX_H_LOW + "h_"
                    + MAX_W_LOW + "w.webp";
        }
    }

    public static String url_hq(String url) {
        if (!url.startsWith("http") || url.endsWith("gif") || url.contains("@") || url.contains("afdiancdn.com"))
            return url;
        if (SharedPreferencesUtil.getBoolean("image_request_jpg", false)) {
            if (url.endsWith("jpeg") || url.endsWith("jpg")) return url;
            return url + "@0e_"
                    + QUALITY_HIGH + "q_"
                    //+ MAX_H_HIGH + "h_"
                    + MAX_W_HIGH + "w.jpeg";
        } else {
            if (url.endsWith("webp")) return url;
            return url + "@0e_"
                    + QUALITY_HIGH + "q_"
                    //+ MAX_H_HIGH + "h_"
                    + MAX_W_HIGH + "w.webp";
        }
    }

    public static void request(ImageView view, String url, int placeholder) {
        Glide.with(view).asDrawable().load(url(url))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .format(DecodeFormat.PREFER_RGB_565)
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(safePlaceholder(placeholder))
                .error(safePlaceholder(placeholder))
                .into(view);
    }

    public static void requestRound(ImageView view, String url, int placeholder) {
        Glide.with(view).asDrawable().load(url(url))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .format(DecodeFormat.PREFER_RGB_565)
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(safePlaceholder(placeholder))
                .error(safePlaceholder(placeholder))
                .apply(RequestOptions.circleCropTransform())
                .into(view);
    }

    public static void request(ImageView view, String url, int roundCorners, int placeholder) {
        Glide.with(view).asDrawable().load(url(url))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .format(DecodeFormat.PREFER_RGB_565)
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(safePlaceholder(placeholder))
                .error(safePlaceholder(placeholder))
                .apply(RequestOptions.bitmapTransform(new RoundedCorners(ToolsUtil.dp2px(roundCorners))))
                .into(view);
    }

    /**
     * 加载失败兜底图。
     *
     * Glide 的 {@code error(int)} 传 0 等于没设，而调用点里确实存在传 0 的情况
     * （如 {@code HotSearchAdapter}）；不兜底时 RecyclerView 复用会显示上一项的封面。
     */
    public static int safePlaceholder(int placeholder) {
        return placeholder != 0 ? placeholder : R.mipmap.placeholder;
    }

    public static TransitionOptions<?, ? super Drawable> getTransitionOptions() {
        // 不缓存开关值：原实现用 static 字段缓存，用户在设置里改「加载渐入渐出动画」后必须重启才生效
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.LOAD_TRANSITION, true)) {
            return DrawableTransitionOptions.with(CROSS_FADE_FACTORY);
        } else {
            return new DrawableTransitionOptions();
        }
    }
}
