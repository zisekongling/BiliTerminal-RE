package com.RobinNotBad.BiliClient.tutorial

import com.RobinNotBad.BiliClient.util.UiText

import android.app.Activity
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.article.OpusInfoActivity
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicActivity
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicInfoActivity
import com.RobinNotBad.BiliClient.activity.message.MessageActivity
import com.RobinNotBad.BiliClient.activity.search.SearchActivity
import com.RobinNotBad.BiliClient.activity.settings.GROUP_APPEARANCE
import com.RobinNotBad.BiliClient.activity.settings.SettingGroupActivity
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity
import com.RobinNotBad.BiliClient.activity.video.RecommendActivity
import com.RobinNotBad.BiliClient.activity.video.ShortVideoPlayerActivity
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity

/**
 * 教程注册表 —— 所有教程的唯一声明处。
 *
 * 新增一篇教程只需在这里加一项并加进 [all]；触发由 `BaseActivity.onStart()` 按
 * [Tutorial.target] 集中匹配，页面本身不用改，也就不会再有「忘了在页面里调用」这种漏加
 * （旧实现里 `tutorial_article` 就是这么漏掉的）。
 *
 * 同页多篇按 [all] 里的声明顺序串行展示（一次一篇，看完自动进下一篇）。
 *
 * ## 排版约定（新增/修改教程时照此写）
 * - **标题**：`bold().color(HIGHLIGHT)`，独占一行；
 * - **小标题**：`bold().color(ACCENT)`，后接说明；
 * - **操作词**（点击 / 长按 / 点按 / 滑动 / 下拉 / 捏合 / 输入）：一律 `bold().color(ACCENT)`，
 *   只加粗在黑色背景上不够醒目，必须带色；
 * - **列表项**：以 `"· "` 开头，项之间一个 `newline()`，段落之间两个；
 * - **注意事项**：`italic()`；
 * - 正文不写颜色，跟随主题文字色（亮色主题下自动变深），一页里强调色不超过两处小标题。
 */
object Tutorials {

    /** 主强调色（与顶栏、链接一致）——用于小标题与**所有操作词**。 */
    private const val ACCENT = "#4a8eca"

    /** 标题与关键提示用的 B 站粉。 */
    private const val HIGHLIGHT = "#FF6699"

    // ==================== 推荐页 ====================

    /** 推荐页 - 新用户上手引导。 */
    val recommend = tutorial(
        id = "recommend",
        title = UiText.of("推荐页面-重要教程"),
        version = 1,
        descriptionRes = R.string.tutorial_desc_recommend,
        target = RecommendActivity::class.java,
        kind = TutorialKind.GUIDE,
        imageRes = R.mipmap.tutorial_recommend,
    ) {
        page {
            text(UiText.of("欢迎来到推荐页面")).bold().color(HIGHLIGHT)
            newline()
            newline()
            text(UiText.of("顶栏")).bold().color(ACCENT)
            text("：")
            text(UiText.of("点击")).bold().color(ACCENT)
            text(UiText.of("可以返回上一页或打开菜单，"))
            text(UiText.of("整个顶栏都能点")).bold()
            text(UiText.of("，不必一直费劲地点左上角。"))
            newline()
            text(UiText.of("列表")).bold().color(ACCENT)
            text("：")
            text(UiText.of("划到顶部")).bold().color(ACCENT)
            text(UiText.of("可以"))
            text(UiText.of("下拉刷新")).bold().color(ACCENT)
            text(UiText.of("，同一个视频不会重复出现。"))
            newline()
            text(UiText.of("视频卡片")).bold().color(ACCENT)
            text("：")
            text(UiText.of("点击")).bold().color(ACCENT)
            text(UiText.of("进入详情页，"))
            text(UiText.of("长按")).bold().color(ACCENT)
            text(UiText.of("可以直接快速缓存（清晰度按设置里的「快捷缓存方式」决定）。"))
            newline()
            newline()
            text(UiText.of("你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！我们不希望你成为下一位来问我们“评论区怎么看”这类已有教程的问题的人！"))
        }
    }

