package com.RobinNotBad.BiliClient.util

import java.io.File

/**
 * 缓存页的两个开关（下面这份 KDoc 只讲第一个）：
 *
 * - [ENABLE]「缓存页文件夹入口」：设置 → 高级与实验 → 功能开关；
 * - [AUTO_REFRESH]「缓存页自动刷新」：设置 → 缓存与下载 → 缓存选项（反馈 #211，见下方它自己的 KDoc）。
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
 * <h3>为什么默认开启</h3>
 * 26.11.17 用户拍板改成默认开：文件夹铺在列表里时，文件夹一多就把「未分类视频」挤到要滑
 * 很久，而独立页顶多让文件夹少的人多一次点击。想要老界面的自己去设置里关掉。
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

    /** 默认开启（26.11.17 用户拍板；此前是默认关闭，想还原老界面的用户自己去设置里关）。 */
    const val DEFAULT_ENABLE = true

    /** 缓存页是否用「文件夹 N」入口 + 独立文件夹页。 */
    @JvmStatic
    fun enabled(): Boolean = SharedPreferencesUtil.getBoolean(ENABLE, DEFAULT_ENABLE)

    /**
     * 「缓存页自动刷新」开关（设置 → 缓存与下载 → 缓存选项；反馈 #211）。
     *
     * <h3>它决定什么</h3>
     * 打开（默认）时行为与以前**完全一致**：每次进
     * [com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity]（缓存页 / 文件夹页）与
     * [com.RobinNotBad.BiliClient.activity.video.local.LocalFolderListActivity] 都在 onCreate 里
     * 整盘扫一遍（LocalListActivity.onCreate 里的 CenterThreadPool.run、LocalFolderListActivity.onCreate 的 loadFolders）。
     * 关掉后进页面先比一次「缓存树指纹」（[CacheTreeSignature]）：指纹没变就直接沿用上次扫描结果，
     * 页面秒开；指纹变了（用户真的下过新视频 / 删过缓存）才重扫。
     *
     * <h3>为什么默认仍是自动</h3>
     * 反馈 #211 的用户要的是「可选」，不是把所有人的默认行为改掉：缓存页自动发现新下载是主流预期，
     * 所以默认值保持 true，不想要那几秒的人自己去设置里关。
     *
     * <h3>改完即生效</h3>
     * 设置页开关点一下就落盘（SettingsAdapter.kt:253-259），本页只在「进页面 / 下拉刷新」时读它
     * （LocalListActivity.onCreate、LocalFolderListActivity.loadFolders），所以拨完退回来立刻按新值走。
     */
    const val AUTO_REFRESH = "cache_page_auto_refresh"

    /** 默认开启：与「加开关之前的既有行为」一致。 */
    const val DEFAULT_AUTO_REFRESH = true

    /** 缓存页进页面时是否自动整盘重扫。 */
    @JvmStatic
    fun autoRefresh(): Boolean = SharedPreferencesUtil.getBoolean(AUTO_REFRESH, DEFAULT_AUTO_REFRESH)
}

/**
 * 缓存目录「指纹」：判断磁盘上的缓存自上次扫描以来有没有变过（反馈 #211 里用户提的「检测一下也行」）。
 *
 * <h3>为什么需要它</h3>
 * [FolderPagePrefs.autoRefresh] 关掉之后，两个缓存页直接沿用上次扫描结果（
 * LocalListActivity.restoreScanSnapshot() / LocalFolderListActivity.loadFolders()）。这条路必须能回答
 * 「上次扫完之后，用户有没有真的下载过新视频、删过缓存」——否则关掉开关就等于看不见新缓存，
 * 用户会以为「缓存没了」。而全盘重扫恰恰是贵的那一步：缓存页要对每个缓存目录 `list()`、读
 * `.quality`、再按候选路径逐个 `isFile` 找封面（LocalListActivity.scanVideos），文件夹页还要按
 * `.folders_info.json` 过滤（同文件 folderScanTitles），文件夹列表页则要为每个夹的每个视频 stat 全部候选根
 * （LocalFolderListActivity.countExistingTitles）。指纹只做「目录项名字 + 目录 mtime」这一层，不 stat 任何子目录、
 * 不读任何文件，成本大致等于扫描那一步的 `listFiles()`。
 *
 * <h3>为什么和开关放在同一个文件</h3>
 * 它和 [FolderPagePrefs.autoRefresh] 是同一个判定（同一个开关、同一个问题），三个页面都要用；
 * 各写一份的话，改一处必漏一处（本仓 [com.RobinNotBad.BiliClient.util.VirtualCollectionPrefs] 的教训同源）。
 *
 * <h3>它认不出什么（兜底就是下拉刷新）</h3>
 * 只看「缓存根目录里的条目名字」与几个 mtime，所以下面两种改动认不出来：
 * 1. 往**已存在**的视频目录里重新下载（目录名不变、条目数也不变）；
 * 2. 文件系统只把 mtime 记到秒级时，同一秒内的「删一个 + 建一个」且名字还相同。
 * 这两种情况用户下拉一次即可——缓存页与文件夹页的下拉刷新都会走真正的重扫
 * （LocalListActivity.refresh()、LocalFolderListActivity.loadFolders()）。
 */
object CacheTreeSignature {

    /**
     * `.folders_info.json` 的文件名。
     *
     * [FolderManager] 里那份是 private 的顶层常量（FolderManager.kt:14），这里只能照抄；改文件名要两处一起改。
     */
    private const val FOLDERS_FILE_NAME = ".folders_info.json"

    /**
     * 算 [root] 这一整套缓存目录的指纹，含别的分支的候选根（[ExternalCacheRoots.candidateRoots]）。
     *
     * 只看名字与 mtime：不 stat 子目录、不读文件，所以装着几百个缓存的机器上也只有几次 `list()`。
     */
    @JvmStatic
    fun of(root: File): Long {
        var h = 17L
        h = mix(h, root)
        // 文件夹记录单独混一次：夹名 / 夹内条目的改动都只落在这个文件上，而 LocalFolderListActivity
        // 整页数据（夹名、视频数、封面）都由它派生。注意改文件内容**不会**改目录 mtime，所以这一步不能省。
        h = h * 31 + File(root, FOLDERS_FILE_NAME).lastModified()
        for (external in ExternalCacheRoots.candidateRoots(root)) {
            h = mix(h, external)
            // 别的分支（改过包名的旧版本）也可能有自己的 .folders_info.json：本机侧要靠它把外部条目
            // 归到同名文件夹（appendExternalSources / folderScanTitles / countExistingTitles 都读它），
            // 只混目录 mtime 捕捉不到它的内容变化（改文件不改目录 mtime），所以单独混一次
            h = h * 31 + File(external, FOLDERS_FILE_NAME).lastModified()
        }
        return h
    }

    /** 一个目录的贡献：mtime + 条目名字（用名字而不是条目数：删一个再建一个时数目不变） */
    private fun mix(seed: Long, dir: File): Long {
        var h = seed * 31 + dir.lastModified()
        val names = dir.list()
        h = h * 31 + (names?.size ?: -1)
        // 排序后参与哈希：`list()` 的返回顺序在文件系统层面不保证稳定，不排会平白多出「变了」的误判
        names?.sorted()?.forEach { h = h * 31 + it.hashCode() }
        return h
    }
}
