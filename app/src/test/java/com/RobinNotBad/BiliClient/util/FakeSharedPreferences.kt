package com.RobinNotBad.BiliClient.util

import android.content.SharedPreferences
import java.util.HashMap
import java.util.HashSet

/**
 * 纯 JVM 假 SharedPreferences，供单测注入 [SharedPreferencesUtil.sharedPreferences] 使用。
 *
 * 原本是 [NetWorkUtilTest] 里的私有内部类，主题单测也要用，故提取为共享助手——
 * 避免每个测试各抄一份（本仓库已有「同一段逻辑复制 19 处」的前科）。
 *
 * 用法：
 * ```
 * @Before fun setUp() { SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences() }
 * @After  fun tearDown() { SharedPreferencesUtil.sharedPreferences = null }
 * ```
 *
 * 注意：`commit()` 直接转调 `apply()`，两态等价——写盘在这里没有意义。
 */
class FakeSharedPreferences : SharedPreferences {
    private val map = HashMap<String, Any?>()

    /**
     * [getString] 被调用的次数。
     *
     * 供「缓存类改动是否真的减少了 SharedPreferences 读取」这类性能回归测试断言使用
     * （见 ColorSchemeTest 的 colorGetters_doNotTouchSharedPreferencesAfterFirstRead）。
     */
    var stringReadCount: Int = 0
        private set

    override fun getAll(): MutableMap<String, *> = HashMap(map)

    override fun getString(key: String, defValue: String?): String? {
        stringReadCount++
        return map[key] as? String ?: defValue
    }

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
