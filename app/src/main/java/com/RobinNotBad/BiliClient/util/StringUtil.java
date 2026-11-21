package com.RobinNotBad.BiliClient.util;

import static com.RobinNotBad.BiliClient.util.LinkUrlUtil.AV_PATTERN;
import static com.RobinNotBad.BiliClient.util.LinkUrlUtil.BV_PATTERN;
import static com.RobinNotBad.BiliClient.util.LinkUrlUtil.CV_PATTERN;
import static com.RobinNotBad.BiliClient.util.LinkUrlUtil.TYPE_AVID;
import static com.RobinNotBad.BiliClient.util.LinkUrlUtil.TYPE_BVID;
import static com.RobinNotBad.BiliClient.util.LinkUrlUtil.TYPE_CVID;
import static com.RobinNotBad.BiliClient.util.LinkUrlUtil.TYPE_USER;
import static com.RobinNotBad.BiliClient.util.LinkUrlUtil.TYPE_WEB_URL;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.CopyTextActivity;
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicTopicActivity;
import com.RobinNotBad.BiliClient.activity.search.SearchActivity;
import com.RobinNotBad.BiliClient.api.ReplyApi;
import com.RobinNotBad.BiliClient.model.At;
import com.RobinNotBad.BiliClient.model.UserInfo;

import org.jsoup.Jsoup;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme;

