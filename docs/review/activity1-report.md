# ReBiliClient Activity 层深度代码审查报告（第 1 批）

- 审查对象：`app/src/main/java/com/RobinNotBad/BiliClient/activity/` 下根目录 16 个 Activity + `article/`、`dynamic/`（含 `send/`）、`live/`、`message/`、`player/`、`reply/` 全部文件，共 38 个文件。
- 审查日期：2026-02（基于 `versionName 26.08.14` 分支快照）。
- 审查方法：逐文件精读 + 对关键引用的基础设施（`CenterThreadPool.java`、`SharedPreferencesUtil.java`、`MenuConfig.kt`、`NetWorkUtil.java`、`BaseActivity.kt`、`RefreshMainActivity/RefreshListActivity/RefreshListFragment`、`AndroidManifest.xml`、`TerminalContext.java`、`AppInfoApi.java` 等）做交叉核实，排除/确认疑似问题。未修改任何源代码。
- 严重度定义：**Critical**（崩溃/数据错误/安全）> **High**（明显逻辑 bug）> **Medium**（健壮性/资源问题）> **Low**（风格/性能小问题）> **Info**（备注）。

---

## Critical（崩溃 / 数据错误 / 安全）

### C1. `PlayerActivity.kt:1496-1500` —— onDestroy 在 `!isFinishing` 时提前 return，所有资源清理被跳过
```kotlin
override fun onDestroy() {
    if (!isFinishing) {
        super.onDestroy()
        return
    }
    ...
}
```
- **问题描述**：只有当 `finish()` 被调用（`isFinishing == true`）时才执行清理；系统因内存压力、"不保留活动"开发者选项等原因销毁 Activity 时（`isFinishing == false`），直接跳过 ijkPlayer release、全部 5 个 Timer 取消、EventBus 反注册、`liveWebSocket.close()`、MediaSession release。此时 WebSocket 仍存活，`PlayerDanmuClientListener.playerActivity` 强引用本 Activity，形成完整泄漏链。
- **影响**：① 每次系统级销毁泄漏播放器 + Timer 线程 + WebSocket 连接；② 泄漏的 listener 在 OkHttp 后台线程继续回调 `playerActivity!!.addDanmaku(...)`（`PlayerDanmuClientListener.kt:162/169/175/180/184/187`），对已销毁 Activity 操作，随时可能 NPE 崩溃；③ 心跳 Timer 持续向服务器发包。
- **修复建议**：无条件执行清理（用标志位防止重复释放即可），`if (!isFinishing)` 分支应删除；`PlayerDanmuClientListener` 回调中先判 `playerActivity?.isDestroyed == true` 则直接返回。

### C2. `GetIntentActivity.kt:20,32-33` —— 导出 Activity 对外部可控 Intent 做 `!!` / `toLong()`，可被外部应用崩溃攻击
```kotlin
"video_bv" -> BiliTerminal.jumpToVideo(this, intent.getStringExtra("content")!!)
"video" -> BiliTerminal.jumpToVideo(this, uri.lastPathSegment!!.toLong())
```
- **问题描述**：`GetIntentActivity` 在 Manifest 中 `android:exported="true"` 且注册了 `http/https/bilibili` scheme 的 VIEW intent-filter（`AndroidManifest.xml:127-143`）。`"video_bv"` 分支对缺失的 `content` extra 用 `!!` 强解引用；URI 分支对 `lastPathSegment` 先 `!!` 再 `toLong()`，外部应用可构造 `bilibili://video/abc` 或 `rebili://video/` 触发 NPE / NumberFormatException。
- **影响**：任意应用可通过隐式 Intent 让本应用稳定崩溃（DoS）；崩溃发生在 `onCreate`，无任何捕获。
- **修复建议**：全部改为 `?.` + `runCatching`/`toLongOrNull()`，非法输入直接 `finish()`；对不可解析的 URI 给出提示而非崩溃。

