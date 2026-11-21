package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大图分块解码的纯策略（[ImageTilePolicy]）。
 *
 * 这里钉三件事：**什么时候该分块**（阈值）、**该解哪些块**（可见区/预取区/对齐的块矩形）、
 * **内存兜底怎么降级**（采样率）。这三条算错了，前端要么白屏要么卡死，所以在 JVM 上先钉死。
 */
class ImageTilePolicyTest {

    // ---------- 阈值：什么时候走分块 ----------

    @Test
    fun 普通尺寸图片不走分块() {
        assertFalse(ImageTilePolicy.shouldTile(1920, 1080))
        assertFalse(ImageTilePolicy.shouldTile(1024, 1000))
    }

    @Test
    fun 单边超过四千零九十六走分块() {
        assertTrue(ImageTilePolicy.shouldTile(4097, 100))
        assertTrue(ImageTilePolicy.shouldTile(100, 4097))
    }

    @Test
    fun 面积超过一千六百万走分块() {
        // 4000×4000 恰好 16M，不超阈值 → 仍走整图
        assertFalse(ImageTilePolicy.shouldTile(4000, 4000))
        // 多 1 像素宽就超了
        assertTrue(ImageTilePolicy.shouldTile(4001, 4000))
        // 4096×4096 面积 16.78M，虽然单边没超也走分块
        assertTrue(ImageTilePolicy.shouldTile(4096, 4096))
    }

    @Test
    fun 长图即使单边没超阈值也走分块() {
        // 1024×20000 = 20.48M px，整图 ARGB_8888 要 82MB，手表上必 OOM
        assertTrue(ImageTilePolicy.shouldTile(1024, 20000))
        assertTrue(ImageTilePolicy.shouldTile(20000, 1024))
    }

    @Test
    fun 尺寸无效时不走分块() {
        assertFalse(ImageTilePolicy.shouldTile(0, 0))
        assertFalse(ImageTilePolicy.shouldTile(-1, 200))
        assertFalse(ImageTilePolicy.shouldTile(200, -1))
        assertFalse(ImageTilePolicy.isValidDimensions(0, 100))
        assertTrue(ImageTilePolicy.isValidDimensions(1, 1))
    }

    @Test
    fun 只有静态图片格式能分块解码() {
        assertTrue(ImageTilePolicy.isTileableMime("image/jpeg"))
        assertTrue(ImageTilePolicy.isTileableMime("image/jpg"))
        assertTrue(ImageTilePolicy.isTileableMime("image/png"))
        assertTrue(ImageTilePolicy.isTileableMime("image/webp"))
        // 大小写不该影响判断
        assertTrue(ImageTilePolicy.isTileableMime("IMAGE/PNG"))
        // 动图必须走整图，否则只显示第一帧
        assertFalse(ImageTilePolicy.isTileableMime("image/gif"))
        // 大小写归一也要在「拒绝」这一侧同样成立，否则 IMAGE/GIF 会被当成静态图分块
        assertFalse(ImageTilePolicy.isTileableMime("IMAGE/GIF"))
        assertFalse(ImageTilePolicy.isTileableMime("video/mp4"))
    }

    @Test
    fun MIME缺失时兜底放行_这是兜底不是正确性() {
        // 注意：下面两条断言钉的是**兜底取值**，不是「MIME 未知就能分块」这条正确性结论。
        // 设计意图：拿不到 MIME（null/空串）时无法判断格式，一律返回 true 让调用方按静态图去试分块，
        // 真正解不出来时由 BigImageView 回退整图兜住。之所以要钉住 true：
        // 一旦改成返回 false，所有 MIME 缺失的图都会静默退回整图解码——大图在手表上直接 OOM。
        assertTrue("未知 MIME 走兜底放行，必须返回 true", ImageTilePolicy.isTileableMime(null))
        assertTrue("空串 MIME 走兜底放行，必须返回 true", ImageTilePolicy.isTileableMime(""))
    }

    // ---------- 适配倍率 ----------

    @Test
    fun 初始倍率让整张图装进视图() {
        // 400×400 的视图里放 1000×500 的图 → 按宽适配 0.4，高度只占 200
        assertEquals(0.4f, ImageTilePolicy.fitScale(1000, 500, 400, 400), 0.0001f)
        // 正方形图按宽/高任一边都是 0.4
        assertEquals(0.4f, ImageTilePolicy.fitScale(1000, 1000, 400, 400), 0.0001f)
        // 长图按高适配（整图可见），0.02 = 400/20000
        assertEquals(0.02f, ImageTilePolicy.fitScale(1024, 20000, 400, 400), 0.0001f)
    }

