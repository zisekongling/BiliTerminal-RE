# ReBiliClient 架构通读（为改功能准备）

> 通读日期：当前 `main` 分支工作区（`versionName 26.09.08`）
> 目的：改功能前先摸清**真实**架构。本文结论均基于逐文件读源码核实；`AGENTS.md` 已按本文事实重写。
> 配套阅读：`docs/review/00-summary.md`（286 条问题清单）、`docs/review/fix-progress.md`（修复进度）、`docs/tutorial-system-redesign.md`（教程系统重做，进行中）

---

## 1. 一句话结论：项目没有"新层"

旧版 `AGENTS.md` 曾声称存在两条并存架构（遗留 Java 层 + 新 Kotlin 层）。**实测第二条完全不存在**，这条幻觉已从 `AGENTS.md` 中删除，事实记录如下：

| 旧 AGENTS.md 声称 | 实际情况（已核实） |
|---|---|
| `network/api/`（Retrofit + kotlinx-serialization） | 目录**空**，0 个文件 |
| `di/`（Hilt） | 目录**空**，0 个文件 |
| `data/repository/` | 目录**空**，0 个文件 |
| `ui/*`（ViewModel/MVVM） | 只有 `ui/theme/`（3 个文件）+ `ui/widget/`（16 个文件），其余 `ui/base`、`ui/player`、`ui/video/viewmodel` 等 **13 个子目录全空** |
| `BiliTerminalApp.kt`（@HiltAndroidApp）为入口 | **死代码**。Manifest 指向 `.BiliTerminal`（Java），该类从未被实例化 |

**核实方式**：全工程 `grep '@AndroidEntryPoint|@HiltViewModel|@Inject|@Module|@InstallIn'` → **0 命中**；空目录统计 → **23 个**。

**结论**：全项目实际是**单层遗留架构**——Java 静态方法 + `org.json` 逐层拆 JSON + `startActivity` 直跳。没有 DI、没有 Repository、没有 ViewModel、没有 Retrofit 调用。

### 死依赖清单（在 `app/build.gradle` 里但全工程无人使用）

```gradle
implementation 'com.google.dagger:hilt-android:2.51.1'      // 无任何注入点
ksp 'com.google.dagger:hilt-compiler:2.51.1'
implementation 'com.squareup.retrofit2:retrofit:2.11.0'      // 无任何 Interface 声明
implementation 'com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0'
implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0'  // 无 @Serializable
ksp { arg("room.schemaLocation", ...) }                      // 项目用 SQLiteOpenHelper，无 Room
```

**改功能时的影响**：新代码直接加到 `api/` + `activity/`，沿用静态方法 + `org.json` 风格；`network/api/`、`di/` 只是空壳，往里加 Retrofit 接口或 Hilt 模块等于新建一座孤岛。

---

## 2. 入口与启动链路

```
AndroidManifest.xml:27  android:name=".BiliTerminal"   ← 真实 Application（Java）
        ↓
BiliTerminal.onCreate()
   ├─ SharedPreferencesUtil.sharedPreferences = getSharedPreferences("default")
   ├─ context = getFitDisplayContext(this)      ← DPI 缩放包装 Context
   ├─ PerformanceManager.init(this)             ← 设备分级
   ├─ 强制更新拦截：注册 ActivityLifecycleCallbacks，
   │    onActivityPreCreated 里把任何 Activity 换成 UpdateActivity 并 finish
   ├─ ErrorCatch.init / Logu 开关
   ├─ 后台异步：动态更新数、消息未读数（仅已登录 mid != 0）
   └─ checkAppUpdate()                          ← 读远端 config.json
        ↓
SplashActivity（LAUNCHER，typewriter 动画）
   ├─ Debug 包先要悬浮窗权限（UETool）
   └─ proceedSplashFlow()
        ├─ 未完成初始设置 → SetupUIActivity
        ├─ 已完成 → SharedPreferencesUtil.loadMenuEnabled().firstOrNull()
        │            → MenuActivity.btnNames[key].second  ← 用"第一个启用的菜单项"当首屏
        └─ 异步：App token / Cookie 刷新、CookiesApi.checkCookies、AppInfoApi.check
```

**改功能注意**：
- `BiliTerminal.onCreate()` 里所有初始化都包在 `if (context == null)` 内——**多进程/重复创建时只会跑一次**。新增全局初始化要放在这个块里，否则可能被跳过。
- 首屏由**用户自定义菜单顺序**决定，不是硬编码 `RecommendActivity`。改导航时注意 `loadMenuEnabled()`。
- `onActivityPreCreated` 的强制更新拦截会 **finish 掉任意 Activity**，调试时若被"莫名其妙踢到更新页"，检查 `force_update_required` 这个 SharedPreferences 键。

