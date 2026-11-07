package com.RobinNotBad.BiliClient.model;

import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme;
import static com.RobinNotBad.BiliClient.util.LinkUrlUtil.TYPE_WEB_URL;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import com.RobinNotBad.BiliClient.util.EmoteUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OpusParagraph {
    //这几个是b站的
    public static final int TYPE_TEXT = 1;
    public static final int TYPE_PIC = 2;
    public static final int TYPE_DIVIDER = 3;
    public static final int TYPE_TEXT_BLOCKQUOTE = 4;
    public static final int TYPE_LIST = 5;
    public static final int TYPE_LINK_CARD = 6;   // 链接卡片（可能为投票）
    public static final int TYPE_HEADING = 8;

    //这几个是自定义的
    public static final int TYPE_TEXT_OPUS = 99;
    public static final int TYPE_VIDEO = 100;
    public static final int TYPE_DYNAMIC = 101;
    public static final int TYPE_ARTICLE = 102;

    public static final int LIST_STYLE_NUMBER = 1;
    public static final int LIST_STYLE_DOT = 2;

    public static int FONT_SIZE_BILI_DEFAULT = 17;

    public int align;
    public int type;
    public Object content;
    public long voteId;   // 投票链接卡片时存 vote_id

    public OpusParagraph() {
    }

    public OpusParagraph(JSONObject para) throws JSONException {
        this.type = para.optInt("para_type");
        this.align = para.optInt("align");
        switch (type) {
            case TYPE_TEXT:
                this.content = analyzeText(para.optJSONObject("text"));
                break;
            case TYPE_TEXT_BLOCKQUOTE:
                this.content = analyzeBlockQuote(para.optJSONObject("blockquote"));
                break;
            case TYPE_HEADING:
                this.content = analyzeText(para.optJSONObject("heading"));
                break;
            case TYPE_PIC:
                this.content = analyzePic(para.optJSONObject("pic"));
                break;
            case TYPE_DIVIDER:
                this.content = analyzeDivider(para.optJSONObject("line"));
                break;
            case TYPE_LIST:
                this.content = analyzeList(para.optJSONObject("list"));
                break;
            case TYPE_TEXT_OPUS:
                this.content = analyzeOpus(para.optJSONArray("data"));
                break;
            case TYPE_LINK_CARD:
                this.voteId = analyzeLinkCard(para.optJSONObject("link_card"));
                this.content = (voteId != 0) ? "[投票]" : "";
                break;
            default:
                this.content = "[无法识别段落：" + type + "]";
        }
    }

    public CharSequence analyzeBlockQuote(JSONObject blockquote) throws JSONException {
        if (blockquote == null) return "";
        JSONArray children = blockquote.optJSONArray("children");
        if (children == null) return "";

        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();

        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.optJSONObject(i);
            if (child == null) break;
            stringBuilder.append(analyzeText(child.optJSONObject("text")));
        }

        // 空 blockquote（children 均为空文本）时 length()==0，旧代码 length()-1 = -1 会让 setSpan 抛越界
        if (stringBuilder.length() > 0) {
            // 引用块底色：正文色 20% 叠加（原写死白 20%，亮色模式下在白底上看不见）
            stringBuilder.setSpan(new BackgroundColorSpan((ColorScheme.INSTANCE.getTEXT_PRIMARY() & 0x00FFFFFF) | 0x33000000),
                    0, stringBuilder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return stringBuilder;
    }

    public CharSequence analyzeList(JSONObject list) throws JSONException {
        if (list == null) return "";
        int listStyle = list.optInt("style");

        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();

        JSONArray items = list.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                if (i != 0) stringBuilder.append("\n");
                JSONObject item = items.getJSONObject(i);
                switch (listStyle) {
                    case LIST_STYLE_NUMBER:
                        stringBuilder.append(String.valueOf(item.optInt("order"))).append(". ");
                        break;
                    case LIST_STYLE_DOT:
                        stringBuilder.append("· ");
                        break;
                }
                stringBuilder.append(analyzeText(item));
            }
        }

        JSONArray childrenPrimary = list.optJSONArray("children");
        if (childrenPrimary != null) {
            for (int i = 0; i < childrenPrimary.length(); i++) {
                if (i != 0) stringBuilder.append("\n");
                JSONObject childPrimary = childrenPrimary.optJSONObject(i);
                if (childPrimary == null) break;
                switch (listStyle) {
                    case LIST_STYLE_NUMBER:
                        stringBuilder.append(String.valueOf(childPrimary.optInt("order"))).append(". ");
                        break;
                    case LIST_STYLE_DOT:
                        stringBuilder.append("· ");
                        break;
                }

                JSONArray childrenSecondary = childPrimary.optJSONArray("children");
                if (childrenSecondary != null) {
                    SpannableStringBuilder childBuilder = new SpannableStringBuilder();
                    for (int j = 0; j < childrenSecondary.length(); j++) {
                        JSONObject childSecondary = childrenSecondary.optJSONObject(j);
                        childBuilder.append(analyzeText(childSecondary.optJSONObject("text")));
                    }
                    stringBuilder.append(childBuilder);
                }
            }
        }

        return stringBuilder;
    }

    public CharSequence analyzeText(JSONObject text) throws JSONException {
        if (text == null) return "";
        JSONArray nodes = text.optJSONArray("nodes");
        if (nodes == null) return "";

        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            switch (node.optString("type")) {
                case "TEXT_NODE_TYPE_WORD":
                    JSONObject word = node.getJSONObject("word");

                    int startPosition = stringBuilder.length();
                    stringBuilder.append(word.optString("words", ""));
                    int endPosition = stringBuilder.length();

                    //粗体斜体，BOLD/ITALIC/BOLD_ITALIC 正好对应 1/2/3 所以可以这样减少判断量？
                    JSONObject style = word.optJSONObject("style");
                    if (style != null) {
                        boolean bold = style.optBoolean("bold");
                        boolean italic = style.optBoolean("italic");
                        int styleInt = (bold ? Typeface.BOLD : 0) + (italic ? Typeface.ITALIC : 0);
                        if (styleInt != 0)
                            stringBuilder.setSpan(new StyleSpan(styleInt), startPosition, endPosition, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    //字体大小，使用的是相对大小，17号的字体在手表上还是太逆天了
                    int fontSize = word.optInt("font_size", 17);
                    if (fontSize != FONT_SIZE_BILI_DEFAULT) {
                        float fontScale = fontSize * 1.0f / FONT_SIZE_BILI_DEFAULT;
                        stringBuilder.setSpan(new RelativeSizeSpan(fontScale), startPosition, endPosition, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    //字体颜色
                    String color = word.optString("color", "#18191c");
                    if (color.startsWith("#") && !color.equals("#18191c")) try {
                        stringBuilder.setSpan(new ForegroundColorSpan(Color.parseColor(color)), startPosition, endPosition, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } catch (Exception ignored) {
                        Logu.e("color error", color);
                    }

                    break;

                case "TEXT_NODE_TYPE_RICH":
                    // 段落里的富文本节点（EMOJI / AT / TOPIC / WEB …）与整篇 rich_list 走同一套解析，
                    // 两条路径不再各写一份 switch：旧实现只处理 EMOJI/TOPIC，@ 和网页链接在段落里
                    // 退化成不可点纯文本，还留了一个快照里不存在的 RICH_TEXT_NODE_TYPE_RICH 死分支
                    RichTextSegment segment = OpusRichTextParser.parseOne(node.optJSONObject("rich"));
                    if (segment != null) {
                        appendRichSegments(stringBuilder, java.util.Collections.singletonList(segment));
                    }
                    break;
                default:
                    stringBuilder.append("[").append(node.optString("type")).append("]");
            }
        }

        return stringBuilder;
    }

    public String[] analyzePic(JSONObject allJson) throws JSONException {
        if (allJson == null) return new String[0];
        JSONArray picsJson = allJson.optJSONArray("pics");
        if (picsJson == null) return new String[0];
        String[] pics = new String[picsJson.length()];
        for (int i = 0; i < picsJson.length(); i++) {
            String url = picsJson.getJSONObject(i).optString("url");
            pics[i] = url.startsWith("http") ? url : "http:" + url;
        }
        return pics;
    }

    public String[] analyzeDivider(JSONObject allJson) throws JSONException {
        if (allJson == null) return new String[0];
        JSONObject pic = allJson.optJSONObject("pic");   // line.pic 可能为 null（分隔线无图），此时返回空数组，避免中断整篇解析
        if (pic == null) return new String[0];
        String url = pic.optString("url");
        return new String[]{url.startsWith("http") ? url : "http:" + url};
    }

    public CharSequence analyzeOpus(JSONArray rich_list) throws JSONException {
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        appendRichSegments(stringBuilder, OpusRichTextParser.parse(rich_list));
        return stringBuilder;
    }

    /**
     * 把解析好的富文本段落依次追加进 [stringBuilder]，并按种类挂可点击 / 表情 span。
     *
     * 这是两条 Opus 路径（段落 `text.nodes` 与整篇 `rich_list`）**唯一**的渲染实现：
     * 同一种节点在摘要与正文里必须表现一致（调研清单第 2 条）。
     */
    private static void appendRichSegments(SpannableStringBuilder stringBuilder, java.util.List<RichTextSegment> segments) {
        for (RichTextSegment segment : segments) {
            if (segment == null) continue;
            int start = stringBuilder.length();
            stringBuilder.append(segment.text);
            int end = stringBuilder.length();
            switch (segment.kind) {
                case RichTextSegment.KIND_AT:
                    StringUtil.setSingleAt(stringBuilder, new At(segment.atMid, start, end));
                    break;
                case RichTextSegment.KIND_TOPIC:
                    StringUtil.setSingleTopic(stringBuilder, start, end, segment.jumpUrl);
                    break;
                case RichTextSegment.KIND_WEB:
                    // WEB 节点的 text 可能被服务端缩短（text="av1"、jump_url 才是真地址），
                    // 所以把 jump_url 当「真实值」传给 LinkClickableSpan，点击用的是它
                    stringBuilder.setSpan(new StringUtil.LinkClickableSpan(segment.text, TYPE_WEB_URL, segment.jumpUrl),
                            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case RichTextSegment.KIND_EMOJI:
                    if (segment.emojiUrl != null && !segment.emojiUrl.isEmpty()) {
                        EmoteUtil.replaceSingle(stringBuilder, segment.emojiUrl, segment.emojiSize, start, end, 1.0f);
                    }
                    break;
                default:
                    break;
            }
        }
    }


    /**
     * 解析链接卡片段落（para_type=6）
     * 若卡片为投票（LINK_CARD_TYPE_VOTE），返回 vote_id；否则返回 0
     */
    public long analyzeLinkCard(JSONObject linkCard) throws JSONException {
        if (linkCard == null) return 0;
        JSONObject card = linkCard.optJSONObject("card");
        if (card == null) return 0;
        String type = card.optString("type");
        // 投票链接卡片：EVA3_VOTE（eva3_vote.info.vote_id）或旧版 VOTE（vote.vote_id）
        JSONObject vote = null;
        if ("LINK_CARD_TYPE_EVA3_VOTE".equals(type)) {
            JSONObject eva3 = card.optJSONObject("eva3_vote");
            if (eva3 != null) vote = eva3.optJSONObject("info");
        } else if ("LINK_CARD_TYPE_VOTE".equals(type)) {
            vote = card.optJSONObject("vote");
        }
        if (vote == null) return 0;
        return vote.optLong("vote_id", 0);
    }
}