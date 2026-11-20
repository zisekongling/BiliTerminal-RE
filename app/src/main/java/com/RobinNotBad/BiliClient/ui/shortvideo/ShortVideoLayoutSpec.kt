package com.RobinNotBad.BiliClient.ui.shortvideo

/**
 * 短视频新版界面的布局规格（纯函数，可单测）。坐标单位 dp，原点在根布局左上角。
 *
 * 设计稿：`design/short-video-redesign.html`。稿子只标注了两个参考尺寸
 * （方屏 240×280dp、圆屏 R=116.5dp），其余屏幕「按边缘锚定，其它尺寸照样成立」。
 * 所以这里**不写死坐标**，而是把每个元素换算成相对它锚定那条边的距离，再乘缩放系数：
 *
 * - 顶部（返回 / 时钟 / ⋯）：距顶；
 * - 右侧动作条：以屏幕竖直中线为基准，上下按 110/154/198 的比例展开；
 * - UP 行 / 标题 / 播放量 / 进度条：**距底**；
 * - 中心播放键：以屏幕正中为基准。
 *
 * 圆屏另有一套极坐标（[roundArcRadius] 等），因为圆内可用宽度随高度变化，
 * 直排会贴到表圈外。
 */
object ShortVideoLayoutSpec {

    /** 设计稿的方屏参考尺寸。 */
    const val REF_WIDTH = 240f
    const val REF_HEIGHT = 280f

    /** 圆屏参考半径。 */
    const val REF_ROUND_RADIUS = 116.5f

    /** 缩放系数的夹取范围：手表 200~320dp 短边之间保持接近 1.0，手机/平板不无限放大。 */
    const val MIN_SCALE = 0.80f
    const val MAX_SCALE = 1.60f

    /**
     * 一块屏的度量。所有落点都由它推出来，换分辨率只换宽高。
     *
     * @param widthDp 当前页面可用宽度
     * @param heightDp 当前页面可用高度
     * @param round 是否圆屏
     */
    data class Metrics(val widthDp: Float, val heightDp: Float, val round: Boolean) {
        val shortSide: Float get() = minOf(widthDp, heightDp)
        val centerX: Float get() = widthDp / 2f
        val centerY: Float get() = heightDp / 2f

        /**
         * 尺寸放大系数。
         *
         * 方屏以 240 宽（或 280 高，取能放下整行的那一边）为基准；圆屏以直径 233（=2R）为基准。
         * 夹在 [MIN_SCALE]~[MAX_SCALE]：手表上不会被缩到按不着，手机上也不会占满整屏。
         */
        val scale: Float
            get() {
                val base = if (round) REF_ROUND_RADIUS * 2f else REF_WIDTH
                return (shortSide / base).coerceIn(MIN_SCALE, MAX_SCALE)
            }

        fun s(value: Float): Float = value * scale
    }

    // ==================== 尺寸 ====================

    /** 顶部热区（返回 / ⋯）。 */
    fun topBarHeight(m: Metrics): Float = m.s(48f)

    /** 顶部压暗渐变高度。 */
    fun topScrimHeight(m: Metrics): Float = m.s(56f)

    /** 底部压暗渐变高度。 */
    fun bottomScrimHeight(m: Metrics): Float = m.s(120f)

    /** 图标热区。 */
    fun touchSize(m: Metrics): Float = m.s(36f)

    /** 图标本身。 */
    fun iconSize(m: Metrics): Float = m.s(20f)

    // ==================== 顶部一行 ====================

    /** 顶部一行的中线 y。 */
    fun topRowCenterY(m: Metrics): Float = m.s(24f)

    /** 返回键中心 x（贴左边）。 */
    fun backCenterX(m: Metrics): Float = m.s(24f)

    /** 时钟中心 x（正中）。 */
    fun clockCenterX(m: Metrics): Float = m.centerX

    /** 「⋯」中心 x（贴右边）。 */
    fun moreCenterX(m: Metrics): Float = m.widthDp - m.s(24f)

    // ==================== 右侧动作条 ====================

    /**
     * 右侧动作条的三个中心 y（简介 / 评论 / 收藏）。
     *
     * 设计稿在 280 高上是 110 / 154 / 198。相对屏幕中线 140 是 -30 / +14 / +58，
     * **不是以中线对称的**——整列偏向中线下方一点。所以先算出「整列的中点」
     * （也就是中间那一格），再向上下各展开一个间距，屏幕变高时整列跟着中线走。
     */
    fun actionCenterYs(m: Metrics): List<Float> {
        val gap = m.s(44f)
        val middle = m.centerY + m.s(14f)
        return listOf(middle - gap, middle, middle + gap)
    }

