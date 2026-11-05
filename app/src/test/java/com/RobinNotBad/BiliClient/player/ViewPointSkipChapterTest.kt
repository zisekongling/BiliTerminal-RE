package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.player.ViewPointSkip.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ViewPointSkip] 的「分段 vs 片头片尾」单测（反馈 #99 / #34）。
 *
 * **根因**：`view_points` 里的 `type` 是**分段类型**，不是片头片尾类型。UGC 稿件（反馈 #99 的
 * `BV1U74w6XEbB`）下发的是 UP 主划的视频章节，`type` 恒为 2（= [ViewPointSkip.TYPE_ED]），
 * `content` 才是章节标题（「狂躁损伤」「泄漏源」……）。旧实现只看 type，于是把 10 个章节全当片尾，
 * 250ms 定时器一路跳，用户看到的就是「一直在跳过片尾」。
 *
 * **修复口径（本文件钉住的就是它）——一道闸**：
 * - [ViewPointSkip.buildSegments] 的**单参版本保持旧口径**：只按 type + 区间 + 时长上限筛，
 *   **不看 content**。`content` 由 UP 主/字幕组自由填写（番剧里常见空串、「1-1」「正片」），
 *   拿它当闸会把真片头也挡掉。所以它**确实**会把 #99 的 10 段当片尾放进来——本文件正面断言
 *   这一点，证明「buildSegments 不背这个锅」。
 * - 真正拦 #99 的是 [ViewPointSkip.buildSegments] 的 `allowAutoSkip` 重载：`false → 空表`。
 *   谁来传 false（`PlayerActivity.isPgcPlayback()` 判定 UGC）属于播放页逻辑，纯 JVM 测不到。
 * - [ViewPointSkip.isOpEdLabel] 是**诊断语义**，不参与过滤；既然公开了，单独钉它的命中/不命中。
 */
class ViewPointSkipChapterTest {

    /**
     * 反馈 #99 的**原始样本**：`x/player/wbi/v2` 对 BV1U74w6XEbB
     * （aid=117546476966330, cid=43264904750, 总长 129s）返回的 view_points，逐字照抄。
     */
    private fun 反馈99原始分段() = listOf(
        Segment(ViewPointSkip.TYPE_ED, 0.0, 7.0, "狂躁损伤"),
        Segment(ViewPointSkip.TYPE_ED, 7.0, 13.0, "泄漏源"),
        Segment(ViewPointSkip.TYPE_ED, 13.0, 19.0, "留声机"),
        Segment(ViewPointSkip.TYPE_ED, 19.0, 34.0, "舞者"),
        Segment(ViewPointSkip.TYPE_ED, 34.0, 42.0, "火炉"),
        Segment(ViewPointSkip.TYPE_ED, 42.0, 48.0, "兔子"),
        Segment(ViewPointSkip.TYPE_ED, 48.0, 65.0, "浮士德"),
        Segment(ViewPointSkip.TYPE_ED, 65.0, 73.0, "毒雾"),
        Segment(ViewPointSkip.TYPE_ED, 73.0, 82.0, "源石地板"),
        Segment(ViewPointSkip.TYPE_ED, 82.0, 129.0, "隐匿术士，黑蛇，反伤怪，飞机")
    )

    // ================= 1) #99 回归钉子 =================

    @Test
    fun 反馈99样本单参buildSegments原样放行十段() {
        val raw = 反馈99原始分段()
        assertEquals("样本本身必须是 10 段，别让测试数据自己走样", 10, raw.size)
        assertEquals(
            "旧口径不看 content：这 10 段 type 都是 2、区间都合法，单参 buildSegments 会全部保留。" +
                "这正是 #99 的成因——闸不能加在这里，必须在上游按 PGC 判定拦住。",
            10,
            ViewPointSkip.buildSegments(raw).size
        )
    }

