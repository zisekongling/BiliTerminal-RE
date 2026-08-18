# 消息/私信与评论/回复 源码功能审查（细颗粒度）

> 审查对象：`app/src/main/java/com/RobinNotBad/BiliClient/activity/message/` 与 `activity/reply/` 下的 7 个文件
> 审查粒度：每个按钮、点击、长按、回复、点赞、举报、删除、分页、Intent Extra、定时器、动画、事件总线等，逐条列出"功能 + 具体用法/触发方式"。

---

## MessageActivity.kt（消息中心主页）

继承 `InstanceActivity`，页面标题与布局为 `activity_message`，使用 `asyncInflate` 异步加载布局。

### 页面初始状态
- **下拉刷新（SwipeRefreshLayout）禁用**：`swipeRefreshLayout.isEnabled = false`，用户无法手动下拉刷新；加载期间显示 `isRefreshing = true` 的转圈动画，数据加载完成后在 `runOnUiThread` 中关闭转圈（`isRefreshing = false`）。

### 设置入口按钮
- **设置按钮（setting_btn，MaterialCardView）**：点击 → 构造 `Intent` 跳转到 `MessageSettingsActivity`（消息设置页）。无 Extra 参数。

### "回复我的"入口（reply 卡片）
- **点击 reply 卡片**：构造 `Intent` 跳转到 `NoticeActivity`，`putExtra("type", "reply")`（表示"回复我的"通知页）。
- 点击后**立即把 reply_text 文案改为"回复我的"**（用于覆盖可能的未读标记显示）。

### "收到的赞"入口（like 卡片）
- **点击 like 卡片**：跳转到 `NoticeActivity`，`putExtra("type", "like")`（"收到的赞"通知页）。
- 点击后立即把 like_text 文案改为"收到的赞"。

### "@我"入口（at 卡片）
- **点击 at 卡片**：跳转到 `NoticeActivity`，`putExtra("type", "at")`（"@我"通知页）。
- 点击后立即把 at_text 文案改为"@我"。

### "系统通知"入口（system 卡片）
- **点击 system 卡片**：跳转到 `NoticeActivity`，`putExtra("type", "system")`（系统通知页）。
- 注意：此入口点击后**不重置** system_text 文案（无未读数字逻辑）。

### 私信会话列表（sessions_list RecyclerView）
- **加载逻辑**：在 `CenterThreadPool` 后台线程中依次执行：
  1. `MessageApi.getUnread()` 获取各类未读计数（JSON 中 `reply`/`like`/`at` 字段）。
  2. `PrivateMsgApi.getSessionsList(20)` 拉取最多 20 条私信会话。
  3. **排序规则**：用 `Collections.sort` 自定义比较器，把有未读（`unread > 0`）的会话排在前面，无未读的排后面；同组内部保持原顺序（不稳定排序）。
  4. 收集所有会话的 `talkerUid` 组成 uidList，调用 `PrivateMsgApi.getUsersInfo(uidList)` 批量获取会话用户信息（头像、昵称等）。
  5. 构造 `PrivateMsgSessionsAdapter(this, sessionsList, userMap)` 作为适配器。
- **UI 呈现**：`sessionsView.isNestedScrollingEnabled = false`（禁止内部嵌套滚动）；设置 `CustomLinearManager` 布局管理器 + adapter。
- **未读数字显示**：根据 getUnread 返回的 `reply`/`like`/`at` 三个 int 值，分别拼接进 `reply_text`/`like_text`/`at_text`，格式为 `"回复我的(3未读)"` 等；值为 0 时不显示括号。
- **清除消息角标**：加载成功后 `SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0)`，把全局"消息更新数"归零（用于清桌面/入口红点）。
- **焦点处理**：把 `scrollView` 设为 `isFocusable = true`、`isFocusableInTouchMode = true` 并 `requestFocus()`（使返回时滚动条不抢焦点，让软键盘/焦点行为正确）。
- **错误处理**：任一环节抛异常，回到 UI 线程用 `MsgUtil.err(e)` 提示。

