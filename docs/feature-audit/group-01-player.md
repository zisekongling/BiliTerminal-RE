# 播放器核心功能审查（group-01-player）

> 审查范围：RE:哔哩终端（ReBiliClient）播放器核心相关源码。逐文件、极细颗粒度列出所有功能与用法（含隐藏/实验功能）。所有设置项均标注其入口（设置页开关名，即 `SharedPreferencesUtil` 键名）。

---

## 1. PlayerActivity.kt（视频播放主页面，3011 行）

视频播放器主界面。支持在线视频（普通/互动/多P/直播/短视频模式）、本地视频/音频、听视频模式、弹幕、字幕、清晰度切换、倍速、分P、分段（看点）、高能进度条、媒体会话、横竖屏、手势缩放等。

### 1.1 Intent Extra 输入（由外部传入）
- **url**（String，必填）：视频流地址。为 null 则 `finish()` 退出。
- **danmaku**（String）：弹幕 URL（在线）或本地弹幕文件路径。为空则 `hasDanmaku=false`。用于本地视频时，若文件存在则直接解析，否则关闭弹幕。
- **title**（String）：视频标题，显示在顶部标题栏。
- **aid / cid / mid**（Long）：视频/分P/UP主ID，用于弹幕、高能进度、分段、互动、历史上报等。
- **progress**（Int）：续播进度（毫秒）。
- **live_mode**（Boolean）：是否直播模式（隐藏进度条/播放按钮，走直播弹幕 WebSocket）。
- **pagenames**（ArrayList<String>）、**cids**（Long[]）、**currentPageIndex**（Int）：多P分页信息（分P选择、自动连播、媒体会话切P）。
- **edgeId**（Long）：互动视频起始边ID。
- **qnStrList**（String[]）、**qnValueList**（Int[]）、**currentQuality**（Int）：清晰度列表及当前清晰度。
- **isShortVideoMode**（Boolean）：短视频模式（显示"查看视频详情"按钮）。
- **audio_only**（Boolean）：本地音频文件标记，强制进入听视频模式。
- **audio_track_url**（String）：DASH 分离音频文件路径，用于音画分离 fallback（用 Android MediaPlayer 单独播放音频并与视频同步）。
- **videoFileList / danmakuFileList**（ArrayList<String>）：本地虚拟合集分页的视频/弹幕文件列表。

### 1.2 Intent Result 输出（`finish()` 回传）
- **progress**：退出时当前播放进度（毫秒）。
- **isPlaying**：退出时是否在播放。
- **isDanmakuEnabled**：弹幕是否开启（`!isDanmakuVisible`）。
- **quality**：当前清晰度。
- 无 ijkPlayer 时返回 `RESULT_CANCELED`。

### 1.3 启动时的全局设置读取
- 主题：按设置页主题键应用对应 Theme（粉/蓝/绿/紫/彩虹/灰）。
- 自动横屏：`player_autolandscape` 或 `ui_landscape` 为 true 时，以 `SENSOR_LANDSCAPE` 启动，否则强制竖屏。
- 软件旋转实验开关 `dev_player_rotate_software`：开启后默认横屏被禁用并提示"不支持默认横屏！"，旋转改由软件方式（旋转整个根布局）。
- 媒体会话：设置页 `PLAYER_MEDIA_SESSION_ENABLE`（player_media_session_enable）开启后初始化 MediaSession。
- 电池显示：顶部电池图标（API>=21 读取电量百分比）。
- 循环开关默认：`player_loop`。
- 听视频模式默认：`player_audio_only`。