    /**
     * 推荐页 - 主题变更说明（版本说明类，可跳过）。
     * v2：补亮色/暗色模式、弥散光晕、玻璃质感说明，底部加「打开外观设置」按钮。
     */
    val themeNotice = tutorial(
        id = "theme_notice",
        title = UiText.of("主题说明"),
        version = 2,
        descriptionRes = R.string.tutorial_desc_theme_notice,
        target = RecommendActivity::class.java,
        kind = TutorialKind.NOTICE,
        action = TutorialAction(
            label = UiText.of("打开外观设置"),
            target = SettingGroupActivity::class.java,
            extras = mapOf(
                "group_type" to GROUP_APPEARANCE,
                "group_title" to "外观设置",
            ),
        ),
    ) {
        page {
            text(UiText.of("本次更新说明")).bold().color(HIGHLIGHT)
            newline()
            newline()
            text(UiText.of("默认主题已经改回"))
            text(UiText.of("经典终端风格")).bold().color(HIGHLIGHT)
            text(UiText.of("，也就是黑色背景 + 粉色点缀的原始外观。"))
            newline()
            newline()
            text(UiText.of("想换回其它配色")).bold().color(ACCENT)
            newline()
            text(UiText.of("「主题配色」里可以自由切换任意主题（哔哩粉、知乎蓝、经典灰等）。"))
            newline()
            newline()
            text(UiText.of("亮色 / 暗色模式")).bold().color(ACCENT)
            newline()
            text(UiText.of("每套主题都有暗色和亮色两版，在「颜色模式」里切换。亮色适合白天和室外强光；播放器始终是暗色。"))
            newline()
            newline()
            text(UiText.of("弥散光晕 / 玻璃质感")).bold().color(ACCENT)
            newline()
            text(UiText.of("弥散光晕会在页面顶部铺一层随主题变色的柔光；玻璃质感让卡片和按钮变成半透明。两个一起开效果最好，低端设备觉得卡可以关掉。"))
            newline()
            newline()
            text(UiText.of("以上都在「设置」→「界面与外观」→「外观设置」里，点下方按钮可直接跳过去。")).italic()
        }
    }

    // ==================== 视频详情页 ====================

    /** 视频详情页 - 主要操作引导（3 页，零碎问题已合并为最后一页）。 */
    val videoMain = tutorial(
        id = "video_main",
        title = UiText.of("视频详情-主要教程"),
        version = 1,
        descriptionRes = R.string.tutorial_desc_video_main,
        target = VideoInfoActivity::class.java,
        kind = TutorialKind.GUIDE,
        imageRes = R.mipmap.tutorial_video,
    ) {
        // 第 1 页：页面结构与基本操作
        page {
            text(UiText.of("欢迎来到视频详情页")).bold().color(HIGHLIGHT)
            newline()
            newline()
            text(UiText.of("这个页面"))
            text(UiText.of("可以左右滑动")).bold().color(ACCENT)
            text(UiText.of("，共三页。"))
            newline()
            newline()
            text(UiText.of("播放")).bold().color(ACCENT)
            text("：")
            text(UiText.of("点击")).bold().color(ACCENT)
            text(UiText.of("播放按钮播放视频，"))
            text(UiText.of("点击或长按")).bold().color(ACCENT)
            text(UiText.of("封面查看大图。"))
            newline()
            text(UiText.of("标签")).bold().color(ACCENT)
            text("：")
            text(UiText.of("点击")).bold().color(ACCENT)
            text(UiText.of("展开，再"))
            text(UiText.of("点标签")).bold().color(ACCENT)
            text(UiText.of("直接跳转搜索。"))
        }
        // 第 2 页：新功能
        page {
            text(UiText.of("视频详情页新增了这些功能")).bold().color(HIGHLIGHT)
            newline()
            newline()
            text("· ")
            text(UiText.of("点击封面")).bold().color(ACCENT)
            text(UiText.of("播放视频，"))
            text(UiText.of("长按封面")).bold().color(ACCENT)
            text(UiText.of("查看大图"))
            newline()
            text("· ")
            text(UiText.of("长按左上角标题栏")).bold().color(ACCENT)
            text(UiText.of("一次性关闭所有视频详情页，直接回到进入视频前的页面"))
            newline()
            text("· ")
            text(UiText.of("点击「视频摘要」")).bold().color(ACCENT)
            text(UiText.of("获取 AI 总结（需登录）"))
            newline()
            text("· ")
            text(UiText.of("点收藏")).bold().color(ACCENT)
            text(UiText.of("弹出的选择页会显示每个收藏夹的数量/上限"))
            newline()
            text(UiText.of("· 开启「隐私模式」后以游客身份请求，隐藏点赞/收藏状态（播放仍用登录身份）"))
        }
        // 第 3 页：注意事项（原「视频详情-零碎问题」独立教程合并至此）
        page {
            text(UiText.of("几点说明")).bold().color(ACCENT)
            newline()
            newline()
            text(UiText.of("· 点赞或投币报错，说明账号可能被风控了（无永久解决方案）")).italic()
            newline()
            text(UiText.of("· 内置播放器退出播放时会上传视频播放进度")).italic()
            newline()
            text("· ")
            text(UiText.of("长按「转发」按钮")).bold().color(ACCENT)
            text(UiText.of("可以复制完整的视频链接")).italic()
            newline()
            newline()
            text(UiText.of("你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！"))
        }
    }