---

## 3. 导航体系

### 3.1 菜单表（唯一导航注册点）

`activity/MenuActivity.kt:45-59` 的 `btnNames` 是 `LinkedHashMap<String, Pair<标题, Class>>`，共 **14 个入口**：

| key | 标题 | Activity | key | 标题 | Activity |
|---|---|---|---|---|---|
| `recommend` | 推荐 | RecommendActivity | `dynamic` | 动态 | DynamicActivity |
| `short_video` | 短视频 | ShortVideoPlayerActivity | `myspace` | 我的 | MySpaceActivity |
| `popular` | 热门 | PopularActivity | `message` | 消息 | MessageActivity |
| `precious` | 入站必刷 | PreciousActivity | `local` | 缓存 | LocalListActivity |
| `ranking` | 全站排行榜 | RankingActivity | `settings` | 设置 | SettingMainActivity |
| `hotsearch` | 热搜 | HotSearchActivity | `search` | 搜索 | SearchActivity |
| `live` | 直播 | RecommendLiveActivity | `timeline` | 时间线 | TimelineActivity |

`MenuActivity` 是 `launchMode="singleTask"` 的独立页；用户在设置里排序/启用哪些项存在 SharedPreferences（`SharedPreferencesUtil.loadMenuEnabled()`）。

**新增一级页面必须改三处**（漏一处就失效）：

1. `MenuActivity.btnNames` 加一行（id → 标题 + Activity Class）。
2. `util/MenuConfig.kt:14` 的 `ALL_ITEMS` 加同一个 id —— 这是 `menu_enabled` 配置串的**单一数据源**，`MenuConfig.parse()` 遇到未知 key 会直接返回 `null`，导致整份用户菜单配置被判非法并回退默认。决定默认是否显示还要看 `FIXED_ITEMS` / `SWITCHABLE_KEYS` / `SWITCH_DEFAULTS`。
3. `AndroidManifest.xml` 注册 Activity。

> `MenuConfig` 是纯 Kotlin 对象、无 Android 依赖，已有 `app/src/test/.../MenuConfigTest.kt`——改菜单逻辑时**优先在这里加测试**。

### 3.2 三个基类层级

```
AppCompatActivity
  └─ BaseActivity                ← 主题应用、方向、DPI/边距、EventBus 注册、onKeyDown(MENU→finish)
       ├─ InstanceActivity       ← 一级页：MENU 键/顶栏 → 打开 MenuActivity；BiliTerminal.setInstance(this)
       │    └─ RefreshMainActivity  ← 一级页 + 下拉刷新 + 上拉加载（自带菜单按钮）
       └─ RefreshListActivity    ← 二级页 + 下拉刷新 + 上拉加载（顶栏点击 = finish）
```

**选哪个基类**：

| 场景 | 继承 |
|---|---|
| 一级页面（菜单能直达） | `InstanceActivity` 或 `RefreshMainActivity` |
| 二级页面（列表 + 分页） | `RefreshListActivity` |
| 二级页面（普通） | `BaseActivity` |

**两级页面的区别在于顶栏点击行为**：`BaseActivity.onStart()` 里 `if (this !is InstanceActivity) setTopbarExit()`——即非一级页点顶栏就 `finish()`；一级页由 `InstanceActivity.setMenuClick()` 改成打开菜单。

### 3.3 导航坑（两条均已在 26.09.08 修复）

- **MENU 键双重触发**：`InstanceActivity.onKeyDown` 打开 MenuActivity 后原本继续走 `super`，被 `BaseActivity.onKeyDown` 又 `finish()` 当前页。现在前者消费按键并 `return true`。
- **`from` 参数传不出去**：`InstanceActivity.menuClick` 原本判的是刚 new 出来的空 Intent（恒 false），现改为读 `getIntent().getStringExtra("from")`。

> 新增菜单跳转时仍要注意：`MenuActivity.btnNames`、`MenuConfig.ALL_ITEMS`、Manifest 三处必须同步（见 3.1）。

---

## 4. 依赖获取方式（没有 DI）

| 依赖 | 获取方式 | 位置 |
|---|---|---|
| Application 级 Context / 工具 | `BiliTerminal.context`（**静态字段**，直接引用） | `BiliTerminal.java:39` |
| 当前栈顶 Activity | `BiliTerminal.getInstanceActivityOnTop()`（`WeakReference`） | `BiliTerminal.java:226` |
| 内容缓存 / 数据源 | `TerminalContext.getInstance()`（`InstanceHolder` 懒汉单例） | `TerminalContext.java:371` |
| 网络客户端 | `NetWorkUtil.getOkHttpInstance()`（`AtomicReference` 双检） | `NetWorkUtil.java:85` |
| 设置读写 | `SharedPreferencesUtil` 静态方法（`sharedPreferences` 静态字段） | `util/SharedPreferencesUtil.java` |
| 跨页事件 | **EventBus**（greenrobot），`SnackEvent` 为 sticky | `BaseActivity.kt:198,233` |
| 线程/协程 | `CenterThreadPool` 静态方法 | `util/CenterThreadPool.java` |

