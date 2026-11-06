# 与 BiliTerminal-Revival（jiabenguiyin）分叉对比报告

- 报告日期：2026-11-06
- 我方基线：`D:\Users\ASUS\Desktop\DEVELOP\ReBiliClient`（main，HEAD `1c404c3`，版本 26.11.06.1，minSdk 19 / targetSdk 34 / compileSdk 34）
- 对方基线：`D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\BiliTerminal-Revival`（`jiabenguiyin/BiliTerminal-Revival`，main `60d4559`，2026-11-04 10:08 +0800，版本 1.2.4 / versionCode 2026110401，tag `v1.2.4-2026110401`）
- 扫描范围：两仓库全部主源码、资源、清单、构建脚本、CI、测试、文档；预计算差异清单（git blob 哈希）作线索，结论一律回到源码核实
- 一句话结论：**两个仓库是同上游的两代兄弟分支——我方基于上游 2.7.0 做了 Kotlin 重写，对方基于上游 2.9.5-fix 继续做 Java 维护。我方在工程规范、测试数量、功能面宽度（外观主题、伪装、短视频、笔记、投票、教程、下载工程）上领先；对方在「播放器内核、连播队列、存储管理、设备档位、举报/关系管理、创作中心稿件、大图查看」这些用户直接可感知的点上更完整，其中约一半应视为「上游 2.7→2.9.5 的增量我方从未吸收」，而不是复活版原创。**

## 0. 报告约定

### 0.1 路径前缀与证据标记

- `[F]` = 对方仓库根 `D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\BiliTerminal-Revival`，路径相对该根。
- `[O]` = 我方仓库根 `D:\Users\ASUS\Desktop\DEVELOP\ReBiliClient`，路径相对该根。
- 每条结论标 **【事实】**（代码/命令可直接复核）或 **【推测】**（由代码推得、未真机验证）。行号均为复核过的真实行号，引用为「起始行-结束行」。
- 难度口径：**S** ≤ 1 人日且不动架构；**M** 1–3 人日或需一处跨层改造；**L** > 3 人日或需新依赖/新存储抽象/多模块联动。

### 0.2 重要前提：基线代差

对方 `res/values/strings.xml:6` 自述「基于 BiliClient v2.9.5-fix 继续维护」，我方 `readme.md` 自述基于上游 2.7.0。**【事实】** 这意味着：

- 对方很多「独有功能」可能来自上游 2.9.x，复活版只在跟版本；但对方只有 13 个压缩提交（`git rev-list --count` = 13），无法用 git 区分「上游带的」与「复活版自创的」——本报告凡涉及归属的地方标 **【推测】**，涉及「对方代码里确实存在」的地方标 **【事实】**。
- 移植时应按我方 Kotlin 架构重写，**不可整包拷 Java**：对方是纯 Java（384 个 `.java`、0 个 `.kt`），我方是 Kotlin 为主（354 `.kt` / 141 `.java`）；且对方 minSdk 14 / targetSdk 26 的用法规格与我方 minSdk 19 / targetSdk 34 不同（`[F] app/build.gradle:31-35` vs `[O] app/build.gradle:42-46`）。

## 1. 身份与基线

### 1.1 对方是谁

| 项 | 内容 | 证据 |
| --- | --- | --- |
| 名称 | 哔哩终端复活版 / BiliTerminal Revival | `[F] readme.md:1-6` |
| 定位 | 为智能手表、旧手机、低性能 Android 设备继续维护的轻量 B 站客户端 | `[F] readme.md:28-30` |
| 上游 | BiliClient v2.9.5-fix（Gitee `RobinNotBad/BiliClient`） | `[F] res/values/strings.xml:6,173` |
| 包名 | `com.RobinNotBad.BiliClient`（**未改名，与官方/其它分叉互斥安装**） | `[F] app/build.gradle:29` |
| 应用名/是否共存 | 无 flavor；测试版只给 versionName 加 `-BETA` 后缀，与正式版同一包名，不能共存 | `[F] app/build.gradle:37-39` |
| 许可 | 代码 GPL-3.0；UI 布局沿用原项目 CC BY-NC-SA | `[F] LICENSE:1-2`、`[F] res/values/strings.xml:7` |
| 第三方致谢 | 明确致谢 `cyq114514/Re-BiliTerminal`（`SearchBangumiFragment.java`/`SearchApi.java` 改编）与 `subsampling-scale-image-view` | `[F] THIRD_PARTY_NOTICES.md:3-20`、`:23-28` |
| 发布渠道 | 官网 `jp.031030.xyz` 下载，GitHub 只放源码/构建记录；另有维护者私有中继服务 | `[F] readme.md:24,42`、`[F] util/NetWorkUtil.java:59-67` |
| 发布节奏 | 3 个 tag：v1.2.2（2026102001）、v1.2.3（2026103003）、v1.2.4（2026110401），约 1–2 周一个版本 | `git -C <F> tag`；`[F] app/build.gradle:10-11` |
| 目标设备 | README 写 Android 4.0.4（API 15）+；构建实际 `minSdk 14`（Android 4.0） | `[F] readme.md:69`、`[F] app/build.gradle:31` |
| 测试 | JVM 单测 71 文件 / 415 个 `@Test`；仪器化测试 60 文件（含视频/音频/GIF 测试资产） | `app/src/test`、`app/src/androidTest` 文件计数 |

### 1.2 README 宣称逐条核实（节选）

| README 宣称 | 代码核实 | 结论 |
| --- | --- | --- |
| 内置视频、直播和音频播放器（`readme.md:36`） | ijkplayer + ExoPlayer 双播放栈（`app/build.gradle:145,147`）、直播 `api/LiveApi.java`、听视频/仅音频下载 | **成立** |
| 下载支持暂停、继续、断点续传（`readme.md:38`） | `service/DownloadService.java:58,79,525-546`：暂停标志 + HTTP Range + 416 保留进度 | **成立** |
| 互动视频保存进度和分支状态（`readme.md:39`） | `util/InteractionProgressStore.java:14`，播放页 `PlayerActivity.java:679,6858` 读写 | **成立** |
| 播放器支持屏幕锁定、亮度/音量手势、圆角安全区（`readme.md:40`） | `player/PlayerSafeAreaPolicy.java:4`、`player/PlaybackVolume.java:3`、设置项文案 `res/values/strings.xml:131` | **成立** |
| Android 5.0+ 可用 SAF 外置 SD 卡目录（`readme.md:41`） | `util/VideoStorageUtil.java:24-80`、`SAF_PATCH_NOTES.md:1-30`、`app/build.gradle:127` documentfile | **成立** |
| 默认直连、异常时按配置切兼容中继（`readme.md:42`） | `util/NetWorkUtil.java:59-67` 硬编码 `https://jp.031030.xyz/bili-relay`，token 由服务端下发 | **成立，但这是设计坑（见第 4 章）** |

