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
| `ui/*`（ViewModel/MVVM） | 只有 `ui/appearance/`（26.09.11 新增，4 个文件：`AppearanceManager.kt` / `ColorScheme.kt` / `CornerStyle.kt` / `FontStyle.kt`）+ `ui/widget/`（12 个文件）。`ui/theme/` **已并入 `ui/appearance/`**（原 `ThemeManager.kt` → `ColorScheme.kt`）；其余 `ui/base`、`ui/player`、`ui/video/viewmodel` 等子目录**已全部删除** |
| `BiliTerminalApp.kt`（@HiltAndroidApp）为入口 | **死代码**。Manifest 指向 `.BiliTerminal`（26.09.10 起是 Kotlin，原先为 `.java`），该类从未被实例化 |

**核实方式**：全工程 `grep '@AndroidEntryPoint|@HiltViewModel|@Inject|@Module|@InstallIn'` → **0 命中**；空目录统计 → **24 个**（26.09.11 已全部删除）。

**结论**：全项目实际是**单层遗留架构**——Java 静态方法 + `org.json` 逐层拆 JSON + `startActivity` 直跳。没有 DI、没有 Repository、没有 ViewModel、没有 Retrofit 调用。

### 死依赖清单 —— **26.09.11 已全部删除**

原先 `app/build.gradle` 里有以下全工程无人使用的依赖，现已移除（连带 `ksp` 与 `kotlin.plugin.serialization` 两个插件、`BiliTerminalApp.kt` 上的 `@HiltAndroidApp`）：

```gradle
// 已删除：
implementation 'com.google.dagger:hilt-android:2.51.1'                      // 无任何注入点
ksp 'com.google.dagger:hilt-compiler:2.51.1'
implementation 'com.squareup.retrofit2:retrofit:2.11.0'                      // 无任何 Interface 声明
implementation 'com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0'
implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0'      // 无 @Serializable
implementation 'androidx.navigation:navigation-fragment-ktx:2.7.7'           // 0 引用
implementation 'androidx.navigation:navigation-ui-ktx:2.7.7'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2'            // 0 引用
implementation 'androidx.asynclayoutinflater:asynclayoutinflater:1.0.0'      // 项目有自实现 AsyncLayoutInflaterX
implementation 'com.google.protobuf:protobuf-javalite:3.21.12'               // ProtobufParser 是手写解析器
implementation 'com.geetest.sensebot:sensebot:4.3.5'                         // 验证码只走 WebView JS
implementation 'androidx.cardview:cardview:1.0.0'                            // 只用 MaterialCardView
debugImplementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
ksp { arg("room.schemaLocation", ...) }                                     // 项目用 SQLiteOpenHelper，无 Room
```

**改功能时的影响**：新代码直接加到 `api/` + `activity/`，沿用静态方法 + `org.json` 风格；`network/api/`、`di/`、`ui/*` 这些空壳目录已删除，别再往里加东西。

> **26.09.10 已清理**：`androidx.multidex:multidex:2.0.1` 依赖、`BiliTerminal.java` / `BiliTerminalApp.kt` 里的 `MultiDex.install(this)` 与对应的 `attachBaseContext` 覆写已删除——`minSdk 24` 下 ART 原生支持 multidex，`MultiDex.install()` 在 API 21+ 首行即返回，是纯死代码。（`multiDexEnabled true` 保留未动，minSdk ≥ 21 下本就无副作用。）

---

## 2. 入口与启动链路

