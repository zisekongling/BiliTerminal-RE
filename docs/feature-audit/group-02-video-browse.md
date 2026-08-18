# 视频浏览与详情 源码功能审查（极细颗粒度）

> 审查范围：`activity/video/` 及 `activity/video/info/` 下的 11 个文件。
> 审查方式：逐行读取全文，按"功能-用法"颗粒度列条。
> 审查日期：按项目版本 26.08.14。

---

## RecommendActivity.kt（推荐页）

继承 `RefreshMainActivity`（自带下拉刷新 + 上拉加载）。

- **功能：设置菜单点击** —— 调用 `setMenuClick()`（继承自基类，用于顶部菜单/侧滑菜单入口）。
- **功能：下拉刷新** —— `setOnRefreshListener { refreshRecommend() }`；用户下拉手势触发。
- **功能：上拉加载更多** —— `setOnLoadMoreListener { addRecommend() }`；滚动到底部自动触发。
- **功能：页面标题** —— `setPageName("推荐")`，顶栏显示"推荐"。
- **功能：列表固定高度** —— `recyclerView.setHasFixedSize(true)`，优化滚动性能。
- **功能：新手引导（Tutorial）** —— `TutorialHelper.showTutorialList(this, R.array.tutorial_recommend, 0)`，首次进入展示推荐页引导气泡。
- **功能：刷新推荐（重置）** —— 下拉刷新触发 `refreshRecommend()`：将 `freshType` 重置为 3、清空已加载 bvid 集合 `loadedBvids`；首次刷新创建新列表，非首次则清除旧列表并通知删除所有条目，随后调用 `addRecommend()`。
- **功能：加载下一页（交替请求参数）** —— `addRecommend()`：记录当前 `freshType` 后交替切换（3↔4），在线程池中调用 `RecommendApi.getRecommend(list, requestFreshType)` 取数。
- **功能：去重过滤** —— 新返回列表用 `loadedBvids.add(bvid)` 过滤已出现过的视频，避免重复卡片。
- **功能：首次/增量刷新界面** —— 首次刷新创建 `VideoCardAdapter` 并 `setAdapter`；非首次用 `notifyItemRangeInserted` 增量插入新条目。
- **功能：加载失败处理** —— 捕获异常调用 `loadFail(e)`（基类行为：提示失败并停止刷新动画）。
- **功能：空结果保护** —— 新列表去重后为空则仅停止刷新动画，不更新 UI。
- **点击行为**：列表项点击/长按等交互由 `VideoCardAdapter` 决定（本页未覆写，见适配器层）。

---

## PopularActivity.kt（热门页）

继承 `InstanceActivity`，使用 `R.layout.activity_simple_main_refresh`。

- **功能：设置菜单点击** —— `setMenuClick()`。
- **功能：滚动时图片延迟加载** —— `ImageAutoLoadScrollListener.install(recyclerView)`，滚动停止才加载可见图片。
- **功能：下拉刷新** —— `swipeRefreshLayout.setOnRefreshListener { loadPopular() }`。
- **功能：页面标题** —— 顶部 `pageName` 设为"热门"。
- **功能：刷新（重置到第 1 页）** —— `loadPopular()`：页码 `page` 置 1；首次创建 `CustomLinearManager` 布局与列表，非首次清空旧列表并通知删除；置刷新动画为 true；在线程池执行 `addPopular()`。
- **功能：加载下一页（翻页）** —— `addPopular()`：`RecommendApi.getPopular(list, page)` 取数后 `page++`；回主线程追加列表、停止刷新动画。
- **功能：上拉加载更多（滚动监听）** —— 首次加载完成后给 recyclerView 添加滚动监听：当最后完全可见项 `>= itemCount - 3` 且向下滚动（`dy>0`）且未在加载中时，触发下一页加载。
- **功能：列表适配器** —— 使用 `VideoCardAdapter` 展示视频卡片。
- **功能：加载失败提示** —— 捕获异常后 `MsgUtil.err(e)` 弹窗提示。

---

## PreciousActivity.kt（入站必刷页）

继承 `InstanceActivity`，逻辑与 PopularActivity 几乎一致，数据源为 `RecommendApi.getPrecious`。

