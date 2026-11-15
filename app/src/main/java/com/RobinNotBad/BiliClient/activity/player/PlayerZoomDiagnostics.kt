package com.RobinNotBad.BiliClient.activity.player

import com.RobinNotBad.BiliClient.util.UiText

/**
 * 播放页「视频缩放有时候失效」的诊断拼装（反馈 #128 / #102）——**只观测，不改任何缩放行为**。
 *
 * 两位用户报的是同一件事：捏合有时能放大、有时没反应，换显示方式也一样。代码里的缩放链路
 * （设置 → [android.view.ScaleGestureDetector] → [ViewScaleGestureListener] → `layout_video.scaleX/scaleY`）
 * 是完整的，也找不出必然失效的分支，已知的两条合法「捏合没反应」路径（听视频模式下 `layout_video`
 * 被 GONE、锁定态下 `control_layout` 被 GONE）解释不了「有时可以有时不行」。所以这一轮不加修复逻辑，
 * 只做「下次复现时能把现场留下来」：
 *
 * - 开关沿用已有的 [com.RobinNotBad.BiliClient.util.SettingsKeys.PLAYER_INTERACTION_DEBUG]，
 *   **不新造开关**；关掉时播放页一行都不打（见 `PlayerActivity.zoomDiagOn()` 的调用点）；
 * - 这里只放**纯函数**：状态判读（[blockers] / [verdict]）、日志行（[downLine] / [moveLine] /
 *   [summaryLine]）、以及「一键取证」要复制的整段文本（[report]）。播放页八千多行、跑不了普通 JVM 单测，
 *   把这些逻辑抽到这里才能把各种组合固定下来（见 `PlayerZoomDiagnosticsTest`）；
 * - 本文件**不 import 任何 android.* 类型**，现场值全由调用方读好后传进来，这样单测里不需要模拟 View。
 *
 * 判读的只有「已知的阻断条件」：说「没发现已知的阻断条件」不等于缩放正常，那正是还需要现场字段的情形。
 */
object PlayerZoomDiagnostics {

    /** 「没有」的占位：现场某一段为空时用它，免得诊断文本里出现空行让人以为漏打了。 */
    const val NONE = "—"

    /**
     * 文案走 [UiText.of]：命中的进 `strings.xml`，单测里没有 Application 时原样回落。
     *
     * 外面再包一层 `try`：单测环境的 `BiliTerminal` 静态初始化可能碰到 `Stub!` 异常，
     * 一条诊断文案不该让测试变红。
     */
    fun text(raw: String): String = try {
        UiText.of(raw)
    } catch (t: Throwable) {
        raw
    }

    /** 存档值：来自 SharedPreferences，与播放页是否还在无关，所以退出播放页后照样能复制出来。 */
    data class Saved(
        /** [com.RobinNotBad.BiliClient.util.SettingsKeys.PLAYER_SCALE]：开关关掉时手势层根本不建 ScaleGestureDetector。 */
        val scaleEnabled: Boolean,
        /** [com.RobinNotBad.BiliClient.util.SettingsKeys.PLAYER_DISPLAY] 的**原始存档字符串**（存档类型可能是布尔也可能是字符串）。 */
        val displayValue: String,
        /** [com.RobinNotBad.BiliClient.util.SettingsKeys.PLAYER_DOUBLEMOVE]：缩放后能否拖动画面。 */
        val doubleMoveEnabled: Boolean
    )

    /**
     * 现场状态：全部由调用方读好后传进来，这里不碰 View、不碰 SharedPreferences。
     *
     * 计数字段来自 [ViewScaleGestureListener] 的诊断计数，每次手势开始时清零，
     * 用来回答「捏合回调到底有没有被调用」——这是「有时可以有时不行」最需要的一条证据。
     */
    data class Snapshot(
        /** `scaleGestureDetector != null`：开关是进页面时读的，进页面后才改开关会出现「设置开了但没生效」。 */
        val detectorCreated: Boolean,
        /** 听视频模式：`layout_video` 被设为 GONE，[ViewScaleGestureListener.onScale] 会直接 return false。 */
        val audioOnly: Boolean,
        /** 锁定态：手势与按钮输入都被锁定逻辑挡住（此时 `control_layout` 通常是 GONE）。 */
        val locked: Boolean,
        /** `layout_video` 的 visibility 文字（`VISIBLE` / `GONE` / `INVISIBLE`）。 */
        val videoVisibility: String,
        /** `control_layout` 的 visibility 文字；它 GONE 时触摸事件到不了手势层。 */
        val controlVisibility: String,
        /** `isVideoZoomed()` 的判读（[PlayerZoomPolicy.isZoomed]）。 */
        val zoomed: Boolean,
        /** 画面实际缩放比例（不是手势里的临时标志）。 */
        val scaleX: Float,
        val scaleY: Float,
        val scaleBeginCount: Int = 0,
        val scaleCount: Int = 0,
        val scaleEndCount: Int = 0,
        /** [ViewScaleGestureListener.onScale] 因 `view.visibility == GONE` 而放弃的次数。 */
        val scaleIgnoredCount: Int = 0,
        /** 最近一次手势最终落在哪个分支。 */
        val branch: PlayerTouchBranch = PlayerTouchBranch.NONE,
        /** 这次事件被哪个分支提前 return 抢走了（点弹幕 / 长按切视频），没被抢走则为 null。 */
        val swallowedBy: PlayerTouchBranch? = null,
        /** 附加备注，例如「横滑门闸因未缩放而关闭」之类，由调用方按需填。 */
        val note: String = ""
    )

