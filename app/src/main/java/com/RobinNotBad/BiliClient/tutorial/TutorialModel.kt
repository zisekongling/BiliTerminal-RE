package com.RobinNotBad.BiliClient.tutorial

import android.app.Activity

/**
 * 教程类型。
 *
 * - [GUIDE]  新用户上手引导：强制读完（翻完所有页 + 停留满时长才能点「已阅」，返回键无效）。
 * - [NOTICE] 版本说明 / 次要提示：可跳过，点返回即视为已读，不再重复弹。
 * - [HINT]   页面翻页提示：不是全屏页，只在目标页面上显示一条提示文字，看过一次即隐藏。
 */
enum class TutorialKind {
    GUIDE,
    NOTICE,
    HINT,
}

/** 富文本样式（对应旧 XML 里的 `<style>` 标签）。 */
enum class TutorialStyle {
    NORMAL,
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKE,
}

/**
 * 教程正文里的一个片段：文字、换行或图片。
 *
 * 旧实现用 `model/CustomText` + XML 表达，这里改成不可变数据类：
 * 既方便单测，也避免 XML 解析那套 `isInXxx` 布尔大礼包。
 */
data class TutorialSpan(
    val text: String? = null,
    val imageRes: Int? = null,
    val style: TutorialStyle = TutorialStyle.NORMAL,
    val color: String = DEFAULT_TEXT_COLOR,
) {
    companion object {
        const val DEFAULT_TEXT_COLOR = "#ffffff"

        /** 换行片段。 */
        val NEWLINE = TutorialSpan(text = "\n")

        /** 图片片段。 */
        fun image(resId: Int) = TutorialSpan(imageRes = resId)
    }
}

/** 教程内的一页。 */
data class TutorialPage(
    val spans: List<TutorialSpan>,
)

/**
 * 一篇教程。
 *
 * @param id      唯一标识，同时是已读状态的存储键。
 *                旧系统拿「数组下标」当键，导致 search/message/dynamic 三篇互相覆盖，这里改成显式 id。
 * @param version 显式版本号：新增内容就 +1，只对没看过这个版本的用户展示。
 * @param target  触发页面，`BaseActivity` 按当前页面类名集中匹配。
 * @param pages   分页内容；[TutorialKind.HINT] 为空。
 * @param hintText  HINT 类型的提示文案。
 * @param hintViewId HINT 类型要显示提示的目标 View id。
 */
data class Tutorial(
    val id: String,
    val title: String,
    val version: Int,
    val target: Class<out Activity>,
    val kind: TutorialKind,
    val pages: List<TutorialPage> = emptyList(),
    val imageRes: Int? = null,
    val hintText: String? = null,
    val hintViewId: Int? = null,
) {
    /** 是否需要全屏分页展示（GUIDE / NOTICE）。 */
    val isFullScreen: Boolean
        get() = kind != TutorialKind.HINT

    /** GUIDE 才强制读完。 */
    val isMandatory: Boolean
        get() = kind == TutorialKind.GUIDE

    /** 页数，至少 1（内容为空时也占一页，避免分页器出现 0 页）。 */
    val pageCount: Int
        get() = pages.size.coerceAtLeast(1)
}
