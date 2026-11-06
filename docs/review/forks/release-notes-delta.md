# 三兄弟分支 release 更新日志比对：代码差异分析的漏项与判错

> 对象：`jiabenguiyin/BiliTerminal-Revival`（v1.2.4）、`PianoEthan/BiliTerminal`（v3.1.3-Qx）、`cyq114514/Re-BiliTerminal`（v1.3.0）。
> 基线：我方 `ReBiliClient` 26.11.06.1 工作树 + 已有四份分析（`revival.md`、`piano-ethan.md`、`cyq.md`、`fork-comparison-2026-11-06.md`）。
> 本文只做只读比对，不改源码、不改已有分报告。

## 1. 方法与来源

### 1.1 日志来源清单

| # | 来源 | 取到的内容 | 可信度与用途 |
| --- | --- | --- | --- |
| 1 | GitHub Releases API（三仓库 `releases?per_page=100`） | Revival 3 条（v1.2.2／1.2.3／1.2.4）、PianoEthan 9 条（2.9.8fix 至 v3.1.3-Qx）、cyq 全部 Release 正文 | 官方发布口径，作为版本清单主骨架【事实】 |
| 2 | 仓库内应用内更新日志 | Revival `app/src/main/res/values/strings.xml:262-267`（`update_log_items` 4 条，= 1.2.4）；PianoEthan 同文件 `:260-263`（2 条）；cyq 该数组不存在，改用来源 3 | 用户口径，最权威；cyq 走另一套机制 |
| 3 | cyq 仓库 `docs/更新日志.md`（1690 行，含版本索引表 `:9-37` 与每版技术根因）＋ `site/changelog.html` | 38 个版本的「用户视角 + 技术要点」双栏索引；1.3.0 深度说明 | 三家里最完整，1.2.5→1.3.0 逐版可核【事实】 |
| 4 | Revival 官网 `https://jp.031030.xyz/`（更新日志／公告／Beta 区） | 1.2.4 公告、**1.2.5-BETA 完整更新说明（2026-11-05）**、1.2.3 公告全文 | 1.2.5-BETA 未进 GitHub Releases、也未进本地 clone，只有官网有【事实】 |
| 5 | README 与提交日志补充 | Revival `readme.md:56-63`、`SAF_PATCH_NOTES.md`；PianoEthan `readme.md:23`、60 条提交信息；cyq 提交信息与 `docs/audit/` | 用来补「只有 tag 没有日志」与核实日志真伪【事实】 |

### 1.2 口径与前提

- 每条结论标【事实】（本轮读过代码／提交／发布正文）或【推测】（合理外推，未验证）。
- 代码证据一律给「文件:行」，行号按本轮读到的三份 clone 与我方 26.11.06 工作树；cyq 的 `docs/更新日志.md` 行号按其当前版本。
- **时效性前提（很重要）**：Revival 仓库 HEAD 停在 1.2.4（2026-11-04，13 个压缩提交），官网 1.2.5-BETA 的代码**没有入库**，所以 1.2.5-BETA 的条目只能核到「日志层面」，无法给代码行号；PianoEthan v3.1.3 的 Release 正文是空的（只有 Full Changelog 链接），其 `update_log_items` 是本轮唯一用户口径；cyq 旧版本（1.0.x–1.2.4）只有索引表，未逐版重读技术段落。
- 对照的报告范围：三份分报告 + 汇总报告的 §3／§4／§5／§7。凡「报告完全没提某个日志条目」才进 §3.1「真缺口」或 §3.2「非缺口但报告漏」；凡「报告说无／说已有」与实际代码冲突的进 §4。

## 2. 三个分支的版本日志清单

### 2.1 Revival（3 个 GitHub Release + 1 个未入库 BETA）

