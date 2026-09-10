# 全代码库扫描报告（死代码清理 + 重复代码合并基线）

> 扫描日期：当前工作区快照（`versionName` 见 `app/build.gradle`）
> 目的：为接下来的「死代码清理」和「重复代码合并」提供**带证据的基线清单**，不包含任何代码改动。
> 方法：4 条并行深度扫描线（`api`+`model` / `util`+基建 / `activity` / `adapter`+`player`+`service`）+ 本轮机械化交叉引用扫描 + 重复代码块检测。
> 详细子报告与原始证据：`.dsh/scan/`（见文末附录）。

---

## 0. 一句话结论

这个代码库**几乎没有「零引用的废文件」式死代码**（整文件死 24 个 / 3099 行，占总量 5.3%），真正吃掉维护成本的是**同一套骨架被手抄多份**——分页骨架 3 份、视频卡组装 19 处、详情页骨架 5 份、用户跳转 9 处、播放器两代实现并存。清理的收益主要来自**合并**，而不是**删除**。

另外发现 **11 处「零引用但是活代码」的陷阱**（Glide 模块、JS 桥、EventBus 订阅等），如果按引用计数机械删除，会直接删掉功能。

---

## 1. 代码库总览

| 维度 | 数值 |
|---|---|
| 源文件总数 | **386**（`app/src` 下 `.java`/`.kt`） |
| 总行数 | **58,339** 行 / 2301 KB |
| 主代码 | Kotlin 240 文件 / 40,394 行；Java 135 文件 / 17,072 行 |
| 测试代码 | 11 文件 / 873 行（Kotlin 10 + Java 1） |
| `res/layout/` | 149 个布局 |
| 空目录 | **24 个**（含 `res/navigation`） |
| 独立 Gradle 模块 | `:app`、`:ijkplayer-java`、`:DanmakuFlameMaster`、`:brotlij` |

### 最大的 10 个文件（重构风险集中区）

| 文件 | 行数 |
|---|---|
| `activity/player/PlayerActivity.kt` | 3,044 |
| `service/DownloadService.kt` | 1,581 |
| `activity/video/ShortVideoPlayerActivity.kt` | 1,011 |
| `api/DynamicApi.java` | 732 |
| `adapter/dynamic/DynamicHolder.kt` | 718 |
| `activity/video/info/VideoInfoFragment.kt` | 687 |
| `activity/video/local/LocalListActivity.kt` | 669 |
| `api/PlayerApi.java` | 632 |
| `util/NetWorkUtil.java` | 613 |
| `adapter/video/CacheListAdapter.kt` | 540 |

> `PlayerActivity.kt` + `DownloadService.kt` + `ShortVideoPlayerActivity.kt` 三个文件 = 5,636 行，占主代码 14%，且三者各自内部都有大块重复（见 §5.2）。

---

## 2. 扫描方法与口径陷阱（复现前必读）

本轮踩到并已修正的坑，后续再做同类扫描请直接避开：

| 陷阱 | 症状 | 正确做法 |
|---|---|---|
| **本机是 Windows PowerShell 5.1** | `.ps1` 里出现中文会**解析失败**（无 BOM 的 UTF-8 被按 ANSI 读） | 扫描脚本一律纯 ASCII |
| **`Get-Content` 丢行** | `DynamicApi.java` 读到 716 行（实为 732）；`PlayerActivity.kt` 读成 3012（实为 3044） | 用 `[System.IO.File]::ReadAllLines($p, [Text.Encoding]::UTF8)` |
| **`Measure-Object -Line` 漏空行** | 行数系统性偏小（`DynamicApi` 654 vs 732） | 同上，或用 `.Count` |
| **`git status` / `git diff` 被沙箱拒绝** | 无法核对工作区是否干净 | 用文件时间戳判断改动范围 |
| **资源引用扫描漏 `res/**/*.xml`** | `cell_topbar` 明明在用却被判死 | 必须把 `res` 下所有 XML 一起纳入待搜文本 |
| **Kotlin 风格名点转下划线** | `Theme.ClassicTerminal` 在代码里是 `R.style.Theme_ClassicTerminal` | 正则要同时匹配点号与下划线两种写法 |
| **重复检测被 import 块污染** | 前 8 大「重复簇」全是 import 列表 | import/package/注解行必须**打断**窗口；空行与注释只**跳过**不断开 |
| **子串误判** | `toDouble()` 命中了 `TODO` 搜索 | 关键词搜索加词边界 |

> 另：**扫描期间工作区被并发修改过**（`BiliTerminal.java` → `BiliTerminal.kt`，`AGENTS.md` 同步更新）。本报告的行号以扫描时刻为准，动手前请重新 grep 校验。

---

## 3. 死代码总清单

### 3.1 可整文件删除的源码（24 个文件，共 3,099 行）

全部经「类名在**其他文件**中的命中数」核实；`自封闭` 表示该文件内的多个类/对象只互相引用，对外零暴露。

