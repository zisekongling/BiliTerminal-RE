package com.RobinNotBad.BiliClient.util

/**
 * 「长按顶部标题栏」手势的三模式与判据。
 *
 * <h3>三种模式</h3>
 * - [MODE_OFF]：全部页面都不响应。长按被吞掉，不会回落成单击；
 * - [MODE_VIDEO_ONLY]：只有视频详情页（`VideoInfoActivity`）响应，普通页面不响应；
 * - [MODE_ALL]：普通页面长按打开菜单列表，视频详情页长按关闭所有详情页（默认，与三模式上线前一致）。
 *
 * 「视频详情页响应」的动作是它原本就有的「关闭所有详情页」（`VideoInfoActivity.setupLongPressToRoot`），
 * 不是打开菜单——两个页面的长按动作本来就不同，这个设置只管**谁响应**，不改各自的动作。
 *
 * 菜单列表页（`MenuActivity`）本来就没有这条手势（`BaseActivity` 对它不安装监听，长按会回落成单击 =
 * 退出菜单），三模式管不到它，别为了「永远不生效」去给菜单页也装监听器——那会改掉它现有的长按行为。
 *
 * <h3>为什么默认值收在这里</h3>
 * 与 `LongPressPrefs` / `player/SkipOpEdPrefs` 同样的理由：默认值一旦散落在「设置页写的」
 * 和「监听器里读的」两处，改漏一处的症状是「设置页显示开着、实际却是关的」——这种错不报错、
 * 只能靠人肉比对发现。设置页必须引用 [DEFAULT_MODE]，不要再写 `"all"` 字面量。
 *
 * <h3>旧开关的迁移（26.10.19 → 26.10.20）</h3>
 * 26.10.19 那版是布尔开关 `SettingsKeys.TOPBAR_LONG_PRESS_MENU_ENABLE`：开 = 普通页面打开菜单、
 * 视频详情页照旧；关 = 只有视频详情页照旧。它正好对应 [MODE_ALL] 与 [MODE_VIDEO_ONLY]，
 * 所以读不到新键时按这个映射回退，老用户升级后行为不变（旧「关」不会突然变成「全部不响应」）。
 *
 * <h3>调用时机</h3>
 * 调用点是**长按按下那一刻**，不是页面 onStart：用户可能在别的页面改完设置再回来，
 * 监听器不需要重装，这里读到的就是最新值。
 *
 * 唯一例外是顶栏的**长按震动开关**（`View.isHapticFeedbackEnabled`）：框架在长按回调返回 true 后
 * 立刻发 `LONG_PRESS` 震动，而「吞掉手势」正是靠返回 true，所以「模式不适用」的页面也会震一下
 * （反馈 #153）。震动开关只能在按下之前设，由 `BaseActivity.syncTopbarLongPressHaptic()`
 * 在每次 onStart 同步一次；模式只能从别的页面改回来、回来必走 onStart，因此与按下时读值等效。
 */
object TopbarLongPressPrefs {

    /** 全部页面都不响应长按顶栏。 */
    const val MODE_OFF = "off"

    /** 只有视频详情页响应（长按 = 关闭所有详情页），普通页面不响应。 */
    const val MODE_VIDEO_ONLY = "video_only"

    /** 普通页面长按打开菜单列表，视频详情页长按关闭所有详情页（默认）。 */
    const val MODE_ALL = "all"

    /** 默认模式：三模式上线前的行为。 */
    const val DEFAULT_MODE = MODE_ALL

    /**
     * 当前模式，取值只会是三个 `MODE_*` 之一。
     *
     * 读取顺序：新键 → 26.10.19 的旧布尔开关（true = [MODE_ALL]、false = [MODE_VIDEO_ONLY]）→
     * [DEFAULT_MODE]。新键里出现脏值（手改存档 / 回滚过版本）时同样回退到旧键/默认值，
     * 不会把界面显示成「点击选择」那种半残状态。
     */
    @JvmStatic
    fun mode(): String {
        val saved = SharedPreferencesUtil.getString(SettingsKeys.TOPBAR_LONG_PRESS_MODE, "")
        if (saved == MODE_OFF || saved == MODE_VIDEO_ONLY || saved == MODE_ALL) return saved
        // 新键不存在（或不可识别）：从旧布尔开关迁移。旧键也没写过时默认 true → MODE_ALL。
        return if (SharedPreferencesUtil.getBoolean(SettingsKeys.TOPBAR_LONG_PRESS_MENU_ENABLE, true))
            MODE_ALL else MODE_VIDEO_ONLY
    }

    /** 普通页面（`BaseActivity.setupTopbarLongPressToHome`）是否响应长按顶栏。 */
    @JvmStatic
    fun isEnabledForNormalPages(): Boolean = mode() == MODE_ALL

    /** 视频详情页（`VideoInfoActivity`）是否响应长按顶栏。 */
    @JvmStatic
    fun isEnabledForVideoPages(): Boolean = mode() != MODE_OFF
}
