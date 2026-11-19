package com.RobinNotBad.BiliClient.ui.shortvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortVideoThemeTest {
    @Test
    fun whiteAccentUsesMoreVisibleTrack() {
        assertEquals(0x66FFFFFF, ShortVideoTheme.trackColor(ShortVideoTheme.WHITE))
        assertEquals(0x33FFFFFF, ShortVideoTheme.trackColor(0xFFFB7299.toInt()))
    }

    @Test
    fun lightAccentUsesDarkIcons() {
        assertTrue(ShortVideoTheme.prefersDarkIcons(ShortVideoTheme.WHITE))
        assertFalse(ShortVideoTheme.prefersDarkIcons(0xFF17181C.toInt()))
    }

    @Test
    fun favouriteOnlyUsesAccentWhenSelected() {
        val pink = 0xFFFB7299.toInt()
        assertEquals(pink, ShortVideoTheme.favouriteTint(pink, true))
        assertEquals(ShortVideoTheme.WHITE, ShortVideoTheme.favouriteTint(pink, false))
    }
}
