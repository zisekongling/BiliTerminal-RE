<!-- markdownlint-disable MD032 -->
<!-- 本文件逐字照抄用户可见文案（素材而非正文）：行首的 `*`、`+`、`##` 与引用块内的列表都是内容，不是 Markdown 格式，故豁免上面这几条结构规则。 -->

# 03 · 教程系统全部文案

> 收录范围：`tutorial/Tutorials.kt` 注册表 11 篇教程正文 + 教程翻页页控件文案 + `strings.xml` 里所有教程字符串 + 旧链路 12 个 `res/xml/tutorial_*.xml` + 教程管理页全部硬编码文案 + 教程触发点清单
> 编号前缀：`T`
> 生成：2026-10-18 · 基于当前工作区
> 计数：共 53 条（T-001 ~ T-053）
> **改写入口**：每条最后的 `- **改写**：` 里**已经预填了原文**（2026-10-19 起），你直接在原地改成新文案，再把该条标题加上 `〔待落地〕`；怎么配合见 [GUIDE.md](GUIDE.md) 第十一节。
> **状态更新（26.11.07）**：教程系统收尾完成，**旧链路已整体删除**（12 个 `res/xml/tutorial_*.xml`、`TutorialHelper`、`TutorialActivity`、`model/Tutorial` + `CustomText`、`tutorial_*` 数组、旧教程页 Manifest 注册）；5 条滑动提示已进 `Tutorials.hints` 注册表。本文件第四节对旧 XML 的逐字收录因此只是**历史存档**，第四节与旧链路各条不再对应任何现存文件；活文案仍是第一节（`tutorial/Tutorials.kt`）与第五节（管理页）。

---

## 〇、先搞清楚教程文案有**两套**在同时存在

这是本节最重要的一件事：**教程系统正在重做，新旧两套并存，两套都有用户可见文案。**

| | 新链路（活） | 旧链路（待清理） |
| --- | --- | --- |
| 正文存放 | `tutorial/Tutorials.kt`（Kotlin DSL，454 行） | `res/xml/tutorial_*.xml`（12 个文件） |
| 解析 | `TutorialRenderer`（直接读 DSL） | `helper/TutorialHelper.kt`（XMLPullParser 解 XML） |
| 展示 | `tutorial/TutorialPagerActivity.kt`（ViewPager2，一篇内可翻页） | `activity/TutorialActivity.kt`（单页滚动）+ 5 个页面的滑动提示 |
| 触发 | `activity/base/BaseActivity.kt:356-364` 按类名集中匹配 | 各页面手写 `TutorialHelper.showPagerTutorial(...)` / `showTutorialList(...)` |
| 状态键 | `tutorial_ver_<id>`（`TutorialStore.kt:40`） | 同名键，但 tag 用的是数组下标（**错位**，见 `TutorialStore.kt:42-54` 的迁移映射） |
| 管理页 | 未接 | `activity/settings/TutorialManagerActivity.kt` 仍按旧数组渲染 |

**对改写的直接影响**：

1. 新链路是**唯一还在给用户看的正文**，`Tutorials.kt` 的 11 篇（第一节）是主战场。
2. 旧链路只剩「5 个页面的滑动提示」（`tutorial_pager`，T-018）和「教程管理页」（第五节）还在被读取——管理页的列表、状态徽章、按钮都读旧数组与旧键。
3. `res/xml/tutorial_*.xml` **还在 APK 里、还被管理页用来算「版本号」**（`TutorialManagerActivity.kt:301` 拿 `xmlArray.size` 当已读版本写进 SP），所以第四节不是纯死代码，但正文确实没人显示了。
4. **改新教程要在 `Tutorials.kt` 里改**；改完记得 `version` 加 1，否则老用户不会重看。

### 排版约定（`Tutorials.kt:25-32` 文件头注释原文，改教程时必须照此写）

> - **标题**：`bold().color(HIGHLIGHT)`，独占一行；
> - **小标题**：`bold().color(ACCENT)`，后接说明；
> - **操作词**（点击 / 长按 / 点按 / 滑动 / 下拉 / 捏合 / 输入）：一律 `bold().color(ACCENT)`，只加粗在黑色背景上不够醒目，必须带色；
> - **列表项**：以 `"· "` 开头，项之间一个 `newline()`，段落之间两个；
> - **注意事项**：`italic()`；
> - 正文默认白色，一页里强调色不超过两处小标题。

两个颜色常量（`Tutorials.kt:37`、`:40`）：`ACCENT = "#4a8eca"`（主蓝，小标题与所有操作词）、`HIGHLIGHT = "#FF6699"`（B 站粉，标题与关键提示）。
> **注意**：`Tutorials.kt` 是靠一串 `text("...")` 拼接出富文本的，颜色/加粗是**独立的 span 调用**，不在字符串里。所以下面每条「原文」我给的是**渲染后的纯文本**（换行按 `newline()` 数量还原），改文案时请对着 `text()` 逐段替换，不要整段重写成一个 `text()`。

---

## 一、教程注册表 `Tutorials.kt` 的 11 篇正文（T-001 ~ T-011）

> 文件：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt`
> 每篇的 `title` 显示在教程页顶栏（17sp 居中、单行、超长省略），`page { }` 里的正文显示在正文区。
> 「出现在哪」写的是**触发页面**——只要该页面没读过这篇教程，进入时就会自动弹出。

### T-001 推荐页教程（recommend）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt:45-81`
- **出现在哪**：进入推荐页（`RecommendActivity`）时自动全屏弹出；`kind = GUIDE`（强制：翻完页 + 停 3 秒才能点「已阅」，返回键无效）；`R.mipmap.tutorial_recommend` 配图；1 页
- **用途**：新用户上手第一篇，讲「顶栏能点、列表能下拉、视频卡片能长按」。**末尾那段「我们承认这可能有些烦人」是全应用统一态度声明，另外 3 篇（T-003/T-004 及旧 XML）都有同款句子**
- **原文**：

> 【标题】推荐页面-重要教程
>
> 欢迎来到推荐页面
>
> 顶栏：点击可以返回上一页或打开菜单，整个顶栏都能点，不必一直费劲地点左上角。
> 列表：划到顶部可以下拉刷新，同一个视频不会重复出现。
> 视频卡片：点击进入详情页，长按可以直接快速缓存（清晰度按设置里的「快捷缓存方式」决定）。
>
> 你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！我们不希望你成为下一位来问我们“评论区怎么看”这类已有教程的问题的人！

- **改写**：

> 【标题】推荐页面-重要教程
>
> 欢迎来到推荐页面
>
> 顶栏：点击可以返回上一页或打开菜单，整个顶栏都能点，不必一直费劲地点左上角。
> 列表：划到顶部可以下拉刷新，同一个视频不会重复出现。
> 视频卡片：点击进入详情页，长按可以直接快速缓存（清晰度按设置里的「快捷缓存方式」决定）。
>
> 你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！我们不希望你成为下一位来问我们“评论区怎么看”这类已有教程的问题的人！

### T-002 主题说明（themeNotice）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt:84-107`
- **出现在哪**：紧跟在 T-001 之后（同一个推荐页，按 `all` 声明顺序串行展示第二篇）；`kind = NOTICE`（随时可点「已阅」，返回即视为已读）；无配图
- **用途**：解释「默认主题改回经典终端」。**这是 26.09.08 那次主题变更的一次性说明**，内容已经过期——主题早就换过好几轮了，改写时要么更新成当前事实，要么整篇删掉（删了不影响任何功能）
- **原文**：

