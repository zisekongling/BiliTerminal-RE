# 可选私有功能（`private/`）

有些功能**不希望公开源码**（用户明确要求过），但发行包必须有。这个仓库的做法是：
实现放私有仓库，构建时才注入；公开仓库里只留反射门面和几处条件挂点。

原则只有一条：**公开仓库编译出来的 APK 里不含这些功能的任何实现**。`private/` 目录不存在时，
工程行为与加入本机制之前完全一致（同一份代码、同一套单测、同一个 APK）。

## 目录结构

`private/` 在**仓库根目录**，已在 `.gitignore` 里整目录忽略（`/private/`，带原因注释）。

```text
private/
├── manifest/AndroidManifest.xml     # 并进合并清单：私有权限 + 私有 Activity 注册
├── src/main/java/<包路径>/...        # 并进主源集的 Kotlin/Java
├── res/...                          # 并进资源（string/layout/...）
└── keep.pro                         # 并进 release 的 R8 规则
```

## `app/build.gradle` 里的注入点

四处，全部以 `hasPrivateFeature` 为开关（该变量 = `private/src/main/java` 是否存在）：

1. `sourceSets.main.java.srcDirs += private/src/main/java`；
2. `sourceSets.main.res.srcDirs += private/res`；
3. `release` 的 `proguardFile private/keep.pro`（私有类只被反射引用，R8 看不见，必须 keep）；
4. `androidComponents.onVariants { variant.sources.manifests.addStaticManifestFile(private/manifest/AndroidManifest.xml) }`。

另外 `defaultConfig` 里还有一组**构建期凭证**注入（同样只在有 `private/` 时定义）：

5. `-PKAZUMI_APPID` / `-PKAZUMI_KEY`（或同名环境变量）→ `BuildConfig.BGM_MIRROR_APP_ID` /
   `BGM_MIRROR_SECRET`，给私有侧访问第三方数据代理用。**仓库里永远是空串**；空串时私有侧
   自动关闭代理、退回旧链路，公开构建连这两个常量都不会出现。下文「发版注入」有 CI 的做法。
6. `-PDANDANAPI_APPID` / `-PDANDANAPI_KEY`（或同名环境变量）→ `BuildConfig.DANDANAPI_APP_ID` /
   `DANDANAPI_SECRET`，给私有侧的第三方弹幕源用；空串时换源播放照旧、只是没有第三方弹幕。

注意清单那处的 API 名字是 **`addStaticManifestFile`**（传文件路径），不是 `addStaticSourceDirectory`
（那是 java/res 的 API，传目录）；AGP 8.5.2 实测如此。

## 公共侧的反射门面

`app/src/main/java/com/RobinNotBad/BiliClient/util/OptionalFeatures.kt`：

- `Class.forName("com.RobinNotBad.BiliClient.feature.FeatureHolder")`，找不到就当功能不存在；
- 每个方法都吞异常并返回 `null` / no-op，**私有代码坏掉绝不能影响公共功能**；
- 只转发，不实现。

私有侧的 `FeatureHolder` 有两个硬约束（改它之前先读注释）：

1. 只用 `@JvmStatic` 静态方法暴露，参数/返回值只用框架类型或**公共侧已有的模型类**
   （`Context` / `View` / `String` / `long` / `MessageCard`…），不能出现私有侧独有的类型；
2. 对自己其他类一律 `Class.forName` 反射调用 —— 这样私有仓库里的文件可以分步写、缺一个也能编译。

## 现有挂点（新增可选功能时照这个模式加）

| 挂点 | 位置 | 公共侧行为 |
| --- | --- | --- |
| 菜单项 | `util/MenuConfig.kt` 的 `OPTIONAL_ITEMS` + `activity/MenuActivity.kt` 的 companion `init` | `OPTIONAL_ITEMS` 为空列表，菜单仍是原有的 14 项 |
| 菜单设置页 | 复用 `MenuActivity.btnNames`（`SettingMenuActivity` 从这里取显示名） | 无需改动 |
| 启动页跳转 | `SplashActivity` 从 `btnNames` 取 Class | 无需改动 |
| 视频详情页动作区 | `fragment_video_info.xml` 的 `@id/video_actions` + `VideoInfoFragment` 里的 `OptionalFeatures.attachDetailActionRow(...)` | 空操作，不多出按钮 |
| 评论长按面板 | `adapter/ReplyAdapter.kt` 的 `showManageMenu`：`replyReportLabel(context)` 给文案、`replyReport(...)` 执行 | 文案为 null → **不加这一项**；别人的评论只剩「复制文字」，走「只剩一项就直接复制」的老捷径 |
| 消息卡片 | `api/MessageApi.java` 两个消息流的 `default` 分支（`fillOptionalMessage`）＋ `adapter/message/NoticeHolder.kt` 的点击（`openOptionalMessage`） | `false` → 照旧显示「无法识别这个类别」/ 不处理点击 |
| 播放页接管片源 | `activity/player/PlayerActivity.kt`：intent 带 `third_party_pending=true` 时（`url` 可为空）停在载入中，再由 `beginPendingThirdPartyResolve()` 调 `OptionalFeatures.requestThirdPartyResolve(...)`，回调 `onResolved(url, referer)` / `onFailed()` | 返回 `false`（没有私有功能）→ 弹 `msg_third_party_no_source` 并结束播放页 |
| 播放页第三方弹幕 | `PlayerActivity.getExtras()` 读 `third_party_danmaku` 等 extra；不下载 B 站弹幕，改为调 `OptionalFeatures.attachThirdPartyDanmaku(...)`，私有侧后台准备好 XML 后回调 `PlayerActivity.applyThirdPartyDanmaku(file)` | 返回 `false` 或无私有功能 → `hasDanmaku=false`，播放照常、只是没有弹幕 |
| 启动期静默预热 | `BiliTerminal.onCreate` 的 `if (context == null)` 块内（`CookieRefreshScheduler.start()` 之后）调一次 `OptionalFeatures.warmUpOptionalData()` | 空操作；有私有功能时只是「叫一声」就返回，预热什么、是否节流全由私有侧决定 |

