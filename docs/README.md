# 文档总索引

> 整理时间：2026-10-27（基线 `versionName 26.10.27` / `versionCode 2610270`）。
> **规则**：本目录只放**活文档**（描述现状 / 承载未完成工作）。一次性审查、已执行完毕的方案、过程流水一律进 `docs/archive/`（索引见 [`archive/README.md`](archive/README.md)）。
> 引用文档前先确认它是不是归档的——**归档文档只代表当时**。

## 按需求找入口

| 你要做的事 | 读这份 | 说明 |
| --- | --- | --- |
| 改任何非平凡代码 | [`../AGENTS.md`](../AGENTS.md) → [`architecture-map.md`](architecture-map.md) | AGENTS 是每次都要遵守的最小约定 + 按场景索引；领域细则在 [`agents/`](agents/)；架构地图是真实分层 + 逐条坑。**两份都别整份读**：架构地图 ≈9.6 万 token，按 `AGENTS.md` 里的阅读预算表只读你那几节；§7 坑点库 58 个小节 ≈5.3 万 token，用 grep 定位单个 `§7.x` |
| 找「现在的状态 / 还没做的事」 | [`review/fix-progress.md`](review/fix-progress.md) | **唯一活台账**：当前版本状态、未完成待办（§2，含播放器合并 / 弱网 / 下载链路的剩余项与 §2.4 视觉技术债）、26.10.22 起的状态快照、历史章节索引。**平时只读 §1 + §2**（全文件 ≈15.9 万 token，快照区是流水，按关键词 grep） |
| 决定这条测试该不该自己跑 / 改完怎么验证 / 交接给人测 | [`agents/testing.md`](agents/testing.md) | **验证分工的唯一判据**：AI 能读文字与数字（日志、控件树、落盘文件、状态码），读不了画面 / 声音 / 手感。三级验证 L0/L1/L2、L1 两轮止损预算、AI 不做的事清单、人测交接单模板、结论写回哪里 |
| 改网络 / 接口 / 风控相关 | [`bilibili-api-notes.md`](bilibili-api-notes.md) | 接口常识与风控硬约束（错误码语义、取流有效期、弹幕协议取舍） |
| 发版前上真机 | [`review/real-device-regression-checklist.md`](review/real-device-regression-checklist.md) | 与发行版同维护的回归清单（260 条），每条的来源小节都标在标题里 |
| 用 adb / AI agent 驱动 Debug 包测试 | [`review/agent-test-guide.md`](review/agent-test-guide.md) → [`agents/testing.md`](agents/testing.md) | 前者是调试桥（可编程控制通道）命令表、日志与 HTTP 追踪、UETool 干扰规避、用哪个 flavor；后者决定**哪些测试值得让 AI 跑、哪些直接交给人** |
| 找不懂代码的人验收新功能 | [`review/manual-test-guide-261106.md`](review/manual-test-guide-261106.md) | 给普通用户写的手工测试指南：34 条用例，每条给「怎么做 / 应该看到 / 不算 bug」，按不登录、登录后、特殊条件分三部分 |
| 写新功能 | [`superpowers/plans/2026-08-27-new-features-roadmap.md`](superpowers/plans/2026-08-27-new-features-roadmap.md) | 功能 backlog（含实测过的接口地址、判据与仍未落地项） |
| 改播放器 / 弱网 / 下载 | [`architecture-map.md`](architecture-map.md) §7.4 / §7.27 / §7.54 + [`review/fix-progress.md`](review/fix-progress.md) §2.2 | 现状与坑看架构地图，剩余待办看台账；原合并方案、弱网调研、下载排查与引擎方案已归档（ijkplayer 选项语义与 `.so` 实测仍可在 `archive/superpowers/specs/` 里查） |
| 改教程系统 | [`tutorial-system-redesign.md`](tutorial-system-redesign.md) | 重做进行中，旧链路未删完——**动之前先读它** |
| 改菜单设置 / 启动页 | [`superpowers/specs/2026-10-20-menu-start-page.md`](superpowers/specs/2026-10-20-menu-start-page.md) | 启动页单选（默认推荐、失效写回）与说明块折叠/长按隐藏的需求、验收与设计 |
| 改短视频取流 | [`superpowers/specs/2026-10-21-short-video-feed-protocol.md`](superpowers/specs/2026-10-21-short-video-feed-protocol.md) | 取流参数、分页状态归属、横屏过滤与跨批去重、降级池策略；动 `ShortVideoFeedApi` 前先读 |
| 改伪装页 / 顶栏双击手势 | [`superpowers/specs/2026-10-22-disguise-mode.md`](superpowers/specs/2026-10-22-disguise-mode.md) | 需求与验收口径、三个存储键、手势层接线点、两种样式的退出方式、已知取舍（单击多等 0.3 秒 / 忘了暗号只能强杀） |
| 改用户看到的任何文案 | 直接改 `app/src/main/res/values/strings.xml` | **用户可见文案已集中到这一处**（26.10.19 完成，见 [`copy-inventory/STRINGS-MIGRATION.md`](copy-inventory/STRINGS-MIGRATION.md) 第九节）；想知道某句话原本出现在哪个页面/干什么用，查 [`copy-inventory/`](copy-inventory/README.md) 的 7 份清单（1012 条）；改之前先读 [`copy-inventory/GUIDE.md`](copy-inventory/GUIDE.md) 的红线 |
| 看用户能用到什么 | [`FEATURES.md`](FEATURES.md) | 用户视角功能总表（对应用户文档，不含实现细节） |
| 查 B 站接口字段 | [`../bilibili-API/`](../bilibili-API/) | 上游接口文档快照（**注意上游已关停，见 `bilibili-api-notes.md` §1 的合规风险**） |
| 部署 / 维护自建服务端 | [`../server/README.md`](../server/README.md) | 反馈 / 公告 / 匿名统计 / 崩溃上报的后端（FastAPI + SQLite + nginx） |

