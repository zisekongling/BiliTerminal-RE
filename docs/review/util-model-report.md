# ReBiliClient util 与 model 层深度代码审查报告

- 审查对象：`app/src/main/java/com/RobinNotBad/BiliClient/util/`（40 个文件）与 `model/`（64 个文件，含全部公共模型）
- 审查方式：逐行阅读 + 与 `bilibili-API/` 文档快照交叉核对 + 全工程 grep 验证调用方与死代码
- 结论统计：**Critical 3 条 / High 8 条 / Medium 17 条 / Low 19 条 / Info 9 条，共 56 条**
- 说明：行号均对应审查时源码；凡涉及调用方的结论均已用 grep 验证过真实调用路径，避免"纸面 bug"

---

## Critical（崩溃 / 数据错误）

### C1. `model/OpusParagraph.java:98-100 —— 空 blockquote 时 setSpan 传 -1 崩溃`
- 问题：`analyzeBlockQuote()` 对 children 循环拼接后直接 `setSpan(..., 0, stringBuilder.length() - 1, ...)`。当 blockquote 的 children 全部为空文本节点（或 children 存在但 analyzeText 返回空）时，`length()==0`，end 为 `-1`，`SpannableStringBuilder.setSpan` 抛 `IndexOutOfBoundsException`。
- 影响：专栏/动态详情页解析到空引用块（B 站富文本常见格式）时直接崩溃，ErrorCatch 弹窗并杀进程。属于内容触发的确定性崩溃。
- 修复：先判断 `length() > 0` 再 setSpan；且末尾应为 `length()`（当前还会漏掉最后一个字符的背景色，off-by-one）。

### C2. `model/Reply.java:75 —— location 字符串硬编码 substring(5) 崩溃`
- 问题：`replyCtrl.getString("location").substring(5)` 硬编码去掉前 5 个字符（"IP属地:"）。若接口返回的 location 为空串或短于 5 个字符（B 站曾返回空 location、或未来字段格式变化），抛 `StringIndexOutOfBoundsException`。
- 影响：单条评论解析抛异常向上传播，**整页评论列表加载失败**（解析在 api 层统一 try 的外层），评论功能大面积不可用。
- 修复：先判空/判长度，或按 `"IP属地:".length()` 且不足时直接使用原串。

### C3. `util/UpdateManager.kt:141-158,179 —— 断点续传对不支持 Range 的服务器会拼接出损坏 APK`
- 问题：`downloadApk()` 在本地已有部分文件时加 `Range: bytes=N-` 请求头；随后只判断 `if (!response.isSuccessful && response.code != 206)`。若服务器**不支持 Range**（返回 200 全量），该条件不成立，直接进入 `writeResponseToFile(response, existingFile, ...)`，而 `writeResponseToFile` 在 `file.exists()` 时以 `append=true` 打开（第 179 行），把**全新 APK 追加到旧残片之后**。
- 影响：APK 文件损坏，安装必然失败；且损坏文件残留在缓存目录，下次续传继续在这个坏文件上追加，永远装不上。属于可复现的数据错误。
- 修复：显式请求过 Range 却收到 200 时，删除旧文件从头下载（判断 `downloadedBytes > 0 && response.code != 206`）。

---

## High（明显逻辑 bug）

### H1. `util/ToolsUtil.java:66-73 —— getRgb888 颜色换算完全错误（弹幕颜色全错）`
- 问题：把 RGB 三个通道的十进制值**拼接成字符串再 parseInt**：`Color.WHITE`（0xFFFFFFFF）→ `"255"+"255"+"255"` → `255255255`。正确 RGB888 白应为 `0xFFFFFF = 16777215`。三个通道拼串与"按位组合"完全是两回事。
- 影响：`PlayerActivity.kt:1812` 发送弹幕时把 `ToolsUtil.getRgb888(Color.WHITE)` 作为 `color=` 参数提交（DanmakuApi.java:42），B 站收到的是 `255255255`（≈0x0F36C8F7 青蓝色）而非白色；黑色（0）之外几乎任何颜色都会得到错误值。**用户每发一条弹幕颜色都是错的**。
- 修复：直接 `return color & 0xFFFFFF;`，删除该方法或重写。