**这意味着**：任何新增功能都可以在任何地方 `BiliTerminal.context` / `TerminalContext.getInstance()` 直接取到全局对象，**不需要也无法用注入**。代价是全局可变状态遍地，测试困难。

---

## 5. 数据流模式

项目里只有**两种**数据流写法，没有第三种：

### 5.1 命令式（绝大多数页面）

```kotlin
CenterThreadPool.run {                       // 后台线程
    try {
        val data = XxxApi.getSomething(id)   // 同步阻塞网络请求
        runOnUiThread { adapter.setData(data) }
    } catch (e: Exception) {
        report(e)                            // BaseActivity.report → MsgUtil.err
    }
}
```

### 5.2 LiveData 式（详情页）

`TerminalContext` 提供 `getVideoInfoByAidOrBvId()` / `getArticleInfoByCvId()` / `getDynamicById()` / `getOpusById()` / `getLiveInfoByRoomId()` / `getReply()`，内部：

```
LruCache<String, Object>(10) 命中？ → MutableLiveData(Result.success(缓存对象))
                    ↓ miss
CenterThreadPool.supplyAsyncWithLiveData { fetch...().getOrThrow() }
```

- 缓存键格式：`ContentType.getTypeCode() + "_" + id`（如 `video_12345`）。
- `Result` 是项目自己实现的类（`util/Result.java`），不是 Kotlin 的。
- 缓存容量仅 10 条，且**不区分 aid/bvid**——见第 7 节坑点。

### 5.3 线程约定

`CenterThreadPool`（`util/CenterThreadPool.java`）：
- `run(Runnable)` → 实际走 `Dispatchers.IO` 协程（`Build.VERSION.SDK_INT < 17` 的原生线程池分支在 minSdk 24 下**永不执行**，是死代码）。
- `supplyAsyncWithLiveData(Callable<T>)` → `LiveData<Result<T>>`，失败自动 `MsgUtil.err(e)`。
- `observe(Future<T>, Consumer<T>)` → 自动切主线程。
- `runOnUiThread(Runnable)` → `Handler(Looper.getMainLooper()).post`。

**改功能注意**：API 层全部是**同步阻塞**方法，调用方负责丢到后台线程。若在主线程直接调 `XxxApi.getXxx()`，会触发 `NetworkOnMainThreadException` 或卡顿。

---

## 6. 网络层

### 6.1 `NetWorkUtil`（`util/NetWorkUtil.java`，605 行，唯一出口）

- **单例 OkHttpClient**，`followRedirects(false)` + 自定义拦截器手写重定向（`b23.tv` 短链走 `RedirectHandler` 回调）。
- **DNS 强制 IPv4**（`Inet4Selector`，注释称 IPv6 请求有异常）。
- **Cookie 管理**：内存缓存 `cachedCookies` + `webHeaders` 静态 `ArrayList`（**索引 1 存 Cookie 字符串**）。`putCookie`/`setCookies` 有 `synchronized`，但 `saveCookiesFromResponse`（拦截器里任意线程调用）**没有锁**——并发 Set-Cookie 可能互相覆盖。
- **风控重试**：`executeJsonWithRiskRetry` 对 `code == -352 / -412` 重试；`executeWithDoctypeRetry` 对返回 `<!doctype`（被风控拦成 HTML）的响应重试。重试次数/间隔读 SharedPreferences（`api_retry_max_times`、`api_retry_interval_seconds`）。
- **隐私模式**：`getJsonPrivacy()` 用 `buildGuestCookieString()` 剔除 `SESSDATA/bili_jct/DedeUserID/sid` 等登录 Cookie。
- **参数构造**：`FormData` 类，内部 `URLEncoder.encode`，默认**不**自动加 `access_key`（注释：web 接口带 access_key 会触发风控）。

### 6.2 WBI 签名（`api/ConfInfoApi.java`）

- `signWBI(url_query)`：取 `nav` 接口的 `wbi_img` → `img_key + sub_key` → 经 `MIXIN_KEY_ENC_TAB` 重排取前 32 位得 `mixin_key` → 参数排序 + `&wts=` + `mixin_key` 做 MD5 得 `w_rid`。
- `mixin_key` **每天只刷新一次**（`getDateCurr()` 写 SharedPreferences 的 `last_wbi`）。
- 结果缓存用 3 个 `volatile` 字段（`lastWbiQuery/lastWbiWts/lastWbiSignedUrl`），**非原子**——极端并发下可能读到"query 已换、签名未换"的组合。

