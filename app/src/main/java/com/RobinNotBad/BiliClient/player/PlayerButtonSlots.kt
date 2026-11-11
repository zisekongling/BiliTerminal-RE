package com.RobinNotBad.BiliClient.player

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
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

    /** 图标在格子里靠哪边；列贴屏幕边缘，热区向内扩，图标位置保持改造前的位置。 */
    enum class IconAlign { START, CENTER, END }

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
        /** 格子的尺寸（26.11.08 起固定值，与 11.02 一致；不再按可用高度放大）。 */
        val cellWidthDp: Float,
        val cellHeightDp: Float,
        /** 图标本身的视觉尺寸；格子比图标大时靠内边距居中。 */
        val iconWidthDp: Float,
        val iconHeightDp: Float,
        val iconAlign: IconAlign,
        /** 每个格子左右各留多少外边距（底部行靠它凑出按钮之间的 6dp 间距；竖列不用）。 */
        val marginHorizontalDp: Float,
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

    /**
     * 影子当前的图标是从真身**哪一份** `Drawable` 复制出来的。
     *
     * 影子不能和真身共用同一个 `Drawable` 实例（见 [cloneDrawable]），所以每次同步都得复制一份；
     * 但 [syncMirrors] 是 400ms 定时器在跑，没有这张表就会每轮给每个影子再造一个 Drawable。
     * 真身只在改图标时换实例（`setImageResource`），所以按实例比对足够。
     */
    private val mirrorDrawableSource = HashMap<View, Drawable?>()

    /**
     * 各区的组格（含「更多」收纳格）：槽位下标 → 展开内容的 keys。
     * render 时重建；[refreshEmptyGroups] 靠它算「这一格里还有没有可见按钮」。
     */
    private val groupCells = LinkedHashMap<PlayerUiLayout.Region, LinkedHashMap<Int, List<String>>>()

    /** key → 真身（清场后重新摆时要能找回）。 */
    private val primariesByKey = LinkedHashMap<String, View>()

    /**
     * @param stripHost 展开条的外壳（`HorizontalScrollView`），显隐打在它身上；没有展开条传 null。
     * @param strip 外壳里装按钮的那层容器；与 [stripHost] 要么都给、要么都不给。
     * @param iconWidthDp / iconHeightDp 图标的视觉尺寸。默认与格子同大（即保持改造前的观感）；
     *   放大热区时单独传原来的图标尺寸，让图标不跟着变胖。
     * @param iconAlign 图标在格子里的位置。左右两列贴屏幕边缘，热区向内扩，图标位置不变。
     * @param marginHorizontalDp 每个格子左右各留多少外边距。底部行传 3dp 就能凑出按钮之间 6dp
     *   的间距（与 11.02 一致）；竖列在 11.02 里是 0 间距，不传。
     */
    fun bind(
        region: PlayerUiLayout.Region,
        container: ViewGroup,
        stripHost: View?,
        strip: ViewGroup?,
        cellWidthDp: Float,
        cellHeightDp: Float,
        iconWidthDp: Float = cellWidthDp,
        iconHeightDp: Float = cellHeightDp,
        iconAlign: IconAlign = IconAlign.CENTER,
        marginHorizontalDp: Float = 0f,
    ) {
        regions[region] = Region(
            container, stripHost, strip, cellWidthDp, cellHeightDp,
            iconWidthDp, iconHeightDp, iconAlign, marginHorizontalDp
        )
    }

    /**
     * 整份重摆。清场 → 逐区按槽位填 → 把影子同步一次。
     *
     * 26.11.08 起**不再按可用高度放大竖列**：那套「量完容器高度再 post 回来放大」在窄手表上
     * 看起来更宽松，但只要屏幕大一点（手机竖屏、宽屏手表）它就会把列格一路放大到上限，
     * 宽屏上按钮簇与底栏一起被撑大、跟 11.02 差得很明显（26.11.08 用户报告）。现在格子全部
     * 取固定值（见 [bind] 的各区参数），渲染一帧就定形，也不再有 post 的二次重摆。
     */
    fun render(layout: PlayerUiLayout.Layout) {
        clearAll()
        groupCells.clear()
        for (regionKey in PlayerUiLayout.Region.values()) {
            val region = regions[regionKey] ?: continue
            val slots = PlayerUiLayout.slots(layout, regionKey)
            for (index in slots.indices) {
                if (index >= regionKey.capacity) break
                when (val slot = slots[index]) {
                    is PlayerUiLayout.Slot.Button -> {
                        if (slot.key == PlayerButtons.MORE) {
                            // 「更多」＝自动收纳格：展开条里放的是当前没摆进布局的按钮。
                            val keys = overflowKeys(layout)
                            groupCells.getOrPut(regionKey) { LinkedHashMap() }[index] = keys
                            region.container.addView(moreButton(region, regionKey, index, keys))
                        } else {
                            placeButton(region, regionKey, slot.key)
                        }
                    }
                    is PlayerUiLayout.Slot.Group -> {
                        groupCells.getOrPut(regionKey) { LinkedHashMap() }[index] = slot.keys
                        region.container.addView(moreButton(region, regionKey, index, slot.keys))
                    }
                }
            }
        }
        refreshEmptyGroups()
        syncMirrors()
    }

    /**
     * 「自动收纳格」的内容：注册表里**没有出现在这份布局任何槽位（含组）**的 key，按候选
     * 列表顺序排列。布局变了（增删按钮）收纳内容跟着变；多个「更多」各自展开的是同一份
     * 清单（它们互相之间不算「已摆放」——收纳格是虚拟的，不占 [placedPrimaries]）。
     */
    private fun overflowKeys(layout: PlayerUiLayout.Layout): List<String> {
        val placed = HashSet<String>()
        for (regionKey in PlayerUiLayout.Region.values()) {
            for (slot in PlayerUiLayout.slots(layout, regionKey)) {
                when (slot) {
                    is PlayerUiLayout.Slot.Button -> placed.add(slot.key)
                    is PlayerUiLayout.Slot.Group -> placed.addAll(slot.keys)
                }
            }
        }
        placed.remove(PlayerButtons.MORE)
        return PlayerButtons.ALL.map { it.key }.filter { it !in placed && primaryOf(it) != null }
    }

    /** 收起所有展开的组（控制栏自动隐藏时调用，跟原来「更多」收起的手感一致）。 */
    fun collapseAll() {
        regions.values.forEach { collapse(it) }
    }

    /**
     * 空组格隐藏（26.11.09，需求②）：组里（或「更多」收纳格展开后）没有任何**可见**按钮时，
     * 那个「更多」格本身也藏起来——留着它点开就是一条空条。
     *
     * 按钮显隐是动态的（看点按钮要等分段加载完、连播按钮要看队列），所以这个判定不能只在
     * render 时做一次：[syncMirrors] 已经是播放页挂的 400ms 对齐定时器在跑，跟着它一起刷新
     * 最省事（[hasMirrors] 因此扩成了「有影子或有组格」——没组格也没影子的机器上定时器
     * 依旧一个不挂）。判定用的 keys 是 render 时记进 [groupCells] 的那份，展开内容也是它。
     */
    fun refreshEmptyGroups() {
        if (groupCells.isEmpty()) return
        for ((regionKey, cells) in groupCells) {
            val region = regions[regionKey] ?: continue
            val container = region.container
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                val index = child.tag
                if (child !is ImageButton || index !is Int) continue
                val keys = cells[index] ?: continue
                val anyVisible = keys.any { primaryOf(it)?.visibility == View.VISIBLE }
                child.visibility = if (anyVisible) View.VISIBLE else View.GONE
            }
        }
    }

    /** 当前有没有需要 400ms 定时器照看的东西：影子，或组格/收纳格（空组格刷新）。 */
    fun hasGroups(): Boolean = groupCells.isNotEmpty()

    /**
     * 把真身的图标/文字/显隐抄给影子；顺手刷一遍空组格。
     *
     * 播放页里改按钮状态的地方很多（起播、点弹幕、切循环……），逐个接线容易漏；这里做成
     * 「定时抄一遍」，由播放页在 onResume 起、onPause 停。影子只在用户真的把同一个按钮放了
     * 多份时才有，大多数机器上这里只做组格的显隐刷新（也很便宜：几个格子各查一遍 keys）。
     */
    fun syncMirrors() {
        refreshEmptyGroups()
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
            // 图标复制一份再给影子：共用同一个 Drawable 实例时，bounds 只由「最后布局 / 最后
            // setImageDrawable 的那个」写一次，另一份就按别人的尺寸画。FIT_XY 直接按 bounds 落笔
            // （没有矩阵居中），所以表现为「小一圈 + 贴左上角」。
            if (mirrorDrawableSource[mirror] !== primary.drawable) {
                mirror.setImageDrawable(cloneDrawable(primary.drawable, primary.resources))
                mirrorDrawableSource[mirror] = primary.drawable
            }
            mirror.contentDescription = primary.contentDescription
        } else if (primary is TextView && mirror is TextView) {
            mirror.text = primary.text
        }
    }

    /**
     * 造一份独立的 `Drawable`（有 `ConstantState` 时走它，位图本身仍共享，不额外占内存）。
     *
     * **不能直接把真身的 drawable 交给影子**（26.11.11 用户报「新加进底部的按钮比同一行的按钮
     * 小一圈、还往上偏」）：`Drawable` 只有一组 `bounds` 和一个 `callback`，两个 `ImageView`
     * 共用实例就会互相覆盖 —— 实测底部 32×37 的格子里画出来的是右列 28×28 格子的 48×48 bounds。
     */
    private fun cloneDrawable(original: Drawable?, resources: Resources): Drawable? {
        val copy = original?.constantState?.newDrawable(resources) ?: return original
        return copy.mutate()
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
        view.layoutParams = cellParams(view, region)
        region.container.addView(view)
    }

    private fun mirrorOf(primary: View, region: Region): View {
        val mirror: View = if (primary is ImageButton) {
            ImageButton(context).apply {
                scaleType = primary.scaleType
                // 图标与背景都得复制一份：共用实例会让真身和影子互相改写 bounds / callback
                setImageDrawable(cloneDrawable(primary.drawable, primary.resources))
                setBackgroundDrawable(cloneDrawable(primary.background, primary.resources))
            }.also { mirrorDrawableSource[it] = primary.drawable }
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
        val attr = android.util.TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, attr, true)) {
            setBackgroundResource(attr.resourceId)
        }
        // 显式清零内边距：selectableItemBackground 这类自带 padding/inset 的背景会被 View 转成
        // 控件内边距，图标就会被挤小、跟同行其它按钮对不上。
        // **顺序不能反**：cellParams 会按「格子尺寸 - 图标尺寸」写回内边距（见 [:cellParams]），
        // 先算 layoutParams 再清零，否则刚清掉的内边距又会被写回来。
        contentDescription = context.getString(R.string.desc_player_group_expand)
        tag = index
        layoutParams = cellParams(this, region)
        setPadding(0, 0, 0, 0)
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
            view.layoutParams = cellParams(view, region)
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
                mirrorDrawableSource.remove(child)
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
        mirrorDrawableSource.clear()
    }

    // ==================== 小工具 ====================

    private fun detach(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    /** 图标类槽位按区的单元格尺寸铺满；文字类（倍速/视频信息/跳转/连播）宽度交给文字自己。 */
    private fun cellParams(view: View, region: Region): LinearLayout.LayoutParams {
        val w = ToolsUtil.dp2px(region.cellWidthDp)
        val h = ToolsUtil.dp2px(region.cellHeightDp)
        val params = if (view is TextView && view !is ImageButton) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, h)
        } else {
            // 图标保持改造前的视觉尺寸，放大的那圈全算热区（26.11.07 调研清单第 11 条）：
            // 直接用 48dp 的 ImageButton 会把 28dp 的图标一起拉大，观感全变。
            if (view is ImageButton) {
                // 图标一律等比居中（26.11.11）：按钮池里两批按钮的 scaleType 不一致（清晰度/音量/
                // 分P 是 fitCenter，弹幕/字幕/循环/连播/旋转/锁定那一批是 fitXY），而底部格子是
                // 32×37、图标是方的——fitXY 会把方图标竖向拉长到 1.19 倍，跟同一行的 fitCenter
                // 按钮比起来就是「新加的按钮大小不一样」。统一在槽位里改成 fitCenter 后，谁被放进
                // 哪一区都长同一个样。
                view.scaleType = ImageView.ScaleType.FIT_CENTER
                val iconW = ToolsUtil.dp2px(region.iconWidthDp)
                val iconH = ToolsUtil.dp2px(region.iconHeightDp)
                val padH = (w - iconW).coerceAtLeast(0)
                val padV = ((h - iconH) / 2).coerceAtLeast(0)
                when (region.iconAlign) {
                    // 左列贴左边，多出来的宽度留在图标右边；右列反过来 —— 图标位置与改造前一致
                    IconAlign.START -> view.setPadding(0, padV, padH, padV)
                    IconAlign.END -> view.setPadding(padH, padV, 0, padV)
                    IconAlign.CENTER -> {
                        val half = padH / 2
                        view.setPadding(half, padV, padH - half, padV)
                    }
                }
            }
            LinearLayout.LayoutParams(w, h)
        }
        // 底部行靠左右外边距凑出按钮之间的间距（11.02 是 6dp）；竖列传 0，保持 0 间距
        val margin = ToolsUtil.dp2px(region.marginHorizontalDp)
        if (margin > 0) {
            params.leftMargin = margin
            params.rightMargin = margin
        }
        return params
    }
}
