# 听歌模式（音频优先播放）设计

- 日期：2026-11-13
- 状态：**待评审，未实现**
- 参考实现：cyq 分支克隆 `D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\Re-BiliTerminal-cyq`（Java，HEAD `c4935a9`）
- 本文所有结论标注：【事实】= 我亲自读过代码/文档并核对了行号；【推测】= 未验证，需要 L1/L2 实测

## 0. 先说三个被任务描述带偏的前提

任务描述里有三处与仓库实际不符，后面的设计全部按实际重写：

| 描述里的说法 | 实际情况 | 影响 |
| --- | --- | --- |
| 「我们没有音频能力，要新做」 | 已有「听视频」模式：`activity/player/PlayerActivity.kt` 的 `isAudioOnlyMode`（:361）+ `toggleAudioOnlyMode()`（:5425-5494），切模式时用 `OPT_CATEGORY_PLAYER` 的 `vn=1` 关掉视频解码 【事实】 | 纯音频解码链路**已被真机验证**，不需要换内核、不需要重编 `.so` |
| 「歌词网易云兜底需要新增第三方域」 | 只做 B 站 CC 字幕时**一个新域名都不用加**：字幕地址是 `//aisubtitle.hdslb.com/...`，`hdslb.com` 已在 `network_security_config.xml` 白名单内；接口 `PlayerApi.getSubtitleLinks/getSubtitle` 本地已有 【事实】 `docs/bilibili-api-notes.md:97` | 网易云是「可选增强」，不是前置条件 |
| 「收藏夹入口在 `FavoriteVideoListActivity.java:981-1064`」 | 该文件实际是 `.kt`，行号已漂移到 :965-1000（`confirmBatchDownload`）、:1058-1064（`getVideoDash` + `startDownloadingAudioOnly`） 【事实】 | 入口设计按现行代码写 |

另外一条必须写在前面的判断：`docs/bilibili-api-notes.md:150,159` 已经把这件事的定位写死了——「后台听视频 + AI 字幕」的现状是「✅ 已有，**缺的是二者组合的『车载/播客式』体验打磨**」，并给出结论「腕上场景的终点不是把 B 站功能搬过来，而是把 B 站内容变成一条**可离线消费的队列**：稍后再看 + 历史续播 + 音频轨 + AI 字幕」。**本文的目标就是把这个组合补齐，而不是再做一遍播放器。**

## 1. 现状盘点

### 1.1 我方（ReBiliClient）已有的零件

| 零件 | 位置 | 能不能复用 | 说明 |
| --- | --- | --- | --- |
| 纯音频解码 | `PlayerActivity.setDisplay()` :2034-2037 | ✅ 直接照抄 | `OPT_CATEGORY_PLAYER` 的 `vn=1` + `OPT_CATEGORY_CODEC` 的 `skip_frame=48`、`skip_loop_filter=48` 【事实】 |
| DASH 取音频流 | `api/PlayerApi.java` `getVideoDash()` :194-261 | ✅ 直接用 | 已回填 `playerData.audioUrl = dashData.getBestAudioStream().baseUrl` 与 `audioUrlCandidates`（主 + 备份）【事实】 |
| 队列模型 | `player/PlayQueue.kt`（299 行） | ✅ 直接用 | **纯逻辑、零 Android 依赖、有 JVM 单测**：`PlayQueueMode{SEQUENTIAL,REVERSE,SHUFFLE,REPEAT_ONE}`、`PlayQueueSource{SINGLE,UPLOADER,COLLECTION,FAVORITE,LOCAL_CACHE}`、`encode()/decode()` token 【事实】 |
| 会话存档 | `player/PlaySessionStore.kt`（162 行） | ⚠ 要加一组 key | 目前只有一组 key（`KEY_SESSION="player_last_session_v1"`、`KEY_QUEUE_TOKEN="player_last_queue_token_v1"`）【事实】 |
| 音频焦点判据 | `player/AudioFocusPolicy.kt`（72 行） | ✅ 扩展即用 | 纯函数 + 单测；`actionFor()` 把 LOSS/LOSS_TRANSIENT 判为 `Action.PAUSE`，CAN_DUCK 判为 NONE（**故意不 duck**，因为音量是 ijk 软件增益）【事实】 |
| 播放器就绪守卫 | `player/IjkNativeLibs.kt` | ✅ 必须走 | `ensureLoaded()` 会把「本进程已失败」锁死，避免每次开页重试刷日志 【事实】 |
| 请求头判据 | `player/PlayerRequestPolicy.kt` | ✅ 必须走 | 直链 CDN 防盗链**只要 Referer、不要 Cookie**（26.11.04 实测：缺 Referer → 403，补上 → 206）【事实】 `docs/bilibili-api-notes.md:117` |
| 字幕接口 | `PlayerApi.getSubtitleLinks(aid,cid)` :1014、`getSubtitle(url)` :1087 | ✅ 直接用 | 已有 `model/Subtitle`、`model/SubtitleLink`；播放页已在用它选轨 【事实】 |
| 后台播放服务 | `service/PlaybackService.kt`（339 行） | ❌ **不能复用** | 见 §3 |
| MediaSession | `PlayerActivity` 内 :285、:4163-4236 | ❌ 不能复用（归 Activity） | 见 §3 与 §9 的 API19 崩溃风险 |
| 灵动岛「播放中」 | `util/IslandStatus.kt` `readPlaying()` :99-104 | ⚠ 必须改一行 | 现在**只认** `PlaybackService.isBackgroundPlaying()` 【事实】 |

