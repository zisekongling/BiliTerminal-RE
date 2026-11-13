package com.RobinNotBad.BiliClient.util.view

import android.widget.EditText

/**
 * 在光标处插入一段文本（换行、表情码……）。
 *
 * 为什么要有这个类：手表端输入法常把回车当「完成 / 下一项」，用户在评论、动态、私信里根本打不出换行，
 * 所以界面上给一张「换行」卡，点一下往光标处插一个 `\n`。而 `EditText.text.insert(...)` 有两个坑：
 *
 * - 插入后光标停在新插内容**前面**，连点两次换行会反向堆叠；
 * - 有选区时 `insert` 不是替换选中文字，而是插在选区起点之前，用户选了半句话点换行，选区还留着。
 *
 * 两个坑都收在这里：先按选区算好要替换的区间（选区反向、越界都归一到合法范围），拼出新文本，
 * 再把光标放到插入内容之后。核心逻辑是纯函数，JVM 单测直接钉住，不需要真机。
 */
object TextInsertion {

    /** 插入结果：新文本 + 插入后光标应处的位置。 */
    data class Inserted(val text: String, val cursor: Int)

    /**
     * 把 [insert] 插进 [source] 的 [start]..[end] 区间（有选区则替换选区）。
     *
     * [start]/[end] 允许反向（选区从右往左拉）和越界（EditText 失去焦点时 selection 会是 -1），
     * 一律归一到 `0..length` 再算，不会抛异常。
     */
    fun insert(source: String, start: Int, end: Int, insert: String): Inserted {
        val length = source.length
        val from = start.coerceIn(0, length)
        val to = end.coerceIn(0, length)
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        return Inserted(source.substring(0, lo) + insert + source.substring(hi), lo + insert.length)
    }

    /** 在 [editText] 光标处插入 [insert]；结果整体写回，顺带把光标放到插入内容之后。 */
    fun insertAtCursor(editText: EditText, insert: String) {
        val editable = editText.text ?: return
        val source = editable.toString()
        // 没有焦点时 selectionStart/End 是 -1，当作「插到末尾」，不然 coerce 之后会插到开头
        val start = if (editText.selectionStart < 0) source.length else editText.selectionStart
        val end = if (editText.selectionEnd < 0) source.length else editText.selectionEnd
        val result = insert(source, start, end, insert)
        // 整段写回：这几个输入框都是纯文本，没有需要保留的 span；比逐段 delete/insert 少一次状态不一致
        editable.replace(0, editable.length, result.text)
        editText.setSelection(result.cursor.coerceIn(0, result.text.length))
        if (!editText.hasFocus()) editText.requestFocus()
    }

    /** 「换行」卡专用：往光标处插一个换行。 */
    fun insertNewline(editText: EditText) = insertAtCursor(editText, "\n")
}
