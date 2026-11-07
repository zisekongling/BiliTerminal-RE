# B 站接口接入常识与风控要点（长期备忘）

> 来源：`docs/archive/watch-optimization-research.md` §10（26.10.04 一手核对），2026-10-10 抽出为独立活文档。
> 用途：改 `api/` 下任何接口前查这里，尤其**错误码语义、风控处置、取流有效期、弹幕协议取舍**。
> 接口字段级快照在 `bilibili-API/`；网络层实现与坑见 `docs/architecture-map.md` §6 与 §7.35。

## 1. 来源与可信度

### 1.1 上游接口字典已永久关停 ⚠️

- `SocialSisterYi/bilibili-API-collect`（20,191★）README 已改为 `# Deprecated` / "本仓库停止维护并永久关停"，并附**律师函**措辞（指控"对非公开 API 及其调用逻辑、参数结构、访问控制及安全认证机制进行系统性收集并传播"），落款 **2026-01-28**；官方文档站 `socialsisteryi.github.io/bilibili-API-collect/` 现返回 404。
  来源：<https://github.com/SocialSisterYi/bilibili-API-collect>、<https://raw.githubusercontent.com/SocialSisterYi/bilibili-API-collect/master/README.md>
- **本项目已经在仓库根目录自带快照 `bilibili-API/`（197 个文件，`docs/` 下 195 篇 md）**，它是关停前的版本。另有贡献者复刻仓库 <https://github.com/pskdje/bilibili-API-collect>（master 同步至 2026-01-25），本文接口路径以这两份快照为准。
- **战略含义**：不要再把"抄现成端点清单"当长期模式。应把本项目**实际依赖的端点、参数、错误码固化为仓库内自有契约文档**，并建立"上游变更 → 快速自检"的机制。另外注意：该文档集为 **CC BY-NC 4.0**，且上游已收到律师函，仓库内自带快照的**合规风险**需要在发布前评估。

### 1.2 来源清单

**接口字典（本文结论的直接依据）**

- bilibili-API-collect（**已永久关停，2026-01-28**）— https://github.com/SocialSisterYi/bilibili-API-collect ；关停说明 https://raw.githubusercontent.com/SocialSisterYi/bilibili-API-collect/master/README.md
- bilibili-API-collect 贡献者复刻（快照 2026-01-25，用于本次接口核对）— https://github.com/pskdje/bilibili-API-collect
- 本仓库自带快照 `bilibili-API/` （关停前版本，197 文件 / 195 篇 md）

**纠偏 / 旁证来源**

- SponsorBlock 官方 README（用于纠偏"是否支持 B 站"）— https://raw.githubusercontent.com/ajayyy/SponsorBlock/master/README.md
- BiliRoaming（已 archived，收到律师函）— https://github.com/yujincheng08/BiliRoaming ；https://m.ithome.com/html/973202.htm
- B 站官方口径（正文可读的少数来源）：官方下载中心 https://app.bilibili.com/ ；腾讯应用宝 https://sj.qq.com/appdetail/tv.danmaku.bili ；小米应用商店 https://app.mi.com/details?id=tv.danmaku.bili
- 说明：`openhome.bilibili.com/doc`、`open-live.bilibili.com/document/`、`www.bilibili.com/blackboard/help.html` 等官方页均为 JS 渲染空壳，**只能读到标题，读不到正文**。
- 本仓库实测：其余结论均标注了 `文件:行号`，可直接核对。（归档文档另收有 Wear OS 官方文档与竞品仓库清单，与接口无关，不在此列出。）
- **B 站官方 Android 客户端静态分析（本地逆向资料，不入库）**：对 `tv.danmaku.bili` 9.15.0 / 9.16.1 的 APK 做 apktool + jadx 反编译后抽取的接口表（574 个 Retrofit 接口类 / 1738 方法、93 个 gRPC 服务 / 743 方法）。产物在本地 `biliapp/`（已被 `.gitignore` 忽略，仓库里看不到），下文凡标「9.16.1 静态分析」的结论都出自它——**是官方实现的事实，不是实测响应**。

### 1.3 可信度分级：哪些是一手实测、哪些是二手/待核实

**一手实测（可直接依赖）**

- §3 第 1~4、8、9 条：全部标注了 `bilibili-API/...md:行号` 原文，可直接核对。
- §3 第 5 条：搜索风控原文（快照 `docs/search/search_request.md:3`）**另加本仓库实测**——`search.bilibili.com/all?keyword=...` 直接返回 `<title>验证码_哔哩哔哩</title>`，确认 Web 页面模拟已不可用。
- §3 第 6 条：快照 `docs/misc/errcode.md` 有更长列表，本文**只逐字核对了 §3 提到的这几条**。
- §3 第 11 条：SponsorBlock 官方 README 全文抓取成功，确认其未提及 B 站。
- §2 速查表三列（登录 / wbi / csrf）：按快照文档逐端点核对。

**二手 / 待核实（用到时须再验）**

- GitHub `RobinNotBad/BiliClient` 返回 404（上游主仓只在 Gitee）；Gitee 无公开 API，`71★ / 1322 commits / 状态「暂停」` 取自网页。
- `Darock-Studio/Darock-Bili`（Apple Watch 版）因匿名 API 限流（HTTP 403）**未能核实**。
- `qingyiwebt/Biliw`、`nonomal/bilibili-for-AppleWatch` 仅见搜索结果中的个位数 star，**未核实**。
- Wear OS 是否存在其他仍在维护的 B 站客户端：**未查到**。
- **「Android 9+ 默认禁止明文 HTTP」未取到可引用的官方原文**：`developer.android.com/privacy-and-security/security-config` 与 `/about/versions/pie/android-9.0-changes-all` 均只读到导航或被截断（入口存在）。本仓库相关的依据只有 `bilibili-API` 快照 README 的"强制使用 https 协议"。
- **SponsorBlock API 正文与"B 站是否被其服务端收录"**：`wiki.sponsor.ajay.app` 抓取失败；其 README 全文未提 B 站 → 只能确认"官方目标平台是 YouTube"，不能确认"B 站被收录"。
- **官方 App 底部 Tab 的具体数量与顺序**：官方页面全是 JS 空壳，商店页仅能确认存在"我的"Tab；归档文档 §7.4 的模块划分是按官方子站与接口域反推的**模块划分**，不是 Tab 顺序断言。
- **动态发布的完整参数取值表**（定时 / 可见范围的字段枚举）：只读到接口与部分字段名，未逐字段核对 `bilibili-API/docs/dynamic/publish.md` 全文。
- **`-352` / `-412` / `-111` 之外的错误码语义**：见上，未逐条核对。
- **标「9.16.1 静态分析」的接口事实**（§3 第 15 条、§6 末尾两条）：来自官方 APK 反编译出的 Retrofit 注解与 Moss 服务桩，**能证明客户端在发什么请求，不能证明服务端对这些请求的响应**。其中 §6 的 `x-bili-gaia-vtoken` 是「官方客户端会带这个头」的事实，本项目发 `batch-deal` 时偶发失败是否真由它引起，**尚未实测复现**——按「用到时须再验」对待。