### 1.2 cyq 的听歌模式（11 个文件 / 约 3500 行）

| 文件 | 行数 | 干的事 | 要不要抄 |
| --- | --- | --- | --- |
| `service/AudioPlayerService.java` | 1514 | 独立前台服务，自持 ijk + MediaSession + 通知 | 抄骨架（见 §3） |
| `activity/player/AudioPlayerActivity.java` | 519 | 听歌页：封面 / 单行歌词 / SeekBar / 音量 / 上下首 / 队列进度 | 抄布局思路 |
| `activity/player/AudioQueueActivity.java` | 115 | 队列页 | M4 再说 |
| `service/AudioCacheProxy.java` | 559 | 127.0.0.1 loopback HTTP 代理，读穿缓存 | 见 §5，倾向**不抄** |
| `util/LyricProvider.java` | 415 | CC 字幕优先 + 网易云兜底，并行 + 打分 + 24h 缓存 | 抄「只做 CC」那半 |
| `helper/MediaSessionHelper.java` | 120 | 把 MediaSession 类型收进单独的类 | **必须抄这个结构** |
| `model/AudioQueue.java` | 226 | 队列 + 模式 + 去重标记 | 不抄（我方 `PlayQueue` 更强） |
| `util/AudioProgressStore.java` | 108 | 进度存档 | 不抄（我方 `PlaySessionStore` 够） |
| 其余 | — | `AudioQueueApi`/`DashAudioStream`/`AudioTrackPicker` | 不需要（我方 `DashData.getBestAudioStream()` 已覆盖） |

## 2. 问题①：音频焦点抄不抄 cyq（它明确「不请求焦点」）

### 2.1 两边的事实

- cyq 的类注释原话：「按产品要求（真混音）：本服务**不请求音频焦点**，避免抢占视频播放的焦点；只在耳机拔出（`ACTION_AUDIO_BECOMING_NOISY`）时暂停，防止突然外放」【事实】`AudioPlayerService.java:57-74`
- 我方**视频侧已经请求焦点**：`PlayerActivity.requestAudioFocusForPlayback()` :3804-3821 用 `AudioManager.requestAudioFocus(listener, STREAM_MUSIC, AUDIOFOCUS_GAIN)`（minSdk 19，不用 API26 的 `AudioFocusRequest`），被拒也只记日志照常播；`handleAudioFocusChange` :3827-3834 → 暂停；`playerPause()` :3756-3774 末尾主动弃焦点 【事实】
- 我方**没有** `ACTION_AUDIO_BECOMING_NOISY` 处理：全仓 grep 0 命中 【事实】。这是真缺口——耳机一拔就外放。

### 2.2 方案对比

| 方案 | 做法 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- | --- |
| A. 照抄 cyq | 不请求焦点，只加 noisy 暂停 | 与视频「真混音」，两路能一起响；cyq 已跑通 | 来电话 / 语音助手 / 导航播报会**和我们叠音**；手表上没有「一边听歌一边看视频」的真实场景，混音收益≈0 | 不推荐 |
| B. 请求 GAIN + 沿用 `AudioFocusPolicy` | 音频服务申请 `AUDIOFOCUS_GAIN`；LOSS/TRANSIENT → 暂停；CAN_DUCK → **不 duck**（不压软件增益）；弃焦点幂等；**另补 noisy 暂停** | 与视频侧行为一致，用户不用记两套；复用现成纯函数 + 单测；修掉「拔耳机外放」这个真缺陷 | 与视频同播时会互抢（但该场景不存在） | **推荐** |
| C. 请求 GAIN + CAN_DUCK 真 duck | 焦点被降时按比例压软件音量 | 听起来「更礼貌」 | 我方音量是 ijk 软件增益，压了会和用户手调的档位打架；`AudioFocusPolicy` 已明确否决过这条 【事实】 | 不做 |

### 2.3 倾向与理由

**倾向 B。** 三条理由：

1. 手表端「听歌」与「看视频」是互斥的两种姿态（一个熄屏一个亮屏），cyq 用「真混音」解决的冲突在本项目里不存在，为它付出「来电叠音」的代价不划算。
2. 焦点判据已经是纯函数 + 单测（`AudioFocusPolicy.kt`），扩展成本几乎为零，而「不请求焦点」需要在服务里另写一套解释性代码。
3. 无论选哪个方案，**`ACTION_AUDIO_BECOMING_NOISY` 必须补**——这条与焦点选择无关，是我们现在的空白，也是 cyq 做对的地方。

## 3. 问题②：服务形态——复用 `PlaybackService` 还是新开一条音频链路

### 3.1 为什么不能复用 `PlaybackService`

