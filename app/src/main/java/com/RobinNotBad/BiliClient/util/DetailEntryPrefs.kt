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

    /** 「点赞 / 投币 / 收藏」卡片主色描边的默认值：开（= 保持既有观感，不改变老用户的界面）。 */
    const val DEFAULT_LIKE_CARD_RING = true

    /**
     * 动作区卡片是否画那一圈主色描边。
     *
     * 反馈 #112：描边色是 `?attr/actionCardStroke`，B站粉与经典终端两套主题下都是 #FF6699。
     * 关掉只是不画边框：卡片底色（`?attr/actionCardColor`）与文字色不变，可读性不受影响。
     * 调用点有两处——视频详情页（`VideoInfoFragment`）与文章/动态末尾的动作卡片
     * （`OpusContentAdapter`），同一个开关必须两边都生效，否则会出现「视频页关了、
     * 文章页还留一圈」的不一致。
     */
    fun isLikeCardRingEnabled(): Boolean =
        SharedPreferencesUtil.getBoolean(SettingsKeys.DETAIL_LIKE_CARD_RING, DEFAULT_LIKE_CARD_RING)
}
