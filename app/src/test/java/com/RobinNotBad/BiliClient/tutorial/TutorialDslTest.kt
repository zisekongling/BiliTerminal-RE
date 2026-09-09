package com.RobinNotBad.BiliClient.tutorial

import android.app.Activity
import com.RobinNotBad.BiliClient.activity.article.OpusInfoActivity
import com.RobinNotBad.BiliClient.activity.video.RecommendActivity
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教程 DSL 与注册表的纯 JVM 单测。
 *
 * 覆盖两点：
 * 1. DSL 产出的数据结构正确（旧 XML 那套 `isInXxx` 布尔解析已被替换，这里是它的回归网）；
 * 2. 注册表自洽（id 唯一、GUIDE 必须有正文、同页顺序稳定）。
 */
class TutorialDslTest {

    private fun sample() = tutorial(
        id = "sample",
        title = "示例",
        version = 1,
        target = Activity::class.java,
        kind = TutorialKind.GUIDE,
    ) {
        page {
            text("普通").color("#123456")
            newline()
            text("加粗").bold()
            image(1)
        }
        page {
            text("第二页")
        }
    }

    @Test
    fun dsl_buildsMultiplePages() {
        val tutorial = sample()

        assertEquals("应有两页", 2, tutorial.pages.size)
        assertEquals("页数应与内容一致", 2, tutorial.pageCount)
    }

    @Test
    fun dsl_appliesStyleAndColorToCorrectSpan() {
        val spans = sample().pages[0].spans

        assertEquals("普通", spans[0].text)
        assertEquals("#123456", spans[0].color)
        assertEquals(TutorialStyle.NORMAL, spans[0].style)

        assertEquals("换行片段", "\n", spans[1].text)

        assertEquals("加粗", spans[2].text)
        assertEquals(TutorialStyle.BOLD, spans[2].style)

        assertNotNull("最后一个片段应是图片", spans[3].imageRes)
    }

    @Test
    fun dsl_withoutPageBlock_countsAsOnePage() {
        val tutorial = tutorial(
            id = "single",
            title = "单页",
            version = 1,
            target = Activity::class.java,
            kind = TutorialKind.NOTICE,
        ) {
            page { text("唯一一页") }
        }

        assertEquals(1, tutorial.pageCount)
    }

    @Test
    fun kind_flags_guideIsMandatory_noticeIsSkippable_hintIsNotFullScreen() {
        assertTrue("GUIDE 强制", sample().isMandatory)
        assertTrue("GUIDE 是全屏页", sample().isFullScreen)

        val notice = tutorial("n", "n", 1, Activity::class.java, TutorialKind.NOTICE)
        assertFalse("NOTICE 可跳过", notice.isMandatory)

        val hint = tutorial("h", "h", 1, Activity::class.java, TutorialKind.HINT)
        assertFalse("HINT 不是全屏页", hint.isFullScreen)
    }

    @Test
    fun registry_idsAreUniqueAndGuideHasContent() {
        val ids = Tutorials.all.map { it.id }
        assertEquals("id 不能重复", ids.size, ids.distinct().size)

        for (tutorial in Tutorials.all) {
            assertTrue("标题不能为空: ${tutorial.id}", tutorial.title.isNotEmpty())
            assertTrue("版本号应从 1 起: ${tutorial.id}", tutorial.version >= 1)
            if (tutorial.kind == TutorialKind.GUIDE) {
                assertTrue("GUIDE 必须有正文: ${tutorial.id}", tutorial.pages.isNotEmpty())
            }
        }
    }

    @Test
    fun registry_videoInfoPageHasSingleMergedTutorial() {
        val list = Tutorials.forPage(VideoInfoActivity::class.java)

        assertEquals("零碎问题已合并进同一篇，应只剩一篇", 1, list.size)
        assertEquals("video_main", list[0].id)
        assertEquals("合并后应为三页", 3, list[0].pageCount)
    }

    @Test
    fun registry_recommendPageHasTwoTutorialsInDeclarationOrder() {
        val list = Tutorials.forPage(RecommendActivity::class.java)

        assertEquals("推荐页有引导 + 主题说明两篇", 2, list.size)
        assertEquals("声明顺序即串行顺序", "recommend", list[0].id)
        assertEquals("theme_notice", list[1].id)
        assertTrue("引导类应强制", list[0].isMandatory)
        assertFalse("版本说明可跳过", list[1].isMandatory)
    }

    @Test
    fun registry_articleTutorialIsWiredToOpusInfoPage() {
        // 旧实现里 tutorial_article 是孤儿：内容写好了但全工程没有任何调用点。
        // 迁移后由注册表集中触发，这里锁住这个回归。
        val list = Tutorials.forPage(OpusInfoActivity::class.java)

        assertEquals(1, list.size)
        assertEquals("article", list[0].id)
        assertTrue("专栏教程应有正文", list[0].pages.isNotEmpty())
    }
}