| 事实 | 行号 | 后果 |
| --- | --- | --- |
| 它靠 `playerRef: WeakReference<PlayerActivity>` 反查播放页，所有控制都转到 Activity 的方法上 | `PlaybackService.kt:62` | Activity 一死播放就没了——**听歌必须能在页面关闭后继续**，这是根本冲突 |
| 它没有 MediaSession，只有两个自定义 action（`ACTION_TOGGLE`/`ACTION_STOP`）+ 1 秒刷一次的通知 | :53-58 | 锁屏控件、蓝牙耳机上一首下一首都没有通路 |
| 硬约束「5 秒义务」：`startForegroundService()` 后必须 5s 内 `startForeground()`，否则 `RemoteServiceException` 崩进程（26.10.10 线上真实发生） | 见 `docs/architecture-map.md` §7.5 | 新服务必须照这个模式写：`onCreate` 里就进场、进场前收到 stop 只置 `stopPending` |
| `onTaskRemoved` 里 `stopSelf()` | :203 | 音频的语义不同：划掉最近任务要不要停，得单独定（见 §9） |
| 通知固定 `NOTIFICATION_ID=1028`、渠道 `playback_channel` | :56-57 | 音频要用自己的 id/渠道，否则两条通知互相顶掉 |

结论：**新开 `service/AudioPlayerService.kt`，不复用、不改造 `PlaybackService`。** 【事实】依据是它 339 行代码里没有一行是「不依赖 Activity」的播放逻辑。

### 3.2 为什么 MediaSession 必须收进单独的类

cyq 的 `MediaSessionHelper.java` 注释记了一个真实崩溃：`android.media.session.*` 是 API 21 的类，当它作为字段出现在 `PlayerActivity` 里时，D8 为兼容 API<24 的 nest 机制会生成返回类型为 `MediaSession` 的合成访问器 `-$$Nest$fgetmediaSession`；而 `BaseActivity.onStart` 的 EventBus 注册要反射 `getMethods()` → **Android 4.4/5.0 上直接 `NoClassDefFoundError: android/media/session/MediaSession`，整个播放页起不来** 【事实】。

我方 minSdk 是 **19** 【事实】`app/build.gradle:74`，且 `docs/architecture-map.md` §7.4（:759）已经归出同一条铁律：MediaSession 的类型**不许**出现在字段/方法签名/返回值里，只能是 `Any?` + SDK 守卫 + 局部 `as?`。

所以设计上加一条硬规则：**新服务里与 MediaSession 有关的一切塞进 `player/AudioMediaSession.kt` 一个文件**，对外只暴露 `fun setMetadata(..)`、`fun setState(..)`、`fun release()` 这类不出现 `MediaSession` 字样的方法。这同时解决 API19 的类加载与 D8 nest 访问器两个问题。

### 3.3 `vn=1` 要不要改 `.so` 或 ijk 配置

**不用改任何原生产物。** 【事实】

- `vn=1` 是 `setOption` 的**运行时**分类参数（`OPT_CATEGORY_PLAYER`），不是编译期开关；
- 我方 `app/libs/{arm64-v8a,armeabi-v7a,x86}/*.so` 是已提交的预编译 ijk，`AGENTS.md` 明确「日常别动」；
- 现有「听视频」模式走的就是这条路，说明现成的 `.so` 支持 【事实】`PlayerActivity.setDisplay()` :2034-2037。

**唯一要注意的是**：`setDisplay()` 里那套通用选项（`reconnect=1`、`packet-buffering=1`、`max-buffer-size=15MB`、`rw_timeout=10_000_000`、`addrinfo_timeout=5_000_000`、`reconnect_streamed=1`、`reconnect_delay_max=5`、`user_agent`）【事实】:2028-2094 现在是内联在 Activity 里的。音频服务要一套减配版（去掉画面/旋转/音画同步项，加大重连），建议抽成 `player/IjkAudioOptions.kt` 只读常量，两边各自读，避免两份魔法数字漂移。

## 4. 问题③：队列与续播——复用 `PlayQueue`/`PlaySessionStore`，一个队列还是两个

### 4.1 现状耦合点

`PlayQueue` 本身是干净的可复用件，但**队列的装配在播放页里**：`PlayerActivity.rebuildQueueFromPlaylist()` :5637-5652 从 `names`/`cids` 数组 + `detectQueueSource()` :5625 **重建**队列，然后 `persistQueueToken()` :5682 把 `queue.encode()` 写进 `PlaySessionStore`；进页面时读 `EXTRA_PLAY_QUEUE_TOKEN`（`"play_queue_token"` :500）或存档里的 token，`restoreQueueTokenIfMatches()` :5663 恢复 【事实】。

也就是说：**队列不是从外部传对象进来的，是从「分页标题数组 + cid 数组 + 来源枚举 + 当前下标」重建的。** 音频入口照这个形状传参即可，不需要新协议。

### 4.2 一个队列还是两个

| 方案 | 做法 | 优点 | 缺点 |
| --- | --- | --- | --- |
| A. 共用一个 | 音频与视频共用 `KEY_SESSION` / `KEY_QUEUE_TOKEN` | 改动最少 | 听完收藏夹再打开视频页，视频页会按音频的进度和队列恢复——**用户会以为放错了** |
| B. 复用类、分开存档 | `PlayQueue` 原样复用（0 改动）；`PlaySessionStore` 把 `save/last` 改成带 key 参数的重载，新增 `KEY_AUDIO_SESSION="player_audio_session_v1"`、`KEY_AUDIO_QUEUE_TOKEN="player_audio_queue_token_v1"`，默认参数仍是视频 key（**视频侧行为零变化**） | 语义清晰；`PlayQueue` 是纯逻辑，复用不引入任何耦合；不碰 `PlaySessionCodec` 的格式（它靠「字段追加在末尾」兼容，`uploaderMid` 是先例，但**没必要动**） | 多两组 key |
| C. 另写 `AudioQueue` | 照 cyq 抄一个 226 行的独立队列 | 「互不影响」最彻底 | 重复实现 `SEQUENTIAL/SHUFFLE/REPEAT_ONE` + token 编解码 + 洗牌种子，将来两套行为必然漂移 |

