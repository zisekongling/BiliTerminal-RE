package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 「自动跳过片头片尾」的默认值与引导判据单测。
 *
 * 钉住四件**不会报错、只会静默错**的事：
 *
 * 1. **默认必须是关闭**（26.10.22 恢复时的口径）——初版是「默认开启」，用户当时要求过
 *    「默认不是开启吗」；26.10.10 又因乱跳紧急撤回。26.10.22 修掉乱跳后恢复，用户重新拍板
 *    **默认关闭**：不让没主动开启的人被动跳片头。这个默认值原先散在四处字面量里，
 *    改漏一处的症状是「设置页显示开着、播放器却不跳」。
 * 2. **用户显式选过的那一侧必须保留**——升级覆盖安装后不能把人家关掉的开关又打开、
 *    也不能把开过的悄悄关掉。靠的是 `getBoolean(key, def)` 的语义（键不存在才用 def）。
 * 3. **引导判据与开关解耦**——原实现「开关开着就 return」，默认改成开启之后
 *    这条引导（真机清单第 200 条）会永远不出现，变成死代码。
 * 4. **撤回必须与存档无关**——把功能关掉时只改 [SkipOpEdPrefs.DEFAULT_ENABLED]
 *    是**无效**的：默认值只决定「键不存在时读什么」，老用户存档里早就是 `true` 了。
 *    护栏是 [SkipOpEdPrefs.FEATURE_ENABLED] 这个**与存档无关的独立否决位**，
 *    26.10.10 那次撤回就是靠它，26.10.22 恢复则是把它改回 `true`。
 */
class SkipOpEdPrefsTest {

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    // ---------- 默认值 ----------

    @Test
    fun 默认值是关闭() {
        assertFalse(
            "26.10.22 恢复时用户拍板：默认关闭，不让没主动开启的人被动跳片头",
            SkipOpEdPrefs.DEFAULT_ENABLED
        )
    }

    @Test
    fun 从未存过时读取默认值得到关闭() {
        // 新装用户：SP 里没有这个键
        val enabled = SharedPreferencesUtil.getBoolean(
            SettingsKeys.PLAYER_SKIP_OP_ED, SkipOpEdPrefs.DEFAULT_ENABLED
        )
        assertFalse("键不存在时应取默认值 false", enabled)
    }

    @Test
    fun 老用户显式关过之后仍然是关闭() {
        // 升级覆盖安装：用户上一版手动关掉了它
        SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_SKIP_OP_ED, false)
        val enabled = SharedPreferencesUtil.getBoolean(
            SettingsKeys.PLAYER_SKIP_OP_ED, SkipOpEdPrefs.DEFAULT_ENABLED
        )
        assertFalse("用户显式关过的设置不能被默认值覆盖掉", enabled)
    }

    @Test
    fun 老用户显式开过之后仍然是开启() {
        SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_SKIP_OP_ED, true)
        val enabled = SharedPreferencesUtil.getBoolean(
            SettingsKeys.PLAYER_SKIP_OP_ED, SkipOpEdPrefs.DEFAULT_ENABLED
        )
        assertTrue(enabled)
    }

    // ---------- 26.10.22 恢复：功能上线且默认关闭 ----------

    @Test
    fun 功能已恢复上线时总开关必须是开启() {
        assertTrue(
            "26.10.22 起「自动跳过片头片尾」已恢复（乱跳修掉之后），FEATURE_ENABLED 恢复为 true",
            SkipOpEdPrefs.FEATURE_ENABLED
        )
    }

    @Test
    fun 恢复后存档里写着开启就能跳() {
        // 恢复的核心断言：总开关为真时，是否跳过完全交还用户存档决定
        SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_SKIP_OP_ED, true)
        val prefValue = SharedPreferencesUtil.getBoolean(
            SettingsKeys.PLAYER_SKIP_OP_ED, SkipOpEdPrefs.DEFAULT_ENABLED
        )
        assertTrue(SkipOpEdPrefs.isEnabled(prefValue))
    }

    @Test
    fun 恢复后默认关闭不会自己跳() {
        // 用户 26.10.22 拍板的口径：默认关闭，没主动开启的用户不该被动跳片头。
        // 这条与上面的「默认值是关闭」互补：那个钉常量，这个钉端到端行为。
        val enabled = SkipOpEdPrefs.isEnabled(SkipOpEdPrefs.DEFAULT_ENABLED)
        assertFalse("默认值本身就不该被判成启用", enabled)
        val prefValue = SharedPreferencesUtil.getBoolean(
            SettingsKeys.PLAYER_SKIP_OP_ED, SkipOpEdPrefs.DEFAULT_ENABLED
        )
        assertFalse("键不存在时（新装用户）读出来应是关闭", prefValue)
    }

    @Test
    fun 老用户开过的话恢复后仍然开启() {
        // 26.10.10 撤回期间老用户存档里可能留着 true（那时开关是隐藏的，改不了）。
        // 默认值改 false 不会覆盖已有存档，所以这类用户恢复后是开启的——这是有意的：
        // 他们当初主动开过，不该因为升级被悄悄关掉。
        SharedPreferencesUtil.putBoolean(SettingsKeys.PLAYER_SKIP_OP_ED, true)
        val prefValue = SharedPreferencesUtil.getBoolean(
            SettingsKeys.PLAYER_SKIP_OP_ED, SkipOpEdPrefs.DEFAULT_ENABLED
        )
        assertTrue("存档里显式写着的 true 优先于默认值", prefValue)
        assertTrue(SkipOpEdPrefs.isEnabled(prefValue))
    }

    // ---------- 引导判据 ----------

    @Test
    fun 没引导过且有片段时要引导() {
        // 这是默认开启之后新增的行为：开关开着、但用户从没见过引导
        assertTrue(
            "默认开启后仍必须引导一次，否则用户不知道进度为什么自己动了",
            SkipOpEdPrefs.shouldShowGuide(hasSegments = true, alreadyGuided = false)
        )
    }

    @Test
    fun 引导过之后不再引导() {
        assertFalse(
            "每集都弹会很烦",
            SkipOpEdPrefs.shouldShowGuide(hasSegments = true, alreadyGuided = true)
        )
    }

    @Test
    fun 视频没有片头片尾时不引导() {
        assertFalse(
            "没有可跳片段还提示「可以自动跳过」是假承诺",
            SkipOpEdPrefs.shouldShowGuide(hasSegments = false, alreadyGuided = false)
        )
        assertFalse(SkipOpEdPrefs.shouldShowGuide(hasSegments = false, alreadyGuided = true))
    }

    // ---------- 文案随开关状态切换 ----------

    @Test
    fun 开关已开时文案是提示而不是劝开() {
        val text = SkipOpEdPrefs.guideText(enabled = true)
        assertTrue("已开启时应该说「已自动跳过」", text.contains("已自动跳过"))
        assertFalse("已经开着还劝用户开启是自相矛盾", text.contains("可以自动跳过"))
        assertEquals("知道了", SkipOpEdPrefs.guideActionText(enabled = true))
    }

    @Test
    fun 开关未开时文案是劝开() {
        val text = SkipOpEdPrefs.guideText(enabled = false)
        assertEquals("这个番剧有片头片尾，可以自动跳过", text)
        assertEquals("开启", SkipOpEdPrefs.guideActionText(enabled = false))
    }
}
