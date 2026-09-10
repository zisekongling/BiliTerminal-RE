package com.RobinNotBad.BiliClient.ui.widget

import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewConfigurationCompat
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.SettingsKeys
import kotlin.math.roundToInt

/**
 * 表冠（旋转编码器）滚动的公共实现。
 *
 * 合并自原先三份近乎逐行的实现：[RotaryRecyclerView]、[RotaryScrollView]、[RotaryNestedScrollView]。
 * 三者只有「读哪个灵敏度设置键」与「滚动后是否抢焦点」两处差异，但基类分别是
 * `RecyclerView` / `ScrollView` / `NestedScrollView`，无法用共同父类归并，
 * 因此这里用**组合**：控件各自保留自己的事件接入方式（监听器 / dispatch 覆写），
 * 把「判定 + 换轴值 + 滚动」这段真正重复的逻辑收进来。
 *
 * 开关与灵敏度都只在 `onAttachedToWindow` 读一次（原有行为，改设置后需重新进页面才生效）。
 */
internal object RotaryEncoderSupport {

    /** 表冠滚动总开关（`SettingsKeys.UI_ROTATORY_ENABLE`，且需要 Android 8.0+）。 */
    fun isEnabled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                SharedPreferencesUtil.getBoolean(SettingsKeys.UI_ROTATORY_ENABLE, false)

    /**
     * 读取某个灵敏度设置键。
     *
     * 总开关关闭时恒返回 0——各控件原本都是 `rotaryEnabled && scrollMultiple > 0` 双条件，
     * 归并成一个「倍数 ≤ 0 即不生效」的判断。
     */
    fun multipleOf(key: String): Float =
        if (isEnabled()) SharedPreferencesUtil.getFloat(key, 0f) else 0f

    /**
     * 处理一次 generic motion 事件。
     *
     * 命中表冠滚动则滚动并返回 `true`；否则返回 `false`，由调用方继续走自己的默认分发。
     *
     * @param multiple    灵敏度倍率，≤ 0 直接不处理
     * @param requestFocus 是否在滚动后抢焦点。`RecyclerView`/`ScrollView` 原本会抢，
     *        `NestedScrollView` 原本不抢——这个差异原样保留，避免改变现有手感。
     * @param smoothScrollBy 竖直平滑滚动动作。`View` 本身没有可访问的 `smoothScrollBy`，
     *        只有各具体子类有，所以由调用方传进来（`{ smoothScrollBy(0, it) }`）。
     */
    fun handle(
        view: View,
        event: MotionEvent,
        multiple: Float,
        requestFocus: Boolean,
        smoothScrollBy: (Int) -> Unit
    ): Boolean {
        if (multiple <= 0f) return false
        if (event.action != MotionEvent.ACTION_SCROLL) return false
        if (event.source != InputDevice.SOURCE_ROTARY_ENCODER) return false

        val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) *
                ViewConfigurationCompat.getScaledVerticalScrollFactor(
                    ViewConfiguration.get(view.context), view.context
                ) * 2
        smoothScrollBy((delta * multiple).roundToInt())
        if (requestFocus) view.requestFocus()
        return true
    }
}