@SuppressLint("ClickableViewAccessibility")
public class StringUtil {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?|ftp|file)://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]");
    private static final Pattern ESCAPE_PATTERN = Pattern.compile("\\\\(.)");

    private static volatile float cachedTextHeight;

    /** 徽章与行高统一取的正文基准字号（sp）。 */
    private static final float TEXT_HEIGHT_SP = 12f;

    /**
     * 按密度把 {@link #TEXT_HEIGHT_SP} 折算成像素。
     *
     * <p>纯函数（不碰 android.graphics）：原实现直接 {@code new Paint()} 取 FontMetrics，
     * 纯 JVM 下 Paint 是桩、一构造就抛 "not mocked"，单测只能永远走 catch，等于没有断言。
     */
    public static float textHeightSizePx(float density) {
        return TEXT_HEIGHT_SP * density;
    }

    /**
     * 由 FontMetrics 的 ascent/descent 算行高（纯函数）。
     *
     * <p>descent 为正、ascent 为负，相减才是字形实际占高；拆出来是为了让「减反了」
     * 这种改动在 JVM 单测里能被钉住。
     */
    public static float lineHeightOf(float ascent, float descent) {
        return descent - ascent;
    }

    static final int[] levelBadges = {
            R.mipmap.level_0,
            R.mipmap.level_1,
            R.mipmap.level_2,
            R.mipmap.level_3,
            R.mipmap.level_4,
            R.mipmap.level_5,
            R.mipmap.level_6,
            R.mipmap.level_h
    };

    public static Pair<Integer, Integer> appendString(SpannableStringBuilder stringBuilder, String str) {
        int startIndex = stringBuilder.length();
        stringBuilder.append(str);
        int endIndex = stringBuilder.length();
        return new Pair<>(startIndex, endIndex);
    }

    /**
     * 单位转换
     *
     * @param num 原始数字
     * @return 转换后的字符串
     */
    public static String toWan(long num) {
        if (num >= 100000000)
            return String.format(Locale.CHINA, "%.1f", (float) num / 100000000) + "亿";
        else if (num >= 10000)
            return String.format(Locale.CHINA, "%.1f", (float) num / 10000) + "万";
        else
            return String.valueOf(num);
    }

    public static String toTime(int progress) {
        int cghour = progress / 3600;
        int cgminute = (progress % 3600) / 60;
        int cgsecond = progress % 60;
        String cghourStr;
        String cgminStr;
        String cgsecStr;

        if (cghour < 10) cghourStr = "0" + cghour;
        else cghourStr = String.valueOf(cghour);

        if (cgminute < 10) cgminStr = "0" + cgminute;
        else cgminStr = String.valueOf(cgminute);

        if (cgsecond < 10) cgsecStr = "0" + cgsecond;
        else cgsecStr = String.valueOf(cgsecond);

        if (cghour > 0) return cghourStr + ":" + cgminStr + ":" + cgsecStr;
        else return cgminStr + ":" + cgsecStr;
    }

    public static String htmlToString(String html) {
        if (html == null || html.length() < 4) return html;
        StringBuilder sb = new StringBuilder(html.length());
        int len = html.length();
        for (int i = 0; i < len; i++) {
            char c = html.charAt(i);
            if (c == '&') {
                int semi = html.indexOf(';', i + 1);
                if (semi != -1 && semi - i <= 7) {
                    String ent = html.substring(i + 1, semi);
                    char decoded = 0;
                    switch (ent) {
                        case "lt": decoded = '<'; break;
                        case "gt": decoded = '>'; break;
                        case "quot": decoded = '"'; break;
                        case "amp": decoded = '&'; break;
                        case "#39": decoded = '\''; break;
                        case "#34": decoded = '"'; break;
                        case "#38": decoded = '&'; break;
                        case "#60": decoded = '<'; break;
                        case "#62": decoded = '>'; break;
                        default: decoded = 0;
                    }
                    if (decoded != 0) {
                        sb.append(decoded);
                        i = semi;
                        continue;
                    }
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static String htmlReString(String html) {
        if (html == null || html.length() == 0) return html;
        return html.replace("<p>", "")
                .replace("</p>", "\n")
                .replace("<br>", "\n")
                .replace("<em class=\"keyword\">", "")
                .replace("</em>", "");
    }

    public static String removeHtml(String html) {
        if (html == null || html.length() == 0 || html.indexOf('<') < 0) return html;
        return Jsoup.parse(html).text();
    }

    public static String unEscape(String str) {
        return ESCAPE_PATTERN.matcher(str).replaceAll("$1");
    }

    /**
     * 「长按复制」这个功能当前开着没有。
     *
     * <p>原先只在 {@link #setCopy} 内部读一次，现在「操作面板」里要不要列出「复制」这一项
     * 也要问同一件事，所以单独暴露出来——两处各写一遍 {@code getBoolean("copy_enable", true)}
     * 的话，将来改键名或改默认值必然漏掉一处。
     */
    public static boolean isCopyEnabled() {
        return SharedPreferencesUtil.getBoolean(SettingsKeys.COPY_ENABLE, true);
    }

    /**
     * 打开复制界面（让用户自己选要复制哪一段）。
     *
     * <p>长按复制与「操作面板里的复制」共用这条路径；面板项点下去时弹窗已经 dismiss，
     * 这里再 startActivity 不会出现两个界面叠着。
     */
    public static void openCopyPage(Context context, String content) {
        Intent intent = new Intent(context, CopyTextActivity.class);
        intent.putExtra("content", content == null ? "" : content);
        context.startActivity(intent);
    }

    public static void setCopy(TextView textView, String customText) {
        if (isCopyEnabled()) {
            textView.setOnLongClickListener(view1 -> {
                //直接传getText()会导致文本变化后点击不了，所以在长按发生时才取
                openCopyPage(textView.getContext(),
                        customText == null ? textView.getText().toString() : customText);
                return true;
            });
        }
    }

    public static void setCopy(TextView textView) {
        setCopy(textView, null); //直接传getText()会导致文本变化后点击不了
    }

    public static void setCopy(TextView... textViews) {
        for (TextView textView : textViews) setCopy(textView);
    }

    public static void copyText(Context context, String str) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData mClipData = ClipData.newPlainText("Label", str);
        cm.setPrimaryClip(mClipData);
    }

    public static void setLink(SpannableStringBuilder spannableString) {
        if (!SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.LINK_ENABLE, true)) return;
        if (TextUtils.isEmpty(spannableString)) return;

        String text = spannableString.toString();

        Matcher urlMatcher = URL_PATTERN.matcher(text);
        while (urlMatcher.find()) {
            int start = urlMatcher.start();
            int end = urlMatcher.end();
            spannableString.setSpan(new LinkClickableSpan(text.substring(start, end), TYPE_WEB_URL),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Matcher matcher;

        matcher = BV_PATTERN.matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            spannableString.setSpan(new LinkClickableSpan(text.substring(start, end), TYPE_BVID),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        matcher = AV_PATTERN.matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            spannableString.setSpan(new LinkClickableSpan(text.substring(start, end), TYPE_AVID),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        matcher = CV_PATTERN.matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            spannableString.setSpan(new LinkClickableSpan(text.substring(start, end), TYPE_CVID),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static void setLink(TextView... textViews) {
        if (!SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.LINK_ENABLE, true)) return;
        for (TextView textView : textViews) {
            if (TextUtils.isEmpty(textView.getText())) continue;
            SpannableStringBuilder spannableString = new SpannableStringBuilder(textView.getText());
            setLink(spannableString);
            textView.setText(spannableString);
            textView.setOnTouchListener(ClickableSpanTouchListener.getInstance());
        }
    }

    public static void setSingleAt(SpannableStringBuilder spannableString, String atName, long atMid) {
        Pattern pattern = Pattern.compile("@" + Pattern.quote(atName));
        String text = spannableString.toString();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            spannableString.setSpan(new LinkClickableSpan(text.substring(start, end), TYPE_USER, String.valueOf(atMid)),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static void setSingleAt(SpannableStringBuilder spannableString, At at) {
        spannableString.setSpan(new LinkClickableSpan(spannableString.subSequence(at.start, at.end).toString(),
                        TYPE_USER, String.valueOf(at.id)),
                at.start, at.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * 给 [start, end) 区间内的话题文本（形如 {@code #话题名#}）上主色 + 可点击跳转。
     *
     * 动态正文里话题节点（{@code RICH_TEXT_NODE_TYPE_TOPIC}）与 Opus 段落都用这一个入口，
     * 避免"两处真相"——此前 DynamicApi 完全没有 TOPIC 分支（话题退化成普通黑字、点不动），
     * 而 OpusParagraph 有分支但只是当普通外链处理。
     *
     * 颜色走 [ColorScheme.PRIMARY]（主题主色），**不硬编码色值**，与 {@link #setTopSpan} 同一约定。
     * 注意：[LinkClickableSpan.updateDrawState] 会把颜色写死成青色并覆盖 ForegroundColorSpan，
     * 所以这里不用它，改用 [TopicClickableSpan]（有 topic_id，进站内话题页）或
     * [TopicSearchClickableSpan]（只有搜索链接，进站内搜索页），两者都继承主色、无下划线。
     *
     * @param stringBuilder 目标富文本
     * @param start         话题文本起始下标
     * @param end           话题文本结束下标（不含）
     * @param jumpUrl       话题跳转链接。26.10.19 实测真实数据里**主要是**
     *                      {@code //search.bilibili.com/all?keyword=xx}（没有 topic_id），
     *                      也可能是 {@code .../topic-detail?topic_id=1305890}。
     *                      有 topic_id 进站内话题页；只有搜索链接就进站内搜索页；
     *                      两者都没有才只上色（服务端连 jump_url 都没下发时）
     */
    public static void setSingleTopic(SpannableStringBuilder stringBuilder, int start, int end, String jumpUrl) {
        if (stringBuilder == null || start < 0 || end > stringBuilder.length() || start >= end) return;
        long topicId = LinkUrlUtil.parseTopicId(jumpUrl);
        if (topicId > 0) {
            stringBuilder.setSpan(new TopicClickableSpan(topicId), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return;
        }
        String searchKeyword = LinkUrlUtil.parseTopicSearchKeyword(jumpUrl);
        if (!searchKeyword.isEmpty()) {
            // 服务端给的就是站内搜索链接（见 LinkUrlUtil.parseTopicSearchKeyword 的实测说明），
            // 按它的语义进搜索页；否则真实动态里的话题会"染成主色却点不动"
            stringBuilder.setSpan(new TopicSearchClickableSpan(searchKeyword),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return;
        }
        // 连搜索链接都没有：只上色，宁可有色不可点，也不要把 "#话题#" 当网址去请求
        stringBuilder.setSpan(new ForegroundColorSpan(ColorScheme.INSTANCE.getPRIMARY()),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * 话题专用点击 span：有主色下划线、点击进站内话题动态页。
     *
     * 不复用 [LinkClickableSpan] 的原因见 {@link #setSingleTopic}：它的
     * {@code updateDrawState} 硬编码青色，且 {@code TYPE_WEB_URL} 分支取的是 span 的
     * **显示文本**（即 {@code #话题#}）当网址，传 jump_url 进去根本不会被用到。
     */
    public static class TopicClickableSpan extends ClickableSpan {
        private final long topicId;

        public TopicClickableSpan(long topicId) {
            this.topicId = topicId;
        }

        @Override
        public void onClick(@NonNull View widget) {
            Context context = widget.getContext();
            context.startActivity(new Intent(context, DynamicTopicActivity.class)
                    .putExtra("topic_id", topicId));
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
            // 主题主色，不硬编码（用户要求"解析应该是蓝色的"，而蓝色正是默认主题的主色）
            ds.setColor(ColorScheme.INSTANCE.getPRIMARY());
        }
    }

    /**
     * 话题通用点击 span：话题没有 topic_id、但服务端给了站内搜索链接时用。
     *
     * 实测话题的 jump_url 就是 {@code //search.bilibili.com/all?keyword=xx}（见
     * {@link LinkUrlUtil#parseTopicSearchKeyword}），所以这里按关键词进站内搜索页，
     * 而不是把 {@code #话题#} 拼成站外网址（那会搜出一堆无关结果）。
     * 颜色与 {@link TopicClickableSpan} 一致：无下划线 + 主题主色。
     */
    public static class TopicSearchClickableSpan extends ClickableSpan {
        private final String keyword;

        public TopicSearchClickableSpan(String keyword) {
            this.keyword = keyword;
        }

        @Override
        public void onClick(@NonNull View widget) {
            Context context = widget.getContext();
            context.startActivity(new Intent(context, SearchActivity.class)
                    .putExtra("keyword", keyword)
                    // 落地即有结果，见 SearchActivity 里对 auto_search 的处理
                    .putExtra("auto_search", true));
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
            ds.setColor(ColorScheme.INSTANCE.getPRIMARY());
        }
    }

    public static void setAtLink(List<At> ats, TextView... textViews) {
        if (ats == null || ats.isEmpty()) return;
        for (TextView textView : textViews) {
            if (TextUtils.isEmpty(textView.getText())) continue;
            String text = textView.getText().toString();
            SpannableString spannableString = new SpannableString(textView.getText());

            for (At at : ats) {
                spannableString.setSpan(new LinkClickableSpan(text.substring(at.start, at.end), TYPE_USER, String.valueOf(at.id)),
                        at.start, at.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            textView.setText(spannableString);
            textView.setOnTouchListener(ClickableSpanTouchListener.getInstance());
        }
    }

    public static void setTopSpan(SpannableStringBuilder spannableString) {
        spannableString.setSpan(new ForegroundColorSpan(ColorScheme.INSTANCE.getPRIMARY()), 0, ReplyApi.TOP_TIP.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    public static ImageSpan getLevelBadge(Context context, UserInfo userInfo) {
        int level = userInfo.level;

        if (level <= -1 || level >= 7) level = 0;
        if (userInfo.is_senior_member == 1) level = 7;

        Drawable drawable = getDrawable(context, levelBadges[level]);

        float lineHeight = getTextHeightWithSize(context);
        float lineWidth = lineHeight * 1.56f;
        if (userInfo.is_senior_member == 1) lineWidth = lineHeight * 1.96f;
        drawable.setBounds(0, 0, (int) lineWidth, (int) lineHeight);
        return new ImageSpan(drawable);
    }

    public static float getTextHeightWithSize(Context context) {
        float cached = cachedTextHeight;
        if (cached > 0) return cached;
        Paint paint = new Paint();
        if (context != null) {
            // 12sp 按 scaledDensity 折算，与 TypedValue.applyDimension(COMPLEX_UNIT_SP, …) 等价
            paint.setTextSize(textHeightSizePx(context.getResources().getDisplayMetrics().scaledDensity));
        } else {
            // context 可能为 null（单元测试或异常路径），回退到系统默认密度
            paint.setTextSize(textHeightSizePx(android.content.res.Resources.getSystem().getDisplayMetrics().density));
        }
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float height = lineHeightOf(fontMetrics.ascent, fontMetrics.descent);
        cachedTextHeight = height;
        return height;
    }

    public static Drawable getDrawable(Context context, @DrawableRes int res) {
        // 必须走 AppCompatResources：本项目开了 vectorDrawables.useSupportLibrary，
        // API<21 没有 PNG 兜底，ResourcesCompat 在那里拿到的仍是原生加载路径，遇到 <vector> 会抛
        // Resources$NotFoundException（invalid drawable tag vector）。等级徽章等就是矢量。
        Drawable drawable = AppCompatResources.getDrawable(context, res);
        if (drawable != null) return drawable;
        else return new BitmapDrawable();
    }

    public static class LinkClickableSpan extends ClickableSpan {
        private final String text;
        /**
         * 真实值
         */
        private final String val;
        private final int type;

        public LinkClickableSpan(String text, int type, String val) {
            this.text = text;
            this.type = type;
            this.val = val;
        }

        public LinkClickableSpan(String text, int type) {
            this(text, type, null);
        }

        @Override
        public void onClick(@NonNull View widget) {
            switch (type) {
                case TYPE_USER:
                    BiliTerminal.jumpToUser(widget.getContext(), Long.parseLong(val));
                    break;
                case TYPE_WEB_URL:
                    // 富文本 WEB 节点的显示文本可能被服务端缩短（text="av1"、jump_url 才是真地址），
                    // 所以 val 非空时以它为准；正文里正则匹配出来的裸链接仍传 null、用显示文本当地址
                    LinkUrlUtil.handleWebURL(widget.getContext(), (val != null && !val.isEmpty()) ? val : text);
                    break;
                case TYPE_BVID:
                    TerminalContext.getInstance().enterVideoDetailPage(widget.getContext(), text);
                    break;
                case TYPE_AVID:
                    TerminalContext.getInstance().enterVideoDetailPage(widget.getContext(), Long.parseLong(text.replace("av", "")));
                    break;
                case TYPE_CVID:
                    TerminalContext.getInstance().enterArticleDetailPage(widget.getContext(), Long.parseLong(text.replace("cv", "")));
                    break;
            }
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
            ds.setColor(ColorScheme.INSTANCE.getLINK()); // 原写死 #66ccff，现按主题取链接色
        }
    }

    /**
     * 这次「按下 → 抬手」是否已经达到系统长按阈值（纯函数，供 JVM 单测）。
     *
     * <p>判据必须与 {@link android.view.ViewConfiguration#getLongPressTimeout()} 对齐：
     * View 正是在按下后这么久触发 {@code OnLongClickListener}，所以「抬手时已经够久」
     * 就等于「长按回调已经跑过（或马上就要跑）」，此时不能再补一次链接点击。
     *
     * <ul>
     *   <li>没有记录到按下时间（{@code downEventTime < 0}）：不是长按——那是从别处滑进
     *       链接区域之类的情况，保持旧的「抬手即点链接」行为；</li>
     *   <li>TextView 不可长按（没挂 OnLongClickListener）：永远不算长按。此时长按回调
     *       根本不会被调用，按住再久抬手也应该点开链接（旧行为），否则链接就点不动了。</li>
     * </ul>
     *
     * @param longClickable       该 TextView 当前是否可长按（{@link View#isLongClickable()}）
     * @param downEventTime       ACTION_DOWN 的 {@link MotionEvent#getEventTime()}，未记录传 -1
     * @param upEventTime         ACTION_UP 的 {@link MotionEvent#getEventTime()}
     * @param longPressTimeoutMs  系统长按阈值，即 {@code ViewConfiguration.getLongPressTimeout()}
     */
    public static boolean isLongPressGesture(boolean longClickable, long downEventTime,
                                             long upEventTime, int longPressTimeoutMs) {
        if (downEventTime < 0) return false;
        if (!longClickable) return false;
        return upEventTime - downEventTime >= longPressTimeoutMs;
    }

    /**
     * 让 TextView 里的 {@link ClickableSpan} 可点，同时**不吞掉长按**。
     *
     * <h3>为什么不能消费 ACTION_DOWN</h3>
     * OnTouchListener 在 {@code View.dispatchTouchEvent} 里先于 {@code TextView.onTouchEvent}
     * 执行，返回 true 就等于把这次触摸从 View 手里拿走。View 收不到按下事件时**不会启动长按
     * 判定**，于是挂在同一个 TextView 上的 {@code setOnLongClickListener}（长按复制 / 长按
     * 操作面板）永远不会触发。旧实现正是这样：正文只要整段是一个链接（评论内容只有一条
     * {@code https://www.bilibili.com/} 就是典型），整块文字都被 ClickableSpan 覆盖，
     * 用户长按没有任何反应，抬手反而直接把链接打开。
     *
     * <h3>现在的分工</h3>
     * <ul>
     *   <li>ACTION_DOWN：**不消费**，只记下「按在哪个链接上、什么时候按的」，把事件交给
     *       View 去启动长按判定；</li>
     *   <li>ACTION_UP：按住时长已达系统长按阈值（{@link #isLongPressGesture}）时说明长按
     *       回调已经处理过这次手势，这里什么都不做，让 View 自己收尾；否则补发一次链接
     *       点击，并**取消 View 待触发的长按**——UP 被我们消费后 View 收不到收尾事件，
     *       那个延时任务不会自己消失，会在抬手之后把「长按复制」弹出来。</li>
     * </ul>
     *
     * <p>状态放在单例上：Android 默认不拆分多指事件（{@code splitMotionEvents=false}），
     * 同一时刻只有一整个触摸序列派发给一个 View，所以不存在两个 TextView 同时按下的情况。
     * 将来若启用多指拆分，这里要改成每个 View 一个实例。
     */
    public static class ClickableSpanTouchListener implements View.OnTouchListener {
        private static final ClickableSpanTouchListener INSTANCE = new ClickableSpanTouchListener();

        /** 按下时命中的链接；null 表示这次手势不是从链接上按下的（或已经结束）。 */
        private ClickableSpan downLink;

        /** 按下时的事件时间；-1 表示没有记录。 */
        private long downEventTime = -1L;

        public static ClickableSpanTouchListener getInstance() {
            return INSTANCE;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (!(v instanceof TextView)) {
                return false;
            }
            TextView widget = (TextView) v;
            CharSequence text = widget.getText();
            if (!(text instanceof Spanned)) {
                return false;
            }
            Spanned buffer = (Spanned) text;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    downEventTime = event.getEventTime();
                    downLink = findLinkAt(widget, buffer, event);
                    // 不消费按下事件，否则 View 不会启动长按判定（见类注释）
                    return false;
                }
                case MotionEvent.ACTION_UP: {
                    ClickableSpan link = downLink;
                    long downTime = downEventTime;
                    downLink = null;
                    downEventTime = -1L;
                    if (link == null) {
                        return false;
                    }
                    // 长按已经由 OnLongClickListener（长按复制 / 操作面板）处理，别再补一次链接点击
                    if (isLongPressGesture(widget.isLongClickable(), downTime,
                            event.getEventTime(), ViewConfiguration.getLongPressTimeout())) {
                        return false;
                    }
                    // 按下与抬手必须落在同一个链接上，避免滑动手势被误判成点击
                    if (findLinkAt(widget, buffer, event) != link) {
                        return false;
                    }
                    // UP 被下面消费，View 收不到收尾事件：替它取消待触发的长按并复位按压态
                    widget.cancelLongPress();
                    widget.setPressed(false);
                    link.onClick(widget);
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    downLink = null;
                    downEventTime = -1L;
                    return false;
                default:
                    return false;
            }
        }

        /**
         * 触摸点命中的链接；没命中（或布局还没完成）返回 null。
         */
        private static ClickableSpan findLinkAt(TextView widget, Spanned buffer, MotionEvent event) {
            Layout layout = widget.getLayout();
            if (layout == null) return null;

            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            int line = layout.getLineForVertical(y);
            if (line < 0 || line >= layout.getLineCount()) return null;
            int off = layout.getOffsetForHorizontal(line, x);

            ClickableSpan[] links = buffer.getSpans(off, off, ClickableSpan.class);
            return links.length == 0 ? null : links[0];
        }
    }
}
