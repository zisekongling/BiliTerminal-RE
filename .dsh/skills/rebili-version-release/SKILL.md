---
name: rebili-version-release
description: >-
  在 RE:哔哩终端（ReBiliClient）仓库发版：递增版本号、把旧“本次更新”移入历史更新日志、
  写入新版本日志、用 Gradle 构建签名发行 APK，由 CI 通知中转服务把发行版同步到 Gitee、产出下载直链，
  最后把新版本同步到官网静态部分（更新日志页、下载兜底链接、JSON-LD 版本号、sitemap）。
  当用户要求“发版/出包/更新日志/构建发行版/把更新日志写入历史/同步官网”等属于本仓库的操作时使用本技能。
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
| 发行包 | `app/build/outputs/apk/release/*-release.apk` | CI 里由「整理发行 APK」步骤汇总（26.10.11 起；Gradle 原始产物按 flavor 分目录，普通版回历史名 `app-<abi>-release.apk`，共存版 `app-coexist-<abi>-release.apk`） |
| 发行渠道 | GitHub Release（`.github/workflows/build-release.yml`）+ Gitee 发行版 | 客户端更新检查按「Gitee → GitHub」顺序读这两个仓库的 `releases/latest` |
| 版本元数据 | Release 说明末尾的 `<!-- update: versionCode=… versionName=… forceUpdate=… -->` | 发版工作流自动从 `app/build.gradle` 读出并写入，**不再有 config.json** |

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

> **🔴 强制：更新日志草案先交用户改，改完再落盘（26.11.17 用户要求，此后每版都照做）。**
>
> 顺序固定为：① 按 §0 的提交清单写一版中文编号草案 → ② **整段贴在对话里**交给用户过目
> （此时**不写** `strings.xml`、不改版本号、不建 tag、不构建）→ ③ 用户把改后的整段发回来，
> **以他那一版为准**逐字落进 `update_log_current` → ④ 才进 §3 / §4。
>
> - 用户没回之前**不要往下走**任何一步（一次改一句几秒钟，发出去再改要重发一整版——tag 开
>   release immutability 后不可复用，代价是一整个 tag 与一轮 CI）。
> - 三个通道一律适用；`exp` 即使打算不写日志，也要先问一句「这版日志留空行不行」。
> - 用户可能在草案里删条目、改措辞、调顺序——都按他的来，不要"帮"他把删掉的条目加回去；
>   他改完的文本就是 `update_log_current` 的正文（首行锚点 `【YY.MM.DD 本次更新】` 由你补）。

- **打包之前的强制步骤：先把版本日期设为当天**（确认“今天”后再填，再进入第 3 步打包）：
  - 三通道口径（见 `docs/version-channels.md` §2–§3）：`app/build.gradle` 里**只手写** `def appVersionDate = "YY.MM.DD"`；
    `versionName` / `versionCode` 全部由它 + Gradle 参数派生，**不要手写**。
    `versionCode = YYMMDD × 100 + 渠道码 × 10 + 序号`，渠道码 stable=0 / beta=1 / exp=2。
  - 例（今天 26.11.17）：`-Pchannel=beta -Pseq=1` → `26.11.17-beta.1` / `26111711`。
  - 用 `Get-Date` / 系统日期确认“今天”，不要沿用旧日期；改完确认 `appVersionDate` 已落到当天值，**再开始打包**。
- 把 `update_log_current` 整体替换为新版内容：
  - 首行 `<item>【YY.MM.DD 本次更新】</item>`
  - 之后按条写功能/修复，用**中文**、加编号、条目语气与既有日志一致。
- 若仓库里 `description` 有 `strings.xml` 注释提示排序规则（新功能在上/修复在下），按提示组织。

### 3. 构建发行版

```bash
./gradlew.bat :app:assembleRelease   # Windows；Linux/WSL 需覆盖 gradle.properties 里的 java.home
```

- 成功标记：`BUILD SUCCESSFUL`。**26.10.11 起两个 flavor 都会构建**，Gradle 原始产物分别在 `app/build/outputs/apk/standard/release/` 与 `app/build/outputs/apk/coexist/release/`（文件名带 flavor，如 `app-standard-arm64-v8a-release.apk` / `app-coexist-arm64-v8a-release.apk`）；**本地想凑出 CI 那种发布目录，要自己按 CI 的「整理发行 APK」步骤把普通版改名成 `app-<abi>-release.apk` 再放到 `app/build/outputs/apk/release/`**（名字不能变——老客户端按精确文件名取包，详见 `docs/architecture-map.md` §7.37）。
- 注意（在受限沙箱/agent 环境下）：Gradle 要写 `C:\Users\<user>\.gradle` 与 SDK 等**工作区之外**的路径，首次可能因写锁/权限失败。若失败需以不受限文件权限重跑同一命令，不要改路径绕过。
- `app/build.gradle` 里 `copyApkToDesktop`（含 `adb install`）已注释，**不会**随 assembleRelease 自动复制/安装；需要时单独跑 `./gradlew.bat copyApkToDesktop`。

