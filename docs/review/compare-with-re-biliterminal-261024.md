# 与 Re-BiliTerminal（cyq114514）全面对比报告

- 报告日期：2026-10-24
- 我方基线：`17212e3c73c52e5250a6c118625b658458c23a5c`（main，2026-10-24 11:33:46 +0800，版本 26.10.24）
- 对比基线：[`cyq114514/Re-BiliTerminal`](https://github.com/cyq114514/Re-BiliTerminal.git) `c6705d84eca3f17b7701269e9237863a0aff4ee9`（main，2026-10-24 12:48:55 +0800，版本 1.2.3fix；本文完成于其 1.2.4 发布前，1.2.4 的增量复核见第 15 章）
- 扫描范围：两个仓库的全部源码、资源、清单、构建脚本、CI 与文档；我方 436 个主代码文件，对方 354 个
- 结论：**我方在工程规范、测试体系、资源化与文档上明显领先；兼容性在现代设备上大幅领先、在「旧安卓手表」目标场景只是小幅领先；对方在登录态生命周期、崩溃可观测性、更新链完整性、旧设备保守选型（OkHttp 3.12 / 纯 Java）与若干播放器/互动细节上更成熟**。我方有 2 个 P0（图片管线 Cookie 外发、收藏夹编辑重置隐私）、20 个 P1 需优先处理。

## 1. 报告说明与方法

### 1.1 两个项目是什么关系

两个仓库**不是同一 git 血统**：我方的提交不在对方对象库中，对方 90 个提交也不在我方历史里。二者是同一上游「哔哩终端（BiliClient）」的两个独立兄弟分支：

- 我方基于上游 2.7.0 做了 Kotlin 重构（Kotlin 299 文件 56120 行 + Java 137 文件 22433 行，主代码），保留了上游的页面结构并重写了大量实现；
- 对方基于上游 2.9.8，保持纯 Java（354 文件 59468 行），以「低配手机 / 圆屏手表 / 老设备」为定位，包名 `com.RobinNotBad.BiliClient.re`，可与原版共存。

对方 readme 第 210-211 行也明确写了「RE：哔哩终端 是另一位开发者用 Kotlin 重构的新分支，与本项目不是同一个项目」。所以本报告按**同源分叉对比**处理：不比「谁抄谁」，只比「同一类需求两边各自做到什么程度」。

### 1.2 证据与严重度定义

- 每条问题都给出 `文件:行号` 与真实代码片段；文件路径前缀 `[ours]` 指 `D:\Users\ASUS\Desktop\DEVELOP\ReBiliClient`，`[theirs]` 指对比仓库克隆。
- 严重度：**P0** 核心链路不可用 / 凭据或隐私泄露 / 数据丢失；**P1** 高频路径的用户可感知缺陷或安全短板；**P2** 体验、可维护性、前瞻性技术债。
- 证据强度标注【事实】（代码/命令可直接复核）与【推测】（由代码推得、未真机复现）。报告中「对方更优」的结论只采用【事实】；对方 readme 的自述一律回代码核实，单列在附录 B。
- 统计口径：行数为原始行数；`app/src/main` 与 `app/src/test` 分开计；中文硬编码剔除整行注释后统计；空 catch 统计主代码（不含测试）。

### 1.3 核验过程（三轮）

1. **分析**：按安全、兼容性、播放器、视频/番剧、社交、界面/教程、工程规范、健壮性、性能、定量指标 10 个维度并行扫描，产出 91 条问题（P0×2、P1×20、P2×69）。
2. **核验**：3 组独立核验代理对全部 91 条逐条复核（复算数字、逐字比对代码、检查行号），共提出 92 条修正意见：数字不准 9 条、需限定 7 条、引用/表述不实 4 条、建议降级 10 条，其余为补充说明；无一条引用了不存在的代码。修正已全部落实，本报告使用的是修正后的口径。
3. **本报告复核**：报告成文后又对全部关键结论做了一轮抽样核对（见第 14 章）。

### 1.4 目标设备画像与评分口径

本项目是**第三方 B 站安卓手表客户端**，产品的第一目标是「旧版安卓手表能跑、好使」，手机/平板只是顺带适配（`AGENTS.md`：一切功能优先考虑手表端）。仓库文档记录的线上目标设备特征：

- 系统跨度：minSdk 19 到 Android 12/13 都可能有，线上出现过的机型包括 XTC `monaco_go Z10`（SDK 30）等定制手表 ROM；
- 硬件：以 32 位 ARM（`armeabi-v7a`）为主，也有 x86 表；内存小、CPU 慢（`docs/architecture-map.md:754` 记录了低内存机型的实际表现）；
- 屏幕与交互：240–400px 级圆屏/窄屏、`values-w300dp` 尺寸档、MENU 键/旋钮（`RotaryRecyclerView`/`RotaryScrollView`）、无键盘，打字成本高；
- ROM 残缺：线上崩溃记录里有 WebView 在特权进程被禁用、系统设置页/相册 Activity 不存在、框架方法被裁（`View.hasOnLongClickListeners` 抛 `NoSuchMethodError`）等（`docs/architecture-map.md:1180-1300`）。

因此本报告按**目标设备口径**评分：第一优先是「API 19–30 的 32 位手表上能装、能启动、核心链路可用，且对残缺 ROM 有防御」；第二是低内存/冷启动/包体/功耗；**「现代 targetSdk 合规」只算前瞻加分**——在旧手表上它不带来行为差异，只在用户换新设备时有用。对方 readme 同样把「低配手机/圆屏手表/老设备」当卖点，所以两边是在同一目标场景下比较，只是路线不同：对方用「保守技术栈」换老设备稳定，我方用「现代工程 + ROM 防御」换可维护性。

> 口径说明：初稿的兼容性评分按现代设备标准给出，经复核本项目目标设备后已重评（见 2.1 与第 4 章）。

## 2. 结论摘要

### 2.1 六维评分（10 分制，基于本报告证据的主观综合）

| 维度 | 我方 | 对方 | 一句话依据 |
| --- | --- | --- | --- |
| 安全 | 6.5 | 6.5 | 我方有 P0 图片 Cookie 外发与更新链缺口，但权限/导出/备份/隐私同意更现代；对方凭证处理更细，但 targetSdk 26 与旧依赖拖后腿 |
| 兼容性（目标：旧安卓手表） | 7.0 | 6.5 | 我方 ROM 残缺防御、圆屏/滚轮、ABI 覆盖更全；但 OkHttp 4.12 官方下限 API 21 与 minSdk 19 冲突，低内存表缺真机证据；对方选 OkHttp 3.12 + 纯 Java 更贴老设备。现代设备口径我方 8.5 vs 4.0，但那是非目标场景 |
| 功能性 | 7.5 | 7.5 | 我方功能面更宽（消息/反馈/教程/笔记/短视频/外观），对方在播放器调音、片头片尾、空降弹幕、动态可见范围、推荐分页等细节更深 |
| 规范性 | 7.0 | 4.0 | 我方有测试（841 个 `@Test`）、3 套 CI、完整文档；但 lint 全关、文档多处失真、发版 tag 无校验；对方自述「AI 生成、别当规范」且 0 测试 |
| 健壮性 | 7.0 | 7.5 | 我方下载/网络细节更强；对方崩溃上报闭环、页面栈恢复、Cookie 续期、原生崩溃可观测性明显更完整 |
| 性能 | 6.5 | 7.0 | 双方都无启动实测；我方冷启动 0 延迟并发约 9–11 个 HTTP，对方统一 2–2.9s 错峰，但对方也没有度量 |

> 评分用于快速定位强弱，不替代逐条证据。同一维度的具体差距见第 3–11 章。

### 2.2 我方必须优先处理的 10 件事

| 序号 | 问题 | 严重度 | 证据位置 | 修复工作量 |
| --- | --- | --- | --- | --- |
| 1 | Glide 图片管线对任意域名附加完整登录 Cookie | P0 | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/helper/CustomGlideModule.kt:46-56` | S |
| 2 | 编辑收藏夹把「私密」重置成公开、清空简介 | P0 | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderEditActivity.kt:73-78` | M |
| 3 | 崩溃上报无本地留档/队列/补发，早期崩溃零记录 | P1 | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/ErrorCatch.java:38-93`、`activity/CatchActivity.kt:82-108` | M |
| 4 | 应用内更新缺 SHA-256/size 校验、安装权限预检、目录在外部缓存 | P1 | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/util/UpdateManager.kt:349-357`、`:427-449` | M |
| 5 | 下载入口被通知权限硬门禁，拒绝后无降级 | P1（条件性 P0） | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:139-147`、`:762-775` | S |
| 6 | 切号无 Cookie 代际隔离；Cookie 续期只在启动时一次 | P1 | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/util/NetWorkUtil.java:517-537`、`activity/SplashActivity.kt:367-390` | M |
| 7 | 动态「仅自己可见/关闭评论」API 已就绪但 UI 未接线 | P1 | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/api/DynamicApi.java:442-449`、`activity/dynamic/DynamicActivity.kt:167-169` | S |
| 8 | 评论列表缺去重/本地补回/换口径兜底；楼中楼开放带图必失败 | P1 | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/reply/ReplyFragment.kt:287-304`、`WriteReplyActivity.kt:199-221` | M |
| 9 | 发版 tag 无版本一致性校验，任意 tag 触发 Release（历史已出错版） | P1 | `[ours] .github/workflows/build-release.yml:38-40`、`:228-233` | S |
| 10 | 进程被杀后页面栈不恢复；下载服务 START_STICKY 后不续跑 | P1 | `[ours] app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadService.kt:371-384`、`BiliTerminal.kt:383` | M |

### 2.3 我方的相对优势（对方明确不足的地方）

1. **测试与门禁**：841 个 JVM 单测 / 2034 处断言，覆盖 API 解析、播放器策略、下载引擎、外观、设置等；对方 0 个测试文件、无 `test/` 目录。
2. **旧手表适配的工程化**：真正对目标设备有意义的是残缺 ROM 防御——`util/ViewCapabilityProbe.kt` 对裁掉框架方法的手表做能力探测（`activity/base/BaseActivity.kt:353-395` 用它绕开 `View.hasOnLongClickListeners` 的 `NoSuchMethodError`）、系统设置页缺失时返回 Boolean 而非卡死、存储可写性按「专属目录免权限」语义判断、选图统一 `ACTION_GET_CONTENT`、MENU 键防双触发、圆屏留白与滚轮输入（`RotaryRecyclerView`/`RotaryScrollView`）；32 位 `armeabi-v7a`/`x86` 与 multidex/Dalvik 也都覆盖。
3. **ABI 覆盖 + 前瞻合规**：我方 3 个 ABI 分包 + universal（对方只打包 `armeabi-v7a`、`x86`，其 readme 自认纯 64 位设备 `INSTALL_FAILED_NO_MATCHING_ABIS`）；targetSdk 34 所需的前台服务类型/权限、`POST_NOTIFICATIONS`、`FLAG_IMMUTABLE`、显式 `android:exported`、通知渠道全部落地——这项在旧手表上用不到，属用户换新设备后的前瞻保障；对方 targetSdk 26 是老设备保守路线的副产品，不应简单判为落后。
4. **资源化与文案**：`strings.xml` 1842 条 vs 180 条；布局文件硬编码中文 0 处 vs 381 处（`android:text` 单属性 364 处）；我方还有统一 `UiText` 迁移层。
5. **安全基线**：`allowBackup=false` + `dataExtractionRules` + `tools:replace`、导出组件仅 2 个、`networkSecurityConfig`、日志脱敏工具 `LogSanitizer`、隐私同意弹窗 + 遥测默认关闭策略；对方上传崩溃到第三方 BaaS（Supabase）且默认开启。
6. **文档与协作**：51 个 Markdown、2.65 MB（架构地图、接口笔记、测试手册、活台账），AGENTS 分层约束；对方 3 个文档、无开发规范。
7. **功能面**：消息未读与通知、自建反馈通道、新教程系统（11 篇 DSL + 分级 + 强制已读）、外观体系（主题/圆屏/字号/边距）、下载引擎（分片并发、原子替换、字节校验、重定向白名单）、播放器 ijk option 与弹幕时钟、PlaybackService 前台义务防护等。
8. **代码结构**：已按 `api/`、`player/`、`service/`、`tutorial/`、`ui/` 分层并有可测策略类；对方大量逻辑集中在 4858 行 `PlayerActivity.java`、958 行 `DownloadService.java` 中。

## 3. 安全

### 3.1 P0：图片加载管线把完整登录 Cookie 发给任意图片域名

这是我方唯一一个「凭据直接外发」级的问题，也是全报告最高优先级。Glide 专用 OkHttpClient 的应用拦截器把全局 `webHeaders` 全量追加，**没有任何目标域判断**：

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/helper/CustomGlideModule.kt:46-56
builder.addInterceptor { chain ->
    val headers: ArrayList<String> = NetWorkUtil.webHeaders
    val requestBuilder: Request.Builder = chain.request().newBuilder()
    var i = 0
    while (i < headers.size) {
        requestBuilder.addHeader(headers[i], headers[i + 1])
        i += 2
    }
    chain.proceed(requestBuilder.build())
}
```

而 `webHeaders` 的第一组就是完整 Cookie：

```java
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/util/NetWorkUtil.java:628-631
headers.add("Cookie");
headers.add(getCachedCookies());   // SharedPreferences 里的完整 Cookie：SESSDATA / bili_jct / DedeUserID
```

外站图片确实会进入这条管线：`GlideUtil.java:30-32` 对任意 `http` URL 原样放行，`UserListAdapter.kt:57-62` 把服务端返回的头像 URL 交给 `Glide.with(...).load(GlideUtil.url(...))`。`network_security_config.xml:22` 为爱发电 `afdiancdn.com` 开了明文白名单，赞助榜头像很可能来自该域（域名归属属【推测】，但「任意 URL 都会被附 Cookie」是【事实】）。

影响：只要图片 URL 指向站外，`SESSDATA`（会话接管）与 `bili_jct`（可代发点赞/评论/动态等写操作）就以请求头形式送达该域持有者；攻击面取决于服务端/内容里配置了什么图片地址。作者其实意识到过这个风险——`AppInfoApi.java:106-108` 自建接口的注释写着「防止携带 b 站 cookies 导致可能存在的开发者盗号问题」——但只堵了那一个接口，漏了图片管线。

对方已修同类问题（其注释直接写明「修复 P0」）：

```java
// [theirs] app/src/main/java/com/RobinNotBad/BiliClient/helper/CustomGlideModule.java:53-93（节选）
//凭据最小化（修复 P0：此前图片管线无域名过滤，把全量登录 Cookie 发往任意图片域）
if (NetWorkUtil.isBilibiliHost(request.url().host())) { /* 逐个 header 覆盖 */ }
else {
    requestBuilder.header("User-Agent", NetWorkUtil.USER_AGENT_WEB);
    requestBuilder.removeHeader("Cookie");
    requestBuilder.removeHeader("Referer");
    requestBuilder.removeHeader("Origin");
}
```

**修法**：拦截器按 `isBilibiliHost(host)` 分支——B 站域用 `header()` 逐个覆盖，非 B 站域只设 UA 并删除 Cookie/Referer/Origin；同时**再加一个 networkInterceptor**做同样过滤（跨主机重定向会保留自定义 Cookie 头，只靠应用拦截器堵不住）。回归点：B 站封面/头像/动图仍要能带 Referer 加载（`hdslb.com` 在 B 站域白名单内），部分 CDN 拒绝空 UA。

### 3.2 P1：应用内更新链的完整性与安装授权

更新链有两条独立缺口，叠加后就是「核心更新通道不可信 + 装不上」。

**完整性校验只有签名**：

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/util/UpdateManager.kt:349-357
val verifyResult = ApkVerifier.verify(context, result)
if (!verifyResult.ok) {
    result.delete()
    CenterThreadPool.runOnUiThread { onError(...) }
    return@run
}
```

`ApkVerifier.kt:21-25` 注释明确写了不采纳哈希的理由（「元数据与安装包来自同一个响应」），断点续传也没有内容校验；安装包落在 `externalCacheDir/update`（`UpdateManager.kt:200-205`、`:315`）。对方下载完立即做 SHA-256 + size + 签名三道闸，缺哈希/size 直接拒绝安装，目录在内部 `getCacheDir()`：

```java
// [theirs] app/src/main/java/com/RobinNotBad/BiliClient/util/UpdateManager.java:539-561（节选）
// sha256 不符 → 删包抛「更新包校验失败（SHA-256 不符）」
// 无 sha256 时用 size；两者都没有 → 删包抛「更新源未提供完整性校验信息，已中止安装」
```

**安装授权无预检、无兜底**：`UpdateManager.kt:427-449` 直接 `FileProvider.getUriForFile` → `startActivity`，无 `canRequestPackageInstalls()`；还给所有能响应安装 Intent 的包授予 **READ + WRITE**（`:439-446`）。调用方 `activity/settings/UpdateActivity.kt:297-302` 没有 try/catch 且紧接着 `finish()`——Android 8+ 首次更新时系统拒绝安装，用户看到的是「什么都没发生」。对方有预检 + 双兜底设置页、只授 READ：

```java
// [theirs] app/src/main/java/com/RobinNotBad/BiliClient/util/UpdateManager.java:604-617（节选）
if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
    MsgUtil.showMsg("请先允许本应用\"安装未知应用\"，再重新点击安装");
    // 跳 ACTION_MANAGE_UNKNOWN_APP_SOURCES，catch 后退到应用详情页
    return;
}
```

**修法**：`UpdateConfig` 携带 release 元数据里的 `sha256`/`size`，下载完成后先比 size 再流式算 SHA-256；目录改内部 `cacheDir` 并在 `file_paths.xml` 补 `cache-path name="update_apk" path="update/"`；`installApk` 加 SDK≥26 预检与引导、授权只给 READ、全程 try/catch 并把失败路径还给 UI（`retryBtn`）。

### 3.3 其余安全问题（P2）

| 编号 | 问题 | 我方证据 | 对方做法 | 建议 |
| --- | --- | --- | --- | --- |
| P2-2 | 全盘文件权限 `MANAGE_EXTERNAL_STORAGE`：默认图片保存链路确实依赖它（`Pictures/哔哩终端`），但视频/缓存默认目录本可免权限 | `app/src/main/AndroidManifest.xml:8-10`、`util/FileUtil.java:334-337`、`activity/download/DownloadActivity.kt:228-233` | 10 条权限，无 MANAGE，用 `tools:node="remove"` 摘掉 READ_PHONE_STATE | 图片默认改存 `MediaStore`/应用私有目录，把 MANAGE 降为「自定义路径」可选申请；与 4.5 P2-2 存储模型合并处理 |
| P2-5 | `Set-Cookie` 域校验 `endsWith("bilibili.com")` 可被 `evilbilibili.com` 绕过；无 Domain 时照收 | `util/NetWorkUtil.java:533-537` | 点后缀匹配 + 无 Domain 时要求来源为主站/API 域 | 改 `equals("bilibili.com") \|\| endsWith(".bilibili.com")`，并校验响应来源 host |
| P2-6 | 直播弹幕连接不校验服务端返回的 wss 域名，并下发含 `bili_jct` 的全量 Cookie | `activity/player/PlayerActivity.kt:2683-2696` | 同类问题存在（`CookieGenerator` 播放链路仍带完整 Cookie） | 解析 `host_list` 后校验域名后缀，Cookie 只带必要项 |
| P2-7 | 导出深链入口注册全部 http/https scheme，无 host/path 限定，且残留调试日志 | `app/src/main/AndroidManifest.xml:163-178` | 同样只注册 scheme（共性问题） | 限定 host 为 `bilibili.com`/`b23.tv`，清理调试日志 |
| P2-8 | 极验 WebView 把服务端返回的 `gt`/`challenge` 直接拼进 JS 字符串 | `activity/settings/login/CaptchaWebViewActivity.kt:200-203` | 用 `JSONObject.quote` 转义并注释缘由 | 改用 `JSONObject.quote(...)`；对 URL 参数做白名单校验 |
| P2-9 | 遥测采集面比对方宽：`ANDROID_ID` 派生 `device_key`、内存/ABI、崩溃日志 | `api/TerminalApi.java:383-405`、`:134-150`、`:211-241` | `install_id` 用随机 UUID，明确不采 AndroidID/账号 | 评估去掉 `device_key`（用 install_id + 服务端去重）；隐私文案逐字覆盖；`ram_*` 只留等级。**26.10.24 定案：`device_key` 按产品需求保留采集（不整改），仅落实「精确内存改档位」；隐私文案维持 v2 口径** |

> **修后状态（26.10.24 第二批，`d0c703b` + `b9d019c`（回退 device_key））**：P2-2 / P2-5 / P2-6 / P2-7 / P2-8 五条已落地，P2-9 部分落地（精确内存改为档位；`device_key` 采集按产品需求保留）——
> 图片默认目录改应用专属 + MediaStore 发布进相册（MANAGE 只在用户自定义公共目录时才可能需要）；
> Cookie 域校验改为 `equals` / 子域点后缀 + 响应 host 校验；直播弹幕校验 wss 域名与端口、握手只带 `SESSDATA`；
> 深链 http/https 限定 B 站域与 `b23.tv`；极验参数白名单 + `JSONObject.quote`；遥测内存只报档位，`device_key` 仍由 `ANDROID_ID` 哈希派生并上报。
> 逐条落点见 13.0，单测见 `CookieDomainPolicyTest`/`DeepLinkPolicyTest`/`CaptchaParamPolicyTest`/`PictureSavePolicyTest`/`TerminalApiTest`。

### 3.4 对方 readme「安全加固」四条核实

| 宣称 | 核实结论 |
| --- | --- |
| 登录凭证不进日志 | **属实**。对方 3 处命中均为文案含 Cookie 主题词，未见打印凭证值；我方同类 14 处亦未见凭证值，且有 `LogSanitizer` |
| 图片与播放链路不带会话 | **半属实（1.2.4 后已自修媒体链路）**：图片管线按域过滤属实；1.2.3fix 的播放链路不属实——`CookieGenerator.getCookieString(true)` 在 `forVideoQuality=true` 时仍拼完整 loggedCookie（含 `bili_jct`），`PlayerActivity.java:3182` 正传 true。1.2.4 新增 `PlayerApi.mediaHeadersFor` 收口（只有 B 站媒体域带 Cookie），但**直播弹幕 wss 握手仍下发全量 Cookie**（已加 host 白名单校验） |
| 更新包强制签名与完整性校验 | **属实**（SHA-256 + size + 签名三道闸，缺信息拒绝安装） |
| 崩溃上报改自建服务、移除闭源统计 SDK、默认开启可关闭 | **部分属实**：确实移除闭源 SDK，但「自建服务」实际是 Supabase BaaS（第三方托管），且默认开启；我方为自建 HTTP 接口 + 默认关闭策略 |

对方 readme 的「Vibe Coding 声明」主动承认命名与注释可能不一致，这一点在其代码里确实能看到（大量 `//noinspection`、注释与实现偶有漂移），评估其宣称时需要回代码。

## 4. 兼容性

### 4.1 总的判断（按目标设备口径）

先给结论：**在「旧安卓手表」这个目标场景，双方都能跑，我方小幅领先；在「现代设备」场景我方大幅领先，但那不是本项目的目标。** 两边都 minSdk 19、都带 `armeabi-v7a`/`x86`（我方另有 arm64），老表都能装；真正的差异在：

| 维度 | 我方 | 对方 | 判定 |
| --- | --- | --- | --- |
| 32 位 ARM / x86 | v7a、x86 分包 + arm64 | v7a、x86 单包 | 打平 |
| 老运行时网络栈 | OkHttp 4.12（4.x 官方要求 API 21+）与 minSdk 19 冲突 | OkHttp 3.12.13（3.x 线，官方支持 Android 2.3+） | **对方更稳** |
| 残缺 ROM 防御 | `ViewCapabilityProbe` 能力探测、缺设置页返回 Boolean、存储语义、`ACTION_GET_CONTENT`、MENU 键修复 | `ACTION_PICK`→`ACTION_GET_CONTENT` 兜底等 | 我方略强 |
| 圆屏/滚轮/按键 | `values-w300dp`、圆屏留白、`RotaryRecyclerView`/`RotaryScrollView`、MENU 键 | 圆屏适配 + `AmbientBackground`，无滚轮控件证据 | 我方略强 |
| 低内存/方法数/启动 | Kotlin + Material + 3 ABI，multidex 必需，冷启动并发 8 协程 | 纯 Java + 更小依赖面，启动统一延迟 2–2.9s | **对方更省**（未实测） |
| 现代系统合规 | targetSdk 34 全套适配 | targetSdk 26 | 我方领先（非目标场景） |

「对方更省」是静态推断：方法数、运行时与启动并发都更小，但两边都没有在低内存手表上量过内存与启动耗时。历史上我把「targetSdk 34 全部适配」当作最大优势，这是现代设备口径；按目标设备重排后，最该先动的是 **OkHttp 4.12 在 Android 4.4/5.0 的运行时风险**（见 4.4）与 **低内存手表上的冷启动/内存**（见第 11 章）。对方 targetSdk 26、纯 32 位、OkHttp 3.12 也不是「落后」，而是为老设备做的保守取舍，代价是新手机/arm64 设备受限。

### 4.2 目标设备的支持面与已声明范围

- 构建层：`minSdk 19` / `targetSdk 34`，`splits.abi` 覆盖 `armeabi-v7a`、`arm64-v8a`、`x86` 并出 universal（`app/build.gradle:21-22`、`:162-167`）；
- 文档层：readme 声明支持 Android 4.4+，但 4.4~6.0 的结论来自静态审计、未真机验证（readme:177-181，`docs/review/fix-progress.md:93`）；
- 设备层：文档出现过 XTC `monaco_go Z10`（SDK 30）等定制手表，但没有「已支持设备矩阵」，也没有声明 `uses-feature android.hardware.type.watch`（对方同样没有——两边都靠侧载/应用商店分发，不靠手表 Play 商店过滤）；
- 交互层：圆屏/窄屏靠 `values-w300dp` + `setRound()`，旋钮靠 `RotaryRecyclerView`/`RotaryScrollView`，无键盘场景靠预设选项与教程引导减少输入。

这四条决定了下面 P1 的定性：**旧手表上真正可能「一票否决」的是运行时兼容（4.4），而不是现代权限模型（4.3）**。

### 4.3 P1（条件性 P0）：Android 13+ 下载入口被通知权限硬门禁

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:762-775
private fun downloadClick() {
    if (FileUtil.resolveVideoDownloadDir(requireActivity()) == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        startDownloadFlow()
    }
}
```

```kotlin
// [ours] 同文件 :139-147 —— 拒绝后只弹一句提示
) { isGranted ->
    if (isGranted) startDownloadFlow()
    else MsgUtil.showMsg(UiText.of("需要通知权限才能进行下载，请前往设置授予权限"))
}
```

**先校准口径**：该问题只影响 Android 13+ 设备；目标旧手表（≤Android 12）不受影响，但对把本应用装到手机/新设备上的用户仍是核心链路缺陷，值得修。

全仓没有跳转通知设置页的代码（`ACTION_APP_NOTIFICATION_SETTINGS` 0 命中），设置页也没有通知权限状态项。对方 targetSdk 26 完全不请求该权限，下载不被门禁（代价只是进度通知不可见）。**下载是核心链路**：一旦用户拒绝或系统不再弹框，此后每次点下载都只有一条 toast，手表上找通知设置路径很长。建议：未授权时照常下载（后台服务不依赖通知权限），toast 改为「进度将在应用内显示」；拒绝分支弹引导对话框并 `startActivity(ACTION_APP_NOTIFICATION_SETTINGS)`（try/catch 兜底）；设置页加通知权限状态项。

### 4.4 P1：目标旧手表的头号运行时风险——OkHttp 4.12 与 minSdk 19

```gradle
// [ours] app/build.gradle:21-22、:247
minSdk 19
targetSdk 34
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

