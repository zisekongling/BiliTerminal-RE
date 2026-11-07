package com.RobinNotBad.BiliClient.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RotaryEventPolicy] 的纯 JVM 单测（反馈 #103/#107「视频详情-简介里表冠不适配」）。
 *
 * 为了让这层完全不依赖 android.jar，测试里的数值都写成字面量，来源标在注释上：
 * 框架侧事实见 `View.dispatchGenericMotionEvent`（只有 pointer 类才按坐标命中控件）与
 * `InputDevice.SOURCE_ROTARY_ENCODER == 0x00400000 | SOURCE_CLASS_NONE`。
 */
class RotaryEventPolicyTest {

    /** `MotionEvent.ACTION_SCROLL`。 */
    private val actionScroll = 8

    /** `MotionEvent.ACTION_MOVE`。 */
    private val actionMove = 2

    /** `InputDevice.SOURCE_ROTARY_ENCODER`（0x00400000 | SOURCE_CLASS_NONE）。 */
    private val sourceRotary = 0x00400000

    /** `InputDevice.SOURCE_TOUCHSCREEN`（pointer 类）。 */
    private val sourceTouchscreen = 0x1002

    /** `InputDevice.SOURCE_MOUSE`（pointer 类）——ROM 把表冠报成滚轮时就是它。 */
    private val sourceMouse = 0x2002

    // ---------- isRotaryEvent ----------

    @Test
    fun 表冠源加滚动动作才算表冠事件() {
        assertTrue(RotaryEventPolicy.isRotaryEvent(actionScroll, sourceRotary))
    }

    @Test
    fun 表冠源但动作不是滚动时不算() {
        assertFalse(RotaryEventPolicy.isRotaryEvent(actionMove, sourceRotary))
    }

    @Test
    fun 指针类来源不算表冠事件() {
        // 触屏拖拽与鼠标滚轮都会发 ACTION_SCROLL，但都不是表冠：不能走表冠的滚动换算
        assertFalse(RotaryEventPolicy.isRotaryEvent(actionScroll, sourceTouchscreen))
        assertFalse(RotaryEventPolicy.isRotaryEvent(actionScroll, sourceMouse))
    }

    @Test
    fun 表冠源叠加指针类时仍算表冠事件() {
        // source 是位或：某些输入设备会同时带 SOURCE_CLASS_POINTER 与表冠位
        assertTrue(RotaryEventPolicy.isRotaryEvent(actionScroll, sourceRotary or 0x0002))
    }

    @Test
    fun 滚动事件判定不看来源() {
        assertTrue(RotaryEventPolicy.isScrollEvent(actionScroll))
        assertFalse(RotaryEventPolicy.isScrollEvent(actionMove))
    }

    // ---------- rawScrollRatio ----------

    @Test
    fun 优先取AXIS_SCROLL() {
        assertEquals(-1f, RotaryEventPolicy.rawScrollRatio(-1f, 0f), 0f)
        assertEquals(2f, RotaryEventPolicy.rawScrollRatio(2f, -3f), 0f)
    }

    @Test
    fun AXIS_SCROLL为零时回落AXIS_VSCROLL() {
        // 把滚动量放在 VSCROLL 上的 ROM/滚轮：回落前恒为 0，回落才有位移
        assertEquals(-1f, RotaryEventPolicy.rawScrollRatio(0f, -1f), 0f)
    }

    @Test
    fun 两轴都为零时没有位移() {
        assertEquals(0f, RotaryEventPolicy.rawScrollRatio(0f, 0f), 0f)
    }

    // ---------- pixelDelta ----------

    @Test
    fun 像素位移按灵敏度线性放大() {
        assertEquals(2, RotaryEventPolicy.pixelDelta(-1f, 1f, 1f))
        assertEquals(-2, RotaryEventPolicy.pixelDelta(1f, 1f, 1f))
        assertEquals(1, RotaryEventPolicy.pixelDelta(-1f, 1f, 0.5f))
        assertEquals(4, RotaryEventPolicy.pixelDelta(-1f, 1f, 2f))
    }

    @Test
    fun 滚动因子参与换算() {
        assertEquals(1, RotaryEventPolicy.pixelDelta(-1f, 0.5f, 1f))
        // 0.2：-(-1)×0.2×2 = 0.4 → 四舍五入 0，即「滚了但不足 1 像素」→ 不消费事件
        assertEquals(0, RotaryEventPolicy.pixelDelta(-1f, 0.2f, 1f))
    }

    @Test
    fun 灵敏度为零时位移为零() {
        assertEquals(0, RotaryEventPolicy.pixelDelta(-1f, 1f, 0f))
    }

    // ---------- shouldConsume ----------

    @Test
    fun 位移为零时不消费事件() {
        // 关键回归点：26.11.07 之前控件级 handle() 在 dy==0 时仍 return true，
        // 事件被吞、画面不动，父级与 DecorView 兜底接不到 —— 与「表冠不适配」同现象
        assertFalse(RotaryEventPolicy.shouldConsume(0))
    }

    @Test
    fun 位移非零时消费事件() {
        assertTrue(RotaryEventPolicy.shouldConsume(2))
        assertTrue(RotaryEventPolicy.shouldConsume(-3))
    }
}