老用户的 `menu_enabled` 里没有可选 key 时，`MenuConfig.loadEnabled` 会走一次迁移把它补进来
（`resolveEnabledList` 对可选项的处理与固定项一致：缺失即补到末尾）。

## 本地开发

私有源码放在独立的 GitHub **私有仓库**里，仓库根目录就是 `private/` 的内容
（`manifest/` · `res/` · `src/` · `keep.pro`）。本机用 `build_tui.py` 同步，和 CI 的行为一致：

```bash
# 第一次：把私有仓库坐标写进 local.properties（这个文件已 gitignore，和签名配置同一个文件）
python build_tui.py --private-repo <owner/repo> --private-token <只读 PAT> [--private-branch main]

# 之后：同步到 private/（已有内容时会先问要不要覆盖），再照常构建
python build_tui.py --sync-private
python build_tui.py --sync-private --private-force   # 不问了，直接按私有仓库最新内容覆盖
./gradlew.bat :app:assembleDebug
```

TUI 里对应的是 `P 同步私有功能源码` 这一项（行式菜单里是 `[p]`），菜单和构建横幅都会显示
`私有功能：已就位 / 未获取`——没同步时构建出来的就是公开版，不会报错。

配置键与 CI 完全同名，读的顺序是**环境变量优先、其次 `local.properties`**：

| 键 | 说明 |
| --- | --- |
| `PRIVATE_FEATURE_REPO` | `owner/repo`（也接受完整 https / `git@` 写法，会被归一化） |
| `PRIVATE_FEATURE_TOKEN` | 只读该私有仓库的细粒度 PAT（Contents: Read）；公开仓库可留空 |
| `PRIVATE_FEATURE_BRANCH` | 可选，默认取私有仓库默认分支 |

同步做的事：`git clone --depth 1` 到 `private.tmp/` → 整体替换 `private/`；日志里的 clone 地址会把
token 抹成 `***`。**只有带了 token 才会删 `.git/`**（CI 那种场景，凭据不能留在磁盘上）；本机靠
凭据管理器鉴权时保留 `.git/`，于是 `private/` 还是一个能继续 commit / push 的仓库。拉下来的目录
里必须有 `src/main/java`，否则视为结构不对并报错（`app/build.gradle` 的 `hasPrivateFeature`
就是按这个路径判断的）。

私有仓库本身在 `zisekongling/BiliTerminal-RE-private`（private），根目录就是 `private/` 的内容。

私有源码并进的是 `:app` 模块，所以**可以直接用 app 的 `R`、`activity.base.*`、`util.*`、`api.*`**，
不需要单独建模块，也不受 library 可见性限制。注意私有代码的包名是
`com.RobinNotBad.BiliClient.feature`，**Kotlin 不会自动解析父包，必须显式
`import com.RobinNotBad.BiliClient.R`**。

`private/` 里只放源码：截图、日志、临时产物一律放 `outputs/`（已 gitignore）。

## 发版注入

`.github/workflows/build-release.yml` 的 `注入可选私有功能源码` 一步：

- 配了 `Secrets.PRIVATE_FEATURE_REPO`（`owner/repo`）与 `Secrets.PRIVATE_FEATURE_TOKEN`
  （只读该私有仓库的细粒度 PAT，Contents: Read）→ 检出到 `private/`，发行包含私有功能；
- 没配 → 打一行 notice 按公开版构建，发版流程照常走完；
- 检出后立刻 `rm -rf private/.git`，避免 token 留在 `.git/config` 里被打进 artifact。

PR 构建（包括 fork 的 PR）拿不到 Secret，因此**永远**是公开版；`ci.yml` 不需要任何改动。

### 第三方凭证（构建期注入，不进仓库）

私有侧有些数据代理需要第三方签发的 `AppId` / `AppSecret`（BGM 数据代理用的
`Secrets.KAZUMI_APPID` / `Secrets.KAZUMI_KEY`；第三方弹幕源用的
`Secrets.DANDANAPI_APPID` / `Secrets.DANDANAPI_KEY`）。和 Kazumi 的做法一致，**只在构建期注入**，
源码、`private/`、文档里都不出现明文：

- CI：`build-release.yml` 的「校验私有功能凭证（未配置则跳过）」一步只打印 notice，
  「构建签名发行 APK」一步把 Secrets 传成同名环境变量；`app/build.gradle` 读环境变量
  （或 `-P` 参数）写进 `BuildConfig`。没配 → 凭证为空串 → 私有侧自动退回旧链路 / 关闭该功能，
  发版照常。
- 本地：`$env:KAZUMI_APPID="…"; $env:KAZUMI_KEY="…"; $env:DANDANAPI_APPID="…"; $env:DANDANAPI_KEY="…"`
  （PowerShell）后照常 `gradlew` / `build_tui.py`；也可以 `-PKAZUMI_APPID=… -PDANDANAPI_APPID=…`。
  值只进 `BuildConfig`，不落任何受版本管理的文件（`local.properties` 不进仓库）。

## 红线

1. `private/` 里的任何内容都不许进公开仓库（靠 `.gitignore` 护栏，不要用 `git add -f`）；
2. 公共侧只能出现「门面 + 条件挂点」，不许出现功能的实现片段（算法、交互流程、数据格式都不行）；
3. 公共文档只描述机制，不写私有功能的实现细节；
4. 私有功能的异常必须被门面吞掉，不能影响公共功能（否则公开构建会连带出问题）。