- **功能：设置菜单点击** —— `setMenuClick()`。
- **功能：滚动时图片延迟加载** —— `ImageAutoLoadScrollListener.install(recyclerView)`。
- **功能：下拉刷新** —— `swipeRefreshLayout.setOnRefreshListener { loadPrecious() }`。
- **功能：页面标题** —— 顶栏"入站必刷"。
- **功能：刷新（重置到第 1 页）** —— `loadPrecious()`：page 置 1；首次建 `CustomLinearManager` + 列表，非首次清空；置刷新动画；线程池执行 `addPrecious()`。
- **功能：加载下一页（翻页）** —— `addPrecious()`：`RecommendApi.getPrecious(list, page)` 后 `page++`，主线程追加并停止刷新。
- **功能：上拉加载更多（滚动监听）** —— 最后可见项 `>= itemCount - 3` 且向下滚动且未加载中时翻页。
- **功能：列表适配器** —— `VideoCardAdapter`。
- **功能：加载失败提示** —— `MsgUtil.err(e)`。

---

## RankingActivity.kt（全站排行榜页）

继承 `InstanceActivity`。

- **功能：设置菜单点击** —— `setMenuClick()`。
- **功能：滚动时图片延迟加载** —— `ImageAutoLoadScrollListener.install(recyclerView)`。
- **功能：下拉刷新** —— `swipeRefreshLayout.setOnRefreshListener { loadRanking() }`。
- **功能：页面标题** —— 顶栏"全站排行榜"。
- **功能：刷新** —— `loadRanking()`：首次建布局+列表，非首次清空；置刷新动画；线程池执行 `addRanking()`。
- **功能：加载榜单数据** —— `addRanking()`：调用 `RankingApi.getRanking(list, 0, "all")`（分区 0 = 全站，类型 all），主线程追加、停止刷新、首次建适配器或增量通知。
- **功能：列表适配器** —— `VideoCardAdapter`。
- **功能：加载失败提示** —— `MsgUtil.err(e)`。
- **注意**：本页**没有**"上拉加载更多"滚动监听（榜单一次拉全量），也没有排序/分区切换 UI（硬编码 `0, "all"`）。

---

## TimelineActivity.kt（时间线页，追番时间线）

继承 `InstanceActivity`，使用 `R.layout.activity_simple_refresh`。

- **功能：页面标题** —— `setPageName("时间线")`。
- **功能：标题点击触发菜单** —— `findViewById(R.id.pageName).setOnClickListener { menuClick.run() }`，点击顶栏标题弹出菜单。
- **功能：设置菜单点击** —— `setMenuClick()`。
- **功能：空状态视图** —— 绑定 `emptyTip`，无数据时显示空提示。
- **功能：启用下拉刷新** —— `swipeRefreshLayout.setEnabled(true)`，并初始置刷新动画 true。
- **功能：下拉刷新** —— `setOnRefreshListener`：清空 `dayInfoList` 后重新 `loadTimeline()`。
- **功能：加载时间线数据** —— `loadTimeline()`：线程池调用 `TimelineApi.getTimeline(types, 7, 7)`（类型 `types="1"`=追番，7 天/每格 7 条）。主线程追加数据；首次创建 `TimelineAdapter` 并 setAdapter，非首次 `notifyDataSetChanged`。
- **功能：空/非空视图切换** —— 数据为空则隐藏 recyclerView、显示 emptyView；反之显示列表。
- **功能：加载失败处理** —— 停止刷新动画、调用 `report(e)` 上报、Toast 长提示"加载失败"。
- **注意**：`types` 硬编码为 `"1"`（追番），未提供类型切换 UI；无加载更多。

---

## HotSearchActivity.kt（热搜页）

继承 `RefreshMainActivity`。

- **功能：页面标题** —— `setPageName("热搜")`。
- **功能：设置菜单点击** —— `setMenuClick()`。
- **功能：下拉刷新** —— `setOnRefreshListener { loadHotSearch() }`。
- **功能：加载热搜** —— `loadHotSearch()`：置刷新动画 true；线程池调用 `HotSearchApi.getHotSearch(newList)`。
- **功能：结果成功应用** —— 返回 true 则替换 `hotList` 并 `applyResult()`：首次创建 `HotSearchAdapter`，非首次 `notifyDataSetChanged`；停止刷新。
- **功能：接口失败提示** —— 返回 false 时 Toast"获取热搜失败，请稍后重试"。
- **功能：网络异常提示** —— 捕获异常：`report(e)` 上报 + Toast"网络异常，请稍后重试"。
- **注意**：无加载更多（热搜单次全量）；热搜项点击/跳转由 `HotSearchAdapter` 负责。

