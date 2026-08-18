# 第 07 组功能审查：缓存/下载、音频播放、文章/图文、合集/系列

> 审查对象：RE:哔哩终端（ReBiliClient）第三方 B 站安卓客户端
> 粒度：极细，逐文件、逐按钮、逐点击/长按/进度/暂停/恢复/清晰度/播放控制/Intent Extra 等
> 说明：本文只记录审查文件内「确实存在的功能与用法」，未实现/占位/声明未用之处也会如实标注。

---

## LocalListActivity.kt（本地缓存列表页 / 缓存管理）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/local/LocalListActivity.kt`（668 行）

页面角色：管理已下载（缓存）视频的「未分类区 + 用户自建文件夹」两级结构，可播放、删改、移动、更新弹幕、切换清晰度、虚拟合集连播。

- **页面标题**：顶部 `pageName` 显示「缓存」；进入某个文件夹后显示该文件夹名。
- **顶栏点击（`R.id.top`）**：
  - 若当前处于某个文件夹内部 → 退出文件夹回到缓存根层（`exitFolder`）。
  - 若在根层 → 触发 `menuClick.run()`（即右上角菜单回调，由 `InstanceActivity` 基类注入）。
- **下拉刷新（SwipeRefreshLayout）**：下拉触发 `refresh()`，重新扫描磁盘视频与文件夹列表。
- **存储权限检查**：onCreate 里若无存储权限则请求权限。
- **首次加载**：进入页面后后台线程加载数据并构建适配器，期间显示刷新转圈。

### 磁盘扫描逻辑（`scanVideos`）
- 遍历缓存根目录（`FileUtil.getVideoDownloadPath()`）下的每个子目录：
  - 每个目录视为一个视频，读取 `cover.png` 作封面。
  - 读取 `.quality` 文件得到清晰度标签（`仅音频` 或 qn 数字对应标签）。
  - 优先使用 `video.mp4`；若不存在但存在合并临时文件 `video_merged_temp.mp4` 则先重命名再使用；否则退回 `audio.m4a`（音频下载）。
  - 若目录存在 `.DOWNLOADING` 标记 → 视为下载中，跳过不显示。
  - DASH 双文件（`video.mp4` + `audio.m4a` 同时存在且主文件是 mp4）时，把 `audio.m4a` 也加入文件列表供播放器使用。
  - 若目录下没有媒体文件，则按「合集视频」处理：扫描其子目录作为分页，每个分页同样按上述优先级找媒体文件、读 `.quality`、跳过 `.DOWNLOADING`。
- 加载每个视频的元数据（`VideoMetaManager.readMeta`），回填 `folderName`、`aid`、`cid`。

### 文件夹操作
- **进入文件夹**：点击文件夹条目 → 列表只显示该文件夹内视频，标题变为文件夹名。
- **退出文件夹**：点顶栏或按返回键 → 回到缓存根层。
- **新建文件夹**：根层列表「新建文件夹」条目 → 弹出输入框（`InputDialogActivity`），校验名称（非空、≤30 字符、无特殊字符），成功则刷新。
- **重命名文件夹**：点文件夹条目上的重命名按钮 → 输入框预填原名 → 校验后重命名并刷新。
- **拆散文件夹**：点「拆散」按钮 → 确认对话框（`ConfirmDialogActivity`，「拆散文件夹」）→ 确认后文件夹内视频移回未分类区，若正在查看该文件夹则自动退出。
- **返回键（onBackPressed）**：在文件夹内部先退出文件夹；根层则按系统默认返回。

### 视频条目操作（由 CacheListAdapter 回调驱动）
- **点击视频**：默认播放该缓存视频（通过缓存列表适配器内部的点击处理；本页为虚拟合集场景提供 `onVideoPlayInVirtualCollection`）。
- **虚拟合集连播**（`playVirtualCollection`）：把当前文件夹内所有视频组成「虚拟合集」传入播放器连续播放：
  - 以点击的视频为起始播放位置（`startPageIndex`）。
  - 每个视频取第一个 mp4（或第一个文件）作为视频源，第一个弹幕文件作为弹幕源。
  - PlayerData 用 `TYPE_LOCAL`，标题为「文件夹名（虚拟合集）」。
  - 通过 Intent Extra `videoFileList`、`danmakuFileList` 把全部文件列表传给播放器用于本地分页切换。
- **更新弹幕**（`updateDanmaku`）：读取元数据，若 aid/cid 有效则通过 `PlayerApi.getVideo` 获取弹幕地址并下载到 `danmaku.xml`；对合集内每个分页分别下载各自弹幕；成功提示「弹幕更新成功」。
- **切换清晰度/重新下载**（`switchQuality`）：读取元数据，若 aid 有效则跳转 `QualityChooserActivity`（Extra：`aid`）选择并重新下载新清晰度。
- **查看详情**（`viewVideoDetail`）：aid 有效则跳转 `VideoInfoActivity`（Extra：`aid`）。
- **删除视频**：
  - 单击删除按钮 → 确认对话框「删除视频」→ 确认后删除整个视频目录并从文件夹中移除，提示「删除成功」并刷新。
  - 长按视频 → 先提示「再长按一次删除」；4 秒内再次长按 → 直接删除（跳过确认）。