## 2. 接口速查表（登录 / wbi / csrf 三列）

三列含义：**登录 = 需 SESSDATA；wbi = 需 `w_rid`+`wts`；csrf = 需 `bili_jct`**。

**播放 / 字幕**

| 端点 | 用途 | 登录 | wbi | csrf |
| --- | --- | --- | --- | --- |
| `x/player/wbi/playurl` | 取流（**现行唯一 Web 取流**） | 可选 | 是 | 否 |
| `x/player/wbi/v2` | 字幕 / 章节看点 / 播放器元数据 | 字幕需登录 | 是 | 否 |
| `x/web-interface/view/conclusion/get` | AI 总结 / AI 字幕（`part_subtitle[].timestamp/content` 带时间戳） | — | — | 否 |
| `bvc.bilivideo.com/pbp/data` | 高能进度条（`step_sec` + `events.default[]`） | 否 | 否 | 否 |
| `x/v2/history/report` | 观看进度上报 | 是 | 否 | 否 |
| `x/web-interface/archive/like`、`coin/add`、`like/triple` | 点赞 / 投币 / 一键三连 | 是（**且需 buvid3**） | 否 | **是** |
| `x/v3/fav/resource/batch-deal` | 收藏 / 取消收藏（`resources` + `add_media_ids` / `del_media_ids`，26.10.23 起唯一写入口） | 是 | 否 | **是** |

**列表 / 内容**

| 端点 | 用途 | 登录 | wbi | csrf |
| --- | --- | --- | --- | --- |
| `x/v2/history/toview`(+`/add` `/del` `/clear`) | 稍后再看 | 是 | 否 | 写操作**是** |
| `x/web-interface/history/cursor`、`x/v2/history` | 历史（游标翻页） | 是 | 否 | 否 |
| `x/web-interface/wbi/index/top/feed/rcmd` | 首页推荐流 | 否 | 是 | 否 |
| `polymer/web-dynamic/v1/feed/all` | 关注动态流 | 是 | 否 | 否 |
| `x/v2/reply/wbi/main` | 新版评论主楼（旧 `x/v2/reply/main` 已废弃） | 是 | **是** | 否 |
| `x/v2/reply/reply`、`x/v2/reply/dialog/cursor` | 楼中楼 / 游标翻页 | 是 | 否 | 否 |
| `x/v2/reply/add`、`/action`、`/hate`、`/report`、`/del`、`/top` | 评论写操作 | 是 | 否 | **是** |
| `pgc/web/timeline`、`pgc/review/user`、`pgc/web/season/section` | 番剧时间表 / 我的追番 / 分集 | 是 | 否 | 否 |
| `pgc/player/web/playurl` | PGC 取流（**必须带 Referer `https://www.bilibili.com`**） | 大会员决定清晰度 | — | — |
| `x/web-interface/wbi/search/all/v2`、`search/type`、`search/square` | 搜索 / 热搜 | 是（Cookies 需足量） | **是** | 否 |
| `s.search.bilibili.com/main/suggest` | 搜索建议（**根字段是 `result.tag[]`，不是 `data`**） | 否 | 否 | 否 |
| `live.bilibili.com/room/v1/Room/get_status_info_by_uids` | 按 uid 查开播状态（开播提醒首选） | 否 | 否 | 否 |
| `live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo` | 直播弹幕 WS 地址 + token | 否 | 否 | 否 |
| `x/report/click/now` | 服务器时间（`data.now` 秒）；**明文 http 可用**（26.10.27 实测 200），给「设备时间不对」提示用，请求不带 Cookie | 否 | 否 | 否 |

## 3. 必须知道的硬约束

1. **`playurl` 取回的 url 有效期 120 分钟**（`bilibili-API/docs/video/videostream_url.md:102`）→ 手表端必须"播放前校验 + 失败重取"，**不能把 url 缓存进离线队列复用**。
2. **FLV 已下线**（同上 `:104`），**分 P 视频只返回单 P url**（`:106`），换 P 必须带对应 `cid` 重新取。
3. **`qn` 在 DASH 格式下无效**（`:117`）；未登录默认 `qn=32`（480P）、登录 `qn=64`（720P）；720P 以上需登录，1080P60/HDR/杜比/会员内容需大会员。
   → **手表端策略**：`fnval=16`（DASH）只取**音频轨 + 低清视频轨**；音频轨最高 192K 正好适合弱网省电；UI 上直说"登录可解锁 720P"。
4. **字幕需要登录**：`x/player/wbi/v2` 的 `data.subtitle.subtitles[]` 在未登录时为空数组，`subtitle_url` 指向 `//aisubtitle.hdslb.com/...`。→ 想把"听视频 + AI 字幕"做成核心体验，**登录是前置条件**。
5. **搜索风控很凶**：`bilibili-API/docs/search/search_request.md:3` 原文——"B站于2022年8月24日更新了搜索api……如果Cookies不足会返回 `-412` 搜索被拦截。**如果没有cookies的话，请在搜索之前先GET一遍 `https://bilibili.com` 以获取cookies**"。
   → 可直接照抄的低成本缓解：**搜索/空间类接口前先"暖一次首页"**。另外实测 `search.bilibili.com/all?keyword=...` 直接返回 `<title>验证码_哔哩哔哩</title>` → 手表端**绝不要走 Web 页面模拟**，并把"需要人工过验证码"当成**不可自动恢复的失败态**，直接给用户提示。
6. **错误码语义（不要硬编码直觉映射）**：`-352` = 风控校验失败（UA 或 wbi 参数不合法）；`-412` = 客户端 IP 被风控拦截；`-111` = csrf 校验失败；`-101` = 账号未登录；**`-403` 在评论场景是"Wbi 签名错误"而非无权限**（`bilibili-API/docs/comment/list.md:867` 原文："Wbi 签名错误时返回 -403 而非 -352"）。
7. **`-352` 与设备指纹是同一件事的两面**：`-352` 时响应体带 `v_voucher`、响应头带 `x-bili-gaia-vvoucher`，处置链路是 `x/gaia-vgate/v1/register` → `/validate` 换 `gaia_vtoken`。**持久化 buvid3/buvid4/UA、不随机换指纹**是根本解法（本项目 §2.4 已做到）。
8. **弹幕协议取舍**：protobuf 版 `x/v2/dm/web/seg.so`（及 wbi 变体、BFS 直链 `i0.hdslb.com/bfs/dm/{data}.bin`）字段丰富但**必须引入 protobuf 运行时并维护 `.proto`**；xml 版 `x/v1/dm/list.so`（**deflate 压缩，必须解压**）与 `comment.bilibili.com/{cid}.xml` 简单。
   → **手表端一屏只显示 3~5 行滚动弹幕，用 xml 即可**，不要为弹幕引入 protobuf 编解码链路；只有要做彩色/高级弹幕/按类型过滤/弹幕点赞数时才值得上 protobuf。