> 【标题】主题说明
>
> 本次更新说明
>
> 默认主题已经改回经典终端风格，也就是黑色背景 + 粉色点缀的原始外观。
>
> 想换回其它配色
> 进入「设置」→「界面与外观」→「主题配色」，可以自由切换任意主题（哔哩粉、知乎蓝、经典灰等）。
>
> 主题修改后会立即应用到全部页面。

- **改写**：

> 【标题】主题说明
>
> 本次更新说明
>
> 默认主题已经改回经典终端风格，也就是黑色背景 + 粉色点缀的原始外观。
>
> 想换回其它配色
> 进入「设置」→「界面与外观」→「主题配色」，可以自由切换任意主题（哔哩粉、知乎蓝、经典灰等）。
>
> 主题修改后会立即应用到全部页面。

### T-003 视频详情页教程（videoMain，**3 页**）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt:112-185`
- **出现在哪**：进入视频详情页（`VideoInfoActivity`）时自动弹出；`kind = GUIDE`；`R.mipmap.tutorial_video` 配图（挂在最后一页下方）；3 页左右滑动
- **用途**：全应用最长的一篇，讲页面结构 + 新功能 + 注意事项。**第 3 页是从旧教程「视频详情-零碎问题」合并来的**（对应第四节的 T-028），所以它既讲操作也讲风控
- **原文**：

> 【标题】视频详情-主要教程
>
> —— 第 1 页 ——
> 欢迎来到视频详情页
>
> 这个页面可以左右滑动，共三页。
>
> 播放：点击播放按钮播放视频，点击或长按封面查看大图。
> 标签：点击展开，再点标签直接跳转搜索。
>
> —— 第 2 页 ——
> 视频详情页新增了这些功能
>
> · 点击封面播放视频，长按封面查看大图
> · 长按左上角标题栏一次性关闭所有视频详情页，直接回到进入视频前的页面
> · 点击「视频摘要」获取 AI 总结（需登录）
> · 点收藏弹出的选择页会显示每个收藏夹的数量/上限
> · 开启「隐私模式」后以游客身份请求，隐藏点赞/收藏状态（播放仍用登录身份）
>
> —— 第 3 页 ——
> 几点说明
>
> · 点赞或投币报错，说明账号可能被风控了（无永久解决方案）
> · 内置播放器退出播放时会上传视频播放进度
> · 长按「转发」按钮可以复制完整的视频链接
>
> 你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！

- **改写**：

> 【标题】视频详情-主要教程
>
> —— 第 1 页 ——
> 欢迎来到视频详情页
>
> 这个页面可以左右滑动，共三页。
>
> 播放：点击播放按钮播放视频，点击或长按封面查看大图。
> 标签：点击展开，再点标签直接跳转搜索。
>
> —— 第 2 页 ——
> 视频详情页新增了这些功能
>
> · 点击封面播放视频，长按封面查看大图
> · 长按左上角标题栏一次性关闭所有视频详情页，直接回到进入视频前的页面
> · 点击「视频摘要」获取 AI 总结（需登录）
> · 点收藏弹出的选择页会显示每个收藏夹的数量/上限
> · 开启「隐私模式」后以游客身份请求，隐藏点赞/收藏状态（播放仍用登录身份）
>
> —— 第 3 页 ——
> 几点说明
>
> · 点赞或投币报错，说明账号可能被风控了（无永久解决方案）
> · 内置播放器退出播放时会上传视频播放进度
> · 长按「转发」按钮可以复制完整的视频链接
>
> 你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！

### T-004 用户主页教程（space）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt:190-223`
- **出现在哪**：进入用户主页（`UserInfoActivity`）时自动弹出；`kind = GUIDE`；`R.mipmap.tutorial_space` 配图；1 页
- **用途**：介绍四页结构。**末尾风控提示是从旧教程「用户主页-风控问题」合并来的**（第四节 T-030）。注意正文说「共四页」但 `space` 只有 1 页——不是 bug，它指的是**用户主页本身**有 4 页
- **原文**：

> 【标题】用户主页-主要教程
>
> 欢迎来到用户主页
>
> 这里就是个人空间，可以左右滑动，共四页。
>
> 四页内容
> · 第一页：动态（点击文字部分查看详情）
> · 第二、三页：投稿视频、专栏列表
> · 第四页：公开收藏夹（私密收藏夹不会显示）
>
> 已登录时还会显示关注和私信按钮。
>
> 注意：打开主页报错或列表加载不出来，可能是账号被风控了（无永久解决方法）。

- **改写**：

> 【标题】用户主页-主要教程
>
> 欢迎来到用户主页
>
> 这里就是个人空间，可以左右滑动，共四页。
>
> 四页内容
> · 第一页：动态（点击文字部分查看详情）
> · 第二、三页：投稿视频、专栏列表
> · 第四页：公开收藏夹（私密收藏夹不会显示）
>
> 已登录时还会显示关注和私信按钮。
>
> 注意：打开主页报错或列表加载不出来，可能是账号被风控了（无永久解决方法）。

### T-005 搜索教程（search）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt:228-252`
- **出现在哪**：进入搜索页（`SearchActivity`）时自动弹出；`kind = GUIDE`；`R.mipmap.tutorial_search` 配图；1 页
- **用途**：讲四类结果页 + 搜索建议 + 长按删记录
- **原文**：

> 【标题】搜索教程
>
> 搜索结果分为四页
> 视频 → 专栏 → 用户 → 直播，左右滑动切换。
>
> · 输入关键词时会自动显示搜索建议
> · 长按搜索记录可以删除

- **改写**：

> 【标题】搜索教程
>
> 搜索结果分为四页
> 视频 → 专栏 → 用户 → 直播，左右滑动切换。
>
> · 输入关键词时会自动显示搜索建议
> · 长按搜索记录可以删除

### T-006 消息页教程（message）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt:257-274`
- **出现在哪**：进入消息页（`MessageActivity`）时自动弹出；`kind = NOTICE`；无配图；1 页
- **用途**：只有两条。**第二条「部分点赞、评论通知的内容暂时显示不出来，后续版本会修」是已知功能缺口**，改写时要么保留这个交代，要么确认已修复后删掉
- **原文**：

> 【标题】消息页面教程
>
> 欢迎来到消息页面
>
> · 长按私信列表中的卡片可以跳转到用户详情页
> · 部分点赞、评论通知的内容暂时显示不出来，后续版本会修

- **改写**：

> 【标题】消息页面教程
>
> 欢迎来到消息页面
>
> · 长按私信列表中的卡片可以跳转到用户详情页
> · 部分点赞、评论通知的内容暂时显示不出来，后续版本会修

### T-007 动态页教程（dynamic）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt:279-297`
- **出现在哪**：进入动态页（`DynamicActivity`）时自动弹出；`kind = NOTICE`；`R.mipmap.tutorial_dynamic` 配图
- **原文**：

> 【标题】动态页教程
>
> 欢迎来到动态页面
>
> · 点击动态的文字部分可以查看动态详情
> · 部分动态类型暂不支持查看

- **改写**：

> 【标题】动态页教程
>
> 欢迎来到动态页面
>
> · 点击动态的文字部分可以查看动态详情
> · 部分动态类型暂不支持查看

### T-008 动态详情页教程（dynamicInfo）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt:300-321`
- **出现在哪**：进入动态详情页（`DynamicInfoActivity`）时自动弹出；`kind = NOTICE`；`R.mipmap.tutorial_dynamic` 配图（**和 T-007 用同一张图**）
- **用途**：同样说「共两页」但自己只有 1 页（指详情页本身两页）
- **原文**：

> 【标题】动态详情页教程
>
> 欢迎来到动态详情
>
> 这个页面可以左右滑动，共两页。
>
> · 点赞报错说明账号可能被风控了（无永久解决方案）
> · 部分类型的动态暂不支持查看

