# ReBiliClient 全量代码审查汇总报告

> 审查日期：基于 `versionName 26.08.27`（versionCode 2608275）分支快照
> 审查范围：`:app` 主模块全部 363 个源文件（136 Java + 227 Kotlin）+ 构建配置 + Manifest + 测试
> 审查方式：5 个并行子代理分区深度审查（总计 ~348 个文件、约 4 万行）+ 主代理亲自审查核心基础设施与交叉验证
> 详细分区报告：
> - `api-report.md`（网络 API 层 41 文件，53 条）
> - `activity1-report.md`（activity 根/player/reply/message/live/dynamic 等 38 文件，45 条）
> - `activity2-report.md`（activity search/settings/user/video/vote 等 85 文件，70 条）
> - `util-model-report.md`（util 40 + model 64 文件，56 条）
> - `adapter-ui-report.md`（adapter/ui/player/service/helper 等，62 条）

## 一、总体结论

这是一个典型的 "vibe coding" 产物（与 AGENTS.md 自述一致）：功能覆盖广、代码量大，但存在大量 AI 生成代码的典型问题——**崩溃路径多、并发意识弱、幻觉功能与死代码泛滥、文档与实现严重脱节**。

### 核心数据（5 个子代理 + 主代理交叉审查合计，含少量跨报告重叠）
| 严重度 | 数量 | 说明 |
|---|---|---|
| Critical | 23 | 确定性崩溃、数据丢失、外部可控崩溃、凭证泄露 |
| High | 52 | 明显逻辑 bug、资源泄漏、敏感信息 |
| Medium | 105 | 健壮性、并发竞态、性能 |
| Low / Info | 106 | 风格、死代码、注释不符 |
| **合计** | **286** | 全部条目含精确 文件:行号 |

> 注：部分问题被多个独立审查方同时发现（如分页卡死、NetWorkUtil 连接泄漏、BiliTerminalApp 未注册），重叠本身就是高置信度信号，数字略有重复计入。

## 二、Top 问题速览（按影响面）

### 🔴 影响面最大的一类问题

1. **列表页"加载更多"系统性失效（High，影响 10+ 页面）**
   - `activity/base/RefreshListActivity.kt`：`goOnLoad()` 置 `isLoading = true` 后，**加载成功路径没有任何地方复位**（全工程仅 3 个页面调用 `onLoadComplete()`：UserSeriesActivity/SeriesInfoActivity/HistoryActivity）→ 第一次"加载更多"成功后 `isLoading` 永久为 true → **后续滚动到底部永远不再加载**。已确认受影响的真实翻页页面：FollowUsersActivity（关注/粉丝）、FollowingBangumisActivity、FollowLiveActivity、NoticeActivity、FavoriteVideoListActivity、FavouriteOpusListActivity、WatchLaterActivity、MedalWallActivity、CollectionInfoActivity、SponsorActivity 等 10 个，均只能看到 2 页数据。
   - `activity/base/RefreshMainActivity.kt`：相反的问题——`goOnLoad()` 只设 `swipeRefreshLayout.isRefreshing` 不设成员 `isRefreshing`，而 `onScrolled` 检查的是成员 → 滚动持续触发 `onLoad`（仅 100ms 防抖）→ DynamicActivity/RecommendActivity 产生**并发分页请求**（offset/freshType 竞态，可能重复/跳漏数据）。

2. **两个 Application 类并存，Hilt 从未生效（High，架构）**
   - Manifest 声明 `android:name=".BiliTerminal"`（Java），而 `BiliTerminalApp.kt`（Kotlin，`@HiltAndroidApp`）**从未被实例化**：`BiliTerminalApp.context` 永远为 null；Hilt 组件从未初始化；`hilt-android`/`hilt-compiler(KSP)` 是纯死依赖（全工程无任何 `@AndroidEntryPoint`/注入点）。
   - 两套重复的静态状态（`BiliTerminal.context/instance` vs `BiliTerminalApp.context/appInstance`），代码混用，是 Kotlin 迁移未完成的半成品。

3. **文档描述的"新层"架构 100% 是空壳（Info，但影响开发决策）**
   - `di/`、`data/`、`data/repository/`、`network/`、`network/api/`、`network/model/`、`ui/base/` 等 **23 个目录为空**；proguard 中 `network.model` 的序列化规则是死规则；AGENTS.md 声称的 Retrofit + kotlinx-serialization + MVVM 实际不存在。

### 🔴 崩溃类（Critical 精选）

4. **外部应用可稳定崩溃攻击（Critical）**：`GetIntentActivity.kt:20,32` 与 `ImageViewerActivity.kt:31` 在 Manifest `exported="true"` 下对外部可控 Intent 直接 `!!`/`toLong()`/`getStringArrayListExtra!!`——任意应用发一条 `bilibili://video/abc` 或空 Intent 即让本应用崩溃（DoS）。

