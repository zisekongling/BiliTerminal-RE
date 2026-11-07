# AGENTS.md

本文件是**每次会话都该读的最小约束与索引**。细则按领域拆到 `docs/agents/`：**做到哪块就读哪份**，别在这里重复展开。

## 语言约定

**与用户交流、代码注释、文档一律中文。**

## 先读架构地图（按 § 查，别整份读）

`docs/architecture-map.md` 实测 **322,334 字节 / 2,052 行 ≈ 9.6 万 token**——整份读完就是十万 token 量级，一次任务里绝大部分是浪费。按下面这张表只读你需要的那几节（行号与 token 都是 26.11.06 实测，文件改版后行号会漂移，对不上就用 `grep -n "^## N\."` 重新定位）：

| 你要干什么 | 只读这几节 | token |
| --- | --- | --- |
| 第一次进仓库、要搞清分层 | §1 `10–175` + §10 `2045–2052` | ≈6.0k |
| 新增页面 / 改列表 / 改 UI 模板 | §8 `1483–1878` + §3 `223–292` | ≈13.8k |
| 改 API 解析 / 找某接口在哪个文件 | §9 `1879–1986` + §6 `353–663` | ≈14.5k |
| 改播放器 / 弹幕 / 下载 | §7.4 `702–783` + §7.27 `1085–1108` + §7.51 `1383–1396` + §7.54 `1419–1430`（动亮屏 / 顶栏再加 §7.52 `1397–1404`、§7.53 `1405–1418`，+1.5k） | ≈9.5k |
| 撞到某个具体坑 | 先 `grep -n "关键词" docs/architecture-map.md` 命中单个 `§7.x` 再读 | 1–3k |

三条铁律：

- **§7 是坑点库，不是必读章节**：`664–1482` 行、58 个 `### 7.x`、≈5.3 万 token，**永远不要通读**，用 grep 定位到那一节。
- **`§9` 末尾三节（7.56 收藏夹管理页 / 7.57 播放器内切 1080P / 7.59 外置卷 FileProvider，`1988–2044`，≈6.5k token）是误放在那里的坑点**，不是 API 映射——读 §9 读到 `1879–1986` 就够，那三节按坑点单独 grep（文内两处已标交叉引用）。
- **`docs/review/fix-progress.md` 平时只读 §1 + §2**（§1 `19–67` ≈4.0k、§2 `68–215` ≈14.0k）；`§3` 起 `216–1661` 行是历史流水（≈15 万 token），只有要查「某轮改动到底怎么改的」才按关键词 grep 进去。

其他参考：`docs/README.md`（文档总索引）、`bilibili-API/`（B 站接口文档快照，**197 份 / 5.5MB，按接口名 grep 单份，别通读**）、`docs/bilibili-api-notes.md`（接口与风控常识，改 API 前读）、`docs/FEATURES.md`（用户可见功能总表，≈3.4 万 token，按章 grep）、`docs/superpowers/specs/`（功能设计文档，只读与你这次改动相关的那一份）、`docs/tutorial-system-redesign.md`（教程系统重做中——**改教程前先读它**，全份仅 2.4k，可以整读）。**`docs/archive/` 里全是历史资料，记录的是当时、不是现状**，别照着它改代码。

## 分册索引（按"我正在做什么"读）

| 你正在做… | 读这份 |
| --- | --- |
| 写界面 / 列表 / 菜单 / 设置 / 弹窗 / 文案 | `docs/agents/dev-conventions.md` |
| 动清单、网络域名、备份、调试入口 | `docs/agents/security.md` |
| 加/改「不进公开仓库」的功能（`private/`、`OptionalFeatures` 门面、发版注入） | `docs/optional-features.md` |
| 构建 / 单测 / 装包 / 遇到构建怪错误 | `docs/agents/build.md` |
| 发版 / 更新日志 / Release 说明 | `docs/agents/release.md` |
| 准备提交、准备说"应该没问题" | `docs/agents/workflow.md` |
| 改完要验证、想上模拟器、判断这条测试该不该交给人工 | `docs/agents/testing.md` |
| 改 API 解析 / 播放器 / 弹幕 / 下载等易踩区 | `docs/agents/pitfalls.md` |
| 收尾（改文档、跑 markdownlint） | `docs/agents/docs.md` |

