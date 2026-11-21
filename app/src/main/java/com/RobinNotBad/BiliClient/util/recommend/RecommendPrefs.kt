package com.RobinNotBad.BiliClient.util.recommend

import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil

/**
 * 推荐流相关的开关读取（默认值只在这里定义一次，调用点不要写死）。
 */
object RecommendPrefs {

    /** 「X 式重排」开关，默认关闭：它会打乱平台原本的排序，属于用户主动选择的功能。 */
    const val RERANK_DEFAULT: Boolean = false

    /** 是否启用 X 式重排。 */
    @JvmStatic
    fun rerankEnabled(): Boolean =
        SharedPreferencesUtil.getBoolean(SettingsKeys.RECOMMEND_RERANK_ENABLE, RERANK_DEFAULT)
}