| 版本 | 日期 | 类别 | 原始要点（中文转述） | 来源 |
| --- | --- | --- | --- | --- |
| 1.2.2 / 2026102001 | 2026-10-20 | 修复 | 弱网下下载失败重试不再从头下（保留已有进度）；播放器四周圆角屏安全区，控件与置顶弹幕不被边角遮挡；置顶弹幕过长自动换行；修复动态类型筛选「全部」指向全部；修复播放页按钮显隐延迟 | Release 正文 |
| 1.2.2 / 2026102001 | 2026-10-20 | 优化 | 继续优化 Android 4.x、低内存设备、智能手表稳定性 | Release 正文 |
| 1.2.3 / 2026103003 | 2026-10-30 | 功能 | 「全部收藏」：收藏夹与历史记录内搜索、翻页；普通收藏夹长按编辑名称／简介／公开状态／删除；动态新增评论入口与评论数，操作栏适配不同屏幕；有管理权限时置顶视频、置顶动态评论；评论点赞、点踩、删除状态跨页面同步；实验室「App 个性化推荐」；大弹幕量保护（默认关，>5000 不自动加载，可手动开）；直播滚动弹幕默认两倍停留时长且可独立调节；手势防误触开关（锁定后轻点画面显隐锁图标）；播放器顶部与控件布局适配小屏／方屏 | Release 正文；官网公告同文 |
| 1.2.3 / 2026103003 | 2026-10-30 | 优化 | Android 4.x／低内存／手表稳定性；Android 5.0+ 按钮触摸水波纹 | Release 正文 |
| 1.2.4 / 2026110401 | 2026-11-04 | 功能 | 多来源连播（UP 主投稿／视频合集／在线收藏夹／缓存歌单，顺序倒序随机单曲循环＋耳机切歌＋进度续播）；收藏视频排序；历史记录长按及多选删除；番剧片头片尾跳过、同系列切季、续看；本人弹幕边框；精细音量；移动数据与 Wi-Fi 独立画质；长按倍速提示尺寸／开关可调；手势切换视频；存储空间清理；评论及视频／动态举报；创作中心本人稿件管理与删除；消息红点开关；关注与黑名单管理；统一弹窗与搜索分类交互；大图查看优化 | `strings.xml:262-267`；Release 正文；`readme.md:56-63` |
| 1.2.4 / 2026110401 | 2026-11-04 | 修复 | 修复退出后重启（回到旧页面）、下载失败提示不明、搜索安全验证误报、界面输入异常 | Release 正文 |
| 1.2.5-BETA / 2026110502 | 2026-11-05 | 功能 | 创作中心新增稿件搜索、单稿详情、评论快捷入口；「我的」新增经验变化记录（增减／原因／时间）；消息页「一键已读」＋返回自动刷新开关＋系统通知查看＋失败重试＋当前账号已读同步；收藏夹视频手动排序（保存顺序可用于连播） | 官网公告与 Beta 区**【事实：无源码入库，仅日志】** |
| 1.2.5-BETA / 2026110502 | 2026-11-05 | 修复／优化 | 统一常用按钮触摸反馈（5.0+ 用原生水波纹）；修复视频详情缺少争议信息时可能崩溃 | 同上 |

### 2.2 PianoEthan（9 个 Release + strings 当前日志）

| 版本 | 日期 | 类别 | 原始要点（中文转述） | 来源 |
| --- | --- | --- | --- | --- |
| 2.9.8fix_Unofficial | 2026-06-05 | 功能删减／修复 | 删掉数百行在线服务与界面代码（在线更新、公告列表、崩溃日志上传等）；修复动态 API 数据类型错误 | Release 正文 |
| 3.0.0-Qx | 2026-06-22 | 功能／修复 | 新增隐私模式（开启后看视频不记录历史）；脆弱解析逻辑大重构（手工 JSON→Gson）；修复原版未登录无法看视频；搜索界面表冠滚动异常（换组件） | Release 正文 |
| 3.0.2-Qx | 2026-06-29 | 修复 | 热门无法加载（issue #6）；楼中楼加载失败 Indicator 不消失；未登录获取评论失败提示登录 | Release 正文 |
| 3.0.3-Qx | 2026-07-13 | 优化 | 修部分已知问题；提升 Gradle 版本（新版 Android Studio 不支持旧 Gradle） | Release 正文 |
| 3.0.4-Qx | 2026-07-22 | 重构 | 参照 PiliPlus 重构私信系统与评论跳转系统（PR #7，作者 cyq114514） | Release 正文 |
| 3.1.0-Qx | 2026-10-18 | 功能／修复 | 横向边距设置恢复生效；移除 `Map.of`／`List.of`／`String.join`／`Objects` 等高版本 API（修 Android 10／8.0／4.4 及以下兼容崩溃）；修主题染色在 Android 5.0–9.0 的 `NoSuchMethodError`；修播放器销毁资源泄漏、EventBus 线程、本地缓存列表刷新、下载服务进程重建续传；导出组件缺入参崩溃＋收紧测试页与深链入口；移除 Cookie／refresh_token 日志、过滤第三方播放器 Cookie、关闭 `allowBackup`、移除多余权限；WebP 转 JPG、补 RTL | Release 正文 |
| 3.1.1-Qx | 2026-10-18 | 修复 | 全屏页顶部双重下移、按钮不再吃封面取色；修主题取色 | Release 正文；提交 `a4241e8` |
| 3.1.2-Qx | 2026-10-31 | 功能／修复／工程 | 内容取色收窄到播放器；主题层补语义令牌与灰阶；默认深色恢复历史配色；直播弹幕回调改主线程；修正播放器销毁释放顺序；`FileUtil.readString` 关闭文件流；依赖仓库改 https、凭据不入日志；新增 CI 自动打包签名与主题染色单测 | Release 正文 |
| 3.1.3-Qx | 2026-11-01 | 功能／修复 | Release 正文为空（仅 Full Changelog 链接）；实际唯一提交 `415edc6` 是「收藏接口更新到 v3」 | Release 正文＋`git log v3.1.2..v3.1.3` |
| （应用内当前日志） | — | 功能 | 增加热搜排行榜；增加番剧追番按钮 | `strings.xml:260-263` |

