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

    // ---------- shouldConsumeAfterFramework（issue #21） ----------

    @Test
    fun 框架自己说处理了就吞事件() {
        // NestedScrollView / ScrollView 滚完返回 true，再冒泡给兜底就是「叠加」
        assertTrue(RotaryEventPolicy.shouldConsumeAfterFramework(frameworkHandled = true, frameworkMoved = true))
        assertTrue(RotaryEventPolicy.shouldConsumeAfterFramework(frameworkHandled = true, frameworkMoved = false))
    }

    @Test
    fun 框架滚了却说没处理时也要吞() {
        // RecyclerView.onGenericMotionEvent（androidx 1.3.2，RecyclerView.java:3985）恒 return false，
        // 只靠返回值判断会把「已经滚过」当成「没处理」，于是兜底再滚一遍 = 反馈里的叠加
        assertTrue(RotaryEventPolicy.shouldConsumeAfterFramework(frameworkHandled = false, frameworkMoved = true))
    }

    @Test
    fun 框架没动才轮到补位() {
        assertFalse(RotaryEventPolicy.shouldConsumeAfterFramework(frameworkHandled = false, frameworkMoved = false))
    }

    // ---------- topUpDelta（反馈 #132：系统优先模式下补足差额，而不是整段吞掉） ----------

    /** 反馈 #132 的实机参数：density 2.4（384dpi），64dp 的竖直滚动缩放值 = 153.6px。 */
    private val scrollFactor = 153.6f

    /** 反馈 #132 日志里的一格滚动量（`axisScroll=-0.0524`）。 */
    private val oneNotchRatio = -0.0524f

    @Test
    fun 框架一点没滚时整段由App给() {
        // 1789a853 的补位：AXIS_VSCROLL 的 ROM、或事件根本没沿焦点链到容器
        assertEquals(16, RotaryEventPolicy.topUpDelta(targetDy = 16, frameworkDelta = 0))
        assertEquals(-16, RotaryEventPolicy.topUpDelta(targetDy = -16, frameworkDelta = 0))
    }

    @Test
    fun 框架滚不够时只补差额() {
        // 反馈 #132 的两个算例（target = 16px，见下面 systemFirstTargetDelta 的用例）：
        // 详情页 NestedScrollView 用主题的 listPreferredItemHeight≈48dp → 框架只给 6px，补 10px；
        // 列表页 ScrollView/RecyclerView 用 config_verticalScrollFactor=64dp → 框架给 8px，补 8px
        assertEquals(10, RotaryEventPolicy.topUpDelta(targetDy = 16, frameworkDelta = 6))
        assertEquals(8, RotaryEventPolicy.topUpDelta(targetDy = 16, frameworkDelta = 8))
        assertEquals(-10, RotaryEventPolicy.topUpDelta(targetDy = -16, frameworkDelta = -6))
    }

    @Test
    fun 框架滚够了一分都不补() {
        // issue #21 那类「框架本来就滚得比 App 多」的机型：必须与改动前完全一致，绝不能叠加
        assertEquals(0, RotaryEventPolicy.topUpDelta(targetDy = 16, frameworkDelta = 16))
        assertEquals(0, RotaryEventPolicy.topUpDelta(targetDy = 16, frameworkDelta = 24))
        assertEquals(0, RotaryEventPolicy.topUpDelta(targetDy = -16, frameworkDelta = -40))
    }

    @Test
    fun 框架方向相反时按整段补() {
        // 框架把这一格吃掉时方向是反的（正在回弹/惯性）：若照符号相减会互相抵消，
        // 手感上等于丢了半格，所以按整段补
        assertEquals(16, RotaryEventPolicy.topUpDelta(targetDy = 16, frameworkDelta = -4))
        assertEquals(-16, RotaryEventPolicy.topUpDelta(targetDy = -16, frameworkDelta = 4))
    }

    @Test
    fun 目标为零时不补() {
        // 与 shouldConsume 同一语义：算出 0 就不该有动作
        assertEquals(0, RotaryEventPolicy.topUpDelta(targetDy = 0, frameworkDelta = 0))
        assertEquals(0, RotaryEventPolicy.topUpDelta(targetDy = 0, frameworkDelta = 8))
    }

    @Test
    fun 框架位移加补位恒等于目标() {
        // 「同一次手势不被算两次」的核心断言：框架滚的那份被如实抵扣掉
        val target = 16
        for (framework in listOf(0, 2, 6, 8, 16)) {
            assertEquals(target, framework + RotaryEventPolicy.topUpDelta(target, framework))
        }
    }

    // ---------- systemFirstTargetDelta（反馈 #132：目标口径 = 开启表冠适配时的距离） ----------

    @Test
    fun 系统优先的目标口径等于开启适配时的位移() {
        assertEquals(
            RotaryEventPolicy.pixelDelta(oneNotchRatio, scrollFactor, 1f),
            RotaryEventPolicy.systemFirstTargetDelta(oneNotchRatio, scrollFactor, 1f)
        )
        // -(-0.0524) × 153.6 × 2 = 16.1 → 16px，正是「开启表冠适配 + 灵敏度 1.0」每格的距离
        assertEquals(16, RotaryEventPolicy.systemFirstTargetDelta(oneNotchRatio, scrollFactor, 1f))
    }

    @Test
    fun 系统优先的目标口径仍然吃灵敏度() {
        // 目标只是「同一条换算 × 缩放系数」，倍率语义与 App 先接管一致
        assertEquals(8, RotaryEventPolicy.systemFirstTargetDelta(oneNotchRatio, scrollFactor, 0.5f))
        assertEquals(0, RotaryEventPolicy.systemFirstTargetDelta(oneNotchRatio, scrollFactor, 0f))
    }

    @Test
    fun 目标缩放系数默认为一() {
        // 真机微调的旋钮：1.0 = 与开启表冠适配一致。别在代码里随手改成手感猜测值
        assertEquals(1.0f, RotaryEventPolicy.SYSTEM_FIRST_TARGET_SCALE, 0f)
    }
}
