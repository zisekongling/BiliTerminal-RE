# 兄弟分支功能对比报告（26.11.20 复核版）

> 上一版：[`fork-comparison-2026-11-13.md`](fork-comparison-2026-11-13.md)（G01–G32 差距表 / O1–O11 优化 / 16 条判死 / 三批排期）。
> 本版**不重写**那 32 条，只做三件事：① 复核三仓有没有新东西；② 核对我方这一周的位移（首批 9 项是否真落地、有没有被新版播放器覆盖掉）；③ 把「还没做」的挑出来，逐条给现状坐标与成本。
> 结论标注：**【事实】**＝本机命令/源码实测；**【推测】**＝按代码推断、未在设备上验证。

---

## 0. 一句话结论

**三仓自 11-13 起一个提交都没有，我方这批差距仍然成立；而这一周的变化全在我们自己这边——首批 9 项已实现，且被新版播放器接住。真正还剩的活是 11 条小项 + 5 条中项 + 听歌模式一个大件，没有新的「对方有我们完全没有」的域。**

---

## 1. 三仓基线（26.11.20 实测）

| 仓库 | 本地克隆 | 基线提交 | origin/main 现在 | 基线之后新提交 |
| --- | --- | --- | --- | --- |
| `jiabenguiyin/BiliTerminal-Revival` | `fork-analysis/BiliTerminal-Revival` | `60d4559` | `60d4559` | **0** |
| `PianoEthan/BiliTerminal` | `fork-analysis/BiliTerminal-PianoEthan` | `3a6083e` | `3a6083e` | **0** |
| `cyq114514/Re-BiliTerminal` | `fork-analysis/Re-BiliTerminal-cyq` | `c4935a9` | `c4935a9` | **0** |

【事实】`git fetch origin` 后 `git rev-list --count <基线>..origin/main` 三次都是 0。三仓仍是纯 Java + XML，没有 Kotlin 化、也没有新增业务包。

**含义**：11-13 版报告里的逐条代码坐标（`path:line`）全部仍然有效，不需要重新逐行核对；本次只核对**我方**。

---

## 2. 我方这一周的位移

【事实】仓库 `main` 现为 `03263e96`，`appVersionDate = "26.11.18"`，`AndroidManifest` 120 个 activity 节点；自首批提交 `460f56f9` 之后另有 **247 个提交**（含本会话的转发修复）。

### 2.1 本会话首批 9 项：全部落地，且没被新版播放器绕过

| 项 | 落点 | 新版播放器（实验开关）是否接住 |
| --- | --- | --- |
| 弹幕屏蔽词 | `player/DanmakuBlockWordPolicy.kt` + `DanmakuBlockWordParser.kt` + `DanmakuManager.appendSegmentInternal` | 是——过滤在 `DanmakuManager` 内部，与 UI 版本无关 |
| ±N 秒快退/快进 | `player/PlayerSeekStepPolicy.kt` + `PlayerButtons.SEEK_BACK/SEEK_FORWARD` | 是——`player/PlayerNewUiController.kt:2001-2029` 有「更多」九宫格的文字格映射 |
| 高能条弹幕密度兜底 | `player/DanmakuDensityHighEnergy.kt` + `PlayerActivity.applyDensityHighEnergyFallback()`（:7413） | 是——数据仍写进 `HighEnergyProgressBar`，新版由 `PlayerNewProgressRenderer` 按同一 `highEnergyStepSeconds` 铺 |
| 进度条封面取色 | `player/CoverTintPolicy.kt` + `HighEnergyProgressBar.setAccentColor` | 是——`PlayerNewUiController:854-855` 同时转给 `arc` 与 `progressRenderer`（圆屏另有 `PlayerArcProgressView.setAccentColor`） |
| 长视频弹幕时长回填 | `DanmakuStreamLoader.updateDuration()` + `PlayerActivity.onPrepared` | 是——与 UI 无关 |
| Cookie 合并白名单 | `util/CookieMerge.kt` + `NetWorkUtil.saveCookiesLocked` | 是——网络层 |
| 华为/鸿蒙默认软解 + ABI 闸 | `player/PlayerCompatPolicy.kt` + `IjkNativeLibs.ensureLoaded` | 是——起播前 |
| 全局转场动画开关 | `util/PageMotion.kt` + `BaseActivity` | 是——109 个走 `BaseActivity` 的页面 |
| 未读角标回菜单刷新 | `util/MessageUnreadRefresh.kt` + `MenuActivity.onResume` | 是——主菜单 |

【事实】九个源文件都在（行数与 11-13 时一致），`PlayerActivity.kt` 里 `text_seek_back` / `SEEK_BACK` / `seekByStep` / `applyCoverTint` / `setAccentColor` / `updateDuration` / `applyDensityHighEnergyFallback` 全部命中。

### 2.2 这一周仓库里的其它变化（不是本会话做的）

