package com.RobinNotBad.BiliClient.model;

public class CoinLog {
    public String time;

    /**
     * 变化量。接口文档里是 num 而不是整数：打赏类记录的收入是小数（`0.1` / `0.2`），
     * 用 int 接会把它们截成 0（见 {@link com.RobinNotBad.BiliClient.util.CoinLogDelta}）。
     */
    public double delta;

    public String reason;

    public CoinLog(String time, double delta, String reason) {
        this.time = time;
        this.delta = delta;
        this.reason = reason;
    }
}

