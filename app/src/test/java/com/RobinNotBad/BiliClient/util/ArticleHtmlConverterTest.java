package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.RobinNotBad.BiliClient.model.OpusParagraph;

import org.junit.Test;

import java.util.List;

/**
 * 专栏 HTML 正文 → 段落列表的转换（反馈 #142）。
 *
 * <p>这段转换是「cv 文章看不到文字」的兜底路径：网页抓取被风控以后，正文只能从
 * {@code x/article/view} 的 HTML 里来。它纯逻辑、没有 Android 依赖，正好全量钉住——
 * 手表上没有 Ctrl+F，排版错了也只是「这段文字怪怪的」，事后极难发现。
 */
public class ArticleHtmlConverterTest {

    /** 把结果里的文字段落按顺序拼起来，便于断言「内容没丢」。 */
    private static String joinText(List<OpusParagraph> paragraphs) {
        StringBuilder builder = new StringBuilder();
        for (OpusParagraph paragraph : paragraphs) {
            if (paragraph.content instanceof CharSequence) {
                if (builder.length() > 0) builder.append('|');
                builder.append(paragraph.content);
            }
        }
        return builder.toString();
    }

    private static boolean allTextParagraphs(List<OpusParagraph> paragraphs) {
        for (OpusParagraph paragraph : paragraphs) {
            if (paragraph.type != OpusParagraph.TYPE_TEXT) return false;
        }
        return true;
    }

    @Test
    public void 段落标题图片_各归各位() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(
                "<p>第一段</p><p>第二段</p><h2>小标题</h2><img src=\"//i0.hdslb.com/a.jpg\">");

        assertEquals("两段正文 + 一个标题 + 一张图", 4, paragraphs.size());
        assertEquals(OpusParagraph.TYPE_TEXT, paragraphs.get(0).type);
        assertEquals("第一段", paragraphs.get(0).content);
        assertEquals("第二段", paragraphs.get(1).content);
        assertEquals("h2 要按标题段落交给适配器", OpusParagraph.TYPE_HEADING, paragraphs.get(2).type);
        assertEquals("小标题", paragraphs.get(2).content);