### 6.3 API 层组织

`api/` 目录下约 40 个类，每个对应一个 B 站功能域，**全部**通过 `NetWorkUtil` 发请求、用 `org.json` 手工拆包。没有 Retrofit、没有数据类映射（除 `model/` 下少量 POJO）。

---

## 7. 改功能前必须知道的坑（按"会不会踩到"排序）

### 7.1 本轮已修复（26.09.08，已验证，别再当 bug 修一遍）

| # | 位置 | 原问题 | 修法 |
|---|---|---|---|
| 1 | `api/PrivateMsgApi.java` | `!has && isNull` 恒等于 `!has`，`account_info` 为 null 的普通会话被误过滤 | 改用 `isNull("account_info")`；解析抽成纯函数 `parseSessionsList()` 并补单测 |
| 2 | `activity/base/InstanceActivity.kt` | MENU 键打开菜单后继续走 `super`，被 `BaseActivity.onKeyDown` 又 `finish()` 当前页 | 消费按键，`return true` |
| 3 | `activity/base/InstanceActivity.kt` | `from` 参数判的是刚 new 的空 Intent，恒 false | 改判 `getIntent().getStringExtra("from")` |
| 4 | `util/NetWorkUtil.java` | `saveCookiesFromResponse` 无锁，与 `putCookie`/`setCookies` 的 `synchronized` 不对称 | 抽出 `saveCookiesLocked()`，纳入同一把 `NetWorkUtil.class` 锁 |
| 5 | `api/ConfInfoApi.java` | WBI 缓存三个 `volatile` 分开赋值，可能读到"query 已换、签名未换" | 合并为不可变 `WbiCache` 对象，整体替换 |
| 6 | `util/MsgUtil.java` | sticky `SnackEvent` 显示后不移除 → 注册时收一次、`onResume` 再取一次，同页弹两次 | `processSnackEvent` 显示分支同时 `removeStickyEvent` |
| 7 | `util/TerminalContext.java` | 视频缓存 aid/bvid 键不一致，bvid 路径**永远 miss** | 新增 `cacheVideo()` 双写两个键；`LruCache` 10→20 保持等效容量 |

**验证**：`:app:testDebugUnitTest` 38 个测试全绿（新增 `PrivateMsgApiTest` 6 例）+ `:app:assembleDebug` 通过。
顺带修正 `app/src/test/.../ToolsUtilTest.kt` 的期望值笔误（`0x00123456` 剥离 alpha 应为 `0x123456`，原写成 `0x000000`）。

### 7.2 更早的修复（26.08.27 快照之后，别照旧报告去改）

- `RefreshListActivity.kt:112-118`：`setRefreshing(false)` 时复位 `isLoading`（注释说明了原因）。
- `RefreshMainActivity.kt:74-88`：`goOnLoad()` 同步置成员 `isRefreshing`，`onScrolled` 用它防重入。
- `ToolsUtil.java:71-73`：`getRgb888` 已改为 `color & 0xFFFFFF`（注释保留旧实现说明）。
- `NetWorkUtil.java:100-113`：重定向前判空 scheme/host、先 `response.close()`。
- `PlayerActivity` / `UpdateManager` / `LocalListActivity` / `VideoInfoFragment` 等见 `fix-progress.md` 第二节。

**建议**：改任何文件前先 `grep` 一下对应代码是否还是旧报告里描述的样子——旧报告基于 `26.08.27` 快照，之后有 3 轮修复。

### 7.3 结构性风险（改功能时容易放大）

- **巨型类**：`activity/player/PlayerActivity.kt` 127 KB、`service/DownloadService.kt` 65 KB、`activity/video/ShortVideoPlayerActivity.kt` 35 KB。改播放/下载相关功能前先想清楚在哪个位置插入。
- **两套 Application 静态状态并存**：`BiliTerminal.context/instance`（活的）与 `BiliTerminalApp.context/appInstance`（死的）。**新代码一律用 `BiliTerminal`**，别碰 `BiliTerminalApp`（除 `SplashActivity` 里 UETool 那几行历史遗留）。
- **测试覆盖极低**：`app/src/test/` 仅 9 个文件（`HotSearchApiTest`、`FavoriteApiTest`、`OpusApiTest`、`PrivateMsgApiTest`、`NetWorkUtilTest`、`MenuConfigTest`、`ToolsUtilTest`、`StringUtilTest`、`HotSearchAdapterTest`）对 363 个源文件。改动解析逻辑时补纯 JVM 单测（参考 `NetWorkUtilTest` 的 FakeSharedPreferences 手法）。
- **文案硬编码**：遗留页面标题/Toast 直接写中文字符串（Manifest 里 `android:label` 也是中文），只有设置页用 `desc_*` 资源。改文案按现有风格来，别顺手抽 `strings.xml`。