补充：`git log` 里另有未被任何 Release 提到的用户可见项——个人资料编辑（`a78e877`）、评论带图发送（`277f356`）、隐私模式、三连图标更换（`3685a0e`）、`CatchActivity` setSpan 越界修复（`9a13aa4`）、`ImageViewerActivity` 打开崩溃修复（`322a0be`）、「回复我的」无昵称与回复消息错乱修复（`d1d85a0`／`ab9c070`）【事实：提交标题】。

### 2.3 cyq（38 个版本；以 `docs/更新日志.md:9-37` 索引为准）

| 版本段 | 类别 | 原始要点（中文转述） |
| --- | --- | --- |
| 1.0.0／1.0.2／1.0.2-fix1 | 功能／修复 | 分支起点修番剧进度无法保存；播放中自动保存进度；修上报静默失效与卡死 |
| 1.1.0／-fix1 | 功能 | 动态增强：带图发布、投票、置顶、话题页 |
| 1.1.1／-fix／1.1.2 | 功能／修复 | 番剧选集；评论区卡死与多 P 进度丢失修复；后台播放＋通知栏遥控；搜索番剧分类 |
| 1.1.3／1.1.4／1.1.5 | 功能 | 多账号切换；应用内检查更新（`update.json`＋断点续传＋镜像）；切后台重进回到原页面（页面栈序列化） |
| 1.1.6／-fix／1.1.7 | 功能／优化 | 新版美学（氛围背景／圆形主菜单／按压反馈）；播放稳定性（长时观看闪退）；流畅度与省流（热搜榜、记住倍速、收藏夹公开／私有、图片缓存、WBI 接口） |
| 1.1.8／1.1.9／1.2.0／1.2.0fix | 功能／修复 | 修内置播放器设置项互相串值；私信可发图片；「记住退出页面」默认关；番剧「换集串进度／读不到进度」彻底修（改按集字段）；经验／硬币记录本机累积 |
| 1.2.1／1.2.1fix／1.2.2／1.2.3／1.2.3fix | 功能／修复 | 崩溃上报改自建服务（默认开，首次弹窗说明）；崩溃页黑屏修复；界面调大后弹窗不被裁；播放页与冷启动变快；退出／崩溃重启不再回旧页面；选背景图与专栏页闪退；老设备播放页闪退与弹幕漂移；开屏提速；「关闭界面过渡动画」「启动时检查更新」开关；搜索页显示热搜（默认关）、启动时等待请求完成（默认关） |
| 1.2.4 | 功能／修复 | 竖屏短视频；「仅音频下载」修复；头像上传闪退；登录信息不再发给第三方线路；下载队列卡死／假取消；`-111` 不再当掉登录；release 包补教程插图 |
| 1.2.5 | 功能／修复 | 视频转发到私信（卡片）＋分享面板与 App 授权；修短视频退出后再进直接退出、授权二维码大字号被截断 |
| 1.2.6 | 功能／修复 | 启动密码锁；桌面图标伪装成「计算器」；搜索分类拖拽排序＋结果排序；设置项搜索；主题配色；修伪装开关打不开、关锁后开关不同步、弹幕周期性清屏 |
| 1.2.7／1.2.8 | 修复／优化 | 搜索结果出来后顶部「排序」按钮不出现；短视频右划退出、相关推荐空白、返回后背景消失、高能进度条无波形、弹幕周期性整层消失；排序入口改搜索框左侧图标；切页与冷启动固定开销优化 |
| 1.2.9 | 功能／修复／优化 | 番剧分区（推荐／追番／索引／新番四页签）；收藏夹「听全部」音频播放＋队列页＋歌词＋缓存代理＋批量下载；分网络默认画质；播放页跳到指定时间＋横滑灵敏度；AI 视频摘要；只读视频笔记；本地缓存断点续播；全量 UI 审计整改（23 个 P1）；用户反馈八项；收藏夹崩溃修复、背景光晕黑点、顶部白条 |
| 1.3.0 | 功能／修复 | 私信表情；卡片长按菜单（稍后再看／不感兴趣／拉黑）；性别＋IP 属地；互关徽章；分享得经验；回声洞重置；听歌页按钮图标化；修横滑必崩（SP 类型混用）、崩溃上报通道、检查更新卡死、悬浮球残留、短视频音量不受无极调音、返回热区变小、联合投稿列表报错 |