        OpusParagraph picture = paragraphs.get(3);
        assertEquals(OpusParagraph.TYPE_PIC, picture.type);
        assertTrue("图片段落的 content 必须是 String[]，与 analyzePic 的输出一致",
                picture.content instanceof String[]);
        assertEquals("协议相对的地址要补 https（App 只走 https）",
                "https://i0.hdslb.com/a.jpg", ((String[]) picture.content)[0]);
    }

    @Test
    public void 行内标签只去标签_文字一个不丢() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(
                "<p>前<b>粗体</b>后<i>斜体</i>再<a href=\"https://b23.tv/x\">链接</a></p>");

        assertEquals(1, paragraphs.size());
        assertEquals("前粗体后斜体再链接", paragraphs.get(0).content);
    }

    @Test
    public void 实体解码成真字符() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(
                "<p>a&amp;b&lt;c&gt;d&quot;e&#39;f&#x4e2d;g</p>");

        assertEquals("a&b<c>d\"e'f中g", paragraphs.get(0).content);
    }

    @Test
    public void 空标签与脚本内容不产生段落() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(
                "<p></p><div>   </div><script>var a = 1 < 2;</script>"
                        + "<style>.a{color:red}</style><p>正文</p>");

        assertEquals("空段落、脚本、样式都不该进正文", 1, paragraphs.size());
        assertEquals("正文", paragraphs.get(0).content);
    }

    @Test
    public void 未知标签按文本处理() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(
                "<span>甲</span><font color=\"red\">乙</font><strong>丙</strong>");

        assertEquals(1, paragraphs.size());
        assertEquals("认不出的标签只丢标签、留文字", "甲乙丙", paragraphs.get(0).content);
    }

    @Test
    public void 超长段落被切开_内容不丢() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 2000; i++) text.append('字');
        String html = "<p>" + text + "</p>";

        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(html);

        assertTrue("2000 字必须切成多段", paragraphs.size() >= 3);
        StringBuilder rebuilt = new StringBuilder();
        int longest = 0;
        for (OpusParagraph paragraph : paragraphs) {
            String content = (String) paragraph.content;
            assertTrue("每段都不该超过上限", content.length() <= ArticleHtmlConverter.MAX_PARAGRAPH_CHARS);
            longest = Math.max(longest, content.length());
            rebuilt.append(content);
        }
        assertEquals("切段不能丢字", text.toString(), rebuilt.toString());
        assertEquals("切满为止，最后一段才是不满的", ArticleHtmlConverter.MAX_PARAGRAPH_CHARS, longest);
    }

    @Test
    public void 段落内换行保留_连续空行折叠() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(
                "<p>第一行<br>第二行<br><br>第三行</p>");

        assertEquals(1, paragraphs.size());
        assertEquals("第一行\n第二行\n第三行", paragraphs.get(0).content);
    }

    @Test
    public void 注释被整段丢弃() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(
                "<!-- 这段是注释 <p>不该出现</p> --><p>真正的正文</p>");

        assertEquals(1, paragraphs.size());
        assertEquals("真正的正文", paragraphs.get(0).content);
    }

    @Test
    public void 图片地址归一_http换成https() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(
                "<img src=\"http://i0.hdslb.com/b.png\">");

        assertEquals(1, paragraphs.size());
        assertEquals("https://i0.hdslb.com/b.png", ((String[]) paragraphs.get(0).content)[0]);
    }

    @Test
    public void 图片地址优先取data_src() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(
                "<img src=\"data:image/gif;base64,R0lGOD\" data-src=\"//i0.hdslb.com/c.webp\">");

        assertEquals(1, paragraphs.size());
        assertEquals("https://i0.hdslb.com/c.webp", ((String[]) paragraphs.get(0).content)[0]);
    }

    @Test
    public void 没有src的图片直接跳过() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs("<img><p>正文</p>");

        assertEquals(1, paragraphs.size());
        assertEquals("正文", paragraphs.get(0).content);
    }

    @Test
    public void 畸形html不吞正文() {
        // 只有一个孤零零的 '<'（B站正文编辑器里偶尔会出现），不能把后面的整段正文都吃掉
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs("<p>1 < 2 是真的</p>");

        assertEquals(1, paragraphs.size());
        assertEquals("1 < 2 是真的", paragraphs.get(0).content);
    }

    @Test
    public void 空输入不崩也不产生段落() {
        assertTrue(ArticleHtmlConverter.toParagraphs(null).isEmpty());
        assertTrue(ArticleHtmlConverter.toParagraphs("").isEmpty());
        assertTrue(ArticleHtmlConverter.toParagraphs("   ").isEmpty());
    }

    @Test
    public void 纯文本输入当成一段正文() {
        // 接口的 data.summary 是纯文本（不带标签），会走到同一条转换链
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs("这是一段没有任何标签的摘要");

        assertEquals(1, paragraphs.size());
        assertTrue(allTextParagraphs(paragraphs));
        assertEquals("这是一段没有任何标签的摘要", paragraphs.get(0).content);
    }

    @Test
    public void toPlainText_段落间换行且不含图片() {
        String plain = ArticleHtmlConverter.toPlainText(
                "<p>甲</p><img src=\"//i0.hdslb.com/d.jpg\"><p>乙</p>");

        assertEquals("甲\n乙", plain);
    }

    @Test
    public void hasContent_只认有文字或图片的段落() {
        assertFalse(ArticleHtmlConverter.hasContent(null));
        assertFalse("只有空段落不算有内容",
                ArticleHtmlConverter.hasContent(ArticleHtmlConverter.toParagraphs("<p>   </p>")));
        assertTrue(ArticleHtmlConverter.hasContent(
                ArticleHtmlConverter.toParagraphs("<img src=\"//i0.hdslb.com/e.jpg\">")));
        assertTrue(ArticleHtmlConverter.hasContent(
                ArticleHtmlConverter.toParagraphs("<p>正文</p>")));
    }

    @Test
    public void 列表与引用按段落拆开() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(
                "<ul><li>第一条</li><li>第二条</li></ul><blockquote>引用的话</blockquote>");

        assertEquals("列表项与引用都当普通段落处理（不失内容）", 3, paragraphs.size());
        assertEquals("第一条", paragraphs.get(0).content);
        assertEquals("第二条", paragraphs.get(1).content);
        assertEquals("引用的话", paragraphs.get(2).content);
        assertTrue(allTextParagraphs(paragraphs));
    }

    @Test
    public void 嵌套标签交错不产生多余空段() {
        List<OpusParagraph> paragraphs = ArticleHtmlConverter.toParagraphs(
                "<div><p>外层第一段</p><div><span>内层</span></div></div>");

        assertEquals("内层的文字要接在同一段里，别被拆成两段", 2, paragraphs.size());
        assertEquals("外层第一段", paragraphs.get(0).content);
        assertEquals("内层", paragraphs.get(1).content);
        assertEquals("外层第一段|内层", joinText(paragraphs));
    }
}