### H2. `util/MediaMerger.kt:144-149 —— 先删原文件再 rename，失败即丢数据`
- 问题：合并完成后 `videoFile.delete()` 然后 `tempFile.renameTo(videoFile)`。若 rename 失败（同名冲突、文件占用、IO 错误），原视频文件**已被删除且不恢复**，仅留下一个 temp 文件。
- 影响：DASH 视频+音频合并时，一旦 rename 失败用户缓存的视频丢失；代码注释"保留临时文件作为备选"，但用户下次下载同名视频仍会因 `.DOWNLOADING` 之外的残留状态产生困惑。
- 修复：先 rename，失败时回退为"复制 tempFile → videoFile"或先备份原文件再替换，rename 成功后才删除原文件。

### H3. `util/NetWorkUtil.java:100-109 —— 手动重定向不关闭原始 Response（连接泄漏）`
- 问题：拦截器里 `response.isRedirect()` 且非 b23.tv 场景时执行 `return chain.proceed(newRequest);`，**之前 `chain.proceed(request)` 得到的 response 从未 close**（重定向响应体也没有被消费）。OkHttp 要求每个 Response 必须关闭，否则连接无法回池。
- 影响：每次遇到重定向（B 站接口大量 302，如 b23.tv 外链、视频跳转）泄漏一条连接；配合 `pingInterval` 与长连接池，长期使用连接数缓慢爬升。`LinkUrlUtil.handleShortUrl`（LinkUrlUtil.java:130-137）对 b23.tv 的 302 响应同样只读了 body 而不关闭。
- 修复：手动跟进前先 `response.close()`；短链场景统一关闭。

### H4. `util/NetWorkUtil.java:393-443 —— saveCookiesFromResponse 无同步，并发写 Cookie 丢更新`
- 问题：`CookieSaveInterceptor` 挂在共享 OkHttpClient 上，OkHttp 不同请求在不同线程并发执行拦截器；`saveCookiesFromResponse` 是"读 SharedPreferences → 合并 → 写回"的读-改-写流程，与 `putCookie/setCookies`（synchronized）不同，**完全不加锁**，且 `cachedCookies` 的 volatile 读-改-写也不原子。
- 影响：启动后多个接口并发返回 Set-Cookie（buvid3/bili_ticket 等），后写覆盖先写，部分新 Cookie 丢失 → 登录态/风控标识不完整，表现为偶发"登录失效"或风控。该竞态在真实使用中概率不低。
- 修复：整个函数体加 `synchronized (NetWorkUtil.class)`，与 putCookie/setCookies 统一锁。

### H5. `model/UserInfo.java:138 + adapter/dynamic/UserDynamicAdapter.kt:169-176 —— vip_role 字段语义错配，会员标签错误/显示 "null"`
- 问题：`UserInfo(JSONObject)` 构造器把 `vip.getInt("vipStatus")`（0/1 布尔语义）存入 `vip_role`；而 `UserInfoApi.java:83` 又把 `vip.getInt("role")`（1/3/7/15 角色语义）存入同一字段。`UserDynamicAdapter` 按 `vipTypeMap = {1:"月度大会员", 3:"年度大会员", 7:"十年", 15:"百年"}` 取值。
- 影响：凡经 JSON 构造器解析的 UserInfo（如评论 sender），年度/十年/百年会员只显示"月度大会员"；若某路径 role=2（不存在于 map），`vipTypeMap[vip_role]` 为 null，`append(null)` 直接渲染出 "null" 字样。同一字段两套语义是明确的模型设计错误。
- 修复：拆成 `vipStatus` 与 `vipRole` 两个字段，或统一按 `vipStatus` 布尔判断 + 单独 role 字段展示。

### H6. `model/DashData.java:44-46 —— dolby.audio 为对象时 optJSONArray 返回 null 直接 NPE`
- 问题：`dolbyAudioObj == null && dolbyObj.has("audio") && !dolbyObj.isNull("audio")` 时执行 `dolbyObj.optJSONArray("audio").optJSONObject(0)`。若 `dolby.audio` 是 **JSONObject**（部分接口版本/番剧返回对象而非数组），`optJSONArray` 返回 null，`null.optJSONObject(0)` 抛 NPE。
- 影响：含杜比音轨的 DASH 播放/下载解析崩溃。
- 修复：先判 `optJSONArray("audio") != null` 再取 [0]，对象形态直接 `optJSONObject("audio")`。