    @Test
    fun 反馈99样本在未放行时必须是空表() {
        assertTrue(
            "UGC 闸（allowAutoSkip=false）必须让 #99 的 10 段一条不剩——这是回归钉子",
            ViewPointSkip.buildSegments(反馈99原始分段(), allowAutoSkip = false).isEmpty()
        )
    }

    @Test
    fun 反馈99样本第7秒会命中泄漏源印证现象() {
        // 用注释写清这就是 #99 现象：0~7s 是「狂躁损伤」，7s 一到就命中「泄漏源」那段 type=2，
        // 如果片段表真的喂给了自动跳过，播放器会在 7s 处"跳片尾"——闸必须在上游拦住。
        val segments = ViewPointSkip.buildSegments(反馈99原始分段())
        val hit = ViewPointSkip.segmentAt(7.0, segments)
        assertEquals("7s 应落入「泄漏源」那段", "泄漏源", hit?.content)
        assertEquals("它的 type 是 2，会被误当片尾", ViewPointSkip.TYPE_ED, hit?.type)
        assertEquals(7.0, hit?.fromSec ?: -1.0, 0.0)
        assertEquals(13.0, hit?.toSec ?: -1.0, 0.0)
    }

    @Test
    fun 反馈99样本未放行时整集任意一秒都不跳() {
        // 端到端钉住用户看到的现象：闸关上后，整集 0~129 秒里任何位置都不能被判成"该跳"，
        // 粒度与播放页 250ms 定时器一致。
        val segments = ViewPointSkip.buildSegments(反馈99原始分段(), allowAutoSkip = false)
        var t = 0.0
        while (t < 129.0) {
            assertNull("第 ${t}s 不该自动跳过（反馈 #99：一直在跳片尾）", ViewPointSkip.segmentAt(t, segments))
            t += 0.25
        }
    }

    @Test
    fun 反馈99样本走UGC闸后已处理集合也拿不到任何key() {
        val segments = ViewPointSkip.buildSegments(反馈99原始分段(), allowAutoSkip = false)
        assertNull(ViewPointSkip.keyForManualSeek(7.0, segments, emptySet()))
        assertNull(ViewPointSkip.shouldSkip(7.0, segments, emptySet()))
    }

    // ================= 2) allowAutoSkip=true 与单参版一致 =================

    @Test
    fun 放行时与单参版本完全一致() {
        val raw = 反馈99原始分段() + listOf(
            Segment(ViewPointSkip.TYPE_OP, 0.0, 30.0, "片头"),
            Segment(ViewPointSkip.TYPE_ED, 120.0, 129.0, "片尾")
        )
        assertEquals(
            "allowAutoSkip=true 只是「放过」，不能改变取样口径",
            ViewPointSkip.buildSegments(raw),
            ViewPointSkip.buildSegments(raw, allowAutoSkip = true)
        )
    }

