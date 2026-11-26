package com.RobinNotBad.BiliClient.util;


import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.TransitionOptions;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
                .apply(thumbOptions(view))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .format(DecodeFormat.PREFER_RGB_565)
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(placeholderDrawable(view.getContext(), placeholder))
                .error(placeholderDrawable(view.getContext(), placeholder))
                .into(view);
    }

    public static void requestRound(ImageView view, String url, int placeholder) {
        if (!canLoad(view)) return;
        Glide.with(view).asDrawable().load(url(url))
                // 圆形头像走 circleCrop（先裁方再填满），降采样必须用 cover 语义，见 thumbOptionsCover
                .apply(thumbOptionsCover(view))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .format(DecodeFormat.PREFER_RGB_565)
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(placeholderDrawable(view.getContext(), placeholder))
                .error(placeholderDrawable(view.getContext(), placeholder))
                .apply(RequestOptions.circleCropTransform())
                .into(view);
    }

    public static void request(ImageView view, String url, int roundCorners, int placeholder) {
        if (!canLoad(view)) return;
        Glide.with(view).asDrawable().load(url(url))
                .apply(thumbOptions(view))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .format(DecodeFormat.PREFER_RGB_565)
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(placeholderDrawable(view.getContext(), placeholder))
                .error(placeholderDrawable(view.getContext(), placeholder))
                .apply(RequestOptions.bitmapTransform(new RoundedCorners(ToolsUtil.dp2px(roundCorners))))
                .into(view);
    }

    /**
     * 列表 / 缩略图请求的**尺寸约束**：按控件尺寸算出解码目标，并把降采样策略钉成 fits-inside。
     *
     * <p>26.11.15 两条线上崩溃都是「解码尺寸不受控」，而不是某一张图特别大：
     * <ul>
     *   <li>{@code OutOfMemoryError: Failed to allocate a 262156 byte allocation}——列表行布局里
     *   那张 256px 占位图每次建行都要重新按原分辨率解一次（正好 256×256×4+12 = 262156），
     *   当时进程只剩 209KB 可用；</li>
     *   <li>{@code Canvas: trying to draw too large(116761824 bytes) bitmap}——栈在 crossfade 的
     *   {@code TransitionDrawable.draw}，一张 5400×5400 的位图被直接拿去画。</li>
     * </ul>
     *
     * <p>Glide 里唯一能钉死「解码出多大位图」的开关是 {@code override(宽, 高)}：不给它，
     * 解码尺寸就取决于 target 解析结果，控件尚未测量、布局又是 {@code wrap_content} 时会退化成
     * 原图。尺寸换算交给 {@link ThumbSizePolicy}（纯函数、有单测），定不下来时**不加 override**，
     * 退回 Glide 的默认行为，不赌一个尺寸。
     *
     * <p>降采样选 {@code CENTER_INSIDE}：解出的位图整张落在 override 框内（fits-inside），
     * 与这些控件默认的 {@code FIT_CENTER} 以及 {@code RoundedCorners} 变换语义一致——等比缩小
     * 之后由 ImageView 铺开，裁剪比例不变，也不会像 {@code CENTER_OUTSIDE} 那样比需要的多解一圈像素。
     */
    public static RequestOptions thumbOptions(View view) {
        return thumbOptions(view, DownsampleStrategy.CENTER_INSIDE);
    }

    /**
     * 同 {@link #thumbOptions(View)}，但降采样用 {@code CENTER_OUTSIDE}（cover 语义）。
     *
     * <p>只给 {@code circleCrop} 这类「先裁成方形再填满控件」的用法：{@code CENTER_INSIDE} 会先按
     * 短边把位图缩小，再被 circleCrop 放大回控件尺寸，头像直接糊掉。解出的位图仍被 override 框
     * （即控件尺寸）夹住，不会解出比控件更大的图。
     */
    public static RequestOptions thumbOptionsCover(View view) {
        return thumbOptions(view, DownsampleStrategy.CENTER_OUTSIDE);
    }

    private static RequestOptions thumbOptions(View view, DownsampleStrategy strategy) {
        RequestOptions options = new RequestOptions().downsample(strategy);
        int[] size = targetPixels(view);
        if (size[0] > 0 && size[1] > 0) {
            options = options.override(size[0], size[1]);
        }
        return options;
    }

    /**
     * 把控件尺寸取出来交给纯函数 {@link ThumbSizePolicy} 换算出解码目标像素。
     *
     * <p>返回 {@code [0, 0]} 表示尺寸定不下来（未测量、布局又是 wrap_content，或屏幕尺寸拿不到），
     * 此时**不要 override**。
     */
    private static int[] targetPixels(View view) {
        if (view == null) return new int[]{0, 0};
        DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        ViewGroup.LayoutParams params = view.getLayoutParams();
        // 测量值包含自身 padding，图片实际可用区域要扣掉
        int measuredWidth = view.getMeasuredWidth() - view.getPaddingLeft() - view.getPaddingRight();
        int measuredHeight = view.getMeasuredHeight() - view.getPaddingTop() - view.getPaddingBottom();
        return ThumbSizePolicy.targetPixels(
                measuredWidth,
                measuredHeight,
                params != null ? params.width : 0,
                params != null ? params.height : 0,
                metrics.widthPixels,
                metrics.heightPixels);
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

    /**
     * 占位图 / 兜底图的共享缓存，键是资源 id。
     *
     * <p>为什么不能把资源 id 直接交给 Glide：{@code placeholder(int)} / {@code error(int)}
     * 并不是「记下 id 等要用时再取」——{@code SingleRequest} 构建时会调
     * {@code Context.getDrawable(resId)} 现解一份 Drawable。Resources 自带的 drawable cache
     * 本来能挡住重复解码，但它在低内存下会被 {@code onTrimMemory} 整个清空，**恰恰在内存最紧时失效**；
     * cache 一旦空了，每次 bind 都要重新 decode 一遍。
     *
     * <p>26.11.09 控制台崩溃 #401 就是这条路径：sdk 25 的小内存机上只剩 182 KB 空闲，
     * {@code onBindViewHolder → Context.getDrawable → BitmapFactory.decodeStream} 要分配
     * 262156 字节（正好 256×256×4 再加 Bitmap 头），直接 OutOfMemoryError。
     *
     * <p>这里缓存 {@link Drawable.ConstantState}：位图只 decode 一次，之后每次
     * {@code newDrawable()} 派生实例共享同一张位图，不再碰 BitmapFactory。派生后调
     * {@code mutate()}，免得多个 ImageView 共用同一份状态时 Callback 打架
     * （mutate 只换状态对象，不复制位图）。
     */
    private static final Map<Integer, Drawable.ConstantState> PLACEHOLDER_STATES = new ConcurrentHashMap<>();

    /**
     * 取一张可安全用于 {@code placeholder()} / {@code error()} 的占位图。
     *
     * <p>跟直接传资源 id 的区别只在内存：同一资源只解码一次，之后都是共享位图的派生实例。
     * 资源本身没有 ConstantState（shape、纯色之类）时直接返回解码结果，它本来就不占位图。
     *
     * <p>取图必须走 {@link StringUtil#getDrawable}（→ {@code AppCompatResources}）：本项目开了
     * {@code vectorDrawables.useSupportLibrary}，API&lt;21 没有 PNG 兜底，而 {@code ContextCompat} /
     * {@code ResourcesCompat} 在那里仍是原生加载路径，遇到 {@code <vector>} 会抛
     * {@code Resources$NotFoundException}（见 {@code docs/agents/dev-conventions.md} 的
     * 「图标 / 矢量图」一节）。
     */
    public static Drawable placeholderDrawable(Context context, int resId) {
        int id = safePlaceholder(resId);
        Drawable.ConstantState state = PLACEHOLDER_STATES.get(id);
        if (state != null) {
            Drawable derived = state.newDrawable(context.getResources());
            return derived != null ? derived.mutate() : null;
        }
        Drawable decoded = StringUtil.getDrawable(context, id);
        state = decoded.getConstantState();
        if (state == null) return decoded;
        PLACEHOLDER_STATES.put(id, state);
        // 首个实例也要脱离共享状态，否则它会和缓存里的 state 抢同一个 Callback
        Drawable first = decoded.mutate();
        return first != null ? first : decoded;
    }

    /**
     * 一次给请求设好占位图与兜底图，替代逐处写两行
     * {@code .placeholder(...)} / {@code .error(...)}。
     *
     * <p>存在的理由不只是省字：这两行**必须成对**。只设 placeholder 不设 error 时，加载失败会
     * 把上一个复用项的画面留在原处（RecyclerView 复用典型脏数据），而这在翻列表时很难被看出来。
     *
     * <p>每次调用都新建 {@code RequestOptions}：占位图是要按控件派生的新实例（见
     * [placeholderDrawable]），不能做成静态常量共享。
     */
    public static RequestOptions withPlaceholder(Context context, int resId) {
        return new RequestOptions()
                .placeholder(placeholderDrawable(context, resId))
                .error(placeholderDrawable(context, resId));
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