### 教程引导
- **TutorialHelper 教程**：`TutorialHelper.showTutorialList(this, R.array.tutorial_message, 5)` 展示消息页新手教程（第 5 组教程，具体文案来自 `tutorial_message` 数组资源），首次进入或按规则展示。

---

## MessageSettingsActivity.kt（消息设置页）

继承 `BaseActivity`，布局为通用 `activity_simple_refresh`，页面名 `setPageName("消息设置")`。本质是"消息/私信接收偏好"的设置页。

### 页面初始状态
- **空态视图**：`emptyView`（emptyTip TextView），默认隐藏，仅在加载失败时显示"加载失败，请重试"。
- **SwipeRefreshLayout**：`isEnabled = false`（禁止手动下拉），初始 `isRefreshing = true`。
- **RecyclerView**：`LinearLayoutManager` 线性布局。
- 创建时直接调用 `loadSettings()` 拉取设置。

### 设置项加载（loadSettings）
- **网络请求**：`MessageApi.getMsgSettings()` 获取当前消息设置。
- **成功分支（code==0）**：取 `data` JSONObject 存入 `currentSettings`，调用 `buildSettingsList()` 构建列表，然后 UI 线程创建 `MessageSettingsAdapter(this, settingsList) { key, value -> onSettingChanged(key, value) }` 并设置到 RecyclerView，关闭刷新转圈。
- **失败分支（code!=0）**：`MsgUtil.showMsg("获取设置失败: " + message)`，关闭转圈，emptyView 显示"加载失败，请重试"并 `VISIBLE`。
- **异常分支**：`report(e)` + 关闭转圈 + emptyView 显示"加载失败，请重试"。

### 设置项列表（buildSettingsList，按字段存在与否动态构建）
以下设置项均由服务端 `currentSettings` 中是否含对应字段决定是否显示，每项含：key、标题、说明、类型（`TYPE_CHOOSE` 二选一 / `TYPE_SWITCH` 开关）、当前值（`==1` 视为开启）、可选项数组：

1. **msg_notify（消息提醒）**：`TYPE_CHOOSE`，选项 `["接收", "不接收"]`，说明"是否接收消息提醒"，默认值 1（接收）。**保存时**：开启 → `1`，关闭 → `3`（特殊值，非 0/1）。
2. **show_unfollowed_msg（收起未关注人消息）**：`TYPE_SWITCH`，说明"收起来自未关注用户的消息"，默认 0。
3. **is_group_fold（收起应援团消息）**：`TYPE_SWITCH`，说明"折叠应援团相关消息"，默认 0。
4. **should_receive_group（接收应援团消息）**：`TYPE_SWITCH`，说明"是否接收应援团消息"，默认 1。
5. **receive_unfollow_msg（接收未关注人消息）**：`TYPE_SWITCH`，说明"是否接收未关注用户的消息"，默认 1。
6. **ai_intercept（私信智能拦截）**：`TYPE_SWITCH`，说明"使用AI智能过滤骚扰私信"，默认 0。

### 设置变更与保存（onSettingChanged）
- **触发方式**：由 Adapter 回调（用户切换开关/选择"接收/不接收"时）以 `(key, value: Boolean)` 触发。
- **构造变更 JSON**：`msg_notify` 关闭时写 `3`，开启写 `1`；其余开关开启写 `1`、关闭写 `0`。
- **提交**：`MessageApi.setMsgSettings(settings)`。
- **成功（code==0）**：Toast `"设置已保存"`。
- **失败（code!=0）**：Toast `"保存失败: " + message`，并调用 `loadSettings()` 重新加载（回滚界面到服务端真实状态）。
- **异常**：Toast `"保存失败"`，并 `loadSettings()` 回滚。

---

## NoticeActivity.kt（通知/消息列表详情页：回复/点赞/@/系统）

继承 `RefreshListActivity`（具备下拉刷新 + 上拉加载更多的基础能力），页面名 `setPageName("详情")`。

### Intent Extra（入口参数）
- **`type`（String）**：唯一入口参数，决定加载哪类通知，取值：
  - `"like"` → 收到的赞
  - `"reply"` → 回复我的
  - `"at"` → @我
  - `"system"` → 系统通知

