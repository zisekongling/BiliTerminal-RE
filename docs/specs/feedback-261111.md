# 未处理反馈需求整理（26.11.11 批次，36 条）

> 本文是**交接物**：把控制台里 36 条待处理反馈逐条核实到代码行，并把开发者 26.11.11 的拍板写进「本批决定」，接手智能体按本文实现即可，不需要再回头问一遍。
> 来源：ReBili 后台 `feedback_list(pending)`，共 36 条，截至 26.11.11 07:20。
> 约束（开发者当场确认）：**36 条全部入文档**、**本轮不把任何一条标 handled**、**先不提交**（工作区现有下载相关未提交改动保持原样）。
> **26.11.11 已派工落地**（开发者：「你派子代理干活吧」，推翻此前「只出文档不派工」）：P0~P2 各条已按本文实现，逐条落点、L2 验收与遗留项见 [§9 实现落点](#9-实现落点261111子代理落地)；第 3~7 章的「本批决定」仍是权威口径，§9 只补「实际改到哪一行」。

## 0. 怎么用这份文档

- 每条包含：来源设备/版本 → 用户原话要点 → **代码核实**（`文件:行号`，全部为 26.11.11 在 `main`（HEAD `4fdc7078`）上读到的现状）→ **本批决定**（开发者答复，不再改）→ 实现要点 → 验收 → 影响文件 → 阻塞。
- 状态图例：
  - `已修·待验证`：代码已在 `main` 上修好，只差真机验收。
  - `已修·待合并`：修复在未合并分支/PR 里。
  - `待实现`：已拍板要做，还没动。
  - `调研`：先查清事实再决定，本轮不改代码。
  - `暂缓`：方向定了但顺序在后，或有前置依赖。
  - `不做`：已回复用户/驳回，保留记录。
- 优先级为本文**建议值**（P0 用户可感知的卡死与退不出 → P3 新功能 → P4 暂缓/不做），开发者未逐条纠正，接手时可按依赖顺序调整。
- 日志类内容只作分析，不原样外传（含机型、账号、设备指纹）。

## 1. 总览

| 编号 | 分类 | 标题 | 状态 | 优先级 | 归属 |
| --- | --- | --- | --- | --- | --- |
| #153 | bug | 长按顶栏在不支持页面也震动 | 已修·待合并（PR #27） | P1 | 交互 |
| #136 | bug | 下载错误后再也下不了 | 已修·待验证（599611e9） | P0 | 下载 |
| #132 | bug | 关闭表冠适配后详情页滑动极慢 | 已修·待验证（e3f42254） | P1 | 交互 |
| #117 | bug | 小米手表1 播放卡死退不出 | 待实现 | P0 | 播放器 |
| #135 | bug | 侧滑返回退不出视频 | 待实现（与 #139② 同开关） | P0 | 播放器 |
| #139 | suggestion | N3 四条建议（仅②入批） | 待实现（②） | P0 | 播放器 |
| #87 / #90 | bug | 分片视频下载残片/失败 | 待实现 | P0 | 下载 |
| #150 | bug | 评论回复与点赞不显示 | 待实现 | P1 | 消息 |
| #143 | bug | 弹幕跳转/倍速后变少 | 待实现 | P1 | 弹幕 |
| #148 | content | 搜索结果重复 | 待实现 | P1 | 搜索 |
| #142 | bug | cv 文章看不到文字 | 待实现 | P1 | 专栏 |
| #151 / #152 | bug | 合集不记录/不回跳上次位置 | 待实现 | P1 | 合集 |
| #125 | bug | 选择窗口按钮点击错位 | 待实现 | P2 | 基础控件 |
| #140 | suggestion | 长按阈值偏短 + 多选菜单样式 | 待实现（样式；阈值见 PR #27 核对） | P2 | 列表 |
| #121 | suggestion | 私信拦截提示可关闭 | 待实现 | P2 | 私信 |
| #123 | suggestion | 一键清空搜索记录 | 待实现 | P2 | 搜索 |
| #129 | suggestion | 短视频双指拖动放大 | 待实现 | P2 | 短视频 |
| #94 | suggestion | 缓存/下载页全选 | 待实现 | P2 | 缓存 |
| #156 | bug | 直播弹幕无法发送 | 待实现（新功能） | P3 | 直播 |
| #133 | suggestion | 视频转发到私信 | 待实现（新功能） | P3 | 私信 |
| #120 | suggestion | 顶栏全局重构 | 待实现（新功能） | P3 | 顶栏 |
| #155 | bug | 音量调到最大也很小 | 调研 | P2 | 播放器 |
| #128 / #102 | bug | 缩放有时失效 | 暂缓（待录屏复现） | P2 | 播放器 |
| #149 | bug | 继续播放入口消失 | 暂缓 | P2 | 菜单 |
| #147 | suggestion | 番剧缓存 | 暂缓（等换源播放） | P3 | 下载 |
| #137 | suggestion | 动态页改点头像展开 | 不做（本批） | P4 | 动态 |
| #134 | suggestion | 详情/播放页一键换官方风 | 不做（本批） | P4 | 外观 |
| #119 | suggestion | 看图页背景透出壁纸 | 不做（本批） | P4 | 图片 |
| #115 | suggestion | 长按推荐改「不感兴趣」 | 不做（本批） | P4 | 推荐 |
| #141 | suggestion | 分享视频链接 | 不做（能力已有） | P4 | 分享 |
| #127 | bug | 内容摘要用不了 | 不做（数据侧无摘要） | P4 | 详情 |
| #154 | bug | 视频网络错误卡住 | 不做（用户网络） | P4 | 播放器 |
| #122 | suggestion | 假计算器兜底入口 | 不做（私有功能边界） | P4 | 私有 |

---

## 2. P0：播放页退不出链（#117 / #135 / #139② 同源）

三条反馈指到同一处代码，**建议一次改完**，否则会出现「修了返回键又被滑动手势吞掉」的反复。

### #117 小米手表1 播放中卡死、退不出，视频还在跑

- 来源：Xiaomi `baiji`，26.11.07，sdk 28。用户补充：**只有横屏播放才出现**，且「内测版似乎无法横屏播放」。
- 代码核实：
  - 三道拦截：`activity/player/PlayerActivity.kt:858-866`（`interceptLockedExit()` → `if (swipeSeekEnabled) return` → `BACK_DISABLE`）、`:6938-6945`（`finish()` 前）、`:1858-1862` + `activity/player/PlayerLockPolicy.kt:69 shouldSwallowExit`。
  - 锁定按钮只浮现 3s：`activity/player/PlayerLockPolicy.kt:20 ICON_VISIBLE_MS=3000`；`LOCKED_HIDDEN` 时 GONE（`PlayerActivity.kt:1821`），只能点画面再唤出（`:1784-1786`）→ 用户观感就是「卡死且在播」。
  - 主线程隐患：`finish()` 在 `PlayerActivity.kt:6955` 直接读 `ijkPlayer!!.currentPosition`，同文件 `:2699-2701` 注释自认「可能长时间不返回、放退出路径上就是 ANR」；`onDestroy` `:3936-3938` 主线程 release。
  - 全仓无「诊断播放」功能（0 命中），即 26.11.07 那版内测功能已不在主线。
- 本批决定：**卡死排 P0**；「主线程反人类调用先修」；返回键走**开关控制**（见 #139②），不做无条件放行；横屏播放问题一并修。
- 实现要点：
  1. `currentPosition` / `release` 移出主线程，加超时与兜底（超时直接 finish，不等 ijk）。
  2. 新增设置项（建议 key `PLAYER_BACK_KEY_EXIT`，落在 `util/SettingsKeys.kt`），默认**开**（即放行返回键退出），关掉回到现状。
  3. 锁定态仍保留拦截，但锁定按钮常驻或加长显示时长，并给出退出提示。
- 验收：L2 人工——锁屏播放中按返回键能退出；退出过程不 ANR（`adb shell dumpsys activity` 无 ANR 记录）；横屏播放可复现路径见下方阻塞项。
- 影响文件：`activity/player/PlayerActivity.kt`、`activity/player/PlayerLockPolicy.kt`、`util/SettingsKeys.kt`、`activity/settings/SettingPrefActivity.kt`。
- 阻塞：**横屏**这条未核实。可查的相关设置项：`util/SettingsKeys.kt:317 PLAYER_DEFAULT_ORIENTATION`（`:325 LANDSCAPE` / `:327 PORTRAIT` / `:329 AUTO`）、`:272 PLAYER_UI_SHOW_ROTATE_BTN`、`:158 DEV_PLAYER_ROTATE_SOFTWARE`、`:196 PLAYER_DOUBLETAP_RESTORE_SCREEN`；`:201` 注明旧「默认横屏」开关已被 `PLAYER_DEFAULT_ORIENTATION` 取代。接手方需先确认「内测版无法横屏」是设置项默认为竖屏还是旋转逻辑回退。

### #135 华强北手表侧边左滑返回退不出视频

- 来源：`aqsh_watch` `dove_evb4`，26.11.09。日志只有第三方更新检查 SSLException（与问题无关）。用户后来说：「可以加个修改说明……也许可以 vibe 一个开关加上去」。
- 代码核实：`activity/player/PlayerActivity.kt:1027-1038` 注释「无条件关掉系统窗口层右滑返回」+ `:1038 setTheme(... noSwipe = true)`，点名 issue #1；退出只剩顶栏（`:4286`）与返回键；锁定态 `:6942-6945` 连 `finish` 都拦。「放大后拖动」是另一条链路：`:1490-1508` + `activity/player/PlayerGesturePolicies.kt`。当前无独立开关。
- 本批决定：**加开关**，与 #139② 共用同一个「播放页侧滑/返回键退出」开关，默认开。
- 实现要点：`noSwipe` 主题改为按开关取值；开关关闭时恢复旧行为（放大后可拖动、右滑不退出）。
- 验收：L2——开关开：右滑/返回键都能退出；开关关：放大后仍可拖动，右滑不退出。
- 影响文件：同 #117。

### #139② 外部返回键（侧键/表冠返回）在播放页失效

- 来源：`BiDeZhi` `uws6152_1h10_go`（用户 N3，住校高中生，一轮写了四条，本批只收第 2 条）。
- 代码核实：`activity/player/PlayerActivity.kt:863 if (swipeSeekEnabled) return` —— 开了「左右滑动控制进度」就整条吞掉按键，注释自陈含蓝牙/表冠/系统返回；`:864` 再叠 `BACK_DISABLE`（`util/SettingsKeys.kt:126`，设置项在 `activity/settings/SettingPrefActivity.kt:95`）；`onKeyDown` `:4456-4479` 不处理 BACK。
- 本批决定：**做②，且改成开关控制**；①②③④ 其余三条本批不做（①进度条热区 26.11.08 已主动回退，③手表模式、④播放页内弹幕设置均不做）。
- 验收：开「左右滑动控制进度」时按外部返回键能退出播放页。
- 影响文件：`activity/player/PlayerActivity.kt`、`util/SettingsKeys.kt`。

---

## 3. 已修·待验证 / 待合并（3 条，不需要再写代码）

### #153 长按标题栏在不支持该功能的页面也震动

- 来源：XTC `msm8909w_i18`，26.11.09。
- 根因（已验证）：`activity/base/BaseActivity.kt:452 onStart → setupTopbarLongPressToHome()`（实现 `473-497`），`:491 if (!TopbarLongPressPrefs.isEnabledForNormalPages()) return@setOnLongClickListener true`；`activity/video/info/VideoInfoActivity.kt:207 handlesTopbarLongPress()=true`、`209-220` 同款。全仓 `Vibrator|vibrate|[Hh]aptic` **0 命中**，即震动不是 App 主动触发的——`View.performLongClickInternal` 在 `OnLongClickListener` 返回 `true` 后会调 `performHapticFeedback(LONG_PRESS)`，而两处为了「吃掉手势、不回落成单击」必须返回 `true`，于是必然震一次。
- 修复：提交 `941acb1a fix(ui): 长按顶栏在模式不适用的页面不再震动`，在分支 `origin/rebili/fix-titlebar-longpress-vibration-5fd2df6020df`（PR #27），改动 `activity/base/BaseActivity.kt`(+26)、`activity/video/info/VideoInfoActivity.kt`(+2)、`util/TopbarLongPressPrefs.kt`(+5)、`docs/review/fix-progress.md`(+1)。**该提交不在 `main` 上**（`git branch --contains` 为空）。
- 待办：合并 PR #27 → 真机验收「不支持长按退出的页面长按不震、支持的长按仍要退出」。
- 注：控制台已自动回复过（announcement #253–#256），用户知道「PR 已就绪等合并」。

### #136 下载出错后再也下不了

- 来源：OPPO `OW20W2`，26.11.09。日志原文：`DownloadService>分片 0（0-2440726）3 次尝试均失败：unexpected end of stream`、`DownloadService>下载未完成：video_new.mp4 524288/7322181`。
- 已修（`599611e9`，26.11.09）：失败收尾清三样——`service/DownloadService.kt:674-677` 置 `state="error"` 后 `cleanFailedResidue(...)`；策略在 `service/download/DownloadResiduePolicy.kt`（有 `video.mp4`/`audio.m4a` 或 `.bak` → 只清临时；纯自有产物 → 整目录删；其他 → 只清临时）；临时清理 `util/FileUtil.java:163-190`。重新入队靠删行：`service/DownloadService.kt:238-249`（单 P）/`:299-309`（多 P）。两条兜底：`cleanupFailedResidues()` `:407-414`（进页触发 `activity/video/local/DownloadListActivity.kt:87`）、`recoverStuckSections()` `:683-701`。
- **遗留风险（已验证）**：入口拦截判据没改，仍是「目录 / `.DOWNLOADING` 在不在」——`activity/video/info/VideoInfoFragment.kt:857-858`、`activity/video/MultiPageActivity.kt:52`；清理没覆盖的失败（进程被杀）仍可能提示「已在下载队列」，只是 DB 侧能自愈。**断点续传未做**：`service/DownloadService.kt:1254-1264 resetFile()` = `file.delete() && file.createNewFile()`（调用点 `:1287 downFileNormal`、`:1440 downFileSpeed`），分片写前 `:1582-1584 raf.setLength(totalSize)` → 失败/暂停后整文件从 0 重下；分片校验不过还会退回单连接再整文件重下（`:1675-1682`）。
- 待办：真机按 `docs/…1109.md` H02 验收；断点续传另开一单（不在本批）。
- 注意：工作区现有未提交改动（`service/DownloadService.kt`、`service/download/DownloadProgressStore.kt`）是「状态文案串台」修复（`reportFileProgress` 的 `state` 参数改名 `fallbackState`，改由 `downloadProgressMap[id]?.state ?: fallbackState` 取），**开发者要求先不提交**，接手方不要顺手 commit。

### #132 关闭表冠适配后详情页滑动速率极小

- 来源：Xiaomi `grasslte`（watch5），sdk 34。日志：`RotaryEncoder>兜底：滚动成功 … dy=28 target=RotaryRecyclerView`、`RotaryEncoder>系统优先：框架已滚动，吞掉事件 … view=RotaryNestedScrollView`。
- 已修（`e3f42254`，在 `main`）：`ui/widget/RotaryEventPolicy.kt:55-59` 把 `pixelDelta` ×2；`:96-109` 注释写明 48dp（NestedScrollView）/64dp/App 2 倍基准，「详情页只有 37% 速度」即本条；`:139-146 topUpDelta` 在吞事件前按带符号差额补足；`:94 SYSTEM_FIRST_TARGET_SCALE=1.0f`、`:91` 系统优先模式不读灵敏度。开关/灵敏度：`util/SettingsKeys.kt:130-132`、`activity/settings/SettingPrefActivity.kt:113-117`。
- 待办：真机按 `manual-test-guide-261109.md` H03 验收（关闭适配后每格距离与开启时一致）。

---

## 4. P1：功能缺陷

### #87 / #90 分片视频/部分视频下载失败（合并处理）

- 来源：#90 XTC `monaco_go` 26.10.27「很多视频都无法缓存」；#87 同机 26.11.01「无法缓存分片视频，另一个版本的 RE 可以」。控制台已用「请更新到新版本」回过 #90。
- 代码核实：
  - 分派：`service/DownloadService.kt:734-749` —— `qn <= 64` 走 `api/PlayerApi.getVideo(data, true)` 单流（`useDash=false`，durl 合一 MP4/flv）；否则 `getVideoDash` 取 video+audio 双流。
  - 多线程分片只在 `Aria2Util.isEnabled()` 的 `downFileSpeed`（`:1439-1484`）：需 HEAD 有 `Content-Length`、`Accept-Ranges: bytes` 且 `totalSize > 2MB`，否则 `downFileSpeedSingle`。
  - Range 类异常都是「降级/变慢」不是「下不了」：`:1777-1780 rangeUnsupported`、`:1785-1788` `Content-Range` 不符、`SegmentDownloadPolicy.shouldDisableSegmentMode(...)`、12s 停滞 → 整文件重下 `:1675-1682`。
  - **真正根因（强推测，需先写失败用例确认）**：多段 `durl` 只取第 0 段 —— `api/PlayerApi.java:354-355 JSONObject firstDurl = durl.getJSONObject(0);`（同型 `:302-303`，番剧 `:429-430`）。全仓 grep `durl|segment_base|segment_duration` **没有任何遍历拼接**；`player/PlayUrlFallbackPolicy.kt:106-107` 只用 `durl.length() > 0` 判可用。服务端回分段 durl 时只下第一段 → 能下但成品是残片（正好对上「分片视频下不了」）。
  - 失败不告知原因：`result != NORMAL` 只 `exitCode = result; return false`（`:913-923`、`:1196-1200`），列表只显示「下载出错」`adapter/video/DownloadAdapter.kt:150`；真实原因只在日志（`:1275-1279` HTTP 码、`:1321-1324` 下载不完整、`:1817` 分片 3 次失败）。
  - 附带小 bug：取流 `IOException` 时 `:764-767` 置 `"none"`，紧接着 `:674` 又置 `"error"`，状态口径矛盾。
- 本批决定：**修：遍历全部分段并拼接**。
- 实现要点：`api/PlayerApi.java` 输出分段清单（url/长度/顺序）→ `service/DownloadService.kt` 按清单顺序下载（可并发但必须落独立分片文件）→ 本地拼接（`video_new.mp4` 汇总）→ 完成后清理分片；清单需进 `service/download/DownloadRepository.kt`（新建列或序列化字段）；`DownloadAdapter` 展示失败原因。
- 验收：L1——找一条分段 durl 视频（qn<=64）下载，落盘文件大小 ≈ 各段之和，播放完整；日志无 `unexpected end of stream` 反复。
- 影响文件：`api/PlayerApi.java`、`service/DownloadService.kt`、`service/download/DownloadRepository.kt`、`adapter/video/DownloadAdapter.kt`。

### #150 新的评论回复和点赞不显示

- 来源：samsung `fresh7ul`，26.11.09。开发者原回复「刷新一次试试」，本批定为**真 bug，排期查**。
- 代码核实：
  - `api/MessageApi.java getLikeMsg 104-212`：items 取 `data.total.items`（`113-115`），游标却按 `data.cursor`（`204-207`），分页参数照抄 reply 的 `reply_time`（`107-108`）→ 结构取错。
  - `getReplyMsg 214-317` 取 `data.items`（`222`）/`data.cursor`（`309`）。
  - `checkMessageUnread() 68-74` 只累加 `at+reply`，**不含 like** → 红点也不提示点赞。
  - `activity/message/NoticeActivity.kt:37` 只在 `onCreate` 拉一次，无下拉刷新、无 `onResume`；`catch` 里 `bottom=true`（`84-92`）→ 首屏失败后再也不翻页。
- 实现要点：按 like 接口真实结构取 items/游标；未读计数补 like；列表加下拉刷新与 `onResume` 重拉；`catch` 不再置 `bottom=true`（区分「真到底」与「请求失败」）。
- 验收：L1——点赞与回复两个 Tab 首屏有数据、下拉能翻页、未读数含点赞；断网再恢复不会永久空白。
- 影响文件：`api/MessageApi.java`、`activity/message/NoticeActivity.kt`。

### #143 弹幕加载不出来；跳到某时间点或加速后弹幕变少

- 来源：HUAWEI `Nick-AL00`，26.11.09。用户补充两次：「跳转到视频某个时间段或加速」→「转到某个时间段，弹幕会变少」。
- 代码核实：
  - 接口：`api/DanmakuApi.java:200-212` 拼 `x/v2/dm/wbi/web/seg.so?...segment_index=N`；`SEGMENT_SECONDS=360`（`:109`）；单段取 + 重试 1 次（`:162`、`:179-188`）。
  - 窗口：`player/DanmakuStreamLoader.kt:26 radius=2`（5 段 ≈30min）；`:194 prepare` 同步拉首窗；`:257 ensureWindow`；`:301 DanmakuWindow.rangeFor` 按 `segmentCount()` 裁剪。
  - 区间来源：`activity/player/PlayerActivity.kt:3005-3019` 用 `ijkPlayer.duration/1000`，取不到回落 **3600**。
  - 触发：`PlayerActivity.kt:4521 seekToVideo → ensureWindow`、`:2527` 每 250ms 进度循环 → `ensureWindow`。
  - 推测（变少的机制）：`DanmakuFlameMaster/.../CacheManagingDrawTask.java:561-576` SEEK 分支 `evict/clearTimeoutCaches`，加 `DrawTask.java:105-134` 只把 `[mLastBeginMills, mLastEndMills]` 内的弹幕放进当前子集 → 远跳后补灌的段不一定进子集。
  - 退避：`player/DanmakuStreamLoader.kt:82 MAX_FETCH_ROUNDS=4`、`:357` 每次失败退避 15s、`:260 windowReady`、`:266 retryAfterMs` → DNS 全断时 4×15s 反复刷日志（#154 日志即此）。
- 本批决定：**跳转/倍速后按新时间窗重拉补齐**；**退避改快速失败**。
- 实现要点：`seekTo` / 倍速变化时重建时间轴（清 DFM 缓存子集并按新 `[begin,end]` 预灌），而不是只补窗口；网络类失败快速失败（如 1 次重试后停），网络恢复后按需重拉。
- 验收：L1——跳转到 30min 后位置，弹幕量与常速播放到该处相当；断开网络 30s 内不再反复请求（日志条数可控）。
- 影响文件：`player/DanmakuStreamLoader.kt`、`activity/player/PlayerActivity.kt`、`api/DanmakuApi.java`、DFM 缓存策略相关类。

### #148 搜索时视频会重复

- 来源：HUAWEI `Nick-AL00`。
- 代码核实：`api/SearchApi.java:309-327 parseVideoEntry` 直接 `add`，**无 bvid 去重**；`activity/search/SearchVideoFragment.kt:76-78 addAll` 纯追加；`refreshInternal()` `92-103` 不清在途请求（刷新与翻页并发会重复）；风控回退 legacy URL 不带 `seid`（`172-184`）也会重复。对照：推荐页已有去重 `activity/video/RecommendActivity.kt:113 filterNewCards(list, loadedBvids)`。
- 本批决定：做（去重）。
- 实现要点：搜索结果按 bvid 去重（复用 `filterNewCards` 思路或抽公共函数）；`refreshInternal` 取消/忽略在途请求（代次 token）。
- 验收：L1——连续下拉刷新 3 次 + 翻 3 页，列表 bvid 无重复。
- 影响文件：`api/SearchApi.java`、`activity/search/SearchVideoFragment.kt`。

### #142 cv 文章无法看到文字

- 来源：FuRuiMi `uws6137_1h10_64b`，26.11.09。开发者已加用户 QQ 索取具体文章样本。
- 代码核实：`api/ArticleApi.java:63 articleInfo.content = data.optString("content","")` 拿的是**原始 HTML**，而全仓 `Html.fromHtml` **0 命中**（`StringUtil.removeHtml` 只服务直播）→ cv 正文根本没有渲染器。专栏实际走 Opus：`api/OpusApi.java:79-82` 对 `id <= 1e8` 用 `read/cv` 抓网页 `__INITIAL_STATE__`（`fetchDetailFromHtml 202-237`），失败 `throw`「未能解析出图文内容，可能被风控拦截」（`85-95`），UI 只剩失败图标 `activity/article/OpusInfoActivity.kt:78-81`；未知段落渲染成 `[无法识别段落：N]`（`model/OpusParagraph.java:82`）、`[type]`（`:215-216`）。渲染是自绘段落列表：`OpusInfoFragment.kt:84-96` → `adapter/article/OpusContentAdapter.kt`。
- 本批决定：**继续自绘富文本**（不引 WebView）。
- 实现要点：cv 类文章（`ArticleApi` 的 HTML 正文）补一个轻量 HTML→段落/文本转换（`<p> <br> <img> <h1-3> <a>` 白名单），接进现有段落适配器；把「风控拦截」与「无摘要/无正文」区分成两种文案。
- 验收：L1——取用户给的那几篇 cv 文章，正文文字完整、图片可见；风控失败时提示准确。
- 影响文件：`api/ArticleApi.java`、`activity/article/OpusInfoActivity.kt`、`OpusInfoFragment.kt`、`adapter/article/OpusContentAdapter.kt`、`model/OpusParagraph.java`、新增 HTML 转换工具（`util/`）。

### #151 / #152 合集不记录、也不回跳上次看到的位置

- 来源：XTC `monaco_go` 两条同一用户；开发者已答「下版本再好好完善一下」。
- 代码核实：`player/CollectionResumeStore.kt`（`save 105-111`、`resolveTargetAid 38-55`、key 前缀 `collection_resume_v1_`）；**唯一写入点** `activity/video/info/VideoInfoFragment.kt:815-817 if (collection.id > 0) CollectionResumeStore.save(collection.id.toLong(), videoInfo!!.aid)`，在 `playClick()` 里；读取 + 滚动 `activity/video/collection/CollectionInfoActivity.kt:64、85 scrollToPosition`，全在 `onCreate`。缺口三条：①`collection` 仅来自 `ugc_season` 解析（`api/VideoInfoApi.java:254-257`），`id <= 0` 静默不写；②只有「详情页点播放」写，收藏夹/历史/下一集直接起播不写；③无 `onResume` 重定位。
- 本批决定：**合集进入也记录 + 一键跳回 + 「上次看到」文字标记**（开发者补充要文字标记）。
- 实现要点：写入点下沉到播放页起播处（所有起播路径都记），`collection.id <= 0` 时退回「按 aid 记录」的降级键；`CollectionInfoActivity` 加 `onResume` 重定位 + 在条目上显示「上次看到」标签（点击即跳）。
- 验收：L1——从合集进入看到第 5 个视频，退回列表能看到标记；杀进程重进合集自动/一键回到第 5 个。
- 影响文件：`player/CollectionResumeStore.kt`、`activity/video/info/VideoInfoFragment.kt`、`activity/video/collection/CollectionInfoActivity.kt`、`api/VideoInfoApi.java`（`ugc_season` id 兜底）。

---

## 5. P2：体验与小修

### #125 手表选择窗口「取消/确定」点击错位

> **26.11.13 已落地，本节的行号与方案作废，只留作历史。** 后来的实测（控制台 #161 同族报障，AVD `wm size 466x466`）证明真因不是测量时序，而是**按钮只占按钮行右端、左侧 144px + 右侧 30px 是死区**，OEM 输入层一旦横向偏移就会「点左执行右」。最终修法是构造性的：每个按钮独占一整行（`MATCH_PARENT` 宽、居中、上下堆叠），`fitButtonBars/fitButtonBar/barDesiredWidth/shareBarWidth` 与 `Sheet` 的 `bottomButtonBar` 参数**全部删除**。详见 `docs/review/fix-progress.md` §1 首条与 `docs/architecture-map.md` §8.7.0。
>
> 下面保留 26.11.11 当时的核实记录：

- 来源：HUAWEI `HWARC` 26.11.07。开发者已答「马上修复」。
- 代码核实：确定/取消只出自 `util/TerminalDialog.kt`（confirm `156-181`、choice `202-219 bottomButtonBar`、addButton `700-739`）；窗口宽度 `prepareLayout 428-448` → `applyPlan 464 setLayout(winW, WRAP_CONTENT)`；`show` 后 `fitButtonBars 492-500` / `fitButtonBar 502-521` 再缩字号与 `minimumWidth`。全仓 `TouchDelegate` 0 命中，`translationX` 只在 `SwipePageLayout`/`PlayerActivity` → **静态找不到确定偏移源**，推测是 `fitButtonBar` 事后改尺寸与窗口宽度测量的时序问题。
- 本批决定：做。
- 实现要点（**已作废**）：按钮尺寸确定后再测量一次窗口（或 `fitButtonBar` 内改为 `requestLayout` + 重测量），避免「先按旧宽度算点击区、再缩字号」。须 L2 真机核验点击位置。
- 验收：L2——手表上点按钮中心/边缘都能触发；不同字号档下都不偏。
- 影响文件（**已作废**）：`util/TerminalDialog.kt`（`428-521`）、`ui/widget/view/DialogSizing.kt`（`19-40`、`174`）。

### #140 推荐页长按阈值偏短 + 多选菜单样式不一致

- 来源：xiaomi `axolotlte`。开发者本批答「两项都做」，并补充「pr 已做」。
- 代码核实：200ms 硬编码在 `adapter/video/VideoCardHolder.kt:128-135 v.postDelayed(longPressRunnable, 200)`，同值出现在 `HistoryVideoCardAdapter.kt:22 LONG_PRESS_DELAY=200L`、`UserVideoAdapter.kt:70`、`ArticleCardAdapter.kt:64`。多选只改项自身：`VideoCardHolder.kt:167-179 alpha = if(selected) 1f else 0.45f` + 标题加「✓ 」前缀；对照 `util/TerminalDialog.kt:66-72`（`GLYPH_IDLE ›` / `ACTIVE ▸` / `CHECK ✓`）+ `paintSelection 749-764`。
- **待核对**：PR #27（`941acb1a`）的 diff 只含 `BaseActivity.kt` / `VideoInfoActivity.kt` / `TopbarLongPressPrefs.kt` / `docs/review/fix-progress.md`，**没有** `VideoCardHolder.kt` 的 200ms 改动，也没有多选样式改动。接手方先确认开发者所说「pr 已做」指哪一个分支；若没有，本条仍按「四项卡片的 200ms → 建议 500–700ms」+「多选态改用 `TerminalDialog` 的 glyph/描边体系」实现，注意四处硬编码要抽同一个常量。
- 验收：L2——推荐页长按不再因误触弹出菜单；多选态的视觉与其他终端弹窗一致。
- 影响文件：`adapter/video/VideoCardHolder.kt`、`HistoryVideoCardAdapter.kt`、`UserVideoAdapter.kt`、`ArticleCardAdapter.kt`、`util/TerminalDialog.kt`（取样式）。

### #121 私信「可能被拦截」提示可关闭

- 来源：HUAWEI `HWARC`。
- 代码核实：`activity/message/PrivateMsgActivity.kt:115` 无条件 `MsgUtil.showMsg("私信有可能被拦截…")`，无开关。
- 实现要点：新增 `SettingsKeys` 布尔项（默认显示）→ 设置页加开关 → 判断后 `showMsg`；首次提示可保留「不再提醒」入口。
- 验收：L1——关闭后重进私信页不再弹提示；重开设置后恢复。
- 影响文件：`activity/message/PrivateMsgActivity.kt`、`util/SettingsKeys.kt`、`activity/settings/*`、`res/values/strings.xml`。

### #123 一键清空搜索记录

- 来源：HUAWEI `HWARC`。开发者已答「我准备加个多选＋全选」。
- 代码核实：存储 `util/SharedPreferencesUtil.java:111 search_history`（JSON 数组），读写 `activity/search/SearchActivity.kt:218-220 / 238-239 / 517-518 / 532-533`；唯一删除入口是长按单条（`232-240`），**无清空全部**。
- 实现要点：搜索页历史区加「清空」按钮（二次确认），清空后同步刷新界面与未读态。
- 验收：L1——点清空后历史列表空、重进应用仍为空。
- 影响文件：`activity/search/SearchActivity.kt`、`util/SharedPreferencesUtil.java`、`res/values/strings.xml`。

### #129 短视频双指拖动放大

- 来源：HUAWEI `Nick-AL00`。用户补充「退出键越来越不好点」（本批不做）。
- 代码核实：捏合已存在——`activity/video/ShortVideoPlayerActivity.kt:584-585`（min 1.0 / max 3.0）、`:715-735`（detector + `coerceIn`）、`:954-959 matrix.setScale`、`:1040-1047 TextureView`；`onDoubleTap` 只切播放/暂停（`:703-706`）；pivot 重算 `:1336-1338`。
- 本批决定：**只做双指拖动放大**（双指手势的缩放 + 平移），退出热区不做。
- 实现要点：现有 `ScaleGestureDetector` 基础上加双指位移平移，放大后限制边界不回弹过头；与上下滑动切视频的手势冲突要分派清楚。
- 验收：L2——双指放大到 3 倍并能拖动画面；松开后不错位；单指上下滑仍能切视频。
- 影响文件：`activity/video/ShortVideoPlayerActivity.kt`。

### #94 分页加载慢/批量操作不便（收敛为「全选」）

- 来源：OPPO `OWW211`。开发者此前已答复：左右拖动灵敏度已做、批量转移已做。
- 代码核实：灵敏度已存在——`activity/player/PlayerSeekPolicies.kt:19-70`（6 档 0.25/0.5/1.0/1.5/2.0/3.0，默认 1.0，存字符串，key `util/SettingsKeys.kt:186`；`targetMs = start + (deltaX/width × durationMs × scale)` 钳 `[0,duration]`；生效 `activity/player/PlayerActivity.kt:4754-4760`、`:4784`；文案 `res/values/strings.xml:2677-2678`）。缓存页 `activity/video/local/LocalListActivity.kt`：多选 `:1093/:1110/:1122/:1139`、批量移动 `:1149-1186 / :1255-1280 / :1282-1294`、单条移动 `:952`、搜索 `:523/:533/:541` + `:1067`、拖动排序 `:468/:480/:494`（`util/LocalListSort.kt` 无预设档位，排序态禁搜索 `:465`）；**无「全选」**（全仓 grep 无命中）。下载列表页 `activity/video/local/DownloadListActivity.kt:246-253` 只有「退出多选/删除」，`:271/:296/:308`；顺序来自 `service/download/DownloadRepository.kt:74 select * from download`（无 `ORDER BY`）；布局 `app/src/main/res/layout/activity_simple_refresh.xml:54 filterBar`、`:111 sortBar` 已存在但未接线。
- 本批决定：**两页都补「全选」**（排序/搜索/批量移动不再扩）。
- 验收：L1——缓存页与下载列表页多选态都能一键全选，删除/移动对全选生效。
- 影响文件：`activity/video/local/LocalListActivity.kt`、`activity/video/local/DownloadListActivity.kt`、`res/values/strings.xml`。

### #155 音量调到最大也很小（调研）

- 来源：Mobvoi `rover`，26.11.09，sdk 28。开发者原回复「无极调音是不是动了」，用户答「二一版本打错了，应该是 re 版本」。
- 代码核实：`player/PlayerVolumePolicy.kt:31 MAX_PERCENT=100`（注释：不放大避免削波），`percentToGain = /100f`；`activity/player/PlayerActivity.kt:3337-3366 changeVolume` 只改系统 `STREAM_MUSIC`；无极调音面板 `:3378-3418`（seekbar max=100），落地 `:3441-3455 ijkPlayer?.setVolume(gain, gain)`；`:3342` 只在增益 0% 时救回 100%。**应用侧没有任何放大通道**，且用户停在中间增益时按音量＋只加系统档、听感仍小。
- 本批决定：**先查播放器音量通道再决定**（调研，不改代码）。
- 调研清单：①该机型是否有独立媒体音量/耳机增益；②无极调音增益与系统音量是否叠加（当前看是相乘）；③是否需要把 `MAX_PERCENT` 提到 >100 并加响度补偿（风险：削波）；④是否修「中间增益时音量＋无效」的交互。
- 产出：结论写回本条，再定是否排期。

### #128 / #102 视频缩放有时失效（暂缓，待录屏）

- 来源：两条 OPPO `OWW261`（26.11.07 / 26.11.06）。#128 用户自己说「这个貌似是我手表问题吧」，开发者复现不出。
- 代码核实（供下次复现用）：`activity/player/PlayerActivity.kt:1466` 整块手势包在 `PLAYER_SCALE` 判断里，**只在页面创建读一次**；`:1472` 监听挂在 `layout_control`；`activity/player/ViewScaleGestureListener.kt:11-21`（clamp 1~5，写 `layout_video.scaleX/Y`），`:12` GONE 直接返回 false；双击复位 `:1616-1631`；播放器 `:4311`/`:3569 player_display` 默认 `SDK_INT < 26`（API ≥ 26 = SurfaceView）。三个可疑点：①改设置后不重建页面不生效；②锁定态 `layout_control` GONE（`:1818`/`:1832`）→ 手势全失效；③SurfaceView 的变换由父层合成，部分 ROM 不跟随（同类提示 `:3570`）。
- 本批决定：**保留待复现**（需用户录屏或明确「失效前改了什么设置」）。

### #149 继续播放入口消失（暂缓）

- 来源：`aqsh_watch` `dove_evb4`。开发者答「准备重做，目前隐藏」，本批决定**先放着**。
- 代码现状（供重做时用）：`util/MenuConfig.kt:55 RESUME`、`:58 ACTION_ITEMS`、`:89`（默认关闭的动作项）、`:170`（故意不补）；入口 `activity/MenuActivity.kt:117-123` + `api/PlayerApi.java:811-838 buildResumeIntent`；持久化 `player/PlaySessionStore.kt:42 PREFERENCES PLAYSESSION1`、`:140 player_last_session_v1`，写入口 `activity/player/PlayerActivity.kt:5608 savePlaySessionNow`。提交 `29e8c169 feat(menu):「继续播放」改成可开关的动作项，默认不显示`。
- 重做时要回答：默认显示还是仅开关、恢复粒度（整条播放会话 vs 单视频进度）、与 #151/#152 的「上次看到」是否统一存储。

### #147 番剧缓存（暂缓，等换源播放）

- 来源：Xiaomi `Archytas`。开发者答「换源播放番剧先做好了再搞这个」。
- 代码核实（做完换源后按此清单接）：番剧**能播不能下**——`api/PlayerApi.java:398-435 getBangumi(PlayerData)`（`fnval=1` → 只 durl 合一 MP4，无 DASH），唯一调用点 `activity/video/JumpToPlayerActivity.kt:126`；下载入口全在 VideoInfo 一族（`api/PlayerApi.java:68-97 startDownloading`、`:107-121 startDownloadingAudioOnly` → `DownloadService.startDownload(title, aid, cid, …)`，调用方 `adapter/video/VideoQuickCache.kt:80/106/124`、`activity/video/QualityChooserActivity.kt:117/155/172`、`activity/user/favorite/FavoriteVideoListActivity.kt:1035/1053/1058`）；下载器把取流写死成普通接口（`service/DownloadService.kt:716 downloadSection.toPlayerData()`，`:719/735/742` 只调 `getVideoDash`/`getVideo`）；`model/DownloadSection.java:106-114 toPlayerData()` 无 `epid/seasonId/seasonType/isBangumi`；download 表 11 列无 epid（insert `DownloadService.kt:253` / `:313`）。
- 缺的四样：①番剧页/播放器内下载入口 ②`DownloadSection` + download 表的 epid/season 映射 ③下载器的 bangumi 分支 ④pgc DASH 取流（`fnval=4048` 走 pgc 端点）+ 大会员/地区/权限判定。
- 前置：`wip/bangumi-playback`（HEAD `dc07d2e3 wip(bangumi): 无感换源收尾…`）。

---

## 6. P3：新功能

### #156 直播弹幕无法发送（26.11.11 已实现，落点见 §9.8）

- 来源：OPPO `OWW211`。开发者原回复「？我好像没做这个功能吧」，本批决定**要做**，范围**只发文本弹幕**。
- 代码核实：无直播弹幕发送能力——`activity/player/PlayerDanmuClientListener.kt:32-49` 只 `onOpen` 发握手包（`protover=3`），`:111` 只发空包心跳。可复用录播的发送链路形状：`activity/player/PlayerActivity.kt:4354-4383` → `api/DanmakuApi.java:29 sendVideoDanmakuByAid`；发送卡片 `res/layout/card_danmaku_send` 可复用。
- 安全/域名：明文白名单 `app/src/main/res/xml/*.xml` 只有 `bilibili.com` / `hdslb.com`（`includeSubdomains="true"`）；直播接口已用 https（`api/LiveApi.java:37`、`api/UserInfoApi.java:359` 走 `api.live.bilibili.com`）→ **无需改清单**，别新增域名。
- 实现要点：新增直播发弹幕 API（roomid/ccid/csrf/cookie，protover=3 的握手与心跳已具备）+ 播放页直播分支接线 + 发送卡片（字数上限、频率限制提示）。
- 验收：L1——登录后在某直播间发一条文本弹幕，日志无风控错误；频率超限时提示明确。L2——真机看是否出现在直播间。
- 影响文件：`api/DanmakuApi.java`（或新增 `LiveDanmakuApi`）、`activity/player/PlayerDanmuClientListener.kt`、`activity/player/PlayerActivity.kt`、`res/layout/card_danmaku_send.xml`、`res/values/strings.xml`。

### #133 视频转发到私信（26.11.11 已实现，落点见 §9.9）

- 来源：samsung `fresh7ul`。开发者答「有的，我甚至还想对接 QQ」。
- 代码核实：现有转发只到动态——`activity/video/info/VideoInfoFragment.kt:585`、`:134-135`；落点 `adapter/dynamic/DynamicHolder.kt:920-938 onRelayClick` → `SendDynamicActivity`；`api/DynamicApi.java:488-521`。私信发送已有 `api/PrivateMsgApi.java:252 sendMsg`（撤回 `306`）。**无「选好友→私信」链路**，全仓无 `ACTION_SEND` 分享面板（只有 `util/LogExporter.kt:90`）。
- 实现要点：转发面板加「发给好友」→ 复用会话列表选择 → 组装卡片消息（bvid + 封面 + 标题）走 `sendMsg`；失败回落到「复制链接 + 提示」。
- 验收：L1——选好友发出后，私信会话里能看到视频卡片；发送失败有提示。
- 影响文件：`activity/video/info/VideoInfoFragment.kt`、`adapter/dynamic/DynamicHolder.kt`（转发入口）、`api/PrivateMsgApi.java`、新增会话选择页/复用现有私信列表、`res/values/strings.xml`。

### #120 顶栏全局重构（含「回首页」、电量百分比统一）

- 来源：HUAWEI `HWARC`（一条里写了 5 项）。本批决定：**只做顶栏重构**（开发者补充「顶栏是上面一条，肯定一起重构了啊」＝全局），私信换行/表冠缩放/私信时间戳本批不做。
- 代码现状：电量百分比——播放页是自绘电池无文字（`ui/widget/BatteryView.kt:44`，数据 `activity/player/PlayerActivity.kt:1094-1097`，开关 `util/SettingsKeys.kt:283-289`），信息岛已是百分比（`ui/widget/TopbarIsland.kt:399-405`），提交 `8829cd67 fix(island): 顶栏灵动岛宽度全局统一，补状态图标与电量百分比`；详情页无「回首页」独立按钮，只有长按标题栏（`activity/video/info/VideoInfoActivity.kt:207-220`）；私信无时间戳（layout grep `text_time|timestamp|date` 0 命中，仅顶栏 `TextClock` `res/layout/activity_private_msg.xml:42-49`）。
- 实现要点：抽一套顶栏组件（返回 / 标题 / 回首页 / 状态岛），播放页、详情页、列表页、设置页统一接入；电量百分比与 `BatteryView` 统一为一处实现；「回首页」= 回到 `MenuActivity`。
- 验收：L2——各页顶栏视觉与点击热区一致；点「回首页」任意页回到首页；电量百分比与系统一致。
- 影响文件：`ui/widget/TopbarIsland.kt`、`ui/widget/BatteryView.kt`、`activity/base/BaseActivity.kt`、各页 layout（`res/layout/activity_*.xml` 顶栏块）。

---

## 7. P4：不做 / 已回复（保留记录，不实现）

| 编号 | 结论与依据 |
| --- | --- |
| #154 | 日志明确是 `Unable to resolve host "api.bilibili.com"`（真实断网）+ 用户报「错误码 -10000」。本批**不改代码**（开发者决定），回复已发。 |
| #141 | 想复制链接：长按转发按钮已可复制。开发者已回复，用户无追问。**不做**。 |
| #127 | 「内容摘要用不了」：数据侧没摘要（日志同时显示「高能进度条>返回体里没有 events.default」）。回复为「有可能是没有摘要导致的」。**不做**。 |
| #137 | 动态页改成「点头像就地展开最新动态」：需要新增注入式 `onAvatarClick` + 展开子列表（现状 `adapter/dynamic/DynamicHolder.kt:782-784` 只 `jumpToUser`）。本批**不做**（开发者未选）。 |
| #134 | 「一键切换官方版界面」：已有 7 套配色主题（`ui/appearance/ColorScheme.kt:42-48/54/809-833/849-860`，入口 `activity/settings/SettingGroupActivity.kt:425-434`），但**无布局镜像**。本批**不做**。 |
| #119 | 看图页背景透出壁纸：`activity/ImageViewerActivity.kt:190` 未设窗口背景，`ui/appearance/ColorScheme.kt applyWindowTheme() 681`（`:715-732` 设壁纸背景）。开发者曾说「之后加入图片裁剪功能」。本批**不做**。 |
| #115 | 推荐页长按改「不感兴趣」：`adapter/video/VideoCardMenu.kt:39-62` 已含 `REDUCE_RECOMMEND`，`activity/video/RecommendActivity.kt:129` + `:161-184 FeedbackApi.dislike` 已注入；用户看到「缓存」可能是未注入或旧版本（回落规则见 `VideoCardAdapter.kt:99` 注释）。本批**不做**（先确认版本再谈）。 |
| #122 | 假计算器兜底入口：公开侧 grep「用户协议/agreement」0 命中 → 该页不存在；公开侧只有 `activity/disguise/CalculatorDisguiseActivity.kt`（类注释 `24-26`：返回键只当 C 清空）与 `BluetoothDisguiseActivity.kt`，密码本体疑似私有（门面 `util/OptionalFeatures.kt:20`）。**不做**；若要做，需求只能进私有仓库。 |
| #139①③④ | ①进度条热区 26.11.07 曾放大到 48dp，26.11.08 已整批回退（`res/layout/activity_player.xml:208-213` 注释）；③手表模式、④播放页内弹幕设置本批**不做**。 |

---

## 8. 交接注意

1. **不要提交**：开发者明确「先不提交」。本轮只产出本文；工作区里的下载相关改动（`service/DownloadService.kt`、`service/download/DownloadProgressStore.kt`）保持未提交状态。
2. **不要标 handled**：36 条在后台仍为未处理，等实际处理后再改状态。
3. **顺序建议**：#117/#135/#139② 是同源，一起改一起验；#153 先合 PR #27；#87/#90 要先写失败用例再动 `api/PlayerApi.java`；#150/#148 都在 `api/` 层，可同批。
4. **L2 项**（只能人工判）：#125 点击位置、#140 手感、#129 手势冲突、#120 顶栏观感、#117 横屏复现。
5. **未核实、需要接手方先查**：#117 的「内测版无法横屏」；#140「pr 已做」指哪个分支；#155 的音量通道结论。
6. 文档改动后按 `AGENTS.md` 要求跑 `npx --yes markdownlint-cli2 "AGENTS.md" "readme.md" "docs/**/*.md" "!docs/archive/**"`，要求 0 错误。

---

## 9. 实现落点（26.11.11，子代理落地）

> 五个子代理各领一段互不重叠的文件域并行实现，收尾由 Lead 统一跑 L0 与回写文档。**L0 全绿**（26.11.11 本机，工作区含全部改动 + 用户既有未提交改动）：`:app:assembleDebug` BUILD SUCCESSFUL（standard / coexist 双 flavor）、`:app:testStandardDebugUnitTest --rerun` → **217 suites / 2343 tests / 0 失败 0 错误**。**L2 全部未验**（本机无手表），清单见 §9.6 与 `docs/review/fix-progress.md` §2.1。

### 9.1 播放器退出链 + 弹幕 + 横屏（#117 / #135 / #139② / #143）

- **返回键退出开关**：`util/SettingsKeys.kt:200` 新增 `PLAYER_BACK_KEY_EXIT`（默认开）；开关落在**播放器设置 → 手势段** `activity/player/PlayerSettingsGroups.kt:174-175`；文案 `res/values/strings.xml:2818-2820`（`setting_player_back_key_exit` / `desc_player_back_key_exit` / `msg_player_locked_exit_hint`）。
- **退出链**：`PlayerActivity.kt:861 backKeyExitEnabled()`；`:864-880 onBackPressed()` 在锁定拦截之后、`swipeSeekEnabled` 吞键**之前**插一道「开关开着直接 `super.onBackPressed()`」；`:1052` `noSwipe = swipeSeekEnabled || !backKeyExitEnabled()`（关掉开关＝26.10.22 之后旧语义；开着且没开滑动调进度＝恢复窗口右滑返回）。
- **锁定态可辨识**：`PlayerLockPolicy.kt:24` `ICON_VISIBLE_MS` 3000→8000、`:30` 新增 `IDLE_ICON_ALPHA = 0.35f`；`PlayerActivity.kt:1842-1846` `LOCKED_HIDDEN` 由 `GONE` 改成**压暗常驻**；`:1893 interceptLockedExit()` 在「压暗→浮现」那一下弹提示（连按不刷屏）。
- **主线程 JNI 移出退出链**：`PlayerActivity.kt:7066 / :7074 finish()` 回传进度改用 `video_now`，不再读 `ijkPlayer!!.currentPosition`；`:3983 / :4026` 新增 `releaseIjkPlayerAsync()`，`onDestroy` 的 `ijkPlayer.release()` 移入独立短命线程（只捕获 player、不捕获 Activity）。
- **弹幕变少**：`player/DanmakuStreamLoader.kt:89 MAX_FETCH_ROUNDS` 4→1、`:86 RETRY_DELAY_MS` 15s→20s 且**冷却只对同一窗口生效**（新字段 `failedRange`，`:146/:285/:419`）；`:294 canRebuildWindow()`、`:310 rebuildWindow(positionMs)`、`:525 DanmakuWindow.needsTimelineRebuild()`（纯函数，跨 360s 分段才重建）；`PlayerActivity.kt:4596-4597 / :4627 seekToVideo` 跨段重建（`clearDanmakusOnScreen()` + `removeAllDanmakus(true)`）、同段只增量补；`:4504` 倍速滑条松手重建一次；新单测 `player/DanmakuWindowTest.kt:104/111/118` 三例。
- **横屏（#117 附带的「内测版无法横屏」）**：根因是**旧全局开关被新播放器逻辑丢掉**——`AndroidManifest.xml:238-243` 的 `PlayerActivity` 没有 `screenOrientation`，方向全由运行时 `requestedOrientation` 决定，而 `defaultOrientationMode()` 只认旧键 `player_autolandscape`（默认 false），旧版本里 `ui_landscape`（设置页「横屏模式」）会连带强制播放器横屏，26.11.x 播放器方向自成一套后这条断了。修法：`PlayerActivity.kt:2345 defaultOrientationMode()` 改为 `PLAYER_AUTOLANDSCAPE || UI_LANDSCAPE` → 横屏，`:1067-1072` 给「软件旋屏兼容 + 默认横屏」分支补显式钉竖屏；`PlayerSettingsGroups.kt:100-105 orientationDefault` 与同口径对齐（设置页「默认值」标签不再与实际相反）。
- **遗留（本轮不做）**：多段 durl 在**播放器**里仍只播第 0 段（见 §9.2 的下一段）。

### 9.2 下载分段（#87 / #90）

- 接口层：`api/PlayerApi.java:170 parseDurlSegments(JSONArray)`，`:333`（`tryGetVideoWithFnvalOnce` 回退）、`:391 getVideo`、`:472 getBangumi` 三处由「只取第 0 段」改为解析**完整分段清单**挂到 `model/PlayerData.java:95 videoSegments`（不参与 Parcel），`videoUrl` 仍取第 0 段（起播路径不变）。
- 下载链路：新增 `model/DownloadSegment.java:22-32`、`service/download/DownloadSegmentPlan.kt:20/40/51/73/108/119/131`（清单 JSON、总字节、`isSamePlan`、`isReusablePart`、`clearTempParts`）、`service/download/DownloadSegmentMerger.kt:22/31`（按序拼接 + 长度校验，失败删残片再抛）；`service/DownloadService.kt:1315-1381` 新方法「逐段下载 → 复用已完成段 → 拼接 → 清临时」，`:752-788` 三支取清单，`:823` 字节预算改用清单总字节，`:911 / :992` 两处单流分支改走它，`:1318-1330` 起分段进度文案。
- 持久化：`helper/sql/DownloadSqlHelper.kt:8/23/25/48-51` download.db **4→5**（`segments TEXT` + `fail_reason TEXT`，只 ALTER 不 drop）；`service/download/DownloadRepository.kt:25/144/160/185` 读写两个新字段；`model/DownloadSection.java:25/29/66-75` cursor 兼容读取（列缺失回落空串）。
- 失败原因可见化：`DownloadService.kt:453-465 / :709 / :801 / :812 / :1418 / :1469 / :1659` 记录 HTTP 码 / 「文件没下完（收到 x/y 字节）」/ 段号；`adapter/video/DownloadAdapter.kt:154-156` 显示「下载出错：<原因>」；文案 `res/values/strings.xml:945-957`。顺带修口径矛盾：`DownloadService.kt:807-815` 取流阶段 `IOException` 由置 `"none"` 改为 `"error"`。
- 单测：`api/PlayerApiDurlSegmentsTest`(5)、`service/download/DownloadSegmentPlanTest`(8)、`DownloadSegmentMergerTest`(4)。
- **数据兼容**：老记录 `segments` 空 → `isSamePlan=false` → 清临时分片按新清单重下；`fail_reason` 空 → 列表仍显示原「下载出错」；单段 / 无 durl / DASH / 纯音频走原 `downFile`。
- **未证实**：`bilibili-API/docs/video/videostream_url.md:157/258-264/339-480` 证明 `durl` 是分段数组且有 4 段实例，但同一份文档 `:1915` 又称「FLV 多段已下线」——本机没抓到真实响应体，**不能断言这是 #87/#90 的唯一根因**（也可能叠加 CDN 403 / 传输截断，失败原因文案就是为此加的）。
- **遗留（本轮不做）**：播放器侧仍只用第 0 段起播，多段视频可能播到第一段末尾就断。补「段尾自动切下一条 URL」要自己维护段索引 + 在 `onCompletion`（`PlayerActivity.kt:2189`）换 URL，而 ijk 换 URL 后计时从 0 重算，`video_now` / 进度上报 / 续看 / 弹幕时钟 / seek 全要加「段偏移量」，粗估 >100 行且动进度口径，收益面只有「老接口回多段 durl」的少数视频 → 记遗留，别顺手做。

### 9.3 消息 / 搜索（#150 / #148 / #123）

- **#150 点赞消息**：`api/MessageApi.java:59-92` 新增 `parseLikeItems`（`data.total.items` 优先回落 `data.items`）、`parseLikeCursor`（`data.total.cursor` 优先回落 `data.cursor`）、`likePageQuery`（首页空串，否则 `&id=…&like_time=…`，**改掉照抄 reply 的 `reply_time`**）；`:151-156 getLikeMsg` 改走这三个函数；`:106-119 checkMessageUnread` 抽 `sumUnread()` 并累加 `at + reply + like`；`activity/message/NoticeActivity.kt:55-72` 补 `onResume`（首次跳过、回前台重拉第一页）与 `:22-31 loadGeneration`，`:73-130 loadFirstPage` / `:132-199 continueLoading` 的 `catch` **不再置 `bottom = true`**（改 `bottom=false` + 可重试空态），空列表才 `showEmptyView()`。
- **#148 搜索去重**：`activity/search/SearchVideoFragment.kt:18-28 loadedBvids`，`:80-99` 落地前走 `RecommendPagePolicy.filterNewCards(list, loadedBvids)`（复用推荐页同一份实现），整页重复时 `bottom=true`；`:111-119 refreshInternal` 补 `invalidateInFlightRequests()` + 清集合；`api/SearchApi.java:171-185 appendSeid` 让风控回退的 legacy 搜索也带 `seid`（`:191 / :194-196`）。
- **#123 清空搜索记录**：`res/layout/activity_search.xml:22-45` 历史区右上「清空」胶囊（`@+id/history_clear_button`，默认 gone，**刻意 `focusable="false"`**——一抢焦点就会触发输入框 `onFocusChange(false)` 把浮层连同自己一起隐藏）；`activity/search/SearchActivity.kt:53-55 / :135-137 / :651-704` 接线 + 二次确认（危险色）+ 内存与落盘一起清 + 就地刷新；文案 `strings.xml:1623-1626`。
- 单测：`api/MessageApiTest`（11 例，含 items/游标双形态、`like_time` 参数、`sumUnread` 含 like）、`api/SearchApiTest`（14 例，含 `appendSeid` 2 例）。
- **未核实**：like 接口真实响应体没拿到（无登录 Cookie，`x/msgfeed/like` 返回 `-101`），`bilibili-API/` 也没有该接口文档快照 → 字段位置靠「代码自身三处矛盾 + 双形态兜底」推出，**双形态写法保证推错也不弄坏好路径**。

### 9.4 专栏正文 / 合集续看（#142 / #151 / #152）

- **#142 cv 文章**：新增 `util/ArticleHtmlConverter.java`（白名单状态机、零 `android.*`，可进 JVM 单测；`:53 toParagraphs`、`:42 MAX_PARAGRAPH_CHARS=800`、`:120 toPlainText`、`:137 hasContent`）；`api/OpusApi.java:121` 页面抓不到 `detail` 时不再直接抛「可能被风控拦截」，改走 `:278 loadArticleFallback`（`ArticleApi.getArticle` → HTML 转段落 → 都空才抛 `:47 ArticleEmptyException`），`:194 / :293 mergeArticleFallback` 在段落全空时用文章接口补正文，`:388 analyzeParagraphs` 跳过空段落、删 4 处 `Log.e("debug-专栏")`；`model/OpusParagraph.java:31 TYPE_CODE=7` + `:101 analyzeCode`（有 `code.pic.url` 就改判出图）+ `:123 analyzeUnknownParagraph`（不再怼 `[无法识别段落：N]`）+ `:278 TEXT_NODE_TYPE_FORMULA`；`activity/article/OpusInfoActivity.kt:84-90` 按异常类型分流两条文案（`strings.xml:2964-2967`）。
- **#151 / #152 合集续看**：`player/CollectionResumeStore.kt:46 lastPlayedAid`（默认 0，旧单测不破）+ `:132 KEY_LAST_PLAYED` 降级槽 + `:147 save`（`seasonId<=0` 也写降级槽、用 `putStringSync` 同步落盘）；写入点由「只有详情页 `playClick`」下沉到 `activity/video/JumpToPlayerActivity.kt:73 / :174`（所有 `PlayerApi.startGettingUrl` 路径）与 `activity/video/info/VideoInfoFragment.kt:817`，再补播放器内换集 `PlayerActivity.kt:5886 switchToOnlinePage`（另两处 `PlayerApi.getVideo` 是换清晰度 / 互动分 P，aid 不变故不写）；读取 `activity/video/collection/CollectionInfoActivity.kt:122 onResume` 重定位（只在目标变化时滚，不打断用户自己滚动）、`:150 lastPlayed()`、`:155 markResumeAid`、`:163 positionOf`，两个 adapter（`:298`、`:393`）在标题前加 「[上次看到] 」（`:359`、`:511 markResumeSeen`，调用点 `:327`、`:482`）。
- 单测：`util/ArticleHtmlConverterTest`(18)、`api/OpusApiTest`(8)、`player/CollectionResumeStoreTest`(23)。
- **遗留**：`<b>/<i>/<h1-3>` 只有文字没有加粗放大（转换器要保持纯 JVM 可单测，样式只能放 UI 层，动 `OpusContentAdapter` 会同时影响图文动态渲染）；`CollectionInfoActivity` 里 `markResumeSeen` 是两个 adapter 各自的 private 方法（Kotlin 嵌套类非 inner 拿不到外层 Activity 成员，放成文件级顶层函数要重排文件尾、风险更大），5 行重复是刻意取舍。

### 9.5 UI 小修（#125 / #140 / #121 / #129 / #94）

- **#125 弹窗按钮错位**（**26.11.13 已被取代，下面只作历史记录**）：`util/TerminalDialog.kt:450` 在算 `DialogSizing.plan(...)` **之前**先 `fitButtonBars(box.width)`；`:455-492 applyPlan()` 重排成「所有改尺寸的事做完 → 最后一次 `content.requestLayout()` + `window.setLayout(winW, WRAP_CONTENT)`」。**这是按 spec 授权的防御性修法，不是实测定位**：静态排查已排除 `TouchDelegate`（0 命中）、`translationX`（只在 SwipePageLayout / PlayerActivity）、`dialog_background` inset（两版本都是纯 `<shape>`），剩下唯一可疑点是测量时序 → 必须 L2 真机核验，若仍偏说明偏移源不在时序，下一步要在 `fitButtonBar` 里 dump 按钮 bounds 与 `getHitRect` 对比。**结论（26.11.13）**：实测证明偏移源不在时序——老布局的按钮只占按钮行右端、左 144px / 右 30px 是死区，OEM 输入层一偏就点左执行右。最终改成「每个按钮独占一整行」，`fitButtonBars` 那一套连同 `Sheet.bottomButtonBar` 全部删除。
- **#140 长按阈值 + 多选样式**：新增 `util/view/LongPressDelay.kt`（`CARD_MS = 600L`，200→600 的理由写在注释里：手表滑列表误触、落在 500–700 中部、略长于系统 500ms）；`adapter/video/VideoCardHolder.kt:146`、`HistoryVideoCardAdapter.kt:70`、`UserVideoAdapter.kt:71`、`adapter/article/ArticleCardAdapter.kt:65` 全部改用它。多选视觉由「整卡 `alpha=0.45f`」改成 TerminalDialog 那套 glyph：选中 `▸` + 标题主色、未选中 `›`（引导符压到 70%）+ 常态色 —— `VideoCardHolder.kt:61/:184/:299-300`、`VideoCardAdapter.kt:50-68`（关动画的理由改成「glyph+主色交叉淡入会叠字」）；补齐另两处同屏页面：`adapter/video/DownloadAdapter.kt:48-56/:148/:154/:209-216` 与 `adapter/video/CacheListAdapter.kt:70-72/:568-578/:580-600`（缓存页文件夹行与视频行是两个不同 TextView，按 view 各记一份常态色，用 `WeakHashMap` 当键）。
- **#121 私信拦截提示可关**：`util/SettingsKeys.kt:146 PRIVATE_MSG_INTERCEPT_TIP_ENABLE`、`activity/settings/SettingPrefActivity.kt:91` 开关、`activity/message/PrivateMsgActivity.kt:117` 判开关再 `showMsg`、文案 `strings.xml:1255 / :219`。
- **#129 短视频双指缩放 + 拖动**：`activity/video/ShortVideoPlayerActivity.kt:590/596` 新增 `translateX/translateY/lastFocusX/lastFocusY/panning`，`:984 handleTwoFingerPan()`（≥2 指且已放大时先 `requestDisallowInterceptTouchEvent(true)`，少一根手指立刻 `panning=false` 把手势还给「上下滑切视频」），`:1014 focusOf()` / `:1021 resetScale()` / `:1036 applyScale()`（`Matrix.setScale` + `postTranslate`，位移按 `(scale-1)*尺寸/2` 双向夹住，缩回 1 倍归零，holder 复用时 `bind()` 重置）。退出热区按 spec **不做**，双击仍是切播放暂停。
- **#94 缓存页 / 下载列表页全选**：`res/layout/activity_simple_main_refresh.xml:122` 与 `res/layout/activity_simple_refresh.xml:189` 各加一个默认 gone 的按钮；`activity/video/local/LocalListActivity.kt:1008/1117/1140/1147` 新增 `visibleSelectableItems()` / `isAllVisibleSelected()` / `toggleSelectAllVisible()`（只勾当前看得见的，顶层优先未分类视频、没有才退化成文件夹）；`activity/video/local/DownloadListActivity.kt:249/319` 接线 `toggleSelectAll()`；文案 `strings.xml:2014-2015`。

### 9.6 本轮 L2 清单（只能人工判，已同步进 `docs/review/fix-progress.md` §2.1）

播放器：返回键退出（含开关关掉＝旧语义）、锁定态压暗常驻与解锁、缓冲中退出不卡、跳转/倍速后弹幕密度、断网快速失败、播放方向与「横屏模式」一致；下载：真多段视频下完不截断、下到一半强杀后跳过已完成段、失败原因文案、Aria2 分片模式回归、番剧分段下载；消息/搜索：点赞首屏与翻页不再重复、断网恢复不留死锁、角标含点赞、搜索下拉+翻页无重复、清空记录二次确认；专栏/合集：cv 正文可读、两条失败文案分得开、代码块与公式不再露方括号英文、`[上次看到]` 标记与回跳（含划掉 App 后恢复）；UI：弹窗按钮中心与边缘内侧都点得中（改字号再试）、长按 600ms 不误触且能稳定弹出、三页多选样式一致且不再整卡发灰、私信提示开关、双指缩放与拖动不打架、两页全选（含搜索过滤后只勾命中项、空列表提示）。

### 9.7 本轮明确没做的

| 条目 | 原因 |
| --- | --- |
| #120 顶栏全局重构 | P3 且是全局重构（顶栏组件抽取 + 电池百分比统一），回归面比前 17 条加起来还大，等这批验证过再单开一轮 |
| #155 音量偏小 | 按拍板只做**调研**：应用侧只有衰减没有放大（`PlayerVolumePolicy.MAX_PERCENT=100`），无极增益与系统音量**相乘**；`changeVolume()` 只动系统音量、且提示语报的是系统百分比 → 结论已进 §6.21 与 `docs/review/fix-progress.md`，代码未改 |
| #149、#147、#128、#102 | 按拍板：先放着 / 等 `wip/bangumi-playback` 前置 / 保留待复现 |
| #153 | 修复已随 **PR #27 合入 main**（`a39cef97`），其后 `6c4fb89b` / `6532288c` 又补了交接单与设置路径修正；本轮不重复改 |
| 多段 durl 播放器侧自动切段、`<b>/<i>` 样式 | 见 §9.2 / §9.4 的遗留 |

### 9.8 直播文本弹幕发送（#156，P3 补做）

- 新增 `api/LiveDanmakuApi.java`：`POST https://api.live.bilibili.com/msg/send`，body **固定顺序**手拼（不用 `NetWorkUtil.FormData`——底层 `HashMap` 键序不定，无法整串断言），键序与取值**对齐 PiliPlus 的实际请求**（`PiliPlus/lib/http/live.dart:36-78` 的 `LiveHttp.sendLiveMsg`）：`bubble / msg / color / mode / room_type / jumpfrom / reply_mid / reply_attr / replay_dmid / statistics / reply_type / reply_uname / fontsize / rnd / roomid / csrf / csrf_token`。其中 `statistics={"appId":100,"platform":5}` 标明「web 端发弹幕」；`reply_*` 本 App 没有「@某人 / 回弹幕」入口，固定 `0` 与空串。`csrf` 与 `csrf_token` 都给（文档把前者列必要、后者列非必要但同值）。
- **签名**：PiliPlus 那条请求在 query 上带 `web_location=444.8` 并走 wbi 签名，所以这里也签名——`signedSendUrl()` 用 `ConfInfoApi.signWBI("…/msg/send?web_location=444.8")` 生成 `w_rid` / `wts`。签名要现取密钥（内部有 TTL 缓存），取不到时**退回未签名 URL** 继续发：未签名最多被风控拒一次（`RISK_CONTROL` 有明确文案），比「根本发不出去」可恢复。
- `liveHeaders()` 复制全局 `webHeaders` 只把 Referer 换成 `https://live.bilibili.com/`，**绝不原地改共享表**（`webHeaders` 被首页 / 动态共用）。
- 返回码 → 枚举 `Kind`（`SUCCESS / LOCAL_THROTTLED / EMPTY_TEXT / TOO_LONG / NOT_LOGGED_IN / CSRF_INVALID / RATE_LIMITED / RISK_CONTROL / ERROR`），不抛裸异常：`0` 成功、`-101` 未登录、`-111` csrf、`-400` 参数、`1003212` 超长、`10031` 频率过快，另按 `docs/bilibili-api-notes.md` 补 `-352` / `-412` 风控。
- 播放页：`PlayerActivity.kt:4431-4434` 直播分支转 `sendLiveDanmaku`（`:4460-4528`）；本地 3 秒节流（网络失败也消耗，防连点刷屏；时钟回拨则重设基准）、按**码点**数 20 字（20 个 emoji 不误判）；**成功后清空输入框 + 一句「弹幕已发送」轻提示**（对齐 PiliPlus 的 toast；直播间虽然会用 WS 把自己的 `DANMU_MSG` 推回来，但网络差时那条可能延迟甚至丢，静默成功会让用户不知道到底发没发出去）；失败按 `Kind` 给确定文案（8 条进 `strings.xml`）。
- 未改清单、未加域名（直播接口本来走 https）；控制台日志只打 `code/kind/message`，csrf 不进 logcat。
- **未验证**：接口能否真发出去（本机无登录 Cookie）、20 字是否当前真实阈值（靠 `1003212` 兜底）、`web_location=444.8` 与 `statistics` 是否真能降低风控（PiliPlus 用它跑通，不等于本 App 一定过）、Referer 是否被风控认可（报 `-352`/`-412` 就先回退全局 webHeaders）。参数与签名口径已与 PiliPlus 逐项对齐，剩下的都是服务端侧判断。

### 9.9 视频转发到私信（#133，P3 补做）

- `api/PrivateMsgApi.java:273-334`：`buildVideoLink` / `buildVideoShareText`（标题 trim 后超 60 字截断加 `…`，拼「标题 + 换行 + 链接」）/ `buildTextContent`（`JSONObject` 组装防引号注入）。
- 新增 `activity/message/ChooseFriendActivity.kt`（继承 `RefreshListActivity`，白拿下拉刷新 / 空态 / 重试；`loadGeneration` 代次 token 丢弃旧响应；会话列表取 50 条）；`AndroidManifest.xml:693-698` 登记（`exported="false"` + `screenOrientation="locked"`）。
- `adapter/message/PrivateMsgSessionsAdapter.kt:48` 加可选 `onSessionClick`，**故意插在 `onSessionsChanged` 之前**——Kotlin 尾随 lambda 绑最后一个可空函数参数，放后面会让 `MessageActivity:143` 静默绑错、会话页不再刷新。
- `activity/video/info/VideoInfoFragment.kt:153-230` + `:668-678`：转发点击改 `TerminalDialog.menu` 两项（原「转发到动态」逻辑原样搬进 `relayToDynamic()`），选好友后 `sendMsg(..., MSG_TYPE_TEXT, ...)`；失败 / 异常回落「复制链接 + 提示」。
- **发文本不发卡片**：`bilibili-API/docs/message/private_msg.md:1811-1852` 写明只接受 `msg_type` 1/2/5，分享卡片是 7（码 21035「该类消息暂时无法发送」）；此结论按文档推得，**未实测**（无登录态）。
- `adapter/dynamic/DynamicHolder.kt` 的转发入口按范围**没动**；会话列表无翻页参数，会话多于 50 条选不到更早的人（遗留）。
