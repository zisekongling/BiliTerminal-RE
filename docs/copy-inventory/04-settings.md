<!-- markdownlint-disable MD004 MD032 MD037 -->
<!-- 本文件逐字照抄用户可见文案（素材而非正文）：行首的 `*`、`+`、`##` 与引用块内的列表都是内容，不是 Markdown 格式，故豁免上面这几条结构规则。 -->

# 04 · 设置项全部文案

> 收录范围：一级分组 / 二级分组 / 子页面 / 每个设置条目的名称与说明 / 候选值 / 设置搜索词
> 编号前缀：`S`
> 生成：2026-10-18 · 基于当前工作区
> 计数：共 386 条（S-001 ~ S-386）
> **改写入口**：每条最后的 `- **改写**：` 里**已经预填了原文**（2026-10-19 起），你直接在原地改成新文案，再把该条标题加上 `〔待落地〕`；怎么配合见 [GUIDE.md](GUIDE.md) 第十一节。

阅读说明：

- 本节「位置」的行号以当前工作区为准（`res/values/strings.xml` 与 `activity/settings/*.kt` 的行号均已逐一 `read` 核对）。
- strings.xml 里的 `\n` 已按真实换行拆成连续 `>` 引用行，字面内容未改动。
- 「另见」表示同一段文案在另一份清点文档里也会出现（宁重复勿遗漏）。

---

## 一、设置主页（八个一级分组）

### S-001 设置页标题「设置」

- **位置**：`app/src/main/res/layout/activity_setting_main.xml:26`（`@string/pagename_setting_main` → `app/src/main/res/values/strings.xml:249`）
- **出现在哪**：设置主页顶栏标题（点菜单「设置」进入后第一眼看到的字样）
- **控件类型**：页面标题文字
- **用途**：标明当前页面
- **原文**：

> 设置

- **改写**：设置

### S-002 设置页搜索框提示语

- **位置**：`app/src/main/res/layout/activity_setting_main.xml:48`
- **出现在哪**：设置主页 → 顶栏下方输入框的灰色占位提示
- **控件类型**：输入框占位提示（`android:hint`）
- **用途**：告诉用户这里可以直接搜设置项
- **原文**：

> 搜索设置项

- **改写**：搜索设置项

### S-003 一级分组名「播放与播放器」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:117`
- **出现在哪**：设置 → 主页第一张卡片标题
- **控件类型**：跳转子页（卡片，点进「选择播放器」页）
- **用途**：一级分组名，用户第一眼的分组锚点
- **原文**：

> 播放与播放器

- **改写**：播放与播放器

### S-004 一级分组说明「播放与播放器」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:118`
- **出现在哪**：设置 → 主页 → 「播放与播放器」卡片标题下的灰色说明（跑马灯单行）
- **控件类型**：说明文字（卡片副标题，单行，会被省略）
- **用途**：一句话说清这个分组里有什么
- **原文**：

> 选择播放器、清晰度与内置播放器设置

- **改写**：选择播放器、清晰度与内置播放器设置

### S-005 一级分组名「账号与登录」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:129`
- **出现在哪**：设置 → 主页第二张卡片标题
- **控件类型**：跳转子页（卡片，进「账号与登录」分组页）
- **用途**：一级分组名
- **原文**：

> 账号与登录

- **改写**：账号与登录

### S-006 一级分组说明「账号与登录」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:130`
- **出现在哪**：设置 → 主页 → 「账号与登录」卡片说明
- **控件类型**：说明文字（单行）
- **用途**：一句话说清分组内容
- **原文**：

> 登录、切换账号与查看登录信息

- **改写**：登录、切换账号与查看登录信息

### S-007 一级分组名「界面与外观」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:138`
- **出现在哪**：设置 → 主页第三张卡片标题
- **控件类型**：跳转子页（卡片）
- **用途**：一级分组名
- **原文**：

> 界面与外观

- **改写**：界面与外观

### S-008 一级分组说明「界面与外观」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:139`
- **出现在哪**：设置 → 主页 → 「界面与外观」卡片说明
- **控件类型**：说明文字（单行）
- **用途**：一句话说清分组内容
- **原文**：

> 界面大小、主题与动画效果

- **改写**：界面大小、主题与动画效果

### S-009 一级分组名「内容与浏览」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:147`
- **出现在哪**：设置 → 主页第四张卡片标题
- **控件类型**：跳转子页（卡片）
- **用途**：一级分组名
- **原文**：

> 内容与浏览

- **改写**：内容与浏览

### S-010 一级分组说明「内容与浏览」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:148`
- **出现在哪**：设置 → 主页 → 「内容与浏览」卡片说明
- **控件类型**：说明文字（单行）
- **用途**：一句话说清分组内容
- **原文**：

> 菜单、搜索、详情页、评论区与偏好

- **改写**：菜单、搜索、详情页、评论区与偏好

### S-011 一级分组名「通知设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:162`
- **出现在哪**：设置 → 主页第五张卡片标题
- **控件类型**：跳转子页（卡片）
- **用途**：一级分组名（用户反馈后新增的一级分组）
- **原文**：

> 通知设置

- **改写**：通知设置

### S-012 一级分组说明「通知设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:163`
- **出现在哪**：设置 → 主页 → 「通知设置」卡片说明
- **控件类型**：说明文字（单行）
- **用途**：一句话说清分组内容
- **原文**：

> 新消息、追番更新等通知栏提醒的开关

- **改写**：新消息、追番更新等通知栏提醒的开关

### S-013 一级分组名「缓存与下载」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:171`
- **出现在哪**：设置 → 主页第六张卡片标题
- **控件类型**：跳转子页（卡片）
- **用途**：一级分组名
- **原文**：

> 缓存与下载

- **改写**：缓存与下载

### S-014 一级分组说明「缓存与下载」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:172`
- **出现在哪**：设置 → 主页 → 「缓存与下载」卡片说明
- **控件类型**：说明文字（单行）
- **用途**：一句话说清分组内容
- **原文**：

> 下载引擎、缓存选项与存储路径

- **改写**：下载引擎、缓存选项与存储路径

### S-015 一级分组名「高级与实验」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:180`
- **出现在哪**：设置 → 主页第七张卡片标题
- **控件类型**：跳转子页（卡片）
- **用途**：一级分组名
- **原文**：

> 高级与实验

- **改写**：高级与实验

### S-016 一级分组说明「高级与实验」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:181`
- **出现在哪**：设置 → 主页 → 「高级与实验」卡片说明
- **控件类型**：说明文字（单行）
- **用途**：提示这里的东西"不保证能用"（与搜索索引里的说明不一致，见 S-336）
- **原文**：

> 不保证能用或者用于开发调试的功能

- **改写**：不保证能用或者用于开发调试的功能

### S-017 一级分组名「关于与帮助」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:189`
- **出现在哪**：设置 → 主页第八张卡片标题
- **控件类型**：跳转子页（卡片）
- **用途**：一级分组名
- **原文**：

> 关于与帮助

- **改写**：关于与帮助

### S-018 一级分组说明「关于与帮助」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:190`
- **出现在哪**：设置 → 主页 → 「关于与帮助」卡片说明
- **控件类型**：说明文字（单行）
- **用途**：一句话说清分组内容
- **原文**：

> 版本信息、更新、公告与教程

- **改写**：版本信息、更新、公告与教程

### S-019 一级分组名「开发者工具」（仅调试包）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:203`
- **出现在哪**：设置 → 主页第九张卡片（**只在 `BiliTerminal.isDebugBuild()` 为真时出现**）
- **控件类型**：跳转子页（卡片）
- **用途**：一级分组名（调试包专属）
- **原文**：

> 开发者工具

- **改写**：开发者工具

### S-020 一级分组说明「开发者工具」（仅调试包）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMainActivity.kt:204`
- **出现在哪**：设置 → 主页 → 「开发者工具」卡片说明（仅调试包）
- **控件类型**：说明文字（单行）
- **用途**：一句话说清分组内容
- **原文**：

> 功能测试、待办清单与调试日志

- **改写**：功能测试、待办清单与调试日志

---

## 二、播放与播放器

### 2.1 选择播放器页（设置 → 播放与播放器）

### S-021 页面标题「选择播放器」

- **位置**：`app/src/main/res/layout/activity_setting_player_choose.xml:26`（`@string/pagename_setting_player_choose` → `app/src/main/res/values/strings.xml:253`）
- **出现在哪**：设置 → 播放与播放器 → 顶栏标题
- **控件类型**：页面标题文字
- **用途**：标明当前页面
- **原文**：

> 选择播放器

- **改写**：选择播放器

### S-022 播放器卡片名「内置播放器」

- **位置**：`app/src/main/res/layout/activity_setting_player_choose.xml:87`
- **出现在哪**：设置 → 播放与播放器 → 第一张播放器卡片标题
- **控件类型**：单选（三张卡片择一，选中描边高亮）
- **用途**：播放器名称（本应用自带播放器）
- **原文**：

> 内置播放器

- **改写**：内置播放器

### S-023 播放器卡片说明「内置播放器」

- **位置**：`app/src/main/res/layout/activity_setting_player_choose.xml:101`
- **出现在哪**：设置 → 播放与播放器 → 「内置播放器」卡片标题下的跑马灯说明
- **控件类型**：说明文字（单行跑马灯，会滚动）
- **用途**：说清特色功能，并提示长按可进二级设置页
- **原文**：

> 支持互动视频、直播弹幕等特色功能 | 长按这里进入设置

- **改写**：支持互动视频、直播弹幕等特色功能 | 长按这里进入设置

### S-024 播放器卡片名「凉腕播放器」

- **位置**：`app/src/main/res/layout/activity_setting_player_choose.xml:142`
- **出现在哪**：设置 → 播放与播放器 → 第二张播放器卡片标题
- **控件类型**：单选（卡片）
- **用途**：第三方播放器名称（外部安装的 App）
- **原文**：

> 凉腕播放器

- **改写**：凉腕播放器

### S-025 播放器卡片说明「凉腕播放器」

- **位置**：`app/src/main/res/layout/activity_setting_player_choose.xml:156`
- **出现在哪**：设置 → 播放与播放器 → 「凉腕播放器」卡片说明
- **控件类型**：说明文字（单行）
- **用途**：一句推荐语
- **原文**：

> 另一个优秀的播放器

- **改写**：另一个优秀的播放器

### S-026 播放器卡片名「小电视播放器」

- **位置**：`app/src/main/res/layout/activity_setting_player_choose.xml:197`
- **出现在哪**：设置 → 播放与播放器 → 第三张播放器卡片标题
- **控件类型**：单选（卡片）
- **用途**：第三方播放器名称
- **原文**：

> 小电视播放器

- **改写**：小电视播放器

### S-027 播放器卡片说明「小电视播放器」

- **位置**：`app/src/main/res/layout/activity_setting_player_choose.xml:211`
- **出现在哪**：设置 → 播放与播放器 → 「小电视播放器」卡片说明
- **控件类型**：说明文字（单行）
- **用途**：提示该播放器已不推荐
- **原文**：

> 不再推荐使用

- **改写**：不再推荐使用

### S-028 行标题「清晰度」

- **位置**：`app/src/main/res/layout/activity_setting_player_choose.xml:230`
- **出现在哪**：设置 → 播放与播放器 → 播放器卡片下方的「清晰度」入口行
- **控件类型**：跳转子页（整行可点，进清晰度选择页）
- **用途**：设置默认播放清晰度
- **原文**：

> 清晰度

- **改写**：清晰度

### S-029 清晰度当前值（默认展示值）

- **位置**：`app/src/main/res/layout/activity_setting_player_choose.xml:242`
- **出现在哪**：设置 → 播放与播放器 → 「清晰度」行右侧的当前值（运行期由 `SettingPlayerChooseActivity.updateQn()` 覆盖为 360P / 720P / 1080P）
- **控件类型**：说明文字（右侧当前值）
- **用途**：展示当前选中的清晰度档位
- **原文**：

> 360P

- **改写**：360P

### S-030 页面底部说明（播放器页）

- **位置**：`app/src/main/res/values/strings.xml:24`（`R.string.desc_setting_player`，布局引用 `activity_setting_player_choose.xml:253`）
- **出现在哪**：设置 → 播放与播放器 → 清晰度行下方整段灰色说明
- **控件类型**：说明文字（多行长段落）
- **用途**：讲清进入方式、播放器获取渠道、内置播放器的清晰度语义
- **原文**：

> *你也可以通过长按“播放”按钮快速进入此页面。若无法跳转播放，请确保你已经安装对应播放器且播放器和哔哩终端都是最新版本。
> *播放器和哔哩终端在洋葱商店、hankmi商店、唯趣均可下载（洋葱上架最快，唯趣上架较慢），适配安卓4的小电视请加群后从群文件中获取。
> *如果使用的是内置播放器，则此处的清晰度为开始播放时的默认清晰度。

- **改写**：

> *你也可以通过长按“播放”按钮快速进入此页面。若无法跳转播放，请确保你已经安装对应播放器且播放器和哔哩终端都是最新版本。
> *播放器和哔哩终端在洋葱商店、hankmi商店、唯趣均可下载（洋葱上架最快，唯趣上架较慢），适配安卓4的小电视请加群后从群文件中获取。
> *如果使用的是内置播放器，则此处的清晰度为开始播放时的默认清晰度。

### S-031 选择小电视播放器时的提示弹窗

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPlayerChooseActivity.kt:125`
- **出现在哪**：设置 → 播放与播放器 → 点选「小电视播放器」时弹出的对话框正文（标题「提醒」）
- **控件类型**：弹窗正文（单按钮确认）
- **用途**：劝退并引导改用内置播放器
- **原文**：

> 不再推荐使用小电视播放器，许多功能已不再支持，推荐使用内置播放器

- **改写**：不再推荐使用小电视播放器，许多功能已不再支持，推荐使用内置播放器

### S-032 安卓版本过低时的提示弹窗

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPlayerChooseActivity.kt:130`
- **出现在哪**：设置 → 播放与播放器 → 点选「凉腕播放器」且系统 API ≤ 19 时弹出的对话框正文
- **控件类型**：弹窗正文（单按钮确认）
- **用途**：告知兼容性风险
- **原文**：

> 您的安卓版本过低，可能无法使用凉腕播放器，可以使用内置播放器

- **改写**：您的安卓版本过低，可能无法使用凉腕播放器，可以使用内置播放器

### 2.2 清晰度选择页（设置 → 播放与播放器 → 清晰度）

### S-033 页面标题「请选择清晰度」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingQualityActivity.kt:36`
- **出现在哪**：设置 → 播放与播放器 → 清晰度 → 顶栏标题
- **控件类型**：页面标题文字
- **用途**：标明当前页面要干什么
- **原文**：

> 请选择清晰度

- **改写**：请选择清晰度

### S-034 清晰度候选值（360P / 720P / 1080P）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingQualityActivity.kt:20-22`（`qnMap` 的键）
- **出现在哪**：设置 → 播放与播放器 → 清晰度 → 列表三行的文字，每行一项
- **控件类型**：单选（列表，点中即保存并退出）
- **用途**：三档清晰度档位名
- **原文**：

> 360P
> 720P
> 1080P

- **改写**：

> 360P
> 720P
> 1080P

### 2.3 内置播放器设置页（设置 → 播放与播放器 → 长按「内置播放器」卡片 / 或从卡片首次选中）

> ⚠️ **26.11.06 起本节的行号已失效**：设置项定义整体搬到
> `app/src/main/java/com/RobinNotBad/BiliClient/player/PlayerSettingsGroups.kt`（按七个组分组），
> `SettingTerminalPlayerActivity.kt` 只剩「拍平交给列表」的十几行。
> 改这一页的文案请按 `SettingSection` 的 name / `R.string` 名去新文件里搜，别照本节行号定位。
> 组标题本身在 `strings.xml` 的 `setting_player_group_*`（手势与滑动 / 播放默认值 / 画面与显示 /
> 渲染与解码 / 弹幕 / 字幕 / 界面按钮）。

### S-035 页面标题「内置播放器设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:50`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 顶栏标题
- **控件类型**：页面标题文字
- **用途**：标明当前页面
- **原文**：

> 内置播放器设置

- **改写**：内置播放器设置

### S-036 条目名「长按倍速」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:62`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 第一项
- **控件类型**：开关
- **用途**：是否允许长按屏幕倍速播放
- **原文**：

> 长按倍速

- **改写**：长按倍速

### S-037 条目名「双击快进快退」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:63`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 第二项
- **控件类型**：开关
- **用途**：是否开启双击左右区域快进/快退
- **原文**：

> 双击快进快退

- **改写**：双击快进快退

### S-038 条目名「左右滑动控制进度」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:64`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：是否允许横向滑动调进度
- **原文**：

> 左右滑动控制进度

- **改写**：左右滑动控制进度

### S-039 条目说明「左右滑动控制进度」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:64`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「左右滑动控制进度」开关下方灰色说明
- **控件类型**：说明文字
- **用途**：讲清触发条件
- **原文**：

> 在视频区域左右滑动快进/快退（未缩放视频时生效）

- **改写**：在视频区域左右滑动快进/快退（未缩放视频时生效）

### S-040 条目名「有缩放时，双击优先恢复缩放」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:65`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：双击语义的优先级设置
- **原文**：

> 有缩放时，双击优先恢复缩放

- **改写**：有缩放时，双击优先恢复缩放

### S-041 条目说明「有缩放时，双击优先恢复缩放」

- **位置**：`app/src/main/res/values/strings.xml:150`（`R.string.desc_player_doubletap_restore_scale`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 该开关下方灰色说明
- **控件类型**：说明文字（多行）
- **用途**：解释缩放与双击、双击快进的关系
- **原文**：

> 开启后，画面有缩放时双击优先恢复原大小和居中位置；未缩放时双击播放/暂停。只切换横竖屏不算缩放，恢复缩放也不会改变当前方向。开启双击快进快退时，左右两侧仍用于快退/快进。

- **改写**：开启后，画面有缩放时双击优先恢复原大小和居中位置；未缩放时双击播放/暂停。只切换横竖屏不算缩放，恢复缩放也不会改变当前方向。开启双击快进快退时，左右两侧仍用于快退/快进。

### S-042 条目名「快进快退秒数」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:67`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：输入框（整数，默认 10）
- **用途**：设置单击快进/快退一次跳多少秒
- **原文**：

> 快进快退秒数

- **改写**：快进快退秒数

### S-043 条目名「后台/熄屏继续播放」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:68`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：是否允许退后台/熄屏后继续出声
- **原文**：

> 后台/熄屏继续播放

- **改写**：后台/熄屏继续播放

### S-044 条目说明「后台/熄屏继续播放」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:69`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：补充通知栏遥控的行为
- **原文**：

> 退到后台或熄屏时继续播放，并挂出通知栏遥控

- **改写**：退到后台或熄屏时继续播放，并挂出通知栏遥控

### S-045 小节标题「播放默认值」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:72`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 中段分组标题
- **控件类型**：说明文字（小节标题）
- **用途**：把"开播自动应用"的一组设置圈在一起
- **原文**：