### 4. 发版（GitHub Release + 通知中转同步 Gitee）

> **⚠️ 硬要求：Release 说明里必须有完整的本次更新说明，不许省略。**
> 发布说明要包含 `strings.xml` 中 `update_log_current` 的**全部条目**（去掉 `<item>` 标签，
> 保留「【YY.MM.DD 本次更新】」标题与编号）。**只留 `### APK 校验值（MD5）` 与
> `<!-- update: … -->` 机器元数据不算合格**；拿 GitHub 自动生成的「Full Changelog」commits
> 摘要顶替也不算——用户点进 Release 是要看这版改了什么。
>
> **成因**：`push` tag 这条路径下 `inputs.*` 恒为空，`release_body` 是空的。
> （26.10.05 就是这么漏掉的。）
>
> **已由工作流保证（26.10.05 后）**：`build-release.yml` 的「计算发行 tag 与发布说明」步骤里，
> 手工触发用输入的 `release_body`；**推 tag 时由 `.github/scripts/extract_update_log.py`
> 自动从 `update_log_current` 抽取**（每行一条、反转义 XML 实体、跳过空条目，并强制 UTF-8 输出）。
> 抽不到任何条目就打印 `::error::` 并以退出码 1 结束——**宁可让发版红掉，也不发出没有更新说明的
> Release**。抽到时 `has_body=true`，GitHub 不再自动补 commits 摘要。
>
> ⚠️ **别把这段逻辑改回「body 只取 `inputs.release_body`」**：那样推 tag 又会退化成只有 MD5 表。
> 手工触发想覆盖文案时，填 `release_body` 即可（它优先于自动抽取）。
>
> 无论走哪条路，发布完都要回读 Release 正文确认（见 §5）。

**优先走 CI**：推一个 tag（或手工触发 `构建发行版并创建 Release` 工作流并填 tag）即可，
工作流会依次完成：

1. 跑单测（`:app:testStandardDebugUnitTest`，两个 flavor 共用同一份 `app/src/test`）→ `:app:assembleRelease` → 「整理发行 APK」（普通版改名回 `app-<abi>-release.apk`、共存版搬 `app-coexist-*` 进 `app/build/outputs/apk/release/`）→ 生成各 APK 的 MD5；
2. 组装 **Release 说明正文**：更新日志在前（手工触发取 `release_body`，推 tag 由
   `.github/scripts/extract_update_log.py` 从 `strings.xml` 的 `update_log_current` 自动抽，
   见上方硬要求），接着是 MD5 表，末尾是从 `app/build.gradle` 读出的
   `versionCode`/`versionName` 与「是否强制更新」构成的机器可读元数据
   `<!-- update: versionCode=… versionName=… forceUpdate=… -->`
   （手工触发时用 `force_update` 输入控制，默认 `false`）——**客户端更新检查就靠这段**；
3. 生成 `release-links.txt`（Gitee 与 GitHub 两侧直链）与（按需）老客户端用的 `config.json`，
   随 APK 一起上传到 **GitHub Release**；
4. Release 发完后**通知中转服务**（末尾的 Notify relay 步骤）：地址与密钥取仓库 Secrets
   `RELAY_URL` / `RELAY_SECRET`，请求体用 HMAC-SHA256 签名；由中转服务自己去 GitHub
   拉附件并同步到 Gitee `zisekongling/bili-terminal-re`（幂等，同步后 Gitee 只留最近若干个发行版）。
   **Action 侧绝不直接访问 Gitee**（网络不通），也不要把地址/密钥写进代码或日志。
   ✅ **26.10.11 实测：`RELAY_URL` / `RELAY_SECRET` 已配好**，Notify relay 步骤 success，
   Gitee 发行版在 GitHub 发布后约 1 分钟内出现（正文一字不差）。
   （26.10.08 换主那阵子这两个 Secret 曾丢失，工作流当时改成「缺 Secret 就 `::warning::` 跳过」而不是
   `exit 1`；万一将来又缺，就只剩手工补或重发通知，且**发版日志里不会红**——记得回读 Gitee 确认。）
   ⚠️ **中转服务端只同步白名单里的附件名**（`assets.include`，26.10.11 实测为 5 个历史文件名）：
   **新增发行渠道 / 改包名前缀后，必须同步把该渠道的 4 个附件名加进白名单并重启中转服务**，
   否则该渠道在 Gitee 上没有包——客户端会按 `UpdateRelease.pickApkUrl` 的精确名匹配失败、
   回落到 GitHub 源（能更新，但国内直连慢）。26.10.11 首次发共存版就踩到这条：Gitee 上只有普通版
   4 个包，手动补传后正常。

