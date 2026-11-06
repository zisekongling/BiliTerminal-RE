# 重复文案报告（同一句话出现在多处）

> 用途：**改一句话之前先查这里**。本表列出的句子在仓库里出现 ≥2 次，改一处不改另一处，用户就会在同一个 App 里遇到两种说法。
> 生成：2026-10-19 · 基于当前工作区
> 检索方式：对 `app/src/main/java`（`.kt` / `.java`）与 `app/src/main/res`（`.xml`）做关键词批量检索，再人工归并。
> **覆盖范围**：第一、二节是人工按 26 组关键词归并的「重点 16 组」（带代码位置和踩坑解释）；**第三节是全量机器扫描**——清单收录的 983 条原文两两比对得 94 组 / 222 条，另加 `strings.xml` 内部（278 条 / 3 组）与 `strings.xml`↔代码（43 处）。

## 〇、三条结论先看

1. **最危险的不是重复本身，是「重复 + 措辞不一致」。** 比如「加载中」有 5 种写法、「操作失败」有 6 种模板——你想统一，得一次改完，否则前后不一更难看。
2. **完全逐字相同的重复有 8 组**（见第一节），这些是「改一处必须全改」的硬要求。
3. **有两类重复是故意的，别去合并**（见第四节）——比如各接口类里各自的 `xxxErrorMsg`。

---

## 一、S 级：逐字完全相同（改一处必须全改）

### `DUP-01` 「啥都木有~」—— 8 处，7 处带波浪号

| 位置 | 形态 |
| --- | --- |
| `app/src/main/res/layout/activity_simple_refresh.xml:261` | `android:text="啥都木有~"` |
| `app/src/main/res/layout/activity_simple_main_refresh.xml:88` | `android:text="啥都木有~"` |
| `app/src/main/res/layout/fragment_simple_refresh.xml:26` | `android:text="啥都木有~"` |
| `app/src/main/res/layout/fragment_simple_list.xml:25` | `android:text="啥都木有~"` |
| `app/src/main/java/…/activity/base/RefreshListActivity.kt:25` | `const val EMPTY_TEXT = "啥都木有~"` |
| `app/src/main/java/…/activity/base/RefreshListActivity.kt:26` | `const val EMPTY_TEXT_WITH_RETRY = "啥都木有~\n点我重试"` |
| `app/src/main/java/…/util/LinkUrlUtil.java:159` | `MsgUtil.showMsg("啥都木有~")` |
| `app/src/main/res/values/strings.xml:200` | `hint_copy_empty` = `啥都木有` ← **唯一没波浪号的** |

> ⚠️ 前 7 处受 `RefreshListActivity` 统一控制（`EMPTY_TEXT` 常量 + 布局默认值），第 8 处 `hint_copy_empty` 是复制失败提示，语义不同但字面几乎一样——改的话要么一起改，要么明确让它保持差异。
> 注意 `DynamicTopicActivity.kt:75` 有注释提到「旧文案『啥都木有~』会让用户以为是自己网络/操作的问题」，说明这个话题已被讨论过，改之前看那处注释。

### `DUP-02` 「上一次的Cookie刷新失败了，\n您可能需要重新登录以进行敏感操作」—— 5 处，标题 4 种

| 位置 | 弹窗标题 |
| --- | --- |
| `app/src/main/java/…/activity/dynamic/send/SendDynamicActivity.kt:205` | `无法发送` |
| `app/src/main/java/…/activity/reply/WriteReplyActivity.kt:190` | `无法发送` |
| `app/src/main/java/…/activity/user/EditProfileActivity.kt:73` | `无法上传` |
| `app/src/main/java/…/activity/user/EditSignActivity.kt:73` | `无法提交` |
| `app/src/main/java/…/activity/user/EditUserInfoActivity.kt:57` | `无法提交` |

> 正文 5 处**逐字相同**（含 `\n`），标题按场景分化是对的。改正文要改 5 处；改标题只需改对应的那几处。
> 相关：`strings.xml` 里**没有**这句，全部硬编码。

### `DUP-03` 「操作失败（错误码 N）」—— 5 个 API 类各一份

| 位置 | 写法 |
| --- | --- |
| `app/src/main/java/…/api/DynamicApi.java:261` | `"操作失败（错误码 " + code + "）"` |
| `app/src/main/java/…/api/FavoriteApi.java:501` | 同上 |
| `app/src/main/java/…/api/FollowApi.java:241` | 同上 |
| `app/src/main/java/…/api/PrivateMsgApi.java:402` | 同上 |
| `app/src/main/java/…/api/ReplyApi.java:454` | 同上 |

