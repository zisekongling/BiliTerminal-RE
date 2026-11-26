package com.RobinNotBad.BiliClient

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 把 `docs/agents/` 里那些「文档写了、但违反时不会有任何东西变红」的约定变成断言。
 *
 * 来路（26.11.22）：199 条硬性表述里只有 `verifyVersionConsistency` 有机器校验，
 * 其余全靠改动者读过文档并记得住。这里挑的都是**能精确判定、当前代码本来就干净**的条目——
 * 宁可少接几条，也不接一条动辄误报的（误报会训练人忽略红灯）。
 *
 * 判定前一律先 strip 注释：这些文档条目本身就在注释里互相解释，不 strip 会把说明文字当成违规。
 *
 * 路径约定同 `DebugActivityExportTest`：Gradle 单测工作目录是 `app/`。
 */
class ConventionGuardTest {

    private fun javaSources(): List<File> =
        File("src/main/java").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()

    private fun stripJavaComments(text: String): String =
        text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//[^\n]*"), "")

    private fun stripXmlComments(text: String): String =
        text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    /** 行号从 1 起，便于直接跳转。 */
    private fun hits(file: File, pattern: Regex, strip: (String) -> String = ::stripJavaComments): List<String> {
        if (!file.isFile) return emptyList()
        val body = strip(file.readText())
        return body.lines().withIndex()
            .filter { pattern.containsMatchIn(it.value) }
            .map { "${file.path}:${it.index + 1}  ${it.value.trim().take(120)}" }
    }

    /**
     * `docs/agents/dev-conventions.md`：统一走 `util/TerminalDialog.kt`，业务代码里不许裸写
     * `AlertDialog.Builder`（26.10.05 已全量迁移 17 处）。裸写会让弹窗样式与危险项标记各说各话。
     */
    @Test
    fun `除 TerminalDialog 外不出现 AlertDialog`() {
        val sources = javaSources()
        assertTrue("src/main/java 只扫到 ${sources.size} 个源文件，路径八成变了", sources.size > 400)

        // DialogSizing 是 TerminalDialog 的尺寸辅助类，合法持有 AlertDialog 类型。
        val allowed = setOf("TerminalDialog.kt", "DialogSizing.kt")
        val bad = sources.filter { it.name !in allowed }
            .flatMap { hits(it, Regex("\\bAlertDialog\\b")) }

        assertTrue(
            "这些位置仍在直接使用 AlertDialog，请改走 util/TerminalDialog.kt " +
                "（menu / singleChoice / confirm / alert 四型；破坏性项进 danger）：\n" +
                bad.joinToString("\n"),
            bad.isEmpty()
        )
    }

    /**
     * `docs/agents/dev-conventions.md`：`?attr/` 不能写进**颜色状态列表 / drawable XML**。
     * 两者在 API 19–22 上走原生 `TypedArray.getColor`，遇到主题属性会静默退成哨兵红 `#FFFF0000`。
     * `drawable-v21/` 这类带限定符的目录只在 API 21+ 被取，允许使用，所以不扫。
     */
    @Test
    fun `res_color 与 res_drawable 里不出现主题属性`() {
        val targets = listOf(File("src/main/res/color"), File("src/main/res/drawable"))
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles { f: File -> f.isFile && f.extension == "xml" }.orEmpty().toList() }
        assertTrue("只扫到 ${targets.size} 个颜色/drawable XML，路径八成变了", targets.size > 20)