### 1.4 播放器初始化（setDisplay / MPPrepare）
- 解码模式：听视频模式强制 `vn=1`（无视频解码）；普通模式按 `player_codec`（设置页"硬解码"）决定 `mediacodec`。
- 音频输出：`player_audio`（设置页"OpenSL ES"）决定 `opensles`。
- 硬性选项：`mediacodec-auto-rotate`、`mediacodec-handle-resolution-change`、`framedrop=4`、`start-on-prepared=1`、`analyzeduration=100`、`soundtouch=1`、`dns_cache_clear=1`、`fflags=flush_packets`、`reconnect=1`。
- 在线视频额外：`packet-buffering=1`、`max-buffer-size=15MB`、`user_agent=NetWorkUtil.USER_AGENT_WEB`。
- 显示模式：`player_display`（设置页"TextureView"开关，默认 API<26 为 true）决定用 TextureView 还是 SurfaceView。
- 在线视频带 Referer=https://www.bilibili.com/ 与 Cookie 请求头。
- 缓冲开始/结束：显示加载动画，弹幕暂停/恢复，并显示实时下载速度 `tcpSpeed`（KB/s）。
- 出错：Toast 提示"播放器可能遇到错误！"并打印错误码。

### 1.5 弹幕功能
- 下载弹幕：默认走新接口（`NEW_DANMAKU_API` 设置键，默认 true）。新接口按视频时长估算分段，经 `DanmakuApi.getAllVideoDanmaku` 获取 protobuf 分段；失败/为空自动回退旧版 XML 接口（`downdanmuOld`，gzip 解压写缓存 danmaku.xml）。
- 弹幕样式配置（`streamDanmaku`，全部来自设置页）：
  - 最大行数 `player_danmaku_maxline`（默认15）。
  - 允许重叠 `player_danmaku_allowoverlap`（默认 true，作用于左右滚动与底部固定弹幕）。
  - 合并重复 `player_danmaku_mergeduplicate`。
  - 滚动速度系数 `player_danmaku_speed`（默认1.0）。
  - 字体缩放 `player_danmaku_size`（默认0.7）。
  - 透明度 `player_danmaku_transparency`（默认0.5）。
  - 描边样式 `DANMAKU_STYLE_STROKEN`。
- 弹幕视图回调：`prepared` 时发送一条欢迎弹幕（新接口文案"弹幕君准备完毕～(是新来的哦～)"，旧接口"(≧∇≦)"）；`updateTimer` 每帧将弹幕时间同步到视频当前进度。
- `addDanmaku`：向屏幕追加一条弹幕，可指定文字/颜色/字号/类型/背景色。
- 弹幕显示开关按钮 `btn_danmaku`：设置页 `player_ui_showDanmakuBtn` 控制按钮是否显示；`pref_switch_danmaku` 持久化弹幕开关状态（onPrepared 时恢复）。点击切换弹幕显示/隐藏，图标切换。
- 发送弹幕：`btn_danmaku_send` 打开发送卡片，输入框 `danmaku_send_edit` 发送。空内容提示"不能发送空弹幕喵"；调用 `DanmakuApi.sendVideoDanmakuByAid`（白字、普通弹幕类型1）在当前进度发送，成功后本地追加一条并清空输入框；结果非0提示发送失败。

### 1.6 直播弹幕（live_mode）
- `danmuSocketConnect`：拉取直播弹幕服务器信息（WBI 签名 getDanmuInfo），建立 wss 连接，携带 Cookie/Origin/UA。
- 连接成功后 `PlayerDanmuClientListener` 处理认证、心跳、解析各类直播消息（见该文件条目）。
- 直播模式隐藏：播放/暂停按钮、进度条、菜单/清晰度/循环/听视频/自动连播/分P按钮。
- 进度文字显示当前直播时间与在线人数。

### 1.7 手势（layout_control 触摸事件）
设置项开关：
- **双指缩放**：`player_scale`（默认 true）开启自研 ScaleGestureDetector 缩放视频画面（1~5倍）。
- **双指移动**：`player_doublemove`（默认 true）：单指/双指拖动视频画面位置（限制在缩放溢出范围内）。
- **双击快进/快退**：`player_doubletap_seek`（默认 false）+ `player_doubletap_seek_seconds`（默认10秒）：双击画面右1/3快进 N 秒、左1/3快退 N 秒、中间区域执行"双击动作"。
- **长按倍速**：`player_longclick`（默认 true）：长按画面以 3.0x 倍速播放（弹幕同步3x），松手恢复原倍速。
- 具体交互：
  - 单击：`clickUI`。300ms 内再次单击=双击逻辑（见下）；否则切换控制栏显示/隐藏（`showcon`/`hidecon`）。
  - 双击中间区域：`handleDoubleTapAction`——若设置页 `player_doubletap_restore_screen`（默认 false）开启且当前横屏，则双击退出横屏；否则播放/暂停（`playerPause`/`playerResume`）并显示控制栏。
  - 长按：进入3x 快进。
  - 拖动：单指/双指移动视频画面位置（缩放后可平移查看）。
  - 缩放：双指缩放画面 1~5 倍；缩放后 `can_reset=true`，此时快速双击可复位画面位置与比例（1.0）。
  - 任意手势触发后自动隐藏控制栏（`hidecon`），4秒无操作自动隐藏（`autohideReset`）。

