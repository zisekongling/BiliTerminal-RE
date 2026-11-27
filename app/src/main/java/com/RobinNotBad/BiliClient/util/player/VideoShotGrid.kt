package com.RobinNotBad.BiliClient.util.player

import com.RobinNotBad.BiliClient.model.VideoShot

/**
 * 快照拼版的取格逻辑（纯函数，便于单测）。
 *
 * 服务端把缩略图拼成若干张大图：[VideoShot.images] 里每张都是
 * `xLen × yLen` 的网格，每格 `xSize × ySize` 像素；[VideoShot.timeTableSec] 是
 * 与格子一一对应的秒级时间点，顺序「从左到右、从上到下」。
 *
 * <p>三个必须防的边界（都实测过，见 [VideoShot] 的注释）：
 * - `index` 有**重复秒值**，所以「按时间找格子」取的是最后一个不晚于它的那个；
 * - `image` 可能**多张**，而 `index` 的长度不保证正好等于 `总格数`；
 * - 服务端可能压根不给 `index`（空表），此时没有可用的时间映射，直接当不可用。
 *
 * 这里全部用纯数据（[Crop]/[CropRect]）而不是 `android.graphics.Rect`，
 * 否则这些分支在纯 JVM 单测里跑不起来。
 */
object VideoShotGrid {

    /** 一张拼版上的格子数；尺寸非法时返回 0。 */
    fun cellsPerSheet(shot: VideoShot): Int {
        if (shot.xLen <= 0 || shot.yLen <= 0) return 0
        return shot.xLen * shot.yLen
    }

    /** 这份快照能不能拿来画预览（缺任一项都不行）。 */
    fun isUsable(shot: VideoShot?): Boolean {
        if (shot == null) return false
        if (shot.images.isEmpty()) return false
        if (shot.timeTableSec.isEmpty()) return false
        if (shot.xSize <= 0 || shot.ySize <= 0) return false
        return cellsPerSheet(shot) > 0
    }

    /**
     * 取「最后一个不晚于 [positionMs] 的时间点」的下标。
     *
     * 早于第一格按第一格算（第一格通常就是 0 秒）；晚于最后一格按最后一格算。
     * 表为空返回 -1；`positionMs` 为负数按第一格算。
     */
    fun indexAt(shot: VideoShot, positionMs: Long): Int {
        val table = shot.timeTableSec
        if (table.isEmpty()) return -1
        val sec = (positionMs / 1000L).toInt()
        if (sec <= table[0]) return 0
        // 二分找最后一个 <= sec 的下标。表里有重复值（实测 0,0,16,…），
        // 「最后一个」正好落在重复段里靠后的那格，与显示顺序一致。
        var lo = 0
        var hi = table.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (table[mid] <= sec) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** 该下标落在第几张拼版上（越界一律夹到已有的最后一张）。 */
    fun sheetOf(shot: VideoShot, index: Int): Int {
        val per = cellsPerSheet(shot)
        if (per <= 0 || index <= 0) return 0
        val last = (shot.images.size - 1).coerceAtLeast(0)
        return (index / per).coerceIn(0, last)
    }

    /** 源图上的裁剪区域（像素坐标，右下开区间）。 */
    data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** 一次预览要画的东西：取哪张拼版的哪一块。 */
    data class Crop(val sheetIndex: Int, val rect: CropRect)

    /**
     * 取 [positionMs] 对应的缩略图裁剪区域；这份快照不可用时返回 null。
     *
     * 网格映射：格子序号 `cell` 在拼版内的列 = `cell % xLen`、行 = `cell / xLen`，
     * 于是左上角就是 `(列 × xSize, 行 × ySize)`。
     */
    fun cropFor(shot: VideoShot?, positionMs: Long): Crop? {
        if (shot == null || !isUsable(shot)) return null
        val index = indexAt(shot, positionMs)
        if (index < 0) return null
        val per = cellsPerSheet(shot)
        val cell = index % per
        val col = cell % shot.xLen
        val row = cell / shot.xLen
        val left = col * shot.xSize
        val top = row * shot.ySize
        return Crop(
            sheetIndex = sheetOf(shot, index),
            rect = CropRect(left, top, left + shot.xSize, top + shot.ySize)
        )
    }
}