## 2. 量化对比

| 指标 | 我方 `[O]` | 对方 `[F]` |
| --- | --- | --- |
| 主源码语言 | Kotlin 354 + Java 141 | 纯 Java 384 |
| 主源码行数 | 66,613 + 24,913 = 91,526 | 63,076 |
| 布局文件 | 152 | 106 |
| JVM 单测 | 134 文件 / 1,429 `@Test` | 71 文件 / 415 `@Test` |
| 仪器化测试 | 0 | 60 文件（含媒体资产） |
| minSdk / targetSdk / compileSdk | 19 / 34 / 34 | 14 / 26 / 33 |
| ABI | arm64-v8a、armeabi-v7a、x86（`app/libs/`） | armeabi-v7a、x86（mips 为空占位；`app/build.gradle:48-53`） |
| 应用形态 | standard + coexist（可共存安装） | 单一包名，与官方互斥 |
| 主要第三方 | OkHttp 4.12、Glide 4.16、jsoup 1.16.1、PhotoView 2.3.0、EventBus 3.3.1 | OkHttp 3.12.13、Glide 4.13.2、jsoup 1.10.2、PhotoView 2.3.0、ExoPlayer 2.18.7、subsampling 3.10.0、protobuf-javalite、commons-compress |
| 播放/弹幕栈 | ijkplayer-java + DanmakuFlameMaster + brotlij（本地模块） | 同上 + exoplayer-core |

结论：**我方不是「功能更少的小弟」**——文件数、行数、测试数、依赖现代度都更高；差异集中在若干具体模块的完成度，见第 3 章。

## 3. 功能增量（对方有 / 我方无或明显更弱）

> 阅读方式：每条给出「用户价值 → 对方证据 → 我方现状（已核实）→ 难度 → 风险」。我方可对等的项不重复列，统一见 3.12。

### 3.1 播放器内核与连播

| 功能 | 用户价值（一句话） | 对方证据 | 我方现状 | 难度 |
| --- | --- | --- | --- | --- |
| **ExoPlayer 单时间轴 DASH 播放** | 1080P 及以上不再「视频一路、音频一路」，拖动/倍速/暂停时不再音画漂移 | `[F] player/ExoDashMediaPlayer.java:42-49`（注释明说「一个 ExoPlayer 时间轴拥有两轨，避免旧的双播放器漂移路径」）；`[F] player/UnifiedDashPlayerFactory.java:19-27`（反射加载，Android 4 设备自动回落）；`[F] app/build.gradle:147` exoplayer-core 2.18.7；`[F] activity/player/PlayerActivity.java:469` 按设备档位选择 | **无（未用 ExoPlayer）**。我方 DASH 是「ijk 播视频 + `android.media.MediaPlayer` 播外挂音轨」双播放器：`[O] activity/player/PlayerActivity.kt:2913-2922`（音量要打两路）、`:2058`（音轨偏差过大再对齐）、`:3859-3881`（seek 只 seek 视频会让音轨留在旧位置）；取流侧也明说「1080P 以上必须起两路播放器」`[O] player/PlayerStreamPolicy.kt:9-10` | L |
| **连播队列：UP 主投稿 / 视频合集 / 在线收藏夹 / 缓存歌单** | 在一个播放页里连续看完整个合集/收藏夹，不用退出再点 | `[F] player/CollectionPlaybackQueue.java:8-23`（SEQUENTIAL/SHUFFLE/REPEAT_ONE/REVERSE + 去重 + 历史栈）；`[F] player/CollectionPlaybackSource.java:15`、`[F] api/FavoritePlaybackSource.java:13`、`[F] api/UploaderPlaybackSource.java:11`（三类来源统一抽象）；`[F] player/CachedPlaybackSnapshot.java`（进程被杀后靠 token 恢复队列）；`[F] player/PlaybackResumePoints.java:6`（进度续播） | **部分**：我方只有「虚拟合集」= 收藏夹/本地缓存整夹连播、顺序播放，且状态塞在播放页里：`[O] util/VirtualCollectionPrefs.kt:4-19`、`[O] activity/player/PlayerActivity.kt:560-624,4822-4824`；**没有** UP 主投稿连播、视频合集连播（`[O] activity/video/collection/CollectionInfoActivity.kt` 不直接起播放器）、没有队列级 token 恢复 | M |
| **播放顺序：顺序 / 倒序 / 随机 / 单曲循环** | 合集连播可随机、可倒着看、可单曲循环 | `[F] player/CollectionPlaybackQueue.java:8-12,39-53`（四种模式 + `nextMode` 循环切换）；`[F] res/values/strings.xml:263` 变更日志 | **弱**：我方只有「自动连播」「循环播放当前视频」两个开关（`[O] activity/settings/SettingTerminalPlayerActivity.kt:102-107`），合集内没有模式切换 | S |
| **耳机/蓝牙按键跨条目切歌** | 手表戴着耳机切上/下一条不用掏屏幕 | `[F] activity/player/PlayerMediaButtonReceiver.java`（`PlayerMediaSessionController.java`/`…Legacy.java`/`…Api21.java` 按 API 分实现）；`[F] res/values/strings.xml:263` | **部分**：我方 MediaSession 的 `onSkipToNext` 只在「多 P 视频内」生效，不跨连播条目：`[O] activity/player/PlayerActivity.kt:3475-3490` | S |
| **长按后上/下划直接切上一条/下一条视频** | 竖屏短视频式浏览，省掉回列表 | `[F] player/LongPressSwitchGesture.java:4-26`（长按计时器触发后识别一次纵向快滑，含速度/横向容差判据）；`[F] res/values/strings.xml:264` | **无**：我方长按只有倍速（`[O] activity/settings/SettingTerminalPlayerActivity.kt:64-74`），横滑只有进度（`[O] activity/player/PlayerGesturePolicies.kt:27-30`） | S |
| **长按倍速提示的尺寸/开关可调** | 小屏上提示别挡住画面 | `[F] util/PlayerSettingsUtil.java:15-16,73`（`player_longpress_hint_enabled` / `…_scale`，带 JVM 单测 `LongPressHintSettingsTest`） | **弱**：我方有长按倍速开关与倍率（`[O] activity/settings/SettingTerminalPlayerActivity.kt:64-74`），没有提示层尺寸调节 | S |