> 播放默认值

- **改写**：播放默认值

### S-046 条目名「弹幕」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:74`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 播放默认值 → 第一项
- **控件类型**：跳转子页（单选列表：开 / 关 / 沿用上次）
- **用途**：开播时弹幕的默认开关
- **原文**：

> 弹幕

- **改写**：弹幕

### S-047 条目说明「弹幕」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:75`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「弹幕」行下方灰色说明
- **控件类型**：说明文字
- **用途**：解释「沿用上次」的语义
- **原文**：

> 开播时的弹幕开关；「沿用上次」= 上次退出播放器时的实际状态

- **改写**：开播时的弹幕开关；「沿用上次」= 上次退出播放器时的实际状态

### S-048 条目名「听视频模式」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:77`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 播放默认值
- **控件类型**：跳转子页（单选列表：开 / 关 / 沿用上次）
- **用途**：开播时是否默认只播音频
- **原文**：

> 听视频模式

- **改写**：听视频模式

### S-049 条目说明「听视频模式」

- **位置**：`app/src/main/res/values/strings.xml:151`（`R.string.desc_audio_only`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「听视频模式」行下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：解释听视频模式的技术含义
- **原文**：

> 开启后，进入播放器时默认使用听视频模式。
> 禁用视频解码，只播放音频

- **改写**：

> 开启后，进入播放器时默认使用听视频模式。
> 禁用视频解码，只播放音频

### S-050 条目名「循环播放」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:80`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 播放默认值
- **控件类型**：跳转子页（单选列表：开 / 关 / 沿用上次）
- **用途**：开播时是否循环
- **原文**：

> 循环播放

- **改写**：循环播放

### S-051 条目说明「循环播放」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:81`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「循环播放」行下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话讲清循环的范围
- **原文**：

> 单集播放结束后循环重播当前视频

- **改写**：单集播放结束后循环重播当前视频

### S-052 条目名「自动连播」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:83`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 播放默认值
- **控件类型**：跳转子页（单选列表：开 / 关 / 沿用上次）
- **用途**：多分P是否自动播下一集
- **原文**：

> 自动连播

- **改写**：自动连播

### S-053 条目说明「自动连播」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:84`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「自动连播」行下方灰色说明
- **控件类型**：说明文字
- **用途**：说明只对多分P视频生效
- **原文**：

> 多分P视频播完当前分P后自动播下一个

- **改写**：多分P视频播完当前分P后自动播下一个

### S-054 条目名「倍速」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:86`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 播放默认值
- **控件类型**：跳转子页（单选列表，9 档）
- **用途**：开播默认倍速
- **原文**：

> 倍速

- **改写**：倍速

### S-055 条目说明「倍速」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:87`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「倍速」行下方灰色说明
- **控件类型**：说明文字
- **用途**：解释「沿用上次」
- **原文**：

> 开播时的播放速度；「沿用上次」= 上次用过的倍速

- **改写**：开播时的播放速度；「沿用上次」= 上次用过的倍速

### S-056 候选值「倍速」的九档名称

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:89`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 倍速 → 弹出选择列表的九行
- **控件类型**：单选（列表项文字）
- **用途**：倍速档位显示名
- **原文**：

> 0.5x
> 0.75x
> 1.0x
> 1.25x
> 1.5x
> 1.75x
> 2.0x
> 3.0x
> 沿用上次

- **改写**：

> 0.5x
> 0.75x
> 1.0x
> 1.25x
> 1.5x
> 1.75x
> 2.0x
> 3.0x
> 沿用上次

### S-057 条目名「字幕」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:91`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 播放默认值
- **控件类型**：跳转子页（单选列表：中文 / 自行选择）
- **用途**：开播时的字幕默认行为
- **原文**：

> 字幕

- **改写**：字幕

### S-058 条目说明「字幕」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:92`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「字幕」行下方灰色说明
- **控件类型**：说明文字（较长，含两条语义）
- **用途**：解释「中文」与「自行选择」的区别
- **原文**：

> 中文 = 开播自动选中文字幕（优先人工，其次 AI）；自行选择 = 是否弹出选择框由下方「自动弹出字幕选择」决定

- **改写**：中文 = 开播自动选中文字幕（优先人工，其次 AI）；自行选择 = 是否弹出选择框由下方「自动弹出字幕选择」决定

### S-059 候选值「字幕」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:94`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 字幕 → 弹出选择列表
- **控件类型**：单选（列表项文字）
- **用途**：字幕默认策略的两个选项名
- **原文**：

> 中文
> 自行选择

- **改写**：

> 中文
> 自行选择

### S-060 条目名「屏幕方向」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:96`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 播放默认值
- **控件类型**：跳转子页（单选列表，3 项）
- **用途**：开播时默认屏幕方向
- **原文**：

> 屏幕方向

- **改写**：屏幕方向

### S-061 条目说明「屏幕方向」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:97`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「屏幕方向」行下方灰色说明
- **控件类型**：说明文字
- **用途**：解释"按视频分辨率选择"的判定规则
- **原文**：

> 播放器的默认屏幕方向；「按视频分辨率选择」= 视频宽大于高则横屏

- **改写**：播放器的默认屏幕方向；「按视频分辨率选择」= 视频宽大于高则横屏

### S-062 候选值「屏幕方向」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:99`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 屏幕方向 → 弹出选择列表
- **控件类型**：单选（列表项文字）
- **用途**：三个方向选项名
- **原文**：

> 默认横屏
> 竖屏
> 按视频分辨率选择

- **改写**：

> 默认横屏
> 竖屏
> 按视频分辨率选择

### S-063 条目名「从历史位置播放」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:106`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置（播放默认值小节之后）
- **控件类型**：开关
- **用途**：是否续播上次进度
- **原文**：

> 从历史位置播放

- **改写**：从历史位置播放

### S-064 条目说明「从历史位置播放」

- **位置**：`app/src/main/res/values/strings.xml:147`（`R.string.desc_fromlast`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「从历史位置播放」下方灰色说明
- **控件类型**：说明文字
- **用途**：说明 5 秒阈值
- **原文**：

> 播放视频时如果有超过5秒的播放历史，将会自动从历史位置播放

- **改写**：播放视频时如果有超过5秒的播放历史，将会自动从历史位置播放

### S-065 条目名「显示实时人数」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:109`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：是否显示在线观看人数
- **原文**：

> 显示实时人数

- **改写**：显示实时人数

### S-066 条目说明「显示实时人数」

- **位置**：`app/src/main/res/values/strings.xml:148`（`R.string.desc_showonline`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「显示实时人数」下方灰色说明
- **控件类型**：说明文字（多行）
- **用途**：解释视频/直播两种口径，并给出崩溃时的排查建议
- **原文**：

> 播放器右下角会显示当前视频的实时观看人数，在直播中则为观看过的人数和进入直播间提醒
> 如果观看视频中途崩溃可以尝试关闭此开关
> 该功能为内置播放器专属

- **改写**：

> 播放器右下角会显示当前视频的实时观看人数，在直播中则为观看过的人数和进入直播间提醒
> 如果观看视频中途崩溃可以尝试关闭此开关
> 该功能为内置播放器专属

### S-067 条目名「视频可缩放」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:112`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：是否允许手势缩放画面
- **原文**：

> 视频可缩放

- **改写**：视频可缩放

### S-068 条目说明「视频可缩放」

- **位置**：`app/src/main/res/values/strings.xml:149`（`R.string.desc_scale`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「视频可缩放」下方灰色说明
- **控件类型**：说明文字（多行长段落）
- **用途**：教用户怎么缩放、单点触控怎么操作、缩放失灵怎么办
- **原文**：

> 播放视频时可使用像查看照片一样的手势来缩放和移动视频；开启“有缩放时，双击优先恢复缩放”后，双击可恢复原大小且保持当前屏幕方向
> 对于单点触控的设备，可以双击屏幕并在第二次按下时不抬手，然后上下拖动来缩放
> ！若无法缩放，请切换下面的“显示方式”！
> 特别感谢：凉腕播放器

- **改写**：

> 播放视频时可使用像查看照片一样的手势来缩放和移动视频；开启“有缩放时，双击优先恢复缩放”后，双击可恢复原大小且保持当前屏幕方向
> 对于单点触控的设备，可以双击屏幕并在第二次按下时不抬手，然后上下拖动来缩放
> ！若无法缩放，请切换下面的“显示方式”！
> 特别感谢：凉腕播放器

### S-069 条目名「缩放时可移动」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:114`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：放大后是否可拖动画布
- **原文**：

> 缩放时可移动

- **改写**：缩放时可移动

### S-070 条目说明「缩放时可移动」

- **位置**：`app/src/main/res/values/strings.xml:152`（`R.string.desc_doublemove`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「缩放时可移动」下方灰色说明
- **控件类型**：说明文字
- **用途**：提示设备适配差异
- **原文**：

> 大屏幕设备上会更好用，手表上可能会造成操作不便

- **改写**：大屏幕设备上会更好用，手表上可能会造成操作不便

### S-071 条目名「显示方式」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:120`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置（分割线下第一项）
- **控件类型**：单选（两项：TextureView / SurfaceView）
- **用途**：选择视频渲染视图类型
- **原文**：

> 显示方式

- **改写**：显示方式

### S-072 条目说明「显示方式」

- **位置**：`app/src/main/res/values/strings.xml:153`（`R.string.desc_display`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「显示方式」下方灰色说明
- **控件类型**：说明文字（两行，含已知机型结论）
- **用途**：播放异常时的第一处置手段
- **原文**：

> ！如果视频播放异常/闪退/未响应/无法缩放，请尝试切换该选项！
> 已知：KidoX3只能Surface，小天才只能Texture，部分设备需要使用Texture才能进行缩放

- **改写**：

> ！如果视频播放异常/闪退/未响应/无法缩放，请尝试切换该选项！
> 已知：KidoX3只能Surface，小天才只能Texture，部分设备需要使用Texture才能进行缩放

### S-073 候选值「显示方式」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:123`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「显示方式」右侧两个单选按钮
- **控件类型**：单选（RadioButton 文字）
- **用途**：渲染视图类型名（英文技术名，改写时注意技术准确性）
- **原文**：

> TextureView
> SurfaceView

- **改写**：

> TextureView
> SurfaceView

### S-074 条目名「解码方式」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:124`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：单选（两项：硬件解码 / 软件解码）
- **用途**：选择视频解码路径
- **原文**：

> 解码方式

- **改写**：解码方式

### S-075 条目说明「解码方式」

- **位置**：`app/src/main/res/values/strings.xml:154`（`R.string.desc_videocodec`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「解码方式」下方灰色说明
- **控件类型**：说明文字（两行，带一句玩笑）
- **用途**：讲清硬解/软解的取舍
- **原文**：

> 硬件解码：使用GPU解码，速度快，性能消耗小
> 软件解码：使用CPU硬算，兼容性好，暖手必备

- **改写**：

> 硬件解码：使用GPU解码，速度快，性能消耗小
> 软件解码：使用CPU硬算，兼容性好，暖手必备

### S-076 候选值「解码方式」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:126`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「解码方式」右侧两个单选按钮
- **控件类型**：单选（RadioButton 文字）
- **用途**：解码方式名
- **原文**：

> 硬件解码
> 软件解码

- **改写**：

> 硬件解码
> 软件解码

### S-077 条目名「音频输出」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:127`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：单选（两项：OpenSles / AudioTrack）
- **用途**：选择音频输出通道
- **原文**：

> 音频输出

- **改写**：音频输出

### S-078 条目说明「音频输出」

- **位置**：`app/src/main/res/values/strings.xml:155`（`R.string.desc_audiocodec`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「音频输出」下方灰色说明
- **控件类型**：说明文字
- **用途**：给出声音异常时的处置建议
- **原文**：

> 通常使用AudioTrack即可，声音延迟或无法播放请尝试切换

- **改写**：通常使用AudioTrack即可，声音延迟或无法播放请尝试切换

### S-079 候选值「音频输出」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:129`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「音频输出」右侧两个单选按钮
- **控件类型**：单选（RadioButton 文字；注意原文写的是 `OpenSles`，疑似 `OpenSLES` 笔误，**改写前先确认**）
- **用途**：音频输出通道名
- **原文**：

> OpenSles
> AudioTrack

- **改写**：

> OpenSles
> AudioTrack

### S-080 条目名「显示高能进度条」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:133`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：是否在进度条下画弹幕密度曲线
- **原文**：

> 显示高能进度条

- **改写**：显示高能进度条

### S-081 条目说明「显示高能进度条」

- **位置**：`app/src/main/res/values/strings.xml:159`（`R.string.desc_player_high_energy`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「显示高能进度条」下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话解释高能进度条
- **原文**：

> 在进度条下方用折线图展示弹幕密度分布

- **改写**：在进度条下方用折线图展示弹幕密度分布

### S-082 「自动跳过片头片尾」说明

- **位置**：`app/src/main/res/values/strings.xml:165`（`R.string.desc_player_skip_op_ed`；设置项在 `SettingTerminalPlayerActivity.kt:136-137`）
- **出现在哪**：设置 → 播放器 → 「显示高能进度条」下面；`SettingsIndex` 的搜索词也在（搜索「自动跳过片头片尾」可定位）。**26.10.10 撤回时曾隐藏，26.10.22 恢复上线（默认关闭）**
- **控件类型**：说明文字（开关本身默认关）
- **用途**：解释自动跳片头片尾与「撤回」按钮
- **原文**：

> 播放番剧/影视时，进度走到片头或片尾会自动跳过去，并在屏幕下方留一个「撤回」按钮，点一下就能回到被跳过的地方。
> 跳过只对 B 站标注了片头片尾的视频生效。

- **改写**：

> 播放番剧/影视时，进度走到片头或片尾会自动跳过去，并在屏幕下方留一个「撤回」按钮，点一下就能回到被跳过的地方。
> 跳过只对 B 站标注了片头片尾的视频生效。

### S-083 条目名「弹幕允许重叠」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:141`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：是否允许弹幕互相重叠
- **原文**：

> 弹幕允许重叠

- **改写**：弹幕允许重叠

### S-084 条目名「合并重复弹幕」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:142`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：相同内容弹幕是否去重
- **原文**：

> 合并重复弹幕

- **改写**：合并重复弹幕

### S-085 条目名「强制为滚动弹幕」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:143`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：把顶/底弹幕也改成滚动显示
- **原文**：

> 强制为滚动弹幕

- **改写**：强制为滚动弹幕

### S-086 条目说明「强制为滚动弹幕」

- **位置**：`app/src/main/res/values/strings.xml:157`（`R.string.desc_danmaku_force_r2l`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：说明目的（避免挡画面）与例外（高级弹幕）
- **原文**：

> 除了高级弹幕以外的弹幕都会被强制以滚动弹幕的形式展示，从而避免顶部弹幕和底部弹幕遮挡画面

- **改写**：除了高级弹幕以外的弹幕都会被强制以滚动弹幕的形式展示，从而避免顶部弹幕和底部弹幕遮挡画面

### S-087 条目名「显示直播弹幕发送者」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:145`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：直播聊天是否显示昵称
- **原文**：

> 显示直播弹幕发送者

- **改写**：显示直播弹幕发送者

### S-088 条目说明「显示直播弹幕发送者」

- **位置**：`app/src/main/res/values/strings.xml:158`（`R.string.desc_danmaku_showsender`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话解释
- **原文**：

> 显示直播聊天弹幕的发送者名称

- **改写**：显示直播聊天弹幕的发送者名称

### S-089 条目名「弹幕最大行数」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:147`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：输入框（整数，默认 10；无说明文字）
- **用途**：一屏最多显示几行弹幕
- **原文**：

> 弹幕最大行数

- **改写**：弹幕最大行数

### S-090 条目名「弹幕字号大小」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:148`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：输入框（小数，默认 0.7；无说明文字）
- **用途**：弹幕文字缩放倍率
- **原文**：

> 弹幕字号大小

- **改写**：弹幕字号大小

### S-091 条目名「弹幕不透明度」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:149`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：输入框（小数，默认 0.5；无说明文字）
- **用途**：弹幕透明度
- **原文**：

> 弹幕不透明度

- **改写**：弹幕不透明度

### S-092 条目名「弹幕速度」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:150`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：输入框（小数，默认 1.0；无说明文字）
- **用途**：弹幕滚动速度倍率
- **原文**：

> 弹幕速度

- **改写**：弹幕速度

### S-093 条目名「自动弹出字幕选择」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:154`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：有字幕的视频开播时是否弹选择框
- **原文**：

> 自动弹出字幕选择

- **改写**：自动弹出字幕选择

### S-094 条目说明「自动弹出字幕选择」

- **位置**：`app/src/main/res/values/strings.xml:161`（`R.string.desc_subtitle_autoshow`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：说明触发条件
- **原文**：

> 开启后，播放存在字幕的视频时，将会自动弹出字幕选择对话框。

- **改写**：开启后，播放存在字幕的视频时，将会自动弹出字幕选择对话框。

### S-095 条目名「允许仅AI字幕」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:156`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：唯一字幕是中文 AI 字幕时是否仍弹选择
- **原文**：

> 允许仅AI字幕

- **改写**：允许仅AI字幕

### S-096 条目说明「允许仅AI字幕」

- **位置**：`app/src/main/res/values/strings.xml:162`（`R.string.desc_subtitle_ai_allowed`）
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 该开关下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：说明行为并给出"可能很烦"的提醒
- **原文**：

> 打开此选项，如果视频只有一个中文AI字幕，播放时仍然会让你选择。
> 可能比较烦人，谨慎开启。

- **改写**：

> 打开此选项，如果视频只有一个中文AI字幕，播放时仍然会让你选择。
> 可能比较烦人，谨慎开启。

### S-097 条目名「字幕校准」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:161`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：输入框（小数，默认 0.3）
- **用途**：字幕时间轴偏移量
- **原文**：

> 字幕校准

- **改写**：字幕校准

### S-098 条目说明「字幕校准」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:162`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「字幕校准」下方灰色说明
- **控件类型**：说明文字
- **用途**：说明正值/负值的效果（原文未说方向，**改写时可补**）
- **原文**：

> 将字幕提前/退后一段时间，从而与视频对齐

- **改写**：将字幕提前/退后一段时间，从而与视频对齐

### S-099 条目名「显示旋转按钮」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:166`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置（最后一段"界面按钮"）
- **控件类型**：开关
- **用途**：播放器界面是否显示旋转按钮
- **原文**：

> 显示旋转按钮

- **改写**：显示旋转按钮

### S-100 条目名「显示弹幕按钮」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:167`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：是否显示弹幕开关按钮
- **原文**：

> 显示弹幕按钮

- **改写**：显示弹幕按钮

### S-101 条目名「显示清晰度按钮」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:168`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：是否显示清晰度切换按钮
- **原文**：

> 显示清晰度按钮

- **改写**：显示清晰度按钮

