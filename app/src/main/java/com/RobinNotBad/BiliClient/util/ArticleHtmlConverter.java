package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.model.OpusParagraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 专栏（cv）HTML 正文 → 段落列表的轻量转换器。
 *
 * <p>为什么需要它：{@code x/article/view} 接口给的 {@code data.content} 是**原始 HTML**，
 * 而专栏详情页（{@code OpusInfoActivity}）的正文走的是自绘段落列表
 * （{@code OpusContentAdapter} + {@link OpusParagraph}），它只认段落对象。
 * 网页抓取路径（{@code OpusApi.fetchDetailFromHtml}）拿不到内容时，就只能靠这里把
 * 接口返回的 HTML 变成段落，否则用户看到的是一篇「只有标题没有正文」的文章。
 *
 * <p>为什么自己写状态机而不是引 Jsoup：本仓库虽然已有 Jsoup（{@code StringUtil.removeHtml}），
 * 但这里要的是「保留段落/图片结构」而不是「抽纯文本」，而且输入的 HTML 来自 B 站正文编辑器、
 * 结构非常有限。为了不给主流程加依赖、也为了让转换逻辑能跑纯 JVM 单测（单元测试里没有
 * Android 框架实现，{@code SpannableStringBuilder} 之类一律不可用），这里只做最朴素的扫描：
 *
 * <ul>
 *     <li>白名单标签：{@code p br img h1 h2 h3 a b i}——其中 {@code a/b/i} 只保留标签里的文字，
 *     不加样式（手表屏小，粗斜体在这个字号下几乎看不出差别，先按纯文本渲染）；</li>
 *     <li>段落级标签（{@code div li ul ol table tr blockquote ...}）当段落分隔符，标签本身丢掉；</li>
 *     <li>其余标签一律「去标签、留文字」（{@code span strong em font code pre ...} 都走这条）；</li>
 *     <li>{@code script}/{@code style} 里的内容整段丢弃（正文里偶尔混进统计脚本）；</li>
 *     <li>HTML 实体（{@code &amp; &nbsp; &#39; &#x4e2d; ...}）解码成真字符；</li>
 *     <li>过长的段落按 {@link #MAX_PARAGRAPH_CHARS} 切开，避免单个 TextView 撑爆排版。</li>
 * </ul>
 *
 * <p>产出直接塞进现有段落适配器：文本 → {@link OpusParagraph#TYPE_TEXT}，
 * 标题（h1~h3）→ {@link OpusParagraph#TYPE_HEADING}，图片 → {@link OpusParagraph#TYPE_PIC}
 * （content 为 {@code String[]}，与 {@code OpusParagraph.analyzePic} 一致）。
 *
 * <p>注意：本类**不得**引用任何 {@code android.*} 类——它会被纯 JVM 单测直接调用。
 */
public final class ArticleHtmlConverter {

    /** 单段最多多少个字符，超过就切开。手表一屏放不下太长的一段，切开也利于阅读时定位。 */
    public static final int MAX_PARAGRAPH_CHARS = 800;

    /** 图片地址可能落在这几个属性里，按顺序找第一个非空的（懒加载图把真地址放在 data-src）。 */
    private static final String[] IMG_SRC_KEYS = {"data-src", "src", "data-original"};

    private ArticleHtmlConverter() {
    }

    /**
     * HTML → 段落列表。传入 null / 空串返回空列表，绝不返回 null（调用方少一层判断）。
     */
    public static List<OpusParagraph> toParagraphs(String html) {
        List<OpusParagraph> paragraphs = new ArrayList<>();
        if (html == null || html.isEmpty()) return paragraphs;

        Builder builder = new Builder(paragraphs);
        int length = html.length();
        int index = 0;
        while (index < length) {
            // script/style 内部：直接跳到闭合标签，中间的文字不进正文
            if (builder.getSkipTag() != null) {
                int close = indexOfIgnoreCase(html, "</" + builder.getSkipTag(), index);
                if (close < 0) break;
                int gt = html.indexOf('>', close);
                if (gt < 0) break;
                index = gt + 1;
                builder.setSkipTag(null);
                continue;
            }

            char current = html.charAt(index);

            if (current == '<') {
                // 注释整段丢掉
                if (html.startsWith("<!--", index)) {
                    int end = html.indexOf("-->", index + 4);
                    index = end < 0 ? length : end + 3;
                    continue;
                }
                int gt = findTagEnd(html, index);
                if (gt < 0) {
                    // 没有闭合的 '<'：当普通字符，别把后面的正文全吃掉
                    builder.append('<');
                    index++;
                    continue;
                }
                String tag = html.substring(index + 1, gt);
                index = gt + 1;
                builder.handleTag(tag);
                continue;
            }

            if (current == '&') {
                int semicolon = html.indexOf(';', index + 1);
                if (semicolon > index && semicolon - index <= 12) {
                    int codePoint = decodeEntity(html.substring(index + 1, semicolon));
                    if (codePoint > 0) {
                        builder.appendCodePoint(codePoint);
                        index = semicolon + 1;
                        continue;
                    }
                }
            }

            builder.append(current);
            index++;
        }

        builder.flushText();
        return paragraphs;
    }

    /**
     * HTML → 纯文本（段落之间用换行连接）。
     *
     * <p>用于「只能显示摘要」之类的降级场景：接口的 summary 有时也带少量标签。
     * 图片在这里不产生任何文字。
     */
    public static String toPlainText(String html) {
        StringBuilder builder = new StringBuilder();
        for (OpusParagraph paragraph : toParagraphs(html)) {
            if (!(paragraph.content instanceof String)) continue;
            String text = (String) paragraph.content;
            if (text.isEmpty()) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(text);
        }
        return builder.toString();
    }

    /**
     * 是不是「有内容」的段落列表（只要有一段带文字或图片就算有内容）。
     *
     * <p>调用方用它区分「转换成功但文章确实没有正文」与「转换器没认出任何内容」。
     */
    public static boolean hasContent(List<OpusParagraph> paragraphs) {
        if (paragraphs == null) return false;
        for (OpusParagraph paragraph : paragraphs) {
            if (paragraph == null) continue;
            Object content = paragraph.content;
            if (content instanceof CharSequence) {
                if (content.toString().trim().length() > 0) return true;
            } else if (content instanceof Object[]) {
                if (((Object[]) content).length > 0) return true;
            }
        }
        return false;
    }

    /**
     * 找到标签结束的 '>' 位置：跳过属性值里的引号内容。
     * 返回 -1 表示这个 '<' 之后没有合法的标签结尾。
     */
    private static int findTagEnd(String html, int start) {
        char quote = 0;
        for (int i = start + 1; i < html.length(); i++) {
            char c = html.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '>') return i;
            // 又一个 '<'：说明前面的 '<' 不是标签，别一路吃到很远
            if (c == '<') return -1;
        }
        return -1;
    }

    /** 解码 HTML 实体，返回码点；认不出来返回 0。 */
    private static int decodeEntity(String entity) {
        switch (entity) {
            case "amp":
                return '&';
            case "lt":
                return '<';
            case "gt":
                return '>';
            case "quot":
                return '"';
            case "apos":
                return '\'';
            case "nbsp":
            case "ensp":
            case "emsp":
            case "thinsp":
                return ' ';
            case "ldquo":
                return 0x201C;
            case "rdquo":
                return 0x201D;
            case "lsquo":
                return 0x2018;
            case "rsquo":
                return 0x2019;
            case "hellip":
                return 0x2026;
            case "mdash":
                return 0x2014;
            case "ndash":
                return 0x2013;
            case "middot":
                return 0x00B7;
            case "times":
                return 0x00D7;
            case "copy":
                return 0x00A9;
            case "reg":
                return 0x00AE;
            case "deg":
                return 0x00B0;
            case "plusmn":
                return 0x00B1;
            case "le":
                return 0x2264;
            case "ge":
                return 0x2265;
            case "ne":
                return 0x2260;
            case "infin":
                return 0x221E;
            default:
                break;
        }
        if (entity.length() > 1 && entity.charAt(0) == '#') {
            try {
                int codePoint = (entity.charAt(1) == 'x' || entity.charAt(1) == 'X')
                        ? Integer.parseInt(entity.substring(2), 16)
                        : Integer.parseInt(entity.substring(1));
                if (Character.isValidCodePoint(codePoint)) return codePoint;
            } catch (NumberFormatException ignored) {
                // 认不出来就当普通文本，交给上层原样显示
            }
        }
        return 0;
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f';
    }

    private static int indexOfIgnoreCase(String source, String target, int from) {
        int max = source.length() - target.length();
        for (int i = Math.max(from, 0); i <= max; i++) {
            if (source.regionMatches(true, i, target, 0, target.length())) return i;
        }
        return -1;
    }

    /** 段落级标签：标签本身丢掉，但前后各断一次段。 */
    private static boolean isBlockTag(String name) {
        switch (name) {
            case "p":
            case "div":
            case "section":
            case "article":
            case "header":
            case "footer":
            case "aside":
            case "main":
            case "nav":
            case "figure":
            case "figcaption":
            case "blockquote":
            case "center":
            case "pre":
            case "ul":
            case "ol":
            case "li":
            case "dl":
            case "dt":
            case "dd":
            case "table":
            case "thead":
            case "tbody":
            case "tr":
            case "td":
            case "th":
            case "h4":
            case "h5":
            case "h6":
            case "hr":
            case "form":
                return true;
            default:
                return false;
        }
    }

    /** 取属性值（属性名大小写不敏感、允许无引号），取不到返回 null。 */
    private static String attrValue(String attrs, String key) {
        int from = 0;
        while (true) {
            int index = indexOfIgnoreCase(attrs, key, from);
            if (index < 0) return null;
            int afterKey = index + key.length();
            from = afterKey;

            boolean boundary = index == 0 || isSpace(attrs.charAt(index - 1));
            int cursor = afterKey;
            while (cursor < attrs.length() && isSpace(attrs.charAt(cursor))) cursor++;
            if (!boundary || cursor >= attrs.length() || attrs.charAt(cursor) != '=') continue;
            cursor++;
            while (cursor < attrs.length() && isSpace(attrs.charAt(cursor))) cursor++;
            if (cursor >= attrs.length()) return null;

            char quote = attrs.charAt(cursor);
            if (quote == '"' || quote == '\'') {
                int end = attrs.indexOf(quote, cursor + 1);
                if (end < 0) return null;
                return attrs.substring(cursor + 1, end);
            }
            int end = cursor;
            while (end < attrs.length() && !isSpace(attrs.charAt(end))) end++;
            return attrs.substring(cursor, end);
        }
    }

    /** 图片地址归一：`//` 开头补 https、`http:` 换 https（App 只走 https）。 */
    private static String normalizeImageUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.startsWith("//")) return "https:" + trimmed;
        if (trimmed.startsWith("http://")) return "https://" + trimmed.substring("http://".length());
        return trimmed;
    }

    /**
     * 扫描过程中的状态：正在攒的文字 + 当前标题级别 + 正在跳过的标签。
     */
    private static final class Builder {
        private final List<OpusParagraph> out;
        private final StringBuilder block = new StringBuilder();
        private int headingLevel;
        private String skipTag;

        Builder(List<OpusParagraph> out) {
            this.out = out;
        }

        String getSkipTag() {
            return skipTag;
        }

        void setSkipTag(String skipTag) {
            this.skipTag = skipTag;
        }

        void append(char c) {
            block.append(c);
        }

        void appendCodePoint(int codePoint) {
            block.appendCodePoint(codePoint);
        }

        void handleTag(String rawTag) {
            String tag = rawTag.trim();
            if (tag.isEmpty()) return;

            boolean closing = tag.charAt(0) == '/';
            if (closing) tag = tag.substring(1).trim();

            boolean selfClosing = tag.endsWith("/");
            if (selfClosing) tag = tag.substring(0, tag.length() - 1).trim();
            if (tag.isEmpty()) return;

            int space = 0;
            while (space < tag.length() && !isSpace(tag.charAt(space))) space++;
            String name = tag.substring(0, space).toLowerCase(Locale.ROOT);
            String attrs = space < tag.length() ? tag.substring(space + 1) : "";

            switch (name) {
                case "script":
                case "style":
                    if (!closing && !selfClosing) skipTag = name;
                    return;

                case "h1":
                case "h2":
                case "h3":
                    flushText();
                    headingLevel = closing ? 0 : (name.charAt(1) - '0');
                    return;

                case "img":
                    if (!closing) addImage(attrs);
                    return;

                case "br":
                    if (!closing) block.append('\n');
                    return;

                default:
                    // 段落级标签：前后各断一次；行内标签（a/b/i/span/...）只丢标签留文字
                    if (isBlockTag(name)) flushText();
            }
        }

        private void addImage(String attrs) {
            String src = null;
            for (String key : IMG_SRC_KEYS) {
                String value = attrValue(attrs, key);
                if (value != null && !value.trim().isEmpty()) {
                    src = value;
                    break;
                }
            }
            String url = normalizeImageUrl(src);
            if (url.isEmpty()) return;
            flushText();
            OpusParagraph paragraph = new OpusParagraph();
            paragraph.type = OpusParagraph.TYPE_PIC;
            paragraph.content = new String[]{url};
            out.add(paragraph);
        }

        /** 把攒着的文字变成一段（过长则切成多段）。 */
        void flushText() {
            int heading = headingLevel;
            String text = normalize(block.toString());
            block.setLength(0);
            if (text.isEmpty()) return;

            while (text.length() > MAX_PARAGRAPH_CHARS) {
                int cut = splitPoint(text);
                addText(heading, text.substring(0, cut));
                text = normalize(text.substring(cut));
                // 只有第一段保留标题样式，切出来的后续段落按正文渲染
                heading = 0;
                if (text.isEmpty()) return;
            }
            addText(heading, text);
        }

        private void addText(int heading, String text) {
            OpusParagraph paragraph = new OpusParagraph();
            paragraph.type = heading > 0 ? OpusParagraph.TYPE_HEADING : OpusParagraph.TYPE_TEXT;
            paragraph.content = text;
            out.add(paragraph);
        }

        /** 去掉首尾空白、把连续换行压成一个（空行在手表上只是白屏）。 */
        private static String normalize(String raw) {
            StringBuilder builder = new StringBuilder(raw.length());
            boolean lastWasNewline = false;
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '\r') continue;
                if (c == '\n') {
                    if (lastWasNewline) continue;
                    lastWasNewline = true;
                } else {
                    lastWasNewline = false;
                }
                builder.append(c);
            }
            return builder.toString().trim();
        }

        /** 超长段落的切分点：优先换行，其次空格/全角空格，最后硬切（避开代理对）。 */
        private static int splitPoint(String text) {
            int limit = Math.min(MAX_PARAGRAPH_CHARS, text.length() - 1);
            for (int i = limit; i > limit / 2; i--) {
                if (text.charAt(i) == '\n') return i;
            }
            for (int i = limit; i > limit / 2; i--) {
                char c = text.charAt(i);
                if (c == ' ' || c == '\u3000') return i;
            }
            int cut = Math.max(1, limit);
            if (Character.isLowSurrogate(text.charAt(cut))) cut--;
            return cut;
        }
    }
}
