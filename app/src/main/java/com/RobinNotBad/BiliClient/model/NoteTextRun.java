package com.RobinNotBad.BiliClient.model;

/**
 * 段落内一段同一样式的文字（26.11.23 笔记改造）。
 *
 * <p>[{@link #start}, {@link #end}) 是相对所属段落 {@link NoteContentItem#text} 的下标。
 * 不直接用 {@code Spannable} 存，是为了让「合并 delta 元素」这段逻辑留在
 * {@code NoteApi.buildContent} 里保持纯函数、可单测（Spannable 依赖 android.text，JVM 单测拿不到）。
 */
public class NoteTextRun {

    public int start;

    public int end;

    public boolean bold;

    public boolean underline;

    public boolean strike;

    /** 文字色，{@code #RRGGBB} 形式；空串表示不改。 */
    public String color = "";

    /** 背景色，同上。 */
    public String background = "";
}