### 首次加载（onCreate 中后台线程）
按 `pageType` 分派：
- **like**：`MessageApi.getLikeMsg(0, 0)`（id=0, time=0 表示第一页），保存返回的 `cursor`（`MessageCard.Cursor`，含 id/time/is_end），列表赋值 `messageList`。
- **reply**：`MessageApi.getReplyMsg(0, 0)` 第一页，同理。
- **at**：`MessageApi.getAtMsg(0, 0)` 第一页，同理。
- **system**：`MessageApi.getSystemMsg()`（一次性全量，无分页游标）。

然后构造 `NoticeAdapter(this, messageList)`，UI 线程设置：
- `setAdapter(noticeAdapter!!)` 绑定列表。
- `setRefreshing(false)` 关闭刷新。
- `setOnLoadMoreListener { i -> continueLoading(i) }` 注册上拉加载更多回调。
- 异常时仅 `e.printStackTrace()`（无用户提示）。

### 上拉加载更多（continueLoading）
- **触发方式**：用户上拉触底触发 `RefreshListActivity` 的 onLoadMore 回调。
- 记录当前 `lastSize = messageList.size`，按 type 分派用**游标续传**：
  - like/reply/at：`MessageApi.getLikeMsg(cursor.id, cursor.time)` 等，用旧游标取下一页，更新 `cursor`，`messageList.addAll(新列表)`。
  - system：重新 `getSystemMsg()` 整体替换。
- **UI 更新**：`noticeAdapter.notifyItemRangeInserted(lastSize, messageList.size - lastSize)` 增量插入新条目。
- **到底判断**：`bottom = cursor.is_end`（驱动 `RefreshListActivity` 停止继续加载）。
- `setRefreshing(false)` 关闭刷新。
- **异常**：`e.printStackTrace()`；`page--` 回退页码（防止死循环翻页）并 `setRefreshing(false)`。

> 说明：`RefreshListActivity` 的下拉刷新能力由基类提供，本页未显式覆写 onRefresh；由于 type 由 Intent 固定，下拉刷新行为依赖基类默认实现（此文件未定义独立刷新逻辑）。

---

## PrivateMsgActivity.kt（私信聊天页）

继承 `BaseActivity`，布局 `activity_private_msg`。负责与单个用户（uid）的私信会话：查看历史、发送、自动刷新、自动已读、加载更多、输入框动画。

### Intent Extra（入口参数）
- **`uid`（Long）**：聊天对象用户 id。`intent.getLongExtra("uid", 114514)`，缺省默认 114514（一个占位默认值）。

### 打开页面的提示
- **进入即 Toast**：`MsgUtil.showMsg("私信有可能被拦截\n尽量不要用终端发私信喵")` —— 提示用户第三方终端私信可能被 B 站拦截，不建议使用。

### 消息加载（onCreate 后台线程）
- `PrivateMsgApi.getPrivateMsg(uid, 50, 0, 0)`：取最近 50 条，`allMsg` 保存原始 JSON。
- `getPrivateMsgList(allMsg)` 解析成消息列表 `list`，`Collections.reverse(list)` 反转为**旧→新**时间序（底部最新）。
- `getEmoteJsonArray(allMsg)` 提取本页用到的表情数组 `emoteArray`。
- 构造 `PrivateMsgAdapter(list, emoteArray, this)`。

### 自动已读（可选）
- **开关**：若 `SharedPreferencesUtil.getBoolean(PRIVATE_MSG_AUTO_READ_ENABLE, true)` 为真（默认开），调用 `PrivateMsgApi.updateAck(uid, 1, 0)` 标记已读；失败仅 `Log.e`，不打扰用户。

### 聊天列表 UI
- **布局管理器**：`CustomLinearManager` + `manager.stackFromEnd = true`（列表从底部堆叠，默认显示最新）。
- **滚动监听（OnScrollListener）**：
  - `SCROLL_STATE_DRAGGING`：当 `!recyclerView.canScrollVertically(-1)`（已滚到最顶部）且未在加载更多时 → `loadMore()` 加载更早消息。
  - `SCROLL_STATE_IDLE`：若输入框当前被隐藏（`!animVisible`），延迟 500ms 后把输入框滑出（`getViewAnimation(layout_input, true, true)`），并 `postDelayed` 200ms 后置 `animVisible = true`。
  - `onScrolled`：若输入框可见（`animVisible`）且列表可继续向下滚（`canScrollVertically(0)`）且 `dy != 0` → 收起输入框（`getViewAnimation(layout_input, false, false)`），置 `animVisible = false`。

