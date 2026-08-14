package com.RobinNotBad.BiliClient.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

object ThemeManager {

    const val THEME_BILIBILI_PINK = "theme_bilibili_pink"
    const val THEME_ZHIHU_BLUE = "theme_zhihu_blue"
    const val THEME_IQIYI_GREEN = "theme_iqiyi_green"
    const val THEME_PURPLE_FANTASY = "theme_purple_fantasy"
    const val THEME_RAINBOW_FANTASY = "theme_rainbow_fantasy"
    const val THEME_CLASSIC_GRAY = "theme_classic_gray"
    const val PREF_KEY_THEME = "theme_selector"

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
        val NAV_BAR_COLOR: Int
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
        NAV_BAR_COLOR = 0xFF1B1B24.toInt()
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
        NAV_BAR_COLOR = 0xFF1B1B24.toInt()
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
        NAV_BAR_COLOR = 0xFF1B1B24.toInt()
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
        NAV_BAR_COLOR = 0xFF1B1B24.toInt()
    )

    object RainbowFantasy : ThemeColors(
        PRIMARY = 0xFFFF6B6B.toInt(),
        PRIMARY_DARK = 0xFFCC5555.toInt(),
        PRIMARY_LIGHT = 0xFFFF8E8E.toInt(),
        SECONDARY = 0xFFFFE66D.toInt(),
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
        NAV_BAR_COLOR = 0xFF1B1B24.toInt()
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
        NAV_BAR_COLOR = 0xFF1B1B24.toInt()
    )

    private fun getCurrentTheme(): ThemeColors {
        val theme = SharedPreferencesUtil.getString(PREF_KEY_THEME, THEME_BILIBILI_PINK)
        return when (theme) {
            THEME_ZHIHU_BLUE -> ZhihuBlue
            THEME_IQIYI_GREEN -> IQIYIGreen
            THEME_PURPLE_FANTASY -> PurpleFantasy
            THEME_RAINBOW_FANTASY -> RainbowFantasy
            THEME_CLASSIC_GRAY -> ClassicGray
            else -> BilibiliPink
        }
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

    fun applyWindowTheme(activity: Activity) {
        val window: Window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = STATUS_BAR_COLOR
        window.navigationBarColor = NAV_BAR_COLOR

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = SURFACE
        }

        val flags = window.decorView.systemUiVisibility
        window.decorView.systemUiVisibility = flags or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        val rootView = activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
        rootView?.setBackgroundColor(BACKGROUND)
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

    fun getCardBackgroundColor(context: Context): Int = CARD_WITH_ALPHA
    fun getButtonBackgroundColor(context: Context): Int = CARD_WITH_ALPHA
    fun getStatusBarColor(context: Context): Int = STATUS_BAR_COLOR
    fun getAccentColor(context: Context): Int = PRIMARY

    fun getColorScheme(context: Context): BiliColorScheme {
        val theme = getCurrentTheme()
        return BiliColorScheme(
            theme.PRIMARY, theme.PRIMARY_DARK, theme.PRIMARY_LIGHT, theme.SECONDARY,
            theme.SURFACE, theme.CARD, theme.BACKGROUND,
            theme.TEXT_PRIMARY, theme.TEXT_SECONDARY, theme.TEXT_TERTIARY
        )
    }

    fun setTheme(theme: String) {
        SharedPreferencesUtil.putString(PREF_KEY_THEME, theme)
    }

    fun getCurrentThemeName(): String {
        return SharedPreferencesUtil.getString(PREF_KEY_THEME, THEME_BILIBILI_PINK)
    }

    fun getThemeDisplayName(): String {
        return when (getCurrentThemeName()) {
            THEME_ZHIHU_BLUE -> "知乎蓝"
            THEME_IQIYI_GREEN -> "爱奇艺绿"
            THEME_PURPLE_FANTASY -> "紫色空灵"
            THEME_RAINBOW_FANTASY -> "五彩斑斓"
            THEME_CLASSIC_GRAY -> "经典灰"
            else -> "B站粉"
        }
    }
}

data class BiliColorScheme(
    val primary: Int,
    val primaryDark: Int,
    val primaryLight: Int,
    val secondary: Int,
    val surface: Int,
    val card: Int,
    val background: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textTertiary: Int
)