### C3. `ImageViewerActivity.kt:31` —— 导出 Activity 对必传 extra 使用 `!!`，缺参即崩溃
```kotlin
val imageList = intent.getStringArrayListExtra("imageList")!!
```
- **问题描述**：`ImageViewerActivity` 在 Manifest 中 `exported="true"`（`AndroidManifest.xml:360-364`），却对 `imageList` extra 直接 `!!`。外部应用可用空 Intent 打开它 → NPE。
- **影响**：外部触发即崩溃；即便内部调用，任何一处漏传该 extra 也会崩。
- **修复建议**：改为 `?: emptyList()`（或 `?: return finish()`），并把导出改为 `exported="false"` 或在入口校验。

### C4. `PrivateMsgActivity.kt:139` —— 私信内容未转义直接拼进 JSON 字符串
```kotlin
val result = PrivateMsgApi.sendMsg(..., "{\"content\":\"$content\"}")
```
- **问题描述**：用户输入直接插入 JSON 字面量，`"`、`\`、换行等字符未做 JSON 转义。用户发送 `"` 会产出畸形 JSON（接口解析失败或字段被截断），输入 `","xxx":"yyy` 一类内容可注入额外字段。
- **影响**：消息内容损坏/发送失败，属于数据错误 + 轻量注入面；私信文本长度也无任何限制。
- **修复建议**：用 `JSONObject().put("content", content).toString()` 构造，或对内容做 `org.json` 转义；发送前限制长度（B 站约 2000 字符）。

### C5. `DynamicInfoActivity.kt:59` —— `diFragment.view!!` 对异步创建的 Fragment 视图强解引用，与第 55 行判空自相矛盾
```kotlin
val view = diFragment.view
if (view != null) view.visibility = View.GONE   // L55 判空
...
diFragment.view!!.post { ... }                  // L59 直接 !!
```
- **问题描述**：`viewPager.adapter = vpfAdapter`（L52-53）只提交了 Fragment 事务（`FragmentStatePagerAdapter.instantiateItem` 内部 `commitAllowingStateLoss()`），fragment 的 `view` 在事务异步执行后才创建，`setAdapter` 返回时 `diFragment.view` 通常为 null。作者在第 55 行明确判空（说明知道 view 可能为 null），第 59 行却用 `!!` 强解引用并调用 `.post`。
- **影响**：在 `onCreate` 主线程同步抛 NPE，整个动态详情页打开即崩（取决于 androidx 版本的事务执行时机，属于确定性高风险而非偶发）。
- **修复建议**：改为 `diFragment.view?.post { ... }`，与 L55 保持一致。

---

## High（明显逻辑 bug）

### H1. `PlayerActivity.kt:2917-2920` —— onPrepared 回调中 `ijkPlayer!!.release()` 与 onDestroy 置空存在竞态
```kotlin
override fun onPrepared(mediaPlayer: IMediaPlayer) {
    if (destroyed) {
        ijkPlayer!!.release()
        return
    }
```
- **问题描述**：`onDestroy` 中 `ijkPlayer = null`（L1514），但 IJK 的 `onPrepared` 回调线程独立。若 Activity 在 prepareAsync 进行中被销毁（尤其叠加 C1 的清理被跳过路径），残留的 onPrepared 回调命中 `destroyed == true` 时 `ijkPlayer` 可能已是 null → `!!` NPE，且该回调不在任何 try 内。
- **影响**：播放器销毁竞态崩溃。
- **修复建议**：`ijkPlayer?.release(); ijkPlayer = null`，或直接 `return` 不做操作。

### H2. `CatchActivity.kt:80-85` —— "重启"按钮实际是退出应用
```kotlin
findViewById<android.view.View>(R.id.restart_btn).setOnClickListener {
    finish()
    stopService(Intent(this, DownloadService::class.java))
    startActivity(Intent(this, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    Process.killProcess(Process.myPid())
}
```
- **问题描述**：`CatchActivity` 与 SplashActivity 同进程（Manifest 未声明 `android:process`），`startActivity` 后立刻 `killProcess(myPid())` 会把刚启动的 SplashActivity 连同整个进程一起杀掉。这种写法只在崩溃页处于独立进程时才有效。
- **影响**：用户点"重启"得到的是应用直接关闭回桌面，与按钮文案不符。
- **修复建议**：去掉 `killProcess`（finish + startActivity 即可实现重启），或用 `PendingIntent`/`AlarmManager` 跨进程重启。

