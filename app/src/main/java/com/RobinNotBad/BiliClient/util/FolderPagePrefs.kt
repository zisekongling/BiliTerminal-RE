package com.RobinNotBad.BiliClient.util

/**
 * 「缓存页文件夹入口」开关（设置 → 高级与实验 → 功能开关）。
 *
 * <h3>它决定什么</h3>
 * 缓存页（[com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity] 顶层）现在把文件夹
 * 铺在列表里：下载列表入口 → 文件夹分区 → 新建文件夹 → 未分类视频。文件夹一多，光文件夹就占满
 * 一整屏，未分类视频被挤到要滑很久才看得见。
 *
 * 打开本开关后，缓存页**不再渲染文件夹分区与「新建文件夹」行**，改在最上面多一行
 * 「文件夹 N」，点它进独立的文件夹列表页（[com.RobinNotBad.BiliClient.activity.video.local.LocalFolderListActivity]）。
 * 关闭时缓存页一行都不变，与开启前完全一致。
 *
 * <h3>为什么默认关闭</h3>
 * 独立文件夹页是新入口，文件夹少的人反而多一次点击才能进夹；默认沿用老界面，
 * 觉得挤的人自己去实验性开关里打开。
 *
 * <h3>键为什么定义在这里</h3>
 * 与 `VirtualCollectionPrefs` 同一个理由：默认值散到多个调用点后，改漏一处的症状是
 * 「设置页显示开着、实际却是关的」——不报错、只能靠人肉比对发现。这个键只在
 * [com.RobinNotBad.BiliClient.adapter.video.CacheListAdapter] 和设置页两处用到，
 * 直接收在本门面里，不占 `SharedPreferencesUtil` 的常量表。
 */
object FolderPagePrefs {

    /** 开关在 SharedPreferences 里的键。 */
    const val ENABLE = "cache_folder_page_enable"

    /** 默认关闭：默认界面与加开关之前完全一致。 */
    const val DEFAULT_ENABLE = false

    /** 缓存页是否用「文件夹 N」入口 + 独立文件夹页。 */
    @JvmStatic
    fun enabled(): Boolean = SharedPreferencesUtil.getBoolean(ENABLE, DEFAULT_ENABLE)
}
