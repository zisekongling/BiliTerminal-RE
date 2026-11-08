# 教程系统重做：设计与 POC

> 状态：**已完成（26.11.07 收尾）**——`tutorial/Tutorials.kt` 注册表共 **16 篇**（11 篇全屏教程 + 5 篇页面滑动提示 `HINT`），全部走新链路（集中触发 + 内部分页 + 串行）；旧链路（`res/xml/tutorial_*.xml` ×12 / `TutorialHelper` / `TutorialActivity` / `model/Tutorial.java` / `model/CustomText.java` / `strings.xml` 里的 `tutorial_*` 数组 / Manifest 旧教程页注册）**已全部删除**，见 §五。
> 注：§四 / §六 里的「44 / 46 个单测」是当时的验证快照；26.10.04 实测 `app/src/test` 共 20 个文件（19 个测试类 + 1 个共享假实现）、147 个用例。本文件只保留设计与历史证据，当前待办与验证状态以 `docs/review/fix-progress.md` 为准。
> 关联：`docs/architecture-map.md`、`docs/review/fix-progress.md`

---

## 一、要解决的问题（实测证据）

| 问题 | 证据 |
| --- | --- |
| 新功能漏加教程 | 根因是**流程缺失**，没人提醒；`tutorial_article.xml` 就是活标本——内容早写好了，全工程没有任何地方展示它 |
| 同页多篇连续弹 | `TutorialHelper.showTutorialList()` 为数组每一项各 `startActivity` 一次，多个 `TutorialActivity` 叠在栈上，点完一个又弹一个 |
| tag 错位（真 bug） | `tutorial_list` 下标是 `[recommend, video, space, search, message, dynamic, dynamic_info, article, short_video]`，但 `SearchActivity` 传 `4`（写进 `tutorial_ver_message`）、`MessageActivity` 传 `5`（写进 `tutorial_ver_dynamic`）、`DynamicActivity` 传 `6`（写进 `tutorial_ver_dynamic_info`）。**看完搜索教程会把消息教程标记成已读** |
| 旧键冲突 | 动态页教程与动态详情页教程共用 `tutorial_ver_dynamic_info`，无法区分 |
| 加教程要改 5 处 | xml + strings 数组 + `tutorial_list` + 管理页三个硬编码数组 + 页面调用点，且靠下标对齐 |
| 死字段 | `Tutorial.type`、`Tutorial.description` 只写不读 |

---

## 二、设计决策

| 维度 | 决定 | 理由 |
| --- | --- | --- |
| 注册表 | Kotlin 对象，每项 `id / title / version / target / kind / content` | 单一数据源，声明顺序即串行顺序 |
| 触发 | `BaseActivity.onStart()` 按当前页面类名集中匹配 | 页面零改动，杜绝「忘了调用」 |
| 版本 | 每篇**显式自带** version | 新增内容只对没看过的人弹；不再依赖数组下标 |
| 类型 | `enum TutorialKind { GUIDE, NOTICE, HINT }` | GUIDE 强制读完，NOTICE 可跳过，HINT 是页内提示 |
| 内容 | 轻量 Kotlin DSL，`page { }` 分块 | 保留颜色/粗体/斜体/下划线/删除线/换行/图片，且纯数据可单测 |
| 展示 | ViewPager2 内部分页 + 页码指示器 | 左右滑翻页；手表端支持旋冠 |
| 强制 | GUIDE：翻到最后一页 **且** 停留满 3 秒才可点「已阅」，返回键无效；NOTICE：随时可点，**返回即视为已读** | 避免「主动关掉下次又弹」 |
| 串行 | 同页多篇一次一篇，点「已阅」后**原地切换**下一篇（不叠栈） | 消除连续弹 |
| 存储 | `tutorial_ver_<id>`（int） | 沿用旧键名格式，便于一次性迁移 |
| 技术 | 纯 View、不引新库、沿用 14sp / 112×192 / 3 秒 | 与项目约定一致 |

---

## 三、新架构