【事实】`git log 460f56f9..HEAD` 里有：新版播放器界面（`335440e0` 等，`player/PlayerNewUiController.kt` 2334 行、`PlayerNewUiFormSpec.kt` 522 行）、播放器界面形态手动切换（`95755b54`/`393ddae4`）、圆屏按真实表盘缩放（`6f6c0043`）、高能波形按真实时间轴铺（`829095b9`）、下载断点续传（`4024bfca`）、表冠三轮修复 + 兼容模式（`08f89c65`/`9a8c1b69`/`43129c32`）、Debug 包页面直操控与控件树调试桥。

**对报告的影响**：§3 里凡是「播放器 UI」相关的能力，判断基准要改成**新版播放器**（`PLAYER_UI_FORM` / 实验开关）；旧 `PlayerActivity` 的按钮池仍是真身，新版通过 `PlayerButtons` 注册表复用同一批视图。

---

## 3. 剩余清单（逐条给现状坐标）

状态口径：✅ 已有／已做；❌ 没有；⚠️ 部分有。成本：**S** ≤1 天、**M** 2–4 天、**L** >1 周（手表端单人节奏）。

### 3.1 播放器域

| # | 能力 | 状态 | 我方现状坐标 | 成本 | 手表端价值 |
| --- | --- | --- | --- | --- | --- |
| R1 | 表冠缩放画面（cyq F4） | ❌ | `util/SettingsKeys.kt` 只有 `RotaryEncoderSupport` 的兼容/灵敏度三件套，无 zoom 键；`RotaryEncoderSupport.kt` 现只做滚动/焦点 | M | 高（旋钮是手表唯一顺手的连续输入） |
| R2 | 崩溃兜底哨兵（cyq F5 `PlayerSentinel`） | ❌ | 全仓 0 命中；`bailOnNativeFailure()` 只处理 java 层失败 | S | 中（native 崩溃/被 LMK 杀进不了 Java handler，只能靠「上次未正常退出」标记） |
| R3 | 视频举报（Revival `VideoReportApi`） | ❌ | `appeal` 全仓 0 命中；评论举报走 `OptionalFeatures` 门面 | M | 低-中（合规向，需用户拍板） |
| R4 | 动态举报（Revival `DynamicReportApi`） | ❌ | `dynamic_report` 0 命中 | S | 低 |

### 3.2 详情页 / 列表

| # | 能力 | 状态 | 我方现状坐标 | 成本 | 手表端价值 |
| --- | --- | --- | --- | --- | --- |
| R5 | 列表内搜索（Revival） | ❌ | 本地/缓存/收藏三页都只有顶部全局搜索 | S-M | 中（收藏夹几百条时只能翻） |
| R6 | 创作中心稿件管理（Revival） | ⚠️ | `activity/user/CreativeCenterActivity.kt` 76 行，只读三项统计（`CreativeCenterApi.getVideoStat()` / `getBeUPTime()`）；**无稿件列表/删除** | M | 低 |
| R7 | 私信会话同响应直取昵称头像（piano O1） | ❌ | `api/PrivateMsgApi.java:223-252` 的 `parseSessionsList` 只读 `talker_id` / `last_msg` / `unread_count` / `top_ts` / `session_ts`，**不读 name/face** | S | 中（少一跳请求，列表出得更快） |
| R8 | 收藏夹头部跟手折叠（cyq O3） | ❌ | `FavoriteVideoListActivity` 无 `AppBarLayout`/collapse 相关命中 | S | 低 |

### 3.3 消息与未读

| # | 能力 | 状态 | 我方现状坐标 | 成本 | 手表端价值 |
| --- | --- | --- | --- | --- | --- |
| R9 | 动态角标刷新 | ❌ | 本轮只做了「消息」；`MenuActivity` 的动态计数仍只在冷启动更新 | S | 中（与消息角标对称） |
| R10 | 全局未读红点样式（Revival） | ⚠️ | 有「消息 (N)」「动态 (N)」文字计数，无红点 | S | 低 |
| R11 | 夜深了提醒 | ✅ | `SplashActivity` 启动通知串行（免责声明 → 夜深了），文案 `uitext_appinfoapi_003` | — | — |

### 3.4 存储与更新

| # | 能力 | 状态 | 我方现状坐标 | 成本 | 手表端价值 |
| --- | --- | --- | --- | --- | --- |
| R12 | SAF 外置存储写入（Revival） | ❌ | `DocumentsContract` / `storage_mode` 0 命中；`FileUtil` 仍全走 `java.io.File` | L | 中（手表存储小，外置卡是刚需，但改造面覆盖下载/缓存/播放） |
| R13 | bsdiff 增量更新（Revival） | ❌ | `bsdiff` 0 命中；更新走整包 | M | 中（手表流量敏感） |

### 3.5 外观

