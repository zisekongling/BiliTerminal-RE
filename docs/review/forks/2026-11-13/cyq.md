# Re-BiliTerminal-cyq 功能差距清单

> 分析对象：GitHub `cyq114514/Re-BiliTerminal`（本地 `fork-analysis/Re-BiliTerminal-cyq`）。
> 参照：本仓库（RE:哔哩终端 / ReBiliClient，Kotlin 重构分支）。
> 范围：**只做能力差距与功能优化**，不含安全/规范审计。
> 证据格式：`[sib] 相对路径:行号`（对方）、`[ours] 相对路径:行号`（我方）；每条结论标【事实】/【推测】。

## 0. 基线

| 项 | 对方 | 我方 |
| --- | --- | --- |
| HEAD | `c4935a9`（2026-11-08） | `965e9781` |
| 版本 | 1.3.5（versionCode 20261115） | 26.11.13 |
| 语言/规模 | 纯 Java，466 个 `.java` / 79,385 行 | Kotlin，424 个 `.kt` |
| Activity | 116 个 `*Activity.java`（含 5 个基类，实际约 104 个） | 约 118 个 `*Activity.kt` |
| minSdk / targetSdk | 19 / **26** | 19 / **34** |
| applicationId | `com.RobinNotBad.BiliClient.re` | `com.RobinNotBad.BiliClient` |

【事实】结构差异决定移植成本：

- 对方设置页**全部用代码构建**（`model/SettingSection.java` 24 行 + `adapter/SettingsAdapter.java` 424 行 + 一批 `SettingXxxActivity`），`res/xml` 下没有任何 preference XML，设置项 key 全部散在代码里（已 dump 约 90 个 key）。
- 我方设置页收敛在 `activity/settings/SettingGroupActivity.kt`（969 行）+ `SettingsIndex.kt` 索引 + `SettingText.kt` 文案表；对方同名项往往对应我方一个分组，**不能按 Activity 一一对应**，必须按「功能」比。
- 我方 UI 有一套对方没有的体系（`ui/appearance/GlassStyle.kt`、`ui/appearance/ColorScheme.kt`、`ui/widget/TopbarIsland.kt`、`IslandPolicy.kt`、`SegmentedBarStyle.kt`、`TerminalDialog.kt`、`HighEnergyProgressBar.kt`），所以对方「新版美学设计 `NEW_UI_DESIGN`」在我方**无对应概念**，不计入差距。
- 对方 `util/` 保留了大量**策略类 + 详注**（如 `util/SegmentSkipController.java` 180 行里 100 行是注释与真机日志结论），这与其更新日志的高密度同源，是本仓库值得借鉴的工程习惯之一。

### 0.1 核实方法与抽查记录

方法：①读 `readme.md` + `docs/更新日志.md` 开头的「版本索引」大表（第 7–39 行，一句话用户视角 + 技术要点）拿到 1.0.0→1.3.5 全量宣传清单；②全量枚举 104 个 activity 与 49 个 `api/` 类，与我对方名单做**双向差分**；③对每条候选回代码读实现；④对我方同样回代码 grep 核实现状（旧审计 `.compare-ctx/findings` 基于对方 1.2.3，仅当线索）。

抽查 5 条对方「已实现」宣称的结果：

1. **分组视图互关徽章（1.3.5）→ 真，但是补丁式**：`[sib] api/FollowApi.java:112` `fillMutualFollow(List<UserInfo>)`，注释自承 `/x/relation/tag` 返回的 `attribute` 恒为 0（`[sib] api/FollowApi.java:96`），改走 `x/relation/relations` 批量补（`:122`），调用点 `[sib] activity/user/FollowUsersActivity.java:143`、`:162`。→ 我方已有同能力（`mutual` 全仓 36 处，`[ours] adapter/FollowGroupAdapter.kt:279`），**不构成差距**。
2. **弹幕屏蔽词（1.3.3）→ 真**：`[sib] activity/player/PlayerActivity.java:3037` `filterBlockedDanmaku(BaseDanmakuParser)`，按 `[,，、;；\s]+` 切词、在 `prepared` 回调里整条摘除；注释如实标注边界「只作用于批量装载的弹幕，自己发送的不走这里」。→ 我方**没有**（见 F2）。
3. **直播发弹幕（1.3.4）→ 真**：`[sib] api/DanmakuApi.java:50-55` POST `api.live.bilibili.com/msg/send`，分支在 `[sib] activity/player/PlayerActivity.java:4375`。→ 我方已有 `[ours] api/LiveDanmakuApi.java`（全仓 67 处命中），**不构成差距**。
4. **第三方番剧播放源（1.3.1）→ 真，但范围比宣称窄**：`AnimekoSelectorSource` 只实现 Animeko `web-selector` 的**纯 HTTP 子集**（`[sib] source/animeko/AnimekoSelectorSource.java:81` search / `:366` channels / `:467` collectEpisodes），另有 `MacCmsPlayer`（153 行）与 `WebViewSniffer`（181 行）两条兜底解析；不是「任意规则全支持」。
5. **长视频 66 分钟后没弹幕（1.3.4-fix）→ 代码改了，但对方自承未真机验证**：改动见 `[sib] api/DanmakuApi.java:179-218`，而 `[sib] docs/更新日志.md` 该版「四、验证与未验证」明确写「未验证（需真机）：>66 分钟处弹幕是否出现」。→ 引用时按【事实=代码已改／结论未验证】处理。

