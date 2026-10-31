package com.RobinNotBad.BiliClient.activity.settings

import android.app.Activity
import android.content.Intent
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.FeedbackActivity
import com.RobinNotBad.BiliClient.activity.feedback.FeedbackListActivity
import com.RobinNotBad.BiliClient.activity.settings.login.AccountSwitchActivity

/**
 * 全局设置搜索索引：集中维护「分组 / 子页面 / 内联设置项 / 叶子设置项」的可搜索条目。
 *
 * 条目 name 与各设置页中 SettingSection 的 name 保持一致，供搜索命中后跳转定位。
 */
object SettingsIndex {

    data class Entry(
        val name: String,
        val desc: String = "",
        val open: (Activity) -> Unit
    )

    fun build(): List<Entry> {
        val list = ArrayList<Entry>()

        // ---- 一级分组 ----
        list += Entry("播放与播放器", "选择播放器、清晰度与内置播放器设置") { a ->
            a.startActivity(Intent(a, SettingPlayerChooseActivity::class.java))
        }
        list += Entry("账号与登录", "登录、切换账号与查看登录信息") { a -> openGroup(a, "account", "账号与登录") }
        list += Entry("界面与外观", "主题配色、字体与布局适配") { a -> openGroup(a, "ui", "界面与外观") }
        list += Entry("布局适配", "界面大小、边距、圆屏与横屏等屏幕适配") { a -> openGroup(a, "layout", "布局适配") }
        list += Entry("内容与浏览", "菜单、搜索、详情页、评论区与偏好") { a -> openGroup(a, "content", "内容与浏览") }
        list += Entry("通知设置", "新消息、追番更新等通知栏提醒的开关") { a -> openGroup(a, GROUP_NOTIFY, "通知设置") }
        list += Entry(
            BiliTerminal.context!!.getString(R.string.text_setting_disguise_group),
            BiliTerminal.context!!.getString(R.string.desc_setting_disguise_index)
        ) { a ->
            openGroup(a, GROUP_DISGUISE, BiliTerminal.context!!.getString(R.string.text_setting_disguise_group))
        }
        list += Entry("缓存与下载", "下载引擎、缓存选项与存储路径") { a -> openGroup(a, "download", "缓存与下载") }
        list += Entry("高级与实验", "性能、推荐与实验功能") { a -> openGroup(a, "lab", "高级与实验") }
        list += Entry("关于与帮助", "版本信息、更新、公告与教程") { a -> openGroup(a, "about", "关于与帮助") }

        // ---- 子页面 ----
        list += Entry("选择播放器", "切换播放器、视频清晰度") { a ->
            a.startActivity(Intent(a, SettingPlayerChooseActivity::class.java))
        }
        list += Entry("清晰度", "设置默认播放清晰度") { a ->
            a.startActivity(Intent(a, SettingQualityActivity::class.java))
        }
        list += Entry("内置播放器设置", "内置播放器的播放、弹幕、字幕与界面") { a ->
            a.startActivity(Intent(a, SettingTerminalPlayerActivity::class.java))
        }
        list += Entry("登录", "使用哔哩哔哩账号登录") { a -> openGroup(a, "account", "账号与登录", "登录") }
        list += Entry("账号切换", "管理已保存的账号，快速切换登录") { a ->
            a.startActivity(Intent(a, AccountSwitchActivity::class.java))
        }
        list += Entry("查看登录信息", "查看当前账号的登录信息") { a -> openGroup(a, "account", "账号与登录", "查看登录信息") }
        list += Entry("菜单设置", "调整菜单顺序、将菜单项移入未启用") { a ->
            a.startActivity(Intent(a, SettingMenuActivity::class.java))
        }
        list += Entry("我的页面设置", "调整「我的」页面入口顺序与更多列表") { a ->
            a.startActivity(Intent(a, SettingMySpaceActivity::class.java))
        }
        list += Entry("搜索设置", "搜索类别显示与排序") { a ->
            a.startActivity(Intent(a, SettingSearchActivity::class.java))
        }
        list += Entry("详情页设置", "视频详情页、专栏详情页等相关设置") { a ->
            a.startActivity(Intent(a, SettingInfoActivity::class.java))
        }
        list += Entry("评论区设置", "关于评论区的一些设置选项") { a ->
            a.startActivity(Intent(a, SettingRepliesActivity::class.java))
        }
        list += Entry("通用偏好", "一些特殊适配和特殊需求选项") { a ->
            a.startActivity(Intent(a, SettingPrefActivity::class.java))
        }
        list += Entry("关于", "版本号、开发团队、联系方式、开源信息") { a ->
            a.startActivity(Intent(a, AboutActivity::class.java))
        }
        list += Entry("检查更新", "检查新版本并更新") { a ->
            a.startActivity(Intent(a, UpdateActivity::class.java))
        }
        list += Entry("公告列表", "查看哔哩终端发布公告") { a ->
            a.startActivity(Intent(a, AnnouncementsActivity::class.java))
        }
        // 「反馈与统计」（26.10.16 起独立成屏）：入口 + 叶子项，全局搜索都要能找到。
        // 叶子项靠 highlight 按项名滚动定位，所以名字必须与 buildFeedbackGroup() 里的
        // SettingSection.name 严格一致（同「通知设置」那两条的做法）。
        list += Entry("反馈与统计", "反馈与建议、我的反馈、匿名统计与崩溃上报") { a ->
            openGroup(a, GROUP_FEEDBACK, "反馈与统计")
        }
        list += Entry("反馈与建议", "直接发送给本项目的开发者（自建通道，无需登录）") { a ->
            a.startActivity(Intent(a, FeedbackActivity::class.java))
        }
        // 「我的反馈」（26.10.16 新增）：独立 Activity 子页面，按 AGENTS 约定要进搜索索引
        list += Entry("我的反馈", "查看你提交过的反馈与开发者的回复") { a ->
            a.startActivity(Intent(a, FeedbackListActivity::class.java))
        }
        list += Entry("匿名使用统计", "每天最多上报一次「随机安装号 + 版本号 + 机型」") { a ->
            openGroup(a, GROUP_FEEDBACK, "反馈与统计", "匿名使用统计")
        }
        list += Entry("崩溃自动上报", "崩溃时自动把堆栈、机型、页面记录与应用日志发到自建服务器") { a ->
            openGroup(a, GROUP_FEEDBACK, "反馈与统计", "崩溃自动上报")
        }
        list += Entry("反馈附带账号 ID", "打开后反馈与崩溃报告会附上你的 UID") { a ->
            openGroup(a, GROUP_FEEDBACK, "反馈与统计", "反馈附带账号 ID")
        }
        list += Entry("教程管理", "管理各页面的新手教程进度") { a ->
            a.startActivity(Intent(a, TutorialManagerActivity::class.java))
        }

        // ---- 界面与外观 内联项 ----
        list += Entry("圆屏适配", "适配圆形屏幕") { a -> openGroup(a, "layout", "布局适配", "圆屏适配") }
        list += Entry("界面大小", "调整应用界面缩放大小") { a -> openGroup(a, "layout", "布局适配", "界面大小") }
        list += Entry("界面边距（横向）", "单位百分比，用于圆屏适配") { a -> openGroup(a, "layout", "布局适配", "界面边距（横向）") }
        list += Entry("界面边距（纵向）", "单位百分比，用于圆屏适配") { a -> openGroup(a, "layout", "布局适配", "界面边距（纵向）") }
        list += Entry("设置Density", "手动指定屏幕密度") { a -> openGroup(a, "layout", "布局适配", "设置Density") }
        list += Entry("外观设置", "主题配色、卡片圆角、自定义字体") { a -> openGroup(a, GROUP_APPEARANCE, "外观设置") }
        list += Entry("颜色模式", "暗色 / 亮色模式切换") { a -> openGroup(a, GROUP_APPEARANCE, "外观设置", "颜色模式") }
        list += Entry("主题配色", "选择应用的主题配色方案") { a -> openGroup(a, GROUP_APPEARANCE, "外观设置", "主题配色") }
        list += Entry("弥散光晕", "页面边缘的主题色光晕背景") { a -> openGroup(a, GROUP_APPEARANCE, "外观设置", "弥散光晕") }
        list += Entry(
            BiliTerminal.context!!.getString(R.string.text_setting_ambient_glow_source),
            BiliTerminal.context!!.getString(R.string.desc_setting_ambient_glow_source_index)
        ) { a ->
            openGroup(a, GROUP_APPEARANCE, "外观设置", BiliTerminal.context!!.getString(R.string.text_setting_ambient_glow_source))
        }
        list += Entry("玻璃质感", "卡片与按钮半透明，让光晕透进来") { a -> openGroup(a, GROUP_APPEARANCE, "外观设置", "玻璃质感") }
        list += Entry("卡片圆角", "调整卡片与按钮的圆角") { a -> openGroup(a, GROUP_APPEARANCE, "外观设置", "卡片圆角") }
        list += Entry("横屏模式", "启用横屏显示") { a -> openGroup(a, "layout", "布局适配", "横屏模式") }
        list += Entry("开屏文字", "自定义开屏显示文字") { a -> openGroup(a, "ui", "界面与外观", "开屏文字") }
        list += Entry("文字跑马灯", "文字跑马灯效果") { a -> openGroup(a, "ui", "界面与外观", "文字跑马灯") }
        list += Entry("加载渐入渐出动画", "页面加载渐入渐出") { a -> openGroup(a, "ui", "界面与外观", "加载渐入渐出动画") }

        // ---- 缓存与下载 内联项 ----
        list += Entry("启用高速下载模式", "多线程高速下载引擎") { a -> openGroup(a, "download", "缓存与下载", "启用高速下载模式") }
        list += Entry("使用旧版下载器", "切换为旧版下载实现") { a -> openGroup(a, "download", "缓存与下载", "使用旧版下载器") }
        list += Entry("最大同时下载数", "同时进行的最大下载任务数量") { a -> openGroup(a, "download", "缓存与下载", "最大同时下载数") }
        list += Entry("分片数", "单个文件下载使用的分片数量") { a -> openGroup(a, "download", "缓存与下载", "分片数") }
        list += Entry("启用快捷缓存方式", "长按视频快速缓存") { a -> openGroup(a, "download", "缓存与下载", "启用快捷缓存方式") }
        list += Entry("并行下载视频数", "后台并行下载的视频数量") { a -> openGroup(a, "download", "缓存与下载", "并行下载视频数") }
        list += Entry("默认缓存质量", "缓存视频的默认清晰度") { a -> openGroup(a, "download", "缓存与下载", "默认缓存质量") }
        list += Entry("强制高分辨率选项", "强制显示4K、1080P高码率等选项") { a -> openGroup(a, "download", "缓存与下载", "强制高分辨率选项") }
        list += Entry("缓存路径", "视频缓存保存路径") { a -> openGroup(a, "download", "缓存与下载", "缓存路径") }
        list += Entry("图片下载路径", "图片下载保存路径") { a -> openGroup(a, "download", "缓存与下载", "图片下载路径") }

        // ---- 高级与实验 内联项 ----
        list += Entry("高性能模式", "使用更多系统资源提升运行速度") { a -> openGroup(a, "lab", "高级与实验", "高性能模式") }
        list += Entry("推荐视频去重", "过滤重复的推荐视频") { a -> openGroup(a, "lab", "高级与实验", "推荐视频去重") }
        list += Entry("推荐源", "选择获取推荐视频的API") { a -> openGroup(a, "lab", "高级与实验", "推荐源") }
        list += Entry("新版弹幕获取方式", "切换弹幕获取API") { a -> openGroup(a, "lab", "高级与实验", "新版弹幕获取方式") }
        list += Entry("私信未读标记", "私信未读标记") { a -> openGroup(a, "lab", "高级与实验", "私信未读标记") }
        list += Entry("虚拟合集", "本地缓存/收藏夹自动组成合集") { a -> openGroup(a, "lab", "高级与实验", "虚拟合集") }
        list += Entry("播放器旋屏兼容方案", "软件旋屏兼容") { a -> openGroup(a, "lab", "高级与实验", "播放器旋屏兼容方案") }
        list += Entry("显示视频分段", "显示章节看点信息") { a -> openGroup(a, "lab", "高级与实验", "显示视频分段") }
        list += Entry("系统媒体控件", "系统媒体会话控件") { a -> openGroup(a, "lab", "高级与实验", "系统媒体控件") }
        list += Entry("互动视频调试", "互动视频变量调试") { a -> openGroup(a, "lab", "高级与实验", "互动视频调试") }
        list += Entry("圆屏列表", "圆屏模式的滚动列表曲率效果") { a -> openGroup(a, "lab", "高级与实验", "圆屏列表") }

        // ---- 叶子设置项（独立设置页）----
        addLeafItems(list, "详情页设置", SettingInfoActivity::class.java, listOf(
            "收藏夹单选", "收藏成功提示", "点击封面播放", "显示视频标签",
            "视频相关推荐", "以游客方式观看直播", "一键三连"
        ))
        addLeafItems(list, "评论区设置", SettingRepliesActivity::class.java, listOf(
            "众生平等", "粉丝铭牌消失术", "昵称不换行显示"
        ))
        addLeafItems(list, "通用偏好", SettingPrefActivity::class.java, listOf(
            "长按复制", "长按打开操作面板", "长按顶部标题栏", "创作中心", "搜索建议", "默认搜索内容", "识别链接", "隐私模式",
            "新动态数量检查", "消息数量检查", "最近更新的UP主", "私信自动已读", "夜深了", "后台自动检查更新",
            "禁用返回键", "禁止视频在相册中显示", "请求JPG格式图片", "翻动时不加载图片",
            "异步加载布局", "新提示信息显示方式", "我的关注列表分组",
            "启用表冠适配", "表冠适配灵敏度（Recycler）", "表冠适配灵敏度（Scroll）"
        ))
        // 「通知设置」分组的叶子项：全局搜索要能找到这两个开关。
        // 跳转目标是 SettingGroupActivity（分组页），靠 highlight 按项名滚动定位——
        // 所以这里的名字必须与 buildNotifyGroup() 里的 SettingSection.name 严格一致。
        list += Entry("新消息通知", "发现新消息时在通知栏提醒") { a -> openGroup(a, GROUP_NOTIFY, "通知设置", "新消息通知") }
        list += Entry("追番更新提醒", "追的番剧有新一集时在通知栏提醒") { a -> openGroup(a, GROUP_NOTIFY, "通知设置", "追番更新提醒") }

        addLeafItems(list, "内置播放器设置", SettingTerminalPlayerActivity::class.java, listOf(
            "长按倍速", "双击快进快退", "有缩放时，双击优先恢复缩放", "快进快退秒数",
            "后台/熄屏继续播放",
            // 「播放默认值」分组：开播时自动应用，各项名字与页内 SettingSection.name 严格一致（搜索靠名字定位）
            "播放默认值", "弹幕", "听视频模式", "循环播放", "自动连播", "倍速", "字幕", "屏幕方向",
            "从历史位置播放", "显示实时人数",
            "视频可缩放", "缩放时可移动", "显示方式", "解码方式", "音频输出",
            // 「自动跳过片头片尾」26.10.10 撤回时摘掉过，26.10.22 恢复（默认关闭）。
            // 关键词必须与页内 SettingSection.name 严格一致，否则搜索命中定位不到。
            "显示高能进度条", "自动跳过片头片尾", "弹幕允许重叠", "合并重复弹幕", "强制为滚动弹幕",
            "显示直播弹幕发送者", "弹幕最大行数", "弹幕字号大小", "弹幕不透明度", "弹幕速度",
            "自动弹出字幕选择", "允许仅AI字幕", "字幕校准", "字幕字号大小",
            "显示旋转按钮", "显示弹幕按钮", "显示清晰度按钮", "显示分P按钮", "互动选项字体大小"
        ))

        // ---- 开发者工具（仅 Debug）----
        // 用 BuildConfig.DEBUG（编译期常量）而不是 BiliTerminal.isDebugBuild()（运行期判断）：
        // release 下 R8 会把这一整个分支折叠掉，TestActivity 随之成为无用类被 strip。
        if (BuildConfig.DEBUG) {
            list += Entry("开发者工具", "功能测试、待办清单与调试日志") { a -> openGroup(a, "dev", "开发者工具") }
            list += Entry("功能测试", "测试各项功能是否正常") { a ->
                a.startActivity(Intent(a, TestActivity::class.java))
            }
            list += Entry("TO DO清单", "开发者的愿望清单") { a ->
                a.startActivity(Intent(a, TodoListActivity::class.java))
            }
        }

        return list
    }

    private fun openGroup(activity: Activity, type: String, title: String, highlight: String? = null) {
        val intent = Intent(activity, SettingGroupActivity::class.java)
        intent.putExtra("group_type", type)
        intent.putExtra("group_title", SettingText.resolve(activity, title))
        if (highlight != null) intent.putExtra("highlight", highlight)
        activity.startActivity(intent)
    }

    private fun openPage(activity: Activity, cls: Class<*>, highlight: String) {
        val intent = Intent(activity, cls)
        intent.putExtra("highlight", highlight)
        activity.startActivity(intent)
    }

    private fun addLeafItems(list: MutableList<Entry>, page: String, cls: Class<*>, items: List<String>) {
        for (name in items) {
            list += Entry(name, page) { a -> openPage(a, cls, name) }
        }
    }
}
