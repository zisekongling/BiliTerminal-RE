# ReBiliClient Activity 层深度代码审查报告（第 2 批）

- 审查日期：2026-08-14（版本 26.08.14 对应代码）
- 审查对象：`app/src/main/java/com/RobinNotBad/BiliClient/activity/` 下的 search/、settings/（含 login/、setup/）、user/（含 favorite/、info/）、video/（含 collection/、info/、local/、series/）、vote/ 全部文件
- 目录说明：项目中**不存在** `shortvideo/` 与 `update/` 子目录，对应功能实为 `video/ShortVideoPlayerActivity.kt` 与 `settings/UpdateActivity.kt`、`settings/UpdateHistoryActivity.kt`，已并入本次审查范围。
- 审查方式：逐个 read 全部文件；所有"调用不存在的接口"类怀疑均经 grep 验证；Critical/High 条目均经交叉核验（本人或子代理逐字核对代码原文）。
- 严重度定义：**Critical**=崩溃/数据错误/安全漏洞；**High**=明显逻辑 bug；**Medium**=健壮性/资源问题；**Low**=风格/性能小问题；**Info**=备注。

---

## 统计总览

| 严重度 | 数量 |
|---|---|
| Critical | 5 |
| High | 13 |
| Medium | 31 |
| Low | 13 |
| Info | 8 |
| **合计** | **70** |

系统性问题的横向总结见文末"七、跨文件共性问题"。

---

## 一、Critical（崩溃 / 数据错误 / 安全）

### C-1. `activity/settings/login/QRLoginFragment.kt:383-384、309-310` —— 登录凭证（Cookie / refresh_token / access_token）明文写入 logcat
- 问题描述：WEB 扫码登录成功分支直接 `Log.d("debug-login-cookies", cookies)` 打印完整 Cookie 串、`Log.e("debug-refresh-token", ...)` 打印 refresh_token；TV 端成功分支同样 `Log.d("debug-tv-login-access-token", accessToken)`。`app/proguard-rules.pro` 未配置 `assumenosideeffects` 剥离 `android.util.Log`，release 包中这些日志依然生效。
- 影响：长期有效的 refresh_token / access_token / 完整 Cookie（含 SESSDATA、bili_jct）通过 logcat 明文泄露，adb / 系统 bugreport 即可提取，属账号凭证泄露类安全漏洞。
- 修复建议：删除这 4 行日志；如需调试改用 Logu 且加 debug 门控，并配置 R8 `-assumenosideeffects` 移除 Log。
- 证据：`383: Log.d("debug-login-cookies", cookies)`、`384: Log.e("debug-refresh-token", ...)`、`309: Log.d("debug-tv-login-access-token", accessToken)`、`310: Log.e("debug-tv-mid", ...)`。

### C-2. `activity/settings/login/SpecialLoginActivity.kt:55` —— 导入登录凭证时 NumberFormatException 未捕获导致崩溃
- 问题描述：`NetWorkUtil.getInfoFromCookie("DedeUserID", cookies).toLong()` 在 `try { ... } catch (e: JSONException)` 内执行，但 Cookie 缺 DedeUserID 时返回空串，`"".toLong()` 抛 NumberFormatException（非 JSONException）直接逃逸到主线程。
- 影响：用户粘贴合法 JSON 但缺少 DedeUserID（如旧版导出、复制不全）→ 点确认即崩溃。
- 修复建议：catch 改为 `catch (Exception)` 或单独捕获 NumberFormatException，并在解析前校验 cookies 是否含 DedeUserID。
- 证据：`55: SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, NetWorkUtil.getInfoFromCookie("DedeUserID", cookies).toLong())`，`71: } catch (e: JSONException) {`。

### C-3. `activity/settings/setup/SetupUIActivity.kt:79、86、92` —— 非数字输入直接 toFloat()/toInt() 崩溃
- 问题描述：`save()` 中 `uiScaleInput.text.toString().toFloat()`、`uiPaddingH/V.text.toString().toInt()` 无任何数字校验或异常捕获，EditText 也未限定输入类型（软键盘外可粘贴任意文本）。
- 影响：输入 "abc" 等非法字符后点"预览/确定"，主线程抛 NumberFormatException 直接崩溃。
- 修复建议：改用 `toFloatOrNull()/toIntOrNull()` 并做范围兜底，或 XML 限定 `android:inputType="numberDecimal"/"number"`。
- 证据：`79: val dpiTimes = uiScaleInput.text.toString().toFloat()`、`86: ...toInt()`、`92: ...toInt()`。

### C-4. `activity/video/local/LocalPageChooseActivity.kt:78-97` —— 长按删除分页：后台线程改列表 + 主线程立即 notify，RecyclerView 数据不一致
- 问题描述：二次长按删除时 `pageList.removeAt(position)` 等数据修改被丢进 `CenterThreadPool.run` 后台任务（且在慢 IO `deleteFolder` 之后），而 `adapter.notifyItemRemoved(position)` 在后台任务启动后立即于主线程执行。`PageChooseAdapter` 持有同一个 pageList 引用，主线程（adapter 查询）与后台线程并发读写同一 ArrayList。
- 影响：删除操作触发 RecyclerView "Inconsistency detected" 崩溃，或列表项错位/幽灵残留。
- 修复建议：数据移除与 notify 必须同线程同步完成——先主线程 removeAt 再 notifyItemRemoved，文件删除放后台。
- 证据：`78: CenterThreadPool.run {`、`83: pageList.removeAt(position)`、`91: adapter.notifyItemRemoved(position)`。

### C-5. `activity/video/local/LocalListActivity.kt:513` —— 虚拟合集播放用全局索引访问过滤后的列表，越界崩溃且起始视频错位
- 问题描述：`CacheListAdapter.playVideo`（CacheListAdapter.kt:431）把 `videoList.indexOf(video)`（**全列表全局下标**）作为 `startVideoIdx` 传给 `playVirtualCollection`；而 `LocalListActivity` 内 `folderVideos = videoList.filter { it.folderName == folderName }`（过滤后列表），随后 `if (v == folderVideos[startVideoIdx])` 用全局下标索引过滤列表。
- 影响：文件夹内点击播放时，若该视频的全局下标 ≥ 文件夹内视频数（如文件夹是列表中第 2 个、共 3 个视频而全局下标为 5）→ IndexOutOfBoundsException 崩溃；下标在范围内时也可能定位到错误的起始视频（全局下标 ≠ 过滤后下标）。
- 修复建议：用引用定位，改为 `folderVideos.indexOf(video)`，并加 `startVideoIdx in folderVideos.indices` 守卫。
- 证据：`513: if (v == folderVideos[startVideoIdx])`；`CacheListAdapter.kt:431: onVideoPlayInVirtualCollection?.invoke(currentFolderName, videoList.indexOf(video))`。

---

