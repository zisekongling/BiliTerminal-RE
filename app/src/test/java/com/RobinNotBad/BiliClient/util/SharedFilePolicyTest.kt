package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住 [SharedFilePolicy] 的路径归属判断与卷根推导，全部能在纯 JVM 里跑
 * （该对象刻意不引用 Android API）。
 *
 * 背景：外置卷（SD 卡）上的缓存视频交给凉腕播放器时，`FileProvider.getUriForFile` 抛
 * `IllegalArgumentException: Failed to find configured root` —— FileProvider 的根只覆盖主卷。
 * 这个崩溃的修法靠「自己算允许分享的卷根」，算错一边就是「该播的播不了」或「不该分享的能分享」，
 * 所以这里的边界用例要锁死：
 * - 前缀相似但不同目录（`/a/bc` 不能在 `/a/b` 之下）必须判 false；
 * - 卷根反推只能从 `/Android/` 往前截，不能把整条系统路径当根；
 * - 多条根命中时取最长（最具体）的那条。
 */
class SharedFilePolicyTest {

    // ==================== isUnder ====================

    @Test
    fun `同一路径与带尾斜杠的写法都算在根内`() {
        assertTrue(SharedFilePolicy.isUnder("/storage/emulated/0", "/storage/emulated/0"))
        assertTrue(SharedFilePolicy.isUnder("/storage/emulated/0/", "/storage/emulated/0"))
        assertTrue(SharedFilePolicy.isUnder("/storage/emulated/0", "/storage/emulated/0/"))
    }

    @Test
    fun `子目录算在根内`() {
        assertTrue(
            SharedFilePolicy.isUnder(
                "/storage/emulated/0",
                "/storage/emulated/0/Android/media/com.RobinNotBad.BiliClient/video.mp4"
            )
        )
    }

    @Test
    fun `前缀相同但多出字符的兄弟目录不算在根内`() {
        // 裸 startsWith 会把 /storage/emulated/0/cache2 误判成 /storage/emulated/0/cache 的子路径。
        assertFalse(SharedFilePolicy.isUnder("/storage/emulated/0/cache", "/storage/emulated/0/cache2/a.mp4"))
        assertFalse(SharedFilePolicy.isUnder("/storage/5C00-1D0E", "/storage/5C00-1D0E2/a.mp4"))
    }

    @Test
    fun `空值与空串一律判否，不抛异常`() {
        assertFalse(SharedFilePolicy.isUnder(null, "/storage/emulated/0/a.mp4"))
        assertFalse(SharedFilePolicy.isUnder("/storage/emulated/0", null))
        assertFalse(SharedFilePolicy.isUnder("", "/storage/emulated/0/a.mp4"))
        assertFalse(SharedFilePolicy.isUnder("/storage/emulated/0", ""))
        // 根只剩 "/" 时 trimEnd('/') 变成空串：整个文件系统不能当成一个共享根。
        assertFalse(SharedFilePolicy.isUnder("/", "/storage/emulated/0/a.mp4"))
    }

    @Test
    fun `外置卷路径算在外置卷根内`() {
        // 用户真机现场：外置卡根 /storage/5C00-1D0E，缓存目录在 Android/media/<包名> 下。
        assertTrue(
            SharedFilePolicy.isUnder(
                "/storage/5C00-1D0E",
                "/storage/5C00-1D0E/Android/media/com.RobinNotBad.BiliClient.coexist/标题/video.mp4"
            )
        )
    }

    // ==================== volumeRoot ====================

    @Test
    fun `从应用专属外部目录反推卷根`() {
        assertEquals(
            "/storage/5C00-1D0E",
            SharedFilePolicy.volumeRoot("/storage/5C00-1D0E/Android/data/com.RobinNotBad.BiliClient.coexist/files")
        )
        assertEquals(
            "/storage/emulated/0",
            SharedFilePolicy.volumeRoot("/storage/emulated/0/Android/media/com.RobinNotBad.BiliClient")
        )
        // 内部目录（/data/user/0/<包名>/files）不是「外部卷」，不能反推出 /data/user/0/<包名>。
        assertNull(SharedFilePolicy.volumeRoot("/data/user/0/com.RobinNotBad.BiliClient/files"))
        // 没有 /Android/ 段、或它就在开头（= 没有卷名前缀）时都不能当卷根。
        assertNull(SharedFilePolicy.volumeRoot("/storage/5C00-1D0E/Movies"))
        assertNull(SharedFilePolicy.volumeRoot("/Android/data/com.RobinNotBad.BiliClient/files"))
        assertNull(SharedFilePolicy.volumeRoot(null))
        assertNull(SharedFilePolicy.volumeRoot(""))
    }

