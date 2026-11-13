# 兄弟分支功能对比（26.11.13）

> 对比对象：`jiabenguiyin/BiliTerminal-Revival`、`PianoEthan/BiliTerminal`、`cyq114514/Re-BiliTerminal`
> 我方基线：RE:哔哩终端 `ReBiliClient`，`main` HEAD `965e9781`，版本 26.11.13
> 范围：**只看「功能有没有 / 功能优不优」**，不做安全与规范审计。三份逐仓原始清单（含更多代码片段）在仓库外：`D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\cmp\`。
> 标注：`[sib]` = 对方路径，`[ours]` = 我方路径，均相对各自仓库根；【事实】= 读过代码确认，【推测】= 未实测推断；**【已复核】**= 本报告合稿时由我（Lead）再核过一遍的条目。

## 0 一句话结论

**没有哪个兄弟分支是我们功能的超集。** 三个仓都是从同一上游（BiliClient 2.9.8 / 26.x）分叉出去的 Java+XML 版本，各自只在一两个方向上做深了：

- **Revival** 深在「运维与合规补丁」——举报、SAF 外置存储、增量更新、Cookie 防掉登录；
- **PianoEthan** 深在「外观引擎」——Material You 动态配色整套（12 个新文件全在 `theme/`），业务域零增量；
- **cyq** 深在「形态扩张」——听歌模式（≈3.4k 行）与第三方番剧源（≈4.6k 行），另有若干手表专属小交互（弹幕屏蔽词、±N 秒 seek、表冠缩放）。

我方的独有能力（连播队列、菜单排序、存储清理、片头片尾跳过五档、隐私模式、软件旋屏、多选下载、创作中心看板、玻璃拟态外观体系等）在这些仓里**多数不存在**，反方向差距比正向差距更大。

## 1 三仓基线

| 项 | Revival | PianoEthan | cyq | 我方 |
| --- | --- | --- | --- | --- |
| HEAD | `60d4559`（2026-11-04） | `3a6083e`（2026-11-07） | `c4935a9`（2026-11-08） | `965e9781` |
| 版本 | 1.2.4 / `2026110401` | 3.1.3-Qx | 1.3.5 | 26.11.13 |
| 语言 | 纯 Java 384 文件 | 纯 Java 319 文件 | 纯 Java 466 文件 / 79,385 行 | Kotlin 424 + Java 145 |
| Activity 数 | 88 | 109（含基类） | 104（含 5 个基类） | 约 120 |
| SDK | 33 / 14 / 26 | 33 / 14 / 26 | 19 / 19 / 26 | 34 / 19 / 34 |
| 分支来源 | 上游 26.x 同源衍生，git 压平成 13 条 | 上游 **2.9.8** 分叉，61 条提交 | 上游 26.x 衍生，applicationId 改为 `...BiliClient.re` | — |

判读差异的三个依据（都直接影响「差距」怎么算）：

1. **Revival 的 git 历史被压平**，`--diff-filter=A` 找不到自创文件，只能靠「类名/资源名/设置 key 差集 + 读实现」判定。
2. **PianoEthan 基线是 2.9.8**，它宣传的多数「新功能」我方在 26.x 线上早就有了（收藏 v3、评论标准分页、评论带图、隐私模式、个人资料编辑、软件旋屏、菜单排序），全量文件名比对后**独有文件只有 43 个，12 个在 `theme/`**。
3. **cyq 与我最像**（同为 26.x 线、同源 UI 骨架），所以它的独有项最多也最值得看，但它 targetSdk 26、applicationId 已分叉，部分改动是为它自己的旧基线打的补丁。

## 2 真实差距总表

下表是合稿去重后的**完整差距清单**，按「我方到底缺不缺」分类。成本/价值按手表端口径：

- 成本 **小** < 半天，**中** 1–3 天，**大** > 3 天；
- 价值看「戴在手腕上的人一天会不会用到」。

### 2.1 播放器与弹幕

| 编号 | 功能 | 来源 | 对方实现 | 我方现状 | 成本 | 价值 |
| --- | --- | --- | --- | --- | --- | --- |
| G01 | **弹幕屏蔽词** | cyq | `[sib] activity/player/PlayerActivity.java:3031-3064` 按 `[,，、;；\s]+` 切词，装载前整条摘除 | **无**（`屏蔽词 / blockWord / blocked_words` 全仓 0 命中）【已复核】 | 小 | 高 |
| G02 | **±N 秒快进/快退按钮** | cyq | `[sib] activity/player/PlayerActivity.java:3266-3272` 底部常驻 `-Ns`/`+Ns`，步长可配 | **无**：`[ours] res/layout/activity_player.xml:785/799` 只有音量 `button_sound_cut/add`【已复核】 | 小 | 高 |
| G03 | **长视频弹幕「时长」上限** | cyq | `[sib] api/DanmakuApi.java:179-218` 按真实时长算段数，未知才退化「连续 3 空段停」并封顶 40 段 | 部分：`[ours] activity/player/PlayerActivity.kt:3088` 仍是 `var estimatedDuration = 3600`，时长取不到时窗口段数按 60 分钟封顶【已复核】 | 小 | 中高 |
| G04 | **表冠缩放画面/图片** | cyq | `[sib] activity/player/PlayerActivity.java:4535-4540`、`[sib] activity/ImageViewerActivity.java:49-56` 6 个开关 + 2 个步长 | 半有：`[ours] ui/widget/RotaryEncoderSupport.kt:88-113` 表冠滚动链路完整，但 `rotaryZoom` 0 命中——**表冠只滚不缩放** | 小–中 | 中 |
| G05 | **播放会话哨兵** | cyq | `[sib] util/PlayerSentinel.java:9-22` 进播放页 `commit()` 写标记，冷启动见标记即判「上次未正常结束」（含 native 崩溃/LMK） | **无**；我方只有进程活着时的停滞看门狗 `[ours] activity/player/PlayerActivity.kt:2642-2659` | 小 | 中 |
| G06 | **封面取色染播放器** | PianoEthan | `[sib] theme/ContentTintHelper.java:16-127` 封面 → `QuantizerCelebi` → `Hct` 派 4 色，两层 LruCache，8 秒超时 | **无**：`[ours] ui/widget/HighEnergyProgressBar.kt:33-38` 取色固定来自 `ColorScheme.PRIMARY`；全仓无 `ColorExtractor` / `Palette` / `dominantColor`【已复核】 | 小–中 | 高 |
| G07 | **高能进度条「弹幕密度」兜底** | Revival | `[sib] util/HighEnergyDataBuilder.java:12-52` 放弃远程 PBP，用已加载弹幕按 10 秒分桶算密度 | 单数据源：`[ours] api/PlayerApi.java:1130-1156` 只走 `bvc.bilivideo.com/pbp/data`，失败只打日志返回 null → 曲线空白【已复核】 | 小 | 中 |
| G08 | 播放器细分手势项补齐 | Revival | `[sib] activity/settings/SettingTerminalPlayerActivity.java:26-143` 40+ 项内置表 | 大部分已有（`[ours] player/PlayerSettingsGroups.kt` 380 行），**未逐项比对**；存疑见 §8 | 中 | 中 |

### 2.2 详情页与内容

| 编号 | 功能 | 来源 | 对方实现 | 我方现状 | 成本 | 价值 |
| --- | --- | --- | --- | --- | --- | --- |
| G09 | **视频举报 + 动态举报** | Revival | `[sib] api/VideoReportApi.java:34-71`（`archive/appeal/tags` + `appeal/v2/submit`，`officialRequired` 转官方页）、`[sib] api/DynamicReportApi.java:14-57`（原因表硬编码 10 条） | **公开构建完全没有举报入口**：`appeal` 0 命中；评论举报走 `OptionalFeatures.replyReportLabel()` 门面，公开侧返回 null 时菜单项不显示 `[ours] adapter/ReplyAdapter.kt:654-695`【已复核】 | 中 | 中（合规） |
| G10 | Bangumi（bgm.tv）番组资料体系 | cyq | `[sib] api/BgmApi.java` 799 行 + 缓存 + `BgmSubjectActivity`，多镜像降级 | **无**（`BgmApi` / `BgmSubject` 0 命中）；番剧资料走 B 站自家 `api/BangumiApi.java` | 中 | 中（**新增第三方域名，需评审**） |
| G11 | 列表内搜索（收藏/历史） | Revival | `[sib] activity/base/SearchableVideoListActivity.java:29-60` 顶部搜索框本地过滤 + 继续分页 | **无**；收藏/历史页只有下拉刷新与排序 | 小 | 低–中 |
| G12 | 详情页「摘要/笔记」入口可隐藏 | cyq | `video_summary_note_enable` 开关 | 半有：`[ours] activity/note/NoteActivity.kt` 有笔记页，入口常驻无开关 | 小 | 低 |
| G13 | 创作中心稿件管理/删除 | Revival | `[sib] api/CreatorArchiveApi.java:15-97` 走 `member.bilibili.com` H5 接口 | 只读看板：`[ours] activity/user/CreativeCenterActivity.kt` 仅渲染统计 | 大 | 低（不建议，见 §4） |

### 2.3 社交与消息

| 编号 | 功能 | 来源 | 对方实现 | 我方现状 | 成本 | 价值 |
| --- | --- | --- | --- | --- | --- | --- |
| G14 | **菜单未读提示的「增量刷新」** | Revival | `[sib] util/MessageUnreadCounter.java:13-107` 前台观察时 60 秒轮询、`MessageUnreadState` 用 account/revision/sequence 丢过期响应 | **已有文字计数、但只在启动时算一次**：`[ours] BiliTerminal.kt:486-509` 启动算 `MESSAGE_UPDATE_NUM` 并触发 `MsgNotifier`；`[ours] activity/MenuActivity.kt:143-206` 把计数拼成「消息 (N)」「动态 (N)」在 onResume 重读 SP，**但 SP 本身不回菜单时刷新**【已复核】 | 小 | 中 |
| G15 | 视频转发到私信卡片 | cyq | `[sib] api/ImGrpcApi.java` 手写 protobuf + gRPC，`msg_type=7` | 只能发链接文本：`[ours] activity/message/PrivateMsgActivity.kt:198` 文本、`:269` 图片 | 大 | 中（不建议，见 §4） |
| G16 | **分享得经验** | cyq | `[sib] api/LikeCoinFavApi.java:60` POST `x/web-interface/share/add` + 当日 `share_exp_aids` 去重 | **无**（`share/add` / `share_exp` 0 命中）；分享走系统 Intent 面板 | 小 | 中 |
| G17 | 私信会话 `account_info` 直取昵称/头像 | PianoEthan | `[sib] api/PrivateMsgApi.java:161-186` `parseSession()` 同响应取 `talkerName/talkerFace` 并补 `maxSeqno/ackSeqno` | 半有：`[ours] api/PrivateMsgApi.java:200` 只判 `account_info` 是否 null，**不解析 name/face**，首屏要靠批量用户信息接口补【已复核】 | 小 | 中高（弱网首屏） |

### 2.4 下载、存储与更新

| 编号 | 功能 | 来源 | 对方实现 | 我方现状 | 成本 | 价值 |
| --- | --- | --- | --- | --- | --- | --- |
| G18 | **SAF 外置存储写入** | Revival | `[sib] util/VideoStorageUtil.java` 357 行，File/DocumentFile 双后端 + 持久化授权 + Range 续传，下载库版本 5 落 `storage_mode/storage_ref` | **无**（`DocumentFile`、`ACTION_OPEN_DOCUMENT_TREE` 0 命中）；`[ours] util/LocalFileProvider.kt` 只做外置卷**只读/分享**，下载只能写私有目录 | 大 | 高（手表 8–32G，建议单独立项） |
| G19 | bsdiff 增量更新 | Revival | `[sib] util/BinaryPatchUtil.java:24-110` 纯 Java BSDIFF40 解包，`UpdateInfoActivity.java:244` 调用 | **无**；`[ours] util/UpdateManager.kt:410/565` 整包下载安装 | 中 | 高（**一半收益在服务端产补丁**） |

### 2.5 外观与设置

| 编号 | 功能 | 来源 | 对方实现 | 我方现状 | 成本 | 价值 |
| --- | --- | --- | --- | --- | --- | --- |
| G20 | **深浅双套背景图 + fitXY/tile + 遮罩色** | PianoEthan | `[sib] theme/model/ThemeManifest.java:9-83`（`image_dark`/`image_light`、4 档适配、`scrim` 0–0.9 + 自定义遮罩色） | 部分：`[ours] ui/appearance/BackgroundImage.kt:53-66` 只有 `FIT_COVER/FIT_CONTAIN`、`SCRIM_PERCENTS=[0,25,50,75]`、单一图片、遮罩色由主题定【已复核】 | 小 | 中高（OLED 省电 + 观感） |
| G21 | **M3 动态配色引擎 + blend + 浅色功能色修正** | PianoEthan | `[sib] theme/SchemeEngine.java:21-92`、`[sib] theme/ThemeManager.java:259-283` | **无**：`[ours] res/values/themes.xml` 86 个 style = 6 套手写主题 × Light/Dark，只能选不能自定义；`[ours] ui/appearance/ColorScheme.kt:57-93` 约 40 个令牌取色表 | 中 | 高（但须连「浅色 tone=40」修正一起抄） |
| G22 | 启动首页可选 | cyq | `[sib] activity/SplashActivity.java:333` 读 `LAUNCH_PAGE` int | **无**（`launch_page` / `启动首页` 0 命中）；我方启动固定进主菜单，另有「问一句续播」 | 小 | 中 |
| G23 | 游客身份 Cookie 兜底 | PianoEthan | `[sib] util/CookieGenerator.java:18-45` 缺则本地生成 `buvid3/4`、`bili_ticket`、`_uuid`、`b_lsid`、`b_nut` | 部分：`[ours] api/CookiesApi.java:89-96` 已有官方 `getbuvid`，`[ours] util/NetWorkUtil.java:348-356` 隐私模式剔登录 Cookie；**缺「从未取到过 buvid 时」的本地兜底** | 小 | 中 |
| G24 | BV ↔ AV 离线互转 | PianoEthan | `[sib] api/BilibiliIDConverter.java:6-30` | **无**（我方直接用接口返回的 aid/bvid） | 小 | 低–中 |
| G25 | 实验室页（实验开关收纳） | PianoEthan | `[sib] activity/settings/SettingLaboratoryActivity.java:27-77` | 能力**全有**（含同名 key `dev_player_rotate_software`，`[ours] util/SettingsKeys.kt:173`），只是散在各处——纯组织偏好 | 小 | 低 |
| G26 | 低电量提醒阈值可配 | cyq | `battery_low_alert_enable` / `battery_low_threshold` | 半有：`[ours] ui/appearance/IslandPolicy.kt:12-43` 有低电量岛事件，无开关/无阈值 | 小 | 中 |
| G27 | 设置项输入上限校验 | PianoEthan | 界面边距 > 30% 拒绝保存 | 半有：`[ours] activity/base/BaseActivity.kt:230` `applyUserPadding()` 有，上限约束未核实 | 小 | 中 |

### 2.6 大件（单独立项，不进本次批量）

| 编号 | 功能 | 来源 | 规模 | 判断 |
| --- | --- | --- | --- | --- |
| G28 | **听歌模式** | cyq | ≈3.4k 行：`[sib] service/AudioPlayerService.java` 1395 行（自持 ijk 纯音频 + MediaSession + 多 CDN 换线 + 预取）、`AudioCacheProxy` loopback 读穿缓存、`LyricProvider`（CC 字幕优先、网易云兜底）、音频胶囊、队列页、断点、音轨选择 | 我方**完全空白**（`听歌` / `AudioPlayerService` / `AudioQueue` / `lyric` 0 命中），只有播放器内「听视频」模式。**手表=耳机遥控器，这是最大的产品空白**。建议分两批：①服务+队列+通知+胶囊；②歌词（涉网易云新域名，单独评审） |
| G29 | 第三方番剧源体系 | cyq | 44 文件 ≈4.6k 行（`source/` 全套：Animeko selector、MacCMS、WebViewSniffer、HLS loopback 代理、规则引擎、源管理 UI 5 页） | 只建议**抄设计约定**（`SourceManager` 三条硬约束、`SourceSearchTask` 逐源回调、`UrlGuard` 白名单），不搬代码：自建网络栈 + 第三方规则 + 合规风险都由我方承担 |
| G30 | 主题包生态（`.btheme`） | PianoEthan | `[sib] theme/BThemeInstaller.java:26-226`（白名单+双遍读 zip+原子替换）、`ThemeSettingsActivity`、作者工具链与 6 个坏包样本 | 不建议：手表上「从文件管理器装 zip + 管理列表」交互过重，且一旦开放格式就是长期兼容负担 |
| G31 | 染色渲染层（ThemeApplier/TintRules） | PianoEthan | `[sib] theme/ThemeApplier.java` 832 行 + `[sib] theme/TintRules.java` 295 行 | 不建议移植（与我方「静态 XML + `appearance_version` recreate」路线正面冲突）；**但 `TintRules` 295 行值得当「哪些色值不能 token 化」的坑点清单读一遍** |
| G32 | 内置 WebView 浏览器壳 | Revival | `[sib] activity/browser/BrowserActivity.java:35-120` | 不建议：小屏浏览体验差，且对方那套的可移植部分几乎只剩壳（中继部分见 §4） |

## 3 我方有、对方做得更细的地方

这些不是「缺功能」，是**同一功能对方某一维度更细**，按值不值得改排序。

| 编号 | 项 | 对方做法 | 我方现状 | 建议 |
| --- | --- | --- | --- | --- |
| O1 | 弹幕条数保护 | — | **我方更完整**：`[ours] player/DanmakuLoadPolicy.kt` 纯状态机（`AUTO_LOAD_LIMIT=5000`、失败开放、手动强制加载优先），新版链路先查总数、XML 链路用 `DanmakuXmlCounter` 流式计数后熔断（`[ours] activity/player/PlayerActivity.kt:3079/3133-3216`）【已复核】 | **无需移植**（见 §5 误报） |
| O2 | 片头片尾跳过语义 | `[sib] util/SegmentSkipMode.java:13-30` 五档 = 怎么跳（含 `MANUAL` 区间内常驻按钮），`SegmentSkipController.java:114-157` 四道防抖闸 | 我方五档 = **跳哪一段**（`[ours] player/SkipOpEdPrefs.kt` 0关/1头/2尾/3都跳/4询问）+ 双数据源，功能面更宽 | 只吸收 `MANUAL` 一种语义（小，中） |
| O3 | 收藏夹头部「跟手」 | `[sib] .../FavoriteVideoListActivity.java:307-343` 浮层 + `translationY` 1:1 跟手 | `[ours] .../FavoriteVideoListActivity.kt:230-239` 滚动即隐藏（`setupAutoHideBars`） | 手感优化，看是否有人抱怨排序栏消失太快（小，中） |
| O4 | 高能条数据源 | 见 G07 | 见 G07 | **建议做**（小，中） |
| O5 | Cookie 合并白名单 | `[sib] util/CookieMergeUtil.java:10-40` 鉴权 5 个 key 不被响应覆盖、空值即删除 | **无**（`mergeCookie / CookieMerge` 0 命中） | 建议做：掉登录是这类客户端最常见的反馈（小，中） |
| O6 | 华为/鸿蒙默认软解 + ABI 回退 + 低内存降倍速 | `[sib] util/PlayerCompatibilityUtil.java:14-63` | `[ours] player/IjkNativeLibs.kt` 无 `huawei/harmony` 命中，只能手动切软解 | 建议做：抽成 `player/*Policy.kt` 风格的纯函数策略（小，中） |
| O7 | 全局转场动画开关 | `[sib] util/PageMotion.java:9-22` | 只有 `[ours] activity/InstanceActivity.kt:24` 一处 `overridePendingTransition` | 建议做：落在 `BaseActivity`（小，中） |
| O8 | 回前台 seek 去抖 750ms | `[sib] util/ForegroundRestorePolicy.java:5-22` | 未见等价阈值【推测】直接 seek | 小，低–中 |
| O9 | 图片去 `@1024w_1024h` 缩放后缀 | `[sib] util/BilibiliImageUrlUtil.java:11-38` | 0 命中；我方按控件尺寸请求 | 小，低（手表分辨率低） |
| O10 | 列表请求取消封装 | `[sib] util/RequestCancellation.java:8-28` 一页一个句柄 + `generation` 丢弃过期结果 | 手写 `.cancel()` 22 处 | 抽基类，小，中（弱网卡顿） |
| O11 | 隐私模式覆盖面自查 | 对方把隐私分支手动铺到播放器/直播请求头（反而说明是「一处开关多处手改」） | `[ours] util/NetWorkUtil.java:348-356` 已实现且更完整，但**播放器/直播取流的 header 是否都走游客 Cookie 未核** | **先自查**（见 §8），这不是功能差距而是承诺一致性 |

## 4 不建议移植（合并三份，附理由）

| 项 | 来源 | 理由 |
| --- | --- | --- |
| 热配置 + 自建 B 站中继 | Revival | 配置地址、信任主机、RSA 公钥全写死 `jp.031030.xyz`，且把用户 Cookie 转发到 `compat/v1/bili`——与我方「只走 https + 四域明文白名单」红线直接冲突 |
| WebView 中继代理 | Revival | 只为上面那套服务；剥离后只剩 §2.6 G32 的低价值壳 |
| DeepSeek 自带 Key 对话 | Revival | 手表输入/输出都是灾难，还要用户自备第三方 Key；我方已有系统级 AI 字幕 |
| App 引流 + 绑定弹窗 | Revival | 目的是把用户导去另一个 App，与产品方向相反 |
| ExoPlayer 第二引擎 | Revival | 我方是单 ijk + 分层封装，再加 394 行 Exo 会变成双引擎双倍适配面（弹幕/字幕/手势/连播全分叉），手表分辨率下收益不可见 |
| 创作中心稿件管理/删除 | Revival | 手表删稿场景罕见 + `member.bilibili.com` H5 接口风控风险高 |
| 「夜深了」提醒 | Revival | 氛围项，不排期 |
| 手写 protobuf/gRPC 私信卡片 | cyq | 协议随时变、我方无 grpc/protobuf 依赖（`app/build.gradle` 里早已移除），坏了是静默失败 |
| 第三方源体系整套 | cyq | 见 G29 |
| 全局主题配色横切改造 / `NEW_UI_DESIGN` 双主题 | cyq | 我方没有「旧主题」可回退，纯历史包袱 |
| `.btheme` 生态 + ThemeApplier 全量 | PianoEthan | 见 G30/G31 |
| Gson 全量反序列化重构 | PianoEthan | 用户零感知，混入 Gson 会让两条解析风格并存 |
| 低版本兼容层（minSdk 14 的补丁） | PianoEthan | 我方 minSdk 19，且 `image_request_jpg` 已有 |
| 自研 Adapter/ViewHolder 骨架 | PianoEthan | 与我方 `RefreshListActivity` 体系重复 |
| 「精简在线服务」路线（删公告/更新/反馈） | PianoEthan | 我方这些是产品能力，不是负担 |
| 三连图标重绘 | PianoEthan | 作者自述「纯个人喜好」，审美分叉不是功能 |

## 5 已核对推翻的误报（重要）

分仓清单里有几条**看起来最值得做、实际我方已经有了**。合稿时逐条复核后推翻，避免按错误结论排期：

| 误报 | 原判断 | 复核结论 |
| --- | --- | --- |
| **大弹幕量保护（5000 条）** | Revival §1.1 列为「我方缺」，并进了它的 Top 2 | **我方已有且更完整**：`[ours] player/DanmakuLoadPolicy.kt`（阈值 5000、失败开放、手动强制加载、换分P 重置）+ `[ours] activity/player/PlayerActivity.kt:3079/3133-3216/7166-7182` 接线与提示文案 + 设置开关 `[ours] player/PlayerSettingsGroups.kt:384-389`。对方只有一处 `shouldLoad()` 判定 |
| **全局未读红点** | Revival §1.6 列为「缺」，并列为它的 Top 1（理由「手表没通知生态」） | **我方有文字版**：`[ours] BiliTerminal.kt:486-509` 启动时算互动+私信未读总数并写 `MESSAGE_UPDATE_NUM`（还带「只增才弹通知」逻辑），`[ours] activity/MenuActivity.kt:143-206` 渲染成「消息 (N)」「动态 (N)」。真差距只剩「回菜单时增量刷新」+「红点样式」→ 归为 G14 |
| 软件旋屏兼容方案 | PianoEthan 一度记为缺口 | **我方有，且 key 同名** `dev_player_rotate_software`：`[ours] activity/player/PlayerActivity.kt:3686-3702` `applySoftwareRotationLayout()` |
| 缓存歌单连播 / av-bv 转换 | Revival 关键字法初判「缺」 | 我方 `[ours] player/PlayQueue.kt:19-53` 与 BV/AV 相关实现已覆盖（后者名不同） |
| 无极调音 / 空降弹幕 / 弹幕磁盘缓存 / 音频焦点 | 旧审计（对方 1.2.3 基线）列为缺 | cyq 分仓复核：我方**四项均已有** |

**方法论教训**：关键字差集法对「改了名的等价实现」会误判，对「读了文件但没读实现」更会误判（大弹幕量保护那条就是对方 teammate 看到 `DanmakuLoadPolicy.kt` 82 行却当成「加载时机策略」）。所以本报告里凡是要排期的条目，都补了 `[ours]` 的行号——**排期前按行号再确认一次成本半小时，比返工一天便宜**。

## 6 排期建议

### 第一批：小成本、独立、零架构冲突（建议一次做掉）

| 序 | 条目 | 成本 | 落点 |
| --- | --- | --- | --- |
| 1 | G01 弹幕屏蔽词 | 小 | 必须挂在 `[ours] player/DanmakuManager.appendSegments` 与窗口补拉入口，**不能只在首窗口 prepared 做一次**（我方是窗口式分段追加） |
| 2 | G02 ±N 秒 seek | 小 | 建议做成长按音量键或并入锁定态控制条，不新占屏宽 |
| 3 | G07 高能条弹幕密度兜底 | 小 | `[ours] api/PlayerApi.java:1130-1156` 返回无效数据时，用已加载弹幕按 10 秒分桶算纯函数兜底 |
| 4 | G06 封面取色染播放器 | 小–中 | 唯一决策点：引不引 `material-color-utilities`（不引则用 HSV 取主色，效果约七成） |
| 5 | O5 Cookie 合并白名单 + O6 华为软解 + O7 转场开关 | 小 | 三条都是「设备兼容/稳健性」类，可一批做，各落在既有 policy/BaseActivity 扩展点 |
| 6 | G16 分享得经验 + G22 启动首页可选 | 小 | 都是单点设置/单接口 |

### 第二批：需要动一处已有链路，仍然可控

| 序 | 条目 | 成本 | 注意 |
| --- | --- | --- | --- |
| 7 | G03 长视频弹幕时长 | 小 | 先修「时长来源」（详情页/分P duration 传进播放页优先于现场采样），再放开窗口段数 |
| 8 | G09 视频+动态举报 | 中 | 两个接口共 128 行、共用一个原因选择弹窗；这是公开构建唯一的合规空洞 |
| 9 | G20 深浅双背景图 + 适配档 + 遮罩色 | 小 | 不碰主题架构，只扩 `AppearanceManager.Appearance` 快照与设置页 |
| 10 | G14 未读计数回菜单刷新 | 小 | 在 `MenuActivity.onResume` 触发一次轻量重查（不驻后台），可选加红点样式 |
| 11 | G17 私信 account_info 合并 + O1 MANUAL 跳过 + G26 低电量阈值 | 小 | 三条互不相干，都是小改 |

### 第三批：单独立项

| 序 | 条目 | 说明 |
| --- | --- | --- |
| 12 | G28 听歌模式 | 最大产品空白，≈3.4k 行；先定「音频焦点策略（独占还是混音）」与「通知渠道共存」，歌词网易云兜底单独评审 |
| 13 | G18 SAF 外置存储写入 | 要动 `service/download/` 整套与数据库字段，建议单独立项 |
| 14 | G19 bsdiff 增量更新 | 客户端只补解包 + 版本不匹配回整包；收益一半在服务端产补丁，建议与发布流水线一起规划 |
| 15 | G21 M3 动态配色 | 保留静态 XML 作兜底，动态配色作为「第 7 套：自定义」叠加在 `ColorScheme` 层，绕开 832 行渲染层；须先解决 API 19–23 降级 |

## 7 存疑与待验（不要当结论用）

1. **G03 我方是否真的截断**：`estimatedDuration = 3600` 只在 `ijkPlayer.duration` 取不到时才生效，而 `downdanmuNew()` 的调用时机是否总在 `prepared` 之后**未逐行确认**——需用一个 >60 分钟视频真机验。
2. **隐私模式是否覆盖播放器/直播取流头**：`[ours] util/NetWorkUtil.java:348-356` 已存在，但 ijk 播放器 header 是否也走游客 Cookie 未核；若不覆盖，这是「承诺与实现不一致」，比任何功能差距都该先查。
3. **G08 播放器 40+ 设置项到底几项真缺**：只做了关键字比对，没有逐项在设备上对照我方设置页。
4. **我方 dpi 缩放下的底部按钮裁切**（cyq F13 `fitBottomButtons`）：我方 `[ours] activity/settings/setup/SetupUIActivity.kt:27/83` 有 dpi 设置，但 dpi 1.5/2.0 下底栏是否溢出**未验证**——对方为此专门重写了排布算法。
5. **私信刷新去重**：对方 1.3.4-fix 加了 `msgSeqno` 去重 + `refreshInFlight` 互斥（定时器与发送回调并发会重复追加）；我方 `[ours] activity/message/PrivateMsgActivity.kt` 是否有等价保护未核——若无，这是「偶发重复消息」的现成补丁。
6. **PianoEthan 那批 2.9.8 时代 bug 修复我方是否都独立修过**：已确认必修的（热门 `data.list`、私信 `account_info` 取反、评论少图、`readString` 关流）我方都修过；未核的是「回复我的页面无昵称」「回复消息显示错乱」「动态详情 crash」「CatchActivity span 越界」「ImageViewer 打开崩溃」五条，建议另开半天的「按 commit message 关键词 grep 我方代码」核对。
7. **高能条 PBP 是否仍可用**：我方注释记录 2026-10 服务端改行为、加 `r=loader` 后恢复。若 PBP 已彻底下线，G07 就从「增益」变成「必需」。
8. **对方自研部分的实测可用性无法验证**：本机没有它们的 Android 构建环境，Revival 的 `CreatorArchiveApi`（H5 风控）、`BinaryPatchUtil`（依赖服务端产补丁）、cyq 的 >66 分钟弹幕修复，都只读到代码，**是否真机跑通未知**。

## 8 附件

三份逐仓原始清单（更细的代码片段、每条的对方/我方对照与成本推算）：

- `D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\cmp\BiliTerminal-Revival.md`（33 KB，11 条差距 / 8 条优化 / 7 条不建议）
- `D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\cmp\BiliTerminal-PianoEthan.md`（48 KB，12 条主题系统拆解 / 5 条优化 / 9 条不建议 / 43 个独有文件清单）
- `D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\cmp\Re-BiliTerminal-cyq.md`（36 KB，F1–F14 / O1–O6 / 6 条不建议 / Top8）

历史对比（本轮之前）：[`fork-comparison-2026-11-06.md`](fork-comparison-2026-11-06.md)、[`compare-with-re-biliterminal-261024.md`](compare-with-re-biliterminal-261024.md)。
