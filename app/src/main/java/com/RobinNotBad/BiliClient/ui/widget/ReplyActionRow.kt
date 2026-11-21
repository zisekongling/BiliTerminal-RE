package com.RobinNotBad.BiliClient.ui.widget

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.RobinNotBad.BiliClient.R
import kotlin.math.roundToInt

/**
 * 评论区操作行（赞数 / 点踩 / 翻译 / 删除 / 回复）的**圆屏排版**。
 *
 * <p>方屏下这一行是「左对齐点赞簇 + 右贴边回复」，左右两端正好落在圆屏的弧线上：
 * 赞数被切掉一半、回复的半个字看不见。[applyIfNeeded] 在**圆屏适配开启**时把它改成
 * 一条打包水平链（CHAIN_PACKED，bias 0.5），五个按钮紧挨成一组、整体落在屏幕中线，
 * 谁都碰不到弧线。方屏（开关关闭）一个约束都不碰，保持原样。
 *
 * <p>链里有两个成员经常是 GONE：[R.id.item_reply_translate] 目前恒隐藏
 * （ReplyTranslateApi.UI_ENABLED=false），[R.id.item_reply_delete] 只在本人或管理可见。
 * ConstraintLayout 会把 GONE 的链成员按 0 尺寸塌缩并忽略其 margin，所以它们在不在都不影响居中。
 *
 * <p>[shouldCenter] 是纯判据，[CENTERED_CHAIN] 是链的成员与顺序，两者都能在 JVM 单测里钉住；
 * 「看起来是否真的居中」只能人眼验收（L2）。
 */
object ReplyActionRow {

    /**
     * 链内相邻按钮的间距（dp）。与布局里 dislikeBtn / item_reply_translate / item_reply_delete
     * 的 marginStart 取同一个值；replyBtn 原本贴右边缘、没有左边距，居中后要补上这一段，
     * 否则「点踩 → 回复」会比别处紧一倍，看起来像挤在一起。
     */
    const val GAP_DP = 6f

    /**
     * 参与居中链的控件，**从左到右**的视觉顺序：赞数 → 点踩 → 翻译 → 删除 → 回复。
     *
     * <p>删掉其中任何一个都会让这一行重新贴边或错位，所以单测把成员与顺序钉在这里。
     */
    @JvmField
    val CENTERED_CHAIN: IntArray = intArrayOf(
        R.id.likes,
        R.id.dislikeBtn,
        R.id.item_reply_translate,
        R.id.item_reply_delete,
        R.id.replyBtn
    )

    /**
     * 这一行要不要居中。判据只有「圆屏适配是否开启」一条：
     * 方屏时代原有排布已经合适，不该为了统一而改动它。
     */
    fun shouldCenter(roundMode: Boolean): Boolean = roundMode

    /** [GAP_DP] 换算成像素。单独拆出来是为了能在 JVM 单测里钉住换算（密度不是整数时也只会差半像素）。 */
    fun gapPx(density: Float): Int = (GAP_DP * density).roundToInt()

    /**
     * 圆屏时把 [itemView] 的操作行改成整体居中；否则什么都不做。
     *
     * <p>[itemView] 必须是 cell_reply_list 的根 ConstraintLayout，传别的类型直接跳过（不抛异常），
     * 免得复用了别的布局时把整条列表炸掉。
     */
    fun applyIfNeeded(itemView: View, roundMode: Boolean) {
        if (!shouldCenter(roundMode)) return
        val layout = itemView as? ConstraintLayout ?: return
        val set = ConstraintSet()
        set.clone(layout)
        bindChain(set)
        // 打包链 + 居中偏置：整组落在父容器（= 去掉左右 padding 的内容区）的正中
        set.setMargin(R.id.replyBtn, ConstraintSet.START, gapPx(layout.resources.displayMetrics.density))
        set.setHorizontalChainStyle(R.id.likes, ConstraintSet.CHAIN_PACKED)
        set.setHorizontalBias(R.id.likes, 0.5f)
        set.applyTo(layout)
    }

    /**
     * 把 [CENTERED_CHAIN] 连成一条水平链：首尾贴父容器两端，中间相邻两两互指。
     *
     * <p>链必须是**双向**的——只连一侧会退化成「A 在 B 左边」而不是一条链，
     * 表现出来就是不居中、按钮还被拉开。
     */
    private fun bindChain(set: ConstraintSet) {
        val last = CENTERED_CHAIN.lastIndex
        for (i in CENTERED_CHAIN.indices) {
            val id = CENTERED_CHAIN[i]
            if (i == 0) set.connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            else set.connect(id, ConstraintSet.START, CENTERED_CHAIN[i - 1], ConstraintSet.END)

            if (i == last) set.connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            else set.connect(id, ConstraintSet.END, CENTERED_CHAIN[i + 1], ConstraintSet.START)
        }
    }
}