> 另有 Kotlin 侧的简写变体：`DynamicHolder.kt:301` 与 `ReplyAdapter.kt:589` 用 `ifEmpty { "操作失败（$code）" }`——**括号里没有「错误码」三个字**，是 B 级不一致（见 `DUP-11`）。

### `DUP-04` 「操作失败：」+ 服务端原因 —— 5 处同构

| 位置 |
| --- |
| `app/src/main/java/…/adapter/dynamic/DynamicHolder.kt:326` |
| `app/src/main/java/…/adapter/article/OpusContentAdapter.kt:256` |
| `app/src/main/java/…/adapter/dynamic/UserDynamicAdapter.kt:340` |
| `app/src/main/java/…/adapter/ReplyAdapter.kt:634` |
| `app/src/main/java/…/activity/video/info/VideoInfoFragment.kt:460` |

> 同一个交互（点赞/投币/收藏）在 5 个页面各写了一遍。另有变体 `UserDynamicAdapter.kt:303`「操作失败（原因未知）：」。

### `DUP-05` 「网络错误，请重试」—— 5 处（登录流程）

| 位置 |
| --- |
| `app/src/main/java/…/activity/settings/login/SMSLoginFragment.kt:169` |
| `app/src/main/java/…/activity/settings/login/SMSLoginFragment.kt:208` |
| `app/src/main/java/…/activity/settings/login/SMSLoginFragment.kt:260` |
| `app/src/main/java/…/activity/settings/login/PasswordLoginFragment.kt:147` |
| `app/src/main/java/…/activity/settings/login/PasswordLoginFragment.kt:197` |

> 都通过 `setStatusText(...)` 显示在登录页状态行。改要 5 处一起改。

### `DUP-06` 「加载失败」—— 5 处 `MsgUtil.showMsgLong`

| 位置 | 场景 |
| --- | --- |
| `app/src/main/java/…/activity/video/TimelineActivity.kt:76` | 时间线 |
| `app/src/main/java/…/activity/base/RefreshMainActivity.kt:166` | 菜单入口列表页 |
| `app/src/main/java/…/activity/base/RefreshListActivity.kt:400` | 返回式列表页 |
| `app/src/main/java/…/activity/search/SearchFragment.kt:149` | 搜索 |
| `app/src/main/java/…/activity/base/RefreshListFragment.kt:123` | 列表 Fragment |

> 另有 2 处「加载失败，请重试」（`activity/message/MessageSettingsActivity.kt:64`、`:72`）、1 处布局写死（`res/layout/activity_short_video_player.xml:50`），以及 `api/UserInfoApi.java:128` 拿「加载失败」当**昵称占位值**（会显示成用户名！改之前想清楚）。

### `DUP-07` 「加载失败：」+ 异常信息 —— 3 处同构

| 位置 |
| --- |
| `app/src/main/java/…/activity/user/CoinLogActivity.kt:64` |
| `app/src/main/java/…/activity/user/ExpLogActivity.kt:60` |
| `app/src/main/java/…/activity/user/LoginRecordActivity.kt:61` |

### `DUP-08` 「未知错误」—— 6 处兜底

| 位置 | 语境 |
| --- | --- |
| `app/src/main/java/…/activity/settings/login/SMSLoginFragment.kt:151` | `optString("message", "未知错误")` |
| `app/src/main/java/…/activity/settings/login/PasswordLoginFragment.kt:129` | 同上 |
| `app/src/main/java/…/activity/settings/login/PasswordLoginFragment.kt:193` | 同上 |
| `app/src/main/java/…/activity/message/MessageSettingsActivity.kt:62` | `optString(…, "未知错误")` |
| `app/src/main/java/…/activity/message/MessageSettingsActivity.kt:165` | 同上 |
| `app/src/main/java/…/api/InteractionVideoApi.java:40` | `optString(…, "未知错误")` |

> 另 `service/DownloadService.kt:417`「下载失败，未知错误」。

---

## 二、A 级：同一个意思，多种说法（想统一就一起改）

### `DUP-09` 「加载中」的 5 种写法

