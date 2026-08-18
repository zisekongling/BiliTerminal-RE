# 动态与直播源码功能审查（group-04-dynamic-live）

> 审查对象：RE:哔哩终端（ReBiliClient）第三方 B 站安卓客户端
> 颗粒度：功能-用法 逐条
> 覆盖 7 个文件：动态 4 个（DynamicActivity / DynamicInfoActivity / DynamicInfoFragment / SendDynamicActivity）+ 直播 3 个（RecommendLiveActivity / FollowLiveActivity / LiveInfoActivity）

---

## DynamicActivity.kt

动态列表主页（继承 `RefreshMainActivity`，支持下拉刷新 + 上滑加载更多）。

### 页面的数据类型与状态
- **功能：动态列表数据源** —— 用 `dynamicList: ArrayList<Dynamic>?` 保存动态列表。
- **功能：分页游标 offset** —— 用 `offset: Long` 记录分页游标（从 API 返回值取得，`-1` 表示到底）。
- **功能：动态类型过滤器 type** —— 用 `type: String` 记录当前分区类型，默认 `"all"`。
- **功能：首次刷新标记 firstRefresh** —— 首次加载时创建列表与适配器，后续刷新清空重载。
- **功能：最近更新的 UP 主列表 recentUpList** —— 保存顶部"最近更新"UP 主快捷入口数据。

### 类型分区映射（顶部 Tab）
- **功能：动态类型名称映射** —— `typeNameMap` 把中文分区名映射为 API 类型值："全部"→`all`、"视频投稿"→`video`、"追番"→`pgc`、"专栏"→`article`。
- **功能：切换动态分区 Tab** —— 通过 `selectTypeLauncher` 接收用户从分区选择页返回的分区名（`item`），映射为类型后调用 `refreshDynamic()` 重新加载。
- **功能：切换分区时的加载保护** —— 若当前 `isRefreshing` 正在加载，Toast 提示"还在加载中OvO"，不响应切换。

### 下拉刷新 / 加载更多
- **功能：下拉刷新** —— `setOnRefreshListener { refreshDynamic() }`，下拉触发 `refreshDynamic()`。
- **功能：上滑加载更多** —— `setOnLoadMoreListener { page -> addDynamic(type) }`，滚动到底部加载下一页。
- **功能：刷新重置逻辑** —— 非首次刷新时把 `offset=0`、`bottom=false`，清空列表并 `notifyDataSetChanged`，随后重新拉取。
- **功能：加载更多并追加** —— `addDynamic()` 调 `DynamicApi.getDynamicList(list, offset, 0, type)`，把返回列表 `addAll` 追加到 `dynamicList`。
- **功能：判断是否到底** —— 当 API 返回 `offset == -1L` 时置 `bottom = true`，停止加载更多。
- **功能：刷新时新增列表项动画** —— 加载更多时用 `notifyItemRangeInserted`（偏移量考虑"最近更新"头部是否显示）增量插入。
- **功能：刷新时重置未读计数** —— 每次下拉刷新成功后把 `SharedPreferencesUtil.DYNAMIC_UPDATE_NUM` 置 0（清除动态页未读数量红点提示）。

### 首次加载与适配器构建
- **功能：首次创建列表与适配器** —— 首次加载时 `dynamicAdapter = DynamicAdapter(this, dynamicList, recyclerView, recentUpList)` 并 `setAdapter`。
- **功能：页面标题"动态"** —— `setPageName("动态")` 设置标题。
- **功能：新手指引** —— `TutorialHelper.showTutorialList(this, R.array.tutorial_dynamic, 6)` 弹出动态页新手教程。
- **功能：进入页面自动加载** —— `onCreate` 里先后调用 `loadRecentUpList()` 和 `refreshDynamic()`。

### 最近更新 UP 主（顶部横条）
- **功能：加载最近更新 UP 主列表** —— `loadRecentUpList()` 调 `DynamicApi.getRecentUpList()`。
- **功能：最近更新条目的插入/移除/更新** —— 根据 `shouldShow` 判断：原来不显示现在显示则 `notifyItemInserted(1)`；原来显示现在不显示则 `notifyItemRemoved(1)`；始终显示则 `notifyItemChanged(1)`。
- **功能：最近更新显示开关** —— `showRecentUp()` 读取 `SharedPreferencesUtil.RECENT_UP_DISPLAY_ENABLE`（默认 true），且列表非空才显示。
- **功能：加载失败降级** —— `getRecentUpList()` 抛异常时置 `recentUpList = null`，隐藏该横条。

