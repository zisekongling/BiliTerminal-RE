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
        // 拿不到 MIME 时按「可以试」处理（真解不了由 BigImageView 回退整图兜住）
        assertTrue(ImageTilePolicy.isTileableMime(null))
        assertTrue(ImageTilePolicy.isTileableMime(""))
        // 动图必须走整图，否则只显示第一帧
        assertFalse(ImageTilePolicy.isTileableMime("image/gif"))
        assertFalse(ImageTilePolicy.isTileableMime("video/mp4"))
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