| 写法 | 位置 |
| --- | --- |
| `加载中` | `res/layout/activity_player.xml:476`、`fragment_media_info.xml:54`、`contentDescription`（`activity_loading.xml:46`、`cell_loading.xml:9`） |
| `加载中...`（三个点） | `activity_myspace.xml:89`、`activity_short_video_player.xml:33`、`activity_vip.xml:93/132/171/210/249/288`（共 7 处） |
| `加载中……`（两个省略号字符） | `res/values/strings.xml:230`（`pagename_loading`） |
| `正在加载中...` | `activity_live_info.xml:73`、`cell_article_head.xml:90`、`fragment_video_info.xml:56` |
| `正在加载…` | `RefreshMainActivity.kt:81`、`RefreshListActivity.kt:308`（代码里）+ 布局 `tools:text` |

> 还有语义变体：「还在加载中OvO」（`activity/dynamic/DynamicActivity.kt:112`）、「视频信息还在加载中」（`activity/video/info/VideoInfoFragment.kt:747`、`:785`）、「正在加载验证码...」（`settings/login/CaptchaWebViewActivity.kt:200`、`:216`，在 WebView 注入的 HTML 里）。
> **统一时注意**：`pagename_loading` 是页面标题，`RefreshListActivity` 那两处是列表底部状态条（`loadMoreTip`），语义不同但都出现"加载中"，可以分别处理。

### `DUP-10` 「网络」类提示的 8 种说法

| 写法 | 位置 |
| --- | --- |
| `网络错误(＃°Д°)` | `strings.xml:181`（`err_network`） |
| `检查更新失败：网络错误，请稍后重试` | `strings.xml:53`（`text_update_network_error`） |
| `网络错误，请重试` | 登录 5 处（见 `DUP-05`） |
| `网络错误！\n请检查你的网络连接是否正常` | `activity/video/JumpToPlayerActivity.kt:125` |
| `网络错误：` + e.message | `util/UpdateManager.kt:62` |
| `网络异常，请稍后重试` | `activity/video/HotSearchActivity.kt:46` |
| `网络异常，请检查网络后重试` | `api/NoteApi.java:234` |
| `TV端网络错误，点击重试\n` + e.message | `activity/settings/login/QRLoginFragment.kt:361` |

> 还有「获取二维码失败，网络错误」（`QRLoginFragment.kt:211`）、WebView 里的「验证失败: … 网络错误」（`CaptchaWebViewActivity.kt:266`）。
> **注意**：`err_network` 那句带颜文字 `(＃°Д°)`，是本项目风格的一部分，删掉它是**文风决策**不是笔误修正。

### `DUP-11` 「操作失败」的 6 种模板

| 模板 | 位置 |
| --- | --- |
| `操作失败（错误码 N）` | 5 个 API 类（`DUP-03`） |
| `操作失败（N）` | `DynamicHolder.kt:301`、`ReplyAdapter.kt:589` |
| `操作失败：` + 原因 | 5 处（`DUP-04`） |
| `操作失败（原因未知）：` + 原因 | `adapter/dynamic/UserDynamicAdapter.kt:303` |
| `操作失败` | `adapter/ReplyAdapter.kt:467` |
| `操作失败，请稍后重试` | `activity/video/info/BangumiInfoFragment.kt:569`、`:576` |

### `DUP-12` 「请稍后再试」家族

| 写法 | 位置 |
| --- | --- |
| `请求出错了，请稍后再试` | `api/FavoriteApi.java:497`、`api/FollowApi.java:227`、`api/NoteApi.java:195` |
| `操作过于频繁，请稍后再试` | `api/DynamicApi.java:295`、`api/ReplyApi.java:430` |
| `修改频率过高，请稍后再试` | `activity/user/EditUserInfoActivity.kt:106` |

### `DUP-13` 「没有更多了」家族

| 写法 | 位置 |
| --- | --- |
| `没有更多了` | `util/view/LoadMoreTipController.kt`（26.11.06 起**只剩这一处**：两个列表基类共用同一个控制器，以前 `RefreshMainActivity.kt` / `RefreshListActivity.kt` 各写一遍同样的 when 分支） |
| `正在加载…` | 同上 |
| `暂时没有更多评论了` | `activity/reply/ReplyFragment.kt:265` |
| `没有更多消息了` | `activity/message/PrivateMsgActivity.kt:355` |
| `呐呐？这里没有更多彩蛋了呢＞﹏＜` | `strings.xml:123`（彩蛋数组末尾，见 07） |