## 二、High（明显逻辑 bug）

### H-1. `activity/video/info/VideoInfoFragment.kt:518-530` —— 三连成功回调在后台线程直接 setImageResource，必崩 CalledFromWrongThreadException
- 问题描述：`tripleActionRunnable` 经 `like.postDelayed` 在主线程排定，但内部 `CenterThreadPool.run { ... coin.setImageResource(...); like.setImageResource(...); fav.setImageResource(...) }`（522-524 行）在 IO 线程直接改 ImageView（同文件点赞/投币回调都正确包了 runOnUiThread，此处不一致）。
- 影响：长按三连触发即主线程崩溃。
- 修复建议：setImageResource 全部包进 runOnUiThread。
- 证据：`522: coin.setImageResource(R.drawable.icon_coin_1)`、`524: fav.setImageResource(R.drawable.icon_fav_1)`（均在 CenterThreadPool.run 块内）。

### H-2. `activity/settings/SponsorActivity.kt:44-57` + `activity/user/FollowUsersActivity.kt`、`FollowingBangumisActivity.kt`、`favorite/FavoriteVideoListActivity.kt`、`favorite/FavouriteOpusListActivity.kt` —— 分页加载更多永久卡死（缺 onLoadComplete）
- 问题描述：`RefreshListActivity.goOnLoad()` 触发加载前置 `isLoading = true`（RefreshListActivity.kt:132），只有 `onLoadComplete()`/`loadFail()` 能复位。上述 5 个页面的加载更多成功分支只 `setRefreshing(false)`，从未调用 `onLoadComplete()`（grep 确认全工程仅 HistoryActivity、UserSeriesActivity、SeriesInfoActivity 调用）。`checkLoadMore()` 第一道闸门就是 `isLoading`。
- 影响：这些列表页只能加载到第 2 页，之后任何滚动都不再触发加载，列表内容缺失。
- 修复建议：成功分支末尾调用 `onLoadComplete()`；更稳妥的是把复位逻辑下沉到基类。
- 证据：RefreshListActivity.kt:69 `if (listener == null || bottom || isLoading || ...) return`；SponsorActivity.kt:44-57 成功路径无 onLoadComplete()。

### H-3. `activity/video/series/UserSeriesActivity.kt:50-59` —— 加载更多数据从未加入 adapter，notifyItemRangeInserted 幽灵插入
- 问题描述：第 1 页把 `seasonList` 实例传入 `SeriesCardAdapter`（构造即持有该列表）；第 2 页起新建局部 seasonList 装载新数据，却对旧 adapter 调 `notifyItemRangeInserted(oldSize, seasonList.size)`，adapter 数据源未变、`getItemCount()` 不变（SeriesCardAdapter.kt:42-44）。
- 影响：加载更多完全失效（新数据永不显示），且 notify 声称插入 N 条而数量未增长，滚动到底触发 RecyclerView 不一致崩溃。
- 修复建议：adapter 暴露 `addData()` 真正追加数据后再 notify（参照 SeriesInfoActivity.kt:110-112 的 SeriesVideoAdapter.addData）。
- 证据：`54: val oldSize = adapter.itemCount`、`55: adapter.notifyItemRangeInserted(oldSize, seasonList.size)`。

### H-4. `activity/video/info/VideoInfoActivity.kt:167-170` —— EventBus ThreadMode.ASYNC 在后台线程操作 RecyclerView 与 List，且 replyFragment!! 未判空
- 问题描述：`onEvent(ReplyEvent)` 标注 `ThreadMode.ASYNC`，回调运行在 EventBus 后台线程；`ReplyFragment.notifyReplyInserted` 内 `replyList!!.add(pos, reply)` 直接改主线程渲染中的列表、访问 `recyclerView.layoutManager`。且 `replyFragment!!` 在 `initVideoInfoView` 网络回调完成前为 null，而 BaseActivity 在 onStart 即注册（含 sticky 事件）。
- 影响：ConcurrentModificationException / 后台线程改 UI 结构崩溃；进入页面瞬间收到回复事件时 NPE 崩溃。
- 修复建议：订阅改为 `ThreadMode.MAIN`，回调内 `replyFragment?.let { }` + `isAdded` 判断。
- 证据：`167: @Subscribe(threadMode = ThreadMode.ASYNC, sticky = true, priority = 1)`、`169: replyFragment!!.notifyReplyInserted(event)`。

### H-5. `activity/video/info/VideoInfoActivity.kt:94` —— videoInfo.staff[0] 无空列表保护
- 问题描述：`ReplyFragment.newInstance(..., videoInfo.staff[0].mid)` 直接取 staff 首元素；`VideoInfoApi` 解析在 owner/staff 缺失时 staff 为空 ArrayList（VideoInfo.java:21 默认空列表）。同项目 VideoInfoFragment.kt:469 对 staff 有 isNotEmpty() 判空，此处没有。
- 影响：owner/staff 缺失的视频打开详情页即 IndexOutOfBoundsException 崩溃。
- 修复建议：`if (videoInfo.staff.isNotEmpty()) ... else 用 0L`。
- 证据：`94: replyFragment = ReplyFragment.newInstance(videoInfo.aid, 1, videoInfo.stats.reply, seek_reply, videoInfo.staff[0].mid)`。

### H-6. `activity/settings/SettingSearchActivity.kt:83-86` —— 快速退出页面时 onDestroy 访问未初始化的 lateinit 崩溃
- 问题描述：布局经 `asyncInflate` 异步 inflate，`searchArticle/searchUser/searchLive` 三个 lateinit 只在 inflate 完成后的回调中赋值；`onDestroy()` 无条件调 `save()` 读取这些字段。用户在 inflate 完成前退出（快速返回/旋转）→ `UninitializedPropertyAccessException`。
- 影响：概率性崩溃（触发窗口是异步 inflate 完成前退出）。
- 修复建议：save() 内用 `::searchArticle.isInitialized` 判空；更佳做法是开关切换时立即落盘（同时解决 M-3）。
- 证据：`84: save()`（onDestroy 内）；save() 内 `SharedPreferencesUtil.putBoolean(..., searchArticle.isChecked)`。

### H-7. `activity/user/MySpaceActivity.kt:108-112` —— 修改签名入口在用户信息未加载时传入空串，保存后覆盖原签名
- 问题描述：`currentUserInfo` 初始 null、异步加载且失败时保持 null（162-181 行 catch 仅 report）。"修改个人描述"入口直接 `putExtra("currentSign", currentUserInfo?.sign ?: "")`；EditSignActivity.kt:43-46 收到 null/空串即填空并允许保存。
- 影响：弱网/接口失败时编辑页显示空白，用户输入新签名保存后原签名被整体覆盖，数据不可恢复。
- 修复建议：EditSignActivity 自行异步拉取当前签名预填，或 currentUserInfo == null 时禁用该入口。
- 证据：`110: intent.putExtra("currentSign", currentUserInfo?.sign ?: "")`。