- **移动到文件夹**（`showMoveToFolderDialog`）：无文件夹则提示「请先创建文件夹」；有则弹出列表对话框（`ListDialogActivity`）显示「文件夹名（N个视频）」，选择后把该视频加入文件夹并刷新。
- **移出文件夹**（`removeVideoFromFolder`）：把视频从当前文件夹移回未分类区，提示「已移出文件夹」并刷新。

### 空态
- 视频与文件夹都为空时显示 `emptyTip` 空提示视图。

---

## DownloadListActivity.kt（下载列表页 / 下载任务管理）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/local/DownloadListActivity.kt`（356 行）

页面角色：展示所有下载任务（单集、分P、合集），支持暂停/恢复/重试/删除，含全局总体进度底栏与实时刷新。

- **页面标题**：「下载列表」。
- **常亮**：onCreate 设置 `FLAG_KEEP_SCREEN_ON`，页面保持屏幕常亮；onDestroy 清除。
- **数据来源**：`DownloadService.getAll()` 从 SQLite 读取全部下载记录（`DownloadSection`）。
- **空列表**：首次为空时 Toast「下载列表为空」并显示空视图（`showEmptyView`）；有数据时隐藏。
- **定时刷新**：`Timer` 每 400ms（首次 300ms 延迟）轮询：
  - 同步全局速度 `DownloadService.speedStr` 与高速模式标志 `isSpeedMode` 到适配器。
  - 若下载服务在运行（`started`），找出所有「正在下载」的项目位置（通过进度映射 `getDownloadProgress` 判断）逐项 `notifyItemChanged` 刷新；否则隐藏底栏。
  - 每次刷新底栏 `updateBottomBar`。
- **点击任务**（`onItemClick`，按状态分派）：
  - `downloading`（下载中）→ `pauseDownload(id)` 暂停该任务（不影响其他并行任务），Toast「已暂停下载」。
  - `paused`（已暂停）→ `resumeDownload(id)` 恢复该任务，Toast「已恢复下载」。
  - `error`（失败）→ 先 `setState(id,"none")` 再 `start(id)` 重试。
  - 其他状态 → `start(id)` 启动/加入调度。
- **长按任务**（`onItemLongClick`）：
  - 首次长按：记录位置与时间，Toast「再次长按删除」。
  - 4 秒内同一位置再次长按 → 确认删除（`deleteSectionItem`）。
- **删除任务**（`deleteSectionItem`）：
  - 若任务正在下载（state 为 downloading 或有进度映射）先 `pauseDownload` 使其退出 IO 循环。
  - 删除该任务文件夹（`FileUtil.deleteFolder`）。
  - `deleteSection(id)` 删数据库记录、清 `pausedMap`、清进度映射。
  - 刷新列表，Toast「删除成功」。不影响其他并行任务。

### 底栏（下载总进度条 `download_bottom_bar`）
- 插入到 SwipeRefreshLayout 父布局底部，并把 SwipeRefreshLayout 设为「在底栏之上」。
- 内容：总进度条（max=1000）、文本「已完成/总任务 (x%) (+并行数)」、总速度、预估剩余时间。
- **总体进度**：`computeOverallProgress` = (已完成 + 失败 + 各下载中项目进度和) / 总工作量；失败计入已结束，不卡进度；中途新增任务会增大总工作量。
- **并行信息**：下载中项目数 >1 时显示「N并行」。
- **总速度**：取 `DownloadService.speedStr`，高速模式加「高速 」前缀；无速度但有下载中显示「准备中...」。
- **剩余时间（ETA）**：解析速度字符串为字节/秒，`剩余字节/速度`；进度≥1 显示「即将完成」；速度或剩余为 0 显示「计算中...」。
- 下载服务未运行或列表为空时底栏隐藏。

### 生命周期
- onDestroy：取消 Timer、清除常亮、置空 `weakRef`。

---

## LocalPageChooseActivity.kt（本地缓存分P选择页）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/local/LocalPageChooseActivity.kt`（152 行）

页面角色：为本地缓存的合集视频展示分页列表，选择某分页播放，或长按删除某个分P。

- **页面标题**：「请选择分页」。
- **顶栏点击**：`finish()` 返回。
- **Intent Extra 输入**：
  - `title`：合集标题（必填）。
  - `pageList`：分页名列表（必填）。
  - `videoFileList`：分页文件路径列表（必填）。
  - `danmakuFileList`：分页弹幕路径列表（必填）。
- **分页媒体映射构建**（`buildPageMediaFiles`）：按「分页目录/`video.mp4` + `audio.m4a`」：
  - 两者都存在 → 主文件为 video.mp4，audio.m4a 作为外部音频轨（DASH 双文件）。
  - 只有 video.mp4 → 单文件。
  - 只有 audio.m4a → 单音频文件。
  - 都没有 → 兼容旧格式，从 `videoFileList` 按序取。
- **点击分页播放**：
  - 构造 `PlayerData(TYPE_LOCAL)`：videoUrl=主文件、danmakuUrl=对应弹幕、title=分页名。
  - `PlayerApi.jumpToPlayer` 后附加 Intent Extra：主文件是 `audio.m4a` 时 `audio_only=true`；存在音频轨时 `audio_track_url=音频路径`。
  - 找不到播放器 Activity 时 Toast「没有找到播放器，请检查是否安装」。