5. **登录凭证明文打 logcat（Critical，安全）**：`QRLoginFragment.kt:383-384,309-310` 登录后把 access_token / refresh_token / 完整 Cookie（含 SESSDATA、bili_jct）用 `Log.d/e` 打印，R8 未剥离日志，**release 包依然泄露**，adb/bugreport 即可提取长期凭证。

6. **WebView 危险配置 + JS 桥（Critical，安全）**：`CaptchaWebViewActivity.kt:93-112` 开启全部危险开关并暴露 `"Android"` JS 桥、baseURL 设为 `https://www.bilibili.com/`，页面脚本可读登录 Cookie，构成实际攻击面。

7. **Cookie 刷新中途崩溃（Critical）**：`CookieRefreshApi.java:99` 对可能为空的 `DedeUserID` 直接 `Long.parseLong("")` → NumberFormatException，登录态更换中途崩溃；`SpecialLoginActivity.kt:55` 导入凭证同样 `"".toLong()` 崩溃。

8. **未登录收藏必崩（Critical）**：`FavoriteApi.java:285` / `LikeCoinFavApi.java:48` 对 `mid=0` 做 `substring(length-2)` → StringIndexOutOfBoundsException（该 hack 被复制两份）。

9. **播放器 onDestroy 资源全泄漏（Critical）**：`PlayerActivity.kt:1496-1500` 在 `!isFinishing`（系统回收/不保留活动）时提前 return，跳过 ijkPlayer/Timer/WebSocket/EventBus 全部清理，残留 WebSocket 回调持续操作已销毁 Activity。

10. **专栏/评论内容触发的确定性崩溃（Critical）**：`OpusParagraph.java:98-100` 空 blockquote 时 `setSpan(0,-1)`；`Reply.java:75` 对 `location` 硬编码 `substring(5)`——单条异常内容即整页解析失败。

11. **APK 断点续传损坏（Critical）**：`UpdateManager.kt:141-158` 服务器不支持 Range（返回 200）时把新 APK **追加**到旧残片后，APK 永久损坏装不上（且残留文件让下次续传继续坏）。

12. **列表数据不一致崩溃（Critical）**：`LocalPageChooseActivity.kt:78-97` 后台线程改列表 + 主线程立即 notify → RecyclerView "Inconsistency detected"；`LocalListActivity.kt:513` 虚拟合集用全局索引访问过滤列表 `folderVideos[startVideoIdx]` → 越界崩溃且起始视频错位；`VideoInfoFragment.kt:518-530` 三连成功回调在 IO 线程 setImageResource → CalledFromWrongThreadException。

### 🔴 安全类

13. **明文 HTTP + 敏感数据上传（High）**：`AppInfoApi.java` 4 处 `http://api.biliterminal.cn/...`（公告、崩溃堆栈、赞助商），配合 `usesCleartextTraffic="true"`；崩溃堆栈明文传输可被中间人窃听篡改。

14. **敏感信息进日志（High）**：`PrivateMsgApi` 私信正文 + 完整 Cookie(SESSDATA) 打 logcat；`NetWorkUtil.post()` 打印 POST 体（登录密码）；大量 `Log.e("debug", ...)` 遗留。

15. **更新供应链风险（Medium-High）**：更新渠道是个人网盘 `config.json`（`UpdateManager.kt:22`），下载的 APK **无签名/哈希校验**（仅靠系统签名冲突兜底）；`installApk` 给目标应用授予 WRITE 权限、未检查 `canRequestPackageInstalls`。

16. **Cookie 并发丢失（High）**：`NetWorkUtil.saveCookiesFromResponse`（OkHttp 拦截器任意线程调用）read-modify-write 无锁，并发 Set-Cookie 互相覆盖 → 偶发登录失效/风控。

### 🔴 明显逻辑 bug（High 精选）

