# 26.11.16 缓存「文件夹内部页没有多选按钮」· 现场取证单（D01~D06，约 10 分钟）

- 安装包：`app/build/outputs/apk/standard/debug/app-standard-universal-debug.apk`（普通版）/ `app/build/outputs/apk/coexist/debug/app-coexist-universal-debug.apk`（共存版）
- 版本：`versionName 26.11.16`（`appVersionDate = "26.11.16"`）/ 多选这块代码自 `2871a079 feat(cache): 缓存页（含文件夹）新增多选批量操作` 引入后一行未改，写单时 main HEAD 是 `51440614`
- **性质：这是取证单，不是修复验收单。** 目前一行代码都还没改——我这边按现成代码走查 + 手表模拟器实测都复现不出你说的现象，所以先要你现场的三条信息把「到底是哪一层、那一刻屏幕上是什么」钉死，再决定改哪里。
- 前置：缓存里至少 1 个文件夹、夹内有 ≥1 条视频；不需要登录。
- 回结果：结论对上就写 `- [x]`；对不上留空，并在旁边写一句「看到的是什么」，能带截图最好。

## 我这边已经验过的（不用你重做）

| 场景 | 实测结果 |
| --- | --- |
| 缓存页顶层（文件夹卡片平铺的那一页） | 底部「多选 / 排序 / 搜索」三格都在 |
| 点文件夹卡片 → 夹内页（`LocalFolderActivity`） | 底部**同样**是「多选 / 排序 / 搜索」 |
| 点「多选」 | 正常进多选态，底栏变「已选 0 项 / 全选 / 操作 / 退出」 |
| 环境 | AVD `rebili_watch37`（Android 17，physical 384×384、override 466×466，dpi 320），装的是本文那个 standard debug 包；夹具 = `.folders_info.json` 里 1 个夹 `TestFolder`（VideoA / VideoB 两条）+ 未分类 `Uncat` 一条 |

代码侧的口径（决定了这张单该往哪儿查）：夹内页与顶层用的是同一套底栏，整条隐藏的**唯一**条件在 `app/src/main/java/com/RobinNotBad/BiliClient/activity/video/local/LocalListActivity.kt:1084` 的 `updateSelectionBar()` 里——`!canSelect && searchKeyword.isEmpty()` 时整条 `GONE`；而 `canSelect = hasSelectableItems()`（`LocalListActivity.kt:1149-1156`）在夹内页的判据是「`videoList` 里有任意一条视频的 `folderName` 等于当前夹名」。换句话说：**夹内页只要能看见哪怕一条视频，多选按钮就必须在**。所以你要么看到的是另一层页面，要么那一页当时其实是空的。

## D01 钉页面：三个候选位置各点一遍，报「哪个位置没有多选」

- **怎么做**：①缓存页顶层，看底部；②点一个文件夹卡片进去，看夹内页底部；③先到「设置 → 高级与实验 → 功能开关」把「缓存页文件夹入口」打开，回缓存页点顶部那行「文件夹 N」进文件夹列表页，再点其中一个文件夹，看夹内页底部。
- **应该看到**：把三处底部**各有几个按钮、分别叫什么**念一遍（例如「多选 / 排序 / 搜索」= 三格；「多选 / 排序」= 两格；空的 = 零格）。
- **不算 bug**：第二层那个「文件夹」列表页底栏只有「多选 / 排序」两格，没有「搜索」——那是有意的，不用报。
- **AI 判不了**：截图里按钮的**有无**我能读到（`adb shell uiautomator dump` 里 `multiSelectBar` 的 `visibility` 与三个 `TextView` 的 `bounds`），但「你说的是哪一页」只有你知道。
- **预计**：2 分钟

## D02 那一页里到底有没有视频条目

- **怎么做**：在「没有多选」的那一页，从上往下看完列表。
- **应该看到**：有视频条目（报条数）/ 还是只有一行「啥都木有~」。
- **不算 bug**：目录里只有 `.DOWNLOADING`、还没成品的下载中任务不算缓存，不列出来；封面缺失会显示占位图，不影响条目在不在。
- **AI 断言**：`adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` 之后看列表区有没有条目节点。这条是关键分叉：**空夹 → 走 D03；有视频但没有多选 → 这是真矛盾，我按你 dump 出的 bounds 直接定位。**
- **预计**：30 秒

## D03 取两份 JSON（最可能的真凶在这里）

