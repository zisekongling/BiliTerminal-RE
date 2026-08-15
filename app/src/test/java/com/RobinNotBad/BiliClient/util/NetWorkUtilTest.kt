package com.RobinNotBad.BiliClient.util

import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.HashMap
import java.util.HashSet

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

    private class FakeSharedPreferences : SharedPreferences {
        private val map = HashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)

        override fun getString(key: String, defValue: String?): String? =
            map[key] as? String ?: defValue

        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (map[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String, defValue: Int): Int =
            (map[key] as? Int) ?: defValue

        override fun getLong(key: String, defValue: Long): Long =
            (map[key] as? Long) ?: defValue

        override fun getFloat(key: String, defValue: Float): Float =
            (map[key] as? Float) ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            (map[key] as? Boolean) ?: defValue

        override fun contains(key: String): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

        private inner class FakeEditor : SharedPreferences.Editor {
            private val changes = HashMap<String, Any?>()
            private var clearAll = false
            private val removed = HashSet<String>()

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                changes[key] = value
                return this
            }

            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                changes[key] = values
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                changes[key] = value
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                changes[key] = value
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                changes[key] = value
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                changes[key] = value
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                removed.add(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) map.clear()
                removed.forEach { map.remove(it) }
                changes.forEach { (k, v) -> map[k] = v }
            }
        }
    }
}