- **长按分页删除**：
  - 首次长按：Toast「再次长按删除」。
  - 4 秒内再次长按同一分页 → 删除该分页目录（`FileUtil.deleteFolder`），并从 pageList/videoFileList/danmakuFileList 同步移除；若分页删空则删除整个合集目录。
  - 用 `notifyItemRemoved` + `notifyItemRangeChanged` 更新列表，Toast「删除成功」。
- **返回刷新**：onDestroy 时若发生过删除（`deleted` 标志），通知栈顶 `LocalListActivity` 刷新列表。

---

## DownloadActivity.kt（通用文件下载页 / APK 下载安装）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/DownloadActivity.kt`（271 行）

页面角色：通用下载执行页，支持下载任意文件（type=0）、下载单集视频+弹幕+封面（type=1）、下载合集分P（type=2），以及 `.bak` APK 下载后自动安装。

- **页面内容**：进度条 `progressView`（高度随百分比变化）+ 文本「描述\n百分比%」，每 100ms 刷新。
- **Intent Extra 输入**：
  - `type`：0=通用文件下载；1=单集视频；2=合集分P。
  - `link`：要下载的主文件 URL。
  - `terminal`：布尔，true 时用 `AppInfoApi.customHeaders`，否则用 `NetWorkUtil.webHeaders`（影响反盗链 header）。
  - `path`（type=0）：目标目录。
  - `title`（type=1/2）：视频/分P 标题（会经 `stringToFile` 转文件安全名）。
  - `parent_title`（type=2）：合集父标题。
  - `danmaku`：弹幕 URL。
  - `cover`：封面 URL。
- **存储权限**：无权限则请求。
- **下载逻辑**：
  - type=0：目标目录不存在则创建；文件名为 `FileUtil.getFileNameFromLink(link)`；调用 `download(link, file, "下载文件中", exitOnFinish=true)`。
  - type=1：目标为「缓存根目录/title」，创建目录，下载弹幕 `danmaku.xml`（先下载解压），封面 `cover.png`（已存在则跳过），主文件 `video.mp4`。
  - type=2：目标为「缓存根目录/parent_title/title」，其余同 type=1。
  - 下载进度：`已下载字节/Content-Length`。
  - 下载失败（IOException）→ Toast「下载失败」并 `finish()`。
- **弹幕下载**（`downdanmu`）：请求弹幕 URL，用 `decompress`（Inflater, nowrap=true）解压后写入 `danmaku.xml`；失败 Toast「弹幕下载失败！」并 finish。
- **下载完成处理**（`handleDownloadComplete`）：
  - 若文件名以 `.bak` 结尾 → 重命名为 `.apk`：
    - 重命名成功 → `installApk`，成功 Toast「下载完成，已尝试安装」，失败则把 apk 移到下载文件夹并 Toast「下载完成，安装失败，已保存到下载文件夹」。
    - 重命名失败 → Toast「下载完成，重命名失败」。
  - 非 `.bak` → Toast「下载完成」。
  - 200ms 后 `finish()` 关闭页面（`finishFlag=true`）。
- **APK 安装**（`installApk`）：Android N+ 用 FileProvider 生成 `content://` URI 并授权；旧系统用 `file://`；启动 `ACTION_VIEW`（`application/vnd.android.package-archive`），带 `FLAG_GRANT_READ_URI_PERMISSION` 与 `FLAG_ACTIVITY_NEW_TASK`；N+ 给所有可处理该 intent 的应用授予读写权限。
- **返回键**：直接 `finish()`。
- **onDestroy**：取消进度 Timer；若未正常完成（`!finishFlag`）：type≠0 且 `downPath` 非空 → 删除整个目标目录（清理未完成下载）；type=0 且 `downFile` 非空 → 删除该文件（下载未完成时清理）。

---

## DownloadService.kt（下载服务，前台 Service + 调度核心）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadService.kt`（1581 行）

页面角色：后台下载服务（前台通知），负责下载队列调度（串行/并行）、进度统计、暂停/恢复/重试/删除、DASH 音视频分离下载、高速分片下载、字幕/弹幕/封面下载、通知栏展示。所有入口通过伴生对象静态方法调用。

### 全局状态与队列（companion object）
- `started`：服务是否在运行。
- `exitCode`、`percent`、`state`、`section`：当前任务的退出码/百分比/状态/当前 section。
- `speedStr`：聚合下载速度字符串；`isSpeedMode`：是否启用高速（分片）模式。
- `batchStats`（DownloadBatchStats）：本批次完成/失败计数。
- `totalBytesDownloaded`：全局累计下载字节（所有并行任务），用于聚合速度采样。
- `activeDownloadsCount`：当前活跃下载任务数。
- `downloadProgressMap`：id→(progress,state,downloadedBytes,totalBytes) 进度映射，供 UI 轮询。
- `pausedMap`：被用户暂停的任务 id 集合。