### H3. `NoticeActivity.kt:85-91` —— "system" 类型加载更多必然 NPE，且列表被整体替换、通知数量可能为负
```kotlin
"system" -> { messageList = MessageApi.getSystemMsg() as MutableList<MessageCard> }
...
runOnUiThread { noticeAdapter!!.notifyItemRangeInserted(lastSize, messageList.size - lastSize) }
bottom = cursor!!.is_end        // cursor 恒为 null → NPE
```
- **问题描述**：`pageType == "system"` 时 `cursor` 从未被赋值（初始为 null），`continueLoading` 中 `cursor!!.is_end` 直接 NPE（被外层 catch 吞掉，`page--` 后静默）；同时 L86 把整个列表替换为新列表而非追加，L89 的 `messageList.size - lastSize` 可能为负 → `notifyItemRangeInserted` 抛 IllegalArgumentException。
- **影响**：系统消息页"加载更多"永远失败；负数量通知在未包裹 try 的情况下直接崩（L89 恰好在 try 内，但 L90 的 NPE 也被吞，用户无任何反馈，且 `bottom` 永远为 false → 反复触发失败加载）。
- **修复建议**：`system` 分支要么单独处理（不设置 bottom，不 append），要么给 `cursor` 一个可空安全的默认值并只在非 system 分支使用 `cursor!!`。

### H4. `ReplyFragment.kt:183-201` / `DynamicInfoActivity.kt:77-80` —— ThreadMode.ASYNC 回调在后台线程修改 `replyList`，且 `rFragment!!` 可能 NPE
```kotlin
@Subscribe(threadMode = ThreadMode.ASYNC, sticky = true, priority = 1)
fun onEvent(event: ReplyEvent) {
    rFragment!!.notifyReplyInserted(event)      // DynamicInfoActivity:79
}
```
- **问题描述**：① `ThreadMode.ASYNC` 使 `onEvent` 在 EventBus 后台线程执行，而 `ReplyFragment.notifyReplyInserted` 在 L187-199 直接 `replyList!!.add(pos, reply)`、`childMsgList.add(reply)`——与主线程上 adapter 的读取（onBindViewHolder）并发 → `ConcurrentModificationException` 风险；② `rFragment` 初始为 null，sticky 事件若在 `TerminalContext.getDynamicById` 的 LiveData 回调之前到达（例如从评论页跳转过来时遗留 sticky ReplyEvent），`rFragment!!` 直接 NPE。
- **影响**：发评论后返回动态/评论详情页时有崩溃风险；列表偶发错乱。
- **修复建议**：`onEvent` 改为 `ThreadMode.MAIN`（或回调内 `runOnUiThread` 再改列表）；`rFragment?.notifyReplyInserted(event)` 判空。

### H5. `DownloadActivity.kt:85-112,118-147` —— 后台线程 `!!` 空解引用直接崩进程；onDestroy 与下载线程竞态删文件
- **问题描述**：① L85/L87/L90/L110/L111 对 `intent.getStringExtra("path"/"link"/"title"/"danmaku"/"cover")!!` 强解引用，任一调用方漏传即 NPE；这些代码跑在 `CenterThreadPool.run`（协程 `Dispatchers.IO`，无 CoroutineExceptionHandler），异常会走到默认未捕获处理器 → 整进程崩溃（不是静默失败）；② L142 只捕获 `IOException`，NPE/SecurityException 等直接冒泡；③ 用户在下载中按返回键 → `onDestroy`（L259-266）删除 `downFile`/`downPath` 文件夹，而下载线程仍在向已删除（unlink）的文件写入，结束后 `renameTo` 失败 → 出现"下载完成，重命名失败"等错乱状态。
- **影响**：入参缺漏即崩溃；中途退出造成半成品文件删除与下载线程竞态。
- **修复建议**：所有 extra 改为 `?: return` 提前退出；catch 收窄为具体异常后在 UI 提示并结束；onDestroy 中通过 `finishFlag` + 取消协程（或下载任务标志）避免删除正在写入的文件。

