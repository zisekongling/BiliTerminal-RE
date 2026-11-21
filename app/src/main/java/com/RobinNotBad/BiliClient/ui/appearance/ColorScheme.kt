package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.util.UiText

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 外观模块一：**配色**。
 *
 * 7 套主题各一张 [ThemeColors] 色表（36 个字段），由 [getCurrentTheme] 按 `theme_selector`
 * 选中，再经 36 个属性 getter 暴露给代码侧（view 层另有 `?attr/` 的 XML 路径）。
 *
 * ## 与门面的分工（见 `docs/architecture-map.md` §8.7）
 * 本对象是**只读模块**：色表、档位名、显示名、`key → style` 映射、以及 `?attr/` 覆盖不到的窗口着色。
 * **写入不在这里**——主题写入统一走 [AppearanceManager.setTheme]（它负责落盘 + 递增外观版本号），
 * 写完调用 [invalidateCache] 让下面的色表缓存失效。
 *
 * ## 色表缓存
 * 36 个 getter 全部走 [getCurrentTheme]，而列表滚动时一个 item 就要调很多次
 * （代码里 `setTextColor` 共 40 处，其中 26 处参数取自本对象）。缓存让这些调用
 * 只读一次 SharedPreferences。失效点只有 [invalidateCache] 一处，
 * 由唯一的写入路径 [AppearanceManager.setTheme] 触发。
 *
 * ## 已知技术债：三套并行的颜色值表
 * 本对象的 Kotlin 色表、`res/values/themes.xml`、`res/values/colors.xml`（含大量历史别名）
 * 是**三套独立值表**，靠人工对齐。收敛计划见 `docs/archive/visual-experience-report.md`。
 */
object ColorScheme {

    const val THEME_BILIBILI_PINK = "theme_bilibili_pink"
    const val THEME_ZHIHU_BLUE = "theme_zhihu_blue"
    const val THEME_IQIYI_GREEN = "theme_iqiyi_green"
    const val THEME_PURPLE_FANTASY = "theme_purple_fantasy"
    const val THEME_RAINBOW_FANTASY = "theme_rainbow_fantasy"
    const val THEME_CLASSIC_GRAY = "theme_classic_gray"
    const val THEME_CLASSIC_TERMINAL = "theme_classic_terminal"

    /** 主题的 SharedPreferences key：单一真源指向 [SettingsKeys.THEME]（此前两处各写一份字符串）。 */
    const val PREF_KEY_THEME = SettingsKeys.THEME

    // 当前默认主题：经典终端（对应老版 BiliClient 的默认黑色外观）
    const val THEME_DEFAULT = THEME_CLASSIC_TERMINAL

    sealed class ThemeColors(
        val PRIMARY: Int,
        val PRIMARY_DARK: Int,
        val PRIMARY_LIGHT: Int,
        val SECONDARY: Int,
        val SURFACE: Int,
        val CARD: Int,
        val CARD_WITH_ALPHA: Int,
        val BACKGROUND: Int,
        val TEXT_PRIMARY: Int,
        val TEXT_SECONDARY: Int,
        val TEXT_TERTIARY: Int,
        val ON_PRIMARY: Int,
        val ON_SURFACE: Int,
        val ON_CARD: Int,
        val ON_BACKGROUND: Int,
        val ON_BUTTON: Int,
        val LIKE_COLOR: Int,
        val COIN_COLOR: Int,
        val FAV_COLOR: Int,
        val SHARE_COLOR: Int,
        val SUCCESS: Int,
        val WARNING: Int,
        val ERROR: Int,
        val INFO: Int,
        val PLAYER_BG: Int,
        val PLAYER_CONTROL_BG: Int,
        val PLAYER_PROGRESS_BG: Int,
        val PLAYER_PROGRESS_FILL: Int,
        val BORDER: Int,
        val DIVIDER: Int,
        val RIPPLE: Int,
        val GOLD: Int,
        val VIP_COLOR: Int,
        val STATUS_BAR_COLOR: Int,
        val NAV_BAR_COLOR: Int,
        val CORNER_RADIUS: Float,
        /**
         * 正文链接色（URL / BV 号 / @ 等可点文字）。此前 `StringUtil` 写死老版的 #66CCFF，
         * 在绿 / 紫 / 粉等主题下是唯一一抹不相干的青蓝。各主题取「主色调的高亮档」，
         * 保证对深色底 ≥ 4.5:1；经典终端 / 经典灰 / B站粉 沿用 #66CCFF（与原观感一致）。
         */
        val LINK: Int = 0xFF66CCFF.toInt()
    )

    object BilibiliPink : ThemeColors(
        PRIMARY = 0xFFFF6699.toInt(),
        PRIMARY_DARK = 0xFFE84B85.toInt(),
        PRIMARY_LIGHT = 0xFFFF8CB0.toInt(),
        SECONDARY = 0xFFFFB3CA.toInt(),
        SURFACE = 0xFF24242E.toInt(),
        CARD = 0xFF2A2A35.toInt(),
        CARD_WITH_ALPHA = 0xCC2A2A35.toInt(),
        BACKGROUND = 0xFF1B1B24.toInt(),
        TEXT_PRIMARY = 0xFFF0E8EC.toInt(),
        TEXT_SECONDARY = 0xFFB0A0AA.toInt(),
        TEXT_TERTIARY = 0xFF7A6A74.toInt(),
        ON_PRIMARY = 0xFFFFFFFF.toInt(),
        ON_SURFACE = 0xFFF0E8EC.toInt(),
        ON_CARD = 0xFFF0E8EC.toInt(),
        ON_BACKGROUND = 0xFFF0E8EC.toInt(),
        ON_BUTTON = 0xFFF0E8EC.toInt(),
        LIKE_COLOR = 0xFFFF6A6A.toInt(),
        COIN_COLOR = 0xFFFFB800.toInt(),
        FAV_COLOR = 0xFFFF6699.toInt(),
        SHARE_COLOR = 0xFFFF8CB0.toInt(),
        SUCCESS = 0xFF52C41A.toInt(),
        WARNING = 0xFFFAAD14.toInt(),
        ERROR = 0xFFE84B85.toInt(),
        INFO = 0xFF1890FF.toInt(),
        PLAYER_BG = 0xFF000000.toInt(),
        PLAYER_CONTROL_BG = 0x33000000.toInt(),
        PLAYER_PROGRESS_BG = 0x55FFFFFF.toInt(),
        PLAYER_PROGRESS_FILL = 0xFFFF6699.toInt(),
        BORDER = 0x20F0E8EC.toInt(),
        DIVIDER = 0x10F0E8EC.toInt(),
        RIPPLE = 0x50FF6699.toInt(),
        GOLD = 0xFFFFB800.toInt(),
        VIP_COLOR = 0xFFFF6699.toInt(),
        STATUS_BAR_COLOR = 0xFFFF6699.toInt(),
        NAV_BAR_COLOR = 0xFF1B1B24.toInt(),
        CORNER_RADIUS = 12f
    )

