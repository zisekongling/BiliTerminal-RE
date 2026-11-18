# DASH 下载改为合并单文件（设计）

- 日期：2026-10-25
- 状态：已实现（设备侧容器重封装已验证，端到端下载待登录实测）

## 背景

B 站 1080P 及以上只提供 DASH：`video.m4s`（纯视频）与 `audio.m4s`（纯音频）两条流，两者都是
**分片 MP4**（moov 里采样表为空，真正的采样在 moof/mdat 里）。720P 及以下是音视频合一的普通
MP4，播放器直接能放；DASH 双文件则要播放器额外挂一条外部音轨（`PlayerActivity.audioTrackUrl` +
`LocalAudioTrackPolicy`），下载目录里也会多一个 `audio.m4a`。

用户要求：1080P 以上也像 720P 一样，缓存下来就是**一个**视频文件。

## 历史：本次是决策反转

- 早期版本确实做过合并（`util/MediaMerger.kt`，MediaMuxer 交替写两条轨），并在
  `strings.xml` 历史更新日志里发布过。
- 2026-08-18 的 `25893f3`（DASH 下载优化）把它移除，改成「下载完保留双文件直供播放器」，
  同时引入临时文件替换、成对替换、暂停恢复等机制；
- 2026-09-11 的 `183523f` 删掉了 `MediaMerger.kt` 本体；
- `docs/archive/superpowers/specs/2026-10-13-download-engine-optimization.md:144` 明确写「本项目已明确删除
  MediaMerger、不做合并……不应照搬」——**本设计把该结论反转**，理由：当时的实现有
  「合并阶段无进度、卡在 85%」「旧实现先删视频再改名有丢数据风险」两个硬伤，这两点在
  `25893f3` 之后已经有现成解法（进度分段常量、`safeReplaceTemp` 备份替换），可以安全地重做。

## 目标

1. 1080P+（走 DASH 的）下载，最终产物是 `video.mp4` 单文件（音画齐全），不再保留 `audio.m4a`。
2. 合并只做容器重封装（remux），**不重新编码**：画质无损、几秒内完成。
3. 合并失败不影响下载结果：保留双文件继续可播，不报下载失败。
4. 合并阶段在进度条上也**单调前进**，不出现回退或长时间卡死。
5. 老缓存（已是双文件）不受影响，继续按外部音轨播放。

## 非目标

- 不处理已存在的双文件缓存（不做批量合并入口）——用户要的是以后下载的就是单文件。
- 不改变清晰度选择、编码优先级（仍然是 AVC + AAC，`model/DashData.java`）。

## 设计

### 1. 合并实现：`util/MediaMerger.kt`

```text
MediaExtractor(video.m4s) ─┐
                            ├─ 按 sampleTime 归并 ─→ MediaMuxer(MPEG_4) ─→ video_merging.tmp
MediaExtractor(audio.m4s) ─┘
```

- 两个 `MediaExtractor` 各取第一条 `video/*` / `audio/*` 轨道（`selectTrack`）；
- 每轮比较两条轨当前样本的时间戳，先写早的；某轨读完就只写另一条（音频通常比视频长）；
- 关键帧标志用 `MediaExtractor.SAMPLE_FLAG_SYNC`（值 1，与 `MediaCodec.BUFFER_FLAG_KEY_FRAME`
  相同，避开 API 21 才有的常量）；
- 输出是标准（非分片）MP4，moov 在文件尾；`setOrientationHint` 透传旋转角；
- 返回码：`RESULT_OK / RESULT_NO_TRACK / RESULT_NO_SPACE / RESULT_STOPPED / RESULT_FAILED`，
  非 `RESULT_OK` 时产物已被删除，调用方保留源文件。

空间检查：源文件还在盘上，合并期间要再写一份同样大小的产物，所以要求
`剩余空间 ≥ 视频 + 音频 + 4MB`；读不到剩余空间时放行（真没空间时 MediaMuxer 抛 IO 异常走失败兜底）。