    /**
     * 已知的阻断条件：按「先看开关 → 再看触摸能不能进手势层 → 最后看回调有没有被忽略」的顺序判。
     *
     * 返回值是给人读的句子（要贴给开发者的那段文字），空列表表示没发现已知问题。
     */
    fun blockers(saved: Saved, snapshot: Snapshot?): List<String> {
        val out = ArrayList<String>(4)
        if (!saved.scaleEnabled) {
            out.add(text("缩放开关（设置 → 播放器 → 双指缩放视频）是关的：手势层没有创建 ScaleGestureDetector，必然捏合没反应"))
        } else if (snapshot != null && !snapshot.detectorCreated) {
            out.add(text("缩放开关是开的，但 scaleGestureDetector 为 null：多半是进页面之后才改的开关，本次播放仍按旧值"))
        }
        if (snapshot == null) return out
        if (snapshot.audioOnly) {
            out.add(text("听视频模式：layout_video 被设为 GONE，捏合回调会被手势监听器直接忽略"))
        } else if (snapshot.videoVisibility != "VISIBLE") {
            out.add(text("layout_video 当前不是 VISIBLE，捏合回调会被手势监听器直接忽略"))
        }
        if (snapshot.locked) {
            out.add(text("播放页处于锁定态：手势与按钮输入都被锁定逻辑挡住"))
        } else if (snapshot.controlVisibility == "GONE") {
            out.add(text("control_layout 当前为 GONE：触摸事件到不了手势层"))
        }
        return out
    }

    /**
     * 一句话结论。
     *
     * 有阻断条件就逐条列出来；没有就说「没发现已知的阻断条件」——**这不是「缩放正常」**，
     * 而是「代码里的合法路径解释不了」，正好是需要 [Snapshot] 里那些现场字段的场合。
     */
    fun verdict(saved: Saved, snapshot: Snapshot?): String {
        val list = blockers(saved, snapshot)
        if (list.isNotEmpty()) return list.joinToString("；")
        if (snapshot == null) return text("没有现场快照，只能确认设置存档：缩放链路本身没看到开关层面的阻断")
        if (saved.scaleEnabled && snapshot.detectorCreated && snapshot.scaleBeginCount == 0) {
            return text("没发现已知的阻断条件，但最近一次手势没有触发 onScaleBegin：可能只是没捏合，也可能是双指事件没送到手势层")
        }
        return text("没发现已知的阻断条件，链路看起来是通的（这正是需要现场字段的情形）")
    }

    /** 存档一行：`key=value` 形式，方便在 logcat 里 grep，也方便贴进反馈里。 */
    fun savedLine(saved: Saved): String {
        val display = if (saved.displayValue.isEmpty()) text("（未设置）") else saved.displayValue
        return "player_scale=${saved.scaleEnabled}" +
            " player_display=$display" +
            " player_doublemove=${saved.doubleMoveEnabled}"
    }

    /** 现场关键状态一行。字段名保持 ASCII，只有人类判读的句子才走 [text]。 */
    fun fieldLine(snapshot: Snapshot): String =
        "detector=${if (snapshot.detectorCreated) "created" else "null"}" +
            " audioOnly=${snapshot.audioOnly}" +
            " locked=${snapshot.locked}" +
            " video=${snapshot.videoVisibility}" +
            " control=${snapshot.controlVisibility}" +
            " zoomed=${snapshot.zoomed}" +
            " scale=${ratio(snapshot.scaleX)}x${ratio(snapshot.scaleY)}"

    /** 捏合回调计数一行：回答「onScaleBegin/onScale/onScaleEnd 到底有没有被调用」。 */
    fun counterLine(snapshot: Snapshot): String =
        "onScaleBegin=${snapshot.scaleBeginCount}" +
            " onScale=${snapshot.scaleCount}" +
            " onScaleEnd=${snapshot.scaleEndCount}" +
            " ignoredByGone=${snapshot.scaleIgnoredCount}"