### 3.2 弹幕

| 功能 | 用户价值 | 对方证据 | 我方现状 | 难度 |
| --- | --- | --- | --- | --- |
| **弹幕按播放窗口分段加载** | 长视频/低内存表不会一次性把整段弹幕塞进内存、也不会卡在解析 | `[F] player/DanmakuWindowLoader.java:14-45`（360 秒一段、单个串行 worker、内存与请求都按窗口有界、失败 15s 重试、可取消）；`[F] activity/player/PlayerActivity.java:3384` 传窗口半径 | **弱**：我方一次性取全部分段并全量交给 DFM：`[O] api/DanmakuApi.java:78,89,142`（`SEGMENT_SECONDS`/`segmentCountFor`/`getAllVideoDanmaku`）、`[O] player/DanmakuManager.kt:139`（`loadFromProtobufSegments` 一次塞完）；无窗口/取消/重试概念 | M |
| **弹幕数量保护（>5000 自动不加载）** | 弹幕海量视频不卡死，仍可手动开 | `[F] player/DanmakuLoadPolicy.java:5-41`（`AUTO_LOAD_LIMIT=5000`、手动优先、先查计数）；`[F] util/DanmakuXmlCounter.java:10-16`（流式计数，超阈值立即停止解析） | **无**：我方无数量阈值/先查计数逻辑（grep 无 5000 判据） | S |
| **本人弹幕边框** | 一眼找到自己发的弹幕 | `[F] DanmakuFlameMaster/src/main/java/master/flame/danmaku/danmaku/util/SelfDanmakuStyle.java:5-20`（对登录 mid 做 CRC32 比对，只比自己的 hash，不推断别人 UID）；`[F] activity/player/PlayerActivity.java:3613,3750` | **无**（grep 己方弹幕样式无命中） | S |
| **强制滚动弹幕（防顶部/底部遮挡）** | 打开后顶部/底部弹幕都按滚动显示，画面更干净 | `[F] res/values/strings.xml:145`（`desc_danmaku_force_r2l`） | **无**：我方弹幕状态只有行数/速度/字号/透明度/合并/重叠（`[O] player/DanmakuManager.kt:30-39`） | S |
| **直播弹幕显示发送者昵称** | 直播聊天弹幕知道谁在说 | `[F] res/values/strings.xml:146`（`desc_danmaku_showsender`） | **无**（我方直播弹幕无昵称选项） | S |
| **直播弹幕时钟对齐** | 直播弹幕与画面时间轴对齐，不靠墙钟飘 | `[F] util/DanmakuClock.java`；`[F] androidTest/.../LiveDanmakuTimingInstrumentedTest.java` | **弱**：我方注释明确「直播没有有意义的播放位置，不要绑媒体时钟」——直播弹幕走墙钟（`[O] player/DanmakuManager.kt:72`） | S/M |

### 3.3 视频 / 番剧 / 互动

| 功能 | 用户价值 | 对方证据 | 我方现状 | 难度 |
| --- | --- | --- | --- | --- |
| **互动视频进度与分支状态持久化** | 退出后回到同一分支继续，不用重走选项 | `[F] util/InteractionProgressStore.java:14-113`（graphVersion/edgeId/隐藏变量快照）；`[F] activity/player/PlayerActivity.java:679,6858` | **弱**：我方互动视频实现完整（选项/变量/条件/跳转），但 `currentEdgeId` 只在内存与 Intent 里（`[O] activity/player/PlayerActivity.kt:641,814,5039`），无落盘快照 | S |
| **视频合集/UP 主投稿自动续播** | 见 3.1 连播队列 | 同 3.1 | 同 3.1 | M |

> 番剧片头片尾跳过、同系列切季、从历史定位上次观看的集：我方**已具备且实现更细**（`[O] player/SkipOpEdPrefs.kt`、`[O] activity/video/info/BangumiInfoFragment.kt:530-570,754-788` 的「各季选中位置记忆 + 按 epid 定位」），不计入增量。

### 3.4 搜索

对方 `SearchBangumiFragment.java`/`SearchApi.java` 系从 `cyq114514/Re-BiliTerminal` 改编（`[F] THIRD_PARTY_NOTICES.md:3-13`），而我方已有同名能力且更细：分类 fragment（`[O] activity/search/SearchBangumiFragment.kt`、`SearchLiveFragment`、`SearchArticleFragment`、`SearchUserFragment`）、排序选项与风控降级（`[O] activity/search/SearchSortOptions.kt`、`[O] api/SearchApi.java:245-268` 的 `RiskControlException`）。**结论：搜索域无增量，我方不弱。**

### 3.5 动态 / 评论 / 私信 / 关注关系

| 功能 | 用户价值 | 对方证据 | 我方现状 | 难度 |
| --- | --- | --- | --- | --- |
| **视频举报** | 遇到违规/引战稿件可就地举报，含官方投诉页兜底 | `[F] api/VideoReportApi.java:13,24-38`（原因列表解析、官方 tag 接口、500 字限制、官方投诉 URL 兜底）；`[F] ui/VideoReportController.java:14`；入口 `[F] activity/video/info/VideoInfoFragment.java:216-218` | **无**：我方公开侧无视频举报 API/入口（grep `appeal`、`x/v2/report` 均无业务命中） | S |
| **动态举报** | 动态流里直接举报 | `[F] api/DynamicReportApi.java:13`；`[F] ui/DynamicReportController.java:14`；入口 `[F] adapter/dynamic/DynamicHolder.java:76-90,228-234` | **无** | S |
| **关注/取关/拉黑/解除 统一动作** | 用户页、列表里一次点击完成关系操作，带并发防抖与确认 | `[F] api/UserRelationApi.java:39-52`（`x/relation/modify`，action 1/2/5/6）；`[F] util/FollowRelationUtil.java:12-22,31-49`（attribute 位解析、按钮文案）；`[F] ui/UserRelationActions.java:18-50`（同一 Activity 只开一个弹窗、防重复提交） | **弱**：我方能关注/取关（`[O] api/UserInfoApi.java:205-212`，仅 act=1/2），**没有拉黑/解除拉黑**；关注分组管理反而比对方强（`[O] activity/user/FollowUsersActivity.kt:34,73,106-114` 组增删改/移人） | S/M |

### 3.6 个人空间 / 收藏 / 创作中心

