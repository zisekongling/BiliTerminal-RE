package com.RobinNotBad.BiliClient.util

/**
 * 虚拟合集拆成两个独立功能后的默认值与「点一下之后做什么」的判定。
 *
 * <h3>为什么拆</h3>
 * 26.11.01 之前只有一个「虚拟合集」开关，它同时决定了两件事：
 * 一是**把同一文件夹/收藏夹的视频组成一个合集**，二是**在收藏夹里点视频卡片就直接开播**。
 * 用户反馈后者的结果很突兀——点一下卡片就离开列表进了播放器，想看视频详情反而没有入口。
 * 现在拆成两个开关（设置 → 高级与实验 → 功能开关）：
 *
 * 1. [collectionEnabled]「虚拟合集」：只负责**组合集**（播放器里能选集切换、自动联播）。默认开启。
 * 2. [clickPlayEnabled]「收藏夹视频点击即播」：在收藏夹里点视频卡片时**直接起播**，
 *    而不是进视频详情页。**默认关闭**——默认点卡片回到老行为（视频详情页），
 *    想整夹连播时长按卡片选「以虚拟合集连播」。
 *
 * <h3>缓存页不受第 2 项控制</h3>
 * 本地缓存视频没有「视频详情页」这个主流程，点文件夹里的视频本来就该直接播；
 * 第 1 项只决定「播这一条」还是「整夹连播」（见 [cacheClickAction]）。
 *
 * <h3>为什么默认值和判定都在这里</h3>
 * 与 `player/SkipOpEdPrefs`、`util/LongPressPrefs` 同一个理由：默认值散到多个调用点后，
 * 改漏一处的症状是「设置页显示开着、实际却是关的」——不报错、只能靠人肉比对发现。
 * 判定本身抽成纯函数，JVM 单测（本仓库没有 Robolectric，单测里没有 Context）能直接跑。
 */
object VirtualCollectionPrefs {

    /** 功能一：虚拟合集（组合集）。键沿用拆分前的老键，老用户存的值继续生效。 */
    const val ENABLE = SharedPreferencesUtil.VIRTUAL_COLLECTION_ENABLE

    /** 功能二：收藏夹里点视频卡片直接播放。 */
    const val CLICK_PLAY = SharedPreferencesUtil.VIRTUAL_COLLECTION_CLICK_PLAY

    /** 功能一默认开启：与拆分前的默认值一致。 */
    const val DEFAULT_ENABLE = true

    /**
     * 功能二默认关闭。
     *
     * 默认值与拆分前相反——拆分前「点卡片就整夹开播」是唯一行为，现在默认点卡片走
     * 视频详情页，要看合集连播得长按卡片选「以虚拟合集连播」。这是用户拍板的口径：
     * 点一下卡片就进播放器太突兀，详情页才是点卡片的默认预期。
     */
    const val DEFAULT_CLICK_PLAY = false

    /** 功能一（虚拟合集 / 组合集）是否开启。 */
    @JvmStatic
    fun collectionEnabled(): Boolean =
        SharedPreferencesUtil.getBoolean(ENABLE, DEFAULT_ENABLE)

    /** 功能二（收藏夹里点视频卡片直接播放）是否开启。 */
    @JvmStatic
    fun clickPlayEnabled(): Boolean =
        SharedPreferencesUtil.getBoolean(CLICK_PLAY, DEFAULT_CLICK_PLAY)

    /** 收藏夹里点一条视频之后应该做什么。 */
    enum class ClickAction {
        /** 多选态：勾选 / 取消勾选。 */
        SELECT,

        /** 以虚拟合集连播整个收藏夹。 */
        PLAY_COLLECTION,

        /** 只播这一条视频。 */
        PLAY_SINGLE,

        /** 进视频详情页（默认行为）。 */
        OPEN_DETAIL,
    }

    /**
     * 收藏夹内点击一条视频的判定。
     *
     * - 多选态优先：点 = 勾选，与两个开关都无关；
     * - 功能二关闭：进视频详情页（老行为），合集连播走长按菜单；
     * - 功能二开启：功能一开着就整夹连播，否则只播这一条。
     */
    @JvmStatic
    fun favoriteClickAction(
        selectionMode: Boolean,
        collectionEnabled: Boolean,
        clickPlayEnabled: Boolean,
    ): ClickAction = when {
        selectionMode -> ClickAction.SELECT
        !clickPlayEnabled -> ClickAction.OPEN_DETAIL
        collectionEnabled -> ClickAction.PLAY_COLLECTION
        else -> ClickAction.PLAY_SINGLE
    }

    /**
     * 本地缓存页点一条视频的判定：**点了就播**，只由功能一决定播单条还是整夹连播。
     *
     * @param inFolder 当前是否已经进入某个缓存文件夹（未分类/根目录下没有「整夹」的概念）
     */
    @JvmStatic
    fun cacheClickAction(inFolder: Boolean, collectionEnabled: Boolean): ClickAction =
        if (inFolder && collectionEnabled) ClickAction.PLAY_COLLECTION else ClickAction.PLAY_SINGLE
}