9. **弹幕池是栈语义**：`bilibili-API/docs/danmaku/danmaku_xml.md:3` 原文——"实时弹幕池容量有限（根据视频类型 500-8000 条不等），占满后再发送会使实时弹幕池底部的弹幕压入历史弹幕池（类似于堆栈）"；弹幕池类型 `0 普通 / 1 字幕 / 2 特殊(代码/BAS) / 3 互动池`。
10. **登录方式**：官方扫码登录（Web）为 `passport-login/web/qrcode/generate` + `/poll`；**TV 端另有独立链路** `x/passport-tv-login/qrcode/auth_code` + `/poll`（备用域 `passport.snm0516.aisee.tv`）。→ 手表**无键盘，密码/短信登录不可行**；**TV 端扫码比 Web 端扫码更贴合"无键盘设备授权"的心智模型**，建议手表端引导用户用 TV 端链路的二维码。密码登录还需先取 `passport-login/web/key`（返回 hash 盐 + 公钥，**有效期仅 20 秒**）。
11. **反向纠偏：SponsorBlock 不是 B 站的方案。** SponsorBlock 官方 README 原文定义其目标是"skip sponsor segments in **YouTube** videos"，支持 Invidious，**未提及 bilibili**。→ 本项目的"空降/跳过片头片尾"应走 `view_points` / `pbp/data` / 自建众包，不要引入 SponsorBlock 依赖。
12. **`fnval=1`（非 DASH）取回的是「音视频合一」的 MP4，不是纯视频；它的清晰度天花板就是 720P。**（26.10.13 匿名实测，稿件 `av170001` / `cid=279786`）
    - **实测**：`qn=64&fnval=1&platform=pc` → `durl[0].url` 下回来是 `format=hdmp4`，容器内 **`trak=2`**（`hdlr` 里同时有 `vide` 与 `soun`，且 `mp4a`+`esds` 存在）→ **视频轨 + 音频轨两条**。
    - **对照**：`fnval=16` 的 `dash.video[].baseUrl` 是 `trak=1` 的**纯视频**；`dash.audio[].baseUrl` 是 `trak=1` 的**纯音频**。
    - **降级行为**：用 `fnval=1` 请求 `qn=80` 会被**降级返回 `quality=64`**——1080P 及以上只有 DASH 拿得到。这正是"720P 及以下走非 DASH 老接口"这个分界的来源。
    - **登录态已复核（26.10.31 补测，登录 + 大会员，`mid=591904067`）**：`avid=80433022&cid=137649199&qn=80&fnval=1&fnver=0&platform=pc` → 响应 **`"quality":64`、`"format":"mp4720"`、`accept_description:["高清 720P","流畅 360P"]`、`accept_quality:[64,16]`**，日志 `quality>合一 MP4 档位回填：80 -> 64` → **720P 确实是 `fnval=1` 的硬上限，不因登录/大会员而放开**。倍速播放不再为此换流（26.10.31 终版给 ijkplayer 打了内核补丁，DASH 纯视频流本身就能真倍速，见 `ijkpatch/README.md`），但 `PlayerApi.applyActualQuality()` 仍把响应 `quality` 回填成真实档位，避免界面档位与实际流不一致。
    - **对代码的含义**：`DownloadService` 里「`qn<=64` → `getVideo`（`fnval=1`）取单文件、`useDash=false`」以及「MP4 分支下载完要删掉同目录残留的 `audio.m4a`」都是**正确**的；**反过来把这条流当成"纯视频"再挂一路外部音轨，就会两轨同时出声**（即 §3.13 修的音频重复）。
    - **26.10.25 起下载侧把 DASH 双文件 remux 成单文件**：`util/MediaMerger.kt` 用 `MediaExtractor`（支持分片 MP4 的 `moof`）+ `MediaMuxer` 重封装，产物就是和 `fnval=1` 同形态的双轨 MP4；这不是「把 `fnval=16` 的纯视频当合一文件」，而是容器层真正写入了音频轨。播放侧的外部音轨路径保留给老缓存。
    - **未覆盖**：`platform=html5`（播放侧 mtvPlayer 分支）实测同样是 `trak=2`，与 `pc` 一致；但只测了 1 个稿件、未登录，大会员内容与其它稿件未复验。
    - **播放侧的含义（26.10.30 修「播放器内切不到 1080P」时确认，同日两轮）**：直播之外的播放链路**一律先试 DASH**（`getVideoDash` / `getBangumiDash`，播放器另起一路外部音频），DASH 失败/缺音轨才回退 `fnval=1` 的合一 MP4。低档位也走 DASH 的原因不是「低档只能 DASH 拿」，而是清晰度面板需要 DASH 的完整档位清单：`fnval=1` 会按请求档位裁剪清单，720P 请求只回 `accept_quality:[64,16]`，1080P60/4K 直接消失。**不能只靠"列表里有 1080P 就以为能拿 1080P"**：`accept_quality` 与 `fnval` 无关，`fnval=1` 时它照样列 1080P，只是 `quality` 被降到 64。判据与取流入口见 `player/PlayerStreamPolicy.kt`、`player/QualityAccessPolicy.kt` 与 `PlayerApi.getPlaybackVideo/getPlaybackBangumi`（`docs/architecture-map.md` §7.57）。
    - **26.11.01 回退后播放侧回到「单路合一 MP4」**：26.10.31 末引入的 ExoPlayer 统一时钟与 ijkplayer 内核补丁在 26.11.01 整套撤回（§六十四），`PlayerApi` 恢复只取 `fnval=1` 的 `durl[0]`——26.11.04 实测播放请求是 `fnval=1&qn=16`、响应 `format:"mp4"` + `durl[]`，清晰度天花板因此回到 720P。上面那条「一律先 DASH」是 26.10.30~31 的中间态结论，别照着它改代码。
    - **直链的 CDN 防盗链只要 Referer、不要 Cookie（26.11.04 实测）**：`playurl` 高峰会把 `durl[].url` 调度到 mcdn/PCDN 边缘节点，host 形如 `r5uw39t.edge.mountaintoys.cn:4483`（`os=mcdn&og=cos&mcdnid=…&upsig=…`），**不在 `bilibili.com` / `bilivideo.com` / `hdslb.com` 任何白名单里**，但它照样强制 `Referer: https://www.bilibili.com/`：同一 URL 只发 `Range: bytes=0-1023` → **403**（openresty 页），补上 Referer → **206** + MP4 字节（`ftypisom…avc1`）。缺 Referer 时 IJK 报 `-10000`，用户看到的就是「部分视频播放时报错」。**Cookie 不是必需**——URL 自带 `upsig` 签名，别把它当第三方源，也别为了防盗链把 Cookie 一起发出去；判据实现见 `player/PlayerRequestPolicy.kt`（§六十九）。
    - **`qn` 仍然决定「清单宽度」（26.10.30 实测）**：`qn` 在 DASH 下不决定返回哪条流，但决定服务端把哪些档位写进 `accept_quality` / `dash` 流列表。`qn=64` 请求只回 `[64,16]`，`qn=127` 才回完整档位；所以播放侧取流固定用 `qn=127`（`PlayerApi.QN_REQUEST_MAX`），真正播哪档由 `DashData.getVideoStream(playerData.qn)` 选。下载侧 `QualityChooserActivity` 早就是这个做法。
    - **4K（120）是否下发由大会员权限决定**：匿名请求 `qn=127&fnval=4048&fourk=1` 实测只回 `accept_quality:[116,80,64,32,16]`，**响应里连 120 都没有**（`fnval=4048` 已含 128/4K 位、`fourk=1` 也带了）。即「列表里没有 4K」不是参数错误，而是账号权限过滤；播放器对没权限账号补一个锁定提示位（见 §7.57），**4K 流本身只能用大会员账号验证**。
