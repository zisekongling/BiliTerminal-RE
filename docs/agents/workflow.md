# 提交与自测

> 触发：准备提交 / 准备说「应该没问题」/ 要装包验证。常驻摘要见 `AGENTS.md`。

## 小步提交

- **小步提交**：把改动切成小步，每步都能独立验证（`assembleDebug` + 单测通过）并说清楚改了什么，再进入下一步；不要一次性堆大量改动。

## 一律提交到 main

- **做完就提交并推送，一律提交到 `main`**（26.10.22 收紧，原先「按改动类型分三条分支」的做法作废）：一次改动完成后**自己写 commit message、自己 commit、自己 push 到 `main`**，不要等用户催；只有改动没验证过（构建/单测没跑通）才停下来问。
- 惯例：中文、`type(scope): 摘要`（`fix` / `feat` / `docs` / `chore` / `refactor` / `test`），一次提交只装一个关注点，末尾带 `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`。
- **为什么不再分分支**：分支桶让「一个改动」散在两处、`main` 上多出一批 merge 提交，提 PR 给上游时结构难看且容易重复。现在所有改动先落 `main`，需要 review / 合上游时再从 `main` 开 PR 或直接对比。
- 历史上的 `feat/gitee-release`、`fix/issue-1-swipe-seek-back` **两个分支名保留、内容停在 2026-10-22 的同步点，不再往上叠**（远程对象别删，CI / 旧链接可能还引用）。要发版仍走 tag / `build-release.yml`，与分支无关。

### 例外：动私有功能 / 开源边界的改动走分支 + PR

- 范围：新增或搬迁**逆向产物**、改 `private/` 注入机制、清理公开侧残留（代码、文案、文档都算）。
- 流程：从 `main` 切分支（如 `refactor/reverse-eng-to-private`）→ 小步提交 → push → 开 PR 到
  `upstream:main`。**不直接推 `main`**，因为这类改动通常跨两个仓库（私有仓要先落地，否则私有构建
  缺实现），且删的行数往往比加的多，留在 `main` 上风险太大。
- 两仓顺序：**先私有仓 `main`，再公开仓分支**；PR 描述里只写「实现搬进私有仓库」这一层，不写搬了
  什么实现（见 `AGENTS.md` 的「私有功能仓库」一节）。
- PR 合并后再删分支；`main` 上不要留半成品。
- 没落在上面的改动（改文案、修崩溃、补文档）**照旧直接推 `main`**，不走 PR。

## 一次改动的标准流程（26.11.06 起，带预算）

| 步 | 做什么 | 预算 | 卡住去哪 |
| --- | --- | --- | --- |
| 1 定位 | `grep` 关键字找文件，只读 1~3 个真正要改的文件 | ≤5 分钟 | 找不到入口 → `docs/architecture-map.md` §9 API 映射表 / §3 导航 |
| 2 读上下文 | `AGENTS.md` + 上表里对应的**那一份**分册，按 § 读 | ≤10 分钟 | — |
| 3 改 | 一次只做一件事，边改边编译 | 按改动规模 | 构建怪错误 → `docs/agents/build.md` |
| 4 自测 | L0 必做；L1 只在判据可读时做，**两轮上限** | 见 `docs/agents/testing.md` §3 | 两轮不过 → 写交接单交人 |
| 5 提交 | `git show --stat` 核对后 push 到 `main` | ≤2 分钟 | — |
| 6 落文档 | `docs/review/fix-progress.md` **一行** + 该改的分册 | ≤3 分钟 | 落点表见 `docs/agents/docs.md` |

- **一次改动不许整份读三份以上大文档**：`docs/architecture-map.md`（≈9.6 万 token）、`docs/review/fix-progress.md`（≈15.9 万 token）、`docs/FEATURES.md`（≈3.4 万 token）都按 § 查，永远不要从头读到尾。`fix-progress.md` 平时只需 §1（当前状态）与 §2（未完成待办），§3/§4/§5 是历史，用 `grep` 反查。
- **需要人测的，提交之后立刻把交接单给出去**，不要攒到发版前一次性堆 30 条。

## 改完必须自测（分三级，细则见 `docs/agents/testing.md`）

- **改完必须自测，不许只说"应该没问题"**。但"自测"按证据形态分三级，**不是每个改动都要上模拟器**：
  - **L0 静态**（每个改动）：`assembleDebug` + 相关单测。**"编译通过 + 单测绿"只证明纯函数没写错，不证明功能可用**（见 `docs/archive/review/device-test-plan.md` 〇节）。
  - **L1 设备可观测**（判据能落在日志 / 控件树 / 产物结构时）：用调试桥拿证据，**两轮上限**，两轮不过就停。命令见 `docs/review/agent-test-guide.md`。
  - **L2 人工验收**（画面、声音、手感、主观取舍、登录态写操作、长链路多步交互）：**写交接单交给人**，模板见 `docs/agents/testing.md` §5，范本是 `docs/review/manual-test-guide-261106.md`。
