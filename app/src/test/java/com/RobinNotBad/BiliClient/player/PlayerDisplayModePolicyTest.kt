package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerDisplayModePolicy] 的纯逻辑单测。
 *
 * 这组用例钉的是「显示方式设置从来没生效过」那个 bug 的修复口径：存档是字符串、
 * 读取必须按字符串判，认不出来时回落 `sdk < 26`。
 */
class PlayerDisplayModePolicyTest {

    @Test
    fun 规范取值按字符串判() {
        assertTrue(PlayerDisplayModePolicy.useTextureView("TextureView", 28))
        assertFalse(PlayerDisplayModePolicy.useTextureView("SurfaceView", 25))
    }

    @Test
    fun 取值大小写与首尾空格容错() {
        assertTrue(PlayerDisplayModePolicy.useTextureView("  textureview ", 28))
        assertFalse(PlayerDisplayModePolicy.useTextureView("SURFACEVIEW", 25))
    }

    @Test
    fun 历史遗留的布尔字符串也认() {
        // 早期版本默认值写的是 "true"/"false"，用户可能留下这种存档
        assertTrue(PlayerDisplayModePolicy.useTextureView("true", 28))
        assertFalse(PlayerDisplayModePolicy.useTextureView("false", 25))
    }

    @Test
    fun 未设置或认不出来时回落sdk默认() {
        assertTrue(PlayerDisplayModePolicy.useTextureView(null, 25))
        assertFalse(PlayerDisplayModePolicy.useTextureView(null, 26))
        assertTrue(PlayerDisplayModePolicy.useTextureView("", 25))
        assertFalse(PlayerDisplayModePolicy.useTextureView("", 28))
        assertTrue(PlayerDisplayModePolicy.useTextureView("???", 25))
        assertFalse(PlayerDisplayModePolicy.useTextureView("???", 28))
    }

    @Test
    fun 默认值与取值回写同一套字符串() {
        assertEquals(PlayerDisplayModePolicy.TEXTURE_VIEW, PlayerDisplayModePolicy.defaultFor(25))
        assertEquals(PlayerDisplayModePolicy.SURFACE_VIEW, PlayerDisplayModePolicy.defaultFor(26))
        assertEquals(PlayerDisplayModePolicy.SURFACE_VIEW, PlayerDisplayModePolicy.defaultFor(34))
        assertEquals(PlayerDisplayModePolicy.TEXTURE_VIEW, PlayerDisplayModePolicy.valueOf(true))
        assertEquals(PlayerDisplayModePolicy.SURFACE_VIEW, PlayerDisplayModePolicy.valueOf(false))
    }

    @Test
    fun 默认值与解析结果自洽() {
        // 默认值字符串喂回解析函数，必须得到与 sdk 判定一致的结果
        for (sdk in intArrayOf(21, 25, 26, 28, 34)) {
            val raw = PlayerDisplayModePolicy.defaultFor(sdk)
            assertEquals(
                "sdk=$sdk 默认值 $raw 解析后应与 sdk 判定一致",
                sdk < 26,
                PlayerDisplayModePolicy.useTextureView(raw, sdk)
            )
        }
    }

    @Test
    fun 旧布尔存档迁移成规范字符串() {
        // 老用户的存档是 Boolean：设置页那行当时是 choose（putBoolean），true = 选中第一个选项
        assertEquals(PlayerDisplayModePolicy.TEXTURE_VIEW, PlayerDisplayModePolicy.migrateLegacy(true))
        assertEquals(PlayerDisplayModePolicy.SURFACE_VIEW, PlayerDisplayModePolicy.migrateLegacy(false))
        // 历史遗留的布尔字符串同样要收口
        assertEquals(PlayerDisplayModePolicy.TEXTURE_VIEW, PlayerDisplayModePolicy.migrateLegacy("true"))
        assertEquals(PlayerDisplayModePolicy.SURFACE_VIEW, PlayerDisplayModePolicy.migrateLegacy(" false "))
    }

    @Test
    fun 迁移是幂等的_规范串与认不出来的值都不动() {
        // 已经是规范串：返回 null，调用方据此不写盘，重复迁移不会反复改写
        assertEquals(null, PlayerDisplayModePolicy.migrateLegacy(PlayerDisplayModePolicy.TEXTURE_VIEW))
        assertEquals(null, PlayerDisplayModePolicy.migrateLegacy(PlayerDisplayModePolicy.SURFACE_VIEW))
        // 键不存在 / 别的类型（早期误写成 Int 之类）/ 垃圾串：都不动，交给解析函数回落默认
        assertEquals(null, PlayerDisplayModePolicy.migrateLegacy(null))
        assertEquals(null, PlayerDisplayModePolicy.migrateLegacy(1))
        assertEquals(null, PlayerDisplayModePolicy.migrateLegacy("???"))
    }

    @Test
    fun 迁移后播放器解析结果与用户当年的选择一致() {
        // 迁移的目的是「别把用户选过的值丢掉」：迁移结果喂回解析函数必须还原当年的语义
        for (sdk in intArrayOf(25, 28, 34)) {
            val migrated = PlayerDisplayModePolicy.migrateLegacy(true)
            assertTrue(PlayerDisplayModePolicy.useTextureView(migrated, sdk))
            val migratedFalse = PlayerDisplayModePolicy.migrateLegacy(false)
            assertFalse(PlayerDisplayModePolicy.useTextureView(migratedFalse, sdk))
        }
    }
}
