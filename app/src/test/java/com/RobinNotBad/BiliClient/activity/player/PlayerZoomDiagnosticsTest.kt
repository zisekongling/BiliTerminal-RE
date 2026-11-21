package com.RobinNotBad.BiliClient.activity.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerZoomDiagnostics] 的纯 JVM 单测。
 *
 * 覆盖反馈 #128 / #102 的判读逻辑：两位用户报的是「捏合有时能放大、有时没反应」，
 * 这一轮只加观测，所以这里固定的是**诊断说了什么**——听视频模式、锁定态、缩放开关关闭、
 * 正常可缩放四种现场各自的结论，以及「一键取证」复制出来的那段文本必须带齐关键字段。
 *
 * 注意：这里断言的是「有没有指出某个阻断条件」，不是「缩放能不能用」。
 * 说「没发现已知的阻断条件」的用例同时也测了另一件事——结论不能冒充「缩放正常」。
 */
class PlayerZoomDiagnosticsTest {

    /** 默认存档：缩放开着、显示方式未设置、缩放后可拖动。 */
    private fun saved(
        scaleEnabled: Boolean = true,
        displayValue: String = "",
        doubleMoveEnabled: Boolean = true,
    ) = PlayerZoomDiagnostics.Saved(scaleEnabled, displayValue, doubleMoveEnabled)

    /** 默认现场：普通模式、未锁定、两层都可见、未缩放、比例 1:1。 */
    private fun snapshot(
        detectorCreated: Boolean = true,
        audioOnly: Boolean = false,
        locked: Boolean = false,
        videoVisibility: String = "VISIBLE",
        controlVisibility: String = "VISIBLE",
        zoomed: Boolean = false,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        scaleBeginCount: Int = 0,
        scaleCount: Int = 0,
        scaleEndCount: Int = 0,
        scaleIgnoredCount: Int = 0,
        branch: PlayerTouchBranch = PlayerTouchBranch.NONE,
        swallowedBy: PlayerTouchBranch? = null,
    ) = PlayerZoomDiagnostics.Snapshot(
        detectorCreated = detectorCreated,
        audioOnly = audioOnly,
        locked = locked,
        videoVisibility = videoVisibility,
        controlVisibility = controlVisibility,
        zoomed = zoomed,
        scaleX = scaleX,
        scaleY = scaleY,
        scaleBeginCount = scaleBeginCount,
        scaleCount = scaleCount,
        scaleEndCount = scaleEndCount,
        scaleIgnoredCount = scaleIgnoredCount,
        branch = branch,
        swallowedBy = swallowedBy,
    )

    @Test
    fun 听视频模式会被指出是阻断条件() {
        val list = PlayerZoomDiagnostics.blockers(
            saved(),
            snapshot(audioOnly = true, videoVisibility = "GONE")
        )
        assertEquals("听视频模式必须报出来，且只说这一条", 1, list.size)
        assertTrue("要提到 layout_video 被设为 GONE", list[0].contains("听视频模式"))
        assertTrue("要让开发者一眼看出是捏合回调被忽略", list[0].contains("忽略"))
    }

    @Test
    fun 画面层不可见同样算阻断() {
        // 不是听视频模式，但 layout_video 因为别的原因不是 VISIBLE：这是「有时可以有时不行」的重点怀疑对象
        val list = PlayerZoomDiagnostics.blockers(saved(), snapshot(videoVisibility = "GONE"))
        assertEquals(1, list.size)
        assertTrue(list[0].contains("不是 VISIBLE"))
    }

    @Test
    fun 锁定态会被指出是阻断条件() {
        // 锁定态下 control_layout 是 GONE，但结论应该是「锁定」而不是重复报一条可见性
        val list = PlayerZoomDiagnostics.blockers(
            saved(),
            snapshot(locked = true, controlVisibility = "GONE")
        )
        assertEquals("锁定态只报一条，不要和 control_layout 的可见性重复", 1, list.size)
        assertTrue(list[0].contains("锁定"))
    }

    @Test
    fun 控制栏被藏掉且未锁定时算阻断() {
        val list = PlayerZoomDiagnostics.blockers(saved(), snapshot(controlVisibility = "GONE"))
        assertEquals(1, list.size)
        assertTrue("要指名 control_layout", list[0].contains("control_layout"))
    }

    @Test
    fun 缩放开关关闭是第一条阻断() {
        val list = PlayerZoomDiagnostics.blockers(saved(scaleEnabled = false), snapshot(detectorCreated = false))
        assertEquals("开关关闭时只报这一条，不必再叠加 detector 为 null", 1, list.size)
        assertTrue(list[0].contains("缩放开关"))
        assertTrue("要点明根本没创建 ScaleGestureDetector", list[0].contains("ScaleGestureDetector"))
    }

    @Test
    fun 开关开着但检测器为null说明改了开关没重进页面() {
        val list = PlayerZoomDiagnostics.blockers(saved(scaleEnabled = true), snapshot(detectorCreated = false))
        assertEquals(1, list.size)
        assertTrue(list[0].contains("scaleGestureDetector 为 null"))
    }

    @Test
    fun 正常可缩放时没有阻断条件但结论不等于缩放正常() {
        val list = PlayerZoomDiagnostics.blockers(saved(), snapshot())
        assertTrue("普通可缩放现场不该报阻断条件", list.isEmpty())

        // 这一场里捏合是成功过的（onScaleBegin 有计数），所以结论落在「链路看起来是通的」
        val verdict = PlayerZoomDiagnostics.verdict(saved(), snapshot(scaleBeginCount = 1))
        assertFalse("没有阻断条件也不能说缩放正常", verdict.contains("正常"))
        assertTrue("要说明这正是需要现场字段的情形", verdict.contains("现场字段"))
    }