### 1.8 控制栏显示/隐藏（showcon/hidecon）
- 显示：右控制列、顶栏、底部按钮、进度条、倍速文字、电池、调试按钮（按条件）。
- 隐藏：相应元素隐藏；圆屏模式下进度文字居中显示；若"更多"菜单开着会自动关闭。
- 自动隐藏计时 4 秒（`autohideReset`）。

### 1.9 控制栏按钮
- **播放/暂停** `btn_control`：`controlVideo()`——播放中则暂停；暂停中若进度接近结尾(<250ms)则先回到开头再播放；互动视频播完会先处理结尾问题。
- **循环** `btn_loop`：切换 `loop_enabled`，图标 loopon/loopoff。默认值来自设置页 `player_loop`。
- **旋转** `btn_rotate`：切换横竖屏（`dev_player_rotate_software` 开启时用软件旋转）。
- **菜单（更多）** `btn_menu`：展开/收起右侧第二排隐藏按钮 `right_second`（更多按钮列）。
- **弹幕发送** `btn_danmaku_send`：打开发弹幕卡片。
- **字幕** `btn_subtitle`：打开字幕选择卡片（`downSubtitle(true)`）。
- **听视频模式** `btn_audio_only`：切换听视频模式（`toggleAudioOnlyMode`）。本地音频文件（audio_only）时隐藏。
- **分P选择** `btn_page_selector`：多P时显示，打开分P选择卡片。
- **自动连播** `btn_auto_next`：多P时显示，切换自动连播下一P（`toggleAutoNext`）。
- **清晰度** `btn_quality`：`player_ui_showQualityBtn`（默认 true）且在线视频时显示，打开清晰度选择卡片。
- **分段（看点）** `btn_viewpoint`：加载到分段数据后显示，打开分段选择卡片。
- **调试** `btn_debug`：仅当设置页 `player_interaction_debug` 开启且当前是含隐藏变量的互动视频时显示，点击打开互动视频变量调试页（InteractionDebugActivity）。
- **音量加/减** `button_sound_add` / `button_sound_cut`：调整系统媒体音量，显示"音量：N%"3秒。
- **视频信息** `btn_video_info`：仅短视频模式显示，跳转 VideoInfoActivity（传 aid）。
- 按钮可见性开关（设置页）：
  - 旋转按钮 `player_ui_showRotateBtn`（默认 true）。
  - 分P按钮 `player_ui_showPageBtn`（默认 true）。
  - 清晰度按钮 `player_ui_showQualityBtn`（默认 true）。
  - 弹幕按钮 `player_ui_showDanmakuBtn`（默认 true）。

### 1.10 倍速
- 倍速档位：0.5 / 0.75 / 1.0 / 1.25 / 1.5 / 1.75 / 2.0 / 3.0。
- 显示：控制栏显示当前倍速文字 `text_speed`（如 "x 1.0"），点击打开倍速面板 `layout_speed`；面板内 `seekbar_speed` 滑动选择倍速，实时应用 `ijkPlayer.setSpeed` 与弹幕 `mDanmakuView.setSpeed`，松手200ms后自动关闭面板。
- 倍速文字随控制栏显示/隐藏。

### 1.11 进度条（HighEnergyProgressBar）
- 拖动进度条：显示当前时间/总时长；松手 `seekTo` 视频与弹幕。
- 缓冲进度：`secondaryProgress` 显示缓冲百分比。
- 高能进度条：设置页 `player_high_energy`（默认 false）开启且在线视频时加载高能数据，`setHighEnergyData` 在进度条上绘制粉色高能曲线。
- 续播：设置页 `player_from_last`（默认 true）且非直播时，若上次进度>5秒则自动跳转并提示"已从上次的位置播放"。

