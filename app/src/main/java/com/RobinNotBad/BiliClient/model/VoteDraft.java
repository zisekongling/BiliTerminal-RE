package com.RobinNotBad.BiliClient.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 投票草稿模型（编辑器 → 发布链路传递）
 */
public class VoteDraft implements Serializable {
    public String title;               // 投票标题
    public String desc;                // 投票描述
    public List<String> options = new ArrayList<>();  // 选项文字列表

    /**
     * 草稿是否有效（至少 2 个选项且标题不为空）
     */
    public boolean isValid() {
        return title != null && !title.trim().isEmpty()
                && options != null && options.size() >= 2;
    }

    /**
     * 草稿是否为空（未填写任何内容）
     */
    public boolean isEmpty() {
        return (title == null || title.trim().isEmpty())
                && (options == null || options.isEmpty());
    }
}