### H7. `util/VideoPreloadManager.kt:21-47 —— 跨线程无同步 + 异常后 isLoading 永久卡死`
- 问题：`allItems` 是普通 `mutableListOf`，`isLoading`/`currentIndex` 非 volatile；`loadInitial/loadMore` 在 `CenterThreadPool`（IO 协程）里修改，`getItem/moveToIndex/preloadVideoUrl` 在 UI 线程读取 → 并发修改/不可见。更严重的是 `loadInitial` 里 `fetchFeedPage()` 抛异常时 `isLoading = false` 那行不会执行，协程异常被 CenterThreadPool 吞掉后 **isLoading 永远为 true**，后续 `loadMore()` 全部短路，短视频列表只能加载第一页。
- 影响：短视频页偶发"滑到底部加载不出更多"且不可恢复；极端情况下 UI 线程遍历 allItems 触发 ConcurrentModificationException。
- 修复：`isLoading` 加 `@Volatile` 并用 `try/finally` 复位；`allItems` 换 CopyOnWriteArrayList 或统一在 IO 线程操作。

### H8. `util/CenterThreadPool.java:78-96 —— run() 不捕获任务异常，协程异常直达全局崩溃处理器`
- 问题：`BuildersKt.launch(...)` 的 lambda 直接 `runnable.run()`，runnable 内抛出的异常会终止协程；`kotlinx.coroutines` 对无 CoroutineExceptionHandler 的 launch 异常会转发到线程默认 uncaughtExceptionHandler（ErrorCatch.java:31-47 已注册为"弹 CatchActivity + killProcess"）。外层 `catch (Throwable)` 只能捕获 launch 调用本身的异常，兜不住任务内异常。
- 影响：任何经由 `CenterThreadPool.run` 提交且自身未 try/catch 的任务（如 H7 场景）一旦出错，**整个应用被杀进程**，而非像线程池那样仅失败该任务。这是"看似安全实则裸奔"的典型陷阱。
- 修复：lambda 内 `try { runnable.run(); } catch (Throwable t) { Logu.e(t); }`，或给 COROUTINE_SCOPE 挂 SupervisorJob + CoroutineExceptionHandler。

---

## Medium（健壮性 / 资源问题）

### M1. `util/TimeUtil.kt:36-43 —— 跨午夜后"今天"边界最长滞后 24 小时`
- 问题：`cachedTodayStartMillis` 只有在"距上次设置超过 24h"或时钟回拨时才刷新。应用在前一天 23:00 首次调用后保持运行，到次日 00:30 时距上次仅 1.5h，`cachedTodayStart` 仍是昨天的零点，凌晨新动态全部显示"昨天"。
- 影响：夜间跨天后动态/评论时间显示错误，直到距上次调用满 24h。
- 修复：改为缓存"日期字符串"而非毫秒（`cachedDateKey != toDateKey(now)` 时刷新）。

### M2. `util/FileUtil.java:37-57 —— deleteFolder 删不掉空目录且可能 NPE`
- 问题：`deleteFolder` 对子目录只在 `listFiles().length != 0` 时递归（第 51 行），**空子目录既不递归也不删除**，最后 `folder.delete()` 因非空而失败，整个清理静默无效；且 `Objects.requireNonNull(file.listFiles())` 在 IO 异常时抛 NPE（第 51 行，与第 32 行 clearCache 同类）。
- 影响：缓存清理不彻底（Glide 缓存会残留空目录），极端情况下 NPE 崩溃。
- 修复：空目录直接 `file.delete()`；listFiles() 判空。

### M3. `util/FileUtil.java:59-77 —— readString 不关闭输入流/Channel`
- 问题：`FileInputStream` 与 `FileChannel` 从未 close（异常路径和正常路径都漏）。
- 影响：每次读取文件泄漏一个 fd，长列表（字幕/本地视频元数据）场景 fd 耗尽风险。
- 修复：try-with-resources。