### 1.12 字幕
- 自动加载：设置页 `player_subtitle_autoshow`（默认 true）在启动/切P时自动加载字幕列表。
- 字幕选择卡片：列出所有字幕轨道（含 AI 字幕，AI 字幕仅在按钮手动打开或设置页 `player_subtitle_ai_allowed`（默认 false）开启时显示）。选中 `id==-1` 项=关闭字幕；否则加载该轨道。
- 字幕时移：`player_subtitle_delta`（默认0.3秒）用于字幕时间偏移。
- 无字幕时点击字幕按钮提示"本视频无字幕"。
- 字幕随播放进度自动切换显示（`showSubtitle` 按 from/to 匹配当前时间），无匹配时隐藏。
- 字幕按钮图标 subtitle_on/subtitle_off 反映加载状态。

### 1.13 分P / 自动连播
- 分P选择卡片：横向列表，显示 "P1 xxx"，当前项高亮粉色。
- 切换分P：`switchToPage`，支持在线（多P真实cid 或 虚拟合集cids存aid需再查视频信息）与本地合集（videoFileList/danmakuFileList）。
- 切换后重置：字幕、分段、互动视频数据、进度、弹幕文件均重载。
- 自动连播：播放完成且 `auto_next_enabled` 且还有下一P时自动切到下一P。

### 1.14 清晰度切换
- 切换卡片：横向列表显示清晰度名称，当前项高亮。
- 切换时以目标 qn 重新获取视频流（`PlayerApi.getVideo`），保持当前播放位置，重建播放器播放。
- 切换后更新清晰度列表与当前清晰度。

### 1.15 分段（视频看点 ViewPoint）
- 设置页 `player_show_viewpoints`（默认 true）开启且在线视频时加载分段列表。
- 分段卡片：显示各分段，当前播放位置对应分段高亮（`updateCurrentPosition`）。
- 点击分段跳转到该段起点，提示"跳转到: xxx"。

### 1.16 互动视频
- 启动/切P时 `loadInteractionVideo` 检测是否互动视频（读取 graph_version）。
- 视频播放完成：若存在 type==0 的问题自动选择第一个满足条件选项；type!=0 则弹出选择卡片（`showInteractionQuestion`）。
- 选择卡片：`cell_interaction_choice` 按钮，字体大小 `player_interaction_choice_size`（默认17f），显示所有非隐藏且条件满足的选项。
- 条件判断：用隐藏变量替换条件表达式中的变量ID，支持 `>= <= > < == !=` 比较（`evaluateExpression`）。
- 原生动作：解析 `nativeAction`（分号分隔的 `变量=表达式` 赋值），支持 `+`/`-` 运算（`executeNativeAction`/`evaluateValueExpression`）。
- 选项选择：跳转到目标边（不同cid则切P `jumpToInteractionPage`），否则恢复播放（若选项要求暂停则自动 resume）。
- 选项若 `pauseVideo==1` 会在展示时暂停视频。
- 调试：`player_interaction_debug` 开启后，含隐藏变量的互动视频在控制栏显示调试按钮，点击进入 `InteractionDebugActivity` 查看/修改变量。

### 1.17 听视频模式
- 关闭画面、隐藏弹幕/倍速/调试按钮，显示纯音频界面（标题+副标题），视频区域隐藏。
- 切换时重建 IJK 播放器并保持进度。
- 本地音频文件（audio_only）强制进入且隐藏听视频按钮。
- DASH 分离音频 fallback：`audioTrackUrl` 非空时用 Android MediaPlayer 单独播放音频，与视频同步；进度偏差>800ms 时重新对齐；seek/暂停/恢复同步控制。

### 1.18 媒体会话（MediaSession）
- 设置页 `PLAYER_MEDIA_SESSION_ENABLE` 开启（API>=21）。
- 支持：播放/暂停、上/下一P（多P时）、seek 到指定位置。
- 元数据：标题、时长。
- 播放状态 actions 按分P边界动态启用/禁用上/下一P。