| 文件 | 行数 | 判定依据 |
|---|---|---|
| `adapter/article/ArticleContentAdapter.kt` | 392 | 全库仅命中自身（`:36/:38/:39/:40`）；被 `OpusContentAdapter` 完全取代 |
| `adapter/video/LocalVideoAdapter.kt` | 329 | 类名全库仅出现 1 次（声明处） |
| `util/ArticleContentParser.java` | 304 | 类名全库仅出现 1 次 |
| `player/PlayerControlDelegate.kt` | 297 | 只被死类 `PlayerIntegrator` 引用；含 `GestureHandler`、`SpeedOption`、`QualityOption`、`ControlState` |
| `util/Mp4FastStart.kt` | 189 | 自封闭（`Mp4FastStart`/`BoxInfo` 只互相引用） |
| `adapter/video/FolderAdapter.kt` | 183 | 类名全库仅出现 2 次（声明 + 自身） |
| `util/MediaMerger.kt` | 167 | `DownloadService.kt:33` 只有一条 **未使用 import**；`mergeAv` 全库 0 调用 |
| `util/GeetestUtil.java` | 158 | 自封闭死簇 |
| `util/WatchAdapter.kt` | 156 | 自封闭（`WatchAdapter`/`CompatHelper`/`PerformanceOptimizer` 只互相引用） |
| `ui/widget/recycler/BaseAdapter.kt` | 138 | 自封闭死簇的一员（见下） |
| `player/PlayerIntegrator.kt` | 130 | 类名全库仅出现 1 次 |
| `ui/widget/recycler/AbstractAdapter.kt` | 84 | 只被 `BaseAdapter` 继承，无外部子类 |
| `ui/widget/recycler/BaseHolder.kt` | 34 | 只被 `BaseAdapter` 使用 |
| `ui/widget/recycler/WrapContentLinearLayoutManager.kt` | 26 | 类名全库仅出现 1 次 |
| `ui/widget/CustomListView.kt` | 17 | 类名全库仅出现 1 次 |
| `util/ModernLogger.kt` | 76 | 自封闭 |
| `util/TimeUtil.kt` | 70 | 类名全库仅出现 1 次 |
| `util/ViewCache.java` | 63 | 类名全库仅出现 1 次 |
| `util/ImageViewer.java` | 34 | 自封闭 |
| `model/Lyric.java` | 51 | 全库零引用（连 `LyricLine` 一起） |
| `model/OpusCard.java` | 59 | 全库零引用 |
| `model/Playlist.java` | 24 | 全库零引用 |
| `api/BilibiliIDConverter.java` | 40 | 全库零调用（`bvtoaid`/`aidtobv` 均无人用） |
| `activity/TutorialActivity.kt` | 78 | 旧教程死链的一环，**有前置条件，见 §3.6** |

> **`ui/widget/recycler/` 三件套是一个自封闭死簇**：`AbstractAdapter` ← `BaseAdapter` ← `BaseHolder`，而 `BaseAdapter` 全工程无任何子类。现役 adapter 一律直接 `extends RecyclerView.Adapter<XxxHolder>()`。整目录可删。

### 3.2 文件内的死成员

| 类别 | 数量 | 代表 |
|---|---|---|
| `api/` 零调用 public 方法 | 18 | `DanmakuApi.likeDanmaku`/`recallDanmaku`、`EmoteApi` 表情包管理 4 个、`PlayerApi.getVideoUri:417`、`ReplyApi.sendDynamicReply:167`、`VoteApi.getFolloweeVotes:194` |
| `api/` 零引用常量 | 8 | `EmoteApi.PACKAGES_TYPE_*`（4）、`PrivateMsgApi.MSG_TYPE_*`（3）、`VideoInfo.COPYRIGHT_SELF:15` |
| `api/` 零调用 public 方法（死代码） | 3 | `NetWorkUtil.uncompress:376`（裸 deflate，全库 0 调用）、`readStream:364`、`setCookies(Cookies):485` |
| `activity/` 零调用 public 方法 | 3 | `ShortVideoPlayerActivity.updateTitle:586`、`PlayerDanmuClientListener.getBrotliData:93`；另有 `VideoInfoActivity.onCloseAllVideoPages:173` 属**假死**（见 §4） |
| `activity/` 零引用 private 字段 | 3 | `PlayerActivity.danmakuSyncRunnable:171`、`ShortVideoPlayerActivity.bottomButtons:320`、`:985 TAG` |
| `activity/` 未使用 import | **43 条** | 其中 5 条是教程迁移残留（见 §3.6） |
| `player/` 零引用方法 | 多个 | `DanmakuManager.loadFromProtobuf`（实现本身就是坏的：解压后从未把数据交给 parser）、`toggleVisibility`/`setSpeedFactor`/`setTextSizeScale`/`setTransparency`、`PlayerSurfaceBinder.cancelAwait` |
| `service/` 零引用方法 | 多个 | `DownloadService.clear`/`startReDownload`/`toastState`/`activeDownloadsCount`/`ERR_DATABASE` |
| `adapter/` 零引用成员 | 多个 | `SettingsAdapter.listChooseLauncher`、`DragAdapter.getFixedPosition`、`QualityChooseAdapter.onItemLongClickListener` |
| 纯空覆写 / 幽灵订阅 | 8 处 | `OpusInfoActivity.kt:89-91` 的**空函数体 `@Subscribe`**（幽灵订阅，每条 sticky `ReplyEvent` 都分发一次空调用）；4 个 `Search*Fragment` 的 `onCreate { super.onCreate }`；`TerminalContext.leaveDetailPage():281` 空实现 |
| 纯噪声 override | 2 处 | `PlayerActivity.kt:1433 onResume` / `:1438 onStop`，只有一行 `Logu.v` |

**`Api` / `Activity` 零调用方法的安全边界**：`R8` 的 `proguard-rules.pro` 只 keep `activity`/`service`/`ui`，**没有 adapter/player keep 规则**，所以删除死 adapter/player 类不影响 release 包。

### 3.3 未使用资源（约 300 项）

按 `R.*` / `@type/name` / `?attr` / `parent=` 全形态匹配后的零引用统计：

| 类型 | 零引用数 | 备注 |
|---|---|---|
| `color` | **202** | `colors.xml` 共 314 个颜色，代码里只有 **4 处** `R.color.` 引用；其余靠主题/style 间接用。此处是主题重构后的调色板残留 |
| `style` | 25 | 主要是 `Theme.*.NoSwipe` / `.Splash` / `.Dark` 变体未被任何 manifest/代码引用 |
| `dimen` | 21（去重后 15） | `topbar_height`、`radius_card`、`row_height*` 等 |
| `string` | 19 | 过半是 `text_update_*` 整组（`UpdateManager` 已改用硬编码中文） |
| `drawable` | 18 | `icon_quality`、`icon_fullscreen`、`modern_button_bg*`、`background_qrcode` 等 |
| `layout` | **10** | `activity_base`、`activity_simple_main_list`、`bar_quality_select`、`bottom_bar_multi_select`、`cell_date_divider`、`cell_lyric_line`、`cell_topbar`、`dialog_folder_settings`、`dialog_new_folder`、`fragment_lyric` |
| `xml` | 11 | `tutorial_*.xml`，**经 `getIdentifier()` 反射引用**，随教程死链一起删 |
| `menu` / `raw` | 1 + 1 | `bottom_nav_menu`、`keep.xml` |