### 发动态入口（从 ActivityResult 回调）
- **功能：写动态（发图文）回调** —— `writeDynamicLauncher` 接收 `SendDynamicActivity` 返回的 `text`。
- **功能：解析 @ 提及用户** —— 用正则 `@(\S+)\s` 提取被 @ 的用户名，逐个调 `DynamicApi.mentionAtFindUser` 把用户名解析成 uid。
- **功能：发布纯文本动态** —— 无 @ 时调 `DynamicApi.publishTextContent(text)`；有 @ 时调 `DynamicApi.publishTextContent(text, atUids)`。
- **功能：发布成功提示** —— 返回 `dynId != -1` 则 Toast"发送成功~"。
- **功能：发布后立即回显到列表顶部** —— 成功后再调 `DynamicApi.getDynamic(dynId)` 拉取新动态，`add(0, ...)` 插入列表头，若当前是"全部"分区则 `notifyItemInserted(0)` + `notifyItemRangeChanged` 刷新。
- **功能：发布失败提示** —— 返回 `-1` 则 Toast"发送失败"。
- **功能：异常处理** —— 捕获异常用 `MsgUtil.err(e)` 提示。

### 转发动态回调
- **功能：静态转发启动器** —— `getRelayDynamicLauncher(activity)` 供外部页面（如动态详情）注册，用于发起转发动态操作。
- **功能：转发内容兜底** —— 若转发文本为空，默认文案为"转发动态"。
- **功能：转发时解析 @ 提及** —— 同样用正则解析 @ 用户名转 uid。
- **功能：执行转发** —— 调 `DynamicApi.relayDynamic(finalText, atUids, dynamicId)`。
- **功能：转发结果提示** —— 成功 Toast"转发成功~"，失败 Toast"转发失败"。

### 动态删除后列表联动（onActivityResult）
- **功能：删除动态后同步移除** —— 从动态详情页返回（`DynamicHolder.GO_TO_INFO_REQUEST` + `RESULT_OK`）时，读取返回的 `position`，扣除"最近更新"头部偏移后 `DynamicHolder.removeDynamicFromList(...)` 把对应动态从列表移除。
- **功能：删除时避免与刷新冲突** —— `isRefreshing` 时不做删除联动。

---

## DynamicInfoActivity.kt

动态详情页容器（继承 `BaseActivity`，用 ViewPager 承载"动态内容" + "评论区"两个 Fragment）。

### Intent Extra
- **功能：接收动态 id** —— `intent.getLongExtra("id", 0)` 获取动态 ID。
- **功能：接收评论定位 seekReply** —— `intent.getLongExtra("seekReply", -1)`，用于跳到指定楼层；非 `-1` 时初始定位到评论区页。

### 页面构建
- **功能：页面标题"动态详情"** —— `pageName.text = "动态详情"`。
- **功能：新手指引** —— `TutorialHelper.showTutorialList(this, R.array.tutorial_dynamic_info, 6)`。
- **功能：加载动态数据** —— `TerminalContext.getInstance().getDynamicById(id)`（LiveData）观察加载结果。
- **功能：构建双 Fragment ViewPager** —— 成功时创建 `DynamicInfoFragment`（动态内容）+ `ReplyFragment`（评论区），用 `ViewPagerFragmentAdapter` 装配。
- **功能：评论 Fragment 参数装配** —— `ReplyFragment.newInstance(dynamic.comment_id, dynamic.comment_type, dynamic.stats.reply, seek_reply, dynamic.userInfo.mid)`；设置 `setManager(dynamic.userInfo)`、`replyType = REPLY_TYPE_DYNAMIC`。
- **功能：seekReply 时直达评论区** —— `seek_reply != -1` 时 `viewPager.currentItem = 1` 跳到评论 Tab。
- **功能：加载过渡动画** —— 成功时 `AnimationUtils.crossFade(loading, diFragment.view)` 从加载图淡入内容。
- **功能：内容页获得焦点** —— 动态内容页 `scrollView` 设为可聚焦并 `requestFocus()`（便于按键/遥控操作）。
- **功能：加载失败展示错误图** —— 失败时 `loading` 图换为 `loading_2233_error` 并 `MsgUtil.err`。
- **功能：分页滑动新手引导** —— `TutorialHelper.showPagerTutorial(this, 2)` 提示可左右滑动切换内容/评论。
- **功能：回复事件刷新评论** —— `@Subscribe`（sticky、ASYNC、priority=1）接收 `ReplyEvent` 事件，调 `rFragment.notifyReplyInserted(event)` 增量插入新回复。
- **功能：离开详情页** —— `onDestroy()` 调 `TerminalContext.getInstance().leaveDetailPage()` 释放详情页缓存。

---

## DynamicInfoFragment.kt