| 功能 | 用户价值 | 对方证据 | 我方现状 | 难度 |
| --- | --- | --- | --- | --- |
| **创作中心：本人稿件列表 / 数据 / 删除** | UP 主在手表上管理自己发的视频 | `[F] api/CreatorArchiveApi.java:14-130`（`member.bilibili.com` 会话、统计、分页稿件、`OwnedArchive`）；`[F] activity/user/CreatorArchiveActivity.java:21,110`（「删除本人稿件」） | **弱**：我方只有统计数字页，无稿件列表/删除：`[O] activity/user/CreativeCenterActivity.kt:27-28`（只调 `getVideoStat`/`getBeUPTime`，API 文件仅这两个方法） | M |
| **历史记录多选删除** | 一次清掉一堆观看记录，不用逐条长按 | `[F] activity/user/HistoryActivity.java:33-61`（长按菜单「删除这条 / 多选删除」、`DeleteSession` 授权、单批最多 100 条） | **弱**：我方只有单条长按删除：`[O] activity/user/HistoryActivity.kt:46`（`deleteHistory` 一次一条） | S |
| **收藏视频排序 / 收藏夹内多选删除** | 按最近收藏/播放量/投稿时间排；批量取消收藏 | 对方 `[F] util/FavoriteVideoOrder.java:7-22`（3 种排序 + 按账号/收藏夹记忆） | **已对等且更强**：我方服务端排序 + 多选删除 + 长按菜单（`[O] activity/user/favorite/FavoriteVideoListActivity.kt:51,211-213,239-288,409-471`） | — |

### 3.7 直播

直播推荐/关注直播双方对等（我方 `[O] activity/live/` 三页，对方 `[F] activity/live/` 三页）。差异只在 3.2 的「直播弹幕时钟/昵称」。

### 3.8 下载 / 缓存 / 存储

| 功能 | 用户价值 | 对方证据 | 我方现状 | 难度 |
| --- | --- | --- | --- | --- |
| **SAF 外置 SD 卡缓存目录（Android 5+）** | 缓存写进可移动 SD 卡/任意授权目录，不占内置空间 | `[F] util/VideoStorageUtil.java:24-80`（file/SAF 双后端、持久授权、写探针、`.biliterminal_write_test`）；`[F] activity/settings/SettingStorageActivity.java:15-40`；`SAF_PATCH_NOTES.md:1-30`（下载库升 v5 存 storage_mode/ref、本地列表扫描 `content://`、断点续传）；`[F] app/build.gradle:127` documentfile | **无**：我方缓存目录只有文件路径（`[O] activity/settings/SettingGroupActivity.kt:589-592`「缓存路径」输入框、`[O] util/FileUtil.java` 路径式读写），无 DocumentFile 依赖与 `ACTION_OPEN_DOCUMENT_TREE` | L |
| **存储空间页（分类统计 + 一键清理）** | 一眼看到图片缓存/临时文件/更新包各占多少，一键清 | `[F] util/AppStorageManager.java:14-70`（图片/临时/APK/可用空间快照 + 有界清理 + 并发互斥）；`[F] activity/settings/SettingStorageActivity.java:15-40`；入口 `[F] activity/settings/SettingMainActivity.java:93` | **弱**：我方 `FileUtil.clearCache()` 已实现（`[O] util/FileUtil.java:49`）但**全仓无调用者**（grep 仅定义处命中），设置里只有「缓存路径」；用户没有任何清理入口 | S |
| **差分更新（BSDIFF40 补丁）** | 手表弱网/流量下只下几十 KB 的补丁 | `[F] util/BinaryPatchUtil.java:14-21`（BSDIFF40 + BZip2）；`[F] activity/update/UpdateInfoActivity.java:244` | **无**：我方全量 APK 下载（`[O] util/UpdateManager.kt:28,226-233`），有签名/包名校验（`[O] util/ApkVerifier.kt:1-30`） | L |

### 3.9 设置 / 交互 / 外观

| 功能 | 用户价值 | 对方证据 | 我方现状 | 难度 |
| --- | --- | --- | --- | --- |
| **移动数据 / Wi-Fi 独立默认画质** | 流量下自动降画质、Wi-Fi 下自动高清 | `[F] util/NetworkPlaybackQuality.java:9-19`（`play_qn` / `play_qn_mobile` / `play_qn_wifi` + 网络类型判定，含 VPN 与 API23 分支）；`[F] activity/settings/SettingPlayerChooseActivity.java:46-47`；`[F] activity/settings/SettingQualityActivity.java:29-77` | **无**：我方只有单一默认清晰度（`[O] activity/video/QualityChooserActivity.kt` / `PlayerDefaults`），grep「移动数据/WiFi 画质」无命中 | S |
| **大图查看：超大图/长图/动图，磁盘优先 + 分块解码 + 失败态** | 看长图/大图不 OOM、不白屏，动图正常播 | `[F] ui/widget/ImageViewerPage.java:44-60`（SubsamplingScaleImageView 分块解码 + PhotoView + GifDrawable + 按堆内存决策 + 状态文案）；`[F] app/build.gradle:136`；`[F] THIRD_PARTY_NOTICES.md:23-28` | **弱**：我方看图页只做 PhotoView + Glide 整图（`[O] activity/ImageViewerActivity.kt:24`），超大图/长图无保护，无失败态/超时文案 | M |
| **统一弹窗/搜索分类交互** | — | `[F] ui/TerminalDialogStyle.java`、`[F] activity/search/SearchActivity.java` | **对等**：我方 `[O] util/TerminalDialog.kt`、`[O] activity/search/SearchSortOptions.kt` | — |

### 3.10 启动 / 更新 / 公告

公告与版本元数据双方都有（我方 `[O] api/TerminalApi.java:733`、`[O] activity/settings/AnnouncementsActivity.kt`；对方 `activity/settings/AnnouncementsActivity.java` + 自建公告服务）。更新链差异见 3.8 差分更新与第 4 章安全取舍。

### 3.11 兼容与健壮性

