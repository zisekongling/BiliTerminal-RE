# 发版与更新检查

> 触发：推 tag / 改 `strings.xml` 的更新日志 / 动更新检查。本册是 Release 正文与发版链路的硬要求；**发版细则先读 `.dsh/skills/rebili-version-release/SKILL.md`**。

## Release 说明必须包含完整的本次更新说明

- 发布说明里要有 `strings.xml` 中 `update_log_current` 的全部条目（去掉 `<item>` 标签、保留「【YY.MM.DD 本次更新】」标题与编号）。
- **只留 `### APK 校验值（MD5）` 与 `<!-- update: … -->` 机器元数据不算合格**，拿 GitHub 自动生成的「Full Changelog」commits 摘要顶替也不算。
- 这条已由 `build-release.yml` 保证：手工触发用输入的 `release_body`，**推 tag 时 `inputs.*` 恒空，改由 `.github/scripts/extract_update_log.py` 从 `update_log_current` 自动抽取**，抽不到就 `::error::` + 退出 1、拒绝发版（26.10.05 曾因没有这道兜底而漏掉说明）。**别把这段逻辑改回「只取 `inputs.release_body`」**。发完仍要回读 Release 正文确认条数对得上。

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
- 版本号与是否强制更新由工作流从 `app/build.gradle` 读出、写进 Release 说明的机器可读元数据。
- 客户端更新检查按「Gitee → GitHub」读两边的 `releases/latest`（见 `util/UpdateRelease.kt`），**客户端已不再读 config.json**。
- 漏同步时用 `.github/workflows/relay-notify.yml` 重新通知（幂等，重复通知无害）。

## 附件名白名单

- **中转服务端有一份附件名白名单（`assets.include`），只同步里面列出的文件名**——新增发行渠道（如 26.10.11 的共存版 `app-coexist-*`）时必须同步把该渠道的 4 个附件名加进白名单并重启中转服务，否则该渠道在 Gitee 上取不到包、只能回落到 GitHub 源（26.10.11 首次发共存版就踩到过）。

## 老客户端迁移（一次性）

> 26.10.02 及更早的客户端只认 123pan 上那份 `config.json`。发版时若把 `emit_config_json` 打开，工作流会额外产出一份**给老客户端**的 `config.json`（`downloadUrl` = Gitee 上 32 位包的直链、`forceUpdate` 与本次一致）并挂到 Release，需人工把它传到 123pan。**它只用于渠道切换那一次**，新客户端不读它。

## 只想拿包、不想发版（build_only）

> 手工触发 `build-release.yml` 时勾选 `build_only`（26.10.17 新增）。它照常跑单测 + `assembleRelease` + 整理发行 APK + 生成 MD5 并上传 artifact `release-apks`，但**跳过**「计算发行 tag 与发布说明」「创建 GitHub Release」「Notify relay」等全部发行动作——不产生 Release、不占 tag（release immutability）、不同步 Gitee，适合传应用商店或自己装。

- 实现上由 job 级 `env.BUILD_ONLY` 归一化（**推 tag 时 `inputs.*` 恒空，必须显式判 `event_name`**），五处步骤条件前置 `env.BUILD_ONLY != 'true'`，并**比较字符串而非布尔**。
- 「tag 留空」不是等价做法：那条路仍会组装发布说明，`update_log_current` 抽不出条目时会 `::error::` 退出。细节见 `docs/review/fix-progress.md` §3.25。