---

## 8. UI 基建速查（新增页面临摹用）

### 8.1 完整继承体系

```
AppCompatActivity
  └─ BaseActivity
       ├─ InstanceActivity              ← 一级页（菜单键/顶栏 → MenuActivity）
       │    └─ RefreshMainActivity      ← 一级页 + 分页（布局 activity_simple_main_refresh）
       └─ RefreshListActivity           ← 二级页 + 分页 + 空视图（布局 activity_simple_refresh）

Fragment → BaseFragment → RefreshListFragment   ← Fragment 版列表页
```

`BaseActivity` 提供：主题应用（6 套主题）、横竖屏、DPI/边距、`getLayoutManager()`（横屏返回 3 列 `CustomGridManager`）、`asyncInflate`（先显 loading 布局再替换）、`onBackPressed` 受 `back_disable` 开关、EventBus 自动注册/注销 + sticky `SnackEvent` 重放、主题变更 `onResume` 自动 `recreate()`、重写 `isDestroyed()`。

`InstanceActivity` 额外：`onCreate` 里 `BiliTerminal.setInstance(this)`；**顶栏点击不自动绑定**，须手动 `setMenuClick()`。

### 8.2 新列表页模板（必须做这 4 步）

`super.onCreate()` 之后：

```kotlin
setPageName("标题")                          // 1. 顶栏标题
setMenuClick()                               // 2. 仅 RefreshMainActivity 需要
setOnRefreshListener { load(1) }             // 3. 不注册则 swipeRefresh 永远转圈（基类 onCreate 里是 isEnabled=false, isRefreshing=true）
setOnLoadMoreListener { page -> load(page) } // 4. page 已由基类自增，别再 ++
```

加载完成**必须**调 `setRefreshing(false)`——它是 `isLoading`/`isRefreshing` 的唯一复位信号（只改 `swipeRefreshLayout.isRefreshing` 不算）。失败走 `loadFail(e)`（会 `page--` 并复位）。到底置 `bottom = true`。

**选型**：菜单入口页 → `RefreshMainActivity`；返回式页面且要空视图 → `RefreshListActivity`。

| | RefreshListActivity | RefreshMainActivity |
|---|---|---|
| 父类/顶栏 | BaseActivity，返回式（自动绑） | InstanceActivity，菜单式（须 `setMenuClick`） |
| 防重入字段 | private `isLoading` | protected `isRefreshing` |
| 并发保护 | 仅 500ms 时间戳 | `synchronized` + 100ms |
| 触发阈值 | `findLastVisibleItemPosition >= itemCount-4`，IDLE 也查 | 完全可见项 `>= itemCount-3` 且 `!canScrollVertically(1)` |
| 空视图 | 有 `showEmptyView/hideEmptyView` | **无**（布局里有 `emptyTip` 但无人管理） |
| 性能 | `PerformanceManager` 动态缓存 + 独立 RecycledViewPool | 固定 cacheSize 10、pool max 20 |

### 8.3 Adapter 写法

**注意**：`ui/widget/recycler/` 下的 `AbstractAdapter` / `BaseAdapter` / `BaseHolder` 三件套**全工程没有任何子类**（grep 只命中定义处）。现役适配器一律 `extends RecyclerView.Adapter<XxxHolder>()` + Holder 直接继承 `RecyclerView.ViewHolder`。

**照抄对象**：`adapter/video/VideoCardAdapter.kt` + `adapter/video/VideoCardHolder.kt` 这一对。需要 header/footer 时才用 `BaseAdapter`（它管 dataList 和 header 偏移，但 `removeItem` 与 `updateItem` 的 position 语义不一致——前者含 header、后者不含）。

### 8.4 现成交互工具

`MsgUtil`（任意线程可调）：
- `showMsg(str)` / `showMsgLong(str)` → 走 EventBus sticky `SnackEvent`；`toast/toastLong` 为降级 Toast。
- `err(Throwable)` 按异常类型自动分流文案（IOException→网络错误、JSONException→带详情、IndexOutOfBounds→Adapter 错误、SQLException→SQL）。
- `showText(title, content)` → `ShowTextActivity`；`showDialog(title, content[, wait])` → `DialogActivity`。

对话框 Activity（Intent extra 传参 + `registerForActivityResult`）：