### H6. `PlayerActivity.kt:920-924` —— 播放错误被静默吞掉，用户无任何反馈
```kotlin
ijkPlayer!!.setOnErrorListener { _, what, extra ->
    val EReport = "播放器可能遇到错误！\n错误码：" + what + "\n附加：" + extra
    Logu.e("ijk-err", EReport)
    false
}
```
- **问题描述**：错误只打日志，返回 `false`（表示未处理，IJK 停止播放），不弹任何提示，加载动画/UI 状态也不会复位。
- **影响**：网络波动、流地址过期（B 站视频 URL 有有效期）时画面卡死，用户不知道发生了什么，只能退出重进。
- **修复建议**：返回 `true` 自行处理，`runOnUiThread` 提示错误并给出重试/退出选项。

### H7. `DynamicActivity.kt:141-154` —— 发动态成功后 `notifyItemInserted(0)` 与列表头部位置错位 + 后台线程改列表
```kotlin
dynamicList!!.add(0, dynamic)                          // 后台线程改 ArrayList
runOnUiThread {
    if (type == "all") {
        dynamicAdapter!!.notifyItemInserted(0)         // 未考虑 recent-up 头部
        dynamicAdapter!!.notifyItemRangeChanged(0, dynamicList!!.size)
    }
}
```
- **问题描述**：① `DynamicAdapter` 在 `showRecentUp()` 开启时位置 0 是"最近更新 UP 主"头部，新动态实际位于 position 1（或 2），`notifyItemInserted(0)` 通知的位置与数据位置不一致 → 列表错位/闪烁；② `dynamicList!!.add(0, ...)` 在 `CenterThreadPool` 后台线程执行，与主线程 `dynamicList!!.size`/adapter 绑定并发读 → CME 风险；③ `type != "all"` 时新动态被插进列表但从不通知，之后切回 "all" 也只在 `notifyDataSetChanged` 时才出现，且与下一次刷新 `clear()` 前残留数据重复。
- **影响**：发布动态后列表展示错乱；偶发并发异常。
- **修复建议**：插入与通知统一走主线程，插入位置按 `showRecentUp()` 计算偏移；非 "all" 类型不插入本地列表。

### H8. `DownloadActivity.kt:231-256`（与 `PlayerActivity.kt:3063-3088` 相同实现）—— deflate 解压循环存在忙等死循环风险
```kotlin
while (!decompresser.finished()) {
    val i = decompresser.inflate(buf)
    o.write(buf, 0, i)
}
```
- **问题描述**：当输入为空数组或"已由 OkHttp 透明解压过的明文"等 `inflate()` 返回 0（needsInput）但不抛异常、`finished()` 恒为 false 的场景，循环空转占满一个 CPU 核。`DownloadActivity.downdanmu()`（L202-227）还会在解压前阻塞后续视频下载流程。
- **影响**：弹幕为空或接口返回异常数据时，视频下载/弹幕加载挂起，界面无响应（ANR 风险）。
- **修复建议**：循环内检查 `decompresser.needsInput()`/`finished()` 或记录连续 `i == 0` 次数，超过阈值即 break 并回退原始数据。

---

## Medium（健壮性 / 资源问题）

### M1. `DialogActivity.kt:32-47`、`TutorialActivity.kt:48-63` —— Timer 未在 onDestroy 取消
- **问题描述**：两个 Activity 都用 `Timer.scheduleAtFixedRate` 做倒计时，Timer 仅存于局部变量；`DialogActivity`/`TutorialActivity` 的 `onBackPressed` 被覆写为空（用户无法返回），但系统仍可能销毁 Activity（后台回收、被其他组件 finish）。Timer 线程持有 Activity 引用（匿名 TimerTask）继续 `runOnUiThread`。
- **影响**：Activity 泄漏 + Timer 线程残留，倒计时回调操作已销毁页面的 View。
- **修复建议**：Timer 提升为字段，`onDestroy` 中 `cancel()`；回调里加 `isDestroyed`/`isFinishing` 判断。

### M2. `PrivateMsgActivity.kt:196-221` —— 空会话下 `list[list.size-1]` 越界，每 15 秒报错一次
- **问题描述**：`refreshTimer` 每 15 秒调 `refresh()`（L121-126），`refresh()` 首行 `list[list.size - 1].msgSeqno`；全新会话（无历史消息）`list` 为空 → `IndexOutOfBoundsException`，被 catch 后 `MsgUtil.err` 弹错误提示——**每 15 秒弹一次**。且 `refreshTimer` 在页面退到后台时仍持续轮询（仅 onDestroy 取消），耗电 + 无谓流量。
- **影响**：空私信会话打开后持续报错弹窗；后台持续轮询。
- **修复建议**：`list.isEmpty()` 时直接 return；Timer 改为在 `onResume` 启动、`onPause` 停止。