### S-102 条目名「显示分P按钮」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:169`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置
- **控件类型**：开关
- **用途**：是否显示分P切换按钮
- **原文**：

> 显示分P按钮

- **改写**：显示分P按钮

### S-103 条目名「互动选项字体大小」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:170`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置（页尾）
- **控件类型**：输入框（小数）
- **用途**：互动视频选项按钮的字号
- **原文**：

> 互动选项字体大小

- **改写**：互动选项字体大小

### S-104 三态候选值（开 / 关 / 沿用上次）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.kt:23`
- **出现在哪**：设置 → 播放与播放器 → 内置播放器设置 → 「弹幕」「听视频模式」「循环播放」「自动连播」四个条目的选择列表
- **控件类型**：单选（列表项文字）
- **用途**：三态开关的统一候选值
- **原文**：

> 开
> 关
> 沿用上次

- **改写**：

> 开
> 关
> 沿用上次

---

## 三、账号与登录（设置 → 账号与登录）

> 本页是「设置 → 账号与登录」分组页（`SettingGroupActivity.buildAccountGroup()`）。登录页、特殊登录页、账号切换页本身属于登录流程，另见对应文档；这里收的是**进入它们的入口卡片文案**与凭证自检弹窗。

### S-105 入口名「登录」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:194`
- **出现在哪**：设置 → 账号与登录 → 第一张卡片（**仅在未登录时出现**）
- **控件类型**：跳转子页（进登录页）
- **用途**：未登录时的登录入口
- **原文**：

> 登录

- **改写**：登录

### S-106 入口说明「登录」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:194`
- **出现在哪**：设置 → 账号与登录 → 「登录」卡片说明
- **控件类型**：说明文字
- **用途**：说明这句卡片点进去做什么
- **原文**：

> 使用哔哩哔哩账号登录

- **改写**：使用哔哩哔哩账号登录

### S-107 入口名「账号切换」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:205`
- **出现在哪**：设置 → 账号与登录 → 第二张卡片
- **控件类型**：跳转子页（进账号切换页）
- **用途**：多账号管理入口
- **原文**：

> 账号切换

- **改写**：账号切换

### S-108 入口说明「账号切换」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:205`
- **出现在哪**：设置 → 账号与登录 → 「账号切换」卡片说明
- **控件类型**：说明文字
- **用途**：说明功能
- **原文**：

> 管理已保存的账号，快速切换登录

- **改写**：管理已保存的账号，快速切换登录

### S-109 入口名「查看登录信息」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:209`
- **出现在哪**：设置 → 账号与登录 → 第三张卡片（**仅在已登录时出现**）
- **控件类型**：跳转子页（进特殊登录页的导出模式）
- **用途**：把本机登录态导出到另一台设备
- **原文**：

> 查看登录信息

- **改写**：查看登录信息

### S-110 入口说明「查看登录信息」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:209`
- **出现在哪**：设置 → 账号与登录 → 「查看登录信息」卡片说明（整句较长，单行跑马灯）
- **控件类型**：说明文字（单行，会滚动）
- **用途**：讲清用途是"跨设备特殊登录"
- **原文**：

> 查看当前账号的登录信息，用于在另外的哔哩终端中特殊登录

- **改写**：查看当前账号的登录信息，用于在另外的哔哩终端中特殊登录

### S-111 入口名「登录凭证状态」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:216`
- **出现在哪**：设置 → 账号与登录 → 第四张卡片
- **控件类型**：按钮（点击弹出纯文本对话框）
- **用途**：排查登录态用的自检入口
- **原文**：

> 登录凭证状态

- **改写**：登录凭证状态

### S-112 入口说明「登录凭证状态」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:216`
- **出现在哪**：设置 → 账号与登录 → 「登录凭证状态」卡片说明
- **控件类型**：说明文字
- **用途**：说明弹窗里能看到什么
- **原文**：

> 查看 Cookie、Token 等登录凭证状态

- **改写**：查看 Cookie、Token 等登录凭证状态

### S-113 凭证弹窗标题「登录凭证状态」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:236`
- **出现在哪**：设置 → 账号与登录 → 登录凭证状态 → 弹出的文本对话框标题
- **控件类型**：弹窗标题
- **用途**：标明弹窗内容
- **原文**：

> 登录凭证状态

- **改写**：登录凭证状态

### S-114 凭证弹窗正文（五行状态）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:231-235`
- **出现在哪**：设置 → 账号与登录 → 登录凭证状态 → 对话框正文（`$mid` 与 `take(8)` 为运行时插入值）
- **控件类型**：说明文字（等宽纯文本，五行 + 未获取分支）
- **用途**：逐项自检 Cookie / Token / UID 是否就绪
- **原文**：

> Cookie：已获取（含SESSDATA）／Cookie：未获取
> Access Token：已获取（XXXXXXXX...）／Access Token：未获取（请使用TV端扫码登录）
> UID：12345678／UID：未登录
> CSRF Token：已获取／CSRF Token：未获取
> Refresh Token：已获取（XXXXXXXX...）／Refresh Token：未获取

- **改写**：

> Cookie：已获取（含SESSDATA）／Cookie：未获取
> Access Token：已获取（XXXXXXXX...）／Access Token：未获取（请使用TV端扫码登录）
> UID：12345678／UID：未登录
> CSRF Token：已获取／CSRF Token：未获取
> Refresh Token：已获取（XXXXXXXX...）／Refresh Token：未获取

---

## 四、界面与外观（设置 → 界面与外观）

### 4.1 分组页主列表

### S-115 按钮「恢复默认」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:240`
- **出现在哪**：设置 → 界面与外观 → 页面最上方的按钮
- **控件类型**：按钮（点击即重置缩放/边距/圆屏适配）
- **用途**：一键把界面参数恢复出厂
- **原文**：

> 恢复默认

- **改写**：恢复默认

### S-116 按钮「查看预览」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:249`
- **出现在哪**：设置 → 界面与外观 → 第二个按钮
- **控件类型**：按钮（跳转界面预览页）
- **用途**：调完参数后先看效果
- **原文**：

> 查看预览

- **改写**：查看预览

### S-117 小节标题「布局适配」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:253`
- **出现在哪**：设置 → 界面与外观 → 分组标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住四个布局相关设置
- **原文**：

> 布局适配

- **改写**：布局适配

### S-118 条目名「圆屏适配」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:254`
- **出现在哪**：设置 → 界面与外观 → 布局适配 → 第一项
- **控件类型**：开关（打开时顺带把横向/纵向边距改成 5 / 3）
- **用途**：圆表盘的一键布局适配
- **原文**：

> 圆屏适配

- **改写**：圆屏适配

### S-119 条目说明「圆屏适配」

- **位置**：`app/src/main/res/values/strings.xml:23`（`R.string.desc_setting_ui_round`）
- **出现在哪**：设置 → 界面与外观 → 「圆屏适配」开关下方灰色说明；**首次启动「调整界面」页也用它**（见 S-357）
- **控件类型**：说明文字（两行）
- **用途**：说清效果与局限（部分页面未适配）
- **原文**：

> 开启选项后，页面将会修改为圆屏布局。
> 由于纯靠代码实现，有部分页面可能未适配，但这是目前最好且工作量最小的办法了。

- **改写**：

> 开启选项后，页面将会修改为圆屏布局。
> 由于纯靠代码实现，有部分页面可能未适配，但这是目前最好且工作量最小的办法了。

### S-120 条目名「界面大小」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:260`
- **出现在哪**：设置 → 界面与外观 → 布局适配
- **控件类型**：输入框（小数，默认 1.0；安全范围 0.25~5.00）
- **用途**：整体界面缩放倍率
- **原文**：

> 界面大小

- **改写**：界面大小

### S-121 条目说明「界面大小」

- **位置**：`app/src/main/res/values/strings.xml:21`（`R.string.desc_setting_ui`）
- **出现在哪**：设置 → 界面与外观 → 「界面大小」下方灰色说明
- **控件类型**：说明文字（长段落，两行 + 警告）
- **用途**：给出安全范围与"别乱调"的警告
- **原文**：

> 有效范围为0.25~5.00（倍），建议每次仅进行轻微调节，调节后请预览页面，确认显示正常后完成修改，打开新页面后将会生效。
> 注意：尽量不要调到两个最值处，否则你可能会后悔！若乱调此选项，导致应用无法正常使用的，请清除应用数据或卸载重装！

- **改写**：

> 有效范围为0.25~5.00（倍），建议每次仅进行轻微调节，调节后请预览页面，确认显示正常后完成修改，打开新页面后将会生效。
> 注意：尽量不要调到两个最值处，否则你可能会后悔！若乱调此选项，导致应用无法正常使用的，请清除应用数据或卸载重装！

### S-122 条目名「界面边距（横向）」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:270`
- **出现在哪**：设置 → 界面与外观 → 布局适配
- **控件类型**：输入框（整数百分比，超过 30 不保存）
- **用途**：横向留白，配合圆屏
- **原文**：

> 界面边距（横向）

- **改写**：界面边距（横向）

### S-123 条目说明「界面边距（横向/纵向）」

- **位置**：`app/src/main/res/values/strings.xml:686`（`R.string.desc_setting_ui_padding`）
- **出现在哪**：设置 → 界面与外观 → 「界面边距（横向）」与「界面边距（纵向）」两条共用的灰色说明
- **控件类型**：说明文字（两行）
- **用途**：解释单位与保护阈值
- **原文**：

> 单位百分比，此选项可以用于圆屏适配。
> 出于保护，数值超过30不会保存。

- **改写**：

> 单位百分比，此选项可以用于圆屏适配。
> 出于保护，数值超过30不会保存。

### S-124 条目名「界面边距（纵向）」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:277`
- **出现在哪**：设置 → 界面与外观 → 布局适配
- **控件类型**：输入框（整数百分比，超过 30 不保存）
- **用途**：纵向留白
- **原文**：

> 界面边距（纵向）

- **改写**：界面边距（纵向）

### S-125 条目名「设置Density」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:284`
- **出现在哪**：设置 → 界面与外观 → 布局适配
- **控件类型**：输入框（整数，最低 72，默认 -1 表示不覆盖）
- **用途**：手动指定屏幕密度
- **原文**：

> 设置Density

- **改写**：设置Density

### S-126 条目说明「设置Density」

- **位置**：`app/src/main/res/values/strings.xml:22`（`R.string.desc_setting_ui_density`）
- **出现在哪**：设置 → 界面与外观 → 「设置Density」下方灰色说明
- **控件类型**：说明文字（三行，含"没必要别改"）
- **用途**：给出下限与风险提示
- **原文**：

> 最低72，建议每次仅进行轻微调节，调节后请预览页面，确认显示正常后完成修改，打开新页面后将会生效。
> 若乱调此选项，导致应用无法正常使用的，请清除应用数据或卸载重装！
> 注：此选项可能有其他问题，没有必要请不要修改。

- **改写**：

> 最低72，建议每次仅进行轻微调节，调节后请预览页面，确认显示正常后完成修改，打开新页面后将会生效。
> 若乱调此选项，导致应用无法正常使用的，请清除应用数据或卸载重装！
> 注：此选项可能有其他问题，没有必要请不要修改。

### S-127 入口名「外观设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:293`
- **出现在哪**：设置 → 界面与外观 → 布局适配之后的跳转卡片
- **控件类型**：跳转子页（本页的另一个 group_type 分组，对用户是独立一屏）
- **用途**：主题配色/圆角/字体的入口
- **原文**：

> 外观设置

- **改写**：外观设置

### S-128 入口说明「外观设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:293`
- **出现在哪**：设置 → 界面与外观 → 「外观设置」卡片说明
- **控件类型**：说明文字
- **用途**：一句话说清里面有什么
- **原文**：

> 主题配色、卡片圆角、自定义字体

- **改写**：主题配色、卡片圆角、自定义字体

### S-129 条目名「横屏模式」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:299`
- **出现在哪**：设置 → 界面与外观 → 布局适配之后
- **控件类型**：开关
- **用途**：强制横屏布局
- **原文**：

> 横屏模式

- **改写**：横屏模式

### S-130 条目说明「横屏模式」

- **位置**：`app/src/main/res/values/strings.xml:35`（`R.string.setting_lab_ui_landscape`）
- **出现在哪**：设置 → 界面与外观 → 「横屏模式」下方灰色说明
- **控件类型**：说明文字（三行，含"不再改进"声明）
- **用途**：说清适用范围与不适配的页面
- **原文**：

> ！此功能将不再继续进行改进，除非开发者有时间！
> 开启选项后，软件将会变为横屏，部分列表会变为三列，更适合平板等默认横屏的设备
> *注意：部分页面仍为单列式，可能有一些页面适配不佳，电视请移步官方电视版本或使用其他第三方软件。

- **改写**：

> ！此功能将不再继续进行改进，除非开发者有时间！
> 开启选项后，软件将会变为横屏，部分列表会变为三列，更适合平板等默认横屏的设备
> *注意：部分页面仍为单列式，可能有一些页面适配不佳，电视请移步官方电视版本或使用其他第三方软件。

### S-131 条目名「开屏文字」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:300`
- **出现在哪**：设置 → 界面与外观
- **控件类型**：输入框（多行文本，默认「欢迎使用\nRE:哔哩终端」）
- **用途**：自定义启动页文案
- **原文**：

> 开屏文字

- **改写**：开屏文字

### S-132 条目说明「开屏文字」

- **位置**：`app/src/main/res/values/strings.xml:41`（`R.string.setting_lab_splashtext`）
- **出现在哪**：设置 → 界面与外观 → 「开屏文字」下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：说明支持换行、何时被打断
- **原文**：

> 顾名思义，可以更改开屏文字，支持换行。开屏文字在网络请求完成后就会被打断。

- **改写**：顾名思义，可以更改开屏文字，支持换行。开屏文字在网络请求完成后就会被打断。

### S-133 条目名「文字跑马灯」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:301`
- **出现在哪**：设置 → 界面与外观
- **控件类型**：开关
- **用途**：全局是否启用跑马灯
- **原文**：

> 文字跑马灯

- **改写**：文字跑马灯

### S-134 条目说明「文字跑马灯」

- **位置**：`app/src/main/res/values/strings.xml:39`（`R.string.setting_lab_marquee`）
- **出现在哪**：设置 → 界面与外观 → 「文字跑马灯」下方灰色说明
- **控件类型**：说明文字
- **用途**：说明关闭的收益与代价
- **原文**：

> 关闭此开关后，将会全局禁用文字跑马灯，可能会提升性能但会导致部分文字显示不全。

- **改写**：关闭此开关后，将会全局禁用文字跑马灯，可能会提升性能但会导致部分文字显示不全。

### S-135 条目名「加载渐入渐出动画」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:302`
- **出现在哪**：设置 → 界面与外观 → 页尾
- **控件类型**：开关
- **用途**：图片与页面加载的过渡动画开关
- **原文**：

> 加载渐入渐出动画

- **改写**：加载渐入渐出动画

### S-136 条目说明「加载渐入渐出动画」

- **位置**：`app/src/main/res/values/strings.xml:146`（`R.string.desc_load_transition`）
- **出现在哪**：设置 → 界面与外观 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明效果
- **原文**：

> 打开此选项，图片和部分页面加载完成时会有渐入/渐出动画。

- **改写**：打开此选项，图片和部分页面加载完成时会有渐入/渐出动画。

### S-137 恢复默认后的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:247`
- **出现在哪**：设置 → 界面与外观 → 点「恢复默认」后弹出的 Toast
- **控件类型**：Toast
- **用途**：确认已重置
- **原文**：

> 恢复完成

- **改写**：恢复完成

### S-138 开圆屏适配后的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:258`
- **出现在哪**：设置 → 界面与外观 → 打开「圆屏适配」开关后弹出的 Toast
- **控件类型**：Toast（两行）
- **用途**：提示边距已改、可再微调
- **原文**：

> 界面边距已更改
> 可以手动微调喵

- **改写**：

> 界面边距已更改
> 可以手动微调喵

### 4.2 外观设置页（设置 → 界面与外观 → 外观设置）

### S-139 页面标题「外观设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:296`（作为 `group_title` 传参，由 `setPageName` 显示）
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 顶栏标题
- **控件类型**：页面标题文字
- **用途**：标明当前页面
- **原文**：

> 外观设置

- **改写**：外观设置

### S-140 小节标题「配色」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:317`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 第一段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住主题配色项
- **原文**：

> 配色

- **改写**：配色

### S-141 条目名「主题配色」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:319`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 第一条（右侧显示当前配色名）
- **控件类型**：跳转子页（单选列表，7 种配色）
- **用途**：切换全应用主题配色
- **原文**：

> 主题配色

- **改写**：主题配色

### S-142 条目说明「主题配色」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:320`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 「主题配色」下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 选择应用的主题配色方案

- **改写**：选择应用的主题配色方案

### S-143 候选值「主题配色」七种

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:323`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 主题配色 → 弹出选择列表的七行
- **控件类型**：单选（列表项文字；选中后会 recreate 整个页面）
- **用途**：配色方案显示名
- **原文**：

> B站粉
> 知乎蓝
> 爱奇艺绿
> 紫色空灵
> 五彩斑斓
> 经典灰
> 经典终端

- **改写**：

> B站粉
> 知乎蓝
> 爱奇艺绿
> 紫色空灵
> 五彩斑斓
> 经典灰
> 经典终端

### S-144 小节标题「圆角」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:341`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 第二段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住卡片圆角项
- **原文**：

> 圆角

- **改写**：圆角

### S-145 条目名「卡片圆角」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:343`
- **出现在哪**：设置 → 界面与外观 → 外观设置
- **控件类型**：跳转子页（单选列表：方角 / 圆角）
- **用途**：切换卡片与按钮的圆角风格
- **原文**：

> 卡片圆角

- **改写**：卡片圆角

### S-146 条目说明「卡片圆角」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:344`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 「卡片圆角」下方灰色说明
- **控件类型**：说明文字（较长，含两档的来源说明）
- **用途**：解释两档各自是什么
- **原文**：

> 「方角」还原原项目 BiliClient 的圆角；「圆角」为本项目主题化时的取值

- **改写**：「方角」还原原项目 BiliClient 的圆角；「圆角」为本项目主题化时的取值

### S-147 候选值「卡片圆角」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/ui/appearance/CornerStyle.kt:52`（`DISPLAY_NAMES`）
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 卡片圆角 → 弹出选择列表
- **控件类型**：单选（列表项文字）
- **用途**：两档圆角名
- **原文**：

> 方角
> 圆角

- **改写**：

> 方角
> 圆角

### S-148 小节标题「自定义字体」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:357`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 第三段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住字体相关两项
- **原文**：

> 自定义字体

- **改写**：自定义字体

### S-149 条目名「选择字体文件」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:360`
- **出现在哪**：设置 → 界面与外观 → 外观设置
- **控件类型**：跳转子页（拉起系统文件管理器）
- **用途**：挑一个 TTF/OTF/TTC 作为界面字体
- **原文**：

> 选择字体文件

- **改写**：选择字体文件

