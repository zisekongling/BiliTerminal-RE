package com.RobinNotBad.BiliClient.player

import com.RobinNotBad.BiliClient.util.FakeSharedPreferences
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 合集「上次看到哪一集」的定位判定与落盘（反馈 #105 / #108）。
 *
 * 这条判定的错误在手表上几乎看不出来：定位到别的集不会报错，只是列表停在了不痛不痒的位置，
 * 用户只会觉得「这个功能好像没生效」。所以把四种会让记录失效的情形逐个钉住。
 */
class CollectionResumeStoreTest {

    @Before
    fun setUp() {
        SharedPreferencesUtil.sharedPreferences = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        SharedPreferencesUtil.sharedPreferences = null
    }

    private fun record(seasonId: Long, aid: Long, updatedAt: Long = 1_700_000_000_000L) =
        CollectionResumeRecord(seasonId = seasonId, aid = aid, updatedAt = updatedAt)

    // ---------- 纯函数：该滚到哪一集 ----------

    @Test
    fun 记录命中本合集_定位到记录里的那一集() {
        val target = CollectionResumePolicy.resolveTargetAid(
            fromAid = 111L,
            seasonId = 9001L,
            record = record(9001L, 222L),
            memberAids = listOf(111L, 222L, 333L)
        )
        assertEquals("上次看的是 222，就不该停在入口视频 111 上", 222L, target)
    }

    @Test
    fun 记录的aid已不在本合集_回落入口视频() {
        // UP 删了那一集（或把它移出了合集），记录里的 aid 还在偏好里
        val target = CollectionResumePolicy.resolveTargetAid(
            fromAid = 111L,
            seasonId = 9001L,
            record = record(9001L, 999L),
            memberAids = listOf(111L, 222L, 333L)
        )
        assertEquals("滚到一个不存在的下标会直接不动，不如老实停在入口视频", 111L, target)
    }

    @Test
    fun seasonId不匹配_回落入口视频() {
        // 值里自带的 seasonId 与当前合集不符：记录被复制过、key 方案改过、或从备份恢复过
        val target = CollectionResumePolicy.resolveTargetAid(
            fromAid = 111L,
            seasonId = 9002L,
            record = record(9001L, 222L),
            memberAids = listOf(111L, 222L, 333L)
        )
        assertEquals("别的合集的进度不能拿来定这个合集的位置", 111L, target)
    }

    @Test
    fun 无记录_回落入口视频() {
        val target = CollectionResumePolicy.resolveTargetAid(
            fromAid = 111L,
            seasonId = 9001L,
            record = null,
            memberAids = listOf(111L, 222L, 333L)
        )
        assertEquals("第一次进这个合集，保持原有定位行为", 111L, target)
    }

    @Test
    fun 合集没有可用seasonId_即使有记录也不认() {
        // analyzeUgcSeason 拿不到 id 时填 -1，此时 key 会退化成 collection_resume_v1_-1
        val target = CollectionResumePolicy.resolveTargetAid(
            fromAid = 111L,
            seasonId = -1L,
            record = record(-1L, 222L),
            memberAids = listOf(111L, 222L)
        )
        assertEquals("拿不到合集标识时记录不可信", 111L, target)
    }

    // ---------- 纯函数：无 seasonId 时的降级定位 ----------

    @Test
    fun 降级槽命中本合集_定位到最近看的那一集() {
        val target = CollectionResumePolicy.resolveFallbackAid(
            fromAid = 111L,
            lastPlayedAid = 222L,
            memberAids = listOf(111L, 222L, 333L)
        )
        assertEquals(222L, target)
    }

    @Test
    fun 降级槽指向别的合集的那一集_回落入口视频() {
        // 全局槽不区分合集：上一次可能是在另一个无 id 合集里看的
        val target = CollectionResumePolicy.resolveFallbackAid(
            fromAid = 111L,
            lastPlayedAid = 999L,
            memberAids = listOf(111L, 222L, 333L)
        )
        assertEquals("那一集不在本合集里，滚过去只会定位到别处", 111L, target)
    }

    @Test
    fun 降级槽为空_回落入口视频() {
        val target = CollectionResumePolicy.resolveFallbackAid(
            fromAid = 111L,
            lastPlayedAid = 0L,
            memberAids = listOf(111L, 222L)
        )
        assertEquals(111L, target)
    }