### H-8. `activity/user/EditUserInfoActivity.kt:41-43`（配合 `api/UserInfoApi.java:126`）—— 接口失败时"加载失败"占位昵称被预填并可被提交
- 问题描述：`UserInfoApi.getCurrentUserInfo()` 在 data 缺失时返回占位对象 `new UserInfo(0, "加载失败", ...)` 而非 null；`if (userInfo != null && userInfo.name.isNotEmpty())` 恒通过，把"加载失败"预填进用户名输入框。
- 影响：登录态失效等场景下用户点保存，提交的昵称就是"加载失败"（若服务端校验通过则直接生效），且因 uname 非空，只改生日也会顺带提交错误昵称。
- 修复建议：预填前校验 `userInfo.mid > 0`；或让接口失败返回 null 并判空。
- 证据：`UserInfoApi.java:126: } else return new UserInfo(0, "加载失败", "", "", 0, 0, 0, false, "", 0, "", 0);`。

### H-9. `activity/settings/login/AccountSwitchActivity.kt:119-129` —— 异步获取用户信息后在已销毁 Activity 上调用 Glide.with(this) 崩溃
- 问题描述：`CenterThreadPool.run { getUserInfo(...) }` 完成后 `runOnUiThread { ... Glide.with(this) ... }`，回调无 isDestroyed/isFinishing 判断也无 try-catch。用户刷新后立刻退出，回调到达时 `Glide.with(已销毁 Activity)` 抛 IllegalArgumentException。
- 影响：离开账号切换页后偶发主线程崩溃（网络越慢越易复现）。
- 修复建议：回调开头 `if (isDestroyed || isFinishing) return`，或用 `view.context`/applicationContext。
- 证据：`119: runOnUiThread {`、`122: Glide.with(this)`。

### H-10. `activity/settings/login/CaptchaWebViewActivity.kt:93-112` —— WebView 安全配置过度开放，且以 bilibili.com 为 baseURL 暴露 Cookie 给页面脚本
- 问题描述：为展示极验验证码开启了 `allowFileAccess`、`allowContentAccess`、`allowUniversalAccessFromFileURLs`、`allowFileAccessFromFileURLs`、`MIXED_CONTENT_ALWAYS_ALLOW`、`setAcceptThirdPartyCookies(true)`，并注入名为 "Android" 的 JavascriptInterface；`loadDataWithBaseURL("https://www.bilibili.com/", ...)` 使页面 origin 归 bilibili.com——页面内任何 JS（含远程 geetest CDN 脚本）可通过 `document.cookie` 读取该域已存在的登录 Cookie（SESSDATA 等）。
- 影响：验证码页成为攻击面：凭证可被第三方脚本读取外泄；`shouldOverrideUrlLoading` 返回 false 使任意跳转都在 WebView 内加载。
- 修复建议：关闭全部 file/content/universal 访问开关，mixedContentMode 改 NEVER_ALLOW，baseURL 改为无敏感 Cookie 的占位域名，移除不必要 JS 接口。
- 证据：`96: settings.allowFileAccess = true`、`98: settings.allowUniversalAccessFromFileURLs = true`、`100: settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW`、`112: loadDataWithBaseURL("https://www.bilibili.com/", ...)`。

### H-11. `activity/settings/login/SMSLoginFragment.kt:93-100、214-236` —— 短信倒计时只禁用子 TextView，发送按钮仍可点击，可无限重发短信
- 问题描述：点击监听挂在 `sendSmsBtn`（MaterialCardView）上，`startCountDown()` 只做 `sendSmsText.isEnabled = false/true`（内部 TextView）；禁用子控件不阻止父卡片接收点击。
- 影响：倒计时期间可反复拉起人机验证并重复下发短信（计费/骚扰/接口风控），倒计时 UI 与真实限制脱节。
- 修复建议：倒计时期间禁用 sendSmsBtn 或加 `countDownTimer != null` 拦截，onFinish 再恢复。
- 证据：`93: sendSmsBtn.setOnClickListener {`、`221: sendSmsText.isEnabled = false`。

### H-12. `activity/settings/login/AccountSwitchActivity.kt:159-163`（配合 `util/AccountManager.java:214-229`）—— 删除"当前账号"不清除登录态
- 问题描述：`AccountManager.removeAccount(mid)` 只从账号列表移除并重置 CURRENT_ACCOUNT_KEY=0，不清空 `SharedPreferencesUtil.mid/cookies/csrf/refresh_token`。
- 影响：删除当前账号后应用仍以该账号身份运行（Splash 仍按已登录处理、继续刷新该账号 Cookie），UI 承诺"删除后需重新登录"与实际不符。
- 修复建议：删除当前账号时同步清空登录态字段并刷新 NetWorkUtil Cookie 缓存。
- 证据：`AccountManager.java:225-227: if (currentMid == mid) { putLong(CURRENT_ACCOUNT_KEY, 0); }`。

### H-13. `activity/video/PopularActivity.kt:103-105`、`activity/video/PreciousActivity.kt:103-105` —— 加载失败路径不重置 refreshing/转圈，页面永久卡死且后续滚动不再加载
- 问题描述：`catch (e: Exception) { runOnUiThread { MsgUtil.err(e) } }` 中既无 `refreshing = false` 也无 `setRefreshing(false)`；网络失败后 SwipeRefresh 转圈永转，且 `refreshing` 恒 true 使 onScrolled 的加载更多判断（`!refreshing`）永远不成立。
- 影响：弱网/接口失败一次后热门/必刷页卡死，只能退出重进。
- 修复建议：catch 分支补 `runOnUiThread { refreshing = false; swipeRefreshLayout.setRefreshing(false) }`。
- 证据：`PopularActivity.kt:103-105` catch 块内仅 MsgUtil.err(e)。

---

## 三、Medium（健壮性 / 资源问题）

### M-1. `activity/search/SearchVideoFragment.kt:55`、`SearchArticleFragment.kt:53`、`SearchLiveFragment.kt:64` —— 加载更多 notifyItemRangeInserted 起点多 +1（off-by-one）
- 问题描述：三个搜索 Fragment 均用 `notifyItemRangeInserted(lastSize + 1, ...)`，但对应 adapter（VideoCardAdapter/ArticleCardAdapter/LiveCardAdapter）`getItemCount()` 均无 header 项；对比 UserDynamicFragment:89（其 adapter 确有 header，+1 正确）与 SearchUserFragment:57（无 +1，正确），此处起点应为主 `lastSize`。
- 影响：每页第一个新条目未被通知，RecyclerView 显示空白/陈旧，需滚动触发 rebind 才恢复。
- 修复建议：改为 `notifyItemRangeInserted(lastSize, ...)`。
- 证据：`SearchVideoFragment.kt:55: videoCardAdapter!!.notifyItemRangeInserted(lastSize + 1, videoCardList.size - lastSize)`。