⚠️ **`color`/`style` 两类需要人工复核**：主题属性（`?attr/colorPrimary` 等）与 `ThemeManager` 的运行期换肤可能间接用到，机械判死有风险。`layout`/`drawable`/`string` 三类经交叉验证假阳性很低，可优先处理。

> **`cell_topbar.xml`（31 行）确认零引用**——架构文档声称「44 个布局共用公共顶栏」，**该改动已被回滚**（`fix-progress.md:216` 记录了还原过程），当前只有 7 个布局含 `<include>`，且都不含 `cell_topbar`。

### 3.4 死依赖（`app/build.gradle`）

| 依赖 | 行 | 依据 |
|---|---|---|
| `hilt-android:2.51.1` + `hilt-compiler` | 238 | 全工程 `@Inject`/`@AndroidEntryPoint`/`@Module` 0 命中 |
| `retrofit2` + `converter-kotlinx-serialization` | — | 无任何 Interface 声明 |
| `kotlinx-serialization-json:1.7.0` | — | 无 `@Serializable` |
| `androidx.navigation` | 256, 257 | 0 引用（`res/navigation/` 目录也是空的） |
| `lifecycle-viewmodel-ktx` | 228 | 0 引用 |
| `protobuf-javalite` | 279 | 0 引用 |
| `geetest` | 282 | 0 引用 |
| `asynclayoutinflater` | 277 | 0 引用（对应 `AsyncLayoutInflaterX` 也**不用它**，是自实现） |
| `cardview` | 253 | 0 引用 |
| `logging-interceptor`（debug） | 244 | 0 引用 |
| `ksp { room.schemaLocation }` | 102, 103 | 项目用 `SQLiteOpenHelper`，无 Room |

**活的依赖**：`coroutines`、`okhttp`、`glide`（含 `okhttp3-integration` 与 `compiler`）、`jsoup`、`zxing`、`eventbus`、`PhotoView`、`brotlij`。

> 删 Hilt 前必须先处理 `BiliTerminalApp.kt`（它带 `@HiltAndroidApp`）。

### 3.5 空目录（24 个）

`data/`、`data/repository/`、`di/`、`navigation/`、`network/`、`network/api/`、`network/model/`、`activity/shortvideo/`、`activity/update/`、`ui/base/`、`ui/component/`、`ui/dynamic/`、`ui/live/`、`ui/menu/`、`ui/message/`、`ui/player/`、`ui/search/`、`ui/settings/`、`ui/shortvideo/`、`ui/splash/`、`ui/user/`、`ui/video/`、`ui/video/viewmodel/`、`res/navigation/`

> 架构文档写「23 个」，实测 **24 个**（多了 `res/navigation`）。

### 3.6 旧教程死链（单独列出，**有前置条件**）

`docs/tutorial-system-redesign.md:94` 已列为待删，当前快照核实结果：

| 环节 | 位置 | 状态 |
|---|---|---|
| `TutorialHelper.showTutorialList()` | `helper/TutorialHelper.kt:137-150` | **零引用**（全库 grep 只命中 `docs/`） |
| `TutorialHelper.show()` | `helper/TutorialHelper.kt:124-135` | 唯一调用点是上面那个死方法 → 随上游死 |
| `activity/TutorialActivity.kt` | 78 行 | 唯一可达路径经 `show()` → 死 |
| `TutorialHelper.loadTutorial()` / `loadText()` | `:31-103` / `:105-122` | 唯一调用点是 `TutorialActivity` → 死 |
| `model/Tutorial.java` / `model/CustomText.java` | — | 只被上面两者引用 |
| `res/xml/tutorial_*.xml`（11 个） | — | 只经 `getIdentifier()` 与 `TutorialActivity` 的默认值引用 |
| 5 条失效 import | `ShortVideoPlayerActivity.kt:45`、`RecommendActivity.kt:10`、`DynamicActivity.kt:17`、`SearchActivity.kt:32`、`MessageActivity.kt:17` | **可立即删，无前置条件** |

🚫 **阻塞点（删之前必须先做）**：
1. `TutorialHelper.showPagerTutorial()`（`:152-190`）**是活的**，4 个调用点：`OpusInfoActivity.kt:77`、`DynamicInfoActivity.kt:65`、`UserInfoActivity.kt:65`、`VideoInfoActivity.kt:84`。它读 `TutorialPagerActivity.isShowing`。**必须先完成 HINT 迁移**，否则删 `TutorialHelper` 会连带打断新教程链路。
2. `TutorialManagerActivity` 仍在读写**新系统共用的** `tutorial_ver_*` 键，会污染新教程的已读状态——需要在迁移时一并处理。

---

## 4. ⚠️ 假死清单（零引用但是活代码，禁止删）

这是本轮扫描**最重要的安全网**。以下项在机械引用计数下都是 0，但删除即等于删功能：

