package com.RobinNotBad.BiliClient.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.RobinNotBad.BiliClient.model.MessageCard
import com.RobinNotBad.BiliClient.model.VideoCard

/**
 * 可选私有功能的反射门面。
 *
 * 有一小批功能只存在于私有构建：源码在仓库根目录的 `private/`（已 gitignore，
 * 由 CI 从私有仓库 checkout 进来），公开仓库里只有这个门面与少量条件挂点。
 * 机制说明见 `docs/optional-features.md`。
 *
 * 规则：
 *  - **这里绝不写实现**，只反射转发到 `com.RobinNotBad.BiliClient.feature.FeatureHolder`；
 *  - 所有方法都吞异常：私有代码不在场时返回 null / no-op，公共构建的行为与从前完全一致；
 *  - 于是公开仓库能编、能跑、能发版，私有构建才多出这些功能。
 *
 * 私有侧的 FeatureHolder 用 @JvmStatic 暴露同名静态方法，参数只有框架类型。
 */
object OptionalFeatures {

    private const val HOLDER_CLASS = "com.RobinNotBad.BiliClient.feature.FeatureHolder"

    /** 私有注入是否在场。null = 不在场（公开构建、PR 构建、别人的开发机）。 */
    private val holder: Class<*>? by lazy {
        try {
            Class.forName(HOLDER_CLASS)
        } catch (e: Throwable) {
            null
        }
    }

    val available: Boolean
        get() = holder != null

    /** 菜单项 key，不在场时为 null。 */
    fun menuKey(): String? = invokeString("menuKey")

    /** 菜单项显示名，不在场时为 null。 */
    fun menuLabel(context: Context): String? = try {
        holder?.getMethod("menuLabel", Context::class.java)?.invoke(null, context) as? String
    } catch (e: Throwable) {
        null
    }

    /** 菜单项对应的页面 Class，不在场时为 null。 */
    fun menuActivity(): Class<*>? = try {
        holder?.getMethod("menuActivity")?.invoke(null) as? Class<*>
    } catch (e: Throwable) {
        null
    }

    /**
     * 往视频详情页的动作区插一行按钮（插在 [anchor] 之前）。具体插什么、点了做什么，
     * 全部由私有侧决定；不在场时什么都不做，调用方不用判空。
     */
    fun attachDetailActionRow(
        container: ViewGroup,
        anchor: View,
        videoId: String,
        aid: Long,
        title: String
    ) {
        try {
            holder?.getMethod(
                "attachDetailActionRow",
                ViewGroup::class.java,
                View::class.java,
                String::class.java,
                java.lang.Long.TYPE,
                String::class.java
            )?.invoke(null, container, anchor, videoId, aid, title)
        } catch (e: Throwable) {
            // 私有功能异常绝不能影响详情页正常显示
            Logu.w("OptionalFeatures", "attachDetailActionRow failed: " + e.message)
        }
    }

    /**
     * 评论操作面板里可选私有功能项的文案。
     *
     * 私有构建返回它自己的字符串，公开构建返回 null —— 调用方据此决定**要不要加这一项**，
     * 于是公开构建里的操作面板与引入私有功能之前完全一致。
     */
    fun replyReportLabel(context: Context): String? = try {
        holder?.getMethod("replyReportLabel", Context::class.java)?.invoke(null, context) as? String
    } catch (e: Throwable) {
        Logu.w("OptionalFeatures", "replyReportLabel failed: " + e.message)
        null
    }

    /**
     * 打开可选私有功能面板。
     *
     * 具体流程全在私有侧，公共侧只转交这三个参数：
     * [oid] 评论区 oid、[replyType] 评论区类型（不是稿件类型）、[rpid] 目标评论。
     */
    fun replyReport(context: Context, oid: Long, replyType: Int, rpid: Long) {
        try {
            holder?.getMethod(
                "replyReport",
                Context::class.java,
                java.lang.Long.TYPE,
                Integer.TYPE,
                java.lang.Long.TYPE
            )?.invoke(null, context, oid, replyType, rpid)
        } catch (e: Throwable) {
            Logu.w("OptionalFeatures", "replyReport failed: " + e.message)
        }
    }

