package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.DownloadSection

/**
 * 下载列表（[com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity]）换掉一批数据之后
 * 「该发哪种通知」的判定。
 *
 * 为什么单独抽成纯函数：这条判定要能在没有 Robolectric 的纯 JVM 单测里跑，所以这里只读
 * [DownloadSection] 上的几个字段，不碰任何 Android 类。
 *
 * 背景：本页的 adapter 是**同一个实例**复用到底的（Timer 每 400ms 要靠它在原实例上逐条
 * `notifyItemChanged` 推进度，整表换 adapter 会把这条高频路径和滚动位置一起打断），
 * 所以每次刷新都得自己回答一句「这次只是状态变了，还是条目被增删/换了顺序」：
 *
 * - 结构变了（条数或 id 顺序不同）→ 只能 `notifyDataSetChanged()` 整表重绑；
 * - 结构没变 → 只重绑状态真的变了的那几行，避免每次回包都把整屏重绑一遍（封面重载、闪烁）。
 *
 * 注意「空列表」在 [com.RobinNotBad.BiliClient.adapter.video.DownloadAdapter.getItemCount] 里
 * 也会占 1 行（占位提示），但两次空列表之间结构是一样、也没有内容变化，所以这里按「不用通知」处理。
 */
object DownloadListDiff {

    /**
     * 两版列表的「结构」是否相同：条数一样，且同一位置上是同一条下载任务（id 相同）。
     *
     * 结构相同才能按下标做增量刷新——按下标通知的前提是「位置没有搬家」。
     */
    fun sameStructure(previous: List<DownloadSection>, current: List<DownloadSection>): Boolean {
        if (previous.size != current.size) return false
        for (position in previous.indices) {
            if (previous[position].id != current[position].id) return false
        }
        return true
    }

    /**
     * 结构相同时，界面上会看到变化的位置（供逐个 `notifyItemChanged`）。
     *
     * 只比这几项：标题、DB 状态、失败原因、封面地址——它们都是列表行直接从
     * [DownloadSection] 读出来的字段，重新取数后可能变；进度、速度来自进度映射表，
     * 由 Timer 那条路径自己刷新，不在这里。
     *
     * 结构已经变了时返回空表：那种情况调用方应当整表重绑，逐个通知覆盖不到增删。
     */
    fun changedPositions(
        previous: List<DownloadSection>,
        current: List<DownloadSection>
    ): List<Int> {
        if (!sameStructure(previous, current)) return emptyList()
        val changed = ArrayList<Int>()
        for (position in previous.indices) {
            val before = previous[position]
            val after = current[position]
            if (before.name_short != after.name_short ||
                before.state != after.state ||
                before.failReason != after.failReason ||
                before.url_cover != after.url_cover
            ) {
                changed.add(position)
            }
        }
        return changed
    }
}
