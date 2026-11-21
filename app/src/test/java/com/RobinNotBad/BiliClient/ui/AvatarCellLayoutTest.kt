package com.RobinNotBad.BiliClient.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 「动态页作者旁有灰边」防回归（26.11.10 第三次修）。
 *
 * 用户反复报的那条灰边不是阴影，是**卡片填充色**：cell_up_avatar 原来用 `MaterialCardView`
 * 加 `app:cardCornerRadius="28dp"` 仅为了把方头像裁圆，而
 * `app/src/main/java/com/RobinNotBad/BiliClient/ui/appearance/AppearanceApplier.kt:108-117`
 * 的 `applyRadius()` 会在圆角档为 ROUNDED 时把**全树所有卡片**的 radius 改成
 * `card_round_large`(12dp) / `card_round`(6dp)，28dp 圆角被覆盖成圆角方卡，
 * 卡片自己的 `cardBackgroundColor`（`@color/card_dark` 或玻璃半透明色）就在圆头像四角露出。
 * f843aa21（压 elevation）、7bf18489（改关注列表）都没碰这层填充色，所以都没修好。
 *
 * 守的是「结构」而不是像素：这个格子一旦重新引进卡片控件，灰边就会回来。
 * 纯 JVM 单测，Gradle 单测的工作目录是 `app/`，直接读 `src/main/res`。
 */
class AvatarCellLayoutTest {

    private val layout = File("src/main/res/layout/cell_up_avatar.xml")

    private fun root(): Element {
        assertTrue("找不到 ${layout.path}", layout.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layout)
        return doc.documentElement
    }

    private fun all(root: Element): List<Element> {
        val out = mutableListOf<Element>()
        fun walk(e: Element) {
            out += e
            val kids = e.childNodes
            for (i in 0 until kids.length) {
                val n = kids.item(i)
                if (n is Element) walk(n)
            }
        }
        walk(root)
        return out
    }

    /** 圆头像格子不能有任何 CardView：卡片的底色 / 描边 / 圆角都不受本布局控制。 */
    @Test
    fun upAvatarCellHasNoCardView() {
        val cards = all(root()).filter { e ->
            val n = e.tagName
            n.endsWith("MaterialCardView") || n.endsWith(".CardView")
        }
        assertTrue(
            "cell_up_avatar.xml 不允许出现 CardView（含 MaterialCardView）：$cards。" +
                " 卡片会继承主题卡底色，且 AppearanceApplier 会把它的圆角改成 6/12dp，" +
                " 圆头像四角就会露出卡底色（用户报的「作者旁有灰边」）。",
            cards.isEmpty(),
        )
    }

    /** 圆形必须由「oval 轮廓 + clipToOutline」提供，且轮廓源本身不着色。 */
    @Test
    fun avatarCircleComesFromOutlineClip() {
        val frame = all(root()).firstOrNull { it.tagName.endsWith("FrameLayout") }
        assertNotNull("头像容器应为 FrameLayout（圆轮廓裁剪）", frame)
        frame!!
        assertEquals("true", frame.getAttribute("android:clipToOutline"))
        assertEquals("background", frame.getAttribute("android:outlineProvider"))
        assertEquals("@drawable/bg_avatar_circle", frame.getAttribute("android:background"))
        assertEquals("56dp", frame.getAttribute("android:layout_width"))
        assertEquals("56dp", frame.getAttribute("android:layout_height"))

        // 轮廓源必须全透明：上色就等于给头像加了一圈「边」。
        val bg = File("src/main/res/drawable/bg_avatar_circle.xml")
        assertTrue("缺少 drawable/bg_avatar_circle.xml", bg.isFile)
        val text = bg.readText()
        assertTrue("bg_avatar_circle 应为 oval", text.contains("android:shape=\"oval\""))
        assertTrue(
            "bg_avatar_circle 必须全透明（只提供轮廓）",
            text.contains("@android:color/transparent"),
        )
    }

    /** RecentUpAdapter 靠这三个 id 绑定，改名要同步改 adapter。 */
    @Test
    fun adapterBindingIdsStillPresent() {
        val ids = all(root())
            .map { it.getAttribute("android:id") }
            .filter { it.isNotEmpty() }
            .toSet()
        assertEquals(
            setOf("@+id/avatar", "@+id/updateIndicator", "@+id/name"),
            ids,
        )
    }
}