## 1. 对方有、我方没有的功能

### 1.1 播放器与弹幕

#### F1 ±N 秒快进/快退按钮【事实】

- **对方**：`[sib] activity/player/PlayerActivity.java:3266-3272` `updateSeekButtons()`——按 `player_seek_buttons`（默认 `true`）把 `button_seek_back` / `button_seek_forward` 的文字写成 `"-"+seekStep+"s"` 并显隐，绑定在 `:845-846`；步长可配（设置页把「快进/快退秒数」写进同一 key 组）。
- **行为**：播放器底部常驻两个 ±N 秒按钮，点一下按固定步长跳，不再依赖手势精度。
- **我方**：`[ours] res/layout/activity_player.xml:785/799` 只有音量 `button_sound_cut` / `button_sound_add`，**没有任何 seek 按钮**；横向拖动 seek 是有的（`[ours] activity/player/PlayerSeekPolicies.kt:64` `targetMs(...)` 按滑行距离换算），但手表小屏上手指一滑就是几分钟，精确定位回退 10 秒很别扭。
- **成本/冲突点**：**小**。冲突在底部按钮位已满（音量±、画面、画质、选集、调试），手表屏宽有限——照搬会挤，建议做成「音量±长按 = seek」或把 seek 按钮并进播放器锁定态控制条。
- **价值**：**高**。手表上没有键盘、没有遥控，±N 秒是最低成本的精确定位手段。

#### F2 弹幕屏蔽词【事实】

- **对方**：`[sib] activity/player/PlayerActivity.java:3031-3064` `filterBlockedDanmaku()`：读 `danmaku_blocked_words`，按 `[,，、;；\s]+` 切词，遍历 `parser.getDanmakus()` 收集含任一关键词的条目后 `removeItem`，异常全部吞掉（`catch (Throwable)`），只在装载阶段执行。
- **我方**：全仓 grep `屏蔽词|blockWord|blocked_words` **0 命中**。我方有大量弹幕显示参数（`player_danmaku_*`、`DanmakuSelfStylePolicy`），但没有内容维度的过滤。
- **成本/冲突点**：**小**，但**不能照抄**：我方弹幕走窗口式分段追加（`[ours] player/DanmakuStreamLoader.kt:274` `ensureWindow`，后续段走 `appendSegments`），过滤必须挂在**每次追加**的入口，而不是只在首窗口 prepared 时做一次；`DanmakuManager` 已有 `appendSegments` 的串行去重，挂点清晰。
- **价值**：**高**。手表端屏幕小、弹幕密度一高就没法看，「屏蔽词」是性价比最高的可访问性开关之一。

#### F3 长视频分段弹幕「按时长取满、空段跳过不停」【事实（代码）／推测（我方差多少）】

- **对方**：`[sib] api/DanmakuApi.java:179-218`——`durationKnown ? (maxDuration + 359) / 360 : 40` 段；时长未知才退化为「连续 3 段为空即停」并封顶 40 段；配套把 `durationMs` 塞进 `PlayerData` 的 Parcel 读写（更新日志 1.3.4-fix 二节），避免播放页拿不到时长。
- **我方**：`[ours] activity/player/PlayerActivity.kt:3088-3093` 同样有 `var estimatedDuration = 3600` 兜底、`ijkPlayer.duration` 采样后覆盖；窗口段数由 `[ours] player/DanmakuStreamLoader.kt:355` `DanmakuApi.segmentCountFor(maxDurationSec)` 决定（`[ours] api/DanmakuApi.java:120-122`）。→【推测】若 `attach` 那一刻 `duration` 还是 0，整片只按 3600 秒算 **10 段**，60 分钟之后的视频段落**永远补不上**（`ensureWindow` 的段数上限被 3600 钉死）。我方是窗口式设计，比对方「一次拉满」更省流量，但**同样继承了 3600 兜底这个上限**。
- **成本/冲突点**：**小**。改动是「把真实时长（`VideoInfo.pages.duration` 或 `JumpToPlayerActivity` 传入的时长度）优先于 `ijkPlayer.duration`」，或在时长补到后调一次「扩段数 + 补拉」。冲突点：`DanmakuStreamLoader` 的 `maxDurationSec` 是 `attach` 时冻结的（`:169-173`），扩容要新增入口而不能改字段。
- **价值**：**中高**。长视频（讲座、番剧合订、直播回放）在手表中占比不低，且症状是「后半程没弹幕」，用户很难归因。

#### F4 表冠手势缩放画面（图片/视频）+ 缩放灵敏度档【事实】

