---
name: rebili-version-release
description: >-
  在 RE:哔哩终端（ReBiliClient）仓库发版：递增版本号、把旧“本次更新”移入历史更新日志、
  写入新版本日志、用 Gradle 构建签名发行 APK，并刷新部署用 config.json。当用户要求
  “发版/出包/更新日志/构建发行版/把更新日志写入历史”等属于本仓库的操作时使用本技能。
---

# RE:哔哩终端（ReBiliClient）发版工作流

把一次“写更新日志 + 构建发行版”的完整流程固化为可复用步骤。改动前先读仓库根
`AGENTS.md`（语言/架构/构建约定），不要盲信现有实现。

## 术语与关键位置

| 对象 | 位置 | 说明 |
| --- | --- | --- |
| 版本号 | `app/build.gradle` → `defaultConfig` | `versionName "YY.MM.DD"`，`versionCode` 为其数字编码 |
| 本次更新日志 | `app/src/main/res/values/strings.xml` → `update_log_current` | 关于页“本次更新 (版本名)”展示的内容 |
| 历史更新日志 | 同上 → `update_history_log` | 按 `## YYYY-MM-DD` 日期分组，新日期放数组最前（最上方） |
| 遗留完整日志 | 同上 → `update_log_items` | 老入口用，非必要时**不要**改动 |
| 部署更新配置 | 项目根 `config.json`（已加入 `.gitignore`，未跟踪） | `UpdateManager` 远程拉取，含版本号/公告/下载链接 |
| 发行包 | `app/build/outputs/apk/release/*-release.apk` | assembleRelease 产物（ABI 分包） |

## 标准流程

### 0. 盘点自上个发布版以来的改动
先确定“上个已发布版本”对应的提交，用它作为日志内容来源：
`git log --oneline <上个发版commit>..HEAD`（可用上次发布的版本名哈希/说明定位）。
据此判断本版本日志该写哪些功能/安全/修复条目，并按实际代码改动撰写，不要凭空编造。

### 1. 处理历史更新日志（把旧的本次更新归档）
`update_log_current` 记录的是**上一个已发布版本**的内容。发新版本前把它移入历史：

- 在 `update_history_log` 数组**最前面**插入一组：
  - `<item>## YYYY-MM-DD</item>`（日期取旧版本发布日）
  - 之后接旧 `update_log_current` 里的条目（去掉原来的 `【xx 本次更新】` 首行，日期已由 `##` 头表达；空行可省略，历史页解析会跳过空行）
- 保持数组内其它旧组不动，新组放最前以便历史页优先展示最新日期。

### 2. 写入新的“本次更新”日志
- **打包之前的强制步骤：先把版本号设为当天日期**（确认“今天”后再填，再进入第 3 步打包）：
  - `versionName` = 今天的 `YY.MM.DD`；
  - `versionCode` = 日期数字后补一个 `0`（即 `YYMMDD0`）。
  - 示例：`2026-09-07` → `versionName "26.09.07"`、`versionCode 2609070`。
  - 用 `Get-Date` / 系统日期确认“今天”，不要沿用旧日期；改完确认 `app/build.gradle` 已落到当天值，**再开始打包**。
- 把 `update_log_current` 整体替换为新版内容：
  - 首行 `<item>【YY.MM.DD 本次更新】</item>`
  - 之后按条写功能/修复，用**中文**、加编号、条目语气与既有日志一致。
- 若仓库里 `description` 有 `strings.xml` 注释提示排序规则（新功能在上/修复在下），按提示组织。

### 3. 构建发行版
```bash
./gradlew.bat :app:assembleRelease   # Windows；Linux/WSL 需覆盖 gradle.properties 里的 java.home
```
- 成功标记：`BUILD SUCCESSFUL`；产物在 `app/build/outputs/apk/release/`。
- 注意（在受限沙箱/agent 环境下）：Gradle 要写 `C:\Users\<user>\.gradle` 与 SDK 等**工作区之外**的路径，首次可能因写锁/权限失败。若失败需以不受限文件权限重跑同一命令，不要改路径绕过。
- `app/build.gradle` 里 `copyApkToDesktop`（含 `adb install`）已注释，**不会**随 assembleRelease 自动复制/安装；需要时单独跑 `./gradlew.bat copyApkToDesktop`。

### 4. 刷新部署用 config.json（如需同步线上公告）
`config.json` 字段：`versionCode`/`versionName`/`description`/`downloadUrl`/`forceUpdate`。
- `description` 要镜像新版本 `update_log_current` 内容（带 `\n` 换行、JSON 需转义双引号）。
- `versionCode`/`versionName` 与 `app/build.gradle` 一致。
- `downloadUrl`：不是“文本”，仅在把新发行包上传到对应网盘后把末尾文件名递增（如 `22.apk`→`23.apk`），不要凭空改成不存在的链接。
- 改完用 `Get-Content -Raw config.json | ConvertFrom-Json`（或等价 JSON 校验）确认可解析。
- 该文件已在 `.gitignore`，改动不会进入版本控制，属部署时单独上传的内容。

### 5. 校验
- strings.xml 保持 XML 合法（本次只改数组文本）。
- 若改了配置/文案类字段，至少确认 JSON/XML 可解析；改动解析逻辑时才需要跑 `:app:testDebugUnitTest`。
- 向用户汇报：改了哪些文件、发行 APK 的路径/大小、config.json 是否需补传 APK/更新链接。

## 约定与坑
- 一律**中文**文案与注释；遗留页文案硬编码、不改 `strings.xml`（设置页为字符串驱动例外，用 `desc_*`）。
- `update_log_items`、`versionCode` 尾码规律、`config.json` 下载链接地址这类与既有约定/外部资源强相关的内容，不确定就先确认再改。
- 用 `strings.xml` 引号注意：XML 文本里直引号合法，但写进 `config.json` 的 JSON 串需转义成 `\"`；中文全角引号无需转义。
