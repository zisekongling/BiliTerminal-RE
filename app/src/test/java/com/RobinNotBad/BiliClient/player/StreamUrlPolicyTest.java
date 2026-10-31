package com.RobinNotBad.BiliClient.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * DASH 流地址策略（StreamUrlPolicy）的单测。
 *
 * <p>背景：B 站一次会下发「主地址 + backup_url」，同一份流落在多个 CDN 上。
 * 国内的自建镜像最稳，Akamai/mountaintoys 这类海外边缘经常连不上或直接失败，
 * 所以「选错主地址」就是「一进 1080P 就没声音 / 一直转圈」。这里把排序规则钉死。
 */
public class StreamUrlPolicyTest {

    private static final String MIRROR_BD = "https://upos-sz-mirrorbd.bilivideo.com/a.m4s";
    private static final String MIRROR_ALI = "https://upos-sz-mirrorali.bilivideo.com/a.m4s";
    private static final String MIRROR_COS = "https://upos-sz-mirrorcos.bilivideo.com/a.m4s";
    private static final String MIRROR_KS = "https://upos-sz-mirrorks.bilivideo.com/a.m4s";
    private static final String MIRROR_BDA1 = "https://upos-sz-mirrorbda1.bilivideo.com/a.m4s";
    private static final String NORMAL_CDN = "https://upos-sz-mirror08c.bilivideo.com/a.m4s";
    private static final String OTHER_CDN = "https://cdn.example.com/a.m4s";
    private static final String OVERSEAS_EDGE = "https://upos-hz-mirrorakam.akamaized.net/a.m4s";
    private static final String MOUNTAINTOYS = "https://cn-hk-eq-01-01.mountaintoys.cn/a.m4s";

    @Test
    public void rank_mirrorHosts_first() {
        // 五个自建镜像标记都排 0
        assertEquals(0, StreamUrlPolicy.rank(MIRROR_BD));
        assertEquals(0, StreamUrlPolicy.rank(MIRROR_ALI));
        assertEquals(0, StreamUrlPolicy.rank(MIRROR_COS));
        assertEquals(0, StreamUrlPolicy.rank(MIRROR_KS));
        assertEquals(0, StreamUrlPolicy.rank(MIRROR_BDA1));
        // 主机名和完整 URL 两种形态都要认
        assertEquals(0, StreamUrlPolicy.rank("upos-sz-mirrorbd.bilivideo.com"));
        // 大小写不敏感
        assertEquals(0, StreamUrlPolicy.rank("HTTPS://UPOS-SZ-MIRRORBD.BILIVIDEO.COM/a.m4s"));
    }

    @Test
    public void rank_otherBilivideoHosts_second() {
        // 镜像标记之外、但仍以 bilivideo.com 结尾的节点排 1
        assertEquals(1, StreamUrlPolicy.rank(NORMAL_CDN));
        assertEquals(1, StreamUrlPolicy.rank("https://upos-sz-mirror08c.bilivideo.com"));
    }

    @Test
    public void rank_unknownHost_third() {
        assertEquals(2, StreamUrlPolicy.rank(OTHER_CDN));
        assertEquals(2, StreamUrlPolicy.rank("https://example.com/a.m4s"));
    }

    @Test
    public void rank_overseasEdge_last() {
        // Akamai / mountaintoys 是国内常见失败源，必须排到最后
        assertEquals(3, StreamUrlPolicy.rank(OVERSEAS_EDGE));
        assertEquals(3, StreamUrlPolicy.rank(MOUNTAINTOYS));
        assertEquals(3, StreamUrlPolicy.rank("https://x.akamaized.net/a.m4s"));
    }

    @Test
    public void rank_blankAndNull_unknown() {
        assertEquals(9, StreamUrlPolicy.rank(null));
        assertEquals(9, StreamUrlPolicy.rank(""));
        assertEquals(9, StreamUrlPolicy.rank("   "));
    }

    @Test
    public void rank_brokenUrl_doesNotThrow() {
        // URI 解析失败时降级成原始串 contains 判断，不能抛异常
        assertEquals(0, StreamUrlPolicy.rank("upos-sz-mirrorbd.bilivideo.com/a b.m4s"));
        assertEquals(3, StreamUrlPolicy.rank("::not a url:: akamaized ::"));
    }

    @Test
    public void selectUrl_prefersBestHost() {
        // 主地址是海外边缘、备用里有一个镜像：必须选镜像
        assertEquals(MIRROR_BD, StreamUrlPolicy.selectUrl(
                OVERSEAS_EDGE, Arrays.asList(NORMAL_CDN, MIRROR_BD)));
        // 主地址本来就好：保持主地址
        assertEquals(MIRROR_BD, StreamUrlPolicy.selectUrl(
                MIRROR_BD, Collections.singletonList(OTHER_CDN)));
    }

    @Test
    public void selectUrl_allEmpty_returnsNull() {
        assertNull(StreamUrlPolicy.selectUrl(null, null));
        assertNull(StreamUrlPolicy.selectUrl("", Collections.emptyList()));
        assertNull(StreamUrlPolicy.selectUrl("   ", Arrays.asList(null, "", "  ")));
    }

    @Test
    public void selectUrl_sameRank_keepsOriginalOrder() {
        String secondMirror = "https://upos-sz-mirrorali.bilivideo.com/a.m4s";
        // 同 rank 时主地址仍在前
        assertEquals(MIRROR_BD, StreamUrlPolicy.selectUrl(
                MIRROR_BD, Collections.singletonList(secondMirror)));
    }

    @Test
    public void orderedUrls_fullOrderAndDedupe() {
        List<String> ordered = StreamUrlPolicy.orderedUrls(
                OVERSEAS_EDGE, Arrays.asList(OTHER_CDN, NORMAL_CDN));
        // 海外边缘排最后，其它按 rank 升序
        assertEquals(Arrays.asList(NORMAL_CDN, OTHER_CDN, OVERSEAS_EDGE), ordered);
    }

    @Test
    public void orderedUrls_dropsBlankAndDuplicates() {
        List<String> ordered = StreamUrlPolicy.orderedUrls(
                MIRROR_BD, Arrays.asList(MIRROR_BD, "  " + MIRROR_BD + "  ", null, "", NORMAL_CDN));
        // 主地址与 backup 重复、首尾空格、null、空串都要收敛成两条
        assertEquals(Arrays.asList(MIRROR_BD, NORMAL_CDN), ordered);
    }

    @Test
    public void orderedUrls_allEmpty_returnsEmptyList() {
        assertEquals(Collections.emptyList(), StreamUrlPolicy.orderedUrls(null, null));
        assertEquals(Collections.emptyList(), StreamUrlPolicy.orderedUrls(" ", Arrays.asList((String) null)));
    }
}
