package com.RobinNotBad.BiliClient.api;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code FeedbackApi.buildForm} 的表单字段测试。
 *
 * <p>只测纯函数，<b>不发网络请求</b>：dislike / cancelDislike 需要 Cookie + csrf，
 * 纯 JVM 下既拿不到也不该发；真正「服务端接不接受这组参数」只能在真机上验证。
 */
public class FeedbackApiTest {

    @Test
    public void buildForm_字段齐全() {
        Map<String, String> form = FeedbackApi.buildForm(114514L, 12345L, "track_abc-123", FeedbackApi.REASON_CONTENT);

        // 10 个业务字段都必须下发（缺一个都可能被判参数不合法）
        assertEquals(10, form.size());
        for (String key : new String[]{"goto", "id", "mid", "track_id", "reason_id",
                "app_id", "platform", "feedback_page", "from_spmid", "spmid"}) {
            assertTrue("缺少字段：" + key, form.containsKey(key));
        }
        // csrf 属于凭据，不在纯函数里拼，由网络方法单独追加
        assertFalse("csrf 不应出现在 buildForm 里", form.containsKey("csrf"));
    }

    @Test
    public void buildForm_id传的是aid不是bvid() {
        Map<String, String> form = FeedbackApi.buildForm(170001L, 0L, "", FeedbackApi.REASON_CONTENT);
        assertEquals("170001", form.get("id"));
        assertEquals("goto 固定 av（按 aid 定位）", "av", form.get("goto"));
    }

    @Test
    public void buildForm_reasonId用传入值() {
        // 1=对内容不感兴趣
        assertEquals("1", FeedbackApi.buildForm(1L, 2L, "", FeedbackApi.REASON_CONTENT).get("reason_id"));
        // 4=不想看此 UP 主
        assertEquals("4", FeedbackApi.buildForm(1L, 2L, "", FeedbackApi.REASON_UP).get("reason_id"));
        // 常量本身的值也是契约（调用方按 1/4 写死）
        assertEquals(1, FeedbackApi.REASON_CONTENT);
        assertEquals(4, FeedbackApi.REASON_UP);
    }

    @Test
    public void buildForm_trackId为null时按空串() {
        assertEquals("", FeedbackApi.buildForm(1L, 2L, null, FeedbackApi.REASON_CONTENT).get("track_id"));
        // 传入非空值时原样保留（编码交给 FormData）
        assertEquals("tm_track-1", FeedbackApi.buildForm(1L, 2L, "tm_track-1", FeedbackApi.REASON_CONTENT).get("track_id"));
    }

    @Test
    public void buildForm_固定埋点常量() {
        Map<String, String> form = FeedbackApi.buildForm(1L, 2L, "", FeedbackApi.REASON_CONTENT);
        assertEquals("app_id", "100", form.get("app_id"));
        assertEquals("platform", "5", form.get("platform"));
        assertEquals("feedback_page", "1", form.get("feedback_page"));
        assertEquals("from_spmid 固定空串", "", form.get("from_spmid"));
        assertEquals("spmid", "333.1007.0.0", form.get("spmid"));
    }

    @Test
    public void buildForm_mid按UP主mid下发() {
        assertEquals("12345", FeedbackApi.buildForm(1L, 12345L, "", FeedbackApi.REASON_CONTENT).get("mid"));
        // UP 主 mid 未知时下发 0（服务端按 aid 定位，不按 mid 过滤）
        assertEquals("0", FeedbackApi.buildForm(1L, 0L, "", FeedbackApi.REASON_CONTENT).get("mid"));
    }
}
