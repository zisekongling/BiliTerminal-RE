package com.RobinNotBad.BiliClient.tutorial

import android.app.Activity

/**
 * 教程 DSL —— 取代原来手写 `res/xml/tutorial_*.xml` 的方式。
 *
 * 用法：
 * ```kotlin
 * val videoMain = tutorial(
 *     id = "video_main",
 *     title = "视频详情-主要教程",
 *     version = 1,
 *     target = VideoInfoActivity::class.java,
 *     kind = TutorialKind.GUIDE,
 * ) {
 *     page {
 *         text("欢迎来到视频详情页").color("#4a8eca").bold()
 *         newline()
 *         image(R.mipmap.tutorial_video)
 *     }
 * }
 * ```
 *
 * 设计约束：本文件只产出纯数据（[Tutorial] / [TutorialSpan]），
 * 不依赖任何 Android 渲染 API，因此可以直接写 JVM 单测。
 */
fun tutorial(
    id: String,
    title: String,
    version: Int,
    target: Class<out Activity>,
    kind: TutorialKind,
    hintText: String? = null,
    hintViewId: Int? = null,
    imageRes: Int? = null,
    block: TutorialScope.() -> Unit = {},
): Tutorial {
    val scope = TutorialScope()
    scope.block()
    return Tutorial(
        id = id,
        title = title,
        version = version,
        target = target,
        kind = kind,
        pages = scope.pages,
        imageRes = imageRes,
        hintText = hintText,
        hintViewId = hintViewId,
    )
}

/** DSL 作用域标记，防止嵌套 block 里误调外层方法。 */
@DslMarker
annotation class TutorialDslMarker

@TutorialDslMarker
class TutorialScope {
    internal val pages = mutableListOf<TutorialPage>()

    /** 声明一页。一次都不调用时，整篇视为一页。 */
    fun page(block: PageScope.() -> Unit) {
        val scope = PageScope()
        scope.block()
        pages += TutorialPage(scope.spans.toList())
    }
}

@TutorialDslMarker
class PageScope {
    internal val spans = mutableListOf<TutorialSpan>()

    /** 追加一段文字，返回可继续链式设置样式的句柄。 */
    fun text(content: String): TextScope {
        spans += TutorialSpan(text = content)
        return TextScope(spans, spans.lastIndex)
    }

    /** 追加一个换行。 */
    fun newline() {
        spans += TutorialSpan.NEWLINE
    }

    /** 追加一张配图。 */
    fun image(resId: Int) {
        spans += TutorialSpan.image(resId)
    }
}

/** `text(...)` 的样式链式句柄。 */
@TutorialDslMarker
class TextScope(
    private val spans: MutableList<TutorialSpan>,
    private val index: Int,
) {
    fun bold() = style(TutorialStyle.BOLD)
    fun italic() = style(TutorialStyle.ITALIC)
    fun underline() = style(TutorialStyle.UNDERLINE)
    fun strike() = style(TutorialStyle.STRIKE)

    fun style(style: TutorialStyle) = apply {
        spans[index] = spans[index].copy(style = style)
    }

    fun color(color: String) = apply {
        spans[index] = spans[index].copy(color = color)
    }
}
