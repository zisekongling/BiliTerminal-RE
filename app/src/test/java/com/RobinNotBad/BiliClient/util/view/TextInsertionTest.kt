package com.RobinNotBad.BiliClient.util.view

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 光标插入 / 换行的落点计算。
 *
 * 这里钉的是用户能直接感觉到的错：连点两次换行变倒序、光标跑到插入内容前面、
 * 选了半句话点换行选区没被替换、输入框失去焦点（selection 为 -1）时换行跑到开头。
 * 真正的 `EditText` 写入要 Android 环境，单测只能跑纯函数这一层。
 */
class TextInsertionTest {

    private fun insert(source: String, start: Int, end: Int, insert: String) =
        TextInsertion.insert(source, start, end, insert)

    // ---------- 正常插入 ----------

    @Test
    fun 光标在末尾时追加并停在末尾之后() {
        val result = insert("第一行", 3, 3, "\n")
        assertEquals("第一行\n", result.text)
        assertEquals(4, result.cursor)
    }

    @Test
    fun 光标在中间时插在光标处() {
        val result = insert("abcd", 2, 2, "\n")
        assertEquals("ab\ncd", result.text)
        assertEquals(3, result.cursor)
    }

    @Test
    fun 光标在开头时插到最前() {
        val result = insert("abcd", 0, 0, "\n")
        assertEquals("\nabcd", result.text)
        assertEquals(1, result.cursor)
    }

    @Test
    fun 连续两次换行是正序不是倒序() {
        // 第一次插入后光标在 1，第二次必须插在它后面；否则用户看到的是「空行在光标上方」
        val once = insert("x", 1, 1, "\n")
        val twice = insert(once.text, once.cursor, once.cursor, "\n")
        assertEquals("x\n\n", twice.text)
        assertEquals(3, twice.cursor)
    }

    @Test
    fun 插入表情码这类多字符文本光标也在其后() {
        val result = insert("[]", 1, 1, "[doge]")
        assertEquals("[[doge]]", result.text)
        assertEquals(7, result.cursor)
    }

    // ---------- 选区 ----------

    @Test
    fun 有选区时替换选区而不是插在选区前() {
        val result = insert("abcd", 1, 3, "\n")
        assertEquals("a\nd", result.text)
        assertEquals(2, result.cursor)
    }

    @Test
    fun 反向选区按同区间替换() {
        val result = insert("abcd", 3, 1, "\n")
        assertEquals("a\nd", result.text)
        assertEquals(2, result.cursor)
    }

    @Test
    fun 全选后插入等于整段替换() {
        val result = insert("abcd", 0, 4, "新")
        assertEquals("新", result.text)
        assertEquals(1, result.cursor)
    }

    // ---------- 边界 ----------

    @Test
    fun 空的插入文本等于什么都不插() {
        val result = insert("abcd", 2, 2, "")
        assertEquals("abcd", result.text)
        assertEquals(2, result.cursor)
    }

    @Test
    fun 空源文本也能插() {
        val result = insert("", 0, 0, "\n")
        assertEquals("\n", result.text)
        assertEquals(1, result.cursor)
    }

    @Test
    fun 负索引被夹到开头而失焦的兜底在调用层() {
        // 纯函数只做夹取：-1 落到 0。EditText 失焦时 selection 是 -1，由 insertAtCursor 先换成
        // 「末尾下标」再进来，所以用户看到的是追加在结尾，见 insertAtCursor 的注释
        val result = insert("abcd", -1, -1, "\n")
        assertEquals("\nabcd", result.text)
        assertEquals(1, result.cursor)
    }

    @Test
    fun 越界索引被夹到合法范围() {
        assertEquals("abcd\n", insert("abcd", 99, 99, "\n").text)
        assertEquals("\nabcd", insert("abcd", -99, -99, "\n").text)
        assertEquals("a\n", insert("abcd", 1, 99, "\n").text)
    }
}