    @Test
    fun 一次捏合都没触发onScaleBegin要单独点出来() {
        // 没阻断条件、但计数器全是 0：捏合事件可能压根没送到手势层，这是最有价值的一条证据
        val verdict = PlayerZoomDiagnostics.verdict(saved(), snapshot(scaleBeginCount = 0))
        assertTrue(verdict.contains("onScaleBegin"))
    }

    @Test
    fun 已经缩放成功过就不必怀疑链路() {
        val verdict = PlayerZoomDiagnostics.verdict(
            saved(),
            snapshot(zoomed = true, scaleX = 2f, scaleY = 2f, scaleBeginCount = 1, scaleEndCount = 1)
        )
        assertFalse("onScaleBegin 有计数时不该再怀疑事件没送到", verdict.contains("onScaleBegin"))
        assertTrue(verdict.contains("现场字段"))
    }

    @Test
    fun 没有现场快照时只凭存档下结论() {
        val verdict = PlayerZoomDiagnostics.verdict(saved(), null)
        assertTrue("要说明没有现场快照", verdict.contains("没有现场快照"))
        assertFalse("没有快照就说不出阻断条件", PlayerZoomDiagnostics.blockers(saved(), null).isNotEmpty())
    }

    @Test
    fun 存档行带齐三个键且未设置时有占位() {
        val line = PlayerZoomDiagnostics.savedLine(saved(displayValue = ""))
        assertTrue(line.contains("player_scale=true"))
        assertTrue(line.contains("player_doublemove=true"))
        assertTrue("显示方式为空要有占位，别让诊断文本看起来像漏打了", line.contains("player_display=（未设置）"))
    }

    @Test
    fun 存档行原样带出显示方式的原始字符串() {
        val line = PlayerZoomDiagnostics.savedLine(saved(displayValue = "SurfaceView"))
        assertTrue("要能看出来存档里到底存的是什么", line.contains("player_display=SurfaceView"))
    }

    @Test
    fun 现场行带齐关键字段() {
        val line = PlayerZoomDiagnostics.fieldLine(snapshot(scaleX = 2.5f, scaleY = 2.5f))
        listOf(
            "detector=created", "audioOnly=false", "locked=false", "video=VISIBLE",
            "control=VISIBLE", "zoomed=false", "scale=2.50x2.50"
        ).forEach { assertTrue("现场行缺字段：$it → $line", line.contains(it)) }
    }

    @Test
    fun 计数行带齐四个回调计数() {
        val line = PlayerZoomDiagnostics.counterLine(
            snapshot(scaleBeginCount = 1, scaleCount = 7, scaleEndCount = 1, scaleIgnoredCount = 3)
        )
        assertTrue(line.contains("onScaleBegin=1"))
        assertTrue(line.contains("onScale=7"))
        assertTrue(line.contains("onScaleEnd=1"))
        assertTrue(line.contains("ignoredByGone=3"))
    }

    @Test
    fun 分支行能看出被哪个分支抢走() {
        val line = PlayerZoomDiagnostics.branchLine(
            snapshot(branch = PlayerTouchBranch.NONE, swallowedBy = PlayerTouchBranch.LONG_PRESS_SWITCH)
        )
        assertTrue(line.contains("branch=未消费"))
        assertTrue("被抢走时要写清是哪个分支", line.contains("swallowedBy=长按后纵向划切视频"))
    }

    @Test
    fun 没被抢走时用占位符() {
        val line = PlayerZoomDiagnostics.branchLine(snapshot(branch = PlayerTouchBranch.SCALE))
        assertTrue(line.contains("branch=捏合缩放"))
        assertTrue(line.contains("swallowedBy=—"))
    }

    @Test
    fun 七个分支都有中文标签() {
        val labels = PlayerTouchBranch.values().map { it.label }
        assertEquals("分支数量变了要同步单测", 7, labels.size)
        assertTrue("多指拖动与捏合必须能区分", labels.contains("捏合缩放") && labels.contains("拖动画面"))
        assertTrue(labels.none { it.isBlank() })
    }

    @Test
    fun 导出的报告带齐关键字段() {
        val report = PlayerZoomDiagnostics.report(
            saved(displayValue = "TextureView"),
            snapshot(scaleBeginCount = 1, scaleCount = 4, scaleEndCount = 1, zoomed = true, scaleX = 2f, scaleY = 2f),
            "2026-11-15 10:00:00"
        )
        listOf(
            "播放器缩放诊断", "time=2026-11-15 10:00:00",
            "player_scale=true", "player_display=TextureView", "player_doublemove=true",
            "detector=created", "onScaleBegin=1", "verdict="
        ).forEach { assertTrue("报告缺字段：$it\n$report", report.contains(it)) }
    }

    @Test
    fun 没有快照时报告也要说明白() {
        val report = PlayerZoomDiagnostics.report(saved(), null, "2026-11-15 10:00:00")
        assertTrue(report.contains("没有现场快照"))
        assertTrue("存档值仍然要在报告里", report.contains("player_scale=true"))
        assertFalse("没有快照就不该出现现场字段", report.contains("detector="))
    }
}
