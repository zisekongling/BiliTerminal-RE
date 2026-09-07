# ReBiliClient UI 适配层（adapter）与自定义控件（ui/widget）及周边组件深度代码审查报告

- 审查范围：`adapter/`（含 article/dynamic/favorite/message/user/video/viewpager 子目录）、`ui/`（含 theme/widget/widget/recycler）、`player/`、`service/`、`event/`、`listener/`、`helper/`（CustomGlideModule、TutorialHelper、DownloadSqlHelper）
- 审查日期：按版本 26.08.14（vibe coding 产物，代码大量 AI 生成）
- 结论：发现 **62** 个问题（Critical 5 / High 12 / Medium 26 / Low 10 / Info 9）。最集中的问题域为 **DownloadService 并发与文件安全**、**ViewHolder 复用状态残留**、**RecyclerView 非法通知**、**后台线程直接操作 UI 数据源**、**幻觉功能/空壳实现**。
- 说明：所有行号以本次读取时文件为准；未修改任何源代码。

---

## Critical（崩溃 / 数据错误 / 安全）

### 1. `app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadService.kt:501-521` —— `start()` 的 `started` 标志无同步，存在双批次竞态
- **问题描述**：`start()` 先检查 `if (started) return` 再 `started = true`，`started` 是普通 `@JvmStatic var`，无 volatile/synchronized。两个线程（如用户快速连点"添加下载"触发两次 `startDownload` → 两次 `CenterThreadPool.run`）可同时通过检查，各自 `startForegroundService`，导致 `onStartCommand` 被调用两次、跑起**两个并发的批次循环**（`sequentialDownload`/`parallelDownload` 各一份）。
- **影响**：两个循环都执行 `getFirst()` 取 `state="none"` 的同一条记录，**同一视频被两个线程同时下载到同一个临时文件**（`video_new.mp4`），`resetFile` 互相截断 → 文件损坏、合并/播放失败；进度映射互相覆盖。
- **修复建议**：`start()` 用 `synchronized` 或 `AtomicBoolean.compareAndSet` 保护；并在 `onStartCommand` 入口加"已有批次在跑则直接 return"的幂等保护（如用 `startId`/`AtomicInteger` 判断当前活跃批次）。

### 2. `app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadService.kt:1392-1435` —— 分片下载 `join(30000)` 超时后回退单线程，与仍在写盘的分片线程并发写同一文件
- **问题描述**：`downFileSpeedSeg` 轮询退出后对每个分片线程 `t.join(30000)`；若某分片线程在慢网络上阻塞超过 30s，join 超时返回，代码继续走到 `downFileSpeedSingle(url, file, ...)` 回退逻辑，而 `downFileSpeedSingle` 开头 `resetFile(file)` 会**删除并重建文件**——此时超时的分片线程仍在通过自己的 `RandomAccessFile` 往旧 inode 写数据。
- **影响**：同一文件被两个线程并发写入 → 文件损坏；且回退下载成功后分片线程可能继续写，静默产生脏数据。
- **修复建议**：回退前必须保证所有分片线程已终止（如 `interrupt()` + 带标志的循环退出并再次 join），或给每个分片文件使用独立临时文件名、全部完成后合并；至少回退前对文件加写锁。

### 3. `app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadService.kt:1564-1577` —— `onDestroy` 异常退出时 `FileUtil.deleteFolder(folder)` 整目录删除，误删已完成文件
- **问题描述**：批次循环抛异常（`exitCode != NORMAL`）时，`onDestroy` 对**最后一个 `section` 的整个下载目录**执行 `FileUtil.deleteFolder(folder)` 并 `setState(id,"none")`。该目录可能同时包含本次未完成文件与**之前已下载完成的 video.mp4/audio.m4a/cover.png/danmaku.xml**（尤其"切换清晰度重下"场景，旧文件被 `safeReplaceTemp` 特意保留）。
- **影响**：一次意外崩溃/未知异常即导致用户已下载完成的视频整目录被删，**数据丢失**。
- **修复建议**：只删除本次会话产生的临时文件（`*.new.*`、`.DOWNLOADING` 等），不要删除整个目录；或记录本次实际写入的文件清单后按清单清理。

### 4. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/ReplyAdapter.kt:307-311` —— 点赞回调中 `(context as Activity)` 强转未捕获，ClassCastException 崩溃
- **问题描述**：`ReplyAdapter` 构造函数接收的是 `Context`，但点赞点击里 `CenterThreadPool.run { ... (context as Activity).runOnUiThread {...} }` 直接强转 Activity。该强转位于 try（只捕获 IOException/JSONException）**之外**（第 310 行），一旦调用方传入非 Activity 的 context（Fragment 的 `requireContext()` 实际是 Activity 包装 context 可用，但若未来从 service/receiver 或 app context 使用即崩溃），后台线程抛 ClassCastException。
- **影响**：点赞/登录提示路径直接崩溃；同类强转在 `ReplyAdapter` 第 317/336/370/386/389 行、`DynamicHolder` 第 678/688/696/706 行、`ArticleContentAdapter`、`OpusContentAdapter` 中大量存在，是系统性风险。
- **修复建议**：统一改为持有 `WeakReference<Activity>` 或在构造时校验 `context is Activity`，非 Activity 时不走点赞路径。