    /** 分支一行：这次手势落在哪个分支、有没有被提前 return 抢走。 */
    fun branchLine(snapshot: Snapshot): String {
        val swallowed = snapshot.swallowedBy?.label ?: NONE
        val base = "branch=${snapshot.branch.label} swallowedBy=$swallowed"
        return if (snapshot.note.isEmpty()) base else "$base note=${snapshot.note}"
    }

    /** 手势开始（ACTION_DOWN）时打一条：现场 + 计数器清零后的初值。 */
    fun downLine(saved: Saved, snapshot: Snapshot): String =
        "[zoom] DOWN ${fieldLine(snapshot)} | ${counterLine(snapshot)} | ${savedLine(saved)}"

    /** MOVE 期间按节流打：只需要知道这一条 MOVE 被谁吃掉了。 */
    fun moveLine(snapshot: Snapshot): String =
        "[zoom] MOVE ${branchLine(snapshot)} | zoomed=${snapshot.zoomed}" +
            " scale=${ratio(snapshot.scaleX)}x${ratio(snapshot.scaleY)}"

    /** 手势结束时打一条摘要（**每次手势只有一条**，不是每个 MOVE 一条）。 */
    fun summaryLine(saved: Saved, snapshot: Snapshot): String =
        "[zoom] END ${branchLine(snapshot)} | ${counterLine(snapshot)} | ${fieldLine(snapshot)} | ${verdict(saved, snapshot)}"

    /**
     * 「一键取证」复制的整段文本。
     *
     * [stamp] 由调用方给（本类不读时间，保持纯函数）：`yyyy-MM-dd HH:mm:ss`。
     * [snapshot] 为 null 表示这次打开应用后还没进过播放页（或已退出），此时只有存档值可看。
     */
    fun report(saved: Saved, snapshot: Snapshot?, stamp: String): String {
        val sb = StringBuilder(512)
        sb.append(text("RE:哔哩终端 · 播放器缩放诊断")).append('\n')
        sb.append("time=").append(stamp).append('\n')
        sb.append(savedLine(saved)).append('\n')
        if (snapshot == null) {
            sb.append(text("没有现场快照：本次打开应用后还没进过播放页，或已退出播放页")).append('\n')
        } else {
            sb.append(fieldLine(snapshot)).append('\n')
            sb.append(counterLine(snapshot)).append('\n')
            sb.append(branchLine(snapshot)).append('\n')
        }
        sb.append("verdict=").append(verdict(saved, snapshot))
        return sb.toString()
    }

    /** 缩放比例固定两位小数：诊断文本里 `1.00x1.00` 比 `1.0000001` 好读，也方便肉眼比对。 */
    private fun ratio(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)
}

/**
 * 一次触摸最终落在哪个手势分支，只用于诊断文字。
 *
 * 顺序与 [PlayerActivity] 触摸监听里的判定顺序一致：点弹幕空降 / 长按后纵向划切视频**先于**
 * 缩放与横滑判定处理，所以「捏合没反应」有时是被前面两个分支提前 return 抢走了。
 */
enum class PlayerTouchBranch(private val rawLabel: String) {
    /** 点弹幕空降时间（`handleDanmakuTimeJumpTap`）。 */
    DANMAKU_TAP("点弹幕空降"),

    /** 长按后纵向划切上/下一条（`handleLongPressSwitchTouch`）。 */
    LONG_PRESS_SWITCH("长按后纵向划切视频"),

    /** 左右滑动调进度（`handleSwipeSeekTouch`，门闸见 [PlayerSwipeSeekPolicy]）。 */
    SWIPE_SEEK("左右滑动调进度"),

    /** 双击（快进/快退/复位缩放）。 */
    DOUBLE_TAP("双击"),

    /** 捏合缩放（[ViewScaleGestureListener]）。 */
    SCALE("捏合缩放"),

    /** 单指/双指拖动画面（[PlayerActivity.videoMoveBy]）。 */
    DRAG("拖动画面"),

    /** 这次事件没被任何分支消费。 */
    NONE("未消费");

    /** 显示用文字（走 `strings.xml`，单测里回落成上面的原文）。 */
    val label: String
        get() = PlayerZoomDiagnostics.text(rawLabel)
}

/**
 * 最近一次播放页现场快照。
 *
 * 设置页的「复制播放器诊断信息」要能复制到**出问题时那一次播放**的状态，而那时播放页已经退出了，
 * 所以由播放页在每次手势结束/状态变化时写进来，设置页读走。只有一个字段，用 `@Volatile` 保证可见性。
 */
object PlayerZoomLastSnapshot {
    @Volatile
    var last: PlayerZoomDiagnostics.Snapshot? = null
}
