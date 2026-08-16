# AGENTS.md

## 语言约定（最重要）

**与用户交流、写代码注释、写文档一律使用中文。**

## 项目概况

- RE:哔哩终端（ReBiliClient）—— 第三方 B 站安卓客户端，BiliClient 2.7.0 的改版分支（`main` 分支）。
- 典型 "vibe coding" 产物（见 `readme.md`）：代码大量由 AI 生成，可能存在逻辑漏洞、幻觉功能、屎山写法。改动前不要盲目信任现有实现。
- 版本号按日期：`versionCode 2608140` / `versionName "26.08.14"`（YY.MM.DD，`app/build.gradle`）。新增版本按此格式递增。

## 构建

多模块：`:app`（主应用）、`:ijkplayer-java`、`:DanmakuFlameMaster`、`:brotlij`（第三方面向播放器/弹幕/brotli 封装，别乱动）。
Gradle 8.11.1（腾讯镜像）、AGP 8.5.2、Kotlin 2.0.0、KSP、Hilt 2.51.1，需 JDK 17。minSdk 24 / targetSdk 34 / compileSdk 35，`resConfigs 'zh'`（只有中文资源）。

常用命令：

```bash
./gradlew :app:assembleDebug        # 编译验证（无 CI，以此为准）
./gradlew :app:testDebugUnitTest    # 单元测试（JUnit4，纯 JVM）
./gradlew :app:assembleRelease      # 正式包（R8+资源压缩，ABI 分包 arm64-v8a/armeabi-v7a）
```

Windows 下另有 `b.bat`（=assembleRelease）、`build.bat`（=installDebug）。

### 构建坑（容易踩）

- `gradle.properties` 硬编码 `org.gradle.java.home=C:\Program Files\Java\jdk-17`（Windows 路径），且配置了 `systemProp.*proxyHost=127.0.0.1:10808` 代理。在 Linux/WSL 构建时需覆盖 java.home；本机代理（Clash 10808）不运行则依赖下载直接失败。
- release 签名从 gitignore 的 `local.properties`（KEY_PATH/KEY_PASSWORD/ALIAS_NAME/ALIAS_PASSWORD）读取。`assembleRelease` 末尾会经 `copyApkToDesktop` 自动把 APK 复制到桌面并 `adb install -r`（`app/build.gradle`）。

## 架构

- **Java + Kotlin 混编**（约 134 Java / 269 Kotlin，正在从 Java 迁移），Kotlin 代码可直接调用 Java 层。
- 两套 UI 范式并存：
  1. **遗留层**：`activity/`、`adapter/`、`api/*.java` —— 静态方法 + org.json 逐层拆 JSON（"大哥上楼梯"风格），网络统一走 `util/NetWorkUtil.java`（OkHttp + Cookie 管理 + brotli/gzip 解压）。
  2. **新层**：`network/api/`（Retrofit + kotlinx-serialization）、`di/`（Hilt）、`ui/*`（ViewModel/MVVM）、`data/repository/`。
- 关键入口/工具：`BiliTerminalApp.kt`（@HiltAndroidApp）、`util/SharedPreferencesUtil.java`（设置）、`util/TerminalContext`（导航）、`util/Logu`（日志）。
- 新页面优先复用现有基建：列表页继承 `activity/base/RefreshMainActivity`，通用 `BaseActivity`、`CenterThreadPool`、`MsgUtil`（见 `docs/superpowers/specs/` 设计文档的用法）。

## 约定

- **文案硬编码中文**：遗留页面标题/Toast 直接写中文字符串，**不改 `strings.xml`**；唯一例外是设置页（字符串驱动，按 `desc_*` 惯例加资源）。参考 `activity_edit_sign.xml`、`EditSignActivity`。
- 网络请求与解析逻辑分离，纯解析部分抽成可单测函数（参考 `HotSearchApi.parseHotSearch`）。
- 手机模式（`ui_mobile_mode` / `MobileShellActivity` 等）代码已彻底移除，不要恢复。
- 不轻易引入新第三方库，优先复用现有依赖。

## 原生库（谨慎）

- `app/libs/{arm64-v8a,armeabi-v7a}/` 是已提交的二进制 `.so`（ijkplayer 的 libijkffmpeg/libijkplayer/libijksdl + 自编译 libbrotli），`jniLibs.srcDirs = ['libs']`。日常开发**不要**动这些文件。
- 重建需 NDK r21e，流程见 `rebuild_all.sh` / `compile_ffmpeg.sh` / `compile_ijk.sh` / `compile_brotli.sh`（基于 `~/ijkplayer-build`，走代理）。

## 文档与验证

- `docs/superpowers/specs/` 有功能设计文档，写新功能前先读。
- `bilibili-API/` 是 B 站接口文档快照，改 API 相关代码时参考。
- 无集成测试；以 `assembleDebug` 编译通过 + 真机手测为准。修改解析逻辑时补 `app/src/test/` 的纯 JVM 单测（避免 Android 框架依赖，参考 `NetWorkUtilTest` 的 FakeSharedPreferences 手法）。