    // ==================== 用户主页 ====================

    /** 用户主页 - 主要操作引导（风控提示已合并到末尾）。 */
    val space = tutorial(
        id = "space",
        title = UiText.of("用户主页-主要教程"),
        version = 1,
        descriptionRes = R.string.tutorial_desc_space,
        target = UserInfoActivity::class.java,
        kind = TutorialKind.GUIDE,
        imageRes = R.mipmap.tutorial_space,
    ) {
        page {
            text(UiText.of("欢迎来到用户主页")).bold().color(HIGHLIGHT)
            newline()
            newline()
            text(UiText.of("这里就是个人空间，"))
            text(UiText.of("可以左右滑动")).bold().color(ACCENT)
            text(UiText.of("，共四页。"))
            newline()
            newline()
            text(UiText.of("四页内容")).bold().color(ACCENT)
            newline()
            text(UiText.of("· 第一页：动态（"))
            text(UiText.of("点击文字部分")).bold().color(ACCENT)
            text(UiText.of("查看详情）"))
            newline()
            text(UiText.of("· 第二、三页：投稿视频、专栏列表"))
            newline()
            text(UiText.of("· 第四页：公开收藏夹（私密收藏夹不会显示）"))
            newline()
            newline()
            text(UiText.of("已登录时还会显示关注和私信按钮。"))
            newline()
            newline()
            text(UiText.of("注意：打开主页报错或列表加载不出来，可能是账号被风控了（无永久解决方法）。")).italic()
        }
    }

    // ==================== 搜索 ====================

    /** 搜索页教程。 */
    val search = tutorial(
        id = "search",
        title = UiText.of("搜索教程"),
        version = 1,
        descriptionRes = R.string.tutorial_desc_search,
        target = SearchActivity::class.java,
        kind = TutorialKind.GUIDE,
        imageRes = R.mipmap.tutorial_search,
    ) {
        page {
            text(UiText.of("搜索结果分为四页")).bold().color(HIGHLIGHT)
            newline()
            text(UiText.of("视频 → 专栏 → 用户 → 直播，"))
            text(UiText.of("左右滑动")).bold().color(ACCENT)
            text(UiText.of("切换。"))
            newline()
            newline()
            text("· ")
            text(UiText.of("输入关键词")).bold().color(ACCENT)
            text(UiText.of("时会自动显示搜索建议"))
            newline()
            text("· ")
            text(UiText.of("长按")).bold().color(ACCENT)
            text(UiText.of("搜索记录可以删除"))
        }
    }

    // ==================== 消息 ====================

    /** 消息页教程。 */
    val message = tutorial(
        id = "message",
        title = UiText.of("消息页面教程"),
        version = 1,
        descriptionRes = R.string.tutorial_desc_message,
        target = MessageActivity::class.java,
        kind = TutorialKind.NOTICE,
    ) {
        page {
            text(UiText.of("欢迎来到消息页面")).bold().color(HIGHLIGHT)
            newline()
            newline()
            text("· ")
            text(UiText.of("长按")).bold().color(ACCENT)
            text(UiText.of("私信列表中的卡片可以跳转到用户详情页"))
            newline()
            text(UiText.of("· 部分点赞、评论通知的内容暂时显示不出来，后续版本会修")).italic()
        }
    }

    // ==================== 动态 ====================

    /** 动态页教程。 */
    val dynamic = tutorial(
        id = "dynamic",
        title = UiText.of("动态页教程"),
        version = 1,
        descriptionRes = R.string.tutorial_desc_dynamic,
        target = DynamicActivity::class.java,
        kind = TutorialKind.NOTICE,
        imageRes = R.mipmap.tutorial_dynamic,
    ) {
        page {
            text(UiText.of("欢迎来到动态页面")).bold().color(HIGHLIGHT)
            newline()
            newline()
            text("· ")
            text(UiText.of("点击动态的文字部分")).bold().color(ACCENT)
            text(UiText.of("可以查看动态详情"))
            newline()
            text(UiText.of("· 部分动态类型暂不支持查看"))
        }
    }