### M3. `PrivateMsgActivity.kt:224-254` —— loadMore 失败后 `isLoadingMore` 永真，滚动加载功能永久失效
- **问题描述**：`isLoadingMore = true` 在进入时设置，仅成功分支（L248）复位；任何异常（含空列表 `list[0]` 越界）走 catch 后不再复位。之后滚动到顶部永远不再触发加载。
- **影响**：一次失败即让"加载更多"功能坏死，只能重进页面。
- **修复建议**：catch 中 `isLoadingMore = false`；空列表直接提示"暂无更多"。

### M4. `PrivateMsgActivity.kt:46,231,239-241,62` —— 日志打印私信全文与空 tag 调试残留
- **问题描述**：L231 `Log.e("", allMsg.toString())`、L239-241 循环把每条私信的 `msgSeqno/name/uid/content` 全量打到 logcat；L62 `Log.e("", uid.toString())` 空 tag。
- **影响**：私信内容（敏感）落入可被其他应用读取的 logcat；调试残留噪声。
- **修复建议**：删除或降级为 `Logu.d` 且不打印 content；统一使用 `Logu`。

### M5. `PlayerActivity.kt:1158-1164` —— 字幕选择项 `selectedItemIndex = subtitleLinks.size` 越界
```kotlin
if (subtitle_selected == -1) subtitle_selected = subtitleLinks!!.size
...
adapter.selectedItemIndex = subtitle_selected
```
- **问题描述**：`SubtitleAdapter.selectedItemIndex` 的 setter 会对新旧位置 `notifyItemChanged`（`SubtitleAdapter.kt:19-25`）；把索引设为等于 itemCount 的 `size`（关闭字幕项应为 `size-1`），`notifyItemChanged(itemCount)` 越界，可能触发 RecyclerView "Inconsistency detected / IndexOutOfBoundsException"。
- **影响**：打开字幕选择面板时偶发崩溃或选中态错乱。
- **修复建议**：改为 `size - 1`（若"关闭"项在末尾），或对 setter 做位置钳制。

### M6. `PlayerActivity.kt:2634-2673` —— 互动视频条件表达式解析失败一律返回 true
- **问题描述**：`evaluateExpression` 只支持单个 `>= <= > < == !=` 的纯数字比较，遇 `a+b>c`、布尔运算等复杂表达式时 `toLong()` 抛异常被吞，返回 `true`。
- **影响**：互动视频分支条件误判 → 播放器选择错误选项/提前结束，静默出错。
- **修复建议**：解析失败时至少提示"条件无法解析"并采用保守策略（不自动选择），而不是一律 true。

### M7. `ReplyInfoActivity.kt:124-149` —— 加载更多插入偏移 `+2` 与列表实际结构不一致
```kotlin
replyList!!.addAll(list)
replyAdapter!!.notifyItemRangeInserted(replyList!!.size - list.size + 2, list.size)
```
- **问题描述**：初始/刷新路径中 `replyList` 只含 1 个根评论项（`replyList.add(0, rootReply)`，L163-165），追加项应从 `旧size+1` 开始通知；这里用 `+2`。若 ReplyAdapter 无额外固定头，则通知位置整体后移 1 位，滚动到底部时出现行绑定错位（可能触发 RecyclerView Inconsistency 崩溃）。
- **影响**：评论详情页加载更多后列表显示错乱/崩溃风险。
- **修复建议**：统一用"根评论 1 项"计算偏移（`+1`），或与 `refresh()` 共用同一 notify 策略。