---

## VideoInfoActivity.kt（视频详情页容器）

继承 `BaseActivity`，使用 `R.layout.activity_simple_viewpager`（ViewPager 承载多个 Fragment：简介 / 评论 / 相关推荐）。

### Intent Extra 输入

- **`type`**（String，默认 `"video"`）：值为 `"media"` 时走番剧详情（`initMediaInfoView`），否则走视频详情（`initVideoInfoView`）。
- **`aid`**（Long，默认 114514）：视频/番剧 av 号或 media_id。
- **`bvid`**（String，可空）：视频 BV 号。
- **`seekReply`**（Long，默认 -1）：定位到某条评论；非 -1 时自动切到评论页。

### 功能/用法

- **功能：清内存后加载** —— 进入即 `Glide.clearMemory()`。
- **功能：长按顶栏返回初始页** —— `setupLongPressToRoot()`：长按顶部栏 `R.id.top`，Toast"已返回初始页面"并广播 `CloseAllVideoPagesEvent`（关闭所有视频详情页）。
- **功能：番剧详情初始化** —— `initMediaInfoView()`：标题"番剧详情"；创建 `BangumiInfoFragment`（传 aid 作为 media_id）+ `ReplyFragment`（aid=番剧 media_id、type=1、评论数=1 占位、seekReply 定位、无 staff）；设 offscreenPageLimit；注册页名监听；若 seekReply 非 -1 则 `viewPager.currentItem=1`；首次进入 Toast"提示：本页面可以左右滑动"（`SharedPreferencesUtil.first_videoinfo`）。
- **功能：视频详情初始化** —— `initVideoInfoView()`：展示新手引导（`tutorial_video`、`showPagerTutorial`）；标题"视频详情"；经 `TerminalContext.getVideoInfoByAidOrBvId` 观察数据。
- **功能：视频成功创建三页** —— ①`VideoInfoFragment`（简介页）；②`ReplyFragment`（评论页，传 `stats.reply`、seekReply、`staff[0].mid`，并 `setManager(staff)`）；③若设置 `related_enable` 为 true 则加 `VideoRcmdFragment`（相关推荐页）。设 offscreenPageLimit、注册页名监听；seekReply 非 -1 则跳到第 1 页（评论）。
- **功能：视频加载失败** —— 加载图换成 `loading_2233_error`，Toast"获取信息失败！\n可能是视频不存在？"，5 秒后 `MsgUtil.err` 输出错误日志。
- **功能：页名随页变化** —— `registerPageNameListener/updatePageName`：主标题（"视频详情"/"番剧详情"）加子标题——评论页→"评论"、相关推荐页→"相关推荐"、其余→"简介"。
- **功能：切换评论对应视频** —— `setCurrentAid(aid)`：主线程刷新评论页（番剧选集/分P 切换时调用，见 BangumiInfoFragment）。
- **功能：加载完成淡入** —— `crossFade(fragmentView)`：淡入页面并让 `scrollView` 获取焦点（可滚动键盘操作）。
- **功能：评论插入事件订阅** —— EventBus 订阅 `ReplyEvent`（sticky、ASYNC），调用 `replyFragment.notifyReplyInserted(event)`。
- **功能：关闭所有详情页事件** —— EventBus 订阅 `CloseAllVideoPagesEvent`（MAIN），非 finishing/destroyed 则 `finish()`。
- **功能：事件总线开关** —— `eventBusEnabled()` 返回 true。

---

## VideoInfoFragment.kt（视频详情-简介页，681 行）

### 构造参数（Intent/Fragment args）

- **`aid`**（Long）、**`bvid`**（String?）：由 `newInstance(aid, bvid)` 传入。

### 回调/ActivityResult

- **功能：收藏结果回调** —— `favLauncher`：结果码 `RESULT_ADDED(1)` → 收藏图标点亮 `icon_fav_1`；`RESULT_DELETED(-1)` → `icon_fav_0`。
- **功能：转发结果回调** —— `writeDynamicLauncher`：`RESULT_OK` 后解析 text 中 `@xxx` 提及（`DynamicApi.mentionAtFindUser` 找 uid），再 `DynamicApi.relayVideo` 转发该视频，成功 Toast"转发成功~"，失败"转发失败"。
- **功能：通知权限回调** —— `notificationPermissionLauncher`：授权后 `startDownloadFlow()`；拒绝则 Toast"需要通知权限才能进行下载，请前往设置授予权限"。