### M4. `util/Cookies.java:17-23 —— 值为空/含 "=" 的 Cookie 被静默丢弃`
- 问题：`cookie.split("=")` 无 limit：值含 `=`（如 base64 填充、`a=b=c`）时 `parts.length > 2` 整条丢弃；值为空（`key=`）时 `parts.length==1` 也丢弃。`putCookie/setCookies` 会经此类完整重写一次 Cookie 串。
- 影响：若登录接口返回含 `=` 的 Cookie 值（部分渠道存在），该 Cookie 永久丢失导致鉴权不全。
- 修复：`split("=", 2)` 且值允许为空。

### M5. `util/JsonUtil.java:29-43 —— search() 越界崩溃 + 字符串内括号误判`
- 问题：循环内 `char nextChar = input.charAt(j + 1);` 当搜索键位于字符串**末尾**时越界抛 StringIndexOutOfBoundsException（`"name":"x"` 即触发）；且计数不区分括号是否在字符串值内，值含 `{ } [ ]` 的字段（如描述文本带花括号）会 count 错乱返回 defaultValue。
- 影响：`OpusApi.java:59` 用 `JsonUtil.search(html, "detail", "")` 解析文章 HTML，遇到上述边界内容时解析失败或抛异常。
- 修复：循环到 `j < len-1` 边界处理；用状态机区分字符串/转义，或直接改用正则/JSON 解析。

### M6. `util/ProtobufParser.java:58-118 —— 字段 wireType 不匹配时既不读取也不跳过，解析错位`
- 问题：`parseDanmakuElem` 的每个 case 都只写 `if (wireType == 0/2) xxx = ...`，wireType 不匹配时**该字段的载荷没有被消费**（tag 已读但 payload 留在流里），后续字段全部错位读成垃圾。
- 影响：B 站弹幕协议若出现字段类型变化（如某版本 id 变 string），整段弹幕解析错乱且不报错。
- 修复：else 分支调用 `skipField(input, wireType)`。

### M7. `util/EmoteUtil.java:63-79 —— 表情尺寸公式可疑（可能放大 40~50 倍）`
- 问题：`drawable.setBounds(0, 0, (int)(size * sp2px(18) * scale), ...)`。B 站表情 `size` 为像素尺寸（如 24~84），`sp2px(18)≈50px`，84×50≈4200px——远超合理表情尺寸；若 size 是缩放系数则乘以 18sp 又偏小。公式与上游"腕上哔哩"的实现差异需要实测确认。
- 影响：若 size 确为像素值，所有表情会渲染成巨大色块（或已被其他层缩放掩盖，需真机验证）。
- 修复：确认 size 语义后改为 `size * scale`（px）或按 dp 换算一次。

### M8. `util/GlideUtil.java:92-103 —— transitionEnabled 静态缓存永不失效`
- 问题：`transitionEnabled` 首次读取设置后永久缓存（volatile 但从不重置）。用户在设置页切换"加载过渡动画"后，已启动进程内所有图片过渡动画行为不变。
- 影响：设置项修改需重启才生效，与设置页即时生效的预期不符。
- 修复：改为每次读取或提供失效方法（设置页保存时调用）。

### M9. `util/AccountManager.java:214-229 —— removeAccount 不清理当前登录态；账号凭证明文存储`
- 问题：删除当前账号时只清 `current_account_mid`，**不清 mid/cookies/refresh_token**（SharedPreferences 仍保留），用户仍处于"已登录"状态但账号已从列表移除，切换/重新登录行为混乱；且 `AccountInfo` 将 SESSDATA、refresh_token、access_key 以明文 JSON 存入 SharedPreferences（第 71-81 行 toJson）。
- 影响：删除账号后登录态残留（用户以为已退出）；root 设备/备份提取可直读登录凭证。
- 修复：删除当前账号时一并清空登录相关 key；敏感凭证至少使用 EncryptedSharedPreferences。

### M10. `util/FolderManager.kt:48-59 —— 文件夹数据非原子写入且无锁`
- 问题：`saveAllFolders` 直接 `file.writeText()` 覆盖写（无临时文件+rename），写入中途崩溃/断电会得到截断 JSON，下次 `getAllFolders` 解析失败 → 所有文件夹配置清空；且读写无任何同步，DownloadService 与 UI 并发增删文件夹会互相覆盖。
- 影响：用户整理的文件夹偶发整体丢失。
- 修复：临时文件 + rename 原子替换；操作加对象锁。

