# 搜索功能审查报告（group-03-search）

> 审查对象：RE:哔哩终端（ReBiliClient）搜索相关源码。
> 颗粒度：极其细小，按"功能 + 具体用法/触发方式"逐条列出，不漏任何一处交互、逻辑与隐藏功能。

---

## SearchActivity.kt

搜索主页面（`activity_search` 布局）。承载搜索框、历史记录、搜索建议、热搜彩蛋、分类 ViewPager2 切换、标题栏返回、搜索栏隐藏动画等。

### 页面初始化与全局状态
- **搜索页入口**：`onCreate` 中 `asyncInflate(R.layout.activity_search)` 异步加载布局；进入时打印 `Log.e("debug","进入搜索页")`。
- **教程引导（Tutorial）**：`TutorialHelper.showTutorialList(this, R.array.tutorial_search, 4)` 展示搜索页分页教程（4 步）。教程是否已显示过按 `tutorial_pager_<类名>` 的 SharedPreferences 布尔值控制（默认 true=显示）。
- **分类动态列表**：`categoryList` 为启用的搜索分类 key 列表，`defaultCategoryOrder = ["video","article","user","audio","live"]` 为默认顺序。视频始终启用且固定在第一位；其余按设置开关启用/排序。
- **ViewPager2 配置**：`offscreenPageLimit = maxOf(1, categoryList.size - 1)`；允许用户手势滑动切换（`isUserInputEnabled = true`）。

### 搜索框（keywordInput）
- **输入框默认 Hint**：若设置 `SEARCH_DEFAULT_CONTENT_ENABLE` 开启，后台线程调用 `SearchApi.getDefaultSearchContent()` 拉取"默认搜索内容"，成功后把 `keywordInput.hint` 设为该内容（用户未输入时提示）。失败仅打印日志不打扰。
- **焦点变化联动**：
  - 获得焦点且关键字为空、或建议功能关闭 → 显示历史记录列表（`historyRecyclerview` VISIBLE），隐藏建议列表。
  - 获得焦点且关键字非空（建议开启）→ 隐藏历史，显示建议列表。
  - 失去焦点 → 历史与建议列表都隐藏（GONE）。
- **软键盘回车/发送键**：`setOnEditorActionListener`，当 actionId 为 `IME_ACTION_SEND` 或 `IME_ACTION_DONE`，或按下实体键盘 ENTER（ACTION_DOWN）时触发 `searchKeyword()` 搜索。
- **搜索按钮点击**：`searchBtn` 点击 → `searchKeyword(keywordInput.text.toString())` 执行搜索。
- **搜索按钮长按（隐藏功能）**：`searchBtn.setOnLongClickListener { jumpToTargetId(it) }` → 把输入框当前文本交给 `LinkUrlUtil.handleId(this, text)`，用于解析并跳转到 ID/链接对应的页面（支持输入 AV/BV/用户 ID 等直达）。返回 true 消费长按。

### 历史记录管理（searchHistory）
- **数据来源**：从 SharedPreferences 键 `search_history` 读取 JSON 数组字符串（默认 `"[]"`），经 `JsonUtil.jsonToArrayList(..., false)` 解析；解析失败 Toast 报错并置空列表。
- **历史列表显示**：`SearchHistoryAdapter` 绑定到 `historyRecyclerview`（CustomLinearManager 线性布局）。
- **点击历史项**：`setOnClickListener` → 把该历史词填入 `keywordInput`（`setText`），即回填但不自动搜索。
- **长按历史项（删除单条）**：`setOnLongClickListener` → Toast "删除成功"，从列表移除该项，`notifyItemRemoved` + `notifyItemRangeChanged` 刷新，并把新列表写回 `search_history` 持久化。
- **历史条数>4 时抢焦点**：若历史超过 4 条，`historyRecyclerview` 设为 focusable 并 `requestFocus()`（便于键盘方向键操作历史列表）。
- **搜索时写入历史**：见 `searchKeyword` 逻辑——新词插入头部；已存在的词移到头部（去重置顶），同时持久化并滚动列表到顶部。
- **历史列表显隐**：搜索执行/失去焦点时 `historyRecyclerview` 被置为 GONE。

