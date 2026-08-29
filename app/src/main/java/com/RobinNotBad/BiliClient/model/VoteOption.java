package com.RobinNotBad.BiliClient.model;

import java.io.Serializable;

/**
 * 投票选项模型
 */
public class VoteOption implements Serializable {
    public int opt_idx;        // 选项索引，从 1 开始
    public String opt_desc;    // 选项文字
    public String img_url;     // 选项图片（图片投票时使用）
    public int cnt;            // 该选项得票数（未投过票/无得票时为 0）
}