我方 `docs/architecture-map.md:62-171` 有一份完整的 minSdk 19 兼容清单（`java.util.function.*` 清零、`Base64`/`java.time`/`stream` 零使用、`Build.SUPPORTED_ABIS` 统一走 `TerminalApi.supportedAbis()` 守卫、`NotificationChannel` 全部带版本判断），工程纪律很好；但 readme:177-181 自己承认「Android 4.4~6.0 的兼容结论全部来自静态审计，尚未在真机验证」，`docs/review/fix-progress.md:93` 也写着未在 API 19~23 运行验证。而 OkHttp 4.x 官方要求 API 21+，对方用 3.12.13（3.x 线，官方支持 Android 2.3+）——**在目标旧手表上，这是全报告最需要先验证或处理的一条**。

这不是「对方落后」：对方是在为老设备有意选保守栈。我方要么在 API 19/20 上实测 OkHttp 4.12（可能因 `Platform`/TLS API 缺失崩），要么把 minSdk 抬到 21/22 并在 readme 如实写，要么对 4.4/5.0 设备降级到 OkHttp 3.12 兼容分支。三条路都比「假设它能跑」稳。

历史事故证明「静态看着没事」不够：`docs/architecture-map.md` 记录 `PasswordEncryptUtil` 用了 API 33 的 `String.strip()`，导致 API 19~32 登录静默失败，直到 26.10.10 才修。建议：按 `docs/archive/review/device-test-plan.md` T12 用 API 19 x86 模拟器 + 一台 API 21/22 真表跑「登录/首页/播放/下载」四链路；或把 readme 改为「Android 5.0+ 已验证 / 4.4 实验性支持」。

