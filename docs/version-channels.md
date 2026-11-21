# 版本通道（稳定 / 测试 / 实验）

本文件是**三通道版本方案的唯一事实源**：版本号怎么编、发版怎么发、客户端怎么选、上报给后台什么字段、官网怎么过滤。
客户端、发版工作流、HK 中转/控制台、官网站点四处实现都必须与这里一致；改口径先改这份文件。

## 1. 为什么要分三通道

原来只有一条线：稳定版直接发，偶发「BETA 版」靠 `int betaVer` 手工改成 `-BETA<n>`，而 `versionCode` 只有
一位尾号（`YYMMDD × 10 + N`），尾号已经用于**同日重发**。需求变成三条通道后，同一个尾号要同时表达
「渠道等级」和「重发序号」，位数不够，必须扩位。

三个通道的口径：

| 通道 | 面向谁 | 内容质量 | 更新日志 |
| --- | --- | --- | --- |
| `stable` 稳定 | 所有用户 | 发版前跑过完整验证 | 必须写，进 Release 正文与官网 |
| `beta` 测试 | 愿意尝鲜的用户 | 功能基本完成，未做全量回归 | 必须写 |
| `exp` 实验 | 作者自己 / 极少数人 | **完全未测试**，可能起不来 | 可以空，写不出就不写 |

## 2. 版本号编码

```text
versionCode = YYMMDD × 100 + 渠道码 × 10 + 当日序号
渠道码：stable = 0，beta = 1，exp = 2
当日序号：stable 从 0 开始（0 可省略），beta / exp 从 1 开始
```

`versionName` 与 `tag` **完全相等**：

| 通道 | versionName = tag | versionCode |
| --- | --- | --- |
| 稳定首次 | `26.11.08` | `26110800` |
| 稳定同日重发 | `26.11.08.1` / `26.11.08.2` | `26110801` / `26110802` |
| 测试第 1 次 | `26.11.08-beta.1` | `26110811` |
| 测试第 2 次 | `26.11.08-beta.2` | `26110812` |
| 实验第 1 次 | `26.11.08-exp.1` | `26110821` |
| 实验第 9 次 | `26.11.08-exp.9` | `26110829` |

这套编码保证了三件必须成立的事：

1. **同日单调**：稳定 < 测试 < 实验（`…00` < `…11` < `…21`），客户端「`code > 当前` 才算有更新」这一条
   比较逻辑完全不用改。
2. **跨日单调**：日期在前，第二天任何通道都大于前一天任何通道。
3. **老用户无感升级**：新 `versionCode` 是 8 位（2611 万+），而线上已发布的最大是 7 位 `2611071`（261 万），
   `>` 天然成立，装着旧包的客户端能自己收到新方案的第一版。

`tag` 带 `-` 后缀是渠道标记的**唯一**依据——**不要**依赖 Release 的 `prerelease` 字段判定渠道：GitHub 会正确
忽略 prerelease，但 Gitee 的行为没实测过，中转服务也未必把该字段完整落库。

### 为什么必须有序号

仓库开着 **release immutability**：一个 tag 用过就永久不能再用。实验版一天可能发几次，没有序号必然撞 tag。

## 3. 构建参数（`app/build.gradle`）

唯一手写项是日期，渠道与序号走 Gradle 参数：

```bash
./gradlew.bat :app:assembleRelease                          # 稳定版，seq 默认 0
./gradlew.bat :app:assembleRelease -Pchannel=beta -Pseq=2    # 测试版第 2 次
./gradlew.bat :app:assembleRelease -Pchannel=exp  -Pseq=1    # 实验版
```

`versionName` / `versionCode` / `BuildConfig` 全部由这三个值派生，不手算。派生出：

| BuildConfig 字段 | 值 | 谁在用 |
| --- | --- | --- |
| `VERSION_NAME` | 带后缀全名（`26.11.08-beta.1`） | 关于页、日志 |
| `VERSION_NAME_BASE` | 只有日期（`26.11.08`） | 更新日志锚点、上报后台 |
| `UPDATE_CHANNEL` | `stable` / `beta` / `exp` | 上报后台、渠道判断 |
| `BETA` | `UPDATE_CHANNEL != "stable"` | 保持老语义，`ToolsUtil.isBeta()` 等三处引用不变 |

`verifyVersionConsistency` 的日志锚点用**日期**（`【26.11.08 本次更新】`），不带渠道后缀——三条通道可以共用
同一份当日日志标题。

## 4. 发版工作流（`.github/workflows/build-release.yml`）