| # | 项 | 为什么零引用却是活的 |
|---|---|---|
| 1 | `helper/CustomGlideModule.kt` | 带 `@GlideModule`，由 Glide 的 **annotationProcessor 生成的 `GeneratedAppGlideModule`** 在运行期反射发现。它还在 `registerComponents` 里给 Glide 换上了**带 Cookie/请求头的 OkHttp**——删掉图片加载直接挂 |
| 2 | `settings/login/CaptchaWebViewActivity.onCaptchaResult` / `onCaptchaError`（`:220`/`:232`） | `addJavascriptInterface` 的 **JS 桥**，由页面内 JS 反射调用 |
| 3 | `video/info/VideoInfoActivity.kt:173 onCloseAllVideoPages` | EventBus `@Subscribe(ThreadMode.MAIN)`，反射分发（长按顶栏关闭所有视频页） |
| 4 | `base/BaseActivity.kt:305 onEvent(SnackEvent)` + 4 处 `onEvent(ReplyEvent)` | 同上，EventBus 反射 |
| 5 | `PlayerActivity` 的 `onPrepared`/`onCompletion`/`onError`/`onInfo`/`onBufferingUpdate` | `IMediaPlayer` 回调 |
| 6 | `res/xml/tutorial_*.xml` | `getIdentifier()` 按名字反射取资源 |
| 7 | `layout/*.xml` 里对自定义 View 的全限定名引用、`tools:context` | 只出现在 XML，代码里 grep 不到 |
| 8 | `model/` 中走磁盘 JSON 的字段（`VideoFolder`/`VideoMeta`/`LocalVideo`） | **字段名即存储格式**，改名/删除会读不出旧缓存 |
| 9 | `model/` 中 `Parcelable` 类的字段（`Stats` 等） | 删字段必须与 `writeToParcel`/`readToParcel` 两端同步，否则**运行期 Parcel 错位且无编译报错** |
| 10 | `player/VideoPlayerCore.kt`（411 行） | 零消费方，但它是**播放器合并 S4 的目标内核**，功能是 `IjkPlayerBridge` 的严格超集（含 `reload()` 复用、surface 就绪事件化、修掉了 `start()` 在 `mediaPlayer == null` 时仍置 `isPlaying` 的 bug）。删它 = 放弃合并方案 |
| 11 | `util/GlideUtil.requestRound` / `BiliTerminal.jumpToUser` | 前者只有 1 处在用（对着 10 处手抄的 Glide 参数），后者只有 1 处在用（对着 9 处手抄的跳转）——它们是**合并的目标落点，不是死代码** |

> **教训**：`CustomGlideModule` 是本轮我自己的机械化扫描给出的假阳性。这就是为什么「引用计数」只能用来**筛候选**，不能用来**下结论**。

---

## 5. 重复代码簇

### 5.1 逐字节 / 逐行复制（已用 MD5 或逐行比对验证）

| # | 内容 | 位置 | 证据 | 合并收益 |
|---|---|---|---|---|
| 1 ★ | `handleQuickCache` 整段 | `VideoCardAdapter.kt:73-172`、`HistoryVideoCardAdapter.kt:91-190`、`UserVideoAdapter.kt:93-192` | **MD5 三份完全一致**（`FEAAEA3305463A03BD7A69A5CD433833`），各 100 行，入参只有 `context` + `videoCard` | **净删 200 行，零行为风险** |
| 2 ★ | `cell_coin_log.xml` ≡ `cell_exp_log.xml` | `res/layout/` | **MD5 完全一致**（`04BA39718759CB52A4F30C32852EBA3D`） | 布局 + adapter 一起合并（两个 adapter 各 44 行，仅 6 行不同） |
| 3 ★ | `handleLoginSuccess` | `PasswordLoginFragment.kt:213-248` ≡ `SMSLoginFragment.kt:264-299` | 36 行逐行相同 | 抽 1 个函数，删 36 行 |
| 4 | 表冠（Rotary）滚动分发 | `RotaryRecyclerView.kt:20-52` ≡ `RotaryScrollView.kt:20-52`；`RotaryNestedScrollView.kt:20-48` 近似 | 30+ 行逐行相同，仅 pref key 与分发方式不同 | 抽共同基类/委托 |
| 5 | `Inflater(true)` 解压块 | `DownloadActivity.kt:238-257` ≡ `PlayerActivity.kt:3024-3043`；另 `DanmakuManager.kt:121`、`DownloadService.kt:200`、`NetWorkUtil.java:379`（死） | 20 行逐字相同，共 **5 份** | 抽工具函数 |
| 6 | 应用初始化片段 | `BiliTerminal.kt:205-235` ≡ `BiliTerminalApp.kt:192-222` | 30 行相同（死类里的那份） | 随 `BiliTerminalApp` 处理 |
| 7 | 设置项构造块 | `MessageSettingsActivity.kt` 内 3 份（`:91-123`/`:102-134`/`:113-145`） | 同文件内 3 份滑动窗口命中 | 数据驱动化 |
| 8 | `MessageApi` 两个方法逐段复制 | `:184-193` ↔ `:283-292`、`:317-326` ↔ `:333-342`；`getLikeMsg:78`/`getReplyMsg:183`/`getAtMsg:281` 三个 ~100 行方法 | 逐段重复，switch 五分支实际 **6 份以上** | **api 层收益最高的合并** |
| 9 | `RecommendApi` 内两段 | `:88-98` ↔ `:110-120` | 逐行相同 | 抽函数 |
| 10 | `UserInfo.java` 5 个构造器 | `:47-56` / `:62-71` / `:79-88` / `:96-105` / `:114-123` | 10 行同构 × 5 | 收敛构造器重载 |
| 11 | `PlayerActivity` 内重置块 | `:2219-2228` ↔ `:2715-2724` | 10 行相同 | 抽 `resetState()` |
| 12 | `SettingsIndex.kt` 的 `Entry(...)` | 9 份同构（`:82-115`） | 数据驱动写法，**非缺陷** | 可不处理 |

> **注意**：跨文件**逐字**复制只有上面明确列出的这些。机械 CPD（≥20 行逐字块）全库只找到 8 簇——**这个项目的重复 90% 是「同一模式重写」而非复制粘贴**，标识符、字段名、分支顺序都不同，靠工具扫不出来，必须人工/语义合并。

### 5.2 结构性重复（同模式重写，需人工合并）

#### 5.2.1 `api/` 层