### M11. `util/VideoMetaManager.kt:54-62 —— 视频元数据非原子写入`
- 问题：`saveMeta` 直接覆盖写 `.video_meta.json`，崩溃时元数据损坏；`readMeta` 失败静默返回空 VideoMeta，导致文件夹归属、画质信息丢失。
- 影响：下载中/下载后进程被杀，视频的文件夹归属与画质记录丢失。
- 修复：同 M10，原子写 + 失败保留旧文件。

### M12. `util/MsgUtil.java:36-52 —— postSticky 的 Snackbar 消息滞留，下个页面弹出过期提示`
- 问题：`showMsg/showMsgLong` 用 `postSticky` 发事件，若当前无 Activity 消费（后台任务报错、页面切换瞬间），sticky 事件驻留 EventBus，`BaseActivity.onResume`（BaseActivity.kt:213）会立即弹出**之前遗留的过期提示**。
- 影响：用户打开任意新页面时莫名弹出一条几分钟前的错误/提示。
- 修复：改为普通 post + 页面注册时主动查询最新一次消息，或带过期时间戳消费后 remove。

### M13. `util/LinkUrlUtil.java:63-68 —— search.bilibili.com 关键词解析粗糙且可能 NPE`
- 问题：`URLDecoder.decode(url.getQuery().replace("keyword=", ""))`：`getQuery()` 为 null 时 NPE（try 捕获后落到浏览器兜底，体验尚可但逻辑错）；`replace("keyword=", "")` 会误删所有出现该子串的位置，且未剥离 `&order=...` 等其他参数，解码结果可能包含多余参数串。
- 影响：带额外参数的搜索链接关键词错误。
- 修复：按 `&` 拆分参数后取 `keyword` 项再 URLDecoder。

### M14. `api/VideoInfoApi.java:190 —— is_upower_exclusive 缺省值错误（字段缺失时全部误判为充电专属）`
- 问题：`data.optBoolean("is_upower_exclusive", true)`。org.json 的 optBoolean 对非布尔值返回 fallback：当字段缺失（或返回 0/1 整数形态）时结果为 **true**。当前接口文档该字段恒为布尔 false（bilibili-API/docs/video/info.md），若字段偶发缺失，普通视频会被标记为充电专属。
- 影响：充电锁标识、下载限制等逻辑对缺失字段的视频误触发。
- 修复：默认值改为 `false`。

### M15. `model/VideoInfo.java:108-110 —— toCard() 对空 staff 直接 get(0) 崩溃`
- 问题：`staff.get(0).name`。非联合投稿时 `VideoInfoApi` 会用 owner 填充 staff，但若 owner 字段缺失（UP 主被封/接口精简），staff 为空，toCard() 抛 IndexOutOfBoundsException。调用方 `SendDynamicActivity.kt:86` 无兜底。
- 修复：`staff.isEmpty() ? "" : staff.get(0).name`。

### M16. `util/ArticleContentParser.java:269-303 —— parseTextContent 无深度限制递归`
- 问题：`parseTextContent` 对 children 无限递归，服务端构造深嵌套 JSON（或解析异常内容）时栈溢出崩溃。
- 影响：专栏内容解析的 DoS 面（内容来自用户投稿，可被恶意构造）。
- 修复：限制递归深度（如 32 层）。

### M17. `model/LocalVideo.java:32-42 —— 画质标签映射错误（qn 116 显示 "2K"）`
- 问题：`qn >= 116 → "2K"`。B 站 qn 116 是 **1080P60 高帧率**（无 2K 档），qn 125/126/127（HDR/杜比视界/8K）落入 `>=120 → "4K"`。
- 影响：本地视频列表画质标签显示错误。
- 修复：116 → "1080P60"；125/126/127 单独处理。

---

## Low（风格 / 性能 / 死代码）

