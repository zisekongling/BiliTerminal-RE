# 重复项合并 · 进度与交接

> 目标：以**优化手表端性能与体积**为目的，只做「重复项合并」，不动死代码。
> 基线清单：`docs/review/cleanup-scan.md` §5（重复代码簇）。
> 状态：**6 项合并全部完成，且已通过真机实测**（26.09.11）。
> 每项都通过 `clean` + `assembleDebug` + `testDebugUnitTest`；真机按 `docs/review/cleanup-test-plan.md` 跑完，用户确认全部通过。

---

## 1. 总览

| # | 合并项 | 重复份数 | 结果 | 主要收益 |
|---|---|---|---|---|
| 1 | 列表长按「快速缓存」 | 3 份（逐字节相同） | → `adapter/video/VideoQuickCache.kt` | 净删约 200 行 |
| 2 | 经验 / 硬币流水列表 | 2 份 adapter + 2 份同 MD5 布局 | → `adapter/LogListAdapter.kt` + `res/layout/cell_log.xml` | 删 2 个类 + 1 个布局 |
| 3 | CDN 裸 deflate 解压 | 3 份 | → `util/NetWorkUtil.decompress(byte[])` | native 内存：统一在 `finally` 里 `end()` |
| 4 | 时间格式化 | 11 处各自 `new SimpleDateFormat` | → `util/TimeUtil.kt`（ThreadLocal 缓存） | 性能 + 修掉 3 处线程不安全的静态实例 |
| 5 | 「跳用户主页」 | 16 处手抄 Intent | → `BiliTerminal.jumpToUser(context, mid)` | 体积 + 行为一致 |
| 6 | 表冠（旋冠）滚动 | 3 份 + 1 处重复开关读取 | → `ui/widget/RotaryEncoderSupport.kt` | 手表核心交互，逻辑单点化 |

---

## 2. 逐项明细

### 2.1 `handleQuickCache` 三份 → 一份

三份原本**逐字节相同**（MD5 `FEAAEA3305463A03BD7A69A5CD433833`，各 100 行）。

| 文件 | 变化 |
|---|---|
| `adapter/video/VideoQuickCache.kt` | 新增（`handle(context, videoCard)` + 私有 `fetchVideoInfo`） |
| `VideoCardAdapter.kt` | 177 → **69** 行 |
| `HistoryVideoCardAdapter.kt` | 198 → **90** 行 |
| `UserVideoAdapter.kt` | 201 → **94** 行 |

### 2.2 经验 / 硬币流水列表 → 泛型一份

两个布局曾 **MD5 完全相同**（`04BA39718759CB52A4F30C32852EBA3D`），adapter 仅差模型类型与 delta 文案。

- 新增 `adapter/LogListAdapter.kt`（泛型 `<T>` + `Row(delta, reason, time)` 映射）与 `res/layout/cell_log.xml`。
- 修改 `CoinLogActivity.kt` / `ExpLogActivity.kt` 各 1 处构造点。
- 删除 `CoinLogAdapter.kt`、`ExpLogAdapter.kt`、`cell_coin_log.xml`、`cell_exp_log.xml`。

### 2.3 解压逻辑 → `NetWorkUtil.decompress`

原先三份逐字相同（`DownloadService` / `DownloadActivity` / `PlayerActivity` 的 companion），
权威实现挪到 `util/NetWorkUtil.java`（顺带替换掉那里原有的、零调用的 `uncompress`）。

- 调用方改造：`DownloadService:1447`、`DownloadActivity:208`、`PlayerActivity:1187`、
  `LocalListActivity:425`、`ShortVideoPlayerActivity:805`。
- 行为差异只有一处**变好**：原来 `DownloadActivity`/`PlayerActivity` 把 `decompresser.end()`
  放在 `try/finally` **之外**，一旦 `out.write` 抛异常（例如 OOM）就不会 `end()`，泄漏一份 native 内存；
  现在统一在 `finally` 里释放。
- `api/UserInfoApi.decompressResponse`（br + gzip）**算法不同，未合并**。

### 2.4 时间格式化 → `util/TimeUtil.kt`

合并 11 处构造点，落点为「模式 + Locale」到 `SimpleDateFormat` 的 **ThreadLocal** 缓存。