### `DUP-14` 「未登录」相关

| 写法 | 位置 |
| --- | --- |
| `请先登录` | `adapter/dynamic/DynamicHolder.kt:618`、`activity/vote/VoteInfoActivity.kt:292` |
| `账号未登录` | `activity/user/EditProfileActivity.kt:144`、`EditSignActivity.kt:99`、`EditUserInfoActivity.kt:103` |
| `未登录，无法追番` | `activity/video/info/BangumiInfoFragment.kt:554` |
| `UID：未登录` | `activity/settings/SettingGroupActivity.kt:233` |
| `我们不对未登录时遇到的问题负责\n——除非它真的经常出现且非常影响使用` | `activity/CatchActivity.kt:137`（崩溃页，风格化长句，别统一掉） |

### `DUP-15` 「下载失败」家族

| 写法 | 位置 |
| --- | --- |
| `下载失败` | `activity/DownloadActivity.kt:149` |
| `下载失败:` / `下载失败：` + code/message | `util/UpdateManager.kt:215`（冒号半角）、`:239`（全角） |
| `下载失败，未知错误` | `service/DownloadService.kt:417` |
| `N 个任务下载失败，请重试` | `service/DownloadService.kt:411` |
| `下载失败：` + error | `activity/settings/UpdateActivity.kt:180` |
| `弹幕下载失败！` | `activity/DownloadActivity.kt:234` |

> 连**冒号都有半角/全角两种**（`UpdateManager.kt:215` vs `:239`），这是最容易漏的。

### `DUP-16` 成功类提示的 4 种语气

| 写法 | 位置 |
| --- | --- |
| `操作成功喵~` | `adapter/dynamic/UserDynamicAdapter.kt:297` |
| `收藏成功~` | `adapter/article/OpusContentAdapter.kt:327` |
| `保存成功` | `activity/user/favorite/FavoriteFolderEditActivity.kt:80` |
| `收藏成功提示`（设置项名，不是提示） | `activity/settings/SettingInfoActivity.kt:22`、`SettingsIndex.kt:146` |

> 前三条是三种语气（卖萌 / 简短 / 正式），**统一它们等于改文风**，先定基调再动手。

---

## 三、跨文件重复（自动扫描的全量结果）

> 这一节是脚本扫出来的**全量**结果，替代人工凭印象的统计。判据：把 7 份清单里全部 983 条「原文」按**逐字相同**分组（忽略载体、行号、前后缀差异），组内 ≥2 条即算重复。
> 一、二两节的 `DUP-01`~`DUP-16` 是其中**值得单独说**的 16 组（带代码位置、带为什么踩坑）；本节给的是**完整名单**，改文案时先来这儿查一眼，能省掉一半返工。

### 三.1 清单内部：94 组、涉及 222 条

