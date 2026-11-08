# 发版与更新检查

> 触发：推 tag / 改 `strings.xml` 的更新日志 / 动更新检查。本册是 Release 正文与发版链路的硬要求；**发版细则先读 `.dsh/skills/rebili-version-release/SKILL.md`**。

## Release 说明必须包含完整的本次更新说明

- 发布说明里要有 `strings.xml` 中 `update_log_current` 的全部条目（去掉 `<item>` 标签、保留「【YY.MM.DD 本次更新】」标题与编号）。**锚点只带日期、不带渠道后缀**：三个通道共用同一份当日日志，`verifyVersionConsistency` 校验的也是这个日期锚点。
- **只留 `### APK 校验值（MD5）` 与 `<!-- update: … -->` 机器元数据不算合格**，拿 GitHub 自动生成的「Full Changelog」commits 摘要顶替也不算。
- 这条已由 `build-release.yml` 保证：手工触发用输入的 `release_body`，**推 tag 时 `inputs.*` 恒空，改由 `.github/scripts/extract_update_log.py` 从 `update_log_current` 自动抽取**，抽不到就 `::error::` + 退出 1、拒绝发版（26.10.05 曾因没有这道兜底而漏掉说明）。**唯一例外是实验版**：`26.11.08` 起 exp 通道抽不到时只 `::warning::` 并用占位说明（见下节）。**别把这段逻辑改回「只取 `inputs.release_body`」**。发完仍要回读 Release 正文确认条数对得上。

## 发版流程与中转

- **发版与更新检查**：推 tag 或手工触发 `.github/workflows/build-release.yml` —— 构建签名 APK → 创建 GitHub Release 并上传全部附件 → **通知中转服务**（地址与共享密钥都在仓库 Secrets：`RELAY_URL` / `RELAY_SECRET`；请求体经 HMAC-SHA256 签名），由中转服务自己去 GitHub 拉附件并同步到 Gitee `zisekongling/bili-terminal-re`。
- **R8 mapping 归档（26.11.01 新增）**：`assembleRelease` 之后有一步把
  `app/build/outputs/mapping/{standardRelease,coexistRelease}/mapping.txt` scp 到 HK 的
  `/opt/rebiliterminal-api/mappings/`，命名 `<version_code>_<flavor>_<variant>_mapping.txt`
  （variant 看有没有注入私有源码），供控制台解混淆崩溃堆栈。需要 `Secrets.HK_SSH_KEY`
  （可选 `HK_HOST` / `HK_USER` / `HK_MAPPING_DIR`），没配就跳过并打印 notice。
  **mapping 含私有类名/方法名，绝不能作为公开仓库的 artifact 上传** —— 只走 scp。
  漏传的版本，控制台会显示「该版本没有 mapping」并回退原始堆栈。
- **Action 侧不要直接访问 Gitee**（网络不通），参见该文件末尾的 Notify relay 步骤；**中转地址与密钥都不要写进代码库或日志**。
- **版本号与强制更新**：`versionCode` / `versionName` 不再从 `build.gradle` 里 grep 字面量（26.11.08 起是派生值），而是工作流 `verify-version` **按 tag 推算**、再经 `-Pchannel` / `-Pseq` 传给 Gradle，构建完用 `aapt2 badging` 复核产物与 tag 一致，最后写进 Release 说明的机器可读元数据。两侧必须一致的原因见 `docs/version-channels.md` §4。
- 客户端更新检查按「Gitee → GitHub」**拉发行版列表**挑本通道最新的稳定版（26.11.08 前是读 `releases/latest`；`/latest` 会排除 prerelease，但两个源的返回顺序相反、Gitee 的 prerelease 语义也没实测过，所以改成自己按 `versionCode` 比，见 `util/UpdateChannel.kt`），**客户端已不再读 config.json**。
- 漏同步时用 `.github/workflows/relay-notify.yml` 重新通知（幂等，重复通知无害）。

## 三通道（稳定 / 测试 / 实验）

> 完整口径（编码公式、Gradle 参数、客户端选版、上报字段、站点过滤）在 [`../version-channels.md`](../version-channels.md)，**这里只列发版时必须照做的部分**。

| 通道 | tag | versionCode | 何时用 |
| --- | --- | --- | --- |
| 稳定 | `26.11.08` / `26.11.08.1` | `26110800` / `26110801` | 给所有人；官网与 `/api/release` 只认这一档 |
| 测试 | `26.11.08-beta.1` | `26110811` | 自己与愿意帮忙的人先装一轮 |
| 实验 | `26.11.08-exp.1` | `26110821` | 完全没测过，出问题预期之内 |

- **tag 就是唯一入口**：`.github/workflows/build-release.yml` 从 tag 后缀解出渠道与序号，`prerelease` 自动设成「非稳定通道」，手工触发时也要按这个格式填 tag。
- **同日重发或换通道要改序号**：`versionCode` 尾两位是「渠道码 × 10 + 当日序号」，客户端判更新是严格 `>`，序号不升就收不到。
- **渠道不会被「降级」**：从 beta 切回 stable 后，装着的 beta 包 `versionCode` 仍更大，只有日期更新的稳定版才会覆盖它——这是刻意的，别试图绕过。
- **实验版允许没有更新日志**：`update_log_current` 抽不到时 exp 只 `::warning::` 并用占位说明，稳定 / 测试仍 `::error::` 拒绝发版。
- **官网只认稳定版**：站点仓库的 `netlify/functions/release.js` 与 `tools/sync-release.py` 已改成「拉列表筛无后缀 tag」，发 beta / 实验不会把首页版本号带跑。改这两处前先读 `docs/version-channels.md` §7。
- **预发布不进 `releases/latest`**：GitHub 天然排除 prerelease；中转服务建 Gitee 发行版时原样透传该字段（`bridge.py` 无需改）。

## 附件名白名单

- **中转服务端有一份附件名白名单（`assets.include`），只同步里面列出的文件名**——新增发行渠道（如 26.10.11 的共存版 `app-coexist-*`）时必须同步把该渠道的 4 个附件名加进白名单并重启中转服务，否则该渠道在 Gitee 上取不到包、只能回落到 GitHub 源（26.10.11 首次发共存版就踩到过）。

## 老客户端迁移（一次性）

> 26.10.02 及更早的客户端只认 123pan 上那份 `config.json`。发版时若把 `emit_config_json` 打开，工作流会额外产出一份**给老客户端**的 `config.json`（`downloadUrl` = Gitee 上 32 位包的直链、`forceUpdate` 与本次一致）并挂到 Release，需人工把它传到 123pan。**它只用于渠道切换那一次**，新客户端不读它。

## 只想拿包、不想发版（build_only）

> 手工触发 `build-release.yml` 时勾选 `build_only`（26.10.17 新增）。它照常跑单测 + `assembleRelease` + 整理发行 APK + 生成 MD5 并上传 artifact `release-apks`，但**跳过**「计算发行 tag 与发布说明」「创建 GitHub Release」「Notify relay」等全部发行动作——不产生 Release、不占 tag（release immutability）、不同步 Gitee，适合传应用商店或自己装。

- 实现上由 job 级 `env.BUILD_ONLY` 归一化（**推 tag 时 `inputs.*` 恒空，必须显式判 `event_name`**），五处步骤条件前置 `env.BUILD_ONLY != 'true'`，并**比较字符串而非布尔**。
- 「tag 留空」不是等价做法：那条路仍会组装发布说明，`update_log_current` 抽不出条目时会 `::error::` 退出。细节见 `docs/review/fix-progress.md` §3.25。
