package com.RobinNotBad.BiliClient.util;


import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.TransitionOptions;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;

public class GlideUtil {
    // 26.10.04 批次 3（B1+B3）：图片请求的质量/宽度档位不再写死在这里，
    // 统一由 PerformanceManager 按设备档位给出（列表图 低端 320w/50q、其余 512w/60q；
    // 大图 低端 512w/60q、其余 1024w/80q）。
    // 原来的 QUALITY_LOW/QUALITY_HIGH/MAX_W_LOW/MAX_W_HIGH 四个常量已删除——它们只是"两处真相"。

    private static final DrawableCrossFadeFactory CROSS_FADE_FACTORY =
            new DrawableCrossFadeFactory.Builder(300).setCrossFadeEnabled(true).build();

    /**
     * 这条图片地址能不能拼 `@0e_75q_640w.webp` 这种缩放后缀。
     *
     * 26.11.06 起改成按主机白名单判定（[ImageRequestPolicy.supportsResizeSuffix]，纯函数、有单测）：
     * 以前是「凡是 http 图片都拼」，站外地址会被拼坏——关于页「赞赏」那张 gitee 收款码被拼成
     * `ma.jpg@0e_75q_640w.webp`，直接 404、图空白。gif 本来也不能拼，已经带过 `@` 的别重复拼。
     */
    private static boolean canAppendResizeSuffix(String url) {
        return !url.endsWith("gif")
                && !url.contains("@")
                && ImageRequestPolicy.supportsResizeSuffix(url);
    }

    public static String url(String url) {
        if (!canAppendResizeSuffix(url))
            return url;
        final int quality = PerformanceManager.getImageQuality();
        final int maxWidth = PerformanceManager.getImageMaxWidth();
        if (SharedPreferencesUtil.getBoolean("image_request_jpg", false)) {
            if (url.endsWith("jpeg") || url.endsWith("jpg")) return url;
            return url + "@0e_"
                    + quality + "q_"
                    //+ MAX_H_LOW + "h_"
                    + maxWidth + "w.jpeg";
        } else {
            if (url.endsWith("webp")) return url;
            return url + "@0e_"
                    + quality + "q_"
                    //+ MAX_H_LOW + "h_"
                    + maxWidth + "w.webp";
        }
    }

    /** 详情 / 卡片大图：宽度已按屏幕长边封顶（手表上不会再请求 1024w）。 */
    public static String url_hq(String url) {
        return hqUrl(url, PerformanceManager.getHqImageMaxWidth());
    }

    /** 可缩放查看的大图（图片查看器）：不按屏幕封顶，放大后仍清晰。 */
    public static String url_zoom(String url) {
        return hqUrl(url, PerformanceManager.getZoomImageMaxWidth());
    }

    private static String hqUrl(String url, int maxWidth) {
        if (!canAppendResizeSuffix(url))
            return url;
        final int quality = PerformanceManager.getHqImageQuality();
        if (SharedPreferencesUtil.getBoolean("image_request_jpg", false)) {
            if (url.endsWith("jpeg") || url.endsWith("jpg")) return url;
            return url + "@0e_"
                    + quality + "q_"
                    //+ MAX_H_HIGH + "h_"
                    + maxWidth + "w.jpeg";
        } else {
            if (url.endsWith("webp")) return url;
            return url + "@0e_"
                    + quality + "q_"
                    //+ MAX_H_HIGH + "h_"
                    + maxWidth + "w.webp";
        }
    }

    public static void request(ImageView view, String url, int placeholder) {
        if (!canLoad(view)) return;
        Glide.with(view).asDrawable().load(url(url))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .format(DecodeFormat.PREFER_RGB_565)
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(safePlaceholder(placeholder))
                .error(safePlaceholder(placeholder))
                .into(view);
    }

    public static void requestRound(ImageView view, String url, int placeholder) {
        if (!canLoad(view)) return;
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
        if (!canLoad(view)) return;
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
     * 现在还能不能对这个 View 发起一次图片加载。
     *
     * <p>Glide 4 的 {@code Glide.with(activity)} 内部会先
     * {@code assertNotDestroyed(activity)}，页面一旦销毁就抛
     * {@code IllegalArgumentException: You cannot start a load for a destroyed activity}；
     * 而 {@code Glide.with(view)} 会沿着 View 的 Context 往上找宿主 Activity，最后走到**同一个**
     * 判断。于是「网络回来后在回调里刷新图片」这种场景必崩——发请求时页面还活着，回来时已经
     * finish 了；弱网与低性能设备只是把这个时间窗拉长。
     *
     * <p>这里提前挡住：**只有确实解析到一个已销毁 / 正在 finish 的 Activity 才拒绝**。
     * 解析不到 Activity（View 挂在 Application 上下文上）时照常放行，与改动前行为一致。
     *
     * <p>{@code Activity#isDestroyed()} 是 API 17+，本工程 minSdk 19，不需要版本闸；
     * 另外 {@code BaseActivity} 重写了它（销毁**或**正在 finish 都返回 true），
     * 这里仍把 {@code isFinishing()} 一起判，是为了不依赖那个重写。
     */
    public static boolean canLoad(View view) {
        if (view == null) return false;
        Activity activity = findActivity(view.getContext());
        if (activity == null) return true;
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    /**
     * 列表 / 卡片里加载图片用的 RequestManager：绑定到 View 所在 Activity 的生命周期。
     *
     * <p>26.10.27 性能审查：约 30 处 adapter 写的是 {@code Glide.with(BiliTerminal.context)}
     * （Application 级），带来三个问题：离开页面后请求不取消，继续下载解码；页面退到后台也不暂停；
     * {@link com.RobinNotBad.BiliClient.util.view.ImageAutoLoadScrollListener} 对 Activity 级
     * RequestManager 的 pause 管不到它们，「翻动时不加载图片」开关等于没开。
     *
     * <p>不用 {@code Glide.with(view)}：它要在 Fragment 树里逐个找宿主，列表 bind 热路径上更慢，
     * 而这里的页面暂停 / 恢复都是 Activity 粒度。找不到 Activity、Activity 已销毁或不在主线程时
     * 回落 Application 级，与改动前行为一致——不会抛「destroyed activity」。
     */
    public static RequestManager with(View view) {
        Context appContext = BiliTerminal.context;
        if (view != null && Looper.myLooper() == Looper.getMainLooper()) {
            Activity activity = findActivity(view.getContext());
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                return Glide.with(activity);
            }
        }
        return Glide.with(appContext != null ? appContext : view.getContext().getApplicationContext());
    }

    /** 沿 ContextWrapper 链找宿主 Activity；不是 Activity 上下文时返回 null。 */
    private static Activity findActivity(Context context) {
        if (context instanceof Activity) return (Activity) context;
        if (context instanceof ContextWrapper) return findActivity(((ContextWrapper) context).getBaseContext());
        return null;
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