- **改写**：

> 【标题】动态详情页教程
>
> 欢迎来到动态详情
>
> 这个页面可以左右滑动，共两页。
>
> · 点赞报错说明账号可能被风控了（无永久解决方案）
> · 部分类型的动态暂不支持查看

### T-009 专栏详情页教程（article）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt:326-351`
- **出现在哪**：进入专栏详情页（`OpusInfoActivity`）时自动弹出；`kind = NOTICE`；`R.mipmap.tutorial_article` 配图
- **用途**：**这篇在旧实现里从未被展示过**（`Tutorials.kt:325` 注释：「旧实现里从未被展示，迁移时补上触发」），26.09.11 才恢复
- **原文**：

> 【标题】专栏详情页教程
>
> 欢迎来到专栏详情页
>
> 这个页面可以左右滑动，共两页：专栏正文 + 评论区。
>
> · 正文支持引用、分隔线、图片、卡片等富文本排版
> · 暂不显示专栏的文字颜色，部分专栏的排版可能有问题
> · 点赞或投币报错说明账号可能被风控了（无永久解决方案）

- **改写**：

> 【标题】专栏详情页教程
>
> 欢迎来到专栏详情页
>
> 这个页面可以左右滑动，共两页：专栏正文 + 评论区。
>
> · 正文支持引用、分隔线、图片、卡片等富文本排版
> · 暂不显示专栏的文字颜色，部分专栏的排版可能有问题
> · 点赞或投币报错说明账号可能被风控了（无永久解决方案）

### T-010 短视频操作指南（shortVideo）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt:357-400`
- **出现在哪**：进入短视频播放页（`ShortVideoPlayerActivity`）时自动弹出；`kind = GUIDE`；无配图；1 页
- **用途**：**唯一一篇以「手势」为主体的教程**，七条手势是全屏播放器唯一的学习途径，改写时不要漏条。26.10.21 新增左右滑与「播完自动续接」（`version` 1→2 后），同日又把左右滑改为「滑到右侧看详情/评论/相关推荐、短视频页右滑退出」，`version` 已升到 3；同日又把长按改成「按住 3 倍速播放，松手恢复」，`version` 升到 4
- **原文**：

> 【标题】短视频操作指南
>
> 欢迎来到短视频页面
>
> 手势操作
> · 点按一次：显示/隐藏底部控制栏
> · 点按两次：播放/暂停
> · 长按：按住时 3 倍速播放，松手恢复正常倍速
> · 上下滑动：切换上一个/下一个短视频；一条播完会自动播下一条，到底后接着放相关推荐
> · 左右滑动：滑到右侧查看视频详情、评论和相关推荐；在短视频页右滑退出
> · 双指捏合：缩放画面
> · 点击顶栏：返回菜单

- **改写**：

> 【标题】短视频操作指南
>
> 欢迎来到短视频页面
>
> 手势操作
> · 点按一次：显示/隐藏底部控制栏
> · 点按两次：播放/暂停
> · 长按：按住时 3 倍速播放，松手恢复正常倍速
> · 上下滑动：切换上一个/下一个短视频；一条播完会自动播下一条，到底后接着放相关推荐
> · 左右滑动：滑到右侧查看视频详情、评论和相关推荐；在短视频页右滑退出
> · 双指捏合：缩放画面
> · 点击顶栏：返回菜单

### T-011 缓存与下载（local）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt:398-431`
- **出现在哪**：进入缓存页（`LocalListActivity`）时自动弹出；`kind = NOTICE`；无配图；1 页
- **用途**：全应用最长的一篇列表式教程（6 条），讲两级目录 + 操作面板 + 自动合并。**「向左滑视频卡片打开操作面板」是最难发现的功能**，这条必须留
- **原文**：

> 【标题】缓存与下载
>
> 欢迎来到缓存页面
>
> · 分两级：未分类视频和自建文件夹，点文件夹进入，点顶栏退回上一级
> · 下拉可以重新扫描已下载的视频
> · 点击视频播放；在文件夹里点一个视频，整个文件夹会当成合集连续播放
> · 向左滑动视频卡片打开操作面板：更新弹幕、切换清晰度、移动文件夹、查看详情
> · 下载会自动合并音视频、自动下载封面/字幕/弹幕，同一个视频不会重复入队
> · 正在下载的任务在「下载列表」里查看进度

- **改写**：

> 【标题】缓存与下载
>
> 欢迎来到缓存页面
>
> · 分两级：未分类视频和自建文件夹，点文件夹进入，点顶栏退回上一级
> · 下拉可以重新扫描已下载的视频
> · 点击视频播放；在文件夹里点一个视频，整个文件夹会当成合集连续播放
> · 向左滑动视频卡片打开操作面板：更新弹幕、切换清晰度、移动文件夹、查看详情
> · 下载会自动合并音视频、自动下载封面/字幕/弹幕，同一个视频不会重复入队
> · 正在下载的任务在「下载列表」里查看进度

---

## 二、教程翻页页控件文案（T-012 ~ T-018）

> 页面：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/TutorialPagerActivity.kt` + `app/src/main/res/layout/activity_tutorial_pager.xml`
> 这个页面是**下半屏被按钮占满**的手表布局：顶栏标题 + 页码，中间 ViewPager2，底部一个大按钮。

### T-012 「已阅」按钮

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/TutorialPagerActivity.kt:200` 与 `:210`（`getString(R.string.btn_read)`）；布局默认值 `app/src/main/res/layout/activity_tutorial_pager.xml:55`（`@string/btn_read`）；字符串定义 `app/src/main/res/values/strings.xml:212`
- **出现在哪**：教程页底部整宽按钮（MaterialButton），两种形态共用同一个词
- **用途**：教程页唯一的确认动作。GUIDE 类要满足「翻到最后一页 + 停满 3 秒」才 `isEnabled = true`；NOTICE 类一直可点
- **原文**：

> 已阅

- **改写**：已阅

### T-013 「向左滑动以完成教程」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/TutorialPagerActivity.kt:212`（**硬编码**）
- **出现在哪**：GUIDE 类教程还没翻到最后一页时，**取代**「已阅」显示在同一个按钮上（按钮同时是禁用态）
- **用途**：用按钮本身当引导，告诉用户往哪滑。**注意它顶掉了「已阅」，用户此时看不到确认动作**——这是刻意设计（`TutorialPagerActivity.kt:211` 注释：水平分页，向左滑 = 下一页）
- **原文**：

> 向左滑动以完成教程

- **改写**：向左滑动以完成教程

### T-014 「已阅(%ds)」倒计时

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/TutorialPagerActivity.kt:213`（`String.format(Locale.getDefault(), "已阅(%ds)", max(0, WAIT_SECONDS - elapsedSeconds))`，**硬编码**）
- **出现在哪**：GUIDE 类教程已经翻到最后一页、但 3 秒没停满时，按钮上显示倒数
- **用途**：把「为什么点不动」变成可见的等待。`WAIT_SECONDS = 3`（`:42`）
- **原文**（实际渲染形如）：

> 已阅(2s)

- **改写**：已阅(2s)

### T-015 「（已更新）」标题后缀

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/TutorialPagerActivity.kt:143`（`"${target.title}（已更新）"`，**硬编码**）
- **出现在哪**：教程页顶栏标题的尾部。当 `TutorialStore.isUpdated(target)` 为真（读过旧版本、当前 `version` 更大）时拼上
- **用途**：解释「怎么又弹了一遍」。**注意括号是全角 `（）`**；改写时若标题本身变长，注意顶栏是 `maxLines="1"` + `ellipsize="end"`，会被截断
- **原文**：

