# 用户空间与收藏夹源码审查（极细颗粒度）

> 审查对象：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/` 及 `user/info/`、`user/favorite/` 下共 24 个文件。
> 为完整覆盖"功能-用法"，本审查同时读取了相关 Adapter（`UserDynamicAdapter`、`DynamicHolder`、`VideoCardAdapter`/`VideoCardHolder`、`FavoriteFolderAdapter`、`UserFavoriteFolderAdapter`、`UserListAdapter`、`FollowGroupAdapter`、`MedalListAdapter`、`ArticleCardAdapter`、`OpusAdapter`、`HistoryVideoCardAdapter`、`CoinLogAdapter`）以及 `FavoriteApi.java` 的关键方法签名，因为这些页面的大量交互（关注/取关、私信、分享、点赞、长按删除、进入详情等）实际实现在 Adapter 层。
> 每一条均以「功能 → 具体用法/触发方式」列出。

---

## MySpaceActivity.kt（我的空间 / 个人中心）

- 功能入口整体数据驱动渲染 → 进入页面后在 `asyncInflate` 回调里调用 `addMenuItems()` 逐个把 `buildMenuItems()` 生成的条目 inflate 成 `cell_myspace_item` 卡片追加进 `menuContainer`；每个条目 = 图标 + 文字 + 点击回调（`MySpaceItem` 数据模型），与用户信息 API 解耦（先渲染入口，再异步加载用户信息）。
- 未登录引导 → 当本地 `mid==0` 时，顶部显示「点击登录」，粉丝/EXP 文本留空，点击顶部信息卡片跳转 `LoginActivity` 登录页。
- 已登录加载用户信息 → 异步调用 `UserInfoApi.getCurrentUserInfo()` + `getCurrentUserCoin()`，成功后更新：头像（Glide 圆角加载，占位图 `akari`）、昵称、粉丝+硬币数（`"xx粉丝 x硬币"`，粉丝数经 `toWan` 格式化）、EXP 显示（`"EXP:当前经验[/下一级经验]"`，满级隐藏分母）；点击顶部信息卡片则进入自己主页 `UserInfoActivity`（`mid` extra）。
- 菜单项-个人信息 → 点击跳转 `UserInfoActivity`（携带 `mid` = 当前用户 mid；未登录时跳登录页）。
- 菜单项-关注 → 点击跳转 `FollowUsersActivity`，`putExtra("mid", 当前mid)`、`putExtra("mode", 0)`（关注列表）。
- 菜单项-稍后再看 → 点击跳转 `WatchLaterActivity`。
- 菜单项-收藏 → 点击跳转 `FavoriteFolderListActivity`（我的收藏夹列表）。
- 菜单项-追番列表 → 点击跳转 `FollowingBangumisActivity`。
- 菜单项-我的歌单 → 点击跳转 `PlaylistActivity`（音频歌单页）。
- 菜单项-历史记录 → 点击跳转 `HistoryActivity`。
- 菜单项-创作中心 → 当设置 `creative_enable` 为 true 时才显示，点击跳转 `CreativeCenterActivity`。
- 菜单项-大会员 → 点击跳转 `VipActivity`。
- 菜单项-登录记录 → 点击跳转 `LoginRecordActivity`。
- 菜单项-硬币变化记录 → 点击跳转 `CoinLogActivity`。
- 菜单项-经验变化记录 → 点击跳转 `ExpLogActivity`。
- 菜单项-编辑个人资料 → 点击跳转 `EditProfileActivity`。
- 菜单项-修改个人描述 → 点击跳转 `EditSignActivity`，`putExtra("currentSign", 当前签名)`（签名来自已加载的 `currentUserInfo.sign`，未加载则为空串）。
- 菜单项-退出登录（红色文字） → 点击需**两次确认**：第一次 Toast「再点一次退出登录！」并置 `confirmLogout=true`；第二次真正执行：后台调 `UserInfoApi.exitLogin()`，然后清理 SharedPreferences 中的 `cookies`/`mid`/`csrf`/`refresh_token`/`access_key`/`cookie_refresh`，Toast「账号已退出」，跳转登录页并 `finish()` 关闭本页。
- 页面焦点管理 → 进入后让外层 ScrollView 请求焦点，避免弹起软键盘/抢占焦点。

---

## UserInfoActivity.kt（用户信息页 / 他人主页容器）

- Intent Extra 读取 → `intent.getLongExtra("mid", 114514)`（默认值 114514，实际 B 站账号会覆盖）；用该 mid 构造各 Tab Fragment。
- ViewPager 四 Tab → 依次为：动态（`UserDynamicFragment`）、视频（`UserVideoFragment`）、专栏（`UserArticleFragment`）、收藏夹（`UserFavoriteFragment`），`offscreenPageLimit` 设为页数（全部预加载）。
- 标题随 Tab 变化 → 滑动切换时 `updatePageName(position)` 把页面标题改为「用户信息-动态/视频/专栏/收藏夹」。
- 新手引导 Tutorial → 进入时显示空间相关引导提示（`tutorial_space` 数组，编号 2），并调用 `TutorialHelper.showPagerTutorial(this, 4)` 展示 ViewPager 手势引导。
- 动态删除回调 → `onActivityResult`：当从动态详情返回且 `requestCode==GO_TO_INFO_REQUEST(71)`、`resultCode==RESULT_OK` 时，读取返回数据里的 `position`（减 1 转真实下标）调用 `udFragment.onDynamicRemove(position)` 从动态列表移除该条（详情页里删除了动态后回来同步删除）。
- 布局 → `activity_simple_viewpager`，加载动画 View 在 onCreate 里直接 GONE 隐藏。

---

## UserVideoFragment.kt（用户主页-视频 Tab）

- 初始化 → 从 arguments 读 `mid`；进入后清空列表、设置加载更多监听（`setOnLoadMoreListener`）。
- 首次加载 → 后台调 `UserInfoApi.getUserVideos(mid, page, "", videoList)`，返回值 `==1` 表示到底；成功后创建 `UserVideoAdapter(requireContext(), mid, videoList)` 并 setAdapter、关闭刷新动画；首屏即空且到底时 `showEmptyView()`。
- 上拉加载更多 → 触发 `continueLoading(page)`，调 `getUserVideos` 追加到列表并 `notifyItemRangeInserted` 局部刷新；返回值 `1` 置 `bottom=true`（到底）；失败走 `loadFail`。
- 失败兜底 → 任意异常调用 `loadFail(e)`（由基类处理错误 UI/提示）。
- 说明：视频列表的「点击进入播放」等交互由 `UserVideoAdapter` 内部处理（本 Fragment 不设置自定义监听）。

---

## UserDynamicFragment.kt（用户主页-动态 Tab）

- 初始化 → 从 arguments 读 `mid`；设置加载更多监听。
- 首次加载两步走 → ① 先 `UserInfoApi.getUserInfo(mid)`：若返回 null 则 Toast「用户不存在」并 `finish()` 关闭整个页面；② 再 `DynamicApi.getDynamicList(dynamicList, offset, mid, null)` 拉动态，返回的 offset `==-1L` 表示到底。
- 头部用户信息卡 → 创建 `UserDynamicAdapter(requireContext(), dynamicList, userInfo)`，该 Adapter 的 position 0 是用户信息头部（`cell_user_info`），后续是动态列表（详见下方 UserDynamicAdapter 的功能明细）。
- 上拉加载更多 → `continueLoading()` 用偏移量继续拉取并 `notifyItemRangeInserted` 追加。
- 动态删除同步 → `onDynamicRemove(position)` 调用 `DynamicHolder.removeDynamicFromList` 从内存列表移除并刷新（供 UserInfoActivity 从详情页返回时调用）。
- 失败兜底 → 任意异常 `loadFail(e)`。

> 本 Tab 头部（UserDynamicAdapter.UserInfoHolder）承担了用户主页最重要的交互，明细如下：
- 头像 → Glide 圆角加载；点击进入 `ImageViewerActivity` 全屏查看该用户头像原图（`imageList` 仅含头像 URL）。
- 昵称 → 显示 `userInfo.name`，若存在 `vip_nickname_color` 则按该颜色上色。
- 等级徽章 → 显示 `Lv{level}` + 等级徽章图；若是大会员（`vip_role>0`）再追加「月度/年度/十年/百年大会员」标签（粉色圆角背景）。
- UID 显示与复制 → 显示 `mid` 字符串，点击或长按可复制（`StringUtil.setCopy`）。
- 个人签名 userDesc → 显示 `sign`，默认 2 行，点击展开/收起（2 行 ↔ 32 行，`desc_expand` 状态翻转）；长按可复制；自动识别链接（`setLink`）点击跳转。
- 公告 userNotice → 显示 `notice`（非空才显示），点击展开/收起（2↔32 行，`notice_expand`），长按可复制。
- 粉丝数 → 显示「xx粉丝」（`toWan` 格式化），点击进入 `FollowUsersActivity` 的**粉丝列表**（`mode=1`）。
- 关注数 → 显示「xx关注」，点击进入 `FollowUsersActivity` 的**关注列表**（`mode=0`）。
- 勋章墙入口 userMedal → 点击进入 `MedalWallActivity`（携带 `mid`）查看粉丝勋章墙。
- 官方认证 → `official!=0` 时显示官方图标 + 认证类型文案（不知名UP主/知名UP主/大V达人/企业/组织/媒体/政府认证/高能主播/社会人士等）+ 认证描述（`officialDesc`）。
- 专属提示 exclusiveTip → `sys_notice` 非空时显示带警示图标（!）的提示卡片。
- 直播间 liveRoom → `live_room` 非空时显示直播间卡片（标题），点击 `enterLiveDetailPage` 进入直播详情。
- 关注/取关按钮 followBtn → 仅当「查看的不是自己且已登录且 mid 非 0」时显示；点击切换关注状态并立即改 UI（「关注」粉底 ⇄「已关注」深灰底），后台调 `UserInfoApi.followUser(mid, !followed)`；成功 Toast「操作成功喵~」并持久化 `followed`；失败回滚 UI，错误码 22015 提示「被B站风控系统拦截了（无法解决，详见公告）」；请求期间禁用按钮防连点。
- 私信按钮 msgBtn → 仅当已关注时显示（`setFollowed(true)` 才可见）；点击进入 `PrivateMsgActivity`（携带 `uid`）发送私信。
- 加入契约/充电按钮 contractBtn → 当「对方开启充电展示（is_follow_display）」且非本人/非游客时显示；点击调 `UserInfoApi.addContract(mid)` 加入充电契约，成功「加入成功」、158001「不满足条件」；该按钮存在时把分割线约束到下移。
- 充电公示面板 electricPanel → 后台 `ElectricApi.getElectricPanel(mid)` 有数据时显示「充电公示（本月 xx 人）」卡片，内部 RecyclerView 展示充电用户（`ElectricUserAdapter`）；点击表头展开/收起用户列表（箭头旋转）。
- 动态列表项（DynamicHolder 行为，详情见 UserDynamicFragment 所在文件下方的 DynamicHolder 部分）→ 每条动态支持：点击进入动态详情、点赞/取消赞、转发分享、长按删除（自己的动态）。

---

## UserArticleFragment.kt（用户主页-专栏 Tab）

- 初始化 → 从 arguments 读 `mid`；清空列表、设置加载更多监听。
- 首次加载 → `UserInfoApi.getUserArticles(mid, page, articleList)` 返回 `==1` 到底；成功后建 `ArticleCardAdapter` 并 setAdapter；首屏空且到底显示空视图。
- 上拉加载更多 → `continueLoading(page)` 追加并 `notifyItemRangeInserted` 局部刷新；返回 `1` 置 `bottom`。
- 专栏卡片点击 → 由 `ArticleCardAdapter` 处理：点击进入专栏详情（`enterArticleDetailPage`）；长按在「快速缓存模式」（`cache_quick_mode`）下缓存该专栏到本地 `article_cache/{id}.json`（提示「专栏已缓存」/「该专栏已缓存」），否则触发长按监听。
- 失败兜底 → `loadFail(e)`。

---

## UserFavoriteFragment.kt（用户主页-收藏夹 Tab）

- 初始化 → 从 arguments 读 `mid`。
- 加载他人公开收藏夹 → `FavoriteApi.getUserFavoriteFolders(mid)` 一次拉取全部（无分页），私密收藏夹已在 API 层过滤；成功后建 `UserFavoriteFolderAdapter` 并 setAdapter；空列表显示空视图。
- 收藏夹卡片 → 显示名称（HTML 转义）、`视频数/上限`（他人无上限则显示「N个视频」）、封面（接口不返回封面字段时用占位图）；点击进入 `FavoriteVideoListActivity`（携带 `mediaId`/`mid`/`name`，走只读新接口）。
- 无管理能力 → 本页为只读视角：无新建按钮、无图文收藏夹入口、不支持编辑/删除（与"我的收藏夹"不同）。
- 失败兜底 → `loadFail(e)`。

---

## EditSignActivity.kt（修改个人描述 / 签名）

- 未登录拦截 → 本地 `mid==0` 时 Toast「还没有登录喵~」并 `finish()`。
- 预填当前签名 → 读 Intent Extra `currentSign`（空则空串），填入 EditText，光标置于末尾。
- 字数限制与计数 → EditText 设置 `LengthFilter(70)`（最多 70 字），右下角实时显示 `"n/70"` 计数（TextWatcher 监听）。
- 提交按钮 → 点击执行：
  - 防重复提交：`isSubmitting` 为 true 时提示「正在提交中...」直接返回。
  - Cookie 校验：`cookie_refresh` 为 false 时弹对话框「无法提交：上一次的Cookie刷新失败了，您可能需要重新登录以进行敏感操作」。
  - 后台调 `UserInfoApi.updateUserSign(newSign)` 并解析 `code`/`message`；提交期间禁用按钮。
  - 成功（code==0）：Toast「修改成功，等待审核」，`setResult(RESULT_OK)` 并 `finish()`。
  - 失败按 code 映射错误文案：-101 账号未登录、-111 CSRF校验失败、40015 签名包含敏感词、40021 签名不能包含表情图片、40022 签名过长、其他用接口 `message`。
  - 网络/JSON 异常（IOException/JSONException）：Toast「修改个人描述失败」。

---

## EditProfileActivity.kt（编辑个人资料：头像上传 / 入口集合）

- 未登录拦截 → `mid==0` 时 Toast「还没有登录喵~」并 `finish()`。
- 页首返回 → 点击页面标题 `pageName` 关闭页面（`finish()`）。
- 当前头像预览 → 读 SharedPreferences `avatar` 字段，非空则 Glide 圆角显示（`DiskCacheStrategy.NONE`）。
- 上传头像卡片 upload_avatar → 点击触发图片选择：
  - `isUploading` 时提示「正在上传中...」；`cookie_refresh` 为 false 时弹「无法上传」对话框。
  - `ACTION_PICK` 打开系统图库，`REQUEST_CODE_PICK_IMAGE=1001`。
  - 选中图片后后台处理：解码 Bitmap → JPEG 质量 90 压缩 → 文件名 `avatar_{时间戳}.jpg` → `UserInfoApi.uploadAvatar(imageData, fileName)`。
  - 成功（code==0）：Toast「头像上传成功，等待审核」，用返回 `data.url` 更新 SharedPreferences `avatar` 并刷新顶部预览图。
  - 失败按 code 映射：-101 未登录、-102 CSRF校验失败、-111 图片格式不支持、-112 图片过大、-400 请求参数错误、-403 CSRF验证失败、其他用 `message`；Toast「{错误}(错误码:{code})」。
  - 解码失败：「无法读取图片」/「无法解码图片」。
- 修改签名入口卡片 edit_sign → 点击跳转 `EditSignActivity`。
- 修改资料入口卡片 edit_user_info → 点击跳转 `EditUserInfoActivity`。

---

## EditUserInfoActivity.kt（修改昵称 / 生日 / 性别）

- 未登录拦截 → `mid==0` 时 Toast「还没有登录喵~」并 `finish()`。
- 返回 → 点击页首 `pageName` 关闭页面。
- 预填昵称 → 进入后异步 `getCurrentUserInfo()`，成功则把当前昵称填入用户名输入框（预填失败不影响提交）。
- 字段输入 → 用户名 `etUsername`、生日 `etBirthday`、性别单选 `rgSex`（男 rb_male / 女 rb_female）。
- 提交按钮 → 点击执行：
  - 防重复提交：`isSubmitting` 时提示「正在提交中...」。
  - Cookie 校验：`cookie_refresh` false 时弹「无法提交」对话框。
  - 生日格式校验：非空时必须匹配 `YYYY-MM-DD` 正则，否则提示「生日格式错误，请使用YYYY-MM-DD」。
  - 空提交拦截：昵称、生日、性别三项全空时提示「请至少填写一项要修改的内容」。
  - 后台调 `UserInfoApi.updateUserInfo(uname?, birthday?, sex?, null)`（空字段传 null），提交期间禁用按钮。
  - 成功（code==0）：Toast「修改成功」并 `finish()`。
  - 失败映射：-101 账号未登录、-111 CSRF验证失败、400 昵称违规或已被占用、412 修改频率过高、2001 昵称已存在、21003 生日格式错误、-403 权限不足、其他用 `message`。

---

## CreativeCenterActivity.kt（创作中心）

- 加载统计与UP主时长 → `asyncInflate` 后后台并行取 `CreativeCenterApi.getVideoStat()` 与 `getBeUPTime()`。
- 非 UP 主处理 → `getVideoStat()` 返回 null 时 Toast「先去成为UP主吧~」并 `finish()` 关闭页面。
- 数据展示 → 有数据时填充 8 组统计，每组 = 总量 + 变化量（用 `toWan` 格式化，增长为正显示 `+N`，负增长不显示符号）：
  - 总粉丝/涨粉（total_fans/incr_fans）
  - 总播放/涨播放（total_click/incr_click）
  - 总点赞/涨赞（total_like/inc_like）
  - 总投币/涨币（total_coin/inc_coin）
  - 总收藏/涨藏（total_fav/inc_fav）
  - 总分享/涨分享（total_share/inc_share）
  - 总评论/涨评论（total_reply/incr_reply）
  - 总弹幕/涨弹幕（total_dm/incr_dm）
- 成为UP主时长 → 显示 `beUpTime` 文本。
- 页面焦点管理 → 外层 ScrollView 请求焦点。

---

## FollowUsersActivity.kt（关注列表 / 粉丝列表）

- Intent Extra → `mode`（0=关注列表，1=粉丝列表）、`mid`（目标用户）。
- 参数校验 → `mode` 不在 [0,1] 或 `mid==-1` 时直接 `finish()`。
- 标题 → mode==0「关注列表」，mode==1「粉丝列表」。
- 分组模式判定 → `groupMode = mode==0 && mid==当前登录用户 && 设置 follow_group_mode==true`（仅自己的关注列表可启用分组）。
- 普通模式加载 → 后台调 `FollowApi.getFollowingList(mid,...)` 或 `getFollowerList(mid,...)`；建 `UserListAdapter`，设置加载更多监听；返回值 `1` 置到底。
- 分组模式加载 → 调 `FollowApi.getFollowTags()` 获取标签分组，过滤 `count>0` 的分组，建 `FollowGroupAdapter`；点击分组头展开时回调 `loadGroupUsers(tagid)`。
- 分组用户加载 → `loadGroupUsers` 调 `getFollowTagUsers(tagid,1,...)` 拉第一页（每页 20），若满 20 条自动递归 `loadMoreGroupUsers` 继续拉剩余页直到不足 20。
- 上拉加载更多 → 普通模式 `continueLoading` 追加并局部刷新；分组模式直接返回（分组已自行拉全）。
- 隐私/风控错误 → 捕获 `e.message` 以 `22115`/`22118` 开头时 `finish()` 关闭页面并 Toast 该错误信息（B站风控）。
- 用户列表项交互（UserListAdapter）→ 显示头像（无则隐藏并允许多行签名）、昵称（按 `vip_nickname_color` 上色）、签名；点击整项进入该用户 `UserInfoActivity`（`mid`）。无关注/取关快捷按钮。
- 分组模式交互（FollowGroupAdapter）→ 分组头显示组名 +「N 位成员」，点击展开/收起（箭头 0°↔90° 旋转动画），首次展开自动拉取组内用户；组内用户项显示头像/昵称/签名，点击进入 `UserInfoActivity`。

---

## FollowingBangumisActivity.kt（追番列表）

- 标题 → 「追番列表」。
- 首次加载 → `BangumiApi.getFollowingList(page, videoList)`，返回 `-1` 不渲染；否则建 `VideoCardAdapter` 并 setAdapter、设置加载更多监听；`1` 置到底。
- 上拉加载更多 → `continueLoading` 追加并局部刷新。
- 卡片交互（VideoCardAdapter/VideoCardHolder）→ 点击进入视频/番剧详情（type `video`→视频详情、`media_bangumi`→番剧详情）；长按在「快速缓存模式」下缓存该集（弹画质选择 / 最高画质 / 仅音频 / 指定画质，按 `cache_default_quality` 设置），否则触发长按监听（此处未设置自定义长按监听，仅缓存）。
- 失败兜底 → `loadFail(e)`。

---

## HistoryActivity.kt（历史记录）

- 标题 → 「历史记录」。
- 首次加载 → `HistoryApi.getHistory(lastResult, videoList)`，`code==0` 时建 `HistoryVideoCardAdapter`；`isBottom` 置到底；`code!=0` 时 Toast 接口 message。
- 长按删除（两次确认） → 对每条历史卡片设置长按监听：第一次长按记录位置与时间戳并 Toast「再次长按删除」；**4 秒内**对同一位置再次长按则调 `HistoryApi.deleteHistory(aid, bvid)`，成功后 Toast「删除成功」并 `removeAt` + `updateList` 刷新；失败提示错误码。注意：长按删除与「快速缓存模式」冲突——`cache_quick_mode` 开启时优先走缓存，关闭时才走删除监听。
- 上拉加载更多 → `continueLoading` 用 `lastResult` 偏移追加并 `updateList` 整体刷新；`isBottom` 置到底；`onLoadComplete()`。
- 卡片显示 → 复用 `cell_video_list`，额外在观看次数后追加观看时间（`HH:mm`，来自 `viewAt`）。
- 失败兜底 → `loadFail(e)`。

---

## WatchLaterActivity.kt（稍后再看）

- 标题 → 「稍后再看」。
- 首次加载 → `WatchLaterApi.getWatchLaterList()` 一次拉全；建 `VideoCardAdapter` 并 setAdapter。
- 长按删除（两次确认） → 对每条设置长按监听：第一次 Toast「再次长按删除」并记录位置/时间；**4 秒内**同一位置再次长按则调 `WatchLaterApi.delete(aid)`，成功后 Toast「删除成功」并 `notifyItemRemoved` + `notifyItemRangeChanged` 刷新；失败提示错误码。长按同样受 `cache_quick_mode` 影响（开启时优先缓存）。
- 卡片交互 → 点击进入视频详情（同 VideoCardAdapter 默认行为）。
- 失败兜底 → `loadFail(e)`。

---

## CoinLogActivity.kt（硬币变化记录）

- 标题 → 「硬币变化记录」。
- 加载 → `CoinLogApi.getCoinLog()` 一次拉全。
- 展示 → 空列表时 Toast「暂无硬币变化记录」并显示 `emptyTip`；否则建 `CoinLogAdapter` 绑定。
- 列表项 → 纯展示（无点击）：硬币变化量（正数前加 `+`）、变化原因、时间。
- 下拉刷新 → `SwipeRefreshLayout` 存在但被禁用（`isEnabled=false`），仅用其转圈动画表示加载中；加载失败 Toast「加载失败：{message}」并显示空提示。

---

## ExpLogActivity.kt（经验变化记录）

- 标题 → 「经验变化记录」。
- 加载 → `ExpLogApi.getExpLog()` 一次拉全。
- 展示 → 空列表 Toast「暂无经验变化记录」+ 空提示；否则建 `ExpLogAdapter`。
- 列表项 → 纯展示：经验变化量、原因、时间（无点击）。
- 下拉刷新 → SwipeRefreshLayout 被禁用仅作加载动画；失败 Toast「加载失败：{message}」+ 空提示。

---

## LoginRecordActivity.kt（登录记录）

- 标题 → 「登录记录」。
- 加载 → 取本地 `mid`、`buvid=""`，调 `LoginRecordApi.getLoginRecord(mid, buvid)`。
- 展示 → 空列表 Toast「暂无登录记录」+ 空提示；否则建 `LoginRecordAdapter`。
- 列表项 → 纯展示（无点击）。
- 下拉刷新 → SwipeRefreshLayout 禁用仅作加载动画；失败 Toast「加载失败：{message}」+ 空提示。

---

## MedalWallActivity.kt（粉丝勋章墙）

- Intent Extra → `mid`（目标用户），`-1` 时 `finish()`。
- 标题 → 「粉丝勋章」。
- 加载解析 → `UserInfoApi.getMedalWall(mid)` 取 `data.list` 数组，逐条解析为 `MedalInfo`：
  - `medal_info`：目标 id、等级、勋章名、三色（start/end/border）、大航海等级 guard_level、佩戴状态 wearing_status、medal_id、亲密度/下一级亲密度、今日亲密度/日上限、守护/荣耀图标。
  - 顶层：目标用户名、目标头像、link、直播状态、官方标识。
  - `uinfo_medal`：勋章名/等级/配色（新式 v2 颜色）、id、type、是否点亮、ruid、大航海等级、score、守护/荣耀图标、用户获得数。
- 展示 → 建 `MedalListAdapter`，无数据 `showEmptyView` 否则 `hideEmptyView`，`bottom=true`（无分页）。
- 勋章列表项（MedalListAdapter）→ 显示勋章名、所属UP名、等级（`Lv.N`，佩戴中追加「(佩戴中)」）、亲密度（`亲密度: x / 下一级`）、UP头像（无则隐藏）；`target_id>0` 时点击进入该 UP 的 `UserInfoActivity`。
- 失败兜底 → `loadFail(e)`。

---

## VipActivity.kt（大会员信息）

- 布局 → `activity_vip` 异步 inflate。
- 数据加载 → `VipApi.getVipInfo()` 取 `VipInfo`。
- 大会员状态 → `isVip` 为真：状态显示「是」，类型按 `vipIsAnnual`→「年度大会员」、`vipIsMonth`→「月大会员」、否则「大会员」；到期时间 `vipDueDate>0` 用 `yyyy-MM-dd HH:mm:ss` 格式化，否则「未知」。非会员：状态「否」、类型「无」、到期「无」。
- 等级与经验 → 等级显示 `level`；经验：`nextExp==-1` 显示「当前经验 (已满级)」，否则「当前 / 下一级」（`toWan` 格式化）。
- 绑定手机号 → `bindPhone` 非空显示，否则「未绑定」。
- 大会员福利 → `privilegeList` 非空时显示福利区块，逐条显示 9 类福利（B币兑换、会员购优惠券、漫画福利券、会员购包邮券、漫画商城优惠券、装扮体验卡、课堂优惠券、游戏礼盒、每日10经验）及状态（0=未兑换、1=已兑换、其他=未完成）。
- 领取每日经验按钮 experienceButton → 点击调 `VipApi.addExperience()`：code==0 且 `is_grant` 为 true →「领取成功」；code==69198 →「今日已领取」；否则「领取失败: {message}」。
- 页面焦点管理 → 外层 ScrollView 请求焦点。

---

## FavoriteFolderCreateActivity.kt（创建收藏夹）

- 布局 → 复用 `activity_favorite_folder_edit`，隐藏删除按钮（`btnDelete` GONE），标题「创建收藏夹」。
- 字段 → 收藏夹名称 `editTitle`、简介 `editIntro`。
- 保存按钮 → 点击执行：
  - 名称空校验：「请输入收藏夹名称」。
  - 后台调 `FavoriteApi.addFolder(title, intro, 0)`（第三个参数 privacy **固定传 0=公开**，本页面未暴露公开/私密切换）；提交期间 `btnSave.isClickable=false` 防连点。
  - 成功（返回 0）：Toast「创建成功」，`setResult(RESULT_OK)` 并 `finish()`。
  - 失败：Toast「创建失败，错误码：{code}」。
  - 异常：`report(e)`。

---

## FavoriteFolderEditActivity.kt（编辑 / 删除收藏夹）

- Intent Extra → `mediaId`（收藏夹 id）、`title`（原名称）、`intro`（原简介）、`isDefault`（是否默认收藏夹）。
- 预填 → 把 `title`/`intro` 填入输入框。
- 默认收藏夹限制 → `isDefault==true` 时：名称/简介输入框禁用、保存按钮不可点（透明度 0.5）、删除按钮隐藏，Toast「默认收藏夹不能编辑或删除」。
- 保存按钮（非默认） → 名称空校验；后台调 `FavoriteApi.editFolder(mediaId, title, intro, 0)`（privacy 固定 0=公开，未暴露公开/私密切换）；成功 Toast「保存成功」+ `setResult(RESULT_OK)` + `finish()`；失败「保存失败，错误码：{code}」；提交期间禁用防连点。
- 删除按钮（非默认，两次确认） → 第一次点击 Toast「再次点击删除按钮确认删除」并启动 3 秒倒计时（超时重置计数）；3 秒内再次点击则调 `FavoriteApi.deleteFolder(mediaId)`；成功 Toast「删除成功」+ `setResult(RESULT_OK)` + `finish()`；失败「删除失败，错误码：{code}」；删除期间禁用防连点。
- 说明 → 页面未提供收藏夹内视频的"添加/移除/排序"功能，这些在收藏视频列表页（FavoriteVideoListActivity）处理。

---

## FavoriteFolderListActivity.kt（我的收藏夹列表）

- 标题 → 「收藏」；`mid` 取本地登录用户。
- 加载 → `FavoriteApi.getFavoriteFolders(mid)` 一次拉全；建 `FavoriteFolderAdapter`。
- 首行创建按钮（cell_create_folder_button） → 通过 Adapter 的 `OnCreateClickListener` 回调 `showCreateDialog()` → 跳转 `FavoriteFolderCreateActivity`（`startActivityForResult(...,2)`）。
- 收藏夹卡片 → 显示名称（HTML 转义）、`视频数/上限`、封面；点击进入 `FavoriteVideoListActivity`（携带 `fid`=folder.id、`mid`、`name`，走"我的收藏"分页接口）。
- 长按编辑 → 对非默认收藏夹长按：跳转 `FavoriteFolderEditActivity`（携带 `mediaId`/`title`/`intro`(空)/`isDefault`，`startActivityForResult(...,1)`）；`mediaId==0` 时 Toast「无法获取收藏夹信息，请稍后重试」；默认收藏夹长按 Toast「默认收藏夹不能编辑」（由 Adapter 层拦截）。
- 尾部图文收藏夹入口（position==folderList.size+1） → 显示「图文收藏夹」卡片，点击进入 `FavouriteOpusListActivity`（图文/动态收藏列表），长按置空不可删。
- 返回刷新 → `onActivityResult`：创建（requestCode 2）或编辑（requestCode 1）返回 `RESULT_OK` 时重新 `loadFolders()` 刷新列表。
- 失败兜底 → `loadFail(e)`。

---

## FavoriteVideoListActivity.kt（收藏夹内视频列表）

- Intent Extra → `mid`（所属用户）、`fid`（收藏夹旧 id，自己）、`mediaId`（新接口收藏夹 id，他人）、`name`（收藏夹名称，作页面标题）。
- 只读判定 → `readOnly = mediaId > 0`（查看他人公开收藏夹，走新接口 `x/v3/fav/resource/list`）；自己收藏夹走 `getFolderVideos(mid, fid, ...)` 老接口。
- 首次加载 → 按只读与否选择接口拉第一页；建 `VideoCardAdapter`；`1` 置到底。
- 虚拟合集播放（开启 `VIRTUAL_COLLECTION_ENABLE`，默认 true） → 点击收藏夹内任意视频时不再进单个视频详情，而是把当前收藏夹**所有视频组成一个"虚拟合集"**连续播放：以点击视频为起始页，`pagenames`=各视频标题、`cids`=各视频 aid、`startPageIndex`=点击位置；后台 `VideoInfoApi.getVideoInfo(起始aid)` 取首个 cid；构建 `PlayerData(TYPE_VIDEO)`（标题=「{收藏夹名}（虚拟合集）」）并 `PlayerApi.startGettingUrl` 播放。收藏夹为空或取 cid 失败时 Toast 提示。
- 长按删除（仅自己的收藏夹，两次确认） → `readOnly` 为 false 时对每条设置长按监听：第一次 Toast「再次长按删除」；**4 秒内**同一位置再次长按调 `FavoriteApi.deleteFavorite(aid, fid)`，成功 Toast「删除成功」并 `notifyItemRemoved` + `notifyItemRangeChanged` 刷新，失败提示错误码。他人收藏夹不提供长按删除。注意：长按删除受 `cache_quick_mode` 影响（开启时优先缓存而非删除）。
- 上拉加载更多 → `continueLoading` 追加并局部刷新（记录插入起始位置）。
- 失败兜底 → `loadFail(e)`。
- 说明 → 本页未实现"排序收藏夹内视频"功能；添加视频到收藏夹在播放器/视频详情页处理，不在本页。

---

## FavouriteOpusListActivity.kt（图文收藏夹 / 动态收藏）

- 标题 → 「图文收藏夹」。
- 首次加载 → `FavoriteApi.getFavouriteOpus(list, page)` 拉第一页；建 `OpusAdapter` 并 setAdapter；设置加载更多监听。
- 上拉加载更多 → `loadMore(page)` 追加，返回值取反后置 `bottom`（返回值表示是否还有更多），局部刷新。
- 图文卡片（OpusAdapter） → 显示收藏时间 `pubTime`、标题、封面（圆角）；点击进入图文/动态详情（`enterOpusDetailPage`，携带 opus id）；内容为「内容失效」时点击 Toast「内容失效，无法打开」。
- 失败兜底 → `loadFail(e)`。

---

## 跨文件关联说明

- **我的空间**（MySpaceActivity）是"我的"功能总入口，通过显式 Intent（部分带 `mid`/`mode`/`currentSign` Extra）跳转本组其余页面。
- **用户信息页**（UserInfoActivity）是他人主页的 ViewPager 容器，真正用户资料头部由 `UserDynamicAdapter` 的 `UserInfoHolder` 承载（头像、昵称、签名、粉丝/关注数、关注/取关、私信、充电、勋章墙、直播间、官签、公告等交互）。
- **收藏夹两套视角**：自己（FavoriteFolderListActivity，可创建/编辑/删除/看图文收藏）与他人（UserFavoriteFragment，只读）。收藏夹视频列表（FavoriteVideoListActivity）按 `mediaId` 区分只读/可删。
- 本组页面普遍使用「两次长按/两次点击确认」删除交互（历史记录、稍后再看、收藏夹视频、收藏夹删除、动态删除），且有 `cache_quick_mode` 与长按删除的优先级冲突逻辑。
- 收藏夹**公开/私密切换**、**收藏夹内排序**、**在收藏夹内添加视频**：当前代码未在 UI 暴露（`addFolder`/`editFolder` 的 privacy 参数固定传 `0`，收藏夹及视频均无排序控件）。