13. **搜索排序只长在「分类搜索」上，聚合搜索没有**（26.10.24 加搜索排序时核实）：`wbi/search/all/v2` 只接受 `keyword`（`bilibili-API/docs/search/search_request.md:21-27`），`order` 是 `wbi/search/type` 的参数——视频/专栏 `totalrank`（默认）/ `click` / `pubdate` / `dm` / `stow` / `scores`（专栏另有 `attention`），直播间 `online` / `live_time`，用户 `0` / `fans` / `level`（`:598`）。
    - **没有「最多点赞」**：视频搜索不支持按点赞数排序，别照用户口吻去拼一个不存在的值。
    - **要排序就得换接口**：本项目视频分类选「综合」时仍走 `all/v2`（它会把番剧结果混进第一页），选其余档位改走 `search/type?search_type=video&order=…`（不混番剧；条目字段与聚合结果同构，解析共用 `SearchApi.parseVideoEntry`）。
    - **`seid` 两条接口都会回**：`search/type` 的响应同样带 `data.seid`，`SearchApi.searchType` 已写回缓存，不会因为不再调 `all/v2` 而丢 `seid`。
    - **风控降级的形态要认出来**（26.10.24 实测）：降级时 `code` 仍是 **0**，`data` 里**既没有 `result` 也没有 `seid`**，只有一个 `v_voucher`——`{"code":0,"message":"OK","data":{"v_voucher":"voucher_…"}}`。**别把它当成"没有搜索结果"**：以前 `SearchApi` 返回 null、页面只剩空列表，看着像排序参数拼错了（用户就是这么报的）。判据只认 `v_voucher`——**真没结果的搜索照样带 `result`**（数组可能为空），不能因为 `result` 为空就报风控。遇到它时走 §3.14 的旧接口回退。
14. **搜索被风控时，旧接口（不带 wbi 签名）仍然可用——这是本仓库的一条实测逃生路**（26.10.24 验证）：
    - **现象**：短时间里连着切排序/换关键词，`wbi/search/type` 会开始回 §3.13 那种 `{"code":0,"data":{"v_voucher":"voucher_…"}}` 空壳（`code` 是 **0**，不是 `-412`）。同一条 URL 形状在冷却几十秒后自己恢复正常，所以**先别怀疑参数拼错**。
    - **逃生路**：把同一个请求打到**旧接口**（文档里标着"旧链接"的那条）——`https://api.bilibili.com/x/web-interface/search/type`（聚合搜索是 `.../search/all/v2`），**不带 `w_rid`/`wts` 签名、不带 `seid`**，实测照样 200：
      - `search_type=video&keyword=linux&order=click&page=1` → 20 条，`play` 严格降序（7972105 → 5017479 → 4438614 → …），说明**旧接口同样吃 `order`**；
      - 换成之前被风控挡下的中文长关键词（`linux操作系统零基础到入门` + `order=click`）→ 同样 20 条、同样降序。
    - **客户端做法**（`SearchApi.search` / `searchType`）：先走 wbi；落在**三种风控形态**任一上才退到旧接口再打一次——`v_voucher` 空壳、`code` 是 `-412`/`-352`、或请求层直接失败（风控回 HTML 拦截页 / 非 JSON 内容）；旧接口**也**拿不到才抛 `SearchApi.RiskControlException`（真断网时上抛原来的网络异常，不诬陷成风控），页面据此把排序按钮画成「因B站风控不可用」、点击提示「请稍候再试」并真的重试一次（见 `docs/review/fix-progress.md` §3.62）。
    - **别把它当长期方案**：旧接口是上游标了"旧链接"的地址，随时可能下线；回退只用来兜住"这一下搜不出来"，不要为了绕风控把主链路整体切过去。
    - **回退只发一次，不走任何重试**：`NetWorkUtil.getJson` 对 `-352`/`-412` 会重试到 `api_retry_max_times`（默认 5 次），`get()` 对 `<!doctype html>` 也会重试同样次数——而风控页恰恰常是 HTML。回退请求再走一遍就是「一次搜索最多 10 个请求」，只会把限流拖得更久（26.10.24 审查指出）。所以回退用新增的 `NetWorkUtil.getJsonOnce()`：它**直连一次**，连 doctype 重试都跳过；主链路的重试行为不变。
    - **旧接口的其它错误码不能当成"没有结果"**：`-400` 之类的非零 `code` 现在会抛 `SearchApi.SearchException`（带服务端错误码，`ApiErrorText.opFailed`），页面弹出来；只有 `code == 0` 且确实没有 `result` 才算"搜不到"。

