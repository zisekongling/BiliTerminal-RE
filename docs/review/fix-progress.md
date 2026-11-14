# 修复台账（活文档）

> 这是**唯一**的当前状态 + 待办台账。完整历史正文已归档到 `docs/archive/review/fix-progress-history.md`（§一~§四十三 逐批次流水，4777 行）。
> 什么时候写这里：修完一个 bug / 关掉一个待办 / 每轮真机验证后。**别再往下追加长流水**——超过一屏就并进 §4「历史章节索引」那一行（§3 是 26.11.06 之前就积下的旧流水，只读不追加）。
> 架构与坑看 `docs/architecture-map.md`；功能清单看 `docs/FEATURES.md`；真机清单看 `docs/review/real-device-regression-checklist.md`（旧的 T/D 测试计划已归档为 `docs/archive/review/device-test-plan.md`，只在要查某条用例原文时翻）。

**怎么读这份文件（26.11.08 实测：全文 ≈14.9 万 token / 1,814 行，别整份读）**：

| 章节 | 行 | token | 什么时候读 |
| --- | --- | --- | --- |
| §1 当前版本状态 | `19–164` | ≈17.9k | **每次改动前必读** |
| §2 未完成待办 | `165–338` | ≈12.7k | 接线前扫一眼；按关键词 grep，别整节读 |
| §3 状态快照 | `339–1671` | ≈9.2 万 | **纯流水**，只在要查「某轮改动到底怎么改的」时 grep 关键词 |
| §4 历史章节索引 | `1672–1774` | ≈2.2 万 | 一般不用读 |
| §5 待回迁长期结论 | `1775–1814` | ≈3.7k | 要查某个长期结论时 |

> 除 §1 外都不要通读：**先 `grep -n "关键词" docs/review/fix-progress.md` 拿到行号，再只读那一段**。行号随文件增长会漂移，对不上就用 `grep -n "^## [0-9]\."` 重新定位章节。

## 1. 当前版本状态

- **26.11.14 进度条标记与「跳不跳」解耦、按类别配色、补上精彩时刻开关（用户原话「这个进度条标记显示单独开一个设置，别跟跳过策略绑在一起，能只显示条子不跳、或者反过来 另外这些设置选项旁边给一个参考色显示这一项的对应颜色」「精彩时刻设置去哪了我不知道」）**：
  - **来路**：三档上线后条子仍只画「自动跳转」那几类，用户要的是两件事彻底分开——能只显示不跳（看得见哪儿有广告，但自己不动），也能只跳不显示（不想被色块打扰）；同时设置页每一行看不出这一项对应什么颜色，以及精彩时刻的三角此前根本没有开关（一直跟着画、与三档无关）。
  - **修法**：① `player/SponsorSkipPolicy.kt` 新增 `markColor(category): Int`（11 类各一个固定色，未知类别走 `MARK_COLOR_FALLBACK`）、`data class MarkedSegment(segment, category, color)`、`markSegments(raw, categories, cid, currentDurationSec, ignoredUuids)`——**类别集合来自「标记」自己的存档**，与三档无关；口径与 `buildSegments` 一致（只认 `skip`、cid、`to > from`、单段上限、换源校验、忽略集），另外多滤 `hidden`。② `util/SettingsKeys.kt` 新增 `PLAYER_SPONSOR_BLOCK_MARKS_ENABLED` / `PLAYER_SPONSOR_BLOCK_MARKS_POI_ENABLED` / `PLAYER_SPONSOR_BLOCK_MARK_CAT_PREFIX`；`player/SponsorSkipPrefs.kt` 新增 `isMarksEnabled()` / `isPoiMarksEnabled()`（都默认 true、互不影响）、`needsSegments()`（= 总开关 &&（三档任一类开着 || 条子开 || 三角开），播放页拉数据的闸）、`markCategoryKey()` / `isCategoryMarked()` / `markCategories()` / `storeCategoryMarked()`。③ `activity/player/PlayerActivity.kt`：`maybeAutoSkipSponsor()` 开头改成「`!needsSegments()` 时先擦掉已画的条子与三角再 return（播放途中把三个开关全关，旧色块不许留在进度条上），否则 `ensureSponsorSegments()`，三档全关但标记开着就直接 return（只画不跳）」；新增字段 `sponsorMarkSegments`；`applySponsorLevel()` 的签名键扩成 `自动|询问|忽略集|已点精彩时刻|条子开|三角开|标记类别`，条子由 `markSegments(...)` 单独算，三角受 `isPoiMarksEnabled()` 控制；`updateSponsorMarks()` 每条用自带颜色、三角用 `markColor(CAT_POI_HIGHLIGHT)`；括号改按「自动 + 询问」两类合计。④ `adapter/SettingsAdapter.kt` + `res/layout/cell_setting_list_choose.xml`：`ListChooseExtra` 新增 `swatchColor`，名字左边多一个 10dp 小色块（默认 GONE，只有传了颜色才显示；`contentDescription=@null` + `importantForAccessibility=no` 以满足布局无障碍测试）。⑤ `activity/settings/SettingSkipActivity.kt` 增加「进度条标记」区块（总开关 + 「标记哪些类别」nav + 三角开关 + 色块说明），类别行传 `markColor()`，并补 `onResume` 重建列表（从标记页回来要刷新「已勾 N / 10 类」）；新建 `activity/settings/SettingMarkCategoryActivity.kt`（逐类别 switch，`player_sponsor_block_mark_cat_<类别>`）；`AndroidManifest.xml` 注册；`activity/settings/SettingsIndex.kt` 收录新页与新增两条叶子。⑥ `activity/video/info/VideoInfoFragment.kt` 的请求闸从 `isEnabled()` 放宽成 `needsSegments()`。
  - **口径**：三个开关（三档 / 条子 / 三角）各一份存档、互不推翻；只显示不跳时一个片段都不跳、一个「不准」按钮都不弹；只跳不显示时进度条上什么都不画，但跳与括号照旧；全部关掉时擦掉残留标记且不再发请求。
  - **L0（26.11.14 本机实测）**：`:app:compileStandardDebugKotlin :app:compileCoexistDebugKotlin` → BUILD SUCCESSFUL（1m29s）；`:app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --no-build-cache` → BUILD SUCCESSFUL，standard 侧 **2602 tests / 0 failures**（新增 `player/SponsorMarkTest.kt` 15 例：颜色互不相同、未知类别兜底、精彩时刻与广告不同色、标记集合为空不画、三档全关也能拿到条子、没勾的类别不画、每条自带颜色、`hidden` 不画、忽略集不画、非 skip 不画、别的分 P 不画、零长度不画、换源前不画、总时长未知不校验、按起点排序）；`:app:assembleDebug` → BUILD SUCCESSFUL（28s，双 flavor）。
  - **未验证（L2，只能真机看）**：色块与条子颜色是否一致、只显示不跳 / 只跳不显示两种组合的实际观感、三角开关即时生效、播放途中全关是否立刻清空；见 `docs/review/manual-test-guide-261114-exp4.md` 的 G 组与 `docs/review/l2-pending.md` §G。
- **26.11.14 「跳过设置」选完档位不回弹、设置其实没存下来（用户原话「设置后好像这个显示依旧显示关闭，不知道是设置本身未生效还是设置的显示问题」）**：
  - **来路**：exp.2 里点「广告」这一行选「自动跳转」，回到本页仍显示「关闭」，播放页也照旧不跳。用户怀疑是「没生效」还是「显示问题」——**两者都是**：值根本没写进存档，而行的当前值又是每次 bind 时按存档算的，所以显示自然也不变。
  - **根因**：`list_choose` 的值由 `activity/settings/SettingChooseResult.kt` 的 `applyListChooseResult()` 写回，它必须由页面在 `onActivityResult` 里调用；`activity/settings/SettingSkipActivity.kt` 是新建页，**漏了这个回调**。同款事故 26.11.08 在 `SettingTerminalPlayerActivity` 上已经发生过一次（那个文件里还留着注释），这次是新页重犯。
  - **修法**：① `SettingSkipActivity` 把 `sections` 提为字段、持 `settingsAdapter`，新增 `onActivityResult` 调 `applyListChooseResult(requestCode, resultCode, data, sections) { settingsAdapter?.notifyItemChanged(it) }`；② 顺带把逐类默认值改成先调一次 `SponsorSkipPrefs.modes()` 再取值——否则升级用户在这一页会先看到「全关」，因为老存档的折算原本只在播放页首次读时才触发；③ `docs/agents/dev-conventions.md` 的「新增设置项与设置子页面」补一条硬规则：页面里出现 `list_choose` 就必须接这个回调。
  - **口径**：写回与刷新都由 `applyListChooseResult` 统一负责，页面不再自己 `putString`；`sections` 必须与列表位置一一对应（`ListChooseActivity` 回传的是 position）。
  - **L0（26.11.14 本机实测）**：两 flavor `compile` → BUILD SUCCESSFUL（21s）；`:app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --no-build-cache` → BUILD SUCCESSFUL，**2587 tests / 0 failures**；`:app:assembleDebug` → BUILD SUCCESSFUL（46s）。
  - **未验证（L2，只能真机看）**：选完当场回弹、退出再进仍保留、改完回播放页即时生效；见 `docs/review/manual-test-guide-261114-exp3.md` 的 A04/A05/B08。
- **26.11.14 跳过设置改成「逐类别三档」并做成二级页（用户原话「对于除了番剧以外的其他类别的跳转功能都分三档，关闭，询问我，自动跳转 并重构跳过设置为二级菜单」）**：
  - **来路**：用户反馈开着「自定义」档却一条都没跳（静态复核未复现出 bug，见下条），加上「六档 + 逐类别勾选」本身是两套存档——改档位会让勾选形同虚设，语义上就不该继续。用户定：除番剧外其他类别一律三档（关闭 / 询问我 / 自动跳转）；「询问我」复用片头片尾那套提示；番剧完全不归这条链路管；入口放在「选择播放器」页的「播放器界面」与「清晰度」之间。
  - **修法**：① `player/SponsorSkipPolicy.kt` 新增三档常量与纯函数 `MODE_OFF/MODE_ASK/MODE_AUTO/MODE_COUNT`、`MODE_CATEGORIES`（= 全部类别去掉 `poi_highlight`）、`fromStoredMode()`（非法/越界一律回落「关闭」，绝不 coerce 成自动）、`modeOf()`、`categoriesWithMode()`、`isAnyModeEnabled()`、`migrateLevel()`（老六档按原预设展开成「自动」，`LEVEL_CUSTOM` 用当时勾选）；老六档常量与 `categoriesFor/resolveCategories/isEnabled(level)` 保留给迁移与详情页胶囊。② `player/SponsorSkipPrefs.kt` 新增 `modeKey()/storedMode()/storeMode()/modes()/autoCategories()/askCategories()/effectiveCategories()`，`modes()` 首次读时把老档位折算写回（`PLAYER_SPONSOR_BLOCK_SKIP_MODE_MIGRATED` 只折一次），`isEnabled()` 改成「总开关 && 至少一类不是关闭」；`util/SettingsKeys.kt` 新增 `PLAYER_SPONSOR_BLOCK_SKIP_MODE_PREFIX`。③ `activity/player/PlayerActivity.kt`：`maybeAutoSkipSponsor()` 分两条——自动类走 `sponsorDecider` 直接跳 + 「不准」，询问类落到 `sponsorAskSegments` 并在到点时弹一次 `showSponsorAskSnack()`（同一段只问一次，`sponsorAskedKeys` 与片头片尾的 `askedSkipKeys` 分开）；`applySponsorLevel()` 的签名键扩成 `自动集合|询问集合|忽略集|已点精彩时刻`，换 P reset 清询问表与去重集合。④ 新建 `activity/settings/SettingSkipActivity.kt`（片头片尾五档 + 10 个类别三档 + 清除「不准」），承接原 `SettingSponsorCategoryActivity`；`player/PlayerSettingsGroups.kt` 的 `skip()` 六档整块换成一行 nav；`activity/settings/SettingPlayerChooseActivity.kt` + `res/layout/activity_setting_player_choose.xml` 在「播放器界面」与「清晰度」之间插入「跳过设置」卡片；`AndroidManifest.xml` 换掉 Activity 条目；`activity/settings/SettingsIndex.kt` 的搜索索引改指向新页并收录类别名。⑤ `res/values/player_sb_strings.xml` 增 `player_sb_mode_ask/player_sb_mode_auto/player_sb_ask_skip/player_skip_page_*`，删掉六档与「要跳的类别」页的死串。
  - **口径**：「询问我」几秒不理就等于不跳，同一段只问一次；「关闭」的类别连请求结论都不参与；一个类别都没开时一个请求都不发；番剧仍然整条链路不触发（`ensureSponsorSegments` 的 `isPgcPlayback()` 闸）。
  - **L0（26.11.14 本机实测）**：`:app:compileStandardDebugKotlin :app:compileCoexistDebugKotlin` → BUILD SUCCESSFUL（48s，中途修掉 `SponsorSkipPrefs.effectiveCategories()` 与老同名函数的重载冲突）；`:app:testStandardDebugUnitTest` → BUILD SUCCESSFUL，**2587 tests / 0 failures**（新增 `player/SponsorModeTest.kt` 14 例：存档解析、越界回落、按档取类别顺序、全关才算没开、六档折算前后跳的类别一致、折算不产生「询问」）；`:app:assembleDebug` 见下一行补测。
  - **未验证（L2，只能真机看）**：三档改动是否即时生效（改完回到播放页不用重进）；「询问我」提示的位置与停留时长、不理会时不跳；升级后老存档折算是否与升级前跳的段落一致；入口在「选择播放器」页的位置与点进去的页名；见 `docs/review/l2-pending.md` §G。
- **26.11.14 进度条时间加「扣掉广告后的时长」括号（用户原话「进度条的时间括号和广告片段标记没有生效」）**：
  - **来路**：用户第一张参考截图（浏览器插件）里进度条时间是 `01:43 (1:34)`，括号指的是「跳完广告后的有效时长」；我们一直只显示 `当前 / 总长`，用户因此报「时间括号没生效」。
  - **修法**：① `player/SponsorSkipPolicy.kt` 新增纯函数 `savedSeconds(segments): Int`（先把各段时长求和再四舍五入到整秒，脏数据 `to <= from` 按 0）；② `activity/player/PlayerActivity.kt` 新增字段 `sponsorSavedMs`（在 `applySponsorLevel()` 里由**已按档位过滤**的片段表算出，改档位/点「不准」自动跟着变）与 `formatProgressText(positionMs)` / `refreshProgressText()`，`initSeekbars()` 的 `onProgressChanged` 改调 `formatProgressText`，`progress_str` 与 `sponsorDecider.applyLoaded` 之后各刷一次；③ `res/values/player_sb_strings.xml` 新增 `player_sb_progress_saved` = `%1$s / %2$s (%3$s)`；④ 换 P 时随 reset 清成 0。
  - **口径**：括号只在**有可跳片段**时出现，没有就退回 `当前 / 总长`；这只影响文案，不改任何跳过行为。
  - **L0（26.11.14 本机实测）**：两 flavor `compile` → BUILD SUCCESSFUL（1m5s）；`:app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --no-build-cache` → BUILD SUCCESSFUL（新增 `player/SponsorTimeSavedTest.kt` 7 例，含 `BV13yW16VEGT` 的真实上游样本：仅恰饭档 1 段、省 13 秒、档位关闭为 0、别的分 P 不算）；`:app:assembleDebug` → BUILD SUCCESSFUL。
  - **未验证（L2，只能真机看）**：括号是否出现在进度条右侧、`107` 秒的 `BV13yW16VEGT` 是否显示 `(01:34)`；见 `docs/review/l2-pending.md` §G。

- **26.11.14 视频详情页标题前加「广告」胶囊，顺带定责「进度条看不到条子」（用户原话「广告标记应该放在视频详情页，你看好了」）**：
  - **来路**：上一批把整片标签做成播放页顶栏胶囊，用户要求把这个「广告」标记挪到**视频详情页**（与截图里「联合投稿」同一位置、同一套 `RadiusBackgroundSpan`）。经确认：详情页与播放页两边都保留；整片 → 「广告」，只有部分片段 → 「含广告」；文案用「广告」。
  - **修法**：① `player/SponsorSkipPolicy.kt` 新增 `BADGE_NONE/BADGE_FULL/BADGE_PARTIAL` 与纯函数 `adBadge(raw, cid, currentDurationSec): Int`——只在恰饭口径两类（`sponsor` / `selfpromo`）里判，片头、三连提醒、离题闲聊都不算广告；整片优先，其次部分，其余 NONE。② `activity/video/info/VideoInfoFragment.kt` 新增字段 `sponsorBadge` / `titleView`，新增 `loadSponsorBadge()`（**只有 `SponsorSkipPrefs.isEnabled()` 为真才发请求**，cid 传 0、总时长传 0 表示「整条视频有没有广告」；失败/空表静默，只写一行 `Logu.w("SponsorBlock", …)`），在 `initView` 的 `title.text = getTitleSpan()` 之后调用；`getTitleSpan()` 改成广告胶囊优先、其余四颗（充电专属/互动视频/全景视频/联合投稿）依次兜底，一颗位只放一颗。③ `res/values/player_sb_strings.xml` 新增 `player_sb_badge_full`「广告」、`player_sb_badge_partial`「含广告」。④ 播放页顶栏那颗不动。
  - **同时定责「进度条看不到条子」**：无真机（`android_devices` 恒为 0），改用**真实上游数据**走完 `parseSegments` → `buildSegments`/`adBadge` 全链（临时单测跑完即删，未进仓库）：`BV1PXWr6HEgZ`（sponsor 2150.133~2264.1，dur 2454.378）在「仅恰饭广告」档 `kept=1`，错 cid → 0，总时长 2454 或 0 → 1；`BV11Kc2zZEDK`（intro 0~20.934，dur 231）在「仅恰饭广告」「广告+自我推广」档 `kept=0`、在「推荐」「全部」档 `kept=1`。**结论：过滤口径没有 bug**，条子为空最可能是「档位只认恰饭 × 这条片子只有 `intro` 标注或根本没标注」（上游热门 30 条仅 5 条有标注）。上游 8 位前缀方案有效，`HASH_PREFIX_LENGTH` 不要改。用户已定：**不加运行期提示，只留日志**。
  - **L0（26.11.14 本机实测）**：`:app:compileStandardDebugKotlin :app:compileCoexistDebugKotlin` → BUILD SUCCESSFUL（1m1s）；`:app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --no-build-cache` → BUILD SUCCESSFUL，standard 侧 **2566 tests / 0 failures**（新增 `player/SponsorAdBadgeTest.kt` 9 例）；`:app:assembleDebug` → exit 0。
  - **未验证（L2，只能真机看画面）**：详情页标题前的「广告」/「含广告」胶囊是否正常渲染、与「联合投稿」冲突时广告优先的观感、长标题是否被挤没；见 `docs/review/l2-pending.md` §G 与 `docs/review/manual-test-guide-261111-sponsorblock.md` S11。

- **26.11.14 「进度条上看不到琥珀色条子」复核（用户报 26.11.14-exp.1 现场看不到标记）**：
  - **结论**：**静态复核未发现绘制或链路缺陷**——`maybeAutoSkipSponsor`(`PlayerActivity.kt:5113`) → `ensureSponsorSegments` → `loadSponsorSegments` → `applySponsorLevel` → `updateSponsorMarks` → `setMarks` 全链存在，`drawMarks` 在 `super.onDraw` 之后画（压得住底轨与进度填充），`isSameVersion` 在总时长未知时放过（所以「请求早于 `video_all` 就绪」不会把所有片段滤空）。最可能是三道门里的一道：档位**默认关闭**（关闭时零请求）、该视频没有被标注过、或放的是番剧/直播/本地文件——这三类都不查。
  - **顺手补的两处**（都不改观感，只为下一次能一眼判定）：① `updateSponsorMarks()` 增加一行 `进度条标记 段=… 点=… max=…`；`loadSponsorSegments` 的既有日志补上白名单类别数与总时长，合成 `原始标注 N 条，可跳 M 段，白名单 K 类，总时长 …s`。② `seekbar_progress.max = video_all` 之后补一次 `updateSponsorMarks()`：250ms 定时器可能比 prepare 更早开始跑，标注先到、`max` 还是 0，落一次兜底，保证时长一就绪就画得出来。
  - **L0（26.11.14 本机实测）**：`:app:compileStandardDebugKotlin :app:compileCoexistDebugKotlin` → BUILD SUCCESSFUL；`:app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --no-build-cache` → 2537 tests / 0 failures；`:app:assembleDebug` → BUILD SUCCESSFUL（双 flavor）。
  - **未验证（L2）**：真机上确认「档位开着 + N>0 + M>0」时条子确实出现——**需要用户提供视频 BV 号与档位**，或在应用日志页搜 `SponsorBlock` 把那两行发过来。
  - 复核口径记进了 `docs/review/manual-test-guide-261114.md` §3.4 开头的「三道门」提示。

- **26.11.14 长图分片加载收敛：修「上面分片加载完一会儿自己消失、随后无限加载」（用户原话「长图的上面分片加载完成后一段时间消失导致无限加载图片」）**：
  - **来路**：`ui/widget/BigImageView.kt` 的 `ensureTiles()` 兜底层序列**方向写反**——原代码 `var sample = allowed; while (...) { ...; sample = max(MIN, sample / 2) }`，采样率越除越小等于一路解**越来越细**的层（每块盖的源像素更少、块数成倍涨），与注释「由细到粗…再加一层更糊的兜底」正好相反。
  - **算出来的病**：1024×20000 长图放 400×400 表屏（`fitScale=0.02`、`allowed` 采样率 32），六层要的块数是 32→3、16→5、8→10、4→20、2→80、1→316，**一轮共 434 块**，而块缓存上限 `MAX_TILE_COUNT=24`（6MB / 256KB）→ 解一块淘汰一块；屏上那 3 块（sample 32）最旧、最先被挤掉，随即重解 → 永远收敛不了。`drawCachedTiles` 用「按倍率算的 allowed」判定该画哪层、`trimCacheLocked` 又是纯 LRU，两处都在放大这个振荡。
  - **修法（四条，缺一不成）**：①层序列改由 `ImageTilePolicy.fallbackSamples(allowed)` 生成，只往更糊（采样率 ×2）走，绝不解比 allowed 更细的层；②新增 `ImageTilePolicy.sampleWithinTileBudget(...)`，预取区在该采样率下要的块数超过 `MAX_TILE_COUNT` 就继续退粗，保证「一轮要解的块装得下」；③新增字段 `activeSample` 让**绘制与解码共用同一档位**（原先绘制另算一遍，退粗后解出来的层会被判成「不该画」）；④`trimCacheLocked` 不淘汰落在当前可见区里的块（`currentVisibleRect()` + 新增 `tileSpan()`），另设硬上限 `PROTECTED_TILE_CEILING = MAX_TILE_COUNT + 8` 防无上限涨。
  - **为什么不去动 `ImageViewerActivity`**：它只在「一块都还没解出来」时显示 `image_viewer_port_tile_loading` 文案，没有跟着分片进度转的菊花；观感「无限加载」就是 BigImageView 自己在重复解码，改这一层才对症。
  - **提交**：`abc13dea`；单测 `ImageTilePolicyTest` 新增 5 例（该类共 34 例，本地 `:app:testStandardDebugUnitTest` 全绿，`assembleDebug` 通过）：层序列方向与去重、块数口径对齐 `tileRects`、对齐桶计数、容量退档不动更细的档位，以及**回归例「1024×20000 长图整图适配一轮只铺三块」**（修好前此处是 434 块）。
  - **未验证（L2，只能真机看画面）**：见 `docs/review/l2-pending.md` §O。

- **26.11.14 短视频画面正中的三角可点即播放/暂停（用户原话「这个可以加个按那个三角播放」）**：
  - **来路**：中心那颗 `playIcon` 一直是纯展示——`fragment_short_video_page.xml:36-45`（56dp、`@android:drawable/ic_media_play`、`alpha=0.7`、默认 `gone`），Activity 里只有四处 visibility 赋值，**一个点击监听都没有**；用户看着三角当然会去按。
  - **修法**：`activity/video/ShortVideoPlayerActivity.kt` 的 `initBottomButtons()` 里给它加 `playIcon.setOnClickListener { togglePlayPause() }`。没复用底部按钮那份 `controlVideo()`：`resume()`（`:1482-1495`）自己已经处理「播到结尾」，会先 `playerBridge.seekTo(0)` + `danmakuManager?.seekTo(0)` 再 `start()`，所以 `togglePlayPause()` 等价且更短。
  - **刻意没动**：页面级 `GestureDetector` 那条链（单击切底栏 / 双击播放暂停 / 长按三倍速 / 双指缩放平移）一行未改。`:755-778` 的 `OnTouchListener` 恒 `return true` 是当年「点一下、滑一下也会在 500ms 后误触发长按跳详情页」的修法，碰它风险不成比例。图标也按用户拍板保持系统三角，没换 B 站素材。
  - **提交**：`2ec3597a`。
  - **未验证（L2，只能真机按）**：见 `docs/review/l2-pending.md` §N 的前两条（56dp 命中区在 466px 表上够不够、点它是不是真的续播而不是从头播）。

- **26.11.14 短视频弹幕改用独立档位（默认 6 行、不重叠），修「鬼畜弹幕」（用户原话「鬼畜弹幕」）**：
  - **来路**：短视频页与长视频共用同一份弹幕档位——`DanmakuManager.init()` 直接读全局存档 `PLAYER_DANMAKU_MAXLINE`（默认 15）与 `player_danmaku_allowoverlap`（默认 true）。短视频恰恰是弹幕最密的场景，屏幕只有 400~466px，15 行 + 允许重叠必然糊成一团，而短视频页本身没有任何可调的地方。
  - **修法**：新增 `player/ShortVideoDanmakuPolicy.kt`——开关 `shortvideo_danmaku_separate`（默认开）、行数 `shortvideo_danmaku_maxline`（默认 6）、重叠 `shortvideo_danmaku_allowoverlap`（默认关），钳制区间 1~30；`DanmakuManager.init(config: DanmakuConfigOverride? = null)` 只覆盖「最大行数 / 允许重叠」两项，传 null 就是原来的全量读档（长视频走这条）；短视频页 init 时传 `ShortVideoDanmakuPolicy.overrideOrNull()`（关掉开关即 null）；设置页「弹幕」分组新增三项，key 进 `util/SettingsKeys.kt:346-348`。
  - **红线**：26.11.08 用户拍板「不许按设备档位偷偷压行数」，这里一个字都没违反——改的两项都是用户自己看得见、可以填的设置，长视频那两项一个字节没动。
  - **提交**：`ee8bfba7`；单测 `ShortVideoDanmakuPolicyTest` 3 例（合法值 / 越界钳制 / 默认档保守）。
  - **未验证（L2，只能真机看画面）**：见 `docs/review/l2-pending.md` §N（6 行在真机观感是否合适、关掉开关是否真的回到 15 行 + 允许重叠）。

- **26.11.14 弹幕「一跳一跳」：蠕动步进改为按真实帧间隔放宽（用户原话「弹幕依旧会一跳一跳的」）**：
  - **来路**：媒体时钟模式下 `DrawHandler.syncTimerByMediaClock` 每帧把 timer 朝「采样位置 + 墙钟外推」的理想值蠕动，步进上限固定 `MediaClockSync.stepFor(speed)` = 40ms（3x 时 120ms），而这一分支**完全绕过了原生墙钟分支的 `averageTime` / `mFrameUpdateRate` 自适应**。40ms 是「60fps」的基准值：帧率一旦掉到 25fps 以下（手表叠加 15 行弹幕、短视频页还要同时解视频帧，很常见），每帧真实过去的媒体时间就超过 40ms，时钟每帧必然落后，误差线性累积，撞到 `SNAP_ERROR_MS` = 500ms 就硬吸一次——肉眼就是「跳」一下。
  - **诊断（纯数值仿真，不依赖设备）**：新增 `app/src/test/java/com/RobinNotBad/BiliClient/player/DanmakuClockDriftSimulationTest.kt`，按 DFM 渲染循环一帧一帧算，10 秒窗口内旧行为 10fps 吸附 **12 次**（约每 0.83s 一次）、20fps **4 次**（每 2.5s 一次）、30fps 0 次、60fps 0 次——与「一跳一跳」「只在某些视频上抖」对得上。没拿模拟器当判据：它是 yuv 软解 + 秒级卡顿，量出来的 fps 代表不了手表。
  - **修法**：蠕动只负责抹平 250ms 采样抖动，不再承担「限制追赶速度」——后者本就错了，媒体时间过去多少，弹幕时钟就该走多少（原生分支的 `timer.add(d)` 就是这个口径）。`MediaClockSync` 新增 `stepFor(float speed, long frameIntervalMs)`：上限取 `max(40ms×倍速, 帧间隔×倍速×1.25)`；`nextTimerValue` 加 4 参重载（3 参版委托 `frameInterval=0`，旧行为一字不变）；`DrawHandler` 记 `mLastClockFrameAt`（硬对齐时重置），帧间隔未知 / 为负 / 超过 500ms（卡顿、后台唤醒）时传 0 退回原行为。
  - **修复后仿真**：10 / 20 / 60fps 与 1x / 3x 全部吸附 0 次，且最大瞬时偏差仍 < 500ms。
  - **提交**：`048efa31`。
  - **未验证（L2，只能真机看）**：见 `docs/review/l2-pending.md` §N 的后两条（真机上还抖不抖、低端手表上是否反而更「追」得生硬）。
  - **播放页（长视频）回归**：这套时钟播放页与短视频页各持一份 `DanmakuManager` 实例，但共用同一份 `MediaClockSync` / `DrawHandler`。按帧间隔取上限后，高帧率下与旧行为**等价**——60fps 时 `帧间隔 16.7ms × 1.25 ≈ 20.8ms < 40ms`，`max()` 取到的仍是原来的 40ms（3x 仍是 `max(120, 62.6) = 120`），只有帧率掉到 30fps 以下才放大（30fps ≈ 41.7ms），而放大正是要修的那件事。L0（26.11.14，隔离工作树 `outputs/wt-rebase`，rebase 到 `49069e35` 之后）：`assembleDebug` → BUILD SUCCESSFUL（2m27s，双 flavor）；`testStandardDebugUnitTest --rerun` → BUILD SUCCESSFUL，215 个测试类 / 2312 例，失败 0 / 错误 0 / 跳过 0（`MediaClockSyncTest` 原有 14 例与新增仿真 8 例全在里面）。**播放页本身未上真机实测**，已列 `docs/review/l2-pending.md` §N 末条。

- **26.11.14 全局隐藏状态栏与导航栏（用户原话「能不能试着适配一下导航栏」，来电是手表实拍里播放页底部那条三键导航栏黑边；用户拍板「所有页面都隐藏状态栏和导航栏」）**：
  - **来路**：主题里一直只有 `android:windowFullscreen`（只压掉状态栏），导航栏从来没处理过，于是手表底部那条三键导航栏在播放页单独占一条黑边。26.11.02 修主题 bug 时又把旧实现里的 `SYSTEM_UI_FLAG_HIDE_NAVIGATION` / `IMMERSIVE` / `IMMERSIVE_STICKY` 一并去掉（`ui/appearance/ColorScheme.kt` 的注释里写着），全工程再无一处隐藏系统栏的代码。
  - **修法 1（统一落窗）**：新增 `app/src/main/java/com/RobinNotBad/BiliClient/ui/appearance/ImmersiveMode.kt` —— `isEnabled()` 读 `SettingsKeys.UI_IMMERSIVE_FULLSCREEN`（默认 true），`apply()` 按开关调 `hideSystemBars()` / `showSystemBars()`；隐藏走官方 API `WindowInsetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())` 配 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`，装饰窗口的 `WindowCompat.setDecorFitsSystemWindows(window, false)` 早就在 `ColorScheme.applyWindowTheme` 里设过，这里不重复设成一个相反的值。`activity/base/BaseActivity.kt` 在 `onCreate`（`applySystemBarInsets()` 之后）与 `onResume` 各调一次：回前台时系统可能已经把栏放回来（弹过系统对话框、从别的页回来），这一处也负责开关刚被关掉时把栏放回去。
  - **修法 2（inset 同步消费）**：`BaseActivity.applySystemBarInsets()` 原先无条件拿 `systemBars() or displayCutout()` 叠到窗口根 padding 上。栏隐藏后按语义 `systemBars()` 就是 0，但个别 ROM（含 HarmonyOS 兼容层）仍会报回栏高，照它加就等于在底部多留一条空白 —— 改成按开关取型：开着只取 `displayCutout()`（横屏 / 挖孔仍要避让），关掉恢复原来的 `systemBars() or displayCutout()`。用户的「界面边距」加在内容容器上（`applyUserPadding()`），两条链路互不覆盖。
  - **修法 3（播放页单独接）**：`PlayerActivity` 不是 `BaseActivity`（`class PlayerActivity : Activity()`，主题 `Theme.NoSwipe`，也不调 `applyWindowTheme`），在 `setContentView(R.layout.activity_player)` 之后与 `onResume` 各调一次 `ImmersiveMode.apply(this)`。控制栏显隐（`clickUI()` / `showcon()` / `hidecon` / `autohideReset()`）一行未改。
  - **修法 4（回退开关）**：新增 `SettingsKeys.UI_IMMERSIVE_FULLSCREEN = "ui_immersive_fullscreen"`；「设置 → 界面与外观 → 布局适配」加 switch「沉浸式全屏」，默认开，`onChange` 立即 `ImmersiveMode.apply(this)`（存取仍由 `SettingsAdapter` 负责）；同页「恢复默认」把它置回 true 并把栏放回来；`activity/settings/SettingsIndex.kt` 同步加一条搜索项（`highlight` 与设置页那行的 name 字面一致）。
  - **L0（26.11.14 本机实测，隔离工作树 `outputs/wt-immersive`，HEAD `47bcfb41` + 本次改动）**：`./gradlew.bat :app:assembleDebug --no-parallel --max-workers=2` → BUILD SUCCESSFUL（121 tasks，双 flavor 出 APK）；`./gradlew.bat :app:testStandardDebugUnitTest --rerun` → BUILD SUCCESSFUL，214 个测试类 / 2304 例，失败 0 / 错误 0 / 跳过 0。
  - **未验证（L2，只能真机看画面与手感）**：见 `docs/review/l2-pending.md` §L 的 8 条。其中「全屏后从屏幕左缘内滑几次才返回」只能人拿着表试，代码层面无法断言。

- **26.11.14 顶栏灵动岛改成三层优先级 + 新增「灵动岛设置」二级页（用户要求「有媒体播放或下载时灵动岛不轮换，一直显示播放状态」+「未读与新公告属突发通知，短暂弹出 3–5 秒后自动恢复」+「平时显示时间，电量像手机一样并排在旁边」+「外观设置里加一个专门的灵动岛设置分区」）**：
  - **来路**：26.11.07 引入、26.11.10 定宽之后，灵动岛的决策仍是「有持续事件就一直占着 + 30 秒事件 / 5 秒时间硬轮换」，把未读当成常驻事件，新消息会顶掉播放状态一整天；电量只能和时钟互相挤；五个状态一个开关都关不掉，用户没法只留时间与电量。
  - **修法 1（纯函数状态机）**：`app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/IslandPolicy.kt` 重写——新增 `IslandFilters`（播放/下载/未读/电量/公告五个开关）、`ROTATE_OPTIONS_MS = listOf(0L, 3_000L, 5_000L, 10_000L)` 与 `rotateMsOf(raw)`，`decide()` 从高到低判 burst → unreadBurst → 常驻（播放 > 下载）→ TIME；`rotateMs <= 0` 时事件一直占着，选了 3/5/10 秒才与基础态对称轮换（事件 N 秒 ↔ 基础态 N 秒）。`ongoingState()` 去掉 unread 参数。宽度口径 `pillWidthPx()` / `PILL_SCREEN_RATIO = 0.31f` 未动。
  - **修法 2（状态源做差分）**：`util/IslandStatus.kt` 的 `BURST_MS` 10 秒 → 5 秒，新增 `trackUnread(now, unread)`：记 `lastUnread` 做差分，**只有未读数增加**才置 `unreadBurstUntil`，首次采样只记基线；`util/IslandState.kt` 的 `IslandSnapshot` 补 `unreadBurst: Boolean = false`。未读因此从「常驻事件」变成「短播」。
  - **修法 3（渲染）**：`ui/widget/TopbarIsland.kt` —— `TIME` 状态右侧挂 `batteryBadge()`（充电闪电/低电红/其余灰），事件态不挂电量；播放 + 下载同时进行时右侧改挂下载小图标；播放时图标 1 秒呼吸（`ValueAnimator` 0.55↔1.0，`detach` / 状态切走 / 页面不可见立刻 `stopRhythm()`）；时钟加 `maxLines = 1` + `ellipsize = END` 防换行撑高；`render()` 首行判 `decorView.isShown`，锁屏与全屏被盖住时不渲染不跑动画。
  - **修法 4（设置页只挂一处）**：`activity/settings/SettingGroupActivity.kt` 新增 `GROUP_ISLAND = "island"` 与 `buildIslandGroup()`（总开关 + 五个筛选开关 + 轮换间隔四档），外观页末尾加入口 `nav(...)`；**「布局适配」页那段老 switch 与其「恢复默认」里的 `TOPBAR_ISLAND = false` 一并删除**；`activity/settings/SettingsIndex.kt` 的搜索结果改指 `GROUP_ISLAND`，并补齐 7 条（入口 + 五个开关 + 轮换）；`strings.xml` 新增 20 个 key 并改写 `desc_setting_topbar_island`。
  - **L0（26.11.14 本机实测）**：`./gradlew.bat :app:assembleDebug --no-parallel --max-workers=2` → BUILD SUCCESSFUL（121 tasks）；`./gradlew.bat :app:testStandardDebugUnitTest --rerun` → 单测全绿，其中 `IslandPolicyTest` 重写为 19 例（含「不轮换 + 未读短播 + 播放中」组合、轮换对称、五个开关逐条过滤）。
  - **未验证（L2，只能真机看画面与手感）**：见 `docs/review/l2-pending.md` §M 的 13 条（逐页面宽度是否仍一致、播放呼吸幅度与流畅度、短播 5 秒够不够看清、播放+下载双图标挤不挤、清零后再来新消息能否再弹、锁屏与全屏是否真的停止刷新、圆屏 31% 封顶与长标题省略）。

- **26.11.14 SponsorBlock 再接两样：精彩时刻（`poi` 跳转点）与「赞助/恰饭」顶栏胶囊（用户原话「功能二实现标记和跳转精彩片段（注意这个片段没听过才给跳转按钮）」+「功能三实现赞助/恰饭的标注，复用互动视频/联合投稿的图标」）**：
  - **来路**：上一批只消费 `actionType == skip`；上游还有两种对观众有用却被丢掉的数据——跳转点 `poi`（`poi_highlight`，一个**点**不是一个区间）与整片标签 `full`（区间固定 `[0,0]`，上一批只弹过一句 Snack）。用户要的观感对齐 B 站播放页：进度条上的蓝色小三角 + 「看点」里的「01:15 精彩时刻」，以及详情页那种「互动视频 / 联合投稿」胶囊。
  - **修法 1（跳转点提炼）**：`app/src/main/java/com/RobinNotBad/BiliClient/player/SponsorSkipPolicy.kt` 新增 `const val ACTION_POI = "poi"`、`data class Highlight(posSec: Double, category: String)`、`highlights(raw, cid, currentDurationSec)`——只认 `poi`，cid 与 `isSameVersion` 同口径过滤，`fromSec <= 0` 时退回 `toSec`，按时间排序并按 `位置|类别` 去重，**总时长未知（`<= 0`）时跳过越界判断**（否则时长还没拿到的第一帧会把所有点都丢掉），**不看档位**（这里没有「替你跳走」的风险，关掉自动跳过的人也该能用）。
  - **修法 2（进度条小三角）**：`ui/widget/HighEnergyProgressBar.kt` 再加一层 `data class PointMark(posMs, color)` + `setPointMarks` / `clearPointMarks`，`onDraw` 顺序 = 高能曲线 → `super.onDraw` → `drawMarks` → `drawPointMarks`，画尖朝下的实心三角（半宽 4dp、高 5dp，中心钳在可用区两端之内）。`PlayerActivity.updateSponsorMarks()` 里片段用 `ColorScheme.WARNING`、跳转点用 `ColorScheme.PRIMARY`——**刻意不同色**，免得「值得看」与「要跳走」混淆。
  - **修法 3（接进「看点」卡片而不是新加按钮）**：`PlayerActivity.showViewPointSelectorCard()` 改成每次打开都按 `buildViewPointDisplayList()` 重新 `setData`（原来 adapter 只创建一次、列表变了不刷新），B 站自带分段在前、精彩时刻在后，用 `type = VIEWPOINT_TYPE_POI (-1)` 标记点型条目（负数不会与 B 站分段的 1/2 撞车）；点型条目在 `ViewPointAdapter.bind` 里只显示 `01:15`（`to <= from` 时不再拼成 `01:15-01:15`）。点过即记进 `sponsorPoiSeen`（键 `类别@秒`）并从进度条与列表里消失——**只在本次播放内记住，不落盘**（用户选项：「没看过才给跳转」按会话算）。`refreshViewPointButton()` 接替 `loadViewPoints()` 里原来内联的显隐判断：B 站分段为空但有精彩时刻时按钮也出现。
  - **修法 4（顶栏「赞助/恰饭」胶囊）**：`applySponsorFullBadge(requestCid)` 取代 `noticeFullVideoCoverage(requestCid)`——`full` 的类别名（「广告」「广告/自我推广」…）拼好后调 `renderPlayerTitle()`，在 `text_title` 前挂 `RadiusBackgroundSpan(0, R.dimen.card_round, Color.WHITE, ColorScheme.PRIMARY)`（与 `activity/video/info/VideoInfoFragment.kt` 的 `getTitleSpan()` 同一套样式），**删掉原来那句一次性 Snack 与 `player_sb_full_notice`**。三处标题写入（`PlayerActivity.kt:1010` / `:6910` / `:7486`）改走 `setPlayerTitle()` 记下 `playerTitleRaw`，两处读取（`:5786` 听视频模式窗口标题、`:7043` 切清晰度重建 `PlayerData.title`）改读 `playerTitleRaw`——**否则胶囊文案会污染标题串**。
  - **L0（26.11.14 本机实测）**：`./gradlew.bat :app:compileStandardDebugKotlin :app:compileCoexistDebugKotlin` → BUILD SUCCESSFUL；`:app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --no-build-cache` → BUILD SUCCESSFUL，`2537 tests completed`（新增 `app/src/test/java/com/RobinNotBad/BiliClient/player/SponsorHighlightTest.kt` 11 例）0 failed；`:app:assembleDebug` → BUILD SUCCESSFUL（双 flavor）。**踩坑**：`text_title` 是可空 `@JvmField` 而非 `lateinit`，写 `::text_title.isInitialized` 会报 `This declaration can only be called on a reference to a 'lateinit' property`，改 `val view = text_title ?: return`；同一字段我先加过一次又在替换旧字段时留了一份，报 `Conflicting declarations`。
  - **未验证（L2，只能真机看画面）**：见 `docs/review/l2-pending.md` §G 后四条（三角是否落在正确位置且与「要跳走」的条子区分得开；点过的点是否立即消失；胶囊在顶栏是否被裁切、与返回箭头/时钟挤不挤；`full` 视频起播后顶栏是否真出现胶囊而不再弹 Snack）。

- **26.11.14 SponsorBlock 补三样：进度条标出会跳的片段 / 跳过提示改成「不准」/ 整片标注提示（用户要求「功能一，标记跳过的片段」+ 在 A/B/C/D 四档里选了「A：本地反馈包」）**：
  - **来路**：对照上游 `hanydd/BilibiliSponsorBlock` wiki API（全文已落 `outputs/sbwiki/API.md`）复盘我方实现，发现三处只消费不反馈、且看不到「待会儿会跳哪」的问题；用户挑了两件先做：把会跳的片段画在进度条上（对齐浏览器插件观感），以及把「撤回」改成「不准」（本地忽略集）。
  - **修法 1（进度条标记）**：`app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/HighEnergyProgressBar.kt` 新增 `data class Mark(startMs, endMs, color)` + `setMarks(List<Mark>?)` / `clearMarks()`，`onDraw` 顺序改为「高能曲线 → `super.onDraw` → `drawMarks`」，条子按 `max`（毫秒）归一化、钳到 `[0,max]`、极短片段保底 2dp 宽（alpha 0x26 铺底 + 贴底 3dp 圆角实心条）。数据源是 `PlayerActivity.updateSponsorMarks()`，直接读 `sponsorDecider.segments`（**过滤之后**的表，与真正会跳的完全一致），颜色 `ColorScheme.WARNING`，在 `applySponsorLevel()` 末尾调用；`doSwitchPage` 的 reset 段 `clearMarks()`。片头片尾（`ViewPointSkip`，番剧每集固定块）不画。
  - **修法 2（「不准」与本地忽略集）**：`app/src/main/java/com/RobinNotBad/BiliClient/api/SponsorBlockApi.kt:80` 的 `RawSegment` 尾部追加 `uuid` / `locked` / `hidden`（默认值兜住既有 17 个位置参数用例）；`app/src/main/java/com/RobinNotBad/BiliClient/player/SponsorSkipPrefs.kt` 加一层忽略集（`ignoredUuids(cid)` / `ignoreSegment(cid, uuid)` / `clearIgnored` / `clearAllIgnored` / `ignoredVideoCount`，每片上限 100 条、最多 50 片，LRU 淘汰连键一起删；`parseIgnored` / `addIgnored` 是纯函数，可 JVM 单测）；`SponsorSkipPolicy.buildSegments(...)` 加尾参 `ignoredUuids: Set<String> = emptySet()` 并在里面过滤；`PlayerActivity.showSponsorUndoSnack()` 的按钮从「撤回」换成「不准」——一次动作两件事：回到被跳过处 + 把这一条记进忽略集（有 UUID 落盘，老数据无 UUID 只落内存 `sponsorSessionIgnores`）。**为什么不并排两个按钮**：`MsgUtil` 的 Snack 只有一个 action 位、手表屏放不下，且点「撤回」的真实意图九成是「这条不该跳」。
  - **修法 3（整片标注提示）**：`SponsorSkipPolicy.fullVideoCategories(raw, cid, currentDurationSec)`（只看 `actionType == full`、按 `ALL_CATEGORIES` 顺序、不看档位）+ `PlayerActivity.noticeFullVideoCoverage(requestCid)`——`full` 的区间固定 `[0,0]` 跳不了，所以只在一 P 标注加载好时提示一次「本片整片都是……」，不进片段表。
  - **修法 4（清空入口）**：`player/PlayerSettingsGroups.kt:297` 的「自动跳过」分组底部加 `TYPE_BUTTON` 行「清除「不准」记录」（`SettingsAdapter.ButtonExtra` + `TerminalDialog.confirm` 带条数确认；空闲时只提示「还没有「不准」记录」），`activity/settings/SettingsIndex.kt` 同步登记搜索关键词。
  - **L0（26.11.14 本机实测）**：`./gradlew.bat :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --no-build-cache` → BUILD SUCCESSFUL，`app/build/test-results/testStandardDebugUnitTest` 汇总 **1220 tests / 0 failures**（新增 `app/src/test/java/com/RobinNotBad/BiliClient/player/SponsorIgnoreTest.kt` 11 例）；`:app:assembleDebug` → BUILD SUCCESSFUL（双 flavor）。**踩坑**：改 `RawSegment` 构造参数个数后单测报 14 处 `NoSuchMethodError at SponsorSkipPolicyTest.kt:30`，真因是 `standardDebugUnitTest` 的编译任务 `FROM-CACHE` 回放旧字节码 + `coexistDebug` 的 class 仍是旧 7 参，两 flavor 的 `app/build/tmp/kotlin-classes/` 不同步；修法与判断办法见 `docs/agents/build.md`「改主类构造参数后单测 `NoSuchMethodError`」。
  - **未验证（L2，只能真机看画面）**：见 `docs/review/l2-pending.md` §F（进度条琥珀色条子是否与真实跳过位置对齐、极短片段是否看得见；「不准」点下去是否回到起点且以后不再跳；清空按钮确认框与空态提示；整片标注是否只提示一次）。

- **26.11.14 收藏夹视频列表 `Inconsistency detected` 收口（从工作区救回的未提交改动）**：
  - **来路**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/user/favorite/FavoriteVideoListActivity.kt` 有 143 行改动**一直躺在工作树里没提交**（线上崩溃 #160 那条的修法），`docs/agents/pitfalls.md:28` / `:29` 与 `docs/architecture-map.md:1497` 早就按它写好了规矩 —— 也就是文档描述的行为在 main 上其实不存在。26.11.14 用户定「跑 L0 后直接提交」，本轮补上。
  - **根因**：`VideoCardAdapter.getItemCount()` 就是 `videoList.size`（`adapter/video/VideoCardAdapter.kt:103`），而原来 `fetch()` 在 `CenterThreadPool` 线程直接往这份活 list 里 `add` / `clear`，回主线程才 `notifyDataSetChanged` / `notifyItemRangeInserted`。`getItemCount()` 抢先变化、通知还没落地，主线程下一趟 layout 就抛 `IndexOutOfBoundsException: Inconsistency detected. Invalid view holder adapter position`。切排序 / 换关键词时还有第二重坑：`clear()` 之后仍在飞的旧页回包会把旧口径结果追加进新列表（握的是同一个 list 实例，光靠代次拦不住）。
  - **修法（三件套）**：
    1. `fetchPage(page, out)`（`:428`）只写调用方给的**临时 list**，绝不碰 `videoList`；
    2. `showList(items)`（`:447`）整份替换 = 新的 `ArrayList` + 新的 `VideoCardAdapter` + `setAdapter`，并带过 `selectionMode`；首屏 / 切排序 / 换关键词 / 聚合四条路一律走它，`switchSort` 等处的原地 `clear()` + `notifyDataSetChanged()` 全删；
    3. `listGeneration`（`:69`）代数守卫：`loadFirstPage()`（`:457`）发起时 `++` 并捕获，`continueLoading()`（`:683`）只捕获不增，回主线程**先判代数、后落数据**，对不上就丢弃（翻页那条顺手 `setRefreshing(false)` 复位翻页占用，否则 `goOnLoad` 再也进不来）；`loadAggregate()` 换数据时也 `++`，让在飞的翻页一并作废。
  - **顺手的**：`continueLoading` 的 `addAll` 与 `notifyItemRangeInserted` 收进同一个 `runOnUiThread`；`loadFail(e)` 从后台线程调用改成先回主线程（它碰 `SwipeRefreshLayout` 与 Toast）；连播起播前对 `videoList` 取快照（`:764`），免得后台线程迭代时被主线程改出 `ConcurrentModificationException`。
  - **L0（26.11.14 本机实测）**：`./gradlew.bat :app:assembleDebug --no-parallel --max-workers=2` → BUILD SUCCESSFUL（双 flavor）；`:app:testStandardDebugUnitTest --rerun` → BUILD SUCCESSFUL，**233 suites / 2515 tests / 0 失败 0 错误**。
  - **未验证（L2）**：见 `docs/review/l2-pending.md` §F（切排序 / 换关键词 / 翻页到底 / 聚合模式 / 多选态是否带过去）。

- **26.11.14 「播放页返回键直接退出」默认值改为关（用户要求「这个按钮改默认关闭」）**：
  - **两处必须同时改**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/player/PlayerActivity.kt:947`（`backKeyExitEnabled()` 的 `getBoolean` 兜底值 `true → false`）与 `app/src/main/java/com/RobinNotBad/BiliClient/player/PlayerSettingsGroups.kt:181`（那条 `SettingSection("switch", …, "true") → "false"`，设置页开关初值读的是这一份）。只改一处会表现为「设置页显示关、播放页按开走」。
  - **行为影响（有意）**：默认回到 26.10.22 旧语义——`onBackPressed` 里「开着『左右滑动控制进度』就吞返回键」重新生效；`onCreate` 的 `noSwipe = swipeSeekEnabled || !backKeyExitEnabled()` 默认就是 `true`，即窗口层右滑返回默认关闭（放大后可拖动画面）。**默认值下（滑动调进度也默认关）按返回仍然能退出**，不会被困在播放页；只有用户同时打开「左右滑动控制进度」才会撞上旧语义。
  - **文案已同步改中性**（`res/values/strings.xml:2873` 的 `desc_player_back_key_exit`）：第二段由「关闭后：开着『左右滑动控制进度』时返回键仍会被吞掉（旧行为），只适合特殊手表。」改成「关闭后（默认）：恢复旧语义，开着『左右滑动控制进度』时返回键仍会被吞掉，窗口右滑也不退出。」——不再把「关闭」说成只有特殊手表才该选，并点明它是默认值。
  - **文档已同步**：`docs/FEATURES.md:215` / `:218` 的「默认开」全部改成「26.11.14 起默认关」。
  - **未验证（L2）**：手表上默认值下按返回键 / 侧键 / 表冠是否能直接退出播放页；打开开关后同一操作是否仍退出（回归）。

- **26.11.14 播放器「跳到指定时间」从另开 Activity 改成页内卡片（用户报「离开播放器回来视频流会消失 + 跳转是新活动 = 没法用」）**：
  - **症状**：用户原话——「本来离开自带播放器再回来视频流会消失不算啥，结果结合了跳转指定时间是新活动就导致没法用了」。即：跳转本身能用，但跳完回来画面没了。
  - **根因**：`PlayerActivity` 的「跳到指定时间」原来是 `startActivityForResult(InputDialogActivity)`（`REQUEST_CODE_JUMP_TO_TIME = 1002`），播放页被输入页整页盖住 → Surface 被销毁 → 返回时靠 `PlayerSurfaceBinder` 的 `onSurfaceReattached` 重新 attach + `seekTo(video_now)` + 重设视频几何来恢复，这条路在手表上并不总成功，于是「跳完黑屏」。而这个交互压根不需要离开播放页。
  - **修法（照「发送弹幕」的样板做页内卡片）**：`app/src/main/res/layout/activity_player.xml:648-696` 新增 `jump_time_card`（与 `danmaku_send_card` 同构：`layout_centerInParent` + `marginHorizontal=6dp` + `@drawable/background_card`，默认 gone）——标题（`text_player_jump_to_time`）+ `jump_time_edit`（`hint_player_jump_to_time`、`maxLength=10`、`singleLine`、`textColor=?android:attr/textColorPrimary`、`textColorHint=#88ffffff`）+ 整行确认按钮 `jump_time_confirm`（`@dimen/touch_min` 高、`background_card_borderless`、文案新增 `btn_player_jump_time`＝「跳转」）。
  - **`PlayerActivity.kt` 侧**：`initJumpToTimeButton()`（约 `:2024`）只做两件接线——`jump_time_btn` 点开 `showJumpTimeCard()`、`jump_time_confirm` 点 `confirmJumpTime()`；`showJumpTimeCard()`（约 `:2037`）先 GONE 掉弹幕卡（两张卡互斥）、显 `layout_card_bg` + 本卡、用 `PlayerJumpTimePolicy.prefillText(video_now.toLong())` 预填并把光标挪到末尾；`confirmJumpTime()`（约 `:2054`）`durationMs = ijkPlayer?.duration ?: 0L`（<=0 直接收卡返回）→ `parseToSeconds` 失败提示 `msg_player_jump_invalid`（**卡片不关**，用户能改）→ `clampMs` 越界提示 `msg_player_jump_out_of_range` → 收卡 + `seekToPosition` + `autohideReset()`。新增 `hideAllCards()`（约 `:2079`）把遮罩与六张卡一次收掉，`card_bg` 点击改调它；`btn_danmaku_send` 点开时先 GONE 跳转卡。
  - **删掉的**：`REQUEST_CODE_JUMP_TO_TIME` 常量、`onActivityResult` override（全仓只这一处用 1002）。`parseToSeconds` / `prefillText` / `clampMs` 三个纯函数与单测 `PlayerJumpTimePolicyTest` **一行没改**——只是输入面板换了宿主。
  - **口径保持不变**：直播没有「时间点」概念，`jump_time_btn` 的显隐仍由 `onPrepared` 的 `isLiveMode` 决定；输入法 / `EditorEnterKeyGuard`（`dispatchKeyEvent` 里兜 singleLine EditText 的 `focusSearch` ISE，线上崩溃 #66）照旧生效，KDoc 里补了 `jump_time_edit` 的名字。
  - **L0（26.11.14 本机实测）**：`./gradlew.bat :app:assembleDebug --no-parallel --max-workers=2` BUILD SUCCESSFUL（双 flavor 全出）；`:app:testStandardDebugUnitTest` BUILD SUCCESSFUL。**注意**：不带 `--no-parallel` 时 `:app:compileCoexistDebugKotlin` 会报「Compilation error. See log for more details」，真因是两 flavor 的 Kotlin 编译并行抢内存（`e: Daemon compilation failed: Not enough memory to run compilation`），不是代码错——见 `docs/agents/build.md`「环境坑」。
  - **L1 已尝试并止损**：两个在线模拟器（`emulator-5554` / `emulator-5556`）都没登录态，启动即被 `SetupUIActivity` 初始化向导盖住（`SplashActivity` 在 `mid == 0` 时必弹），且该 AVD 的 Surface 渲染本身就是花屏残帧——判不了画面，按 `docs/agents/testing.md` 不上模拟器硬试。
  - **未验证（L2，只能真机看画面 / 判手感）**：①「更多」里点「跳到指定时间」后卡片是否居中弹出、预填是否是当前进度；②输入后点「跳转」是否真的跳过去**且画面还在**（本次要修的就是这个）；③输入非法/越界时提示是否出现且卡片留在原地能继续改；④卡片开着时点遮罩是否收回；⑤手表小屏上卡片高度与输入法弹出后是否挡住确认按钮。

- **26.11.14 短视频长按三倍速失效（一直 1x）：播放器选项漏了 `soundtouch`（反馈 #82）**：
  - **症状**：短视频页长按，底部倍速按钮与提示都会变成 `3.0x`，松手回 `1.0x`，但画面与声音的播放速度自始至终是 1x。
  - **根因（不是手势、也不是应用层没下发）**：`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/ShortVideoPlayerActivity.kt` 的 `shortVideoOptions` 是**独立一份**播放器选项列表（`util` 层的 `IjkPlayerBridge.createPlayer(options)` 在 options 非空时只用调用方给的项、**不套默认项**），里面没有 `soundtouch`。ijk 的变速靠 soundtouch 拉伸音轨、让音轨时钟加速，视频再跟随音轨时钟；stock ijkplayer 的 `soundtouch` 默认 **0**（`bilibili/ijkplayer` 的 `ff_ffplay_options.h`：`{ "soundtouch", "SoundTouch: enable", OPTION_INT(0, 0, 1) }`），不开时 `setSpeed` 只改了 `pf_playback_rate` 这个属性，播放不变。长视频 `app/src/main/java/com/RobinNotBad/BiliClient/activity/player/PlayerActivity.kt:2202` 一直显式设 `soundtouch 1`（此前全仓唯一一处），短视频这条链路漏了。
  - **修法**：`shortVideoOptions` 补 `IjkOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1)`；顺带修一处顺序缺陷——`onPrepared` 里原来无条件下发 `currentSpeed`，会把「本条 prepare 之前就按住」的长按 boost 压回 1x（按钮却还停在 3.0x），改为 `applySpeedToPlayers(if (speedBoosted) boostSpeed else currentSpeed)`。
  - **L0（26.11.14 本机）**：`:app:assembleDebug` + `:app:testStandardDebugUnitTest` BUILD SUCCESSFUL。
  - **L1（`emulator-5554` / standardDebug，判据是日志原文）**：装包后起 `ShortVideoPlayerActivity`，`adb logcat` 出现 `I/IJKMEDIA: player-opts : soundtouch = 1`，随后 `FFP_MSG_PREPARED` / `Playback rate: 1.000000` / `FFP_MSG_AUDIO_RENDERING_START` / `FFP_MSG_VIDEO_RENDERING_START` 全部正常，没有「完全播放不了」这类回归（对照 §3.88 补丁版 `.so` 的教训）。定位阶段同时已证明应用层到 native 是通的：长按时 `I/IJKMEDIA: Playback rate: 3.000000` 与 `E/J4A: J4AC_android_media_AudioTrack__setSpeed 3.000000` 都出现，松手回到 1.0——即「属性下发了、播放却没变」，正好指向 soundtouch 缺失。
  - **未验证（L2，只能真机看画面 / 听声音判）**：①手表上长按约 1.5s 后画面与声音是否真的约 3 倍速（模拟器软解掉帧到秒级，`Davey! duration=1192~4962ms`，速率无法判定）；②3x 下弹幕是否跟得上（#82 原文提到「弹幕抽搐」，本次没动弹幕侧，`DanmakuManager.setSpeed` 与 `MediaClockSync` 的步进补偿未改）；③松手是否立即回 1x（#146 报过「还继续倍速一小段时间」）。
  - **已知未处理（26.11.14 用户决定先放着、不排期）**：`app/libs` 里随包的 ijk `.so` 是上游 stock（`15d6a53e` 回退上游），没有纯视频流 `compute_target_delay` 补丁，DASH 纯视频流（1080P+）的倍速可能仍无效；本次修的是带音轨的合一 MP4 路径（soundtouch 生效），两件事互不影响。

- **26.11.14 缓存文件夹里的多 P 视频只能看 P1：合集嵌套化（用户报「缓存里文件夹内的多P视频只能看P1（倒不如说文件夹就是个虚拟视频）」）**：
  - **根因**（`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/local/LocalListActivity.kt` 的 `playVirtualCollection()`）：文件夹被当成虚拟合集时，每个夹内条目只往合集里塞**一条**——`pagenames.add(v.title)`、`videoUrlList.add(files.firstOrNull { it.endsWith(".mp4") } ?: files[0])`，而 `LocalVideo.videoFileList` 是**扁平**的（多 P 的每一页各占一项，DASH 页还会连 `audio.m4a` 一起再占一项），`pageList` 完全没被读。于是「一个多 P 缓存」在合集里退化成「只有一个 P 的视频」，P2..Pn 连入口都没有。
  - **修法（嵌套，不是把 P1..Pn 拍平）**：新增纯数据 + 纯函数 `app/src/main/java/com/RobinNotBad/BiliClient/model/LocalPlaylistEntry.kt`（`Serializable`，字段 `title/pageNames/videoFiles/audioFiles/danmakuFiles/cover`，`LocalPageMedia(videoFile, audioFile, danmakuFile)`；`build(title, pageList, videoFileList, danmakuFileList, cover)` 用既有的 `util/LocalPageFileIndexPolicy.indicesOfPage`（按父目录名 == 页名）把扁平列表**归一成一页一媒体**：主文件优先 `.mp4`、音轨取 `.m4a` 且不与主文件同一条（纯音频页不会叠两路声音）、目录名对不上时退化成按目录分组）。`LocalListActivity.playVirtualCollection()` 改为每个夹内视频产出一条 `LocalPlaylistEntry`，走 `putExtra(EXTRA_LOCAL_PLAYLIST)` 交给播放器；`LocalPageChooseActivity` 即「夹内点单个多 P 视频」的旧路径一行没动。
  - **播放器**（`app/src/main/java/com/RobinNotBad/BiliClient/activity/player/PlayerActivity.kt`）：新增 `localPlaylist` / `localLayerIndex` / `localRootResumeIndex` / `enterableLocalEntry` 四个字段，复用在线合集那套「可进入的下一层」交互——根层（`localLayerIndex = -1`）的 `pagenames` 是各视频标题，选集卡片标题是文件夹名；`enterableLocalEntry` 指向的条目 `pageCount() > 1` 时卡片上多一行「进入分 P 列表」，点了进层（`enterLocalLayer`：`pagenames` 换成该视频的分 P 名、`currentPageIndex = 0`、先 `reportProgressNow` 存根层进度），层内 `pagenames` 是分 P 名、`localLayerIndex` 指向该条目；「返回」和「层内放完最后一个 P」都走 `returnToLocalRoot(advance)` 回到根层并接着播下一条。`switchToLocalPage` / 新增的 `resolveLocalPageMedia` 按「层内取该条目第 i 页、根层取第 i 个条目第 0 页」解出媒体，替掉了原来「intent 三条列表同下标」的写法（那条老路径仍在，给 `LocalPageChooseActivity` 起的播放器用）。`showPageSelectorCard` 的守卫从 `hasMultiplePages()` 换成 `canOpenPageSelector()`（根层只有一个视频时也要能进它的分 P），按钮显隐收口到 `refreshPageNavButtons()`，`applyUploaderQueue` 里那条重复的显隐块也一并换掉。
  - **统一性自检（用户要求「检查其他地方虚拟合集是否统一」）**：全仓虚拟合集的生产者只三处——在线收藏夹（`FavoriteVideoListActivity`，两层靠 `enterableInfo` + `enterMultiPageLayer`）、UP 主投稿（`applyUploaderQueue`，压 `returnPlaylist` + `enterableMultiPage`）、本地文件夹（本次）。三者现在共用播放器里同一套「`returnPlaylist` 压栈 / `enterable*` 提示可进入 / `switchToLocalPage`·`switchToOnlinePage` 切页」机制，没有第二套状态机；`switchQuality`（`if (!isOnlineVideo) return`）与互动相关重建 `PlayerData` 的两处只跑在线，不受影响。
  - **开关默认关、行为不变**：本地文件夹的虚拟合集仍受「虚拟合集」与「文件夹内点击即播」两个开关控制，本次只改「进合集之后 P 怎么排」，没改何时进合集。
  - **L0（26.11.14 本机实测）**：`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest` BUILD SUCCESSFUL（Debug 双 flavor 全出）。新增单测 `app/src/test/java/com/RobinNotBad/BiliClient/model/LocalPlaylistEntryTest.kt` 8 例全绿：多 P 且个别页 DASH 双文件不串页、单文件视频（`pageList` 空时页名取标题）、`pageList` 为 null、无文件 / 全是空串返回 null、目录名对不上退化按目录分组、页比文件多只留可播页且三列表等长、纯音频页 `audioFile` 为空、封面透传。
  - **未验证（L2，只能真机看画面 / 判手感）**：①夹内连播到多 P 条目时选集卡片是否真的多出「进入分 P 列表」一行、点进去是否从 P1 起、P2 能否播；②层内放完最后一个 P 是否自动回根层并接着播下一条；③卡片「返回」回根层时是否回到原来那条（`localRootResumeIndex` 的记忆）；④夹里只有一个多 P 视频时选集按钮是否照常出现（这次修的正是这个场景）。

- **26.11.14 缓存页「文件夹 N」入口 + 独立文件夹页（实验性开关，默认关；用户要求「文件夹入口放到缓存最上面」「设置-实验性里开一个开关，整个文件夹设置复用我的-收藏的系统」）**：
  - **口径**（用户拍板）：「复用我的-收藏的系统」= 只复用**界面与交互**（收藏夹那套封面 / 「N 个视频」/ 多选 / 拖动排序），数据仍走本地 `.folders_info.json`，不绑在线收藏夹；开关默认关，关着时缓存页维持原样（文件夹平铺）。
  - **开关**：`app/src/main/java/com/RobinNotBad/BiliClient/util/FolderPagePrefs.kt`（`ENABLE = "cache_folder_page_enable"`、`DEFAULT_ENABLE = false`、`enabled()`；键留在门面里而不进 `SharedPreferencesUtil`，与 `VirtualCollectionPrefs` 同一理由——默认值散到调用点会「设置页显示开着、实际却是关的」）。开关 UI 在「设置 → 高级与实验 → 功能开关」里。
  - **缓存页**（`app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/CacheListAdapter.kt`）：唯一判断点在 `rebuildFlatList()` 顶层分支开头。开 → 平铺的文件夹分区与「新建文件夹」整段跳过，顶部先加一行 `TYPE_GOTO_FOLDERS`（复用 `cell_goto` 样式 + `FolderEntryHolder`，文案「文件夹 N」，数量取全部文件夹而非搜索命中数），搜索空态只看视频是否命中；关 → 逐行等价原实现。`onFolderListClick` 回调：接了由 Activity 用 launcher 起页并在回来时重扫，不接适配器自己 `startActivity`。
  - **独立页**（`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/local/LocalFolderListActivity.kt` + `app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/LocalFolderAdapter.kt` + `app/src/main/res/layout/cell_local_folder_list.xml`）：`RefreshListActivity` 子类，每夹一卡（封面走 `CachePathResolver.videoDirCandidates` + `cover.png`，不扫全库；缺封面上占位图），复用 `manageBar` 三个槽做「多选 / 排序 / 操作」，长按「重命名 / 拆散文件夹」，批量只有「拆散文件夹」（`FolderManager.deleteFolder` 的语义本就是拆散、不删视频文件），拖动落盘 `FolderManager.saveFolderOrder`。
  - **回跳刷新**：`LocalListActivity` 新增 `folderListLauncher`（`RESULT_OK` → 既有的 `refreshAfterExternalChange()`，入口上的文件夹数不会显示旧数字）；另加 `folderPageEnabledAtLoad`，`onResume` 发现开关变了就 `refresh()` 重排——改完设置回来即生效，不用重启。
  - **L0（26.11.14 本机实测）**：同上一条的同一次 `assembleDebug` + 双 flavor 单测 BUILD SUCCESSFUL（本批没加纯函数，未新增单测）。
  - **未验证（L2）**：①手表上独立文件夹页的行高 / 封面 / 「N 个视频」观感，和收藏夹列表页摆在一起像不像同一套；②开关打开后缓存页顶部那行「文件夹 N」的位置与点击热区；③夹内多选 / 拖动手感（手表上长按拖动尤其难判）。

- **26.11.13 私信发送入口改成底部整行按钮、点开复用写评论页；评论/动态/私信三处发送模块加「换行」**（用户口径：「修改消息界面为底部发送按钮向发送评论一样铺满 / 点击后复用评论区的发送模块 / 另外给评论、动态这种发送模块加一个换行功能」）：
  - **私信页**（`app/src/main/java/com/RobinNotBad/BiliClient/activity/message/PrivateMsgActivity.kt` + `app/src/main/res/layout/activity_private_msg.xml`）：底部原来那一行「输入框 + 表情/图片/发送三个 38dp 图标」（#157 改成权重布局的那一行）整段删掉，换成 `MaterialButton @+id/write_msg_btn`（`match_parent`、左右 `margin=@dimen/activity_padding_horizontal`、文案「写私信」），与评论区「写评论」按钮同一套样式。点击用 `ActivityResultLauncher` 打开写评论页的**私信模式**，回 `RESULT_OK` 就 `refresh()`；上滑收起动画从 `layout_input` 挪到按钮本身（`animVisible` / `getViewAnimation` 口径不变）。内联的 `sendImage` / `pickImage` / `openEmotePanel` 与两个 launcher 全删，发私信的代码只剩写评论页一处。`msg_view` 底部 padding 40dp→60dp 给按钮让位。
  - **写评论页一个类两种模式**（`app/src/main/java/com/RobinNotBad/BiliClient/activity/reply/WriteReplyActivity.kt`）：新增 `EXTRA_PRIVATE_UID`（>0 即私信模式）、`isPrivateMsg`、`sendPrivate()`、`privateSend()`。私信模式：标题换「写私信」、输入提示换成私信那句、表情面板 `business=im`、图片只留一张（再选一张是**替换**，不预上传）、发送走 `ImageApi.prepareImage → ImageApi.uploadImage(ImageApi.BIZ_MESSAGE) → PrivateMsgApi.buildImageContentOfKb → PrivateMsgApi.sendMsg(TYPE_PIC)`，再补一条文字 `sendMsg(TYPE_TEXT)`；**图没发出去就整条不发**（半截发出去用户更难收拾）。不查 `ReplyApi.isReplyVisible`、不做评论 KY 拦截、不发 `ReplyEvent`。评论分支代码原样保留（send 监听里私信先 `return`）。
  - **换行**：新增 `app/src/main/java/com/RobinNotBad/BiliClient/util/view/TextInsertion.kt`（纯函数 `insert(source, start, end, insert)` + `insertAtCursor` + `insertNewline`）。不用 `EditText.text.insert`：那个插完光标停在新内容**前**（连点两次换行会倒序），有选区时也不替换选区。`insertAtCursor` 用 `editable.replace(0, len, 新文本)` 整段写回再 `setSelection`，`selectionStart/End < 0` 的失焦态按「插到末尾」处理。`activity_write_reply.xml` / `activity_send_dynamic.xml` 在输入框正下方各插一张 `@+id/newline` 卡（与表情/图片卡同款 35dp + `arrow_forward`），写私信页复用写评论页那张，于是评论 / 动态 / 私信三处都有。需求来源：手表输入法把回车当「完成 / 下一项」，自己打不出换行。
  - **文案**：`btn_newline`=换行、`pagename_write_private`=写私信、`uitext_writereplyactivity_024`=私信发送失败：\n%1$s。新增字符串一开始占了 `uitext_writereplyactivity_006/007`，与文件后半段已有编号撞车（AAPT 报 `Found item String/uitext_writereplyactivity_006 more than one time`），已改用 `_024`。
  - **L0（26.11.13 本机实测）**：`./gradlew.bat :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest :app:assembleDebug` BUILD SUCCESSFUL（Debug 双 flavor 各 4 支 APK 全出）；单测 **232 suites / 2506 tests / 0 失败（两个 flavor 同）**，含新增 `TextInsertionTest` 12 例。（首次跑挂过 3 条，全是我自己写的期望值写错：`"[doge]]"` 应为 `"[[doge]]"`、越界区间 `1..99` 实际得到 `"a\n"`、负索引按纯函数是夹到 0 而非末尾——已按真实语义改正并复跑全绿。）
  - **未验证（L2，只能看画面 / 判手感）**：①私信页底部整行按钮的观感、上滑收起动画是否还顺、会不会挡住最后一条消息；②写私信页里是否还有别处写死评论语境（标题/提示之外）；③三处「换行」卡点击后换行是否真的落在光标处、连点两次是否正序、有选区时是否替换；④私信发图（选一张、再选一张替换）与「图没发出去就整条不发」的实际表现。

- **26.11.13 弹窗按钮改成整行宽、上下堆叠（控制台反馈 #161，同族 #125 / #54）**：用户报「移除稍后再看时点『移除』没反应、点『取消』却是删除」，追加两条「取消收藏」与「反馈页追加说明」也是「要点两个选项中间才生效」。三条症状同一形状：**点左边那一格，执行的是右边那一格；点右边那一格，什么都没发生**。
  - **实测定位**（AVD `rebili_watch37`，`wm size 466x466` / density 320，装 26.11.09 老包，测 `SplashActivity` 的 `choice()` 隐私框）：按钮行 `terminal_dialog_buttons` 铺满面板 419px，但两个按钮只占 `x167..301`（不同意）与 `x302..411`（同意）——**左侧 144px 与右侧 30px 都是死区**（实测点 (100,338) 截图逐字节不变，什么都没发生），两按钮之间缝隙 0px、文字画在各自节点正中央（Δx −0.5 / 0px）。这些设备（#54 HUAWEI HWARC 原话「点击屏幕上的按钮时触控会判定到旁边的位置」、#125「得点稍微往左一点才能点」）存在**横向命中偏移 ≈ 半个按钮宽 + 右侧那 30px 空档**：点「取消」→ 落到「移除」；点「移除」→ 落到右侧空档 = 没反应；「点两个选项中间才生效」→ 缝隙加上偏移仍落在右按钮内。三条症状一次性解释完。
  - **修法（构造性，不再猜测量时序）**：`TerminalDialog.Sheet` 的按钮改成**每个按钮独占一整行**（`MATCH_PARENT` 宽 + `Gravity.CENTER` + `minHeight=dialog_item_min_height`），整块按钮区**统一放在滚动区之外**（`layout_dialog_terminal.xml` 的 `terminal_dialog_buttons` 由 horizontal 改 vertical，首子项加一条 1dp 分割线）；于是「取消」那一行的 419px 全是它的命中区 —— 点行内任何位置都不会落到别的按钮上，横向偏移在结构上不再有害。顺带**整段删掉** `fitButtonBar` / `barDesiredWidth` / `shareBarWidth` / `BUTTON_BAR_TAG` 这套「事后改字号 / 改权重」的补救机制（`#125` 一直怀疑它和 `setLayout` 的时序有关），`Sheet` 的构造参数 `bottomButtonBar` 因此也删掉（按钮永远在滚动区外，`choice()` / `input()` 不再需要传 true）；`HistoryActivity:276` 唯一一处直接 new `Sheet` 的调用点同步改成两参。
  - **取证口**：`Sheet.logButtonGeometry()` 在 `onShow` 的 `decorView.post` 里把 `screen / decor / content` 尺寸与**每个按钮的 `getHitRect` + `getLocationInWindow`** 打一条 `Logu.w("dialog", …)`（`w` 级恒执行、进内存环形缓冲、随反馈附带）。下次再有人报「点不中」，反馈里直接带几何，不用再靠猜。
  - **L0（26.11.13 本机实测）**：`:app:assembleDebug` BUILD SUCCESSFUL（`--max-workers=1` 绕开两 flavor 并行 OOM；另清掉 `intermediates/packaged_res` 里残留的旧 `activity_menu.xml`，它引用了源码里不存在的 `?attr/colorTextSecondary`）；`:app:testStandardDebugUnitTest` **2506 tests / 3 failures**——3 条全在 `TextInsertionTest`（工作区里别人未提交的 WIP 引入，与本次无关），`DialogSizingTest` 全绿。
  - **L1（AVD `rebili_api37_sd` → `emulator-5556`，`wm size 466x466` / density 320，standard debug universal 包）**：新布局两行各 `x23..441 w419 h88`（「不同意」y230..318、「同意」y318..406）；`pm clear` 后启动点**「不同意」行最左端 x=40**（旧布局的死区位置）→ `shared_prefs/default.xml` 落 `terminal_telemetry_enable=false` / `terminal_crash_report_auto=false`；再 `pm clear` 后点**「同意」行最左端 x=40** → `terminal_telemetry_enable=true` + `terminal_crash_report_auto=true` + `terminal_privacy_consent_version=3`。两行各点各的、整行宽度都能命中，横向偏移在模拟器上无从复现但**结构上已被消掉**。
  - **未验证（L2）**：真表上点按钮左右两端、界面大小调 1.0 / 1.8 两档、界面边距 > 0 时的实际手感；模拟器是 AOSP、没有 OEM 兼容层，**复现不了 #54/#125 的偏移本身，不能拿它当「已修好」的证据**。

- **26.11.14 整页输入框的「取消 / 确定」也改成整行堆叠（#161 同族，只动 `activity_input_dialog.xml`）**：`TerminalDialog` 修完之后，全仓还剩一处**自己手拼**的横排「取消 / 确定」——`app/src/main/res/layout/activity_input_dialog.xml` 原来是 `orientation="horizontal"` + `gravity="end"` + 两个 `wrap_content` MaterialButton（中间 8dp），与弹窗老布局是同一个形状、同一个命中偏移风险。用户定案只改这一处（`activity_confirm_dialog.xml` 同形状，但 `ConfirmDialogActivity` 全仓无调用点，本轮不动，见 §2.2 的待办）。
  - **改法**：去掉横排容器，两个按钮改成父容器（竖排、`paddingHorizontal=activity_padding_horizontal`）的**直接子项**，各 `match_parent` 宽、`gravity=center`、`minHeight=@dimen/dialog_item_min_height`，按钮区上方加一条 1dp 分割线（与 `layout_dialog_terminal.xml` 的按钮区一致）；`btn_cancel` 原来的 `alpha=0.65` 保留。id 一个没改，所以 `InputDialogActivity`（`findViewById` + 点击回调 + 标题点击取消）与 `activity/TextButtonColors.apply(...)` 一行未动，纯布局改动。
  - **影响页面**（都只是 `Intent` 起 `InputDialogActivity`，共用这一份布局）：`activity/user/FollowUsersActivity.kt:239/271`（分组新建 / 重命名 / 删除）、`activity/video/local/LocalListActivity.kt:560/650/669`（本地列表重命名、删除等）。`PlayerActivity` 的「跳到指定时间」在 `c43cd6cd` 已改成页内卡片，本来就是单个整行按钮，本轮一处未改。
  - **L0（26.11.14 本机实测）**：`:app:assembleDebug --no-parallel --max-workers=2` BUILD SUCCESSFUL；`:app:testStandardDebugUnitTest --rerun` BUILD SUCCESSFUL，**233 suites / 2515 tests / 0 失败 0 错误**。
  - **未验证（L2）**：这 3 处输入页的按钮区从一行变两行、页面多占约 48dp——要看短屏（466px 高）上会不会把输入框挤小、`RotaryScrollView` 的转动手感是否变差，以及上下两个按钮的距离是否够分开点（真表上各点一次中心与两端）。

- **26.11.13 设置搜索跳转定位：目标行由「贴屏幕底部」改成「贴屏幕顶部」（用户报「修复设置搜索跳转跳转至对应按钮的跳转位置，由屏幕底部改为屏幕顶部」）**：
  - **根因**：五个设置页（`SettingInfoActivity` / `SettingGroupActivity` / `SettingTerminalPlayerActivity` / `SettingRepliesActivity` / `SettingPrefActivity`）在 `onCreate` 末尾都调 `RefreshListActivity.scrollToHighlight(list, intent.getStringExtra("highlight"))`，旧实现只调 `recyclerView.scrollToPosition(index)`——它**只保证目标行「刚好看得见」**；而跳转进来时列表停在顶端，目标行必然在下方 ⇒ 每次贴**屏幕底部**。旧记录里「目标顶到最上方可见位」那句与真实行为不符（`scrollToPosition` 是「最小滚动到可见」，不是顶对齐）。
  - **修法**（只动 `app/src/main/java/com/RobinNotBad/BiliClient/activity/base/RefreshListActivity.kt:389-403`）：`recyclerView.post {}` 里把定位改成 `(layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)`——`offset = 0` 即「目标行顶边贴列表顶边」；万一布局管理器不是 `LinearLayoutManager` 就退回 `scrollToPosition` 保底。前提都已核实：五个页面用的是 `CustomLinearManager` / `CustomGridManager`（都继承 `LinearLayoutManager`），`SettingsAdapter` 的 adapter position 与 `sections` 下标 1:1（没有 header）。高亮（`SettingsHighlight` 1.5s 常亮 + 450ms 淡出）**没动**。
  - **L0（26.11.13 本机实测）**：当时仓库里有别的会话在跑 `gradlew`，会 `--stop` 掐 daemon、与我互踩 `app/build`（表现为 `Failed to store cache entry … Could not get file mode`、`程序包 com.RobinNotBad.BiliClient.activity.live不存在`、`R.jar` 缺失导致几千行 `Unresolved reference 'R'` 等假失败），因此整仓 `robocopy` 到 `D:\Users\ASUS\AppData\Local\Temp\rebili-iso` 隔离构建：`:app:assembleCoexistDebug` BUILD SUCCESSFUL（1m48s，四个 ABI 包齐），`:app:testCoexistDebugUnitTest` BUILD SUCCESSFUL（`231 suites / 2494 tests / 0 失败 0 错误`）。
  - **L1（26.11.13 手表模拟器 `rebili_watch37`，466×466 / Android 17）**：共存 Debug 包装机后，用 debug 调试桥（`com.RobinNotBad.BiliClient.coexist/com.RobinNotBad.BiliClient.debug.DebugBridge`，`--es token rebili-agent-bridge`）`start --es activity SettingPrefActivity --es extras "highlight=…"` 直达页面，`uiautomator dump` 读 bounds。同一目标行（第 21 项「翻动时不加载图片」，`recyclerView` bounds `[0,54][466,466]`）**前后对照**：
    - 旧实现 `recyclerView.scrollToPosition(index)`：目标行 `[12,281][454,462]`，行底 462 ≈ 列表底 466 ⇒ **贴屏幕底部**（用户报的现象复现）。
    - 新实现 `scrollToPositionWithOffset(index, 0)`：目标行 `[12,58][454,239]`，行顶 58 ≈ 列表顶 54（差 4px 是 CardView 的上边距）⇒ **贴屏幕顶部**。
    - 边界：目标选末行「表冠适配灵敏度（Scroll）」时滚到能滚的最大位置停住（`[12,321][454,462]`，上一行仍留在视野顶上），与「后面内容不足一屏就滚不到顶」的口径一致。
    - 旁证（记一笔，省下次踩坑）：**华为真机 `GTJ0220A17004229`（Android 12）拦该调试桥广播**（`am broadcast` 只回 `result=0` 无 data，logcat `W BroadcastQueue: enter shouldPreventSendBroadcast`），`SYSTEM_ALERT_WINDOW` appops 豁免也不管用，所以这条链路只能在模拟器上验；模拟器上中文 extras 经 Windows `adb` 正常传递（不依赖 ADBKeyboard）。
  - **未验证（L2）**：①闪一次的红色高亮观感与手感；②目标行靠近列表末尾时「停在能滚的最大位置」是否符合用户直觉（若希望它居中或贴底，需要另外定口径），只能人看画面。

- **26.11.13 主菜单底部加「更多」折叠按钮（带开关）+ 菜单编排回到菜单后不刷新**：用户提「增加一个新的更多按钮可以折叠被隐藏的按钮 / 固定显示在底部 / 有开关可调节是否显示 / 就像调节我的页面的编排一样」，顺带报「改变编排不能用的 bug」并要求统一「我的页面」与「菜单排序」页的视觉风格（以菜单排序为准）。
  - **「更多」按钮**：`activity_menu.xml` 的根是垂直 `LinearLayout`——`top` + `RotaryScrollView(id=menu_scroll, layout_weight=1)`，滚动区里依次是 `menu_layout`（主按钮 + `id=menu_hidden_group` 折叠区）与代码现造的「更多」按钮。**「更多」不是悬浮条，而是列表末尾的最后一个普通按钮**：尺寸、行高、间距与上面那些一模一样，随列表一起滚动，只有翻到列表末尾才看得见（用户 26.11.14 明确纠正：「底部固定的意思是一直显示在最底部和正常按钮一样会滚动」，第一版做成了钉在屏幕底部、不随列表滚动的 footer，已改掉）。折叠区 `id=menu_hidden_group`（含标题「未启用项（点按可直接进入）」+ `id=menu_hidden_buttons`）插在「更多」**之前**，所以展开后按钮被顶到更下面、文案变「收起」；`toggleHidden()` 翻转显隐并用 `menuScroll.smoothScrollTo(0, hiddenGroup.top)` 把折叠区滚进视野（手表一屏只四个按钮，不滚用户看不到变化）。
  - **折叠区收哪些项**：`MenuConfig.disabledFrom(enabledMenu)` 按「当前真的能渲染」过滤——`exit` 恒可；`resume` 只在 `PlayerApi.buildResumeIntent` 有会话时；`dynamic/message/myspace` 只在已登录时；其余要求 `MenuActivity.btnNames` 里有对应页面。口径与主列表一致，所以在折叠区里点一项和它被启用时点一项走的是同一个 `killAndJump`（`createButton(btn, hiddenItem = true)` 只是**不接管** `dynamicButton/messageButton`——未读文案只能有一个实例）。
  - **开关**：`SharedPreferencesUtil.MENU_MORE_BUTTON`（`menu_more_button`，缺省 `true`）+ `loadMenuMoreButton()` / `saveMenuMoreButton()`。UI 落在**菜单排序页的最后一行**（`MenuSettingAdapter.TYPE_TOGGLE_MORE`，`getItemCount()` 因此 `+4`）：放最末是为了不动 `enabledStart()=2` / `disabledStart()=enabled.size+3` 这两个常数——两区的拖拽换算全依赖它们，动一行偏移就可能重现「拖完落错位」那类问题。该行不参与拖拽（`getMovementFlags` 返回 0）。顺手修了一个 holder 复用污染：`bindToggleMore` 会把 `item_fixed` 槽当作状态标签，所以 `bindEnabled` 里补了 `holder.fixed.setText(R.string.desc_menu_fixed)`。该行的标题文案 `desc_menu_more_button` 定成「底部显示更多按钮」（8 字）：466px 宽的手表上行内还要放勾选框，写「菜单底部显示「更多」按钮」会折成两行（L1 截图验过）；同时**不写「已关闭」字样**——勾选状态本身就够看，多出来的 `item_fixed` 会把标题再挤窄一格。
  - **「改变编排不能用」的根因（已修，已在设备上复现并验证修好）**：用户回报的确切现象是「在菜单排序页拖完、回到菜单顺序没变」。`MenuActivity` 原来**只在 `onCreate` 建按钮**，而 `BaseActivity.setupTopbarLongPressToHome()` 回菜单用 `CLEAR_TOP|SINGLE_TOP`——栈里已有实例就不重建，页面留着的还是旧顺序。第一版想用「配置指纹」判据（`enabled 串 | 更多开关 | 登录态 | 有无会话`）决定要不要重建，后来在设备上实测发现**拖拽本身一直是好的**（`input draganddrop` 之后 `menu_enabled` 立刻从 `recommend;short_video;…` 变成 `short_video;recommend;…`），所以问题只在「菜单页拿到的是旧数据」，于是把判据整个删掉、改成 **`onResume()` 无条件 `buildMenu()`**：按钮总共十几枚 `MaterialButton`，重排开销可以忽略，胜过任何指纹判据的缺口；`builtSignature` / `signature()` / `configChanged()` 三处死代码一并删掉。

  - **视觉统一（以菜单排序为准）**：两个*设置页*本来就共用 `activity_setting_menu.xml` 与 `item_menu_setting*` 三份行布局，真正不一致的是「我的」页的功能行 `cell_myspace_item.xml`（13sp、无外边距、无水波纹、靠容器 `showDividers` 隔行）。现改成菜单排序页那套：根加 `marginHorizontal=@dimen/activity_padding_horizontal` + `marginVertical=2dp` + `foreground="?attr/selectableItemBackground"`，文字 13sp→15sp，图标/箭头内缩 8dp→16dp；`activity_myspace.xml` 的容器去掉 `paddingHorizontal` 与 `divider/showDividers`（改由行自带 2dp 上下 margin），用户卡片补上同样的 `marginHorizontal` 并把 `marginBottom` 调成 2dp（+首行的 2dp = 行间那 4dp）。`MySpaceMoreActivity` 复用同一份布局，自动跟着统一。
  - **L0（26.11.13 本机实测）**：`:app:assembleCoexistDebug` BUILD SUCCESSFUL（`-Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g -Dkotlin.daemon.jvmargs=-Xmx4g`；默认 4G/2G 那次报 `OutOfMemoryError: Java heap space`），`:app:testStandardDebugUnitTest` BUILD SUCCESSFUL。主工作树当轮被另一路会话的 `strings.xml` 重复项（`uitext_writereplyactivity_006` 两份，aapt2 报 `Found item String/… more than once`）打坏，所以 L0 与下列 L1 都是在一个隔离 worktree（`outputs/wt-menumore`，基线 `52f50d19` + 本轮改动 + **临时**去掉重复字符串，scratch 用、不提交）里做的。
  - **L1（26.11.13 / 26.11.14 模拟器 `rebili_watch37` / 466px 实测，全部通过）**：①菜单列表顶端看不到「更多」，滚到末尾它作为最后一个按钮出现（`[12,370][454,466]`，尺寸与上面那些一致，`搜索/缓存/设置/退出/更多` 依次排下来）；②点它展开出「未启用项（点按可直接进入）」+ 入站必刷 / 全站排行榜 / 直播 / 时间线，按钮被顶到列表更下方且文案变「收起」，再点收起（展开时会自动滚到折叠区）；③菜单排序页 `adb shell input draganddrop 233 300 233 445 900` 把「推荐」拖到「短视频」之后，`menu_enabled` 立刻变成 `short_video;recommend;popular;…`（**拖拽落盘一直是好的**）；④长按顶栏回菜单，顺序确实是 登录 / 短视频 / 推荐 / 热门——用户报的「拖完顺序没变」不再复现；⑤开关行默认勾选，点掉后 `menu_more_button=false`、回菜单末尾不再有「更多」，再点回 `true` 就回来。开关行标题也已确认在 466px 下单行显示。
  - **仍属 L2（只能人看画面 / 判手感）**：①长按拖拽的手指手感与拖到未启用区后的落位（本轮是 adb `draganddrop` 等价构造的）；②「更多」展开/收起的动画手感；③「我的」页新行样式的观感（字重/间距/水波纹）是否与菜单排序页看起来一致，尤其圆屏表上 15sp 与 16dp 内缩是否偏大。
  - 交接单一并记：`run-as` 直启非导出 Activity 在 Android 12 上被拒（`package=com.android.shell does not belong to uid=...`），真机路径只能靠 UI 导航进「设置 → 菜单」。
  - **26.11.14 用户定案**：保持列表末尾就地展开，开关留在「菜单设置」末行，不改独立页、不挪位置。

- **26.11.13 评论长按的两处残留不一致（承接 26.11.11 的 `7976822b`，只动 `ReplyAdapter.kt`）**：用户回报「自己发的评论长按不弹面板、反而直接进详情页；但长按照样能弹面板、点删除键也能弹面板，行为不一致」。核对后分两件事：①**用户复现的是线上 26.11.09 / 更早的 release**——那些包都不含 `7976822b`（`git merge-base --is-ancestor 7976822b HEAD` 为真，本地 main 早已修好），属「已修未发版」，不是修复失效；②HEAD 上仍有两处同源的口子，本轮一并修掉：
  - **详情页整卡长按完全无响应**：`7976822b` 给 `itemView` 挂长按的条件写成了 `panelEnabled && !isDetail`，于是评论详情页里「长按正文弹面板、长按昵称/时间/卡片留白毫无反应」，还是两套行为。去掉 `!isDetail`，正文与整卡统一走 `showManageMenu(reply)`；详情页删根评论本来就会 `finish()`（`deleteReply` 里 `indexBefore == 0 && isDetail`），补上长按不会删完留在空页。
  - **面板开关关 + 复制开关关时长按退化成点击**：`StringUtil.setCopy`（`util/StringUtil.java:218-227`）在 `copy_enable` 关闭时**根本不挂监听**，而原实现又把 `itemView` / `childReplyCard` 的长按显式置 `null`，抬手就落回卡片的点击监听 → 直接进评论详情页；同一时刻末尾删除键长按却能弹面板（「点删除键能进面板」正是这么来的）。新增私有 `bindLegacyCopyLongPress(view, content)`：复制开着→打开复制界面（旧行为），复制关掉或正文为空→**只消费这次长按**（始终 `return true`），绝不让它退化成点击。正文 / 整条卡片 / 楼中楼卡片 / 每条楼中楼文字四个落点全部改走这一个入口，长按落在哪里结果都一样。
  - 顺带记录（本轮未改）：长按落在点赞数 / 头像 / 图片 / 投票标签这些小控件上会被它们自己的监听吃掉、不出面板——这些控件本身有独立动作，抢走长按是合理取舍，真要统一再单独排。
  - **L0（26.11.13 本机实测）**：`:app:assembleDebug :app:testStandardDebugUnitTest` BUILD SUCCESSFUL（47s，两个 flavor 都编，`private/` 已注入），`223 suites / 2414 tests / 0 失败 0 错误`。
  - **未验证**：长按手感、详情页按昵称/留白是否也弹面板、两个开关都关掉后长按是否不再跳详情页，都只能真机点（L2，本机无 adb 设备）。已加进 [`manual-test-guide-261113-total.md`](manual-test-guide-261113-total.md) 的 **T07 第 ③④ 项**；注意 26.11.13-exp.1 的 APK 是 `965e9781` 基线、**不含本轮修复**，要在真机上验这两项得重新出包。
- **26.11.13 本地编 exp 包（`26.11.13-exp.1` / `26111321`）并写「26.11.09.2 起」总验收单**：按要求把 `app/build.gradle` 的 `appVersionDate` 落到 `26.11.13`（文件头四条渠道示例一起更新），并把 26.11.09 的 30 条日志整组归档进 `update_history_log`（新插 `<item>## 2026-11-09</item>` 组，去掉「【26.11.09 本次更新】」首行），`update_log_current` 换成 26.11.13 的 28 条（首行锚点「【26.11.13 本次更新】」，`:app:verifyVersionConsistency` 靠它）。**只在本机编译，不 push、不打 tag、不跑 CI、不同步官网**（用户口径「仅本地编译」）。**L0（26.11.13 本机实测）**：`./gradlew.bat :app:assembleRelease -Pchannel=exp -Pseq=1` BUILD SUCCESSFUL（2m49s，两个 flavor 都出包，`private/` 已注入、`CN=BiliClient` 签名通过 `apksigner verify`），`:app:verifyVersionConsistency` 输出「版本号一致 versionName=26.11.13 versionCode=26111300」。产物：`app/build/outputs/apk/{standard,coexist}/release/` 各 4 支（universal 12.9 / 13.0 MB，arm64-v8a 7.3 / 7.4 MB）。总验收单 [`manual-test-guide-261113-total.md`](manual-test-guide-261113-total.md)（T01~T08，覆盖 `acf0c975` → `965e9781` 的 77 个提交，逐条细化做法仍看三份批次单）。**未验证**：T01~T08 全部只能真机看画面 / 判手感 / 听声音（L2），本机无 adb 设备。
- **26.11.12 同步 origin/main（`5ccb7973` → `96ecb735`，14 个提交）并修掉合并后的重复字符串**：本地未提交的 26.11.11 反馈批次与远端 4 条功能修复（私信输入框 #157 / 复制文本选区 #158 / 评论长按面板 / 合集续看补齐）在 5 个文件上冲突。合集续看是**同一功能的两套实现**，择一留 main 那套（`resolveFallbackAid` + 全局降级槽 `collection_resume_last_played_v1`，带 6 例单测、已进 fix-progress/FEATURES），丢弃本地草稿（`resolveTargetAid` / `CollectionResumeCodec.decodeLastPlayed` / `putStringSync` 同步落盘 + adapter 重构）——草稿留在 `git stash`（备份分支 `wip/stash-261112`）可随时回退。合并后 `res/values/strings.xml` 出现两条 `btn_select_all`（远端 `f6d731dc`/`7e139505` 补的断链字符串 + 本地反馈 #94 多选各加一条），AAPT 直接报 `Found item String/btn_select_all more than one time`、`:app:packageCoexistDebugResources` 失败；删掉本地那条、保留上游带注释的那条。**L0（26.11.12 本机实测）**：`:app:assembleCoexistDebug :app:testCoexistDebugUnitTest` BUILD SUCCESSFUL（含 `private/` 注入），`223 suites / 2414 tests / 0 失败 0 错误`。**未验证**：本轮新同步的四条修复的画面/手感项见交接单 [`manual-test-guide-261112-sync-batch.md`](manual-test-guide-261112-sync-batch.md)（H01~H08）；另注意合集续看落盘由本地草稿的 `putStringSync` 变成 main 的 `putString`（异步 apply），播完立刻划掉 App 有极小概率丢记录，记作已知差异、不算回归。
- **26.11.12 合集续看补齐「[上次看到]」标记与无 id 降级记录（控制台 App 反馈 #152，PR #31）**：合集续看此前只做到「起播写记录 + 进合集页定位」两件，本轮补齐拍板口径的第三件，并修掉审查指出的三处漏洞。
  - **标记**：`CollectionInfoActivity` 的两个 adapter 在 `showVideoCard` 之后，给目标那一集的标题前置 `[上次看到]`（`uitext_collectioninfoactivity_004`，`TextUtils.concat` 保留标题原有 span）；目标变化时 `notifyDataSetChanged()` 重画。
  - **无 id 降级**：`CollectionResumeStore.save` 在 `ugc_season.id` 缺失（`seasonId<=0`）时不再静默丢弃，改写全局槽 `collection_resume_last_played_v1`（`saveLastPlayed` / `lastPlayed`）；读取侧新增纯函数 `CollectionResumePolicy.resolveFallbackAid`，只在「那一集仍在本合集里」时采用（降级槽不区分合集，上一次可能来自另一个无 id 合集）。
  - **只在目标变化时滚**：`CollectionInfoActivity.onResume` 先比对目标 aid，变了才重滚；用户自己滑到第 20 集、接电话回来不会被拽回第 5 集。
  - **写入点**：收口到 `JumpToPlayerActivity.jump()`（真正交给播放器之后，取到流又秒退不挪记录）与 `PlayerActivity.switchToOnlinePage`（播放器内换集）；删掉三处 `if (collection.id > 0)` 重复守卫，由 `save` 内部统一处理。
  - **「进入合集」**：属于合集的条目（详情页 / 分页起播）把 `videoInfo` 放进 `PlayerData.enterableInfo`，多P 时播放器选集卡片才有「进入合集」一行；真实多P 换P 时保留原判断。
  - **L0**：本机 `./gradlew` 被工单执行环境禁用（`DSH_SUPERAGENT_BLOCKED_COMMANDS` 含 `gradlew`），未在本机跑构建/单测；由 PR #31 的 CI 在合并前跑 `:app:assembleDebug`、`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 与 `:app:assembleRelease`（与 AGENTS.md 的 L0 口径一致）。**未验证**：标记观感与滚动位置只能真机看画面（L2），清单见 `docs/review/l2-pending.md` §K。
  - **建议（未执行，等用户定）**：本功能在 26.11.12 同步时被判定为「同一需求的两套实现」，已择 main 这套（`resolveFallbackAid` + 全局降级槽）、本地草稿整份丢弃并把备份留在 `wip/stash-261112`（详见上方「26.11.12 同步 origin/main」那条）。**现在真正待定的只剩那个备份分支留不留**：留着可随时回退到草稿路线（`putStringSync` 同步落盘 + adapter 重构），删掉则彻底放弃同步落盘、连带放弃对「播完立刻划掉 App 可能丢记录」这一已知差异的补救（现用 `putString` 异步 apply）。建议先留到 26.11.13 版发出去、真机确认合集续看（§K）无回归后再删；**本会话没有删除任何代码或分支**。
- **26.11.11 评论长按误进详情页（MaiBot 转达的 QQ 群反馈 #22 第 1 项，群 1129816758）**：`ReplyAdapter` 原来只在 `replyText` 上挂长按，按在昵称 / 时间 / 留白、楼中楼卡片或楼中楼文字上时事件落到 `itemView` / `childReplyCard` 的点击监听，抬手被当成「打开评论详情」。现在整条评论（非详情页）、`childReplyCard` 与每条楼中楼都挂同一个 `showManageMenu` 入口，并在绑定前按当前开关清掉复用 holder 的旧监听；面板的「置顶 / 取消置顶」只在 `reply.root == 0`（一级评论）出现，避免楼中楼展示必然回 12030 的入口。**本机未编译（执行环境禁用 gradle），L0 由 CI `build-and-test` 完成（Debug 双 flavor、两 flavor 单测、Release R8 冒烟）**；长按手感与「抬手不误跳详情页」仍需真机 L2 确认，清单见 `docs/review/l2-pending.md` §J。反馈第 2 项「应用内反馈发送失败：网络」无法从客户端代码定位：`TerminalApi.submitFeedback` 只在 `IOException` 时给这句文案，DNS / TLS / 服务端非 2xx 都会命中，而服务端代码不在本仓库，本轮未改。
- **版本号**：`26.11.07`（按新的三通道编码为 `versionCode 26110700`，tag `26.11.07`）——**编码规则已于 26.11.08 改成三通道派生**（见下条），`app/build.gradle` 里只手写 `appVersionDate`；CI 的 `verify-version` 拿 tag 的日期段去比 `appVersionDate`，再经 `-Pchannel`/`-Pseq` 传给 Gradle，构建后用 `aapt2 badging` 复核产物；`strings.xml` 的 `update_log_current` 锚点是「【26.11.07 本次更新】」，`:app:verifyVersionConsistency` 以该锚点校验。tag `26.10.31`、`26.10.31.1`、`26.11.01`、`26.11.02`、`26.11.06`、`26.11.06.1`、`26.11.07` 均已占用（发版记录见 §4）；补丁版 `26.11.07.1` **沿用** `versionName 26.11.07` / `versionCode 2611071`（旧编码；按新编码是 `26110701`，尾号 1 必须等于 tag 的 `.1`），只多带两处修复（§3.135 / §3.136）。
- **26.11.11 私信输入框的内容被右侧图标盖住（控制台反馈 #157「私信表情图片发送图标太大，内容看不见了」）**：根因在 `res/layout/activity_private_msg.xml` 的 `layout_input`——它是 `RelativeLayout`，输入框 `match_parent` 宽 + `layout_toStartOf="@id/emote_btn"`，而 RelativeLayout 里 `match_parent` 的子 View 仍按**整行**宽度测量，`layout_toStartOf` 只改右边缘位置：输入框并没有缩成「三个按钮左侧的剩余宽度」，而是仍按整行宽度排版、文字区域与按钮重叠（按钮后声明、画在上层），26.11.06 加表情按钮后又多叠住 42dp，于是输入内容看不见（提示语与刚打的字都被右侧图标压住）。修法只动这一个容器：`layout_input` 改成横向 `LinearLayout`，输入框 `layout_width=0dp` + `layout_weight=1`，宽度就是「整行 − 三个按钮」，换行也按真实可用宽度算；按钮顺序 / id / 点击 / 表情 / 发图逻辑与上滑隐藏动画全不变（`layout_input` 仍是 `View`，`getViewAnimation` 不依赖容器类型）。**未编译验证（按项目约定不在本机编译）**：只做静态自查——`git diff` 通读、XML 解析通过、`PrivateMsgActivity.kt` 里对 `msg_input_et` / `layout_input` 的引用逐处核对（无 `RelativeLayout.LayoutParams` 假设）。**未验证（L2）**：见 `docs/review/l2-pending.md` §I（多行增高后文字是否仍被图标盖住、三个按钮的排布与命中区）。
- **26.11.11 复制文本页精确调整时看不见选中区间（控制台反馈 #158，OPPO / OWW212）**：`CopyTextActivity` 的正文是个 `EditText`，选区高亮与手柄**只在它自己拿到焦点时**才由系统绘制；而「精确调整」要往下面的起止下标输入框里打字，手指一落到 `begin_index` 上焦点就离开正文，`setSelection` 只是在 `Editable` 里挪了选区位置，画面上什么都不剩。修法：该页自己按下标给选中区间铺一层 `BackgroundColorSpan`，取的是主题里同一个选区底色 `@color/text_selection_highlight`（`themes.xml` 的 `android:textColorHighlight` 用的就是它，观感与系统选区一致）——`syncSelectionHighlight()` 在下标 TextWatcher、两个下标框获得焦点时、`onCreate` 末尾各调一次（直接点进下标框也能马上看到选中区间），每处都重设并夹紧边界（下标解析失败 / 起止相等 / 越界只清不高亮），正文重新拿到焦点时撤掉自绘底色、交还系统原生选区（避免两层叠色），正文仍持有焦点时（点 `<` / `>` 按钮，触摸模式下按钮不抢焦点）不叠自绘。本特性只动 `CopyTextActivity.kt`（不改布局、不改文案）；构建修复另见本 PR 的两条 `fix(settings)` / `fix(build)` 提交（把 main 上 `6501c3f4` 误插进 `switch(...)` 参数表的「顶栏高度」`input(...)` 挪回同级语句；补上 `activity_simple_refresh.xml` 引用却缺失的 `btn_select_all` 字符串，`5ccb7973` 起公开构建在 `processResources` 就失败），都是 main 上的既有断链，与本特性无关。**未编译验证（按项目约定不在本机编译）**：静态自查 `git diff` 通读，`ContextCompat.getColor` / `BackgroundColorSpan` 在仓库里均有既有用法（`GlassStyle.kt` / `NoteActivity.kt:176`）。**未验证**：高亮在真机上的观感（选中区间是否一眼可见、与原生选区色是否一致），只能看画面（L2），清单见 `docs/review/l2-pending.md` §H。
- **26.11.11 接入 BilibiliSponsorBlock 自动跳过广告片段（默认关闭，五档）**：数据来自第三方志愿者标注服务（`bsbsb.top`），**复用**已有的片头片尾跳过链路（`SkipDecider` + `ViewPointSkip.Segment` + `seekToPosition` + Snack 撤回），没有另写一套跳过逻辑。落点：新增 `api/SponsorBlockApi.kt`（独立 `OkHttpClient`，4s 连接 / 6s 读、`followRedirects=false`、**不带 Cookie 与任何拦截器**，绝不走 `NetWorkUtil` 主客户端——那边的 `CookieSaveInterceptor` 会把登录态泄露给第三方）、`player/SponsorSkipPolicy.kt`（纯函数：档位→类别集、区间过滤）、`player/SponsorSkipPrefs.kt`、`res/values/player_sb_strings.xml`；`PlayerActivity.kt` 加 `sponsorDecider` 字段与 `maybeAutoSkipSponsor` / `ensureSponsorSegments` / `loadSponsorSegments` / `applySponsorLevel` / `resolveBvid` / `showSponsorUndoSnack` / `sponsorCategoryLabel` 七个方法（250ms 定时器里懒加载；`SkipDecider` 的 generation 丢弃换集后的过期响应；与片头片尾共用 `skipQuietUntilMs` 静默期；失败静默 + 60 秒冷却）。设置项落在「播放器 → 自动跳过」，设置搜索索引同步。
  - **隐私**：只发 BVID 的 SHA-256 前 8 位，不发明文 BVID；代价是同一前缀会带回别的视频，因此按 `videoID` + `cid` 双重过滤，并用标注里的 `videoDuration` 与当前视频总时长做 ±2 秒校验（换源前的旧标注秒数已错位，直接丢）。
  - **范围**：只跳 `actionType=skip`（静音型 `mute`、整段标签 `full` 后续再做）；`poi_highlight` 任何档位都不跳（它是跳转点，不是广告段）；直播 / 本地 / 番剧（`isPgcPlayback()`）一律不查；档位「关闭」时**一个请求都不发**。
  - **顺带修**：播放器此前拿不到 BVID（`PlayerData` 没有该字段、`buildTerminalPlayerIntent` 也不传），所以高能进度条从历史 / 缓存入口进来时是空的——现在 `PlayerData.bvid` 贯通到播放页，既是 SponsorBlock 的主数据源，也让高能进度条恢复正常；缺 BVID 的老入口按 aid 现查一次兜底。
  - **同日晚些时候按用户要求收口设置**（原话「把番剧跳过和广告/恰饭跳过一起放到设置里」）：新增「自动跳过」分组（`PlayerSettingsGroups.SKIP`，排在「播放默认值」之后、「画面与显示」之前），把「自动跳过片头片尾」与「跳过广告片段（SponsorBlock）」两条从「画面与显示」整块搬过来，条目名不动；分组标题走 `player_sb_strings.xml` 的 `setting_player_group_skip`，设置搜索索引同步加了组标题与子页名。
  - **自定义档（第 6 档）与逐类别勾选页**：`SponsorSkipPolicy` 增 `LEVEL_CUSTOM` / `resolveCategories(level, custom)` / `sanitizeCategories()`（剔掉 `poi_highlight` 与未知 id），`buildSegments` 主签名改成吃「允许的类别集合」而不是档位数字（旧重载留给固定档）；`SponsorSkipPrefs` 增 `categoryKey()` / `categoryDefaultEnabled()` / `customCategories()` / `effectiveCategories()`，一个类别一个布尔键；新增 `SettingSponsorCategoryActivity`（10 个开关 + 一行说明，进页记快照、离开时勾选有变化就把档位切成「自定义」）与 `player/SponsorCategoryLabels.kt`（类别 id → 中文名的唯一映射，播放页 Snack 与设置页共用）。播放页的片段表签名从「档位数字」改成「类别集合排序后的字符串」，自定义档下改勾选播放中即时生效。
  - **L0（26.11.11 本机实测）**：`:app:assembleDebug` BUILD SUCCESSFUL（18:20，另一批补上 `tabTextSize` 的 `TextAppearance` 之后跑的整包），`:app:testStandardDebugUnitTest` 全量无失败；新增 `SponsorSkipPolicyTest`（17 例：档位逐级包含、任何档位不跳 `poi_highlight`、非法存档回落关闭、只跳 skip、别的一集不跳、上游没给 cid 放行、换源时长对不上就丢、脏区间丢弃、窄档不放行 `filler`、自定义档按勾选算集合 / 一个没勾就不跳 / 勾选里混进的精彩时刻与未知类别被清洗 / 存档里的自定义档能读回）与 `SponsorBlockApiTest`（9 例：`hashPrefix("BV1GJ411x7h7") == "d5c2bf68"`、同前缀别的 videoID 整条滤掉、大小写不敏感、按 cid 过滤分 P、缺字段片段跳过、空数组 / 非 JSON / HTML 都返回空表等），`tests="17"/"9" failures="0" errors="0"` 全绿。
  - **未验证**：真机上的跳过观感、Snack 文案与撤回手感、跳完弹幕是否跟着走属 L2；本机无设备。验收单见 [`manual-test-guide-261111-sponsorblock.md`](manual-test-guide-261111-sponsorblock.md)（S01～S05）。
- **26.11.11 分段条 / 页签统一换成「玻璃胶囊」行（用户报「这些按钮和横条巨丑无比且和背景融合度很差」；后又追加「实线不要贯穿整条轨道」「看起来像能拖动滑动的样子，尽量压缩点空间」）**：
  - **新增** `ui/appearance/SegmentedBarStyle.kt`（`object SegmentedBarStyle`）——一套「轨道 + 选中胶囊」的画法，两个私有 `Drawable`：`TrackDrawable` 是 1dp 描边圆角矩形（`TEXT_PRIMARY` 加 α`0x1C` 填充 / α`0x2C` 描边，**不写死色值** ⇒ 亮色 / 暗色主题各自成立），`PillDrawable` 是水平渐变 `PRIMARY` α`0x4A` → `PRIMARY_LIGHT` α`0x14`、内描边 α`0x5A`，**不做外发光**（试过 3dp α`0x1A` 光晕，手表小屏上只会糊边，已删）；选中项文字 `TEXT_PRIMARY` + `Paint.isFakeBoldText`（**不换 typeface**，否则会顶掉 `FontStyle`）。轨道与胶囊共用同一条高度公式 `min(BAR_HEIGHT_DP, bounds.height() - 2 * edge)` ⇒ 任意容器高度都不会出现「胶囊比轨道高」的错位。
  - **主色竖条只在一侧、且不贯穿轨道**：`PillDrawable` 把竖条画在胶囊**内缘**（`ACCENT_INSET_DP=3dp` 起、2dp 宽、18dp 高、圆角收边），左右各一个 `RectF`；`accentSidesOf(item)` 按「可见兄弟里的序号」判定——**首格画左、末格画右、夹在中间不画**（中间画会变成格子之间的分隔线）、单格（收藏夹页程序化加的搜索框）也不画；`refreshTabs` 用同逻辑的纯索引版 `accentSidesAt(i, tabs.tabCount)`。`itemBackground(context, selected, accentSides)` 走 `@JvmOverloads`，默认 `ACCENT_NONE`。
  - **拖动切换**（`installDrag`）：`installDrag(bar: ViewGroup, excluded: View? = null)` 给整条挂**同一个** `OnTouchListener`（闭包共享 `var current`），用 `event.rawX` + 各格 `getLocationOnScreen` 算命中格（不用父容器坐标，TabLayout 页签在内部 ScrollView 里会被滚动偏移带歪），落在整条左 / 右侧外侧贴首 / 末格；因为吃掉 DOWN 的那个 View 会一路收到后续 MOVE / UP，**跨格拖动天然成立**。`excluded` 用来排除「清除已看完」这类动作格（拖过去不该弹确认框）。`installDrag(tabs: TabLayout)` 只在 `MODE_FIXED` 装，`MODE_SCROLLABLE`（表情面板 / 更新日志）直接返回——横向拖动会和「滑动看更多页签」抢同一个手势。
  - **压缩空间**：`BAR_HEIGHT_DP` 40→32dp、`RADIUS_DP` 14→10dp、`EDGE_INSET_DP`/`TRACK_EDGE_DP` 3→2dp；新增 `@dimen/segmented_bar_min = 40dp` 作格子行高（`activity_simple_refresh.xml` 的 filterBar/sortBar、`activity_bangumi.xml` 的 tabBar、两个 TabLayout 全部改它），格内 `paddingVertical` 4→0dp、文字 `13sp`/`text_subtitle` → `@dimen/text_body`（12sp）、容器 `paddingVertical` 2→0dp。**行高比 `touch_min` 矮 8dp 是刻意的**：整条现在是一个可拖动控件，格子不再需要各自撑 48dp 才点得准。TabLayout 侧没有 `tabTextSize` 属性，文字改走新增的 `TextAppearance.ReBili.SegmentedTab`（`styles.xml`，12sp + 非全大写）经 `app:tabTextAppearance` 引用。
  - **落地 5 处同类控件**：`activity_simple_refresh.xml` 的 `filterBar`（`WatchLaterActivity` 的「全部 / 未看完 / 清除已看完」）与 `sortBar`（`FavoriteVideoListActivity` 三个排序项 + 该页程序化加入的搜索框 `searchBar`）、`activity_emote.xml` 的 `tl_tab`（表情分类页签）、`activity_update_history.xml` 的 `version_tabs`（版本页签）、`activity_bangumi.xml` 的 `tabBar`（推荐 / 追番 / 索引）。TabLayout 侧 XML 改 `app:tabIndicatorHeight="0dp"`（替换原 `tabIndicatorFullWidth="false"`）+ `tabPaddingStart/End="8dp"`；代码侧 `styleTabs(tabs)` 里额外 `setTabTextColors(...)`，因为 TabView 的 `update()` 会用内部 ColorStateList 重刷文字，只改子 View 会被覆盖。
  - **范围探路**（用户追问「这类按钮可能不止这些」）：全仓 `TabLayout` 只有上述 2 处；手写分段条另有番剧 `tabBar`；`manageBar` / `groupBar` / `subBar` / `activity_simple_main_refresh.xml` 的 `multiSelectBar` / `download_bottom_bar.xml` 是**动作行**，`PageSelectorAdapter` / `SubtitleAdapter` / `QualitySelectorAdapter` / 投票选项等是**列表内选择器**，形态不同，本轮均未动，列为后续候选（用户未拍板）。
  - **L0（26.11.11 本机实测）**：`:app:assembleDebug :app:testStandardDebugUnitTest` BUILD SUCCESSFUL；`ui/appearance/SegmentedBarStyleTest` 7 例（断言 `colorsFor` 的 α 派生与换主题色后结构不变）`tests="7" failures="0" errors="0"`。踩坑四条：①`Unresolved reference 'subscriptionErrorText'` 是 **Kotlin 增量编译缓存脏**（该符号确实存在），清 `app\build\kotlin` 即过；②`app:tabTextSize` **不是合法属性**，只能用 `tabTextAppearance`；③Kotlin IR lowering 报 `OutOfMemoryError: Java heap space`，`gradle.properties` 里 `kotlin.daemon.jvmargs=-Xmx2048m` 不够，用 `"-Pkotlin.daemon.jvmargs=-Xmx4096m -XX:+UseG1GC"` 重跑通过（另注意本机 qemu 模拟器能占 8~19GB 内存，构建前先看 `Get-Process | Sort WorkingSet64`）；④这些文件在 HEAD 里是 **LF**，用 `[System.IO.File]::WriteAllText` + `.Replace("`r`n","`n")` 写回，否则 `git diff` 会整文件重写。
  - **L1（AVD `rebili_watch37`，`wm size 466x466` + `wm density 320` ≈ 233dp 真表几何，standard debug universal 包）**：①收藏夹排序条横向拖动 收藏时间 → 投稿时间，选中胶囊跟着手指走到末格、竖条出现在**右侧**；②稍后再看筛选条拖动 全部 → 未看完，顺利切换且拖过「清除已看完」**不弹确认框**；③番剧 tabBar 拖动 推荐 → 索引，页签与下方内容一起切换；④版本页签 12sp 无裁切、无下划线、轨道与胶囊对齐；⑤三个页签行实测高度 80px = 40dp（改造前 96px = 48dp），筛选条「清除已看完」在 12sp 下不再折行。
  - **未验证**：五处控件在「经典终端 / 亮色 / 玻璃质感」各主题下的观感、拖动在真表上的手感（L2，只能看画面 / 判手感）；表情面板页签需播放器侧 extras 才能进入，本轮只用版本页签验证了 TabLayout 这条路。设计对比稿与五种备选方案在本地 `outputs/design-tab-bars/index.html`（`outputs/` 不入库）。
  - **未验证**：五处控件在「经典终端 / 亮色 / 玻璃质感」各主题下的观感（L2，只能看画面）；本机 AVD `rebili_watch37` 的渲染截图整屏彩色块 / 条纹（gfxstream 截图坏），不能作为判断依据。设计对比稿与五种备选方案在本地 `outputs/design-tab-bars/index.html`（`outputs/` 不入库）。

- **26.11.11 控制台反馈一批 17 条落地（播放器退出链 / 下载分段 / 消息与搜索 / 专栏正文与合集续看 / UI 小修）**：需求与逐条口径见 [`docs/specs/feedback-261111.md`](../specs/feedback-261111.md)（36 条全量 + 本批拍板），实现落点见该文 **§9**，验收项见 §2.1 同名七条与 [`manual-test-guide-261111-feedback-batch.md`](manual-test-guide-261111-feedback-batch.md)（P01~P42）。**L0（26.11.11 本机实测，工作区含用户既有 `DownloadService.kt` / `DownloadProgressStore.kt` 未提交改动）**：`:app:assembleDebug` BUILD SUCCESSFUL（standard / coexist 双 flavor）；`:app:testStandardDebugUnitTest --rerun` **217 suites / 2343 tests / 0 失败 0 错误**。改动 `47 files changed, +2068/−222`。分域要点：①**播放器退不出（#117/#135/#139②）**三道拦截串联——锁定态 `shouldSwallowExit`、`PlayerActivity.kt:863 if (swipeSeekEnabled) return`（为横滑调进度加的，26.10.22 又把 `noSwipe` 改成无条件 true，连窗口右滑也掐掉）、`BACK_DISABLE`，于是三条退路全断；现在新增开关 `SettingsKeys.PLAYER_BACK_KEY_EXIT`（默认开，落在播放器设置手势段）在吞键**之前**放行返回，锁定按钮由「3 秒后 GONE」改为**压暗常驻**（`IDLE_ICON_ALPHA=0.35`）并给提示；顺带把 `finish()`（`:7066/:7074`）与 `onDestroy`（`:3983/:4026`）的主线程 JNI 挪走——退出链上不再读 `ijkPlayer!!.currentPosition`、release 进独立短命线程。②**弹幕跳完变少（#143）**：DFM 的 `addDanmaku` 只把落在 `[mLastBeginMills, mLastEndMills]` 的弹幕加进绘制子集，远跳后补的段大多不进子集；现在跨 360s 分段就整体重建（`DanmakuStreamLoader.rebuildWindow` / `DanmakuWindow.needsTimelineRebuild`，纯函数带 3 例单测），同段只增量补，倍速松手也重建一次；失败路径 `MAX_FETCH_ROUNDS` 4→1、冷却只对同一窗口生效。③**横屏（#117 附带）**：旧全局「横屏模式」(`UI_LANDSCAPE`) 会连带强制播放器横屏，26.11.x 播放器方向自成一套后这条断了 → `defaultOrientationMode()` 改为 `PLAYER_AUTOLANDSCAPE || UI_LANDSCAPE`，设置页默认值标签同口径。④**下载截断（#87/#90）**：`api/PlayerApi.java:170 parseDurlSegments` + 三处取 URL 的地方改为解析**完整分段清单**挂到 `PlayerData.videoSegments`（`videoUrl` 仍取第 0 段，起播不变），新增 `DownloadSegmentPlan`（清单 / 总字节 / 复用判定）+ `DownloadSegmentMerger`（按序拼接 + 长度校验），`DownloadService` 走「逐段下载→复用已完成段→拼接→清临时」，download.db **4→5** 加 `segments` / `fail_reason` 两列（只 ALTER），失败原因（HTTP 码 / 收到字节不足 / 段号）显示到列表。**播放器侧仍只播第 0 段**，补「段尾自动切下一条」要自维护段索引并把 `video_now` / 进度上报 / 续看 / 弹幕时钟 / seek 全加段偏移，粗估 >100 行且动进度口径 → 记遗留不做；`bilibili-API` 文档自身对「FLV 多段是否已下线」自相矛盾（`videostream_url.md:1915`），本机也没抓到真实响应体，故**多段截断不是唯一根因的断言**，失败原因文案就是为下次反馈取证。⑤**收到的赞翻不动（#150）**：`getLikeMsg` items 取 `data.total.items` 却按 `data.cursor` 走游标、分页参数照抄 reply 的 `reply_time`；`NoticeActivity` 只 onCreate 拉一次且 catch 里置 `bottom=true` 导致永不翻页。现在三项都修（items / 游标双形态兜底、`like_time` 参数、`onResume` 重拉 + `loadGeneration` 防竞态 + 失败可重试空态），消息角标 `checkMessageUnread` 也把点赞未读算进去（靠「代码自身三处矛盾」推出字段位置，双形态写法保证推错也不弄坏好路径）。⑥**搜索重复（#148）**：翻页结果走推荐页那套 `RecommendPagePolicy.filterNewCards` 按 bvid 去重（整页重复就停），刷新时代际号失效在飞请求；legacy 搜索补 `seid`。⑦**清空搜索记录（#123）**：历史区右上加「清空」胶囊（刻意 `focusable="false"`，否则会抢焦点把浮层连同自己一起隐藏）+ 二次确认。⑧**专栏正文读不了（#142）**：cv 号文章必然走「整页抓 `__INITIAL_STATE__`」，抓不到就整条 throw 与「本来没正文」混为一谈，而 `x/article/view` 的 HTML 正文全仓没有渲染器（`Html.fromHtml` 0 处）→ 新增 `util/ArticleHtmlConverter.java`（白名单状态机、零 `android.*`、超长段落优先换行→空格→硬切并避开代理对，18 例单测）+ 两个语义化异常（拦截 / 空）分别出文案 + 文章接口兜底合并；`OpusParagraph` 补 `TYPE_CODE` 与未知段落降级，不再露 `[无法识别段落：N]`。**`<b>/<i>/<h1-3>` 只出文字不加粗**（转换器要保持纯 JVM 可单测，样式只能放 UI 层，动 `OpusContentAdapter` 会连带影响图文动态）→ 留人工决定。⑨**合集续看（#151/#152）**：`CollectionResumeStore` 原唯一写入点在详情页 `playClick`，别的入口都不写；现在下沉到 `JumpToPlayerActivity` 所有起播路径 + 详情页 + 播放器内换集（`PlayerActivity.kt:5886 switchToOnlinePage`，另两处 `getVideo` 是换清晰度 / 互动分 P，aid 不变故不写），`seasonId<=0` 只写降级槽，读取侧 `CollectionInfoActivity.onResume` 重定位 + 「[上次看到] 」 标记（`markResumeSeen` 是两个 adapter 各自的 private 方法——Kotlin 嵌套类非 inner 拿不到外层成员，放文件级顶层函数要重排文件尾，5 行重复是刻意取舍）。⑩**UI 五连**：长按阈值 200ms→600ms 收敛到 `util/view/LongPressDelay.CARD_MS`（手表滑列表误触 + 略长于系统 500ms），多选视觉从整卡 `alpha=0.45` 改成 `▸`/`›` 引导符 + 标题主色（视频卡 / 历史 / 用户投稿 / 文章 / 下载 / 缓存六处统一）；私信拦截提示加开关（`PRIVATE_MSG_INTERCEPT_TIP_ENABLE`）；短视频双指缩放补拖动平移（少一根手指立刻把手势还给「上下滑切视频」）；缓存页与下载列表页补「全选」（只勾当前可见项，缓存页顶层优先未分类视频）。**#125 弹窗按钮错位是防御性修法、不是实测定位**：静态已排除 `TouchDelegate` 0 命中、`translationX`、`dialog_background` inset（两版本都是纯 `<shape>`），只剩测量时序可疑 → `fitButtonBar` 提前到算尺寸之前 + `applyPlan` 收尾统一一次 `requestLayout`，真机若仍偏需在 `fitButtonBar` 里 dump bounds 与 `getHitRect` 对比继续查。
- **26.11.11 控制台反馈 P3 两条落地（直播文本弹幕 / 视频转发到私信）**：spec 见 [`docs/specs/feedback-261111.md`](../specs/feedback-261111.md) §6 + 实现落点 §9.8/§9.9，验收 P32~P42。**L0**：`:app:assembleDebug` BUILD SUCCESSFUL（双 flavor）；`:app:testStandardDebugUnitTest --rerun` **218 suites / 2371 tests / 0 失败 0 错误**（新增 `LiveDanmakuApiTest` 20 例、`PrivateMsgApiTest` 8 例）。①**直播弹幕发不出去（#156）**：原代码根本没有发送能力——`PlayerDanmuClientListener.kt:32-49` 只在 `onOpen` 发握手包（protover=3）、`:111` 只发空包心跳。新增 `api/LiveDanmakuApi.java`：`POST https://api.live.bilibili.com/msg/send`，body 固定顺序手拼、**键序与取值逐项对齐 PiliPlus**（`PiliPlus/lib/http/live.dart:36-78`）`bubble/msg/color/mode/room_type/jumpfrom/reply_mid/reply_attr/replay_dmid/statistics/reply_type/reply_uname/fontsize/rnd/roomid/csrf/csrf_token`（不用 `NetWorkUtil.FormData`，底层 `HashMap` 键序不定、无法整串断言；`statistics` 标 web 端 `{"appId":100,"platform":5}`，`reply_*` 因 App 无 @ 入口恒 0/空串）；**query 带 `web_location=444.8` 并走 wbi 签名**（`signedSendUrl()` 调 `ConfInfoApi.signWBI`，取密钥失败退回未签名 URL 照发），返回码映射成枚举 `Kind`（成功 / 本地节流 / 空 / 超长 / 未登录 / csrf 失效 / 频率超限 / 风控 / 通用失败）而不是抛裸异常；`liveHeaders()` 复制全局 webHeaders 只换 Referer 为 `https://live.bilibili.com/`（绝不原地改共享表）。播放页 `PlayerActivity.kt:4431-4434` 直播分支转到 `sendLiveDanmaku`（`:4460-4528`），**成功后清空输入框 + 一行「弹幕已发送」轻提示**（对齐 PiliPlus 的 toast；原先打算完全静默靠 WS 回推自己的 `DANMU_MSG`，但弱网下那条可能延迟甚至丢）；本地 3 秒节流 + 按**码点**数 20 字（20 个 emoji 不被误判）。**未改清单、未加域名**（直播接口本来就走 https）。②**转发只有动态、没有私信（#133）**：`PrivateMsgApi.java:273-334` 新增 `buildVideoLink`/`buildVideoShareText`（标题超 60 字截断加 `…`）/`buildTextContent`（`JSONObject` 组装防引号注入）；新增 `activity/message/ChooseFriendActivity.kt`（继承 `RefreshListActivity` 白拿下拉刷新/空态/重试，`loadGeneration` 代次 token 丢弃旧响应）；`PrivateMsgSessionsAdapter.kt:48` 加可选 `onSessionClick`——**故意插在 `onSessionsChanged` 之前**，否则 `MessageActivity:143` 的尾随 lambda 会静默绑到新参数、会话页不再刷新；`VideoInfoFragment.kt:668-678` 转发点击改成 `TerminalDialog.menu` 两项（原「转发到动态」逻辑原样搬进 `relayToDynamic()`），发送失败回落「复制链接 + 提示」。**发文本不发卡片**：`private_msg.md:1811-1852` 写明只接受 `msg_type` 1/2/5，分享卡片是 7（码 21035），故降级为「标题 + 链接」文本（未实测，见 `bilibili-api-notes.md` §17）。
- **26.11.11 播放器底部新加按钮比同行按钮小一圈、还往上偏（用户截图报「新加的按钮和其他原有按钮不在同一列」）**：像素实测（622×139 截图，由按钮间距 66.3px 反推 1dp≈1.745px）发现坏的只有弹幕开关那一格——墨迹 45×41、中心 y=100，而同行 quality / sound_cut / sound_add / page_selector 都是 52×47 上下、中心 y≈108，播放圆盘 55×55、中心也是 108；换算下来坏的那个**内容框只有 ≈27dp 且顶对齐**（32×37 的格子里占 y 76..124，正常格是 76..141）。两个独立根因：①**影子与真身共用同一个 `Drawable`**——用户把弹幕开关也放进了底部左区，而它默认仍在右列，于是底部那份是真身、右列那份是影子；`syncMirrors` 每 400ms `mirror.setImageDrawable(primary.drawable)`，而 `ImageView.setImageDrawable` 内部按**接收方自己的**内容区写 `configureBounds()`，`Drawable` 一个实例只有一组 bounds/一个 callback，两份互相覆盖 → 底部那颗按右列 28dp 格（48×48px）的 bounds 落笔，又因为 `fitXY` 时没有绘制矩阵、直接从内容区左上角落笔，于是「小一圈 + 往上偏」。②**按钮池里两套 `scaleType`**——quality/sound_cut/sound_add/page_selector 是 `fitCenter`，弹幕/字幕/循环/连播/旋转/锁定那一批是 `fitXY`；底部格子 32×37 而图标是方的，fitXY 那批恒被竖向拉长 ≈1.19 倍，所以「新加的按钮」和同行的老按钮永远长得不一样。修法（`player/PlayerButtonSlots.kt`）：新增 `cloneDrawable()`（`constantState.newDrawable(resources)` 再 `mutate()`，位图本身仍共享）+ `mirrorDrawableSource` 记住影子当前图标的来源实例（400ms 定时器里只在实例变了才复制，不然每轮都造一个新 Drawable），`mirrorOf` 的图标与背景都改成复制一份、`syncOne` 复用同一逻辑；`cellParams` 的 ImageButton 分支统一 `scaleType = FIT_CENTER`（四区图标一律等比居中，`moreButton` 的显式 `FIT_XY` 一并删掉，`setPadding(0,0,0,0)` 仍必须留在 `cellParams` 之后）；`collapse` / `clearAll` 同步清 `mirrorDrawableSource` 防泄漏。**L0（26.11.11 本机实测）**：`:app:assembleDebug` BUILD SUCCESSFUL（首次因 Windows 文件锁删不掉 `app\build\tmp\kotlin-classes\standardDebug\com` 而失败，`gradlew --stop` 后重跑通过）；`:app:testStandardDebugUnitTest --rerun` **220 suites / 2385 tests / 0 失败 0 错误**。**未验证**：槽位渲染依赖真实 View、没有可加的 JVM 单测，只能真机看画面，交接单见 [`manual-test-guide-261111-player-slot-icon.md`](manual-test-guide-261111-player-slot-icon.md)（H25~H26）。
- **26.11.10 长按顶栏在「不适用」页面误震动（反馈 #153）**：`BaseActivity` / `VideoInfoActivity` 的长按监听器在模式不允许时靠**返回 true** 把手势吞掉（避免回落成一次单击退出页面），但 `View.performLongClick()` 只要回调返回 true 就无条件 `performHapticFeedback(LONG_PRESS)`——框架不区分「真执行了动作」还是「只是把长按吃掉」，于是「仅视频详情页 / 不响应」下普通页面长按什么都不做却震一下。修法：`BaseActivity` 新增 `syncTopbarLongPressHaptic()`，在每次 `onStart` 调用的 `setupTopbarLongPressToHome()` 里按本页判据（`handlesTopbarLongPress()` 决定走视频页还是普通页）同步 `topBar.isHapticFeedbackEnabled`；模式只能从设置页改回来、回来必走 `onStart`，与「按下时读模式」等效，动作判定仍走 `TopbarLongPressPrefs`。**L0（26.11.11 本机实测，worktree `outputs/wt-pr27` @ `941acb1a`，未注入 `private/`）**：`:app:assembleDebug` BUILD SUCCESSFUL in 3m 56s（两 flavor 各 4 个 APK）；`:app:testStandardDebugUnitTest` **191 suites / 2040 tests / 0 失败 0 错误**；合并提交 `a39cef97` 与 `941acb1a` 的 tree 完全相同（均 `657799f7`），故该结论对 main 直接成立；PR CI `build-and-test`（run 42852727267）另已 9m5s 通过。**未验证**：震动有无只能真机上手感受（本机无 Android 设备），且「仅视频详情页 / 不响应」两档下普通页与视频详情页各自的震动表现需逐档复核；交接单见 [`manual-test-guide-261111-topbar-longpress-haptic.md`](manual-test-guide-261111-topbar-longpress-haptic.md)（H18~H21）。
- **26.11.10 下载速度与进度改成按真实字节算（用户反馈「速度和进度显示的都非常不准确」，§3.146）**：三个根因一起修——①进度按**写死的阶段权重**摊派（附件 12% / 视频 40% / 音频 36% / 合并 12%），与真实字节无关，4K 视频流下完才 52%、音频下完却 88%；②速度是**全局一个**采样器、由通知栏 1 秒定时器推，喂的是整个批次的累计字节，于是列表每行显示同一个总速度；③那个全局计数器允许被减（失败分片回滚），采样撞上回滚就被 `coerceAtLeast(0)` 抹成 0。改法：新增 `service/download/DownloadSectionBytes.kt`（每条任务一个字节预算，进度 = 实际落盘字节 / 预算，`finishFile` 用实际字节、`finishAll` 在合并前顶到 100%、`monotoneProgress` 防倒退）与 `service/download/SpeedSampler.kt`（**每条任务一个**采样器，实测时间差做分母、500ms 最小窗口、0.4 EMA、2.5s 无新字节归零）；`DownloadService` 新增 `probeContentLength`（HEAD，失败退 `Range: bytes=0-0` 读 `Content-Range`，都失败返回 -1 回退阶段权重）与 `probeSectionBytes`，四个下载函数全改走 `reportFileProgress` / `finishFileProgress`，附件（封面 / 字幕 / 弹幕）不再把字节算进任务分子；`DownloadBatchStats.overallProgress` 改按字节，成功任务出队前由 `retireSection(id, true)` 把字节留给批次；UI 侧 `DownloadAdapter` 每行读自己的 `progressInfo.speedBps`、`DownloadListActivity` 用 `getTotalSpeedBps()` 且 ETA 直接用字节算（**删掉 `parseSpeedToBytes`**），`DownloadNotifier` 去掉每秒 `sampleSpeed()`。顺带删掉 `speedStr` 门面属性、三个全局字节 API 与 `dashMergeProgress` / `DASH_MERGE_END`。**L0（26.11.10 本机实测）**：`:app:assembleDebug` BUILD SUCCESSFUL（两 flavor）；两 flavor 单测各 **213 suites / 2280 tests / 0 失败 0 错误**（本 worktree 未注入 `private/`）。**未验证**：进度与「已下 / 总大小」是否吻合、并行时每行速度是否各不相同、总速度是否等于各行之和、ETA 是否收敛——本机无 Android 设备 / 模拟器，需真机看画面；交接单见 [`manual-test-guide-261110-download-speed-progress.md`](manual-test-guide-261110-download-speed-progress.md)（H13~H17）。
- **26.11.10 缓存文件夹页改独立 Activity（反馈 #20）**：缓存页原来把「当前文件夹」当同一个 Activity 里的状态（`adapter.currentFolderName`），右滑 / 返回退出文件夹只能就地 `adapter.setData(folderList, videoList, "")` 把整张表 `notifyDataSetChanged` 重建（封面走 `DiskCacheStrategy.NONE`，整屏重读），夹里视频一多就是用户说的「右滑退出时卡住」，而且文件夹没有自己的返回栈。现在文件夹是独立页面 `activity/video/local/LocalFolderActivity.kt`（只声明 `EXTRA_FOLDER_NAME`，实现全部复用 `LocalListActivity`），缓存页 `openFolder()` 用 `registerForActivityResult` 起页，退出 = `finish()`。**PR #26 审查后补的 4 条**：①切栏目收整条链——`LocalListActivity.finishLocalCacheChain(top)` 在顶层是文件夹页时连 `cachePageRef` 那层缓存页一起 `finish()`，`MenuActivity.killAndJump` 调它、不自己判断 `isFolderPage`；②文件夹页 `onDestroy` 归还「最近实例」前加 `getInstanceActivityOnTop() === this` 守卫，避免切栏目 / `recreate()` 时把新页面的登记覆盖成那张不可见的缓存页；③起夹页 Intent 透传缓存页的 `from`，菜单页名与 `killAndJump` 的 `name != from` 才正常；④置脏收敛到真正改数据的路径（夹内删除 / 移出 / 移动到文件夹，以及外部入口 `refreshAfterExternalChange()`：`DownloadService.refreshLocalList`、`LocalPageChooseActivity` 删分 P 后通知），下拉刷新 / `onResume` 换根目录不再置脏；顺带删掉「拆散文件夹」里不可达的夹内兜底分支，`label_activity_local_folder` 改名 `pagename_localfolder` 并挪进 `pagename_` 组。**已知取舍**：文件夹页 `onCreate` 走的是和缓存页同一套 `loadData()`，会扫描缓存根下所有目录、逐个读 `.video_meta.json`，进夹不再是旧实现的零扫描；夹 / 缓存越大等待越久。没有改成「只扫目标目录」是因为 `VideoFolder.videoTitles` 的标题与磁盘目录名可能不一致（下载侧清洗过），按标题拼路径会漏条目。**L0（26.11.10 本机实测，head `169a94a8` + 本改动）**：`:app:assembleDebug` BUILD SUCCESSFUL（两 flavor 8 个 debug APK）；`:app:testStandardDebugUnitTest` **188 suites / 2019 tests / 0 失败 0 错误**（本 worktree 未注入 `private/`）；coexist 单测由 PR CI `build-and-test` 复核。**未验证**：本机无 Android 设备 / 模拟器（`/dev/kvm` 不存在，SDK 下无 `emulator` 与 `system-images`），「右滑退出是否还卡」与文件夹页观感只能真机看；交接单见 [`manual-test-guide-261110-cache-folder-page.md`](manual-test-guide-261110-cache-folder-page.md)（H08~H12）。
- **26.11.09 下载列表多选删除（反馈 #18 第二项）**：下载列表（`activity/video/local/DownloadListActivity.kt`）原来只支持「长按两次删除」，一条一条删太慢，也没有确认框。现在长按任一条进入多选并勾中它，多选态下点条目/长按都是勾选或取消；顶部复用 `activity_simple_refresh` 的 `manageBar` 显示「退出多选 / 删除(n)」，删除前走 `TerminalDialog.confirm` 弹「将删除已下载文件（N 项），此操作无法恢复。」（居中确认框），确认后逐条暂停、删文件与记录，不影响其他并行任务；多选态下按返回只退出多选。`DownloadAdapter` 同步加上与缓存页/收藏夹一致的多选视觉（勾中保亮 + 标题「✓ 」前缀，未勾压暗），并关掉 item 内容变化动画，避免取消勾选后 alpha 被动画改回 1。**反馈 #18 第一项（收藏夹批量下载二次确认）在当前 main 上已存在**（`FavoriteVideoListActivity.confirmBatchDownload` → `TerminalDialog.confirm`，26.11.06 的 `8d4950f` 引入），本次未重复改动。删除前用新增的 `DownloadSection.peekPath()` 取目录（不 mkdirs），因此能判断文件原本在不在、正确报出「文件未能删除」。**L0 实测**：本机（Linux + Android SDK 34 + JDK 17）执行 `./gradlew :app:assembleDebug :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --no-parallel` → **BUILD SUCCESSFUL in 6m 1s**，两 flavor 各 **184 suites / 1991 tests / 0 失败 0 错误**；PR #25 的 CI `build-and-test` 另作复核。**未验证**：多选视觉、顶部条排版、确认框居中与删除后列表刷新需真机 L2 看画面；交接单见 `docs/review/manual-test-guide-261109-download-multiselect.md`（H05~H07）。
- **26.11.08 版本三通道改造（稳定 / 测试 / 实验）**：版本号从「手写 `versionCode` + `versionName` + `int betaVer`」改为**只写 `appVersionDate`，全部派生**——`versionCode = YYMMDD×100 + 渠道码×10 + 当日序号`（stable=0/beta=1/exp=2），`versionName` 为 `26.11.08` / `26.11.08.1` / `26.11.08-beta.1` / `26.11.08-exp.1`；命令行 `-Pchannel=stable|beta|exp -Pseq=N`，四项校验（日期格式、渠道白名单、seq 0~9、beta/exp 的 seq ≥1）。发版侧：tag 正则放宽到 `-(beta|exp).N`，`verify-version` 推出版本号后经 job env 传给构建，新增 aapt2 badging 复核，`prerelease` 改按渠道（非 stable 即预发布），实验版允许没有 `update_log_current`（只 `::warning::` + 占位日志）。客户端侧：新增 `util/UpdateChannel.kt`（tag→渠道、`accepts`、`bestOf` 纯函数）、`UpdateManager` 从「读 `/releases/latest`」改为「拉发行版列表筛本通道最大版本」（`/latest` 只当稳定版快路径）、设置 → 关于新增「更新通道」（**默认测试版**，装实验包的人默认实验版，显式选过的值优先；切换会清 `cachedConfig` / `forceUpdate_*` / `update_last_new_version`）、ping 与 `App-Info` 头新增 `channel` 字段（`VERSION_NAME_BASE` 上报不带后缀）。口径见 [`docs/version-channels.md`](../version-channels.md)。**未验证**：三通道界面文案与切换手感要真机看；`26.11.08-beta.1` 首次真实发版才能验证预发布不进 `releases/latest`。
- **26.11.07 本次更新日志 31 条**＝26.11.06 的 19 条**逐字保留**+ 12 条新增（播放器按钮槽位与七组设置、连播队列与继续播放、弹幕分段与磁盘缓存、评论三层兜底、片头片尾五档、推荐页游标去重、主题背景图、赞赏入口等）。按用户要求 **26.11.06 不单独进 `update_history_log`**（「06 版本就当不存在」），历史页顶部仍是「## 2026-11-02」。
- **26.11.06 本轮改动**：覆盖 26.11.02 之后的 28 个提交（§3.108~§3.118），分别是「我的订阅」（含订阅 / 取消订阅入口）、缓存页多选批量操作与拖动排序搜索、缓存合集按下载页序排列、下载期间不休眠 + 下载页常亮、热搜跑马灯、播放器滑动调进度灵敏度 / 跳到指定时间 / 锁定按钮开关、mcdn 直链 -10000 修复、自动跳过片头片尾只对 PGC 生效、番剧「播放来源」面板与换源播放接入弹弹play 弹幕（可选私有功能）、账号代际隔离与多账号导入导出、#88 关注分组长按、#85 评论「假发送成功」、#97 设置页搜索框随内容滚动、带图评论图床目录与大会员前置拦截。文档侧另补：App 开源信息页与 `readme.md` 致谢加入 Kazumi（选源交互 / 第三方弹幕接入思路，GPL v3，未包含其源码）。
- **上一版**：`26.11.02` / tag `26.11.02`（`versionCode 2611020`、`versionName 26.11.02`）；其 12 条日志已归档进 `update_history_log` 的「## 2026-11-02」组（本版首次把它补进历史页——26.11.02 发版时该组未写入）。
- **上一版（26.11.01）**：按用户要求把**播放器相关代码整体退回 26.10.27 发版状态**（§3.90）——撤掉自编译的 ijkplayer 补丁库（`.so` 回到上游预编译版，三 ABI 体积回落）、ExoPlayer 统一时钟内核与 DASH 双流方案，修上一版在真机上「安装包明显变大、视频完全播不出来」；同时合并 PR #18（§3.91 菜单退出按钮、§3.92 媒体控件归类、§3.93 圆屏列表实验效果、§3.94 封面请求复用）与 PR #19（§3.83 举报 / 弹幕消息迁入可选私有功能，公开侧只留门面与挂点）。明细见 `strings.xml` 的 `update_log_current`（5 条编号条目，另 1 条为版本锚点）。
- **上一轮改动（26.10.31.1）**：修复「倍速播放时听到两路重叠音频、其中一路跟不上」（§3.88 终版——给随包 ijkplayer 打内核补丁；该补丁随 26.11.01 的回退一并撤掉）、修掉扫码登录停轮询后的失败提示外露英文异常原文（§3.86）。
- **上一轮改动（26.10.31）**：清晰度按账号权限锁定 + 4K 接入（§4 六十）、播放页内切 1080P（§4 五十九）、私信 2 分钟内撤回、内置选图页、图片加载提速、弥散光晕可选光源位置（§3.76）、系统时间错误防呆（§3.72）、账密 / 短信登录补存 App access_token（§3.87），以及四类修复（工具页跳转杀下层、短视频弹幕不跟设置、空关键词搜索藏历史、倍速两路重叠音频）。另有**一轮私有化整理**：评论举报与弹幕类消息整体迁入可选私有功能，公开仓库只留门面与挂点（§3.83）。明细见 `strings.xml` 的 `update_log_current`（10 条编号条目，另 1 条为版本锚点）。
- **26.10.31 发版时工作区已清空**：26.10.27 之后累积的未发版改动（§3.72~§3.85）与发版前补的登录令牌改动（§3.87）都随本版一起出去了。
- **26.11.01 发版时工作区已清空**：§3.91~§3.94（PR #18）与 §3.83 的私有化整理（PR #19）都随本版一起发布；26.11.01 之后新的待发版改动从这里往下记。
- **PR #20 已合入（26.11.06 发版前）**：①长按二级菜单弹窗与两个伪装页补圆屏适配（§3.113）；②「虚拟合集」起播索引修复（§3.114）；③菜单「退出」改为可排序、可关闭（§3.115）；④搜索设置「视频搜索（始终启用）」显示修正（§3.117）；⑤关于页开发者列表新增 Kuinone（§3.118）。来源 PR #20（作者 Kuinone）；合入时剔除了误提交的 16 个 build-*.log 与 .github/workflows/auto_pr_kuinone.yml。
- **26.11.05 已随 26.11.06 发版**：合并远程 `main` 后没解干净的冲突（资源重名 + `BaseActivity` 残留旧边距算术，§3.116）、搜索设置「视频搜索（始终启用）」显示成锁定关（§3.117）、关于页开发者列表新增 Kuinone（§3.118）。版本号未动（仍是 `26.11.01` / `2611010`）。
- **26.11.07 之后待发版的改动**：表冠三处加固（反馈 #103/#107）——新增纯函数 `ui/widget/RotaryEventPolicy.kt`（13 例 JVM 单测，26.11.07.1 又加 5 例共 18 例）+ 改 `ui/widget/RotaryEncoderSupport.kt`（位移为 0 不再吞事件、滚动量回落 `AXIS_VSCROLL`、兜底找目标要求与窗口有实际交集、加 W 级诊断日志）。两个 flavor 单测 **189 suites / 2067 tests / 0 失败**（26.11.07 实测）；真机验证项见 §2.2 同名待办。
- **26.11.07.1 表冠二次修复（issue #21「wearos5.0安卓14系统表冠滚动失效」，Galaxy Watch 7 / OneUI 6.0 / Wear OS 5，26.11.07 之后新开）**：改成**系统优先、App 补位**——总开关关闭（默认态）时控件先跑 `super`，用滚动偏移有没有变判断框架是否真滚了，滚了就吞事件（不叠加）、没滚才按 `pixelDelta` 补位（每个界面都能转）；总开关打开仍走老的「App 先接管」（留给 #20 这类要自定义灵敏度 / 系统滚动偏慢的用户）。根因是 `RecyclerView.onGenericMotionEvent` 滚完恒 `return false`（`androidx.recyclerview 1.3.2`，`:3985`），事件继续上抛导致 DecorView 兜底再滚一次；`NestedScrollView` 滚完 `return true`（`:1363`），所以只有视频详情 / 评论区「看起来正常」。改动落点：`ui/widget/RotaryEventPolicy.kt`（新增 `shouldConsumeAfterFramework` / `shouldFillIn` 两个纯函数 + 5 例单测）、`ui/widget/RotaryEncoderSupport.kt`（新增 `handleSystemFirst` / `sensitivityOf` / `isSystemFirstMode`，`handlePage` 不再受总开关限制）、三个 `Rotary*` 控件改覆写 `onGenericMotionEvent`、`activity/base/BaseActivity.kt` 兜底 KDoc、`strings.xml:54` 开关文案。原理见 `docs/architecture-map.md` §3.2「第四个坑」。L0 已过：两 flavor `assembleDebug` + 单测 **191 suites / 2105 tests / 0 失败 0 错误**（26.11.07.1 实测，`RotaryEventPolicyTest` 18 例）。真机验证项见 §2.2 同名待办。
- **26.11.07 另加一个启动期静默预热挂点（公开侧只有门面 + 挂点）**：`util/OptionalFeatures.warmUpOptionalData()`（新增、无参、异常吞掉）+ `BiliTerminal.onCreate` 的 `if (context == null)` 块内、`CookieRefreshScheduler.start()` 之后调一次。私有功能不在场时是纯空操作，公开构建行为不变；具体预热什么由私有侧自己决定（本次是第三方片源规则的静默检查/同步，实现与实测记录写在私有仓库 README，不进公开文档）。
- **26.11.07.1 补丁（`versionCode 2611071`、`versionName` 仍 `26.11.07`）**：在 tag `26.11.02` → `26.11.07.1` 区间做了一轮全量 code review（Standards + Spec 双轴），修复随本补丁一起出去。**必须升 `versionCode`**：`UpdateManager` 是严格大于比较，与 26.11.07 首包尾号相同时老用户收不到修正包（先例 `1c404c3` 只升尾号、`versionName` 不动）。
- **26.11.07.1 审查修复清单（按域）**：①存储页三个清理按钮的确认对话框补 `.show()`（此前点了没反应，按钮全是死的）；②播放器七组设置、播放器界面页、关于页开发者简介、本地列表批量操作对话框的文案全部资源化（`strings.xml` 新增 `setting_player_*` / `about_dev_*_desc` / `uitext_locallistactivity_036..046` 等，`SettingPlayerUiActivity` 选按钮改用 `@StringRes` 而不是拿中文当键）；③清死键 9 个（`text_activityplayer_10`、`setting_player_ui_{add_slot,group_members_desc,add_group_member,slot_hint}`、`setting_player_lock_button_{name,desc}`、`player_queue_resume_desc`），`strings.xml` 里 12 行「裸 `#` 当注释」改成 XML 注释，`update_log_current` 删掉宣传已删功能「显示锁定按钮」的条目并重排编号；④`player/DanmakuStreamLoader` 加代际号 `generation`：`attach()` 之后在飞的旧 worker 不再把上一条视频的分段塞进新 manager，也不会两个 worker 抢带宽；⑤`api/SubscriptionApi.getSeasonFavState` 请求失败不再短路「翻我的订阅」兜底，删掉语义相反的 `parseSeasonFavState`；⑥`util/AccountManager.applyCredentials` 无条件比对 `DedeUserID`（不再「Cookie 里没有就放行」），`util/SpecialLoginParser` 把 JSON `null` 归一成空（org.json 的 `optString` 会给字符串 `"null"`）；⑦`api/ReplyTranslateApi` 的 gRPC 请求改用 `NetWorkUtil.newTaggedRequestBuilder()`，跟其它请求一样带账号代际标签；⑧`util/OptionalFeatures` 不再反向依赖 `activity/player/PlayerActivity`，改成 `ResolveHintHost` 接口由播放页实现；⑨`player/PlayerButtonSlots` 的影子按钮阴影照抄真身（原先写死 3f/2f/黑）；左列对齐改到窗口坐标并随布局重算（原先跨 parent 比 raw `top`，列高可变就错位）；⑩`ui/appearance/ColorScheme` 的光晕与背景图统一按夜间位判亮暗（原先光晕读偏好，闪屏/播放器强制暗色时两者不一致）；⑪`util/FavoriteAggregatePolicy` 的「来自 N 个收藏夹」不再把读取失败的页算进去；⑫弹幕缓存上限键登记进 `util/SettingsKeys`（`PLAYER_DANMAKU_CACHE_MAX_MB`）；⑬修 8 条恒真 / 自比 / 只断 size 的假通过单测；⑭账号切换页删除失败不再提示「当前登录的账号不能删除」，该页对话框文案也搬进资源。
- **本轮判定为「不改」或「非问题」的审查项（第二批修完后更新的口径）**：`SettingsIndex.kt` 里约 60 条 Entry 标题/描述硬编码中文——**已在第二批修掉**（见上条 ⑯，345 处字面量归零）；`SettingsKeys` 里 5 个已停用的「显示××按钮」旧键**故意保留**（删了会连带删掉用户存档，KDoc 已写明不读不写不迁移）；`strings.xml` 里成批的空 `<item></item>` 是更新日志的日期分组间隔符，不是脏数据；`api/SubscriptionApi` 里三段重复的请求骨架属结构问题，重构成本高于收益（**订阅错误码 `when` 的四份重复已在第三批修掉**）；`strings.xml` 里仍有 110 组「新旧键同文案」（`settingtext_*` 旧方案与语义新键并存），第三批只合掉审查点名的 6 组和本批自己引入的 8 组，成批统一另开一批；`model/VideoInfo.java:138`（`toPlayerData`）与 `model/Bangumi.java:122`、`model/DownloadSection.java:100` 直接读 `SharedPreferencesUtil`（登录 mid / 起播清晰度）属分层不纯，修它要把这两个值从 17 处调用点一路透传，收益低于改动面，另开一批；`util/UiText.MAP` 之外还有 86 条 `UiText.of/fmt` 字面量未登记（`CatchActivity`、`JumpToPlayerActivity`、`ReplyAdapter`、`tutorial/Tutorials.kt`、`ui/appearance/BackgroundImage.kt` 等），属「整文件没走过资源化」的历史基线，一次登记要新增 80+ 键并改动大量 UI 代码，同样另开一批。
- **26.11.07.1 审查修复清单（第二批）**：⑮`app/build.gradle` 关掉 `testOptions.unitTests.returnDefaultValues`（它让 JVM 单测里对 Android API 的误用静默返回默认值，问题只能等真机暴露），前置条件是 `util/Logu` 改成 JVM 安全（统一走 `cat()`：先试 `Log.println`，被桩实现抛 `RuntimeException` 时回落 `System.out.println`；堆栈串失败回落 `StringWriter` + `PrintWriter`）；⑯`activity/settings/SettingsIndex.kt` 345 处中文字面量全部资源化（新键 `setting_index_*` 14 个，其余 223 条去重文案复用既有键，优先 `SettingText.MAP`），`activity/settings/SettingGroupActivity.kt` 的「颜色模式 / 弥散光晕 / 玻璃质感 / 圆屏列表」四处标题改用同一批新键（原先一边写死一边走资源，改字就静默失配）；⑰写死色值语义化：新增 `player_icon_on_scrim` / `vector_icon_mask` / `image_viewer_hint_text` / `card_scrim` 四个色键，替换 8 个 drawable 的白色、`activity_setting_player_ui.xml` 的 `#66000000`、`BigImageView` 的 `0xCCFFFFFF`（`icon_help.xml` 的路径 fill 也必须改成引用，否则 `tools/appearance/gen_light_mode.py` 会生成「白 tint + 深填充」的亮色版，SRC_IN 下白上白不可见）；⑱`ui/appearance/AmbientGlow.kt` 两份几乎相同的私有 Drawable 合并成一个（`bottom: Int?` 区分窗口背景 / 背景图叠加层），删约 70 行重复实现，对外 `createDrawable` / `createOverlayDrawable` 签名不变；⑲播放器影子同步按 `PlayerButtonSlots.hasMirrors()` 门控，没有重复按钮时不再每 400ms 空转一遍全部槽位；⑳特殊登录页导出为空时提示写回说明栏，不再塞进可编辑可复制的输入框（用户会把提示当数据复制走）；㉑CI `verify-version` 增加「`versionCode` 末位 == tag 的 `.N` 后缀」校验（26.11.07.1 事故的直接封堵，先例 `1c404c3` 把 26.11.06.1 补成 `2611061`），注入私有源码时缺 `KAZUMI_*` / `DANDANAPI_*` 直接 `::error::` 失败（原先只 echo 一条 notice 照样出包）。
- **背景图「选择背景图」改走内置选图页（26.11.07，用户报「选择背景时调用系统相册」）**：`activity/settings/SettingGroupActivity.kt` 的 `pickBackgroundImage()` 此前仍直接拉 `ACTION_GET_CONTENT`（反馈 #53 统一改造时漏掉这一处，手表上会落到文件管理器、要从存储根目录一级级点进 DCIM / Pictures），现改为 `backgroundPickerLauncher.launch(ImagePickerActivity.intent(this))`，与私信发图 / 带图评论 / 发动态配图 / 换头像统一；结果格式不变（`RESULT_OK` + `data.data`），`ui/appearance/BackgroundImage.install` 一行未动。文案 `appearance_background_no_picker` 由「没有可用的文件管理器」改为「无法打开选图页」。L0：两 flavor `assembleDebug` + 单测 **191 suites / 2105 tests / 0 失败 0 错误**（26.11.07 实测）。L1（AVD `Medium_Phone_API_37.0`，standard debug 包，`DebugBridge` 进程内拉起外观设置页）：点「选择背景图」→ `dumpsys activity activities` 栈里出现 `ImagePickerActivity`（`resultTo` 指向 `SettingGroupActivity`），页首「全部图片 / 系统选择器」+ 三列缩略图；点第 1 张 → 回到设置页该行变成「当前：已设置」，`adb shell run-as … ls -l files/appearance/` 得到 `background.img` 1,199,035 字节。手表端未验项见 §2.1。
- **API 19（Android 4.4）启动崩溃修复：`<shape>` 里的主题属性颜色**（26.11.08，用户报「26.11.07.1 应用崩了」）：栈是 `SplashActivity.onCreate` → `Resources$NotFoundException: File res/uU.xml from drawable resource ID #0x7f0800a2`，内层 `UnsupportedOperationException: Can't convert to color: type=0x2`（`GradientDrawable.inflate` → `TypedArray.getColor`）。`aapt2 dump resources` 把 `0x7f0800a2` 解回 **`drawable/dialog_background`** ⇒ 触发点是启动时隐私同意弹窗给 `TerminalDialog` 换背景（`util/TerminalDialog.kt:354` 的 `AppCompatResources.getDrawable(..., R.drawable.dialog_background)`）：API<21 的 `GradientDrawable` 不支持 `?attr`，`type=0x2`（TYPE_ATTRIBUTE）直接抛。同源还有两处：`drawable/background_card.xml`（播放页卡片）、`drawable/zoom_btn_bg.xml`（验证码页缩放按钮）。按仓库既有惯例改成 `drawable/` 放具体颜色（`@color/surface_dark` / `@color/on_surface_bili` / `@color/pink_brand`，随 `values` / `values-notnight` 自动亮暗）+ 新增 `drawable-v21/` 三个保留 `?attr` 的版本。**该栈的行号与本仓源码对不上**：`SplashActivity.onCreate(:150)` / `SplashActivity.b(:70)` / `com.bumptech.glide.c.z(:16)` 都对不上 tag `26.11.07.1` 的 `SplashActivity.kt`（469 行，`onCreate` 在 83 行、全文无 Glide），全仓历史里也没在启动页用过 Glide ⇒ 用户那版包疑似被重打包/改过；但 `res/uU.xml` 与资源 ID 确属本仓 `dialog_background`，所以这条修复照样要出。L0 已过：两 flavor `assembleDebug` 成功，`aapt2 dump resources` / `xmltree` 确认三处都有 `()` 与 `(v21)` 两份、base 版是具体色值不是 `?`。**未验证**：API 19 真机（本机只有 Android 16 的 `10AC9S2M4L000QL`，AVD 里没有 API19 镜像）——需人拿一台 4.4 设备确认启动不再崩、弹窗背景与播放页卡片在夜间/亮色两套主题下都对。
- **关注列表切「分组」后互关徽章消失（26.11.08，用户报「切成「分组」后，互关用户头像旁的徽章消失了」）**：
  - **根因（分组明细接口问不出关系）**：`GET /x/relation/tag`（分组明细）返回的 `attribute` **恒为 0**（`special` 同样恒 0、`tag` 恒 null，`bilibili-API` 的 `docs/user/relation.md` 字段表已注明），而 `api/FollowApi.getFollowTagUsers` 原来按 `isMutualFollow(true, attribute)` 判互关，等于永远判 false ⇒ 分组模式下一个徽章都不亮。普通关注 / 粉丝列表走 `/x/relation/followings|followers` 的 `attribute == 6`，是好的，所以只在分组模式坏、看起来像偶发。展示侧没问题：`adapter/user/FollowGroupAdapter.kt:281` 每次 bind 都按 `user.mutualFollow` 重置可见性，未改动。
  - **修法**：新增 `FollowApi.fillMutualFollow` / `relationBatchUrl` / `applyRelationAttributes`（`RELATION_BATCH_SIZE = 50`）——分组明细拿到一页成员后回问 `GET /x/relation/relations?fids=<逗号分隔 mid>`（`data` 是「mid → `{attribute, special, …}`」对象），只写 `mutualFollow`（`attribute == 6`），**不碰 `specialFollow`**（「特别关注」本身就是一个分组，分组视图里再标一遍是重复）；请求失败只 `Log.w`，不打断分组加载（徽章是装饰信息，多出来的一个请求不该让整页失败）。`getFollowTagUsers` 改为 `userList.addAll(pageUsers)` 后再补，返回码语义不变。
  - **单测**：新增 `api/FollowGroupMutualFollowTest` 8 例（`fids` 逗号拼法跳过占位 mid / 空安全 / 只认 `attribute == 6`（`2` 单关、`0` 无关系、`128` 拉黑都不亮）/ 未回条目保持不亮 / 不误动 `specialFollow` / 缺 `data` / 跳过非数字键与非对象值）。
  - **L0（26.11.08 实测，本机工作区含他人未提交改动）**：`:app:testStandardDebugUnitTest` + `:app:testCoexistDebugUnitTest` + `:app:assembleDebug` 全部 BUILD SUCCESSFUL，两 flavor 各 **206 suites / 2229 tests / 0 失败 0 错误**。
  - **接口结论**已进 `docs/bilibili-api-notes.md` §11（分组明细 `attribute` 恒 0 这一条），功能说明同步进 `docs/FEATURES.md` §7.7。
  - **未验证**：真机看画面——需登录态 + 自己有关注分组，展开分组后确认互关成员的粉色「互关」徽章恢复、非互关成员仍不亮（L2，判断依据是画面）。
- **弹窗文字按钮在 API 21/22 上整块变纯红（26.11.09，用户报「有些设备的这一类按钮都出现了红色显示问题」）**：
  - **根因**：Material 文字按钮（`Widget.Material3.Button.TextButton` 与 M2 的 `Widget.MaterialComponents.Button.TextButton`）把文字色 / 底色写成「**颜色状态列表里引用主题属性**」——`res/color/m3_text_button_foreground_color_selector.xml`、`m3_text_button_background_color_selector.xml`（M3）与 `mtrl_text_btn_text_color_selector.xml`、`mtrl_btn_text_btn_bg_color_selector.xml`（M2）的每个 item 都是 `?attr/colorPrimary` / `?attr/colorOnContainer` 这类引用。而 **颜色状态列表里的主题属性引用要 API 23 才解析得了**：API 21/22 取不到值，退回框架内部哨兵 `ColorStateList.DEFAULT_COLOR = Color.RED`（`android/content/res/ColorStateList.java:151`）⇒ 纯红实心底 + 同色文字（看不见），与报障截图「红块里一个字都没有」完全吻合。
  - **定位证据**（报障设备 ZTE 手表 Android 5.1 / API 22，用临时诊断包读屏上的数字）：主题属性本身都正常——`primary=#FFFF6699 onPrimary=#FFFFFFFF`、`容器=#00000000 容字=#FFFF6699`；塌的只有状态列表这条路——`按钮1 字=#FFFF0000 实画=#FFFF0000 着色=#FFFF0000`，而同一屏「对照」按钮（代码里直接给单色 + 透明底）`字=#FFFF6699 着色=#00000000` 正常，诊断行 `命中 #FF0000（框架哨兵红）`。API 37 模拟器上同一份代码取到 `#FFFF6699` 粉字，所以本地复现不出来——与机型 / 主题 / 玻璃开关都无关，纯老框架限制。
  - **修法**：新增 `util/view/TextButtonColors.apply(vararg MaterialButton)`——按当前主题解析一次 `?attr/colorPrimary`，写成单色列表（透明底 + 主色文字 + 主色 `0x40` 透明水波纹），绕开状态列表；颜色仍只读 `?attr/`，不写死色值。套用到全仓**仅有的 4 处** TextButton 布局、共 8 个按钮：`activity_input_dialog.xml`（新建 / 重命名文件夹）、`activity_confirm_dialog.xml`（确认弹窗）、`activity_image_picker.xml`（相册 / 系统相册）、`activity_send_dynamic.xml`（加选项 / 删投票），调用点分别在 `InputDialogActivity` / `ConfirmDialogActivity` / `ImagePickerActivity` / `SendDynamicActivity` 的 `onCreate` 里。
  - **L0（26.11.09 实测）**：两 flavor `assembleDebug` + `:app:testStandardDebugUnitTest` + `:app:testCoexistDebugUnitTest` 全部 BUILD SUCCESSFUL。
  - **未验证**：老框架真机（本机 `rebili_api23_sd` AVD 起不来、也没有 API 22 镜像）——需人拿那台 Android 5.1 设备确认按钮恢复「透明底 + 粉字」、按下有水波纹。
  - 坑点口径已记入 `docs/agents/dev-conventions.md`「暗色 / 亮色模式」一节。
- **播放器底部播放 / 暂停键变小（26.11.09.2，用户报「为啥播放器暂停按钮变小了（参考11.02版本）改回来」）**：
  - **根因是布局里漏删的内边距，不是格子尺寸**：`app/src/main/res/layout/activity_player.xml` 的 `@+id/button_video` 在 tag `26.11.02` 里是 `32dp × 37dp`、**无 padding**、`scaleType="fitCenter"`（`btn_player_play.xml` / `btn_player_pause.xml` 都是 30dp 见方的 vector，fitCenter 在 32×37 里画成约 32×32，圆形底盘直径 ≈ 32dp）。`6591f053`（26.11.07「播放器热区放大」）把格子放大到 44×44 并加了 `paddingStart/End=6dp`、`paddingTop/Bottom=3dp`，让图标仍占 32×38；`e4c4c380`（26.11.08「宽屏布局回退 11.02 尺寸」）把格子退回 32×37，**却漏删那四行 padding** ⇒ 内容区只剩 20×31dp，图标被 fitCenter 缩成约 20×20，底盘从 32dp 掉到 20dp。
  - **为什么只有它坏**：底部 / 侧列的其它按钮由 `player/PlayerButtonSlots.cellParams`（`PlayerButtonSlots.kt:353-385`）按「格子尺寸 − 图标尺寸」在代码里写 padding，底部区 `PlayerActivity.initButtonSlots()`（`PlayerActivity.kt:1311-1335`）传的是 `32f,37f,32f,37f` ⇒ padding 0；只有这个布局里写死的播放键带着 26.11.07 的残留。图标本身没变——`btn_control = findViewById(R.id.button_video)`（`PlayerActivity.kt:1239`）只是 `setImageResource(R.drawable.btn_player_pause / btn_player_play)` 换图（`PlayerActivity.kt:3686`、`:3707` 等），两个 vector 尺寸一致。短视频页走另一份布局 `activity_short_video_player.xml`（无 `button_video`），本来就没这个问题。
  - **修法**：删掉那四行 padding，保留 32×37dp 与 `layout_marginStart/End=3dp`、`fitCenter`；把上方注释改成「26.11.07 加、26.11.08 漏删，别再往里加内边距，要放大热区就连格子一起改」。
  - **L0（26.11.09 实测）**：`:app:assembleCoexistDebug` 与 `:app:assembleRelease` 均 BUILD SUCCESSFUL；共存 debug 包已装到用户测试机（vivo PD2229 / Android 16）。
  - **未验证**：真机看画面（L2）——需人确认底部播放 / 暂停键的圆形底盘恢复成与 11.02 一样大、按下仍有水波纹，且宽屏布局没有被这批内边距撑动。
- **26.11.09 一批修复（控制台当日反馈 + 崩溃，逐条细节见 §3.142–§3.145）**：
  - **崩溃 `Only fullscreen activities can request orientation`（26.11.09 新增形态，华为 LEO-DLXXU / Android 8.0 连崩 8 次）**：`BaseActivity.onCreate` 那句 `setRequestedOrientation` 没有任何 catch，Android 8.0 起非全屏窗口调用即抛 → 包 `try/catch (IllegalStateException)`，拿不到方向就按系统方向显示（§3.142）。
  - **反馈 #136「下载错一次就再也下不动」**：失败任务留下的 `state="error"` 记录 + 残留 `.DOWNLOADING` 把重试入口堵死，且重试只会打同一条线路 → 三处一起改：按 playurl `backupUrl`/`backup_url` 逐条换线重试、`startDownload` 命中 error 记录时删掉重排、进下载列表时清理历史失败残留（§3.143）。
  - **反馈 #132「关掉表冠适配后详情页只有 1/3 速」**：系统优先模式只吞事件不补差额，而 NestedScrollView 的滚动基准只有 48dp×2 → `topUpDelta` 补足差额，**supersede 26.11.07.1 表冠二次修复里的 `shouldFillIn`**（§3.144）。
  - **Glide `Cannot recycle a resource while it is still acquired`（26.11.02 起累计 51 条）**：成因是 Glide 4.16 上游竞态，本仓库只能降噪——去掉三处「无条件 clearMemory / 反复 clear」，Debug 包开 Glide VERBOSE 日志便于取证；**修没修好只能看线上条数**（§3.145）。

- **API<21（Android 4.4 及以下）打开就崩：布局里 `android:src` 直引矢量图（26.11.09，用户报「安装最新版本后打开应用通过教程后就崩了」）**：
  - **症状与白屏点**：栈是 `InflateException: Binary XML file line #115: Error inflating class ImageView` → `Resources$NotFoundException: File res/N2.xml from drawable resource ID #0x7f0800dd` → `XmlPullParserException: Binary XML file line #12: invalid drawable tag vector`，`ImageView.<init>(:129)` ← `AppCompatImageView` ← `QRLoginFragment.onCreateView`。用本地 release `mapping.txt` 反混淆确认 `Q0.r` = `QRLoginFragment`、`h.D` = `AppCompatDelegateImpl`、`o.y` = `AppCompatImageView`、`x0.g` = `ViewPager`；`line #115` 对上 `fragment_qr_login.xml` 里 `@+id/helpIcon` 的 `<ImageView android:src="@drawable/icon_help">` ⇒ **教程走完进扫码登录页，ViewPager 一 measure 就崩**（所以是「过完教程就崩」，不是启动崩）。
  - **根因**：`app/build.gradle:83-85` 的 `vectorDrawables { useSupportLibrary true }` 让 AAPT **不为 API<21 生成 PNG 兜底**（关掉它又会因为 `drawable/` 与 `drawable-v21/` 同名双份撞 `Duplicate resources`，试过，编不过），所以 `<vector>` 只能靠 AppCompat 的 `VectorDrawableCompat` 解析。而 AppCompat 只接管 `app:srcCompat` / `app:drawable*Compat` 这些 Compat 属性，**`android:` 命名空间的 `src` / `drawableStart` / `drawableEnd` / `background` / `thumb` 与 selector 里的 `android:drawable` 仍由控件构造函数原生加载** ⇒ pre-21 一律抛 `Resources$NotFoundException`。`docs/architecture-map.md` §1.1 原先写「矢量只出现在 `android:src` / `android:drawableStart`，pre-21 由 AppCompat 接管」——这句是错的，错的正是「AppCompat 会接管 `android:` 属性」这个前提，已改写。
  - **波及面（一次扫干净，不是只修登录页）**：全库 76 个纯 `<vector>` + 1 个引用矢量的 selector（`drawable/player_selector_progress.xml`）；`android:` 直引矢量的属性共 **96 处**（80 `android:src` + 9 `drawableStart` + 5 `drawableEnd` + 2 `android:thumb`），其中 `activity_creative_center.xml` 的 9 个是 **TextView 上的 `android:src`（属性根本不存在，纯无效残留，直接删）**。另外 `activity_player.xml` 由**原生 `Activity`** 承载（`PlayerActivity : Activity()` + `android:Theme.Black`，没有 AppCompat 控件工厂），`app:srcCompat` 在那里会被静默忽略，所以不能只换成 Compat 属性。
  - **修法**：①布局 85 处改 `app:srcCompat` / `app:drawableStartCompat` / `app:drawableEndCompat`（含本次崩溃点 `fragment_qr_login.xml`），9 个无效 `android:src` 与 2 个 `HighEnergyProgressBar` 的 `android:thumb` 删掉；②`activity_player.xml` 的 17 个 `<ImageButton>` + 1 个 `<ImageView>` 换成新增控件 `ui/widget/VectorImageView.kt`（`VectorImageView` / `VectorImageButton`：自己读 `app:srcCompat`，并把 `setImageResource` 覆写成 `AppCompatResources.getDrawable`）；`HighEnergyProgressBar.init` 里按 SDK 给 `thumb` 赋值（≥21 用 selector，<21 手工 `StateListDrawable`）；③代码侧统一 `AppCompatResources.getDrawable`：`util/StringUtil.java` 的 `getDrawable`（等级徽章 / 动态 / 收藏夹共用入口）、`ReplyAdapter`、`DynamicHolder` 三处 `ContextCompat` 都改走它，6 处 `setCompoundDrawables*WithIntrinsicBounds(int…)` 改 `TextViewCompat.setCompoundDrawables*WithIntrinsicBounds(view, drawable…)`，`ui/appearance/ThemeTint.setInactiveIcon` 改用 `setImageDrawable`；④`ui/widget/MarqueeTextView` 基类由原生 `TextView` 换成 `AppCompatTextView`（否则它的 `app:drawableStartCompat` 同样被静默忽略，图标直接不见）。
  - **单测（防回归）**：新增 `app/src/test/java/com/RobinNotBad/BiliClient/ui/VectorDrawableCompatTest.kt`——纯 JVM 扫 `src/main/res`，递归收集「含矢量」的 drawable（含复合），凡 `layout/` 或 `drawable*/` 里 android 命名空间的 `src` / `background` / `drawable*` / `thumb` / `progressDrawable` / `indeterminateDrawable` 指向它们就失败；白名单只留 `drawable/player_selector_progress.xml`（只在 API≥21 加载），另有 `allowListEntriesStillExist` 防白名单腐烂。**已实测会红**：把一处 `app:drawableStartCompat` 改回 `android:drawableStart` → `nativeNamespaceDoesNotLoadVectorDrawables FAILED`。同时 `ui/a11y/LayoutAccessibilityTest.kt` 的图片标签识别从「裸类名」改成「按最后一段类名」，把 `VectorImageView` / `VectorImageButton` 纳入无障碍检查（18 个按钮否则会静默逃过），也已实测会红。
  - **L0（26.11.09 实测）**：`:app:assembleDebug`（standard + coexist）BUILD SUCCESSFUL；两 flavor 单测各 **210 suites / 2259 tests / 0 失败 0 错误**。
  - **未验证**：API 19/20 真机（本机没有 API≤23 的镜像，也从没在 4.4 上跑过）——交接单见 `docs/review/manual-test-guide-261109-vector.md`（5 条），需人拿一台 4.4 设备确认：过完教程进扫码登录页不再崩、帮助图标正常显示；播放页 18 个按钮图标都在（原生 Activity 路径）、高能进度条滑块显示正常；各页 TextView 左侧 / 右侧图标齐全（`MarqueeTextView` 换基类后）；暗色 / 亮色两套主题下图标颜色都对。
  - 坑点口径已记入 `docs/architecture-map.md` §1.1 与 `docs/agents/dev-conventions.md`。
- **26.11.07.1 审查修复清单（第三批，纯去重，无用户可见变化）**：㉒订阅类错误码文案收口到 `util/SubscriptionErrorText.kt` 的 `Context.subscriptionErrorText(code)`——此前收藏夹页 / 合集页 / 订阅列表页 / 番剧详情页各抄一份**逐字相同**的 `when`，服务端加一个码就要改四处、漏一处就有一边显示「操作失败（xxxxx）」（为什么是 `Context` 扩展而不是纯函数：这几句必须走资源，而 JVM 单测拿不到 `Context`，取舍口径同 `api/ApiErrorText.kt`）；㉓合掉审查点名的 6 组重复文案：`text_subscription_type_folder` → `uitext_favoritevideolistactivity_011`（「收藏夹」）、`msg_sub_unsubscribed` → `msg_subscription_cancelled`（「已取消订阅」）、`text_sub_toggle_subscribed` → `msg_sub_subscribed`（「已订阅」，开关按钮与提示语用同一份）、`uitext_favoritevideolistactivity_025` → `uitext_locallistactivity_034`、`uitext_favoritevideolistactivity_023` → `uitext_locallistactivity_035`（「虚拟合集」两串以本地列表为源，`util/UiText.MAP` 的映射同步改指）、`social_history_cancel` → `btn_cancel`；㉔清掉第二批自己引入的 7 组重复：关于页开发者简介（Robin / moye / silent / dudu / 达达 / huanli / Jank）改为复用既有 `settingtext_aboutactivity_*`，只保留 Kuinone、紫色空灵、千awa 三条真正新增的描述键；本批净删死键 13 个。
- **26.11.07 设置页新增「番剧设置」分组挂点（公开侧只有门面 + 挂点）**：`activity/settings/SettingGroupActivity.kt` 加文件级 `const val GROUP_BANGUMI = "bangumi"` 与 `buildBangumiGroup()`（两行 `nav`：管理源 / 管理弹幕），`SettingMainActivity.buildGroups()` 在「内容与浏览」之后插一张 `icon_bangumi` 卡片，`SettingsIndex.build()` 加三条带 `highlight` 的搜索条目（分组 + 两个子页），`strings.xml` 新增 6 条 `setting_bangumi_*`；门面 `util/OptionalFeatures.kt` 加 `thirdPartySettingsAvailable` / `openThirdPartySourceSettings(context)` / `openThirdPartyDanmakuSettings(context)`（反射 `FeatureHolder` 同名方法，异常只 `Logu.w`）。私有功能不在场时整组（主设置页卡片 + 分组页两行 + 三条搜索条目）都不出现，**公开构建行为不变**；两个设置页的实现与视觉重排、实测记录都在私有仓库 README，不进公开文档。

- **26.11.07 调研清单第 1/2/3/4/6/7/10 条已落地（本机 `main` 已提交；本环境无 GitHub 凭据，未 push）**：
  - ①弹幕下载收口成 `util/DanmakuFileFetcher`：非 2xx / 空正文一律抛 `IOException`，给 `PlayerActivity.downdanmuOld` 与 `ShortVideoPlayerActivity.loadDanmakuFromXml` 补上原先漏掉的状态码校验（404/412 错误页不再落盘），原 4 份副本合一，`DanmakuFileFetcherTest` 5 例。
  - ②Opus 段落富文本改走纯解析器 `model/OpusRichTextParser` + `model/RichTextSegment`：段落里的 AT / WEB 与整篇 `rich_list` 共用同一套解析与渲染，删掉 bilibili-API 快照里不存在的 `RICH_TEXT_NODE_TYPE_RICH` 死分支；WEB 点击改用 `jump_url`（显示文本可能被缩短），`OpusRichTextParserTest` 8 例。
  - ③CI 两处堆内存 `sed` 改整行替换 + `grep` 硬校验（原先写死 `-Xmx4096m`、替换 0 命中且被 `|| true` 掩盖）。
  - ④台账 versionCode 硬错（2611070 → 2611071）与「下次递增」三件事已更正。
  - ⑤`ci.yml` 增加 coexist flavor 单测与 `assembleRelease`（R8 / shrinkResources / keep 规则）冒烟——这条链路此前只在打 tag 发版时第一次被验证。
  - ⑥双文件缓存删页下标错位（B2）改走纯函数 `util/LocalPageFileIndexPolicy`（按目录名反查、降序删除），`LocalPageFileIndexPolicyTest` 6 例。
  - ⑦文档漂移一批：`FEATURES.md` 的锁定按钮开关（已随播放器按钮槽位删除）、`AGENTS.md` / `architecture-map.md` 的「无 CI」、播放器界面 spec 的状态、`docs/README.md` 收录 3 份活 spec、`agents/build.md` 的 Gradle 版本（8.11.1 → 8.14.5）与架构地图实测尺寸/行号。
  - **L0 验证（26.11.07 实测，本机已注入 `private/`）**：两 flavor 全量单测各 **197 suites / 2149 tests / 0 失败 0 错误**；`:app:assembleDebug`、`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest`、`:app:assembleRelease` 全部 BUILD SUCCESSFUL。
  - 调研清单第 **5/8/9/11/12** 条（教程旧链路收尾、播放器巨类收口、零测试模块补测、无障碍与热区、x86 瘦身）按用户 26.11.07 指示另拆工单，本批未展开。

- **26.11.07 教程系统收尾（调研清单第 5 条；本机 `main` 已提交，本环境无 GitHub 凭据、未 push）**：①5 条页面滑动提示改成注册表驱动的 `TutorialKind.HINT`（`Tutorials.hints`），新增 `tutorial/TutorialHints.kt` 统一渲染，页数读宿主 ViewPager / ViewPager2 的**真实页数**（旧实现写死 3/4/2/2，搜索页还是 `size-1`），5 个宿主页不再手写 `showPagerTutorial(activity, 页数)`，`SearchActivity` 自己抄的那份显隐逻辑删除；②`TutorialStore` 的 `isRead` / `markRead` / `clear` 按 kind 分流，管理页改由 `Tutorials.all` + `Tutorials.hints` 渲染，不再维护 `HINT_PAGES` 硬编码清单；③**旧链路整体删除**：`helper/TutorialHelper.kt`、`activity/TutorialActivity.kt`、`model/Tutorial.java`、`model/CustomText.java`、`res/layout/activity_tutorial.xml`、`res/xml/tutorial_*.xml` ×12、`strings.xml` 的 10 个 `tutorial_*` 数组与 2 条只被 `TutorialHelper` 使用的文案、Manifest 旧教程页注册；④存档键仍是 `tutorial_pager_<宿主类名>`，老用户「看过不再弹」不会重来。**L0（26.11.07 实测，隔离工作树、未同步 `private/`）**：`:app:assembleDebug`、`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest`、`:app:assembleRelease` 全部 BUILD SUCCESSFUL，两 flavor 各 **175 suites / 1911 tests / 0 失败 0 错误**。真机未验项见 §2.1，交接单 [`review/manual-test-guide-261107-tutorial-hint.md`](manual-test-guide-261107-tutorial-hint.md)（5 条）。

- **26.11.07 无障碍与触控热区（调研清单第 11 条；本机 `main` 已提交，本环境无 GitHub 凭据、未 push）**：
  - ①**布局无障碍全量收口**：`res/layout` 下 124 个 `ImageView|ImageButton` 现在全部显式声明——49 个交互 / 信息图给可读描述（播放器按钮复用既有 `setting_player_btn_*`、`setting_player_ui_label_play`，其余新增 14 条 `a11y_*`），75 个装饰图统一 `contentDescription="@null"` + `importantForAccessibility="no"`（改造前只有 12 处 `contentDescription`、0 处 `importantForAccessibility`）。新增 `ui/a11y/LayoutAccessibilityTest`（4 例）防回归：每个图片控件必须有声明、装饰图必须对读屏隐藏、`ImageButton` / `android:clickable="true"` 的图必须有可读文案、引用的 `@string` 必须真实存在（`values/` 下多个文件都算）。
  - ②**播放器热区**：底部按钮 32×37 → **44×44dp**（「清晰度 / 音量± / 分P / 播放」5 个键全上 48dp 会超过 233dp 屏宽；44 是仓库已有的手表紧凑档下限，与 `dialog_item_min_height` 同值）；左右两列 28×28 → **48dp 宽 × 按可用高度自适应的格高**（新增纯函数 `player/PlayerControlCellSize` + 6 例单测；图标视觉尺寸与贴屏幕边缘的位置不变，多出来的宽度向屏幕内侧扩）；进度条 20 → **48dp**（`paddingTop/Bottom=14dp` 保住原来 20dp 的波形高度）；弹幕发送按钮 35 → 48dp；倍速 / 无极调音两个滑杆补 `minHeight=48dp` + 上下内边距（原来实际高度约 32dp）；底部按钮行两侧改成 `layout_weight=1` 的横向滚动条（按钮少时贴播放键，多时在自己的半屏内滚动），避免整行比屏幕宽被裁。**本条 26.11.08 已整体回退**（宽屏下底栏被撑到 94dp、按钮簇被摊开、竖列被放大到 48dp，见 §1「26.11.08 播放器宽屏布局回归」；尺寸回到 11.02 口径，`PlayerControlCellSize` 及其单测删除）。
  - **L1（AVD 466×466 @320dpi，standard debug 包，本地 mp4 起播，`uiautomator dump` 量控件 bounds；2px=1dp）**：播放键 `[201,344][265,418]`=32×37dp → `[189,330][277,418]`=44×44dp；进度条 `40px`=20dp → `96px`=48dp；右列弹幕 `[402,91][458,147]`=28×28dp → `[362,53][458,111]`=48×29dp（宽度达标、图标仍贴右边缘）；同一次 dump 里 `danmaku_btn` / `rotate_btn` / `button_sound_cut` / `button_video` 的 `content-desc` 从空串变成「弹幕 / 旋转 / 音量减 / 播放与暂停」。改造前后同视角对照图：`outputs/a11y/player-touch-before-after.png`（`outputs/` 已 gitignore）。
  - **已知限制（未在本条擅自改交互）**：小屏手表的窗口可用高度只有 209dp，「顶栏 + 进度条 48dp + 按钮行 44dp + 右列 3 格」放不下 48×48 的竖列，实测只能到 48×29dp；要让竖列真正 48×48 必须减少常驻按钮或改成可滚动列，属设计决定，留给真机验证后再定（见 §2.1）。短视频播放页（`fragment_short_video_page.xml`）底部一排 6 个 32×37dp 按钮同样没动：6×44dp 会超出 233dp 屏宽，要改得先定「这排按钮怎么折行 / 滚动」。文章页点赞/投币/收藏的 30dp 图标本来就不在播放器里，也不在本条范围。
  - 真机 TalkBack 逐屏走查与手感见 §2.1。

- **26.11.07 番剧分区页（对比清单「番剧分区的推荐 / 追番 / 索引 / 筛选」这一条，本机 `main` 已提交；本环境无 GitHub 凭据，未 push）**：
  - **新增一级页面** `activity/video/BangumiActivity`（菜单项 `bangumi`，标题「番剧」）+ 布局 `activity_bangumi.xml`：三个页签「推荐 / 追番 / 索引」，三份独立的分页状态（页码 / 到底 / 在飞 / 代际），切页签不丢已加载内容。
  - **推荐**：`/pgc/season/index/result` + 固定 `order=3`（最多追番）、无筛选；未登录也能看。
  - **追番**：复用 `BangumiApi.getFollowingList`；未登录时不发请求，给「点我去登录」的空态，登录回来 `onResume` 自动补拉。
  - **索引 + 筛选**：筛选条件走 `/pgc/season/index/condition` 动态下发（排序 5 项 + 地区/风格/类型/付费/配音/版权/状态/年份/季度 9 组）。**刻意没做整排 chip**：9 组 × 最多 45 个选项在 192–233dp 的手表屏上横滑也看不全，还会挤掉列表空间；改成一行「筛选：当前条件」+ `TerminalDialog` 分组单选（与「搜索排序」同一套交互）。选完立即按新条件重拉，入口行回显当前条件。
  - **新 API**：`api/BangumiIndexApi.kt`（`getCondition` / `parseCondition` / `getIndex` / `parseIndex` / `defaultFilters`）。解析按 26.11.07 实测的现役字段写（`score` / `order` 是字符串，`has_next` 要和 `total` 一起判到底），请求失败一律抛 `IOException`——**不能**返回空页，否则上层会把风控页当成「到底了」。单测 `BangumiIndexApiTest` 7 例。
  - **菜单项对老用户回填一次**：新增 `MenuConfig.BANGUMI` + `ensureBangumi()`（照 `ensureExit` 的写法，判据是新键 `menu_bangumi_state`，由 `saveEnabled` 写入），否则新功能只有新装用户看得见。回填位置用 `insertAtCanonicalPosition` 算（落在「热门」之后，不是甩到末尾）。`MenuConfigTest` 补 2 例 + 更新 4 处既有断言。菜单表/API 映射同步进 `docs/architecture-map.md` §3.1 / §9，接口结论进 `docs/bilibili-api-notes.md` §14，功能说明进 `docs/FEATURES.md` §2.8 与 §1.2（14 → 15 个入口）。
  - **L0（26.11.07 实测，隔离工作树、未同步 `private/`）**：`:app:assembleDebug`、`:app:testStandardDebugUnitTest` 均 BUILD SUCCESSFUL，standard flavor **178 suites / 1931 tests / 0 失败 0 错误**。**设备端（L1/L2）本轮没跑成**：本机只有 android-37 的 google_apis 镜像，`wear5`（android-34 wear）系统镜像缺失；唯一可用的 `rebili_watch37` 正被另一个工单占用，且所有模拟器必须一起 `-read-only` 才能并存——手机 AVD 起在 5556 后随即退出。真机项见 §2.1。

- **26.11.07.1 之后新修：点搜索历史崩溃（控制台崩溃 #230）**：
  - **现象**：`java.lang.IndexOutOfBoundsException: Index: 1, Size: 1` → `SearchHistoryAdapter.onBindViewHolder$lambda$0` → `SearchActivity.onCreate$lambda$14$lambda$9(SearchActivity.kt:237)`，即**点搜索历史条目**时崩。现场：26.11.07 / 共存版 / OPPO OWW231 / Android 11，控制台用 `2611070_coexist_private_mapping.txt` 解混淆成功（`retrace_state=1`）。
  - **根因**：adapter 在 `onBindViewHolder` 里把 **position 捕获进点击/长按 lambda**，点下去才用它索引**可变的 ArrayList**（`searchHistory`），列表增删之后旧下标就过期。`onBindViewHolder` 里那句「position 越界就早退」的守卫在帮倒忙：早退时 holder 上的旧监听器不清空，行还在、点它拿到的却是越界 position。
  - **修法**：①`adapter/SearchHistoryAdapter.kt` 点击与长按改成事件发生时取 `holder.bindingAdapterPosition`（`NO_POSITION` / 越界直接吞）；②`adapter/SearchSuggestionsAdapter.kt` 同样处理（建议列表每轮 `clear + addAll`，同一个坑）；③`SearchActivity` 三处回调（历史点击 / 历史长按删除 / 建议点击）补 `position in list.indices` 边界检查。
  - **同族排查（20 处回调站点逐处定性）**：另命中 1 处并一并修掉——`activity/video/local/LocalPageChooseActivity.kt`：删分 P 之后 `notifyItemRangeChanged(0, size - position)` 的**起点写错**（应为 `position`），删中间页时尾段 holder 不重绑、仍握着旧下标，点它就是 `pageMediaFiles[position]` 越界（**不需要竞态**，比 #230 更确定性）；回调体同时改 `getOrNull` 兜底。其余 19 处不命中——7 处已有边界守卫，其余对应集合在本页只读、无收缩路径。
  - **L0（26.11.07 实测，本机已同步 `private/`）**：`:app:assembleDebug`、`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 全部 BUILD SUCCESSFUL，两 flavor 各 **203 suites / 2201 tests / 0 失败 0 错误**（首次 `assembleDebug` 撞上 Gradle build cache 写盘报 `Could not get file mode …SettingsAdapter$InputExtra.class`，按 `docs/agents/build.md` 加 `--no-build-cache` 后通过）。
  - **未验证**：真机复现路径（点历史条目 / 长按删历史后再点 / 删分 P 后再点）属人测，本次只做到 L0——这条是崩溃兜底修复，越界时降级成「点了没反应」，不再崩。

- **26.11.07 用户主页「视频」Tab 加空间内搜索与排序（对比清单「用户信息-搜索稿件以及旁边的排序方式」这一条，参考 `outputs/ref/cyq-Re-BiliTerminal` 的做法；本机 `main` 已提交）**：
  - ①`api/UserInfoApi` 加排序档位常量与重载 `getUserVideos(mid, page, keyword, order, list)`：`order` 走白名单 `normalizeOrder`（只认 `click` / `stow`，其余回落 `pubdate`），`keyword` 先按 `encodeURIComponent` 口径编码再签 WBI（原先直接拼进签名串，用户输入含 `&` / `=` 会破坏 query 并使签名失配，编码口径见下面 ⑤ⓑ）；原四参重载保留（`PlayerActivity` 的联合投稿兜底仍在用）。请求地址抽成纯函数 `buildUserVideoUrl`，`UserInfoApiTest` 5 例（白名单回落 / 关键词编码 / 空关键词）。
  - ②`UserVideoFragment` 重写：搜索栏 + 排序入口（最新发布 / 最多播放 / 最多收藏，弹窗走 `TerminalDialog.singleChoice`）、换关键词或换排序都重拉第一页并按「代次」丢弃迟到响应、下拉刷新接上（基类默认禁用且此前未注册，失败后只能退出重进）、空态分「TA 还没有投稿过视频」/「没有搜到相关稿件~」/「加载失败，请下拉重试」、翻页失败与基类同口径回退页码。
  - ③`RefreshListFragment` 补带文案空态 `showEmptyView(文案)` 与 `hideEmptyView()`：空态是把 `recyclerView` 设 `GONE` 顶上去的，搜索从「没搜到」变回有结果时必须还原可见性，否则用户一直看着空态文案。默认文案在 `onViewCreated` 记一份，无参 `showEmptyView()` 行为不变。
  - ④新增 `res/layout/fragment_user_video.xml`：`CoordinatorLayout` + `AppBarLayout`（`scroll|snap`）+ `include fragment_simple_refresh`，搜索栏随列表滚出屏幕；回第一页时 `setExpanded(true, false)` 展开。AppBar 透明底 + `elevation=0` + `liftOnScroll=false`，不切断氛围背景。`strings.xml` 新增 `hint_search_space_video` / `text_space_video_*` 共 9 条。
  - ⑤**审查后修正（同一功能的后续提交）**：ⓐ首屏 catch 的空态原先无条件显示——「下拉刷新 / 换排序失败」时列表里还留着上一批数据，`showEmptyView` 会把 `recyclerView` 设 `GONE`，一次网络抖动就让整页投稿消失；改成只在 `videoList.isEmpty()` 时才换失败空态（参考实现同一位置也有这个守卫）。ⓑ关键词编码从表单编码（`URLEncoder`）改成 `encodeURIComponent` 口径（空格 `+`→`%20`、`~ ! ' ( )` 还原）——`signWBI` 的 `Uri.encode` 保留集合含 `%` 与 `+`，表单编码的差异项会让签名串与服务端重算的 `%20` / 原文对不上，实测 `a~b` 用 `a%7Eb` 签会回 `-352`「风控校验失败」。ⓒ`onViewStateRestored` 按恢复后的输入框文字同步 keyword，修「换主题 / 改字体后重建，输入框里有关键词、列表却是全部投稿」。
  - **L0（26.11.07 实测，隔离工作树 `outputs/wt-user-video`，未同步 `private/`）**：`:app:assembleDebug` BUILD SUCCESSFUL（两 flavor 各 4 个 APK），两 flavor 单测各 **178 suites / 1926 tests / 0 失败 0 错误**（含 `UserInfoApiTest` 5 例：排序白名单 2 + 请求地址 2 + 关键词编码口径 1）。
  - **L1（AVD `rebili_watch37`，384×384 @320dpi，standard debug 包；`GetIntentActivity --es type user --el content 2` 直达用户主页后左滑到「视频」Tab）**：改前该页只有「视频系列」入口与卡片；改后同一视角多出搜索栏（左排序图标、hint「搜索 TA 的稿件~」、右侧搜索图标），点排序图标弹出「投稿排序 / 最新发布 / 最多播放 / 最多收藏」且当前档位带对勾。对照图 `outputs/user-video-search-before.png` / `-after.png` / `-sort-dialog.png`（`outputs/` 已 gitignore）。**顺手修掉一个实测出来的布局缺陷**：排序按钮原先声明在 `EditText` 之前，被输入框整块盖住——点图标弹出的是键盘、排序弹窗根本不开（改成声明在输入框之后，与搜索页一致）。
  - **未验证（第二次启动模拟器后图形后端报 `Failed to bind to texture` 并 adb 掉线，按「两轮上限」止损）**：①切换排序后列表是否真按播放量重排（弹窗与勾选态已验，选项点击后的重排没截到图）；②输入关键词后的空态与「回到有结果时列表恢复」；③搜索栏随列表滚出的 `scroll|snap` 手感。三条留作真机验收，交接单 [`review/manual-test-guide-261107-user-video-search.md`](manual-test-guide-261107-user-video-search.md)（4 条，含 AI 断言写法）。

- **26.11.07 关注/粉丝列表补「特别关注」徽章（用户要求「关注列表互关显示和特别关注显示」；互关已在 26.11.06 的批次 1 实现，本批补特别关注；互关做法参照 `outputs/ref/cyq-Re-BiliTerminal`）**：
  - 数据：`api/FollowApi` 解析 `x/relation/followings` / `followers` 列表项的 `special`（0/1），新增纯函数 `isSpecialFollow(selfList, special)`——与互关同口径，只有查看自己的列表才成立；`getFollowTagUsers` 不设（分组明细的 `special` 恒为 0，且特别关注本身就是系统分组 `tagid=-10`）。`model/UserInfo` 末尾追加 `specialFollow`（Parcelable 只追加、与 `mutualFollow` 同序；`UserInfo` 没有落盘序列化，老数据不受影响）。
  - 展示：`res/layout/cell_user_list.xml` 昵称行在「互关」左侧加金色「特别关注」徽章，金色取 `ColorScheme.getGold()`（当前主题的 `GOLD`；**只有「彩虹幻境」的亮色表换成更深的 `#E6A800`，默认主题亮色下仍是 `#FFB800`**，浅色卡片上对比偏低，已列为 L2 重点核对项）；`UserListAdapter` / `FollowGroupAdapter` 每次 bind 重置显隐，`cell_up_list` 换布局时判空。文案 `social_special_follow_badge` 加在 `values/social_port_strings.xml`（与互关徽章同文件）。
  - **L0（26.11.07 实测，隔离工作树 `outputs/wt-follow`，未同步 `private/`）**：`:app:assembleDebug` BUILD SUCCESSFUL（两 flavor 各 4 个 APK），两 flavor 单测各 **179 suites / 1937 tests / 0 失败 0 错误**（含 `MutualFollowPolicyTest.isSpecialFollow_requiresSelfListAndSpecial1` 1 例；比含私有功能的全量少 24 suites / 264 tests，因 `private/` 未同步）。
  - **未验证（L2）**：本机模拟器没有登录态、关注列表打不开，徽章的实际显示、双徽章挤压与列表滚动复用时是否串行都只能人看；亮色模式下的金色可读性是重点（默认主题亮色仍是 `#FFB800`）；交接单 [`review/manual-test-guide-261107-follow-special.md`](manual-test-guide-261107-follow-special.md)（2 条）。

- **26.11.08 验收不合格的 P0+P1 九项修复（来源：验收工单 298854… 的「优先修复清单」P0①–③ / P1④–⑨；本机 `main` 已提交，本环境无 GitHub 凭据、未 push）**：
  - ①**markdownlint 3 错（P0）**：`docs/architecture-map.md` §8.2 里两处连续空行 + 代码围栏收尾后缺空行（620a0e9 / 0a5900e 引入），整条 lint 命令恢复 0 错误。
  - ②**死链（P0）**：`docs/README.md` 收录了 `superpowers/specs/2026-10-14-console-v2-design.md`，该文件被 `.gitignore:71` 排除、HEAD 未跟踪，新克隆必死链 → 删掉那行索引（文件本身留在本机；`docs/archive/README.md` 已注明它不在仓库内）。
  - ③**播放器左列热区回归（P0）**：`PlayerButtonSlots.fitColumnsToAvailableHeight` 给左列取 `rootView.height`（整窗 233dp），用户把 3 个键放左列就放大到 3×48=144dp，越过顶栏与底栏之间约 105–110dp，压住进度条与底部按钮行并抢触摸（右列取被夹住的父容器高度，一直是对的）。改成左右两列共用右列父容器（`right_control`）的高度上限（新增纯函数 `PlayerControlCellSize.columnAvailableHeightPx`；量不到就不放大、保持改造前 28dp），单测 6 → 11 例，含回归复现 36dp × 3 = 108dp 放得下。
  - ④**番剧索引筛选竞态（P1）**：`loadIndex` 原来「在飞就整轮丢掉这次调用」，用户在请求在飞时改筛选会被丢；而且那一轮旧条件的响应回来仍照常上屏（筛选行新条件、列表旧条件）。新增纯 Kotlin 闸门 `activity/video/BangumiIndexLoadGate`（5 例单测）：在飞只记「条件变了」，收尾后按最新条件重拉一次，被作废那轮不上屏；触底翻页在飞时仍直接丢弃。
  - ⑤**空间投稿三处（P1）**：ⓐ首屏重拉失败走基类 `loadFail` 的 `page--`，page 变 0 之后触底会把第 1 页整页再追加一遍（失败后页码回 1）；ⓑ`result == -1` 业务失败此前与成功同路（清空并重建列表），下拉刷新 / 换排序失败就整页消失（改成保留上一批、只在列表本来为空时换失败空态）；ⓒ排序档位不随主题 / 密度重建恢复（`onSaveInstanceState` + `onViewStateRestored`，与 keyword 同口径、取值走 `normalizeOrder` 白名单）。
  - ⑥**弹幕落盘第 5 份副本（P1）**：`DownloadService.downDanmaku` 是 39e4136 收口时漏掉的那份，仍自己判状态码 / 解压 / 写盘且没有空正文校验；改走 `DanmakuFileFetcher.fetch` + `writeBytes`（不再先 `resetFile` 建空壳），网络侧失败 → `ERR_NETWORK`、写盘失败 → `ERR_FILE`，`DanmakuFileFetcher` 头注释按 5 份副本的事实重写。
  - ⑦⑧⑨**文档（P1）**：教程交接单 H02 的「公开包 2 页」改准（普通视频详情默认 3 页；关「视频相关推荐」或番剧详情才是 2 页；私有包给番剧详情多一页「角色」），搜索页「提示一露出就记已读」的口径变化补记；新增三份 L2 交接单 [`review/manual-test-guide-261108-a11y.md`](manual-test-guide-261108-a11y.md)（7 条）/ [`review/manual-test-guide-261108-opus-danmaku.md`](manual-test-guide-261108-opus-danmaku.md)（6 条）/ [`review/manual-test-guide-261108-bangumi.md`](manual-test-guide-261108-bangumi.md)（7 条）；`agents/build.md` 的「无 CI」与架构地图 §10 的 CI 描述按 workflow 改准，`AGENTS.md` 的架构地图尺寸与各 § 行号重新实测（329,193 字节 / 2,067 行）。
  - **L0（26.11.08 实测，隔离工作树 HEAD=401527e、未注入 `private/`）**：`:app:assembleDebug` BUILD SUCCESSFUL（两 flavor）；`testStandardDebugUnitTest` / `testCoexistDebugUnitTest` 各 **180 suites / 1947 tests / 0 失败 0 错误**（基线 179 / 1937，+1 套 +10 例 = `PlayerControlCellSizeTest` 6→11、`BangumiIndexLoadGateTest` 5）。
  - **未验证（L2，只能上手 / 看画面）**：③的左列多键复现与热区手感、④筛选竞态的真实操作时序、⑤排序随主题重建、⑥失效弹幕地址下载的可见提示——见 §2.1 对应条目与三份交接单。

- **下次递增（26.11.07 之后）必须同时做三件事，缺一件 CI 就红或用户收不到包**：
  1. **升 `versionCode`**（`app/build.gradle:45`，`versionName` 在 `:46`）：补丁版把尾号补成 tag 的 `.N`（`26.11.07.2` → `2611072`）。CI 的 `verify-version` 现在硬校验「`versionCode` 末位 == tag 的 `.N` 后缀」，写错直接发版失败（26.11.07.1 事故的封堵，先例 `1c404c3` 只升尾号、`versionName` 不动；而 `UpdateManager` 是严格大于比较，尾号不升老用户收不到修正包）。
  2. **改 `strings.xml` 的 `update_log_current` 锚点**为「【YY.MM.DD 本次更新】」，否则 `:app:verifyVersionConsistency` 失败、CI 抽不到发版说明。
  3. **把 HEAD 上所有未发版提交补进 `update_log_current`**：26.11.07.1 之后仍有 31 个提交（表冠三处加固、三入口默认隐藏、背景图入口、本批调研清单修复等）没进日志，`versionCode` 仍停在已发布的 `2611071`。发版前必须逐条归属到日志条目，别出现「装了什么说不清」的版本（26.11.06 事故，见 §3.135）。
  - release immutability 下被 Release 用过的 tag **永久不可复用**，同版本名补丁版只能用 `26.11.07.1` 这种后缀形式。
- **构建命令**（单测任务名**带 flavor 前缀**，`testDebugUnitTest` 自 26.10.11 引入 flavor 后已不存在）：

  ```text
  .\gradlew.bat :app:assembleDebug
  .\gradlew.bat :app:testStandardDebugUnitTest
  .\gradlew.bat :app:testCoexistDebugUnitTest
  .\gradlew.bat :app:assembleRelease
  ```

- **JDK**：需要 17。仓库 `gradle.properties` **不再写死** `org.gradle.java.home`（写死的机器路径换机即报 `Value '...' is invalid`），本机 JVM 不是 17 时在**用户级** `~/.gradle/gradle.properties` 里设，或让 `JAVA_HOME` 指向 17。
- **SDK**：`compileSdk 34` 需要 `platforms/android-34` 与 `build-tools/34.0.0`；缺了 AGP 会在配置阶段自动下载（licenses 已接受时），也可用 `sdkmanager` 预装。`local.properties` 里写 `sdk.dir`，该文件被 gitignore。
- **依赖仓库**：`settings.gradle` 已把 aliyun 的 `google` / `public` / `gradle-plugin` 三个镜像前置，`google()`/`mavenCentral()`/jitpack 仅作兜底。
- **改了 `res/` 文件集合时**：`:app:clean` 与后续命令**必须分两次 Gradle 调用**，且带 `--no-build-cache --no-configuration-cache`，否则会回放陈旧资源（症状是 `Unresolved reference 'R.layout.xxx'`）。
- **版本一致性**：`:app:verifyVersionConsistency` 以 `strings.xml` 的 `update_log_current` 为锚点校验版本一致性；发版说明也由 CI 从它抽取（抽不到直接拒绝发版）。
- **单测（26.11.05 合并远程 `main`、修好冲突残留后实测；`testStandardDebugUnitTest` 与 `testCoexistDebugUnitTest` 各跑一遍，命令带 `--no-build-cache --no-configuration-cache`）**：两个 flavor 均 **130 suites / 1379 tests / 0 失败 0 错误 0 跳过**（`app/src/test/` 下 `*Test.kt|java` 共 129 个源文件）。**本机已同步私有功能仓库（`private/` 存在，26.11.07 复核）**——本行 130 suites / 1379 tests 是 26.11.05「未同步 `private/`」口径的历史记录，**已不代表现在**；本机当前全量计数（含随 `private/` 一起注入的用例类）记在 §1「26.11.07 调研清单」那条。计数方式：`app/build/test-results/<flavor>DebugUnitTest/TEST-*.xml` 的 `tests=` 求和（**用 PowerShell 解析会因中文用例名抛 `InvalidCastToXmlDocument`，改用 Python `xml.etree`**）。用例数一律以机器产出为准，队友口述数字不进入文档。
- **26.10.10 那一轮的构建记录（历史，留作「改 minSdk / 重跑 release 链路」时的验证口径）**：`minSdk 24 → 19` 全部改动（含 jsoup 1.16.1、7 个 `res/drawable-v21` 资源）落盘后，按「`clean` → `assembleRelease` → `lintRelease` → 单测」四步跑完（均带 `--no-configuration-cache`，`clean` 与 `assemble` 分两次调用），四步全 `BUILD SUCCESSFUL`（37s / 2m13s / 1m25s / 39s）。当时单测汇总 **49 suites / 460 tests / 0 failures**（任务名当时还是 `testDebugUnitTest`，现已带 flavor 前缀），最新 XML 时间 `2026-10-09T22:59:04`（晚于当轮 APK 出包时间 22:56:58，覆盖了当轮全部源码改动）。`:app:lintRelease` 的 **`NewApi` 从 22 条降到 2 条**，且这 2 条就是 `PlayerActivity.kt:1981` 的已知假阳性。发版前仍建议再跑一次确认。
- **「自动跳过片头片尾」已恢复上线（26.10.22）**：26.10.10 的紧急撤回**已解除**，`SkipOpEdPrefs.FEATURE_ENABLED` 回到 `true`，设置入口与搜索索引均已恢复；口径按用户拍板改为**默认关闭**（`DEFAULT_ENABLED = false`，老用户存档里显式写的值仍然优先）。恢复细节见 §3.3「恢复记录」，乱跳的三个根因修复见 §3.55。
- **本轮基建改动（26.10.10）**：**`minSdk 24 → 21 → 19（Android 4.4）`**，唯一落点是 `app/build.gradle:21`。两级下调的成本不同，分开记：
  - **24 → 21**：代码侧清掉**四处** API 24 才有的调用：`util/AsyncLayoutInflaterX.java` 的 `java.util.function.BooleanSupplier` → 工程内 `CancelledChecker`；`util/SharedPreferencesUtil.java:167` 的 `java.util.function.Consumer` → 工程内 `EditorAction`；`activity/reply/WriteReplyActivity.kt:177` 的 `Map.getOrDefault` → `msgMap[k] ?: 默认值`；`util/VideoPreloadManager.kt:12` 的 `ConcurrentHashMap.newKeySet<Int>()` → `Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())`（**这一处是会真崩的**：类一初始化就 `NoSuchMethodError`，且已确认留在 release dex 里）。两个调用点（`activity/base/BaseActivity.kt:435`、`util/AccountManager.java:202`）走 SAM 转换，未改。
  - **21 → 19**：依赖必须降两处——`androidx.appcompat:appcompat` 1.7.0 → **1.6.1**、`androidx.fragment:fragment-ktx` 1.8.0 → **1.6.2**（只有这两代 AAR 自己还声明 minSdk 14；1.7.0 / 1.8.0 声明的是 21，`checkReleaseAarMetadata` 会硬失败），并**恢复** `androidx.multidex:multidex:2.0.1` + `BiliTerminal.kt` 的 `attachBaseContext()` 里 `MultiDex.install(this)`——26.09.10 那次「minSdk 21 下是死代码」的删除被整体回滚。改回 21 时这三件事要一起撤。
  - **21 → 19 又清掉一批 API 21 调用点**（按 `:app:lintRelease` 的 `NewApi`，`current min is 19`）：`Build.SUPPORTED_ABIS` 三处（**静态数组字段**，低版本一执行即 `NoSuchFieldError`，而它是 `Error`，`catch (Exception)` 兜不住）统一改走 `api/TerminalApi.java` 新增的 `firstAbi()` / `supportedAbis()`（`SDK_INT >= LOLLIPOP` 闸 + 回落 `Build.CPU_ABI`）；`ui/widget/TextClock.kt` 的 `@JvmOverloads` 主构造会经 4 参 `TextView` 构造（API 21），而全工程 **43 个布局**都 inflate 它 → 改成三个显式次构造；`activity/settings/TutorialManagerActivity.kt:195/199` 的 `View.setBackgroundTintList` → `ViewCompat.setBackgroundTintList`；7 个矢量图（`icon_announcement`/`icon_download`/`icon_home`/`icon_nav_dynamic`/`icon_nav_media`/`icon_nav_trending`/`icon_update`）的 `android:tint="?android:attr/…"`（pre-21 解析不了）→ `res/drawable/` 改固定色，**带 `?attr` 的正本原样挪到 `res/drawable-v21/`（两份必须一起改）**。
  - **依赖侧一处真改动（24 → 21 阶段）**：`org.jsoup:jsoup:1.17.2` → **`1.16.1`**。1.16.2 起 jsoup 的 `StringUtil` 在**静态初始化块**里调 API 24 才有的 `ThreadLocal.withInitial(java.util.function.Supplier)`，而 `Jsoup.parse()` 必然用到它，minSdk 19/21 上会 `NoSuchMethodError`。**这不是推测**：把出包的 `classes.dex` 反汇编后确认 1.17.2 的 `withInitial` 就在 `<clinit>` 里，降到 1.16.1 后 dex 中 `java/util/function/` 与 `withInitial` 归零。**jsoup 不要再往上升**。
  - **验证方式**：不只看源码，还把**出包的 `app-universal-release.apk` 的 `classes.dex` 反汇编**逐条归属到类（判据与工具坑见 `docs/architecture-map.md` §1.1 末尾）。定稿包的 dex 实测：`newKeySet` / `KeySetView` / `java/util/function/` / `withInitial` **全为 0**，`newSetFromMap` 为 1；残余 `java/util/stream/` 1 与 `java/time/` 2 已追溯到「只被带 `SDK_INT` 闸或 `@IgnoreJRERequirement` 重载调用」的位置。
  - `:app:lintRelease` 另外两类输出**不改**：`InlinedApi`（常量已内联进字节码，旧系统只是忽略，如 `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS` / `MotionEvent.AXIS_SCROLL`）与 `ObsoleteSdkInt`（现在恒真的 `SDK_INT` 分支）。`activity/player/PlayerActivity.kt:1981` 的 `MediaSession.Callback` 是 **lint 假阳性**——所在 `private fun initMediaSession()` 第一行就是 `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return`。
  - **只对 release 包负责**：`jvmTarget 17` 下 Kotlin 会给 21 个数据类的 `hashCode()` 生成 `java/lang/Long.hashCode(J)` 之类的 API 24 调用，release 里被 R8 全部消除（dex 零命中），**debug 包才有**。本分支只发 release，故不动；要发 debug 包必须先处理这一条。
  - **完整兼容清单（含「再往下调会硬失败」的依赖下限、dex 级审计出的第三方库 API 24 引用与为什么可以不管）见 `docs/architecture-map.md` §1.1**，本文件不复述。

- **26.11.08 播放器卡顿回归（26.11.07 起「有些设备放视频卡顿、上版没有」）根因定位与修复：档位不该碰解码方式与清晰度**：
  - **归因（真机诊断包 8 份报告，控制台 `testbuild.diag.PlaybackDiag` id 238~246）**：1GB 手表 `aqsh_watch A6-A66`（926MB / 4 核 / sdk27，档位分 30 → 低档）软解 720P 实测 `video.fpsOutput` **14.2~17.5**（源 30fps），**关掉弹幕后仍是 14.2 / 17.5**（240、244 两份 `danmaku.visible=false`）；同机器 360P 软解 33.3（246，Xiaomi Mi Watch）；硬解对照组 vivo V1901A 720P 为 30.3 / 29.9。**卡顿＝解码器被换成了软解**，不是弹幕、不是缓冲（`play.stallHits=0`、`play.errorCount=0`）。
  - **主根因**：`e413c7a` 让 `mediacodec` 默认值随设备档位走——`hardwareDecodeEnabled()` 原为 `getBoolean("player_codec", !DeviceProfile.resolve(this).preferSoftwareDecode)`，低端档 `preferSoftwareDecode=true`，于是**从没动过解码设置的用户被静默切成软解**；26.11.06.1 同处是 `getBoolean("player_codec", true)` 恒硬解。设置页还显示「硬件解码」（`PlayerSettingsGroups.kt` 该行默认值写死 `"true"`），用户完全无从察觉。
  - **附带根因（同一版）**：`DeviceProfile.playQn` 的清晰度封顶在**主入口根本走不到**——`activity/video/JumpToPlayerActivity.kt:114` 先用 `NetworkPlaybackQuality.defaultQn(this)` 定 `data.qn`，`api/PlayerApi.java:723` 把它塞进 `currentQuality` extra，`PlayerActivity` 取 extra 时 `startupDefaultQn()` 只做兜底，所以 `device.playQnLimit=16` 的机器照样请求 720P（报告里 `quality.qnCurrent=80`、`video.resolution=1152x720`）。等于「只把用户的硬解换成软解，却没给到降清晰度的补偿」。
  - **修法（用户拍板「一般都不要软解」＋清晰度听用户配置）**：①`util/DeviceProfile.kt` 的 `Profile` 删掉 `playQn` / `preferSoftwareDecode` 两个字段，档位**只剩弹幕行数**一项；②`PlayerActivity.hardwareDecodeEnabled()` 改回 `getBoolean(SettingsKeys.PLAYER_CODEC, true)`，`startupDefaultQn()` 收敛成 `NetworkPlaybackQuality.defaultQn(this)`（并留注释禁止再把档位判断加回解码）；③`player/PlaybackDiag.kt` 去掉 `device.preferSoftwareDecode` / `device.tierPlayQn` 指标，`decode.mediacodec` 默认恒 `true`、来源改报「默认硬解」；④`PlayerActivity.diagFillMetrics` / `diagRecordIjkOptions` 去掉 `device.playQnLimit` / `device.preferSoftwareDecode` / `ijk.preferSoftwareDecode`；⑤`DeviceProfileTest` / `PlaybackDiagTest` 跟着改口径（新增「没设过必须是硬解」断言）。
  - **当时刻意没动**：弹幕行数上限（`player/DanmakuManager.kt:138` 仍按档位把用户设的行数压到 6/10/15，报告 245 里用户设 50 实际 6）——属同类「静默盖用户设置」，改它要调弹幕观感。**26.11.08 用户拍板撤掉，见下一条①**。
  - **L0（26.11.08 实测，隔离工作树 HEAD=5c531f9、未注入 `private/`）**：`:app:assembleDebug` BUILD SUCCESSFUL（两 flavor 各 4 个 APK）；`testStandardDebugUnitTest` **181 suites / 1962 tests / 0 失败 0 错误**（基线 180 / 1947，+1 套 +15 例 = `PlaybackDiagTest`）。**主工作区当时混着另一会话的在途改动（`DetailEntryPrefs.kt` 语法错、`isLikeCardRingEnabled` 未解析），不能直接构建**，故在 `git worktree` 里只叠加本任务文件后跑。**拔出后再跑一遍（隔离工作树 HEAD=5c531f9，只叠 3 个代码文件）**：`:app:assembleDebug` BUILD SUCCESSFUL（8 个 debug APK）、`testStandardDebugUnitTest` **180 suites / 1947 tests / 0 失败 0 错误**，`PlaybackDiagTest` 已随框架消失，回到基线。
  - **测试版框架同日拔出（26.11.08）**：问题在真机确认修复后，按 `docs/archive/superpowers/specs/2026-11-07-test-build-framework.md` §5.2 把整套东西删干净——`util/TestBuild.kt`、`player/PlaybackDiag.kt`、`PlaybackDiagTest.kt`、`app/build.gradle` 的 `testBuildName` 段、详情页 `diag_play` 入口（布局里的按钮 + 两条 strings + `initView` 那段 + `onResume` 恢复）、`VideoInfoFragment.startPlay(diag)` 回退成 `playClick()`、`PlayerActivity` 里全部 `diag*` 字段与方法、`Logu` 的诊断期放大（`RING_MAX_CHARS_DIAG` / `setRingMaxCharsTemp` / `restoreRingMaxChars` / `ringMaxChars` / `trimRingLocked` / `ringSnapshot(int)`）、`ErrorCatch` 的带额度快照；**保留**的是修复本身（`DeviceProfile` 瘦身、`hardwareDecodeEnabled` 的默认硬解与「禁止再按档位软解」注释、`startupDefaultQn` 收敛）。配方留档在 `docs/archive/superpowers/specs/2026-11-07-test-build-framework.md`，文档挂点（`docs/agents/testing.md` §3.3、`docs/agents/build.md`、`docs/README.md`）改成「已拔出 + 去哪找配方」。
  - **真机验证（L2）**：26.11.08 用户装修复版测试包（`26.11.07-TEST-diag` + 本修复）后确认「问题修复了」，1GB 手表不再卡顿；「硬解会不会花屏」这个风险项没有出现。

- **26.11.08 「不看设置只看性能」同型 bug 批量修复（7 条）**：卡顿回归定位后按用户要求把同类问题一次找齐——`settings-audit` 逐键审计 140 个真实 SharedPreferences 键 × 消费方（死设置 / 默认值不一致 / 存取类型错配），`player-core` 审计「只按设备或环境判定、绕过设置」的分支与「程序反写设置」的路径。两份都是只读审计。**用户拍板「7 条一起修」**：
  - **① 弹幕行数被档位静默封顶（与解码 / 清晰度同源）**：`player/DanmakuManager.kt` 去掉 `DeviceProfile.capDanmakuMaxLines(...)`，改为直接 `SharedPreferencesUtil.getInt(SettingsKeys.PLAYER_DANMAKU_MAXLINE, 15)`；设置页默认值 `player/PlayerSettingsGroups.kt` 由 `"10"` 改成 `"15"` 与引擎对齐。诊断报告 245 里「用户设 50 行、实际只上 6 行」随之消失。**`util/DeviceProfile.kt` 与 `util/DeviceProfileTest.kt` 整份删除**——去掉封顶后它已零业务消费方（`private/` 无引用），这也是「档位」这个概念在代码里的最后一次退场。
  - **② 「高性能模式」在高档机关不掉**：`PerformanceManager.isEffectiveHighPerf()` 原为 `highPerformanceMode || currentPerfLevel == PERF_LEVEL_HIGH`，打分 ≥65 的机器上关掉开关什么都不会变。改为拆两个来源：`userSetHighPerf`（用户是否显式设过 `KEY_HIGH_PERFORMANCE_MODE`）与 `autoDetectedHighPerf`（新键 `KEY_PERFORMANCE_AUTO_DETECTED`），`isEffectiveHighPerf() = effectiveHighPerf(userSet, userEnabled, autoDetected)`，纯函数形式 `@JvmStatic fun effectiveHighPerf(...) = if (userSet) userEnabled else autoDetected` 可直接单测。
  - **③ 首启反写用户开关**：`detectAndApplyPerformanceLevel()` 不再在「用户没设过」时把 `KEY_HIGH_PERFORMANCE_MODE` 写成 `true`（那会让用户以为是自己开的），只写 `KEY_PERFORMANCE_AUTO_DETECTED`；`setHighPerformanceMode()` 里置 `userSetHighPerf = true`。`KEY_PERFORMANCE_AUTO_DETECTED` 此前定义了但全仓没读过。
  - **④ 设置页「高性能模式」默认值与实际不符**：`activity/settings/SettingGroupActivity.kt` 该开关第 4 参由写死 `false` 改成 `PerformanceManager.isEffectiveHighPerf()`（否则界面显示「关」而实际按高配跑）。两条提示文案改成「高性能模式已开启，列表与图片缓存会使用更高配置」「高性能模式已关闭，改用标准配置」——原文案 `msg_settinggroupactivity_008` 在**关闭**时提示「已切换到性能优先模式」，语义是反的；`007` 说「下次启动生效」，而参数全由 getter 现取，立即生效。
  - **⑤ 顶栏「显示标题 / 显示电量 / 显示时钟」三个开关是死的**：`PLAYER_UI_SHOW_TITLE/_BATTERY/_CLOCK` 自 26.11.06 加进设置页起就没有任何读取方（顶栏一直全显示，因为默认值与旧行为一致，所以没人发现）。在 `PlayerActivity.initUI()` 里按三项设置可见性，默认 `true`；「取不到电量时 GONE」的既有逻辑不动（开关只负责「用户不想看」）。
  - **⑥ 「翻动时不加载图片」界面默认与实际相反**：界面 `activity/settings/SettingPrefActivity.kt` 默认 `"false"`、消费方 `util/view/ImageAutoLoadScrollListener.kt` 默认 `true`，于是界面显示「关」而实际一直在省图。改界面默认值为 `"true"`，不改行为、只让界面说真话。
  - **⑦ 两条 Debug 解析报错开关同理**：`util/MsgUtil.java` 的 `dev_jsonerr_detailed` / `dev_recyclererr_detailed` 默认值由写死 `false` 改成 `ToolsUtil.isDebugBuild()`，与设置页默认值一致（原来 Debug 包里开关显示「开」、实际仍走简短提示）。
  - **附带：弹幕「速度」文案方向是反的**：`player_danmaku_speed` 实际喂给 DFM 的 `Duration` 倍率（`Duration.setFactor`：factor 越大停留越久 = 越慢），文案改成「弹幕停留时长」并补描述「数值越大弹幕走得越慢，1.0 为默认」。
  - **L0（26.11.08 实测，主工作区 HEAD=bf4dfef 直接构建——另一会话的在途改动已自行提交，工作区只剩本轮改动）**：`:app:testStandardDebugUnitTest :app:assembleDebug` **BUILD SUCCESSFUL in 1m 47s**（两 flavor 各 4 个 debug APK）；`testStandardDebugUnitTest` **203 suites / 2197 tests / 0 失败 0 错误**，`TEST-com.RobinNotBad.BiliClient.util.DeviceProfileTest.xml` 已随类删除而不存在；`npx --yes markdownlint-cli2 "AGENTS.md" "readme.md" "docs/**/*.md" "!docs/archive/**"` → 54 files / 0 issues。**人测项（L2，无法自动验证）**：①高档机把「高性能模式」关掉后列表图/Glide 缓存应确实降配（可用 `dumpsys meminfo` 对比）；②播放页关掉「显示标题/电量/时钟」后顶栏对应项消失、返回再进仍保持；③弹幕行数设 15 后低端机确实上到 15 行（观感与掉帧由用户判断）。

- **26.11.08 弹幕「时间戳空降」崩溃（线上 3 例 `ConcurrentModificationException`）**：`DanmakuFlameMaster/src/main/java/master/flame/danmaku/controller/DrawTask.java:202` 给 `getVisibleDanmakusOnTime(long)` 加 `synchronized`。它是**唯一从 UI 线程直接调用的读取入口**（`DanmakuView.getCurrentVisibleDanmakus()` → `DrawHandler` → 本方法），却要遍历 `danmakuList.subnew()` 返回的 `TreeSet` 子集视图；而弹幕列表的结构性修改全发生在持本对象锁的方法里（`addDanmaku` / `removeAllDanmakus` / `removeUnusedLiveDanmakusIn` / `draw`），不持锁就是**读写在同一个 `TreeSet` 上**。3 例都出现在弹幕量 > 5000 且点「弹幕时间戳空降」时。`CacheManagingDrawTask.prepareCaches()` 对同一调用的 try/catch 重试是缓存线程侧兜底，**不能替代**这里的互斥（那边是读读竞争，这边是读写）。
- **26.11.08 详情页「点赞卡片描边」开关（反馈 #112）**：动作区「点赞 / 投币 / 收藏」卡片改成 surface 底 + 1dp 主色描边后，描边色 `?attr/actionCardStroke` 在 B站粉与经典终端两套主题下都是 `#FF6699`，有用户觉得那圈粉边扎眼。
  - 新键 `SettingsKeys.DETAIL_LIKE_CARD_RING = "detail_like_card_ring"`，判据 `DetailEntryPrefs.isLikeCardRingEnabled()`（`DEFAULT_LIKE_CARD_RING = true`，默认开＝不改老用户观感）；设置项在「设置 → 内容与浏览 → 详情页设置」，同步收进 `SettingsIndex` 搜索索引；文案 `setting_detail_like_card_ring` / `desc_setting_detail_like_card_ring`。
  - 调用点两处：`activity/video/info/VideoInfoFragment.kt`（`if (!DetailEntryPrefs.isLikeCardRingEnabled()) likeCoinFavCard.strokeWidth = 0`）与 `adapter/article/OpusContentAdapter.kt`（文章 / 动态末尾 `cell_article_end` 的 `R.id.like_coin_fav`）。**两处必须一起判断**，否则会出现「视频页关了、文章页还留一圈」。关掉只把 `strokeWidth` 归零，底色（`?attr/actionCardColor`）与文字色都不动。
  - 顺带修掉 `util/DetailEntryPrefs.kt` 里未闭合的 KDoc（缺 `*/`）——原状态整文件编译不过，这是本批能落地的前提。
- **26.11.08 表情页标题栏黑带（反馈 #113）**：`activity/EmoteActivity.kt` 删掉 `tabLayout.setBackgroundColor(ColorScheme.getPlayerBg(this))`。那是**播放器播放区**的底色，7 套主题里恒为 `0xFF000000`（亮色主题也照抄暗色表），于是知乎蓝这类浅底主题下标题栏成了一条黑带（默认主题看不出来，因为页面底色本身就是黑的）。改成不设背景、继承页面主题底色，与全工程另一个 `TabLayout`（`activity/UpdateHistoryActivity.kt`）一致。
- **26.11.08 教程管理页左上角图标改正**：`res/layout/activity_tutorial_manager.xml` 的 `app:drawableStartCompat` 由 `@drawable/arrow_down` 改成 `@drawable/arrow_back`——那个位置的语义是「返回上一级」，此前画的是向下箭头。
- **26.11.08 合集页进入时定位到上次观看的那一集（反馈 #105 / #108）**：
  - 新增 `player/CollectionResumeStore.kt`：`CollectionResumePolicy.resolveTargetAid(fromAid, seasonId, record, memberAids)`（纯函数，判「有记录且在列表上 / 记录失效回落入口」）、`CollectionResumeCodec`（存档格式 `seasonId\taid\tupdatedAt`）、`CollectionResumeStore.save/read`，键前缀 `collection_resume_v1_`；单测 `app/src/test/java/com/RobinNotBad/BiliClient/player/CollectionResumeStoreTest.kt`（用共享 `FakeSharedPreferences`）。**seasonId 取 `ugc_season.id`（= `Collection.id`），不是 `PlayerData.seasonId`**——后者是番剧的季。
  - `activity/video/collection/CollectionInfoActivity.kt`：定位目标改由 `resolveResumeAid()` 决定；`CardAdapter.findCardPosition` / `SectionAdapter.findEpisodePosition` 各自按 `getTypes` 的排布算下标（position 0 是合集信息卡），**两个分支都定位**——此前只有 `sections` 分支会滚，从「收藏的合集」那种只有 `cards` 的形态进来永远停在顶部。`scrollToPosition` 放在 `setAdapter` **之后**（数据就位再登记，不依赖布局时序）。`displayedAids()` 取列表上真实可见的条目，**不用 `Collection.toPlaylist()`**：后者会剔掉缺 cid 的条目，会让这些条目上的记录静默失效，功能看起来像没生效。
  - `VideoInfoFragment` 在首播教程 / 数据未就绪等早退分支**之后**、三条起播分支之前 `CollectionResumeStore.save(collection.id, videoInfo!!.aid)`——没真起播就不留记录。
- **26.11.08 顶栏灵动岛（新功能，26.11.08 当天解耦为独立开关，默认关；26.11.09 已恢复总闸）**：
  - 分层：`util/IslandState.kt`（`IslandState` 枚举 TIME / PLAYING / DOWNLOADING / UNREAD / BATTERY / ANNOUNCEMENT + `IslandSnapshot`）、`util/IslandStatus.kt`（状态源：后台播放读 `PlaybackService.isBackgroundPlaying()`、下载读 `DownloadService` 已启动 + `activeDownloadsCount` + percent、未读、电量 15 秒一次、更新检查 60 秒一次；瞬时事件 `BURST_MS = 10_000` 并用 `xxxArmed` 保证同一件事只弹一次）、`ui/widget/IslandPolicy.kt`（纯函数 `ongoingState` / `decide`：瞬时事件优先，持续事件按 播放中 > 下载中 > 未读，每显示 30 秒让 5 秒给时间，没有事件恒显示时间；`EVENT_SLICE_MS = 30_000` / `TIME_SLICE_MS = 5_000`）、`ui/widget/TopbarIsland.kt`（只做绘制与绑定）。单测 `ui/widget/IslandPolicyTest.kt`。
  - 挂点：`BaseActivity` 在 `onStart` 与 `asyncInflate` 回调里 `TopbarIsland.attach(this)`（后者页面此时才有 `R.id.top`），`onStop` / `onDestroy` 一律 `detach`——手表上不留每秒唤醒；`ui/widget/TextClock.kt` 新增 `@Volatile var externallyManaged`，`startTick()` 里直接 return，否则亮屏 / 重新可见会把事件文字覆盖回时间；`api/TerminalApi.java` 的 `checkAnnouncement` 在有新公告时 `IslandStatus.markAnnouncement()`（启动线程只置标记，不碰 UI）；`service/PlaybackService.kt` 暴露 `@JvmStatic fun isBackgroundPlaying()`。宽度 **72dp 全局固定**、按屏宽 0.31 封顶（26.11.10 用户拍板，同设备所有页面/状态一致，不再按标题空档自适应）；文字前加 12dp 状态图标（播放 / 下载 / 未读 / 电池 / 闪电 / 公告 / 更新），电量状态直接显示百分比、充电时闪电慢闪；1 秒 tick，绑定用 `WeakHashMap` 弱引用持 Activity，**只认顶栏里的 `@id/timeText`**（视频详情页发布时间、文章末尾时间戳也叫这个 id）。
- **本轮 L0（26.11.08 实测，本机已注入 `private/`，HEAD 为提交后的 `main`）**：`:app:assembleDebug` BUILD SUCCESSFUL（两 flavor 各出 APK）；`testStandardDebugUnitTest` 与 `testCoexistDebugUnitTest` 各 **202 suites / 2188 tests / 0 失败 0 错误**。**未逐步重跑**：6 组改动按「文件 / 模块互不重叠」切分（弹幕库 → 详情页开关 → 表情页 → 布局 → 合集 → 灵动岛），只跑了最终态；逐步可编译性是推断而非实测。真机未验项见 §2.1 同名四条。

- **26.11.08 控制台反馈清账（按源码逐条核实后批量标记已处理）**：控制台 32 条 pending 全部过了一遍源码，标 **18 条**为已处理——#112（`util/DetailEntryPrefs.isLikeCardRingEnabled`，两处调用点）、#113（`EmoteActivity` 不再刷 `getPlayerBg`）、#114（`activity_tutorial_manager.xml` 的 `drawableStartCompat` 改 `arrow_back`）、#105/#108（`player/CollectionResumeStore` + 合集页定位）、#103（表冠三处加固）、#97（设置页 `search_input` 在 `RotaryScrollView` 内随内容滚动）、#94（`LocalListActivity` 多选 / 拖动排序 / 搜索 + `SettingsKeys.PLAYER_SWIPE_SEEK_SCALE` 灵敏度）、#92（`item_hot_search.xml` 走 `MarqueeTextView`）、#91（`util/DownloadWakeLock` + 下载页常亮）、#41（`player/AudioSeekVolumePolicy` 静音 seek 校正）、#31（`PlayerScreenMetricsPolicy`）、#106/#104（「继续播放」进菜单配置，可排序可关且默认关）、#110（表冠「系统优先 + App 补位」，框架滚过就吞事件故不再叠加；**真机待复测**，见 §2.1）、#116（控制台已回复「已定位并修复」，对应 `1a66c0c`「档位不再干预解码方式」）、#126（`473fd5a` + `fb2c4fb`：Set-Cookie 合并不再抹掉 SESSDATA、刷新失败不再清登录态，正是「界面显示已登录、请求却回 -101」的那条机制）、#93（官网捐赠入口在站点仓库 `bili-terminal-site` 的 `a3748e8`，`index.html` 导航与页脚各一处链接）。**保留 pending 的 14 条与逐条理由记在 §2.5**，避免下次重复排查。

- **26.11.08 播放器宽屏布局回归（宽屏下底栏变粗、按钮簇被摊开、竖列被放大；对比 26.11.02 明显）**：
  - **现象**（用户 26.11.08 报「宽屏下操作按钮被拉伸，底栏被拉伸，错位，对比 11.02 版本即可看出区别」）：同机同配置实测，底栏 `bottom_control` 59dp → 94dp；底部按钮 32×37dp → 44×44dp、相邻 6dp → 12dp；按钮簇宽 146dp（居中）→ 188dp 且中点左偏 22dp；进度条 20dp → 48dp；左右竖列格 28×28dp → 48×48dp（列高 84 → 144dp）；字幕常量没跟栏高走，字幕底边压进按钮行 18dp。
  - **根因**：`6591f05`（26.11.07「播放器控制键热区放大」，即上方 26.11.07 热区条目②）把热区放大时只在 466×466@320dpi（233dp）手表上量过，宽屏一次没量。三处把「手表可用高度小、会被夹住」当成了不变式：①`app/src/main/res/layout/activity_player.xml` 的 `videoprogress` 高度改 `@dimen/touch_min`(48dp) + 上下各 14dp 内边距（波形本身没变，只把灰条撑粗）；②底部按钮行改成两条 `layout_width="0dp"` + `weight="1"` + `fillViewport` 的 `HorizontalScrollView`（运行时把**不可见视口**撑成半屏，`gravity=end/start` 让按钮簇虽不变形却不再整体居中）；③`player/PlayerButtonSlots.fitColumnsToAvailableHeight()` **只放大不缩小**，宽屏把竖列吃满 `PlayerControlCellSize.MAX_DP = 48f`。**「拉伸」里唯一的真形变是「更多」按钮**：`moreButton()` 先清 padding 再调 `cellParams()`，后者按图标尺寸把 padding 写回，FIT_XY 的方形 `morehide` 被画进 32×38（竖向 1.19×）。**图标本身没变形**——像素比对 `outputs/cmp_measure.py`：quality 64×42 vs 64×43px、sound_cut 78×70 vs 78×71、video 31×40 vs 31×41、danmaku 68×60 一致
  - **修法**（用户拍板：全设备统一回 11.02 尺寸、**不做屏宽分支**；按钮整行作为整体居中）：①`activity_player.xml` 进度条回 `20dp`、删掉上下 14dp 内边距（横向 14dp 保留）；②`bottom_buttons` 内改成 `bottom_scroll`(match_parent `HorizontalScrollView`, `fillViewport`) → `bottom_row`(`wrap_content` + `gravity="center"`) → `bottom_left_slots` + `button_video`(32×37dp, 左右 margin 3dp) + `bottom_right_slots`，删掉 `bottom_left_scroll` / `bottom_right_scroll` 两条半屏滚动条（代码里没有 findViewById 引用）；③`PlayerActivity.initButtonSlots()` 的 bind 参数：左右列 `48f,28f,28f,28f` → `28f,28f,28f,28f`，底部 `44f,44f,32f,37f` → `32f,37f,32f,37f, marginHorizontalDp = 3f`（相邻 6dp）；④`player/PlayerButtonSlots`：`Region` 加 `marginHorizontalDp`、`cellWidthDp`/`cellHeightDp` 改 `val`，删掉 `scheduleColumnFit()` / `fitColumnsToAvailableHeight()` 与 `lastLayout` / `fitting`（不再按可用高度放大竖列，渲染一帧定形），`cellParams()` 按 `marginHorizontalDp` 写左右 margin，`moreButton()` 改成先 `cellParams` 再清零 padding；⑤**删除 `player/PlayerControlCellSize.kt` 与其 11 例单测**（`MIN_DP`/`MAX_DP`/`cellSizeDp`/`columnAvailableHeightPx` 已无消费方）
  - **L0（26.11.08 实测，主工作区 HEAD 直接构建）**：`:app:assembleStandardDebug :app:testStandardDebugUnitTest` BUILD SUCCESSFUL；`:app:testCoexistDebugUnitTest` BUILD SUCCESSFUL（43s）。两 flavor 各 **202 suites / 2186 tests / 0 失败 0 错误**（`PlayerControlCellSizeTest` 的 11 例随类删除）
  - **L1（AVD `Medium_Phone_API_37.0` = 1080×2337 内容区 @420dpi ≈ 411dp，standard arm64 debug 包，本地 mp4 + `DebugBridge` 拉起 `PlayerActivity`，`uiautomator dump` 量 bounds；与同机同配置的 11.02 实测逐项对照）**：底栏 `bottom_control [0,2182][1080,2337]` = 59dp（11.02 相同）；`videoprogress [0,2187][1080,2240]` = 20dp（相同）；`bottom_buttons` = 37dp；`button_quality [348,2240][432,2337]` = 32×37dp、`sound_cut` 448..532、`button_video` 548..632、`sound_add` 648..732（相邻 16px = 6dp，整行 348..732 = 146dp，**两版逐像素同位置**）；`right_control [995,65][1069,2182]` = 28dp 宽、列格 28×28dp（3 格 84dp）、`lock_btn` 28×28dp，与 11.02 完全一致。**手表尺寸回测**（`wm size 466x466` + `wm density 320` = 233dp）：底栏 59dp、进度条 20dp、按钮行 37dp、按钮 32×37dp、整行 146dp 居中、列格与 `lock_btn` 均 28×28dp——146dp 远小于 233dp 屏宽，不需要横向滚动。底栏区域像素差（Pillow `ImageChops`，对照图 `outputs/cmp_bottombar_1102_vs_fixed.png`）mean abs diff 3.43/255，肉眼只剩播放键水波纹按住态与进度条 thumb 的抗锯齿差异
  - **未验证（L2，需人判）**：①底部按钮放满、需要整行横向滚动时的手感与是否仍居中（411dp / 233dp 两种屏本轮都没塞满）；②横屏 / 分屏 / 平板（>600dp）看画面；③「更多」按钮只在槽位溢出时才渲染，本轮没构造出溢出场景，1.19× 拉伸的修复未在画面里确认

- **26.11.08 菜单里「扫一扫」关不掉、永远贴在菜单最底下（控制台反馈 #118）**：
  - **现象**：用户在「菜单顺序」设置里关掉「扫一扫」，回到主菜单它仍然在，而且固定贴在「退出」下方（菜单最底下）；原文「菜单顺序设置里无论是否选择显示扫一扫 扫一扫都会在菜单最底下显示」（26.11.07 / 三星 `fresh7ul` / 注入私有功能的构建）。
  - **根因**：`app/src/main/java/com/RobinNotBad/BiliClient/util/MenuConfig.kt` 的 `loadStoredEnabled` 把「严格 `parse` 成功 **且** `OPTIONAL_ITEMS.all { it in parsed }`」当合法判据——用户关掉私有项之后存的 `menu_enabled` 必然缺这一项，判非法 → 落进容错分支；而容错分支里写着 `lenient + OPTIONAL_ITEMS.filter { it !in lenient }`，**每次读取都把缺的可选项无条件追回末尾并写回**。于是「关掉」不生效，又因为追加在 `exit` 之后，它永远显示在菜单最底下。私有侧不读写菜单配置（`FeatureHolder.menuKey()` 只返回 `"scan_qr"`），问题全在公共侧。
  - **修法**：可选项改成与 `exit` / `bangumi` 同一套「标记 + 规范位置回填」——①`util/SharedPreferencesUtil.java` 新增 `MENU_OPTIONAL_STATE_PREFIX = "menu_optional_state_"`，`MenuConfig.saveEnabled` 给每个可选 key 写 `"1"`（启用）/ `"0"`（用户自己关掉），拼键走 `MenuConfig.optionalStateKey(key)`；②合法性判据放宽成 `optionalItems.all { it in parsed || savedOptionalState(it) }`，容错分支只做「丢未知 key / 去重」，**不再追加可选项**；③新增 `ensureOptionalItems()` 接管回填——只补「既不在列表里、标记也从没写过」的项，位置用 `insertAtCanonicalPosition` 按 `DEFAULT_ENABLED` 算，不再扔到末尾；④`loadEnabled` / `saveEnabled` 都加 `optionalItems: List<String> = OPTIONAL_ITEMS` 参数（`@JvmOverloads` 保住 `SharedPreferencesUtil.java:197` / `:211` 的旧调用），`ALL_ITEMS` / `DEFAULT_ENABLED` / `parse` / `parseLenient` / `resolveEnabledList` 同时接受这份列表，避免注入时只改一半。
  - **同族排查**：`ensureExit` / `ensureBangumi` 本来就是「`menu_*_state` 标记 + `insertAtCanonicalPosition`」，只有可选项还留在旧的「无条件补齐」路径上；公开构建 `OPTIONAL_ITEMS` 为空，行为不变。
  - **L0（26.11.08 实测，本机已注入 `private/`）**：`:app:assembleDebug`、`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 全部 BUILD SUCCESSFUL，两 flavor 各 **202 suites / 2195 tests / 0 失败 0 错误**。`MenuConfigTest` 新增 7 例（关掉假可选后不再补回 / 关掉**真实**私有项后不再补回 / 老数据无标记仍回填且落在「设置」之后 / 用户启用过的留在原位 / `saveEnabled` + `loadEnabled` 端到端复现 #118 / 另一个新可选仍能回填 / 保存写入标记），该类 49 例全绿。
  - **L1（26.11.08 真机实测：vivo V2229A / Android 16，注入私有功能的 coexist debug 包，`uiautomator --compressed dump` 读控件树、`run-as` 读 `shared_prefs/default.xml`）**：先用 `run-as` 直接构造「用户已关掉」的存档（`menu_enabled` 里没有 `scan_qr`、`menu_optional_state_scan_qr=0`）——主菜单只剩 12 项、**没有「扫一扫」**（旧版这里必然出现且贴在「退出」下面），读盘后 `menu_enabled` **未被改写**（不再被强制补回），`am force-stop` 重启后仍然没有；「菜单设置」页的「未启用」区里确实能看到「扫一扫」（入站必刷 / 全站排行榜 / 直播 / 时间线 / 扫一扫 / 继续播放）。再删掉标记键模拟老数据：启动后「扫一扫」出现在「设置」与「退出」之间，`menu_enabled` 被改写成 `…;settings;scan_qr;exit`（插在规范位置，而不是旧版的追到最末）。验证完已把存档恢复成用户原顺序（热搜 → 扫一扫 → 搜索）。
  - **未验证（L2，需人拿手指判手感）**：①真的在「菜单顺序」里**长按拖拽**把「扫一扫」拖进未启用区（adb 的 `input draganddrop` 在这台机器上会被当成点击、拉不起 `ItemTouchHelper` 拖拽，所以 L1 那轮是用存档等价构造的）；②重新打开后它落在哪里、拖拽其它项改过的顺序在重启后是否保持。
- **26.11.08 反馈 #118 的容器语义（给后来改 `MenuConfig` 的人）**：`optionalItems` 参数是**替换**而不是追加——传进去的列表必须包含真实私有 key，否则 `parse` 会把它当未知 key 过滤掉（单测里因此写成 `MenuConfig.OPTIONAL_ITEMS + fakeOptional`）。JVM 单测里 `OPTIONAL_ITEMS` 是否为空取决于本机有没有同步 `private/`，断言别写死。

- **26.11.08 视频卡片长按改成「更多操作菜单」（新功能，用户需求：「长按视频卡片，可以弹出更多操作菜单，而不是直接弹出缓存下载」）**：
  - **旧行为**：`VideoCardAdapter` / `HistoryVideoCardAdapter` / `UserVideoAdapter` 在「页面没有注册专属长按」时把长按**直接**交给 `VideoQuickCache.handle()`——手指一按住就跳出缓存清晰度选择页或直接起下载，列表里做不了别的事。
  - **新行为**：回落路径改成弹菜单。新建 `app/src/main/java/com/RobinNotBad/BiliClient/adapter/video/VideoCardMenu.kt`：`object VideoCardMenu` + `enum class Action { WATCH_LATER, FAVORITE, CACHE, VIEW_COVER, REDUCE_RECOMMEND }`（声明顺序＝显示顺序）、纯函数 `actionsFor(videoCard, reduceRecommendAvailable)`、渲染入口 `show(context, videoCard, reduceRecommendAvailable = false, onReduceRecommend = null)`（走 `TerminalDialog.menu`，无动作可给时返回 false 不弹）。五项：稍后再看（`WatchLaterApi.add`）/ 收藏（`AddFavoriteActivity`，只带 `aid` extra）/ 缓存到本地（沿用旧 `VideoQuickCache.handle`，链路不变）/ 看封面（`ImageViewerActivity` 的 `imageList` extra，卡片无 `cover` 时这一项不出现）/ 减少推荐（仅推荐流出现）。
  - **菜单判据**：只有普通投稿卡（`type` 为 null / 空串 / `"video"`）才给动作。番剧卡片的 `aid` 装的是 media_id、直播卡片是 room_id，拿它们调稍后再看/收藏只会得到 -400，所以 `actionsFor` 直接返回空、长按不弹任何东西（旧行为是弹一句「番剧暂不支持快速缓存」）。私有功能产出的卡片仍由 `OptionalFeatures.skipQuickCache` 在调用方挡掉。
  - **页面专属长按仍然优先**（历史记录删除、稍后再看移除、收藏夹多选等一律不动）；没有专属长按的浏览列表（推荐 / 排行 / 搜索 / UP 主空间 / 缓存列表）统一走新菜单。
  - **设置项改名与口径**：`cache_quick_mode` 文案「启用快捷缓存方式」→「启用长按快捷操作」（描述同步改写，点明菜单四项、专栏卡仍直接缓存、关闭后长按无响应）；`activity/settings/SettingText.kt` 的搜索索引用字面量键，跟着改。**`cache_default_quality`（默认缓存画质）不再跟着这个开关隐藏**——它还被收藏夹批量下载读（`util/FavoriteAggregatePolicy.kt`、`activity/user/favorite/FavoriteVideoListActivity.kt:966`），关掉长按快捷操作的人照样要设画质。
  - **专栏卡片按用户拍板保持原样**：`adapter/article/ArticleCardAdapter.kt` 长按仍是直接缓存，只把写死的 `"cache_quick_mode"` 换成 `SettingsKeys.CACHE_QUICK_MODE`。
  - **「减少推荐（不感兴趣）」**（参考 [bilibili-margin](https://github.com/jiuyang5354/bilibili-margin) 的做法，只对首页推荐流开放）：`model/VideoCard.java` 追加 `trackId`（`track_id`）与 `upMid`（`owner.mid`）两个字段，Parcel 读写**一律追加在最末尾**（:100-101 / :137-139）；`api/RecommendApi.java` 的推荐流解析补取这两个值（原有「goto≠av 或无 bvid 丢弃」逻辑不动）；新建 `api/FeedbackApi.java`——`POST https://api.bilibili.com/x/web-interface/feedback/dislike`，表单 10 字段（`goto=av`、`id=<aid>`、`mid=<upMid>`、`track_id`、`reason_id`、`app_id=100`、`platform=5`、`feedback_page=1`、`from_spmid=`、`spmid=333.1007.0.0`）+ 运行时追加 `csrf=NetWorkUtil.currentCsrf()`，**不签 WBI**（写了注释说明为什么）；`activity/video/RecommendActivity.kt` 给 adapter 注入 `onReduceRecommend`，`code==0` 时按 `list.indexOf(card)` 重算下标再 `removeAt` + `notifyItemRemoved`（卡片已被刷新换掉时 indexOf 返回 -1，只提示不动列表）。撤销接口 `FeedbackApi.cancelDislike` 已备好但暂无调用方。
  - **单测**：`adapter/video/VideoCardMenuTest.kt` 7 例（普通视频信息流五项 / 非信息流四项 / 封面为空时不给「看封面」/ 空 type 当普通视频 / live 与 media_bangumi 与未知 type 一律空）；`api/FeedbackApiTest.java` 6 例（字段齐全且不含 csrf、id 传 aid、reason_id 用传入值、trackId null→空串、固定埋点常量、mid）。
  - **L0（26.11.08 实测，本机已注入 `private/`，HEAD=`d90dc30`，`--rerun-tasks` 强制全量重跑而不是吃缓存）**：`:app:assembleDebug :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest` **BUILD SUCCESSFUL in 1m 57s**（135 actionable tasks 全部 executed，两 flavor 各 4 个 debug APK）；`testStandardDebugUnitTest` 与 `testCoexistDebugUnitTest` 各 **205 suites / 2217 tests / 0 失败 0 错误**。`docs/FEATURES.md`、本文件、`AGENTS.md` 全量 `npx --yes markdownlint-cli2` 0 错误。
  - **未验证（L2，见 §2.1）**：菜单在 192–233dp 手表宽度上的排版与手感、长按后抬手是否仍不误跳详情页、「减少推荐」是否真的改变后续推荐（要登录态 + 真实推荐流）、未登录时两条登录拦截的提示。

- **26.11.09 稳定版已排定（11-09 00:00 由 CI 自动发，tag `26.11.09`）**：`app/build.gradle` 的 `appVersionDate` 已是 `26.11.09`（`versionCode 26110900`）；`strings.xml` 的 `update_log_current` 换成 31 条＝锚点「【26.11.09 本次更新】」+「本次更新为稳定版，请大家放心更新」+ 编号 1–29 连续，26.11.07 那组整段归档进 `update_history_log` 的「## 2026-11-07」（提交 `62551855`，只动 `strings.xml` + `app/build.gradle`，**未构建、未打 tag**）。发版方式：`.github/workflows/release-once-26.11.09.yml`（`cron: '0 16 8 11 *'`＝+08:00 零点；另有 `workflow_dispatch` 的 `dry_run` 供演练）自己打 tag 再显式 `workflow_dispatch` 触发 `build-release.yml`——**`GITHUB_TOKEN` 推的 tag 不会触发 workflow**，所以不能只靠 `push: tags`（官方例外只有 `workflow_dispatch` / `repository_dispatch`）；门禁里跑一遍 `strings.xml` 锚点校验，缺了直接 `::error::` 不出包。dispatch 之后的确认改成 6×15 秒轮询 runs（`66d79a68`，原先单次 `sleep 20` 会假红）。发完应删除该 yml；`26.11.09` 若已占用则只能换 `26.11.09.1`（仓库开着 release immutability）。
- **26.11.08 搜索筛选 / 伪装分组 / 下载图标换成矢量（`bb741947`）**：`drawable/icon_sort_filter.xml` + `drawable-v21/icon_sort_filter.xml`（Material Symbols tune，靠 `android:tint` 取正文色：v21 走 `?android:attr/colorForeground`，API 19/20 走 `@color/text_primary`）替掉原先两份手写 PNG（`drawable-nodpi` 暖白 `#EBE0E2`、`drawable-notnight-nodpi` 深色 `#3A3438`）——亮暗共用一套、改图标只改一处，且原 PNG 在亮色搜索框上几乎不可见；该名已登记进 `tools/appearance/gen_light_mode.py` 的 `EXCLUDE`。设置页「伪装与安全」分组由借用 `icon_person` 改成专用 `icon_disguise`（`#ebe0e2` 正本 + gen 脚本生成的两份亮色版）；`drawable{,-v21}/icon_download.xml` 的 pathData 换成同一套图形。L0：`:app:assembleDebug` **BUILD SUCCESSFUL in 47s**（两 flavor 各 4 个 debug APK），`markdownlint-cli2` 56 files / 0 issues。**未验证（L2）**：亮色模式下排序图标是否确实可见、伪装分组图标与隔壁分组图标的粗细/颜色是否协调、下载图标在新旧主题下的观感。另：改动期间 `tools/appearance/gen_light_mode.py` 整文件被编辑器转成 CRLF（`git diff` 显示 642 行、实际只改 2 行），提交前已转回 LF。

- **26.11.08 弹幕重复显示两次（反馈 #16）**：`player/DanmakuStreamLoader.prepare()` 一进来就把 `managerRef` 挂上，而它要**同步**拉首窗口（网络，几百毫秒到几秒）。这段窗口里播放页的进度回调已经在按 250ms 调 `ensureWindow`，于是「正在拉的首窗口」被当成缺段又拉一遍，走 `appendSegments` 进待灌队列；等 DFM `prepared()` 时它和 parser 里的首窗口一起灌进去 —— 同一批弹幕被画两次（`DanmakuManager.appendSegmentInternal` 与 parser 各一份，`DanmakuManager` 不去重，类注释早就写着「重复追加会看到重影」）。
  - **修法**：新增独立的 `@Volatile windowReady` 作为「窗口加载开关」，只有首窗口真正交给 DFM 之后才置真；`prepare()` 开头、`attach()`、`release()` 一律复位为假。`ensureWindow` 以它（而不是 `managerRef` 是否为空）为唯一闸门——`prepare` 期间与失败回退后都不补拉，状态显式可读，也不再和 worker 取 manager 的引用绑在一起。另：`DanmakuManager.configureAndPrepare` 开头把 `DanmakuState.isPrepared` 复位，避免同一实例再次加载失败时 `appendSegments` 往旧 parser 里灌新分段（串台）；`generation` 在 26.11.07.1 加代际号时就已经是 `@Volatile`。
  - **触发面（按实际时序）**：切 P / 互动分支是 `bindDanmakuView() → setDisplay() → postDelayed 60ms → loadDanmakuWithGuard()`（`PlayerActivity.kt:6057/6059/6073`、`:6615/6617/6631`），播放器可能先于首窗口返回就绪，这是本缺陷的命中路径；首播是同一个后台块里先 `loadDanmakuWithGuard()`（`:1178`）再 `setDisplay()`（`:1208`），播放不会早于首窗口返回，但 `prepare` 期间同样不该放行 `ensureWindow`，本修复一并覆盖。pending 队列解决的是「DFM `prepare` 异步、追加早于 `prepared()`」，与播放器/弹幕谁先无关。
  - **本机无 Android SDK（`ANDROID_HOME` 未设置、无 `local.properties`）**：只做静态自查；编译与两 flavor 单测由 PR #23 的 CI `build-and-test` 覆盖。

- **「关注的人」头像条出现黑色边框（26.11.09，用户报「自定义背景下关注界面的关注的人列表头像有黑色边框」，补充「只有滑动时出现，其他页面没有」）**：用户说的「关注界面」横条 = 动态页顶部「最近更新的UP主」（`cell_recent_up_list` 的 portal `up_list` → `RecentUpAdapter` → `cell_up_avatar`），每格用圆形 `MaterialCardView` 包住头像。这张卡不写背景也不写描边，全部继承主题 `materialCardViewStyle` → `CardStyle`：`cardElevation 4dp`、`strokeColor @color/divider_dark`、`strokeWidth 1dp`、`cardBackgroundColor @color/card_dark`（`CardStyleLight` 是 2dp / `@color/divider_color`），而 divider 色**每套主题一份**（`values/colors.xml` 的 `divider_color` / `divider_dark` 与 zhihu、iqiyi、purple_fantasy、rainbow、classic_gray 各一对）——用户「边框颜色推测由主题决定」在代码里有对应物。但**描边这条早有主**：`app:strokeWidth="0dp"` 自 `a7fb06be`（2026-10-30）起就写在这份布局里（`HEAD~15` 也有），即用户装的那版描边已归零、暗边仍在，剩下的源头只有 `cardElevation`（全仓无 `setStrokeColor` / `setStrokeWidth` / `setCardElevation` 调用，阴影是静态绘制的）。修法：`app:cardElevation="0dp"`。另有一条仍待真机确认：卡片从 style 继承 `clickable` / `focusable` = true，而点击挂在外层 `LinearLayout`（`RecentUpAdapter` 两处 `setOnClickListener`），按下态可能抬 `translationZ` —— 用户报的「只在滑动时出现」可能来自这里，若复验仍有残留就把这张卡换成 `FrameLayout` + `ImageView`（`updateIndicator` 保持 `layout_gravity="top|end"`）彻底消源。L0：本机 `:app:assembleDebug` BUILD SUCCESSFUL（26.11.09 实测，head `f843aa21`）。**未验证**：真机看画面——自定义背景 + 暗色/亮色主题下，横条静止与手指划过时都不再出现暗边（L2，依据是画面），验收步骤见 `docs/review/manual-test-guide-261109-avatar-shadow.md`。

- **「动态页作者旁有灰边」第三次定位：不是阴影，是卡片填充色（26.11.10，用户报「之前的提交没修好」）**：
  - **现象（用户截图实测）**：动态页顶部「最近更新的UP主」横条每一格，56dp 圆头像的**四角**露出一个与主题卡色同色的圆角方块。按像素量：绿圆 bbox 56dp；粉「有更新」圆点 12dp、距卡片上/右各 2dp（＝`updateIndicator` 的 12dp + 2dp margin，确认这一格就是 `cell_up_avatar`）；灰方块与圆同 bbox、圆角 ≈10–14dp（≈圆角档 12dp）。
  - **根因**：`app/src/main/java/com/RobinNotBad/BiliClient/ui/appearance/AppearanceApplier.kt:108-117` 的 `applyRadius()` 在圆角档为 ROUNDED（或装了自定义字体）时，把**全树所有** `MaterialCardView` / `CardView` 的 `radius` 改成 `card_round_large`(12dp) / `card_round`(6dp)。它把 `cell_up_avatar.xml` 里「只为把方头像裁圆」写的 `app:cardCornerRadius="28dp"` 一起覆盖掉，卡片从圆变圆角方卡，于是卡片自己的 `cardBackgroundColor`（`@color/card_dark`，玻璃主题下是半透明 `glass_card_pink`）就在圆头像四角露出来——用户说的「灰边」。
  - **纠正前两条结论**：`f843aa21`（压 `cardElevation`）与 `7bf18489`（改关注页两处抬高）治的都是**阴影**，本问题残留的是**卡片填充色**，所以 elevation / 描边怎么调都不会消失；「只有滑动时出现」也不是按压态 `translationZ`。
  - **修法**：`app/src/main/res/layout/cell_up_avatar.xml` 去掉 `MaterialCardView`，改成 `FrameLayout` 56dp + `android:background="@drawable/bg_avatar_circle"` + `android:clipToOutline="true"` + `android:outlineProvider="background"`（与 `cell_user_list.xml` 同一做法）；新增 `app/src/main/res/drawable/bg_avatar_circle.xml`（全透明 `oval`，只提供圆形轮廓，不上色）。顺带把 Glide 的 `placeholder` / `error` 方图也裁圆了——`GlideUtil.requestRound` 的 `circleCropTransform()` 不作用于 placeholder。头像本身仍由 `GlideUtil.requestRound` 圆裁；`avatar` / `updateIndicator` / `name` 三个 id、点击位置、更新点显隐逻辑全不变。
  - **防回归**：新增 `app/src/test/java/com/RobinNotBad/BiliClient/ui/AvatarCellLayoutTest.kt`（纯 JVM 读 `res/`）——断言 `cell_up_avatar.xml` 内**不含任何 CardView**、圆由 oval 轮廓 + `clipToOutline` 提供且轮廓源全透明、三个绑定 id 仍在。
  - **给后来者的硬约束**：这个工程里**用 CardView 做「圆形裁剪」必然失效**，任何布局显式写的 `cardCornerRadius` 都会被圆角档覆盖（机制见 `docs/architecture-map.md` §8.7.1）。要圆形就用 `FrameLayout` + oval 轮廓 + `clipToOutline`，或 `@drawable/` 形状 + `ShapeableImageView`。
  - **L0（26.11.10 本机实测，head `34ecdd5a` + 本改动）**：`:app:assembleDebug` BUILD SUCCESSFUL（两 flavor 各 4 个 debug APK）；`:app:testStandardDebugUnitTest` 与 `:app:testCoexistDebugUnitTest` 各 **211 suites / 2262 tests / 0 失败 0 错误**（基线 210 / 2259，+1 套 +3 例 = `AvatarCellLayoutTest`；计数按仓库口径用 Python `xml.etree` 从 `app/build/test-results/<flavor>DebugUnitTest/TEST-*.xml` 求和）。
  - **未验证（L2，依据是画面）**：真机看画面——自定义背景 + 暗/亮各一套主题下，横条每格头像四角不再有方块色、头像仍是圆的、粉点位置不变、点击仍能进用户空间。步骤见 `docs/review/manual-test-guide-261109-avatar-shadow.md` 的 H05（已按新根因重写）。

- **同一类阴影暗边的第二处：关注页列表项去掉 2dp 抬高（26.11.10，排查反馈 #17 时同案发现）**：`cell_user_list.xml`（关注 / 粉丝列表，另被搜索用户页 `SearchUserFragment`、充电鸣谢 `SponsorActivity` 复用）原写 `android:elevation="2dp"`，`cell_follow_group.xml`（关注分组标题行）原写 `app:cardElevation="2dp"` —— 全 App 只有关注页给列表项抬高（其它列表项一律 0），而 `cell_user_list` 又是全仓唯一手绘不透明卡底的列表项（`@drawable/bg_card_rounded` 是 `solid @color/card_dark` + 12dp 圆角，**没有 stroke**，全库仅它一个使用者），阴影沿卡片轮廓投在自定义背景的照片上就是一圈暗边；卡色随主题变（`card_dark` 暗色 `#2A2A35` / 亮色 `#FFFEF7F1`），所以「边框颜色随主题」也有对应物。**这与 `2026-11-09` 那条不是同一个落点**：那条改的是动态页顶部头像条的 `cell_up_avatar`，本条是关注页两处列表项；`GlassStyle.kt:146-154` 的既有注释（「elevation 一并置 0：半透明底下的阴影会从下面透出来形成沿轮廓一圈的脏边」）已写明同一约定，只是默认分支没压。改法：两处抬高归 0，卡色 / 圆角 / 内容 / 点击一律不动（`clipToOutline` 与 `outlineProvider="background"` 保留，裁圆角不依赖 elevation）。L0（26.11.10 本机实测，head `8b43d494` + 本改动）：`:app:assembleDebug` BUILD SUCCESSFUL、`:app:testStandardDebugUnitTest` **210 suites / 2259 tests / 0 失败 0 错误**。**未验证**：真机看画面（L2，依据是画面），步骤见 `docs/review/manual-test-guide-261109-avatar-shadow.md` 的 H06。

## 2. 未完成待办

### 2.1 真机未验证（无设备 / 无登录态）

> **本轮测试包（26.11.08，release 普通版 + debug 共存版）要测哪些**：整包口径、分组总表与时间预算见 [`test-plan-261108.md`](test-plan-261108.md)；下面每一条是该计划里的详细验收项来源。
> **26.11.09 三条 L2 项（方向崩溃 / 下载重试 / 表冠速率）的验收步骤与判据见 [`manual-test-guide-261109.md`](manual-test-guide-261109.md)（H01~H04）。**
> **26.11.11 五组反馈批次（播放器 / 下载分段 / 消息搜索 / 专栏合集 / UI 小修）的验收步骤与判据见 [`manual-test-guide-261111-feedback-batch.md`](manual-test-guide-261111-feedback-batch.md)（P01~P42）。**
> **26.11.11 消息页「私信列表」玻璃质感的验收步骤与判据见 [`handoff-261111-private-msg-glass.md`](handoff-261111-private-msg-glass.md)（H22~H24）。**
> **26.11.11 播放器底部按钮行「新加按钮与同行不一致」的验收步骤与判据见 [`manual-test-guide-261111-player-slot-icon.md`](manual-test-guide-261111-player-slot-icon.md)（H25~H26）。**
> **26.11.11 自动跳过广告片段（SponsorBlock）的验收步骤与判据见 [`manual-test-guide-261111-sponsorblock.md`](manual-test-guide-261111-sponsorblock.md)（S01~S06）。**

- [ ] **26.11.11 自动跳过广告片段（SponsorBlock）真机未验（L2，只能看画面 / 判手感，且要设备能出网）**：L0 已过（两套单测 26 例全绿 + 整包 `assembleDebug` 通过）。需确认：①档位「关闭」下播任一视频全程无请求、无提示；设置搜索能搜到「自动跳过」组与两项设置；②档位「仅恰饭广告」下 `BV1KLgkzzEb1` 播到 5:17 自动跳到 6:27、提示「已跳过广告」、点「撤回」回到 5:17 且不会被立刻弹走、同一段只跳一次、跳完弹幕不是空白；③档位「推荐」下 `BV11Kc2zZEDK` 开头 0~21 秒被跳过且文案是「已跳过片头」（类别名必须是中文）；④档位「全部类别」下 `BV1Pb3uzyEmT` 的相邻重叠标注不会连跳 / 乱跳；⑤番剧、直播、本地视频一律不查不跳；⑥断网时静默降级（不弹错），恢复网络后一分钟内自愈；⑦换 P / 换集后上一集片段不串到新一集；⑧从历史 / 缓存入口进播放时广告照跳、且高能进度条曲线能显示（顺带修的老问题）；⑨「自动跳过」组里点「要跳的类别」，列表**没有**「精彩时刻」，勾一类返回后档位自动变「自定义」，回播放页该类别照跳、取消勾选后不再跳。步骤见 [`manual-test-guide-261111-sponsorblock.md`](manual-test-guide-261111-sponsorblock.md)（S01~S06）。

- [ ] **26.11.11 播放器底部按钮行的图标一致性真机未验（L2，只能看画面）**：L0 已过（220 suites / 2385 tests / 0 失败 0 错误）。需确认：①设置 → 视频播放器 → 界面里把「弹幕开关」放进底部左区 / 右区，**同时把它留在右列**（同一个按钮放两处，正是用户截图的状态），回播放页看底部那一格：图标大小与同行的清晰度 / 音量- / 音量+ / 分P 一致、且落在同一条水平中线上；②停留 10 秒以上（影子同步定时器 400ms 一轮）再截一张，两处图标仍各自正常，没有一处变小或缩到格子左上角；③点底部那一格能正常开关弹幕、点右列那一格也生效（影子转真身点击）；④把按钮从底部删掉 / 再挪回右列 / 组内展开收起各操作一轮，图标与格子尺寸都不飘；⑤「更多」收纳格（morehide / moreshow）仍是方形、没被拉成扁条。步骤见 [`manual-test-guide-261111-player-slot-icon.md`](manual-test-guide-261111-player-slot-icon.md)（H25~H26）。

- [ ] **26.11.11 消息页「私信列表」玻璃质感真机未验（L2，要登录态，只能看画面）**：L0 已过（两 flavor 各 220 suites / 2385 tests / 0 失败 0 错误）。需确认：①打开玻璃质感后「消息」页的私信会话卡变成半透明、与顶部四张入口卡同一种材质与圆角；②关掉后回到不透明深灰板且与修复前一模一样；③点卡能进会话、按住仍有水波纹、列表滚动/表冠正常；④转发时的「选好友」页复用同一适配器，一并看。本机无在线设备、私信列表又必须有登录态才有数据，故 L1 为空。交接单 [`handoff-261111-private-msg-glass.md`](handoff-261111-private-msg-glass.md)（H22~H24）。

- [ ] **26.11.11 播放器退出链 + 弹幕 + 横屏真机未验（L2，反馈 #117/#135/#139②/#143）**：L0 已过（本机 assembleDebug + 单测 217 suites / 2343 tests / 0 失败）。需确认：①播放中按返回键直接退出播放页（新开关「返回键退出」默认开），返回首页 / 退到详情页都正常；②把开关关掉后恢复旧行为（横滑调进度时按返回不退出、窗口右滑也不退出）——这是有意的隔离，不是回归；③锁定态下按返回 / 右滑有提示且解锁按钮**压暗常驻**（不再是 3 秒后消失、分不清锁了还是卡死）；④缓冲中（打转）按返回能退出且不 ANR；⑤暂停后再退出，播放页「继续播放」的进度等于退出那一刻的位置（不再读到 0）；⑥跳转后 / 倍速切回 1× 后弹幕仍在、密度正常；⑦断网时弹幕请求快速失败，不再挂 4 轮 × 15 秒；⑧设置里打开「横屏模式」后进播放器是横屏、关掉是竖屏，与播放器设置里的「默认方向」一致。

- [ ] **26.11.11 下载分段（反馈 #87/#90）真机未验（L2，要真机 + 能下完的片子）**：L0 已过。需确认：①找一条明确分多段 durl 的视频（老接口 / 部分番剧），下完后**能完整播到尾**（改前只有第一段长度）；②下到一半强杀 App 再进下载列表重试，已完成的段被复用、不从头重下；③失败时列表显示具体原因（HTTP 码 / 「文件没下完（收到 x/y 字节）」/ 段号），不再是笼统的「下载出错」；④单段视频、DASH 视频、纯音频、Aria2 分片模式四种老路径都还能下完；⑤升级前已存在的下载记录仍能打开、重试、删除（db 4→5 只加列）。

- [ ] **26.11.11 消息与搜索（反馈 #150/#148/#123）真机未验（L2，要登录态）**：L0 已过。需确认：①「收到的赞」页首屏有内容、下拉下拉能继续翻页且**不重复**（改前首屏报错后底部锁死、再也不翻页）；②断网进这一页→恢复网络→下拉能正常加载（不再留死锁）；③消息角标数值含点赞未读；④搜索结果下拉刷新 + 上滑翻页无重复卡片（整页重复时干净停下、不鬼打墙）且底部不再提前消失；⑤搜索历史区右上「清空」能点、弹二次确认、清完历史立刻为空且重启后仍为空。

- [ ] **26.11.11 专栏正文与合集续看（反馈 #142/#151/#152）真机未验（L2）**：L0 已过。需确认：①打开一篇老 cv 号专栏，正文能读（改前只要整页抓不到就整条失败）；②风控 / 空文章两种情况提示文案**分得开**（「被拦截」vs「没有正文」），不再统一「可能被风控拦截」；③代码块显示为单行文本、公式不再露 `[无法识别段落：N]` 这类方括号英文；④合集详情页在「上次看到」那条前有 「[上次看到] 」 标记，进合集自动停在那一条；⑤从任意入口起播（详情页 / 搜索 / 动态）后在合集里换了几集，退出再进合集详情页，标记停在新看的那一集；⑥划掉 App 后重进，标记仍在；⑦收藏夹虚拟合集与 UP 主投稿队列切集不崩。

- [ ] **26.11.11 UI 小修五连（反馈 #125/#140/#121/#129/#94）真机未验（L2，多半只能看画面 / 判手感）**：L0 已过。需确认：①「新建文件夹」等弹窗按钮：点按钮中心与按钮内侧边缘都命中，改字号后再试一次仍准（#125 是防御性修法，若仍偏需按 `docs/specs/feedback-261111.md` §9.5 继续定位）；②首页卡片长按 600ms 稳定弹出菜单、正常上下滑列表不误触（#140），多选态视觉改成 `▸` 引导符 + 标题主色（视频卡 / 历史 / 用户投稿 / 文章 / 下载 / 缓存六处一致，不再整卡发灰）；③私信拦截提示的开关关掉后不再弹（#121）；④短视频双指放大后能双指拖动平移、少一根手指立刻恢复「上下滑切视频」、双击仍切播放暂停、缩回 1 倍不残留偏移（#129）；⑤缓存页与下载列表页的「全选」：只勾当前可见项、搜索过滤后只勾命中项、空列表给提示、缓存页顶层优先未分类视频（#94）。

- [ ] **26.11.10 动态页头像条「作者旁灰边」三次修后真机未验（L2，只能看画面）**：`cell_up_avatar` 已不再是 `MaterialCardView`（圆由 `bg_avatar_circle` 的 oval 轮廓 + `clipToOutline` 提供，卡底色 / 描边 / 阴影 / 圆角覆盖四个源一次消掉）。需自定义背景（带明暗细节）+ 暗 / 亮各一套主题，在动态页顶部「最近更新的UP主」横条上看：①每格头像四角**不再有方块色**（改前是与卡色同色的圆角方块）；②头像仍是正圆、边缘没被裁掉，「有更新」粉点位置不变；③手指划过 / 按住时不冒暗边；④点头像与名字都能进用户空间，横条左右滑动流畅，超长名字仍是省略号；⑤**圆角档为「圆角」**（设置 → 界面与外观 → 圆角）时再看一遍——这一档正是原来触发覆盖的条件。步骤见 [`manual-test-guide-261109-avatar-shadow.md`](manual-test-guide-261109-avatar-shadow.md) 的 H05。

- [ ] **26.11.10 关注页列表项去抬高真机未验（L2，只能看画面）**：`cell_user_list` / `cell_follow_group` 的 2dp 抬高已归 0。需自定义背景（选带明暗细节的亮色照片）+ 暗 / 亮各一套主题，在关注列表的普通模式与分组模式看每行卡片外沿不再有暗边；并确认复用 `cell_user_list` 的另两个页面（搜索 → 用户、视频详情 → 充电鸣谢）卡片变平后头像 / 文字 / 徽章无位移。步骤见 [`manual-test-guide-261109-avatar-shadow.md`](manual-test-guide-261109-avatar-shadow.md) 的 H06。

- [ ] **26.11.09 BaseActivity 方向崩溃修复真机未验（L2，要 Android 8.0 + 非全屏窗口）**：L0 已过（两 flavor 各 209 suites / 2257 tests）。需一台 sdk26 手表在**非全屏**窗口状态（分屏 / 悬浮 / 自由窗口）下进任意子页（复现机型是 huawei LEO-DLXXU 进收藏夹页），确认①不再崩 `Only fullscreen activities can request orientation`；②`Logu.w` 里能读到「设置屏幕方向失败（窗口非全屏），按系统方向显示」这一行；③页面按系统方向正常显示、能正常退出。全屏状态下应完全无感。

- [ ] **26.11.11 直播弹幕发送（反馈 #156）真机未验（L2，要登录态 + 真直播间）**：L0 已过（218 suites / 2371 tests / 0 失败，`LiveDanmakuApiTest` 20 例）。需确认：①登录后在某直播间发一条 20 字以内文本，输入框清空 + 出现「弹幕已发送」提示，且直播间画面里出现**1 条且只有 1 条**——若提示成功后直播间始终看不到自己那条，说明服务端没真正收下（不是本地位移问题）；②连点两下，第二次本地提示「刚发过啦，等 N 秒再发」且没有网络请求；③21 字本地拦下、不清输入框；④游客模式提示先登录；⑤录播发弹幕与改动前完全一致（回归）；⑥绕过本地节流直到服务端拒绝时，提示「发得太频繁了」而不是笼统失败。**没验证的还有**：接口是否真能发出去（本机无登录 Cookie；参数与签名已逐项对齐 PiliPlus `PiliPlus/lib/http/live.dart:36-78`，含 `web_location=444.8` + wbi 签名 + `statistics`，但服务端是否认账只能实发才知道）、20 字是否为当前直播间真实阈值（无查询接口，靠 `1003212` 兜底）、`web_location`/`statistics` 是否真降低风控、Referer 换成 `https://live.bilibili.com/` 属风控口径推测（报 `-352`/`-412` 就先回退成全局 webHeaders）。
- [ ] **26.11.11 视频转发到私信（反馈 #133）真机未验（L2，要登录态 + 两个账号）**：L0 已过。需确认：①转发面板出现「转发到动态」「发给好友」两项，选第一项仍进原动态页（回归）、长按「复制链接」照旧；②选好友页能列会话、下拉刷新、点人返回；③对方会话里收到「标题 + https://www.bilibili.com/video/BV…」文本，自己侧提示「已发送给 XX」；④断网 / 隐私限制 / 未关注时提示「发送失败，已复制视频链接」且剪贴板正确；⑤无会话账号进选好友页有空态且能重试。**遗留**：卡片私信做不了（服务端只收 `msg_type` 1/2/5，卡片是 7 → 21035，需真实登录实测才能翻案）；会话列表取 50 条且无翻页参数（`PrivateMsgApi.getSessionsList`），会话更多的人选不到更早的。

- [ ] **26.11.09 下载失败后再也下不动（反馈 #136）真机未验（L2，要真机 + 登录态）**：L0 已过。需确认：①26.11.09 之前留下的失败任务（下载列表里 `state="error"` 那类），升级后打开下载列表页会被自动清理，回视频页点下载能重新进队列；②能下完一次；③若线上出现 `DownloadService>下载失败，换备用线路重试（2/N）`，说明换线路径真的被走到，且最终下完。④清空缓存 / 重启 App 后仍然可用（旧版本正是在这两步之后也救不回来）。

- [ ] **26.11.09 关闭表冠适配后详情页滚动速率（反馈 #132）真机未验（L2，只能判手感）**：需小米 Watch 5（或任意一台走「系统优先」的手表）：①关闭表冠适配后，详情页每格距离应与**开启适配**时一致（改前只有 1/3）；②连续快速转动不打嗝；③内层列表与详情页外层交替滚动时不叠加。偏慢只调 `RotaryEventPolicy.SYSTEM_FIRST_TARGET_SCALE`（1.1~1.25），打嗝把 `RotaryEncoderSupport` 里的补位改成瞬时 `scrollBy`。

- [ ] **26.11.08 弹幕去重修复真机未验（L2，只能看画面）**：`player/DanmakuStreamLoader` 的 `windowReady` 开关（反馈 #16）。需在手表上确认：①多 P 视频切 P、互动分支跳转时，首窗口网络返回前播放器已起播的情况下，同一批弹幕只出现一次；②普通首播视频弹幕正常、不重复；③播放中 seek 跨窗口后补拉的分段不重复、不漏；④被数量保护跳过弹幕的视频点弹幕按钮强制加载后只有一份，且后续分段能补上。

- [ ] **26.11.08 视频卡片长按菜单真机未验（L2，只能看画面 / 判手感，还要登录态）**：L0 已过（两 flavor 各 205 suites / 2217 tests / 0 失败 0 错误，`--rerun-tasks` 全量重跑）。需在手表上确认：①在推荐 / 排行 / 搜索 / UP 主空间这类列表里长按卡片，弹的是**操作菜单**（推荐流五项：稍后再看 / 收藏 / 缓存到本地 / 看封面 / 减少推荐；其它列表四项，没有「减少推荐」）而不是缓存画质页；**番剧卡与直播卡长按应毫无反应**（不弹空菜单）。②弹菜单后**抬手不能跳进视频详情页**（旧 `suppressClickAfterLongPress` 逻辑要确认没被菜单打断）；弹窗标题是卡片标题、长标题不撑破屏、五个条目在 192–233dp 宽度下放得下（注意这一项可能已经超出屏幕，需要能滚到）。③「稍后再看」加成功有提示、进稍后再看页能看到；未登录时提示「还没有登录喵~」而不是错误码。④「收藏」进的是收藏夹选择页且默认不选中任何夹子，选完提示成功。⑤「缓存到本地」与旧行为一致（按设置里的「默认缓存画质」走 dialog / 最高 / 720P / 360P / 仅音频）。⑥「看封面」能全屏放大、可退出回到列表；顺手确认没有封面的卡片（如部分搜索结果）长按菜单里不出现这一项。⑦「减少推荐」只在**首页推荐流**出现，点了之后该卡片从列表消失、提示「已减少推荐」，下拉刷新后同类内容是否真的变少（这项只能用户自己看推荐结果）；网络异常时不崩、提示错误码。⑧设置里关掉「启用长按快捷操作」后，视频卡与专栏卡长按都没有反应；「默认缓存画质」这一项**不再跟着开关隐藏**（关掉开关后仍能进设置看到并修改）。⑨历史记录页 / 稍后再看页 / 收藏夹页的原有长按（删除、移除）没有被新菜单顶掉。

- [x] **26.11.08 播放器软解回退（§1「档位不该碰解码方式与清晰度」）已在真机确认修复**：用户 26.11.08 装 `26.11.07-TEST-diag` 修复版后反馈「问题修复了」，卡顿的那台 1GB 手表恢复正常；①诊断报告里的 `codec.videoDecoderId=MEDIACODEC(硬解)` / `video.fpsOutput ≥28` 这组对照未逐项抄回来（用户只确认了观感），②「硬解花屏/绿屏」风险项未出现。诊断包与测试版框架已同日拔出（见 §1 末两条），后续如需复测按 `docs/archive/superpowers/specs/2026-11-07-test-build-framework.md` §4 重建。

- [ ] **26.11.08 播放器宽屏布局回退真机未验（L2，只能看画面 / 判手感）**：已做 L0（两 flavor 各 202 suites / 2186 tests / 0 失败）与 L1（AVD 1080×2337@420dpi 与 466×466@320dpi 两种尺寸的 `uiautomator dump` 数字均与 26.11.02 逐项一致）。需在真机确认：①**宽屏（手机 / 大屏）**上底栏厚度、底部按钮大小与间距、按钮簇是否整体居中（播放键应随内容居中，不再钉在屏幕正中）；②左右竖列按钮是否回到 28dp 紧凑尺寸、贴屏幕边缘不压画面；③**底部按钮放满、需要整行横向滚动**时的观感（本轮两种屏都没塞满过）；④「更多」按钮展开后的图标是否方正（1.19× 竖向拉伸已修，但只在溢出场景渲染，本轮没构造出来）；⑤横屏 / 分屏 / 平板看画面；⑥字幕底边与底栏按钮行之间恢复约 17dp 间隙、不再重叠。**交接单：[manual-test-guide-261108-player-wide-layout.md](manual-test-guide-261108-player-wide-layout.md)（H01~H06，约 10 分钟，无需登录）**

- [ ] **26.11.08 顶栏灵动岛真机未验（L2，只能看画面 / 判观感；26.11.08 已与圆屏适配解耦，方屏也要看）**：本机无圆屏设备，只做了 L0（构建 + 单测）。**26.11.10 宽度固定 + 状态图标已补 L1（vivo V2229A / Android 16 / coexist debug 包）**：`uiautomator dump` 读 `@id/timeText` 的 bounds，首页 / 设置 / 搜索 / 消息四个页面**完全一致** `[1676,123][1892,181]`（216px 宽 = 72dp @density 3.0；屏宽 1916px，216 < 594 未触发 31% 封顶），未读态与时间态宽度也相同；截屏确认未读态是「橙色信封 + 数字」、时间态无图标。仍需人判：①在「界面与外观 → 布局适配」里**单独打开「顶栏灵动岛」**（不依赖圆屏适配），顶栏时钟位在后台播放 / 下载中 / 有未读 / 低电（≤20%）/ 充电 / 有新公告 / 有更新时显示对应内容，某个事件连续显示满 30 秒会插 5 秒时间再切回事件，**一个事件都没有时恒显示时间**；②亮屏、切页、退后台再回前台之后，事件文字不被时钟覆盖回时间（`TextClock.externallyManaged` 生效），退到后台不再每秒闪（`detach` 生效）；③关掉该开关后恢复成普通时钟、顶栏宽度不变形；**开着圆屏适配但关掉灵动岛**也必须是普通时钟（两个开关互不牵连）；④老用户迁移口径：升级前就开着「圆屏适配」的人，进设置页应看到灵动岛已是开；⑤**宽度统一（26.11.10 用户报 bug 后改）**：方屏（手机 / 圆屏适配关）上开启后，**同一设备所有页面、所有状态胶囊宽度一致**（72dp，屏宽 31% 封顶），长标题由省略号截断、不压住标题、不挤出屏；圆屏上同为这个宽度；⑥**状态图标（26.11.10 用户要求）**：未读 / 下载 / 低电 / 充电 / 公告 / 更新时文字前有 12dp 小图标（信封 / 下载箭头 / 电池 / 闪电 / 喇叭 / 更新箭头），电量状态显示百分比而不是裸数字、充电时闪电慢闪（闪烁节奏与「像充电宝」的观感只能人判）；「99%」「15%」这类长内容在那点宽度下不压住图标、不挤出屏；⑦低电 / 公告这类瞬时事件只弹一次，不会一直压着播放状态不放。交接单 [`review/manual-test-guide-261108.md`](manual-test-guide-261108.md)（H01）。
- [ ] **26.11.08 合集续播定位真机未验（L2）**：需在手表上确认：①在某合集里看到第 N 集后返回合集页，进的是同一个合集且自动滚到第 N 集——**分节合集与只有卡片的「收藏的合集」两种形态都要试**；②那一集被删 / 已不在本合集时回落到入口视频，而不是停在顶部；③`scrollToPosition` 在手表小列表上不出现「先闪一下顶部再跳」的观感问题；④同一合集第二次进入仍定位到最近一次那一集（记录被覆盖而不是累加）。交接单 [`review/manual-test-guide-261108.md`](manual-test-guide-261108.md)（H02）。
- [ ] **26.11.08 点赞卡片描边开关真机未验（L2）**：①设置里关掉后，视频详情页动作区与文章 / 动态末尾的动作卡片都不再有描边，底色与文字色不变；②重启 App 后保持关闭；③浅底主题下关掉描边后卡片边界仍能分辨（对比度别掉太多）；④默认值确认为「开」，老用户升级后观感不变。交接单 [`review/manual-test-guide-261108.md`](manual-test-guide-261108.md)（H03）。
- [ ] **26.11.08 表情页标题栏黑带真机未验（L2）**：切到知乎蓝等浅底主题进表情页，标题栏应跟主题底色、不再是一条纯黑带；默认暗色主题下观感与修复前一致（不能变成灰条）。交接单 [`review/manual-test-guide-261108.md`](manual-test-guide-261108.md)（H04）。
- [ ] **26.11.08「只看性能不看设置」同型 bug 批量修复（7 条）真机未验（L2，只能看画面 / 判手感）**：本机只做了 L0（203 suites / 2197 tests / 0 失败，构建 + markdownlint 均过）。需在手表上确认：①**高性能模式**——在一台打分 ≥65 的高档机上把它关掉，列表图与 Glide 缓存应确实降配（`dumpsys meminfo` 内存或观感差异），开关状态在重启后保持关闭；反之在低档机上开启后应立即生效（不再需要「下次启动」）；②**顶栏三开关**——播放页关掉「显示标题 / 显示电量 / 显示时钟」后顶栏对应项消失、标题不再挤占位置，退出再进保持；三个都关掉时顶栏仍可点击退出（`layout_top` 的点击区不受影响）；③**弹幕行数**——低端机把行数设成 15 后确实上到 15 行（此前最多 6 行），同屏弹幕变多后的掉帧程度由用户判断；④**弹幕停留时长**——把 1.0 改到 2.0，弹幕应明显变慢（旧文案叫「速度」、旧行为是改大反而更慢，新描述已写明「数值越大越慢」）；⑤**翻动时不加载图片**——升级后开关显示为「开」（与旧行为一致），关掉它再翻列表，图片应在翻动时也开始加载；⑥**Debug 包的两条解析报错开关**显示「开」且立即生效，不用再手动关一次再开。

- [ ] **26.11.08 反馈 #118「扫一扫关不掉」只差拖拽手势未验（L2，需要注入私有功能的包）**：L0 已过（两 flavor 各 202 suites / 2195 tests / 0 失败），L1 已在本机真机（vivo V2229A / Android 16，coexist debug 包）验完「关掉不再出现（含重启）/ 老数据补回位置在「设置」之后」两项，判据是控件树文字 + `shared_prefs/default.xml`。剩下只能靠手指判的两条：①在「菜单顺序」里**长按拖拽**（不是点按——点按已启用项是「设为启动页」）把「扫一扫」拖进未启用区，回主菜单它不在了、重启仍不在（adb 的 `input draganddrop` 拉不起 `ItemTouchHelper` 拖拽，只能人工）；②关掉「扫一扫」的同时拖拽改过其它项的顺序，重启后顺序仍保住（不能被「补项」顺带打乱）。

- [ ] **26.11.07 关注/粉丝列表「特别关注」徽章真机未验（L2，只能看画面 / 需要登录态）**：本机模拟器没有登录态，关注列表打不开，只做了 L0（构建 + 单测）。需要在手表上确认：①自己的关注列表里，特别关注的人有金色「特别关注」、互关的人有粉色「互关」，两个都满足时两个徽章同时出现；自己的粉丝列表里，我特别关注过的粉丝也有金色徽章；②看别人的关注/粉丝列表时一个徽章都不出现；③暗色 / 亮色模式下金色都清楚可读（**亮色是重点**：默认主题亮色下金色仍是 `#FFB800`，浅底对比偏低，见交接单 H02），长昵称截断不压住徽章、两个徽章不重叠不出屏、列表滚动复用时不串行。交接单 [`review/manual-test-guide-261107-follow-special.md`](manual-test-guide-261107-follow-special.md)（2 条）。

- [ ] **26.11.07 番剧分区页（§3.138）设备端整页未验（L1+L2；交接单 [`review/manual-test-guide-261108-bangumi.md`](manual-test-guide-261108-bangumi.md)，7 条）**：本轮没能起模拟器（见 §3.138 末条），需要在手表上确认：①菜单里出现「番剧」且点得开（老用户升级后应被自动回填，自己在菜单设置里关掉后不应再加回来）；②三个页签的选中态（主题色 + 加粗）与切换后列表是否保留；③推荐/追番/索引三个列表的下拉刷新与滑到底翻页，**尤其「追番」未登录时的空态与「点我去登录 → 回来自动加载」**；④索引页「筛选」弹窗的分组单选在 192–233dp 手表宽度上是否放得下、选项（风格 45 个）能否滚到底、选完是否立刻换列表且回显正确（26.11.08 起还会在上一轮请求收尾后按新条件重拉一次，转圈不中途停）；⑤失败态（断网/风控）应提示「点我重试」而不是显示成空列表；⑥圆屏模式下顶栏与页签不被圆盘切掉、表冠能在列表里滚动。

- [ ] **26.11.07 无障碍与播放器热区的真机项未验（L2，只能上手 / 听读屏；交接单 [`review/manual-test-guide-261108-a11y.md`](manual-test-guide-261108-a11y.md)）**：①**TalkBack 逐屏走查**：播放页 17 个按钮与左右列图标要读作「弹幕 / 旋转 / 音量减 / 播放与暂停 / 清晰度…」而不是「按钮」；列表页头像读作「查看用户主页」，封面 / 角标 / 装饰图标不再被逐个念成「图片」；登录页二维码、搜索结果排序键仍能读到原描述。②**手感**：44dp 底部按钮在真手指下是否明显减少误触；进度条 48dp 热区是否更好拖；③**小屏手表**（可用高度约 209dp）：右列 3 格 + 顶栏 + 改高后的底栏不重叠、圆屏边缘不切按钮；④**按钮多时**（左右底部各 3 格）底部行两侧的横向滚动是否顺手、不会和播放器滑动手势打架。⑤**自定义布局把 3 个及以上按键放左列**（26.11.08 验收发现的回归，已按 `2989652` 修复）：左列格子原先按**整窗高度**放大，3 格就顶到进度条上、进度条左半段拖不动；修法是左列借用右列那个被上下栏夹住的高度上限，量不到就不放大（保持 28dp）。真机仍要确认左列不压进度条、进度条照常能拖——这一页当前**没有 UI 入口**（26.11.06 起隐藏），交接单 H05 写了调试桥直达 `SettingPlayerUiActivity` 的命令。

- [ ] **26.11.07 教程旧链路收尾（调研清单第 5 条）的翻页提示未验（L2，只能看画面；交接单 [`review/manual-test-guide-261107-tutorial-hint.md`](manual-test-guide-261107-tutorial-hint.md)）**：5 个宿主页（搜索 / 视频详情 / 用户主页 / 专栏详情 / 动态详情）首次进入应出现「←这个页面可以左右翻页喵→（共有 N 页）」，N 要与真实分页数一致——**视频详情默认 3 页**（简介 / 评论 / 相关推荐，`related_enable` 默认开；关掉「视频相关推荐」才是 2 页）；**番剧详情默认 2 页**，注入了可选私有功能、多一页「角色」时是 3 页（公开包看不到这一页）；搜索页 = 当时启用的搜索类别数（默认 5，旧实现写死成「类别数 − 1」，显示 4 是错的）；用户主页 4、专栏详情 2、动态详情 2。左右滑一次后提示消失且不再出现；同页还有全屏教程（视频详情 `video_main`）时，提示要等教程关闭后才出现，不能在教程盖住时被记成「已展示」；管理页「重置所有页面滑动引导」后回到页面应重新出现；老用户（`tutorial_pager_*` 已有存档）升级后**不应**重新弹。**记账时机自 `034a037` 起五页统一为「提示一露出就写已读」**：搜索页原来是「左右滑一次之后才记」，现在与其它四页一致，所以搜索页提示刚出现就退出、一次都没滑，下次也不再出现——这是刻意口径统一，不是丢存档（详见 `docs/tutorial-system-redesign.md` §五）。

- [ ] **26.11.07 调研清单第 1、2 条的观感与端到端未验（L2 / L1）**：①Opus 正文段落里的 `@某某` 与网页链接现在按 `StringUtil.LinkClickableSpan` 的链接色渲染并可点（此前是普通正文色、点不动），需真机看：颜色与话题 / 动态正文一致、不抢正文、长按不误触；点 @ 进对应用户页；点链接进 `LinkUrlUtil.handleWebURL` 的正确地址（服务端会把显示文本缩短成 `av1`，点击必须用 `jump_url`，单测只锁了解析、没锁真实跳转）。②弹幕下载失败（弹幕地址 404/412 或 CDN 返回空正文）时应提示失败、不再把错误页写进 `danmaku.xml`：本地列表「更新弹幕」走同一路径（`DanmakuFileFetcher` 收口弹幕落盘副本：`39e4136` 并掉 4 份，26.11.08 `b2be26f` 又补上漏掉的 `DownloadService.downDanmaku` 那份，现共 5 处）。~~用 Debug 包把 `danmaku_url` 换成必然 404 的地址~~——26.11.08 复核：**没有这样的开关**（弹幕地址来自 `PlayerApi` 响应，调试桥只有 `http` / `set` 两类命令，改不了它），能人工复现的只有「断网 → 更新弹幕应明确报失败、不显示成功、原有弹幕不被覆盖」，404/412 那条由 `DanmakuFileFetcherTest` 5 例 + 代码走查兜底，见交接单 [`review/manual-test-guide-261108-opus-danmaku.md`](manual-test-guide-261108-opus-danmaku.md) H05。

- [ ] **26.11.07.1 第二批修复的观感项（L2，只能看画面判）**：①播放器图标（倍速 / 锁定 / 时钟 / 角标 / 调试）改成引用 `@color/player_icon_on_scrim` 后在深色与浅色主题下的实际观感——语义是「压在视频上的图标必须纯白」，不应跟着主题变灰；②`icon_help.xml` 的 tint 与路径 fill 都改成 `@color/vector_icon_mask` 后，帮助图标在亮/暗主题下都应正常着色（SRC_IN 下若白上白会直接看不见）；③播放器设置卡片滑出时的遮罩（`@color/card_scrim`）浓度是否与改前一致；④大图查看器的「加载中」提示文字（`@color/image_viewer_hint_text`）压在照片上是否仍清晰；⑤`AmbientGlow` 合并后：有背景图时「图 + 光晕」与无背景图时「底色渐变 + 光晕」两种形态的观感与合并前逐项一致，暗色、亮色、终端主题各看一眼；⑥设置页全局搜索里用新资源键的条目（配色模式、弥散光晕、玻璃质感、背景图、背景图适配、背景图压暗、圆屏列表）仍能被搜到且点进去能定位到对应开关。

- [ ] **26.11.07.1 审查修复里的四处观感项（L2，只能看画面判）**：①播放页左列与右列的中线对齐（删/加格子、展开组、横竖屏切换后都要仍然居中，不高不低）；②闪屏与播放器强制暗色时，背景图与弥散光晕的明暗配套，不应出现「暗底亮光晕」；③播放器影子按钮（组格展开、后台镜像）的阴影粗细与真身一致，不再出现 2px 硬黑影；④关于页开发者简介换行与「版本名 / 版本号」加粗区间（标签字数变了也要对齐）；⑤账号切换页长按删除账号的确认对话框文案与资源化后的排版。

- [ ] **背景图入口改内置选图页（26.11.07）手表端未验**：L1 已在 AVD `Medium_Phone_API_37.0`（手机 1080×2400）上走通「外观设置 → 选择背景图 → 内置选图页三列缩略图 → 选一张 → 返回后该行『当前：已设置』且 `files/appearance/background.img` 落盘 1.19MB」。**手表（Wear OS / 466×466 圆屏）上未验**：①内置选图页的三列缩略图在圆屏上会不会被圆角切掉、顶部「全部图片 / 系统选择器」两个按钮是否点得到；②表冠能否在缩略图网格里滚动；③首次进入申请读图权限的弹窗在手表 ROM 上是否正常（部分手表 ROM 无权限弹窗，此时只能靠页内「系统选择器」退回）。本机 `wear5` 系统镜像缺失起不来，只能交人工。

- [ ] **收藏夹管理页新版式 + 内容管理入口（§3.80）部分设备验证**：已在 SDK AVD `wear5`（Android 14 / 466×466）上用 `coexistDebug` universal 包核对了**结构与文案**（默认夹与非默认夹两种版式、两行内容管理入口、点「批量删除内容」能开出夹内容页）。**仍未验的**：①点「批量删除内容」后是否真的**直达多选态**（要先登录拉到列表，空列表按设计不进多选）；②「清空失效内容」的确认框与 `data=0` 成功提示；③真手表/圆屏上的实际观感。**注意**：删除与清空都是真实账号的破坏性写操作，真机验证要做好心理准备，别拿主号试。

- [ ] **DASH 下载合并单文件（§3.66）端到端未验证**：需要登录态 + 选 1080P，本机只有模拟器且未登录。容器层已用两条现成 m4s 走 DebugBridge `merge` 实测通过（`result=OK`、`tracks=2`、采样数与源一致），**但「真实下载 → 合并 → 本地列表只剩视频 → 内置播放器有声」整条链路没跑过**。真机用例：登录后下载一个 1080P 视频，观察进度走完 0.88–1.00 的「合并音视频」阶段、目录里只有 `video.mp4`（无 `audio.m4a`、无 `video_merging.tmp`）、本地页直接播放有声；再故意制造合并失败（如把磁盘写满）确认保留双文件且下载不报错。

- [ ] **媒体控件「音乐播放」归类（§3.90）真表未验证**：本机模拟器不可用，会话↔通知配对只有代码级推演。真机用例：开启「系统媒体控件」后播放视频，系统媒体卡片应显示为音乐播放类型且标题正确；切 P / 换清晰度后标题跟随；退出播放页通知消失、不残留僵尸卡片；同时挂着下载任务时控件仍应标「音乐播放」（不再标「下载」）。

- [ ] **「圆屏列表」效果（§3.91）真表未验证**：视觉手感（0.50 缩放下限、0.85 系数的 t³ 淡出）是按代码定的参数。真机用例：圆屏模式下开启后各列表页滚动，条目应边缘变小变透明、中央正常；滑动流畅无卡顿（滚动回调零分配）；横向翻页（短视频 / 搜索）与文章、大图不应缩放；关闭开关重进页面恢复原样。

- [ ] **图片保存改造（对比审计 §3.3 P2-2，第二批）真机未验证**：Android 10+ 默认目录改为应用专属、下载完成经 MediaStore 发布进 `Pictures/哔哩终端/`。用例：看图页下载图片 → 相册可见、无 0 字节残留；再下载同一张覆盖不产生两份；自定义公共目录时行为不变；API 28 及以下仍落公共 Pictures。日志判据 `adb logcat -s PictureMediaStore`。
- [ ] **直播弹幕 wss 校验（§3.3 P2-6）真机未验证**：正常直播间能连、能收弹幕；`host_list` 缺失/异常时应提示而不是继续裸连；握手 Cookie 只剩 `SESSDATA` 后，需确认长连接鉴权仍成功。
- [ ] **深链白名单（§3.3 P2-7）真机未验证**：`bilibili://video/...` 等自定义 scheme 仍能跳转；`https://bilibili.com/...`、`https://b23.tv/...` 能进 `GetIntentActivity`；站外链接被拒且不再打 `debug-host` 日志。
- [ ] **极验参数校验（§3.3 P2-8）真机未验证**：滑块验证仍能正常出图、完成登录；服务端返回异常 `gt`/`challenge` 时提示而不是白屏/崩。
- [ ] **遥测内存档位化需真机观察（§3.3 P2-9 部分）**：客户端崩溃载荷已改为只报 `ram_level` 档位（`ram_total/ram_avail` 恒 0），`device_key` 按产品需求**保留采集**（26.10.24 决定回退删除），隐私文案维持 v2 口径；真机上确认崩溃页/控制台按新字段展示正常即可。

- [ ] **登录页扫码中途退出的竞态（§3.38）真机未复现**：修法是「判活搬进 Runnable + 文案走 application context」，判据已由 R8 mapping 还原（崩点 `QRLoginFragment.kt:284`）。真机用例：扫码页在「已扫码，等待手机确认」时点「跳过 / 返回」/ 按表冠退出，反复若干次，不应再出 `Fragment … not attached to a context`。
- [ ] **缓存页空态行的真机观感（§3.34）**：只在 380×380 表盘模拟器上跑过，未在真手表上确认「未分类视频 0 个 + 啥都木有~」两行的字号/间距是否合适。

- [ ] **缓存页长按菜单的真机手感（§3.35）**：模拟器上长按 400ms 即触发；真手表需确认「长按播放/进文件夹」不会与长按误触冲突、5 项菜单在 45% 滚动区里滑动是否顺手、两侧约 24px 空白点不点得到。

- [ ] **本地音轨防重（§3.13）**：含内嵌音轨 MP4 + 残留 M4A 只播主音轨；纯视频 DASH + M4A 有声；纯音频不重复；反复切听视频/重试不叠声；暂停/恢复/拖进度正常；退出后静音；错误音频不崩。需用 Debug 包观察 `AudioTrack` 日志确认 IJK 轨道列表，UNKNOWN 应提示。下载双文件替换与音频专用模式残留问题仍待修，不能当本次已闭环。

- [ ] **倍速 + DASH 高清仍需 arm 真机实测（§3.88 终版）**：补丁版 `libijk*.so` 已在 x86_64 模拟器上验证「纯视频流真倍速 + 外挂音轨同速 + 同刻只有一路音轨」，但**手表（arm64-v8a）没上过机**，且模拟器上「在线 DASH 双流 + 外挂音轨」因夹具网络跑不通（宿主 HTTP 夹具只能经 `adb reverse` 给 ijk 用，媒体服务进程连不上），只用本地双文件夹具等价验证。真机用例：登录 → 选 1080P 起播（走 DASH 双流）→ 切 2x（画面应真变快，`本地进度`/进度上报每墙钟秒 ≈2 秒内容）→ 期间 `音频轨道重新同步` 计数应 ≤1 且不得出现两路声音 → 3x 长按同样验证 → 拖回 1x 画面恢复正常速率。判据看 `/sdcard/Android/data/<包名>/files/logs/rebili.log`（`AudioTrack>` / `进度上报`）与 `adb logcat -s IJKMEDIA`（`Playback rate:`）。

- [ ] **详情页中央加载遮罩收起时机（§3.11）待真机弱网复测**：用弱网（限速/弱信号）打开视频详情页并立即切到评论栏，评论出现后遮罩必须已消失且不挡触摸；再验番剧详情页的"敬请期待"番（无可用季/集）不再永久停在加载图。纯 UI 时序，JVM 单测覆盖不到。

- [ ] **双击缩放复位与旋转解耦（§3.9）待手表真机确认**：开启优先恢复后，默认竖屏→横屏→双击与默认横屏→竖屏→双击均应暂停且不变方向；再验证放大后双击仅复位且不变方向、第二次双击暂停。覆盖双击快进开/关（开时用中间区域）、系统旋屏/软件旋屏、TextureView/SurfaceView；关闭优先恢复时放大后双击也应暂停。

- [ ] **后台播放前台服务崩溃修复（§3.6）真机未复现**：代码已过两 flavor 构建 + 各 478 例单测，但未在低内存手表上跑过。触发用例：起后台播放后 5 秒内切回前台、快速进出播放页，反复若干次，观察是否再出 `RemoteServiceException: Context.startForegroundService() did not then call Service.startForeground()`。
- [ ] **评论带图 12066**：`img_size` 小数截断是已确证的确定性缺陷，但**与 12066 的因果关系未建立**。
  - 12066 在本地快照与上游 collector 均无文档（`bilibili-API/docs/comment/action.md` 错误码表只到 12001-12052）。
  - 验证方法：真机在 `ReplyApi.sendReply` 失败分支打印**完整响应体**取回 `message` 原文。§二十九(第二处) / §36.5
  - **26.11.14 用户定案（楼中楼图片入口）：保持现状、不改代码。** 复核依据：`activity/reply/WriteReplyActivity.kt` 用 `rpid == 0` 判定图片入口显隐，楼中楼分支走 `GONE`，这是 `e9cd46ac`（26.11.06）有意为之；上游 RE:BiliTerminal 同样只按一级评论放行；`docs/review/compare-with-re-biliterminal-261024.md:563` 记「服务端不收楼中楼 `pictures`」但**从未实测**。以后「楼中楼写评论发不了图」按已知设计处理，别再当新 bug 重查；若哪天官方客户端放出该入口，改动点只有三处（`WriteReplyActivity` 的可见性判断、`activity_write_reply.xml` 里 `image`/`imageText` 的默认可见性、`docs/FEATURES.md` 的口径）。
- [ ] **笔记「无法正常加载」根因未确证**：已排除 csrf、`note_id_str` 精度设计、UI 层。
  - 仍存假设：H1「服务端按 `num` 解析 17 位 `note_id` 丢精度 → 79502」、H2「空 `csrf` 被当参数错误」。**头等未验证项**。
  - 验证方法：同一 id 分别按字符串 / 按 num 各发一次对比。§29.1 / §29.5 / §36.5
  - `api/NoteApi.java:259-276`（getNoteInfo 不带 csrf，与 `bilibili-API/docs/note/info.md:65-71` 一致）
  - 未改清单：`note_id` 传参方式一行未动；`activity/video/` 下无任何写入（`VideoInfoFragment.kt:563-569`）
- [ ] **下载速度与进度改按真实字节后的真机验收（26.11.10，用户反馈「速度和进度显示的都非常不准确」）**：本机无 Android 设备 / 模拟器，只做到 L0。验收步骤与判据见 [`manual-test-guide-261110-download-speed-progress.md`](manual-test-guide-261110-download-speed-progress.md)（H13~H17）：高码率 DASH 单任务的百分比要与「已下 ÷ 总大小」吻合、视频流下完不停在 52%；并行 3 条时每行速度互不相同、总速度约等于各行之和；总进度条单调不回退、ETA 收敛；封面 / 字幕 / 弹幕不再让百分比跳变；MP4 单文件与探测失败的兜底路径仍能显示进度且抖动时不回退。§3.146
- [ ] **缓存文件夹页改独立 Activity 后的真机验收（26.11.10，反馈 #20）**：本机无 Android 设备 / 模拟器（`/dev/kvm` 不存在，SDK 下没有 `emulator` 与 `system-images`），只做到 L0。验收步骤与判据见 [`manual-test-guide-261110-cache-folder-page.md`](manual-test-guide-261110-cache-folder-page.md)（H08~H12）：点文件夹是独立页面且只显示夹内视频；四条退出路径（右滑 / 返回键 / 顶栏箭头 / 分区标题）都不卡、退回后缓存页滚动位置还在；夹里删一条或移出后退回能看见变化；只翻看不动数据退回不重扫；从文件夹页长按顶栏切栏目后返回栈里不残留缓存页。
- [ ] **稍后再看「未看完」第二层待真机**：本地判据已修，但服务端 `viewed=2` 的真实语义未确认。若仍为空 → 抓 `history/toview/web` 的请求 `viewed` 值与响应体。§三十五 / §36.5
- [ ] **收藏夹排序行**：逐条排查后**未发现可修缺陷**（两处入口汇合到同一个 `FavoriteVideoListActivity`）。
  - 真机排查指令：`adb logcat -s "debug-收藏夹排序"`，正常应 `sortBar.visibility=0 mediaId=<非0> fid=<非0> readOnly=false`。
  - 临时诊断埋点在 `activity/user/favorite/FavoriteVideoListActivity.kt` 的 `setupSortBar()` 末尾，倾向保留。§二十二 / §36.5
- [ ] **关注分组移动成员未真机验证**：`FollowGroupAdapter.getUserOwnerGroup(position)` 的位置算术只做过静态复核。§三十六 / §36.5
- [ ] **片头片尾跳过**：跳过逻辑本身（`player/ViewPointSkip.kt`）一行未动，行为改变全在默认值 / 引导判据上；真机清单第 199/200 条未跑（且 §四十三 已整体撤回）。§三十三 / §36.5
- [ ] **内置播放器「切后台回来视频位置出错」代码已修，真机只跑到「未复现」**（26.10.11 用户报告；同日已在 vivo 手机上真机跑过，见下方「真机记录」——本机触发条件不成立，**不等于修复已被证实**）。
  - 机制①（**已修**）：`activity/player/PlayerActivity.kt` 原本**没有** `onSaveInstanceState`/`onRestoreInstanceState`，Activity 被系统回收后重建只能回退到**进入页面那一刻**的 Intent `progress`（`:372` 读、`:3553-3560` `seekTo(progress_history)`）。现加状态保存/恢复（`STATE_POSITION` + `restoredFromSaveState`；恢复的那次不再弹「已从上次的位置播放」）。
  - 机制②（**已修**）：`:2138` 的默认显示方式为 SurfaceView（`player_display` 默认 `SDK_INT < 26`，手表多为 API≥26），切后台回来 Surface 必被销毁重建 → `:2127-2133` 的 `onSurfaceReattached` 用**UI 进度条的值** `seekTo(seekbar_progress.progress)`；叠加 `enable-accurate-seek` 默认 0（不精确 seek）⇒ **每次回前台往回跳一个关键帧间隔**（数秒），反复切会累积。现改为 `seekTo(video_now)` 并加 `!isLiveMode` 守卫，点播开启 `enable-accurate-seek=1`；直播下该进度条恒为 0（`:1190` 只在非 live 分支赋值）的问题随之消失。
  - **遗留未修**：链路页 `activity/video/JumpToPlayerActivity.kt` 的 `onCreate` 仍无 `savedInstanceState` 守卫，系统回收后会重新取流并再 `launch` 一次（不直接造成位置错，但会白跑一次取流）。
  - 真机判据（埋点已在代码里，无需另加）：`adb logcat -s "PlayerActivity>onCreate" "PlayerActivity>onPrepared"` 应看到「恢复位置」+「进度跳转」；`PlayerActivity>checkStall` / `PlayerActivity>switchLiveCandidate` / `PlayerActivity>getExtras`（「直播候选」）对应看门狗与线路轮换。用例见 `docs/archive/review/device-test-plan.md` D3-11~D3-15。
  - **真机记录（26.10.11，设备 vivo `V2229A` / PD2229，Android 16 / SDK 36，arm64-v8a，1080×2520，序列号 `10AC9S2M4L000QL`）**：`adb` 深链 `bilibili://video/170001` → 详情页 → 播放 → 分 P → `PlayerActivity` 正常起播（画面 + 弹幕 + 进度文本，例 `00:12/03:19`）。① 熄屏再亮屏：`00:56 → 00:58`，无回跳；② 把详情页压到播放器之上再返回：无回跳。**但同样的两次操作在修复前的旧 release 包上也复现不出回跳**，说明这台手机上原始触发条件不成立——熄屏不销毁 SurfaceView 的 surface，按 HOME 切走后 vivo 会把任务冻结/回收（再回来落在首页而不是播放器）。⚠️ 装的都是 **release 包**，而 `Logu` 的开关默认只在 `BuildConfig.DEBUG` 打开（`BiliTerminal.kt:228-230`），所以本机 logcat 里**看不到任何 `PlayerActivity>` 埋点**——上面的 logcat 判据必须在 debug 包上执行。
  - 调研与修法见 `docs/archive/superpowers/specs/2026-10-11-player-network-resilience.md` P1-1 / P1-2。§四十四
- [ ] **评论侧「长按双弹窗」未复现**（`res/layout/cell_reply_list.xml` 兄弟节点分析证明布局层面不可能）。§30.2 / §30.4
- [ ] **`MANAGE_DEDUP_MS = 400L` 去重未真机验证**：`app/build.gradle` 只有 junit 与 org.json，**无 Robolectric**，`SystemClock` 无法进 JVM 单测。§30.3 / §30.4
- [ ] **300×300 表盘上弹窗实际可点性未在真机确认**。§30.4
- [x] ~~**未决项：`activity/reply/ReplyInfoActivity.kt` 分页插入起点 `+2` 疑似越界**~~ —— **已确认不是越界**（26.11.07 复核）：`ReplyInfoActivity.kt:211` 现在写的是 `startIndex + 1`，且 `:209-210` 的注释写明「数据下标 i >= 1 的楼中楼在 adapter 里的位置是 i + 1（第 0 位是根评论、第 1 位是『写评论』头部）」；热度排序走 `applySort()` 整体重排、不拼插入位置。原先按主评论列表（`ReplyFragment` 第 0 位是头部占位）的口径读出 `+2`，宿主页前提不同。§30.5b
- [ ] **动态详情页线上 NPE 未真机复现**：用户只给了混淆栈、未给触发场景。若补丁后同一栈还在，说明真凶是被 R8 内联进来的别处，需新 mapping 重新 retrace，并附触发页面 + 操作步骤。§38.5 / §38.6
- [ ] **收藏夹顶部工具条三处修法均未真机复现**（团队无设备）：
  - (1) 取消勾选不暗（排除法：`itemView.alpha` 只由 `VideoCardHolder.applySelection()` 写）。
  - (3) 若真机仍在列表中途展开，说明 `recyclerView.canScrollVertically(-1)` 不可靠，需改用 `computeVerticalScrollOffset() == 0`。§39.5
- [ ] **滚动收放与长按面板的真机手感未验证**：动画与手势走 View 层，JVM 单测覆盖不到（正因如此才抽出 `util/view/ScrollRetractDecider.kt`）。§37.3 / §37.2
- [ ] **新增反馈页 / 关于页入口 / 崩溃自动上报 / 启动弹窗全部未真机跑过**（条目已补进回归清单第 216~222、223~227 条，**尚未执行**）：
  - ① 关于页 → 反馈页能进、分类、4000 字上限、断网报错文案、成功清空；
  - ② 三个开关关掉后抓包确认无到 `rebiliterminal.zsapp.asia` 的请求；
  - ③ 故意崩溃一次，崩溃页显示「报错ID」且服务端 `/admin/api/crash` 可见；
  - ④ 公告页在上游接口挂掉时仍能显示自建公告（反向也要成立）。正文见 `docs/archive/review/fix-progress-history.md` §41.7。
- [ ] **隐私同意闸门未真机验证**（条目已补进回归清单第 228~231、236 条，**尚未执行**）：全新安装 / 清数据后首次启动必须弹一次；点「不同意」能正常进主界面且抓包无请求。正文见 `docs/archive/review/fix-progress-history.md` §41.8。
- [ ] **minSdk 19 未在 API 19~23 上做过任何运行验证** —— 本机 `D:\Program Files\android-sdk` 下**既无 `emulator` 也无 `system-images`**，本轮 `minSdk 24 → 19` 的结论全部来自静态审计 + 依赖元数据 + lint + dex 反汇编，**没有真机/模拟器背书**。
  - 需覆盖的真机用例已写进 `docs/archive/review/device-test-plan.md`（§1.2 设备侧 + T12 平台 API 兼容）：任一含 `TextClock` 的页面（43 个布局里都有，4.4 上原本会因 4 参 `TextView` 构造直接崩）、列表页 `AsyncLayoutInflaterX.fadeIn`（`activity/base/BaseActivity.kt:435`）、任一写 SharedPreferences 的操作（登录态刷新 / 设置项开关）、发评论或回复（`activity/reply/WriteReplyActivity.kt:177`）、设置页各图标（验证 `res/drawable-v21` 之外的兜底色可接受）、通知（三处 `NotificationChannel` 均有 `SDK_INT >= O` 闸，预期低版本不弹且不崩）。
  - 逐项静态结论与依赖下限见 `docs/architecture-map.md` §1.1。

- [ ] **调试桥（§3.14）未真机验证**：`DebugBridge` 的 `am broadcast` 能否回显结果、`appops set <包名> SYSTEM_ALERT_WINDOW allow` 后 `start` 能否真的拉起 `exported=false` 的页面、`http` 命令在风控下是否可用、日志落盘路径是否可 `adb pull` / `run-as` 取到——全部只过了编译与静态核实。逐条验证清单见 `docs/review/agent-test-guide.md` §2 与 §6。
  - **26.11.13 部分实测（补在这条下面）**：模拟器（AVD `rebili_watch37` / Android 17 / 466×466）上 `result data 回显`、`agent --ez on true`（回 `logDir=…`）、`notutorials`、`start --es activity … --es extras "中文值"`、`top` 复核**五项全部通过**，中文 extras 也不需要 ADBKeyboard。**但华为真机 `GTJ0220A17004229`（NOP-AN00 / Android 12 / sdk31）拦这条广播**：`am broadcast` 只回 `Broadcast completed: result=0`、无 data，logcat 有 `W BroadcastQueue: enter shouldPreventSendBroadcast second BroadcastRecord{…}`，`cmd appops set <包名> SYSTEM_ALERT_WINDOW allow` 也救不回来（结果文件 `logs/bridge-last.txt` 同样不生成）。⇒ 「能不能回显结果」根本不是包的问题，**取决于 ROM 允不允许 shell 发这条显式广播**；AI 做 L1 时要先把这一条当环境前提验一次，不通就换模拟器（或按 `agent-test-guide.md` §6 的兜底 `run-as <包名> am broadcast`）。
  - 风险最高的一条：个别精简 ROM 的 `adb shell` 可能不持有 `DUMP` 权限，那样导出组件会被 AMS 拒绝，调试桥整体不可用。兜底方案见手册 §6（`adb root`，或 `run-as <包名> am broadcast`——同 uid 不受导出限制）。

- [ ] **番剧界面补齐（§3.8）未真机验证**：季切 tab、两行选集卡片、追番按钮、全量选集弹窗、历史卡片一键续播、番剧心跳 5 秒 + 看完上报 -1 全部只过了编译 + JVM 单测。真机要看的：① 多季番能切到第二季/剧场版并出集；② 从别的季切回来回到这一季上次看的那一集；③ 一集播完后重进/从历史进来是接下一集而不是停在本集末尾；④ 历史里的番剧卡片点一下直接起播且位置对；⑤ 未登录点追番要提示，不能静默失败。

- [ ] **缩放后左右滑动调进度（§3.32）待真机确认**：开启「左右滑动控制进度」+「视频可缩放」→ 捏合放大 → 松手 → 单指横滑，应完全不出现进度浮层、不动进度条；双指缩回 1 倍后横滑恢复；再验关掉「缩放时可移动」时放大后单指拖动不再移位。判据单测覆盖不到 ACTION_UP 之后的手势分发。

- [ ] **长按顶栏三模式（§3.33）待真机确认**：①「不响应」下普通页面与视频详情页长按都无动作（OPPO 系手表长按顶部本意是系统控制中心，应用侧至少不能自己再动）；②「仅视频详情页」下普通页面无动作、视频详情页仍关闭所有详情页；③「全部页面」= 旧行为；④ 切档后不用退出页面即时生效；⑤ 26.10.19 显式关过旧开关的老用户，升级后设置页显示「仅视频详情页」（不是「不响应」）。

- [ ] **短视频取流协议修订（§3.41）待真机**：本会话无可用设备，只过了两 flavor 构建 + JVM 单测。真机用例：①进入短视频页首屏能出内容、连续上下滑不出现相邻重复视频；②反复退出再进短视频页，首屏正常（旧实现的静态页码已删）；③飞行模式进入 → 出现「获取视频列表失败」，恢复网络后从页面既有入口重试成功；④观察是否还有明显横屏内容（解析层已丢弃 `dimension` 明确宽>高且 `rotate==0` 的卡片）。

- [ ] **缓存视频断点续播（§3.53）待真机**：本机只过了 `:app:assembleDebug` + 两 flavor 单测，位置读写纯函数有 JVM 覆盖，但「退出再进真的接着播」必须真机。用例：① 缓存视频播到约 01:30 退出再进 → 从约 01:30 起播且提示「已从上次的位置播放」（±2s）；② 关掉「从历史位置播放」→ 从头且不提示；③ 看到距结尾 5 秒内 / 播完 → 下次从头；④ 多 P 视频 A 播一半、B 从头 → 各自记住不串；⑤ 单文件、DASH 双文件、听视频（`audio_only`）、虚拟合集连播、多 P 选择页五个入口都验；⑥ 缓存文件夹改名/移动后仍能续播；⑦ 播放中直接杀进程（后台划掉）→ 最多丢 15 秒；⑧ 文件被删/损坏/换成更短的版本 → 静默从头不崩。

- [ ] **短视频页圆屏适配（§3.65）真机未验证**：只在 MuMu 模拟器（`wm size 320x320` + `wm density 160`）上验过。真机用例：圆屏手表开启「圆屏适配」→ 进短视频页，顶栏标题/返回箭头不贴边、底栏进度条与按钮不被裁角、竖屏视频按内接圆缩小居中且不变形；关闭开关后恢复原样；圆屏开时上下切视频、左右滑详情页、弹幕显示均正常。

- [ ] **短视频画面尺寸 state 驱动（§3.98）的边界形态待真机**：`videoWidth==0` 的真实流（容器无分辨率 / probe 窗过短）与旋转 90/270 的真实视频（`MEDIA_INFO_VIDEO_ROTATION_CHANGED`）模拟器上都构造不出来（B 站 9:16 合一 MP4 尺寸非 0、rotation 全 0），这两条靠 `ShortVideoLayoutMathTest` + 代码走查兜底。真机用例：①刷到「容器元数据不带分辨率」的短视频时画面应留黑边而不是铺满拉伸；②刷到带旋转元数据的竖屏视频时比例正确、不横向压扁；③播放中 Home → 回前台，`dumpsys window windows` 里该窗口的 `fl` 应重新含 `KEEP_SCREEN_ON`；④暂停/切后台时不得残留 `KEEP_SCREEN_ON`。

- [ ] **三个入口的隐藏开关（§3.137；其中私有分享行 26.11.08 起默认显示，见 §3.139）的登录态 / 会话态未验证**。**复现前置**：Debug 包（本次模拟器装的是含 `private/` 注入的 `standardDebug` universal 包，故详情页设置里能看到「分享到其他设备」；公开包没有这一条，见用例 C 第 4 步）；一个**测试账号**登录（本组用例全是只读操作，不涉及删除/清空）。**回菜单页的通用手势**：在任意页面顶栏长按约 1 秒（`BaseActivity.setupTopbarLongPressToHome()`，受「顶栏长按」设置控制，默认全部页面）——`MenuActivity` 不导出，`am start` 会被拒。

  - **用例 A｜有会话时「继续播放」才置顶**：①设置 → 内容与浏览 → 菜单设置，在「未启用」区末尾找到「继续播放」，点一次移到「已启用」区（可长按拖动排位置）；②长按顶栏回菜单页——**此时若从未播放过任何视频，最上方不应出现「继续播放」**（`PlayerApi.buildResumeIntent` 会把过期/过短的会话过滤掉，这是设计而非缺陷）；③播放任意视频超过约 30 秒后退出播放页，再回菜单页——最上方应出现「继续播放」并排在「推荐」之前，点它应直接回到刚才的视频与播放位置；④回菜单设置把它移回「未启用」——回菜单页后该项立即消失（无论有没有会话）。
  - **用例 B｜登录态下「视频笔记」**：①详情页设置里「视频笔记」默认应为关；②打开任意视频详情页，动作区（点赞 / 收藏 / 转发那一排）**不应有**「笔记」；③回设置打开该开关，重进同一视频——动作区出现「笔记」，点进去能打开笔记页并加载列表（若列表仍报「无法正常加载」，那是本文件更下方「笔记『无法正常加载』根因未确证」那条既有问题，不算本次引入）；④关掉开关重进详情页——「笔记」又消失。未登录时无论开关如何都恒隐藏（代码里 `else if (!isNoteEntryEnabled())` 分支之前先判登录态）。
  - **用例 C｜私有分享行（仅带 `private/` 的构建有这条开关）**：①详情页设置里「分享到其他设备」**默认应为开**（26.11.08 起按用户要求改回默认显示，见 §3.139），进视频详情页时动作区**应直接有**该行，点进去能进分享页并出二维码；②回设置关掉该开关，重进同一视频——该行消失，且关着的时候连反射都不做（`VideoInfoFragment.kt` 里 `if (DetailEntryPrefs.isShareRowEnabled())` 之外不触碰 `OptionalFeatures.attachDetailActionRow`）；③再打开重进——该行回来；④反向确认：用**不含** `private/` 的包（`build_tui.py` 不勾同步私有源码）打开详情页设置，只有「视频笔记」，**没有**「分享到其他设备」。
  - **用例 D｜设置页搜索能命中**：在设置首页顶部搜索框输入「笔记」——应出现「视频笔记」，点进去应滚到该开关并高亮（项名必须与 `SettingInfoActivity` 里的 `SettingSection.name` 逐字一致，改文案时两边要一起改）；输入「跨设备」——同上（仅带 `private/` 的构建）。命令行验证中文输入需先装 ADBKeyboard。
  - **用例 E｜466×466 圆屏排版**：圆屏机器（或装了 wear 系统镜像的 AVD）上打开视频详情页，确认动作区多出的那行没有被圆角裁掉、整块能滚到底、「笔记」与分享行的点击命中区正常。

  模拟器（AVD `Medium_Phone_API_37.0`，Android 17、1080×2400；本机 `wear5` 的系统镜像缺失、起不来）上已实测：①默认配置下菜单页依次是「登录 / 推荐 / 短视频 / 热门 / 热搜 / 搜索 / 缓存 / 设置 / 扫一扫 / 退出」，**没有**「继续播放」；②「设置 → 内容与浏览 → 菜单设置」的「未启用」区末尾有「继续播放」，点回已启用后再点它提示「继续播放」不是页面、不能设为启动页（`adb logcat` 抓到 `MsgUtil>showMsg: 「继续播放」不是页面，不能设为启动页`）；③「设置 → 内容与浏览 → 详情页设置」在原 7 个开关之后多了「视频笔记」（默认关）与「分享到其他设备」（默认关；这条只在 `OptionalFeatures.available` 为真的构建里出现，本次模拟器装的是带 `private/` 注入的 standard debug 包）；④把「视频笔记」打开后设备上 `shared_prefs/default.xml` 多出 `<boolean name="detail_note_entry" value="true" />`，关掉后恢复，键名与 `SettingsKeys.DETAIL_NOTE_ENTRY` 一致。**仍未验的（都要登录态或真实播放会话）**：①有可续播会话时，把「继续播放」放到已启用区，菜单最上方应出现它、点进去回到上次视频与位置；再关掉开关应立刻消失；②登录态下「视频笔记」开 → 详情页动作区出现「笔记」且能进笔记页，关 → 不出现；③带 `private/` 的构建里「分享到其他设备」开 → 详情页出现该行并能进分享页，关 → 不出现且不做反射；④设置页搜索框搜「笔记」「跨设备」应命中这两条并滚到高亮；⑤466×466 圆屏上动作区多一行后的排版与滚动观感。**未验证**。（**26.11.08 更新**：§3.139 已把私有分享行改回默认显示，上面 ③④ 里「默认关」的口径过期，只作当时的测量记录。）

### 2.2 功能与工程待办

- [ ] **`ConfirmDialogActivity` / `activity_confirm_dialog.xml` 是疑似死页面，要不要删要人定（26.11.14 记）**：全仓搜不到 `ConfirmDialogActivity` 的任何调用点（只剩 `AndroidManifest.xml:502` 的登记与 `docs/architecture-map.md` 契约表里的一行），但它的布局 `activity_confirm_dialog.xml:41-63` 仍是与弹窗老布局同形状的「横排 + `gravity="end"` + 两个 `wrap_content`」（#161 那类命中偏移的温床）。三条路：**删**（连同清单登记与文档契约一起清）、**留但把按钮改成整行堆叠**（若还打算当契约页用）、**留原样**（承认它是历史遗留）。在定案前本轮不动它。

- [ ] **三通道改造的收尾项（26.11.08）**：①站点仓库 `bili-terminal-site` 的改动只在本地 commit（`7accc86`，`netlify/functions/release.js` + `tools/sync-release.py`）——**远端仓库当前拉不到，尚未 push**，等网络/权限恢复后推送并触发 Netlify 重新部署；②`per_page=30` 的实际返回条数没实测，预发布超过 30 条时要翻页（Gitee 按升序返回，翻页方向与 GitHub 相反）；③站点 `index.html:682`/`:801`、`legacy.html:241`/`:255` 仍是 `releases/latest/download/` 形式（`latest` 指向预发布时会指错版本，现状走不到）；④`server/rebiliterminal_mcp.py` 的 `stats` 白名单没透出新增的 `channels`，`server/selftest.py` 也没加 channel 断言；⑤`26.11.08-beta.1` / `-exp.1` 首次真实发版才能验证「预发布不进 `releases/latest`、官网仍取稳定版」。

- [x] **弹幕行数「听用户的」（26.11.08 已定，按方案①执行）**：`player/DanmakuManager.kt` 原用 `DeviceProfile.capDanmakuMaxLines(...)` 把用户设置的行数按档位压到 6/10/15（诊断报告 245 里用户设了 50、实际只上 6 行），与已撤掉的「档位静默改解码 / 清晰度」同类。用户拍板**完全听用户的**：封顶逻辑删除、`util/DeviceProfile.kt` 整份删除、设置页默认值与引擎对齐为 15；代价是低端机用户可以自己把行数调到超出性能承受范围，属用户自选。实测效果待真机观感确认（`docs/review/manual-test-guide-*.md` 口径）。

- [ ] **表冠三处加固 + 系统优先补位（26.11.07 / 26.11.07.1，反馈 #103/#107、issue #21；26.11.08 起也覆盖反馈 #110「评论区转表冠先向下动、再回弹一下」）待带表冠设备验证**：新增纯函数 `ui/widget/RotaryEventPolicy.kt`（18 例 JVM 单测），`ui/widget/RotaryEncoderSupport.kt` 随之改三处——①位移算出 0 时不再吞事件（原先控件级 `handle()` 无论算出多少都 return true，事件被吃、画面不动、DecorView 兜底也接不到）；②滚动量在 `AXIS_SCROLL` 为 0 时回落 `AXIS_VSCROLL`；③兜底找目标要求与窗口有实际交集，不再只看 `isShown()`（视频详情页 `offscreenPageLimit = 页数`，离屏页始终 shown）。原理与两条既有约束见 `docs/architecture-map.md` §3.2 末段。L0 已过（两 flavor 构建 + 单测）；**维护者没有表冠设备**，真机结论只能等带表冠用户：①首页推荐列表转表冠手感不变；②视频详情-简介页转表冠能一直滚到底；③设置里关掉「表冠适配」（默认态）后**每个界面都能用表圈转**，且同一页不会出现两份滚动量叠加（issue #21 的「软件和系统滚动效果叠加」应消失），系统自带的表冠震动仍在；④开启「表冠适配」后由 App 完全接管、下面的灵敏度倍数生效，手感与 26.11.07 一致（#20 类用户不受影响）；⑤若仍转不动，让用户勾「附带应用日志」再反馈，日志里应有 `RotaryEncoder` 开头的 W 行（同进程最多 6 条），按它区分「事件没进 App / 进了但不是表冠源 / 进了但位移为 0」三种形态。**未验证**。
- [x] ~~**「自动跳过片头片尾」处于撤回状态**~~ ——26.10.22 **已恢复**（默认关闭），恢复清单 ①–④ 已完成，⑤⑥（发版公告与版本号）随下次发版做。§3.3
- [ ] **播放器弱网 / 直播：P0-1、P0-3、P1-1、P1-2、P2-1 与半个 P2-3 已实施，其余待办**（26.10.11 已过编译 + 两 flavor 各 478 个 JVM 单测全绿；真机只验证到「播放器能正常起播、回前台位置未复现回跳」，**看门狗/线路轮换未在真机触发**——release 包 `Logu` 默认关闭，需 debug 包 + 真实直播间）：
  - 已实施：**P0-1** `activity/player/PlayerActivity.kt` 的 `setDisplay()` 给在线流加 `rw_timeout=10s` / `addrinfo_timeout=5s` / `reconnect_streamed=1` / `reconnect_delay_max=5s`；**P0-3** 新增 `checkStall()` 停滞看门狗（直播 20s / 点播 30s 不动就重试，直播切候选线路、点播原地重建；最多 3 次、冷却 15s、位置前进 5s 后复位）；**P1-1** 状态保存恢复；**P1-2** 重挂 Surface 改用 `video_now`；**P2-1** 删 `activity/video/ShortVideoPlayerActivity.kt` 里实测不存在的 `max_cached_duration`；**P2-3 的一半** 新增纯函数 `util/live/LiveStreamSelector.kt`（14 个单测），候选按「FLV 各线路 → HLS」排序，随 Intent `live_url_candidates` 交给播放器做运行时线路轮换。
  - **未做**：P0-2 的「重连前重调 `getRoomPlayInfo` 拿新 URL」（现在只在已到手的候选里轮换，URL 过期后仍会失败）、P1-3 后台保活与音频焦点、P2-2 短视频激进参数复核、`PlayerActivity.kt:892` 的 `dns_cache_clear`（二手证据不足，未动）。
  - **明确不采纳**：P0-4 提高直播默认清晰度（`util/TerminalContext.java:259` 写死 `qn=80`=流畅）——用户诉求是弱网可看，低码率是刻意保守；`reconnect_at_eof` 不开——点播正常播完会被当成 EOF 触发重连，有「永远播不完」的风险。
  - 建议清单见 `docs/archive/superpowers/specs/2026-10-11-player-network-resilience.md` §4（已归档，行号为当时位置）；该文档 §2.4 附**本仓库 `.so` 的选项存在性实测**（`reconnect_on_network_error`、`max_cached_duration`、`http-detect-range-support` 均不存在）。§四十四
- [ ] **播放器核合并剩余项**（26.10.27 从已归档的 `docs/archive/superpowers/specs/2026-09-10-player-core-merge.md` 回迁）：S1–S3 已完成，S6 里 `PlayerIntegrator` / `PlayerScaleMode` 已删、`DanmakuManager.loadFromProtobuf` 已换成 `loadFromProtobufSegments`。仍未做：**S4** 短视频迁到 `player/VideoPlayerCore.kt`（它至今**没有任何消费方**，`PlayerActivity` 只在注释里提到它；短视频仍直接用 `IjkPlayerBridge`）；**S5 后半** `TextureView` 复用与相邻页预创建；**S6 收尾** `player/PlayerControlDelegate.kt` 全库零外部引用，删或接线待决定。
- [ ] **下载链路剩余缺陷**（26.10.27 从已归档的 `docs/archive/superpowers/specs/2026-10-13-download-bug-diagnosis.md` / `2026-10-13-download-engine-optimization.md` 回迁；C3、B1 播放侧、A1/A2、A5 已修，见归档快照 §3.12/§3.13/§3.36）：
  - **B2 已修（26.11.07，调研清单第 7 条）**：`activity/video/local/LocalPageChooseActivity.kt` 删分 P 时不再对 `videoFileList` 用页下标 `removeAt(position)`，改由纯函数 `util/LocalPageFileIndexPolicy.kt` 按「文件所在目录名 == 页目录名」找出该页占用的全部下标、从大到小删（双文件目录每页占 2 项），`buildPageMediaFiles()` 的旧格式兜底也改走同一映射；6 例 JVM 单测。**B3 已修（反馈 #51，早于本批）**：`LocalListActivity.playVirtualCollection()` 现在同时构建 `videoUrlList` / `audioUrlList`，双文件目录的 `.mp4` 作主文件、同目录 `.m4a` 作外部音轨。26.10.25 起新下载合并成单文件（§3.66），这两条只在旧缓存 / 合并失败时可达。
  - **A3** 分片失败回退时进度先满后退；**A4** 未知大小时伪进度封顶 0.9（`DownloadProgressMath.PSEUDO_MAX`）；**A6** 通知栏总进度按「当前文件」口径汇总，跨阶段时可能回退。
  - **引擎层**：没有真正的断点续传（分片失败仍整文件单连接重下）、没有「仅 WiFi 下载」、没有磁盘空间预检（只有合并前的 `StatFs` 检查）、弹幕仍走单文件 XML。分片看门狗 / Range 校验 / 强制 HTTP/1.1 已于 26.10.25 落地（§3.63）。
- [ ] **26.10.05 那个已发布的 Release 说明没能补上**：本机无 `gh` CLI 也无 GitHub token，只能人工在网页端编辑，把 `update_log_current` 的 28 条粘进去。§40.5
- [ ] **服务端没有告警渠道**：只有 systemd `Restart` + `health.log` 留痕（`*/10` 探活 cron 失败只追加一行，要真告警还得接通知渠道）。§42.9
- [ ] **管理控制台只有口令登录**，没有 TOTP / IP 白名单；口令泄露 = 全部后台能力。§42.9
- [ ] **控制台 WebUI 没有真机 / 真浏览器回归记录**（环境无浏览器），只验证了 HTML 结构与接口联通。§42.9
- [ ] **控制台仍 pending 的 7 条反馈**（26.11.01 批量标已处理后剩余，逐条结论见 §3.99）：**#60** 老掉登录（要上周期性 Cookie 刷新）、**#48** 无法下载更新（疑似已被证书信任修复解决，已回管理员备注请用户升级后复测）、**#41** 静音拖进度出声（只在外挂音轨出现，等用户录屏）、**#34** 视频分段导致跳片头片尾（未定位）、**#31** 载入中切后台回来比例错（需真机复现）、**#30** 缓存文件夹「加入」按钮（功能请求）、**#20** 表冠转动慢（需原版终端倍率对照）。
- [ ] **话题广场仍是单页**（`bottom = true`，不翻页）；`page_info.has_more` 已在数据里，将来要翻页可直接接。§三十四
- [ ] **`WriteReplyActivity.kt` 的 `pendingUploads++/--`（`:224`/`:253`）未加同步保护**：已发现主线程入口与后台 `finally` 各改一次、无同步，「与本次用户报的 bug 无关，改动会引入无法验证的风险」→ **发现但不动**。§29.5
- [ ] **§32 遗留的视觉面未动**：
  - 新增 `colorError` 语义色**未重新做对比度测量**（`docs/archive/visual-experience-report.md` §2.25：`#EBE0E2` 叠 `#FF6699` 约 2.1:1）；
  - §2.12「86 处布局引用静态调色板」与 `styles.xml` 组件样式硬编码同源，本次未动。§三十二
- [ ] **收藏夹排序行「更醒目」的视觉标识没做**（无依据，且会动 `res/layout/activity_simple_refresh.xml`）。§二十二
- [ ] **`docs/review/real-device-regression-checklist.md` 第 159 条未改**（不属该轮范围）。§二十二
- [ ] **「个性化设置」开关落点需用户确认**：`long_press_panel_enable` 落在 `activity/settings/SettingPrefActivity.kt`（`setPageName("偏好设置")`，「内容与浏览 → 通用偏好」进入）的「功能」组、紧挨「长按复制」；用户心里的「个性化设置」若是别处，搬走只需改这一行。§37.1
- [ ] **`DynamicApiTest` 曾失败的 2 例（`DynamicApiTest.kt:151`/`:203`）与 `NoteApiTest.kt:198` 1 例**：**已闭环**（§36.3 修掉并全绿），修法见归档「长期结论」；此处仅作登记。
- **功能类 backlog 一律看** `docs/superpowers/plans/2026-08-27-new-features-roadmap.md`，不在本文件展开。

### 2.3 明确不做（别再排期）

- **F4 漫画**：快照 `bilibili-API/docs/manga/` 无任何「追漫/收藏漫画列表」接口，也无漫画搜索接口，项目内漫画实现为零（只有 `activity/user/VipActivity.kt:79-80` 会员权益文案）。
- 用户 26.10.04 明确「漫画算了」，记 **不做**（不是暂缓：暂缓意味着还打算做）。§三十一
- **番剧 B/E 两项（26.10.11 用户拍板暂不做，别顺手补）**：B = 进番剧详情页自动查历史接口定位"上次看到的那一集"；E = 实验室里的「番剧进度诊断页」。同一轮对比里远端的 `BangumiActivity.java`（番剧主页：新番/追番/索引）是**跨全部 tag 都不可达的死代码**，同样不要照抄。§3.8

### 2.4 视觉技术债（26.10.10 从 `docs/archive/visual-experience-report.md` 回迁；数字均为当时 grep 实测）

> 真机验证项已并进 `docs/review/real-device-regression-checklist.md` 第十三节（第 238~247 条）。下面只留「代码里真的还没做」的部分。

- [ ] **顶栏仍是 45 份手抄复制品**：`cell_topbar.xml` 与 `include layout="@layout/cell_topbar"` 全工程 **0 匹配**，`android:id="@+id/top"` 在 **45 个 layout** 里各自手写（`activity_simple_refresh.xml:9`、`activity_menu.xml:10`、`activity_player.xml:144` …），`@dimen/topbar_height`（30dp / w300dp 36dp）**0 引用**（死 token）；退出靠 `BaseActivity.kt:182-197 setTopbarExit()` 代码兜。**批次 4 的公共顶栏成果实际已被回退**（详见 `docs/architecture-map.md` §8.1/§8.5/§7.4），顶部返回是 45 个页面的唯一返回入口、高度无 48dp 保证。值得做（风险中）。
- [x] **无障碍：`contentDescription` 覆盖率 4.3%**（26.11.07 已收口，见 §1「无障碍与触控热区」）：改造前全 `res/layout` 只有 12 处 `contentDescription`（其中 1 处 `@null`）、0 处 `importantForAccessibility`，而 `<ImageView|<ImageButton` 共 124 处；现在 124 个图片控件**全部**显式声明（49 个可读描述 + 75 个装饰图 `@null` + `importantForAccessibility="no"`），由 `ui/a11y/LayoutAccessibilityTest` 锁死。真机 TalkBack 逐屏走查见 §2.1。
- [x] **播放器触控热区大量 <48dp**（26.11.07 已收口，见 §1「无障碍与触控热区」）：底部按钮 32×37→44×44dp、进度条 20→48dp、弹幕发送 35→48dp、左右两列 28×28→48dp 宽 × 自适应高（小屏实测 48×29dp），图标视觉尺寸与贴边位置不变，底部行改 `weight` 均分 + 各自横向滚动。**仍未达 48×48 的部分**：小屏手表竖列高度受可用高度限制，要改交互才能满足（§1 已知限制 / §2.1 真机项）。
- [ ] **无自适应图标**：`res/mipmap*` 只有 `mipmap-nodpi/`（约 52 个 png/webp），无 `mipmap-anydpi-v26`、无 `ic_launcher_foreground/background`；桌面图标形状由系统强制裁切。需要设计资产，非纯代码任务。
- [ ] **残留 px 单位与全透明短写色**：`layout_height="0px"` `activity_download.xml:23`、`cell_video_local.xml:87`，`1px` `activity_player.xml:16/17`（共 5 处）；`background="#6000"` `activity_search.xml:129`，`background="#0000"` `cell_article_end.xml:117/140/164` + `fragment_video_info.xml:229/252/276`（共 7 处）。一次改完，零风险。
- [ ] **过小字号**：`textSize="8|9|10|11sp"` 共 **57 处**，其中真正不可读的 6 处是 8sp（`activity_player.xml:181`）、9sp（`activity_captcha_webview.xml:33`）、10sp（`activity_feedback.xml:66/109`、`activity_player.xml:479/493`）；`android:alpha` 在 layout 下仍有 **142 处**（文字靠 alpha 拉层级基本没动）。先修那 6 处成本极低。
- [ ] **浅色主题 `Theme.BiliClient.Light` 是死样式**：`themes.xml:45` 定义、全工程 **0 引用**（活的是 7 套 `Theme.*` + `Theme.*.NoSwipe.AppCompat`）；弹幕颜色仍无设置项（grep `danmaku_.*color` 0 匹配）。二选一：接线或删掉。
- [ ] **`colors.xml` 别名膨胀**：`<color name=` **≥328 条**（`zhihu_*`/`iqiyi_*`/`purple_fantasy_*`/`rainbow_*`/`classic_gray_*`/`terminal_*` 各约 60 条，绝大多数指向同一 hex）。有了 `ui/appearance/ColorScheme.kt` 这个真源后，别名层是纯负担；清理需一批回归。风险中。

**别再重复排查（已排除）**：WebView 白底闪屏**不存在**；菜单按钮 `MaterialButton` 默认 `minHeight=48dip` **已达标**；`ui/widget/recycler/` 与 `fragment_empty.xml` 是**死代码**（不当在用组件排查；`BiliTerminalApp.kt` 已于 26.10.02 删除）。**不做**：裸 `Activity` 收编（`PlayerActivity`/`ImageViewerActivity`/`SplashActivity`/`GetIntentActivity`）——播放器保持黑色系统栏是有意为之，且 `PlayerActivity.kt` 3000 行，收益不抵回归风险（理由已进 `docs/architecture-map.md` §8.7.3）。**可接受**：`CardStyle`/`ButtonStyle` 强制 `clickable+focusable`；间距/圆角 token 已建但布局未全量改用；同构布局重复；布局层静态色已清干净（`@color/(card_dark|pink|divider_dark|pink_light)` 在 `res/layout` 0 处）。

### 2.5 控制台反馈待处理（26.11.08 核实后保留的 14 条）

> 26.11.08 把控制台 32 条 pending 逐条对源码核实，只给已修的 18 条标了已处理（见 §1 末条）。下面 14 条**故意没标**，按「核实结论 + 要什么才能推进」记下来，别重复排查。

- **#127 内容摘要用不了**（26.11.07）：入口与接口都在（`activity/video/info/VideoInfoFragment.kt` 的 `video_summary` 按钮 → `api/VideoInfoApi.getVideoConclusion`），但「服务端没摘要」（响应里 `data.code` 1 / -1）与「请求失败」被压成同一个 `null`，用户只能看到「获取视频摘要失败」。要么按 `code` 分文案，要么先确认就是服务端无数据。
- **#117 小米手表1 播放卡住、无法退出**（26.11.07）：卡顿面大概率已随 `1a66c0c`（撤掉档位静默软解）解决；**「无法退出」没有任何对应修复**——`PlayerActivity` 的 `finish()` 仍在主线程直读 `ijkPlayer!!.currentPosition`，而同文件别处注释自己写着那是取 native 锁的 JNI 调用、主线程调有卡死风险。用户补充「只有横屏出现」。需要 `logcat -b crash` 或 ANR 的主线程栈。
- **#102 视频缩放用不了了**（26.11.06）：静态链路找不到退化点（`activity/player/ScaleGestureDetector` 自 Initial commit 未改、`getScaleFactor()` 与 `1f..5f` 夹取正常、双指事件也不被 `handlePlayerTouchExtras` 吃掉）。两条合法「没反应」路径：听视频模式 `layout_video` 为 GONE、锁定态 `control_layout` 整条 GONE。需要用户 `player_scale` 的存档值 + 事发时是否在听视频 / 锁定态。
- ~~**#125 选择窗口「取消 / 确定」点击错位**（26.11.07，HUAWEI HWARC）~~ → **26.11.13 已按构造性修法消掉**（见 §1 首条，同族 #161 / #54）：按钮改成整行宽上下堆叠，行内任意位置都是同一个动作，横向命中偏移不再有害。当初的判断仍然成立：老布局的按钮行是 `gravity=END` + `wrap_content`（左 144px / 右 30px 死区，实测于 AVD），本仓代码里读不出「可视位置 ≠ 命中区」的写法，偏移来自 OEM 输入层。**真表复测仍待做**（L2）。
- **#118 扫一扫关不掉、总在菜单最末尾**（26.11.07）：**根因已定位**——`util/MenuConfig.loadStoredEnabled` 的合法判据要求 `OPTIONAL_ITEMS.all { it in parsed }`，用户把可选私有项关掉后配置永远不合法，落进容错分支又被 `lenient + OPTIONAL_ITEMS.filter { it !in lenient }` **追加回末尾并写盘**，下次渲染照样出现。修法照 `ensureExit` / `ensureBangumi`：给可选项也记一个「用户动过」的键，动过就不再补回。**本轮未改**（改的是菜单存档语义，且只有注入私有源码的包能复现）。需要另行决定：选择项怎么回填。
- **#120 私信相关五项（部分实现）**：换行 `(a)` 与「回首页」`(d)` 已有（`activity_private_msg.xml` 多行输入 / 顶栏长按关掉所有详情页），表冠缩放 `(b)`、私信显示时间 `(e)` 未做，电量百分比 `(c)` 只在顶栏灵动岛的「低电 / 充电」窗口里露出（该功能与圆屏适配已解耦，单独开也能看到），不是常显。
- **#124 / #115 / #123 / #122 / #121 / #119**（功能请求，均未实现）：#124「开屏首页可自定义」`(a)` 与「断网直接进应用」`(b)` 已有，长按卡片的「稍后再看 / 不感兴趣 / 黑名单」`(c)` 未做（长按仍是快速缓存）；#115 推荐页长按「不感兴趣」未做（全库无此文案）；#123 只有单条长按删搜索记录，没有「清空全部」；#122 公开侧根本没有「用户协议」页可挂兜底入口；#121 私信页那句「私信有可能被拦截」是无条件 toast，既无开关也无一次性静音；#119 看图页没有自己的不透明底，`ui/appearance/ColorScheme.applyWindowTheme` 设的自定义背景图直接透出来。
- **#90 / #87 缓存下载失败**（26.10.27 / 26.11.01）：DASH（1080P+ 音画分离）已支持并合并成单文件，但多段 `durl` 只取第 0 段（`api/PlayerApi` 无遍历拼接）、失败原因只落日志不告知用户（HTTP 非 2xx 只在 `service/DownloadService` 里 log）、没有断点续传。~~`backupUrl` 解析后无人使用~~ → **26.11.09 已接上**（反馈 #136，见 §3.143：playurl 的主 / 备线路贯通到下载器，网络类失败逐条换线重试，失败任务的 `state="error"` 记录与 `.DOWNLOADING` 残留不再堵死重新入队）。剩下这三项仍需用户日志里的 HTTP 码或复现机型 + 视频 + 清晰度才能定位。
- **#135 侧边左滑退出视频（26.11.09，aqsh_watch 华强北手表）**：用户报「侧边左滑手势返回退不出视频」。开发者当天在控制台已回复「这是刻意设计（有些手表右滑就是退出视频，而放大后的视频又要靠拖动才能挪）」，并提议加一个「关闭此功能」的开关——**产品取舍尚未拍板，代码未动**。要做就得先定：开关放播放器设置还是全局设置、默认开还是关。

## 3. 最新状态快照（26.10.22 ~ 26.11.06，流水，按关键词 grep 不通读）

> §3.0 ~ §3.51（26.10.09 ~ 26.10.21）已原样归档到 `docs/archive/review/fix-progress-snapshots-261009-261021.md`，**编号不变**：本文其他地方写的 §3.0~§3.51 都去那里找。

### 3.52 存储兼容：默认目录按 API 分档 + 无全盘权限时自动降级（Wear OS，26.10.22，待真机）

- **背景**：§3.28 只把「判据」从权限查询改成试写，试写失败之后仍然是 `finish()` / `return` —— 手表（Wear OS）上「所有文件访问」的接口存在，但系统 UI 不提供这个开关，用户授不了权，于是缓存/下载整条链路走不通。用户要求同时适配高低版本 Android 与这类无法授权的设备。
- **根因（能力假设错）**：默认目录 `Android/media/<包名>/` 在 API 29+ 属公共媒体目录，写它需要 `MANAGE_EXTERNAL_STORAGE`；API 30+ 上只有用户在系统 UI 里勾选才生效，`isExternalStorageManager()` 查得再准、权限在清单里声明得再全都救不了。而下载链路把「能写这个目录」当成了功能前提。
- **改法（`util/FileUtil.java` + 新增 `util/StoragePathPolicy.kt`）**：
  - `util/StoragePathPolicy.kt`（纯逻辑、无 Android API，可 JVM 单测）：`defaultVideoDir(apiLevel, mediaDir, appExternalDir, internalDir)` —— API≥21 用 `Android/media/<包名>/`，API 19/20 用 `getExternalFilesDir("video")`（应用专属、免权限），都拿不到再退 `filesDir/video`；`chooseVideoDir(...)` 保留老用户目录；`hasRealContent(names)` 把 `.nomedia` 这类隐藏标记排除在「有内容」之外。
  - `FileUtil.getVideoDownloadPath()` 拆成「正式目录 `getConfiguredVideoDownloadPath()`」与「降级目录 `getFallbackVideoDownloadPath()`（`getExternalFilesDir("video")` → `filesDir/video`）」，进程级 `videoFallbackActive` 决定返回哪个 —— 下载服务、缓存列表、元数据必须看到同一个根目录，不能各算各的。
  - `resolveVideoDownloadDir(Context)` / `resolvePictureDownloadDir(Context, File)`：正式目录可写就用正式目录（若此前降级过，`mergeMoveDirectoryContents` 把降级目录里的东西搬回，只增不覆盖、同名改成 `name (1).ext`、跳过 `.nomedia`、绝不删目标已有内容）；正式目录不可写但降级目录可写就降级并 toast 实际位置（每个进程只提示一次）；两个都写不了才返回 null。
  - **下载链路不再主动跳系统设置页**（手表上那是死路），`requestStoragePermission` 只保留给设置页里的手动入口。所有入口统一先 resolve：`LocalListActivity`（含 `onResume` 复检，降级状态变过就刷新列表）、`VideoInfoFragment.downloadClick`、`DownloadActivity.onCreate`（图片走 `resolvePictureDownloadDir`）、`VideoQuickCache.handle`（列表长按缓存，以前完全不判可写）、`DownloadService.startSingle` / `startDownload` / `onStartCommand`（服务可能在新进程恢复，标记为空，必须再 resolve 一次）。
  - 设置页「缓存路径」预填 `getConfiguredVideoDownloadPath()`（降级时不会把降级目录写进设置项），新增「去系统设置授予所有文件访问」入口与说明（手表会提示打不开，下载自动降级）。
  - 图片降级保存到应用专属目录时，完成提示改用 `msg_picture_saved_fallback` 明说「不在系统相册中」，且不再调 `PictureMediaStore.scan`（降级目录扫了也不会有 MediaStore 记录，白扫）。
- **为什么不用其它方案**：SAF `ACTION_OPEN_DOCUMENT_TREE` 在手表上通常没有 `DocumentsUI`；`MediaStore` 插入只能管媒体单文件，而缓存目录还要放 `.quality` / `.DOWNLOADING` / 弹幕等附属文件；回退 `targetSdk 29` + `requestLegacyExternalStorage` 是能力倒退，且本仓 `targetSdk 34` 下已失效。结论：把「全盘权限」从**必需**变成**可选**。
- **单测**：新增 `StoragePathPolicyTest` 12 例（API 分档、配置优先、老目录保留、隐藏文件不算内容）、`FileUtilMergeMoveTest` 5 例（搬移、同名不覆盖、递归合并、跳过 `.nomedia`、空输入安全）。判据与理由已同步 `docs/architecture-map.md` §7.41 ④。
- **验证（本机，已跑）**：`$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; ./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --offline --no-configuration-cache` → `BUILD SUCCESSFUL in 1m 49s`；两 flavor 各 **75 个测试类 / 800 例 / 0 失败 0 错误 0 跳过**。
- **真机待验（全部未验证）**：① 手表 Android 7/8/9/11 首次进缓存页 / 点下载直接成功，不弹系统设置页；② 把缓存路径改成公共 `/sdcard/Download/xxx`（无权限）后下载**自动降级**并提示实际位置，文件落在应用专属目录、缓存页能列出；③ PC 的 MTP / 文件管理器能看到该目录（`Android/media` 比 `Android/data` 可见性略好，但都比公共目录差，需在文档/教程里说清）；④ 图片降级保存提示「不在相册中」；⑤ 手机上授权后回到缓存页自动切回正式目录，降级期间下的缓存被搬回。

### 3.53 缓存视频断点续播：内置播放器播本地/缓存视频也从上次位置起（26.10.22，待真机）

- **需求**：内置播放器播放**下载的缓存视频**时也要「从历史位置播放」。此前只有在线视频（含番剧）能续播——位置来自服务端观看历史；本地视频入口构造 `PlayerData(TYPE_LOCAL)` 时从不填 `progress`，`onPrepared` 的 `resumeTarget` 恒为 0，所以永远从头。用户确认口径：进度存 `.video_meta.json`、复用现有「从历史位置播放」开关（不新增）、多 P 按分 P 分别记、纯本地记录、四个入口全要（缓存列表单文件/DASH、多P 选择页、虚拟合集连播、听视频/纯音频）。
- **为什么入口一行都不用改**：四个入口传的 `video_url` 都指向该视频（或该分 P）自己的缓存目录，`File(video_url).absoluteFile.parentFile` 就是 `.video_meta.json` 所在目录。播放器在 `getExtras()` 里反推目录、在 `doSwitchPage()` 里跟着切 P 换目录，入口侧无需传新 extra。
- **数据层**：
  - `model/VideoMeta.java` 新增 `progressMs`（0=从头/已看完）与 `durationMs`（0=未知；存它是为了判断"停在距结尾几秒"），`toJson`/`fromJson` 读写，老 meta 缺键时 `optLong` 兜 0。
  - 新增 `util/LocalVideoProgress.kt`（纯函数、无 Context/文件，可 JVM 单测）：读与写用**同一条判定**，避免"存得进去读不出来"——`<=5s` 无意义、`时长-位置<=5s` 视为看完、位置超过时长视为看完；时长未知（<=0）只按位置判断。
  - `util/VideoMetaManager.kt`：新增 `readResumeMs(folder)` 与 `@Synchronized saveProgress(folder, positionMs, durationMs)`；`saveMeta` 也加 `@Synchronized`。`saveProgress` **先读旧 meta 再合并**（直接 new 一份会冲掉 title/画质列表）。
  - `service/download/DownloadRepository.kt:saveVideoMeta` 原来是 new 一份 `VideoMeta()` 整份覆盖 —— 这正是重下载/换清晰度会冲掉进度的坑，改成先 `readMeta` 合并。
- **播放器侧（`activity/player/PlayerActivity.kt`）**：`getExtras` 里对非 http 的 url 设 `localMediaDir` 并用 `readResumeMs` 补 `progress_history`（单位毫秒，与在线一致）；`doSwitchPage` 切 P 时重算目录并清去重标记；新增 `maybeSaveLocalProgress()`（挂在既有 250ms 定时器上，墙钟 15s 节流）+ `saveLocalProgressNow(reason)`（**主线程同步写**：异步会让"退出补写"被队列里的周期写覆盖）；落盘点＝周期/`onPause`/`onStop`/`onDestroy`/`finish`/`onCompletion`（看完清零）；`onCompletion` 后单曲循环重新开始时清 `finishWatching`，否则第二轮会一直按"看完"写 0。续播本身复用 `onPrepared` 既有那段（`restoredFromSaveState` → `player_from_last` 开关 → 0），未新增开关。
- **已知取舍**：播放器内手动/自动切 P 从 0 起播（沿用 `progress_history = 0` 的既有行为）；位置在首次 prepare 之前不会落盘（防"刚进页面就退出"把旧进度清掉）。
- **单测**：新增 `LocalVideoProgressTest`（14 例：阈值边界、结尾容差、超时长、时长未知、读写一致性循环）与 `VideoMetaProgressTest`（6 例：老格式 meta 读回 0、位置往返、落盘不冲掉 title/aid/cid/画质列表、看完清零、时长未知可续播、目录不存在不抛）。`org.json` 在单测里是真实现（`app/build.gradle` 有 `testImplementation 'org.json:json'`），所以 `VideoMeta` JSON 与文件读写都能在纯 JVM 里测。
- **验证（本机，已跑）**：`.\gradlew.bat :app:assembleDebug --offline` → `BUILD SUCCESSFUL`（121 tasks，1 分内）；`.\gradlew.bat :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest --offline` → `BUILD SUCCESSFUL`，两 flavor 各 **76 类 / 804 例 / 0 失败 0 错误**（其中新增 `LocalVideoProgressTest` 12 例、`VideoMetaProgressTest` 6 例）。
- **真机待验**：播到 01:30 退出再进应从约 01:30 起播且提示「已从上次的位置播放」（±2s）；关掉「从历史位置播放」则从头且不提示；看到距结尾 5 秒内/播完 → 下次从头；多 P 不串位置（A 看到一半、B 从头）；单文件 / DASH / 听视频 / 虚拟合集 / 多 P 选择页均生效；缓存文件夹改名或移动后仍有效；视频被删/文件损坏/时长变短时静默从头不崩。

### 3.54 播放页禁止系统右滑返回（无条件，26.10.22，待真机）

- **需求**（用户确认口径）：长视频播放页**禁止通过右滑手势退出应用**，且**不设开关**、页内处处生效；用户明确补充「不要屏蔽其他手势（如滑动调节进度和移动画面等）」。范围只到 `PlayerActivity`（投稿 / 番剧 / 直播 / 缓存视频都走它）；短视频页 `ShortVideoPlayerActivity` 的「右滑退出」是产品定义的另一种语义，不动。反馈方式为静默忽略（不弹提示）。
- **根因（与 issue #1 同源）**：手表右滑返回由窗口属性 `android:windowSwipeToDismiss` 实现，**窗口层直接 `finish()`，不走 `onBackPressed`**，应用收不到。改前 `PlayerActivity.onCreate` 是 `val noSwipe = swipeSeekEnabled || SharedPreferencesUtil.getBoolean(SettingsKeys.BACK_DISABLE, false)`，即**两个开关都关着时右滑照常退出**——这正是用户看到的现象。
- **改法（一处）**：`activity/player/PlayerActivity.kt` `onCreate` 删掉那个复合判断，改为无条件 `setTheme(ColorScheme.themeResId(theme, noSwipe = true))`；`swipeSeekEnabled` 的赋值保留（`onBackPressed` 仍用它判断手势型返回键）。不新增设置项、不改基类、不动 `res/`。
- **为何不碰其他手势**：`*.NoSwipe.AppCompat` 相对主主题**只多一条 `windowSwipeToDismiss=false`**（`ColorScheme.kt:526-557` 的 7 套映射 + `res/values/themes.xml`），配色 / 圆角 / 字体全同；它只改窗口属性，**不参与触摸分发**，滑动调进度、双击、长按倍速、捏合缩放与拖动画面仍由本类自己的手势逻辑处理。
- **行为边界**：左滑 / 右滑调进度（`PLAYER_SWIPE_SEEK`）开启时 `onBackPressed` 里 `if (swipeSeekEnabled) return` 的既有兜底**保留未改**（issue #1 对「把右滑模拟成返回键」的手表）；未开启时返回键照常退出播放页。退出路径仍是顶栏返回按钮 + 返回键。
- **手表端复核（26.10.22 二次审查；用户提示「这个主要是针对手表端的右滑返回设计的」）**：
  - **覆盖的正是手表那条链路**：关闭的就是手表系统「窗口层 swipe-to-dismiss」(`android:windowSwipeToDismiss`)，即 issue #1 实测机型的机制（窗口层直接 `finish()`、应用收不到）。这同时兑现了 `docs/archive/superpowers/specs/2026-10-20-player-touch-lock.md` §4.4 留的预案——「若右滑拦截有副作用，退路是播放页无条件 `noSwipe = true`」。
  - **映射无遗漏**：`ColorScheme.noSwipeThemeResId`（`ui/appearance/ColorScheme.kt:550-558`）对 6 套具名主题 + 默认主题都有 `*.NoSwipe.AppCompat`，没有回落到主主题的分支；`res/values/themes.xml` 里每套 `NoSwipe` 相对主主题只多 `windowSwipeToDismiss=false`，配色 / 圆角 / 字体一致。
  - **无子窗口干扰**：`PlayerActivity` 全文件没有 `Dialog` / `PopupWindow` / `WindowManager.addView`（弹幕、控制栏、互动选项都是 Activity 内的普通 View），不存在「某个弹窗窗口自己还开着右滑」的情况；视频画面是 `layout_video.addView(surfaceView/textureView)`，不是独立 Window。
  - **手机端无副作用**：该窗口属性只有手表系统消费，手机上换 `NoSwipe` 主题的效果与主主题完全相同。
  - **已知不覆盖的两类（用户 26.10.22 拍板：保持现状）**：① 用「模拟按下返回键」实现右滑的手表（应用自己的设置文案点名米兔等，见 `docs/copy-inventory/04-settings.md` 的「禁用返回键」说明）——到应用侧就是一个 `KEYCODE_BACK`，与物理返回键不可区分，窗口属性对它无效，播放页在此类手表上右滑仍会退出（要拦只能连返回键一起拦，违背「只禁右滑手势」的口径）；② 手机 / 平板的系统返回手势不读该窗口属性，仍可退出播放页（要拦需 API 29+ 的 `setSystemGestureExclusionRects`，仓库未用、无真机可验）。
  - **锁定态反馈变化（轻微）**：触摸锁定期间右滑不再触发「开锁图标浮现」——事件在窗口层就被丢掉了，到不了 `finish()` / `onBackPressed`。锁定的语义不变（仍出不去），只是少了一次视觉反馈。
  - **真机判断方法**：若用户那台表实测「右滑仍退出」，就说明它属于上面第 ① 类（模拟返回键），此时开「左右滑动控制进度」或「禁用返回键」都能拦住，但这属于既有开关，不是本功能的缺陷。
- **验证（本机，26.10.22 已跑）**：`.\gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest --offline` → `BUILD SUCCESSFUL in 56s`（两 flavor 都出 Debug 包，128 tasks）；单测按 `app/build/test-results/testStandardDebugUnitTest/*.xml` 实测 **76 类 / 804 例 / 0 失败 0 错误 0 跳过**，与改动前基线一致——本次只换窗口主题属性，没有可 JVM 单测的纯逻辑，故未新增用例（`noSwipe` 是 `ColorScheme.themeResId` 的既有参数，已被既有主题映射覆盖）。
- **真机待验**：① 手表上「左右滑动控制进度」与「禁用返回键」都关着时，播放页右滑不再退出；② 开启「左右滑动控制进度」后右滑仍是调进度、不退出；③ 双击 / 长按倍速 / 捏合缩放 / 拖动画面全部照常；④ 顶栏返回按钮与返回键仍能退出；⑤ 主题配色与改动前一致；⑥ 短视频页右滑退出行为不变（本次未改，回归确认）。

### 3.55 自动跳过片头片尾「乱跳」：三处判定互相污染（26.10.22，待真机）

- **现象**（用户报告）：「有乱跳的问题 / 问题好像是判断循环没有及时退出导致的」。实际表现是开播后进度自己往前跳、跳过的位置不对、偶尔跳完又弹回原处。
- **澄清**：`maybeAutoSkipOpEd()` 里**没有任何循环**，不存在「判断循环没及时退出」。真正的驱动是既有 250ms 主线程定时器（`progressChange` → `maybeAutoSkipOpEd`），它每拍都会重新判定一次，所以任何「已被处理」的标记只要丢了，就会在下一拍立刻重跳。
- **根因三条（互相独立，都会造成乱跳）**：
  1. **跳转入口分不清调用者**：`seekToVideo()` 是拖进度条、按键、滑动、点看点、自动跳过**共用的唯一入口**，原先它无条件调 `onUserSeekTo(position)` 把落点所在片段记为已处理。自动跳过自己跳到区间右端点本来在区间外，但只要与用户操作撞在同一帧/相邻帧，就会被当成「用户想看这段」而把真正该跳的片段吃掉；反过来用户拖动途中的中间帧也可能误标片段。
  2. **重复拉取清空已处理集合**：`loadViewPoints()` 在一次播放里会被调用多次（启动、投屏恢复等），原先**无条件** `skipHandled.clear()`。只要某次响应在用户已越过片头之后才到达，就把「片头已处理」抹掉 → 下一拍定时器重新命中 → 再跳一次，**每多拉一次就多跳一次**。
  3. **异步 seek 回弹**：`seekToVideo()` 先写 `video_now` 再调 `ijkPlayer.seekTo()`，而 IJK 的 seek 是异步的——落地前 `progressChange` 会把 `video_now` 拉回**旧位置**，旧位置若仍在片段内就触发下一轮判定，表现为「跳完又跳回去」。原先这条不变式只靠 `skipHandled` 一条防线，而它恰好会被第 2 条清掉。
- **改法（不改 `ViewPointSkip` 的纯判定，只修接线）**：
  - 新增 `player/SkipCancelPolicy.kt`（纯函数，无 Android 依赖，可 JVM 单测）两个判据：`cancelKeyFor(userInitiated, segmentKey, handled)` 与 `segmentsChanged(old, new)`。
  - `seekToPosition` / `seekToVideo` 新增 `userInitiated: Boolean = true`，由调用点**如实声明**；`maybeAutoSkipOpEd` 传 `false`，其余用户手势/按键/看点列表/撤回按钮都走默认 `true`。`onUserSeekTo` 改走 `SkipCancelPolicy.cancelKeyFor`：非用户发起一律不标记。
  - `loadViewPoints()` 改为只在 `SkipCancelPolicy.segmentsChanged(...)` 为真（片段表真的变了＝换集/换 P）时才清 `skipHandled`；同一批片段（迟到的重复响应、投屏恢复重拉）保留已处理状态。
  - 新增 `SKIP_QUIET_AFTER_SEEK_MS = 600L` 与 `skipQuietUntilMs`：自动跳过跳完起一段静默期（`markSeekTarget` 经 `pendingSeekIsAutoSkip` 触发），盖住「新位置已写、IJK seek 未落地」的窗口。**只由自动跳过起**——用户主动拖动的落点已被正式记为已处理，不需要时间兜底，也不该因为拖一下就压掉 600ms 的自动跳过。
- **为什么不给自动跳过用更短的静默期**：定时器周期本身就是 250ms，静默期短于它拦不住回弹；而片头后紧跟片尾时，任何长于两段间隔的静默期都会把片尾吃掉。600ms（约 2 拍）是「拦得住一次 seek 往返」与「不越过相邻片段」之间的取值。
- **单测**：新增 `SkipCancelPolicyTest`（11 例：用户主动跳进片段算反悔 / 自动跳过自己不算 / 落点不在片段内不标记 / 已处理不重复标记 / 首次加载算变化 / 重复拉到相同片段不算变化 / 内容变了算变化 / 数量相同区间不同算变化 / 换集算变化 / 两批都空不算变化 / 有内容变空算变化）。
- **验证（本机，26.10.22 已跑）**：`.\gradlew.bat :app:assembleDebug --offline --no-configuration-cache` → `BUILD SUCCESSFUL`（两 flavor 都出包）；`testStandardDebugUnitTest` 与 `testCoexistDebugUnitTest` → 各 **817 例 / 0 失败 0 错误**（改动前基线 806 例，新增 11 例）。改动只涉及播放器接线与纯判定，无新增用户可见文案。
- **真机待验**：① 有片头片尾的视频开播后只跳一次，不再出现「十几秒后又跳」；② 番剧切集/切 P 后新一集仍能正常跳（不应因保留 handled 而漏跳）；③ 用户主动拖进片头后不被弹走；④ 连续两段（片头紧邻片尾）时片尾仍跳得到；⑤ 投屏/后台恢复后不重复跳。

### 3.56 公告单源：上游公告改由服务端逐条审核透传 + 正文页标注来源（26.10.23，生产已部署 / 待真机）

- **需求**（用户）：「从应用端移除上游路的公告」，把上游公告接口接到自建服务器，**由维护者亲自逐条选择是否透传**；透传的公告**只在正文页顶部标注一行来源**（列表卡片不加徽标）；透传公告**参与启动差量弹窗**。来源文案定为「此公告来自原版哔哩终端」。
- **客户端改法（两路合并整体拆除，单源化）**：
  - `api/AppInfoApi.java`：删掉 `checkAnnouncement()` / `getAnnouncementList()` 两个方法、`check()` 里的调用、`Announcement` import；`check()` 不再发公告请求，只留更新包清理 / debug 警告（原先那条 `catch (IOException)` 随之失去可抛出点而删除，`uploadStack` 的 catch 仍需要 import）。
  - `api/TerminalApi.java`：删 `mergeAnnouncements` / `loadMergedAnnouncements`，`parseAnnouncements` 增加 `source`（`optString("source","local")`）；`checkAnnouncement()` 改为单一源差量、`MsgUtil.showText(title, content, isRelayed())`；反馈的 `feedbackHeaders` 顺带修掉一处行合并。
  - `model/Announcement.java` 加 `source` 与 `isRelayed()`；`MsgUtil.showText(title,text,relayed)` 新重载传 `relay_source` extra；`activity/ShowTextActivity.kt` 据 extra 显隐顶部来源行（`res/layout/activity_simple_text.xml` 新 `TextView relaySource`）；`adapter/AnnouncementAdapter.kt` 点击时透传 `isRelayed()`。
  - `activity/settings/AnnouncementsActivity.kt`：只调 `getOwnAnnouncementList()`，空态/失败态走 `showEmptyView` + `MsgUtil.err`，删掉「部分公告源连不上」提示（`strings.xml` 该串换成 `text_announcement_relay_source`）。
  - `SettingsKeys.TERMINAL_ANNOUNCEMENT_LAST` 注释更新：本地公告与透传公告**共用这一个游标**。
- **服务端（`server/`，26.10.23 由 teammate 实现，`SCHEMA_VERSION=4`）**：`announcements` 加 `source`/`origin_id`/`relay_state` + 部分唯一索引、新增 `meta` 表存 `relay_last_pull`；上游拉取用标准库 `urllib.request`（`UPSTREAM_ANN_URL` 可被环境变量覆盖、超时 10s、`run_in_threadpool`）；管理接口 `GET /admin/api/announcement/relay`、`POST …/relay/refresh`（失败回 HTTP 200 + `{code:1,msg}`）、`POST …/relay/decision`（approve / reject，reject 可撤销已透传项，refresh 不复活 rejected）；`_VISIBLE_SQL` 只放 `relay_state='approved'`；控制台「公告」页签新增「上游透传（原版哔哩终端）」区块（待审 / 已透传 / 已拒绝 + 手动拉取 + `last_pull` 超 300s 自动补拉一次）；`selftest.py` 新增 §22（8 小节）。
- **契约**：客户端 `get_list` 每行多 `"source": "local"|"relay"`；id 仍是本地自增 + `1_000_000_000`（两类公告共用 id 空间，relay 另存 `origin_id` 去重）；`local` 行行为与排序/差量语义**完全不变**；pending / rejected 对客户端与 `from` 差量都不可见。
- **部署（HK 生产，26.10.23 21:02 完成）**：生产 `/opt/rebiliterminal-api/` 的三件套原为 **26.10.17 基线**（比本目录工作副本新：含 `/admin?token=` 303 → HttpOnly 会话 Cookie、控制台 UI 打磨、selftest §5 旧口令用例），直接用工作副本覆盖会回退这些改动，因此把 relay 改动**合并到生产基线**（产物 diff：`rebiliterminal_api.py` +336/−9、`console.html` +129/−4、`selftest.py` +250/−0），本机用合并产物跑 `selftest.py` 187 项全通过后才上传。
  - 备份：`./backup.sh` → `backups/data-20261023.db`（7 张表）；旧文件另存 `.bak-20261023-210201-beforerelay`。
  - 上传后 `systemctl restart rebiliterminal-api` → active，`/terminal/health` `code=0`；`PRAGMA user_version` 3→4，`announcements` 已加 `source/origin_id/relay_state` 三列、`meta` 表已建。
  - 上线冒烟：公网 `get_list` 正常（local 行带 `"source":"local"`）；未登录 `GET /admin/api/announcement/relay` 与 `POST …/relay/decision` 均 **403**；`https://console.zsapp.asia/` 200 且含 relay 区块（`CONSOLE_VERSION = "v4 2026-10-23"`）；带 token 真拉上游 → `added=3 total=3`，relay 列表 `pending=3 approved=0 rejected=0`；`from=1000000000` 差量返回 4 行且 `relay=0`（待审不泄漏）；本地公告管理接口 41 行且仅 local。
  - 部署后本目录 `server/` 三件套已同步为合并产物（106452 / 83457 / 45849 bytes）。
- **控制台 v5 改版（26.10.23 21:23，已部署）**：
  - 「上游透传」从公告页搬成**独立页签**：`data-tab="relay"`、待审角标 `c-relay`、hash 记 `#relay`、键盘 `1`-`5` 切页（footer 同步改 1-5）；页签打开时 `last_pull` 超 1 小时只是**兜底补拉一次**（初始化时不在 relay 页才预取，避免重复请求）。
  - 「公告」页前端过滤 `reply_feedback_id>0` 的行（后端 `GET /admin/api/announcements` 仍返回这些行与 `reply_feedback_id`，脚本调用方不受影响），37 条定向回复不再混在正式公告里。
  - 定向回复的删除入口挪到「反馈」对话里每条开发者气泡旁：新增 `POST /admin/api/feedback/reply/delete` ← `{id}`（内部公告 id；只删 `reply_feedback_id>0` 的行，不存在/非回复 404）；`_db_feedback_thread` 的 dev 消息多带内部 `ann_id` 供回传，客户端忽略该字段。
  - `selftest.py` 增 `§3` 页签断言与 `§16b` 删除定向回复用例；当前 **201 项**全通过（离线少 2 项真上游冒烟）。
  - 顺带修掉旧 `/admin` 兜底看板的 500：`crash_rows` 把第 11 列（`substr(stack,…)`）当 `ctime` 传给 `ts()`，库里有带堆栈的崩溃就 `int()` 失败、整页 500；改为 `ts(r[12])`，并在 §20.1 加回归断言「有崩溃堆栈时旧看板仍 200」。
  - 部署：`./backup.sh` → `backups/data-20261023.db`（8 张表）；旧文件另存 `.bak-20261023-212226-v5`；上传后 restart active。冒烟：`/` 200 且 `CONSOLE_VERSION = "v5 2026-10-23"`、含 relay 页签与公告筛选代码；公网 `console.zsapp.asia` 200；旧 `/admin` 从 500 恢复 **200**；`relay pending=3 approved=0 rejected=0`；本地公告管理接口 41 行（37 条定向回复由前端隐藏）；客户端 `get_list` 仍 4 行、全部 `source=local`；未登录 relay/回复删除接口均 403。
  - 本目录 `server/` 三件套同步为 109064 / 85728 / 48144 bytes（`ebf682f8…` / `4e9247a5…` / `45d7ac04…`）。
- **上游公告自动拉取改为服务端每小时一次（26.10.23 晚，用户要求「间隔改为 1 小时一次」）**：
  - 之前只在控制台打开时按需拉（`last_pull` 超 300s），没人开控制台就不拉。现在 FastAPI `startup`
    起 daemon 线程 `relay-auto-pull`，每 `REBILITERMINAL_RELAY_PULL_INTERVAL` 秒（默认 `3600`，
    `0` 关闭）调一次 `_db_relay_refresh()`；启动时按 `relay_last_pull` 倒推等待，**重启不会额外多拉**；
    从没拉过则 5s 后补；失败只 `logging.warning`（logger `rebiliterminal.relay`）+ 退避至多 10 分钟，
    `last_pull` 不写脏。
  - 等待时间抽成纯函数 `_relay_wait_seconds(last_pull, interval, now)`（下限 5s、上限一个周期），
    `selftest.py` 新增 §22.9 五条断言钉边界；自测文件头设 `REBILITERMINAL_RELAY_PULL_INTERVAL=0`
    关线程，避免 TestClient 进 startup 就联网。控制台 `RELAY_AUTO_PULL_AGE` 300→3600，只作兜底。
  - 本机 E2E（临时脚本，已删）：`interval=3` + 假上游 → 线程随 startup 拉起、约 5s 后写入
    `relay_state='pending'` 行、`meta.relay_last_pull` 落库，`calls=1`。
  - **生产上线（26.10.23 21:38，控制台 v6）**：三件套上传 + `systemctl restart` → active。
    白盒验证线程真的在跑：把 `meta.relay_last_pull` 拨到 3700s 前再重启，**8s 内自动拉取成功**
    （`last_pull` 刷新为当前时刻），且拉取**不改写已有审核结论**（2 条 rejected 仍是 rejected，
    未复活）。客户端 `get_list` 仍 4 行全 `local`（1 条待审 + 2 条已拒绝均未泄漏）；
    `console.zsapp.asia` 登录流 303→200、`CONSOLE_VERSION = "v6 2026-10-23"`、`relay-panel` 在。
    备份 `./backup.sh`（ok 20261023，8 表），旧文件 `.bak-20261023-213703-autopull`。
    部署后生产 / 本机三件套 hash 一致：api `c82fac12…`（111718 bytes）、console `e0ce3d44…`（85884）、
    selftest `789e00fa…`（49752）。
- **验证（本机，26.10.23 已跑）**：
  - `.\gradlew.bat :app:assembleDebug --offline` → `BUILD SUCCESSFUL`（standard + coexist 两个 Debug 包）。
  - `:app:testStandardDebugUnitTest` 与 `:app:testCoexistDebugUnitTest` → 两 flavor 各 **815 例 / 0 失败 0 跳过**（`TerminalApiTest` 现 17 例：删掉 3 例「两路合并」并改为断言 `source`）。  这是上游在本 PR 之外的基线上测的；**合入本 PR 后全量基线为 79 类 / 841 例**（见 §1）。  - `server/`：`python -m py_compile` OK；`python server/selftest.py` → **206 项全通过**（含真上游冒烟；离线少 2 项）；本地 uvicorn 真服务 E2E 验证 approve 后客户端立即可见 `source=relay`、上游不可达时 `code=1`、登录后控制台含 relay 区块；定时拉取线程用临时脚本 E2E 通过（见上）；`console.html` 的 script 块 `node --check` 通过。
  - markdownlint：本次改动的 5 个 md 0 错误。
- **未验证 / 待办**：① 生产上 3 条待审上游公告**未做 approve/reject 实操**（避免真实客户端看到未定内容；按用户要求「亲自选择是否透传」留给维护者在控制台点）；② 控制台页签切换、公告筛选、回复删除按钮**没在真实浏览器点过**（目前只有 curl 冒烟 + TestClient 断言）；③ 客户端未真机验证（列表只剩自建源、透传标注、待审不可见、差量弹窗只弹一次、断网不卡启动）；④ 控制台兜底补拉仍无退避（每小时最多一次，后端定时线程已有退避）。
- **注意**：`server/` 整个目录在 `.gitignore` 里（既有约定，服务端代码不进版本库），所以本次服务端改动只存在于工作区与 HK 部署目录，不随客户端提交。

### 3.57 弹幕时间轴改由播放器位置驱动（彻底去掉周期性纠偏，26.10.22）

- **用户反馈**：「弹幕每隔一会自动纠偏本就是一个不正常的行为，按理说应该全程跟随视频进度；其次在快进时这个纠偏更为严重。」并要求核实「弹幕的滚动速度计算是否有问题，导致弹幕与视频差距越来越大」。
- **结论：确实是速率问题，不是阈值问题。** §3.51 的 `decide()` 只是把「清屏」限了频，没有消除漂移本身——根因在 DFM 内部：`DrawHandler.syncTimer()` 把 `SystemClock.uptimeMillis() - mTimeBase` 当成媒体时间，只有「视频恰好按 1.0x 实时播放」时才成立。
- **实测取证（MuMu Android 15 x86_64，1.0x，临时诊断日志 1 秒一次）**：

  ```text
  视频=3743  弹幕=4249  差=-506      ← 弹幕跑快
  视频=7754  弹幕=8722  差=-968
  视频=11773 弹幕=13217 差=-1444
  视频=15789 弹幕=15787 差=2         ← 触发一次吸附（= 清屏重建）
  ```

  视频每 4 秒只前进 3981ms（≈0.9x 墙钟，解码跟不上），弹幕每 4 秒前进 4478ms（≈1.0x 墙钟）——**速率差约 12.5%，差距线性增长**，所以「每隔十来秒必清一次屏」。倍速下同理：2.0x 时墙钟每 3 秒只该走 6 秒的一半，偏差扩大得更快，与用户「快进时更严重」的观察一致。
- **为什么不能继续用 `seekTo` 修**：`seekTo` → `DrawTask.seek()` → `reset()` 是「清空屏上弹幕 + 重建缓存」（§3.51 已确证），拿它做纠偏等于周期性清屏。要让「全程跟随」成立，只能换时间源。
- **修法（4 层，`DanmakuFlameMaster` 模块可改）**：
  - 新增纯逻辑类 `DanmakuFlameMaster/.../controller/MediaClockSync.java`：`project(采样位置, 采样时刻, 当前时刻, 倍速)` 在两次采样之间外推（否则弹幕每 250ms 才动一次）；`nextTimerValue(当前, 理想)` 每帧**最多挪 40ms**（把采样抖动摊平，不出现画面回退）；`isDiscontinuity(上次, 本次)` 位置差 >1s 判为跳转/换源，直接硬对齐不走蠕动。
  - `DrawHandler` 新增 `interface MediaClock { long positionMs(); boolean isPlaying(); }` 与 `setMediaClock()`；`syncTimer()` 优先走 `syncTimerByMediaClock()`，位置为负数（未就绪/已销毁）时**回退原生墙钟分支**。
  - `IDanmakuView` / `DanmakuView` 暴露 `setMediaClock()`（转发不判 `isPrepared`，因为绑定通常早于 prepare）。
  - `player/DanmakuManager.kt` 新增 `bindMediaClock(position, isPlaying)` 并在 `configureAndPrepare()` 末尾重放一次；`PlayerActivity.bindDanmakuView()` 绑 `video_now`（就是 `progressChange` 每 250ms 采样的那个 `@Volatile` 字段）与 `isPrepared && isPlaying && !destroyed`；`ShortVideoPlayerActivity` 同样绑定（`videoNow` 已补 `@Volatile`）。**直播不绑**（没有有意义的播放位置）。
  - `PlayerActivity.progressChange` 里整块周期性吸附删除，`DanmakuSync.kt` 及其 9 例单测**删除**；新增 `MediaClockSyncTest`（12 例：外推、倍速、空档封顶、不倒推、限幅蠕动、半秒内收敛、不连续判定、阈值边界）。
- **为什么不用改单条弹幕的滚动时长**：`DanmakuFactory` 的 `scrollSpeedFactor` 决定「一条弹幕在屏上停留多久（以媒体时间为单位）」。倍速下时钟推进 2x，同一条弹幕自然也是 2 倍速划过屏幕——这正是期望行为，弹幕出现时机与画面严格对齐，不需要额外放大滚动速度。
- **线程安全边界（改动中最容易踩的坑）**：`MediaClock` 的两个方法跑在 DFM **渲染线程**上，只能读主线程采样好的内存值；**绝不能**在里调 `IjkMediaPlayer.currentPosition`（取 native 锁，与主线程 seek/release 并发有卡死风险，也是 26.09.10 那版「渲染线程读位置导致弹幕全没」的老坑）。
- **模拟器实测（26.10.22，coexist debug 包；日志为当时的体检输出，现已收敛为「仅异常时、≥5 秒一条」）**：
  - **1.0x 稳态 40 秒**：偏差 `9 / 11 / 25 / 26 / 5 / 15 / 18 / 10 / 13 / 16 / 9` ms，**横盘不增长**（改前同样条件每 4 秒涨 490ms）。
  - **2.0x 播放 20 秒**（视频每 3 秒推进 ≈6020ms）：偏差 `42 / 39 / 34 / 40 / -102 / 26` ms，仍不累积；`-102` 是单帧采样抖动，下一帧即回落。
  - **拖进度条**：从 81s 拖到 128s 后首个采样偏差 **8ms**（`isDiscontinuity` 硬对齐生效），截图确认弹幕立即可见、无"闪一下又没了"。
  - **长时间无周期性清屏**：整段日志再无任何「纠偏/清屏」记录；`弹幕缺口` 无输出；错误日志只有 MuMu 自己的旋转提示，应用侧零异常。
- **验证（本机）**：`:app:assembleCoexistDebug` 通过；`:app:testStandardDebugUnitTest` = **76 类 / 809 例 / 0 失败**（806 − 9 + 12）。
- **手表端**：**维护者已人工实测通过**（26.10.23，维护者自述，非本会话 agent 复现）：1.0x / 2x 播放与拖进度条下弹幕全程跟随、不再周期性清屏。仍**没有**设备侧量化数据的是「极弱机（真实手表 SoC）上媒体时钟的收敛速度」与「暂停→恢复的瞬时手感」——要等有人拿 `adb logcat -s 弹幕时钟` 在手表上跑一遍才有日志证据。

### 3.58 短视频页没有阻止自动熄屏（26.10.22）

- **问题**（用户提出「检查短视频部分是否没有做阻止自动熄屏」）：**确实没有**。全仓与亮屏相关的代码只有两处——`res/layout/activity_player.xml` 根布局的 `android:keepScreenOn="true"`（长视频播放页）与 `DownloadListActivity` 的下载期 `FLAG_KEEP_SCREEN_ON`；短视频这套（`activity_short_video_player.xml` + `fragment_short_video_page.xml`）**一处都没有**，看短视频到系统熄屏时间就黑屏。
- **改法**（`activity/video/ShortVideoPlayerActivity.kt` + `ShortVideoFeedFragment.kt`）：
  - `PageHolder.isPlaying` 加自定义 setter，任何一处改播放状态都触发宿主 `syncKeepScreenOn()`——赋值点有 8 处（`play` / `pause` / `resume` / `setActive` / `onPrepared` / 完成回调 / `releasePlayer` / state 收集），逐个补调用必漏。
  - 判据取「当前活跃那一页是否真的在播」（`ShortVideoFeedFragment.isCurrentPlaying()` → `ShortVideoPagerAdapter.isCurrentPlaying()` → `activeHolder.isPlaying()`），**每次整体重算**而不是增量 add/clear：增量写法在 `releasePlayer()` 这类「本来就没在播」的路径上会漏，残留一次就是永久亮屏。
  - `onStop` 兜底清标志、`onStart` 回前台重算，避免后台期间把状态带回来。
- **为什么用窗口标志而不是 View 级 `keepScreenOn`**：先试过在 `PageHolder` 里设 `itemView.keepScreenOn`，模拟器实测**暂停后窗口 `fl` 里仍留着 `KEEP_SCREEN_ON`**（RecyclerView 的 item 挂着，View 级标志的清退不可靠）。换成 `window.addFlags/clearFlags(FLAG_KEEP_SCREEN_ON)` 后一次就对。
- **实测（MuMu Android 15 x86_64，coexist debug，判据是 `dumpsys window windows | grep -c 'fl=KEEP_SCREEN_ON'`）**：播放中 `1` → 暂停后 `0` → 恢复播放 `1` → 上滑切下一条 `1`（新一条自动播）。用户随后在自己环境复验通过。
- **验证（本机）**：`:app:assembleCoexistDebug` 通过；`:app:testStandardDebugUnitTest` = 76 类 / 809 例 / 0 失败（本次只有 UI 状态逻辑，无新增可单测的纯函数）。
- **顺带记一笔**：`mWakefulness` **不能**当判据——MuMu 本身不自动熄屏（桌面对照实验 22 秒后仍 `Awake`），必须看窗口 `fl`。

### 3.59 新增「伪装与安全」：双击顶栏切到假计算器 / 假蓝牙提示（issue #22，26.10.22）

- **需求与验收口径见 `docs/superpowers/specs/2026-10-22-disguise-mode.md`**（Gitee 侧 issue #22，GitHub 镜像上没有该编号；技术坑见 `docs/architecture-map.md` §7.53、`docs/agents/pitfalls.md`「伪装与顶栏手势」）。
- **需求**（issue #22，用户定的三条口径）：① 两种样式都做、可在设置里选；② 计算器的退出方式是「输入暗号再按 `=`」；③ 做成设置开关、**默认关**；触发方式为**双击顶栏**。
- **新增**：
  - `util/DisguisePrefs.kt`：开关 / 样式（`calculator` / `bluetooth`）/ 暗号（4~8 位数字）与纯函数 `normalizeCode`、`matchesSecret`。暗号**只允许数字**，否则计算器上敲不出来 = 用户把自己锁在伪装页里，只能强杀应用。
  - `util/CalculatorEngine.kt`：真能算的四则运算求值（先乘除后加减、小数、除零报错、末尾多余运算符忽略、**绝不抛异常**）。伪装页算错就等于暴露。
  - `activity/disguise/CalculatorDisguiseActivity`：深灰底 + 橙色运算符的手表计算器（**刻意不继承 BaseActivity**——主题、DPI 适配、教程、EventBus 任何一项都会让伪装露馅）。暗号命中 → `finish()`；**返回键只当 `C`**，不退页。
  - `activity/disguise/BluetoothDisguiseActivity`：半透明窗口（`Theme.BiliClient.Disguise.Dialog`）上盖一张仿系统提示卡，底下的页面会透出来再压一层暗色；「重新连接」只换一行字；**长按标题 2 秒**退出，返回键被吃掉。
  - `activity/disguise/DisguiseLauncher`：按设置选样式的唯一入口。
  - `BaseActivity.setupTopbarDoubleTapDisguise`：顶栏手势层，技术结论见 `docs/architecture-map.md` §7.53。
  - 设置：新分组 `GROUP_DISGUISE`（「伪装与安全」）+ `SettingMainActivity` 一级入口 + `SettingsIndex` 搜索条目；`strings.xml` 全部文案；清单两个 Activity 均 `exported="false"` 并各带伪装主题。
- **单测**：`CalculatorEngineTest` 14 例（空表达式、单数字、加减、乘除优先、小数显示、整除不带 `.0`、末尾运算符、连续运算符、除零、非法输入、`*` `/` 等价、负数续算、`5×-2`）、`DisguisePrefsTest` 7 例（合法/裁空格/长度/非数字/匹配/非法暗号恒不匹配/样式合法性），伪装相关共 **21 例**。合并上游后的全量基线是 **79 类 / 841 例 / 0 失败**。
- **模拟器实测（MuMu Android 15 x86_64，coexist debug，用户本人操作）**：双击顶栏 → `WindowManagerShell` 日志确认 `CalculatorDisguiseActivity` 在 display 22 打开；输入暗号按 `=` → `disguise>暗号命中，退出伪装` 并回到原页面；全程 **0 条 `FATAL EXCEPTION` / `AndroidRuntime` 异常**；`BaseActivity>setupTopbarDoubleTapDisguise: 顶栏双击伪装已就绪` 在每个页面正常打印。
- **手表端**：**维护者已人工实测通过**（26.10.23，维护者自述）：双击顶栏可切到伪装页、计算器能算、暗号能退出、蓝牙样式轻点不再误退出。仍缺设备侧验证的是「单击顶栏那个 0.3 秒双击判定窗口在手表上的手感」与「半透明蓝牙提示页在圆屏上的布局」——这两条只能在圆屏真机上肉眼判断。

### 3.60 上游审查意见修复（26.10.23）

上游对 PR 提了 1 个 P0 + 7 条 P1/P2。逐条核对后的处理：

- **P0（真回归，§3.57 引入）短视频首播弹幕不显示**：`ShortVideoPlayerActivity` 在 `loadDanmaku()` **之前**调 `danmakuManager?.resume()`，而 `DanmakuManager.resume()` 有 `isPrepared` 守卫，此刻必然 false → 空操作；全页又没有任何 `start()`。DFM 的 UPDATE 循环**只由 START / SEEK_POS / RESUME 三个分支拉起，`prepare()` 本身不会启动**，旧实现是靠周期性 `seekTo` 的副作用（SEEK_POS 落穿到 RESUME）间接启动的。§3.57 删掉周期性 seekTo 时把这个副作用一起删了。
  - 修法：`DanmakuManager` 新增 `start(positionMs = -1)`，并在 `configureAndPrepare()` 末尾自动调用（起播位置优先取媒体时钟的已知位置，续播/切页别从 0 重走）。等价于长视频页原本在 `onPrepared` 里做的 `mDanmakuView.start()`。
  - 实测（模拟器）：进入短视频页**不做任何操作**，弹幕区出现「弹幕准备完毕」+ 多条滚动弹幕（修复前只有烧进画面的字幕）。
- **P1-1 双击顶栏覆盖面与文案不一致**：`PlayerActivity` 不继承 `BaseActivity`（没有手势）；20 个 `asyncInflate` 页面在 `onStart` 时视图还没进来，`findViewById(R.id.top)` 为 null 直接 return（首次进入无效、退出再进才生效）；短视频的顶栏在分页 item 里也装不上。
  - 修法：手势实现抽成 `util/DisguiseGesture.kt`（`install` / `detach` / `isInstalled`，用 `WeakHashMap` 防重复安装）；`BaseActivity` 拿不到顶栏时 `decorView.post` 重试一次；`PlayerActivity.onStart` 与短视频 `PageHolder` 各接一次线。现在「双击任意页面顶栏」与文案一致。
- **P1-2 3× 长按媒体时钟追不上**：`MAX_STEP_MS = 40` 在 60fps 下只能追 2400ms/s，3× 需要 3000ms/s；而 `DISCONTINUITY_MS = 1000` 又大于 3× 的相邻采样差 750ms，永不触发硬对齐。
  - 修法：新增 `stepFor(speed)`（步进上限随倍速放大）与 `SNAP_ERROR_MS = 500`（误差过半秒直接吸附）。`nextTimerValue` 增加 `speed` 参数，补单测 2 例。
- **P1-3 计算器负数无法续算**：`3-6` 之后表达式里留下 `-3`，再按运算符或 `=` 会因为「首字符是运算符」直接 `ERROR`，伪装页显示「错误」很扎眼。
  - 修法：`CalculatorEngine` 支持表达式开头的一元负号；补负数用例 6 条（`-3+2` / `-3×2` / `0-3-3` / `-3` / `-3÷2`。
- **P1-6 文档一致性**：`architecture-map` 的测试清单（§3/§8 两处 76 类 806 例 → 79 类 841 例，补 `MediaClockSyncTest` / `CalculatorEngineTest` / `DisguisePrefsTest` / `SkipCancelPolicyTest`，删已不存在的 `DanmakuSyncTest`）、弹幕时钟旧口径（把「DanmakuSync + 周期性 seekTo」整段换成 §7.51 的现行做法）、`docs/README.md`「19 章」→「20 章」、`FEATURES.md` 重编号后 6 处旧引用、`readme.md` 的分支制度与已不存在的 `testDebugUnitTest`。
- **P2 顺手修**：短视频 `onError` 未把 `isPlaying` 置 false（画面停住却一直挂着 `KEEP_SCREEN_ON`）；`isPlaying` 补 `@Volatile`（弹幕渲染线程读）；`format` 指定 `Locale.US`（默认 Locale 会把小数点写成逗号，结果再回填就解析不回来）；`DrawHandler` 的 SEEK_POS / RESUME 分支重置媒体时钟采样（避免暂停恢复后计时器前冲再回落）；长视频续播 seek 补 `markSeekTarget`；设置里的暗号不再**逐键**弹 toast（`TextWatcher` 每次击键都会触发）。
- **未做（需单独决策）**：把 PR 拆成三份、`readme.md` 功能表补伪装条目之外的市场化描述、`excludeFromRecents`/图标（需真机确认最近任务卡片表现）、`setScreenOnWhilePlaying(true)` 对 TextureView 无效但仍保留（SurfaceView 路径可能有意义，改动收益不明确）。
- **验证**：`:app:assembleCoexistDebug` 通过；`:app:testStandardDebugUnitTest` **79 类 / 841 例 / 0 失败**；模拟器实测 P0 与双击手势；markdownlint 31 文件 0 issue。
- **第二轮审查的三条必修（26.10.23 晚，已修 + 已实测）**：
  1. **双击伪装会无限自旋**：开关开启且本页没有 `R.id.top` 时，`BaseActivity` 用 `decorView.post` 无限重试——有 **11 个页面布局里本来就没有顶栏**（Download / Catch / Tutorial / TutorialPager / JumpToDialog / InputDialog / CaptchaWebView / QRLoginRegister / ConfirmDialog / Dialog / ListDialog），进这些页面会每帧投递一次、一直耗电到页面结束。改成「`postDelayed` + 封顶 20 次 × 50ms + `isFinishing/isDestroyed` 立刻停」，并在放弃时打一条 `XXX 没有 R.id.top，本页不安装双击伪装`。实测：进 `DownloadActivity` 只出现**一次**该日志。
  2. **蓝牙伪装页轻点标题就自动退出**（伪装的致命伤）：`OnTouchListener` 对 `ACTION_DOWN` 返回 `false` → View 没成为触摸目标 → 系统不再派发 `UP`/`CANCEL` → `removeCallbacks` 永远执行不到 → 轻点一下、2 秒后窗口自己 `finish()`。改成**全部返回 true**（消费事件；标题本就没有点击行为）。实测（真实坐标 `[144,1623][2016,1763]`，2160×3840 屏）：轻点 1 次 + 等 4 秒**仍在伪装页**，连点 5 次 + 等 4 秒**仍在**；长按 2.5 秒 → 退出到推荐页，日志 `disguise>长按标题，退出伪装`。
  3. **文案与覆盖不一致 / 关开关不摘手势**：`desc_setting_disguise_enabled` 与 FEATURES 从「任意页面」改成「**有顶栏的页面**」（并说明对话框/崩溃页/教程页没有该手势）；`PlayerActivity.onStart` 与短视频 `PageHolder.syncDisguiseGesture()`（`onBindViewHolder` 每次重算）都补上「关掉开关就 `detach`」，不再需要重进页面才失效。
- **顺带修掉的真 bug（同批）**：
  - **短视频页从来没给弹幕设过倍速**：全页只有 `playerBridge.setSpeed(...)`，`danmakuManager.setSpeed` 一处都没有 → 0.5~3.0x 下 `DrawHandler.mSpeed` 一直是 1.0，媒体时钟的倍速外推与步进补偿（§7.51）在短视频上完全没生效。新增 `applySpeedToPlayers(speed)` 一并设，三个调用点（`cycleSpeed` / `startSpeedBoost` / `endSpeedBoost`）与 `onPrepared` 全部改走它。
  - **`setSpeed` 早于 DFM prepared 会被丢弃**：`DanmakuView.setSpeed` 有 `handler.isPrepared()` 守卫，而播放器 `onPrepared` 通常早于 DFM 的 `prepared()`（后者异步）→ 默认倍速非 1× 时那次调用等于没设。`DanmakuManager` 新增 `setSpeed()` 记 `pendingSpeed`，在 `prepared()` 回调里补设一次；`PlayerActivity` 的 5 处 `mDanmakuView?.setSpeed(...)` 全改走它。
  - **`DrawHandler` 的 PREPARE ready 回调吞掉起播位置**：`ready()` 是后台解析完才回调的（异步），无条件 `pausedPosition = 0` 会把此前 `start(pos)` 设好的位置清零 → 续播/切页时弹幕时间轴从 0 重走一遍。新增 `mPendingStartPos`，`START` 时记录、`ready()` 时优先取它。
  - P2 清扫：删掉计算器页空的 `onTouchEvent`、`DanmakuView.setMediaClock` 注释改成符合实现（只判 `handler != null`，不判 `isPrepared`）、圆屏 padding 抽成 `applyDisguiseRoundPadding`（两伪装页共用，蓝牙页也补上了）、设置分组文案与搜索索引改走 `strings.xml`（新增 `desc_setting_disguise_index`）。
- **第三轮审查（Standards 轴 + 伊莲）后的补充修复（26.10.23 晚）**：
  - **暂停中加载完弹幕会被 autostart 拉起**：`configureAndPrepare()` 末尾原来无条件 `start()`。改成「`prepared()` 回调里按 `mediaPlaying()` 判断」——不在播就不起渲染，之后由宿主各自的 `resume()` 路径拉起（DFM 的 RESUME 分支在 `mReady` 后会自行启动 UPDATE）。直播未绑媒体时钟（`mediaPlaying == null`）照常启动。
  - **重复 start 会把时间轴锚回 0**：`PlayerActivity.onPrepared` 原来裸调 `mDanmakuView!!.start()`，续播/已经跑了一段时会让弹幕跳回开头闪一下。改成 `danmakuManager?.start(video_now)`。
  - **短视频首播那处 `resume()` 是空操作**（`loadDanmaku()` 之前调用，`isPrepared` 必然 false）：删掉；真正的起播由 `prepared()` 负责。
  - **计算器支持 `5×-2`**：`CalculatorEngine` 重写词法，把「期望数字位置出现的 `+`/`-`」当正负号（`5×-2 = -10`、`5--3 = 8`），并抽 `scanNumber()` 消掉原来两段重复的数字扫描；`CalculatorDisguiseActivity` 的运算符处理允许在 `×`/`÷` 后追加 `-`（否则 UI 上构造不出负数运算）。新增/调整 3 例。
  - **未知按键 tag 不再静默拼进表达式**：只接受单个数字字符。
  - **暗号输入改数字键盘**：`SettingsAdapter` 新增 `input_digits` 类型（`TYPE_CLASS_NUMBER`），设置项从 `input_string` 换过去。
  - **Java Javadoc 的 `[X]` 链接**改成 `{@link ...}`（DFM 三个文件）。
  - `CalculatorEngine.ERROR` 保留中文常量，KDoc 写明「纯函数跑在 JVM 单测里没有 Context，故意不读资源」（AGENTS.md 已有该例外条款）。
- **文档计数修正**：`architecture-map` 的测试清单从「80 个文件」改为「**79 个文件**（78 个 `*Test` + 1 个 `FakeSharedPreferences.kt`）、**79 个测试类**」（`CalculatorEngineTest.kt` 内有 `CalculatorEngineTest` 与 `DisguisePrefsTest` 两个类），用例数统一为 **841**。`strings.xml` / `themes.xml` 末尾多余空行已清。
- **手表端验证口径（26.10.23）**：本 PR 涉及的弹幕时间轴、短视频亮屏、伪装功能**已由维护者人工在手表上实测通过**（维护者自述，非本会话 agent 复现，因此没有设备日志/截图入库）。仍未覆盖的是「极弱机上的媒体时钟收敛」「单击顶栏 0.3 秒延迟的手感」「圆屏蓝牙提示页布局」——这三项只能真机肉眼判断，已分别记在 §3.57 / §3.59 的对应条目里。

### 3.61 收藏夹接口层整体迁移到 `x/v3/fav/*` 新族（26.10.23）

- **缘起**：上游 PiliPlus 的收藏夹接口已全部换成 v3 新族（写操作 `x/v3/fav/resource/batch-deal`、收藏夹列表 `x/v3/fav/folder/created/list`、图文收藏 `x/polymer/web-dynamic/v1/opus/feed/fav`），本机还在用 `getBoxList` / `x/space/fav/arc` / `medialist/gateway/*` 一代。本轮只迁**接口层**，UI 与交互零改动；上游有而本机完全没有的 4 类能力（夹内手动排序、收藏夹本身排序、清理失效收藏、夹详情/封面/公开私密编辑）只登记到 `docs/FEATURES.md` §8.5「待实现」。
- **`api/FavoriteApi.java` 重写**：`getFavoriteFolders` 从「`getBoxList` + `list-all` 两次请求手拼 media_id」改成 `x/v3/fav/folder/created/list`（`ps=100` + `has_more` 分页，一次拿 `id`/`title`/`cover`/`attr`）；删除 `getFolderVideos()`（`x/space/fav/arc`）、`legacyOrder()`、`buildMediaId()`、`resolveMaxCount()`、`LEGACY_CUSTOM_FOLDER_MAX_COUNT`；新增纯函数 `isDefaultFolder(attr)`（`attr & 2 == 0`，bit0 私密 / bit1=1 非默认）与 `maxCountOf(isDefault)`；写操作统一收口到私有 `dealFavorites()`（`POST x/v3/fav/resource/batch-deal`，`resources` + `add_media_ids` + `del_media_ids` + `csrf`），`addFavorite`/`deleteFavorite`/`batchDeleteResources` 改签名收 **media_id**；`deleteFolder` 补 `platform=web`；`getFavouriteOpus` 换 `opus/feed/fav`（`page_size=20`，封面兼容对象/字符串，`code != 0` 回退旧 `favlist`）；`getFavoriteState`/`parseFavoriteState` 增加 `mediaIdList` 出参。
- **调用方**：`adapter/favorite/FolderChooseAdapter.kt` 的 `fidList` 换成 `mediaIdList`（点选写操作只认 media_id）；`activity/video/info/AddFavoriteActivity.kt` 收集并下传 `mediaIdList`；`activity/user/favorite/FavoriteVideoListActivity.kt` 删掉 `fid` extra 与老接口兜底分支（`mediaId <= 0` 直接按加载失败）、删掉「连点两次长按删除」（`confirmLegacyRemove()` 整段移除，长按统一走管理菜单/只读菜单）、两处 `deleteFavorite` 改传 `mediaId`；`adapter/favorite/FavoriteFolderAdapter.kt` 不再传 `fid`。`api/LikeCoinFavApi.favorite()`（手拼 media_id 的死路径）一并删除。
- **单测**：`FavoriteApiTest.kt` 删 `resolveMaxCount`/`buildMediaId`/`legacyOrder` 三组用例，改成 `isDefaultFolder`/`maxCountOf` + `parseFavoriteState` 的 `mediaIdList`/默认夹按 attr 判定回归用例（测试数据用线上真实值：默认夹 `attr=0`、自建夹 `attr=22`）。
- **验证**：`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest` 全绿；未上手表实测（接口层改动，UI 未动，按 `docs/agents/workflow.md` 的判据走构建 + 单测）。
- **注意**：接口域名/路径变了但都还在 `bilibili.com` 白名单内，不需要动 `<domain-config>`；`getBoxList` 用的 `space.bilibili.com` 从此不再被收藏链路请求。

### 3.62 搜索结果加排序：单按钮 + 单选弹窗（综合 / 播放量 / 最新 / 收藏，26.10.24，用户反馈）

- **需求来源**：用户反馈原文「希望可以在搜索里加一个最多播放 最多点赞 最新播放之类的分类」。两轮口径：① 先做成**视频 tab 顶部一条常驻排序行**，随后按用户要求改成**单按钮 + 点开弹窗选择**（弹窗用番剧选集同款 `TerminalDialog.singleChoice`，按钮走主题里的经典终端按钮样式）；② 四档 **综合 / 播放量 / 最新 / 收藏**；③ 覆盖**视频 + 专栏**两个 tab。
- **接口事实**（已并入 `docs/bilibili-api-notes.md` §3.13）：`all/v2` 只吃 `keyword`，`order` 是 `search/type` 的参数；视频搜索**没有「最多点赞」**（只有 `click`/`pubdate`/`dm`/`stow`/`scores`）。所以**视频分类选「综合」时仍走 `all/v2`**（保留它把番剧混进第一页的既有行为），选其余档位改走 `search/type?search_type=video&order=…`（不混番剧）。
- **控件为什么在本页而不是 fragment**：`activity_search.xml` 里 `ViewPager` 只对齐到 `@id/top` 下方，`searchbar` 与它同起点、后声明（画在上层）。第一版把控件加进 `fragment_simple_refresh` 顶部，模拟器实测**四个档位正好压在搜索框里**。现在控件是 `activity_search.xml` 的 `searchSortButton`（锚 `searchbar` 下方，`ViewPager` 锚它），`SearchFragment` 只暴露 `sortOptions()` / `currentSortOrder()` / `selectSort()`，`SearchActivity` 负责文案、弹窗与显隐。
- **按钮为什么是裸 `<Button>`**：MaterialComponents 主题会把 `<Button>` inflate 成 MaterialButton 并套用主题里声明的 `materialButtonStyle` —— 默认主题（经典终端）对应 `ButtonStyleTerminal`（`#cc262626` 半透明卡底 + 暖白字 + 圆角），换主题时自动跟随各自的按钮样式。**别写死 `@color/terminal_*`**，浅色主题下会变成暗块。
- **弹窗**：`TerminalDialog.singleChoice(this, title, labels, checked)`，与 `BangumiInfoFragment.getEposideChooseDialog()` 同一款（`›` 引导符 + 选中项 `▸`/`✓` + 底部「点空白处取消」）。**TerminalDialog 只负责拼装、不会自己 show**，调用方必须 `.show()`——第一次就是漏了这个：点按钮只有水波纹，弹窗不出现（模拟器实测抓到）。
- **几个刻意的取舍**：
  - **没搜过不显示**（`hasSearched`）——那时页面上是搜索历史，摆一个「排序」没有意义；搜索框被滚走时按钮**一起收**（两层 chrome 只收一半会在列表顶上留个洞）。
  - **排序不持久化**：每次进搜索页都是「综合」。少一个存储键，也少一类「我什么时候设过排序」的困惑。
  - **`sortOrder` 是 `@Volatile`**：点击在主线程写、`continueLoading` 在 `CenterThreadPool` 线程读（与 `bottom` 同一类可见性问题）。
  - **切换排序时在途结果作废**：请求发出前抓一份 `orderAtRequest`，回包后发现排序已变就整批丢弃，否则会把旧排序的第 2 页追加到新列表里。
- **风控降级不再表现为空列表 + 自动回退旧接口**（26.10.24 用户报「有时候拼接的链接会没有任何搜索结果」，实测抓到原始响应）：`search/type` 在风控降级时会回 `{"code":0,"message":"OK","data":{"v_voucher":"voucher_…"}}` —— `code` 是 0，但 `data` 里**既没有 `result` 也没有 `seid`**，只有 `v_voucher`。以前 `SearchApi` 对它返回 null，页面就只剩空列表 +「啥都木有~」，看上去像排序参数拼错了。
  - **回退链路**（按用户要求）：wbi 那条 → 若拿到 `v_voucher` 空壳，改打**旧接口** `x/web-interface/search/type`（不带 `w_rid`/`wts`/`seid`）→ 旧接口也是空壳才抛 `SearchApi.RiskControlException`。旧接口实测同样吃 `order`（`order=click` 下 `play` 严格降序 7972105 → 5017479 → 4438614 → …），中文长关键词也一样能出。
  - **按钮不可用态**：抛到页面后 `SearchFragment.markSortBlocked(true)`，排序按钮改成「因B站风控不可用」、点它只提示「请稍候再试」；下一次加载成功会自动解除。状态变化经 `onSortStateChanged` 回主线程重画按钮。
  - **判据只认 `v_voucher`**：真没结果的搜索仍会返回带 `result` 的响应，不能因为 `result` 为空就报风控。同理**不要急着怀疑参数**——同一条 URL 形状在冷却几十秒后立刻恢复 200 / `numResults:1000`。接口结论已并进 `docs/bilibili-api-notes.md` §3.14。

- **顺带修的**：`SearchApi` 里三处封面/头像用 `"http:" +` 拼协议（违反「只走 https」），统一改成 `httpsUrl()`（`//` 开头才补 `https:`，已是绝对地址的原样保留）；视频条目解析抽成 `parseVideoEntry`，聚合搜索与分类搜索共用一份。
- **单测**：`SearchApiTest` 从 6 例补到 **12 例**（分类搜索视频解析、非 video 条目跳过、null 输入、聚合分支与分类分支共用同一解析与封面规则，以及两条风控判据）。
- **模拟器实测（MuMu Android 15 x86_64，coexist debug）**：MuMu 会把窗口在 display 2~7 之间搬来搬去，每次搬运都重建 Activity，所以每一步都要现查 `dumpsys display` 拿 physicalId（截图）与逻辑 displayId（`input -d`）。
  - 搜 `linux` → 搜索框下方出现「排序：综合」按钮；点开是单选弹窗（标题「搜索结果排序」，四项带 `›`，当前项 `▸`+`✓`，底部「点空白处取消」）。
  - 选「播放量」→ `order=click&page=1&search_type=video`，结果按播放量降序（797.2万 → 501.7万 → 443.9万 → 412.9万），按钮变成「排序：播放量」。
  - 选「最新」→ `order=pubdate`，首屏全是个位数播放量的新视频（0/11/56 观看），按钮变成「排序：最新」。
  - 选「收藏」→ `order=stow`，按钮变成「排序：收藏」，列表正常返回。
  - 切到**专栏**：按钮文案里的档位变成「阅读量」；切到**用户**、**番剧**：按钮消失。
  - 列表下滑 → 搜索框与排序按钮一起收起、列表顶到顶栏下；回滑恢复。
  - 全程 `logcat` 里 `FATAL EXCEPTION` / `AndroidRuntime` **0 条**。
- **子代理审查（26.10.24）修掉的 4 条**：
  - **过期请求的失败会把按钮锁死**：成功分支有 `orderAtRequest != sortOrder` 就丢弃，`catch` 里却没有同样的判断——风控链路比正常链路慢，最坏情况是新排序已经刷出来了、旧请求才抛 `RiskControlException`，于是按钮被冤枉成「不可用」。两处 `catch` 开头补同一个判据。
  - **`-412` / `-352` 形态漏了**：以前只在 `data` 有 `v_voucher` 时才回退，而 `code=-412` 的响应往往连 `data` 都没有，会走 `if (all.isNull("data")) return null;` 静默返回空列表。新增 `shouldFallback(all)` 同时认两种形态（含 `NetWorkUtil` 重试 5 次后原样返回的 `-412`），旧接口也一样认。
  - **风控文案被吞成「网络错误」**：`MsgUtil.err` 对任何 `IOException` 一律换成 `err_network`，而 `RiskControlException` 正是它的子类 → 「搜索被B站风控了…」只会进 logcat。改为在 `SearchFragment.report()` 里先拦一道（5 个搜索分类共用）。
  - **风控态没有重试入口**：按钮只弹提示、下拉刷新关着、同关键词再点搜索会短路，锁定后再也解不开。现在点按钮会真的重拉第一页（`SearchFragment.retry()`），且 `update(keyword)` 换了关键词时会复位。
- **第二轮审查（26.10.24，外部评审）修掉的 3 条 + 补的测试**：
  - **在途的旧请求仍可能污染新结果**：只在后台线程比了 `sortOrder`，从"检查通过"到 `runOnUiThread` 真正执行之间状态变了仍会把旧结果追加进新列表；换关键词更是完全没校验。改成发请求时抓 `loadGeneration` + `keyword` + `sortOrder` 三样，**后台与改列表前各验一次**（`SearchFragment.invalidateInFlightRequests()` 在 `update` / `selectSort` / `retry` 里自增）。
  - **一次搜索最多 10 个请求**：通用重试对 `-352/-412` 重试 5 次，回退旧接口再走一遍。新增 `NetWorkUtil.getJsonOnce()`，回退只发一次；主链路重试行为不变。
  - **旧接口的其它错误码被伪装成空结果**：`resultOrThrow` 以前对所有非风控情况返回 null，`-400` 之类会显示成「啥都木有」。现在非零 `code` 抛 `SearchApi.SearchException`（`ApiErrorText.opFailed`），页面把错误码弹出来；`SearchFragment.report()` 改成认 `SearchException` 基类，风控与其它错误码都按自己的文案显示。
  - **测试补上判据**：`SearchApi.shouldFallback` / `isRiskControl` 提成 public static 供单测，新增 2 例钉住两种风控形态与「真没结果不算风控」。合并上游前的基线 **79 类 / 847 例 / 0 失败**；合并上游（v3 收藏夹等 15 个提交）后 **87 类 / 941 例 / 0 失败**。
- **合并上游后的回归（26.10.24，本机 MuMu Android 15 x86_64 / coexist debug）**：合入 upstream 15 个提交（收藏夹 v3 迁移、无极调音、安全加固等）后重装包，搜索页排序链路仍正常——「排序：综合」→ 弹窗选「播放量」→ 797.2万 → 501.8万 → 443.9万 → 412.9万 严格降序；`logcat` 无 `FATAL EXCEPTION`（崩溃缓冲里那条是 `uiautomator` 自己的，与本应用无关）。构建与单测见上。
- **用户反馈修复（26.10.24 当天，两处）**：① 「排序按钮消失和出现的时机、动画没跟随搜索框」——旧实现是搜索框 `translationY` 滑 300ms、按钮自己瞬间 GONE，且 `ViewPager` 锚在按钮下面（按钮 GONE 那一下列表整块往下跳）。现在两者由**同一条 `ValueAnimator` 按同一进度收放真实高度**（`SearchActivity.applyTopChrome`，`CHROME_ANIM_MS = 200`），列表跟着平滑补位；自然高度只在 `wrap_content` 时采样并缓存，避免把动画中间值记成自然高度。② 「第二次点搜索框时按钮没消失，挡住候选词」——历史 / 建议浮层与按钮占的是同一条（都锚在 `searchbar` 下方），按钮是 MaterialButton 带海拔，画在浮层上面。现在浮层一律走 `showHistoryDropdown()` / `showSuggestionsDropdown()` / `hideDropdowns()` 三个入口，浮层开着时按钮置 `INVISIBLE`（**让画面不让位置**，列表不整体上移，浮层正好盖住它）。
  - **模拟器实测**：点搜索框 → 候选词第一行完整可见、按钮不再压在上面；滚动 → 搜索框与排序条同时收缩（把时长临时调到 1500ms 抓过中间帧，两者同比例变形）、结束时列表紧贴顶栏；回滚 → 一起展开；切到「番剧」分类按钮消失、切回「视频」恢复；`logcat` 0 条 `FATAL EXCEPTION`。
  - **验证**：`:app:assembleDebug`（coexist）通过；两 flavor 单测各 **87 套 / 941 例 / 0 失败**（纯 UI 改动，未新增用例）；markdownlint 40 文件 0 issue。
- **第二轮审查（26.10.25，外部评审）修掉的 P1 + 文档失实**：
  - **风控直接回 HTML 拦截页时回退链根本没启动**：`SearchApi` 只在拿到**已解析 JSON** 后才判 `shouldFallback`，而风控被拦时 B 站还可能直接回一张 `<!DOCTYPE html>` 的「出错啦」页——那会让 `NetWorkUtil.getJson` 的 doctype 重试耗尽后抛 `IOException`（或非 JSON 抛 `JSONException`），请求层就失败了，页面只显示「网络错误」，排序按钮也进不了风控态。实测形态见 `docs/review/agent-test-guide.md`（`412` + `<!DOCTYPE html>`）。新增 `SearchApi.fallbackToLegacy(keyword, legacyUrl, primary)`：请求层失败时同样兜一次旧接口；旧接口给出明确结论（风控 / 服务端错误码）就用它，两边都失败时**主链路是网络层失败就如实上抛**（真断网别诬陷成风控）、主链路是合法 JSON 的风控降级则抛 `RiskControlException`。
  - **`getJsonOnce` 名不副实**：它原本仍走 `get()` → `executeWithDoctypeRetry`，而风控页正是 HTML，等于又重试 5 次——注释里「只发一次」是错的。现在改成**直连一次**（自己建 `Request`，不经过 doctype 重试），调用点只有 `SearchApi` 的回退链路。
  - 清理审查指出的失实/死代码：`SearchSortOptions.SLOT_COUNT` 零引用且注释指向已废弃的 4 格排序条（删）；`SearchApi.resultOrThrow` 上叠加的两段 javadoc（合并）；`refreshSortBar` 的 KDoc 说「点它只提示」但实现会真的 `retry()`（改注释）；`SearchFragment` 未使用的 `ColorScheme` import（删）。
  - 文档对齐：`FEATURES.md` 的排序按钮说明改成「点它会真的重试一次」；`bilibili-api-notes.md` §3.14 补「三种风控形态都会回退」与「`getJsonOnce` 连 doctype 重试也跳过」；`architecture-map.md` §6 的 http 明文清单移除 `SearchApi`（已改 `httpsUrl()`）。
  - **切到首次访问的分类时排序按钮可能一直不出现**（审查「疑点 A」的加固）：`FragmentStateAdapter` 是懒创建的，`onPageSelected` 里 `findFragmentByTag("f$position")` 可能还是 null，那一刻 `refreshSortBar` 只能判成「本分类不支持排序」把按钮收掉，而 `sortBarEligible` 是缓存值、之后没人重算。现在 `onPageSelected` 之后 `viewPager.post { refreshSortBar() }` 补算一次（幂等，按当时的 currentItem 算，不会自旋）。审查侧的观察是「切 tab 时整体收起、上滑一次即恢复」，判断为 main 既有逻辑而非本功能引入；这条补算属于顺手加固，不是回归修复。
  - **验证**：`:app:assembleDebug` 通过；两 flavor 单测各 **87 套 / 941 例 / 0 失败**；markdownlint 40 文件 0 issue。**HTML 风控回退这条新分支没法主动触发**（要 B 站真回拦截页），靠既有纯函数判据 + 构建与单测兜底，如实记在这里；本轮也**未上设备实测**（模拟器已关闭，且这条路径模拟器同样复现不了）。
- **未做**：用户 / 直播的排序（它们的 `order` 是另一套键：`fans`/`level`、`online`/`live_time`），以及「最多弹幕 / 最多评论」两档（手表屏上弹窗列表已经够长）。

### 3.63 下载卡在 20%：分片下载加看门狗 + Range 校验 + 强制 HTTP/1.1（26.10.25）

- **缘起**：用户报「下载进度卡在 20% 不动」。20% 是 `DownloadService.processDownloadSection` 给 MP4 / 纯音频主文件写死的**起始基准**（`setDownloadProgress(id, 0.2f, ...)` 后 `downFile(..., 0.2f)`），所以卡在 20.0% 基本等于主文件的 `downFileSpeedSeg` 阶段一个字节都没再往前推（失败分支会清掉进度映射，因此不是"映射残留"）。
- **实测（真实 B 站直链，51,973,319 字节的 720P MP4）**：单连接 h1.1 21.28s ≈ 2.44MB/s；5 路并发 Range（h1.1）聚合 ≈ 10.4MB/s（约 4.2 倍）；5 路 h2 流复用一条连接只有 ≈ 4.9MB/s 且**没有传输层停顿**（`maxGap` ≤ 173ms）——所以「多线程本身」没问题，问题在分片实现的容错。
- **定位到的 4 个缺陷**：①轮询循环没有停滞看门狗，唯一的时间约束是 30s 总 join 预算，线程卡在 DNS／h2 流排队／读超时时进度一直停；②join 超时后不检查线程是否还活着就起单连接重下，两拨线程写同一个文件；③`downloadSegment` 不校验 `206`／`Content-Range`，若 CDN 忽略 Range 回 200，每片都把整个文件读一遍（5 片 = 5 倍流量，期间进度不动），最坏 3 次重试 × 5 片；④HEAD 没有 `callTimeout`，"连上但一直不给响应"可以无限期等下去。
- **改法**：
  - 新增 `service/download/SegmentDownloadPolicy.kt`（`internal object`，纯逻辑无 Android 依赖）：`STALL_TIMEOUT_MS = 12s` / `ABORT_JOIN_TIMEOUT_MS = 3s` / `FINISH_JOIN_TIMEOUT_MS = 30s`、`hasStalled` / `isPartialContent` / `isRangeIgnored` / `parseContentRangeStart` / `isContentRangeMismatch` / `shouldDisableSegmentMode`。
  - `DownloadService.downFileSpeedSeg`：轮询里先判停滞（12s 没有新字节 → 记日志、取消在飞 Call、不再干等）→ 中止后只给 3s join 预算，线程没退出就报错（**绝不并发重下**）→ 停滞 / Range 不可用时回滚已计字节、把进度退回基准，再回退整文件单连接；命中后置 companion 的 `@Volatile segmentModeDisabled`，本次进程后续任务直接单连接（不复位）。
  - `downloadSegment`：新增 `SegmentSharedState`（总字节 `AtomicLong`、失败 / Range 不可用 / 已中止三个 `AtomicBoolean`、在飞 `Call` 的 `CopyOnWriteArrayList`、每片最后收字节时刻 `AtomicLongArray`）替代原来的两个裸原子量；每片响应校验「200 = 忽略 Range」「非 206 失败」「Content-Range 起点不符」；读循环加 `written < expectLen` 上界且只写本片范围；每写一块就 `markByte(idx)` 喂看门狗。
  - `downFileSpeed`：HEAD 单独用 15s `callTimeout`；下载专用 OkHttpClient 强制 `HTTP_1_1`（实测 5 连接 h1.1 ≈ 10.4MB/s，5 条 h2 流 ≈ 4.9MB/s）。
- **结论：不删多线程**。删掉等于放弃实测约 4.2 倍的吞吐；项目自己的 `docs/archive/superpowers/specs/2026-10-13-download-engine-optimization.md` 定的方向也是「分片健壮化 + 失败语义统一」，不是砍功能。
- **验证**：`./gradlew.bat :app:assembleDebug` 双 flavor 成功；`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 各 **88 suites / 946 tests / 0 failures**（新增 `SegmentDownloadPolicyTest` 8 例）。
- **遗留**：①分片失败兜底仍是「整文件单连接重下」，没有真正的断点续传（P0，见上述 spec）；②PCDN 域名失效时没有切 `backup_url`；③进度公式仍是 0.2 基准 + `%.1f`，大文件上的低速波动在界面上不明显。**未真机复现卡 20%**（本机 adb 无设备、模拟器未开），看门狗 / 降级路径靠单测与真实直链探针（临时探针单测与结果文件已删除，未进提交）验证。

**本方补充（同日，独立修同一批缺陷时多做的两项）**：上游这轮已经把「分片不校验 206 / Content-Range」「join 超时后并发重下」都修了，方向与本方一致。另有两点为本方独有，已并入同一实现：

- **落盘尺寸兜底**：分片全部写满 `expectLen` 之后仍校验 `file.length() == totalSize`。`setLength` 在部分 FUSE / exFAT 存储上可能静默失败，此时即使每片都算对，文件实际尺寸也未必对，仅凭字节计数发现不了。
- **失败原因留痕**：某片 3 次尝试全失败时打一条日志（含分片号与区间）。旧实现静默吞异常，"服务端忽略 Range / 区间对不上"只表现为"多线程慢、退回单连接"，不看日志查不出来。

**本方实测（MuMu Android 15 / coexist debug，未登录）**：av170001 分 P「Хоп」按 720P 下载（`qn=64` → `fnval=1` 单 MP4，7.87 MB > 2 MB 且 CDN 支持 Range → 走的正是分片路径），落地后拉回本机 `ffprobe` 校验 `h264 512x288 + aac / 199.33s / 7871518 字节`，容器可正常解析，0 条 `FATAL EXCEPTION`。未覆盖的是 DASH 双文件分支（未登录拿不到 `qn>64`）。

### 3.64 断网启动仍进自定义启动页：断网兜底进缓存页（26.10.25）

- **缘起**：用户「314」反馈（QQ 截图）：「自从改完打开界面逻辑之后，无网络时打开会跳转到自定义打开界面，希望改回原来那种无网络时打开自动进缓存，能正常加载时再进入自定义界面。」
- **根因**：`3f54b1c` 把启动目标改成「菜单设置里选定的启动页」时，只换了 `SplashActivity` 里的取页逻辑，全流程没有网络可用性判断。`menu_start` 缺省是「推荐」，推荐 / 动态 / 消息等页断网时只能看到加载失败；原本能离线使用的「缓存」页反而被启动页设置挡在后面。
- **改法**（三条，互不耦合）：
  - `util/NetWorkUtil.java:693` 新增 `isNetworkAvailable(Context)`：移植上游 1.2.4 的本地判断（`getActiveNetworkInfo() != null && isConnected()`，不用 API 23 的 `NetworkCapabilities`——`minSdk 19` 上老系统会拒绝），异常一律返回 `true`（fail-open，判断不出来就退回原来的失败路径）。
  - `util/MenuConfig.kt:107/146` 新增 `OFFLINE_START = "local"` 与纯函数 `resolveLaunchStart(enabled, stored, online)`：`online` 走原 `resolveStart`，否则固定 `local`。**只决定本次启动，不写回 `menu_start`**。
  - `activity/SplashActivity.kt:341-363`：启动时先取 `online`，按 `resolveLaunchStart` 选页；断网在 `finish()` 后 `MsgUtil.showMsg(R.string.msg_splash_offline_to_cache)`（缓存页 `BaseActivity` 注册 EventBus 后收到 sticky 消息）；四处启动网络请求（token/Cookie 刷新、`AppInfoApi.check`、公告、匿名统计）整体包进 `if (online)`——这是断网时不再弹「终端接口出现问题」的关键。更新日志页与启动提示都走本地判断，保留。新文案 `msg_splash_offline_to_cache` 进 `strings.xml`。
- **单测**：`MenuConfigTest` 新增 3 例（在线沿用用户设置、断网无论设置成什么都进 `local`、`local` 必为固定项）。
- **验证（MuMu 模拟器，Android 12 / x86_64，26.10.25）**：
  - 在线默认：启动 → `RecommendActivity`（默认启动页）。
  - 在线自定义：`menu_start=settings` → `SettingMainActivity`（任务栈实测）。
  - 断网（`Active default network: none`）：启动 → `LocalListActivity`，logcat `I/MsgUtil>showMsg: 无网络，已进入缓存页`，缓存页底部 Snackbar 显示同一句；全程无「终端接口出现问题」。
  - `./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest` 全绿（双 flavor 各 **88 suites / 952 tests / 0 failures**，含新增 `MenuConfigTest` 3 例；以本轮 rebase 到最新 main 后 `app/build/test-results/` 下 `TEST-*.xml` 的 `tests=` 求和为准）。
- **遗留**：①真手表未验证（本机只有模拟器）；②断网启动仍会有 `BiliTerminal` 启动检查（更新检查、未读、追番）的后台网络失败日志，它们各自 catch 只记日志、不弹提示，属既有行为，本次不动；③模拟器上 `svc wifi disable` / `cmd wifi set-wifi-enabled disabled` 会让 guest 的 adb shell 卡死，断网测试只能靠重启模拟器，后续模拟器断网测试不要用关 Wi-Fi 的方式。

### 3.65 短视频页开启「圆屏适配」后并未适配（26.10.25）

- **缘起**：用户报告「短视频界面在开启圆屏适配后并未适配」。
- **现状（为什么没适配，两处）**：
  - `activity/base/BaseActivity.kt` 的 `setRound()` 只在 `findViewById(R.id.pageName) != null` 时执行；短视频页顶栏在 ViewPager item 的 `fragment_short_video_page.xml` 里、只有 `text_title`，没有 `pageName` → 整段被跳过（普通页面靠它把标题横向内缩 18% 宽、时间居中那一套完全没有作用到本页）。
  - `ShortVideoPlayerActivity.PageHolder.adjustVideoSize()` 只按整屏做 fit，没有圆屏内接圆分支；而本页继承 `InstanceActivity` → `BaseActivity`，`player_ui_round` 打开时 `BaseActivity.onCreate` 会给根 View 加 5%/3% 的 root padding（内容区小于整屏），算画面尺寸时也没扣内容区。
- **改法（单文件 `app/src/main/java/com/RobinNotBad/BiliClient/activity/video/ShortVideoPlayerActivity.kt`，+127 / −13）**：
  - 新增三个属性：`roundAdapted`（读 `SettingsKeys.UI_ROUND`）、`roundPadding = (screenWidth * 0.03f).toInt()`（与 `PlayerActivity.initUI()` 同一口径）、`contentWidth/contentHeight`（= `BaseActivity.window_width/window_height`，≤0 时退回整屏尺寸）。
  - `PageHolder` 先快照 `top`/`videoProgress`/`textProgress` 的 XML 基础 padding，`applyRoundInsets()` 按开关收放：圆屏时 `top.setPadding(padding*7, padding, padding*7, 0)`、进度条左右 `padding*4`、时间文本左 `padding*3`、弹幕上下 margin `padding*3`；关闭时逐项还原（幂等）。`bind()` 与 `initPlayer()`（拿到 `danmakuView` 之后）各调一次。
  - `adjustVideoSize(screenW, screenH)` 重写：可用区域优先取 `itemView` 实测尺寸（回退 `contentWidth/contentHeight`）；圆屏按视频比例求内接圆矩形——`diameter = min(屏宽, 屏高)`、`videoMul = 视频高/宽`、`side = sqrt(diameter² / ((视频高/宽)² + 1))`、宽 = `side`、高 = `side * videoMul`，再按可用区域等比夹；非圆屏分支按可用区域 fit（原逻辑按整屏）。**取 `min` 而不是 `max`**：首轮在 1440×2560 的模拟器上 `max` 会算出比屏宽还宽的画面。
- **验证（MuMu 模拟器，Android 12 / x86，`wm size 320x320` + `wm density 160` 模拟圆屏，26.10.25）**：
  - 圆屏开（5% / 3%）：内容区 `[16,9][304,302]`；`top [16,9][304,35]`、`text_title [79,18][241,35]`（= 16 + 7×9）、`videoprogress [16,216][304,236]`、`text_progress [16,238][304,253]`、`bottom_buttons [16,253][304,302]`。竖屏视频算出 157×279（9:16 内接圆），像素测量视频占列 81..237 与公式一致，四角距圆心 ≈160，底栏各按钮中心距圆心 ≈142~143（均 <160）。
  - 圆屏关：`top [0,0][320,25]`、`text_title [8,4][312,21]`（XML 原值）、竖屏视频 180×320 满高 —— 与改动前完全一致（无回归）。
  - 合入前的最终构建（已删掉 `onStart()` 热刷新钩子）在同一模拟器上复跑：`top`/`videoprogress`/`text_progress`/`bottom_buttons` 坐标与上面逐字一致，16:9 横屏视频同样按内接圆（279×157）居中，播放正常。
  - `.\gradlew.bat :app:assembleDebug` 与 `.\gradlew.bat :app:testStandardDebugUnitTest` 均通过。
- **遗留**：①真手表 / 真圆屏设备未验证（本机只有 MuMu 模拟器）；②没有加 `onStart()` 热刷新钩子——`MenuActivity.killAndJump()` 会先 finish 下层页面，从设置页回到短视频页必然是新实例（冷启动路径），root padding 与内缩都随新实例一次性生效；③验证后期 B 站短视频信息流接口偶发 `API error code=-400: 请求错误`（`ShortVideoFeedApi.x/v2/feed/index/story` 与动态兜底都失败），与本次改动无关；重试后恢复，最终构建复测正常。

### 3.66 DASH 下载完成后合并成单文件：1080P+ 也像 720P 一样一个文件（26.10.25，用户要求）

- **缘起**：用户提出「B 站 1080p 以上的缓存视频分视频流和音频流的分开的，现在我想让你给他变成和 720p 一样的合并的视频」。
- **现状（为什么以前是双文件）**：`processDownloadSection` 里 `qn<=64` 走 `PlayerApi.getVideo`（`fnval=1`，音视频合一 MP4）；1080P+ 只能走 DASH，下成 `video.mp4` + `audio.m4a`，播放时由 `PlayerActivity.audioTrackUrl` 额外挂一条外部音轨。合并层历史上存在过（`util/MediaMerger.kt`），2026-08-18 `25893f3` 因「合并阶段无进度卡 85%」「旧实现先删视频再改名有丢数据风险」被移除，2026-09-11 `183523f` 删掉了类本体；`docs/archive/superpowers/specs/2026-10-13-download-engine-optimization.md` 还写着「不做合并」。这次**决策反转**：两个旧硬伤现在分别有「进度分段常量」与「`safeReplaceTemp` 先备份再替换」的现成解法。
- **改法**：
  - 新增 `app/src/main/java/com/RobinNotBad/BiliClient/util/MediaMerger.kt`（`object MediaMerger`，259 行）：两个 `MediaExtractor` 各取第一条 `video/*` / `audio/*` 轨，**按 `sampleTime` 归并**写入 `MediaMuxer(MUXER_OUTPUT_MPEG_4)`（改掉旧实现「每轮一视频一音频」的交替写）；关键帧标志用 `MediaExtractor.SAMPLE_FLAG_SYNC`（避开 API 21 才有的 `BUFFER_FLAG_KEY_FRAME`）；`setOrientationHint` 透传旋转；写前查 `StatFs` 剩余空间（需 ≥ 两输入之和 + 4MB）；返回码 `RESULT_OK / NO_TRACK / NO_SPACE / STOPPED / FAILED`，**非 OK 一律删产物**。KDoc 写明「失败必须保留双文件、不得判下载失败」。
  - `service/download/DownloadPathSpec.kt`：新增 `FILE_VIDEO_MERGING = "video_merging.tmp"`（刻意不用 `.mp4` 后缀，避免被 `LocalListActivity` 对历史 `video_merged_temp.mp4` 的兼容改名逻辑误认成成品）。
  - `service/download/DownloadProgressMath.kt`：新增 `DASH_ATTACH_END = 0.12f` / `DASH_VIDEO_END = 0.52f` / `DASH_AUDIO_END = 0.88f` / `DASH_MERGE_END = 1.0f` 与 `dashMergeProgress(fraction)`；`DownloadProgressMathTest` 新增 2 例（严格递增 + 区间映射/夹取）。
  - `service/DownloadService.kt`：单 P / 多 P 两处 DASH 分支在 `replaceDashPairOrFail` 之后调 `mergeDashPair`（新增私有方法）——先判 `isPaused` 再合并；合并进度驱动 `setDownloadProgress`，`shouldStop = { !started }`；成功 `safeReplaceTemp(merged, video.mp4)` 后删 `audio.m4a`，失败只记日志。
  - `util/FileUtil.java`：`cleanDownloadTempFiles` 增加删除 `video_merging.tmp`。
  - `res/values/strings.xml`：新增 `text_download_merging`「合并音视频」。
  - `app/src/debug/.../DebugBridge.kt`：新增 `merge` 命令（给两条 m4s 直接跑一次 `MediaMerger`，回显返回码/耗时/产物轨道与采样数），`help` 与 `docs/review/agent-test-guide.md` §4.8 同步。
  - 新增设计文档 `docs/superpowers/specs/2026-10-25-dash-merge-single-file.md`（含历史决策反转说明与 API 19 兼容性依据）。
- **验证**：
  - 构建：`.\gradlew.bat :app:assembleDebug` 双 flavor 成功；`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 各 **88 suites / 954 tests / 0 failures**（`app/build/test-results/` 下 `TEST-*.xml` 求和）。
  - 设备侧容器验证（Wear OS 模拟器 `sdk_gwear_x86_64`，Android 14，装 `standardDebug` universal 包；注意 **x86 单 ABI 包装不上**，本模拟器 `abilist=x86_64,arm64-v8a` 没有 32 位）：把仓库根的两条匿名 360P 样本 m4s（视频 3,867,429B / 音频 1,265,842B）经 `run-as` 拷进应用私有目录，广播 `--es cmd merge` → `result=OK(0) cost=3381ms`、产物 5,167,200B、`tracks=2`（`video/hevc` 2992 samples / `audio/mp4a-latm` 8584 samples，与源文件一致）、`progress=0,20,50,90`。
  - 踩到的坑：`adb shell` 推到 `/sdcard/Android/data/<包名>/files/` 的文件，应用进程里 `File.isFile` 看不到（广播返回「文件不存在」）；改用 `adb push /data/local/tmp` + `run-as cp` 进 `/data/data/<包名>/files/` 后正常。**这是验证路径的坑，不影响真实下载**（真实下载由应用自己写外部目录）。
- **遗留**：①真实登录态下载 1080P→合并→本地播放整条链路未跑（见 §2.1）；②真手表未验证；③HEVC 视频轨写入需 API 24+，但 `DashData` 选流优先 AVC，正常路径不触发；④合并期间多占约一份文件大小的磁盘空间，空间不足直接跳过合并（保留双文件）。

### 3.67 部分设备弹窗显示成「只有方块、没有内容」（OPPO Watch X2 Mini，26.10.25，用户反馈）

- **缘起**：用户发来实拍照片：「修复部分设备弹窗显示成这样的bug」——圆表盘屏幕内一个接近整屏的方形面板，只有 1px 描边、**里面没有任何文字/按钮**。追问后确认：设备 `oppo watch x2 mini`，只有这一款/少数机型出现，**每次启动必现**（即启动时的「隐私说明」`TerminalDialog.choice` 空壳，按钮不可见 = 用户卡在闪屏，进不了 App）。
- **复现对照**（都不是 bug 的成因）：MuMu Android 12 1440×2560@640、`wm size 320x320`+`density 160`、`wm size 454x454`+`density 320`、Wear OS 5 圆屏 466×466@320（`dumpsys uimode` = `0x16`，真手表 uiMode）——隐私弹窗**全部渲染正常**；实验性给 overlay 加 `android:windowIsFloating=false`、`android:windowElevation=0dp` 也不复现（appcompat `values-watch-v20` 那条覆盖不是原因）。
- **取证**：无法拿到该机 adb，临时加了一个诊断包 —— `util/DialogDiagnostics.kt`（`ENABLED=true`）在 `Sheet` 的 `onShow` 后抓版本/设备/Configuration/屏幕 metrics/窗口 `LayoutParams`/主题属性/完整 DecorView 视图树（含每层 `bg` 描述与 `getGlobalVisibleRect`），写 logcat（TAG=`DialogDiag`）+ `files/dialog-diag.txt`，再跳 `ShowTextActivity` 整页显示让用户拍照回传（空白弹窗拍不出来，但这页一定渲染）。用户装上 `coexist release` 诊断包后回传了报告。
- **真机报告（OPPO OWW242 / Android 11 SDK 30 / ColorOS Watch）关键项**：`Configuration 233x233dp densityDpi=320 uiMode=0x26 round=true`；屏幕 `466x466 density=2.0 dpi=320`；窗口 `w=378 h=-2 gravity=17 fmt=fffffffe`、`decor 378x415 bg=Color#00000000`；主题 `android:background=GradientDrawable`、`windowBackground=GradientDrawable`、`windowIsFloating=false`；颜色 = `Theme.ClassicTerminal`（`colorSurface=#FF262626`、`colorOnSurface=#FFEBE0E2`、`colorPrimary=#FFFF6699`，照片里的紫是相机偏色）。
- **根因（视图树直接给出）**：**主题 `android:background` 会被框架/ROM 贴到弹窗 window 里每一个内部视图上** —— 报告里 `SwipeDismissLayout` / `FitWindowsFrameLayout#action_bar_root` / `ContentFrameLayout#content` / `AlertDialogLayout#parentPanel` / `topPanel` / `contentPanel` / `customPanel` / `custom` 的 `bg` 全是同一份 `GradientDrawable`（= `@drawable/dialog_background`）。且 ColorOS Watch 的 `DecorView` 末尾**多一个 `SwipeGlowOverlay`**（378×415），也被贴上同一份**不透明**面板，它排在内容之后 = 画在最上层，把我们 `@id/custom` 里的内容整块盖住。MuMu/Wear OS 没有这个 overlay → 一直只有部分设备复现。（此前用「把主题 background 换成纯品红」的实验已在 MuMu 上证实主题 background 确实落在 appcompat subdecor 各层，只是那里背景透明/无遮挡，肉眼看不出来。）
- **改法（3 个文件，不再依赖主题 `android:background`）**：
  - `res/values/themes.xml`：7 份 `ThemeOverlay.<X>.Dialog` 删掉 `<item name="android:background">@drawable/dialog_background</item>`，只留 `colorSurface`/`colorOnSurface`/`colorError`；注释块补上「为什么不能再写」的证据。
  - `res/drawable/dialog_background.xml`：注释改为「只给 `layout_dialog_terminal.xml` 根视图用，不要再挂主题 android:background」。
  - `util/TerminalDialog.kt`：`Sheet.init` 里 `content.background = AppCompatResources.getDrawable(dialog.context, R.drawable.dialog_background)`（窗口背景仍透明）；新增 `Sheet.stripForeignBackgrounds()`，在 `onShow` 第一帧后遍历 `decorView`，把**不属于 content 子树**的视图背景清成 `null`（兜住 ROM 在 show 之后才挂的装饰层）。
- **验证**：
  - Wear OS 5 模拟器（466×466@320 圆屏，standardDebug universal 包）：隐私弹窗外观与修前**逐字节相同**（截图 sha256 一致），诊断视图树里 `android:background=null`、内部各层 `bg=null`、content 根 `bg=GradientDrawable`（419×440 窗口）。
  - MuMu Android 12 1440×2560@640：面板/圆角/1dp 描边/标题/正文/「不同意·同意」/提示行全部正常。
  - `.\gradlew.bat :app:assembleDebug` 成功；`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 均 BUILD SUCCESSFUL、0 失败。
  - 诊断代码已清理（`DialogDiagnostics.kt` 删除、`TerminalDialog` 与 `SplashActivity` 里 `ENABLED` 分支删除、`SplashActivity` 恢复原样），临时提升的 `versionCode 2610242` 已在 `c340444` 正式提交里保留。
- **遗留**：①**用户侧真机未复测**（本机没有 OPPO 手表），只验证了「主题不再给内部视图贴背景 + 内容树自绘面板」这一条链路；②若该 ROM 还有其他在内容之后挂的装饰层且带**非 null 背景**，`stripForeignBackgrounds()` 会在 `onShow` 后的第一帧清掉它——但如果它在那之后再挂就仍可能遮挡（当前证据里没有这种情况）；③`docs/agents/pitfalls.md` 里若还有「弹窗面板走主题 background」的旧说法需要同步（已 grep：无）。

### 3.68 「我的」页左上箭头方向错误（26.10.25，用户反馈）

**规则**（26.10.25 用户明确）：**由菜单唤起的一级页**用 `arrow_down`，点它打开菜单并在同级页面间切换；**一级页的子栏目**用 `arrow_back`，点它返回上一级。图标与行为必须一致。

- **现象**：菜单进「我的」，顶栏标题左边是 `←`（返回箭头），但点它并不返回——打开的是菜单。
- **根因**：`BaseActivity.kt:584` 对 `InstanceActivity` 装的是「点顶栏打开菜单」（`setMenuClick`），对其它页面装的才是「返回」（`setTopbarExit`）。`MySpaceActivity : InstanceActivity()` 属于菜单级，图标该是 `arrow_down`；但 `activity_myspace.xml:28` 写死了 `@drawable/arrow_back`。
- **为什么不能只改布局**：这个布局被 `MySpaceMoreActivity : BaseActivity()`（返回级）复用，它那边 `arrow_back` 才是对的。改布局等于把「更多」页改坏。
- **修法一（图标跟随角色）**：角色判定收进 `InstanceActivity.setMenuClick()`——装监听时顺手把 `pageName` 的开头图标覆盖成 `arrow_down`。菜单级一律朝下、返回级保持布局里的 `arrow_back`，新页面继承 `InstanceActivity` 就自动对。
- **修法二（子栏目站错队）**：`TodoListActivity`（「TO DO清单」）是从「设置」进的**子栏目**（`SettingGroupActivity:725` / `SettingsIndex:197`），却继承了 `InstanceActivity`——于是顶栏点开菜单、图标也被修法一改成了 `⌄`，反过来错。已改成 `: BaseActivity()`，同时 `activity_todo_list.xml` 里原本写死的 `arrow_back` 就此正确。核对过菜单 14 个入口：全部经 `InstanceActivity`（含 `RefreshMainActivity` 间接继承），只有它一个子栏目混进来。
- **不受影响**：`LocalListActivity`（菜单「缓存」）顶层自己就设 `arrow_down`、进文件夹后设 `arrow_back`，两次都在基类之后执行。
- **实测（MuMu Android 15 x86_64 / coexist debug）**：菜单页 `^`（自身，收起菜单）；「设置」`⌄`、「我的」`⌄` 且点顶栏打开 `MenuActivity` ✓；「TO DO清单」`‹` 且点顶栏返回 `SettingMainActivity` ✓；「更多」仍是 `‹` 且返回 ✓；`logcat` 0 条 `FATAL EXCEPTION`。
- 规则已记进 `docs/agents/pitfalls.md`「基类与列表」。

### 3.69 启动时发现新版本自动跳到更新页（26.10.25，用户要求）

- **缘起**：用户要求「先同步远端仓库，之后修改，当发现新版本的时候自动跳转到下载页面」。手表屏小，此前非强制更新只在启动时闪一条 Toast「发现新版本 x.y.z」（`BiliTerminal.kt` 旧 `:491`），用户很容易错过、也就不会去更新。
- **改法（3 个文件）**：
  - `util/UpdateManager.kt:96` 新增纯函数 `shouldAutoOpenUpdatePage(config, installedVersionCode, lastSeenVersion)`：**非强制** 且 `config.versionCode > installedVersionCode` 且 `config.versionCode != lastSeenVersion` 才返回 true。不碰 `Context` / `SharedPreferences`，直接 JVM 单测。
  - `BiliTerminal.kt` 非强制分支（现 `:545-555`）不再直接弹 Toast：条件满足时 `CenterThreadPool.runOnUiThread { requestAutoOpenUpdatePage(config) }`。`requestAutoOpenUpdatePage()`（`:129`）把版本存进 `@Volatile pendingAutoOpenUpdate`（`:121`）再尝试打开；`tryShowAutoOpenUpdatePage()`（`:140`）在前台 Activity 就绪时 `startActivity(UpdateActivity)`，extras 与强制更新链路一致（`has_config` / `version_code` / `version_name` / `description` / `download_url` / `force_update=false`）；页面拉不起来时退回原来那条 Toast（`:158`）。
  - `onActivityResumed`（`:432`）末尾再调一次 `tryShowAutoOpenUpdatePage()`——**检查更新是异步的，结果可能在启动页还在播动画时返回**，那时直接 `startActivity` 会被启动页随后拉起首屏的动作盖住；`SplashActivity` 不算就绪，已在 `UpdateActivity` 上也不再叠一层。
  - 「一个版本只自动跳一次」不加新状态：更新页 `showUpdateInfo()` 本来就写 `update_last_new_version`，这里直接复用（用户看过没更新就不再反复打扰）。强制更新链路（`forceUpdateBlocking` + `onActivityPreCreated` 拦截）未改。
  - 新增 `app/src/test/java/com/RobinNotBad/BiliClient/util/UpdateManagerAutoOpenTest.kt` 6 例：首次见到新版 / 上次记录更旧 → 跳；强制更新 / 已看过 / 同版本 / 更旧发行版 → 不跳。
- **验证**：
  - 构建：`:app:assembleDebug` 双 flavor 成功；`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 成功，`UpdateManagerAutoOpenTest` 6 例 0 失败。
  - 设备侧（SDK AVD `wear5`，Android 14 / 466×466 圆屏，`coexistDebug` universal 包；**临时**把 `versionCode` 降到 `2610230` 造出「已装 < Gitee 最新 `2610240`」，测完已改回 `2610242`）：
    - 首启：`logcat` 有 `更新检查>发现新版本 26.10.24，已自动打开更新页`；前台 Activity = `UpdateActivity`，页面显示「发现新版本 / 版本 26.10.24 (2610240) / 取消 / 下载更新」。
    - 更新页打开后 `default.xml` 里 `update_last_new_version=2610240`；杀进程再启动，前台停在 `RecommendActivity`、无自动打开日志 → 「一版一次」生效。
    - 环境坑：AVD 启动后 Wi-Fi 默认没连（`wlan0` DOWN、`ip route` 为空），应用报 `Unable to resolve host "gitee.com"`；`adb shell svc wifi enable` + `adb shell cmd wifi connect-network AndroidWifi open` 连上 `10.0.2.16` 后才能测（已记进 `docs/review/agent-test-guide.md` §6）。
- **遗留**：①**启动期若恰好有新公告**（`TerminalApi.checkAnnouncement` 把服务端返回的未读公告逐条 `MsgUtil.showText`，只记最大 id），公告页会叠在更新页之上，按返回逐层退出后才能看到更新页——这是既有启动公告行为，本次不改（用延迟/重排去抢时序太脆弱）；用户已读过最新公告（常态）时不出现。②真手表未验证，只有 SDK AVD。
- **文档**：`docs/FEATURES.md` §14.15 与「后台检查更新」、`docs/copy-inventory/02-update-log.md` L-032、`docs/architecture-map.md`（启动链路 + 改功能注意）。

### 3.70 部分设备内测码更新报 `Trust anchor for certification path not found`（26.10.26，待真机）

- **症状**：部分设备长按「检查更新」输入内测码后报 `java.security.cert.CertPathValidatorException: Trust anchor for certification path not found`；同一台设备走 Gitee / GitHub 的普通更新正常。
- **根因（推断，本机沙箱连不上两个分享站，没能直接看到证书链）**：内测分享站（`file.zsapp.asia` / `share.lanol.cn`，自建 FileCodeBox）用的是 Let's Encrypt 证书，它的根 **ISRG Root X1 直到 Android 7.1.1（API 25）才进系统证书库**。以前老系统靠「DST Root CA X3 交叉签名」兜底，2024 年 Let's Encrypt 已停发这条链，于是 Android 7.1 及以下（手表大量停在 4.4~7.1）找不到信任锚；Gitee / GitHub 用 DigiCert / Sectigo 的老根，不受影响。正好对上「部分设备」。
- **改法**：
  - 新增 `res/raw/extra_ca_roots.pem`：ISRG Root X1（至 2035-06-04）+ ISRG Root X2（至 2040-09-17），两张的 SHA-256 已与 Let's Encrypt 公布的值核对过，指纹写在 `ExtraTrust.java` 类注释里。PEM 里故意不写注释，老系统的证书解析器不一定容忍 PEM 块外的文字。
  - 新增 `util/ExtraTrust.java`：系统 TrustManager 的全部 `acceptedIssuers` + 内置根，一起放进内存 `KeyStore`，再用 `TrustManagerFactory.init(ks)` 生成**平台标准**的 `X509TrustManager`。链、有效期、主机名照常校验，**不是 trust-all**。没写成「先系统、失败再内置」的组合 TrustManager：API 24+ 的系统 TrustManager 是网络安全配置的 RootTrustManager，有 domain-config 时只接受带主机名的 `X509ExtendedTrustManager` 调用，而那个类 API 24 才有，minSdk 19 上继承会类加载失败。任何一步出错都回落系统默认。
  - `util/UpdateManager.kt` 的 `okHttpClient` 套上 `ExtraTrust.apply(...)`，所以更新检查、内测解析、内测 txt、APK 下载都走合并后的信任。**带登录 Cookie 的主客户端 `NetWorkUtil` 没动**，不扩大信任面。
- **验证**：本机沙箱没有 Android SDK，**`assembleDebug` / 单测没跑成**。把 `ExtraTrust` 的纯逻辑部分（`readCertificates` / `systemTrustManager` / `build`）剥出来在 JDK 25 上测过：PEM 读出 2 张证书（ISRG Root X1 / X2）；keytool 自签 CA 签发的叶子证书，系统 TM 拒绝、合并 TM（不含该 CA）**仍然拒绝**、合并 TM（含该 CA）通过。
- **未验证 / 残留**：①Android 编译与单测待跑；②要在 Android 7.1 及以下的真机或 AVD 上跑通内测码下载才算真修好；③如果分享站换成别家证书（比如根也不在老系统里），往 `extra_ca_roots.pem` 追加对应根证书即可，代码不用改；④如果其实是服务器漏发了中间证书，所有设备都会报错，那就不属于这次修的问题，要改服务器配置。

### 3.71 搜索排序入口挪进搜索框内部 + 收起逻辑回退（26.10.26 发版后，用户要求）

- **缘起**：用户要求「查看排序这个按钮加入之前搜索框的逻辑，现在删除搜索框，之后用这个图标（`filter_filters_funnel_list_navigation_sort_sorting_icon_123212.ico`）放在搜索框的最最左边来替代底部的排序功能入口，将搜索框的收起逻辑改为没加入排序功能之前的逻辑（一模一样最好）」。即：撤掉 26.10.24 加的那条全宽排序按钮与为它引入的整套高度动画，排序入口收敛成搜索框内左侧一个图标按钮。
- **改法（2 个代码/布局文件 + 2 张新图 + 3 处文档）**：
  - `app/src/main/res/layout/activity_search.xml`：删掉 `<Button @+id/searchSortButton>`（全宽、锚 `searchbar` 下方那条）；`ViewPager` 的 `layout_constraintTop_toBottomOf` 回 `@id/top`（旧版口径：搜索框盖在列表上方、列表从顶栏下方就开始走）；`searchbar` 内部最左边新增 `@+id/searchSortButton` 的 `ImageView`（30dp 宽、`marginStart 3dp`、`padding 5dp`、`scaleType=fitCenter`、`visibility=gone`、`contentDescription` 复用 `text_search_sort_title`，id 沿用旧名所以 `findViewById` 绑定方式不变）。
  - `SearchActivity.kt`：删整套 `applyTopChrome` / `setViewHeight` / `resetViewHeight` / `naturalHeightOf` / `setDropdownVisible` 与 `chromeAnimator` / `naturalHeights` / `CHROME_ANIM_MS`；`onScrolled` 逐字回退到加排序之前（`ObjectAnimator.ofFloat(searchBar, "translationY", 0f, -height)` + `handler.postDelayed({ visibility = GONE }, 200)`，展开反向，`dy < -1` 才展开，`animate_last` 200ms 节流）；`refreshSortBar()` 只控制图标显隐与点击（`hasSearched` 且当前 fragment 有 `sortOptions()` 才显示，风控时 `alpha=0.4` + 点击 `MsgUtil.showMsg` 后 `retry()`）；新增 `applySortIconVisible()` 在 `keywordInput.setPaddingRelative` 上把 `paddingStart` 在 8dp 与 `SORT_ICON_SLOT_DP=38f`（3dp 边距 + 30dp 图标 + 5dp 间隙）之间切，给图标让位。`showSortDialog()` 与 `TerminalDialog.singleChoice` 未改。
  - 图标：新建 `app/src/main/res/drawable-nodpi/icon_sort_filter.png`（仓库无密度分桶目录，图片一向放 `mipmap-nodpi`）。源 `.ico` 是 128×128 纯黑图形，黑色在 `#80242424` 的搜索框上看不见，用 Pillow 裁 alpha bbox（9,17,119,111）→ 6% 留白 → LANCZOS 缩到 128 → RGB 统一重着色成 `#EBE0E2`（与 `icon_search` 同色），alpha 原样保留。亮色模式另出一份 `app/src/main/res/drawable-notnight-nodpi/icon_sort_filter.png`（同图形、RGB 换成 `#3A3438`，与 `drawable-notnight/icon_search.xml` 的描边同色）：亮色模式的搜索框底是 `#0A000000`（浅底），`#EBE0E2` 那份会隐形，`drawable-notnight` 里既然已经给 `icon_search` 备了深色版，这个新图标也得同一套。以后改这个图标两份一起改（`tools/appearance/gen_light_mode.py` 的约定）。
- **验证**：
  - 构建/单测：`assembleDebug` 双 flavor 成功；`testStandardDebugUnitTest` + `testCoexistDebugUnitTest` 全绿。
  - 设备侧（SDK AVD `wear5`，Android 14 / 466×466 圆屏，standard debug，headless 启动 + `adb emu power ac off` 否则 Wear 会盖一层 `ChargingActivity`）：未搜索时 `searchSortButton` 不在 `uiautomator` dump 里（GONE ✔）；输入 `linux` 回车后以 `ImageView` 出现，bounds `[41,91][101,175]`（= 3dp 边距 + 30dp 图标，密度 2.0）、`content-desc="排序：综合"`、`clickable=true`；像素测量（screencap + Pillow 统计亮列）图标描边占 x=53..88、文字从 x=112 起，互不重叠，`paddingStart` 让位生效；点图标弹出 `TerminalDialog` 单选（标题「搜索结果排序」，项「综合 ✓ / 播放量 / 最新」，第四档「收藏」在滚动区外，底部「点空白处取消」），点「播放量」后 `content-desc` 变「排序：播放量」、列表 798.2万 → 502.0万 降序、弹窗消失，选档 → `selectSort("click")` 链路通。
  - 收起/展开（旧版口径）：上滑 `input swipe 233 350 233 120 300` 后 dump 里 `searchbar` 整个 GONE、卡片从 y=87（`@id/top` 下方）开始铺 ✔；下滑 `input swipe 233 130 233 400 400` 后 `searchbar` 回到 `[23,91][443,175]`、`content-desc` 仍是「排序：播放量」✔。
  - 亮色模式：把 `shared_prefs/default.xml` 的 `ui_color_mode` 临时改成 `light` 复测（测完改回 `dark`）——列表底色平均亮度 237 确认亮色生效，图标区最小亮度 4.0、958 个暗像素，说明 `#3A3438` 那份可见；列扫描图标描边到 x≈103、文字仍从 x=112 起，与暗色一致。
  - `logcat -b crash` 无本应用崩溃（只有模拟器自身 sensor HAL 的 `unexpected sensor type: 26`）。
- **遗留**：①真手表未测，只在 SDK AVD 上验证；②亮色版图标是手工另存的一份 PNG，`tools/appearance/gen_light_mode.py` 目前不覆盖 `drawable-nodpi`，下次重跑该脚本不会自动生成它（不影响运行，只是别以为它是脚本产物）。
- **文档**：`docs/FEATURES.md` §4.2 搜索排序条目、`docs/agents/pitfalls.md` 三条（搜索框盖住 fragment / 收起走 `translationY` / 图标靠 `paddingStart` 让位）、`docs/architecture-map.md` §8.7.3 搜索框滚动隐藏逻辑、`docs/review/fix-progress.md` §1「发版后的未发版改动」与本节。

### 3.72 系统时间错误防呆（26.10.27，用户反馈）

- **缘起**：用户（Sonim XP5800 三防机，非触屏）反馈 B 站与 Gitee 同时报证书错误，系统浏览器弹「Security warning」，要求「无视风险直接信任证书」。用 openssl 查过两条链：B 站是 GlobalSign R1、Gitee 是 TrustAsia → Certum，都是老根，不缺。两家 CA 不同却同时失败，最后确认是**设备时间不对**（没电重启回到出厂时间），所有证书都被判「尚未生效」。trust-all 违反 `docs/agents/security.md` 红线，没做；改成提示用户改时间。
- **改法**：
  - 新增 `util/ClockSanity.kt`（纯函数，不读资源）：`buildDateMillis(versionCode)` 由 YYMMDD0 反推构建日期；`isClockBehind(now, versionCode)` 判当前时间早于构建日期（放宽 1 天抵消时区）；`isCertTimeError(e)` 沿 cause 链找 `CertificateNotYetValidException` / `CertificateExpiredException`，并按消息兜底。
  - `SplashActivity`：进首屏 / 初始化向导后，若 `isClockBehind` 为真，`showMsgLong(msg_clock_behind)`（与断网提示同一套 sticky Snackbar）。
  - `MsgUtil.err`：`IOException` 分支先判 `isCertTimeError`，命中则显示 `err_cert_time`，替代笼统的「网络错误」。
  - 新文案 `err_cert_time` / `msg_clock_behind` 进 `strings.xml`。
- **验证**：
  - `ClockSanityTest` 9 例全过；`assembleDebug` 双 flavor 成功。
  - MuMu（Android 15 / x86_64，root）：`settings put global auto_time 0` + `date 010112002015.00` 把时间拨到 2015-01-01。共存版（未完成初始化）冷启动进 `SetupUIActivity`，截图底部 Snackbar 显示「设备时间似乎不对……请到系统设置里改正日期和时间」✔。普通版 `am start` 推荐页，logcat 原文：`SSLHandshakeException: Unacceptable certificate: CN=GlobalSign RSA OV SSL CA 2018` → `Caused by: CertificateNotYetValidException: Certificate not valid until Wed Nov 21 ... 2018 (compared to Thu Jan 01 12:19:46 GMT+08:00 2015)`，随后 `MsgUtil>showMsgLong: 网络连接被拒：证书时间校验失败 / 请检查设备的日期和时间是否正确` ✔。测完 `auto_time 1` 恢复。
  - 未来时间（同一台 MuMu，`date 060112002031.00`）：推荐页 logcat 原文 `Caused by: CertificateExpiredException: Certificate expired at Tue Nov 21 08:00:00 GMT+08:00 2028 (compared to Sun Jun 01 12:00:01 GMT+08:00 2031)`，随后同样显示 `err_cert_time` ✔（12 秒后的截图里 Snackbar 已按时长消失，以日志为准）。
- **追加：明文取服务器时间，提示里给出具体日期**（同日，用户同意方案）：
  - 新增 `api/ServerTimeApi.kt`：`http://api.bilibili.com/x/report/click/now`（`bilibili.com` 本在明文白名单，不加域名）。**独立 OkHttpClient，不带 Cookie、无拦截器、不跟随跳转**，4 秒超时；`parseNowMillis` 纯解析。
  - `ClockSanity.isSkewed`：本机与服务器时间差 > 1 小时（早晚都算）。
  - `MsgUtil.reportCertTimeError`：先给通用提示，再后台取服务器时间，偏差过大时补一条 `err_cert_time_skew`（设备时间 / 实际时间约）。60 秒内只取一次（`elapsedRealtime` 节流，不受系统时间影响）；该条四行，Snackbar 时长 8 秒并自带「x」。服务器时间**只用于显示**，不参与任何校验。
  - 验证：`ClockSanityTest` 11 例、`ServerTimeApiTest` 2 例全过，双 flavor 单测与 `assembleDebug` 通过。MuMu 拨到 2031-06-01 与 2015-01-01 各测一次，logcat 均出现「设备时间：2031-06-01 12:00 / 2015-01-01 12:00，实际时间约：2026-10-27 18:36」；2031 那次启动后 5 秒截图，提示条四行完整、右侧有「x」✔。测完恢复自动对时。
- **遗留**：①时间被拨到**未来**只在请求失败时提示，启动时不提示——本地没有可靠的「最晚日期」，用上次使用时间当上限会误报；时间只超前几天到几周、证书还没过期时网络本来正常，不影响使用；②系统浏览器 / WebView 的证书弹窗不归本应用管；③未在 XP5800 真机复测。

### 3.73 点赞投币收藏卡片的玻璃质感适配（26.10.27，用户反馈）

- **缘起**：用户反馈视频详情的点赞 / 投币 / 收藏卡片和文章详情末尾的点赞 / 收藏卡片在玻璃质感下观感很差。核查发现两张卡片都在布局里写死底色，玻璃覆盖层管不到：视频页是不透明 surface 底，夹在半透明玻璃按钮中间很突兀；文章页用 `?attr/colorPrimary` 实底，点赞激活后图标（`ThemeTint.setActiveIcon` 也用主色）和底色融成一片，卡片还比上方 UP 卡片宽。
- **改法**：
  - `attrs_glass.xml` 新增 `actionCardColor` / `actionCardStroke`；`themes.xml` 9 个会 inflate 布局的主题都定义了（surface + 各主题 brand 色），`themes_glass.xml` 7 个 `GlassOverlay` 改成玻璃按钮同源的 `glass_btn_bg_fill_<x>` / `glass_btn_stroke_<x>`。
  - `fragment_video_info.xml`、`cell_article_end.xml` 的 `like_coin_fav` 改读这两个属性；文章卡片补上 1dp 描边和 `activity_padding_horizontal` 的左右边距，宽度与 UP 卡片对齐。
  - 非玻璃模式下视频卡片观感不变；文章卡片从主色实底改成和视频页一样的 surface 底 + 主色描边，算顺手统一。
- **验证**：双 flavor `assembleDebug` 与单测通过。MuMu（共存版）截图看了视频详情和文章详情，组合有：粉色玻璃（暗色 / 亮色）、知乎蓝玻璃、经典终端玻璃、粉色非玻璃。两张卡片都和周围的玻璃按钮同一种半透明底和描边，文章卡片宽度与 UP 卡片一致 ✔。测完把主题、玻璃、亮暗设置恢复成默认。
- **遗留**：没有在已点赞状态下截图（要真去点赞），激活态图标和玻璃底的对比是按色值推断的。

### 3.74 弥散光晕代码审查：补单测 + 私信页顶栏挡光晕（26.10.27）

- **缘起**：审查 `ui/appearance/AmbientGlow.kt`。实现本身无误，但注释称「纯函数，供 JVM 单测」而单测为 0；私信页顶栏用 `?android:attr/colorBackground` 不透明底盖在全屏列表上，开光晕后顶部最亮处变成一条纯色横带（经典终端下它取主题 XML 的 `#1B1B24`，与运行时背景 `#000000` 本就不一致）。
- **改法**：
  - 新增 `AmbientGlowTest` 7 例：14 份方案的 `top` 全不透明（drawable 声明了 `OPAQUE`）、几何参数在界内、未知 key 兜底与 `ColorScheme` 一致（B站粉）、色标与 alpha 一一对应且首尾 1 / 0、`scaleAlpha`、亮色 `top` 与亮色背景的 CIE76 ΔE 在 [8, 20]（改米白底色忘了重算 `top` 时会红）。顺手把 `0x70C9A0F0` 补上 `.toInt()`。
  - `activity_private_msg.xml` 根布局 `FrameLayout` → `RelativeLayout`：顶栏去掉底色，列表 `layout_below` 顶栏（`paddingTop` 24dp → 4dp），消息不会再滚到标题下面，所以不再需要不透明底。
- **验证**：`AmbientGlowTest` 7 例全过，`assembleDebug` 通过。MuMu（共存版、经典终端、光晕开）进入真实私信会话截图：标题与时钟直接压在光晕上、没有横带，消息从顶栏下方开始排，输入栏位置不变 ✔。
- **遗留**：没在消息多到需要滚动的会话里测；`clipToPadding=false` 只影响列表自身 4dp 内边距，不会画到顶栏区域（列表本身就在顶栏下方）。

### 3.75 性能审查①：评论 / 动态的表情改为并发加载（26.10.27）

- **缘起**：全量性能审查（手表优先）。`EmoteUtil.replaceSingle` 对每个表情 `submit().get()` **串行阻塞**，而 `Reply` / `DynamicApi.analyzeTextContent` 在构造时就调它：一页 20 条评论 + 楼中楼的表情逐个排队等，首屏要等所有表情下完才显示，弱网下尤其慢；`get()` 无超时，一张卡住就拖到 OkHttp 读超时。私信页更糟：传入的是整个会话的表情表，每条消息都把表里**每一张**图同步取一遍，绝大多数根本不在这条消息里。
- **改法**：
  - `EmoteUtil.textReplaceEmote(ArrayList<Emote>…)`：只对文本里真正出现的表情发请求，先全部 `submit()` 再按同一截止时间（5s）取结果；超时保留原文字。`replaceSingle` 两个重载先判 `contains` 再下载，`get` 也加了 5s 上限。
  - 新增 `EmoteUtil.prefetch(urls)`：整页并发预取（总上限 10s），完成的立即 `clear` 退回内存 LRU；超时未完成的不 clear（clear 会取消下载）。接线点：`ReplyApi.analyzeReplyArray`、`DynamicApi.getDynamicList`。
  - 新增纯函数 `util/EmoteUrlCollector.kt`：评论收 `content.emote.*.url`（含楼中楼），动态只收 `modules.module_dynamic` 子树与 `orig` 里的 EMOJI 节点（评论预览等不渲染的位置不收）。`EmoteUrlCollectorTest` 4 例。
- **验证**：双 flavor `assembleDebug` 与单测通过。MuMu（共存版、已登录）：视频 av80433022 评论区日志 `表情预取 10/10 张，用时 160ms`，楼中楼 `[笑哭]` 截图确认渲染为图片 ✔；动态页 `表情预取 8/8 张，用时 37ms`，列表正常 ✔。
- **遗留**：没有在弱网 / 真表上对比改前改后的首屏耗时；私信会话内的表情没截到图（点击导航在 MuMu 上不稳定），只靠代码路径确认。

### 3.76 弥散光晕可选光源位置：上 / 下 / 左 / 右 / 中间（26.10.27）

- **缘起**：用户希望光晕最亮处不只能在顶部，可选下、左、右、中间，默认仍是上。
- **改法**：
  - `AmbientGlow` 的 14 份 `Spec` 仍只按「顶部」写一份，新增纯函数 `placeGlow` / `baseGradient` 按光源位置变换：下 / 左 / 右是把「沿边位置、离边深度」映射到对应边（下与上、右与左互为镜像，方屏上「左」即「上」转 90°）；中间把各团光晕的重心平移到屏幕中心、半径 × 0.8，底色改为从中心向外的径向渐变。`draw` 仍零分配。
  - 新键 `ui_ambient_glow_source`（`top` / `bottom` / `left` / `right` / `center`，未知值回退 `top`），写入走 `AppearanceManager.setAmbientGlowSource`（规整取值 + 递增版本号）。外观设置在「弥散光晕」下方加「光晕位置」单选，设置搜索可直达。
  - `AmbientGlowTest` 增至 15 例：默认值与规整、落盘与版本号、上位置与原公式一致、各位置贴边、镜像 / 旋转关系、中间位置重心与半径、底色渐变坐标。
- **验证**：双 flavor `assembleDebug` 与单测全过。MuMu（共存版）：选择弹窗列出 5 项；选「左」后页面自动重建、光晕移到左侧，落盘 `ui_ambient_glow_source=left` ✔；下 / 右 / 中间截图位置正确 ✔；亮色经典终端居中正常，五彩斑斓亮色居中时红 / 青两团关于中线对称 ✔。
- **遗留**：没在圆形真表上看「左 / 右」时光晕是否被圆角裁掉太多。

### 3.77 性能审查②：列表图片绑定页面生命周期 + 「翻动时不加载图片」真正生效（26.10.27）

- **缘起**：约 30 处 adapter / holder 用 `Glide.with(BiliTerminal.context!!)`（Application 级，`VideoCardHolder` / `NoticeHolder` / `TimelineAdapter` 还是 companion 里的静态 RequestManager）。后果：离开页面请求不取消，图片继续下载解码；页面退后台不暂停；`ImageAutoLoadScrollListener` 对 Activity 级 RequestManager 的 `pauseRequests()` 管不到它们，「翻动时不加载图片」开关形同虚设。基类与热门 / 必刷 / 排行榜还无条件 `install()`，不看这个开关。
- **改法**：
  - 新增 `GlideUtil.with(view)`：主线程上解析到存活 Activity 时用 `Glide.with(activity)`，否则回落 Application 级（不会抛「destroyed activity」）。不用 `Glide.with(view)`，因为它在 Fragment 树里找宿主，bind 热路径上更慢。上述调用点全部改调它，顺手删掉因此不再使用的 import。
  - `ImageAutoLoadScrollListener` 只在 DRAGGING → SETTLING（手指甩出）时暂停。生效之后要防表冠：表冠走 `smoothScrollBy`，是 IDLE → SETTLING，原来遇到 SETTLING 一律暂停，等于转表冠时图片一张都不加载。纯函数 `shouldPause` 配 `ImageAutoLoadScrollListenerTest` 3 例。
  - 6 处 `install()` 改为 `installIfEnabled()`，设置项关掉时不再暂停。
- **验证**：双 flavor `assembleDebug` 与单测通过。MuMu（共存版）：推荐页连续快速甩动 3 次，停下后封面全部加载 ✔；动态 / 消息 / 推荐快速进出各一次，logcat 没有崩溃 ✔；评论区甩动后头像与表情都正常 ✔。
- **遗留**：MuMu 没有表冠（`input rotaryencoder` 不存在），表冠滚动不暂停这一条只有单测覆盖，需要真表确认。

### 3.78 性能审查③：图片请求宽度按屏幕封顶，图片查看器懒加载（26.10.27）

- **缘起**：列表图请求 512w，高清图请求 1024w，只看档位不看屏幕。手表屏长边多在 320～480px，下载和解码的像素是实际显示的 2～4 倍。`ImageViewerActivity` 一进来就把所有页（动态九图就是 9 张）按 `SIZE_ORIGINAL` 全量加载，低内存的表上容易卡顿甚至 OOM。另外 `VideoCardHolder` 的复用分支只隐藏 UP 主 / 播放量，不再重新显示，卡片复用后这两行可能一直不出来。
- **改法**：
  - `PerformanceManager.capWidthToScreen(tierWidth, screenLongSidePx)` 纯函数：取屏幕长边向上对齐到 320/480/640/720/1024 档，宽度取它与档位值中的较小者；屏幕超过 1024 或读不到尺寸时保持档位值。`getImageMaxWidth()` / `getHqImageMaxWidth()` 都封顶，**只会变小**，CDN 缓存命中仍集中在少数几档。
  - 新增 `getZoomImageMaxWidth()`（不封顶）和 `GlideUtil.url_zoom()`，专给图片查看器用，因为要放大看细节。
  - `ImageViewerActivity` 只加载当前页和前后各一页，翻页时补齐，已加载过的页不再重复请求。
  - `VideoCardHolder` 复用分支补上 `VISIBLE`。
  - `PerformanceManagerTest` 新增 3 例：手表屏用小档，封顶不抬高宽度，大屏 / 未知尺寸保持档位。
- **验证**：双 flavor `assembleDebug` 与单测通过。MuMu（共存版）：466x466 下推荐页封面清晰 ✔；动态九图进入图片查看器后第 1 页正常，滑到第 3/9 页图片都正常加载 ✔；logcat 无崩溃、无 OOM ✔。
- **遗留**：实际请求 URL 里的宽度只靠单测覆盖，没有在模拟器上抓包确认。

### 3.79 反馈列表清账：7 个 bug + 私信撤回 + 内置选图页（26.10.28，用户反馈）

- **缘起**：按控制台未处理反馈逐条核对源码，挑出「原因在代码里看得出来」的 7 条全修，另做两个新功能（#57 私信撤回、#53 内置选图页）。#49（推荐页长按改稍后再看）、#11（内置文件管理器）用户决定不做。括号里是控制台反馈编号。
- **#55 短视频弹幕设置不生效**（此前已回复用户「已修复」，但源码里没有）：`ShortVideoPlayerActivity` 每个 `PageHolder` 写死 `isDanmakuVisible = true`，从不读「播放默认值 → 弹幕」。改成 Activity 级字段，用与长视频同一个 `PlayerDefaults.resolveTriState(PLAYER_DEFAULT_DANMAKU, PLAYER_LAST_DANMAKU)` 开播，切换时写回 `PLAYER_LAST_DANMAKU`。另一半根因在 `DanmakuManager`：`hide()` 有 `isPrepared` 守卫，prepare 之前调用只记状态就返回，所以 `prepared()` 回调里补一次 `danmakuView.hide()`。
- **#44「收到的赞」不显示弹幕点赞**：这类消息（还有「回复我的」里的弹幕被回复）已整体迁入**可选私有功能**，公开侧只留 `OptionalFeatures.fillOptionalMessage` / `openOptionalMessage` 两个挂点；`MessageApi` 的两个消息流在 `default` 分支问一次门面，不认识就照旧「无法识别这个类别」。字段缺省读取（`parseBusinessId` 缺字段给 -1）留在公开侧，`MessageApiBusinessIdTest` 3 例。迁移细节见 §3.83 与私有仓库 README。
- **#52 空关键词搜索后历史记录消失**：`SearchActivity.searchKeyword` 先 `hideDropdowns()` 再判空 `return`。判空挪到最前。
- **#51 文件夹里点 P2 从 P1 开始播 / 老缓存没声音**：`LocalListActivity.playVirtualCollection` 的起播地址固定取 `[0]`，`startPageIndex` 只写进了选集高亮。改成按 `startPageIndex` 取；双文件（未合并 DASH）缓存原来只传 mp4，现在另传与 `videoFileList` 对齐的 `audioFileList`，起播带 `audio_track_url`，`PlayerActivity.switchToLocalPage` 切 P 时把对应音轨交给 `doSwitchPage`（新增 `newAudioTrackUrl` 参数，原来这里固定置 null）。
- **#38 图片查看页仍响应右滑**：清单里的 `Theme.NoSwipe` 被 `BaseActivity.onCreate` 的 `setTheme` 覆盖。`ImageViewerActivity` 覆写 `forceNoSwipeTheme = true`（与短视频页同一钩子），退出走顶栏 / 返回键。
- **#20 表冠默认转得很慢**：原本打算把 `RotaryEncoderSupport` 总开关默认改成开启（`DEFAULT_ENABLED = true`），**用户看过改动后要求回退**，已用 `revert` 撤销，保持默认关闭与原文案。反馈 #20 仍未解决——系统自带表冠滚动在多数页面偏慢，且维护者没有表冠设备可调手感；真正要改需要先拿到原版终端的滚动倍率做对照。
- **#57 私信撤回（新功能）**：长按自己 120 秒内发出的文字 / 图片 → 菜单「复制文字 / 撤回」，撤回即发一条 `msg_type=5`、内容为目标 `msg_key` 的私信（`private_msg_content.md`「撤回消息」）。入口判据 `PrivateMsgApi.canRecall` 纯函数（自己发的、非撤回通知、本地时间看来未超 120 秒；本地时间比消息早时仍给入口，交服务端判，见 §3.72 的时间问题）。顺带：接口对被撤回的原消息仍回原文（`msg_status=1`），原先原文和「撤回了一条消息」两条都显示，现在 `shouldDisplay` 过滤掉原消息；撤回通知 seqno 必大于原消息，过滤不影响 refresh / loadMore 的分页起点。`PrivateMsgRecallTest` 5 例。
- **#53 内置选图页（新功能）**：新增 `activity/ImagePickerActivity`（不导出）+ 纯逻辑 `util/ImagePickerAlbums`（相册分组 / 过滤 / 读图权限名，`ImagePickerAlbumsTest` 4 例）。读 MediaStore 按 `DATE_ADDED` 倒序铺三列缩略图（Glide 按格子尺寸、RGB_565 解码），可按相册筛选；顶部「系统选择器」退回原 `ACTION_GET_CONTENT` 并把结果原样转交。**结果格式与 `ACTION_GET_CONTENT` 相同**（`data.data` 为 content Uri），私信 / 写评论 / 发动态 / 换头像四个调用点只换了 Intent。清单新增 `READ_MEDIA_IMAGES`（Android 13+；13 以下沿用已有的 `READ_EXTERNAL_STORAGE`），只在打开选图页时申请，拒绝后显示说明并可走系统选择器。
- **验证**：双 flavor `assembleDebug` 通过；`testStandardDebugUnitTest` 99 类 / 1039 例 / 0 失败（26.10.29 补 #44 回复流后为 **102 类 / 1087 例**）。MuMu（Android 12L / SDK 32，共存版，已登录）：
  - #52：搜索页空输入点搜索 → 日志出现「还没输入内容喵~」，历史记录列表仍可见（uiautomator 能取到两条历史）✔。
  - #55：`player_default_danmaku=off` 进短视频 → 弹幕 170 条已加载但不显示、按钮是关闭图标 ✔；点按钮打开 → 滚动弹幕出现、`pref_switch_danmaku=true` ✔；再关掉后上滑到下一条 → 仍为关闭 ✔。测完已删掉这两个设置项。
  - #53：选图页三列缩略图正常 ✔；相册弹窗列出「全部图片（226）/ 其他 / burst3 …」，选 burst3 后网格只剩 4 张 ✔；从发动态页进入选一张 → 回到发动态页显示「添加图片(1)」与预览 ✔；「系统选择器」拉起 `documentsui` 的 PickActivity，返回回到选图页 ✔。权限申请链路走到了 `GrantPermissionsActivity`（logcat 可见），但 MuMu 会自动授予，**「拒绝后」的分支没能测到**。
- **遗留**：
  - #44 的两条渲染（「弹幕被点赞」/「弹幕被回复」）仍未在设备上跑过：模拟器未登录，`msgfeed/like` / `msgfeed/reply` 都回 -101，只做过空数据冒烟（`NoticeActivity` 正常渲染、`logcat -b crash` 为空）。解析与文案现在都在私有仓库。
  - **同一轮顺手补的（仍在公开侧）**：`getReplyMsg` 的 `item.getString("source_content")` 与三个消息流共用的 `item.getInt("business_id")`，遇到没有该字段的条目会抛 `JSONException`，**把整页消息带崩**（不是少一条，是整页空白）。
  - ~~#51 没在模拟器上跑~~ → **26.10.29 已在 MuMu 上验证通过**（伪造双文件缓存不需要真机，`adb root` 就能往 `Android/media/<包名>/` 写）：用 ffmpeg 造两条「只有视频轨的 mp4 + 独立 m4a」，按 `DownloadPathSpec` 建 `.folders_info.json` / `.video_meta.json` / `.quality`，实测三条全过——①点文件夹里排第二的视频 → `视频地址` 日志是**那一条**（换成合并前的旧包复测会固定播第一条，正好反证了修复）；②`AudioTrack>主文件轨道=[VIDEO]，外部文件可用=true，选择=EXTERNAL` → `已启用唯一的外部音频轨道`；③在选集面板切到另一 P → `setDisplay: url` 换成另一条、外部音轨重新初始化。配方见 `docs/review/agent-test-guide.md` §4.9。
  - #57 没在模拟器上实测撤回（会真的给对方发私信），只有判据单测；需要真机给自己的小号发一条再撤回。
  - #38 的手势本身只能在真手表上确认（MuMu 没有窗口层右滑返回）。

### 3.80 收藏夹管理页：内容管理（批量删除 / 清空失效）+ 视觉统一（26.10.29，用户要求）

- **缘起**：用户给了 B 站「批量删除内容」（`x/v3/fav/resource/batch-del`）与「清空所有失效内容」（`x/v3/fav/resource/clean`）两份接口文档，要求把功能做在「长按收藏夹跳转出的管理页面」上，并顺手把这个管理页的视觉风格与配色统一到全站口径。
- **入口语义变更**：`FavoriteFolderEditActivity` 从「编辑收藏夹」升级为「收藏夹管理」（顶栏 + manifest label 共用 `text_activityfavoritefolderedit_06`，值改成「收藏夹管理」）。`FavoriteFolderAdapter` 的长按不再拦默认收藏夹（原 `!folder.isDefault` + toast），因为内容管理只在这个页面有入口；默认夹进来改显示常驻提示 `hintDefault`、隐藏改名/删除两块，且不再每次弹 toast。
- **批量删除内容**：管理页新增一行 → 打开 `FavoriteVideoListActivity` 并带 `selectMode=true`，内容页在首屏加载成功后若 `writable && 列表非空` 自动 `enterSelectionMode()`（空夹/只读夹不硬进；切排序不重复进；首屏失败不标记，重试仍可进）。返回时管理页 `setResult(RESULT_OK)` 让收藏夹列表重拉数量。
- **修掉一个真 bug（资源类型位）**：`resources` 的 `{id}:{type}` 里 `2`=视频稿件 / `12`=音频 / `21`=视频合集，原来 `buildResources()` 一律写 `:2`，音频与合集条目删的是错 id 语义。`VideoCard` 末尾追加 `favType`（Parcelable 只能末尾追加）、`getFolderVideosNew` 用 `normalizeFavType(media.optInt("type"))` 填、`buildResources` 按卡片类型拼串、新增 `deleteFavorite(aid, favType, mediaId)` 重载（旧两参委托它）。**没有引入 `batch-del`**：项目既定写操作全部收口 `x/v3/fav/resource/batch-deal`，两者能力重叠（`bilibili-api-notes.md` §6 已记录这条判断）。
- **清空失效内容**：`FavoriteApi.cleanInvalidResources(mediaId)` → `POST x/v3/fav/resource/clean`（form 只有 `media_id`+`csrf`，`mediaId<=0` 直接 `-400`）。成功 `data` 是 `0` 不是条数，所以只提示「清理完成」**不谎报清了几条**；快照里没有 `folder/clean-state`，因此不做「可清理数量」预检。
- **视觉统一**：`activity_favorite_folder_edit.xml` 整文件照 `activity_edit_user_info.xml` 的单卡片编辑页模板重写——12sp `?android:attr/textColorSecondary` 小标题、输入框裸 `@drawable/background_edittext`、保存行是卡片行 `textEnd`+`arrow_forward`、内容管理两行是「标题 + 描述 + 箭头」单行卡片（**不加图标**：`drawable/icon_*.xml` 硬编码浅色 `#ebe0e2`，亮色主题下对比度不足）、危险操作用 `@color/status_error`。卡片一律不写 `cardBackgroundColor`，交给主题的 `materialCardViewStyle`。
- **验证**：`./gradlew.bat :app:assembleDebug` 通过（两 flavor，2m11s）；`testStandardDebugUnitTest` + `testCoexistDebugUnitTest` 通过，`FavoriteApiTest` 20 例全绿（含新增 `normalizeFavType_*` / `buildResources_*` 4 例）。
- **设备侧（SDK AVD `wear5`，`sdk_gwear_x86_64` / Android 14 / 466×466 圆屏 / 320dpi，装 `coexistDebug` universal 包）**：经 DebugBridge 拉起 `FavoriteFolderEditActivity` 核对结构与文案——默认夹（extras `mediaId=1;isDefault=true;title=DemoFolder`）顶栏「收藏夹管理」、`hintDefault` 显示「默认收藏夹仅支持管理内容，不能改名或删除」、`groupInfo`/`groupDanger` 均不在 dump 里（已隐藏）、内容管理小标题 + 「批量删除内容 / 勾选要移除的内容后删除」「清空失效内容 / 清理已失效（被删除或不可见）的收藏」两行卡片；非默认夹（`mediaId=123456;isDefault=false`）`groupInfo` 可见、标题回填 `DemoFolder`、滚动后「保存」行与两行内容管理入口都在；点「批量删除内容」成功开出 `FavoriteVideoListActivity`，顶栏显示 `DemoFolder`（`name` extra 透传成功）。**没有登录态**，故列表拉不到、多选态与两个写操作的成功提示未验。另记一条环境结论：该 AVD 上**所有页面**（含未改动的 `SettingMainActivity`）都渲染成内容放大溢出、左侧与底部被裁的样子，所以只能用来核对结构与文本，**不能做像素级版式对照**；同时 `android_find_text` 在本机不可用（无 Tesseract），`uiautomator dump` 是唯一可靠的判读手段。
- **调试踩坑（对后续设备测试有用）**：PowerShell 5.1 往原生程序传参时会吃掉内嵌双引号，`adb shell 'am broadcast ... --es extras "a=1;b=2"'` 到设备端只剩 `a=1`（分号被设备 shell 当命令分隔符）。可靠写法是 `adb --% shell "am broadcast ... --es extras 'mediaId=1;isDefault=true'"`——`--%` 停止 PowerShell 解析、内层改用**单引号**给设备 shell。
- **遗留**：① 「批量删除内容」是否**直达多选态**、「清空失效内容」的确认框与「清理完成」提示要在登录态下再验（空列表/只读夹按设计不进多选）；② 真手表/圆屏上的实际观感未看，AVD 渲染本身不可信；③ 音频/合集卡片点击仍按视频处理（`aid` 里装 auid/合集 id 走详情/虚拟合集），是改动前既有行为，本次未扩大范围。

### 3.81 长按三倍速期间不再响应横滑调进度（26.10.30，用户报告）

- **缘起**：用户报告「长按触发三倍速时，仍然能触发滑动控制进度」。长按是「按住不放」的动作，手指在加速期间几乎必然有几十像素的横向漂移；旧逻辑把这段漂移当成滑动调进度，浮层跳变、松手还会 seek 到漂移后的位置——本来只想加速看一段，进度却被拽走。
- **根因**：`PlayerActivity.handleSwipeSeekTouch()` 的判据只有「设置开 + 已准备 + 非直播 + 播放器就绪 + 未缩放」（`PlayerSwipeSeekPolicy.isSeekAllowed`），**完全没看 `onLongClick`**；而 `handleSwipeSeekTouch()` 在两套 `layout_control` 触摸分支的 ACTION_MOVE 里都会被调用，所以加速期间横滑照样激活 `swipe_seek_overlay` 并改 `seekbar_progress`。
- **修法**：
  - `app/src/main/java/com/RobinNotBad/BiliClient/activity/player/PlayerGesturePolicies.kt`：`PlayerSwipeSeekPolicy.isSeekAllowed(...)` 增加第 7 个参数 `longPressBoost: Boolean`（**不设默认值**，逼调用点显式表态），判据追加 `&& !longPressBoost`；`handleSwipeSeekTouch()` 把 `onLongClick` 作为该参数传入，被拒后走原有的 `cancelSwipeSeekIfActive()`（浮层不出现、进度不动）。
  - `PlayerActivity`：新增 `private fun endLongPressBoost()` 收口「松手还原倍速」——复位 `onLongClick`、`ijkPlayer?.setSpeed(speed_values[seekbar_speed.progress])`、`danmakuManager?.setSpeed(...)`、恢复 `text_speed` 文案；两套触摸分支的 ACTION_UP 都改调它，并**同时接 ACTION_CANCEL**。
  - **ACTION_CANCEL 必须接**：这是本次翻出来的连带缺陷。改前 `onLongClick` 只在 ACTION_UP 复位，触摸被父容器拦截或失焦 CANCEL 后标志卡在 true，倍速停在 3 倍；一旦把 `onLongClick` 做成滑动门闸，同一个 bug 还会把「横滑调进度」永久锁死（这辈子不再恢复）。
  - 文案：设置项 `settingtext_settingterminalplayeractivity_005` 改为「在视频区域左右滑动快进/快退（未缩放视频、未长按加速时生效）」。
- **验证**：`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest` 通过（两 flavor，1m18s）；`testStandardDebugUnitTest` **99 suites / 1045 tests / 0 失败 0 错误 0 跳过**，`PlayerGesturePoliciesTest` 11 例（新增 `longPressBoost_blocksSwipeSeek`、`longPressRelease_restoresSwipeSeek`）。
  - 设备侧（SDK AVD `wear5`，`sdk_gwear_x86_64` / Android 14 / 466×466，装 `coexistDebug`；`player_scale` / `player_swipe_seek` / `player_longclick` 全开，走的是 `layout_control` 缩放触摸分支）：样本是 `screenrecord` 自录的 44 秒本地 mp4，经 DebugBridge 直接拉起 `PlayerActivity`（`extras url=<本地路径>`，不需要登录）。
    - **对照**：普通横滑（DOWN → 3 次 MOVE）→ dump 出现 `swipe_seek_overlay`、无 `longclick` 日志 → 调进度正常 ✔。
    - **修复后**：DOWN 后按住 1000ms（logcat 出现 `gesture>longclick_down`，三倍速已触发）再 MOVE 两次 → dump **无** `swipe_seek_overlay`、进度文本不动 ✔。
    - **松手恢复**：加速拖到 x=330 期间进度不变，UP 后 logcat `IJKMEDIA: Playback rate: 3.000000` → `Playback rate: 1.000000`，紧接着普通横滑 → 浮层出现（`00:24 / 00:44 (55%)`）✔。
    - **ACTION_CANCEL**：按住加速后发 `input motionevent CANCEL` → 倍速 3.0 → 1.0；CANCEL 之后再普通横滑 → 浮层出现（`00:37 / 00:44 (82%)`）→ `onLongClick` 没卡 true ✔。
- **调试踩坑（对后续播放器设备测试有用）**：① `adb shell input motionevent DOWN/MOVE/UP/CANCEL x y` 能跨进程拼出一条真实触摸序列，`input swipe` 做不到「按住不动再横移」这种时序；② `screenrecord` 录静止画面只产出 3~4 秒、几 KB 的短片，播放器立刻 `FFP_MSG_COMPLETED`、`isPlaying` 不成立就没法测长按，录的时候要同时在设备上制造画面变化（这次录出 44 秒 / 15MB），录完还要 `chmod 660`（属主是 shell，app 读不到）；③ CANCEL 之后前台偶尔被 Wear 系统 UI 抢走，dump 取不到播放器控件是**假阴性**，先用 `dumpsys window | Select-String mCurrentFocus` 确认焦点在 `PlayerActivity` 再判读。
- **遗留**：① 只实测了「开启视频可缩放」的触摸分支（默认设置）；未缩放分支的 UP/CANCEL 收尾代码同构，没单独跑；② 圆屏 AVD 的命中区与真手表手感不同，系统 long press 阈值在真机上的误触率仍待真机观察；③ 长按加速期间**双指缩放/拖动**仍被吞（既有行为，本次未扩大范围）。

### 3.82 亮色模式下按钮的深色描边（26.10.30，用户报告）

- **缘起**：用户贴两张截图——亮色（经典终端 / 纸质终端）视频详情页的「播放」按钮四周有一圈显眼的深灰框，暗色同一页面则是干净的无边框色块，要求把亮色这个描边修掉。第一轮删掉描边后用户又圈出「红线两边的差异依旧明显」，即描边没了、按钮四周仍有一圈比纸底亮的「光边」。
- **根因（两层，都得拆）**：
  - **描边**：不是 MaterialButton 默认样式，是 26.10.26 给亮色单独补的：`themes.xml` 的 `ButtonStyleTerminal` 写了 `strokeColor=@color/terminal_divider` + `strokeWidth=@dimen/terminal_btn_stroke_width`，而 `terminal_btn_stroke_width` 在 `values/dimens.xml` 是 `0dp`（暗色）、被 `values-notnight/dimens.xml` 覆盖成 `1dp`（亮色），`terminal_divider` 的亮色值又是 `#26000000`（15% 黑）——所以只有亮色长出深灰框。
  - **光边**：`ButtonStyleTerminal` 没压掉父样式的 `elevation`（MDC 按钮默认带阴影 + `stateListAnimator` 按下会抬升），而按钮底是 **80% 半透明** 的 `@color/terminal_card_bg`（`#CCFDF6EC`）——阴影从半透明底下面透出来，就在亮色纸底上沿按钮轮廓留了一圈「外深环 + 内侧渐亮」的脏边。暗色看不出，是因为黑底上的阴影本身就是黑的。**同一条坑玻璃主题早就踩过**（`themes_glass.xml:4` 的注释：「卡片 / 按钮 elevation 必须为 0：半透明底下面的阴影会透出来，形成脏边」）。
- **修法**（`app/src/main/res/values/themes.xml` 的 `ButtonStyleTerminal`）：
  - 删掉 `strokeColor` / `strokeWidth` 两项，连同只为它存在的两处 dimen（`values/dimens.xml` 的 `terminal_btn_stroke_width=0dp` 与注释、整份 `values-notnight/dimens.xml`）。
  - 补 `elevation=0dp` + `android:elevation=0dp` + `android:stateListAnimator=@null`（与 `ButtonStyleTerminal.Glass` 同款写法，保留 clickable / ripple，只去掉抬升与阴影）。
  - 亮暗两版回到同一套语义：无描边、无阴影、按钮底 = 卡片色 `@color/terminal_card_bg`，靠「底色 vs 页底」的差区分——亮色 `#CCFDF6EC` 压在 `#EBDFCF` 纸底上实测 `#F9F2E6`，与暗色 `#2A2A2A` 压在 `#000` 上的层级关系一致。
- **未动的**：`@color/terminal_divider` 两个值保留（改完已无引用，删色值要连带动 `tools/appearance/gen_light_mode.py` 的亮色色表，本次不扩大范围）；玻璃质感的 `ButtonStyleTerminal.Glass` 自带 overlay 描边（那是玻璃主题的设计），不受影响；其余六套主题的按钮走 `gradient_button_bg_*` 背景、本来就没写 stroke，也不受影响；卡片上的粉色 `actionCardStroke` 描边与本条无关（`CardStyleTerminal` 的 `cardElevation` 也没动）。
- **验证**：`./gradlew.bat :app:assembleDebug` 通过（两 flavor）；`:app:testStandardDebugUnitTest` + `:app:testCoexistDebugUnitTest` 通过。
  - 设备侧（emulator-5554 / SDK AVD `24031PN0DC` / Android 12，装 `coexistDebug` universal 包，经典终端主题）：亮色视频详情页（`--es bvid BV1jBpE6zEGD --el aid 0`，不加 `--el aid 0` 会走 aid=114514 报 `video object is null`）。
  - **只删描边那一版**：深灰框消失，但沿按钮上边缘扫出来是 `(235,223,207)` 纸底 → `(239,232,222)` 光边 → `(249,242,230)` 按钮底，正是用户圈出的「红线两边」；放大 3x 能看到边缘一圈渐变带。
  - **加 elevation=0 之后**：同一条扫描变成 `(235,223,207)` → `(249,242,230)` 直接跳变，中间那圈 10~14px 的光边带消失，放大后按钮就是一个干净的圆角色块；暗色同页仍是 `#2A2A2A` 色块、无描边无阴影（黑底上本来也看不见阴影）。
  - 测完把模拟器状态还原：`coexist` 的 `shared_prefs/default.xml` 用测前备份覆盖回去（6102 字节、md5 与备份一致、属主仍是 `u0_a52`），`standard` 里临时插进去的 `ui_color_mode=light` 键删掉。
- **遗留**：无。附带记两条：① 复现亮/暗要改 `shared_prefs/default.xml` 的 `ui_color_mode`（写回用 `cat` 覆写保持属主，`sed -i` 会留下 root 属主文件）；② 以后给**半透明底**的组件加 elevation/阴影一律按本条处理——亮色下会变成可见的脏边。

### 3.83 新增「举报评论」（26.10.27，用户要求；26.10.31 迁入可选私有功能）

- **现状**：这条功能**不在公开仓库**。实现与单测已整体搬进私有仓库，公开侧只剩 `util/OptionalFeatures` 的 `replyReportLabel` / `replyReport` 门面和 `ReplyAdapter.showManageMenu` 里的一个条件挂点。
- **公开侧行为**：私有注入不在场时面板里不出现「举报评论」，别人的评论长按仍是「只剩复制文字就直接进复制页」的老行为；`ManageAction.Kind` 为此保留了通用的 `EXTRA`（不复用 `COPY`，否则会误触上面那条直连捷径）。
- **验证**：公开配置（无 `private/`）与私有配置（有 `private/`）各跑过 `assembleDebug` 双 flavor 与两 flavor 单测，均 0 失败。细节见私有仓库 README。
- **遗留**：登录态下的成功提交（`code=0`）仍未验——模拟器没有可用登录态。

### 3.84 按线上崩溃报告修 7 类缺陷（26.10.28，用户要求，mapping 还原后定位）

- **缘起**：控制台积了 50 条待处理崩溃。第一轮只按「堆栈里未混淆的帧」推断，结论不可用；用户提示「编译一遍 release 就有 mapping」后重新做：`./gradlew.bat :app:minifyStandardReleaseWithR8 :app:minifyCoexistReleaseWithR8`（本机没有 `key.jks`，只跑 R8 阶段即可）产出 `app/build/outputs/mapping/{standard,coexist}Release/mapping.txt`，逐帧还原后 **50 条全部归类**，其中 7 类能落到代码。
- **还原的可信度校验**（重要，否则等于瞎猜）：崩溃文本自带的 `adapter:d1.r@…` → mapping 里 `d1.r = MenuSettingAdapter`；`UpdateActivity.O` 的相邻帧 `R0.v` → `UpdateActivity$$ExternalSyntheticLambda0`；页面轨迹与解出的类一致。**跨版本的报告只能解出方法名、行号不可用**（mapping 来自当前 `main`），所以 26.10.19/26.10.16 的那 3 条 NPE 与 3 条 Fragment 崩溃仍未定位。
- **改法（7 处 + 1 个新类 + 1 组单测）**：
  1. `adapter/MenuSettingAdapter.kt` `dragCallback.clearView` → `recyclerView.post { ensureStartValid() }`。ItemTouchHelper 是在 RecyclerView 的 layout/scroll 过程中回调 `clearView` 的，当场 `notifyItemChanged` 必然抛 `Cannot call this method while RecyclerView is computing a layout or scrolling`（**6 例**）。
  2. `activity/settings/UpdateActivity.kt` `startDownload()`：`updateConfig!!.downloadUrl!!` → 先取 `downloadUrl` 判 `isNullOrBlank`，为空提示新文案 `msg_update_no_download_url` 直接返回。根因是强制更新拦截把 SP 里可能为 null 的 `force_update_download_url`（`BiliTerminal.kt` 的 `getString(..., null)`）塞进 intent（**5 例**，NPE 消息为 `null`，Kotlin `!!` 的指纹）。
  3. `activity/video/ShortVideoPlayerActivity.kt` 封面加载套 `GlideUtil.canLoad(coverImage)`；`adapter/user/UserListAdapter.kt` 头像加载套 `GlideUtil.canLoad(holder.itemView)`。Glide 4 的 `with(Activity)`/`with(View)` 会在宿主销毁时抛 `You cannot start a load for a destroyed activity`（**4 例**能确认到这两个调用点）。**注意 `Glide.with(BiliTerminal.context!!)` 不受影响**——那是 `createConfigurationContext(applicationContext)`，走 application manager，不做销毁校验；Opus/私信那几处就是被 R8 lambda 合并误指的。
  4. `activity/video/ShortVideoFeedFragment.kt` 三条 `preloadManager` 回调（`onItemsLoaded` / `onLoadError` / `onRelatedLoaded`）开头加 `if (!isAdded) return@label`。预加载是后台线程拉、回主线程才回调，中间用户可能已退出（**4 例** `Fragment ... not attached to an activity`）。
  5. `activity/settings/setup/SetupUIActivity.kt` 的 `save()` 改走新纯函数 `util/UiScaleInput.kt`（`parseDpi` / `parsePadding`，`toFloatOrNull` + 原范围校验）。旧代码直接 `toFloat()`，线上两次 `For input string: "界面大小"` / `"确认设置"`——都是页面自己的文案，疑似被粘贴进框（**2 例**）。
  6. `activity/player/PlayerActivity.kt` `onDestroy` 里 `requestedOrientation = …` 包 `try/catch (IllegalStateException)`。Android 8.0 在非全屏（分屏/悬浮）下调它抛 「Only fullscreen activities can request orientation」，在 `onDestroy` 里会升级成 `Unable to destroy activity` 直接崩在退出路径（**4 例**，全部 sdk 26 / HONOR HWDUK）。
  7. 新增 `ui/widget/SafeViewPager.kt` 并让 3 个布局（`activity_simple_viewpager.xml` / `activity_emote.xml` / `activity_short_video_player.xml`）改用它：只吞 message 含 `pointerIndex` 的 `IllegalArgumentException`，其余照抛。老 ViewPager 的多指下标越界是已知库缺陷（**7 例**：4 例 `pointerIndex out of range` + 3 例 `invalid pointerIndex -1 for MotionEvent`，全部停在该类的 `onInterceptTouchEvent`）。
- **验证**：
  - `assembleDebug` 双 flavor 成功；两 flavor 单测各 **95 个测试类 / 1041 例 / 0 失败**（新增 `UiScaleInputTest` 9 例，钉住 `"界面大小"` / `"确认设置"` / 空串 / null / 越界 / 保留旧口径的负边距）。
  - 设备侧（MuMu Android 15 / coexist debug）：`SettingMenuActivity` 正常渲染；`VideoInfoActivity` 左右滑能在「简介 / 评论」间切换（走的正是新的 `SafeViewPager`）；`ShortVideoPlayerActivity` 打开并来回滑 3 轮，`logcat -b crash` **为空**。
  - **没能复现验证的部分（如实记录）**：①`clearView` 的崩溃依赖「拖拽结束恰好落在 smoothScroll 期间」，adb 手势造不出来，只改了 1 行 `post`；②`PlayerActivity` 的方向异常要设备处于分屏/悬浮窗口才触发，单窗口模拟器造不出；③DPI 框的中文输入无法用 `adb input text` 复现（走 KeyEvent，被 `DigitsKeyListener` 挡掉），改由 `UiScaleInputTest` 覆盖。
- **遗留**：①仍 6 条无法定位（3 条 26.10.16 的 NPE、3 条 26.10.19 的 Fragment not attached）——需要按对应版本再编一次 release 取 mapping；②`Inconsistency detected`（4 例）与第 1 条同源但无法定位到具体 notify 点；③全仓仍有 ~58 处裸 `Glide.with(...)`，建议后续统一收敛到 `GlideUtil.request*`（已自带 `canLoad`）或 `AccountSwitchActivity` 那种「一次取 RequestManager」的写法。
- **另一条结构性建议**：`mapping.txt` 现在是构建副产物，`build/` 一清就没了，下次再有人报崩溃又回到解不出来的状态。建议在 `build-release.yml` 里把它作为 artifact 按 `versionCode` 归档。

### 3.85 搜索彩蛋第 4 组换成「紫色空灵 + 纳西妲」专属彩蛋（26.10.30，用户要求）

- **缘起**：用户要求删掉搜索页第 4 组彩蛋（「8 个特定真人姓名」那一组），换成「关键词同时包含 `紫色空灵` 和 `纳西妲`」触发的新彩蛋，文案由用户本人提供。
- **改法**：
  - `app/src/main/java/com/RobinNotBad/BiliClient/activity/search/SearchActivity.kt`：删掉 `specialNamesList` 字段与 `searchKeyword()` 里的遍历分支；在同一位置（心理援助词表之后）新增 `if (keyword.contains("紫色空灵") && keyword.contains("纳西妲"))` → `MsgUtil.showText(UiText.of("特殊彩蛋"), getString(R.string.egg_zisekongling_nahida))`。**放在暖心词之后是故意的**：心理危机词的安全兜底优先于玩梗。判定用 `contains`，所以「紫色空灵 纳西妲」中间夹别的字也能命中。
  - `app/src/main/res/values/strings.xml`：删除 `egg_special_names`，新增 `egg_zisekongling_nahida`，13 行按用户原文逐行 `\n`，一字未改（含结尾「会见~」）。无触发方式说明行（用户没写，不与 3、4 组原条目统一）。
  - 文档：`docs/copy-inventory/07-eggs.md` 的搜索彩蛋表（触发代码行号同步到 `450-474`）与 `E-050` 条目、`docs/FEATURES.md:268` 的彩蛋描述。搜索彩蛋仍是 4 组，`README.md` 的 50 条合计不变。
- **验证**：
  - `./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest` 通过（两 flavor）；两 flavor 各 **104 suites / 1112 tests / 0 失败**。
  - 设备侧（emulator-5554 / Android 12 / `coexistDebug` universal 包）：经 DebugBridge `start SearchActivity --es extras 'keyword=紫色空灵 纳西妲;auto_search=true'` → 顶层 `ShowTextActivity`，截图与用户原文逐行一致，13 行一屏内放得下。
  - **回归**：`严炜` 不再弹彩蛋、正常留在 `SearchActivity` 拉真实搜索（旧组已删）；`Robin 纳西妲` 仍弹彩蛋 2、`抑郁` 仍弹暖心词；单搜 `紫色空灵` 不触发（正常搜索）。
  - 说明：DebugBridge 的 intent 入口与真实 UI 提交走的是同一个 `searchKeyword()`（`SearchActivity.kt:329-331` 调用点），故 intent 验证对 UI 路径等效。

### 3.86 扫码登录补齐健壮性：轮询分档 / 失败退避 / 状态码收敛 / web 落地修正（26.10.30，用户要求）

- **缘起**：用户要求「扫码登录体验补齐健壮性」。先做只读审计（对照官方 App 9.16.1 的逆向结论），查出 9 处问题，本轮修其中 7 处；`NetWorkUtil` 的重试/超时与 `AccountManager.saveCurrentAccount()` 的同步网络**按决策不动**。
- **改法（只动 `activity/settings/login/QRLoginFragment.kt` 与 `res/values/strings.xml`，`api/LoginApi.java` 无需改）**：

| 问题 | 改法 | 实测结论 |
| --- | --- | --- |
| 轮询固定 1s、无次数上限（旧 `:263`） | `PollCount` 分档：第 1–30 次 2s、31–60 次 4s、61 次起 6s；`MAX_POLL_COUNT = 90`，到顶即停并提示重取码。Timer 改成「每拍只排一发」（`scheduleNextPoll()`），才能变间隔 | ✔ 2s→4s 在设备上实测到切换（见验证第 3 条）；上限 90 仅代码复核 |
| 一次网络异常就永久停轮询（旧 `:253-261`） | 新增 `onPollFailure()`：连续失败 <3 次 → 提示「网络异常，正在重试……」并以 3s 退避续轮询；第 3 次才停、恢复可点、按模式给 `err_qr_*` 文案。**理由**：手机扫码后用户可能要隔几秒才点确认，一次抖动就让人重扫的代价远大于多轮询两下 | ✔ 实测：失败第 1、2 次各 3s 退避续轮询且显示「网络异常，正在重试……」，第 3 次才停（见验证第 4 条） |
| 未知状态码不停轮询（旧 `:432` / `:349-351`） | 两个 detect 的 `else` 分支：`stopPolling()` + `need_refresh=true` + 恢复二维码可点 + 新文案 `err_qr_unknown_code(%1$d)` | ⚠ 仅代码复核（服务端造不出未知码）；已知码 `86101`/`86039`/`86090` 的分支在设备上跑通 |
| 模式开关卡片可连点，连点两下出两套 Timer（旧 `:113-120` + `:238`） | `startLoginDetect()` 入口先 `stopPolling()`（幂等）；`refreshQrCode()` 期间 `loginModeSwitch.isEnabled=false`，出码/失败回调再恢复 | ✔ 实测：三连点只发出一次 `auth_code` 请求、TV 轮询单条流 2.07–2.12s（见验证第 5 条） |
| 页面切走/销毁只 cancel 不置标志位，正在跑的那一拍会把轮询续回来 | 新增 `pollStopped` 标志 + `stopPolling()`（`pollStopped=true` → `timer.cancel()` → `timer=null`）；`onDestroy` 与三个跳转卡片都不再裸调 `timer?.cancel()` | ✔ 代码复核：文件内只剩 `scheduleNextPoll()` 与 `stopPolling()` 两处 cancel |
| web 成功落地：`.toLong()` 可能崩、`access_key` 不清、`requestSSOs` 抛异常会把成功报成失败 | 新增 `handleWebLoginSuccess()`：cookie 改取 `NetWorkUtil.getCachedCookies()`；`DedeUserID` 用 `toLongOrNull()`，取不到给 `err_qr_no_mid` 而不是崩；`refresh_token` 用 `optString`；**显式 `putString(access_key, "")` 清残留 TV token**（否则 `SplashActivity.kt:396-408` 会走 APP token 刷新分支，web 会话拿不到 cookie 续期）；`requestSSOs()` 包 try 且移到导航之后 | ✔ 端到端实测通过（见验证第 2 条） |
| `refreshQrCode()` 在 IO 线程改 View（旧 `:180-181`） | 换图/置灰/清标志全部挪到 UI 线程，`CenterThreadPool.run{}` 里只留网络 | ✔ 代码复核 |
| 文案：重试/未知码/凭证不全都只有一句模糊提示 | 新增 `text_qr_retrying`、`err_qr_unknown_code`、`err_qr_no_mid`；`err_qr_no_info` 等处的 `%1$s` 兜空串，不再显示 "null" | ✔ 见截图 |

- **验证**：
  - `./gradlew.bat :app:assembleStandardDebug :app:testStandardDebugUnitTest` 通过，**104 suites / 1112 tests / 0 failures / 0 errors**（日志 `outputs/build_login2.log`；工作区同期的私有页测试也在其中，本轮改动前后都是 0 失败）；早前一轮（不含维护者新增测试）为 104 suites / 1076 tests，日志 `outputs/build_login.log`。另编 `assembleCoexistDebug` 供设备实测（代码同源）。
  - 设备侧（emulator-5554 / Wear OS 14 / `coexistDebug` universal 包）：web 模式出码正常（`outputs/smoke/qr_login_web_3.png`）；轮询 130s 窗口抓 38 拍（`outputs/smoke/poll_cadence_130s.log`）——前 27 拍间隔 2.07–2.27s，第 28 拍起 4.07–4.34s，与「第 30 次进 4s 档」的设计一致（抓包前已轮询约 9s，绝对计数≈30）。
  - 该窗口内二维码被外部扫走（`86090` → 2 秒后 `code:0`），**意外把 web 成功链路端到端跑通了**：`saveCookiesLocked` 落地 `SESSDATA/bili_jct/DedeUserID/DedeUserID__ckMd5/sid`，随后 `x/web-interface/card`、`space/notice`、`wbi/acc/info`，最后 GET `data.url`（`passport.biligame.com/.../crossDomain`）；落盘核对 `access_key` 为空串、`cookies` 466B、`csrf` 32B、`refresh_token` 32B、`mid` 已写、`setup=true`、`account_list` 已写；**无崩溃、无 ANR**。
  - **P2 断网实测（三个窗口）**：①连续失败到停——15:21:58.905 起连续 3 次失败（15:21:58.9 / 15:22:02.3 / 15:22:05.7，间隔 3.4s＝3s 退避＋约 0.45s 请求），第 3 次后 `MsgUtil` 打「网络错误(＃°Д°)」、文案回 `err_qr_no_info`，此后 40s 内没有任何 poll（`outputs/smoke/p2_retry_3.png`、`p2_stage3.log`）；②重试文案——离线 2.2s 时截图拍到「网络异常，正在重试……」（`outputs/smoke/p2_retrying_live.png`）；③短暂抖动自愈——失败 2 次后网络恢复，15:24:16.670 轮询成功回到 `86101` 并按 2s 档继续（`outputs/smoke/p2_transient_recovery.log`、`p2_recovered_transient.png`）。**首拍失败很快**：DNS 直接 `UnknownHostException`，`NetWorkUtil` 的内部 5 次重试每次约 0.1s，所以 3 次上限约 7s 就会触发。
  - **P4 连点实测**：在模式卡上 `input tap` 连点 3 下（间隔约 50–100ms）→ 只发出一次 `POST .../passport-tv-login/qrcode/auth_code`（15:24:41.990），没有第二次取码、也没有回切 web 分支；随后 TV 轮询单条流：42.589 / 44.655 / 46.731 / 48.848 / 50.923 / 52.984 / 55.044，间隔 2.07–2.12s、每拍只有一条 `tv_login_state`，说明只有一套 Timer（`outputs/smoke/p4_doubletap.log`、`p4_doubletap_2s.png`、`p4_doubletap_12s.png` 显示「（TV端模式）」）。
  - 环境事实（不是缺陷）：App 退到后台约 100s 后被系统 `ActivityManager: freezing <pid>` 冻结，轮询随之暂停——这是系统对 cached 进程的常规策略，不是 P2 退避在停轮询；回到前台后是否续跑未单独验证。
  - 另一个观察（**已修**）：停轮询后 `err_qr_no_info` 会把异常原文拼进 UI，截图里是「…取二维码信息，点击上…\nUnable to resolve host …」——`%1$s` 拿到的是 `e.message`。现在 `onPollFailure()` 达到上限后不再把 `e.message` 拼进 `scanStat`：web 侧 `IOException` → `err_qr_web_network`、其余 → `err_qr_web_api`（都是不带 `%1$s` 的专用文案），异常详情仍走 `MsgUtil.err` 的日志；`err_qr_no_info` 只留给取码阶段。
- **遗留**：①P2 的 3 次上限在「快速失败」网络下约 7s 就停，10 秒级的电梯/地铁断网仍要用户手点重试（这是拍板口径的必然结果，想更宽容只需调大 `MAX_POLL_FAIL`）；②未知状态码分支服务端造不出来，只有代码复核；③TV 模式只跑了轮询（`86039` 正常），**TV 成功落地（access_token/refresh_token 写盘）未实测**；④90 次上限（≈180s＋C 端耗时）没有跑满，按代码复核；⑤~~`err_qr_no_info` 会外露英文异常原文~~ 已在 26.10.31 补丁里改掉（web 侧换 `err_qr_web_network` / `err_qr_web_api` 两条专用文案，见上）；⑥`refreshQrCode()` 里 `CookiesApi.checkCookies()` 仍可能耗时数秒，取码阶段最坏仍会卡在「正在获取二维码」（`NetWorkUtil` 重试策略未动，属已知边界）。

### 3.87 账密 / 短信登录补存 App access_token（26.10.31，发版前补）

- **缘起 / 根因**：`activity/settings/login/PasswordLoginFragment.kt` 的 `handleLoginSuccess` 与 `SMSLoginFragment.kt` 的同类收尾，从登录响应 `data/token_info` 里只取了 `refresh_token` 写盘，把同一响应中的 `access_token` 丢掉了 → 走账密 / 短信登录后 `SharedPreferencesUtil.access_key` 一直是空串。而 `activity/SplashActivity.kt:396` 按「有 access_key = TV/APP 登录」分流去 `api/AppTokenRefreshApi.refreshAppToken()`（该接口同时续 access_token 与 cookies），为空就只能走 `checkCookieRefresh()`；`NetWorkUtil` 的 `autoAddAccessKey`、`ShortVideoFeedApi` 等依赖 App 令牌的接口也一直拿不到凭证。
- **改法（两处，各自的 `handleLoginSuccess` 内）**：写 `refresh_token` 之后补写 `access_key`——先取 `data.token_info.access_token`，为空再回落 `data.access_token`。取值口径与紧邻的 `refresh_token` 一致，**无条件写**：响应里真没有 access_token 时写入空串 = 显式清掉上一次登录（可能是另一个账号、另一套 appkey）残留的令牌，避免它和本次的 mid / cookies 错配；空 access_key 是个明确定义的状态（`SplashActivity.kt:396` 会走 web cookie 刷新分支，与 §3.86 的 web 登录清空口径一致）。cookie 刷新路径不动。
- **为什么不是兼容性风险**：账密 / 短信登录本身就是 App 接口，返回的 access_token 与 refresh_token 同源；`AccountManager.saveCurrentAccount()` 在这两行之后执行，所以多账号快照也会带上 access_key，切号不丢凭证。
- **验证**：`./gradlew.bat :app:assembleDebug` 通过（含私有功能源码注入）；`testStandardDebugUnitTest` + `testCoexistDebugUnitTest` 各 **1112 例 / 0 失败 / 0 错误**（12:25 实测）。**登录端到端未实测**（需真账号）：待验「账密 / 短信登录 → 重启后按 access_key 分流到 `AppTokenRefreshApi`、access_token 与 cookies 都能续期」。
- **台账口径**：本条只记公共侧改动；配套的私有功能改动按规矩只记在私有仓库，不写本文件。

### 3.88 倍速播放出现两路重叠音频、一路跟不上（26.10.31，用户报告）

- **现象 / 缘起**：用户在 26.10.31 正式版上反馈「倍速播放时音频流会变成两个（重叠），还有一个跟不上」；1.0x 正常。26.10.30 的 §4 五十九引入了 DASH 双流 + 外部音轨，倍速正是那时被顺手「同步」到音轨的。
- **根因（模拟器实测钉死，不是猜的）**：DASH 播放是「ijk 播纯视频流 + `android.media.MediaPlayer` 播纯音频流」。`IjkMediaPlayer.setSpeed(2.0)` 在**没有音轨的纯视频流**上实际不生效——实测 10.6s 墙钟视频位置只前进 10.6s（恒 1.0000x），而 `MediaPlayer.setPlaybackParams(2.0)` 生效 → 音频一路领先视频，`progressChange()` 的「800ms 基线偏差校正」每 ~1.3 秒就把音轨 seek 回去一次（在线 2x 实测 69.4s 内 **47 次**、每次回拉 700~1000ms）。用户听到的「两个重叠 + 一个跟不上」就是同一段音频被反复回拉/跳过。ijk 的变速靠音轨时钟 + `soundtouch` 驱动，纯视频流没有可缩放的音频时钟，**stock ijkplayer 在这类片源上倍速必然无效**（26.10.31 终版用内核补丁修掉，见下）。
- **中间版本（26.10.31 早间，已撤）**：先绕成「倍速时换自带音轨的合一 MP4」——新增 `player/PlayerSpeedStreamPolicy.kt`（7 例单测）+ `PlayerActivity.syncStreamForSpeed()`（挂在 `applySpeedFromSeekbar()` 末尾与滑条 `onStopTrackingTouch()`，`streamSyncPending` 防重入），`applyAudioSpeed()` 对外挂音轨恒设 1x，`applyQuality` 多一个 `fromSpeedSync` 参数，`switchQuality` 放开高清拦截。**放弃原因**：登录态 + 大会员实测 `fnval=1&qn=80` 仍回 `quality:64`（`日志 quality>合一 MP4 档位回填：80 -> 64`），720P 是合一 MP4 的硬天花板（`docs/bilibili-api-notes.md` §3.12），等于强制降清晰度；本地老双文件也仍不能变速。
- **终版改法：改 ijk 内核，让无音轨的纯视频流也真倍速**（用户指出「这个 so 库应该是有 GitHub 源码的」后转向）：
  - `ijkpatch/0001-ffplay-video-playback-rate.patch`：`compute_target_delay()` 里只在「无音轨 + 倍速 != 1」分支把帧间隔除以 `pf_playback_rate`（PTS/时钟不动，所以进度条、弹幕、`getCurrentPosition()`、seek 全部自动正确）；`ijkpatch/0002-ffmpeg-hevc-termios-b0.patch`：arm 上 NDK `<termios.h>` 把波特率常量定义成 `B0`，与 HEVC `TAB_MVF_PU(B0)` 撞名（`x ## v` 粘成 `x0000000`）导致 ffmpeg 编不过，`#undef B0` 即可。构建要点（NDK **r14b** 才有 `make-standalone-toolchain.sh`、`module-lite-hevc` + flac/vp8/vp9、`--disable-ffserver`/`--disable-vda` 已移除、`--disable-bsf=eac3_core`、改了 module 要 `make distclean` 否则 `reuse configure`、Windows 克隆的 CRLF 与符号链接退化）全记在 `ijkpatch/README.md`。
  - 三个 ABI（arm64-v8a / armeabi-v7a / x86）的 `libijk{ffmpeg,player,sdl}.so` 用补丁版重编并替换 `app/libs/`，随包发布。
  - 应用侧退回「倍速就是真倍速」：`applyAudioSpeed()` 恢复跟随倍速（`PlaybackParams().setSpeed(speed)`）；删除 `PlayerSpeedStreamPolicy` 及其单测、`syncStreamForSpeed()`、`applyQuality` 的 `fromSpeedSync`、`userQuality`/`streamSyncPending`、相关三条文案；保留 `PlayerApi.applyActualQuality()`（用响应 `data.quality` 回填 `playerData.qn`）；长按 3x 改为 `longPressBoostActive` + `effectiveSpeedValue()` + `boostLabel()`，与滑条走同一条下发链路（不再直接 `setSpeed(3f)`）。
  - `progressChange()` 重同步分支补 `audioResyncSuppressMs = nowElapsed + AUDIO_RESYNC_COOLDOWN_MS`：`MediaPlayer.seekTo` 是异步的，seek 未落地时 `currentPosition` 仍报旧值，会连着 seek 三次（1x 实测 3 连 seek，听感是音频连跳三下）。
- **验证**：① `:app:assembleDebug` + `:app:testStandardDebugUnitTest` + `:app:testCoexistDebugUnitTest` 全绿，两个 flavor 均 **104 suites / 1112 tests / 0 失败**；② 模拟器 `emulator-5554`（AVD `wear5` / Android 14 x86_64，装载补丁版 `x86_64` 库）：本地 DASH 双文件夹具 @2x → `本地进度` 每 15.1s 前进 30.3s = **2.00x**、`音频轨道重新同步` 仅起播对齐 1 次、`dumpsys media.audio_flinger` 同刻只有 1 条活跃 AudioTrack；@1x → resync 0 次、1.0x；在线 DASH 纯视频流（HTTP 夹具 @2x）→ 进度上报 15s→30s→45s 各 ≈7.5s = **2x**；合一 MP4 @2x 与补丁前一致 ≈2x（音频主时钟路径未受影响）。
- **遗留 / 验证边界**：① **arm 真机（手表）未实测**——模拟器只能载入测试专用、未提交的 `x86_64` 库；② 模拟器上「在线 DASH 双流 + 外挂音轨」没跑通（宿主 HTTP 夹具只能经 `adb reverse` 给 ijk 用，媒体服务进程连不上；`10.0.2.2` 被宿主防火墙挡住），该组合用本地双文件夹具等价验证；③ `fnval=1` 的合一 MP4 绝不能挂外部音轨（§7.4 B1）；④ DASH 流地址 ~120 分钟过期仍未处理（§7.35）。
- 文档同步：`docs/architecture-map.md` §7.57「倍速」几条已改写为终版口径（内核补丁 + 应用侧回退 + 冷却期），`docs/bilibili-api-notes.md` §3.12 补充了「登录态实测仍 `quality:64`」，构建与实测细节在 `ijkpatch/README.md`。

### 3.89 整体转向兄弟仓库的播放架构：普通档回合一 MP4、高档 DASH 统一时钟、`.so` 回退上游（26.10.31 末，用户要求）

- **缘起**：用户报告 1080P 适配后「音画不同步 / 音频轨不加载」，随后追加「听视频模式报 `-10000`」。第一版把在线 DASH 全部改成 ExoPlayer 统一时钟（见 §3.88 之后那一版 `e65cab7`），用户装包后反馈「**包体积增大很多且完全播放不了视频**」，并要求「完全参照 BiliTerminal-Revival 的方案，连 so 库也换成他的，弃用我们目前的方案」。
- **「完全播放不了」的根因**：不是 ExoPlayer 接入本身，而是 `app/libs/` 里那份**自编译的补丁版 `libijk*.so`**（§3.88 为了纯视频流真倍速打的内核补丁）。量化对比：补丁版三 ABI 合计 **23.04 MB**，上游原版 **17.48 MB**（`libijkffmpeg` arm64 7.22→5.29 MB、v7a 5.79→4.40 MB、x86 8.14→6.22 MB，`libijkplayer`/`libijksdl` 同样膨胀）。处理：`git checkout 686f937^ -- app/libs` 回退到上游预编译库（arm64 保留本项目自己的，三方库 v7a/x86 与兄弟仓库**字节数逐一相同**，即同一份上游库）。回退后高档位纯视频流改由 Exo 播，不再需要内核倍速补丁，`ijkpatch/` 与 `rebuild_all.sh` 保留但当前不参与构建。
- **新架构（照搬兄弟仓库）**：
  - `util/DeviceProfile.kt` + `util/DeviceProfilePolicy.kt`：`Tier{COMPAT, BALANCED, HIGH_END}` 按「手表形态 / SDK / memoryClass / 核数」判定，`DEVICE_PROFILE_OVERRIDE` 可覆盖；`maxQuality()` 1080P / 4K、`autoQuality()` 720P / 1080P；`useUnifiedDashPlayer()` **默认全设备 true**（用户拍板：手表也保留 Exo，不抄兄弟仓库「COMPAT 档禁用 Exo」那刀），`useDashDualPlayer()` 恒 false。`BiliTerminal.onCreate` 里 `DeviceProfile.init(this)`。
  - `player/PlaybackStrategyPolicy.kt`：`usesProgressivePlayback(qn)` 只对 qn∈{6,16,32,64} 为 true；`chooseSource(dashQn, progressiveQn, unifiedPlayerAvailable)` 在合一档位 ≥ DASH 档位时选 MUXED，被降档时 unified 可用就保留 DASH（**不把 1080P 静默降成 720P**）、不可用才退 MUXED（否则纯 DASH 无音轨 = 有画面没声音）；`fallbackQualities(qn)` 给出逐档降级序列。
  - `api/PlayerApi.java`：`getPlaybackVideo` / `getPlaybackBangumi` 改「先 DASH（fnval=16，拿完整档位清单 + 真实 `quality` + 音轨）→ 真实档位属于普通档时再补取合一 MP4 → `PlaybackStrategyPolicy` 决定用哪个」，`switchToProgressiveIfBetter()` 用 `DashSnapshot` 做快照回滚，**MUXED 时清 `dashData`/`audioUrl` 但用 `restoreQualityLists()` 保留 DASH 的清晰度清单**（合一接口按请求档位裁剪 `accept`，不还原面板会丢 1080P 档位）；`applyDashStreams` 回填服务端真实 `qn`；新增 `getProgressiveFallback(source, qn)` 供高档起播失败时同档降级。实测依据：真机账号下 `fnval=0`/`fnval=1` 一律 `quality=64`、`accept=[64,16]`，只有 `fnval=16` 才回 `quality=80`、`accept=[112,80,64,32,16]`——**合一 MP4 天花板就是 720P**。
  - `PlayerActivity.kt`：**删掉整个 DASH 双播放器路径**——`initRemoteAudioTrack()`、`retryRemoteAudioTrack()`、`onHighQualityAudioFailed()`、`dashAudioRetryCount`、`dashAudioFallbackInFlight`、`audioBackupUrls` 及两个重试常量全部移除；`initAudioTrack()` 遇到 `UnifiedDashPlayer` 直接返回（在线音轨由内核内部消费），只有**本地双文件**还保留外挂 `MediaPlayer` 与 250ms 漂移校正。高档起播失败改走 `onUnifiedPlayerFailed()` → `applyQuality(720, false)` 换合一 MP4；`applyQuality` 的档位请求用 `minOf(AccountStateStore.clampQuality(newQuality), DeviceProfile.maxQuality())` 夹紧。保留上一版新增的「起播健康检查 + 按版本自动回退 IJK」（`player_unified_blocked_version`）。
  - `app/build.gradle`：`exoplayer-core` 回到 **2.18.7**（与兄弟仓库一致；实测 2.19.1 对「起播 1 秒后永久 BUFFERING」无改善）。
- **验证**：`.\gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest` **BUILD SUCCESSFUL**；**109 suites / 1166 tests / 0 failures / 0 errors**（新增 `DeviceProfilePolicyTest` 17、`PlayerCompatibilityUtilTest` 7、`PlaybackStrategyPolicyTest` 11、`StreamUrlPolicyTest` 12、`DashDataCompatibleAudioTest` 7 全绿）。包体积：`app-standard-arm64-v8a-debug.apk` **19.71 MB**、`armeabi-v7a` 19.31 MB、`x86` 16.23 MB（对比补丁版 .so 时期的三 ABI universal 包 23.5 MB，`.so` 回退单项就省 ~5.5 MB 未压缩）。
- **遗留 / 验证边界**：① **真机最终验收由用户执行**（用户明确「真机验证我来」），模拟器侧只做到「装包可启动、进详情页无崩溃」；② MuMu 模拟器（127.0.0.1:7555，Android 12 x86_64）上 ExoPlayer 统一内核**必现**「READY 播约 1.1–1.4 秒后掉回 BUFFERING 永久卡死」，已排除 seek / 音频轨合并 / App 主动 pause / `changeVideoSize` / 同步异步 codec 适配器 / Exo 版本（2.18.7 与 2.19.1 表现相同），同机官方 B 站客户端正常、而我们**只听音频**时完全正常——所以这是该模拟器视频解码/Surface 链路与 Exo 的组合问题，`scheduleUnifiedHealthCheck()` 的自动回退即为它兜底；③ 回退后的 IJK 路径在该模拟器上也已退化（位置停在 seek 落点、日志出现 `FFP_MSG_COMPLETED`），无法在模拟器上完成端到端播放验收。
- 文档同步：`docs/architecture-map.md` §7.4 整节按新架构重写（总原则、分档、取流决策、高档内核、听视频、接入点、自动回退、取流配套、`.so` 回退），`docs/FEATURES.md` §3.6 清晰度与音画同步两条改口径。

### 3.90 播放器相关代码整体退回 26.10.27 发版状态（26.10.31 末，用户要求）

- **用户决定**：「把播放器全部的相关代码退回到 10.27 tag 发版时候的状态，其他的不变」，改完推送到 `main`。
- **退回范围**（现在这些文件/目录等同于 `git show 26.10.27:...`）：`activity/player/**`、`player/**`（保留项见下）、`api/PlayerApi.java`、`model/PlayerData.java`、`model/DashData.java`、`app/libs` 三 ABI 的 `libijk*.so`；新增于 10.27 之后的播放器实现整批删除（`player/ExoDashMediaPlayer.java`、`UnifiedDashPlayer.java`、`PlaybackStrategyPolicy.kt`、`StreamUrlPolicy.kt`、`util/DeviceProfile*.kt`、`util/PlayerCompatibilityUtil.kt` 及其单测），`ijkpatch/` 整目录删除，`app/build.gradle` 去掉 `exoplayer-core`，`BiliTerminal.kt` 去掉 `DeviceProfile.init(this)`。
- **保留项（不属于播放路径，别的功能依赖）**：`player/PlayerStreamPolicy.kt`、`player/QualityAccessPolicy.kt` 及两个单测——`util/AccountStateStore.kt`、`activity/settings/SettingQualityActivity.kt`、`adapter/QualitySelectorAdapter.kt` 仍用它做**清晰度面板的账号权限锁定**。`activity/video/JumpToPlayerActivity.kt` 的取流调用改回 10.27 的 `PlayerApi.getBangumi(playerData)` / `getVideo(playerData, download != 0)`，但保留 `AccountStateStore.clampQuality()` 的档位夹紧。
- **结果**：在线 1080P 及以上的播放器内切档、DASH 分离流、统一时钟内核、设备分档、CDN 备份地址、兼容音轨优选**全部不再存在**；播放形态回到「合一 MP4 单播放器 + 本地双文件外挂音轨」。
- **验证**：`:app:assembleDebug`（standard + coexist 两个 flavor）**BUILD SUCCESSFUL**；`:app:testStandardDebugUnitTest` **104 suites / 1110 tests / 0 失败 / 0 错误**。真机播放行为按 26.10.27 发版口径复验（用户自测）。
- 文档同步：`docs/architecture-map.md` §7.4 该节改写为「播放器已整体退回 10.27」的现状口径，`docs/FEATURES.md` §3.6 恢复 10.27 的清晰度/音画描述并删掉 1080P 播放与统一时钟两条。

### 3.91 菜单底部新增「退出应用」按钮（26.10.31）

- **缘起**：用户要求菜单里有「退出」按钮，按下后完全关闭应用。菜单一直是纯导航页，手表上没有任何彻底收摊的入口；`desc_back_disable` 的文案从老项目起就写着"菜单最下方的退出按钮"，这次把它补回来。
- **改法**：`MenuActivity` 在 btnList 循环后追加一枚固定的 MaterialButton（不进 `btnNames`——菜单项受「菜单设置」管控且点了要跳页，退出是常驻入口）。点击先 `finishAffinity()` 收掉任务栈，再 `android.os.Process.killProcess(Process.myPid())` 结束进程：线程池 / Glide / 播放器引擎没有统一停机口，光收 Activity 会留下空转的后台进程。写法与 `UpdateActivity.exitApp()` 一致，文案复用既有 `btn_exit_app`（"退出应用"）。
- **验证**：`assembleDebug` + 双 flavor 单测通过。**模拟器不可达**：本机此刻 MuMu 与常见 adb 端口（7555/16384/5555/21503/62001）connect 均被拒绝、`adb devices` 为空，按小改动口径（单文件 UI 逻辑、不碰播放器状态机与线程时序）以构建 + 单测 + 代码走查为准。
- **26.10.31 晚追加（用户要求）**：按钮文案由「退出应用」改为「退出」二字——新增专用字符串 `btn_exit`，`UpdateActivity` 强更弹窗的「退出应用」（`btn_exit_app`）不受影响。

### 3.92 媒体控件误标「下载」修复：会话挂 MediaStyle 通知（26.10.31）

- **缘起**：用户反馈开启「系统媒体控件」后，控件被识别成「下载」而非音乐播放。
- **根因**：播放页的 `MediaSession` 从未绑定过任何通知。系统给媒体控件归类时找不到会话专属的媒体通知，就按应用当时挂着的那条通知取渠道——正在下载的机器上挂的是「下载」渠道（ID 1027），控件于是被标成「下载」。
- **改法**（`PlayerActivity`）：
  - 新增独立渠道 `channel_media_control`（显示名「音乐播放」，IMPORTANCE_LOW，O+ 建、幂等），挂一条带 `Notification.MediaStyle` + 会话令牌的常驻通知（ID **1031**，1027/1028/1029/1030 已占用），分类再叠 `CATEGORY_TRANSPORT` 双保险。系统拿到明确的配对对象后按会话归类；Android 11+ 的媒体卡片本来也要求这条通知才显示会话。
  - 通知只在元数据变化时重发（onPrepared / 切 P / 换清晰度 / 出错重建都会走 `updateMediaSessionMetadata`）；播放中每秒一次的 `updateMediaSessionPlaybackState` 不碰通知，避免 Binder 风暴。进度由 PlaybackState 提供，通知上不放进度条。标题取 `videoTitle`（空回落"RE：哔哩终端正在播放"），小图标 `R.mipmap.icon`，content intent 复用 `PlaybackService` 的 REORDER_TO_FRONT 写法。
  - `onDestroy` 先撤通知再释放会话，不留点不动的僵尸卡片。整体走 LOLLIPOP 守卫，minSdk 19 安全。
- **验证**：`assembleDebug` + 双 flavor 单测通过；模拟器不可达（同 §3.91），会话↔通知配对效果需要真表确认，已在 §2.1 记为待办。

### 3.93 实验性「圆屏列表」效果（26.10.31）

- **缘起**：用户要求实验性设置里新增「圆屏列表」开关，开启后若处于圆屏模式，所有图形界面的滚动列表加圆屏滚动列表效果。
- **改法**：
  - 设置接线：`SettingsKeys.UI_ROUND_LIST_EFFECT`（`ui_round_list_effect`，默认关）→ `buildLabGroup()`「功能开关」分组的 switch → `SettingsIndex` 内联搜索条目（描述"圆屏模式的滚动列表曲率效果"）。生效条件是**本开关 + 圆屏模式**（`player_ui_round`）同时开启；在 `BaseActivity.onCreate` 读取，改后重进页面生效。
  - 新文件 `ui/appearance/RoundListEffect.kt`：全树扫描竖向滚动容器（`RecyclerView` / `ListView` / `ScrollView` / `NestedScrollView`；横向 LayoutManager、`ViewPager2` 及其内部 RV、不可竖滚的 LayoutManager 全部排除），把每个可见条目按其在屏幕上的纵向位置做**等比缩放 + 边缘淡出**——缩放取圆的弦长比 `chord=√(1−t²)`、下限 0.62（中心 1.0 → 屏缘 0.62），淡出取 `t³` 曲线（只在外四分之一段明显）。半径取短边一半；高度超过屏幕半径的条目（文章、大图这类整页内容）不参与。
  - 性能红线（写进了类注释）：只写 `scaleX/scaleY/alpha` 变换属性（setter 内部有等值短路），不 requestLayout、不加层；滚动回调里只做数学变换、全程零分配（坐标数组与扫描栈按窗口复用）；全树扫描只挂在 `OnGlobalLayoutListener`；开关关闭时 `install` 直接 return，一个监听都不挂。容器内不下钻（卡片里的横向封面轮播不被外层效果带动）。
- **验证**：`assembleDebug` + 双 flavor 单测通过；模拟器不可达（同 §3.91），视觉效果与圆屏真机手感待真表确认，已在 §2.1 记为待办。
- **26.10.31 晚追加（用户要求「明显一点、适配绝大多数界面」）**：① 曲线加强——缩放下限 0.62→**0.50**（边缘条目缩到一半）、淡出系数 0.65→**0.85**（边缘条目透明度降到 0.15，t³ 曲线保持中央四分之一段几乎不动）；② 放开 ViewPager2 页内竖向列表（搜索结果、教程翻页原先被整页跳过——翻页器内部 RV 是横向的，本来就被"不可竖滚"挡住，竖向页内列表的窗口坐标算法通用，删掉双保险判断即可）；③ 大行不再按"高于屏幕半径"整段跳过，改按"接近整屏高（≥窗口高）"跳过——466px 手表半径才 233px，最常见的视频卡（高 250~320px）原先全被跳过，效果等于没开。性能红线不变（仍是纯变换属性、零分配）。

### 3.94 性能审查④：绑定热路径微优化（26.10.31）

- **缘起**：用户反馈应用非常耗电，要求在不改变功能的前提下尽力优化。§3.75~§3.78 已处理表情并发、图片生命周期、请求宽度封顶，本轮把剩余静态审计结论收尾。
- **审计结论**（全量代码走查）：无空闲 / 后台耗电循环——播放循环已限流且受位置门控，`PlayerActivity` 的"实时人数"轮询（5s）默认关闭且离开页面即取消，`PlaybackService` 通知定时器仅后台播放时存在；短视频页的延迟渲染是一次性调度不是循环；表情预取有 10s 截止与 `clear` 配对，无泄漏。
- **改法**：`VideoCardHolder.getRequestOptions()` 此前每次绑定都 `new RequestOptions().transform(CenterCrop(), RoundedCorners(dp2px(5f)))`——快速滑动时每个条目分配一次，Glide 内部还会再拷贝一份。改为 companion 里 `by lazy` 缓存一份进程级单例：圆角换算用的 `ToolsUtil.dp2px` 读应用级 density（「设置Density」只改各 Activity 自己的 Resources），进程存活期内不变，缓存安全。行为完全不变（同样 CenterCrop + 5dp 圆角）。
- **验证**：`assembleDebug` + 双 flavor 单测通过（含此前所有单测 0 失败）。耗电的最终定量对比需要真表长时间观察，本次以消除已知浪费点为目标。

### 3.95 「虚拟合集」拆成两个独立功能：组合集 / 收藏夹点击即播（26.11.01，用户要求）

- **缘起（用户报告）**：点收藏夹里的视频卡片会直接进入播放界面，被当成 bug。排查确认不是回归：收藏夹页 `bindAdapter` 用 `adapter.onItemClickListener` 接管了卡片点击，而默认行为本来是 `VideoCardHolder.bindClick` → 视频详情页；且「组合集」与「点卡片直接播」被绑在同一个开关上（默认开），所以只要开着虚拟合集，点卡片的默认行为就永远被覆盖，想进详情页只剩长按菜单。
- **改法**：拆成两个功能，各自一个开关（设置 → 高级与实验 → 功能开关）：
  - 「虚拟合集」（`virtual_collection_enable`，默认**开**）：只负责把同一文件夹/收藏夹的视频组成合集（选集切换、自动联播），行为不变。
  - 「收藏夹视频点击即播」（新增 `virtual_collection_click_play`，默认**关**）：开启后收藏夹里点卡片直接起播；关闭时点卡片进视频详情页（回到老行为）。
  - 判定收进 `util/VirtualCollectionPrefs.kt`（默认值 + `favoriteClickAction` / `cacheClickAction` 两个纯函数），**开关在点击时读**——顺带修掉旧实现「绑定适配器时读一次、本页开着改开关不生效」。
  - 功能一开、功能二关时，长按菜单（自己的和别人的收藏夹都）补一项「以虚拟合集连播」；功能二开时点击本身就是连播，不重复给。
  - 本地缓存文件夹**不受功能二控制**（本地视频没有「视频详情页」这个主流程）：点了就播，功能一只决定单条还是整夹。
- **验证**：`assembleDebug` 通过；`testStandardDebugUnitTest` / `testCoexistDebugUnitTest` 各 **107 suites / 1126 tests / 0 失败 0 错误 0 跳过**（含 `private/` 注入配置；新增 `VirtualCollectionPrefsTest` 10 例）。
- **遗留 / 真机待验**：① 音频、视频合集卡片（`favType != 2`）点击仍按视频处理，是改动前既有行为，本次未扩大范围；② 真机交互待验：默认（功能一开/功能二关）点卡片应进详情页、长按应多出「以虚拟合集连播」、打开功能二后点卡片应整夹连播；缓存文件夹点视频应直接播且功能一关时只播单条。

### 3.96 控制台清账后的回归修复：弹幕可见性 / 缓存切 P 音轨 / 搜索框 / 选区高亮 + 4 类崩溃兜底（26.11.01）

- **缘起**：用户要求「看看还有什么反馈没做的」。按控制台未处理反馈 16 条 + 未处理崩溃 82 条逐条核对源码与历史记录，发现 **26.10.31 的「播放器整体退回 26.10.27」（§3.90）把 26.10.28 之后落在 `player/**` 与 `activity/player/**` 的修复一并回退了**，老反馈因此复发：26.11.01 新报的 #78 就是 26.10.28 修过的 #55。
- **#78/#55 短视频关掉弹幕仍会出弹幕**（26.10.28 只修好了一半）：`ShortVideoPlayerActivity` 那半（Activity 级开关、`PLAYER_DEFAULT_DANMAKU` 开播）还在，但 `player/DanmakuManager.kt` 的 `prepared()` 补 `hide()` 那半被 §3.90 回退掉了。`show()`/`hide()` 都有 `isPrepared` 守卫、只把意图写进 `state`，而短视频每条视频都新建 `DanmakuManager`、长视频切 P 也重建，prepare 之前那次 `hide()` 于是被丢弃，新 manager 按默认可见起播。修法：`prepared()` 里 `isPrepared = true` 之后按 `state.isVisible` 补一次 `danmakuView.hide()`。
- **#51 缓存双文件切 P 丢外挂音轨**：`LocalListActivity.playVirtualCollection` 的起播音轨（26.10.28 修的）还在，但 `PlayerActivity.switchToLocalPage` / `doSwitchPage` 又被回退成「只读 videoFileList、切 P 时 audioTrackUrl 置 null」。修法：`switchToLocalPage` 读 `audioFileList`，`doSwitchPage` 增加第 4 个参数 `newAudioTrackUrl`，`audioTrackUrl` 用本页音轨（在线切 P 传空串）。
- **#42 空关键词切分类后搜索框消失**：`SearchActivity` 的 `ViewPager2.onPageScrolled` 里有一句 `onScrolled(256)`——那是 26.10.26「按用户要求逐字回退」时留下的模拟滚动，方向正好是「收起搜索框」；空关键词时分类页没有内容可滚，收起后没有任何事件把它拉回，只能切走再切回搜索。删掉这一句，搜索框显隐只由真实列表滚动驱动。
- **#56 复制页选中区间看不见**：8 个 `EditTextStyle*` 都没定义 `android:textColorHighlight`，选中区间只有默认浅色底、压在自定义 `background_edittext` 上几乎看不出。新增 `@color/text_selection_highlight`（`#55FF6699`，33% 品牌粉）并加到全部 8 个 `EditTextStyle*`。
- **崩溃类 6（PlayerActivity「Only fullscreen…」/「Unable to destroy」）**：`e95d4ea` 给 `onDestroy` 里 `requestedOrientation=` 加的 try/catch 随 §3.90 一起被删，HEAD 上又是裸赋值。按原样加回：只要求还原方向，失败只记日志、不阻断后续清理。
- **崩溃 159（pointerIndex，图片页）**：图片查看器的 `PhotoViewpager` 是裸 `ViewPager` 子类，没套 `SafeViewPager` 的指针兜底。把 `guardPointerIndexCrash` 提成文件级 `internal inline` 函数，`SafeViewPager` 与 `PhotoViewpager` 的 `onInterceptTouchEvent` / `onTouchEvent` 共用。
- **崩溃 128/129（`BiliDanmukuParser` NumberFormatException）**：`p` 字段与高级弹幕的整数字段可能被上游写成小数（线上实测 `2188.43537415` 喂给 `Integer.parseInt`）。解析跑在 DFM 渲染线程上，异常逸出会打死整条线程。修法：`startElement` 的 `p` 解析包 try/catch（只丢这一条），`characters()` 拆成包装 + `charactersUnsafe()` 统一吞 `NumberFormatException`；`textArr[9]` 的时长改为 `Float.parseFloat` 后截断。
- **崩溃 141（`AndroidDisplayer.draw` 的 DrawingCache NPE）**：`hasDrawingCache()` 为真时 `danmaku.cache` 仍可能已被置空，`.get()` 直接 NPE。改成 `danmaku.cache instanceof DrawingCache` 判空，取不到就退回直接画文字。
- **控制台清账**：崩溃 82 → 12（70 条按「已修 / 老版本」标已处理，保留 12 条：`158`/`159`/`156`/`141`/`132`/`129`/`128`/`123`/`118`/`95`/`88`/`74`）；反馈 #52/#38/#46 已属「已修 / 已解答」，但当时控制台接口（MCP 侧）只暴露崩溃的 handled 开关、没有反馈的，只能在 WebUI 手动点；**26.11.01 晚服务端补了 `feedback_set_handled` / `feedback_bulk_handled`，这批已在 §3.99 标掉**。**标记已处理 ≠ 风险清除**：类 6 的 6 条已标，代码修复是这一轮才补回的。
- **验证**：
  - `:app:assembleDebug`（standard + coexist）通过；`:app:testStandardDebugUnitTest` / `:app:testCoexistDebugUnitTest` 各 107 suites / 1126 tests / 0 失败。
  - 设备侧（AVD `wear5`，`sdk_gwear_x86_64` / Android 14 / 466×466，装 `coexist-arm64-v8a-debug`）：#42 用空关键词连续横滑三次（视频 → 番剧 → 用户）后 `searchbar` 仍在 `y=58`、`keywordInput` 存在 ✔；#56 用 `aapt2 dump resources` 确认 8 个 `EditTextStyle*` 的 `android:textColorHighlight`（attr `0x01010099`）都指向 `color/text_selection_highlight` ✔（该 AVD 渲染本身放大溢出，**像素级对照不可信**，与 §3.80 结论一致）。
  - **未验**：#78（短视频需要登录态与真实 feed）、#51（需按 `docs/review/agent-test-guide.md` §4.9 伪造双文件缓存）、4 处崩溃兜底（要复现崩溃条件）。三处以代码走查 + 构建/单测为准。
  - **顺带发现（待查）**：该 AVD 上历史记录浮层 `history_recyclerview` 覆盖 ViewPager 区域，从屏幕中间横滑会被它吃掉，只有贴右边缘起滑才翻页；真机是否同样需要再确认。

### 3.97 两条 26.11.01 新反馈：取流失败文案 / 圆屏列表长条目过度缩放（26.11.01）

- **缘起**：§3.96 清账后新到的两条反馈（#80、#79），都是 26.11.01 版上报，一条是取流失败的报错文案、一条是实验效果的边界。
- **#80「进视频详情页提示 no valve for durl」**：原文是 org.json 的 `JSONException: No value for durl`。详情页 `VideoInfoFragment` 进页会预取播放地址（`PlayerApi.getVideo`），而退回 10.27 的取流实现是裸 `data.getJSONArray("durl")`；付费/充电专属、地区限制、需登录、互动视频等稿件本来就不返回 durl，异常被 `MsgUtil.err` 原样弹成报错框。修法：`getVideo` / `getBangumi` 改用 `optJSONObject` / `optJSONArray` 判空，取不到时抛新的 `PlayerApi.NoPlayUrlException`（继承 `JSONException`，调用点既有的 catch 分支不用改），文案由纯函数 `PlayerApi.describePlayUrlFailure` 生成（优先服务端 message，其次接口码，最后通用原因）；详情页预取把这个异常降级成「只记日志」，点播放时再用同一句原因提示（新增 `playUrlError` 字段），不再空弹「视频信息还在加载中」。
- **#79「圆屏列表遇评论区长图会把整条评论缩小、透明度很高」**：`RoundListEffect.transform` 原来按**条目中心**算离屏幕中心的距离。带长图的评论条目很高（但没到「接近整屏高就跳过」的阈值），中心点已经贴到屏缘 → t≈1 → 整条缩到 0.5、淡到 0.15，而它明明有一部分就在屏幕中间。修法：判定抽到纯逻辑 `ui/appearance/RoundListEffectMath`，改成按「条目纵向跨度离屏幕中心最近的一点」算距离（跨度覆盖屏幕中心 → t=0，不缩放；完全在中心一侧才按最近边算）；缩放/淡出曲线与「接近整屏高的内容整体跳过」都不变。
- **验证**：
  - `assembleDebug` 双 flavor 通过；两 flavor 单测各 **109 suites / 1142 tests / 0 失败**（新增 `RoundListEffectMathTest` 9 例、`PlayerApiPlayUrlTest` 7 例，后者钉住「服务端 message 优先 / 只有接口码 / code=0 但无 durl / 响应为空」四种文案，并断言不再出现 `No value for`）。
  - 设备侧（AVD `wear5`、Android 14、466×466）：进 `aid=2` 详情页，logcat 可见 `playurl -> 200`、`history>819000,62131`、`qn_val>[80,64,16]`、评论首屏拉取成功，**没有走失败分支**（成功路径无回归）✔；把 `ui_round_list_effect` / `player_ui_round` 打开后冷启动重进菜单页，截图与关闭态不同——首/末项明显收窄变淡、中间项满宽，重构后曲率效果仍生效 ✔（测完已把两个开关恢复成关闭）。
  - **未验**：#80 的失败形态（要真的打开一个付费/充电专属稿件或该用户的那条稿件）与 #79 的长图评论（要真实评论区长图）都没能在模拟器上构造，靠两个纯逻辑单测钉住；AVD 渲染本身放大溢出，像素级版式结论不可信（同 §3.80）。

### 3.98 短视频「画面被拉伸铺满整屏」与「保持亮屏丢失」（26.11.01，用户点名）

- **缘起**：用户报「①短视频比例被强行拉伸成全屏 ②短视频保持亮屏功能丢失」，要求看代码定位并修。
- **先记复现结论（防止误判成回退）**：在 AVD `wear5`（Android 14、466×466、已登录）上用 26.11.01 发行版跑完整个矩阵（默认设置 / 圆屏适配开 / 界面边距 5%·3% / 播放→暂停→恢复→上滑切条→Home→熄屏→横滑详情页→退出重进，两个 flavor 都测）：**两个症状都没复现**——12+ 条 `ANativeWindow_setBuffersGeometry` 全与视频尺寸同比例（`360x640 → 262x466`、圆屏 `228x406`），真在播时窗口 `fl` 一直含 `KEEP_SCREEN_ON`。`git log -S` 与逐行 diff 也确认 §3.90 回退范围不含短视频两文件、`6216c1c`（保亮屏）/ `fbe057f`（圆屏画面尺寸）都还在 HEAD —— **不是回退，是长期设计缺陷**；模拟器上「B 站合一 MP4 的 moov 带分辨率」「AVD/MuMu 不自带熄屏」正好把两条缺陷遮住（后者 §7.52 已记）。
- **缺陷 1：画面尺寸只在 `onPrepared` 读一次**（→「拉伸成全屏」）
  - `PageHolder` 从 `PlayerState` 只取一次 `videoWidth/videoHeight`，`adjustVideoSize()` 在 `videoWidth==0` 时**静默早退**，而 `TextureView` 初始是 `MATCH_PARENT` → 尺寸未知时画面一直铺满整页（连顶栏/底栏背后一起铺满）。
  - 尺寸为什么会未知：尺寸消息来自容器 `codecpar`（`outputs/ijk-src/.../ff_ffplay.c` 先发 `FFP_MSG_VIDEO_SIZE_CHANGED` 再发 `FFP_MSG_PREPARED`），moov 没带分辨率（或被 `probesize=1024` / `analyzeduration=1` 的激进探测窗压掉）时就是 0；mediacodec 路径下解码器的补发被注释掉（`ffpipenode_android_mediacodec_vdec.c`），**0 一旦出现即永久 0**。而 `IjkPlayerBridge.setOnVideoSizeChangedListener` 一直在更新 `PlayerState`，**全仓无消费者**（断头路，自 initial commit 起）。
  - 更常见的一条成因：`mediacodec-auto-rotate=1` 下容器给未旋转尺寸、解码器出旋转后的帧（同文件 `frame_rotate_degrees` 分支），而 `MEDIA_INFO_VIDEO_ROTATION_CHANGED` 以前没人消费 → 90/270 的竖屏视频按横屏比例摆框，观感同样是「拉伸」。
  - 改法：`PageHolder.applyVideoSizeFromState()` 在 `startStateCollection()` 每轮采样里检查尺寸（去重，真变了才重摆），`onPrepared` 与 `updateVideoSize()`（接回 `onConfigurationChanged → onHostScreenSizeChanged()`，原先 `notifyScreenSizeChanged()` 零调用方）走同一入口，`releasePlayer()` 复位尺寸与去重字段；未知尺寸用 `fallbackRatio`（上一条有效比例，默认 16:9）退化，**绝不留 `MATCH_PARENT`**；`IjkPlayerBridge` 新增 `PlayerState.rotationDegrees` 并在 `setOnInfoListener` 接 `MEDIA_INFO_VIDEO_ROTATION_CHANGED`；尺寸计算抽成纯逻辑 `activity/video/ShortVideoLayoutMath.kt`。
- **缺陷 2：保亮屏只挂在一个 setter 上**（→「保持亮屏丢失」）
  - 判据链 `syncKeepScreenOn()` 要穿 `shellAdapter → activeFeedFragment → PageHolder` 四层；`onStart` 里那次重算必然早于 Fragment 的 `onResume`（此刻还在暂停态、算出来是 false），真正的恢复全靠 `onResume → resumeCurrent()` 再写一次 `isPlaying = true`；而 `wasPlayingWhenPaused` 依赖 `onPause` 那一刻 `activeHolder` 还在（被回收/换页时取到 false）→ 会出现「播放器其实在走、窗口没有 `KEEP_SCREEN_ON`」的窗口，真机就熄屏。
  - 另两条方向相反的脱钩：`PageHolder.play()` 在 `!isPrepared` 时也把 `isPlaying` 置真（取流失败 / prepare 没完成 → 永久亮屏）；页面不可见时 prepare 完成仍会 `start()` 并置真，把标志加回已 stop 的窗口。
  - 改法：`syncKeepScreenOn()` 叠**前台判据**（`isForeground`，`onStart`/`onStop` 维护，`onStop` 仍在兜底清）；新增 `onResume()` 与 `ShortVideoFeedFragment.onResume` / `onResumeAfterShellSwipe` 兜底重算；`isPlaying` 在 state 采样里跟 `playerBridge.isPlaying` 真值对齐；`play()` 加 `isPrepared` 守卫；取流失败 / `createPlayer` 失败 / `initPlayer` 异常三条路径补 `isPlaying = false`。
- **验证**：
  - `:app:assembleDebug`（standard + coexist）通过；两 flavor 单测各 **110 suites / 1152 tests / 0 失败**（新增 `ShortVideoLayoutMathTest` 10 例：9:16 / 16:9 letterbox、方形、非法比例退化、非法区域返回 null、旋转交换判据、圆屏内接圆与夹取）。
  - 设备侧（AVD `wear5`，装 `coexist-universal-debug`；`adb install -r` 覆盖已装的 26.11.01，签名同为 debug key，登录态不丢）：默认设置进短视频页 —— logcat 出现新增的 `画面尺寸: 360x640（旋转 0°，区域 466x466）`，`ANativeWindow_setBuffersGeometry: w=262, h=466 => w=360, h=640`（9:16 居中留黑边，未拉伸）✔；窗口 `fl`：播放中 `KEEP_SCREEN_ON` ✔ → 暂停后消失 ✔ → 恢复后回来 ✔ → Home 后消失（新增前台判据生效）✔ → 回 App 自动续播且标志恢复 ✔；上滑切下一条：新页重新应用尺寸（日志只打一次，去重生效）+ 几何 `262x466` + 标志仍在 ✔。圆屏适配开（`player_ui_round=true`）：几何 `w=228, h=406 => 360x640`（内接圆矩形，228/406=0.5616 ≈ 360/640），播放中 `fl` 含 `KEEP_SCREEN_ON` ✔。测完已把 `player_ui_round` 恢复 `false`、原 26.11.01 安装包装回。
  - **未验**：`videoWidth==0` 的真实流、旋转 90/270 的真实视频、`ui_landscape` / 非默认 `density`；信息流连刷 10+ 条全是 9:16 / rotation=0。前三项靠 `ShortVideoLayoutMathTest` 与代码走查兜底，真机用例登记在 §2.1。
- **文档**：`docs/architecture-map.md` §7.52 补三道闸 + 新增 §7.58；`docs/agents/pitfalls.md` 保亮屏条目补三道闸、新增画面尺寸条目。

### 3.99 菜单设置「改了保存不住、恢复默认」+ 控制台反馈批量标记已处理（26.11.01）

- **缘起**：用户要求「再看看有没有新的反馈，然后把所有处理过的反馈标记已处理」。控制台新到一条 26.11.01 反馈 **#81**（OPPO OWW212）：菜单设置更改后无法保存、会恢复默认；同时控制台 MCP 服务端这次补上了反馈的写入能力（`feedback_set_handled` / `feedback_bulk_handled` / `feedback_note_add` / `crash_bulk_handled`），以前只能去 WebUI 手点。
- **#81 根因**：`util/MenuConfig.kt` 只有一个严格解析入口 `parse(raw)` —— 任一 key 不在 `ALL_ITEMS`（公共 14 项 + `OPTIONAL_ITEMS` 私有项）或出现重复就直接返回 null。`loadEnabled()` 把「parse 失败」一律当成**旧数据**处理，走迁移分支用默认顺序/默认显隐重建并**写回** `menu_enabled`，于是用户的排序与显隐被静默清空。触发条件很现实：`menu_enabled` 里含公开包不认识的 key —— ①装过私有/BETA 包（菜单项如 `scan_qr` 来自私有 `FeatureHolder.menuKey()`），换回公开包后公开包不认它；②历史版本删改过菜单项留下的残 key。
- **改法**：新增 `parseLenient(raw)` —— 丢弃不认识的 key、按原顺序保留、去重；`loadEnabled()` 严格解析失败后先走容错分支：清理结果非空且固定项（`FIXED_ITEMS`）都在 → 保留用户顺序、补齐缺失的 `OPTIONAL_ITEMS`、写回清理后的串并返回；只有连固定项都缺（真正的脏数据）才回退到原来的迁移/默认值。严格 `parse()` 的语义（含两条拒绝用例）保持不变。
- **验证**：
  - 新增 5 例单测（`MenuConfigTest` 现 29 例）：丢弃未知 key 与重复、保留私有 key、全脏时返回 null、**未知 key 时保留用户顺序而不是重置默认**（存 `settings;recommend;bogus_key;local;search` → 结果以 `settings` 开头）、固定项缺失时仍走迁移。`:app:assembleDebug`（standard + coexist）通过；两 flavor 单测各 **110 suites / 1157 tests / 0 失败**（较 §3.98 的 1152 例 +5）。
  - 设备侧（AVD `wear5`，Android 14，466×466，`coexist-arm64-v8a-debug`）：先用调试桥把 `menu_enabled` 写成 `settings;recommend;short_video;popular;hotsearch;search;dynamic;myspace;message;local;scan_qr;bogus_key`（首项刻意不是默认的 `recommend`），打开 `SettingMenuActivity` 后重读 —— 变成 `settings;recommend;…;scan_qr`：**首项仍是 settings（顺序保住）、`bogus_key` 被丢弃**，修复前同样操作会被整份重置成默认顺序 ✔。测完把 `menu_enabled` 恢复成默认顺序。
  - **未验**：真实 OPPO 手表上的现场（模拟器用 `bogus_key` 模拟「不认识的 key」）；「私有包 → 公开包」覆盖安装的真实升级路径没有走（要私有包产物）。
- **控制台清账（本轮）**：把已处理/已解答/已拍板的反馈标已处理 —— **#78、#56、#55、#52、#51、#42、#38、#46、#49、#81**（其中 #55/#52/#51/#38 是 §3.79 修过、§3.96 补回回退的，#46 已由「左右滑动控制进度」设置项解答，#49 用户拍板不做）。保留 pending 7 条：**#60**（老掉登录，待上周期性 Cookie 刷新）、**#48**（无法下载更新，疑似已随证书信任修复，已加管理员备注请用户升级后再试）、**#41**（静音拖进度出声，只在外挂音轨出现，等录屏）、**#34**（分段导致跳片头片尾，未定位）、**#31**（载入中切后台回来比例错，需真机复现）、**#30**（缓存文件夹「加入」按钮，功能请求）、**#20**（表冠转动慢，需原版倍率对照）。崩溃侧本轮未动，仍是 12 条保留。

### 3.100 收藏夹列表「Invalid view holder adapter position」崩溃（控制台 #160，26.11.01）

- **缘起**：清账后控制台新到一条 26.11.01 崩溃 **#160**（hesc `uws6152_1h10_go`）：`java.lang.IndexOutOfBoundsException: Inconsistency detected. Invalid view holder adapter position … adapter:f1.d … context:FavoriteFolderListActivity`。堆栈是 release 混淆帧，用 `app/build/outputs/mapping/coexistRelease/mapping.txt` 还原：`f1.d` = `adapter.favorite.FavoriteFolderAdapter`，`p0.Y.l` / `p0.o.*` = `androidx.recyclerview.widget.RecyclerView$Recycler` 与 `GapWorker`（预取线程）。日志时序（10:32 列表页 → 编辑页 → 回列表页 → 再进编辑页）说明是**从编辑页返回后立刻重载**这条路径。
- **根因**：`FavoriteFolderListActivity.loadFolders()` 在 `CenterThreadPool` 线程里直接 `folderList.clear()` + `addAll(...)`，而这份 `ArrayList` **就是当前已挂在 RecyclerView 上的 adapter 的备份数据**；新 adapter 还在后面才创建。后台线程清空的瞬间 `getItemCount()` 从「N+2」掉到 2，紧接着又长回 N+2，全程没有任何 notify —— 布局/预取线程拿着旧位置去取 holder，就抛这个 Inconsistency。`onActivityResult`（改名/新建返回）会立刻重载，所以「快速进出编辑页」最容易撞上。
- **改法**：请求结果先在后台装进一份**全新的** `ArrayList`，不再碰旧列表；然后在 UI 线程一次性换掉 —— 新建 adapter、挂监听、`setAdapter`（基类内部再 `runOnUiThread` 赋值），并 `isFinishing || isDestroyed` 双重早退。旧 adapter 随替换被摘下，它引用的旧列表不再被任何人改写，itemCount 不会在无 notify 的情况下跳变。
- **验证**：
  - `:app:assembleDebug`（standard + coexist）通过；两 flavor 单测各 **110 suites / 1157 tests / 0 失败**。
  - 设备侧（AVD `wear5`，Android 14，已登录 mid=591904067）：装新包后进「我的 → 收藏」，日志正常拉到默认收藏夹（1888/50000）、重要收藏夹（23/1000）等，页面正常渲染、无 FATAL / Inconsistency ✔。
  - **未验**：原崩溃的精确触发（要在真机上「进编辑页改名保存 → 返回」并让预取线程刚好在重载窗口取 holder），本次以「不再共享可变列表」的线程模型改造 + 页面正常加载为准；该崩溃 1 次、单机型，先观察后续上报。

### 3.101 外置 SD 卡上的缓存视频用凉腕播放器播放崩溃：`Failed to find configured root`（26.11.01，用户现场）

- **现场**：用户（Android 6.0 手表，凉腕播放器 5.1.0，硬解 / TextureView / AudioTrack）把设置里「缓存路径」改到外置 SD 卡后，点缓存视频用凉腕播放器播放即崩：`java.lang.IllegalArgumentException: Failed to find configured root that contains /storage/5C00-1D0E/Android/media/com.RobinNotBad.BiliClient.coexist/<标题>/video.mp4`，栈顶 `androidx.core.content.FileProvider.d(SourceFile:180)`。崩溃发生在**拉起播放器之前**（`PlayerApi.getVideoUri` 里），与硬解 / Surface / 音频输出设置无关。
- **根因**：androidx 的 FileProvider 只认**主卷** —— `file_paths.xml` 的 `<external-path>` 取 `Environment.getExternalStorageDirectory()`，`<external-media-path>` 取 `getExternalMediaDirs()[0]`（1.13.1 源码实测）。用户在配置里把缓存目录指到外置卷后，路径形如 `/storage/5C00-1D0E/Android/media/<包名>/…`，没有任何配置根包含它，`getUriForFile` 直接抛异常。XML 是编译期资源，没法按当前挂载的卷动态长根；历史能覆盖它的 `<root-path path="" />` 已被审计 M12-e 删掉。完整分析见 `docs/architecture-map.md` §7.59。
- **改法**：新增 `util/LocalFileProvider.kt`（只读 `ContentProvider`，authority `${applicationId}.LocalFileProvider`，URI `content://…/f/<Uri.encode(规范绝对路径)>`）+ `util/SharedFileRoots.kt`（运行时收集所有存储卷的根：主卷根、每块卷的 `files` / `cache` / `media` 应用专属目录、由 `/Android/` 段反推的卷根、内部 `filesDir/video`）+ `util/SharedFilePolicy.kt`（纯函数归属判断，`SharedFilePolicyTest` 14 例）。`PlayerApi.getVideoUri` 先照旧走 FileProvider，捕获 `IllegalArgumentException` 后改走自建 provider；两者都拿不到 URI 时返回 null，`jumpToPlayer` 回落 `buildTerminalPlayerIntent()`（从原 `terminalPlayer` 分支原样抽出，逐字段等价）用内置播放器打开并提示 `msg_player_local_share_fallback` —— 分享文件失败不再带崩缓存页。安全边界与 `file_paths.xml` 注释、`docs/agents/pitfalls.md` 已同步（`exported=false` + `grantUriPermissions`，只读，先 `canonicalPath` 再判根，不提供枚举）。
- **验证**：
  - `:app:assembleDebug`（standard + coexist）与 `:app:assembleRelease`（standard + coexist，R8）均通过；两 flavor `*DebugUnitTest` 各 **111 suites / 1171 tests / 0 失败**（比本轮前 110 / 1157 新增 `SharedFilePolicyTest` 14 例、合计 +1 suite +14 tests）。
  - 发布包静态复核：`app/build/outputs/mapping/*Release/mapping.txt` 里 `com.RobinNotBad.BiliClient.util.LocalFileProvider -> com.RobinNotBad.BiliClient.util.LocalFileProvider`（类名没被 R8 改掉）；`app-coexist-universal-release.apk` 的清单里 provider 的 `exported=false` / `grantUriPermissions=true` / `authorities=com.RobinNotBad.BiliClient.coexist.LocalFileProvider` 都在。
  - **模拟器端到端（主证据：API 23 / Android 6.0，AVD `rebili_api23_sd`，512M SD 卡，外置卷 `/storage/0000-0000`，coexistDebug）**：
    - 旧包（未修复的 HEAD `47c8a41` 用 `git archive` 独立构建）点外置卷缓存即崩，异常类型 / 消息 / 栈帧（`FileProvider$SimplePathStrategy.getUriForFile` → `PlayerApi.getVideoUri` → `jumpToPlayer` → `CacheListAdapter.playVideo`）与用户现场同型，系统弹「已停止」，全程 0 条外部播放器日志（崩在 `startActivity` 之前）；
    - 修复包（16:30:57 构建）：不崩、`logcat -b crash` 为空；日志出现「文件不在 FileProvider 配置根内（多为外置卷），改走 LocalFileProvider」；stub 播放器（`com.aliangmaker.media` / `PlayVideoActivity`，另一进程另一 UID：应用 10061 / stub 10062）收到 `content://<包名>.LocalFileProvider/f/%2F…`，`readGrant=true`，`ContentResolver.openInputStream` 读到 **55,881 B = 夹具文件大小**，`getType` = `video/mp4`；
    - 主卷回归：缓存路径改回 `/storage/emulated/0/Android/media/<包名>` → URI 回到 `content://<包名>.FileProvider/ext_media_root/…`，stub 同样读到 55,881 B、应用不崩。
    - 补充证据（API 37 / Android 17，`rebili_api37_sd`）：旧包在同一 AVD 上以用户现场那种 `…/Android/media/<包名>` 路径复现同型崩溃。
  - **仍未覆盖 / 边界**：真机 Android 6.0 手表 + 真实凉腕播放器 5.1.0（模拟器用 stub 播放器验证的是「跨进程拿到 content:// 并读到字节」这条机制，不是凉腕 UI）；API 23 模拟器上 app 进程没有 `media_rw` / `sdcard_rw` 补充组、写不进 SD 的 `Android/media`，所以那一轮的复现路径是同卷 `Android/data/<包名>/files/video`（同根因、同异常、同栈），「真机 SD 的 `Android/media` 可写 + `getExternalMediaDirs()` 枚举到外置卷」这条具体路径目前只有单测覆盖；内置播放器兜底分支与非 mp4 扩展名（`m4s` / `m4a`）未端到端构造。真机回归项见 `docs/review/real-device-regression-checklist.md` 第 250 条。

### 3.102 圆屏适配：首页/推荐页的时钟被顶到屏幕最右、被圆盘切掉（26.11.01，用户点名）

- **现场**：用户给了两张顶栏截图 —— 期望图是「时钟居中、下面『⌄ 推荐』居中」，实际图是「时钟贴在屏幕最右上、标题居中」。原话：「开启圆屏适配后的期望效果是图一，可是目前是图二的状态，请修复」。
- **根因**：`res/layout/activity_simple_main_refresh.xml` 是全工程**唯一**把 `@id/timeText` 套进一层 `@id/menuArea`(LinearLayout, `alignParentRight`) 的顶栏布局（其余 40+ 个布局的时钟都是 `@id/top`(RelativeLayout) 的直系子 View）。`BaseActivity.setRound()` 的圆屏分支靠 `newTopbarParams(clock).addRule(RelativeLayout.CENTER_HORIZONTAL)` 把时钟挪到正中；时钟在 LinearLayout 里时 `newTopbarParams` 返回的是 `LinearLayout.LayoutParams`，`if (clockParams is RelativeLayout.LayoutParams)` 判假 → 居中规则被静默跳过，时钟仍由 `alignParentRight` 钉在最右。5 个用该布局的页面全部中招：`RefreshMainActivity`（含「推荐」页 `activity/video/RecommendActivity.kt`）、`RankingActivity`、`PopularActivity`、`PreciousActivity`、`video/local/LocalListActivity`。
- **改法（只改布局，不动代码逻辑）**：删掉 `menuArea`，把 `timeText` 提成 `@id/top` 的直系子 View（保留原 `alignParentRight/End` + `marginEnd=8dp` / `marginVertical=3dp`），`pageName` 的 `layout_toStartOf` 从 `@id/menuArea` 改指 `@id/timeText` —— 顶栏结构与 `activity_simple_refresh.xml` 等其余布局完全一致，`setRound()` 不需要任何新分支。顺带把 `BaseActivity.setRound()` / `newTopbarParams()` 里点名 `menuArea` 的注释改成现状（保留「按真实父容器判型」的防御分支）。
- **验证**：
  - `:app:assembleDebug`（standard + coexist）通过；两 flavor 单测各 **111 suites / 1171 tests / 0 失败**。
  - 设备侧（AVD `wear5`，Android 14 / 466×466 / 320dpi 圆屏，装 `coexist-universal-debug`，`player_ui_round=true` + `paddingH_percent=5` / `paddingV_percent=3`）：
    - **修前（复现）**：`pageName [184,41][282,83]`（居中 ✔）、`timeText [380,13][443,46]`（钉最右 ✘）；截图顶栏 x330–466 全黑——圆屏遮罩在 y=30 处只亮到 x≈347，时钟整块落在圆盘外被切掉，与用户图二同型。
    - **修后**：`pageName [184,41][282,83]`、**`timeText [201,13][264,46]`（中心 232.5 ≈ 屏中心 233 ✔）**；像素扫描顶栏第一行亮像素 x203–262（中心 232）、第二行 x192–271（中心 231），截图与用户图一一致。
    - 同布局的另外 3 页同样修好：`PopularActivity` / `RankingActivity` / `PreciousActivity` 的 `timeText` 均为 `[201,13][264,46]` ✔。
    - **回归对照（`player_ui_round=false`，同一页）**：`pageName [39,19][355,61]`、`timeText [355,19][427,57]`（右边距 427 = 内容右 443 − 8dp×2 = 16），顶栏高 54 = 标题高 42 + 2×3dp，与改前逐项一致 —— 非圆屏版式零变化。
    - 参照页 `settings/SettingMainActivity`（本来就走直系子 View 的布局）同一套设置下 `timeText [201,13][264,46]`，两处口径一致。
  - **未验**：真圆屏手表上的肉眼观感（本机只有 AVD）。
- **文档**：`docs/agents/pitfalls.md`「伪装与顶栏手势」补一条「顶栏时钟不能包在 LinearLayout 里」；`docs/architecture-map.md` §8.5 补第 12 条。

### 3.103 「我的反馈」空态点顶栏不退出、改成原地刷新（26.11.02，用户点名）

- **现场**：用户报「我的反馈界面点击顶栏不会退出，而是会刷新」。「我的反馈」列表为空时（新装、或本机没提交过反馈），点顶栏标题区域不返回上一级，而是出现刷新转圈并重拉一次列表。
- **根因**：`res/layout/activity_simple_refresh.xml` 的空态浮层 `emptyTip` 排在 `top` **之后**声明，且 `layout_width/height=match_parent` —— RelativeLayout 里后声明的兄弟节点画在更上层，触摸也**从后往前**派发，于是它把整条顶栏盖住。多数页面只是「空态长文案压住标题」（`emptyTip` 不可点，点击会穿透回 `top`），但 `RefreshListActivity.setOnEmptyRetry()` 会给它装 `OnClickListener`（`isClickable=true`），此后再点顶栏就全归空态：`hideEmptyView(); setRefreshing(true); action.run()`，「我的反馈」的 action 正是 `load()` → 用户看到的就是「点了顶栏，页面刷新」。`activity_simple_main_refresh.xml` 是同一份模板的复制品（首页/推荐页的菜单栏），同样中招。
- **改法（只改布局，不动任何 Java/Kotlin 逻辑）**：两个布局的 `emptyTip` 各加 `android:layout_below="@id/top"` —— 空态只铺列表区，顶栏重新可点；顺带解决空态文案压住顶栏标题的观感问题。`setOnEmptyRetry` 的「点空态重试」语义保留（空态区域本身仍可点）。
- **验证**：
  - `:app:assembleDebug`（standard + coexist）通过；`testStandardDebugUnitTest` **111 suites / 1171 tests / 0 失败**（与改前同基线，本改动没有新增/删除用例）。
  - 设备侧（AVD `wear5`，Android 14 / 466×466 / 320dpi，装 `coexist-arm64-v8a-debug`，用 DebugBridge `start FeedbackListActivity` 直达页面，列表为空）：
    - **修前（复现）**：`emptyTip [23,13][443,440]` 与 `top [23,13][443,122]` 完全重叠；点屏上 `(233, 65)`（顶栏标题处）→ 前台仍是 `FeedbackListActivity`，logcat 出现 `GET https://rebiliterminal.zsapp.asia/terminal/feedback/mine -> 200`，即「刷新而不退出」，与用户描述一致 ✔；
    - **修后**：`emptyTip [23,122][443,440]`（落在 `top` 下方）；同一点位点击 → 前台变为 `SettingMainActivity`（上一级），logcat 无 `/feedback/mine` 请求，正常退出 ✔；
    - 点空态自身区域 `(233, 233)` 仍会 `GET /feedback/mine`，说明「点我重试」没有被改坏 ✔。
  - **未验**：真手表上的实际手感；非空列表态（`emptyTip` 此时 `GONE`，改动不参与，顶栏点击路径与改前完全一致）没有另行实点。
- **文档**：`docs/agents/pitfalls.md`「基类与列表」补一条「空态浮层必须 `layout_below="@id/top"`」。

### 3.104 按钮文字换行 + 基线对齐导致「同一行按钮对不齐」（26.11.02，用户点名，共性问题）

- **现场**：用户先贴了一张手表实拍（某工具页），再补一句「视频详情页里也有这个 bug，所以这是按钮的共性问题」——同一排的两颗按钮，一颗文字折成两行、另一颗一行，两颗按钮上下边缘错开，整块看起来像布局崩了。
- **根因（三层叠加）**：
  1. 主题里 8 个按钮样式此前都直接继承 `Widget.MaterialComponents.Button`，**没有限制文字行数**。手表内容区只有 200dp 出头，两个按钮各占一半时每颗内容宽 ~70～100dp；5～6 个汉字的标签（「从剪贴板粘贴」「稍后再看」等）在系统字体放大后会折成两行。
  2. 横向 `LinearLayout` 默认 `baselineAligned="true"`：两颗按钮按**文字基线**对齐，一旦行数/字号不同，较矮那颗会被整体上下平移，于是上下边缘都错开。
  3. 系统「字体大小」越大越明显；同一份布局在经典终端主题（按钮左右 padding 2dp）能撑住，在 16dp padding 的主题下更早折行，所以问题「看主题、看设备」偶发。
- **改法**：
  - 新增 `res/values/styles.xml` 的 `ButtonStyleBase`：`android:maxLines=1` + `android:ellipsize=end` + AppCompat 自动字号（`autoSizeTextType=uniform`，7～14sp、1sp 步进）。8 个主题按钮样式（`ButtonStyle` / `ButtonStyleLight` / `ButtonStyleZhihu` / `ButtonStyleIQIYI` / `ButtonStylePurpleFantasy` / `ButtonStyleRainbow` / `ButtonStyleClassicGray` / `ButtonStyleTerminal`，玻璃变体随父样式）统一改为继承它 → 所有 MaterialButton 永远单行，放不下就缩字号，不折行。
  - **保留多行/写死字号的例外**（避免修 A 坏 B）：`cell_episode.xml`、`cell_item_vertical.xml`（番剧标题、看点的「内容\n时间段」）与 `item_menu_setting_help.xml`（展开后的多行说明）在布局里覆盖 `maxLines=10` + `autoSizeTextType=none`；`activity_captcha_webview.xml` 三个缩放键、`activity_feedback.xml` 提交键、`activity_image_picker.xml` 与 `activity_send_dynamic.xml` 各两键、`cell_subtitle.xml` 用 `autoSizeMaxTextSize` 把上限钉回原字号；`TutorialManagerActivity` 程序化创建的 11sp 按钮用 `setAutoSizeTextTypeUniformWithConfiguration(7, 11, 1, SP)`。
  - 多按钮横排的 `LinearLayout` 补 `android:baselineAligned="false"`：`fragment_video_info.xml`（两行）、`fragment_media_info.xml`、`activity_image_picker.xml`、`activity_update.xml`、`activity_send_dynamic.xml`、`activity_captcha_webview.xml`；另有一个可选私有功能页的同类布局在私有仓库单独提交。
- **验证**：
  - 纯净 worktree（`git worktree` at HEAD `9580444` + 本改动，公开配置）：`:app:assembleDebug`（standard + coexist）通过；`testStandardDebugUnitTest` 与 `testCoexistDebugUnitTest` 各 **108 suites / 1125 tests / 0 失败 0 错误 0 跳过**（本改动不新增用例）。
  - AVD `wear5`（Android 14 / 466×466 / 320dpi，`coexistDebug` + 私有源码注入）实测：
    - `font_scale` 1.0 / 1.3 / 1.5 / 2.0 逐档看按钮：视频详情页（aid=170001）的「稍后再看 | 缓存」「转发 | 视频摘要」两排、工具页的「粘贴 | 打开」在 1.5 与 2.0 下都是**单行、同高、上下边缘齐平**；2.0 档长标签自动缩到 7sp 仍不折行；
    - 回归：菜单页（全宽按钮）、菜单设置（「使用说明」点开后仍是多行说明，无截断）、搜索设置（8 字按钮「调整搜索类别排序」单行）、内置选图页（「全部图片 | 系统选择器」同高）、教程管理（程序化 11sp 按钮）；
    - 未验：真手表；播放页「看点」列表与竖版选集（`cell_episode` / `cell_item_vertical` 只解除了单行限制，本机没造出可实点的播放场景）；验证码页 WebView 在该 AVD 上不可用（页面直接提示 WebView 不可用），只确认布局能 inflate。
  - **本机 AVD 的通用问题（与本次改动无关）**：改 `font_scale` 会触发整进程重启，重启后可能弹「夜深了」/debug 包警告等启动弹窗；用 DebugBridge 先把 `night_reminder_enable=false`、`disclaimer_shown=true`、`app_version_last` 写成当前版本号再测，能省掉反复点弹窗。
- **文档**：`docs/agents/dev-conventions.md` 新增「按钮文字与行内对齐」一节（含例外写法与自测口径）。

### 3.105 控制台 11 条「代码无修复 / 未做」反馈批量修复（26.11.02，用户点名，代码审计后）

- **缘起**：用户要求用控制台 MCP 核对 47 条反馈的「完成情况」，对照 HEAD 提交发现 13 条「代码里根本没有对应修复 / 压根没做」；用户勾选其中 11 条要求修复（未选 #20 表冠慢、#82 短视频长按倍速）。
- **本轮修复（每条：改法 + 关键文件）**：
  - **#84** 消息页空列表必现 IOOBE：`PrivateMsgApi.getUsersInfo` 空 uidList 发请求前返回空 map；`MsgUtil` 只在异常信息带 RecyclerView/Adapter 特征时才提示「Adapter错误」，其余按普通错误显示原文（`util/AdapterErrorClassifier` + 单测）。
  - **#60** 老是掉登录：新增 `util/CookieRefreshPolicy`（30 分钟正常间隔、失败 5min 起指数退避封顶 4h、-101 边界）与 `util/CookieRefreshScheduler`（`BiliTerminal.onCreate` 进程级启动、5 分钟扫描；`NetWorkUtil` 统一响应入口识别「带 SESSDATA 仍 -101」只打标记）。不用 WorkManager/AlarmManager、不新增依赖。
  - **#61** 长按倍速可配置：`activity/player/PlayerLongPressPolicy`（1.5/2/3/5，默认 3.0）+ 设置页「长按倍速档位」；长按处每次重读（改完即生效）。
  - **#83** 多P续播：`PlayerData` 补 `cidHistory` 进 Parcel 并新增 `lastPlayTimeMs`；`activity/video/MultiPageResumePolicy`；详情页点播放命中记录直接起播上次分P；`MultiPageActivity` 修正「点上次那P却播P1」并滚到该P。
  - **#41** 静音拖进度出声：`activity/player/AudioSeekVolumePolicy` + `syncExternalAudioPlayback()`；外挂音轨只在「能听见且视频在播」时运行，静音即暂停、解静音先对齐再起播；`initAudioTrack` 先设增益再 prepare/start。
  - **#31** 载入中切后台回来比例怪：`activity/player/PlayerScreenMetricsPolicy`；缓存上次有效源尺寸，`onResume` / Surface 重建时重读屏幕尺寸并重排画面（不动播放状态）。
  - **#34** 分段导致误跳片头片尾：`activity/player/SkipDecider` 状态机；换P 立即清空片段、在途 `view_points` 响应按 aid/cid/代次丢弃、重复拉取不清已处理。
  - **#14** 图片双击缩放：双击识别挪到 `PhotoViewpager.dispatchTouchEvent` 旁路 GestureDetector（不吞事件），`ImageViewerActivity` 用 `ImageViewerZoomPolicy` 显式 1x→2x→6.25x→1x，动画后校验被 reset 就补钉。
  - **#17** 评论区图片下载：`DownloadActivity` 新增 `PictureDownloadPolicy`（URL 补 https、失败换协议重试、文件名剥 `@`/查询串/非法字符）；看图页下载改单击直接开始（连点闸门）；图片成功/失败提示 SnackBar→Toast（页面 finish 后仍可见）。
  - **#54** 界面边距>0 触控偏移：`BaseActivity` 把用户边距从 DecorView 下沉到 `android.R.id.content`；padding 算术本身不改触摸命中（推导见 KDoc），真机仍待验。
  - **#58** 缓存删不掉：`util/CachePathResolver` 按「条目实际所在根 + 模型真实路径」解析候选目录；`FileUtil.deleteFolderRecursively` 空目录也删、按「目标是否真的不存在」返回结果；`LocalListActivity` / `LocalPageChooseActivity` 失败不再谎报成功、不摘条目。
- **验证**：`:app:assembleDebug`（standard + coexist）通过；`:app:testStandardDebugUnitTest` / `:app:testCoexistDebugUnitTest` 各 **123 suites / 1297 tests / 0 失败 0 错误**（本轮新增 12 个测试类 / 125 例）。
- **未验 / 遗留**：#41（需真机：系统媒体音量 0 时暂停外挂音轨）、#31（需真机复现「载入中切后台再回来」）、#34（需真机多P 连切）、#54（根因未确证，HWARC 真机需复验）；#17 将「3 秒二次点击」改为单击直接下载（用户可见交互变化）；#83 命中续播记录时「点播放」不再进选集页而是直接起播上次分P（另一P 走播放器内选集）；#58 在 Android 11+ 公共目录、无本应用写权限的旧文件仍删不掉，现在会如实报错。

### 3.106 长按加速期间横滑又会调进度（§3.90 回退砍掉 §3.81 门闸，26.11.02 补回）

- **缘起**：用户报「在长按倍速的情况下滑动会触发滑动调节进度」。这条 26.10.30 已修（§3.81，commit `1bff1f0`），26.10.31 末播放器整目录回退（§3.90，commit `7c94531`）时被一起砍掉；§3.96（commit `e39f5c5`）只补回了 4 条中的 3 条，这条一直缺——但 §3.81 与 `docs/architecture-map.md` 仍写着已修，属于**文档与代码不一致**。
- **机制**：长按加速走 `layout_control.setOnLongClickListener`（View 自带 500ms 判定，手指在界内横移不会取消长按回调）。长按回调置 `onLongClick = true` 并设倍速后，`ACTION_MOVE` 里只要横移超过 `swipeSeekThreshold`（24f）就仍会激活 `swipe_seek_overlay`、改 `seekbar_progress`，UP 时还会真的 seek 过去。反向顺序（先横滑后长按）本来就不会误加速：横滑会置 `gesture_click_disabled`，长按回调里有 `!gesture_click_disabled` 守卫。
- **改法**：
  - `activity/player/PlayerGesturePolicies.kt`：`PlayerSwipeSeekPolicy.isSeekAllowed(...)` 增加第 7 个参数 `longPressBoost: Boolean`（**不设默认值**，逼每个调用点显式表态），判据追加 `&& !longPressBoost`；KDoc 写明「26.11.02 原样补回，别当没做过」。
  - `activity/player/PlayerActivity.kt`：`handleSwipeSeekTouch()` 把 `onLongClick` 传进去，门闸拒绝时复用 `cancelSwipeSeekIfActive()` 收残留浮层；新增 `private fun endLongPressBoost()`（恢复 `speed_values[seekbar_speed.progress]` 到 IJK / 弹幕 / 倍速文案）。**两套 `layout_control` 触摸分支的 ACTION_UP 与 ACTION_CANCEL 都要接**：只接 UP 时，触摸被父容器/失焦 CANCEL 后 `onLongClick` 会卡 true → 横滑调进度被永久拦掉、倍速也停在高档。
  - `player/PlayerGesturePoliciesTest.kt`：新增 `longPressBoost_blocksSwipeSeek`、`longPressRelease_restoresSwipeSeek` 两例（该类共 11 例）。
  - 文案/文档侧不用改：`strings.xml:1230` 的设置说明从 §3.81 起就是「未缩放视频、未长按加速时生效」，`docs/FEATURES.md` 同口径——当年没被回退，只有代码被砍了。
- **验证**：`:app:assembleDebug`（standard + coexist）通过；`testStandardDebugUnitTest` **123 suites / 1299 tests / 0 失败 0 错误 0 跳过**（比 §3.105 多 2 例，正是本次新增）。AVD `wear5`（Android 14 / 466×466 / x86_64，装 `app-standard-universal-debug`）实测（DebugBridge 把 `player_swipe_seek` / `player_scale` / `player_longclick` 置真，本地 `combined.mp4` 起播）：
  - **bug 用例**：`input motionevent DOWN` 按住 1.2s（logcat `gesture>longclick_down speed=3.0`、`Playback rate: 3.000000`）后横移 147px——中途截图**没有** `swipe_seek_overlay`，进度只按 3 倍自然前进（12:08→12:25）；UP 后 `Playback rate: 1.000000`。
  - **对照组**（同一注入手法，证明 MOVE 确实被消费，不是没打到）：普通横滑同位移 → 浮层出现 `12:08 / 34:15 (35%)`，UP 后进度真的从 01:08 跳到该点（下一次 dump 已 12:25，含继续播放）。
  - **CANCEL 用例**：长按后发 `input motionevent CANCEL` → 倍速回落 1.0；紧接着普通横滑仍能出浮层（`23:32 / 34:15 (68%)`）→ `onLongClick` 没卡 true。
- **遗留**：未在真手表上验；注入的 DOWN/MOVE 之间没有真人手指的微抖，但三条判据（浮层有无、倍速回落、seek 结果）都来自应用自身行为，对照组成立即可信。

### 3.107 带图评论：发图失败 + 不该按大会员前置拦截（26.11.02，用户点名）

- **结论先说，避免误读**：
  - **确定改对的两件**：① 图床 `biz` 从自造值 `new_reply` 恢复成 `new_dyn`（评论 `pictures` 只认该 BFS 目录，上游 PiliPlus 同口径）；② 删除「带图评论仅大会员可用」的前置拦截，资格交还服务端判定。
  - **未确证的**：**12066 的确切语义仍然没拿到**（本地 `bilibili-API/` 与上游 collector 的错误码表都未收录）。本次**不是**「证明 12066 就是 biz 造成的」，而是把 26.10.02 引入的确定性偏离改回官方口径，并让失败时能看见服务端的码与原话。若仍失败，下一步是拿日志里的 code/message 再收敛。
- **根因 1（biz 被改错）**：提交 `3058512`（26.10.02，批次 6 F3-c）把 `upload_bfs` 的 `biz` 由 `new_dyn` 改成 `new_reply`，提交说明写「用 new_dyn 会被服务端当成动态图处理」——这是误判，`biz` 只决定图片落在哪个 BFS 目录，与"业务类型校验"无关。上游 PiliPlus 评论图与动态图共用 `biz:'new_dyn'`（`lib/pages/common/publish/common_rich_text_pub_page.dart:515-519`），只有私信才用 `im`（`lib/pages/whisper_detail/view.dart:329-331`）。改错后的表现：**图能上传成功，发评论时被服务端以 12066 拒绝**（与 26.10.04 用户反馈的症状一致）。
- **根因 2（不该拦）**：`3b70a2b`（2026-08-16）给「图片」按钮加了 `VipApi.getVipInfo()` 前置检查，非大会员直接 toast「带图评论仅大会员可用喵~」。图片评论的资格应由服务端判定，客户端拦只会把服务端其实允许的用户挡在选图页外（26.11.02 用户点名：发图不需要大会员）。
- **改法**：
  - `api/ReplyApi.java`：`BIZ_DYNAMIC = "new_dyn"`（动态图 + 评论图），新增 `BIZ_MESSAGE = "im"`（私信图），**删除 `BIZ_REPLY`**；`uploadReplyImage` 失败改为抛 `UploadException(code, serverMessage)`，并 `Log.e("debug-上传图片", code/message/biz)`（只记码与原话，不记整包响应）；新增 `SendReplyResult` + `sendReplyDetailed(...)` 把服务端 `message` 带回来，旧 `sendReply(...)` 委托它（行为不变）。
  - `api/ImageApi.java`：常量与类注释同步（`BIZ_MESSAGE`，评论/动态共用 `BIZ_DYNAMIC`）。
  - `api/ApiErrorText.kt`：新增纯函数 `uploadFailed(code)`——-101 未登录 / -102·-111 凭证失效 / -352·-412 风控 / -400·4100001 图片不被接受 / -509 太频繁 / 其余带错误码。
  - `activity/reply/WriteReplyActivity.kt`：删掉 VIP 前置检查与 `VipApi` import（点「图片」直接进内置选图页，仍保留 9 张上限）；上传失败按 `UploadException.code` 翻人话；发送失败优先用 `msgMap`，**未收录的码回落到服务端原话**；另修一个同链路缺陷：服务端 `code=0` 但 `data.reply` 为空时按成功处理（评论已发出，原先会在 `resultReply.forceDelete` 处 NPE 并被 `MsgUtil.err` 当成失败，诱导用户重发）。
  - `activity/message/PrivateMsgActivity.kt`：私信图 biz 由 `BIZ_REPLY` 改为 `BIZ_MESSAGE`（`/bfs/im/`）。
  - `util/UiText.kt` + `res/values/strings.xml`：删除已无引用的「带图评论仅大会员可用喵~」（`uitext_writereplyactivity_003`）。
  - 单测：`ReplyApiTest.imageBiz_commentSharesDynamicDirectoryAndMessageUsesIm`（钉死两个 biz 值）、新增 `ApiErrorTextTest`（未登录≠凭证失效、4100001 提示换图、-509 提示频繁、未知码带码）。
- **验证**：`:app:assembleDebug`（standard + coexist）**BUILD SUCCESSFUL**；`:app:testStandardDebugUnitTest` 与 `:app:testCoexistDebugUnitTest` 均 **125 suites / 1311 tests / 0 失败 0 错误**（机器产出 `app/build/test-results/*` 计数）；本轮新增 `ApiErrorTextTest`（5 例）与 `ReplyApiTest.imageBiz_commentSharesDynamicDirectoryAndMessageUsesIm`（1 例）。
- **遗留**：12066 语义未确证；本机无在线设备（`adb devices` 为空），**未做真机复验**。复现时看日志 `debug-上传图片`（code/message/biz）与 `debug-发送评论`（服务端整包 code/message），可直接判断是"图床拒收"还是"评论接口拒收图片"。

### 3.108 新增「我的订阅」：我订阅的收藏夹 / 合集（26.11.02，用户要求照 PiliPlus 实现）

- **可见功能**：「我的」页新增「订阅」入口。列表显示我订阅的收藏夹/合集（封面、标题、UP 主、类型与视频数、失效置灰）；点收藏夹进**只读**收藏夹内容页，点合集进新的「合集详情」页；长按条目二次确认后取消订阅。
- **接口事实**（PiliPlus `lib/http/fav.dart` / `lib/http/user.dart` + 本地 `bilibili-API/docs/fav/info.md`）：
  - 列表 `GET x/v3/fav/folder/collected/list?platform=web&up_mid=&pn=&ps=20`。**`platform=web` 必带**，否则不返回合集；每项 `type=11`（收藏夹，`id`=media_id）/ `type=21`（合集，`id`=season_id），`state=1` 失效。
  - 合集详情 `GET x/space/fav/season/list?season_id=&pn=&ps=20`（返回 `info`+`medias`，**不是** `ugc_season`）。
  - 取消：`POST x/v3/fav/folder/unfav`（`media_ids`，-400 时回退单数 `media_id`，与 PiliPlus 口径兼容）/ `POST x/v3/fav/season/unfav`（`season_id`+`platform=web`），均带 `csrf`。
- **改动**：
  - 新增 `model/SubscriptionFolder.java`、`api/SubscriptionApi.java`（解析拆纯函数）、`adapter/subscription/SubscriptionFolderAdapter.kt`、`res/layout/cell_subscription_folder.xml`、`activity/user/subscription/{SubscriptionListActivity,SubSeasonDetailActivity}.kt`。
  - `MySpaceMenu` 加 `subscription` 项与跳转；`MySpaceConfig.ALL_ITEMS` 同步（`MySpaceConfigTest` 守卫通过）；manifest 注册两个 `exported=false` + `screenOrientation=locked` 的 Activity；`strings.xml` 新增 2 个 pagename + 4 个 text + 7 个 msg，**无硬编码中文**。
- **验证**：
  - `:app:assembleDebug` 两 flavor **BUILD SUCCESSFUL**；`:app:testStandardDebugUnitTest` / `:app:testCoexistDebugUnitTest` 均 **125 suites / 1311 tests / 0 失败 0 错误**（含新增 `SubscriptionApiTest` 6 例：收藏夹/合集混合解析、脏条目跳过、可选字段兜底、合集视频与总数、空/缺字段不崩）。
  - AVD `wear5`（466×466 圆屏）实测：我的 →「订阅」→ 页面标题「我的订阅」、空态「啥都木有~ 点我重试」、底部「没有更多了」；logcat 确认请求 `collected/list?...platform=web&pn=1` → `200`、`{"code":0,...,"data":{"count":0,"list":[],"has_more":false}}`（**勘误见 §3.109：该账号当时已登录过期，匿名请求也会返回这个结果，不是「0 条订阅」**），**无崩溃/无 ANR**。
- **遗留**：① 测试账号 0 条订阅，**列表渲染、点击进夹/进合集、长按取消订阅三条路径未在真机走到**，需有订阅数据的账号复验；② `/x/space/fav/season/list` 的 200 响应字段只有 PiliPlus 源码作依据（本地 API 快照没有该接口），首次用真实 season_id 跑一次后建议把实测响应补进 `docs/bilibili-api-notes.md`；③ `folder/unfav` 的参数名两种口径只做了代码级兼容，未拿到真实成功响应验证。

### 3.109 「我的订阅」补添加订阅入口 + 三处实测纠错（26.11.02 当晚，用户追问「我如何添加订阅」）

- **用户反馈**：订阅页只能看不能加。确实——§3.108 把功能范围定成「只读」，但用户要的是完整闭环。本次补入口并顺手把接口实测中发现的错误改掉。
- **新增添加订阅入口**：
  - 共享布局 `activity_simple_refresh.xml` 新增默认 `gone` 的 `subBar`（一个 `subToggle`，与 `manageBar` 同层，不进自动收回）。
  - `FavoriteVideoListActivity`：`readOnly`（别人的收藏夹）+ 已登录 + 有 media_id 时点亮「订阅 / 已订阅」；状态查 `GET x/v3/fav/folder/info?media_id=` 的 `data.fav_state`（1=已订阅）；点击走 `POST x/v3/fav/folder/fav`（`media_id`+`csrf`），已订阅时二次确认后走 unfav。查询失败整条隐藏，不显示误导性状态。
  - `CollectionInfoActivity`（视频合集详情）：自己的合集/未登录不显示；状态先读 `space/fav/season/list` 的 `info.fav_state`，读不到时回退翻「我的订阅」前 3 页找该 season；点击走 `POST x/v3/fav/season/fav`（`platform=web`+`season_id`+`csrf`）。
  - `strings.xml` 新增 3 条 text + 4 条 msg；`SubscriptionApi` 新增 `subscribeFolder` / `subscribeSeason` / `getFolderFavState` / `getSeasonFavState` 与两个纯函数解析；`SubscriptionApiTest` 增 2 例（共 8 例）。
- **三处实测纠错（都是拿真接口/真设备验出来的）**：
  1. **`x/fav/season/unfav` 是 404，正确路径带 v3**：PiliPlus `Api.unfavSeason = '/x/fav/season/unfav'` 实测 HTTP 404；`/x/v3/fav/season/unfav` 返回 -101（账号未登录），`/x/v3/fav/season/fav` 同。§3.108 的实现沿用了 PiliPlus 的错误常量，本轮已改成 v3 —— **原实现下合集取消订阅必然失败**。
  2. **收藏夹 unfav 主参数是 `media_id`（单数）**：原实现按 Web 文档先发 `media_ids` + `platform=web`，实测这几个写接口在未登录时都先报 -101 看不出差异；但 PiliPlus 的取消订阅（已在用的路径）明确发 `media_id` 且不带 `platform`。本轮把主口径改成 `media_id`（`media_ids` 只作 -400 回退），减少一次无谓失败。
  3. **AVD 登录态其实已过期（§3.108 的结论要更正）**：`collected/list` 当时返回 `code:0` 空表，被当成「已登录、0 条订阅」；复查 `x/web-interface/nav` 用设备里的 SESSDATA 请求返回 **-101 账号未登录**，`我的` 页用户信息也显示「加载失败」——即**匿名请求 `collected/list` 会返回 `code:0` + 空列表**。所以：空态验证有效，但「登录态下 0 条订阅」的判断作废；列表渲染/点击/订阅/取消仍未在登录账号上跑过。
- **`space/fav/season/list` 实测响应（补上 §3.108 遗留②）**：`GET ?season_id=9126374&pn=1&ps=20`（匿名即可）→ `code:0`；`data.info` 字段只有 `id/season_type/title/cover/upper/cnt_info/media_count/intro`，**没有 `fav_state`**；`data.medias[]` 每条 `id` 是 **avid**（如 `117292654397426`）、带 `bvid/duration/pubtime/upper/cnt_info`；**没有 `has_more`**，用 `info.media_count` 判到底。
- **验证**：`:app:assembleDebug` 两 flavor BUILD SUCCESSFUL；两 flavor 单测 **125 suites / 1313 tests / 0 失败**；AVD `wear5` 重装后实测首页/「我的」/「订阅」页均正常（共享布局加条未破坏其它页面），「订阅」页空态与请求链路同 §3.108；四个写接口用本机 HTTP 实测均为 -101（存在、需登录）而非 404。
- **遗留**：仍是登录态不足——**订阅/取消订阅的真实成功响应、以及列表有数据时的渲染与点击，仍需用户自己登录后走一遍**（复现时看 logcat `debug-订阅收藏夹` / `debug-订阅合集` / `debug-取消订阅*` 的 code/message）。

### 3.110 控制台反馈 #88 / #85：用户主页长按关注加入分组 + 评论「假发送成功」（26.11.02 傍晚，用户点名）

- **#88（建议）· 已实现**：用户主页头部的「已关注」按钮**长按** → `TerminalDialog.menu` 列出关注分组（自建分组 `tagid > 0`，已在的分组带「（已加入）」后缀），点未加入的分组即调用 `POST x/relation/tags/addUsers` 追加（**不是**移动，不会取关/移出原分组）；末项「新建分组」→ `TerminalDialog.input` 取名 → 创建后回查 tagid 再加入。未关注 / 未登录时长按不拦截，交回系统默认行为。
  - `api/FollowApi.java`：新增 `getUserFollowTags(fid)`（`GET x/relation/tag/user?fid=`，解析「分组 id → 分组名」对象）、`addFollowTagUsers(tagids, fid)`（`POST x/relation/tags/addUsers`）、纯函数 `addUsersFields` / `joinTagIds`（字段名与 `moveUsers` 不同，单测钉死）。
  - `adapter/dynamic/UserDynamicAdapter.kt`：`followBtn.setOnLongClickListener` + 四个私有方法（拉分组/弹菜单/新建后加入/加入请求）；所有网络请求都在 `CenterThreadPool` 后台线程、提示回主线程。
  - `strings.xml` +7 条（`uitext_userdynamicadapter_022~028`），`UiText.kt` 同步登记；`FollowApiAddUsersTest` 8 例。
- **#85（bug）· 根因与修复**：反馈原文「发不了评论，显示发出去了，但是一刷新又没了（那个视频只有我一个人评论）」。根因是 `POST /x/v2/reply/add` 会**在评论进入公开列表之前**就回 `code=0`（审核 / 列表同步延迟），旧实现把「接口成功」当成「评论区可见」，先本地插一条再报「发送成功」，一刷新就消失。
  - `api/ReplyApi.java`：`SendReplyResult` 增加 `rpid`（取 `data.rpid`，`data.reply` 为 null 时也能用）；新增 `isReplyVisible(oid, rpid, type)`（`getRepliesLazy(mode=2)` + `seek_rpid` 定位，0 / 0.4s / 1.0s 三次；三次都查不到再兜底查一次「按时间第一页」，防 `seek_rpid` 在个别评论区不生效）与纯函数 `containsReplyId`（含楼中楼）。
  - `activity/reply/WriteReplyActivity.kt`：一级评论 `code=0` 后先核实可见性；查不到 → 提示「评论已提交，但评论区暂时查不到（可能正在审核）」，**不插入本地列表、不 finish**（原文保留可重试/稍后刷新）；查到才走原来的「发送成功 + ReplyEvent + finish」。楼中楼回复不做核实（`seek_rpid` 对子评论不可靠），保持原行为。
  - `strings.xml` +1 条（`uitext_writereplyactivity_022`）；`ReplyApiTest` +4 例（顶层 / 楼中楼 / 不存在 / null 与非法入参）。**额外延迟**：每条一级评论最多多 3 次列表查询（正常评论第一次即命中，多一次请求约数百毫秒）。
- **验证**：`:app:assembleDebug` 两 flavor **BUILD SUCCESSFUL**；`:app:testStandardDebugUnitTest` / `:app:testCoexistDebugUnitTest` 各 **126 suites / 1325 tests / 0 失败 0 错误**（基线 125/1313 + 本轮 12 例）。
- **遗留（必须说清）**：本机唯一在线设备是 AVD `wear5`，**未登录 B 站账号**，因此 ① 长按加入分组 / 新建分组 / 追加成功的真实链路、② 评论审核场景下「查不到 → 提示暂未确认」的真实表现，**都没有端到端跑过**；两条判定都靠纯函数单测 + 代码走查。真机复现时看 logcat：分组走 `post-url`（`tags/addUsers`）与返回 code；评论走 `debug-发送评论`（服务端整包）+ 新增的 `debug-评论可见性`（三次查询结果）。

### 3.111 缓存页（含文件夹）新增多选批量操作（26.11.02 傍晚，用户要求）

- **需求**：缓存页（顶层与文件夹内）能一次处理多条——批量删视频、批量更新弹幕、批量移动 / 移出文件夹、批量拆散文件夹；下载列表页（`DownloadListActivity`）不加多选。
- **入口**：`res/layout/activity_simple_main_refresh.xml` 新增默认 `gone` 的 `multiSelectBar`（这个布局被推荐 / 热门 / 排行 / 收藏 / 缓存五个一级页共用，只有 `LocalListActivity` 点亮它）；非多选态只有一个「多选」，多选态变「已选 N 项 / 操作 / 退出」；一条可选条目都没有时整条隐藏，进入多选时提示「点条目勾选，再点「操作」」。
- **勾选实现**：`CacheListAdapter` 加 `selectionMode` / `selectedVideoTitles` / `selectedFolderNames` / `onVideoToggle` / `onFolderToggle`；多选态下条目点击与长按都变成勾选（不再播放、不进文件夹、不弹单条菜单），勾中项正常亮度 + 标题前「✓ 」，未勾项 `alpha = 0.5` 压暗 + 全角空格占位（与收藏夹多选同一套视觉）。勾选集合由页面持有（`LinkedHashSet<String>`，视频按标题、文件夹按名称），刷新重扫后仍对得上；`onAttachedToRecyclerView` 里关掉 `supportsChangeAnimations`（否则取消勾选的交叉淡入会把 alpha 改回 1，收藏夹多选踩过同样的坑）。
- **菜单四类**：全视频（顶层）删除视频 / 更新弹幕 / 添加到文件夹；全视频（文件夹内）删除视频 / 更新弹幕 / 移出文件夹；全文件夹拆散文件夹；**混选不弹菜单**，只提示「文件夹和视频不能一起操作，先取消一种」。菜单走 `TerminalDialog.menu`（标题「已选 N 项」，删除项是危险色）；一条没勾时「操作」`alpha = 0.5` 并提示「先选几条吧~」。
- **复用单条逻辑**：`LocalListActivity` 把单条删除抽成 `deleteVideoFiles(video)`、更新弹幕抽成 `updateDanmakuFor(video)`，批量与单条共用（删除仍按「扫出这条时的根」算路径、只删真实存在的目录、**只有真删掉才摘文件夹记录**）；批量执行都在 `CenterThreadPool.run` 里跑，完成后**退出多选 → `refresh()` → 提示结果**。
- **文案**：删除确认「确定删除选中的 N 个视频吗？此操作不可恢复。」→「已删除 N 条」/「已删除 X 条，Y 条失败」；更新弹幕「正在更新弹幕...」→「弹幕更新成功」/「弹幕更新完成：成功 X 个，失败 Y 个」；移动「已移动到「文件夹名」」、移出「已移出文件夹」；拆散确认「确定拆散选中的 N 个文件夹吗？视频会移回未分类区域。」→「文件夹已拆散」。`strings.xml` 新增 `uitext_locallistactivity_023~031`（024 / 028 / 029 / 030 / 031 带占位符），`UiText.kt` 同步登记。
- **返回键**：多选态下返回键只退出多选、不离开缓存页；文件夹里点顶栏返回箭头同样先退多选再退回未分类区（`exitFolder()` / `enterFolder()` 里都有 `if (selectionMode) exitSelectionMode()`）。
- **验证**：`:app:assembleDebug`（standard + coexist 两 flavor）**BUILD SUCCESSFUL**；`:app:testStandardDebugUnitTest` **全绿**；`:app:testCoexistDebugUnitTest` 有 2 例失败，都在 `com.RobinNotBad.BiliClient.util.AccountManagerTest`（`removeAccount_removesOtherAccount` 断言失败、`applyCredentials_rejectsMidCookieMismatch_andWritesNothing` 报 `Method println in android.util.Log not mocked`），该测试类与相邻会话的账号改动（未跟踪文件，见 §3.112）同批产生，与本条无关。
- **AVD 实测**（`wear5`，Android 14 / 466×466 圆屏；coexist Debug 包由本轮源码构建，夹具为 6 个 40.0MB 缓存目录 + 2 个文件夹，逐项见 `docs/review/real-device-regression-checklist.md` §十七）：
  - ✅ 底部条只出现在缓存页（`推荐` / `全站排行榜` 页 dump 无 `multiSelectBar`）；默认态只有一个「多选」，多选态变「已选 N 项 / 操作 / 退出」，进多选提示「点条目勾选，再点「操作」」。
  - ✅ 勾选视觉与交互：点 / 长按均只勾选（长按不再弹单条菜单、不跳播放），勾中项标题前「✓ 」、取消变全角空格占位，计数与选中数一致；一条没勾点「操作」只在 logcat 里提示「先选几条吧~」，不弹菜单。
  - ✅ 菜单四类：全文件夹 → 只有「拆散文件夹」；顶层全视频 → 删除视频 / 更新弹幕 / 添加到文件夹；文件夹内全视频 → 删除视频 / 更新弹幕 / 移出文件夹；**混选只提示「文件夹和视频不能一起操作，先取消一种」且不弹菜单**。
  - ✅ 批量删除：确认框文案「确定删除选中的 2 个视频吗？此操作不可恢复。」→ 确认后提示「已删除 2 条」，磁盘上两个缓存目录**确实消失**，自动退出多选并刷新。
  - ✅ 批量移动：列表标题「移动到文件夹」、条目带各自视频数 → 选中后提示「已移动到「合集一」」，`.folders_info.json` 真的把两个标题加进了该文件夹。
  - ✅ 移出文件夹：提示「已移出文件夹」，json 里对应标题被移除。
  - ✅ 批量拆散：确认框文案「确定拆散选中的 2 个文件夹吗？视频会移回未分类区域。」→ 提示「文件夹已拆散」，`.folders_info.json` 变空数组，原来夹内的视频回到未分类区。
  - ✅ 批量更新弹幕：流程走通——先提示「正在更新弹幕...」，全部跑完提示「弹幕更新完成：成功 0 个，失败 2 个」（该 AVD 未登录 B 站账号，接口必然失败，属预期），随后自动退出多选、列表刷新、App 不崩。
  - ✅ 退出多选后行为恢复：长按视频重新弹单条菜单（更新弹幕 / 切换分辨率 / 添加到文件夹 …），点击视频进入 `PlayerActivity` 播放。
  - ⚠️ **返回键未能在该 AVD 验证（BLOCKED，非本次回归）**：多选态按 BACK 会直接回表盘；对照测试发现**本次之前就有的**「文件夹内按 BACK 退出文件夹」在该 AVD 上一样失效，说明是模拟器把 BACK 当窗口层 dismiss（`values/themes.xml` 各主题的 `android:windowSwipeToDismiss`，`activity/base/BaseActivity.kt` 注释也点明「窗口层直接 finish，不走 onBackPressed」），不是本次代码问题；多选态的 BACK 分支与收藏夹多选同款，待手机 / 真机复验。
  - ⚠️ 圆屏适配（结构判读）：底部条 `[23,344][443,440]`，466 圆屏在条中心高度处可见宽度约 x∈[63,403]，「退出」文字（中心 x≈381、宽约 26px）落在圆内不被切；按钮右侧超出圆外的部分只裁到背景，而背景取 `?android:attr/colorBackground` 与页面同底色，**视觉无割裂**。

### 3.112 账号切换 / 特殊登录的凭证隔离与多账号导入导出（26.11.02 夜，用户要求）

- **需求**（用户原话方向）：① 账号切换导致凭证混乱；② 特殊登录凭据的账号隔离混乱；③ 特殊登录支持一键导入 / 导出多个登录者信息且不串号。
- **根因**：① 切号只重写 SP 与 Cookie，不区分「登录态版本」，切号前发出的请求返回时其 `Set-Cookie` 仍被合并进新账号（污染 csrf 快照 / 覆盖 SESSDATA），且按账号的缓存（大会员状态 / 未读红点 / 追番快照）从不清理；② 特殊登录导入缺 `access_key` 时沿用上一个账号的 `access_key`，`mid` 与 Cookie 里的 `DedeUserID` 也不校验；③ 导出只有「当前账号单条平铺 JSON」，多个账号只能一个个导。
- **代际隔离**（`util/NetWorkUtil.java`）：新增 `private static volatile long accountGeneration`；4 个请求构造点统一走 `newTaggedRequestBuilder()`，给 `Request` 打 `tag(Long.class, accountGeneration)`；`saveCookiesFromResponse()` 在 `synchronized (NetWorkUtil.class)` 内用 `isStaleGeneration(requestGeneration, accountGeneration)` 判断，旧代际的 `Set-Cookie` 直接丢弃并 `Logu.d("saveCookiesFromResponse", "丢弃旧账号代际的 Set-Cookie：req=… cur=…")`；新增 `getAccountGeneration()` / `bumpAccountGeneration()` / `applyAccountCookies(cookies)`（锁内 `generation++` + 写 SP / cache / headers）。写登录态一律走 `applyAccountCookies`，不再直接 `setCookiesString()`。
- **统一凭据入口**（`util/AccountManager.java`）：新增 `applyCredentials(mid, name, cookies, refreshToken, accessKey, avatar[, csrfHint])`：校验 `mid>0`、cookies 非空、`DedeUserID` 必须等于 `mid`（否则 `Logu.w` 拒绝且不写任何东西）；csrf 取实时 `bili_jct`、回退 `csrfHint`、否则空串；一次 `SharedPreferencesUtil.edit` 写完 `mid / cookies / refresh_token / access_key / csrf / current_account_mid / cookie_refresh / setup`（**缺 `access_key` 会清空旧值**）；随后 `NetWorkUtil.applyAccountCookies()`、更新账号快照列表、清按账号缓存。`switchToAccount()` 改为转调它；新增 `clearCurrentLogin()`（清 5 项凭证 + 代际 +1 + 清缓存 + 通知 `CookieRefreshScheduler`），`removeAccount()` 改为**当前账号拒绝删除**（否则启动同步会把它加回来）。`saveCurrentAccount()` 用「起始 mid + 起始代际」双守卫：网络回来后若 SP 已换号或代际变了，整笔丢弃，不再把旧账号信息写进新账号的列表。
- **按账号缓存**：`AccountStateStore.onAccountChanged()`（清大会员状态缓存）、`CookieRefreshScheduler.onAccountChanged()`（重置上次尝试与退避、清 `-101` 标记）；`AccountManager` 同时 `removeValue` 掉 `DYNAMIC_UPDATE_NUM` / `MESSAGE_UPDATE_NUM` / `BANGUMI_UPDATE_SNAPSHOT`。
- **多账号导入导出**（`util/SpecialLoginParser.kt` + `activity/settings/login/SpecialLoginActivity.kt`）：导出改为 `AccountManager.exportAllAccounts()`，信封 `{"format":"rebili.accounts","version":1,"exported_at":…,"accounts":[…]}`，当前账号用 SP 实时凭证覆盖快照（也可单条复制）；`parseAny()` 同时接受「单条对象 / 裸数组 / 带 `accounts` 的信封」；导入单条 = 完整登录（`applyCredentials` → 跳启动页），导入多条 = 只写账号列表（`AccountManager.importAccounts()`，**不切当前账号**），toast「已导入 N 个账号（新增 X，更新 Y）」。校验：每条的 cookies 必须含 `DedeUserID` 且有 `refresh_token`；自带 `mid` 与 `DedeUserID` 必须一致；重复 mid 保留最后一条；**一条坏数据整批拒绝**（提示第几个账号有问题）。「粘贴并登录」走同一套解析，因此也支持多账号。
- **登录 / 退出路径收口**：`QRLoginFragment`（TV 扫码 + Web 扫码）、`PasswordLoginFragment`、`SMSLoginFragment` 全部改为 `applyCredentials(...)` + `saveCurrentAccount()`，TV 扫码的 Cookie 从空串开始拼（避免带上一个账号的 SESSDATA）；`SplashActivity.resetLogin()` 与 `MySpaceActivity.handleLogout()` 改为 `AccountManager.clearCurrentLogin()`（**顺带清掉原来漏清的 `access_key`**，并升代际防止在飞请求把 Cookie 写回来）。
- **入口**：账号切换页底部新增「导入账号数据（支持一次性导入多个账号）」，长按删除对当前账号改为提示「当前登录的账号不能删除，请先切换到其他账号」。
- **验证**：`:app:assembleDebug` BUILD SUCCESSFUL；`:app:testStandardDebugUnitTest` / `:app:testCoexistDebugUnitTest` 各 **127 suites / 1357 tests / 0 失败**（新增 `AccountManagerTest` 22 例 + `SpecialLoginParserTest` 10 例批量解析）。模拟器（AVD `wear5`，466×466）实测：账号切换页入口与文案 ✓、长按当前账号被拒 toast ✓、导出页内容含 `format: rebili.accounts` 与当前账号实时凭证 ✓、导入页可进入且能敲入多账号 JSON ✓。
- **遗留（必须说清）**：批量导入的「点登录 → toast 计数 → 账号列表多出条目」最后一步 **未在模拟器上跑通**——实测期间另一个并行会话持续抢占同一台 AVD（反复拉起 `com.RobinNotBad.BiliClient.coexist`），最后模拟器掉线；该逻辑由 `AccountManagerTest.importAccounts_*` 与 `SpecialLoginParserTest.parseAny_*` 覆盖，真机复测时重点看 toast 计数与账号列表条数。
- **并发注意**：本轮改动与相邻会话的缓存页多选（§3.111）在同一工作区并行，本条只提交本功能涉及的文件。

### 3.113 圆屏适配补两块：长按二级菜单弹窗、两个伪装页（26.11.02，用户点名）

- **缘起**：用户报「部分长按产生的二级菜单（例如长按缓存的视频）没有圆屏适配而显示不全；伪装界面未做圆屏适配」。
- **定位①（长按二级菜单）**：长按缓存里的视频 / 文件夹弹的是 `TerminalDialog.menu`（`activity/video/local/LocalListActivity.kt:291` 与 `:311`），它落在**独立的 Dialog 窗口**里；§3.93 的实验效果「圆屏列表」只对本 Activity 的 `decorView` 生效，管不到 Dialog 窗口，所以圆屏上这些菜单的左右两端会被表盘切掉。全工程没有一处 `PopupWindow`（已全仓 grep），二级菜单不是 Dialog 就是 Activity，这一条覆盖 Dialog 那一类。
- **改法①**：`util/view/DialogSizing.kt` 增加「按表盘内接矩形收窄」——`ROUND_FIT_MARGIN = 0.97`，可用直径 `d = min(屏宽, 屏高) × 0.97`；弹窗宽仍取可用宽的 90%，但**再夹到内接正方形**（`d / √2`），高度预算按 `√(d² − w²)` 给（宽高平方和 ≤ `d²` 才在圆内），高度不够时内容区滚动，长列表不再把弹窗撑出屏幕。生效判据只看 `SettingsKeys.UI_ROUND`（「圆屏适配」），与「圆屏列表」实验开关解耦。
- **定位②（伪装页）**：`BluetoothDisguiseActivity` / `CalculatorDisguiseActivity` 是 `AppCompatActivity`，不在 `BaseActivity` 体系里，页边距那套（`paddingH_percent` / `paddingV_percent` + 圆屏额外下边距）自然没落到它们身上。
- **改法②**：把页边距算法抽成纯函数 `util/view/RoundInsets.compute(屏宽, 屏高, padH%, padV%, round)` + `util/view/ScreenMetrics`（取真实屏幕像素），`BaseActivity` 与两个伪装页共用同一份结论；伪装页把 inset 加到自己的根布局 padding 上。**顺带修掉一处老错**：旧 `DisguiseRoundPadding` 把百分比直接当 dp 用，466px 的圆屏上算出的是 5dp 而不是 5%。
- **验证**：`:app:assembleDebug` 双 flavor 通过；两 flavor 单测各 **109 suites / 1133 tests / 0 失败**（新增 `DialogSizingTest` 19 例、`RoundInsetsTest` 5 例，钉住真实几何：380×380 屏 + 5%/3% 时老算法给出的 `307×336` 试算 `207145 > 144400` 必须被拒，收窄到内接正方形后通过）。
- **未验**：模拟器不可达（MuMu `127.0.0.1:7555` 与 `16384/5555/21503/62001` 全部拒连、`adb devices` 为空），圆屏上的实际观感（弹窗不再被表盘切、伪装页边距与正常页一致）没能在设备上复核，结论基于纯函数单测 + 代码路径核对。

### 3.114 「虚拟合集」点哪一条都从第一条开始播（26.11.02，用户点名）

- **缘起**：用户报「播放虚拟合集的视频时，无论选的哪一个实际上播放的都是第一个」。
- **根因（两处，都在起播下标的推算上）**：
  - **本地缓存页**：`LocalListActivity.playVirtualCollection` 原循环对文件夹内**每一个**视频都往 `pagenames` 塞一项，但只有 `videoFileList != null` 时才往 `videoUrlList` 塞——两个列表从第一个不可播条目起就错位；起播下标又是用 `v == videoList[startVideoIdx]` 的**对象同一性**在过滤后的列表里找，错位后自然指回第 0 项。
  - **收藏夹**：`FavoriteVideoListActivity.playFavoriteVirtualCollection` 只用 `v.aid == startAid` 反查下标，长按/点击传进来的 `startPosition` 被完全丢掉；`aid` 为 0 或重复时下标恒为 0。
- **改法**：新增纯函数 `util/VirtualCollectionStart`（`resolveOnline(startPosition, startAid, aids)`：点击位置优先，位置与 aid 不矛盾就用位置，只有位置不合法才退回按 aid 查；`resolveLocal(clickedInFullList, collectionFullIndices)`：按下标映射表换算）。两个页面都改成**一趟循环**同时建齐 4 个列表 + 一份「全集下标 ↔ 合集下标」映射表，不可播条目整条跳过（不再产生空 pagename 与错位）；换算不出起播项时明确提示（新增 `uitext_locallistactivity_034` / `uitext_favoritevideolistactivity_025`）并返回，**不再默默播第一条**。
- **验证**：`:app:testStandardDebugUnitTest --tests "com.RobinNotBad.BiliClient.util.*"` `BUILD SUCCESSFUL`（`VirtualCollectionStartTest` 8 例：位置优先、位置与 aid 矛盾、aid 为 0、aid 重复、位置越界、本地映射缺项）；`:app:assembleDebug` 双 flavor、`:app:assembleRelease` 全通过。
- **未验**：用户的实际触发路径没能复现，改为逐条核对三条入口的代码路径（缓存文件夹连播 / 收藏夹点击即播 / 播放页选集切换），模拟器不可达。播放页内部换页链路（`switchToOnlinePage` 用 `cids[pageIndex]` 取流、`doSwitchPage` 落地 URL 与标题）本轮复核后确认本来就是对的，未改。

### 3.115 菜单「退出」改成可排序、可关闭（26.11.02，用户要求）

- **缘起**：用户要求「菜单退出按钮可调顺序与启用」。26.10.31 加的退出按钮是硬编码在菜单最下方的一枚 `MaterialButton`（§3.91），不受「菜单设置」管控。
- **改法**：`util/MenuConfig.kt` 新增动作项 key `EXIT = "exit"`，进 `ALL_ITEMS`（于是能排序、能关掉、默认启用且压在列表最后），但**不进 `FIXED_ITEMS`**、**不作启动页候选**（选了它开机就收摊：`resolveStart` 既不沿用也不兜底到它、`loadStart` 读到旧值改回默认、菜单设置里点它只弹提示 `msg_menu_exit_not_start`）。`MenuActivity` 删掉硬编码按钮，改在菜单列表循环里渲染：文案取 `R.string.btn_exit`，点击直接 `exitAppCompletely()`（不走 `killAndJump`，它要往页面栈里跳）。
- **老数据迁移（关键）**：`menu_enabled` 里没有 `exit` 有两种含义——「升级前的老数据」与「用户自己把它关掉了」。用新键 `menu_exit_state`（`SharedPreferencesUtil.MENU_EXIT_STATE`，保存菜单配置时写 `"1"`/`"0"`）区分：缺项且没写过 → 补在末尾（默认启用）；缺项且写过 → 保持隐藏，不再偷偷加回来。`architecture-map.md` §3.1 记过「新增菜单 key 老用户不会自动出现」，这是第一个带迁移的新 key。
- **验证**：`MenuConfigTest` 34 例（新增「老数据缺 exit 会补上并写回」「用户关掉后不再加回」「保存时写标记」「exit 不能当启动页、旧启动页值会被改回」）；两 flavor 单测各 **109 suites / 1133 tests / 0 失败**；`:app:assembleDebug` 与 `:app:assembleRelease`（standard / coexist 各 4 个 ABI 包，6.2~12.3MB）均 `BUILD SUCCESSFUL`。
- **未验**：模拟器不可达，菜单页面上的拖动排序 / 关掉退出项、以及升级路径（旧 `menu_enabled` 不含 exit）没能在设备上走一遍，靠单测 + 代码路径核对。

### 3.116 远程合并冲突没解干净：资源重名 + `BaseActivity` 残留旧边距算术（26.11.05，用户报构建失败）

- **现场**：用户把远程 `main`（26.11.02 那一批：缓存页排序/搜索、缓存页多选批量、账号代际隔离、订阅、播放器三项…）合并进来后 `assembleDebug` 直接失败。
- **残留①（资源重名）**：`:app:mergeCoexistDebugResources` 报 `Found item String/uitext_locallistactivity_023 more than one time`——两边各自都从 `_023` 往后加：远程加的是多选批量（`_023` 操作 / `_024` 已选 %1$d 项 / `_025` 退出 / `_026` 点条目勾选，再点「操作」，`_027`~`_031`），我加的是虚拟合集（进入合集提示 / 该文件夹内没有可播放的视频 / 无法定位起始视频 / 合集页名）。两边 `standard` 与 `coexist` 都合并同一份 `strings.xml`，重名两个 flavor 一起挂。修法是**挪我这四条**到下一个空位 `_032`~`_035`（远程那几条的语义已被新合并进来的代码引用，不该动它们），同步改 `LocalListActivity` 四处引用。
- **残留②（编译不过）**：`:app:compileCoexistDebugKotlin` 报 `BaseActivity.kt:167` 起 `Unresolved reference 'paddingH_percent'` / `Overload resolution ambiguity`。原因是两边改了同一段：远程把「界面边距」从 `DecorView` 根挪到内容容器（新增 `userPaddingH/T/B` 字段 + `applyUserPadding()` + `onContentChanged` 里重施，图的是触摸与系统栏 inset 互不覆盖），而我这一侧已把那整段百分比算术换成 `RoundInsets.compute(...)`、顺手删掉了 `paddingH_percent` / `paddingV_percent` 两个局部量；解冲突时把两边都留下了，于是既在算已经不存在的局部量、窗口尺寸算了两遍、还同时往 `DecorView` 与内容容器加边距。
- **改法**：这段只留一份结论——`RoundInsets.compute(屏宽, 屏高, padH%, padV%, 圆屏)` 算出 `insets` → `window_width/height` 取它，`userPaddingH/T/B` 取它，交给远程的 `applyUserPadding()` 加到内容容器上；删掉 `DecorView` 那次 `setPadding` 与整段旧算术。两边的意图（统一算法 / 边距不进 DecorView）都保住。
- **顺带（文档）**：`fix-progress.md` 合并后我这三条与远程的 §3.101~§3.103 撞号，而远程那几个号已被 `docs/review/real-device-regression-checklist.md` §435 引用，所以把本侧三条挪到 §3.113~§3.115 并改掉 §1 里的交叉引用；`docs/FEATURES.md` 与 `docs/architecture-map.md` 的合并结果与两侧各自 diff 逐文件比对完全一致，无需处理。
- **验证**：`:app:assembleDebug`（standard + coexist）`BUILD SUCCESSFUL`；`:app:testStandardDebugUnitTest` / `:app:testCoexistDebugUnitTest` / `:app:assembleRelease` 结果见 §1 的单测计数。
- **未验**：模拟器不可达（MuMu `127.0.0.1:7555` 拒连），圆屏上「边距加在内容容器」的实际观感（远程改法）没能在设备上复核。

### 3.117 搜索设置里「视频搜索（始终启用）」显示成「锁定关」（26.11.05，用户点名）

- **现场**：用户报这一项开关看着是「锁定的关」，像视频搜索被关掉了。
- **根因**：`activity_setting_search.xml` 里这一项只写了 `android:enabled="false"` + `android:clickable="false"`，**没有 `android:checked`**，代码侧也从不读它——`SearchActivity` 的类别表里 `"video"` 恒为 `true`，压根没有对应它的设置项，所以它一直显示「关」。它是「始终启用」的展示项，本该锁成开。
- **改法**：布局加 `android:checked="true"`（保留 `enabled=false`，仍然点不动），并在 `SettingSearchActivity` 膨胀完成后再兜一次 `isChecked = true`（防个别 AppCompat 版本忽略 xml 的 `android:checked`）。行为没有任何变化，只是显示对了。
- **验证**：`:app:assembleDebug` 通过；纯布局 + 一行赋值，没有逻辑分支可测。
- **未验**：模拟器不可达，实际观感（锁定开）没能在设备上看。

### 3.118 关于页开发者列表新增 Kuinone（26.11.05，用户要求）

- **内容**：`AboutActivity.DEVELOPERS_RE` 追加一项——名字 `Kuinone`、签名 `Kuinone可能是迄今为止唯一一个符合RESTful API规范的苯醌`、UID `475731079`；头像由用户提供的 `misc/kuinone.jpg`（512×511）方裁成 256×256、转成 `res/mipmap-nodpi/avatar_kuinone.webp`（WebP q88，18KB），与其余 8 张头像同目录同格式。点击卡片照旧跳该 UID 的用户页（走 `BiliTerminal.jumpToUser`）。
- **放在哪一列**：本仓库是 RE 版，所以放进「RE：哔哩终端主要开发者」（`about_dev_trae`）那一列；要改挂到「主要开发者 / 联合开发者」，改一行列表即可。
- **验证**：`:app:assembleDebug` 通过（新资源 `R.mipmap.avatar_kuinone` 能解析）。
- **未验**：模拟器不可达，卡片实际渲染（头像圆裁、长签名的跑马灯）没能在设备上看。
- **现象**（用户原话方向）：「点这一集，界面却写着下一集」。
- **公开侧根因**：`OptionalFeatures.interceptBangumiPlay()` 只把 `episodeIndex`（`BangumiInfoFragment.selectedEpisode`，即该 section 列表里的**位置**）交给可选功能实现，而位置 != 集号——两边列表只要有一边多夹了 PV/预告/第 0 话，界面标题就整体偏移一位。
- **公开侧改动**：门面增加第 5 个参数 `episodeLabel`，`BangumiInfoFragment.startPlay()` 传 `episode.title`（`BangumiApi.analyzeEpisode` 已把 B 站 `title` 映射到 `episode.title`，形如「第5话」）；反射取方法时同步加 `String::class.java`。KDoc 写明「只看下标会串集」。私有实现侧同步接收该参数（本仓库只有门面与挂点）。
- **验证**：`:app:assembleDebug` BUILD SUCCESSFUL；`:app:testCoexistDebugUnitTest --tests "com.RobinNotBad.BiliClient.feature.*"` 全绿（含 `FeatureHolderContractTest` 对新签名的断言）。模拟器 e2e：`third_party_episode_label=第3话` 时日志出现「集名=第3话」，界面标题不再是下标推算值。
- **已知遗留**：标题匹配仍按「双向包含」，同系列不同季可能互相命中（改名侧下一步加季数/年份约束）。

- **需求**（用户原话）：「算了，咱们也和 kazumi 一样的逻辑算了，不搞静默换源了」（Kazumi 式选源；实测同机同网时，静默换源链路从点击到首帧约 9.2s，而 Kazumi 点选集到起播约 7.1s，且用户看不到在等什么）。
- **公开侧改动**：`BangumiInfoFragment` 删掉详情页加载完成时的 `OptionalFeatures.warmUpBangumiSource(...)` 预热调用（面板化后预热没有意义，反而会和用户实际操作抢线程）；`startPlay()` 的注释改为「会员集会打开播放来源面板」。门面 `interceptBangumiPlay` / `warmUpBangumiSource` 签名不变（私有实现侧改行为）。
- **私有侧改动**（不在本仓库）：`ThirdPartySourceActivity` 重写成「播放来源」面板——13 个源并发检索、逐源显示 `N 个结果 / 无结果 / 检索失败`、可展开选条目、再选剧集；`interceptBangumiPlay` 只负责打开面板；删除「先起播放页再背后解析」的 `playPendingInPlayer`，剧集页改为在透明中转页解析出地址后才起播放器。
- **验证**：`:app:assembleDebug` BUILD SUCCESSFUL；模拟器（Android 37）点面板 → DM84 → 剧集 1…12 → 第 1 集 → 嗅探命中 m3u8 → 播放器 1920×1080 起播，全程没有「静默跳播放页」。
- **已知遗留**：面板首屏会有 2~3 个慢源长时间停在「检索中…」（总预算不设上限，保证用户能看到全部结果）；「主关键词 + 别名」两轮搜索仍是串行。

### 3.119 番剧搜索「先出 B 站结果、再补 BGM 卡片」，不再整页等补充数据（26.11.05，用户要求）

- **现象**：番剧分类搜索要 8–10s 才整页出现（同机 Kazumi 只需 0.9s）；期间刷新圈一直转，用户以为卡死。
- **根因**：`SearchBangumiFragment.continueLoading()` 把 B 站 wbi 搜索（约 1.2s）与 `OptionalFeatures.appendBangumiSearchExtras()`（BGM 反代搜索 + 最多 8 次详情补集数，串行多反代，最坏十几秒）串在同一个后台任务里，两者跑完才 `runOnUiThread` 贴列表。
- **公开侧改动**：B 站结果先 `appendToAdapter(page, list)` 上屏并立即 `setRefreshing(false)`；再把结果副本交给补充数据源，返回后只追加新增卡片（新增 `appendToAdapter(page, items)` 供两段共用）。失败仍走 `loadFail` 兜底。
- **私有侧改动**（不在本仓库）：`BgmApi` 给三个反代加 5 分钟失败短路表，挂掉的反代不再每条请求各等 6s。
- **实测**（MuMu，Android 37）：切到「搜索-番剧」2s 时 4 张 B 站卡片已在屏上、刷新圈已停；BGM 独有卡片（「约会大作战F Last Date」）约 5s 时追加在末尾。
- **遗留**：`api.kazumi.fyi` 的 `/v0/search/subjects` 需要签名（未签名 401，签名钥匙由 Kazumi 构建期注入），因此 BGM 搜索仍以 `fxbgm.tv` 打头；补充数据本身仍是串行反代，可再并行化。

### 3.120 自动跳过片头片尾把「视频分段」当片尾一直跳（26.11.05，控制台反馈 #99 / #34）

- **反馈**：#99（26.11.02，OPPO OWW212，SDK 30）「开启『自动跳过片头片尾』时观看 BV1U74w6XEbB 会一直在跳过片尾」；#34（26.10.24）「跳过片头片尾会因为视频分段也跳过」。两条同根，用户要求「解决这一类问题」而不是单条。
- **根因**：`x/player/wbi/v2` 的 `data.view_points[].type` **是分段类型，不是片头片尾类型**。番剧/影视 EP 数据的 `type == 1/2` 才带片头片尾语义；UGC 稿件的 `view_points` 是 up 主或平台自动生成的**视频分段（章节）**。实测 `BV1U74w6XEbB`（aid=117546476966330 / cid=43264904750，单P 129s）解开是 10 段普通章节，**`type` 全部为 `2`** → 旧实现 `TYPE_ED = 2` 把它们全当片尾 → 250ms 进度定时器每轮都命中 → 「一直在跳过片尾」。接口侧结论记在 `docs/bilibili-api-notes.md` §12，架构侧记在 `docs/architecture-map.md` §7.7。
- **修法（一道闸）**：自动跳过**只对 PGC 生效**。
  - `api/PlayerApi.buildTerminalPlayerIntent` 给播放器 Intent 补 `is_bangumi`（取自 `PlayerData.isBangumi()`），所有入口自动带上；
  - `PlayerActivity` 读成 `pgcPlaybackFlag`（`is_bangumi || epid > 0`，**不看 seasonId**，两者都没有按 UGC 处理），`isPgcPlayback()` 供 `loadViewPoints()` 调 `ViewPointSkip.buildSegments(raw, allowAutoSkip)`：UGC 直接空表，自动跳过彻底不参与；
  - 「显示视频分段」按钮、分段选择卡片、手动跳章节**一行未动**，UGC 用户照旧可用；
  - 引导 `maybeShowSkipGuide()` 也只在 PGC 里弹，UGC 不再被劝「开启」。
- **为什么不用 `content` 白名单当闸**（曾试过，已否决）：番剧片段的 `content` 常为空串或「1-1」「正片」「简介」，按标题过滤会把真片头挡掉，用户反而觉得「开了没用」；也挡不住「预告」类标注。`ViewPointSkip.isOpEdLabel()` 只保留作**诊断**，不参与过滤；单参 `buildSegments(raw)` 回到旧口径（只看 type + 区间 + 时长上限）。
- **测试**：新增 `app/src/test/java/com/RobinNotBad/BiliClient/player/ViewPointSkipChapterTest.kt`（18 条，含 #99 十段样本逐段断言、`allowAutoSkip=false` 必为空表、`segmentAt(7.0)` 命中「泄漏源」复现旧现象、0~129s 按 250ms 逐点全 null、真片头片尾 15/95 与 1380/1440 仍保留、`isOpEdLabel` 诊断语义）；`ViewPointSkipTest.kt` 原有 12 条未改、另加 4 条。`:app:testStandardDebugUnitTest` 全绿（142 suites / 1501 tests / 0 失败 0 错误 0 跳过）。
- **已验证**：`./gradlew.bat :app:assembleDebug --rerun-tasks` BUILD SUCCESSFUL（1m2s）；单测全绿；接口层已用 curl 实拉两条接口确认 `type` 语义；MuMu（Android 12 / x86_64）安装 `app-standard-x86-debug.apk` 成功。
- **未验证（推测）**：`isPgcPlayback()` 是 `PlayerActivity` 私有方法，纯 JVM 测不到，**端到端「UGC 不再自动跳片尾 / 番剧仍跳」需要在有登录态的机器上跑一遍**（本机模拟器无登录态）。
- **遗留**：①`pgcPlaybackFlag` 只在 `onCreate` 写一次，同一个 Activity 会话里跨到另一条视频（合集 / 收藏夹混排）会继承入场时的判定——播放器目前每次都重新起 Activity，暂不触发，但若将来做「不重启 Activity 换视频」必须一并改成按 aid/epid 重算；②要把 `isPgcPlayback()` 纳入纯 JVM 单测，需把判据抽到 `player/` 下成纯函数（比如 `PlayerPlaybackKind.fromExtras(...)`），本轮没做。

### 3.121 设置页搜索框取消固定 + 缓存合集按合集顺序排列（26.11.06，控制台反馈 #97 / #98）

- **反馈与拍板**：#97（26.10.27，HUAWEI HWMDS）「设置里的搜索框调小一点 / 专门出一个设置」——维护者拍板**「取消固定即可」**，不做折叠交互。#98（26.11.02，OPPO OWW211）「缓存一整个合集后顺序混乱、找不到 1~10 集」——维护者补充：**反馈本意是排序**，不是「合集页点缓存不能直接播」。
- **#97 改法**：`res/layout/activity_setting_main.xml` 把 `@id/search_input` 从「顶栏之下、scrollView 之外」挪进 `RotaryScrollView` 内容的 `LinearLayout`。搜索框跟着设置项一起滚动（取消固定）；`SettingMainActivity.filter()` 只重建 `settings_container`，输入框与焦点不受影响，无新增文案。
- **#98 改法**：
  - 新增 `util/PageOrderStore.kt`：合集父目录下 `.page_order.json` 记录服务端下发的 `pagenames`（先过 `FileUtil.stringToFile`，与真实落盘目录名一致）；纯函数 `parse` / `serialize` / `orderedPages`，排序口径复用 `LocalListSort.mergeOrder`（记录里的页优先、扫描到但没记录的按原序补尾、去重、记录里已删的忽略），写文件失败不影响下载本身。
  - `api/PlayerApi.java` 的 `startDownloading` / `startDownloadingAudioOnly` 在多P分支入队前（`CenterThreadPool`）调 `PageOrderStore.remember(title, pagenames)`。
  - `activity/video/local/LocalListActivity.kt` 的 `scanVideos` 扫描合集子目录时按记录排序；**老缓存没有该文件时原样返回扫描顺序**，行为与改动前一致。
- **验证**：`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest -x lint` BUILD SUCCESSFUL；`testStandardDebugUnitTest` **143 suites / 1512 tests / 0 失败 0 错误**（新增 `PageOrderStoreTest` 11 例：JSON 往返、坏数据兜底、记录优先与补尾、已删页忽略、去重、不丢不重）。**设备未验**：本机唯一可用的 AVD 是 Play Store 镜像（android-37.0），任务栈被历史任务反复抢占（刚打开设置页就被别的 Activity 顶掉，首次启动教程 / 夜间提醒连环弹），没拿到稳定的滚动与缓存页截图。真手表复验两步：① 设置页往下滚，搜索框应随内容滚出屏幕；② 缓存一个多P合集后进缓存页，合集内页序应与 UP 主顺序一致。
- **遗留**：旧缓存（这版之前下载的）没有 `.page_order.json`，不会自动补写顺序——需要重新缓存才会生成记录；要给老缓存补顺序得再加「按 meta 里的 aid/cid 反查 pagenames 重建」的逻辑，本轮没做。
- **控制台状态（本轮）**：按维护者拍板把 **#11**（确认「加了」指 #53 内置选图页）、**#48**（证书信任修复，标已处理关闭）、**#82**（维护者确认 26.11.02 之后已正常）、**#89**（对应 `1a1536e` 账号代际隔离）、**#100**（26.11.02 已实现多P续播）标为已处理；#44 顺便核对私有仓库 `feature/DanmuMessage.kt` 已实现「弹幕被点赞 / 被回复」的填充与点击，公开侧仅 `OptionalFeatures` 门面，此前结论成立。

### 3.122 「从 26.11.02 升级到当前版本会掉登录」溯源与凭证链返工（26.11.06，用户点名）

- **结论先放前面（已验证）**：**升级本身不清登录态**。取证口径四条，都能复跑：
  1. 打包面没问题：26.11.02 / 26.11.06 / 26.11.06.1 三个正式包的签名证书 SHA-256 完全一致（`991386eb27546d51487c7d2e20b6f5cbf6eec9c1cbb135c05f19c5b8854fc9ed`）、包名都是 `com.RobinNotBad.BiliClient`、versionCode `2611020 → 2611060 → 2611061` 单调递增 → 覆盖安装合法。排除了「换签名必须卸载」「误发成共存版」这两类会造成掉登录的打包事故（用 `apksigner verify --print-certs` + `aapt dump badging` 对线上 Release 资产实测）。
  2. `git diff 26.11.02..HEAD` 里启动/刷新链路逐字节相同：`SplashActivity.checkCookieRefresh()` / `CookieRefreshApi` / `AppTokenRefreshApi` / `CookiesApi` / `Cookies` / `LoginApi` / `CookieRefreshPolicy` 一个字符都没改。
  3. 全库能写空登录态的只有两个入口：`SplashActivity.resetLogin()`（仅当服务端明确 `-101`，或本地 Cookie 里**没有** SESSDATA 时）与 `MySpaceActivity` 的「退出登录」（要连点两次确认）。升级首启两条都不走。
  4. 账号代际隔离（`1a1536e`）在首启不会误判：`accountGeneration` 初值 0，只有登录 / 切号 / 退出才会自增；首启请求代际 = 当前代际，`saveCookiesFromResponse()` 的丢弃分支进不去。
- **真正的掉登录机制（代码事实，两版同源，本轮修掉）**：`NetWorkUtil.saveCookiesLocked()` 合并 `Set-Cookie` 时有三个缺陷，任一都会让本地**看起来**掉登录：
  1. **凭据按空值覆盖**：收到一条 `SESSDATA=`（`Max-Age=0` / `Expires` 过期 / 服务端清 Cookie）就把本地 SESSDATA 写成空串。`SplashActivity.hasLocalSession()` 的判据正是「Cookie 里有非空 SESSDATA」，于是下一次冷启动直接走「登录信息过期」→ 清整套凭证。
  2. **属性截断只认 `"; "`**：`Set-Cookie: SESSDATA=x;Path=/;Domain=…`（分号后不带空格，RFC 允许）会把属性整段当成值存下来，之后按 `;` 重解析错位。
  3. **SharedPreferences 未就绪时照写**：此时 `getCachedCookies()` 返回空串，从空串合并出来的串只剩本次响应那几个键，SESSDATA / DedeUserID 直接消失。
  另外 `CookieRefreshApi.refreshCookie()` 的顺序与官方流程（`bilibili-API/docs/login/cookie_refresh.md`）相反：新 `refresh_token` 是**一次性轮换**口令，旧实现要等 `confirm/refresh` 成功才落盘（中途任何异常 = 永远续不上会话）；而且是**先 confirm 让旧 Cookie 失效、后校验新 Cookie**，一旦新 Cookie 没落全就是「旧会话被作废 + 本地还是旧的」= 必掉登录。
- **改法**（小步、可单测）：
  1. 新增纯策略 `util/CookieJarPolicy.kt`（`parse` / `parseAll` / `merge` / `parseCookieString`）：`CREDENTIAL_KEYS`（SESSDATA / bili_jct / DedeUserID / DedeUserID__ckMd5 / sid）**任何响应都不得删除或清空**（记 `MergeResult.protectedKeys`，仅用于日志）；非凭据键按 `Max-Age=0` / `Expires` 正常删除；空值写入忽略；输出 `"; "` 连接且无尾随分隔符。
  2. `NetWorkUtil.saveCookiesLocked()` 改调该策略；`sharedPreferences == null` 时**丢弃本次 Set-Cookie**；`putCookie()` 同样加就绪检查。
  3. `CookieRefreshPolicy` 新增纯函数 `checkRefreshedCredentials(...)` → `READY / NO_REFRESH_TOKEN / NO_SESSION_COOKIE / MID_MISMATCH`。
  4. `CookieRefreshApi.refreshCookie()` 返回 `RefreshResult{OK, RETRY, NEED_LOGIN}`：新 `refresh_token` **到手立刻落盘** → 校验新 SESSDATA / DedeUserID / mid 齐全 → 才 `confirm` → 成功后 `AccountManager.applyCredentials()` 原子落库（升代际、清按账号缓存）。只有 `-101` 是 NEED_LOGIN，其余失败一律 RETRY 且**不动本地凭证**。
  5. `SplashActivity.checkCookieRefresh()` 按三档处理；`resetLogin(reason)` 把触发原因写进日志（此前这条唯一的「自伤」路径静默无痕，用户说掉登录、日志里查不到）。`CookieRefreshScheduler` 同步三档，并在刷新成功后重记 `lastAttemptMs`（`applyCredentials` 会重置节流状态，不重记就变成每 5 分钟轮换一次口令）。
  6. `AccountSwitchActivity` 尊重 `switchToAccount()` 返回值：26.11.06 起新 `applyCredentials` 会因「mid 与 Cookie 里的 DedeUserID 不一致 / Cookie 为空」整笔拒绝，而页面原来忽略返回值照提示「已切换」→ 用户看到的是「切了等于没切 / 所有账号都登不上」（对应控制台 #89 时段的现象）。
- **验证**：
  - 构建：`./gradlew.bat :app:clean` → `:app:assembleDebug`（双 flavor，本机 `private/` 已同步）**BUILD SUCCESSFUL**（1m53s）。
  - 单测：`testStandardDebugUnitTest` 与 `testCoexistDebugUnitTest` 各 **149 suites / 1600 tests / 0 失败 0 错误 0 跳过**（新增 `CookieJarPolicyTest` 19 例；`CookieRefreshPolicyTest` 由 13 例扩到 18 例）。
  - 设备（AVD `rebili_api37_sd`，Android 17 x86_64，`standard-universal-debug`）：用 `run-as` 注入一份假登录态（mid / SESSDATA / bili_jct / DedeUserID / csrf / refresh_token）后冷启动。服务端把假会话判成未登录（日志 `E/Cookies cookieInfo 获取/解析失败，跳过本次刷新检查`），**本地凭证一条没少**——回读 `shared_prefs/default.xml`：`SESSDATA` / `bili_jct` / `DedeUserID` / `mid` / `csrf` / `refresh_token` 全在，且**没有** `清空登录态` 日志；随后 `CookiesApi.checkCookies()` 追加的 `b_nut=100` 以干净格式并入（`…; _uuid=fake-uuid; b_nut=100`，**无尾随 `"; "`**，旧实现必留）；启动流程正常走到公告页 → 推荐页，无崩溃。
  - **未验**：真实登录态下「刷新成功后服务端换发新 SESSDATA」本机无账号可复现，由官方文档语义（`bilibili-API/docs/login/cookie_refresh.md`）+ 纯函数单测覆盖。
- **遗留**：真机上的「刷新成功后服务端是否真的换发新 SESSDATA」没有登录态可复现，靠官方文档语义 + 本地纯函数单测兜住；`Chrome` 式 Cookie「删除凭据键」的语义被有意忽略（凭据只由登录 / 切号 / 退出改写），若将来需要支持网页端「退出登录」同步下线，得单独设计一条显式失效通道。

### 3.123 「没有更多了」横幅常驻在推荐 / 收藏夹底部不消失（26.11.06，用户点名）

- **现象**：列表底部的「没有更多了」是**列表外的兄弟节点**（`activity_simple_refresh.xml` / `activity_simple_main_refresh.xml` 里排在 `SwipeRefreshLayout` 下面），钉在屏幕底部、不随列表滚走。以前只要某页拿到「没有下一页」的信号就**永久**留着这一条，用户看到的就是「横幅一直固定在推荐和合集-收藏夹子底部，不会隐藏」。
- **根因**（两条叠加，都不是"某个页面写错了"）：
  1. `updateLoadMoreTip()` 只按 `end || loading` 置 `VISIBLE`，**没有任何收起路径**——`end=true` 之后只有「再次加载」或「整页刷新」才会把它改回 `GONE`。
  2. 「第一页就到底」的页面遍地都是：`FavoriteVideoListActivity` 的 `has_more=false`（收藏夹不足一页）、`SubscriptionListActivity` 的 `!has_more`、`DynamicTopicActivity.loadPlaza()` 的无条件 `bottom = true`……这些页面一进来就常驻一条。
- **改法**（两个列表基类各改一处，覆盖全部列表页）：
  - 新增纯策略 `util/view/LoadMoreTipPolicy.kt`：`stateOf(loading, end)` → `HIDDEN/LOADING/END`（`end` 优先）、`isVisible()`、`autoHides()`、`END_VISIBLE_MS = 2000`。**只有 END 自动收起**，LOADING 必须等到加载回调——请求慢时按时间收掉会让用户以为列表卡死。
  - 新增 `util/view/LoadMoreTipController.kt`：组合而非继承（两个基类没有共同父类，`BaseActivity` 有向下类型判断的坑），持有 `TextView` + 一次性的 `postDelayed` 收起任务；每次重画先 `removeCallbacks`，避免「刚显示就翻下一页」被旧定时器提前收掉。文案也一并收进来——以前同一句话在两个基类里各写一份（`DUPLICATES.md` 的 `DUP-13`），改一处必漏一处。
  - `RefreshListActivity` / `RefreshMainActivity`：字段由 `TextView?` 换成 `LoadMoreTipController?`，`updateLoadMoreTip()` 改为 `runOnUiThread { tip.render(loading, end) }`；新增 `onDestroy()` 调 `release()` 撤掉未到点的定时器（不撤也不崩，只是白留 Activity 2 秒）。
- **验证**：
  - 构建：`./gradlew.bat :app:assembleDebug`（双 flavor）**BUILD SUCCESSFUL**。
  - 单测：`testStandardDebugUnitTest` 与 `testCoexistDebugUnitTest` 各 **184 suites / 1982 tests / 0 失败 0 错误 0 跳过**（新增 `LoadMoreTipPolicyTest` 7 例：四态判定、`end` 优先、只有 END 自动收、时长上下限）。
  - **设备 A/B（AVD `rebili_api37_sd`，Android 17 x86_64，`standard-universal-debug`，用 `DebugBridge` 的 `start` 直接拉起 `DynamicTopicActivity` 广场页——它 `bottom = true` 且 `setRefreshing(false)`，是「第一页就到底」的最小复现）**，`uiautomator dump` 逐帧读 `@id/loadMoreTip`：
    - **修前**（`git stash` 掉本改动后重编装包）：t=2.7s / 5.2s / 7.7s / 10.4s / 13.0s / 15.6s **每次都还是「没有更多了」**；
    - **修后**：t=2.3s 显示「没有更多了」，t=4.7s 起（连续四次 dump 到 t=14.4s）**已不可见**，与 `END_VISIBLE_MS = 2000` 吻合。
  - 回归：推荐页正常出内容、连续下滑仍能翻页（`fresh_idx=1 → 2` 各一次请求）、滚到底部后 `loadMoreTip` 不在树里（空闲不占位）。
- **未验 / 说明**：推荐页本身没在设备上走到 `bottom=true`（推荐流是无限流，只有「本页不足一页」或「去重后无新增」才会置 `bottom`），所以设备证据取自同一套基类路径的广场页；收藏夹 / 我的订阅两条真机用例需要有效登录态，本机只有假会话，**未跑**。真手表复验：进一个不足一页的收藏夹 → 底部短暂出现「没有更多了」→ 约 2 秒后消失、列表底部不再被占一条。

### 3.124 连播「按 UP 主投稿」拉的是登录账号自己的投稿（26.11.06，用户点名）

- **现象**：播放页点「连播」，拉出来的队列是**登录账号自己**的投稿，连播层标题（选集卡片上那一行）也变成自己的昵称，而不是当前视频的作者。
- **根因**（一个字段当两件事用）：
  - `model/VideoInfo.java` 的 `toPlayerData()` 把 `SharedPreferencesUtil.getLong("mid", 0)`（**当前登录账号**）写进 `PlayerData.mid`；
  - `PlayerActivity` 的连播按钮又用同一个 `mid` 去 `UserInfoApi.getUserVideos(mid, ...)` 拉「UP 主投稿」，`applyUploaderQueue()` 还把列表里第一条的 `upName` 当连播层标题。
  - 于是「作者」= 登录账号。`mid` 在播放器里其实只该回答「我是谁」（直播弹幕握手包的 `uid`、进度上报的登录闸），不是视频作者。
- **改法**（作者与登录态彻底分开，新增一个字段贯穿整条链路）：
  - `model/PlayerData.java`：新增 `uploaderMid`（作者 mid，0 = 未知），Parcelable 读写**追加在末尾**；`mid` 的注释写明它是登录账号、不是作者。
  - `model/VideoInfo.java`：新增 `uploaderMid()`（取 `staff[0]`，联合投稿即第一作者；`staff` 为空返回 0），`toPlayerData()` 里另写 `data.uploaderMid`。
  - `api/PlayerApi.java`：`buildTerminalPlayerIntent` 补 `putExtra("uploader_mid", …)`；`buildResumeIntent` 从会话哨兵回填。
  - `player/PlaySessionStore.kt`：`PlaySession` 加 `uploaderMid`，编解码把它**追加在标题之后**——老文本（15 段）仍能解析，作者回落 0，标题不受影响（有单测钉住）。
  - `activity/player/PlayerActivity.kt`：新增字段 `uploaderMid`；连播按钮判据收进新纯策略 `player/PlayerQueueAuthorPolicy.kt`（`!直播 && !番剧 && 在线 && (作者已知 || 有 aid)`）；`loadUploaderQueue()` 用作者 mid，未知时 `resolveUploaderMid(aid)` 用 `x/web-interface/view` 现查（**绝不回退登录 mid**）；切P/换清晰度/互动跳转都把 `uploaderMid` 带进新建的 `PlayerData`；切到别的条目 / 进入分P层时按新 `videoInfo` **重新定稿**（允许 0，不许沿用上一条的作者）；`buildPlaySession()` 落盘。
  - `activity/user/favorite/FavoriteVideoListActivity.kt`：虚拟合集起播这条自拼 `PlayerData` 的路径补一行 `uploaderMid`（`videoInfo` 本来就在手里，省掉播放器现查的 1~2 个请求）。
  - **顺带修正**：番剧（PGC）播放页不再显示「连播」按钮——它连的是剧集列表，原来的按钮点了也是去拉登录账号的投稿。
- **验证**：
  - 构建：`./gradlew.bat :app:assembleDebug`（双 flavor）**BUILD SUCCESSFUL**。
  - 单测：`testStandardDebugUnitTest` **185 suites / 1991 tests / 0 失败 0 错误 0 跳过**（新增 `PlayerQueueAuthorPolicyTest` 9 例：按钮矩阵六格 + 换条目/换P 的作者取舍；新增 `VideoInfoUploaderMidTest` 4 例：单P / 联合投稿 / 空 staff / `toPlayerData` 里登录 mid 与作者 mid 不串；`PlaySessionCodecTest` 由 4 例扩到 6 例，含 15 段老文本回落 0 且标题完好）。
  - **设备（AVD `rebili_api37_sd`，Android 17 x86_64，`standard-universal-debug`，用 `DebugBridge` 注入 `mid=999888` 当"登录账号"）**：`VideoInfoActivity`（aid=80433022，作者 486906719「索尼音乐中国」）→ 播放 → 读回会话哨兵 `player_last_session_v1`：
    `PLAYSESSION1  80433022  137649199  999888  0  0  0  0  45248  212394  collection  0  0  1793952400682  【官方 MV】…  486906719`
    —— 第 4 段是登录 mid、第 16 段是**视频作者**，两者分离且作者值正确，证明 `VideoInfo → PlayerData → Intent → PlayerActivity → 会话哨兵` 整条链路搬的是真作者。
  - **未验（设备）**：点「连播」按钮那一下没在设备上跑到。该按钮在播放器控制栏里，而本机 AVD 上控制栏用 `input tap` / `motionevent` 都拉不出来（`uiautomator dump` 只在极少数帧里出现过 `bottom_control`，之后再也复现不了；怀疑是 dump 等 idle 超过 4 秒自动隐藏窗口）。所以「`arc/search?mid=<作者>`」这条请求没有设备抓包证据，靠 `PlayerQueueAuthorPolicyTest` + 代码走查覆盖。真手表复验：进任意投稿 → 点「连播」→ 连播层标题应是**视频作者**的昵称、队列是该作者的投稿；收藏夹里切到另一个 UP 主的条目后再点「连播」，队列应跟着换成新作者。
- **遗留**：①联合投稿取 `staff[0]`，接口没有明文契约保证顺序（`getInfoByJson` 在联合投稿分支丢弃了 `owner`），真遇到排错再按 `staff[i].sign == "UP主"` 筛；②`resolveUploaderMid()` 现查时 `VideoInfoApi.getVideoInfo` 会顺带发一次 `archive/relation`（登录态下），所以「入口没带作者」的路径比带作者多 1~2 个请求，已在收藏夹虚拟合集那条入口上堵掉。

### 3.125 「跳到指定时间」打开时按当前位置预填（26.11.06，用户要求）

- **需求**：用户说「我希望打开跳转页后，自动填写当前的时间」——指播放器菜单里的「跳到指定时间」（`jump_time_btn` → `InputDialogActivity`），不是外部播放器跳转页 `JumpToPlayerActivity`（那个页面没有时间输入）。原状是每次打开都是空框，用户想微调几秒也得自己重新敲一遍当前时间。
- **改法**（格式与解析同一处真相，不让两份逻辑漂移）：
  - `player/PlayerSeekPolicies.kt` 的 `PlayerJumpTimePolicy` 新增 `prefillText(positionMs: Long): String?`：`<=0` 返回 null；`totalSec = positionMs / 1000`，`hour > 0` 用 `String.format(Locale.US, "%d:%02d:%02d", …)`（`1:02:03`），否则 `String.format(Locale.US, "%02d:%02d", …)`（`01:30`）——**与 `StringUtil.toTime` 的进度文本口径一致**，且**固定 `Locale.US`**，避免某些 locale 的 `%d` 输出非 ASCII 数字后 `parseToSeconds` 又解析不回来。与 `parseToSeconds` 放在同一个 `object` 里，靠单测做 round-trip 钉住。
  - `PlayerActivity.initJumpToTimeButton()`（`:1714` 起）：`val prefill = PlayerJumpTimePolicy.prefillText(video_now.toLong())`，非 null 时 `putExtra("initial_text", prefill)`。**位置取 `video_now` 而不是 `ijkPlayer.currentPosition`**：seek 之后 `video_now` 先写、IJK 的 `seekTo` 后落地，落地前 `currentPosition` 还返回旧位置（`PlayerActivity.kt:817` 附近的既有注释已记录这条坑）。
  - `InputDialogActivity` **一行不改**：光标按既有行为落在预填文本末尾。预填只当「底稿」，用户明确不要整串选中（初版做过 `select_all` extra，当天按用户意见撤回）。
- **验证**：
  - 单测：`PlayerJumpTimePolicyTest` 扩到 15 例（新增「预填文本按播放位置格式化」：0 / -1 → null、90_000 与 90_999 → `01:30`、5_000 → `00:05`、3_723_000 → `1:02:03`；「预填文本能被原样解析回同一秒」：`1_000 / 59_999 / 60_000 / 3_599_000 / 3_600_000 / 7_384_000` 逐个 round-trip）。
  - 构建：`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest` 与 `:app:testCoexistDebugUnitTest` 均 **BUILD SUCCESSFUL**（双 flavor）；两个 flavor 单测各 **186 suites / 2011 tests / 0 失败 0 错误 0 跳过**（本机 `private/` 已同步，数字含其注入的用例类）。
  - **注意一次假失败**：首次跑 `testStandardDebugUnitTest` 报 `AnimeRuleCssTest > keywordKeepsFirstWordAndDropsSpecialChars`（`AnimeRuleCssTest.kt:295`）失败，单独跑该用例/该类又全绿；查 mtime 发现 `private/src/main/java/.../AnimeRuleCss.kt` 与 `AnimeRuleCssTest.kt` 在构建前后被另一个并行会话改动过（16:47:28 / 16:48:04），属「并发构建撞上在途中间态」，与本改动无关，重跑全绿。
  - **设备（AVD `rebili_api37_sd`，Android 17 x86_64 横屏 2400×1080，`standard-universal-debug`）**走完整链路「首页 → 热门 → 视频详情-简介 → 点封面起播 → 双击唤出控制栏 → `menu_btn` → 「跳到指定时间」」：① 播放到 `00:03` 打开弹窗，EditText 预填 `00:03`（hint 不再显示，即确实有内容）；② 点「确定」后进度 `00:49/02:34`（在预填基础上改成 45 秒并跳过去），证明预填文本能被 `parseToSeconds` 解析并正常 seek。**注**：上述设备证据是「整串选中」版本拍的；撤回选中后只改了 `InputDialogActivity` 的 `setSelection` 一行（回到仓库原有行为），按用户要求未重跑。
  - **反例验证**：视频播完（`02:34/02:34`）后打开弹窗，框里是空的（`video_now=0` → `prefillText` 返回 null），仍是原来的输入体验——不会预填出一个 0 秒的假位置。
- **遗留 / 说明**：①「当前时间」取的是**上报进度用的 `video_now`**（每 250ms 由 `progressChange()` 刷新），最长滞后 250ms，对跳转精度无实际影响；②光标落在末尾，想改高位得先挪光标（用户选择「不要整串选中」的直接后果）；③寻址到秒（与输入格式的最小单位一致），不支持毫秒预填。

### 3.126 内置播放器设置按七组重构（26.11.06，用户要求「改设置-播放与播放器的层级结构」第 1 步 / 共 6 步）

- **背景**：用户要「假播放器界面当设置」，最终目标是把播放器控制按钮做成可自定义槽位（设计文档 `docs/superpowers/specs/2026-11-06-player-ui-custom-layout.md`，§2 十七条口径已与用户逐条确认）。六步走：①抽分组数据（本条）②布局纯逻辑 + 单测 ③播放器按布局挂载按钮 ④假界面只读 ⑤编辑能力 ⑥顶部三开关 + 其余点击映射。
- **原状**：`SettingTerminalPlayerActivity` 里一段 200 行的 `ArrayList<SettingSection>().apply { … }`，48 项平铺，中间 5 条 `divider` 全无标题，「从历史位置播放 / 视频可缩放 / 缩放时可移动」被视觉上并进了上一个「播放默认值」标题之下。
- **改法**：设置项全集搬到新文件 `app/src/main/java/com/RobinNotBad/BiliClient/player/PlayerSettingsGroups.kt`：`data class Group(key, title, sections)` + 七个 `private fun`（gesture / defaults / display / decode / danmaku / subtitle / toolbar）。对外三个入口：`build(context)`（全部组）、`group(context, key)`（按组取，给假界面的按钮卡片用）、`flatten(context)`（每组前插一行 `type="title"` 的 `SettingSection`，供「完整设置」页）。Activity 只剩 47 行：`sections = PlayerSettingsGroups.flatten(this)` → `SettingsAdapter` → 把 `list_choose` 回写接回 `applyListChooseResult`。
- **组名**：新增 7 条字符串 `setting_player_group_*`（手势与滑动 / 播放默认值 / 画面与显示 / 渲染与解码 / 弹幕 / 字幕 / 界面按钮），**不用中文硬编码**——这条路径不经过 `SettingText` 的字面量映射表。
- **分组实况（48 项一项没少、key 一个没动，只重排 + 补标题）**：手势与滑动 10 / 播放默认值 8（原 7 项 + 从历史位置播放）/ 画面与显示 5（显示实时人数·视频可缩放·缩放时可移动·显示高能进度条·自动跳过片头片尾）/ 渲染与解码 3 / 弹幕 12 / 字幕 4 / 界面按钮 6（5 个「显示××按钮」+ 互动选项字体大小，这 5 个开关会在第 3 步被布局取代后删除）。
- **顺带修掉的真缺陷**：`SettingsIndex.kt` 的「内置播放器设置」搜索词漏收录 7 项，本次补 6 项（左右滑动控制进度 / 滑动调进度灵敏度 / 弹幕数量保护 / 直播弹幕停留倍率 / 弹幕缓存上限 / 长按倍速档位）；「显示锁定按钮」故意不补——该开关第 3 步就要删。另外把 4 个组名加进搜索词，点一下直接滚到那一组开头。
- **验证**：`./gradlew.bat :app:assembleDebug` 双 flavor BUILD SUCCESSFUL；`./gradlew.bat :app:testStandardDebugUnitTest` BUILD SUCCESSFUL（186 suites 全绿）。真机观感（组标题在本页列表里的字号/间距、`highlight` 定位是否准）**尚未上机**，等第 4 步假界面一起验。
- **注意（给后来者）**：`SettingsIndex` 的搜索定位靠 `indexOfFirst { it.name == highlight }` 与页内 `SettingSection.name` **逐字比对**，插了 title 行不影响它，但**改任一项的名字必须同步改索引**，否则搜索命中后跳过去停在原地。

### 3.127 设置页还原成改造前的样子，播放器界面独立成一个入口（26.11.06，用户点名）

- **背景**：用户在看过假播放器预览后改口：「设置页面还原成没改过之前的样子，但是功能保留（播放器界面设置单独开一个界面）」。
- **还原**：`SettingMainActivity.buildPlayerGroup()` 的描述回到「选择播放器、清晰度与内置播放器设置」、`target` 回到 `SettingPlayerChooseActivity`；`SettingsIndex` 一级 Entry 同步还原，子页面区保留「播放器界面」并排在「选择播放器」之后。
- **新入口**：`res/layout/activity_setting_player_choose.xml` 在「清晰度」卡片前插一张 `MaterialCardView id=player_ui_choose`（标题「播放器界面」+ 副标题「调整播放器上的按钮与布局」），`SettingPlayerChooseActivity` 里点它开 `SettingPlayerUiActivity`。选这张页而不是主设置页，是因为四个播放侧入口（视频详情 / 番剧详情 / 直播间 / `PlayerApi`）都直接开「选择播放器」页，放在这儿不额外增加一层。
- **验证**：设置主页第 0 行回到「选择播放器、清晰度与内置播放器设置」（模拟器实测），点进去能看到新的「播放器界面」卡片。

### 3.128 播放器界面：点空白收起卡片、可能被收起的按钮加注意标志（26.11.06，用户点名）

- **问题一**：卡片打开后点列表空白处关不掉（用户「点击空白处底部按钮没变化」）。原来只有返回键和卡片里的动作能收卡片。改法：`activity_setting_player_ui.xml` 在 `contentList` 与 `cardPanel` 之间加一层遮罩 `View id=cardScrim`（`#66000000`、clickable），`showCard()` 里 `bringToFront()` 后显示、点它 `hideCard()`，`hideCard()` 里连带隐藏。
- **问题二**：有些按钮在特定视频里根本没有（直播没有循环/分P、本地音频没有清晰度、无字幕视频没有字幕……），用户摆上去以后会以为布局配坏了。改法：`PlayerButtons.Button` 新增 `mayHide` 标记（true 的 11 个：倍速/发弹幕/循环/听视频/自动连播/看点/视频信息/跳转时间/连播队列/清晰度/分P），播放器界面的槽位行、候选按钮列表、组内成员列表都在描述里追加「注意：有些视频里不显示」。
- **顺带**：删掉预览版遗留、已无人引用的 `setting_player_ui_hint` / `_speed_sample` / `_title_sample` / `_online_sample` 四条字符串，并修掉一处重复的 `setting_player_ui_region_left`。
- **验证**：`assembleStandardDebug` + 单测（187 suites / 2039 cases / 0 failure）；模拟器上点「锁定」行弹出卡片、点遮罩收起正常。

### 3.129 锁定按钮被摆到右列/底部后，锁屏就解不开了（26.11.06，用户点名）

- **现象**：用户把锁定按钮从默认的左列摆到右列或底部，锁屏后按钮跟着控制栏一起消失，锁再也解不开；而且「原有的地方（左列）的按钮还显示着」。
- **根因**：右列与底部两区的槽位在 `control_layout` 内部，锁定态 `layout_control.visibility = GONE` 会把整条控制栏连按钮一起藏掉。而 `left_control` 在 root 层（`control_layout` 之外），锁定时只单独隐藏了 `lock_btn` / `text_speed`，所以用户摆进左列的**其它**按钮会浮在遮罩上——两个方向都是同一个结构性疏忽：锁定态以前只认识「锁定按钮和倍速文字」这两个写死的成员。
- **改法**：
  - `activity_player.xml` 在 `lock_overlay` 之后加 `FrameLayout id=lock_holder`（match_parent、不可点），作为锁定期间锁定按钮的落脚点。
  - `PlayerActivity.parkLockButton()` 把 `lock_btn` 搬进 `lock_holder`，按它原来所在的区决定贴边（左/右贴边居中、底部贴底，`lockButtonGravity()` 从当前布局里找含 `lock` 的槽，含组内）；`unparkLockButton()` 解锁后按用户布局摆回去。锁定态三相处都补上 `left_control` 的显隐，LOCKED_ICON_VISIBLE 时 `parkLockButton()`。
  - `applyPlayerButtonLayout()` 记住当前布局（`playerUiLayout`），并在锁定态里重摆时重跑一遍 `applyLockPhase(lockPhase)`——否则从设置页回来会把图标摆回被藏起来的控制栏里。
  - `PlayerButtonSlots.clearAll()` 补一条：被外部搬走的真身（真身不在任何槽位容器里时）也要收回按钮池。不补这条，解锁重摆时真身被当成「已摆放」，槽位里摆出的是一个影子、真身留在遮罩层原处——用户看到的就是「锁解开后右侧还是有锁的图标」。
- **验证**（模拟器 emulator-5554，Android 17，1080×2400，设备上存的布局把锁放在右列第 4 格）：点锁 → `lock_overlay` 可见、`left_control` 变 GONE、按钮搬进 `lock_holder` 且贴在右边缘垂直居中（936,1077-1062,1203）；点画面浮现图标 → 点图标解锁后 `lock_overlay` GONE、`lock_holder` 空、`lock_btn` 回到 `right_column_slots` 第 4 格。**踩坑**：浮现图标只有约 3 秒，用 `adb` 分两次 tap 会错过窗口（第二次点到遮罩上，看起来像「点了没反应」），要在一条 `sh -c` 里连着敲两次 tap。

### 3.130 顶部标题栏三件套：显示标题 / 显示电量 / 显示时钟（26.11.06，用户点名「写在完整设置里吧」）

- **背景**：布局可自定义之后，顶部标题栏仍是常显、没有开关。第 6 步先做成了「播放器界面」里点标题栏弹出的卡片，用户要求改到「完整设置（内置播放器设置）」里。
- **改法**：
  - `player/PlayerSettingsGroups.kt` 的 `display()` 最前面加三条 `switch`：`显示标题` / `显示电量` / `显示时钟`，键 `SettingsKeys.PLAYER_UI_SHOW_TITLE` / `_BATTERY` / `_CLOCK`，默认 `"true"`（与改造前常显一致）。
  - `activity/settings/SettingsIndex.kt` 的「内置播放器设置」补三条搜索词（名字与页内 `SettingSection.name` 严格一致，否则搜索点进来定位不到）。
  - `activity/player/PlayerActivity.kt` 新增 `applyTopBarSwitches()`，在 `applyPlayerButtonLayout()` 末尾调用（`onResume` 会重跑 → 设置里改完返回即生效），按三个键分别设 `R.id.text_title` / `R.id.battery` / `R.id.clock` 的显隐。用 `findViewById` 现取不加字段——只在应用设置时读一次。
  - 「播放器界面」页去掉「画面元素 → 标题栏」那一行与其 `onElementClick("title")` 分支，以及只服务于它的 `switchSection()` 和 `setting_player_ui_label_title` 字符串，避免两处入口。
- **验证**（模拟器）：`显示标题` 关掉后进播放页，顶栏只剩电量与时间、标题消失（截图 `outputs/player-top-off.png`）；默认全开时 `dumpsys` 里 `text_title` / `battery` / `clock_layout` 都是 `V`（可见），与改造前一致。
- **留给后来者**：`layout_top` 整条仍可点（点标题栏退出播放）。把标题关掉后这块区域变成不可见的可点区——这是沿用改造前的行为，没再动；若以后要收紧，判据应放在 `layout_top.setOnClickListener` 那一处（:4228 附近）。

### 3.131 组格「更多」按钮被压扁、点了展不开，外加锁定后解锁崩溃（26.11.06，用户点名：「更多按钮是扁的，且点击后展不开」）

- **现象**：右列第三个「更多」画成一条扁白横条（截图量到内容区 108×34，约 3.2:1），点它没有任何反应。
- **「扁」的根因**：`PlayerButtonSlots.moreButton()` 给 `ImageButton` 设了 `setBackgroundResource(selectableItemBackground)`，这份背景 drawable 自带 inset，`View` 会把它转成控件内边距（126−40=86=43dp×2），于是 `ScaleType.FIT_XY` 把 128×128 的方形图标压进 `126×40` 的扁框。实测日志：`lp=126x126 measured=126x126 st=FIT_XY intrinsic=128x128 bounds=0 0 126 40`。**注意视图本身一直是 126×126 正方形**（临时把背景换成红色，红块 bbox 实测 `x936..1061, y1153..1278`），所以只量 bounds 是查不出来的，必须看 `drawable.bounds`。
- **「展不开」的根因**：三条展开条的外层 `left/right/bottom_group_scroll`（`HorizontalScrollView`）在 `activity_player.xml` 里写死 `android:visibility="gone"`，而 `toggleGroup()` 只切内层 `*_group_slots`（`LinearLayout`）——外层永远 GONE，组内按钮排进去了也看不见。
- **修复**：`moreButton()` 在设完背景后显式 `setPadding(0, 0, 0, 0)`（改完 `drawableBounds=0 0 126 126`）；XML 把 `android:visibility="gone"` 从三条外层 ScrollView 挪到内层 LinearLayout，并留注释说明外层必须一直可见。
- **顺带修掉的真崩溃**：锁定态下 `lock_btn` 被 `parkLockButton()` 搬进 `lock_holder`，解锁时 `clearAll()` 收真身直接 `pool.addView(view)` → `java.lang.IllegalStateException: The specified child already has a parent`（`PlayerButtonSlots.clearAll` ← `render` ← `applyPlayerButtonLayout` ← `unparkLockButton` ← `applyLockPhase` ← `onLockButtonClick`）。改成先 `detach` 再入池。
- **锁定图标重画**：按用户给的 lucide `lock-keyhole` / `lock-keyhole-open` 两个 SVG，以 24×24 viewBox 原值改写成 Android vector（`#ffffff` 描边、线宽 2、`fillColor="#00000000"`），覆盖 `icon_player_lock.xml` / `icon_player_unlock.xml`。用户最初给的是 24×24 黑色 PNG，直接放进来在深色播放器上看不见、放大必糊（28dp × density 4.5 = 126px），所以改成矢量。同一批还把「跳转时间 / 连播队列」两个文字按钮换成 `icon_player_clock_fading` / `icon_player_corner_down_right`，模式与时间点写进 `contentDescription`。
- **验证**（模拟器 emulator-5554，Android 17，1080×2400，应用内 density 4.5）：更多按钮恢复方形白框套三点；点它横向展开 `▶ CC □ ♪ 跳到指定时间 连播` 共 6 个组内按钮；锁定/解锁来回切换不再崩，新锁图标在左列与右列都正常渲染。`assembleStandardDebug` + `testStandardDebugUnitTest` 绿。
- **图标几何核对**（独立于模拟器，可复现）：把两张 lucide SVG 的原始 `d`（相对命令、含隐式 lineto）与 vector 里折成绝对坐标后的路径，分别用同一个小光栅器（自写 `M/L/H/V/A` 解析 + 端点转圆心参数化的弧采样 + 圆头描边）渲染成 320×320 再逐像素比对，**两张都是 0 像素差异**——`clock-fading` 的六段弧与指针、`corner-down-right` 的 L 型折线 + 箭头都没走形。另外在不含 `private/` 的独立 worktree（detached `d947c2d`）里跑 `assembleDebug`（两 flavor 都出包）+ `testStandardDebugUnitTest` + `testCoexistDebugUnitTest` 全绿，顺带证明「无私有源码也能独立编译」。
- **留给后来者**：组展开条出现时会压在右列按钮上（截图观察，未定论是遮挡还是重排），若后续确认是遮挡，动的是 `right_group_scroll` 的布局位置，不是显隐逻辑。

### 3.132 搜索「用户」页没有玻璃质感（26.11.06，用户点名）

- **现象**：开着「玻璃质感」时，搜索结果里视频 / 番剧 / 专栏 / 直播 tab 的卡片都是半透明 + 一道主题色亮边，唯独「用户」tab 的卡片是不透明平板灰、没有描边、永远 12dp 圆角——看起来像这个页面没开玻璃质感。
- **根因**：另外四个 tab 的 cell 都是裸 `MaterialCardView`（`cell_video_list.xml` / `cell_article_list.xml` / `cell_up_list.xml`…），底色 / 圆角 / 描边全由主题 `materialCardViewStyle` 提供，而玻璃覆盖层 `GlassOverlay.<X>` 改的正是这条样式引用，所以自动生效。用户页走 `cell_user_list.xml`：根节点是单层 `FrameLayout` + 写死 `@drawable/bg_card_rounded`（不透明 `@color/card_dark`、12dp 圆角、`elevation=2dp`），完全不经过那条样式 → 覆盖层够不着。
- **改法**（不回退布局）：`cell_user_list` 顶部注释写明单层 FrameLayout 是当初为「减少布局嵌套和渲染开销」刻意做的优化（手表优先），玻璃只是一个外观开关，不该为它把 cell 改回 `MaterialCardView`（那样非玻璃观感也会跟着变：圆角 12dp→6dp、多一道 1dp 描边、elevation 2dp→4dp）。改为代码侧换背景：
  - `ui/appearance/GlassStyle.kt` 新增 `applyCardBackground(view: View): Boolean`：玻璃开启时 `background = panelBackground(context, CornerStyle.radiusPx(context))` 并把 `elevation` 置 0，关闭时直接返回（零影响）。圆角取 `CornerStyle.radiusPx` 而不是写死 12dp，才能和主题卡片同档位（选「圆角」时一起变 12dp、方角时 6dp）；`elevation` 清零沿用「半透明底一律无投影」的约定，否则轮廓外会有一圈脏边。
  - 两个 inflate 点各调一次：`adapter/user/UserListAdapter.kt`（搜索结果「用户」页）与 `adapter/user/FollowGroupAdapter.kt`（关注分组的成员行）——同一个 cell，漏一个就有一处还是老样式。
- **验证 · L0**：`./gradlew.bat :app:assembleDebug` 通过；`:app:testStandardDebugUnitTest` + `:app:testCoexistDebugUnitTest` 均绿（退出码 0）。
- **验证 · L1（机器可读部分）**：模拟器 emulator-5554（Android 17 / 1080×2400 / standardDebug 26.11.06 / 经典终端暗色主题，`search_category_sort=user` 让「用户」成为第一页后搜 `miku`）跑玻璃开、玻璃关两种状态，全程 `logcat -b crash` 为空、无 `am_anr` —— 换背景这步没有引入崩溃。另有旁证数值（**不作判据**，理由见下）：玻璃关时 8 张用户卡内一律 `(42,42,53)`＝`@color/card_dark`、边缘 x=35→36 硬跳变、左上弧顶 dx=51px；玻璃开时卡内色随页面背景单调变化、边缘出现 4px＝`strokePx` 的 `(38,27,30)`/`(49,38,41)`/`(61,50,53)` 三段描边、弧顶 dx=24px；`ui_corner_radius=rounded` 时弧顶 dx 从 24px 变到 49–51px（1dp≈4.25px）。**按 `docs/agents/testing.md` §4，AI 不用截图下颜色 / 布局结论**，这些数值只说明「改动确实落到了视图上」，观感好坏仍归人测。
- **未验证（人测，见 `docs/review/handoff-261106-search-user-glass.md`）**：① 玻璃开时「搜索-用户」卡片观感是否与相邻 tab 一致；② 玻璃关时是否与修复前一模一样；③ 点用户卡仍能进用户空间（`input tap` 那次被这台模拟器吞掉没跳转，随后模拟器整体消失，没拿到 `top` 复核）；④ 关注分组页的成员行——该包未登录，`FollowApi.getFollowTags()` 走未登录分支（`{"code":-101,"message":"账号未登录"}`），`FollowUsersActivity` 起来即 finish，进不到分组列表。
- **踩坑**：DebugBridge 的 `set` 只写 prefs、`start` 带 CLEAR_TOP 不重建，改外观开关后必须 force-stop 再起进程才生效；`search_category_sort` 写成 `user` 是最省事的复现姿势（不用滑页，「用户」就是第一页）。
- **留给后来者**：这个 cell 的点击涟漪仍是布局里的 `?attr/selectableItemBackground`，而主题玻璃卡片用的是 `CardStyle*.Glass` 里指定的 `rippleColor`；玻璃开启时行内涟漪颜色与相邻 tab 的卡片略有差异（改动前就是这样，本次没动）。真要统一，就在 `applyCardBackground` 里一并换成 `RippleDrawable`，但要注意别把 `foreground` 覆盖成不透光的遮罩。另有一处已知细节：`MaterialCardView` 的 1dp 描边画在卡片内侧，而这里的 `GradientDrawable` 描边由 `box()` 居中画（轮廓外多 2px），颜色与宽度一致，肉眼基本无差。

### 3.133 「播放器界面」（播放器按钮自定义）的设置入口先藏起来（26.11.06，用户点名）

- **要求原文**：用户说「先把调整播放器按钮的设置入口隐藏起来」。按「只藏入口、页面与功能全留」执行，随时可原路恢复。
- **改了两处入口**：
  1. `res/layout/activity_setting_player_choose.xml` 的 `MaterialCardView id=player_ui_choose`（「播放器界面 / 调整播放器上的按钮与布局」那张卡）加 `android:visibility="gone"`，卡上方留了注释写明是用户要求、恢复时删这一行。
  2. `activity/settings/SettingsIndex.kt` 子页面区的「播放器界面」Entry 整段注释掉。**两条必须一起藏**：全局设置搜索是第二条入口，只藏卡片的话搜「播放器界面」仍能直达该页（`openPage` 直接 `startActivity`，不经过「选择播放器」页）。
- **没动的**：`activity/settings/SettingPlayerUiActivity.kt`、`res/layout/activity_setting_player_ui.xml`、manifest 里的注册、`strings.xml` 的 `pagename_setting_player_ui` / `setting_player_ui_entry_desc`（另有页面内文案）、`SettingPlayerChooseActivity` 里对 `player_ui_choose` 的点击绑定（view 仍是 gone 但存在，留着恢复时少改一处），以及播放器侧 `player/PlayerButtonSlots.kt` / `PlayerUiLayout.kt` 的渲染逻辑——已存档的布局继续生效，只是用户进不去改了。
- **验证 · L0**：`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest` → `BUILD SUCCESSFUL in 45s`（两 flavor 都出包）；`testStandardDebugUnitTest` 187 suites / 2039 tests / 0 失败 0 错误 0 跳过（数字取自 `app/build/test-results/testStandardDebugUnitTest/TEST-*.xml` 求和；本机存在 `private/`，与 §1 记的旧计数不同源，未逐类核对）。**没上设备**：本条结论是「卡片不出现 + 搜索里搜不到」，由 `visibility="gone"` 与索引条目删除静态可判，按 `docs/agents/testing.md` 不占人测额度。
- **26.11.09 已恢复**：commit `cfb4f83d`（用户点名恢复）。`activity_setting_player_choose.xml` 的 `player_ui_choose` 卡片删掉 `visibility="gone"`；`SettingsIndex.kt` 恢复「播放器界面」搜索 Entry（改用 `R.string.pagename_setting_player_ui` / `setting_player_ui_entry_desc`，原注释块里的硬编码中文面量不再回来）。L0：`assembleDebug` + 两 flavor 单测全绿。同 commit 一并恢复顶栏灵动岛总闸（见 §1）。

### 3.134 关于页新增「赞赏」入口，顺手把「捐赠列表」入口藏起来（26.11.06，用户点名）

- **要求原文**：用户先给了图（`https://gitee.com/zisekongling/bili-terminal-re/raw/master/ma.jpg`）说「关于设置里加一个赞赏，点击之后调用播放器弹出这张图」，随即澄清「不是调用播放器，是调用图片查看啊」；随后追加免责声明的措辞（「收到的钱只用于维护服务器和AIapikey」）；最后一句「这个先隐藏吧」经反问确认，指的是**原有的「捐赠列表」入口**，不是新加的「赞赏」。
- **关于页四件事**：
  1. `res/layout/activity_setting_about.xml`：在 `sponsor_list` 卡之前插一张 `MaterialCardView id=donate_qr_entry`（行样式照抄 `sponsor_list`：`TextView` 35dp / 13sp / `textAlignment="textStart"` / `app:drawableEndCompat="@drawable/arrow_forward"`），两侧各一个 `cell_divider`。**顺序是「赞赏」在前、「捐赠列表」在后**，后者现在藏了。
  2. `strings.xml`：`about_donate_entry`=赞赏、`about_donate_url`（`translatable="false"`，就是上面那个 gitee 地址，换图不用改代码）、`about_donate_disclaimer`=四行免责声明（纯自愿 / 钱只用于维护服务器和 AI API Key / 不设付费功能、意见同等考虑 / 未成年人征求意见，末行「点「同意」查看收款码」）。同一文件里 `donate_desc` 的「仅用于维护服务器」同步改成「仅用于维护服务器和 AI API Key」，两句口径保持一致。
  3. `AboutActivity.kt`：`TerminalDialog.confirm(...)` 弹免责声明，`confirmText` 复用 `privacy_consent_agree`（同意）、`confirmIsDanger=false`，`onConfirm` 才 `openDonateQr()`；`openDonateQr()` = `startActivity(Intent(this, ImageViewerActivity::class.java).putExtra("imageList", arrayListOf(getString(R.string.about_donate_url))))`。走**图片查看器**不是播放器：手表屏幕窄，收款码得能双指放大。
  4. 「捐赠列表」入口先藏：`sponsor_list` 那张卡加 `android:visibility="gone"`（卡上方注释写明是用户要求、恢复时删这一行），`SponsorActivity` 页面与 `AboutActivity` 里的点击绑定**全都留着**。
- **顺带修掉的两个 bug（都是这次踩出来的）**：
  - **站外图被拼上 B 站 CDN 的缩放后缀 → 404**：`util/GlideUtil.java` 的 `url()` / `hqUrl()` 原来只排除「非 http / gif / 已带 `@` / afdian」，其余一律拼 `@0e_<q>q_<w>w.webp`（jpg 模式 `.jpeg`）。gitee 的 `ma.jpg` 拼完是 `ma.jpg@0e_75q_640w.webp`，实测 **HTTP 404**，图片查看器里就是一片空白。改法：两个方法共用新的 `private static boolean canAppendResizeSuffix(String url)`，判断下沉到纯 JVM 可测的 `util/ImageRequestPolicy.supportsResizeSuffix(url)`（白名单 `.hdslb.com` / `.biliimg.com` / `.bilivideo.com`，解析不出 host 一律 false）。**故意不用 `NetWorkUtil.isBilibiliHost`**：主站域名属 B 站但不认这个后缀。
  - **`TerminalDialog` 只造弹窗、不负责显示**：`confirm/menu/alert/...` 返回 `AlertDialog`，调用点必须自己接 `.show()`（仓里既有写法见 `FavoriteVideoListActivity.kt:192`、`SubscriptionListActivity.kt:149` 的 `).show()`）。第一版漏了 → 用户实测「点不动」，已补上并在 `AboutActivity` 该块注释里写明这个坑。
- **验证 · L0**：`./gradlew.bat :app:assembleDebug` → `BUILD SUCCESSFUL`（两 flavor 出包）；`:app:testStandardDebugUnitTest` 187 suites / **2043** tests / 0 失败 0 错误 0 跳过（`ImageRequestPolicyTest` 新增 4 个用例：`supportsResizeSuffix_bilibiliCdn_true` / `_externalHosts_false` / `_bilibiliMainSite_false` / `_unparsable_false`）。
- **验证 · L1（模拟器 emulator-5554，Android 17 / 1080×2400 / standardDebug）**：关于页 →「赞赏」行在「注意：本软件仅使用了 WearBili…」卡之下 →点它弹出免责声明（标题「赞赏」、四行正文、底部「取消 / 同意」）→ 点「同意」→ `图片查看` 页，底部「第 1/1 张」，**收款码完整显示**（这条同时证明 Glide 后缀修复在真机链路上成立）。隐藏生效后再看，「赞赏」行下面直接是「此版本主要开发者」，捐赠列表不再出现。全程 `logcat -b crash` 为空。本机没有 OCR 后端，定位控件只能用 `android_ui_tree` + `android_tap_element`（按 text 匹配）。
- **未验证（人测，见 `docs/review/handoff-261106-about-donate.md`）**：① 收款码能不能被手机扫码识别（要真人拿手机扫手表屏幕，属于实操 + 观感）；② 免责声明在 466×466 圆屏上是否需要滚动才能看全、按钮是否够得着。

### 3.135 推荐页误判「到底」：弹一次「没有更多了」之后再也翻不动（26.11.07.1，用户点名）

- **现象**：推荐页滑几屏后底部弹「没有更多了」，此后怎么滑都不再加载新内容。
- **根因**：26.11.06 批次 3 引入的 `RecommendPagePolicy.shouldStop(usableCount, requestedPageSize, newItemCount)` 拿「过滤掉广告 / 番剧 / 直播卡之后的可用条数」跟请求页大小比——一页 20 条里只要有 1 张非 av 卡就 `19 < 20` 判成到底，`bottom` 置 true（`activity/video/RecommendActivity.kt` ← 父类 `RefreshMainActivity` 的滚动监听）。**两个症状同一个根**：父类看到 `bottom` 弹底部提示，同时滚动监听里的 `!bottom` 守卫永久拦住翻页；「去重后 0 条新增」分支同样会置 `bottom = true`。
- **改法**：`util/RecommendPagePolicy.kt` 删掉 `shouldStop`（连同 `RecommendPagePolicyTest` 里它的 4 个用例），页面侧**永不置 `bottom = true`**（去重后没新增只表示这一页回绕到旧内容，不是到底）；`nextPage = RecommendPagePolicy.nextPage(requestPage)` 从「有新增」分支提到去重**之前**，改为无条件前进——否则某一页全重复时会永远卡在同一个 `fresh_idx` 上反复拿同一批。`RecommendApi.java` 与 `RecommendPagePolicy` 的类注释同步改口径：推荐流是无底流，接口深翻（`fresh_idx≈120`）仍返回满页、**没有空页信号**。
- **验证 · L0**：`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest` 通过（`RecommendPagePolicyTest` 删 4 个用例后其余全绿）。
- **未验证（人测）**：一直往下滑是否持续出新内容、不再出现「没有更多了」。反向坑别踩：26.11.02 的旧代码没有游标，服务端对相同参数返回同一批，去重后 0 条新增 → 表现成「触发翻页但列表不长」；现在是游标前进 + 去重并存，连续多页全重复时列表会暂时停住，滑到底仍会继续请求（不是 bug）。

### 3.136 播放器组展开条点「更多」又展不开（26.11.07.1，用户点名，§3.131 回归）

- **现象**：左列 / 右列 / 底部的组格子点「更多」没有任何反应（26.11.06 修过的那条回归）。
- **根因**：`res/layout/activity_player.xml` 里三条外层 `*_group_scroll`（`HorizontalScrollView`）当前仍写死 `android:visibility="gone"`（§3.131 当时是把 `gone` 从外层挪到了内层 `*_group_slots`，26.11.07 的按钮槽位重构把布局改回去了），而 `player/PlayerButtonSlots.kt` 的 `toggleGroup()` 只切内层 `LinearLayout` → 外层恒 GONE，按钮排进去也看不见。
- **改法**：`PlayerButtonSlots.Region` 增加 `stripHost`（外层外壳），`bind(region, container, stripHost, strip, cellWidthDp, cellHeightDp)`；新增 `private fun setStripVisible(region, visible)` **同时**设外壳与内层，`toggleGroup()` 与 `clearAll()` 两处都改走它。这样 XML 里哪一层写着 `gone` 都能展开，不再依赖「布局里到底把 gone 写在谁身上」这个易失细节。
- **验证 · L0**：`./gradlew.bat :app:assembleDebug`（两 flavor 出包）+ `:app:testStandardDebugUnitTest` 通过。
- **未验证（人测）**：点「更多」能横向展开该组按钮、再点一次收起；组展开时不再压在右列按钮上（§3.131「留给后来者」那条仍开放）。

### 3.137 三个入口默认藏起来：菜单「继续播放」、详情页「笔记」、私有分享行（26.11.07，用户要求）

- **需求**：用户要求把三个入口藏掉，且都不是删功能，而是「给个开关、默认关、想用自己打开」：① 菜单页「继续播放」；② 视频详情页「视频笔记」；③ 视频详情页那行可选私有功能（公共侧只有反射挂点，私有仓库一行不改）。
- **改法 · ①（`util/MenuConfig.kt`）**：`resume` 从「有可续播会话就硬插第一位」提升为**动作项**——新增 `RESUME` 常量与 `ACTION_ITEMS = listOf(EXIT, RESUME)`，`ALL_ITEMS = BASE_ITEMS + OPTIONAL_ITEMS + ACTION_ITEMS`；`DEFAULT_ENABLED` 显式排除 `RESUME`；`resolveEnabledList` 迁移时**故意不补** resume（老用户升级后菜单里不该多一项），并把「旧排序非法」的兜底基准从 `ALL_ITEMS` 改成 `DEFAULT_ENABLED`（否则 resume 会顺着「不在开关表里 → 视为启用」被重新塞回来，26.11.07 首轮 L0 就是挂了这 5 个用例）；`resolveStart` / `loadStart` 一律不认动作项（不能被设为启动页）。`activity/MenuActivity.kt` 改成「已启用才插，且只在真有可续播会话时插」；`SettingMenuActivity.titleOf` 与 `adapter/MenuSettingAdapter.kt`（短按提示）补 `RESUME` 分支，文案 `msg_menu_resume_not_start`。
- **改法 · ②③（公共侧）**：新增 `util/DetailEntryPrefs.kt`（`isNoteEntryEnabled()` / `isShareRowEnabled()`，默认都 `false`；默认值集中一处，免得设置页显示关着、详情页却按开处理）+ `SettingsKeys.DETAIL_NOTE_ENTRY` / `DETAIL_SHARE_ROW`；开关落在「设置 → 内容与浏览 → 详情页设置」（`SettingInfoActivity`），其中那条私有入口开关只在 `OptionalFeatures.available` 为真（带私有功能的构建）时才出现，`SettingsIndex` 的搜索索引按同一条件收录。`activity/video/info/VideoInfoFragment.kt`：`OptionalFeatures.attachDetailActionRow(...)` 整段进 `if (DetailEntryPrefs.isShareRowEnabled())`（默认连反射都不做）；未登录隐藏那段之后追加 `else if (!DetailEntryPrefs.isNoteEntryEnabled()) note.visibility = View.GONE`（登录态下默认也藏；点击监听与 `NoteActivity` 都保留）。
- **范围**：`NoteApi` / `NoteActivity` / 私有仓库源码一行未改（只是公共挂点不再调用它）。
- **验证 · L0**：`./gradlew.bat :app:assembleDebug`（两 flavor 出包）+ `:app:testStandardDebugUnitTest` 与 `:app:testCoexistDebugUnitTest` 各 **187 suites / 2044 tests、0 失败**（`MenuConfigTest` 新增 5 例、`SettingsKeysTest` 锁定 2 个新键、`disabledFrom_returnsCanonicalComplement` 期望值补上 `RESUME`）。
- **验证 · L1**：见下方交接单所在 §2.1 条目；模拟器（AVD `Medium_Phone_API_37.0`，本机 `wear5` 的系统镜像已缺、起不来）实测到的部分记在那里。
- **未验证（人测）**：登录态下「笔记」开关前后的真实显示与跳转、可续播会话下「继续播放」的置顶出现、带私有功能构建里那行按钮的点击落地、圆屏/方屏动作区排版疲劳度。

### 3.138 番剧分区页：推荐 / 追番 / 索引 + 动态筛选（26.11.07）

来源：用户对比清单里「番剧分区极其内部的推荐/追番/索引/筛选」这一条（参照仓库 `cyq114514/Re-BiliTerminal` 的 `activity/video/BangumiActivity`）。

**做了什么**

| 落点 | 内容 |
| --- | --- |
| `activity/video/BangumiActivity.kt`（新） | 三个页签 + 三份独立分页状态；推荐固定 `order=3`；追番复用 `BangumiApi.getFollowingList`；索引带筛选 |
| `res/layout/activity_bangumi.xml`（新） | 顶栏（结构照抄 `activity_simple_main_refresh.xml`，时钟是 `@id/top` 直系子 View）+ 页签行 44dp + 筛选入口 36dp + 三块 `SwipeRefreshLayout`/`RotaryRecyclerView` + 共用空态 |
| `api/BangumiIndexApi.kt`（新） | `getCondition` / `parseCondition` / `getIndex` / `parseIndex` / `defaultFilters`；纯解析可 JVM 测 |
| `MenuActivity.btnNames` / `MenuConfig` / `SharedPreferencesUtil` / `AndroidManifest.xml` | 菜单项 `bangumi`（标题「番剧」，排在「热门」之后）、默认启用、对老用户回填一次 |
| `strings.xml` | 19 条 `text_bangumi_*` / `msg_bangumi_*` / `pagename_bangumi` |
| `app/src/test/.../BangumiIndexApiTest.kt`（新）、`MenuConfigTest.kt` | 解析 7 例 + 菜单回填 2 例，另更新 4 处既有断言 |

**几个刻意的取舍（改之前先读）**

1. **不继承 `RefreshMainActivity`**：那套基类只服务单个列表（`page` / `bottom` / `listener` 都是单例状态），三个列表共用一个实例会互相踩。本页自己管三份「页码 / 到底 / 在飞 / 代际」，`setRefreshing(false)` 也自己按页签复位。
2. **代际（`generation`）必须有**：连点筛选项或快速下拉时，旧请求回来会覆盖新条件的结果（现象是「选了日本却显示全部」）。三处代际不匹配的回调直接丢弃。
3. **筛选不做 chip 行**：`condition` 接口风格一组就有 45 个选项。手表可用宽度 192–233dp，横滑 chip 既看不全又要吃掉列表 100dp 左右的竖向空间（参考实现里「折叠 3 行」在 233dp 屏上就把列表挤到只剩两三条）。改成一行「筛选：当前条件」+ `TerminalDialog.menu → singleChoice` 两级弹窗，与本项目「搜索排序」「选季」同一套交互。
4. **失败不能当到底**：`BangumiIndexApi.parseIndex` 在 `code != 0` / 结构缺失时抛 `IOException`，上层分别走「可点重试的空态」与「下拉重试 Toast」。返回空页会让页码永久停住且无任何提示。
5. **追番未登录不发请求**：`code=-101` 的失败没必要打出去；直接给「点我去登录」的空态，`onResume` 发现已登录就自动补拉（手表上很容易漏掉手动下拉）。
6. **卡片映射**：左侧副信息放评分（没开分退回 `order` 文案，例如「762.3万追番」），右侧放更新进度（「全24话」）。`media_id` 缺失时退回 `season_id`——拿 0 去开详情页是空页。
7. **菜单回填**：`MenuConfig.ensureBangumi()` 与既有 `ensureExit()` 同一套判据（缺项 + 新键 `menu_bangumi_state` 没写过 = 老数据 → 回填）。不直接追加到列表末尾，否则会排在「退出」后面；用 `insertAtCanonicalPosition` 按规范顺序插入。

**L0 验证（26.11.07，隔离工作树、未同步 `private/`）**：`:app:assembleDebug`、`:app:testStandardDebugUnitTest` 均 BUILD SUCCESSFUL；standard flavor **178 suites / 1931 tests / 0 失败 0 错误**。接口侧另做了直连实测（见 `docs/bilibili-api-notes.md` §14：两个接口 `code=0`、`order=3` 的分页与字段、`condition` 的 5 项排序 + 9 组筛选）。

**设备端没跑成（L1/L2 未验）**：本机 `system-images` 只剩 `android-37.0/google_apis_playstore/x86_64`，`wear5`（android-34 wear）镜像缺失。`rebili_watch37`（384×384 @320dpi）能 headless 起机、装包、拉起应用，但启动后的服务端公告 / 免责声明是一串 `ShowTextActivity` 页，headless 下按返回只能在这些页之间翻，到不了菜单；期间宿主重启三次，按「两轮上限」止损。改前 / 改后两个调试包已放到 `outputs/`（gitignore）供人工对照：`ReBiliClient-baseline-standard-debug.apk`（0a5900e）与 `ReBiliClient-bangumi-standard-debug.apk`（620a0e9，含本功能）。真机用例见 §2.1。

### 3.139 私有分享行改回默认显示（26.11.08，用户要求撤回 §3.137 的默认藏）

- **起因**：用户问「我分享到其他设备的按钮呢？？？」——§3.137 把这一行改成开关、默认关之后找不到了；用户拍板「改成默认显示吧」。
- **改法**：`util/DetailEntryPrefs.kt` 的 `DEFAULT_SHARE_ROW_ENABLED` 由 `false` 改成 `true`。**开关本身保留**（仍在「设置 → 内容与浏览 → 详情页设置」，关掉后 `VideoInfoFragment` 连 `OptionalFeatures.attachDetailActionRow` 都不调）；设置项文案改口：`desc_setting_detail_share_row_entry` 由「打开此选项…默认关闭」改成「关闭此选项…默认开启」。**「视频笔记」不动，仍默认关**（`DEFAULT_NOTE_ENABLED = false`）。
- **不需要迁移代码**：`SharedPreferencesUtil.getBoolean(key, def)` 只在键不存在时才用默认值——从没动过这个开关的老用户升级后自动变回显示，显式关过的人保持关（同 §5「改默认值天然满足『新装默认开 + 老用户尊重显式选择』」）。
- **L0 验证（26.11.08）**：`:app:testStandardDebugUnitTest --rerun` 实际执行 → BUILD SUCCESSFUL，standard flavor **206 suites / 2229 tests / 0 失败 0 错误**；`assembleDebug` 走的是 UP-TO-DATE（本机另一会话在我改完之后已经用同一份源码构建过，Gradle 按输入哈希判定无需重编），但资源合并产物 `app/build/intermediates/packaged_res/standardDebug/packageStandardDebugResources/values/values.xml` 里已是新文案「关闭此选项…默认开启」，说明字符串改动确实进了包。`npx markdownlint-cli2` 56 个文件 0 错误。
- **未验证（人测 / L2）**：真机进视频详情页动作区应直接有这一行、点进去能进分享页出二维码；在详情页设置里关掉后重进应消失；圆屏上多出这一行的排版观感。真机用例见 §2.1 用例 C。

### 3.140 关于页加项目仓库地址（26.11.08，用户要求「链接指向 GitHub 仓库，并加上可以点击的超链接」）

- **要求原文**：「在关于里加上仓库地址，链接指向 GitHub 仓库，并加上可以点击的超链接」。中途追加两条位置要求：「你放上面点啊」→「放在官网的上面」。最终位置：`debug_tip` 卡与 `cell_divider` 之后、**官网 + 交流群那张卡之前**（首屏滑一段就能看到）。
- **改了四处**：
  1. `app/src/main/res/values/strings.xml`：`pagename_opensource` 之后加 `about_repo_title`（项目仓库）、`about_repo_url`（`translatable="false"`，值 `https://github.com/zisekongling/BiliTerminal-RE`）、`about_repo_tip`（点击链接用浏览器打开，长按可复制地址）。地址只写在这一处，换仓库不用动代码。
  2. `app/src/main/res/layout/activity_setting_about.xml`：新增 `MaterialCardView id=repo_entry`（内含竖排 LinearLayout：13sp 粗体标题 / `TextView id=repo_url` 11sp / 11sp alpha 0.8 提示，全部 `gravity="center"`）；同时给下面的官网卡补 `android:layout_marginTop="6dp"`——两张卡上下紧贴时底色连成一片，用户当场反馈「你看这都连起来了」。
  3. `app/src/main/java/com/RobinNotBad/BiliClient/activity/settings/AboutActivity.kt`：`SpannableStringBuilder(repoUrl)` 上挂 `StringUtil.LinkClickableSpan(repoUrl, LinkUrlUtil.TYPE_WEB_URL)`（`SPAN_EXCLUSIVE_EXCLUSIVE`），配 `StringUtil.ClickableSpanTouchListener.getInstance()`，再 `StringUtil.setCopy(repoView, repoUrl)` 保留长按复制。
  4. `docs/FEATURES.md` §14.16 关于页条目补「项目仓库地址」子条。
- **两条关键理由**：
  1. **不用 `StringUtil.setLink(TextView...)`**：它整体受「识别链接」设置（`SharedPreferencesUtil.LINK_ENABLE`，默认 true）管，用户关掉后这条入口就点不动；仓库地址是关于页唯一的对外链接，要保证永远可点，所以自己挂 span。
  2. **必须配 `ClickableSpanTouchListener`**：`ClickableSpan` 自己收不到触摸事件（需要 `MovementMethod`），而本工程的 `ClickableSpanTouchListener` 在 `ACTION_DOWN` 不消费事件、只在 `ACTION_UP` 命中同一链接时触发，所以长按复制不会被吞。
- **跳转链路**：`LinkUrlUtil.handleWebURL` 只对 b23.tv / `*.bilibili.com` 做站内跳转，其它域名一律 `Intent.ACTION_VIEW` 交系统浏览器——工程内没有通用内置浏览器（只有极验用的 `CaptchaWebViewActivity`）。
- **L0 验证（26.11.08）**：`:app:assembleDebug` BUILD SUCCESSFUL；`:app:testStandardDebugUnitTest --rerun` 实跑 → BUILD SUCCESSFUL，standard flavor **206 suites / 2229 tests / 0 失败 0 错误**；改完 6dp 间距后再跑 `:app:assembleDebug` 仍 BUILD SUCCESSFUL。
- **L1 验证（26.11.08，`emulator-5554` / AVD `rebili_watch37`，384×384 @320dpi，Android 17）**：①`uiautomator dump` 读到 `项目仓库` 与 `https://github.com/zisekongling/BiliTerminal-RE` 两个节点，卡片顺序为「版本名/版本号 → 分隔线 → 项目仓库 → 官网」，与要求一致；②点地址文字后 `topResumedActivity` 变成 `com.android.chrome/org.chromium.chrome.browser.firstrun.FirstRunActivity`，即系统浏览器接管（链接确实可点）；③用 `adb shell wm size 384x900` 把视口临时拉高截图，确认仓库卡与官网卡之间有可见间距（修掉连成一片的问题），看完 `wm size reset` 复原。
- **顺带记两条设备侧坑**（与本改动无关、但会挡住下次验证）：①About 页在 384×384 上触摸滑动**滑不动**，只有表冠 `adb shell input roll 0 -6` 能滚；②Debug 包的 UETool 悬浮窗是 `[0,10][384,118]` 的窗口，会把整条顶栏盖住导致长按顶栏打不开菜单，解法是写 `dev_agent_mode=true`（`SettingsKeys.DEV_AGENT_MODE`，Debug 专属）后重启应用。
- **未验证（人测 / L2）**：真机（含圆屏）上仓库卡的排版与字号观感、长按复制地址是否弹复制页、点开后浏览器是否正常加载（模拟器上 Chrome 是首启流程，没真的把页面渲染出来）。

### 3.141 播放器按钮自定义三件套：图标对齐播放器、空组格隐藏、「更多」可自摆（26.11.09，用户点名）

- **要求原文**：「注意这个播放器设置的图标我想要求和播放器内显示的图标一样，另外要处理和考虑部分视频出现的图标隐藏问题以及更多按钮这个特殊的类型可以容纳其他按钮，但是可以放多个更多按钮以容纳更多的其他按钮」。
- **①图标对齐**（`player/PlayerButtons.kt` 注册表改两处，设置页候选列表自动跟随）：LOCK 由 `icon_player_lock` 改 `icon_player_unlock`（播放器默认态就是解锁图标）；VIEWPOINT 由借用的 `icon_star` 改 `icon_folder`（真身 `activity_player.xml` 的 `viewpoint_btn` 用的是文件夹图标）。其余 15 个逐一核对过本来就一致；AUDIO_ONLY 真身 XML 里是 `icon_play_16` 但运行时 `updateAudioOnlyButton()` 会换成 `icon_audio_only_on/off`，注册表的 `icon_audio_only_off` 与运行态一致，不动；SPEED 真身是文字按钮（×1.0），注册表用 `ic_player_speed` 作示意图标，不动；VIDEO_INFO 真身是 TextView 无图标，`icon_info` 作示意，不动。
- **②空组格隐藏**（`player/PlayerButtonSlots.kt`）：新增 `refreshEmptyGroups()`——按 render 时记进 `groupCells`（`区 → 槽位下标 → 展开内容 keys`）的清单，逐格判「keys 里有没有可见按钮」，全 GONE 时把那个「更多」格设 GONE。刷新挂在 `syncMirrors()` 开头，由既有的 400ms 定时器带着跑（显隐是动态的：看点要等分段加载、连播要看队列）；`startMirrorSync()` 的挂表条件由 `hasMirrors()` 扩成 `hasMirrors() || hasGroups()`，没组格没影子的机器依旧一个定时器不挂。**单个按钮槽位不改**——真身 GONE 时格子空着是既有行为（用户自己摆的按钮，位置留着等它出现），只有「点了没内容的组格」算缺陷。
- **③「更多」可自摆**：
  - 语义定为**自动收纳格**：`Slot.Button(key="more")` 在 `render()` 里走 `moreButton()` 组格路径，展开内容 = `overflowKeys(layout)` = 注册表里当前没摆进任何槽位/组的 key（`more` 自身排除）。放多个「更多」各自展开同一份清单；不对应 `button_pool` 真身（曾按「池里加 more_btn」实施过，发现单格点击语义无法定义后回滚）。
  - `PlayerButtons.kt`：+`const val MORE = "more"` + ALL 末尾 `Button(MORE, setting_player_btn_more, R.mipmap.morehide)`（mayHide=false）。存档格式不变（对旧档而言 more 只是个新认识的 key）。
  - 设置页 `SettingPlayerUiActivity.kt` `showPicker()`：**AddGroupMember 模式把 more 从候选里滤掉**（收纳格套收纳没有意义；AddSlot/ReplaceSlot 允许）；候选行给 more 配说明串 `setting_player_ui_more_desc`「自动收纳没摆进布局的按钮，可以放多个」。
  - 手改脏数据写出 `{..+more}` 组不丢 key：`toggleGroup` 里 `primaryOf("more")` 返回 null 会 `continue`，渲染成组里少一格，不崩（单测钉住解析行为）。
- **单测**（`PlayerUiLayoutTest.kt` +3）：`more slot survives round trip and can be duplicated`、`more inside a group survives parsing as a plain member`；既有 registry 用例（key 唯一 / 全 key round trip / 无分隔符）自动覆盖新 key。默认布局不含 more，`DEFAULT_SERIALIZED` 不变。
- **验证 · L0**：`:app:assembleDebug` BUILD SUCCESSFUL in 51s（两 flavor 出包；中途一次 `compileStandardDebugKotlin` 因 Gradle 缓存打包失败报 `Could not get file mode for ...PlayerControlDelegate...class`，是本机文件被短暂锁住的临时故障，重跑即绿，非代码问题）；`:app:testStandardDebugUnitTest :app:testCoexistDebugUnitTest` BUILD SUCCESSFUL in 37s。
- **未验证（人测 / L2）**：①设置页候选列表里锁定/看点的新图标观感；②真机点「更多」收纳格展开的是没摆进布局的按钮、放两个「更多」各自能开；③直播页里组格（如默认布局右侧那个 9 格组）整个跟着藏掉的手感——直播把 loop/audio_only/quality 等全 GONE 后，那个组格只剩 danmaku_send/subtitle/video_info 等少数可见成员，不会消失但内容变少，属预期。

### 3.142 修 26.11.09 新增崩溃：Android 8.0 非全屏窗口下 `setRequestedOrientation` 抛异常（26.11.09，控制台待处理崩溃）

- **现场**：crash 271 / 274–281，同一台 huawei sawshark `LEO-DLXXU`（sdk26 / Android 8.0，`coexist` 包，版本 26.11.09.2）15:17–15:18 连崩 8 次：`java.lang.RuntimeException: Unable to start activity ComponentInfo{...coexist/com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteVideoListActivity}: java.lang.IllegalStateException: Only fullscreen activities can request orientation`，栈压 `BaseActivity.onCreate(BaseActivity.kt:132)` ← `RefreshListActivity.onCreate(RefreshListActivity.kt:88)` ← `FavoriteVideoListActivity.onCreate(FavoriteVideoListActivity.kt:105)`。同设备页面轨迹显示之前正常进出 VideoInfo / FavoriteVideoList / PrivateMsg 多页，属于「本来好好的，某个窗口状态下突然起不来」。
- **根因**：Android 8.0（API 26）起，`Activity.setRequestedOrientation` 在窗口不是全屏（悬浮 / 分屏 / 系统判定为多窗口）时直接抛 `IllegalStateException`。全仓库只有 `activity/base/BaseActivity.kt` 一处调用它（`grep setRequestedOrientation` 唯一命中），**而且没有任何 catch** ⇒ 任何 `BaseActivity` 子页在那个窗口状态下都会「起不来」，崩在启动路径上。本项目历史上同一异常在 `PlayerActivity.onDestroy` 也崩过（26.10.19，4 例全是 sdk26），当时修法也是 try/catch，后来随 §3.90 一起被删掉了（见 §3 里 26.10.19 那条与 26.11.06 的回迁记录）。
- **改法**（`app/src/main/java/com/RobinNotBad/BiliClient/activity/base/BaseActivity.kt`）：`onCreate` 里那句 `setRequestedOrientation(if (ui_landscape) SCREEN_ORIENTATION_SENSOR_LANDSCAPE else SCREEN_ORIENTATION_PORTRAIT)` 包进 `try { … } catch (e: IllegalStateException) { Logu.w("BaseActivity", "设置屏幕方向失败（窗口非全屏），按系统方向显示：${e.message}") }`，前面加中文注释说明「Android 8.0 起非全屏窗口抛、26.11.09 华为 LEO-DLXXU 连崩 8 次、拿不到方向就按系统方向走」。**不改语义**：能设照设，设不上就跟随系统方向，而不是让页面起不来。
- **为什么不去改调用方**：`ui_landscape` 是全局设置（所有页面共用），不可能靠逐个页面加判断绕开；异常只可能在调用点抛，也只能在调用点兜。
- **验证 · L0（26.11.09）**：`:app:assembleDebug`、`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 全 BUILD SUCCESSFUL，两 flavor 各 209 suites / 2257 tests、0 失败。
- **未验证（人测 / L2）**：需一台 Android 8.0 手表在**非全屏**窗口状态（分屏 / 悬浮 / 自由窗口）下进入任意子页确认不再崩、页面按系统方向正常显示。本机模拟器是 Android 17 / 384×384 全屏，构造不出该异常的前置条件，所以这条只能真机验。

### 3.143 反馈 #136：「有些视频一旦下载错误之后就再也无法下载」（26.11.09，控制台反馈）

- **要求原文**：「有些视频一旦下载错误之后就再也无法下载。尝试过去删除缓存文件或者是删应用重启。都没有作用。」（OPPO OW20W2，sdk27，26.11.09.2，附日志 185 字符。）
- **用户日志**：`E DownloadService>分片 0（0-2440726）3 次尝试均失败：unexpected end of stream` + `E DownloadService>下载未完成：video_new.mp4 524288/7322181`。
- **日志解读**：`video_new.mp4` 是 `DownloadPathSpec.FILE_VIDEO_TMP`，即 **DASH（qn>64）视频的临时文件**；分片路径（64KiB 缓冲）与单连接路径（`DownloadStreamTransfer` 256KiB 缓冲）都断在 **524288 字节**同一个偏移上，`unexpected end of stream` 是 OkHttp 在 `Content-Length` 没传满就 EOF 时从 `FixedLengthSource` 抛的 ⇒ 判定为**服务端 / CDN 线路掐断，不是 App 写文件写坏**。`processDownloadSection` 每次尝试都重新取 playurl（不是复用过期 URL），所以也不是签名过期。
- **App 侧真正的缺陷（这条反馈「再也下不动」的成因，三条叠加）**：
  1. **重试永远打同一条线**：`DownloadService.downloadSegment` 的 3 次尝试复用同一个 `url`，`downFileSpeedSingle` 失败后直接返回，谁都没有去试 playurl 给的备用线路。而 `model/DashVideoStream.java:16/26/45-51`、`model/DashAudioStream.java:16/23/39-45` **早就把 `backupUrl` / `backup_url` 解析成 `List<String>` 了，全仓库一个消费点都没有**（§2.5 里 #90 / #87 那条记的就是这件事）。
  2. **失败记录把重新入队堵死**：`DownloadService.startDownload(...)` 的两个重载先 `select * from download where aid=? and cid=?`，**命中任意一行就 `showMsg(R.string.text_download_already_queued)` 并 return**，不看 `state`。失败任务留下的是 `state="error"`（`runDownloadSection` 失败时 `DownloadRepository.setState(id, "error")`），于是用户在视频页再点下载，只会看到「已在下载队列中」，永远进不了队列。
  3. **详情页的拦截判据是目录存在**：`activity/video/info/VideoInfoFragment.kt:851-882 startDownloadFlow()` 用 `FileUtil.getVideoDownloadPath(...).exists()` 判断，目录里还留着 `.DOWNLOADING` 标记时提示「已在下载队列\n如有异常，长按可清空文件」——**而这条提示是用户唯一能看到的出路，他删了缓存/重启 App 都不会让目录消失**。
- **改法（四块）**：
  1. **备用线路贯通到下载器**：新增 `app/src/main/java/com/RobinNotBad/BiliClient/service/download/DownloadUrlCandidates.kt`（`object` + `@JvmStatic fun of(primary, backups): List<String>`：主线路在前、去空、去重）；`model/PlayerData.java` 加 `public ArrayList<String> videoUrlCandidates / audioUrlCandidates`（**刻意不写进 Parcel**——与 `dashData` 同理，是一次取流的附属信息，跨 Intent 后本就过期）；`api/PlayerApi.java` 在 5 处取流点填候选（DASH 分支读 `videoStream.backupUrl` / `audioStream.backupUrl`，三处 durl 分支新增 `readDurlBackups(JSONObject)` 读 `backup_url` 数组；辅助方法 `rememberUrlCandidates(...)` / `readDurlBackups(...)` 在 `PlayerApi.java:134-154`）。
  2. **`DownloadService` 逐条换线重试**：`processDownloadSection` 先算出 `val videoUrls: List<String>` / `val audioUrls: List<String>`（audioOnly / qn≤64 / DASH 三个分支各自赋值，均 `data.*UrlCandidates.ifEmpty { listOf(url_xx) }`，8 处取流调用点改成 `downFile(videoUrls, …)` / `downFile(audioUrls, …)`）；新增 `private fun downFile(urls: List<String>, file, sectionId, baseProgress, endProgress): Int` 逐条尝试，**只对 `DownloadPathSpec.ERR_NETWORK` 换线**（`ERR_FILE` / `ERR_PAUSED` / `ERR_UNKNOWN` 立即返回），换线时 `Logu.w("DownloadService", "下载失败，换备用线路重试（${index + 1}/${candidates.size}）：${file.name}")`；原实现体整体改名 `downFileOnce(...)`，`downFile(url: String, …)` 保留成一行委托（调用点语义不变）。
  3. **error 记录不再堵门**：`startDownload` 两个重载命中 `state="error"` 的行时改成 `delete from download where aid=? and cid=?` 后继续入队（其余 state 仍维持原来的「已在下载队列中」提示）。
  4. **历史失败残留会被清理**：新增 `app/src/main/java/com/RobinNotBad/BiliClient/service/download/DownloadResiduePolicy.kt`（`internal object`，`Disposition{NONE, CLEAN_TEMPS, DELETE_FOLDER}` + `hasFinishedArtifact(names)`：目录里有已完成成品时**一律不动**，`.bak` 也算成品；否则有 `.DOWNLOADING` / 临时文件就清临时，只剩自己人（`.quality` / 封面 / 弹幕 / 字幕目录 / `.video_meta.json`）才整目录删）；`DownloadService.cleanFailedResidue(folder)` 搬进 companion，与新增的 `cleanupFailedResidues()`（遍历 `DownloadRepository.getAll()` 里 `state == "error"` 的行逐个清）并列；`activity/video/local/DownloadListActivity.kt` 进页面时先调一次 `cleanupFailedResidues()` 再 `refreshList(false)`——这样升级前留下的失败任务，用户打开下载列表就自愈，详情页那个「已在下载队列」也就消失了。
- **不改的地方（有意）**：没有实现真正的断点续传（分片级 `RandomAccessFile` 已有，但失败后仍是整文件重下）——那是另一个量级的改动，本轮只解决「下不动」；`backupUrl` 只用于下载，播放器取流没动。
- **单测**：新增 `DownloadUrlCandidatesTest`（5 例：主线路排第一、无备用、主线路为空仍给备用、空串与重复剔除、全空返回空）与 `DownloadResiduePolicyTest`（9 例：清临时 / 删目录 / 有成品不动 / `.bak` 视为成品 / 目录为空时的边界等）。
- **验证 · L0（26.11.09）**：`:app:assembleDebug`、`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 全 BUILD SUCCESSFUL，两 flavor 各 209 suites / 2257 tests、0 失败。
- **未验证（人测 / L2）**：①换线本身要一条**真会被 CDN 掐断**的流才能触发，本机造不出来，只能靠线上日志里出现 `换备用线路重试` 反证；②真机跑「失败过的任务再点下载能重新入队并下完」；③`cleanupFailedResidues()` 在真机上对历史失败任务的效果（该机需存在 26.11.09 之前留下的 error 记录）。

### 3.144 反馈 #132：关闭表冠适配后详情页滚动只有 1/3 速（26.11.09，控制台反馈）

- **要求原文**：「小米watch5使用中关闭表冠适配后详情页滑动速率小，得一直转动的还慢，详情页正常。」（xiaomi `grasslte` / 小米 Watch 5，sdk34，26.11.09.2，附日志 355 字符。）
- **用户日志**：
  `W RotaryEncoderSupport>h | RotaryEncoder>兜底：滚动成功（本进程首次） action=8 src=0x400000 rotary=true axisScroll=-0.0917 axisVScroll=0.0 dy=28 target=RotaryRecyclerView`
  `W RotaryEncoderSupport>h | RotaryEncoder>系统优先：框架已滚动，吞掉事件（本进程首次） action=8 src=0x400000 rotary=true axisScroll=-0.0524 axisVScroll=0.0 view=RotaryNestedScrollView`
  两条恰好是两条路径（兜底 / 系统优先），且同一台机器同一格差 2.7 倍 —— 就是问题本身。
- **根因**：系统优先模式（关闭表冠适配 ⇔ `RotaryEncoderSupport.isSystemFirstMode()` = `!isEnabled()`）的实现是「跑完框架的 `super`，再用 `scrollOffset()` 前后有没有变判断框架动没动，动过就整段 `return true` 吞掉」，**从不算差额**。但框架各容器的换算基准是三套、且都比 App 口径小：`NestedScrollView`（视频详情页 `fragment_video_info.xml` 的 `RotaryNestedScrollView`）取 `getVerticalScrollFactorCompat()` = 主题属性 `android:listPreferredItemHeight`（≈48dp × density）、平台 `ScrollView` / `RecyclerView` 取 `config_verticalScrollFactor`（64dp × density），而 App 自己的口径 `RotaryEventPolicy.pixelDelta` 是 64dp × 2。用日志反推（`dy=28`、`multiple=1.0` ⇒ 缩放值 ≈153.6px = 64dp × density 2.4），同一格 `axisScroll=-0.0524`：详情页框架只给 ≈6px、列表页 ≈8px，开启适配时是 16px ⇒ 详情页只到手感的 **37.5%**（慢 2.7 倍）。
- **改法**（保留「框架动过就吞事件」的结构，吞之前补足差额）：
  - `app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/RotaryEventPolicy.kt`：新增 `const val SYSTEM_FIRST_TARGET_SCALE = 1.0f`、`systemFirstTargetDelta(ratio, scaledVerticalScrollFactor, multiple)`、`topUpDelta(targetDy, frameworkDelta)`；**删除已无意义的 `shouldFillIn`**。
  - `app/src/main/java/com/RobinNotBad/BiliClient/ui/widget/RotaryEncoderSupport.kt`：`handleSystemFirst` 改为 `frameworkDelta = scrollOffset() - before` → `target = systemFirstTargetDelta(...)` → `topUp = topUpDelta(target, frameworkDelta)`，只有 `topUp != 0` 才 `smoothScrollBy(topUp)`；抽 `rawScrollRatio` / `scaledVerticalScrollFactorOf` / `systemFirstTargetDelta(view, …)`；新增 `topUpProbed` 诊断（W 级 probe，带 `target= frameworkDelta= topUp=`，`PROBE_MAX` 仍是 6）。
  - 三个 `Rotary*` 控件（`RotaryNestedScrollView` / `RotaryScrollView` / `RotaryRecyclerView`）只改注释（`scrollOffset` lambda 本来就返回带符号差值）。
  - `res/values/strings.xml:60 setting_lab_ui_rotatory` 文案改成「系统没滚够时自动补足差额」，并修正原文里「1.0 即系统原始滚动量」这处错误（1.0 是**应用内标准滚动量**，约为系统原始量的两倍）。
- **为什么这样补不会重复计数**：框架滚过的那一份如实抵扣（`topUp = target - frameworkDelta`），同一次手势的总额恰好等于目标；框架给够 / 方向相反 / 到边界的机型 `topUp == 0`，行为与改前完全一致，也不需要「多少才算滚过」的阈值。
- **单测**：`RotaryEventPolicyTest` 18 → 25 例，含「框架位移 + 补位 ≡ 目标」的防重复计数断言、以及用本机实参（16−6=10、16−8=8）的差额用例。
- **已知局限（记录在案）**：①系统优先模式下 `sensitivityOf()` 仍忽略灵敏度倍数（26.11.07.1 既有语义，未改）；②内层是**原生** `RecyclerView`（不是 `RotaryRecyclerView`）时外层拿不到 `frameworkDelta`，仍是原速。
- **验证 · L0（26.11.09）**：`:app:assembleDebug`、`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 全 BUILD SUCCESSFUL，两 flavor 各 209 suites / 2257 tests、0 失败（其中 `RotaryEventPolicyTest` 25 例）。
- **未验证（人测 / L2）**：小米 Watch 5 关闭表冠适配后，详情页每格距离应与开启适配一致、连续快速转动不打嗝、内层列表与详情页外层交替滚动时不叠加。偏慢只调 `SYSTEM_FIRST_TARGET_SCALE`（1.1~1.25），打嗝把补位改成瞬时 `scrollBy(0, topUp)`（`RotaryEncoderSupport.kt` 里一行）。

### 3.145 Glide `Cannot recycle a resource while it is still acquired` 降噪（26.11.09，控制台待处理崩溃第一名）

- **现场**：`java.lang.IllegalStateException: Cannot recycle a resource while it is still acquired`，栈是 `at com.bumptech.glide.load.engine.EngineResource.recycle(EngineResource.java:68)` ← `ResourceRecycler$ResourceRecyclerCallback.handleMessage(ResourceRecycler.java:37)`（混淆栈 `F1.y.c(SourceFile:39)` / `A2.e.handleMessage(SourceFile:17)`）。自 26.11.02 起累计 51 条，涉及 OPPO / HUAWEI / samsung / XTC 等多款手表；26.11.09 最新一条 crash 282（HUAWEI NIK-AL00，sdk29，26.11.09.2）的页面轨迹是 VideoInfo / Search / UserInfo / OpusInfo / DynamicInfo / PrivateMsg / ImagePicker 反复进出，应用日志里没有异常行——典型的「主线程延迟回收撞上仍被持有的资源」。
- **结论：本仓库代码里没有可确定的成因**。栈里这一层是**主线程消息队列的延迟回收**（`Engine.onResourceRemoved` → `resourceRecycler.recycle(.., forceNextFrame = true)` 投递），`EngineResource.recycle()` 只在 `acquired > 0` 时抛，而 `ResourceRecycler.handleMessage` 是**裸调用 recycle、不再复查 acquired**；`Engine.loadFromCache` 里 `cache.remove(key)` 与 `acquired.acquire()` 不在同一把锁内、`onResourceReleased` 也没 synchronized ⇒ 判定为 Glide 4.16 上游 `Engine` / `ActiveResources` / `MemoryCache` 三处加锁交错的竞态。**因此没升级/替换依赖，只做「减少回收流量、降低触发概率」的最小规避。**
- **排查过的四个方向（都排除，记下来免得下次重复查）**：①同一 target 两次 clear —— `SingleRequest.clear()` 对 `status==CLEARED` 幂等、`RequestManager.untrack` 在无 request 时直接返回，重复 clear 只是空转，不会二次 `engine.release`；②页面级 / Application 级两个 manager 管同一 target —— `RequestManager.untrackOrDelegate` 会 `glide.removeFromManagers(target)` 跨 manager 兜底（上游注释点名 issue #2262），release 仍只有一次；③销毁 / 复用后仍有 in-flight 请求 —— `into(ImageView)` 只覆盖 tag，旧请求由自身 clear 或 manager `onDestroy` 释放；④`ActiveResources.cleanupActiveReference` 的 `isRecyclable=false` 包装 —— 只做无副作用回收，不是来源。
- **改法（4 改 1 增）**：
  1. `activity/ImageViewerActivity.kt`：新增纯策略 `internal object ImageViewerClearPolicy { @JvmStatic fun shouldClearPhoto(photoLoadStarted: Boolean, photoCleared: Boolean) = photoLoadStarted && !photoCleared }`，`ImagePage` 加 `photoLoadStarted` / `photoCleared` 两个标记，`attachTiled()` 里把**原来无条件**的 `Glide.with(this).clear(page.photoView)` 改成「只清这一页真发起过的那次整图请求、且只清一次」，`attachPhoto()` 发起加载时复位标记。理由：`attachTiled()` 的注释本来就写着它必须幂等（ViewPager 来回翻会反复调用），原来却在里面无条件 clear——这是全仓库唯一一处「同一 View 被反复 clear」的真实来源。
  2. `activity/video/info/VideoInfoActivity.kt`：删掉 `onCreate` 里无条件的 `Glide.get(BiliTerminal.context!!).clearMemory()`（连带两个变成无用的 import）。三条理由写进注释：清不到真正占内存的位图（它们在 ActiveResources / 请求里，acquired > 0）；MemoryCache 容量本来由 `LruResourceCache` 卡住、系统压力时 Glide 自己走 `trimMemory`；它是**本崩溃最大的放大器**——clearMemory → 逐条 `onItemEvicted` → `Engine.onResourceRemoved` → `forceNextFrame = true` 逐条 post 主线程，而紧接着本页就开始加载一批图片（crash 282 的轨迹起点正是 VideoInfo）。
  3. `activity/MenuActivity.kt`：删掉 `killAndJump` 里「低性能设备 `clearMemory()`」那段（同一机制，且**只在低性能设备走**，而低性能手表正是崩溃集中人群；清完紧接着 `startActivity` 的新页又要加载图片，只会让淘汰更密），连带删两个无用 import。
  4. `helper/CustomGlideModule.kt`：`applyOptions` 里 `if (BuildConfig.DEBUG) builder.setLogLevel(Log.VERBOSE)`——这类崩溃现场无异常可抓，只能靠 Engine 日志看「命中 cache / 放回 cache / 被淘汰」的先后。用 `BuildConfig.DEBUG` 编译期常量，Release 被 R8 折叠、零开销不打日志。
- **有意保留**：`activity/video/info/VideoInfoFragment.kt:801` 与 `activity/video/info/BangumiInfoFragment.kt:423` 的「起播前腾内存」`clearMemory()`（语义正当，起播即批量加载图片的情况很少）；`util/GlideUtil.java:166-175` 的 `with(View)` 在宿主销毁时回落 `applicationContext` 的行为（改掉会重新引入 `You cannot start a load for a destroyed activity`）。
- **单测**：新增 `app/src/test/java/com/RobinNotBad/BiliClient/activity/ImageViewerClearPolicyTest.kt`（5 例：发起过且没清过 → true、同一次请求第二次 → false、从未发起 → false、两标记边界、「清过一次 → 重新加载 → 再清一次」）。
- **验证 · L0（26.11.09）**：`:app:assembleDebug`、`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 全 BUILD SUCCESSFUL，两 flavor 各 209 suites / 2257 tests、0 失败。
- **未验证 / 只能线上观察**：上游竞态本身修不掉，**不能保证零复现**。若下一版起该崩溃条数明显下降、且 crash 282 那类 VideoInfo→…→ImagePicker 轨迹不再出现，说明降噪有效；否则要回到 Glide 上游（升级或自建 Engine 补丁）才能根治。

### 3.146 下载速度与进度改成按真实字节算（26.11.10，用户反馈「速度和进度显示的都非常不准确」）

- **现场**：用户对比 Bili23-Downloader 后反馈本仓库下载页的速度与进度都不可信。读代码定位到三个独立根因，互相叠加：
  1. **进度按人为阶段权重摊派，与字节无关**：`DownloadProgressMath` 把一条任务切成附件 `0.00–0.12` / 视频 `0.12–0.52` / 音频 `0.52–0.88` / 合并 `0.88–1.00`，两个流的权重是写死的 40:36。DASH 实测视频流常是音频流的数十倍（4K 更夸张），于是**视频流下完进度才 52%、音频流下完直接 88%**，最后一段大文件几乎不动、小文件一冲而过。
  2. **速度是全局一个采样器**：`DownloadProgressStore.speedStr` 只有一个，喂的是 `totalBytesDownloaded`（整个批次所有任务所有分片的累计字节），由 `DownloadNotifier` 的 1 秒通知定时器调用 `sampleSpeed()` 驱动。列表每行渲染的是同一个字符串 ⇒ **并行 3 个任务时 3 行显示同一个总速度**，总速度又因为被 N 等分而看起来忽高忽低。
  3. **全局计数器允许回滚，采样点撞上回滚就归零**：分片失败 `addDownloadedBytes(-written)`、完整性校验失败 `addDownloadedBytes(-segBytes)` 都会减全局量；`SpeedSampler.sample` 用 `coerceAtLeast(0)` 把负增量当 0，叠加 1 秒窗口 ⇒ 速度显示成 0 或者腰斩。
- **改法（与 Bili23-Downloader 的 `_calculate_speed` / `calc_downloaded_size` 同口径：已下载量必须是磁盘上真实存在的字节，速度用实测时间差分）**：
  1. 新增 `service/download/DownloadSectionBytes.kt`：每条任务一个字节预算（`probeSectionBytes` 探视频流 + 音频流的 `Content-Length`），进度 = `(已完成文件的实际字节 + 当前文件已下字节) / 预算`。`finishFile(actualBytes)` 用**实际**落盘字节而不是预算份额、`finishAll()` 在合并前把进度顶到 100%、`monotoneProgress` 让回滚 / 重试时进度条不倒退、`growBudget` 处理「只探到一条流」。**探测失败（返回 -1）时回退到原来的阶段权重**，所以老机器 / 拒绝 HEAD 的线路行为不变。
  2. 新增 `service/download/SpeedSampler.kt`：**每条任务一个**采样器，分母用实测时间差（`SystemClock.elapsedRealtime()`）、最小窗口 500ms（挡掉密集采样放大噪声）、0.4 权重 EMA 平滑、超过 2.5s 没有新字节就归零（下载停滞时速度必须掉下来）。
  3. `DownloadService`：新增 `probeContentLength`（HEAD 读 `Content-Length`；失败再发 `Range: bytes=0-0` 从 `Content-Range` 抠总长；都失败返回 -1）+ `probeSectionBytes`；`downFileNormal` / `downFileSpeedSingle` / `downFileSpeedSeg` / `downloadSegment` 全部改走 `reportFileProgress` / `finishFileProgress`，分片阶段只加自己的 `shared.totalDownloaded`（不再碰全局量）；阶段标记（视频中 / 音频中）改走新增的 `setDownloadState`，只换文案不动数字。
  4. **附件不占进度区间**：封面 / 字幕 / 弹幕的 `downFile` 不再传 `sectionId`（否则附件字节会算进分子而分母没有它们），只保留「正在下载封面 / 弹幕」这类状态文案。
  5. `DownloadBatchStats.overallProgress(activeDoneBytes, activeTotalBytes, waitingCount)` 改按字节：`(已完成任务字节 + 各活跃任务已下字节) / (已完成任务字节 + 各活跃任务总字节)`，排队任务按「已完成任务的平均大小 × 排队数」估分母。成功任务出队前由 `DownloadProgressStore.retireSection(id, true)` 把总字节记进 `batchStats`，否则总进度会在任务出队的瞬间往回掉。
  6. UI：`DownloadAdapter` 每行读 `progressInfo.speedBps`（删掉 `lastSpeedStr` / `lastSpeedMode` 两个字段）；`DownloadListActivity.updateBottomBar()` 用 `getTotalSpeedBps()`，ETA 直接用字节算——**删掉 `parseSpeedToBytes`**（原实现把「12.3 MB/s」正则解析回字节，格式一变 ETA 就消失）；`DownloadNotifier` 去掉每秒的 `sampleSpeed()` 调用。
- **顺带删掉的公开面**：`DownloadService.speedStr` 门面属性、`getDownloadedBytes` / `addDownloadedBytes` / `resetDownloadedBytes` / `sampleSpeed` / `resetSpeedSampling`，以及 `DownloadProgressMath.dashMergeProgress` 与 `DASH_MERGE_END` 常量（合并阶段不再占进度区间）。`isSpeedMode` / `activeDownloadsCount` 仍保持非 volatile（既有数据竞争，未顺手改）。
- **单测**：`DownloadSectionBytesTest` 8 例（预算内 / 无预算兜底 / 接下一个文件 / 实际字节超预算 / 不回退 / `finishAll` 顶满 / `growBudget` / `snapshot` 只读）、`SpeedSamplerTest` 7 例（首次采样 / 窗口不足 / 实测间隔 / EMA 平滑 / 字节倒退 / 停更归零 / reset）、`DownloadBatchStatsTest` 改按字节 8 例、`DownloadProgressMathTest` 补 `byteRatio` 3 例与 `monotoneProgress` 1 例并删掉合并阶段断言。
- **验证 · L0（26.11.10 本机实测）**：`:app:assembleDebug` BUILD SUCCESSFUL（两 flavor）；`:app:testStandardDebugUnitTest`、`:app:testCoexistDebugUnitTest` 各 **213 suites / 2280 tests / 0 失败 0 错误**。首轮跑出 3 个失败，全是**测试期望写错**（`snapshot()` 只读语义、被拒采样不更新 `prevBytes`、负数用例复用了已有完成量的 stats），已按实现语义修正并补上 `DownloadSectionBytes.lastDone`。
- **未验证（人测 / L2）**：单任务进度是否与「已下 / 总大小」吻合、并行多任务时每行速度是否各不相同、总速度是否等于各行之和、ETA 是否收敛。本机无 Android 设备 / 模拟器，只能真机看；交接单见 [`manual-test-guide-261110-download-speed-progress.md`](manual-test-guide-261110-download-speed-progress.md)（H13~H17）。

### 3.147 新增「顶栏高度」设置：最小高度 + 触摸范围跟着变（26.11.11，用户需求）

- **需求原话**：「先加一个设置可以调节顶栏的高度，同时影响触摸事件的生效范围，默认值就是现在的值」。
- **定案（三项都问过用户，全选推荐项）**：① 语义 = **最小高度**（0 = 跟随内容 = 现状），因为 `R.id.top` 在 50 份布局里全是 `wrap_content`、内容也不都只有标题+时钟（`activity_setting_player_ui.xml` 多一个 `btnFull`），固定高度会压扁内容；② 触摸范围 = **跟随顶栏实际高度**；③ 加高后文字 = **垂直居中**。
- **触摸范围为什么不用写代码**：单击 `setTopbarExit` / 长按 `setupTopbarLongPressToHome` / 双击 `DisguiseGesture` 三套手势本来就装在整条 `R.id.top` 上，top 变高可点区域自动跟着变，因此**没有引入 `TouchDelegate`**，三套手势语义一字未动（用户明确不选「改手势」那一项）。
- **改动**：
  1. 新增 `ui/widget/TopbarHeightPolicy.kt`：`DEFAULT_DP=0` / `MAX_DP=64`、`clampDp`、`resolveMinHeightPx(dp, density)`（`dp<=0 || density<=0f` → 0）、`shouldCenterVertically(px, roundMode)` = `px > 0 && !roundMode`。
  2. `util/SettingsKeys.kt` 加 `TOPBAR_HEIGHT = "topbar_height"`；`util/UiScaleInput.kt` 加 `parseTopbarHeight`（只判可解析，范围交给 `clampDp`）。
  3. `activity/base/BaseActivity.kt`：`setRound()` 末尾调 `applyTopbarHeight()`（普通布局走 `onStart`、asyncInflate 页走 inflate 回调，两条路都经过这里）；新增 `applyTopbarHeight()` 与 `applyTopbarVerticalCenter()`。
  4. `activity/settings/SettingGroupActivity.kt`：布局适配页灵动岛开关之后加 `input_int` 项，「恢复默认」一起写回 0；`activity/settings/SettingsIndex.kt` 同步加搜索 `Entry`（两份平行清单）；`res/values/strings.xml` 加 `setting_topbar_height` / `desc_setting_topbar_height`。
- **两个刻意为之的坑**（别退回）：
  - **圆屏下不叠 `CENTER_VERTICAL`**：`setRound()` 圆屏分支已经把 timeText 排在上、pageName 按屏高比例下移（时钟在上、标题在下），再居中会重叠；圆屏只向下补空白。
  - **只在状态不一致时改 `layoutParams`**：默认值 0 下每页 `onStart` 都会走一遍 `applyTopbarHeight()`，无脑重设会白触发一次重布局（默认值必须与改动前像素级一致）。
- **验证 · L0**：`TopbarHeightPolicyTest`（clamp 合法/越界/负数/Int 极值、0 语义、density 换算 32dp@3.0=96 / 64dp@1.5=96 / 25dp@3.0=75、越界先钳后换、`shouldCenterVertically` 三态）跑 `:app:testStandardDebugUnitTest --tests "…TopbarHeightPolicyTest"` → BUILD SUCCESSFUL。
- **未验证（人测 / L2）**：调 0 / 20 / 40 dp 后顶栏实际高度与预期一致、手势可点区域随高度变大、方屏文字垂直居中、圆屏不重叠且只向下补空白、`activity_setting_player_ui` 的 `btnFull` 没被挤、恢复默认后回到像素级一致。本机无 Android 设备，交真机。

### 3.148 消息页「私信列表」的用户卡片补玻璃质感（26.11.11，用户报「补这个界面的用户列表无玻璃质感的bug」）

- **现象**：外观设置打开「玻璃质感」后，消息页（`MessageActivity`）顶部四张入口卡（回复我的 / @我 / 收到的赞 / 消息设置）是半透明玻璃板，下面「私信列表」的用户会话卡仍是不透明深灰（`@color/card_dark`）。
- **根因**：玻璃质感在两类卡片上走两条不同的路——`MaterialCardView` 靠主题 `materialCardViewStyle` 自己吃到色，再加上 `GlassStyle.applyTo(activity)` 在 `BaseActivity.onCreate` 之后叠的 `GlassOverlay.*`；**普通 `FrameLayout` 卡必须在代码里显式调 `GlassStyle.applyCardBackground(view)`**。`cell_user_list` 是单层 `FrameLayout` + 写死 `@drawable/bg_card_rounded`、不读 `materialCardViewStyle`，而 `PrivateMsgSessionsAdapter.onCreateViewHolder` 只 inflate 没有那一步 ⇒ 与 26.11 搜索「用户」页（§3.132）、关注分组同一类漏接。
- **改动**：
  1. `adapter/message/PrivateMsgSessionsAdapter.kt` 的 `onCreateViewHolder` 在 inflate 之后调 `GlassStyle.applyCardBackground(view)`（开关关着时它直接 `return false`，默认观感不变）。
  2. `ui/appearance/GlassStyle.kt` 两处 KDoc 的调用点清单补上「消息页的私信列表」，免得下次又照旧清单判断「已全覆盖」。
- **顺带覆盖**：`activity/message/ChooseFriendActivity.kt`（反馈 #133 的转发选好友页）复用同一适配器，自动一起修好，两处不必各改一遍。
- **为什么不改布局**：`cell_user_list.xml` 同时被搜索结果「用户」页、关注分组、私信会话三处复用，改成 `MaterialCardView` 要连带改三个适配器的 ViewHolder 类型与点击 / 长按 / 水波纹路径；`applyCardBackground` 本来就是为这个用例准备的既有 API。
- **验证 · L0（26.11.11 本机实测，工作区含上一批未提交改动）**：`:app:assembleDebug` BUILD SUCCESSFUL；`:app:testStandardDebugUnitTest` + `:app:testCoexistDebugUnitTest` 各 **220 suites / 2385 tests / 0 失败 0 错误**。
- **未验证（人测 / L2）**：开关打开后私信会话卡是否与顶部四张卡一致的半透明观感、关掉是否回到不透明深灰、按住时的水波纹仍在（本机无在线设备，且私信列表要登录态才有数据）。交接单见 [`handoff-261111-private-msg-glass.md`](handoff-261111-private-msg-glass.md)（H22~H24）。

### 3.149 设置搜索跳转后目标行短暂高亮（26.11.11，用户要求「搜索到某个设置项，就跳转到对应位置并高亮显示」）

- **需求原话**：「我想增强一下设置的搜索，如果搜索到某个设置项，就直接跳转到对应位置并高亮显示（高亮显示未做，补一下）」。
- **缺口**：跳转早就通了——搜索命中后 `SettingsIndex.Entry.open` 把目标行名塞进 intent 的 `highlight`，五个页面再各自滚到同名行。但 `RefreshListActivity.scrollToHighlight` 只 `scrollToPosition`，滚过去没有任何视觉提示，手表小屏上尤其容易「不知道跳到了哪一行」。
- **定案（四项都问过用户，全选推荐项）**：① 形态 = 主色描边 + 主色淡底，约 1.5 秒后自己淡掉（不常驻，免得用户以为那是选中态）；② 范围 = **所有**接收 `highlight` 的 5 个页面（分组页 + 通用设置页 + 关于页 + 回复设置页 + 播放器设置页）；③ 分组标题（`title` 行）与按钮（`button` 行）被搜到时同样要亮；④ 滚动位置**不改**（仍是 `scrollToPosition`，目标顶到最上方可见位，不做居中）。
- **三种行根，三种画法**（`util/view/SettingsHighlight.kt`）：`cell_setting_*` 五行布局的根都是 `MaterialCardView` ⇒ 描边加粗 1dp→2dp + `strokeColor` 一次性插值到 `ColorScheme.PRIMARY`、`cardBackgroundColor` 向主色插 18%；button 行的 itemView 就是代码 new 的 `MaterialButton` 本体（没有卡片外壳）⇒ 改 `backgroundTintList` 向主色插 55%；`cell_setting_title.xml` 的根是 `LinearLayout` ⇒ 塞一块圆角 `GradientDrawable` 淡色底板（主色 alpha 0x3D）。纯几何量走 `fadeFactor(fraction)` / `lerpColor(from,to,t)` 两个纯函数，其余交给 `ValueAnimator`（`HOLD_MS=1500` + `FADE_MS=450`，线性）。
- **改动**：
  1. 新建 `util/view/SettingsHighlight.kt`（`object SettingsHighlight`，`bind(itemView, highlighted)` 入口，内部 `CardSkin` / `ButtonSkin` / `PlateSkin` 三个皮肤，原始样式存进 `itemView` 的 keyed tag）；
  2. 新建 `res/values/ids.xml`（只放 `tag_setting_highlight`），存皮肤用的 tag key；
  3. `adapter/SettingsAdapter.kt`：加 `highlightPosition`（默认 `RecyclerView.NO_POSITION`）与 `fun highlight(position)`（越界直接 return），`onBindViewHolder` 末尾统一调 `SettingsHighlight.bind(holder.itemView, position == highlightPosition)`；
  4. `activity/base/RefreshListActivity.kt`：`scrollToHighlight` 找到行号后先让 `SettingsAdapter` 记下标红位置，再 `scrollToPosition`（适配器不是 `SettingsAdapter` 时只滚不标红）；
  5. `activity/settings/SettingGroupActivity.kt`：删掉自己内联写的那份滚动定位，改用基类的 `scrollToHighlight`（与另外 4 页共用一条路）。
- **四个刻意为之的坑**（别退回）：
  - **不用 `View.setForeground`**：任意 View 的前景层是 API 23+，本项目 `minSdk 19`，只能按行根类型分别改真实属性；
  - **描边原始值存 `strokeColorStateList`**：`MaterialCardView.getStrokeColor()` 返回的是 `int`，用合成属性 `card.strokeColor` 会吃 `'var strokeColor: Int' is deprecated` 告警，还原时也要走 `setStrokeColor(ColorStateList)`；
  - **`PlateSkin` 只在构造时换一次 `background`**：动画里只调 `GradientDrawable.setColor`，否则每帧改背景都会触发一次 `requestLayout`，滚动时掉帧；
  - **`highlightPosition` 播完不清空**：清空就得再 `notifyItemChanged` 一次，不如让 `bind(false)` 在复用/重绑时负责还原（所以非目标行也要调 `bind`，回收来的 itemView 可能还带着上一行的高亮）。
- **验证 · L0（26.11.11 本机实测）**：`./gradlew.bat :app:compileStandardDebugKotlin :app:testStandardDebugUnitTest "-Pkotlin.incremental=false"` → BUILD SUCCESSFUL；新增 `SettingsHighlightTest` **7 tests / 0 失败 0 错误**（hold 期内恒 1、末尾归 0、hold 后单调下降、`lerpColor` 端点精确、比例越界钳位、四通道含 alpha 都插值、`blend` 等价于 `lerpColor`）；`./gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL。
- **未验证（人测 / L2）**：高亮观感本身——描边加粗是否足够明显、主色淡底会不会盖掉卡片的玻璃质感、标题 / 按钮行的淡色底板块好不好看（标题行原本没有卡片外壳）、`1.5s + 0.45s` 的节奏是否太快或太慢。本机无在线设备，需真机。

### 3.150 02 版崩溃清一：Glide「destroyed activity」两处调用点（26.11.13，控制台待处理崩溃）

- **触发**：用户「仅通过 MCP 查看最近的崩溃，修复他们」，随后收窄为「仅修复最新的 02 版本的」（= `version_code 26110902`，tag `26.11.09.2`）。
- **线上条目**：#310（sdk36 Samsung SM-R9450）、#309（sdk27 hesc XH011）、#295（sdk27 某 S03），异常 `java.lang.IllegalArgumentException: You cannot start a load for a destroyed activity`。
- **retrace 后的两处落点**（MCP `crash_detail` 已解混淆，`retrace_state=1`）：
  1. `Glide.with(Glide.java:574)` ← `LiveInfoActivity.onCreate$lambda$22$lambda$21$lambda$17(LiveInfoActivity.kt:90)` ← `Result.onSuccess` ← LiveData observer `onChanged` ← `LiveData$1.run(LiveData.java:93)`；
  2. `Glide.with(Glide.java:545)` ← `BangumiInfoFragment.initView(BangumiInfoFragment.kt:201)` ← `onViewCreated$lambda$3$lambda$1(:171)` ← `Result.onSuccess` ← `onViewCreated$lambda$3(:169)` ← 同一条 LiveData 回调。
- **根因**：两处都在 `CenterThreadPool.supplyAsyncWithLiveData { }` 的回包里加载封面。`postValue` 派发的 Runnable 回主线程时用户可能已经退出页面，此时 `Glide.with(activity)` / `Glide.with(requireContext())` 取 RequestManager 就校验宿主，`isDestroyed()` 为真直接抛。
- **改动**（两处，链式调用其余部分一字未动）：
  - `LiveInfoActivity.kt:90` `Glide.with(this)` → `GlideUtil.with(cover)`；
  - `BangumiInfoFragment.kt:201` `Glide.with(requireContext())` → `GlideUtil.with(imageMediaCover)`（顺带避开 detached fragment 上 `requireContext()` 抛 `not attached to a context`）；
  - 两个文件各删掉已无引用的 `import com.bumptech.glide.Glide`。
- `GlideUtil.with(View)`（`util/GlideUtil.java:166-175`）在主线程 + 宿主 Activity 健在时返回**同一个** Activity 级 manager（页面暂停/恢复、`ImageAutoLoadScrollListener` 的「翻动不加载」语义不变），已 finish/销毁或非主线程才回落 Application 级 manager，不做销毁校验 → 不再抛。

### 3.151 02 版崩溃清二：Glide「Cannot recycle a resource while it is still acquired」收尾（26.11.13，控制台待处理崩溃第一名）

- **线上条目（02 版共 15 条）**：#269 / #273 / #282 / #287 / #288 / #290 / #291 / #292 / #293 / #297 / #302 / #303 / #304 / #305 / #308，栈恒为 `EngineResource.recycle(EngineResource.java:68)` ← `ResourceRecycler$ResourceRecyclerCallback.handleMessage` ← `Handler.dispatchMessage`，线程 main。
- **新证据（这次才拿到）**：#282 的页面轨迹是 30 秒内 5 次「`VideoInfoActivity` ↔ `SearchActivity`」反复（18:14:56–18:15:30），**根本没进播放器**；#291 也是以 `VideoInfoActivity` 收尾。也就是说崩溃不集中在「起播那一刻」，而集中在**反复进出视频详情页**。
- **对上了**：02 版里「进页面就无条件清缓存」的 `Glide.get(...).clearMemory()` 共有 4 处——`VideoInfoActivity.onCreate`、`MenuActivity.killAndJump`、`VideoInfoFragment.playClick`（起播前）、`BangumiInfoFragment.playOnBili`（起播前）。强制的 `clearMemory()` 会把「仍被 target 占用」的资源也排队回收，延迟 `EngineResource.recycle` 随后抛异常；而 `460736b0`（26.11.09，**晚于 02 版**）只删掉了前两处，`VideoInfoActivity.onCreate` 那行恰是 02 版每次进详情页都执行的。
- **改动**：删掉剩下两处（`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/VideoInfoFragment.kt:884` 与 `app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/BangumiInfoFragment.kt:421` 的 `Glide.get(...).clearMemory()`），原地留一句中文注释说明为什么删。改完全仓 `clearMemory|trimMemory|onTrimMemory` **只剩注释**，Java 侧也没有——应用不再主动喂这条回收路径（磁盘缓存清理 `AppStorageManager.clearGlideDiskCache` 不受影响）。
- **理由边界（必须照抄，别写成已根除）**：清空是**放大器**，不是竞态本身。Glide 4.16 的 `Engine`/`ActiveResources`/`MemoryCache` 加锁交错仍可能在正常逐出时撞上（§3.145 的历史结论不变），真根治要等升级 Glide。本次只是把「应用自己强制造出的回收流量」清零。

### 3.152 02 版崩溃清三：短视频信息流 `Inconsistency detected`（26.11.13，控制台待处理崩溃）

- **线上条目**：#300（02 版，OPPO OWW251 / sdk30），`IndexOutOfBoundsException: Inconsistency detected. Invalid view holder adapter position position=18, oldPos=15`，`RecyclerView$Recycler.validateViewHolderForOffsetPosition` ← `ViewPager2.onLayout` ← 外层 `ViewPager.onLayout`。
- **根因**：`ShortVideoPagerAdapter.getItemCount()` 直接返回 `preloadManager.getItemCount()`（= `allItems.size`），而 `VideoPreloadManager` 的 `loadInitial/loadMore/loadRelated` 在 `CenterThreadPool` 线程就 `allItems.addAll(items)`，通知 `onItemsLoaded/onRelatedLoaded` 才 post 回主线程 → `getItemCount()` 抢在通知之前涨；两批请求交错时两批又都按 `count - items.size` 算出同一个 `start`，插入通知重复覆盖同一段，视图与数据永久错位。
- **改动**：
  1. `util/VideoPreloadManager.kt`：三处 `allItems.addAll(items)` 搬进各自的 `mainHandler.post { if (released) return@post; allItems.addAll(items); onItemsLoaded?.invoke(items) }`（`loadInitial` 里连 `preloadNext()` 一起；`loadRelated` 的空列表分支单独一个只回调的 post），`allItems` 注释改成「主线程写、`preloadVideoUrl` 在后台线程按索引读」；
  2. `activity/video/ShortVideoFeedFragment.kt`：`onRelatedLoaded` 改成先取 `val adapter = feedAdapter` 再用**同一实例**通知（原来 `feedAdapter?.notifyItemRangeInserted(...)` 取值与通知之间可能换成另一个实例）；`onItemsLoaded` 补注释说明「count 与插入通知必须同 turn、同实例」。
- **未动的部分**：`isLoading`/`released` 的 volatile 与 check-then-act、`pendingAdvanceAfterRelated` 的切页顺序、`release()` 的 `allItems.clear()`（主线程调用、且此前已 `viewPager.adapter = null`）。
- **遗留（未改，登记备查）**：`onDestroyView` 之后到 `onDetach` 之间 `isAdded` 仍为 true，`onItemsLoaded` 会走 `adapter == null` 分支给已置空 adapter 的旧 ViewPager2 新建一个；`moveToIndex()` 是无调用方的死代码且不校验线程。二者都不是这条崩溃的成因。

### 3.153 同批复核：屏幕方向崩溃 02 版之后已修，本次未再改（26.11.13）

- 02 版 #306（`KazumiPlayActivity`）、#271/#274~#281（`FavoriteVideoListActivity`）都是 `Only fullscreen activities can request orientation`，落点 `BaseActivity.kt:132` 的 `setRequestedOrientation(...)`。
- `2e504e72`（26.11.09 19:22）已把它包进 `try/catch (IllegalStateException)` 并降级为 `Logu.w`，`git merge-base --is-ancestor 2e504e72 HEAD` 为真，§3.142 已登记。02 版这几条的 `ctime` 早于该提交，属修复前产物 —— **本次不改代码**。

- **验证 · L0（26.11.13 本机实测）**：`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest` → BUILD SUCCESSFUL；单测按 `app/build/test-results/testStandardDebugUnitTest/*.xml` 的 `<testcase>` 计数为 **223 suites / 2414 tests / 0 failures / 0 errors / 0 skipped**。
- **未验证（人测 / L2）**：番剧页与直播页的封面在「退出页面后才回包」时是否照常不加载、短视频信息流连续下滑翻页与相关推荐续接是否还会 Layout 抖动 —— 需要真机按崩溃日志里的路径复现。

### 3.154 兄弟分支功能移植·第一批（26.11.13，三仓对比选题后落地）

- **背景**：按 `docs/review/fork-comparison-2026-11-13.md` 的差距表，用户从 A/B/D/E 四组里勾了 8 项实现、1 项出设计稿（听歌模式）。
- **本期落地**（逐项）：
  1. **G01 弹幕屏蔽词**：新增 `player/DanmakuBlockWordPolicy.kt`（纯解析 + 命中判据，逗号/顿号/分号/空白分隔、转小写、去重保序、单词 ≤32 字、≤200 词）与 `player/DanmakuBlockWordParser.kt`（包装 `BaseDanmakuParser`，照 `ForceScrollParser` 的范式转发四个 setter 后重写 `parse()`）；`player/DanmakuManager.kt` 在 `configureAndPrepare` 里拍一次快照并把 parser 包成「先强制滚动、再屏蔽词」，窗口追加路径 `appendSegmentInternal` 单独判一次（那条路不经 parser）；设置项 `player_danmaku_block_words`（`input_string`）挂在弹幕组，文案进 `player_port_strings.xml`。
  2. **G02 ±N 秒快退/快进**：`player/PlayerSeekStepPolicy.kt`（步长 `3..120`，默认 10，与双击快进秒数**分开**存 `player_seek_step_seconds`）；`PlayerButtons` 新增 `SEEK_BACK`/`SEEK_FORWARD` 两个 **textOnly** 按钮并默认放进 `DEFAULT_RIGHT_GROUP`；`activity_player.xml` 的按钮池里加两个 `TextView`（`text_seek_back`/`text_seek_forward`），`PlayerActivity` 接线 `initSeekButtons/refreshSeekButtons/seekByStep`，跳转沿用 `PlayerJumpTimePolicy.clampMs` 钳边界。
  3. **G03 长视频弹幕时长**：`DanmakuStreamLoader` 新增 `updateDuration(sec)`（只增不清已载段、清失败标记），`PlayerActivity.onPrepared` 拿到真实 `duration` 后回填；`DanmakuApi.segmentCountFor` 公式与兄弟分支一致，差距只在时长来源。
  4. **G06 封面取色**：新增 `player/CoverTintPolicy.kt`（12 色相×4 明度×4 饱和度加权投票 + 深色 UI 调色，HSV 换算手写以保持纯 JVM 可测）；`ui/widget/HighEnergyProgressBar.kt` 加 `setAccentColor(@ColorInt)`（保留原 A8/33 不透明度）；`PlayerActivity.applyCoverTint()` 用 Glide 解 48×48 小图投票染色，没有 `cover` extra（直播/本地/第三方源）或取不到色时静默保持主题主色。
  5. **G07 高能曲线弹幕密度兜底**：新增 `player/DanmakuDensityHighEnergy.kt`（10 秒分桶、<30 条或 <60 秒不画、非零桶 95 分位定标 + sqrt 抬低段）；`DanmakuManager` 记 `loadedTimes`（两条装载路径）并暴露 `loadedDanmakuTimes()`；`PlayerActivity.loadHighEnergyData()` 的两个失败分支改为延迟 6 秒一次性用本地密度兜底（不轮询不重试）。
  6. **O5 Cookie 合并白名单**：新增 `util/CookieMerge.kt`（鉴权键 6 个大小写不敏感、键名归一成本地拼写、空值即删但 `ac_time_value` 空值不清），`util/NetWorkUtil.java` 的 `saveCookiesLocked` 改走它。**刻意偏离兄弟分支**：`ALLOW_AUTH_COOKIE_UPDATES = true`——B 站会在任意响应里轮换 `bili_jct`，全拦会让 POST 静默 -111；「不删/不清空」由合并引擎兜住。
  7. **O6 华为/鸿蒙默认软解 + ABI 闸**：新增 `player/PlayerCompatPolicy.kt`（纯判据），`player/IjkNativeLibs.kt` 在 `ensureLoaded` 最前播种（**只写 `player_codec` 不存在的存档**，用户手动值永不被覆盖）+ ABI 闸（不匹配置 `STATE_FAILED` 但**不** `markFailed`、不上报）。兄弟分支的「换系统播放器」分支**不移植**：本项目只有 ijk 一个内核（见 `bailOnNativeFailure` 注释），要做回落等于引入第二个播放引擎，已在对比报告 §4 判为不建议。
  8. **O7 全局转场动画开关**：新增 `util/PageMotion.kt`（`windowAnimationsFor`/`shouldSuppressTransition` 纯判据 + `apply`/`forget`），`activity/base/BaseActivity.kt` 三处接线，设置项 `ui_disable_page_transitions` 挂在「外观设置」组末尾，文案进 `appearance_port_strings.xml`。覆盖 109 个走 `BaseActivity` 的页面；`PlayerActivity`/`SplashActivity`/`GetIntentActivity`/两个伪装页不在此列。
  9. **E 未读角标回菜单刷新**：新增 `util/MessageUnreadRefresh.kt`（`MessageUnreadRefresh` 纯状态机：account/revision/sequence 三元组丢过期响应、单飞、5 秒最小间隔、失败保留旧值 + `MessageUnreadRefresher` 胶水），`BiliTerminal` 启动块与 `activity/MenuActivity.kt`（onResume 触发 / onPause 撤观察者）接线。**不做后台轮询**。
- **验证 · L0（26.11.13 本机实测）**：`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest` → BUILD SUCCESSFUL，2486 条单测 0 失败（新增 5 份测试：`DanmakuBlockWordPolicyTest` 8、`DanmakuDensityHighEnergyTest` 7、`PlayerSeekStepPolicyTest` 7、`CoverTintPolicyTest` 8、以及 steady 侧 `CookieMergeTest` 18 / `PageMotionTest` 7 / `PlayerCompatPolicyTest` 16 / unread 侧 `MessageUnreadRefreshTest` 14）。首轮曾 6 条失败，全是测试自身口径问题（朴素包含把 `script` 判成命中 `sc`、定长编号被前缀误命中、默认布局规范串未同步新按钮、单飞测试没跨过 5 秒间隔），已逐条修正。
- **未验证（人测 / L2）**：屏蔽词是否真的不显示（含拖动跨段补拉的弹幕）、±按钮的落点与「更多」格里的取放、长视频（>60 分钟）后半段是否补出弹幕、封面色在深/浅两套主题下是否看得清、缺 PBP 数据时曲线是否像话、华为机首次是否软解且手改后不被改回、转场开关关掉后 109 个页面是否都立刻切换、回主菜单角标是否在 1 秒内更新且读掉后归零。见 `docs/review/manual-test-guide-261113-fork-port.md`。

### 3.155 收藏夹视频列表页 `Inconsistency detected` 崩溃（26.11.13，用户贴栈，控制台无此条）

- **触发**：`FavoriteVideoListActivity`（收藏夹内的视频列表）翻页 / 切排序 / 换关键词之后，主线程某趟 layout 抛 `java.lang.IndexOutOfBoundsException: Inconsistency detected. Invalid view holder adapter positionA{439506 position=40 id=116952160863790, oldPos=0, pLpos:0 scrap [attachedScrap] tmpDetached no parent}`，栈是 `RecyclerView$Recycler.validateViewHolderForOffsetPosition` ← `LinearLayoutManager.onLayoutChildren` ← `RecyclerView` 的 `Choreographer` 回调。`id` 非 -1 说明走了 stable id（`VideoCardAdapter.init { setHasStableIds(true) }`，`getItemId()` 直接按下标取卡片）。
- **线上条目**：`crash_list` 按 `Inconsistency`（8 条）/ `position=40`（0 条）/ `FavoriteVideoListActivity`（9 条，全是 26110902 的 `Only fullscreen…` 方向崩溃，见 §3.153）**都没命中这一条** —— 控制台里没有这份样本（推测来自用户本机或 AVD 的 debug 构建，没有 version_code / 机型可佐证）。同源、已修的两条：#160 收藏夹管理页（`FavoriteFolderListActivity`，见 §3.14x）、#300 短视频信息流（`ShortVideoPagerAdapter`）。
- **根因**：`VideoCardAdapter.getItemCount()` 直接返回 `videoCardList.size`，而这份 list 就是挂在 RecyclerView 上的活数据；`fetch()` 却是在 `CenterThreadPool` 线程把新页 `add` 进去，`notifyDataSetChanged()` / `notifyItemRangeInserted()` 得等回到主线程才发。这中间 `getItemCount()` 已经涨了、adapter 眼里还是旧的条数，下一趟 layout 就判 holder 位置越界。另外切排序 / 换关键词时主线程先 `videoList.clear()` 再发请求，**在飞的旧页回包会追加到同一份已清空的 list 上**，"清空" 被旧结果糊掉，而且同样没有对应的通知。
- **改动**（`activity/user/favorite/FavoriteVideoListActivity.kt`）：
  1. `fetch(page)` → `fetchPage(page, out)`：后台线程只往调用方给的临时 `ArrayList` 里取数据，**绝不碰 `videoList`**；
  2. 新增 `showList(items)`：整份替换 = 新 `ArrayList` + 新 `VideoCardAdapter` + `setAdapter`，并把 `selectionMode` 带过去（否则多选条亮着、勾选框没了）；
  3. `loadFirstPage()`：发起时 `val generation = ++listGeneration`，回包在主线程先判 `isFinishing/isDestroyed`、再判代数，然后 `showList(loaded)`；空态文案与 `pendingSelectMode` 都改成看 `loaded`；`catch` 里的 `loadFail(e)` 也搬进 Runnable 内部判活；
  4. `continueLoading(page)`：只捕获代数不增，回包代数对不上就丢弃并 `setRefreshing(false)` 复位翻页占用（否则 `isLoading` 卡住、`goOnLoad` 再也进不来）；`videoList.addAll(loaded)` 与 `notifyItemRangeInserted(lastSize, loaded.size)` 在同一个 `runOnUiThread` 里做完；
  5. `switchSort()` / `applyKeyword()`：去掉主线程的 `videoList.clear()` + `notifyDataSetChanged()`，整份内容交给 `loadFirstPage()` 换新实例；
  6. `loadAggregate()`：`listGeneration++` + `showList(cards)`，让在飞的翻页一并作废；
  7. `playFavoriteVirtualCollection()`：后台线程改用 `val snapshot = ArrayList(videoList)`，避免迭代期间被翻页改到（`ConcurrentModificationException`）；
  8. 顺手修 `loadAggregate()` 的 `catch`：原来在后台线程直接调 `loadFail(e)`（会碰 `SwipeRefreshLayout` 与 Toast），改成 `runOnUiThread { if (!isFinishing && !isDestroyed) loadFail(e) }`，与另两处一致。
- **文档**：`docs/agents/pitfalls.md` 新增两条 —— 「整份替换必须换 list + 换 adapter」「整份重拉要带代次守卫」（并把本页作为 Activity 侧范例，与 `SearchFragment.loadGeneration`、`VideoPreloadManager` 同族）。
- **同批盘点（只静态审计，本次未改）**：同根因还存在于 `NoticeActivity`、`SponsorActivity`、`FavouriteOpusListActivity`、`DynamicTopicActivity`（`clear()` 还没通知，比本页更狠）、`LocalPageChooseActivity`（删某一 P）；`DownloadListActivity`、`LocalListActivity` 属「换整份内容」的条件复现。**26.11.13 已拍板：本轮只修本页，其余按清单记账待办**（真出现线上崩溃再按同一套路改）。
- **验证 · L0（26.11.14 10:19 补测）**：`:app:assembleDebug --no-parallel --max-workers=2` **BUILD SUCCESSFUL**（standard / coexist 双 flavor 全出）；`:app:testStandardDebugUnitTest --rerun` **BUILD SUCCESSFUL**，按 XML 计 **233 suites / 2515 tests / 0 失败 0 错误 0 跳过**；markdownlint 77 文件 0 问题。26.11.13 23:1x 那三次失败确系并发构建重写 `R.jar`（4960 条 `Unresolved reference 'R'` 与源码无关），对方收工后重跑即绿；代码随 `ffc2984b` 提交并推 `main`。
- **未验证（人测 / L2）**：真机滚动到第二屏触发翻页、翻页途中切排序 / 换关键词、空收藏夹与搜索无结果的空态、多选态下切排序后勾选框是否还在 —— 需要真机按崩溃路径复现。

## 4. 历史章节索引

> 完整正文在 `docs/archive/review/fix-progress-history.md`；§3.0~§3.51 的状态快照（26.10.09~26.10.21）在 `docs/archive/review/fix-progress-snapshots-261009-261021.md`。**编号在历史上有重复**（`## 二十二` / `## 二十九` 各出现两次、`## 30.` 是阿拉伯数字写法，原为不打断既有交叉引用而保持不动），下表**以归档文件的物理行序为准**。
> §一~§三十 的「收尾状态」按归档正文与 §五 / §十 的登记推断：批次类记「已闭环」，正文明确登记遗留的记「部分遗留」，总览 / 明细 / 建议 / 验证记录类记「纯历史」。

| §编号 | 标题 | 一句话内容 | 收尾状态 |
| --- | --- | --- | --- |
| 一 | 修复总览 | 286 条基线（Critical 23 / High 52 / Medium 105 / Low 106）+ 2026-09-24 深度审查新增 严重 10 / 中等 13 组 / 轻微 11 | 纯历史 |
| 二 | 已修复问题明细 | 逐轮修复记录，逐条 file:line（第一~四轮表格为主） | 纯历史 |
| 三 | 审查前已修复（本次核查确认，无需改动） | 审查前已完成、本次核查确认无需改动的项 | 纯历史 |
| 四 | 构建验证状态 | 当时的构建 / 单测记录与结论 | 纯历史 |
| 五 | 待处理问题（按优先级） | 绝大多数已 `[x]`；仍未关闭的 A7 WebView 输入校验 / A8 教程键污染 / A9 硬编码专栏 id / E1 拆 PlayerActivity / 发布侧 APK 校验等见 §2.2 | 部分遗留 |
| 六 | 下一步建议 | 4 条早期建议，已过时 | 纯历史 |
| 七 | GitHub issue 修复轮次（2026-10-03） | issue #1 手表右滑返回与滑动调进度冲突；根因 `android:windowSwipeToDismiss` 在窗口层直接 finish + `setTheme(ColorScheme.themeResId(theme))` 覆盖 `Theme.NoSwipe*` | 已闭环 |
| 八 | 发版链路改造：Gitee 发行版同步 + 更新检查改读发行版（2026-10-03） | 发行版同步到 Gitee，客户端更新检查改读发行版而非 tag | 已闭环 |
| 九 | 高能进度条接口变更适配（2026-10-04） | pbp 接口变更后的适配 | 已闭环 |
| 十 | 26.10.04 逐条拍板后的待做队列 | A1~A12 / C6b / E1~E6 结论映射表 + 已作废项（D2 跑马灯、D3 圆屏、D5 候选词）+ 数字勘误 + 8 批落地顺序；仍开放的即 A7 / A8 / A9 / E1 | 部分遗留 |
| 十一 | 26.10.04 批次 1：A 组快修落地记录 | A 组快修逐条落地 | 已闭环 |
| 十二 | 26.10.04 批次 2：A1 csrf 实时化 + A10 评论点踩 | csrf 实时化与评论点踩 | 已闭环 |
| 十三 | 26.10.04 批次 3：性能参数接线 + 列表增量刷新越界 + 崩溃页独立进程 | 性能参数接线、增量刷新越界排查（判据进 `architecture-map.md` §7.9）、崩溃页独立进程 | 已闭环 |
| 十四 | 26.10.04 批次 4：SettingsKeys 收敛收尾 + 删假批量 API + 更新包校验 | E4 键名收敛（`SettingsKeysTest` 钉键名）+ 删假批量 API + 更新包校验（`ApkVerifier`） | 已闭环 |
| 十五 | 26.10.04 批次 5（1/4）：私信会话管理（C13） | 私信会话管理（置顶 / 删除 / 查看主页） | 已闭环 |
| 十六 | 26.10.04 批次 5（2/4）：私信发图（C12） | 私信发图；`size` 小 1024 倍缺陷（见 `architecture-map.md` §7.14） | 已闭环 |
| 十七 | 26.10.04 批次 5（3/4）：新消息通知栏通知（C14） | 新消息通知栏通知 | 已闭环 |
| 十八 | 26.10.04 批次 5（4/4）：追番更新提醒（C16） | 追番更新提醒 | 已闭环 |
| 十九 | 26.10.04 批次 6（1/8）：评论置顶 / 取消置顶（C3） | 评论置顶与取消置顶 | 已闭环 |
| 二十 | 26.10.04 批次 6（2/8）：评论楼中楼排序（C4） | 楼中楼排序开关（纯客户端 `Reply.sortReplies`）；后续由 §30. 重做成真双向排序 | 已闭环 |
| 二十一 | 26.10.04 批次 6（3/8）：带图评论的三个静默出错（C6b） | 带图评论三个静默出错；12066 未复现 | 部分遗留 |
| 二十二 | 26.10.04 批次 6（4/8）：动态编辑与管理菜单（C7） | 动态编辑与管理菜单（点击与长按弹同一个菜单） | 已闭环 |
| 二十三 | 26.10.04 批次 6（5/8）：动态置顶与取消置顶（C8） | 动态置顶与取消置顶 | 已闭环 |
| 二十四 | 26.10.04 批次 6（6/8）：动态定时发布（C9） | 动态定时发布 | 已闭环 |
| 二十五 | 26.10.04 批次 6（7/8）：话题广场与话题动态列表（C10） | 话题广场与话题下动态列表；广场仍单页不翻页 | 部分遗留 |
| 二十六 | 26.10.04 批次 6（8/8）：视频笔记查看（C27） | 视频笔记查看；「无法正常加载」根因未确证 | 部分遗留 |
| 二十七 | 26.10.04 批次 7（1/4）：稍后再看「未看完」分类（C18） | 稍后再看「未看完」分类；服务端 `viewed=2` 语义待真机 | 部分遗留 |
| 二十八 | 26.10.04 批次 7（2/4）：收藏夹排序与复制 / 移动（C19） | 收藏夹排序行与复制 / 移动 | 已闭环 |
| 二十九 | 26.10.04 批次 7（3/4）：收藏夹多选删除（C20） | 收藏夹多选删除 | 已闭环 |
| 三十 | 26.10.04 批次 7（4/4）：关注分组增删改（C21） | 关注分组增删改（系统分组不可编辑） | 已闭环 |
| 三十一 | 26.10.04 批次 8（1/2）：拆分 DownloadService（E2） | `service/download/` 分层（`DownloadPathSpec`/`DownloadProgressMath`/`DownloadProgressStore`/`DownloadRepository`/`DownloadNotifier`），对外 `@JvmStatic` API 一字未改；F4 漫画裁为不做；测试数 38 类 / 327 例 | 已闭环 |
| 三十二 | 26.10.05：弹窗（选择框）主题接入 + 终端列表收口 | 三个缺失主题属性、7 份 `ThemeOverlay.<X>.Dialog`、`dialog_background.xml`、`util/TerminalDialog.kt`（369 行四方法）、17 处调用点全迁 | 已闭环（当时的编译验证缺口由 §36.3 关闭；对比度未重测属遗留） |
| 二十二（第二处） | 26.10.04 批次 7（5/5）：置顶会话排序 + 收藏夹排序行 + 关注分组移动成员（C22） | `util/SessionSorter.kt`、`FavoriteVideoListActivity` 链路排查（代码无缺陷 + 诊断埋点）、`FollowApi.moveFollowTagUsers` + 长按移动成员 | 部分遗留（真机未验证） |
| 三十三 | 26.10.05：片头片尾跳过默认开启 + 新增「通知设置」大类 | `player/SkipOpEdPrefs.kt`、`GROUP_NOTIFY` 分组、`SettingsIndex` 条目 | 纯历史（后由 §四十三 整体撤回；「通知设置」部分仍生效） |
| 三十四 | 26.10.05：话题广场加载不出来 + `#话题#` 不解析（C23） | `TopicApi` 换 `pub/search`、三处富文本 switch 补 `RICH_TEXT_NODE_TYPE_TOPIC`、`StringUtil.TopicClickableSpan`、`LinkUrlUtil.parseTopicId` | 已闭环（广场单页不翻页属遗留） |
| 二十九（第二处） | 26.10.04 批次 6 复查（C27 视频笔记 + C6b 带图评论） | `NoteApi.failureText` 分级 + `BiliNote` 埋点、`img_size` 改 double / `buildPictures`、私信 `buildImageContentOfKb` | 部分遗留（笔记根因未确证、12066 未复现） |
| 30. | 长按冲突与楼中楼排序二次切换失效（task-4） | `Reply.sortReplies` 真双向排序 + `ctime`、`DynamicHolder.showManage()` 去重、`ReplyAdapter.canManage`、`cell_reply_list.xml` 兄弟节点分析 | 部分遗留（§30.5b 未决项、真机未验证） |
| 三十五 | 26.10.05：稍后再看「未看完」为空（C18 回归）+ 新增「清除所有已看完」（task-2） | 服务端 `viewed` 参数、`isUnfinished`/`filterUnfinished` 判据翻转、`history/toview/clear` + `clearWatched` | 部分遗留（服务端 `viewed=2` 语义待抓包确认） |
| 三十六 | 26.10.05 Lead 终验：全队冻结后的串行全量验证 + 本轮问题闭环对照 | 13 条用户报告逐条对照表、43 类 / 400 例全绿、`AlertDialog` 自检 0 命中、方法论 5 条 | 已闭环（关闭 §29.4/§30.4/§35.6 的构建缺口） |
| 三十七 | 26.10.05 批次 8：长按操作面板（复制收进弹窗）+ 顶部工具条滚动收回 | `util/LongPressPrefs.kt` + `long_press_panel_enable`、「复制文字」进面板、`setupAutoHideBars` / `expandAutoHideBars` / `util/view/ScrollRetractDecider.kt` | 部分遗留（真机手感未验证） |
| 三十八 | 26.10.05 线上 release 崩溃：动态详情页 NPE（用户提供混淆栈） | mapping retrace 定位 lambda、`Dynamic.ensureDetailFields()`、`diFragment.view!!` 改 `view?.let`、6 例新单测 | 部分遗留（未真机复现） |
| 三十九 | 26.10.05 第三批真机反馈：收藏夹顶部工具条三处 | `supportsChangeAnimations = false`、`manageBar` 只在多选态可见 + 长按菜单「多选」入口、`ScrollRetractDecider.action()` 展开条件收紧为「仅 `!canScrollUp`」、`suppressClickAfterLongPress` | 部分遗留（三处均未真机复现） |
| 四十 | Release 说明漏掉更新日志：根因与兜底（26.10.05 发版后） | `.github/scripts/extract_update_log.py`、`build-release.yml` fail-closed、AGENTS/SKILL 硬要求 | 部分遗留（26.10.05 Release 正文未人工补） |
| 四十一 | 26.10.09：自建反馈界面 + 自建公告源 + 匿名统计 / 崩溃自动上报（新功能） | `server/` 全栈 + `api/TerminalApi.java` + `util/TelemetryReporter.kt` / `CrashReporter.kt` / `CrashTrail.kt` + `activity/FeedbackActivity.kt` + 隐私文案改写 + §41.8 隐私再同意闸门（`PRIVACY_CONSENT_VERSION` / `PRIVACY_PROMPTED_VERSION` / `TerminalDialog.choice`） | 已闭环（明细回归清单待补）；正文见 `docs/archive/review/fix-progress-history.md` |
| 四十二 | 26.10.09：管理控制台 WebUI（遥测 / 反馈 / 崩溃 / 公告四合一）+ 服务端并发加固 | `server/console.html` 零依赖单文件、`run_in_threadpool` + `synchronous=NORMAL`、无状态签名 Cookie + 双 CSRF 防线、`console.zsapp.asia` vhost / 证书、`backup.sh` + 探活、selftest 39 项 | 部分遗留（无告警渠道、无 TOTP/IP 白名单、无浏览器回归） |
| 四十三 | 26.10.10 补丁：紧急撤回「自动跳过片头片尾」（2610102） | `SkipOpEdPrefs.FEATURE_ENABLED = false` + `isEnabled()`、四处落点、`SkipOpEdPrefsTest` +3 例、tag `26.10.10.1` | **26.10.22 已恢复**（`FEATURE_ENABLED = true`，口径改为默认关闭），详见 §3.3 恢复记录 |
| 四十四 | 26.10.11：播放器网络韧性（弱网卡死 / 直播线路 / 切后台位置错） | `PlayerActivity.kt` 加读超时与重连选项 + 停滞看门狗 + `onSaveInstanceState` + 重挂 Surface 改 seek `video_now`；新增 `util/live/LiveStreamSelector.kt`（+14 单测）与 `LiveInfoActivity.kt` 接线、`PlayerData.liveUrlCandidates`；删 `max_cached_duration` | 部分遗留（真机只跑到「位置未复现回跳」；看门狗未真机触发；P0-2 重取 URL、P1-3 后台保活未做） |
| 四十五 | 26.10.11：共存版 product flavor（普通版 + 共存版双包） | `standard` / `coexist` flavor、FileProvider authority 去硬编码、`app/src/coexist/res/mipmap-nodpi/icon.png` 图标覆盖（两版同名 RE:哔哩终端，靠图标区分）、`UpdateRelease.pickApkUrl(namePrefix)`（+4 单测）、`build-release.yml` 整理发行 APK 步骤 | 已闭环（真机双包共存 + 数据隔离 + 同名 + 图标已验；共存版自更新待下次发版） |
| 四十六 | 26.10.11：弹幕「一抖一抖」——时钟基准错位 | `player/DanmakuManager.kt` 的 `updateTimer` 不再 `timer.update(pos)`（删掉位置回调参数）；对齐改到主线程 `PlayerActivity.progressChange()` + `DanmakuSync` 判定 + `mDanmakuView.seekTo(video_now)`（`SEEK_POS` 会重设 `mTimeBase`）；`onPrepared` 起播锚定；短视频播放器同样处理 | 真机取证已完成（42 次/秒硬校正、偏差最大 199s）；修复后待用户实机确认 |
| 四十六 | 26.10.11：线上崩溃 `PlaybackService` 5 秒义务未兑现（`RemoteServiceException`） | 根因 = `onPause` 的 `startForegroundService` 与 `onResume`/`onDestroy` 的 `stopService` 竞态（低内存手表上服务创建慢，`stopService` 先到 → 义务作废 → 5 秒后崩主线程）+ `startUpdateTimer()` 误放 `onCreate`；修法 = `foregrounded`/`stopPending` 两标志 + `onCreate` 就 `enterForeground()` + 兜底通知 + 定时器移到进场之后 | 已过构建与单测（两 flavor 各 478 例全绿）；真机未复现 |
| 四十七 | 26.10.11：控制台补「反馈 / 崩溃」已处理开关，并关掉已解决项 | `server/rebiliterminal_api.py`（`_db_set_handled` + `POST /admin/api/{feedback,crash}/handled`；两个列表接口加 `handled` 与 `pending=1`）、`server/console.html`（默认只看未处理 + 状态标签 + 标记/重开按钮 + 待处理计数）、`server/selftest.py` +17 例、`server/README.md` 与 `docs/architecture-map.md` 同步 | 已闭环（服务器实测：56 项 selftest 全通过、curl 覆盖 403/415/400/404；crash #2「PlaybackService 5 秒义务」与 feedback #2 测试内容已标记；crash #3/#4/#5 `TimeUtil$cache` 未修故保留待处理） |
| 四十八 | 26.10.16：日志基建 + 崩溃 / 反馈附带日志 + 「我的反馈」页面 | `util/Logu.java` 内存环形缓冲（`RING_MAX_CHARS=65536`、只记 W/E/F、不落盘）、`util/LogSanitizer.kt`（键=值脱敏，复用 `NetWorkUtil` 两份键名清单）、`util/LogExporter.kt`（FileProvider 分享）、`util/CrashReporter.kt` 的 `mergeLog()`、`ErrorCatch.java` 的 `ring_log` Intent extra、`CatchActivity.kt` / `FeedbackActivity.kt` 勾选框、`TerminalApi` 的 `log` 参数与 `getMyFeedback()`、`activity/feedback/` 列表 + 详情、服务端 `feedback.log` 列（`SCHEMA_VERSION` 1→2）+ `/terminal/feedback/mine` + `/admin/api/{feedback,crash}/detail` + `log_len` | 部分遗留（服务端已上线；客户端已真机验证通过，仅日志本地导出、崩溃自动上报端到端、跨设备找回未覆盖，见 §3.20） |
| 四十九 | 26.10.16：反馈对话——用户可追加说明 | 服务端 `feedback_comment` 表（`SCHEMA_VERSION` 2→3，**v3 刻意无 DDL**）+ `POST /terminal/feedback/reply` + 归属判据 `_MINE_SQL` 与 `/feedback/mine` **共用同一份** + 追加即把 `handled` 打回 0（reopen 语义）+ `_db_feedback_thread` 合并两侧消息 + 管理台 `user_reply_count` 与对话抽屉；客户端 `TerminalDialog.input()`（+`Sheet.addInput`）、`FeedbackReply.author`、`submitFeedbackReply`（走新增 `postWithHeaders`）、`FeedbackDetailActivity` 时间线与「追加说明」入口、`FeedbackListActivity` 结果码重拉 | 部分遗留（跨设备 `X-Mid` 追加、发送失败重开预填未真机覆盖，见 §3.21） |
| 五十 | 26.10.19：线上崩溃两路清剿（#43–#49） | A 路：#43–#47/#49 无 message NPE = `UserDynamicFragment.continueLoading` 里 `adapter!!`（首屏加载失败 → adapter 从未创建 → 空列表拖动翻页）；三处 `runOnUiThread` 加 `isAdded && view != null` + `adapter?.`，`setRefreshing(false)` 进 `finally`，顺带修 `UserVideoFragment` 插入起点漏 `+1`。B 路：#48 `libijksdl.so` 加载失败 = `UnsatisfiedLinkError`（`Error`）冒到主线程 + `IjkMediaPlayer()` 构造会二次加载；新增 `player/IjkNativeLibs.kt`（失败锁死 + 诊断上传），`PlayerActivity` / `IjkPlayerBridge` / `VideoPlayerCore` / `ShortVideoPlayerActivity` 全部改判返回值并降级 | 部分遗留（构建与两 flavor 单测全绿，**未真机复现**；见 §3.29） |
| 五十一 | 26.10.24：对比审计后的第一批修复（清单见 `docs/review/compare-with-re-biliterminal-261024.md`） | P0-1 图片管线 Cookie 外发：新增 `util/ImageRequestPolicy.kt` + `helper/CustomGlideModule.kt` 应用层/网络层双过滤（站外去 Cookie/Referer/Origin）；P0-2 收藏夹编辑丢隐私/简介：新增 `FavoriteApi.getFolderInfo()` + `attr` bit0 解析，编辑页改为先回填再保存；P1-1 下载通知权限硬门禁 → `DownloadNotificationGate` 改为并行请求不阻断；P1-2 教程键名统一到 `TutorialStore`（`tutorial_ver_<id>`）+ 管理页重写；P1-3 动态「仅自己可见/关闭评论」接线；P1-4 发版 tag 校验（`build-release.yml` 增 `verify-version` job）；P1-5 下载服务 START_STICKY 重启续跑（`DownloadServiceRecovery`）；新增「无极调音」（`PlayerVolumePolicy` + 播放器滑条，短按音量键仍调系统音量）；`strings.xml` +34 条 | 部分遗留（报告 91 条中已修 7 条：P0 2/2、P1 5/20；其余 84 条未动。`assembleDebug` 双 flavor 成功；`testStandardDebugUnitTest` 83 suites / 901 tests / 0 failures、`testCoexistDebugUnitTest` 通过；**播放器/UI 时序改动未真机验证**；afdiancdn 去 Referer 是否 403 待真机） |

| 五十二 | 26.10.24：对比审计 §3.3 安全组五条 P2 加固 + P2-9 部分（清单见 `docs/review/compare-with-re-biliterminal-261024.md` §13.0 第二批） | 图片目录：默认改应用专属 + `PictureMediaStore.publishToGallery()` 发布进 `Pictures/哔哩终端`（API 29+ 走 MediaStore + `IS_PENDING`，旧系统 `scan`），`FileUtil.getConfiguredPictureDownloadPath()`/`PictureSavePolicy` 分档，manifest 注明 MANAGE 只服务用户自定义公共目录；Cookie：`CookieDomainPolicy` 严格域校验（equals/子域点后缀）+ 响应 host 校验（fail-closed）；直播弹幕：wss host 必须 B 站域、端口校验，握手只带 `SESSDATA`；深链：`DeepLinkPolicy` 限定 http/s 到 bilibili.com 子域与 b23.tv，删 `debug-host` 日志；极验：`CaptchaParamPolicy` 白名单 + `JSONObject.quote`；遥测：精确内存只报档位（`TerminalApi.ramLevel`），`device_key` 按产品需求**保留采集**（26.10.24 用户决定回退该子项） | `assembleDebug` 双 flavor 成功；单测 87 suites / 935 tests / 0 failures（新增 4 个测试类 + 重写 `TerminalApiTest`）；**图片发布/直播弹幕/深链/极验均未真机验证**（见 §2.1） |

| 五十三 | 26.10.25：短视频横滑「一顿一顿 / 跳页 / 卡中间 / 按住返回崩溃」整体修复 | 外壳从 `ViewPager2 + SwipePageLayout` 自绘 fakeDrag 换回经典 `ViewPager`（与普通视频详情页同款，原生滚动）：`activity_short_video_player.xml` 改 `androidx.viewpager.widget.ViewPager`，`ShortVideoShellAdapter` 改 `FragmentPagerAdapter`（`BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT`，页面壳实例稳定、`getItemPosition` 恒 `POSITION_UNCHANGED`，换视频只热更新内容不重建页面）；`SwipePageLayout` 重写为只保留「第 0 页右滑退出」边缘手势（不设 `edgeExitCheck` 则永不拦截，进度条/横向滚动控件按坐标避让）；滑去详情页时暂停信息流（`pauseForShellSwipe`/`resumeAfterShellSwipe`），滑回恢复；详情三页维持延迟合并渲染（`RENDER_DELAY_MS=180` / `STAGGER_MS=60`）；删除 `beginFakeDrag/endFakeDrag` 与返回键收口逻辑（`Cannot change current item when ViewPager2 is fake dragging` 崩溃路径随之消失） | 已闭环（`assembleDebug` / `assembleRelease` 均通过；真机 vivo V2229A 按住静止、左右滑一页一页、右滑退出、返回键均正常，26.10.25 用户复测确认「修好了」） |

| 五十四 | 26.10.25：短视频页开启「圆屏适配」后并未适配 | `activity/video/ShortVideoPlayerActivity.kt`：新增 `roundAdapted` / `roundPadding` / `contentWidth`-`contentHeight`；`PageHolder.applyRoundInsets()` 收放顶栏、进度条、时间文本、弹幕留白；`adjustVideoSize()` 增圆屏内接圆分支并按内容区居中 | 部分遗留（MuMu 320×320 模拟圆屏：开/关两态实测通过，`assembleDebug` + `testStandardDebugUnitTest` 通过；真手表未验证，见 §3.65） |

| 五十五 | 26.10.26：评论区「文A」翻译（gRPC `TranslateReply`） | 新增 `api/ReplyTranslateApi.java`（手工 protobuf 编解码 + 5 字节 gRPC 帧 + `x-bili-locale-bin` 目标语言头；`translateReplies`/`translateReply`/`encodeTranslateRequest`/`wrapGrpcFrame`/`parseTranslateResponse`/`buildLocaleHeader` 都是可单测的纯函数）、`res/drawable/icon_translate.xml`（从 B 站 9.16.1 反编译资源 `ic_translate_no_padding.xml` 提取的「文A」6 条 path，fill `#EBE0E2`）、`cell_reply_list.xml` 新增 `item_reply_translate` + `replyTranslation`、`ReplyAdapter.kt`（按钮绑定、已有译文切换、`notifyReplyChanged` 重绑整行）、`model/Reply.java`（`translatedMessage`/`translationShown` + `hasTranslation`/`toggleTranslation`/`applyTranslation`）、`strings.xml` 3 条、`ReplyTranslateApiTest`（21 例）+ `ReplyTranslationTest`（7 例）；文档 `FEATURES.md` §9.5/§9.6、`architecture-map.md` §7.55 与 API 表、`bilibili-api-notes.md` §7 | 部分遗留（`assembleDebug` 双 flavor 通过；`testStandardDebugUnitTest` / `testCoexistDebugUnitTest` 各 91 suites / 988 tests / 0 failures；模拟器 sdk_gwear_x86_64（Android 14，466×466）：视频 80433022 的详情页与列表页点「文A」都实测出现英文译文、再点收起，gRPC 请求 HTTP 200 / 634–643ms，列表 `position-1 → index` 映射正确；per-app locale 改 zh-CN 后同一条中文评论返回空 map、界面不显示译文也不卡死；**失败路径（12002 等业务错误）未上设备、真手表未验**；API 19/20 无 HTTP/2 ALPN 时按普通网络失败提示，见 `docs/bilibili-api-notes.md` §7） |

| 五十六 | 26.10.26：评论区翻译目标语言固定中文 + 入口暂时隐藏 | `api/ReplyTranslateApi.java`：新增 `UI_ENABLED = false`（带开关说明，恢复改成 `true`）与 `TARGET_LOCALE = Locale.SIMPLIFIED_CHINESE`，javadoc 从「跟随系统语言」改为「固定简体中文」；`adapter/ReplyAdapter.kt` 的 `bindTranslation` 开头按 `UI_ENABLED` 提前返回，隐藏 `item_reply_translate`/`replyTranslation` 并清掉点击监听（`buildLocaleHeader(TARGET_LOCALE)`，不再 `Locale.getDefault()`）；`ReplyTranslateApiTest` +1 例（目标语言固定中文断言，共 22 例）；文档 `FEATURES.md` §9.5/§9.6、`architecture-map.md` §7.55、`bilibili-api-notes.md` §7 标注「入口隐藏」 | 部分遗留（`assembleDebug` 双 flavor 通过；`testStandardDebugUnitTest` / `testCoexistDebugUnitTest` 各 91 suites / 989 tests / 0 failures；模拟器 sdk_gwear_x86_64（Android 14，466×466）装 coexist 包实测：视频 80433022 列表页评论行只剩「点赞/踩/回复」，uiautomator dump 列表页与详情页均无 `item_reply_translate`/`replyTranslation` 节点（此前列表页有「文A」）；目标语言为中文时同一条中文评论请求 gRPC 200、服务端返回空 map、界面不显示译文也不卡死；**外语评论→中文仍无样本可验**（上游扫描 25 个视频、多组关键词均未找到英文评论，这也是暂隐藏入口的原因）；本轮 `ReplyInfoActivity` 打开后 `getReply` 回调抛异常自动关闭，详情页未单独复测（与列表页共用 `bindTranslation`）；真手表未验） |

| 五十七 | 26.10.26：发版 26.10.26（2610260）与官网同步 | 版本号 `26.10.24`→`26.10.26`（versionCode `2610242`→`2610260`）、把 26.10.24 的 20 条日志归档进 `update_history_log`、`update_log_current` 按用户口径精简为 12 条；提交 `055eaad`（版本号 + 日志归档）+ `8388d56`（日志精简）并推送 `main` 与 tag `26.10.26`；CI「构建发行版并创建 Release」#15 全部步骤 success，GitHub Release 10 个资产（8 个 APK + `md5sums.txt` + `release-links.txt`，正文 12 条 + MD5 表 + `versionCode` 标记），Gitee 发行版已同步 26.10.26；站点仓库 `bili-terminal-site` 用 `tools/sync-release.py` 重新生成 `data/releases.json` / `index.html` / `changelog.html` 等静态页，提交 `bdc9e37` 推送 `main` | 部分遗留（**官网静态页仍未更新**：线上 `data/releases.json` 最新 tag 仍是 26.10.14、`sitemap.xml` lastmod 仍是 2026-10-14、`changelog.html` 仍写「当前最新版 26.10.14」，且 `rebiterminal.netlify.app` 已返回 404「site-not-found」（站点被改名或反代目标失效），而 `/api/release` 运行时接口已返回 26.10.26 → 判断 Netlify 自 26.10.14 之后没再部署该仓库（`26.10.19`、`26.10.24` 两次同步提交同样未上线），需要在 Netlify 后台检查站点是否仍绑定 `PurpleEthereality/BiliTerminal-RE_web` 的 `main` 分支，或手动触发一次 Deploy；站点仓库内容与本地生成结果均已就绪，恢复部署即自动生效） |

| 五十八 | 26.10.27：发版 26.10.27（2610270）与官网同步 | 版本号 `26.10.26`→`26.10.27`（versionCode `2610260`→`2610270`）、把 26.10.26 的 12 条日志归档进 `update_history_log` 的 `## 2026-10-26` 组、`update_log_current` 按用户口径改成 5 条（暗色 / 亮色 + 弥散光晕 + 玻璃质感、设置改版、搜索排序图标、字幕下沉、Android 7.1 及以下更新失败）；提交 `4cf93c6` 推送 `main` 与 tag `26.10.27`；CI「构建发行版并创建 Release」`40449814197` 全部 success，GitHub Release 10 个资产（8 个 APK + `md5sums.txt` + `release-links.txt`，正文 5 条 + MD5 表 + `versionCode` 标记），Gitee 发行版同步出 11 个资产（8 个 APK + `md5sums.txt` + 源码归档 zip/tar.gz）；站点仓库 `bili-terminal-site` 用 `tools/sync-release.py` 重新生成 `data/releases.json` / `index.html` / `changelog.html` 等静态页，提交 `ac21dde` 推送 `main`；构建过程中 `:app:minifyCoexistReleaseWithR8` 因本地 Gradle 构建缓存里的坏产物（`DynamicApi$UpInfo.class` 为 532 字节全零）失败，删坏 class + `:app:clean` + `--no-build-cache --no-configuration-cache` 重建成功 | 部分遗留（**官网静态页仍未更新**：`changelog.html` 仍写「当前最新版 26.10.14」，Netlify 依旧未从 `PurpleEthereality/BiliTerminal-RE_web` 的 `main` 重新部署，需在 Netlify 后台检查站点绑定或手动 Deploy；`/api/release` 运行时接口已返回 26.10.27 + 5 条日志，动态部分正常；站点仓库内容与本地生成结果均已就绪，恢复部署即自动生效。另：发版提交落地时本地 HEAD 已被人为切到 `refactor/layout-as-subpage`（非本代理所为），已用 `git branch -f main 4cf93c6` 把 `main` 快进并推送，该分支未删，等待用户决定） |

| 五十九 | 26.10.30：播放器内切不到 1080P（根因 + 修复） | 根因：播放链路全走 `fnval=1` 合一 MP4（清晰度天花板 720P，`qn=80` 被服务端降级成 `quality=64`），而 `accept_quality` 仍列 1080P → 界面能看到、切了没效果。修复：新增 `player/PlayerStreamPolicy.kt`（`QN_720P=64`、`requiresDash`、`isPlayableDash`、`isRemoteAudioTrack`，纯函数 + 6 例单测）；`api/PlayerApi.java` 新增 `getPlaybackVideo`/`getPlaybackBangumi`/`getBangumiDash`，`applyDashStreams`/`fillQualityLists` 抽出公共解析；`activity/player/PlayerActivity.kt` 新增 `initRemoteAudioTrack`（`MediaPlayer` + `setDataSource(Context, Uri, headers{Referer/UA/Cookie})` + `prepareAsync` 播 DASH 音轨）、`onHighQualityAudioFailed`（音轨失败一次性回退 720P）、`applyAudioSpeed`（`PlaybackParams` 同步倍速，API 23+）、`switchQuality` 拆出唯一落地路径 `applyQuality`；切P与互动跳转也改走 DASH 入口并带上新音轨。文档同步 `architecture-map.md` §7.57、`bilibili-api-notes.md` §3.12、`FEATURES.md`。**模拟器实测又抓出一个真 bug**：切 1080P 后远程音轨报 `INVALID_OPERATION`(-38) 被误判失败、自动踢回 720P——根因是 `progressChange` 对还没 `prepareAsync` 完成的 `MediaPlayer` 调 `seekTo`；修法是新增 `audioTrackPrepared` 守卫，并补 `retryRemoteAudioTrack`（600ms 重试一次）、`audioDriftBaseline`（只纠变大的漂移，不纠固定偏差）、回退时不覆盖清晰度列表（否则 1080P 入口消失） | 部分遗留（`assembleDebug` 双 flavor 通过；`testStandardDebugUnitTest` / `testCoexistDebugUnitTest` 通过，`PlayerStreamPolicyTest` 6 例全绿；**模拟器 emulator-5554 实测**：`play_qn=80` 起播走 `fnval=4048&qn=80`，播放器内切「高清 1080P60」→ `fnval=4048&qn=116` → `在线 DASH 音轨已就绪`（不再 -38），清晰度列表保持 DASH 档可再切回；但模拟器未登录，实际只拿到 DASH 的 720P 视频流，**1080P 画面本体仍需登录/大会员真机复核**；DASH 流地址约 120 分钟过期导致超长视频中途断流未处理；倍速同步在 API < 23 上无 `PlaybackParams`，仍只靠偏差校正） |
| 六十 | 26.10.30：清晰度按账号权限锁定 + 4K 接入 | 按用户要求把“能不能选”与“能不能播”分开：新增 `player/QualityAccessPolicy.kt`（`maxUnlockedQn`=未登录 720P / 非大会员 1080P / 大会员不限、`isLocked`、`needsLocked4kHint`，9 例单测）与 `util/AccountStateStore.kt`（`x/web-interface/nav` 的 `isLogin`/`vip.status` → SharedPreferences 缓存，`clampQuality` 把超权请求夹到权限内）。播放器清晰度面板：`QualitySelectorAdapter` 支持锁定项（复用 `icon_player_lock`、`alpha=0.55`、点击只提示），未登录锁 1080P 及以上、非大会员锁 1080P60/4K，4K 追加“需开通大会员”提示位（服务端不给无权限账号下发 120）。取流改为**一律先 DASH**（否则 `fnval=1` 会按档位裁剪清单，高档整体消失），请求 `qn` 固定 127（`QN_REQUEST_MAX`，只影响清单宽度）。`SettingQualityActivity` 增加 1080P60/4K 并**按权限隐藏**不可选档位。文档同步 `architecture-map.md` §7.57、`bilibili-api-notes.md` §3.12、`FEATURES.md` | 部分遗留（`assembleDebug` + 两 flavor 单测通过，`QualityAccessPolicyTest` 9 例全绿；**emulator-5554 实测**：匿名面板显示 1080P60/1080P/720P/480P/360P + 锁定「4K 超清」，点锁定项弹对应提示且不切换；设置页仅剩 360P/720P；切 480P 生效（`changeVideoSize video>852x480` + `在线 DASH 音轨已就绪`）。**4K 流本身未验证**：匿名请求 `qn=127&fnval=4048&fourk=1` 的响应只有 `[116,80,64,32,16]`，4K 档由大会员权限决定是否下发，需要大会员账号真机复验） |

| 六十一 | 26.10.31：发版 26.10.31（2610310）与官网同步（首次验证「私有仓库 + 公开仓库」发版链路） | 版本号 `26.10.27`→`26.10.31`（versionCode `2610270`→`2610310`）、把 26.10.27 的 5 条日志归档进 `update_history_log` 的 `## 2026-10-27` 组、`update_log_current` 按用户定稿写 10 条（用户草稿里的「扫一扫」「个人名片 / 视频链接分享」两条属私有功能，按 AGENTS.md 不外传，已从公开日志删掉）；提交 `e3c9378`（账密 / 短信登录补存 access_token，§3.87）+ `bea1fa5`（版本号 + 日志归档）+ `7cae4b2`（日志定稿）并推送 `main` 与 tag `26.10.31`；CI「构建发行版并创建 Release」run `41170272361` 全部 success（`verify-version` + `build-and-release` 7m33s）。**本轮首次真正验证私有注入**：发版前给公开仓库补了 Actions Secrets `PRIVATE_FEATURE_REPO` / `PRIVATE_FEATURE_TOKEN`（原本缺失，注入步骤只会走「按公开版构建」分支），先用 `workflow_dispatch -f build_only=true` 跑 run `41168852898` 预演（注入步骤输出「私有功能源码已注入」、单测 + `assembleRelease` 全绿、未建 Release 不占 tag），正式发版 CI 日志同样显示私有源码参与编译；出包 `app-arm64-v8a-release.apk`（7205288 字节，md5 `3b0b9c8f…`）反查：`classes.dex` 含私有类，`aapt2 dump xmltree` 显示四个私有 Activity 均注册且 `exported=false` / `screenOrientation` 竖屏锁定（旧的网页版 Activity 已不在清单里）。GitHub Release 10 个资产（8 个 APK + `md5sums.txt` + `release-links.txt`，正文 10 条 + MD5 表 + `versionCode` 标记），Gitee 发行版同步 11 个资产（8 个 APK + `md5sums.txt` + 源码归档 zip/tar.gz），下载 Gitee 的 arm64 包与 GitHub 同名包 **字节级一致**（7205288 / `3b0b9c8f…`）；relay 返回 `HTTP 202 {"ok":true,"queued":true,"tag":"26.10.31"}`，`/api/release` 已返回 `tag=26.10.31` + 10 条日志 | 部分遗留（**官网静态页仍未更新**，且这次比前两版更糟：站点仓库远端 `https://github.com/PurpleEthereality/BiliTerminal-RE_web.git` 现在返回 `Repository not found`（未认证 API 404），本地生成的同步提交 `a992a72` 推不上去——前三版是「推上去了但 Netlify 没部署」，这次是**仓库本身已不可访问**，需要用户确认站点仓库被删 / 转移 / 转私有，或提供新的部署方式；线上 `changelog.html` 静态快照仍停在 `v26.10.14`，动态接口 `/api/release` 正常，客户端更新检查与下载不受影响） |
| 六十二 | 26.10.31.1：倍速两路音频重叠的终版修复（ijkplayer 内核补丁）与补丁版重发 | 修复「倍速播放听到两路重叠音频、其中一路跟不上」：根因是 stock ijkplayer 的 `pf_playback_rate` 只作用在音频路径，DASH 纯视频流没有音频时钟 → 视频恒 1x、外挂音轨 2x，800ms 基线校正每 ~1.3s 回拉一次（实测 69.4s 内 47 次）。终版给 ijkplayer 打两笔内核补丁（`ijkpatch/0001` 让「无音轨 + 倍速」时按 `pf_playback_rate` 缩放帧间隔；`ijkpatch/0002` 修 NDK arm 的 `<termios.h>` `B0` 与 HEVC `TAB_MVF_PU(B0)` 撞名），用 WSL + NDK r14b 重编三 ABI 的 `libijk{ffmpeg,player,sdl}.so` 随包发布；应用侧撤掉中间版本"倍速换合一 MP4"绕行（`applyAudioSpeed` 恢复跟随倍速、长按三倍速走 `effectiveSpeedValue()`），并给 `progressChange` 的重同步补 `audioResyncSuppressMs` 冷却（seek 未完成时 `currentPosition` 还报旧值，会连 seek 三下）。实测：纯视频 DASH 2x 视频真 2.00x、本地双文件 2x `resync` 仅起播 1 次、同刻只有 1 条活跃 AudioTrack；单测 104 suites / 1112 tests。代价：`libijkffmpeg.so` arm64 5.29→7.22MB（module 带 294 个 filters）。发版 tag `26.10.31.1`（versionCode 2610311） | 部分遗留（代码/发版/Gitee 已闭环；**官网静态页仍未更新**——站点仓库远端 `https://github.com/PurpleEthereality/BiliTerminal-RE_web.git` 仍返回 `Repository not found`（未认证 API 404），本地同步提交 `a992a72`（26.10.31）与 `10a6e0b`（26.10.31.1）都推不上去，与 26.10.27 / 26.10.31 两次同因；线上 `changelog.html` 静态快照仍停在 `v26.10.14`，动态接口 `/api/release` 正常，客户端更新检查与下载不受影响。另：arm 真机未实测、模拟器无法验"在线 DASH 双流 + 外挂音轨"，见 §2.1） |

| 六十三 | 26.10.31 末：DASH 双流音画不同步 / 音频轨不加载 / 听视频 -10000 的根治（引入统一时钟内核） | 参考兄弟仓库 `BiliTerminal-Revival` 的 ExoPlayer 方案：新增 `player/UnifiedDashPlayer.java`（接口 `setPlaybackSpeed` / `setAudioSource` / `setAudioOnly`）与 `player/ExoDashMediaPlayer.java`（`extends AbstractMediaPlayer`，ExoPlayer **2.19.1**，`MergingMediaSource(true,true,video,audio)` 一条时钟同时消费视频+音频；`setAudioOnly(true)` 只建音频源，供听视频模式用），`app/build.gradle` 加 `com.google.android.exoplayer:exoplayer-core:2.19.1`；`PlayerActivity` 的播放器字段 `ijkPlayer:IjkMediaPlayer?` 抽象成 `playerCore:IMediaPlayer?`（112 处改名），`createPlayerOrBail()` 在「在线点播 && 非直播 && 有远程音轨」时返回统一内核、否则回退 `createIjkPlayerOrBail()`；`setIjkOption()` / `applyPlayerSpeed()` / `MPPrepare()` 按实例类型分支（`IMediaPlayer` 没有 `setSpeed` / `setDataSource(String,Map)`）；`initAudioTrack()` 对统一内核直接 return（否则叠加出第二路音频）；`onPrepared` 里起播后调用 `scheduleUnifiedHealthCheck()`（2.5s 取样 + 2s 复检，位置前进 <800ms 判定不兼容 → 记 `player_unified_blocked_version=BuildConfig.VERSION_NAME` + `retryAfterPlayerError()` 回退 IJK，同版本不再重试）。取流侧配套：新增 `player/StreamUrlPolicy.kt`（主机优先级选主地址 + 备份列表，12 例单测）、`DashData.getBestCompatibleAudioStream()`（只选 AAC/mp4a，绝不选 flac/dolby，7 例单测）、`PlayerData.videoBackupUrls/audioBackupUrls`、`PlayerApi` 的 WBI→legacy `x/player/playurl` 回退与 durl `backup_url` 解析、`jumpToPlayer` 下发 `video_backup_urls`/`audio_backup_urls`；`PlayerActivity.rotateVideoBackupUrl()` 在播放器报错重建时换下一个 CDN。文档同步 `architecture-map.md` §7.4、`FEATURES.md` §3.6 | 部分遗留（`assembleDebug` 双 flavor + `:app:testStandardDebugUnitTest` 通过，1131 用例全绿，含新增 `StreamUrlPolicyTest` 12 例、`DashDataCompatibleAudioTest` 7 例；**MuMu 模拟器（127.0.0.1:7555，x86_64 / Android 12）实测**：1080P DASH 双流能起播（`onTracksChanged: video/avc 1920x1080`、`ExoPlayerLib/2.19.1`），听视频模式（只看音频轨）能持续前进、AudioTrack 长期 `started`，证明统一内核与音频路径可用；但该模拟器上**只要视频轨参与，播约 1.1–1.4 秒后必掉回 BUFFERING 且永不恢复**（已排除 seek、合并音频、App pause、changeVideoSize、异步/同步 MediaCodec 适配器、2.18.7/2.19.1 版本差异；AudioTrack 在同一毫秒被置 paused，Exo 不报 error，播放线程空闲在 Looper），同一模拟器上官方 B 站 App 正常，判断是 MuMu 视频解码/Surface 链路问题；健康检查+回退链路已实测触发（日志 `统一时钟播放器在本机不可用（起播后播放位置仅前进 0ms）` → 重建 IJK → `在线 DASH 音轨已就绪`），但该模拟器随后 IJK 视频位置也不再前进（环境已劣化），**端到端「不卡 + 音画同步」仍需真机复验**；另外看到 `play_qn=80` 起播流仍可能被 `fnval=1` 路径回填成 720P（`applyActualQuality`），需真机确认详情页预取链路） |
| 六十四 | 26.11.01：发版 26.11.01（2611010，紧急回退版）与官网同步尝试 | 版本号 `26.10.31`→`26.11.01`（`versionCode 2610311`→`2611010`）、把 26.10.31.1 的两条日志（倍速两路重叠音频、扫码登录停轮询后外露英文异常）归档进 `update_history_log` 的 `## 2026-10-31` 组、`update_log_current` 按用户定稿写 5 条（播放器整体退回 26.10.27 稳定实现 / 菜单退出按钮 / 圆屏列表实验效果 / 媒体控件归类修复 / 列表封面请求复用）；提交 `d7d5abd` 推送 `main` 与 tag `26.11.01`；CI「构建发行版并创建 Release」run `41220070951` 全部 success（`verify-version` + `build-and-release`，含私有源码注入），GitHub Release `26.11.01` 10 个资产（8 个 APK + `md5sums.txt` + `release-links.txt`，正文 5 条 + MD5 表 + `versionCode` 标记），Gitee 发行版同步 11 个资产，`/api/release` 已返回 `tag=26.11.01` + 5 条日志；出包体积（release）：arm64 7212652 B / v7a 6765149 / x86 7316806 / universal 13104357，coexist 四包 7259921 / 6812411 / 7364074 / 13151631（对比 26.10.31.1 的补丁 .so 版已回落）；文档同步 `architecture-map.md` §7.4、`FEATURES.md` §3.6 与 §3.90 | 部分遗留（**官网静态页仍未更新**：线上 `data/releases.json` 最新条目仍是 `26.10.14`、`changelog.html` 仍写旧版本；本轮确认站点源码仓库 `https://github.com/PurpleEthereality/BiliTerminal-RE_web.git` 已 `Repository not found`（GitHub API `/repos/PurpleEthereality/BiliTerminal-RE_web` → 404，该账号仓库列表里也没有站点仓库），本地站点仓库 `bili-terminal-site` 的三个同步提交（`a992a72` 26.10.31、`10a6e0b` 26.10.31.1、`2f75bec` 26.11.01）都推不上去，且顺带修了 `tools/sync-release.py` 的发行日期口径（GitHub `published_at` 是 UTC，凌晨发版会写成前一天，已统一换算东八区）——需要用户决定重建该仓库并把三个提交推上去、或在 Netlify 里改接另一个仓库/账号；动态接口 `/api/release` 正常，客户端更新检查与下载不受影响。另：本次回退撤掉了 ijkplayer 内核补丁与 ExoPlayer 统一时钟，**播放器改动未在真机复验**（用户自行验证），DASH 1080P 等 26.10.30/31 的播放能力随回退一并移除，见 §3.90） |
| 六十五 | 26.11.01：圆屏适配下首页/推荐页的时钟被顶到屏幕最右、被圆盘切掉 | 用户点名（给了期望/实际两张顶栏截图）。`res/layout/activity_simple_main_refresh.xml` 是全工程唯一把 `@id/timeText` 套进一层 `@id/menuArea`(LinearLayout) 的顶栏布局，而 `BaseActivity.setRound()` 的圆屏居中靠 `newTopbarParams(clock).addRule(RelativeLayout.CENTER_HORIZONTAL)`；父容器是 LinearLayout 时拿到的是 `LinearLayout.LayoutParams`，`is RelativeLayout.LayoutParams` 判假 → 居中规则静默跳过，时钟仍被 `alignParentRight` 钉在最右。改法：删掉 `menuArea`、把时钟提成 `@id/top` 的直系子 View、`pageName` 的 `layout_toStartOf` 改指 `@id/timeText`（结构对齐 `activity_simple_refresh.xml`，`setRound()` 零逻辑改动，只改注释）；`docs/agents/pitfalls.md` 与 `docs/architecture-map.md` §8.5 各补一条约束 | 已闭环（`assembleDebug` 双 flavor 通过，两 flavor 单测各 111 suites / 1171 tests / 0 失败；AVD `wear5` 466×466 圆屏实测：修前 `timeText [380,13][443,46]` → 修后 `[201,13][264,46]`（中心 232.5 ≈ 屏中心 233），另外 3 个同布局页面同步修好，`player_ui_round=false` 回归对照版式零变化；真机未验） |
| 六十六 | 26.11.02：「我的反馈」空态点顶栏不退出、改成原地刷新 | `res/layout/activity_simple_refresh.xml` / `activity_simple_main_refresh.xml` 的 `emptyTip` 加 `android:layout_below="@id/top"`。空态浮层此前排在 `top` 之后且 `match_parent`，盖住整条顶栏；`setOnEmptyRetry` 一给它装点击监听，点顶栏就变成重拉列表（`FeedbackListActivity` 的 action 是 `load()`）。同一份模板的首页菜单栏也一起修好 | 已闭环（`assembleDebug` 双 flavor 通过、`testStandardDebugUnitTest` 111 suites / 1171 tests / 0 失败；AVD `wear5` 466×466 实测：修前点顶栏 `(233,65)` 触发 `GET /terminal/feedback/mine` 且停在原页，修后同一点位返回 `SettingMainActivity` 且无请求，点空态自身区域仍能重试；真机未验） |
| 六十七 | 26.11.02：发版 26.11.02（2611020）与官网同步尝试 | 版本号 `26.11.01`→`26.11.02`（`versionCode 2611010`→`2611020`）、把 26.11.01 的 5 条日志归档进 `update_history_log` 的 `## 2026-11-01` 组、`update_log_current` 按用户口径（「直接发」）写 12 条：虚拟合集拆分成「虚拟合集 / 收藏夹点击即播」两个开关、长按倍速档位可配、多P 续播、看图双击缩放与评论区图片单击下载、外置 SD 卡缓存交外部播放器失败回落内置播放器、定期续期登录凭证、短视频比例与保亮屏、播放器一批修正、界面一批修正、菜单设置不再被重置、崩溃兜底、失败提示说人话——覆盖 26.11.01 之后的 27 个提交（§3.96~§3.106）；提交 `81e9ffc` 推送 `main` 与 tag `26.11.02`；本地 `:app:assembleRelease` 先跑通（BUILD SUCCESSFUL 3m35s，standard + coexist 各 4 包）再推 tag；CI「构建发行版并创建 Release」run `41365799799` 全部 success（`verify-version` + `build-and-release`），GitHub Release `26.11.02` 10 个资产（8 APK + `md5sums.txt` + `release-links.txt`，正文 12 条 + MD5 表 + `versionCode` 标记，已逐条回读 1~12 全在），Gitee 发行版同步 11 个资产（8 APK + `md5sums.txt` + 源码归档 zip/tar.gz，**每个渠道 4 个包齐全**），`/api/release` 已返回 `tag=26.11.02` + 12 条日志 + 11 个 file；出包体积（release）：arm64 7229559 B / v7a 6782053 / x86 7333715 / universal 13121277，coexist 四包 7276714 / 6829208 / 7380871 / 13168424；`main` 推送触发的「CI 编译与单测自检」run `41365463839` success | 部分遗留（**官网静态页仍推不上去**：站点仓库远端 `PurpleEthereality/BiliTerminal-RE_web` 仍 `Repository not found`，本地 `bili-terminal-site` 已用 `tools/sync-release.py` 生成同步（`data/releases.json` + `index.html` 版本号/直链/JSON-LD + `sitemap.xml` lastmod + 重新生成 `changelog.html` 等 7 文件）并提交 `81b8ee7`，但推送失败、仓库现 ahead 6；`sync-release.py` 里的 `gen-pages.py` 子进程报退出码 1（输出编码问题），单独重跑 `python tools/gen-pages.py` 成功、页面已含 26.11.02；线上静态 `changelog.html` 仍停在 `v26.10.14`，动态接口 `/api/release` 已更新、客户端更新检查与下载不受影响——仍需用户决定重建站点仓库或给 Netlify 换绑定。另：CI 的「归档 R8 mapping 到控制台」被 `::warning::` 跳过（**未配置 Secrets.HK_SSH_KEY**），本版崩溃在控制台无法解混淆，补 Secret 后需单独补传 26.11.02 的 mapping；`readme.md` 的 version badge 长期停在 `26.10.26`，本次一并更到 `26.11.02`） |

| 六十八 | 26.11.03：按后台控制台反馈落地 5 条建议（App #94 拖动手感 + 缓存页排序搜索、#92 热搜跑马灯、#91 下载不休眠；QQ #9 精确空降、#7 锁定按钮开关） | ① **#94a**：新增 `activity/player/PlayerSeekPolicies.kt` 里的 `PlayerSwipeSeekScalePolicy`（六档 0.25/0.5/1.0/1.5/2.0/3.0、`DEFAULT_SCALE=1.0f`、纯函数 `targetMs(startMs,deltaX,widthPx,durationMs,scale)` 钳到 `[0,duration]`），`PlayerActivity.kt:3842` 每次横滑激活时重读存档、`:3856` 换用新换算；设置页「左右滑动控制进度」下方新增 list_choose「滑动调进度灵敏度」。② **QQ#9**：`PlayerJumpTimePolicy.parseToSeconds`（纯秒 / `mm:ss` / `hh:mm:ss`、全角冒号、分秒 <60、≤3 段、`MAX_PART=999999`）+ `clampMs`；`res/layout/activity_player.xml:382-397` 新增 `jump_time_btn`（默认 `gone`，`onPrepared` 非直播才显示），`PlayerActivity.kt:1438/1454` 走 `startActivityForResult` + `onActivityResult`（`PlayerActivity` 是原生 `Activity`，没有 `registerForActivityResult`），非法输入只提示、越界先提示再钳到结尾。③ **QQ#7**：新 key `SettingsKeys.PLAYER_UI_SHOW_LOCK_BTN="player_ui_showLockBtn"`（默认 true）；`PlayerLockPolicy.phaseWithLockButtonDisabled()` 在状态机层把「按钮不可用」与「锁定」钉成互斥（否则锁定态 overlay 吞触摸 + 唯一解锁入口被藏 = 死锁），`refreshLockButtonSetting()` 关闭瞬间若已锁定则强制回 `UNLOCKED`。④ **#94b**：新增 `util/LocalListSort.kt`（`enum Key{DEFAULT,TITLE,TIME,SIZE}` + 七档 `OPTION_KEYS`/`OPTION_ASCENDING`，`videoOrder`/`folderOrder` 返回**原始下标**，方向翻转手写比较器以避开 minSdk19 上 `Comparator.reversed()` 要 desugaring）；`model/LocalVideo.java` 加 `lastModified`；`LocalListActivity.kt` 加 `btnSort`/`btnSearch`/`btnSearchClear` + 七档排序菜单 + 关键词搜索，排序过滤只发生在 `CacheListAdapter` 显示层（多选/删除/移动/虚拟合集仍按原始条目下标）；`activity_simple_main_refresh.xml` 是共享模板，三个按钮默认 `gone` 不污染其他页。⑤ **#92**：`item_hot_search.xml` 的 `keywordText` 换成 `ui/widget/MarqueeTextView`（删 `ellipsize=end`），适配器不重复调 `setMarquee()`（RecyclerView 复用同一实例、`init` 已置 `isSelected`）。⑥ **#91**：新增 `util/DownloadWakeLock.kt`（`PARTIAL_WAKE_LOCK` tag `BiliTerminal:download` + `WIFI_MODE_FULL_HIGH_PERF`、`setReferenceCounted(false)`、`@SuppressLint("WakelockTimeout")` 故意不设超时）与 `util/DownloadWakeLockCounter.kt`（`DownloadService` 与 `DownloadActivity` 共用同一把系统锁的计数）；`DownloadService.kt:451`/`:472-475` 与 `DownloadActivity.kt:112`/`:160-162` 是 acquire/release 配对（覆盖图片保存、前台视频/番剧、自更新 `.bak`），`DownloadActivity.kt:77`/`:400` 补 `FLAG_KEEP_SCREEN_ON`；`AndroidManifest.xml:31-37` 新增 `WAKE_LOCK` 权限 + 中文理由（WifiLock 只要 `WAKE_LOCK`，没恢复 `ACCESS_WIFI_STATE`） | 部分遗留（`./gradlew.bat :app:assembleDebug :app:testStandardDebugUnitTest -x lint` BUILD SUCCESSFUL；`app/build/test-results/testStandardDebugUnitTest` **134 suites / 1445 tests / 0 失败**，新增 `PlayerSwipeSeekScalePolicyTest` 13、`PlayerJumpTimePolicyTest` 13、`PlayerLockPolicyTest` +2（15）、`LocalListSortTest` 14、`DownloadWakeLockCounterTest` 4。AVD `wear5`（466×466 圆屏，x86_64 镜像没有 x86 32 位支持，装的是 arm64-v8a 包走 ARM 翻译）实测：**热搜首行关键词确实在横向滚动**——连续三帧同区域像素差 2992/2729/3357，肉眼可见「志遗体在京火化 → 体在京火化 → 京火化…刘延」推进；**播放器设置页两条新项都在且能落盘**——「滑动调进度灵敏度」选 0.5x 后 prefs `player_swipe_seek_scale=0.5`、「显示锁定按钮」关掉后 prefs `player_ui_showLockBtn=false`。**播放页内三项部分实测**（同一台 wear5，用 `am start .../activity.player.PlayerActivity --es url http://10.0.2.2:8765/Big_Buck_Bunny_360_10s_1MB.mp4 --es title BBB` 直连起播，主机用 `python -m http.server` 供片）：**锁定按钮两分支都对**——`player_ui_showLockBtn=false` 时 dump 出的 `right_control` 只有 `danmaku_btn`/`rotate_btn`/`menu_btn`，改回 `true` 后出现 `lock_btn [8,147][64,203]`；**跳到指定时间入口通**——菜单面板点开后有 `jump_time_btn`（圆屏 176px 宽不截断），点它弹出「跳到指定时间」的 `InputDialogActivity`（hint「秒数 / mm:ss / hh:mm:ss」），暂停在 `00:02/00:05` 时输入 `00:04` 确定后进度变 `00:04/00:05`（后续重复尝试中有几次没跳，怀疑暂停态下进度文本不刷新或点击落空，尚未稳定复现）；**横滑按倍率生效**——`player_swipe_seek_scale=0.5` 时 `input swipe 120 359 300 359 400` 把 `00:00/00:10` 移到 `00:01/00:10`（180/466×10s×0.5≈1.9s，量级一致；未做 0.5x/1.0x A/B，改存档需同进程写才生效）。测试期间该模拟器被并行会话占用（对方在测番剧页，会反复重装同一包并抢前台），部分操作因此中断。**遗留**：圆屏上输入法弹出会盖住「取消/确定」，必须先收键盘再点（`InputDialogActivity` 的既有问题，不是本次引入）。**#91 唤醒锁未实测**（需要登录态跑真实下载任务），只有计数逻辑单测；真机未验）

| 六十九 | 26.11.04：部分视频播放时弹「播放器可能遇到错误！错误码：-10000」（playurl 的 mcdn/PCDN 直链缺 Referer 被 CDN 403） | 现象：真机 coexist debug 播 `BV1c3pk6LEaa`（aid=117436804242218 / cid=42726132853）停在「载入视频中」`00:00/00:00`，日志 `E PlayerActivity>MPPrepare$lambda$28 \| ijk-err>播放器可能遇到错误！\n错误码：-10000\n附加：0`，紧接 `I MsgUtil>showMsgLong` 弹同文案。根因：`playurl` 高峰把直链调度到 mcdn/PCDN 边缘节点（`https://r5uw39t.edge.mountaintoys.cn:4483/upgcxcode/53/28/42726132853/42726132853-1-16.mp4?…&os=mcdn&og=cos&upsig=…`），该 host 不在 `NetWorkUtil.isBilibiliHost` 白名单；当时工作树里的「第三方片源」改动把 `Referer` 与 `Cookie` **一起**按白名单门控，这种直链**一个头都不带** → 403。修法：新增 `player/PlayerRequestPolicy.kt`（`BILIBILI_REFERER = "https://www.bilibili.com/"`；`isThirdPartySource(isLive,aid,cid,bilibiliHost) = !isLive && aid <= 0 && cid <= 0 && !bilibiliHost`；`buildHeaders(thirdParty,bilibiliHost,cookies,thirdPartyReferer,thirdPartyUserAgent)` —— 非第三方一律发 Referer，仅 `bilibiliHost` 且 Cookie 非空白才发 Cookie，第三方只发它自己的 `third_party_referer` / `third_party_user_agent`、拿不到任何 B 站 Referer/Cookie）；`PlayerActivity.kt:1621-1636` 的 `MPPrepare` 改调该策略，`isBilibiliHost`（`:5464-5475`）现在只决定「能不能带 Cookie」；第三方直链（无 aid/cid）跳过字幕接口，避免 JSONException 弹「数据解析错误」 | 已验证：`./gradlew.bat :app:assembleDebug` BUILD SUCCESSFUL；两 flavor 单测各 **135 suites / 1431 tests / 0 失败**（新增 `PlayerRequestPolicyTest` 8 例）。真机 `10AC9S2M4L000QL`（vivo V2229A / Android 16，coexist debug）实测：① 同一 mcdn 节点（`os=mcdn`、`r5uw39t.edge.mountaintoys.cn:4483`）现在 `onPrepared` 后正常播放，进度 00:00→00:30/08:50，`sendProgressReport` 连续上报 15s / 30s，全程无 `ijk-err`；② 对照实验（DebugBridge `http` 命令跑在应用进程内、可自定义 headers）：同一 URL 只带 `Range: bytes=0-1023` → **403**（openresty 页，150 B），补 `Referer: https://www.bilibili.com/` → **206**（1024 B，`ftypisom…avc1`）；③ 第三方直链回归（`https://www.w3schools.com/html/mov_bbb.mp4`，intent 无 aid/cid）仍正常起播到 `00:09/00:10` |

| 七十 | 26.11.05：#94b 返工——缓存页排序从「七档预设菜单」改成手动拖动排序（用户指出需求本来就是缓存条目自己排序，交互照菜单设置页的长按拖动） | 上一轮 §六十八 ④ 把「选择性排序」理解成七档预设排序菜单，方向错了，本次整段重做：① `util/LocalListSort.kt` 重写——删掉 `enum Key`/`OPTION_KEYS`/`OPTION_ASCENDING`/`videoOrder`/`folderOrder` 等预设排序逻辑，只留 `normalizeKeyword`/`matches`，新增两个纯函数 `orderByStoredTitles(scanIndices, storedTitles, titleAt)`（持久化标题序优先、扫描到的漏记标题按原序补尾、重复标题只算一次）与 `mergeOrder(newOrder, previous)`（新顺序在前、旧顺序里没出现的按原序补后、过滤空标题去重）。② 新增 `util/CacheListOrderStore.kt`：未分类视频顺序存 SharedPreferences，键 `cache_list_order::<缓存根路径>`（按根目录分键，换路径/降级互不干扰），值为 JSON 数组，坏数据回落空表。③ `util/FolderManager.kt` 新增 `saveFolderOrder(orderedNames)`（按名字 rank 稳定重排 `.folders_info.json` 数组顺序，未列出的排最后）与 `saveFolderVideoOrder(folderName, orderedTitles)`（写 `VideoFolder.videoTitles`，用 `mergeOrder` 保留旧标题）。④ `util/SettingsKeys.kt` 删 `CACHE_LIST_SORT_KEY`/`CACHE_LIST_SORT_ASCENDING`。⑤ `CacheListAdapter` 删 `sortKey`/`sortAscending`/`applyViewOptions`，新增 `sortMode`、`dragCallback`（`ItemTouchHelper`，`isLongPressDragEnabled=false`，只给文件夹条目/视频条目发 `UP or DOWN`，跨分区 `onMove` 返回 false）、`setUnclassifiedOrder`、`setSearchKeyword`、`onOrderDragFinished`；`onMove` 只就地挪 `flatItems` + `notifyItemMoved`，落盘推迟到 `clearView` 里 `recyclerView.post`（与 `MenuSettingAdapter` 同因：不能在 RecyclerView 计算布局/滚动时写文件）；条目绑定改成点击/长按时才读 `selectionMode`/`sortMode`（进/退排序模式不 `notifyDataSetChanged`，避免封面 `DiskCacheStrategy.NONE` 重读闪动），顶层「未分类视频」标题清掉监听器（该 viewType 夹内复用）。⑥ `LocalListActivity`：`btnSort` 变排序模式开关（文案切「完成」，左侧 `textSelectedCount` 显示「长按条目拖动排序」），进入前先退多选、清搜索词并提示「已显示全部缓存」（排序模式不允许搜索，否则被过滤掉的条目无法表达相对位置）；`onBackPressed` 先退排序模式，进出文件夹也先退；`playVirtualCollection` 改按 `orderedVideosInFolder`（`videoTitles` 顺序）起播，虚拟合集连播顺序与列表一致；`persistOrderChange` 在主线程就地落盘（与 `FolderManager.createFolder` 同一先例）。⑦ `strings.xml` 删八条 `text_cache_sort_*`，新增 `btn_cache_sort_done`/`text_cache_sort_hint`/`msg_cache_sort_show_all`；`docs/FEATURES.md` §10.1 改写成拖动排序说明 | 已闭环（`./gradlew.bat :app:assembleDebug` BUILD SUCCESSFUL；两 flavor 单测各 135 suites / 1431 tests / 0 失败，`LocalListSortTest` 重写 15 例、新增 `CacheListOrderStoreTest` 6 例。**MuMu 模拟器实测**（x86 debug，`127.0.0.1:7555`，Android 12，1440×2560，造了 2 个文件夹 + 5 个缓存目录的夹具）：① 点「排序」→ 底栏只剩「长按条目拖动排序」+「完成」，条目点击失效；② 长按拖未分类视频换位 → 顺序即时变化，松手后 SharedPreferences 键 `cache_list_order::/storage/emulated/0/Android/media/com.RobinNotBad.BiliClient` 写入新标题序；③ 拖文件夹换位 → `.folders_info.json` 数组顺序随之改变；④ 进甲文件夹内拖动两个视频 → 该夹 `videoTitles` 变 `["甲二","甲一"]`；⑤ `am force-stop` 后经 DebugBridge 重开缓存页，三层顺序（文件夹序 / 未分类序 / 夹内序）全部保持；⑥ 非排序模式长按视频仍弹「更新弹幕 / 切换分辨率 / 移出文件夹 / 查看详情 / 删除视频」原菜单，返回键在排序模式里只退模式、不离开页面。**真机未验**（手表端长按手感需用户上手确认） |

| 七十一 | 26.11.06：换源播放接入弹弹play 弹幕；「无感播放」下线、选源交互对齐 Kazumi | ① 私有侧新增 `DandanApi.kt`（`X-Auth` / `X-AppId` / `X-Timestamp` / `X-Signature`，签名 = `base64(sha256(appId + 秒级 ts + uri.path + key))`，**只签 path 不签 query**；bgmtv 映射 / 番剧详情 / 搜索 / 评论，5 分钟 TTL）、`DandanDanmaku.kt`（`fromComments` → `shield` → `mergeDuplicates`（5 秒同文合并成「文本 xN」顶部白字）→ `shift`（时间轴偏移，正数更晚）→ `toBiliXml`/`writeXml`）、`KazumiDanmaku.kt`（编排：bgm 条目精确映射优先、标题/别名搜索兜底；产物落 `cacheDir/danmaku/dandan_{episodeId}_{chConvert}_{offset}_{merge}.xml`）、`DanmakuPrefs.kt`、`DanmakuSettingsActivity.kt`（启用 / 简繁 / 偏移 ±30s / 合并 / 屏蔽规则 / 恢复默认 6 行）；`DandanDanmakuTest` 19 例。② 公共侧挂点：`PlayerActivity` 新增 `third_party_danmaku` 等 extras（置位时隐藏「发送弹幕」、跳过 B 站弹幕接口）、`applyThirdPartyDanmaku(file)`、`OptionalFeatures.attachThirdPartyDanmaku` 门面 + `FeatureHolder` 同签名转发；`app/build.gradle` 与 `build-release.yml` 增加 `DANDANAPI_APPID`/`KEY` 构建期注入（缺凭证 = 无第三方弹幕，公共侧照常）。③ `DebugBridge` 的 `extras` 支持 `int:` / `long:` / `bool:` / `float:` / `string:` 前缀。④「无感播放」下线：`BgmInfoFragment` 删掉加载时自动 `ThirdPartyResolver.resolve` 与自动填选集（选集区显式 GONE，只留常亮「播放」→ `ThirdPartySourceActivity`），`KazumiPlayActivity` 删 title-only 跨源竞速（`playFromCandidates` / `startParallelResolve` / `extractFirst`），只保留单页解析 | 已验证（`clean` + `assembleStandardDebug` 通过；`testStandardDebugUnitTest` **134 suites / 1501 tests / 0 失败**。AVD `rebili_api37_sd`（Android 17）实测四条：① DebugBridge 直起播放页带 typed extras → 日志 `bgm 条目 95225 → bangumiId=9977`、`就绪 5162 条（episodeId=99770001）`、`第三方弹幕已载入`，截图里弹幕按时间轴滚动；② 改 prefs 后重起 → 文件名变 `dandan_99770001_1_2.5_0.xml`、条数 5448，证明简繁 / 偏移 / 合并都进链路；③ BGM 95225 条目页只剩「播放」，点击开选源面板（13 源 + 末行「弹幕设置 / 弹弹play 弹幕已开启」），设置页 6 行可改（简繁转换实测生效）；④ 真实流程 DM84 → 命运之夜 无限剑制 → 第1集 → 嗅探 m3u8 → 播放页载入 5162 条弹幕。**遗留**：弹弹play 的剧集时间轴与第三方片源时间轴天然可能错位，只能靠手动偏移补；简繁转换依赖服务端 `chConvert`；凭证借用 Kazumi 的（建议自申请）；手表真机未验） |
| 七十二 | 26.11.06：「从 26.11.02 升级到当前版本掉登录」溯源 + 凭证链返工（用户点名） | **先说结论：升级本身不清登录态**。① 26.11.02 / 26.11.06 / 26.11.06.1 三个正式包签名证书 SHA-256 完全一致（`991386eb…`）、包名一致、versionCode 单调递增 → 覆盖安装合法（排除换签名、误发共存版两类打包事故）；② `26.11.02..HEAD` 的启动与刷新链路逐字节相同；③ 全库只有 `SplashActivity.resetLogin()` 与「退出登录」会清空登录态，升级首启都不走；④ 账号代际在首启不会误判。**真正的机制（两版同源）**：`NetWorkUtil.saveCookiesLocked()` 三处缺陷 —— 收到 `SESSDATA=` 就把本地 SESSDATA 写成空串（`hasLocalSession()` 判空 → 下次冷启动清整套凭证）、属性截断只认 `"; "`、SharedPreferences 未就绪时从空串合并把 SESSDATA/DedeUserID 写丢；`CookieRefreshApi.refreshCookie()` 又与官方流程相反（新 `refresh_token` 等 confirm 成功才落盘；先 confirm 作废旧 Cookie 再校验新 Cookie）。**改法**：新增纯策略 `util/CookieJarPolicy.kt`（凭据键不接受删除/清空、属性按 `;` 切、空值不落盘、无尾随分隔符）+ `saveCookiesLocked`/`putCookie` 就绪检查 + `CookieRefreshPolicy.checkRefreshedCredentials()` + `refreshCookie` 返回 `RefreshResult{OK,RETRY,NEED_LOGIN}` 并把顺序排正为「立刻落盘新口令 → 校验新凭据 → confirm → `applyCredentials` 原子落库」+ 启动页/周期调度按三档处理并记录清空原因 + `AccountSwitchActivity` 尊重 `switchToAccount()` 返回值 | 已验证：`clean` + `assembleDebug` 双 flavor BUILD SUCCESSFUL；两 flavor 单测各 **149 suites / 1600 tests / 0 失败**（新增 `CookieJarPolicyTest` 19 例，`CookieRefreshPolicyTest` 扩到 18 例）；AVD `rebili_api37_sd` 注入假登录态冷启动：服务端 -101 后本地凭证一条没少、无「清空登录态」日志、追加的 `b_nut` 以干净格式并入、启动正常进推荐页。遗留：真实登录态下「刷新成功换发新 SESSDATA」无账号可复现，靠文档语义 + 纯函数单测兜住 |
| 七十三 | 26.11.06：兄弟分支移植批次 1（13 项 S 级 + 2 项 release 日志补漏） | **① 播放器域**：playurl 双接口兜底（WBI→legacy、pgc v2→v1；失败请求不再占 10 分钟缓存）；音频焦点（起播/恢复 request，LOSS/LOSS_TRANSIENT 暂停且不自动续播，暂停/播完/报错/onDestroy abandon，后台播放保留焦点）；弹幕数量保护（`stat.danmaku` 先查、>5000 不自动加载、弹幕按钮强制加载、旧 XML 流式计数）；「强制为滚动弹幕」死开关接线（`ForceScrollParser`，含实时插入弹幕）；直播弹幕停留倍率（1.0–3.0，默认 2.0）；短视频无极调音（软件增益复用 `KEY_GAIN_PERCENT`）。新增 7 个策略类：`PlayUrlFallbackPolicy` / `AudioFocusPolicy` / `DanmakuLoadPolicy` / `DanmakuStylePolicy` / `LiveDanmakuFactorPolicy` / `DanmakuXmlCounter` / `ForceScrollParser`。**② 社交域**：私信表情（新增 `BUSINESS_IM` + 空面板回落 reply + 光标处插入表情码，渲染侧原本就绪）；性别/IP 属地（`x/space/wbi/acc/info` 同一次请求解析，`UserInfo` Parcelable 末尾同序追加 `sex`/`ipLocation`/`mutualFollow` + `serialVersionUID`，用户主页头部展示、保密/空值整行隐藏）；互关徽章（`attribute == 6`，仅本人关注/粉丝/分组列表显示）；历史记录多选删除（单批 100、全选/计数、失败如实汇总）。**③ 存储/设置/工具**：存储空间页（图片缓存/临时/更新包/可用空间统计 + 白名单清理；与 cacheDir 重叠的受保护根会让整棵 cacheDir 跳过，绝不删下载目录与正在播放的弹幕文件）；`UpdateManager` 检查更新 client 加 `callTimeout(20s)`（下载不受影响）；移动数据/Wi-Fi 独立默认画质（`NetworkPlaybackQuality.defaultQn(context)`，统一 `PlayerActivity`/`JumpToPlayerActivity`/`VideoInfo`/`ShortVideoFeedApi` 共 5 处读取点）；`FileUtil.readString` 改 try-with-resources；`SharedPreferencesUtil` 五个 getter 加 `ClassCastException` 保险丝；`SettingsIndex` 补 3 条搜索项。**④ 日志补漏**：动态卡片评论入口 + 评论数（`DynamicCommentText` 万/亿缩写；`DynamicApi` 解析 `module_stat.comment.count`，顺带修「动态详情页评论数恒为 0」）；视频详情争议信息判空（`parseArgueMsg` + 同文件 `ugc_season.stat`/`pages[].part`/联合投稿 `staff` 等安全 opt 化） | **已验证**：`./gradlew.bat :app:assembleDebug` BUILD SUCCESSFUL（双 flavor）；两 flavor 单测各 **164 suites / 1719 tests / 0 失败**（本批新增约 90 例：player 48、storage 25、social 4 类、dynamic 2 类）。**模拟器 emulator-5554（Android 17 x86_64，standard debug 包）实测**：① 存储空间页正常渲染（占用情况/可用空间/三个清理按钮/「不会删已缓存内容」说明）；② 清晰度页 Wi-Fi 子页选 720P 后回写成功（prefs `play_qn_wifi=64`，行文案变「当前：720P」）；③ 内置播放器设置新增三项都在（弹幕数量保护默认开、强制为滚动弹幕可开关并落盘 `player_danmaku_forceR2L=true`、直播弹幕停留倍率 2.0x）；④ `PlayerActivity` 用本地视频起播，日志出现 `AudioManager ... requestAudioFocus()` 弃用警告（证明焦点请求真的发出），无崩溃。**未验证**（模拟器本轮被并行会话反复抢占 + 部分页面需登录态/数据）：来电/抢音的焦点丢失路径、>5000 弹幕视频的抑制与强制加载、直播倍率观感、短视频音量与长视频存档互通、番剧 v2 回退日志、动态评论入口/性别属地/互关徽章/私信表情的界面。**遗留**：评论入口走非 ForResult 的 `TerminalContext.enterDynamicDetailPage`，从评论入口进详情后删/改返回列表不自动刷新（下拉刷新可解；后续给 ForResult 版加 `seekReply` 重载）；历史多选删除「长按松手是否顺带触发一次点击」待真机确认（`HistoryVideoCardAdapter` 不在本批 scope）；多选面板用了 `TerminalDialog.Sheet`（internal，同模块可用），建议下批收进 TerminalDialog 作为第五种形态；`SharedPreferences` 保险丝会把类型写错静默降级为默认值，必要时加日志；音频焦点用 API26 已废弃接口（minSdk 19 下刻意保留，编译有 deprecation 警告） |
| 七十四 | 26.11.06：兄弟分支移植批次 2（10 项 M 级） | **① 播放核心**：MediaSession 隔离（minSdk 19 类加载崩溃隐患：`PlayerActivity.kt` 字段与辅助函数改 `Any?`，四个入口先 SDK 守卫再局部 `as? MediaSession`，`onDestroy` 整段进 `SDK>=21`）；连播队列（新 `player/PlayQueue.kt`：5 来源 SINGLE/UPLOADER/COLLECTION/FAVORITE/LOCAL_CACHE × 4 模式 顺序/倒序/随机/单曲，确定性洗牌 + 历史栈 64、行式 token 编解码；`activity_player.xml` 加连播按钮；媒体键、耳机切歌、自动连播、换 P 全部走队列；token 落盘重启可恢复，与虚拟合集共存）；播放会话哨兵（新 `player/PlaySessionStore.kt`，15s 节流 + 生命周期落盘、`PLAYSESSION1` 编解码；`PlayerApi.buildResumeIntent` + 主菜单「继续播放」，5 秒/7 天/直播三重过滤）；番剧每集进度（新 `player/BangumiProgressStore.kt`，epid 维度、上限 128、看完标完成；`PlayerApi.getBangumi` 用本地进度覆盖服务端）；`DeviceProfile` 三处接线（默认清晰度上限 / 弹幕行数上限 / 低端机软解）。**② 弹幕内核**：`DanmakuStreamLoader` 360 秒分段窗口（前后各 2 段、首屏 3 段并发、单串行 worker、15s 失败退避、最多 4 轮）+ `DanmakuDiskCache`（`cacheDir/danmaku_cache`、LRU、默认 64MB / 上限 128MB、tmp+fsync+rename 原子写）+ `DanmakuManager.appendSegments` 待灌队列 + 设置页「弹幕缓存上限」档位；窗口加载失败回退整段接口。**③ 评论域**：`ReplyFetchPolicy` 三层兜底（rpid 跨页去重 → `LocalReplyCache` 补回刚发的评论 → 换评论类型重拉）+ `ReplyStateStore`（512 条 LRU，点赞/点踩/删除跨页同步，不写盘）；`ReplyFragment` / `ReplyInfoActivity` / `ReplyAdapter` 接线。**④ 收藏与历史**：夹内搜索（`FavoriteApi.getFolderVideosNew` 带服务端 keyword，可与 order 同用）、历史搜索（客户端过滤 + 「仅本页已加载 N 条」提示）、「全部收藏」聚合（`FavoriteAggregatePolicy.merge` 本地合并各夹首页）、批量下载（`FavoriteBatchDownloadPolicy`，单批 20 条）。**⑤ 大图**：`ImageTilePolicy` + `ui/widget/BigImageView.kt`（256×256 分块、只解码可见块、预取半屏、6MB 块缓存、连续 4 块失败回退整图降采样）+ `ImageViewerActivity` 重写（每页 PhotoView / BigImageView 二选一） | 已验证：`assembleDebug` 成功（两 flavor 共 8 个 APK）；两 flavor 单测各 **174 suites / 1867 tests / 0 失败**（批次 1 为 1719，本批 +148；首轮 2 例失败经核实为测试断言写错，改断言后全绿）。AVD `rebili_api37_sd`（Android 17，匿名态真视频 aid=117566290790345 / cid=43364712790）实测：① 弹幕窗口 `起始窗口已加载：segments=1..3 非空=3/3 条数=1698`，`cache/danmaku_cache/dm_43364712790_{1,2,3}.bin` 生成；② 队列 token 落盘 `PLAYQUEUE1 collection sequential …`（7 项多 P）；③ 会话哨兵 `PLAYSESSION1 … 13662 3141099 collection 0 1 … 第一局`，onStop 与销毁兜底各落一次；④ 主菜单「继续播放」出现并点通：`buildResumeIntent progress=226678` → 跳转页取流后补回 → `onPrepared 进度跳转>226678` → 会话写回 240532ms，确认真的续播。**修掉两个真 bug**：`buildResumeIntent` 把毫秒除以 1000（`PlayerData.progress` 全仓单位是毫秒，除完变 13ms ≈ 从头播）；`PlayerApi.getVideo/getBangumi` 会用服务端 `last_play_time` 覆盖 `playerData.progress`（匿名态归零），跳转页现按 `resume_progress_ms` extra 在取流后补回。**未验证**（需登录态）：评论三层兜底与跨页同步、收藏夹搜索 / 全部收藏 / 批量下载、历史搜索、大图分块真实长图、番剧每集进度。**遗留**：从「继续播放」进入时 `restoreQueueTokenIfMatches` 因分 P 列表尚未加载（7 vs 0）丢弃 token，「下一个」按钮暂无队列（安全阀按设计生效，可后续在分 P 就绪后二次恢复） |
| 七十五 | 26.11.06：兄弟分支移植批次 3（S 清单 1–10 + 主题背景图 + Wi-Fi/移动数据分辨率复核） | **① 崩溃与接口健壮性**：`CatchActivity.kt` 的 `setSpan` 结束下标改成 `minOf(8, length)`（崩溃原因短于 8 字原来直接越界崩溃）；`RankingApi.java` 整段改 `opt*` 安全取值并跳过空卡；写评论页楼中楼隐藏图片入口（B 站楼中楼不支持带图，原来选完图才失败）。**② 播放器**：片头片尾五档（关闭/仅片头/仅片尾/都跳/询问我，老布尔自动迁移，询问档只问一次且绝不自动 seek，`SkipOpEdPolicy`/`SkipOpEdPrefs`）；点带时间的弹幕跳转（`DanmakuTimestampPolicy`，拒绝 1080p/1:2/99:99 这类假时间）；长按上下划切视频（`LongPressSwitchPolicy`，0.18 屏高阈值 + 横向容差，命中先结束倍速）；长按倍速提示尺寸四档（80/100/120/150%）；本人弹幕边框（vendored `DanmakuFlameMaster` 的 `BaseDanmaku.borderColor` 去 final、解析器补 `userHash`，按登录 mid 的 CRC32 比对，`DanmakuSelfStylePolicy`，未登录不加框）。**③ 数据域**：推荐页翻页游标（`RecommendPagePolicy` + `RecommendApi` 的 ps/fresh_idx，实测 fresh_idx 为真游标、brush 无效；bvid 去重 + 代数判据防旧请求覆盖）；定时发布自定义时间 + 6 分钟~7 天校验（`ScheduleTimePolicy`）。**④ 外观**：主题背景图（`BackgroundImage`，私有目录拷贝、16MB/8192px 上限、≤1024 采样、填充/适应、0/25/50/75% 遮罩）+ 与弥散光晕叠加（`AmbientGlow.createOverlayDrawable` + `ColorScheme` 的 LayerDrawable 半透明层）；**values-night 不加**：我方外观是手动两态（`ColorMode.applyGlobal` 强制覆盖 uiMode，`values-notnight` 已覆盖亮色），系统夜间位无表达力。**⑤ 清晰度分档复核**：起播四入口（PlayerActivity/JumpToPlayerActivity/VideoInfo/短视频）均已走 `NetworkPlaybackQuality.defaultQn`，补齐选择播放器页摘要；下载链与直播不走分档（直播 qn 写死在 `TerminalContext`）。**⑥ 验证**：`assembleDebug` 成功；两 flavor 各 182 suites / 1969 tests / 0 失败；模拟器实测片头片尾五档落盘 `player_skip_op_ed_mode=4`、本人弹幕边框开关、外观页背景图选择/清除/适配/遮罩、背景图重启后仍在（1091926B）且渲染生效、光晕叠加 A/B 像素证据、推荐页下滑出新内容。**未验证（需登录或人工操作）**：点弹幕跳转、长按划动切视频、询问档弹窗、大图分块渲染、评论/收藏/历史域。 |

| 七十六 | 26.11.06：「跳到指定时间」输入框打开时按当前位置预填（用户点名） | 新增纯函数 `PlayerJumpTimePolicy.prefillText(positionMs)`（`<=0` → null；`mm:ss`，超一小时 `h:mm:ss`，固定 `Locale.US` 保证能被 `parseToSeconds` 解回来）；`initJumpToTimeButton()` 传 `initial_text`，位置取 `video_now`（seek 未落地时 `currentPosition` 还是旧值）；`InputDialogActivity` 不改，光标照旧落在末尾（初版做过整串选中，当天按用户意见撤回）。文档同步 `FEATURES.md` 精确空降条目与 `update_log_current` 第 7 条 | 已闭环（单测 `PlayerJumpTimePolicyTest` 15 例含 round-trip；双 flavor `assembleDebug` 通过，两 flavor 单测各 186 suites / 2011 tests / 0 失败；AVD `rebili_api37_sd` 实测：`00:03` 时打开弹窗预填 `00:03`、确定后进度 `00:49/02:34`，播完（`02:34/02:34`）再打开仍是空框；撤回整串选中后按用户要求未重跑，真手表未验） |

## 5. 仍待回迁 / 仅见于归档的长期结论

> 下列结论目前**只存在于归档正文**，`docs/architecture-map.md` §7.29~§7.36 与 AGENTS.md 都还没有；要么回迁到 `architecture-map.md` §7，要么保持「归档可查」。已被 §7 / AGENTS / §6.5 覆盖的结论不再重复。

- **默认值必须收敛到唯一常量**：片头片尾跳过被用户误判为「压根没实现」，实际接线完整，唯一原因是**四处调用点各写一遍字面量 `false`**。漏掉一处就会出现「设置页显示开着、播放器却不跳」这种**半生效状态**（范式：`player/SkipOpEdPrefs.kt` 的 `DEFAULT_ENABLED`、`util/LongPressPrefs.kt`）。§三十三 / §37.1
- **改默认值天然满足「新装默认开 + 老用户尊重显式选择」，不需要任何迁移 / 重置代码**：`SharedPreferences.getBoolean(key, def)` 的语义就是「键不存在时才用 def」。既有同款范式：`util/SharedPreferencesUtil.java:132-144` 的 `loadMenuEnabled()` 用 `sharedPreferences.contains(key)` 区分。§三十三 / §37.1
- **改默认值 / 改键名是两种效果**：改键名等于把老用户设置读成「没设过」，默认值会把他关掉的开关重新打开 → 单测必须有「键名必须稳定」的护栏。§37.1
- **撤回一个已发布的特性，不能只改默认值**（见 §3.3 的 `FEATURE_ENABLED` 否决位）。§四十三
- **废弃接口的经典陷阱：`code=0` + `data=null` 造成静默空列表**：话题广场原走的 `app.bilibili.com/x/topic/web/dynamic/rcmd` **恒返回 `{"code":0,…, "data":null}`**（带 / 不带参数、游客 Cookie、伪装浏览器 headers、`page_size` 9/25/26 全部一样）。因为 `code` 是 0，`if (code != 0) throw` 永不触发 → 静默 `data == null → 返回空列表` → `showEmptyView()`。**按「网络异常 / 抛异常」方向排查全是死路**。替代源：`api.bilibili.com/x/topic/pub/search`（`keywords` 留空即按热度返回整页话题，稳定 20 条，匿名可用）。§三十四
- **单向排序缺陷的形态**：`model/Reply.java` 的 `sortReplies` 首行 `if (replies == null || sort != SORT_LIKE) return;` → `SORT_TIME`（= 0）直接 return、从不重排 → 「时间→热度→时间」往返后顺序**永久停在热度序**。这是**推翻一个旧的设计取舍**（归档正文 `fix-progress.md:1758`「时间序 = 不做任何本地重排」），不是修疏忽。修法用 `ctime`（服务端**恒返回**）显式升序，两个比较器 `LIKE_ORDER`/`TIME_ORDER` 都是 `private static final` 且各自 null 兜底，**两个分支都幂等**。**`pubTime` 是展示文案、不单调，不能做排序键。**§30.1
- **「排序前备份原列表、切回时还原」被否决**：`Reply` 实现 `Serializable` 且跨页传递，模型里藏 mutable 备份字段经翻页 / 点赞 / 删除后极易变脏。§30.1
- **`Reply` 只实现 `Serializable`、无任何 `Parcelable`**（`model/Reply.java:31`），评论相关 `putExtra` 只传标量 → 新增 `ctime` 无需同步 Parcelable（属**理由修正、结论不变**）。§30.1
- **同一手势承载两种语义 = 用户嘴里的「冲突」**：`util/StringUtil.java:170` 的 `setCopy(TextView, String)` 是业务代码自己注册的长按监听（`SharedPreferencesUtil.getBoolean("copy_enable", true)` + `startActivity(CopyTextActivity)`），不是 `LinkMovementMethod`/`textIsSelectable`。**同一个 TextView 只能有一个长按结果** → 修复方向是「按需让位」，不是去改复制本体。§30.2 / §37.1
- **`performLongClick()` 转发 click 会弹两次菜单**：长按手势结束后系统还会补发一次 click。动态侧原代码 `item_dynamic_delete!!.setOnClickListener { item_dynamic_delete!!.performLongClick() }` 就是真实缺陷。修法：**存普通 lambda 而不是 `View.OnLongClickListener`**，收敛到唯一真相源 `DynamicHolder.showManage()` + `MANAGE_DEDUP_MS = 400L` 去重；`showManage()` 在 `manageAction == null` 时返回 `false` → 不消费长按 → 自动回落到 `setCopy`。§30.2 / §37.1
- **`setOnTouchListener` + `ACTION_DOWN` 返回 `false` 也会有补发 click**（`VideoCardHolder.bindClick()`）：多选态下「长按先选上、抬手立刻取消 = 什么也选不中」，非多选态下「长按弹菜单、抬手又跳详情页」。修法：`suppressClickAfterLongPress` 标记，长按回调真执行就立起、随后那次点击自己吞掉，`bindClick()` 里随新绑定清零。§39.2
- **RecyclerView 的 item animator 会吃掉你的 alpha**：`DefaultItemAnimator` 对 `notifyItemChanged` 是另建 ViewHolder 交叉淡入（`supportsChangeAnimations = true` 时 `canReuseUpdatedViewHolder()` 返回 `false`），动画收尾把新布局 alpha 设成 `1f` → 「勾选（目标 1f）看不出问题、取消勾选（目标 0.45f）被静默改回 1f」。修法：`VideoCardAdapter.onAttachedToRecyclerView()` 里 `(recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false`。§39.1
- **`ScrollRetractDecider` 一条反直觉返回值必须保留**：已收着时**继续向下滚仍返回 `COLLAPSE`**，唯一作用是让调用方把累加值清零；返回 `NONE` 会让累加值一直涨，表现为「条收起来以后怎么滚都不回来」。§37.2 / §39.1（工具条的兄弟节点结构与「展开只允许在列表到顶」的口径已在 §7.28 / AGENTS）
- **「自然高度」必须缓存**：条本是 `wrap_content`，收回被压成 0，而 0 高度量不出「原本多高」→ 否则「收得回去、展不回来」；且「收回」只动当前真正可见的条。§37.2
- **`danger` 不要写死下标**：`setOf(2)` 在菜单插入新项后会漂移，改为 `setOf(actions.indexOfFirst { it.first == "取消收藏" })`。§39.1
- **`local` 判据的朝向决定用户看到什么**：稍后再看旧 `isUnfinished` 首行 `if (progress <= 0) return false;` 把「从未播放」当成「不是未看完」→ 整表都从未播放时「未看完」档**就是空的**（与用户现象逐字吻合）；且本工程**从未给 `history/toview/web` 带过 `viewed` 参数**，服务端能力从未被使用。正确：**主判据 = 服务端 `viewed`（0 全部 / 2 未看完）**，本地 `filterUnfinished` 退化为兜底（`duration <= 0` → 一律算「未看完」；只有 `progress >= duration && duration > 0` 才算「已看完」）。§三十五（§7.23 只覆盖了 `isUnfinished` 的判据本身）
- **`content` 长按点击的 span 陷阱**：`StringUtil.LinkClickableSpan` 的 `case TYPE_WEB_URL:` 取的是 `text`（span 显示文本）而**不是**第三个构造参数 `val`（`StringUtil.java:205` 能工作是因为那里 `text` 就是 URL）；且 `updateDrawState` **硬编码 `ds.setColor(Color.rgb(0x66,0xcc,0xff))`**，执行时会**覆盖 `ForegroundColorSpan`** → 话题必须新写 `StringUtil.TopicClickableSpan`（颜色取 `ColorScheme.INSTANCE.getPRIMARY()`，**不硬编码色值**），解析不出 `topic_id` 时退化为「只上色、不可点」。§三十四
- **R8 合并类的 mapping 不是一对一**：`E0.j` 表头写 `DynamicInfoActivity$$ExternalSyntheticLambda1`，但它同时有 `(CollectionInfoActivity, long)` 构造来源；`C0.c` 底下合并了 `VideoRcmdFragment`/`VideoInfoFragment`/`VideoInfoActivity`/`BangumiInfoFragment` 一串同类 SAM 包装。**单看表头类名定归属会错**，必须结合 `residualsignature` 注释与实际方法签名。§38.1
- **崩的是 release 包就必须单独验 release 链路**：`assembleRelease`（含 `minifyReleaseWithR8`）不能只跑 debug。§38.4
- **`app/build/outputs/mapping/release/mapping.txt` 不进版本库**，换机或 `clean` 后即失 → 线上混淆栈要趁 mapping 还在先 retrace。ProGuard retrace 能读 R8 写进 mapping 的 `# {"id":"sourceFile"}` 与 `residualsignature` 注释，**不需要额外装 R8 retrace**。§38.1
- **`Result.onSuccess` 自己已判空**（`if (isSuccess()) { T value = getOrNull(); if (value != null) accept(value); }`）→ **NPE 只可能在消费者 lambda 体内**，不在 `Result` 里，这是比读 mapping 表头更快的定位路径。§38.1
- **并发构建必须串行**：`app/build/` 共享，Gradle 对 `tmp/kotlin-classes`、`intermediates/javac` 先删再写，多人同跑必互踩并产生**假失败**。判据三选：① 复跑同一命令 ② `git status --short` 前后对比确认工作树静止 ③ 报错文件 `git diff` 与 HEAD 有实际差异；不全只能报「疑似在途中间态」。**类型不匹配 = 真错误；引用不存在的类 = 多为中间态。** §36.7
- **用例数一律以机器产出为准**：读 `app/build/test-results/testDebugUnitTest/*.xml` 的 `<testcase>` 计数；队友口述「23 例」实际 25 例（场景数口径与 `@Test` 数口径混用）。口述数字不进入正式文档。§36.7
- **正式文档只写正确结论**；唯一例外是「以错误理由做出的正确决定」，写成「**理由修正、结论不变**」并保留原错误理由，否则后人会重新推演出同一个错误理由。§36.7
- **JVM 单测里的 Android 桩陷阱（`§7` 只覆盖了「判据抽纯函数」这一半）**：`android.util.Pair` 这类**直接 `new` 出来的框架类**在单测下字段恒 null，只断言列表长度完全测不出。判据不是「类型名里有没有 `android`」，而是「**这个值是真实实现算出来的，还是桩给的默认值**」（自实现 Android 接口的 fake 如 `FakeSharedPreferences`、以及 `app/build.gradle` 单独补了真实实现的 `org.json` 都不踩坑）；**「测试文件里没有 `import android.*`」≠「该测试不依赖 Android 桩」**。修法：新建 `model/TopicNode.java` 取代 `Pair` + 回归守卫 `parseTopicNodes_returnsRealObjectsNotAndroidStubs`。§36.3 / §36.7
- **异常堆栈里没有被测方法的帧 → 失败在构造参数转型那一层**：`NoteApiTest.kt:198` 原写 `as String`，Kotlin 在构造器调用当成就抛 NPE，异常根本没进 `failureText`；改 `as String?` 后 null 才传进去（`NoteApi.failureText` 的 null 兜底 `NoteApi.java:237` 失败时就已存在、一行未改）。⚠️ 不要读成「产品代码曾有 null 返回缺陷」。§36.3
- **断言「某属性全工程缺失」必须同时 grep 相邻属性做对照组**：最初结论「弹窗层从未接入主题系统」被「7 族主题都声明了 `colorSurface`」与「XML 侧 71 处 `?attr/` 引用」直接推翻。§三十二（`§8.4` 只覆盖了主题层根因）
- **`Sheet.init` 必须清 window 背景**（`setBackgroundDrawable(TRANSPARENT)`），否则圆角被方角底衬盖掉。§三十二
- **`colorError` 的对比度未重测**：`docs/archive/visual-experience-report.md` §2.25（`#EBE0E2` 叠 `#FF6699` 约 2.1:1）仍待处理。§三十二
- **Python 服务的瓶颈不是 Python 而是 fsync**：单 uvicorn worker 处理「小 JSON + 一次小写入」天花板在几百 req/s；加固两件事：阻塞 sqlite 调用必须 `run_in_threadpool`（否则撞锁时 `busy_timeout=15000` 卡死整个事件循环、连 `/health` 都不响应）、`PRAGMA synchronous=NORMAL`（WAL 下默认 `FULL` = 每次 commit 都 fsync）。§42.2
- **带 `Secure` 的 Cookie 在 http 下不会被回发**：selftest 一开始用 `TestClient(api.app)` 默认 `http://testserver` → 「登录成功但后续全 403」；改成 `TestClient(api.app, base_url="https://console.test")` 才对。这个假失败恰好证明 `Secure` 确实生效。§42.3
- **nginx 签证书的坑**：证书还没签出来时 vhost 不能引用证书文件，得先用只 `listen 80` 的引导配置；引导配置若写了 `location / { return 404; }` 而**没给 `/.well-known/` 单独开口**，acme 的 http-01 会拿到 404（`Invalid response from …/.well-known/acme-challenge/…: 404`）。补 `location ^~ /.well-known/ { root /www/wwwroot/<域名>; allow all; }` 后签发成功。§42.6
- **WAL 模式下备份必须用 `sqlite3 .backup`，不能 `cp`**（最近写入还在 `-wal` 里，直接拷会丢数据）。§42.7
- **PowerShell 传 JSON 的坑**：`curl.exe --data-raw '{"title":"中文…"}'` 含中文时会被 Windows 控制台代码页搞坏 → 服务端 `json.loads` 失败 → `read_json` 返回 `{}` → 报「标题和正文都不能为空」。正确姿势：**把请求体写成无 BOM 的 UTF-8 文件，再用 `--data-binary @file`**。§42.8
- **`update_log_current` 是当版日志的单一真相**（关于页读它、`:app:verifyVersionConsistency` 拿它当锚点），不引入第二份需要维护的文案。§40.3
- **播放请求头必须「Referer 与 Cookie 分开判」**：Referer 是 CDN 防盗链要求（缺了 403 → IJK `-10000`），Cookie 是登录凭证。任何「按 host 白名单一起门控请求头」的写法都会打在 B 站自己的 PCDN 节点上（`*.edge.mountaintoys.cn:4483`，`os=mcdn`，host 不在 `isBilibiliHost` 白名单，但 URL 自带 `upsig`、不需要 Cookie）；正确判据是**内容来源**——B 站入口一律发 Referer，第三方只发它自己声明的 Referer/UA，Cookie 只给 `isBilibiliHost`。§六十九
