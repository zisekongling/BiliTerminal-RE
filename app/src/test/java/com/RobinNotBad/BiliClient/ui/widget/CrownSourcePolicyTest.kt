package com.RobinNotBad.BiliClient.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CrownSourcePolicy] 的纯 JVM 单测（反馈 #197「OPPO Watch 4 Pro 在视频详情页简介部分，表冠无法
 * 精确控制滑动」）。
 *
 * 数值全写字面量，来源标在注释上，与 [RotaryEventPolicyTest] 同一套做法——这层不碰 android.jar，
 * 也就不用 Robolectric。
 */
class CrownSourcePolicyTest {

    /** `MotionEvent.ACTION_SCROLL`。 */
    private val actionScroll = 8

    /** `MotionEvent.ACTION_MOVE`。 */
    private val actionMove = 2

    /** `InputDevice.SOURCE_ROTARY_ENCODER`（0x00400000 | SOURCE_CLASS_NONE）。 */
    private val sourceRotary = 0x00400000

    /** `InputDevice.SOURCE_TOUCHSCREEN`（0x00001002，pointer 类）。 */
    private val sourceTouchscreen = 0x1002

    /** `InputDevice.SOURCE_MOUSE`（0x00002002，pointer 类）——ROM 把表冠报成滚轮时就是它（#197 日志 src=0x2002）。 */
    private val sourceMouse = 0x2002

    /** `InputDevice.SOURCE_MOUSE_RELATIVE`（0x00020002，pointer 类）——注意与上一条差一位，别写混。 */
    private val sourceMouseRelative = 0x00020002

    // ---------- kindOf：认不认得出这一格 ----------

    @Test
    fun 编码器来源判成编码器() {
        assertEquals(CrownSourcePolicy.ENCODER, CrownSourcePolicy.kindOf(actionScroll, sourceRotary))
    }

    @Test
    fun 鼠标滚轮来源判成滚轮() {
        // #197 的现场：action 仍是 ACTION_SCROLL，source 却是 SOURCE_MOUSE
        assertEquals(CrownSourcePolicy.WHEEL, CrownSourcePolicy.kindOf(actionScroll, sourceMouse))
    }

    @Test
    fun 相对位移鼠标也判成滚轮() {
        // SOURCE_MOUSE 是 0x00002002、SOURCE_MOUSE_RELATIVE 是 0x00020002，差一位；两种都要认
        assertEquals(
            CrownSourcePolicy.WHEEL,
            CrownSourcePolicy.kindOf(actionScroll, sourceMouseRelative)
        )
    }

    @Test
    fun 触屏来源不判成表冠() {
        // 触屏拖拽的 ACTION_SCROLL 不是表冠；0x1002 与两个鼠标掩码相与都只剩 0x2，不该被认成滚轮
        assertEquals(CrownSourcePolicy.NONE, CrownSourcePolicy.kindOf(actionScroll, sourceTouchscreen))
    }

    @Test
    fun 没有来源位时不判成表冠() {
        assertEquals(CrownSourcePolicy.NONE, CrownSourcePolicy.kindOf(actionScroll, 0))
    }

    @Test
    fun 动作不是滚动时不判成表冠() {
        assertEquals(CrownSourcePolicy.NONE, CrownSourcePolicy.kindOf(actionMove, sourceRotary))
        assertEquals(CrownSourcePolicy.NONE, CrownSourcePolicy.kindOf(actionMove, sourceMouse))
    }

    @Test
    fun 同时带表冠位与指针位时按编码器算() {
        // 编码器优先：真实编码器即使叠了 SOURCE_CLASS_POINTER 也走每格 ×2 的老口径，不让手感回退
        assertEquals(
            CrownSourcePolicy.ENCODER,
            CrownSourcePolicy.kindOf(actionScroll, sourceRotary or 0x0002)
        )
    }

    // ---------- isCoarseWheelStep：这一格够不够粗 ----------

    @Test
    fun 整格滚轮量算粗粒度() {
        // 实测值：#197 日志里是 AXIS_VSCROLL = ±1.0，部分 ROM 报 ±16.0
        assertTrue(CrownSourcePolicy.isCoarseWheelStep(1.0f))
        assertTrue(CrownSourcePolicy.isCoarseWheelStep(-1.0f))
        assertTrue(CrownSourcePolicy.isCoarseWheelStep(16.0f))
        assertTrue(CrownSourcePolicy.isCoarseWheelStep(-16.0f))
    }

    @Test
    fun 连续小量不算粗粒度() {
        // 触控板/精细滚轮给的是连续小量，框架自己换算更顺手，不该被抢
        assertFalse(CrownSourcePolicy.isCoarseWheelStep(0.1f))
        assertFalse(CrownSourcePolicy.isCoarseWheelStep(-0.5f))
        assertFalse(CrownSourcePolicy.isCoarseWheelStep(0f))
    }

