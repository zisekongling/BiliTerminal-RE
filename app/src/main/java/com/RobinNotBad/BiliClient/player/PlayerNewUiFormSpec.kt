package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SettingsKeys
import kotlin.math.sqrt

/**
 * 新版播放器方屏布局的几何规格（纯函数，可单测）。坐标单位 dp，原点在根布局左上角。
 *
 * 设计稿（`design/player-redesign-v2.html` 方屏规格）写明「以 240×280dp 为例，全部按边缘锚定，
 * 其它尺寸照样成立」。旧实现把底部元素写成距**顶**的固定值（进度行 top=208、底栏 y=252），
 * 屏幕一高一矮就错位。这里把每个元素换算成相对它所锚定那条边的距离：
 *
 * - 顶部（顶栏、渐变、锁定提示）：距顶；
 * - 左右两列：以屏幕竖直中线为基准，上下各 44（240×280 时正好是 96 / 140 / 184）；
 * - 进度行、底栏、沉浸细线：距底（280 时正好是 222 / 252 / 272）；
 * - 「更多」网格与拖动预览：以屏幕中心为基准。
 *
 * 三种形态（设置 → 播放器界面形态，手动切换）：
 * - 手表：scale = 1，与设计稿逐项一致；
 * - 手机：同一套锚点乘 [Spec.scale] 放大；播放键参照 B 站横屏全屏放进底栏最左（[Spec.playInRow]），
 *   画面中央不再压一个大圆键；
 * - 长条屏（词典笔一类，高度约 100dp）：竖直方向放不下左右两列，两列的按钮并入底栏，
 *   底栏放不下的进「更多」；中心播放键同样并入底栏。
 *
 * 圆屏只存在于手表形态，几何仍由 [PlayerNewUiGeometry] 的极坐标负责。
 */
object PlayerNewUiFormSpec {

    const val FORM_WATCH = SettingsKeys.PLAYER_FORM_WATCH
    const val FORM_PHONE = SettingsKeys.PLAYER_FORM_PHONE
    const val FORM_STRIP = SettingsKeys.PLAYER_FORM_STRIP

    /** 设计稿方屏参考尺寸。 */
    const val REF_WIDTH = 240f
    const val REF_HEIGHT = 280f

    /** 手表上手指能可靠点中的最小热区边长（dp），不随形态缩小。 */
    const val MIN_TOUCH_DP = 24f

