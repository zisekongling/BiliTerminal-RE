package com.RobinNotBad.BiliClient.model;

public class FavoriteFolder {
    public long id;
    public long mediaId;
    public String name;
    public String cover;
    public int videoCount;
    public int maxCount;
    public boolean isDefault;
    /**
     * 简介（备注）。列表接口（created/list）不下发该字段，只有
     * {@code x/v3/fav/folder/info} 会返回，编辑页用它预填。
     */
    public String intro;
    /**
     * 0=公开，1=私密。v3 接口没有独立的 privacy 字段，由 {@code attr} 的
     * bit0 解析（见 FavoriteApi#isPrivateFolder 与 bilibili-API/docs/fav/info.md:185-191）。
     */
    public int privacy;
    /**
     * 创建者（UP 主）昵称与 mid。列表接口（created/list、collected/list）不下发该字段，只有
     * {@code x/v3/fav/folder/info} 的 {@code data.upper} 会返回，
     * 字段口径见 {@code bilibili-API/docs/fav/info.md:53-62}（upper.name / upper.mid）。
     * 收藏夹详情页「分享至动态」把它填进 RepostCard 的作者行；拿不到时留空（0），卡片照样能发。
     */
    public String upperName;
    public long upperMid;

    public FavoriteFolder() {
    }
}
