package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ApkDownloadStore.fileName]（更新包存到 Download 时的文件名）的纯 JVM 单测。
 *
 * 钉住的是几件「错了也不会崩、但用户会看见」的事：
 * 1. 版本号必须落在文件名里 —— 用户要的就是「一眼看出这是哪个版本」；
 * 2. 共存版必须能区分 —— 否则同版本的两个包会被系统改名成 (1)；
 * 3. 版本号可能是**分享文件名**（内测通道），带 .apk、带路径分隔符、本来就以应用名开头，都得洗干净。
 */
class ApkDownloadStoreTest {

    // ==================== 正常版本号 ====================

    @Test
    fun fileName_normalVersion() {
        assertEquals(
            "RE哔哩终端-26.11.17.apk",
            ApkDownloadStore.fileName("26.11.17", false)
        )
    }

    /** 测试通道的 versionName 带 -beta.N，原样保留（正好能区分通道）。 */
    @Test
    fun fileName_keepsChannelSuffix() {
        assertEquals(
            "RE哔哩终端-26.11.17-beta.1.apk",
            ApkDownloadStore.fileName("26.11.17-beta.1", false)
        )
    }

    @Test
    fun fileName_coexistSuffix() {
        assertEquals(
            "RE哔哩终端-26.11.17-共存.apk",
            ApkDownloadStore.fileName("26.11.17", true)
        )
    }

    // ==================== 版本号脏数据 ====================

    /** 内测通道把分享文件名当版本号传进来，可能是 app.apk 这种。 */
    @Test
    fun fileName_stripsApkExtension() {
        assertEquals(
            "RE哔哩终端-26.11.17.apk",
            ApkDownloadStore.fileName("26.11.17.apk", false)
        )
        // 大小写都要认
        assertEquals(
            "RE哔哩终端-26.11.17.apk",
            ApkDownloadStore.fileName("26.11.17.APK", false)
        )
    }

    /** 分享文件名本来就叫 RE哔哩终端-26.11.17.apk 时，不能拼出两个应用名。 */
    @Test
    fun fileName_doesNotDoublePrefix() {
        assertEquals(
            "RE哔哩终端-26.11.17.apk",
            ApkDownloadStore.fileName("RE哔哩终端-26.11.17.apk", false)
        )
        assertEquals(
            "RE哔哩终端-26.11.17-共存.apk",
            ApkDownloadStore.fileName("RE：哔哩终端-26.11.17.apk", true)
        )
    }

    /** 路径分隔符、冒号这些字符在文件系统里非法，必须换成下划线。 */
    @Test
    fun fileName_replacesIllegalChars() {
        assertEquals(
            "RE哔哩终端-26.11.17_1.apk",
            ApkDownloadStore.fileName("26.11.17/1", false)
        )
        assertEquals(
            "RE哔哩终端-26.11.17_2.apk",
            ApkDownloadStore.fileName("26.11.17:2", false)
        )
    }

    /** 结尾的点与连字符在 Windows 上会被悄悄吃掉，先自己修掉。 */
    @Test
    fun fileName_trimsDotsAndDashes() {
        assertEquals(
            "RE哔哩终端-26.11.17.apk",
            ApkDownloadStore.fileName("26.11.17.", false)
        )
        assertEquals(
            "RE哔哩终端-26.11.17.apk",
            ApkDownloadStore.fileName("-26.11.17-", false)
        )
    }

    @Test
    fun fileName_blankFallsBack() {
        assertEquals("RE哔哩终端-未知版本.apk", ApkDownloadStore.fileName(null, false))
        assertEquals("RE哔哩终端-未知版本.apk", ApkDownloadStore.fileName("", false))
        assertEquals("RE哔哩终端-未知版本.apk", ApkDownloadStore.fileName("   ", false))
        // 只有 .apk 也算没有版本号
        assertEquals("RE哔哩终端-未知版本.apk", ApkDownloadStore.fileName(".apk", false))
    }

    /** 超长版本号要截断，但不能把主干挤没了。 */
    @Test
    fun fileName_capsLength() {
        val long = "2".repeat(300)
        val name = ApkDownloadStore.fileName(long, false)
        assertTrue(name, name.endsWith(".apk"))
        assertTrue(name, name.length <= 80 + "RE哔哩终端-".length + ".apk".length)
        assertFalse(name, name.contains("\n"))
    }

    /** 生成的名字必须真的落在 Download 目录下（不再含路径分隔符）。 */
    @Test
    fun fileName_hasNoPathSeparator() {
        val name = ApkDownloadStore.fileName("26.11.17/../../evil", false)
        assertFalse(name, name.contains("/"))
        assertFalse(name, name.contains("\\"))
    }
}