- tag 格式：`^YY.MM.DD(\.\d)?(-(beta|exp)\.\d)?$`，且 `versionName` 必须与之完全相等。
- `verify-version` 由 tag 反推 `(渠道, 序号)`，再断言 `versionCode` 符合第 2 节公式；不再有「尾号 == tag 的 .N」
  这种按稳定版写死的断言。
- Release 的 `prerelease` = `渠道 != stable`。
- 附件名**保持不变**（`app-<abi>-release.apk` / `app-coexist-<abi>-release.apk`），所以 HK 中转服务的
  `assets.include` 白名单不用动。
- `exp` 通道允许抽不到 `update_log_current`：抽不到就写一句「实验版，无更新说明」，不 `::error::` 退出。

## 5. 客户端检查更新

渠道存在 `SharedPreferences` 的 `update_channel`；**没选过时默认 `beta`**（26.11.08 用户拍板：这个项目绝大多数时间在发测试包，默认稳定会让愿意帮忙测的人白等一个稳定版周期），**唯一例外是装着实验版包的人默认 `exp`**（否则他默认降到测试版后，实验版就再也送不到手上）；显式选过的值优先，存量脏值按 `stable` 归一。设置页「关于 → 更新通道」有三选一入口。

选版规则（两个源都试，Gitee 先、失败回落 GitHub）：

| 当前渠道 | 能收到哪些版本 |
| --- | --- |
| `stable` | 只有无后缀 tag |
| `beta` | 无后缀 tag + `-beta.*` |
| `exp` | 全部 |

实现上有两条路径：

1. **快路径**（stable）：`/releases/latest`，响应小。**必须校验返回的 tag 不带 `-` 后缀**——万一 Gitee 不过滤
   prerelease，直接用它就会把测试版推给稳定用户；校验失败即判定「该源不过滤」，改走列表。
2. **列表路径**（beta / exp）：`/releases?per_page=30`，按 tag 后缀过滤后取 `versionCode` 最大的一条。
   **不能依赖返回顺序**：Gitee 实测是升序、GitHub 是降序，两者相反。

「强制更新」不需要额外隔离逻辑：强制标记跟在**被选中的那条 release** 上，而选版已经按渠道过滤，测试版的强制
更新天然落不到稳定用户头上。

**降级是硬限制**：Android 不允许同包名降 `versionCode` 安装，所以「实验版 → 退回稳定版」不可能通过应用内更新
完成，只能卸载重装或手动下载安装。切通道的弹窗里要把这句写出来。

## 6. 上报契约（客户端 → HK 控制台 API）

`POST /terminal/telemetry/ping` 载荷新增一个字段，其余不变：

```json
{
  "version_name": "26.11.08",        // 只报日期，不带渠道后缀
  "version_code": 26110811,
  "is_beta": true,                    // 老字段，语义不变（channel != stable）
  "channel": "beta"                   // 新增：stable | beta | exp
}
```

- `version_name` **必须去后缀**：后台版本分布直接 `GROUP BY version_name`，带后缀会碎成
  `26.11.08-beta.1`、`-beta.2`、`-exp.1` 一堆条目，而且每发一次实验版就多一条。
- `channel` 沿用服务端既有的「**没报就保留原值**」策略（与 `is_beta` / `is_coexist` 同款 CASE 写法）：
  老客户端根本不发这个字段，当成空串覆盖会把已记下的渠道刷没。

服务端（`server/rebiliterminal_api.py`）：

- `installs` 表新增 `channel TEXT DEFAULT ''`，启动时幂等迁移（`ALTER TABLE … ADD COLUMN` 失败即已存在）。
- 统计接口返回渠道分布，控制台据此能看到三通道各多少台设备。

## 7. 官网

站点仓库 `bili-terminal-site` 有两处默认「最新 release 就是稳定版」，一旦发预发布就会把首页版本号和更新日志页
带成测试版，两处都必须**跳过 tag 带 `-` 后缀的 release**：

- `netlify/functions/release.js`（`/api/release`，首页下载清单与版本号靠它实时拉取）
- `tools/sync-release.py`（生成 `data/releases.json` / `changelog.html` / `index.html` 版本号 / `sitemap.xml`）

两处原本都直接读 `/releases/latest`（该接口无法过滤），需要改成「拉列表 → 过滤 → 按 `versionCode` 或日期取最大」。

## 8. HK 中转服务（`biliterminal-bridge`）

**不需要改。** `/opt/biliterminal-bridge/bridge.py:285` 建 Gitee 发行版时已经原样透传：

```python
"prerelease": bool(rel.get("prerelease")),
```

`select_assets` 只按附件名白名单取包、跟渠道无关；`gitee_latest_tag()` 只用于打日志。附件名不变就不用动
`assets.include`。
