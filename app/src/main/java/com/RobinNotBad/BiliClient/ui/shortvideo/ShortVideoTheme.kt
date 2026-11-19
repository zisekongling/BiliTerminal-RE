package com.RobinNotBad.BiliClient.ui.shortvideo

/** 进度条取色规则：避免进度轨迹与强调色在暗背景上糊成一条。 */
object ShortVideoTheme {
    const val WHITE = 0xFFFFFFFF.toInt()
    fun trackColor(accent: Int): Int = if (accent == WHITE) 0x66FFFFFF else 0x33FFFFFF
    fun favouriteTint(accent: Int, selected: Boolean): Int = if (selected) accent else WHITE

    /** 浅色强调按钮上的图标用深色，其余保持白色。 */
    fun prefersDarkIcons(accent: Int): Boolean {
        val r = (accent shr 16) and 0xFF
        val g = (accent shr 8) and 0xFF
        val b = accent and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000 > 160
    }
}