### M8. `ListChooseActivity.kt:39-44` —— "items" 缺失时 `finish()` 后仍继续执行，lateinit 未初始化即崩溃
```kotlin
if (intent.getSerializableExtra("items") == null) {
    finish()
} else {
    this.displayNames = intent.getSerializableExtra("items") as List<String>
}
...
adapter.setNameList(displayNames)   // displayNames 未初始化 → UninitializedPropertyAccessException
```
- **问题描述**：`finish()` 只标记销毁，当前调用栈继续走完；`displayNames` 是 `lateinit var`，缺失 items 时从未赋值，L50 访问即抛 `UninitializedPropertyAccessException`。这个判空守卫形同虚设。
- **影响**：漏传 items 的调用方不是优雅退出而是崩溃。
- **修复建议**：`if (items == null) { finish(); return }`（补 return），或去掉 lateinit 用可空类型。

### M9. `LiveInfoActivity.kt:187-200` —— 切换清晰度失败后播放按钮永久禁用
- **问题描述**：质量点击回调先 `play.isEnabled = false`，网络请求失败走 catch 只弹 `MsgUtil.err`，从不恢复 `play.isEnabled = true`。
- **影响**：切清晰度失败一次后，播放按钮永远点不动，只能退出重进。
- **修复建议**：catch 中恢复 `play.isEnabled = true`。

### M10. `DynamicActivity.kt:184-236` —— 下拉刷新与加载更多并发时共享 `offset` 字段产生竞态
- **问题描述**：`offset` 是实例字段，`refreshDynamic()` 置 0 后调 `addDynamic(type, true)`；若此刻上一轮 loadMore 请求尚未返回（`RefreshMainActivity.goOnLoad` 仅做 100ms 节流、无 in-flight 互斥），两个请求同时读/写 `offset`，响应乱序到达 → 重复项或跳页。
- **影响**：快速下拉刷新时列表出现重复/缺页。
- **修复建议**：加载中标志互斥（refresh 未完成时禁止 loadMore），或把 offset 改为请求局部变量。

### M11. `WriteReplyActivity.kt:206-215` —— 图片全尺寸解码，OOM 风险
- **问题描述**：`BitmapFactory.decodeStream` 不采样直接解码整张原图（12MP 照片 ≈ 48MB），再 `compress(JPEG, 100)` 全量进内存后 `toByteArray()`；低内存机型（虽有 largeHeap）易 OOM，异常只在 catch 里提示"图片处理失败"。
- **影响**：选大图发评论时崩溃风险；上传体积过大。
- **修复建议**：按目标尺寸 `inSampleSize` 采样 + 降低 JPEG 质量（如 80），限制单张体积。

### M12. `InteractionDebugActivity.kt:14-21,41-44` —— 静态字段传大对象 + 重建即失效
- **问题描述**：`companion object` 静态持有 `InteractionVideoData`，`onDestroy` 置空；Activity 被系统重建（旋转虽锁定，但内存回收等）时静态数据已清空，`onCreate` 拿到 null 直接 `finish()`，调试页"消失"。
- **影响**：数据经静态中转是反模式，重建/二次打开行为不可靠。
- **修复建议**：数据随 Intent/Parcelable 传递或由 ViewModel 持有。

### M13. `CatchActivity.kt:59-75` —— 崩溃上传回调未判 Activity 存活 + `Process.killProcess` 出口按钮
- **问题描述**：`CenterThreadPool.run` 内网络回调后 `runOnUiThread` 直接操作 `btnUpload`（无 `isDestroyed` 判断，崩溃页本身生命周期短）；L40 退出按钮直接 `killProcess(myPid())` 绕过一切收尾（DownloadService 等）。
- **影响**：理论上回调时 Activity 已销毁仍会操作视图（无崩溃但属坏味道）；killProcess 退出过于粗暴。
- **修复建议**：回调加 `isFinishing/isDestroyed` 判断；退出用 `finishAffinity()` 替代 killProcess。

### M14. `SplashActivity.kt:126-136,139-160` —— Debug 构建悬浮窗授权流程可能卡死启动
- **问题描述**：`ensureUEToolOverlayPermission()` 返回 true 时 onCreate 直接 return，等待 `onActivityResult`；若用户从系统授权页返回时本 Activity 已被系统回收（进程被杀/重建），`onActivityResult` 不会回调 → 停留在白屏启动页；且该方法使用了已废弃的 `onActivityResult` 机制。
- **影响**：仅 Debug 构建受影响，但卡死启动是启动页的严重 UX 问题。
- **修复建议**：改 Activity Result API（`registerForActivityResult`）并加超时兜底（若干秒后强制继续启动流程）。

