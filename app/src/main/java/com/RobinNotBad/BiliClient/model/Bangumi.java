package com.RobinNotBad.BiliClient.model;

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.ArrayList;

public class Bangumi {
    public Info info;
    public ArrayList<Section> sectionList;

    public static class Info {
        public long media_id;
        public long season_id;
        public int type;
        public int count;
        public float score;
        public String title;
        public String cover;
        public String cover_horizontal;
        public String type_name;
        public String area_name;
        public String indexShow;
        /** 更新状态文案（new_ep.desc，如"已完结，全12话"），可能为空 */
        public String newEpDesc;

        // 新增字段
        public String evaluate; // 简介
        public String staff; // 制作人员信息
        /** 角色与声优：`"角色：CV\n角色：CV…"` 多行文本，可能为空 */
        public String actors;
        public String record; // 备案号
        public String subtitle; // 副标题
        public Publish publish; // 发布时间信息
        public ArrayList<String> styles; // 标签
        public Stat stat; // 状态数
        public UpInfo up_info; // UP主信息
        public Series series; // 系列信息
        public ArrayList<Season> seasons; // 同系列所有季信息
    }

    public static class Publish {
        public int is_finish; // 完结状态 0：未完结 1：已完结
        public int is_started; // 是否发布 0：未发布 1：已发布
        public String pub_time; // 发布时间
        public String pub_time_show; // 发布时间文字介绍
    }

    public static class Stat {
        public int favorites; // 收藏数
        public int series_follow; // 系列追番数
        public int views; // 播放数
        public int vt; // 虚拟观看数
    }

    public static class UpInfo {
        public long mid;
        public String name;
        public String avatar;
    }

    public static class Series {
        public long series_id;
        public String series_title;
    }

    public static class Season {
        public long media_id;
        public long season_id;
        public String season_title;
        public String cover;
        public String badge;

        /**
         * 季类型（心跳上报的 sub_type），由 {@code BangumiApi.getSeasonDetail} 在切季时回填；
         * 0 表示没取到，上报侧要回退 {@link Info#type}。
         */
        public int seasonType;

        /** 该季的选集状态文案（new_ep.desc），由 {@code BangumiApi.getSeasonDetail} 回填，可能为空 */
        public String statusDesc;
    }

    public static class Section {
        public long id;
        public int type;
        public String title;
        public ArrayList<Episode> episodeList;

        public Section() {
        }
    }

    public static class Episode {
        public long id;
        public long aid;
        public long cid;
        public String title;
        public String title_long;
        public String cover;
        public String badge;//标记（如会员/限免）

        public Episode() {
        }

        public PlayerData toPlayerData() {
            return toPlayerData(0, 0);
        }

        /**
         * 番剧剧集转播放数据。番剧的进度上报必须带 epid/sid/sub_type，
         * 而本类（Episode）自身只有 epid（就是 id），季信息挂在父级 Bangumi.Info 上，
         * 所以由调用方把 season_id 与季类型一起传进来。
         *
         * @param seasonId   所属季的 season_id，未知传 0（播放侧会因此退回投稿视频的上报方式）
         * @param seasonType 所属季类型，即 Bangumi.Info#type，未知传 0
         */
        public PlayerData toPlayerData(long seasonId, int seasonType) {
            PlayerData data = new PlayerData(PlayerData.TYPE_BANGUMI);
            data.aid = aid;
            data.cid = cid;
            data.title = title;
            data.mid = SharedPreferencesUtil.getLong("mid", 0);
            data.epid = id;
            data.seasonId = seasonId;
            data.seasonType = seasonType;
            return data;
        }
    }
}