**只要签名包、不发版（26.10.17 起，勾 `build_only`）**：手工触发
`构建发行版并创建 Release` 并**勾选 `build_only`**——照常跑单测、`:app:assembleRelease`、
整理发行 APK、生成 MD5 并上传 artifact `release-apks`，但**跳过**「计算发行 tag 与发布说明」
「创建 GitHub Release」「Notify relay」等全部发行动作：**不产生 Release**（因此不进
`releases/latest`、不会被中转同步到 Gitee）、**不占用 tag**（release immutability 下这点很关键）。
适用场景：只想拿一份正式签名的包去传应用商店 / 自己装。产物从 Actions run 页面的
**Artifacts → `release-apks`** 下载。

> ⚠️ **别用「tag 留空」代替 `build_only`**：那条路仍会组装发布说明，`update_log_current`
> 抽不出条目时会 `::error::` 退出（见上方硬要求）——为拿一个包而被迫先写好当版更新日志并不合理。
> 实现与取舍（为什么比较字符串而不是布尔、为什么连「计算发行 tag」这步一起跳过）见
> `docs/review/fix-progress.md` §3.25。

- 客户端更新源：Gitee 发行版优先，失败回落 GitHub 发行版；两处都是公开仓库，读 release 不需要 token。
- Gitee 直链格式：`https://gitee.com/zisekongling/bili-terminal-re/releases/download/<tag>/<文件名>`。
- 漏同步 / 想重跑同步：触发 `.github/workflows/relay-notify.yml` 并填 tag（只重发通知，不重新构建）；
  若只是某个渠道的包漏了（白名单问题），直接在中转服务器上跑它的手动同步（`python3 bridge.py --once --tag <tag>`，
  幂等、只补缺的附件、会按 `md5sums.txt` 校验）最快。
- ⚠️ **本仓库开着 release immutability**：一个 tag 只要被不可变 release 用过，**就永久不能再建**，
  即使把 release 和 tag 都删掉也一样（报 `tag_name was used by an immutable release` +
  `Cannot create ref due to creations being restricted`）。所以**重发同一个版本必须换 tag**
  （例如 `26.10.03` 用掉了就用 `26.10.03.1`）；另注意不可变 release 的**附件也不能增删改**，
  发完就没法补传，只能靠 `relay-notify.yml` 重发通知让中转去补。
- 若需本地出包（不开 CI）：按 §3 构建后手工触发工作流上传；**不要**在本地直接调 Gitee API。
  **只是想要一份签名的包（传商店 / 自装）就勾 `build_only`**，别为它写当版更新日志，见上方那段。

### 5. 校验

- strings.xml 保持 XML 合法（本次只改数组文本）。
- 版本号改过就必须让 `:app:verifyVersionConsistency` 通过（校验 build.gradle 与 strings.xml 更新日志锚点）。
- **Release 建好后必须回读它的说明正文**（`https://api.github.com/repos/zisekongling/BiliTerminal-RE/releases/latest` 的 `body`），确认 `update_log_current` 的全部条目都在里面。若正文只有 `### APK 校验值（MD5）` + `<!-- update: … -->` + `**Full Changelog**`，就是省略了更新说明 —— 见 §4 的硬要求，必须补上。**别只看 `conclusion=success` 就收工**：工作流全绿也不代表说明写了。
- **还要回读 Gitee 的附件清单**（`/repos/zisekongling/bili-terminal-re/releases/tags/<tag>` 的 `assets`）：**每个发行渠道都要有 4 个包 + `md5sums.txt`**。Gitee 上少包 = 中转服务端白名单没更新（见 §4），客户端会回落 GitHub 源。26.10.11 就是回读时发现只有普通版、共存版一个都没有。
- 向用户汇报：改了哪些文件、发行 APK 的路径/大小、**Release 说明是否含完整更新日志**、CI 里 Gitee 同步是否成功（**含每个渠道的附件是否齐**）、`release-links.txt` 里的直链。

### 6. 同步官网静态部分

发版只完成了一半：官网还有一批**静态**内容不会自己变，要同步过去。

