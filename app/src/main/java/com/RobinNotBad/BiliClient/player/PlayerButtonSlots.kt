package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.ToolsUtil

/**
 * 按用户布局把播放界面的按钮摆到四个槽位区里（左列 / 右列 / 底部左 / 底部右）。
 *
 * 设计要点：
 * 1. **按钮本体不动**。`activity_player.xml` 里的那 17 个按钮被收进一个隐藏的 `button_pool`，
 *    它们仍然是唯一的「真身」——所有状态（图标、文字、监听器）都写在真身上，播放页里原有的
 *    那些 `btn_danmaku.setImageResource(...)` / `btn_page_selector.visibility = ...` 一行都不用改。
 *    本类只负责把真身搬进槽位；同一个按钮在布局里出现第二次时，才复制一个「影子」出来。
 * 2. **影子只复刻外观，点击转发给真身**。这样「同一个按钮放多份、状态实时同步」不需要改任何
 *    业务代码：`syncMirrors()` 把真身的图标/文字/显隐抄给影子，点击走 `primary.performClick()`。
 * 3. **组（Slot.Group）** 在槽位里渲染成一个「更多」按钮，点开后把组内按钮搬进同一侧展开条
 *    （右侧展开条在列左边、左侧在列右边、底部在按钮行上方），同一侧同时只开一组。
 */