### 4.5 其余兼容性问题（P2）

| 编号 | 问题 | 我方证据 | 对方对照 | 建议 |
| --- | --- | --- | --- | --- |
| P2-1 | ABI 策略领先但有三处风险：arm64 缺 `libndkbitmap.so`；CI 产物名与 `pickApkUrl` 强耦合；universal 12.2MB | `app/libs/arm64-v8a/` 4 个 so vs 32 位 5 个；`DanmakuFlameMaster/src/main/java/tv/cjump/jni/NativeBitmapFactory.java:38-44` | 无 arm64，纯 64 位设备装不上 | 补 arm64 `libndkbitmap` 或显式记录降级原因；构建时校验三 ABI so 文件名集合一致 |
| P2-2 | 存储模型：targetSdk 34 下 `requestLegacyExternalStorage` 已是死配置；Android 10 上图片保存只能落私有目录 | `app/src/main/AndroidManifest.xml:22`、`util/FileUtil.java:334-337` | 无 MANAGE，路径策略更保守 | **26.10.24 已修（`d0c703b`）**：图片默认目录改应用专属，下载完成经 MediaStore 发布进 `Pictures/哔哩终端`；MANAGE 降为「自定义公共目录」才可能用到 |
| P2-3 | 媒体通知：「系统媒体控件」默认关闭且无 `MediaStyle`（MediaSession 本身已实现，约 30 处） | `activity/player/PlayerActivity.kt:2712-2720`、`service/PlaybackService.kt` | 有 MediaSessionHelper 隔离（API21+ 才加载） | 补 `MediaStyle` + 默认开启开关；MediaSession 抽到 `@RequiresApi(21)` 的 bridge 里 |
| P2-4 | 设备形态：107 处屏幕方向全锁（对手表是合理默认，对手机是限制）、仅 1 处 `windowSoftInputMode`、无 `values-night` | `AndroidManifest.xml`（107 处 `screenOrientation`） | 88 处方向锁、0 处输入法适配 | 手表保持锁定；若继续「顺带适配手机」，声明 resizeable 并只在手机配置下解除方向锁；输入密集页补 `adjustResize` |
| P2-5 | Android 12+ 系统 Splash 与自定义启动页叠加，双闪（仅 Android 12+ 生效，旧手表看不到） | `activity/SplashActivity.kt:80-99` | 同样问题（共性问题） | 用 `SplashScreen` API 或 `windowSplashScreen*` 主题 |
| P2-6 | 无安装器 ROM + targetSdk 34 存储限制下应用内更新静默失败 | `util/UpdateManager.kt:427-449` | 有安装页兜底与失败提示 | 归入 3.2 修复；失败时提供 APK 路径 |

### 4.6 兼容性方面我方的优势（按目标设备排序）

- **残缺 ROM 的防御式适配**：`util/ViewCapabilityProbe.kt` 把「手表框架裁掉某个方法」变成可测的能力探测（`activity/base/BaseActivity.kt:353-395` 用它绕开 `hasOnLongClickListeners` 的 `NoSuchMethodError`）；系统设置页缺失时权限申请返回 Boolean 而不是卡死；存储可写性区分「专属目录免权限」与「设置页可能不存在」；选图统一 `ACTION_GET_CONTENT` 而不是 `ACTION_PICK`；
- **手表交互**：`values-w300dp` 尺寸档、`BaseActivity.setRound()` 圆屏留白、`RotaryRecyclerView`/`RotaryScrollView` 滚轮、MENU 键消费与防双触发（`docs/architecture-map.md:281`）；
- **ABI 覆盖**：v7a/x86 覆盖老表 + arm64 覆盖手机（对方无 arm64，纯 64 位手机装不上）；
- **老运行时兜底**：multidex + `MultiDex.install`（Dalvik 必需）、minSdk 19 的 API 守卫清单（`NotificationChannel`、`Build.SUPPORTED_ABIS` 等）；
- **前瞻项（非目标场景）**：targetSdk 34 全套适配、`POST_NOTIFICATIONS`、FGS types、`FLAG_IMMUTABLE`、通知渠道——旧手表用不到，换新设备才有意义；
- 安全/备份：`backup_rules`/`data_extraction_rules` + `allowBackup=false`、107 个 Activity 显式 `exported="false"`（对方 89 个也处理了）。

## 5. 功能（一）：播放器、弹幕与下载

我方播放器功能并不弱，弱在「小而高频的交互」：无极调音、片头片尾多档、空降弹幕、±N 秒按钮都缺；而背景播放、下载引擎、弹幕时钟这些底层实现反而更稳。按修正后的严重度，本维度 11 条全部为 P2（功能缺口，不影响核心可用性），但用户感知强，建议按收益排序补。下表编号沿用 findings 的原始编号（`P1-1`…`P1-5` 是核验前的暂定级），严重度以本段说明为准。

### 5.1 功能缺口清单

| 编号 | 缺口 | 我方现状与证据 | 对方做法 | 建议 |
| --- | --- | --- | --- | --- |
| P1-1 | 无极调音 | 只有系统音量 ±1 档：`PlayerActivity.kt:2396-2413` `changeVolume()` 直接 `setStreamVolume`，手表系统音量往往只有 3~5 档 | ijk `setVolume` 增益 + 0~100% 滑条 + 长按音量键弹滑条 + 增益持久化（`PlayerActivity.java:2706-2747`） | 在 ijk 层加 gain 通道，UI 复用 `text_volume` 的显示；注意与系统音量解耦并单独记忆 |
| P1-2 | 片头片尾跳过只有一档且默认关 | `player/SkipOpEdPrefs.kt:32` `const val DEFAULT_ENABLED = false`；`PlayerActivity.kt:3136-3148` 仅「自动跳 + 撤回 Snackbar」，无手动按钮；数据还多打一次 `loadViewPoints()`（`:4039-4043`） | 五档（关/仅片头/仅片尾/都跳/询问）：`util/SegmentSkipController.java:27-40`；`clip_info_list` 随取流响应一次返回（`api/PlayerApi.java:65-67/376`），不额外请求 | 把 view_point 并入播放数据响应；加手动「跳片头/片尾」按钮；默认值保持关但给设置页入口与说明 |
| P1-3 | 空降弹幕 | 全仓无 `OnDanmakuClickListener`/`TimestampHighlighter`/`空降` 命中；`player/DanmakuManager.kt` 只做渲染与时钟绑定 | 正则解析 `mm:ss`/`h:mm:ss`（`DanmakuTimestampUtil.java:25-68`）+ 高亮 + 点击 seek（`PlayerActivity.java:231-246`） | 先在 DFM 层加 `TimestampHighlighter`（可独立测试），再在控制层触摸链路旁路 `DanmakuTouchHelper`，坐标要换算到弹幕层 |
| P1-4 | 音频焦点 | 双方都没有（两边 `requestAudioFocus` 均 0 命中），属共同缺口 | 同样没有 | 起播/恢复时 request，`LOSS_TRANSIENT` → pause，`LOSS_TRANSIENT_CAN_DUCK` → 降 gain；与 P1-1 的 gain 复用 |
| P1-5 | 弹幕磁盘缓存 | 每次进播放页全量重拉分段（`PlayerActivity.kt:2237` → `DanmakuApi.java:142-170` 并发拉 seg.so），无持久缓存 | 有本地缓存与失效策略（`PlayerActivity.java:2223-2258`） | 以 `cid+segment` 为键落盘，带 TTL；先做 L2 磁盘缓存即可 |
| P2-1 | 回前台重挂 surface 不完整 | 只对 SurfaceView 补画，TextureView 与直播分支不补（`PlayerActivity.kt:2863-2871`） | 对两种渲染路径都补画当前帧 | 在 `onResume` 按当前渲染器类型补画；直播分支单独验证 |
| P2-2 | 缺 ±N 秒按钮，横滑调进度默认关 | 只有音量加减按钮；`player_swipe_seek` 默认 `false` | `-10s/+10s` 按钮（可关）+ 横滑默认开（松手才 seek） | 左侧按钮列加两个文字按钮；评估把横滑默认改 true（我方已做松手才跳） |
| P2-3 | 播放会话哨兵 | 无 `PlayerSentinel`/`ExitReasonCollector`；native 崩溃/被 LMK 杀无痕迹 | 进入播放页写 SP 标记、阶段打点、冷启动对账（`PlayerSentinel.java`） | 归入 9.1 P1-1 的崩溃管道；先做精简版阶段标记 |
| P2-4 | 进度上报缺 bvid 与失败回读 | `HistoryApi.java:73` `reportHistoryPgc(...)` 无 bvid 形参，只 `logReportResult` 打日志 | 带 bvid、实时重建 Cookie 头、播放 25s 后四路回读并写诊断 | 加 bvid；加调试开关回读一次；注意我方 `currentCsrf()` 取实时 Cookie 的设计比对方更稳，勿回退 |
| P2-5 | 控制层双实现 | `PlayerControlDelegate`/`VideoPlayerCore` 真实调用点 0（只有定义、TAG、注释与文案命中） | 单文件实现（同样难维护） | 二选一：真接入并对齐「横滑松手才 seek」，或标 `@Deprecated` 并删除误导 |
| P2-6 | MediaSession 内联 | `PlayerActivity` 直接持有 `MediaSession` 字段（API21 类），minSdk 19 下字节码仍引用 | 隔离到只在 API21+ 加载的 `MediaSessionHelper` | 抽 `MediaSessionBridge`（`@RequiresApi(21)`），PlayerActivity 只调 bridge |

### 5.2 我方在播放器/下载上的优势

- **下载引擎**：分片并发、失败回退、30 秒总预算、字节数校验、重定向白名单、解压防死循环、原子替换；`DownloadService` 的前台服务 5 秒义务做记账（对方 958 行实现无这些细节）。
- **弹幕时钟**：以播放器位置为准，不每帧强改时钟（对方 readme 自述曾修「弹幕时间轴错位」，我方实现方向一致）。
- **ijk option**：`IjkNativeLibs` 对 so 加载状态做一次性状态机（UNLOADED/LOADED/FAILED），失败锁死不重试，避免反复崩溃；对方无对应处理。
- **播放页手势**：松手才 seek 的设计避免误触，且已有单测 `PlayerGesturePoliciesTest`、`SkipCancelPolicyTest` 等 9 个播放器策略测试。
- **策略可测**：`player/` 包把默认值、手势、跳过策略抽成纯对象，单测覆盖；对方全部内联在 4858 行 Activity 里。

## 6. 功能（二）：视频、番剧与搜索

### 6.1 P0：编辑收藏夹会把「私密」重置成「公开」并清空简介

这是全报告两个 P0 之一，且触发条件非常日常（改一次收藏夹名字就会中招）。列表页把 `intro` 硬编码为空串：

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderListActivity.kt:49-54
val intent = Intent(this, FavoriteFolderEditActivity::class.java)
intent.putExtra("mediaId", folder.mediaId)
intent.putExtra("title", folder.name)
intent.putExtra("intro", "")
intent.putExtra("isDefault", folder.isDefault)
startActivityForResult(intent, 1)
```

编辑页把空串当原简介回填，保存时 `privacy` 写死 `0`：

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderEditActivity.kt:73-78
val intro = editIntro.text.toString().trim()
btnSave.isClickable = false
CenterThreadPool.run {
    try {
        val result = FavoriteApi.editFolder(mediaId, title, intro, 0)
```

接口层原样上送（`FavoriteApi.java:391-398`）。B 站 v3 语义是 `privacy=0` 即公开（`bilibili-API/docs/fav/action.md:19`、`:113`），**省略/null 不等于不修改，默认就是公开**——所以不能靠「不传」绕过。根因是数据模型没有这两个字段：`FavoriteFolder` 只有 id/mediaId/name/cover/videoCount/maxCount/isDefault，列表接口也只解析 title/cover/media_count/attr。

对方编辑页打开时补一次 `folder/info` 回填：

```java
// [theirs] app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteFolderEditActivity.java:69-87（节选）
//拉取真实简介与隐私：列表数据不带这两个字段，不预填的话一保存就会把简介清空、隐私重置为公开
CenterThreadPool.run(() -> {
    FavoriteApi.FolderInfo info = FavoriteApi.getFolderInfo(mediaId);
    ...
    if (editIntro.getText().toString().trim().isEmpty()) editIntro.setText(info.intro);
    if (info.privacy == 1) radioPrivate.setChecked(true);
});
```