- **对方**：`[sib] activity/player/PlayerActivity.java:4535-4540`（`ui_rotatory_video_zoom` 开关 + `ui_rotatory_video_zoom_step` 步长）、`[sib] activity/ImageViewerActivity.java:49-56`（`ui_rotatory_image_zoom` / `_step`）；配套 `ui/widget/RotaryRecyclerView.java` 89 行、`RotaryScrollView.java` 59 行、`RotaryNestedScrollView.java` 59 行，实验室里 6 个开关 + 2 个步长。
- **我方**：`[ours] ui/widget/RotaryEncoderSupport.kt:88-113` 有 `isEnabled()` / `multipleOf(key)` / `sensitivityOf(key)`，滚轮滚动链路完整（`RotaryRecyclerView` / `RotaryScrollView` / `RotaryNestedScrollView` 三件套都在），但全仓 grep `rotaryZoom|rotary_zoom|zoomBy` **0 命中**——**表冠只滚不缩放**。
- **成本/冲突点**：**小–中**。冲突点：视频画面缩放要和已有的双指缩放/双击复位（`[ours] activity/player/ViewScaleGestureListener.kt`）共用同一套 transform，否则两路并存会互相覆盖。
- **价值**：**中**。手表屏幕小，看漫画分镜/视频细节几乎只能靠表冠；这是本方仓库「表冠滚动」能力的自然延伸，边际成本低。

#### F5 播放会话哨兵 `PlayerSentinel`【事实】

- **对方**：`[sib] util/PlayerSentinel.java:9-22` 类注释——进播放页同步 `commit` 写会话标记（注释说明必须 commit，原生崩溃后 `apply` 的异步落盘会丢），播放中更新「当前阶段」，正常销毁清除；下次启动若标记仍在即判「上个会话未正常结束」（native 崩溃/被 LMK 回收/被强杀，三类都进不了 Java 崩溃 handler）。冷启动检查点在 `[sib] util/PlayerSentinel.java` 被 `[sib] BiliTerminal.java:133` 调用；注释明确「只留本地，不上传」。
- **我方**：grep `PlayerSentinel|Sentinel` **0 命中**。我方播放器有停滞看门狗（`[ours] activity/player/PlayerActivity.kt:2642-2659` `STALL_RECOVER` / `MAX_STALL_RETRY`），但那只能在进程活着时救场；native 崩溃一行记录都不留。
- **成本/冲突点**：**小**（一个类 + 两处调用）。冲突点：我方已有自建崩溃上报通道（`[ours] activity/settings/SettingGroupActivity.kt:913-951`，`SettingsKeys.CRASH_REPORT_AUTO`），哨兵数据必须按对方口径**只进本地日志、不进上传队列**，否则会污染现有的崩溃聚类。
- **价值**：**中**。「点播放就闪退、三条日志零记录」这类问题，我方目前同样无法观测。

### 1.2 视频与详情页

#### F6 Bangumi（bgm.tv）番组资料体系与新番热门网格【事实】

- **对方**：`api/BgmApi.java` 799 行（独立 OkHttp、无 Cookie、多镜像降级 `api.bgmapi.com` → `api.kazumi.fyi` → 直连，`[sib] api/BgmApi.java:46-53`；`[sib] api/BgmApi.java:121-131` 说明 kazumi 的 `/v0/*` 需自签 401、只有热门接口免签，故把 kazumi 移出通用候选）+ `api/BgmCache.java` + `api/BgmLocalStore.java` + `activity/video/BgmSubjectActivity.java`；1.3.0 把番剧「新番」页签改成 Bangumi 热门网格（镜像优先）。
- **我方**：`BgmApi|BgmCache|BgmLocalStore` **0 命中**；番剧资料走 B 站自家 `api/BangumiApi.java` + `BangumiIndexApi.java` + `activity/video/BangumiActivity.kt` + `info/BangumiInfoFragment.kt`。没有「按季/按热度浏览番组、看简介与制作信息」这一层。
- **成本/冲突点**：**中**（799 行 + 缓存 + 一个 Activity + 首页页签改造）。冲突点有两个：①新增第三方域名（我方安全红线是只走 https 且明文白名单只放行 4 个域名，bgmapi 走 https 可行但仍需评审新增域）；②镜像长期可用性靠第三方，BgmApi 的 121-131 行注释本身就是「镜像不稳」的证据。
- **价值**：**中**。对手表端「找番」有实际用处，但属于番剧用户的纵深功能，不是主路径。

#### F7 详情页「视频摘要 / 笔记」入口可隐藏【事实】

- **对方**：`video_summary_note_enable` 开关（更新日志 1.3.3），详情页多出来的摘要/笔记入口可按需隐藏。
- **我方**：`[ours] activity/note/NoteActivity.kt` 有笔记页，但 grep `video_summary_note|SUMMARY_NOTE` **0 命中**——入口常驻、不可关。
- **成本/冲突点**：**小**（一个开关 + 一处 visibility）。冲突点：我方详情页入口由 `VideoInfoFragment.kt`（918 行）+ `SettingGroupActivity` 分组统一管理，加开关要进 `SettingsIndex.kt` 索引否则搜不到。
- **价值**：**低**。属于偏好型开关，手表端入口本来就少。

### 1.3 动态与社交

#### F8 转发菜单「分享得经验」【事实】