### L1. `util/NetWorkUtil.java:366-382 —— uncompress() 死代码且实现有死循环风险`
- 问题：全工程无任何调用（grep 验证）。实现上 `while (!inflater.finished())` 不检查 `needsInput()/返回 0`，输入被截断时会无限自旋；异常被 printStackTrace 吞掉后返回部分数据。
- 影响：当前无影响；一旦未来被用于解压会用 CPU 空转/静默返回坏数据。
- 修复：删除或按规范实现（检查 `inflate` 返回值与 needsInput，失败抛异常）。

### L2. `util/SharedPreferencesUtil.java:134-142 —— beginBatchEdit/applyBatch 是幻觉 API（死代码）`
- 问题：`beginBatchEdit()` 创建 editor 后直接丢弃；`applyBatch(Runnable)` 创建 editor 但从不传给 Runnable（注释写"通过ThreadLocal或回调"但根本没实现），执行的操作仍各自 apply，批处理语义完全无效。全工程零调用。
- 修复：删除，或真正实现 `edit(Consumer<Editor>)`（已有）并推广使用。

### L3. `util/FileUtil.java:32-33,51 —— clearCache/deleteFolder 的 requireNonNull 可能 NPE`
- 问题：`Objects.requireNonNull(cacheDir.listFiles())` 在 listFiles 返回 null（IO 错误、目录被并发删除）时抛 NPE。
- 修复：判空跳过。

### L4. `util/TerminalContext.java:267-268, 362-364 —— leaveDetailPage() 空实现；getTerminalKey(Reply) 永远返回 null`
- 问题：`leaveDetailPage()` 注释宣称"释放上下文对象"但方法体为空；`getTerminalKey` 的 Reply 分支声明了 `Reply reply = (Reply) item;` 后没有任何 return 语句，落到 `return null`。
- 影响：依赖 getTerminalKey 做缓存键的调用方对 Reply 恒 miss（若未使用则无影响）；leaveDetailPage 是"语义承诺但无行为"的占位。
- 修复：删除或补全实现。

### L5. `util/CenterThreadPool.java:42-67 —— ThreadPoolExecutor 分支为死代码`
- 问题：`Build.VERSION.SDK_INT < JELLY_BEAN_MR1(17)` 才走线程池分支，而项目 minSdk=24，该分支永不可达；且 `getThreadPoolInstance()` 中 `if (THREAD_POOL == null) return null` 与随后 `new ThreadPoolExecutor` 的逻辑自相矛盾（THREAD_POOL 为 null 时直接返回 null，永远建不出池）。
- 修复：删除线程池分支，仅保留协程路径。

### L6. `util/SSLSocketFactoryCompat.java:39-70 + NetWorkUtil.java:121-147 —— 全部为 SDK<=22 死代码，且含拼写错误的加密套件名`
- 问题：`NetWorkUtil.setOkHttpSsl` 仅 `SDK_INT > 22` 直接返回原样（minSdk 24 → 恒不生效）；`SSLSocketFactoryCompat` 静态块里 `"TLS_ECHDE_RSA_WITH_AES_128_GCM_SHA256"`（第 49 行）拼写错误（应为 ECDHE），且整个类只在 SDK<=22 分支被引用。
- 影响：无运行时影响，但误导读者以为存在"信任所有证书"路径（实际没有）；错误套件名一旦被复制到生效代码会静默不匹配。
- 修复：删除死分支，或至少修正拼写。

### L7. `util/Logu.java:70-78 —— getCaller 栈索引偏移，日志 TAG 恒显示调用者的调用者`
- 问题：`getStackTrace()[4]`：索引 0=getStackTrace、1=getCaller、2=Logu.v/d、3=实际调用方、4=调用方的调用方，标签层数恒差一。
- 影响：日志 TAG 误导排障。
- 修复：改用索引 3 或 `Thread.currentThread().getStackTrace()[3]` 并注释。

### L8. `util/AnimationUtils.java:43-47 —— crossFade 两个重载参数名互相颠倒（行为恰好正确）`
- 问题：2 参版本声明 `(toShow, toHide)` 却调用 3 参版本 `crossFade(toShow, toHide, 100)`，而 3 参版本参数序是 `(toHide, toShow)`。现有调用方（OpusInfoActivity.kt:76 等）恰好按"先隐藏后显示"传参，行为正确；但任何按 2 参命名语义（先显示后隐藏）写的新调用都会把两个视图的显隐弄反。
- 修复：统一参数命名顺序。