### 视图初始化与状态

- **功能：横屏布局适配** —— 设置 `ui_landscape` 时按屏宽/6 加左右 padding。
- **功能：番剧跳转** —— 若 `epid != -1`（来自番剧的普通视频），跳转 `BangumiApi.getMdidFromEpid` 得到的 media 详情页并结束本页。

### 封面（img_cover）

- **功能：封面加载** —— Glide 圆角加载视频封面。
- **功能：点击封面播放** —— `cover_play_enable` 为 true 时点击→`playClick()`；否则点击→`showCover()`（全屏看图）。
- **功能：长按封面看图** —— `showCover()` 打开 `ImageViewerActivity` 显示封面大图。

### 标题

- **功能：标题徽章** —— `getTitleSpan()`：按类型加徽章——充电专属 / 互动视频 / 全景视频 / 联合投稿（红底白字 RadiusBackgroundSpan）。
- **功能：复制标题** —— `StringUtil.setCopy(title, ...)` 长按复制。

### 标签（tags）

- **功能：标签加载** —— 设置 `tags_enable` 时线程池拉 `VideoInfoApi.getTags`，构建可点击标签 span。
- **功能：标签展开/收起** —— 点击标签文本在 1 行 / 233 行之间切换 `tagsExpand`。
- **功能：点击标签搜索** —— 点单个标签 → `SearchActivity` 并 `putExtra("keyword", 标签名)` 搜索该标签。
- **功能：关闭标签** —— `tags_enable` 关闭时隐藏标签区。

### 点赞 / 投币 / 收藏（一键三连区）

- **功能：点赞状态图标** —— 已赞 `icon_like_1`；已投币 `icon_coin_1`；已收藏 `icon_fav_1`。
- **功能：点赞（点击 layout_like）** —— 未登录 Toast"还没有登录喵~"；`LikeCoinFavApi.like` 切换点赞/取消；成功更新图标与计数（"+1"/"-1"）；错误码处理：-403 风控、65006"已经点赞过了喵~"。
- **功能：投币（点击 layout_coin）** —— 未登录提示；`LikeCoinFavApi.coin` 投 1 币；`coinAdd` 计数（会话内最多 +2）；`stats.coined >= coin_limit` 时提示"投币数量到达上限"；错误码：-403 风控、34002"不能给自己投币哦"。
- **功能：收藏（点击 layout_fav）** —— 跳 `AddFavoriteActivity`（extra：aid、bvid）用 `favLauncher` 等待结果更新图标。
- **功能：一键三连（长按 layout_like）** —— 设置 `like_one_triple` 且已登录时：三个图标播放无限 shake 动画 2 秒，期间若松手（`ACTION_UP/CANCEL`）取消动画（`cancelTripleAction`）；2 秒后 `LikeCoinFavApi.triple(aid)` 执行三连，成功后图标全亮并 Toast"三连成功"。长按但条件不满足返回 false。
- **功能：三连取消（触摸监听）** —— `ACTION_UP`/`ACTION_CANCEL` 时 `cancelTripleAction()` 移除回调并停止动画。

### 统计信息

- **功能：播放数/点赞/投币/收藏数** —— 用 `StringUtil.toWan` 万单位显示。
- **功能：弹幕数** —— `danmakuCount` 显示弹幕总数。
- **功能：BV 号显示与复制** —— 长按 BV 号复制，Toast"BV号已复制"。
- **功能：发布时间** —— `timeDesc` 展示（如"x天前"）。
- **功能：视频时长** —— `duration` 展示。
- **功能：独家/争议提示** —— `argueMsg` 非空时显示 `exclusiveTip` 提示条。

### UP主区（up_recyclerView）

- **功能：UP 主列表** —— `UpListAdapter` 展示所有 staff（含联合投稿/合作），点击查看 UP 主由适配器处理。

### 简介（description）

- **功能：展开/收起简介** —— 点击简介在 3 行 / 512 行间切换。
- **功能：链接可点** —— `StringUtil.setLink` 识别并点击跳转网址。
- **功能：@提及可点** —— `StringUtil.setAtLink(videoInfo.descAts, ...)` 点击跳 UP 主。
- **功能：复制简介** —— `StringUtil.setCopy` 长按复制。

### 播放按钮（play）

