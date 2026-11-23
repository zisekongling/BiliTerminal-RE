package com.RobinNotBad.BiliClient.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 公开笔记列表的一页（{@code x/note/publish/list/archive}，26.11.23）。
 *
 * <p>公开笔记是**逐视频开关**：{@code data.show_public_note} 为 false 时接口会说
 * 「当前视频下公开笔记功能已关闭」，而不是报错，所以页面要能区分
 * 「功能关闭」与「还没有人发布」两种空态。
 */
public class NoteListPage {

    public List<NoteSummary> items = new ArrayList<>();

    /** 后面还有没有下一页（用 {@code data.page} 的 total / size / num 算）。 */
    public boolean hasMore;

    /** 这个视频的公开笔记功能是否开着。字段缺失时按开着处理。 */
    public boolean showPublicNote = true;

    /** 服务端给的空列表说明，例如「还没有人发布笔记哦，快去发布一篇吧～」。 */
    public String message = "";
}