### 输入框滑入/滑出动画（getViewAnimation）
- `TranslateAnimation` 垂直位移，`duration=200ms`，`AccelerateDecelerateInterpolator` 缓动，`fillAfter=true`。
- `show_or_hide=true`：开始即 `VISIBLE`，从（上/下）位移到 0（滑入）；结束时不动。
- `show_or_hide=false`：从 0 位移到（上/下）隐藏（滑出），结束置 `GONE`。
- 方向由 `up_or_down` 控制（向上滑/向下滑）。

### 自动轮询刷新（refreshTimer）
- **定时器**：进入页面后创建 `Timer`，`schedule(refresh, 15000, 15000)` —— 每 15 秒自动调用 `refresh()` 拉新消息。`onDestroy` 时 `refreshTimer.cancel()` 取消。

### 刷新新消息（refresh）
- 用 `list[list.size-1].msgSeqno` 作为起始序号，`PrivateMsgApi.getPrivateMsg(uid, 50, 最新seqno, 0)` 取增量。
- 新消息表情并入 `emoteArray`。
- `Collections.reverse(newList)` 后按序 `list.add(msg)` + `notifyItemInserted(list.size-1)`，再 `notifyItemRangeChanged(oldListSize-1, list.size)`。
- `msgView.smoothScrollToPosition(list.size - 1)` 平滑滚到最新。

### 加载更早消息（loadMore，滑动到顶部触发）
- 置 `isLoadingMore = true`，Toast `"加载更多中..."`。
- 若 `allMsg.getInt("has_more") == 1`：用 `list[0].msgSeqno` 作为末尾游标，`PrivateMsgApi.getPrivateMsg(uid, 15, 0, 最早seqno)` 取更早 15 条。
- 表情并入 `emoteArray`；`adapter.addItem(newList)` 插入到顶部；Toast `"已加载更多消息！"`。
- 若 `has_more == 0`：Toast `"没有更多消息了"`。
- 异常：`MsgUtil.err(e)`。

### 发送消息（sendBtn 点击）
- **触发**：点击发送按钮 `sendBtn`（ImageButton）。
- **空输入校验**：`contentEt.text.toString() == ""` → Toast `"你还木有输入喵~"`，不发。
- **发送流程**（后台线程）：
  1. 读取输入内容，`runOnUiThread { contentEt.setText("") }` 立即清空输入框。
  2. `PrivateMsgApi.sendMsg(SharedPreferencesUtil.getLong(mid, 114514), uid, PrivateMessage.TYPE_TEXT, System.currentTimeMillis()/1000, "{\"content\":\"$content\"}")` —— 用本机 mid 作发送者、纯文本类型、时间戳秒、JSON 包裹正文。
  3. 结果 `code == 0`：Toast `"发送成功"`，并 `refresh()` 刷新列表。
  4. `code == 21047`（特殊错误）：Toast `result.getString("message")`（显示 B 站返回的具体原因，如拦截/风控）；随后再 Toast `"发送失败"`。
  5. 其它 code：Toast `"发送失败"`。
  6. 解析异常（JSONException）：Toast `"发送失败：\n" + result`（原始内容）。
  7. 其它异常：`MsgUtil.err(e)`。

> 说明：本页**无表情面板、无图片发送、无已读回执 UI、无删除会话/删除消息**功能；仅文本发送 + 自动轮询 + 自动已读。

---

## ReplyFragment.kt（评论列表 Fragment）

继承 `RefreshListFragment`，用于在视频/动态详情页展示评论列表，支持排序切换、下拉刷新、上拉加载更多、发表评论即时插入、UP 主判定。