| 功能 | 用户价值 | 对方证据 | 我方现状 | 难度 |
| --- | --- | --- | --- | --- |
| **playurl 双接口兜底（wbi → 旧接口；番剧 v2 → v1）** | WBI 风控/接口抖动时仍能播，而不是「解析失败」 | `[F] api/PlayerApi.java:116-117`（wbi + legacy 双 URL）、`:172-174`、`:351-357`、`requestPlayDataWithFallback`（`:410` 起，区分 requireDash）、`:694-700`（pgc v2 失败回退 v1） | **弱**：我方 WBI 单路径、失败即抛（`[O] api/PlayerApi.java:125,136`；番剧 `:348` 只走 `pgc/player/web/playurl`），有错误文案映射但无接口级回退 | S |
| **设备档位集中策略（COMPAT/BALANCED/HIGH_END）** | 低配表自动降画质/关 DASH/软解/放宽超时/弹幕限额，稳定优先 | `[F] util/DeviceProfile.java:11-24,148-211`（≤128MB、≤2 核、手表屏、SDK≤N_MR1 → COMPAT；COMPAT 自动 720P、不请求 4K、默认软解、关 DASH 双播放器、弹幕上限 2000、诊断批量变小）；接入点 `[F] api/PlayerApi.java:183-218`、`[F] activity/player/PlayerActivity.java:1865,1989-2000,3384,3559` | **弱**：我方 `[O] util/PerformanceManager.kt:12-18` 有高/中/低三档，但只用于图片质量、列表页大小、Glide 缓存、RecyclerView 预取（grep `PerformanceManager.` 30 处），**没有接取流与播放器超时**；取流对所有人都固定 `fnval=4048`（`[O] api/PlayerApi.java:129`） | M |
| **播放流诊断字段随反馈上报** | 反馈里带设备档位/取流明细，便于定位「某视频放不了」 | `[F] api/PlayerApi.java:204`、`[F] activity/player/PlayerActivity.java:2602,4364`（`streamDetails`/`device_profile`） | **对等**：我方有日志脱敏 + 反馈日志导出（`[O] util/LogSanitizer.kt`、`[O] util/LogExporter.kt`、`[O] util/CrashTrail.kt`），整体更偏隐私 | — |

### 3.12 已对等 / 我方不弱（避免误报，逐条核实过）

- 收藏视频排序、收藏夹多选删除：我方已有且更强（`[O] activity/user/favorite/FavoriteVideoListActivity.kt:51,211-213,239-288`）。
- 番剧片头片尾跳过、切季、按 epid 定位上次观看：我方已有（`[O] player/SkipOpEdPrefs.kt`、`[O] activity/video/info/BangumiInfoFragment.kt:359-362,530-570`）。
- 弹幕样式（字号/透明度/行数/速度/合并/重叠）：我方已有（`[O] player/DanmakuManager.kt:30-39`）。
- 长按倍速、倍率、横滑调进度、双击快进、缩放/移动、后台播放、听视频、字幕、屏幕方向、从历史位置播放：我方设置项齐全（`[O] activity/settings/SettingTerminalPlayerActivity.kt:64-138`）。
- 消息未读标记/新私信通知：我方有（`[O] util/MsgNotifier.kt:25-45`、`[O] util/SettingsKeys.kt:95`）。
- 图片 webp→jpg 兼容开关：双方都有（`[F] res/values/strings.xml:122` vs `[O] util/GlideUtil.java:36-49`、`[O] util/SettingsKeys.kt:87`）。
- touch feedback/ripple：双方都有（`[F] res/drawable-v21/background_button_compat.xml` vs `[O] res/values/themes.xml:31-32` 等）。
- 搜索风控降级：我方更细（`[O] api/SearchApi.java:245-268` `RiskControlException` + 排序按钮禁用态）。
- 互动视频本体（选项/变量/条件/跳转）：我方完整实现（`[O] api/InteractionVideoApi.java`、`[O] activity/player/PlayerActivity.kt:5014-5470`）。
- 直播推荐/关注直播、动态发布/话题、稍后再看、番剧订阅、视频合集、系列详情：双方对等。

### 3.13 我方独有优势（供排期时权衡，不展开）

外观/主题系统（`[O] app/src/main/java/.../ui/appearance/` 11 文件 + 多套 `values/themes*.xml`）、伪装（计算器/蓝牙伪装 `[O] activity/disguise/` 4 文件）、短视频流（`[O] activity/video/ShortVideo*`）、笔记（`[O] api/NoteApi.java` + `activity/note/`）、投票（`[O] api/VoteApi.java`）、订阅合集（`[O] activity/user/subscription/`）、教程系统（`[O] tutorial/` 6 文件）、下载工程（`[O] service/download/` 7 文件 + 分段/断点/WakeLock/Aria2）、圆屏与滚轮（`[O] ui/widget/Rotary*.kt`、`[O] util/view/RoundInsets.kt`）、ROM 残缺防御（`[O] util/ViewCapabilityProbe.kt`）、更新包签名校验（`[O] util/ApkVerifier.kt`）、日志脱敏（`[O] util/LogSanitizer.kt`）、standard/coexist 双 flavor（`[O] app/build.gradle:120-137`）。对方**没有**对应物。

## 4. 优化点（双方都有、对方做得更好）

| 主题 | 对方做法（证据） | 我方现状（证据） | 值不值得搬 | 判断 |
| --- | --- | --- | --- | --- |
| DASH 播放架构 | 单 ExoPlayer 时间轴统一音视频（`[F] player/ExoDashMediaPlayer.java:42-49`） | 双播放器 + 定期对齐（`[O] activity/player/PlayerActivity.kt:2058,3859-3881`） | **值得，收益最大** | 我方的漂移是结构性的：seek/倍速/暂停组合下只能靠「偏差过大再拉回」补偿；这是播放体验天花板问题，不是 bug |
| 取流容错 | wbi/legacy、pgc v2/v1 双路径（`[F] api/PlayerApi.java:116-117,694-700`） | 单路径失败即抛（`[O] api/PlayerApi.java:125,348`） | **值得，成本最低** | 纯增量代码 + 可单测，不改架构 |
| 弹幕内存模型 | 窗口加载 + 数量保护（`[F] player/DanmakuWindowLoader.java:14-45`、`DanmakuLoadPolicy.java:5-41`） | 全量取段全量解析（`[O] api/DanmakuApi.java:142`、`[O] player/DanmakuManager.kt:139`） | **值得** | 手表内存吃紧，长视频/番剧连播时最明显 |
| 连播队列 | 纯逻辑队列类 + 四种模式 + 历史栈 + token 快照（`[F] player/CollectionPlaybackQueue.java:8-70`、`CachedPlaybackSnapshot.java`） | 队列状态散在 Activity（`[O] activity/player/PlayerActivity.kt:560-624`），不可单测 | **值得** | 我方虚拟合集已有语义，抽类 + 补模式是低风险改造 |
| 历史批量删除 | 删除会话授权 + 多选 100/批（`[F] activity/user/HistoryActivity.java:33-61`） | 单条删除（`[O] activity/user/HistoryActivity.kt:46`） | **值得** | 纯 UI + 一次 API 调用 |
| 存储可观测/可清理 | 分类快照 + 有界清理（`[F] util/AppStorageManager.java:14-70`） | `clearCache` 死代码（`[O] util/FileUtil.java:49` 无调用） | **值得** | 低成本补一个用户长期缺失的入口 |
| 设备档位 | 档位集中 + 取流/播放器/弹幕全接入（`[F] util/DeviceProfile.java:11-24`） | 性能分档只影响图片/列表（`[O] util/PerformanceManager.kt:12-18`） | **值得，但需真机矩阵** | 手表目标场景收益高；可与 ExoPlayer 改造合并排期 |
| 大图渲染 | 分块解码 + 动图 + 失败态（`[F] ui/widget/ImageViewerPage.java:44-60`） | 整图 PhotoView（`[O] activity/ImageViewerActivity.kt:24`） | **值得，先无依赖方案** | 长图 OOM 是手表真实风险；但新依赖需按项目约定单独批准 |
| 测试策略 | 60 个仪器化测试 + 媒体测试资产（播放/弹幕/手势/大图/发布冒烟） | 0 仪器化，靠 adb 手测与 agent 指南（`[O] docs/review/agent-test-guide.md`） | **部分值得** | 不必搬 60 个，但「播放器/弹幕/大图」三组 UI 行为值得有 3–5 个回归用例 |
| 设置项组织 | 按「播放器选择/网络画质/存储/UI」分页（`[F] activity/settings/SettingPlayerChooseActivity.java:46-47` 等） | 单页 `SettingGroupActivity`（834 行）+ `SettingsIndex` 索引（`[O] activity/settings/SettingGroupActivity.kt`） | **不搬** | 我方已有索引式设置，改造成本高于收益 |