### M-2. `activity/search/SearchFragment.kt:115-123` —— goOnLoad 先 page++ 再发请求，快速滚动时分页请求并发、结果乱序
- 问题描述：`goOnLoad()` 先 `page++` 再 `listener!!.onLoad(page)`，仅有 100ms 时间戳防抖；`bottom` 标志只在响应后设置。快速滚动可并发触发多个 page 请求，晚发先至时列表顺序错乱/重复。
- 影响：搜索结果分页偶发重复或缺失条目。
- 修复建议：增加 isLoading 防重入标志（对齐 RefreshListActivity），响应按请求序号校验。
- 证据：`119: page++`、`120: listener!!.onLoad(page)`。

### M-3. `activity/settings/SettingSearchActivity.kt:59-69、74-86` —— 开关设置只在 onDestroy 落盘，异常退出/进程被杀即丢失
- 问题描述：三个搜索开关只在 onDestroy 一次性 save()；`resetToDefault()` 只改 UI 不保存。
- 影响：切换开关后 Home 退后台被系统回收，设置丢失；"重置"按钮表现"没反应"。
- 修复建议：OnCheckedChangeListener 内即时落盘，resetToDefault() 末尾补 save()。
- 证据：`74-80: save()` 仅在 onDestroy 调用。

### M-4. `activity/settings/AnnouncementsActivity.kt:27-30` —— 请求异常后刷新转圈永久不消失
- 问题描述：catch 分支只 report + Toast，未 `setRefreshing(false)`（基类创建时已 isRefreshing=true 且禁用下拉）。
- 影响：接口异常时转圈永转且无法下拉重试。
- 修复建议：catch 补 setRefreshing(false)。
- 证据：`27-30: } catch (e: Exception) { report(e); runOnUiThread { MsgUtil.showMsg(...) } }`。

### M-5. `activity/settings/TestActivity.kt:180、232-237` —— response.body 为 null 时提前 return，跳过恢复控件代码，界面永久禁用
- 问题描述：`val body = response.body ?: return@run` 直接退出后台 lambda，绕过其后"恢复按钮可用性"的 runOnUiThread；此前已把输入控件全部禁用。
- 影响：响应体 null（低概率）时猫娘聊天界面永久锁死。
- 修复建议：判空走 finally/提前恢复控件，或改为抛异常统一处理。
- 证据：`180: val body = response.body ?: return@run`。

### M-6. `activity/settings/TestActivity.kt:133-147、191` —— 第三方 API Key 明文存储并整条流式响应打入日志
- 问题描述：DeepSeek API Key 明文写 SharedPreferences（`dev_catgirl_apikey`），SSE 流每行 `Log.d("debug-deepseek", line)`。
- 影响：root/备份可读 key；对话全文进 logcat。
- 修复建议：至少改用 EncryptedSharedPreferences；日志截断或删除；该页仅 Debug 可达（现状可接受但仍属风险）。
- 证据：`138: putString("dev_catgirl_apikey", api_key)`、`191: Log.d("debug-deepseek", line)`。

### M-7. `activity/settings/login/QRLoginFragment.kt:216-243、211-214` —— 扫码轮询 Timer 不随生命周期暂停
- 问题描述：Timer 每秒轮询，只在 onDestroy/模式切换/成功时 cancel，无 onPause/onStop 处理；ViewPager 切到其他登录方式或退后台时轮询继续。
- 影响：后台持续网络轮询浪费流量电量。
- 修复建议：onPause cancel、onResume 按需重建轮询。
- 证据：`218: timer!!.schedule(object : TimerTask() {...}, 500, 1000)`。

### M-8. `activity/settings/login/QRLoginFragment.kt:374-401` —— WEB 扫码登录成功分支 JSON 解析异常导致登录态半保存
- 问题描述：code==0 分支先写 mid/csrf（374-375 行）再 `getJSONObject("data").getString("refresh_token")`（376 行，可能抛 JSONException，被外层捕获提示"无法获取二维码信息"），此时 mid/csrf 已写入但 saveCurrentAccount/跳转未执行，392 行还重复三次 `getJSONObject("data")`。
- 影响：接口结构微变即出现"半登录"脏状态，下次启动逻辑错乱。
- 修复建议：先整体解析并缓存 data，必填字段用 optXxx 兜底，失败统一回滚已写入 prefs。
- 证据：`374: putLong(mid, ...toLong())`、`376: putString(refresh_token, loginJson.getJSONObject("data").getString("refresh_token"))`。

### M-9. `activity/settings/UpdateActivity.kt:152-153` —— downloadUrl 可空，点击下载即 NPE 崩溃
- 问题描述：has_config 路径下 downloadUrl 来自 intent extra（可 null），`startDownload()` 直接 `updateConfig!!.downloadUrl!!`。
- 影响：调用方漏传 download_url 时点下载即崩溃。
- 修复建议：downloadUrl 判空提示并恢复按钮状态。
- 证据：`153: updateConfig!!.downloadUrl!!`。

### M-10. `activity/settings/login/CaptchaWebViewActivity.kt:241-247` —— onDestroy 直接 destroy() 未先从父布局移除 WebView
- 问题描述：WebView 仍挂接在视图层级时直接 destroy()，部分机型在随后的视图回收阶段抛异常。
- 修复建议：destroy 前 `(it.parent as? ViewGroup)?.removeView(it)`。
- 证据：`242-244: webView?.let { it.destroy(); webView = null }`。

### M-11. `activity/settings/login/CaptchaWebViewActivity.kt:133-134` —— gt/challenge 未转义直接拼接进 JS 字符串
- 问题描述：`"var gt = '" + mGt + "';"` 直接把接口返回值嵌入 HTML 脚本，值含单引号可破坏 JS 结构。
- 影响：上游返回异常字符时脚本注入/验证码白屏（与 C 组 WebView 过度开放叠加放大风险）。
- 修复建议：对拼接值做 JS 转义或 JSON 序列化注入。
- 证据：`133-134: "var gt = '" + mGt + "';\n" + "var challenge = '" + mChallenge + "';\n"`。

### M-12. `activity/settings/login/PasswordLoginFragment.kt:136-139`、`SMSLoginFragment.kt:160-163` —— 异步回调中 requireContext()/launch 无 Fragment 存活检查
- 问题描述：获取验证码完成后 `runOnUiThread { Intent(requireContext(), ...); captchaLauncher.launch(intent) }` 无 isAdded/lifecycle 检查。
- 影响：网络慢时用户退出登录页 → requireContext 抛 IllegalStateException 或 launch 抛异常崩溃。
- 修复建议：lambda 开头 `if (!isAdded) return@runOnUiThread`，launch 前检查生命周期状态。
- 证据：`136: val intent = Intent(requireContext(), CaptchaWebViewActivity::class.java)`。

