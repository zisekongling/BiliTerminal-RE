package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.SettingsKeys

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
