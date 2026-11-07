package com.RobinNotBad.BiliClient.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 多账号凭据管理 [AccountManager] 的纯 JVM 测试。
 *
 * 这组用例锁的是用户报过的三类问题：
 * 1. **切号/导入后凭据混乱**：上一个账号的 access_key / csrf 残留在 SharedPreferences 里；
 * 2. **特殊登录导入的账号串号**：导入数据里 mid 与 Cookie 的 DedeUserID 不一致却照写；
 * 3. **多账号导入导出**：导出的东西必须能被同一套解析器重新导入，多账号也不能互相覆盖。
 *
 * 只有 [AccountManager.saveCurrentAccount] 会发网络（昵称/头像），本类不碰它；
 * 其余方法都是纯本地写入，因此不需要 mock 网络层。
 */
class AccountManagerTest {

    private val fakePrefs = FakeSharedPreferences()

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = fakePrefs
        // 每个用例都从干净的账号列表开始（NetWorkUtil 的静态 Cookie 缓存跨用例残留，
        // 但本类断言的是 SharedPreferences / 账号列表，不受影响）
        SharedPreferencesUtil.putString("account_list", "")
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    private fun cookiesOf(mid: Long, tag: String = "v1"): String =
        "SESSDATA=sess-$mid-$tag; bili_jct=csrf-$mid-$tag; DedeUserID=$mid"

    private fun apply(mid: Long, accessKey: String = "ak-$mid", cookies: String = cookiesOf(mid)): Boolean =
        AccountManager.applyCredentials(mid, "", cookies, "rt-$mid", accessKey, "")

    // ==================== applyCredentials：整套替换，缺省字段必须写空 ====================

