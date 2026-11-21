package com.RobinNotBad.BiliClient.util

import android.app.Activity
import android.view.Window
import java.util.WeakHashMap

/**
 * 全局「关闭页面转场动画」开关的执行层（O7）。
 *
 * <h3>为什么需要</h3>
 * 手表端的转场动画是这个尺寸上最容易被嫌弃的东西：一屏只有 400×400，切入动画那 200ms 里
 * 用户什么都做不了，而设置项（[SettingsKeys.UI_DISABLE_PAGE_TRANSITIONS]）本身只存了个 bool。
 * 真正要落地得同时压住两处：
 *
 * 1. **窗口入场动画** `WindowManager.LayoutParams.windowAnimations`（系统级的窗口淡入/滑动，
 *    由主题决定，光调 `overridePendingTransition` 关不掉）；
 * 2. **Activity 转场** `Activity.overridePendingTransition(0, 0)`（`startActivity` 时那一下）。
 *
 * <h3>为什么记原值</h3>
 * `windowAnimations` 是**每个窗口各自一份**的属性，主题给的值不一定相同（有的页面自己
 * `setTheme` 换过主题，比如播放器的全屏主题）。所以第一次执行时把原值记进 [saved]，
 * 开关关掉后再写回去 —— 直接写某个「默认值」会把页面自己的主题覆盖掉。
 * 记录用 [WeakHashMap]：Activity 销毁后由 GC 释放，配合 [forget] 在 `onDestroy` 里主动清。
 *
 * <h3>和既有 `overridePendingTransition` 的关系</h3>
 * `activity/base/InstanceActivity.kt` 里本来就有一处
 * `overridePendingTransition(R.anim.anim_activity_in_down, 0)`。开关**默认关闭**时本类完全不碰
 * 任何窗口属性、也不调 `overridePendingTransition`，既有行为一个像素都不变；只有用户显式
 * 关闭转场时，才会在同一帧再压一次 `(0, 0)`（后调用的生效，正是我们要的效果）。
 *
 * <h3>纯逻辑</h3>
 * 判据都抽成了纯函数（[windowAnimationsFor] / [shouldSuppressTransition]），窗口操作放在
 * [apply] 里，因此核心分支能在 JVM 单测里验证（见 `PageMotionTest`）。
 */
object PageMotion {

    private const val TAG = "PageMotion"

    /** Activity → 「关闭动画之前」的 windowAnimations 原值。 */
    private val saved = WeakHashMap<Activity, Int>()

    /** 开关当前是否要求关闭转场。 */
    @JvmStatic
    fun isDisabled(): Boolean =
        SharedPreferencesUtil.getBoolean(SettingsKeys.UI_DISABLE_PAGE_TRANSITIONS, false)

    /**
     * 纯判据：这次应该把 `windowAnimations` 设成多少。
     *
     * @param disabled 开关是否要求关闭转场
     * @param remembered [saved] 里记下的原值（没有记录为 null）
     * @return 要写入的值；null 表示「什么都不用动」
     */
    @JvmStatic
    fun windowAnimationsFor(disabled: Boolean, remembered: Int?): Int? = when {
        // 关闭转场且还没记过原值：先落 0（原值由 apply 负责记录）
        disabled && remembered == null -> 0
        // 已经关过：重复调用不再写，省掉一次 setAttributes
        disabled -> null
        // 开关关掉且手里有原值：还原
        remembered != null -> remembered
        // 开关本来就是关的、也没有记录：不动
        else -> null
    }

    /** 纯判据：是否需要额外压一次 `overridePendingTransition(0, 0)`。 */
    @JvmStatic
    fun shouldSuppressTransition(disabled: Boolean): Boolean = disabled

    /**
     * 按开关状态调整这个页面的转场动画。在 `onCreate` 与 `onResume` 各调一次即可
     * （回前台时窗口属性可能被系统重置）。
     */
    @JvmStatic
    fun apply(activity: Activity) {
        val window: Window = activity.window ?: return
        val disabled = isDisabled()
        val remembered: Int? = saved[activity]
        val current = window.attributes.windowAnimations
        val next = windowAnimationsFor(disabled, remembered)

        // 第一次关闭转场前先记下原值，供开关关掉后还原
        if (disabled && remembered == null) saved[activity] = current
        if (next != null && next != current) {
            try {
                val attributes = window.attributes
                attributes.windowAnimations = next
                window.attributes = attributes
                Logu.d(TAG, "页面窗口动画 = $next（原值 $current，disabled=$disabled）")
            } catch (e: Exception) {
                // 窗口已销毁等极端情况：动画关不掉不影响功能，别把页面弄崩
                Logu.w(TAG, "设置窗口动画失败：" + e.message)
            }
        }
        if (!disabled) saved.remove(activity)

        if (shouldSuppressTransition(disabled)) {
            try {
                activity.overridePendingTransition(0, 0)
            } catch (e: Exception) {
                Logu.w(TAG, "overridePendingTransition 失败：" + e.message)
            }
        }
    }

    /** 页面销毁时释放记录（[saved] 是弱表，这里主动清是为了不依赖 GC 时机）。 */
    @JvmStatic
    fun forget(activity: Activity) {
        saved.remove(activity)
    }
}