15. **官方客户端的搜索主链路已经不在 REST 上了**（B 站 9.16.1 客户端静态分析，26.10.25 记录）：`grpc.biliapi.net` 上的 `bilibili.polymer.app.search.v1.Search` 提供 `SearchAll` / `SearchByType` / `SearchOGV` / `SearchComic` / `SearchReserve` / `SearchAddReserve` / `SearchDelReserve` / `SearchReserveCancel` 八个方法，`bilibili.app.interfaces.v1.Search` 另有 `Search` / `DefaultWords` / `Suggest3`；而官方 APK 的 574 个 Retrofit 接口类里**一条 `search/type` 都没有**。
    - **对本项目的含义**：我们（和所有第三方）还在用的 `wbi/search/{all/v2,type}` 是官方**已经弃用**的路径，风控压这条上并不奇怪——§3.13/§3.14 记的「短时间连切排序就被拦」正是这个背景。
    - **所以「退旧接口」只是兜底，不是长期方案**：真要摆脱风控得走 gRPC，但那要引入 moss/protobuf 运行时，与 `AGENTS.md`「不轻易引入新第三方库」冲突，暂不做。
    - **搜索建议同代际**：本项目走的是 `s.search.bilibili.com/main/suggest`（老独立域名），官方对应的是 gRPC `Search/Suggest3` 与 `Search/DefaultWords`。它目前仍然可用，没有风控表现，所以不急着换。

## 4. 按接口可行性收敛的「值得做 / 不值得做」

> 下表是 **26.10.04 的接口可行性判断**，不是最终产品裁决。用户裁决与此表有三处相反，以归档文档 `docs/archive/watch-optimization-research.md` §12 台账为准：① 「关注主播开播提醒」裁为**无计划**；② 漫画裁为**不做**（追漫列表无接口）；③ 「收藏夹批量整理」「发布动态/评论/弹幕」裁为**想要实现**（"需要输入"不构成否决理由），其中多项已落地。

**值得做（且本项目的接口基础已经具备）：**

| 方向 | 为什么 | 本项目现状 |
| --- | --- | --- |
| 稍后再看队列 | 本质是离线播放队列，无键盘 + 弱网点播 + 碎片时间三件事同时满足 | ✅ 列表/增删已有（`api/WatchLaterApi.java:23,48,59`），仅缺"未看完"分组 |
| 历史续播 | "打开就接着看/听" | ✅ 已有（`api/HistoryApi.java:37,174,257` + PGC 专用心跳） |
| 后台听视频 + AI 字幕 | 圆屏天生适合"一屏一句"；音频轨省流省电 | ✅ 听视频、字幕、AI 字幕已有；**缺的是二者组合的"车载/播客式"体验打磨** |
| 私信通知 + 速回 | 手表最强场景 | ✅ 文本收发已有；缺通知栏速回、发图、折叠消息 |
| 关注主播开播提醒 | 接口免登录、一屏一条 | ❌ 未做，**高价值低成本** |
| 收藏/投币/一键三连快捷操作 | 播放中一个手势完成 | ✅ 已有（注意 §5.1 的 csrf 问题） |
| 榜单（热门/排行/每周必看） | 有限枚举、无输入需求 | ✅ 已有 |
| 追番更新提醒 | "今天更了什么"一屏可读完 | ✅ 时间表已有，缺提醒 |

**明确不值得做**：创作中心、会员购、漫画、课堂、发布动态/评论/弹幕（需输入）、礼物面板/舰长榜（涉消费决策）、收藏夹批量整理、关注/粉丝列表管理、登录密码/短信、风纪委员与入站考试。

**一个额外结论**：从接口可行性看，**腕上场景的终点不是"把 B 站功能搬过来"，而是"把 B 站内容变成一条可离线消费的队列"**——稍后再看 + 历史续播 + 音频轨 + AI 字幕，这四样凑齐就是手表上最完整的产品形态，其余都是锦上添花。

## 5. UGC 合集（`ugc_season`）与收藏夹的归属探测（26.10.15 实测）

**问题**：在收藏夹里怎么知道"这条视频属于某个 UP 的合集"？结论是**列表接口不给，只能逐条反查**。

- `x/v3/fav/resource/list` 的单条 media 字段（实测全集）：`attr / bv_id / bvid / cnt_info / cover / ctime / duration / fav_time / id / intro / link / media_list_link / ogv / page / pubtime / season / title / type / ugc / upper`。
  - **没有 `ugc_season`**；`season` 字段存在但**恒为 null**（那是 OGV/番剧用的，不是 UGC 合集）。
  - 顺带两个**目前没解析、但有用**的字段：`ugc.first_cid`（该视频首个 cid）、`page`（分P 数）。解析 `ugc.first_cid` 可以让"收藏夹虚拟合集"切集时省掉一次 `view?aid=` 反查。
- 只能对每条调 `x/web-interface/view?aid=` 看 `data.ugc_season` 是否存在。**命中率不低**：默认收藏夹第 1 页 20 条里 **6 条**在合集里（30%）。
- `view?aid=` 的 `ugc_season` **带完整剧集**（实测 `ep_count=336`，`sections[].episodes[]` 也正好 336 条，分 9 节），且每条 episode 带 `aid`/`cid`/`title` —— 所以**一次请求就能拿到整个合集的官方顺序与真 cid**，足以直接构造播放列表。
- **但响应体很大**：上述 336 集那个 `view` 响应 **494 KB**。→ 收藏夹列表层逐条预取合集归属不可行（20 条最坏 ~10MB，手表弱网不能接受）。要探测就**按需探测**（用户主动要，或只对当前可见的几条懒加载）。
- 本项目已有的解析可直接复用：`api/VideoInfoApi.java` 的 `analyzeUgcSeason` → `model/Collection`（`sections[].episodes[]`，`Collection.Episode` 有 `aid`/`cid`/`title`）。合集详情页入口见 `docs/architecture-map.md` §7.38。

## 6. 收藏夹：写接口代际、上限字段与默认夹识别（26.10.16 实测 / 26.10.23 迁移）

**代际结论（26.10.23）**：收藏夹读写已整体迁到 `x/v3/fav/*`，老接口全部不再调用。

