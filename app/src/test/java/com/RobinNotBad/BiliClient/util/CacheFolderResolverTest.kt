package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.VideoFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住 [CacheFolderResolver]：反馈 #254 的现象是「缓存页文件夹上写着 N 个视频，点进去一条都没有」。
 *
 * 根因不在扫描，而在归属存了两份、判据只认其中一份：夹内页按 `.video_meta.json` 的 `folderName`
 * 过滤，夹卡数字 / 封面 / 夹页白名单却来自 `.folders_info.json` 的 `videoTitles`。
 * 这里锁的是把两边统一成「记录优先、元数据兜底」之后的结果，以及什么时候该写回盘面（自愈）。
 *
 * 全部是字符串层面断言（不碰文件系统），能在纯 JVM 单测里跑。
 */
class CacheFolderResolverTest {

    private fun folder(name: String, vararg titles: String): VideoFolder {
        val f = VideoFolder(name)
        for (t in titles) f.addVideo(t)
        return f
    }

    // ---------- 索引 ----------

    @Test
    fun `索引按清洗后的标题建键`() {
        // 夹记录里存的是原始标题（这里是全角冒号 + 半角斜杠），磁盘目录名是清洗过的
        val raw = "第一集：P1/P2"
        val index = CacheFolderResolver.titleIndex(listOf(folder("追番", raw)))

        assertEquals("追番", index[FileUtil.stringToFile(raw)])
        // 清洗过的标题再清洗一次不变，所以磁盘目录名（已是清洗后的那份）用它去查也命中
        assertEquals("追番", index[FileUtil.stringToFile(FileUtil.stringToFile(raw))])
        // 原始标题带着半角斜杠，跟磁盘目录名不是同一串，查不到是预期的——调用方必须传归一后的名字
        assertFalse(index.containsKey(raw))
    }

    @Test
    fun `索引跳过空夹名与空标题`() {
        val index = CacheFolderResolver.titleIndex(listOf(folder("", "abc"), folder("空夹")))

        assertTrue(index.isEmpty())
    }

    @Test
    fun `同一标题写在两个夹里取先出现的那个`() {
        // 老数据 / 手改 json 才会出现；正常操作路径会先把旧夹里的摘掉
        val index = CacheFolderResolver.titleIndex(listOf(folder("甲", "视频"), folder("乙", "视频")))

        assertEquals("甲", index["视频"])
    }

    // ---------- 解析口径 ----------

    @Test
    fun `两边一致时照旧`() {
        assertEquals(
            "追番",
            CacheFolderResolver.resolve("追番", "追番", setOf("追番", "杂项"))
        )
    }

    @Test
    fun `记录有元数据空时按记录算`() {
        // 反馈 #254 的典型盘面：老版本只往 .folders_info.json 写过归属
        assertEquals(
            "追番",
            CacheFolderResolver.resolve("追番", "", setOf("追番"))
        )
    }

    @Test
    fun `记录空元数据有时按元数据兜底`() {
        assertEquals(
            "追番",
            CacheFolderResolver.resolve("", "追番", setOf("追番"))
        )
    }

    @Test
    fun `两边不一致时记录优先`() {
        // 改夹名时目录没找到、元数据停在旧夹名——卡片数字按记录算，就得按记录显示
        assertEquals(
            "新夹",
            CacheFolderResolver.resolve("新夹", "旧夹", setOf("新夹", "旧夹"))
        )
    }

    @Test
    fun `夹表里没有的夹名一律不认`() {
        // 夹被删了：认它等于这条视频在哪都看不见（顶层按空串筛未分类，夹页又打不开这个夹）
        assertEquals("", CacheFolderResolver.resolve("已删掉的夹", "", setOf("追番")))
        assertEquals("", CacheFolderResolver.resolve("", "已删掉的夹", setOf("追番")))
    }

    @Test
    fun `两边都空就是未分类`() {
        assertEquals("", CacheFolderResolver.resolve("", "", setOf("追番")))
    }

    // ---------- 自愈判据 ----------

    @Test
    fun `元数据与解析结果不一致才写回元数据`() {
        assertTrue(CacheFolderResolver.needsMetaHeal("追番", "", setOf("追番")))
        assertTrue(CacheFolderResolver.needsMetaHeal("追番", "旧夹", setOf("追番")))
        assertFalse(CacheFolderResolver.needsMetaHeal("追番", "追番", setOf("追番")))
        // 解析成未分类、元数据也本来就是空：无可写
        assertFalse(CacheFolderResolver.needsMetaHeal("", "", setOf("追番")))
    }

    @Test
    fun `夹表为空时绝不自愈元数据`() {
        // json 读坏了 / 被清过时 getAllFolders 返回空表，这时每条都会解析成空串；
        // 照着写会把所有视频的夹名抹平，等于毁掉最后一份记录
        assertFalse(CacheFolderResolver.needsMetaHeal("", "追番", emptySet()))
    }

    @Test
    fun `夹表合法为空时允许自愈元数据`() {
        // 用户把最后一个夹也拆了：.folders_info.json 里是一份合法的空数组，
        // 这时元数据仍留着旧夹名会让这条视频在「下次建同名夹」时自己跳进去，所以要写回
        assertTrue(CacheFolderResolver.needsMetaHeal("", "追番", emptySet(), true))
        assertFalse(CacheFolderResolver.needsMetaHeal("", "", emptySet(), true))
    }

    @Test
    fun `夹记录读不出来时仍然不自愈`() {
        // 与上一条对照：文件缺失 / 写坏了，就分不清「真的没有夹」还是「记录丢了」，
        // 宁可留着元数据里那一份，也不能把它抹平
        assertFalse(CacheFolderResolver.needsMetaHeal("", "追番", emptySet(), false))
    }

    @Test
    fun `记录里缺这条标题才补记录`() {
        assertTrue(CacheFolderResolver.needsRecordHeal("追番", ""))
        assertFalse(CacheFolderResolver.needsRecordHeal("追番", "追番"))
        assertFalse(CacheFolderResolver.needsRecordHeal("", ""))
    }
}