## 3. 漏掉项

### 3.1 真缺口：日志写了、报告没提、我方确实缺（或明显弱）

| # | 功能 | 来源（版本＋要点） | 对方代码证据 | 我方现状（本轮核实） | 建议 | 成本 |
| --- | --- | --- | --- | --- | --- | --- |
| D1 | 收藏夹与历史内**搜索＋翻页**，以及跨收藏夹的「全部收藏」聚合流 | Revival 1.2.3「新增『全部收藏』，支持收藏夹和历史记录内搜索、翻页」 | `[F] api/FavoriteApi.java:120-148`（`keyword`／`allFolders` 进入 URL）；`[F] api/AllFavoriteVideosPager.java:17-18,101-145`（多夹流按时间归并、分页游标可序列化）；`[F] activity/user/favorite/FavoriteVideoListActivity.java:28,40-45,62-75`（`allFolders` 时页面名与搜索提示切换） | 【事实】收藏列表只有服务端三档排序与多选删除：`api/FavoriteApi.java:260-267`（`getFolderVideosNew` 仅 `pn/ps/order`，无 `keyword`／`all`）；`activity/user/favorite/FavoriteVideoListActivity.kt:28,47-59`（列表只有 `ArrayList`＋排序，无搜索框／聚合游标） | 批次 2（收藏域整批做；先做搜索，再做聚合流） | M |
| D2 | 动态卡片上的**评论入口＋评论数**（无评论时显示「评论」），操作栏按宽度自适应 | Revival 1.2.3「动态新增评论入口和评论数，操作栏自适应不同屏幕」 | `[F] adapter/dynamic/DynamicHolder.java:63,109,408-423`（`commentCount` 文案、`toWan` 缩写、点击带 `commentId`／`commentType` 跳评论页）；`[F] ui/widget/DynamicActionLayout.java:10-30`（按卡片实测宽度算三按钮字号／图标／间距） | 【事实】`adapter/dynamic/` 四个文件（`DynamicAdapter.kt`、`DynamicHolder.kt`、`UserDynamicAdapter.kt`、`TopicDynamicAdapter.kt`）grep「评论／reply_count／comment」**0 命中**，卡片没有评论入口与评论数 | 批次 1（单文件＋一个布局） | S |
| D3 | 评论点赞／点踩／删除状态**跨页面同步**（同一 rpid 在详情页与列表页保持一致） | Revival 1.2.3「评论点赞、点踩和删除状态跨页面同步」 | `[F] util/ReplyStateStore.java:9,28`（按 账号＋类型＋oid＋rpid 的容量受限状态表）；`[F] util/ReplyStateSync.java:12,25-26,44-53`（`observe`／`publish` 走 EventBus）；`[F] api/ReplyApi.java:95-109,154-180`（拉取时 `beginRead`＋`observe`）；`[F] adapter/ReplyAdapter.java:659-687`（`applyLatestReplyStates`） | 【事实】我方可 grep `ReplyStateStore`／`ReplyStateSync`／`跨页` 均 0 命中；只有 `api/ReplyApi.java:589-601,680-695` 的置顶与 `adapter/ReplyAdapter.kt:576-626` 的长按菜单，点赞/删除只在当前列表内改 | 批次 2（评论域；纯逻辑可 JVM 单测） | M |
| D4 | **评论举报**（Revival 1.2.4 明写「评论及视频／动态举报」，报告只列了视频／动态） | Revival 1.2.4 更新日志 | `[F] api/ReplyApi.java:438`（`reportReply(oid,rpid,type,reason,content,account)`）；`[F] adapter/ReplyAdapter.java:498,509-524`（举报弹窗＋原因与补充说明）；`[F] ui/ReplyReportDialog.java` | 【事实】公开侧只有可选能力门面：`util/OptionalFeatures.kt:92-115`（`replyReportLabel`／`replyReport` 反射），`adapter/ReplyAdapter.kt:607-610` 在门面返回非空时才显示「举报」；**公开仓库内无实现，是否可用取决于私有注入**。报告（`revival.md:114-115`）只写了视频/动态举报 | 批次 3／不排期（写操作需用户拍板；已有私有实现则公开侧无需再补） | S（接入）／— |
| D5 | 直播滚动弹幕**停留时长倍率**（默认 2 倍，1.0–3.0 可调） | Revival 1.2.3「直播滚动弹幕默认两倍停留时长，并支持独立调节」 | `[F] util/PlayerSettingsUtil.java:14`（`LIVE_DANMAKU_DURATION_FACTOR`）、`:30`（默认 2.0）、`:68-71`（`getDanmakuDurationFactor(live)` 与普通弹幕速度分离） | 【事实】我方无该键也无该语义：grep「停留」「duration factor」在 `player/` 无命中；`player/DanmakuManager.kt:30-39` 只有 `speedFactor` 一个速度参数 | 批次 1（一个设置项＋一处取值，可 JVM 单测档位） | S |
| D6 | 消息页**「一键已读」＋返回自动刷新开关＋系统通知查看＋已读同步** | Revival 1.2.5-BETA（官网公告，代码未入库） | 无代码证据：本地 clone HEAD=1.2.4，`git log` 无对应提交；1.2.4 里只有 `[F] activity/message/PrivateMsgActivity.java:129` 的「自动已读失败」日志 | 【事实】我方消息相关只有 `activity/message/PrivateMsgActivity.kt:105` 的自动已读；`activity/message/` 下 grep「已读／一键／markRead」无批量已读能力 | 批次 2（需先实测消息已读接口的批量参数） | M |
| D7 | 收藏夹视频**手动排序**（保存的顺序可用于连播） | Revival 1.2.5-BETA（官网公告，代码未入库） | 无代码证据（同上，未入库）；1.2.4 的 `[F] util/FavoriteVideoOrder.java` 只是三种服务端排序＋按账号记忆 | 【事实】我方只有服务端三档排序：`api/FavoriteApi.java:28-33`（`mtime`／`view`／`pubtime`）、`activity/user/favorite/FavoriteVideoListActivity.kt:50-55` | 批次 2（需要本地顺序存档＋连播读取） | M |
| D8 | 创作中心：**稿件搜索、单稿详情、评论快捷入口** | Revival 1.2.5-BETA（官网公告） | 无代码证据（未入库）；1.2.4 的稿件管理在 `[F] api/CreatorArchiveApi.java`、`[F] activity/user/CreatorArchiveActivity.java` | 【事实】我方无稿件管理（`activity/user/CreativeCenterActivity.kt` 只有统计）；报告已把「稿件管理／删除」列暂缓，但未提搜索／详情／评论入口 | 批次 3／随稿件管理一起评估 | M |
| D9 | 视频详情**争议信息缺失时崩溃**的修复（同类风险见 §5） | Revival 1.2.5-BETA（官网公告） | 无代码证据（未入库）；1.2.4 的 `[F] api/VideoInfoApi.java:239-240` 同样只用 `getString("argue_msg")` | 【事实】我方 `api/VideoInfoApi.java:231-232` 是「`argue_info` 存在就 `getString("argue_msg")`」，字段缺失会抛 `JSONException` | 批次 1（一行 `optString`） | S |
| D10 | 搜索页内嵌**热搜**（默认关） | cyq 1.2.3fix「新增『搜索页显示热搜』（默认关）」 | `[F]` cyq 代码未逐行读，证据为 `docs/更新日志.md:18`（索引表同版技术要点「两个新开关」）【事实：日志；未读码】 | 【事实】我方有独立热搜页与首页跑马灯（`api/HotSearchApi.kt:11-25`、`activity/video/HotSearchActivity.kt:22`、`activity/MenuActivity.kt:55`），但 `activity/search/SearchActivity.kt` grep「热搜／hot」0 命中 | 不排期（与既有独立入口重复，价值低） | S |

