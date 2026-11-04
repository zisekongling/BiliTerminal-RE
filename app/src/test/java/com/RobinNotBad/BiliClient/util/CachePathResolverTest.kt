package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 锁住 [CachePathResolver]：反馈 #58「已缓存文件无法在应用内删除」的根因之一就是删除路径靠
 * `File(根目录, 标题)` 硬拼，而真实落盘目录可能是「下载侧清洗过的标题」「多 P 合集子目录」
 * 「用户改过的根目录」「外置卡根目录」这几种情况。
 *
 * 这里全部是字符串层面的断言（本类不碰文件系统），所以能在纯 JVM 单测里跑。
 */
class CachePathResolverTest {

    /** 默认缓存根（API 21+ 的默认值，见 FileUtil/StoragePathPolicy）。 */
    private val defaultRoot = "/storage/emulated/0/Android/media/com.RobinNotBad.BiliClient"

    // ---------- 普通标题 ----------

    @Test
    fun `普通标题只按清洗后的标题拼一个候选`() {
        val candidates = CachePathResolver.videoDirCandidates(defaultRoot, "【测试】第一集")

        assertEquals(1, candidates.size)
        assertEquals("$defaultRoot/【测试】第一集", candidates[0])
    }

    // ---------- 带非法字符的标题 ----------

    @Test
    fun `带非法字符的标题会同时给出清洗后与原始两种候选`() {
        // 下载侧用 FileUtil.stringToFile 把 / : * ? " < > | \ 换成全角，目录名是清洗后的那份
        val raw = "第一集：P1/P2*测试"
        val safe = FileUtil.stringToFile(raw)

        val candidates = CachePathResolver.videoDirCandidates(defaultRoot, raw)

        assertEquals("$defaultRoot/$safe", candidates[0])
        // 老数据/手工搬进来的目录可能直接用了原始标题，留一个兜底候选
        assertEquals("$defaultRoot/$raw", candidates[1])
        // 两个候选都不能含半角分隔符以外的非法字符拼出来的错路径重复项
        assertEquals(2, candidates.size)
    }

    @Test
    fun `标题已经是清洗后的名字时不会重复给候选`() {
        val title = FileUtil.stringToFile("第一集：测试")
        val candidates = CachePathResolver.videoDirCandidates(defaultRoot, title)

        assertEquals(listOf("$defaultRoot/$title"), candidates)
    }

    // ---------- 分 P（合集） ----------

    @Test
    fun `多 P 合集按实际文件路径反推到合集根目录`() {
        // 缓存列表把「整部合集」当一个条目，它的目录是合集根，不是分 P 子目录
        val media = "$defaultRoot/某部番剧/第1P/video.mp4"

        val candidates = CachePathResolver.videoDirCandidates(defaultRoot, "某部番剧", media)

        assertEquals("$defaultRoot/某部番剧", candidates[0])
        // 再按标题拼一次做兜底，且去重后不应出现分 P 目录
        assertTrue(candidates.contains("$defaultRoot/某部番剧"))
        assertFalse("不能把分 P 子目录当成视频目录", candidates.any { it.endsWith("/第1P") })
    }

    @Test
    fun `单 P 视频按实际文件路径反推到自己的目录`() {
        val media = "$defaultRoot/某个视频/video.mp4"

        val candidates = CachePathResolver.videoDirCandidates(defaultRoot, "某个视频", media)

        assertEquals("$defaultRoot/某个视频", candidates[0])
        assertEquals(listOf("$defaultRoot/某个视频"), candidates)
    }

    @Test
    fun `DASH 双文件的音频路径也能反推`() {
        val media = "$defaultRoot/某个视频/audio.m4a"

        assertEquals(
            "$defaultRoot/某个视频",
            CachePathResolver.videoDirCandidates(defaultRoot, "某个视频", media)[0]
        )
    }

    // ---------- 用户自定义下载根目录 ----------

    @Test
    fun `根目录被用户改过时按新根解析`() {
        val customRoot = "/storage/emulated/0/我的视频缓存"
        val media = "$customRoot/某个视频/video.mp4"

        val candidates = CachePathResolver.videoDirCandidates(customRoot, "某个视频", media)

        assertEquals("$customRoot/某个视频", candidates[0])
        assertFalse(
            "不能残留硬编码的默认根",
            candidates.any { it.contains("Android/media") }
        )
    }

    // ---------- 外置卡根目录 ----------