    @Test
    fun 尺寸无效时倍率回落到一比一() {
        assertEquals(1f, ImageTilePolicy.fitScale(0, 100, 400, 400), 0.0001f)
        assertEquals(1f, ImageTilePolicy.fitScale(100, 100, 0, 400), 0.0001f)
        assertEquals(1f, ImageTilePolicy.fitScale(100, 100, 400, 0), 0.0001f)
    }

    // ---------- 采样率 ----------

    @Test
    fun 一比一或放大时采样率取一() {
        assertEquals(1, ImageTilePolicy.sampleSizeFor(1f))
        assertEquals(1, ImageTilePolicy.sampleSizeFor(2f))
        assertEquals(1, ImageTilePolicy.sampleSizeFor(6.25f))
        assertEquals(1, ImageTilePolicy.sampleSizeFor(1.5f))
    }

    @Test
    fun 缩小时采样率取二的幂且不小到糊() {
        // 0.5：一个源像素占半个屏幕像素 → 2 倍采样正好
        assertEquals(2, ImageTilePolicy.sampleSizeFor(0.5f))
        // 0.4：需要 2.5 倍 → 取不超过它的最大 2 的幂 = 2
        assertEquals(2, ImageTilePolicy.sampleSizeFor(0.4f))
        assertEquals(4, ImageTilePolicy.sampleSizeFor(0.25f))
        // 0.1：需要 10 倍 → 8
        assertEquals(8, ImageTilePolicy.sampleSizeFor(0.1f))
        // 0.03：需要 33 倍，封顶 32
        assertEquals(32, ImageTilePolicy.sampleSizeFor(0.03f))
        assertEquals(32, ImageTilePolicy.sampleSizeFor(0.02f))
    }

    @Test
    fun 倍率异常时按最省内存处理() {
        assertEquals(ImageTilePolicy.MAX_SAMPLE_SIZE, ImageTilePolicy.sampleSizeFor(Float.NaN))
        assertEquals(ImageTilePolicy.MAX_SAMPLE_SIZE, ImageTilePolicy.sampleSizeFor(0f))
        assertEquals(ImageTilePolicy.MAX_SAMPLE_SIZE, ImageTilePolicy.sampleSizeFor(-1f))
        // 无穷大（坏数据）→ 取最清晰，至少能看出东西
        assertEquals(ImageTilePolicy.MIN_SAMPLE_SIZE, ImageTilePolicy.sampleSizeFor(Float.POSITIVE_INFINITY))
    }

    @Test
    fun 块覆盖的源像素等于块边长乘采样率() {
        assertEquals(256, ImageTilePolicy.tileSourcePx(1))
        assertEquals(1024, ImageTilePolicy.tileSourcePx(4))
        assertEquals(8192, ImageTilePolicy.tileSourcePx(32))
        // 非法采样率/块边长要夹住，不能算出 0（否则切块会死循环）
        assertEquals(256, ImageTilePolicy.tileSourcePx(0))
        assertEquals(1, ImageTilePolicy.tileSourcePx(1, 0))
    }

    // ---------- 可见区 / 预取区 ----------

    @Test
    fun 未缩放时可见区就是视图覆盖的源矩形() {
        val rect = ImageTilePolicy.visibleRect(1000, 1000, 400, 400, 1f, 0f, 0f)
        assertEquals(ImageTilePolicy.TileRect(0, 0, 400, 400), rect)
    }

    @Test
    fun 图片左上角移出屏幕后可见区整体平移() {
        // 图片往左上挪了 100px（offset 为负）→ 屏幕左上角看到的是源图 (100,100)
        val rect = ImageTilePolicy.visibleRect(1000, 1000, 400, 400, 1f, -100f, -100f)
        assertEquals(ImageTilePolicy.TileRect(100, 100, 500, 500), rect)
    }

    @Test
    fun 缩小时可见区变大并裁到图片范围内() {
        // 0.5 倍时 400px 视图看到 800 源像素；图片只有 1000 → 仍完整落在图内
        assertEquals(
            ImageTilePolicy.TileRect(0, 0, 800, 800),
            ImageTilePolicy.visibleRect(1000, 1000, 400, 400, 0.5f, 0f, 0f)
        )
        // 转出右下边界要裁回去
        assertEquals(
            ImageTilePolicy.TileRect(600, 600, 1000, 1000),
            ImageTilePolicy.visibleRect(1000, 1000, 400, 400, 0.5f, -300f, -300f)
        )
    }

