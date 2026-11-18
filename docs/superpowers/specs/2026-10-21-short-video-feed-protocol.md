# 竖屏短视频流取流协议修订

- **日期**：2026-10-21
- **状态**：已实施（26.10.21），编译与两 flavor JVM 单测通过，**待真机验证**（用例见 §4）
- **涉及文件**：`app/src/main/java/com/RobinNotBad/BiliClient/api/StoryFeedQuery.kt`（新增，纯函数）、`api/ShortVideoFeedApi.kt`（重写取流部分，新增 `StoryFeedPager`）、`util/VideoPreloadManager.kt`、`app/src/test/java/com/RobinNotBad/BiliClient/api/StoryFeedQueryTest.kt`（新增）
- **不涉及**：播放页 UI、点赞/投币/收藏/关注、曝光与行为上报、后台轮询

## 1. 背景

短视频页（菜单 key `short_video`）的取流实现在 `api/ShortVideoFeedApi.kt`，此前是手拼查询串的一次性实现，存在四个已知缺陷：

1. **分页参数不符合接口约定**：`pull` 一直被传成字符串 `true`，而该参数的有效取值是 `0` / `1`；批次序号 `display_id` 也没有和「首屏 / 翻页」区分。
2. **无法跟随服务端的推荐路由**：接口会在响应里下发 `track_id`，下一次请求需要回传（`trackid` 参数）才能保持在同一条推荐链路。旧实现完全丢弃它，于是每次翻页都像一次全新的首次请求，内容重复、风格漂移。
3. **兜底地址不在文档端点表里**：旧实现主接口失败后改请求 `/x/v2/feed/index`，这个路径在接口文档里并不存在；实际可用的同语义兜底是动态流的 `/x/v2/feed/index/dynamic/story`。
4. **分页状态是 object 上的静态字段**：`currentPage` 挂在单例上，退出再进页面不会归零，长期使用后页码越滚越大；同一进程内也不存在「一次会话」的概念。

## 2. 目标

在**不改动播放页交互**的前提下，让短视频取流符合接口约定、能跟随推荐路由，并把分页状态收敛到「一次进入短视频页」的生命周期内。

### 2.1 非目标

> 26.10.21 第二轮（见 §6）已按用户新需求覆盖其中两条：做了 `relate/story` 续接、改了播放页手势（新增左右滑与播完自动续播）。下面的清单记录的是**第一轮**的边界。

- 不新增页面、不新增菜单项、不新增设置项；
- ~~不做 `relate/story`（相关推荐）、`space/story`（UP 主空间）等其他取流通道~~（第二轮已做 `relate/story`；`space/story` 仍未做）；
- 不做点赞 / 投币 / 收藏 / 关注，也不做曝光、播放进度等行为上报；
- ~~不改动短视频播放页的任何现有手势与视觉~~（第二轮已改：只新增左右滑、其余手势与底栏保持原样）。

## 3. 设计

### 3.1 参数拼装（纯函数）

新增 `api/StoryFeedQuery.kt`，只做参数与 JSON 的纯计算，不读 `Context` / `SharedPreferences` / `Build`，因此可直接在 JVM 单测里跑。核心约定：

| 参数 | 取值 | 说明 |
| --- | --- | --- |
| `display_id` | 批次序号，首屏 `1`，每成功一批 +1 | 不是页码；失败不递增 |
| `pull` | 首屏 `0`，翻页 `1` | 整数语义，不再传 `true` |
| `open_event` | `cold` | 冷启动事件 |
| `trackid` | 上一批最后一条非空 `track_id` | 仅正式路由回传，降级通道不回传 |
| `access_key` | 登录态存在时附加 | 未登录不带 |
| `device_name` | `Build.MODEL`，取不到则省略 | 空串不上送 |

另外：

- 百分号编码只保留 `A-Z a-z 0-9 - _ . ~`，其余按 UTF-8 字节转 `%XX`（大写）。**不用 `URLEncoder`**：它把空格编成 `+`，与接口要求的 `%20` 不一致。
- **不发** `from` / `from_spmid` / `spmid`：三者同时出现会被服务端判为请求错误，单传又没有可靠的取值来源，因此一律不发送。

### 3.2 会话级分页状态

`ShortVideoFeedApi.kt` 里新增 `class StoryFeedPager`，由 `VideoPreloadManager` 持有一个实例：