| Activity | 入参 | 返回 |
|---|---|---|
| `DialogActivity` | title、content、wait_time | 无（只能点按钮） |
| `ConfirmDialogActivity` | title、content | RESULT_OK / RESULT_CANCELED |
| `ListDialogActivity` | title、`items`(StringArrayList) | `selected_position` |
| `InputDialogActivity` | title、initial_text、hint | `input_text`（已 trim，不校验空） |

### 8.5 这一层额外的坑

1. `InstanceActivity` 不自动绑顶栏 → 子类忘调 `setMenuClick()` 则顶栏点击无反应。
2. `CenterThreadPool.observe(future, consumer)` **空 catch 静默吞异常**（`CenterThreadPool.java:147-148`）。
3. `RefreshMainActivity.kt:43` 把 layoutManager 强转 `LinearLayoutManager`——换 StaggeredGrid 会 CCE（横屏给的 `CustomGridManager` 是 GridLayoutManager 子类，安全）。
4. 主题/密度变化触发 `recreate()`，子类 `onResume` 的一次性逻辑会重跑。
5. 覆写 `eventBusEnabled()` 返回 false 会连 sticky Snackbar 一起失效。
6. `RefreshListFragment` 的 `setRefreshing` 只切 UI，不复位状态；也没有 `hideEmptyView`。
7. `setAdapter/setRefreshing/showEmptyView` 内部已切主线程，但直接 `recyclerView.adapter =` / `notifyItemRangeInserted` 必须自己回主线程。
8. **改 `Guideline.setGuidelinePercent` 不会自动重新布局**：`Guideline.onMeasure` 恒 `setMeasuredDimension(0,0)`，自身尺寸不随 percent 变化，父级若是 `wrap_content` 的 ConstraintLayout 就不会重算，必须手动 `requestLayout()`。登录页二维码缩放（`QRLoginFragment.kt`）踩过这个坑；另外宽度变化要带动高度得靠 `app:layout_constraintDimensionRatio`。

---

### 8.6 新增一个设置项的完整链路

设置项 key 有**两套定义处**，新增时统一加在 `util/SettingsKeys.kt`（`SharedPreferencesUtil` 里那 38 个常量是历史遗留，不要重复声明）：

1. `util/SettingsKeys.kt` 加 `const val XXX = "xxx"`。
2. 对应设置页加条目：`activity/settings/SettingGroupActivity.kt`（字符串驱动，按 `desc_*` 惯例加资源；这是**唯一**该动 `strings.xml` 的地方）。
3. `activity/settings/SettingsIndex.kt` 的 `build()` 加一条 `Entry(name, desc) { ... }`——否则全局设置搜索找不到这一项。

读写统一走 `SharedPreferencesUtil.getXxx(key, default)` / `putXxx(key, value)`。

---

## 9. API 层映射表（40 个类，改功能时定位用）

按功能域分组。`api/` 下 38 个 Java + 2 个 Kotlin（`HotSearchApi.kt`、`ShortVideoFeedApi.kt`）。

### 视频与播放

| 类 | 职责 | 关键方法 |
|---|---|---|
| `VideoInfoApi` | 视频详情/tag/在看/AI 总结 | `getVideoInfo(String)/(long)`、`getTags`、`getInfoByJson`、`getWatching`、`getVideoConclusion` |
| `PlayerApi` | 播放地址/字幕/下载/跳外部播放器 | `getVideoDash`、`getVideo`、`getBangumi`、`getSubtitleLinks`、`jumpToPlayer` |
| `DanmakuApi` | 弹幕收发 | `sendVideoDanmakuByAid/ByBvid`、`getVideoDanmakuSegment`、`getAllVideoDanmaku` |
| `InteractionVideoApi` | 互动视频分支 | `getEdgeInfo` |
| `RecommendApi` | 推荐/热门/入站必刷/相关 | `getRecommend`、`getPopular`、`getPrecious`、`getRelated` |
| `RankingApi` | 排行榜 | `getRanking` |
| `HotSearchApi.kt` | 热搜 | `getHotSearch`、`parseHotSearch` |
| `SearchApi` | 搜索/建议/默认内容 | `search`、`searchType`、`getSearchSuggestions`、`getDefaultSearchContent` |
| `HistoryApi` | 历史记录 | `getHistory`、`reportHistory`、`deleteHistory` |
| `WatchLaterApi` | 稍后再看 | `getWatchLaterList`、`add`、`delete` |
| `ShortVideoFeedApi.kt` | 短视频 Feed | `fetchFeedPage`、`fetchVideoUrl` |

### 动态、专栏与番剧