动态详情页"动态内容"Fragment（继承 `BaseFragment`，承载一条动态的完整展示）。

### 数据与生命周期
- **功能：构造参数 id** —— `newInstance(id)` 把动态 ID 存入 arguments，`onCreate` 读取。
- **功能：空布局承载** —— `onCreateView` 加载 `fragment_empty` 布局。
- **功能：加载动态数据** —— `TerminalContext.getInstance().getDynamicById(id)` 观察，成功回调 `initView`；失败静默处理。

### 内容渲染
- **功能：横屏留白** —— 当设置 `ui_landscape`（横屏）时按屏幕宽度的 1/6 设置 `scrollView` 左右 padding，避免内容过宽。
- **功能：渲染动态主卡片** —— `View.inflate` 出 `cell_dynamic` 布局，用 `DynamicHolder(dynamicView, activity, false)` 的 `showDynamic(...)` 渲染动态本体（含点赞、评论、转发、图片、视频等交互）。
- **功能：删除按钮（长按触发）** —— `DynamicHolder.getDeleteListener(activity, dynamic)` 设为 `item_dynamic_delete` 的长按监听，长按可删除自己的动态。
- **功能：删除按钮显隐** —— 仅当 `dynamic.canDelete` 为 true 时才显示删除按钮（否则 GONE）。
- **功能：渲染转发子动态** —— 当 `dynamic.dynamic_forward != null` 时，用 `DynamicHolder(childCard, activity, true)` 渲染被转发的子动态卡片并 `cell_dynamic_child` 设为 VISIBLE（展示被转发内容，子卡片点击会跳转到原动态详情）。

---

## SendDynamicActivity.kt

发动态（写动态）页面（继承 `BaseActivity`，承载文本输入 + 表情 + 转发卡片预览）。

### 登录校验
- **功能：未登录拦截** —— 若 `SharedPreferencesUtil.mid == 0`（未登录），直接 `setResult(RESULT_CANCELED)` 并 `finish()`，Toast"还没有登录喵~"。

### 输入与发送
- **功能：文本输入框** —— `editText = findViewById(R.id.editText)`，用户在此输入动态正文。
- **功能：发送按钮（MaterialCardView）** —— 点击 `send` 触发发送。
- **功能：Cookie 有效性校验后发送** —— 仅当 `SharedPreferencesUtil.cookie_refresh` 为 true 才允许发送；否则弹 `MsgUtil.showDialog("无法发送", "上一次的Cookie刷新失败了，……")`。
- **功能：回传发送结果给调用方** —— 发送时把本 Activity 的 `intent.extras`（保留调用方透传参数，如转发目标 dynamicId）与用户输入的 `text` 一起放进 result Intent，`setResult(RESULT_OK, result)` 后 `finish()`，由 `DynamicActivity.writeDynamicLauncher` 真正执行发布。
- **功能：表情选择** —— 点击 `R.id.emote` 打开 `EmoteActivity`，`putExtra("from", EmoteApi.BUSINESS_DYNAMIC)` 指定为动态业务。
- **功能：表情回填** —— `emoteLauncher` 收到 `EmoteActivity` 返回的 `text`（表情文本）后 `editText.append(...)` 追加到输入框。

### 转发内容预览
- **功能：读取待转发内容** —— `TerminalContext.getInstance().getForwardContent()` 取转发目标，按类型判断：`VideoInfo`（视频）或 `Dynamic`（动态）。
- **功能：转发动态预览卡片** —— 转发目标为 `Dynamic` 时，inflate `cell_dynamic` 用 `DynamicHolder.showDynamic` 展示被转发的动态卡片。
- **功能：转发视频预览卡片** —— 转发目标为 `VideoInfo` 时，inflate `cell_video_list` 用 `VideoCardHolder.showVideoCard` 展示视频卡片。

### 清理
- **功能：清理转发缓存** —— `onDestroy()` 里 `TerminalContext.getInstance().setForwardContent(null)` 清空转发内容，避免残留影响下次发动态。

---

## RecommendLiveActivity.kt

推荐直播列表页（继承 `RefreshMainActivity`，支持下拉刷新 + 上滑加载更多）。

### 列表构建
- **功能：页面标题"推荐直播"** —— `setPageName("推荐直播")`。
- **功能：固定列表尺寸** —— `recyclerView.setHasFixedSize(true)` 提升滚动性能。
- **功能：加载推荐直播列表（第一页）** —— `LiveApi.getRecommend(page)` 获取推荐直播间列表。
- **功能：构建适配器** —— `LiveCardAdapter(this, roomList)`，点击直播间卡片进入直播间详情（由 LiveCardAdapter 内部处理）。
- **功能：加载完成移除刷新动画** —— `setRefreshing(false)`。