### 5. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/message/PrivateMsgAdapter.kt:328` —— `content_array.get(0)` 越界未被捕获，渲染系统消息时崩溃
- **问题描述**：`PrivateMessage.TYPE_SYSTEM` 分支直接 `(msg.content_array.get(0) as JSONObject)`，外层 try 只捕获 `JSONException`（第 350 行）。若 `content_array` 为空数组（服务端数据异常），抛 `IndexOutOfBoundsException` 不被捕获。
- **影响**：私信列表遇到异常系统消息直接崩溃（崩溃点在整个 bind 内，会拖垮整条列表渲染）。
- **修复建议**：改为 `if (msg.content_array.length() > 0)` 守卫，或外层 catch 改为 `Exception`。

---

## High（明显逻辑 bug）

### 6. `app/src/main/java/com/RobinNotBad/BiliClient/player/PlayerControlDelegate.kt:215-225, 282-284` —— 长按标志 `isLongPressing` 置 true 后永不复位，一次长按后所有手势失效
- **问题描述**：`onLongPress` 置 `isLongPressing = true`，`onScroll` 开头 `if (isLongPressing) return false`；但 `onDown` 只重置了 `isVolumeGesture/isBrightnessGesture/isSeekGesture`，**从未复位 `isLongPressing`**。
- **影响**：播放器任意一次长按之后，所有滑动手势（调音量/亮度/进度）全部失效，直到 Activity 重建。
- **修复建议**：在 `onDown` 中加 `isLongPressing = false`；或监听 `ACTION_UP/CANCEL` 复位。

### 7. `app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadService.kt:819-822, 663-728` —— IO 失败置 `state="none"`，并行模式下无限重试同一失败任务
- **问题描述**：`processDownloadSection` 拉取链接时 `IOException` → `setState(id, "none")` 并返回 false；`parallelDownload` 调度循环只看 `state="none"` 的任务，失败任务立刻被再次拾取 → 再次拉链接失败 → 循环往复，**服务不退、网络请求打不停**（串行模式因 `break` 逃过此劫）。
- **影响**：网络异常时下载服务变成死循环重试，耗电、耗流量、阻塞其他任务。
- **修复建议**：网络类失败设置重试上限（如连续失败 N 次置 `error`），或临时标记"本轮已尝试过"避免同轮重复拾取。

### 8. `app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadService.kt:1146-1155` —— `downSubtitles` 中 `createNewFile()` 失败（文件已存在）直接返回 `ERR_FILE`，整个下载中止
- **问题描述**：下载字幕时 `val subtitleFile = File(subtitleFolder, subtitleLink.lang + ".json"); if (!subtitleFile.createNewFile()) return ERR_FILE`。**重新下载/续传时字幕文件已存在，`createNewFile()` 返回 false**，字幕阶段直接判定失败，连带封面、弹幕、视频全部中止。
- **影响**：任何一次字幕残留都会让该视频永远无法重新下载（"该视频已在下载队列中"之外的二次下载路径直接失败）。
- **修复建议**：存在即跳过（`if (!subtitleFile.exists())`）或先删除再创建。

### 9. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/QualitySelectorAdapter.kt:21-27, 33` —— `setData` 里 `selectedItemIndex = -1` 触发 setter → `notifyItemChanged(-1)`
- **问题描述**：`selectedItemIndex` 的 setter 会 `notifyItemChanged(previous)` + `notifyItemChanged(value)`；`setData` 第一行 `this.selectedItemIndex = -1` 直接触发 `notifyItemChanged(-1)`。
- **影响**：RecyclerView 收到负位置通知，导致 "Invalid item position -1" 类异常/列表不一致（每次打开画质选择器都会走到）。
- **修复建议**：setter 内对 `value < 0` 做守卫（`if (value < 0) return`），或 setData 中先置空再整体 `notifyDataSetChanged()`。

### 10. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/MenuSettingAdapter.kt:156-183` —— 拖拽移入未启用区后 `notifyItemMoved` 目标索引未考虑 `removeAt` 位移
- **问题描述**：`onMove` 中先 `enabled.removeAt(fromIndex)` 再 `notifyItemMoved(from, toFinal)`。拖到"未启用"标题时 `toFinal = to` 未减 1，但移除后所有下游行号已左移一位，通知的目标位置与实际数据位置错位（拖到未启用项时虽减了 1，但 `disabled.add(di, key)` 插入后未启用区整体又右移，同样错位）。
- **影响**：拖拽后列表与数据不一致，触发 RecyclerView "Inconsistency detected" 崩溃或项位置错乱。
- **修复建议**：统一先改数据再按**改完后**的真实目标行号计算 `notifyItemMoved`，或在 `onMove` 里直接 `notifyDataSetChanged()`（本场景数据量小，性能可接受）。

