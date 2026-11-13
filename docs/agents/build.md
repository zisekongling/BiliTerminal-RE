# 构建与单测

> 日常四条命令在 `AGENTS.md`；本册是构建 / 单测 / 装包 / 构建缓存报错时的完整手册。遇到编译怪象先读这里再动手。

## 命令

```bash
./gradlew.bat :app:assembleDebug                 # 编译验证（两个 flavor 都编）；CI 也跑它
./gradlew.bat :app:testStandardDebugUnitTest     # 纯 JVM 单测（普通版）
./gradlew.bat :app:testCoexistDebugUnitTest      # 纯 JVM 单测（共存版，同源码）
./gradlew.bat :app:assembleRelease               # 正式包（R8 + ABI 分包，两个 flavor 都出）
```

**CI 实际跑什么**（`.github/workflows/ci.yml` 的 `build-and-test` job；push / PR 到 `main` 与手工触发都会跑）：依次是 `:app:verifyVersionConsistency` → `:app:assembleDebug` → `:app:testStandardDebugUnitTest` → `:app:testCoexistDebugUnitTest`（两个 flavor 共用同一份 `app/src/test`，但构建配置不同，都要绿——coexist 以前从没进过 CI）→ `:app:assembleRelease`（unsigned 冒烟：CI 不写签名材料；验 R8 + `shrinkResources` + `keep.pro`，两个 flavor × 3 ABI 分包 + 通用包）。发版链在 `build-release.yml`，只跑 `testStandardDebugUnitTest` + 签名 `assembleRelease`（前面还有 `verify-version` 硬校验 tag ↔ 版本号）。本地仍按下面这几条自己先过一遍。

**曾经的「测试版构建」属性已拔出**：`-PtestBuild=<name>` 那套（`BuildConfig.TEST_BUILD` / `util/TestBuild.kt` / `player/PlaybackDiag.kt` / `Logu` 的诊断期放大 / `ErrorCatch` 的带额度快照）在 2026-11-08 随「部分设备点播卡顿」专项收尾**整体删除**，`app/build.gradle` 里现在没有这个属性，传了也没有任何效果。要再出临时测试包，按 `docs/archive/superpowers/specs/2026-11-07-test-build-framework.md` §4 的清单重建。

## 单测任务名与产物

- **单测任务名带 flavor 前缀**（26.10.11 起引入 `standard` / `coexist` 两个 product flavor，见 `docs/architecture-map.md` §7.37）：`testDebugUnitTest` **已不存在**，写成它 Gradle 直接报任务找不到。
- 产物按 flavor 分目录：`app/build/outputs/apk/<flavor>/<buildType>/app-<flavor>-<abi>-<buildType>.apk`（发布附件里普通版会被 CI 改名回历史名 `app-<abi>-release.apk`，共存版是 `app-coexist-<abi>-release.apk`）。

## 交互式构建 / 装包（build_tui.py）

- **交互式构建 / 装包用 `build_tui.py`**（26.10.20 起，纯标准库、无第三方依赖）：`python build_tui.py` 进全屏 TUI —— 首两行切换「构建类型（Debug/Release）」与「发行渠道（普通版/共存版）」，下面是「构建 Debug / 构建 Release / **构建全部**（两渠道 × 两类型，4 个包一次出，带 `--continue` 所以某个变体失败其余照跑）/ **版本号 / 版本名**（`V`，直接改 `app/build.gradle` 的 `versionCode` / `versionName`，改完会询问是否同步改 `strings.xml` 的「【YY.MM.DD 本次更新】」锚点——不同步则 `check` / CI 的 `verifyVersionConsistency` 会失败，`assemble` 不受影响）/ 安装到真机 / 安装到模拟器」，安装时按设备 `ro.product.cpu.abi` 自动挑 ABI 分包（没有对应分包退通用包），包比源码旧会先自动构建。`↑↓` 选择、`←→` 切换、`Enter` 执行、`1-4` 直达、`A` 全构建、`V` 改版本、`R` 刷新设备、`C` 连接模拟器、`Q` 退出。
- 非 TTY 环境（CI / 管道）自动降级为行式菜单；也可走参数：`--connect` / `--list-devices` / `--list-apks` / `--list-version` / `--build --type release --flavor coexist` / `--build-all`（四变体一次构建）/ `--install --target emulator` / `--version-name 26.10.20 --version-code 2610200 [--sync-anchor]`（可与 `--build` / `--build-all` 连用，先改版本再构建）/ `--dry-run`（只打印命令，ABI 也真读）。
- **私有功能源码同步**：`P`（行式菜单 `[p]`）或 `--sync-private` 把 GitHub 私有仓库拉到 `private/`，机制与配置键见 `docs/optional-features.md`（`--private-repo` / `--private-token` / `--private-branch` 写进 `local.properties`，`--private-force` 覆盖已有目录）。菜单与构建横幅会显示 `私有功能：已就位 / 未获取`，未获取时构建出的就是公开版；`private/` 也算构建输入，同步完再构建不会误判「包是新的」。
- **调用的就是上面那几条 Gradle 任务**，所以任务名变化时要同步改脚本里的 `task_name()`。adb 从 PATH / `ANDROID_HOME` / `local.properties` 的 `sdk.dir` 里找。改版本只动 `build.gradle` 那一行、按原样保留换行（CRLF / LF 都行，写后回读校验）。

