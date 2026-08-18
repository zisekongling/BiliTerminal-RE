# DASH 视频下载方法研究报告

> 调研日期：2026-08（基于 ReBiliClient 仓库现状与公开资料整理）
> 范围：DASH 协议原理、通用下载方法、B 站 DASH 流专有下载流程、本项目（ReBiliClient）现有实现分析、常见坑

---

## 1. DASH 协议概述

### 1.1 什么是 DASH

**DASH（Dynamic Adaptive Streaming over HTTP，HTTP 动态自适应流）** 是 MPEG 制定的流媒体传输标准（ISO/IEC 23009-1），与 HLS（Apple）、MSS（Microsoft）并列为主流自适应流协议。核心特征：

- **音视频分离**：视频轨和音频轨作为**独立的流**分别传输，各自可有多档码率/编码。
- **分片传输**：媒体被切成若干 2~10 秒的**分片（Segment）**，播放器按需顺序/并发拉取。
- **自适应**：播放器根据带宽动态切换不同码率分片（本项目用不到，下载是取固定清晰度）。

DASH 通常有两种封装形态：

| 形态 | 说明 | 典型后缀 |
| ---- | ---- | ---- |
| **SegmentBase（单文件整段）** | 整段媒体就是一个文件，内部按分片布局（`sidx` 索引 + 多个 `moof`/`mdat`），可用 Range 请求跳读 | `.m4s`（B 站用的就是这种） |
| **SegmentList / SegmentTemplate** | 一个初始化段（init） + 大量独立小分片文件 | `.m4s`、`.m4v`、`.m4a`、`.mp4` |

### 1.2 与 FLV / MP4 的区别

| 对比项 | FLV（已下线） | MP4（durl） | DASH |
| ------ | ------------ | ----------- | ---- |
| 音视频 | 合并单文件 | 合并单文件 | **分离双流** |
| 分段 | 老视频存在分段 | 不分段 | 单文件内部按 `sidx`/`moof` 分片 |
| 编码 | 仅 H.264 | 仅 H.264 | H.264 / H.265 / AV1 |
| 高清支持 | 上限低 | 新视频无高分辨率 | 4K/HDR/8K/杜比 全在这 |
| 下载后 | 直接播放 | 直接播放 | **需合并音视频** |

> 现状：B 站新视频的较高分辨率（1080P+）**只提供 DASH 流**，MP4 只有低清晰度或老视频，所以想下载高清视频绕不开 DASH。

### 1.3 DASH 单文件（m4s）的内部结构

B 站返回的 `.m4s` 是 **fMP4（fragmented MP4）** 变体，典型布局（以接口返回的 `SegmentBase` 字段为索引）：

```
| ftyp | moov | sidx | moof+mdat | moof+mdat | moof+mdat | ... |
└─ initialization（0-821 字节）─┘└─ index ┘└────── 媒体分片（关键帧+普通帧）──────┘
```

- `initialization`（如 `0-821`）：`ftyp` + `moov` box，包含轨道信息、编码参数、分片元数据。
- `index_range`（如 `822-1309`）：`sidx`（Segment Index）box，记录**各关键帧的时间戳与文件内偏移**，播放器靠它拖进度条。
- 其余为 `moof`（movie fragment，含每帧元数据）+ `mdat`（媒体数据）交替。

> 关键差异：常规 MP4 的帧索引全部集中在 `moov`；fMP4 只把关键帧索引放进 `sidx`，其余帧信息分散在各 `moof` 中。下载后如果直接当普通 MP4 打开，很多播放器会无法 seek，需要 remux 或用支持 fMP4 的播放器（B 站 App、ExoPlayer、ffmpeg 处理后的文件均可）。

---

## 2. DASH 下载的通用方法

### 2.1 本质流程（三步）

无论用哪个工具，DASH 下载的本质都是：

1. **取流清单**：拿到 MPD（Media Presentation Description，XML）或等效的流描述 JSON，得到初始化段 URL + 分片 URL 列表 + 音视频轨道元数据。
2. **拉流**：下载初始化段和全部分片（或直接整段下载单文件 m4s）。
3. **合并**：把视频轨与音频轨 mux 成一个 MP4/MKV（推荐流拷贝 `-c copy` 免转码，秒级完成、无损）。

### 2.2 常用工具对比

