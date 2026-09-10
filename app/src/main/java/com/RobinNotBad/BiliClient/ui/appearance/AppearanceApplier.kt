package com.RobinNotBad.BiliClient.ui.appearance

import android.app.Activity
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.WeakHashMap

/**
 * 把当前外观（自定义字体 + 圆角档位）套到界面上。字体与圆角共用**同一次**遍历。
 *
 * ## 为什么圆角也要走遍历（而不是主题属性）
 * 曾试过用主题属性做圆角的运行时切换：`<item name="cardCornerRadius">?attr/appCornerRadius</item>`
 * 配合 `theme.applyStyle(覆盖样式)`。**真机实测半径变成 0**——卡片角完全没被裁，
 * 连原来 `@dimen/card_round` 的 6/10dp 都丢了。维度属性的 `?attr/` 间接层在这里没有解析成功。
 * 现在 XML 里圆角就是具体 dimen（默认「方角」），只有选「圆角」时才在运行时覆盖。
 *
 * ## 为什么字体不用 `LayoutInflater.Factory2`（更漂亮的做法）
 * `LayoutInflater.setFactory2()` 只在**从未设过** factory 时可用，否则抛 `IllegalStateException`。
 * 而 `BaseActivity : AppCompatActivity`，AppCompat 已经在 `super.onCreate()` 里装好了自己的
 * factory（还带着 Material 的控件替换，`MaterialButton`/`MaterialCardView` 靠它）。
 * 用自己的 factory 顶掉它会让 `<Button>` 退回普通 Button——比字体问题严重。
 * 公开 API 没有干净办法把两个 factory 串起来，所以走遍历。
 *
 * ## 性能代价（手表优先，必须说清楚）
 * - **两项都没启用时零开销**：字体未装 + 圆角为默认「方角」→ 本对象的入口立即 return，
 *   一次遍历都不做。默认状态就是这条路径，绝大多数用户付 0 代价。
 * - 启用后：每次 `Activity.onContentChanged()` 跑**一次**遍历，字体与圆角在同一次里处理。
 * - 列表项由 `RecyclerView` 复用、绑定发生在遍历之后，故对遍历中遇到的每个 `RecyclerView`
 *   挂 `OnChildAttachStateChangeListener`，只处理**新挂上来的** item 视图；复用的视图再次挂载时
 *   因 `!==` / `!=` 判断而几乎零成本。
 *
 * 这套代价是**用户主动开启**换来的，不是所有人付。
 */
object AppearanceApplier {

    /** 已经挂过监听器的容器，避免重复挂（弱引用，随 Activity 一起回收）。 */
    private val hookedGroups = WeakHashMap<ViewGroup, Boolean>()

    /** 已派生的带样式字体缓存（NORMAL/BOLD/ITALIC/BOLD_ITALIC）。 */
    private var cachedBase: Typeface? = null
    private val cachedStyled = arrayOfNulls<Typeface>(4)

    /**
     * 把当前外观套到该 Activity 的整个内容视图上。
     *
     * 由 [com.RobinNotBad.BiliClient.activity.base.BaseActivity.onContentChanged] 调用——
     * 那是 `setContentView` 之后必被触发的钩子，能同时覆盖普通布局与 `asyncInflate` 的替换布局。
     */
    fun applyToContentView(activity: Activity) {
        val typeface = FontStyle.typeface(activity)
        // 默认「方角」时 XML 里就是目标值，不需要任何运行时覆盖 → 只有非默认档才取 radius
        val radius = if (CornerStyle.needsRuntimeOverride()) CornerStyle.radiusPx(activity) else null
        if (typeface == null && radius == null) return

        val root = activity.findViewById<View>(android.R.id.content) ?: return
        apply(root, typeface, radius)
    }

    private fun apply(view: View, typeface: Typeface?, radius: Float?) {
        if (typeface != null && view is TextView) applyToTextView(view, typeface)
        if (radius != null) applyRadius(view, radius)
        if (view is ViewGroup) {
            hookGroup(view, typeface, radius)
            for (i in 0 until view.childCount) apply(view.getChildAt(i), typeface, radius)
        }
    }

    /**
     * 给每个 `ViewGroup` 挂 `OnHierarchyChangeListener`，覆盖**遍历之后才加进来**的子视图。
     *
     * 这一条是必需的，不是保险：设置索引页（`SettingMainActivity`）的卡片是在
     * `asyncInflate` 回调里**程序化 `addView`** 加进容器的，发生在本遍历之后；
     * 只在 `onContentChanged` 走一遍的话，那一整页都不会生效。
     * `RecyclerView` 的 item 挂载同样走 `addView`，所以这条也顺带覆盖了列表项，
     * 不需要再单独挂 `OnChildAttachStateChangeListener`。
     *
     * 全工程没有其它地方用 `setOnHierarchyChangeListener`（grep 确认），所以这里直接设置，
     * 不需要链式保留别人的回调。
     */
    private fun hookGroup(group: ViewGroup, typeface: Typeface?, radius: Float?) {
        if (hookedGroups.containsKey(group)) return
        hookedGroups[group] = true
        group.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                apply(child, typeface, radius)
            }

            override fun onChildViewRemoved(parent: View, child: View) = Unit
        })
    }

    /**
     * 圆角只作用于卡片与按钮——它们是 XML 里唯一从 `card_round` 取值的控件。
     *
     * **普通 `androidx.cardview.widget.CardView` 也必须处理**：本工程并非只有
     * `MaterialCardView`（实测 `dumpsys activity top` 里两者同时存在，settings 索引页的卡片就是
     * 普通 `CardView`）。普通 CardView 不读 `materialCardViewStyle`，XML 里的
     * `cardCornerRadius` 对它无效，只有 `setRadius()` 能改——漏掉它就会出现
     * 「有的卡片跟着档位变、有的不变」。
     *
     * 分支顺序有意义：`MaterialCardView extends CardView`，必须先匹配子类。
     */
    private fun applyRadius(view: View, radius: Float) {
        when (view) {
            is MaterialCardView -> if (view.radius != radius) view.radius = radius
            is CardView -> if (view.radius != radius) view.radius = radius
            is MaterialButton -> {
                val px = radius.toInt()
                if (view.cornerRadius != px) view.cornerRadius = px
            }
        }
    }

    /**
     * 单独设置 TextView 的字体，**保留它原有的粗体/斜体**。
     *
     * 直接 `setTypeface(custom)` 会把 `android:textStyle="bold"` 的效果抹掉
     * （自定义字体是按 NORMAL 解析的），所以要按原样式派生一份。
     */
    private fun applyToTextView(textView: TextView, typeface: Typeface) {
        val current = textView.typeface
        val style = current?.style?.and(0x3) ?: Typeface.NORMAL
        val target = styled(typeface, style)
        if (current !== target) textView.typeface = target
    }

    private fun styled(base: Typeface, style: Int): Typeface {
        if (cachedBase !== base) {
            cachedBase = base
            cachedStyled.fill(null)
        }
        cachedStyled[style]?.let { return it }
        val derived = if (style == Typeface.NORMAL) base else Typeface.create(base, style)
        cachedStyled[style] = derived
        return derived
    }
}