    /**
     * 用私有实现填充一条「可选类别」消息卡片（[itemJson] 是消息条目的原始 JSON）。
     *
     * 返回 false 表示私有侧不认识这条消息（或不在场），调用方按「无法识别这个类别」处理。
     * [fromLike] 区分「收到的赞」与「回复我的」两条流。
     */
    fun fillOptionalMessage(card: MessageCard, itemJson: String, fromLike: Boolean): Boolean = try {
        holder?.getMethod(
            "fillOptionalMessage",
            MessageCard::class.java,
            String::class.java,
            java.lang.Boolean.TYPE
        )?.invoke(null, card, itemJson, fromLike) as? Boolean ?: false
    } catch (e: Throwable) {
        Logu.w("OptionalFeatures", "fillOptionalMessage failed: " + e.message)
        false
    }

    /**
     * 点击一条「可选类别」消息。返回 true 表示私有侧已经处理。
     *
     * [itemType] 与 [bvid] 都由公共侧从消息里取好再传，私有侧不用再碰卡片结构。
     */
    fun openOptionalMessage(context: Context, itemType: String?, bvid: String?): Boolean = try {
        holder?.getMethod(
            "openOptionalMessage",
            Context::class.java,
            String::class.java,
            String::class.java
        )?.invoke(null, context, itemType, bvid) as? Boolean ?: false
    } catch (e: Throwable) {
        Logu.w("OptionalFeatures", "openOptionalMessage failed: " + e.message)
        false
    }

    // ==================== 番剧资料（Bgm）与第三方片源：门面 ====================
    //
    // 实现全在 private/ 里；公开构建下这些方法全部是空操作 / null。
    // 调用方（搜索页、番剧详情页、列表适配器、播放页）只按返回值的「有/无」决定要不要多一块内容。

    /**
     * 搜索混排：让私有侧往 [out] 追加它认识的条目（B 站没有的番剧）。
     *
     * 只在关键词搜索第一页、且**在后台线程**调用；私有侧不在场时什么也不加。
     * 失败必须静默——补充源挂掉不能影响 B 站搜索结果本身。
     */
    fun appendBangumiSearchExtras(context: Context, keyword: String, out: MutableList<VideoCard>) {
        try {
            holder?.getMethod(
                "appendBangumiSearchExtras",
                Context::class.java,
                String::class.java,
                MutableList::class.java
            )?.invoke(null, context, keyword, out)
        } catch (e: Throwable) {
            Logu.w("OptionalFeatures", "appendBangumiSearchExtras failed: " + e.message)
        }
    }

    /**
     * 番剧详情页：让私有侧在 [root] 里装它自己的资料行（评分等）并异步加载。
     *
     * [seasonId] / [mediaId] 用于让私有侧做条目匹配；私有侧不在场时什么都不做。
     */
    fun attachBangumiDetails(root: ViewGroup, title: String, seasonId: Long, mediaId: Long) {
        try {
            holder?.getMethod(
                "attachBangumiDetails",
                ViewGroup::class.java,
                String::class.java,
                java.lang.Long.TYPE,
                java.lang.Long.TYPE
            )?.invoke(null, root, title, seasonId, mediaId)
        } catch (e: Throwable) {
            Logu.w("OptionalFeatures", "attachBangumiDetails failed: " + e.message)
        }
    }

    /**
     * 番剧详情页加载完成：让私有侧异步预热片源（“点播放时立刻能播”）。
     *
     * [hasVipEpisode] 为 false 时调用方根本不会调（没有会员集就不需要备用源）；私有侧自己再判账号态。
     */
    fun warmUpBangumiSource(title: String, hasVipEpisode: Boolean) {
        try {
            holder?.getMethod(
                "warmUpBangumiSource",
                String::class.java,
                java.lang.Boolean.TYPE
            )?.invoke(null, title, hasVipEpisode)
        } catch (e: Throwable) {
            Logu.w("OptionalFeatures", "warmUpBangumiSource failed: " + e.message)
        }
    }

    /**
     * 点「播放」时先问私有侧是否接管（无感换源）。
     *
     * [episodeLabel] 是 B 站那一集的真实集名（如「第5话」）：私有侧拿它显示标题、并按集号对齐
     * 采集站的集列表——只看 [episodeIndex] 会因两边列表里夹着 PV/预告而串集。
     *
     * 返回 true = 私有侧已经把这次播放接过去了（例如静默改用备用片源），调用方**不要再走 B 站播放**；
     * 返回 false = 私有侧不接管，照旧走 B 站。私有侧不在场时永远 false。
     */
    fun interceptBangumiPlay(
        context: Context,
        title: String,
        episodeIndex: Int,
        badge: String,
        episodeLabel: String
    ): Boolean = try {
        holder?.getMethod(
            "interceptBangumiPlay",
            Context::class.java,
            String::class.java,
            Integer.TYPE,
            String::class.java,
            String::class.java
        )?.invoke(null, context, title, episodeIndex, badge, episodeLabel) as? Boolean ?: false
    } catch (e: Throwable) {
        Logu.w("OptionalFeatures", "interceptBangumiPlay failed: " + e.message)
        false
    }

