package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.VideoFolder
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 锁住 [CacheMigrator]：缓存页「迁移到本应用」就是把它搬进本机下载根。
 *
 * 这里全部走真实临时目录（纯 JVM，不碰 Android API），锁三件容易错的事：
 * 1. 目标名冲突时不能覆盖已有缓存；
 * 2. 整目录（含多 P 子目录、封面、画质文件）一起搬，不是只搬媒体文件；
 * 3. 对方的 .folders_info.json 能被读成「标题 → 夹名」，且磁盘上的清洗名与记录里的原始标题能对上。
 */
class CacheMigratorTest {

    private val created = ArrayList<File>()

    private fun tempDir(prefix: String): File {
        val dir = Files.createTempDirectory(prefix).toFile()
        created.add(dir)
        return dir
    }

    /** 造一条单 P 缓存：封面 + 画质 + 媒体文件。 */
    private fun makeSingleVideo(root: File, title: String): File {
        val dir = File(root, title)
        dir.mkdirs()
        File(dir, ".cover.jpg").writeText("cover")
        File(dir, ".quality").writeText("80")
        File(dir, "video.mp4").writeText("0123456789")
        return dir
    }

    @After
    fun cleanup() {
        for (dir in created) CacheMigrator.deleteTree(dir)
    }

    // ---------- 目标名 ----------

    @Test
    fun `名字没被占用时原样使用`() {
        assertEquals("第一集", CacheMigrator.uniqueDirName(setOf("第二集"), "第一集"))
    }

    @Test
    fun `名字撞了就依次加后缀`() {
        assertEquals("第一集 (2)", CacheMigrator.uniqueDirName(setOf("第一集"), "第一集"))
        assertEquals(
            "第一集 (3)",
            CacheMigrator.uniqueDirName(setOf("第一集", "第一集 (2)"), "第一集")
        )
    }

    @Test
    fun `后缀试到 999 之后用时间戳兜底不会死循环`() {
        val taken = HashSet<String>()
        taken.add("第一集")
        for (i in 2..999) taken.add("第一集 ($i)")

        val name = CacheMigrator.uniqueDirName(taken, "第一集")

        assertTrue(name.startsWith("第一集 ("))
        assertFalse(taken.contains(name))
    }

    // ---------- 搬迁 ----------

    @Test
    fun `整个目录搬进目标根且源目录不再存在`() {
        val source = tempDir("mig-source")
        val dest = tempDir("mig-dest")
        val videoDir = makeSingleVideo(source, "第一集")

        val result = CacheMigrator.moveDir(videoDir, dest)

        assertTrue(result.ok)
        assertTrue(result.sourceCleaned)
        val moved = File(dest, "第一集")
        assertEquals(moved.absolutePath, result.dest!!.absolutePath)
        assertTrue(File(moved, "video.mp4").isFile)
        assertEquals("0123456789", File(moved, "video.mp4").readText())
        assertTrue(File(moved, ".quality").isFile)
        assertFalse(videoDir.exists())
    }

    @Test
    fun `合集的多 P 子目录一起搬`() {
        val source = tempDir("mig-multi-source")
        val dest = tempDir("mig-multi-dest")
        val videoDir = File(source, "某合集")
        File(videoDir, "1").mkdirs()
        File(videoDir, "2").mkdirs()
        File(videoDir, "1/video.mp4").writeText("p1")
        File(videoDir, "2/video.mp4").writeText("p2")

        val result = CacheMigrator.moveDir(videoDir, dest)

        assertTrue(result.ok)
        val moved = File(dest, "某合集")
        assertEquals("p1", File(moved, "1/video.mp4").readText())
        assertEquals("p2", File(moved, "2/video.mp4").readText())
    }

    @Test
    fun `目标里已有同名缓存时不覆盖而是加后缀`() {
        val source = tempDir("mig-dup-source")
        val dest = tempDir("mig-dup-dest")
        val videoDir = makeSingleVideo(source, "第一集")
        val existing = makeSingleVideo(dest, "第一集")
        File(existing, "video.mp4").writeText("old")

        val result = CacheMigrator.moveDir(videoDir, dest)

        assertTrue(result.ok)
        assertEquals("第一集 (2)", result.dest!!.name)
        // 本机原有的那条一个字都没动
        assertEquals("old", File(existing, "video.mp4").readText())
        assertEquals("0123456789", File(result.dest!!, "video.mp4").readText())
    }

