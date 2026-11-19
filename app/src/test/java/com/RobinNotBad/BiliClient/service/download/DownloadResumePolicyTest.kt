package com.RobinNotBad.BiliClient.service.download

import com.RobinNotBad.BiliClient.service.download.DownloadResumePolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 断点续传判定（反馈 #250 / #252 / #253）。
 *
 * 每条用例都对应一个真实后果：判成 FRESH 就是用户白白重下几十分钟，判成 APPEND 而文件其实
 * 不是同一份数据，拼出来的就是放不了的视频——所以"什么时候不能信这个文件"要锁死。
 */
class DownloadResumePolicyTest {

    private val tag = DownloadResumePolicy.tag(170001, 300002, 80, DownloadResumePolicy.STREAM_VIDEO)

    private fun record(total: Long, segments: List<Long> = emptyList(), tag: String = this.tag) =
        DownloadResumePolicy.Record(tag, total, segments)

    @Test
    fun 没有存档就从头下() {
        val plan = DownloadResumePolicy.singlePlan(null, tag, 1234L)
        assertEquals(Action.FRESH, plan.action)
        assertEquals(0L, plan.offset)
    }

    @Test
    fun 存档属于别的画质就当没有() {
        val other = DownloadResumePolicy.tag(170001, 300002, 32, DownloadResumePolicy.STREAM_VIDEO)
        val plan = DownloadResumePolicy.singlePlan(record(1000L, tag = other), tag, 500L)
        assertEquals(Action.FRESH, plan.action)
    }

    @Test
    fun 半成品接着写而不是重下() {
        // 反馈 #252「暂停后再开始会从头下载」的现场
        val plan = DownloadResumePolicy.singlePlan(record(1000L), tag, 400L)
        assertEquals(Action.APPEND, plan.action)
        assertEquals(400L, plan.offset)
    }

    @Test
    fun 文件已经写满就直接算完成() {
        val plan = DownloadResumePolicy.singlePlan(record(1000L), tag, 1000L)
        assertEquals(Action.COMPLETE, plan.action)
        assertEquals(1000L, plan.offset)
    }

    @Test
    fun 文件比总长还大说明不是同一份数据() {
        assertEquals(Action.FRESH, DownloadResumePolicy.singlePlan(record(1000L), tag, 1200L).action)
    }

    @Test
    fun 空文件不值得续传() {
        assertEquals(Action.FRESH, DownloadResumePolicy.singlePlan(record(1000L), tag, 0L).action)
    }

    @Test
    fun 分片模式的存档不能拿来当单连接续传() {
        // 分片模式下文件被 setLength 预分配成整长，长度不能当已下载量
        val plan = DownloadResumePolicy.singlePlan(record(1000L, listOf(250L, 250L)), tag, 1000L)
        assertEquals(Action.FRESH, plan.action)
    }

    @Test
    fun 总长未知的存档不续传() {
        assertEquals(Action.FRESH, DownloadResumePolicy.singlePlan(record(0L), tag, 100L).action)
    }

    @Test
    fun 分片偏移能原样取回() {
        val offsets = DownloadResumePolicy.segmentOffsets(record(1000L, listOf(200L, 300L)), tag, 1000L, 2)
        assertEquals(listOf(200L, 300L), offsets!!.toList())
    }

    @Test
    fun 总长变了就整份作废() {
        assertNull(DownloadResumePolicy.segmentOffsets(record(1000L, listOf(200L, 300L)), tag, 999L, 2))
    }

    @Test
    fun 片数变了就整份作废() {
        assertNull(DownloadResumePolicy.segmentOffsets(record(1000L, listOf(200L, 300L)), tag, 1000L, 4))
    }

    @Test
    fun 偏移总和超过总长是脏数据() {
        assertNull(DownloadResumePolicy.segmentOffsets(record(1000L, listOf(600L, 600L)), tag, 1000L, 2))
    }

    @Test
    fun 负数偏移是脏数据() {
        assertNull(DownloadResumePolicy.segmentOffsets(record(1000L, listOf(-1L, 300L)), tag, 1000L, 2))
    }

    @Test
    fun 每片都写满才算分片下载完成() {
        assertTrue(DownloadResumePolicy.isComplete(record(1000L, listOf(500L, 500L)), tag, 1000L))
        assertFalse(DownloadResumePolicy.isComplete(record(1000L, listOf(500L, 400L)), tag, 1000L))
        assertFalse(DownloadResumePolicy.isComplete(record(1000L), tag, 1000L))
    }

    @Test
    fun 存档能原样往返() {
        val original = record(1000L, listOf(200L, 300L))
        val restored = DownloadResumePolicy.fromJson(DownloadResumePolicy.toJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun 单连接存档往返后仍能续传() {
        val original = record(1000L)
        val restored = DownloadResumePolicy.fromJson(DownloadResumePolicy.toJson(original))
        assertEquals(Action.APPEND, DownloadResumePolicy.singlePlan(restored, tag, 128L).action)
    }

    @Test
    fun 脏数据一律当成没有存档() {
        assertNull(DownloadResumePolicy.fromJson(null))
        assertNull(DownloadResumePolicy.fromJson(""))
        assertNull(DownloadResumePolicy.fromJson("不是 json"))
        assertNull(DownloadResumePolicy.fromJson(""))
        assertNull(DownloadResumePolicy.fromJson("[1,2,3]"))
        assertNull(DownloadResumePolicy.fromJson("""{"v":1,"total":100}"""))
        assertNull(DownloadResumePolicy.fromJson("""{"v":2,"tag":"t","total":100,"segments":[]}"""))
        assertNull(DownloadResumePolicy.fromJson("""{"v":1,"tag":"t","total":0,"segments":[]}"""))
        assertNull(DownloadResumePolicy.fromJson("""{"v":1,"tag":"t","total":100,"segments":[-5]}"""))
    }
}