### 11. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/SettingsAdapter.kt:264-321` —— `InputHolder` 每次 bind 都 `addTextChangedListener` 且从不移除，watcher 叠加泄漏
- **问题描述**：`input_int/input_float/input_string` 三个分支都在 bind 里 `input.addTextChangedListener(object : TextWatcher {...})`，无 `removeTextChangedListener` 前置清理（对比 `InteractionDebugAdapter` 的做法）。
- **影响**：item 滚出/回收再 bind 后旧 watcher 仍然存活：a) 内存泄漏（watcher 持有 `settingSection` 与 EditText 互相引用）；b) 每次输入触发 N 个 watcher 写 N 次 SharedPreferences；c) 旧 watcher 捕获的是**上一次 bind 的 settingSection**，把 A 项输入写到 B 项设置里——设置项互相污染。
- **修复建议**：bind 开头 `input.removeTextChangedListener(watcher)`，并像 `InteractionDebugAdapter` 一样把 watcher 存为 holder 字段复用。

### 12. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/ReplyAdapter.kt:365-372` —— 后台线程直接 `replyList.removeAt()` 修改 UI 数据源
- **问题描述**：删除回调里 `CenterThreadPool.run { ... replyList.removeAt(realPosition); runOnUiThread { notifyItemRemoved(...) } }`。`replyList` 是普通 `ArrayList`，删除发生在后台线程，而 UI 线程的 `onBindViewHolder` 同时在读它。
- **影响**：ArrayList 非线程安全，并发读写可能读到半更新状态 → 偶发 `IndexOutOfBoundsException` / 数据错位 / bind 显示错误项。
- **修复建议**：`removeAt` 与 `notifyItemRemoved` 一起放到 `runOnUiThread` 内执行（数据修改必须在主线程）。

### 13. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/DynamicHolder.kt:87-133` —— 删除监听闭包捕获 `finalPosition`，列表变化后越界
- **问题描述**：`getDeleteListener` 闭包捕获 bind 时的 `finalPosition`，在后台线程执行 `dynamicList[finalPosition]`、`removeAt(finalPosition)`。用户长按两次期间若列表被分页刷新/其他删除操作改变，位置即失效。
- **影响**：`IndexOutOfBoundsException` 崩溃（后台线程未捕获到列表索引异常，只有 IOException 被捕获）。
- **修复建议**：改为用 `dynamicId` 定位，删除前检查 `finalPosition < dynamicList.size`；同 #12 把数据修改挪回主线程。

### 14. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/UserDynamicAdapter.kt:223-228` —— `official_signs[userInfo.official]` 服务端可控下标
- **问题描述**：`official != 0` 时直接用 `userInfo.official` 索引 10 长度的数组。official 值来自服务端，若大于 9（或为负）则 `ArrayIndexOutOfBoundsException`。
- **影响**：用户信息页渲染崩溃（整个列表绑定失败）。
- **修复建议**：`if (official in 1..official_signs.size-1)` 守卫，否则显示兜底文案。

### 15. `app/src/main/java/com/RobinNotBad/BiliClient/player/PlayerControlDelegate.kt:180-182` —— `setBrightness` 只更新 StateFlow，从不应用到窗口，亮度手势是死功能
- **问题描述**：手势 onScroll 调 `delegate.setBrightness(...)`，该方法仅 `_controlState.update { currentBrightness = ... }`；全项目 grep `currentBrightness` 只有定义与赋值，**没有任何地方读取它去设置 `window.attributes.screenBrightness`**。
- **影响**：右侧滑动"调亮度"完全无效果（幻觉功能）。
- **修复建议**：在持有 Window 的层消费 `currentBrightness` 并写回 `window.attributes.screenBrightness`，或移除该手势。

### 16. `app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/recycler/WrapContentLinearLayoutManager.kt:11-25` —— 注释宣称"正确测量子项使 wrap_content 生效"，实现却是空壳
- **问题描述**：类文档写"用于嵌套在ScrollView中的RecyclerView，正确测量所有子项高度，使wrap_content生效"，但实现只有一个 try/catch 包住 `super.onLayoutChildren`，**没有任何自定义测量代码**，与普通 `LinearLayoutManager` 行为完全一致；且全项目 grep 无任何引用（死代码）。
- **影响**：a) 嵌套滚动 wrap_content 失效的真实问题并未被解决（幻觉修复）；b) `catch (Throwable) { /* ignore */ }` 吞掉所有布局异常，掩盖真实 bug。
- **修复建议**：要么实现真正的 wrap_content 测量（`onMeasure` 遍历子项累加高度），要么删除该类并在文档中说明。