**倾向 B。** 判据：`PlayQueue` 已经被 6 个 `PlayQueueMode` 分支和单测钉住，另写一套等于把「上一首/下一首/随机」重新实现一遍；而存档隔离只花两组 key 的代价。

### 4.3 分 P 展开

cyq 在 `onPrepared` 之后按 aid 拉 `PlayerApi.getPageList(aid)`，把其余分 P 插到当前项后，并用 `AudioQueue.markExpanded(aid)` 去重（失败回滚标记）【事实】。我方 `PlayQueueItem(aid, cid, title)` 的 `cid` **允许为 0** 【事实】，正好用来表示「这个 aid 还没展开」。

建议 M2 再做，理由：收藏夹条目只带第一 P 的 cid 【事实】cyq 注释，但展开是**串行的网络请求 + 队列突变**，M1 先把「能连续听」跑通再说。做法沿用 cyq：展开放在起播之后、每个 aid 只展开一次。

## 5. 问题④：要不要 loopback 读穿缓存，预取放什么时机

### 5.1 cyq 的做法（`AudioCacheProxy.java` 559 行）

在本进程起 `127.0.0.1` 的 HTTP 代理，ijk 播 `http://127.0.0.1:<port>/a?cid=<cid>`；代理按 Range 先读磁盘、缺的边下边存；**只缓存从 0 开始的连续前缀**（音频顺序播放，前缀覆盖「拖回已播位置」，跳到未播位置就退回透传）；`MAX_CACHE_BYTES=80MB`、`PREFETCH_BYTES=1.5MB`、缓存键是 **cid 不是 URL**（URL 会过期 cid 不会）、连续失败 3 次熔断回退直连 【事实】。

### 5.2 我方前置条件

- 我方**没有任何 loopback 代理**，也没有 `OkHttp` 之外的自建 HTTP 服务；
- `network_security_config.xml` 是 `base-config cleartextTrafficPermitted="false"` + 四域白名单 【事实】。`http://127.0.0.1` 会不会被这条拦住：**ijk/ffmpeg 走 native socket，不经过 `NetworkSecurityPolicy`** 【推测】——必须在 M2 开工前用 10 行探针验一次（L1，见 §11）；
- 我方已有真落盘链路 `DownloadService`（含 `audioUrlCandidates` 多 CDN 换线 :766/:787）【事实】，用户「想离线听」时其实已经有「仅音频下载」入口（`DownloadPathSpec.TYPE_AUDIO_ONLY`）。

### 5.3 方案对比

| 方案 | 做法 | 成本 | 收益 | 结论 |
| --- | --- | --- | --- | --- |
| A. 抄 loopback 代理 | 559 行 HTTP 服务 + Range + LRU + 熔断 | 高（新增 ~600 行，且是网络层代码，出错面大） | 拖动不重建连接；弱网/回头听同一首免流量 | 不推荐（除非 L1 实测证明「起播慢/拖动断音」是真痛点） |
| B. 只做轻预取 | 下一首的 URL 提前解析 + 首个 `Range: bytes=0-1023` 探测活跃性，起播时直接用 | 低（~80 行，纯 OkHttp） | 切歌等待从「解析 + 连接」降到「连接」；提前发现坏 CDN 换备份 | **推荐（M2）** |
| C. 不做缓存 | — | 0 | — | M1 就先这样 |

### 5.4 预取时机（无论选哪个方案）

按 cyq 的实测经验反过来设计【推测 收益，事实 常量】：

1. **起播成功后**再预取下一首（不要和当前首并发，cyq 明确把分 P 展开挪到 `onPrepared` 之后就是因为「同网络并发会拖慢起播」）；
2. 阈值：当前播放进度 > 30 秒 或 > 曲目 15% 时触发，避免「连点下一首」时刷一串请求；
3. URL 有效期 120 分钟 【事实】`docs/bilibili-api-notes.md:93`，所以预取结果**只活 10 分钟**（cyq 的 `PREFETCH_TTL_MS=5min`、`URL_CACHE_TTL_MS=10min` 是同一判断），过期即丢，**不写进任何持久队列**；
4. 失败静默，绝不因为预取失败去打扰正在播的那首。

## 6. 问题⑤：歌词——CC 字幕优先，网易云兜底单列

### 6.1 只做 CC 字幕的最小版本（推荐，M3）

**完全可以不碰新域名。** 【事实】

- `PlayerApi.getSubtitleLinks(long aid, long cid)` :1014 → `PlayerApi.getSubtitle(String url)` :1087，模型 `model/Subtitle`、`model/SubtitleLink` 都在；
- 字幕 URL 指向 `//aisubtitle.hdslb.com/...`，`hdslb.com` 已在明文白名单内 【事实】`docs/bilibili-api-notes.md:97` + `network_security_config.xml`；
- 选轨逻辑现成：`PlayerActivity` :3004-3048 用 `PlayerDefaults.SubtitleCandidate(lang, isAI)` 排序，cyq 的 `pickTrack()` 顺序（中文人工 > 中文AI > 其他人工 > 其他AI）与之一致 【事实】。