    @Test
    fun 图片比视图小时可见区裁到图片本身() {
        // 100×100 的图居中放在 400px 视图里（offset=150），四边都多出来
        val rect = ImageTilePolicy.visibleRect(100, 100, 400, 400, 1f, 150f, 150f)
        assertEquals(ImageTilePolicy.TileRect(0, 0, 100, 100), rect)
    }

    @Test
    fun 倍率或视图尺寸无效时不要求解码() {
        assertEquals(
            ImageTilePolicy.TileRect(0, 0, 0, 0),
            ImageTilePolicy.visibleRect(1000, 1000, 0, 400, 1f, 0f, 0f)
        )
        assertEquals(
            ImageTilePolicy.TileRect(0, 0, 0, 0),
            ImageTilePolicy.visibleRect(0, 1000, 400, 400, 1f, 0f, 0f)
        )
        // 倍率坏掉时退化成「整张图都算可见」，至少不会漏块导致白屏
        assertEquals(
            ImageTilePolicy.TileRect(0, 0, 1000, 1000),
            ImageTilePolicy.visibleRect(1000, 1000, 400, 400, 0f, 0f, 0f)
        )
    }

    @Test
    fun 预取区在可见区四周各扩一半() {
        val visible = ImageTilePolicy.TileRect(200, 200, 600, 600)
        assertEquals(
            ImageTilePolicy.TileRect(0, 0, 800, 800),
            ImageTilePolicy.prefetchRect(visible, 1000, 1000)
        )
        // fraction=0 等价于不预取
        assertEquals(visible, ImageTilePolicy.prefetchRect(visible, 1000, 1000, 0f))
        // 负数/NaN 都按不预取处理
        assertEquals(visible, ImageTilePolicy.prefetchRect(visible, 1000, 1000, -1f))
        assertEquals(visible, ImageTilePolicy.prefetchRect(visible, 1000, 1000, Float.NaN))
    }

    @Test
    fun 预取区挨着图片边缘时被裁掉() {
        val visible = ImageTilePolicy.TileRect(0, 0, 200, 200)
        assertEquals(
            ImageTilePolicy.TileRect(0, 0, 300, 300),
            ImageTilePolicy.prefetchRect(visible, 1000, 1000, 0.5f)
        )
        // 可见区为空时不做预取
        val empty = ImageTilePolicy.TileRect(0, 0, 0, 0)
        assertEquals(empty, ImageTilePolicy.prefetchRect(empty, 1000, 1000, 0.5f))
    }

    // ---------- 切块 ----------

    @Test
    fun 切块起点对齐到块边长的整数倍() {
        // 可见区从 300 开始，但块必须从 256 的整数倍开始切——否则每滑动 1px 缓存全失效
        val rects = ImageTilePolicy.tileRects(
            ImageTilePolicy.TileRect(300, 300, 700, 700), 1000, 1000, 256
        )
        assertEquals(4, rects.size)
        assertEquals(256, rects[0].left)
        assertEquals(256, rects[0].top)
        // 四个块并集正好覆盖 (256,256)-(700,700)
        assertEquals((700 - 256).toLong() * (700 - 256), rects.sumOf { it.width.toLong() * it.height })
    }

    @Test
    fun 切出的块都在图片内且互不重叠() {
        val imageWidth = 1000
        val imageHeight = 1200
        val rects = ImageTilePolicy.tileRects(
            ImageTilePolicy.TileRect(0, 0, imageWidth, imageHeight), imageWidth, imageHeight, 256
        )
        assertTrue(rects.isNotEmpty())
        rects.forEach { rect ->
            assertFalse("空块 $rect", rect.isEmpty)
            assertTrue("越界 $rect", rect.left >= 0 && rect.top >= 0)
            assertTrue("越界 $rect", rect.right <= imageWidth && rect.bottom <= imageHeight)
        }
        // 面积之和 = 整个区域（不重不漏）
        assertEquals(
            imageWidth.toLong() * imageHeight,
            rects.sumOf { it.width.toLong() * it.height }
        )
    }

    @Test
    fun 边缘块会变小而不是越界() {
        // 600×600 的区域按 256 切：最右一列只有 88px 宽
        val rects = ImageTilePolicy.tileRects(
            ImageTilePolicy.TileRect(0, 0, 600, 600), 600, 600, 256
        )
        assertEquals(9, rects.size)
        val last = rects.last()
        assertEquals(600, last.right)
        assertEquals(600, last.bottom)
        assertEquals(88, last.width)
        assertEquals(88, last.height)
    }

    @Test
    fun 可见区完全在图外时不切块() {
        val rects = ImageTilePolicy.tileRects(
            ImageTilePolicy.TileRect(2000, 2000, 2100, 2100), 1000, 1000, 256
        )
        assertTrue(rects.isEmpty())
    }

