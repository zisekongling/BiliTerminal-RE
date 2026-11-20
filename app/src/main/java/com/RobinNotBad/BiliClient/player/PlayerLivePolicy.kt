package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.StringUtil

/**
 * 新版播放器**直播态**的纯策略（可 JVM 单测），只在实验性新版播放器里生效。
 *
 * 设计稿 `design/player-redesign-v2.html` A2 / BOTTOM_LIVE：
 * - 直播没有进度概念，圆屏的直播弧、方屏的进度行整行换成「直播 pill + 在看人数」；
 * - 底栏第 4 格（默认布局的分 P）直播时换成「发弹幕」——直播没有分 P，那一格空着浪费。
 *
 * 人数来源：直播 WS 的 `WATCHED_CHANGE` 把**已格式化**的 `text_large`（如「1.2万」）写进
 * `PlayerActivity.online_number`；拿不到 / 为 0 时界面用「直播中」兜底（文案在 strings.xml）。
 */
object PlayerLivePolicy {

    /**
     * 把原始人数串整理成可展示的短格式；返回 null 表示「拿不到有效人数」，调用方用兜底文案。
     *
     * - null / 空白 / "0" / 非正数 / 解析不了 → null；
     * - 已带「万 / 亿」（WS 的 text_large 就是这种）→ 原样返回，不二次格式化；
     * - 纯数字 → 走 [StringUtil.toWan]（≥1 亿→亿、≥1 万→万、否则原数）。
     */
    fun onlineText(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "0") return null
        if (text.contains("万") || text.contains("亿")) return text
        val num = text.toLongOrNull() ?: return null
        return if (num <= 0L) null else StringUtil.toWan(num)
    }

    /**
     * 直播时的底栏布局：把「分 P」那一格换成「发弹幕」（设计稿 BOTTOM_LIVE 第 4 格）。
     *
     * - 底栏已经有发弹幕（用户自定义布局）→ 原样返回，不重复加；
     * - 否则按 底左 → 底右 的顺序找第一个分 P 单按钮替换（默认布局在底右末格）；
     * - 没有分 P 格且底右未满 → 追加一格发弹幕；底右满了就不动（不挤掉用户摆的按钮）。
     *
     * 只改底栏两区，左右两列与组一律不动；返回新 Layout，不改入参。
     */
    fun withLiveBottomSend(layout: PlayerUiLayout.Layout): PlayerUiLayout.Layout {
        val bottom = layout.bottomLeft + layout.bottomRight
        val hasSend = bottom.any { it is PlayerUiLayout.Slot.Button && it.key == PlayerButtons.DANMAKU_SEND }
        if (hasSend) return layout

        fun swapFirstPage(slots: List<PlayerUiLayout.Slot>): List<PlayerUiLayout.Slot>? {
            val index = slots.indexOfFirst {
                it is PlayerUiLayout.Slot.Button && it.key == PlayerButtons.PAGE_SELECTOR
            }
            if (index < 0) return null
            return slots.toMutableList().also { it[index] = PlayerUiLayout.Slot.Button(PlayerButtons.DANMAKU_SEND) }
        }

        swapFirstPage(layout.bottomLeft)?.let { return layout.copy(bottomLeft = it) }
        swapFirstPage(layout.bottomRight)?.let { return layout.copy(bottomRight = it) }
        return if (layout.bottomRight.size < PlayerUiLayout.Region.BOTTOM_RIGHT.capacity) {
            layout.copy(bottomRight = layout.bottomRight + PlayerUiLayout.Slot.Button(PlayerButtons.DANMAKU_SEND))
        } else {
            layout
        }
    }
}