### newInstance 工厂（多种构造方式，对应不同入口）
1. `newInstance(aid, type)`：基础，args 含 `aid`、`type`。
2. `newInstance(aid, type, dontload)`：额外 `dontload`（是否初始不加载）。
3. `newInstance(aid, type, seek_rpid)`：额外 `seek`（定位到某条评论）。
4. `newInstance(aid, type, dontload, seek_rpid)`：组合。
5. `newInstance(aid, type, count, seek_rpid, up_mid)`：额外 `count`（评论总数）、`up_mid`（UP 主 id，用于判定是否楼主）。

### 参数解析（onCreate）
从 `arguments` 读取：`aid`（对象 id）、`count`、`type`（评论类型，同时赋给 `replyType`）、`dontload`、`seek`（默认 -1）、`mid`（UP 主 id，默认 -1）。

### 视图初始化（onViewCreated）
- `setForceSingleColumn()`：强制单列布局。
- **横屏边距**：若设置 `ui_landscape` 为真，用 `WindowManager` 取屏幕宽，`recyclerView.setPadding(width/6, 0, width/6, 0)` 左右各留 1/6 边距。
- 注册 `setOnRefreshListener { refresh(aid) }`（下拉刷新）。
- 注册 `setOnLoadMoreListener { continueLoading(it) }`（上拉加载更多）。
- `replyList = ArrayList()` 初始化。
- 若 `!dontload` → `refresh(aid)` 首次加载。

### UP 主/管理员判定（setManager）
- **触发**：外部（详情页）调用，传入 `source`（可为 `List<UserInfo>` 或单个 `UserInfo`）。
- 若本机未登录（`mid == 0`）直接返回。
- 若传入的是 `List`：遍历查找是否有 `userInfo.mid == 本机mid`，有则 `isManager = true`。
- 若传入的是单个 `UserInfo`：`source.mid == 本机mid` 即 `isManager = true`。
- `isManager` 会传给 `ReplyAdapter`（决定是否显示"删除/置顶/折叠"等 UP 管理操作）。
- 异常 `MsgUtil.err(e)`。

### 创建适配器（createReplyAdapter）
- `ReplyAdapter(requireContext(), replyList!!, aid, mid, 0L, replyType, sort, replyType)`：主列表 oid=aid、无 rpid（0）、评论类型、排序。

### 排序切换（setOnSortSwitch）
- **触发**：Adapter 的排序切换监听回调。
- **逻辑**：`sort` 在 2（最新）与 3（最热）之间切换（`sort == 2 ? 3 : 2`），同步 `replyAdapter.sort = sort`，然后 `refresh(aid)` 重新加载。
- 注意：初始 `sort = 3`（最热）。

### 下拉刷新（refresh(aid)）
- 重置 `pagination = ""`，`this.aid = aid`，`setRefreshing(true)`。
- 后台 `ReplyApi.getRepliesLazy(aid, seek, pagination, type, sort, list)` 取第一页。
- 返回 `pageState.first`（结果码）与 `.second`（下一页游标 pagination）。
- 成功且 `isAdded`：清空 `replyList` 并填充；若 adapter 为空则 `createReplyAdapter()` 并设置 `count`、`isManager`、`setOnSortSwitch()`、`setAdapter`；否则 `notifyDataSetChanged()`。
- 结果码 == 1 → `bottom = true`（没有更多）；否则 `bottom = false`。
- 异常 → `loadFail(e)`。

### 上拉加载更多（continueLoading）
- `ReplyApi.getRepliesLazy(aid, 0, pagination, type, sort, list)` 用游标取下一页，更新 `pagination`。
- `result != -1`：`replyList.addAll(list)` + `notifyItemRangeInserted(...)`；`result == 1` → `bottom = true`。
- 异常 → `loadFail(e)`。

### 发表评论即时插入（notifyReplyInserted，由 EventBus 的 ReplyEvent 驱动）
- **触发**：写评论页 `WriteReplyActivity` 发送成功后 `EventBus.post(ReplyEvent(...))`，此处订阅接收（基类事件总线）。
- 仅处理 `event.oid == aid` 的消息。
- **根评论（reply.root == 0L）**：找到第一个完全可见的 item 位置 `pos`（`max(pos,0)`），把新评论插入该位置，`notifyItemInserted` + `notifyItemRangeChanged`，并 `scrollToPositionWithOffset(pos+1, 0)` 滚动到新评论。
- **子回复（replyEvent.pos >= 0）**：定位到 `replyList[pos]` 的 `childMsgList`，`add` 新回复、`childCount++`，`notifyItemChanged(pos+1)` 刷新该楼。
- `pos < 0` 的情况（未指定位置）无处理分支。

