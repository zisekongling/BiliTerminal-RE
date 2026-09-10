package com.RobinNotBad.BiliClient.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetWorkUtilTest {

    private val fakePrefs = FakeSharedPreferences()

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    @Test
    fun buildGuestCookieString_removesLoginCookies_keepsGuestCookies() {
        val full = "SESSDATA=abc; bili_jct=def; DedeUserID=123; DedeUserID__ckMd5=xyz; " +
                "sid=9; buvid3=aaa; buvid4=bbb; bili_ticket=ccc; _uuid=ddd; CURRENT_FNVAL=4048"
        val guest = NetWorkUtil.buildGuestCookieString(full)

        assertFalse("应剔除SESSDATA", guest.contains("SESSDATA"))
        assertFalse("应剔除bili_jct", guest.contains("bili_jct"))
        assertFalse("应剔除DedeUserID", guest.contains("DedeUserID"))
        assertFalse("应剔除DedeUserID__ckMd5", guest.contains("DedeUserID__ckMd5"))
        assertFalse("应剔除sid", guest.contains("sid"))
        assertTrue("应保留buvid3", guest.contains("buvid3=aaa"))
        assertTrue("应保留buvid4", guest.contains("buvid4=bbb"))
        assertTrue("应保留bili_ticket", guest.contains("bili_ticket=ccc"))
        assertTrue("应保留_uuid", guest.contains("_uuid=ddd"))
    }

    @Test
    fun buildGuestCookieString_nullOrEmpty_returnsEmpty() {
        assertEquals("", NetWorkUtil.buildGuestCookieString(null))
        assertEquals("", NetWorkUtil.buildGuestCookieString(""))
    }
}