    data class Spec(
        val form: String,
        /** 尺寸放大系数：热区、图标、字号、间距都乘它。 */
        val scale: Float,
        /** 播放键并入底栏最左（手机 / 长条屏），否则在画面中心（手表）。 */
        val playInRow: Boolean,
        /** 是否有左右两列；长条屏没有，两列的按钮并入底栏。 */
        val sideColumns: Boolean,
        /** 底栏最多几格（不含并入的播放键），超出进「更多」。 */
        val bottomMaxSlots: Int,
    ) {
        fun s(value: Float) = value * scale

        // ---- 尺寸 ----
        val buttonSize get() = s(36f)
        val iconSize get() = s(20f)
        val playSize get() = if (playInRow) buttonSize else s(56f)
        val playIconSize get() = if (playInRow) iconSize + s(2f) else s(24f)
        val topBarHeight get() = s(44f)
        val scrimTop get() = s(66f)
        val scrimBottom get() = s(110f)
        val textSmall get() = s(10f)
        val textTitle get() = s(12f)

        // ---- 左右两列 ----
        val sideX get() = s(26f)
        fun sideYs(height: Float): List<Float> = listOf(-44f, 0f, 44f).map { height / 2f + s(it) }
        /** 右列的中心 x：镜像左列。 */
        fun sideRightX(width: Float) = width - sideX

        // ---- 进度行（中线距底 58）----
        fun progressCenterY(height: Float) = height - s(58f)
        val timeLeft get() = s(12f)
        val timeWidth get() = s(36f)
        val timeHeight get() = s(14f)
        fun timeTop(height: Float) = progressCenterY(height) - timeHeight / 2f
        /** 轨道两端各留 54（240 宽时轨道 132）。 */
        val trackInset get() = s(54f)
        val trackRowHeight get() = s(24f)
        /** 进度条控件顶边：渲染器里轨道中线在控件内 14dp 处。 */
        fun trackTop(height: Float) = progressCenterY(height) - s(14f)

        // ---- 底栏（中线距底 28，中心间距 48）----
        fun bottomY(height: Float) = height - s(28f)
        val bottomSpacing get() = s(48f)

        // ---- 沉浸细进度线（顶边距底 8，左右各 30）----
        fun idleTop(height: Float) = height - s(8f)
        val idleInset get() = s(30f)
        val idleHeight get() = s(2f)
        /** 细进度线整条宽度：左右各留 [idleInset]，再窄也不小于 60。 */
        fun idleWidth(width: Float) = (width - idleInset * 2f).coerceAtLeast(s(60f))

        // ---- 拖动预览三行（240×280 时 top 100 / 116 / 156）----
        fun seekRows(height: Float): List<Float> = listOf(-40f, -24f, 16f).map { height / 2f + s(it) }
        val seekDeltaHeight get() = s(14f)
        val seekMainHeight get() = s(36f)
        val seekTotalHeight get() = s(13f)
        val seekMainText get() = s(30f)

        /**
         * 拖动预览缩略图：宽取屏宽 40%、高 16:9，贴在三行文字**上方**。
         *
         * 间隙只有 2dp：参考尺寸下顶栏区块高 44、首行 100，中间只有 56dp，
         * 而 40% 宽的图正好 54dp 高——间隙给 4dp 就会顶进顶栏。下方也不考虑，
         * 三行末尾与进度条之间的空档在矮屏上会被压没。
         */
        fun seekThumbWidth(width: Float): Float = width * PlayerNewUiGeometry.SEEK_THUMB_WIDTH_RATIO
        fun seekThumbHeight(width: Float): Float = seekThumbWidth(width) * 9f / 16f
        fun seekThumbTop(width: Float, height: Float): Float =
            seekRows(height)[0] - s(2f) - seekThumbHeight(width)

        /** 这块空间够不够放下缩略图（长条屏、极窄屏会不够，UI 层据此决定显示或跳过）。 */
        fun seekThumbFits(width: Float, height: Float): Boolean =
            seekThumbTop(width, height) >= topBarHeight

        // ---- 「更多」网格（格 64×58、间距 6，整块 204×186；240×280 时 top 46）----
        val gridCellW get() = s(64f)
        val gridCellH get() = s(58f)
        val gridGap get() = s(6f)
        val gridW get() = gridCellW * 3 + gridGap * 2
        val gridH get() = gridCellH * 3 + gridGap * 2
        /**
         * 「更多」网格的顶边：竖直**居中**。
         *
         * 以前按「屏幕中心 - 93」锚定，那是以 280 高为参考算的；横屏（368dp 高）时
         * 网格顶在 90 左右、整块 186 高，底边压到 276，底部就空出一大截，看起来偏低。
         * 改成让整块网格在可用高度里居中，任何比例都不会偏。
         */
        fun gridTop(height: Float) = ((height - gridH) / 2f).coerceAtLeast(topBarHeight)
        fun gridHintTop(height: Float) = gridTop(height) + gridH + s(12f)

        /**
         * 「更多」右侧页码点的排布。
         *
         * 设计稿把点画成 8dp、间距 12dp，但那是**视觉**尺寸：手表手指粗，12dp 间距塞不下
         * 24dp 热区，相邻热区一重叠就会点错页。所以间距至少一个热区，可用高度富余时最多
         * 放宽到 1.5 倍热区（再宽就与网格不成比例）。高度不够也不缩热区——点不中比整列
         * 略高一点更糟，面板整块居中，高一点仍在屏内。
         */
        fun gridDots(pageCount: Int, availableH: Float): GridDots {
            val count = pageCount.coerceAtLeast(1)
            val hot = maxOf(MIN_TOUCH_DP, s(24f))
            val spacing = if (count <= 1) hot else maxOf(hot, minOf(availableH / count, hot * 1.5f))
            return GridDots(hot = hot, spacing = spacing, dot = s(8f), dotActive = s(12f), height = spacing * count)
        }

        /** 页码点列与网格之间的留白：热区变宽后这里必须收窄，否则圆屏上整块会顶出表圈。 */
        val gridDotsMargin get() = s(2f)

        // ---- 清晰度行的大会员徽标（设计稿 .vip：8sp、左右 4dp、圆角 3、1dp 描边）----
        val qualityBadgeText get() = s(8f)
        val qualityBadgeHeight get() = s(12f)
        val qualityBadgePadH get() = s(4f)
        val qualityBadgeMargin get() = s(4f)
        val qualityBadgeRadius get() = s(3f)

        /**
         * 清晰度行里档位名能占的最大宽度：屏宽扣掉行边距、徽标与勾。
         *
         * 名字过长时让**名字**出省略号，而不是把徽标或勾挤出屏幕——设计稿要求徽标紧跟名字，
         * 两者都得看得见。圆屏整行居中、方屏是底部抽屉，两侧留白不一样，所以分开算。
         */
        fun qualityLabelMaxWidth(screenWidth: Float, hasBadge: Boolean, hasCheck: Boolean, centered: Boolean): Float {
            val inset = if (centered) s(24f) else s(40f)
            val badge = if (hasBadge) s(40f) else 0f
            val check = if (hasCheck) (if (centered) s(20f) else s(32f)) else 0f
            return (screenWidth - inset - badge - check).coerceAtLeast(s(48f))
        }

        // ---- 清晰度 / 列表底部弹层（240×280 时 top 96，即距底 184）----
        fun sheetTop(height: Float) = (height - s(184f)).coerceAtLeast(topBarHeight)

        // ---- 锁定提示 ----
        val lockHintTop get() = s(16f)

        /** 底栏一行能放几格（含播放键）：整行居中时最外侧热区不越界。 */
        fun bottomCells(width: Float) =
            ((width - buttonSize) / bottomSpacing).toInt() + 1

        /**
         * 底栏落点的最终形态：播放键的 x（不在底栏时为 null）、左半槽位的 x、右半槽位的 x。
         *
         * 并入底栏时**播放键固定在屏幕正中**，槽位按 `bottomLeft` / `bottomRight` 分列两侧——
         * 这正好对上布局存档的语义（底左＝播放键左边、底右＝播放键右边）。左右两侧从中心
         * 各向外按 [bottomSpacing] 排开，两边不对称时整行不再居中，但播放键本身始终不动。
         *
         * 分开返回是刻意的——调用方若自己按 `bottomXs(index)` 取，会把并入的播放键那一格
         * 一起算进槽位里，第一个槽位正好压在播放键上。这里把错位挡在源头。
         */
        fun bottomPlacement(leftCount: Int, rightCount: Int, width: Float): BottomPlacement {
            val center = width / 2f
            if (!playInRow) {
                // 手表：播放键在画面中心，底栏整行居中（设计稿口径，一格都不改）。
                val total = leftCount + rightCount
                val xs = (0 until total).map { center + (it - (total - 1) / 2f) * bottomSpacing }
                return BottomPlacement(playX = null, leftXs = xs.take(leftCount), rightXs = xs.drop(leftCount))
            }
            // 左半从中心往左数：紧邻播放键的那格是 leftCount-1。
            val left = (0 until leftCount).map { center - (leftCount - it) * bottomSpacing }
            val right = (1..rightCount).map { center + it * bottomSpacing }
            return BottomPlacement(playX = center, leftXs = left, rightXs = right)
        }

        /**
         * 底栏能放几个槽位（不含播放键），受 [bottomMaxSlots] 与屏幕宽度双重约束。
         * 播放键占正中一格，所以两侧各有 (总格数-1)/2 的空间。
         */
        fun bottomSlotCount(width: Float) =
            (bottomCells(width) - if (playInRow) 1 else 0).coerceIn(0, bottomMaxSlots)
    }