    /** 动作条中心 x：贴右侧安全边。 */
    fun actionCenterX(m: Metrics): Float = m.widthDp - m.s(26f)

    /** 动作条每格的宽（图标 + 计数）。 */
    fun actionCellWidth(m: Metrics): Float = m.s(36f)

    /** 动作条每格的高。 */
    fun actionCellHeight(m: Metrics): Float = m.s(40f)

    // ==================== 底部信息（距底锚定）====================

    /** UP 行中线距底。 */
    fun authorRowOffsetFromBottom(m: Metrics): Float = m.s(68f)

    /** 标题基线距底。 */
    fun titleOffsetFromBottom(m: Metrics): Float = m.s(52f)

    /** 播放量距底。 */
    fun viewsOffsetFromBottom(m: Metrics): Float = m.s(34f)

    /** 进度条中线距底（280 高时 y=266）。 */
    fun progressOffsetFromBottom(m: Metrics): Float = m.s(14f)

    /** 底部信息区的左右留白。 */
    fun contentInset(m: Metrics): Float = m.s(12f)

    /** 底部信息区右侧要避让动作条的宽度。 */
    fun contentRightInset(m: Metrics): Float = m.s(56f)

    fun authorCenterY(m: Metrics): Float = m.heightDp - authorRowOffsetFromBottom(m)
    fun titleTop(m: Metrics): Float = m.heightDp - titleOffsetFromBottom(m)
    fun viewsTop(m: Metrics): Float = m.heightDp - viewsOffsetFromBottom(m)
    fun progressCenterY(m: Metrics): Float = m.heightDp - progressOffsetFromBottom(m)

    /** 头像直径。 */
    fun avatarSize(m: Metrics): Float = m.s(22f)

    // ==================== 底部信息区字号（sp）====================

    fun authorTextSize(m: Metrics): Float = 11f * m.scale
    fun titleTextSize(m: Metrics): Float = 12f * m.scale
    fun viewsTextSize(m: Metrics): Float = 10f * m.scale
    fun clockTextSize(m: Metrics): Float = 11f * m.scale
    fun actionLabelSize(m: Metrics): Float = 9f * m.scale

    // ==================== 进度条 ====================

    /** 常驻态轨道粗细。 */
    fun progressTrackHeight(m: Metrics): Float = m.s(2f)

    /** 拖动时轨道粗细。 */
    fun progressTrackHeightDragging(m: Metrics): Float = m.s(4f)

    /** 沉浸态的细进度线粗细。 */
    fun progressTrackHeightIdle(m: Metrics): Float = m.s(1.5f)

    /** 进度条左右留白。 */
    fun progressInset(m: Metrics): Float = m.s(14f)

    /**
     * 进度条的热区高度（可拖范围）：设计稿要求上下各扩 11dp，共 24。
     * 屏幕越大热区越大，但不会大到盖住上面的播放量文字。
     */
    fun progressTouchHeight(m: Metrics): Float =
        m.s(24f).coerceAtMost(titleOffsetFromBottom(m) - progressOffsetFromBottom(m) - m.s(4f))

    // ==================== 中心播放键 ====================

    /** 中心播放键直径。 */
    fun playSize(m: Metrics): Float = m.s(56f)

    /** 中心播放键图标。 */
    fun playIconSize(m: Metrics): Float = m.s(24f)

    /** 播放键中心：屏幕正中。 */
    fun playCenterX(m: Metrics): Float = m.centerX
    fun playCenterY(m: Metrics): Float = m.centerY

    // ==================== 面板 ====================

    /** 面板顶部圆角。 */
    fun panelCorner(m: Metrics): Float = m.s(18f)

    /** 面板最大宽度（手机上不铺满整屏，留出边距）。 */
    fun panelMaxWidth(m: Metrics): Float = m.s(420f).coerceAtMost(m.widthDp)