    @Test
    fun 切块参数异常时不产生零尺寸块() {
        val rects = ImageTilePolicy.tileRects(
            ImageTilePolicy.TileRect(0, 0, 100, 100), 100, 100, 0
        )
        assertTrue(rects.isNotEmpty())
        rects.forEach { assertFalse("空块 $it", it.isEmpty) }
    }

    // ---------- OOM 降级 ----------

    @Test
    fun 采样率每次翻倍直到上限() {
        assertEquals(2, ImageTilePolicy.degradedSample(1))
        assertEquals(4, ImageTilePolicy.degradedSample(2))
        assertEquals(32, ImageTilePolicy.degradedSample(16))
        // 已到上限就原样返回：调用方据此判定「不能再降了，回退整图」
        assertEquals(ImageTilePolicy.MAX_SAMPLE_SIZE, ImageTilePolicy.degradedSample(32))
    }

    @Test
    fun 非法采样率降级时也被夹住() {
        assertEquals(2, ImageTilePolicy.degradedSample(0))
        assertEquals(2, ImageTilePolicy.degradedSample(-5))
        assertEquals(ImageTilePolicy.MAX_SAMPLE_SIZE, ImageTilePolicy.degradedSample(100))
    }

    // ---------- 铺块层序列（长图无限加载的回归） ----------

    @Test
    fun 铺块层只会越来越糊不会比当前倍率更细() {
        // 采样率越大 = 越糊。序列必须从「当前倍率该有的档位」起步、逐级 ×2，
        // 绝不能出现比起点更小的采样率——那正是 26.11.14 长图 bug 的根：
        // 一路解到采样率 1，块数涨到缓存装不下，屏上的块被反复挤掉 → 无限加载。
        assertEquals(listOf(1, 2, 4, 8, 16, 32), ImageTilePolicy.fallbackSamples(1))
        assertEquals(listOf(2, 4, 8, 16, 32), ImageTilePolicy.fallbackSamples(2))
        assertEquals(listOf(4, 8, 16, 32), ImageTilePolicy.fallbackSamples(4))
        // 已经是最粗的档位：没有更糊的兜底层，只解这一层
        assertEquals(listOf(32), ImageTilePolicy.fallbackSamples(32))
        // 非法输入被夹进合法区间，且至少含一项
        assertEquals(listOf(1, 2, 4, 8, 16, 32), ImageTilePolicy.fallbackSamples(0))
        assertEquals(listOf(1, 2, 4, 8, 16, 32), ImageTilePolicy.fallbackSamples(-7))
        assertEquals(listOf(32), ImageTilePolicy.fallbackSamples(1000))
        ImageTilePolicy.fallbackSamples(3).let { samples ->
            assertTrue("必须从 allowed 起步", samples.first() >= 3)
            assertEquals("必须严格递增", samples, samples.sorted().distinct())
        }
    }

    @Test
    fun 长图整图适配时一轮只铺三块() {
        // 1024×20000 的长图塞进 400×400 的手表屏：适配倍率 0.02 → 采样率 32（块盖 8192 源像素）
        val scale = ImageTilePolicy.fitScale(1024, 20000, 400, 400)
        assertEquals(0.02f, scale, 0.0001f)
        val sample = ImageTilePolicy.sampleSizeFor(scale)
        assertEquals(32, sample)
        val visible = ImageTilePolicy.visibleRect(1024, 20000, 400, 400, scale, 0f, 0f)
        val prefetch = ImageTilePolicy.prefetchRect(visible, 1024, 20000)
        var total = 0
        for (layer in ImageTilePolicy.fallbackSamples(sample)) {
            val target = if (layer == sample) prefetch else visible
            total += ImageTilePolicy.tileCount(
                target, 1024, 20000, ImageTilePolicy.tileSourcePx(layer)
            )
        }
        // 修好之前这里会是 434 块（远大于 24 块的缓存上限 → 无限重解）
        assertEquals(3, total)
    }

    // ---------- 块数统计与缓存容量约束 ----------

    @Test
    fun 块数与切块结果一致() {
        val cases = listOf(
            ImageTilePolicy.TileRect(300, 300, 700, 700) to 256,
            ImageTilePolicy.TileRect(0, 0, 600, 600) to 256,
            ImageTilePolicy.TileRect(0, 0, 1024, 20000) to 8192,
            ImageTilePolicy.TileRect(0, 0, 1024, 20000) to 256,
            ImageTilePolicy.TileRect(2000, 2000, 2100, 2100) to 256
        )
        for ((area, tile) in cases) {
            val rects = ImageTilePolicy.tileRects(area, 1000, 1000, tile)
            val count = ImageTilePolicy.tileCount(area, 1000, 1000, tile)
            assertEquals(
                "面积 $area / 块 $tile：计数口径必须与切块一致",
                rects.size.toLong(),
                count.toLong()
            )
        }
    }