    /** 底栏落点，见 [Spec.bottomPlacement]。 */
    data class BottomPlacement(val playX: Float?, val leftXs: List<Float>, val rightXs: List<Float>) {
        /** 所有槽位（左半在前），手表形态直接用这个顺序。 */
        val slotXs: List<Float> get() = leftXs + rightXs
    }

    /** 页码点落点，见 [Spec.gridDots]。[spacing] ≥ [hot] 保证相邻热区互不重叠。 */
    data class GridDots(
        val hot: Float,
        val spacing: Float,
        val dot: Float,
        val dotActive: Float,
        val height: Float,
    )

    /**
     * 「更多」九宫格整块的自适应落点（纯函数，可单测），见 [gridFit]。
     *
     * 只缩「网格一套」（格子、间距、标题/提示字号、页码点视觉），**不动全局 [Spec.scale]**，
     * 顶栏、底栏、进度行等其余界面元素不受影响。所有字段单位 dp、原点根布局左上角。
     */
    data class GridFit(
        /** 网格专属缩放（≤1），叠在形态系数之上。 */
        val fitScale: Float,
        /** 格子内部元素（图标 20 / 标签 9sp / 行高 15 / 间距 4 / 圆角 10）统一乘的系数。 */
        val unit: Float,
        val cellW: Float,
        val cellH: Float,
        val gap: Float,
        /** 满 3 行网格块的宽高（实际行数更少时高度由调用方按行重算）。 */
        val gridW: Float,
        val gridH: Float,
        val gridLeft: Float,
        val gridTop: Float,
        /** 居中容器（网格 + 提示）的可用竖直区域。 */
        val regionTop: Float,
        val regionH: Float,
        /** 扣掉提示区后喂给 gridCapacity 算行数的高度。 */
        val availH: Float,
        val titleTop: Float,
        val titleH: Float,
        val titleText: Float,
        val titleLeft: Float,
        /** 标题宽度；圆屏为 -1 表示 wrap_content 居中。 */
        val titleWidth: Float,
        val hintGap: Float,
        val hintH: Float,
        val hintText: Float,
        val dots: GridDots,
        val dotsLeft: Float,
        val dotsTop: Float,
        /** 方屏页码点在行内 CENTER_VERTICAL 之外额外下移的量（躲收起按钮）。 */
        val dotsShift: Float,
        /** 收起按钮（仅方屏）：热区 ≥24dp 不缩；圆屏三个字段为 0。 */
        val collapseSize: Float,
        val collapseLeft: Float,
        val collapseTop: Float,
        /** 极矮屏（长条屏一类）收起按钮挪到右下角，否则它会把点列顶出提示区。 */
        val collapseAtBottom: Boolean,
        val round: Boolean,
        val screenW: Float,
        val screenH: Float,
    ) {
        /** 网格块竖直中心：与行数无关（行数变化只改 gridTop，中心不动）。 */
        val gridCenterY get() = regionTop + (regionH - hintGap - hintH) / 2f

        /** 按实际页数重算点列（spacing 不变，高度随页数）。 */
        fun dotsFor(pageCount: Int): GridDots =
            dots.copy(height = dots.spacing * pageCount.coerceAtLeast(1))

        /** 实际页数下点列顶边：圆屏对屏中心居中，方屏对网格中心居中且不压收起按钮。 */
        fun dotsTopFor(pageCount: Int): Float {
            val h = dotsFor(pageCount).height
            return if (round) screenH / 2f - h / 2f else maxOf(gridCenterY - h / 2f, dotsTopMin)
        }

        /** 方屏点列被收起按钮顶下去时的行内下移量。 */
        fun dotsShiftFor(pageCount: Int): Float =
            if (round) 0f else (dotsTopFor(pageCount) - (gridCenterY - dotsFor(pageCount).height / 2f)).coerceAtLeast(0f)

        /** 方屏点列顶边的下限（收起按钮底边 + 2）；收起挪到底部时为 0。 */
        val dotsTopMin: Float
            get() = if (round || collapseAtBottom) 0f else collapseTop + collapseSize + 2f * unit
    }