【核验限定】「对方更稳」只对 intro 成立：`folder/info` 的文档字段表里没有 `privacy`（只有 `attr` 位），对方 `info.privacy` 很可能恒 0，私有夹同样可能被重置（【推测】，需真机抓包）；对方 `info == null` 时也只 toast、不禁用保存。

**修法**：`FavoriteApi` 增加 `getFolderInfo(media_id)`（返回 title/intro/privacy/media_count/attr），`FavoriteFolder` 加 `intro`、`privacy`，编辑页打开时回填并加「公开/私密」单选，请求失败**禁用保存**；创建页同步加隐私选项；回归点：默认夹禁编辑、私有夹改名后仍私有、简介不被清空。

### 6.2 P1：番剧取流仍用 v1 接口 + `season_type` 硬编码 1

```java
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/api/PlayerApi.java:304-318（节选）
public static void getBangumi(PlayerData playerData) throws IOException, JSONException {
    String url = "https://api.bilibili.com/pgc/player/web/playurl";
    url += new NetWorkUtil.FormData().setUrlParam(true)
            .put("aid", playerData.aid)
            .put("cid", playerData.cid)
            .put("fnval", 1)
            .put("fnvar", 0)
            .put("qn", playerData.qn)
            .put("season_type", 1)          // ← 硬编码 1，所有分区都按「番剧」取流
            .put("session", md5(...))
            .put("platform", "pc");
```

续播走 `getLastPlayProgress(aid, cid)`（`x/player/wbi/v2` 的季级 `last_play_time` + `last_play_cid == cid`），失败才回退 `HistoryApi.findProgressMsByAid`。问题在于：服务端只维护「本季最近观看」一条记录，`last_play_cid` 判据在 PGC 上不能证明「这一集看过」——打开一集没看过的新番，只要 cid 恰好配对通过，就会把上一集的进度套过来。对方已迁到 v2 并读「本集进度」：

```java
// [theirs] app/src/main/java/com/RobinNotBad/BiliClient/api/PlayerApi.java:297-318（节选，注释为原文）
// 取流走 pgc/player/web/v2/playurl（PiliPlus 同款）：只有 v2 的响应里带
// play_view_business_info.user_status.watch_progress，那是"这一集自己的观看进度"；
// 老 v1 接口的 result 里完全没有这些字段。
int realSeasonType = playerData.seasonType != 0 ? playerData.seasonType : 1;
```

此外国创/电视剧/综艺/纪录片用错误的季类型取流，服务端可能直接不返回 v2 字段。**修法**：改 v2、补 `ep_id`/`season_id`、`season_type` 透传 `playerData.seasonType`；解析 `watch_progress` 且校验 `episode_info.cid == 请求 cid`；PGC 路径移除 wbi/v2 的 `last_play_time` 回退，只留 `findProgressMsByAid` 兜底。对方提交链（`f82f06b`、`ee572b0`、`c1024c9`）说明这一串坑真实存在。

### 6.3 P1：首页推荐没有分页游标，加载更多会静默停住

