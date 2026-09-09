# ReBiliClient 修复进度报告

> 更新日期：2026-09-07
> 基线：`docs/review/00-summary.md`（基于 26.08.27 快照，共 286 个问题）
> 状态：Critical 抽查项已全部确认/修复，High/Medium 待继续

---

## 一、修复总览

| 严重度 | 基线数量 | 已确认修复 | 待处理 |
|---|---|---|---|
| Critical | 23 | 17 | 剩余 6 待排查 |
| High | 52 | 4（分页 2 + 菜单键/Cookie 锁 2） | 48 待处理 |
| Medium | 105 | 0 | 105 待处理 |
| Low/Info | 106 | 0 | 106 待处理 |

---

## 二、已修复问题明细

### 第一轮（构建前修复）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `RefreshListActivity.kt` | `isLoading` 加载成功后永不复位，导致 10+ 页面翻页卡死 | `setRefreshing(false)` 时复位 `isLoading` |
| `RefreshMainActivity.kt` | `goOnLoad()` 未置成员 `isRefreshing`，滚动持续触发并发分页 | `goOnLoad()` 同步置 `isRefreshing = true` |

### 第二轮（审查后修复）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `QRLoginFragment.kt:309,310,383,384` | access_token / refresh_token / 完整 Cookie 明文打 logcat | 删除 4 行敏感日志 |
| `CaptchaWebViewActivity.kt:93-112` | WebView 开启全部危险开关 + JS 桥 | 关闭文件访问 4 项开关，混合内容改 NEVER_ALLOW |
| `LocalListActivity.kt:513` | 虚拟合集用全局索引访问过滤列表导致越界 | 改用 `videoList[startVideoIdx]` + 边界检查 |
| `VideoInfoFragment.kt:522-524` | 三连成功回调在 IO 线程 setImageResource | 包裹 `runOnUiThread { }` |

### 第三轮（剩余 Critical 排查）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `PrivateMsgActivity.kt:139` | 私信 JSON 注入（用户输入直接拼 JSON 字符串） | 改用 `JSONObject().put("content", content)` 安全构造 |
| `PrivateMsgActivity.kt:200` | 空会话列表 `list[list.size - 1]` 越界崩溃 | 加 `if (list.isEmpty()) return` 防护 |
| `NetWorkUtil.java:96` | 相对路径 Location 时 `getScheme()` NPE | scheme/host 判空后再比较 |
| `NetWorkUtil.java:100-109` | 手动重定向未 close 原响应导致连接泄漏 | 重定向前 `response.close()` |

### 第四轮（26.09.08 架构通读后修复）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `PrivateMsgApi.java:159` | `!has && isNull` 恒等于 `!has`，account_info 为 null 的普通会话被误过滤 | 改用 `isNull`；解析抽成 `parseSessionsList()` 纯函数 + 新增 `PrivateMsgApiTest`（6 例） |
| `InstanceActivity.kt:32` | MENU 键打开菜单后继续走 super，被 BaseActivity 又 finish 当前页 | 消费按键 `return true` |
| `InstanceActivity.kt:16` | `from` 参数判的是新建空 Intent，恒 false | 改判 `getIntent().getStringExtra("from")` |
| `NetWorkUtil.java:403` | `saveCookiesFromResponse` 无锁，Cookie 并发丢失 | 抽 `saveCookiesLocked()` 纳入 `NetWorkUtil.class` 锁 |
| `ConfInfoApi.java:41-43` | WBI 缓存三字段非原子写 | 合并为不可变 `WbiCache`，整体替换 |
| `MsgUtil.java:89-94` | sticky SnackEvent 显示后不移除，同页弹两次 | 显示分支同时 `removeStickyEvent` |
| `TerminalContext.java:118-133` | 视频缓存 aid/bvid 键不一致，bvid 路径永远 miss | 新增 `cacheVideo()` 双写；LruCache 10→20 |
| `ToolsUtilTest.kt:33` | 期望值笔误（`0x00123456` 剥离 alpha 写成 `0x000000`） | 改为 `0x123456` |

> 验证：`:app:testDebugUnitTest` 38 个测试全绿 + `:app:assembleDebug` 通过。

### 第五轮（登录页二维码缩放 bug）

| 文件 | 问题 | 修复方式 |
|---|---|---|
| `res/layout/fragment_qr_login.xml:34-53` | 二维码卡片 `layout_height="wrap_content"` 且无底部约束，改宽度后高度不跟着变（配合 `adjustViewBounds`+`scaleType=fitXY` 甚至可能整体尺寸不变） | 加 `app:layout_constraintDimensionRatio="1:1"`，ImageView 改 `match_parent` + `fitCenter` |
| `QRLoginFragment.kt:113-148` | 点二维码只改 `setGuidelinePercent` 不生效：Guideline 自身 `onMeasure` 恒 `setMeasuredDimension(0,0)`、尺寸不参与布局变化，外层 ConstraintLayout 又是 `wrap_content`，不触发父级重新测量；且 `findViewById` 用非空 `view` 但无判空、Toast 放在分支末尾，异常时既不变大也不提示 | 改 percent 后显式 `view?.requestLayout()`；Guideline 判空早退；提示文案统一在末尾触发 |