    object ZhihuBlue : ThemeColors(
        PRIMARY = 0xFF056DE8.toInt(),
        PRIMARY_DARK = 0xFF0354B5.toInt(),
        PRIMARY_LIGHT = 0xFF3B8AF2.toInt(),
        SECONDARY = 0xFF6DB0FF.toInt(),
        SURFACE = 0xFF24242E.toInt(),
        CARD = 0xFF2A2A35.toInt(),
        CARD_WITH_ALPHA = 0xCC2A2A35.toInt(),
        BACKGROUND = 0xFF1B1B24.toInt(),
        TEXT_PRIMARY = 0xFFF0F7FF.toInt(),
        TEXT_SECONDARY = 0xFF8BB8E8.toInt(),
        TEXT_TERTIARY = 0xFF5A7FA5.toInt(),
        ON_PRIMARY = 0xFFFFFFFF.toInt(),
        ON_SURFACE = 0xFFF0F7FF.toInt(),
        ON_CARD = 0xFFF0F7FF.toInt(),
        ON_BACKGROUND = 0xFFF0F7FF.toInt(),
        ON_BUTTON = 0xFFF0F7FF.toInt(),
        LIKE_COLOR = 0xFFFF6A6A.toInt(),
        COIN_COLOR = 0xFFFFB800.toInt(),
        FAV_COLOR = 0xFF056DE8.toInt(),
        SHARE_COLOR = 0xFF3B8AF2.toInt(),
        SUCCESS = 0xFF52C41A.toInt(),
        WARNING = 0xFFFAAD14.toInt(),
        ERROR = 0xFFE84B85.toInt(),
        INFO = 0xFF056DE8.toInt(),
        PLAYER_BG = 0xFF000000.toInt(),
        PLAYER_CONTROL_BG = 0x33000000.toInt(),
        PLAYER_PROGRESS_BG = 0x55FFFFFF.toInt(),
        PLAYER_PROGRESS_FILL = 0xFF056DE8.toInt(),
        BORDER = 0xFF204A87.toInt(),
        DIVIDER = 0xFF1A3A6E.toInt(),
        RIPPLE = 0x50056DE8.toInt(),
        GOLD = 0xFFFFB800.toInt(),
        VIP_COLOR = 0xFF056DE8.toInt(),
        STATUS_BAR_COLOR = 0xFF056DE8.toInt(),
        NAV_BAR_COLOR = 0xFF1B1B24.toInt(),
        CORNER_RADIUS = 12f,
        LINK = 0xFF6DB0FF.toInt()
    )

    object IQIYIGreen : ThemeColors(
        PRIMARY = 0xFF00DC5A.toInt(),
        PRIMARY_DARK = 0xFF00B347.toInt(),
        PRIMARY_LIGHT = 0xFF33E67A.toInt(),
        SECONDARY = 0xFF66F0A3.toInt(),
        SURFACE = 0xFF24242E.toInt(),
        CARD = 0xFF2A2A35.toInt(),
        CARD_WITH_ALPHA = 0xCC2A2A35.toInt(),
        BACKGROUND = 0xFF1B1B24.toInt(),
        TEXT_PRIMARY = 0xFFE6FDF0.toInt(),
        TEXT_SECONDARY = 0xFF80E6AB.toInt(),
        TEXT_TERTIARY = 0xFF55B37A.toInt(),
        ON_PRIMARY = 0xFFFFFFFF.toInt(),
        ON_SURFACE = 0xFFE6FDF0.toInt(),
        ON_CARD = 0xFFE6FDF0.toInt(),
        ON_BACKGROUND = 0xFFE6FDF0.toInt(),
        ON_BUTTON = 0xFFE6FDF0.toInt(),
        LIKE_COLOR = 0xFFFF6A6A.toInt(),
        COIN_COLOR = 0xFFFFB800.toInt(),
        FAV_COLOR = 0xFF00DC5A.toInt(),
        SHARE_COLOR = 0xFF33E67A.toInt(),
        SUCCESS = 0xFF52C41A.toInt(),
        WARNING = 0xFFFAAD14.toInt(),
        ERROR = 0xFFE84B85.toInt(),
        INFO = 0xFF00DC5A.toInt(),
        PLAYER_BG = 0xFF000000.toInt(),
        PLAYER_CONTROL_BG = 0x33000000.toInt(),
        PLAYER_PROGRESS_BG = 0x55FFFFFF.toInt(),
        PLAYER_PROGRESS_FILL = 0xFF00DC5A.toInt(),
        BORDER = 0xFF006633.toInt(),
        DIVIDER = 0xFF004D29.toInt(),
        RIPPLE = 0x5000DC5A.toInt(),
        GOLD = 0xFFFFB800.toInt(),
        VIP_COLOR = 0xFF00DC5A.toInt(),
        STATUS_BAR_COLOR = 0xFF00DC5A.toInt(),
        NAV_BAR_COLOR = 0xFF1B1B24.toInt(),
        CORNER_RADIUS = 12f,
        LINK = 0xFF66F0A3.toInt()
    )