## 5. 对方设计坑 / 不建议照搬

1. **中继/浏览器代理绑定维护者私有服务。** `[F] util/NetWorkUtil.java:59-67` 硬编码 `https://jp.031030.xyz/bili-relay` 与运行时下发 token；`[F] activity/browser/BrowserRelayProxy.java:32-50` 把 B 站流量改道到该代理；视频详情页甚至有专门的「relay」按钮（`[F] activity/video/info/VideoInfoFragment.java:231`）。**这不满足我方「只走 https + 域名白名单 + 不引入集中式中间人」的红线**（`[O] docs/agents/security.md` 口径），也把可用性绑在第三方服务器上。**不可搬。**
2. **App 推荐流硬编码 B 站官方 App 密钥 + 伪装 UA + 账号绑定。** `[F] api/AppRecommendApi.java:30-35` 写死 `APP_KEY/APP_SECRET` 与 `BiliDroid/9161100` UA；`[F] activity/settings/AppRecommendBindingDialog.java:76` 引导「绑定 App 推荐账号」。属于凭据硬编码 + 模拟官方客户端 + 账号操作三重风险，**不可搬**。
3. **DeepSeek 问答只是半成品。** 实现只有 API 客户端与测试页（`[F] api/DeepSeekApi.java:34-40` 直连 `api.deepseek.com`，用户自填 key；唯一调用点是 `[F] activity/settings/TestActivity.java:46-57`）。不是可交付功能，收益不明，**不搬**。
4. **targetSdk 26 / compileSdk 33 / minSdk 14 的过时组合。** `[F] app/build.gradle:17,31-35`；依赖也停在 EOL 版本（okhttp 3.12.13、appcompat 1.5.1、jsoup 1.10.2、material 1.9.0，`[F] app/build.gradle:119-135`）。在新系统上会碰到通知/后台/安装限制与 API 返工，**只借鉴代码思路，不借鉴构建配置**。
5. **无 arm64 原生库。** `[F] app/build.gradle:48-53` 只打包 `armeabi-v7a`/`x86`，`mips` 是空占位文件；纯 arm64 设备上无法用原生解码。我方 `app/libs/arm64-v8a` 齐全（**优势**），移植对方播放器/JNI 相关代码时不要反向下调 ABI。
6. **三套播放栈并存。** ijkplayer + ExoPlayer + DanmakuFlameMaster 原生库（`[F] app/build.gradle:145-149`）体积与内存翻倍；我方只有两套。引入 ExoPlayer 时要同时规划「何时释放另一套」，否则低内存表更糟——对方用 `DeviceProfile.useUnifiedDashPlayer()`（`[F] activity/player/PlayerActivity.java:469`）做二选一，这个思路要一起搬。
7. **更新链未见签名/包名校验。** 对方 `UpdateInfoActivity`/`UpdateInstallActivity` 直接 `FileProvider` + 系统安装器（`[F] activity/update/UpdateInstallActivity.java:14,84-87`），grep 无 `signature`/`PackageManager` 校验；我方有 `[O] util/ApkVerifier.kt`。**这一项我方更强，不要为了「对齐对方」而删校验。**
8. **Java 代码内大量中文字面量**（如 `[F] util/FollowRelationUtil.java:34-40` 的「互粉/已关注/回关」、`UserRelationActions` 的「不能对自己执行此操作」），未走 `strings.xml`；与我方「用户可见文案一律进 `strings.xml`」约定冲突，移植时**必须资源化并用 `UiText` 映射**。
9. **浏览器/中继带来的隐私与合规问题。** 若照搬「浏览器 + 中继 + 全局代理」，等于把用户流量交给第三方；我方无此能力反而是优势。

## 6. 推荐移植 Top 10（按「用户价值 / 实现成本」排序）

> 每一项给出：来源、我方目标文件、新增 API/资源/权限、单测、风险与替代。排序前先看一条总原则：**先做不改播放架构的 S 级，再评估 ExoPlayer L 级；所有新第三方依赖必须单独获批。**

### 6.1 Top1：playurl 双接口兜底（S）

- 来源：`[F] api/PlayerApi.java:116-117,172-174,351-357,410` 与 `:694-700`。
- 我方目标：`[O] api/PlayerApi.java`（`getVideo`/`tryGetVideoWithFnval`/番剧取流三处）；建议把「首选/回退」顺序抽成纯类 `[O] app/src/main/java/com/RobinNotBad/BiliClient/player/PlayUrlFallbackPolicy.kt`。
- 新增 API/资源/权限：无新权限、无新资源；沿用 `ConfInfoApi.signWBI` 与 `NetWorkUtil`。
- 单测：`[O] app/src/test/java/com/RobinNotBad/BiliClient/api/PlayerApiTest.java` 扩展（已有该文件）；回退顺序/条件用纯函数测，避免真网。
- 风险/替代：回退接口是同一份 B 站内容，只需确保 Cookie/Referer 规则仍走 `[O] player/PlayerRequestPolicy.kt`；若担心风控，可在设置里加开关而不是全量回退。

### 6.2 Top2：弹幕窗口加载 + 数量保护（M）

