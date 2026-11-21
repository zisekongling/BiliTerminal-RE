package com.RobinNotBad.BiliClient.adapter.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.video.info.AddFavoriteActivity
import com.RobinNotBad.BiliClient.api.ApiErrorText
import com.RobinNotBad.BiliClient.api.FeedbackApi
import com.RobinNotBad.BiliClient.api.WatchLaterApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.recommend.BlockedAuthorStore
import com.RobinNotBad.BiliClient.util.recommend.RecommendTasteStore

/**
 * 列表项长按「更多操作」菜单。
 *
 * <h3>它替代了什么</h3>
 * 26.11.08 之前，[VideoCardAdapter]、[HistoryVideoCardAdapter]、[UserVideoAdapter] 里
 * 「没有页面专属长按」的那条回落路径是**直接** `VideoQuickCache.handle()`——手指一按住就把缓存
 * 清晰度选择页糊到脸上，既吓人、也没法在列表里做别的事（加稍后再看、收藏、不感兴趣）。
 * 现在回落路径改成这个菜单，[Action.CACHE] 只是菜单里的一项，行为与旧路径完全一致。
 *
 * <h3>为什么要一个纯函数 [actionsFor]</h3>
 * 「这张卡片上该出现哪几项」是逻辑不是渲染：番剧卡片的 `aid` 装的是 media_id（拿去做稍后再看/
 * 收藏必然失败）、直播卡片同理。判据集中在 [actionsFor] 里，JVM 单测直接调它即可
 * （按 `docs/agents/dev-conventions.md` §用户可见文案：会被单测调用的纯函数**不读资源**，
 * 所以它只返回枚举，文案由 [show] 在带 Context 的地方取）。
 */
object VideoCardMenu {

    /**
     * 菜单动作。声明顺序即显示顺序，与 [actionsFor] 的拼装顺序保持一致。
     *
     * 增删动作时**同时**改三处：[actionsFor]、[label]、[show] 里的 `when`。
     */
    enum class Action {
        /** 加进稍后再看（[WatchLaterApi.add]）。 */
        WATCH_LATER,

        /** 打开收藏夹选择页（[AddFavoriteActivity]）。 */
        FAVORITE,

        /** 沿用旧的快速缓存链路（[VideoQuickCache]），按 `cache_default_quality` 决定细节。 */
        CACHE,

        /**
         * 全屏看封面大图（[ImageViewerActivity]）。
         *
         * 不需要登录、也不碰服务端，所以它是唯一一项「卡片没封面时静默不给」的动作
         * （判据在 [actionsFor]，免得点进去是个空白页）。
         */
        VIEW_COVER,

        /**
         * 减少推荐 / 不感兴趣。动作本体由列表页注入：它要拿到这条推荐在信息流里的位置与 track 信息，
         * 适配器拿不到这些，只能当二传手（见 [VideoCardAdapter.onReduceRecommend]）。
         */
        REDUCE_RECOMMEND,

        /**
         * 屏蔽作者：加进本地名单（[BlockedAuthorStore]），并给服务端补一次「不想看此 UP 主」
         * （[FeedbackApi.REASON_UP]）。名单是**本地过滤**，所以立刻生效；服务端那次只是让长期
         * 推荐也少推一点，失败不影响本地效果。
         *
         * 与 [REDUCE_RECOMMEND] 的区别：那个只针对**这一条**内容，这个针对**这位作者的全部内容**，
         * 且会在设置页留一条可撤销的记录。
         */
        BLOCK_AUTHOR
    }