### L9. `util/NetWorkUtil.java:160-164 —— getJsonPrivacy 依赖 webHeaders 的硬编码下标`
- 问题：`headers.set(1, ...)` 假定 Cookie 恰好是下标 1；webHeaders 后续增删元素会静默改到错误请求头。
- 修复：构造时按 name 查找 Cookie 下标，或改为不可变结构+工具方法。

### L10. `util/ViewCache.java:15-62 —— LinkedList/SparseArray 无同步`
- 问题：若从多线程回收/取用 View（如多个 RecyclerView 池并发）会损坏链表。
- 影响：当前主要单线程使用，风险低。
- 修复：加锁或换 ConcurrentLinkedQueue。

### L11. `model/DownloadSection.java:32,59-65 —— id 用 getInt 读 long 列；截断阈值 off-by-one；substring 未判空`
- 问题：`id = cursor.getInt(0)`（第 32 行）与 aid/cid 的 getLong 不一致；`title.substring(0, min(8,...))` 配 `title.length() > 7` 时 8 字符标题会显示成 "12345678..."；title/child 为 NULL 时 substring 直接 NPE（构造函数内，会拖垮整个列表加载）。
- 修复：getLong；阈值改 `> 8`；判空兜底。

### L12. `model/HighEnergyData.java:9 —— events 声明为 float[]，实际 JSON 为 {"default": [...]}`
- 问题：模型与接口结构不对应，目前靠 `PlayerApi.getHighEnergyData` 解析端展平兜底（optJSONObject→optJSONArray("default")）。
- 影响：换解析方式或直接序列化该模型会取空。
- 修复：模型改为含 `default` 数组的结构或注明展平来源。

### L13. `model/ArticleCard.java:13 —— upName 字段三种语义（UP 名/分类名/固定文案）`
- 问题：UserInfoApi:195 填作者名、SearchApi:148 填 `category_name`、DynamicApi:495 填"投稿文章"，同一字段在搜索页作者位置显示分类名。
- 修复：拆字段或解析端统一取作者名。

### L14. `model/Bangumi.java:92-99 —— 纯模型内嵌 SharedPreferences 读取全局设置`
- 问题：`toPlayerData()` 里 `SharedPreferencesUtil.getLong("mid", 0)`，模型层依赖 Android 全局状态，无法脱离上下文单测，违反"纯解析模型"约定。
- 修复：mid 由调用方注入。

### L15. `model/Lyric.java:26-38 —— parseLrc 只取首个时间戳，多时间戳行内容拼接进歌词`
- 问题：`matcher.find()` 只处理第一个 `[mm:ss]`，同一行多个时间戳（常见"合唱/分段"LRC）时剩余时间戳文本会混进歌词正文。
- 修复：循环匹配并跳过全部时间戳。

### L16. `model/OpusCard.java、model/Playlist.java —— 整类死代码`
- 问题：全工程零引用（grep 验证）；Playlist 字段名（songCount/playCount 等）与 B 站音频接口实际字段（song/statistic.play）不对应，属幻觉模型。
- 修复：删除或按真实接口重写。

### L17. `util/SettingsKeys.kt:16 与 util/SharedPreferencesUtil.java:54 —— padding 键名两套并存`
- 问题：SettingsKeys.PADDING_H = "paddingH_percent"（实际在用），SharedPreferencesUtil.padding_horizontal = "padding_horizontal" 全工程无引用（死常量）。两套命名易导致未来误用。
- 修复：删除死常量，统一走 SettingsKeys。

### L18. `util/WatchAdapter.kt:106-120 —— PerformanceOptimizer 死代码，且含 System.gc()`
- 问题：`cacheImage/getCachedImage/clearImageCache/suggestGarbageCollectIfNeeded` 全工程无调用；`suggestGarbageCollectIfNeeded` 直接 `System.gc()`（Android 上主动 GC 反而可能引发卡顿）。
- 修复：删除该 object 或去掉 System.gc。

