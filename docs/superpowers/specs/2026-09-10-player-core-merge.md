# 播放内核合并（方案 A + 方案 B）设计文档

> 制定日期：2026-09-10
> 状态：进行中
> 目标：**以保证播放性能为最高优先级**，把两个播放器（`PlayerActivity` / `ShortVideoPlayerActivity`）的播放内核与弹幕栈合并为一套。
> 相关阅读：`docs/architecture-map.md` 第 7.4 节（两个播放器是两套独立实现）

---

## 1. 为什么要合并（性能视角）

合并本身不是目的，**它解决的是三处会造成实际播放开销的重复实现**：

| 重复实现 | 性能代价 |
|---|---|
| **Surface 绑定**：`PlayerActivity` 用 `java.util.Timer` 每 200ms 轮询等 surface 就绪；短视频用 `TextureView` listener | 首播/切清晰度/切分页/切听视频模式**每次都新建一个 Timer 线程**；surface 未就绪时最长等满 200ms 才开始 `prepareAsync`，**直接拖慢首帧** |
| **进度轮询**：`PlayerActivity` 主线程自循环；`IjkPlayerBridge` 协程 | 两套机制、两套生命周期，容易漏清理 |
| **弹幕栈**：`PlayerActivity` 内联 `streamDanmaku`/`createParser`/`DanmakuContext` 配置；`DanmakuManager` | 切清晰度/切分页都会重建 `DanmakuContext` + parser；两套实现意味着两套 bug |

**不可动摇的性能红线**：短视频播放器需要**多实例**（ViewPager2 缓存页各持一个播放器），所以内核必须**可多实例、且不持有 Activity/View 强引用**。`PlayerIntegrator` 把 `Context` + `FrameLayout` 焊进构造器，是反模式，不作为内核基座。

---

## 2. 分层设计

```
activity/（UI、手势、Intent 契约、页面级功能）
    ├─ PlayerActivity            普通播放器：多P/清晰度/字幕/互动视频/直播/听视频/MediaSession
    └─ ShortVideoPlayerActivity  短视频：ViewPager2 分页/预加载/封面/控制栏
                 ↓ 只依赖下面两层
player/
    ├─ PlayerSurfaceBinder       ① Surface 就绪事件化（无轮询、无线程）
    ├─ VideoPlayerCore           ② 播放器生命周期 + 状态 + 进度回调（多实例安全）
    └─ DanmakuController         ③ 弹幕（XML / protobuf 分段，统一栈）
                 ↓
    IjkMediaPlayer / DanmakuFlameMaster
```

**保留在 Activity 层、不进内核的东西**（避免内核接口变形）：
直播模式（WebSocket 弹幕 + 无时长无 seek）、互动视频分支、字幕轨选择、视频分段、听视频模式的 UI、MediaSession 具体实现、所有 Intent extras / `setResult` 契约。

---

## 3. 内核接口约定（硬性）

1. **不持有 `Activity`/`View` 强引用**：`VideoPlayerCore` 只接受 `Context.applicationContext` 与回调；View 通过 `attach*` 方法传入，内部以弱引用或及时解绑处理。
2. **回调线程 = 主线程**：所有对外回调（`onPrepared` / `onCompletion` / `onPosition` / `onBufferingChange` / `onError`）统一在主线程投递，调用方不需要自己 `runOnUiThread`。
3. **`IjkMediaPlayer` 必须在有 Looper 的线程创建**：IJK 的事件分发到创建线程的 Looper，违反会导致回调在 IO 线程改 UI。
4. **幂等释放**：`release()` 可重复调用，第二次是 no-op。
5. **状态用 `StateFlow<PlayerState>` 作为唯一真相源**，进度等高频数据额外提供回调，避免调用方为了拿一个 position 去订阅整个 state。

---

## 4. 分步实施（每步都必须 `assembleDebug` + `testDebugUnitTest` 通过）

