# BiliTerminal-Revival 功能差距清单

> 分析对象：`jiabenguiyin/BiliTerminal-Revival`（本地克隆 `D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\BiliTerminal-Revival`，`origin/main` = `60d4559`）
> 对照基线：`ReBiliClient`（RE:哔哩终端，`main` HEAD `965e9781`，版本 26.11.13）
> 标注约定：`[sib]` = 对方路径（相对克隆根）/ `[ours]` = 我方路径（相对 `ReBiliClient` 根）；**【事实】**= 读过代码确认，**【推测】**= 未实测推断。代码片段均 ≤15 行。
> 范围：只做「功能性差距 + 功能优化」，不含安全/规范审计。

## 0. 基线

| 项 | BiliTerminal-Revival | ReBiliClient（我方） |
| --- | --- | --- |
| versionName | `1.2.4`（`[sib] app/build.gradle:11`） | 26.11.13（`appVersionDate` 派生） |
| versionCode | `2026110401`（`[sib] app/build.gradle:10`） | 同上派生 |
| 最近提交 | `60d4559 build: keep the Gradle wrapper executable on Linux`（2026-11-04） | `965e9781` |
| compileSdk / minSdk / targetSdk | 33 / 14 / 26（`[sib] app/build.gradle:17-33`） | 35 / 19 / 35 量级 |
| 语言 | 纯 Java + XML，0 个 Kotlin 文件 | Kotlin 为主，Java 残留在 `api/` |
| Activity 数 | 88 | 120 |
| 源文件数 | 384 `.java` | 569 `.java`/`.kt` |
| 三方库（差异点） | `androidx.documentfile`（SAF）、`androidx.webkit`（WebView ProxyController）、`exoplayer-core 2.18.7`、`brotli4j`、`commons-compress` | 无 SAF、无 webkit 代理、无 exoplayer（单 ijk 引擎） |
| Gradle 模块 | `:app` `:ijkplayer-java` `:DanmakuFlameMaster` `:brotlij`，`rootProject.name = "BiliTerminal"` | 完全相同 |

**与上游 BiliClient 的关系【事实】**：`settings.gradle` 模块、包名 `com.RobinNotBad.BiliClient`、`rootProject.name` 与我方完全一致，目录结构 90% 重合，可判定为同一上游 BiliClient 的**同源衍生**，不是另起框架的分支。

**git 历史情况【事实】**：只有 13 条提交，最早为 `0de9009 Initial public source release`，之后全是 `release: sync 1.2.x` / CI 调整。**历史被压平过**，因此任务书里「用 `--diff-filter=A` 找自创文件」的做法对这个仓库不可用——本次改用「同名类/同名资源对照 + 读实现」的方式判定差异。

**基线抽查（不采信 readme 自称）【事实】**：readme 与 1.2.4 更新日志列了约 30 条能力，实测**大部分我方已有**：

- 「连播顺序/倒序/随机/单曲循环 + 耳机切歌 + 进度续播」→ 我方已有 `[ours] app/src/main/java/com/RobinNotBad/BiliClient/player/PlayQueue.kt:19-53`（枚举 `PlayQueueSource{SINGLE,UPLOADER,COLLECTION,FAVORITE,LOCAL_CACHE}`、`PlayQueueMode{SEQUENTIAL,REVERSE,SHUFFLE,REPEAT_ONE}`、`seed` 稳定随机序 + `history`）与 `player/PlaySessionStore.kt`（138 行）。
- 「菜单可排序」→ 我方已有 `[ours] util/MenuConfig.kt:100-160`（`serialize/parse/parseLenient/resolveEnabledList`）。
- 「收藏视频排序」→ 我方已有 `[ours] activity/user/favorite/FavoriteVideoListActivity.kt:81`、`activity/search/SearchVideoFragment.kt:60`。
- 「存储空间清理」→ 我方已有 `[ours] util/AppStorageManager.kt:44-314`。
- 「评论举报」→ 我方**有**，但见 §1.3 的差异说明。

结论：**这个分支相对我方不是「功能更多」，而是「少数几块做深了」**。Activity 名差集只有 10 个：`BrowserActivity`、`CreatorArchiveActivity`、`SearchableVideoListActivity`、`SettingLaboratoryActivity`、`SettingStorageActivity`、`SettingUIActivity`、`SortSettingActivity`、`TutorialActivity`、`UpdateInfoActivity`、`UpdateInstallActivity`，其中后 6 个我方都有等价实现。

## 1. 对方有、我方没有的功能

### 播放器与弹幕

