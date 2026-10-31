package com.RobinNotBad.BiliClient.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

/**
 * DASH 音轨兼容性选择的单测。
 *
 * <p>背景：在线播放换成 ExoPlayer 单时钟后，音轨若选到播放器不支持的编码（杜比 ec-3、无损 flac），
 * 就是「音频轨不加载 / 音画不同步」。下载合并那条路仍然要「尽量有音轨」，所以两个方法并存：
 * {@link DashData#getBestCompatibleAudioStream()} 只认 AAC/MP4，{@link DashData#getBestAudioStream()}
 * 保持原样。这里把两条选择规则的差异钉死。
 */
public class DashDataCompatibleAudioTest {

    private static DashData parse(String json) throws Exception {
        return DashData.fromJson(new JSONObject(json));
    }

    @Test
    public void compatible_picksHighestBandwidthAac() throws Exception {
        // 三个普通音轨都是 AAC：选码率最高的 192K
        DashData data = parse("{\"audio\":["
                + "{\"id\":30216,\"baseUrl\":\"https://a/64.m4s\",\"bandwidth\":67000,"
                + "\"mimeType\":\"audio/mp4\",\"codecs\":\"mp4a.40.5\"},"
                + "{\"id\":30232,\"baseUrl\":\"https://a/132.m4s\",\"bandwidth\":132000,"
                + "\"mimeType\":\"audio/mp4\",\"codecs\":\"mp4a.40.2\"},"
                + "{\"id\":30280,\"baseUrl\":\"https://a/192.m4s\",\"bandwidth\":320000,"
                + "\"mimeType\":\"audio/mp4\",\"codecs\":\"mp4a.40.2\"}]}");

        DashAudioStream best = data.getBestCompatibleAudioStream();
        assertNotNull("有 AAC 流时必须返回一条", best);
        assertEquals("必须选 AAC 里码率最高的那条", 30280, best.id);
    }

    @Test
    public void compatible_acceptsAudioMp4MimeWithoutCodecs() throws Exception {
        // codecs 缺失但 mimeType 是 audio/mp4：视为兼容
        DashData data = parse("{\"audio\":["
                + "{\"id\":30280,\"baseUrl\":\"https://a/192.m4s\",\"bandwidth\":320000,"
                + "\"mimeType\":\"audio/mp4\"}]}");

        DashAudioStream best = data.getBestCompatibleAudioStream();
        assertNotNull(best);
        assertEquals(30280, best.id);
    }

    @Test
    public void compatible_skipsDolbyAndFlac() throws Exception {
        // 杜比/无损码率再高也不能被选：它们不是普通音轨，播放器不加载就是没声音
        DashData data = parse("{\"audio\":["
                + "{\"id\":30216,\"baseUrl\":\"https://a/aac.m4s\",\"bandwidth\":67000,"
                + "\"mimeType\":\"audio/mp4\",\"codecs\":\"mp4a.40.5\"}],"
                + "\"dolby\":{\"type\":1,\"audio\":{\"id\":30250,\"baseUrl\":\"https://a/dolby.m4s\","
                + "\"bandwidth\":999999,\"mimeType\":\"audio/mp4\",\"codecs\":\"ec-3\"}},"
                + "\"flac\":{\"display\":true,\"audio\":{\"id\":30251,\"baseUrl\":\"https://a/flac.m4s\","
                + "\"bandwidth\":9999999,\"mimeType\":\"audio/mp4\",\"codecs\":\"fLaC\"}}}");

        DashAudioStream best = data.getBestCompatibleAudioStream();
        assertNotNull(best);
        assertEquals("必须选普通 AAC，而不是码率更高的杜比/无损", 30216, best.id);
    }

    @Test
    public void compatible_fallsBackToHighestNormalAudioWhenNoneCompatible() throws Exception {
        // 普通音轨里没有 AAC（例如只有 opus / ec-3）：退回普通音轨中码率最高的一条，
        // 但依然不碰 dolby/flac（哪怕它们码率更高）
        DashData data = parse("{\"audio\":["
                + "{\"id\":30216,\"baseUrl\":\"https://a/opus.m4s\",\"bandwidth\":1000,"
                + "\"mimeType\":\"audio/opus\",\"codecs\":\"opus\"},"
                + "{\"id\":30232,\"baseUrl\":\"https://a/eac3.m4s\",\"bandwidth\":9000,"
                + "\"mimeType\":\"audio/eac3\",\"codecs\":\"ec-3\"}],"
                + "\"dolby\":{\"type\":1,\"audio\":{\"id\":30250,\"baseUrl\":\"https://a/dolby.m4s\","
                + "\"bandwidth\":9999999,\"mimeType\":\"audio/mp4\",\"codecs\":\"ec-3\"}}}");

        DashAudioStream best = data.getBestCompatibleAudioStream();
        assertNotNull(best);
        assertEquals("没有兼容流时退回普通音轨里码率最高的", 30232, best.id);
    }

    @Test
    public void compatible_noNormalAudio_returnsNullEvenWithDolbyAndFlac() throws Exception {
        // 只有杜比/无损（没有普通音轨）时返回 null：交给上层回退音视频合一的 MP4
        DashData data = parse("{\"dolby\":{\"type\":1,\"audio\":{\"id\":30250,"
                + "\"baseUrl\":\"https://a/dolby.m4s\",\"bandwidth\":999999,"
                + "\"mimeType\":\"audio/mp4\",\"codecs\":\"ec-3\"}},"
                + "\"flac\":{\"display\":true,\"audio\":{\"id\":30251,"
                + "\"baseUrl\":\"https://a/flac.m4s\",\"bandwidth\":9999999,"
                + "\"mimeType\":\"audio/mp4\",\"codecs\":\"fLaC\"}}}");

        assertNull("没有普通音轨时必须返回 null，绝不返回杜比/无损",
                data.getBestCompatibleAudioStream());
    }

    @Test
    public void compatible_noAudioAtAll_returnsNull() throws Exception {
        DashData data = parse("{\"video\":[]}");
        assertNull(data.getBestCompatibleAudioStream());
    }

    @Test
    public void legacy_getBestAudioStream_stillPrefersAacThenFallsBackToDolby() throws Exception {
        // 老方法（下载合并用）行为不能被这次改动带偏：
        // 有普通 AAC 时选 AAC 最高码率
        DashData withAac = parse("{\"audio\":["
                + "{\"id\":30216,\"baseUrl\":\"https://a/aac.m4s\",\"bandwidth\":67000,"
                + "\"mimeType\":\"audio/mp4\",\"codecs\":\"mp4a.40.5\"}],"
                + "\"dolby\":{\"type\":1,\"audio\":{\"id\":30250,\"bandwidth\":999999,"
                + "\"mimeType\":\"audio/mp4\",\"codecs\":\"ec-3\"}}}");
        assertEquals(30216, withAac.getBestAudioStream().id);

        // 完全没有普通音轨时才兜底杜比（MediaMuxer 兜底路径依赖这个行为）
        DashData onlyDolby = parse("{\"dolby\":{\"type\":1,\"audio\":{\"id\":30250,"
                + "\"bandwidth\":999999,\"mimeType\":\"audio/mp4\",\"codecs\":\"ec-3\"}}}");
        assertEquals(30250, onlyDolby.getBestAudioStream().id);
    }
}