| 类 | 职责 | 关键方法 |
|---|---|---|
| `DynamicApi` | 动态列表/详情/发布/转发/点赞/@ | `getDynamicList`、`getDynamic`、`publishComplex`、`relayDynamic`、`analyzeDynamic` |
| `OpusApi` | 图文动态与专栏正文（HTML 抓取） | `getOpus`、`likeOpus`、`analyzeCommentInfo`、`analyzeParagraphs` |
| `ArticleApi` | 专栏 cv | `getArticle`、`like`、`addCoin`、`opusId2cvid` |
| `VoteApi` | 投票 | `createVote`、`doVote`、`getVoteInfo`、`parseVoteInfo` |
| `SeriesApi` | 合集/系列 | `getUserSeries`、`getSeriesInfo`、`getSeriesByJson` |
| `BangumiApi` | 番剧/追番 | `getFollowingList`、`getBangumi`、`getSections`、`getMdidFromEpid` |
| `TimelineApi` | 番剧时间表 | `getTimeline` |

### 用户与社交

| 类 | 职责 | 关键方法 |
|---|---|---|
| `UserInfoApi` | 用户/空间/关系/资料 | `getUserInfo`、`getUserSpaceInfo`、`followUser`、`updateUserInfo`、`uploadAvatar` |
| `FollowApi` | 关注/粉丝/分组 | `getFollowingList`、`getFollowerList`、`getFollowTags` |
| `ReplyApi` | 评论 | `getReplies`、`getRootReply`、`sendReply`、`likeReply`、`uploadReplyImage` |
| `PrivateMsgApi` | 私信 | `getPrivateMsg`、`getSessionsList`、`sendMsg` |
| `MessageApi` | 消息中心/未读/消息设置 | `getUnread`、`checkMessageUnread`、`getLikeMsg/getReplyMsg/getAtMsg`、`getSystemMsg` |
| `EmoteApi` | 表情包 | `getEmotes`、`getMyPackages`、`setPackage`、`analyzeEmotePackages` |
| `VipApi` | 大会员 | `getVipInfo`、`addExperience` |
| `CreativeCenterApi` | 创作中心 | `getVideoStat`、`getBeUPTime` |
| `ExpLogApi` / `CoinLogApi` / `ElectricApi` / `LoginRecordApi` | 经验/硬币/充电/登录记录流水 | 各自一个 `getXxx()` |

### 收藏与直播

| 类 | 职责 | 关键方法 |
|---|---|---|
| `FavoriteApi` | 收藏夹 | `getFavoriteFolders`、`getFolderVideosNew`、`addFavorite`、`parseFavoriteState` |
| `LikeCoinFavApi` | 视频三连/点赞/投币 | `triple`、`like`、`coin`、`favorite`、`getVideoStats` |
| `LiveApi` | 直播 | `getRecommend`、`getFollowed`、`getRoomInfo`、`getRoomPlayInfo`、`analyzeLiveRooms` |

### 登录、鉴权与基建

| 类 | 职责 | 关键方法 |
|---|---|---|
| `LoginApi` | 登录（扫码/TV/密码/短信/SSO） | `getLoginQR`、`getLoginState`、`passwordLogin`、`smsLogin` |
| `CookiesApi` | Cookie/buvid/ticket | `checkCookies`、`genWebHeaders`、`getWebBuvids`、`genBiliTicket` |
| `CookieRefreshApi` | Web Cookie 刷新 | `cookieInfo`、`getCorrespondPath`、`refreshCookie` |
| `AppTokenRefreshApi` | access_token 续期 | `refreshAppToken` |
| `ConfInfoApi` | **WBI 签名**（全 api 层依赖） | `signWBI`、`getWBIMixinKey`、`sortUrlParams` |
| `AppInfoApi` | 公告/崩溃上报/赞助/检查更新 | `check`、`getAnnouncementList`、`uploadStack`、`getSponsors` |
| `BilibiliIDConverter` | av/bv 互转（纯函数） | `bvtoaid`、`aidtobv`（**全工程 0 调用，死代码**） |

### 网络出口的 3 个例外

40 个类里只有 3 处**绕过** `NetWorkUtil` 自建 Request，因此不受它的解压/重试/Cookie 管理保护：

1. `ReplyApi.java:190-208` `uploadReplyImage`（multipart，不处理 br/gzip）
2. `UserInfoApi.java:291-303` `updateUserInfo`（自带 `decompressResponse`）
3. `UserInfoApi.java:330-348` `uploadAvatar`

三者均手工拼 Cookie 头。改这几个方法时不要假设 NetWorkUtil 的行为。

### 纯函数可测点