- **怎么做**：把缓存根目录下的 `.folders_info.json`，和「没有多选」那页里某一条视频目录下的 `.video_meta.json`，内容贴回来。缓存根默认是 `/sdcard/Android/media/com.RobinNotBad.BiliClient/`（设置里改过路径的话按你改的那个）。
- **命令**（手机上也能用文件管理器打开看）：
  - `adb shell cat "/sdcard/Android/media/com.RobinNotBad.BiliClient/.folders_info.json"`
  - `adb shell cat "/sdcard/Android/media/com.RobinNotBad.BiliClient/<视频标题>/.video_meta.json"`
- **应该看到**：那条视频的 `"folderName"` 与夹名**完全一致**。
- **对不上就是根因**：夹内页是靠 `CacheListAdapter.kt:263` 用 `videoMeta.folderName == 当前夹名` 过滤的，夹名却另存在 `.folders_info.json` 的 `videoTitles` 里。两边一旦不一致（`folderName` 为空 / 是老版本留下的 / 手工改过 json / 目录被改过名），就会出现「文件夹卡片上写着 N 个，点进去一条都没有」，底栏随之整条收掉。这种情况我会把夹内页的可见判据与文件夹列表页的统计口径统一，并对「记录里有、磁盘上没有」的视频单独给提示。
- **AI 断言**：两份 JSON 到手就能直接判，不用你看画面。
- **预计**：2 分钟

## D04 「文件夹 N」那一行点得动吗（真机上判，可能是另一个 bug）

- **怎么做**：打开「缓存页文件夹入口」开关后回缓存页，点最上面那行「文件夹 N」。
- **应该看到**：进到「文件夹」列表页（标题「文件夹」，一夹一卡、带封面与「N 个视频」）。
- **不算 bug**：排序模式下点它不响应（避免拖到一半跳页）；搜索状态下它不跟着变数（入口显示的是全部夹数）。
- **为什么问**：手表模拟器上这一行**点了完全没反应**——`logcat -c` 后点，`logcat -d | grep "START u0"` 连一个 START 都没有、也没有异常；而同屏更靠下的「下载列表」行一点就进得去。可疑点是该 AVD 上 App 自己的调试悬浮窗（UETool 那个 UE 圆球，`SYSTEM_ALERT_WINDOW`，frame `[1,10][466,118]`）正好盖住屏幕最上面约 118px，同一区域点顶栏标题也没反应，怀疑是它吞了触摸。**真机上若也点不动，那就是真 bug**，我按 `CacheListAdapter.kt:504-519` 那条点击链去修。
- **AI 断言**：`adb logcat -c` → 点该行 → `adb logcat -d | grep "START u0"`，进不去就没有 START；`adb shell dumpsys window | grep mCurrentFocus` 应停在 `LocalFolderListActivity`。
- **预计**：1 分钟

## D05 夹内条目数 vs 卡片上的「N 个视频」

- **怎么做**：随便挑 2~3 个文件夹，对比「文件夹」列表页卡片上的「N 个视频」与点进去实际看到的条目数。
- **应该看到**：两处数字一致。
- **不算 bug**：正在下载的条目不算，可能让夹内看起来比卡片少 1~2 条（真差得多就记下来）。
- **AI 半自动**：能读 `dump` 里的条目数，但「你本意上有没有删过文件」只有你知道，所以只记数字。
- **预计**：2 分钟

## D06 对不上时交什么

按这个顺序给，基本一次就能定位：

1. 「没有多选」那一页的**截图**（要能看到底部整条）；
2. `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` 拿到的 xml（有它我能读到 `multiSelectBar` 在不在、几个按钮的 bounds）；
3. D03 那两份 JSON 的原文；
4. 复现率（每次都有 / 偶尔 / 只在某个夹）。

## 看到结果后我这边准备的两条改法

1. **D02 = 空夹 / D03 = `folderName` 对不上**：把夹内页的可见判据从「只认 `video_meta.folderName`」改成与文件夹列表页同一口径（夹名命中 `.folders_info.json` 的 `videoTitles` 也算），并对「记录里有、磁盘上没有」的视频在夹内页给一句提示，不再让整页变空白连按钮一起消失。
2. **D04 = 真机上也点不动**：查 `FolderEntryHolder` 那条点击链（`CacheListAdapter.kt:504-519` 把监听挂在 `itemView` 上、而「下载列表」行挂在 holder 自己的 `show()` 里），把两者写法统一后复测。

如果 D01 真复现出「有视频但没有多选」，那就是 `hasSelectableItems()` 与 `rebuildFlatList()` 的判据真的分叉了，我按你的 dump 直接定位，不用再猜。
