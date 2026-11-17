---
name: rebili-feedback-triage
description: >-
  在 RE:哔哩终端（ReBiliClient）里处理控制台用户反馈与崩溃报告：用 rebili-console MCP 拉取未处理条目、
  按异常与机型聚类、读日志并定位到源码、判别「真 bug / 线上旧包已修 / 不是 bug / 信息不足」、
  把已确认的批量标成已处理、对真 bug 落地修复并写 docs/review/fix-progress.md 台账。
  当用户说「看看最近的反馈」「处理崩溃报告」「控制台那批还没标的」「这条反馈是不是 bug」
  「把已修的标掉」「最近用户报了什么」时使用本技能。
---

# 反馈与崩溃处理：rebili-console → 代码 → 标记

把「从控制台拉反馈、定位到代码、标记、修、再标记」固化成一条可重复的流水线。
先在仓库根读 `AGENTS.md`（语言、构建、验证分级、台账约定的权威来源），本技能只讲这条流水线特有的东西。

## 四条硬约束（先背下来）

1. **只看 bug**：`category` 是建议 / 内容 / 功能诉求的先搁一边。用户明确要求「只看 bug」时，功能建议一律不修、不讨论，只在汇报里报个数量。
2. **控制台数据已脱敏，也不许还原**：反馈与崩溃正文里可能带用户日志、手机号、邮箱、IP；`***` 就是终点，不要试图拼回原值，也不要把用户日志原文抄进公开仓库、issue 或回复。
3. **私有功能（`private/`）的结论只写私有侧**：崩溃栈落到 `com.RobinNotBad.BiliClient.feature.*` 就说明是私有功能，**口头报用户，绝不写进公开的 `docs/review/fix-progress.md`**。
4. **标记只是后台阅读状态**：`*_set_handled` 不删数据、不改客户端、不影响用户。删除能力不开放（要删去 WebUI）。所以标记本身安全，但**用户要先知道命中什么**。

## 1. 拉取与聚类

```text
crash_list({ pending: true, limit: 200 })      → { total, items[] }
feedback_list({ pending: true, limit: 200 })   → { total, items[] }
stats({ days: 7 })                             → 装机/日活/版本与品牌分布，用来判断「线上主流是哪个包」
```

- 先把 `total` 记下来（这是后面「标记前后各多少条」的基数），再翻页取 `items`。
- 崩溃按 `exception` 字段的**类名前缀**分组，不要一条条读：`IllegalStateException` 86 条里 80 条是同一个 Glide 消息。分组后每组的 `id` 清单记下来，后面批量标记直接用。
- 反馈按 `category` + 机型 + `version_name` 分组。同一机型连续几条同类抱怨（如三台 XTC 都报下载失败）本身就是线索。
- 每组的初判分三类：**像是真 bug / 像是已修（老版本）/ 根本没法查（信息不足）**。

**完成判据**：手里有一张表——组名、条数、id 清单、初判，外加两个 `total` 数字。

## 2. 单条取证

列表接口只给 `log_len`，正文和日志要单独取：

```text
feedback_detail({ feedback_id, log_chars: 4000 })   → 正文、contact、机型、log、thread（开发者与用户对话）
crash_detail({ crash_id, log_chars: 8000 })         → exception、message、thread、stack（默认已解混淆）、log
```

- `log` 为空的反馈是**常态**，不是异常，记为「信息不足」并写清缺什么（要日志 / 要复现步骤 / 要机型版本），别自己脑补成因。
- 日志里先搜这几类信号：`csrf 为空`、`Unable to resolve host`、`-111` / `-400`（风控与上报）、`OutOfMemory`、`dlopen`。DNS 失败与风控 -111 常常是同一现象的另一半证据。
- `thread` 里有开发者已回复的内容（「我之后修下」）——这类条目其实已被人认领，标记时别当新问题处理。

**完成判据**：每条候选都有明确判定「有证据（贴出关键行）」或「无证据（在等什么）」。

## 3. 定位代码