14. **弹幕颜色全错**：`ToolsUtil.getRgb888` 把 RGB 三通道十进制拼串（白=255255255 而非 16777215），每一条发送的弹幕颜色都错。
15. **私信/评论/弹幕发送未 URL 编码**：`ReplyApi.java:153`、`PrivateMsgApi.java:217`、`DanmakuApi.java:30,42`，含 `&`/`=` 内容被截断。
16. **私信 JSON 注入**：`PrivateMsgActivity.kt:139` 用户输入直接拼 JSON 字符串。
17. **私信会话列表逻辑写反**：`PrivateMsgApi.java:157` `!has && isNull` 恒等于 `!has`，服务端一旦返回 `account_info` 字段私信列表整体变空。
18. **私信空列表轮询崩溃**：`PrivateMsgActivity.kt:199` 空会话 `list[size-1]` 越界，每 15 秒弹一次错 + 退后台仍轮询。
19. **播放器长按后手势全失效**：`PlayerControlDelegate.kt:282` `isLongPressing` 置 true 永不复位。
20. **亮度手势是死功能**：`PlayerControlDelegate.kt:180-182` 只更新 StateFlow 无人消费（grep 全工程无读取）。
21. **Menu 键同时打开菜单并关闭当前页**：`InstanceActivity.onKeyDown`（打开 MenuActivity）→ super → `BaseActivity.onKeyDown`（finish()）。
22. **转发"from"参数丢失**：`InstanceActivity.kt:16` 对新建 Intent 判 `hasExtra("from")` 恒 false（应判 `getIntent()`）。
23. **重定向处理 NPE + 连接泄漏**：`NetWorkUtil.java:96` 相对路径 Location 时 `URI.getScheme()` 为 null → NPE（未捕获）；`NetWorkUtil.java:100-109` 手动跟进重定向不 close 原 Response → 连接泄漏。
24. **分页/下载并发竞态**：`DownloadService.start()` 无同步可双批次并发下载同一文件；分片 `join(30s)` 超时后与残留线程并发写同一文件；`onDestroy` 异常退出删除整个下载目录（已完成文件误删）；`downSubtitles` 字幕文件已存在直接中止整个下载。
25. **主线程/后台线程数据源混用**：`ReplyAdapter.kt:365`、`DynamicHolder.kt` 等后台线程直接改 `ArrayList` UI 数据源。
26. **WBI 缓存键不一致**：`TerminalContext` bvid 查询用 `video_bvid` 键、缓存写入用 `video_aid` 键 → bvid 路径缓存永远 miss，每次重新网络请求。
27. **Sticky Snack 事件不清理**：`BaseActivity.onResume` 反复消费同一个 sticky SnackEvent，后台消息延迟到几小时后打开 App 才弹出/重复弹。
28. **`SplashActivity.kt:88` 字符串 `!= ""` 引用比较**（Java 反模式，refresh_token 空存时误判）。
29. **`UserSeriesActivity.kt:50-59` 幽灵插入**：加载更多数据从未加入 adapter 却 notifyItemRangeInserted，数据永不显示 + 滚动触发不一致崩溃。
30. **WebView 输入校验缺失**：`SetupUIActivity.kt:79,86,92` 非数字输入 `toFloat()/toInt()` 直接崩溃。

### 🧹 幻觉功能/死代码（Info/Low 精选）
- `SharedPreferencesUtil.beginBatchEdit()/applyBatch()` —— 完全无效（创建 Editor 后丢弃，无调用方）。
- `BiliTerminalApp.kt` 整类未注册；`SSLSocketFactoryCompat` 整类（minSdk 24 下 `SDK_INT > 22` 恒真，信任所有证书分支永不执行）；`FileUtil.clearCache()`（无调用方）；`FileUtil.requireTFCardPermission()`（空方法）；`TerminalContext.leaveDetailPage()`（空方法）；`WrapContentLinearLayoutManager`（注释宣称的功能未实现，且全工程无引用）；`OpusContentAdapter` TYPE_VIDEO/TYPE_ARTICLE 空分支；`PlayerApi fnvar` 参数拼写错误（应 fnver）。
- `proguard-rules.pro` 中 `network.model` kotlinx-serialization 规则为死规则（包不存在）。

## 三、建议优先修复顺序（按收益/成本）
1. **P0 崩溃与数据安全**：GetIntentActivity/ImageViewerActivity 外部入口加固；CookieRefreshApi/FavoriteApi parseLong/substring 防护；UpdateManager 断点续传修正；OpusParagraph/Reply 解析防护；PlayerActivity onDestroy 无条件清理。
2. **P0 功能正确性**：RefreshListActivity isLoading 复位（影响 10+ 页面翻页）；RefreshMainActivity 防重入；私信/评论/弹幕 URL 编码；ToolsUtil.getRgb888。
3. **P1 架构清理**：删除 BiliTerminalApp.kt 与 Hilt 死依赖（或真正启用）；清空 23 个空目录；删除幻觉方法；统一 Cookie 写入锁。
4. **P1 安全**：AppInfoApi 升级 https；敏感日志清理；更新 APK 校验（对比官方签名/哈希）；Manifest 权限收敛（allowBackup=false、READ_PHONE_STATE 移除、exported=false）。
5. **P2 工程化**：巨型类拆分（PlayerActivity 127KB/3090 行、DownloadService 65KB）；补单元测试（当前仅 7 个测试文件覆盖 363 个源文件）。

（详细条目见各分区报告，每条含精确 文件:行号。）