### L19. `util/MsgUtil.java:79-98 —— processSnackEvent 时长分支与 Snackbar 常量错位`
- 问题：`duration == Snackbar.LENGTH_SHORT(-1) → 1950ms`、`LENGTH_LONG(0) → else 分支 2750ms`、`LENGTH_INDEFINITE(-2) → MAX_VALUE`；LENGTH_LONG(0) 无法与"默认 2750"区分，语义混乱但结果尚可。
- 修复：按常量显式映射。

---

## Info（备注）

### I1. `BiliTerminalApp.kt（整文件）—— @HiltAndroidApp 的 Application 未注册，Hilt 全工程未启用`
- AndroidManifest.xml:27 注册的是 `.BiliTerminal`（Java）。全工程 grep 无任何 `@AndroidEntryPoint/@HiltViewModel/@Inject`，BiliTerminalApp.kt 的 onCreate（含 SharedPreferences 初始化、延迟动态/消息检查、主题设置）**永远不会执行**，其中 `if (context == null)` 包裹初始化代码的门槛也暗示它可能是早期迁移遗留。两套 Application（BiliTerminal.java 与 BiliTerminalApp.kt）存在大量重复代码（context/DPI/jumpTo 等），维护极易改漏。

### I2. `util/MsgUtil.java:171-225 —— err() 把原始异常/堆栈直接弹给用户`
- 设计取向问题：普通用户会看到"错误：org.json.JSONException: ..."等原始信息；建议至少脱敏或只保留友好文案。

### I3. `util/DmImgParamUtil.java:41-124 —— 风控指纹参数为随机伪造值`
- `dm_img_str/dm_cover_img_str` 是硬编码字符串、`dm_img_list/dm_img_inter` 用 Random 生成，与真实浏览器 canvas 指纹完全无关。这是"反风控"的安慰剂实现：一旦 B 站校验指纹一致性，这些参数非但无用还可能加剧风控判定。

### I4. `util/Aria2Util.java —— 名称残留`
- 外部 Aria2 已移除，仅剩参数读取（aria2_enabled/aria2_split），类名与设置项名仍然叫 aria2，容易误导后续维护者以为还在用 Aria2。

### I5. `util/NetWorkUtil.java:384-391 —— getInfoFromCookie 用 contains 匹配 Cookie 名`
- `i.contains(name + "=")` 是子串匹配而非精确匹配；当前调用方（bili_jct/DedeUserID/SESSDATA/buvid3）不存在前缀冲突，暂无实际 bug，但换名字短的键（如 "id"）就会误匹配，属于埋雷。

### I6. `util/NetWorkUtil.java:47-61 —— getCachedCookies 的内存缓存与 SharedPreferences 双写`
- 设计上有意为之（避免每次请求读 SP），但任何绕过 NetWorkUtil 直接写 SP cookies 的代码都会与缓存脱节（当前无此类调用，属约定风险）。

### I7. `util/NetWorkUtil.java:488-513 —— webHeaders 是 public static final 可变 ArrayList`
- 全局共享可变容器，任何代码都能 add/remove；`get/post` 按 i+=2 成对取头，若被改为奇数长度会 AIOOBE。建议封装为不可变列表+单点替换 Cookie 值。

### I8. `util/NetWorkUtil.java:195-220 —— executeJsonWithRiskRetry 只对 -352/-412 重试`
- 网络 IOException 不重试直接抛出（doctype 重试路径 executeWithDoctypeRetry 才覆盖 IO 错误），两条重试逻辑并存且语义不同，后续维护易混淆；建议统一。

### I9. `util/AccountManager.java:124-169 —— saveCurrentAccount 内含同步网络请求`
- `UserInfoApi.getUserInfo(mid)` 在调用线程同步执行；已核实全部 7 处调用点（SplashActivity/QR/密码/SMS/TV 登录）均位于 IO 线程，当前无主线程网络风险；但该方法签名未声明"需后台线程"，属隐式契约。

---

## 附：审查方法说明

- util 包 40 个文件全部逐行人工审查；model 包 64 个文件全部覆盖（23 个核心文件人工逐行，其余 41 个由两个并行子审查代理逐行审查，结论已复核）。
- 每条结论均标注了精确文件与行号；"死代码/幻觉功能"类结论均以全工程 grep 的零引用为依据。
- 未修改任何源代码文件；本报告为唯一交付物。
