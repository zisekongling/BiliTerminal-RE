package com.RobinNotBad.BiliClient.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * DASH格式数据
 */
public class DashData {
    public int duration; // 视频长度（秒）
    public double minBufferTime; // 最小缓冲时间
    public List<DashVideoStream> videoStreams; // 视频流列表
    public List<DashAudioStream> audioStreams; // 音频流列表
    public DashAudioStream dolbyAudio; // 杜比全景声音频
    public DashAudioStream flacAudio; // 无损音轨音频

    public DashData() {
        videoStreams = new ArrayList<>();
        audioStreams = new ArrayList<>();
    }

    /**
     * 从JSON对象解析DASH数据
     */
    public static DashData fromJson(JSONObject json) throws JSONException {
        DashData dashData = new DashData();
        dashData.duration = json.optInt("duration", 0);
        dashData.minBufferTime = json.optDouble("minBufferTime",
                json.optDouble("min_buffer_time", 1.5));

        // 解析视频流
        dashData.videoStreams = DashVideoStream.fromJsonArray(json.optJSONArray("video"));

        // 解析音频流
        dashData.audioStreams = DashAudioStream.fromJsonArray(json.optJSONArray("audio"));

        // 解析杜比全景声
        JSONObject dolbyObj = json.optJSONObject("dolby");
        if (dolbyObj != null && dolbyObj.optInt("type", 0) > 0) {
            JSONObject dolbyAudioObj = dolbyObj.optJSONObject("audio");
            if (dolbyAudioObj == null && dolbyObj.has("audio") && !dolbyObj.isNull("audio")) {
                // audio可能是数组，取第一个
                dolbyAudioObj = dolbyObj.optJSONArray("audio").optJSONObject(0);
            }
            if (dolbyAudioObj != null) {
                dashData.dolbyAudio = DashAudioStream.fromJson(dolbyAudioObj);
            }
        }

        // 解析无损音轨
        JSONObject flacObj = json.optJSONObject("flac");
        if (flacObj != null && flacObj.optBoolean("display", false)) {
            JSONObject flacAudioObj = flacObj.optJSONObject("audio");
            if (flacAudioObj != null) {
                dashData.flacAudio = DashAudioStream.fromJson(flacAudioObj);
            }
        }

        return dashData;
    }

    /**
     * 获取指定清晰度的视频流。
     *
     * B 站同一清晰度会返回多个编码的流（H.264/H.265/AV1），顺序不定。
     * 为兼容本地 MediaMuxer 合并与系统播放器，按编码优先级选择：
     * H.264(avc1/codecid=7) > H.265(hev1,hvc1/codecid=12) > AV1(av01/codecid=13)。
     * 找不到指定清晰度时，取不高于 qn 的最高可用流；再不行取最低清晰度，
     * 避免误下载比用户选择更高的清晰度（流量与兼容性问题）。
     */
    public DashVideoStream getVideoStream(int qn) {
        DashVideoStream bestMatch = null;    // 精确匹配 qn 的最优编码
        DashVideoStream bestLower = null;    // 不高于 qn 的最优编码
        DashVideoStream lowest = null;       // 兜底：最低清晰度

        for (DashVideoStream stream : videoStreams) {
            if (lowest == null || stream.id < lowest.id) {
                lowest = stream;
            }
            if (stream.id == qn) {
                if (bestMatch == null || codecPriority(stream) < codecPriority(bestMatch)) {
                    bestMatch = stream;
                }
            } else if (stream.id < qn) {
                if (bestLower == null || stream.id > bestLower.id
                        || (stream.id == bestLower.id && codecPriority(stream) < codecPriority(bestLower))) {
                    bestLower = stream;
                }
            }
        }
        if (bestMatch != null) return bestMatch;
        if (bestLower != null) return bestLower;
        return lowest;
    }

    /** 编码优先级：数值越小越优先（H.264 > H.265 > AV1 > 其他） */
    private static int codecPriority(DashVideoStream stream) {
        switch (stream.codecid) {
            case 7: return 0;   // H.264 AVC
            case 12: return 1;  // H.265 HEVC
            case 13: return 2;  // AV1
            default: break;
        }
        String codecs = stream.codecs == null ? "" : stream.codecs;
        if (codecs.startsWith("avc1")) return 0;
        if (codecs.startsWith("hev1") || codecs.startsWith("hvc1")) return 1;
        if (codecs.startsWith("av01")) return 2;
        return 3;
    }

    /**
     * 获取用于下载合并的音频流。
     *
     * 优先普通 AAC 流（mp4a.*）：MediaMuxer 与系统播放器兼容性最好。
     * 杜比全景声(ec-3/eac3)、无损(flac) 封装 MediaMuxer 不支持写入，
     * 会导致合并必然失败，因此仅在没有任何普通音频流时才作为兜底。
     */
    public DashAudioStream getBestAudioStream() {
        DashAudioStream bestAac = null;
        DashAudioStream bestOther = null;
        for (DashAudioStream stream : audioStreams) {
            String codecs = stream.codecs == null ? "" : stream.codecs;
            boolean isAac = codecs.startsWith("mp4a") || codecs.isEmpty();
            if (isAac) {
                if (bestAac == null || stream.bandwidth > bestAac.bandwidth) {
                    bestAac = stream;
                }
            } else {
                if (bestOther == null || stream.bandwidth > bestOther.bandwidth) {
                    bestOther = stream;
                }
            }
        }
        if (bestAac != null) return bestAac;
        if (bestOther != null) return bestOther;
        // 最后才考虑杜比/无损（MediaMuxer 大概率不支持，仅兜底）
        if (dolbyAudio != null) return dolbyAudio;
        if (flacAudio != null) return flacAudio;
        return null;
    }

    /**
     * 是否有有效的音频流
     */
    public boolean hasAudio() {
        return !audioStreams.isEmpty() || dolbyAudio != null || flacAudio != null;
    }

    /**
     * 是否有有效的视频流
     */
    public boolean hasVideo() {
        return !videoStreams.isEmpty();
    }
}