    object PurpleFantasy : ThemeColors(
        PRIMARY = 0xFF7B2CBF.toInt(),
        PRIMARY_DARK = 0xFF5A1F8F.toInt(),
        PRIMARY_LIGHT = 0xFF9B59D0.toInt(),
        SECONDARY = 0xFFBA7FE8.toInt(),
        SURFACE = 0xFF24242E.toInt(),
        CARD = 0xFF2A2A35.toInt(),
        CARD_WITH_ALPHA = 0xCC2A2A35.toInt(),
        BACKGROUND = 0xFF1B1B24.toInt(),
        TEXT_PRIMARY = 0xFFF5EBFC.toInt(),
        TEXT_SECONDARY = 0xFFC4A8E0.toInt(),
        TEXT_TERTIARY = 0xFF8B6BA8.toInt(),
        ON_PRIMARY = 0xFFFFFFFF.toInt(),
        ON_SURFACE = 0xFFF5EBFC.toInt(),
        ON_CARD = 0xFFF5EBFC.toInt(),
        ON_BACKGROUND = 0xFFF5EBFC.toInt(),
        ON_BUTTON = 0xFFF5EBFC.toInt(),
        LIKE_COLOR = 0xFFFF6A6A.toInt(),
        COIN_COLOR = 0xFFFFB800.toInt(),
        FAV_COLOR = 0xFF7B2CBF.toInt(),
        SHARE_COLOR = 0xFF9B59D0.toInt(),
        SUCCESS = 0xFF52C41A.toInt(),
        WARNING = 0xFFFAAD14.toInt(),
        ERROR = 0xFFE84B85.toInt(),
        INFO = 0xFF7B2CBF.toInt(),
        PLAYER_BG = 0xFF000000.toInt(),
        PLAYER_CONTROL_BG = 0x33000000.toInt(),
        PLAYER_PROGRESS_BG = 0x55FFFFFF.toInt(),
        PLAYER_PROGRESS_FILL = 0xFF7B2CBF.toInt(),
        BORDER = 0xFF3D1A66.toInt(),
        DIVIDER = 0xFF2E124D.toInt(),
        RIPPLE = 0x507B2CBF.toInt(),
        GOLD = 0xFFFFB800.toInt(),
        VIP_COLOR = 0xFF7B2CBF.toInt(),
        STATUS_BAR_COLOR = 0xFF7B2CBF.toInt(),
        NAV_BAR_COLOR = 0xFF1B1B24.toInt(),
        CORNER_RADIUS = 12f,
        LINK = 0xFFC9A0F0.toInt()
    )

    object RainbowFantasy : ThemeColors(
        PRIMARY = 0xFFFF6B6B.toInt(),
        PRIMARY_DARK = 0xFFCC5555.toInt(),
        // 约定：PRIMARY_LIGHT == xml 的 colorPrimaryVariant（rainbow_accent）、SECONDARY == colorSecondary（rainbow_light）。
        // 此前这两个值与 xml 恰好互换，导致代码路径与 xml 控件的强调色相反。
        PRIMARY_LIGHT = 0xFFFFE66D.toInt(),
        SECONDARY = 0xFFFF8E8E.toInt(),
        SURFACE = 0xFF24242E.toInt(),
        CARD = 0xFF2A2A35.toInt(),
        CARD_WITH_ALPHA = 0xCC2A2A35.toInt(),
        BACKGROUND = 0xFF1B1B24.toInt(),
        TEXT_PRIMARY = 0xFFFFF5F5.toInt(),
        TEXT_SECONDARY = 0xFFFFAFAF.toInt(),
        TEXT_TERTIARY = 0xFFCC8888.toInt(),
        ON_PRIMARY = 0xFFFFFFFF.toInt(),
        ON_SURFACE = 0xFFFFF5F5.toInt(),
        ON_CARD = 0xFFFFF5F5.toInt(),
        ON_BACKGROUND = 0xFFFFF5F5.toInt(),
        ON_BUTTON = 0xFFFFF5F5.toInt(),
        LIKE_COLOR = 0xFFFF6A6A.toInt(),
        COIN_COLOR = 0xFFFFE66D.toInt(),
        FAV_COLOR = 0xFFFF6B6B.toInt(),
        SHARE_COLOR = 0xFFFF9F43.toInt(),
        SUCCESS = 0xFF52C41A.toInt(),
        WARNING = 0xFFFAAD14.toInt(),
        ERROR = 0xFFE84B85.toInt(),
        INFO = 0xFFFF6B6B.toInt(),
        PLAYER_BG = 0xFF000000.toInt(),
        PLAYER_CONTROL_BG = 0x33000000.toInt(),
        PLAYER_PROGRESS_BG = 0x55FFFFFF.toInt(),
        PLAYER_PROGRESS_FILL = 0xFFFF6B6B.toInt(),
        BORDER = 0xFF663333.toInt(),
        DIVIDER = 0xFF4D2929.toInt(),
        RIPPLE = 0x50FF6B6B.toInt(),
        GOLD = 0xFFFFE66D.toInt(),
        VIP_COLOR = 0xFFFF6B6B.toInt(),
        STATUS_BAR_COLOR = 0xFFFF6B6B.toInt(),
        NAV_BAR_COLOR = 0xFF1B1B24.toInt(),
        CORNER_RADIUS = 12f,
        LINK = 0xFF45B7D1.toInt()
    )