### 17. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/article/OpusContentAdapter.kt:348-352` —— `TYPE_VIDEO` / `TYPE_ARTICLE` 分支为空实现
- **问题描述**：`onBindViewHolder` 中 `OpusParagraph.TYPE_VIDEO -> {}` 与 `OpusParagraph.TYPE_ARTICLE -> {}` 两个 when 分支完全为空（onCreateViewHolder 里却为它们 inflate 了 `cell_dynamic_video`/`cell_article_list`）。
- **影响**：Opus 动态里含视频卡片/文章卡片时渲染空白占位（item 有高度但无内容），用户看到空白行。
- **修复建议**：补全渲染逻辑，或在解析层过滤这两类段落（至少 inflate 后隐藏 itemView 而不是留白）。

---

## Medium（健壮性 / 资源问题）

### 18. `app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/RotaryRecyclerView.kt:43`、`RotaryScrollView.kt:43` —— 每次旋转事件都 `requestFocus()`，抢夺输入焦点
- **问题描述**：旋钮滚动时调用 `requestFocus()`；用户在搜索框/输入框打字时转动旋钮，焦点被抢走，输入中断。
- **修复建议**：仅在列表本身需要焦点时请求，或去掉 `requestFocus()`。

### 19. `app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/RotaryRecyclerView.kt:25-31`（及 RotaryScrollView/RotaryNestedScrollView）—— 设置只在 `onAttachedToWindow` 读取一次
- **问题描述**：`rotaryEnabled`/`scrollMultiple` 只在 attach 时从 SharedPreferences 读取；用户运行中改"旋转开关/倍率"设置，已挂载的视图不会生效，必须重启页面。
- **修复建议**：监听设置变化（如回调/EventBus）并重读。

### 20. `app/src/main/java/com/RobinNotBad/BiliClient/player/DanmakuManager.kt:84-106` —— protobuf 弹幕 `Inflater.inflate` 只调一次 + 固定 20 倍缓冲
- **问题描述**：`val output = ByteArray(deflatedData.size * 20)` 后仅 `inflater.inflate(output)` 一次。若解压后数据超过 20 倍（或恰好撑满缓冲且未 finished），**剩余数据被静默丢弃**，弹幕截断；正确做法是循环 inflate 直到 `finished()`。另外 `safeCallOrDefault("danmaku_parse", BiliDanmukuParser().apply{...}){...}` 的默认参数在 Kotlin 中**调用时即求值**，parser 被创建两次，第一个被丢弃（`sharedPreferences` 白设）。
- **影响**：部分视频弹幕缺失/截断；无谓对象分配。
- **修复建议**：参照 `DownloadService.decompress` 的 while(!finished) 循环；去掉 safeCallOrDefault 的双重创建。

### 21. `app/src/main/java/com/RobinNotBad/BiliClient/helper/sql/DownloadSqlHelper.kt:8-36` —— 每次操作新建 SQLiteOpenHelper 并立即 open/close，多线程写并发
- **问题描述**：`DownloadService.getFirst/getAll/setState/deleteSection/clear` 每次调用都 `new DownloadSqlHelper(BiliTerminal.context)` 再 open/close。并行下载多个线程同时写 `download.db`，SQLite 文件锁竞争 → 偶发 `SQLiteDatabaseLockedException`（被 catch 吞掉，`MsgUtil.err`）。
- **影响**：并发下载时状态更新偶发失败，进度/暂停状态丢失。
- **修复建议**：helper 改为单例持有，写操作串行化（如单线程执行器）。

### 22. `app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadService.kt:1248-1270` —— 暂停逻辑注释与实现矛盾：注释"不清理半成品"，实际删除
- **问题描述**：注释称"用户暂停：不视为错误，不清理半成品，恢复后重新下载覆盖"，但随后 `if (result != NORMAL && fileIncomplete) file.delete()` —— `ERR_PAUSED` 且文件不完整时**临时文件被删除**。
- **影响**：恢复下载只能从头重下（浪费流量）；注释误导后续维护者。
- **修复建议**：暂停时保留临时文件（result==ERR_PAUSED 不删除），恢复时续传或从断点继续。

### 23. `app/src/main/java/com/RobinNotBad/BiliClient/player/IjkPlayerBridge.kt:226-240` —— 进度轮询在 Main dispatcher 每 250ms 调 native `currentPosition`
- **问题描述**：`scope = CoroutineScope(Dispatchers.Main)`，轮询循环在主线程执行 `player.currentPosition`（JNI 调用）。
- **影响**：主线程每 250ms 一次 native 调用，低端机滚动/动画时可能卡顿；状态更新本身会触发 UI 刷新。
- **修复建议**：轮询放到 `Dispatchers.Default`/IO，用 `MutableStateFlow.update` 线程安全地更新。