| 原位置 | 模式 | 处理 |
|---|---|---|
| `adapter/message/NoticeHolder.kt` | `yyyy-MM-dd HH:mm` | 静态实例 → `TimeUtil.formatDateTime` |
| `adapter/TimelineAdapter.kt` | `HH:mm` | 实例字段 → `TimeUtil.formatTime`；**`dateFormat` 字段是死的，一并删除** |
| `adapter/video/HistoryVideoCardAdapter.kt` | `HH:mm`（`Locale.CHINESE`） | 静态实例 → `TimeUtil.formatTime(..., Locale.CHINESE)` |
| `ui/widget/TextClock.kt` | `HH:mm` | 静态实例 → `TimeUtil.formatTime`（顺带删掉失效的 `@SuppressLint("SimpleDateFormat")`） |
| `activity/user/VipActivity.kt` | `yyyy-MM-dd HH:mm:ss` | 局部变量 → `TimeUtil.formatDateTimeSec` |
| `api/AppInfoApi.java` ×2 | `yyyy-MM-dd` / `yyyy-MM-dd hh:mm` | → `formatDate` / `format(PATTERN_DATE_TIME_12H)` |
| `api/LiveApi.java` | `yyyy-MM-dd HH:mm:ss` | → `formatDateTimeSec` |
| `api/VideoInfoApi.java` | `yyyy-MM-dd HH:mm:ss` | → `formatDateTimeSec` |
| `model/Reply.java` | `yyyy-MM-dd HH:mm`（`Locale.SIMPLIFIED_CHINESE`） | → `formatDateTime(..., Locale.SIMPLIFIED_CHINESE)` |

**性能意义**：`NoticeHolder` / `TimelineAdapter` / `HistoryVideoCardAdapter` 都在
`onBindViewHolder` 这类滚动高频路径上格式化时间；`SimpleDateFormat` 的构造要查 Locale 数据，
在手表端弱 CPU 上是可测量的开销。另外 `NoticeHolder` / `TextClock` / `HistoryVideoCardAdapter`
的三处**静态实例**原本被多线程共用，本身就是线程不安全的。

### 2.5 跳用户主页 → `BiliTerminal.jumpToUser`

`BiliTerminal.jumpToUser(context, mid)` 早就有，但只有 `RecentUpAdapter` 在用，另外 **16 处**各自手抄。

改造的调用方：
`UserListAdapter`、`MedalListAdapter`、`FollowGroupAdapter`、`ElectricUserAdapter`、
`ReplyAdapter`、`PrivateMsgSessionsAdapter`、`NoticeHolder`、`DynamicHolder`、`OpusContentAdapter`、
`AboutActivity`、`MySpaceActivity`、`util/LinkUrlUtil`（2 处）、`util/StringUtil`。

**逐个核对过**：这些点全部只传 `"mid"`、全部不加 flag，与 `jumpToUser` 完全等价（含 `Intent(context, cls)` 与 `Intent().setClass(...)` 两种写法，二者等价）。

### 2.6 表冠滚动 → `RotaryEncoderSupport`

| 原文件 | 前 → 后 |
|---|---|
| `ui/widget/RotaryRecyclerView.kt` | 52 → 29 行 |
| `ui/widget/RotaryScrollView.kt` | 52 → 29 行 |
| `ui/widget/RotaryNestedScrollView.kt` | 49 → 36 行 |
| `ui/widget/RotaryEncoderSupport.kt` | 新增（判定 + 轴值换算 + 滚动） |
| `tutorial/TutorialPagerActivity.kt` | 删掉重复的开关/灵敏度读取，改调 `multipleOf(SettingsKeys.UI_ROTATORY_SCROLL)` |

三者基类分别是 `RecyclerView` / `ScrollView` / `NestedScrollView`，**无法用共同父类归并**，
因此用组合：控件保留自己的事件接入方式，只有「判定 + 换轴值 + 滚动」这段真正重复的逻辑被收进 helper。
顺带把散落的字符串字面量 `"ui_rotatory_*"` 改走 `util/SettingsKeys.kt` 的常量。

> 注意：`View` 本身没有可访问的 `smoothScrollBy(int,int)`（只有具体子类有），
> 所以 helper 的滚动动作由调用方以 lambda 传入。

---

## 3. 新增的公共落点（以后别再手抄）

| 落点 | 取代了 | 用在哪 |
|---|---|---|
| `adapter/video/VideoQuickCache.handle(context, card)` | 3 份长按快速缓存 | 视频卡列表的长按 |
| `adapter/LogListAdapter<T>` + `layout/cell_log.xml` | 2 份流水 adapter + 2 份同 MD5 布局 | 经验 / 硬币变化记录页 |
| `util/NetWorkUtil.decompress(byte[])` | 3 份 `Inflater(true)` | 所有 CDN 裸 deflate 响应 |
| `util/TimeUtil` | 11 处 `new SimpleDateFormat` | 全工程时间格式化 |
| `BiliTerminal.jumpToUser(context, mid)` | 16 处手抄 Intent | 所有「跳用户主页」 |
| `ui/widget/RotaryEncoderSupport` | 3 份表冠滚动 + 1 处开关读取 | 三个 Rotary* 控件 + 教程翻页 |

---

## 4. 刻意**没有**改动的地方（行为保持）

合并过程里发现几处「看起来该统一」但改了就会变行为/文案的地方，一律原样保留：

1. **`AppInfoApi` 的 `yyyy-MM-dd hh:mm` 是 12 小时制**（同文件另一处用的是 24 小时制 `HH`）。
   这多半是历史笔误，但改 `HH` 会直接改变赞助名单的显示文案 —— 保留，并在 `TimeUtil.PATTERN_DATE_TIME_12H` 上写了注释。