- 保存 `display_id`、`lastTrackId`、是否已成功拉过首屏、已出现过的 `aid` 集合、是否已记过降级日志；
- `fetchPage()`：首屏 `pull=0`，翻页 `pull=1`；主信息流返回空则改用竖屏动态流兜底；
- 只在**成功拿到至少一条可用内容**后才推进 `display_id` 与 `track_id`，失败保持原状，便于直接重试；
- 页面销毁（`VideoPreloadManager.release()`）时随实例一起丢弃，不留下跨页面的静态状态。

### 3.3 解析与去重

- `code != 0`、缺 `data`、缺 `items`、JSON 为 null 一律返回空批；
- 非视频卡（`goto` 不是 `av` / `vertical_av`、横幅卡、缺 `aid`）由既有 `ShortVideoItem.fromFeedItem` 过滤；
- **明确横屏卡片跳过**：有 `dimension`、`width > height`、`rotate == 0` 三者同时成立才判定为横屏；缺字段、宽高非正或 `rotate != 0` 一律保留，避免字段变化时误删竖屏内容；
- **跨批去重**：批内按 `aid` 去重，并与已返回过的 `aid` 集合比对。服务端换批时偶尔会重复下发同一视频，直接在解析层丢掉，避免播放页出现连续两条相同内容。

### 3.4 降级池

部分情况下服务端下发的 `track_id` 以 `gateway_fallback_` 开头，表示这批内容来自降级通道（推荐质量明显更泛）。处理策略：

- 该批内容**照常播放**，不打断用户；
- 只记一条日志（每个会话至多一次），便于排查「内容为什么变泛」；
- 降级通道的 `track_id` **不回传**，避免把后续请求钉死在降级链路上。

### 3.5 播放清晰度跟随设置

列表取流参数里的 `qn` 只影响「卡片元数据」，真正决定播放分辨率的是逐条调用的 `x/player/wbi/playurl`。这部分约定：

- `ShortVideoFeedApi.fetchVideoUrl` 的 `qn` 默认值改为读 `SettingsKeys.PLAY_QN`（即「视频设置 → 分辨率」：360P=16 / 720P=64 / 1080P=80），老用户没有该键时回退 16；播放页与预加载两处调用**都不传该参数**，因此自动跟随设置。
- `qn` 只是首选值：登录态不足或稿件没有该清晰度时服务端会静默降级，因此用纯函数 `StoryFeedQuery.pickQuality(qn, qnValueList)` 从响应里的 `accept_quality` 还原实际档位（≤ 请求值的最高档；列表缺失时原样返回请求值），不一致时记日志。
- 同一页面内的 `item.videoUrl` 是会话内缓存，**改设置后需重新进入短视频页**才生效。

## 4. 验收标准

工程验收（本仓库无 CI，以本地 Gradle 为准）：

1. `./gradlew.bat :app:assembleDebug` 通过（`standard` 与 `coexist` 两个 flavor 都编）。
2. `./gradlew.bat :app:testStandardDebugUnitTest` 与 `:app:testCoexistDebugUnitTest` 全绿。
3. 新增单测覆盖：首屏/翻页的 `pull` 与 `display_id`、`trackid` 回传与降级时不回传、不发 `from` 三件套、百分号编码、降级判定、横屏过滤、跨批去重、`code != 0` 与字段缺失。

功能验收（**待真机**：本会话无可用设备，交付后需在手表上确认）：

4. 进入短视频页能正常出首屏内容，上下滑能持续加载，不出现连续两条相同视频。
5. 反复退出再进入短视频页，首屏内容正常（不因历史页码导致空列表）。
6. 弱网 / 接口异常时页面给出「获取视频列表失败」并可通过页面既有入口重试，重试成功后正常播放。
7. 解析层跳过横屏卡片后，短视频页不再出现上下黑边明显的横屏内容；后续若接口字段变化（缺 `dimension`），竖屏内容不受影响。
8. 把「视频设置 → 分辨率」从 360P 改到 720P/1080P 后重新进入短视频页，画面分辨率确实提高（可在日志里看到 `qn=` 变化）；未登录时降级属预期。

## 5. 回滚

改动集中在 `StoryFeedQuery.kt`、`ShortVideoFeedApi.kt` 的取流与清晰度部分与 `VideoPreloadManager.kt` 的两处调用；回滚只需还原这三个文件（`fetchVideoUrl` 的签名未变，播放页无感知）。

## 6. 26.10.21 第二轮修订：相关推荐续接与播放页手势