```text
tutorial/TutorialModel.kt        TutorialKind / TutorialStyle / TutorialSpan / TutorialPage / Tutorial
tutorial/TutorialDsl.kt          tutorial { page { text().bold().color() / newline() / image() } }
tutorial/Tutorials.kt            注册表：16 篇 = 11 篇全屏（recommend / theme_notice / video_main / space / search / message / dynamic / dynamic_info / article / short_video / local）+ 5 篇页面滑动提示（hint_search / hint_video_info / hint_user_info / hint_opus_info / hint_dynamic_info），另有 all / hints / forPage() / byId()
tutorial/TutorialStore.kt        已读状态（全屏教程 tutorial_ver_<id>，HINT 沿用旧键 tutorial_pager_<宿主类名>）
tutorial/TutorialRenderer.kt     spans → SpannableStringBuilder（未指定或解析失败的颜色不挂色，跟随主题文字色）
tutorial/TutorialPagerActivity.kt  分页展示页
tutorial/TutorialHints.kt        页面滑动提示（HINT）的渲染：显示 R.id.text_tutorial_pager、页数取宿主 ViewPager/ViewPager2 真实页数
res/layout/activity_tutorial_pager.xml / item_tutorial_page.xml
```

**触发链路**：`BaseActivity.onStart()` → `Tutorials.forPage(javaClass)` → 过滤未读 → 按类型分流：
全屏教程进 `TutorialPagerActivity.start()`，`HINT` 进 `TutorialHints.show()`（同页两者共存时提示避让到教程关闭之后）。
每个 Activity 实例只检查一次；教程页自身覆写 `tutorialAutoTriggerEnabled()` 返回 `false` 避免递归。

**加一篇教程 = 注册表加一行**（并在 `all` 里登记）。

---

## 四、POC 范围与验收

**范围（POC 当时）**：只把视频详情页一篇（`video_main`，GUIDE，3 页：欢迎/滑动 → 新功能 → 提醒/零碎问题）切到完整新链路，其余教程暂走旧链路。**该 POC 已推进为全量迁移**——现注册表 11 篇全部走新链路，见 §五。
> 原「视频详情-零碎问题」（`video_minor`）已按需求合并为同一篇的最后一页，注册表里只留 `video_main`。

**已通过**：`assembleDebug` 编译成功；44 个单测全绿（新增 `TutorialDslTest` 6 例，覆盖 DSL 结构、kind 语义、注册表自洽、同页顺序）。

**待真机手表验收**：

1. 左右滑动能翻页，页码显示 `1/3` 正确；
2. **旋冠能翻页**（依赖设置里的「旋冠滚动」开关 `ui_rotatory_enable` / `ui_rotatory_scroll`，且需 Android 8.0+）；
3. GUIDE：翻到最后一页且停留 3 秒后「已阅」才可点，返回键无效；
4. NOTICE：随时可点「已阅」，返回键等同已读；
5. 未翻完时底部按钮显示「向左滑动以完成教程」（明确方向），翻完且停留满 3 秒后才变可点的「已阅」。
6. 同页多篇串行机制已实现但暂无用例（视频详情页已合并为一篇），留待后续内容迁移时验证。

**已知风险**：ViewPager2 内部是 RecyclerView，可能自行消费旋冠滚动事件。若真机手感不对，需要改成自定义容器拦截 `ACTION_SCROLL`（`RotaryScrollView` 是同款写法，可参考）。

---

## 五、迁移进度

**已完成**