**前置条件（必须写进 UI 文案）**：`x/player/wbi/v2` 的 `data.subtitle.subtitles[]` **未登录时是空数组** 【事实】`docs/bilibili-api-notes.md:97`。所以「没歌词」的提示不能写「该视频无字幕」，要写「登录后可显示 AI 字幕」。

建议新建 `player/AudioLyricProvider.kt`（**不改造 cyq 的 415 行**，只取其中三件）：

| 抄什么 | 细节 |
| --- | --- |
| cid 级缓存 | `getCacheDir()/lyrics/<cid>.json`，`{source, lines:[{t,x}]}`，TTL 24h 【事实】cyq `CACHE_TTL_MS` |
| 超时纪律 | 字幕 1200ms 上限，超时当作「没有」，不许把起播拖住 【事实】cyq `BILI_TIMEOUT_MS` |
| 回调竞态守卫 | 回调里 `cid != lyricCid` 就丢弃（用户已经切歌了）【事实】cyq `AudioPlayerActivity.ensureLyrics` |

不抄：并行双源 + 打分 + 官方名清洗（那是网易云那一半的复杂度）。

### 6.2 网易云兜底：合规与风控（单独成节，默认不做）

如果要做，代价不只是「加一个域名」：

| 项 | 事实 / 判断 |
| --- | --- |
| 新域名 | `music.163.com`、`*.music.126.net`（封面/音频），要进 `network_security_config.xml` 白名单 —— 而该文件注释明写「若将来又出现明文自建接口，应改接口而不是往这里加域名」，加域名需要单独理由 【事实】 |
| 接口性质 | 网易云**没有公开歌词 API**，cyq 用的是社区逆向出的搜索/歌词接口（`NeteaseApi`），无文档、无 SLA、随时可能改 【推测】 |
| 匹配质量 | cyq 用「歌名 0.55 + 时长 0.35 + 排名 0.10」打分，`MIN_MATCH_SCORE=0.45`，注释原话是「**综合评分低于阈值宁可没有歌词，也不要配一首不相干的歌**」【事实】——即作者自己也不认为它可靠 |
| 内容与条款 | 把第三方平台的歌词下载并缓存进本 App，涉及对方服务条款与著作权；本项目定位是「第三方 B 站客户端」，引入非 B 站内容源会拉长分发风险 【推测，需用户拍板】 |
| 隐私 | 搜索关键词 = 用户正在看的视频标题，会发给第三方 【推测】 |

**倾向：M3 只做 CC 字幕；网易云那半单独提一个决策（见 §10 决策 4），默认不做。**

## 7. 问题⑥：入口与交互

### 7.1 入口（手表端优先）

| 入口 | 现状 | 改动 | 阶段 |
| --- | --- | --- | --- |
| 收藏夹「听整个收藏夹」 | 长按条目 → `TerminalDialog.menu`（`FavoriteVideoListActivity.kt:525/555`）；多选菜单已有「批量下载」且含「仅音频」档位 【事实】 | 菜单里加一项，直接 `AudioPlayerService.startQueue(...)` 并进听歌页 | M1 |
| 视频详情页「听」 | 详情页动作区已有「笔记 / 分享到其他设备」这类整行插入点（`SettingsKeys` 里 `DETAIL_SHOW_*` 系列）【事实】 | 加一个「听音频」入口，队列 = 该 UP 的合集或同稿件分 P | M1 |
| 本地缓存列表「播放全部」 | `LocalListActivity:345,404` 有「仅音频」入口 【事实】 | 同上 | M2 |
| 收藏夹/合集页顶部「播放全部」 | 未核 | 需要 UI 位置 | M4 |

### 7.2 听歌页（`activity/player/AudioPlayerActivity.kt`）

布局照 cyq 的形状，但按手表尺寸改。**cyq 的 `applyResponsiveLayout()` 里有两条真机经验值得直接抄**【事实】：

- 封面边长 = `min(屏宽 × 0.56, 屏高 × 0.30)`，再夹在 `96dp`（且 ≤ 屏宽 0.4）与 `240dp` 之间——注释写的是「界面大小放大后 220dp 正方形把标题/进度/按钮全挤出视口」；
- 控制行按屏宽比例分配：两侧各 20%、播放键 26%、间距各 5%（合计 76%），触控下限 36dp。

我方控件清单（M1 最小集）：

| 控件 | 行为 | 复用 |
| --- | --- | --- |
| `btnPrev` / `btnPlay` / `btnNext` | 上/下首走 `PlayQueue.previousIndex/nextIndex` | `PlayQueue` |
| `seekBar` | **松手才 seek**（cyq 同做法）【事实】 | — |
| `text_title` / `tvQueueInfo`（「3/57」） | 标题 + 队列位置 | `PlayQueue` |
| `lyricLine` | 单行，淡入淡出 | `AudioLyricProvider`（M3） |
| 音量 | `PlayerActivity.changeVolume()` 那套软件增益 | `PlayerVolumePolicy` |