#### 1.1 大弹幕量保护（5000 条阈值降级）

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.java:101-102`（开关「大弹幕量保护（5000条）」）、`[sib] app/src/main/java/com/RobinNotBad/BiliClient/activity/player/PlayerActivity.java:3453-3456`（装配到 `DanmakuLoadPolicy`）
- **行为**：弹幕总数超过 5000 条的稿件，加载弹幕前先由 `DanmakuLoadPolicy.shouldLoad()` 判定是否降级（直播模式不参与），避免一次性把几万条弹幕灌进引擎导致播放卡死；用户可在播放器设置里关掉。
- **我方现状**：缺。`[ours] app/src/main/java/com/RobinNotBad/BiliClient/player/DanmakuManager.kt:36`（默认 `maxLines = 15`）、`:138`（读 `SettingsKeys.PLAYER_DANMAKU_MAXLINE`）、`:273-288`（`setMaximumLines` 只控**行数**）——**只限制同屏行数，无总量阈值/降级开关**。`[ours] player/DanmakuLoadPolicy.kt`（82 行）看起来是加载时机策略，未做条数保护。
- **成本**：**小**（<半天）。冲突点：`player/DanmakuLoadPolicy.kt` + `DanmakuManager.kt` + `util/SettingsKeys.kt` + 播放器设置页文案。
- **价值**：**中**。手表内存只有几百 MB，热门稿件弹幕动辄上万条，这条是纯性能止损。

#### 1.2 弹幕/播放器设置项的更细粒度（部分项我方缺）

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingTerminalPlayerActivity.java:26-143`（一张 40+ 项的内置播放器设置表）
- **行为**：除我方已有项外，还有「放大后单击切换移动/进度」「缩放后移动方式（单指/双指）」「双击优先操作（暂停·复原·智能）」「互动视频选项按钮大小 0.7」「音量步进百分比」「显示实时人数」「左侧亮度/音量手势」等细分项，都是给**小屏单手操作**调手感的。
- **我方现状**：【事实】我方大部分同类项已存在（字幕、双指、双击、合并重复弹幕、强制滚动、直播弹幕停留倍率、音频输出、软解均有命中：`[ours] player/PlayerSettingsGroups.kt`（380 行）、`player/LiveDanmakuFactorPolicy.kt`、`player/ForceScrollParser.kt`、`player/AudioSeekVolumePolicy.kt`）。【推测】个别项（「放大后单击切换移动/进度」「缩放后移动方式 单/双指」的解释器）我方可能没有独立开关，未逐项比对表单。
- **成本**：**小**（单项）／**中**（补齐整表）。冲突点：`player/PlayerSettingsGroups.kt` 表结构 + `SettingsKeys.kt`。
- **价值**：**中**（逐项看是低，合起来决定小屏手感）。

### 视频与详情页

#### 1.3 视频举报（完整原因选择 + 提交）

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/api/VideoReportApi.java:34-71`、`[sib] app/src/main/java/com/RobinNotBad/BiliClient/ui/VideoReportController.java`（复用 `[sib] app/src/main/java/com/RobinNotBad/BiliClient/ui/ReplyReportDialog.java` 的弹窗）
- **行为**：视频详情页「更多」里点举报 → 先拉 `https://api.bilibili.com/x/web-interface/archive/appeal/tags` 得到官方原因列表（过滤 `state!=1`、`business!=1`）→ 勾原因、最多填 500 字描述 → 提交 `https://api.bilibili.com/x/web-interface/appeal/v2/submit`（`block_author=false`）；某些原因类型 `officialRequired=true` 时不给它提交，直接跳官方投诉页 `https://www.bilibili.com/appeal/?avid=`。`AtomicBoolean submitted` 防重复提交。
- **我方现状**：**缺**。全仓 `appeal` **0 命中**；仅有的举报入口是评论举报，且是**私有可选门面**：`[ours] app/src/main/java/com/RobinNotBad/BiliClient/adapter/ReplyAdapter.kt:656-695` 里 `OptionalFeatures.replyReportLabel(context)` 返回 null 时整个菜单项不显示（公开构建无举报能力）。
- **成本**：**中**（1–3 天）。冲突点：新增 `api/VideoReportApi`（我方 `api/` 全同步阻塞、走 `NetWorkUtil`），`activity/video/VideoInfoFragment` 的更多菜单，`adapter/` 里的原因单选弹窗。
- **价值**：**中**。手表不是举报的主场景，但「App 内没有任何举报入口」在应用商店/合规侧是硬伤；而且这是唯一一条**我方公开构建完全没有**的合规能力。