### M-13. `activity/settings/login/PasswordLoginFragment.kt:164-165`、`QRLoginFragment.kt:163` —— 后台线程读写 UI 控件
- 问题描述：`doPasswordLogin` 在 CenterThreadPool（IO）内读 `usernameInput.text`；`refreshQrCode` 后台协程内 `qrImageView.isEnabled = false`。
- 影响：跨线程读写 View 状态，违反 Android 线程模型，用户输入时可读到中间态。
- 修复建议：进后台前先取局部变量；View 操作回主线程。
- 证据：`164: val username = usernameInput.text.toString().trim()`（在 run 块内）。

### M-14. `activity/settings/setup/SetupUIActivity.kt:36-47` —— 圆角开关立即持久化而边距仅在 save() 持久化，状态不一致
- 问题描述：勾选"圆角"立即 `putBoolean("player_ui_round", true)` 并改输入框文字为 5/3，但 paddingH/V 的 prefs 只在点"预览/确定"时写入；勾选后直接返回则 round=true 而 padding 仍旧值。
- 修复建议：开关变化时同步把 5/3 写入 prefs。
- 证据：`38-40: uiPaddingH.setText("5"); uiPaddingV.setText("3"); putBoolean("player_ui_round", true)`。

### M-15. `activity/settings/login/PasswordLoginFragment.kt:231-235`、`SMSLoginFragment.kt:282-286` —— 登录成功处理在主线程执行同步网络请求（ANR 风险）
- 问题描述：两个 Fragment 的登录成功处理整体包在 `runOnUiThread` 内，其中 `AccountManager.saveCurrentAccount()` 同步调 `UserInfoApi.getUserInfo(mid)`（两次 HTTP），`LoginApi.requestSSOs()` 对多个 SSO 域名发起 POST，全部在主线程执行。
- 影响：登录成功后主线程被多个同步 HTTP 阻塞，弱网下卡死/ANR。
- 修复建议：网络部分移出 runOnUiThread，仅 startActivity/finish 回主线程。
- 证据：`231: AccountManager.saveCurrentAccount()`、`235: LoginApi.requestSSOs()`（runOnUiThread 块内）。

### M-16. `activity/user/HistoryActivity.kt:34-73、81-101` —— 加载失败路径：首次失败无限转圈 + 分页失败后游标被重置导致重复请求
- 问题描述：`lastResult.code == 0` 分支内才 setRefreshing(false)，else 只弹消息 → 首次失败转圈永转；`continueLoading` 中 `lastResult = HistoryApi.getHistory(lastResult, list)` 无条件覆盖，失败返回的 ApiResult 游标归零（`ApiResult(JSONObject)` 不保留 offset），下次从头拉取。
- 影响：历史接口失败时卡加载态；偶发失败后恢复会重复第一页/无限重试。
- 修复建议：else 补 setRefreshing(false)；失败时保留旧游标或置 bottom。
- 证据：`73: } else MsgUtil.showMsg(lastResult.message)`（无 setRefreshing(false)）、`85: lastResult = HistoryApi.getHistory(lastResult, list)`。

### M-17. `activity/user/EditSignActivity.kt:115-125` —— 异常捕获过窄：非 IO/JSON 异常时提交按钮永久禁用
- 问题描述：catch 中只有 `e is IOException || e is JSONException` 才恢复 isSubmitting/submit 状态；其他运行时异常（如 result 为 null 的 NPE）静默穿过。
- 影响：点提交后按钮永久置灰、无提示、无法重试。
- 修复建议：catch 所有 Exception 统一恢复状态并提示。
- 证据：`116: if (e is IOException || e is JSONException) {`。

### M-18. `activity/user/WatchLaterActivity.kt:36-52` —— 删除回调异常捕获过窄，未捕获异常在协程中导致应用崩溃
- 问题描述：CenterThreadPool 基于 `CoroutineScope(Dispatchers.IO)` 无 CoroutineExceptionHandler；catch 只覆盖 IOException/JSONException，`videoCardList[position]` 越界等运行时异常直接走全局崩溃。
- 修复建议：改为 `catch (e: Exception)` 并恢复 longClickPosition。
- 证据：`38-52` catch 仅两类异常且 `printStackTrace()`。

### M-19. `activity/user/EditProfileActivity.kt:105-118` —— 头像大图无采样直接解码，OOM 崩溃风险
- 问题描述：`BitmapFactory.decodeStream(inputStream)` 未做 inSampleSize 采样，随后 `bitmap.compress(JPEG, 90, baos)` 再复制字节数组；现代手机照片（4800 万像素）解码约需 200MB。
- 影响：选高分辨率图片上传头像时进程 OOM（后台线程，无法 catch 恢复）。
- 修复建议：先 inJustDecodeBounds 读尺寸，按目标尺寸（如 1024px）计算 inSampleSize 后二次解码，或改用 ImageDecoder/Glide 采样。
- 证据：`105-118` 直接 decodeStream + compress。

### M-20. `activity/user/FollowUsersActivity.kt:105-138` —— 分组用户展开时递归拉取全部页，无生命周期/取消控制
- 问题描述：每页满 20 条时无条件递归请求下一页（`if (result == 0 && tagUsers.size == 20) loadMoreGroupUsers(...)`），一次性拉完整个分组；递归链不检查 Activity 状态。
- 影响：大分组连续多页请求，退出页面后请求照发；`groupAdapter!!` 对生命周期零容错。
- 修复建议：改为随滚动惰性分页，或加 Activity 存活判断与取消机制。
- 证据：`113-114: if (result == 0 && tagUsers.size == 20) { loadMoreGroupUsers(tagid, tagUsers.size) }`。

### M-21. `activity/vote/VoteInfoActivity.kt:95-98` —— 强制"恰好选满" choice_cnt 项，语义与模型注释相悖
- 问题描述：VoteInfo 注释 choice_cnt 为"最多选几项"，此处 `selectedOptions.size < info.choice_cnt` 就拦截，要求必须选满 N 项才可提交；"请选择 N 项"提示同理。
- 影响：用户无法提交少于上限的合法选择，多选投票功能受限。
- 修复建议：choice_cnt==1 单选；>1 只限制上限 `size > choice_cnt`，允许至少 1 项提交。
- 证据：`95: if (selectedOptions.size < info.choice_cnt) {`。

### M-22. `activity/video/local/LocalListActivity.kt:393-410` —— 多 P 视频更新弹幕所有分页都使用第一 P 的 cid
- 问题描述：注释写"使用cid列表（如果有的话）"，但 VideoMeta 只有单个 cid 字段，循环里每页都 `pagePlayerData.cid = meta.cid`。
- 影响：多 P 缓存视频第 2 页以后弹幕全部错误（下载重复弹幕）。
- 修复建议：VideoMeta 增加 cids 数组持久化，或按 cid 列表逐个请求。
- 证据：`399-403: pagePlayerData.aid = meta.aid; pagePlayerData.cid = meta.cid`。

