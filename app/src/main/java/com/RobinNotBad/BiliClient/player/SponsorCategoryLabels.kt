package com.RobinNotBad.BiliClient.player

import androidx.annotation.StringRes
import com.RobinNotBad.BiliClient.R

/**
 * SponsorBlock 类别 id → 中文文案的**唯一映射**。
 *
 * 播放页（跳过提示里的「已跳过广告」）与设置页（勾选列表）都要按类别出中文名，
 * 两处各写一份 `when` 早晚会漂（新增类别只改了一处，另一处就显示兜底文案）。
 *
 * 这里只放资源 id，不放逻辑：哪些类别能跳由 [SponsorSkipPolicy] 决定。
 */
object SponsorCategoryLabels {

    /** 勾选页里列出的类别：全部类别去掉「精彩时刻」（那是跳转点，不是可跳片段）。 */
    val selectable: List<String> =
        SponsorSkipPolicy.ALL_CATEGORIES.filter { it != SponsorSkipPolicy.CAT_POI_HIGHLIGHT }

    /** 类别中文名；上游以后新增了类别就用「广告片段」兜底。 */
    @StringRes
    fun nameRes(category: String): Int = when (category) {
        SponsorSkipPolicy.CAT_SPONSOR -> R.string.player_sb_cat_sponsor
        SponsorSkipPolicy.CAT_SELFPROMO -> R.string.player_sb_cat_selfpromo
        SponsorSkipPolicy.CAT_INTERACTION -> R.string.player_sb_cat_interaction
        SponsorSkipPolicy.CAT_INTRO -> R.string.player_sb_cat_intro
        SponsorSkipPolicy.CAT_OUTRO -> R.string.player_sb_cat_outro
        SponsorSkipPolicy.CAT_PREVIEW -> R.string.player_sb_cat_preview
        SponsorSkipPolicy.CAT_FILLER -> R.string.player_sb_cat_filler
        SponsorSkipPolicy.CAT_MUSIC_OFFTOPIC -> R.string.player_sb_cat_music_offtopic
        SponsorSkipPolicy.CAT_POI_HIGHLIGHT -> R.string.player_sb_cat_poi_highlight
        SponsorSkipPolicy.CAT_EXCLUSIVE_ACCESS -> R.string.player_sb_cat_exclusive_access
        SponsorSkipPolicy.CAT_CHAPTER -> R.string.player_sb_cat_chapter
        else -> R.string.player_sb_cat_unknown
    }

    /**
     * 该类别在勾选页里的提示语；返回 0 表示"没什么可提醒的"（多数类别如此）。
     *
     * 只有主观性强的两档才值得提醒一次：`filler` 是把闲聊也跳掉、`music_offtopic`
     * 会切掉 MV 里的说话段——都是"勾上之前最好知道"的事。
     */
    @StringRes
    fun descRes(category: String): Int = when (category) {
        SponsorSkipPolicy.CAT_FILLER -> R.string.player_sb_cat_filler_desc
        SponsorSkipPolicy.CAT_MUSIC_OFFTOPIC -> R.string.player_sb_cat_music_offtopic_desc
        else -> 0
    }
}