> （已更新）

- **改写**：（已更新）

### T-016 页码「当前页/总页数」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/tutorial/TutorialPagerActivity.kt:183-188`（`String.format(Locale.getDefault(), "%d/%d", …)`）
- **出现在哪**：顶栏右侧 12sp 小字（`activity_tutorial_pager.xml:27-37`）。**单页教程直接 `View.GONE`**（`:178-181`，注释：1/1 没意义还占顶栏空间）
- **用途**：纯数字，不需要改写，列出仅为完整性
- **原文**（实际渲染形如）：

> 1/3

- **改写**：1/3

### T-017 旧教程页标题「教程」

- **位置**：定义在 `app/src/main/res/values/strings.xml:260`（`R.string.pagename_tutorial`）；被 `app/src/main/res/layout/activity_tutorial.xml:26` 当作 `text_title` 默认值
- **出现在哪**：旧教程页（`activity/TutorialActivity.kt`）顶部居中标题。**该页面只剩 5 个页面在手写调用 `TutorialHelper.showPagerTutorial` 时不再走它——它的入口已被 `TutorialHelper.showTutorialList` 承担，而那个方法现在没有调用点**（`BaseActivity.kt:353` 注释明确说旧实现靠手写、`tutorial_article` 因此漏掉）
- **用途**：旧链路标题。**判定为死文案**（`showTutorialList` 无调用者），保留在此供你决定删或留
- **原文**：

> 教程

- **改写**：教程

### T-018 页面滑动提示（`tutorial_pager`）

- **位置**：`app/src/main/res/values/strings.xml:618`；调用点 `app/src/main/java/com/RobinNotBad/BiliClient/helper/TutorialHelper.kt:171` 与 `app/src/main/java/com/RobinNotBad/BiliClient/activity/search/SearchActivity.kt:531`（`getString(R.string.tutorial_pager, pagecount)`）
- **出现在哪**：**仍在使用**。5 个多页页面的底部悬浮提示条（`R.id.text_tutorial_pager`）：搜索页传 `categoryList.size - 1`、视频详情页传固定 3、用户主页传 4、专栏详情页与动态详情页传 2（各页面手写的常量）
- **用途**：教用户左右翻页。**滑一下就不再显示**（显示后立刻把 `tutorial_pager_<类名>` 置 false）；教程全屏页在前台时会被刻意隐藏且不写标记（`TutorialPagerActivity.kt:50-59` 的 `isShowing`）
- **原文**（`\n` 是真实换行，`%d` 是页数占位）：

> 提示
>
> ←这个页面可以左右翻页喵→
>
> （共有%d页，滑动一下不再显示）

- **改写**：

> 提示
>
> ←这个页面可以左右翻页喵→
>
> （共有%d页，滑动一下不再显示）

---

## 三、`strings.xml` 里的教程字符串（T-019 ~ T-025）

### T-019 「一键通过所有教程」

- **位置**：`app/src/main/res/values/strings.xml:262`（`R.string.tutorial_btn_pass_all`）；用在 `app/src/main/res/layout/activity_tutorial_manager.xml:66`
- **出现在哪**：教程管理页最顶部的整宽卡片按钮，文字用 `?attr/colorPrimary`（主题色）
- **用途**：调试/洁癖用户一键清空所有教程弹窗
- **原文**：

> 一键通过所有教程

- **改写**：一键通过所有教程

### T-020 「清除所有教程进度」

- **位置**：`app/src/main/res/values/strings.xml:263`（`R.string.tutorial_btn_clear_all`）；用在 `activity_tutorial_manager.xml:106`
- **出现在哪**：教程管理页**列表下方**的整宽卡片按钮。**文字写死红色 `#e74c3c`**（`activity_tutorial_manager.xml:109`）——这是全工程少数没走 `?attr/colorError` 的地方，本身就该改
- **用途**：把已读状态全部清掉，让教程重新弹
- **原文**：

> 清除所有教程进度

- **改写**：清除所有教程进度

### T-021 「重置所有页面滑动引导」

- **位置**：`app/src/main/res/values/strings.xml:264`（`R.string.tutorial_btn_clear_pager`）；用在 `activity_tutorial_manager.xml:124`
- **出现在哪**：教程管理页最底部的整宽卡片按钮，文字写死橙色 `#e67e22`（`:127`）
- **用途**：只重置 T-018 那 5 个页面的滑动提示（对应 5 个 `tutorial_pager_<类名>` 键）
- **原文**：

> 重置所有页面滑动引导

- **改写**：重置所有页面滑动引导

### T-022 「教程列表」

- **位置**：`app/src/main/res/values/strings.xml:265`（`R.string.tutorial_section_list`）；用在 `activity_tutorial_manager.xml:82`
- **出现在哪**：教程管理页里「一键通过所有教程」与列表之间的**分区标题卡片**，12sp 加粗、左对齐
- **原文**：

> 教程列表

- **改写**：教程列表

### T-023 「教程管理」页标题

