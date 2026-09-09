package com.RobinNotBad.BiliClient.tutorial

import android.app.Activity
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.article.OpusInfoActivity
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicActivity
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicInfoActivity
import com.RobinNotBad.BiliClient.activity.message.MessageActivity
import com.RobinNotBad.BiliClient.activity.search.SearchActivity
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
 * - 正文默认白色，一页里强调色不超过两处小标题。
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
        title = "推荐页面-重要教程",
        version = 1,
        target = RecommendActivity::class.java,
        kind = TutorialKind.GUIDE,
        imageRes = R.mipmap.tutorial_recommend,
    ) {
        page {
            text("欢迎来到推荐页面").bold().color(HIGHLIGHT)
            newline()
            newline()
            text("顶栏").bold().color(ACCENT)
            text("：")
            text("点击").bold().color(ACCENT)
            text("可以返回上一页或打开菜单，")
            text("整个顶栏都能点").bold()
            text("，不必一直费劲地点左上角。")
            newline()
            text("列表").bold().color(ACCENT)
            text("：")
            text("划到顶部").bold().color(ACCENT)
            text("可以")
            text("下拉刷新").bold().color(ACCENT)
            text("，同一个视频不会重复出现。")
            newline()
            text("视频卡片").bold().color(ACCENT)
            text("：")
            text("点击").bold().color(ACCENT)
            text("进入详情页，")
            text("长按").bold().color(ACCENT)
            text("可以直接快速缓存（清晰度按设置里的「快捷缓存方式」决定）。")
            newline()
            newline()
            text("你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！我们不希望你成为下一位来问我们“评论区怎么看”这类已有教程的问题的人！")
        }
    }

    /** 推荐页 - 主题变更说明（版本说明类，可跳过）。 */
    val themeNotice = tutorial(
        id = "theme_notice",
        title = "主题说明",
        version = 1,
        target = RecommendActivity::class.java,
        kind = TutorialKind.NOTICE,
    ) {
        page {
            text("本次更新说明").bold().color(HIGHLIGHT)
            newline()
            newline()
            text("默认主题已经改回")
            text("经典终端风格").bold().color(HIGHLIGHT)
            text("，也就是黑色背景 + 粉色点缀的原始外观。")
            newline()
            newline()
            text("想换回其它配色").bold().color(ACCENT)
            newline()
            text("进入「设置」→「界面与外观」→「主题配色」，可以自由切换任意主题（哔哩粉、知乎蓝、经典灰等）。")
            newline()
            newline()
            text("主题修改后会立即应用到全部页面。")
        }
    }

    // ==================== 视频详情页 ====================

    /** 视频详情页 - 主要操作引导（3 页，零碎问题已合并为最后一页）。 */
    val videoMain = tutorial(
        id = "video_main",
        title = "视频详情-主要教程",
        version = 1,
        target = VideoInfoActivity::class.java,
        kind = TutorialKind.GUIDE,
        imageRes = R.mipmap.tutorial_video,
    ) {
        // 第 1 页：页面结构与基本操作
        page {
            text("欢迎来到视频详情页").bold().color(HIGHLIGHT)
            newline()
            newline()
            text("这个页面")
            text("可以左右滑动").bold().color(ACCENT)
            text("，共三页。")
            newline()
            newline()
            text("播放").bold().color(ACCENT)
            text("：")
            text("点击").bold().color(ACCENT)
            text("播放按钮播放视频，")
            text("点击或长按").bold().color(ACCENT)
            text("封面查看大图。")
            newline()
            text("标签").bold().color(ACCENT)
            text("：")
            text("点击").bold().color(ACCENT)
            text("展开，再")
            text("点标签").bold().color(ACCENT)
            text("直接跳转搜索。")
        }
        // 第 2 页：新功能
        page {
            text("视频详情页新增了这些功能").bold().color(HIGHLIGHT)
            newline()
            newline()
            text("· ")
            text("点击封面").bold().color(ACCENT)
            text("播放视频，")
            text("长按封面").bold().color(ACCENT)
            text("查看大图")
            newline()
            text("· ")
            text("长按左上角标题栏").bold().color(ACCENT)
            text("一次性关闭所有视频详情页，直接回到进入视频前的页面")
            newline()
            text("· ")
            text("点击「视频摘要」").bold().color(ACCENT)
            text("获取 AI 总结（需登录）")
            newline()
            text("· ")
            text("点收藏").bold().color(ACCENT)
            text("弹出的选择页会显示每个收藏夹的数量/上限")
            newline()
            text("· 开启「隐私模式」后以游客身份请求，隐藏点赞/收藏状态（播放仍用登录身份）")
        }
        // 第 3 页：注意事项（原「视频详情-零碎问题」独立教程合并至此）
        page {
            text("几点说明").bold().color(ACCENT)
            newline()
            newline()
            text("· 点赞或投币报错，说明账号可能被风控了（无永久解决方案）").italic()
            newline()
            text("· 内置播放器退出播放时会上传视频播放进度").italic()
            newline()
            text("· ")
            text("长按「转发」按钮").bold().color(ACCENT)
            text("可以复制完整的视频链接").italic()
            newline()
            newline()
            text("你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！")
        }
    }

    // ==================== 用户主页 ====================

    /** 用户主页 - 主要操作引导（风控提示已合并到末尾）。 */
    val space = tutorial(
        id = "space",
        title = "用户主页-主要教程",
        version = 1,
        target = UserInfoActivity::class.java,
        kind = TutorialKind.GUIDE,
        imageRes = R.mipmap.tutorial_space,
    ) {
        page {
            text("欢迎来到用户主页").bold().color(HIGHLIGHT)
            newline()
            newline()
            text("这里就是个人空间，")
            text("可以左右滑动").bold().color(ACCENT)
            text("，共四页。")
            newline()
            newline()
            text("四页内容").bold().color(ACCENT)
            newline()
            text("· 第一页：动态（")
            text("点击文字部分").bold().color(ACCENT)
            text("查看详情）")
            newline()
            text("· 第二、三页：投稿视频、专栏列表")
            newline()
            text("· 第四页：公开收藏夹（私密收藏夹不会显示）")
            newline()
            newline()
            text("已登录时还会显示关注和私信按钮。")
            newline()
            newline()
            text("注意：打开主页报错或列表加载不出来，可能是账号被风控了（无永久解决方法）。").italic()
        }
    }

    // ==================== 搜索 ====================

    /** 搜索页教程。 */
    val search = tutorial(
        id = "search",
        title = "搜索教程",
        version = 1,
        target = SearchActivity::class.java,
        kind = TutorialKind.GUIDE,
        imageRes = R.mipmap.tutorial_search,
    ) {
        page {
            text("搜索结果分为四页").bold().color(HIGHLIGHT)
            newline()
            text("视频 → 专栏 → 用户 → 直播，")
            text("左右滑动").bold().color(ACCENT)
            text("切换。")
            newline()
            newline()
            text("· ")
            text("输入关键词").bold().color(ACCENT)
            text("时会自动显示搜索建议")
            newline()
            text("· ")
            text("长按").bold().color(ACCENT)
            text("搜索记录可以删除")
        }
    }

    // ==================== 消息 ====================

    /** 消息页教程。 */
    val message = tutorial(
        id = "message",
        title = "消息页面教程",
        version = 1,
        target = MessageActivity::class.java,
        kind = TutorialKind.NOTICE,
    ) {
        page {
            text("欢迎来到消息页面").bold().color(HIGHLIGHT)
            newline()
            newline()
            text("· ")
            text("长按").bold().color(ACCENT)
            text("私信列表中的卡片可以跳转到用户详情页")
            newline()
            text("· 部分点赞、评论通知的内容暂时显示不出来，后续版本会修").italic()
        }
    }

    // ==================== 动态 ====================

    /** 动态页教程。 */
    val dynamic = tutorial(
        id = "dynamic",
        title = "动态页教程",
        version = 1,
        target = DynamicActivity::class.java,
        kind = TutorialKind.NOTICE,
        imageRes = R.mipmap.tutorial_dynamic,
    ) {
        page {
            text("欢迎来到动态页面").bold().color(HIGHLIGHT)
            newline()
            newline()
            text("· ")
            text("点击动态的文字部分").bold().color(ACCENT)
            text("可以查看动态详情")
            newline()
            text("· 部分动态类型暂不支持查看")
        }
    }

    /** 动态详情页教程。 */
    val dynamicInfo = tutorial(
        id = "dynamic_info",
        title = "动态详情页教程",
        version = 1,
        target = DynamicInfoActivity::class.java,
        kind = TutorialKind.NOTICE,
        imageRes = R.mipmap.tutorial_dynamic,
    ) {
        page {
            text("欢迎来到动态详情").bold().color(HIGHLIGHT)
            newline()
            newline()
            text("这个页面")
            text("可以左右滑动").bold().color(ACCENT)
            text("，共两页。")
            newline()
            newline()
            text("· 点赞报错说明账号可能被风控了（无永久解决方案）").italic()
            newline()
            text("· 部分类型的动态暂不支持查看")
        }
    }

    // ==================== 专栏 ====================

    /** 专栏详情页教程（旧实现里从未被展示，迁移时补上触发）。 */
    val article = tutorial(
        id = "article",
        title = "专栏详情页教程",
        version = 1,
        target = OpusInfoActivity::class.java,
        kind = TutorialKind.NOTICE,
        imageRes = R.mipmap.tutorial_article,
    ) {
        page {
            text("欢迎来到专栏详情页").bold().color(HIGHLIGHT)
            newline()
            newline()
            text("这个页面")
            text("可以左右滑动").bold().color(ACCENT)
            text("，共两页：专栏正文 + 评论区。")
            newline()
            newline()
            text("· 正文支持")
            text("引用、分隔线、图片、卡片").bold()
            text("等富文本排版")
            newline()
            text("· 暂不显示专栏的文字颜色，部分专栏的排版可能有问题")
            newline()
            text("· 点赞或投币报错说明账号可能被风控了（无永久解决方案）").italic()
        }
    }

    // ==================== 短视频 ====================

    /** 短视频操作指南。 */
    val shortVideo = tutorial(
        id = "short_video",
        title = "短视频操作指南",
        version = 1,
        target = ShortVideoPlayerActivity::class.java,
        kind = TutorialKind.GUIDE,
    ) {
        page {
            text("欢迎来到短视频页面").bold().color(HIGHLIGHT)
            newline()
            newline()
            text("手势操作").bold().color(ACCENT)
            newline()
            text("· ")
            text("点按一次").bold().color(ACCENT)
            text("：显示/隐藏底部控制栏")
            newline()
            text("· ")
            text("点按两次").bold().color(ACCENT)
            text("：播放/暂停")
            newline()
            text("· ")
            text("长按").bold().color(ACCENT)
            text("：打开视频详情页")
            newline()
            text("· ")
            text("上下滑动").bold().color(ACCENT)
            text("：切换上一个/下一个短视频")
            newline()
            text("· ")
            text("双指捏合").bold().color(ACCENT)
            text("：缩放画面")
            newline()
            text("· ")
            text("点击顶栏").bold().color(ACCENT)
            text("：返回菜单")
        }
    }

    // ==================== 缓存 ====================

    /** 缓存页教程。 */
    val local = tutorial(
        id = "local",
        title = "缓存与下载",
        version = 1,
        target = LocalListActivity::class.java,
        kind = TutorialKind.NOTICE,
    ) {
        page {
            text("欢迎来到缓存页面").bold().color(HIGHLIGHT)
            newline()
            newline()
            text("· 分两级：未分类视频和自建文件夹，")
            text("点文件夹").bold().color(ACCENT)
            text("进入，")
            text("点顶栏").bold().color(ACCENT)
            text("退回上一级")
            newline()
            text("· ")
            text("下拉").bold().color(ACCENT)
            text("可以重新扫描已下载的视频")
            newline()
            text("· ")
            text("点击视频").bold().color(ACCENT)
            text("播放；在文件夹里点一个视频，整个文件夹会当成合集连续播放")
            newline()
            text("· ")
            text("向左滑动视频卡片").bold().color(ACCENT)
            text("打开操作面板：更新弹幕、切换清晰度、移动文件夹、查看详情")
            newline()
            text("· 下载会自动合并音视频、自动下载封面/字幕/弹幕，同一个视频不会重复入队")
            newline()
            text("· 正在下载的任务在「下载列表」里查看进度")
        }
    }

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
    )

    /** 某个页面需要展示的全屏教程（HINT 类型不走这里）。 */
    fun forPage(page: Class<out Activity>): List<Tutorial> =
        all.filter { it.target == page && it.isFullScreen }

    /** 按 id 查教程，供管理页「重看」使用。 */
    fun byId(id: String): Tutorial? = all.firstOrNull { it.id == id }
}
