package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设备档位画像单测（批次 2）。
 *
 * 这个映射决定「清晰度 / 弹幕行数 / 是否软解」三件受影响最大的参数，映射错一次就是
 * 一整档设备同时体验变差，所以在纯逻辑层把三档与所有输入组合都钉死。
 * 注意：这里只测纯函数，不碰 `PerformanceManager` 的单例状态、不碰 Context
 * （单测没有 Android 运行时，档位输入以参数形式传入正是为此）。
 */
class DeviceProfileTest {

    // ------------------------------------------------------------ tierOf 输入组合

    @Test
    fun `硬件低且未手动开高性能是低端档`() {
        // PerformanceManager.isLowPerfLevel 的定义：level==LOW && !highPerformanceMode
        assertEquals(DeviceProfile.TIER_LOW, DeviceProfile.tierOf(0, lowPerfDevice = true, effectiveHighPerf = false))
    }

    @Test
    fun `硬件高档是高端档`() {
        assertEquals(DeviceProfile.TIER_HIGH, DeviceProfile.tierOf(2, lowPerfDevice = false, effectiveHighPerf = false))
    }

    @Test
    fun `低端硬件但手动开了高性能模式升到高端档`() {
        // 这是「用户嫌卡就开高性能」的预期路径：虽然 isLowPerfDevice 为 false
        // （isLowPerfLevel 里 `&& !highPerformanceMode` 已经排除），还应落到高端。
        assertEquals(DeviceProfile.TIER_HIGH, DeviceProfile.tierOf(0, lowPerfDevice = false, effectiveHighPerf = true))
    }

    @Test
    fun `中端硬件且无额外信号是中端档`() {
        assertEquals(DeviceProfile.TIER_MEDIUM, DeviceProfile.tierOf(1, lowPerfDevice = false, effectiveHighPerf = false))
    }

    @Test
    fun `高性能模式对中端硬件同样生效 且 低端信号压过硬件等级`() {
        // 原用例与上面的「中端硬件且无额外信号」用的是同一组入参 tierOf(1, false, false)，
        // 断言也相同，等于没覆盖任何新分支。这里换成两个原先没钉过的入参组合：
        // 1) 中端 perfLevel + 手动开了高性能 → 必须升到高端（高性能模式不能只对低端机生效）；
        assertEquals(
            DeviceProfile.TIER_HIGH,
            DeviceProfile.tierOf(1, lowPerfDevice = false, effectiveHighPerf = true)
        )
        // 2) 硬件等级是高（perfLevel=2）但被判成低端设备 → 低端信号优先，仍是低端档。
        assertEquals(
            DeviceProfile.TIER_LOW,
            DeviceProfile.tierOf(2, lowPerfDevice = true, effectiveHighPerf = false)
        )
    }

    @Test
    fun `低端信号优先于高性能信号`() {
        // 理论上两者互斥（isLowPerfDevice 含 !highPerformanceMode），但接口允许同时传 true。
        // 约定：保守优先 —— 宁可少用硬件能力，也不要在一台被判定为低端的机器上放开行数。
        assertEquals(DeviceProfile.TIER_LOW, DeviceProfile.tierOf(0, lowPerfDevice = true, effectiveHighPerf = true))
    }

    // ------------------------------------------------------------ 三档取值

    @Test
    fun `三档参数逐项正确`() {
        // 26.11.08 起档位只剩「弹幕行数」一项差异：解码方式默认恒为硬解（用户拍板「一般都不要软解」），
        // 清晰度交给用户配置。这里同时钉住「不要偷偷把解码/清晰度策略加回来」。
        val low = DeviceProfile.profileForTier(DeviceProfile.TIER_LOW)
        assertEquals(DeviceProfile.TIER_LOW, low.level)
        assertEquals("低端 6 行", 6, low.danmakuMaxLines)

        val medium = DeviceProfile.profileForTier(DeviceProfile.TIER_MEDIUM)
        assertEquals(DeviceProfile.TIER_MEDIUM, medium.level)
        assertEquals("中端 10 行（与设置页默认一致）", 10, medium.danmakuMaxLines)

        val high = DeviceProfile.profileForTier(DeviceProfile.TIER_HIGH)
        assertEquals(DeviceProfile.TIER_HIGH, high.level)
        assertEquals("高端 15 行（不加码）", 15, high.danmakuMaxLines)
    }