    /** 动态详情页教程。 */
    val dynamicInfo = tutorial(
        id = "dynamic_info",
        title = UiText.of("动态详情页教程"),
        version = 1,
        descriptionRes = R.string.tutorial_desc_dynamic_info,
        target = DynamicInfoActivity::class.java,
        kind = TutorialKind.NOTICE,
        imageRes = R.mipmap.tutorial_dynamic,
    ) {
        page {
            text(UiText.of("欢迎来到动态详情")).bold().color(HIGHLIGHT)
            newline()
            newline()
            text(UiText.of("这个页面"))
            text(UiText.of("可以左右滑动")).bold().color(ACCENT)
            text(UiText.of("，共两页。"))
            newline()
            newline()
            text(UiText.of("· 点赞报错说明账号可能被风控了（无永久解决方案）")).italic()
            newline()
            text(UiText.of("· 部分类型的动态暂不支持查看"))
        }
    }

    // ==================== 专栏 ====================

    /** 专栏详情页教程（旧实现里从未被展示，迁移时补上触发）。 */
    val article = tutorial(
        id = "article",
        title = UiText.of("专栏详情页教程"),
        version = 1,
        descriptionRes = R.string.tutorial_desc_article,
        target = OpusInfoActivity::class.java,
        kind = TutorialKind.NOTICE,
        imageRes = R.mipmap.tutorial_article,
    ) {
        page {
            text(UiText.of("欢迎来到专栏详情页")).bold().color(HIGHLIGHT)
            newline()
            newline()
            text(UiText.of("这个页面"))
            text(UiText.of("可以左右滑动")).bold().color(ACCENT)
            text(UiText.of("，共两页：专栏正文 + 评论区。"))
            newline()
            newline()
            text(UiText.of("· 正文支持"))
            text(UiText.of("引用、分隔线、图片、卡片")).bold()
            text(UiText.of("等富文本排版"))
            newline()
            text(UiText.of("· 暂不显示专栏的文字颜色，部分专栏的排版可能有问题"))
            newline()
            text(UiText.of("· 点赞或投币报错说明账号可能被风控了（无永久解决方案）")).italic()
        }
    }

    // ==================== 短视频 ====================

    /** 短视频操作指南。 */
    val shortVideo = tutorial(
        id = "short_video",
        title = UiText.of("短视频操作指南"),
        // 26.10.21：左右滑改为翻到右侧的详情/评论/相关推荐页；长按改为 3 倍速播放。
        // 内容变了要 +1，老用户才会重新看到。
        version = 4,
        descriptionRes = R.string.tutorial_desc_short_video,
        target = ShortVideoPlayerActivity::class.java,
        kind = TutorialKind.GUIDE,
    ) {
        page {
            text(UiText.of("欢迎来到短视频页面")).bold().color(HIGHLIGHT)
            newline()
            newline()
            text(UiText.of("手势操作")).bold().color(ACCENT)
            newline()
            text("· ")
            text(UiText.of("点按一次")).bold().color(ACCENT)
            text(UiText.of("：显示/隐藏底部控制栏"))
            newline()
            text("· ")
            text(UiText.of("点按两次")).bold().color(ACCENT)
            text(UiText.of("：播放/暂停"))
            newline()
            text("· ")
            text(UiText.of("长按")).bold().color(ACCENT)
            text(UiText.of("：按住时 3 倍速播放，松手恢复正常倍速"))
            newline()
            text("· ")
            text(UiText.of("上下滑动")).bold().color(ACCENT)
            text(UiText.of("：切换上一个/下一个短视频；一条播完会自动播下一条，到底后接着放相关推荐"))
            newline()
            text("· ")
            text(UiText.of("左右滑动")).bold().color(ACCENT)
            text(UiText.of("：滑到右侧查看视频详情、评论和相关推荐；在短视频页右滑退出"))
            newline()
            text("· ")
            text(UiText.of("双指捏合")).bold().color(ACCENT)
            text(UiText.of("：缩放画面"))
            newline()
            text("· ")
            text(UiText.of("点击顶栏")).bold().color(ACCENT)
            text(UiText.of("：返回菜单"))
        }
    }

    // ==================== 缓存 ====================

