# 重复项合并 · 真机测试清单

> ✅ **结果：26.09.11 实测全部通过**（用户确认「都 ok」）。
> 覆盖 P0 时间格式化 / 快速缓存 / 流水页、P1 跳用户主页、P2 表冠滚动、以及 §6 回归冒烟（含解压路径）。
> 本文档保留为**回归清单**——以后再动这几处公共落点时照着跑一遍即可。

> 对应改动见 `docs/review/cleanup-progress.md`（6 项合并）。
> **测试包**：`app/build/outputs/apk/debug/` 下的 `app-arm64-v8a-debug.apk` / `app-armeabi-v7a-debug.apk`
> **当前状态**：`com.RobinNotBad.BiliClient`（arm64 调试包）**已覆盖安装到那台 vivo V2229A 手机上**（00:49），登录态保留。
> 手表还没装，表冠那几项必须在手表上验。

---

## 0. 我已经替你验掉的部分（不用重测）

在那台 vivo 手机上，用 `uiautomator` 取视图树文本实测：

| 已验证 | 证据 | 对应改动 |
|---|---|---|
| 冷启动无崩溃 | `SplashActivity` → `RecommendActivity`，logcat 无 `FATAL`/`AndroidRuntime` | 全部改动 |
| 顶栏时钟正常 | 顶栏 `timeText` = `00:49` | `TimeUtil.formatTime`（原 `TextClock` 静态实例） |
| 视频发布时间正常 | 视频详情页 `timeText` = `2026-08-16 22:02:26` | `TimeUtil.formatDateTimeSec`（原 `VideoInfoApi`） |
| 覆盖安装保留数据 | 我的页显示 `紫色空灵_伊莲 / 110粉丝 54硬币` | — |

> ⚠️ 我覆盖掉了你 23:53 装的那个调试包。数据没丢，但**旧包我没有留备份**，需要的话得从 git 重新构建。

---

## 1. 优先级 P0：时间格式化（影响面最广，11 处调用全改了）

**做法**：把下面每处的时间显示看一眼，只要**格式对、日期时间合理**就算过。

| # | 去哪里看 | 期望格式 | 原实现位置 |
|---|---|---|---|
| 1.1 | 随便进 5~6 个页面，看**顶栏右上角时钟** | `HH:mm`（如 `00:49`），且过一分钟会自己跳 | `TextClock` |
| 1.2 | 视频详情页「发布时间」 | `yyyy-MM-dd HH:mm:ss`（如 `2026-08-16 22:02:26`）✅我验过 | `VideoInfoApi` |
| 1.3 | 视频评论区，**3 天以内**的评论 | 相对时间（`xx分钟前` 之类，走 `time_desc`，不是格式化） | — |
| 1.4 | 视频评论区，**3 天以前**的评论 | `yyyy-MM-dd HH:mm` | `model/Reply.java` |
| 1.5 | 消息页 → 点赞/回复/@ 通知 | `yyyy-MM-dd HH:mm` | `NoticeHolder` |
| 1.6 | 启动时的**公告**（或设置→关于→公告列表） | `yyyy-MM-dd`（只有日期） | `AppInfoApi:162` |
| 1.7 | 设置→关于→**赞助名单**的「捐赠时间」 | `yyyy-MM-dd hh:mm`——**注意这里本来就是这个 12 小时制格式**，别当 bug | `AppInfoApi:211` |
| 1.8 | 直播 → 直播间详情「开播时间」 | `yyyy-MM-dd HH:mm:ss` | `LiveApi` |
| 1.9 | 我的 → 大会员页「到期时间」 | `yyyy-MM-dd HH:mm:ss` | `VipActivity` |
| 1.10 | 番剧时间表每一话的时间 | `HH:mm` | `TimelineAdapter` |
| 1.11 | 历史记录列表（卡片上的观看时间） | `HH:mm` | `HistoryVideoCardAdapter` |