### 2. 合并时机与进度

`DownloadService.processDownloadSection` 的 DASH 分支（单 P / 多 P 各一处）：

```text
附件 0.00–0.12 → 视频 0.12–0.52 → 音频 0.52–0.88 → 合并 0.88–1.00
```

合并阶段放在 `replaceDashPairOrFail` **之后**：此时双文件已经是本代的正式成品，合并只是原地
remux；失败也不影响下载成功语义。进度边界集中在 `DownloadProgressMath`
（`DASH_ATTACH_END / DASH_VIDEO_END / DASH_AUDIO_END / DASH_MERGE_END` + `dashMergeProgress()`），
有单测钉住。

### 3. 兜底与暂停

- 合并中**不响应暂停**（只做容器重封装，通常几秒内完成；中途停手反而要处理半成品），
  但 `started == false`（服务销毁）时中止并返回 `RESULT_STOPPED`，按「下载未完成」处理；
- 下载完成后、合并开始前若用户按了暂停：不合并，直接走 `runDownloadSection` 的暂停分支；
- 合并成功：`safeReplaceTemp(merged, video.mp4)` 替换后删 `audio.m4a`；
- 合并失败：删除半成品，**保留 `video.mp4` + `audio.m4a`**（播放器外部音轨路径仍可用），
  只记日志，下载照常算成功。

### 4. 残留清理

- 合并临时文件 `video_merging.tmp`（`DownloadPathSpec.FILE_VIDEO_MERGING`）刻意不用 `.mp4`
  后缀，也不会被本地列表的历史合并残留兼容逻辑（`video_merged_temp.mp4`）误认；
- `FileUtil.cleanDownloadTempFiles` 增加删除该文件；若进程恰好在 `safeReplaceTemp` 中间被杀，
  `.bak` 恢复逻辑照旧把 `video.mp4` 救回来，双文件仍可播。

### 5. 兼容性

- `MPEG4Extractor.cpp` 从 Android 4.4（API 19，即本机 `minSdk`）起就含 `moof` / `firstMoofOffset`
  解析，分片 MP4 可直接读；
- `MediaMuxer` 写 AVC + AAC 从 API 18 起支持；HEVC 封装要 API 24+，而选流优先 AVC，正常路径不碰；
- `MediaMuxer` 写不了杜比（ec-3/eac3）与 FLAC——`DashData.getBestAudioStream()` 本来就优先
  `mp4a`，注释里也写明是为合并/播放器兼容。

### 6. 调试接口

`DebugBridge` 新增命令 `merge --es video <m4s> --es audio <m4s> [--es out <mp4>]`：直接跑一次
`MediaMerger`，回显返回码、耗时、产物轨道与采样数。完整下载流程要登录 + 选 1080P 才走得到合并
分支，容器层则可以用两条现成 m4s 单独验，这条命令就是干这个的。

## 验证

- 纯 JVM 单测：DASH 阶段边界与 `dashMergeProgress`（`DownloadProgressMathTest` 新增 2 例）；双 flavor 各 **88 suites / 954 tests / 0 failures**。
- 设备侧（Wear OS 模拟器，Android 14 / x86_64）：先用 `app_process` 探针验证「分片 MP4 → 标准 MP4」整条链路成立；实现完成后再用 `DebugBridge merge` 跑**正式代码路径**——两条匿名 360P 样本（3,867,429B + 1,265,842B）→ `result=OK(0) cost=3381ms`、产物 5,167,200B、`tracks=2`（`video/hevc` 2992 samples / `audio/mp4a-latm` 8584 samples，与源文件一致）、进度回调 `0,20,50,90`。
- 端到端下载（真实 1080P 视频）需要登录态，未在本次改动内实测；真机用例见 `docs/review/fix-progress.md` §2.1。

## 风险

- 极旧设备上 MediaMuxer 可能拒绝某些编码；已按「失败保留双文件」兜底。
- 合并期间多占约一份文件大小的磁盘空间；空间不足时直接跳过合并（保留双文件）。