- 用反馈里的**用户原话**去 grep：`grep("缓存", "app/src/main")` 之类往往能一步命中设置的文案，再顺藤摸到实现。
- **注释与文档都可能错**，结论以源码为准；判断「有没有这行逻辑」时用 `grep` 找调用方，找不到调用方的函数就是死代码（`FileUtil.clearCache` 就是这样被排除的）。
- 排除法要写下来：把「哪一步可能删目录 / 哪一步可能砍音量」的候选路径逐条列出来，每条给出证伪依据（无调用方 / 只 ALTER 不 drop / 只在手动入口 / 内存态标记进程重启即失效…）。这次 #187 排了 9 条，最后结论是「代码里没有任何一步会清下载目录」——**一个站得住的「不是这里」比一个含糊的「可能是这里」有用得多**。
- 代码搜不到成因时，分岔口是「加诊断」还是「直接改」：**听感、手感、画质这类主观量，先加只观测的诊断**（见参考 B 的 #182），别猜着改默认值。

**完成判据**：每条结论都能落到 `文件:行号`，或者是「该路径不存在 / 没有调用方」的明确证伪。

## 4. 版本判别（最省事也最容易漏的一步）

线上的崩溃与反馈常常来自**几天前的包**，你正在看的 bug 可能早已修好。

- `version_code` 是 `YYMMDD<seq>`：`26110902` = 26.11.09 第 2 次构建；`version_name` 直接是 `26.11.09`。
- 拿疑似修复提交的时间比：`git log -1 --format="%h %ad %s" --date=short <commit>`。**包日期 < 修复提交日期 → 老包，改代码是白干**，直接进「已修」桶，标记时把这条理由写进汇报。
- 反过来，包日期 ≥ 修复提交日期仍然崩，才是新样本，需要重新看。

**完成判据**：每条崩溃都能说出「包的构建日期」与「疑似修复提交日期」，并据此归桶。

## 5. 标记

先给用户**命中条数**、得到确认再动手——「标已处理」虽是安全操作，但用户要知道什么被标掉了：

```text
crash_bulk_handled({ q, version_name, pending_only: true, handled: true, dry_run: true })   // 先数
crash_bulk_handled({ q, version_name, pending_only: true, handled: true, dry_run: false })  // 再改
```

- `dry_run` **默认是 true**，忘了传 `false` 就什么也没改。单次上限 1000 行，返回 `truncated: true` 说明没标完，继续下一批。
- 按 `id` 逐条标更可控，组内 id 清单在第 1 步就有了：`crash_set_handled({ crash_id, handled: true })` / `feedback_set_handled({ feedback_id, handled: true })`（一次 136 条崩溃 + 15 条反馈就是这么标的）。
- **留下的必须写清理由**：还能查的（不同源的崩溃、等用户补信息的反馈）保持 pending，汇报里列出来。

**完成判据**：重新 `crash_list({pending:true,limit:1})` / `feedback_list({pending:true,limit:1})` 复核 `total` 已下降，且下降数与「你说要标的条数」一致。

## 6. 修真 bug

走仓库既有纪律（`AGENTS.md` + `docs/agents/testing.md`），这条流水线额外强调三点：

- **纯函数下沉再写单测**：把判读逻辑抽成不 import `android.*` 的类（`activity/player/PlayerAudioDiagnostics.kt`、`player/PlayerVolumePolicy.kt` 都是这个形状），JVM 单测才跑得起来。
- **L0 必跑**：`./gradlew.bat :app:testStandardDebugUnitTest :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`，单测 `0 失败`。统计用例数别用 `[xml]` 强转 XML，用 `[System.IO.File]::ReadAllText($f,[System.Text.Encoding]::UTF8)` + 正则 `<testsuite\s+name="[^"]*"\s+tests="(\d+)"\s+skipped="(\d+)"\s+failures="(\d+)"\s+errors="(\d+)"`。
- **台账条目模板**（`docs/review/fix-progress.md` §1 顶部，按日期倒序插入）：来路（反馈 id / 机型 / 版本 / 用户原话）→ 根因（`文件:行号`）→ 改法 → **口径**（为什么这么改、明确没做什么）→ L0 结果 → **未验证 L2** → 给用户的复现话术。改完 `.md` 跑 `npx --yes markdownlint-cli2`。
- 提交小步：代码一条 `fix(scope): …`、文档一条 `docs(fix-progress): …`，用户说「并行、快」时也别把两件事塞进一个 commit。