> 根因用 `constraintlayout-2.1.4.aar` 内 `Guideline.class` 字节码核实：`setGuidelinePercent` 仅写 `guidePercent` 字段并 `setLayoutParams`，`onMeasure` 第 2315 字节处为 `setMeasuredDimension(0,0)`。

---

## 三、审查前已修复（本次核查确认，无需改动）

| 文件 | 问题 | 防御措施 |
|---|---|---|
| `GetIntentActivity.kt` | 外部 Intent 直接 `!!`/`toLong()` 崩溃 | `toLongOrNull()` + `getLongExtra` 默认值 |
| `ImageViewerActivity.kt:31` | 空 Intent 崩溃 | `isNullOrEmpty()` 检查 |
| `CookieRefreshApi.java:99` | parseLong 空值崩溃 | 空值回退旧 mid |
| `FavoriteApi.java:285` | 未登录收藏 substring 越界 | 长度检查 |
| `LikeCoinFavApi.java:48` | 同上（重复实现） | 已修复 |
| `Reply.java:75` | location substring(5) 越界 | `length() > 5` 判断 |
| `OpusParagraph.java:98-100` | 空 blockquote setSpan 越界 | 长度检查 |
| `PlayerActivity.kt:1496` | onDestroy 提前 return 跳过清理 | 无条件清理 |
| `UpdateManager.kt:141-158` | 断点续传 200 响应追加损坏 | 丢弃旧残片从头写 |
| `LocalPageChooseActivity.kt:78-97` | 后台线程改列表 + 主线程 notify | 分离数据操作与 UI notify |
| `ToolsUtil.java:71` | 弹幕颜色错误（字符串拼接而非 RGB888） | `color & 0xFFFFFF` |
| `DanmakuApi.java:31,43` | 弹幕内容未 URL 编码 | `URLEncoder.encode(msg, "UTF-8")` |
| `PrivateMsgApi.java:219` | 私信内容未 URL 编码 | `URLEncoder.encode(content, "UTF-8")` |
| `ReplyApi.java:153,231,232` | 评论内容未 URL 编码 | `URLEncoder.encode(text/pictures, "UTF-8")` |

---

## 四、构建验证状态

| 时间 | 任务 | 结果 |
|---|---|---|
| 2026-09-07 15:45 | `:app:assembleDebug` | ✅ 成功（产出 4 个 APK） |
| 2026-09-07 16:01 | `:app:assembleDebug`（第二轮修复后） | ✅ BUILD SUCCESSFUL in 36s |
| 2026-09-07 16:20 | `:app:assembleDebug`（第三轮修复后） | ✅ BUILD SUCCESSFUL in 37s |
| 2026-09-07 16:45 | `:app:assembleRelease`（R8 压缩 + 签名） | ✅ BUILD SUCCESSFUL |

> 第二轮修复的 4 个文件（QRLoginFragment/CaptchaWebViewActivity/LocalListActivity/VideoInfoFragment）已重新编译验证通过。APK 时间戳更新至 16:01:21，universal 包 31.04 MB。
> 第三轮修复的 2 个文件（PrivateMsgActivity/NetWorkUtil）已重新编译验证通过。构建仅有 1 个 Hilt 处理选项无关警告，不影响产物。
> Release 构建成功：universal 22.94 MB / arm64-v8a 9.87 MB / armeabi-v7a 8.44 MB / x86 10.86 MB。

### 设备安装

| 操作 | 结果 |
|---|---|
| 卸载旧版（签名不一致） | ✅ Success |
| 安装 debug universal 版 | ✅ Success（设备 10AC9S2M4L000QL） |

---

## 五、待处理问题（按优先级）

### P0 剩余 Critical（待排查）

- [x] 私信会话列表逻辑写反：`PrivateMsgApi.java:157`（26.09.08 已修，第四轮）
- [ ] 下载并发竞态：`DownloadService.start()` 无同步（改动面大，需单独评估）
- [ ] 分片 join 超时并发写：`DownloadService`（同上）

### P0 功能正确性（High）

- [ ] 播放器长按后手势全失效：`PlayerControlDelegate.kt:282`
- [x] Menu 键同时打开菜单并关闭当前页（26.09.08 已修，第四轮）
- [ ] WebView 输入校验缺失：`SetupUIActivity.kt:79,86,92`

### P1 架构清理

- [ ] 删除 `BiliTerminalApp.kt` 与 Hilt 死依赖（或真正启用）
- [ ] 清空 23 个空目录（di/data/network/ui 等）
- [ ] 删除幻觉方法（`SharedPreferencesUtil.beginBatchEdit` 等）
- [x] 统一 Cookie 写入锁（26.09.08 已修，第四轮）

### P1 安全

- [ ] AppInfoApi 升级 https（4 处明文 HTTP）
- [ ] 敏感日志清理（PrivateMsgApi/NetWorkUtil.post）
- [ ] 更新 APK 签名/哈希校验
- [ ] Manifest 权限收敛

### P2 工程化

- [ ] 拆分 PlayerActivity（3090 行）
- [ ] 拆分 DownloadService（65KB）
- [ ] 补单元测试（当前 7 个测试文件覆盖 363 个源文件）

---

## 六、下一步建议

1. 立即重新构建验证第二轮 4 处修改
2. 继续排查剩余 Critical（私信/下载/网络）
3. 转向 High 功能正确性修复（弹幕颜色/URL 编码收益高、改动小）
4. 架构清理与安全加固（P1）可安排到后续迭代
