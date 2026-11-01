package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 异常信息分类的 JVM 单测（反馈 #84）。
 *
 * 锁住两条相反的错误方向：
 * - 真正的 RecyclerView 不一致错误 → 仍然识别为 Adapter 错误（保留原来「无需上报」的提示）；
 * - 普通越界 / 空列表 bug（#84 的 StringIndexOutOfBoundsException）→ **不能**再被标成 Adapter 错误，
 *   否则真实问题会被「无需上报」盖掉。
 */
class AdapterErrorClassifierTest {

    @Test
    fun recyclerView不一致错误_识别为Adapter错误() {
        // RecyclerView 内部状态不一致的典型原文（线上/线上模拟都长这样）
        assertTrue(
            AdapterErrorClassifier.looksLikeAdapterError(
                "java.lang.IndexOutOfBoundsException: Inconsistency detected. " +
                        "Invalid view holder adapter positionViewHolder{8c1a2f position=7 id=-1, oldPos=-1, pLpos:-1 no parent}"
            )
        )
    }

    @Test
    fun invalidItemPosition_识别为Adapter错误() {
        assertTrue(
            AdapterErrorClassifier.looksLikeAdapterError(
                "java.lang.IndexOutOfBoundsException: Invalid item position 3(offset:3). Item count:2"
            )
        )
    }

    @Test
    fun 消息里提到RecyclerView_识别为Adapter错误() {
        assertTrue(
            AdapterErrorClassifier.looksLikeAdapterError(
                "java.lang.IndexOutOfBoundsException: while updating RecyclerView"
            )
        )
    }

    @Test
    fun 消息里提到Adapter_识别为Adapter错误() {
        assertTrue(
            AdapterErrorClassifier.looksLikeAdapterError(
                "java.lang.IndexOutOfBoundsException: adapter index out of range"
            )
        )
    }

    @Test
    fun 空列表导致的StringIndexOutOfBounds_不再算Adapter错误() {
        // #84 的真实异常形态：拼接 uids 参数时 substring(0, -1)
        assertFalse(
            AdapterErrorClassifier.looksLikeAdapterError(
                "java.lang.StringIndexOutOfBoundsException: begin 0, end -1, length 5"
            )
        )
    }

    @Test
    fun 普通下标越界_不算Adapter错误() {
        assertFalse(
            AdapterErrorClassifier.looksLikeAdapterError(
                "java.lang.IndexOutOfBoundsException: Index: 3, Size: 2"
            )
        )
        assertFalse(
            AdapterErrorClassifier.looksLikeAdapterError(
                "java.lang.ArrayIndexOutOfBoundsException: length=2; index=3"
            )
        )
    }

    @Test
    fun 空值或空串_不识别() {
        assertFalse(AdapterErrorClassifier.looksLikeAdapterError(null))
        assertFalse(AdapterErrorClassifier.looksLikeAdapterError(""))
        assertFalse(AdapterErrorClassifier.looksLikeAdapterError("   "))
    }

    @Test
    fun 匹配不区分大小写() {
        assertTrue(
            AdapterErrorClassifier.looksLikeAdapterError(
                "java.lang.IndexOutOfBoundsException: INCONSISTENCY DETECTED. invalid item position"
            )
        )
    }
}