    object ClassicGray : ThemeColors(
        PRIMARY = 0xFF8787FB.toInt(),
        PRIMARY_DARK = 0xFF6B6BDC.toInt(),
        PRIMARY_LIGHT = 0xFFA3A3FD.toInt(),
        SECONDARY = 0xFFC8C8FF.toInt(),
        SURFACE = 0xFF24242E.toInt(),
        CARD = 0xFF2A2A35.toInt(),
        CARD_WITH_ALPHA = 0xCC2A2A35.toInt(),
        BACKGROUND = 0xFF1B1B24.toInt(),
        TEXT_PRIMARY = 0xFFEBE0E2.toInt(),
        TEXT_SECONDARY = 0xFFA09098.toInt(),
        TEXT_TERTIARY = 0xFF706870.toInt(),
        ON_PRIMARY = 0xFFFFFFFF.toInt(),
        ON_SURFACE = 0xFFEBE0E2.toInt(),
        ON_CARD = 0xFFEBE0E2.toInt(),
        ON_BACKGROUND = 0xFFEBE0E2.toInt(),
        ON_BUTTON = 0xFFEBE0E2.toInt(),
        LIKE_COLOR = 0xFFFF6A6A.toInt(),
        COIN_COLOR = 0xFFFFB800.toInt(),
        FAV_COLOR = 0xFF8787FB.toInt(),
        SHARE_COLOR = 0xFF66CCFF.toInt(),
        SUCCESS = 0xFF52C41A.toInt(),
        WARNING = 0xFFFAAD14.toInt(),
        ERROR = 0xFFFF6A6A.toInt(),
        INFO = 0xFF66CCFF.toInt(),
        PLAYER_BG = 0xFF000000.toInt(),
        PLAYER_CONTROL_BG = 0x33000000.toInt(),
        PLAYER_PROGRESS_BG = 0x55FFFFFF.toInt(),
        PLAYER_PROGRESS_FILL = 0xFF8787FB.toInt(),
        BORDER = 0xFF454555.toInt(),
        DIVIDER = 0xFF353545.toInt(),
        RIPPLE = 0x508787FB.toInt(),
        GOLD = 0xFFFFB800.toInt(),
        VIP_COLOR = 0xFF8787FB.toInt(),
        STATUS_BAR_COLOR = 0xFF8787FB.toInt(),
        NAV_BAR_COLOR = 0xFF1B1B24.toInt(),
        CORNER_RADIUS = 12f
    )

    // 经典终端主题：基底复刻老版 BiliClient（黑底/暗卡 #cc262626/暖白字 #ebe0e2/
    // 分隔线 #50FEFEFE/链接 #66ccff 等原样保留），粉色系主点缀改用 B站粉 family
    // 鲜艳主色 #FF6699 / #FF8CB0 / #E84B85 / #FFB3CA，使终端默认观感更鲜艳。
    object ClassicTerminal : ThemeColors(
        PRIMARY = 0xFFFF6699.toInt(),            // B站粉主色（原老版 #FB8787）
        PRIMARY_DARK = 0xFFE84B85.toInt(),       // B站粉 deep
        PRIMARY_LIGHT = 0xFFFF8CB0.toInt(),      // B站粉 accent
        SECONDARY = 0xFFFFB3CA.toInt(),          // B站粉 light
        SURFACE = 0xFF262626.toInt(),            // 卡片等效不透明
        CARD = 0xCC262626.toInt(),               // 与 xml 的 terminal_card_bg(#CC262626) 对齐（此前丢了 alpha，两条渲染路径不一致）
        CARD_WITH_ALPHA = 0xCC262626.toInt(),    // 老版卡片 #cc262626
        BACKGROUND = 0xFF000000.toInt(),         // bgblack
        TEXT_PRIMARY = 0xFFEBE0E2.toInt(),       // textwhite
        // 老版只有一种文字色，但那样「标题/播放量/UP 主」在默认主题下完全同色，
        // 层级只能靠 alpha 硬凑（实测 11sp + alpha0.5 对卡片底的对比度约 4.24:1，不到 AA）。
        // 这里拆出可区分的次要/三级文字色，保持暖白调性：
        TEXT_SECONDARY = 0xFFB8AEB2.toInt(),     // 对纯黑约 9:1
        TEXT_TERTIARY = 0xFF8C868A.toInt(),      // 对纯黑约 6:1
        ON_PRIMARY = 0xFFEBE0E2.toInt(),
        ON_SURFACE = 0xFFEBE0E2.toInt(),
        ON_CARD = 0xFFEBE0E2.toInt(),
        ON_BACKGROUND = 0xFFEBE0E2.toInt(),
        ON_BUTTON = 0xFFEBE0E2.toInt(),
        LIKE_COLOR = 0xFFFF6A6A.toInt(),         // B站粉 点赞红
        COIN_COLOR = 0xFFFFB800.toInt(),
        FAV_COLOR = 0xFFFF6699.toInt(),          // B站粉主色
        SHARE_COLOR = 0xFF66CCFF.toInt(),        // link
        SUCCESS = 0xFFBBFFBB.toInt(),            // light_green #bfb
        WARNING = 0xFFFAAD14.toInt(),
        ERROR = 0xFFFF6699.toInt(),
        INFO = 0xFF66CCFF.toInt(),               // link
        PLAYER_BG = 0xFF000000.toInt(),
        PLAYER_CONTROL_BG = 0x33000000.toInt(),
        PLAYER_PROGRESS_BG = 0x55FFFFFF.toInt(),
        PLAYER_PROGRESS_FILL = 0xFFFF6699.toInt(),
        BORDER = 0x50FEFEFE.toInt(),             // 老版分隔线即 text_transparent
        DIVIDER = 0x50FEFEFE.toInt(),
        RIPPLE = 0x78FEFEFE.toInt(),             // color_ripple (精确)
        GOLD = 0xFFFFB800.toInt(),
        VIP_COLOR = 0xFFFF6699.toInt(),
        STATUS_BAR_COLOR = 0xFFFF6699.toInt(),   // 鲜艳粉顶栏
        NAV_BAR_COLOR = 0xFF000000.toInt(),
        CORNER_RADIUS = 6f                       // 老版 card_round 6dp
    )

    // ==================== 亮色模式（见 ColorMode） ====================

    /**
     * 一套主题在亮色模式下要换掉的字段；未列出的（点赞红、播放器、圆角等）沿用暗色表。
     * ⚠️ 与 `res/values-notnight/colors_light.xml` 同源（同一份数据由 tools/appearance/gen_light_mode.py 生成），改色两边一起改。
     */
    class LightPalette(
        val primary: Int,
        val primaryDark: Int,
        val primaryLight: Int,
        val secondary: Int,
        val background: Int,
        val surface: Int,
        val card: Int,
        val cardWithAlpha: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textTertiary: Int,
        val border: Int,
        val divider: Int,
        val ripple: Int,
        val link: Int,
        val info: Int,
        val share: Int,
        val success: Int? = null,
        val gold: Int? = null
    )

