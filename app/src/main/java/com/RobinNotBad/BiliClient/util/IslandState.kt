package com.RobinNotBad.BiliClient.util

/**
 * 顶栏灵动岛当前展示的状态。
 *
 * 只描述「岛上正在显示什么」，不含业务数据；具体文案与颜色在
 * `ui/widget/TopbarIsland` 里按状态映射。
 */
enum class IslandState {
    /** 空闲：显示时间。 */
    TIME,

    /** 后台播放中。 */
    PLAYING,

    /** 下载中，显示总进度百分比。 */
    DOWNLOADING,

    /** 有未读消息。 */
    UNREAD,

    /** 低电量 / 充电（瞬时事件，10 秒后自动让位）。 */
    BATTERY,

    /** 新公告 / 新版本（瞬时事件，10 秒后自动让位）。 */
    ANNOUNCEMENT
}

/**
 * 一次状态采样。
 *
 * 刻意做成纯数据：`IslandStatus` 负责从各模块读值，`IslandPolicy` 只做纯函数判断，
 * JVM 单测不需要 Android 环境。
 */
data class IslandSnapshot(
    val playing: Boolean = false,
    val downloading: Boolean = false,
    val downloadPercent: Int = -1,
    val unread: Int = 0,
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val burst: IslandState? = null,
    /** 当前这条瞬时事件是「新版本」而不是「公告」。 */
    val burstIsUpdate: Boolean = false
)
