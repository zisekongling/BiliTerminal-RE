package com.RobinNotBad.BiliClient.model;

/**
 * 笔记列表页的一行（26.11.23 笔记改造）。
 *
 * <p>两种来源合在同一个模型里，靠 {@link #own} 区分：
 * <ul>
 *   <li>「我的笔记」：该稿件下自己写的私有笔记，来自 {@code x/note/list/archive}。
 *       只有 {@link #own}、{@link #noteId}、{@link #title} 有意义——列表页不预取正文；</li>
 *   <li>公开笔记：来自 {@code x/note/publish/list/archive}，用 {@link #cvid} 定位详情。
 *       免登录可看，所以列表页在未登录时也有内容。</li>
 * </ul>
 */
public class NoteSummary {

    /** true 表示这一行是「我的笔记」（私有笔记），false 表示公开笔记。 */
    public boolean own;

    /** 公开笔记 id（详情接口 {@code x/note/publish/info} 的入参）。私有笔记为 0。 */
    public long cvid;

    /** 私有笔记 id（字符串，17 位超出 2^53，原因见 {@link Note#noteId}）。公开笔记为空串。 */
    public String noteId = "";

    public String title = "";

    /** 服务端给的预览文本。私有笔记这一行不预取，留空。 */
    public String summary = "";

    /** 公开发布时间，**秒级**时间戳；私有笔记为 0。 */
    public long pubtime;

    public String authorName = "";

    public String authorFace = "";

    public int likes;

    public boolean hasLike;
}
