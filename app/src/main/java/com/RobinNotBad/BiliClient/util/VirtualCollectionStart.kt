package com.RobinNotBad.BiliClient.util

/**
 * 虚拟合集的「起播哪一条」判据。
 *
 * 两条合集入口各有一次下标空间换算，换算错了表现都是同一句话——**点哪条都播第一条**：
 *
 *  * 收藏夹（在线）：条目列表的 aid 拿不到（接口回 0）或列表里出现重复 aid 时，
 *    只按 `aid == 点击项 aid` 反查会一直匹配到第 0 条；
 *  * 本地缓存：合集只收「真的有文件」的视频，被点那条在全量列表里的下标不能直接当合集下标用。
 *
 * 抽成纯函数：判据错了会静默播错视频，单测比重现路径便宜。**任何一侧都不得静默退回 0** ——
 * 用 -1 表示"定不下来"，由调用方提示用户，而不是把第一条播出去。
 */
object VirtualCollectionStart {

    /**
     * 收藏夹（在线）合集的起播下标。
     *
     * @param startPosition 点击项在列表中的位置（界面直接给的，**权威值**）
     * @param startAid      点击项的 aid；为 0 表示没取到，只能用位置
     * @param aids          列表各条的 aid，与列表等长
     * @return 起播下标；定不下来返回 -1
     */
    fun resolveOnline(startPosition: Int, startAid: Long, aids: List<Long>): Int {
        // 位置在范围内、且能和 aid 对上（或本来就没有 aid）→ 直接用它
        if (startPosition in aids.indices && (startAid == 0L || aids[startPosition] == startAid)) {
            return startPosition
        }
        // 位置越界（列表刷新过、adapter 位置不可信）时才按 aid 反查
        if (startAid != 0L) {
            val found = aids.indexOfFirst { it == startAid }
            if (found >= 0) return found
        }
        return -1
    }

    /**
     * 本地缓存合集的起播下标：合集由「文件夹里的视频」再筛出「真的有文件」的那些组成，
     * 被点那条在**全量列表**里的下标必须换算到合集下标。
     *
     * @param clickedInFullList 被点视频在 videoList（全量列表）里的下标
     * @param collectionFullIndices 合集各条在 videoList 里的下标，顺序即合集顺序
     * @return 合集下标；被点那条不在合集里（不在本文件夹、或没有可播文件）返回 -1
     */
    fun resolveLocal(clickedInFullList: Int, collectionFullIndices: List<Int>): Int {
        if (clickedInFullList < 0) return -1
        return collectionFullIndices.indexOf(clickedInFullList)
    }
}