    /** 缓存页教程。 */
    val local = tutorial(
        id = "local",
        title = UiText.of("缓存与下载"),
        // 26.10.20 改版：长按弹菜单、文件夹卡长按、进文件夹后顶栏变返回箭头、空态改为列表内一行。
        // 内容变了就 +1，老用户才会重新看到这篇。
        version = 2,
        descriptionRes = R.string.tutorial_desc_local,
        target = LocalListActivity::class.java,
        kind = TutorialKind.NOTICE,
    ) {
        page {
            text(UiText.of("欢迎来到缓存页面")).bold().color(HIGHLIGHT)
            newline()
            newline()
            text(UiText.of("· 分两级：未分类视频和自建文件夹，"))
            text(UiText.of("点文件夹卡")).bold().color(ACCENT)
            text(UiText.of("进入，进文件夹后"))
            text(UiText.of("点顶栏的返回箭头")).bold().color(ACCENT)
            text(UiText.of("退回上一级（顶层时顶栏是朝下的箭头，点它打开菜单）"))
            newline()
            text("· ")
            text(UiText.of("下拉")).bold().color(ACCENT)
            text(UiText.of("可以重新扫描已下载的视频"))
            newline()
            text("· ")
            text(UiText.of("点击视频卡")).bold().color(ACCENT)
            text(UiText.of("播放；在文件夹里点一个视频，整个文件夹会当成合集连续播放"))
            newline()
            text("· ")
            text(UiText.of("长按视频卡")).bold().color(ACCENT)
            text(UiText.of("弹出功能菜单：更新弹幕、切换分辨率、移动到文件夹、查看详情、删除视频（会再确认一次）"))
            newline()
            text("· ")
            text(UiText.of("长按文件夹卡")).bold().color(ACCENT)
            text(UiText.of("可以重命名或拆散文件夹"))
            newline()
            text(UiText.of("· 下载会自动合并音视频、自动下载封面/字幕/弹幕，同一个视频不会重复入队"))
            newline()
            text(UiText.of("· 正在下载的任务在「下载列表」里查看进度"))
            newline()
            text(UiText.of("· 一个视频都没有时，列表里会显示一行「啥都木有~」")).italic()
        }
    }

    // ==================== 页面滑动提示（HINT） ====================
    //
    // 这些不是全屏教程：宿主页（带左右分页的详情页 / 搜索页）的布局里有一层
    // `R.id.text_tutorial_pager` 覆盖文字，由 `BaseActivity` 统一触发、`TutorialHints` 渲染。
    // 旧实现是每个页面自己调 `TutorialHelper.showPagerTutorial(activity, 页数)`：
    // 宿主类名、页数全写死在五个调用点上，加一个带分页的页面就要再改一处。
    // 现在宿主类名与提示文案都在这里，页数由 `TutorialHints` 读宿主 ViewPager 的真实页数。
    //
    // 存储键沿用旧格式 `tutorial_pager_<宿主类名>`（见 `TutorialStore.hintKeyOf`），
    // 老用户「看过就不再弹」的状态不会因为这次迁移丢掉。

    private fun hint(id: String, title: String, target: Class<out Activity>): Tutorial = tutorial(
        id = id,
        title = title,
        version = 1,
        target = target,
        kind = TutorialKind.HINT,
        hintTextRes = R.string.tutorial_pager,
        hintViewId = R.id.text_tutorial_pager,
    )

    val hintSearch = hint("hint_search", UiText.of("搜索页面引导"), SearchActivity::class.java)

    val hintVideoInfo = hint("hint_video_info", UiText.of("视频详情页引导"), VideoInfoActivity::class.java)

    val hintUserInfo = hint("hint_user_info", UiText.of("用户主页引导"), UserInfoActivity::class.java)

    val hintOpusInfo = hint("hint_opus_info", UiText.of("专栏详情页引导"), OpusInfoActivity::class.java)

    val hintDynamicInfo =
        hint("hint_dynamic_info", UiText.of("动态详情页引导"), DynamicInfoActivity::class.java)

    private val pageHints: List<Tutorial> = listOf(
        hintSearch,
        hintVideoInfo,
        hintUserInfo,
        hintOpusInfo,
        hintDynamicInfo,
    )

    /** 全部教程。顺序 = 同页串行顺序，也是迁移与覆盖清单的唯一数据源。 */
    val all: List<Tutorial> = listOf(
        recommend,
        themeNotice,
        videoMain,
        space,
        search,
        message,
        dynamic,
        dynamicInfo,
        article,
        shortVideo,
        local,
    ) + pageHints

    /** 全部页面滑动提示（HINT）。管理页按它渲染「页面滑动引导」分组。 */
    val hints: List<Tutorial> = pageHints

    /** 某个页面需要展示的全部教程（含页面滑动提示），按 [all] 的声明顺序返回。 */
    fun forPage(page: Class<out Activity>): List<Tutorial> =
        all.filter { it.target == page }

    /** 按 id 查教程，供管理页「重看」使用。 */
    fun byId(id: String): Tutorial? = all.firstOrNull { it.id == id }
}