package com.RobinNotBad.BiliClient.activity.user

import com.RobinNotBad.BiliClient.util.UiText

import android.app.Activity
import android.content.Intent
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteFolderListActivity
import com.RobinNotBad.BiliClient.activity.user.subscription.SubscriptionListActivity

/**
 * 「我的」页面的功能入口定义：key → 图标 / 文案 / 跳转。
 *
 * 主列表（[MySpaceActivity]）与更多列表（[MySpaceMoreActivity]）共用同一份定义，
 * 避免跳转逻辑写两遍；顺序与分区由 `MySpaceConfig` 决定。
 */
object MySpaceMenu {

    data class Item(val key: String, val iconRes: Int, val label: String)

    /** key 集合需与 `MySpaceConfig.ALL_ITEMS` 一致（`MySpaceConfigTest` 有守卫用例）。 */
    val ITEMS: List<Item> = listOf(
        Item("follow", R.drawable.icon_followings, UiText.of("关注")),
        Item("watch_later", R.drawable.icon_play_12, UiText.of("稍后再看")),
        Item("favorite", R.drawable.icon_star, UiText.of("收藏")),
        // 订阅与收藏是两个接口：「收藏」是我建的收藏夹，「订阅」是我订阅别人的收藏夹/合集
        Item("subscription", R.drawable.icon_folder, UiText.of("订阅")),
        Item("bangumi", R.drawable.icon_bangumi, UiText.of("追番列表")),
        Item("history", R.drawable.icon_history, UiText.of("历史记录")),
        Item("creative", R.drawable.icon_creative_center, UiText.of("创作中心")),
        Item("vip", R.drawable.icon_info, UiText.of("大会员")),
        Item("login_record", R.drawable.icon_time, UiText.of("登录记录")),
        Item("coin_log", R.drawable.icon_info, UiText.of("硬币变化记录")),
        Item("exp_log", R.drawable.icon_info, UiText.of("经验变化记录")),
        Item("edit_profile", R.drawable.icon_info, UiText.of("编辑个人资料"))
    )

    private val BY_KEY: Map<String, Item> = ITEMS.associateBy { it.key }

    fun itemOf(key: String): Item? = BY_KEY[key]

    fun labelOf(key: String): String = BY_KEY[key]?.label ?: key

    /** 执行功能入口，两个页面共用；[mid] 供「关注」使用。 */
    fun open(activity: Activity, key: String, mid: Long) {
        when (key) {
            "follow" -> activity.startActivity(
                Intent(activity, FollowUsersActivity::class.java)
                    .putExtra("mid", mid)
                    .putExtra("mode", 0)
            )

            "watch_later" -> activity.startActivity(Intent(activity, WatchLaterActivity::class.java))
            "favorite" -> activity.startActivity(Intent(activity, FavoriteFolderListActivity::class.java))
            "subscription" -> activity.startActivity(Intent(activity, SubscriptionListActivity::class.java))
            "bangumi" -> activity.startActivity(Intent(activity, FollowingBangumisActivity::class.java))
            "history" -> activity.startActivity(Intent(activity, HistoryActivity::class.java))
            "creative" -> activity.startActivity(Intent(activity, CreativeCenterActivity::class.java))
            "vip" -> activity.startActivity(Intent(activity, VipActivity::class.java))
            "login_record" -> activity.startActivity(Intent(activity, LoginRecordActivity::class.java))
            "coin_log" -> activity.startActivity(Intent(activity, CoinLogActivity::class.java))
            "exp_log" -> activity.startActivity(Intent(activity, ExpLogActivity::class.java))
            "edit_profile" -> activity.startActivity(Intent(activity, EditProfileActivity::class.java))
        }
    }
}