| 次数 | 文案 | 涉及编号 |
| ---: | --- | --- |
| 5 | `登录` | A-073 / S-105 / U-057 / U-100 / U-205 |
| 5 | `教程管理` | T-023 / T-038 / S-352 / U-033 / U-092 |
| 4 | `反馈与建议` | A-047 / A-048 / S-356 / U-094 |
| 4 | `还没有登录喵~` | M-094 / M-095 / M-096 / M-101 |
| 4 | `删除成功` | M-057 / M-066 / M-109 / M-115 |
| 4 | `搜索类别排序` | S-287 / U-051 / U-083 / U-254 |
| 3 | `哔哩终端，启动！` | A-031 / S-368 / U-211 |
| 3 | `菜单设置` | S-159 / S-171 / U-081 |
| 3 | `查看预览` | S-116 / S-382 / U-218 |
| 3 | `初次见面……` | A-029 / S-366 / U-087 |
| 3 | `创作中心` | S-200 / S-234 / U-065 |
| 3 | `调整界面……` | A-032 / S-369 / U-088 |
| 3 | `复制` | A-077 / U-064 / U-219 |
| 3 | `关于` | A-005 / S-345 / U-079 |
| 3 | `恢复默认` | S-115 / S-384 / U-213 |
| 3 | `界面大小` | S-120 / S-373 / U-220 |
| 3 | `开源信息` | A-017 / U-019 / U-059 |
| 3 | `评论区设置` | S-167 / S-221 / U-056 |
| 3 | `请选择清晰度` | S-033 / U-050 / U-191 |
| 3 | `设置` | S-001 / S-190 / U-080 |
| 3 | `搜索设置` | S-163 / S-278 / U-082 |
| 3 | `推荐` | S-177 / S-323 / U-038 |
| 3 | `我的反馈` | A-064 / S-358 / U-005 |
| 3 | `我的页面设置` | S-161 / S-191 / U-055 |
| 3 | `详情页设置` | S-165 / S-206 / U-052 |
| 3 | `圆屏适配` | S-118 / S-371 / U-223 |
| 2 | `RE：哔哩终端` | A-001 / L-014 |
| 2 | `版本更新` | U-093 / U-256 |
| 2 | `崩溃时自动把堆栈、机型、最近的页面记录与应用日志（已脱敏）发到自建服务器，便于定位问题；崩溃…` | A-044 / S-363 |
| 2 | `崩溃自动上报` | A-043 / S-362 |
| 2 | `表情` | U-067 / U-208 |
| 2 | `查看你提交过的反馈与开发者的回复` | A-065 / S-359 |
| 2 | `但是我拒绝` | A-076 / U-207 |
| 2 | `登录记录` | S-202 / U-029 |
| 2 | `登录凭证状态` | S-111 / S-113 |
| 2 | `第0/0张` | U-185 / U-252 |
| 2 | `第一个输入框为横向边距，第二个输入框为纵向边距，单位百分比。 出于保护，数值超过30不会保存…` | A-034 / S-381 |
| 2 | `点击跳转` | U-114 / U-242 |
| 2 | `动态` | S-186 / U-003 |
| 2 | `发现新版本` | L-017 / U-229 |
| 2 | `反馈附带账号 ID` | A-045 / S-364 |
| 2 | `公告列表` | S-350 / U-020 |
| 2 | `横向` | S-378 / U-221 |
| 2 | `互动视频变量调试` | U-002 / U-192 |
| 2 | `恢复完成` | S-137 / S-386 |
| 2 | `获取视频信息失败` | M-037 / M-067 |
| 2 | `加载中...` | U-106 / U-158 |
| 2 | `检查更新` | L-016 / S-348 |
| 2 | `教程` | T-017 / U-091 |
| 2 | `解析视频地址……` | U-135 / U-228 |
| 2 | `界面边距` | S-377 / U-224 |
| 2 | `界面边距已更改 可以手动微调喵` | S-138 / S-385 |
| 2 | `界面大小预览` | S-158 / U-086 |
| 2 | `界面设置` | U-085 / U-255 |
| 2 | `经验变化记录` | S-204 / U-039 |
| 2 | `捐赠列表` | A-066 / U-031 |
| 2 | `开启选项后，页面将会修改为圆屏布局。 由于纯靠代码实现，有部分页面可能未适配，但这是目前最好…` | S-119 / S-372 |
| 2 | `历史更新日志` | L-013 / U-026 |
| 2 | `历史记录` | S-199 / U-036 |
| 2 | `每天最多上报一次「随机安装号 + 版本号 + 机型」，用于统计安装量与活跃度。不含账号、Co…` | A-042 / S-361 |
| 2 | `默认关闭。打开后反馈与崩溃报告会附上你的 UID，方便开发者定位你的账号相关问题，代价是上报…` | A-046 / S-365 |
| 2 | `内置播放器设置` | S-035 / U-053 |
| 2 | `你好呀，同在茫茫网络世界中漂流的旅者！这里是哔哩终端的开发团队。感谢你选择了哔哩终端，每增加…` | A-030 / S-367 |
| 2 | `匿名使用统计` | A-041 / S-360 |
| 2 | `偏好设置` | S-228 / U-054 |
| 2 | `确认设置` | S-383 / U-217 |
| 2 | `热搜` | S-182 / U-032 |
| 2 | `啥都木有~` | U-095 / U-099 |
| 2 | `稍后再看` | S-196 / U-017 |
| 2 | `时间线` | S-184 / U-018 |
| 2 | `收藏` | S-197 / U-046 |
| 2 | `首次使用，非常建议调整下界面哦~ 调整下面的选项，然后查看预览 直到对布局满意为止` | A-033 / S-370 |
| 2 | `搜索` | S-185 / U-077 |
| 2 | `特殊登录` | A-070 / U-069 |
| 2 | `图文收藏夹` | U-048 / U-164 |
| 2 | `外观设置` | S-127 / S-139 |
| 2 | `我的` | S-187 / U-073 |
| 2 | `消息` | S-188 / U-072 |
| 2 | `写评论` | U-076 / U-233 |
| 2 | `选择播放器` | S-021 / U-084 |
| 2 | `页面标题` | U-060 / U-257 |
| 2 | `已阅` | T-012 / U-214 |
| 2 | `已在下载队列` | M-019 / M-022 |
| 2 | `硬币变化记录` | S-203 / U-013 |
| 2 | `有效范围为0.25~5.00（倍），建议每次仅进行轻微调节，调节后请预览页面，确认显示正常后…` | S-121 / S-376 |
| 2 | `在这里输入登录信息` | A-072 / U-201 |
| 2 | `粘贴并登录` | A-074 / U-206 |
| 2 | `账号切换` | S-107 / U-070 |
| 2 | `正在加载…` | U-098 / U-258 |
| 2 | `直播列表 / 发动态 / 类型 / 话题广场` | U-136 / U-241 |
| 2 | `直接发送给本项目的开发者（自建通道，无需登录）` | A-049 / S-357 |
| 2 | `转发成功~` | M-007 / M-076 |
| 2 | `追番列表` | S-198 / U-040 |
| 2 | `纵向` | S-379 / U-222 |

