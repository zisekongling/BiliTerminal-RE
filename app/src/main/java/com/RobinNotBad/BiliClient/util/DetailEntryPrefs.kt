package com.RobinNotBad.BiliClient.util

/**
 * 视频详情页「动作区」两个可选入口的显隐判据。
 *
 * 26.11.07 用户要求把详情页的两个入口默认藏起来：
 * - 「笔记」（公共功能，进 `NoteActivity`）；
 * - 「分享到其他设备」整行（私有功能，由 [OptionalFeatures.attachDetailActionRow] 反射插入）。
 *
 * 两个都做成设置开关、**默认关**（= 默认不显示）。默认值与判据集中在这里，
 * 避免设置页、详情页、全局搜索索引各写一份 `false`——那种分散最容易出现
 * 「设置页显示关着、详情页却按开处理」的不一致。
 */
object DetailEntryPrefs {

    /** 「笔记」按钮的默认值：关。 */
    const val DEFAULT_NOTE_ENABLED = false

    /** 「分享到其他设备」整行的默认值：关。 */
    const val DEFAULT_SHARE_ROW_ENABLED = false

    /**
     * 是否显示「笔记」按钮。
     *
     * 只是其中一个条件：未登录时仍然恒隐藏（笔记要 SESSDATA），调用点要两者同时满足。
     */
    fun isNoteEntryEnabled(): Boolean =
        SharedPreferencesUtil.getBoolean(SettingsKeys.DETAIL_NOTE_ENTRY, DEFAULT_NOTE_ENABLED)

    /**
     * 是否插入「分享到其他设备」整行。
     *
     * 公开构建里根本没有这一行（[OptionalFeatures.available] 为 false），这里读到的值只是
     * 一份用不上的配置；设置页也只在私有构建里显示这个开关。
     */
    fun isShareRowEnabled(): Boolean =
        SharedPreferencesUtil.getBoolean(SettingsKeys.DETAIL_SHARE_ROW, DEFAULT_SHARE_ROW_ENABLED)
}