- **对方**：`[sib] api/LikeCoinFavApi.java:60` POST `https://api.bilibili.com/x/web-interface/share/add`；配套 `share_exp_aids` / `share_exp_day` 两个 key 记录「今天分享过哪些稿件」，避免重复无效分享（更新日志 1.3.0）。
- **我方**：grep `share/add|分享得经验|share_exp` **0 命中**——没有分享加经验这条路径（分享本身有）。
- **成本/冲突点**：**小**。冲突点：我方分享走系统 Intent 面板（无 App 内分享弹窗），要在分享动作前后插入「调用一次 share/add + 记当日计数」；注意别把「刷经验」做成自动循环（对方也是用户手动点一次记一次）。
- **价值**：**中**。手表端用户对「经验/等级」敏感，且实现成本极低。

### 1.4 私信与消息

#### F9 视频/动态转发到私信卡片（手写 protobuf / gRPC）【事实】

- **对方**：`api/ImGrpcApi.java`（对方 api 目录独有，我方无）+ `activity/message/ShareToMsgDialog.java`；实现是手写 protobuf 编解码 + `ImInterface/SendMsg`，`msg_type=7` 的卡片消息（更新日志 1.2.5）。
- **我方**：`[ours] activity/message/PrivateMsgActivity.kt:198` 文本 `sendMsg`、`:269` 图片 `msg_type=2`（上传图床后组装 content）。**没有卡片消息类型**，所以「把视频转发给好友」只能发链接文本。
- **成本/冲突点**：**大**（手写 protobuf + gRPC over HTTP/2 + 风控签名，任何一次服务端协议变更都会静默失效）。冲突点：我方 `api/` 是全同步阻塞 + `org.json` 的极简风格，引入 protobuf 编码器是**体系外新增**；且我方无 grpc 依赖（`app/build.gradle` 里 protobuf-javalite 早已移除）。
- **价值**：**中**。体验提升明显（卡片比链接好看得多），但投入/维护比很差，且手表端转发频率低。

### 1.5 空间与用户

**本域无新缺口（逐项核过）**：

- 性别 / IP 属地：我方已有（`[ours] api/UserInfoApi.java:59-101` 从 `x/space/wbi/acc/info` 取 `sex` / `ip_location`，渲染在 `[ours] activity/user/UserDynamicAdapter.kt:388`）。
- 关注分组互关徽章：我方已有（见 0.1 抽查 1）。
- 「TA 的投稿」空间内搜索与排序：我方已有（`[ours] activity/user/info/UserVideoFragment.kt:36` + `[ours] api/UserInfoApi.java:248/263` 走 `space/wbi/arc/search`）。

### 1.6 收藏与稍后再看

#### F10 「听歌模式」（收藏夹/本地列表整夹音频播放）【事实】

这是对方**体量最大、与我方完全没有交集**的一块（约 3.4k 行）：

- **对方实现**：
  - 前台服务 `[sib] service/AudioPlayerService.java` 1395 行——自持 `IjkMediaPlayer`（`vn=1` 纯音频解码）、自持 `MediaSession` 与通知渠道，注释明确「**不请求音频焦点**，避免抢占视频播放的焦点；只在耳机拔出时暂停」（`[sib] service/AudioPlayerService.java:70-83`）；多 CDN 换线 + 失败节点记忆（`:586` `failedUrls`、`:613` `healthOrder`）、playurl 按 cid 缓存 10 分钟、下一首预取（`:1014` `prefetchNext`）、多 P 自动展开（`:430`）。
  - 缓存代理 `[sib] service/AudioCacheProxy.java:35-50`——在 `127.0.0.1` 起只服务本进程的 HTTP 代理，按 `Range` 先读磁盘前缀、缺的部分回源边下边存；注释说明「ijk 没有 media3 的 `CacheDataSource`，拖回已播位置会重新建连」，等价于读穿缓存（`audio_fast_load`）。
  - 歌词 `[sib] util/LyricProvider.java:24-45`——**B 站 CC 字幕优先，网易云兜底**（`api/NeteaseApi.java`），按 cid 缓存 24 小时，界面角落标注来源；无字幕时按标题关键词搜歌并按**时长**匹配。
  - 界面 `[sib] activity/player/AudioPlayerActivity.java:43-62`（`seekBar` + `volumeBar` + `lyricLine` + `VolumeKeyHelper`）、队列页 `AudioQueueActivity.java` 115 行、`model/AudioQueue.java` 226 行、断点 `util/AudioProgressStore.java` 108 行、音轨选择 `util/AudioTrackPicker.java` 33 行、排队接口 `api/AudioQueueApi.java` 40 行。
  - 入口三处：收藏夹 `[sib] activity/user/favorite/FavoriteVideoListActivity.java:241/245`（`startQueue` → 打开听歌页）、本地列表 `[sib] activity/user/local/LocalListActivity.java:90`、以及全局「音频胶囊」`[sib] activity/base/BaseActivity.java:243-275`（任何页面顶部可回到听歌页）。
