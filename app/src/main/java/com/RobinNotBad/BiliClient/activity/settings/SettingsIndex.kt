package com.RobinNotBad.BiliClient.activity.settings

import android.app.Activity
import android.content.Intent
import com.RobinNotBad.BiliClient.BiliTerminal
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
        list += Entry("界面与外观", "界面大小、主题与动画效果") { a -> openGroup(a, "ui", "界面与外观") }
        list += Entry("内容与浏览", "菜单、搜索、详情页、评论区与偏好") { a -> openGroup(a, "content", "内容与浏览") }
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
        list += Entry("教程管理", "管理各页面的新手教程进度") { a ->
            a.startActivity(Intent(a, TutorialManagerActivity::class.java))
        }

        // ---- 界面与外观 内联项 ----
        list += Entry("圆屏适配", "适配圆形屏幕") { a -> openGroup(a, "ui", "界面与外观", "圆屏适配") }
        list += Entry("界面大小", "调整应用界面缩放大小") { a -> openGroup(a, "ui", "界面与外观", "界面大小") }
        list += Entry("界面边距（横向）", "单位百分比，用于圆屏适配") { a -> openGroup(a, "ui", "界面与外观", "界面边距（横向）") }
        list += Entry("界面边距（纵向）", "单位百分比，用于圆屏适配") { a -> openGroup(a, "ui", "界面与外观", "界面边距（纵向）") }
        list += Entry("设置Density", "手动指定屏幕密度") { a -> openGroup(a, "ui", "界面与外观", "设置Density") }
        list += Entry("主题配色", "选择应用的主题配色方案") { a -> openGroup(a, "ui", "界面与外观", "主题配色") }
        list += Entry("横屏模式", "启用横屏显示") { a -> openGroup(a, "ui", "界面与外观", "横屏模式") }
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

        // ---- 叶子设置项（独立设置页）----
        addLeafItems(list, "详情页设置", SettingInfoActivity::class.java, listOf(
            "收藏夹单选", "收藏成功提示", "点击封面播放", "显示视频标签",
            "视频相关推荐", "以游客方式观看直播", "一键三连"
        ))
        addLeafItems(list, "评论区设置", SettingRepliesActivity::class.java, listOf(
            "众生平等", "粉丝铭牌消失术", "昵称不换行显示"
        ))
        addLeafItems(list, "通用偏好", SettingPrefActivity::class.java, listOf(
            "长按复制", "创作中心", "搜索建议", "默认搜索内容", "识别链接", "隐私模式",
            "新动态数量检查", "消息数量检查", "最近更新的UP主", "私信自动已读", "夜深了", "后台自动检查更新",
            "禁用返回键", "禁止视频在相册中显示", "请求JPG格式图片", "翻动时不加载图片",
            "异步加载布局", "新提示信息显示方式", "我的关注列表分组",
            "启用表冠适配", "表冠适配灵敏度（Recycler）", "表冠适配灵敏度（Scroll）"
        ))
        addLeafItems(list, "内置播放器设置", SettingTerminalPlayerActivity::class.java, listOf(
            "长按倍速", "双击快进快退", "双击优先还原屏幕", "快进快退秒数", "洗脑循环",
            "熄屏继续播放", "默认横屏", "从历史位置播放", "显示实时人数", "听视频模式",
            "视频可缩放", "缩放时可移动", "显示方式", "解码方式", "音频输出",
            "显示高能进度条", "弹幕允许重叠", "合并重复弹幕", "强制为滚动弹幕",
            "显示直播弹幕发送者", "弹幕最大行数", "弹幕字号大小", "弹幕不透明度", "弹幕速度",
            "自动弹出字幕选择", "允许仅AI字幕", "字幕校准",
            "显示旋转按钮", "显示弹幕按钮", "显示清晰度按钮", "显示分P按钮", "互动选项字体大小"
        ))

        // ---- 开发者工具（仅 Debug）----
        if (BiliTerminal.isDebugBuild()) {
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
        intent.putExtra("group_title", title)
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