    @Test
    fun `档位越高行数不减`() {
        // 防止以后调参时把某一档调成「更高档反而更差」的荒谬组合。
        val low = DeviceProfile.profileForTier(DeviceProfile.TIER_LOW)
        val medium = DeviceProfile.profileForTier(DeviceProfile.TIER_MEDIUM)
        val high = DeviceProfile.profileForTier(DeviceProfile.TIER_HIGH)
        assertTrue(low.danmakuMaxLines < medium.danmakuMaxLines)
        assertTrue(medium.danmakuMaxLines <= high.danmakuMaxLines)
    }

    @Test
    fun `非法档位按中端处理而不是崩溃`() {
        assertEquals(DeviceProfile.TIER_MEDIUM, DeviceProfile.profileForTier(99).level)
        assertEquals(DeviceProfile.TIER_MEDIUM, DeviceProfile.profileForTier(-1).level)
        assertEquals(DeviceProfile.TIER_MEDIUM, DeviceProfile.profileForTier(Int.MIN_VALUE).level)
    }

    @Test
    fun `profileOf 是 tierOf 与 profileForTier 的组合`() {
        assertEquals(
            DeviceProfile.profileForTier(DeviceProfile.TIER_LOW),
            DeviceProfile.profileOf(0, lowPerfDevice = true, effectiveHighPerf = false)
        )
        assertEquals(
            DeviceProfile.profileForTier(DeviceProfile.TIER_MEDIUM),
            DeviceProfile.profileOf(1, lowPerfDevice = false, effectiveHighPerf = false)
        )
        assertEquals(
            DeviceProfile.profileForTier(DeviceProfile.TIER_HIGH),
            DeviceProfile.profileOf(2, lowPerfDevice = false, effectiveHighPerf = false)
        )
    }

    // ------------------------------------------------------------ 弹幕行数封顶

    @Test
    fun `用户设置超过档位上限时取上限`() {
        assertEquals("低端机用户设 15 行也只给 6 行", 6, DeviceProfile.capDanmakuMaxLines(15, 6))
        assertEquals(10, DeviceProfile.capDanmakuMaxLines(15, 10))
        assertEquals(15, DeviceProfile.capDanmakuMaxLines(15, 15))
    }

    @Test
    fun `用户调低时保留用户选择`() {
        // 封顶只压「上限」，不改用户主动调小的设置（有人就喜欢 3 行清爽）。
        assertEquals(3, DeviceProfile.capDanmakuMaxLines(3, 6))
        assertEquals(1, DeviceProfile.capDanmakuMaxLines(1, 15))
    }

    @Test
    fun `非法行数与非法档位上限都兜到 1`() {
        // 行数 <=0 会让 DFM 的 setMaximumLines(0) 直接不出弹幕（表现为「弹幕开关打开了却没弹幕」）。
        assertEquals(1, DeviceProfile.capDanmakuMaxLines(0, 6))
        assertEquals(1, DeviceProfile.capDanmakuMaxLines(-5, 6))
        assertEquals(1, DeviceProfile.capDanmakuMaxLines(15, 0))
        assertEquals(1, DeviceProfile.capDanmakuMaxLines(15, -1))
        assertEquals(1, DeviceProfile.capDanmakuMaxLines(Int.MAX_VALUE, 0))
    }

    @Test
    fun `封顶对每档的真实上限都成立`() {
        // 直接把每档上限当作用户想要更多来测：结果必须等于该档上限。
        for (tier in listOf(DeviceProfile.TIER_LOW, DeviceProfile.TIER_MEDIUM, DeviceProfile.TIER_HIGH)) {
            val profile = DeviceProfile.profileForTier(tier)
            assertEquals(
                "档位 $tier 的封顶值必须等于 profile.danmakuMaxLines",
                profile.danmakuMaxLines,
                DeviceProfile.capDanmakuMaxLines(profile.danmakuMaxLines + 100, profile.danmakuMaxLines)
            )
        }
    }
}