### 暂停 / 恢复 / 进度 API
- `pauseDownload(id)`：置 `pausedMap[id]=true`，`setState(id,"paused")`；下载线程在下一轮 IO 循环检测到暂停标志退出。
- `resumeDownload(id)`：移除暂停标志，`setState(id,"none")`，并 `start(id)` 触发调度重新拾取。
- `isPaused(id)`：判断是否被暂停。
- `setDownloadProgress(...)`/`getDownloadProgress(id)`/`removeDownloadProgress(id)`：进度映射读写删。
- `getDownloadedBytes()/addDownloadedBytes()/resetDownloadedBytes()`：全局字节累计读写。
- `computeOverallProgress(sections)`：按批次统计与进度映射算总体进度。
- `getActiveRemainingBytes(sections)`：仍在下载项目剩余字节合计（用于 ETA）。

### 数据库操作
- `getFirst()`：取下一个待下载任务（优先上次中断的 `firstDown` id，否则取 `state="none"` 的第一条）。
- `getAll()`：返回全部下载记录。
- `deleteSection(id)`：删除某条记录。
- `clear()`：清空下载表。
- `setState(id,state)`：更新任务状态。
- `startReDownload(title,aid,cid,cover,newQn)`：切换清晰度重新下载——删旧记录、删旧 video.mp4/audio.m4a/.DOWNLOADING、`VideoMetaManager.updateQuality` 更新画质元数据，再调用 `startDownload`（封面传空串跳过）。
- `startDownload(title,aid,cid,cover,qn,downloadType,audioUrl)`（单集重载）：去重检查（同 aid+cid 已在队列则提示「该视频已在下载队列中」），插入 `video_single` 记录，创建目录与 `.DOWNLOADING` 标记，写 `.quality`（audio_only 或 qn），保存 `.video_meta.json` 元数据，Toast「已添加（音频）下载」，`start(-1)`。
- `startDownload(parent,child,aid,cid,cover,qn,downloadType,audioUrl)`（分P重载）：同上，type=`video_multi`，路径为「parent/child」。
- `start(first)`：若未运行则置 started=true，启动前台服务（O+ 用 `startForegroundService`），失败则提示「启动下载服务失败，请重试」。
- `resetSpeedSampling()/sampleSpeed()`：批次开始时重置速度采样；周期性采样全局字节得到聚合速度字符串。

### 服务生命周期（onStartCommand）
- 前台通知：创建「哔哩终端下载服务」通知渠道（无声音无振动），`statusBuilder`（「下载视频中」，含进度条、点击跳下载列表）与 `completionBuilder`（「下载完成」）。
- Q+ 以 `FOREGROUND_SERVICE_TYPE_DATA_SYNC` 前台运行。
- 每次启动：`recoverStuckSections()` 把上次遗留的 `downloading` 状态恢复为 `none`（防卡死）并清空进度映射；重置批次统计/字节/速度/活跃数。
- 读取并行下载数 `Aria2Util.getParallelDownloadVideos()`（clamp 1..10）：≤1 走 `sequentialDownload()`，否则 `parallelDownload(n)`。
- 结束后 `refreshDownloadList()`；退出消息：有失败则「N 个任务下载失败，请重试」，否则「全部下载完成」；`stopSelf()`。

### 串行调度（sequentialDownload）
- 循环取 `getFirst()`，逐个 `runDownloadSection`；遇到失败即 `break`，剩余任务保持 `none` 排队，可再次启动续传。

### 并行调度（parallelDownload）
- 信号量（Semaphore）限并行数；循环获取空闲槽位 → 取 `getFirst()` → 立即置 `downloading` + 进度「准备中」防竞态重复调度 → 后台线程 `runDownloadSection`。
- 单个任务失败不影响批次，其余任务继续。
- 无任务但还有活跃下载时 sleep 500ms 再试（允许新加入任务被拾取）；无任务且无活跃下载才真正结束。
- 结束后等待所有活跃下载完成。

### 单任务执行（runDownloadSection / processDownloadSection）
- 成功记 `batchStats.recordSuccess()`；失败且被用户暂停 → 状态保持 `paused` 不记失败、不中断批次；其他失败记 `recordFailure()`、置 `error`（终态，不会被再次拾取）。
- 地址获取：音频下载（isAudioOnly）用 `getVideoDash` 取音频 URL；qn≤64 用 `getVideo`（MP4 单文件）；qn>64 用 `getVideoDash`（DASH 音视频分离）。
- 下载前把画质列表（qnStrList/qnValueList）写入元数据。
- JSON 异常 → `error` + 通知「下载链接获取失败」；网络异常 → `none`（可重试）。
- 按 type 分派：
  - `video_single`/`video_multi` 共用流程，只是路径不同（单集 vs 分P）。
  - **纯音频**：下载 audio.m4a（先写 `audio_new.m4a` 临时文件，成功再替换）。
  - **DASH**：分段进度「视频 0-100% → 音频 0-100%」，都先写临时文件（`video_new.mp4`/`audio_new.m4a`），全部成功后替换正式文件；不合并，保留分离双文件直接播放。
  - **MP4 单文件或无音轨 DASH**：下载单个 `video_new.mp4` 后替换为 `video.mp4`；并删除残留的旧 `audio.m4a`（防止从 DASH 切回 MP4 时播放器误判双文件、旧音频继续播放）。