## 项目概况

- RE:哔哩终端（ReBiliClient），第三方 B 站**手表**安卓客户端，`main` 分支。
- 典型 vibe coding 产物：代码大量由 AI 生成。**改动前先读源码核实，注释和文档都可能是错的。**
- 版本号按 YY.MM.DD（`app/build.gradle`）。

## 架构（三条与直觉相反的）

1. **入口是 `BiliTerminal.kt`**（26.09.10 由 `BiliTerminal.java` 迁移而来）。原 `BiliTerminalApp.kt` 已于 26.10.02 整体删除——它从未被实例化，只因 `SplashActivity` 的 UETool 逻辑引用其静态方法而残留；那些方法与常量已移入 `BiliTerminal.kt` 伴生对象。全局 Context 取 `BiliTerminal.context`——它在 companion 里用 `@JvmField` 暴露成**静态字段**，所以 Java 侧仍是 `BiliTerminal.context` 的字段读法，Kotlin 侧按需 `!!`。
2. **没有 DI / Retrofit / ViewModel**——26.09.11 死代码清理后这已是**事实**而非"死依赖残留"：Hilt、Retrofit、kotlinx-serialization、Jetpack Navigation、protobuf-javalite、geetest sensebot、asynclayoutinflater、cardview、lifecycle-viewmodel-ktx 等**已全部从 `app/build.gradle` 移除**，`ksp` 与 `kotlin.plugin.serialization` 两个插件也一并去掉。原先 24 个空目录（`di/`、`network/`、`data/`、`ui/base` 等）已删除。新功能写进 `api/` + `activity/`，沿用静态方法 + `org.json`。
3. **只有一条链**：`activity/` → `api/`（全同步阻塞）→ `util/` → `model/`。导航由 `MenuActivity.btnNames` + `util/MenuConfig.kt` 决定。

## 每个改动都守

- 网络请求包在 `CenterThreadPool.run { }` 里。
- **小步提交**：改动切成能独立验证的小步（`assembleDebug` + 相关单测通过）再做下一步，别一次堆大量改动。**做完自己 commit + push 到 `main`**，不等催；commit message 与 co-author 格式见 `docs/agents/workflow.md`。
- **改完必须验证，不许只说"应该没问题"**，分三级（细则、预算、止损与交接单模板见 `docs/agents/testing.md`）：
  - **L0 静态**：每个改动都做——`assembleDebug` + 相关单测绿。只证明纯函数没写错，**不证明功能能用**。
  - **L1 设备可观测**：结论能用**文字/数字读出来**时做（日志原文、控件树文字与 bounds、落盘文件结构、HTTP 状态码），用 Debug 包 + `adb`/调试桥跑；**两轮上限，每轮 ≤10 分钟**，测不出来立刻止损写交接单，别反复试。
  - **L2 人工验收**：结论只能靠**看画面 / 听声音 / 判手感 / 主观取舍**时，**不要自己上模拟器硬试**——写交接单交给人，并在 `docs/review/fix-progress.md` 记「未验证：<项>」。
- **不许攒交接单**：需要人测的改动提交后立刻给交接单，别攒到发版前一次堆几十条。
- 用户可见文案一律进 `app/src/main/res/values/strings.xml`（写法与纯函数例外见 `docs/agents/dev-conventions.md`）。
- 不轻易引入新第三方库；**一切功能优先考虑手表端**，手机等设备只是顺便适配。
- **改完同步文档**：修 bug / 关待办 → `docs/review/fix-progress.md`；改架构 / 基建 → `docs/architecture-map.md`；新增用户可见功能 → `docs/FEATURES.md`；接口 / 风控结论 → `docs/bilibili-api-notes.md`；本文件描述的内容变了 → 改对应分册。改任何 `.md` 后跑 `npx --yes markdownlint-cli2 "AGENTS.md" "readme.md" "docs/**/*.md" "!docs/archive/**"`，**要求 0 错误**（规则、豁免与逐字素材示例见 `docs/agents/docs.md`）。

## 安全红线（常驻摘要，细则见 `docs/agents/security.md`）