### 下拉刷新 / 加载更多
- **功能：上滑加载更多** —— `setOnLoadMoreListener { continueLoading(it) }`，滚动到底触发 `continueLoading(page)`。
- **功能：加载下一页** —— `continueLoading` 里 `LiveApi.getRecommend(page)` 取下一页，`addAll` 追加并用 `notifyItemRangeInserted` 增量插入。
- **功能：判断到底** —— 返回列表 `< 1` 时置 `bottom = true` 停止加载。
- **功能：加载失败处理** —— 异常时 `loadFail(e)` 走统一失败逻辑。

> 说明：本页代码中未显式调用 `setOnRefreshListener`，下拉刷新的绑定/行为由父类 `RefreshMainActivity` 提供（首次加载与加载更多均由该类管理）。

---

## FollowLiveActivity.kt

我关注的直播列表页（继承 `RefreshListActivity`，支持下拉刷新 + 上滑加载更多）。

### 列表构建
- **功能：页面标题"我关注的直播"** —— `setPageName("我关注的直播")`。
- **功能：固定列表尺寸** —— `recyclerView.setHasFixedSize(true)`。
- **功能：加载关注直播间列表（第一页）** —— `LiveApi.getFollowed(page)` 获取已关注主播的直播间。
- **功能：构建适配器** —— `LiveCardAdapter(this, roomList)`，点击卡片进入直播间（LiveCardAdapter 内部处理）。
- **功能：空列表展示空视图** —— 当首屏 `roomList.size < 1` 时调 `showEmptyView()` 展示空态。
- **功能：加载完成移除刷新动画** —— `setRefreshing(false)`。

### 下拉刷新 / 加载更多
- **功能：上滑加载更多** —— `setOnLoadMoreListener { continueLoading(it) }`，滚动到底触发 `continueLoading(page)`。
- **功能：加载下一页** —— `LiveApi.getFollowed(page)` 取下一页，`addAll` 追加并用 `notifyItemRangeInserted` 增量插入。
- **功能：判断到底** —— 返回列表 `< 1` 时置 `bottom = true` 停止加载。
- **功能：加载失败处理** —— 异常时 `loadFail(e)`。

> 说明：与推荐直播页类似，下拉刷新行为由父类 `RefreshListActivity` 提供。

---

## LiveInfoActivity.kt

直播间详情页（继承 `BaseActivity`，承载封面、信息、清晰度/路线选择、播放入口）。

### Intent Extra 与数据
- **功能：接收房间号 room_id** —— `intent.getLongExtra("room_id", 0)`；若为 0 直接 `finish()` 退出。
- **功能：加载直播间信息** —— `TerminalContext.getInstance().getLiveInfoByRoomId(room_id)`（LiveData）观察，成功解析出 `LiveRoom`、`UserInfo`（主播）、`LivePlayInfo`（播放信息）。
- **功能：加载失败处理** —— Toast"直播不存在"，1 分钟后 `MsgUtil.err`，并 `finish()` 退出。

### 页面元素与交互
- **功能：加载过渡动画** —— `AnimationUtils.crossFade(loading, scrollView)` 从加载图淡入内容。

#### 封面
- **功能：展示封面图** —— Glide 加载 `room.user_cover`（圆角 4dp、降采样 0.85、跳过内存缓存、磁盘缓存 AUTOMATIC），占位图为 `placeholder`。
- **功能：点击封面看图** —— 点击封面打开 `ImageViewerActivity`，`putExtra("imageList", [user_cover])` 全屏查看封面。

#### 基本信息
- **功能：标题展示** —— `title.text = StringUtil.removeHtml(room.title)`（去 HTML 标签）。
- **功能：主播信息展示** —— 构建一个 `UserInfo(mid, name, avatar, "主播", ...)` 加入列表，用 `UpListAdapter` 在 `up_recyclerView` 横向展示主播（点击进入用户主页由 UpListAdapter 处理）。
- **功能：观看人数** —— `viewsCount.text = StringUtil.toWan(room.online) + "人观看"`。
- **功能：开播时间** —— `durationText.text = "直播开始于" + room.liveTime`。
- **功能：房间号展示** —— `idText.text = "房间号: $room_id"`，若有短号则追加 `(短号: xxx)`。
- **功能：标签展示与展开/收起** —— `tags.text = "标签：" + room.tags`；点击 `tags` 在 `maxLines=1` 与 `maxLines=233` 间切换（展开/收起）。
- **功能：分区展示** —— `areaText.text = "分区: " + 父分区 + " > " + 子分区`。
- **功能：关注数展示** —— `attentionText.text = "关注数: " + StringUtil.toWan(room.attention)`。
- **功能：直播状态展示** —— `liveStatusText` 按 `live_status` 显示：0="未开播"、1="直播中"、2="轮播中"、其他="未知"。
- **功能：房间号/标签/标题可复制** —— `StringUtil.setCopy(idText, tags, title)`，长按对应文本可复制。
- **功能：简介展示与展开/收起** —— `description.text = StringUtil.removeHtml(StringUtil.htmlToString(room.description))`；点击在 `maxLines=3` 与 `maxLines=512` 间切换（展开/收起），`desc_expand` 记录状态。