**重点看 1.7**：它和 1.6 同文件但格式不同（12 小时制 vs 24 小时制），是**原有笔误**，我刻意没改。若你觉得该统一成 24 小时制，告诉我，这是独立的一行改动。

---

## 2. 优先级 P0：长按「快速缓存」（3 份逐字节相同 → 1 份）

**前置**：设置里确认「长按快速缓存」开关（`cache_quick_mode`）是开的，且 `cache_default_quality` 记得是哪一档。

对下面**三个入口**各做一次长按（约 0.5 秒）：

| # | 入口 | 说明 |
|---|---|---|
| 2.1 | 推荐 / 热门 / 排行榜 / 搜索结果 / 收藏夹 任意一个视频卡列表 | 走 `VideoCardAdapter` |
| 2.2 | 我的 → 历史记录 | 走 `HistoryVideoCardAdapter`，它是 **200ms** 自实现的触摸长按，不是系统 `OnLongClickListener` |
| 2.3 | 点进某用户空间 → 视频系列那一栏的卡片 | 走 `UserVideoAdapter`（注意它的列表第 0 项是「视频系列」跳转项，别点错） |

**期望**（三处行为应完全一致，因为原本就是同一份代码）：
- `cache_default_quality = dialog` → 弹出清晰度选择页
- `= highest` → 提示「已开始缓存」
- `= audio_only` → 提示「已开始缓存音频」（若无音频流则提示「该视频没有可用的音频流」）
- 该视频已缓存过 → 提示「该视频已缓存，请先删除原缓存文件」
- **直播类卡片长按不触发**快速缓存（原本就判了 `type != "live"`）

---

## 3. 优先级 P0：经验 / 硬币变化记录（合并了 adapter + 布局）

**路径**：我的 → 更多 → 硬币变化记录 / 经验变化记录

| # | 检查 | 期望 |
|---|---|---|
| 3.1 | 列表能正常渲染 | 每项一个圆角卡片，三行：**变化值 / 变化说明 / 时间** |
| 3.2 | 硬币的负值 | 显示成 `-5`（不是 `+-5`） |
| 3.3 | 经验的值 | **恒带 `+`**，负数会显示 `+-5`——这是**原有行为**，别当新 bug |
| 3.4 | 有数据时不崩 | 没数据时提示「暂无硬币变化记录 / 暂无经验变化记录」 |

> 这一项我最想看：布局文件从两份合成了一份，虽然 MD5 相同，但**合并后有没有渲染错位**只有真机能确认。

---

## 4. 优先级 P1：跳用户主页（16 处手抄 → 统一）

逐个点，**每一个都应进到对应用户主页**：

| # | 位置 |
|---|---|
| 4.1 | 我的页 → 自己的头像 / 用户信息卡 |
| 4.2 | 关注列表 / 粉丝列表的每一项 |
| 4.3 | 视频评论区，某条评论的**头像** |
| 4.4 | 动态列表里动态的**头像** |
| 4.5 | 消息页 → 点赞/回复/@ 通知里的**头像**（同一条可能有多个人，逐个点） |
| 4.6 | 私信会话列表，**长按**某项 |
| 4.7 | 专栏 / 图文动态页顶部的 **UP 主卡片** |
| 4.8 | 勋章墙每一项 |
| 4.9 | 充电（电池）用户列表每一项 |
| 4.10 | 设置 → 关于 → **开发者卡片** |
| 4.11 | 评论区里点 `@某人` 或 `uid:xxx` 链接（走 `StringUtil`） |
| 4.12 | 任意 App 外部的 `uid` 深链（走 `LinkUrlUtil`） |

重点：4.11 / 4.12 是 **Java 侧**改的（`StringUtil` / `LinkUrlUtil`），我在这两处顺手删了 `UserInfoActivity` 的 import，**如果跳转没反应或崩了，优先怀疑这两处**。

---

## 5. 优先级 P2：表冠滚动（**只能在手表上测**）

