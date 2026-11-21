package com.RobinNotBad.BiliClient.ui.widget

import com.RobinNotBad.BiliClient.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评论区操作行圆屏排版的纯逻辑单测。
 *
 * 这块错了的后果：圆屏开关打开后这一行还是贴着两端（赞数/回复被圆弧切掉），
 * 或者居中链少了一个按钮导致顺序错位——两者都只能靠人眼看出来，所以至少把
 * 「什么条件下居中」和「链里有谁、按什么顺序」钉在单测里。
 */
class ReplyActionRowTest {

    // ---- 判据 ----

    @Test
    fun shouldCenter_followsRoundMode() {
        assertTrue(ReplyActionRow.shouldCenter(true))
        assertFalse(ReplyActionRow.shouldCenter(false))
    }

    // ---- 间距换算 ----

    @Test
    fun gapPx_convertsByDensity() {
        // 6dp：1x/2x/3x 三档密度都必须落成整数像素；2.625（部分 xhdpi 设备）四舍五入
        assertEquals(6, ReplyActionRow.gapPx(1.0f))
        assertEquals(12, ReplyActionRow.gapPx(2.0f))
        assertEquals(18, ReplyActionRow.gapPx(3.0f))
        assertEquals(16, ReplyActionRow.gapPx(2.625f))
    }

    // ---- 链的成员与顺序 ----

    @Test
    fun centeredChain_keepsVisualOrder() {
        // 赞数 → 点踩 → 翻译 → 删除 → 回复，顺序变了这一行就错位
        assertArrayEquals(
            intArrayOf(
                R.id.likes,
                R.id.dislikeBtn,
                R.id.item_reply_translate,
                R.id.item_reply_delete,
                R.id.replyBtn
            ),
            ReplyActionRow.CENTERED_CHAIN
        )
    }

    @Test
    fun centeredChain_hasNoDuplicates() {
        // 同一个控件在链里出现两次会让 ConstraintSet 连出自环，界面直接塌成一条竖线
        assertEquals(
            ReplyActionRow.CENTERED_CHAIN.size,
            ReplyActionRow.CENTERED_CHAIN.toHashSet().size
        )
    }
}
