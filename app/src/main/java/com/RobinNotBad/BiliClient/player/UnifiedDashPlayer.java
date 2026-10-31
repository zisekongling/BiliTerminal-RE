package com.RobinNotBad.BiliClient.player;

import java.util.Map;

/**
 * 「统一时钟播放器」的公共契约。
 *
 * <p>本仓库旧的在线播放方案是两套独立播放器：IjkMediaPlayer 播纯视频流、android.media.MediaPlayer
 * 播纯音频流，两条流各走各的时钟，只能靠轮询校正，于是必然出现音画不同步。统一时钟方案改成
 * **一个播放器同时渲染视频与音频**（见 {@link ExoDashMediaPlayer}），但上层（PlayerActivity 等）
 * 不应该被绑死在 ExoPlayer 这个具体实现上——它们只通过本接口完成两件旧播放器没有能力做的事：
 *
 * <ol>
 *   <li>{@link #setPlaybackSpeed(float)}：倍速（DASH 场景要同时作用在音、视频两条轨上）；</li>
 *   <li>{@link #setAudioSource(String, Map)}：在视频流之外补上音频流地址（旧方案里音频是
 *       另一个播放器在播，统一时钟方案里它必须交给同一个播放器）。</li>
 * </ol>
 *
 * <p>刻意只放这两个方法：接口里不出现任何 ExoPlayer 类型，这样没有 ExoPlayer 实现时
 * （例如回退到 IjkMediaPlayer）上层也只需做一次 {@code instanceof} 判断，不必编译期依赖。
 */
public interface UnifiedDashPlayer {

    /** 设置播放倍速（1.0 为原速）。实现方需要把它同时作用到音、视频两条轨上。 */
    void setPlaybackSpeed(float speed);

    /**
     * 设置音频流地址与请求头。
     *
     * <p>在 {@code prepareAsync()} 之前调用最稳妥（下一次 prepare 会直接带上音频轨）；
     * 若在准备完成之后调用，实现方应重建媒体源并尽量回到原来的播放位置。
     *
     * @param audioUrl 音频流地址；传 null 或空串表示「只播视频」
     * @param headers  该音频流需要附加的请求头（Referer / Cookie 等），可以为 null
     */
    void setAudioSource(String audioUrl, Map<String, String> headers);

    /**
     * 切换「听视频 / 纯音频」模式。
     *
     * <p>B 站「听视频」场景只需要音频轨。旧的「IjkMediaPlayer 播视频 + MediaPlayer 播音频」方案
     * 在这条路径上会抛 IJK -10000；统一时钟实现应当**完全不创建视频轨数据源**，
     * 既省流量也避免视频解码开销。
     *
     * <p>约定在 {@code prepareAsync()} **之前**调用（实现方在 prepare 时读取最终值）。
     *
     * @param audioOnly true = 只播音频轨；false = 正常视频+音频
     */
    void setAudioOnly(boolean audioOnly);
}