---

## ReplyInfoActivity.kt（评论详情页 / 楼中楼）

继承 `BaseActivity`，布局 `activity_simple_refresh`，页面名 `setPageName("评论详情")`。展示"某条根评论 + 其楼中楼回复"，支持刷新、加载更多、排序切换、发表回复即时插入。

### Intent Extra（入口参数）
- **`rpid`（Long）**：目标根评论 id（必填，默认 0）。
- **`oid`（Long）**：所属对象 id（必填，默认 0）。
- **`type`（Int）**：评论类型码，默认 1；经 `ContentType.getContentType(int)` 解析为 `ContentType` 枚举，若非法类型抛 `ContentType.TerminalIllegalTypeCodeException`（转为 RuntimeException 崩溃）。
- **`up_mid`（Long）**：UP 主 id，默认 -1。
- **`is_manager`（Boolean）**：是否管理员/UP 主，默认 false。

### 初始加载（onCreate）
- `refreshLayout.setOnRefreshListener { refresh() }`（下拉刷新）。
- **横屏边距**：同 ReplyFragment，`ui_landscape` 时左右各留 1/6 屏宽。
- `refreshLayout.isRefreshing = true` 转圈。
- 通过 `TerminalContext.getInstance().getReply(type, oid, rpid).observe(this)` 获取根评论 `rootReply`（LiveData 观察，onSuccess/onFailure）。
- 成功后后台 `ReplyApi.getReplies(oid, rpid, page, type, sort, replyList!!)` 拉取楼中楼第一页。
  - `result != -1`：`replyList.add(0, rootReply)` 把根评论插到列表头部；创建 `ReplyAdapter(this, replyList!!, oid, up_mid, rpid, type.typeCode, sort, type.typeCode)`，设 `isManager`、`isDetail = true`；`setOnSortSwitch()`；`CustomLinearManager` + 设 adapter。
  - **滚动监听加载更多**：`onScrolled` 中，当最后一个可见 item `>= itemCount - 3` 且 `dy > 0` 且未在刷新且 `!bottom` → `refreshing = true`，后台 `continueLoading()`。
  - `result == 1` → `bottom = true`。
- 根评论获取失败 → `onPullDataFailed`（`MsgUtil.err` + 关转圈）。

### 上拉加载更多（continueLoading）
- `refreshLayout.isRefreshing = true` 显示转圈，`page++`。
- `ReplyApi.getReplies(oid, rpid, page, type, sort, list)` 取下一页。
- `result != -1`：`replyList.addAll(list)` + `notifyItemRangeInserted(...)` + 关转圈；`result == 1` → `bottom = true`。
- 结束 `refreshing = false`；异常 → `MsgUtil.err` + 关转圈。

### 下拉刷新（refresh）
- 重置 `page = 1`，转圈。
- 重新获取根评论 + `ReplyApi.getReplies(oid, rpid, 1, ...)` 第一页。
- `replyList.clear()` → `add(0, rootReply)` → `addAll(list)`；adapter 为空则重建（`isDetail = true`、`setOnSortSwitch`），否则 `notifyDataSetChanged()`。
- 关转圈；`result == 1` → `bottom = true` 否则 `bottom = false`。
- 失败 → `onPullDataFailed`。

### 排序切换（setOnSortSwitch）
- **触发**：Adapter 排序监听回调。
- **逻辑**：`sort` 在 `0` 与 `1` 之间切换（`sort == 0 ? 1 : 0`），然后 `refresh()` 重载。
- 注意：此处 sort 取值 0/1（与 ReplyFragment 的 2/3 不同体系）。