- 判据是"错了 AI 能不能靠读东西发现自己错了"，不是"改动行数少不小"。**必须实测的典型**：`FLAG_KEEP_SCREEN_ON` 的写法、`View` 级 `keepScreenOn` 能否清掉、弹幕时钟接没接上——这些只能靠 `dumpsys` / 日志在设备上确认。**交给人的典型**：颜色、字有没有被切、音轨、跟手、掉帧。
- MuMu 的环境坑（应用被丢到新建的副屏、display id 每次都会变、`input` 不带 `-d` 落桌面、截图回 `Status: -2` 时怎么办）统一记在 `docs/review/agent-test-guide.md` §6，别在这里再抄一遍。

## 提交后、打 tag 前必须核对提交内容（26.11.06 事故）

- **事故**：26.11.06 发版时，我给「更新日志补 4 条」这一步写了 `git add -A <路径1> <路径2>`——**`-A` 和路径同时出现时 git 会忽略路径、对全仓暂存**，把当时工作区里其它已改未提交的文件一起卷了进去。提交完我又只看了 `git log --oneline` 的两行输出，没核对内容，就直接打了 tag 并触发发版，等于**带着一条「说不清装了什么」的提交发布了出去**。
- **规则：每个 commit 之后、打 tag 或触发 CI 之前，必须跑一次 `git show --stat <sha>`，逐条比对「文件清单」与「提交说明讲的事」是否对得上。** 对不上就先修，再打 tag。`git log --oneline` 只证明提交存在，不证明它装的是该装的东西。
- **不要写 `git add -A <路径>`**：要么 `git add <路径>`（只加指定文件），要么干脆 `git add -A`（明确就要全仓）。两者混写是这次事故的直接成因，语义和直觉相反。
- 合完 PR / 解完冲突之后尤其危险：`git merge` 自动合并成功的文件本来就躺在工作区里，此时任何一次「顺手全加」都会把它们裹进一个不相干的提交。合完 PR 应当**先独立提交一次 merge 结果**，再动别的。
- 理由：提交历史是这个仓库唯一的追溯依据，`fix-progress.md` 与 `README` 都靠它反查「这版到底改了什么」。一条名不副实的提交，比一次构建失败更难发现。

## 同一个工作树上有多个会话（26.11.15 实测）

本机同时开多个 agent 会话改同一个仓库时，所有人共用一个工作树（同一份 index、同一份 `app/build`）。26.11.15 一次真实事故：会话 A 为「隔离对方未提交的 WIP」跑 `git stash push -m ... -- <4 个路径>`，把同一文件里**自己刚加的字符串**一起存走了；会话 B 随后 `git add -A` 提交，把 A 的 `AccountManager.java` 卷进 B 的提交；A 的构建报 `Unresolved reference 'text_account_export_all'`，追了半天以为是资源链路问题。

规矩：

- **不要对整树或跨会话路径 `git stash` / `git checkout --` / `git reset`**。确需暂存只 `git stash push -- <自己的具体路径>`，并且当场用完就 pop/drop。
- **提交一律 `git add <显式路径>...`**，永远不要 `git add -A`（理由见上一节 26.11.06 事故）。
- 两个会话都可能改的同一份文件（`strings.xml`、`docs/FEATURES.md`、`docs/review/fix-progress.md` 是重灾区），提交前先 `git status` 看一眼是不是 `MM`，并**口头约定由谁提交**——一个人提交、另一个人事后增量补，别各交一半。
- 构建**串行**：共用 `app/build`，两个 gradle 同时跑会锁 `R.jar` 并让 Kotlin daemon OOM，判据与解法见 `docs/agents/build.md`「同工作区并发构建」。
- 收尾时各自 `git show --stat <sha>` 核对：卷进别人文件不可怕，**没人发现才可怕**。

## 提交前检查行尾抖动（26.11.09）

- **症状**：`git diff --stat` 显示某个 `.java` / `.kt` 文件几百行改动，而实际只加了几行——这是**工作区被转成 CRLF、索引里是 LF**（本仓库 `.java`、`.kt`、布局文件都是 LF；注意 `strings.xml` 是 CRLF，别一起归）。
- **成因**：编辑器 / 自动化改写工具按平台默认换行整文件重写。26.11.09 这次一个 `edit` 操作就把 `api/PlayerApi.java`（2459 行）与 `model/PlayerData.java`（396 行）整份翻成 CRLF，`docs/copy-inventory/STRINGS-MIGRATION.md` 坑 17 也记过同一现象。
- **判据与修法**：`git ls-files --eol -- <file>`（看 `w/crlf` 就是要修的那个）；`git diff --numstat` 与 `git diff -w --numstat` 数量差得远就是行尾抖动；用 `[System.IO.File]::ReadAllText` 读、`-replace "`r`n","`n"` 归一，再以 UTF-8 无 BOM 写回。