| 工具 | 语言 | 特点 | 适用场景 |
| ---- | ---- | ---- | ---- |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Python | 最通用的下载器，内置 B 站支持（含 DASH 双流自动合并）；分片并发下载、断点续传、`-f` 选清晰度 | 几乎所有站点，命令行首选 |
| [N_m3u8DL-RE](https://github.com/nilaoda/N_m3u8DL-RE) | C# | 专注流媒体：HLS/DASH/MSS 多协议，m4s 分片合并、KEY 解密、并发强 | 手动把 MPD/m3u8 喂给它的场景 |
| [BBDown](https://github.com/nilaoda/BBDown) | C# | B 站专用，处理登录/大会员/番剧/多 P 最省心 | 只想下 B 站 |
| [lux](https://github.com/iawia002/lux)（原 annie） | Go | 通用、单二进制、依赖 ffmpeg 合并 | 快速命令行下载 |
| [ffmpeg](https://ffmpeg.org/) | C | 本身可作 HTTP 下载器（`-i url` + `-c copy`），也是所有工具的合并后端 | 已有流 URL 时的最终合并 |
| MediaSniff 等浏览器扩展 | JS | 抓取页面实际播放的 HLS/DASH/MP4 流 URL，一键生成 yt-dlp/ffmpeg 命令 | 面对「页面只给你 blob」的场景 |

### 2.3 手动抓流（不依赖现成工具）

以浏览器为例：

1. **抓清单**：打开视频页，F12 → Network，过滤 `mpd`/`playurl`，找到流清单请求；或用抓包工具拿到 MPD 全文。
2. **解析清单**：MPD 里有 `<AdaptationSet>`（视频轨/音频轨各一组）、`<BaseURL>`、`<SegmentList>`/`<SegmentTemplate>` 或 `SegmentBase` 的 `indexRange`。
3. **下载**：按清单拼接分片 URL 逐个下载；B 站这类 SegmentBase 单文件则直接下载整个 m4s。
4. **合并**：

```bash
# 通用 DASH 合并（视频轨 + 音频轨 → 单文件，流拷贝）
ffmpeg -i video.m4s -i audio.m4s -c copy -f mp4 output.mp4
```

### 2.4 yt-dlp 的 DASH 下载引擎要点（作为工程参考）

- yt-dlp 从清单提取每个轨道的 **fragment URL 列表**，用多线程**并发下载分片**，再拼接/合并。
- 下载完成后通过内置的 FFmpeg 后处理器做 **remux（`-c copy`）或转码**。
- 断点续传：`.part` 文件 + 已完成分片跳过；失败分片重试。
- 接口参考：[yt-dlp/yt-dlp](https://github.com/yt-dlp/yt-dlp) 的 `YoutubeDL.py`、`downloaders/http.py`；剖析文章见文末参考链接。

---

## 3. B 站 DASH 视频专有下载方法

### 3.1 取流接口：`playurl`

**GET** `https://api.bilibili.com/x/player/wbi/playurl`（需登录 Cookie；番剧走 `pgc/player/web/playurl`）

核心参数：

| 参数 | 说明 | 备注 |
| ---- | ---- | ---- |
| `avid` / `bvid` | 稿件 id（二选一） | |
| `cid` | 分 P 号 | 多 P 视频每个 P 一个 cid |
| `qn` | 期望清晰度（16/32/64/80/125/126/127） | DASH 下仅表示"优先"，实际返回所有档 |
| `fnval` | **格式位运算**，关键参数 | 见下表 |
| `fnver` | 固定 `0` | |
| `fourk` | `1` 允许 4K | |
| `platform` | `pc`（web）/ `html5`（免防盗链） | |
| `w_rid` / `wts` | **WBI 签名** | 2022 年后必须，否则风控/报错 |

`fnval` 位含义（按位或叠加）：

| 值 | 含义 | 备注 |
| --- | ---- | ---- |
| 16 | DASH 格式 | 与其他格式互斥 |
| 64 | HDR 视频 | 仅 H.265，需 `qn=125` |
| 256 | 杜比音频 | 大会员 |
| 512 | 杜比视界 | 大会员 |
| 1024 | 8K | 大会员 |
| 2048 | AV1 编码 | |
| 4048 | **全部 DASH 选项** | `16|64|256|512|1024|2048`，一次拿全 |

### 3.2 响应结构（`data.dash`）

```jsonc
{
  "data": {
    "dash": {
      "duration": 706,
      "minBufferTime": 1.5,
      "video": [ /* 视频流数组，含 H.264/H.265/AV1 各清晰度 */ ],
      "audio": [ /* 音频流数组 */ ],
      "dolby": { "type": 1, "audio": [...] },   // 杜比，大会员
      "flac":  { "display": true, "audio": {...} }  // 无损，大会员
    }
  }
}
```

每个流的字段（`video`/`audio` 数组项）：

| 字段 | 含义 | 备注 |
| ---- | ---- | ---- |
| `id` | 清晰度/音质代码 | 视频：16/32/64/80/125/126/127；音频：30216 等 |
| `baseUrl` / `base_url` | 主流 URL | **注意 `\u0026` 是 `&` 的 Unicode 转义**；有效期 120 分钟 |
| `backupUrl` / `backup_url` | 备用流 URL | 主流失败时换用 |
| `bandwidth` | 码率（Byte/s） | 选音频流时按它排序 |
| `mimeType` | `video/mp4` / `audio/mp4` | |
| `codecs` | 编码串，如 `avc1.640032`（H.264）、`hev1.*`（H.265）、`av01.*`（AV1）、`mp4a.40.2`（AAC） | |
| `width`/`height`/`frameRate` | 分辨率/帧率 | 仅视频流 |
| `SegmentBase` | `initialization`（ftyp+moov 字节范围）、`index_range`（sidx 字节范围） | 仅视频流 |

### 3.3 标准下载流程（Web 端）

```bash
# 1) 取流（需登录 Cookie，wbi 签名后请求）
curl -G 'https://api.bilibili.com/x/player/wbi/playurl' \
    --data-urlencode 'bvid=BV1rp4y1e745' \
    --data-urlencode 'cid=244954665' \
    --data-urlencode 'fnval=4048' \
    --data-urlencode 'fnver=0' \
    --data-urlencode 'fourk=1' \
    -b 'SESSDATA=xxx'

# 2) 从返回的 dash.video / dash.audio 里挑目标流，分别下载
#    视频流（m4s）
wget '<video baseUrl>' \
    -H "User-Agent: Mozilla/5.0 ..." \
    --referer 'https://www.bilibili.com' \
    -O video.m4s
#    音频流（m4s）
wget '<audio baseUrl>' \
    -H "User-Agent: Mozilla/5.0 ..." \
    --referer 'https://www.bilibili.com' \
    -O audio.m4s

# 3) 合并（流拷贝，不重编码）
ffmpeg -i video.m4s -i audio.m4s -c:v copy -c:a copy -f mp4 output.mp4
```

### 3.4 防盗链与鉴权要点

1. **防盗链**：流 URL 校验 `Referer`（必须为 `.bilibili.com` 域）且 **UA 不能为空**；否则返回 `403 Forbidden`。传 `platform=html5` 则跳过防盗链验证。
2. **登录**：高清晰度（1080P 高码率/4K/HDR/杜比/8K/AV1）需要登录 Cookie（`SESSDATA`），部分需要**大会员**。
3. **WBI 签名**：`playurl` 接口 2022 年起要求 `w_rid`（对查询参数 + img_key/sub_key 做 HMAC-SHA256 摘要），否则返回 `-403`。签名所需 key 从 `https://api.bilibili.com/x/web-interface/nav` 的 `wbi_img` 获取。
4. **URL 有效期**：约 **120 分钟**，过期需重新取流。
5. **`\u0026` 转义**：JSON 里 URL 的 `&` 被转义成 `\u0026`，解析时须替换为真实 `&`。
6. 无音轨视频：`dash.audio` 为 `null`，此时只下视频流即可。

---

## 4. 本项目（ReBiliClient）的 DASH 下载实现

### 4.1 分层结构

```
取流层  PlayerApi.java          —— 请求 playurl，解析出 videoUrl / audioUrl
        DashData.java           —— DASH JSON 解析 + 选流策略
下载层  DownloadService.kt      —— 调度：封面/弹幕/字幕 → 视频流 → 音频流 → 合并
合并层  MediaMerger.kt          —— Android MediaExtractor + MediaMuxer 本地 mux
播放层  PlayerActivity.kt       —— 合并失败的 fallback：外部音频轨道
        LocalPageChooseActivity —— 本地 DASH 双文件（video.mp4 + audio.m4a）映射
清晰度  QualityChooserActivity  —— 强制高清晰度下载（自定义 fnval）
```

### 4.2 取流层（`api/PlayerApi.java`）

**`getVideoDash(PlayerData)`**（约 L106）—— 标准 DASH 取流：

- 请求 `x/player/wbi/playurl`，参数：`avid` + `cid` + `qn` + **`fnval=4048&fnver=0`**（一次拿全 DASH 选项）+ `platform=pc` + `fourk=1`。
- 先 `ConfInfoApi.signWBI(url)` 补 WBI 签名，再用 `NetWorkUtil.webHeaders`（带 Cookie/Origin/Referer/UA，见 `NetWorkUtil.java` L470-492，**Referer 固定 `https://www.bilibili.com/`**）请求 —— 已正确处理防盗链。
- 解析 `data.dash` → `DashData.fromJson()`，随后：
  - `getVideoStream(qn)` 选**指定清晰度**的视频流；
  - `getBestAudioStream()` 选**最高音质**音频流（优先级：无损 FLAC > 杜比 > 普通流中 bandwidth 最大者）。
- 无 `dash` 字段时回退到 `getVideo(playerData, true)`（MP4 单文件）。

**`tryGetVideoWithFnval(PlayerData, fnval)`**（约 L175）—— 强制清晰度下载用的变体：按传入 fnval 取流，失败回退 `durl`（MP4）。

**`getVideo(PlayerData, download)`**（约 L236）—— 旧 MP4 路径：`fnval=1`，解析 `data.durl[0].url`，一次缓存 10 分钟（`timeStamp`）。

### 4.3 选流策略（`model/DashData.java`）

- `getVideoStream(qn)`：优先精确匹配 `id == qn`，否则返回列表第一个（最高清晰度）。
- `getBestAudioStream()`：**flac > dolby > max(bandwidth)**。
- `fromJson` 兼容 `baseUrl`/`base_url`、`backupUrl`/`backup_url`、`SegmentBase`/`segment_base` 两套命名。
- 注意：本项目取流用的是**网络返回的 `baseUrl` 整段单文件**，没有利用 `SegmentBase` 做 Range 分片下载。

### 4.4 下载调度（`service/DownloadService.kt`）

`processDownloadSection()`（约 L741）的关键决策：

- **纯音频**（`isAudioOnly`）：只下 `audio.m4a`，不合并。
- **qn ≤ 64（720P 及以下）**：走旧 MP4 单文件路径（`getVideo` + `downFile` 直接存 `video.mp4`）。
- **qn > 64（1080P 及以上）**：走 DASH 路径——
  1. `PlayerApi.getVideoDash(data)` 拿到视频/音频 URL；
  2. `downFile(url_video, video.mp4, ...)`，进度区间 **0.2 → 0.6**；
  3. `downFile(url_audio, audio.m4a, ...)`，进度区间 **0.6 → 0.85**；
  4. `MediaMerger.mergeAv(videoFile, audioFile)`，进度 0.85，**合并失败仅记日志、保留分离双文件**（不中断下载）。
- `video_single`（单 P）与 `video_multi`（多 P）两套流程结构相同。

### 4.5 合并层（`util/MediaMerger.kt`）

纯 Android 框架实现（无第三方库）：

- 双 `MediaExtractor` 分别读视频/音频文件 → 找 `video/*` 与 `audio/*` 轨道。
- `MediaMuxer`（`MUXER_OUTPUT_MPEG_4`）建双轨，循环 `readSampleData` → `writeSampleData`，**透传时间戳（presentationTimeUs）与 flags**。
- 先写临时文件 `video_merged_temp.mp4`，成功后**同目录 rename 覆盖** `video.mp4` 并删除 `audio.m4a`（避免整文件复制）；异常时清理临时文件。
- 缓冲 256KB；未处理音视频时间戳起点对齐问题（两者通常都从 0 开始，可直接写入）。

### 4.6 播放侧 fallback（合并失败的兜底）

- `PlayerActivity`（约 L1878/L2931）：DASH 分离文件**合并失败**时，以 `video.mp4` 为主视频、`audio.m4a` 作为**外部音频轨道**（ExoPlayer side-load）播放。
- `LocalPageChooseActivity` / `LocalListActivity` / `CacheListAdapter`：本地文件场景同样识别"DASH 双文件"（`video.mp4` + `audio.m4a`），构建外部音频轨映射。

### 4.7 强制高清晰度（`activity/video/QualityChooserActivity.kt`）

- 内置 `forcedQualityFnval` 映射（如 `fnval=4048` 全开）。
- 下载高清晰度失败时按目标 qn 的 fnval 调用 `PlayerApi.tryGetVideoWithFnval` 重试，成功后以 DASH 方式下载（约 L130-140）。

### 4.8 小结（对本项目的评价）

- **优点**：链路完整——取流（WBI+防盗链）、选流（flac>dolby>码率）、双流下载、MediaMuxer 合并、播放 fallback 都齐了；纯 Android 框架实现，无新依赖，符合项目"不轻易引第三方库"的约定。
- **可优化点**：
  1. 大文件直接整段下载（无 Range 分片/并发），长视频断点续传与速度一般；可参考 yt-dlp 的分片并发。
  2. `MediaMuxer` 合并是"读一个 sample 写一个 sample"的单线程，且只处理视频+音频两轨；字幕轨、多音轨（如多语言）不支持。
  3. 音频优先选 FLAC/杜比，但这需要大会员，普通账号会取流失败——当前实现没有对取流失败的音频降级（视频成功、音频失败时整体报错，实际下载流程里音频失败即 `return false`）。
  4. `getVideoStream(qn)` 匹配不到时返回列表第一个（最高清晰度），与用户选择的 qn 可能不一致。

---

## 5. 常见问题与避坑清单

| 现象 | 原因 | 对策 |
| ---- | ---- | ---- |
| 下载返回 **403** | 防盗链：Referer 不对 / UA 为空 | 带 `Referer: https://www.bilibili.com` + 非空 UA；或 `platform=html5` 取流 |
| 接口返回 `-403` | 缺少 WBI 签名 | 对请求参数做 WBI 签名（`w_rid`/`wts`），key 从 nav 接口拿 |
| 高清流取不到 | 未登录 / 非大会员 | 带 `SESSDATA` Cookie；杜比/无损/8K/HDR 需大会员 |
| URL 解析报错 | JSON 里 `\u0026` 未还原为 `&` | 替换 `\\u0026` → `&` |
| 下载到一半失败 | 流 URL 过期（120 分钟） | 重新取流；或做断点续传 |
| 合并后无法拖动进度条 | fMP4 的 moov/sidx 结构未被归一 | ffmpeg `-c copy` remux，或 `-movflags faststart` 生成可流式播放文件 |
| 合并后音画不同步 | 双流时间戳起点不一致 | ffmpeg 用 `-itsoffset` 校正；或检查采样率/帧率元数据 |
| 杜比/无损音频文件播放无声 | 播放器/设备不支持 eac3/flac 容器 | 换解码器；或仅取普通 AAC 音频流 |
| 无音轨视频（`audio: null`） | 视频本身没有声音 | 只下载视频流，跳过合并 |

---

## 6. 参考链接

**协议与文档**

- [bilibili-API-collect：视频流接口文档（videostream_url）](https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/video/videostream_url.md)（本仓库 `bilibili-API/docs/video/videostream_url.md` 快照）
- [ISO/IEC 14496-12（MP4 文件格式标准）](https://www.iso.org/standard/83102.html)
- [MP4 文件结构（百度百科）](https://baike.baidu.com/item/mp4/9218018)

**通用工具**

- [yt-dlp / yt-dlp](https://github.com/yt-dlp/yt-dlp)
- [N_m3u8DL-RE](https://github.com/nilaoda/N_m3u8DL-RE)（DeepWiki 解析：[nilaoda/N_m3u8DL-RE](https://deepwiki.com/nilaoda/N_m3u8DL-RE)）
- [BBDown](https://github.com/nilaoda/BBDown)
- [lux（原 annie）](https://github.com/iawia002/lux)
- [MediaSniff（浏览器抓流扩展）](https://github.com/chethan62/mediasniff)
- [ffmpeg](https://ffmpeg.org/)

**原理剖析文章**

- [yt-dlp 下载引擎剖析：HLS/DASH 分片下载与并发控制](https://blog.csdn.net/csdn122345/article/details/161173150)
- [Engineering a High-Performance Bilibili Video Downloader: DASH, WBI Signatures, and Binary Muxing](https://dev.to/yqqwe/engineering-a-high-performance-bilibili-video-downloader-a-deep-dive-into-dash-wbi-signatures-3po1)
- [全链路解析：基于云原生架构的 Bilibili 视频下载引擎实现](https://developer.aliyun.com/article/1703355)
- [B站流媒体解析与音视频合并技术学习笔记](https://blog.csdn.net/2603_95532426/article/details/159320356)
- [把 HLS、DASH、MSE 这条前端链路讲透（为什么 B 站只给你一个 blob）](https://juejin.cn/post/7629524163645145151)

---

*本报告基于仓库代码（`PlayerApi.java`、`DashData.java`、`DownloadService.kt`、`MediaMerger.kt`、`QualityChooserActivity.kt`、`NetWorkUtil.java`）与 `bilibili-API/` 接口快照整理，可作为后续改进 DASH 下载（分片并发、断点续传、音频降级等）的设计依据。*