#### 播放（播放器跳转）
- **功能：点击"播放"进入直播播放** —— `play` 按钮点击后：取首个 stream→format→codec 的 URL 信息；`selectedHost` 选中的线路（`url_info[selectedHost]`）拼出 `host + base_url + extra` 播放地址；构造 `PlayerData(PlayerData.TYPE_LIVE)`，设 `videoUrl`、`title="直播·"+room.title`、`aid=room_id`、`mid=登录mid`，调 `PlayerApi.jumpToPlayer(playerData)` 跳转到播放器页面。
- **功能：直播已结束提示** —— codec 为空或 `url_info` 为空时 Toast"直播已结束"。
- **功能：找不到播放器提示** —— 跳转抛 `ActivityNotFoundException` 时 Toast"没有找到播放器，请检查是否安装"。
- **功能：长按"播放"更换播放器** —— 长按 `play`：若当前播放器不是 `terminalPlayer` 则 `showMsgLong("若无法播放请更换为内置播放器")`，并打开 `SettingPlayerChooseActivity` 选择播放器。
- **功能：非内置播放器播放提示** —— 加载完成时若当前播放器不是 `terminalPlayer`，`showMsgLong("直播可能只有内置播放器可以正常播放")`。

#### 清晰度选择
- **功能：清晰度列表** —— 遍历 `LiveApi.QualityMap`（清晰度名称→质量值）生成 `Bangumi.Episode` 列表，用 `MediaEpisodeAdapter` 横向展示在 `quality_list`。
- **功能：默认选中第一个清晰度** —— `qualityAdapter.selectedItemIndex = 0`。
- **功能：切换清晰度** —— 点击清晰度项：清空线路列表、`play.isEnabled=false`，后台调 `LiveApi.getRoomPlayInfo(room_id, 质量值)` 重新拉取播放信息，成功后 `refresh_host_list()` 刷新线路并恢复按钮可用；异常 `MsgUtil.err`。

#### 播放线路（CDN 路线）选择
- **功能：线路列表刷新** —— `refresh_host_list()` 根据 codec 的 `url_info` 数量生成"路线1/路线2/..."列表，`selectedHost=0` 并选中第一项。
- **功能：切换线路** —— 点击线路项 `selectedHost = i`，播放时用该线路地址。

#### 焦点与释放
- **功能：内容区获得焦点** —— `scrollView` 设为可聚焦并 `requestFocus()`（便于按键/遥控）。
- **功能：离开详情页** —— `onDestroy()` 调 `TerminalContext.getInstance().leaveDetailPage()` 释放详情页缓存。

---

## 汇总要点

| 文件 | 核心功能 |
| --- | --- |
| DynamicActivity.kt | 动态列表：分区 Tab（全部/视频/追番/专栏）、下拉刷新、上滑加载、最近更新UP主横条、发动态回调、转发回调、删除联动、未读计数清零 |
| DynamicInfoActivity.kt | 动态详情容器：ViewPager（内容+评论）、seekReply 定位评论、回复事件插入、错误图、新手引导 |
| DynamicInfoFragment.kt | 动态详情内容：渲染动态卡片、转发子动态、删除按钮（长按）、横屏留白 |
| SendDynamicActivity.kt | 发动态：文本输入、表情、Cookie 校验、转发视频/动态预览、未登录拦截 |
| RecommendLiveActivity.kt | 推荐直播列表：加载更多、到底判断 |
| FollowLiveActivity.kt | 关注直播列表：加载更多、空视图、到底判断 |
| LiveInfoActivity.kt | 直播间详情：封面看图、主播、信息展示/复制、简介/标签展开、清晰度选择、CDN路线选择、播放跳转、长按换播放器 |

> 说明：直播两个列表页的"下拉刷新"与部分统一失败处理由父类 `RefreshMainActivity` / `RefreshListActivity` 提供；卡片点击进直播间由 `LiveCardAdapter` 内部实现，未在本批文件范围内展开。
