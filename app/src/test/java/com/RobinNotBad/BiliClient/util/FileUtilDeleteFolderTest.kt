package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 锁住 [FileUtil.deleteFolderRecursively]（反馈 #58「点击删除后视频没消失，文件也没删」）。
 *
 * 旧实现 [FileUtil.deleteFolder] 的三个坑都在这里钉住：
 * 1. **空子目录永远删不掉**：子目录只有在 `listFiles().length != 0` 时才递归，空目录被跳过，
 *    于是父目录 `delete()` 必然失败、整个缓存目录原地留下；
 * 2. 删没删掉没人知道：签名是 void，所有 `delete()` 的返回值都被丢弃；
 * 3. `listFiles()` 返回 null 时还会 NPE。
 *
 * 新实现要回答两件事：**递归删干净（含空目录）**、**删除结果如实返回**。
 * 这里只碰 `java.io.File`，能在纯 JVM 单测里跑。
 */
class FileUtilDeleteFolderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** 造一个和真实缓存目录同构的目录：成品 + 标记 + 空子目录。 */
    private fun newCacheDir(name: String): File {
        val dir = tempFolder.newFolder(name)
        File(dir, "video.mp4").writeText("video")
        File(dir, "audio.m4a").writeText("audio")
        File(dir, "cover.png").writeText("cover")
        File(dir, "danmaku.xml").writeText("<i/>")
        File(dir, ".quality").writeText("80")
        return dir
    }

    @Test
    fun `普通缓存目录被整个删掉`() {
        val dir = newCacheDir("某个视频")

        assertTrue(FileUtil.deleteFolderRecursively(dir))
        assertFalse("目录本身必须消失", dir.exists())
    }

    @Test
    fun `空子目录也会被删掉并且父目录随之消失`() {
        // 这就是 #58 的确定性 bug：subtitles/ 建了但没下到字幕（或删到一半留下空目录）时，
        // 旧实现跳过空目录 → 父目录 delete() 失败 → 任务目录原地留下
        val dir = newCacheDir("某个视频")
        val emptySub = File(dir, "subtitles")
        assertTrue("测试前置：空子目录要建出来", emptySub.mkdirs())

        assertTrue(FileUtil.deleteFolderRecursively(dir))
        assertFalse("空子目录不能拖住删除", dir.exists())
    }

    @Test
    fun `多 P 合集的所有分 P 目录与空目录都删干净`() {
        val root = tempFolder.newFolder("某部番剧")
        for (page in listOf("第1P", "第2P", "第3P")) {
            val pageDir = File(root, page)
            assertTrue(pageDir.mkdirs())
            File(pageDir, "video.mp4").writeText("v")
            File(pageDir, "audio.m4a").writeText("a")
        }
        // 分 P 里的字幕目录可能有两个：一个空的、一个有内容的
        assertTrue(File(File(root, "第1P"), "subtitles").mkdirs())
        val subtitleDir = File(File(root, "第2P"), "subtitles")
        assertTrue(subtitleDir.mkdirs())
        File(subtitleDir, "zh-CN.json").writeText("{}")

        assertTrue(FileUtil.deleteFolderRecursively(root))
        assertFalse(root.exists())
    }

    @Test
    fun `目录不存在时视为已经删掉`() {
        assertTrue(FileUtil.deleteFolderRecursively(File(tempFolder.root, "never_existed")))
    }

    @Test
    fun `传单个文件也能删掉`() {
        val file = tempFolder.newFile("video_new.mp4")

        assertTrue(FileUtil.deleteFolderRecursively(file))
        assertFalse(file.exists())
    }

    @Test
    fun `null 返回false且不抛异常`() {
        assertFalse(FileUtil.deleteFolderRecursively(null))
    }

    @Test
    fun `只删目标目录不动兄弟目录`() {
        val parent = tempFolder.newFolder("缓存根")
        val target = File(parent, "要删的视频")
        assertTrue(target.mkdirs())
        File(target, "video.mp4").writeText("v")
        val sibling = File(parent, "别的视频")
        assertTrue(sibling.mkdirs())
        File(sibling, "video.mp4").writeText("keep")

        assertTrue(FileUtil.deleteFolderRecursively(target))

        assertFalse(target.exists())
        assertTrue("兄弟目录里的缓存不能被顺手删掉", File(sibling, "video.mp4").exists())
        assertEquals("keep", File(sibling, "video.mp4").readText())
        assertTrue("缓存根目录本身也不能被删", parent.isDirectory)
    }

    @Test
    fun `旧签名 deleteFolder 也修好了空子目录`() {
        // 旧调用点（分 P 选择页 / 下载列表 / 视频详情页清缓存）仍然调 void 版，
        // 它们必须同样受益，否则同一个缓存从别的入口删依然删不干净
        val dir = newCacheDir("某个视频")
        assertTrue(File(dir, "subtitles").mkdirs())

        FileUtil.deleteFolder(dir)

        assertFalse(dir.exists())
    }
}
