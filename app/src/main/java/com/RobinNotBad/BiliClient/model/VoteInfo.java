package com.RobinNotBad.BiliClient.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 投票信息模型
 */
public class VoteInfo implements Serializable {
    public long vote_id;
    public String title;
    public String desc;
    public int join_num;
    public int type;          // 0 文字 / 1 图文
    public int choice_cnt;    // 最多选几项
    public long end_time;
    public int status;        // 1=进行中
    public long vote_publisher;
    public List<Integer> my_votes = new ArrayList<>();  // 我的选择（opt_idx 数组）
    public List<VoteOption> options = new ArrayList<>();

    /**
     * 投票是否已结束
     * 注意：end_time 未解析到（为 0）时不视为过期，避免 feed 精简数据误判导致选项禁用
     */
    public boolean isExpired() {
        if (status != 1) return true;
        if (end_time <= 0) return false;
        return System.currentTimeMillis() / 1000 > end_time;
    }

    /**
     * 是否已投票
     */
    public boolean hasVoted() {
        return !my_votes.isEmpty();
    }
}