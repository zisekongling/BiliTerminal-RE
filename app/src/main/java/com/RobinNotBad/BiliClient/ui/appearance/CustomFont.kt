package com.RobinNotBad.BiliClient.ui.appearance

import android.app.Activity
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.WeakHashMap

/**
 * 把 [FontStyle] 里的自定义字体套到界面上。
 *
 * ## 为什么不用 `LayoutInflater.Factory2`（那是更漂亮的做法）
 * `LayoutInflater.setFactory2()` 只在**从未设过** factory 时可用，否则抛 `IllegalStateException`。
 * 而 `BaseActivity : AppCompatActivity`，AppCompat 已经在 `super.onCreate()` 里装好了自己的
 * factory（还带着 Material 的控件替换，`MaterialButton`/`MaterialTextView` 靠它，
 * 我们的圆角模块也依赖它）。用自己的 factory 顶掉它，会让 `<Button>` 退回普通 Button、
 * 圆角失效——那是比字体更严重的回归。公开 API 没有干净的办法把两个 factory 串起来，
 * 所以走遍历这条路。
 *
 * ## 性能代价（手表优先，必须说清楚）
 * - **未启用自定义字体时完全零开销**：[FontStyle.typeface] 返回 null，`applyToContentView` 立即 return，
 *   一次遍历都不做。默认（未装字体）就是这条路径。
 * - 启用后：[applyToContentView] 在每次 `Activity.onContentChanged()` 跑**一次**静态视图树遍历，
 *   只对 `TextView` 调 `setTypeface`，并用 `!==` 判断跳过已套好的。
 * - 列表项由 `RecyclerView` 复用，绑定发生在遍历之后，所以另外挂
 *   `OnChildAttachStateChangeListener`，只处理**新挂上来的** item 视图；复用的视图再次挂载时
 *   因 `!==` 判断而几乎零成本。
 *
 * 这套代价是「用户主动开启」换来的，不是所有人都付。
 */
object CustomFont {

    /** 已经挂过监听器的 RecyclerView，避免重复挂（弱引用，随 Activity 一起回收）。 */
    private val hookedRecyclerViews = WeakHashMap<RecyclerView, Boolean>()

    /** 已派生的带样式字体缓存（NORMAL/BOLD/ITALIC/BOLD_ITALIC）。 */
    private var cachedBase: Typeface? = null
    private val cachedStyled = arrayOfNulls<Typeface>(4)

    /**
     * 把当前自定义字体套到该 Activity 的整个内容视图上。
     *
     * 由 [com.RobinNotBad.BiliClient.activity.base.BaseActivity.onContentChanged] 调用——
     * 那是 `setContentView` 之后必被触发的钩子，能同时覆盖普通布局与 `asyncInflate` 的替换布局。
     */
    fun applyToContentView(activity: Activity) {
        val typeface = FontStyle.typeface(activity) ?: return
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        apply(root, typeface)
    }

    private fun apply(view: View, typeface: Typeface) {
        if (view is TextView) applyToTextView(view, typeface)
        if (view is RecyclerView) hook(view, typeface)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) apply(view.getChildAt(i), typeface)
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

    private fun hook(recyclerView: RecyclerView, typeface: Typeface) {
        // 已挂过就只补一次现有子视图（asyncInflate 换布局时可能已有内容）
        if (hookedRecyclerViews.containsKey(recyclerView)) {
            for (i in 0 until recyclerView.childCount) apply(recyclerView.getChildAt(i), typeface)
            return
        }
        hookedRecyclerViews[recyclerView] = true
        for (i in 0 until recyclerView.childCount) apply(recyclerView.getChildAt(i), typeface)
        recyclerView.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) = apply(view, typeface)
                override fun onChildViewDetachedFromWindow(view: View) = Unit
            }
        )
    }
}