用户同日追加需求（原话见 `docs/review/fix-progress.md` §3.46）：① 推荐更像 B 站、少重复 / 横屏 / 低质，到底后能接着看相关推荐；② 操作向手机版靠拢——只加「右滑进 UP 主主页 / 左滑返回」，其余手势与底栏保持原样，播完自动接下一条。

### 6.1 取流侧

- **请求头补指纹**：`ShortVideoFeedApi.buildHeaders()` 追加 `Buvid`（取自 Cookie 的 `buvid3`，没有就不发）与 `Display-ID`（`util/DeviceIdentity.kt` 生成的 32 位大写十六进制、持久化复用）。桌面报告实测：缺指纹会落到降级池，补上后兴趣命中明显提高。
- **池参数**：`StoryFeedQuery.buildParams(...)` 新增 `request_from`（0..6，默认 0，越界夹取）；本轮不暴露给用户改，留作后续「换一批风格」的接口。
- **跨会话去重**：新增 `api/StorySeenStore.kt`，把最近 300 条已消费 `aid` 存进 SharedPreferences（`short_video_seen_aids`），每次取流成功后合并写回；翻页解析时与它比对，避免退出再进重复看到同样内容。
- **相关推荐**：新增 `StoryFeedQuery.PATH_RELATE = "/x/v2/feed/index/relate/story"`、`buildRelatedParams(anchorAid, ...)` 与 `parseRelated(...)`（复用主信息流解析与横屏过滤）；`StoryFeedPager.fetchRelated(anchorAid)` 负责请求与去重记账；`VideoPreloadManager.loadRelated()` 把结果追加到列表尾部并通过 `onRelatedLoaded` 回主线程。

### 6.2 播放页侧

- **关闭系统右滑返回**：`BaseActivity` 新增可覆写属性 `forceNoSwipeTheme`（默认 false），并入原本的 `SettingsKeys.BACK_DISABLE` 判断；`ShortVideoPlayerActivity` 覆写为 true。不能直接在子类 `super.onCreate` 前再 `setTheme`（会被覆盖）。
- **左右滑**：`PageHolder` 的 `GestureDetector` 增加 `onFling`——横向位移占优且超过 24dp 才拦：右滑 `openAuthorPage()`（`UserInfoActivity` + `mid`），左滑 `activity.onSwipeLeftToBack()`（暂停后 finish）。使用 `onFling` 而非 `onScroll` 是为了让横向手势与 ViewPager2 的纵向滑动互不抢；缩放中（`isScaling`）忽略横滑。
- **播完自动续播**：`setOnCompletion` 不再 `seekTo(0)` 循环，改为 `activity.onVideoCompleted(boundPosition)`：列表还有下一条就 `setCurrentItem(next)`；到底则以当前 `aid` 为锚点 `loadRelated()`，结果一到就自动切到追加的第一条；拉不到（空列表）提示「没有更多短视频了」并停下。`pendingAdvanceAfterRelated` 标记防止用户中途手动翻页后又被自动切走。

### 6.3 验收

- 单测：`StoryFeedQueryTest` 新增 `request_from` 夹取 / `buildRelatedParams` / `parseRelated` 去重与过滤；`StorySeenStoreTest` 覆盖合并去重、300 条上限、编解码容错。
- 真机待验：右滑进主页、左滑返回（且系统右滑不再直接退出）、播完自动下一条、到底后相关推荐能加载并自动接上、拉不到时提示且不循环。

> §6.2 的「右滑进 UP 主页 / 左滑返回」在真机上被用户反馈为**闪退**，且需求改为与手机版一致的横滑进详情，见 §7。

## 7. 26.10.21 第三轮修订：横向四页结构（短视频 → 详情 → 评论 → 相关推荐）

用户原话："滑动直接闪退，而不是进入主页，建议滑动后视频详情页面做到右边，像搜索一样连起来，连贯一些，第一页，短视频，第二页到第四页，原有的视频详情卡片，不懂的问我"。追问确认：第 2 页视频详情、第 3 页评论、第 4 页相关推荐；手势**完全按手机版**（单击显隐底栏、双击播放/暂停、长按详情、上下滑切视频、横滑进右页）；短视频页**右滑退出**；详情页右滑退回短视频；相关推荐返回后才自动切下一条，失败则停；相关推荐**复用既有「视频详情页相关推荐」设置开关**。

### 7.1 结构调整

