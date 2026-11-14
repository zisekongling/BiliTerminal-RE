package com.RobinNotBad.BiliClient.ui.appearance

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 沉浸式全屏：隐藏状态栏与导航栏，让页面 / 播放画面铺满整块屏幕（26.11.14）。
 *
 * <h3>为什么要收口在这里</h3>
 * 主题里一直有 `android:windowFullscreen=true`（只压掉**状态栏**），导航栏从来没有处理过；
 * 而 [ColorScheme.applyWindowTheme] 又打开了 `setDecorFitsSystemWindows(false)`，于是手表底部的
 * 三键导航栏在播放页单独占了一条黑边（26.11.14 用户实拍反馈）。收进一个对象里，别在页面里各写一份。
 *
 * <h3>开关</h3>
 * 用户 26.11.14 拍板「所有页面都隐藏状态栏和导航栏」，但手表上导航栏是主要的返回手段，全屏后
 * 边缘手势可能要划两次才生效，所以留 [SettingsKeys.UI_IMMERSIVE_FULLSCREEN] 当回退路径
 * （默认开，设置页「布局适配」里可关）。关掉时 [apply] 把系统栏放回来，等同 26.11.13 之前的行为。
 *
 * <h3>调用点</h3>
 * [com.RobinNotBad.BiliClient.activity.base.BaseActivity] 的 `onCreate` / `onResume`（覆盖所有普通页面）
 * 与 `PlayerActivity` 的同名回调（它不是 BaseActivity）。重复调用幂等。
 */
object ImmersiveMode {

    /** 开关当前是否为开（默认开，见 [SettingsKeys.UI_IMMERSIVE_FULLSCREEN]）。 */
    fun isEnabled(): Boolean =
        SharedPreferencesUtil.getBoolean(SettingsKeys.UI_IMMERSIVE_FULLSCREEN, true)

    /**
     * 按开关状态给窗口上一次状态：开着 → 隐藏系统栏；关着 → 让系统栏回来。
     *
     * 回到前台时系统栏可能已经被系统重新显示出来（弹过系统对话框、从别的页面回来），
     * 所以 `onResume` 也要调一次，不能只在 `onCreate` 调。
     */
    fun apply(activity: Activity) {
        if (isEnabled()) hideSystemBars(activity) else showSystemBars(activity)
    }

    /** 隐藏状态栏与导航栏，内容铺到屏幕四边。 */
    fun hideSystemBars(activity: Activity) {
        val window = activity.window
        // 内容要能画进系统栏原来占的位置。AppCompatActivity 那边外观模块已经设过 false，
        // 这里是为 PlayerActivity（不是 BaseActivity、不调 applyWindowTheme）补上；
        // 重复设置同一个值没有副作用。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // 从屏幕边缘往里划时，系统栏先短暂浮现再自动收起，划动本身不会被当成「返回」。
        // 这是视频页最常用的手感，代价是「返回」可能要多划一次（待人工验收，见 docs/review/l2-pending.md）。
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** 让状态栏与导航栏保持可见（开关关闭时的行为，等同改造前）。 */
    fun showSystemBars(activity: Activity) {
        val window = activity.window
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }
}