- 读收藏夹列表：`GET x/v3/fav/folder/created/list?up_mid=&pn=&ps=100`（自己/他人同一接口，按 `has_more` 分页）。每项 `id` 就是 media_id、`fid` 只是展示用的旧 id、`attr` bit0=1 私密 / bit1=0 默认、`cover` 直接给。
- 读收藏夹内容：`GET x/v3/fav/resource/list?media_id=&platform=web&pn=&ps=20&order=`（`order` 取 `mtime`/`view`/`pubtime`）。
- 写收藏/取消/多选删除：统一 `POST x/v3/fav/resource/batch-deal`，form `resources`（`{内容id}:{内容类型}` 逗号串）+ `add_media_ids` + `del_media_ids` + `csrf`（可同时加/删）。
- **`resources` 的类型位不只是 `2`**（26.10.29 核实 `bilibili-API/docs/fav/action.md`）：`2`=视频稿件（id 用 avid）、`12`=音频（auid）、`21`=视频合集（合集 id）。`x/v3/fav/resource/list` 每条 media 的 `type` 字段就是它，所以删除音频/合集收藏必须按真实类型拼，不能一律写 `:2`。本项目 `FavoriteApi.normalizeFavType()` 只放行 2/12/21，其余回落 2；`VideoCard.favType` 承载该值（`buildResources` 按卡片类型拼串）。
- 清空夹内失效：`POST x/v3/fav/resource/clean`，form 只有 `media_id` + `csrf`，成功 `{code:0,message:"0",ttl:1,data:0}`——**`data` 是 0 而不是清理条数**，服务端不告诉清了几条；本项目的接口快照里**没有** `folder/clean-state` 章节，所以不做「可清理数量」预检（26.10.29 已接入，见 `FavoriteApi.cleanInvalidResources`）。tab 级全量清理是 `x/v3/fav/tab/clean_invalid`，仍未接。
- 已弃用（本项目不再调用）：`space.bilibili.com/ajax/fav/getBoxList`、`x/space/fav/arc`、`medialist/gateway/coll/resource/deal`、`medialist/gateway/coll/resource/batch/del`、`x/v3/fav/resource/batch-del`、`x/v3/fav/resource/deal`。**注意**：官方文档里专门有一条「批量删除内容」`x/v3/fav/resource/batch-del`（form `resources`+`media_id`+`platform=web`+`csrf`），能力与 `batch-deal` 的删除半边重复，本项目继续走 `batch-deal`，不引入它。
- 图文收藏：`x/polymer/web-dynamic/v1/opus/feed/fav`（`page_size=20&page=`，`items[].cover` 是对象 `{url}`），旧 `x/polymer/web-dynamic/v1/opus/favlist` 仅兜底。

**上限字段的坑（26.10.16 历史，迁移后已不存在）**：老 `getBoxList` 对自建收藏夹恒返回 `max_count = 999`（实际上限 1000），「我的收藏夹」页因此显示过 `1000/999`；v3 系（`created/list`、`list-all`）**完全不返回上限**。现在上限按服务端约定本地算：默认收藏夹 50000、自建 1000（`FavoriteApi.maxCountOf()`）；默认收藏夹用 `attr & 2 == 0` 判断（不再用 `index == 0`，`index` 判据只对已删除的 `getBoxList` 成立）。详见 `docs/architecture-map.md` §7.34。

**已知但未接的收藏接口**（逐条能力表见 `docs/FEATURES.md` §8.5）：`x/v3/fav/resource/sort`（夹内手动排序）、`x/v3/fav/folder/sort`（收藏夹本身排序）、`x/v3/fav/tab/clean_invalid`（清理所有夹的失效）、`x/v3/fav/thump/up`、`x/v3/fav/tab/report`、`resource/infos|ids`、`folder/space|space/v2|whitelist`、`x/v3/fav/tab` 族、`x/v3/fav/season/unfav`。前两个需 APP 签名（`appkey` + `ts` + `sign = md5(query + appsec)`）。

**批量写接口要带 `x-bili-gaia-vtoken`**（9.16.1 客户端静态分析）：`POST x/v3/fav/resource/batch-deal` 的请求头里有一个人机校验 token（`BiliApiException.mCode == 11211` 时官方客户端会单独处理、不弹通用错误）。这个 token 由风控链路下发，第三方拿不到——所以本项目走 `batch-deal` 时**偶发失败要优先怀疑它**，退路是逐条打 `x/v3/fav/resource/deal`（官方也保留着这条）。单条 `deal` 不需要该头。

## 7. 评论区翻译：gRPC `TranslateReply`（26.10.26 实测）

**结论**：这是目前唯一能“按需翻译单条评论”的接口，游客可用、不需要签名/登录；本项目实现见 `api/ReplyTranslateApi.java`，UI 见 `adapter/ReplyAdapter.kt`（每条评论操作行的「文A」按钮）。

- **调用**：`POST https://grpc.biliapi.net/bilibili.main.community.reply.v1.Reply/TranslateReply`，`content-type: application/grpc`、`te: trailers`、`grpc-accept-encoding: identity`。
- **请求 protobuf**：`TranslateReplyReq { int64 type=1; int64 oid=2; repeated int64 rpids=3 (packed) }`。`type` 用 `ContentType` 的 typeCode（视频=1、动态=17、专栏=12、音频=14、课程=33…），`oid` 是对应的 aid/动态 id。一次 1~20 条。
- **响应 protobuf**：`TranslateReplyResp { map<int64, ReplyInfo> translated_replies=1 }`；`ReplyInfo` 里取 `translated_content=17`（`Content`）的 `message=1`。**成功判据只看 `translated_content` 是否有非空文本**。
- **目标语言**由请求头 `x-bili-locale-bin` 决定（base64 的 `Locale` protobuf）：`Locale { LocaleIds c_locale=1 { language=1; script=2; region=3 }; s_locale=2; ... }`。最小可用只写 `c_locale.language`，实测 en=`CgQKAmVu`、ja=`CgQKAmph`、zh=`CgQKAnpo`。**不发该头必然没有译文**。
- **gRPC 帧**：1 字节 `0x00` + 4 字节大端长度 + protobuf body。
- **错误形态**：HTTP 仍是 200。成功时 `grpc-status: 0`/`bili-status-code: 0` 在 trailers；`trailers-only` 业务错误时这两个头直接出现在响应头里（如 `bili-status-code=12002`「当前页面评论功能已关闭」）。OkHttp 4.12 的 `Response.trailers()` 可用，但保险做法是**先查响应头、body 读尽后再 best-effort 查 trailers**。
- **空 map 不是错误**：评论已经是目标语言、或服务端判定不可译时都会返回空 map；`reply_control.translation_switch`（`ReplyInfo` field 37）只有 0/1/2/3 四态，且翻译成功时恒为 3（SHOW_ORIGIN），没有区分度。
- **无法预判能否翻译**：REST `/x/v2/reply` 返回的 `translation_switch` 恒为 1（UNSUPPORTED 语义之外的占位），只有带 locale 头的 gRPC `MainList` 才会直接带译文。本项目列表走 REST，所以只能**点按钮时再调** `TranslateReply`。
- **本仓库的取舍**：按条懒翻译（不预取整页、不批量）、目标语言**固定为简体中文 `zh`**（`ReplyTranslateApi.TARGET_LOCALE`；App 只有中文界面，不跟随系统语言）、不重试、失败只提示一次「翻译失败，请稍后再试」。
- **实测边界**：`zh` 目标头只验证过「请求被接受、中文源评论返回空译文」（源=目标），**外语评论翻成中文尚未实测**（当时没有可用的外语评论样本）；en/ja 目标头有真实译文。
- **当前 UI 状态**：入口暂时隐藏（`ReplyTranslateApi.UI_ENABLED = false`，26.10.26），接口实现与单测保留；恢复显示只需把开关改回 `true`。
- **已知限制**：`grpc.biliapi.net` 只支持 HTTP/2；minSdk 19/20 的旧设备没有 ALPN，OkHttp 协商不出 h2 会直接抛 `IOException`（不会自动降级），UI 侧按普通网络失败提示。

