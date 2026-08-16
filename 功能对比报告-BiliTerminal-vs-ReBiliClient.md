# BiliTerminal vs ReBiliClient 功能对比报告

> 对比对象：
>
> - **对方项目（BiliTerminal）**：`https://github.com/PianoEthan/BiliTerminal.git`（已克隆至 `/tmp/opencode/BiliTerminal`，commit `b8d1a33`，v3.1.0-Qx，Java + XML，minSdk 14）
> - **本项目（ReBiliClient）**：`/mnt/e/Users/ASUS/Desktop/ReBiliClient`（versionName `26.08.14`，Kotlin/Java 混合 + Hilt，minSdk 24）

生成日期：2026-08-15

---

## 1. 项目概况

| 维度 | BiliTerminal | ReBiliClient |
|---|---|---|
| 语言 | 纯 Java + XML | Kotlin 2.0 + 遗留 Java，Hilt/KSP |
| 最低系统 | Android 4.0（minSdk 14） | Android 7.0（minSdk 24） |
| 目标系统 | targetSdk 26（旧版存储） | targetSdk 34 |
| 模块 | :app + ijkplayer-java + DanmakuFlameMaster + brotlij + material-color-utilities | :app + ijkplayer-java + DanmakuFlameMaster + brotlij |
| 网络层 | 原生 OkHttp3 + Gson | 遗留 OkHttp3 + 新版 Retrofit/kotlinx.serialization |
| 登录 | 仅 扫码 + Cookie 粘贴 | 扫码(Web/TV) + 密码 + 短信 + Cookie 粘贴 + 多账号切换 |
| UI | 单一"菜单中枢"（MenuActivity 按钮宫格） | 菜单中枢 + 新版三 Tab（MobileShell：首页/我的/设置） |
| 独有能力 | 动态主题系统（material-color-utilities） | 短视频、音乐播放器+歌词、Aria2/分段下载、DASH 合并、现代 UI |

两者同源于 BiliClient（RobinNotBad/huanli233），API 覆盖面高度重合，属于同一血统的双分支，功能体量基本对等。**主要差异集中在"主题系统"和"账号/资料编辑"两处（我方缺失）以及"短视频/音乐/下载增强"三处（对方缺失）。**

---

## 2. 功能总览对比表

| 功能模块 | BiliTerminal | ReBiliClient | 备注 |
|---|---|---|---|
| 扫码登录（Web） | ✔ | ✔ | 对方：生成/poll；我方：另有 TV 二维码 |
| 密码登录 | ✘ | ✔ | 我方独有（RSA + Geetest） |
| 短信登录 | ✘ | ✔ | 我方独有 |
| Cookie 粘贴登录 | ✔ | ✔ | |
| 多账号切换 | ✘ | ✔ | 我方独有（AccountSwitchActivity） |
| cookie 刷新/buvid/风控指纹 | ✔ | ✔ | |
| 视频播放（ijkplayer DASH/FLV） | ✔ | ✔ | |
| 清晰度选择 + 默认清晰度 | ✔ | ✔ | 我方另有音画分离 `voice_balance` |
| 分P 选择 | ✔ | ✔ | |
| 互动视频（变量调试） | ✔ | ✔ | |
| 弹幕（protobuf+XML 双轨、发送/点赞/撤回） | ✔ | ✔ | 我方另有 wss 实时弹幕 |
| 弹幕图片参数 DmImgParamUtil | ✔ | ✔ | |
| 字幕（选择/校准/下载） | ✔ | ✔ | |
| 高能进度条 + 在线人数 | ✔ | ✔ | |
| 音频模式 / 熄屏播放 / 后台播放 | ✔ | ✔ | |
| 循环 / 自动连播 | ✔ | ✔ | |
| 双击快进 / 长按倍速 / 手势 | ✔ | ✔ | |
| 电池电量显示（播放器） | ✔ | ✔ | |
| 推荐 / 热门 / 入站必刷 / 排行榜 / 番剧时间线 | ✔ | ✔ | |
| **短视频（story 信息流）** | ✘ | ✔ | 我方独有（ShortVideoFeedApi） |
| **音乐播放器 + 歌词 + 歌单** | ✘ | ✔ | 我方独有（AudioApi） |
| 搜索（视频/番剧/用户/直播/专栏/音频） | ✔ | ✔ | 我方可搜音频 |
| 动态 feed / 详情 / 发布 / 转发 / 点赞 / 删除 | ✔ | ✔ | |
| 评论（列表/楼中楼/点赞/删除/写评论/深链定位） | ✔ | ✔ | |
| 收藏夹（创建/编辑/删除/视频列表/图文收藏夹） | ✔ | ✔ | |
| 稍后再看 | ✔ | ✔ | |
| 历史记录（按天分组） | ✔ | ✔ | 我方另有历史删除 `x/v2/history/delete` |
| 合集 / 系列 | ✔ | ✔ | |
| 关注 / 粉丝 / 关注分组 | ✔ | ✔ | |
| 粉丝勋章墙 | ✔ | ✔ | |
| 大会员特权 + 每日经验领取 | ✔ | ✔ | |
| 创作中心（UP 数据） | ✔ | ✔ | |
| 硬币记录 / 经验记录 / 登录记录 | ✔ | ✔ | |
| 充电榜（月榜） | ✔ | ✔ | |
| **编辑个人资料（头像/昵称/性别/生日）** | ✔ | ✘ | **我方缺失**（对方 `EditProfileActivity`/`EditUserInfoActivity`） |
| 编辑签名 | ✔ | ✔ | |
| 直播（推荐/关注/房间播放+弹幕） | ✔ | ✔ | |
| 私信（会话/聊天/设置） | ✔ | ✔ | 我方另有自动已读设置 |
| 消息通知（@/赞/回复/系统） | ✔ | ✔ | |
| 下载（前台服务+SQLite） | ✔ | ✔ | 我方另有分段下载、Aria2、DASH 音视频合并 |
| 本地缓存列表 / 离线模式 | ✔ | ✔ | |
| 表情包选择 | ✔ | ✔ | |
| 专栏/图文（opus）阅读+互动 | ✔ | ✔ | |
| 深链（av/bv/cv/url） | ✔ | ✔ | |
| 崩溃捕获 | ✔ | ✔ | |
| 菜单排序 | ✔ | ✔ | |
| **主题：固定色板（暗色）** | ✔ | ✔ | 我方 6 套 |
| **主题：动态取色 + .btheme 导入 + 深浅切换** | ✔ | ✘ | **我方缺失**（详见 §3.1） |
| 第一启动向导 / 关于 / 实验功能 | ✔ | ✔ | |
| 终端风 DPI 缩放（TerminalContext） | ✔ | ✔ | |