### 24. `app/src/main/java/com/RobinNotBad/BiliClient/player/IjkPlayerBridge.kt:247-254` —— `release()` 在主线程执行原生 stop/reset/release
- **问题描述**：`release()` 无线程切换直接调用 `IjkMediaPlayer.stop/reset/release`，这些是重量级 native 调用。
- **影响**：退出播放页时主线程卡顿（ANR 风险场景之一）。
- **修复建议**：release 移入后台线程（注意 setDisplay(null) 等仍回主线程）。

### 25. `app/src/main/java/com/RobinNotBad/BiliClient/player/IjkPlayerBridge.kt:180-184` —— `start()` 在 mediaPlayer 为 null 时仍置 `isPlaying=true`
- **问题描述**：`mediaPlayer?.start()` 对 null 无操作，但 `_state.update { isPlaying = true }` 无条件执行。
- **影响**：未创建播放器时 UI 显示"播放中"；`togglePlayPause` 状态错乱。
- **修复建议**：`mediaPlayer ?: return` 后再更新状态。

### 26. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/message/NoticeHolder.kt:100-111` —— 每次 bind 向 `extraCard` inflate 新子卡片，rebind 不清理会堆叠
- **问题描述**：`VideoCardHolder(View.inflate(context, R.layout.cell_dynamic_video, extraCard))` / `ReplyCardHolder(...)` 直接把新 View inflate 进 `extraCard`，无去重。`NoticeAdapter.onViewRecycled` 虽有 `removeAllViews()`，但 `notifyDataSetChanged` 触发的原地 rebind（未回收）会不断追加子视图。
- **影响**：消息列表局部刷新时卡片视图重复堆叠、内存增长。
- **修复建议**：bind 开头 `extraCard.removeAllViews()`（与 onViewRecycled 一致）。

### 27. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/TimelineAdapter.kt:46-76` —— 每次 bind `removeAllViews()` + 无条件 Glide load
- **问题描述**：每 bind 先 `episodesLayout.removeAllViews()` 再逐个重新 addView，且封面 `requestManager...load(coverUrl)` 无条件执行（无 URL 对比），即使复用同一数据也重新发请求、重排布局。
- **影响**：滚动时频繁重排与图片重载，列表滚动卡顿。
- **修复建议**：按 URL 去重加载；子视图池复用而非 removeAllViews（或仅更新差异项）。

### 28. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/article/ArticleCardHolder.kt:51-58`、`adapter/video/DownloadAdapter.kt:135-146` —— Glide 每次 bind 无条件 load，无 URL 去重
- **问题描述**：封面加载不比较上次 URL，每次绑定都重新发起请求（Glide 虽命中缓存但仍有请求开销与跨页 transition 闪烁）。
- **影响**：列表滚动图片闪烁、轻微卡顿。
- **修复建议**：仿照 `VideoCardHolder.lastCoverUrl` 做 URL 去重。

### 29. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/user/FollowGroupAdapter.kt:292-297` —— 展开箭头 ObjectAnimator 不取消，视图回收后动画残留
- **问题描述**：`animateRotation` 每次 bind 直接 `animator.start()`，无 `animator.cancel()`；若视图被回收/复用，旧动画继续作用于新绑定的 ImageView，且连续点击产生重叠动画。
- **影响**：展开/收起时箭头旋转错乱，动画泄漏。
- **修复建议**：把 animator 存 holder 字段，bind 时先 cancel 再 start。

### 30. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/CacheListAdapter.kt:246-254`（及 FolderAdapter.kt:109-116、LocalVideoAdapter.kt:163-170）—— 滑动过程中 `notifyItemChanged` 重绑，正在进行的触摸手势状态丢失
- **问题描述**：MOVE 中滑动超过阈值即 `notifyItemChanged(adapterPos)` 重绑该 item，`setOnTouchListener` 被替换为新监听器（startX/isSwiping 归零），同一手势后续 MOVE/UP 走新监听器。
- **影响**：快速滑动时设置面板打开/关闭抖动、点击判定错乱；滑动性能差（每帧重绑）。
- **修复建议**：滑动手势期间不要重绑，仅更新面板 View 的 visibility；或把滑动状态提升为 adapter 字段。

### 31. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/article/OpusContentAdapter.kt:385-387` —— `itemView.visibility = View.GONE` 隐藏投票卡片项
- **问题描述**：voteId==0 时把 RecyclerView item 自身置 GONE。GONE 只隐藏内容，item 仍占据列表槽位 → 列表出现空白行。
- **修复建议**：解析时过滤 voteId==0 的段落，而不是 GONE itemView。