- **我方**：grep `听歌|AudioPlayerService|AudioQueue|lyric|歌词` **0 命中**；只有播放器内的「听视频/仅音频」模式（`[ours] activity/player/PlayerActivity.kt:187/219/361` `layout_audio_only`/`btn_audio_only`/`isAudioOnlyMode`）——那是「把视频当音频播」，**不是独立的听歌队列**。
- **成本/冲突点**：**大**（≈3.4k 行 + 一个新前台服务 + 一个新 Activity 家族）。冲突点集中：①音频焦点策略与对方相反（对方刻意混音，我方已有 `PlayerActivity` 的 `requestAudioFocus` 与 `service/PlaybackService.kt` 的 5 秒前台义务防护），混音还是独占要先定；②通知渠道/MediaSession 要与既有播放通知共存，否则两个通知打架；③歌词兜底引入网易云第三方接口（我方要新增域名与合规评审）。
- **价值**：**高**。手表是「戴在手腕上的耳机遥控器」，「收藏夹一键听歌」比「看视频」更贴合手表形态，是我方目前最大的产品空白。

### 1.7 直播

**本域我方不弱**：直播发弹幕已有（`[ours] api/LiveDanmakuApi.java`），弹幕渲染链路 67 处命中。仅「选集弹窗分区与倒序」等细节存疑，见 §5。

### 1.8 搜索

**本域无缺口**：搜索分类拖动排序我方已有（`[ours] activity/settings/SearchSortActivity.kt:84-85` 用 `ItemTouchHelper` 挂拖拽），设置项搜索我方也有（`[ours] activity/settings/SettingSearchActivity.kt` + `SettingsIndex.kt`），与对方 1.2.6 的同类改造等价。

### 1.9 下载与缓存

**本域我方更强**，无移植需求（下载引擎分片并发 + 原子替换 + 暂停恢复，见 §2 末）。对方独有的「音频快速加载」属 F10 子项，不单列。

### 1.10 设置·外观·可访问性

#### F11 启动首页可选（`launch_page`）【事实】

- **对方**：`[sib] util/SharedPreferencesUtil.java:33` 定义 `LAUNCH_PAGE`，`[sib] activity/SplashActivity.java:333` 读 `getInt(LAUNCH_PAGE, 0)` 决定启动后进哪一页（更新日志 1.3.4「启动首页」）。手表上可以固定落到「动态」或「收藏」。
- **我方**：grep `launch_page|启动首页` **0 命中**；`SplashActivity.kt` 固定跳主菜单，另有「续播上次页面」提示（`[ours] activity/video/info/VideoInfoFragment.kt:922` `msg_player_resume_page`）——是「问一句」，不是「固定落点」。
- **成本/冲突点**：**小**。冲突点：我方主菜单由 `MenuActivity` + `util/MenuConfig.kt` 决定（菜单项本身可配、可排序），启动落点要能引用「菜单第 N 项」而不能硬编码 Activity 类名，否则用户改了菜单顺序就落错页。
- **价值**：**中**。手表交互成本高，「少按两下」的收益比手机大。

#### F12 主题配色自定义（`ThemeColorActivity`）【事实/推测混合】

- **对方**：`activity/settings/ThemeColorActivity.java` 300 行 + `ui/theme/ThemeManager.java` 225 行 + `ui/theme/ThemeApplier.java` 301 行 + `ui/widget/AmbientBackground.java` 202 行（跟随配色的环境背景）；1.3.5 修了 minSdk 19 上 `GridLayout.spec(int,float)` 的 `NoSuchMethodError`（那是 API 21 才有的重载）。
- **我方**：`[ours] ui/appearance/ColorScheme.kt` 是**结构化的调色板对象**（PRIMARY/CARD/SURFACE/TEXT_* 等语义色），全仓 `THEME_COLOR|themeColor|主题色` 命中都是「取 `colorPrimary` 属性」的用法，**【推测】没有面向用户的配色选择页**。
- **成本/冲突点**：**中**。对方是「预设 + 全局 Applier 重新着色」，我方是「语义色 + XML 属性」，落地方式完全不同：改我方等于给 `ColorScheme` 加一层用户覆盖，再让所有 `GlassStyle`/`TerminalDialog` 取色点走覆盖值，属于**横切改造**。
- **价值**：**中低**。手表以黑白灰为主，配色偏好价值有限；但「夜间低对比度/高对比度」对可访问性有意义。

#### F13 界面放大后底部按钮可达（`fitBottomButtons`）【事实（对方）/推测（我方是否也有此病）】

- **对方**：`[sib] activity/player/PlayerActivity.java:3968-4003`——`bottom_buttons` 只包一层 `wrap_content` 子容器，原代码把 `-2`（MATCH_PARENT）乘上 scale 再取 max，结果恒被抬到 `24dp`，5 个 38dp 按钮全挤进 24dp 被裁；新版改为遍历**真正的按钮层**、按实测 `MarginLayoutParams` 累加所需宽度、下限按「可用宽度均分」兜底，宽屏保持原尺寸。
- **我方**：底部按钮排布在 `activity_player.xml` 里是平铺的（`button_video` / `button_pool` / `button_quality` / `button_sound_*` / `button_page_selector`），**没有同类按缩放倍率重排的代码**（grep `fitBottom` 只命中 `ShortVideoPlayerActivity.kt:808` 的 `initBottomButtons`，语义无关）。我方**有** dpi 缩放设置（`[ours] activity/settings/setup/SetupUIActivity.kt:27/83` 写 `dpi`），所以**【推测】**在 dpi>1 时同样会出现按钮挤压/裁切，只是还没有人报。
- **成本/冲突点**：**小**。前提是先做一次真机验证（dpi 1.5/2.0 下看底部一排是否溢出）。
- **价值**：**中**。属可访问性缺陷，而不是新功能。