编号前缀对照：`A`=01 应用身份 · `L`=02 更新日志 · `T`=03 教程 · `S`=04 设置 · `U`=05 页面与控件 · `M`=06 消息与提示 · `E`=07 彩蛋。

几条读法：

- **跨清单的 3~5 处重复基本都是「同一个名字在多个入口复用」**，如 `教程管理`（设置首页、教程管理页标题、我的页面、设置索引、布局各一份）。这类只改「页面标题」会漏掉设置索引和菜单名——`A-073` 的 `登录` 同理。
- **同一份清单内部的重复**（如 `M-057`/`M-066`/`M-109`/`M-115` 的「删除成功」）说明同一句提示在多个位置各写一遍，改一处就会立刻不一致。
- **`S-xxx` 与 `U-xxx` 成对出现的最多**：`S` 是设置页里的那一行（含副标题），`U` 是布局/菜单/页面标题里的同一句。改设置项名称时必须两边一起改，否则设置页叫「X」、标题栏叫「Y」。

### 三.2 `strings.xml` 内部：278 条里只有 3 组重复

这一条纠正了 `六、已知未覆盖` 里留下的担心——`strings.xml` 内部**几乎没有重复**，只有这 3 组（值相同、键不同）：

| 文案 | 重复的键 |
| --- | --- |
| `反馈与建议` | `pagename_feedback` · `feedback_entry_title` |
| `复制` | `pagename_copy` · `btn_login_copy` |
| `表情` | `btn_emote` · `pagename_emote` |

前两组是**页面标题**与**功能名**的撞车（`pagename_*` 和 `btn_*` 各存一份），第三组 `表情` 是同一个词的两种用途。改的时候按用途分别判断，别硬合并成一个键。

### 三.3 `strings.xml` ↔ 代码：43 处「同一句话两边都有」

真正的重复大头在这里。代码里带中文的字符串字面量共 **2991 条**，其中 **43 条**与 `strings.xml` 里的某个值逐字相同——也就是同一句话既有资源键、又在代码里硬编码。改资源键**不会**改到硬编码那份，反之亦然。