### 1.19 系统按键/DPAD 遥控
- 播放准备后：
  - Enter / DPAD_CENTER：播放/暂停。
  - DPAD_LEFT / RIGHT：快退/快进10秒。
  - DPAD_UP / DOWN：音量加/减。

### 1.20 后台/生命周期
- 返回键：设置页 `back_disable` 为 true 时屏蔽返回键。
- onPause：设置页 `player_background` 为 true 时后台继续播放，否则暂停。
- onDestroy（真正退出时）：释放弹幕、播放器、外部音频、WebSocket、媒体会话；删除在线弹幕缓存文件；恢复屏幕方向设置。
- onNewIntent：直接 `finish()`（单实例场景）。
- 事件总线：设置页 `SNACKBAR_ENABLE`（Snackbar 开关）开启时注册 sticky SnackEvent，按时长 Toast 显示（防重复）。

### 1.21 圆屏适配
- `player_ui_round`（默认 false）开启时：进度条/在线人数/按钮/弹幕区/字幕/标题/时钟全部按圆屏重新布局（边距、居中、maxWidth）。

### 1.22 在线人数显示
- 设置页 `player_show_online`（默认 false）开启且在线视频时，每5秒查询观看人数并显示"N人在看"。

### 1.23 顶部标题栏
- 点击顶栏任意处 `finish()` 退出播放器。
- 顶部含 TextClock 时钟控件（圆屏模式下居中显示）。

### 1.24 其他
- 启动时加载动画 `loading_tv_shaking`。
- 缓存目录创建。
- `decompress`：gzip 解压工具（静态方法，弹幕XML用）。

---

## 2. ShortVideoPlayerActivity.kt（短视频播放页面，921 行）

B站短视频（竖屏信息流）播放器。内含 Activity + RecyclerView 适配器（PageHolder），支持上下滑动切换、自动播放、预加载、弹幕、倍速、缩放、音量等。

### 2.1 Activity 功能
- 数据加载：`VideoPreloadManager(preloadCount=3)` 预加载3条；`loadInitial` 加载，成功填充 ViewPager2 适配器，失败显示错误页+重试按钮（`retryButton`）。
- 上下滑动切换：ViewPager2 竖向（onPageSelected 暂停旧页、播放新页；滚动停止后 `playAtPosition`）。
- 后台/暂停恢复：onPause 暂停当前、记录是否在播；onResume 恢复；onStop 正在销毁时释放全部播放器。
- 屏幕旋转：`onConfigurationChanged` 更新尺寸并通知各页 `notifyScreenSizeChanged`。
- 返回键：先暂停当前页再 finish。
- 新手引导：`TutorialHelper.show`（tutorial_short_video，key="short_video"，第1次）。

### 2.2 PageHolder 手势
- **单击**：切换底部控制栏显示/隐藏。
- **双击**：播放/暂停。
- **长按**：打开视频详情页（VideoInfoActivity，传 aid）。
- **双指缩放**：1~3倍缩放画面（Matrix 变换），禁用父容器拦截触摸。

### 2.3 底部控制栏
- 自动隐藏：显示后10秒自动隐藏（`hideBottomRunnable`）。
- **播放/暂停** `button_video`：controlVideo（接近结尾则先回开头）。
- **弹幕开关** `button_danmaku`：切换弹幕显示/隐藏，图标 danmakuon/off。
- **音量加/减** `button_sound_add/cut`：调系统媒体音量，显示"音量：N%"3秒。
- **倍速** `button_speed`：循环切换 0.5/0.75/1.0/1.25/1.5/2.0，按钮文字实时显示。
- **进度条** `videoProgress`（HighEnergyProgressBar）：拖动 seek，显示当前/总时长，松手同步视频与弹幕。
- **顶栏返回** `top`：暂停并退出。