## 模拟器与 adb

> **模拟器不自己出现在 `adb devices` 里是常态**：MuMu / 雷电 / 夜神这类要 `adb connect 127.0.0.1:<端口>`（MuMu 12 是 16384，老版 5555 / 7555）。脚本在「一台在线设备都没有」时自动扫这几个端口，也可按 `C` / `--connect` 手动触发。

- **别用 `ro.kernel.qemu` 判真机/模拟器**——MuMu 不写这个属性（实测为空），脚本改成看 AVD 序列号、`ro.hardware`、虚拟机 DMI（MuMu 报 `innotek GmbH` / `NiohBox`）、本机回环 TCP、x86 ABI 兜底。
- MuMu 会同时开 5555 / 7555 / 16384 三个转发口，`adb devices` 会出现三条同一台机器，脚本按 `settings get secure android_id` 去重成一台。
- 调试包签名与已装包不一致时（例如设备上是 release 包、要装 debug 包）adb 会拒装，脚本会提示先卸载（**会清空登录态**），别自动替用户卸。

## 多模块与工具链

- 多模块 `:app` / `:ijkplayer-java` / `:DanmakuFlameMaster` / `:brotlij`（后三个别乱动）。
- Gradle 8.14.5（`gradle/wrapper/gradle-wrapper.properties`）、AGP 8.5.2、Kotlin 2.0.0、JDK 17、minSdk 19 / compileSdk 34、`resConfigs 'zh'`。
- 降 minSdk 时必须先看 `docs/architecture-map.md` §1.1 的兼容清单（依赖版本下限、`Build.SUPPORTED_ABIS`、`res/drawable-v21` 双份资源等）。

## 环境坑（gradle.properties 等）

- `gradle.properties` **不再写死** `org.gradle.java.home`（26.10.20 起；旧版指向 `C:\Program Files\Java\jdk-17`，换台机器/换 JDK 发行版就报 `Value '...' given for org.gradle.java.home Gradle property is invalid`，整个构建起不来）——本机 JVM 不是 JDK 17 时在**用户级** `~/.gradle/gradle.properties` 里设，或让 `JAVA_HOME` 指向 17。
- 代理配置在第 97-101 行**是注释状态**；`compileSdk 34` 需要 `platforms/android-34` + `build-tools/34.0.0`（缺了 AGP 自动下载，licenses 已接受时），`sdk.dir` 写在 gitignore 的 `local.properties`，release 签名也读它。
- **堆配置按通用开发机取值，别照顶配工作站写**（26.11.10 起）：`org.gradle.jvmargs` 4 G、`kotlin.daemon.jvmargs` 2 G、`org.gradle.workers.max=4`。旧值是 8 G / 8 G / 8，两个 daemon 叠加出十几个 G 的虚拟内存——在 2 核 / 7941 MiB 的小机器上构建时 `java` 被内核 OOM killer 杀掉（total-vm 11.7 G、anon-rss 2.85 G），连带把宿主的 `dsh-web.service` 一起拖死、机器重启两次。**内存更小的机器不要改这个文件**，用用户级 `~/.gradle/gradle.properties` 或 `./gradlew -Dorg.gradle.jvmargs=-Xmx1500m` 覆盖。
- **不要在 SSH 别名 `HK` 那台小服务器上编译本仓库**（2 核 / 7.9 G，常驻 2 个 Halo 容器 + 灵灵 + nginx，磁盘 29 G）：出包在本地开发机或 GitHub Actions 上做。确实要在这种小机器上验证时先 `free -m` 看余量，并加 `--max-workers=1 -Dorg.gradle.jvmargs=-Xmx1500m -Dkotlin.daemon.jvmargs=-Xmx1024m`；构建完顺手 `rm -rf ~/.gradle` 回收磁盘（一次全量构建的缓存就是 1 G 量级）。
- **两 flavor 并行编译会伪装成「语法错」**（26.11.14 实测）：`assembleDebug` 报 `> Task :app:compileCoexistDebugKotlin` + `Compilation error. See log for more details`，报告里**没有一行 `e:` 错误**——真因是 Kotlin daemon 拿不到内存（单独跑 `:app:compileStandardDebugKotlin` 才看到 `e: Daemon compilation failed: Not enough memory to run compilation. Try to increase it via 'gradle.properties'`）。`org.gradle.parallel=true` 下两个 flavor 的编译同时抢 4 G daemon + 2 G kotlin daemon，本机 31 G 内存但只剩 5.5 G 可用时必挂。**判据**：报编译错但输出里找不到 `^e: .*\.kt:` 行，就先怀疑内存；**解法**：加 `--no-parallel --max-workers=2` 重跑（同族历史记录见 `docs/review/fix-progress.md` 里「`--max-workers=1` 绕开两 flavor 并行 OOM」那条）。
- 依赖仓库已在 `settings.gradle` 前置 aliyun 的 `google`/`public`/`gradle-plugin` 镜像；`copyApkToDesktop` 未挂在 `assembleRelease` 上，需手动跑。