- `activity_short_video_player.xml` 从「竖向短视频流」改成**横向 `ViewPager2`（`pager`）**；短视频流移到 `fragment_short_video_feed.xml`。
- 新增 `ShortVideoFeedFragment`：承接原 Activity 的全部播放逻辑（取流、预加载、竖向 pager、播放器、弹幕、播完续接）。关键点：页内 `itemView.setOnTouchListener` 必须返回 **false**，把事件交回外壳 pager，横滑才能翻页；`onPause` 暂停 / `onResume` 续播由 Fragment 生命周期自然驱动。
- 新增 `ShortVideoDetailPageFragment` + `fragment_short_video_detail_page.xml`：右侧三页的壳，按当前 `aid` 在 `onViewCreated` 建 `VideoInfoFragment` / `ReplyFragment` / `VideoRcmdFragment`。因为这三个 Fragment 的 aid 在创建时定死，外层用 `ShortVideoShellAdapter.getItemId = detailAid * 10 + position` 让 aid 变化时整页重建。
- `ShortVideoShellAdapter`（`androidx.viewpager2.adapter.FragmentStateAdapter`）：第 0 页 id 恒 0（翻视频不重建），`offscreenPageLimit = itemCount` 常驻；外壳只负责 `onFeedAnchorChanged()` / `currentShortVideo()` / 转发播放完成。
- 返回键：右侧页先回第 0 页，再退出。第 0 页右滑退出的实现后来在 §8 改成了 `SwipePageLayout`，不再是 `Activity.dispatchTouchEvent`。

### 7.2 旧手势路径的处理

页内 `onFling`、`openAuthorPage()` 与 `UserInfoActivity` 跳转**整段删除**。闪退根因未定位（本机无设备、用户未提供 logcat），删掉该路径即规避；横向手势全部由外壳 pager 承担，页面内不再抢手势。

### 7.3 验收

- 构建：`:app:assembleDebug` 两 flavor 通过。
- 真机待验（全部未验，无设备）：横滑到详情/评论/相关推荐、详情页跟随当前视频、返回键与右滑退出的层级、详情页三个复用 Fragment 在非 `VideoInfoActivity` 宿主下的表现、闪退是否消失。

## 8. 26.10.21 第四轮修订：横滑判向改由页面自己负责（修误触发）

用户反馈："误触发左右滑动的概率极高，所有滑动都会触发进入视频详情，且这些界面并没有真正连接到一起，而是跳转类似的"。

### 8.1 根因

上一版把横滑全部交给外壳 `ViewPager2`。它判「横向」的门槛是 `dx > dy`（1:1），竖向刷短视频时手指稍斜就被 intercept 成翻页；页面层没有否决权，于是「几乎每次滑动都翻页」。

### 8.2 实现（`ui/widget/SwipePageLayout`）

- `pager.isUserInputEnabled = false`：pager 不再自己接管触摸（反编译 `viewpager2-1.1.0` 确认 `RecyclerViewImpl.onInterceptTouchEvent/onTouchEvent` 都以该开关为闸门），但事件仍正常分发给页面内容。
- 每页根布局换成 `SwipePageLayout`（继承 `FrameLayout`，不是 `ViewGroup`，保证布局中的 `layout_gravity` 等属性仍然生效）。它只在「方向明确横向」时拦截：超过 `touchSlop` 后 `|dx| > |dy| * 2`；多指手势（捏合缩放）不参与。
- 拦截后用 `beginFakeDrag()` / `fakeDragBy(ev.x - lastX)` 让页面**跟手**移动，松手 `endFakeDrag()` 由 pager 吸附。方向已由反编译确认：`FakeDrag.fakeDragBy(f)` 内部先 `mRequestedDragDistance -= f`，再 `RecyclerView.scrollBy(round(requested - actual), 0)`；`scrollBy` 正数才是下一页，所以**传负值 = 下一页**（手指左移时 `ev.x - lastX` 正好为负，直接用这个差值。§9 修正了本节的符号说明）。
- 松手时若已超过屏宽 18%、pager 却没吸附过去，补一次 `setCurrentItem`，避免「滑了不翻页」。
- 第 0 页向右滑：整页 `translationX` 跟手位移，过阈值后整页滑出再 `finish()`；退出判定必须比对**按下时**所在的页（`startItem == 0`），否则从详情页滑回首页会被误判成退出。
- Activity 侧只剩 `shellPager()` 与 `handleSwipe(direction)` 两个入口：`-1` = 手指向左翻下一页，`+1` = 手指向右翻上一页，`target < 0` 即退出。原 `dispatchTouchEvent` + `swipeExitDetector` 已删除。