- 来源：`[F] player/DanmakuWindowLoader.java:14-45`、`[F] player/DanmakuLoadPolicy.java:5-41`、`[F] activity/player/PlayerActivity.java:3384,3559`。
- 我方目标：新增 `[O] app/src/main/java/com/RobinNotBad/BiliClient/player/DanmakuWindowLoader.kt`、`DanmakuLoadPolicy.kt`；改 `[O] player/DanmakuManager.kt`（加载入口）与 `[O] activity/player/PlayerActivity.kt:2717`（`prepareDanmaku`）。
- 新增 API/资源/权限：无；设置项文案进 `res/values/strings.xml`，并在 `[O] activity/settings/SettingGroupActivity.kt`、`SettingsIndex.kt`、`SettingText.kt`、`UiText.kt` 注册（项目文案四件套）。
- 单测：新增 `app/src/test/java/.../player/DanmakuWindowLoaderTest.kt`（窗口滑动/取消/重试/内存上界）与 `DanmakuLoadPolicyTest.kt`（阈值/手动优先边界）。
- 风险/替代：DFM 的 `BiliProtobufDanmakuParser.setDanmakuSegments` 需要按时间轴增量喂段，改不好会出现「弹幕只出前 6 分钟」；替代方案是只做数量保护（S）先止血。

### 6.3 Top3：ExoPlayer 单时间轴 DASH（L）

- 来源：`[F] player/ExoDashMediaPlayer.java:42-49,68-95`、`[F] player/UnifiedDashPlayerFactory.java:19-27`、`[F] app/build.gradle:147`、`[F] activity/player/PlayerActivity.java:469` 的档位二选一。
- 我方目标：新增 `[O] app/src/main/java/com/RobinNotBad/BiliClient/player/ExoDashPlayer.kt`（实现我方 `[O] player/IjkPlayerBridge.kt` 同形接口，而不是照抄对方的 ijk `IMediaPlayer` 适配层）；改 `[O] activity/player/PlayerActivity.kt`（DASH 分支改用单播放器，删除外挂音轨同步补偿代码前做灰度）；`[O] app/build.gradle` 新增 `com.google.android.exoplayer:exoplayer-core:2.18.7`（minSdk 16，兼容我方 19）。
- 新增 API/资源/权限：新依赖 1 个（约 +1MB 级，需实测）；不需要新权限。
- 单测：把「选 Exo 还是 ijk」「DASH 源构造参数」抽纯函数测（`PlayerStreamPolicy` 已有基础）；播放器行为只能真机/模拟器验证。
- 风险/替代：① 新增第三方库违反「不轻易引入」约定，需用户批准；② ExoPlayer 2.18 的 `DefaultHttpDataSource` 需接我方 Cookie/Referer 白名单（`[O] player/PlayerRequestPolicy.kt`），接错会 403；③ 低内存表可能更吃内存 → 必须同时实现档位二选一；④ 若暂不引入，可先把双播放器同步策略从「偏差过大再拉」改为「每 N 秒硬对齐」，作为过渡方案（不解决根因）。

### 6.4 Top4：连播队列（模式/耳机切歌/token 恢复）（M）

- 来源：`[F] player/CollectionPlaybackQueue.java:8-70`、`[F] api/FavoritePlaybackSource.java:13`、`[F] api/UploaderPlaybackSource.java:11`、`[F] player/PlaybackResumePoints.java:6`、`[F] player/CachedPlaybackSnapshot.java`。
- 我方目标：新增 `[O] app/src/main/java/com/RobinNotBad/BiliClient/player/PlaybackQueue.kt`（纯逻辑：去重/顺序/倒序/随机/单曲/历史栈）与 `PlaybackResumeStore.kt`（token 快照）；改 `[O] activity/player/PlayerActivity.kt`（`onSkipToNext/Previous` 走队列，接 `[O] util/VirtualCollectionPrefs.kt` 现有开关）、`[O] activity/user/favorite/FavoriteVideoListActivity.kt:614-665`、`[O] activity/video/collection/CollectionInfoActivity.kt`、`[O] activity/user/info/UserVideoFragment.kt`（新增「整合集连播/UP 主投稿连播」入口）。
- 新增 API/资源/权限：新增 `x/space/wbi/arc/search` 分页播放源（若我方已有 `[O] api/UserInfoApi.java` 投稿列表接口则复用）；文案进 strings + UiText。
- 单测：`app/src/test/java/.../player/PlaybackQueueTest.kt`（四模式、边界、去重、random 可注入种子）。
- 风险/替代：我方播放页已有「两层文件夹」模型（`[O] activity/player/PlayerActivity.kt:578-624`），队列接入要避免与虚拟合集状态机打架；可先只做「收藏夹/缓存」，合集与 UP 主连播后置。

### 6.5 Top5：存储空间页（S）

- 来源：`[F] util/AppStorageManager.java:14-70`、`[F] activity/settings/SettingStorageActivity.java:15-40`。
- 我方目标：新增 `[O] app/src/main/java/com/RobinNotBad/BiliClient/util/AppStorageManager.kt` + `[O] activity/settings/StorageActivity.kt` + layout；入口挂到 `[O] activity/settings/SettingGroupActivity.kt:589-592` 的「存储路径」分组；复用已存在的 `[O] util/FileUtil.java:49` `clearCache`。
- 新增 API/资源/权限：无新权限（`StatFs` 读内置存储即可；外置需 `ContextCompat.getExternalFilesDirs`，不申请旧式存储权限）。
- 单测：`AppStorageManagerTest.kt` 测「清理边界：不碰已下载成品/不删当前播放文件」（把判定抽纯函数）。
- 风险/替代：清理范围必须白名单（缓存/临时/更新包），**绝不删下载目录**——我方 `FileUtil` 已有「只清临时产物」约定（`[O] util/FileUtil.java:140` 注释），沿用即可。

### 6.6 Top6：移动数据 / Wi-Fi 独立画质（S）

- 来源：`[F] util/NetworkPlaybackQuality.java:9-19`、`[F] activity/settings/SettingQualityActivity.java:29-77`。
- 我方目标：新增 `[O] app/src/main/java/com/RobinNotBad/BiliClient/util/NetworkPlaybackQuality.kt`（纯逻辑：网络类型 → 取哪个 qn 键）；改 `[O] activity/settings/SettingQualityActivity` 同级页面（我方清晰度选择器）与 `[O] player/PlayerDefaults.kt`；设置键进 `[O] util/SettingsKeys.kt`。
- 新增 API/资源/权限：`ACCESS_NETWORK_STATE` 我方清单已声明（`[O] app/src/main/AndroidManifest.xml:25`），无需新增权限；无新资源。
- 单测：`NetworkPlaybackQualityTest.kt`（WiFi/移动/VPN/未知/手动覆盖优先级）。
- 风险/替代：手表上「移动数据」常是 eSIM 流量，降档收益明确；若不想加设置项，可先只在「流量网络自动 720P」上做单开关。