2. **经验流水的 delta 恒前置 `"+"`**，负数会显示成 `"+-5"`（硬币流水没有这个问题）。保留。
3. **`RotaryNestedScrollView` 不抢焦点**，且走 `dispatchGenericMotionEvent` 覆写而不是
   `setOnGenericMotionListener` —— 两者都原样保留（helper 用 `requestFocus` 参数区分）。
4. 硬币 / 经验两个 Activity 本身高度相似（各 65 行），但它们的入口由
   `MySpaceMenu.ITEMS` 按 Activity 类注册，合并会牵动导航配置，**不在本轮范围**。

---

## 5. 未纳入本轮的项目

| 项目 | 为什么不做的原因 |
|---|---|
| 视频卡片组装 **19 处**（`api/` 层） | **前置**：`VideoCard` 是多语义垃圾桶（`bvid` 里放过 `season_id`/`media_id`/截断 BV 号，`aid` 里放过 `media_id`，`view` 有 4 种语义），必须先拆模型，否则固化错误 |
| 分页骨架 3 份 / 详情页骨架 5 份 | 骨架级重构，改动面大，需逐步真机验证 |
| `DownloadService` 内部大块重复 | **前置**：先修 `start()` 的并发竞态与静态状态 |
| `player/` 两代播放器实现 | **前置**：先决定 `VideoPlayerCore.kt`（411 行零消费方）是复用还是删除 |
| Glide 头像参数 10 处 → `GlideUtil.requestRound` | 用户本轮未选 |
| `MenuSettingAdapter` ↔ `MySpaceSettingAdapter` | `AGENTS.md` 把「复用该双分区拖拽写法」定为**刻意约定**，优先级最低 |

---

## 6. 体积与性能的实际影响（如实说明）

- **release 体积**：按 `AGENTS.md` 的实测记录，死代码清理对 release 只减了约 0.22 MB——
  因为 R8 本来就会 strip 未被引用的类与方法。本轮是**合并**而不是删除，被合并的逻辑原本就都被引用，
  R8 无法把它们当死代码去掉，所以**release 体积的收益很小**（主要是少了两份 companion 实现与一个布局文件）。
  真正的大头仍是 native `.so`（占 release 67.3%，其中 `libijkffmpeg.so` 单个 5.16 MB）。
- **debug APK 体积**：debug 不做 R8，delegate/方法数确实减少，但 debug 包在增量构建下会虚高 3~4 MB，
  任何体积对比都必须 `clean` 后测量。
- **性能**：本轮有两处是实打实的：
  1. 时间格式化从「每次进 `onBindViewHolder` 可能重新构造 / 共用不安全的静态实例」变成 ThreadLocal 复用；
  2. `Inflater` 的解压路径从 3 份收敛为 1 份，并保证异常时也 `end()`（此前有 native 内存泄漏路径）。
  其余项目（表冠、跳转）主要是**可维护性与行为一致性**，不是性能。
- **可维护性**：6 类重复各自收敛成药丸状的单一落点。**行数变化按逐项实测净减约 280 行**
  （`git diff --stat` 混着另一轮死代码清理，无法隔离统计，下面是逐项实测值）：
  - 步骤 1 净删约 193 行（576 → 383，含新增的 130 行 helper）
  - 步骤 2 净删约 60 行（删 2 个 adapter + 1 个布局，换来 1 个泛型 adapter + 1 个布局）
  - 步骤 3 净删约 43 行（删 3 份 companion，新增一份权威实现）
  - 步骤 4 **净增约 50 行**（11 处调用各减几行，但新增了 85 行的 `TimeUtil`）——
    这一项的收益在**性能与线程安全**，不在行数
  - 步骤 5 净删约 45 行（16 处手抄 Intent）
  - 步骤 6 **净增约 13 行**（153 → 169，多了 helper）——同样，收益在逻辑单点化，不在行数

> 结论：如果你的首要目标是**减小 release 包体积**，本轮的帮助有限，应该去看 `libijkffmpeg.so`
> （需要 NDK 重新编译精简编解码器，属于另一件事）；如果目标是**手表端体感与后续维护成本**，本轮有效。

---

## 7. 复现验证

```bash
./gradlew.bat :app:clean --offline --no-configuration-cache
./gradlew.bat :app:assembleDebug --offline --no-build-cache --no-configuration-cache
./gradlew.bat :app:testDebugUnitTest --offline --no-build-cache --no-configuration-cache
```

`clean` 与 `assemble` **必须分两次调用**（`res/` 文件集合被改过：新增 `cell_log.xml`、删除两个旧布局）。

**真机实测：26.09.11 全部通过**（清单见 `docs/review/cleanup-test-plan.md`，可复用为回归清单）：
1. 三个 `Rotary*` 控件的表冠滚动手感与灵敏度设置；
2. 列表项长按快速缓存（视频卡列表 / 历史 / 用户空间三个入口）；
3. 经验、硬币变化记录页的文案与 delta 显示；
4. 各页时间显示（公告、直播、视频发布时间、评论时间、赞助名单、大会员、追番）。
