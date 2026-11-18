package com.RobinNotBad.BiliClient.util

import com.RobinNotBad.BiliClient.model.Emote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表情取图策略的纯逻辑（26.11.18 线上崩溃「Cannot recycle a resource while it is still acquired」）。
 *
 * [EmoteUtil] 里真正碰 Glide 的三步（提交 / 取结果 / clear）无法在 JVM 上测，但它们之间只有一条判据：
 * **先决定这条文本要给哪几张图提交请求**——多提交一次就是多一次 acquire，少提交一次就是表情不显示。
 * 这段判读被抽成 [EmoteUtil.presentEmotes] / [EmoteUtil.distinctUrls] / [EmoteUtil.distinctEmoteUrls]
 * 三个不依赖 android.* 的纯函数，本测试盯的就是它们。
 */
class EmoteAcquirePolicyTest {

    private fun emote(name: String?, url: String?, size: Int = 1) = Emote(name, url, size)

    /** 故意塞 null 的列表：接口给的 emote 数组解析失败时真会给 null 条目，三个纯函数都必须容忍。 */
    @Suppress("UNCHECKED_CAST")
    private fun withNulls(vararg items: Emote?): List<Emote> = items.toList() as List<Emote>

    @Test
    fun presentEmotes_只保留文本里真的出现的表情并跳过空项() {
        val doge = emote("[doge]", "https://i0.hdslb.com/doge.png")
        val emotes = withNulls(
            doge,
            emote("[笑哭]", "https://i0.hdslb.com/xk.png"),   // 文本里没有
            null,                                            // 空条目
            emote("", "https://i0.hdslb.com/empty-name.png"),
            emote("[崩坏]", ""),                              // 没有 url
            emote(null, "https://i0.hdslb.com/null-name.png")
        )

        val present = EmoteUtil.presentEmotes("前排[doge]围观", emotes)

        assertEquals(1, present.size)
        assertSame(doge, present[0])
    }

    @Test
    fun presentEmotes_保持表情表顺序() {
        val a = emote("[a]", "https://i0.hdslb.com/a.png")
        val b = emote("[b]", "https://i0.hdslb.com/b.png")

        val present = EmoteUtil.presentEmotes("[b]然后[a]", listOf(a, b))

        assertEquals(listOf(a, b), present)
    }

    @Test
    fun presentEmotes_空文本或空表情表返回空() {
        assertTrue(EmoteUtil.presentEmotes("[doge]", null).isEmpty())
        assertTrue(EmoteUtil.presentEmotes("纯文字", emptyList()).isEmpty())
        assertTrue(EmoteUtil.presentEmotes(null, listOf(emote("[doge]", "u"))).isEmpty())
    }

    @Test
    fun presentEmotes_不修改传入的表情表() {
        val emotes = mutableListOf(emote("[doge]", "https://i0.hdslb.com/doge.png"))
        EmoteUtil.presentEmotes("[doge]", emotes)
        assertEquals(1, emotes.size)
    }

    @Test
    fun distinctUrls_按出现顺序去重并跳过空值() {
        @Suppress("UNCHECKED_CAST")
        val urls = listOf("https://a.png", "https://b.png", "https://a.png", null, "", "https://b.png") as List<String>
        assertEquals(listOf("https://a.png", "https://b.png"), EmoteUtil.distinctUrls(urls))
        assertTrue(EmoteUtil.distinctUrls(null).isEmpty())
    }

    @Test
    fun distinctEmoteUrls_同一张图被多个表情条目引用时只提交一次() {
        // 服务端偶尔把同一个 url 挂在不同表情名下；一条文本里同时出现时不该为它 acquire 两次
        val emotes = listOf(
            emote("[doge]", "https://i0.hdslb.com/doge.png"),
            emote("[狗头]", "https://i0.hdslb.com/doge.png"),
            emote("[笑哭]", "https://i0.hdslb.com/xk.png")
        )

        assertEquals(
            listOf("https://i0.hdslb.com/doge.png", "https://i0.hdslb.com/xk.png"),
            EmoteUtil.distinctEmoteUrls(emotes)
        )
    }

    @Test
    fun distinctEmoteUrls_容忍空条目() {
        assertTrue(EmoteUtil.distinctEmoteUrls(null).isEmpty())
        assertTrue(EmoteUtil.distinctEmoteUrls(withNulls(null, emote(null, null), emote("x", ""))).isEmpty())
    }
}