### S-150 字体条目说明（两种状态）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:361-362`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 「选择字体文件」下方灰色说明（未安装时为后者，已安装时为前者 + 文件名）
- **控件类型**：说明文字（状态相关）
- **用途**：显示当前字体，并告知支持的格式
- **原文**：

> 当前：系统默认（支持 TTF / OTF / TTC）
> 当前：<已安装字体的文件名>

- **改写**：

> 当前：系统默认（支持 TTF / OTF / TTC）
> 当前：<已安装字体的文件名>

### S-151 按钮「恢复系统字体」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:366`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 页尾常驻按钮（**故意不藏在条件分支里**，是字体渲染坏掉时唯一的自救入口）
- **控件类型**：按钮
- **用途**：删除自定义字体、回到系统字体
- **原文**：

> 恢复系统字体

- **改写**：恢复系统字体

### S-152 字体应用失败提示（前缀）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:78`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 选完字体文件后，失败时的 Toast（`$error` 为 S-157 的某一条）
- **控件类型**：Toast（拼接动态原因）
- **用途**：告知失败原因
- **原文**：

> 字体未应用：<原因>

- **改写**：字体未应用：<原因>

### S-153 字体应用成功提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:80`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 选完字体文件成功后弹出的 Toast
- **控件类型**：Toast
- **用途**：确认已生效
- **原文**：

> 自定义字体已应用

- **改写**：自定义字体已应用

### S-154 没有文件管理器时的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:378`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 点「选择字体文件」但设备没有可响应的文件管理器时的 Toast
- **控件类型**：Toast
- **用途**：解释为什么没反应
- **原文**：

> 没有可用的文件管理器

- **改写**：没有可用的文件管理器

### S-155 已是系统字体时的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:385`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 未安装自定义字体时点「恢复系统字体」的 Toast
- **控件类型**：Toast
- **用途**：避免无效操作
- **原文**：

> 当前已是系统字体

- **改写**：当前已是系统字体

### S-156 恢复系统字体成功的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:389`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 点「恢复系统字体」成功后
- **控件类型**：Toast
- **用途**：确认已恢复
- **原文**：

> 已恢复系统字体

- **改写**：已恢复系统字体

### S-157 字体文件被拒绝的六种原因

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/ui/appearance/FontStyle.kt:123、135、136、139、145、149、182、187、189`
- **出现在哪**：设置 → 界面与外观 → 外观设置 → 「选择字体文件」后，接在 S-152 后缀里显示
- **控件类型**：Toast 内嵌文本（错误原因，含动态值：文件大小上限 MB、异常信息）
- **用途**：告诉用户这个文件为什么不能用
- **原文**：

> 无法读取该文件
> 文件为空
> 文件过大（超过 32MB）
> 复制失败：<异常信息>
> 无法覆盖旧字体文件
> 无法保存字体文件
> 文件不是字体（内容太短）
> 不支持 WOFF/WOFF2 网页字体，请选 TTF / OTF / TTC
> 不是可识别的字体文件（支持 TTF / OTF / TTC）

- **改写**：

> 无法读取该文件
> 文件为空
> 文件过大（超过 32MB）
> 复制失败：<异常信息>
> 无法覆盖旧字体文件
> 无法保存字体文件
> 文件不是字体（内容太短）
> 不支持 WOFF/WOFF2 网页字体，请选 TTF / OTF / TTC
> 不是可识别的字体文件（支持 TTF / OTF / TTC）

### S-158 界面预览页标题

- **位置**：`app/src/main/res/values/strings.xml:255`（`@string/pagename_setting_ui_preview`，布局引用 `activity_setting_ui_preview.xml:24`）
- **出现在哪**：设置 → 界面与外观 → 查看预览 → 顶栏标题
- **控件类型**：页面标题文字
- **用途**：标明这是预览页
- **原文**：

> 界面大小预览

- **改写**：界面大小预览

---

## 五、内容与浏览（设置 → 内容与浏览）

### 5.1 分组页入口列表

### S-159 入口名「菜单设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:395`
- **出现在哪**：设置 → 内容与浏览 → 第一张卡片
- **控件类型**：跳转子页（`SettingMenuActivity`）
- **用途**：调整主菜单顺序与启用状态
- **原文**：

> 菜单设置

- **改写**：菜单设置

### S-160 入口说明「菜单设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:395`
- **出现在哪**：设置 → 内容与浏览 → 「菜单设置」卡片说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 调整菜单顺序、将菜单项移入未启用

- **改写**：调整菜单顺序、将菜单项移入未启用

### S-161 入口名「我的页面设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:398`
- **出现在哪**：设置 → 内容与浏览 → 第二张卡片
- **控件类型**：跳转子页（`SettingMySpaceActivity`）
- **用途**：调整「我的」页面入口
- **原文**：

> 我的页面设置

- **改写**：我的页面设置

### S-162 入口说明「我的页面设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:398`
- **出现在哪**：设置 → 内容与浏览 → 「我的页面设置」卡片说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 调整「我的」页面入口顺序、将入口移入更多

- **改写**：调整「我的」页面入口顺序、将入口移入更多

### S-163 入口名「搜索设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:401`
- **出现在哪**：设置 → 内容与浏览 → 第三张卡片
- **控件类型**：跳转子页（`SettingSearchActivity`）
- **用途**：搜索类别显隐与排序
- **原文**：

> 搜索设置

- **改写**：搜索设置

### S-164 入口说明「搜索设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:401`
- **出现在哪**：设置 → 内容与浏览 → 「搜索设置」卡片说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 搜索类别显示与排序

- **改写**：搜索类别显示与排序

### S-165 入口名「详情页设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:404`
- **出现在哪**：设置 → 内容与浏览 → 第四张卡片
- **控件类型**：跳转子页（`SettingInfoActivity`）
- **用途**：视频/专栏详情页行为
- **原文**：

> 详情页设置

- **改写**：详情页设置

### S-166 入口说明「详情页设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:404`
- **出现在哪**：设置 → 内容与浏览 → 「详情页设置」卡片说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 视频详情页、专栏详情页等相关设置

- **改写**：视频详情页、专栏详情页等相关设置

### S-167 入口名「评论区设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:407`
- **出现在哪**：设置 → 内容与浏览 → 第五张卡片
- **控件类型**：跳转子页（`SettingRepliesActivity`）
- **用途**：评论区显示策略
- **原文**：

> 评论区设置

- **改写**：评论区设置

### S-168 入口说明「评论区设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:407`
- **出现在哪**：设置 → 内容与浏览 → 「评论区设置」卡片说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 关于评论区的一些设置选项

- **改写**：关于评论区的一些设置选项

### S-169 入口名「通用偏好」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:410`
- **出现在哪**：设置 → 内容与浏览 → 第六张卡片（点进去的页面标题叫「偏好设置」，见 S-228，两者措辞不一致）
- **控件类型**：跳转子页（`SettingPrefActivity`）
- **用途**：杂项偏好的总入口
- **原文**：

> 通用偏好

- **改写**：通用偏好

### S-170 入口说明「通用偏好」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:410`
- **出现在哪**：设置 → 内容与浏览 → 「通用偏好」卡片说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 一些特殊适配和特殊需求选项

- **改写**：一些特殊适配和特殊需求选项

### 5.2 菜单设置页（设置 → 内容与浏览 → 菜单设置）

### S-171 页面标题「菜单设置」

- **位置**：`app/src/main/res/values/strings.xml:250`（`@string/pagename_setting_menu`，布局引用 `activity_setting_menu.xml:24`）
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 顶栏标题
- **控件类型**：页面标题文字
- **用途**：标明当前页面
- **原文**：

> 菜单设置

- **改写**：菜单设置

### S-172 分区标题「已启用」

- **位置**：`app/src/main/res/values/strings.xml:676`（`R.string.desc_menu_enabled`，使用点 `app/src/main/java/com/RobinNotBad/BiliClient/adapter/MenuSettingAdapter.kt:87`）
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 上半区标题
- **控件类型**：说明文字（分区标题）
- **用途**：标记"会显示在菜单里"的一区
- **原文**：

> 已启用

- **改写**：已启用

### S-173 分区标题「未启用」

- **位置**：`app/src/main/res/values/strings.xml:677`（`R.string.desc_menu_disabled`，`MenuSettingAdapter.kt:87`）
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 下半区标题
- **控件类型**：说明文字（分区标题）
- **用途**：标记"被收起来"的一区
- **原文**：

> 未启用

- **改写**：未启用

### S-174 固定项角标「固定」

- **位置**：`app/src/main/res/values/strings.xml:678`（`R.string.desc_menu_fixed`）
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 固定项（推荐/设置/缓存/搜索）右侧的角标
- **控件类型**：说明文字（行内小角标）
- **用途**：表示该项不可拖动/隐藏
- **原文**：

> 固定

- **改写**：固定

### S-175 固定项长按提示

- **位置**：`app/src/main/res/values/strings.xml:679`（`R.string.desc_menu_fixed_hint`，`MenuSettingAdapter.kt:163`）
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 长按固定项时弹出的 Toast/提示
- **控件类型**：Toast
- **用途**：解释为什么拖不动
- **原文**：

> 此项不可隐藏

- **改写**：此项不可隐藏

### S-176 页面底部操作说明（菜单设置）

- **位置**：`app/src/main/res/values/strings.xml:680`（`R.string.desc_menu_footer`，`MenuSettingAdapter.kt:93`）
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 列表最下方整段说明
- **控件类型**：说明文字（页脚，多行）
- **用途**：把拖拽/点击/固定规则一次讲清
- **原文**：

> 长按并拖动可调整菜单顺序
> 拖入未启用区域可将其移入未启用列表
> 点击未启用项将其放回已启用列表末尾
> 推荐、设置、缓存、搜索固定不可隐藏

- **改写**：

> 长按并拖动可调整菜单顺序
> 拖入未启用区域可将其移入未启用列表
> 点击未启用项将其放回已启用列表末尾
> 推荐、设置、缓存、搜索固定不可隐藏

### S-177 菜单项名「推荐」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:46`（`btnNames` 的显示名，菜单设置页据此渲染行标题）
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名（固定项）
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名，同时是主菜单按钮文字
- **原文**：

> 推荐

- **改写**：推荐

### S-178 菜单项名「短视频」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:47`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 短视频

- **改写**：短视频

### S-179 菜单项名「热门」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:48`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 热门

- **改写**：热门

### S-180 菜单项名「入站必刷」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:49`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 入站必刷

- **改写**：入站必刷

### S-181 菜单项名「全站排行榜」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:50`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 全站排行榜

- **改写**：全站排行榜

### S-182 菜单项名「热搜」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:51`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 热搜

- **改写**：热搜

### S-183 菜单项名「直播」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:52`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 直播

- **改写**：直播

### S-184 菜单项名「时间线」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:53`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 时间线

- **改写**：时间线

### S-185 菜单项名「搜索」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:54`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名（固定项）
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 搜索

- **改写**：搜索

### S-186 菜单项名「动态」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:55`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 动态

- **改写**：动态

### S-187 菜单项名「我的」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:56`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 我的

- **改写**：我的

### S-188 菜单项名「消息」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:57`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 消息

- **改写**：消息

### S-189 菜单项名「缓存」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:58`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名（固定项）
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 缓存

- **改写**：缓存

### S-190 菜单项名「设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt:59`
- **出现在哪**：设置 → 内容与浏览 → 菜单设置 → 条目名（固定项）
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：菜单项名
- **原文**：

> 设置

- **改写**：设置

### 5.3 我的页面设置页（设置 → 内容与浏览 → 我的页面设置）

### S-191 页面标题「我的页面设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingMySpaceActivity.kt:28`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 顶栏标题
- **控件类型**：页面标题文字
- **用途**：标明当前页面
- **原文**：

> 我的页面设置

- **改写**：我的页面设置

### S-192 分区标题「主列表」

- **位置**：`app/src/main/res/values/strings.xml:682`（`R.string.desc_myspace_main`，`MySpaceSettingAdapter.kt:83`）
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 上半区标题
- **控件类型**：说明文字（分区标题）
- **用途**：标记「我的」页主列表区
- **原文**：

> 主列表

- **改写**：主列表

### S-193 分区标题「更多列表」

- **位置**：`app/src/main/res/values/strings.xml:683`（`R.string.desc_myspace_more`，`MySpaceSettingAdapter.kt:83`）
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 下半区标题
- **控件类型**：说明文字（分区标题）
- **用途**：标记「更多」列表区
- **原文**：

> 更多列表

- **改写**：更多列表

### S-194 页面底部操作说明（我的页面设置）

- **位置**：`app/src/main/res/values/strings.xml:684`（`R.string.desc_myspace_footer`，`MySpaceSettingAdapter.kt:89`）
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 列表最下方整段说明
- **控件类型**：说明文字（页脚，多行）
- **用途**：讲清拖拽/点击规则与两个固定项
- **原文**：

> 长按并拖动可调整顺序
> 拖入另一区域可将其移入该列表
> 点击更多列表项将其放回主列表末尾
> 用户卡片固定在最顶部，「更多」与「退出登录」固定排最后两位
> 更多列表为空时，页面上不显示「更多」按钮

- **改写**：

> 长按并拖动可调整顺序
> 拖入另一区域可将其移入该列表
> 点击更多列表项将其放回主列表末尾
> 用户卡片固定在最顶部，「更多」与「退出登录」固定排最后两位
> 更多列表为空时，页面上不显示「更多」按钮

### S-195 我的页面入口名「关注」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceMenu.kt:20`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 拖拽列表项标题（数据源）
- **控件类型**：拖拽列表项标题
- **用途**：「我的」页入口名
- **原文**：

> 关注

- **改写**：关注

### S-196 我的页面入口名「稍后再看」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceMenu.kt:21`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 拖拽列表项标题
- **控件类型**：拖拽列表项标题
- **用途**：「我的」页入口名
- **原文**：

> 稍后再看

- **改写**：稍后再看

### S-197 我的页面入口名「收藏」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceMenu.kt:22`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 拖拽列表项标题
- **控件类型**：拖拽列表项标题
- **用途**：「我的」页入口名
- **原文**：

> 收藏

- **改写**：收藏

### S-198 我的页面入口名「追番列表」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceMenu.kt:23`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 拖拽列表项标题
- **控件类型**：拖拽列表项标题
- **用途**：「我的」页入口名
- **原文**：

> 追番列表

- **改写**：追番列表

### S-199 我的页面入口名「历史记录」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceMenu.kt:24`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 拖拽列表项标题
- **控件类型**：拖拽列表项标题
- **用途**：「我的」页入口名
- **原文**：

> 历史记录

- **改写**：历史记录

### S-200 我的页面入口名「创作中心」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceMenu.kt:25`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 拖拽列表项标题（是否显示另受「创作中心」开关控制）
- **控件类型**：拖拽列表项标题
- **用途**：「我的」页入口名
- **原文**：

> 创作中心

- **改写**：创作中心

### S-201 我的页面入口名「大会员」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceMenu.kt:26`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 拖拽列表项标题
- **控件类型**：拖拽列表项标题
- **用途**：「我的」页入口名
- **原文**：

> 大会员

- **改写**：大会员

### S-202 我的页面入口名「登录记录」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceMenu.kt:27`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 拖拽列表项标题
- **控件类型**：拖拽列表项标题
- **用途**：「我的」页入口名
- **原文**：

> 登录记录

- **改写**：登录记录

### S-203 我的页面入口名「硬币变化记录」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceMenu.kt:28`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 拖拽列表项标题
- **控件类型**：拖拽列表项标题
- **用途**：「我的」页入口名
- **原文**：

> 硬币变化记录

- **改写**：硬币变化记录

### S-204 我的页面入口名「经验变化记录」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceMenu.kt:29`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 拖拽列表项标题
- **控件类型**：拖拽列表项标题
- **用途**：「我的」页入口名
- **原文**：

> 经验变化记录

- **改写**：经验变化记录

### S-205 我的页面入口名「编辑个人资料」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/MySpaceMenu.kt:30`
- **出现在哪**：设置 → 内容与浏览 → 我的页面设置 → 拖拽列表项标题
- **控件类型**：拖拽列表项标题
- **用途**：「我的」页入口名
- **原文**：

> 编辑个人资料

- **改写**：编辑个人资料

### 5.4 详情页设置（设置 → 内容与浏览 → 详情页设置）

### S-206 页面标题「详情页设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingInfoActivity.kt:17`
- **出现在哪**：设置 → 内容与浏览 → 详情页设置 → 顶栏标题
- **控件类型**：页面标题文字
- **用途**：标明当前页面
- **原文**：

> 详情页设置

- **改写**：详情页设置

### S-207 条目名「收藏夹单选」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingInfoActivity.kt:21`
- **出现在哪**：设置 → 内容与浏览 → 详情页设置 → 第一项
- **控件类型**：开关
- **用途**：收藏后是否立刻退出收藏页
- **原文**：

> 收藏夹单选

- **改写**：收藏夹单选

### S-208 条目说明「收藏夹单选」

- **位置**：`app/src/main/res/values/strings.xml:130`（`R.string.desc_fav_single`）
- **出现在哪**：设置 → 内容与浏览 → 详情页设置 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：说明收藏与取消收藏的不同行为
- **原文**：

> 打开此选项，添加收藏后即退出收藏页面，取消收藏不自动退出。

- **改写**：打开此选项，添加收藏后即退出收藏页面，取消收藏不自动退出。

### S-209 条目名「收藏成功提示」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingInfoActivity.kt:22`
- **出现在哪**：设置 → 内容与浏览 → 详情页设置
- **控件类型**：开关
- **用途**：退出收藏页时是否弹"收藏成功"
- **原文**：

> 收藏成功提示

- **改写**：收藏成功提示

### S-210 条目说明「收藏成功提示」

- **位置**：`app/src/main/res/values/strings.xml:131`（`R.string.desc_fav_notice`）
- **出现在哪**：设置 → 内容与浏览 → 详情页设置 → 该开关下方灰色说明
- **控件类型**：说明文字（两行，带吐槽）
- **用途**：解释这个提示其实是心理安慰
- **原文**：

> 打开此选项，退出收藏页面会提示收藏成功。
> 但其实在你框选了收藏夹的时候就已经收藏了，这个选项只是为了看上去放心（

- **改写**：

> 打开此选项，退出收藏页面会提示收藏成功。
> 但其实在你框选了收藏夹的时候就已经收藏了，这个选项只是为了看上去放心（

### S-211 条目名「点击封面播放」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingInfoActivity.kt:23`
- **出现在哪**：设置 → 内容与浏览 → 详情页设置
- **控件类型**：开关
- **用途**：点封面是播放还是看大图
- **原文**：

> 点击封面播放

- **改写**：点击封面播放

### S-212 条目说明「点击封面播放」

- **位置**：`app/src/main/res/values/strings.xml:132`（`R.string.desc_cover_play`）
- **出现在哪**：设置 → 内容与浏览 → 详情页设置 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：说明另一种行为
- **原文**：