| 代码处数 | 文案 | 资源键 | 代码位置（前几处） |
| ---: | --- | --- | --- |
| 5 | `下载完成` | `text_update_download_complete` | UpdateActivity.kt:170<br>DownloadActivity.kt:170<br>DownloadNotifier.kt:63<br>DownloadService.kt:667 |
| 5 | `登录` | `btn_login_confirm` | LoginActivity.kt:19<br>SettingGroupActivity.kt:194<br>SettingsIndex.kt:48<br>SettingsIndex.kt:48 |
| 3 | `创作中心` | `pagename_creative_center` | SettingPrefActivity.kt:30<br>SettingsIndex.kt:153<br>MySpaceMenu.kt:25 |
| 3 | `界面大小` | `section_ui_size` | SettingGroupActivity.kt:260<br>SettingsIndex.kt:108<br>SettingsIndex.kt:108 |
| 3 | `圆屏适配` | `section_ui_round` | SettingGroupActivity.kt:254<br>SettingsIndex.kt:107<br>SettingsIndex.kt:107 |
| 3 | `仅音频` | `cache_quality_audio_only` | LocalListActivity.kt:178<br>LocalListActivity.kt:223<br>QualityChooserActivity.kt:90 |
| 3 | `教程管理` | `pagename_tutorial_manager` | SettingGroupActivity.kt:533<br>SettingsIndex.kt:102<br>TutorialManagerActivity.kt:39 |
| 2 | `分片数` | `aria2_split` | SettingsIndex.kt:124<br>SettingsIndex.kt:124 |
| 2 | `账号切换` | `pagename_account_switch` | SettingGroupActivity.kt:205<br>SettingsIndex.kt:49 |
| 2 | `写评论` | `pagename_write_reply` | ReplyFragment.kt:288<br>ReplyAdapter.kt:620 |
| 2 | `菜单设置` | `pagename_setting_menu` | SettingGroupActivity.kt:395<br>SettingsIndex.kt:53 |
| 2 | `我的` | `pagename_myspace` | QRLoginHelpActivity.kt:48<br>MenuActivity.kt:56 |
| 2 | `关于` | `pagename_about` | SettingGroupActivity.kt:517<br>SettingsIndex.kt:71 |
| 2 | `搜索设置` | `pagename_setting_search` | SettingGroupActivity.kt:401<br>SettingsIndex.kt:59 |
| 2 | `启用快捷缓存方式` | `cache_quick_mode` | SettingsIndex.kt:125<br>SettingsIndex.kt:125 |
| 2 | `后台自动检查更新` | `text_update_background_check` | SettingPrefActivity.kt:53<br>SettingsIndex.kt:154 |
| 2 | `温馨提醒` | `donate_title` | AppInfoApi.java:56<br>AppInfoApi.java:59 |
| 2 | `崩溃自动上报` | `setting_crash_auto` | SettingsIndex.kt:96<br>SettingsIndex.kt:97 |
| 2 | `匿名使用统计` | `setting_telemetry` | SettingsIndex.kt:93<br>SettingsIndex.kt:94 |
| 2 | `反馈附带账号 ID` | `setting_feedback_attach_mid` | SettingsIndex.kt:99<br>SettingsIndex.kt:100 |
| 2 | `正在发送…` | `feedback_sending` | FeedbackDetailActivity.kt:187<br>WriteReplyActivity.kt:158 |
| 2 | `启用高速下载模式` | `aria2_enable` | SettingsIndex.kt:121<br>SettingsIndex.kt:121 |
| 2 | `发送` | `btn_send` | FeedbackDetailActivity.kt:173<br>TerminalDialog.kt:232 |
| 2 | `我的反馈` | `feedback_mine_entry_title` | FeedbackListActivity.kt:50<br>SettingsIndex.kt:90 |
| 1 | `查看预览` | `btn_preview` | SettingGroupActivity.kt:249 |
| 1 | `直接发送给本项目的开发者（自建通道，无需登录）` | `feedback_entry_desc` | SettingsIndex.kt:86 |
| 1 | `查看你提交过的反馈与开发者的回复` | `feedback_mine_entry_desc` | SettingsIndex.kt:90 |
| 1 | `反馈与建议` | `pagename_feedback` | SettingsIndex.kt:86 |
| 1 | `反馈与建议` | `feedback_entry_title` | SettingsIndex.kt:86 |
| 1 | `数据解析错误` | `err_json` | VoteInfoActivity.kt:131 |
| 1 | `恢复默认` | `btn_reset` | SettingGroupActivity.kt:240 |
| 1 | `已阅` | `btn_read` | TutorialActivity.kt:57 |
| 1 | `准备下载...` | `text_update_download_preparing` | UpdateActivity.kt:150 |
| 1 | `开源信息` | `pagename_opensource` | OpenSourceActivity.kt:24 |
| 1 | `正在检查更新...` | `text_update_checking` | UpdateActivity.kt:93 |
| 1 | `当前已是最新版` | `text_update_latest` | UpdateActivity.kt:105 |
| 1 | `设置` | `pagename_setting_main` | MenuActivity.kt:59 |
| 1 | `搜索类别排序` | `pagename_setting_search_sort` | SearchSortActivity.kt:43 |
| 1 | `选择播放器` | `pagename_setting_player_choose` | SettingsIndex.kt:39 |
| 1 | `搜索` | `pagename_search` | MenuActivity.kt:54 |
| 1 | `发现新版本` | `text_new_version_released` | UpdateActivity.kt:121 |
| 1 | `图片查看` | `pagename_image_viewer` | ImageViewerActivity.kt:70 |
| 1 | `消息` | `pagename_message` | MenuActivity.kt:57 |