- **位置**：字符串定义在 `app/src/main/res/values/strings.xml:261`（`R.string.pagename_tutorial_manager`），是 `activity_tutorial_manager.xml:26` 的布局默认值；**但运行时被硬编码覆盖**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:39`（`setPageName("教程管理")`）
- **出现在哪**：教程管理页顶栏标题（带朝下箭头 `@drawable/arrow_down`，因为该页长按顶栏可换页）
- **用途**：入口在 设置 → 关于与帮助 → 教程管理（`SettingGroupActivity.kt` 的 `buildAboutGroup()` 里有对应 `nav`）
- **原文**：

> 教程管理

- **改写**：教程管理

### T-024 封面点击行为提示·开启态

- **位置**：`app/src/main/res/values/strings.xml:139`（`R.string.tutorial_cover_play_enabled`）
- **出现在哪**：视频详情页点封面触发，`MsgUtil.showDialog("播放视频", …)` 弹窗正文（`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:736`）
- **用途**：首次点击封面时解释「现在点封面会播视频」。**注意标题「播放视频」是硬编码在调用处的**
- **原文**（`\n` 为真实换行）：

> 从现版本开始，默认状态下，点击封面将会播放视频，长按封面查看大图。
> 播放按钮仍可使用，长按播放按钮可切换播放器。
>
> 如果不习惯，你可以在 设置-详情页设置 中关闭“点击封面播放”的开关，换回老版本里点击封面查看大图的操作逻辑。

- **改写**：

> 从现版本开始，默认状态下，点击封面将会播放视频，长按封面查看大图。
> 播放按钮仍可使用，长按播放按钮可切换播放器。
>
> 如果不习惯，你可以在 设置-详情页设置 中关闭“点击封面播放”的开关，换回老版本里点击封面查看大图的操作逻辑。

### T-025 封面点击行为提示·关闭态

- **位置**：`app/src/main/res/values/strings.xml:140`（`R.string.tutorial_cover_play_disabled`）
- **出现在哪**：同上，「开关处于关闭态」时弹的另一半文案（`VideoInfoFragment.kt:738`）
- **原文**（`\n` 为真实换行）：

> 提示：如果你想要点击封面播放视频，请在 设置-详情页设置 中打开“点击封面播放”的开关。
>
> 现版本对于新用户已将此开关默认开启，老用户仍保持原来的偏好设定。

- **改写**：

> 提示：如果你想要点击封面播放视频，请在 设置-详情页设置 中打开“点击封面播放”的开关。
>
> 现版本对于新用户已将此开关默认开启，老用户仍保持原来的偏好设定。

---

## 四、旧链路 XML 教程（T-026 ~ T-037）

> 目录：`app/src/main/res/xml/`。
> **这批文件的结构**：`<tutorial>` 下有 `<name>`（教程名）、`<description>`（管理页用的一句话说明）、`<type>`（0=普通）、`<img>`（配图，可选）、`<content>` 里一串 `<item>`；每个 `<item>` 是**一个富文本片段**——`<type>0</type>` 是文字片段（带 `<text>`/`<style>`/`<color>`），`<type>1</type>` 是一个换行。
> **编辑器里的 `style` 取值**：`none` / `bold` / `italic` / `underline` / `strike`（`TutorialHelper.kt:114-117`）。
> **状态**：正文由 `TutorialHelper.loadTutorial()` 解析，但唯一加载它的 `TutorialActivity` 现在没有调用点（见 T-017）。**仍然活着的是 `<name>`/`<description>` 被管理页读取的路径，以及 `<item>` 数量被当作版本号**（`TutorialManagerActivity.kt:298-302`）。
> **改写策略建议**：这批是「旧版措辞的存档」。如果新链路已经覆盖了同一批教程（T-001~T-011），**最省事的做法是直接删除整批 XML 与 `TutorialHelper`、`TutorialActivity`**，而不是逐条重写。下面给全文是为了让你能对照取舍。

### T-026 推荐页（旧）

- **位置**：`app/src/main/res/xml/tutorial_recommend.xml:1-77`（被 `R.array.tutorial_recommend` 引用，`strings.xml:640-642`）
- **出现在哪**：旧推荐页教程；新链路对应 T-001
- **元数据**：name `推荐页面-重要教程`；description `用于引导用户使用推荐页面和哔哩终端`；type `0`；img `mipmap/tutorial_recommend`
- **原文**（渲染后）：

> 欢迎来到推荐页面
>
> 在整个哔哩终端内，点击页面最上方写着标题的时间的“标题栏”可以返回上一页或打开菜单
>
> （小提示：整个顶栏都能点！不必一直费劲地点左上角哦~）
>
>
> 一些列表划到顶部后可以下拉刷新（如推荐和评论区）
>
>
> 你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！我们不希望你成为下一位来问我们“评论区怎么看”这类已有教程的问题的人！

- **改写**：

> 欢迎来到推荐页面
>
> 在整个哔哩终端内，点击页面最上方写着标题的时间的“标题栏”可以返回上一页或打开菜单
>
> （小提示：整个顶栏都能点！不必一直费劲地点左上角哦~）
>
>
> 一些列表划到顶部后可以下拉刷新（如推荐和评论区）
>
>
> 你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！我们不希望你成为下一位来问我们“评论区怎么看”这类已有教程的问题的人！

### T-027 视频详情·主要教程（旧）

- **位置**：`app/src/main/res/xml/tutorial_video.xml:1-73`（被 `R.array.tutorial_video` 引用，`strings.xml:632-635`）
- **出现在哪**：旧视频详情页教程；新链路对应 T-003 第 1 页
- **元数据**：name `视频详情-主要教程`；description `用于引导用户使用视频详情页`；type `0`；img `mipmap/tutorial_video`
- **原文**（渲染后）：

> 欢迎来到视频详情页，这个页面可以左右滑动，共三页
>
>
> 点击下方播放按钮播放视频，点击封面查看大图（若你首次使用时直接点击了封面，以后点击封面也将会变成播放视频，而长按封面才会查看大图）
>
>
> 点击视频标签可以展开，展开后可点击标签直接跳转到搜索页
>
>
> 你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！我们不希望你成为下一位来问我们“评论区怎么看”这类已有教程的问题的人！

- **改写**：

> 欢迎来到视频详情页，这个页面可以左右滑动，共三页
>
>
> 点击下方播放按钮播放视频，点击封面查看大图（若你首次使用时直接点击了封面，以后点击封面也将会变成播放视频，而长按封面才会查看大图）
>
>
> 点击视频标签可以展开，展开后可点击标签直接跳转到搜索页
>
>
> 你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！我们不希望你成为下一位来问我们“评论区怎么看”这类已有教程的问题的人！

### T-028 视频详情·零碎问题（旧）

- **位置**：`app/src/main/res/xml/tutorial_video_2.xml:1-39`（被 `R.array.tutorial_video` 引用，**排在 T-027 之前**：`<item>xml/tutorial_video_2</item>` 先于 `<item>xml/tutorial_video</item>`）
- **出现在哪**：旧视频详情页的第二篇；**内容已并入 T-003 第 3 页，此文件可删**
- **元数据**：name `视频详情-零碎问题`；description `用于引导用户使用视频详情页`；type `0`；**无 img**
- **原文**（渲染后）：

> 注意：如果点赞或投币时报错，那么说明你的B站账号可能被风控了（该问题无永久解决方案）
>
>
> 另外：内置播放器退出播放时会上传视频播放进度
>
>
> 另外的另外：长按“转发”按钮可以复制完整的视频链接（小声：应该没多少人会用到

- **改写**：

> 注意：如果点赞或投币时报错，那么说明你的B站账号可能被风控了（该问题无永久解决方案）
>
>
> 另外：内置播放器退出播放时会上传视频播放进度
>
>
> 另外的另外：长按“转发”按钮可以复制完整的视频链接（小声：应该没多少人会用到

### T-029 用户主页·主要教程（旧）

- **位置**：`app/src/main/res/xml/tutorial_space.xml:1-106`（被 `R.array.tutorial_space` 引用，`strings.xml:627-630`）
- **出现在哪**：旧用户主页教程；新链路对应 T-004
- **元数据**：name `用户主页-主要教程`；description `用于引导用户使用用户主页`；type `0`；img `mipmap/tutorial_space`
- **原文**（渲染后）：

> 这个页面是用户主页，也就是个人空间
>
> 此页面可以左右滑动，共四页
>
>
> 第一页为动态
>
> 点击动态文字部分可以查看动态详情
>
>
> 第二页、第三页分别为投稿视频、专栏列表（一片空白可能是由于该用户未投稿过，也可能是你未登录）
>
> 第四页为该用户的公开收藏夹（私密收藏夹不会显示，也无需登录即可查看）
>
>
> 若已登录，会显示关注和私信按钮
>
>
> 你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！我们不希望你成为下一位来问我们“评论区怎么看”这类已有教程的问题的人！

- **改写**：

> 这个页面是用户主页，也就是个人空间
>
> 此页面可以左右滑动，共四页
>
>
> 第一页为动态
>
> 点击动态文字部分可以查看动态详情
>
>
> 第二页、第三页分别为投稿视频、专栏列表（一片空白可能是由于该用户未投稿过，也可能是你未登录）
>
> 第四页为该用户的公开收藏夹（私密收藏夹不会显示，也无需登录即可查看）
>
>
> 若已登录，会显示关注和私信按钮
>
>
> 你可能会在后续看到很多这样的教程弹窗，我们承认这可能有些烦人，但这是必要的！我们不希望你成为下一位来问我们“评论区怎么看”这类已有教程的问题的人！

### T-030 用户主页·风控问题（旧）

- **位置**：`app/src/main/res/xml/tutorial_space_2.xml:1-16`（被 `R.array.tutorial_space` 引用，**排在 T-029 之前**）
- **出现在哪**：旧用户主页第二篇；**内容已并入 T-004 末尾，此文件可删**
- **元数据**：name `用户主页-风控问题`；description `用于引导用户使用用户主页`；type `0`；**无 img**
- **原文**（渲染后）：

> 注意：若打开主页时报错或无法加载动态/投稿列表，则可能说明你的B站账号被风控了（无永久解决方法）

- **改写**：注意：若打开主页时报错或无法加载动态/投稿列表，则可能说明你的B站账号被风控了（无永久解决方法）

### T-031 搜索（旧）

- **位置**：`app/src/main/res/xml/tutorial_search.xml:1-27`（被 `R.array.tutorial_search` 引用，`strings.xml:637-639`）
- **出现在哪**：旧搜索页教程；新链路对应 T-005
- **元数据**：name `搜索教程`；description `用于引导用户使用搜索`；type `0`；img `mipmap/tutorial_search`
- **原文**（渲染后）：

> 搜索结果第一页为视频列表，第二页为专栏列表，第三页为用户列表，第四页为直播列表
>
>
> 长按可以删除搜索记录

- **改写**：

> 搜索结果第一页为视频列表，第二页为专栏列表，第三页为用户列表，第四页为直播列表
>
>
> 长按可以删除搜索记录

### T-032 消息页（旧）

- **位置**：`app/src/main/res/xml/tutorial_message.xml:1-38`（被 `R.array.tutorial_message` 引用，`strings.xml:644-646`）
- **出现在哪**：旧消息页教程；新链路对应 T-006
- **元数据**：name `消息页面教程`；description `用于引导用户使用消息页面`；type `0`；**无 img**
- **原文**（渲染后）：

> 欢迎来到消息页面
>
>
> 这里只有一个要介绍的：长按私信列表中的卡片可以跳转到用户详情页
>
>
> 另外：终端可能无法显示某些点赞、评论通知的内容，后续版本大抵应该会修

- **改写**：

> 欢迎来到消息页面
>
>
> 这里只有一个要介绍的：长按私信列表中的卡片可以跳转到用户详情页
>
>
> 另外：终端可能无法显示某些点赞、评论通知的内容，后续版本大抵应该会修

### T-033 动态页（旧）

- **位置**：`app/src/main/res/xml/tutorial_dynamic.xml:1-39`（被 `R.array.tutorial_dynamic` 引用，`strings.xml:652-654`）
- **出现在哪**：旧动态页教程；新链路对应 T-007
- **元数据**：name `动态页教程`；description `用于引导用户使用动态`；type `0`；img `mipmap/tutorial_dynamic`
- **原文**（渲染后）：

> 欢迎来到动态页面
>
>
> 点击动态的文字部分可以查看动态详情
>
>
> 哔哩终端暂不支持查看部分动态类型

- **改写**：

> 欢迎来到动态页面
>
>
> 点击动态的文字部分可以查看动态详情
>
>
> 哔哩终端暂不支持查看部分动态类型

### T-034 动态详情页（旧）

- **位置**：`app/src/main/res/xml/tutorial_dynamic_info.xml:1-51`（被 `R.array.tutorial_dynamic_info` 引用，`strings.xml:656-658`）
- **出现在哪**：旧动态详情页教程；新链路对应 T-008
- **元数据**：name `动态详情页教程`；description `用于引导用户查看动态`；type `0`；img `mipmap/tutorial_dynamic`
- **原文**（渲染后）：

> 欢迎来到动态详情，这个页面可以左右滑动，共两页
>
>
> 注意：如果点赞时报错，那么说明你的B站账号可能被风控了（该问题无永久解决方案）
>
>
> 哔哩终端暂不支持查看部分类型的动态

- **改写**：

> 欢迎来到动态详情，这个页面可以左右滑动，共两页
>
>
> 注意：如果点赞时报错，那么说明你的B站账号可能被风控了（该问题无永久解决方案）
>
>
> 哔哩终端暂不支持查看部分类型的动态

### T-035 专栏详情页（旧）

- **位置**：`app/src/main/res/xml/tutorial_article.xml:1-64`（被 `R.array.tutorial_article` 引用，`strings.xml:648-650`）
- **出现在哪**：旧专栏详情页教程；新链路对应 T-009。**这份 XML 在旧实现里从未被任何页面加载过**（`Tutorials.kt:325` 注释、`TutorialStore.kt:61` 注释都点名了它）
- **元数据**：name `专栏详情页教程`；description `用于引导用户使用专栏详情页`；type `0`；img `mipmap/tutorial_article`
- **原文**（渲染后）：

> 欢迎来到专栏详情页，这个页面可以左右滑动，共两页
>
>
> 第一页为专栏详情和专栏内容，第二页为评论区
>
>
> 注意：如果点赞或投币时报错，那么说明你的B站账号可能被风控了（该问题无永久解决方案）
>
>
> 哔哩终端暂不显示专栏的文本颜色，部分专栏的排版也有可能出现问题

- **改写**：

> 欢迎来到专栏详情页，这个页面可以左右滑动，共两页
>
>
> 第一页为专栏详情和专栏内容，第二页为评论区
>
>
> 注意：如果点赞或投币时报错，那么说明你的B站账号可能被风控了（该问题无永久解决方案）
>
>
> 哔哩终端暂不显示专栏的文本颜色，部分专栏的排版也有可能出现问题

### T-036 短视频操作指南（旧）

- **位置**：`app/src/main/res/xml/tutorial_short_video.xml:1-122`（被 `R.array.tutorial_short_video` 引用，`strings.xml:660-662`）
- **出现在哪**：旧短视频教程；新链路对应 T-010。旧链路已不展示，此文件只作迁移存证，26.10.21 同步补了左右滑与自动续接两条（左右滑文案随第三轮改为「滑到右侧看详情、评论和相关推荐」），同日长按条目也改成「按住 3 倍速播放」
- **元数据**：name `短视频操作指南`；description `用于引导用户使用短视频播放功能`；type `0`；**无 img**
- **原文**（渲染后）：

> 欢迎来到短视频页面
>
>
> 点按一次：隐藏或显示底栏控制按钮
> 点按两次：播放或暂停视频
> 长按：按住时 3 倍速播放，松手恢复正常倍速
> 点击顶栏：返回菜单页面
>
>
> 上下滑动可切换下一个/上一个短视频
>
> 左右滑动：滑到右侧查看视频详情、评论和相关推荐；在短视频页右滑退出
>
> 一条播完会自动切到下一条；刷到底时用相关推荐续接
>
> 双指捏合可缩放视频画面

- **改写**：

> 欢迎来到短视频页面
>
>
> 点按一次：隐藏或显示底栏控制按钮
> 点按两次：播放或暂停视频
> 长按：按住时 3 倍速播放，松手恢复正常倍速
> 点击顶栏：返回菜单页面
>
>
> 上下滑动可切换下一个/上一个短视频
>
> 左右滑动：滑到右侧查看视频详情、评论和相关推荐；在短视频页右滑退出
>
> 一条播完会自动切到下一条；刷到底时用相关推荐续接
>
> 双指捏合可缩放视频画面

### T-037 主题说明（旧）

- **位置**：`app/src/main/res/xml/tutorial_theme_notice.xml:1-74`
- **出现在哪**：**孤儿文件**——不在 `res/values/strings.xml` 的任何 `R.array.tutorial_*` 里（我逐个核对过 9 个数组，只有它是没人引用的）。新链路对应 T-002。可删
- **元数据**：name `主题说明`；description `哔哩终端的默认主题已改回经典终端`；type `0`；**无 img**
- **原文**（渲染后）：

> 本次更新说明
>
> 这个版本的默认主题已经改回了经典终端风格，也就是黑色背景 + 粉色点缀的原始外观。
>
>
> 如果你不喜欢这个主题，想换回其它配色：
>
> 请进入「设置」→「界面与外观」→「主题配色」，在那里可以自由切换任意主题（包括哔哩粉、知乎蓝、经典灰等）。
>
>
> 主题修改后会立即应用到全部页面。

- **改写**：

> 本次更新说明
>
> 这个版本的默认主题已经改回了经典终端风格，也就是黑色背景 + 粉色点缀的原始外观。
>
>
> 如果你不喜欢这个主题，想换回其它配色：
>
> 请进入「设置」→「界面与外观」→「主题配色」，在那里可以自由切换任意主题（包括哔哩粉、知乎蓝、经典灰等）。
>
>
> 主题修改后会立即应用到全部页面。

> **附：数组引用关系**（`app/src/main/res/values/strings.xml:627-674`，`#` 开头那几行是 XML 注释）
> - `tutorial_space` → [tutorial_space_2, tutorial_space]
> - `tutorial_video` → [tutorial_video_2, tutorial_video]
> - `tutorial_search` → [tutorial_search]
> - `tutorial_recommend` → [tutorial_recommend]
> - `tutorial_message` → [tutorial_message]
> - `tutorial_article` → [tutorial_article]
> - `tutorial_dynamic` → [tutorial_dynamic]
> - `tutorial_dynamic_info` → [tutorial_dynamic_info]
> - `tutorial_short_video` → [tutorial_short_video]
> - `tutorial_list` → [recommend, video, space, search, message, dynamic, dynamic_info, article, short_video]（**9 个 tag，被管理页当行键**）
> - 数组上方注释：`#后来写新功能添加的教程请放在列表的第一个`
> - **`tutorial_theme_notice.xml` 不在任何数组里**（见 T-037）