> 打开此选项，点击封面将播放视频，否则将查看大图。

- **改写**：打开此选项，点击封面将播放视频，否则将查看大图。

### S-213 条目名「显示视频标签」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingInfoActivity.kt:24`
- **出现在哪**：设置 → 内容与浏览 → 详情页设置
- **控件类型**：开关
- **用途**：详情页是否显示标签
- **原文**：

> 显示视频标签

- **改写**：显示视频标签

### S-214 条目说明「显示视频标签」

- **位置**：`app/src/main/res/values/strings.xml:133`（`R.string.desc_tags_enable`）
- **出现在哪**：设置 → 内容与浏览 → 详情页设置 → 该开关下方灰色说明
- **控件类型**：说明文字（含示例标签串）
- **用途**：举例说明标签长什么样
- **原文**：

> 打开此选项，视频详情页会显示形如“原神UP主激励计划/原神/纳西妲/手机游戏”的视频标签

- **改写**：打开此选项，视频详情页会显示形如“原神UP主激励计划/原神/纳西妲/手机游戏”的视频标签

### S-215 条目名「视频相关推荐」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingInfoActivity.kt:25`
- **出现在哪**：设置 → 内容与浏览 → 详情页设置
- **控件类型**：开关
- **用途**：评论区右侧相关推荐开关
- **原文**：

> 视频相关推荐

- **改写**：视频相关推荐

### S-216 条目说明「视频相关推荐」

- **位置**：`app/src/main/res/values/strings.xml:134`（`R.string.desc_related_enable`）
- **出现在哪**：设置 → 内容与浏览 → 详情页设置 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：说明出现在哪一侧
- **原文**：

> 打开此选项，视频详情页的评论区右侧会有相关视频推荐

- **改写**：打开此选项，视频详情页的评论区右侧会有相关视频推荐

### S-217 条目名「以游客方式观看直播」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingInfoActivity.kt:26`
- **出现在哪**：设置 → 内容与浏览 → 详情页设置
- **控件类型**：开关
- **用途**：进直播间是否用游客身份
- **原文**：

> 以游客方式观看直播

- **改写**：以游客方式观看直播

### S-218 条目说明「以游客方式观看直播」

- **位置**：`app/src/main/res/values/strings.xml:166`（`R.string.desc_live_by_guest`）
- **出现在哪**：设置 → 内容与浏览 → 详情页设置 → 该开关下方灰色说明
- **控件类型**：说明文字（两行 + 空行 + 警示）
- **用途**：说明副作用（漏弹幕更严重）
- **原文**：

> 开启此选项后，进入直播间时将会使用游客身份
>
> 注意：开启后的漏弹幕情况将会更加严重

- **改写**：

> 开启此选项后，进入直播间时将会使用游客身份
>
> 注意：开启后的漏弹幕情况将会更加严重

### S-219 条目名「一键三连」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingInfoActivity.kt:27`
- **出现在哪**：设置 → 内容与浏览 → 详情页设置 → 最后一项
- **控件类型**：开关
- **用途**：长按点赞触发一键三连
- **原文**：

> 一键三连

- **改写**：一键三连

### S-220 条目说明「一键三连」

- **位置**：`app/src/main/res/values/strings.xml:622`（`R.string.desc_one_triple`）
- **出现在哪**：设置 → 内容与浏览 → 详情页设置 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：说明触发手势
- **原文**：

> 开启此选项后，在视频详情页长按点赞按钮可以进行一键三连操作

- **改写**：开启此选项后，在视频详情页长按点赞按钮可以进行一键三连操作

### 5.5 评论区设置（设置 → 内容与浏览 → 评论区设置）

### S-221 页面标题「评论区设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingRepliesActivity.kt:17`
- **出现在哪**：设置 → 内容与浏览 → 评论区设置 → 顶栏标题
- **控件类型**：页面标题文字
- **用途**：标明当前页面
- **原文**：

> 评论区设置

- **改写**：评论区设置

### S-222 条目名「众生平等」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingRepliesActivity.kt:21`
- **出现在哪**：设置 → 内容与浏览 → 评论区设置 → 第一项
- **控件类型**：开关
- **用途**：是否取消大会员的红色昵称
- **原文**：

> 众生平等

- **改写**：众生平等

### S-223 条目说明「众生平等」

- **位置**：`app/src/main/res/values/strings.xml:163`（`R.string.desc_no_vip_color`）
- **出现在哪**：设置 → 内容与浏览 → 评论区设置 → 「众生平等」下方灰色说明
- **控件类型**：说明文字
- **用途**：解释开关效果
- **原文**：

> 开启此选项后，将不会在评论区为大会员用户显示专属的红色名称

- **改写**：开启此选项后，将不会在评论区为大会员用户显示专属的红色名称

### S-224 条目名「粉丝铭牌消失术」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingRepliesActivity.kt:22`
- **出现在哪**：设置 → 内容与浏览 → 评论区设置 → 第二项
- **控件类型**：开关
- **用途**：隐藏粉丝勋章
- **原文**：

> 粉丝铭牌消失术

- **改写**：粉丝铭牌消失术

### S-225 条目说明「粉丝铭牌消失术」

- **位置**：`app/src/main/res/values/strings.xml:164`（`R.string.desc_no_medal`）
- **出现在哪**：设置 → 内容与浏览 → 评论区设置 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：解释开关效果
- **原文**：

> 开启此选项后，将不会在评论区显示用户的粉丝铭牌

- **改写**：开启此选项后，将不会在评论区显示用户的粉丝铭牌

### S-226 条目名「昵称不换行显示」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingRepliesActivity.kt:23`
- **出现在哪**：设置 → 内容与浏览 → 评论区设置 → 第三项
- **控件类型**：开关
- **用途**：长昵称改为跑马灯单行
- **原文**：

> 昵称不换行显示

- **改写**：昵称不换行显示

### S-227 条目说明「昵称不换行显示」

- **位置**：`app/src/main/res/values/strings.xml:165`（`R.string.desc_reply_marquee_name`）
- **出现在哪**：设置 → 内容与浏览 → 评论区设置 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：说明改用跑马灯
- **原文**：

> 开启此选项后，评论区的用户名称将会以跑马灯的形式展现，而不是换行

- **改写**：开启此选项后，评论区的用户名称将会以跑马灯的形式展现，而不是换行

### 5.6 通用偏好（设置 → 内容与浏览 → 通用偏好）

### S-228 页面标题「偏好设置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:20`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 顶栏标题（**入口卡片叫「通用偏好」，标题叫「偏好设置」，措辞不一致**）
- **控件类型**：页面标题文字
- **用途**：标明当前页面
- **原文**：

> 偏好设置

- **改写**：偏好设置

### S-229 小节标题「功能」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:24`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 第一段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住功能类开关
- **原文**：

> 功能

- **改写**：功能

### S-230 条目名「长按复制」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:25`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 功能 → 第一项
- **控件类型**：开关
- **用途**：长按文字可复制
- **原文**：

> 长按复制

- **改写**：长按复制

### S-231 条目说明「长按复制」

- **位置**：`app/src/main/res/values/strings.xml:137`（`R.string.desc_copy_enable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 「长按复制」下方灰色说明
- **控件类型**：说明文字
- **用途**：说明可复制的对象
- **原文**：

> 打开此选项，长按一些文字（如视频描述或评论）可以打开复制界面以复制这些文本

- **改写**：打开此选项，长按一些文字（如视频描述或评论）可以打开复制界面以复制这些文本

### S-232 条目名「长按打开操作面板」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:28`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 功能
- **控件类型**：开关
- **用途**：长按正文弹操作面板
- **原文**：

> 长按打开操作面板

- **改写**：长按打开操作面板

### S-233 条目说明「长按打开操作面板」

- **位置**：`app/src/main/res/values/strings.xml:138`（`R.string.desc_long_press_panel_enable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明
- **控件类型**：说明文字（长句）
- **用途**：说明开/关两种长按行为
- **原文**：

> 打开此选项，长按评论或动态的正文会弹出操作面板，复制和置顶/删除等都在面板里；关闭则长按直接打开复制界面

- **改写**：打开此选项，长按评论或动态的正文会弹出操作面板，复制和置顶/删除等都在面板里；关闭则长按直接打开复制界面

### S-234 条目名「创作中心」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:30`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 功能
- **控件类型**：开关
- **用途**：「我的」页是否显示创作中心
- **原文**：

> 创作中心

- **改写**：创作中心

### S-235 条目说明「创作中心」

- **位置**：`app/src/main/res/values/strings.xml:135`（`R.string.desc_creative_enable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 「创作中心」下方灰色说明
- **控件类型**：说明文字
- **用途**：说明显示位置与用途
- **原文**：

> 打开此选项，在“我的”页面会显示创作中心（可查看投稿视频的数据变化情况）

- **改写**：打开此选项，在“我的”页面会显示创作中心（可查看投稿视频的数据变化情况）

### S-236 条目名「搜索建议」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:31`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 功能
- **控件类型**：开关
- **用途**：输入时是否显示搜索建议
- **原文**：

> 搜索建议

- **改写**：搜索建议

### S-237 条目说明「搜索建议」

- **位置**：`app/src/main/res/values/strings.xml:623`（`R.string.desc_search_suggestions_enable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 「搜索建议」下方灰色说明
- **控件类型**：说明文字
- **用途**：说明关闭后只剩历史
- **原文**：

> 打开此选项，在搜索页输入关键词时会自动显示搜索建议，关闭后仅显示搜索历史

- **改写**：打开此选项，在搜索页输入关键词时会自动显示搜索建议，关闭后仅显示搜索历史

### S-238 条目名「默认搜索内容」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:33`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 功能
- **控件类型**：开关
- **用途**：搜索框占位符与空搜索的行为
- **原文**：

> 默认搜索内容

- **改写**：默认搜索内容

### S-239 条目说明「默认搜索内容」

- **位置**：`app/src/main/res/values/strings.xml:624`（`R.string.desc_search_default_content_enable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 「默认搜索内容」下方灰色说明
- **控件类型**：说明文字
- **用途**：说明占位符与直接搜索的行为
- **原文**：

> 打开此选项，搜索框会显示默认搜索内容作为占位符，未输入内容直接点击搜索时会搜索默认内容

- **改写**：打开此选项，搜索框会显示默认搜索内容作为占位符，未输入内容直接点击搜索时会搜索默认内容

### S-240 条目名「识别链接」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:35`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 功能
- **控件类型**：开关
- **用途**：文本里的链接/BV号是否可点
- **原文**：

> 识别链接

- **改写**：识别链接

### S-241 条目说明「识别链接」

- **位置**：`app/src/main/res/values/strings.xml:143`（`R.string.desc_link_enable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 「识别链接」下方灰色说明
- **控件类型**：说明文字
- **用途**：列出可识别的号段
- **原文**：

> 打开此选项，会识别部分文本中的链接/BV号/AV号/CV号并标记为链接，点击可打开。

- **改写**：打开此选项，会识别部分文本中的链接/BV号/AV号/CV号并标记为链接，点击可打开。

### S-242 条目名「隐私模式」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:36`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 功能
- **控件类型**：开关
- **用途**：不写入 B 站历史记录
- **原文**：

> 隐私模式

- **改写**：隐私模式

### S-243 条目说明「隐私模式」

- **位置**：`app/src/main/res/values/strings.xml:144`（`R.string.desc_privacy_mode`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 「隐私模式」下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：说明保留下来的能力与丢失的状态
- **原文**：

> 开启后，浏览视频详情和播放视频时不会记录到B站历史记录中。
> 仍可享受登录账号的高清画质，但视频详情页不会显示你的点赞/投币/收藏状态。

- **改写**：

> 开启后，浏览视频详情和播放视频时不会记录到B站历史记录中。
> 仍可享受登录账号的高清画质，但视频详情页不会显示你的点赞/投币/收藏状态。

### S-244 小节标题「更新提醒」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:39`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 第二段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住"启动时检查"类开关（真正的通知开关已移走，见第六节）
- **原文**：

> 更新提醒

- **改写**：更新提醒

### S-245 条目名「新动态数量检查」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:40`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 更新提醒 → 第一项
- **控件类型**：开关
- **用途**：启动时检查新动态并在菜单显示数量
- **原文**：

> 新动态数量检查

- **改写**：新动态数量检查

### S-246 条目说明「新动态数量检查」

- **位置**：`app/src/main/res/values/strings.xml:167`（`R.string.desc_dynamic_update_check_enable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 启动应用时检查是否有新动态并显示在菜单中

- **改写**：启动应用时检查是否有新动态并显示在菜单中

### S-247 条目名「消息数量检查」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:42`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 更新提醒
- **控件类型**：开关
- **用途**：启动时检查未读消息数
- **原文**：

> 消息数量检查

- **改写**：消息数量检查

### S-248 条目说明「消息数量检查」

- **位置**：`app/src/main/res/values/strings.xml:168`（`R.string.desc_message_update_check_enable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：说明统计口径
- **原文**：

> 启动应用时检查是否有新消息并显示在菜单中
> 菜单中的未读消息数量只统计未读私信数和回复我的数、艾特我的数

- **改写**：

> 启动应用时检查是否有新消息并显示在菜单中
> 菜单中的未读消息数量只统计未读私信数和回复我的数、艾特我的数

### S-249 条目名「最近更新的UP主」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:47`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 更新提醒
- **控件类型**：开关
- **用途**：动态页是否显示最近更新 UP 主
- **原文**：

> 最近更新的UP主

- **改写**：最近更新的UP主

### S-250 条目说明「最近更新的UP主」

- **位置**：`app/src/main/res/values/strings.xml:170`（`R.string.desc_recent_up_display_enable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 在动态页面显示最近更新的UP主列表

- **改写**：在动态页面显示最近更新的UP主列表

### S-251 条目名「私信自动已读」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:49`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 更新提醒
- **控件类型**：开关
- **用途**：打开会话即标已读
- **原文**：

> 私信自动已读

- **改写**：私信自动已读

### S-252 条目说明「私信自动已读」

- **位置**：`app/src/main/res/values/strings.xml:173`（`R.string.desc_private_msg_auto_read_enable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 打开私信会话时自动将会话标记为已读

- **改写**：打开私信会话时自动将会话标记为已读

### S-253 条目名「夜深了」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:51`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 更新提醒
- **控件类型**：开关
- **用途**：深夜打开应用时的休息提醒
- **原文**：

> 夜深了

- **改写**：夜深了

### S-254 条目说明「夜深了」

- **位置**：`app/src/main/res/values/strings.xml:175`（`R.string.desc_night_reminder`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 「夜深了」下方灰色说明
- **控件类型**：说明文字
- **用途**：给出触发时段
- **原文**：

> 在23:00-03:00期间打开应用时显示休息提醒

- **改写**：在23:00-03:00期间打开应用时显示休息提醒

### S-255 条目名「后台自动检查更新」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:53`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 更新提醒 → 最后一项
- **控件类型**：开关
- **用途**：启动时查新版本
- **原文**：

> 后台自动检查更新

- **改写**：后台自动检查更新

### S-256 条目说明「后台自动检查更新」

- **位置**：`app/src/main/res/values/strings.xml:59`（`R.string.text_update_background_check_desc`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 启动应用时自动检查是否有新版本

- **改写**：启动应用时自动检查是否有新版本

### S-257 小节标题「交互优化」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:56`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 第三段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住交互与性能类开关
- **原文**：

> 交互优化

- **改写**：交互优化

### S-258 条目名「禁用返回键」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:57`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 交互优化 → 第一项
- **控件类型**：开关
- **用途**：给模拟"右滑=返回"的手表兜底
- **原文**：

> 禁用返回键

- **改写**：禁用返回键

### S-259 条目说明「禁用返回键」

- **位置**：`app/src/main/res/values/strings.xml:129`（`R.string.desc_back_disable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 「禁用返回键」下方灰色说明
- **控件类型**：说明文字（四行长段落）
- **用途**：讲清适用设备、副作用与退出方式
- **原文**：

> 有一些手表（例如米兔等）是通过模拟按下返回键自行实现的右滑返回，若你在左右翻页时因触发滑动返回而退出页面，请开启此开关
> 开启后，其他设备上则为按下返回键也不退出页面，并且右滑退出后会卡住。
> 你可以使用菜单最下方的退出按钮结束本软件。
> 正常用户无需打开。

- **改写**：

> 有一些手表（例如米兔等）是通过模拟按下返回键自行实现的右滑返回，若你在左右翻页时因触发滑动返回而退出页面，请开启此开关
> 开启后，其他设备上则为按下返回键也不退出页面，并且右滑退出后会卡住。
> 你可以使用菜单最下方的退出按钮结束本软件。
> 正常用户无需打开。

### S-260 条目名「禁止视频在相册中显示」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:58`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 交互优化
- **控件类型**：开关
- **用途**：缓存视频是否进系统相册
- **原文**：

> 禁止视频在相册中显示

- **改写**：禁止视频在相册中显示

### S-261 条目说明「禁止视频在相册中显示」

- **位置**：`app/src/main/res/values/strings.xml:136`（`R.string.desc_ban_gallery`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 打开此选项，缓存的视频将不会出现在系统相册中

- **改写**：打开此选项，缓存的视频将不会出现在系统相册中

### S-262 条目名「请求JPG格式图片」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:59`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 交互优化
- **控件类型**：开关
- **用途**：老设备不支持 webp 时的兼容开关
- **原文**：

> 请求JPG格式图片

- **改写**：请求JPG格式图片

### S-263 条目说明「请求JPG格式图片」

- **位置**：`app/src/main/res/values/strings.xml:142`（`R.string.desc_img_request_jpg`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明
- **控件类型**：说明文字（长句）
- **用途**：说明适用设备与代价
- **原文**：

> 部分杂牌手表和安卓版本极低的设备不支持webp格式，可能导致大量图片无法显示。打开此选项，将会请求jpg格式的图片以解决此问题，会略微延长加载时间。

- **改写**：部分杂牌手表和安卓版本极低的设备不支持webp格式，可能导致大量图片无法显示。打开此选项，将会请求jpg格式的图片以解决此问题，会略微延长加载时间。

### S-264 条目名「翻动时不加载图片」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:61`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 交互优化
- **控件类型**：开关
- **用途**：滑动时暂停图片加载
- **原文**：

> 翻动时不加载图片

- **改写**：翻动时不加载图片

### S-265 条目说明「翻动时不加载图片」

- **位置**：`app/src/main/res/values/strings.xml:141`（`R.string.desc_img_no_load_onscroll`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：说明观感与性能的取舍
- **原文**：

> 打开此选项，翻动列表时将不会加载图片，松手后再加载，观感可能有影响，可能增加流畅度。

- **改写**：打开此选项，翻动列表时将不会加载图片，松手后再加载，观感可能有影响，可能增加流畅度。

### S-266 条目名「异步加载布局」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:63`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 交互优化
- **控件类型**：开关
- **用途**：页面是否异步 inflate
- **原文**：

> 异步加载布局

- **改写**：异步加载布局