| 项 | 说明 |
| --- | --- |
| ✅ 全量内容迁移 | 12 个 XML → 11 篇 DSL（`Tutorials.kt`），内容里的 XML 格式化空白已清理；`tutorial_video_2` / `tutorial_space_2` 分别并入各自主教程 |
| ✅ 单页教程处理 | `pageCount <= 1` 时隐藏页码指示器（`1/1` 无意义且占顶栏空间） |
| ✅ 旧键迁移 | `TutorialStore.migrateLegacyKeys()`，`BiliTerminal.onCreate` 启动时执行，幂等 |
| ✅ 孤儿补接 | `tutorial_article` → `article`，target = `OpusInfoActivity`（旧实现从未被展示） |
| ✅ 删除旧调用点 | Recommend / UserInfo / Search / Message / Dynamic / DynamicInfo / ShortVideo / VideoInfo 八处手写调用已移除，改由 `BaseActivity` 集中触发 |
| ✅ 管理页改造（26.10.24） | 「重看」调 `TutorialPagerActivity.start`；通过/清除/一键通过/清除全部改走 `TutorialStore`，页面里不再拼 `tutorial_ver_*` 键 |
| ✅ HINT 纳入注册表（26.11.07） | 5 条页面滑动提示改成 `TutorialKind.HINT` 条目（`Tutorials.hints`），`BaseActivity` 与全屏教程一起分流、`tutorial/TutorialHints.kt` 渲染；宿主页不再手写 `showPagerTutorial(activity, 页数)`，页数改读宿主 ViewPager / ViewPager2 的真实页数——所以**视频详情页默认 3 页**（简介 / 评论 / 相关推荐），关掉「视频相关推荐」或看的是番剧详情才是 2 页（私有包给番剧详情多一页「角色」，那种包是 3 页），搜索页 = 当时启用的搜索类别数（默认 5），用户主页 4、专栏详情 2、动态详情 2。`SearchActivity` 自己抄的那份显隐逻辑一并删除 |
| ✅ 记账时机统一（26.11.07，`034a037`） | 五条 HINT 一律「提示**一露出**就写已读」（`TutorialHints.tryShow` 里 `TutorialStore.markRead`），与其它四页的旧行为一致；**旧搜索页是「左右滑一次之后」才记**，这次跟着统一。用户可见的变化是：搜索页提示刚出现就退出、一次都没滑，下次也不再出现（`docs/review/manual-test-guide-261107-tutorial-hint.md` H01 已写明） |
| ✅ 删除旧链路（26.11.07） | `helper/TutorialHelper.kt`、`activity/TutorialActivity.kt`、`model/Tutorial.java`、`model/CustomText.java`、`res/layout/activity_tutorial.xml`、`res/xml/tutorial_*.xml` ×12、`strings.xml` 里 10 个 `tutorial_*` 数组与 2 条只被 `TutorialHelper` 使用的文案、Manifest 里的旧教程页注册，全部删除 |

**待办**

- ⬜ **强制声明清单** `TutorialCoverage` + 单测：每个 `BaseActivity` 子类表态「有教程 / 无教程」，新增页面不改清单就测试失败。
- ⬜ **真机验收 HINT 迁移**：5 个宿主页的翻页提示文字与页数、翻页后收起、教程页盖住时提示延后出现——只能在真机上看（L2），见 `docs/review/fix-progress.md` §2。

---

## 六、验证记录

| 时间 | 任务 | 结果 |
| --- | --- | --- |
| 26.09.08 | `:app:testDebugUnitTest` | ✅ 44 用例全绿（含 `TutorialDslTest` 6 例） |
| 26.09.08 | `:app:assembleDebug` | ✅ 见 `fix-progress.md` |
| 26.10.27 | 亮色主题下教程正文仍为白字 | ✅ 修：`TutorialSpan.color` 默认 `null`（原写死 `#ffffff`），渲染时不挂色、跟随主题；`theme_notice` 升 v2，补亮/暗色、弥散光晕、玻璃质感说明，并新增 `Tutorial.action` 底部按钮直达「外观设置」。`assembleDebug` + `testStandardDebugUnitTest` 通过；模拟器中途断开，**亮色下实机截图未完成** |
| 26.11.07 | 教程旧链路与硬编码 HINT 收尾 | ✅ 5 条 HINT 进注册表、旧链路删除（明细见 §五）；两 flavor `assembleDebug` + 单测通过。验证口径与真机未验项记在 `docs/review/fix-progress.md` |