#### 1.4 列表内搜索（收藏夹 / 历史顶部搜索框）

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/activity/base/SearchableVideoListActivity.java:29-60`、布局 `[sib] app/src/main/res/layout/view_library_search.xml`
- **行为**：收藏夹与历史两个长列表页顶部内嵌一个搜索框（`:29` 类注释写明「只给收藏和历史共用」），输入关键词后本地过滤 + 支持继续分页拉取，`generation` 计数防止旧请求回填串号，`loading` 位去重。
- **我方现状**：缺。全仓 `列表内搜索`/`SearchableList` **0 命中**；`[ours] activity/user/favorite/FavoriteVideoListActivity.kt`、`[ours] activity/user/HistoryActivity.kt` 只有下拉刷新与排序。
- **成本**：**小**（<半天，纯前端过滤 + 一个 `RefreshListActivity` 基类扩展点）。冲突点：`activity/base/RefreshListActivity.kt` + 两个列表页。
- **价值**：**低–中**。手表打字体验差，收益主要在「几百条收藏里找一条」，聊胜于无。

### 动态与社交

#### 1.5 动态举报

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/api/DynamicReportApi.java:14-57`、`[sib] app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/DynamicInfoActivity.java`（调用点）
- **行为**：动态卡片菜单里举报 → 原因表**硬编码 10 条**（`IDS={4,8,1,5,3,9,10,12,13,0}`：垃圾广告/引战/色情/人身攻击/违法信息/涉政谣言/涉社会事件谣言/虚假不实信息/违法信息外链/其他）→ 提交 `https://api.bilibili.com/x/dynamic/feed/dynamic_report/add`，带 `dynamic_id/accused_uid/reason_type/reason_desc/csrf`，`Origin/Referer=t.bilibili.com`。
- **我方现状**：缺（同 1.3，`appeal`/`dynamic_report` 均 0 命中）。
- **成本**：**小**（接口只有 57 行，弹窗复用视频举报的实现）。
- **价值**：**低**。手表上刷动态举报的概率很低，但**顺手做**（与 1.3 同一批）比单独做划算。

### 私信与消息

