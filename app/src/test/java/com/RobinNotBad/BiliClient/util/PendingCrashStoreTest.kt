package com.RobinNotBad.BiliClient.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 崩溃报告本地暂存区（[PendingCrashStore]）的落盘行为。
 *
 * <p>这块代码存在的理由只有一个：**上传失败不再等于崩溃记录消失**。所以钉住的是「存得进去、读得回来、
 * 存不下时删最旧的、坏文件不顶替好文件」这几件——它们错了都不会抛异常，只会让用户在崩溃之后
 * 又一次什么都看不到。
 */
class PendingCrashStoreTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "pending_crash_test_" + System.nanoTime())
        assertTrue("临时目录必须建得起来", dir.mkdirs())
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** 隔几毫秒再写：文件名以毫秒时间戳开头、排序也看修改时间，靠它保证先后关系是确定的。 */
    private fun saveOrdered(content: String): File {
        val file = PendingCrashStore.save(dir, content)
        assertNotNull("落盘不该失败：" + content, file)
        Thread.sleep(3)
        return file!!
    }

    // ---------- 存 / 读 ----------

    @Test
    fun 存一条能原样读回来() {
        val payload = "{\"exception\":\"java.lang.NullPointerException\"}"
        val file = PendingCrashStore.save(dir, payload)!!
        assertTrue("文件应该真的落在目录里", file.isFile)
        assertTrue("文件名要以 .json 结尾，补传时按后缀筛", file.name.endsWith(".json"))
        assertEquals("读回来的必须和写进去的一模一样", payload, PendingCrashStore.read(file))
    }

    @Test
    fun 目录不存在时自动创建() {
        val nested = File(dir, "a/b/c")
        assertNotNull(PendingCrashStore.save(nested, "{}"))
        assertEquals(1, PendingCrashStore.list(nested).size)
    }

    @Test
    fun 目录为空时列表是空的() {
        assertTrue(PendingCrashStore.list(dir).isEmpty())
        assertTrue(PendingCrashStore.list(null).isEmpty())
    }

    // ---------- 排序：最旧的先试 ----------

    @Test
    fun 最旧的排在最前面() {
        saveOrdered("old")
        saveOrdered("middle")
        saveOrdered("new")
        val contents = PendingCrashStore.list(dir).map { PendingCrashStore.read(it) }
        assertEquals(listOf("old", "middle", "new"), contents)
    }

    @Test
    fun 失败后touch会排到队尾() {
        val first = saveOrdered("first")
        saveOrdered("second")
        PendingCrashStore.touch(first)
        Thread.sleep(3)
        assertEquals("被 touch 过的那条应该排到后面，否则它会把队列堵死", "first", PendingCrashStore.read(PendingCrashStore.list(dir).last()))
    }

    // ---------- 上限 ----------

    @Test
    fun 超过上限时删最旧的() {
        val total = PendingCrashStore.MAX_FILES + 3
        for (i in 0 until total) {
            saveOrdered("seq-" + i)
        }
        val left = PendingCrashStore.list(dir).map { PendingCrashStore.read(it) }
        assertEquals("条数必须卡在上限", PendingCrashStore.MAX_FILES, left.size)
        for (i in 0 until 3) {
            assertFalse("最旧的 seq-" + i + " 应该已经被删掉", left.contains("seq-" + i))
        }
        assertTrue("最新的那条必须还在", left.contains("seq-" + (total - 1)))
    }

    // ---------- 覆盖 ----------

    @Test
    fun rewrite覆盖同一个文件且不新增() {
        val file = saveOrdered("before")
        assertTrue(PendingCrashStore.rewrite(file, "after"))
        assertEquals("after", PendingCrashStore.read(file))
        assertEquals("重写不该多出一条", 1, PendingCrashStore.list(dir).size)
    }

    @Test
    fun rewrite遇到空文件返回false() {
        assertFalse(PendingCrashStore.rewrite(null, "x"))
    }

    // ---------- 坏文件 ----------

    @Test
    fun 空文件读出来是null() {
        val file = File(dir, "empty.json")
        file.writeText("", Charsets.UTF_8)
        assertNull("空文件当坏文件处理，否则会拿一个空报文去发请求", PendingCrashStore.read(file))
    }

    @Test
    fun 超大文件读出来是null() {
        val file = File(dir, "huge.json")
        file.writeText("x".repeat(PendingCrashStore.MAX_FILE_BYTES + 1), Charsets.UTF_8)
        assertNull("超过上限的文件不该被读进内存", PendingCrashStore.read(file))
    }

    @Test
    fun 目录不存在时读出来是null() {
        assertNull(PendingCrashStore.read(null))
    }

    @Test
    fun 非json后缀不算待传报告() {
        File(dir, "junk.txt").writeText("not a crash", Charsets.UTF_8)
        saveOrdered("real")
        assertEquals("只有 .json 才算待传", 1, PendingCrashStore.list(dir).size)
    }
}