### 2.4 播放器
- 硬解 `mediacodec=1`、`mediacodec-all-videos=1`、`framedrop=5`、`opensles=0`、`start-on-prepared=0`（手动控制播放防弱网多视频同时播）、快速打开 `analyzeduration=1/probesize=1024/fflags=fastseek`、网络 `reconnect=1/timeout=10s/addrinfo_timeout=5s`、缓冲 `packet-buffering=0/max-buffer-size=8MB/min-frames=5/max_cached_duration=3000/infbuf=1`、UA、`skip_loop_filter=48`。
- TextureView 渲染，按视频比例自适应屏幕（居中）。
- 播放完成：循环播放（seek 0 + 重新播放）。
- 只活跃页自动播放；非活跃页准备完暂停显示封面播放图标。
- 封面加载（Glide），缓冲指示器。
- 弹幕：`DanmakuManager`，异步下载并 gzip 解压 XML 后 `loadFromXmlInput`。

### 2.5 生命周期
- onViewRecycled 释放播放器；releaseAll 释放全部；releasePlayer 清理定时器、弹幕、播放器、视图。
- 进度定时器 250ms 更新进度条与时间文字。

---

## 3. PageSelectorAdapter.kt（分P选择列表适配器，73 行）

用于播放器分P选择卡片的 RecyclerView 适配器。
- 数据：分P名称列表 + 当前选中索引。
- 显示：每项 "P{n} {名称}"；选中项粉字 + 卡片背景，未选中浅色字 + 无边框背景。
- 交互：点击选中并回调 `OnItemClickListener(index)`。
- 方法：`setSelectedItemIndex`（刷新新旧两项）、`setData`（重置数据）、`setOnItemClickListener`。

---

## 4. PlayerDanmuClientListener.kt（直播弹幕 WebSocket 监听，208 行）

直播弹幕 WebSocket 客户端监听器。
- 认证包：连接成功后发送认证（uid 按设置页 `live_by_guest`（默认 false）决定是否匿名；roomid；protover=3；platform=web；buvid3；type=2；key）。
- 心跳：收到认证成功（actionCode 8）后每 32 秒发送一次心跳（首次延迟3秒）。
- 消息解析（actionCode 5）：支持 brotli 解压或 JSON 直读：
  - **DANMU_MSG**：显示弹幕。设置页 `player_danmaku_showsender`（默认 true）开启时前缀"昵称："，否则纯内容。白色。
  - **WATCHED_CHANGE**：更新直播在线人数。
  - **INTERACT_WORD**：进入直播间消息（青色，小字号，固定类型4）。
  - **SEND_GIFT**：送礼消息（白字，粉色底）。
  - **ENTRY_EFFECT**：进场特效文案（蓝底）。
  - **NOTICE_MSG**：系统公告（红字，白底半透明）。
  - **ROOM_CHANGE**：直播标题变更，更新顶栏标题。
- 连接关闭/失败：停止心跳并打印日志。

---

## 5. ScaleGestureDetector.kt（自研双指缩放检测器，236 行）

为播放器实现的双指/快速缩放手势检测器（仿系统 ScaleGestureDetector 但不依赖 AndroidX，规避某些兼容问题）。
- 接口 `OnScaleGestureListener`：onScale / onScaleBegin / onScaleEnd；提供 `SimpleOnScaleGestureListener` 默认实现。
- 功能：双指间距（hypot）计算缩放比例 `getScaleFactor()`；支持手指增减（ACTION_POINTER_DOWN/UP）后的跨度重置。
- 快速缩放（quickScale）：targetSdk > JB_MR2 自动开启，用 GestureDetector 检测双击进入锚点缩放模式（ANCHORED_SCALE_MODE_DOUBLE_TAP），单指上下滑动触发缩放（`getScaleFactor` 按方向放大/缩小，SCALE_FACTOR=0.5）。
- 状态字段：mFocusX/Y（焦点）、mCurrSpan/mPrevSpan/mInitialSpan、mInProgress（缩放进行中）。
- 触屏滑动阈值 `mSpanSlop = scaledTouchSlop * 2`。

---

## 6. SubtitleAdapter.kt（字幕轨道选择适配器，77 行）