**不要** `FLAG_KEEP_SCREEN_ON`。这是与视频页相反的一条：`docs/architecture-map.md` §7.52 要求播放页保亮屏，但**听歌页的整个价值就是熄屏听**，保亮屏会直接把续航打穿 【事实/设计判断】。

### 7.3 手表按键与表冠

现状【事实】`PlayerActivity.onKeyDown()` :4647-4669：

| 键 | 现有行为 |
| --- | --- |
| `DPAD_LEFT` / `DPAD_RIGHT` | `seekToPosition(video_now ∓ 10000L)`（**±10 秒**） |
| `DPAD_UP` / `DPAD_DOWN` | `changeVolume(true/false)` |
| `MEDIA_NEXT` / `MEDIA_PREVIOUS` | `playNextFromQueue()` / `playPreviousFromQueue()` |
| `HEADSETHOOK` / `MEDIA_PLAY_PAUSE` | 播放暂停 |
| `ENTER` / `DPAD_CENTER` | `controlVideo()` |

**表冠（旋转输入）在全仓没有任何通路**：grep `onGenericMotionEvent` / `Rotary` / `SOURCE_ROTARY_ENCODER` 均 0 命中 【事实】。要支持表冠得新增 `onGenericMotionEvent` 并按 `AXIS_SCROLL` 累积步进（Wear OS 写法），属于新功能而非复用。

**倾向：M1 不碰表冠**，`DPAD` 左右 ±10 秒已经覆盖「这歌前奏太长」的主诉；表冠放 M4，先做 L2 手感验证再决定。

### 7.4 胶囊（灵动岛）与通知

- `IslandStatus.readPlaying()` :99-104 现在**只**读 `PlaybackService.isBackgroundPlaying()` 【事实】。音频要亮「播放中」必须在这里加一条 `|| AudioPlayerService.isRunning()`。M1 就改，否则用户会以为没在播。
- 通知：音频服务自己建渠道。建议**复用**「音乐播放」语义（`PlayerActivity` 的 `CHANNEL_MEDIA_CONTROL`，在 `initMediaSession()` 里建 【事实】），不要新开第三个渠道 —— 手表上通知设置项越少越好。`NOTIFICATION_ID` 用 **1029**（1027 下载、1028 视频后台播放，两个都已在用 【事实】）。
- 锁屏/蓝牙控制走 `MediaSession`（`FLAG_HANDLES_MEDIA_BUTTONS | FLAG_HANDLES_TRANSPORT_CONTROLS` + `setPlaybackState` 按 `canNext/canPrevious` 增删 `ACTION_SKIP_TO_*`），形状照 cyq `MediaSessionHelper` 【事实】。

## 8. 问题⑦：分阶段 M1–M4

> 成本列的口径：**改动规模**（文件数 / 行数量级）是【事实】可估的，**工时**是【推测】。

### M1：能听——独立音频服务 + 听歌页（核心）

| 交付物 | 类型 | 规模 |
| --- | --- | --- |
| `service/AudioPlayerService.kt` | 新建 | ~600 行（骨架照 `AudioPlayerService.java`，去掉 loopback/网易云/分 P 展开） |
| `player/AudioMediaSession.kt` | 新建 | ~90 行（类型隔离，见 §3.2） |
| `player/IjkAudioOptions.kt` | 新建 | ~60 行（`vn=1` 那套 option 的只读常量） |
| `activity/player/AudioPlayerActivity.kt` | 新建 | ~450 行 |
| `layout/activity_audio_player.xml` | 新建 | ~150 行 |
| `player/AudioPlaybackState.kt` | 新建 | ~60 行（纯逻辑：进度快照 + 状态机，可单测） |
| `player/PlaySessionStore.kt` | 改 | +30 行（加音频 key 重载，默认参数不变） |
| `player/AudioFocusPolicy.kt` | 改 | +20 行（`shouldPauseOnNoisy()` 判据 + 单测） |
| `util/IslandStatus.kt` | 改 | +2 行（`readPlaying()` 加音频服务） |
| `AndroidManifest.xml` | 改 | +6 行（服务声明 `foregroundServiceType="mediaPlayback"` + `exported="false"`） |
| `strings.xml` | 改 | ~15 条 |
| 入口：`FavoriteVideoListActivity.kt`、视频详情页 | 改 | 各 +20 行 |

**验收**：

- L0：`assembleDebug` 两 flavor 通过；新增 `AudioFocusPolicyTest`、`AudioPlaybackStateTest`、`PlaySessionStoreTest`（音频 key 往返）全绿。
- L1（Debug 包 + adb）：
  1. `dumpsys media_session` 能看到我们的会话与播放状态；
  2. `dumpsys activity services` 里服务是 `isForeground=true`、type 含 mediaPlayback；
  3. `logcat` 里起播耗时（cyq 实测 prepare 中位 284ms / 最慢 1453ms 可作对照 【事实】）；
  4. 杀页面（`am force-stop` 不行，用返回键退出听歌页）后 `dumpsys media_session` 仍有活跃会话 = 「页面关了还在播」。
- L2（人）：听感（有没有爆音/卡顿）、切歌手感、锁屏后能否继续播、拔耳机是否立刻停。

**成本**：【推测】这是本次 80% 的工作量，风险最高处在「起播失败的用户提示」与「ijk 的 onError 迟到」——cyq 的 `STARTUP_WATCHDOG_MS=3000`（因为 ijk 自身 onError 最坏 15.2 秒才报 【事实】）必须照抄，否则用户面对的是 3 秒黑屏还是 15 秒黑屏的区别。