### 3.2 报告漏了、但核实后**不是缺口**（避免下轮重复造轮子）

| # | 日志条目 | 来源 | 我方已有（证据） |
| --- | --- | --- | --- |
| N1 | 收藏夹长按编辑（名称／简介／公开状态）＋删除 | Revival 1.2.3 | `activity/user/favorite/FavoriteFolderEditActivity.kt:26-37`（信息编辑＋批量删除＋清空失效＋删除夹，且先拉 `folder/info` 回填、读不到禁用保存，比对方更稳）；`api/FavoriteApi.java:523,537` |
| N2 | 手势防误触开关、锁定后轻点显隐锁图标 | Revival 1.2.3 | `activity/player/PlayerLockPolicy.kt:4`（触摸锁定状态机）＋`activity/player/PlayerActivity.kt:1368`；我方另有「锁定按钮开关」（`docs/review/fix-progress.md` 六十八） |
| N3 | 隐私模式：看视频不记录历史 | PianoEthan 3.0.0 | `api/HistoryApi.java:44,74`（`PRIVACY_MODE` 开则直接 return，不上报历史）＋`api/VideoInfoApi.java:33,43`；我方语义还多一层「以游客身份请求隐藏点赞／收藏」（`util/NetWorkUtil.java:346`） |
| N4 | 界面边距（横向／纵向百分比，圆屏适配） | PianoEthan 3.1.0 | `activity/settings/SettingGroupActivity.kt:312-319`（`PADDING_H`／`PADDING_V`）＋`activity/base/BaseActivity.kt:111,193-232`＋`util/view/RoundInsets.kt:24`（上限 0–30 与对方一致） |
| N5 | 个人资料编辑 | PianoEthan 提交 `a78e877` | `activity/user/EditProfileActivity.kt:25`（我方另有 `MySpaceMenu.kt:35` 入口） |
| N6 | 经验变化记录（经验增减／原因／时间） | Revival 1.2.5-BETA | `api/ExpLogApi.java`＋`model/ExpLog.java`＋`activity/user/ExpLogActivity.kt`（我方已有整页） |
| N7 | 热搜排行榜 | PianoEthan `update_log_items` | 实为「全站排行榜」菜单入口＋`menu_ranking` 开关（`[F] activity/MenuActivity.java:62,128`、`activity/settings/SettingMenuActivity.java:33-50`，默认关）；我方 `activity/video/RankingActivity.kt`＋`api/RankingApi.java` 已有 |
| N8 | 记住倍速 | cyq 1.1.7 | `util/SettingsKeys.kt:222-223,245`（`player_default_speed` 支持「沿用上次」＋`player_last_speed`）、`player/PlayerDefaults.kt:40-43` |
| N9 | 导出组件缺入参崩溃、收紧测试页与深链入口 | PianoEthan 3.1.0 | `activity/GetIntentActivity.kt:18,33-56`（`DeepLinkPolicy` 白名单，注释即「审计 P2-7」）；Release 仅 2 个导出组件 |
| N10 | 过滤第三方播放器 Cookie | PianoEthan 3.1.0 | `api/PlayerApi.java:615`（「允许跨进程传给外部播放器的 Cookie 键（白名单）」） |
| N11 | 下载服务进程重建后续传 | PianoEthan 3.1.0 | `service/DownloadServiceRecovery.kt:4-42`＋`service/DownloadService.kt:385-450`（`START_STICKY` 空 Intent 的续跑决策） |
| N12 | 设置项搜索 | cyq 1.2.6 | `activity/settings/SettingsIndex.kt`（`docs/review/compare-with-re-biliterminal-261024.md` 已记） |