---

## Low（风格 / 性能小问题）

### L1. 大量 `Log.e("debug", ...)` 调试残留
- 涉及：`MenuActivity.kt:72,126,131,136`、`DynamicActivity.kt:170,185,205`、`NoticeActivity.kt:110,166,173`、`PlayerDanmuClientListener.kt:34,105,109,126,138,165`、`OpusInfoFragment.kt:77,79,86`、`ReplyFragment.kt:123,166,173,234`、`ReplyInfoActivity.kt:110,131,138,177`、`FollowLiveActivity.kt:45,53`、`RecommendLiveActivity.kt:46,54`、`EmoteActivity`（无，但 L96-100 捕获后 printStackTrace）、`PrivateMsgActivity.kt:62,94,231,239-244`。
- **影响**：logcat 噪声、性能损耗；`Log.e` 级别会把日志刷进系统日志影响其他调试。
- **建议**：统一收敛为 `Logu.d` 或删除。

### L2. 默认值 `114514` 语义错误
- `PrivateMsgActivity.kt:61` `uid = intent.getLongExtra("uid", 114514)`、`OpusInfoActivity.kt:38` `oid = intent.getLongExtra("id", 114514)`。
- **影响**：调用方漏传参数时会打开"野兽先辈"的私信/视频，而不是失败提示。默认值应为 `-1` 并校验。

### L3. `OpusInfoActivity.kt:89-91` —— 空实现 `@Subscribe` 死代码
- `fun onEvent(event: ReplyEvent) {}` 订阅后什么都不做，空耗 EventBus 分发；疑似作者误解 sticky 事件"可被消费"（EventBus 会发给所有订阅者，空订阅无法拦截）。

### L4. `MessageActivity.kt:47-72` —— 点击"回复/赞/@我"后文案被改成不带未读数的静态文本
- 点击跳转 `NoticeActivity` 前先把 `reply_text/like_text/at_text` 设置为"回复我的"等纯文案，把刚显示的"（N未读）"抹掉，行为与直觉相反（应保留或清空未读计数状态）。

### L5. `SendDynamicActivity.kt:55-59,37,201` —— finish 后继续执行 + `voteDraft` 死代码
- 未登录时 `finish()` 后仍继续初始化整个界面（无害但浪费）；`voteDraft` 字段只赋值 null（L201），从未被真正使用，`collectVoteDraft()` 另建局部对象，字段属死代码。

### L6. `EmoteActivity.kt:44-48,91-94` —— 空 onScrolled 死代码 + 逐图标 `.submit().get()` 阻塞
- `onScrollListener` 的 `onScrolled` 为空实现；加载每个 Tab 图标用 `Glide...submit().get()` 同步阻塞在后台线程，图标多时串行加载慢；Activity 销毁后 `Glide.with(activity)` 抛异常仅中断后续图标。

### L7. `GetIntentActivity.kt:26-36` —— "type" extra 与 uri data 双通道会重复跳转
- 若 Intent 同时带 `type` 和 data URI，两个分支都会执行跳转（跳两次页面）。应 `else if`。

### L8. `RefreshMainActivity.kt:74-84`（被 DynamicActivity 使用）—— `page` 字段死变量
- `goOnLoad` 里 `page++` 后传入 listener，但 `DynamicActivity.addDynamic` 忽略该参数（用自身 `offset`），`page` 状态无人消费，属于残留设计。

### L9. `FollowLiveActivity.kt` / `RecommendLiveActivity.kt` —— 无下拉刷新入口；RecommendLive 缺空态
- 两者都未调用 `setOnRefreshListener`（`RefreshListActivity`/`RefreshMainActivity` 默认禁用下拉），列表无法手动刷新；`FollowLiveActivity` 有空列表提示而 `RecommendLiveActivity` 没有，行为不一致。

### L10. `LiveInfoActivity.kt:229-233` —— 失败回调里 1 分钟延迟的错误提示无意义
- `CenterThreadPool.runOnUIThreadAfter(1, TimeUnit.MINUTES) { MsgUtil.err(e) }`：页面已 finish，1 分钟后弹错误提示既无上下文也无必要（且 Activity 已销毁仍通过静态 handler 弹提示）。

