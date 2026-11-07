package com.RobinNotBad.BiliClient.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuConfigTest {

    @Test
    fun defaultEnabled_matchesSpec() {
        // 可选私有项（OPTIONAL_ITEMS）在私有构建里会追加到末尾；公开构建里它是空列表，
        // 所以这条断言对两种构建都成立，不需要分叉。
        assertEquals(
            listOf(
                "recommend", "short_video", "popular", "hotsearch", "search",
                "dynamic", "myspace", "message", "local", "settings"
            ) + MenuConfig.OPTIONAL_ITEMS + MenuConfig.EXIT,
            MenuConfig.DEFAULT_ENABLED
        )
    }

    @Test
    fun resolve_noOldData_returnsDefaults() {
        assertEquals(MenuConfig.DEFAULT_ENABLED, MenuConfig.resolveEnabledList(null, emptyMap()))
    }

    @Test
    fun resolve_validOldSort_preservesOrderAndFiltersBySwitches() {
        val oldSort = "recommend;short_video;popular;precious;ranking;hotsearch;live;timeline;search;dynamic;myspace;message;local;settings"
        val switches = mapOf(
            "popular" to true, "short_video" to true, "precious" to false,
            "ranking" to false, "live" to true, "timeline" to false
        )
        assertEquals(
            listOf(
                "recommend", "short_video", "popular", "hotsearch", "live",
                "search", "dynamic", "myspace", "message", "local", "settings"
            ) + MenuConfig.OPTIONAL_ITEMS + MenuConfig.EXIT,
            MenuConfig.resolveEnabledList(oldSort, switches)
        )
    }

    @Test
    fun resolve_invalidOldSort_fallsBackToCanonicalOrder() {
        assertEquals(
            MenuConfig.DEFAULT_ENABLED,
            MenuConfig.resolveEnabledList("recommend;not_a_key", emptyMap())
        )
    }

    @Test
    fun resolve_missingSwitchKeys_useDefaults() {
        val oldSort = "recommend;short_video;popular;precious;ranking;hotsearch;live;timeline;search;dynamic;myspace;message;local;settings"
        assertEquals(MenuConfig.DEFAULT_ENABLED, MenuConfig.resolveEnabledList(oldSort, emptyMap()))
    }

    @Test
    fun resolve_fixedItemsAlwaysPresent_missingFixedAppendedLast() {
        val oldSort = "recommend;short_video;popular;precious;ranking;hotsearch;live;timeline;search;dynamic;myspace;message;local"
        val switches = mapOf(
            "popular" to true, "short_video" to true, "precious" to true,
            "ranking" to true, "live" to true, "timeline" to true
        )
        val result = MenuConfig.resolveEnabledList(oldSort, switches)
        assertTrue("固定项必须全部在结果中", MenuConfig.FIXED_ITEMS.all { it in result })
        // 缺失项（固定项与可选私有项）追加在末尾；固定项排在可选私有项之前；
        // 「退出」是动作项，永远压在整份列表最后（所以固定项前面还要多让一格）
        assertEquals(
            "缺失的固定项追加到末尾（其后只剩可选的私有项与退出项）",
            "settings",
            result[result.size - 2 - MenuConfig.OPTIONAL_ITEMS.size]
        )
        assertEquals(MenuConfig.EXIT, result.last())
    }

    @Test
    fun serialize_parse_roundTrip() {
        val list = listOf("recommend", "search", "local", "settings")
        assertEquals("recommend;search;local;settings", MenuConfig.serialize(list))
        assertEquals(list, MenuConfig.parse(MenuConfig.serialize(list)))
    }

    @Test
    fun parse_rejectsBlank() {
        assertNull(MenuConfig.parse(""))
        assertNull(MenuConfig.parse(null))
        assertNull(MenuConfig.parse("   "))
    }

    @Test
    fun parse_rejectsUnknownKey() {
        assertNull(MenuConfig.parse("recommend;bogus"))
    }

    @Test
    fun parse_rejectsDuplicate() {
        assertNull(MenuConfig.parse("recommend;recommend"))
    }

    // ---- 容错解析：本版本不认识的菜单 key（旧/新构建、私有构建特有项）不能连累整份配置 ----

    @Test
    fun parseLenient_dropsUnknownKeysAndDuplicates() {
        assertEquals(
            listOf("settings", "recommend", "local"),
            MenuConfig.parseLenient("settings;bogus;recommend;settings;local")
        )
    }

    @Test
    fun parseLenient_keepsOptionalPrivateKey() {
        val raw = (listOf("recommend", "search") + MenuConfig.OPTIONAL_ITEMS).joinToString(";")
        assertEquals(listOf("recommend", "search") + MenuConfig.OPTIONAL_ITEMS, MenuConfig.parseLenient(raw))
    }

    @Test
    fun parseLenient_blankOrAllUnknown_returnsNull() {
        assertNull(MenuConfig.parseLenient(null))
        assertNull(MenuConfig.parseLenient("  "))
        assertNull(MenuConfig.parseLenient("bogus;another_bogus"))
    }

    @Test
    fun loadEnabled_unknownKey_keepsUserOrderInsteadOfResettingToDefaults() {
        // 用户的真实顺序 + 一个本版本不认识的 key（模拟从带私有功能的构建切到公开构建）
        val stored = "settings;recommend;bogus_key;local;search"
        var written: String? = null
        val result = MenuConfig.loadEnabled(
            readString = { key -> if (key == SharedPreferencesUtil.MENU_ENABLED) stored else null },
            readBoolean = { null },
            writeString = { _, value -> written = value }
        )
        // 认识的 key 按用户原顺序保留；缺失的可选私有项补在末尾；未知 key 被清掉；
        // 「退出」是老数据里没有的项，也在末尾补上
        assertEquals(
            listOf("settings", "recommend", "local", "search") +
                MenuConfig.OPTIONAL_ITEMS + MenuConfig.EXIT,
            result
        )
        assertEquals(MenuConfig.serialize(result), written)
        // 关键：没有被重置成默认顺序（默认顺序以 recommend 开头）
        assertTrue("用户顺序被保留", result.first() == "settings")
    }

    @Test
    fun loadEnabled_unknownKeyWithoutFixedItems_stillMigratesToDefaults() {
        var written: String? = null
        val result = MenuConfig.loadEnabled(
            readString = { key ->
                when (key) {
                    SharedPreferencesUtil.MENU_ENABLED -> "bogus_key;recommend"
                    else -> null
                }
            },
            readBoolean = { null },
            writeString = { _, value -> written = value }
        )
        assertEquals(MenuConfig.DEFAULT_ENABLED, result)
        assertEquals(MenuConfig.serialize(MenuConfig.DEFAULT_ENABLED), written)
    }

    @Test
    fun disabledFrom_returnsCanonicalComplement() {
        // 未启用区 = 全量顺序减去已启用项；「继续播放」（26.11.07 起是默认关闭的动作项）
        // 排在最后，用户在菜单设置里能把它拖回已启用区。
        assertEquals(
            listOf("precious", "ranking", "live", "timeline", MenuConfig.RESUME),
            MenuConfig.disabledFrom(MenuConfig.DEFAULT_ENABLED)
        )
    }

    @Test
    fun loadEnabled_existingValidValue_returnsWithoutWrite() {
        var wrote = false
        // 「合法」的定义包含可选私有项：私有构建下缺了它们要走一次迁移（见 MenuConfig.loadEnabled）。
        val valid = MenuConfig.serialize(
            listOf(
                "recommend", "short_video", "popular", "hotsearch", "search",
                "dynamic", "myspace", "message", "local", "settings"
            ) + MenuConfig.OPTIONAL_ITEMS + MenuConfig.EXIT
        )
        val result = MenuConfig.loadEnabled(
            readString = { if (it == SharedPreferencesUtil.MENU_ENABLED) valid else null },
            readBoolean = { false },
            writeString = { _, _ -> wrote = true }
        )
        assertEquals(valid.split(";"), result)
        assertEquals(false, wrote)
    }

    @Test
    fun loadEnabled_oldDataWithoutExit_appendsExitAndWritesBack() {
        // 26.11.02 之前「退出」是硬编码在菜单最下方的固定按钮，老用户存下的
        // menu_enabled 里没有这个 key；不补的话升级后菜单里再也找不到退出按钮
        val old = MenuConfig.serialize(
            MenuConfig.DEFAULT_ENABLED.filter { it != MenuConfig.EXIT }
        )
        var written: String? = null
        val result = MenuConfig.loadEnabled(
            readString = { key ->
                when (key) {
                    SharedPreferencesUtil.MENU_ENABLED -> old
                    // 从没保存过菜单配置：读出来是空串（SharedPreferencesUtil 的缺省值）
                    SharedPreferencesUtil.MENU_EXIT_STATE -> ""
                    else -> null
                }
            },
            readBoolean = { null },
            writeString = { _, value -> written = value }
        )
        assertEquals(MenuConfig.DEFAULT_ENABLED, result)
        assertEquals(MenuConfig.serialize(MenuConfig.DEFAULT_ENABLED), written)
    }

    @Test
    fun loadEnabled_userDisabledExit_doesNotAddItBack() {
        // 用户自己把「退出」拖进未启用区：保存时写过 MENU_EXIT_STATE，
        // 于是"缺项"不再被当成老数据，别偷偷加回来
        val stored = MenuConfig.serialize(
            MenuConfig.DEFAULT_ENABLED.filter { it != MenuConfig.EXIT }
        )
        var wrote = false
        val result = MenuConfig.loadEnabled(
            readString = { key ->
                when (key) {
                    SharedPreferencesUtil.MENU_ENABLED -> stored
                    SharedPreferencesUtil.MENU_EXIT_STATE -> "0"
                    else -> null
                }
            },
            readBoolean = { null },
            writeString = { _, _ -> wrote = true }
        )
        assertEquals(false, MenuConfig.EXIT in result)
        assertEquals(false, wrote)
    }

    @Test
    fun saveEnabled_recordsExitStateMarker() {
        val written = HashMap<String, String>()
        MenuConfig.saveEnabled(listOf("recommend", MenuConfig.EXIT)) { key, value ->
            written[key] = value
        }
        assertEquals("1", written[SharedPreferencesUtil.MENU_EXIT_STATE])
        written.clear()
        MenuConfig.saveEnabled(listOf("recommend")) { key, value ->
            written[key] = value
        }
        assertEquals("0", written[SharedPreferencesUtil.MENU_EXIT_STATE])
    }

    @Test
    fun loadEnabled_invalidValue_migratesFromOldAndWritesBack() {
        var written: String? = null
        val result = MenuConfig.loadEnabled(
            readString = { key ->
                when (key) {
                    SharedPreferencesUtil.MENU_ENABLED -> "recommend;short_video"
                    else -> null
                }
            },
            readBoolean = { null },
            writeString = { _, value -> written = value }
        )
        assertEquals(MenuConfig.DEFAULT_ENABLED, result)
        assertEquals(MenuConfig.serialize(MenuConfig.DEFAULT_ENABLED), written)
    }

    // ---- 启动页（打开应用时直接进入的菜单页） ----

    @Test
    fun resolveStart_noStored_returnsRecommend() {
        assertEquals("recommend", MenuConfig.resolveStart(MenuConfig.DEFAULT_ENABLED, null))
    }

    @Test
    fun resolveStart_storedStillEnabled_keepsItEvenIfReordered() {
        assertEquals("search", MenuConfig.resolveStart(MenuConfig.DEFAULT_ENABLED, "search"))
        assertEquals("local", MenuConfig.resolveStart(listOf("settings", "local", "recommend"), "local"))
    }

    @Test
    fun resolveStart_storedDisabled_fallsBackToRecommend() {
        assertEquals("recommend", MenuConfig.resolveStart(MenuConfig.DEFAULT_ENABLED, "precious"))
        assertEquals("recommend", MenuConfig.resolveStart(MenuConfig.DEFAULT_ENABLED, "not_a_key"))
    }

    @Test
    fun resolveStart_exitIsNeverAStartPage() {
        // 启动页指向退出项 → 改回默认；连"列表首项兜底"也不能落到退出项上，
        // 否则开机就直接收摊
        assertEquals("recommend", MenuConfig.resolveStart(MenuConfig.DEFAULT_ENABLED, MenuConfig.EXIT))
        assertEquals("recommend", MenuConfig.resolveStart(listOf(MenuConfig.EXIT), null))
    }

    @Test
    fun resolveStart_recommendMissing_fallsBackToFirstEnabled() {
        // 推荐是固定项，正常不会缺失；这里只验证表达式本身的兜底顺序
        assertEquals("search", MenuConfig.resolveStart(listOf("search", "local"), null))
        assertEquals("recommend", MenuConfig.resolveStart(emptyList(), "search"))
    }

    @Test
    fun loadStart_neverSet_returnsDefaultWithoutWrite() {
        var wrote = false
        val result = MenuConfig.loadStart(
            enabled = MenuConfig.DEFAULT_ENABLED,
            readString = { null },
            writeString = { _, _ -> wrote = true }
        )
        assertEquals("recommend", result)
        assertEquals(false, wrote)
    }

    @Test
    fun loadStart_blankStored_treatedAsNeverSet() {
        var wrote = false
        val result = MenuConfig.loadStart(
            enabled = MenuConfig.DEFAULT_ENABLED,
            readString = { "" },
            writeString = { _, _ -> wrote = true }
        )
        assertEquals("recommend", result)
        assertEquals(false, wrote)
    }

    @Test
    fun loadStart_validStored_returnsWithoutWrite() {
        var wrote = false
        val result = MenuConfig.loadStart(
            enabled = MenuConfig.DEFAULT_ENABLED,
            readString = { "myspace" },
            writeString = { _, _ -> wrote = true }
        )
        assertEquals("myspace", result)
        assertEquals(false, wrote)
    }

    @Test
    fun loadStart_storedDisabled_rewritesDefault() {
        var written: String? = null
        val result = MenuConfig.loadStart(
            enabled = MenuConfig.DEFAULT_ENABLED,
            readString = { key -> if (key == SharedPreferencesUtil.MENU_START) "timeline" else null },
            writeString = { key, value -> if (key == SharedPreferencesUtil.MENU_START) written = value }
        )
        assertEquals("recommend", result)
        assertEquals("recommend", written)
    }

    @Test
    fun loadStart_storedExit_rewritesDefault() {
        // 旧版本可能把退出项存成了启动页（它当时还是固定按钮，不该被选），读到就改回来
        var written: String? = null
        val result = MenuConfig.loadStart(
            enabled = MenuConfig.DEFAULT_ENABLED,
            readString = { key ->
                if (key == SharedPreferencesUtil.MENU_START) MenuConfig.EXIT else null
            },
            writeString = { key, value ->
                if (key == SharedPreferencesUtil.MENU_START) written = value
            }
        )
        assertEquals("recommend", result)
        assertEquals("recommend", written)
    }

    // ---- 断网启动兜底：网络不可用时一律进缓存页 ----

    @Test
    fun resolveLaunchStart_online_usesUserSetting() {
        assertEquals(
            "myspace",
            MenuConfig.resolveLaunchStart(MenuConfig.DEFAULT_ENABLED, "myspace", true)
        )
        // 在线且从未设置过 → 默认推荐
        assertEquals(
            "recommend",
            MenuConfig.resolveLaunchStart(MenuConfig.DEFAULT_ENABLED, null, true)
        )
    }

    @Test
    fun resolveLaunchStart_offline_alwaysGoesToCache() {
        // 无论用户把启动页设成了哪一页，断网都进缓存页
        for (stored in listOf(null, "recommend", "search", "myspace", "settings")) {
            assertEquals(
                "local",
                MenuConfig.resolveLaunchStart(MenuConfig.DEFAULT_ENABLED, stored, false)
            )
        }
        // 启用列表异常（比如连推荐都缺）也不影响断网兜底
        assertEquals("local", MenuConfig.resolveLaunchStart(emptyList(), "recommend", false))
    }

    @Test
    fun resolveLaunchStart_offlineKeyIsFixedItem() {
        // 缓存是固定项：不存在「断网目标页被移入未启用」这种组合
        assertTrue(MenuConfig.OFFLINE_START in MenuConfig.FIXED_ITEMS)
        assertTrue(MenuConfig.OFFLINE_START in MenuConfig.ALL_ITEMS)
    }

    // ---- 动作项「继续播放」（26.11.07：默认不启用，用户可自己打开）----

    @Test
    fun resume_isActionItemButNotEnabledByDefault() {
        // 26.11.07 之前它是有会话就硬插到第一位的固定按钮，用户关不掉；
        // 现在进菜单配置、能排序能关掉，但默认必须是关的
        assertTrue(MenuConfig.RESUME in MenuConfig.ALL_ITEMS)
        assertTrue(MenuConfig.RESUME in MenuConfig.ACTION_ITEMS)
        assertTrue(MenuConfig.RESUME !in MenuConfig.DEFAULT_ENABLED)
        assertEquals(MenuConfig.EXIT, MenuConfig.DEFAULT_ENABLED.last())
    }

    @Test
    fun loadEnabled_oldDataWithoutResume_keepsItDisabled() {
        // 老用户存下的 menu_enabled 里没有 resume，升级后菜单不该突然多出「继续播放」
        val old = MenuConfig.serialize(MenuConfig.DEFAULT_ENABLED)
        var wrote = false
        val result = MenuConfig.loadEnabled(
            readString = { key ->
                if (key == SharedPreferencesUtil.MENU_ENABLED) old else null
            },
            readBoolean = { null },
            writeString = { _, _ -> wrote = true }
        )
        assertTrue(MenuConfig.RESUME !in result)
        // 配置本身合法（固定项齐全），不该被当成老数据改写
        assertEquals(false, wrote)
    }

    @Test
    fun loadEnabled_userEnabledResume_isPreserved() {
        // 用户在菜单设置里把「继续播放」放回已启用（并排在最前）：读回来顺序不能动
        val stored = MenuConfig.serialize(listOf(MenuConfig.RESUME) + MenuConfig.DEFAULT_ENABLED)
        var wrote = false
        val result = MenuConfig.loadEnabled(
            readString = { key ->
                if (key == SharedPreferencesUtil.MENU_ENABLED) stored else null
            },
            readBoolean = { null },
            writeString = { _, _ -> wrote = true }
        )
        assertEquals(0, result.indexOf(MenuConfig.RESUME))
        assertEquals(stored, MenuConfig.serialize(result))
        assertEquals(false, wrote)
    }

    @Test
    fun resolveStart_resumeIsNeverAStartPage() {
        assertEquals(
            "recommend",
            MenuConfig.resolveStart(MenuConfig.DEFAULT_ENABLED, MenuConfig.RESUME)
        )
        // 连推荐都缺时跳过动作项，取第一个真正的页面
        assertEquals(
            "search",
            MenuConfig.resolveStart(listOf(MenuConfig.RESUME, MenuConfig.EXIT, "search"), null)
        )
        // 只剩动作项时退回默认启动页，不能返回 resume
        assertEquals("recommend", MenuConfig.resolveStart(listOf(MenuConfig.RESUME), null))
    }

    @Test
    fun loadStart_storedResume_rewritesDefault() {
        var written: String? = null
        val result = MenuConfig.loadStart(
            enabled = MenuConfig.DEFAULT_ENABLED,
            readString = { key ->
                if (key == SharedPreferencesUtil.MENU_START) MenuConfig.RESUME else null
            },
            writeString = { key, value ->
                if (key == SharedPreferencesUtil.MENU_START) written = value
            }
        )
        assertEquals("recommend", result)
        assertEquals("recommend", written)
    }
}