## 4. 判错项与「日志吹牛／未入库」

### 4.1 判错项（报告说「无／弱」，核实后不成立）

| # | 报告原文 | 实际（本轮核实） | 修正建议 |
| --- | --- | --- | --- |
| E1 | `revival.md:94` 与 `fork-comparison-2026-11-06.md:221`：「直播弹幕显示发送者昵称——我方无（我方直播弹幕无昵称选项）」 | 【事实】我方有该设置且已接线：`util/SettingsKeys.kt:170`（`PLAYER_DANMAKU_SHOW_SENDER`）、`activity/settings/SettingTerminalPlayerActivity.kt:164-165`（开关「显示直播弹幕发送者」）、`activity/player/PlayerDanmuClientListener.kt:161`（按该键拼接昵称） | 该行改为「我方已有」；不再进批次 |
| E2 | `revival.md:93`：「强制滚动弹幕——我方无（弹幕状态只有行数/速度/字号/透明度/合并/重叠）」 | 【事实】我方有设置项但**没接线**：`util/SettingsKeys.kt:169`（`PLAYER_DANMAKU_FORCE_R2L`）、`activity/settings/SettingTerminalPlayerActivity.kt:162-163`（开关「强制为滚动弹幕」）、`activity/settings/SettingsIndex.kt:194`、`activity/settings/SettingText.kt:200`；`app/src/main/java` 全量 grep `forceR2L` 除上述设置项自身与两处文案索引外**无任何消费方**，`player/` 与 DanmakuFlameMaster 侧都不读这个键——用户打开开关不会生效（与 cyq 1.2.9 的「番剧开关没接上」同类缺陷） | 改成「有开关、不生效」，并按 §6 放进批次 1 顺手修；比「无」更严重 |
| E3 | `piano-ethan.md:115`、`fork-comparison-2026-11-06.md:115`：「PianoEthan 只做主题……其余 60 个提交是 API 14–18 兼容补丁与全量 Gson 迁移」 | 【事实】60 个提交里另有用户可见能力：个人资料编辑（`a78e877`）、评论带图（`277f356`）、收藏 v3 与契约单测（`415edc6`）、隐私模式（`3685a0e`）、`CatchActivity` setSpan 修复（`9a13aa4`）、`ImageViewerActivity` 崩溃修复（`322a0be`）、回复消息错乱修复（`ab9c070`）。其中多数我方已对等或更强（见 §3.2），所以「画像」结论不必推翻，但「其余只有兼容补丁」这句不成立 | 摘要里把「只做主题」收敛为「主增量是主题引擎，另有若干上游修复与个人资料编辑，均不对我方构成增量」 |

