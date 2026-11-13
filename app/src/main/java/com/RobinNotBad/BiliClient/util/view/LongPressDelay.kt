package com.RobinNotBad.BiliClient.util.view

/**
 * 列表卡片「长按」的触发延时（反馈 #140）。
 *
 * 原先四处各写一个硬编码 200L：`VideoCardHolder.bindClick`、
 * `HistoryVideoCardAdapter`、`UserVideoAdapter`、`ArticleCardAdapter`。
 * 200ms 在手表上太短——滑列表时手指稍一停顿就会误触长按（弹出菜单、进入多选），
 * 用户往往还没看清就跳走了。统一放到这里，取值 [CARD_MS] = 600ms：
 *
 * - 落在建议区间 500–700ms 的中间，比系统 `ViewConfiguration.getLongPressTimeout()`
 *   （默认 500ms）略长一点，滑动误触基本消失，真长按仍然是「按住不到一秒」的手感。
 * - 只影响这四处卡片；播放器隐藏控件、快捷键等其它 `postDelayed` 与此无关，不要引用它。
 */
object LongPressDelay {
    /** 卡片长按触发延时（毫秒）。 */
    const val CARD_MS = 600L
}