```
AndroidManifest.xml:27  android:name=".BiliTerminal"   ← 真实 Application（Kotlin）
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

### 3.1b「我的」页面入口配置（26.09.10 新增）

`activity/user/MySpaceActivity.kt` 的功能入口不再硬编码，由 `util/MySpaceConfig.kt` 驱动：

- 两份有序 key 串 `myspace_main` / `myspace_more`（`;` 连接），互斥且并集 = `ALL_ITEMS`；任一非法（未知 key、重复、漏项、跨列表重复）整体回退默认并写回。
- 固定项不参与配置：用户卡片永远第一（设置页里不出现），`more`（更多按钮）与 `logout`（退出登录）永远最后两位；**更多列表为空时页面上不显示「更多」按钮**。
- 入口的图标 / 文案 / 跳转统一定义在 `activity/user/MySpaceMenu.kt`，主列表与更多页（`MySpaceMoreActivity`）共用，改跳转只改这一处。
- `creative`（创作中心）仍受通用偏好开关 `creative_enable` 控制：设置页里始终可见可排序，页面上按开关决定是否渲染。
- 设置入口 `activity/settings/SettingMySpaceActivity.kt`，适配器 `adapter/MySpaceSettingAdapter.kt` 单页双分区拖拽（两区都能排序、可互相拖入）。
- 加新入口时同步 `MySpaceConfig.ALL_ITEMS` 与 `MySpaceMenu.ITEMS` 并注册 Manifest；`MySpaceConfigTest.allItems_andMenuKeys_match` 会守卫两者一致。

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
| Application 级 Context / 工具 | `BiliTerminal.context`（**静态字段**，`@JvmField`，直接引用） | `BiliTerminal.kt:41` |
| 当前栈顶 Activity | `BiliTerminal.getInstanceActivityOnTop()`（`WeakReference`） | `BiliTerminal.kt:83` |
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
- **裸 deflate 解压**：`decompress(byte[])`（26.09.11 起为本工程唯一实现，原先
  `DownloadService`/`DownloadActivity`/`PlayerActivity` 各有一份逐字相同的副本）。
  解压失败回退原数据；`Inflater` 持 native 资源，统一在 `finally` 里 `end()`。
  注意与 `api/UserInfoApi.decompressResponse`（br + gzip）**不是同一件事**，不要互相替换。

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
- **测试覆盖极低**：`app/src/test/` 仅 13 个文件（12 个测试类 + 1 个共享假实现 `FakeSharedPreferences`），对 363 个源文件。已有：`HotSearchApiTest`、`FavoriteApiTest`、`OpusApiTest`、`PrivateMsgApiTest`、`NetWorkUtilTest`、`MenuConfigTest`、`MySpaceConfigTest`、`ToolsUtilTest`、`StringUtilTest`、`HotSearchAdapterTest`、`TutorialDslTest`、`ColorSchemeTest`。改动解析/配置/主题逻辑时补纯 JVM 单测——注入 `SharedPreferencesUtil.sharedPreferences`，用 `util/FakeSharedPreferences.kt`（26.09.11 从 `NetWorkUtilTest` 的私有内部类提取为共享助手，别再抄一份）。
- **主题色表带缓存，失效点只有一处**：`ColorScheme.getCurrentTheme()`（26.09.11 起）缓存当前色表，**只由 `AppearanceManager.setTheme()` 经 `ColorScheme.invalidateCache()` 置空**。这是刻意的——36 个属性 getter 全走它，而列表滚动时一个 item 要调多次，此前每次都重读 SharedPreferences（热路径重复 IO）。**若将来给主题 key 增加第二个写入路径（比如直接 `SharedPreferencesUtil.putString(SettingsKeys.THEME, …)`），必须同步调用 `ColorScheme.invalidateCache()`，否则改主题后色表不跟着变且在 `onResume` 重建后依然错**。守卫测试：`ColorSchemeTest.themeCache_isInvalidatedOnEverySetTheme`、`colorGetters_doNotTouchSharedPreferencesAfterFirstRead`。
- **主题体系有 3 个"裸 Activity"不参与**：`SplashActivity`、`GetIntentActivity` 不继承 `BaseActivity`（开屏/外链恒定 B站粉），`PlayerActivity` 自己 `setTheme` 但**不调 `applyWindowTheme`、也不参与 `onResume` 主题检测**。改主题相关行为时别以为全局都生效了。
- **文案硬编码**：遗留页面标题/Toast 直接写中文字符串（Manifest 里 `android:label` 也是中文），只有设置页用 `desc_*` 资源。改文案按现有风格来，别顺手抽 `strings.xml`。

### 7.4 两个视频播放器是两套独立实现（改播放前必读）

工程里**没有统一的播放层**，两个播放器各写各的，共用代码只有 `player/IjkPlayerBridge.kt` 这一小块：

| | 普通视频播放器 | 短视频播放器 |
|---|---|---|
| 入口 | `activity/player/PlayerActivity.kt`（**3087 行**） | `activity/video/ShortVideoPlayerActivity.kt`（907 行） |
| 基类 | **直接 `extends Activity`**，不走 `BaseActivity` 体系（自己实现主题、EventBus、横竖屏） | `InstanceActivity` + `ViewPager2` 竖滑翻页 |
| 播放内核 | **裸 `IjkMediaPlayer`**，`setOption`/`setDataSource`/监听器全部内联在 Activity 里 | `player/IjkPlayerBridge`（Flow 驱动状态）+ `player/DanmakuManager` |
| 弹幕 | `IDanmakuView` 内联处理（`streamDanmaku`/`createParser` 都在 Activity 里） | `DanmakuManager` |
| 轮询 | 3 个 `java.util.Timer` 线程（在线人数 5s / 倍速收起 / surface 就绪）+ 2 个主线程 `Handler` 自循环（播放进度 250ms、缓冲速度 500ms） | `IjkPlayerBridge` 内协程，250ms 更新 StateFlow |
| 预加载 | 无 | `util/VideoPreloadManager`（**只预取播放地址 URL，不预热播放器**） |

`player/` 包里**有一半是死代码**（全工程 0 引用，已核实）：

- `IjkPlayerBridge.kt` — 在用，被短视频的 `PageHolder` 直接使用。
- `DanmakuManager.kt` — 在用，**两个播放器都已接入**（26.09.10 起 `PlayerActivity` 的内联弹幕栈已删除，统一走它）。
- `PlayerSurfaceBinder.kt`（26.09.10 新增）— 在用，Surface 就绪事件回调，取代 200ms 轮询 Timer。
- `VideoPlayerCore.kt`（26.09.10 新增）— **已可用但尚无消费方**，是合并两个播放器的目标内核（多实例安全、不持 Activity、`reload()` 复用播放器）。
- `PlayerIntegrator.kt` — ~~死代码~~ **26.09.11 已删除**（全工程无一处实例化）。
- `PlayerControlDelegate.kt`（含 `GestureHandler`）— **仍未删除，待决策**：`PlayerIntegrator`（它唯一的引用方）已删，此后它全库零外部引用；但播放器合并方案 S6 明确把它列为"**决定去留**"（`docs/superpowers/specs/2026-09-10-player-core-merge.md:63`），且它带着 26.09.10 的手势 bug 修复（`isLongPressing` 复位），**删之前先定方案**。
- `PlayerScaleMode` 枚举（原在 `IjkPlayerBridge.kt`）— **26.09.11 已删除**（无人使用）。
- `DanmakuManager.loadFromProtobuf` / `toggleVisibility` / `setSpeedFactor` / `setTextSizeScale` / `setTransparency` — **26.09.11 已删除**（全库零调用；`loadFromProtobuf` 实现还是坏的：解压后从未把数据交给 parser）。连带 `PlayerSurfaceBinder.cancelAwait`、`DanmakuManager` 的 `Inflater`/`ByteArrayInputStream` import 一并删除。

**改播放相关代码时的判断顺序**：先确认要改的是"普通播放器那套"还是"短视频那套"。合并工作正在进行（见 `docs/superpowers/specs/2026-09-10-player-core-merge.md`：S1/S2/S3 已完成，S4/S5/S6 待做），所以两边文件的归属会逐步收敛；**当前 `player/` 包仍不是公共层** —— `PlayerActivity` 只用了 `DanmakuManager` 与 `PlayerSurfaceBinder`，播放器本身还是裸 `IjkMediaPlayer`。

**弹幕序列化的两个硬约束（踩过坑，别重犯）**：

1. **`DanmakuManager.createParser()` 只能在主线程、且不可并发**。`DanmakuLoaderFactory.create(TAG_BILI)` 返回的是**进程级单例** `BiliDanmakuLoader.instance()`（`BiliDanmakuLoader.java:27-38`），而 `dataSource` 是它的**实例字段**（`:29`），`load()` 写字段 + `loader.dataSource` 读字段是一段 check-then-act。并发时两个 holder 会拿到同一个数据源，表现为弹幕串台或解析为空。
2. **`createParser()` 不做任何 XML 解析**，把它挪到后台线程**没有任何收益**。`AndroidFileSource(InputStream)`（`AndroidFileSource.java:44-46`）、`BiliDanmakuLoader.load()`（`:44-46`）、`BaseDanmakuParser.load()`（`BaseDanmakuParser.java:67-70`）全都只是存引用；真解析在 `getDanmakus()` → `parse()`（`:81-89`）**懒执行**，全工程唯一调用点是 `DrawTask.java:283`，跑在 `DanmakuView` 自己的渲染线程上。所以"短视频滑动卡顿 = 弹幕解析阻塞主线程"是**错误归因**（26.09.10 已核实并回退过一次这样的改动）。

普通播放器与短视频的弹幕栈是两套（前者内联，后者走 `DanmakuManager`），统一它们是合并两个播放器时**风险最低、收益最高的第一步**，但要先解决上面第 1 条的并发约束。

3. **`DanmakuManager` 的位置回调在"播放器未就绪/正在重建"时必须返回负数**。`updateTimer` 跑在 `DanmakuView` 的渲染线程上，与主线程重建 `IjkMediaPlayer` 的动作并发；`IjkMediaPlayer` 的 native 层不是线程安全的，窗口期读到的脏位置一旦灌进 `DanmakuTimer`，**整批弹幕会被判定为"已过期"而一条都不显示**，且因为是竞态所以**间歇性**出现（26.09.10 真实踩过：合并弹幕栈时删掉了原 `if (ijkPlayer != null && isPrepared)` 守卫，导致"普通视频弹幕间歇性消失"）。对应的两个 `isPrepared` 字段也因此加了 `@Volatile`。

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

`BaseActivity` 提供：主题应用（7 套主题，色表经 `ColorScheme.getCurrentTheme()` 缓存，仅由 `AppearanceManager.setTheme` 失效）、横竖屏、DPI/边距、**系统栏 insets 避让（`applySystemBarInsets`，含刘海）**、`getLayoutManager()`（横屏按「每列 ≥220dp」返回 `CustomGridManager`，下限 2 列）、`asyncInflate`（先显 loading 布局再替换）、`onBackPressed` 受 `back_disable` 开关、EventBus 自动注册/注销 + sticky `SnackEvent` 重放、主题变更 `onResume` 自动 `recreate()`、重写 `isDestroyed()`。

`InstanceActivity` 额外：`onCreate` 里 `BiliTerminal.setInstance(this)`；**顶栏点击不自动绑定**，须手动 `setMenuClick()`。

**顶栏曾是公共组件，但那次收敛被回滚了**：`res/layout/cell_topbar.xml` 目前**全库 0 处引用**（26.09.11 实测：149 个布局里只有 7 个含 `<include>`，且都不含 `cell_topbar`），实际是各页手抄顶栏。历史经过见 `docs/review/fix-progress.md:216`——「44 个布局换成 `<include>`」那一轮在真机实测顶栏吃满整屏后**已整体还原**。所以本段旧描述（"44 个布局共用"）**与现状相反**，要重做收敛请先读 `docs/visual-experience-report.md` 的方案再动手。

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

**注意**：`ui/widget/recycler/` 下的 `AbstractAdapter` / `BaseAdapter` / `BaseHolder` 三件套**全工程没有任何子类**，已于 **26.09.11 删除**（连同 `WrapContentLinearLayoutManager`）。现役适配器一律 `extends RecyclerView.Adapter<XxxHolder>()` + Holder 直接继承 `RecyclerView.ViewHolder`。该目录现在只剩 `CustomLinearManager` / `CustomGridManager` 两个 LayoutManager。

**照抄对象**：`adapter/video/VideoCardAdapter.kt` + `adapter/video/VideoCardHolder.kt` 这一对（header/footer 需要时自己写，别找现成基类）。

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

### 8.4b 重复项合并后的公共落点（26.09.11 新增，别再手抄）

做「重复代码合并」时把 6 类重复收敛成了下列单一落点。**要写这几件事时直接用它们，不要重新手抄一份**；
合并的来龙去脉与逐项证据见 `docs/review/cleanup-progress.md`。

| 落点 | 取代了 | 说明 |
|---|---|---|
| `adapter/video/VideoQuickCache.handle(context, videoCard)` | 3 份逐字节相同的 `handleQuickCache` | 视频卡列表长按的「快速缓存」，按 `cache_default_quality` 分支 |
| `adapter/LogListAdapter<T>` + `res/layout/cell_log.xml` | 2 份流水 adapter + 2 份 MD5 相同的布局 | 经验 / 硬币变化记录；delta 文案由调用方以 `Row` 映射传入（两者文案不同，刻意未统一） |
| `util/NetWorkUtil.decompress(byte[])` | 3 份 `Inflater(true)` | CDN 裸 deflate 响应；`api/UserInfoApi.decompressResponse` 是 br+gzip，**算法不同不可合并** |
| `util/TimeUtil` | 11 处 `new SimpleDateFormat` | 时间格式化统一入口，ThreadLocal 缓存；`PATTERN_DATE_TIME_12H` 是历史遗留的 12 小时制，勿顺手改 `HH` |
| `BiliTerminal.jumpToUser(context, mid)` | 16 处手抄 Intent | 所有「跳用户主页」；等价于 `Intent().setClass(UserInfoActivity).putExtra("mid", mid)` |
| `ui/widget/RotaryEncoderSupport` | 3 份表冠滚动 + 1 处开关读取 | 三个 `Rotary*` 控件的公共逻辑；控件各自保留事件接入方式（监听器 vs `dispatchGenericMotionEvent`） |

> `Rotary*` 三件套的差异是**刻意保留**的：`RecyclerView`/`ScrollView` 走
> `setOnGenericMotionListener` 且滚动后抢焦点，`NestedScrollView` 走 `dispatchGenericMotionEvent`
> 覆写且**不**抢焦点。helper 用 `requestFocus` 参数区分，改它等于改手表手感。

### 8.5 这一层额外的坑

1. `InstanceActivity` 不自动绑顶栏 → 子类忘调 `setMenuClick()` 则顶栏点击无反应。
2. `CenterThreadPool.observe(future, consumer)` **空 catch 静默吞异常**（`CenterThreadPool.java:147-148`）。
3. `RefreshMainActivity.kt:43` 把 layoutManager 强转 `LinearLayoutManager`——换 StaggeredGrid 会 CCE（横屏给的 `CustomGridManager` 是 GridLayoutManager 子类，安全）。
4. 主题/密度变化触发 `recreate()`，子类 `onResume` 的一次性逻辑会重跑。
5. 覆写 `eventBusEnabled()` 返回 false 会连 sticky Snackbar 一起失效。
6. `RefreshListFragment` 的 `setRefreshing` 只切 UI，不复位状态；也没有 `hideEmptyView`。
7. `setAdapter/setRefreshing/showEmptyView` 内部已切主线程，但直接 `recyclerView.adapter =` / `notifyItemRangeInserted` 必须自己回主线程。
8. **改 `Guideline.setGuidelinePercent` 不会自动重新布局**：`Guideline.onMeasure` 恒 `setMeasuredDimension(0,0)`，自身尺寸不随 percent 变化，父级若是 `wrap_content` 的 ConstraintLayout 就不会重算，必须手动 `requestLayout()`。登录页二维码缩放（`QRLoginFragment.kt`）踩过这个坑；另外宽度变化要带动高度得靠 `app:layout_constraintDimensionRatio`。
9. **`wrap_content` 的 RelativeLayout 里不能放 `layout_alignParentBottom` 的子元素**：只要有一个"贴底"子元素，RelativeLayout 的 `wrap_content` 就会被撑成父容器高度。公共顶栏 `cell_topbar.xml` 第一版就是这么写的（1dp 分割线贴底），结果**顶栏直接吃满整屏、列表被顶到屏幕外**（真机实测 `top` bounds = `[0,114][1080,2394]`）。现在顶栏根节点是竖向 LinearLayout，内层 RelativeLayout 只放标题/时钟（`BaseActivity.setRound()` 需要 RelativeLayout.LayoutParams）。
10. **`<include>` 建议显式写 `android:layout_width/layout_height`**：不写时行为依赖被包含布局根节点的参数，排查困难。另外注意：`cell_topbar.xml` 目前**已无任何 include 引用**（那次 44 处替换被整体回滚，见 8.1 节），未来若重做收敛再照本条办。

---

### 8.6 新增一个设置项的完整链路

设置项 key 有**两套定义处**，新增时统一加在 `util/SettingsKeys.kt`（`SharedPreferencesUtil` 里那 38 个常量是历史遗留，不要重复声明）：

1. `util/SettingsKeys.kt` 加 `const val XXX = "xxx"`。
2. 对应设置页加条目：`activity/settings/SettingGroupActivity.kt`（字符串驱动，按 `desc_*` 惯例加资源；这是**唯一**该动 `strings.xml` 的地方）。
3. `activity/settings/SettingsIndex.kt` 的 `build()` 加一条 `Entry(name, desc) { ... }`——否则全局设置搜索找不到这一项。

**放哪个分组**：界面尺寸类进 `buildUIGroup()`（`group_type = "ui"`）；外观类（配色/圆角/字体）进
`buildAppearanceGroup()`（`GROUP_APPEARANCE = "appearance"`，26.09.11 新增的独立一屏，
由 `SettingGroupActivity` 的 `group_type` 分发，非独立 Activity）。若新设置需要自己的页面，
看 `AGENTS.md`「新增设置子页面有两种形态」——只是列表项就别新建 Activity。

读写统一走 `SharedPreferencesUtil.getXxx(key, default)` / `putXxx(key, value)`。
**外观类设置例外**：写入必须走 `AppearanceManager.setXxx()`，它负责递增外观版本号（见 §8.7）。

---

### 8.7 外观三模块：配色 / 卡片圆角 / 字体（26.09.11 起）

**位置**：`ui/appearance/`。三个模块**完全独立**（各自一个 key、互不干涉），
由一个门面统一收口读写与「外观已变更」通知。

| 文件 | 角色 | key | 档位 |
|---|---|---|---|
| `AppearanceManager.kt` | 门面：快照 + 版本号 + **唯一写入入口** | `appearance_version` | — |
| `ColorScheme.kt` | 配色：7 套主题（**只读模块**，原 `ui/theme/ThemeManager.kt`） | `theme_selector` | 7 套 |
| `CornerStyle.kt` | 卡片圆角 | `ui_corner_radius` | `square`（默认）/ `rounded` |
| `FontStyle.kt` | 自定义字体（用户从文件管理器选字体文件） | `ui_font_path` | 有 / 无（默认无） |
| `CustomFont.kt` | 把自定义字体套到视图上 | — | — |

**分层约定（别打破）**
- **模块**（`CornerStyle`/`FontStyle`）只放：候选值常量、显示名、纯函数（规整、档位→数值）、读取。
  **不放写入**——写入一律走门面，这样「递增版本号」不会漏。
- **门面**只做「汇总快照 + 唯一写入 + 版本号」，**绝不做几何计算**（手表性能优先）。

**版本号机制**：`AppearanceManager.version()` 是一个存在 SharedPreferences 的 Int，
任何外观写入都 +1。Activity 只需记住自己创建时的版本号、`onResume` 比一次，
就知道要不要重建——**不会随模块增加而增加比较项**（新增第 4 个模块不需要改 `BaseActivity`）。
> 现状：`BaseActivity` 仍在比它自己的 `appliedTheme` 字符串，**尚未接入版本号**；
> 接入随「圆角模块落地」一起做。

**两条不可破坏的性能约定**
1. **未启用自定义字体 = 渲染路径零开销**：`FontStyle.typeface()` 返回 null 时
   `CustomFont.applyToContentView` 立即 return，一次视图树遍历都不做。这是绝大多数用户的状态。
   守卫测试：`FontStyleTest.shouldLoad_isFalseWhenNoFontConfigured`。
2. 圆角**只决定「用哪个主题属性取值」**，不做运行时几何计算，也不在 `RecyclerView` 绑定路径上做额外工作。

### 8.7.1 圆角的生效机制（26.09.11 落地，改圆角前必读）

**为什么不能直接用 dimen**：`dimen` 是编译期固定的，用户设置在运行时改不了它；
`shape drawable` 也读不到主题属性。**只有主题属性（`?attr/`）能被 `theme.applyStyle()` 覆盖**，
所以圆角走的是主题属性这条唯一可行的路：

1. `res/values/styles.xml` 声明 `<attr name="appCornerRadius" format="dimension"/>`，
   并定义两个覆盖样式 `Appearance_CornerSquare` / `Appearance_CornerRounded`。
2. 所有 `CardStyle*`/`ButtonStyle*`（`styles.xml` + `themes.xml` 共 7 套）的
   `cardCornerRadius`/`cornerRadius` 一律引用 `?attr/appCornerRadius`——
   原先 10 处硬编码 `12dp` 已全部消除。
3. 每套主题都自带一条 `<item name="appCornerRadius">@dimen/card_round</item>` 作为**兜底**，
   这样不走 `BaseActivity` 的裸 Activity（`SplashActivity`/`GetIntentActivity`）与
   `Theme.NoSwipe.AppCompat` 类界面拿到的是「方角」而不是解析失败的 0dp。
4. `BaseActivity.onCreate` 在 `setTheme(...)` 之后、任何视图 inflate **之前**，
   执行 `this.theme.applyStyle(CornerStyle.overlayStyleResId(), true)`。
   **这是圆角模块唯一的运行时成本，且是 O(1)，无任何视图遍历。**

> `force = true` 是必需的：`appCornerRadius` 已在主题里定义过，不加 force 覆盖不生效。

**档位取值**：`card_round`（方角：手表 6dp / 宽屏 10dp）、`card_round_large`
（圆角：手表 12dp / 宽屏 16dp）。真源是 `dimens.xml` + `values-w300dp/dimens.xml`。

**已知未覆盖（圆角模块的遗留项）**
- **10 个 shape drawable 仍直接用 `@dimen/card_round`**，因此不跟随档位
  （`background_card`、`background_card_borderless`、`background_edittext`×3、
  `background_grey_cardview`、`background_privatemsg_send`、`background_searchbar`、
  `background_searchhistory`）。`shape` 的 `<corners android:radius>` 读不到主题属性。
  修法有两条，**都必须先真机确认**：① 把这几处改成 `ShapeableImageView`/`MaterialCardView`
  等能吃主题属性的控件；② 在 `setContentView` 之后做**一次**遍历改写 `GradientDrawable` 半径，
  且**仅在档位 ≠ 主题兜底值时才执行**。当前选择：先不做，避免在热路径引入特判。
- **layout 级内联圆角**保持原值，不跟随档位：`cell_up_avatar`(28dp，圆形头像)、
  `activity_vote_info` 的两个按钮(18dp)、`cell_follow_group`/`cell_log`/`cell_login_record`(8dp)、
  `item_account`(12dp)。属「尺寸派生圆角」，按设计豁免。

**「方角」的语义（已对上游实测核实）**：上游 BiliClient（gitee `develop`，HEAD `f2b1aca`）
全项目唯一圆角是 `@dimen/card_round` = **6dp**，经主题 `materialCardViewStyle` 全局下发；
**上游没有 0dp 直角外观，也没有任何圆角设置项**。所以 `square` 档的值是 `card_round`
（手表 6dp / 宽屏 `values-w300dp` 10dp），语义是「还原原项目」，不是「做成直角」。
本项目偏离上游之处是给 6 套主题硬编码了 12dp，那是 `rounded` 档。

**默认档位的观感影响（修正早先「零变化」的说法）**
默认 `square`，对**默认主题「经典终端」是零变化**（它本来就走 `@dimen/card_round`）；
但**另外 6 套主题的卡片圆角会从 12dp 变为 6dp**——那 12dp 是主题化改造时复制 `CardStyle`
引入的漂移（5 套主题各抄了一份 12dp），不是刻意的设计取值，本次借模块化收敛回原项目取值。

### 8.7.2 自定义字体的生效机制（26.09.11 落地）

用户在设置 →「界面与外观」→「外观设置」→「自定义字体」里，用文件管理器挑一个
TTF/OTF/TTC，应用把它**拷进私有目录**（`filesDir/custom_font/custom_font.ttf`）并全局应用。

**为什么是「拷贝」而不是记住 URI**
1. `minSdk 24`，`Typeface.Builder(FileDescriptor)` 要 API 26 用不了；安全可用的只有
   `Typeface.createFromFile(File)`，它需要一个**真实路径**。
2. 用户随时可能删除/移动源文件；记 URI 还得处理 `takePersistableUriPermission`。拷一份最稳。

**为什么不用 `LayoutInflater.Factory2`（更漂亮的做法）**
`LayoutInflater.setFactory2()` 只在**从未设过** factory 时可用，否则抛 `IllegalStateException`。
而 `BaseActivity : AppCompatActivity`，AppCompat 已在 `super.onCreate()` 里装好 factory
（还带着 Material 的控件替换，`MaterialButton`/`MaterialTextView` 靠它，**圆角模块也依赖它**）。
用自己的 factory 顶掉它会让 `<Button>` 退回普通 Button、圆角失效——比字体问题严重得多。
公开 API 没有干净办法把两个 factory 串起来，所以走遍历。

**实际机制**
- `BaseActivity.onContentChanged()`（`setContentView` 之后必被触发，同时覆盖普通布局与
  `asyncInflate` 的替换布局）→ `CustomFont.applyToContentView(this)`。
- 遍历静态视图树，只对 `TextView` 调 `setTypeface`，用 `!==` 跳过已套好的。
- 列表项由 `RecyclerView` 复用、绑定发生在遍历之后，故对遍历中遇到的每个 `RecyclerView`
  挂 `OnChildAttachStateChangeListener`，只处理**新挂上来的** item 视图。
- **保留粗体/斜体**：自定义字体按 NORMAL 解析，直接 `setTypeface` 会抹掉 `android:textStyle="bold"`，
  所以按原样式派生（`Typeface.create(base, style)`，按样式缓存）。

**性能代价（手表优先，必须说清楚）**：未启用时零开销；启用后每次 `onContentChanged` 跑一次遍历。
这套代价是**用户主动开启**换来的，不是所有人付。

**已知边界**
- **不走 `BaseActivity` 的界面不生效**：`SplashActivity`、`GetIntentActivity`、`PlayerActivity`
  （三者都不继承 `BaseActivity`）。开屏无正文、外链页极简，播放器以视频为主。
- **未被遍历到的、遍历之后才动态创建的 TextView 不生效**（非 `RecyclerView` 的晚建视图）。
- 只改字体外观，**不改字号**。原计划的「字号 4 档 + 字族 2 选」（token 收敛 + `scaleFactor`）
  已按需求变更取消：`ui_font_scale` / `ui_font_family` 两个 key 随之删除。

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
| `BilibiliIDConverter` | av/bv 互转（纯函数） | ~~`bvtoaid`、`aidtobv`~~ **26.09.11 已删除**（全工程 0 调用） |

### 网络出口的 3 个例外

40 个类里只有 3 处**绕过** `NetWorkUtil` 自建 Request，因此不受它的解压/重试/Cookie 管理保护：

1. `ReplyApi.java:190-208` `uploadReplyImage`（multipart，不处理 br/gzip）
2. `UserInfoApi.java:291-303` `updateUserInfo`（自带 `decompressResponse`）
3. `UserInfoApi.java:330-348` `uploadAvatar`

三者均手工拼 Cookie 头。改这几个方法时不要假设 NetWorkUtil 的行为。

### 纯函数可测点

已抽成 static/object、只依赖 `org.json`、可直接 JVM 单测：
`HotSearchApi.parseHotSearch`、`FavoriteApi.parseFavoriteState`/`buildMediaId`、`OpusApi.analyzeCommentInfo`/`analyzeParagraphs`、`VoteApi.parseVoteInfo`、`EmoteApi.analyzeEmotePackages`、`LiveApi.analyzeLiveRooms`、`BangumiApi.analyzeSection`/`analyzeEpisode`、`VideoInfoApi.analyzeTags`/`analyzeUgcSeason`/`getInfoByJson`、`ReplyApi.analyzeReplyArray`、`SeriesApi.getSeriesByJson`、`DynamicApi.parseAtContent`、`ConfInfoApi.getWBIMixinKey`/`sortUrlParams`、`CookiesApi.hmacSha256`。（原列表里的 `BilibiliIDConverter.bvtoaid`/`aidtobv` 已于 26.09.11 随死代码删除。）

**不可单测**（内部发网络 / 依赖 Context / 弹 UI）：`DynamicApi.analyzeDynamic`、`MessageApi` 全部解析（SpannableString）、`PrivateMsgApi.getPrivateMsgList`、`LikeCoinFavApi.getVideoStats`。

**测试覆盖现状**：`app/src/test/` 15 个测试类 / 107 用例，api 层只有 3 个类的 3 个解析函数被覆盖（`HotSearchApiTest`、`FavoriteApiTest`、`OpusApiTest`）；`ui/appearance/` 下有 4 个测试类共 48 个用例——`ColorSchemeTest`（14）覆盖 7 套主题的 `key → style` / `key → 色表` / 中文显示名映射、无 key 时的默认值、以及色表缓存的失效与读取次数；`CornerStyleTest`（10）覆盖圆角两档与「档位 → 覆盖样式」映射；`FontStyleTest`（13）覆盖字体文件头校验（含 WOFF 专门拒绝）与「未配置不加载」的性能约定；`AppearanceManagerTest`（11）覆盖外观版本号与唯一写入入口。

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