        val bad = targets.flatMap { hits(it, Regex("\\?(android:)?attr/"), ::stripXmlComments) }
        assertTrue(
            "这些 drawable/color XML 里用了 ?attr/，API 19–22 会静默变成 #FFFF0000。\n" +
                "要么改用具体颜色资源，要么把这份放平到 -v21 等限定符目录里：\n" +
                bad.joinToString("\n"),
            bad.isEmpty()
        )
    }

    /**
     * `docs/agents/dev-conventions.md`：不要在 `values-notnight` 覆盖 `terminal_bgblack` / `bgblack`。
     * 冷启动窗口按**系统** uiMode 绘制，覆盖会让暗色用户先闪一帧白。
     */
    @Test
    fun `values_notnight 不覆盖 bgblack`() {
        val resRoot = File("src/main/res")
        val notnightDirs = resRoot.listFiles { f: File -> f.isDirectory && "notnight" in f.name }.orEmpty()
        assertTrue("找不到 values-notnight* 目录，路径或命名变了", notnightDirs.isNotEmpty())

        val bad = notnightDirs
            .flatMap { dir -> dir.listFiles { f: File -> f.isFile && f.extension == "xml" }.orEmpty().toList() }
            .flatMap { hits(it, Regex("name=\"(terminal_)?bgblack\""), ::stripXmlComments) }

        assertTrue(
            "values-notnight 里覆盖了 bgblack：冷启动窗口按系统 uiMode 绘制，暗色用户会先闪一帧白。\n" +
                bad.joinToString("\n"),
            bad.isEmpty()
        )
    }

    /**
     * `docs/agents/pitfalls.md`：设备档位参数一律从 `PerformanceManager` 取。
     * `GlideUtil` 的 `QUALITY_*` / `MAX_W_*` 已于 26.10.04 删除，任何调用点重新写死就又是「两处真相」。
     */
    @Test
    fun `设备档位常量不在 GlideUtil 之外被引用`() {
        val sources = javaSources()
        assertTrue("src/main/java 只扫到 ${sources.size} 个源文件，路径八成变了", sources.size > 400)

        val bad = sources.filter { !it.name.startsWith("GlideUtil") }
            .flatMap { hits(it, Regex("\\b(QUALITY|MAX_W)_[A-Z_]+\\b")) }

        assertTrue(
            "这些位置仍在引用设备档位常量，请改从 PerformanceManager 取：\n" +
                bad.joinToString("\n"),
            bad.isEmpty()
        )
    }

    /**
     * `docs/agents/pitfalls.md`：`SettingsKeys.PLAYER` / `PLAY_QN` 已收敛（26.10.04），
     * 新代码不要再写 `"player"` / `"play_qn"` 字面量。
     * 两处例外：`SettingsKeys.kt` 是常量定义本身；`SettingMainActivity.kt` 的 `"player"` 是菜单分组 id，不是 SP 键。
     */
    @Test
    fun `不再新写 player 与 play_qn 字面量`() {
        val sources = javaSources()
        assertTrue("src/main/java 只扫到 ${sources.size} 个源文件，路径八成变了", sources.size > 400)

        val allowed = mapOf(
            "SettingsKeys.kt" to setOf("\"player\"", "\"play_qn\""),
            "SettingMainActivity.kt" to setOf("\"player\""),
        )
        val bad = sources.flatMap { file ->
            val allowedLiterals = allowed[file.name].orEmpty()
            hits(file, Regex("\"(player|play_qn)\"")).filter { hit ->
                // hit 形如 "path:line  <code>"，从代码部分取出实际出现的字面量
                val code = hit.substringAfter("  ")
                allowedLiterals.none { code.contains(it) }
            }
        }

        assertTrue(
            "这些位置新写了 player / play_qn 字面量，请改用 SettingsKeys：\n" +
                bad.joinToString("\n"),
            bad.isEmpty()
        )
    }

    /**
     * `AGENTS.md`：单测任务名必须带 flavor 前缀（`testDebugUnitTest` 已不存在）。
     * 写错了 CI 会在「任务不存在」上失败，而那条报错很难一眼看出是任务名问题。
     */
    @Test
    fun `工作流里不出现裸单测任务名`() {
        val dir = File("../.github/workflows")
        val files = dir.listFiles { f: File -> f.isFile && (f.extension == "yml" || f.extension == "yaml") }.orEmpty()
        assertTrue("找不到 $dir，路径可能变了", files.isNotEmpty())

        val bad = files.flatMap { file ->
            val body = file.readText().lines()
                .joinToString("\n") { it.substringBefore('#') }   // 注释里说明「已不存在」是合法的
            body.lines().withIndex()
                .filter { Regex("test(Debug|Release)UnitTest").containsMatchIn(it.value) }
                .map { "${file.path}:${it.index + 1}  ${it.value.trim()}" }
        }

        assertTrue(
            "工作流里出现裸单测任务名，必须带 flavor 前缀（testStandardDebugUnitTest / testCoexistDebugUnitTest）：\n" +
                bad.joinToString("\n"),
            bad.isEmpty()
        )
    }

    /**
     * `AGENTS.md`：用户可见文案一律进 `res/values/strings.xml`。
     *
     * 只守**布局文件**这一面：`android:text` 之类写死中文是纯增量错误，没有"老代码例外"，
     * 而且 XML 静态可判。代码侧（`UiText.of("…")`）有 348 处 26.10.19 之前的存量，
     * 文档明确「迁移未覆盖的老代码可以留着，改到哪块顺手迁哪块」，所以不做一刀切。
     */
    @Test
    fun `布局里不写死中文文案`() {
        val layoutDir = File("src/main/res/layout")
        val files = layoutDir.listFiles { f: File -> f.isFile && f.extension == "xml" }.orEmpty().toList()
        assertTrue("找不到 $layoutDir 或目录为空，路径可能变了", files.size > 20)

        val bad = files.flatMap { hits(it, Regex("android:(text|hint|contentDescription|title)\\s*=\\s*\"[^\"]*[\\u4e00-\\u9fff]"), ::stripXmlComments) }
        assertTrue(
            "这些布局把中文写死在 android:text/hint/contentDescription 里，请改成 @string/…：\n" +
                bad.joinToString("\n"),
            bad.isEmpty()
        )
    }

    /**
     * `AGENTS.md`：不许 `git add -f` 绕过 `.gitignore`。
     *
     * `git ls-files -ci --exclude-standard` 一条命令就能列出「已被跟踪、但按规则本应忽略」的文件——
     * 出现任何一条都说明有人强推过被忽略的东西（本仓库 `.gitignore` 里有 `private/`、`outputs/`、
     * `.mcp.json` 这类含敏感内容的路径，误提交代价很高）。
     */
    @Test
    fun `没有被强推入库的忽略文件`() {
        val repoRoot = File("..")
        assertTrue("找不到 ${repoRoot.absolutePath} 下的 .git，路径可能变了", File(repoRoot, ".git").exists())

        val process = ProcessBuilder("git", "-C", repoRoot.absolutePath, "ls-files", "-ci", "--exclude-standard")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        assertTrue("git ls-files 执行失败（exit=$exit）：$output", exit == 0)

        val offenders = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        assertTrue(
            "这些文件已被跟踪，但按 .gitignore 本应被忽略——说明用了 git add -f 强推。" +
                "确认里面不含 private/ 内容或凭据后，从索引里移除（git rm --cached <路径>）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /**
     * `docs/agents/dev-conventions.md` §界面：加载图标必须走 `AppCompatResources`
     * （统一入口 `util/StringUtil.getDrawable`），**不许**用 `ContextCompat.getDrawable` /
     * `ResourcesCompat.getDrawable`——本项目开了 `vectorDrawables.useSupportLibrary`，
     * API&lt;21 没有 PNG 兜底，那两条在这里仍是原生加载路径，遇到 `<vector>` 直接抛
     * `Resources$NotFoundException`。
     *
     * 为什么补这条（26.11.26）：`VectorDrawableCompatTest` 只扫 `res/` 下的 XML，
     * **代码侧一直是靠记性守的**。26.11.24 给 GlideUtil 加占位图缓存时就用了
     * `ContextCompat.getDrawable`，成了全仓第一处，CI 全绿、无人发现。
     */
    @Test
    fun `代码侧取图不用 ContextCompat 或 ResourcesCompat`() {
        val sources = javaSources()
        assertTrue("src/main/java 只扫到 ${sources.size} 个源文件，路径八成变了", sources.size > 400)

        // SDK>=21 分支显式使用：那里要加载的是「含矢量的 selector」，AppCompatResources 解不了，
        // 与 VectorDrawableCompatTest 对 drawable/player_selector_progress.xml 的白名单同源。
        val allowed = setOf("HighEnergyProgressBar.kt")
        val bad = sources.filter { it.name !in allowed }
            .flatMap { hits(it, Regex("(ContextCompat|ResourcesCompat)\\.getDrawable\\b")) }

        assertTrue(
            "这些位置用 ContextCompat/ResourcesCompat 取图，API<21 的矢量资源会崩。" +
                "改走 AppCompatResources.getDrawable（或 util/StringUtil.getDrawable）：\n" +
                bad.joinToString("\n"),
            bad.isEmpty()
        )
    }

    /**
     * 占位图 / 兜底图不许直接传资源 id。
     *
     * Glide 的 `placeholder(int)` / `error(int)` 不是「记下 id 以后再用」——构建请求时会
     * `Context.getDrawable(resId)` 现解一份。Resources 的 drawable cache 在低内存下会被
     * `onTrimMemory` 整个清空，cache 一空就是每次 bind 重解一遍，`onBindViewHolder` 里直接把
     * 低端机打 OOM（控制台崩溃 #401：262156 B = 256×256×4 + Bitmap 头）。
     *
     * 统一入口是 `GlideUtil.withPlaceholder(context, resId)`（一次设好占位 + 兜底）或
     * `GlideUtil.placeholderDrawable(context, resId)`（需要单独用一张 Drawable 时）。
     *
     * 只拦 `R.mipmap.*`：那是位图类资源，正是这条崩溃的来路；`R.drawable.*` 里多为 shape，
     * 本来就不占位图，一刀切会误报。
     */
    @Test
    fun `占位图不直传 mipmap 资源 id`() {
        val sources = javaSources()
        assertTrue("src/main/java 只扫到 ${sources.size} 个源文件，路径八成变了", sources.size > 400)

        val bad = sources.flatMap { hits(it, Regex("\\.(placeholder|error)\\(\\s*R\\.mipmap\\.")) }
        assertTrue(
            "这些位置把 mipmap 资源 id 直接交给 placeholder/error，低内存下每次 bind 都会重解位图。" +
                "改用 GlideUtil.withPlaceholder(context, R.mipmap.*)：\n" +
                bad.joinToString("\n"),
            bad.isEmpty()
        )
    }
}