    @Test
    fun `反推出的卷根必须是绝对路径且不以斜杠结尾`() {
        val root = SharedFilePolicy.volumeRoot("/storage/emulated/0/Android/data/x/files")
        assertEquals("/storage/emulated/0", root)
        assertFalse(root!!.endsWith("/"))
    }

    // ==================== volumeRoots ====================

    @Test
    fun `一批外部目录反推卷根时去重并保持顺序`() {
        val roots = SharedFilePolicy.volumeRoots(
            listOf(
                "/storage/emulated/0/Android/data/pkg/files",
                "/storage/5C00-1D0E/Android/data/pkg/files",
                "/storage/emulated/0/Android/media/pkg", // 与第一条同一块卷
                null,
                "/storage/5C00-1D0E/Android/media/pkg"
            )
        )
        assertEquals(listOf("/storage/emulated/0", "/storage/5C00-1D0E"), roots)
    }

    @Test
    fun `全为空列表时返回空集`() {
        assertTrue(SharedFilePolicy.volumeRoots(emptyList()).isEmpty())
        assertTrue(SharedFilePolicy.volumeRoots(listOf(null, "")).isEmpty())
    }

    // ==================== findContainingRoot ====================

    @Test
    fun `多条根命中时返回最长最具体的那条`() {
        val roots = listOf("/storage", "/storage/5C00-1D0E")
        assertEquals(
            "/storage/5C00-1D0E",
            SharedFilePolicy.findContainingRoot(roots, "/storage/5C00-1D0E/Android/media/pkg/a/video.mp4")
        )
    }

    @Test
    fun `没有任何根包含时返回 null`() {
        assertNull(SharedFilePolicy.findContainingRoot(listOf("/storage/emulated/0"), "/data/local/tmp/a.mp4"))
        assertNull(SharedFilePolicy.findContainingRoot(emptyList(), "/storage/emulated/0/a.mp4"))
        assertNull(SharedFilePolicy.findContainingRoot(listOf("/storage/emulated/0"), null))
        assertNull(SharedFilePolicy.findContainingRoot(listOf("/storage/emulated/0"), ""))
    }

    @Test
    fun `前缀相似的根不会把兄弟目录认领走`() {
        // 根是 /storage/emulated/0/cache，请求的是 /storage/emulated/0/cache2/a.mp4
        assertNull(SharedFilePolicy.findContainingRoot(listOf("/storage/emulated/0/cache"), "/storage/emulated/0/cache2/a.mp4"))
    }

    @Test
    fun `重复登记的等长根保留先出现的那条`() {
        // findContainingRoot 只在「更长」时替换，等长不替换：行为固定下来，避免以后把
        // `root.length > best.length` 改成 `>=` 造成归属抖动（日志/测试会跟着变）。
        val roots = listOf("/storage/0000-0000", "/storage/0000-0000")
        assertEquals("/storage/0000-0000", SharedFilePolicy.findContainingRoot(roots, "/storage/0000-0000/a.mp4"))
    }

    @Test
    fun `多余斜杠要由 canonicalPath 归一化，isUnder 本身不猜`() {
        // isUnder 是纯字符串分段比较，不认识 "//"（两个方向都不会被判成包含）；真实链路里
        // 根与文件都先 canonicalPath（SharedFileRoots / LocalFileProvider 都做了），所以这里
        // 只锁「多余斜杠不会让 isUnder 误判成包含」。
        assertFalse(SharedFilePolicy.isUnder("/storage//0000-0000", "/storage/0000-0000/a.mp4"))
        assertFalse(SharedFilePolicy.isUnder("/storage/0000-0000", "/storage//0000-0000/a.mp4"))
    }
}