### 4.2 日志写了、当前代码里没有（吹牛／已回滚／未入库）

| # | 条目 | 结论 |
| --- | --- | --- |
| F1 | PianoEthan `update_log_items`：「增加番剧追番按钮」 | 【事实】代码未见：`activity/video/info/BangumiInfoFragment.java:162-164` 只展示 `series_follow`（追番数）文案，`btn_follow`／`follow_bangumi` 等在 `app/src/main` grep 0 命中；v3.1.3 唯一提交是收藏 v3，与追番无关。属「日志超前写入或未实现」 |
| F2 | PianoEthan v3.1.3 Release 正文 | 正文为空（仅 Full Changelog 链接）；`strings` 的两条日志与 v3.1.3 提交不对应（见 F1 与 N7）。**不能把 v3.1.3 当功能版本** |
| F3 | Revival 1.2.5-BETA 全部条目 | 【事实】官网已发（`2026110502`），但 GitHub Releases 与本地 clone 都没有对应代码；属「已发布、源码未同步」，不是吹牛，但本轮无法验证实现（尤其消息一键已读与手动排序是否真落地） |

## 5. 我方同类风险（修复类条目 → 我方可疑文件）

| # | 对方修复（版本） | 我方可疑点（本轮读到的线索） |
| --- | --- | --- |
| S1 | 视频详情缺少争议信息时崩溃（Revival 1.2.5-BETA） | `api/VideoInfoApi.java:231-232`：判了 `argue_info` 非空却用 `getString("argue_msg")`，字段缺失即抛 `JSONException`；与对方修的是同一处 |
| S2 | `CatchActivity` 的 `SpannableString.setSpan` 越界（PianoEthan `9a13aa4`）；cyq 1.2.1 崩溃页自崩同类 | `activity/CatchActivity.kt:168`：`setSpan(..., 0, 8, ...)` 的 **8 是写死的**，靠「可能的崩溃原因：」正好 8 字；一旦改文案（如加字）就变成越界崩溃——崩溃页自己崩 |
| S3 | 高能进度条「打开开关没有波形」（cyq 1.2.8：`view_points` 无 `score` 字段，改本地弹幕密度） | 我方仍走 `bvc.bilivideo.com/pbp/data`：`api/PlayerApi.java:845-871`（带 `r=loader`＋Referer，并兼容新旧两种响应形态），`activity/player/PlayerActivity.kt:4369-4394`。四份报告都没写这条链路的现状；对方已判定「官方数据源失效」并换源，我方这条是否仍有数据**必须实测**（拿 2–3 个视频看 `Logu` 是否有「成功获取 N 个数据点」）【推测：可能已失效但不报错】 |
| S4 | 顶栏返回热区变小（cyq 1.3.0 `17b91d4`） | `res/layout/activity_player.xml:163` 仍是 `drawableStart="@drawable/arrow_back"` 的文本按钮，宽度随右侧电量文本伸缩；报告（`cyq.md:128`）已标「未复核」——本条保留为真机项 |
| S5 | 低版本主题染色 `NoSuchMethodError`／Android 5–9 禁用主题（PianoEthan `33090d9`、`3d79357`） | 我方主题在 `ui/appearance/`＋`values*` 资源层，minSdk 19；报告只写了「MCU 需 API 24」这一面，没写「旧系统降级策略」。若后续引入取色，需要同款「按 API 降级而不是崩」的闸 |
| S6 | SP 类型漂移导致 `ClassCastException`（cyq 1.3.0 横滑必崩；PianoEthan `getFloat`） | 我方横滑灵敏度写入/读取自洽（`activity/player/PlayerSeekPolicies.kt`＋`adapter/SettingsAdapter.kt` 均走字符串，已核）；但 `util/SharedPreferencesUtil.java` 的 `getString` 仍无 `ClassCastException` 保险丝（`cyq.md:165` 已列，批次 1 已排） |

