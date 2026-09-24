package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ViewCapabilityProbe] 的 JVM 单测。
 *
 * 不用 android.view.View（JVM 测试里是 stub，方法体为空），改用一个行为等价的替身类，
 * 覆盖三条路径：方法存在、方法不存在、以及「探测结论会被缓存」。
 */
class ViewCapabilityProbeTest {

    /** 目标替身：一个方法存在、一个方法不存在、一个方法会抛异常。 */
    class FakeView {
        var clickListenerCount = 0

        fun hasOnClickListeners(): Boolean = clickListenerCount > 0

        fun brokenBoolean(): Boolean = throw RuntimeException("框架方法自己炸了")

        fun throwError(): Boolean = throw AssertionError("由目标方法主动抛出的 Error")
    }

    @Before
    fun setUp() {
        // 探测缓存是进程级共享的，用例之间必须重置，否则互相污染
        ViewCapabilityProbe.resetForTest()
    }

    @Test
    fun probeBoolean_methodExists_returnsValueAndCaches() {
        val view = FakeView()

        // 第一次：走反射，读到的就是真实值
        assertEquals(false, ViewCapabilityProbe.probeBoolean(view, "hasOnClickListeners"))

        // 改变真实状态，探测应命中缓存并拿到新值（缓存的是「方法可用」，不是返回值）
        view.clickListenerCount = 1
        assertEquals(true, ViewCapabilityProbe.probeBoolean(view, "hasOnClickListeners"))
    }

    @Test
    fun probeBoolean_methodMissing_returnsNullAndReportsOnce() {
        val view = FakeView()
        val failures = mutableListOf<String>()

        val first = ViewCapabilityProbe.probeBoolean(view, "hasOnLongClickListeners") { name, _ ->
            failures.add(name)
        }
        assertNull("本机没有的方法应返回 null 让调用方降级", first)
        assertEquals(1, failures.size)
        assertEquals("hasOnLongClickListeners", failures[0])

        // 第二次不再重复上报（缓存已判定为不可用），返回值仍然让调用方降级
        val second = ViewCapabilityProbe.probeBoolean(view, "hasOnLongClickListeners") { name, _ ->
            failures.add(name)
        }
        assertNull(second)
        assertEquals("探测失败只应上报一次", 1, failures.size)
    }

    @Test
    fun probeBoolean_targetThrowsRuntimeException_degradesToNull() {
        val view = FakeView()
        val failures = mutableListOf<String>()

        // 反射会把目标方法抛的异常包成 InvocationTargetException，这里必须拆包后仍走降级，
        // 而不是把包装异常漏给调用方（漏出去一样是崩）
        assertNull(ViewCapabilityProbe.probeBoolean(view, "brokenBoolean") { name, _ -> failures.add(name) })
        assertEquals(1, failures.size)

        // 第二次走「缓存判定不可用」这条路，行为必须一致：仍然是 null 而不是把异常漏出去
        assertNull(ViewCapabilityProbe.probeBoolean(view, "brokenBoolean") { name, _ -> failures.add(name) })
    }

    @Test
    fun probeBoolean_targetThrowsError_propagatesNotSwallowed() {
        // 目标方法自己抛的 Error 要原样冒出来（说明是代码缺陷），不能被降级逻辑静默吞掉
        var escaped = false
        try {
            ViewCapabilityProbe.probeBoolean(FakeView(), "throwError")
        } catch (e: AssertionError) {
            escaped = true
        }
        assertTrue("目标方法自己抛的 Error 不该被静默吞掉（避免掩盖真实缺陷）", escaped)
    }
}