    /** 暗色表 + 亮色覆盖 = 亮色表。导航栏取亮色背景（系统栏图标随之变深）。 */
    private class LightColors(base: ThemeColors, p: LightPalette) : ThemeColors(
        PRIMARY = p.primary,
        PRIMARY_DARK = p.primaryDark,
        PRIMARY_LIGHT = p.primaryLight,
        SECONDARY = p.secondary,
        SURFACE = p.surface,
        CARD = p.card,
        CARD_WITH_ALPHA = p.cardWithAlpha,
        BACKGROUND = p.background,
        TEXT_PRIMARY = p.textPrimary,
        TEXT_SECONDARY = p.textSecondary,
        TEXT_TERTIARY = p.textTertiary,
        ON_PRIMARY = base.ON_PRIMARY,
        ON_SURFACE = p.textPrimary,
        ON_CARD = p.textPrimary,
        ON_BACKGROUND = p.textPrimary,
        ON_BUTTON = p.textPrimary,
        LIKE_COLOR = base.LIKE_COLOR,
        COIN_COLOR = p.gold ?: base.COIN_COLOR,
        FAV_COLOR = p.primary,
        SHARE_COLOR = p.share,
        SUCCESS = p.success ?: base.SUCCESS,
        WARNING = base.WARNING,
        ERROR = base.ERROR,
        INFO = p.info,
        PLAYER_BG = base.PLAYER_BG,
        PLAYER_CONTROL_BG = base.PLAYER_CONTROL_BG,
        PLAYER_PROGRESS_BG = base.PLAYER_PROGRESS_BG,
        PLAYER_PROGRESS_FILL = p.primary,
        BORDER = p.border,
        DIVIDER = p.divider,
        RIPPLE = p.ripple,
        GOLD = p.gold ?: base.GOLD,
        VIP_COLOR = p.primary,
        STATUS_BAR_COLOR = p.primary,
        NAV_BAR_COLOR = p.background,
        CORNER_RADIUS = base.CORNER_RADIUS,
        LINK = p.link
    )

    private val LIGHT_PALETTES: Map<ThemeColors, LightPalette> by lazy {
        mapOf(
        BilibiliPink to LightPalette(
            primary = 0xFFFF6699.toInt(),
            primaryDark = 0xFFE84B85.toInt(),
            primaryLight = 0xFFE84B85.toInt(),
            secondary = 0xFFFFB3CA.toInt(),
            background = 0xFFF4E7DF.toInt(),
            surface = 0xFFFAF0E8.toInt(),
            card = 0xFFFEF7F1.toInt(),
            cardWithAlpha = 0xE6FEF7F1.toInt(),
            textPrimary = 0xFF2B2026.toInt(),
            textSecondary = 0xFF6B5862.toInt(),
            textTertiary = 0xFF9A8892.toInt(),
            border = 0xFFE7DBD1.toInt(),
            divider = 0xFFEEE2D9.toInt(),
            ripple = 0x30FF6699.toInt(),
            link = 0xFF1A8FD8.toInt(),
            info = 0xFF1890FF.toInt(),
            share = 0xFFE84B85.toInt()
        ),
        ZhihuBlue to LightPalette(
            primary = 0xFF056DE8.toInt(),
            primaryDark = 0xFF0354B5.toInt(),
            primaryLight = 0xFF2F7FE6.toInt(),
            secondary = 0xFF6DB0FF.toInt(),
            background = 0xFFEEE9E1.toInt(),
            surface = 0xFFF6F1E9.toInt(),
            card = 0xFFFBF8F1.toInt(),
            cardWithAlpha = 0xE6FBF8F1.toInt(),
            textPrimary = 0xFF15202E.toInt(),
            textSecondary = 0xFF4F5F73.toInt(),
            textTertiary = 0xFF8090A3.toInt(),
            border = 0xFFE2DCD3.toInt(),
            divider = 0xFFE9E3DB.toInt(),
            ripple = 0x30056DE8.toInt(),
            link = 0xFF0A63C9.toInt(),
            info = 0xFF056DE8.toInt(),
            share = 0xFF0354B5.toInt()
        ),
        IQIYIGreen to LightPalette(
            primary = 0xFF00A84E.toInt(),
            primaryDark = 0xFF008A3F.toInt(),
            primaryLight = 0xFF00873E.toInt(),
            secondary = 0xFF33D47A.toInt(),
            background = 0xFFEDEADC.toInt(),
            surface = 0xFFF5F1E6.toInt(),
            card = 0xFFFBF8F0.toInt(),
            cardWithAlpha = 0xE6FBF8F0.toInt(),
            textPrimary = 0xFF13241A.toInt(),
            textSecondary = 0xFF4A6656.toInt(),
            textTertiary = 0xFF7C9686.toInt(),
            border = 0xFFE1DDCF.toInt(),
            divider = 0xFFE8E4D6.toInt(),
            ripple = 0x3000A84E.toInt(),
            link = 0xFF008A3F.toInt(),
            info = 0xFF00A84E.toInt(),
            share = 0xFF008A3F.toInt()
        ),
        PurpleFantasy to LightPalette(
            primary = 0xFF7B2CBF.toInt(),
            primaryDark = 0xFF5A1F8F.toInt(),
            primaryLight = 0xFF8A45C8.toInt(),
            secondary = 0xFFBA7FE8.toInt(),
            background = 0xFFF2E8E1.toInt(),
            surface = 0xFFF8F0E9.toInt(),
            card = 0xFFFDF7F1.toInt(),
            cardWithAlpha = 0xE6FDF7F1.toInt(),
            textPrimary = 0xFF231A2E.toInt(),
            textSecondary = 0xFF5D4D70.toInt(),
            textTertiary = 0xFF8E80A0.toInt(),
            border = 0xFFE6DBD3.toInt(),
            divider = 0xFFECE2DB.toInt(),
            ripple = 0x307B2CBF.toInt(),
            link = 0xFF6A22AD.toInt(),
            info = 0xFF7B2CBF.toInt(),
            share = 0xFF5A1F8F.toInt()
        ),
        RainbowFantasy to LightPalette(
            primary = 0xFFF25C5C.toInt(),
            primaryDark = 0xFFC94444.toInt(),
            primaryLight = 0xFFB87D00.toInt(),
            secondary = 0xFFFF8E8E.toInt(),
            background = 0xFFF5E7DA.toInt(),
            surface = 0xFFFAF0E5.toInt(),
            card = 0xFFFEF7EF.toInt(),
            cardWithAlpha = 0xE6FEF7EF.toInt(),
            textPrimary = 0xFF2D1D1D.toInt(),
            textSecondary = 0xFF6D5454.toInt(),
            textTertiary = 0xFFA08787.toInt(),
            border = 0xFFE8DBCD.toInt(),
            divider = 0xFFEFE2D5.toInt(),
            ripple = 0x30F25C5C.toInt(),
            link = 0xFF1F8FB0.toInt(),
            info = 0xFFF25C5C.toInt(),
            share = 0xFFE07B1F.toInt(),
            gold = 0xFFE6A800.toInt()
        ),
        ClassicGray to LightPalette(
            primary = 0xFF6E6EE6.toInt(),
            primaryDark = 0xFF5555C8.toInt(),
            primaryLight = 0xFF6464DC.toInt(),
            secondary = 0xFFA3A3FD.toInt(),
            background = 0xFFF0E8E1.toInt(),
            surface = 0xFFF7F1E9.toInt(),
            card = 0xFFFCF7F1.toInt(),
            cardWithAlpha = 0xE6FCF7F1.toInt(),
            textPrimary = 0xFF1E1E2A.toInt(),
            textSecondary = 0xFF595968.toInt(),
            textTertiary = 0xFF8A8A9A.toInt(),
            border = 0xFFE4DBD3.toInt(),
            divider = 0xFFEBE3DA.toInt(),
            ripple = 0x306E6EE6.toInt(),
            link = 0xFF1E88C8.toInt(),
            info = 0xFF1E88C8.toInt(),
            share = 0xFF1E88C8.toInt()
        ),
        ClassicTerminal to LightPalette(
            primary = 0xFFFF6699.toInt(),
            primaryDark = 0xFFE84B85.toInt(),
            primaryLight = 0xFFD9437A.toInt(),
            secondary = 0xFFFFB3CA.toInt(),
            background = 0xFFEBDFCF.toInt(),
            surface = 0xFFF6EDDF.toInt(),
            card = 0xCCFDF6EC.toInt(),
            cardWithAlpha = 0xCCFDF6EC.toInt(),
            textPrimary = 0xFF2A2326.toInt(),
            textSecondary = 0xFF6B6266.toInt(),
            textTertiary = 0xFF958B90.toInt(),
            border = 0x33000000.toInt(),
            divider = 0x26000000.toInt(),
            ripple = 0x26000000.toInt(),
            link = 0xFF1E88C8.toInt(),
            info = 0xFF1E88C8.toInt(),
            share = 0xFF1E88C8.toInt(),
            success = 0xFF2E9E44.toInt()
        )
        )
    }