| 簇 | 数量 | 位置 |
|---|---|---|
| **视频卡片组装** | **19 处**（架构文档写「7 份」，严重低估） | `RankingApi:31`、`RecommendApi:44/69/93/115`、`WatchLaterApi:33`、`SearchApi:83/102`、`SeriesApi:113`、`FavoriteApi:155/221`、`UserInfoApi:157`、`HistoryApi:53`、`BangumiApi:34`、`MessageApi:114/219/307`、`DynamicApi:625`、`VideoInfo.java:109` |
| `ArticleCard` 组装 | 4 处 | — |
| `UserInfo` 组装 | 13 处 | — |
| 「观看」后缀拼接 | 9 处 | `SeriesApi:117` 漏了后缀 |
| `PlayerApi` 同一 URL 请求 3 次 | 3 处 | `getSubtitleLinks:469`/`getViewPoints:494`/`getInteractionGraphVersion:520` 都打 `/x/player/wbi/v2` |
| `accept_description/accept_quality` → qn 解析 | 3 处 | `PlayerApi` |
| 弹幕 XML 地址 | 3 处 | `PlayerApi:107/243/319` |
| 明文 `http://api.biliterminal.cn` | 4 处 | `AppInfoApi:137/156/180/203`（**全部明文 HTTP**） |
| `csrf` 读取 | 两套 | 字面量 `"csrf"` 32 处/8 文件 vs 常量 `SharedPreferencesUtil.csrf` 12 处/6 文件（值相同） |

🚧 **合并最大障碍：`VideoCard` 是「多语义垃圾桶」**——`bvid` 字段里放过 `season_id`（`SearchApi:114`）、`media_id`、被截断的 BV 号（`MessageApi:116`）；`aid` 里放过 `media_id`；`view` 有 4 种语义。**合并前必须先拆分这个模型**，否则只会把错误固化。

#### 5.2.2 `activity/` 层

| 簇 | 份数 | 说明 |
|---|---|---|
| **分页骨架** | **3 份** | `base/RefreshListActivity.kt`(212) / `base/RefreshMainActivity.kt`(171) / `base/RefreshListFragment.kt`(133)。`updateLoadMoreTip`、`showEmptyView`/`hideEmptyView`、`loadFail()` **逐字相同** |
| **视频卡片列表页** | **4 份** | `PopularActivity`(74-81) / `PreciousActivity`(74-81) / `RankingActivity`(69-76) 的 `runOnUiThread` 块 + 分页逻辑；`Popular` 与 `Precious` 近乎逐行相同，且**都不走基类** |
| **ViewPager 详情页骨架** | **5 份** | `VideoInfoActivity.kt:71-72` 与 `:101-102` **同文件内建了两次 adapter** |
| `onEvent(ReplyEvent)` 订阅 | 4 份 | `@Subscribe(ASYNC, sticky=true, priority=1)` |
| `getLayoutManager()` / `landscapeSpanCount()` | 3 份 | 第三份逻辑已落后 |
| 打开播放器（构造 `PlayerData` + `jumpToPlayer` + 进度处理） | **8 处** | — |

**基类各自的 bug（合并时不要「按多数实现」照抄）**：
- `RefreshListFragment.loadFail():111-121` **不复位任何状态**（它没有 `isLoading`）。
- `RefreshListFragment.goOnLoad():84-92` 直接 `listener!!.onLoad(page)`，**无空判**；对比 `RefreshListActivity.kt:183` 的 `val loadMore = listener ?: return`。
- 分层气味：`RefreshMainActivity.kt:116` 直接取兄弟基类的常量 `RefreshListActivity.EMPTY_TEXT_WITH_RETRY`。

#### 5.2.3 `adapter/` + `player/` + `service/` 层

| 簇 | 说明 |
|---|---|
| **`UserInfoActivity` 跳转写 9 遍** | 而 `BiliTerminal.jumpToUser` 只有 `RecentUpAdapter.kt:40` 1 处在用 |
| **头像/封面 Glide 参数抄 10 遍** | 而 `GlideUtil.requestRound` 只有 1 处在用 |
| `cell_user_list` 被 3 个 Holder 各解析一遍 | — |
| `CoinLogAdapter` ↔ `ExpLogAdapter` | 布局 MD5 相同，adapter 仅 6 行不同 |
| 两个 `FavoriteHolder` | 逐行相同 |
| `DynamicAdapter.kt:95-123` ↔ `UserDynamicAdapter.kt:69-98` | 胶水块逐行重复，只差偏移量 |
| **两代播放器实现** | `IjkPlayerBridge`(259) ↔ `VideoPlayerCore`(180)；默认 options 12 连（`:68-91` ↔ `:124-147`，含逐行相同的 UA 注释）、监听器（`:93-146` ↔ `:159-213`）、250ms 轮询（`:226-245` ↔ `:383-405`）。**且已经行为分叉**（`start()` 的 null 守卫、`onCompletion` 是否停轮询） |
| **`DownloadService` 内部** | `video_single`(841-907) ↔ `video_multi`(908-980) ≈**90% 相同**；两个 `startDownload`(398-447/449-498) ≈**95% 相同**；DB 样板重复 **7 份**（且 `close()` 被调 2 次）；伪进度节流循环 2 份；请求头拷贝 3 份；阶段进度收尾 3 份 |
| **时间格式化** | **11+ 处各自 `new SimpleDateFormat`**（模式 4 种、Locale 3 种）；`NoticeHolder.kt:166`、`TextClock.kt:26` 是**静态实例，非线程安全** |

### 5.3 布局重复

**事实上的复用协议是 id 约定**（`img_cover`/`text_title`/`text_upname`/`text_viewcount`），`VideoCardHolder` 正是靠它跨动态页/消息页/专栏页复用。改这些 id 会**同时打破 4 个包**（`PrivateMsgAdapter.kt:64-70`、`DynamicHolder`、`NoticeHolder`、`OpusContentAdapter`）。

复用良好的布局：`cell_video_list`（**9 个用户**，且被 `cell_private_msg.xml:38` include）、`cell_choose`（5）、`cell_episode`（3）、`cell_video_local`（3）、`cell_user_list`（3）。

近似重复可合并：

| 布局对 | 差异 |
|---|---|
| `cell_article_image`(34) ≈ `cell_dynamic_image`(32) | 只差 `paddingHorizontal`/`marginVertical` |
| `cell_article_list`(115) ≈ `cell_dynamic_article`(108) | 同构，只差根 id 与 marquee/icon |
| `cell_setting_input` ≈ `cell_interaction_debug_var` | 差 3 处 |
| `cell_coin_log` ≡ `cell_exp_log` | **完全相同（MD5）** |

