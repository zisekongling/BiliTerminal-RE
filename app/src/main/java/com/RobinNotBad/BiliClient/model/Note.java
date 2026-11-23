package com.RobinNotBad.BiliClient.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频笔记（26.10.04 批次 6 的 C27，仅查看）。
 *
 * <p>一个稿件只能加一篇私有笔记，所以从 {@code x/note/list/archive} 拿到 id 列表后直接读第一篇。
 * 正文见 {@link NoteBlock}：服务端下发的是 Quill delta 序列的 JSON 字符串，不是 HTML。
 */
public class Note {

    /**
     * 笔记 id。**只认字符串**：{@code x/note/list} 的示例里
     * {@code "note_id": 24508729145690110} 与 {@code "note_id_str": "24508729145690112"} 最后几位都不一样，
     * 因为 17 位 id 已超过 2^53，走 JSON number 必然丢精度。取值一律走
     * {@code NoteApi.pickNoteId}（note_id_str 优先），传参也当字符串。
     */
    public String noteId = "";

    public String title = "";

    /** 笔记预览文本（详情接口的 summary）。 */
    public String summary = "";

    /** 笔记所属稿件标题（详情接口的 {@code arc.title}）。 */
    public String videoTitle = "";

    /** 笔记所属稿件简介（详情接口的 {@code arc.desc}）。 */
    public String videoDesc = "";

    /** 笔记所属稿件 aid（详情接口的 {@code arc.oid}，oid_type=0 时即 avid）。 */
    public long aid;

    public String bvid = "";

    /** 公开笔记 id（cvid，{@code x/note/publish/info} 的入参）；私有笔记为 0。 */
    public long cvid;

    /** 公开发布时间，**秒级**时间戳；私有笔记为 0。 */
    public long pubtime;

    /** 笔记作者昵称（公开笔记详情才有；私有笔记为空串）。 */
    public String authorName = "";

    public String authorFace = "";

    public int likes;

    /** 正文块；接口没给正文（79503 笔记正文未找到）时为空表。 */
    public List<NoteBlock> blocks = new ArrayList<>();
}