- **功能：播放（点击）** —— `playClick()`：首次播放弹提示框（根据 `cover_play_enable` 显示不同引导文案）；单 P（`pagenames.size==1`）直接 `PlayerApi.startGettingUrl(playerData)` 进播放器；多 P 跳 `MultiPageActivity`（extra：data=playerData）；重置 `playerData.timeStamp=0`。
- **功能：选择播放器（长按 play）** —— 跳 `SettingPlayerChooseActivity` 选择播放器。

### 稍后再看（addWatchlater）

- **功能：添加稍后再看（点击）** —— `WatchLaterApi.add(aid)`，成功 Toast"添加成功"。
- **功能：查看稍后再看（长按）** —— 跳 `WatchLaterActivity`。

### 下载（download）

- **功能：下载（点击）** —— `downloadClick()`：无存储权限→请求；Android 13+ 无通知权限→请求通知权限；否则 `startDownloadFlow()`。
- **功能：下载流程（startDownloadFlow）** —— 单 P 且目录已存在：显示"已在下载队列/已下载完成"（按 `.DOWNLOADING` 标记判断）；多 P→跳 `MultiPageActivity`（extra：download=1、data）；单 P→跳 `QualityChooserActivity`（extra：page=0、aid、bvid）选清晰度。
- **功能：清除缓存（长按 download）** —— 删除 `FileUtil.getVideoDownloadPath` 对应文件夹，Toast"已清除此视频的缓存文件夹"。

### 转发（relay）

- **功能：转发动态（点击）** —— 跳 `SendDynamicActivity`，`writeDynamicLauncher` 等待结果并执行转发（含 @ 提及解析）。
- **功能：复制链接（长按 relay）** —— 复制 `https://www.bilibili.com/{bvid}`，Toast"视频完整链接已复制"。

### 视频摘要（video_summary）

- **功能：AI 视频摘要（点击）** —— `VideoInfoApi.getVideoConclusion(aid, bvid, cid, upMid)` 生成摘要，`MsgUtil.showText` 弹出标题+摘要；失败 Toast"获取视频摘要失败"。
- **注意**：未登录时 `addWatchlater / relay / video_summary` 均隐藏。

### 合集（collection）

- **功能：合集入口** —— 有合集时显示"合集 · 标题"，点击跳 `CollectionInfoActivity`（extra：fromVideo=aid）。

### 其他

- **功能：播放器数据预取 + 上报历史** —— 进入时 `PlayerApi.getVideo(playerData, false)` 取流，并 `HistoryApi.reportHistory(aid, cidHistory, progress)` 上报播放进度。
- **功能：加载完成回调** —— `onFinishLoad()` 调 `activity.crossFade(view)` 淡入。
- **功能：销毁清理** —— `onDestroy()` 取消三连动画。

---

## VideoRcmdFragment.kt（相关推荐页）

继承 `RefreshListFragment`（自带刷新/加载更多）。

- **构造参数**：`newInstance(aid)` 传入 `aid`（av 号）。
- **功能：加载相关推荐** —— `onViewCreated` 用 `CenterThreadPool.supplyAsyncWithLiveData { RecommendApi.getRelated(aid) }` 异步取数并 observe。
- **功能：渲染列表** —— 成功用 `VideoCardAdapter` 渲染，`setAdapter` 并 `setRefreshing(false)`。
- **功能：加载失败** —— `loadFail(it)` 提示。
- **注意**：本页在 `VideoInfoActivity` 中作为可选的第三页，受设置 `related_enable` 控制是否加入。

---

## BangumiInfoFragment.kt（番剧详情-简介页）

### 构造参数

- **`media_id`**（Long）：番剧 media_id，由 `newInstance(mediaId)` 传入。

### 信息展示

- **功能：封面横图** —— 加载 `cover_horizontal`，点击→`ImageViewerActivity` 看大图。
- **功能：标题** —— `title` 展示。
- **功能：副标题** —— `subtitle` 非空显示。
- **功能：地区/类型** —— `area_name` + `| ` + `type_name` 组合显示。
- **功能：评分** —— `score > 0` 时显示"评分：x.x (N人)"。
- **功能：发布时间/状态** —— `pub_time_show` + （已完结/连载中）。
- **功能：统计** —— 播放 / 收藏 / 追番 用 `formatNumber`（万/亿）显示。
- **功能：标签** —— `styles` 显示"标签：xxx"。
- **功能：简介（evaluate）** —— 点击"简介"标题头展开/收起正文（箭头旋转 180°）。
- **功能：STAFF（staff）** —— 点击"STAFF"标题头展开/收起（箭头旋转）。
- **功能：备案号** —— `record` 显示"备案号：xxx"。
- **功能：当前选集显示** —— `indexShow` 显示当前集标题（如"第1话 xxx"）。