> `cell_article_{heading,blockquote,code,hr}.xml` 随死类 `ArticleContentAdapter` 一起删；但 `cell_article_{head,end,textview,image,list}.xml` **仍被 `OpusContentAdapter` 使用，不可删**。

### 5.4 评估后**不建议合并**的（避免白干）

| 对 | 原因 |
|---|---|
| `QualityChooseAdapter` vs `QualitySelectorAdapter` | 且 `QualityChooseAdapter.Holder` 被 `ListChooseActivity.kt:66,83-88` 跨类复用，是隐藏耦合 |
| `MenuSettingAdapter` vs `MySpaceSettingAdapter` | 逐字相同 94/180 行、LCS 107，但 `AGENTS.md` 把「复用该写法」写成**刻意约定**，优先级最低 |
| `activity/player/ScaleGestureDetector.kt` vs 短视频用的 framework 版 | **同名不同源**，自定义版只被 `PlayerActivity` 用 |
| `UserInfoApi.decompressResponse` vs `NetWorkUtil.uncompress` | 算法不同（br+gzip vs 裸 deflate），**不是重复**；`uncompress` 是死代码 |
| 点赞/投币/收藏 | `activity/` 层**没有**跨页面重复（已收敛在 `VideoInfoFragment`）；重复实际在 adapter 层 |

---

## 6. 文档与代码不一致清单（8 处，动手前先改文档）

`AGENTS.md` 已警告「注释和文档都可能是错的」。本轮实测到的过时描述：

| # | 位置 | 文档写的 | 实测 |
|---|---|---|---|
| 1 | `architecture-map.md:308`、`:367` | 「44 个布局共用 `cell_topbar`」 | **0 处引用**，该改动已被回滚（`fix-progress.md:216`） |
| 2 | `architecture-map.md:329` | `RefreshMainActivity` 空视图「**无**」 | 已有完整的 `showEmptyView:91`/`hideEmptyView:100`/`setOnEmptyRetry:113` |
| 3 | `architecture-map.md` §9 坑 4 / `AGENTS.md` | 视频卡片解析重复「**7 份**」 | **19 处** |
| 4 | `architecture-map.md:469` | `AppInfoApi` 硬编码 HTTP 在 `120/139/163/186` | 实际 `137/156/180/203` |
| 5 | `architecture-map.md:250` | 测试「10 个文件 / 38 个测试」 | **11 个文件 / 59 个 `@Test`** |
| 6 | `architecture-map.md:21`、`AGENTS.md` | 「23 个空目录」 | **24 个** |
| 7 | `architecture-map.md:259` | `PlayerActivity.kt` 「3087 行」 | **3044 行**（`ShortVideoPlayerActivity` 907 → **1011**） |
| 8 | `AGENTS.md` 入口章节 | `BiliTerminal.java` | 扫描期间已迁移为 **`BiliTerminal.kt`**（已经同步） |

**另有一处文档与文档自相矛盾**：`architecture-map.md:271` 说 `VideoPlayerCore.kt` 是「已可用但尚无消费方」，同文件 `:275` 又说它属于正在进行的合并方案——**这两句合起来才是完整事实**（是死代码，但是有意为之的过渡态）。

---

## 7. 顺带发现的真实缺陷（不是死代码，但建议先修）

扫描过程中撞见的、**至今未修**的可见 bug：

| # | 位置 | 问题 |
|---|---|---|
| 1 | `AnnouncementsActivity.kt:27-30`、`NoticeActivity.kt:58-60` | 加载失败**不复位转圈** → 下拉刷新永久卡死（违反 `AGENTS.md` 硬约定） |
| 2 | `CollectionInfoActivity.kt:42-71` | 缺 `onFailure` 分支 |
| 3 | `PopularActivity.kt:103-105` | 不复位 `refreshing` |
| 4 | `SeriesInfoActivity.kt:35-37` | `seriesCover`/`seriesIntro`/`seriesTotal` **声明后从不赋值**：简介恒为「这里没有简介哦」、浏览量恒为「共」、封面恒为占位图、封面点击恒不触发。上一轮报告（`activity2-report.md` M-24）已指出，**至今未修** |
| 5 | `CollectionInfoActivity.kt:38-39` | 同类的「复制粘贴后忘了接线」 |
| 6 | `OpusInfoActivity.kt:89-95` | 空函数体的幽灵 `@Subscribe` + 空 `onDestroy`（漏了 `TerminalContext.leaveDetailPage()`，而 `DynamicInfoActivity:82-85`、`LiveInfoActivity:255-258` 都调了） |
| 7 | `DownloadService.start():501-506` | `if (started) return; started = true` **无锁**（`started` 是普通 `@JvmStatic var`），并发路径 `DownloadService:439/490`、`DownloadListActivity.kt:176/178`。另有 8 处已核实竞态（分片 `join(30000)` 超时后回退与残留线程同写一文件 `:1422-1435`、`batchStats` 非原子自增、两个共享 `NotificationCompat.Builder` 被多线程并发 `build()`、`onDestroy` 删整目录 `:1564-1577`） |
| 8 | `settings/TestActivity.kt:105` | 硬编码一个具体专栏 id `781871626480254985L`；`:177-179` POST `api.deepseek.com`；Manifest `:56-60` 写了 `exported="true"` 但入口是 Debug-only |
| 9 | `TutorialManagerActivity.kt:65,89-97` | 仍在读写新教程系统共用的 `tutorial_ver_*` 键，会污染新系统已读状态 |

> 第 1~3 条共 4 个文件，**改动风险极低，建议作为第 1 步独立提交**。

---

## 8. 风险红线（合并/删除时不要碰）

1. **弹幕的两条并发红线**（`architecture-map.md:277-284` 已记录真实事故）：
   - `DanmakuManager.createParser()` **只能在主线程且不可并发**（底层 `BiliDanmakuLoader` 是进程级单例，`dataSource` 是实例字段）。把它挪到后台线程**没有任何收益**（真解析是懒执行）。
   - `updateTimer` 的位置回调在「播放器未就绪/正在重建」时**必须返回负数**——26.09.10 删掉 `if (ijkPlayer != null && isPrepared)` 守卫，导致「普通视频弹幕间歇性消失」，已回退过一次。