class PlayerButtonSlots(
    private val context: Context,
    private val pool: ViewGroup,
    private val primaryOf: (String) -> View?,
) {

    /**
     * 一个槽位区：放槽位的容器 + 组展开条（没有展开条的区传 null）+ 单元格尺寸。
     *
     * [stripHost] 是展开条的外壳（`HorizontalScrollView`），[strip] 是外壳里那层放按钮的
     * `LinearLayout`。**显隐必须打在 [stripHost] 上**：外壳在 `activity_player.xml` 里默认是
     * `visibility="gone"`，只把里面那层设成 VISIBLE 的话父容器仍是 GONE，点「更多」什么都不会弹出来。
     */
    private class Region(
        val container: ViewGroup,
        val stripHost: View?,
        val strip: ViewGroup?,
        val cellWidthDp: Float,
        val cellHeightDp: Float,
    ) {
        /** 当前展开的槽位下标；-1 = 都收着。 */
        var openIndex: Int = -1
    }

    private val regions = LinkedHashMap<PlayerUiLayout.Region, Region>()

    /** 重摆/展开之后回调（播放页拿它对一次左右两列的中线）。 */
    var onLayoutChanged: (() -> Unit)? = null

    /** 正在槽位里的「真身」，清场时要送回池子。 */
    private val placedPrimaries = HashSet<View>()

    /** key → 影子；真身在别处时用。 */
    private val mirrors = LinkedHashMap<String, MutableList<View>>()

    /** key → 真身（清场后重新摆时要能找回）。 */
    private val primariesByKey = LinkedHashMap<String, View>()

    /**
     * @param stripHost 展开条的外壳（`HorizontalScrollView`），显隐打在它身上；没有展开条传 null。
     * @param strip 外壳里装按钮的那层容器；与 [stripHost] 要么都给、要么都不给。
     */
    fun bind(
        region: PlayerUiLayout.Region,
        container: ViewGroup,
        stripHost: View?,
        strip: ViewGroup?,
        cellWidthDp: Float,
        cellHeightDp: Float,
    ) {
        regions[region] = Region(container, stripHost, strip, cellWidthDp, cellHeightDp)
    }

    /** 整份重摆。清场 → 逐区按槽位填 → 把影子同步一次。 */
    fun render(layout: PlayerUiLayout.Layout) {
        clearAll()
        for (regionKey in PlayerUiLayout.Region.values()) {
            val region = regions[regionKey] ?: continue
            val slots = PlayerUiLayout.slots(layout, regionKey)
            for (index in slots.indices) {
                if (index >= regionKey.capacity) break
                when (val slot = slots[index]) {
                    is PlayerUiLayout.Slot.Button -> placeButton(region, regionKey, slot.key)
                    is PlayerUiLayout.Slot.Group -> region.container.addView(moreButton(region, regionKey, index, slot.keys))
                }
            }
        }
        syncMirrors()
    }

    /** 收起所有展开的组（控制栏自动隐藏时调用，跟原来「更多」收起的手感一致）。 */
    fun collapseAll() {
        regions.values.forEach { collapse(it) }
    }

    /**
     * 把真身的图标/文字/显隐抄给影子。
     *
     * 播放页里改按钮状态的地方很多（起播、点弹幕、切循环……），逐个接线容易漏；这里做成
     * 「定时抄一遍」，由播放页在 onResume 起、onPause 停。影子只在用户真的把同一个按钮放了
     * 多份时才有，大多数机器上这里是空转。
     */
    fun syncMirrors() {
        if (mirrors.isEmpty()) return
        for ((key, list) in mirrors) {
            val primary = primariesByKey[key] ?: continue
            for (mirror in list) syncOne(primary, mirror)
        }
    }

    /**
     * 当前有没有影子（同一个按钮被放了多份）。
     *
     * 播放页据此决定要不要挂对齐定时器：绝大多数机器上这里都是 false，那就一个定时器都不挂，
     * 别为了一个不存在的影子每 400ms 醒一次主线程。
     */
    fun hasMirrors(): Boolean = mirrors.isNotEmpty()

    private fun syncOne(primary: View, mirror: View) {
        mirror.visibility = primary.visibility
        if (primary is ImageButton && mirror is ImageButton) {
            mirror.setImageDrawable(primary.drawable)
            mirror.contentDescription = primary.contentDescription
        } else if (primary is TextView && mirror is TextView) {
            mirror.text = primary.text
        }
    }

    // ==================== 摆放 ====================

    private fun placeButton(region: Region, regionKey: PlayerUiLayout.Region, key: String) {
        val primary = primaryOf(key) ?: return
        val view: View
        if (placedPrimaries.contains(primary)) {
            // 真身已经在别的槽位里（同一个按钮放了两份）→ 复制一个影子
            view = mirrorOf(primary, region)
            mirrors.getOrPut(key) { ArrayList() }.add(view)
        } else {
            detach(primary)
            primariesByKey[key] = primary
            placedPrimaries.add(primary)
            view = primary
        }
        view.layoutParams = cellParams(primary, region)
        region.container.addView(view)
    }

    private fun mirrorOf(primary: View, region: Region): View {
        val mirror: View = if (primary is ImageButton) {
            ImageButton(context).apply {
                scaleType = primary.scaleType
                setImageDrawable(primary.drawable)
                setBackgroundDrawable(primary.background?.constantState?.newDrawable()?.mutate())
            }
        } else {
            val source = primary as? TextView
            TextView(context).apply {
                if (source != null) {
                    text = source.text
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, source.textSize)
                    setTextColor(source.textColors)
                    gravity = source.gravity
                    setPadding(source.paddingLeft, source.paddingTop, source.paddingRight, source.paddingBottom)
                    // 阴影参数照抄真身，别用「3f/2f/黑」这种拍出来的值：真身的阴影来自布局或主题，
                    // 主题或截图改了以后影子就跟不上；真身本来没有阴影时也不该凭空多一层黑边。
                    if (source.shadowRadius > 0f) {
                        setShadowLayer(source.shadowRadius, source.shadowDx, source.shadowDy, source.shadowColor)
                    }
                }
            }
        }
        mirror.contentDescription = primary.contentDescription
        // 点击/长按一律转发给真身：状态与业务逻辑都只写在真身那一份里
        mirror.setOnClickListener { primary.performClick() }
        mirror.setOnLongClickListener { primary.performLongClick() }
        return mirror
    }

    /** 组槽位在播放页里就是一个「更多」按钮：点开把组内按钮摆到同侧展开条上。 */
    private fun moreButton(
        region: Region,
        regionKey: PlayerUiLayout.Region,
        index: Int,
        keys: List<String>,
    ): View = ImageButton(context).apply {
        setImageResource(R.mipmap.morehide)
        scaleType = android.widget.ImageView.ScaleType.FIT_XY
        val attr = android.util.TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, attr, true)) {
            setBackgroundResource(attr.resourceId)
        }
        // 显式清零内边距：selectableItemBackground 这类自带 padding/inset 的背景会被 View 转成
        // 控件内边距，FIT_XY 就会把方形图标压成一条扁条（更多按钮「扁了」的根因）
        setPadding(0, 0, 0, 0)
        contentDescription = context.getString(R.string.desc_player_group_expand)
        tag = index
        layoutParams = cellParams(null, region)
        setOnClickListener { toggleGroup(regionKey, index, keys) }
    }

    private fun toggleGroup(regionKey: PlayerUiLayout.Region, index: Int, keys: List<String>) {
        val region = regions[regionKey] ?: return
        val strip = region.strip ?: return
        val wasOpen = region.openIndex == index
        // 底部左右两区共用同一条展开条，所以这里按「展开条」而不是按「区」清场
        collapseStrip(strip)
        if (wasOpen) return
        for (key in keys) {
            val primary = primaryOf(key) ?: continue
            val view: View
            if (placedPrimaries.contains(primary)) {
                view = mirrorOf(primary, region)
                mirrors.getOrPut(key) { ArrayList() }.add(view)
            } else {
                detach(primary)
                primariesByKey[key] = primary
                placedPrimaries.add(primary)
                view = primary
            }
            view.layoutParams = cellParams(primary, region)
            strip.addView(view)
        }
        region.openIndex = index
        setStripVisible(region, strip.childCount > 0)
        refreshMoreIcons(region)
        syncMirrors()
        onLayoutChanged?.invoke()
    }

    /**
     * 清空一条展开条。
     *
     * 底部左右两区共用一条展开条（`bottom_group_slots`），所以清场必须按展开条做：
     * 只清「我这一区」的话，另一区的 openIndex 会留在旧值上，更多按钮的图标就会对不上。
     */
    private fun collapseStrip(strip: ViewGroup) {
        for (region in regions.values) {
            if (region.strip === strip) collapse(region)
        }
    }

    private fun collapse(region: Region) {
        val strip = region.strip ?: return
        if (region.openIndex < 0 && strip.childCount == 0) return
        val children = ArrayList<View>(strip.childCount)
        for (i in 0 until strip.childCount) children.add(strip.getChildAt(i))
        strip.removeAllViews()
        for (child in children) {
            if (placedPrimaries.remove(child)) {
                pool.addView(child)
            } else {
                mirrors.values.forEach { it.remove(child) }
            }
        }
        setStripVisible(region, false)
        region.openIndex = -1
        refreshMoreIcons(region)
    }

    /**
     * 显隐一条展开条。
     *
     * **外壳与内层都要设**：外壳是 `activity_player.xml` 里默认 `visibility="gone"` 的
     * `HorizontalScrollView`，内层是它里面装按钮的 `LinearLayout`。两层里只要还有一层是 GONE，
     * 组内按钮就一定看不见——「点更多展不开」的根因就是只切了内层、外壳一直是 GONE。
     */
    private fun setStripVisible(region: Region, visible: Boolean) {
        val flag = if (visible) View.VISIBLE else View.GONE
        region.stripHost?.visibility = flag
        region.strip?.visibility = flag
    }

    private fun refreshMoreIcons(region: Region) {
        val container = region.container
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val index = child.tag
            if (child is ImageButton && index is Int) {
                child.setImageResource(if (index == region.openIndex) R.mipmap.moreshow else R.mipmap.morehide)
            }
        }
    }

    private fun clearAll() {
        for (region in regions.values) {
            collapse(region)
            val container = region.container
            val children = ArrayList<View>(container.childCount)
            for (i in 0 until container.childCount) children.add(container.getChildAt(i))
            container.removeAllViews()
            for (child in children) {
                if (placedPrimaries.remove(child)) pool.addView(child)
            }
        }
        // 真身也可能被外部搬走（锁定态把锁定按钮挪到遮罩层上落脚）。这些不在任何容器里，
        // 上面的循环够不着，不收回池子的话它会被当成"已摆放"——槽位里摆出一个影子，
        // 真身留在外面，解锁后右侧就多出一个锁图标。
        for (view in ArrayList(placedPrimaries)) {
            if (view.parent === pool) continue
            // 必须先脱离原父容器：锁定态里 lock_btn 被搬到 lock_holder 上，直接 addView 会抛
            // "The specified child already has a parent"（PlayerButtonSlots.clearAll 崩溃）
            detach(view)
            pool.addView(view)
        }
        placedPrimaries.clear()
        primariesByKey.clear()
        mirrors.clear()
    }

    // ==================== 小工具 ====================

    private fun detach(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    /** 图标类槽位按区的单元格尺寸铺满；文字类（倍速/视频信息/跳转/连播）宽度交给文字自己。 */
    private fun cellParams(primary: View?, region: Region): LinearLayout.LayoutParams {
        val w = ToolsUtil.dp2px(region.cellWidthDp)
        val h = ToolsUtil.dp2px(region.cellHeightDp)
        return if (primary is TextView && primary !is ImageButton) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, h)
        } else {
            LinearLayout.LayoutParams(w, h)
        }
    }
}