| 步骤 | 内容 | 性能收益 | 需要真机 |
|---|---|---|---|
| **S1** ✅ | `player/PlayerSurfaceBinder.kt`：Surface 就绪改事件回调，接入 `PlayerActivity`（删除 `surfaceTimer`、`mSurfaceTexture`） | 去掉每次 `setDisplay()` 的 Timer 线程；去掉最长 200ms 的首帧等待 | ✅ 是（黑屏/不播） |
| **S2** ✅ | `player/VideoPlayerCore.kt`：通用播放内核（多实例安全、无 View 依赖、回调统一主线程、`reload()` 复用播放器保留进度）。**已可用，但尚未接入消费方** | 复用 `IjkMediaPlayer` 省掉切清晰度/切分页的一次 native 播放器创建 | ✅ 是 |
| **S3** ✅ | 弹幕栈合并（方案 B）：`DanmakuManager` 补 protobuf 分段 / 空 parser / 与 `PlayerActivity` 完全同签名的 `addDanmaku`；`PlayerActivity` 删掉内联的 `createParser`×2 + `streamDanmaku`×2 + `mContext` | 两个播放器共用一套弹幕配置与回调；`PlayerDanmuClientListener` 无需改动 | ✅ 是 |
| **S4** ⏳ | 短视频迁到 `VideoPlayerCore` | 为 S5 做准备 | ✅ 是 |
| **S5** 🟡 | 短视频性能：**已做** `bind()` 一次性初始化（不再每次重建手势与全部监听器）、`release()` 去掉冗余 `stop()`、缓冲指示器与进度文本按变化才刷、`releasePlayer` 复位；**待做** `TextureView` 复用与相邻页预创建 | 滑动期分配减少、主线程释放不再等待 | ✅ 是 |
| **S6** ⏳ | 删死代码：`PlayerIntegrator`、`PlayerScaleMode`、`DanmakuManager.loadFromProtobuf`；决定 `PlayerControlDelegate` 去留 | 无（清理） | 否 |

### S1 实施记录（已完成）

新增 `player/PlayerSurfaceBinder.kt`，把 `PlayerActivity.setDisplay()` 里两段 200ms 轮询 `Timer` 换成
`TextureView.SurfaceTextureListener` / `SurfaceHolder.Callback` 驱动：

- 删除了 `surfaceTimer` 字段与 `mSurfaceTexture` 字段，以及随之而来的 `Surface`/`SurfaceHolder`/`SurfaceTexture` 三个 import；
- 新增 `attachSurface(SurfaceTarget)` / `detachSurface()` 两个辅助方法，统一 SurfaceView / TextureView 两种挂载方式；
- 两个回调语义分开：`onReadyForPrepare`（正在等开播 → 挂载 + prepare）与 `onSurfaceReattached`（surface 重建 → 只重新挂载，SurfaceView 再 seek 回当前位置强制出画面，与旧行为一致）。

**原实现里被顺带修掉的一个隐性 bug**：旧代码是在轮询命中之后才 `surfaceHolder.addCallback(...)`，如果 surface 早已 created，`surfaceCreated` 再也不会触发，那段"重建时重新 setDisplay"的逻辑实际是死的。现在回调在 `initUI` 就注册，能正常触发。

**线程决策（有意为之）**：`PlayerSurfaceBinder` 保证 `await()` 与所有回调都在主线程执行（`setDisplay()` 会从 onCreate 的 `CenterThreadPool.run` 块里被调用，而 View 状态只能主线程读）。
因此 `MPPrepare()` 从"Timer 线程"变成了"主线程"。保留这个选择是有意的：主线程执行**天然串行化**，能避免用户快速连点切清晰度时两路并发对同一个 `IjkMediaPlayer` 调 `setDataSource`/`prepareAsync`；而 `setDataSource` 在 `ijkplayer-java` 里最终只走 native `_setDataSource`，短视频播放器本来也就在主线程这么做。

> **S1 待真机确认的点**：首播能正常出画面、切清晰度/切分页后能正常出画面、退后台再回来画面能恢复（`onSurfaceReattached` 路径）、SurfaceView 与 TextureView 两种设置都可用。

### S2 实施记录（已完成，尚未接入消费方）

新增 `player/VideoPlayerCore.kt`（约 400 行），相对 `IjkPlayerBridge` 的关键差异：