### 6.7 Top7：历史记录多选删除（S）

- 来源：`[F] activity/user/HistoryActivity.java:33-61`。
- 我方目标：改 `[O] activity/user/HistoryActivity.kt`（增加长按菜单「删除这条/多选删除」与选择态）；`[O] api/HistoryApi.java:223` 扩展批量删除（可循环单条，先不加新接口）。
- 新增 API/资源/权限：无；文案进 strings/UiText。
- 单测：批量选择/上限判定抽纯函数测（新增 `HistorySelectionPolicyTest.kt`）。
- 风险/替代：对方用 `DeleteSession` 授权防 CSRF 失配；我方 `deleteHistory` 已带 csrf，逐条删即可，注意失败中断要提示已删条数。

### 6.8 Top8：大图查看分块渲染（M）

- 来源：`[F] ui/widget/ImageViewerPage.java:44-60,60-206`（尺寸/堆内存决策、Subsampling、Gif、失败态）。
- 我方目标：改 `[O] activity/ImageViewerActivity.kt`（或新增 `[O] ui/widget/ImageViewerPage.kt`）；方案 A（推荐先做）：不引依赖，用 `BitmapRegionDecoder` + 手势自绘，覆盖「超长图/超大图」两类；方案 B：引入 `subsampling-scale-image-view-androidx:3.10.0`（Apache-2.0，minSdk 14），需按项目约定单独批准。
- 新增 API/资源/权限：方案 B 新增 1 个依赖 + `THIRD_PARTY` 许可记录；无权限。
- 单测：`ImageViewerPolicyTest` 测「何时走分块/何时整图/何时降采样」的纯判定。
- 风险/替代：我方已有 PhotoView 双击缩放策略（`[O] activity/ImageViewerActivity.kt:34-70`），不要重写这部分；只在「超大/超长/解码失败」三类分流。

### 6.9 Top9：设备档位接入取流与播放器（M）

- 来源：`[F] util/DeviceProfile.java:11-24,148-211`、`[F] api/PlayerApi.java:183-218`、`[F] activity/player/PlayerActivity.java:1865,1989-2000`。
- 我方目标：在 `[O] util/PerformanceManager.kt` 之上加「档位→策略」纯类（如 `[O] player/DevicePolicy.kt`）：COMPAT 档自动 720P、关 DASH 双播放器/Exo、放宽超时、弹幕限额 2000；改 `[O] api/PlayerApi.java`（qn/fnval 选择）与 `[O] activity/player/PlayerActivity.kt`（超时/缓冲参数）。
- 新增 API/资源/权限：无。
- 单测：`DevicePolicyTest.kt`（档位×网络×登录态矩阵）。
- 风险/替代：判据误伤会让高配表画质下降；必须先打印档位到日志、灰度一个版本再收紧；与 6.3 联动做。

### 6.10 Top10：视频 / 动态举报（S，需用户拍板）

- 来源：`[F] api/VideoReportApi.java:13,24-38`、`[F] api/DynamicReportApi.java:13`、`[F] ui/VideoReportController.java:14`、`[F] ui/DynamicReportController.java:14`、入口 `[F] activity/video/info/VideoInfoFragment.java:216-220`、`[F] adapter/dynamic/DynamicHolder.java:76-90`。
- 我方目标：新增 `[O] app/src/main/java/com/RobinNotBad/BiliClient/api/VideoReportApi.java`、`DynamicReportApi.java` 与统一弹窗 `[O] ui/…ReportDialog.kt`；入口接到 `[O] activity/video/info/VideoInfoFragment.kt` 与 `[O] adapter/dynamic/DynamicHolder.kt`。
- 新增 API/资源/权限：B 站举报接口（原因列表 + 提交）；无系统权限；**涉及账号写操作与平台合规**，实现前需用户明确同意。
- 单测：原因列表解析/去重/空态用 mock JSON 测（不真发请求）。
- 风险/替代：举报是「代用户对外提交」，误报会牵连账号；不做则保持现状——我方公开侧目前没有视频/动态举报入口。

### 6.11 暂缓 / 不建议排期

- **SAF 外置 SD（L）**：对方资料最完整（`SAF_PATCH_NOTES.md`），但手表几乎无 SD 卡槽、且要改下载库/本地扫描/播放/删除四层，收益/成本比最差；老手机用户若呼声高再评估。
- **差分更新（L）**：依赖发布侧生成并托管补丁（我方发布走自有终端服务 + GitHub），基建成本远高于用户收益；先优化全量包体积/断点续传即可。
- **关注/拉黑（S/M）**：价值真实但有账号影响，且需 `x/relation/modify` 的 action 5/6 与 attribute 位语义验证；可放到举报之后、与关系页改版一起做。
- **创作中心稿件管理（M）**：`member.bilibili.com` 会话/风控最不稳定，维护成本高；建议先只读（统计已有）。
- **内置浏览器 + 中继、App 推荐、DeepSeek**：见第 5 章，「不可搬」。

## 7. 方法与局限

1. **证据来源**：`fork-analysis/cmp/BiliTerminal-Revival/{only-fork,only-ours,modified}.txt` 提供差异线索；`modified.txt` 193 条含大量 Java→Kotlin 同名重写（例如 `BiliTerminal.java` vs `[O] BiliTerminal.kt`），**不能按「路径是否独有」判功能有无**，本报告一律回到两侧源码逐项核实。
2. **未真机验证**：所有「用户可感知」的判断（音画漂移、弹幕卡顿、长图 OOM）是代码结构推得，属 **【推测】**；代码存在性与行号是 **【事实】**。
3. **归属不确定**：对方 git 历史仅 13 个压缩提交，无法区分「上游 2.9.5 自带」与「复活版自创」；第 3 章的「对方有」只保证「对方代码里现在有」，不保证是复活版发明。
4. **未做**：不编译对方工程、不对比 APK 体积（对方 `targetSdk 26` 的包体积口径与我方不可直接比），不读取我方 `private/`（私有目录，与本任务无关且禁止引用）。
5. **复核建议**：按第 6 章逐项开工前，先 `read` 对应行号确认代码仍在（本报告基于 2026-11-04 的 `60d4559` 与我方 `1c404c3`）。