    /**
     * 「更多」九宫格在任意屏上的自适应几何。设计稿 `design/player-redesign-v2.html` morePanel()：
     *
     * - 方屏参考 240×280：格 64×58 / 间距 6 → 块 204×186；标题 top16 行高18 13sp；
     *   提示 top244 行高12 9sp；收起 36dp 热区中心 (214,25)；页码点视觉 8dp、间距 12。
     * - 圆屏参考 233dp：格 46×46 / 间距 4 → 块 146×146，left43.5 top42（内接正方形）；
     *   标题 top20 行高16 12sp；提示 top196；**无收起按钮**；点列在网格右侧。
     *
     * 实测根因（本轮修复对象）：192dp 方屏格子不缩、右边裁掉 12dp、标题压第一格、收起压时钟、
     * 页码点/提示出屏；233dp 圆屏网格中心 (106.5,104.5) 偏离屏中心 (116.5,116.5)，
     * 左上角距圆心 118.8 > 半径 116.5 被表圈切掉。
     *
     * 做法：整块（含标题区/提示区/底边距）按 min(1, 各约束) 等比缩，只缩网格一套；
     * 方屏 fitH = H/(280·u)、fitW = (W−热区)/(214·u)；圆屏 fitH = D/233，另加
     * 「四角在表圈内」与「点列最坏情况在表圈内」两条约束。热区恒 ≥ [MIN_TOUCH_DP]，
     * 只缩视觉点与间距。网格水平居中（圆屏对**屏幕中心**居中，修中心偏移），
     * 竖直在「标题区以下、底边距以上」的区域里连提示一起居中。
     *
     * [pageCount] 传页数估计值（按每页 9 格估）；行数算出来 ≥3 时实际页数 ≤ 估计值，
     * 点列约束偏保守、安全。实际页数确定后用 [GridFit.dotsFor] 等重算。
     */
    fun gridFit(spec: Spec, round: Boolean, width: Float, height: Float, pageCount: Int): GridFit {
        val count = pageCount.coerceAtLeast(1)
        val u = if (round) 1f else spec.scale
        val hot = maxOf(MIN_TOUCH_DP, 24f * u)
        val margin0 = 2f
        // ---- 设计稿基准尺寸（未缩）----
        val cw0 = if (round) 46f else 64f
        val ch0 = if (round) 46f else 58f
        val gap0 = if (round) 4f else 6f
        val gw0 = cw0 * 3 + gap0 * 2
        val gh0 = ch0 * 3 + gap0 * 2
        val titleTop0 = if (round) 20f else 16f
        val titleH0 = if (round) 16f else 18f
        val titleGap0 = if (round) 6f else 12f
        val hintGap0 = if (round) 8f else 12f
        val hintH0 = 12f
        val bottomPad0 = if (round) 25f else 24f
        val titleZone0 = titleTop0 + titleH0 + titleGap0
        val hintZone0 = hintGap0 + hintH0
        val reserve0 = titleZone0 + hintZone0 + bottomPad0 // 圆 233 / 方 280 的竖直总预留

        // ---- 缩放系数 ----
        var fit = (height / (reserve0 * u + gh0 * u)).coerceAtMost(1f)
        if (round) {
            val r = width / 2f
            val pad = 6f // 表圈内留 6dp，不贴圈
            // 四角约束：网格中心比屏中心高 1.5·fit（设计 top42 的不对称预留），半对角线随之。
            val halfDiag = sqrt((gw0 / 2f) * (gw0 / 2f) + (gh0 / 2f + 1.5f) * (gh0 / 2f + 1.5f))
            fit = minOf(fit, ((r - pad) / halfDiag).coerceAtLeast(0.1f))
            // 点列约束（最坏 spacing=hot）：点列右缘 x = 75·fit + hot，上下半高 hot·count/2。
            val dotsHalf = hot * count / 2f
            val room = (r - pad) * (r - pad) - dotsHalf * dotsHalf
            val reach = if (room > 0f) sqrt(room) else 0f
            fit = minOf(fit, ((reach - hot) / (gw0 / 2f + margin0)).coerceAtLeast(0.1f))
        } else {
            // 整行（网格 + 留白 + 点列热区）左右各留 4·u，热区不缩所以从屏宽里先扣掉。
            fit = minOf(fit, ((width - hot) / ((gw0 + margin0 + 8f) * u)).coerceAtLeast(0.1f))
        }

        // ---- 缩放后的落点 ----
        val cellW = cw0 * u * fit
        val cellH = ch0 * u * fit
        val gap = gap0 * u * fit
        val gridW = cellW * 3 + gap * 2
        val gridH = cellH * 3 + gap * 2
        val titleZone = titleZone0 * u * fit
        val hintZone = hintZone0 * u * fit
        val bottomPad = bottomPad0 * u * fit
        val regionTop = titleZone
        val regionH = height - titleZone - bottomPad
        // fit ≤ fitH 保证 3 行网格数学上放得下；max 只纠浮点噪声（边界尺寸上
        // regionH-hintZone 可能比 gridH 小 1e-5，不纠就会把行数算成 2、页数估计失守）。
        val availH = maxOf(regionH - hintZone, gridH)
        val gridTop = regionTop + ((regionH - gridH - hintZone) / 2f).coerceAtLeast(0f)
        val margin = margin0 * u * fit
        // 圆屏网格对屏幕中心水平居中；方屏整行（网格+点列）居中。
        val gridLeft = if (round) (width - gridW) / 2f else (width - (gridW + margin + hot)) / 2f
        val titleTop = titleTop0 * u * fit
        val titleH = titleH0 * u * fit
        val hintGap = hintGap0 * u * fit
        val hintH = hintH0 * u * fit
        val hintTop = gridTop + gridH + hintGap

        // ---- 收起按钮（方屏）：顶栏时钟底边 32·u 以下，热区 ≥24 不缩 ----
        val collapseSize = if (round) 0f else maxOf(MIN_TOUCH_DP, 24f * u)
        var collapseTop = if (round) 0f else maxOf(32f * u, titleTop + titleH)
        // 极矮屏：收起在顶部会把点列顶进提示区，挪到右下角（仍在时钟底边以下）。
        val dotsTopMinIfTop = collapseTop + collapseSize + 2f * u * fit
        val collapseAtBottom = !round && dotsTopMinIfTop + hot * count > hintTop - 2f * u * fit
        if (collapseAtBottom) collapseTop = height - collapseSize - 2f * u * fit
        val collapseLeft = if (round) 0f else width - 4f * u - collapseSize
        val dotsTopMin = if (round || collapseAtBottom) 0f else collapseTop + collapseSize + 2f * u * fit

        // ---- 页码点：热区不缩，视觉与间距随 fit；圆屏再受表圈弦长约束 ----
        val spacingCap = if (round) {
            val r = width / 2f
            val pad = 6f
            val dx = (gw0 / 2f + margin0) * fit + hot // 点列右缘到屏中心的水平距离
            val room = (r - pad) * (r - pad) - dx * dx
            if (room > 0f) 2f * sqrt(room) / count else hot
        } else {
            (height - bottomPad - hintZone - dotsTopMin) / count
        }
        val spacing = maxOf(hot, minOf(gridH / count, hot * 1.5f, spacingCap))
        val dots = GridDots(hot = hot, spacing = spacing, dot = 8f * u * fit, dotActive = 12f * u * fit, height = spacing * count)
        val dotsLeft = gridLeft + gridW + margin
        val gridCenterY = regionTop + (regionH - hintZone) / 2f
        val dotsTop = if (round) height / 2f - dots.height / 2f else maxOf(gridCenterY - dots.height / 2f, dotsTopMin)
        val dotsShift = if (round) 0f else (dotsTop - (gridCenterY - dots.height / 2f)).coerceAtLeast(0f)

        // ---- 标题：方屏左对齐网格、右缘不压收起；圆屏 wrap 居中 ----
        val titleRight = if (collapseAtBottom) width - 4f * u * fit else collapseLeft - 4f * u * fit
        val titleWidth = if (round) -1f else (titleRight - gridLeft).coerceAtLeast(48f * u * fit)

        return GridFit(
            fitScale = fit,
            unit = u * fit,
            cellW = cellW, cellH = cellH, gap = gap,
            gridW = gridW, gridH = gridH,
            gridLeft = gridLeft, gridTop = gridTop,
            regionTop = regionTop, regionH = regionH, availH = availH,
            titleTop = titleTop, titleH = titleH,
            titleText = (if (round) 12f else 13f) * u * fit,
            titleLeft = gridLeft, titleWidth = titleWidth,
            hintGap = hintGap, hintH = hintH, hintText = 9f * u * fit,
            dots = dots, dotsLeft = dotsLeft, dotsTop = dotsTop, dotsShift = dotsShift,
            collapseSize = collapseSize, collapseLeft = collapseLeft, collapseTop = collapseTop,
            collapseAtBottom = collapseAtBottom,
            round = round, screenW = width, screenH = height,
        )
    }

