package com.RobinNotBad.BiliClient.ui.shortvideo

/**
 * 短视频界面的取色规则（纯函数，可单测）。
 *
 * 设计稿「主题」那一节的硬要求：
 * 1. **强调色只表示三件事**——已播进度、已开启/已收藏、当前 Tab；其余一律白色。
 * 2. **不用实底强调色按钮**：白字压在 B 站粉这类实底上对比度不足 3:1，
 *    所以主要按钮一律「20% 强调色底 + 强调色字」。
 * 3. **细线自动退到浅一档**：2dp 进度线与 9sp 的「已开启」字对黑底要 ≥ 3:1，
 *    主题的 PRIMARY 不够时就换 PRIMARY_LIGHT。
 * 4. 面板始终深色，不随主题变；主题只改强调色。
 */
object ShortVideoTheme {
    const val WHITE = 0xFFFFFFFF.toInt()

    /** 面板底色 #161319，97% 不透明（与播放器 v2 稿的清晰度弹层同一个底色）。 */
    const val PANEL_BG = 0xF7161319.toInt()

    /** 次级文字（白 60%）。 */
    const val TEXT_SECONDARY = 0x99FFFFFF.toInt()

    /** 细线 / 小字对黑底的最低对比度要求。 */
    const val MIN_LINE_CONTRAST = 3.0

    /** 进度轨道。 */
    fun trackColor(accent: Int): Int = if (accent == WHITE) 0x66FFFFFF else 0x33FFFFFF

    /** 收藏图标：已收藏用强调色，未收藏用白色。 */
    fun favouriteTint(accent: Int, selected: Boolean): Int = if (selected) accent else WHITE

    /**
     * 这条强调色对黑底够不够用（用于 2dp 进度线与 9sp 小字）。
     * 不够时调用方应改用 [lineColor] 拿到的浅一档。
     */
    fun meetsLineContrast(accent: Int): Boolean =
        contrastRatio(accent, 0xFF000000.toInt()) >= MIN_LINE_CONTRAST

    /**
     * 细线 / 小字该用哪一支强调色：够用就用 [primary]，否则退到 [primaryLight]；
     * 两者都不够才退回白色——白色对黑底对比度 21，一定够。
     */
    fun lineColor(primary: Int, primaryLight: Int): Int = when {
        meetsLineContrast(primary) -> primary
        meetsLineContrast(primaryLight) -> primaryLight
        else -> WHITE
    }

    /**
     * 「浅底 + 色字」按钮的底色：20% 不透明的强调色。
     *
     * 设计稿选这条是因为白字压实底在 B 站粉/爱奇艺绿上都不到 3:1；
     * 反过来做成浅底后，字用强调色本身对深色面板仍然清晰，且不用逐主题挑字色。
     */
    fun softAccentBackground(accent: Int): Int = (accent and 0x00FFFFFF) or (0x33 shl 24)

    /** 浅色强调按钮上的图标用深色，其余保持白色。 */
    fun prefersDarkIcons(accent: Int): Boolean = luminance(accent) > 0.6

    /**
     * 把强调色调浅一档（用于对比度不够时的细线/小字）。
     *
     * 没有主题的 PRIMARY_LIGHT 可读时用它兜底：往白色方向插值 35%，
     * 在深色底上一定比原色更亮，且不会改变色相。
     */
    fun lighten(color: Int, amount: Float = 0.35f): Int {
        val a = amount.coerceIn(0f, 1f)
        fun mix(channel: Int) = (channel + (255 - channel) * a).toInt().coerceIn(0, 255)
        val r = mix((color shr 16) and 0xFF)
        val g = mix((color shr 8) and 0xFF)
        val b = mix(color and 0xFF)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ==================== WCAG 相对亮度 ====================

    /** WCAG 相对亮度（0~1）。 */
    fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val s = value / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val r = channel((color shr 16) and 0xFF)
        val g = channel((color shr 8) and 0xFF)
        val b = channel(color and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** 两色之间的 WCAG 对比度（1~21）。 */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }
}