### 搜索建议（searchSuggestions）
- **开关**：`suggestionsEnabled = getBoolean("search_suggestions_enable", true)`，关闭则不监听输入变化、不拉建议。
- **输入监听**：`TextWatcher.afterTextChanged`——每次输入变化先 `removeCallbacks` 取消上一次未执行的建议任务。
  - 关键字为空：焦点存在时显示历史、隐藏建议。
  - 关键字非空：300ms 防抖后（`handler.postDelayed(..., 300)`）起后台线程调 `SearchApi.getSearchSuggestions(keyword)`；返回后若输入框仍聚焦，则更新建议列表 `notifyDataSetChanged`；有建议 → 隐藏历史、显示建议；无建议 → 显示历史、隐藏建议。
- **点击建议项**：`setOnClickListener` → 回填输入框、光标移到末尾（`setSelection(suggestion.length)`），并立即执行 `searchKeyword(suggestion)` 搜索。
- **建议列表控件**：`SearchSuggestionsAdapter` + `suggestionsRecyclerview`（CustomLinearManager）。

### 分类切换（ViewPager2 + 动态 Fragment）
- **适配器**：`FragmentStateAdapter`，`getItemCount` = `categoryList.size`；`createFragment` 按 key 调 `createFragmentForCategory`：
  - `video` → `SearchVideoFragment`（默认/兜底）
  - `article` → `SearchArticleFragment`
  - `user` → `SearchUserFragment`
  - `audio` → `SearchAudioFragment`
  - `live` → `SearchLiveFragment`
- **页面标题联动**：`updatePageName(position)` 把标题设为"搜索-视频 / 搜索-专栏 / 搜索-用户 / 搜索-音频 / 搜索-直播"（按当前分类 key 匹配中文）。
- **滑动切换（onPageScrolled）**：当滑离第 0 页（`position != 0 && position != lastPosition`）时调用 `onScrolled(256)`（触发搜索栏隐藏动画），并在首次滑动时隐藏教程指示文字 `text_tutorial_pager`、写回 `tutorial_pager_<类名>=false`。
- **选中页（onPageSelected）**：更新标题；通过 `findFragmentByTag("f$position")` 找到当前分类 Fragment，调用其 `refresh()` 刷新（搜索后各页联动刷新）。

### 分类开关与排序配置（设置联动）
- **排序配置**：`buildCategoryList()` 读取 `SEARCH_CATEGORY_SORT`（分号分隔的 key 顺序）；若有效（段数=5）按其顺序排列，否则用默认顺序；只加入启用的分类；最后确保 `video` 一定存在并放首位。
- **各分类开关**：`isCategoryEnabled(key)`——
  - `video` 恒 true；
  - `article` → `SEARCH_CATEGORY_ARTICLE_SHOW`（默认 true）；
  - `user` → `SEARCH_CATEGORY_USER_SHOW`（默认 true）；
  - `audio` → `SEARCH_CATEGORY_AUDIO_SHOW`（默认 true）；
  - `live` → `SEARCH_CATEGORY_LIVE_SHOW`（默认 true）；
  - 其它 key → false。
- **设置变更热更新**：`onResume` 里若分类列表已初始化，重新 `buildCategoryList()`；当类别数量变化时 `notifyDataSetChanged()` 并更新 `offscreenPageLimit`；无论是否变化都刷新标题 `updatePageName(viewPager.currentItem)`。即从设置页返回后分类开关/排序即时生效。

### 搜索执行核心（searchKeyword）
- **彩蛋拦截（特殊关键词）**：命中则弹对应 Toast/内容并 return，不发起真实搜索：
  - 关键词含 "Robin"/"robin" 且含 "撅" → `R.string.egg_special`（特殊彩蛋）。
  - 含 "Robin"/"robin" 且含 "纳西妲" → `R.string.egg_robin_nahida`。
  - 命中 `specialList = ["心理疾病","自杀","自尽","自残","抑郁","双相","安眠药"]` → `R.string.egg_warmwords_warmworld`（暖心词彩蛋）。
  - 命中 `specialNamesList = ["严炜","陈学峰","徐波","易德元","舒微函","张自东","杨国明","张俊胜"]` → `R.string.egg_special_names`。
