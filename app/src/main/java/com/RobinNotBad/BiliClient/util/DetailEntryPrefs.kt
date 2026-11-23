package com.RobinNotBad.BiliClient.util

/**
 * 视频详情页「动作区」两个可选入口的显隐判据。
 *
 * 26.11.07 用户要求把详情页的两个入口藏起来，于是都做成设置开关：
 * - 「笔记」（公共功能，进 `NoteActivity`）；
 * - 「分享到其他设备」整行（私有功能，由 [OptionalFeatures.attachDetailActionRow] 反射插入）。
 *
 * 26.11.08 用户要求「分享到其他设备」改回**默认显示**：默认值翻成开，开关本身保留（想藏的人还能关）。
 * 26.11.23 「笔记」也翻成**默认显示**（笔记页改成公开笔记列表后免登录也能看，没有理由再藏）。
 * 默认值与判据集中在这里，
 * 避免设置页、详情页、全局搜索索引各写一份 `false`——那种分散最容易出现
 * 「设置页显示关着、详情页却按开处理」的不一致。
 */
object DetailEntryPrefs {

    /** 「笔记」按钮的默认值：开（26.11.23 起）。 */
    const val DEFAULT_NOTE_ENABLED = true

    /**
     * 「分享到其他设备」整行的默认值：开。
     *
     * 26.11.08 用户要求改回默认显示（26.11.07 那次藏的）。开关还在，关掉即不显示、也不做反射。
     */
    const val DEFAULT_SHARE_ROW_ENABLED = true

    /**
     * 是否显示「笔记」按钮。
     *
     * 26.11.23 起不再与登录态挂钩：笔记页的主体是**公开笔记列表**（免登录可看），
     * 只有「我的笔记」那一行按登录态决定显不显示，所以调用点只看这个开关。
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
