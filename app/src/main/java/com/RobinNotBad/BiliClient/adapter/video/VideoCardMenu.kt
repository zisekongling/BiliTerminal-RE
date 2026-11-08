package com.RobinNotBad.BiliClient.adapter.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.video.info.AddFavoriteActivity
import com.RobinNotBad.BiliClient.api.ApiErrorText
import com.RobinNotBad.BiliClient.api.WatchLaterApi
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog

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
         * 减少推荐 / 不感兴趣。动作本体由列表页注入：它要拿到这条推荐在信息流里的位置与 track 信息，
         * 适配器拿不到这些，只能当二传手（见 [VideoCardAdapter.onReduceRecommend]）。
         */
        REDUCE_RECOMMEND
    }

    /**
     * 某张卡片该出现哪些动作（纯函数，可 JVM 单测）。
     *
     * 目前只在「普通投稿卡片」上给动作，理由是 `aid` 的语义：番剧卡片的 `aid` 其实是 media_id、
     * 直播卡片是 room_id，拿它们调稍后再看/收藏会得到一个莫名其妙的 `-400`。私有功能产出的卡片
     * （没有 B 站 aid/cid）由调用方用 `OptionalFeatures.skipQuickCache` 提前挡掉，不在这里判。
     *
     * @param reduceRecommendAvailable 当前列表是否支持「减少推荐」（只有信息流才有意义）
     */
    fun actionsFor(videoCard: VideoCard, reduceRecommendAvailable: Boolean): List<Action> {
        if (!isNormalVideo(videoCard)) return emptyList()
        val actions = ArrayList<Action>(4)
        actions.add(Action.WATCH_LATER)
        actions.add(Action.FAVORITE)
        actions.add(Action.CACHE)
        if (reduceRecommendAvailable) actions.add(Action.REDUCE_RECOMMEND)
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
     * @return 是否真的弹了菜单
     */
    fun show(
        context: Context,
        videoCard: VideoCard,
        reduceRecommendAvailable: Boolean = false,
        onReduceRecommend: (() -> Unit)? = null
    ): Boolean {
        val actions = actionsFor(videoCard, reduceRecommendAvailable && onReduceRecommend != null)
        if (actions.isEmpty()) return false
        val labels = actions.map { label(context, it) }
        TerminalDialog.menu(context, videoCard.title, labels) { index ->
            when (actions[index]) {
                Action.WATCH_LATER -> addToWatchLater(context, videoCard)
                Action.FAVORITE -> openFavoriteChooser(context, videoCard)
                Action.CACHE -> VideoQuickCache.handle(context, videoCard)
                Action.REDUCE_RECOMMEND -> onReduceRecommend?.invoke()
            }
        }.show()
        return true
    }

    private fun label(context: Context, action: Action): String = when (action) {
        Action.WATCH_LATER -> context.getString(R.string.text_video_card_menu_watch_later)
        Action.FAVORITE -> context.getString(R.string.text_video_card_menu_favorite)
        Action.CACHE -> context.getString(R.string.text_video_card_menu_cache)
        Action.REDUCE_RECOMMEND -> context.getString(R.string.text_video_card_menu_reduce_recommend)
    }

    /**
     * 需要登录的两项（稍后再看、收藏）先统一拦截：未登录时接口只会回 -101，
     * 弹一句和详情页一致的「还没有登录喵~」比抛错误码友好。
     */
    private fun requireLogin(): Boolean =
        SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0L

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
        // 适配器里的 context 未必是 Activity（列表可能挂在 application context 上），
        // 非 Activity 起 Activity 必须带 NEW_TASK，否则直接抛 AndroidRuntimeException。
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