    @Test
    fun `源目录不存在时返回失败且不动目标根`() {
        val source = tempDir("mig-missing-source")
        val dest = tempDir("mig-missing-dest")

        val result = CacheMigrator.moveDir(File(source, "不存在"), dest)

        assertNull(result.dest)
        assertFalse(result.ok)
        assertTrue(dest.listFiles()!!.isEmpty())
    }

    @Test
    fun `deleteTree 递归删干净`() {
        val root = tempDir("mig-del")
        val nested = File(root, "a/b/c")
        nested.mkdirs()
        File(nested, "video.mp4").writeText("x")

        assertTrue(CacheMigrator.deleteTree(root))
        assertFalse(root.exists())
    }

    // ---------- 对方的文件夹层级 ----------

    private fun writeFolders(root: File, vararg folders: Pair<String, List<String>>) {
        val array = JSONArray()
        for ((name, titles) in folders) {
            val folder = VideoFolder(name)
            folder.videoTitles = ArrayList(titles)
            array.put(folder.toJson())
        }
        File(root, ".folders_info.json").writeText(array.toString())
    }

    @Test
    fun `读对方根目录的 folders_info 得到标题到夹名的索引`() {
        val source = tempDir("mig-folders")
        writeFolders(source, "追番" to listOf("第一集", "第二集"), "教程" to listOf("怎么用"))

        val index = CacheMigrator.folderIndex(source)

        assertEquals("追番", CacheMigrator.folderOf(index, "第一集"))
        assertEquals("教程", CacheMigrator.folderOf(index, "怎么用"))
        assertEquals("", CacheMigrator.folderOf(index, "没记录的视频"))
    }

    @Test
    fun `记录里是原始标题而磁盘上是清洗名时也能对上`() {
        val source = tempDir("mig-folders-clean")
        // 下载侧会把 / 换成全角，所以磁盘目录名是「第一集／P1」，记录里存的是原始标题
        writeFolders(source, "追番" to listOf("第一集/P1"))

        val index = CacheMigrator.folderIndex(source)

        assertEquals("追番", CacheMigrator.folderOf(index, "第一集／P1"))
    }

    @Test
    fun `没有 folders_info 时索引为空`() {
        val source = tempDir("mig-folders-none")

        val index = CacheMigrator.folderIndex(source)

        assertTrue(index.isEmpty())
        assertEquals("", CacheMigrator.folderOf(index, "第一集"))
    }

    @Test
    fun `列表顺序按 folders_info 的数组顺序读出来`() {
        val source = tempDir("mig-folders-order")
        writeFolders(source, "先" to listOf("a"), "后" to listOf("b"))

        val folders = FolderManager.getAllFoldersAt(source)

        assertEquals(listOf("先", "后"), folders.map { it.name })
        assertNotNull(folders[0].videoTitles)
    }

    // ---------- 预建文件夹（对方的夹先在本机建好） ----------

    @Test
    fun `只挑本机还缺的夹并保持首次出现顺序`() {
        val wanted = listOf("追番", "教程", "追番", "已存在")

        val toCreate = CacheMigrator.foldersToCreate(setOf("已存在"), wanted)

        assertEquals(listOf("追番", "教程"), toCreate)
    }

    @Test
    fun `空名与不合法夹名不会被拿去建夹`() {
        // "a/b" 带路径分隔符、长串超过 30 字，都建不出来：留着它们只会让条目归进不存在的夹
        val longName = "长".repeat(31)
        val wanted = listOf("", "   ", "a/b", longName, "正常的夹")

        val toCreate = CacheMigrator.foldersToCreate(emptySet(), wanted)

        assertEquals(listOf("正常的夹"), toCreate)
    }

    @Test
    fun `夹名两头有空格时按去掉空格后的名字建`() {
        val toCreate = CacheMigrator.foldersToCreate(emptySet(), listOf(" 追番 "))

        assertEquals(listOf("追番"), toCreate)
    }

    @Test
    fun `本机全都有时不需要建任何夹`() {
        assertTrue(CacheMigrator.foldersToCreate(setOf("追番", "教程"), listOf("教程", "追番")).isEmpty())
    }

    @Test
    fun `没有对方夹名时什么也不建`() {
        assertTrue(CacheMigrator.foldersToCreate(emptySet(), emptyList()).isEmpty())
    }
}