- 完成后：发完成通知、清进度映射、删 `.DOWNLOADING`、`deleteSection` 删记录、`refreshLocalList()` 通知本地缓存页刷新。
- 附件阶段（`downloadAttachments`）：封面（不存在才下）→ 字幕（`downSubtitles`，非音频下载才下，多个语言各存 `subtitles/语言.json`）→ 弹幕（解压写 danmaku.xml）。DASH 流程附件进度 0→100%（封面 0-40%、字幕 40-60%、弹幕 60-100%），其余流程保持旧比例区间（封面 0.05-0.1、字幕 0.1、弹幕 0.15）。
- `safeReplaceTemp`：备份旧文件→替换→恢复的原子替换，失败时恢复旧文件，保证重新下载不丢旧数据。

### 文件下载（downFile 系列）
- `downFile(...)`：若 `Aria2Util.isEnabled()` → `downFileSpeed`（高速模式，isSpeedMode=true）；否则 `downFileNormal`（普通模式）。
- `downFileNormal`：OkHttp 下载，校验响应码（403 防盗链/URL 过期等非 2xx 判失败），未知总大小时按 512KB 节流推进伪进度（封顶 90%）防进度条卡死；暂停时返回 `ERR_PAUSED`（不清半成品，恢复后覆盖重下）；下载不完整判失败并删除半成品；成功后进度推进到阶段终点。
- `downFileSpeed`：HEAD 请求探测 `Content-Length` 与 `Accept-Ranges`；大小>0、支持 Range 且 >2MB 时走分片（`downFileSpeedSeg`），否则回退单线程（`downFileSpeedSingle`）。
- `downFileSpeedSeg`：动态分片（约每 2MB 一片，上限受 `Aria2Util.getSplit()` 配置约束），每片一个线程（Range 请求，最多重试 3 次，失败重试间隔递增），RandomAccessFile 写盘；轮询进度直到全部分片完成/失败/暂停/取消；任一失败或字节不足 → 回退整文件单线程重下；暂停直接返回 `ERR_PAUSED`。
- `downDanmaku`：请求弹幕 URL，解压后写 danmaku.xml，进度推进 0.05。
- 暂停信号（ERR_PAUSED）在 runDownloadSection 中不会记失败，保持 `paused`。

### 通知与 UI 同步
- `startNotifyProgress`：每 1 秒采样聚合速度并更新前台通知「总进度 X% · 当前任务」，进度条 `setProgress(100,...)`。
- `notifyExit`：Toast 内容 + 取消前台通知 + 发「下载结束」完成通知。
- `notifyCompletion`：Toast + 发单任务完成通知（id 取模避免覆盖）。
- `refreshDownloadList`：通过 `DownloadListActivity.weakRef` 回调刷新下载列表页。
- `refreshLocalList`：通知栈顶 `LocalListActivity` 刷新。

### 退出清理（onDestroy）
- `started=false`、清百分比/状态/速度/进度映射/批次统计/字节/活跃数，取消 Timer。
- 若服务被异常结束（exitCode≠NORMAL）且仍有 section：把该任务置回 `none` 并删除其文件夹（清理不完整下载），发「下载服务已退出」通知。

---

## DownloadBatchStats.kt（批次统计 + 速度采样工具）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadBatchStats.kt`（80 行）

纯逻辑、无 Android 依赖，便于单测。

- **DownloadBatchStats**：
  - `completed`/`failed`：本批次成功/失败任务数（private set）。
  - `recordSuccess()`/`recordFailure()`：自增成功/失败。
  - `reset()`：清零。
  - `overallProgress(activeProgressSum, activeCount, waitingCount)`：总进度 = (已完成 + 失败 + 各下载中项目进度和) / (已完成 + 失败 + 下载中 + 等待中)，结果 clamp 0..1；失败项目视作终态不会卡进度；中途新增任务会增大总工作量。
- **SpeedSampler**（速度采样器，要求单线程采样）：
  - `reset(now)`：重置基准字节/时间。
  - `sample(bytes, now)`：按「(本次字节-上次字节)/间隔秒」算字节/秒；数据不足或间隔 <0.5s 返回 null。
- **formatDownloadSpeed(bps)**：把字节/秒格式化为 `X.X MB/s` / `X.X KB/s` / `X B/s`（中文 Locale）。

---

## AudioPlayerActivity.kt（音频播放器页）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/audio/AudioPlayerActivity.kt`（350 行）

页面角色：B 站音频（音乐）播放器，基于 Android MediaPlayer，支持播放/暂停、进度拖动、播放模式（列表循环/单曲循环/随机）、歌词拉取；下载按钮为占位。

- **Intent Extra 输入**：
  - `sid`：音频 id（必填）。
  - `title`：歌曲标题（用于先显示）。
  - `author`：作者。
  - `cover`：封面地址。
