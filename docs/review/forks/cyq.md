# Re-BiliTerminal（cyq114514）1.3.0 增量与可移植性复核

> 本文只做两件事：**(a)** 把旧报告 `docs/review/compare-with-re-biliterminal-261024.md`（针对对方 1.2.3）第 5/6/7/13 章与附录 A/B 里「对方有、我方缺」的**功能类**条目，逐条用我方 **26.11.06.1** 当前代码复核；**(b)** 分析对方 **1.2.5 → 1.3.0**（共 29 个提交）新增的功能与修复。旧报告已覆盖的安全、兼容性、工程规范、健壮性通用结论不在此重复，只在影响可移植性判断时引用。
>
> 两条引用约定：`[对方]` 路径相对 `D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\Re-BiliTerminal-cyq\`（其 `app/src/main/java/com/RobinNotBad/BiliClient/` 前缀省略）；`[我方]` 路径相对 `D:\Users\ASUS\Desktop\DEVELOP\ReBiliClient\`。所有对方行号按 **v1.3.0 工作树（567450c）**、我方行号按 **1c404c3** 核过。

## 1. 身份与基线

### 1.1 双方关系

| 项 | 我方 ReBiliClient | 对方 Re-BiliTerminal（cyq114514） |
| --- | --- | --- |
| 版本 / HEAD | 26.11.06.1 / `1c404c3` | 1.3.0 / `567450c`（tag `v1.3.0`，2026-11-06） |
| 血统 | 上游「哔哩终端」主线，Kotlin 化演进 | 基于上游哔哩终端 **2.9.8** 的独立分叉，纯 Java |
| 包名 | `com.RobinNotBad.BiliClient` | `com.RobinNotBad.BiliClient.re`（可与原版共存） |
| 许可 | GPL-3.0 | GPL-3.0（`LICENSE` 首行 GPLv3）【事实】 |
| SDK / ABI | minSdk 19 / compileSdk 34 / 三 ABI | minSdk 19 / compileSdk 33 / **targetSdk 26** / `abiFilters 'armeabi-v7a','x86'`（无 arm64）【事实】：`[对方] app/build.gradle:11/29/31/43` |
| 工程可测性 | `app/src/test` 134 个 JVM 测试文件 | `app/src/test` **0 个测试文件**；`app/src/main` 731 个文件【事实】 |
| 技术栈差异 | Kotlin 主线 + 部分 Java；自研播放器策略层、下载引擎、教程系统 | 单文件巨型 Activity（`PlayerActivity` 近 5000 行）、无测试、中文硬编码 |

【事实】旧报告已证明两者「同源不同血统」：对方 957 个 `app/src/main` 文件对回我方 1515 个，大量同名文件仅是 Java/Kotlin 转写；本次预计算清单 `fork-analysis/cmp/Re-BiliTerminal-cyq/{only-fork,only-ours,modified}.txt` 只用于定位差异面，未逐文件比对。

### 1.2 1.2.3 → 1.3.0 的发布节奏

| Tag | 日期 | 覆盖提交 | 主要内容 |
| --- | --- | --- | --- |
| v1.2.3 / v1.2.3fix | 10-23 / 10-24 | `c6705d8`（旧报告基线） | — |
| v1.2.4 | 10-24 | `2a4777d`、`6701718`、`b569274`、`cd234c4`、`94709cc` | 竖屏短视频；旧报告 §15 已复核 |
| v1.2.5 | 10-24 | `05e40ed`、`81a1efd` | 转发到私信卡片、App 授权二维码、短视频退出 |
| v1.2.6 / 1.2.7 / 1.2.8 | 11-01 | `acf5e99`(109 文件)、`071d9e5`、`95b1c16`、`c60f64e`、`8cd42eb` 等 | 启动密码锁 + 图标伪装、搜索分类拖拽 + 结果排序、设置项搜索、主题配色、整批 UI/性能审计、高能进度条改数据源 |
| v1.2.9 | 11-05 | `a3a3577`（195 文件） | 听歌域整体上线（快速加载/进度记忆/歌词/下载全部音频）、收藏夹听全部、Wi-Fi 与流量分档清晰度 |
| v1.3.0 | 11-06 | 17 个提交（`3b5a3c6` … `567450c`） | 私信表情、卡片长按菜单、性别/IP 属地、互关徽章、分享得经验、回声洞进度；修横滑必崩/上报通道/更新卡死/短视频音量/返回热区 |

【事实】13 天 7 个 tag，其中 1.2.6–1.2.8 同日三连（补丁式滚动发布）；同期我方 10-24→11-06 有 100+ 提交、5 次发版（`git log --since=2026-10-24`）。对方节奏是「先上功能、下一个小版本集中修」，这与它无测试、靠真机反馈兜底的工程形态一致【推测】。

### 1.3 本次方法与口径

- **增量范围**：`git log 94709cc..HEAD`＝28 提交、318 文件、`+30121/−8881`（`git diff --shortstat`；`git rev-list --count` 复核）。1.2.4 及以前已被旧报告 §15 覆盖，本文只补 1.2.5 起的增量。
- **取证**：逐提交 `git show --name-only` 定位 → 目标文件 read/grep 取行号；我方现状按当前工作树 grep/read，不做「文档说了就算」，文档与代码冲突时以代码为准。
- **结论标记**：每条判断标【事实】（读过代码/提交）或【推测】（合理外推，未验证）。
- **未复核项**统一列在 §7，不参与统计。

## 2. 旧报告遗留缺口复核（§5/§6/§7/§13 + 附录 A/B）

### 2.1 复核范围

旧报告 §13.0 的未修清单共 79 条，其中大部分是安全（§3）、兼容（§4）、规范（§9）、健壮性（§10）、性能（§11）条目，**不属「对方有、可移植」的功能类**，本文不逐条重跑。下表只收 §5.1 功能缺口、§6.2–§6.4、§7.4–§7.6、§8.2 的界面项、§13.3 的功能分批、附录 B 的 readme 宣称中，可判定「对方有、我方缺（或曾缺）」的功能条目。

### 2.2 复核结论表

| # | 旧报告条目 | 旧报告位置 | 我方 26.11.06.1 现状（证据） | 结论 | 若仍缺：移植建议 |
| --- | --- | --- | --- | --- | --- |
| 1 | 无极调音 0~100% | §5.1 P1-1 | 【事实】已实现：`[我方] app/src/main/java/com/RobinNotBad/BiliClient/player/PlayerVolumePolicy.kt:6`、`activity/player/PlayerActivity.kt:2852-2935`、`AndroidManifest` 无关；`docs/FEATURES.md:238` | 已补齐（26.10.24 `13db917`） | — |
| 2 | 片头片尾五档（关/仅头/仅尾/都跳/询问） | §5.1 P1-2 | 【事实】仍是单布尔：`[我方] player/SkipOpEdPrefs.kt:32` `DEFAULT_ENABLED=false`；只有「自动跳过 + 撤回」，无档位选择 | 仍缺 | 把 `SkipOpEdPrefs` 扩成枚举 + 设置页 listChoose；view_point 已在取流响应里拿（`PlayerApi.getVideo`），不必新接口；M |
| 3 | 空降弹幕（点弹幕里的时间戳 seek） | §5.1 P1-3 | 【事实】全仓无 `OnDanmakuClickListener`/`TimestampHighlighter`（grep 0 命中）；只有输入式的「跳到指定时间」`PlayerActivity.kt:1519` + `PlayerJumpTimePolicy`（26.11.02 起，FEATURES.md:213） | 仍缺（已有变体） | 先在 `player/` 加纯函数时间戳正则（可 JVM 单测），再接弹幕层点击；L（触摸坐标换算 + DFM 命中） |
| 4 | 音频焦点 | §5.1 P1-4 | 【事实】无 `requestAudioFocus`（grep 0）。**但对方 1.2.9 已补**：`[对方] activity/player/PlayerActivity.java:368-380`（listener：`LOSS`/`LOSS_TRANSIENT` 暂停，不自动恢复）、`:642`、`:3338-3353`（request/abandon）、`:3482/:3496`（退出/销毁释放） | 仍缺（旧报告当时判「双方都没有」，现已不成立） | 直接搬语义即可：起播 request、失去焦点 pause、`onStop/onDestroy` abandon；S，见 §3.4 |
| 5 | 弹幕磁盘缓存 | §5.1 P1-5 | 【事实】`api/DanmakuApi.java:78` 只有分段拉取（`SEGMENT_SECONDS=360`）与 `getVideoDanmakuSegment`，无落盘缓存 | 仍缺 | 以 `cid+segment` 为键落 `cacheDir`，带 TTL；M |
| 6 | ±N 秒按钮 | §5.1 P2-2 | 【事实】手势已有（`player/PlayerControlDelegate.kt:103-108` `seekForward/seekBackward(10)`，秒数可调）；控制栏仍无 ±N 按钮（FEATURES.md:188-195 按钮清单无此项） | **部分**（有手势、无按钮） | 表盘空间有限，建议做成可关的两枚文字按钮，或直接不补（手势已覆盖） |
| 7 | 播放会话哨兵 | §5.1 P2-3 | 【事实】grep `Sentinel` 0 命中 | 仍缺 | 见 §4 优化点与 §6 第 9 条（对方 `[对方] util/PlayerSentinel.java:10-22` 只留本地的思路值得直接搬） |
| 8 | 收藏夹编辑重置隐私/清空简介（P0） | §6.1 | 【事实】已修：`[我方] api/FavoriteApi.java:192-236` 有 `getFolderInfo`，privacy 由 attr bit0 解析（`:223`），编辑页回填 | 已补齐（并比对方更稳：对方用未文档化的 `privacy` 字段） | — |
| 9 | 番剧取流 v1 → v2 + 每集进度 | §6.2 P1 | 【事实】仍是 v1：`[我方] api/PlayerApi.java:348` `pgc/player/web/playurl`、`:342` `season_type=1` 硬编码；`:371` 注释自认「result 不返回 last_play_*」 | 仍缺 | 改 `pgc/player/web/v2/playurl` + 透传 `playerData.seasonType` + 解析 `watch_progress`；M（我方已有 `PlayerApiPlayUrlTest` 可扩） |
| 10 | 首页推荐分页游标 | §6.3 P1 | 【事实】`[我方] api/RecommendApi.java:37` 只有 `fresh_type`，无 `ps`/`fresh_idx`/`brush` | 仍缺 | 加三参数 + 空页 `setBottom(true)`；S/M |
| 11 | 番剧详情定位上次观看 | §6.4 P2-1 | 【事实】全仓无 `last_ep_id`/`findLastWatchedEpid`（grep 0） | 仍缺 | 低优先；M |
| 12 | 排行榜解析零容错 | §6.4 P2-3 | 【事实】`[我方] api/RankingApi.java:26-37` 仍是 `getJSONObject`/`getString` 链，无 code 判断 | 仍缺 | 统一改 `opt*`；S（并顺手定「api 层只用 opt*」） |
| 13 | 番剧索引/排行入口 | §6.4 P2-7 | 【事实】主菜单 14 项无番剧入口（FEATURES.md:50）；无 `BangumiIndexApi` | 仍缺 | 对方 `api/BangumiIndexApi.java` 可参考，但属新页面；M/L |
| 14 | 动态「仅自己可见/关闭评论」死代码 | §7.4 P1 | 【事实】已接线：`[我方] activity/dynamic/DynamicActivity.kt:169-179` 组装 `privatePub`/`closeComment`（26.10.24 `a0bcbb3`） | 已补齐 | — |
| 15 | 评论列表三层兜底 | §7.5 P1 | 【事实】无 `loadedRpids`/`localPostedReplies`/`FALLBACK_REPLY_TYPES`（grep 0）；`activity/reply/ReplyFragment.kt:289-295` 仍按可见位置插入；`api/ReplyApi.java:708` `getReplyCount` 仍无调用者 | 仍缺 | 去重 + 按 rpid 定位 + 空列表换排序重拉；M，可拆成多个 JVM 单测 |
| 16 | 楼中楼带图入口按 rpid 隐藏 | §7.5 P1 | 【事实】`[我方] activity/reply/WriteReplyActivity.kt:226` 图片入口无 `rpid` 可见性判断；`rpid` 只用于一级评论校验（`:173`）。26.11.02 `d53dcb5` 还去掉了大会员前置拦截 | 仍缺（失败面更直接） | 一行 `setVisibility(rpid == 0 ? VISIBLE : GONE)` + 发送前兜底；S |
| 17 | 定时发布档位/范围校验 | §7.6 P2-1 | 【事实】`SendDynamicActivity.kt:66-71` 收集秒级时间戳；未见「6 分钟~7 天」范围校验（grep 未命中） | 仍缺 | 提交前校验 + 可输入时间；S |
| 18 | 教程管理页写旧键 | §8.1 P1 | 【事实】已修（26.10.24 `35d79a4`），FEATURES.md:790 记录键统一到 `tutorial_ver_<id>` | 已补齐 | — |
| 19 | 界面可配置缺 6 项（过渡动画/背景底图/页面栈恢复等） | §8.2 P2-5 | 【事实】转场仍硬编码：`[我方] activity/base/InstanceActivity.kt:22` `overridePendingTransition(...)`；无背景底图/氛围背景类（只有自研 `ui/appearance/AmbientGlow.kt`）；页面栈恢复见 #22 | 仍缺（部分为我方不同实现） | 过渡动画开关 S；背景底图 M（注意 GPU，对方实测 1.6 倍缩放单帧 972ms）；氛围背景可复用我方 `AmbientGlow` |
| 20 | 无障碍 contentDescription | §8.2 P2-7 | 【事实】`app/src/main/res/layout/*.xml` 仅 8 处 `contentDescription` | 仍缺 | 交互控件补描述，优先播放器/下载/设置；M |
| 21 | 动画缩放诊断 | §8.2 P2-10 | 【事实】grep `ANIMATOR_DURATION_SCALE` 0 命中 | 仍缺 | 关于页读 `Settings.Global.ANIMATOR_DURATION_SCALE`；S |
| 22 | 进程被杀后页面栈恢复 | §10.3 / 附录 B | 【事实】无 `ResumePageUtil`（grep 0）；对方 `[对方] util/ResumePageUtil.java:29-37` | 仍缺 | 见 §4；M |
| 23 | 深色资源 `values-night` | §8.2 P2-12 | 【事实】`app/src/main/res` 下只有 `values`、`values-notnight`、`values-w300dp` | 仍缺（有 notnight 变体） | 加 `values-night` 覆盖色值；S/M |
| 24 | 更新链 SHA-256/镜像 | §3.2 P1 | 【事实】`[我方] util/ApkVerifier.kt:24` 明确写「哈希校验（MD5/SHA-256 写进 Release 元数据）没有采纳」，改用签名 + 包名 + 双源 | 不适用（已决策） | 若要补，须先解决「元数据与包同源」的反驳；不建议本轮动 |
| 25 | 首页推荐 web/app 数据源切换 | 附录 B | 【事实】无 App 签名/Buvid 源（grep `AppSign`/`fresh_idx` 0） | 仍缺 | 对方 `util/AppSign.java`、`AppBuvid.java`、`api/RecommendApi.java`；buvid 获取依赖 App 签名链路，易随风控失效；L，不建议 |

### 2.3 统计

上表共 25 行，逐行结论：

- **已补齐 4 条**：#1 无极调音、#8 收藏夹隐私/简介回填、#14 动态可见范围/关评论、#18 教程键错位。全是 10-24 那批修复的产物，且 #8 的实现比对方更稳（用 attr bit0 而非未文档化的 `privacy`）。
- **仍缺 19 条**：#2、#3、#4、#5、#7、#9、#10、#11、#12、#13、#15、#16、#17、#19、#20、#21、#22、#23、#25。集中在四块：**播放器交互**（片头片尾档位/弹幕空降/弹幕磁盘缓存/会话哨兵/音频焦点）、**番剧链路**（v2 取流与每集进度、详情定位、索引入口）、**社交容错**（评论三层兜底、楼中楼带图、定时校验）、**界面配置与无障碍**（过渡动画/背景/页面栈恢复/无障碍描述/动画缩放诊断/`values-night`），外加**推荐分页与 app 数据源**。
- **部分 1 条**：#6 ±N 秒按钮——手势（`seekForward/seekBackward(10)`、秒数可调）已有，裁掉的只是控制栏按钮。
- **不适用 1 条**：#24 更新链 SHA-256（`util/ApkVerifier.kt:24` 已明确写下不采纳的理由）。

表外另复核了旧报告 §13.2「C 登录态生命周期」的三项，**均已落地**：切号 Cookie 代际隔离（26.11.02 `1a1536e`）、Cookie 进程内周期刷新 + 指数退避（26.11.02 `f06f2b4`）、统一凭据入口 `AccountManager.applyCredentials`（`docs/review/fix-progress.md` §3.112）。这三条不计入上表 25 行。

**一句话结论**：对方 1.2.3 报告里「我方缺、值得补」的功能缺口，经过 10.24→11.06 两个迭代已被消化掉账号/收藏夹/动态可见性/教程这几个高优先级块；**剩下 19 条里真正值得排期的只有 6–7 条**（评论兜底、番剧 v2、推荐分页、片头片尾档位、楼中楼带图、音频焦点、弹幕磁盘缓存），其余多为界面配置与无障碍，属「有余力再做」。

## 3. 1.2.5 → 1.3.0 增量功能清单（按域）

难度口径：**S** ≤ 半个工作日、单文件为主；**M** 1–3 天、跨 2–4 个文件；**L** 一周以上或需新协议/新页面。

### 3.1 私信与消息

| 功能 | 用户价值 | 证据（对方） | 我方现状 | 难度 | 依赖与风险 |
| --- | --- | --- | --- | --- | --- |
| 私信发表情（输入行表情按钮，选中表情码随文本发送） | 高：私信是高频；我方 FEATURES 9.4 明确「目前私信**不支持**发表情」 | 【事实】`[对方] activity/message/PrivateMsgActivity.java:69-105`（`emoteLauncher` + `emote_btn`，`business=im`，失败回落 reply）、`activity/EmoteActivity.java:212-214`（`setResult` 带 `"text"` 表情码） | 【事实】`[我方] api/EmoteApi.java:19-20` 只有 `BUSINESS_REPLY`/`BUSINESS_DYNAMIC`；`adapter/message/PrivateMsgAdapter.kt:119` 已能渲染 emote（`textReplaceEmote`）→ **渲染链路就绪，只缺输入入口** | **S** | 服务端 `emote/user/panel/web?business=im` 是否对网页 Cookie 开放需实测；表情码以 `[name]` 文本入队，服务端按 `msg_type=1` 解析 |
| 视频转发到私信卡片 | 中高：比复制链接好用 | 【事实】`[对方] activity/message/ShareToMsgDialog.java:46-53`（最近会话多选 + 关注列表选人 + 100 字附言，gRPC `msg_type=7` 失败自动降级文本链接）、`api/ImGrpcApi.java:21-38`（手写 gRPC 帧 + protobuf）、`activity/settings/login/AppAuthActivity.java:29-36` 与 `util/AppAuthStore.java:4-10`（TV 扫码换 `access_key`，按 mid 存储） | 【事实】无 `ShareToMsg` 任何实现（grep 0）；`[我方] api/PrivateMsgApi.java:34-36` 只支持 `MSG_TYPE_TEXT/PIC/RETRACT`(1/2/5) | 卡片 **L**／仅文本链接 **M** | 卡片需 `access_key`（网页 Cookie 拿不到）+ 手写 protobuf；私信本身有风控（我方 FEATURES 9.4 已提示「尽量不要用终端发私信」）；建议先做「发送文本+链接」半版 |
| 私信会话列表长按置顶/删除/看主页 | 中 | 1.2.6 更新日志 | 【事实】已有（FEATURES.md:522） | — | 已补齐 |
| 消息卡片点击（老 av 号）解析修正 | 低 | 1.2.5 更新日志 | 【事实】`[我方] model/MessageCard.java` 已存在解析类；具体分支未逐行比对【推测】 | S | 需构造 av 号小的卡片样本 |

### 3.2 视频卡片与详情页

| 功能 | 用户价值 | 证据（对方） | 我方现状 | 难度 | 依赖与风险 |
| --- | --- | --- | --- | --- | --- |
| 卡片默认长按菜单：稍后再看 / 不感兴趣 / 加入黑名单 | 高：列表页就地操作，省一次进详情页 | 【事实】`[对方] adapter/video/VideoCardAdapter.java:159-171`（仅有默认菜单时才接管长按，页面自设监听不受影响）、`:179-226`（`AdaptiveDialog` 单选）、`:242-279`（拉黑二次确认 + `upMid<=0` 时补查 `/x/web-interface/view` 拿 owner）；`api/UserInfoApi.java:355-358` `blockUser(act=5/re_src=11)` | 【事实】我方卡片长按只回调页面自设监听（`adapter/video/VideoCardAdapter.kt` 同源结构）；无「不感兴趣」、无 `blockUser` API（grep 0） | **M** | 「不感兴趣」对方是**本地假实现**（`:206-213` 注释自认「服务端反馈接口属新接口，留待后续」）——不要照搬；拉黑不可逆，必须二次确认 |
| 转发菜单加「分享得经验（每日一次）」 | 中：白拿每日经验 | 【事实】`[对方] activity/video/info/VideoInfoFragment.java:623-637`（转发改三选一）、`:644-682`（本地按 `share_exp_day`+`share_exp_aids` 去重，`-403` 风控/`-101` 失效分别提示）、`api/LikeCoinFavApi.java:54-61`（`share/add` + 实时 csrf） | 【事实】`[我方] activity/video/info/VideoInfoFragment.kt:577` 转发仅「转发动态」、长按复制链接；`api/LikeCoinFavApi.java` 只有 triple/like/coin，无 share | **S** | 经验是否到账由服务端风控决定（对方注释已声明）；我方有 `ExpLogApi` 可查经验记录，形成闭环 |
| 联合投稿详情页列表 NPE 修复 | 低 | 1.3.0 `448bc2d`（列表组件补判空） | 【事实】我方详情页已展示联合投稿徽章与全部 UP 主（FEATURES.md:138、:144） | 不适用 | 实现路径不同，未复现同类崩溃 |
| 自适应弹窗（圆屏/大字号不裁按钮） | 中：我方弹窗在圆屏下靠 `BaseActivity` 收窄，但无统一 `AdaptiveDialog` | 【事实】`[对方] util/AdaptiveDialog.java`（1.3.0 长按菜单、转发菜单、检查更新共用） | 【事实】我方无 `AdaptiveDialog`（grep 0），用系统/Material 弹窗 + 圆屏内接矩形收窄（26.11.02 `99ebede`） | M | 属重构项，收益是交互一致性，非功能缺口；不建议单独排期 |

### 3.3 用户与关系

| 功能 | 用户价值 | 证据（对方） | 我方现状 | 难度 | 依赖与风险 |
| --- | --- | --- | --- | --- | --- |
| 用户详情页显示性别与 IP 属地 | 中高：判断对方身份/地域，且成本极低 | 【事实】`[对方] api/UserInfoApi.java:224-248`（注释明说「只有 `wbi/acc/info` 返回，card 接口没有」，隐私拿不到就空串）、`model/UserInfo.java:51-52`、`adapter/dynamic/UserDynamicAdapter.java:217-224`（`"保密"` 不显示、Holder 复用必须每次重置显隐） | 【事实】`[我方] api/UserInfoApi.java:59` 已经调用 `getUserSpaceInfo(mid)` 拿 `sys_notice`/`live_room`/`contract`——**同一份 JSON 里就有 `sex`/`ip_location`**；`model/UserInfo.java:21` 只有 `followed` | **S** | 隐私设置关闭时字段为空，必须隐藏该行；`UserInfo` 走 Parcelable（`:172-218` 读写），加字段要同步读写序 |
| 关注/粉丝/分组列表标出互相关注 | 中：关系判断 | 【事实】`[对方] api/FollowApi.java:30` `attribute` 字段、`:47/:61/:86` `user.mutualFollow = item.attribute == 6`；`adapter/user/UserListAdapter.java:34`、`:82-87`（仅「看自己的列表」显示粉色「互关」徽章，Holder 复用重置） | 【事实】`[我方] api/FollowApi.java:29-50` 解析 `getFollowingList/getFollowerList`；`model/UserInfo.java` 无 `mutualFollow` | **S** | `attribute` 语义随列表主体变化（看别人列表时是「与对方互关」），必须按对方注释只在本人列表显示 |
| 拉黑 UP 主（`blockUser`） | 中 | 【事实】`[对方] api/UserInfoApi.java:355-358` | 【事实】无（grep 0） | **S** | 写接口，需 csrf；不可逆需确认；另需 `unblock`(act=6) 给回退入口 |

### 3.4 播放器与短视频

| 功能 | 用户价值 | 证据（对方） | 我方现状 | 难度 | 依赖与风险 |
| --- | --- | --- | --- | --- | --- |
| 短视频接入无极调音（软件增益） | 中：短视频声音普遍偏小，手表喇叭更明显 | 【事实】1.3.0 `03105df` 单文件改 `ShortVideoFeedFragment.java`；无极调音本体 `PlayerActivity.java:206`（`volumeGain`）、`:793-795`（读档回填滑条）、`:3030-3048`（`applyVolumeGain` / `persistVolumeGain`，存 `player_volume_gain` 百分数） | 【事实】`[我方] activity/video/ShortVideoPlayerActivity.kt:895-901` 只有系统音量 ±1（`setStreamVolume`）；软件增益只在长视频 `PlayerActivity.kt:2852-2935` + `player/PlayerVolumePolicy.kt:6` | **S** | 复用 `PlayerVolumePolicy.percentToGain/clampPercent` 与 `KEY_GAIN_PERCENT` 存档即可；注意短视频页有独立 `audioManager` 实例与音量悬浮层（`:593`） |
| 播放器抢占音频焦点 | 中高：来电/其他 App 抢音时该暂停就暂停，焦点回来自动续播会打断用户手动暂停 | 【事实】1.2.9 `a3a3577` 新增：`[对方] activity/player/PlayerActivity.java:368-380`（`OnAudioFocusChangeListener`：`LOSS`/`LOSS_TRANSIENT` 一律暂停，注释说明「`AUDIOFOCUS_GAIN` 不自动恢复，避免用户已手动暂停后被抢播」）、`:642`（起播 request）、`:3338-3353`（`requestAudioFocusForPlayback`/`abandonAudioFocusIfNeeded`）、`:3482/:3496`（页面停止/销毁释放） | 【事实】无 `requestAudioFocus`/`AUDIOFOCUS` 任何命中（grep 0）；旧报告 §5.1 P1-4 当时判定「双方都没有」已过时 | **S** | 与「后台/熄屏继续播放」「听视频模式」联动：后台播放时是否仍持有焦点要试；`abandon` 必须放在真的停播路径，否则后台播放会被误掐 |
| 顶栏返回热区固定宽度 | 中：电量文本变长后返回点不到 | 【事实】1.3.0 `17b91d4` 改 `activity_player.xml` + `PlayerActivity.java` | 【事实】`[我方] app/src/main/res/layout/activity_player.xml:163` 用 `drawableStart="@drawable/arrow_back"`；实际宽度是否随电量文本压缩**未复核**【推测】存在同风险 | S | 真机看顶栏电量 3 位数时返回的点击命中区 |
| 横滑灵敏度 `getFloat` 修复 | — | 【事实】1.3.0 `3b5a3c6`：设置页 `input_float` 写入、播放页 `getString` 读，`SharedPreferencesImpl` 抛 `ClassCastException`，改灵敏度后每次横滑必崩 | 【事实】我方同类策略在 `[我方] activity/player/PlayerSeekPolicies.kt:19` `PlayerSwipeSeekScalePolicy`，写入走 `listChoose`→`putString`（`adapter/SettingsAdapter.kt:281-285`），读取 `PlayerActivity.kt:4069-4074` 也是 `getString` → **类型自洽，无此崩溃** | 不适用 | 但「SP 类型漂移」本身是共性坑，见 §4 |
| 听歌页队列/停止胶囊按钮 | 低 | 1.3.0 `a89048f` | 【事实】我方无音频页（grep `AudioPlayerService` 0） | 不适用 | — |
| 音频后台清理即停 / 队列播完收摊 | 低 | 1.3.0 `80d10d3` | 【事实】同上，我方后台播放走自研播放器 + 通知遥控（FEATURES.md:242-243） | 不适用 | — |

### 3.5 搜索

| 功能 | 用户价值 | 证据（对方） | 我方现状 | 难度 | 依赖与风险 |
| --- | --- | --- | --- | --- | --- |
| 搜索结果排序（综合/最多播放/最新发布/最多收藏） | 高：手表上没键盘，排序比换关键词更实用 | 【事实】`[对方] api/SearchApi.java:169-179`（`order` 档位，注释说明只有 `search/type` 链路吃 order、`all/v2` 不支持）、1.2.8 更新日志（入口改搜索框左侧小图标，改过排序图标变主题色） | 【事实】我方搜索设置只有分类开关与分类拖拽排序（FEATURES.md:758）；`api/SearchApi` 无 `order` 参数（grep 未命中） | **M** | 仅视频/专栏支持，番剧/用户/直播必须隐藏入口；`order` 需与现有 `all/v2` 番剧混排逻辑隔离 |
| 搜索分类可长按拖拽排序 | 中 | 1.2.6 更新日志 | 【事实】已有（FEATURES.md:758） | — | 已补齐 |

### 3.6 音频（对方 1.2.9 整批上线，我方无此域）

| 功能 | 用户价值 | 证据（对方） | 我方现状 | 难度 | 依赖与风险 |
| --- | --- | --- | --- | --- | --- |
| 收藏夹「听全部」+ 全屏音频页 + 队列页 | 高（对方把它当主力场景） | 【事实】`[对方] activity/player/AudioPlayerActivity.java`、`AudioQueueActivity.java`、`service/AudioPlayerService.java`、`api/AudioQueueApi.java`、`model/AudioQueue.java` | 【事实】我方只有播放器内「听视频模式（关画面只听声音）」（FEATURES.md:192），无独立音频页/队列 | **L** | 与「手表听歌」场景契合，但等于新增一个播放域（服务/通知/队列/进度/歌词）；建议只做「收藏夹连播 + 屏幕可关」轻量版，不引入新服务 |
| 听歌快速加载（本地读穿缓存代理） | 中 | 【事实】`[对方] service/AudioCacheProxy.java:34-46`（127.0.0.1 HTTP 代理，按 Range 读盘+回源，只缓存 0 起连续前缀，80MB 上限，连续失败 3 次熔断回退直连） | 【事实】无音频缓存（我方缓存是整片下载） | L（若做音频域则 M） | 该设计可独立复用：任何「无 media3 缓存」的 ijk 场景都能用；但本地端口监听需注意安全（只绑 127.0.0.1） |
| 听歌进度记忆 / 歌词 | 中 | 【事实】`util/AudioProgressStore.java`、`util/LyricProvider.java`（B 站字幕优先，回落网易云）、`api/NeteaseApi.java` | 【事实】无 | L（依附音频域） | 网易云歌词接口属第三方非官方接口，合规与稳定性风险【推测】 |
| 收藏夹「下载全部（音频）」 | 中 | 【事实】`activity/user/favorite/FavoriteVideoListActivity.java:114-181`（`DownloadService.startDownloadBatch`，aid+cid 去重，逐页入队） | 【事实】我方收藏夹有批量复制/移动/取消收藏（FEATURES.md:456-470），无批量下载 | **M** | 复用我方 `service/DownloadService.kt` 队列；注意手表存储小，需体积确认弹窗（对方 `:146-150` 也做了） |
| Wi-Fi / 移动数据分别默认清晰度 | 中 | 【事实】1.2.9 更新日志；`[对方] util/NetworkPlaybackQuality.java`、`PlaybackQualityPolicy.java` | 【事实】我方无网络类型判据（`util/NetWorkUtil.java:833-842` 只判「是否联网」） | **M** | 需在取流前读 `NetworkCapabilities`/`getActiveNetworkInfo`；低版本 API 兼容 |

### 3.7 隐私与伪装（1.2.6）

| 功能 | 用户价值 | 证据（对方） | 我方现状 | 难度 | 依赖与风险 |
| --- | --- | --- | --- | --- | --- |
| 启动密码锁 | 中高：「防熟人翻手机」诉求，与我方伪装页互补 | 【事实】`[对方] util/PrivacyLockUtil.java:6-11`（固定盐 + 明文的 SHA-256，注释自述「只挡君子不挡拆包人」、「忘了密码没有后门」）、`:21-43`（enable / hash / verify / isSwitchOn）、`activity/privacy/PrivacyLockSettingsActivity.java` | 【事实】我方只有「双击顶栏伪装 + 退出暗号」（FEATURES.md:910-934），**无冷启动门禁** | **S/M** | 忘密码=清数据，必须在设置页写明；与「退出」菜单、崩溃页、教程页的启动路径都要判一次，避免绕过 |
| 桌面图标伪装成「计算器」 | 中：比应用内伪装更彻底 | 【事实】1.2.6 新增 `activity-alias` 切换 + `res/mipmap-nodpi/icon_calculator.png`；1.2.6 修复「名字写错导致开关自己弹回」 | 【事实】我方计算器页已有（`activity/CalculatorActivity`）但只从应用内进入；无 launcher alias | **M** | 各家手表 ROM 对 `activity-alias` 的桌面刷新不一致（需逐机型验）；共存版 flavor、图标缓存、卸载残留快捷方式都要处理；风险高于收益 |
| 隐私与伪装使用说明页 | 低 | 1.2.6 更新日志 | 【事实】我方设置页已有说明文案（FEATURES.md:933） | — | 可选 |

### 3.8 其它增量

| 功能/修复 | 用户价值 | 证据（对方） | 我方现状 | 难度 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 回声洞：看完的条不再出现、长按两次重置 | 低（彩蛋） | 【事实】`[对方] activity/settings/SettingMainActivity.java:140-158`（存**已读条数**而非下标，跨版本数组变长时夹取） | 我方彩蛋是「连点 7 次」形态（FEATURES.md:791），无回声洞 | 不适用 | 「存已读条数防错位」这个细节可借用，功能本身不搬 |
| 检查更新限时 + 逐源反馈 | 中：消除「永远卡在检查更新中」 | 【事实】`[对方] util/UpdateManager.java:111-138`（**两个 client**：检查带 `callTimeout 20s`，下载不套总闸）、`:210` 逐源回调 | 【事实】`[我方] util/UpdateManager.kt:42-45` 有 connect 15s / read 60s；`:252-267` 逐源尝试 + 错误汇总，但**无 `callTimeout` 总闸** | S | 部分已具备；补 `callTimeout` 即可（见 §4） |
| 崩溃上报改走官网反代 | — | 【事实】`[对方] util/CrashUploader.java:45-55`（Supabase + netlify 反代回落） | 【事实】我方自建 `api/TerminalApi` + 隐私同意；无本地待发队列 | 不适用（BaaS 不搬） | 只学管道：本地留档 + 待发队列 + 冷启动补发（§4） |
| SP `getString` 类型保险丝 | 中（防崩） | 【事实】1.3.0 `aa0903e` | 【事实】`[我方] util/SharedPreferencesUtil.java:100-105` 只防 `sharedPreferences == null`，不防类型漂移；但设置写入按 `type` 分派（`adapter/SettingsAdapter.kt:281-285`） | S | 可选加固：`getString` 内 `try/catch ClassCastException` 回 `def` |
| 番剧入口可在菜单里关 | 低 | 1.2.9 更新日志 | 【事实】我方菜单 14 项本无番剧入口（FEATURES.md:50） | 不适用 | — |

## 4. 优化点（性能 / 交互 / 稳定性）

1. **崩溃本地留档 + 待发队列**（稳定性，高价值）。对方 `[对方] util/CrashUploader.java:130` `saveLocal`、`:163-172` `enqueue`（先入队再上传，进程途中被杀也不丢，`MAX_PENDING` 上限 + 按优先级淘汰）。我方旧报告 §10.1 的 P1「崩溃上报无本地留档、无队列、无补发」**至今未修**：【事实】全仓 grep `crash_pending`/`enqueue`/`saveLocal` 只命中播放进度落盘，崩溃链路只有 `api/TerminalApi.java:713` `uploadCrash` 一次性上传。**建议搬管道不搬 BaaS**：落 `cacheDir/crash_pending/`，冷启动补发我方 `TerminalApi`。
2. **播放会话哨兵**（稳定性，高价值）。`[对方] util/PlayerSentinel.java:10-22`：进播放页 `commit()` 同步写阶段标记，正常销毁清除；下次冷启动若标记仍在 → 「这次播放没正常收尾」，且**只留本地不上传**（因为 native 崩溃/LMK/强杀在数据上同形，上传会误导归因）。我方 §10.4「启动早期崩溃零记录」与 native 崩溃无痕是同一问题。**值得搬**，M。
3. **更新检查加总闸**（稳定性，S）。我方 `[我方] util/UpdateManager.kt:42-45` 只有 connect/read 超时，DNS 挂死时没有上界；对方拆成「检查 client 带 `callTimeout 20s`、下载 client 不套总闸」(`[对方] util/UpdateManager.java:124-138`)。**值得搬**，1 小时。
4. **页面栈恢复**（体验，M）。`[对方] util/ResumePageUtil.java:29-37`：可恢复页面 `onCreate` 入栈 / `onDestroy` 出栈，`putStringSync` 同步落盘（注释解释：主动退出/崩溃收尾必须同步写，否则 SIGKILL 丢盘 → 「点了退出又回到上次页面」）。我方 §10.3 仍缺。**值得搬思路**，但要接入我方 `MenuActivity.killAndJump` 与 `CatchActivity`，且必须做「退出/崩溃时清栈」的负向测试。
5. **氛围背景的预渲染降采样**（性能，参考）。`[对方] ui/widget/AmbientBackground.java:35-42`：径向光晕半径是 `max(w,h)*0.75`，每帧全屏求值，实测 1.6 倍缩放下「关于」页单帧 GPU 972ms；解法是预渲染降采样位图 + 只改缓存、不改绘制。我方 `ui/appearance/AmbientGlow.kt:79` 是同思路的自研实现，**是否已有降采样未复核**【推测】，建议用 `util/PerformanceManager` 实测一帧再定。
6. **列表/切页固定开销**（性能，参考）。对方 1.2.8 的提交 `8cd42eb` 触及 `BiliTerminal.java`、`BaseActivity.java`、`Logu.java`、`BackgroundManager.java`，更新日志写明「进入和返回页面、从菜单切页、冷启动的一批固定开销（重复的系统查询、每次切页都清图片缓存等）」。我方旧报告 §11.1/§11.2 同样有「冷启动网络风暴」「启动链路零埋点」，方向一致，可对照其改动点自查。
7. **收藏夹列表主线程收拢**（稳定性，已收敛）。对方 1.2.9 修「下拉刷新/编辑返回偶发闪退（列表数据与界面刷新不在同一线程）」；我方 26.11.01 `d203840` 已用「整表替换」解决同类崩溃（崩溃 #160）。**结论：已收敛，无需再动**。
8. **SP 类型保险丝**（稳定性，S）。见 §3.8；顺手做，成本 10 行。

## 5. 对方的设计坑（不建议照搬）

1. **为 3 个字段手写 gRPC + protobuf**（`[对方] api/ImGrpcApi.java:21-38`）。为发一张视频卡片手写 5 字节帧 + protobuf 编码 + 5 种降级路径，维护成本远超收益；我方若要「转发到私信」，先做「文本 + 链接」即可覆盖 80% 场景。
2. **崩溃上报绑死第三方 BaaS**。`CrashUploader` 直连 Supabase（境外）+ 自建 netlify 反代（`netlify/functions/crash.mjs`），与对方隐私弹窗默认开启同一套【推测：默认值未在本轮复核】；与我方「自建接口 + 隐私同意 + 可关」策略冲突。**只学队列，不学后端**。
3. **「不感兴趣」是本地假实现**。`[对方] VideoCardAdapter.java:206-213` 注释自认「服务端反馈接口属新接口，留待后续」，只从当前列表移除并提示「已减少此类推荐（本地生效）」。照搬会给出**假承诺**；要么接 `/x/web-interface/feedback/rcmd` 验证，要么文案如实写「本地隐藏」。
4. **targetSdk 26 + compileSdk 33 + 只打 32 位**（`[对方] app/build.gradle:11/31/43`）。对方靠「旧设备友好」换体积与兼容，代价是 Android 13+ 的通知权限/媒体权限行为全部失效；我方 minSdk 19 + targetSdk 34 + 三 ABI 是不同取舍，不能拿它的 SDK 配置当参照。
5. **零测试 + 5000 行 Activity**。对方 `app/src/test` 为 0，1.3.0 里「横滑必崩」（`3b5a3c6`）就是典型的「类型约定没人校验、上线才炸」；我方 `app/src/test` 已有 134 个 JVM 测试与 `player/` 纯策略层，**不要为了对齐对方而把逻辑内联回 Activity**。
6. **桌面图标伪装**（`activity-alias`）。收益是隐私，成本是逐 ROM 兼容 + flavor 交叉；1.2.6 自己就踩了「名字写错 → 开关弹回」和「解锁死循环」两次。若做，必须先在目标手表上验证 alias 切换后桌面图标是否即时刷新。
7. **中文硬编码 + 单文件巨型类**。对方新功能仍在 Activity 里内联中文（如 `"稍后再看"/"不感兴趣"/"加入黑名单"`），我方文案必须进 `res/values/strings.xml`（AGENTS.md 硬约束），移植时是「照逻辑重写」而不是「复制粘贴」。

## 6. 推荐移植 Top 9（按 用户价值 ÷ 实现成本 排序）

> 每条给出：来源（对方）→ 我方落点 → 需要的 API/资源 → 单测要点 → 风险。我方测试目录 `app/src/test/java/com/RobinNotBad/BiliClient/`。

1. **性别 / IP 属地展示**（S，价值高）
   - 来源：`[对方] api/UserInfoApi.java:224-248`、`model/UserInfo.java:51-52`、`adapter/dynamic/UserDynamicAdapter.java:217-224`。
   - 落点：`api/UserInfoApi.java:59-91`（`getUserSpaceInfo` 返回的 JSON 里直接 `optString("sex")`/`("ip_location")`）、`model/UserInfo.java`（加字段 + Parcelable 读写）、布局 `res/layout/cell_user_info.xml`、`res/layout/cell_dynamic.xml`。
   - API：无新接口（复用 `x/space/wbi/acc/info`）。
   - 单测：`UserInfoApiTest`（新增）——空串/`"保密"` 不产出展示文本、Parcelable 往返保持字段。
   - 风险：`UserInfo` 是多处构造的 Parcelable，加字段必须同步 `readInt/writeString` 顺序，否则跨页面传参会错位。

2. **互关徽章**（S，价值中）
   - 来源：`[对方] api/FollowApi.java:30/47/61/86`、`adapter/user/UserListAdapter.java:34/82-87`。
   - 落点：`api/FollowApi.java:29-50`（解析 `attribute`）、`model/UserInfo.java` 加 `mutualFollow`、`adapter/user/UserListAdapter.kt` + `res/layout/cell_user_list.xml` 加徽章。
   - 单测：`FollowApiTest`（已存在）扩一例 `attribute==6` → `mutualFollow=true`；`attribute` 为 1–5 时 false。
   - 风险：只在「看自己的关注/粉丝/分组」时显示；看别人列表 `attribute` 语义不同，误显示会误导。

3. **分享得经验**（S，价值中）
   - 来源：`[对方] activity/video/info/VideoInfoFragment.java:623-682`、`api/LikeCoinFavApi.java:54-61`。
   - 落点：`api/LikeCoinFavApi.java` 加 `share(long aid)`（csrf 走 `NetWorkUtil.getLoginCsrf()`，与 `triple/like/coin` 同构）、`activity/video/info/VideoInfoFragment.kt:577` 转发菜单加第三项 + 本地按天去重（SP 键 `share_exp_day`/`share_exp_aids`）、文案进 `strings.xml`。
   - 单测：抽纯函数 `ShareExpDedup`（当天同 aid 二次点击返回 false）；`LikeCoinFavApi` 的 csrf 取值用现有模式断言。
   - 风险：`-403` 风控会常态出现，提示文案要如实；经验到账不可保证（对方注释已声明），不要写「已获得经验」。

4. **私信表情**（S，价值高）
   - 来源：`[对方] activity/message/PrivateMsgActivity.java:69-105`、`activity/EmoteActivity.java:212-214`。
   - 落点：`api/EmoteApi.java:19-20` 加 `BUSINESS_IM="im"`、`activity/EmoteActivity.kt`（选中即 `setResult` 带 `"text"`）、`activity/message/PrivateMsgActivity.kt:78-99`（输入行加表情按钮，回收 `emoteLauncher` 结果 append）、`res/layout/activity_private_msg.xml` + `icon_emote`。
   - 单测：`EmoteUrlCollectorTest` 同目录加「表情码拼接不破坏光标位置」；`PrivateMsgApiTest` 断言 `msg_type=1` 文本含 `[name]`。
   - 风险：`business=im` 面板接口可能要求 App 端签名；拉不到时按对方做法回落 `reply` 包（但显示的表情可能不在私信里生效）。

5. **播放器音频焦点**（S，价值中高）
   - 来源：`[对方] activity/player/PlayerActivity.java:368-380/642/3338-3353/3482/3496`（1.2.9 `a3a3577` 新增）。
   - 落点：`activity/player/PlayerActivity.kt`（起播 request、`onAudioFocusChange` 暂停、`onStop/onDestroy` abandon）、`player/PlayerControlDelegate.kt:43`（已有 `AudioManager` 实例可直接复用）。
   - 单测：抽 `AudioFocusPolicy`（焦点变化 → 是否暂停/是否恢复）做 JVM 测试；与 `PlayerGesturePoliciesTest` 同目录。
   - 风险：后台/熄屏播放时是否继续持焦要真机验；焦点短暂丢失（导航播报）应「暂停后可续」还是「duck」要定策略，不要盲目照抄 `LOSS_TRANSIENT` 直接暂停。

6. **搜索排序**（M，价值高）
   - 来源：`[对方] api/SearchApi.java:169-179`、1.2.8 `activity/search/SearchActivity.java` + `dialog_search_sort.xml`。
   - 落点：`api/SearchApi`（我方 `SearchApiTest.kt` 已有）加 `order` 可选参数；`activity/search/SearchActivity.kt` 顶部加排序入口 + 弹窗；番剧/用户/直播/专栏分支隐藏（仅视频与专栏支持）。
   - 单测：`SearchApiTest` 断言 `order=null/""/"totalrank"` 不拼参数、其余拼 `&order=`。
   - 风险：我方番剧搜索走另一条混排链路，`order` 不能透传到 `all/v2`；改过排序的 UI 状态要能在分类切换后保持。

7. **启动密码锁**（S/M，价值中高）
   - 来源：`[对方] util/PrivacyLockUtil.java:6-43`、`activity/privacy/PrivacyLockSettingsActivity.java`。
   - 落点：新增 `util/PrivacyLockUtil.kt`（SHA-256(盐+密码)，只存 hash）、`activity/SplashActivity.kt` 首次进入前判一次（未解锁跳伪装页/密码页）、`activity/settings/SettingGroupActivity.kt` 加开关 + 改密码（需先验旧密码）。
   - 单测：纯函数 `Sha256`/`verify` 的 JVM 测试；「未设密码时 isEnabled=false」。
   - 风险：忘密码无后门（对方自述），文案必须写明；要覆盖 `GetIntentActivity` 深链、崩溃页、通知点入等旁路，否则等于没锁。

8. **卡片长按菜单（先只做「稍后再看 + 加入黑名单」）**（M，价值高）
   - 来源：`[对方] adapter/video/VideoCardAdapter.java:159-171/179-226/242-279`、`api/UserInfoApi.java:355-358`。
   - 落点：`adapter/video/VideoCardAdapter.kt`（默认长按仅当页面未设监听时接管）、`api/UserInfoApi.java` 加 `blockUser(mid, block)`、`api/WatchLaterApi.java:127`（`add(aid)` 已有）；弹窗按我方圆屏收窄逻辑做（不必引入 `AdaptiveDialog`）。
   - 单测：`WatchLaterApiTest`（已有）扩一例；拉黑确认流程抽纯策略测「upMid<=0 时不发请求」。
   - 风险：拉黑不可逆；「不感兴趣」**本轮不要做**（服务端接口未验证，假实现会误导用户）。

9. **收藏夹批量下载 / 短视频无极调音（二选一，M 与 S）**
   - 批量下载：来源 `[对方] activity/user/favorite/FavoriteVideoListActivity.java:114-181`；落点 `activity/user/favorite/FavoriteVideoListActivity.kt` + `service/DownloadService.kt` 批量入队；风险是手表存储小，必须先弹体积确认（对方 `:146-150` 同样处理）。
   - 短视频无极调音：来源 1.3.0 `03105df`；落点 `activity/video/ShortVideoPlayerActivity.kt:895-901` 改走 `player/PlayerVolumePolicy.kt` 的 gain 通道并复用存档键；单测复用 `PlayerVolumePolicyTest`（已有）。
   - 建议先做短视频调音（S、单文件、可复用已测策略），批量下载排在其后。

**第二梯队（不做也行，做的话按序）**：片头片尾五档（M）、播放会话哨兵（M）、楼中楼带图隐藏（S，一行）、定时发布范围校验（S）、更新检查总闸（S）、`opt*` 容错（S）、过渡动画开关（S）、`values-night`（S/M）。

## 7. 局限与未复核项

- **未复核**（本轮没读代码，不参与统计）：§2 表外的 `MediaSession` 隔离（旧报告 §5.1 P2-6）、回前台重挂 surface（§5.1 P2-1）、失效详情页自动退出（§6.4 P2-2）、搜索建议双形态（§6.4 P2-4）、收藏夹 `media_id<=0` 兜底（§6.4 P2-5）、图片上传结果缓存（§7.6 P2-2）、删除「当前账号」后仍登录（§7.6 P2-3）。
- **未复核**的推断已在正文标【推测】：我方播放器顶栏返回热区是否有同款压缩（§3.4）、我方 `AmbientGlow` 是否已做降采样（§4 第 5 条）、对方崩溃上报默认开启（§5 第 2 条）。
- **度量口径**：对方 731 个 `app/src/main` 文件与我方 1515 个，是按各自仓库工作树计数；预计算清单基于 git blob 哈希，`only-fork`/`only-ours` 里含大量文档与截图，不代表功能差异面。
- **未构建对方 1.3.0**：本文只做静态阅读，其 `assembleDebug` 可用性以旧报告审计记录（1.2.3 成功）与对方 CI 记录为准；1.3.0 的编译修复提交（`a766838`「面板回退逻辑变量改为 effectively final，修复编译错误」）说明它是**带着编译错误提交再补**的，靠 CI/真机兜底。
- **合规边界**：本报告未读取我方 `private/` 目录，未在对方代码里寻找任何绕过服务端限制的实现；对方「私信卡片」依赖 App `access_key` 的用法属其自有实现，我方是否采用需另行评估（私信风控与账号安全）。