    /** 亮色表按暗色表实例缓存（每套主题只建一次）。 */
    private val lightTables = HashMap<ThemeColors, ThemeColors>()

    private fun lightOf(base: ThemeColors): ThemeColors = synchronized(lightTables) {
        lightTables.getOrPut(base) { LIGHT_PALETTES[base]?.let { LightColors(base, it) } ?: base }
    }

    /** 主题 key → 暗色表（模式无关）。 */
    private fun darkTableOf(theme: String): ThemeColors = when (theme) {
        THEME_ZHIHU_BLUE -> ZhihuBlue
        THEME_IQIYI_GREEN -> IQIYIGreen
        THEME_PURPLE_FANTASY -> PurpleFantasy
        THEME_RAINBOW_FANTASY -> RainbowFantasy
        THEME_CLASSIC_GRAY -> ClassicGray
        THEME_CLASSIC_TERMINAL -> ClassicTerminal
        else -> BilibiliPink
    }

    /**
     * 当前主题的**暗色**表，不管当前是什么模式。给永远暗色的播放器（`PlayerActivity` 用
     * `forceDark` 上下文）里的代码设色用——那里的资源是暗色，全局 getter 在亮色模式下却会返回亮色值。
     */
    fun darkColors(): ThemeColors = darkTableOf(getCurrentThemeName())

    /**
     * 当前主题色表的缓存。
     *
     * [PRIMARY] 等 36 个属性 getter 全部走 [getCurrentTheme]，而列表滚动时一个 item 就要读很多次
     * （代码里 `setTextColor` 共 40 处，其中 26 处参数取自本对象）。此前每次 getter 都会
     * 重新读一遍 SharedPreferences 再跑一遍 when——是热路径上的重复 IO。
     *
     * 主题 key 的**唯一写入路径是 [AppearanceManager.setTheme]**（全仓库 grep 确认：其余引用全是读），
     * 它写完会调用 [invalidateCache]；进程被杀后缓存为 null 会自动重算。
     * `@Volatile` 保证这份单例引用在写入线程与读线程之间正确发布。
     */
    @Volatile
    private var cachedColors: ThemeColors? = null

    private fun getCurrentTheme(): ThemeColors {
        cachedColors?.let { return it }
        val theme = SharedPreferencesUtil.getString(PREF_KEY_THEME, THEME_DEFAULT)
        val dark = darkTableOf(theme)
        // 亮 / 暗模式也只在缓存未命中时读一次（切换模式走 AppearanceManager.setColorMode，同样会 invalidateCache）
        val colors = if (ColorMode.isLight()) lightOf(dark) else dark
        cachedColors = colors
        return colors
    }