## AGENTS 分册（`agents/`）

> `AGENTS.md` 只放每次都要遵守的最小约束 + 索引；下面是按领域拆开的细则，**做到哪块读哪份**，不要凭记忆。

| 分册 | 内容 | 触发时机 |
| --- | --- | --- |
| [`agents/dev-conventions.md`](agents/dev-conventions.md) | 列表页 / 菜单 / 横滑翻页 / 设置项与子页面 / 外观设置 / 弹窗 / 伪装页 / 排序分区 / 请求解析分离 / 用户可见文案 | 写界面与功能时 |
| [`agents/security.md`](agents/security.md) | exported、https 与明文白名单、allowBackup、调试页只进 Debug、DUMP 权限、调试开关即改即生效 | 动清单 / 网络 / 备份 / 调试入口时 |
| [`agents/build.md`](agents/build.md) | 构建任务名与 flavor、build_tui.py、adb / 模拟器、工具链、gradle.properties、build cache 回放、增量编译坏掉、量体积与 APK 构成、原生库 | 构建 / 单测 / 装包 / 构建报怪错时 |
| [`agents/release.md`](agents/release.md) | Release 说明硬要求、relay 中转、附件名白名单、老客户端 config.json、build_only | 发版 / 改更新日志时 |
| [`agents/workflow.md`](agents/workflow.md) | 一次改动的标准流程（带时间预算）、小步提交、一律推 main 与 commit 格式、改完必须验证的三级判据（L0/L1/L2，细则在 `testing.md`） | 准备提交 / 准备说"应该没问题"时 |
| [`agents/testing.md`](agents/testing.md) | **验证分工**：唯一判据（结论能不能被机器读出来）、L0 静态 / L1 设备可观测 / L2 人工验收、L1 两轮止损预算（≤10 分钟、≤15 条 adb 命令）、AI 不做的事、人测交接单模板、结论落点 | 改完要验证 / 想上模拟器 / 决定交不交给人测时 |
| [`agents/pitfalls.md`](agents/pitfalls.md) | 仍存在的已知坑：API 重复解析、跨包 id、notifyItemRangeInserted、通知 ID、下载锁、播放器 / 弹幕 / 保亮屏 / 原生库 / 主线程判活、伪装与顶栏手势 | 改这些易踩区之前 |
| [`agents/docs.md`](agents/docs.md) | 改完同步文档的落点表、markdownlint 命令与豁免、copy-inventory 逐字素材 | 每个改动收尾时 |