播放器字幕选择卡片的适配器。
- 数据：`Array<SubtitleLink>` 字幕轨道列表。
- 显示：每项一个 Button，文字为 `lang`（语言名）。
- 选中项：主题主色文字+主色背景；未选中：正文色+卡片色背景。
- 交互：点击选中并回调 `OnItemClickListener(index)`。
- 方法：`setData`、`setOnItemClickListener`、`selectedItemIndex` 属性（setter 自动刷新新旧项）。

---

## 7. ViewScaleGestureListener.kt（视频画面缩放监听，37 行）

播放器画面缩放的手势监听器（配合自研 ScaleGestureDetector）。
- onScale：以画面当前 scaleX 为基准乘缩放因子，限制在 1.0~5.0 倍，同时应用 scaleX/scaleY；非 1.0 时置 `can_reset=true`（供双击复位判断）。
- onScaleBegin：置 `scaling=true`（供 PlayerActivity 判断当前是否在缩放）。
- onScaleEnd：置 `scaling=false`。
- 画面隐藏（View.GONE）时不处理缩放。

---

## 8. QualityChooserActivity.kt（缓存清晰度选择页，180 行）

下载缓存时选择清晰度（含强制高清与仅音频）。
- 入口 Intent：`aid`、`bvid`、`page`（分P索引）。
- 顶部点击返回 `RESULT_CANCELED`。
- 获取视频信息后以最高画质 qn=127 请求，得到清晰度列表。
- **强制高清选项**（设置页 `force_high_quality_options`，默认 true）：在 API 列表末尾追加未返回的 4K 超清(120)/1080P高码率(112)/1080P高清(80)，标注"[强制]"。
- **仅音频**：列表末尾追加"仅音频"选项。
- 选项处理：
  - 普通分辨率：`PlayerApi.startDownloading` 以该 qn 下载，200ms 后 finish。
  - 强制高清：1080P高码率(112)与4K(120)需验证大会员（`VipApi.getVipInfo`，非会员提示"仅大会员可下载喵~"）；用对应 fnval=4048（DASH|HDR|4K|杜比全景声|杜比视界|8K|AV1）尝试获取，成功则 `startDownloading`，失败提示"该视频不支持{分辨率}分辨率，缓存失败"。
  - 仅音频：`PlayerApi.getVideoDash` 获取音频流，无音频提示"该视频没有可用的音频流"；否则 `startDownloadingAudioOnly` 下载纯音频，200ms 后 finish。

---

## 9. JumpToPlayerActivity.kt（播放跳转中转页，122 行）

从列表/详情页跳转到播放器或下载的中转 Activity，负责获取视频流并上报进度。
- Intent Extra：`data`（Parcelable PlayerData）、`download`（0=播放，1=视频下载，2=分P下载带 parent_title）、`cover`（封面，下载用）、`parent_title`。
- 流程：qn 为空(-1)时用设置页 `play_qn`（默认16）→ 请求视频（bangumi 走 `getBangumi`，否则 `getVideo`，download!=0 时仅取流不播放）→ 成功 `jump()`。
- 播放路径：`PlayerApi.jumpToPlayer` 构建 Intent，用 `launcher` 启动（StartActivityForResult）。返回后：
  - 若 `RESULT_OK`：读取 `progress`，异步上报历史进度 `HistoryApi.reportHistory(aid, cid, progress/1000)`，然后 finish。
  - 返回前显示"等待退出播放后上报进度（点击跳过）"，点击文本直接 finish（`setClickExit`）。
- 下载路径：download==1/2 时跳转 DownloadActivity（type=download，带 link/danmaku/title/cover/parent_title）。
- 错误处理（点击文本退出）：
  - 网络错误："网络错误！请检查你的网络连接是否正常"。
  - JSONException："视频获取失败！可能的原因：1.仅大会员可播放 2.接口失效 清除应用数据也许可以解决"。
  - ActivityNotFoundException："跳转失败！请安装对应的播放器或在设置中选择正确的播放器…"。

---

## 10. MultiPageActivity.kt（多P选择页，80 行）