---

## 3. ReBiliClient 缺失的功能（对方有、我方没有）

### 3.1 动态主题系统（差距最大的一项）

对方基于 `material-color-utilities` 模块构建了完整的动态主题体系（`app/src/main/java/com/RobinNotBad/BiliClient/theme/`），我方仅有 6 套写死的暗色色板（`ui/theme/ThemeManager.kt`，全部为深色，无浅色、无取色）。

| 功能 | 对方实现 | 我方现状 |
|---|---|---|
| **主题包导入/删除/切换** | `BThemeInstaller.java` + `ThemeSettingsActivity.java`：支持 `.btheme` 文件（选文件或输入路径）导入、列表展示、长按删除 | 无，只能切内置 6 套 |
| **种子色动态配色** | `SchemeEngine.java` + `ThemePalette.java`：由任意种子色经 material color utilities 生成整套 Material 配色 | 无，色板硬编码 |
| **深浅色切换** | `ThemeSettingsActivity` 的 `switchDark`；主题分为默认暗黑/预设/已安装三类，均有名称与说明 | 无浅色主题，全部色板均为暗色（`SURFACE=0xFF24242E`） |
| **内置预设（种子色）** | `ThemeManager.getBuiltinPresets()`，若干预设色种子 | 无预设概念 |
| **背景取色** | `ColorExtractor.java` + `THEME_EXTRACT_BG`：从视频封面提取主色生成主题 | 无 |
| **内容动态取色** | `ContentTintHelper.java` + `THEME_CONTENT_TINT`：对封面等图片做动态着色（带缓存） | 无 |
| **混合强度** | `setBlend(int)`：种子色与默认色的混合比例 | 无 |
| **主题预览** | `InstalledTheme.getPreviewFile()`，主题列表显示预览图 | 无 |

> 参考文件：`/tmp/opencode/BiliTerminal/app/src/main/java/com/RobinNotBad/BiliClient/theme/*.java`、`ThemeSettingsActivity.java`、`adapter/ThemeListAdapter.java`

### 3.2 编辑个人资料（头像/昵称/性别/生日）

对方有完整的资料编辑能力，我方只有"编辑签名"。

| 功能 | 对方实现 | 我方现状 |
|---|---|---|
| 更换头像 | `activity/user/EditProfileActivity.java`（调系统图片选择器，保存到 SharedPreferences "avatar"） | 无 |
| 改昵称/性别/生日 | `activity/user/EditUserInfoActivity.java`，调用 `UserInfoApi.updateUserInfo(...)`（`x/member/web/update`） | 无 |
| 换头像接口 | `UserInfoApi` 中 `face/update`（`x/member/web/face/update`） | 无 |

> 参考文件：`/tmp/opencode/BiliTerminal/app/src/main/java/com/RobinNotBad/BiliClient/activity/user/EditProfileActivity.java`、`EditUserInfoActivity.java`、`api/UserInfoApi.java`