## 文档清单（活）

| 文件 | 内容 | 维护时机 |
| --- | --- | --- |
| [`architecture-map.md`](architecture-map.md) | 架构分层地图（322,334 字节 / 2,052 行 ≈9.6 万 token，**按 § 查不通读**）：§6 网络层、§7 改前必查的坑（58 个 7.x 小节，≈5.3 万 token，**只 grep 不通读**）、§8 UI 基建与外观、§9 API 层映射（48 个文件，读到 `1879–1986` 即可）、§10 检查清单 | 改架构 / 基建 / 新增基类时 |
| [`optional-features.md`](optional-features.md) | 可选私有功能机制：`private/` 目录、AGP 四处注入点、`OptionalFeatures` 反射门面、现有挂点、本地开发与 CI 注入（不写私有功能的实现细节） | 新增/调整「不进公开仓库」的功能、改 `app/build.gradle` 的 `hasPrivateFeature`、动发版注入步骤时 |
| [`FEATURES.md`](FEATURES.md) | 用户可见功能总表（20 章） | 新增用户可见功能时 |
| [`bilibili-api-notes.md`](bilibili-api-notes.md) | 接口与风控常识（来源与可信度、速查表、11 条硬约束、值得做/不做） | 核实出新接口结论时 |
| [`review/fix-progress.md`](review/fix-progress.md) | 活台账（≈15.9 万 token）：当前状态 + 未完成待办 + 26.10.22 起的快照（更早的 §3.0~§3.51 已归档）+ 历史章节索引。**平时只读 §1 §2，别整份读**（文件开头有逐节行号表） | 修完 bug / 关掉待办 / 每轮真机验证后 |
| [`review/real-device-regression-checklist.md`](review/real-device-regression-checklist.md) | 发版前真机回归清单（260 条，唯一编号 1–258；含第十三节视觉面；条目只增不改，失效就地标注作废） | 每个发行版 |
| [`review/agent-test-guide.md`](review/agent-test-guide.md) | Debug 包的 agent 测试手册：调试桥命令、可观测性设施、已知限制与设计原则 | 增删调试能力 / 调试桥命令时 |
| [`review/manual-test-guide-261106.md`](review/manual-test-guide-261106.md) | 面向普通用户的手工测试指南：26.11.06 新增功能的 34 条用例（无技术术语，含「不算 bug」与已知限制） | 找人验收新功能 / 自己按步骤过一遍时 |
| [`review/compare-with-re-biliterminal-261024.md`](review/compare-with-re-biliterminal-261024.md) | 与同源分支 `cyq114514/Re-BiliTerminal`（1.2.3fix）的六维对比审计：91 条问题（P0×2 / P1×20 / P2×69）、定量总表、P0–P2 路线图、四轮核验；**按目标设备口径（旧版安卓手表）评分** | 再次对比 / 排期取建议时；结论基于 2026-10-24 双方 HEAD |
| [`review/fork-comparison-2026-11-06.md`](review/fork-comparison-2026-11-06.md) | 三个兄弟分支（Revival / PianoEthan / cyq 1.3.0）与我们的功能增量对比、实现质量优化、不建议照搬清单与三批实施路线；分报告在 `review/forks/` | 排期移植 / 参考兄弟分支实现时；结论基于 2026-11-06 双方 HEAD |
| [`superpowers/plans/2026-08-27-new-features-roadmap.md`](superpowers/plans/2026-08-27-new-features-roadmap.md) | 功能路线图 + 从各报告回迁的仍未落地项 | 排期 / 落地后 |
| [`superpowers/specs/2026-10-20-menu-start-page.md`](superpowers/specs/2026-10-20-menu-start-page.md) | 菜单启动页与菜单设置说明改造：需求、验收标准、存储键与行结构设计 | 改菜单设置 / 启动流程时 |
| [`superpowers/specs/2026-10-21-short-video-feed-protocol.md`](superpowers/specs/2026-10-21-short-video-feed-protocol.md) | 竖屏短视频取流协议修订：参数约定、会话级分页状态、横屏过滤、跨批去重、降级池与验收标准 | 改短视频取流 / 排查短视频页内容异常时 |
| [`superpowers/specs/2026-10-22-disguise-mode.md`](superpowers/specs/2026-10-22-disguise-mode.md) | 伪装与安全：双击顶栏触发、两种样式与各自退出方式、三个存储键、手势层三个接线点、验收标准与已知取舍 | 改伪装页 / 顶栏双击手势时 |
| [`tutorial-system-redesign.md`](tutorial-system-redesign.md) | 教程系统重做（含 §五 待办 4 项） | 动教程时 |
| [`copy-inventory/README.md`](copy-inventory/README.md) | 全仓文案清单总索引（7 份、1012 条：`A` 应用身份 / `L` 更新日志 / `T` 教程 / `S` 设置项 / `U` 页面静态文案 / `M` 运行时提示 / `E` 彩蛋） | 改文案时按需打开对应那一份，改完在「改写」栏回填 |
| [`copy-inventory/GUIDE.md`](copy-inventory/GUIDE.md) | 文案改写指南：同一句话的多出处、四种改法、十条红线、手表字数常识、自查清单、FAQ、**怎么把新文案交给 agent** | **动手改文案前先读这一份**，比总索引更先看 |
| [`copy-inventory/DUPLICATES.md`](copy-inventory/DUPLICATES.md) | 重复文案报告：16 组同一句话的多处位置（逐字相同的 8 组 + 措辞不一致的 8 组 + 刻意保留的 4 类） | 改任何一句之前查它，避免改一处漏一处 |
| [`copy-inventory/STRINGS-MIGRATION.md`](copy-inventory/STRINGS-MIGRATION.md) | 文案集中化台账：**已全部完成**（B1~B14，`strings.xml` 1787 条 / `UiText` 映射 814 条 / 1002 处调用，26.10.20 复核），含命名规范、占位符规范、不迁移清单、27 条已知坑 | 改文案只想看「现在该改哪里」→ 看第九节；想知道为什么要这么设计 → 看 5.8~5.10 与第 7 节 |
| [`archive/README.md`](archive/README.md) | 归档索引：每份归档文档的内容与「为什么归档」 | 归档新文档时 |

## 维护纪律（避免又长出一堆旧文档）

1. **只写现状，不写流水**。修 bug 的经过超过一屏就并进 [`review/fix-progress.md`](review/fix-progress.md) 的「历史章节索引」一行，正文留在归档里。
2. **一份知识只有一个落点**：架构/坑 → `architecture-map.md`；用户可见 → `FEATURES.md`；接口/风控 → `bilibili-api-notes.md`；状态与待办 → `review/fix-progress.md`。别在别处复述。
3. **归档不是删除**：`git mv` 进 `docs/archive/` 并在 `archive/README.md` 加一行「内容 + 为什么归档」，正文一字不改（只在开头加存档说明）。
4. **归档前先回迁**：文档里唯一还活着的知识（不要回退的取舍、未落地的待办、实测数字）要先搬进活文档，再归档。
5. 文档间引用一律写**仓库相对路径**（`docs/...`），归档后同步改引用；源码注释里的文档路径同样要改。
6. **格式**：改完任何 `.md` 跑一次 `npx --yes markdownlint-cli2 "AGENTS.md" "readme.md" "docs/**/*.md" "!docs/archive/**"`，**0 错误**才算完；规则与豁免理由见 [`agents/docs.md`](agents/docs.md)。
