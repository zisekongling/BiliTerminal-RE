package com.RobinNotBad.BiliClient.ui.theme

import android.graphics.Color
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

object BiliColors {
    private const val THEME_BILIBILI_PINK = "theme_bilibili_pink"
    private const val THEME_ZHIHU_BLUE = "theme_zhihu_blue"
    private const val THEME_IQIYI_GREEN = "theme_iqiyi_green"
    private const val THEME_PURPLE_FANTASY = "theme_purple_fantasy"
    private const val THEME_RAINBOW_FANTASY = "theme_rainbow_fantasy"
    private const val THEME_CLASSIC_GRAY = "theme_classic_gray"
    private const val PREF_KEY_THEME = "theme_selector"

    private fun getCurrentTheme(): String {
        return SharedPreferencesUtil.getString(PREF_KEY_THEME, THEME_BILIBILI_PINK)
    }

    val Primary get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#056DE8")
        THEME_IQIYI_GREEN -> Color.parseColor("#00DC5A")
        THEME_PURPLE_FANTASY -> Color.parseColor("#7B2CBF")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FF6B6B")
        THEME_CLASSIC_GRAY -> Color.parseColor("#8787FB")
        else -> Color.parseColor("#FF6699")
    }
    val PrimaryDark get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#0354B5")
        THEME_IQIYI_GREEN -> Color.parseColor("#00B347")
        THEME_PURPLE_FANTASY -> Color.parseColor("#5A1F8F")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#CC5555")
        THEME_CLASSIC_GRAY -> Color.parseColor("#6B6BDC")
        else -> Color.parseColor("#E84B85")
    }
    val PrimaryLight get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#3B8AF2")
        THEME_IQIYI_GREEN -> Color.parseColor("#33E67A")
        THEME_PURPLE_FANTASY -> Color.parseColor("#9B59D0")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FF8E8E")
        THEME_CLASSIC_GRAY -> Color.parseColor("#A3A3FD")
        else -> Color.parseColor("#FF8CB0")
    }
    val Secondary get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#6DB0FF")
        THEME_IQIYI_GREEN -> Color.parseColor("#66F0A3")
        THEME_PURPLE_FANTASY -> Color.parseColor("#BA7FE8")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FFE66D")
        THEME_CLASSIC_GRAY -> Color.parseColor("#C8C8FF")
        else -> Color.parseColor("#FFB3CA")
    }
    val SecondaryDark get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#3B8AF2")
        THEME_IQIYI_GREEN -> Color.parseColor("#33E67A")
        THEME_PURPLE_FANTASY -> Color.parseColor("#9B59D0")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FF9F43")
        THEME_CLASSIC_GRAY -> Color.parseColor("#A3A3FD")
        else -> Color.parseColor("#FF8CB0")
    }

    val SurfaceDark = Color.parseColor("#24242E")
    val CardDark = Color.parseColor("#2A2A35")
    val CardLight get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#E8F1FF")
        THEME_IQIYI_GREEN -> Color.parseColor("#CCF9DE")
        THEME_PURPLE_FANTASY -> Color.parseColor("#E8D5F5")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FFE8E8")
        THEME_CLASSIC_GRAY -> Color.parseColor("#E8E8FF")
        else -> Color.parseColor("#FFECF1")
    }

    val BackgroundDark = Color.parseColor("#1B1B24")
    val BackgroundLight = Color.parseColor("#F8F9FC")

    val TextPrimaryDark get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#F0F7FF")
        THEME_IQIYI_GREEN -> Color.parseColor("#E6FDF0")
        THEME_PURPLE_FANTASY -> Color.parseColor("#F5EBFC")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FFF5F5")
        THEME_CLASSIC_GRAY -> Color.parseColor("#EBE0E2")
        else -> Color.parseColor("#F0E8EC")
    }
    val TextSecondaryDark get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#8BB8E8")
        THEME_IQIYI_GREEN -> Color.parseColor("#80E6AB")
        THEME_PURPLE_FANTASY -> Color.parseColor("#C4A8E0")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FFAFAF")
        THEME_CLASSIC_GRAY -> Color.parseColor("#A09098")
        else -> Color.parseColor("#B0A0AA")
    }
    val TextTertiaryDark get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#5A7FA5")
        THEME_IQIYI_GREEN -> Color.parseColor("#55B37A")
        THEME_PURPLE_FANTASY -> Color.parseColor("#8B6BA8")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#CC8888")
        THEME_CLASSIC_GRAY -> Color.parseColor("#706870")
        else -> Color.parseColor("#7A6A74")
    }

    val TextPrimaryLight = Color.parseColor("#1E1E2A")
    val TextSecondaryLight = Color.parseColor("#5A5A6E")
    val TextTertiaryLight = Color.parseColor("#C0C0D0")

    val Success = Color.parseColor("#52C41A")
    val Warning = Color.parseColor("#FAAD14")
    val Error = Color.parseColor("#E84B85")
    val Info get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#056DE8")
        THEME_IQIYI_GREEN -> Color.parseColor("#00DC5A")
        THEME_PURPLE_FANTASY -> Color.parseColor("#7B2CBF")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FF6B6B")
        THEME_CLASSIC_GRAY -> Color.parseColor("#66CCFF")
        else -> Color.parseColor("#1890FF")
    }

    val PlayerBackground = Color.parseColor("#000000")
    val PlayerControlBg = Color.parseColor("#33000000")
    val PlayerProgressBg = Color.parseColor("#55FFFFFF")
    val PlayerProgressFill get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#056DE8")
        THEME_IQIYI_GREEN -> Color.parseColor("#00DC5A")
        THEME_PURPLE_FANTASY -> Color.parseColor("#7B2CBF")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FF6B6B")
        THEME_CLASSIC_GRAY -> Color.parseColor("#8787FB")
        else -> Color.parseColor("#FF6699")
    }

    val Gold = Color.parseColor("#FFB800")
    val VIPPink get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#056DE8")
        THEME_IQIYI_GREEN -> Color.parseColor("#00DC5A")
        THEME_PURPLE_FANTASY -> Color.parseColor("#7B2CBF")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FF6B6B")
        THEME_CLASSIC_GRAY -> Color.parseColor("#8787FB")
        else -> Color.parseColor("#FF6699")
    }

    val LikeColor = Color.parseColor("#FF6A6A")
    val CoinColor = Color.parseColor("#FFE66D")
    val FavColor get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#056DE8")
        THEME_IQIYI_GREEN -> Color.parseColor("#00DC5A")
        THEME_PURPLE_FANTASY -> Color.parseColor("#7B2CBF")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FF6B6B")
        THEME_CLASSIC_GRAY -> Color.parseColor("#8787FB")
        else -> Color.parseColor("#FF6699")
    }
    val ShareColor get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#3B8AF2")
        THEME_IQIYI_GREEN -> Color.parseColor("#33E67A")
        THEME_PURPLE_FANTASY -> Color.parseColor("#9B59D0")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FF9F43")
        THEME_CLASSIC_GRAY -> Color.parseColor("#66CCFF")
        else -> Color.parseColor("#FF8CB0")
    }

    val DividerColor get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#204A87")
        THEME_IQIYI_GREEN -> Color.parseColor("#006633")
        THEME_PURPLE_FANTASY -> Color.parseColor("#3D1A66")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#663333")
        THEME_CLASSIC_GRAY -> Color.parseColor("#454555")
        else -> Color.parseColor("#FFD9E4")
    }
    val BtnHover get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#6DB0FF")
        THEME_IQIYI_GREEN -> Color.parseColor("#66F0A3")
        THEME_PURPLE_FANTASY -> Color.parseColor("#BA7FE8")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FFE66D")
        THEME_CLASSIC_GRAY -> Color.parseColor("#A3A3FD")
        else -> Color.parseColor("#FFB3CA")
    }
    val BtnPressed get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#0354B5")
        THEME_IQIYI_GREEN -> Color.parseColor("#00B347")
        THEME_PURPLE_FANTASY -> Color.parseColor("#5A1F8F")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#CC5555")
        THEME_CLASSIC_GRAY -> Color.parseColor("#6B6BDC")
        else -> Color.parseColor("#E84B85")
    }
    val BtnDisabledBg get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#E8F1FF")
        THEME_IQIYI_GREEN -> Color.parseColor("#CCF9DE")
        THEME_PURPLE_FANTASY -> Color.parseColor("#E8D5F5")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#FFE8E8")
        THEME_CLASSIC_GRAY -> Color.parseColor("#E8E8FF")
        else -> Color.parseColor("#FFECF1")
    }

    val PrimaryTransparent get() = when (getCurrentTheme()) {
        THEME_ZHIHU_BLUE -> Color.parseColor("#99056DE8")
        THEME_IQIYI_GREEN -> Color.parseColor("#9900DC5A")
        THEME_PURPLE_FANTASY -> Color.parseColor("#997B2CBF")
        THEME_RAINBOW_FANTASY -> Color.parseColor("#99FF6B6B")
        THEME_CLASSIC_GRAY -> Color.parseColor("#998787FB")
        else -> Color.parseColor("#99FF6699")
    }
    val Gray = Color.parseColor("#70707070")
    val ButtonBgSelected = Color.parseColor("#FFE8E8E8")
    val ButtonBgNormal = Color.parseColor("#30FFFFFF")
}

object BiliDimens {
    const val SPACING_XS = 4f
    const val SPACING_SM = 8f
    const val SPACING_MD = 12f
    const val SPACING_LG = 16f
    const val SPACING_XL = 24f
    const val SPACING_XXL = 32f

    const val CARD_CORNER = 12f
    const val BUTTON_CORNER = 8f
    const val CHIP_CORNER = 20f

    const val ICON_SM = 16f
    const val ICON_MD = 20f
    const val ICON_LG = 24f
    const val ICON_XL = 32f

    const val WATCH_LIST_ITEM_HEIGHT = 72f
    const val WATCH_COVER_SIZE = 56f
    const val WATCH_COVER_CORNER = 8f

    const val TITLE_LARGE = 20f
    const val TITLE_MEDIUM = 16f
    const val TITLE_SMALL = 14f
    const val BODY_LARGE = 14f
    const val BODY_MEDIUM = 13f
    const val BODY_SMALL = 12f
    const val CAPTION = 11f
    const val STAT_NUMBER = 13f

    const val ELEVATION_NONE = 0f
    const val ELEVATION_CARD = 2f
    const val ELEVATION_FAB = 6f
    const val ELEVATION_DIALOG = 8f
}