    @Test
    fun `外置卡根目录（多 P）也能反推`() {
        // 用户把缓存路径改到 SD 卡：FileProvider 崩溃那次（26.11.01）的同一类现场
        val sdRoot = "/storage/5C00-1D0E/Android/media/com.RobinNotBad.BiliClient"
        val media = "$sdRoot/某部番剧/第2P/video.mp4"

        val candidates = CachePathResolver.videoDirCandidates(sdRoot, "某部番剧", media)

        assertEquals("$sdRoot/某部番剧", candidates[0])
    }

    @Test
    fun `根目录末尾带分隔符时不拼出双斜杠`() {
        val rootWithSlash = "$defaultRoot/"

        assertEquals(
            "$defaultRoot/某个视频",
            CachePathResolver.videoDirCandidates(rootWithSlash, "某个视频")[0]
        )
    }

    // ---------- 路径不在当前根下 ----------

    @Test
    fun `实际文件路径不在当前根下时只按标题拼候选`() {
        // 例如根目录刚在「正式目录 / 降级目录」之间切换，模型里还留着旧根的路径
        val media = "/storage/emulated/0/别的目录/某个视频/video.mp4"

        val candidates = CachePathResolver.videoDirCandidates(defaultRoot, "某个视频", media)

        assertEquals(listOf("$defaultRoot/某个视频"), candidates)
    }

    @Test
    fun `根目录为空或标题为空时是安全的空结果`() {
        assertTrue(CachePathResolver.videoDirCandidates(null, "某个视频").isEmpty())
        assertTrue(CachePathResolver.videoDirCandidates("", "某个视频").isEmpty())
        assertTrue(CachePathResolver.videoDirCandidates(defaultRoot, "").isEmpty())
        assertTrue(CachePathResolver.videoDirCandidates(defaultRoot, null).isEmpty())
    }

    // ---------- 基础路径运算 ----------

    @Test
    fun `路径规范化统一分隔符并去掉多余分隔符与点段`() {
        assertEquals("$defaultRoot/某个视频", CachePathResolver.normalizePath("$defaultRoot/某个视频/"))
        assertEquals("$defaultRoot/某个视频", CachePathResolver.normalizePath("$defaultRoot//某个视频"))
        assertEquals("$defaultRoot/某个视频", CachePathResolver.normalizePath("$defaultRoot/./某个视频"))
        assertEquals("/", CachePathResolver.normalizePath("/"))
        assertEquals("", CachePathResolver.normalizePath(""))
        assertEquals("", CachePathResolver.normalizePath(null))
        // Windows 风格反斜杠（用户在设置里手输过路径时可能出现）也统一成 /
        assertEquals("$defaultRoot/某个视频", CachePathResolver.normalizePath("$defaultRoot\\某个视频"))
    }

    @Test
    fun `根目录判定不会把同前缀的兄弟目录算进去`() {
        assertTrue(CachePathResolver.isUnderRoot(defaultRoot, "$defaultRoot/某个视频/video.mp4"))
        assertTrue(CachePathResolver.isUnderRoot(defaultRoot, defaultRoot))
        // 前缀陷阱：/data/root2 不在 /data/root 下
        assertFalse(CachePathResolver.isUnderRoot("/data/root", "/data/root2/a"))
        assertFalse(CachePathResolver.isUnderRoot(null, "$defaultRoot/a"))
    }

    @Test
    fun `一级子目录解析覆盖文件与深层路径`() {
        assertEquals(
            "$defaultRoot/某个视频",
            CachePathResolver.directChildOf(defaultRoot, "$defaultRoot/某个视频/video.mp4")
        )
        assertEquals(
            "$defaultRoot/某部番剧",
            CachePathResolver.directChildOf(defaultRoot, "$defaultRoot/某部番剧/第1P/video.mp4")
        )
        assertEquals(
            "$defaultRoot/某个视频",
            CachePathResolver.directChildOf(defaultRoot, "$defaultRoot/某个视频")
        )
        assertNull("根目录自己没有一级子目录", CachePathResolver.directChildOf(defaultRoot, defaultRoot))
        assertNull(CachePathResolver.directChildOf(defaultRoot, "/storage/emulated/0/别的目录/x"))
        assertNull(CachePathResolver.directChildOf(null, "$defaultRoot/a"))
    }

    @Test
    fun `候选路径可以直接当 File 用`() {
        val candidate = CachePathResolver.videoDirCandidates(defaultRoot, "某个视频").first()

        // 只是确认拼出来的是合法路径（本类不承诺文件存在）。
        // 父路径要过一遍 normalizePath：单测可能跑在 Windows 主机上，File.parent 会给反斜杠。
        assertEquals("某个视频", File(candidate).name)
        assertEquals(defaultRoot, CachePathResolver.normalizePath(File(candidate).parent))
    }
}