    /**
     * 番剧详情页的「角色」页（私有侧提供时才有）。
     *
     * 返回 null = 不提供这一页，调用方保持原来的页数。
     */
    fun mediaActorsFragment(aid: Long): Fragment? = try {
        holder?.getMethod("mediaActorsFragment", java.lang.Long.TYPE)?.invoke(null, aid) as? Fragment
    } catch (e: Throwable) {
        Logu.w("OptionalFeatures", "mediaActorsFragment failed: " + e.message)
        null
    }

    /** 「角色」页的标题；null = 不提供该页。 */
    fun mediaActorsPageTitle(context: Context): String? = try {
        holder?.getMethod("mediaActorsPageTitle", Context::class.java)?.invoke(null, context) as? String
    } catch (e: Throwable) {
        null
    }

    /**
     * 列表卡片点击：私有侧认识的卡片（它自己产出的那些类型）返回 true 表示已处理跳转。
     *
     * 公开侧只在既有的 `when (type)` 分支都命不中时才问它，公开构建的点击行为不变。
     */
    fun handleVideoCardClick(context: Context, card: VideoCard): Boolean = try {
        holder?.getMethod("handleVideoCardClick", Context::class.java, VideoCard::class.java)
            ?.invoke(null, context, card) as? Boolean ?: false
    } catch (e: Throwable) {
        Logu.w("OptionalFeatures", "handleVideoCardClick failed: " + e.message)
        false
    }

    /**
     * 卡片封面的地址覆盖：返回非 null 时用它，null 时按公开侧的默认规则（高画质后缀）。
     *
     * 私有侧的卡片封面可能带 query 串，加高画质后缀会 404，所以由它自己给出最终地址。
     */
    fun coverUrlOverride(card: VideoCard): String? = try {
        holder?.getMethod("coverUrlOverride", VideoCard::class.java)
            ?.invoke(null, card) as? String
    } catch (e: Throwable) {
        null
    }

    /** 长按卡片时的「快速缓存」是否跳过（私有侧的卡片没有 B 站 aid/cid）。 */
    fun skipQuickCache(card: VideoCard): Boolean = try {
        holder?.getMethod("skipQuickCache", VideoCard::class.java)
            ?.invoke(null, card) as? Boolean ?: false
    } catch (e: Throwable) {
        false
    }

    /**
     * 「备用片源解析」的回调。
     *
     * 播放页收到待解析的 intent 时先照常初始化并停在载入中，然后调 [requestThirdPartyResolve]
     * 把解析工作交给私有侧；解析结果通过这个回调回到播放页（私有侧保证在主线程回调）。
     */
    interface ThirdPartyResolveCallback {
        /** 解析成功：给出可直接播放的地址和（可空）该地址需要的 Referer。 */
        fun onResolved(url: String, referer: String?)

        /** 解析失败：私有侧已经提示过用户（Toast + 手动选源），播放页只需要收尾退出。 */
        fun onFailed()
    }

    /**
     * 请求私有侧解析备用片源，结果走 [callback]。
     *
     * 返回 true 表示私有侧已接手（无论成败都会回调）；false 表示私有侧不在场，
     * 调用方自己要给用户一个交代后退出。
     */
    fun requestThirdPartyResolve(
        activity: Activity,
        intent: Intent,
        callback: ThirdPartyResolveCallback
    ): Boolean = try {
        holder?.getMethod(
            "requestThirdPartyResolve",
            Context::class.java,
            Intent::class.java,
            ThirdPartyResolveCallback::class.java
        )?.invoke(null, activity, intent, callback) as? Boolean ?: false
    } catch (e: Throwable) {
        Logu.w("OptionalFeatures", "requestThirdPartyResolve failed: " + e.message)
        false
    }

    private fun invokeString(name: String): String? = try {
        holder?.getMethod(name)?.invoke(null) as? String
    } catch (e: Throwable) {
        null
    }
}
