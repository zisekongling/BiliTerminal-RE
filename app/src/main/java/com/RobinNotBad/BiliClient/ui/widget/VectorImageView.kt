package com.RobinNotBad.BiliClient.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources

/**
 * 自己读 `app:srcCompat` 的 ImageView / ImageButton。
 *
 * **为什么不能直接用原生控件**：`app/build.gradle` 里 `vectorDrawables { useSupportLibrary true }`
 * 关掉了 API<21 的 PNG 兜底（仓库 `drawable/` 与 `drawable-v21/` 有同名双份，关掉这个开关
 * AAPT 会在 `mergeResources` 撞 Duplicate resources），所以矢量图只有 `AppCompatResources` /
 * `VectorDrawableCompat` 能解析。而 AppCompat 的控件工厂只装在 `AppCompatActivity` 上，
 * `PlayerActivity` 是原生 `Activity`（主题 `android:Theme.Black`，见 `AndroidManifest.xml`），
 * 布局走系统 inflater：
 *
 * - `android:src` 由原生 ImageView 构造函数解析，API<21 抛
 *   `Resources$NotFoundException: File res/xx.xml ... invalid drawable tag vector`（26.11.09 扫码登录页崩溃同类）；
 * - `app:srcCompat` 被原生控件当未知属性静默忽略，图标直接不显示。
 *
 * **为什么也不能换 `androidx.appcompat.widget.AppCompatImageButton`**：它的构造函数里有
 * `ThemeUtils.checkAppCompatTheme(this, getContext())`，非 AppCompat 主题会直接抛
 * `IllegalArgumentException: You need to use a Theme.AppCompat theme (or descendant) with this widget.`，
 * 而播放器为了全屏黑色只能留 `android:Theme.Black`。
 *
 * 所以这两个类只做最小的一件事：把 `app:srcCompat` 当普通资源 id 读出来交给 [AppCompatResources]，
 * 并让 [setImageResource] 走同一条路（`setImageResource` 内部同样是原生 `Resources.loadDrawable`，
 * 传矢量同样会崩）。不碰主题，不依赖控件工厂，用法与普通控件一致：
 *
 * ```xml
 * <com.RobinNotBad.BiliClient.ui.widget.VectorImageButton
 *     android:id="@+id/btn"
 *     app:srcCompat="@drawable/btn_player_play" />
 * ```
 */
open class VectorImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ImageView(context, attrs, defStyleAttr) {

    init {
        loadSrcCompatInto(this, attrs)
    }

    override fun setImageResource(@DrawableRes resId: Int) {
        setImageDrawable(AppCompatResources.getDrawable(context, resId))
    }
}

/** 见 [VectorImageView] 的类注释：原生 `ImageButton` 在非 AppCompat 主题下同样解析不了矢量图。 */
open class VectorImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ImageButton(context, attrs, defStyleAttr) {

    init {
        loadSrcCompatInto(this, attrs)
    }

    override fun setImageResource(@DrawableRes resId: Int) {
        setImageDrawable(AppCompatResources.getDrawable(context, resId))
    }
}

private fun loadSrcCompatInto(view: ImageView, attrs: AttributeSet?) {
    if (attrs == null) return
    val a = view.context.obtainStyledAttributes(attrs, intArrayOf(androidx.appcompat.R.attr.srcCompat))
    val resId = a.getResourceId(0, 0)
    a.recycle()
    if (resId != 0) view.setImageDrawable(AppCompatResources.getDrawable(view.context, resId))
}