| # | 能力 | 状态 | 我方现状坐标 | 成本 | 手表端价值 |
| --- | --- | --- | --- | --- | --- |
| R14 | M3 动态配色 + 自定义种子色（piano F1–F4） | ❌ | `app/build.gradle:358` 只有 `material:1.12.0`，无 `material-color-utilities`/`palette`；主题是 6 套静态 XML × 明暗 | M | 中（但**必须连「浅色功能色 tone=40」一起抄**，否则白底对比度不够） |
| R15 | 深浅双套背景图 + `fitXY`/`tile` + 遮罩可调（piano F6） | ⚠️ | `ui/appearance/BackgroundImage.kt:53-63` 只有 `cover`/`contain`、单图、`DEFAULT_SCRIM=50` | S-M | 中 |
| R16 | 转场动画开关 | ✅ | `util/PageMotion.kt` + 外观设置项 | — | — |
| R17 | 启动首页可选（cyq F11） | ❌ | `launch_page` 0 命中 | S | 低-中 |
| R18 | 分享得经验（cyq F8） | ❌ | `share_exp` 0 命中 | S | 低（每天一次，收益小） |

### 3.6 大件与判死

| # | 能力 | 状态 | 说明 | 成本 |
| --- | --- | --- | --- | --- |
| R19 | 听歌模式（cyq F10） | ❌（有设计稿） | 设计稿：[`../superpowers/specs/2026-11-13-audio-mode.md`](../superpowers/specs/2026-11-13-audio-mode.md)。结论是**新建** `service/AudioPlayerService.kt`（不复用 `PlaybackService`，它靠 `WeakReference<PlayerActivity>` 反查且无 MediaSession） | L（分 M1–M4） |
| — | 第三方番剧源体系（cyq F14，44 文件 ≈4.6k 行） | 判死 | 与「只走 https + 域名白名单」红线冲突，只值得抄它的 `SourceManager` 设计约定 | — |
| — | `.btheme` 主题包生态 / ThemeApplier 832 行（piano） | 判死 | 与我方静态 XML + `recreate` 路线正面冲突 | — |
| — | ExoPlayer 二引擎 / WebView 代理外壳 / 自建中继热配置（Revival） | 判死 | 见 11-13 版 §4 | — |

---

## 4. 与 11-13 版的差异

- **新增结论**：R2（崩溃哨兵）在 11-13 版里排得偏后，本轮复核后建议提前——这一周仓库连出三笔 native/短视崩溃修复（`03263e96`/`f4c0b602`/`54de1a8f`），说明「崩了没人知道」是当前真实痛点，而 `PlayerSentinel` 只要几十行就能给出下一次崩溃的现场。【推测】
- **口径修正**：R15 从「小」上调为「S-M」——`BackgroundImage` 已经有三档遮罩百分比与 `AppearanceManager` 版本号机制，加第二张图要同时改设置页与 `appearance_version` 递增路径。
- **降级**：R18（分享得经验）从「第二批」降为「可选」——每天一次的收益对手表场景太薄。
- **不变**：11-13 版 §5「已核对推翻的 5 条误报」（大弹幕量保护、未读计数、软件旋屏、无极调音等）继续有效，本轮未发现新误报。

---

## 5. 建议排期

### 第二批（小项，一次构建能全绿）

R9 动态角标刷新、R7 私信昵称头像直取、R2 崩溃哨兵、R5 列表内搜索、R17 启动首页。五条都是「新纯函数 + 一处接线 + JVM 单测」，不动播放器内核。

### 第三批（中项，逐条独立提交）

R1 表冠缩放（要接 `RotaryEncoderSupport`，与刚做的兼容模式共存）、R15 双背景图、R14 M3 动态配色（含 tone=40 修正）、R13 bsdiff、R6 创作中心稿件管理。

### 大件（要单独立项）

R19 听歌模式 M1：先做「收藏夹/本地列表长按 → 听歌」，复用 `PlayQueue` + `PlaySessionStore`，请求 `AUDIOFOCUS_GAIN` 并补 `ACTION_AUDIO_BECOMING_NOISY`（现在拔耳机直接外放）。R12 SAF 外置存储单独排。

---

## 6. 需要拍板的地方

1. **R3/R4 举报**要不要做：这是**向 B 站提交内容**的对外动作，且要处理「官方已受理 / 需要网页申诉 / 重复提交」三种回执；上一轮选题时被跳过。
2. **R19 听歌模式**的四个前置：音频焦点（推荐 `AUDIOFOCUS_GAIN`）／队列存档（推荐复用 `PlayQueue` + `PlaySessionStore`）／loopback 读穿缓存（推荐不做，只做地址预取）／网易云歌词兜底（推荐不做，只用 CC 字幕）。
3. **R12 SAF** 值不值得：会牵动下载、缓存、播放三条链，收益集中在「手表 8–16G 存储 + 外置卡」这批用户。
4. **R1 表冠缩放**的交互口径：转动直接缩放（可能与滚动冲突）／长按后转动才缩放。

---

## 7. 附件

- 逐仓原始清单（11-13 横扫，仍有效）：[`forks/2026-11-13/revival.md`](forks/2026-11-13/revival.md)、[`piano-ethan.md`](forks/2026-11-13/piano-ethan.md)、[`cyq.md`](forks/2026-11-13/cyq.md)
- 首批 8 项的人工验收单：[`manual-test-guide-261113-fork-port.md`](manual-test-guide-261113-fork-port.md)
- 听歌模式设计稿：[`../superpowers/specs/2026-11-13-audio-mode.md`](../superpowers/specs/2026-11-13-audio-mode.md)