### M-23. `activity/video/collection/CollectionInfoActivity.kt:47-66` —— CardAdapter 分支永不可达（死代码）+ collection!! 无判空
- 问题描述：Collection 模型 sections/cards 默认初始化为空列表，`analyzeUgcSeason` 只可能设 sections、cards 从不赋值 → `sections == null && cards != null` 恒假，CardAdapter 整段死代码；`collection` 为 null 时 `collection!!` 直接 NPE。
- 影响：无 collection 的 videoInfo 场景（缓存/接口异常）打开合集详情即崩溃；死代码掩盖兼容逻辑。
- 修复建议：按 sections 统一处理并 `collection ?: return`，删除死分支。
- 证据：`47: if (collection!!.sections == null && collection!!.cards != null) {`。

### M-24. `activity/video/series/SeriesInfoActivity.kt:35-37、139-150` —— 头部卡片数据 seriesIntro/seriesCover/seriesTotal 从未赋值（幻觉 UI）
- 问题描述：三个字段仅声明并在 adapter header 绑定中使用，全文件无任何赋值点；启动该页的 intent 只传 type/mid/sid/name，`getSeriesInfo` 返回的 pageInfo.total 也未被使用。
- 影响：合集详情页头部封面、简介、"共X"永远为空/占位。
- 修复建议：从 pageInfo/额外接口填充，或移除头部数据展示。
- 证据：`140: holder.playTimes.text = "共${activity.seriesTotal}"`（seriesTotal 恒 ""）。

### M-25. `activity/video/local/DownloadListActivity.kt:72-105` —— Timer 在后台线程创建，快速退出时永不 cancel（线程泄漏）
- 问题描述：Timer 在 CenterThreadPool.run 任务内创建；若 Activity 在任务执行前被销毁，onDestroy 的 `timer?.cancel()` 永不执行，Timer 线程（非 daemon）常驻并持续向已销毁页面投递 runnable；TimerTask 内还直接写 `adapter!!.lastSpeedStr`（timer 线程写、主线程 bind 读）。
- 影响：退出下载列表后 Timer 线程泄漏、反复刷新已销毁视图。
- 修复建议：Timer 改在主线程创建，或改用 Handler.postDelayed + onDestroy removeCallbacks。
- 证据：`72: CenterThreadPool.run {`、`76: timer = Timer()`。

### M-26. `activity/video/info/BangumiInfoFragment.kt:36、255-298` —— dialog 字段从不 dismiss，销毁后窗口泄漏
- 问题描述：getSectionChooseDialog()/getEposideChooseDialog() 把 AlertDialog 存入 dialog 字段并 show()，onDestroyView/onDestroy 无任何 dismiss。
- 影响：旋转/返回时对话框窗口泄漏（持有 Activity Context），反复操作累积泄漏。
- 修复建议：onDestroyView 中 `dialog?.dismiss(); dialog = null`。
- 证据：`272: dialog = builder.create()`。

### M-27. `activity/video/info/VideoInfoFragment.kt:226-234` —— epid 跳转在后台线程 startActivity 并 finish
- 问题描述：`CenterThreadPool.run { TerminalContext.getInstance().enterVideoDetailPage(context, ...); activity.finish() }` 在 IO 线程执行导航与 finish，context 取自 rootview.context，Fragment 销毁后为失效引用。
- 修复建议：`if (isAdded) requireActivity().runOnUiThread { ... }`。
- 证据：`228: CenterThreadPool.run {`、`231: val activity = activity ?: return@run`、`232: activity.finish()`。

### M-28. `activity/video/local/LocalListActivity.kt:366-419、438-469、612-615` —— readVideoMeta 文件 IO 在主线程执行
- 问题描述：updateDanmaku/switchQuality/viewVideoDetail 由 adapter 回调（主线程）直接调 `readVideoMeta` → `VideoMetaManager.readMeta` 读磁盘；同文件 loadData 却在后台线程做同样的事。
- 影响：点击"更新弹幕/切换清晰度/查看详情"时主线程读磁盘，明显卡顿/ANR。
- 修复建议：三个入口先 CenterThreadPool.run 读 meta，再回主线程。
- 证据：`612-615: private fun readVideoMeta(...) { return VideoMetaManager.readMeta(videoDir) }`。

### M-29. `activity/video/info/VideoInfoFragment.kt:76-83、113-121、85-111` —— ActivityResult 回调假设视图/数据已就绪，旋转恢复后崩溃
- 问题描述：favLauncher 回调直接 `fav.setImageResource(...)`（fav 为 lateinit，仅 initView 后赋值）；notificationPermissionLauncher 回调 `startDownloadFlow()` 内 `videoInfo!!`；writeDynamicLauncher 回调 `videoInfo!!.aid`。Activity 旋转重建时系统会把挂起的 ActivityResult 立即投递给新 Fragment 实例，此时 videoInfo 未加载、fav 未初始化。
- 修复建议：回调内判 `videoInfo != null` / `::fav.isInitialized`，未就绪时忽略或延迟处理。
- 证据：`79: fav.setImageResource(R.drawable.icon_fav_1)`（fav 为 lateinit）。

### M-30. `activity/video/local/LocalListActivity.kt:421-433` —— downloadDanmakuFile：catch 后原样 rethrow + response 未关闭
- 问题描述：`catch (e: Exception) { throw e }` 是无效包装（死代码）；`response.body()?.bytes()` 或解压抛异常时 response 不会关闭。
- 修复建议：删除空 rethrow，response 用 use/finally 关闭。
- 证据：`426-429` 区域 catch-{throw e} 写法。

### M-31. `activity/video/ShortVideoPlayerActivity.kt` 若干行（详见下）—— 播放器页面生命周期与回调问题
- 问题描述：
  - 91-106 行 `preloadManager.onItemsLoaded/onLoadError` 回调（经 mainHandler 投递）无 `isDestroyed` 判断，销毁后仍操作 loadingLayout/viewPager。
  - 585-588 行 `ShortVideoFeedApi.fetchVideoUrl` 完成后 `mainHandler.post { initPlayer(...) }`，Activity 已销毁时仍创建 IjkMediaPlayer（持有已销毁页面的 Surface/Context），造成播放器资源泄漏。
  - 520-525 行 hideVolumeRunnable、336-345 行 hideBottomRunnable 等 postDelayed 未在 onDestroy 统一 removeCallbacks（依赖 PageHolder.releasePlayer 逐项清理，遗漏即滞留）。