    @Test
    fun 放行时只保留type一或二且区间合法的段() {
        val raw = listOf(
            Segment(0, 100.0, 200.0, "普通看点"),                        // type=0 丢弃
            Segment(3, 300.0, 400.0, "预留类型"),                        // type=3 丢弃
            Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "片头"),          // 保留
            Segment(ViewPointSkip.TYPE_ED, 95.0, 95.0, "空区间"),        // 丢弃
            Segment(ViewPointSkip.TYPE_ED, 200.0, 100.0, "反向区间"),    // 丢弃
            Segment(ViewPointSkip.TYPE_OP, 0.0, 601.0, "超长脏数据"),    // 丢弃
            Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0, "片尾")       // 保留
        )
        assertEquals(
            "放行时口径 = 旧口径：type ∈ {1,2} + 区间正向 + 时长 ≤ MAX_SEGMENT_SECONDS，不看 content",
            listOf(
                Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "片头"),
                Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0, "片尾")
            ),
            ViewPointSkip.buildSegments(raw, allowAutoSkip = true)
        )
    }

    @Test
    fun 放行时空content的段也照样保留() {
        // 这条专门正面钉住"不看 content"：番剧片段 content 常常是空串或「1-1」，
        // 如果哪天有人把白名单接回 buildSegments，这条会立刻红。
        val raw = listOf(
            Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0),
            Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0, "1-1")
        )
        assertEquals(
            "content 为空或是「1-1」时必须照样保留，否则真片头片尾会被挡掉（用户反而觉得开了没用）",
            2,
            ViewPointSkip.buildSegments(raw, allowAutoSkip = true).size
        )
    }

    @Test
    fun 不许放行时真片头片尾也是空表() {
        val raw = listOf(
            Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "片头"),
            Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0, "片尾")
        )
        assertTrue(
            "UGC 闸是硬否决：闸关上时不存在任何绕过路径，哪怕片段看起来再像片头片尾",
            ViewPointSkip.buildSegments(raw, allowAutoSkip = false).isEmpty()
        )
        assertTrue(
            "空输入必定空表（放行与否都一样）",
            ViewPointSkip.buildSegments(emptyList(), allowAutoSkip = true).isEmpty() &&
                ViewPointSkip.buildSegments(emptyList(), allowAutoSkip = false).isEmpty() &&
                ViewPointSkip.buildSegments(emptyList()).isEmpty()
        )
    }

    @Test
    fun 放行时按起点排序() {
        val raw = listOf(
            Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0, "片尾"),
            Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "片头")
        )
        assertEquals(
            "segmentAt 依赖「第一个命中」，必须按起点升序",
            listOf(15.0, 1380.0),
            ViewPointSkip.buildSegments(raw, allowAutoSkip = true).map { it.fromSec }
        )
    }

    // ================= 3) 真片头片尾保留 + 命中 + key 稳定 =================

    @Test
    fun 真片头片尾必须保留且命中正确() {
        val raw = listOf(
            Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "片头"),
            Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0, "片尾")
        )
        val segments = ViewPointSkip.buildSegments(raw, allowAutoSkip = true)
        assertEquals("真片头片尾一条都不能丢", 2, segments.size)

        val op = ViewPointSkip.segmentAt(15.0, segments)
        val ed = ViewPointSkip.segmentAt(1400.0, segments)
        assertEquals("起点算在片头内", Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "片头"), op)
        assertEquals("片尾中间命中", Segment(ViewPointSkip.TYPE_ED, 1380.0, 1440.0, "片尾"), ed)
        assertNull("片头终点右开，95.0 不算在片头内", ViewPointSkip.segmentAt(95.0, segments))

        // 分类正确：两者 type 不同、key 不串，撤回提示文案靠 type 区分「片头/片尾」
        assertEquals(ViewPointSkip.TYPE_OP, op?.type)
        assertEquals(ViewPointSkip.TYPE_ED, ed?.type)
        assertFalse("片头和片尾的 key 不能相同", ViewPointSkip.keyOf(op!!) == ViewPointSkip.keyOf(ed!!))
    }

    @Test
    fun 真片头片尾的key稳定可重复计算() {
        val first = ViewPointSkip.buildSegments(
            listOf(Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "片头")), allowAutoSkip = true
        )
        val again = ViewPointSkip.buildSegments(
            listOf(Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "片头")), allowAutoSkip = true
        )
        assertEquals(
            "同一次播放里重复拉取必须得到同一个 key，否则已处理集合失效、片头会被再跳一次",
            ViewPointSkip.keyOf(first.first()),
            ViewPointSkip.keyOf(again.first())
        )
        assertEquals("1@15.0-95.0", ViewPointSkip.keyOf(first.first()))
    }

    // ================= 4) isOpEdLabel：诊断语义（不参与过滤） =================

    @Test
    fun 片头片尾的中英文写法都能识别() {
        listOf("片头", "片尾", "OP", "op", "Opening", "opening", "ED", "ed", "Ending", "ending", "主题曲")
            .forEach { title ->
                assertTrue("「$title」应被 isOpEdLabel 认成片头/片尾语义", ViewPointSkip.isOpEdLabel(title))
            }
    }

    @Test
    fun 长词与派生写法也能识别() {
        // 标题里夹空格/标点要能容忍：s（保留分隔）与 compact（去非字母数字）都参与中文词判定。
        // 「片 尾」「OP 主题曲」曾经不命中——KDoc 第 1 步承诺过能识别「片 尾」，实现漏了，已补上。
        listOf(
            "片头曲", "片尾曲", "OP主题曲", "O.P.", "OP!", " the op song ", "前情提要",
            "片 尾", "片\u3000尾", "OP 主题曲", "片头 Opening"
        ).forEach { title ->
            assertTrue("「$title」应归一后命中", ViewPointSkip.isOpEdLabel(title))
        }
    }

    @Test
    fun 短词必须是独立单词才命中() {
        assertTrue("op 独立成词应命中", ViewPointSkip.isOpEdLabel("the op song"))
        assertTrue("ed 带括号说明应命中", ViewPointSkip.isOpEdLabel("ED (ending)"))
        assertFalse("两个字母粘在别的词里不算：hop", ViewPointSkip.isOpEdLabel("hop"))
        assertFalse("两个字母粘在别的词里不算：bed", ViewPointSkip.isOpEdLabel("bed"))
        assertFalse("opens 不是独立单词 op", ViewPointSkip.isOpEdLabel("opens"))
    }

    @Test
    fun 常见误伤标题一律不命中() {
        // 短子串误伤是这块最容易翻车的地方：op/ed 只有两个字母
        listOf(
            "Stop", "Speed", "Episode 3", "作业", "前排",
            "opera", "credit", "spread", "reed", "shopping",
            "开场", "正片", "第一部分", "BOSS战", "结尾", "谢谢观看",
            "狂躁损伤", "泄漏源", "留声机", "舞者", "火炉", "兔子", "浮士德", "毒雾",
            "源石地板", "隐匿术士，黑蛇，反伤怪，飞机"
        ).forEach { title ->
            assertFalse("「$title」只是普通章节/单词，绝不能被判成片头片尾", ViewPointSkip.isOpEdLabel(title))
        }
    }

    @Test
    fun 空与纯标点不命中() {
        listOf("", "   ", "---", "。。", "！？", " / ", "\t\u3000").forEach { title ->
            assertFalse("「$title」没有任何可辨语义", ViewPointSkip.isOpEdLabel(title))
        }
    }

    @Test
    fun 诊断判定不影响过滤结果() {
        // 这条钉住「isOpEdLabel 只诊断、不参与过滤」这个口径本身：
        // 一个 content 明显写着「OP」的段和一个空 content 的段，放行后都必须保留下来。
        // 注意 Segment 是 data class，相等性包含 content——所以只能比"保留下来的条数"，
        // 不能整体 assertEquals（那样比的是数据本身，跟过滤口径无关）。
        val labelled = Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0, "OP")
        val unlabelled = Segment(ViewPointSkip.TYPE_OP, 15.0, 95.0)
        assertTrue("「OP」命中诊断词表", ViewPointSkip.isOpEdLabel(labelled.content))
        assertFalse("空串不命中诊断词表", ViewPointSkip.isOpEdLabel(unlabelled.content))
        assertEquals(
            "内容有没有片头片尾词，放行后都保留 1 条——过滤与 content 无关",
            1,
            ViewPointSkip.buildSegments(listOf(labelled), allowAutoSkip = true).size
        )
        assertEquals(
            "内容有没有片头片尾词，放行后都保留 1 条——过滤与 content 无关",
            1,
            ViewPointSkip.buildSegments(listOf(unlabelled), allowAutoSkip = true).size
        )
    }
}