    /**
     * 某张卡片该出现哪些动作（纯函数，可 JVM 单测）。
     *
     * 目前只在「普通投稿卡片」上给动作，理由是 `aid` 的语义：番剧卡片的 `aid` 其实是 media_id、
     * 直播卡片是 room_id，拿它们调稍后再看/收藏会得到一个莫名其妙的 `-400`。私有功能产出的卡片
     * （没有 B 站 aid/cid）由调用方用 `OptionalFeatures.skipQuickCache` 提前挡掉，不在这里判。
     *
     * @param reduceRecommendAvailable 当前列表是否支持「减少推荐」（只有信息流才有意义）
     * @param blockAuthorAvailable     当前列表是否支持「屏蔽作者」（信息流才有意义；其它列表
     *                                 不该出现这一项，否则用户会以为在收藏夹里屏蔽也能过滤推荐）
     */
    fun actionsFor(
        videoCard: VideoCard,
        reduceRecommendAvailable: Boolean,
        blockAuthorAvailable: Boolean = false
    ): List<Action> {
        if (!isNormalVideo(videoCard)) return emptyList()
        val actions = ArrayList<Action>(6)
        actions.add(Action.WATCH_LATER)
        actions.add(Action.FAVORITE)
        actions.add(Action.CACHE)
        // 没封面的卡片（个别接口不返回 pic）不给这一项，否则点进去是一张空白大图。
        if (!videoCard.cover.isNullOrBlank()) actions.add(Action.VIEW_COVER)
        if (reduceRecommendAvailable) actions.add(Action.REDUCE_RECOMMEND)
        // upMid 为 0 说明接口没给作者 mid，本地名单无从匹配，给这一项只会点了个寂寞。
        if (blockAuthorAvailable && videoCard.upMid != 0L) actions.add(Action.BLOCK_AUTHOR)
        return actions
    }

    /** 卡片是否是普通投稿视频（`type` 缺省就是 `"video"`，见 [VideoCard.type]）。 */
    private fun isNormalVideo(videoCard: VideoCard): Boolean {
        val type = videoCard.type
        return type == null || type.isEmpty() || type == "video"
    }

    /**
     * 弹出菜单；**没动作可给时不弹**并返回 false，让调用方把这次长按当成没处理
     * （例如番剧卡片：旧行为是弹一句「番剧暂不支持快速缓存」，现在是压根不该有快捷动作）。
     *
     * @param reduceRecommendAvailable 列表是否支持「减少推荐」
     * @param onReduceRecommend        真的点了「减少推荐」时执行；为 null 时该动作不出现
     * @param blockAuthorAvailable     列表是否支持「屏蔽作者」（信息流才传 true）
     * @param onBlockAuthor            屏蔽成功后通知列表移除该作者已显示的卡片；为 null 时该动作不出现
     * @return 是否真的弹了菜单
     */
    fun show(
        context: Context,
        videoCard: VideoCard,
        reduceRecommendAvailable: Boolean = false,
        onReduceRecommend: (() -> Unit)? = null,
        blockAuthorAvailable: Boolean = false,
        onBlockAuthor: ((VideoCard) -> Unit)? = null
    ): Boolean {
        val actions = actionsFor(
            videoCard,
            reduceRecommendAvailable && onReduceRecommend != null,
            blockAuthorAvailable && onBlockAuthor != null
        )
        if (actions.isEmpty()) return false
        val labels = actions.map { label(context, it) }
        TerminalDialog.menu(context, videoCard.title, labels) { index ->
            when (actions[index]) {
                Action.WATCH_LATER -> addToWatchLater(context, videoCard)
                Action.FAVORITE -> openFavoriteChooser(context, videoCard)
                Action.CACHE -> VideoQuickCache.handle(context, videoCard)
                Action.VIEW_COVER -> viewCover(context, videoCard)
                Action.REDUCE_RECOMMEND -> onReduceRecommend?.invoke()
                Action.BLOCK_AUTHOR -> blockAuthor(context, videoCard, onBlockAuthor)
            }
        }.show()
        return true
    }

    private fun label(context: Context, action: Action): String = when (action) {
        Action.WATCH_LATER -> context.getString(R.string.text_video_card_menu_watch_later)
        Action.FAVORITE -> context.getString(R.string.text_video_card_menu_favorite)
        Action.CACHE -> context.getString(R.string.text_video_card_menu_cache)
        Action.VIEW_COVER -> context.getString(R.string.text_video_card_menu_view_cover)
        Action.REDUCE_RECOMMEND -> context.getString(R.string.text_video_card_menu_reduce_recommend)
        Action.BLOCK_AUTHOR -> context.getString(R.string.text_video_card_menu_block_author)
    }

    /**
     * 需要登录的两项（稍后再看、收藏）先统一拦截：未登录时接口只会回 -101，
     * 弹一句和详情页一致的「还没有登录喵~」比抛错误码友好。
     */
    private fun requireLogin(): Boolean =
        SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0L