## build cache 回放陈旧资源

`gradle.properties` 开着 `org.gradle.configuration-cache` 与 build cache。只要你改动 `res/` 的**文件集合**（新增/删除/移动资源文件），`:app:mergeDebugResources` 可能 `FROM-CACHE` 回放一份旧结果，症状是编译报莫名其妙的 `Unresolved reference 'R.layout.xxx'` 或新的 string/color 找不到，而源文件明明存在。此时必须：

```bash
./gradlew.bat :app:clean --offline --no-configuration-cache
./gradlew.bat :app:assembleDebug --offline --no-build-cache --no-configuration-cache
```

判断依据：`app/build/intermediates/merged_res/debug/**/` 里找不到该资源对应的 `.flat` 文件，但 `aapt2 compile` 单独编译它又是成功的。

注意两点：

1. **`clean` 和 `assemble` 必须分两次 Gradle 调用**。写在同一次调用里（哪怕顺序正确）可能因配置缓存复用而报 `ManifestMerger2$MergeFailureException: NoSuchFileException: .../navigation_json/debug/extractDeepLinksDebug/navigation.json`。
2. 光加 `--no-build-cache` 不够时，把 `--no-configuration-cache` 也带上——配置缓存会复用过期的任务图，让上面那条 `Unresolved reference 'R.layout.xxx'` 继续复现。

## 增量编译坏掉

- 报 `org.jetbrains.kotlin.util.FileAnalysisException … FileNotFoundException: app\build\tmp\kotlin-classes\debug\…\Xxx.class`（某个 class 被删了但增量状态没更新）：`clean` 后重建解决。
- **同一个坏状态的第二种长相（26.10.21 实测，更坑）**：某个 flavor 的 Kotlin 模块元数据被写成空文件，单测编译报一堆 `Unresolved reference`（顶层函数 / DSL / 扩展函数全解析不到，**类却能正常解析**），看着像主源码真的删了符号。判据是 `app/build/tmp/kotlin-classes/<flavor>Debug/META-INF/app_<flavor>Debug.kotlin_module` 的大小——正常 233 字节左右，坏的时候只有 24 字节。只删该 flavor 的 `kotlin-classes/<flavor>Debug` 与 `build/kotlin/compile<Flavor>DebugKotlin` 后重编即可恢复，不必整包 clean。

## VS Code 的 Java 语言服务器（不是编译问题）

- 症状：任意 `.java` 文件首行报 **`X.java is not on the classpath of project app, only syntax errors are reported`**，多个文件一起报，`Project Settings` 里对应项目的 `Sources` 显示 **「No source paths are configured」**。
- 判据：`%APPDATA%\Code\User\workspaceStorage\<hash>\redhat.java\jdt_ws\.metadata\.log` 里是 `src/main/java/... [in app] does not exist`（Java Model Exception code 969），且 `jdt_ws` 下**只有 `.metadata` 没有项目实体**。Gradle 侧同期是 `CONFIGURE SUCCESSFUL`，所以**编译/单测/打包全都正常**，坏的只是 JDT 的模型。
- **修法：重启 VS Code**（26.10.29 实测一次就好）。不要先去 `+ Add Source Root...` 手动补——项目是空壳时补的路径不会落地，还可能把错状态固化下来。
- 根因：vscode-java 用 Buildship 导入 Gradle 项目，而 Buildship **不认识 AGP 的 `android { sourceSets }`**（日志里多出来的根项目 `BiliTerminal-BiliTerminal-RE` 也是同一原因）。所以这类报错在 AGP 工程里会反复出现，**重启即可，不必当作代码缺陷去查**；要稳定的代码导航请用 Android Studio。

## 量体积与 APK 构成

- **量体积必须先 clean**：debug 包在**增量**构建下会产生大量 `classesN.dex` 分片，体积可以虚高 3~4 MB（实测同一份代码增量构建 19.96 MB、clean 构建 15.44 MB）。任何"改动前后比体积"的结论都必须在 `:app:clean` 之后分别测量。
- **APK 体积构成（26.09.11 实测，release arm64 9.73 MB）**：native `.so` **67.3%**（`libijkffmpeg.so` 单文件就占一半）、dex 17.9%、`res/` 8.2%、`resources.arsc` 5.0%。**死代码删除对 release 体积几乎无影响**——R8 本来就会 strip 掉未引用的类；实测整轮死代码+死依赖清理只减了约 0.22 MB（2.2%）。真正的大头是 `libijkffmpeg.so`（5.16 MB），要动它得用 NDK 重编精简编解码器，属于另一件事。

## 原生库

`app/libs/{arm64-v8a,armeabi-v7a,x86}/` 是已提交的 `.so`，日常别动；重建需 NDK r21e（见 `rebuild_all.sh` 等脚本）。
