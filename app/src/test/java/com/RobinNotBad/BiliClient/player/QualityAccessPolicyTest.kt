package com.RobinNotBad.BiliClient.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 清晰度权限判据的单测。
 *
 * 这三种账号状态在设备上很难凑齐（尤其大会员），而判据一旦写反，用户看到的是
 * 「锁错档」或「点得动却播不出来」，所以钉死在纯逻辑层。
 */
class QualityAccessPolicyTest {

    @Test
    fun `未登录最高720P`() {
        val qn = QualityAccessPolicy.maxUnlockedQn(loggedIn = false, vip = false)
        assertEquals(64, qn)

        assertFalse(QualityAccessPolicy.isLocked(16, loggedIn = false, vip = false))   // 360P
        assertFalse(QualityAccessPolicy.isLocked(32, loggedIn = false, vip = false))   // 480P
        assertFalse(QualityAccessPolicy.isLocked(64, loggedIn = false, vip = false))   // 720P
        assertTrue(QualityAccessPolicy.isLocked(80, loggedIn = false, vip = false))    // 1080P
        assertTrue(QualityAccessPolicy.isLocked(116, loggedIn = false, vip = false))   // 1080P60
        assertTrue(QualityAccessPolicy.isLocked(120, loggedIn = false, vip = false))   // 4K
    }

    @Test
    fun `登录非大会员最高1080P`() {
        val qn = QualityAccessPolicy.maxUnlockedQn(loggedIn = true, vip = false)
        assertEquals(80, qn)

        assertFalse(QualityAccessPolicy.isLocked(64, loggedIn = true, vip = false))
        assertFalse(QualityAccessPolicy.isLocked(80, loggedIn = true, vip = false))
        assertTrue(QualityAccessPolicy.isLocked(116, loggedIn = true, vip = false))    // 1080P60 起要大会员
        assertTrue(QualityAccessPolicy.isLocked(120, loggedIn = true, vip = false))    // 4K
    }

    @Test
    fun `大会员全部解锁`() {
        assertEquals(
            Int.MAX_VALUE,
            QualityAccessPolicy.maxUnlockedQn(loggedIn = true, vip = true)
        )
        assertFalse(QualityAccessPolicy.isLocked(80, loggedIn = true, vip = true))
        assertFalse(QualityAccessPolicy.isLocked(116, loggedIn = true, vip = true))
        assertFalse(QualityAccessPolicy.isLocked(120, loggedIn = true, vip = true))
    }

    @Test
    fun `未登录不看大会员标记`() {
        // 退出登录后缓存里可能还留着「大会员」标记，不能因为旧缓存就解锁高档位
        assertEquals(64, QualityAccessPolicy.maxUnlockedQn(loggedIn = false, vip = true))
        assertTrue(QualityAccessPolicy.isLocked(80, loggedIn = false, vip = true))
    }

    @Test
    fun `720P判据与取流判据一致`() {
        assertEquals(PlayerStreamPolicy.QN_720P, QualityAccessPolicy.QN_720P)
    }

    @Test
    fun `未登录时补4K锁定提示项`() {
        // 服务端不会把 120 下发给匿名账号，面板里要自己补一个锁定项，用户才知道能开会员解锁
        val serverList = intArrayOf(116, 80, 64, 32, 16)
        assertTrue(QualityAccessPolicy.needsLocked4kHint(serverList, loggedIn = false, vip = false))
    }

    @Test
    fun `非大会员时也补4K锁定提示项`() {
        val serverList = intArrayOf(116, 80, 64, 32, 16)
        assertTrue(QualityAccessPolicy.needsLocked4kHint(serverList, loggedIn = true, vip = false))
    }

    @Test
    fun `大会员不补假的4K`() {
        // 有权限时服务端会真的下发 120；没下发就说明这条视频没有 4K 源，硬补会造出假档位
        val with4k = intArrayOf(120, 116, 80, 64, 32, 16)
        val without4k = intArrayOf(116, 80, 64, 32, 16)
        assertFalse(QualityAccessPolicy.needsLocked4kHint(with4k, loggedIn = true, vip = true))
        assertFalse(QualityAccessPolicy.needsLocked4kHint(without4k, loggedIn = true, vip = true))
    }

    @Test
    fun `已经下发4K时不再重复补项`() {
        // 例如大会员短时间掉权限后缓存里仍留着真实 120，这时补第二项就重复了
        val with4k = intArrayOf(120, 80, 64, 32, 16)
        assertFalse(QualityAccessPolicy.needsLocked4kHint(with4k, loggedIn = false, vip = false))
    }

    @Test
    fun `大会员徽标判据就是已登录非大会员的那条线`() {
        // 取流接口（accept_quality / support_formats）里没有 vip 字段，徽标只能按 qn 判。
        // 判据复用 maxUnlockedQn(loggedIn=true, vip=false) = 1080P，不另立阈值。
        val free = intArrayOf(16, 32, 64, 74, 80)
        val vip = intArrayOf(100, 112, 116, 120, 125, 126, 127, 129)
        free.forEach { assertFalse("qn=$it 不该挂大会员徽标", QualityAccessPolicy.requiresVip(it)) }
        vip.forEach { assertTrue("qn=$it 该挂大会员徽标", QualityAccessPolicy.requiresVip(it)) }
        // 与「锁不锁」保持同一条线：登录非大会员时锁住的档，正是要挂徽标的档。
        (free + vip).forEach {
            assertEquals(
                QualityAccessPolicy.isLocked(it, loggedIn = true, vip = false),
                QualityAccessPolicy.requiresVip(it),
            )
        }
    }
}
