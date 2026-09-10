# 已实现功能总结文档

- 日期：2026-09-08
- 项目：ReBiliClient
- 说明：本文档汇总四份已完成落地并核对通过的功能设计，原设计文档已归档删除。

---

## 1. 动态投票功能（发起 + 展示 + 参与）

### 功能范围
B 站动态投票完整闭环：发起投票、动态流展示投票卡片、点击参与投票。先做文字投票 + 单选，接口通过浏览器抓包补齐 do_vote。

### 接口设计
| 接口 | URL | 说明 |
|---|---|---|
| create_vote | POST api.vc.bilibili.com/vote_svr/v1/vote_svr/create_vote | multipart/form-data，返回 vote_id |
| do_vote | POST api.bilibili.com/x/vote/do_vote?csrf=... | JSON body，响应为 base64 编码 JSON |
| vote_info | GET api.bilibili.com/x/vote/vote_info?vote_id=... | 返回 options（opt_idx 从 1 开始）、my_votes 等 |
| followee_votes | GET api.vc.bilibili.com/vote_svr/v1/vote_svr/followee_votes | 关注的人投票 |

### 实现落点
| 层 | 文件 | 关键内容 |
|---|---|---|
| API | api/VoteApi.java | createVote() / doVote() / getVoteInfo() / parseVoteInfo()；createVote 用 setAutoAddAccessKey(false) 规避风控 |
| 模型 | model/VoteInfo.java、VoteOption.java、VoteDraft.java | 含 isExpired()/hasVoted()/isValid() 辅助方法 |
| 解析 | api/DynamicApi.java | analyzeDynamic 第 577-579 行处理 ADDITIONAL_TYPE_VOTE |
| 渲染 | adapter/dynamic/DynamicHolder.kt | showVoteCard() / createVoteOptionView() / fetchFullVoteInfo()，支持单选/多选、已投锁定、过期禁用 |
| 编辑 | activity/dynamic/send/SendDynamicActivity.kt | collectVoteDraft() + putExtra("voteDraft", ...) |
| 发布 | activity/dynamic/DynamicActivity.kt | 第 100-123 行：接收 Draft → createVote → 挂载 {"vote":{"vote_id":...}} |

### 关键边界
- 未登录提示，csrf 失效（-111）提示重新登录。
- do_vote 响应 base64 解码，解码失败按空处理。
- 选项索引 opt_idx 从 1 开始（创建时从 0 开始，需转换）。
- 投票结束（status != 1 或 end_time 过期）禁用按钮。

---

## 2. 选择收藏夹页显示数量/上限

### 功能范围
视频详情点收藏弹出的"选择收藏夹"页，每个收藏夹显示数量/上限，与"我的-收藏夹"页一致。

### 核心决策
- 上限不依赖接口：默认收藏夹 50000，自建 1000。
- 默认收藏夹识别：index == 0。
- 数据来源：现有 list-all 接口的 media_count 字段，无需新增请求。
- 不改共享布局 cell_choose.xml，新建 cell_folder_choose.xml。

### 实现落点
| 文件 | 改动 |
|---|---|
| api/FavoriteApi.java | getFavoriteState / parseFavoriteState 新增 countList、maxCountList 参数，解析 media_count，上限按 index 填 50000/1000 |
| res/layout/cell_folder_choose.xml | 新建，名称 + 数量/上限 两 TextView |
| adapter/favorite/FolderChooseAdapter.kt | 新布局 + 数量文本渲染 + 越界保护 |
| activity/video/info/AddFavoriteActivity.kt | 新增两个 List 并传入 |

---

## 3. 菜单设置界面重构（已启用/未启用双分区）

### 功能范围
菜单设置页上下分区：上方"已启用"（可拖拽排序/禁用），下方"未启用"（点击启用），删除菜单"退出"按钮。推荐/设置/缓存/搜索四项固定不可隐藏。

### 核心决策
- 单一数据源 menu_enabled（分号连接有序已启用 key 列表），替代旧 MENU_SORT + 6 个 menu_* 布尔，旧数据自动迁移。
- 移入未启用手势：拖拽已启用项到未启用区域。

### 菜单项
| 固定项 | 可禁用项 |
|---|---|
| recommend / search / local / settings | short_video / popular / precious / ranking / hotsearch / live / timeline / dynamic / myspace / message |

默认启用：recommend, short_video, popular, hotsearch, search, dynamic, myspace, message, local, settings。
默认未启用：precious, ranking, live, timeline。