- 新增 Activity 一律 `android:exported="false"`；Release 包仅 `SplashActivity` 与 `GetIntentActivity` 导出。
- 只走 https；明文域名白名单只放行 `bilibili.com` / `hdslb.com` / `bilivideo.com` / `afdiancdn.com`，加域名要写原因。
- `android:allowBackup` 保持 `false`，并保留主清单的 `tools:replace="android:allowBackup"`。
- 调试页只进 Debug 包，入口用编译期常量 `BuildConfig.DEBUG` 包住；Debug 包的外部可达组件必须挂 `android.permission.DUMP`。
- 调试开关必须"改完即生效"（统一走 `BiliTerminal.applyLogSwitches()`）。

## 私有功能仓库（不进开源）

本机还有一份**不公开**的私有功能源码仓库 `zisekongling/BiliTerminal-RE-private`（private）。
它不属于 GPL 发布内容，也不随公开仓库分发；公开仓库里只有反射门面和条件挂点。

- **本地位置**：公开仓库根目录下的 `private/`（已 gitignore）。没同步时构建出来就是公开版。
- **同步**：本机 `python build_tui.py --sync-private`（TUI 里 `P`，行式菜单 `[p]`）；CI 由
  `.github/workflows/build-release.yml` 的「注入可选私有功能源码」一步用 Secrets 拉取。
- **机制**（构建期注入点、门面规则、挂点写法）：`docs/optional-features.md`。
- **硬要求**：没有 `private/` 时公开仓库必须能独立编译、单测全绿；私有功能整个缺失也不许崩。

### 对 agent 的态度

1. 私有代码按「可以读、可以改、**绝不能外传**」对待：不要贴进公开仓库、issue、PR、评论、
   文档、回复或任何外部服务（含截图与日志）。
2. 公开侧只保留「门面 + 条件挂点」（如 `util/OptionalFeatures.kt`）；算法、交互流程、数据格式、
   文案细节一律留在 `private/`。**「换个说法提一句」同样禁止**——改名、拆片段、塞进注释/测试/
   截图都是变相公开。
3. 改私有功能就改私有仓库，与公开仓库分开提交；commit 格式与公开仓库一致
   （见 `docs/agents/workflow.md`），一条提交说清一件事。
4. 不许 `git add -f` 绕过 `.gitignore`；新增文件先判断它属于公开还是私有。
5. 私有功能的实测记录写私有仓库的 `README.md`，**不写**公开的 `docs/review/fix-progress.md`。
6. 拿不准某个改动该进哪一边时，**先问用户**，不要自行决定。

### 私有仓库的规矩

- 仓库根目录就是 `private/` 的内容：`manifest/`、`res/`、`src/main/java/…`、`keep.pro`、`README.md`。
- **只放源码**：截图、日志、APK、临时产物一律不进（放 `outputs/`，已 gitignore）。
- 默认分支 `main`。本机 `private/` 保留 `.git` 可以继续提交；CI 检出后立刻删 `.git`
  （token 不许留在磁盘上）。
- 同步只需要只读 PAT（细粒度，Contents: Read）；写权限只在用户自己的机器上。
- 硬约束：包名固定 `com.RobinNotBad.BiliClient.feature`；Kotlin 不自动解析父包的 `R`，
  用到资源必须显式 `import com.RobinNotBad.BiliClient.R`。
- 私有 Activity 同样遵守安全红线：`android:exported="false"` + `android:screenOrientation="locked"`。
- 私有类只被反射调用，R8 保留规则写在私有仓库的 `keep.pro` 里。

## 构建

```bash
./gradlew.bat :app:assembleDebug                 # 编译验证（无 CI，以此为准；两个 flavor 都编）
./gradlew.bat :app:testStandardDebugUnitTest     # 纯 JVM 单测（普通版）
./gradlew.bat :app:testCoexistDebugUnitTest      # 纯 JVM 单测（共存版，同源码）
./gradlew.bat :app:assembleRelease               # 正式包（R8 + ABI 分包，两个 flavor 都出）
```

单测任务名必须带 flavor 前缀（`testDebugUnitTest` 已不存在）；交互式构建 / 装包用 `python build_tui.py`；环境坑、缓存回放、增量编译坏掉、量体积与 APK 构成见 `docs/agents/build.md`。

## 原生库

`app/libs/{arm64-v8a,armeabi-v7a,x86}/` 是已提交的 `.so`，日常别动；重建需 NDK r21e（见 `rebuild_all.sh` 等脚本）。