- **异步布局**：`asyncInflate(activity_audio_player)` 后绑定控件；标题支持横向滚动（`isSelected=true`）。
- **音频信息加载**（`loadAudio`）：`AudioApi.getAudioInfo(sid)` 取信息（标题/作者）、`AudioApi.getAudioStream(sid, 2)` 取音质 2 的音频流；无流则提示「获取音频流失败，尝试其他音质」并 `loadAudioFallback`。同时尝试 `AudioApi.getLyric(sid)` 拉歌词（失败忽略）。
- **音质回退**（`loadAudioFallback`）：音质 2 失败依次尝试音质 1、音质 0；都失败提示「无法获取音频流」。
- **开始播放**（`startPlayback`）：MediaPlayer，`CONTENT_TYPE_MUSIC/USAGE_MEDIA`；取 cdns[0] 为数据源，`prepareAsync`；cdns[0] 失败时尝试 cdns[1]，再失败提示「播放失败」。
  - `onPrepared`：开始播放、图标切换为暂停、设置总时长、seekBar max=1000、启动进度轮询。
  - `onCompletion`：按播放模式处理（见下）。
  - `onError`：尝试下一个 CDN 源（`tryNextSource`）。
- **播放/暂停**（`btnPlayPause` / `togglePlayPause`）：播放中→暂停（图标切换、停进度轮询）；暂停→继续播放。
- **播放模式**（`btnMode` / `cyclePlayMode`）：循环切换 0/1/2，持久化到 SharedPreferences `audio_play_mode`，Toast 提示：
  - 0：列表循环（图标 icon_play_12）。
  - 1：单曲循环（图标 icon_audio_only_on）——播完 seekTo(0) 重播。
  - 2：随机播放（图标 icon_audio_only_off）——播完 seekTo(0) 重播（实际与单曲循环相同，仅图标区分；无真正的随机切歌）。
- **播放完成处理**（`onPlaybackComplete`）：模式 0 → 停止并切回播放图标、停进度轮询；模式 1/2 → seekTo(0) 重播。
- **进度拖动**（seekBar）：`max=1000`；拖动中 `isSeeking=true` 并实时更新当前时间文本（`isSeekFromUser`）；停止拖动 → `mediaPlayer.seekTo(progress*duration/1000)`。
- **进度轮询**（`startProgressUpdates`）：每 200ms 若在播放且未拖动则更新进度条与当前时间文本（用户拖动时不覆盖）。
- **下载按钮**（`btnDownload` / `downloadCurrent`）：有音频流则 Toast「下载功能开发中」——**占位未实现**。
- **切换 CDN 源**（`tryNextSource`）：出错时尝试 cdns 中下一个地址（`indexOfFirst{it==currentUrl}` 逻辑，currentUrl 恒为 ""，实际只会尝试 cdns[1]）。
- **时间格式化**：`mm:ss`。
- **释放**：`releasePlayer` 停轮询并 release MediaPlayer；onDestroy 释放。

> 注意：`btnPrev`（上一首）、`btnNext`（下一首）、`coverPlaceholder` 已声明绑定，但**本文件未实现**上一首/下一首/封面逻辑（无点击监听）。

---

## PlaylistActivity.kt（歌单页：我的/热门/榜单）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/audio/PlaylistActivity.kt`（172 行）

页面角色：歌单列表入口，顶部 Tab 切换「我的歌单 / 热门歌单 / 热门榜单」，每类支持分页加载。

- **布局**：TabLayout + ViewPager2，3 个 `PlaylistPageFragment`。
- **Tab 0 - 我的歌单**（`TYPE_MY`）：`AudioApi.getMyPlaylists(page)`。
- **Tab 1 - 热门歌单**（`TYPE_HOT`）：`AudioApi.getHotPlaylists(page)`。
- **Tab 2 - 热门榜单**（`TYPE_RANK`）：`AudioApi.getRankPlaylists(page)`。
- **歌单列表（PlaylistPageFragment）**：
  - 首次进入 `loadMore()` 加载第 1 页。
  - 滚到底部（`!canScrollVertically(1)` 且未到底）→ 加载下一页。
  - 某页返回空 → 置 `bottom=true` 停止加载；异常 Toast「加载失败」。
  - 每项显示：歌单标题、作者（uname）、歌曲数「N首」。
- **点击歌单** → 跳 `PlaylistDetailActivity`，Extra：
  - `mlid`：我的歌单用 `playlist.id`，热门/榜单用 `playlist.menuId`。
  - `title`：歌单标题。
  - `author`：作者。

---

## PlaylistDetailActivity.kt（歌单详情页）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/audio/PlaylistDetailActivity.kt`（125 行）

页面角色：展示某个歌单内的歌曲列表，点击歌曲进入音频播放器。

- **Intent Extra 输入**：
  - `mlid`：歌单 id（必填）。
  - `title`：歌单标题（用作页面标题）。
  - `author`：作者（当前未展示）。
- **页面标题**：`pageName` = 歌单标题。
- **加载详情**（`loadPlaylistDetail`）：`AudioApi.getPlaylistDetail(mlid)` 取歌单；成功后在 `playlist_info` 显示「歌曲数: N  播放: M」；随后逐个 `sid` 调 `AudioApi.getAudioInfo(sid)` 拉取歌曲信息，每拉到一条插入列表。
- **歌曲列表**：显示标题、作者、时长（`mm:ss`）。
- **点击歌曲** → 跳 `AudioPlayerActivity`，Extra：`sid`、`title`、`author`、`cover`。
- 加载失败 Toast「加载歌单失败」。