### M2：快——轻预取 + 断点续听

| 交付物 | 规模 |
| --- | --- |
| `player/AudioPrefetchPolicy.kt`（纯逻辑：何时预取、TTL、失败退避） | ~120 行 + 单测 |
| 服务里接预取（下一首 URL + Range 探测） | ~80 行 |
| 分 P 展开（`PlayerApi.getPageList` + 一次性标记） | ~70 行 |
| 断点续听（`PlaySessionStore` 音频 key 的 15s 节流落盘 + 进页面恢复） | ~40 行 |

**验收**：L0 单测；L1 用 logcat 量「切歌到出声」的毫秒数，与 M1 基线对比（预取命中应显著低于首次）；L2 判断「连续听 30 首不卡」。

**成本**：中等。【推测】不确定性在预取是否真能省时——如果 M1 的基线本来就只有 300ms，那 M2 收益不足，应跳过直接做 M3。

### M3：词——CC 字幕歌词

| 交付物 | 规模 |
| --- | --- |
| `player/AudioLyricProvider.kt`（cid 缓存 + 1200ms 超时 + 竞态守卫） | ~200 行 |
| 听歌页 `lyricLine` 绑定（按进度找当前行，二分） | ~80 行 |
| 「登录后可显示 AI 字幕」文案 | 3 条 strings |

**验收**：L0 单测（LRC/cue 时间轴查找、缓存 TTL）；L1 用一条已知有 AI 字幕的稿件验证 `lyrics/<cid>.json` 落盘且结构与 cyq 一致；L2 判断「一屏一句」在圆屏上读得清。

**成本**：低（接口全都现成）。**前置：登录态**（未登录字幕数组为空 【事实】）。

### M4：用——入口铺开与打磨

- 合集页 / 本地缓存列表的「播放全部（听歌）」入口；
- 队列页 `AudioQueueActivity`（照 cyq 115 行，含「后台补页后队列长度变了就整体重建」那条修复 【事实】）；
- 表冠 ±N 秒（需 L2 手感验证）；
- 若决策 4 选了「要网易云」：那半按 §6.2 单独立项。

## 9. 问题⑧：风险清单

| # | 风险 | 触发条件 | 影响 | 缓解 | 级别 |
| --- | --- | --- | --- | --- | --- |
| 1 | 前台服务被国产 ROM 杀掉 | 用户清后台 / 省电策略 | 音乐无声消失，且用户不知道 | 通知常驻；`onTaskRemoved` 语义单独定（见 #2）；提供「继续上次」入口 | L2 |
| 2 | 划掉最近任务 = 停还是不停 | — | cyq 选择**停**（注释：否则重开应用胶囊又按 `isRunning()` 出现，而应用内没有关闭入口）【事实】 | 倾向照 cyq：划掉就停；但要保证胶囊同步熄灭 | L2 |
| 3 | 来电话/播报叠音 | 未请求焦点（cyq 方案） | 两个声源同时响 | 见 §2，倾向请求 GAIN | L1+L2 |
| 4 | 拔耳机继续外放 | `ACTION_AUDIO_BECOMING_NOISY` 未监听（我方现状 0 命中 【事实】） | 公共场合突然外放，是最容易被投诉的缺陷 | M1 必修 | L2 |
| 5 | 锁屏后被冻结、播放卡住 | CPU 休眠 | 音频断续 | 【推测】音频管线自持 wakelock；若 L1 证明会卡，就加 `PARTIAL_WAKE_LOCK`（`WAKE_LOCK` 权限清单里已有 【事实】，可照 `util/DownloadWakeLock` 的写法） | L1 |
| 6 | MediaSession 冲突（视频页 + 音频服务同时活跃） | 先从听歌页进视频播放页 | 系统媒体键投给错的一方（通常是最新活跃的） | 明确策略：进入视频播放页时先 `AudioPlayerService.pause()` 并释放会话；退出视频页不自动续播 | L1 |
| 7 | API19 类加载崩溃 | MediaSession 类型出现在字段/方法签名 | 低版本设备整个页面起不来（cyq 真实踩过 【事实】） | §3.2 的单独成类 + `Any?` 守卫 | L0（编译期看不出，需 API19 设备跑） |
| 8 | 通知渠道合并的副作用 | 与视频共用「音乐播放」渠道 | 用户关掉它，视频锁屏控制也一起没了 | 文案里说明，或最终分成两渠道（成本很低，改主意容易） | — |
| 9 | 起播失败提示 | 取流失败 / CDN 403 / 音轨为空 | 用户看到「播不了」但不知道为什么 | 复用 `PlayerApi.describePlayUrlFailure()` :1287 与 `NoPlayUrlException` 【事实】；文案要区分「未登录 / 地区限制 / 版权下架」 | L1 |
| 10 | URL 过期 | 缓存 URL 超过 120 分钟 【事实】 | 播放中途 403 | 只缓存 10 分钟；每次续播前校验；**绝不把 URL 写进持久存档** | L1 |
| 11 | 自动跳歌失控 | 连续失败重试 | 一路跳到底，用户听着听着队列空了 | 照 cyq：`MAX_CONSECUTIVE_FAILURES=3` + 失败按完整 URL 记忆 10 分钟（`failedUrls`）【事实】 | L1 |
| 12 | 耗电 | `vn=1` + 熄屏 | 手表续航 | 纯音频已经是最省的解码路径；**听歌页不保亮屏**（§7.2）是最大的一笔 | L2 |
| 13 | 队列 token 过大 | 几千项收藏夹 `queue.encode()` | SharedPreferences 写入卡顿（`MAX_TOKEN_CHARS=256KB` 已是上限 【事实】） | 超限就只存「当前 + 前后各 50 项」，或干脆不存 token 只存当前项 | L1 |

