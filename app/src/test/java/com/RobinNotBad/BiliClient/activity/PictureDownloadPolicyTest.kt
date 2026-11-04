package com.RobinNotBad.BiliClient.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片下载 URL / 文件名的净化规则（反馈 #17「评论区图片无法下载」）。
 *
 * 覆盖两种真实存在的 B 站图床地址后缀：`@1048w_!web-dyn`（处理参数）与
 * `?x-oss-process=…`（查询串）。不剥掉它们，文件名要么带非法字符创建失败，
 * 要么扩展名认不出来、`PictureMediaStore` 不登记 → 相册里永远看不到。
 */
class PictureDownloadPolicyTest {

    // ---------------- normalizeUrl ----------------

    @Test
    fun 协议相对地址补上https() {
        assertEquals(
            "https://i0.hdslb.com/bfs/x/a.jpg",
            PictureDownloadPolicy.normalizeUrl("//i0.hdslb.com/bfs/x/a.jpg")
        )
    }

    @Test
    fun 明文http统一升级为https() {
        assertEquals(
            "https://i0.hdslb.com/bfs/new_dyn/a.png",
            PictureDownloadPolicy.normalizeUrl("http://i0.hdslb.com/bfs/new_dyn/a.png")
        )
        // 大小写不敏感
        assertEquals(
            "https://i0.hdslb.com/x.jpg",
            PictureDownloadPolicy.normalizeUrl("HTTP://i0.hdslb.com/x.jpg")
        )
    }

    @Test
    fun 已是https或非网络地址保持原样() {
        assertEquals("https://i0.hdslb.com/x.jpg", PictureDownloadPolicy.normalizeUrl("https://i0.hdslb.com/x.jpg"))
        // content:// / file:// 不是网络地址，交给调用方按原样处理
        assertEquals(
            "content://media/external/images/1",
            PictureDownloadPolicy.normalizeUrl("content://media/external/images/1")
        )
    }

    @Test
    fun 空地址与首尾空白() {
        assertNull(PictureDownloadPolicy.normalizeUrl(null))
        assertNull(PictureDownloadPolicy.normalizeUrl(""))
        assertNull(PictureDownloadPolicy.normalizeUrl("   "))
        assertEquals(
            "https://i0.hdslb.com/x.jpg",
            PictureDownloadPolicy.normalizeUrl("  https://i0.hdslb.com/x.jpg \n")
        )
    }

    // ---------------- alternateSchemeUrl ----------------

    @Test
    fun 协议互换用于失败兜底() {
        assertEquals("http://i0.hdslb.com/x.jpg", PictureDownloadPolicy.alternateSchemeUrl("https://i0.hdslb.com/x.jpg"))
        assertEquals("https://i0.hdslb.com/x.jpg", PictureDownloadPolicy.alternateSchemeUrl("http://i0.hdslb.com/x.jpg"))
        assertNull(PictureDownloadPolicy.alternateSchemeUrl("content://media/external/images/1"))
        assertNull(PictureDownloadPolicy.alternateSchemeUrl(""))
    }

    // ---------------- fileNameFor ----------------

    @Test
    fun 普通图片地址取末段文件名() {
        assertEquals("8ad56400.png", PictureDownloadPolicy.fileNameFor("http://i0.hdslb.com/bfs/new_dyn/8ad56400.png"))
    }

    @Test
    fun 剥掉图床的at处理参数() {
        // 图片查看器请求大图时会拼 @0e_…w.webp，评论区接口回的原地址也可能已带 @…
        assertEquals("abc.jpg", PictureDownloadPolicy.fileNameFor("https://i0.hdslb.com/bfs/x/abc.jpg@1048w_!web-dyn"))
    }

    @Test
    fun 剥掉查询串与fragment() {
        assertEquals(
            "x.jpg",
            PictureDownloadPolicy.fileNameFor("https://i0.hdslb.com/bfs/face/x.jpg?x-oss-process=image/resize,w_100")
        )
        assertEquals("a.png", PictureDownloadPolicy.fileNameFor("https://h/a.png#frag"))
    }

    @Test
    fun 非法字符替换成下划线() {
        // `:` `*` 在部分文件系统上非法，createNewFile() 会直接抛 IOException
        assertEquals("a_b_c.png", PictureDownloadPolicy.fileNameFor("https://h/a:b*c.png"))
    }

    @Test
    fun 没有文件名或没有扩展名时有兜底() {
        assertEquals("image.jpg", PictureDownloadPolicy.fileNameFor("https://i0.hdslb.com/bfs/"))
        assertEquals("abc.jpg", PictureDownloadPolicy.fileNameFor("https://i0.hdslb.com/bfs/abc"))
        assertEquals("image.jpg", PictureDownloadPolicy.fileNameFor(""))
        assertEquals("image.jpg", PictureDownloadPolicy.fileNameFor(null))
    }

    @Test
    fun 纯点号文件名有兜底() {
        // "." / ".." 在任何文件系统上都不是合法文件名
        assertEquals("image.jpg", PictureDownloadPolicy.fileNameFor("https://h/."))
        assertEquals("image.jpg", PictureDownloadPolicy.fileNameFor("https://h/.."))
    }

    @Test
    fun 百分号编码原样保留() {
        // 百分号在文件名里合法；这里不做 URL 解码，避免解出新的非法字符
        assertEquals("a%20b.jpg", PictureDownloadPolicy.fileNameFor("https://h/a%20b.jpg"))
    }

    @Test
    fun 超长文件名截断但保留扩展名() {
        val longName = "a".repeat(200) + ".webp"
        val name = PictureDownloadPolicy.fileNameFor("https://h/$longName")
        assertTrue("长度应被截断：$name", name.length <= 80)
        assertTrue("扩展名必须保留：$name", name.endsWith(".webp"))
    }
}