---

## LyricFragment.kt（歌词展示与同步滚动 Fragment）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/audio/LyricFragment.kt`（184 行）

页面角色：展示某首音频的滚动歌词，高亮当前句并自动滚动到合适位置；需外部注入 MediaPlayer 才能同步。

- **创建**：`LyricFragment.newInstance(sid)`，Extra `sid`=音频 id。
- **加载歌词**（`loadLyric`）：`AudioApi.getLyric(sid)`；有歌词行则填充列表并隐藏「无歌词」提示；无歌词或失败显示 `no_lyric_tip`。
- **注入播放器**（`setMediaPlayer(mp)`）：外部（如音频播放器页）传入 MediaPlayer；非空则 `startSync()` 开始同步。
- **歌词同步**（`startSync` / `syncLyricToPosition`）：每 200ms 读取 `mediaPlayer.currentPosition`，找到「time ≤ 当前位置」的最后一行作为高亮句。
  - 高亮行变化时：更新适配器高亮索引（旧的恢复暗淡、新的高亮）。
  - 若高亮行不在当前可视区（≤首个可见或 ≥末个可见），`smoothScrollToPosition(目标行-3)` 平滑滚动让高亮句处于可视范围。
- **样式**：当前句 alpha=1.0、字号 15sp；其他行 alpha=0.4、字号 12sp。
- **停止同步**（`stopSync`）：移除轮询回调（onDestroyView 时调用）。
- 布局：`fragment_lyric` + 每行 `cell_lyric_line`。

---

## OpusInfoActivity.kt（图文/专栏动态详情宿主页）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/article/OpusInfoActivity.kt`（89 行）

页面角色：动态-图文（Opus）详情宿主，用 ViewPager 承载「内容页 + 评论区」两个 Fragment；旧版动态转发到 DynamicInfoActivity。

- **页面标题**：「文章详情」。
- **Intent Extra 输入**：
  - `id`：opus 的 oid（默认 114514）。
  - `seekReply`：要定位到的评论 id（默认 -1）。
- **加载 Opus**：`TerminalContext.getOpusById(oid)` 观察结果。
  - 若 `opus.type == TYPE_DYNAMIC_OLD_STYLE` → 跳转 `DynamicInfoActivity`（Extra：`id`、`seekReply`）并 finish。
  - 否则构建 ViewPager：
    - Fragment 0：`OpusInfoFragment.newInstance(oid)`（图文内容）。
    - Fragment 1：`ReplyFragment.newInstance(commentId, commentType, stats.reply, seekReply, upInfo.mid)`，并 `setManager(upInfo)`（评论区）。
  - 若 `seekReply != -1` → 默认切到第 1 页（评论区并定位评论）。
  - 加载完成后 `AnimationUtils.crossFade` 淡入内容，并显示分页滑动教程（`TutorialHelper.showPagerTutorial(this, 2)`）。
  - 失败 → 显示错误占位图 `loading_2233_error` 并 `MsgUtil.err`。
- **EventBus**：`eventBusEnabled()` 返回 true；订阅粘性 `ReplyEvent`（空实现）。

---

## OpusInfoFragment.kt（图文内容展示 Fragment）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/article/OpusInfoFragment.kt`（84 行）

页面角色：承载 Opus 图文内容，用 `OpusContentAdapter` 渲染正文；实际按钮/图片/点赞/投币/收藏/评论等交互实现在该 Adapter 中（不在本清单，本文只记录本 Fragment 层面的行为）。

- **创建**：`OpusInfoFragment.newInstance(oid)`，Extra `oid`。
- **横屏适配**：若设置 `ui_landscape` 为 true，按屏幕宽度给列表左右各加 `宽度/6` 内边距（居中阅读）。
- **加载内容**：`TerminalContext.getOpusById(oid)` 观察；成功且已加入 Activity 时用 `OpusContentAdapter(requireActivity(), opus)` 渲染 RecyclerView（`CustomLinearManager`），并让列表获得焦点（便于键盘/遥控操作）；失败 `MsgUtil.err`。
- 内容数据源为 Opus 模型（含富文本/图片），图片查看、复制等交互由 `OpusContentAdapter` 提供。

---

## CollectionInfoActivity.kt（视频合集详情页）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/collection/CollectionInfoActivity.kt`（249 行）

页面角色：展示 UP 主的视频合集（分节分集），点某集进入视频详情；含合集头部信息卡与封面查看。

- **页面标题**：「合集详情」。
- **Intent Extra 输入**：
  - `fromVideo`：来源视频 aid（用于定位当前播放到的分集并滚动定位）。
  - `season_id`：合集 season id（读取，当前未用于加载）。
  - `mid`：UP 主 mid（读取，当前未用于加载）。
- **加载**：`TerminalContext.getVideoInfoByAidOrBvId(fromAid, null)` 取视频信息并取其 `collection`。
  - `collection.sections == null && collection.cards != null` → 用 `CardAdapter`（扁平视频卡片列表）。
  - `collection.sections != null` → 用 `SectionAdapter`（分节分集列表），并计算 `fromAid` 所在分集位置，用 `scrollToPosition` 滚动定位到该集。
  - 两者都无 → `finish()`。