### 实现落点
| 文件 | 改动 |
|---|---|
| util/MenuConfig.kt | 纯 Kotlin：ALL_ITEMS / DEFAULT_ENABLED / FIXED_ITEMS、resolveEnabledList() 迁移校验、serialize() / parse()、disabledFrom() |
| activity/settings/SettingMenuActivity.kt | 重写为单竖向 RecyclerView 双分区 |
| adapter/MenuSettingAdapter.kt | 多 view 类型 |
| activity/MenuActivity.kt | 读 menu_enabled，删除 exit 按钮 |
| activity/SplashActivity.kt | 读 menu_enabled 首项决定直达页 |
| SortSettingActivity.kt | 删除 |

### 测试
app/src/test/ 新增 MenuConfigTest 纯 JVM 单测。

---

## 4. 热搜 / 编辑个人资料 / 隐私模式

### 功能一：热搜
独立页面 + 旧版/新版菜单入口。

| 文件 | 说明 |
|---|---|
| api/HotSearchApi.kt | getHotSearch |
| model/HotSearchCard.kt | 数据模型 |
| adapter/video/HotSearchAdapter.kt | 列表适配器 |
| activity/video/HotSearchActivity.kt | 热搜页 |

### 功能二：编辑个人资料
枢纽页结构：

| 文件 | 说明 |
|---|---|
| activity/user/EditProfileActivity.kt | 头像上传枢纽 |
| activity/user/EditUserInfoActivity.kt | 昵称/生日/性别 |

头像选择用 ACTION_PICK，沿用 BiliTerminal 错误码文案映射。

### 功能三：隐私模式
完整复刻 BiliTerminal 行为：

| 行为 | 说明 |
|---|---|
| 设置开关 | SharedPreferencesUtil.PRIVACY_MODE + SettingPrefActivity 开关项 |
| 不记录观看历史 | 隐私模式下跳过历史记录上报 |
| 视频详情走游客 Cookie | 隐藏点赞/收藏状态 |
| 播放仍用登录 Cookie | 保持高清 |

---

## 5.「我的」页面入口配置化（2026-09-10）

### 功能范围
「我的」页面的功能入口支持排序与分区：用户卡片固定第一，「更多」与「退出登录」固定最后两位，
其余入口都能自由排序，也可以移入「更多」列表；「更多」列表自身也能排序。
更多列表为空时，页面上不显示「更多」按钮。

### 核心决策
- 配置拆成两份有序 key 串 `myspace_main` / `myspace_more`（`;` 连接）：互斥且并集 = 全部可配置项；
  任一非法（未知 key、重复、漏项、跨列表重复）整体回退默认并写回，避免半截配置弄丢入口。
- 固定项不入配置：用户卡片固定顶部且设置页里不出现，`more` / `logout` 永远最后两位。
- 入口定义（图标 / 文案 / 跳转）抽到 `MySpaceMenu`，主列表与更多页共用，避免跳转逻辑写两遍。
- 创作中心仍受 `creative_enable` 开关控制：设置页里始终可排序，页面按开关决定是否渲染。
- 交互与菜单设置一致（单页双分区 + 长按拖拽），差别是更多区自身也可拖拽排序（菜单设置的未启用区不可拖）。

### 实现落点
| 文件 | 改动 |
|---|---|
| util/MySpaceConfig.kt | 新建：ALL_ITEMS、Layout、parseList / resolve / load / save（纯 Kotlin，无 Android 依赖） |
| util/SharedPreferencesUtil.java | 新增 loadMySpaceLayout() / saveMySpaceLayout() |
| activity/user/MySpaceMenu.kt | 新建：key → 图标 / 文案 / 跳转 |
| activity/user/MySpaceActivity.kt | 按配置渲染；移除「个人信息」「修改个人描述」两组与卡片重复的入口 |
| activity/user/MySpaceMoreActivity.kt | 新建：更多列表页（复用 activity_myspace.xml，隐藏用户卡片） |
| adapter/MySpaceSettingAdapter.kt | 新建：双分区拖拽，两区可互相拖入 |
| activity/settings/SettingMySpaceActivity.kt | 新建：设置入口（复用 activity_setting_menu.xml） |
| settings/SettingGroupActivity.kt、settings/SettingsIndex.kt、AndroidManifest.xml | 注册入口与索引 |

### 测试
`app/src/test/.../MySpaceConfigTest.kt`：解析 / 回退 / 往返，以及 `ALL_ITEMS` 与 `MySpaceMenu.ITEMS` 的 key 一致性守卫。

---

## 实现核对结论（2026-09-08）

四份设计文档描述的功能全部已实现，代码与文档高度一致。仅存在以下过时/偏差（已在原文档中修正）：
- dynamic-vote-design 原写"没有 MVVM 新层"，实际已引入 Hilt/Retrofit 新层。
- dynamic-vote-design 原写"实现待编码"，实际已全链路落地。
- SendDynamicActivity 实际位于 activity/dynamic/send/ 子包，非根目录。
- createVote 实现新增 setAutoAddAccessKey(false) 风控规避。