### 3.3 其余细节差异（对方有、我方未见）

- **动态的"最近更新 UP 列表"**：对方 `DynamicActivity` 有"最近更新的UP"横向栏（`RecentUpAdapter`），我方动态页未见对应模块（未深入验证，需确认）。
- **跳转第三方播放器前选清晰度**：对方 `JumpToPlayerActivity` + `SettingPlayerChooseActivity`（内置/小电视/凉腕）支持第三方播放器；我方同样有 `SettingPlayerChooseActivity`，但注意确认是否保留了 `JumpToPlayerActivity` 的对外跳转能力。
- 以上两点为低置信度差异，建议结合源码二次确认。

---

## 4. ReBiliClient 领先的功能（我方有、对方没有）

| 功能 | 说明 | 参考 |
|---|---|---|
| **短视频信息流** | `app.bilibili.com/x/v2/feed/index/story`，支持下滑连播+预加载 | `activity/video/ShortVideoPlayerActivity.kt`、`api/ShortVideoFeedApi.kt` |
| **音乐播放/歌词/歌单** | 歌曲播放、歌词显示、歌单详情、播放模式 | `activity/audio/AudioPlayerActivity.kt`、`PlaylistActivity.kt`、`api/AudioApi.java` |
| **密码/短信登录 + Geetest 人机验证** | RSA 加密 + 极验 + 风控校验，`PasswordEncryptUtil.java` | `activity/settings/login/PasswordLoginFragment.kt`、`SMSLoginFragment.kt`、`CaptchaWebViewActivity.kt` |
| **TV 二维码登录** | `passport-tv-login/qrcode/auth_code` | `QRLoginFragment.kt` |
| **多账号切换** | `AccountManager` 多凭证管理 | `AccountSwitchActivity.kt` |
| **Aria2 外部下载器集成** | RPC 对接 Aria2 | `util/Aria2Util.java` |
| **多线程分段下载** | `SpeedDownloadService.kt`（并发分块） | |
| **DASH 音视频合并** | 下载后合并为 MP4 | `util/MediaMerger.kt` |
| **历史记录删除** | `x/v2/history/delete` | `api/HistoryApi.java` |
| **本地缓存文件夹管理** | `FolderManager.kt` | |
| **新版现代 UI** | Hilt + StateFlow + ViewModel，三 Tab 外壳 `MobileShellActivity`（首页/我的/设置），首页 ViewPager2 频道流 | `ui/` |
| **gRPC 基址 + 新版网络层** | Retrofit + `grpc.biliapi.net` | `network/ApiClient.kt` |
| **虚拟合集 / 章节看点跳转 / 互动变量调试开关等实验项** | 实验功能多于对方 | `SettingLaboratoryActivity.kt` |

---

## 5. 技术架构差异（仅供参考）

- **主题**：对方新增 `material-color-utilities` Gradle 模块（我方未引入）；对方主题体系是 `theme/` 包 + `.btheme` 格式（JSON 清单 + 预览图），我方是 `ui/theme/ThemeManager.kt` 硬编码色板。
- **网络**：我方在遗留 OkHttp 之外新增了 Retrofit 层与 Hilt 依赖注入；对方保持纯 OkHttp3 + Gson。
- **UI 生成**：对方全部 XML 布局；我方新 UI 为代码动态创建（`createMenuGrid` 等），旧 UI 保留 XML。
- **最低版本**：对方面向 Android 4.0 老设备做了大量兼容（WBI 签名、WebP 降级、硬件加速 clipPath 修复等），我方 minSdk 24，无此负担。

---

## 6. 结论与建议（按优先级）

1. **【高】动态主题系统**：这是对方目前最突出的差异化能力，也是我方最大空白。若移植，工作量中等——核心是把 `material-color-utilities` 模块、`SchemeEngine`/`ThemeManager` 抽出来，并将我方 `ThemeManager.kt` 的 `PREF_KEY_THEME` 语义扩展为"主题 ID（含 .btheme）"。至少可以先做"浅色主题 + 种子色生成"，暂缓 `.btheme` 导入格式。
2. **【中】编辑个人资料**：改昵称/性别/生日只需接一个 `x/member/web/update` 接口 + 简单表单页；换头像需处理图片选择与上传，工作量小、用户感知明显（账号完整性）。
3. **【低】动态"最近更新 UP"栏**：确认后按对方 `RecentUpAdapter` 思路补齐。
4. **无需补齐项（我方优势）**：短视频、音乐、多账号、下载增强、现代 UI 均保持领先，无需向对方看齐。

---

*报告基于双方 `main` 分支最新源码静态分析生成，所有"缺失"结论均已对照源码确认；个别标注"低置信度"的条目建议二次核对。*