- **CardAdapter（无分节合集）**：
  - 头部（position 0）为 `cell_collection_info`：名称、简介（空则「这里没有简介哦」）、「共N」播放量、封面图。
  - 封面点击 → `ImageViewerActivity`（Extra：`imageList`=[封面]）大图查看。
  - 名称/简介支持长按复制（`StringUtil.setCopy`），简介可识别链接（`StringUtil.setLink`）。
  - 每条视频（`cell_video_list`）点击 → `enterVideoDetailPage(aid, bvid)` 进视频详情。
- **SectionAdapter（分节合集）**：
  - 头部（position 0）同上合集信息卡。
  - 每节一个节标题行（`SectionHolder`，TextView 左缩进）。
  - 每集（`cell_video_list`）显示：标题、播放量（`toWan` 万化）、封面；点击 → `enterVideoDetailPage`。
  - 封面点击 → `ImageViewerActivity` 大图查看。
- 无「追番/订阅/一键播放全部」按钮在本页实现（点击集数才播放）。

---

## SeriesInfoActivity.kt（用户系列详情页）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/series/SeriesInfoActivity.kt`（167 行）

页面角色：展示某用户「系列」（收藏夹式视频合集）的分页列表，支持下拉刷新与加载更多，点视频进详情。

- **页面标题**：系列名（`seriesName`）。
- **Intent Extra 输入**：
  - `type`：系列类型（`series` 或 `season`，默认 `series`）。
  - `mid`：UP 主 mid。
  - `sid`：系列 id。
  - `name`：系列名称（默认「系列详情」）。
- **加载**（`loadData(page)`）：`SeriesApi.getSeriesInfo(type, mid, sid, page, videoList)`。
  - 第 1 页：空则 `showEmptyView`；否则创建 `SeriesVideoAdapter` 并设置。
  - 后续页：`adapter.addData` + `notifyItemRangeInserted`；若返回条数 < `return_ps` 则 `bottom=true`（不再加载更多）。
  - 失败：第 1 页显示空视图；后续页 `loadFail`。
- **下拉刷新**：`setOnRefreshListener { loadData(1) }`。
- **上拉加载更多**：`setOnLoadMoreListener { loadData(it) }`。
- **SeriesVideoAdapter**：
  - 头部（position 0，`cell_collection_info`）：系列名、简介（空则「这里没有简介哦」）、「共N」；封面图点击 → `ImageViewerActivity`（`imageList`=[封面]，封面非空才可点）。
  - 视频行（`cell_video_list`）点击 → `enterVideoDetailPage(aid, bvid)`。

---

## UserSeriesActivity.kt（用户投稿的系列列表页）

文件路径：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/series/UserSeriesActivity.kt`（77 行）

页面角色：列出某 UP 主「投稿的系列」，支持下拉刷新与加载更多。

- **页面标题**：「投稿的系列」。
- **Intent Extra 输入**：`mid`：UP 主 mid。
- **加载**（`loadData(page)`）：`SeriesApi.getUserSeries(mid, page, seasonList)`。
  - 第 1 页：空则 `showEmptyView`；否则创建 `SeriesCardAdapter` 并设置（该适配器在点击时进入 `SeriesInfoActivity`，携带 type/mid/sid/name）。
  - 后续页：从 recyclerView 取现有 `SeriesCardAdapter`，`notifyItemRangeInserted(oldSize, ...)`；`result != 0` 时 `bottom=true`（停止加载）。
  - 失败：第 1 页空视图；后续页 `loadFail`。
- **下拉刷新**：`setOnRefreshListener { loadData(1) }`。
- **上拉加载更多**：`setOnLoadMoreListener { loadData(it) }`。

> 潜在问题：后续页加载时只调用 `notifyItemRangeInserted`，**没有把新数据 addData 进 adapter 的数据源**，可能导致加载更多后列表数据未真正追加（分页 bug）。

---

## 汇总：本组功能要点

- **缓存管理**：两级（未分类+自建文件夹）结构；新建/重命名/拆散文件夹；移动到文件夹/移出；虚拟合集连播；更新弹幕；切换清晰度（重下）；删除（确认或二次长按）；分P选择播放与分P删除。
- **下载管理**：串行/并行（1-10）调度；单任务暂停/恢复/重试/删除；DASH 音视频分离下载；高速分片下载（Aria2，动态分片+失败回退）；总体进度底栏（进度/速度/ETA/并行数）；前台通知进度；崩溃遗留任务恢复。
- **音频播放**：播放/暂停、进度拖动、三种播放模式（列表循环/单曲循环/随机，但 1/2 实际同为单曲重播）；音质回退；歌词滚动同步；歌单（我的/热门/榜单）与歌单详情；下载按钮为占位未实现；上一首/下一首未实现。
- **文章/图文**：ViewPager 承载内容+评论区；旧版动态转发；横屏居中；评论定位（seekReply）。
- **合集/系列**：合集详情（分节分集、封面大图、复制、点集进详情）；系列详情（分页、刷新、加载更多、封面大图）；用户系列列表（分页；含分页数据未追加的潜在 bug）。