已抽成 static/object、只依赖 `org.json`、可直接 JVM 单测：
`HotSearchApi.parseHotSearch`、`FavoriteApi.parseFavoriteState`/`buildMediaId`、`OpusApi.analyzeCommentInfo`/`analyzeParagraphs`、`VoteApi.parseVoteInfo`、`EmoteApi.analyzeEmotePackages`、`LiveApi.analyzeLiveRooms`、`BangumiApi.analyzeSection`/`analyzeEpisode`、`VideoInfoApi.analyzeTags`/`analyzeUgcSeason`/`getInfoByJson`、`ReplyApi.analyzeReplyArray`、`SeriesApi.getSeriesByJson`、`DynamicApi.parseAtContent`、`ConfInfoApi.getWBIMixinKey`/`sortUrlParams`、`BilibiliIDConverter.bvtoaid`/`aidtobv`、`CookiesApi.hmacSha256`。

**不可单测**（内部发网络 / 依赖 Context / 弹 UI）：`DynamicApi.analyzeDynamic`、`MessageApi` 全部解析（SpannableString）、`PrivateMsgApi.getPrivateMsgList`、`LikeCoinFavApi.getVideoStats`。

**测试覆盖现状**：`app/src/test/` 8 个文件，api 层只有 3 个类的 3 个解析函数被覆盖（`HotSearchApiTest`、`FavoriteApiTest`、`OpusApiTest`）。

### API 层的坑

1. **解析里发网络**：`DynamicApi.java:482` 在 `analyzeDynamic` 内调 `BangumiApi.getMdidFromEpid`；`PrivateMsgApi.java:54,70` 在解析里调 `UserInfoApi` → 无法纯测 + N+1 请求。
2. **解析依赖 UI/Context**：`DynamicApi.java:685`（`BiliTerminal.context`）、`MessageApi.java:128/145/232/320`（SpannableString）、`LikeCoinFavApi.java:71`（弹窗）、`AppInfoApi.java:33-85`（网络 + SharedPreferences + 弹窗混在一起）。
3. **api 类做 UI 跳转/下载**：`PlayerApi.java:44-51`（startActivity）、`53-99`（DownloadService）、`339-415`（拼 Intent）。
4. **重复实现**：视频卡片解析 **7 份**（`RankingApi:30-39`、`RecommendApi:69-76/92-100/114-122`、`WatchLaterApi:33-42`、`SearchApi:83-101`、`SeriesApi:113-120`、`FavoriteApi:155-171`、`UserInfoApi:157-168`）；`ReplyApi.sendReply` 两份；`DanmakuApi` 发送两份；`VideoInfoApi.getVideoInfo` 两份；解压逻辑 `NetWorkUtil` 已有一份、`UserInfoApi.java:354` 又写一份。**改一处记得 grep 其余几处。**
5. **参数写错**：`PlayerApi.java:301` `.put("fnvar",0)`（应为 `fnver`）；`ReplyApi.java:245` `likeReply` 硬编码 `type=1`（动态/专栏评论点赞会失败）；`DanmakuApi.java:133,144` `segment_index` 从 1 开始（注释却说从 0）。
6. **硬编码**：`AppInfoApi.java:120,139,163,186` 明文 `http://api.biliterminal.cn`；弹幕 XML 地址重复 3 处（`PlayerApi:107,243,319`）；URL 散落在方法体内，无常量表。
7. **全局可变状态**：`SearchApi.java:24-25` 的 `static seid/search_keyword`（多入口搜索会串）、`ConfInfoApi.java:41-43` 的 WBI 缓存、`LoginApi.java:29-30`。
8. **SharedPreferences key 混用**：字面量 `"csrf"`（`HistoryApi:32,84`、`WatchLaterApi:49,60`、`DanmakuApi:33`）与常量 `SharedPreferencesUtil.csrf`（`EmoteApi:50`）并存。

---

## 10. 动手前的检查清单

1. 确认要改的类属于哪一层：`activity/`（UI）→ `api/`（网络+解析）→ `util/`（基建）→ `model/`（数据）。
2. 网络请求**必须**在 `CenterThreadPool.run {}` 里调，API 方法是阻塞的。
3. 新增页面：选基类（第 3.2 节）→ 注册 Manifest → 如需菜单入口再改 `MenuActivity.btnNames` + `loadMenuEnabled` 默认值。
4. 新增 API：加在 `api/` 下对应类里，用 `NetWorkUtil.getJson/post` + `FormData`，纯解析部分抽成 `static` 函数以便单测。
5. 改动后跑 `./gradlew.bat :app:assembleDebug`（无 CI，编译通过 + 真机手测为准）；解析逻辑改动补 `app/src/test/` 单测。
6. 改完顺手看一眼 `docs/review/fix-progress.md` 的待办，如果修掉了其中的条目就更新它。