### S-267 条目说明「异步加载布局」

- **位置**：`app/src/main/res/values/strings.xml:145`（`R.string.desc_async_inflate_enable`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明
- **控件类型**：说明文字（两行，含"自行判断"）
- **用途**：讲清开/关各自的代价
- **原文**：

> 打开此选项，会对部分页面进行异步加载（显示一个加载中动画，加载完成后显示页面）
> * 请注意，异步加载的开启可能会触发一些崩溃问题，但关闭会导致一些页面在打开期间看上去像卡死了一样，体验不佳。请自行判断！

- **改写**：

> 打开此选项，会对部分页面进行异步加载（显示一个加载中动画，加载完成后显示页面）
> * 请注意，异步加载的开启可能会触发一些崩溃问题，但关闭会导致一些页面在打开期间看上去像卡死了一样，体验不佳。请自行判断！

### S-268 条目名「新提示信息显示方式」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:65`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 交互优化
- **控件类型**：开关
- **用途**：Toast 还是新的 Snackbar 式提示
- **原文**：

> 新提示信息显示方式

- **改写**：新提示信息显示方式

### S-269 条目说明「新提示信息显示方式」（硬编码字符串）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:66`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明（**唯一一条硬编码在本页 Kotlin 里的说明**）
- **控件类型**：说明文字
- **用途**：说明打开后的效果
- **原文**：

> 打开此选项，会启用新提示信息显示方式

- **改写**：打开此选项，会启用新提示信息显示方式

### S-270 小节标题「视觉」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:68`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 第四段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住显示相关项
- **原文**：

> 视觉

- **改写**：视觉

### S-271 条目名「我的关注列表分组」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:69`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 视觉
- **控件类型**：开关
- **用途**：关注列表是否按分组展示
- **原文**：

> 我的关注列表分组

- **改写**：我的关注列表分组

### S-272 条目说明「我的关注列表分组」

- **位置**：`app/src/main/res/values/strings.xml:174`（`R.string.desc_follow_group_mode`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 以分组形式展示关注列表

- **改写**：以分组形式展示关注列表

### S-273 小节标题「表冠适配」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:72`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 第五段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住表冠相关三项
- **原文**：

> 表冠适配

- **改写**：表冠适配

### S-274 条目名「启用表冠适配」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:73`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 表冠适配 → 第一项
- **控件类型**：开关
- **用途**：自定义表冠行为的总开关
- **原文**：

> 启用表冠适配

- **改写**：启用表冠适配

### S-275 条目说明「启用表冠适配」

- **位置**：`app/src/main/res/values/strings.xml:36`（`R.string.setting_lab_ui_rotatory`）
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 「启用表冠适配」下方灰色说明
- **控件类型**：说明文字（四行长段落）
- **用途**：解释默认已生效、何时才需要开、灵敏度含义
- **原文**：

> 默认情况下此选项无需开启，系统会自动为可滚动列表应用表冠操作，如果表冠未生效或需要自定义灵敏度，可尝试开启此选项。
> 开启后可能会丢失表冠震动。
> 下面的两项为灵敏度倍数，1.0 即系统原始滚动量，默认 1.0;填 0 等于该类页面不响应表冠。
> 改完立即生效，无需重进页面。

- **改写**：

> 默认情况下此选项无需开启，系统会自动为可滚动列表应用表冠操作，如果表冠未生效或需要自定义灵敏度，可尝试开启此选项。
> 开启后可能会丢失表冠震动。
> 下面的两项为灵敏度倍数，1.0 即系统原始滚动量，默认 1.0;填 0 等于该类页面不响应表冠。
> 改完立即生效，无需重进页面。

### S-276 条目名「表冠适配灵敏度（Recycler）」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:75`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 表冠适配
- **控件类型**：输入框（小数，默认取 `RotaryEncoderSupport.DEFAULT_MULTIPLE`）
- **用途**：列表类页面表冠灵敏度倍数
- **原文**：

> 表冠适配灵敏度（Recycler）

- **改写**：表冠适配灵敏度（Recycler）

### S-277 条目名「表冠适配灵敏度（Scroll）」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.kt:77`
- **出现在哪**：设置 → 内容与浏览 → 通用偏好 → 表冠适配
- **控件类型**：输入框（小数，默认取 `RotaryEncoderSupport.DEFAULT_MULTIPLE`）
- **用途**：滚动容器页面表冠灵敏度倍数
- **原文**：

> 表冠适配灵敏度（Scroll）

- **改写**：表冠适配灵敏度（Scroll）

### 5.7 搜索设置（设置 → 内容与浏览 → 搜索设置）

### S-278 页面标题「搜索设置」

- **位置**：`app/src/main/res/values/strings.xml:251`（`@string/pagename_setting_search`，布局引用 `activity_setting_search.xml:24`）
- **出现在哪**：设置 → 内容与浏览 → 搜索设置 → 顶栏标题
- **控件类型**：页面标题文字
- **用途**：标明当前页面
- **原文**：

> 搜索设置

- **改写**：搜索设置

### S-279 条目名「视频搜索（始终启用）」

- **位置**：`app/src/main/res/layout/activity_setting_search.xml:65`
- **出现在哪**：设置 → 内容与浏览 → 搜索设置 → 第一行（灰掉的开关）
- **控件类型**：开关（`enabled=false`、`clickable=false`，只读展示）
- **用途**：说明"视频"类别不可关闭
- **原文**：

> 视频搜索（始终启用）

- **改写**：视频搜索（始终启用）

### S-280 条目名「显示“专栏”搜索」

- **位置**：`app/src/main/res/layout/activity_setting_search.xml:78`
- **出现在哪**：设置 → 内容与浏览 → 搜索设置 → 第二行
- **控件类型**：开关（退出页面时才落盘）
- **用途**：控制搜索页是否出现"专栏"类别
- **原文**：

> 显示“专栏”搜索

- **改写**：显示“专栏”搜索

### S-281 条目名「显示“用户”搜索」

- **位置**：`app/src/main/res/layout/activity_setting_search.xml:91`
- **出现在哪**：设置 → 内容与浏览 → 搜索设置 → 第三行
- **控件类型**：开关
- **用途**：控制"用户"类别
- **原文**：

> 显示“用户”搜索

- **改写**：显示“用户”搜索

### S-282 条目名「显示“直播”搜索」

- **位置**：`app/src/main/res/layout/activity_setting_search.xml:104`
- **出现在哪**：设置 → 内容与浏览 → 搜索设置 → 第四行
- **控件类型**：开关
- **用途**：控制"直播"类别
- **原文**：

> 显示“直播”搜索

- **改写**：显示“直播”搜索

### S-283 条目名「显示“番剧”搜索」

- **位置**：`app/src/main/res/layout/activity_setting_search.xml:117`
- **出现在哪**：设置 → 内容与浏览 → 搜索设置 → 第五行
- **控件类型**：开关
- **用途**：控制"番剧"类别
- **原文**：

> 显示“番剧”搜索

- **改写**：显示“番剧”搜索

### S-284 按钮「调整搜索类别排序」

- **位置**：`app/src/main/res/layout/activity_setting_search.xml:124`
- **出现在哪**：设置 → 内容与浏览 → 搜索设置 → 第一个按钮
- **控件类型**：按钮（跳转排序页）
- **用途**：进入拖拽排序页
- **原文**：

> 调整搜索类别排序

- **改写**：调整搜索类别排序

### S-285 按钮「重置为默认设置」

- **位置**：`app/src/main/res/layout/activity_setting_search.xml:132`
- **出现在哪**：设置 → 内容与浏览 → 搜索设置 → 第二个按钮
- **控件类型**：按钮（点击即重置全部搜索设置）
- **用途**：一键恢复默认
- **原文**：

> 重置为默认设置

- **改写**：重置为默认设置

### S-286 重置后的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingSearchActivity.kt:74`
- **出现在哪**：设置 → 内容与浏览 → 搜索设置 → 点「重置为默认设置」后的 Toast
- **控件类型**：Toast
- **用途**：确认已重置
- **原文**：

> 已重置为默认设置

- **改写**：已重置为默认设置

### 5.8 搜索类别排序（设置 → 内容与浏览 → 搜索设置 → 调整搜索类别排序）

### S-287 页面标题「搜索类别排序」

- **位置**：`app/src/main/res/values/strings.xml:252`（`@string/pagename_setting_search_sort`；代码里另有硬编码 `setPageName("搜索类别排序")` 于 `SearchSortActivity.kt:43`）
- **出现在哪**：设置 → 内容与浏览 → 搜索设置 → 调整搜索类别排序 → 顶栏标题
- **控件类型**：页面标题文字
- **用途**：标明当前页面
- **原文**：

> 搜索类别排序

- **改写**：搜索类别排序

### S-288 进入排序页的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SearchSortActivity.kt:107`
- **出现在哪**：搜索类别排序 → 每次 `onResume` 弹出的 Toast
- **控件类型**：Toast（每次进页面都会弹）
- **用途**：教用户怎么排序、说明"视频钉在第一位"
- **原文**：

> 长按拖动以排序（视频始终在第一位）

- **改写**：长按拖动以排序（视频始终在第一位）

### S-289 退出排序页的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SearchSortActivity.kt:113`
- **出现在哪**：搜索类别排序 → 离开页面（`onPause`）时的 Toast
- **控件类型**：Toast
- **用途**：确认顺序已保存
- **原文**：

> 排序已保存

- **改写**：排序已保存

### S-290 五个搜索类别名

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SearchSortActivity.kt:29-35`（`categoryNames`）
- **出现在哪**：搜索类别排序 → 列表项文字；同时也是搜索页顶部分类标签的文字
- **控件类型**：拖拽列表项标题（数据源）
- **用途**：搜索类别的显示名
- **原文**：

> 视频
> 番剧
> 专栏
> 用户
> 直播

- **改写**：

> 视频
> 番剧
> 专栏
> 用户
> 直播

---

## 六、通知设置（设置 → 通知设置）

> 只放「会让通知栏响」的开关。页面本身是 `SettingGroupActivity.buildNotifyGroup()`（第 429-435 行）。

### S-291 小节标题「通知开关」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:430`
- **出现在哪**：设置 → 通知设置 → 页面唯一的分组标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住两个通知开关
- **原文**：

> 通知开关

- **改写**：通知开关

### S-292 条目名「新消息通知」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:431`
- **出现在哪**：设置 → 通知设置 → 第一项
- **控件类型**：开关（`private_msg_notify_enable`，默认开）
- **用途**：私信新消息是否弹通知
- **原文**：

> 新消息通知

- **改写**：新消息通知

### S-293 条目说明「新消息通知」

- **位置**：`app/src/main/res/values/strings.xml:169`（`R.string.desc_private_msg_notify_enable`）
- **出现在哪**：设置 → 通知设置 → 该开关下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：说清提醒时机（仅在"应用内检查未读变多"时）与点击行为
- **原文**：

> 发现新消息时在通知栏提醒
> 仅在"打开应用检查未读"发现未读数变多时提醒一次，不做后台定时检查；点通知直接进消息页

- **改写**：

> 发现新消息时在通知栏提醒
> 仅在"打开应用检查未读"发现未读数变多时提醒一次，不做后台定时检查；点通知直接进消息页

### S-294 条目名「追番更新提醒」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:433`
- **出现在哪**：设置 → 通知设置 → 第二项
- **控件类型**：开关（`bangumi_update_notify_enable`，默认开）
- **用途**：追的番有新一集时是否弹通知
- **原文**：

> 追番更新提醒

- **改写**：追番更新提醒

### S-295 条目说明「追番更新提醒」

- **位置**：`app/src/main/res/values/strings.xml:171`（`R.string.desc_bangumi_update_notify_enable`）
- **出现在哪**：设置 → 通知设置 → 该开关下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：说清判据（最新一集变了才提醒）与点击行为
- **原文**：

> 追的番剧有新一集时在通知栏提醒
> 只在打开应用时对比追番列表，发现最新一集变了才提醒一次，不做后台定时检查；点通知直接进追番列表

- **改写**：

> 追的番剧有新一集时在通知栏提醒
> 只在打开应用时对比追番列表，发现最新一集变了才提醒一次，不做后台定时检查；点通知直接进追番列表

---

## 七、缓存与下载（设置 → 缓存与下载）

### S-296 小节标题「下载引擎」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:438`
- **出现在哪**：设置 → 缓存与下载 → 第一段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住下载引擎三项
- **原文**：

> 下载引擎

- **改写**：下载引擎

### S-297 条目名「启用高速下载模式」

- **位置**：`app/src/main/res/values/strings.xml:268`（`R.string.aria2_enable`，用于 `SettingGroupActivity.kt:439`）
- **出现在哪**：设置 → 缓存与下载 → 下载引擎 → 第一项
- **控件类型**：开关（`aria2_enabled`，默认开）
- **用途**：是否用多线程下载引擎
- **原文**：

> 启用高速下载模式

- **改写**：启用高速下载模式

### S-298 条目说明「启用高速下载模式」

- **位置**：`app/src/main/res/values/strings.xml:269`（`R.string.aria2_enable_desc`）
- **出现在哪**：设置 → 缓存与下载 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：说明引擎能力范围
- **原文**：

> 启用后将使用多线程高速下载引擎，支持视频、音频和图片文件的快速下载。

- **改写**：启用后将使用多线程高速下载引擎，支持视频、音频和图片文件的快速下载。

### S-299 条目名「使用旧版下载器」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:440`
- **出现在哪**：设置 → 缓存与下载 → 下载引擎 → 第二项
- **控件类型**：开关（`dev_download_old`，默认关）
- **用途**：后台下载出问题时的回退开关
- **原文**：

> 使用旧版下载器

- **改写**：使用旧版下载器

### S-300 条目说明「使用旧版下载器」

- **位置**：`app/src/main/res/values/strings.xml:40`（`R.string.setting_lab_download_old`）
- **出现在哪**：设置 → 缓存与下载 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：讲清何时该打开
- **原文**：

> 若后台下载在你的设备上有问题，请打开此选项以换回旧版的前台下载器！

- **改写**：若后台下载在你的设备上有问题，请打开此选项以换回旧版的前台下载器！

### S-301 条目名「分片数」

- **位置**：`app/src/main/res/values/strings.xml:271`（`R.string.aria2_split`，用于 `SettingGroupActivity.kt:441`）
- **出现在哪**：设置 → 缓存与下载 → 下载引擎 → 第三项
- **控件类型**：输入框（整数，默认 5）
- **用途**：单文件下载分片数
- **原文**：

> 分片数

- **改写**：分片数

### S-302 条目说明「分片数」

- **位置**：`app/src/main/res/values/strings.xml:272`（`R.string.aria2_split_desc`）
- **出现在哪**：设置 → 缓存与下载 → 「分片数」下方灰色说明
- **控件类型**：说明文字
- **用途**：给出默认值与调节代价
- **原文**：

> 单个文件下载时使用的分片数量，默认5。提高分片数可加速下载但会增加服务器负载

- **改写**：单个文件下载时使用的分片数量，默认5。提高分片数可加速下载但会增加服务器负载

### S-303 小节标题「缓存选项」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:443`
- **出现在哪**：设置 → 缓存与下载 → 第二段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住快捷缓存相关项
- **原文**：

> 缓存选项

- **改写**：缓存选项

### S-304 条目名「启用快捷缓存方式」

- **位置**：`app/src/main/res/values/strings.xml:275`（`R.string.cache_quick_mode`，用于 `SettingGroupActivity.kt:444`）
- **出现在哪**：设置 → 缓存与下载 → 缓存选项 → 第一项（打开/关闭会重建本页，联动显示"默认缓存画质"）
- **控件类型**：开关（`cache_quick_mode`，默认开）
- **用途**：长按视频快速缓存
- **原文**：

> 启用快捷缓存方式

- **改写**：启用快捷缓存方式

### S-305 条目说明「启用快捷缓存方式」

- **位置**：`app/src/main/res/values/strings.xml:276`（`R.string.cache_quick_mode_desc`）
- **出现在哪**：设置 → 缓存与下载 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：说明生效范围与例外页面
- **原文**：

> 开启后，长按任意视频即可快速缓存到本地，在缓存页面和短视频界面中无效

- **改写**：开启后，长按任意视频即可快速缓存到本地，在缓存页面和短视频界面中无效

### S-306 条目名「并行下载视频数」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:447`
- **出现在哪**：设置 → 缓存与下载 → 缓存选项
- **控件类型**：输入框（整数，默认取 `Aria2Util.DEFAULT_PARALLEL_DOWNLOAD_VIDEOS`）
- **用途**：后台同时下几个视频
- **原文**：

> 并行下载视频数

- **改写**：并行下载视频数

### S-307 条目说明「并行下载视频数」

- **位置**：`app/src/main/res/values/strings.xml:687`（`R.string.desc_parallel_download`）
- **出现在哪**：设置 → 缓存与下载 → 该输入框下方灰色说明
- **控件类型**：说明文字
- **用途**：给推荐值
- **原文**：

> 同时在后台并行下载的视频数量，推荐2-5个。网络环境良好时可以提高效率

- **改写**：同时在后台并行下载的视频数量，推荐2-5个。网络环境良好时可以提高效率

### S-308 条目名「默认缓存画质」

- **位置**：`app/src/main/res/values/strings.xml:277`（`R.string.cache_default_quality`，用于 `SettingGroupActivity.kt:450`；**仅在快捷缓存开启时出现**）
- **出现在哪**：设置 → 缓存与下载 → 缓存选项 → 快捷缓存开关之后
- **控件类型**：跳转子页（单选列表，5 项）
- **用途**：快捷缓存默认取哪一档
- **原文**：

> 默认缓存画质

- **改写**：默认缓存画质

### S-309 条目说明「默认缓存画质」

- **位置**：`app/src/main/res/values/strings.xml:278`（`R.string.cache_default_quality_desc`）
- **出现在哪**：设置 → 缓存与下载 → 「默认缓存画质」下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 快捷缓存时使用的默认视频画质选项

- **改写**：快捷缓存时使用的默认视频画质选项

### S-310 候选值「默认缓存画质」五项

- **位置**：`app/src/main/res/values/strings.xml:279-283`（`cache_quality_highest` / `_720p` / `_360p` / `_audio_only` / `_dialog`，用于 `SettingGroupActivity.kt:455-459`）
- **出现在哪**：设置 → 缓存与下载 → 默认缓存画质 → 弹出选择列表的五行
- **控件类型**：单选（列表项文字）
- **用途**：画质档位显示名（其中「弹出选择框」= 每次缓存都问）
- **原文**：

> 最高画质
> 720P
> 360P
> 仅音频
> 弹出选择框

- **改写**：

> 最高画质
> 720P
> 360P
> 仅音频
> 弹出选择框

### S-311 条目名「强制高分辨率选项」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:464`
- **出现在哪**：设置 → 缓存与下载 → 缓存选项 → 最后一项
- **控件类型**：开关（`force_high_quality`，默认开）
- **用途**：缓存时是否强行列出高码率选项
- **原文**：

