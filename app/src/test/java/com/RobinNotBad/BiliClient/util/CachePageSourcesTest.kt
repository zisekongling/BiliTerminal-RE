package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.LocalVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住 [CachePageSources] 的「合集按页合并 / 按根分组删除」。
 *
 * 背景（反馈 #217 / #214）：番剧与合集落盘是「季名父目录 + 集名子目录」，改过缓存位置之后
 * 同一季的集一半在新根、一半留在旧根。按标题整条去重会让用户只看到本机那几集；合并之后
 * 又必须记住每页在哪个根，否则删除 / 写弹幕 / 写续播会作用到别人分支的目录上。
 */
class CachePageSourcesTest {

    private val localBase = "/storage/emulated/0/Android/media/com.RobinNotBad.BiliClient/Download"
    private val externalBase = "/storage/emulated/0/Android/media/com.RobinNotBad.BiliClient.re/Download"

    /** 本机那条：只缓存了 6、7 两集。 */
    private fun localSeason(): LocalVideo = LocalVideo().apply {
        title = "UBW 第一季"
        folderName = ""
        pageList = arrayListOf("第6话", "第7话")
        videoFileList = arrayListOf(
            "$localBase/UBW 第一季/第6话/video.mp4",
            "$localBase/UBW 第一季/第7话/video.mp4"
        )
        danmakuFileList = arrayListOf(
            "$localBase/UBW 第一季/第6话/danmaku.xml",
            "$localBase/UBW 第一季/第7话/danmaku.xml"
        )
        sizeList = arrayListOf(100L, 200L)
        qualityList = arrayListOf("1080P", "1080P")
        pageRootList = arrayListOf(CachePageSources.LOCAL_ROOT, CachePageSources.LOCAL_ROOT)
        calcTotalSize()
    }

    /** 外部那条：8、9、10 三集留在旧根上。 */
    private fun externalSeason(): LocalVideo = LocalVideo().apply {
        title = "UBW 第一季"
        this.externalRoot = externalBase
        pageList = arrayListOf("第8话", "第9话", "第10话")
        videoFileList = arrayListOf(
            "$externalBase/UBW 第一季/第8话/video.mp4",
            "$externalBase/UBW 第一季/第9话/video.mp4",
            "$externalBase/UBW 第一季/第10话/audio.m4a"
        )
        danmakuFileList = arrayListOf(
            "$externalBase/UBW 第一季/第8话/danmaku.xml",
            "$externalBase/UBW 第一季/第9话/danmaku.xml",
            "$externalBase/UBW 第一季/第10话/danmaku.xml"
        )
        sizeList = arrayListOf(300L, 400L, 500L)
        qualityList = arrayListOf("1080P", "1080P", "仅音频")
        pageRootList = arrayListOf(externalBase, externalBase, externalBase)
        calcTotalSize()
    }

    @Test
    fun `外部合集按页接在本机合集后面`() {
        val target = localSeason()
        val added = CachePageSources.mergePages(target, externalSeason())

        assertEquals(3, added)
        assertEquals(listOf("第6话", "第7话", "第8话", "第9话", "第10话"), target.pageList)
        assertEquals(100L + 200L + 300L + 400L + 500L, target.size)
        // 每页一项的三条列表要与 pageList 对齐
        assertEquals(5, target.danmakuFileList!!.size)
        assertEquals(5, target.qualityList!!.size)
        assertEquals(5, target.sizeList!!.size)
        assertEquals(5, target.pageRootList!!.size)
        // 页根：前两页本机，后三页那个外部根
        assertEquals(CachePageSources.LOCAL_ROOT, target.pageRootList!![0])
        assertEquals(externalBase, target.pageRootList!![2])
        // videoFileList 按页展开，保留绝对路径（播放器直接吃）
        assertTrue(target.videoFileList!!.contains("$externalBase/UBW 第一季/第10话/audio.m4a"))
        assertEquals(5, target.videoFileList!!.size)
    }

