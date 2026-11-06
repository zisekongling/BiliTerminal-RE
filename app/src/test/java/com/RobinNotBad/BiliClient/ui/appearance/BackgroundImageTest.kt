package com.RobinNotBad.BiliClient.ui.appearance

import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [BackgroundImage] 的纯 JVM 单测。
 *
 * 只测「纯计算 + 偏好读写」这一层：解码（BitmapFactory）、拷文件（ContentResolver）、
 * 画布裁剪都要真机/模拟器，这里不碰。覆盖率集中在三处容易算错的地方：
 *
 * 1. `inSampleSize` 只能取 2 的幂，且解码后最长边要**不超过**上限（算小会 OOM，算大是白降清）；
 * 2. 「覆盖」矩形必须等比、居中、铺满（算错会出现留黑边或拉伸变形）；
 * 3. 安装前的尺寸/大小校验（放行不该放行的图 = 之后解码 OOM / 显示不出）。
 */
class BackgroundImageTest {

    private val fakePrefs = FakeSharedPreferences()

    private val backgroundPath =
        "/data/user/0/com.RobinNotBad.BiliClient/files/appearance/background.img"

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
        AppearanceManager.setTheme(ColorScheme.THEME_DEFAULT)
        fakePrefs.edit().clear().apply()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    // ==================== 降采样倍数 ====================

    @Test
    fun computeInSampleSize_downscalesLongestSideToLimit() {
        // 4000 / 4 = 1000 <= 1024，而 4000 / 2 = 2000 > 1024 → 取 4
        assertEquals(4, BackgroundImage.computeInSampleSize(4000, 3000, 1024))
    }

    @Test
    fun computeInSampleSize_keepsImageThatAlreadyFits() {
        assertEquals(1, BackgroundImage.computeInSampleSize(1024, 768, 1024))
        assertEquals(1, BackgroundImage.computeInSampleSize(500, 500, 1024))
    }

    @Test
    fun computeInSampleSize_handlesPortraitAndSquare() {
        // 竖图看最长边（高），不只看宽
        assertEquals(2, BackgroundImage.computeInSampleSize(1000, 2048, 1024))
        assertEquals(2, BackgroundImage.computeInSampleSize(2048, 2048, 1024))
    }

    @Test
    fun computeInSampleSize_neverReturnsZeroOrFraction() {
        val sample = BackgroundImage.computeInSampleSize(8191, 3, 1024)
        assertTrue("必须是 2 的幂", sample > 0 && sample and (sample - 1) == 0)
        assertTrue("除以倍数后不能超过上限", 8191 / sample <= 1024)
    }

    @Test
    fun computeInSampleSize_invalidInputFallsBackTo1() {
        assertEquals(1, BackgroundImage.computeInSampleSize(0, 100, 1024))
        assertEquals(1, BackgroundImage.computeInSampleSize(100, -1, 1024))
        assertEquals(1, BackgroundImage.computeInSampleSize(100, 100, 0))
    }

    // ==================== 覆盖矩形 ====================

    @Test
    fun coverRect_wideBitmapCropsLeftAndRight() {
        // 400×200 铺 200×200：高度撑满，两侧各裁 100
        val rect = BackgroundImage.coverRect(400, 200, 200, 200)
        assertEquals(-100f, rect[0], 0.01f)
        assertEquals(0f, rect[1], 0.01f)
        assertEquals(400f, rect[2], 0.01f)
        assertEquals(200f, rect[3], 0.01f)
    }

    @Test
    fun coverRect_tallBitmapCropsTopAndBottom() {
        val rect = BackgroundImage.coverRect(200, 400, 200, 200)
        assertEquals(0f, rect[0], 0.01f)
        assertEquals(-100f, rect[1], 0.01f)
        assertEquals(200f, rect[2], 0.01f)
        assertEquals(400f, rect[3], 0.01f)
    }

    @Test
    fun coverRect_squareBitmapFillsTallView() {
        // 100×100 铺 200×300：放大 3 倍 → 300×300，左右各裁 50
        val rect = BackgroundImage.coverRect(100, 100, 200, 300)
        assertEquals(-50f, rect[0], 0.01f)
        assertEquals(0f, rect[1], 0.01f)
        assertEquals(300f, rect[2], 0.01f)
        assertEquals(300f, rect[3], 0.01f)
    }

    @Test
    fun coverRect_exactMatchDrawsAsIs() {
        val rect = BackgroundImage.coverRect(200, 200, 200, 200)
        assertEquals(0f, rect[0], 0.01f)
        assertEquals(0f, rect[1], 0.01f)
        assertEquals(200f, rect[2], 0.01f)
        assertEquals(200f, rect[3], 0.01f)
    }

    @Test
    fun coverRect_invalidSizesReturnEmptyRect() {
        assertEquals(0f, BackgroundImage.coverRect(0, 100, 200, 200)[2], 0.01f)
        assertEquals(0f, BackgroundImage.coverRect(100, 100, 0, 200)[3], 0.01f)
    }