**完成判据**：build 绿 + 台账有条目 + 已 push，并且「未验证 L2」那行写的是具体的人工验收动作，不是「应该没问题」。

## 7. 收尾汇报

给三样东西，不写流水账：

1. **数字**：pending 前后各多少、哪几组被标掉、留下的还剩几条。
2. **已修 / 已排除的结论**：每条一句话根因 + 提交 hash。
3. **只能口头说的**：私有功能的崩溃、需要用户补信息的反馈、未验证的人工项。

## 参考 A：控制台工具速查

| 工具 | 关键参数 | 说明 |
| --- | --- | --- |
| `stats` | `days`, `top` | 装机 / 日活 / 版本与品牌分布，判断线上主流包 |
| `feedback_list` | `pending`, `q`, `category`, `limit`, `offset` | 返回 `total` + 本页 `items`（含 `log_len`） |
| `feedback_detail` | `feedback_id`, `log_chars` | 正文 + 日志 + `thread`（开发者回复与用户追加） |
| `feedback_set_handled` / `feedback_bulk_handled` | `feedback_id` / 筛选条件 + `dry_run` | 只改阅读状态 |
| `feedback_note_add` | `feedback_id`, `content` | 只在控制台留内部备注，用户看不到 |
| `crash_list` | `pending`, `q`, `limit`, `offset` | `items` 含 `exception`、`exception_retraced`、`version_name`、`brand`、`model`、`thread` |
| `crash_detail` | `crash_id`, `log_chars`, `retrace` | `stack` 默认解混淆；`retrace.state` 1 成功 / 2 无 mapping / 3 失败 / 0 未尝试 |
| `crash_set_handled` / `crash_bulk_handled` | `crash_id` / 筛选条件 + `dry_run` | 同上，默认 `dry_run: true` |
| `qq_feedback_*` | — | QQ 群反馈是**另一张表**，与 App 反馈不混算 |

## 参考 B：两个判断范式

**排除法（#187「每次更新后下载的 500M 电影没了」）**：把「可能删目录」的路径全列出来——`FileUtil.clearCache`（无调用方）、`DownloadSqlHelper.onUpgrade`（只 ALTER 不 drop）、`DownloadRepository.clear`（死代码）、`AppStorageManager.clearTempFiles`（仅设置页手动）、启动迁移（只写 prefs 单键）、`UpdateManager`（只删 APK）、`DownloadListActivity` / `SplashActivity`（不碰文件）、空间不足（只在 `MediaMerger` 跳过合并）——逐条证伪，最后把结论落在**机制**上：下载根翻转只活在进程内存（更新=进程重启必丢）、`.DOWNLOADING` 残留让缓存页整条跳过。**「代码里没有这一步」+「真正的机制是这几个」** 比「可能是安装方式」有用得多。

**只观测（#182「仅音频连蓝牙音量只有别家一半」）**：三处代码都不砍半（默认增益 100% 只衰不增、增益同时打到 ijk 与外挂音轨、音量键走系统媒体音量）→ 剩下只可能在系统档位 / 蓝牙绝对音量 / 音频焦点，而这三层只能现场读 → 加一行 `audio_diag gain=… vol=…/… focus=… onlyAudio=… extTrack=… out=…`，并让 `verdict()` 自己给判读。**不猜着调高默认增益**：听感上的「一半」可能就是系统档位，猜错既修不好又会在别处爆音。诊断的开关沿用既有的调试开关，不新造。

## 参考 C：工具坑

- `crash_bulk_handled` / `feedback_bulk_handled` **默认 `dry_run: true`**，只数不改。
- `tools.list_agents()` 这类无参工具必须传 `{}`，否则报 `binding arguments must be lossless JSON`。
- PowerShell 没有 heredoc：提交信息先 `write` 到临时文件，再 `git commit -F 临时文件`。
- `edit` 工具可能把 `.kt` 改成 CRLF 并重写整文件；改完用 `[System.IO.File]::ReadAllBytes` 数一下 CRLF 数量再提交。
- 读中文文件别用 `Get-Content`（本机乱码），用 `read` 工具或 `[System.IO.File]::ReadAllText($f,[System.Text.Encoding]::UTF8)`。