| 要同步的 | 为什么 |
| --- | --- |
| `changelog.html` / `changelog-legacy.html` | 预生成的更新日志页，不会自己更新 |
| `index.html` 的静态兜底（版本号 + 10 条 Gitee 直链） | `/api/release` 挂掉时页面退化为它 |
| `index.html` 的 JSON-LD `softwareVersion` | 搜索引擎读的是这个静态值 |
| `sitemap.xml` 的 `lastmod` | 新鲜度信号（Bing 网站管理员指南第 3/19 条） |

> 首页的**下载清单与版本号本身**由 `/api/release` 实时拉取，发版后自动更新，
> **不需要**人工干预。上表是它之外的静态部分。

**站点仓库**：`D:\Users\ASUS\Desktop\DEVELOP\bili-terminal-site`（与客户端仓库同级，独立仓库）

```bash
cd D:/Users/ASUS/Desktop/DEVELOP/bili-terminal-site
python tools/sync-release.py          # 同步（有变更才写文件；无变更则明确报告）
python tools/sync-release.py --check  # 只检查：有变更则退出码 1
python tools/sync-release.py --dry-run # 只打印将做的改动
git add -A && git commit -m "chore: 同步发行版 <tag>" && git push
```

推送后 Netlify 自动部署。脚本会从 **Gitee 与 GitHub 两个源**各取一次 Release，
按「日期 + tag 序号」取较新者 —— 因为中转服务同步到 Gitee 有约 1 分钟延迟，
刚发完版只信 Gitee 会同步成旧版本。取到后依次：

1. 把新版本追加进 `data/releases.json`（更新日志页的数据源）
2. 替换 `index.html` 里 14 处版本号（10 处 Gitee 直链 + 1 处 JSON-LD + 3 处展示）
3. 更新 `sitemap.xml` 的 `lastmod`
4. 重跑 `tools/gen-pages.py` 重新生成更新日志页

**顺序**：本步要在 §4 之后做（Gitee 上有新版本才同步得准）。脚本已做双源比对，
即使中转还没同步完也不会同步成旧版本，但**先确认 §5 的 Gitee 附件回读通过**再跑更稳。

**已自动化**：站点仓库有 `.github/workflows/sync-release.yml`，
**每天定时**（北京时间 02:20）自动跑同一套同步并提交，也可在 Actions 页面手动触发。
所以本步是**兜底 + 想立刻生效时的手动通道**：CI 没跑成、或用户马上要看新版页面时用。

**验证**：同步后回读 `https://biliterminal.zsapp.asia/changelog.html`，
确认新版本条目在**最上面**，且 `index.html` 的 `softwareVersion` 已更新。

## 约定与坑

- 一律**中文**文案与注释；遗留页文案硬编码、不改 `strings.xml`（设置页为字符串驱动例外，用 `desc_*`）。
- `update_log_items`、`versionCode` 尾码规律（`YYMMDD0`；同一天发第二个包时尾位递增为 `YYMMDD1`）、Gitee 直链这类与既有约定/外部资源强相关的内容，不确定就先确认再改。
- **新客户端不再读 config.json**：更新检查已改为读发行版本身，版本元数据由发版工作流写进 Release 说明，少一处人工同步的远端文件。
- 例外：**渠道切换那一次**（把 26.10.02 及更早、只认 123pan config.json 的老客户端带过来）需要在触发发版时把 `emit_config_json` 打开，工作流会额外产出一份 `config.json`（`downloadUrl` = Gitee 上 **32 位包**直链、`forceUpdate` 同本次）挂到 Release —— 还需**人工把它传到 123pan**。之后不再产出。
- `:app:verifyVersionConsistency` 只校验 build.gradle 与 strings.xml 更新日志锚点两处；改了版本号就要同步那个锚点，否则 CI 会红。
- Gitee 会在发行版里**自动附带** `{tag}.zip` / `{tag}.tar.gz` 两个源码归档（tag 落在 `master` 上产生），
  客户端选包必须按精确文件名匹配（普通版 `app-<abi>-release.apk`、共存版 `app-coexist-<abi>-release.apk`，见 `util/UpdateRelease.apkNamePrefix`），不能用"第一个附件"。
- **官网是另一个独立仓库**（`D:\Users\ASUS\Desktop\DEVELOP\bili-terminal-site`），不在本仓库内，
  发版后要同步它的静态部分，见 §6。官网页面内容由 `tools/gen-pages.py` 生成 ——
  **直接手改生成的 HTML 会在下次生成时被覆盖**，要改内容就改脚本里的数据再重跑。