    @Test
    fun coverRect_alwaysCoversView() {
        val cases = listOf(
            intArrayOf(400, 200, 200, 200),
            intArrayOf(200, 400, 200, 200),
            intArrayOf(100, 100, 200, 300),
            intArrayOf(3000, 1000, 480, 480)
        )
        for (case in cases) {
            val rect = BackgroundImage.coverRect(case[0], case[1], case[2], case[3])
            assertTrue("左边不能露底", rect[0] <= 0.01f)
            assertTrue("上边不能露底", rect[1] <= 0.01f)
            assertTrue("右边不能露底", rect[0] + rect[2] >= case[2] - 0.01f)
            assertTrue("下边不能露底", rect[1] + rect[3] >= case[3] - 0.01f)
        }
    }

    // ==================== 蒙版色 ====================

    @Test
    fun scrimColor_darkPressesDownLightLifts() {
        // 50% → 127/255
        assertEquals(0x7F000000, BackgroundImage.scrimColor(50, false))
        assertEquals(0x7FFFFFFF, BackgroundImage.scrimColor(50, true))
        // 0% 不遮罩、100% 全遮
        assertEquals(0x00000000, BackgroundImage.scrimColor(0, false))
        assertEquals(0xFF000000.toInt(), BackgroundImage.scrimColor(100, false))
        // 越界收敛到 100，不能出现 alpha 溢出
        assertEquals(0xFF000000.toInt(), BackgroundImage.scrimColor(150, false))
        assertEquals(0x00000000, BackgroundImage.scrimColor(-10, false))
    }

    @Test
    fun scrimAlpha_mapsPercentToAlpha() {
        assertEquals(0, BackgroundImage.scrimAlpha(0))
        assertEquals(63, BackgroundImage.scrimAlpha(25))
        assertEquals(127, BackgroundImage.scrimAlpha(50))
        assertEquals(191, BackgroundImage.scrimAlpha(75))
        assertEquals(255, BackgroundImage.scrimAlpha(100))
    }

    // ==================== 适配方式 ====================

    @Test
    fun normalizeFit_onlyAcceptsContain() {
        assertEquals(BackgroundImage.FIT_CONTAIN, BackgroundImage.normalizeFit("contain"))
        assertEquals(BackgroundImage.FIT_COVER, BackgroundImage.normalizeFit("cover"))
        assertEquals(BackgroundImage.FIT_COVER, BackgroundImage.normalizeFit(null))
        assertEquals(BackgroundImage.FIT_COVER, BackgroundImage.normalizeFit(""))
        assertEquals(BackgroundImage.FIT_COVER, BackgroundImage.normalizeFit("COVER"))
    }

    @Test
    fun containRect_keepsWholeImageCentered() {
        // 4000×3000 进 400×400：缩到 400×300，上下各留 50
        val rect = BackgroundImage.containRect(4000, 3000, 400, 400)
        assertEquals(0f, rect[0], 0.01f)
        assertEquals(50f, rect[1], 0.01f)
        assertEquals(400f, rect[2], 0.01f)
        assertEquals(300f, rect[3], 0.01f)
    }

    @Test
    fun containRect_letterboxesPortraitIntoWideView() {
        // 1000×2000 进 400×400：缩到 200×400，左右各留 100
        val rect = BackgroundImage.containRect(1000, 2000, 400, 400)
        assertEquals(100f, rect[0], 0.01f)
        assertEquals(0f, rect[1], 0.01f)
        assertEquals(200f, rect[2], 0.01f)
        assertEquals(400f, rect[3], 0.01f)
    }

    @Test
    fun fitRect_dispatchesByFitMode() {
        val cover = BackgroundImage.fitRect(4000, 3000, 400, 400, BackgroundImage.FIT_COVER)
        // 4000×3000 铺满 400×400：放大 4/30 → 533.33×400，左右各裁 66.67
        assertEquals(-66.67f, cover[0], 0.1f)
        assertEquals(533.33f, cover[2], 0.1f)
        assertEquals(400f, cover[3], 0.01f)

        val contain = BackgroundImage.fitRect(4000, 3000, 400, 400, BackgroundImage.FIT_CONTAIN)
        assertEquals(0f, contain[0], 0.01f)
        assertEquals(50f, contain[1], 0.01f)

        // 脏值按填满处理，与 normalizeFit 一致
        val dirty = BackgroundImage.fitRect(4000, 3000, 400, 400, "bogus")
        assertEquals(cover[0], dirty[0], 0.01f)
    }

    @Test
    fun fitRect_invalidInputCollapsesToZero() {
        val rect = BackgroundImage.fitRect(0, 3000, 400, 400, BackgroundImage.FIT_CONTAIN)
        assertEquals(0f, rect[0], 0.01f)
        assertEquals(0f, rect[2], 0.01f)
    }

    // ==================== 安装前校验 ====================

    @Test
    fun rejectReason_acceptsNormalImage() {
        assertNull(BackgroundImage.rejectReason(2L * 1024 * 1024, 1080, 1920))
    }

    @Test
    fun rejectReason_rejectsEmptyFile() {
        assertNotNull(BackgroundImage.rejectReason(0L, 1080, 1920))
    }