## 10. 需要用户拍板的 4 个决策点

| # | 决策 | 选项 A | 选项 B（推荐） | 推荐理由 |
| --- | --- | --- | --- | --- |
| 1 | 音频焦点要不要请求 | **不请求**，只补「拔耳机暂停」（照 cyq 的「真混音」） | **请求 `AUDIOFOCUS_GAIN`**，沿用 `AudioFocusPolicy`（LOSS→暂停，CAN_DUCK→不动），另补拔耳机暂停 | 手表上听歌与看视频互斥，「混音」无收益；不请求焦点的代价是来电/播报叠音，用户会当场投诉 |
| 2 | 队列存档怎么隔离 | 音频与视频**共用**一组 key（改动最小） | **复用 `PlayQueue` 类，`PlaySessionStore` 加两组音频 key**（视频侧零变化） | 共用会让「听完收藏夹后打开视频页」恢复成音频进度；`PlayQueue` 是纯逻辑，复用不引入耦合 |
| 3 | 要不要做 loopback 读穿缓存（cyq 的 559 行代理） | **做**：`127.0.0.1` 代理 + cid 键 + 80MB 前缀缓存 | **不做**：只做「下一首 URL 预取 + Range 探测」（M2，约 80 行） | 600 行网络层代码换「拖动不重建连接」，收益未证实；先量 M1 的切歌耗时再决定 |
| 4 | 歌词要不要网易云兜底 | **要**：新增 `music.163.com` 等域名 + 逆向接口 + LRC 打分 | **不要**：只做 B 站 CC 字幕（零新域名，接口现成） | 网易云无公开 API、匹配靠打分猜、且把非 B 站内容缓存进客户端会拉长分发风险；B 站 AI 字幕在听歌场景已够用 |

## 11. 验证计划

### L0（静态，每次改动都做）

- `./gradlew.bat :app:assembleDebug` 两 flavor；
- `./gradlew.bat :app:testStandardDebugUnitTest` / `:testCoexistDebugUnitTest`；
- 新增纯逻辑单测：`AudioFocusPolicyTest`（noisy 判据）、`AudioPlaybackStateTest`、`PlaySessionStoreTest`（音频 key 往返 + 老文本兼容）、`AudioPrefetchPolicyTest`（M2）。

### L1（设备可观测，两轮上限）

| 要证明的事 | 怎么读出来 |
| --- | --- |
| 服务真的在前台且类型正确 | `adb shell dumpsys activity services <pkg>` 看 `isForeground` / `foregroundServiceType` |
| 会话真的注册上了 | `adb shell dumpsys media_session` 找 `BiliAudioPlayer` |
| 页面关了还在播 | 退出听歌页后重复上一条；再 `dumpsys audio` 看 STREAM_MUSIC 是否有活跃 player |
| 起播耗时 | `logcat -s audio-diag`（沿用 cyq 的 TAG 便于对照） |
| 拔耳机暂停 | `adb shell media dispatch key` 造不出 noisy；用 `adb shell am broadcast -a android.media.AUDIO_BECOMING_NOISY` 或真机拔线 |
| `127.0.0.1` 明文是否可用（仅当决策 3 选 A） | 10 行探针：`IjkMediaPlayer` 播一个本地 `http://127.0.0.1:PORT` 文件，看是否报 `-10000` |

### L2（人工验收，必须交接给人）

- 听感：起播有没有爆音、切歌空档多久、连续听 30 首是否漂移/卡顿；
- 锁屏后是否继续、进电梯断网再恢复是否续上；
- 拔耳机是否立刻停；
- 圆屏上单行歌词的可读性；
- 续航：听 30 分钟的耗电百分比（与「听视频」模式对照）。

## 12. 参考

- 现状与坑：`docs/architecture-map.md` §7.4（播放器现状 / MediaSession 的 API19 禁令）、§7.5（后台播放服务）、§7.52（亮屏标志，**听歌页要反着来**）
- 接口硬约束：`docs/bilibili-api-notes.md` §3.1（URL 120 分钟）、§3.4（字幕需登录 + `hdslb.com` 已在白名单）、§3.12（防盗链只要 Referer）、§4（「听视频 + AI 字幕」的定位）
- 既有设计：`docs/superpowers/specs/2026-10-25-dash-merge-single-file.md`（DASH 双文件的来龙去脉）、`2026-11-06-player-ui-custom-layout.md`（播放页自定义布局）
- 参考实现：`D:\Users\ASUS\Desktop\DEVELOP\fork-analysis\Re-BiliTerminal-cyq`（`AudioPlayerService.java` / `AudioPlayerActivity.java` / `LyricProvider.java` / `MediaSessionHelper.java`）