选择视频分P（播放或缓存）。
- Intent Extra：`data`（PlayerData，含 aid）、`download`（0=播放，1=下载）。
- 顶部点击返回。
- 按 aid 获取视频信息，展示分P列表（PageChooseAdapter）。
- **播放模式**（download!=1）：点击分P，若 cid 与历史不同则更新 PlayerData 的 cid/分P，`PlayerApi.startGettingUrl` 取流并跳转播放。
- **下载模式**（download==1）：点击分P检查下载目录，若已存在 `.DOWNLOADING` 标记提示"已在下载队列"，否则提示"已下载完成"；未下载则跳转 QualityChooserActivity（传 page/aid/bvid）选择清晰度缓存。

---

## 11. InteractionDebugActivity.kt（互动视频变量调试页，45 行）

互动视频隐藏变量调试界面（实验功能）。
- 入口：由 PlayerActivity 的调试按钮（设置页 `player_interaction_debug` 开启）进入，`setInteractionData` 静态传入当前互动视频数据。
- 无数据/无隐藏变量直接 finish。
- 页面标题"互动视频变量调试"，用 `InteractionDebugAdapter` 列表展示隐藏变量（id/值等，可编辑调试）。
- onDestroy 清空静态数据。

---

## 12. BatteryView.kt（电池电量自绘控件，83 行）

播放器顶部电池图标（自绘 View）。
- `setPower(percent)`：设置电量百分比（<0 钳为0），`setCharging(charging)` 设置充电状态。
- 绘制：白色描边电池框 + 正极头；填充色按状态：充电=绿色、电量<=20%=红色、否则白色。
- 由 PlayerActivity 在启动/显示控制栏时更新。

---

## 13. HighEnergyProgressBar.kt（高能进度条，125 行）

带高能曲线绘制的 SeekBar（继承 AppCompatSeekBar），播放器与短视频共用。
- `setHighEnergyData(FloatArray, stepSec)`：设置高能数据（每 stepSec 秒一个值）。
- `setShowHighEnergy(show)` / `clearHighEnergyData()`：开关/清除高能显示。
- 绘制：粉色描边曲线（`0xA8FB7299`）+ 半透明粉色填充（`0x33FB7299`），x 按时间映射进度条宽度，y 按密度（值/最大值 的 0.7 次方）映射高度（最大波高80%）。
- 常规 SeekBar 功能（进度、缓冲 secondary、拖动回调）照常由父类提供。

---

## 14. MarqueeTextView.kt（跑马灯文本控件，36 行）

自动滚动文字（用于标题等）。
- 构造时调用 `setMarquee()`。
- 设置页 `marquee_enable`（默认 true）开启时：`isSelected=true` + ellipsize=MARQUEE + 单行 + 无限重复 + 可聚焦，文字自动横向滚动；关闭时退化为单行 END 省略。

---

## 15. TextClock.kt（时钟文本控件，81 行）

显示当前时间的 TextView（播放器顶栏时钟，圆屏模式使用）。
- 格式 `HH:mm`，加粗字体。
- 每整分钟对齐刷新（`60000 - now % 60000` 延迟到下一分钟）。
- `startTick()`/`stopTick()`：启停；随 onAttachedToWindow/onDetachedFromWindow、屏幕亮灭、可见性聚合自动启停。

---

## 16. PhotoViewpager.kt（图片ViewPager，30 行）

图片浏览用 ViewPager 子类，处理多指缩放与翻页冲突。
- 覆盖 `requestDisallowInterceptTouchEvent` 记录是否禁止父拦截。
- 覆盖 `dispatchTouchEvent`：当多指（pointerCount>1）且禁止拦截时，临时放开拦截交给 super 分发（便于图片缩放），再恢复禁止拦截。
- 用途：详情页多图浏览时，双指缩放图片不触发父容器滑动。

---

## 17. RadiusBackgroundSpan.kt（圆角背景文本Span，63 行）

给文字绘制圆角背景的 `ReplacementSpan`。
- 参数：margin（外边距）、radius（圆角半径）、textColor、bgColor、maxHeight（最大高度，超出时上移，默认不限）。
- `getSize`：文本宽度 + 2*margin。
- `draw`：绘制圆角矩形背景 + 垂直/水平居中文字（用字号度量校正基线偏移）。
- 用途：播放器内给某段文字加圆角标签背景（如弹幕/通知样式文本）。