    @Test
    fun 块数按对齐后的桶计算() {
        // 块起点对齐到 256 的整数倍：从 300 起算，实际用到 256 和 512 两个桶（不是 300/512 两块）
        val misaligned = ImageTilePolicy.TileRect(300, 300, 700, 700)
        assertEquals(4, ImageTilePolicy.tileCount(misaligned, 1000, 1000, 256))
        assertEquals(4, ImageTilePolicy.tileRects(misaligned, 1000, 1000, 256).size)
        // 刚好对齐时桶数就等于算术商，边界那块不会被多算
        val aligned = ImageTilePolicy.TileRect(0, 0, 512, 512)
        assertEquals(4, ImageTilePolicy.tileCount(aligned, 1000, 1000, 256))
        // 图片边界裁掉尾部的那块仍然算一块
        val clamped = ImageTilePolicy.TileRect(0, 0, 999, 999)
        assertEquals(16, ImageTilePolicy.tileCount(clamped, 999, 999, 256))
        assertEquals(16, ImageTilePolicy.tileRects(clamped, 999, 999, 256).size)
        // 参数非法时一块都不切
        assertEquals(0, ImageTilePolicy.tileCount(
            ImageTilePolicy.TileRect(0, 0, 0, 0), 999, 999, 256
        ))
        assertEquals(0, ImageTilePolicy.tileCount(
            ImageTilePolicy.TileRect(0, 0, 100, 100), 0, 999, 256
        ))
    }

    @Test
    fun 块数超缓存容量时采样率往粗退() {
        // 一屏 1024×20000 全可见、要按采样率 1 解 → 316 块，远超 24 块的缓存
        val visible = ImageTilePolicy.TileRect(0, 0, 1024, 20000)
        val sample = ImageTilePolicy.sampleWithinTileBudget(visible, 1024, 20000, 1, 24)
        assertTrue("必须退到能装进缓存的档位，实际 $sample", sample > 1)
        assertTrue(
            "退到的档位得真的装得下",
            ImageTilePolicy.tileCount(visible, 1024, 20000, ImageTilePolicy.tileSourcePx(sample)) <= 24
        )
        // 装得下时原样返回，绝不变细
        assertEquals(8, ImageTilePolicy.sampleWithinTileBudget(
            ImageTilePolicy.TileRect(0, 0, 1024, 2000), 1024, 2000, 8, 24
        ))
        // 容量参数无效（<=0）时不动采样率
        assertEquals(1, ImageTilePolicy.sampleWithinTileBudget(visible, 1024, 20000, 1, 0))
        // 退到最粗仍装不下：返回上限，由调用方决定是否回退整图
        assertEquals(
            ImageTilePolicy.MAX_SAMPLE_SIZE,
            ImageTilePolicy.sampleWithinTileBudget(
                ImageTilePolicy.TileRect(0, 0, 100000, 100000), 100000, 100000, 1, 24
            )
        )
    }

    // ---------- 矩形基础行为 ----------

    @Test
    fun 矩形裁剪到图片范围() {
        assertEquals(
            ImageTilePolicy.TileRect(0, 0, 50, 50),
            ImageTilePolicy.TileRect(-10, -10, 50, 50).clampTo(100, 100)
        )
        assertEquals(
            ImageTilePolicy.TileRect(80, 80, 100, 100),
            ImageTilePolicy.TileRect(80, 80, 150, 150).clampTo(100, 100)
        )
        assertTrue(ImageTilePolicy.TileRect(-50, -50, -10, -10).clampTo(100, 100).isEmpty)
        assertTrue(ImageTilePolicy.TileRect(0, 0, 10, 10).clampTo(0, 0).isEmpty)
    }

    @Test
    fun 矩形相交判定用左闭右开() {
        val a = ImageTilePolicy.TileRect(0, 0, 10, 10)
        assertTrue(a.intersects(ImageTilePolicy.TileRect(5, 5, 15, 15)))
        // 贴边不算相交——块与块之间正是贴边关系，不能被当成重叠
        assertFalse(a.intersects(ImageTilePolicy.TileRect(10, 0, 20, 10)))
        assertFalse(a.intersects(ImageTilePolicy.TileRect(10, 10, 20, 20)))
        assertFalse(a.intersects(ImageTilePolicy.TileRect(20, 20, 30, 30)))
    }
}