```java
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/api/RecommendApi.java:29-43（节选）
public static void getRecommend(List<VideoCard> videoCardList, int freshType) throws IOException, JSONException {
    String url = ("https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd");
    url += new NetWorkUtil.FormData().setUrlParam(true)
            .put("web_location", 1430650)
            .put("feed_version", "V8")
            .put("homepage_ver", 1)
            .put("uniq_id", UNIQ_ID)
            .put("screen", "1100-2056")
            .put("fresh_type", freshType);       // 只有 fresh_type 3/4 交替
```

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/video/RecommendActivity.kt:60-76（节选）
val newItems = list.filter { loadedBvids.add(it.bvid) }
if (newItems.isEmpty()) {
    setRefreshing(false)
    if (videoCardList!!.isEmpty()) showEmptyView()
    return@runOnUiThread          // ← 非首屏全被去重：既不 setBottom 也不提示
}
```

对方实测：不传 `fresh_idx`/`brush` 时服务端返回同一批 30 条（交集 30、顺序一致），带上后相邻两页交集为 0；并有独立的 app 端推荐数据源与设置项。**修法**：`getRecommend(List, page)` 补 `ps=20`、`fresh_idx=page`、`brush=page`、`fresh_type=4`；解析改 `opt*` 并过滤 `ad_info`/`can_play==0`/`card_goto!="av"`；空页 `setBottom(true)` 并提示；app 数据源可作为二期。

### 6.4 其余视频/番剧问题（P2）

| 编号 | 问题 | 我方证据 | 建议 |
| --- | --- | --- | --- |
| P2-1 | 番剧详情页不自动定位上次观看（现降 P2） | `BangumiInfoFragment.kt:346-350` 仅历史入口带 `targetEpid`，全仓无 `last_ep_id` 定位 | 增加 `HistoryApi.findLastWatchedEpid`，只定位不写记录，首屏执行一次 |
| P2-2 | 失效/获取信息失败详情页不自动退出，停在错误图 | `VideoInfoActivity`/`BangumiInfoActivity` 的失败分支 | 失败页给「返回/重试」，或 3s 后自动退出 |
| P2-3 | 排行榜解析零容错：无 code 判断、无 WBI 失败降级、全 `get*` | `api/RankingApi` | 改 `opt*`、判 code、WBI 失败换口径 |
| P2-4 | 搜索建议只认单形态、无页面状态恢复 | `api/SearchApi`、`SearchActivity` | 兼容双形态；保存 query/position |
| P2-5 | 收藏夹视频列表 `media_id<=0` 直接放弃 | `FavoriteApi` | 用 `fid→media_id` 兜底换算 |
| P2-6 | 接口层解析健壮性：`get*` 比例显著高于对方（`getString` 513 vs `optString` 401） | `api/` 46 个文件统计 | 定规范「api 层只用 opt*」；先改推荐/排行/搜索三类卡片并补 JVM 单测 |
| P2-7 | 番剧索引/排行双方都无入口，但对方留有可移植实现 | `MenuActivity.kt:47-62` 14 个菜单项，无番剧入口 | 可选排期：移植 chip 筛选 + `BangumiIndexApi` |

### 6.5 我方在视频/番剧上的优势

- 番剧选集、多季/OVA 切换、`targetEpid` 定位（历史入口）已实现；
- 视频详情/番剧详情的失败态、空态与重试比对方完整；
- 短视频（story）流、话题页（我方 108 行 vs 对方 33 行）、笔记、专栏等页面更完整；
- `PlayerApiPbpTest` 等 API 层单测覆盖游标与 PBP 解析，对方无测试。

## 7. 功能（三）：社交与账号

### 7.1 P1：切号没有 Cookie 代际隔离

```java
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/util/NetWorkUtil.java:517-527（节选）
private static void saveCookiesFromResponse(Response response) {
    List<String> newCookies = response.headers("Set-Cookie");
    if (newCookies.isEmpty()) return;
    synchronized (NetWorkUtil.class) {
        saveCookiesLocked(newCookies);
    }
```

```java
// [ours] 同文件 :536-537
if (cookies.containsKey("Domain") && !cookies.get("Domain").endsWith("bilibili.com"))
    continue;
```

在途请求在切号后返回时，其 `Set-Cookie` 会被无条件合并进新账号会话：轻则 csrf 快照污染（POST 回 -111），重则 SESSDATA 被旧值覆盖。`endsWith("bilibili.com")` 还放行 `evilbilibili.com`，无 Domain 时照收。对方用代际号在锁内比对，不一致直接丢弃：

```java
// [theirs] app/src/main/java/com/RobinNotBad/BiliClient/util/NetWorkUtil.java:446-472（节选）
private static volatile long accountGeneration = 0;
public static void bumpAccountGeneration() { accountGeneration++; }

private static void saveCookiesFromResponse(Response response) {
    Long requestGeneration = response.request().tag(Long.class);
    synchronized (NetWorkUtil.class) {
        if (requestGeneration != null && requestGeneration != accountGeneration) {
            Logu.d("cookie-skip", "stale generation, response discarded");
            return;
        }
```

切号时 `AccountManager.java:177-192` 先 `bumpAccountGeneration()` 再写状态。**修法**：`NetWorkUtil` 加代际 volatile + 请求 tag + 锁内比对；所有「写入全局登录态」入口（切号/登录收尾/resetLogin）自增；域名校验改点后缀匹配。改动小、风险低。

> **已修（26.11.02）**：`util/NetWorkUtil.java` 已加 `volatile long accountGeneration`，四个请求构造点统一 `tag(Long.class, …)`，`saveCookiesFromResponse()` 在锁内 `isStaleGeneration()` 判定后丢弃旧代际 `Set-Cookie`；写登录态收口到 `AccountManager.applyCredentials()` / `clearCurrentLogin()` → `NetWorkUtil.applyAccountCookies()`（锁内自增代际）。域名点后缀校验此前已由 `CookieDomainPolicy` 落地（见 §13.0 的 3.3 P2-5）。落点与验收见 `docs/review/fix-progress.md` §3.112。

### 7.2 P1：Cookie 自动续期只在启动时一次

`CookieRefreshApi` 唯一调用方是 `SplashActivity.checkCookieRefresh()`（`SplashActivity.kt:367-390`），且只对「有 SESSDATA、无 access_key」的账号走；失败一律保留登录态、无周期检查、无 `-101` 触发、无并发单飞。手表端常年不冷启动，Cookie 过期即掉登录且无提示。对方抽出 `CookieRefresher`：每小时（首次延迟 30 分钟）周期检查、`-101` 触发、5 分钟节流、`synchronized` 单飞、结果枚举区分「可重试/需重登」。**修法**：先把 `refreshCookie` 的 `boolean` 升级成结果枚举（独立可测），再抽 `util/CookieRefresher.kt`，`-101` 挂到统一错误处理里。

### 7.3 P1：登录收尾三处内联，取 refresh_token 可抛异常留下半登录态

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/login/QRLoginFragment.kt:396-408（节选）
SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, NetWorkUtil.getInfoFromCookie("DedeUserID", cookies).toLong())
SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, NetWorkUtil.getInfoFromCookie("bili_jct", cookies))
SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, loginJson.getJSONObject("data").getString("refresh_token"))
SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.cookie_refresh, true)
```

`getString` 在字段缺失/`null` 时抛异常，异常路径只把二维码置为可重扫，已写入的 `mid/csrf` 不回滚；`PasswordLoginFragment.kt:238-244` 用 `optString`，JSON `null` 会变成字符串 `"null"` 被后续 `isEmpty()` 当成有效凭据。对方统一到 `LoginApi.finishLogin`：

```java
// [theirs] app/src/main/java/com/RobinNotBad/BiliClient/api/LoginApi.java:180-192（节选）
String refreshToken = extractRefreshToken(loginData);
if (!TextUtils.isEmpty(refreshToken)) {
    SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, refreshToken);
} else if (previousMid != 0 && loggedInMid != previousMid) {
    //换号登录却没拿到新 token：本地残留的是上一个账号的凭据，必须清掉
    SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, "");
```

**修法**：抽 `LoginApi.finishLogin(context, loginData)` 统一双结构提取 + `isBlankToken` 过滤 `"null"`、换号时清旧 token、先校验再落盘、异常回滚；三个 Fragment 各删一段重复逻辑。

> **已修（26.11.02）**：没有新增 `LoginApi.finishLogin`，而是把「先校验、再一次性批量落盘」收到 `AccountManager.applyCredentials(mid, …)`——`mid` 必须与 Cookie 里的 `DedeUserID` 一致才写，缺 `access_key` / `refresh_token` 时写空串而不是沿用上一个账号的值；三个登录 Fragment 与 `SplashActivity.resetLogin()` / `MySpaceActivity.handleLogout()` 全部改走该入口。落点与验收见 `docs/review/fix-progress.md` §3.112。

### 7.4 P1：动态「仅自己可见/关闭评论」是死代码

API 层四个参数早已实现：

```java
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/api/DynamicApi.java:442-449
public static JSONObject buildPublishOption(boolean privatePub, Integer closeComment, Integer upChooseComment, Integer timerPubTime) throws JSONException {
    JSONObject option = new JSONObject();
    if (privatePub) option.put("private_pub", true);
    if (closeComment != null) option.put("close_comment", closeComment);
    if (upChooseComment != null) option.put("up_choose_comment", upChooseComment);
    if (timerPubTime != null) option.put("timer_pub_time", timerPubTime);
    return option;
}
```

但唯一调用点只喂了定时：

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/dynamic/DynamicActivity.kt:167-169
val option = if (timerSeconds > 0) {
    DynamicApi.buildPublishOption(false, null, null, timerSeconds.toInt())
} else null
```

`SendDynamicActivity` 只有图片、定时、投票、表情四个按钮，没有可见范围/评论开关，也没有发布后改可见性的接口。对方发布页有「仅自己可见」「关闭评论」两个可切换入口，并组装 `buildPublishOption(privatePub, closeComment ? 1 : null, null, timerPubTime)`，还有 `setPrivatePub` 长按菜单。**修法**：加两个开关 + Intent 回传 + 组装透传（编辑/转发场景隐藏）；可选补 `setPrivatePub`。

### 7.5 P1：评论列表缺兜底；楼中楼开放带图必失败

评论插入按事件 `pos`、不去重、无本地补回、计数只在建 adapter 时赋值：

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/reply/ReplyFragment.kt:287-304（节选）
if (reply.root == 0L) {
    var dataIndex = Math.max(layoutManager.findFirstCompletelyVisibleItemPosition() - 1, 0)
    list.add(dataIndex, reply)
    adapter.notifyItemInserted(dataIndex + 1)
    adapter.notifyItemRangeChanged(dataIndex + 1, list.size + 1 - (dataIndex + 1))
} else if (replyEvent.pos >= 0L && replyEvent.pos < list.size) {
    list[replyEvent.pos].childMsgList.add(reply)   // 用事件里的 pos，而不是按 rpid 定位
```

对方在同样位置堆了三层兜底：`loadedRpids` 去重、`localPostedReplies` 5 分钟本地补回、空列表按 `FALLBACK_REPLY_TYPES={17,12,1}` 换排序/换类型重拉、子评论按 `root.rpid` 定位、`getReplyCount` 回填计数。**修法**：照此移植，并让死方法 `getReplyCount` 复活。

楼中楼带图入口未按 `rpid` 隐藏：

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/reply/WriteReplyActivity.kt:199-215（节选）
findViewById<android.view.View>(R.id.image).setOnClickListener {
    // 带图评论仅大会员可用，点击前先检测大会员状态
    CenterThreadPool.run {
        try {
            val vipInfo = VipApi.getVipInfo()
```

服务端不支持楼中楼带图，用户会经历「压缩上传几十秒 → 12066 失败」（该错误码已收录在 `WriteReplyActivity.kt:39-51`）。对方一行隐藏：`addPic.setVisibility(rpid == 0 ? View.VISIBLE : View.GONE)`。**修法**：按 `rpid == 0` 设可见性 + 发送前兜底判断。

### 7.6 其余社交问题（P2）

| 编号 | 问题 | 建议 |
| --- | --- | --- |
| P2-1 | 定时发布只有 6 个固定档，未校验 B 站「6 分钟~7 天」 | 改成可输入时间；提交前校验范围 |
| P2-2 | 图片上传无结果缓存，编辑/重试整批重传 | 按本地 URI+尺寸缓存上传结果；失败只重传失败项 |
| P2-3 | 删除「当前账号」后仍以该账号登录，下次保存会加回 | 删除当前账号时强制切到有效账号或登出 |

### 7.7 社交方面我方优势

- 消息未读轮询 + 通知（`MsgNotifier`）、动态更新数、追番更新提醒；
- `-352`/`-412` 统一重试与错误文案；
- 自建反馈通道（Feedback 列表/详情/回复）、公告、开源许可页；
- 隐私同意弹窗 + 遥测开关（对方上传第三方 BaaS 且默认开）；
- 多账号管理比对方更完整（头像、删除、崩溃防护、`access_key` 兼容）；
- 话题页实现更完整，图片评论有 GIF/PNG 保真与大会员前置校验（对方无这些细节）。

## 8. 功能（四）：界面、教程与更新

### 8.1 P1：教程管理页写旧键，用户点「通过」无效

管理页用三份平行硬编码数组拼列表，且「通过」写的是旧 tag 键：

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/TutorialManagerActivity.kt:301-312（节选）
private fun passTutorial(item: TutorialItem) {
    if (item.isPager) {
        SharedPreferencesUtil.putBoolean("tutorial_pager_${item.tag}", false)
    } else {
        val xmlArray = resources.getStringArray(item.xmlArrayId)
        if (xmlArray.isNotEmpty()) {
            SharedPreferencesUtil.putBoolean("tutorial_history_${item.tag}", true)
            SharedPreferencesUtil.putInt("tutorial_ver_${item.tag}", xmlArray.size)   // ← 旧 tag
        }
    }
```

新系统读写的是 `tutorial_ver_<新id>`（`TutorialStore.kt:17,29` → `BaseActivity.kt:487`），新 id 是 `video_main`、`theme_notice`、`local`，旧 tag 是 `video`：视频详情教程「通过」无效；`theme_notice`（主题说明）与 `local`（缓存改版说明）在管理页根本没入口；`Tutorials.byId()` 注释说「供管理页重看使用」，但全仓无调用者 → 没有重看入口。对方只有单轨教程、能力更弱（无分级/强制/单篇管理），但键读写同一套、不会错位。**修法**：管理页改 `Tutorials.all` 单一数据源，通过/清除统一走 `TutorialStore.markRead/clear`，每张卡片加「重看」；加 JVM 单测断言「每个 id 都能在管理页数据源里找到且写入键一致」。

### 8.2 其余界面/教程/更新问题（P2）

| 编号 | 问题 | 我方证据 | 对方对照 | 建议 |
| --- | --- | --- | --- | --- |
| P2-4 | 教程三套数据源并行（新 DSL + 12 个 XML + 9 个 string-array + 旧 Activity/Helper） | `helper/TutorialHelper.kt:127`、`res/xml/tutorial_*.xml` 12 个 | 单轨 | 两步收敛：4 处 `showPagerTutorial` 迁新系统；XML 转 DSL 后删旧链路 |
| P2-5 | 界面可配置缺 6 项：背景底图、氛围背景、新版/经典回退、过渡动画总开关、启动等待、页面栈恢复 | `SettingGroupActivity.kt:258-311` 仅 8 项；`InstanceActivity.kt:21` 转场硬编码 | 独立 `SettingUIActivity` 241 行逐项实现 | 按收益补：过渡动画开关（S）→ 背景底图（M，注意 GPU：对方实测 1.6 倍缩放单帧 972ms）→ 氛围背景做预设 |
| P2-6 | 应用内更新无镜像/换源、无安装权限引导、无发布页兜底 | `UpdateManager.kt:226-241` 双源固定 | 镜像三选一 + 发布页兜底 | 归入 3.2；加镜像列表与失败文案 |
| P2-7 | 无障碍近乎空白：117 个图片控件仅 6 处 contentDescription，播放器控制条 5 个无描述 | 布局统计 | 同样偏低（共性问题） | 给交互控件补 `contentDescription`，优先播放器/下载/设置 |
| P2-8 | 设置搜索大小写敏感子串匹配，索引名靠手工对齐 | `SettingsIndex.kt` | 有搜索但同为手写 | 统一小写匹配；索引改为从设置定义生成 |
| P2-9 | 文案三层映射静默回退，教程管理页 32 条资源成死资源 | `strings.xml:1173-1204`、`SettingText.resolve` 在该页 0 调用（全仓 42 处） | 无资源化层 | 加「字面量都在 MAP 中」单测；删死资源 |
| P2-10 | 关于页缺「动画缩放诊断」，系统动画被关时无法自查 | 关于页 | 有类似诊断 | 读取 `Settings.Global.ANIMATOR_DURATION_SCALE` 并提示 |
| P2-11 | 拦截式 UI 一律吞返回键；GUIDE 教程不可跳过 | `BaseActivity`/教程 Pager | 对方 `onBackPressed` 空实现（更差） | 允许返回键关闭/跳过非强制教程 |
| P2-12 | 只有一个 `values`、`resConfigs 'zh'`、无夜间/亮色资源 | `res/values`、`app/build.gradle` | 同样只有 `values` | 至少加 `values-night` 跟随系统深色 |

### 8.3 界面与教程方面我方优势

- 外观体系：`ui/appearance`（主题、配色、圆角、字体）+ `values-w300dp` 圆屏适配 + 应用内预览；对方只有「新版/经典」两套布局与背景图。
- 教程系统：11 篇 DSL 教程 + 分级（GUIDE/NOTICE）+ 强制已读 + 单篇版本管理；对方只有 11 个 XML + 单页渲染，能力弱得多（当前是收敛中的中间态）。
- 独有页面：伪装页（计算器/蓝牙，防查手机）、笔记、待办、反馈、公告、赞助、开源许可、更新历史、设置分组/搜索。
- 播放页控制条、圆屏适配、横屏/竖屏布局拆分（`layout`/`layout-land`）比对方细。

## 9. 工程规范

我方在规范上的投入（测试、文档、CI、分层）明显高于对方，但「有制度、没门禁」的问题突出：lint 全关、tag 无校验、文档与实现漂移、架构宣称不实。对方则是另一种极端：代码集中、无测试、无规范文档，readme 自己声明「AI 生成、别当规范范本」。

### 9.1 P1：发版 tag 与安装包版本无一致性校验，任意 tag 触发 Release

```yaml
# [ours] .github/workflows/build-release.yml:38-40
    push:
      tags:
        - '*'
```

```bash
# [ours] .github/workflows/build-release.yml:228-233、:277-278（节选）
if [ "$EVENT_NAME" = "push" ]; then
  tag="$REF_NAME"
...
version_name=$(grep -oP 'versionName\s+"\K[^"]+' app/build.gradle | head -1)
```

两条信息没有任何一致性判断。事故实证：`tag 28.08.16` 指向 commit `a558f4f`，而该 commit 的 `app/build.gradle` 是 `versionName "26.08.14" / versionCode 2608140`——Release 名与包内版本不一致，且 `26.08.14` 没有对应 tag。另有 `26.08.17` 指向纯文档提交、`26.09.07` 与 `26.09.07-security` 指向同一 commit；18 个 tag 中 16 个 lightweight、仅 2 个 annotated。对方 CI 只对 `tags: 'v*'` 触发，22 个 tag 全部 `vX.Y.Z[-fix]` 且能对回发版提交，客户端按显式 `versionCode` 比较。

**修法**：签名前加硬校验（`tag == versionName` 否则 `exit 1`）；触发面收窄为数字版本 tag；发版一律 `git tag -a`；`verifyVersionConsistency` 扩展为接收 tag 参数。历史 `28.08.16` 建议加勘误说明。

### 9.2 lint 门禁整体关闭，CI 无 lint 步骤

```gradle
// [ours] app/build.gradle:143-147
lint {
    abortOnError false
    checkReleaseBuilds false
    disable 'UnusedResources', 'UnusedIds', 'MissingTranslation', 'ExtraTranslation'
}
```

「质量门禁」只剩单测；`UnusedResources` 关闭后死资源无人清理（见 P2-8 的 32 条死字符串）。建议至少恢复 `NewApi`（防止再出 `String.strip()` 事故）、`UnusedResources`，CI 加 `./gradlew :app:lintStandardDebug`，先 `abortOnError false` 观察一轮再收紧。

### 9.3 其余规范问题（P2）

| 编号 | 问题 | 证据 | 建议 |
| --- | --- | --- | --- |
| P2-1 | 注释/规则与实现不符：CI 注释描述不存在的本机配置、`gradle.properties` 注释自相矛盾、jsoup 注释 minSdk 口径错 | `.github/workflows/ci.yml`、`gradle.properties`、`app/build.gradle` | 逐条改正并把「注释即文档」纳入 review 清单 |
| P2-2 | README/AGENTS 自我描述失真 6 处：AGENTS.md:57「无 CI」、:67 仍有 mips、readme badge 版本、`strings.xml:352` 残留 Hilt/Retrofit 等 | 见左列 | 修文案；`verifyVersionConsistency` 扩成文档锚点校验（badge/Android 版本/workflow 数量） |
| P2-3 | 文档体系膨胀且有过期引用：51 个 md / 2.65MB，单文件 249KB/318KB，3 处引用已迁移路径 | `docs/architecture-map.md`、`docs/review/fix-progress.md` | `architecture-map.md` 拆 index + net/ui/pitfalls 分册；CI 校验相对链接存在 |
| P2-4 | `gradle.properties` 半数是僵尸/实验配置，已提交的 configuration-cache 在 CI 从未验证 | `gradle.properties` 97 行 | 删死键；CI 加 `./gradlew help` 验证配置缓存；本机配置移到 `~/.gradle` |
| P2-5 | 构建可复现性与供应链：镜像分发无校验、JitPack 依赖、无依赖验证清单、AGP 落后 | `gradle/wrapper/gradle-wrapper.properties`、`app/build.gradle` | 加 `distributionSha256Sum`；生成 `verification-metadata.xml`；JitPack 依赖改本地 aar |
| P2-6 | 单测质量高于预期但覆盖盲区集中在 Activity/网络/播放时序 | `app/src/test` 79 文件 | 引 `mockwebserver` 覆盖 `NetWorkUtil` 解压/重试/Cookie；`CenterThreadPool` 抽依赖后可测 |
| P2-7 | 架构「单链」宣称不实：30 处反向 import，`api/` 直接 import Activity，播放器 4774 行 | `api/PlayerApi.java:13-15` import 三个 Activity；`PlayerActivity.kt` | 修 AGENTS 描述；api 层不许 import Activity；播放器按手势/弹幕/控制/跳转拆分 |
| P2-8 | 死代码/资源小账：8 个确定死字符串、13 处 XML 伪注释 | `strings.xml`、`res/values/*.xml` | 删除；`#` 伪注释改 `<!-- -->`；`UnusedResources` 恢复后不再红 |
| P2-9 | GPL 合规：缺源码头声明与第三方（LGPL/Apache）许可文本 | 全仓 `.kt/.java` 与 `docs/` | 新增 `NOTICE`/`docs/LICENSE-THIRD-PARTY.md`；包级加修改声明 |
| P2-10 | 构建脚本混入本机任务：`copyApkToDesktop` 用 Windows 路径硬拼 + Groovy `String.execute()` 调 adb | `app/build.gradle:172-179` | 移到 `~/.gradle/init.gradle` 或独立脚本；统一 `tasks.register` |

### 9.4 规范方面我方优势

- 841 个单测、2034 处断言，79 个测试文件覆盖 api/player/service/ui/appearance；
- 3 套 CI（`ci.yml` 编译+单测、`build-release.yml` 发版、`relay-notify.yml` 同步）；
- 文档体系（AGENTS 分册、架构地图、接口笔记、测试手册、活台账）远超对方 3 个文档；
- GPL-3.0 许可、fork 归属与上游致谢完整；
- 代码分层（api/player/service/tutorial/ui）与依赖注入式纯函数抽取，可测性显著更好；
- 注释密度与「为什么这么做」的记录习惯（例如 `SkipOpEdPrefs` 的撤回说明）优于对方。

## 10. 健壮性

我方没有 P0 级健壮性缺陷，工程化处理（超时、分片回退、前台服务义务、解压防护）扎实；真正的差距是**失败之后留下了什么**：崩溃、下载、页面栈、Cookie 过期四条链路的「事后可恢复性」弱于对方。

### 10.1 P1：崩溃上报无本地留档、无队列、无补发

```java
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/ErrorCatch.java:45-74（节选）
Intent intent = new Intent(context, CatchActivity.class);
intent.putExtra("stack", stackTrace);
...
```

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/activity/CatchActivity.kt:82-108（节选）
if (TerminalApi.isCrashReportAutoEnabled()) {
    handler.postDelayed({
        if (isDestroyed) return@postDelayed
        CenterThreadPool.run { CrashReporter.upload(report) }
    }, AUTO_REPORT_DELAY_MS) // 3000L
}
```

唯一持久化载体是 Intent 字符串，进程被杀即无；上报是固定 3 秒一次性动作，用户点「退出」/系统回收即取消；失败只把文案改成「自动上报失败」，没有重试/排队/补发。对方是完整管道：

```java
// [theirs] app/src/main/java/com/RobinNotBad/BiliClient/ErrorCatch.java:123-167（节选）
CrashUploader.saveLocal(report);          // 先落本地，断网也不丢
boolean upload = CrashFilter.decide(report).upload; // 24h 指纹去重/栈帧过滤
CrashUploader.enqueue(context, report);   // 先入待发队列
...
t.join(1500);                            // 最多等 1.5 秒，不阻塞崩溃页
```

配套 `saveLocal`/`enqueue`/`tryUpload`/`flushPending`（冷启动补发）/`flushEarly`（早期崩溃）/每文件失败计数 `MAX_RETRY=20`，以及 24 小时去重与「无我方栈帧不传」过滤。

**修法**：先写 `filesDir/crash_pending/<ts>-<pid>.json` 再启动崩溃页（路径作 extra，顺带绕开 Intent 大字符串）；崩溃页改为「读文件 → 上传 → 成功才删」；启动时后台 `flushPending`（≤8 条待发、每文件 ≤20 次）；加 24h 指纹去重；上报与崩溃页解耦（daemon 线程 + 1.5s 上限）。

### 10.2 P1：START_STICKY 后下载不续跑

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadService.kt:371-391（节选）
override fun onStartCommand(serviceIntent: Intent?, flags: Int, startId: Int): Int {
    Logu.d("onStartCommand")
    if (serviceIntent == null) {
        return START_STICKY
    }
    // 幂等守卫（审计 S6）……
    if (batchRunning) { ... }
    batchRunning = true
    startForeground(FOREGROUND_ID, statusBuilder.build(), ...)   // ← 只有带 intent 才走到
```

系统在进程被 LMK 杀掉后重投 null intent，服务被拉起→立即返回→无人 `startForeground`、无人读队列、无人改 DB 状态：下载列表永远显示「下载中」，还可能因未兑现前台义务触发 `RemoteServiceException`。对方把 `startForeground` 放最前，null intent 显式恢复 `started` 后继续 worker 循环，注释直接点名该 bug 类：

```java
// [theirs] app/src/main/java/com/RobinNotBad/BiliClient/service/DownloadService.java:131-137
startForeground(FOREGROUND_ID, statusBuilder.build());
// START_STICKY 进程级重启时 intent 为 null：static 标记已复位，若不恢复 started，
// 下面的 worker 会立即空转退出，队列永久卡在 downloading 且服务空占前台
if (serviceIntent == null) { started = true; }
```

**修法**：null 分支与首次启动同构（幂等判断 → `startForeground` → `recoverStuckSections()` → 调度）；若不想自动续跑，至少把状态置 `none` 并通知「下载已中断，点击继续」。

### 10.3 P1：进程被杀后页面栈不恢复

全库检索 `onSaveInstanceState|onRestoreInstanceState|resume_page` 只命中播放器自身（`PlayerActivity.kt:232/300/711/2551-2554`），`BiliTerminal.kt:383` 的 `onActivitySaveInstanceState` 是空实现，`CrashTrail` 只记页面名、不能重建导航。对方 `ResumePageUtil` 把可恢复页面链持久化（`resume_page_stack`，`MAX_DEPTH=6`，token 精确 pop），Splash 冷启动 `takeRestoreIntents()` 重建返回栈，菜单退出/崩溃前清理，且用 `commit()` 保证 SIGKILL 前落盘。

**修法**：移植 Kotlin 版（约 150 行）：只保存「可从 Intent 无损重建」的页面（视频详情/番剧详情/搜索/动态），跳过播放页与 Menu；`singleTask` 的 Menu 要补 `onNewIntent`；崩溃 handler 里先 clear 再启动崩溃页。

### 10.4 P1：启动早期崩溃零记录

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/BiliTerminal.kt:381-390
override fun onActivityPaused(activity: Activity) {}
override fun onActivityStopped(activity: Activity) {}
override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
override fun onActivityDestroyed(activity: Activity) {}
})
val errorCatch = ErrorCatch.getInstance()
errorCatch.init(context)          // ← 在 MultiDex/SP/教程迁移/性能检测之后
```

`:317-324` 对 `:error_activity` 进程只做 SP/context/log 开关就 return，不装任何 handler。「装完打开就闪退」恰好发生在这段窗口，且不会留下记录。对方在 `attachBaseContext` 完成 `MultiDex.install` 后立即 `ErrorCatch.initEarly`（只写 `files/crash_early` + killProcess），崩溃页进程也装「只留痕、不再 startActivity」的 handler。**修法**：三步纯搬运——attachBaseContext 后装早期 handler、onCreate 最早处装完整 handler、崩溃页进程装只落盘 handler。

### 10.5 其余健壮性问题（P2）

| 编号 | 问题 | 证据 | 建议 |
| --- | --- | --- | --- |
| P2-1 | 应用内更新无安装权限预检、`startActivity` 无异常兜底 | `UpdateManager.kt:427-449` | 归入 3.2 |
| P2-2 | Cookie 续期只在启动时一次 | `SplashActivity.kt:109-167` | 归入 7.2 |
| P2-3 | 播放/进程异常退出无可观测性：无 PlayerSentinel/ExitReasonCollector | 全库 0 命中 | 先做精简哨兵（SP 阶段标记 + 冷启动对账），再做 API30+ `getHistoricalProcessExitReasons` |
| P2-4 | JSON 解析缺强类型容错：字段缺失/类型漂移整页失败（`getString` 513 vs `optString` 401） | `api/` 层 | 统一 `safeGet` 工具；热点接口先改 |
| P2-5 | 静默吞异常：空 catch 75 处（10 处仅注释）、`printStackTrace` 107 处 | 主代码统计 | 分「清理类/业务类」，业务类改 `Logu.w` 或提示；CI grep 禁新增 |
| P2-6 | 无网络连通性判断，弱网/断网全站等超时 | 仅 5 行 `isNetworkAvailable` + 1 处消费点 | 请求前快速失败 + 统一「无网络」文案 |

### 10.6 健壮性方面我方优势

- 下载：分片失败回退、30 秒总预算、字节数校验、重定向白名单、解压防死循环、POST 判非 2xx；
- `PlaybackService` 对前台服务 5 秒义务做记账（对方无）；
- 播放器 so 加载状态机（失败锁死，避免反复崩）；
- `CenterThreadPool` 统一异常兜底（协程异常不会冒到 `ErrorCatch` 杀进程）；
- `LogSanitizer` + 崩溃上报脱敏；
- 崩溃页独立进程 `:error_activity`，主进程崩溃后仍能展示（设计正确）。

## 11. 性能与包体

### 11.1 P1：冷启动网络风暴

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/BiliTerminal.kt:392-406（节选）
if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.DYNAMIC_UPDATE_CHECK_ENABLE, true)
    && SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0L
) {
    CenterThreadPool.run {
        try {
            val updateBaseline = SharedPreferencesUtil.getLong("dynamic_update_baseline", 0)
            val updateNum = DynamicApi.checkDynamicUpdate("all", updateBaseline)
```

后续三块（未读 2 个 HTTP、追番、更新检查）结构相同、**没有任何延迟**；`SplashActivity.kt:367-404` 再叠账号刷新（2 个 HTTP）、公告、遥测。合计**8 个协程 / 约 9–11 个 HTTP 同时起手**，全部落在 `Dispatchers.IO`。对方统一 `postDelayed` 2000–2900ms（`BiliTerminal.java:60` `STARTUP_NET_DEFER_MS = 2000`，`:117-134`），默认首屏前 0 个 HTTP。

**修法**：四块统一 `runOnUIThreadAfter(2500..3500)` 错峰，或挂到「首个 Activity resume 之后」；未读两个 HTTP 合并/串行；遥测并入同批。工作量 S。

需要纠正一个流传的说法：**「对方启动串行等待所有请求、弱网最多多等 6 秒」不成立**——`SplashActivity.java:156` 的 `splash_wait_requests` 默认 false，`:171` 的校验已挪到首屏之后，6 秒只存在于可选分支（`:296-315`）。真实差距是反向的：我方是 0 延迟并发，对方是延迟错峰。

在目标低内存手表上，这条比在桌面基准上更严重：冷启动阶段 CPU 与 IO 都最紧，8 个协程同时抢 `Dispatchers.IO` 会显著拉长首屏；对方的延迟错峰可能正是为低端设备做的取舍。不过两边都没在手表上量过启动耗时与内存，属【推测】，这也是 11.2 要先补埋点的原因。

### 11.2 P1：启动链路零耗时埋点

```kotlin
// [ours] app/src/main/java/com/RobinNotBad/BiliClient/BiliTerminal.kt:305-308
override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    MultiDex.install(this)          // 多 dex 扫描，最慢的启动步骤之一，零计时
}
```

从 `attachBaseContext` 到首屏 `onResume` 没有任何 `SystemClock` 计时；对方有 3 个 `Log.d("startup", ...)` 埋点，能把 MultiDex、Application.onCreate、首屏三段拆开看。**修法**：3 个埋点即可（attachBaseContext 前后、onCreate 结尾、首屏 onResume），统一 `Log.i("startup", ...)` 并保留到 release。

### 11.3 其余性能问题（P2）

| 编号 | 问题 | 证据 | 建议 |
| --- | --- | --- | --- |
| P2-1 | Glide 缓存上限偏大、位图池未封顶 | `helper/CustomGlideModule.kt:25-32`（内存档位 16/32/64MB、磁盘 64/128/256MB，无 `setBitmapPool`） | 按档位设位图池；低端机下调内存缓存 |
| P2-2 | `PerformanceManager` 三档「应用」方法空实现，首次冷启动档位到不了 Glide | `util/PerformanceManager.kt:221-232` | 在 Glide 初始化前完成档位判定，或提供延迟重建 |
| P2-3 | 包体：`crunchPngs false`，universal 12.20MB / debug 18.76MB | `app/build.gradle:94` | 发布只发 ABI 分包（已是）；universal 仅兜底；开 `crunchPngs` 需先验证 9-patch/圆屏资源 |

### 11.4 包体对比的正确口径

对方 release 9.02MB（unsigned）常被误读为「比我方小一半」。按 ABI 归一后：

| 口径 | 我方 | 对方 | 说明 |
| --- | --- | --- | --- |
| 单 ABI release | v7a 6.15MB / arm64 6.58MB / x86 6.68MB | 9.02MB 内含 v7a+x86 两套 so，v7a 半包约 11.44MB | 对方单包塞双 ABI，不能直接比 |
| 归一 v7a 总解压体积 | 11.75MB | 11.44MB | 差 2.7%，我方并不落后 |
| universal | 12.20MB | 无（32 位单包） | 我方多 ABI 覆盖的代价 |
| debug | 18.76MB | 14.62MB（15,330,222 B） | debug 含 UETool 与未压缩资源 |

结论：包体不是我方短板；真正的风险是 arm64 缺 `libndkbitmap.so` 与 CI 产物名耦合（见 4.5 P2-1）。

## 12. 定量对比总表

所有数字均由可复现脚本统计（方法见附录 C），口径统一为 `app/src` 范围、原始行数、主代码与测试分开。

| 指标 | 我方（26.10.24） | 对方（1.2.3fix） | 判读 |
| --- | --- | --- | --- |
| 主代码文件数 | 436（Kotlin 299 + Java 137） | 354（纯 Java） | 我方规模略大 |
| 主代码行数 | 78553 | 59468 | 我方 +32% |
| 测试文件 / 行数 | 79 / 10256 | 0 / 0 | 我方独有体系 |
| `@Test` 数 / 断言调用 | 841 / 2034 | 0 / 0 | 同上 |
| `strings.xml` 条目 | 1842 | 180 | 我方资源化程度高一个量级 |
| 布局硬编码中文 | 0 | 381（`android:text` 单属性 364） | 我方明显更好 |
| 空 catch（主代码，仅注释） | 75（10） | 99（9） | 归一后我方更低（0.96 vs 1.66/kLOC） |
| `printStackTrace` | 107 | 96 | 绝对量接近，归一后我方更低 |
| `runOnUiThread`（出现次数口径） | 574 | 423 | 双方都重，Kotlin 尾随 lambda 无括号导致我方计数偏高 |
| TODO/FIXME（主代码） | 7 | 9 | 相当 |
| `@Suppress` / `@Deprecated` | 171 / 4 | 177 / 0 | 相当 |
| Kotlin `!!` / `lateinit` | 1091 / 292 | 不适用 | 我方特有债务 |
| 依赖声明行数 | 28 | 18 | 我方多 debug 工具与 test 依赖 |
| 原生 so（每 ABI） | v7a 5 / arm64 4 / x86 5 | v7a 5 / x86 5 | 我方 arm64 缺 `libndkbitmap` |
| ABI 覆盖 | 3 + universal | 2（纯 32 位） | 我方领先 |
| minSdk / targetSdk / compileSdk | 19 / 34 / 34 | 19 / 26 / 33 | 我方更新；旧手表目标下多为前瞻收益（见 1.4） |
| 权限数 | 11 | 10 | 我方多 MANAGE/FGS types |
| Activity 条目 | 107 | 89 | 我方页面更多 |
| `exported=true` 组件 | 2 | 2 | 相当（都只开启动页与深链入口） |
| 废弃 API：`getColor(int)` / `onBackPressed` 覆写 / `notifyDataSetChanged` | 0 / 15 / 35 | 8 / 8 / 36 | 我方新 API 用得多 |
| release 体积 | v7a 6.15 / arm64 6.58 / x86 6.68 / universal 12.20 MB | 9.02 MB（v7a+x86 合一，unsigned） | 按 ABI 归一基本持平 |
| debug 体积 | 18.76 MB | 14.62 MB | 我方 debug 含 UETool |
| 最大单文件 | `PlayerActivity.kt` 4774 行 | `PlayerActivity.java` 4858 行 | 双方都有上帝类 |
| 文档 | 51 个 md / 2.65MB | 3 个文件 | 我方远超 |
| CI workflow | 3 | 1 | 我方更完整 |
| Gradle wrapper | 8.11.1 | 8.14.3 | 相当 |
| 崩溃上报闭环 | 无本地队列/补发 | saveLocal + enqueue + flush | 对方领先 |
| 页面栈恢复 | 无 | `ResumePageUtil` | 对方领先 |
| Cookie 周期续期 | 无 | 每小时 + -101 触发 | 对方领先 |
| 启动网络错峰 | 无（0 延迟并发） | 2–2.9s 错峰 | 对方领先 |
| 启动耗时埋点 | 0 | 3 处 | 对方领先 |

> 本表是修复前基线（17212e3）的快照。26.10.24 两批修复落地后：`strings.xml` 1876 条、测试 **87 个类 / 935 个用例**（`@Test` 935）；主代码文件/行数与空 catch 等其余指标未变。

## 13. 建议路线图

> 修复进度（26.10.24 晚，两批）：13.1 的两项 P0、13.2 的若干条与 §3.3 的 P2-2/P2-5/P2-6/P2-7/P2-8 已按本报告落地（图片管线按域过滤、收藏夹回填、下载通知门禁、教程键统一、动态可见范围、发版 tag 校验、下载续跑、无极调音；以及图片目录/域校验/深链/极验五项 P2 加固；§3.3 P2-9 只落地「精确内存改档位」，`device_key` 采集按产品需求保留）；逐项跟踪见 `docs/review/fix-progress.md` 第 51、52 条。**已修 12 条（P0 2/2、P1 5/20、P2 5/69），其余 79 条（P1 剩 15、P2 剩 64）尚未修**，详见 13.0。

### 13.0 已修复条目的逐条状态（26.10.24）

**第一批**（7 条：P0 2/2、P1 5/20，另完成 §5.1 的功能缺口 P1-1）：

| 报告编号 | 内容 | 落点提交 |
| --- | --- | --- |
| 3.1（P0） | 图片管线把 Cookie 发给站外域名 | `07a5336` |
| 6.1（P0） | 收藏夹编辑重置隐私/清空简介 | `0c96492` |
| 4.3（P1） | Android 13+ 通知权限硬门禁 | `6a947a6` |
| 7.4（P1） | 动态「仅自己可见/关闭评论」死代码 | `a0bcbb3` |
| 8.1（P1） | 教程管理页写旧键 | `35d79a4` |
| 9.1（P1） | 发版 tag 无版本一致性校验 | `b051edc` |
| 10.2（P1） | START_STICKY 后下载不续跑 | `6a947a6` |
| 5.1（P1-1） | 无极调音 0~100%（新增实现） | `13db917` |

**第二批**（§3.3 五条 P2 + P2-9 部分，提交 `d0c703b` + `b9d019c`（回退 device_key））：

| 报告编号 | 内容 | 落点文件 |
| --- | --- | --- |
| 3.3 P2-2 | 默认图片目录改应用专属 + MediaStore 发布进相册，MANAGE 降为「自定义公共目录」才可选申请 | `util/PictureSavePolicy.kt`、`util/FileUtil.java`、`util/PictureMediaStore.kt`、`activity/DownloadActivity.kt`、`AndroidManifest.xml` |
| 3.3 P2-5 | `Set-Cookie` 严格域校验（equals/子域点后缀）+ 响应 host 必须是 B 站域，fail-closed | `util/CookieDomainPolicy.kt`、`util/NetWorkUtil.java` |
| 3.3 P2-6 | 直播弹幕 wss 域名/端口校验，握手 Cookie 只带 `SESSDATA` | `activity/player/PlayerActivity.kt` |
| 3.3 P2-7 | 深链 http/https 限定 B 站域与 `b23.tv`，删调试日志 | `util/DeepLinkPolicy.kt`、`activity/GetIntentActivity.kt`、`AndroidManifest.xml` |
| 3.3 P2-8 | 极验 `gt`/`challenge` 白名单 + `JSONObject.quote` 转义 | `util/CaptchaParamPolicy.kt`、`activity/settings/login/CaptchaWebViewActivity.kt` |
| 3.3 P2-9 | 只落地「精确内存只报档位」；`device_key`（ANDROID_ID 哈希）按产品需求保留采集，隐私文案维持 v2 口径 | `api/TerminalApi.java`、`util/CrashReporter.kt` |

未修清单（按报告章节编号）：

- §3.2（P1 更新链完整性 + 安装授权）
- §3.3 P2-9 的 `device_key` 采集部分（产品需求明确保留，不作整改）
- §4.4（P1 OkHttp 4.12 与 minSdk 19）、§4.5 P2-1、P2-3～P2-6（P2-2 已修）
- §5.1 P1-2 / P1-3 / P1-4 / P1-5、P2-1～P2-6
- §6.2、§6.3（P1）；§6.4 P2-1～P2-7
- §7.1（已修，26.11.02 代际隔离，见 §3.112）、§7.2（已修，`CookieRefreshScheduler`）、§7.3（已修，26.11.02 统一凭据入口，见 §3.112）、§7.5（P1）；§7.6 P2-1～P2-3
- §8.2 P2-4～P2-12
- §9.2（lint 门禁）、§9.3 P2-1～P2-10
- §10.1、§10.3、§10.4（P1）；§10.5 P2-1～P2-6
- §11.1、§11.2（P1）；§11.3 P2-1～P2-3

### 13.1 P0：本周内修（2 项）

| 任务 | 改动点 | 验收方式 | 工作量 |
| --- | --- | --- | --- |
| 图片管线按域过滤 Cookie | `helper/CustomGlideModule.kt` 拦截器 + networkInterceptor；`NetWorkUtil.isBilibiliHost` 复用 | 单测：伪造站外 URL 断言无 Cookie 头；真机抓包赞助榜/站外封面 | S |
| 收藏夹编辑回填真实 intro/privacy | `FavoriteApi.getFolderInfo` + `FavoriteFolder` 字段 + 编辑页回填/单选 + 失败禁保存 | 单测解析 + 真机：私有夹改名后仍私有、简介保留 | M |

### 13.2 P1：两周内完成（按工作流分组）

| 工作流 | 覆盖问题 | 关键动作 | 验收 |
| --- | --- | --- | --- |
| A 崩溃可观测性 | 10.1、10.4、10.2 的哨兵部分（P2-3） | 本地留档 + 待发队列 + 冷启动补发 + 早期 handler + 崩溃页进程 handler；随后加精简 PlayerSentinel | 断网崩溃后重启可补发；日志可见 pending 计数 |
| B 更新链可信 | 3.2、4.5 P2-6、8.2 P2-6 | SHA-256/size 校验、内部 cacheDir、安装权限预检 + 引导、try/catch、镜像与发布页兜底 | 篡改包被拒；首次安装跳设置后能完成；无安装器不崩 |
| C 登录态生命周期 | 7.1、7.2、7.3、3.3 P2-5 | Cookie 代际隔离、CookieRefresher（1h + -101 + 单飞）、统一 finishLogin、域名点后缀校验 | 单测覆盖代际丢弃/续期枚举；真机切号无串号。**26.11.02：代际隔离与统一凭据入口已落地（`fix-progress.md` §3.112）** |
| D 社交功能补齐 | 7.4、7.5、7.6 | 动态可见范围/关评论开关、评论去重/本地补回/换口径、楼中楼隐藏带图、定时校验 | 空评论区发图可见；仅自己可见动态发布成功；楼中楼无带图入口 |
| E 兼容性收口（目标手表） | 4.3、4.4 | 判断 OkHttp 4.12 在 API 19/20 的可运行性（实测或降 minSdk/降库）；API 19/21/23/26/30 的 32 位表冒烟；下载通知权限降级 + 引导（手机路径）；readme 支持范围如实 | 低版本手表登录/首页/播放/下载四链路记录；OkHttp 取舍有结论 |
| F 发版门禁 | 9.1、9.2 | tag==versionName 硬校验、触发面收窄、annotated tag；lint 恢复 NewApi/UnusedResources | 故意推错 tag 的 CI 失败；lint 跑通 |
| G 进程恢复 | 10.3、10.2 | ResumePageUtil + DownloadService null intent 恢复 | 后台被回收后回原页；下载自动续跑或显式中断 |

### 13.3 P2：按收益分批（建议一个迭代一批）

1. **可观测与容错**：启动 3 处埋点、启动网络错峰、JSON `safeGet`、静默异常治理、网络连通性判断；
2. **播放器体验**：无极调音、片头片尾多档并入取流响应、空降弹幕、±N 秒按钮、弹幕磁盘缓存、音频焦点；
3. **推荐/番剧**：推荐分页游标与过滤、v2 取流 + 按集进度、番剧详情定位、排行榜/搜索容错；
4. **界面与教程**：教程管理页单一数据源 + 重看、过渡动画开关、背景底图、无障碍描述、`values-night`；
5. **规范与文档**：文档拆分、AGENTS 失真修正、gradle.properties 清理、供应链校验、NOTICE 许可清单；
6. **性能与包体**：Glide 位图池、PerformanceManager 接线、`crunchPngs` 评估、arm64 so 补齐与构建校验。

### 13.4 不建议做的事

- **不要把「支持 Android 4.4」当成必须**：目标是旧安卓手表，但线上主力是 Android 5–7 的 32 位表；OkHttp 4.12 在 API 19/20 的风险应先实测（见 4.4），再决定「如实收窄到 5.0+」还是「降库」。为了 Android 4.4 盲目回退 3.12 会牵连全部调用面，成本最高。
- **不要为了追 targetSdk 35/36 抬 minSdk 或大改行为**：目标设备是旧安卓手表，Android 14/15 的行为变更不在主要用户群；targetSdk 34 已满足当前合规，升级只该跟随「新设备用户占比」，而不是追新。
- **不要照抄对方的「双布局 + 立即重启生效」**：新旧 UI 混排风险高、维护成本大；我方应做能力开关而非双份布局。
- **不要照抄对方的崩溃上报到第三方 BaaS**：Supabase 属境外托管且默认开启，与我方自建接口 + 隐私同意策略冲突；只学其管道设计。

## 14. 核验记录与局限

### 14.1 四轮核验做了什么

1. **分析轮**：10 个维度并行扫描，产出 91 条问题；每条要求 `文件:行号` + 真实代码 + 对方对照 + 修法 + 证据强度。
2. **核验轮**（3 组独立核验代理，共 92 条修正意见）：
   - 数字类：`SettingsIndex` 297→69、空 catch 77→75、TODO 11→7、`onBackPressed` 26→15、断言 2207→2034、`docs` 2.9MB→2.65MB、`printStackTrace` 34→96（对方）、tutorial XML 17→12、布局硬编码 361→364（单属性口径）等；
   - 降级类：func-player 5 条 P1→P2（功能缺口非缺陷）、func-video P1-1→P2（历史入口已可定位）、func-ui P1-2/P1-3→P2、metrics 两条 P1→P2、sec P1-4→P2（默认图片保存确实依赖 MANAGE）；
   - 不实/需限定：func-player「全仓无 setVolume」不实（实际 6 处，仅主播放器未用）、sec「对方播放链路只发只读会话」不实（对方 `forVideoQuality=true` 仍带完整 Cookie）、sec「uploadStack 死代码」不实（`CatchActivity.kt:142` 有调用）、func-video「对方 privacy 一定正确」需限定（对方 `info.privacy` 很可能恒 0）、「对方启动串行等待 6 秒」不成立（默认 false）。
3. **报告复核轮（成文后）**：用脚本 + 逐行抽查再核一遍本报告的事实：
   - 引用解析：全文 45 个 `[ours]/[theirs]` 完整引用全部命中（文件存在、行号未越界，0 失配）；表格内省略包前缀的短路径按两仓库实际布局逐项对照。
   - 代码抽样：28 处关键代码（P0/P1 与全部「对方更优」结论）逐行比对，21 处与正文一致；7 处不匹配均为复核脚本自身的假设问题，报告原文无误——1 处是「预期缺失」的探针（`UpdateManager.kt` 本就没有哈希校验，命中反而说明报告写错）、3 处是路径假设（`SegmentSkipController`/`DanmakuTimestampUtil` 实际在对方 `util/` 目录、`ErrorCatch.java` 在对方根包而非 `util/`）、3 处是取值假设（`CustomGlideModule.kt` 经 `NetWorkUtil.webHeaders` 间接带 Cookie、`VideoInfoFragment` 用 `RequestPermission()` 而非权限名字面量、`RecommendApi` 的分页游标本就是「不存在」的待证项）。
   - 数字复核：严重度重新按条计数，确认 91 条 = P0×2 / P1×20 / P2×69；`PlayerActivity.kt` 4774 行、对方 `PlayerActivity.java` 4858 行与正文一致。
   - 复核中发现并修正 2 处表述不够精确：①`clip_info_list` 的解析位置实际在对方 `api/PlayerApi.java:65-67/376`，`SegmentSkipController` 只消费 `ClipInfo`，报告已补全引用；②附录 A 的 `AndroidManifest.xml`、`NativeBitmapFactory.java` 路径补全为仓库内全路径。
   - 复核后全仓 `markdownlint-cli2` 跑通（0 错误）；除本报告与 `docs/README.md` 索引外，未改动我方源码。
4. **口径复核轮（用户反馈后）**：用户指出本项目目标是「旧版安卓手表」，初稿按现代设备标准的评分口径不成立；已新增 §1.4 目标设备画像，重评兼容性（2.1、第 4 章），把 OkHttp 4.12 与 minSdk 19 的冲突升为旧手表头号运行时风险，targetSdk 34 适配改述为「前瞻加分」，并重排 4.x 小节、路线图与交叉引用；91 条问题与 P0/P1/P2 计数不变。
5. **修复验证轮（两批修复后）**：第一批（`87800a9` 等 9 个提交）与第二批（`d0c703b`）各自跑过 `:app:assembleDebug`（standard/coexist 双 flavor 通过）与两个 flavor 的 JVM 单测；第二批后实测 **87 suites / 935 tests / 0 failures**（新增 `CookieDomainPolicyTest`、`DeepLinkPolicyTest`、`CaptchaParamPolicyTest`、`PictureSavePolicyTest` 及重写的 `TerminalApiTest`）。§13.0 的落点提交与文件均按实际 `git log`/工作树核对。播放器、下载续跑、图片发布进相册等系统集成链路**仍未真机验证**，见 `fix-progress.md` 第 51、52 条的状态列。

### 14.2 无法核实或标注为推测的事项

| 事项 | 状态 | 说明 |
| --- | --- | --- |
| 赞助榜头像具体域名 | 【推测】 | 代码只证明头像来自服务端 JSON；`afdiancdn.com` 明文白名单提示可能来源，但无域名字面量 |
| 对方收藏夹隐私是否同样被重置 | 【推测】 | `folder/info` 文档无 `privacy` 字段，Gson 可能恒 0；需真机抓包 |
| 我方真机上的 Cookie 串号表现 | 【推测】 | 机制由代码直接决定，但复现率与症状未实测 |
| 双方启动耗时 | 未实测 | 只有「请求起手时机」这一确定差异；「慢几百毫秒」属定性 |
| 我方 native 崩溃比例、弱网丢失率 | 【推测】 | 无 `ApplicationExitInfo`/哨兵数据，无法量化 |
| 对方 9.02MB 与 11.44MB 的精确分解 | 估算 | lib/dex/res 分解未逐项精确核对（已标注） |
| 已验证机型/系统版本清单 | 缺失 | 仓库只有零散机型记录（XTC `monaco_go Z10` 等），没有「已支持设备矩阵」，评分只能按文档与代码推断 |
| 真机/模拟器验证 | 部分 | 报告结论基于静态审计 + 构建；两批修复做了编译与 JVM 单测（87 类/935 例全绿），但播放器、下载续跑、图片发布进相册等系统集成链路未真机验证 |

### 14.3 方法学局限

- 静态审计无法覆盖运行时时序、厂商 ROM 差异与风控行为；`【推测】` 标注即为此而设；
- 「对方更优」的结论只采用代码可证事实，不采信其 readme 自述；
- 严重度是工程判断，P1 与 P2 的边界（尤其功能缺口）存在主观性，报告已逐条给出理由；
- 代码行数、中文硬编码等指标受统计口径影响，报告中同时给出主代码/测试、单属性/四属性等口径；
- 目标设备口径：本报告初稿按「现代设备」评分，收到项目定位反馈后已按「旧安卓手表」重排（见 1.4）；兼容性分数与路线图优先级以重排后为准。仍缺一份明确的「已验证机型/系统版本」清单，这是下一步最该补的证据。

## 15. 上游增量复核（1.2.4，26.10.26）

> 本文定稿后对方发布了 1.2.4。26.10.26 已把兄弟仓库同步到 `94709cc`（tag `v1.2.4` 指向 `2a4777d`，versionCode `20261026`；其后还有 2 个仅官网改动 `cd234c4`、`94709cc`），相对审计基线 `c6705d8` 共 5 个提交、67 个文件、+3792/−158。本章只记录影响本文结论的部分，不重跑全量对比。

### 15.1 主要改动

- **新增竖屏短视频**：`activity/video/shortvideo/` 下 `ShortVideoFeedFragment.java`（1280 行）、`ShortVideoPlayerActivity.java`、两个 adapter 与 `FeedGestureLayout`；配套 `api/ShortVideoFeedApi.java`（289 行）、`api/StoryFeedQuery.java`（235 行）、`model/ShortVideoItem.java`（147 行）与 3 个布局；主菜单入口可在设置里关闭。
- **安全自修（对方自称 3 个 P0，均已在代码核实）**：媒体请求头按域分级——`PlayerApi.mediaHeadersFor(url)` 只对 B 站媒体域附带 Cookie，第三方 PCDN 候选只保留 https，播放器与短视频两处均改走该出口；DASH 反序列化修复——`GsonUtil` 注册 `DashDataDeserializer`，「仅音频下载」此前因字段名不匹配而从未可用；头像上传改 1080 采样重压 + `catch (Throwable)` 兜住 OOM。
- **稳定性**：下载 worker 整体 `try/finally` 收口 + `AtomicBoolean` 单 worker + 真取消语义；`-111` 归入可重试 `CSRF_MISMATCH`（只有 `-101` 才登出）；`saveCookiesFromResponse` 在 Cookie 轮换后同锁同步 csrf 快照，写操作改用 `NetWorkUtil.getLoginCsrf()`；全局 OkHttp 加 `retryOnConnectionFailure(false)`；暂停态 seek/重绘与弹幕 `userPaused`；失效页 `finish()` 条件修正；菜单排序只校验 key。
- **发布/工程**：`res/raw/keep.xml` 补 `@mipmap/tutorial_*`；CI 增加 release 资源冒烟（`aapt2 dump resources` 断言 6 张教程插图仍在包内）；版本 1.2.4 与官网/友情链接更新。

### 15.2 对本文结论的影响

- §3.4「播放链路不带会话」的对方问题已在 1.2.4 修复媒体路径；但**直播弹幕 wss 握手仍下发全量 Cookie**——`PlayerActivity.danmuSocketConnect`（1.2.4 工作树 `:3215-3274`）加了 host 白名单校验与全局 OkHttp 复用，`CookieGenerator.getCookieString(true)` 未收窄。对 1.2.4 而言，该项仍属「半属实」。
- 对方对 `-111`/CSRF 与下载服务的修法，与本文 §13.2 C、§10 的建议方向一致，说明两边在收敛到同一套修法；我方对应的 P1 项已部分收敛——**Cookie 代际隔离与统一凭据入口 26.11.02 已修**（`docs/review/fix-progress.md` §3.112），**`-111` 重试语义仍未修**，见 §13.0。
- 兼容性结论不变：1.2.4 仍是 `targetSdk 26`、只打 32 位 ABI、0 测试文件；新增的 CI 资源冒烟是 APK 级断言，不构成单测覆盖。
- 新增隐私面：`util/DeviceIdentity.java` 生成并持久化一个随机 32 位十六进制 `Display-ID`（短视频取流请求头），来源不是 ANDROID_ID，但属于新的设备级伪标识；我方「设备键」争议的同类问题在对方也出现了，只是实现不同（我方向 ANDROID_ID 哈希、对方随机持久 ID）。
- 文档瑕疵：对方 1.2.4 更新日志多处引用 `docs/audit/00-summary.md`，该文件不在仓库中（链接 404），其自述审计结论无法复核。

### 15.3 复核方式

- 同步：`git fetch origin --tags` + `git merge --ff-only origin/main`（`c6705d8`→`94709cc`，tag `v1.2.4`）。
- 逐文件核对：`git diff c6705d8..94709cc -- <path>`；关键行为在 1.2.4 工作树中按行号复核（`PlayerApi.java:210-270`、`PlayerActivity.java:1415-1417/3215-3274`、`ShortVideoFeedFragment.java:664-666`、`NetWorkUtil.java:69-81/531-543`、`ImageApi.java:125-190`）。
- 未重新构建 1.2.4（本次只做增量阅读）；其构建可用性仍以审计时的 `assembleDebug`/`assembleRelease` 记录为准。

## 附录 A：关键证据索引

| 维度 | 我方关键证据 |
| --- | --- |
| 安全 | `helper/CustomGlideModule.kt:46-56`、`util/NetWorkUtil.java:628-631`、`util/UpdateManager.kt:349-357/427-449`、`util/ApkVerifier.kt:21-25`、`app/src/main/AndroidManifest.xml:8-10/163-178`、`api/TerminalApi.java:383-405` |
| 兼容性 | `app/build.gradle:21-22/247`、`activity/video/info/VideoInfoFragment.kt:139-147/762-775`、`app/src/main/AndroidManifest.xml:22/544`、`activity/player/PlayerActivity.kt:2712-2720`、`util/ViewCapabilityProbe.kt:8-18`、`activity/base/BaseActivity.kt:353-395`、`res/values-w300dp/dimens.xml` |
| 播放器 | `activity/player/PlayerActivity.kt:2396-2413/3136-3148/2863-2871`、`player/SkipOpEdPrefs.kt:32`、`api/DanmakuApi.java:142-170`、`player/IjkNativeLibs.kt` |
| 视频/番剧 | `activity/user/favorite/FavoriteFolderListActivity.kt:49-54`、`activity/user/favorite/FavoriteFolderEditActivity.kt:73-78`、`api/FavoriteApi.java:391-398`、`api/PlayerApi.java:304-318`、`api/RecommendApi.java:29-43`、`activity/video/RecommendActivity.kt:60-76` |
| 社交 | `util/NetWorkUtil.java:517-537`、`activity/SplashActivity.kt:126-147/367-390`、`activity/settings/login/QRLoginFragment.kt:396-408`、`api/DynamicApi.java:442-449`、`activity/dynamic/DynamicActivity.kt:167-169`、`activity/reply/ReplyFragment.kt:287-304`、`activity/reply/WriteReplyActivity.kt:199-221` |
| 界面/教程 | `activity/settings/TutorialManagerActivity.kt:65-108/301-312`、`tutorial/TutorialStore.kt:17-29`、`helper/TutorialHelper.kt:127`、`activity/settings/SettingGroupActivity.kt:258-311`、`activity/base/InstanceActivity.kt:21` |
| 规范 | `.github/workflows/build-release.yml:38-40/228-233/277-278`、`app/build.gradle:143-147`、`AGENTS.md:57/67`、`gradle.properties`、`api/PlayerApi.java:13-15` |
| 健壮性 | `ErrorCatch.java:45-74`、`activity/CatchActivity.kt:82-108`、`service/DownloadService.kt:371-391`、`BiliTerminal.kt:381-390`、`api/TerminalApi.java:673-685` |
| 性能 | `BiliTerminal.kt:305-308/392-406`、`activity/SplashActivity.kt:367-404`、`helper/CustomGlideModule.kt:25-32`、`util/PerformanceManager.kt:221-232`、`app/build.gradle:94` |
| 度量 | `app/src/main` 与 `app/src/test` 全量扫描；`app/libs/*`；`app/build/outputs/apk` |

> 说明：表中以 `activity/`、`util/`、`api/`、`helper/`、`player/` 等开头的路径省略了公共前缀 `app/src/main/java/com/RobinNotBad/BiliClient/`；`ErrorCatch.java` 位于包根目录（`.../com/RobinNotBad/BiliClient/ErrorCatch.java`）；`app/build.gradle`、`app/src/main/AndroidManifest.xml`、`app/src/main/res/...` 按仓库根相对路径计。

## 附录 B：对方 readme 宣称核实（节选）

| 宣称 | 核实 | 说明 |
| --- | --- | --- |
| 番剧每集进度记忆 / 服务端双向一致 | 基本属实 | 走 v2 `watch_progress`，且有提交链修复单位与串集问题；我方仍 v1 季级口径 |
| 番剧选集（多季/剧场版/OVA） | 属实 | `BangumiInfoFragment.java` 多 section 实现 |
| 后台播放 + 通知遥控 | 属实 | `PlaybackService` + MediaSession（我方也有） |
| 切后台回原页面 | 属实 | `ResumePageUtil`；我方无 |
| 动态带图/投票/置顶/话题 | 属实 | 我方 likewise，话题页我方更完整 |
| 动态可见范围/关闭评论 | 属实 | 我方 API 就绪但 UI 未接线（我方 P1） |
| 多账号切换 | 属实 | 双方都有；我方管理更完整 |
| 应用内检查更新（镜像/断点续传/完整性） | 属实 | 我方缺 SHA-256/size 与镜像（我方 P1） |
| 界面过渡动画可关 | 属实 | 我方无此开关（P2） |
| 片头片尾五档 | 属实 | 我方单档且默认关（P2） |
| 空降弹幕点击直达 | 属实 | 我方零实现（P2） |
| 自定义背景底图 / 氛围背景 | 属实 | 我方无（P2） |
| 无极调音 0~100% | 属实 | 我方仅系统音量 ±1（P2） |
| ±N 秒快进快退 | 属实 | 我方无（P2） |
| 启动等待请求完成（弱网多等 6 秒） | **不实** | 默认关闭，且校验在首屏后；我方反而是 0 延迟并发 |
| 首页推荐 web/app 数据源切换 | 属实 | 我方无（P2） |
| 安全：图片/播放链路不带会话 | **半属实** | 图片按域过滤属实；播放链路仍带完整 Cookie |
| 修复老设备 Android 4.4/5.x 闪退 | 无法核实 | 对方称已修；我方无真机证据（P1） |
| 已知问题：只打包 32 位 | 属实（自认） | 我方 3 ABI 是优势 |

## 附录 C：度量方法与可复现命令

- 环境：Windows + PowerShell 5.1；两仓库均为只读审计（对方 clone 仅写入 `local.properties` 用于构建，未改源码）；我方仓库仅新增本报告与 README 索引。
- 规模统计：`app/src` 全量按扩展名计数与行数；主代码排除 `app/src/test`。
- 中文硬编码：匹配中文 Unicode 区间，剔除整行注释；布局只统计 `android:text|hint|title|contentDescription` 属性值。
- 空 catch/静默异常：正则匹配 `catch (...) { }` 与 `printStackTrace`，人工二次分类；报告采用主代码（不含测试）口径。
- 对方构建：`gradlew.bat :app:assembleDebug`（2m27s 成功）、`:app:assembleRelease`（成功，unsigned 9.02MB）；构建期本机并发 8 个分析进程，耗时不可用于横向性能比较。
- 体积：`app/build/outputs/apk/**` 现有产物（时间戳 2026-10-24 11:32–11:33，HEAD 处构建）；对方产物为本次现场构建。
- 报告中的每一处 `[ours]/[theirs]` 引用均在核验轮与成文后复核轮各核对一次；`【事实】/【推测】` 标注见 1.2。