## 8. 举报

**这一节的内容不在公开仓库。**

评论举报是**可选私有功能**（机制见 `docs/optional-features.md`）：接口可达性、理由表、错误码映射等全部结论都跟着实现一起放在私有仓库，公开侧只有 `util/OptionalFeatures` 里的门面与评论区面板上的一个条件挂点。公开文档里不写它的实现细节，理由见 `docs/optional-features.md` 的「红线」一节。

需要改这块时：先把私有仓库检出到仓库根目录的 `private/`（`python build_tui.py --sync-private`），再读那边的 README。

## 9. 图床 `upload_bfs` 与评论带图（26.11.02）

**接口**：`POST https://api.bilibili.com/x/dynamic/feed/draw/upload_bfs`（multipart：`file_up` 文件、`category=daily`、`biz`、`csrf`＝cookie 里的 `bili_jct`；Cookie 鉴权）。成功 `data` 给 `image_url` / `image_width` / `image_height` / `img_size`（**KB，小数**，`docs/dynamic/publish.md`）。

**`biz` 决定图片落在哪个 BFS 目录，不是"业务类型校验"**：

| 用途 | biz | 结果目录 | 依据 |
| --- | --- | --- | --- |
| 动态图 / **评论图** | `new_dyn` | `/bfs/new_dyn/` | 上游 PiliPlus `common_rich_text_pub_page.dart` 评论图与动态图共用 `biz: 'new_dyn'`；评论的 `pictures` 只认这个目录下的图 |
| 私信图 | `im` | `/bfs/im/` | 上游 PiliPlus `whisper_detail/view.dart` 传 `biz: 'im'` |

- **踩过的坑**：26.10.02（F3-c）把评论图改成自造值 `new_reply`，理由是"用 new_dyn 会被服务端当成动态图处理"——这个理由是错的（biz 只影响目录）。改完后图能上传成功，但发评论被服务端以 **12066** 拒绝。26.11.02 恢复 `new_dyn`。
- **12066 语义仍未确证**：本地 `bilibili-API/` 全库无收录，上游 collector 的 `docs/comment/action.md` 错误码表末尾也写着「其他错误码有待补充」。现在的做法是发送失败时把服务端 `message` 原样带出来（`ReplyApi.sendReplyDetailed`），下次复现能直接看到服务端说了什么。
- **不预设大会员门槛**：图片评论的资格由服务端判定。客户端原先在点「图片」时先调 `VipApi.getVipInfo()`，非大会员直接拦掉；26.11.02 已删除（用户点名"发图不需要大会员"）。

## 10. 评论发送「成功」≠ 评论区可见（26.11.02，反馈 #85 的根因）

**症状**：用户发评论 → App 提示「发送成功」、评论立刻出现在列表里 → 一刷新/重进评论区就没有了（那个视频原本只有他一个人评论，排除分页问题）。

**结论**：`POST /x/v2/reply/add` 会在评论**尚未进入公开列表**（先审后发 / 列表同步延迟）时就返回 `code=0`，`data.reply` 里甚至已经带了一条完整评论对象。所以「接口成功」只代表服务端收下了这次提交，不代表这条评论已经可被公开列表读到。

**客户端对策**（`ReplyApi.isReplyVisible`）：

1. 发送成功后取 `data.rpid`（`data.reply` 为 null 时也用它），调 `GET /x/v2/reply/wbi/main`，带 `type`/`oid`/`mode=2`（仅按时间）+ `seek_rpid=<rpid>` 直接定位新评论，在返回的 `replies`（含 `childMsgList`）里找这个 rpid。
2. 三次机会：立即、0.4s 后、1.0s 后。查到才算发布成功；三次都查不到再兜底查一次「按时间的第一页」（新评论只要公开可见，必然在 mode=2 的第一页；这一步防的是 `seek_rpid` 在个别评论区不生效）。仍查不到就提示「评论已提交，但评论区暂时查不到（可能正在审核）」，**不**把评论本地插进列表、**不**关闭写评论页。
3. 楼中楼回复不做这层核实（`seek_rpid` 对子评论不可靠），仍是「成功即插入」的老行为。

**实测边界**：本机 AVD 未登录 B 站账号，无法端到端复现审核场景；这条对策的依据是「接口先回成功、列表后可见」的已知形态（兄弟仓库 `BiliTerminal-Revival` 的 `ReplyApi.isReplyVisible` 是同一思路）。真机复现时看 logcat 的 `debug-发送评论`（服务端整包）与 `debug-评论可见性`（查询结果）即可判断是审核还是别的问题。

## 11. 关注分组：把一个已关注的人加进分组（26.11.02，反馈 #88）

| 用途 | 接口 | 参数 |
| --- | --- | --- |
| 列出我的分组 | `GET /x/relation/tags` | — |
| 某人在哪些分组 | `GET /x/relation/tag/user?fid=<mid>` | 返回 `data` 是「分组 id → 分组名」对象；**在默认分组时该对象为空**；特别关注是 `tagid=-10` |
| **追加**到分组 | `POST /x/relation/tags/addUsers` | `tagids`（逗号分隔，**复数**）、`fids`（逗号分隔）、`csrf` |
| 从 A 移到 B | `POST /x/relation/tags/moveUsers` | `beforeTagids` / `afterTagids` / `fids` / `csrf` |

