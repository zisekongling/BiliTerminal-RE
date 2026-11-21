package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 短视频弹幕档位的纯函数部分。
 *
 * 读档那几个函数要 SharedPreferences，只能在设备/仪器测试里跑；这里只钉住
 * 「用户填的离谱值会被钳回合法区间」这一条——它是唯一有分支的纯逻辑。
 */
class ShortVideoDanmakuPolicyTest {

    @Test
    fun clampLines_keepsLegalValue() {
        assertEquals(6, ShortVideoDanmakuPolicy.clampLines(6))
        assertEquals(ShortVideoDanmakuPolicy.MIN_MAX_LINES, ShortVideoDanmakuPolicy.clampLines(1))
        assertEquals(ShortVideoDanmakuPolicy.MAX_MAX_LINES, ShortVideoDanmakuPolicy.clampLines(30))
    }

    @Test
    fun clampLines_clampsOutOfRange() {
        // 设置项输入框允许填 0 / 负数 / 999，引擎侧必须收得住，否则 DFM 会吃 null 行数表
        assertEquals(ShortVideoDanmakuPolicy.MIN_MAX_LINES, ShortVideoDanmakuPolicy.clampLines(0))
        assertEquals(ShortVideoDanmakuPolicy.MIN_MAX_LINES, ShortVideoDanmakuPolicy.clampLines(-3))
        assertEquals(ShortVideoDanmakuPolicy.MAX_MAX_LINES, ShortVideoDanmakuPolicy.clampLines(999))
    }

    @Test
    fun defaults_areConservative() {
        // 手表小屏：默认 6 行、不重叠（与设置页文案里写的数字一致）
        assertEquals(6, ShortVideoDanmakuPolicy.DEFAULT_MAX_LINES)
        assertEquals(false, ShortVideoDanmakuPolicy.DEFAULT_ALLOW_OVERLAP)
    }
}