    val WATCH = Spec(FORM_WATCH, scale = 1f, playInRow = false, sideColumns = true, bottomMaxSlots = 5)

    /** 手机：手表的 1.4 倍（36 → 50dp 热区，接近 B 站横屏全屏的按钮尺寸）。 */
    val PHONE = Spec(FORM_PHONE, scale = 1.4f, playInRow = true, sideColumns = true, bottomMaxSlots = 6)

    /** 长条屏：缩到 0.8，左右两列并入底栏。 */
    val STRIP = Spec(FORM_STRIP, scale = 0.8f, playInRow = true, sideColumns = false, bottomMaxSlots = 6)

    fun of(form: String?): Spec = when (form) {
        FORM_PHONE -> PHONE
        FORM_STRIP -> STRIP
        else -> WATCH
    }

    /** 读取设置（默认手表）。改形态要重进播放器才生效，与本页其它界面设置同口径。 */
    fun current(form: String?): Spec = of(form)

    /**
     * 把「当前这个视频用不上」的槽位从布局里摘掉。
     *
     * 为什么必须做：槽位布局是用户存档的，不会随视频变；而按钮本体是 Activity 的常驻字段，
     * 单 P 视频时 `btn_page_selector` 会被置 GONE。如果照存档摆位，那一格仍然占着位置，
     * 表现就是**底栏右侧空出一格**（用户反馈的「最后一个按钮右边空得太多」）——
     * 圆的、方的、手机的一个都跑不掉，所以三种形态共用这一个判据。
     *
     * 判据只看「按钮本体是否存在且不是 GONE」。不用 `isShown`：它在页面还没上屏时恒为 false，
     * 会把整条底栏都判没。
     */
    fun visibleOnly(
        layout: PlayerUiLayout.Layout,
        isVisible: (String) -> Boolean,
    ): PlayerUiLayout.Layout {
        fun keep(slot: PlayerUiLayout.Slot): Boolean = when (slot) {
            is PlayerUiLayout.Slot.Button -> isVisible(slot.key)
            // 组本身永远保留：它是「更多」的入口，里面装着什么由网格那边逐格判可见。
            is PlayerUiLayout.Slot.Group -> true
        }
        return PlayerUiLayout.Layout(
            left = layout.left.filter(::keep),
            right = layout.right.filter(::keep),
            bottomLeft = layout.bottomLeft.filter(::keep),
            bottomRight = layout.bottomRight.filter(::keep),
        )
    }