    /**
     * 屏蔽作者：本地名单先落下（立刻生效），再顺手给服务端一次「不想看此 UP 主」。
     *
     * <p>两件事的顺序有讲究：本地名单是用户能立刻感知的部分（卡片马上消失、设置页能看到），
     * 服务端请求要联网、还可能因为没登录被判 -101，绝不能让它挡在前面。所以先本地后网络，
     * 网络那次失败只在日志里留痕，不打扰用户——本地已经屏蔽了，用户的诉求已经满足。
     */
    private fun blockAuthor(context: Context, videoCard: VideoCard, onBlockAuthor: ((VideoCard) -> Unit)?) {
        val upMid = videoCard.upMid
        if (upMid == 0L) return
        val added = BlockedAuthorStore.block(upMid, videoCard.upName)
        // 记一笔负反馈：X 式重排会按这个把该 UP 往后压（对齐参考实现的 noteNegative）
        RecommendTasteStore.noteNegative(upMid)
        if (!added) {
            // 名单里已经有了（比如同一作者的另一条卡片又点了一次屏蔽）
            MsgUtil.showMsg(context.getString(R.string.msg_video_card_menu_block_author_exists))
            return
        }
        val displayName = videoCard.upName?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.text_video_card_menu_block_author_fallback)
        MsgUtil.showMsg(context.getString(R.string.msg_video_card_menu_block_author_ok, displayName))
        onBlockAuthor?.invoke(videoCard)
        CenterThreadPool.run {
            try {
                // aid <= 0 时 FeedbackApi 自己会返回 -400，不去打扰服务端
                if (videoCard.aid > 0L) {
                    FeedbackApi.dislike(videoCard.aid, upMid, videoCard.trackId, FeedbackApi.REASON_UP)
                }
            } catch (e: Exception) {
                // 屏蔽已经靠本地名单完成了，服务端这步失败不该弹错误框，只留一条日志
                Logu.w("屏蔽作者的服务端反馈失败：" + e)
            }
        }
    }

    private fun addToWatchLater(context: Context, videoCard: VideoCard) {
        if (!requireLogin()) {
            MsgUtil.showMsg(ApiErrorText.loginRequired())
            return
        }
        CenterThreadPool.run {
            try {
                val result = WatchLaterApi.add(videoCard.aid)
                val msg = if (result == 0) {
                    context.getString(R.string.msg_video_card_menu_watch_later_ok)
                } else {
                    context.getString(R.string.msg_video_card_menu_watch_later_failed, result)
                }
                CenterThreadPool.runOnUiThread { MsgUtil.showMsg(msg) }
            } catch (e: Exception) {
                MsgUtil.err(e)
            }
        }
    }

    private fun openFavoriteChooser(context: Context, videoCard: VideoCard) {
        // 登录态其实由 AddFavoriteActivity 自己也判一遍（它会 toast 后 finish），
        // 这里先判是为了不当着用户的面闪一个空页面。
        if (!requireLogin()) {
            MsgUtil.showMsg(ApiErrorText.loginRequired())
            return
        }
        val intent = Intent(context, AddFavoriteActivity::class.java)
        intent.putExtra("aid", videoCard.aid)
        startActivity(context, intent)
    }

    /** 全屏看封面，与详情页长按封面走同一个页面（[ImageViewerActivity] 的 extra 是 `imageList`）。 */
    private fun viewCover(context: Context, videoCard: VideoCard) {
        val cover = videoCard.cover
        if (cover.isNullOrBlank()) {
            // 理论上 actionsFor 已经把这一项滤掉了，这里只是兜底。
            return
        }
        val imageList = ArrayList<String>(1)
        imageList.add(cover)
        val intent = Intent(context, ImageViewerActivity::class.java)
        intent.putExtra("imageList", imageList)
        startActivity(context, intent)
    }

    /**
     * 适配器里的 context 未必是 Activity（列表可能挂在 application context 上），
     * 非 Activity 起 Activity 必须带 NEW_TASK，否则直接抛 AndroidRuntimeException。
     */
    private fun startActivity(context: Context, intent: Intent) {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
