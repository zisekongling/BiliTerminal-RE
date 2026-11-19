package com.RobinNotBad.BiliClient.player

import kotlin.math.cos
import kotlin.math.sin

/** 新版播放器的纯几何与槽位映射；坐标单位为 dp，原点在屏幕左上角。 */
object PlayerNewUiGeometry {
    const val REFERENCE_SIZE_DP = 233f
    const val ROUND_RADIUS_DP = 116.5f
    const val GRID_PAGE_SIZE = 9

    data class Point(val x: Float, val y: Float)

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