- 影响：快速退出短视频页后播放器/回调滞留，偶发崩溃与资源泄漏。
- 修复建议：回调统一加 `if (isDestroyed || isFinishing) return`；initPlayer 前检查；onDestroy 统一 removeCallbacks。
- 证据：`93: preloadManager.onItemsLoaded = { items ->`、`586: mainHandler.post { initPlayer(item, screenW, screenH) }`。

---

## 四、Low（风格 / 性能小问题）

### L-1. `activity/video/info/VideoInfoFragment.kt:499-545` —— "长按三连"交互几乎不可达且成功后回调线程错误（与 H-1 同源）
- 问题描述：`like.postDelayed(tripleActionRunnable, 2000)` 排定三连，但 layout_like 的 OnTouchListener 在 ACTION_UP/ACTION_CANCEL 无条件 `cancelTripleAction()`；长按抬起通常早于 2 秒，三连几乎总是被取消且无任何提示。
- 修复建议：确认设计意图（松手触发或长按进度反馈）。
- 证据：`532: like.postDelayed(tripleActionRunnable, 2000)`、`540-542: ACTION_UP → cancelTripleAction()`。

### L-2. `activity/video/info/VideoInfoFragment.kt:279、394` —— 死代码与冗余计数逻辑
- 问题描述：279 行 playerData 刚赋非空立即判 null（恒假）；394 行 `if (++coinAdd <= 2)` 先自增再比较，且 stats.coined 从未被解析（恒 0，VideoInfoApi 只赋值 coin/coin_limit），本地假计数跨端不同步。
- 修复建议：删除 279 行判空；从服务端读取 coined 或本地置顶上限；判断改 `if (coinAdd <= 2) { coinAdd++; ... }`。
- 证据：`277-279: playerData = videoInfo!!.toPlayerData(0); PlayerApi.getVideo(playerData!!, false); if (playerData == null) return@run`。

### L-3. `activity/video/info/VideoInfoActivity.kt:106-112` —— 失败后延迟 5 秒弹错误提示
- 问题描述：onFailure 用 `runOnUIThreadAfter(5L, TimeUnit.SECONDS)` 延迟弹 MsgUtil.err，用户已退出页面仍会弹出。
- 修复建议：立即提示并在回调检查生命周期。
- 证据：`109-111`。

### L-4. `activity/user/info/UserDynamicFragment.kt:56、99-104` —— requireActivity() 无 isAdded 判断 + 越界异常被空 catch 吞掉
- 问题描述：`runOnUiThread { ... requireActivity().finish() }` 无 isAdded 检查；`onDynamicRemove` 用 `catch (ignored: Throwable)` 吞掉全部异常。
- 修复建议：isAdded 判断；空 catch 至少打日志。
- 证据：`56: requireActivity().finish()`、`102: } catch (ignored: Throwable) {`。

### L-5. `activity/user/info/UserInfoActivity.kt:82-87` + `UserDynamicFragment.kt:99-104` —— onActivityResult 删除动态无边界检查
- 问题描述：`data.getIntExtra("position", 0) - 1` 可能为 -1 直接传给 removeAt（被空 catch 吞掉），删除后列表残留/删错条目；对照 DynamicActivity.kt:278 有边界保护。
- 修复建议：仿 DynamicActivity 加 `>= 0 && < size` 判断。
- 证据：`84: udFragment.onDynamicRemove(data.getIntExtra("position", 0) - 1)`。

### L-6. `activity/search/SearchActivity.kt:95、246 等` —— 裸 Thread 发起网络请求，未统一走 CenterThreadPool
- 问题描述：搜索建议/默认搜索词用 `Thread { ... }.start()`，生命周期无法取消、线程不可控。
- 修复建议：统一 CenterThreadPool，回调加 isFinishing 判断。
- 证据：`95: Thread {`、`246: Thread {`。

### L-7. `activity/settings/SettingGroupActivity.kt:249` —— 主题保存用 commit() 主线程同步写盘且与 onActivityResult 重复写入
- 修复建议：保留一次写入即可；同步写仅对主题项。
- 证据：`249: ...putString(SettingsKeys.THEME, newValue).commit()`。

### L-8. `activity/settings/SettingGroupActivity.kt:307-308` —— 构建下载分组时主线程执行文件 I/O（getVideoDownloadPath 内部可能建/删 .nomedia）
- 修复建议：移入后台线程或改为纯读取路径。
- 证据：`307: input("缓存路径", ..., FileUtil.getVideoDownloadPath().toString())`。

### L-9. `activity/settings/SettingsIndex.kt:93、97` —— 搜索索引项名与设置页实际项名不一致，跳转后高亮定位失效
- 问题描述："最大同时下载数"在下载分组不存在；"默认缓存质量"与实际项名"默认缓存画质"不一致；SettingGroupActivity 用 `indexOfFirst { it.name == highlight }` 定位，名不匹配则 index=-1 静默不滚动。
- 修复建议：核对并统一全部索引名。
- 证据：`93、97` 行 Entry 名。

### L-10. `activity/user/FollowingBangumisActivity.kt:29、52`、`activity/user/info/UserVideoFragment.kt:66`、`UserArticleFragment.kt:66` —— `result != -1` 恒真死分支
- 问题描述：相关 API 只返回 0/1（异常直接 throw），-1 分支永不执行。
- 修复建议：删除无效防御分支。
- 证据：`FollowingBangumisActivity.kt:29: if (result != -1) {`。

### L-11. `activity/user/favorite/FavoriteFolderEditActivity.kt:96-106` —— Handler postDelayed 未移除，销毁后 Activity 被短暂隐式持有
- 修复建议：Handler 存成员，onDestroy removeCallbacksAndMessages。
- 证据：`100-101: val handler = Handler(); handler.postDelayed({ deleteClickCount = 0 }, 3000)`。

### L-12. `activity/user/MySpaceActivity.kt:132-148` —— 退出登录二次确认状态跨页面往返保持
- 修复建议：onResume 重置 confirmLogout 或 3 秒自动复位。
- 证据：`144-147` 无任何复位路径。

### L-13. 大量 `Log.e("debug", ...)` / `Log.e("debug-*", ...)` 调试日志残留（release 不剥离）
- 涉及：SearchActivity.kt:85、SearchVideoFragment.kt:43、SearchUserFragment.kt:46、SearchLiveFragment.kt:48、SearchArticleFragment.kt:42、PopularActivity.kt:36/51/68、PreciousActivity.kt:36/51/68、RankingActivity.kt:33/48/64、RecommendActivity.kt:29/45/60、HotSearchActivity（无）、MySpaceActivity.kt:54、SettingInfoActivity.kt:18、SettingPrefActivity.kt:19、SettingRepliesActivity.kt:18、AboutActivity.kt:38、SettingPlayerChooseActivity.kt:103、LoginActivity.kt:18、UserVideoFragment.kt:67、UserArticleFragment.kt:67、UserDynamicFragment.kt:60/65、VideoRcmdFragment.kt:36、DownloadListActivity.kt:129/143/156/212、VideoInfoFragment.kt:438 等。
- 修复建议：删除或统一 Logu.d；release 配置 -assumenosideeffects。