    /**
     * 面板贴底时的高度。设计稿给了四档（作者卡 220 / 简介评论 66%·94% / 收藏夹 176 / 更多 232），
     * 这里统一按「占屏高比例」表达，屏幕变高时面板跟着长，不会出现手表上只有一条缝。
     *
     * @param kind 见 [PanelKind]
     */
    fun panelHeight(m: Metrics, kind: PanelKind): Float {
        val maxH = m.heightDp * 0.92f
        val raw = when (kind) {
            PanelKind.AUTHOR -> m.heightDp * 0.52f
            PanelKind.DETAIL -> m.heightDp * 0.66f
            PanelKind.DETAIL_EXPANDED -> m.heightDp * 0.94f
            PanelKind.FOLDER -> m.heightDp * 0.45f
            PanelKind.MORE -> m.heightDp * 0.60f
        }
        // 先按比例算，再用一个「不小于某绝对值、但不超出屏高」的区间夹住。
        // 下限不能比上限还大（矮屏上会出现 empty range），所以下限取两者的较小值。
        val minH = minOf(m.s(180f), maxH)
        return raw.coerceIn(minH, maxH)
    }

    enum class PanelKind { AUTHOR, DETAIL, DETAIL_EXPANDED, FOLDER, MORE }

    /**
     * 圆屏上的面板内缩：整圆弹层会被外层圆再切一刀，四角文字必挂，
     * 所以设计稿要求内缩后做成圆角方块。这里按半径比例算，任意表盘都成立。
     */
    fun roundPanelInset(m: Metrics): Float = m.shortSide * 0.11f

    // ==================== 「更多」网格 ====================

    /** 「更多」网格格宽（设计稿 moreBody：方屏 64 / 圆屏 46）。 */
    fun moreCellWidth(m: Metrics): Float = m.s(if (m.round) 46f else 64f)

    /** 「更多」网格格高（设计稿 moreBody：方屏 58 / 圆屏 46）。 */
    fun moreCellHeight(m: Metrics): Float = m.s(if (m.round) 46f else 58f)

    /** 「更多」网格间距（设计稿 moreBody：方屏 6 / 圆屏 4）。 */
    fun moreCellGap(m: Metrics): Float = m.s(if (m.round) 4f else 6f)

    // ==================== 圆屏极坐标 ====================

    /** 圆屏动作条所在半径（相对屏心）。 */
    fun roundActionRadius(m: Metrics): Float = m.shortSide / 2f * 0.77f

    /** 圆屏动作条三个角度（度，从 3 点钟逆时针）。 */
    val ROUND_ACTION_ANGLES = listOf(27f, 0f, -27f)

    /** 圆屏上某个角度、半径处的落点。 */
    fun roundPoint(m: Metrics, radius: Float, degrees: Float): Pair<Float, Float> {
        val rad = Math.toRadians(degrees.toDouble())
        return (m.centerX + radius * kotlin.math.cos(rad)).toFloat() to
            (m.centerY - radius * kotlin.math.sin(rad)).toFloat()
    }

    /**
     * 圆屏顶部返回 / ⋯ 的落点。
     *
     * 设计稿圆屏规格表：直径 233 时中心在 (58, 42) / (175, 42)。换算成极坐标：
     * 半径 ≈ 94.7 = R × 0.813，角度左 128° / 右 52°（y=42 处弦宽 179，热区放得下）。
     * 半径一律按 shortSide 比例表达，192dp / 227dp 等真实表盘照样成立。
     */
    fun roundTopPoint(m: Metrics, right: Boolean): Pair<Float, Float> =
        roundPoint(m, m.shortSide / 2f * 0.813f, if (right) 52f else 128f)

    /**
     * 圆屏顶部压暗渐变高度（设计稿 feedLayer 圆屏 st=64 @ 直径 233）。
     * 也用作圆屏顶栏容器高：返回 / ⋯ 中心 y=42、热区 36，底沿 60 < 64，不会被裁。
     */
    fun roundTopBarHeight(m: Metrics): Float = m.s(64f)

    /** 圆屏底部压暗渐变高度（设计稿圆屏 sb=96 @ 直径 233）。 */
    fun roundBottomScrimHeight(m: Metrics): Float = m.s(96f)

    /** 圆屏时钟中线 y（设计稿 y=16 @ 直径 233，表盘习惯放正上方）。 */
    fun roundClockCenterY(m: Metrics): Float = m.shortSide * (16f / 233f)

    // ==================== 圆屏底部信息（水平居中，距底锚定）====================
    //
    // 设计稿：UP 行 / 标题 / 播放量中线 y=170 / 186 / 202 @ 直径 233，
    // 换算成距底 63 / 47 / 31，再按 shortSide 比例缩放——192dp / 227dp 表盘照样成立。

