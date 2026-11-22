package com.RobinNotBad.BiliClient.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject

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

    /** 与 [AccountManager] 里的私有常量同名字面量（键值不该为了测试而放开可见性）。 */
    private val CURRENT_ACCOUNT_KEY = "current_account_mid"

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

    // ---------- 单条导出（26.11.02 的三字段格式，特殊登录页「查看登录信息」用） ----------

    @Test
    fun exportCurrentAccount_notLoggedIn_returnsEmptyString() {
        assertEquals("", AccountManager.exportCurrentAccount())
    }

    @Test
    fun exportCurrentAccount_isExactlyThreeFieldSingleAccountJson() {
        apply(1L, accessKey = "ak-1")

        val exported = AccountManager.exportCurrentAccount()
        assertTrue("导出内容不能为空", exported.isNotEmpty())

        val json = JSONObject(exported)
        assertEquals(
            "必须是 11.02 的三字段格式：不能夹带 mid / name，也不能是多账号信封",
            setOf("cookies", "refresh_token", "access_key"),
            json.keys().asSequence().toSet()
        )
        assertEquals(cookiesOf(1L), json.getString("cookies"))
        assertEquals("rt-1", json.getString("refresh_token"))
        assertEquals("ak-1", json.getString("access_key"))

        // 回环：导出 → 导入侧解析必须是单账号（Batch 会让特殊登录页走「只进列表不切号」那条路）
        val parsed = SpecialLoginParser.parseAny(exported)
        assertTrue("单条导出必须被 parseAny 读成 Single：$parsed", parsed is SpecialLoginParser.ParseResult.Single)
        assertEquals(1L, (parsed as SpecialLoginParser.ParseResult.Single).account.mid)
    }

    @Test
    fun exportCurrentAccount_onlyExportsCurrentAccount() {
        apply(1L, accessKey = "ak-1")
        apply(2L, accessKey = "ak-2")

        val parsed = SpecialLoginParser.parseAny(AccountManager.exportCurrentAccount())

        assertTrue(parsed is SpecialLoginParser.ParseResult.Single)
        assertEquals("只导出当前账号，不能把 1 号也带上", 2L, (parsed as SpecialLoginParser.ParseResult.Single).account.mid)
    }

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

    // ==================== 刷新台账：待确认口令 / 会话原子提交 / 启动修复 ====================
    // 26.11.22「老是掉登录」返工新增。这组用例锁的是「刷新过程中任何一步失败都不许把本地
    // 已经拿到的新凭据弄丢」——一次性轮换口令丢了就永久断链，那是用户眼里的掉登录。

    @Test
    fun commitRefreshedSession_writesNewCredentialSetAndRecordsPendingConfirm() {
        apply(1L)
        val newCookies = cookiesOf(1L, "v2")

        assertTrue(
            AccountManager.commitRefreshedSession(cookiesOf(1L), "rt-1", newCookies, "rt-2", "ak-2")
        )

        assertEquals(newCookies, SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
        assertEquals("rt-2", SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""))
        assertEquals("ak-2", SharedPreferencesUtil.getString(SharedPreferencesUtil.access_key, ""))
        assertEquals("csrf-1-v2", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, ""))
        // 旧口令必须留成待确认凭证：confirm 失败还能事后补发
        assertEquals("rt-1", AccountManager.getPendingConfirm())
        // 快照要跟着走，否则下次冷启动回滚会把用户送回旧会话
        assertEquals(newCookies, AccountManager.getAccountByMid(1L)!!.cookies)
    }

    @Test
    fun commitRefreshedSession_rejectsWhenLocalSessionChangedMeanwhile() {
        apply(1L)
        // 期间已经换成了 v3（比如另一条刷新链先成功）
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookiesOf(1L, "v3"))
        SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, "rt-3")

        assertFalse(
            "本地会话已换过，旧结果写下去会把好口令换回废口令",
            AccountManager.commitRefreshedSession(cookiesOf(1L), "rt-1", cookiesOf(1L, "v2"), "rt-2", "")
        )

        assertEquals(cookiesOf(1L, "v3"), SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
        assertEquals("rt-3", SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""))
    }

    @Test
    fun commitRefreshedSession_rejectsStaleRefreshToken() {
        apply(1L)

        assertFalse(
            "发起刷新时拿到的口令已经不是当前口令（一次性轮换），说明这份结果过期了",
            AccountManager.commitRefreshedSession(cookiesOf(1L), "rt-OLD", cookiesOf(1L, "v2"), "rt-2", "")
        )

        assertEquals(cookiesOf(1L), SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
    }

    @Test
    fun commitRefreshedSession_rejectsIncompleteNewCookies() {
        apply(1L)

        // 缺 bili_jct：写进去等于把本地登录态换成假的
        assertFalse(
            AccountManager.commitRefreshedSession(
                cookiesOf(1L), "rt-1", "SESSDATA=sess-1-v2; DedeUserID=1", "rt-2", ""
            )
        )
        assertEquals(cookiesOf(1L), SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
        assertEquals("", AccountManager.getPendingConfirm())
    }

    @Test
    fun commitRefreshedSession_rejectsOtherAccount() {
        apply(1L)

        assertFalse(
            AccountManager.commitRefreshedSession(cookiesOf(1L), "rt-1", cookiesOf(2L), "rt-2", "")
        )
        assertEquals(1L, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
    }

    @Test
    fun pendingConfirm_setAndClear() {
        assertEquals("", AccountManager.getPendingConfirm())

        AccountManager.setPendingConfirm("rt-1")
        assertEquals("rt-1", AccountManager.getPendingConfirm())

        AccountManager.clearPendingConfirm()
        assertEquals("", AccountManager.getPendingConfirm())

        // 空串等于「没有待确认」，不该留下一个空值键
        AccountManager.setPendingConfirm("")
        assertEquals("", AccountManager.getPendingConfirm())
    }

    @Test
    fun applyCredentials_clearsPendingConfirmAndRefreshClock() {
        apply(1L)
        AccountManager.setPendingConfirm("rt-old")
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.app_token_last_refresh, 123L)

        // 重新登录 = 全新的登录态，上一轮的确认线索与刷新计时都该作废
        assertTrue(apply(1L))

        assertEquals("", AccountManager.getPendingConfirm())
        assertEquals(0L, SharedPreferencesUtil.getLong(SharedPreferencesUtil.app_token_last_refresh, -1L))
    }

    @Test
    fun restoreCurrentAccount_fixesMidFromCookie() {
        apply(1L)
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 0)
        SharedPreferencesUtil.putLong(CURRENT_ACCOUNT_KEY, 0)

        assertTrue(AccountManager.restoreCurrentAccount())

        // 真正随请求发出去的是 Cookie，mid 只是本地记账，所以以 Cookie 的 DedeUserID 为准
        assertEquals(1L, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
        assertEquals(1L, SharedPreferencesUtil.getLong(CURRENT_ACCOUNT_KEY, 0))
    }

    @Test
    fun restoreCurrentAccount_backfillsRefreshTokenFromSnapshot() {
        apply(1L)
        SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, "")

        assertTrue(AccountManager.restoreCurrentAccount())

        // 没有 refresh_token 时刷新链路会直接放弃，表现就是「再也不续期」
        assertEquals("rt-1", SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""))
    }

    @Test
    fun restoreCurrentAccount_noopWhenAlreadyConsistent() {
        apply(1L)

        assertFalse("一切都对的时候不该白写一次", AccountManager.restoreCurrentAccount())
    }

    @Test
    fun restoreRejectedSession_rollsBackToSnapshotSession() {
        apply(1L)
        // 模拟「轮换把新 Cookie 写进了 SP，但快照没跟着更新」的半新半旧状态
        val rotated = cookiesOf(1L, "v2")
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, rotated)

        assertTrue(AccountManager.restoreRejectedSession(rotated))

        assertEquals(cookiesOf(1L), SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
    }

    @Test
    fun restoreRejectedSession_refusesWhenSnapshotIsSameSession() {
        apply(1L)

        assertFalse(
            "快照里就是这份被拒的 Cookie：没有更早的会话可退",
            AccountManager.restoreRejectedSession(cookiesOf(1L))
        )
    }

    @Test
    fun restoreRejectedSession_refusesWithoutAnyMid() {
        assertFalse(AccountManager.restoreRejectedSession("SESSDATA=only-sess"))
    }
}