    val PRIMARY get() = getCurrentTheme().PRIMARY
    val PRIMARY_DARK get() = getCurrentTheme().PRIMARY_DARK
    val PRIMARY_LIGHT get() = getCurrentTheme().PRIMARY_LIGHT
    val SECONDARY get() = getCurrentTheme().SECONDARY
    val SURFACE get() = getCurrentTheme().SURFACE
    val CARD get() = getCurrentTheme().CARD
    val CARD_WITH_ALPHA get() = getCurrentTheme().CARD_WITH_ALPHA
    val BACKGROUND get() = getCurrentTheme().BACKGROUND
    val TEXT_PRIMARY get() = getCurrentTheme().TEXT_PRIMARY
    val TEXT_SECONDARY get() = getCurrentTheme().TEXT_SECONDARY
    val TEXT_TERTIARY get() = getCurrentTheme().TEXT_TERTIARY
    val ON_PRIMARY get() = getCurrentTheme().ON_PRIMARY
    val ON_SURFACE get() = getCurrentTheme().ON_SURFACE
    val ON_CARD get() = getCurrentTheme().ON_CARD
    val ON_BACKGROUND get() = getCurrentTheme().ON_BACKGROUND
    val ON_BUTTON get() = getCurrentTheme().ON_BUTTON
    val LIKE_COLOR get() = getCurrentTheme().LIKE_COLOR
    val COIN_COLOR get() = getCurrentTheme().COIN_COLOR
    val FAV_COLOR get() = getCurrentTheme().FAV_COLOR
    val SHARE_COLOR get() = getCurrentTheme().SHARE_COLOR
    val SUCCESS get() = getCurrentTheme().SUCCESS
    val WARNING get() = getCurrentTheme().WARNING
    val ERROR get() = getCurrentTheme().ERROR
    val INFO get() = getCurrentTheme().INFO
    val PLAYER_BG get() = getCurrentTheme().PLAYER_BG
    val PLAYER_CONTROL_BG get() = getCurrentTheme().PLAYER_CONTROL_BG
    val PLAYER_PROGRESS_BG get() = getCurrentTheme().PLAYER_PROGRESS_BG
    val PLAYER_PROGRESS_FILL get() = getCurrentTheme().PLAYER_PROGRESS_FILL
    val BORDER get() = getCurrentTheme().BORDER
    val DIVIDER get() = getCurrentTheme().DIVIDER
    val RIPPLE get() = getCurrentTheme().RIPPLE
    val GOLD get() = getCurrentTheme().GOLD
    val VIP_COLOR get() = getCurrentTheme().VIP_COLOR
    val STATUS_BAR_COLOR get() = getCurrentTheme().STATUS_BAR_COLOR
    val NAV_BAR_COLOR get() = getCurrentTheme().NAV_BAR_COLOR
    val CORNER_RADIUS get() = getCurrentTheme().CORNER_RADIUS
    val LINK get() = getCurrentTheme().LINK

    fun applyWindowTheme(activity: Activity) {
        val window: Window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // setStatusBarColor / setNavigationBarColor 是 API 21；minSdk 19 上直接调会 NoSuchMethodError。
        // Android 4.4 的系统栏本来就不支持自定义底色，跳过即可（下面的图标深浅逻辑同样只对 21+ 有意义）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = STATUS_BAR_COLOR
            window.navigationBarColor = NAV_BAR_COLOR
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = SURFACE
        }

