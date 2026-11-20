package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 新版播放器直播态纯策略（[PlayerLivePolicy]）的 JVM 测试：
 * 人数格式化兜底口径 + 底栏「分 P → 发弹幕」换格（设计稿 BOTTOM_LIVE）。
 */
class PlayerLivePolicyTest {

    @Test
    fun `online text falls back when number is missing or zero`() {
        assertNull(PlayerLivePolicy.onlineText(null))
        assertNull(PlayerLivePolicy.onlineText(""))
        assertNull(PlayerLivePolicy.onlineText("   "))
        assertNull(PlayerLivePolicy.onlineText("0"))
        assertNull(PlayerLivePolicy.onlineText("-5"))
        assertNull(PlayerLivePolicy.onlineText("abc"))
    }

    @Test
    fun `online text formats plain numbers via toWan`() {
        assertEquals("9999", PlayerLivePolicy.onlineText("9999"))
        assertEquals("1.2万", PlayerLivePolicy.onlineText("12345"))
        assertEquals("1.0亿", PlayerLivePolicy.onlineText("100000000"))
        // WS 的 text_large 已带单位：原样透传，不二次格式化。
        assertEquals("1.2万", PlayerLivePolicy.onlineText("1.2万"))
        assertEquals("3亿", PlayerLivePolicy.onlineText("3亿"))
        assertEquals("42", PlayerLivePolicy.onlineText(" 42 "))
    }

    @Test
    fun `default layout swaps page selector for danmaku send`() {
        val live = PlayerLivePolicy.withLiveBottomSend(PlayerUiLayout.DEFAULT)
        // 底右末格（第 4 格）由分 P 换成发弹幕，其余三格不动。
        assertEquals(
            listOf(PlayerButtons.SOUND_ADD, PlayerButtons.DANMAKU_SEND),
            live.bottomRight.filterIsInstance<PlayerUiLayout.Slot.Button>().map { it.key },
        )
        assertEquals(PlayerUiLayout.DEFAULT.bottomLeft, live.bottomLeft)
        assertEquals(PlayerUiLayout.DEFAULT.left, live.left)
        assertEquals(PlayerUiLayout.DEFAULT.right, live.right)
        // 入参不被改动。
        assertEquals(
            listOf(PlayerButtons.SOUND_ADD, PlayerButtons.PAGE_SELECTOR),
            PlayerUiLayout.DEFAULT.bottomRight.filterIsInstance<PlayerUiLayout.Slot.Button>().map { it.key },
        )
    }

    @Test
    fun `layout already having send at bottom is untouched`() {
        val layout = PlayerUiLayout.DEFAULT.copy(
            bottomRight = listOf(
                PlayerUiLayout.Slot.Button(PlayerButtons.SOUND_ADD),
                PlayerUiLayout.Slot.Button(PlayerButtons.DANMAKU_SEND),
            ),
        )
        assertSame(layout, PlayerLivePolicy.withLiveBottomSend(layout))
    }

    @Test
    fun `page selector in bottom left is swapped first`() {
        val layout = PlayerUiLayout.DEFAULT.copy(
            bottomLeft = listOf(
                PlayerUiLayout.Slot.Button(PlayerButtons.PAGE_SELECTOR),
                PlayerUiLayout.Slot.Button(PlayerButtons.SOUND_CUT),
            ),
        )
        val live = PlayerLivePolicy.withLiveBottomSend(layout)
        assertEquals(PlayerButtons.DANMAKU_SEND, (live.bottomLeft[0] as PlayerUiLayout.Slot.Button).key)
        assertEquals(layout.bottomRight, live.bottomRight)
    }

    @Test
    fun `no page selector appends send when bottom right has room`() {
        val layout = PlayerUiLayout.DEFAULT.copy(
            bottomRight = listOf(PlayerUiLayout.Slot.Button(PlayerButtons.SOUND_ADD)),
        )
        val live = PlayerLivePolicy.withLiveBottomSend(layout)
        assertEquals(
            listOf(PlayerButtons.SOUND_ADD, PlayerButtons.DANMAKU_SEND),
            live.bottomRight.filterIsInstance<PlayerUiLayout.Slot.Button>().map { it.key },
        )
    }

    @Test
    fun `full bottom right without page selector is left alone`() {
        val layout = PlayerUiLayout.DEFAULT.copy(
            bottomRight = listOf(
                PlayerUiLayout.Slot.Button(PlayerButtons.SOUND_ADD),
                PlayerUiLayout.Slot.Button(PlayerButtons.QUALITY),
                PlayerUiLayout.Slot.Button(PlayerButtons.SPEED),
            ),
        )
        assertSame(layout, PlayerLivePolicy.withLiveBottomSend(layout))
    }

    @Test
    fun `send inside a group does not count as bottom slot`() {
        // 组里的发弹幕不算底栏已有：底栏仍要换出一格（设计稿第 4 格）。
        val layout = PlayerUiLayout.DEFAULT.copy(
            bottomRight = listOf(
                PlayerUiLayout.Slot.Button(PlayerButtons.SOUND_ADD),
                PlayerUiLayout.Slot.Group(listOf(PlayerButtons.DANMAKU_SEND, PlayerButtons.LOOP)),
            ),
        )
        val live = PlayerLivePolicy.withLiveBottomSend(layout)
        // 没有分 P 可换、底右未满 → 追加一格。
        assertEquals(3, live.bottomRight.size)
        assertEquals(PlayerButtons.DANMAKU_SEND, (live.bottomRight[2] as PlayerUiLayout.Slot.Button).key)
    }
}