2. **`cell_video_list` / `cell_dynamic_video` 的 id 是跨包事实协议**：改 id 会同时打破 `PrivateMsgAdapter`、`DynamicHolder`、`NoticeHolder`、`OpusContentAdapter`。
3. **`BaseActivity.kt:226` 用 `if (this !is InstanceActivity)` 做向下判断**——任何「让 `RefreshMainActivity` 继承 `RefreshListActivity`」的方案都会把顶栏行为从「打开菜单」变成「点击即返回」。
4. **`Parcelable` 与磁盘 JSON 模型**：删字段必须两端同步（`model/` 里的 `VideoFolder`/`VideoMeta`/`LocalVideo` 字段名即存储格式）。
5. **改 `res/` 的文件集合会触发 build cache 回放陈旧资源**：`clean` 与 `assembleDebug` **必须分两次 Gradle 调用**，并带 `--no-build-cache --no-configuration-cache`。
6. **`player/` 包不是公共层**：`VideoPlayerCore` 已是 `IjkPlayerBridge` 的功能超集，但**还不能直接替换**，三个阻塞点：(a) `PlayerState`/`IjkOption` 定义在 `IjkPlayerBridge.kt:17-36`，删旧类前必须先搬家；(b) `VideoPlayerCore.release()` 有一次性 `released` 标志（`:83`/`:230-232`），而 `PageHolder.onViewRecycled` 后可能复用 → 直接换会导致 **native 播放器泄漏**；(c) surface 挂载要从自建 `TextureView` listener 改成 `attachTextureView` + `prepare()`。
7. **改动前先决定 `VideoPlayerCore` 的去留**：留存（继续 S4 合并）还是删除。含糊着抽公共层会造出**第 3 套实现**。

---

## 9. 建议的执行顺序（6 批，低风险→高风险）

| 批次 | 内容 | 规模 | 风险 |
|---|---|---|---|
| **第 1 批** | 修 §7 的 4 处卡死 bug（`AnnouncementsActivity`/`NoticeActivity`/`CollectionInfoActivity`/`PopularActivity`） | 4 文件 | 极低，独立提交 |
| **第 2 批** | 合并 `handleQuickCache` 三份（MD5 已验证）→ 合并 `cell_coin_log`/`cell_exp_log` → 合并 `handleLoginSuccess` 两份 | 净删 ~250 行 | 低，收益最高 |
| **第 3 批** | 删 §3.1 的死文件（**先排除 `TutorialActivity`**）+ §3.2 死成员 + 43 条死 import + §3.5 空目录 | 23 文件 / ~3,000 行 | 低（R8 无 keep 规则），删 `res/` 时注意红线 5 |
| **第 4 批** | 删死依赖（Hilt/Retrofit/serialization/navigation/…），需先处理 `BiliTerminalApp.kt`（但它的 companion 5 个成员是活的，被 `SplashActivity.kt:127-142` 调用——**先迁移再删**） | `build.gradle` | 中 |
| **第 5 批** | 结构性合并：`MessageApi` 三个方法 → 视频卡片组装 19 处（**前置：拆分 `VideoCard` 多语义**）→ 用户跳转 9 处 / Glide 10 处 → 时间格式化统一 | 数百行 | 中高，需补单测 |
| **第 6 批** | 骨架级重构：分页骨架 3 份 → 详情页骨架 5 份 → `DownloadService` 内部（**前置：先修并发**）→ 播放器合并（**前置：先定 `VideoPlayerCore` 去留**） | 巨大 | 高，逐步真机验证 |

**每批完成后**：`./gradlew.bat :app:assembleDebug` + `:app:testDebugUnitTest`（当前 59 个测试全绿基线）+ 手表端真机手测。

---

## 附录：证据文件索引

| 文件 | 内容 |
|---|---|
| `.dsh/scan/api-model.md` | `api/` + `model/` 深扫（409 行） |
| `.dsh/scan/util-infra.md` | `util/`+`helper/`+`ui/`+`event/`+`listener/`+`tutorial/`+根包（含空目录、死依赖、未使用资源） |
| `.dsh/scan/activity.md` | `activity/` 全 22 子包 131 文件（1011 行，含 14 个重复簇、`PlayerActivity` 专项） |
| `.dsh/scan/adapter-player-service.md` | `adapter/`+`player/`+`service/`（含 4 份子报告于 `.dsh/scan/parts/`） |
| `.dsh/class-refs.csv` | 534 个类/接口/object 的跨文件引用计数（本文件 §3、§4 的机械底稿） |
| `.dsh/res-refs.csv` | 970 个资源的引用计数（§3.3 底稿） |
| `.dsh/file-inventory.csv` | 386 个源文件的精确行数/大小（§1 底稿） |
| `.dsh/scan-*.ps1` | 本轮全部扫描脚本（纯 ASCII，可复跑；含 `scan-dup.ps1` 重复代码检测器） |

> 复跑提示：`scan-refs.ps1`（引用计数）、`scan-res.ps1`（资源引用）、`scan-dup.ps1 -KeepIdentifiers -MinLines 20`（逐字重复块）、`scan-dead.ps1`（死代码分级）。

---

## 11. 死代码清理执行记录（26.09.11）

本轮**只做删除，不做重复代码合并**。共 6 批，每批都以 `:app:assembleDebug` + `:app:testDebugUnitTest`（59 用例）验证通过后进入下一批；因改动涉及源码文件集合与 `res/` 文件集合，每批验证前都先 `:app:clean`。

### 11.1 各批内容