- **Surface 由内核自己管**：内部持有 `PlayerSurfaceBinder`，`attachTextureView/attachSurfaceView` 之后不需要调用方操心就绪时机；
- **多实例安全**：非单例、不持有 Activity、`release()` 幂等，满足短视频 ViewPager2 同时持有多页播放器的需求；
- **`reload()` 复用播放器**：切清晰度/切分页时只 `reset()` + 重新套 options + 重新设数据源，省掉一次 native 播放器创建与解码器初始化（`keepPosition=true` 还会把当前位置带过去）；
- **`onPosition(pos, duration)` 高频回调**：调用方不必为了拿一个播放位置去订阅整个 `state`。

> 本步是**纯新增**，没有任何消费方，因此零回归风险；它的价值要等 S4 接上才兑现。

### S3 实施记录（已完成，方案 B）

- `DanmakuManager` 新增：`loadFromProtobufSegments(segments)`（新版分段弹幕，原来只内联在 `PlayerActivity.downdanmuNew()` 里）、`prepareEmpty()`（直播模式先准备空 parser）、以及参数与原 `PlayerActivity.addDanmaku(text, color, textSize, type, backgroundColor)` **完全一致**的重载 —— 这一点很关键，因为 `PlayerDanmuClientListener` 直接调用 `playerActivity.addDanmaku(...)` 的 5 参形式，签名一致才能零改动迁移。
- `PlayerActivity` 删除：`createParser`×2、`streamDanmaku`×2、`mContext` 字段，以及 12 个已无用的 danmaku parser import；新增 `bindDanmakuView()` / `releaseDanmaku()` / `prepareDanmaku {}` 三个小助手。
- **行为保持**：`addDanmaku` 的 time/priority/textSize 公式、`DanmakuContext` 的六项配置、`setMaximumLines`/`preventOverlapping` 的映射全部与原实现逐项对齐。
- **已知的刻意差异**：`prepared()` 里的系统提示文案由"弹幕君准备完毕～(是新来的哦～)/(*≧ω≦)"统一成了 `DanmakuManager` 的"弹幕准备完毕"。纯文案，无功能影响。

### S5 实施记录（部分完成）

`ShortVideoPlayerActivity` 的滑动期开销：

- `bind()` 原本每次都重建 `GestureDetector` + `ScaleGestureDetector` + `setOnTouchListener` + 全部按钮监听器，现全部移到 `PageHolder` 的 `init {}` 一次性注册，`bind()` 只做条目数据绑定；
- `IjkPlayerBridge.release()` 去掉 `stop()`（`reset()` 已足够，而 `stop()` 是等待型调用，且这段跑在主线程的 `onViewRecycled` 上）；
- 缓冲指示器按"目标可见性是否变化"才动 View；进度文本按整秒变化才拼字符串 + `setText`（进度条本身仍每 250ms 刷以保证平滑）。

> `TextureView` 复用与相邻页预创建（真正的起播加速项）仍未做，留到 S4 之后。

---

## 5. 已知坑（实施时必须遵守）

1. **`DanmakuManager.createParser()` 只能在主线程、不可并发**：`DanmakuLoaderFactory.create(TAG_BILI)` 返回进程级单例 `BiliDanmakuLoader`，`dataSource` 是它的实例字段（`BiliDanmakuLoader.java:29,44-51`）。详见 `docs/architecture-map.md` 7.4 节。
2. **`createParser()` 不做 XML 解析**：真解析在 `getDanmakus()` → `parse()` 懒执行，跑在 `DanmakuView` 自己的渲染线程（唯一调用点 `DrawTask.java:283`）。**不要**为了"把解析挪出主线程"去改它。
3. **`PlayerActivity` 的 Intent 契约必须冻结**：`PlayerApi.jumpToPlayer()` 的 20 个 extra key + `finish()` 回传的 `progress`/`isPlaying`/`isDanmakuEnabled`/`quality`。它是三个可替换后端之一（`terminalPlayer`/`mtvPlayer`/`aliangPlayer`），且 `exported="true"`。
4. **`PlayerActivity` 直接 `extends Activity`**，不走 `BaseActivity`；不要顺手把它并进基类体系（那是独立风险）。
5. **`setDisplay()` 可能从后台线程调用**（onCreate 的 `CenterThreadPool.run` 块），因此 `PlayerSurfaceBinder.await()` 必须自己保证在主线程执行。