    @Test
    fun applyCredentials_writesWholeCredentialSet() {
        assertTrue(apply(1L))

        assertEquals(1L, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
        assertEquals(cookiesOf(1L), SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
        assertEquals("rt-1", SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""))
        assertEquals("ak-1", SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, ""))
        assertEquals("csrf-1-v1", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
        assertTrue(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.cookie_refresh, false))
        assertTrue(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.setup, false))
    }

    @Test
    fun applyCredentials_missingAccessKey_clearsPreviousAccountAccessKey() {
        // 先让 SP 里存在上一个账号的 access_key（特殊登录导入的数据通常不带 access_key）
        SharedPreferencesUtil.putString(SharedPreferencesUtil.access_key, "OLD-ACCOUNT-AK")

        assertTrue(AccountManager.applyCredentials(2L, "", cookiesOf(2L), "rt-2", "", ""))

        assertEquals(
            "导入数据里没有 access_key 时，必须写空而不是沿用上一个账号的",
            "",
            SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, "not-null")
        )
    }

    @Test
    fun applyCredentials_csrf_comesFromCookieNotFromStaleSnapshot() {
        SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, "STALE-CSRF")

        assertTrue(apply(3L))

        assertEquals("csrf-3-v1", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
    }

    @Test
    fun applyCredentials_cookieWithoutCsrf_fallsBackToHint() {
        val cookies = "SESSDATA=x; DedeUserID=4"

        assertTrue(AccountManager.applyCredentials(4L, "", cookies, "rt-4", "ak-4", "", "HINT-CSRF"))

        assertEquals("HINT-CSRF", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
    }

    @Test
    fun applyCredentials_cookieWithoutCsrfAndNoHint_writesEmpty() {
        val cookies = "SESSDATA=x; DedeUserID=5"

        assertTrue(AccountManager.applyCredentials(5L, "", cookies, "rt-5", "ak-5", ""))

        assertEquals("", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "not-empty"))
    }

    @Test
    fun applyCredentials_rejectsMidCookieMismatch_andWritesNothing() {
        assertFalse(
            "mid 与 Cookie 的 DedeUserID 不一致时必须拒绝，否则就是串号",
            AccountManager.applyCredentials(6L, "", cookiesOf(7L), "rt", "ak", "")
        )

        assertEquals(0L, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
        assertEquals("", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
        assertTrue(AccountManager.getAccounts().isEmpty())
    }

    @Test
    fun applyCredentials_rejectsCookieWithoutDedeUserId() {
        // Cookie 里连 DedeUserID 都没有时无法核对身份，以前会直接放行（空值放水）
        assertFalse(
            "Cookie 里没有 DedeUserID 时不能写凭据",
            AccountManager.applyCredentials(8L, "", "SESSDATA=sess; bili_jct=csrf", "rt", "ak", "")
        )
        assertEquals(0L, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
        assertTrue(AccountManager.getAccounts().isEmpty())
    }

    @Test
    fun applyCredentials_rejectsInvalidArguments() {
        assertFalse(AccountManager.applyCredentials(0L, "", cookiesOf(1L), "rt", "ak", ""))
        assertFalse(AccountManager.applyCredentials(-1L, "", cookiesOf(1L), "rt", "ak", ""))
        assertFalse(AccountManager.applyCredentials(1L, "", "", "rt", "ak", ""))
        assertFalse(AccountManager.applyCredentials(1L, "", "   ", "rt", "ak", ""))
    }

    // ==================== switchToAccount：账号之间互不污染 ====================

    @Test
    fun switchToAccount_keepsBothAccountsAndSwapsWholeCredentialSet() {
        apply(1L, accessKey = "ak-1")
        apply(2L, accessKey = "ak-2")
        assertEquals(2, AccountManager.getAccountCount())

        val first = AccountManager.getAccountByMid(1L)
        assertNotNull(first)
        assertTrue(AccountManager.switchToAccount(first!!))

        assertEquals(1L, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
        assertEquals(cookiesOf(1L), SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
        assertEquals("ak-1", SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, ""))
        assertEquals("rt-1", SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""))
        assertEquals("csrf-1-v1", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
        // 另一个账号的快照不能被这次切换改掉
        assertEquals("ak-2", AccountManager.getAccountByMid(2L)!!.accessKey)
    }

    @Test
    fun switchToAccount_invalidAccount_rejected() {
        val broken = AccountManager.AccountInfo(9L, "", "", "rt", "ak", "csrf", "")

        assertFalse(AccountManager.switchToAccount(broken))
        assertEquals(0L, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
    }

    // ==================== 导出：当前账号用实时凭证覆盖快照 ====================

    @Test
    fun exportAllAccounts_empty_returnsEmptyString() {
        assertEquals("", AccountManager.exportAllAccounts())
    }

    @Test
    fun exportAllAccounts_containsEveryAccountAndIsParseableByBatchParser() {
        apply(1L, accessKey = "ak-1")
        apply(2L, accessKey = "ak-2")

        val exported = AccountManager.exportAllAccounts()
        assertTrue("导出内容不能为空", exported.isNotEmpty())

        val parsed = SpecialLoginParser.parseAny(exported)
        assertTrue("导出的多账号格式必须能被 parseAny 重新读回：$parsed", parsed is SpecialLoginParser.ParseResult.Batch)
        parsed as SpecialLoginParser.ParseResult.Batch
        assertEquals(2, parsed.accounts.size)
        assertEquals(setOf(1L, 2L), parsed.accounts.map { it.mid }.toSet())
    }

    @Test
    fun exportAllAccounts_usesLiveCurrentCredentialInsteadOfStaleSnapshot() {
        apply(1L, accessKey = "ak-old")
        // 模拟 Cookie 刷新后 SP 是新的、列表快照还是旧的
        val refreshed = "SESSDATA=new; bili_jct=newcsrf; DedeUserID=1"
        NetWorkUtil.applyAccountCookies(refreshed)
        SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, "rt-new")

        val exported = AccountManager.exportAllAccounts()
        val parsed = SpecialLoginParser.parseAny(exported) as SpecialLoginParser.ParseResult.Batch

        assertEquals(refreshed, parsed.accounts.first().cookies)
        assertEquals("rt-new", parsed.accounts.first().refreshToken)
    }

    // ==================== 导入：只写列表，不动当前登录态 ====================

    @Test
    fun importAccounts_doesNotTouchCurrentLogin() {
        apply(1L)
        val imported = listOf(
            AccountManager.AccountInfo(2L, "B", cookiesOf(2L), "rt-2", "ak-2", "csrf-2-v1", "")
        )

        val result = AccountManager.importAccounts(imported)

        assertEquals(1, result.added)
        assertEquals("导入不是切号：当前账号必须还是 1", 1L, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
        assertEquals(cookiesOf(1L), SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
        assertEquals(2, AccountManager.getAccountCount())
    }

    @Test
    fun importAccounts_existingMid_replacesCredentialsWholesale() {
        apply(1L, accessKey = "ak-old")
        val newCookies = cookiesOf(1L, "v2")

        val result = AccountManager.importAccounts(
            listOf(AccountManager.AccountInfo(1L, "", newCookies, "rt-new", "ak-new", "", ""))
        )

        assertEquals(1, result.updated)
        assertEquals(1, AccountManager.getAccountCount())
        val stored = AccountManager.getAccountByMid(1L)!!
        assertEquals(newCookies, stored.cookies)
        assertEquals("ak-new", stored.accessKey)
        assertEquals("rt-new", stored.refreshToken)
        assertEquals("csrf-1-v2", stored.csrf)
    }

    @Test
    fun importAccounts_duplicateMidInOneBatch_countedOnce() {
        val same = AccountManager.AccountInfo(3L, "", cookiesOf(3L), "rt", "ak", "", "")

        val result = AccountManager.importAccounts(listOf(same, same))

        assertEquals(1, result.added)
        assertEquals(1, result.skipped)
        assertEquals(1, AccountManager.getAccountCount())
    }

    @Test
    fun importAccounts_midCookieMismatch_skipped() {
        val broken = AccountManager.AccountInfo(4L, "", cookiesOf(5L), "rt", "ak", "", "")

        val result = AccountManager.importAccounts(listOf(broken))

        assertEquals(0, result.total())
        assertEquals(1, result.skipped)
        assertTrue(AccountManager.getAccounts().isEmpty())
    }

    @Test
    fun importAccounts_emptyOrNull_noop() {
        assertEquals(0, AccountManager.importAccounts(emptyList()).total())
        assertEquals(0, AccountManager.importAccounts(null).total())
        assertTrue(AccountManager.getAccounts().isEmpty())
    }

    // ==================== 删除：当前账号不许删 ====================

    @Test
    fun removeAccount_refusesCurrentAccount() {
        apply(1L)

        assertFalse("删当前账号会被 saveCurrentAccount 再加回来，必须拒绝", AccountManager.removeAccount(1L))
        assertEquals(1, AccountManager.getAccountCount())
    }

    @Test
    fun removeAccount_removesOtherAccount() {
        apply(1L)
        apply(2L)

        // 当前账号是 2（后登录的那个），所以能删的是 1
        assertTrue(AccountManager.removeAccount(1L))
        assertEquals(1, AccountManager.getAccountCount())
        assertNull(AccountManager.getAccountByMid(1L))
        assertEquals(2L, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
    }

    // ==================== 退出：清干净，但保留列表 ====================

    @Test
    fun clearCurrentLogin_clearsAllCredentialKeysButKeepsAccounts() {
        apply(1L, accessKey = "ak-1")

        AccountManager.clearCurrentLogin()

        assertEquals(0L, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
        assertEquals("", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
        assertEquals("", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
        assertEquals("", SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""))
        assertEquals("", SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, ""))
        assertFalse(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.cookie_refresh, true))
        // 快照仍保留：切回去还能用
        assertEquals(1, AccountManager.getAccountCount())
    }

    // ==================== 代际隔离的判定函数 ====================

    @Test
    fun isStaleGeneration_onlyStaleWhenTaggedWithOlderGeneration() {
        // 没打标签的请求（极少数不走 NetWorkUtil 构造器的）不能被误杀
        assertFalse(NetWorkUtil.isStaleGeneration(null, 5L))
        assertFalse(NetWorkUtil.isStaleGeneration(5L, 5L))
        assertTrue(NetWorkUtil.isStaleGeneration(4L, 5L))
        assertTrue("请求建在「未来」代际同样不可信", NetWorkUtil.isStaleGeneration(6L, 5L))
    }

    @Test
    fun bumpAccountGeneration_incrementsMonotonically() {
        val before = NetWorkUtil.getAccountGeneration()
        NetWorkUtil.bumpAccountGeneration()
        assertEquals(before + 1, NetWorkUtil.getAccountGeneration())
    }
}
