package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E2「强制为滚动弹幕」的纯判据单测。
 *
 * 这个开关此前是全仓无消费方的死开关：设置项能点、值能存，但打开后顶部/底部弹幕照样固定。
 * 这里钉死「只转换顶部/底部、不动高级弹幕与滚动弹幕」，避免实现时顺手把所有弹幕都改了。
 */
class DanmakuStylePolicyTest {

    @Test
    fun `顶部与底部固定弹幕算固定类型`() {
        assertTrue(DanmakuStylePolicy.isFixedType(DanmakuStylePolicy.TYPE_FIX_TOP))
        assertTrue(DanmakuStylePolicy.isFixedType(DanmakuStylePolicy.TYPE_FIX_BOTTOM))
        assertFalse(DanmakuStylePolicy.isFixedType(DanmakuStylePolicy.TYPE_SCROLL_RL))
        assertFalse(DanmakuStylePolicy.isFixedType(DanmakuStylePolicy.TYPE_SCROLL_LR))
        assertFalse(DanmakuStylePolicy.isFixedType(DanmakuStylePolicy.TYPE_SPECIAL))
    }

    @Test
    fun `关闭开关时任何类型都原样返回`() {
        val types = listOf(
            DanmakuStylePolicy.TYPE_SCROLL_RL,
            DanmakuStylePolicy.TYPE_FIX_TOP,
            DanmakuStylePolicy.TYPE_FIX_BOTTOM,
            DanmakuStylePolicy.TYPE_SPECIAL
        )
        for (type in types) {
            assertFalse(DanmakuStylePolicy.shouldForceScroll(false, type))
            assertEquals(type, DanmakuStylePolicy.effectiveType(false, type))
        }
    }

    @Test
    fun `开启开关时顶部底部转成滚动`() {
        assertTrue(DanmakuStylePolicy.shouldForceScroll(true, DanmakuStylePolicy.TYPE_FIX_TOP))
        assertTrue(DanmakuStylePolicy.shouldForceScroll(true, DanmakuStylePolicy.TYPE_FIX_BOTTOM))
        assertEquals(
            DanmakuStylePolicy.TYPE_SCROLL_RL,
            DanmakuStylePolicy.effectiveType(true, DanmakuStylePolicy.TYPE_FIX_TOP)
        )
        assertEquals(
            DanmakuStylePolicy.TYPE_SCROLL_RL,
            DanmakuStylePolicy.effectiveType(true, DanmakuStylePolicy.TYPE_FIX_BOTTOM)
        )
    }

    @Test
    fun `开启开关时滚动弹幕与高级弹幕保持不变`() {
        assertFalse(DanmakuStylePolicy.shouldForceScroll(true, DanmakuStylePolicy.TYPE_SCROLL_RL))
        assertFalse(DanmakuStylePolicy.shouldForceScroll(true, DanmakuStylePolicy.TYPE_SCROLL_LR))
        assertFalse(DanmakuStylePolicy.shouldForceScroll(true, DanmakuStylePolicy.TYPE_SPECIAL))
        assertEquals(
            DanmakuStylePolicy.TYPE_SPECIAL,
            DanmakuStylePolicy.effectiveType(true, DanmakuStylePolicy.TYPE_SPECIAL)
        )
        assertEquals(
            DanmakuStylePolicy.TYPE_SCROLL_LR,
            DanmakuStylePolicy.effectiveType(true, DanmakuStylePolicy.TYPE_SCROLL_LR)
        )
    }
}