- **软键盘收起**：搜索前通过 `InputMethodManager.hideSoftInputFromWindow` 隐藏软键盘。
- **空关键字兜底**：关键字为空时，若"默认搜索内容"功能开启且有值则用默认内容；否则 Toast "还没输入内容喵~" 并 return。
- **重复关键词去抖**：若新关键字与 `lastKeyword` 相同（`Objects.equals`）→ 仅清除输入框焦点、隐藏历史，不重新搜索（避免重复请求）。
- **写入/更新历史**（见上"历史记录管理"）：新词插 0 位、已存在词移到 0 位；`notifyItemInserted/Moved` + `notifyItemRangeChanged`，滚动到顶部，并持久化 `search_history`。
- **通知各分类页更新**：遍历 `categoryList`，对每个 `findFragmentByTag("f$i")` 调用 `(SearchFragment).update(keyword)`（重置页码=1、记录 keyword、置 refreshable）；再对当前页 `refresh()` + `requestFragmentFocus()`。
- **教程提示刷新**：若教程未显示过，搜索后显示 `text_tutorial_pager` 提示文案（含"可左右滑动切换分类，共 categoryList.size-1 页"）。
- **并发保护**：`refreshing` 标志避免搜索执行中重复触发。

### 搜索栏隐藏/显示动画（onScrolled）
- **滚动驱动**：当 `dy > 0` 且搜索栏当前可见 → 用 `ObjectAnimator` 将 searchBar `translationY` 从 0 移到 `-(searchBar.height + 2dp)` 隐藏，200ms 后 `visibility=GONE`（有 200ms 动画节流 `animate_last`）。
- **上滑回显**：当 `dy < -1` 且搜索栏已隐藏 → 先置 VISIBLE 再从负偏移动画回 0 位显示。
- **调用来源**：分类页 RecyclerView 滚动回调 `onScrolled(dy)`；以及切换页面时 `onScrolled(256)` 强制触发。滚动后还会 `requestFragmentFocus()`。

### 焦点转移（requestFragmentFocus）
- 找到当前分类 Fragment，调用其 `refresh()`；若其视图已创建，把内部 `R.id.recyclerView` 设为 focusable/focusableInTouchMode 并 `requestFocus()`（让方向键/滚动焦点进入列表，配合历史列表抢焦点）。

### Intent Extra（外部跳入）
- **`intent.getStringExtra("keyword")`**：若非空，则点击标题栏 `top` 区域 → `finish()` 返回详情页；并把该 keyword 填入搜索框（`setText`），Toast "可点击标题栏返回详情页"。即从详情页"搜索"入口带关键词进入搜索页，且可点标题栏返回。

---

## SearchFragment.kt

搜索分类页的公共基类（`fragment_simple_refresh` 布局：SwipeRefreshLayout + RecyclerView + emptyTip）。视频/专栏/用户/音频/直播页都继承它。

- **布局**：`onCreateView` inflate `fragment_simple_refresh`；`onViewCreated` 取 `emptyTip`（空视图 TextView）、`swipeRefreshLayout`（默认 `isEnabled=false`，由子类刷新逻辑接管）、`recyclerView`。
- **列表管理器自适应**：`getLayoutManager()`——若设置 `ui_landscape` 为 true 返回 `CustomGridManager(3列)`，否则 `CustomLinearManager`（竖屏线性）。
- **列表性能优化**：`setHasFixedSize(true)`、`setItemViewCacheSize(20)`、`isNestedScrollingEnabled=true`；若为 LinearLayoutManager 则 `initialPrefetchItemCount=4` + `isItemPrefetchEnabled=true`（预取优化）。
- **滚动监听（双职责）**：
  - **触发父页搜索栏动画**：`onScrolled` 里把 `dy` 传给 `SearchActivity.onScrolled(dy)`；`SCROLL_STATE_DRAGGING` 且列表顶部（`!canScrollVertically(-1)`）时传 `-114`（模拟上滑回显搜索栏）。
  - **分页加载（加载更多）**：向下滚（`dy>0`）且非刷新中、未到底（`!bottom`）、有 listener 时，当 `lastVisibleItemPosition >= itemCount - 3` 触发 `goOnLoad()`。