### 事件总线（EventBus）
- `eventBusEnabled()` 返回 `true` 启用。
- **`@Subscribe(threadMode = ASYNC, sticky = true, priority = 1) fun onEvent(ReplyEvent)`**：
  - 仅处理 `event.oid == oid` 的评论。
  - 找到第一个完全可见位置 `pos` 并 `pos--`；若 `pos <= 0` 改用第一个可见位置再 `pos--`；再 `pos = if (pos <= 0) 1 else pos`（保证最小为 1，即根评论之后）。
  - `replyList.add(pos, event.message)` + `notifyItemInserted` + `notifyItemRangeChanged` + `scrollToPositionWithOffset(pos+1, 0)` 滚动到新回复。
  - sticky 模式：即使稍后进入也能收到最近一次发送事件。

---

## WriteReplyActivity.kt（发表评论/回复页）

继承 `BaseActivity`，布局 `activity_write_reply`。支持文字、@回复、表情、图片（大会员）评论，发送后通过 EventBus 通知列表即时插入。

### Intent Extra（入口参数）
- **`oid`（Long）**：对象 id（必填）。
- **`rpid`（Long）**：回复的根评论 id（0 表示发表根评论）。
- **`parent`（Long）**：父评论 id（楼中楼回复的父级）。
- **`replyType`（Int）**：评论类型，默认 `ReplyApi.REPLY_TYPE_VIDEO`。
- **`parentSender`（String）**：被回复者昵称，用于预填 `"回复 @xxx :"`。
- **`pos`（Int）**：位置参数，随发送事件回传给列表定位插入。

### 登录校验
- 若 `SharedPreferencesUtil.getLong(mid, 0) == 0L`（未登录）→ Toast `"还没有登录喵~"` 并 `finish()` 关闭页面。

### 预填回复内容
- 若 `parentSender` 非空：`editText.setText("回复 @$parentSender :")`，光标移到末尾（`setSelection(text.length)`）。

### 发送评论（send 卡片点击）
- **触发**：点击 `send`（MaterialCardView）。
- **Cookie 刷新检查**：若 `SharedPreferencesUtil.getBoolean(cookie_refresh, true)` 为真（正常）→ 继续；否则 `MsgUtil.showDialog("无法发送", "上一次的Cookie刷新失败了，\n您可能需要重新登录以进行敏感操作", -1)` 弹窗拦截（不可发送）。
- **防重复发送**：`sent` 标志，发送中再次点击 → Toast `"正在发送中"`。
- **内容校验**：文字与图片都为空 → Toast `"还没输入内容呢~"`。
- **KY 保护（checkKy）**：检测文本是否提及"哔哩终端/终端"：
  - 含"哔哩终端" → 触发。
  - 含"终端"且同时含"表"或"b站"或"B站"或"bili"或"哔" → 触发。
  - 触发时：`MsgUtil.showDialog("保护措施……", getString(R.string.reply_dont_ky), 15)` 弹出确认框（15 秒倒计时），并置 `dontKyPlease = false`（本次会话只弹一次，之后直接发送）。
- **构造图片参数**：`buildPictures()` 把已上传图片数据拼成 JSON 数组字符串。
- **发送**：`ReplyApi.sendReply(oid, rpid, parent, text, replyType, pictures)`，返回 `Pair<resultCode, resultReply>`。
  - `resultCode == 0` 成功：Toast `"发送成功>w<"`；`resultReply.forceDelete = true`、`pubTime = "刚刚"`；把 `uploadDataList` 中的 `image_url` 全部加进 `resultReply.pictureList`；`EventBus.getDefault().post(ReplyEvent(1, resultReply, pos, oid))` 通知列表插入；`finish()` 关闭页面。
  - 失败：Toast `"评论发送失败：\n" + 错误文案`（`msgMap` 查表，见下），`sent = false` 允许重试。
- **错误码文案表（msgMap）**：
  - `-101` → "没有登录or登录信息有误？"
  - `-102` → "账号被封禁！"
  - `-509` → "请求过于频繁！"
  - `12015` → "需要评论验证码...？"
  - `12016` → "包含敏感内容！"
  - `12025` → "字数过多啦QAQ"
  - `12035` → "被拉黑了..."
  - `12051` → "重复评论，请勿刷屏！"
  - 其它码 → 直接显示数字。