    /**
     * 这一屏能放下哪些槽位、哪些要进「更多」。
     *
     * 顺序与设计稿一致：左列、右列、底栏，各自按上限截断，剩下的按原顺序追加进「更多」，
     * 不丢按钮。没有左右两列的长条屏，两列整段进底栏。
     */
    data class Plan(
        val left: List<PlayerUiLayout.Slot>,
        val right: List<PlayerUiLayout.Slot>,
        /** 底栏里落在播放键**左边**的槽位（存档的 bottomLeft）。 */
        val bottomLeft: List<PlayerUiLayout.Slot>,
        /** 底栏里落在播放键**右边**的槽位（存档的 bottomRight）。 */
        val bottomRight: List<PlayerUiLayout.Slot>,
        /** 放不下、要进「更多」面板的槽位，保持原有顺序。 */
        val overflow: List<PlayerUiLayout.Slot>,
        /** 底栏末尾要不要借一格给「更多」按钮。 */
        val needsMoreCell: Boolean,
    ) {
        val bottom: List<PlayerUiLayout.Slot> get() = bottomLeft + bottomRight
        val bottomCount: Int get() = bottomLeft.size + bottomRight.size
    }

    /**
     * 按这一屏宽度排出各区域。
     *
     * [hasGroup] 为真（布局里已有显式的组）时不再借格补「更多」——组本身就是入口，
     * 溢出的槽位会并进那一组的键表。
     */
    fun plan(
        spec: Spec,
        layout: PlayerUiLayout.Layout,
        width: Float,
        hasGroup: Boolean,
    ): Plan {
        val perSide = if (spec.sideColumns) 3 else 0
        // 长条屏没有左右两列，两列的按钮整段并入底栏（排在底左之前，即播放键更左边）。
        val mergedLeft = if (spec.sideColumns) emptyList() else layout.left + layout.right
        val leftAll = mergedLeft + layout.bottomLeft
        val rightAll = layout.bottomRight
        val capacity = spec.bottomSlotCount(width)
        // 长条屏没有左右两列：两列的槽位已经整段并进 leftAll，
        // 这里必须返回空，否则同一批槽位会被算两次（既在底栏又在溢出里）。
        val sideOverflow = if (spec.sideColumns) {
            layout.left.drop(perSide) + layout.right.drop(perSide)
        } else {
            emptyList()
        }
        // 没有显式组、且有东西放不下时，从底栏借一格摆「更多」。
        val needsMoreCell = !hasGroup && (sideOverflow.isNotEmpty() || leftAll.size + rightAll.size > capacity)
        val slotCapacity = if (needsMoreCell) (capacity - 1).coerceAtLeast(0) else capacity
        // 放不下时先保右边（那里通常是音量/分P 这类高频键），左边从尾部截。'
        val shownRight = rightAll.take(slotCapacity)
        val shownLeft = leftAll.take((slotCapacity - shownRight.size).coerceAtLeast(0))
        return Plan(
            left = if (spec.sideColumns) layout.left.take(perSide) else emptyList(),
            right = if (spec.sideColumns) layout.right.take(perSide) else emptyList(),
            bottomLeft = shownLeft,
            bottomRight = shownRight,
            // 溢出顺序按阅读顺序：左列余下、右列余下、底栏余下，与设计稿「按原顺序追加」一致。
            overflow = sideOverflow + leftAll.drop(shownLeft.size) + rightAll.drop(shownRight.size),
            needsMoreCell = needsMoreCell,
        )
    }
}