### 1.11 通知与后台

**本域无整块缺口**：低电量提醒我方已有瞬时岛事件（`[ours] ui/appearance/IslandPolicy.kt:12-43`，低电量/公告/更新为瞬时事件、10 秒后让位）；后台播放通知我方有 `[ours] service/PlaybackService.kt:142-238`。仅「阈值可调」等细节见 §2。

### 1.12 杂项工具

#### F14 第三方番剧播放源体系（`source/` 44 文件 ≈4.6k 行）【事实】

- **对方规模**（均为对方独有目录）：解析层 `source/animeko/{AnimekoSelectorSource 566, AnimekoSelectorConfig 271, AnimekoMediaRule 65}`、`source/resolve/{MediaResolver 182, WebViewSniffer 181, MacCmsPlayer 153, HtmlVideoScanner 131, MediaUrls 76, MediaRule 27, DefaultMediaRule 54}`、`source/rule/{JsonPathLite 264, Templates 205, XPathPage 159, WebFetch 99, SafeRegex 92, EpisodeSorts 72, RuleLimits 57}`、网络层 `source/net/{SourceHttp 210, UrlGuard 128}`、编排层 `source/{SourceManager 372, SourceStore 218, SourceSubscriptions 180, SourceChannelHealth 173, SourceSearchTask 121, SourceDefaults 92, SourcePreferenceStore 80, SourceType 25}`、播放层 `source/play/{ThirdPartyPlayback 308, LocalHlsProxy 324, HlsPlaylist 73}`、弹幕 `source/danmaku/{DandanplayClient 237, ThirdPartyDanmaku 91, DanmakuXml 56}`、UI `activity/source/{SourceDetailActivity 316, SourceMatchActivity 253, SourceSearchActivity 183, SourceListActivity 166, SourceImportActivity 114}` + 3 个 adapter。
- **关键设计（值得抄的是这些，不是代码）**：
  - `[sib] source/SourceManager.java:12-40`：三条硬约束写在类注释里——**每次操作新建源实例**（不缓存，避免取消信号/节流互相污染）、**单源失败绝不向上抛**（只折算健康度）、并发上限；`MAX_CONCURRENT = 6`（注释记录了从 3 提到 6 的理由：60 个源串行时十几个 8s 超时就能把一页拖到一分钟）；搜索缓存 TTL 5 分钟 / LRU 120；连续失败源 `FAIL_BLOCK_MS = 10 分钟` 内跳过。
  - `[sib] source/SourceSearchTask.java:14-40`：**逐源独立回调**（快源先出、慢源后补、一个源失败不影响已出结果），回调统一切主线程，`cancel()` 后不再回调，`onSourceResult(..., costMs, done, total)` 带进度。
  - `[sib] source/net/UrlGuard.java:6-40`：远程规则是不可信输入，做字面量级准入（协议白名单 + 主机/IP 段黑名单），**注释主动写明「不做 DNS 解析，域名指向内网 IP 的绕过检测不到」**。
  - `[sib] source/play/ThirdPartyPlayback.java:262-286`：HLS 先抓原始 m3u8，**加密 HLS（`#EXT-X-KEY`）经 loopback 代理改写**，绕开 ijk 对 `crypto + https://host:非443` 的协议层限制；请求头按白名单透传（`media.headersForIntent()`）。
  - `[sib] source/SourceStore.java` / `SourceSubscriptions.java`：订阅制源列表 + 持久化 + 单源启用/停用（列表页 166 行几乎全是管理动作）。
- **我方**：grep `animeko|第三方源|SourceChannel|web-selector` **0 命中**，完全没有这一层。我方 `PlayerActivity.kt` 有第三方播放的**接入点**（`third_party_danmaku` 标记、外部 extra），但没有「发现源 → 搜索番剧 → 选线路 → 解析出媒体地址」的链路。
- **成本/冲突点**：**大**（4.6k 行、44 个文件、3 条解析链、外加 UI 五页）。冲突点极多：①我方 `api/` 全同步阻塞 + 无 DI，而它需要自己的无 Cookie 网络栈（`SourceHttp`）；②播放器要多一条 `TYPE_THIRD_PARTY` 分支与 HLS 代理（对方为此改了 `PlayerData`/`PlayerApi` 的取流与请求头）；③规则来自第三方 JSON/XPath，**安全面与维护面都在我方之外**（UrlGuard 自己都承认不做 DNS 解析）；④我方安全红线对新增域名/明文有明确约束。
- **价值**：**高（用户侧）／低（我方工程侧）**。它是对方 1.3.1 的旗舰功能，能显著扩大「能看什么」；但对手表端来说，涉及第三方视频源的合规与稳定性风险都由我方承担。

## 2. 我方有、对方做得更好的功能优化

这些是**双方都有**、但对方某一维度更细的地方。按「值不值得改我方」排序。