> 强制高分辨率选项

- **改写**：强制高分辨率选项

### S-312 条目说明「强制高分辨率选项」

- **位置**：`app/src/main/res/values/strings.xml:688`（`R.string.desc_force_high_quality`）
- **出现在哪**：设置 → 缓存与下载 → 该开关下方灰色说明
- **控件类型**：说明文字（长句）
- **用途**：说明白"列出来但视频可能并不支持"
- **原文**：

> 强制在缓存分辨率选择中显示4K、1080P高码率、1080P等选项，即使该视频可能不支持这些分辨率

- **改写**：强制在缓存分辨率选择中显示4K、1080P高码率、1080P等选项，即使该视频可能不支持这些分辨率

### S-313 小节标题「存储路径」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:466`
- **出现在哪**：设置 → 缓存与下载 → 第三段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住两个路径输入框
- **原文**：

> 存储路径

- **改写**：存储路径

### S-314 条目名「缓存路径」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:467`
- **出现在哪**：设置 → 缓存与下载 → 存储路径 → 第一项（输入框内预填当前默认路径）
- **控件类型**：输入框（字符串路径）
- **用途**：视频缓存目录
- **原文**：

> 缓存路径

- **改写**：缓存路径

### S-315 条目说明「缓存路径」

- **位置**：`app/src/main/res/values/strings.xml:37`（`R.string.setting_lab_path_video`）
- **出现在哪**：设置 → 缓存与下载 → 「缓存路径」下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：说明自动建目录，旧缓存要手动搬
- **原文**：

> 缓存的视频将会保存到这个位置，若文件夹不存在会自动创建。之前缓存的视频请用文件管理器手动移动到新文件夹内。
> 不保证会不会出现奇怪的问题。

- **改写**：

> 缓存的视频将会保存到这个位置，若文件夹不存在会自动创建。之前缓存的视频请用文件管理器手动移动到新文件夹内。
> 不保证会不会出现奇怪的问题。

### S-316 条目名「图片下载路径」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:468`
- **出现在哪**：设置 → 缓存与下载 → 存储路径 → 第二项
- **控件类型**：输入框（字符串路径）
- **用途**：图片保存目录
- **原文**：

> 图片下载路径

- **改写**：图片下载路径

### S-317 条目说明「图片下载路径」

- **位置**：`app/src/main/res/values/strings.xml:38`（`R.string.setting_lab_path_pictures`）
- **出现在哪**：设置 → 缓存与下载 → 「图片下载路径」下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：同上，针对图片
- **原文**：

> 下载的图片将会保存到这个位置，若文件夹不存在会自动创建。之前下载的图片请用文件管理器手动移动到新文件夹内。
> 不保证会不会出现奇怪的问题。

- **改写**：

> 下载的图片将会保存到这个位置，若文件夹不存在会自动创建。之前下载的图片请用文件管理器手动移动到新文件夹内。
> 不保证会不会出现奇怪的问题。

---

## 八、高级与实验（设置 → 高级与实验）

### S-318 小节标题「性能」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:472`
- **出现在哪**：设置 → 高级与实验 → 第一段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住性能开关
- **原文**：

> 性能

- **改写**：性能

### S-319 条目名「高性能模式」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:474`
- **出现在哪**：设置 → 高级与实验 → 性能 → 唯一一项
- **控件类型**：开关（`high_performance_mode`，默认关）
- **用途**：用更多资源换流畅度
- **原文**：

> 高性能模式

- **改写**：高性能模式

### S-320 条目说明「高性能模式」

- **位置**：`app/src/main/res/values/strings.xml:689`（`R.string.desc_high_performance`）
- **出现在哪**：设置 → 高级与实验 → 「高性能模式」下方灰色说明
- **控件类型**：说明文字（两行句）
- **用途**：说清手机自动启用、手表建议关
- **原文**：

> 开启后将使用更多系统资源以提升运行速度。高性能手机自动启用；低性能手表建议关闭以获得更好的续航和稳定性。

- **改写**：开启后将使用更多系统资源以提升运行速度。高性能手机自动启用；低性能手表建议关闭以获得更好的续航和稳定性。

### S-321 打开高性能模式后的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:481`
- **出现在哪**：设置 → 高级与实验 → 打开「高性能模式」且当前未处于高性能档时的 Toast
- **控件类型**：Toast
- **用途**：说明部分优化要下次启动才生效（避免用户以为没生效）
- **原文**：

> 高性能模式已开启，部分优化将在下次启动应用时生效

- **改写**：高性能模式已开启，部分优化将在下次启动应用时生效

### S-322 关闭高性能模式后的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:483`
- **出现在哪**：设置 → 高级与实验 → 关掉「高性能模式」时的 Toast
- **控件类型**：Toast
- **用途**：确认已切到性能优先
- **原文**：

> 已切换到性能优先模式

- **改写**：已切换到性能优先模式

### S-323 小节标题「推荐」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:487`
- **出现在哪**：设置 → 高级与实验 → 第二段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住推荐流相关项
- **原文**：

> 推荐

- **改写**：推荐

### S-324 条目名「推荐视频去重」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:488`
- **出现在哪**：设置 → 高级与实验 → 推荐 → 第一项
- **控件类型**：开关（默认开）
- **用途**：过滤看过的推荐视频
- **原文**：

> 推荐视频去重

- **改写**：推荐视频去重

### S-325 条目说明「推荐视频去重」

- **位置**：`app/src/main/res/values/strings.xml:690`（`R.string.desc_recommend_dedup`）
- **出现在哪**：设置 → 高级与实验 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 过滤掉已经显示过的推荐视频，避免重复

- **改写**：过滤掉已经显示过的推荐视频，避免重复

### S-326 条目名「推荐源」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:489`
- **出现在哪**：设置 → 高级与实验 → 推荐 → 第二项
- **控件类型**：跳转子页（单选列表：网页源 / APP源 / 混合使用）
- **用途**：选哪套 API 拉推荐
- **原文**：

> 推荐源

- **改写**：推荐源

### S-327 条目说明「推荐源」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:491`
- **出现在哪**：设置 → 高级与实验 → 「推荐源」下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 选择使用哪个API获取推荐视频

- **改写**：选择使用哪个API获取推荐视频

### S-328 候选值「推荐源」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:494`
- **出现在哪**：设置 → 高级与实验 → 推荐源 → 弹出选择列表
- **控件类型**：单选（列表项文字）
- **用途**：三个来源的显示名
- **原文**：

> 网页源
> APP源
> 混合使用

- **改写**：

> 网页源
> APP源
> 混合使用

### S-329 小节标题「功能开关」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:502`
- **出现在哪**：设置 → 高级与实验 → 第三段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住若干实验性功能开关
- **原文**：

> 功能开关

- **改写**：功能开关

### S-330 条目名「新版弹幕获取方式」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:503`
- **出现在哪**：设置 → 高级与实验 → 功能开关 → 第一项
- **控件类型**：开关（默认开）
- **用途**：切换弹幕接口
- **原文**：

> 新版弹幕获取方式

- **改写**：新版弹幕获取方式

### S-331 条目说明「新版弹幕获取方式」

- **位置**：`app/src/main/res/values/strings.xml:156`（`R.string.desc_new_danmaku_api`）
- **出现在哪**：设置 → 高级与实验 → 该开关下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：说明只对内置播放器有效、不支持高级弹幕
- **原文**：

> 开启后，内置播放器将通过新的弹幕接口实时获取视频弹幕。
> 注意：该选项仅对内置播放器有效，且不支持高级弹幕

- **改写**：

> 开启后，内置播放器将通过新的弹幕接口实时获取视频弹幕。
> 注意：该选项仅对内置播放器有效，且不支持高级弹幕

### S-332 条目名「私信未读标记」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:504`
- **出现在哪**：设置 → 高级与实验 → 功能开关 → 第二项
- **控件类型**：开关（默认关）
- **用途**：私信列表是否显示红色未读并置顶
- **原文**：

> 私信未读标记

- **改写**：私信未读标记

### S-333 条目说明「私信未读标记」

- **位置**：`app/src/main/res/values/strings.xml:172`（`R.string.desc_private_msg_unread_badge_enable`）
- **出现在哪**：设置 → 高级与实验 → 该开关下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：说明效果与卡顿代价
- **原文**：

> 在私信会话列表中为未读消息显示红色"未读"标记并置顶显示
> 注意：开启后打开消息页面可能会加剧卡顿问题。

- **改写**：

> 在私信会话列表中为未读消息显示红色"未读"标记并置顶显示
> 注意：开启后打开消息页面可能会加剧卡顿问题。

### S-334 条目名「虚拟合集」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:505`
- **出现在哪**：设置 → 高级与实验 → 功能开关 → 第三项
- **控件类型**：开关（默认开）
- **用途**：本地缓存/收藏夹自动组成合集
- **原文**：

> 虚拟合集

- **改写**：虚拟合集

### S-335 条目说明「虚拟合集」

- **位置**：`app/src/main/res/values/strings.xml:691`（`R.string.desc_virtual_collection`）
- **出现在哪**：设置 → 高级与实验 → 该开关下方灰色说明
- **控件类型**：说明文字（长句）
- **用途**：讲清合集怎么组成、能干什么
- **原文**：

> 开启后，在播放本地缓存视频或收藏夹视频时，会将同一文件夹/收藏夹的视频自动组成合集，支持切换和自动联播

- **改写**：开启后，在播放本地缓存视频或收藏夹视频时，会将同一文件夹/收藏夹的视频自动组成合集，支持切换和自动联播

### S-336 小节标题「播放器兼容」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:507`
- **出现在哪**：设置 → 高级与实验 → 第四段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住播放器兼容性开关
- **原文**：

> 播放器兼容

- **改写**：播放器兼容

### S-337 条目名「播放器旋屏兼容方案」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:508`
- **出现在哪**：设置 → 高级与实验 → 播放器兼容 → 第一项
- **控件类型**：开关（默认关）
- **用途**：软件旋屏兜底
- **原文**：

> 播放器旋屏兼容方案

- **改写**：播放器旋屏兼容方案

### S-338 条目说明「播放器旋屏兼容方案」

- **位置**：`app/src/main/res/values/strings.xml:692`（`R.string.desc_player_rotate_software`）
- **出现在哪**：设置 → 高级与实验 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：指出适用机型与症状
- **原文**：

> 在极少数手表上（如小米手表），系统旋屏存在显示不全的问题。打开此开关，播放器将会使用软件旋屏方法。

- **改写**：在极少数手表上（如小米手表），系统旋屏存在显示不全的问题。打开此开关，播放器将会使用软件旋屏方法。

### S-339 条目名「显示视频分段」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:509`
- **出现在哪**：设置 → 高级与实验 → 播放器兼容
- **控件类型**：开关（默认开）
- **用途**：章节看点显示开关
- **原文**：

> 显示视频分段

- **改写**：显示视频分段

### S-340 条目说明「显示视频分段」

- **位置**：`app/src/main/res/values/strings.xml:693`（`R.string.desc_player_viewpoints`）
- **出现在哪**：设置 → 高级与实验 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 显示视频的章节看点信息，可快速跳转到指定章节

- **改写**：显示视频的章节看点信息，可快速跳转到指定章节

### S-341 条目名「系统媒体控件」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:510`
- **出现在哪**：设置 → 高级与实验 → 播放器兼容
- **控件类型**：开关（默认关）
- **用途**：通知栏/锁屏播放控制
- **原文**：

> 系统媒体控件

- **改写**：系统媒体控件

### S-342 条目说明「系统媒体控件」

- **位置**：`app/src/main/res/values/strings.xml:45`（`R.string.setting_lab_media_session`）
- **出现在哪**：设置 → 高级与实验 → 该开关下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：说明能力与兼容性限制
- **原文**：

> 启用后可在通知栏和锁屏界面控制播放。
> 仅部分设备可正常使用。

- **改写**：

> 启用后可在通知栏和锁屏界面控制播放。
> 仅部分设备可正常使用。

### S-343 条目名「互动视频调试」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:511`
- **出现在哪**：设置 → 高级与实验 → 播放器兼容 → 最后一项
- **控件类型**：开关（默认关）
- **用途**：在播放器里显示互动变量调试按钮
- **原文**：

> 互动视频调试

- **改写**：互动视频调试

### S-344 条目说明「互动视频调试」

- **位置**：`app/src/main/res/values/strings.xml:694`（`R.string.desc_player_interaction_debug`）
- **出现在哪**：设置 → 高级与实验 → 该开关下方灰色说明
- **控件类型**：说明文字
- **用途**：说明按钮位置与用途
- **原文**：

> 在互动视频播放时，在左侧倍速按钮上方显示调试按钮，可以查看和修改互动视频的变量

- **改写**：在互动视频播放时，在左侧倍速按钮上方显示调试按钮，可以查看和修改互动视频的变量

---

## 九、关于与帮助（设置 → 关于与帮助）

> 一级分组说明见第一节。本页是四个入口卡片 + 一个二级分组入口（26.10.16 起「反馈与统计」收成子页面）。
> 关于页（`AboutActivity`）正文、检查更新页（`UpdateActivity`）、公告列表（`AnnouncementsActivity`）、教程管理（`TutorialManagerActivity`）的**页内文案统一由 01-app-identity.md 收**，本节只收「设置里的入口名与说明」。

### S-345 条目名「关于」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:517`
- **出现在哪**：设置 → 关于与帮助 → 第一张卡片标题
- **控件类型**：跳转子页（点进 `AboutActivity`；**长按有隐藏彩蛋**）
- **用途**：进入关于页
- **原文**：

> 关于

- **改写**：关于

### S-346 条目说明「关于」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:518`
- **出现在哪**：设置 → 关于与帮助 → 关于卡片下方灰色说明
- **控件类型**：说明文字
- **用途**：预告关于页里有什么
- **原文**：

> 版本号、开发团队、联系方式、开源信息

- **改写**：版本号、开发团队、联系方式、开源信息

### S-347 彩蛋标题「回声洞」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:522`
- **出现在哪**：设置 → 关于与帮助 → **长按**「关于」卡片弹出的提示框标题（正文随机取自 `R.array.eggs`）
- **控件类型**：按钮（长按触发）→ 弹窗标题
- **用途**：隐藏彩蛋，连按逐条翻阅
- **原文**：

> 回声洞

- **改写**：回声洞

### S-348 条目名「检查更新」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:527`
- **出现在哪**：设置 → 关于与帮助 → 第二张卡片
- **控件类型**：跳转子页（`UpdateActivity`）
- **用途**：手动检查新版本
- **原文**：

> 检查更新

- **改写**：检查更新

### S-349 条目说明「检查更新」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:527`
- **出现在哪**：设置 → 关于与帮助 → 「检查更新」下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 检查新版本并更新

- **改写**：检查新版本并更新

### S-350 条目名「公告列表」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:530`
- **出现在哪**：设置 → 关于与帮助 → 第三张卡片
- **控件类型**：跳转子页（`AnnouncementsActivity`）
- **用途**：看项目公告
- **原文**：

> 公告列表

- **改写**：公告列表

### S-351 条目说明「公告列表」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:530`
- **出现在哪**：设置 → 关于与帮助 → 「公告列表」下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 查看哔哩终端发布公告

- **改写**：查看哔哩终端发布公告

### S-352 条目名「教程管理」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:533`
- **出现在哪**：设置 → 关于与帮助 → 第四张卡片
- **控件类型**：跳转子页（`TutorialManagerActivity`，页内文案见 03-tutorials.md）
- **用途**：重置/查看各页新手教程进度
- **原文**：

> 教程管理

- **改写**：教程管理

### S-353 条目说明「教程管理」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:533`
- **出现在哪**：设置 → 关于与帮助 → 「教程管理」下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 管理各页面的新手教程进度

- **改写**：管理各页面的新手教程进度

### S-354 条目名「反馈与统计」（二级分组入口）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:542`
- **出现在哪**：设置 → 关于与帮助 → 第五张卡片（点进去是设置 → 关于与帮助 → 反馈与统计）
- **控件类型**：跳转子页（同 `SettingGroupActivity`，`group_type="feedback"`，子页标题也传「反馈与统计」`:547`）
- **用途**：把反馈通道与三个上报开关收成一屏
- **原文**：

> 反馈与统计

- **改写**：反馈与统计

### S-355 条目说明「反馈与统计」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingGroupActivity.kt:543`
- **出现在哪**：设置 → 关于与帮助 → 「反馈与统计」下方灰色说明
- **控件类型**：说明文字
- **用途**：列出里面有哪几项
- **原文**：

> 反馈与建议、我的反馈、匿名统计与崩溃上报开关

- **改写**：反馈与建议、我的反馈、匿名统计与崩溃上报开关

### S-356 条目名「反馈与建议」

- **位置**：`app/src/main/res/values/strings.xml:560`（`R.string.feedback_entry_title`，用于 `SettingGroupActivity.kt:562`）
- **出现在哪**：设置 → 关于与帮助 → 反馈与统计 → 第一项（反馈页正文见 01-app-identity.md）
- **控件类型**：跳转子页（`FeedbackActivity`）
- **用途**：进自建反馈通道
- **原文**：

> 反馈与建议

- **改写**：反馈与建议

### S-357 条目说明「反馈与建议」

- **位置**：`app/src/main/res/values/strings.xml:561`（`R.string.feedback_entry_desc`）
- **出现在哪**：设置 → 关于与帮助 → 反馈与统计 → 该项下方灰色说明
- **控件类型**：说明文字
- **用途**：强调不用登录、直达开发者
- **原文**：

> 直接发送给本项目的开发者（自建通道，无需登录）

- **改写**：直接发送给本项目的开发者（自建通道，无需登录）

### S-358 条目名「我的反馈」

- **位置**：`app/src/main/res/values/strings.xml:592`（`R.string.feedback_mine_entry_title`，用于 `SettingGroupActivity.kt:572`）
- **出现在哪**：设置 → 关于与帮助 → 反馈与统计 → 第二项
- **控件类型**：跳转子页（`FeedbackListActivity`）
- **用途**：回看自己发过的反馈与回复
- **原文**：

> 我的反馈

- **改写**：我的反馈

### S-359 条目说明「我的反馈」

- **位置**：`app/src/main/res/values/strings.xml:593`（`R.string.feedback_mine_entry_desc`）
- **出现在哪**：设置 → 关于与帮助 → 反馈与统计 → 该项下方灰色说明
- **控件类型**：说明文字
- **用途**：一句话说明
- **原文**：

> 查看你提交过的反馈与开发者的回复

- **改写**：查看你提交过的反馈与开发者的回复

### S-360 条目名「匿名使用统计」

- **位置**：`app/src/main/res/values/strings.xml:596`（`R.string.setting_telemetry`，用于 `SettingGroupActivity.kt:578`）
- **出现在哪**：设置 → 关于与帮助 → 反馈与统计 → 第一项开关
- **控件类型**：开关（`TELEMETRY_ENABLE`，默认开；打开时会补记一次隐私同意）
- **用途**：安装量/活跃度统计的授权开关
- **原文**：