### 32. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/article/ArticleContentAdapter.kt:344-347` —— "br" 行设置的 `minimumHeight` 在复用时不复位
- **问题描述**：`"br" -> textView.minimumHeight = ToolsUtil.dp2px(6f)`；该 TextView 被复用到普通行时 minimumHeight 残留。
- **影响**：部分文本行高度异常（多 6dp 空白）。
- **修复建议**：else 分支重置 `minimumHeight = 0`（并重置 alpha，虽然 alpha 有重置）。

### 33. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/UserDynamicAdapter.kt:365-404` —— 用户信息卡内嵌套竖向 RecyclerView（充电公示列表）
- **问题描述**：`electricUserList` 是套在 RecyclerView item 里的竖向 RecyclerView，LinearLayoutManager 的 wrap_content 高度不可靠，且每次 bind 都新建 `LinearLayoutManager`/`ElectricUserAdapter`。
- **影响**：充电公示列表可能只显示一行/高度异常；滚动性能差。
- **修复建议**：固定高度（如 maxHeight + 内部滚动），adapter 复用。

### 34. `app/src/main/java/com/RobinNotBad/BiliClient/helper/TutorialHelper.kt:152-159` —— `showPagerTutorial` 中 textView 可能为 null
- **问题描述**：`activity.findViewById<TextView>(R.id.text_tutorial_pager)` 未判空即 `textView.visibility = ...`；不是所有 Activity 布局都有该控件。
- **影响**：调用即 NPE 崩溃（在 runOnUiThread 内，无法被外部捕获）。
- **修复建议**：判空 return。

### 35. `app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/HighEnergyProgressBar.kt:89-123` —— onDraw 每次分配 Path 对象
- **问题描述**：`drawHighEnergy` 每帧 `Path()` × 2；拖动进度条/seek 时 onDraw 高频触发。
- **影响**：GC 抖动，播放器拖动进度卡顿。
- **修复建议**：Path 提升为成员复用，仅在数据变化时重建。

### 36. `app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/BatteryView.kt:21-68` —— onDraw 每次分配 Paint/Rect
- **问题描述**：Paint 与 3 个 Rect 在 onDraw 内 new（虽打了 `@SuppressLint("DrawAllocation")` 眼不见为净）。
- **影响**：若放在播放器常驻 overlay，每帧分配。
- **修复建议**：成员缓存；另 `setPower` 未做 >100 上限钳制（fill 会超出电池框）。

### 37. `app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/RadiusBackgroundSpan.kt:60-62` —— `getCustomTextPaint` 每次 measure/draw 都 new TextPaint
- **问题描述**：`TextPaint(srcPaint)` 每次调用创建副本，文本测量与绘制各一次。
- **影响**：大量评论区 span 渲染时的分配压力。
- **修复建议**：缓存按 paint 参数 key 的 TextPaint。

### 38. `app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/MarqueeTextView.kt:28-29` —— `isFocusableInTouchMode = true` 抢焦点
- **问题描述**：所有启用了跑马灯设置的 TextView 都可触摸获取焦点；列表里的跑马灯 TextView 会抢走 EditText/搜索框焦点，点击即弹输入法。
- **影响**：输入类页面焦点错乱。
- **修复建议**：去掉 `isFocusableInTouchMode`，跑马灯仅靠 `isSelected` 即可。

### 39. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/ReplyAdapter.kt:142-144` —— 昵称 VIP 颜色只设不复位 + `Color.parseColor` 无保护
- **问题描述**：仅当 `vip_nickname_color` 非空时 `setTextColor(...)`；复用 ViewHolder 时上一条 VIP 用户的颜色会**残留在普通用户行**。同时 `Color.parseColor` 对服务端非法颜色字符串抛 `IllegalArgumentException` 直接崩溃（对比 `UserListAdapter` 的 try/catch + 缓存做法）。
- **影响**：评论区昵称颜色错乱；极端数据下崩溃。
- **修复建议**：else 分支复位默认色；parseColor 包 try/catch 并缓存。

### 40. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/SettingsAdapter.kt:32-37, 372-382` —— `listChooseLauncher`/`ListChooseExtra.onSelect` 死代码，`startActivityForResult(1001)` 结果无人处理
- **问题描述**：类里声明 `listChooseLauncher` 并暴露 setter，但 bind 里用的是 `activity.startActivityForResult(intent, 1001)`；`ListChooseExtra.onSelect` 也从未被调用。结果回传完全依赖外部 Activity 的 onActivityResult 魔法数字。
- **影响**：设置页"列表选择"项点击后结果处理链路断裂/不可控；死代码误导。
- **修复建议**：统一走 `listChooseLauncher`（registerForActivityResult），删除未用的 onSelect。

