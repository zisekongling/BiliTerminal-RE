package com.RobinNotBad.BiliClient.activity

import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.ImageRequestPolicy
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * 看图页加载失败提示的文案选择（反馈 #282：赞赏收款码一直空白，连一句提示都没有）。
 *
 * 锁两件事：**站内 / 站外两种失败的措辞必须分开**（站外图多半是对方限流，说「检查网络」会带偏），
 * 以及**判据真的接在 [ImageRequestPolicy] 上**——gitee 上的赞赏收款码必须被判成站外图源。
 */
class ImageViewerFailureTextTest {

    @Before
    fun setUp() {
        // shouldAttachSession 会触发 NetWorkUtil 类初始化（静态 webHeaders 读 SharedPreferences），先注入假实现。
        SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    @Test
    fun `站外图源用可能限流的那条文案`() {
        assertEquals(
            "站外图挂了多半不是用户网络问题，措辞要指向图源",
            R.string.text_image_viewer_load_failed_external,
            ImageViewerFailureText.resFor(external = true)
        )
    }

    @Test
    fun `站内图源用普通的加载失败文案`() {
        assertEquals(
            "B 站图床失败基本就是自己这边网络，给通用的「点一下重试」",
            R.string.text_image_viewer_load_failed,
            ImageViewerFailureText.resFor(external = false)
        )
    }

    @Test
    fun `两种情况的文案不能是同一条`() {
        assertNotEquals(
            "两条文案必须不同，否则上面两个断言会失去意义",
            ImageViewerFailureText.resFor(external = true),
            ImageViewerFailureText.resFor(external = false)
        )
    }

    @Test
    fun `gitee 上的赞赏收款码被认成站外图源`() {
        val donateUrl = "https://gitee.com/zisekongling/bili-terminal-re/raw/master/ma.jpg"
        assertEquals(
            "赞赏码是站外图（gitee 直链），失败提示必须走站外那条",
            R.string.text_image_viewer_load_failed_external,
            ImageViewerFailureText.resFor(!ImageRequestPolicy.shouldAttachSession(donateUrl))
        )
    }

    @Test
    fun `B 站图床图片被认成站内图源`() {
        val biliUrl = "https://i0.hdslb.com/bfs/archive/cover.jpg"
        assertEquals(
            "B 站图床走通用文案",
            R.string.text_image_viewer_load_failed,
            ImageViewerFailureText.resFor(!ImageRequestPolicy.shouldAttachSession(biliUrl))
        )
    }
}
