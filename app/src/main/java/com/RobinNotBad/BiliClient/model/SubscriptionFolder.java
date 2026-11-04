package com.RobinNotBad.BiliClient.model;

import java.io.Serializable;

/**
 * 「我的订阅」里的一个条目：被别人创建、被我订阅的收藏夹或合集。
 *
 * <p>数据来源是 Web 端接口 {@code x/v3/fav/folder/collected/list}（带
 * {@code platform=web} 时才会把合集一起返回），与「我创建的收藏夹」
 * （{@code x/v3/fav/folder/created/list}）不是一回事，别混用模型。
 *
 * <p><b>两套 id 语义不一样，点进去的方式也不同</b>：
 * <ul>
 *   <li>{@link #TYPE_FAV_FOLDER}（11）：{@link #id} 是收藏夹的 <b>media_id</b>，
 *       进现有的 {@code FavoriteVideoListActivity}（只读）；</li>
 *   <li>{@link #TYPE_SEASON}（21）：{@link #id} 是合集的 <b>season_id</b>，
 *       走 {@code x/space/fav/season/list} 才能拿到里面的视频，
 *       不能当成 aid/media_id 用。</li>
 * </ul>
 */
public class SubscriptionFolder implements Serializable {

    /** 收藏夹。 */
    public static final int TYPE_FAV_FOLDER = 11;

    /** 视频合集。 */
    public static final int TYPE_SEASON = 21;

    /** 收藏夹 media_id 或合集 season_id，取决于 {@link #type}。 */
    public long id;

    /** {@link #TYPE_FAV_FOLDER} / {@link #TYPE_SEASON}，缺字段时按收藏夹处理。 */
    public int type = TYPE_FAV_FOLDER;

    /** 0=正常，1=已失效（被创建者删除/转为私密等）。 */
    public int state;

    public String title = "";

    public String cover = "";

    /** 创建者昵称，取接口里的 {@code upper.name}。 */
    public String upperName = "";

    /** 收藏夹/合集里的视频数。 */
    public int mediaCount;

    /** 合集才有意义的播放量，收藏夹通常为 0。 */
    public long viewCount;

    public boolean isSeason() {
        return type == TYPE_SEASON;
    }

    public boolean isInvalid() {
        return state == 1;
    }
}