---

## 五、教程管理页全部文案（T-038 ~ T-053）

> 页面：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt`（371 行，**文案全部硬编码在代码里**，不走 `strings.xml`）+ `app/src/main/res/layout/activity_tutorial_manager.xml`
> 入口：设置 → 关于与帮助 → 教程管理
> **这个页面是给「不想再看到教程弹窗」的用户用的**，所以它的文案会跟第一节的教程正文一起被用户看到

### T-038 页面标题（`setPageName` 硬编码）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:39`
- **出现在哪**：教程管理页顶栏。**与 T-023 的 `R.string.pagename_tutorial_manager` 内容相同但来源不同**，改一处不会同步另一处
- **原文**：

> 教程管理

- **改写**：教程管理

### T-039 九个教程的显示名（`names` 数组）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:67-77`
- **出现在哪**：管理页每张卡片第一行 13sp 白色（`createTutorialCard` 的 `titleText`，`:176-179`）
- **用途**：**这 9 个名字与教程 XML 里的 `<name>` 是两套**——管理页优先用这个数组覆盖 `<name>`（`:105`）。顺序对应 `tutorial_list` 的 9 个 tag
- **原文**：

> 推荐页面教程
> 视频详情教程
> 用户主页教程
> 搜索教程
> 消息页面教程
> 动态页教程
> 动态详情页教程
> 专栏详情页教程
> 短视频教程