| 批次 | 内容 | 验证 |
|---|---|---|
| B1 | 删 20 个 HEAD 干净的死文件（`WatchAdapter`/`Mp4FastStart`/`TimeUtil`/`ViewCache`/`ImageViewer`/`GeetestUtil`/`ArticleContentParser`/`MediaMerger`/`CustomListView`/`recycler` 三件套 + `WrapContentLinearLayoutManager`/`BilibiliIDConverter`/`Lyric`/`OpusCard`/`Playlist`/`FolderAdapter`/`PlayerIntegrator`）；顺带删 `DownloadService` 里对 `MediaMerger` 的无用 import | ✅ 59 用例 |
| B2 | 删 `ArticleContentAdapter.kt`(392) + `LocalVideoAdapter.kt`(329) + 其独占的 4 个 `cell_article_{heading,blockquote,code,hr}.xml` | ✅ |
| B3 | 删 31 个零引用资源文件（10 布局 / 18 drawable / 2 `res/color` / 1 `res/menu`） | ✅ |
| B4 | 删 11 条死依赖 + `logging-interceptor`，见 §1 死依赖清单 | ✅ |
| B5 | 移除 Hilt：摘 `@HiltAndroidApp`，删 hilt/ksp/serialization 三个插件与依赖（构建任务里 `hilt`/`ksp` 已归零） | ✅ |
| B6 | 删活文件里的死成员：`NetWorkUtil.{uncompress,readStream,setCookies(Cookies)}`、`DanmakuManager` 5 个方法、`PlayerSurfaceBinder.cancelAwait`、`DownloadService.{startReDownload,toastState}`、`PlayerScaleMode`、`PlayerActivity.danmakuSyncRunnable`、`ShortVideoPlayerActivity.bottomButtons`、`ModernLogger.kt` 整文件、5 条教程迁移残留 import | ✅ |
| B7 | 删 26 个空目录（含 `res/color`、`res/menu`、`res/navigation`） | ✅ |

### 11.2 实测体积（clean 构建）

| 指标 | 清理前 | 清理后 | 变化 |
|---|---|---|---|
| debug arm64 | 16.42 MB | **15.44 MB** | −0.98 MB（−6.0%） |
| release arm64 | 9.95 MB（含全部死依赖） | **9.73 MB** | −0.22 MB（−2.2%） |

**关键结论：死代码对 release 体积几乎无影响。** R8 本来就会 strip 未引用的类，所以删源码基本不减包；真正减下来的是死依赖（约 0.2 MB）。release APK 的体积构成是 **native `.so` 67.3%**（`libijkffmpeg.so` 5.16 MB 独占一半）、dex 17.9%、`res/` 8.2%、`resources.arsc` 5.0%——**体积优化的真正杠杆是重编精简版 `libijkffmpeg.so`（需 NDK r21e）**，不是删代码。

### 11.3 执行中发现、需要记住的坑

1. **顶层函数不在类扫描范围内**：B1 把 `util/ModernLogger.kt` 当"自封闭死簇"删掉，编译才发现它的顶层 `safeCallOrDefault` 被 `DanmakuManager` 调用。**判死必须同时扫顶层 `fun`/`val`，还要查通配符 `import ...util.*`。**
2. **`enum class X` 会被朴素正则误捕获为 `class`**：早期脚本因此漏登记枚举类、并产生一个名为 `class` 的假目标。
3. **零引用 ≠ 可删**：`res/raw/keep.xml`（`tools:keep="@xml/*"`，资源压缩器配置）、`helper/CustomGlideModule.kt`（`@GlideModule` 运行期反射发现，还给 Glide 注入带 Cookie 的 OkHttp）、`CaptchaWebViewActivity.onCaptchaResult`（JS 桥）、`@Subscribe` 方法、`IMediaPlayer` 回调、`Parcelable`/磁盘 JSON 字段——全部必须保留。详见 §4 假死清单。
4. **删文件会级联**：删 `DanmakuManager.loadFromProtobuf` 后，它唯一调用的 `safeCallOrDefault`（及 `ModernLogger.e`）也变成死代码，最终整个 `ModernLogger.kt` 可删。删完要重跑一次引用扫描。
5. **PS 5.1 的 `Get-Content` 按 ANSI 解码**：读 UTF-8 中文源码会丢行（`PlayerActivity.kt` 读成 3012、实为 3044），行号必须用 `[IO.File]::ReadAllLines($p, UTF8)` 或 ripgrep 复核；扫描脚本本身必须纯 ASCII（中文会让 PS 5.1 解析失败）。
6. **删依赖会让 Gradle 请求缓存里没有的旧版本**：移除 `androidx.navigation` 后，`slidingpanelayout` 从 1.2.0 掉回 `legacy-support-core-ui` 要求的 1.0.0，`--offline` 直接解析失败——改依赖的构建别带 `--offline`。

### 11.4 有意**没有**动的（留待决策）

| 项 | 原因 |
|---|---|
| `player/PlayerControlDelegate.kt`（297 行，含 `GestureHandler`） | 合并方案 S6 明确写「**决定其去留**」，且它带着 26.09.10 的手势 bug 修复。删它之前得先定方案 |
| 17 个 api 层零调用方法（`EmoteApi` 表情包管理 4 个、`DanmakuApi.likeDanmaku/recallDanmaku`、`VoteApi` 2 个等） | 已验证零调用，但 `EmoteApi` 那组是「表情包管理页」TODO 的 API 铺垫；且 R8 反正会 strip，收益接近零 |
| 旧教程死链（`TutorialActivity.kt` 78 行、`TutorialHelper` 的死方法、`model/Tutorial.java`、`model/CustomText.java`、11 个 `res/xml/tutorial_*.xml`、Manifest 注册） | **有前置条件**：`TutorialHelper.showPagerTutorial` 仍活（4 个调用点），必须先做 `docs/tutorial-system-redesign.md:90` 的 HINT 迁移 |
| `BiliTerminalApp.kt`（225 行，已摘掉 `@HiltAndroidApp`） | 类从不实例化，但 companion 的 5 个成员被 `SplashActivity.kt:127-142` 用于 UETool 悬浮窗引导——**先把这 5 个成员迁到 `BiliTerminal` 再删** |
| 205 个零引用 `color` / 25 个 `style` / 15 个 `dimen` / 19 个 `string` | 与主题系统耦合，#11.3 第 3 条的假死风险最高；而且总量只占 `resources.arsc` 的一小部分。要做请单独一轮并逐类核对 `?attr` |
| §5 的全部重复代码簇 | 本轮范围外（用户要求"仅死代码清理"） |