### 8.3 验收

- 构建：`:app:assembleDebug` 两 flavor 通过；JVM 单测 71 类 / 759 例 / 0 失败（两 flavor 同）。
- 真机待验：竖向刷视频不再被误判成横滑；横滑跟手；详情页之间连续拖动；第 0 页右滑有滑出动画再退出。

## 9. 26.10.21 第五轮修订：横滑方向与跟手修正、长按改 3 倍速

> 本节记录的改动已在真机（MuMu，`emulator-5554`）逐项验证，见 §9.3。

### 9.1 横滑方向反转与「不跟手」

- 现象：① 拖动过程中页面完全不动，松手才跳页；② 从详情页往右滑，方向与实际手指相反；③ 一次滑动偶尔翻两页。
- 根因：`FakeDrag.fakeDragBy(f)` 是 `mRequestedDragDistance -= f` 后再 `RecyclerView.scrollBy(requested - actual, 0)`，正数才是下一页，**所以传负值才是下一页**。旧代码传的是 `lastX - ev.x`（手指左移为正），符号正好相反：第 0 页向左滑时 `scrollBy` 拿到负值、又没有更左的页，被 clamp 成 0，于是「怎么拖都不动」；到详情页等中间页时则会往反方向滚，出现「方向和手指相反」。
- 修正：改传 `ev.x - lastX`。
- 「偶尔翻两页」：`endFakeDrag()` 的吸附是**异步**的，旧代码在它之后立刻读 `currentItem`（此时还是按下时的页），于是又补一次 `handleSwipe`。现在改为松手后 `postDelayed(150ms)` 再比对，只有 pager 确实没吸附过去才补一次。

### 9.2 长按改为 3 倍速（用户要求）

- 长按不再打开视频详情页（详情页已作为横向第 2 页存在，右滑即可到达），改为**按住期间 3 倍速、松手还原**。
- 实现：`GestureDetector.onLongPress` → `PageHolder.startSpeedBoost()`；`ACTION_UP` / `ACTION_CANCEL` / 第二根手指按下（开始捏合）→ `endSpeedBoost()`。只改播放器倍速与底部倍速按钮文案，不动 `currentSpeed`（用户选的档位要保持）；按住时用原有的音量提示位 `showsound` 显示「3.0x 倍速播放中」（`R.string.text_shortvideoplayer_speed_boost`）。
- 教程与功能表同步：`Tutorials.kt` 的 `shortVideo` 教程 `version` 3→4、长按条目改为「按住时 3 倍速播放，松手恢复正常倍速」；`res/xml/tutorial_short_video.xml` 同步。

### 9.3 真机验证（`emulator-5554`，release 包覆盖安装，保留登录态）

- 拖动中截图可见短视频页左移、详情页从右侧进入 —— 跟手成立。
- 短视频页左滑一次 → 详情页（page 1）；再左滑 → 评论（page 2）；再左滑 → 相关推荐（page 3）；最后页再左滑不再前进。
- 详情页右滑 → 回到短视频页（方向正确，与手指一致）；短视频页右滑 → 退出本页。
- 单击、上下滑不再误跳 `VideoInfoActivity`（上一轮 `setOnTouchListener` 返回值问题已修）。

### 9.4 拖动增量按帧合并（修「一卡一卡」）

- 用户反馈详情页横滑「没有那么丝滑，一卡一卡的」。
- 触摸采样率（120~240Hz）常高于帧率，原来每个 `ACTION_MOVE` 都直接 `fakeDragBy`，一帧内多次 `RecyclerView.scrollBy`，每次都触发两侧页面量算/绘制，弱机上表现为一格一格地动。
- `SwipePageLayout` 改为：MOVE 只把增量累加到 `pendingDrag`，用 `ViewCompat.postOnAnimation` 在下一帧统一 `fakeDragBy` 一次；松手/取消/`onDetachedFromWindow` 时 `flushDragNow()` 先提交残余量再 `endFakeDrag()`，保证最后几像素不被吞。
- 本机（x86 MuMu）`dumpsys gfxinfo`、`Choreographer` 均无掉帧记录，无法复现用户手机上的卡顿，因此这属于按成因做的优化，需用户真机复验手感。