    fun roundAuthorCenterY(m: Metrics): Float = m.heightDp - m.shortSide * (63f / 233f)
    fun roundTitleCenterY(m: Metrics): Float = m.heightDp - m.shortSide * (47f / 233f)
    fun roundViewsCenterY(m: Metrics): Float = m.heightDp - m.shortSide * (31f / 233f)

    /** 圆屏底部三行的最宽（设计稿 140 / 140 / 110，y=202 处弧内侧弦宽约 141）。 */
    fun roundAuthorMaxWidth(m: Metrics): Float = m.s(140f)
    fun roundTitleMaxWidth(m: Metrics): Float = m.s(140f)
    fun roundViewsMaxWidth(m: Metrics): Float = m.s(110f)

    /** 圆屏头像直径（设计稿 18）。 */
    fun roundAvatarSize(m: Metrics): Float = m.s(18f)

    /** 圆屏底部字号（设计稿 UP 10 / 标题 11 / 播放量 9 / 时钟 10sp）。 */
    fun roundAuthorTextSize(m: Metrics): Float = 10f * m.scale
    fun roundTitleTextSize(m: Metrics): Float = 11f * m.scale
    fun roundViewsTextSize(m: Metrics): Float = 9f * m.scale
    fun roundClockTextSize(m: Metrics): Float = 10f * m.scale

    /**
     * 圆屏面板：内缩后做成圆角方块（设计稿 181×181 @ 直径 233）。
     * 整圆弹层会被外层圆再切一刀，四角文字必挂，所以宽高都取内接正方形边长。
     */
    fun roundPanelSize(m: Metrics): Float =
        (m.shortSide - 2f * roundPanelInset(m)).coerceAtMost(m.heightDp * 0.92f)

    /** 圆屏面板四角圆角（设计稿 28，四角圆心离屏心 88.4+28≈116.4，正好落在表圈内）。 */
    fun roundPanelCorner(m: Metrics): Float = m.s(28f)

    // ==================== 圆屏进度弧 ====================

    /** 圆屏进度弧半径（设计稿 r=111 @ R=116.5，贴表圈、不占内容区）。 */
    fun roundArcRadius(m: Metrics): Float = m.shortSide / 2f * (111f / REF_ROUND_RADIUS)

    /** 进度弧起点（数学角，3 点钟逆时针）：225°，屏幕左下。 */
    const val ROUND_ARC_START_DEGREE = 225f

    /** 进度弧扫角：225° → 315°（经过 270° 正下方），共 90°。 */
    const val ROUND_ARC_SWEEP_DEGREE = 90f

    /** 进度弧的可拖带宽（半径方向上下各 12dp，与长视频 PlayerArcProgressView 同口径）。 */
    fun roundArcTouchBand(m: Metrics): Float = m.s(12f)

    /**
     * 触点 → 进度（纯函数）。角度换算与长视频 [com.RobinNotBad.BiliClient.player.PlayerArcProgressView]
     * 同一套：atan2 取屏幕坐标得到 Canvas 角（顺时针为正），再算「从起点沿弧走了多远」，
     * 缺口区按最近端点吸附。短视频弧只有 90°（225→315），与长视频 140° 不同，
     * 所以起点 / 扫角做成参数而不是写死。
     */
    fun arcFraction(
        centerX: Float,
        centerY: Float,
        x: Float,
        y: Float,
        startDegree: Float = ROUND_ARC_START_DEGREE,
        sweepDegree: Float = ROUND_ARC_SWEEP_DEGREE,
    ): Float {
        if (sweepDegree <= 0f) return 0f
        var angle = Math.toDegrees(kotlin.math.atan2((y - centerY).toDouble(), (x - centerX).toDouble())).toFloat()
        if (angle < 0f) angle += 360f
        val distance = ((360f - startDegree - angle) % 360f + 360f) % 360f
        val swept = when {
            distance <= sweepDegree -> distance
            distance - sweepDegree < 360f - distance -> sweepDegree
            else -> 0f
        }
        return (swept / sweepDegree).coerceIn(0f, 1f)
    }

    // ==================== 沉浸态细进度线 ====================

    /**
     * 沉浸态细进度线中线距底（方屏）。设计稿 squareNew 的 thin 分支：
     * 热区 top 262 + 轨道偏移 11 + 半高 0.75 = 273.75 @ 280 高，即距底 6.25dp，
     * 比常驻进度条（距底 14）更贴底边。圆屏不用它——沉浸时弧本身变细。
     */
    fun progressIdleOffsetFromBottom(m: Metrics): Float = m.s(6.25f)
}
