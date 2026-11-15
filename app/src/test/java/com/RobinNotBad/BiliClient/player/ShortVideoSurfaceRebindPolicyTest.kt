package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.player.ShortVideoSurfaceAction.ATTACH_ONLY
import com.RobinNotBad.BiliClient.player.ShortVideoSurfaceAction.ATTACH_REPAINT
import com.RobinNotBad.BiliClient.player.ShortVideoSurfaceAction.ATTACH_REPAINT_AND_START
import com.RobinNotBad.BiliClient.player.ShortVideoSurfaceAction.NONE
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 反馈 #167（短视频回滑黑屏）的 surface 处置判定。
 *
 * 这几条分支就是「回来之后会不会黑」的全部逻辑：只有「丢过 surface + 已 prepared」才需要
 * seek 强制出帧，只有「活跃且宿主停在第 0 页」才允许顺手续播。
 */
class ShortVideoSurfaceRebindPolicyTest {

    private fun decide(
        hasTexture: Boolean = true,
        needsRepaint: Boolean = true,
        isPrepared: Boolean = true,
        isActive: Boolean = true,
        feedVisible: Boolean = true
    ) = ShortVideoSurfaceRebindPolicy.decide(
        hasTexture = hasTexture,
        needsRepaint = needsRepaint,
        isPrepared = isPrepared,
        isActive = isActive,
        feedVisible = feedVisible
    )

    @Test
    fun noTexture_meansNothingToDo() {
        // View 还没上屏（比如刚 bind、还没 attach）：等 surface 回调，别去碰播放器
        assertEquals(NONE, decide(hasTexture = false))
        assertEquals(NONE, decide(hasTexture = false, needsRepaint = false))
    }

    @Test
    fun firstAttach_onlyAttaches() {
        // 首次挂载：onPrepared 会按 isActive 起播，多余的 seek 只会拖慢首帧
        assertEquals(ATTACH_ONLY, decide(needsRepaint = false, isPrepared = false))
        // 顺序被打乱（prepared 早于首次挂载）也不 seek：这两个状态互相匹配
        assertEquals(ATTACH_ONLY, decide(needsRepaint = false, isPrepared = true))
    }

    @Test
    fun surfaceLostWhilePreparing_onlyAttaches() {
        // prepare 还没完成就重挂：挂上就行，出帧由 onPrepared 之后开始
        assertEquals(ATTACH_ONLY, decide(needsRepaint = true, isPrepared = false))
    }

    @Test
    fun surfaceLostAfterPrepare_repaintsAndResumes() {
        // 反馈 #167 的主场景：滑走再滑回来，这一页既是活跃页、宿主也停在第 0 页
        assertEquals(ATTACH_REPAINT_AND_START, decide())
    }

    @Test
    fun surfaceLostOnDetailPage_repaintsButDoesNotResume() {
        // 横滑到详情页时信息流整页仍在屏幕上：补帧可以，但绝不能把声音放出来
        assertEquals(ATTACH_REPAINT, decide(feedVisible = false))
        assertEquals(ATTACH_REPAINT, decide(feedVisible = false, isActive = false))
    }

    @Test
    fun surfaceLostOnInactivePage_repaintsOnly() {
        // 邻页重新拿到 surface（预加载中）：补一帧，但不起播
        assertEquals(ATTACH_REPAINT, decide(isActive = false))
    }
}