最高频的几处值得单独提醒：

- `下载完成`（5 处代码 + 1 个资源键）：`UpdateActivity.kt:170`、`DownloadActivity.kt:170`、`DownloadNotifier.kt:63`、`DownloadService.kt:667`。通知栏、下载页、更新页三处的「下载完成」其实是三份。
- `登录`（4 处代码 + `btn_login_confirm`）：`LoginActivity.kt:19`、`SettingGroupActivity.kt:194`、`SettingsIndex.kt:48`。按钮、设置索引、账号入口共用这两个字。
- `SettingsIndex.kt` **自己内部**就有重复：`分片数`、`启用快捷缓存方式`、`界面大小`、`圆屏适配`、`崩溃自动上报` 都在同一文件的相邻两行各写一遍。

### 三.4 一句话建议

改任何一条**短文案**（尤其 2~4 字的按钮名、页面名、设置项名）之前，先在 `三.1` 的表里搜一下这个词：只要有 ≥2 个编号，就按「全部一起改」对待，别只改清单里那一条——那些编号往往就是其他写法的所在。

## 四、刻意允许的重复（别去合并）

| 现象 | 为什么不合并 |
| --- | --- |
| 各接口类各有一份 `xxxErrorMsg(code)`（`DynamicApi` / `FavoriteApi` / `FollowApi` / `ReplyApi` / `NoteApi` / `PrivateMsgApi`） | 各接口错误码语义不同（`AGENTS.md` 记录的 `-101` / `-111` / `79502` 等），合并会丢失上下文；但**字符串模板相同是可以统一改的**（`DUP-03`） |
| `RefreshMainActivity` 与 `RefreshListActivity` 各有一份「正在加载…／没有更多了」的 `when` | 两个基类没有继承关系（`RefreshMainActivity` 不能继承 `RefreshListActivity`，见 `AGENTS.md` 的 `BaseActivity.kt:282` 向下判断坑），所以是**被迫重复**——改要改两处，但不能为省事去改继承关系 |
| `啥都木有~` 出现在 4 个布局 + 1 个常量 | 布局里的值是「预览/兜底」，运行时被 `RefreshListActivity` 覆盖；两处都要改，但布局那份是给 Android Studio 预览看的 |
| 崩溃页 `CatchActivity.kt:137` 的长句 | 刻意的风格化文案（带破折号），统一掉会失去个性 |

---

## 五、怎么自己复查（改完用）

```bash
# 1. 查某句还有没有漏网（把「要改的原文」换进去）
grep -rn "要改的原文" app/src/main/java app/src/main/res

# 2. 查半角/全角冒号混用
grep -rn "下载失败:" app/src/main/java

# 3. 查「加载中」全部写法
grep -rn "加载中" app/src/main/java app/src/main/res

# 4. 查所有 xxErrorMsg 模板
grep -rn "操作失败（错误码" app/src/main/java
```

> `AGENTS.md` 提醒：本机读中文文本别用 `Get-Content`（乱码会导致 `-match` 全失配）。上面的 `grep` 指 Git Bash / WSL / IDE 内置搜索，不是 PowerShell。

---

## 六、已知未覆盖

- 第三节的全量比对只覆盖**清单里收录的 983 条原文**（按「逐字相同」分组）。**近似同义但用词不同的**（如「加载中」家族）只由第二节人工归并，仍可能漏。
- `strings.xml` 内部已扫（278 条 / 3 组）；`strings.xml`↔代码按逐字相同扫出 43 处，**近似写法未纳入**。
- `bilibili-API/` 文档目录、`docs/` 自身未扫。
- 布局里 `tools:text` 的预览值与 `android:text` 实际值是否一致未核对。
- 代码里 2991 条中文字面量中，**只在代码内部重复、且不与 `strings.xml` 撞车**的那些未纳入（量大，与「改一次文案」关系较弱）。

如需刷新第三节，按 `五、怎么自己复查` 重跑脚本即可。

**发现新的重复组，直接在本文件追加一节，编号接着 `DUP-17` 往下排。**