那台 vivo 是手机，没有旋转编码器，这一项测不了。

**前置**：设置 → 界面与外观 → **启用表冠适配** 打开，并把「表冠适配灵敏度（Recycler）」「（Scroll）」设成你平常的值（非 0），**然后重新进页面**（开关只在 `onAttachedToWindow` 读一次，这是原有行为）。

| # | 去哪测 | 期望 |
|---|---|---|
| 5.1 | 任意视频卡列表（推荐/热门）、设置菜单列表 | `RotaryRecyclerView`：表冠能滚，灵敏度与改动前**一致** |
| 5.2 | 设置主页面、我的页、关于页、登录页这类长页 | `RotaryScrollView`：同上 |
| 5.3 | **视频详情页**、**直播详情页**、消息页 | `RotaryNestedScrollView`：能滚；**滚动后不应抢焦点**（原本就不抢，我保留了） |
| 5.4 | 教程分页页 | 表冠翻页正常（它现在也读同一套开关常量） |

**风险点**：`RotaryNestedScrollView` 原本用 `dispatchGenericMotionEvent` 覆写，我保留了机制没换，但**判定逻辑挪进了 helper**。如果 5.3 手感变了或滚不动，就是这一处，告诉我。

---

## 6. 回归冒烟（不专门测，但顺路看一眼）

| # | 操作 | 为什么 |
|---|---|---|
| 6.1 | 正常**播放一个视频**，看能不能出画 | 基本盘 |
| 6.2 | 视频详情 → **缓存/下载弹幕**，然后播放看弹幕有没有 | **`NetWorkUtil.decompress` 的三个调用方之一**（下载路径） |
| 6.3 | 打开**短视频**页，滑动看弹幕能不能加载 | 另一个 `decompress` 调用方 |
| 6.4 | 我的 → 缓存 → 下载列表进某个本地视频 | 第三个 `decompress` 调用方 |

> 6.2 / 6.3 / 6.4 是解压合并的直接验证面。**如果弹幕变空、下载的弹幕文件是坏的**，立刻告诉我——那说明合并后的解压实现有问题（我把 `end()` 挪进了 `finally`，理论上更安全，但值得确认）。

---

## 7. 出问题时给我什么

只要发我：**哪个页面 + 什么操作 + 现象**，再加一段日志即可。日志这样抓（在你操作的**同时**跑，抓完 Ctrl+C）：

```powershell
& 'D:\Program Files\android-sdk\platform-tools\adb.exe' logcat -v time | Select-String -Pattern 'BiliClient|AndroidRuntime|FATAL|Exception'
```

如果是崩溃，下面这条更直接：

```powershell
& 'D:\Program Files\android-sdk\platform-tools\adb.exe' logcat -b crash -d
```

也可以直接说「你来看」，我可以自己抓日志并定位。

---

## 8. 把新包装到手表

手表连上后：

```powershell
& 'D:\Program Files\android-sdk\platform-tools\adb.exe' devices
# 认出手表的序列号后（假设是 XXXX）：
& 'D:\Program Files\android-sdk\platform-tools\adb.exe' -s XXXX shell getprop ro.product.cpu.abi
# arm64-v8a 用这个：
& 'D:\Program Files\android-sdk\platform-tools\adb.exe' -s XXXX install -r 'D:\Users\ASUS\Desktop\DEVELOP\ReBiliClient\app\build\outputs\apk\debug\app-arm64-v8a-debug.apk'
# armeabi-v7a 用这个：
& 'D:\Program Files\android-sdk\platform-tools\adb.exe' -s XXXX install -r 'D:\Users\ASUS\Desktop\DEVELOP\ReBiliClient\app\build\outputs\apk\debug\app-armeabi-v7a-debug.apk'
```

`-r` 是覆盖安装、保留数据；如果报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（签名不同），那就得先卸载——**卸载会清掉登录态**，先跟我说一声。