    @Test
    fun `同名页不重复合并`() {
        val target = localSeason()
        val source = externalSeason().apply {
            pageList = arrayListOf("第7话", "第8话")
            videoFileList = arrayListOf(
                "$externalBase/UBW 第一季/第7话/video.mp4",
                "$externalBase/UBW 第一季/第8话/video.mp4"
            )
            pageRootList = arrayListOf(externalBase, externalBase)
        }
        val added = CachePageSources.mergePages(target, source)

        assertEquals(1, added)
        assertEquals(listOf("第6话", "第7话", "第8话"), target.pageList)
        // 第 7 话保留本机那份
        assertEquals("$localBase/UBW 第一季/第7话/video.mp4", target.videoFileList!![1])
    }

    @Test
    fun `单文件条目不参与合并`() {
        val target = LocalVideo().apply {
            title = "单集"
            pageList = arrayListOf()
            videoFileList = arrayListOf("$localBase/单集/video.mp4")
            danmakuFileList = arrayListOf("$localBase/单集/danmaku.xml")
            sizeList = arrayListOf(10L)
            qualityList = arrayListOf("720P")
        }
        assertEquals(0, CachePageSources.mergePages(target, externalSeason()))
        assertEquals(1, target.videoFileList!!.size)
    }

    @Test
    fun `双文件页合并时两个文件都进来`() {
        val target = localSeason().apply {
            pageList = arrayListOf("第6话")
            videoFileList = arrayListOf("$localBase/UBW 第一季/第6话/video.mp4")
            danmakuFileList = arrayListOf("$localBase/UBW 第一季/第6话/danmaku.xml")
            sizeList = arrayListOf(100L)
            qualityList = arrayListOf("1080P")
            pageRootList = arrayListOf(CachePageSources.LOCAL_ROOT)
        }
        val source = LocalVideo().apply {
            title = "UBW 第一季"
            this.externalRoot = externalBase
            pageList = arrayListOf("第7话")
            videoFileList = arrayListOf(
                "$externalBase/UBW 第一季/第7话/video.mp4",
                "$externalBase/UBW 第一季/第7话/audio.m4a"
            )
            danmakuFileList = arrayListOf("$externalBase/UBW 第一季/第7话/danmaku.xml")
            sizeList = arrayListOf(200L)
            qualityList = arrayListOf("1080P")
            pageRootList = arrayListOf(externalBase)
        }
        assertEquals(1, CachePageSources.mergePages(target, source))
        assertEquals(2, target.pageList!!.size)
        // 双文件页占两个下标，页名仍然只算一页
        assertEquals(3, target.videoFileList!!.size)
        assertTrue(target.videoFileList!!.contains("$externalBase/UBW 第一季/第7话/audio.m4a"))
    }

    @Test
    fun `删除按页根分组`() {
        val target = localSeason()
        CachePageSources.mergePages(target, externalSeason())

        val targets = CachePageSources.deleteTargets(target, localBase)
        assertEquals(2, targets.size)
        // 本机组：媒体文件反推出本机的季目录
        assertEquals(localBase, targets[0].rootPath)
        assertEquals("$localBase/UBW 第一季/第6话/video.mp4", targets[0].mediaFilePath)
        // 外部组：同一份删除逻辑要作用在外部根上，而不是拿本机根去套
        assertEquals(externalBase, targets[1].rootPath)
        assertEquals("$externalBase/UBW 第一季/第8话/video.mp4", targets[1].mediaFilePath)
    }

    @Test
    fun `没有页根的条目只有一条删除计划`() {
        val video = LocalVideo().apply {
            title = "老条目"
            videoFileList = arrayListOf("$localBase/老条目/video.mp4")
        }
        val targets = CachePageSources.deleteTargets(video, localBase)
        assertEquals(1, targets.size)
        assertEquals(localBase, targets[0].rootPath)
    }

    @Test
    fun `外部条目的页根就是它自己的根`() {
        val video = externalSeason()
        assertEquals(externalBase, CachePageSources.rootOfPage(video, 0))
        val targets = CachePageSources.deleteTargets(video, localBase)
        assertEquals(1, targets.size)
        assertEquals(externalBase, targets[0].rootPath)
    }
}