- **改写**：

> 推荐页面教程
> 视频详情教程
> 用户主页教程
> 搜索教程
> 消息页面教程
> 动态页教程
> 动态详情页教程
> 专栏详情页教程
> 短视频教程

### T-040 九个教程的说明（`descs` 数组）

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:78-88`
- **出现在哪**：管理页每张卡片第二行 11sp 灰色 `#aaaaaa`（`:208-218`）
- **用途**：一句话讲这篇教程教什么。**措辞高度模板化（「引导用户使用…」×9）**，是改写收益最明显的一组
- **原文**：

> 引导用户使用推荐页面和哔哩终端
> 引导用户使用视频详情页
> 引导用户使用用户主页
> 引导用户使用搜索功能
> 引导用户使用消息页面
> 引导用户使用动态功能
> 引导用户查看动态详情
> 引导用户使用专栏详情页
> 引导用户使用短视频播放功能

- **改写**：

> 引导用户使用推荐页面和哔哩终端
> 引导用户使用视频详情页
> 引导用户使用用户主页
> 引导用户使用搜索功能
> 引导用户使用消息页面
> 引导用户使用动态功能
> 引导用户查看动态详情
> 引导用户使用专栏详情页
> 引导用户使用短视频播放功能

### T-041 状态徽章「已完成」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:194`
- **出现在哪**：卡片第一行右侧的小圆角徽章，11sp 白字，底色写死绿色 `#27ae60`（`:198`）
- **原文**：

> 已完成

- **改写**：已完成

### T-042 状态徽章「未完成」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:200`
- **出现在哪**：同上，底色写死橙色 `#e67e22`（`:202`）
- **用途**：**「未完成」这个词有点刺眼**——它其实只表示「这次升级后还没弹给你看」，不是用户做错了什么。改写建议往中性方向走
- **原文**：

> 未完成

- **改写**：未完成

### T-043 历史记录行「历史记录：已完成过（版本 N）」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:232`（`"历史记录：已完成过（版本 ${getTutorialVersion(item.tag)}）"`）
- **出现在哪**：卡片第三行 10sp 灰色 `#888888`（`:233`）。**只有「当前未完成但以前完成过」时才显示**（`:231`，即教程 `version` 变大后）
- **用途**：解释「怎么又弹了」。`${…}` 是**旧系统写进去的数组长度**（`:301` 用的是 `xmlArray.size`），不是真实版本号——所以它显示的数字对用户没有意义，改写时可以考虑去掉数字
- **原文**（实际渲染形如）：

> 历史记录：已完成过（版本 2）

- **改写**：历史记录：已完成过（版本 2）

### T-044 按钮「通过教程」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:255`
- **出现在哪**：每张卡片底部左侧按钮（11sp、高 32dp、占一半宽）
- **用途**：单篇标记为已读完
- **原文**：

> 通过教程

- **改写**：通过教程

### T-045 按钮「清除进度」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:271`
- **出现在哪**：每张卡片底部右侧按钮
- **用途**：单篇恢复未读，让教程重新弹。**注意它和「通过教程」并排、颜色完全相同，只靠文字区分语义相反的两个动作**，是个可改的点
- **原文**：

> 清除进度

- **改写**：清除进度

### T-046 操作反馈「「X」已通过」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:304`（`MsgUtil.showMsg("「${item.name}」已通过")`）
- **出现在哪**：点「通过教程」后的底部 Snackbar。`X` 是 T-039 里的名字，**外面套的是全角书名号「」**
- **原文**：

> 「推荐页面教程」已通过

- **改写**：「推荐页面教程」已通过

### T-047 操作反馈「「X」的进度已清除」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:317`
- **出现在哪**：点「清除进度」后的底部 Snackbar
- **原文**：

> 「推荐页面教程」的进度已清除

- **改写**：「推荐页面教程」的进度已清除

### T-048 一键通过的反馈「所有教程已标记为完成」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:335`
- **出现在哪**：点顶部「一键通过所有教程」后的底部 Snackbar
- **用途**：**「标记为完成」这个说法比「已通过」更准确**（它确实只是标记），可作统一措辞的参考
- **原文**：

> 所有教程已标记为完成

- **改写**：所有教程已标记为完成

### T-049 清除全部的反馈「所有教程进度已清除」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:350`
- **出现在哪**：点「清除所有教程进度」后的底部 Snackbar
- **原文**：

> 所有教程进度已清除

- **改写**：所有教程进度已清除

### T-050 重置滑动的反馈「页面引导已重置」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:364`
- **出现在哪**：点「重置所有页面滑动引导」后的底部 Snackbar
- **用途**：**这条和按钮上的「滑动引导」（T-021）用词不一致**（这里叫「页面引导」），改写时统一一下
- **原文**：

> 页面引导已重置

- **改写**：页面引导已重置