        // 按系统栏底色的亮度决定图标深浅（亮底配深色图标，暗底配浅色图标）。
        //
        // 旧实现是 `flags or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()`：对一个取反的常量做「或」
        // 等于把 0xFFFFDFFF 的所有位都置 1 —— 既清掉了 LIGHT_STATUS_BAR（状态栏图标恒浅色，
        // 压在浅色品牌底上对比度不足），又把 LIGHT_NAVIGATION_BAR 打开（导航栏图标恒深色，
        // 压在纯黑导航栏上等于看不见），还顺手打开了 HIDE_NAVIGATION / IMMERSIVE /
        // IMMERSIVE_STICKY 等沉浸式标志。这里改用官方 API 显式设置。
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLightColor(STATUS_BAR_COLOR)
        controller.isAppearanceLightNavigationBars = isLightColor(NAV_BAR_COLOR)

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        val rootView = activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
        // 蒙版方向按「这个 Activity 实际的夜间位」取，而不是读偏好：闪屏 / 播放器强制暗色
        // （ColorMode.nightBits(forceDark = true)），按偏好取会让强制暗色的页面压一层亮色蒙版。
        val nightMode = activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isLightNight = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_NO
        val wallpaper = BackgroundImage.drawable(isLightNight, BACKGROUND)
        if (wallpaper != null) {
            // 自定义背景图与弥散光晕可以叠加：图铺底，光晕作半透明叠加层（AmbientGlow 的
            // 正常 drawable 是整屏不透明底色，直接叠会把图盖住，所以这里用只画光晕圆的那份）。
            // 两层都画在窗口背景（DecorView 整屏），content 置透明。
            // 解码结果有内存缓存 + 降采样，重复进页面不会重复解原图。
            // 亮暗来源与 wallpaper 一致（Activity 的实际夜间位）——以前这里读 ColorMode.isLight()，
            // 强制暗色的页面会出现「背景图按暗色解、光晕按亮色画」的一深一浅。
            val glowOverlay = if (AmbientGlow.isEnabled()) {
                AmbientGlow.createOverlayDrawable(
                    getCurrentThemeName(), isLightNight, AmbientGlow.currentSource()
                )
            } else null
            window.setBackgroundDrawable(
                if (glowOverlay == null) wallpaper
                else LayerDrawable(arrayOf(wallpaper, glowOverlay))
            )
            rootView?.background = null
        } else if (AmbientGlow.isEnabled()) {
            // 弥散光晕画在窗口背景上（DecorView 整屏，含「界面边距」留出的外圈，不会在边距处断层），
            // content 自己不再铺纯色，否则会把光晕整块盖住。关闭时走下面的原路径，零额外开销。
            window.setBackgroundDrawable(
                AmbientGlow.createDrawable(getCurrentThemeName(), BACKGROUND, isLightNight, AmbientGlow.currentSource())
            )
            rootView?.background = null
        } else {
            // 窗口底色也换成主题背景：*.NoSwipe.AppCompat 主题的 windowBackground 是纯黑，
            // 开了「界面边距」时外圈露出的就是它——亮色模式下会是一圈黑框。
            // 窗口背景本来就要画，这里只是换个颜色，不增加绘制。
            window.setBackgroundDrawable(ColorDrawable(BACKGROUND))
            rootView?.setBackgroundColor(BACKGROUND)
        }
    }

    fun getPrimary(context: Context): Int = PRIMARY
    fun getPrimaryDark(context: Context): Int = PRIMARY_DARK
    fun getPrimaryLight(context: Context): Int = PRIMARY_LIGHT
    fun getSecondary(context: Context): Int = SECONDARY

    fun getSurface(context: Context): Int = SURFACE
    fun getCard(context: Context): Int = CARD
    fun getCardWithAlpha(context: Context): Int = CARD_WITH_ALPHA
    fun getBackground(context: Context): Int = BACKGROUND

    fun getTextPrimary(context: Context): Int = TEXT_PRIMARY
    fun getTextSecondary(context: Context): Int = TEXT_SECONDARY
    fun getTextTertiary(context: Context): Int = TEXT_TERTIARY

    fun getOnPrimary(context: Context): Int = ON_PRIMARY
    fun getOnSurface(context: Context): Int = ON_SURFACE
    fun getOnCard(context: Context): Int = ON_CARD
    fun getOnBackground(context: Context): Int = ON_BACKGROUND
    fun getOnButton(context: Context): Int = ON_BUTTON

    fun getLikeColor(context: Context): Int = LIKE_COLOR
    fun getCoinColor(context: Context): Int = COIN_COLOR
    fun getFavColor(context: Context): Int = FAV_COLOR
    fun getShareColor(context: Context): Int = SHARE_COLOR

    fun getSuccessColor(context: Context): Int = SUCCESS
    fun getWarningColor(context: Context): Int = WARNING
    fun getErrorColor(context: Context): Int = ERROR
    fun getInfoColor(context: Context): Int = INFO

    fun getPlayerBg(context: Context): Int = PLAYER_BG
    fun getPlayerControlBg(context: Context): Int = PLAYER_CONTROL_BG
    fun getPlayerProgressBg(context: Context): Int = PLAYER_PROGRESS_BG
    fun getPlayerProgressFill(context: Context): Int = PLAYER_PROGRESS_FILL

    fun getBorder(context: Context): Int = BORDER
    fun getDivider(context: Context): Int = DIVIDER
    fun getRipple(context: Context): Int = RIPPLE
    fun getGold(context: Context): Int = GOLD
    fun getVipPink(context: Context): Int = VIP_COLOR

    fun getStatusBarColor(context: Context): Int = STATUS_BAR_COLOR
    fun getAccentColor(context: Context): Int = PRIMARY

    /** 判断颜色是否为「亮色」，用于决定系统栏图标该用深色还是浅色。 */
    private fun isLightColor(color: Int): Boolean = ColorUtils.calculateLuminance(color) > 0.5

    /**
     * 主题 key → 主题资源 id。
     *
     * `BaseActivity` / `PlayerActivity` / `BiliTerminalApp` 此前各自复制了一份相同的 `when`，
     * 改主题时容易漏改；这里收敛成唯一映射。
     *
     * [noSwipe] = true 时返回同套主题的 `*.NoSwipe.AppCompat` 变体，它只比主主题多一项
     * `android:windowSwipeToDismiss=false`，用于关掉手表系统自带的「右滑返回」。
     *
     * **为什么必须在运行时再指定一次**：清单上的 `Theme.NoSwipe*` 会被 `onCreate` 里紧随其后的
     * `setTheme(...)` 覆盖回带右滑返回的主主题，于是「禁用返回键」开关对手表右滑返回失效，
     * 视频播放页的滑动调进度也会被系统的右滑返回抢走（issue #1）。
     */
    fun themeResId(theme: String = getCurrentThemeName(), noSwipe: Boolean = false): Int =
        if (noSwipe) noSwipeThemeResId(theme) else when (theme) {
            THEME_ZHIHU_BLUE -> R.style.Theme_ZhihuBlue
            THEME_IQIYI_GREEN -> R.style.Theme_IQIYIGreen
            THEME_PURPLE_FANTASY -> R.style.Theme_PurpleFantasy
            THEME_RAINBOW_FANTASY -> R.style.Theme_RainbowFantasy
            THEME_CLASSIC_GRAY -> R.style.Theme_ClassicGray
            THEME_CLASSIC_TERMINAL -> R.style.Theme_ClassicTerminal
            else -> R.style.Theme_BiliClient
        }

    /**
     * [themeResId] 的「关闭系统右滑返回」变体。
     *
     * 7 套主题各有一份 `*.NoSwipe.AppCompat`（父主题就是对应的主主题，只多关一项
     * `windowSwipeToDismiss`），因此配色/圆角/字体完全一致，只是不接受系统右滑返回。
     */
    private fun noSwipeThemeResId(theme: String): Int = when (theme) {
        THEME_ZHIHU_BLUE -> R.style.Theme_ZhihuBlue_NoSwipe_AppCompat
        THEME_IQIYI_GREEN -> R.style.Theme_IQIYIGreen_NoSwipe_AppCompat
        THEME_PURPLE_FANTASY -> R.style.Theme_PurpleFantasy_NoSwipe_AppCompat
        THEME_RAINBOW_FANTASY -> R.style.Theme_RainbowFantasy_NoSwipe_AppCompat
        THEME_CLASSIC_GRAY -> R.style.Theme_ClassicGray_NoSwipe_AppCompat
        THEME_CLASSIC_TERMINAL -> R.style.Theme_ClassicTerminal_NoSwipe_AppCompat
        else -> R.style.Theme_NoSwipe_AppCompat
    }

    /** 当前主题主色 + 指定不透明度（0～255），用于"进行中/待定"这类淡色描边。 */
    fun withPrimaryAlpha(alpha: Int): Int = (PRIMARY and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    /**
     * 清空色表缓存。
     *
     * **唯一调用者是 [AppearanceManager.setTheme]**。任何绕过它直接写 `theme_selector` 的代码
     * 都必须自己调用本方法，否则会出现「改了主题但色表还是旧的」——且在 `onResume` 重建后依然错。
     */
    fun invalidateCache() {
        cachedColors = null
    }

    fun getCurrentThemeName(): String {
        return SharedPreferencesUtil.getString(PREF_KEY_THEME, THEME_DEFAULT)
    }

    fun getThemeDisplayName(): String {
        return when (getCurrentThemeName()) {
            THEME_ZHIHU_BLUE -> UiText.of("知乎蓝")
            THEME_IQIYI_GREEN -> UiText.of("爱奇艺绿")
            THEME_PURPLE_FANTASY -> UiText.of("紫色空灵")
            THEME_RAINBOW_FANTASY -> UiText.of("五彩斑斓")
            THEME_CLASSIC_GRAY -> UiText.of("经典灰")
            THEME_CLASSIC_TERMINAL -> UiText.of("经典终端")
            else -> UiText.of("B站粉")
        }
    }
}