### 选集 / 分P / 章节

- **功能：选集横排列表** —— `MediaEpisodeAdapter` 横向 RecyclerView 展示当前 section 的分集；点击某一集 → `selectedEpisode=index` 并 `refreshReplies()` 切换对应评论。
- **功能：章节切换（section_choose）** —— 点击弹单选对话框列出所有 section（分卷），选择后重置 `selectedEpisode=0`、刷新评论、更新按钮文案、重设分集列表并 `scrollToPosition(0)`。
- **功能：分集选择（episode_choose）** —— 点击弹单选对话框列出当前卷所有集（`title.title_long` 格式），选择后刷新评论、高亮选中集并滚动到该集。
- **功能：无 section 时占位** —— `sectionList` 空：按钮"敬请期待"、隐藏播放按钮与分集区、停止评论刷新。
- **功能：播放（点击 btn_play）** —— 取当前选中集 `episode.toPlayerData()` 跳 `JumpToPlayerActivity`（extra：data）；播放前 `Glide.clearMemory()`。
- **功能：选择播放器（长按 btn_play）** —— 跳 `SettingPlayerChooseActivity`。
- **功能：评论联动** —— `refreshReplies()` 调 `VideoInfoActivity.setCurrentAid(当前集 aid)` 刷新评论区为该集评论。
- **功能：加载完成淡入** —— `onFinishLoad()` 调 `activity.crossFade(view)`。
- **功能：数据加载失败** —— `MsgUtil.err("番剧详情：", error)`。

---

## AddFavoriteActivity.kt（添加收藏页）

继承 `RefreshListActivity`。

### Intent Extra 输入

- **`aid`**（Long）：要收藏/管理的视频 av 号。

### 功能/用法

- **功能：页面标题** —— `setPageName("添加收藏")`。
- **功能：未登录拦截** —— 未登录 Toast"还没有登录喵~"并 `finish()` 返回。
- **功能：加载收藏夹状态** —— 线程池 `FavoriteApi.getFavoriteState(aid, folderList, fidList, stateList, countList, maxCountList)` 获取各收藏夹及其包含状态。
- **功能：渲染收藏夹列表** —— `FolderChooseAdapter` 展示收藏夹（名称、是否已含该视频、已存/上限数量），支持勾选加入/取消。`setAdapter` 后停止刷新。
- **功能：加载失败** —— `loadFail(e)`。
- **功能：返回结果回传** —— `finish()` 时根据适配器状态 `setResult`：`added` → `RESULT_ADDED(1)`；全部取消 → `RESULT_DELETED(-1)`；供 `VideoInfoActivity` 更新收藏图标。
- **功能：结果提示** —— `onDestroy()` 且 `fav_notice` 开启时：添加成功→"添加成功"；全部删除→"删除成功"；有变更→"更改成功"。
- **注意**：具体收藏/取消收藏的请求与"全选""新建收藏夹"等操作在 `FolderChooseAdapter` 内实现。

---

## 跨页面共性小结

- **列表页统一范式**：`VideoCardAdapter`（视频卡片）+ `CustomLinearManager`（线性布局）+ 下拉刷新（SwipeRefreshLayout）+ 上拉翻页/滚动监听加载更多。
- **视频详情页 ViewPager 三页**：简介（VideoInfoFragment）→ 评论（ReplyFragment）→ 相关推荐（VideoRcmdFragment，可选）。
- **番剧详情页 ViewPager 两页**：简介（BangumiInfoFragment）→ 评论（ReplyFragment）。
- **关键设置项**（SharedPreferences）：`related_enable`（相关推荐开关）、`tags_enable`（标签显示）、`cover_play_enable`（点击封面播放）、`like_one_triple`（长按一键三连）、`fav_notice`（收藏结果提示）、`ui_landscape`（横屏适配）、`first_videoinfo` / `first_play`（首次引导）。
- **一键三连完整链路**：点赞=点击，投币=点击，收藏=点击，三连=长按点赞图标 2 秒；未登录时仅点赞/投币/收藏可点（均提示未登录），稍后再看/转发/摘要仅登录可见。