- **`addUsers` 是追加语义，`moveUsers` 是移动语义**：前者不会把他从原分组移走、也不取关；后者会。两组字段名完全不同（`tagids` vs `beforeTagids`/`afterTagids`），写错只会在真机上收到 `-400`，看不出是拼写问题——所以两个字段组装都抽成纯函数并钉了单测（`FollowApiAddUsersTest` 里有一条「加入与移动是两组不同的字段名」的对照断言）。
- **默认分组与特别关注不进「加入分组」菜单**：`tagid=0` 是默认分组（加入它等于没加），`-10` 是特别关注（有独立接口/入口）。弹窗只列 `tagid > 0` 的自建分组。
- **新建分组后拿不到 tagid**：`POST /x/relation/tag/create` 的响应按文档 `data.tagid` 会回新 id，但当前实现只取 `code`；加入前按名字回查一次 `GET /x/relation/tags` 拿 tagid（服务端保证分组名唯一，重名会回 22106），比猜接口稳。

## 12. `view_points` 的 `type` 是「分段类型」，不是「片头/片尾类型」（26.11.05，反馈 #99 / #34）

`GET /x/player/wbi/v2?aid=&cid=` 的 `data.view_points[]` 每项形如 `{type, from, to, content, img_url, logo_url}`。**同一个字段在两类内容上的语义不同**：

| 内容类型 | `view_points` 的含义 | `type` 的实际取值 |
| --- | --- | --- |
| **番剧 / 影视（PGC）** | 片头、片尾、正片等**运营标注** | `1` / `2` 等，带片头片尾语义 |
| **UGC 投稿** | up 主或平台自动生成的**视频分段（章节）** | 实测**恒为 `2`**，`content` 才是章节名 |

- **实测样本**：`BV1U74w6XEbB`（aid=117546476966330 / cid=43264904750，单P，129s）解开后是 10 段普通章节——「狂躁损伤」「泄漏源」「留声机」「舞者」「火炉」「兔子」「浮士德」「毒雾」「源石地板」「隐匿术士，黑蛇，反伤怪，飞机」，**10 段的 `type` 全部是 `2`**。
- **踩坑形态**：把 `type == 2` 当成「片尾」后，UGC 稿件的每个章节都成了片尾 → 250ms 进度定时器每轮都命中 → 用户看到的是「一直在跳过片尾」。这就是反馈 #99 与 #34 的同根问题。
- **对策**：自动跳过**只对 PGC 生效**（`PlayerActivity.isPgcPlayback()`，判据是 `is_bangumi` / `epid`），UGC 拿到空片段表；「显示视频分段」的浏览/点选不受影响。
- **不要用 `content` 白名单代替这个判据**：番剧片段的 `content` 常为空串或「1-1」「正片」「简介」，按标题过滤会把真片头也挡掉，且挡不住「预告」类标注。

## 13. Web Cookie 刷新：`refresh_token` 一次性轮换 + confirm 作废旧 Cookie（26.11.06）

接口与流程文档在仓库快照 `bilibili-API/docs/login/cookie_refresh.md`（原始出处 socialsisteryi/bilibili-API-collect）。三条与实现直接相关的结论：

1. **`cookie/refresh` 的响应会通过 `Set-Cookie` 下发新的 `SESSDATA` / `bili_jct` / `DedeUserID` / `DedeUserID__ckMd5` / `sid`**（`Domain=bilibili.com`，有效期约半年），同时在 JSON 的 `data.refresh_token` 里返回**新的** `refresh_token`。响应样例见上述文档快照。
2. **`refresh_token` 是一次性轮换的**：刷新成功后旧的 `refresh_token` 就不再是「下次可用的口令」。所以「等 `confirm` 成功后再落盘新口令」是错的——中途任何异常（网络断、解析失败、进程被杀）都会让本地永久续不上会话，最终表现为随机掉登录。正确顺序：拿到新口令立刻持久化。
3. **`confirm/refresh` 的语义是「让旧 `refresh_token` 对应的 Cookie 失效」**，它必须带**旧的** `refresh_token` 与**新** Cookie 里的 `bili_jct`。因此 confirm 之前必须先核对新 Cookie 真的落到本地了（至少 `SESSDATA` 非空、`DedeUserID` 与当前 mid 一致），否则「旧会话被作废 + 本地还是旧的」= 必掉登录。

实现侧对应 `api/CookieRefreshApi.refreshCookie()` 返回的 `RefreshResult{OK, RETRY, NEED_LOGIN}`：只有刷新接口返回 `-101` 才算 `NEED_LOGIN`；`86095`（refresh_csrf 错 / 口令与 Cookie 不匹配）之类只算 `RETRY`，**不得**清本地凭证。

## 14. 番剧索引 / 推荐 / 动态筛选条件（26.11.07 实测）

番剧分区页（`activity/video/BangumiActivity`）的三个数据源，两个接口都**无 WBI、Cookie 可选**（未登录能拿到完整结果）：

| 用途 | 接口 | 关键参数 |
| --- | --- | --- |
| 索引 / 推荐 | `GET /pgc/season/index/result` | `season_type=1`（番剧）、`type=1`、`order`（排序 field）、`sort=0`、`page`/`pagesize`、9 个筛选字段 |
| 筛选条件 | `GET /pgc/season/index/condition` | `season_type=1`、`type=0` |

实测结论（26.11.07 本机直连，`code=0`）：

1. **响应字段与仓库里的旧快照对不上**：`data.list[]` 的 `score`（评分，字符串，未开分是 `"0.0"`）与 `order`（**当前排序指标的展示文案**，字符串）才是现役字段；旧文档里的 `rating` / `styles` / `order` 对象已不存在。`has_next` / `total` / `num` / `size` 照旧。
2. **`order` 的含义随排序指标变**：`order=3`（最多追番）时是 `"762.3万追番"`，`order=4`（最高评分）时是 `"9.9分"`。所以卡片副标题不能无条件显示 `score`——没开分（`0.0`）时要退回 `order` 文案。
3. **筛选条件必须从 `condition` 接口拿，不要硬编码**：它下发 `order[]`（5 项排序：综合 `8` / 最多追番 `3` / 最多播放 `2` / 最近更新 `0` / 最高评分 `4`）与 `filter[]` 9 组（地区 / 风格 / 类型 / 付费 / 配音 / 版权 / 状态 / 年份 / 季度）。每组的 `values[].keyword` 才是 query 值，第一项固定是 `-1`（全部）。
4. **`year` 的 keyword 是区间串**（`[2026,2027)`、`[1990,2000)`），必须原样透传；`area` 的「国产」是 `1,6,7` 这种逗号列表，也别自己拆。
5. **到底判据**：`has_next` 之外再看 `page * pagesize < total`——深翻过 `total/pagesize` 时 `has_next` 可能仍是 1。请求失败（`-412` 风控、HTML 错误页）必须抛异常，**不能**返回空页：空页会被上层当成「到底了」，页码永久停在半截。
6. **`media_id` 缺省时退回 `season_id`**：列表项两者通常相等，但个别条目只给其一；拿 `0` 去开详情页会得到空页。