- 异常 → `MsgUtil.err(e)`。

### 表情选择（emote 点击）
- **触发**：点击 `emote` 视图。
- `emoteLauncher.launch(Intent(this, EmoteActivity::class.java).putExtra("from", EmoteApi.BUSINESS_REPLY))` 打开表情选择页（评论业务的表情包）。
- **返回处理**：`resultCode == RESULT_OK` 且 data 含 `"text"` extra → `editText.append(该文本)` 在光标处追加表情文本。

### 添加图片（image 点击，仅大会员）
- **触发**：点击 `image` 视图。
- **大会员检测**：后台 `VipApi.getVipInfo()`，UI 线程判断 `vipInfo.isVip`：
  - 是大会员：若 `imageList.size >= 9` → Toast `"最多只能添加9张图片喵~"`；否则 `Intent(ACTION_GET_CONTENT)` + `type = "image/*"` 调起系统图片选择器（`imageLauncher`）。
  - 非大会员：Toast `"带图评论仅大会员可用喵~"`。
- **选择器返回**：`RESULT_OK` 且有 `data.data`：若 `imageList.size >= 9` → Toast `"最多只能添加9张图片喵~"`；否则 `addImage(data.data!!)`。

### 图片处理（addImage / compressImage / 上传）
- `imageList.add(uri.toString())`，`updateImageText()` 更新按钮文字（显示已选数量 `"图片(3)"`）。
- 后台：`compressImage(uri)` → 打开输入流、`BitmapFactory.decodeStream` 解码、JPEG 质量 100 压缩成 `ByteArray`。
- `ReplyApi.uploadReplyImage(compressed, System.currentTimeMillis() + ".jpg")` 上传，成功返回 `UploadImageData`（含 image_url/宽/高/大小）加入 `uploadDataList`。
- 上传失败：Toast `"图片上传失败"`，从 `imageList` 移除该 uri 并 `updateImageText()`。
- 处理异常：Toast `"图片处理失败"`，同样移除并刷新。
- **buildPictures()**：把 `uploadDataList` 中每个数据构造成 `{img_src, img_width, img_height, img_size}` 的 JSONObject，合成 JSONArray 字符串作为发送参数。

### 图片数量按钮文字（updateImageText）
- `imageText.text = if (count == 0) getString(R.string.btn_image) else getString(R.string.btn_image) + "($count)"`，显示已选图片数。

---

## 汇总要点

- **消息中心（MessageActivity）**：设置入口 + 回复/赞/@/系统 4 个通知入口 + 私信会话列表（未读优先排序）+ 未读数字 + 清角标 + 教程。各通知入口通过 `NoticeActivity` 的 `type` Extra 区分。
- **消息设置（MessageSettingsActivity）**：6 个服务端驱动的接收偏好开关（消息提醒二选一 + 5 个开关），变更即保存，失败自动回滚重载。
- **通知列表（NoticeActivity）**：按 type 加载"点赞/回复/@/系统"消息；like/reply/at 用游标分页加载更多；system 一次性全量。无独立点赞/举报/删除等交互（在 NoticeAdapter 中实现，不在此文件）。
- **私信聊天（PrivateMsgActivity）**：文本发送 + 15 秒自动轮询 + 自动已读 + 滑到顶部加载更早 + 输入框滚动滑入/滑出动画。**无**表情/图片/删除会话功能。
- **评论列表（ReplyFragment）**：最热(3)/最新(2)排序切换 + 下拉刷新 + 游标分页 + UP 主/楼主判定 + 发表评论/回复即时插入（EventBus）。
- **评论详情（ReplyInfoActivity）**：根评论 + 楼中楼，0/1 排序切换 + 滚动自动加载更多 + 下拉刷新 + 回复即时插入（sticky EventBus）。
- **发表评论（WriteReplyActivity）**：文字 + @预填 + 表情（EmoteActivity）+ 大会员带图（最多 9 张，先上传再发送）+ KY 保护弹窗 + 详细错误码文案 + Cookie 失效拦截 + 发送成功 EventBus 通知。