> 匿名使用统计

- **改写**：匿名使用统计

### S-361 条目说明「匿名使用统计」

- **位置**：`app/src/main/res/values/strings.xml:597`（`R.string.desc_setting_telemetry`）
- **出现在哪**：设置 → 关于与帮助 → 反馈与统计 → 该开关下方灰色说明
- **控件类型**：说明文字（长句，含"上报频率 + 上报字段 + 不含什么"三层）
- **用途**：把上报内容与边界写清楚（隐私承诺）
- **原文**：

> 每天最多上报一次「随机安装号 + 版本号 + 机型」，用于统计安装量与活跃度。不含账号、Cookie 与任何设备唯一标识。关闭后不再上报。

- **改写**：每天最多上报一次「随机安装号 + 版本号 + 机型」，用于统计安装量与活跃度。不含账号、Cookie 与任何设备唯一标识。关闭后不再上报。

### S-362 条目名「崩溃自动上报」

- **位置**：`app/src/main/res/values/strings.xml:598`（`R.string.setting_crash_auto`，用于 `SettingGroupActivity.kt:589`）
- **出现在哪**：设置 → 关于与帮助 → 反馈与统计 → 第二项开关
- **控件类型**：开关（`CRASH_REPORT_AUTO`，默认开）
- **用途**：崩溃时是否自动传堆栈
- **原文**：

> 崩溃自动上报

- **改写**：崩溃自动上报

### S-363 条目说明「崩溃自动上报」

- **位置**：`app/src/main/res/values/strings.xml:599`（`R.string.desc_setting_crash_auto`）
- **出现在哪**：设置 → 关于与帮助 → 反馈与统计 → 该开关下方灰色说明
- **控件类型**：说明文字（长句，含与崩溃页勾选框的关系）
- **用途**：说清上报什么、关掉后还剩什么
- **原文**：

> 崩溃时自动把堆栈、机型、最近的页面记录与应用日志（已脱敏）发到自建服务器，便于定位问题；崩溃页上的「附带应用日志」勾选框可单独取消。关闭本项后仅保留崩溃页上的手动上传。

- **改写**：崩溃时自动把堆栈、机型、最近的页面记录与应用日志（已脱敏）发到自建服务器，便于定位问题；崩溃页上的「附带应用日志」勾选框可单独取消。关闭本项后仅保留崩溃页上的手动上传。

### S-364 条目名「反馈附带账号 ID」

- **位置**：`app/src/main/res/values/strings.xml:600`（`R.string.setting_feedback_attach_mid`，用于 `SettingGroupActivity.kt:597`）
- **出现在哪**：设置 → 关于与帮助 → 反馈与统计 → 第三项开关
- **控件类型**：开关（`FEEDBACK_ATTACH_MID`，默认关）
- **用途**：上报时是否带 UID
- **原文**：

> 反馈附带账号 ID

- **改写**：反馈附带账号 ID

### S-365 条目说明「反馈附带账号 ID」

- **位置**：`app/src/main/res/values/strings.xml:601`（`R.string.desc_setting_feedback_attach_mid`）
- **出现在哪**：设置 → 关于与帮助 → 反馈与统计 → 该开关下方灰色说明
- **控件类型**：说明文字（讲清了默认值与代价）
- **用途**：让用户自己权衡"好定位"vs"可关联"
- **原文**：

> 默认关闭。打开后反馈与崩溃报告会附上你的 UID，方便开发者定位你的账号相关问题，代价是上报内容能关联到你的账号。

- **改写**：默认关闭。打开后反馈与崩溃报告会附上你的 UID，方便开发者定位你的账号相关问题，代价是上报内容能关联到你的账号。

---

## 十、首次启动初始化设置（首次启动 → 调整界面 → 初次见面）

> 流程：`SetupUIActivity`（调整界面）→ 预览 → 确认 → `IntroductionActivity`（介绍页）→ 进入主界面。
> 两个页面的标题都带省略号，是刻意的悬念式写法。介绍页正文与隐私承诺**同时属于 01-app-identity.md**，此处仍逐字收录并标注。

### S-366 页面标题「初次见面……」

- **位置**：`app/src/main/res/values/strings.xml:256`（`R.string.pagename_setup_intro`，用于 `activity_setup_introduction.xml`）
- **出现在哪**：首次启动 → 介绍页顶栏标题
- **控件类型**：说明文字（页面标题）
- **用途**：给用户一个"第一次见面"的仪式感
- **原文**：

> 初次见面……

- **改写**：初次见面……

### S-367 介绍页正文（长文，含隐私承诺）

- **位置**：`app/src/main/res/values/strings.xml:25`（`R.string.text_setup_introduction`，用于 `activity_setup_introduction.xml:56`）
- **出现在哪**：首次启动 → 介绍页主体（可滚动长文）
- **控件类型**：说明文字（多段长文；**另见 01-app-identity.md**）
- **用途**：欢迎语 + 社群/官网/开源信息 + 三条承诺（免费、可关统计、付费下载警示）
- **原文**：

> 你好呀，同在茫茫网络世界中漂流的旅者！这里是哔哩终端的开发团队。感谢你选择了哔哩终端，每增加一位用户，我们就增加了更多开发的动力！
>
> 愿你在这一方小小的屏幕中，找到属于自己的小小的快乐！
>
> QQ交流群：1129816758
> 唯一官网：https://biliterminal.zsapp.asia/
> 开源地址：https://gitee.com/RobinNotBad/BiliClient
> *以上信息可稍后在关于页面查看
>
> 请注意：
> 本软件永久免费开源，不会收集你的账号密码或 Cookie，请放心使用！
> 软件默认开启一项匿名使用统计（只有随机安装号、版本号、机型，以及一个仅用于设备去重的不可逆哈希短串，不含账号），可在设置里随时关闭。崩溃与反馈时还可自行选择是否附带最近的应用日志（已脱敏）。
> 若你在任何平台付费下载到哔哩终端，那么你被坑了！
>
> 点击下面的按钮，让我们开始吧！

- **改写**：

> 你好呀，同在茫茫网络世界中漂流的旅者！这里是哔哩终端的开发团队。感谢你选择了哔哩终端，每增加一位用户，我们就增加了更多开发的动力！
>
> 愿你在这一方小小的屏幕中，找到属于自己的小小的快乐！
>
> QQ交流群：1129816758
> 唯一官网：https://biliterminal.zsapp.asia/
> 开源地址：https://gitee.com/RobinNotBad/BiliClient
> *以上信息可稍后在关于页面查看
>
> 请注意：
> 本软件永久免费开源，不会收集你的账号密码或 Cookie，请放心使用！
> 软件默认开启一项匿名使用统计（只有随机安装号、版本号、机型，以及一个仅用于设备去重的不可逆哈希短串，不含账号），可在设置里随时关闭。崩溃与反馈时还可自行选择是否附带最近的应用日志（已脱敏）。
> 若你在任何平台付费下载到哔哩终端，那么你被坑了！
>
> 点击下面的按钮，让我们开始吧！

### S-368 按钮「哔哩终端，启动！」

- **位置**：`app/src/main/res/values/strings.xml:209`（`R.string.btn_setup_init`，用于 `activity_setup_introduction.xml:70`）
- **出现在哪**：首次启动 → 介绍页底部唯一按钮
- **控件类型**：按钮（进入主界面）
- **用途**：仪式感收尾 + 明确的下一步
- **原文**：

> 哔哩终端，启动！

- **改写**：哔哩终端，启动！

### S-369 页面标题「调整界面……」

- **位置**：`app/src/main/res/values/strings.xml:257`（`R.string.pagename_setup_ui`，用于 `activity_setup_ui.xml:26`）
- **出现在哪**：首次启动 → 调整界面页顶栏标题
- **控件类型**：说明文字（页面标题）
- **用途**：提示这一步是调界面
- **原文**：

> 调整界面……

- **改写**：调整界面……

### S-370 页面说明「首次使用，非常建议调整下界面哦~」

- **位置**：`app/src/main/res/values/strings.xml:19`（`R.string.desc_setup_ui`，用于 `activity_setup_ui.xml:58`）
- **出现在哪**：首次启动 → 调整界面页顶部灰色说明
- **控件类型**：说明文字（三行）
- **用途**：说明这个页面的用法（调 → 预览 → 满意再确认）
- **原文**：

> 首次使用，非常建议调整下界面哦~
> 调整下面的选项，然后查看预览
> 直到对布局满意为止

- **改写**：

> 首次使用，非常建议调整下界面哦~
> 调整下面的选项，然后查看预览
> 直到对布局满意为止

### S-371 小节标题「圆屏适配」

- **位置**：`app/src/main/res/values/strings.xml:216`（`R.string.section_ui_round`，用于 `activity_setup_ui.xml:86`）
- **出现在哪**：首次启动 → 调整界面 → 第一段标题（同名字段也用于设置 → 界面与外观 → 界面大小页）
- **控件类型**：说明文字（小节标题）
- **用途**：圈住圆屏开关
- **原文**：

> 圆屏适配

- **改写**：圆屏适配

### S-372 开关说明「圆屏适配」

- **位置**：`app/src/main/res/values/strings.xml:23`（`R.string.desc_setting_ui_round`，用于 `activity_setup_ui.xml:96`）
- **出现在哪**：首次启动 → 调整界面 → 圆屏适配开关下方灰色说明（已收于第四节，此处按初始化页再现收录）
- **控件类型**：说明文字（两行）
- **用途**：说明效果与"部分页面可能未适配"
- **原文**：

> 开启选项后，页面将会修改为圆屏布局。
> 由于纯靠代码实现，有部分页面可能未适配，但这是目前最好且工作量最小的办法了。

- **改写**：

> 开启选项后，页面将会修改为圆屏布局。
> 由于纯靠代码实现，有部分页面可能未适配，但这是目前最好且工作量最小的办法了。

### S-373 小节标题「界面大小」

- **位置**：`app/src/main/res/values/strings.xml:176`（`R.string.section_ui_size`，用于 `activity_setup_ui.xml:111`）
- **出现在哪**：首次启动 → 调整界面 → 第二段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住缩放倍数输入框
- **原文**：

> 界面大小

- **改写**：界面大小

### S-374 输入框提示「请输入倍数」

- **位置**：`app/src/main/res/layout/activity_setup_ui.xml:120`（`android:hint` 硬编码）
- **出现在哪**：首次启动 → 调整界面 → 界面大小 → 输入框空态占位文字
- **控件类型**：输入框（数字，默认 1.0）
- **用途**：占位提示，告诉用户该填什么
- **原文**：

> 请输入倍数

- **改写**：请输入倍数

### S-375 输入框说明「有效范围：0.25倍-5.00倍」

- **位置**：`app/src/main/res/values/strings.xml:177`（`R.string.desc_setup_ui_size`，用于 `activity_setup_ui.xml:134`）
- **出现在哪**：首次启动 → 调整界面 → 输入框下方灰色说明
- **控件类型**：说明文字
- **用途**：给范围与"少量多次"的建议
- **原文**：

> 有效范围：0.25倍-5.00倍，建议少量多次调节

- **改写**：有效范围：0.25倍-5.00倍，建议少量多次调节

### S-376 缩放说明「有效范围为0.25~5.00（倍）」

- **位置**：`app/src/main/res/values/strings.xml:21`（`R.string.desc_setting_ui`）
- **出现在哪**：设置 → 界面与外观 → 界面大小 → 缩放比例条目下方（**另见第四节**；初始化页的同类说明是 S-375）
- **控件类型**：说明文字（两行，含强烈警告）
- **用途**：范围 + 调节方法 + "乱调的后果"
- **原文**：

> 有效范围为0.25~5.00（倍），建议每次仅进行轻微调节，调节后请预览页面，确认显示正常后完成修改，打开新页面后将会生效。
> 注意：尽量不要调到两个最值处，否则你可能会后悔！若乱调此选项，导致应用无法正常使用的，请清除应用数据或卸载重装！

- **改写**：

> 有效范围为0.25~5.00（倍），建议每次仅进行轻微调节，调节后请预览页面，确认显示正常后完成修改，打开新页面后将会生效。
> 注意：尽量不要调到两个最值处，否则你可能会后悔！若乱调此选项，导致应用无法正常使用的，请清除应用数据或卸载重装！

### S-377 小节标题「界面边距」

- **位置**：`app/src/main/res/values/strings.xml:217`（`R.string.section_ui_paddings`，用于 `activity_setup_ui.xml:150`）
- **出现在哪**：首次启动 → 调整界面 → 第三段标题
- **控件类型**：说明文字（小节标题）
- **用途**：圈住两个边距输入框
- **原文**：

> 界面边距

- **改写**：界面边距

### S-378 标签「横向」

- **位置**：`app/src/main/res/values/strings.xml:178`（`R.string.text_ui_padding_horizontal`，用于 `activity_setup_ui.xml:165`）
- **出现在哪**：首次启动 → 调整界面 → 界面边距 → 第一个输入框左/上方标签
- **控件类型**：说明文字（输入框标签）
- **用途**：标明这个框是横向边距
- **原文**：

> 横向

- **改写**：横向

### S-379 标签「纵向」

- **位置**：`app/src/main/res/values/strings.xml:179`（`R.string.text_ui_padding_vertical`，用于 `activity_setup_ui.xml:210`）
- **出现在哪**：首次启动 → 调整界面 → 界面边距 → 第二个输入框标签
- **控件类型**：说明文字（输入框标签）
- **用途**：标明这个框是纵向边距
- **原文**：

> 纵向

- **改写**：纵向

### S-380 单位「%」

- **位置**：`app/src/main/res/values/strings.xml:293`（`R.string.percent`，用于 `activity_setup_ui.xml:190`、`:236` 两个输入框右侧）
- **出现在哪**：首次启动 → 调整界面 → 界面边距 → 两个输入框后面的单位
- **控件类型**：说明文字（单位后缀）
- **用途**：提示输入的是百分比
- **原文**：

> %

- **改写**：%

### S-381 边距说明「第一个输入框为横向边距…」

- **位置**：`app/src/main/res/values/strings.xml:20`（`R.string.desc_setup_ui_padding`，用于 `activity_setup_ui.xml:248`）
- **出现在哪**：首次启动 → 调整界面 → 界面边距 → 两个输入框下方灰色说明
- **控件类型**：说明文字（两行）
- **用途**：说明两个框的含义、单位与 30 的上限
- **原文**：

> 第一个输入框为横向边距，第二个输入框为纵向边距，单位百分比。
> 出于保护，数值超过30不会保存。

- **改写**：

> 第一个输入框为横向边距，第二个输入框为纵向边距，单位百分比。
> 出于保护，数值超过30不会保存。

### S-382 按钮「查看预览」

- **位置**：`app/src/main/res/values/strings.xml:620`（`R.string.btn_preview`，用于 `activity_setup_ui.xml:259`）
- **出现在哪**：首次启动 → 调整界面 → 底部左按钮（进 `UIPreviewActivity` 预览效果）
- **控件类型**：按钮（跳转子页）
- **用途**：先看效果再决定
- **原文**：

> 查看预览

- **改写**：查看预览

### S-383 按钮「确认设置」

- **位置**：`app/src/main/res/values/strings.xml:619`（`R.string.btn_confirm_settings`，用于 `activity_setup_ui.xml:266`）
- **出现在哪**：首次启动 → 调整界面 → 底部右按钮（保存并进入介绍页）
- **控件类型**：按钮（保存并跳转）
- **用途**：结束调整、确认落地
- **原文**：

> 确认设置

- **改写**：确认设置

### S-384 按钮「恢复默认」

- **位置**：`app/src/main/res/values/strings.xml:211`（`R.string.btn_reset`，用于 `activity_setup_ui.xml:67`）
- **出现在哪**：首次启动 → 调整界面 → 右上角小按钮（也用于设置 → 界面与外观 的相关页）
- **控件类型**：按钮（一键重置为 1.0 倍 / 0 边距 / 关闭圆屏）
- **用途**：调坏了能一键回去
- **原文**：

> 恢复默认

- **改写**：恢复默认

### S-385 打开圆屏适配后的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/setup/SetupUIActivity.kt:41`
- **出现在哪**：首次启动 → 调整界面 → 打开「圆屏适配」开关时的 Toast（开关会自动把边距填成 5 / 3）
- **控件类型**：Toast（两行）
- **用途**：解释"边距怎么自己变了"，并鼓励微调
- **原文**：

> 界面边距已更改
> 可以手动微调喵

- **改写**：

> 界面边距已更改
> 可以手动微调喵

### S-386 恢复默认后的提示

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/setup/SetupUIActivity.kt:73`
- **出现在哪**：首次启动 → 调整界面 → 点「恢复默认」后的 Toast
- **控件类型**：Toast
- **用途**：确认已复位
- **原文**：

> 恢复完成

- **改写**：恢复完成

---

## 覆盖性自查

### 已扫描

- `app/src/main/res/values/strings.xml` 里全部设置相关字符串：`section_ui_*`、`text_ui_padding_*`、`desc_*`（`:129-175`）、`aria2_*`/`cache_*`（`:268-287`）、`zero`/`tilde`/`lt`/`gt`/`percent`（`:289-293`）等；逐条核对了引用位置。
- `app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/` 下全部设置页（含 `setup/`、`login/` 子目录）——设置页的标题与说明是**用 `SettingSection` DSL 内联写在代码里**的，不在 `strings.xml`，所以必须逐文件读。
- 菜单设置页的 14 个菜单项名（`S-177`~`S-190`，唯一来源 `activity/MenuActivity.kt:46-59` 的 `btnNames`）与我的页面设置页的 11 个入口名（`S-195`~`S-205`，唯一来源 `activity/user/MySpaceMenu.kt:19-31`）——这两批虽是「设置页里的文字」，但**定义在别处**，改写时要去那两个文件改。
- 首次启动的界面初始化设置（第十节 `S-366`~`S-386`）也收在本文件；同一批文案在 01 的首次启动引导条目里另有登记（**宁重复勿遗漏**）。

### 明确排除（归属其它文件）

| 排除对象 | 归属 |
| --- | --- |
| 更新日志正文、检查更新页 | 02 |
| 教程正文与教程管理页 | 03 |
| 设置之外的页面常驻文案（顶栏标题、按钮、空态、列表项标签） | 05 |
| Toast / 弹窗 / 错误翻译 / 通知栏 | 06 |
| 彩蛋「回声洞」 | 07 |
| 关于页正文、开源信息、隐私说明、免责声明、反馈页、捐赠页、特殊登录 | 01 |

### 存疑

1. 设置的**搜索词**（`activity/settings/SettingsIndex.kt` 里给设置搜索框用的关键词）是否算「给用户看的文案」取决于取舍——本文件按「算」收，因为它会作为搜索命中结果展示。若判定不算，可整批删掉。
2. 部分条目的「说明」在界面上**只在特定条件下出现**（如仅调试包、仅某主题、仅某项开启时）。本文件按「出现即收录」处理，未逐条标注生效条件。