- **图片懒加载**：`ImageAutoLoadScrollListener.installIfEnabled(recyclerView)` 安装图片自动加载监听。
- **分页加载节流（goOnLoad）**：距上次加载 >100ms 才执行；`page++`，置 `swipeRefreshLayout.isRefreshing=true`，调用 `listener.onLoad(page)`，记录 `lastLoadTimestamp`。
- **到底提示（bottom）**：`bottom` setter——当 `page==1` 时据 `value` 显示/隐藏空视图；当 `page>1` 且 `value=true` 时 Toast "已经到底啦OwO"（即非首屏滚动加载完提示已到底）。
- **刷新控制**：`setRefreshing(bool)` 主线程设置 SwipeRefreshLayout 刷新圈；`setOnRefreshListener` 存回调。
- **页面状态**：
  - `page` 页码；`keyword` 当前关键词；`refreshable` 是否可刷新；`lastLoadTimestamp` 上次加载时间。
  - `update(keyword)`：重置 `page=1`、记录 keyword、`refreshable=true`、`bottom=false`（由 SearchActivity 在新搜索时对每个分类页调用）。
  - `refresh()`：若 `refreshable` 则置 false、`setRefreshing(true)`、调 `refreshListener.onRefresh()`。
- **空视图**：`showEmptyView(empty)` 主线程切换 `emptyTip` 显隐。
- **失败处理**：`loadFail()` → `page--`、Toast "加载失败"、停刷新圈；`loadFail(e)` → `page--`、`report(e)`（MsgUtil 报错）、停刷新圈。
- **线程工具**：`runOnUiThread` 若已 attach 则走 `requireActivity().runOnUiThread`。
- **`refreshInternal()`**：`protected open` 空实现，由子类覆写实际刷新逻辑。

---

## SearchVideoFragment.kt

视频搜索结果页（继承 SearchFragment）。

- **初始化**：`VideoCardAdapter` 绑定视频列表，`setOnRefreshListener { refreshInternal() }`、`setOnLoadMoreListener { page -> continueLoading(page) }`。
- **加载/分页（continueLoading）**：后台线程调 `SearchApi.search(keyword, page)` 取结果；`SearchApi.getVideosFromSearchResult(result, list, page==1)` 解析视频卡片（首屏带分区标题等）。返回空 → `bottom=true`；否则主线程 `addAll` + `notifyItemRangeInserted(lastSize+1, ...)` 追加。异常 `loadFail(e)`。最后停刷新圈。
- **刷新（refreshInternal）**：主线程把 `page=1`、清空列表（`notifyItemRangeRemoved`），再起线程 `continueLoading(1)` 重新拉第一页。
- **分类 key**：`video`；点击视频卡片跳转逻辑由 `VideoCardAdapter` 内部实现（本文件未覆写点击）。

---

## SearchArticleFragment.kt

专栏搜索结果页（继承 SearchFragment）。

- **初始化**：`ArticleCardAdapter` 绑定专栏列表；设置刷新与加载更多监听。
- **加载/分页（continueLoading）**：后台线程调 `SearchApi.searchType(keyword, page, "article")`（返回 JSONArray）；`SearchApi.getArticlesFromSearchResult(result, list)` 解析文章卡片。空 → `bottom=true`；否则主线程 `addAll` + `notifyItemRangeInserted` 追加。异常 `report(e)`。停刷新圈。
- **刷新（refreshInternal）**：主线程 `page=1`、清空列表、`continueLoading(1)` 重拉。
- **分类 key**：`article`；点击跳转由 `ArticleCardAdapter` 内部实现。

---

## SearchAudioFragment.kt

音频搜索结果页（继承 SearchFragment）。注意：音频分类实际仍调用"视频"搜索接口（`search_type=video` + `tids=30`），并自建 AudioAdapter 渲染为音频条目。

- **初始化**：`AudioAdapter`（内部类）绑定音频列表；设置刷新与加载更多监听。
- **加载/分页（continueLoading）**：后台线程手工拼 URL：`https://api.bilibili.com/x/web-interface/wbi/search/type?search_type=video&page=...&keyword=URLEncode(...)&tids=30&order=totalrank`，经 `ConfInfoApi.signWBI(url)` 签名、`NetWorkUtil.getJson` 请求。
  - `data` 为 null → `bottom=true` 停刷新圈返回。
  - `result` 为空 → `bottom=true`。
  - 否则逐条解析：title 去掉 `<em class="keyword">/</em>` 高亮标签、author、pic（无 http 前缀时补 `https:`）、duration（`parseDuration` 转秒）、bvid（存到 `audio.lyricUrl` 字段复用）。组装 `AudioInfo` 追加，`notifyItemRangeInserted`。
  - 异常 `loadFail(e)`。