#### 1.6 全局未读计数 + 主菜单红点 + 开关

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/util/MessageUnreadCounter.java:13-107`、`[sib] app/src/main/java/com/RobinNotBad/BiliClient/util/MessageUnreadState.java:4-43`（配套 `[sib] app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/MessageBadgeStyle.java`）
- **行为**：`POLL_MS=60000`、`MIN_REFRESH_MS=5000`，**只在前台菜单页被观察时轮询**（`observe/unobserve`，不起后台服务、不驻留进程）；计数 = 互动消息 + 私信未读，写进 `MESSAGE_UPDATE_NUM`；主菜单「消息」项的箭头位置画红点，开关是 `MESSAGE_MENU_ARROW_DOT_ENABLE`；账号切换或读数清零时 `State.reset`。`MessageUnreadState` 用 `account/revision/sequence` 三元组丢弃过期响应（切换账号时旧响应不会覆盖新账号的角标）。
- **我方现状**：缺。`[ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/MenuActivity.kt` 全文无 `未读|dot|unread|红点` 命中，菜单项就是 `:50-63` 的 key→(标题,Activity) 静态映射；`[ours] activity/message/MessageActivity.kt:133-156` 只在会话页内部拼「(N未读)」文本，**没有跨页/入口级提示，也没有开关**。
- **成本**：**小**（<半天）。冲突点：`activity/MenuActivity.kt` 的菜单 View（我方是 `MenuConfig` 驱动的列表，需要给单项加角标绘制）+ 一个 `util/` 里的轮询器 + `SettingsKeys.kt`。
- **价值**：**高**。手表没有通知栏生态、屏幕又小，「哪个入口有几条没看」几乎只能靠角标；这是本次清单里性价比最高的一条。

### 空间与用户

#### 1.7 创作中心：本人稿件管理与删除

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/activity/user/CreatorArchiveActivity.java`（198 行）、`[sib] app/src/main/java/com/RobinNotBad/BiliClient/api/CreatorArchiveApi.java:15-97`
- **行为**：从创作中心进入稿件管理页 → 按「全部/已发布/未发布（`is_pubing,pubed,not_pubed`）」筛选列表 → 可删除稿件。走的是 `member.bilibili.com` 的 H5 接口：统计 `https://member.bilibili.com/x/web/index/stat`、创作时长 `/x/web/index/scrolls`、稿件列表与删除；必须带 web UA 与 `Origin/Referer=https://member.bilibili.com`，并用 `PENDING`（`ConcurrentHashMap` 集合）串行化同一稿件的并发操作。
- **我方现状**：只读看板，无管理能力。`[ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/user/CreativeCenterActivity.kt:20-60` 只渲染 `CreativeCenterApi.getVideoStat()` / `getBeUpTime()` 的粉丝/播放/点赞/投币/收藏/分享/评论/弹幕增量；全仓 `稿件管理`、`member/archive` **0 命中**。
- **成本**：**大**（>3 天，需要处理 H5 接口风控、CSRF、稿件列表分页与删除二次确认）。冲突点：新增 `api/CreatorArchiveApi` + `activity/user/` 新页 + `CreativeCenterActivity` 入口。
- **价值**：**低**。UP 主在手表上删稿件的场景很罕见；`member.bilibili.com` 这类 H5 接口随时可能加风控，维护成本高于收益。

### 下载与缓存

#### 1.8 SAF 外置存储写入（下载到 SD 卡 / `content://` 播放）

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/util/VideoStorageUtil.java`（357 行）、`[sib] SAF_PATCH_NOTES.md:17-26`（设计说明）
- **行为**：`VideoStorageUtil` 统一 File/DocumentFile 双后端；用 `ACTION_OPEN_DOCUMENT_TREE` 让用户选目录并**持久化读写授权**，选的时候写一个探针文件确认真的可写；下载服务支持 SAF 创建/覆盖写入与 HTTP Range 续传，遇到不支持 seek 的文档提供程序就清空分片重下；下载库版本升到 5，每个任务落 `storage_mode`/`storage_ref`；本地缓存列表能扫 SAF 目录、读封面、播 `content://`、读弹幕/字幕、删除、分页。文档里专门警告：不要把 `content://` 转成 `/storage/XXXX-XXXX/...` 路径用（那条路径不带写权限）。
- **我方现状**：缺。全仓 `DocumentFile` **0 命中**、`ACTION_OPEN_DOCUMENT_TREE` **0 命中**；仅有 `[ours] app/src/main/java/com/RobinNotBad/BiliClient/util/LocalFileProvider.kt:25`、`util/SharedFilePolicy.kt:11`、`util/SharedFileRoots.kt:14`、`api/PlayerApi.java:747`——都是**只读/分享**外置卷，下载只能写应用私有目录。
- **成本**：**大**（>3 天）。冲突点：`service/download/` 整套下载器（分片、续传、数据库字段）、`util/` 新增存储抽象、本地缓存列表页、`AndroidManifest` 权限与 provider。
- **价值**：**高**。手表内置存储普遍只有 8–32 GB，且用户经常要靠电脑/SD 卡塞视频；「只能下到私有目录、清缓存就被端掉」是真实痛点。

#### 1.9 增量更新（bsdiff 差分补丁）

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/util/BinaryPatchUtil.java:24-110`、`[sib] app/src/main/java/com/RobinNotBad/BiliClient/activity/update/UpdateInfoActivity.java:244`（真正调用 diff 补丁的位置；`UpdateInstallActivity` 是同一流程的安装引导页）
- **行为**：`applyBsdiff(oldFile, patchFile, outputFile)` 是**纯 Java 的 BSDIFF40 解包器**——校验 `MAGIC`、读 control/diff/extra 三段长度、用 `openBzip2Section()` 分别解压（依赖 `commons-compress`/`brotli4j`），逐块 `old+diff` 再拼 `extra`，全程无 native 库。有了它，更新时只需下发「旧 APK → 新 APK」的补丁，本地合成为完整 APK 再走安装引导页。
- **我方现状**：缺。全仓 `BinaryPatch` **0 命中**；`[ours] app/src/main/java/com/RobinNotBad/BiliClient/util/UpdateManager.kt`（`:410` ABI 选择、`:565` 安装）是**整包下载**。
- **成本**：**中**（1–3 天；算法只是照着 bsdiff 格式实现，难点在服务端要生成补丁、以及版本不匹配时的降级回整包）。冲突点：`util/UpdateManager.kt` + `UpdateActivity.kt` + 发布流水线（`docs/agents/release.md` 那套）。
- **价值**：**高**。手表多为 Wi-Fi 弱、流量贵，整包 APK 又是几十 MB 量级；不过**前提是服务端愿意产补丁**，纯客户端移植只有一半收益。

### 通知与后台

#### 1.10 「夜深了」提醒

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/SettingPrefActivity.java:55`（开关 `NIGHT_REMINDER_ENABLE`）、`[sib] app/src/main/java/com/RobinNotBad/BiliClient/api/AppInfoApi.java:61`（启动时判定并弹提示）
- **行为**：启动时若处于深夜时段且开关开着，弹一句「夜深了」提醒，默认开启。
- **我方现状**：缺（`夜间提醒|nightReminder` 0 命中）。
- **成本**：**小**（<1 小时）。
- **价值**：**低**（属于氛围项，不是功能）。

### 杂项工具

#### 1.11 内置 WebView 浏览器（仅指「普通浏览器壳」这部分）

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/activity/browser/BrowserActivity.java:35-120`
- **行为**：一个带地址栏、前进/后退/主页/刷新/「用外部浏览器打开」和进度条的 WebView 页面，`setBlockNetworkLoads(true)` 先阻断再逐域放行。**注意**：它的主页写死成 `HOME_URL="https://jp.031030.xyz/"`，且在「中继开启」时用 `androidx.webkit.ProxyController` 把 WebView 流量整体倒进自己的 `BrowserRelayProxy`（238 行）——这部分**不可移植**，见 §3。
- **我方现状**：无 `BrowserActivity`（`Browser` 只作为字符串出现在 `[ours] api/CookiesApi.java`、`util/UpdateManager.kt`、`util/UpdateRelease.kt`）。我方有 `CaptchaWebViewActivity.kt` 但只服务人机验证。
- **成本**：**小–中**（剥掉中继部分后就是一个 WebView + 地址栏）。冲突点：新增 `activity/` 页面 + 网络安全白名单（我方只允许 https 与 4 个明文域）。
- **价值**：**低**。手表小屏浏览网页体验极差；除非有「在 App 内打开 B 站活动页/专栏外链」的具体需求，否则不建议为了对齐而做。

## 2. 我方有、对方做得更好的功能优化

### 2.1 高能进度条：多一个「弹幕密度」数据源兜底

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/util/HighEnergyDataBuilder.java:12-52`
- **证据/行为**：对方**完全放弃**了远程 PBP 接口，改用「已经加载到内存的 protobuf 弹幕窗」按 `DEFAULT_STEP_SEC=10` 秒分桶统计密度，直接产出 `new HighEnergyData(stepSec, "", copy, "danmaku-density")`；注释写明理由：避开已下线的 PBP、长视频只算当前可见窗口。
- **我方现状**：`[ours] app/src/main/java/com/RobinNotBad/BiliClient/api/PlayerApi.java:1130-1156` 只走 PBP：`https://bvc.bilivideo.com/pbp/data?cid=...&r=loader`，`pbpHeaders()` 带视频页 Referer，`parseHighEnergyData()` 兼容新旧两种响应形态；**接口失败时只打日志、返回 null，曲线就是空白**（`:1143-1154`）。`:1135-1138` 的注释记录了「2026-10 前后服务端改了行为，缺 `r=loader` 就没 events」——说明这条链路本来就很脆。
- **改法方向**：保留 PBP 为主，`getHighEnergyData()` 返回 `hasValidData()==false` 时用「本地弹幕密度」兜底（我方 `player/DanmakuStreamLoader.kt`/`DanmakuXmlCounter.kt` 已经能拿到弹幕序列，纯计算、无额外网络）。
- **成本**：**小**（<半天，一个纯函数 + 一处分支）。**价值**：**中**（高能条是长视频跳广告位的主要依据，空白曲线等于功能失效）。

### 2.2 Cookie 合并：鉴权 cookie 不被响应覆盖

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/util/CookieMergeUtil.java:10-40`
- **证据/行为**：`AUTH_COOKIE_NAMES={sessdata, bili_jct, dedeuserid, dedeuserid__ckmd5, ac_time_value}`；当 `allowAuthCookieUpdates=false` 时**拒绝**用响应里的 `Set-Cookie` 覆盖这 5 个鉴权项，空值则视为删除。目的是：普通接口返回的残缺 Cookie 不会把已登录态搞坏。
- **我方现状**：`mergeCookie|CookieMerge` **0 命中**；`[ours] api/CookiesApi.java` 有 cookie 存取与 `cookie_refresh` 逻辑，但没有「哪些 key 允许被响应覆盖」的白名单。
- **改法方向**：在写 cookie 的路径上加一层 key 白名单 + 空值语义（删除 vs 忽略）。
- **成本**：**小**。**价值**：**中**（掉登录是这类客户端最常见的反馈类型之一）。

### 2.3 华为/鸿蒙默认软解 + ABI 兜底 + 低内存降长按倍速

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/util/PlayerCompatibilityUtil.java:14-63`
- **证据/行为**：（a）`prefersSoftwareCodec()` 命中 `huawei`/`harmony` 就默认软解（硬解在这些机型上黑屏/花屏）；（b）`shouldUsePlatformMediaPlayer()` 在设备 ABI 不属于 `armeabi-v7a`/`x86` 时回退系统播放器，避免 `System.loadLibrary` 直接崩；（c）`shouldReduceLongPressLoad(sdk<=19 || memoryClass<=192)` 时长按倍速从 3.0x 降到 2.0x；（d）`supportsHighFrameRate(refreshRate>=59f)` 决定要不要上高帧率。
- **我方现状**：`[ours] app/src/main/java/com/RobinNotBad/BiliClient/player/IjkNativeLibs.kt:1-187` **无 `huawei`/`harmony` 关键字命中**；`:2040` 一带有手动切软解，但**没有按机型自动前置**；ABI 兜底逻辑在 `IjkNativeLibs` 里有 native 库可选性判断，未与「回退系统播放器」这条路径对齐（【推测】）。
- **改法方向**：把「机型/内存/ABI → 默认解码方式与长按倍速」抽成一个纯函数策略类（我方已有 `player/*Policy.kt` 的既有写法），设置页只作为「用户覆盖」。
- **成本**：**小**（新增一个 policy 类 + 两处调用）。**价值**：**中**（手表 SoC 以国产为主，硬解兼容性参差）。

### 2.4 页面转场动画的全局关闭开关

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/util/PageMotion.java:9-22`
- **证据/行为**：开关 `ui_disable_page_transitions` 打开后，把窗口动画置 0 并 `overridePendingTransition(0,0)`，用 `WeakHashMap` 记住原值以便恢复；所有 Activity 统一生效。
- **我方现状**：只有零星一处 `[ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/InstanceActivity.kt:24` 调了 `overridePendingTransition`，**没有全局开关**（全仓 `windowAnimations` 0 命中）。
- **成本**：**小**（在 `activity/BaseActivity.kt` 里统一处理后即可）。**价值**：**中**（手表 CPU 弱，转场动画是肉眼可见的卡顿来源；对齐 2.3 一起做还能掩盖部分白屏）。

### 2.5 图片地址：看大图时去掉 `@...` 缩放后缀

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/util/BilibiliImageUrlUtil.java:11-38`
- **证据/行为**：`stripResizeSuffix(url)` 把 hdslb/biliimg 域名下形如 `...@1024w_1024h.webp` 的缩放后缀砍掉，取原图；只在「大图查看器」里用。
- **我方现状**：**`stripResizeSuffix` 0 命中**。我方图片加载集中在 `[ours] util/ImageTilePolicy.kt`、`[ours] ui/widget/BigImageView.kt:398`（分块解码），【推测】走的是「按控件尺寸请求尺寸，而不是取原图」，所以「点开看原图」这条链路上可能仍拿到缩略图。
- **成本**：**小**。**价值**：**低**（手表分辨率低，原图收益有限，但成本几乎为零）。

### 2.6 回前台恢复播放位置的去抖

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/util/ForegroundRestorePolicy.java:5-22`
- **证据/行为**：`MIN_POSITION_DELTA_MS=750`——回到前台时只有当「记录位置」比「当前播放位置」落后超过 750 ms 才 seek，否则不动，避免每次切前台都一次多余的 seek（手表上表现为明显卡顿/黑一下）。
- **我方现状**：`[ours] activity/player/PlayerActivity.kt` 有前后台处理与 `AudioFocusPolicy`，但未见等价的阈值常量（【推测】直接 seek）。
- **成本**：**小**。**价值**：**低–中**。

### 2.7 翻页/切页时的「上一次请求取消」封装

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/util/RequestCancellation.java:8-28`
- **证据/行为**：`attach(Call)` / `cancel()`，取消的是**单个请求**而不是共享 client；配合列表页 `generation` 计数丢弃过期结果。
- **我方现状**：我方有 `.cancel()` 调用（22 处，如 `[ours] activity/base/RefreshListActivity.kt:178`、`activity/message/PrivateMsgActivity.kt:155`），但没有统一的「一页一个请求句柄」封装，靠各页手写。
- **成本**：**小**（抽基类）。**价值**：**中**（手表弱网下重复请求是卡顿主因之一）。

### 2.8 缓存歌单的快照 token 化（卷管理器稳健性参考）

- **对方实现**：`[sib] app/src/main/java/com/RobinNotBad/BiliClient/player/CachedPlaybackSnapshot.java:12-78`、`[sib] app/src/main/java/com/RobinNotBad/BiliClient/player/CollectionPlaybackQueue.java:8-95`
- **证据/行为**：快照写 `cacheDir/playback-queues/<uuid>.json`，只保留最近 16 份；>2 MB 直接报「缓存歌单过大，请选择当前合集或分P」；`read()` 用 `[0-9a-fA-F-]{36}` 校验 token 防路径注入；启动时只把 uuid 塞进 Intent，绕开 Binder 1 MB 限制；播放队列带 `history` 支持「上一首/下一首」回溯。
- **我方现状**：**能力已有且更完整**——`[ours] player/PlayQueue.kt:19-53` 有 4 种模式 + `seed` 稳定随机序 + `history` + `LOCAL_CACHE` 源，`player/PlaySessionStore.kt`（138 行）负责落盘恢复。**差距只在「大歌单传参方式的保护」**：我方未知是否对超大队列做了体积上限与回落提示。
- **成本**：**小**（补两条上限与提示）。**价值**：**低**（当前只是稳健性加固）。

## 3. 不建议移植

| 项 | 对方实现 | 理由 |
| --- | --- | --- |
| 热配置 + 自建 B 站中继 | `[sib] util/HotConfigManager.java:29-120`（`CONFIG_URLS=["https://jp.031030.xyz/terminal/config/get"]`、RSA 验签 envelope、`compatBaseUrl="https://jp.031030.xyz/compat/v1/bili"`）、`[sib] util/StartupRoutePolicy.java:6-16`、`[sib] util/NetworkCredentialPolicy.java:10-45` | **强依赖对方私有服务器与私钥**：配置地址、信任主机、RSA 公钥全写死在 `jp.031030.xyz`。移植等于把我们的网络栈嫁给第三方服务，且「把用户 Cookie 转发到中继`/compat/v1/bili`」这条路与我方「只走 https + 四个明文域白名单」的安全红线直接冲突。方向不符，不移植。 |
| WebView 中继代理 | `[sib] activity/browser/BrowserRelayProxy.java`（238 行）+ `BrowserActivity` 里的 `ProxyController` 挂载 | 同上，纯为上面那套中继服务服务；剥离后只剩 §1.11 那个低价值浏览器壳。 |
| DeepSeek AI 对话（自带 API Key） | `[sib] api/DeepSeekApi.java:20-45`（`https://api.deepseek.com/chat/completions`、`model=deepseek-v4-pro \| deepseek-flash`、`stream=true`、`max_tokens=8192`、`thinking`+`reasoning_effort=high`） | 三代问题：手表**输入**（要打字提问）与**输出**（长文本/思维链）都是灾难；要用户自备第三方 Key；我方已用系统能力做了 AI 字幕（`[ours] activity/player/PlayerActivity.kt:2995`）。价值低、维护面大。 |
| App 推荐引流 + 绑定弹窗 | `[sib] api/AppRecommendApi.java`、`api/AppRecommendAuthApi.java`、`ui/widget/AppRecommendBindingDialog.java`（pref `app_recommend_access_key*`/`app_recommend_bound_mid`/`app_recommend_experiment`） | 目的就是把用户导去另一个 App。与我们的产品方向相反，不移植。 |
| ExoPlayer 第二播放引擎 | `[sib] player/ExoDashMediaPlayer.java`（394 行）、`player/UnifiedDashPlayer*`、依赖 `exoplayer-core 2.18.7` | 我方是**单 ijk 引擎 + `VideoPlayerCore`/`IjkPlayerBridge`/`PlayerSurfaceBinder` 分层**（`[ours] player/` 41 个 Kotlin 文件），再加一套 394 行的 Exo 实现会变成双引擎双倍适配面（弹幕、字幕、手势、连播全要分叉）。收益（DASH/部分编码格式）在手表分辨率下几乎不可见。 |
| 创作中心稿件管理 | 见 §1.7 | 不是「不能做」，是**性价比不划算**：手表上删稿场景罕见 + `member.bilibili.com` H5 接口风控风险高。若一定要做，只做「稿件列表查看」不做删除。 |
| 「夜深了」提醒 | 见 §1.10 | 氛围项，放进 §1 只是为了让差距清单完整；实际不建议排期。 |

## 4. 优先建议（Top 7）

排序依据：**对「手表端」的真实价值 ÷ 移植成本**，并优先挑能与我方现有未完成项协同的。

| # | 功能 | 成本 | 价值 | 理由 |
| --- | --- | --- | --- | --- |
| 1 | **消息未读红点 + 全局计数**（§1.6） | 小 | 高 | 手表上没有通知生态，角标是唯一「一眼可见」的入口提示；对方实现只有约 150 行（`MessageUnreadCounter`+`MessageUnreadState`），且明确「不驻留后台」，完全符合我方「不轻易加后台服务」的约束。冲突面只有 `MenuActivity` 菜单项与一个轮询器。 |
| 2 | **大弹幕量保护**（§1.1） | 小 | 中 | 两三行常量 + 一处 `shouldLoad()` 判定，直接止损「热门稿件一打开就卡死」。可与 §4.3 同一批进播放器。 |
| 3 | **高能进度条弹幕密度兜底**（§2.1） | 小 | 中 | 我方 PBP 单一数据源、失败只打日志；对方用已加载弹幕本地算密度，成本是一个纯函数。属于「不做就一直空白」的兜底。 |
| 4 | **全局转场动画开关 + 华为/鸿蒙默认软解**（§2.4 + §2.3） | 小 | 中 | 两条都是「设备兼容性止损」，都落在 `BaseActivity`/`player/*Policy.kt` 的既有扩展点上，一起做半天内。 |
| 5 | **列表内搜索**（§1.4） | 小 | 低–中 | 收藏/历史两个长列表的实用小改进，改动局限在 `RefreshListActivity` 基类 + 两个页面。 |
| 6 | **SAF 外置存储写入**（§1.8） | 大 | 高 | 手表存储是硬瓶颈，「只能下到私有目录」是真实痛点；但它要动 `service/download/` 整套下载器与数据库字段，**建议单独立项**，不要挂在这次功能对齐里。 |
| 7 | **视频 + 动态举报**（§1.3/§1.5） | 中 | 中（合规） | 我方公开构建**完全没有举报入口**（仅私有的评论举报门面）。两个接口加起来 128 行、共享一个原因选择弹窗，是唯一「不做会留下合规空洞」的项。 |
| 备 | 增量更新 bsdiff（§1.9） | 中 | 高 | 收益很大但有一半在服务端（要产补丁），客户端只能先落地解包与降级回整包逻辑。适合等发布流水线一起规划。 |

**协同项**：§4.3 与 §4.4 都落在 `player/` 的策略类上，可与我方现有 `DanmakuLoadPolicy`/`PlayerSettingsGroups` 一起改；§4.1 会顺带把 `MenuActivity` 的菜单渲染抽象出来，对后续任何「入口级状态」（缓存条数、更新提示）都是复用点。

## 5. 存疑

1. **对方自研部分的实测可用性无法验证**：本机上没有 Rust/Android 构建环境跑它，全部结论来自读码。特别是 `CreatorArchiveApi`（走 `member.bilibili.com` H5 接口，风控与 Cookie 域要求苛刻）与 `BinaryPatchUtil`（依赖服务端产 bsdiff 包）这两条，**是否在真机跑通未知**。
2. **「缺少」判定依赖关键字检索**：§1 每条都做到「对方有实现文件 + 我方同类关键字 0 命中」，但关键字检索对**改了名的等价实现**会误判（本次已抓到反例：我方 PlayQueue 已覆盖对方「缓存歌单连播」，`av/bv` 转换我方也有实现只是函数名不同）。个别条目（如 §1.2 的细分手势项）可能属于这一类。
3. **我方私信相关能力未比对清楚**：对方有 `private_msg_unread_badge_enable`（私信未读标记）与 `private_msg_auto_read_enable`（自动已读），我方的 `[ours] activity/message/PrivateMsgActivity.kt` 是否已有等价行为未逐行确认，故未写成 §1 条目。
4. **我方手势细节的等价度**：对方把「双击优先操作（三选一）」「放大后单击切换移动/进度」「单/双指移动」拆成三个独立设置项；我方 `PlayerGesturePolicies.kt`/`PlayerLockPolicy.kt` 里是否有同等粒度，只做了关键字级确认，未逐项对照实现。
5. **高能进度条 PBP 是否仍可用**：我方注释说 2026-10 服务端改行为、加 `r=loader` 后恢复，说明目前仍在使用；§2.1 的兜底建议在「PBP 仍可用」的前提下是**增益**，如果 PBP 已被彻底下线，那它就变成**必需**——需要一次真机验证来确定。
6. **对方 `SettingTerminalPlayerActivity` 的 40+ 设置项里到底有几项我方真缺**：我只比对了关键字，没有逐项在设备上看我方的实际设置页，故 §1.2 的成本/价值给的是区间。
7. **对方是否存在未被我发现的隐藏功能**：它把能力大量内联在 `PlayerActivity.java`（数千行级）与 `TerminalContext.java`（352 行）这类大文件里，本次是按「类名/资源名/设置 key 差集」推进的，可能有内联能力漏计。