### O1 片头片尾跳过：我方档位更全，对方语义更细【事实】

- 我方：`[ours] player/SkipOpEdPrefs.kt` 五档是**「跳哪一段」**——0=关 / 1=仅头 / 2=仅尾 / 3=都跳 / 4=询问（`SkipOpEdPrefs.kt:60` 附近注释 `读取当前五档（0=关 / 1=仅头 / 2=仅尾 / 3=都跳 / 4=询问）`），另有 `FEATURE_ENABLED` 总闸、`DEFAULT_ENABLED = false`（`:34`）、双数据源（`view_points` + `bsbsb.top`，见 `[ours] activity/player/PlayerActivity.kt:862` 注释「数据源不同（`view_points` vs bsbsb.top）」）。
- 对方：`[sib] util/SegmentSkipMode.java:13-30` 五档是**「怎么跳」**——`ALWAYS / ONCE / MANUAL / SHOW_ONLY / DISABLED`，`DEFAULT = MANUAL`（`:31-37` 注释：跳过是主动改变播放位置，交用户自己点更稳妥）；`[sib] util/SegmentSkipController.java:114-157` 的 `onTick` 用 `handled` 闩锁 + `SEEK_COOLDOWN_MS=2000` + `REARM_MARGIN_MS=3000` + `MIN_REMAINING_MS=15000` + 每段最多跳 3 次四道闸防「反复加载」，每条闸都附真机日志理由。
- **结论**：我方**功能面更宽**（能分头/尾、有询问档、多一个数据源），但缺对方「**手动跳过按钮在区间内持续显示**」「**跳过一次**」「**仅提示**」三种交互语义。建议只吸收 `MANUAL`（区间内常驻可点按钮）一种，代价最小。
- 附带：对方的四道防抖闸是**注释级经验**，值得对照我方 `ViewPointSkip`（189 行）检查是否也有「seek 后位置回退被当成新穿越」的保护。

### O2 播放器会话哨兵 vs 我方停滞看门狗【见 F5】

我方 `[ours] activity/player/PlayerActivity.kt:2642-2659` 的看门狗只覆盖「进程还活着」；对方的哨兵覆盖「进程直接消失」。两者互补，不冲突。

### O3 收藏夹列表头部跟手【事实】

- 对方：`[sib] activity/user/favorite/FavoriteVideoListActivity.java:307-343`——头部用浮层 + 给列表 `paddingTop` 让位，滚动时按 `computeVerticalScrollOffset()` 移 `translationY`（1:1 跟手，注释记录「不会出现条目 2 倍速上移」），并删掉了 150ms 收起动画（1.3.5）。
- 我方：`[ours] activity/user/favorite/FavoriteVideoListActivity.kt:230-239` 走 `SegmentedBarStyle` + `setupAutoHideBars`（滚动即隐藏 `sortBar`/`filterBar`），是**「藏」**而不是**「跟」**。手表小屏上，「跟手」比「突然消失」更稳（不会因为一次误触滚屏就丢掉排序入口）。
- **价值**：中。属手感优化，收益取决于是否有人抱怨排序栏消失太快。

### O4 详情页/播放页的「时长」传递【事实】

对方把 `durationMs` 写进 `PlayerData` 的 Parcel（更新日志 1.3.4-fix 二节，明确指出「时长其实取到了，但过不了 Parcel」）；我方在播放页**现场**采样 `ijkPlayer.duration`（`[ours] activity/player/PlayerActivity.kt:3090-3093`），采样时机若早于 prepared 就是 0，落回 3600。→ 这是 F3 的根因侧优化，**改一处胜过改三处**。

### O5 低电量提醒的可配阈值【事实】

对方 `battery_low_alert_enable` / `battery_low_threshold` 两个 key；我方只有岛事件（`[ours] ui/appearance/IslandPolicy.kt:12-43`，无阈值/无开关）。手表电量本来就吃紧，阈值可配是低成本增强。

### O6 网络重试参数可配（实验室）【事实】

对方 `api_retry_interval_seconds`（默认 0.1）+ `api_retry_max_times`（默认 5）在实验室可调，配合 `dev_api_error_detail` 打印请求细节。我方 retry 逻辑写死在调用点（如 `[ours] activity/player/PlayerActivity.kt:625/2642-2659` 的停滞重试），**不可配、不可观测**。这条的移植价值主要在「调试与线上归因」，不在用户可见功能。

## 3. 不建议移植