    @Test
    fun 记录aid非法_回落入口视频() {
        val target = CollectionResumePolicy.resolveTargetAid(
            fromAid = 111L,
            seasonId = 9001L,
            record = record(9001L, 0L),
            memberAids = listOf(111L, 0L, 222L)
        )
        assertEquals("aid 为 0 的记录是垃圾，不该被当成目标", 111L, target)
    }

    // ---------- 编解码：坏数据只能退化成「没有记录」 ----------

    @Test
    fun 编解码往返_三个字段都还原() {
        val decoded = CollectionResumeCodec.decode(CollectionResumeCodec.encode(record(9001L, 222L)))
        assertEquals(record(9001L, 222L), decoded)
    }

    @Test
    fun 坏数据_一律解析成没有记录() {
        assertNull("空串", CollectionResumeCodec.decode(""))
        assertNull("null", CollectionResumeCodec.decode(null))
        assertNull("只有一段", CollectionResumeCodec.decode("222"))
        assertNull("两段", CollectionResumeCodec.decode("9001\t222"))
        assertNull("aid 不是数字", CollectionResumeCodec.decode("9001\tabc\t1700000000000"))
        assertNull("seasonId 不是数字", CollectionResumeCodec.decode("x\t222\t1700000000000"))
        assertNull("seasonId 非正", CollectionResumeCodec.decode("-1\t222\t1700000000000"))
        assertNull("aid 非正", CollectionResumeCodec.decode("9001\t-5\t1700000000000"))
    }

    @Test
    fun updatedAt坏掉_仍按有效记录处理() {
        // updatedAt 只是排查信息，坏了不该连定位一起失效
        val decoded = CollectionResumeCodec.decode("9001\t222\tnot-a-number")
        assertEquals(9001L, decoded?.seasonId)
        assertEquals(222L, decoded?.aid)
        assertEquals(0L, decoded?.updatedAt)
    }

    // ---------- 落盘：按 seasonId 分开存 ----------

    @Test
    fun 不同seasonId互不干扰() {
        CollectionResumeStore.save(9001L, 222L)
        CollectionResumeStore.save(9002L, 333L)

        assertEquals(222L, CollectionResumeStore.read(9001L)?.aid)
        assertEquals(333L, CollectionResumeStore.read(9002L)?.aid)
        assertNull("另一个没看过的合集不该读到别人的进度", CollectionResumeStore.read(9003L))
    }

    @Test
    fun 同一seasonId反复写入_以最后一次为准() {
        CollectionResumeStore.save(9001L, 222L)
        CollectionResumeStore.save(9001L, 444L)
        assertEquals(444L, CollectionResumeStore.read(9001L)?.aid)
    }

    @Test
    fun 无seasonId时写降级槽_不再静默丢弃() {
        // ugc_season 拿不到 id 时填 -1：按 seasonId 的键写不了，但这类合集最容易漏记，
        // 必须退到「最近一次在无 id 合集里看到哪一集」的全局槽。
        CollectionResumeStore.save(-1L, 222L)
        CollectionResumeStore.save(0L, 333L)

        assertEquals("降级槽只保留最近一次", 333L, CollectionResumeStore.lastPlayed())
        assertNull(CollectionResumeStore.read(-1L))
        assertNull(CollectionResumeStore.read(0L))
    }

    @Test
    fun 正常的seasonId写入不碰降级槽() {
        CollectionResumeStore.saveLastPlayed(111L)
        CollectionResumeStore.save(9001L, 222L)
        assertEquals("有 id 的合集走自己的键，降级槽保持原值", 111L, CollectionResumeStore.lastPlayed())
    }

    @Test
    fun 非法aid一律不落盘_免得留下永远匹配不上的垃圾() {
        CollectionResumeStore.save(9001L, 0L)
        CollectionResumeStore.save(0L, -5L)
        CollectionResumeStore.saveLastPlayed(0L)

        assertNull(CollectionResumeStore.read(9001L))
        assertEquals("aid 非正时哪一格都不该写", 0L, CollectionResumeStore.lastPlayed())
    }

    @Test
    fun 降级槽的坏数据当作没有记录() {
        SharedPreferencesUtil.putString("collection_resume_last_played_v1", "坏掉的值")
        assertEquals(0L, CollectionResumeStore.lastPlayed())
    }

    @Test
    fun 读到的坏数据当作没有记录() {
        SharedPreferencesUtil.putString(CollectionResumeStore.key(9001L), "坏掉的值")
        assertNull(CollectionResumeStore.read(9001L))
    }
}