### T-051 五个「页面滑动引导」项的名称与说明

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:113-117`（`tutorialItems.add(TutorialItem(标签, 名称, 说明, 0, true))`）
- **出现在哪**：管理页列表**最下方 5 张卡片**（排在 9 篇教程之后）。它们是 T-018 那个悬浮提示的开关项，不是全屏教程
- **用途**：名称与说明成对写死，`tag` 是 Activity 类名（决定读哪个 `tutorial_pager_<类名>` 键）
- **原文**：

> 搜索页面引导 / 搜索页面的左右翻页提示
> 视频详情页引导 / 视频详情页的左右翻页提示
> 用户主页引导 / 用户主页的左右翻页提示
> 专栏详情页引导 / 专栏详情页的左右翻页提示
> 动态详情页引导 / 动态详情页的左右翻页提示

- **改写**：

> 搜索页面引导 / 搜索页面的左右翻页提示
> 视频详情页引导 / 视频详情页的左右翻页提示
> 用户主页引导 / 用户主页的左右翻页提示
> 专栏详情页引导 / 专栏详情页的左右翻页提示
> 动态详情页引导 / 动态详情页的左右翻页提示

### T-052 旧链路提示「教程已更新」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/helper/TutorialHelper.kt:128`（`Toast.makeText(context.applicationContext, "教程已更新", Toast.LENGTH_SHORT)`）
- **出现在哪**：旧链路在教程版本号变大、准备重新弹教程时弹的 Toast。**只在走 `TutorialHelper.showTutorialList` 时触发，而该方法已无调用点**
- **用途**：与 T-015 的「（已更新）」表达同一件事，措辞与载体都不同
- **原文**：

> 教程已更新

- **改写**：教程已更新

### T-053 旧链路错误「加载教程时遇到问题」

- **位置**：`app/src/main/java/com/RobinNotBad/BiliClient/helper/TutorialHelper.kt:147`（`MsgUtil.showMsg("加载教程时遇到问题")`）
- **出现在哪**：旧链路解析 XML 失败时的底部 Snackbar
- **原文**：

> 加载教程时遇到问题

- **改写**：加载教程时遇到问题

---

## 六、教程触发点清单（改教程前必须知道的事）

| 触发方式 | 位置 | 说明 |
| --- | --- | --- |
| 全屏教程自动弹出 | `app/src/main/java/com/RobinNotBad/BiliClient/activity/base/BaseActivity.kt:356-364` | `onStart`/`onResume` 路径里调 `autoTriggerTutorial()`：每个 Activity 实例只查一次，按 `Tutorials.forPage(javaClass)` 取该页全部教程，过滤掉 `TutorialStore.isRead` 的，剩下的一次性交给 `TutorialPagerActivity` **串行**展示。教程页自己覆写 `tutorialAutoTriggerEnabled()` 返回 false 防递归（`TutorialPagerActivity.kt:270`） |
| 已读判定 | `app/src/main/java/com/RobinNotBad/BiliClient/tutorial/TutorialStore.kt:14` | `readVersion(id) >= tutorial.version`；**改写正文不改 `version` 的话老用户看不到新版** |
| 旧键迁移 | `app/src/main/java/com/RobinNotBad/BiliClient/tutorial/TutorialStore.kt:64-72` | 启动时由 `BiliTerminal.kt:329` 调一次，把旧 tag 的已读版本搬给新 id（旧实现拿数组下标当 tag，**错位**：搜索写进了 message 的键） |
| 页面滑动提示 | `TutorialHelper.showPagerTutorial()`，5 个调用点 | 搜索页 `SearchActivity.kt:531`（`categoryList.size - 1`）、视频详情 `VideoInfoActivity.kt:84`（3）、用户主页 `UserInfoActivity.kt:65`（4）、专栏详情 `OpusInfoActivity.kt:77`（2）、动态详情 `DynamicInfoActivity.kt:75`（2） |
| 教程管理页 | 设置 → 关于与帮助 → 教程管理 | `SettingGroupActivity.kt` 的 `buildAboutGroup()` 里有对应 `nav` |

**同页多篇的串行顺序**由 `Tutorials.kt:434-446` 的 `all` 列表决定（声明顺序即顺序）。当前同页两篇的只有推荐页（T-001 → T-002）。

---

## 覆盖性自查

- **已扫描**：
  - `app/src/main/java/com/RobinNotBad/BiliClient/tutorial/Tutorials.kt`（**全文 454 行**）、`tutorial/TutorialPagerActivity.kt`（**全文 315 行**）、`tutorial/TutorialStore.kt`（**全文 73 行**）。
  - `app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt`（**全文 371 行**）。
  - `app/src/main/java/com/RobinNotBad/BiliClient/helper/TutorialHelper.kt`（grep 全部字符串字面量 + 精读 `:100-190`）。
  - `app/src/main/java/com/RobinNotBad/BiliClient/activity/TutorialActivity.kt`（grep + 调用点核实）。
  - `app/src/main/java/com/RobinNotBad/BiliClient/activity/base/BaseActivity.kt:340-380`（触发逻辑）。
  - `app/src/main/res/xml/tutorial_*.xml`（**12 个文件逐字读完**：recommend / video / video_2 / space / space_2 / search / message / dynamic / dynamic_info / article / short_video / theme_notice）。
  - `app/src/main/res/values/strings.xml`：grep `tutorial` 全部 31 处命中 + 精读 `:139-140`、`:212`、`:225-266`、`:615-674`。
  - 布局：`res/layout/activity_tutorial_manager.xml`（全文 133 行）、`activity_tutorial_pager.xml`（全文 59 行）、`activity_tutorial.xml`（全文 56 行）。
- **明确排除 / 交给别人的**：
  - 教程里提到的功能入口本身的文案（「主题配色」「快捷缓存方式」「视频摘要」「隐私模式」等设置项名） → `04-settings.md`。
  - `MsgUtil.showDialog("播放视频", …)` 的**标题**「播放视频」是硬编码在 `VideoInfoFragment.kt:736`/`:738` 的调用处，和 T-024/T-025 配套，**未单列条目**——如果你要改它，记得改调用处而非字符串。
  - `activity/TutorialActivity.kt` 页面内的 `text_default_subtitle` 占位 → `05-ui-pages.md`。
  - 教程管理页 `createTutorialCard` 里的颜色常量（`#ffffff` / `#27ae60` / `#e67e22` / `#aaaaaa` / `#888888` / `#ffffff`）不是文案，未收录。
- **存疑**：
  1. **T-017、T-052、T-053 确认是旧链路的死文案**（`TutorialHelper.showTutorialList` 无调用者），但 `TutorialHelper.showPagerTutorial` 还活着，所以 `TutorialHelper.kt` **不能整个删**——要删得先确认 5 个调用点怎么处理。
  2. **T-002 主题说明已经过期**（讲的是 26.09.08 那次默认主题变更），但它仍在推荐页自动弹出。是「改成当前事实」还是「整篇删除」需要你决定；删的话要同步删 `Tutorials.kt:84-107` 与 `all` 列表里的 `themeNotice`。
  3. **T-037 `tutorial_theme_notice.xml` 是孤儿**（不在任何数组里），我逐个核对了 9 个 `R.array.tutorial_*`。
  4. **教程级配图与页内配图的规则**：`TutorialPagerActivity.kt:303-304` —— 页内写了 `image()` 就用页内的；否则**只有最后一页**回退到教程级 `imageRes`。所以 T-003 的配图出现在第 3 页末尾，不是第 1 页。改写时如果增删页数，配图位置会跟着变。
  5. 教程正文里多处「共三页 / 共四页 / 共两页」指的是**承载页面的分页数**，不是教程自己的页数。T-004 和 T-008 尤其容易误读（它们自己都只有 1 页教程内容）。已在各条注明。