    @Test
    fun rejectReason_rejectsOversizeFile() {
        assertNotNull(BackgroundImage.rejectReason(BackgroundImage.MAX_BYTES + 1, 1080, 1920))
        // 正好卡在上限要放行（否则用户看到「不能超过 16MB」却被拒的 16MB 图）
        assertNull(BackgroundImage.rejectReason(BackgroundImage.MAX_BYTES, 1080, 1920))
    }

    @Test
    fun rejectReason_rejectsUndecodableImage() {
        assertNotNull("outWidth/outHeight 为 0 说明不是能解码的图", BackgroundImage.rejectReason(1024L, 0, 0))
    }

    @Test
    fun rejectReason_rejectsHugeDimension() {
        assertNotNull(BackgroundImage.rejectReason(1024L, BackgroundImage.MAX_DIMENSION + 1, 100))
        assertNull(BackgroundImage.rejectReason(1024L, BackgroundImage.MAX_DIMENSION, 100))
    }

    // ==================== 偏好读写与外观版本号 ====================

    @Test
    fun preferenceKey_isStableForSettingsKeysMigration() {
        // 这个键名当前硬编码在 BackgroundImage 里，之后要收进 SettingsKeys；改名会让存量用户丢背景图。
        assertEquals("appearance_background_path", BackgroundImage.KEY_PATH)
    }

    @Test
    fun currentPath_defaultsToEmpty() {
        assertEquals("", BackgroundImage.currentPath())
    }

    @Test
    fun setBackgroundPath_persistsAndBumpsVersion() {
        AppearanceManager.setBackgroundPath(backgroundPath)
        assertEquals(backgroundPath, BackgroundImage.currentPath())
        assertEquals(backgroundPath, AppearanceManager.snapshot().backgroundPath)
        assertEquals(
            "外观写入必须递增版本号，否则其它页面 onResume 不会重建",
            AppearanceManager.INITIAL_VERSION + 1, AppearanceManager.version()
        )
    }

    @Test
    fun clearBackgroundPath_resetsAndBumpsVersion() {
        AppearanceManager.setBackgroundPath(backgroundPath)
        AppearanceManager.clearBackgroundPath()
        assertEquals("", BackgroundImage.currentPath())
        assertEquals("", AppearanceManager.snapshot().backgroundPath)
        assertEquals(AppearanceManager.INITIAL_VERSION + 2, AppearanceManager.version())
    }

    @Test
    fun isEnabled_isFalseWithoutStoredPath() {
        assertFalse("没设置路径就不该进入背景图分支", BackgroundImage.isEnabled())
    }

    // ==================== 档位存档 ====================

    @Test
    fun coerceScrimPercent_fallsBackAndClamps() {
        assertEquals(BackgroundImage.DEFAULT_SCRIM, BackgroundImage.coerceScrimPercent(null))
        assertEquals(BackgroundImage.DEFAULT_SCRIM, BackgroundImage.coerceScrimPercent(""))
        assertEquals(BackgroundImage.DEFAULT_SCRIM, BackgroundImage.coerceScrimPercent("abc"))
        assertEquals(0, BackgroundImage.coerceScrimPercent("0"))
        assertEquals(75, BackgroundImage.coerceScrimPercent(" 75 "))
        assertEquals(100, BackgroundImage.coerceScrimPercent("150"))
        assertEquals(0, BackgroundImage.coerceScrimPercent("-5"))
    }

    @Test
    fun currentFitAndScrim_defaultWhenNothingStored() {
        assertEquals(BackgroundImage.FIT_COVER, BackgroundImage.currentFit())
        assertEquals(BackgroundImage.DEFAULT_SCRIM, BackgroundImage.currentScrimPercent())
    }

    @Test
    fun setBackgroundFit_normalizesAndBumpsVersion() {
        AppearanceManager.setBackgroundFit("contain")
        assertEquals(BackgroundImage.FIT_CONTAIN, BackgroundImage.currentFit())
        assertEquals(BackgroundImage.FIT_CONTAIN, AppearanceManager.snapshot().backgroundFit)
        assertEquals(AppearanceManager.INITIAL_VERSION + 1, AppearanceManager.version())

        // 脏值不会原样落盘
        AppearanceManager.setBackgroundFit("bogus")
        assertEquals(BackgroundImage.FIT_COVER, BackgroundImage.currentFit())
    }

    @Test
    fun setBackgroundScrim_clampsAndBumpsVersion() {
        AppearanceManager.setBackgroundScrim(75)
        assertEquals(75, BackgroundImage.currentScrimPercent())
        assertEquals(75, AppearanceManager.snapshot().backgroundScrim)
        assertEquals(AppearanceManager.INITIAL_VERSION + 1, AppearanceManager.version())

        AppearanceManager.setBackgroundScrim(999)
        assertEquals(100, BackgroundImage.currentScrimPercent())
    }

    @Test
    fun scrimPercents_areTheSettingsPageSteps() {
        assertEquals(listOf(0, 25, 50, 75), BackgroundImage.SCRIM_PERCENTS.toList())
    }
}
