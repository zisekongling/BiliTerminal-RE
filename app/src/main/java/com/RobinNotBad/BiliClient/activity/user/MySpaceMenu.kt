package com.RobinNotBad.BiliClient.activity.user

import android.app.Activity
import android.content.Intent
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteFolderListActivity

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
        Item("follow", R.drawable.icon_followings, "关注"),
        Item("watch_later", R.drawable.icon_play_12, "稍后再看"),
        Item("favorite", R.drawable.icon_star, "收藏"),
        Item("bangumi", R.drawable.icon_bangumi, "追番列表"),
        Item("history", R.drawable.icon_history, "历史记录"),
        Item("creative", R.drawable.icon_creative_center, "创作中心"),
        Item("vip", R.drawable.icon_info, "大会员"),
        Item("login_record", R.drawable.icon_time, "登录记录"),
        Item("coin_log", R.drawable.icon_info, "硬币变化记录"),
        Item("exp_log", R.drawable.icon_info, "经验变化记录"),
        Item("edit_profile", R.drawable.icon_info, "编辑个人资料")
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
