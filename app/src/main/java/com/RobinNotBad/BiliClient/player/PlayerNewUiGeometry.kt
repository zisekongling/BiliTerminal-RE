package com.RobinNotBad.BiliClient.player

import kotlin.math.cos
import kotlin.math.sin

/** 新版播放器的纯几何与槽位映射；坐标单位为 dp，原点在屏幕左上角。 */
object PlayerNewUiGeometry {
    const val REFERENCE_SIZE_DP = 233f
    const val ROUND_RADIUS_DP = 116.5f
    const val GRID_PAGE_SIZE = 9

    /** 「更多」网格固定 3 列。 */
    const val GRID_COLUMNS = 3

    data class Point(val x: Float, val y: Float)

    /** 一段竖直区间（dp）：文字行、热区都用它判「有没有压在一起」。 */
    data class Band(val top: Float, val height: Float) {
        val bottom: Float get() = top + height
        fun overlaps(other: Band): Boolean = top < other.bottom && other.top < bottom
    }

    /** 圆屏中心键下方那行「当前 / 总长」：设计稿 top 72、行高 13，按实际直径缩放。 */
    fun roundProgressTextBand(sizeDp: Float): Band = Band(72f, 13f).scaled(sizeDp)

    /** 圆屏直播态中心键下方那行「直播 pill + 在看人数」：设计稿 A2 top 70、行高 14，按实际直径缩放。 */
    fun roundLiveBand(sizeDp: Float): Band = Band(70f, 14f).scaled(sizeDp)

    /**
     * 圆屏拖动预览三行（+mm:ss / 目标时间 / 共 mm:ss）的落点。
     *
     * 设计稿写的是 top 78 / 94 / 132，但第一行 78–92 与时间行 72–85 **重叠**，
     * 拖弧时「+mm:ss」会压在「当前 / 总长」上。这里把整块挪到时间行下方（留 2dp），
     * 行高 14 / 34 / 13 与行间距 2 / 4 仍照设计稿，全部乘实际直径 / 233。
     * 中心播放键与底部按钮在预览期间是 GONE 的（设计稿 seek 态只留顶栏、弧与这三行），
     * 所以整块可以占住画面中部，只要不伸进底部按钮那一圈。
     */
    fun roundSeekRows(sizeDp: Float): List<Band> {
        val factor = sizeDp / REFERENCE_SIZE_DP
        val heights = floatArrayOf(14f, 34f, 13f)
        val gaps = floatArrayOf(2f, 4f)
        var top = roundProgressTextBand(sizeDp).bottom + 2f * factor
        return heights.indices.map { index ->
            val band = Band(top, heights[index] * factor)
            top = band.bottom + gaps.getOrElse(index) { 0f } * factor
            band
        }
    }

    private fun Band.scaled(sizeDp: Float): Band {
        val factor = sizeDp / REFERENCE_SIZE_DP
        return Band(top * factor, height * factor)
    }

    data class Spec(
        val slots: List<PlayerUiLayout.Slot>,
        val positions: List<Point>
    )

    fun sideSpec(slots: List<PlayerUiLayout.Slot>, round: Boolean): Spec = Spec(
        slots.take(3),
        if (round) listOf(155f, 180f, 205f).map { polar(94f, it) }
        else listOf(Point(26f, 96f), Point(26f, 140f), Point(26f, 184f))
    )

    fun mirrorX(spec: Spec, widthDp: Float): Spec = spec.copy(
        positions = spec.positions.map { it.copy(x = widthDp - it.x) }
    )

    /**
     * 底栏落位。圆屏固定 235°/258.3°/281.7°/305° 四个角（r=88）；
     * 方屏最多 5 格，整行居中、相邻中心间距 48dp（设计稿 S_BOT_X：240 宽的屏上是 48/96/144/192）。
     */
    fun bottomSpec(slots: List<PlayerUiLayout.Slot>, round: Boolean, widthDp: Float = 240f): Spec {
        if (round) {
            return Spec(slots.take(4), listOf(235f, 258.3f, 281.7f, 305f).map { polar(88f, it) })
        }
        val count = minOf(slots.size, 5)
        val center = widthDp / 2f
        return Spec(
            slots.take(5),
            (0 until count).map { Point(center + (it - (count - 1) / 2f) * 48f, 252f) }
        )
    }

    /** 同一份溢出只追加一次：圆屏底栏第 5 格及之后，方屏第 6 格及之后。 */
    fun visibleAndOverflow(layout: PlayerUiLayout.Layout, round: Boolean): Pair<List<PlayerUiLayout.Slot>, List<PlayerUiLayout.Slot>> {
        val bottom = layout.bottomLeft + layout.bottomRight
        val visible = layout.left.take(3) + layout.right.take(3) + bottom.take(if (round) 4 else 5)
        val extra = layout.left.drop(3) + layout.right.drop(3) + bottom.drop(if (round) 4 else 5)
        return visible to extra
    }

    fun pages(keys: List<String>): List<List<String>> =
        if (keys.isEmpty()) emptyList() else keys.chunked(GRID_PAGE_SIZE)

    /**
     * 按可用高度算一页能放几格（3 列的网格）。
     *
     * 手表/圆屏竖着只能放 3 行（9 格）；手机竖屏能放 4 行、横屏/长条屏更少行但更宽。
     * 用户要求「手机播放器一页展示全部更多按钮」——与其固定 9 格分页，
     * 不如按屏幕实际能装下多少行来定，短列表就一页放完，不再为了翻页而翻页。
     */
    fun gridCapacity(cellH: Float, gapH: Float, availableH: Float, columns: Int = 3): Int {
        if (cellH <= 0f || availableH <= 0f) return GRID_PAGE_SIZE
        val rowH = cellH + gapH
        // +gapH 是因为 n 行之间只有 n-1 个间隙。
        // +1e-3 行是浮点容差：可用高度恰等于 n 行时（网格 fit 的边界尺寸），
        // 除法可能落在 2.9999998 上，不纠就会少算一行、把一页放得下的按钮拆成两页。
        val rows = ((availableH + gapH) / rowH + 1e-3f).toInt().coerceAtLeast(1)
        return (rows * columns).coerceAtLeast(columns)
    }

    /** 按容量分页；容量为 0 或 keys 为空时返回空列表。 */
    fun pages(keys: List<String>, pageSize: Int): List<List<String>> =
        if (keys.isEmpty() || pageSize <= 0) emptyList() else keys.chunked(pageSize)

    fun polar(radius: Float, degrees: Float): Point {
        val radians = Math.toRadians(degrees.toDouble())
        return Point(
            (ROUND_RADIUS_DP + radius * cos(radians)).toFloat(),
            (ROUND_RADIUS_DP - radius * sin(radians)).toFloat()
        )
    }

    /** 非 233dp 圆屏等比缩放，保证热区外沿仍在表圈内。 */
    fun scalePoint(point: Point, sizeDp: Float): Point {
        val factor = sizeDp / REFERENCE_SIZE_DP
        return Point(point.x * factor, point.y * factor)
    }
}