## 6. 建议补进哪批

在汇总报告既有批次不推翻的前提下，增量建议如下（价值／成本口径沿用汇总 §1.3）：

- **批次 1 追加（S，均单文件或双文件）**：D2 动态评论入口＋评论数；D5 直播弹幕停留倍率；D9／S1 争议信息判空；E2 强制滚动弹幕接线（顺手修 bug）。
- **批次 2 追加（M，按域整批）**：D1 收藏夹搜索＋「全部收藏」聚合（与既有「历史多选删除」同域）；D3 评论状态跨页同步；D7 收藏夹手动排序；D6 消息一键已读（先实测接口）。
- **批次 3／随域评估**：D8 创作中心稿件搜索／详情／评论入口（跟稿件管理一起）；D4 评论举报（写操作，先确认私有实现是否已覆盖，公开侧不做）；S3 高能进度条数据源（先实测再决定是否换成本地弹幕密度）；S5 主题 API 降级闸。
- **不排期**：D10 搜索页内嵌热搜（我方已有独立入口）；N 系列全部（已对等）。

一句话：**真缺口 10 条，其中 4 条是 S、可并入批次 1；最值得先做的是「动态评论入口」「直播弹幕停留倍率」「强制滚动弹幕接线」这三件小事**，因为它们同时补日志漏项与修一个已存在的死开关。

## 7. 局限

1. Revival 的 1.2.5-BETA 无源码（GitHub 与 clone 都没有），D6–D9 只能给日志级结论，无法给行号，也无法判断实现是否真的落地（F3）。
2. PianoEthan v3.1.3 的 Release 正文为空，`strings` 又与提交对不上（F1／F2）；本文对 Piano 的版本归属已按 `git log` 校正，但仍可能存在「tag 与代码不完全同步」。
3. cyq 的 38 个版本只逐版读了 `docs/更新日志.md` 的索引表与 1.2.5 以后的技术段；1.0.x–1.2.4 的细节以旧报告（`compare-with-re-biliterminal-261024.md`）为准，未逐条重读代码。
4. 我方现状按 26.11.06 工作树 grep/read；行号会随后续提交漂移，复核时以符号名（如 `PLAYER_DANMAKU_FORCE_R2L`、`ReplyStateStore`）检索更可靠。
5. 评论举报是否可用取决于私有注入（`OptionalFeatures.replyReport` 门面），本文未读私有仓库，D4 的「是否已覆盖」按【推测】处理。
6. 所有「我方缺」均指公开仓库当前代码；未做真机验证（S3 高能进度条、S4 返回热区、D6 消息接口都需要设备或抓包确认）。