### 41. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/favorite/FavoriteFolderAdapter.kt:78-87` —— `folderList` 为空时 bind 直接 return，"新建收藏夹"按钮无点击事件
- **问题描述**：`if (folderList.size == 0) return` 在 holder 分发**之前**，导致空列表时 position 0 的 `CreateButtonHolder` 永远不会设置点击监听。
- **影响**：无收藏夹时"新建收藏夹"按钮是死的（用户无法从空态创建）。
- **修复建议**：把空列表判断放到 CreateButtonHolder 分支之后，或对 position==0 单独放行。

### 42. `app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/recycler/CustomGridManager.kt:26-32`、`CustomLinearManager.kt:25-31` —— catch Throwable 吞掉布局异常
- **问题描述**：`catch (e: Throwable)` 捕获一切（含 OutOfMemoryError/StackOverflowError），仅弹 MsgUtil 后继续。
- **影响**：掩盖真实布局 bug（如 inconsistent RecyclerView 状态），且布局失败后列表可能处于半渲染状态；吞 OOM 会延续问题。
- **修复建议**：只 catch 可预期的 `IndexOutOfBoundsException`，且记录完整堆栈。

### 43. `app/src/main/java/com/RobinNotBad/BiliClient/adapter/dynamic/DynamicAdapter.kt:29-30` —— 构造时强转 Activity 并持有
- **问题描述**：`private val dynamicActivity: DynamicActivity = context as DynamicActivity` 与 `writeDynamicLauncher = dynamicActivity.writeDynamicLauncher` 在构造时执行，adapter 强持有 Activity 与 ActivityResultLauncher。
- **影响**：adapter 生命周期长于 Activity 时泄漏 Activity；context 非 DynamicActivity 时构造即崩溃。
- **修复建议**：WeakReference 持有 Activity，launcher 延迟获取。

---

## Low（风格 / 性能小问题）

### 44. 多处 adapter —— 每次 bind 新建匿名监听器（无状态泄漏，纯分配浪费）
- 涉及：`ReplyAdapter.kt:90/106/276/287/300/307/405`、`DynamicHolder.kt:496/519/539/551/581/608/621/672`、`LiveCardAdapter.kt:52-61`、`PageChooseAdapter.kt:32-42`、`HotSearchAdapter.kt:55-59`、`PrivateMsgSessionsAdapter.kt:103-113`、`DownloadAdapter.kt:85-96` 等。
- **修复建议**：无状态点击统一在 `onCreateViewHolder` 设置一次 + `bindingAdapterPosition` 取位置（参照 `UserListAdapter.Holder.init` 的典范写法）。

### 45. `adapter/video/VideoCardAdapter.kt:37-40`（及 HistoryVideoCardAdapter.kt:45-48）—— `getItemId` 用 `bvid.hashCode()` 当稳定 id
- hashCode 碰撞 → 两个不同视频共享 id，`setHasStableIds(true)` 下 ViewHolder 复用错乱。
- **修复建议**：无 aid 时返回 `bvid.hashCode().toLong()` 与 aid 区段错开，或干脆关闭 stableIds。

### 46. `ui/widget/recycler/BaseAdapter.kt:52-56` —— `preposeItem` 后 `notifyItemRangeChanged(headerCount, itemCount)` 全量重绑；`getAllData()` 直接暴露可变内部列表
- 全量重绑浪费；外部拿到 `getAllData()` 后随意 add/remove 不会通知 RecyclerView → 不一致。
- **修复建议**：改为 `notifyItemRangeInserted` 精确定位；`getAllData` 返回不可变视图。

### 47. `adapter/video/MediaEpisodeAdapter.kt:40-44` —— `setData` 时旧列表为空仍触发 `selectedItemIndex = 0` 的 setter（`notifyItemChanged(0)`）
- 空列表上通知 position 0 → 潜在 "Inconsistency detected"。
- **修复建议**：setData 内直接字段赋值（绕过 setter）再整体 notify。

### 48. `service/DownloadService.kt:1432-1435` —— 分片回退单线程重下时 `addDownloadedBytes` 重复计数
- 分片阶段已计字节 + 回退单线程再次全量计数 → 通知栏聚合速度虚高。
- **修复建议**：回退前从 totalBytesDownloaded 扣减已计分片字节，或引入按 section 去重。

### 49. `service/DownloadService.kt:599-603` —— `onStartCommand` 收到 null intent 直接 `return START_STICKY`，重启后不再处理任何任务
- 系统杀进程后 sticky 重启，下载队列永远不会被自动恢复执行，服务空转。
- **修复建议**：null intent 时主动重新 `startNotifyProgress` + 启动批次。

### 50. `service/DownloadService.kt:1119-1123` —— `notifyCompletion` 用 `id % 100 + 100` 作通知 id
- 超过 100 个任务时通知互相覆盖（次要）。
- **修复建议**：用递增 id 或 Long 截断。

### 51. `adapter/viewpager/ViewPagerFragmentAdapter.kt:7-8` —— 使用已废弃的 `FragmentStatePagerAdapter(fm)` 构造函数
- 建议换 `FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT)`。