    // ---------- wheelScrollDelta：保留量级，阅读容器封顶 ----------

    @Test
    fun 一格滚轮按一倍滚动系数换算() {
        // factor 50、灵敏度 1.0：一格就是 50px。方向用滚动的语义（内容上移 = 负值），
        // 与 RotaryEventPolicy.pixelDelta 的符号约定保持一致。
        assertEquals(
            -50,
            CrownSourcePolicy.wheelScrollDelta(1f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST)
        )
        assertEquals(
            50,
            CrownSourcePolicy.wheelScrollDelta(-1f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST)
        )
    }

    @Test
    fun 快转的整格量按量级放大但单事件封顶两格() {
        // #234 的反面教训：不能把量级收成 ±1（beta.1 就这么干，1px 基准下等于不动）。
        // 但 26.11.18-beta.5 起上限只有 2 格（#235「一滑到底」）：基准修回 128px 后，
        // 一个 -32.0 的事件若原样放大就是 4096px，一次跳满屏。所以 16 格 → 2 格 = 100px。
        assertEquals(
            -100,
            CrownSourcePolicy.wheelScrollDelta(16f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST)
        )
        assertEquals(
            100,
            CrownSourcePolicy.wheelScrollDelta(-16f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST)
        )
        // 量级在「一格以内」仍原样保留：两格以内不吃封顶
        assertEquals(
            -100,
            CrownSourcePolicy.wheelScrollDelta(2f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST)
        )
    }

    @Test
    fun 阅读容器把单次滚轮量封到一格() {
        // #197：简介/消息是 NestedScrollView，一行 ≈ 48dp；基准修好后一格 128px 已接近 1.4 行，
        // 再多乘就是「转一格跳三行」（beta.5 从 2 格收到 1 格）
        assertEquals(
            -50,
            CrownSourcePolicy.wheelScrollDelta(16f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_READING)
        )
        assertEquals(
            50,
            CrownSourcePolicy.wheelScrollDelta(-16f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_READING)
        )
    }

    @Test
    fun 封顶不影响慢转的一格() {
        // 慢转一格本来就只有 1 格，封顶不该改变它的手感
        assertEquals(
            -50,
            CrownSourcePolicy.wheelScrollDelta(1f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_READING)
        )
    }

    @Test
    fun 滚轮一格是编码器一格的一半() {
        // 守住「不乘 2」这个决定：×2 是给编码器一格定的手感基准，滚轮一格对齐框架口径（factor × 1），
        // 否则 #197 用户对照「其他地方都正常」的参照物就不成立了。
        val factor = 50f
        val encoder = RotaryEventPolicy.pixelDelta(1f, factor, 1f)
        val wheel = CrownSourcePolicy.wheelScrollDelta(1f, factor, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST)
        assertEquals(encoder / 2, wheel)
    }

    @Test
    fun 细量按比例换算而不是被当成整格() {
        // 触控板式 / 部分 ROM 的连续小量（|量| < 0.9）。26.11.18-beta.3 起这类量也由 App
        // 换算（以前整段交回框架，而在把表冠报成鼠标的 ROM 上框架根本不换算轴值）。
        assertEquals(
            -10,
            CrownSourcePolicy.wheelScrollDelta(0.2f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST)
        )
        assertEquals(
            -10,
            CrownSourcePolicy.wheelScrollDelta(0.2f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_READING)
        )
    }

    @Test
    fun 细量乘灵敏度按比例放大且不在这里兜灵敏度() {
        // 守住「公式对细量与整格量是同一套」，免得下次为了「动不了」在别处偷偷加系数。
        // 灵敏度本身由 RotaryEventPolicy.clampSensitivity 在读取时收进 0.2–8，
        // #234 那种填到 700 的值不会走到这里，所以上限 8 时是 0.2 × 50 × 8 = 80px。
        assertEquals(
            -80,
            CrownSourcePolicy.wheelScrollDelta(0.2f, 50f, 8f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST)
        )
    }

    @Test
    fun 细量不足半像素时算成零() {
        // 算成 0 不是错误：shouldConsume 会因此把事件交回框架，不至于「吃掉却不滚」。
        assertEquals(
            0,
            CrownSourcePolicy.wheelScrollDelta(0.01f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST)
        )
    }

    @Test
    fun 方向为零或灵敏度为零时不产生位移() {
        assertEquals(0, CrownSourcePolicy.wheelScrollDelta(0f, 50f, 1f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST))
        assertEquals(0, CrownSourcePolicy.wheelScrollDelta(1f, 50f, 0f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST))
        assertEquals(0, CrownSourcePolicy.wheelScrollDelta(1f, 50f, -1f, CrownSourcePolicy.WHEEL_MAX_STEPS_LIST))
        assertEquals(0, CrownSourcePolicy.wheelScrollDelta(1f, 50f, 1f, 0f))
    }
}