---

## 五、Info（备注）

### I-1. `activity/video/info/VideoInfoActivity.kt:51`、`activity/user/info/UserInfoActivity.kt:28` —— Intent extra 缺失时默认值使用梗值 114514
- 影响：调用方漏传 aid/mid 时静默进入"视频 114514 / 用户 114514"页面而非提示，番剧入口还会打开错误番剧。
- 修复建议：默认 -1 并 finish/提示。

### I-2. `activity/settings/login/QRLoginFragment.kt:256、355` —— Logu.v 打印完整登录轮询响应体（含 refresh_token/跳转 url）
- 说明：Logu 的 verbose 默认关闭，但运行时一旦开启即全量泄露；建议删除或只打印 code。
- 证据：`256: Logu.v("tv_login_state", str)`、`355: Logu.v("login_state", str)`。

### I-3. `activity/settings/login/SpecialLoginActivity.kt:80-86` —— 凭证明文存 SharedPreferences / 导出模式一键复制剪贴板
- 说明：项目既有惯例（无 EncryptedSharedPreferences）；导出复制到剪贴板后其他应用可读。建议后续迁移 EncryptedSharedPreferences 并加风险提示。

### I-4. `activity/settings/setup/IntroductionActivity.kt:28-31`、`activity/settings/SettingGroupActivity.kt:137-144` —— `Build.VERSION.SDK_INT >= 19` 恒真（minSdk 24），else 分支死代码
- 说明：疑似从旧项目照搬的幻觉分支；SpecialLoginActivity 在引导流程不可达。
- 修复建议：删除条件直接跳 LoginActivity。

### I-5. `activity/settings/UpdateHistoryActivity.kt:52-60` —— 空数据兜底判断逻辑错误
- 说明：无任何"## 日期"标题行时才显示"暂无历史更新日志"，但无日期头的普通行会被静默丢弃，存在"有数据被丢弃仍显示暂无"。
- 修复建议：记录 addedCount 作为兜底条件。

### I-6. `activity/settings/SearchSortActivity.kt:73-88` —— 拖拽排序保存依赖 onPause 兜底，onItemClick 空实现
- 说明：onChanged 只在 notifyDataSetChanged 触发，拖拽移动走 notifyItemMoved 不触发保存，实际靠 onPause；空 onItemClick 属无效代码。

### I-7. `activity/user/favorite/FavoriteVideoListActivity.kt:37-39` —— `name!!` 强解包依赖所有调用方传参
- 说明：当前两个调用方均传了 name，暂无崩溃；建议改 `name ?: ""` 防未来漏传。
- 证据：`39: setPageName(name!!)`。

### I-8. `activity/user/info/UserVideoFragment/UserArticleFragment/UserDynamicFragment/UserFavoriteFragment` —— 四个用户信息 Fragment 均未启用下拉刷新（RefreshListFragment 默认禁用）
- 说明：只能退出重进，功能缺失而非崩溃；建议补 setOnRefreshListener。

---

## 六、幻觉功能 / 死代码专项

（除上文 M-23、M-24、L-10、I-4 外，汇总如下）

| 位置 | 问题 | 判定 |
|---|---|---|
| CollectionInfoActivity.kt:47-66 | CardAdapter 分支永不可达（cards 从不赋值） | 死代码 |
| SeriesInfoActivity.kt:35-37 | 头部字段从未赋值，UI 恒为空 | 幻觉 UI |
| FollowingBangumisActivity.kt:29/52、UserVideoFragment.kt:66、UserArticleFragment.kt:66 | `result != -1` 恒真 | 无效防御 |
| VideoInfoFragment.kt:279 | playerData 判空恒假 | 死代码 |
| VideoInfoFragment.kt:499-545 | 三连交互被 ACTION_UP 取消，功能几乎不可达 | 幻觉功能 |
| LocalListActivity.kt:426-429 | catch { throw e } 无效重抛 | 死代码 |
| IntroductionActivity.kt:28-31、SettingGroupActivity.kt:137-144 | SDK_INT>=19 恒真分支 | 死代码 |
| VoteInfoActivity.kt:95-98 | 强制选满与"最多选 N 项"模型语义矛盾 | 逻辑幻觉 |

**正面结论**：全部被引用的 API 方法、Adapter、模型字段、设置键经 grep 验证**均真实存在**，未发现调用不存在接口的编译级幻觉代码（幻觉集中在逻辑分支与数据源缺失层面）。

---

## 七、跨文件共性问题（系统性）

1. **分页 isLoading 卡死**（H-2）：5 个页面漏调 `onLoadComplete()`——建议在 RefreshListActivity 基类下沉复位逻辑，杜绝子类遗漏。
2. **异步回调无生命周期保护**（M-4/5/12、L-6、AccountSwitchActivity H-9、ShortVideoPlayerActivity M-31 等）：CenterThreadPool 任务不可取消、销毁后回调继续执行——建议统一回调入口 `if (isDestroyed || isFinishing) return`，长任务用可取消的协程 Job。
3. **日志规范**：`Log.e("debug", ...)` 数十处 + 2 处敏感凭证日志（C-1）+ 2 处完整响应体日志（I-2）——建议统一 Logu + debug 门控 + R8 剥离。
4. **后台线程改 UI**：VideoInfoFragment 三连（H-1）、LocalPageChooseActivity 跨线程改列表（C-4）、PasswordLoginFragment/QRLoginFragment 后台读控件（M-13）、VideoInfoActivity EventBus ASYNC（H-4）。
5. **notify 与数据不同步**：Search 三 Fragment off-by-one（M-1）、UserSeriesActivity 幽灵插入（H-3）。
6. **窄 catch 导致状态卡死**：EditSignActivity（M-17）、WatchLaterActivity（M-18）、TestActivity（M-5）、Popular/Precious（H-13）、AnnouncementsActivity（M-4）、HistoryActivity（M-16）。

---

## 八、修复优先级建议

1. **立即（安全）**：C-1 凭证日志、H-10 WebView 过度开放、H-11 短信无限重发。
2. **立即（崩溃）**：C-2/C-3 数字解析崩溃、C-4 LocalPageChooseActivity、C-5 虚拟合集越界、H-1 三连后台线程改 UI、H-3 UserSeriesActivity 幽灵插入、H-4/H-5 VideoInfoActivity。
3. **尽快（功能）**：H-2 分页卡死 ×5、H-6 SettingSearchActivity lateinit、H-7 签名覆盖、H-8 "加载失败"昵称、H-12 删除账号不清登录态、H-13 失败卡死 ×2。
4. **排期（健壮性）**：M 系列资源/生命周期问题。
5. **清理**：L-13 日志残留、I-4/I-6 死代码。