### L11. `PlayerActivity.kt:1472-1476` —— onNewIntent 直接 finish()
- 单顶模式下收到新 Intent（如再次点开视频）直接关闭播放器，而非切换到新内容；对"从推荐继续播放"场景是行为丢失。属设计取舍，但应确认是否符合预期。

### L12. `DownloadActivity.kt:128-133` —— contentLength 为 -1（未知长度）时进度百分比为负
- `dldPercent = 1.0f * CompleteFileSize / TotalFileSize` 未处理 `-1`，进度条百分比显示负数。UI 层 `DownloadActivity` 进度条高度 `(dldPercent * scrHeight)` 也可能为负。

### L13. `GetIntentActivity.kt:29` —— `Log.e("debug-host", ...)` 调试残留（同 L1，单独列出因其为导出入口）。

---

## Info（备注）

### I1. 多个 Activity 导出且缺少入参校验（扩大攻击面）
- `AndroidManifest.xml` 中 `ShowTextActivity`、`PlayerActivity`、`OpusInfoActivity`、`GetIntentActivity`、`ImageViewerActivity`、`SettingMainActivity`、`SearchActivity`、`LoginActivity` 等均 `exported="true"`。其中已确认可被外部输入直接打崩的有 C2/C3；其余虽内部已做基本兜底（如 `PlayerActivity.getExtras` 缺 url 返回 false 安全退出），但建议逐一收敛导出范围并统一入口校验。

### I2. `AppInfoApi.java:163` —— 崩溃栈上传使用明文 HTTP
- `http://api.biliterminal.cn/terminal/upload/stack`（Manifest `usesCleartextTraffic="true"`）。上传内容含崩溃栈与设备信息（无 Cookie/token，风险有限），但明文传输仍不推荐，建议改 HTTPS。

### I3. `ReplyInfoActivity.kt:151-188` —— 每次 refresh 注册新 LiveData observer，轻微累积
- `getReply(...).observe(this) { ... }` 每次下拉刷新都向 Activity 注册一个新 observer（旧的随其一次性 LiveData 不再触发，但 observer 实例留存到 Activity 销毁）。量小，但属可避免的累积模式；可用 `removeObservers` 或只注册一次。

### I4. `AnimationUtils.java:43-47` —— crossFade 重载参数名 toShow/toHide 在重载间颠倒
- 两个重载中同名参数的语义（谁淡出、谁淡入）互换，行为本身一致（实现统一按"第一个隐藏、第二个显示"处理），但阅读/调用易混淆，建议统一参数名。

### I5. `EmoteActivity.kt:164-168` / `GridView` 装饰 —— spanSizeLookup 与 GridSpacingItemDecoration 的列号计算在 type==4（跨 2 格）时错位
- 装饰器按 `position % spanCount` 计算列，未考虑跨格项的 span，视觉间距在混合跨度下不齐。纯外观问题。

---

## 统计

- **Critical：5**（C1-C5）
- **High：8**（H1-H8）
- **Medium：14**（M1-M14）
- **Low：13**（L1-L13）
- **Info：5**（I1-I5）
- **合计：45 条**

## Top 5 最严重问题

1. **PlayerActivity onDestroy 在非 finish 场景跳过全部清理**（C1）：系统级销毁时播放器/定时器/WebSocket 全泄漏，残留回调持续操作已销毁 Activity，是播放器崩溃与内存泄漏的最大源头。
2. **GetIntentActivity / ImageViewerActivity 两个导出 Activity 对外部 Intent 强解引用**（C2/C3）：任意应用一条 Intent 即可让本应用稳定崩溃。
3. **DynamicInfoActivity 对异步创建的 fragment.view 用 `!!`**（C5）：动态详情页存在打开即崩的确定性 NPE 风险，且与同文件判空逻辑自相矛盾。
4. **PrivateMsgActivity 私信 JSON 未转义 + 空会话每 15 秒越界报错**（C4/M2）：既造成数据错误/注入面，又让空会话页面持续弹错、后台无限轮询。
5. **CatchActivity"重启"按钮同进程 killProcess**（H2）：崩溃后的"重启"实际等于退出，功能名不副实，用户无法从崩溃页恢复。