1. **F9 手写 protobuf / gRPC 私信卡片**：协议随时变、我方无 grpc/protobuf 依赖，坏了就是静默失败；收益（卡片比链接好看）与维护成本严重不匹配。若真要做，优先找现成 REST 接口或直接发链接卡片文本。
2. **F14 第三方源体系整套**：4.6k 行 + 自建网络栈 + HLS loopback 代理 + 五页 UI，且规则来自第三方（UrlGuard 自承不做 DNS 解析）。若确实需要，建议**只移植设计约定**（`SourceManager` 的三条硬约束、`SourceSearchTask` 的逐源回调语义、`UrlGuard` 的白名单思路），而不是代码。
3. **F12 全局主题配色横切改造**：我方 UI 是语义色 + `GlassStyle`/`TerminalDialog` 体系，对方的「预设 + Applier 重新着色」套不进；性价比低于同等工作量的可访问性改进。
4. **`NEW_UI_DESIGN` 新旧双主题开关**：我方没有「旧主题」可回退，纯粹是对方的历史包袱。
5. **对方 `MinSdk 19 + targetSdk 26` 时代的兼容补丁**（`GridLayout.spec(int,float)` 之类）：我方 `minSdk 19` 但 `targetSdk 34`，且出问题的是对方自己的主题页；除非我方真的复用了同一 API，否则无意义。
6. **调试类 Activity 合集**（`TestActivity` / `InteractionDebugActivity` / 主动触发崩溃）：我方已有对应物（`[ours] activity/settings/TestActivity.kt`、`activity/InteractionDebugActivity.kt`），无需再引进一套。

## 4. 优先建议（Top 5–8）

| # | 功能 | 成本 | 价值 | 理由 |
| --- | --- | --- | --- | --- |
| 1 | **F2 弹幕屏蔽词** | 小 | 高 | 手表小屏最缺的弹幕降噪手段；我方零依赖，挂在 `DanmakuManager.appendSegments` 入口即可，且能一次覆盖「窗口补拉」的全部分段 |
| 2 | **F1 ±N 秒 seek 按钮** | 小 | 高 | 手表无键盘无遥控，手势滑动无法精确定位；可利用现有 `button_sound_*` 长按或播放器锁定态控制条，不新占屏宽 |
| 3 | **F3 + O4 长视频弹幕时长** | 小 | 中高 | 先修「时长来源」（Parcel/详情页传入优先于现场采样），再放开窗口段数上限；症状（后半程无弹幕）用户无法自行归因，修好口碑收益高 |
| 4 | **F10 听歌模式（分两批）** | 大 | 高 | 手表的产品形态就是「耳机遥控器」；建议第一批只做「前台音频服务 + 队列 + 通知 + 音频胶囊」，第二批再加歌词与缓存代理（歌词的网易云兜底涉及新域名，单独评审） |
| 5 | **F11 启动首页可选** | 小 | 中 | 一个 int 设置 + 落点按菜单项解析，投入极小；手表上「少按两下」的体感放大 |
| 6 | **F4 表冠缩放** | 小–中 | 中 | 我方表冠滚动链路已完整（`RotaryEncoderSupport` + 三件套），只差把事件映射到 transform；图片查看的收益最直接 |
| 7 | **F5 PlayerSentinel** | 小 | 中 | 一个类 + 两处调用，把我方「播放闪退无日志」变成可见数据；注意只进本地日志 |
| 8 | **F8 分享得经验** | 小 | 中 | 一次 POST + 当日去重计数，性价比高；需明确只在用户主动分享时触发 |

若只能做三件：**F2 → F1 → F3**（全部「小」成本、全部直击手表端真实痛点）。

## 5. 存疑

1. **F3 我方是否真的截断**：`estimatedDuration = 3600` 兜底在 `[ours] activity/player/PlayerActivity.kt:3088`，但 `downdanmuNew()` 的调用时机是否已在 `prepared` 之后（`ijkPlayer.duration` 已就绪）未逐行确认。需真机用 >60 分钟视频（如对方更新日志提到的 BV1uE4B6fE7X）验一次。
2. **F13 我方是否有同样的按钮裁切**：我方无 `fitBottomButtons` 同类代码，但有 dpi 缩放（`SetupUIActivity` 写 `dpi`）；dpi 1.5/2.0 下底部一排是否溢出**未验证**。
3. **F12 我方是否真的没有配色入口**：`ColorScheme.kt` 是结构化调色板，是否已有隐藏的用户可选预设未查全（`SettingGroupActivity.kt` 969 行的界面分组未逐项读）。
4. **私信刷新去重（1.3.4-fix 三节）**：对方加了 `msgSeqno` 去重 + `refreshInFlight` 互斥（定时器与发送回调并发会重复追加）；我方 `PrivateMsgActivity.kt` 是否已有等价保护**未核**——若无，这是「偶发重复消息」类 bug 的现成补丁。
5. **直播「选集弹窗分区与倒序」（1.3.4）**：对方宣称的功能细节未与我方 `LiveInfoActivity.kt` 逐项比对。
6. **对方「23 项 UI 审计」（1.2.9）**：无法逐项核实，未计入本清单任何条目。
7. **F6 的镜像可用性**：`api.bgmapi.com` / `api.kazumi.fyi` 均由第三方维护，对方自己的注释已经记录了 kazumi 部分路径 401，长期可用性存疑。
8. **弹弹play 第三方弹幕**：对方自带 `source/danmaku/DandanplayClient.java`（237 行）实现；我方 `[ours] activity/player/PlayerActivity.kt:3230` 有接入口、公开侧门面在 `[ours] util/OptionalFeatures.kt:331`，其实现细节不在本次可核对范围（**不作结论**）。

---

*本文所有 `[sib]` / `[ours]` 行号均取分析时的 HEAD（对方 `c4935a9`、我方 `965e9781`）。若仓库已推进，按符号名重新定位。*
