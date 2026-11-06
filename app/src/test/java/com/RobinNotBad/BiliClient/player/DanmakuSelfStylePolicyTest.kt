package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「本人弹幕边框」纯判定逻辑的单元测试（批次 3）。
 *
 * 重点覆盖三件事：
 * 1. **CRC32 口径**：B 站下发的发送者 hash 是 mid（十进制字符串）的 CRC-32，算错就全盘失效；
 * 2. **归一化**：XML 侧带前导零、大小写不定，protobuf 侧小写无前导零 —— 必须都能命中，
 *    同时空串/非十六进制/超长不能被误判成「自己的」；
 * 3. **边界**：未登录（mid <= 0）、开关关闭、别人弹幕都必须返回 false。
 */
class DanmakuSelfStylePolicyTest {

    // ---------- CRC32 计算 ----------

    /**
     * 手工算好的向量（python zlib.crc32，与 java.util.zip.CRC32 同口径）。
     * 这些值一旦对不上，说明「十进制字符串逐字符喂 CRC32」这个算法被改错了。
     */
    @Test
    fun `crc32 与参考向量一致`() {
        assertEquals("mid=1", 2212294583L, DanmakuSelfStylePolicy.crc32Of(1L))
        assertEquals("mid=2", 450215437L, DanmakuSelfStylePolicy.crc32Of(2L))
        assertEquals("mid=1234", 2615402659L, DanmakuSelfStylePolicy.crc32Of(1234L))
        assertEquals("mid=12345678", 2598427311L, DanmakuSelfStylePolicy.crc32Of(12345678L))
        assertEquals("mid=987654321", 23003649L, DanmakuSelfStylePolicy.crc32Of(987654321L))
        assertEquals("mid=10086", 1443254904L, DanmakuSelfStylePolicy.crc32Of(10086L))
    }

    @Test
    fun `非法 mid 的 crc32 为 0`() {
        assertEquals("mid=0 表示未登录", 0L, DanmakuSelfStylePolicy.crc32Of(0L))
        assertEquals("负数也要当成未登录", 0L, DanmakuSelfStylePolicy.crc32Of(-5L))
    }

    @Test
    fun `十进制与十六进制写法`() {
        assertEquals("十进制写法", "2212294583", DanmakuSelfStylePolicy.decimalHash(1L))
        assertEquals("十六进制小写无前导零", "83dcefb7", DanmakuSelfStylePolicy.hexHash(1L))
        // 这条的 CRC32 高位是 0，hex 只有 7 位 —— 归一化不能把它当非法值
        assertEquals("7 位 hex", "15f0201", DanmakuSelfStylePolicy.hexHash(987654321L))
        assertEquals("未登录十进制为空", "", DanmakuSelfStylePolicy.decimalHash(0L))
        assertEquals("未登录十六进制为空", "", DanmakuSelfStylePolicy.hexHash(0L))
    }

    // ---------- 归一化 ----------

    @Test
    fun `归一化大小写与前导零`() {
        assertEquals("大写转小写", "83dcefb7", DanmakuSelfStylePolicy.normalize("83DCEFB7"))
        assertEquals("去前导零", "57075e9", DanmakuSelfStylePolicy.normalize("057075e9"))
        assertEquals("全零保留一位", "0", DanmakuSelfStylePolicy.normalize("000"))
        assertEquals("去首尾空白", "9ae0daaf", DanmakuSelfStylePolicy.normalize(" 9ae0daaf "))
        assertEquals("单个 0", "0", DanmakuSelfStylePolicy.normalize("0"))
    }

    @Test
    fun `无法识别的发送者标识归一化为空`() {
        assertEquals("null", "", DanmakuSelfStylePolicy.normalize(null))
        assertEquals("空串", "", DanmakuSelfStylePolicy.normalize(""))
        assertEquals("纯空白", "", DanmakuSelfStylePolicy.normalize("   "))
        assertEquals("非十六进制字符", "", DanmakuSelfStylePolicy.normalize("unknown"))
        assertEquals("含 g", "", DanmakuSelfStylePolicy.normalize("83dcefgb"))
        assertEquals("超长（十进制最多 10 位）", "", DanmakuSelfStylePolicy.normalize("12345678901"))
        assertEquals("全角数字不是十六进制", "", DanmakuSelfStylePolicy.normalize("８３"))
    }

    // ---------- 判定 ----------

    @Test
    fun `本人的弹幕能命中`() {
        val ownMid = 1L
        assertTrue("十六进制命中", DanmakuSelfStylePolicy.matchesOwnHash(ownMid, "83dcefb7"))
        assertTrue("大写十六进制命中", DanmakuSelfStylePolicy.matchesOwnHash(ownMid, "83DCEFB7"))
        assertTrue("十进制写法命中", DanmakuSelfStylePolicy.matchesOwnHash(ownMid, "2212294583"))
        // XML 侧可能补齐成固定宽度（8 位 hex），归一化后应仍相等
        assertTrue("带前导零命中", DanmakuSelfStylePolicy.matchesOwnHash(987654321L, "015f0201"))
    }

    @Test
    fun `别人的弹幕不命中`() {
        assertFalse("别人 mid", DanmakuSelfStylePolicy.matchesOwnHash(1L, DanmakuSelfStylePolicy.hexHash(1234L)))
        assertFalse("别人 mid 的十进制", DanmakuSelfStylePolicy.matchesOwnHash(1L, DanmakuSelfStylePolicy.decimalHash(1234L)))
    }

    @Test
    fun `未登录或无效发送者不命中`() {
        assertFalse("未登录", DanmakuSelfStylePolicy.matchesOwnHash(0L, "83dcefb7"))
        assertFalse("负数 mid", DanmakuSelfStylePolicy.matchesOwnHash(-1L, "83dcefb7"))
        assertFalse("发送者 null", DanmakuSelfStylePolicy.matchesOwnHash(1L, null))
        assertFalse("发送者空串", DanmakuSelfStylePolicy.matchesOwnHash(1L, ""))
        assertFalse("发送者非十六进制", DanmakuSelfStylePolicy.matchesOwnHash(1L, "unknown"))
    }

    @Test
    fun `边框开关关闭时一律不加`() {
        assertFalse(
            "开关关闭",
            DanmakuSelfStylePolicy.shouldApplyBorder(1L, "83dcefb7", false)
        )
        assertTrue(
            "开关打开且是自己",
            DanmakuSelfStylePolicy.shouldApplyBorder(1L, "83dcefb7", true)
        )
        assertFalse(
            "开关打开但不是自己",
            DanmakuSelfStylePolicy.shouldApplyBorder(1L, "9be3e0a3", true)
        )
    }

    // ---------- 边框色 ----------

    @Test
    fun `边框色强制不透明`() {
        assertEquals(
            "半透明主题主色补上 alpha",
            0xFFFF6699.toInt(),
            DanmakuSelfStylePolicy.borderColorFor(0x00FF6699)
        )
        assertEquals(
            "已不透明的颜色保持不变",
            0xFFFF6699.toInt(),
            DanmakuSelfStylePolicy.borderColorFor(0xFFFF6699.toInt())
        )
        assertEquals(
            "0 也能得到可见的黑色",
            0xFF000000.toInt(),
            DanmakuSelfStylePolicy.borderColorFor(0)
        )
    }
}