- **时长解析（parseDuration）**：`"MM:SS"` 格式 → `分*60+秒`；仅支持 2 段；非法/空返回 0。
- **刷新（refreshInternal）**：主线程 `page=1`、清空列表、`continueLoading(1)`。
- **AudioAdapter（内部类）**：
  - `setHasStableIds(true)`，`getItemId = audio.sid`（稳定 ID）。
  - 布局 `cell_audio_list`：标题 `audio_title`、作者 `audio_author`、时长 `audio_duration`（`duration>0` 时格式化为 `分:秒`）。
  - **点击条目**：构造 Intent 到 `AudioPlayerActivity`，携带 extra：`sid`、`title`、`author`、`cover`，`startActivity` 播放该音频。
- **分类 key**：`audio`（实际请求类型为 video+tids=30）。

---

## SearchLiveFragment.kt

直播搜索结果页（继承 SearchFragment）。

- **初始化**：`LiveCardAdapter` 绑定直播间列表；设置刷新与加载更多监听。
- **加载/分页（continueLoading）**：后台线程调 `SearchApi.searchType(keyword, page, "live")`。
  - 结果若是 JSONObject → 取 `live_room` 数组；若是 JSONArray → 直接用；`LiveApi.analyzeLiveRooms(jsonArray)` 解析为 `LiveRoom` 列表。空 → `bottom=true`；否则主线程 `addAll` + `notifyItemRangeInserted`。
  - 异常 `report(e)`。
  - 特殊：若 `bottom && roomList.isEmpty()` → `showEmptyView(true)`（第一页无结果时显示空视图）。
- **刷新（refreshInternal）**：主线程 `page=1`、清空列表、`continueLoading(1)`。
- **分类 key**：`live`；点击跳转由 `LiveCardAdapter` 内部实现。

---

## SearchUserFragment.kt

用户搜索结果页（继承 SearchFragment）。

- **初始化**：`UserListAdapter` 绑定用户列表（`lateinit`）；设置刷新与加载更多监听。
- **加载/分页（continueLoading）**：后台线程调 `SearchApi.searchType(keyword, page, "bili_user")`（返回 JSONArray）；`SearchApi.getUsersFromSearchResult(result, list)` 解析用户。空 → `bottom=true`；否则主线程 `addAll` + `notifyItemRangeInserted`（注意此处插入起点用 `lastSize`）。异常 `loadFail(e)`。停刷新圈。
- **刷新（refreshInternal）**：先同步置 `page=1`，主线程清空列表、`continueLoading(1)`。
- **分类 key**：`bili_user`；点击用户跳转由 `UserListAdapter` 内部实现。

---

## HotSearchActivity.kt

热搜榜页面（继承 `RefreshMainActivity`，位于 `activity/video` 包）。

- **页面标题**：`setPageName("热搜")`。
- **菜单点击**：`setMenuClick()`（沿用基类的标题栏菜单行为）。
- **首次加载**：`onCreate` 即调 `loadHotSearch()`。
- **下拉刷新**：`setOnRefreshListener { loadHotSearch() }`，下拉重拉热搜。
- **加载逻辑（loadHotSearch）**：置刷新圈；后台线程调 `HotSearchApi.getHotSearch(newList)`：
  - 成功 → `hotList = newList`，主线程 `applyResult()`。
  - 失败（返回 false）→ 主线程停刷新圈 + Toast "获取热搜失败，请稍后重试"。
  - 异常 → `report(e)` + 主线程停刷新圈 + Toast "网络异常，请稍后重试"。
- **结果应用（applyResult）**：adapter 未建则 `HotSearchAdapter(this, hotList)` 挂到 `recyclerView`；已建则 `notifyDataSetChanged()`；最后停刷新圈。榜单点击/长按行为由 `HotSearchAdapter` 内部实现（本文件未覆写）。

---

## 附：支撑适配器（补充核对）

### SearchHistoryAdapter.kt（历史列表适配器）
- 布局 `cell_choose`（文本 `R.id.text`，`StringUtil.htmlToString` 转义 HTML）。
- **点击** → `clickListener.onItemClick(position)`。
- **长按** → `longClickListener.onItemLongClick(position)` 并返回 true（消费长按）；无监听返回 false。
- SearchActivity 中：点击=回填输入框；长按=删除单条历史。

### SearchSuggestionsAdapter.kt（搜索建议适配器）
- 布局 `cell_choose`（文本 `R.id.text`，`StringUtil.htmlToString`）。
- **点击** → `clickListener.onItemClick(position)`（无长按逻辑）。
- SearchActivity 中：点击=回填输入框+光标末尾+立即搜索。