### 52. `adapter/article/OpusContentAdapter.kt:229/241/265/274/301` —— 遗留 `Log.e("debug-底部", ...)` 调试日志
- 生产包打印点赞/投币参数，含用户 mid，属调试残留。
- **修复建议**：删除或降级为 debug 级别。

### 53. `adapter/SettingsAdapter.kt:226` —— `ChooseHolder` 中 `settingSection.extra as Array<String>` 硬转换
- extra 未配置时 ClassCastException 崩溃；建议 `as?` 判空。

---

## Info（备注 / 幻觉与死代码）

### 54. `adapter/DragAdapter.kt:16-18, 64-66` —— `fixedPosition = -1` 常量与 `getFixedPosition()` 永远返回 -1
- 疑似 AI 幻觉的"固定项"功能残留，无调用方、无语义。
- 建议删除或实现真实固定项逻辑。

### 55. `adapter/ExpLogAdapter.kt:29` —— 经验日志 delta 恒为 "+" 前缀
- 若服务端存在负经验（扣分）则显示错误；需确认接口是否恒非负。

### 56. `adapter/QualityChooseAdapter.kt:30-32` —— `if (nameList == null)` 死判空（nameList 非空初始化）

### 57. `adapter/video/UserVideoAdapter.kt:87-91` —— `onViewRecycled` 中 `holder is DynamicHolder` 分支永假
- 该 adapter 只创建 `VideoCardHolder` 与匿名 ViewHolder，`DynamicHolder` 分支是照抄动态页的死代码。

### 58. `ui/theme/` —— 三套并行主题系统（ThemeManager.kt / BiliColors.kt / ThemeUtils.kt）颜色定义重复
- 改主题色需同步三处，极易漂移（如 `ThemeManager.ClassicGray.PRIMARY` 与 `BiliColors.Primary` 的经典灰取值不同）。
- 建议收敛为单一来源。

### 59. `ui/widget/HighEnergyProgressBar.kt:55-56` —— `onDraw` 上的 `@Synchronized` 无实际同步对象意义
- 只会无谓阻塞其他线程；如确需保护数据，同步数据访问而非 draw。

### 60. `adapter/TimelineAdapter.kt:48-77` —— 时间线剧集卡片无任何点击事件（含 onclick/跳转）

### 61. `adapter/ReplyAdapter.kt:100` —— `sorts[sort]` 中 sort==0/1 显示"未知排序"占位字符串（疑似未完成功能）；`:355` 用硬编码 key `"mid"` 而非 `SharedPreferencesUtil.mid`

### 62. `player/DanmakuManager.kt:86-88` —— `Inflater(true)`（raw deflate）与 B 站 zlib 封装兼容性存疑；解压失败被静默吞掉（`catch(Exception){ e.printStackTrace() }`），弹幕缺失无任何提示

---

## 汇总

| 严重度 | 数量 | 代表问题 |
|---|---|---|
| Critical | 5 | 下载服务双批次竞态、分片并发写文件、异常退出删整目录、ReplyAdapter 强转崩溃、私信系统消息越界 |
| High | 12 | 手势长按标志不复位、并行下载死循环重试、字幕 createNewFile 中止下载、notifyItemChanged(-1)、拖拽索引错位、TextWatcher 叠加、后台改列表、official 越界、亮度手势死功能、WrapContent 空壳、Opus 空分支 |
| Medium | 26 | 旋钮抢焦点、设置不实时生效、弹幕解压截断、DB 并发锁、暂停删半成品、主线程 native 调用、子卡片堆叠、滑动中重绑、动画泄漏、span/Paint 每帧分配等 |
| Low | 10 | bind 建监听器、hashCode 稳定 id、调试日志、弃用 API、硬转换等 |
| Info | 9 | 死代码（fixedPosition、DynamicHolder 分支、listChooseLauncher）、三套主题、注释与实现不符等 |

### Top 5 最严重问题

1. **DownloadService.start() 竞态**（DownloadService.kt:501-521）：并发添加下载会启动两个批次循环，同一视频被两个线程同时写同一临时文件，直接损坏文件。
2. **onDestroy 异常退出删整目录**（DownloadService.kt:1564-1577）：一次意外异常即删除该视频目录下全部已完成文件，用户数据丢失。
3. **分片 join 超时后回退与残留线程并发写盘**（DownloadService.kt:1421-1435）：超时的分片线程继续写已被 resetFile 重建的文件，静默产生损坏文件。
4. **PlayerControlDelegate 长按后手势永久失效**（PlayerControlDelegate.kt:282-284）：`isLongPressing` 无复位点，一次长按后播放器全部滑动手势不可用。
5. **SettingsAdapter 输入框 TextWatcher 叠加**（SettingsAdapter.kt:264-321）：每次 bind 追加 watcher 不清理，设置项之间输入互相写脏 + 泄漏。
