# 提交与自测

> 触发：准备提交 / 准备说「应该没问题」/ 要装包验证。常驻摘要见 `AGENTS.md`。

## 小步提交

- **小步提交**：把改动切成小步，每步都能独立验证（`assembleDebug` + 单测通过）并说清楚改了什么，再进入下一步；不要一次性堆大量改动。

## 一律提交到 main

- **做完就提交并推送，一律提交到 `main`**（26.10.22 收紧，原先「按改动类型分三条分支」的做法作废）：一次改动完成后**自己写 commit message、自己 commit、自己 push 到 `main`**，不要等用户催；只有改动没验证过（构建/单测没跑通）才停下来问。
- 惯例：中文、`type(scope): 摘要`（`fix` / `feat` / `docs` / `chore` / `refactor` / `test`），一次提交只装一个关注点，末尾带 `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`。
- **为什么不再分分支**：分支桶让「一个改动」散在两处、`main` 上多出一批 merge 提交，提 PR 给上游时结构难看且容易重复。现在所有改动先落 `main`，需要 review / 合上游时再从 `main` 开 PR 或直接对比。
- 历史上的 `feat/gitee-release`、`fix/issue-1-swipe-seek-back` **两个分支名保留、内容停在 2026-10-22 的同步点，不再往上叠**（远程对象别删，CI / 旧链接可能还引用）。要发版仍走 tag / `build-release.yml`，与分支无关。

## 改完必须自测

- **改完必须自测，不许只说"应该没问题"**（26.10.22 起）：本机常驻 **MuMu 模拟器**（`adb connect 127.0.0.1:7555`，Android 15 / x86_64，`adb root` 可用 → 用 `app-coexist-x86-debug.apk`）。
- 凡涉及播放器 / 网络 / UI 时序的改动，改完自己装包、跑一遍、看日志与截图，把实测结论（日志原文、关键截图、未复现的原因）写进 `docs/review/fix-progress.md`。**"编译通过 + 单测绿"只证明纯函数没写错，不证明功能可用**（见 `docs/archive/review/device-test-plan.md` 〇节）。测法见 `docs/review/agent-test-guide.md`。
- **MuMu 的坑（26.10.27 更新）**：活动会落在 `display 14/15/16...` 等**副屏**（每次启动新建一个，`dumpsys window displays | grep mDisplayId` 查）。26.10.27 实测 **`screencap -d <id>` 对任何 display 都回 `Failed to take screenshot. Status: -2`**（root 也一样），所以副屏上**截不了图**；`screencap -d 0` 拍到的是桌面。可行替代：**`uiautomator dump --display <displayId>` 拿控件树与 bounds**（文字/位置/状态都能验），配合 **`input -d <displayId> tap/swipe/motionevent`** 驱动；**`input` 不带 `-d` 一律落在桌面**。长按用 `input -d <id> motionevent DOWN x y` → sleep 1s → `motionevent UP x y`（`swipe x y x y 1200` 同点同终点会被当成滚动）。

## 提交后、打 tag 前必须核对提交内容（26.11.06 事故）

- **事故**：26.11.06 发版时，我给「更新日志补 4 条」这一步写了 `git add -A <路径1> <路径2>`——**`-A` 和路径同时出现时 git 会忽略路径、对全仓暂存**，把当时工作区里其它已改未提交的文件一起卷了进去。提交完我又只看了 `git log --oneline` 的两行输出，没核对内容，就直接打了 tag 并触发发版，等于**带着一条「说不清装了什么」的提交发布了出去**。
- **规则：每个 commit 之后、打 tag 或触发 CI 之前，必须跑一次 `git show --stat <sha>`，逐条比对「文件清单」与「提交说明讲的事」是否对得上。** 对不上就先修，再打 tag。`git log --oneline` 只证明提交存在，不证明它装的是该装的东西。
- **不要写 `git add -A <路径>`**：要么 `git add <路径>`（只加指定文件），要么干脆 `git add -A`（明确就要全仓）。两者混写是这次事故的直接成因，语义和直觉相反。
- 合完 PR / 解完冲突之后尤其危险：`git merge` 自动合并成功的文件本来就躺在工作区里，此时任何一次「顺手全加」都会把它们裹进一个不相干的提交。合完 PR 应当**先独立提交一次 merge 结果**，再动别的。
- 理由：提交历史是这个仓库唯一的追溯依据，`fix-progress.md` 与 `README` 都靠它反查「这版到底改了什么」。一条名不副实的提交，比一次构建失败更难发现。

## 小改动不必上模拟器

- **小改动不必上模拟器**（26.10.22 收）：文案/注释、单文件小逻辑、日志收敛、纯常量调整这类**不碰播放器状态机与线程时序**的改动，跑通 `assembleDebug`（至少 `compileCoexistDebugKotlin`）+ 相关单测即可直接提交。
- 判据是"错了会不会只有真机才看得出来"，不是"改动行数少"。反过来的例子：`FLAG_KEEP_SCREEN_ON` 的写法、`View` 级 `keepScreenOn` 能否清掉、弹幕时钟接没接上——这些**只能**靠 `dumpsys` / 日志在设备上确认，属于必须实测的类别。
