package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [CacheDeletePlan] 的纯 JVM 单测（反馈 #162）。
 *
 * 用户报的三件事，能被机器判定的部分都在这里：
 * - 「目录删了 / 没删 / 删了一半」必须分得开——旧实现只会在路径不存在时静默返回，界面一律说
 *   「已清除」，所以先测**结果分级**；
 * - 「删错目录」的根子是按标题硬拼路径，所以测**候选集合**：多 P 合集要落到合集根目录，
 *   媒体文件路径反推出来的目录与标题拼出来的目录不一致时两个都得删。
 *
 * 真机上的删除行为（被占用、没写权限、删完文件到底还在不在）不在单测范围内，见交接说明。
 */
class CacheDeletePlanTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var rootDir: File? = null

    /** 本用例的缓存根目录（等于 `FileUtil.getVideoDownloadPath()` 那个位置）。 */
    private fun root(): File = rootDir ?: tempFolder.newFolder("root").also { rootDir = it }

    /**
     * 在缓存根下造一串目录并塞入典型缓存文件（`video.mp4` / `cover.png`），返回最深那一级。
     *
     * 有真实文件才谈得上「删干净」：只删空目录的话，父目录 `delete()` 成功也可能掩盖残留。
     */
    private fun cacheDir(vararg names: String): File {
        var dir = root()
        for (name in names) {
            dir = File(dir, name)
            dir.mkdirs()
        }
        File(dir, "video.mp4").writeText("fake-video")
        File(dir, "cover.png").writeText("fake-cover")
        return dir
    }

    /** 候选路径按 `/` 规范化后比较（Windows 上 `File.path` 是反斜杠）。 */
    private fun normalized(file: File): String = CachePathResolver.normalizePath(file.path)

    // ---------- 一、候选目录：多 P 合集 / 单 P / 标题与目录名不一致 ----------

    @Test
    fun `多 P 合集的候选是合集根目录，不是分 P 子目录`() {
        val root = root()
        val page1 = cacheDir("某部番剧", "第1P")
        cacheDir("某部番剧", "第2P")

        val candidates = CacheDeletePlan.candidateDirs(root.path, "某部番剧", File(page1, "video.mp4").path)

        // 缓存列表里「一部合集」是一个条目，删它就该删合集根，而不是只删第一个分 P
        assertEquals(listOf(normalized(File(root, "某部番剧"))), candidates.map { normalized(it) })
    }

    @Test
    fun `多 P 合集删掉的是整部合集，分 P 目录一起没了`() {
        val root = root()
        val page1 = cacheDir("某部番剧", "第1P")
        val page2 = cacheDir("某部番剧", "第2P")
        val collection = File(root, "某部番剧")

        val result = CacheDeletePlan.execute(root.path, "某部番剧", File(page1, "video.mp4").path)

        assertEquals(CacheDeletePlan.Outcome.SUCCESS, result.outcome)
        assertEquals(1, result.existed)
        assertEquals(1, result.deleted)
        assertFalse("合集根目录应该整个消失", collection.exists())
        assertFalse("分 P 子目录不能留下", page2.exists())
        assertTrue("缓存根目录本身不能被删", root.isDirectory)
    }

    @Test
    fun `单 P 视频删掉自己的目录，缓存根目录留着`() {
        val root = root()
        val dir = cacheDir("某个单 P 视频")

        val result = CacheDeletePlan.execute(root.path, "某个单 P 视频", File(dir, "video.mp4").path)

        assertEquals(CacheDeletePlan.Outcome.SUCCESS, result.outcome)
        assertEquals(1, result.existed)
        assertFalse(dir.exists())
        assertTrue(root.isDirectory)
    }

    @Test
    fun `单 P 与多 P 各删各的，互不牵连`() {
        val root = root()
        val single = cacheDir("单 P 视频")
        val page = cacheDir("多 P 合集", "第1P")
        val collection = File(root, "多 P 合集")

        val singleResult = CacheDeletePlan.execute(root.path, "单 P 视频", File(single, "video.mp4").path)
        val multiResult = CacheDeletePlan.execute(root.path, "多 P 合集", File(page, "video.mp4").path)

        assertEquals(CacheDeletePlan.Outcome.SUCCESS, singleResult.outcome)
        assertEquals(CacheDeletePlan.Outcome.SUCCESS, multiResult.outcome)
        assertFalse(single.exists())
        assertFalse(collection.exists())
    }

    @Test
    fun `媒体路径反推的目录与标题拼出的目录同时存在时两个都删`() {
        val root = root()
        // 老数据 / 用户手工搬过的现场：磁盘目录名和列表里的标题不一致
        val byMedia = cacheDir("磁盘上的目录名")
        val byTitle = cacheDir("标题")

        val result = CacheDeletePlan.execute(root.path, "标题", File(byMedia, "video.mp4").path)

        assertEquals(2, result.existed)
        assertEquals(2, result.deleted)
        assertEquals(CacheDeletePlan.Outcome.SUCCESS, result.outcome)
        assertFalse(byMedia.exists())
        assertFalse("按标题拼出来的那份老目录也要删掉", byTitle.exists())
    }

    @Test
    fun `候选去重后同一个目录只删一次`() {
        val root = root()
        val dir = cacheDir("同一个视频")
        var calls = 0

        val result = CacheDeletePlan.execute(
            root.path, "同一个视频", File(dir, "video.mp4").path
        ) { target ->
            calls++
            FileUtil.deleteFolderRecursively(target)
        }

        assertEquals("媒体路径反推和标题拼接指向同一个目录时不能删两遍", 1, calls)
        assertEquals(1, result.existed)
        assertEquals(CacheDeletePlan.Outcome.SUCCESS, result.outcome)
    }

    // ---------- 二、结果分级：成功 / 部分失败 / 没找到 ----------

    @Test
    fun `一个候选目录都不存在时是没找到，不是删除成功`() {
        val root = root()
        cacheDir("别的视频")

        val result = CacheDeletePlan.execute(root.path, "从来没缓存过的视频")

        assertEquals(CacheDeletePlan.Outcome.NOT_FOUND, result.outcome)
        assertEquals(0, result.existed)
        assertEquals(0, result.deleted)
        assertFalse(result.ok)
        assertNull(result.firstFailedName)
    }

    @Test
    fun `有一个候选目录删不掉时是部分失败，另一个照样删掉`() {
        val root = root()
        val byMedia = cacheDir("磁盘上的目录名")
        val byTitle = cacheDir("标题")

        val result = CacheDeletePlan.execute(
            root.path, "标题", File(byMedia, "video.mp4").path
        ) { target ->
            // 模拟「文件被占用 / 没有写权限」：一个删不掉，另一个正常
            if (target.name == "标题") false else FileUtil.deleteFolderRecursively(target)
        }

        assertEquals(CacheDeletePlan.Outcome.PARTIAL_FAILED, result.outcome)
        assertEquals(2, result.existed)
        assertEquals(1, result.deleted)
        assertFalse(result.ok)
        assertFalse(byMedia.exists())
        assertTrue("删不掉的目录必须留着，不能假装删了", byTitle.exists())
        assertEquals("标题", result.firstFailedName)
    }

    @Test
    fun `判结局只看真实计数：没找到优先于部分失败`() {
        // 一个候选都没找到时，即便调用方塞了失败目录也仍然是「没找到」（不该提示成删失败）
        val notFound = CacheDeletePlan.judge(0, 0, listOf(File("/root/随便")))
        assertEquals(CacheDeletePlan.Outcome.NOT_FOUND, notFound.outcome)

        val success = CacheDeletePlan.judge(2, 2)
        assertEquals(CacheDeletePlan.Outcome.SUCCESS, success.outcome)
        assertTrue(success.ok)

        val partial = CacheDeletePlan.judge(2, 1, listOf(File("/root/删不掉的目录")))
        assertEquals(CacheDeletePlan.Outcome.PARTIAL_FAILED, partial.outcome)
        assertEquals("删不掉的目录", partial.firstFailedName)
    }

    // ---------- 三、安全边界 ----------

    @Test
    fun `标题为空时不会把整个缓存根目录当成视频目录删掉`() {
        val root = root()
        val other = cacheDir("别的视频")

        val result = CacheDeletePlan.execute(root.path, "")

        assertEquals(CacheDeletePlan.Outcome.NOT_FOUND, result.outcome)
        assertTrue("缓存根目录不能被当成某部视频的目录删掉", root.isDirectory)
        assertTrue("别人的缓存也不能被顺手删掉", other.exists())
    }

    @Test
    fun `缓存根目录为空时是没找到，且不碰文件系统`() {
        val result = CacheDeletePlan.execute(null, "任意标题")

        assertEquals(CacheDeletePlan.Outcome.NOT_FOUND, result.outcome)
        assertEquals(0, result.existed)
    }

    // ---------- 四、结局 → 提示文案 ----------

    @Test
    fun `三档结局各自对应不同的提示文案`() {
        val success = CacheDeletePlan.messageOf(
            CacheDeletePlan.Result(CacheDeletePlan.Outcome.SUCCESS, 1, 1)
        )
        val partial = CacheDeletePlan.messageOf(
            CacheDeletePlan.Result(
                CacheDeletePlan.Outcome.PARTIAL_FAILED, 2, 1, listOf(File("/root/目录名"))
            )
        )
        val notFound = CacheDeletePlan.messageOf(
            CacheDeletePlan.Result(CacheDeletePlan.Outcome.NOT_FOUND, 0, 0)
        )

        assertEquals(R.string.msg_cache_delete_success, success.resId)
        assertNull("成功文案不需要参数", success.arg)

        assertEquals(R.string.msg_cache_delete_partial_failed, partial.resId)
        assertEquals("部分失败要把没删掉的目录名报出来", "目录名", partial.arg)

        assertEquals(R.string.msg_cache_delete_not_found, notFound.resId)
        assertNull(notFound.arg)

        // 三档不能撞成同一句：旧实现正是「删没删都提示已清除」
        assertEquals(
            "三档提示必须是三条不同文案",
            3,
            setOf(success.resId, partial.resId, notFound.resId).size
        )
    }
}
