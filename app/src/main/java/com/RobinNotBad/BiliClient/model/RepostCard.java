package com.RobinNotBad.BiliClient.model;

/**
 * 「分享至动态」用的一张通用卡片。
 *
 * <p>把「转发哪一种内容（[dynType]）＋ 它的 id（[rid]）＋ 要给人看的三行字（标题/封面/副标题）
 * ＋ 原作者」打包成一个对象：调用方（番剧页、收藏夹页、将来的分享面板）只管填这张卡，
 * 发布页只管画卡与发请求，不必为每种内容各写一套转发逻辑。
 *
 * <p>纯数据类：不读资源、不碰网络、不做校验之外的任何事。字段可空即表示「这一行不显示」，
 * 页面按空值隐藏对应控件，不会因为缺封面就崩。
 */
public class RepostCard {

    /** 转发目标类型（动态报文里的 dyn_type），取值见 util/ForwardTargets.kt；<=0 视为不合法。 */
    private int dynType;
    /** 转发目标 id（视频是 aid，收藏夹是 media_id 一类）。 */
    private long rid;
    /** 卡片标题；为空则不显示标题行。 */
    private String title;
    /** 卡片封面 URL；为空则整块隐藏封面，只留文字。 */
    private String cover;
    /** 卡片副标题（作者名、集数、条目数一类）；为空则不显示。 */
    private String subtitle;
    /** 原作者昵称：非空时转发会自动带「//@作者:标题」的引用。 */
    private String authorName;
    /** 原作者 uid，配合 authorName 使用。 */
    private long authorMid;

    public RepostCard() {
    }

    public RepostCard(int dynType, long rid, String title) {
        this.dynType = dynType;
        this.rid = rid;
        this.title = title;
    }

    /**
     * 是否具备发布所需的最小信息：类型与 id 都得成立。
     *
     * <p>发布页据此提前拦下，避免发出去一张服务端认不出的卡片。
     */
    public boolean isValid() {
        return dynType > 0 && rid > 0;
    }

    public int getDynType() {
        return dynType;
    }

    public void setDynType(int dynType) {
        this.dynType = dynType;
    }

    public long getRid() {
        return rid;
    }

    public void setRid(long rid) {
        this.rid = rid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public long getAuthorMid() {
        return authorMid;
    }

    public void setAuthorMid(long authorMid) {
        this.authorMid = authorMid;
    }
}
