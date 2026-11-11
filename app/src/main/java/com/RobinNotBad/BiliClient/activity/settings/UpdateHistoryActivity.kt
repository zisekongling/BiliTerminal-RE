package com.RobinNotBad.BiliClient.activity.settings

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.BuildConfig
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.ui.appearance.SegmentedBarStyle
import com.RobinNotBad.BiliClient.util.ToolsUtil
import com.google.android.material.tabs.TabLayout

/**
 * 更新日志页（历史 + 当前版本），按**版本**选项卡切换。
 *
 * 数据源仍是 strings.xml 里既有的两个数组，不新增、不修改任何字符串资源：
 * - R.array.update_log_current：首行形如「【26.09.24 本次更新】」，即**当前版本**；
 * - R.array.update_history_log：以「## YYYY-MM-DD」分组的历史版本，日期就是版本名
 *   （本项目版本号规则为 YY.MM.DD，`2026-09-13` → `26.09.13`）。
 *
 * 发版流程（见 .dsh/skills/rebili-version-release）会把旧的 update_log_current
 * 整段搬到 update_history_log 最前面，两个数组因此天然拼成一条完整版本时间线，
 * 且当前版本不在历史数组里 —— 这正是本页把「当前版本」作为第 0 个选项卡的依据。
 *
 * 入口：关于页「历史更新日志」（预选当前版本）；以及 F4 —— 版本升级后首次启动时
 * 由 SplashActivity 带 [EXTRA_VERSION_INDEX] 自动打开本页。
 */
class UpdateHistoryActivity : BaseActivity() {

    /** 一个版本的日志。[version] 为版本名（YY.MM.DD），[lines] 为其正文行。 */
    data class VersionLog(val version: String, val lines: List<String>)

    companion object {
        /** SplashActivity 传入的预选选项卡下标（越界回退 0）。沿用上游的 intent 参数名。 */
        const val EXTRA_VERSION_INDEX = "version_index"

        /** 版本名特征：YY.MM.DD */
        private val VERSION_TOKEN = Regex("\\d{2}\\.\\d{2}\\.\\d{2}")

        /**
         * 把「## 2026-09-13」里的日期换成版本名「26.09.13」。
         * 日期长度不足 4 位或格式不符时原样返回，避免把异常数据弄成半截版本号。
         */
        fun dateToVersionLabel(date: String): String {
            val parts = date.split('-')
            if (parts.size == 3 && parts[0].length == 4 && parts[1].isNotEmpty() && parts[2].isNotEmpty()) {
                return parts[0].substring(2) + "." + parts[1] + "." + parts[2]
            }
            return date
        }

        /** 从「【26.09.24 本次更新】」这类标题里取出「26.09.24」；取不到返回 null。 */
        fun extractVersionLabel(title: String): String? = VERSION_TOKEN.find(title)?.value

        /**
         * 纯函数：把「当前版本数组 + 历史版本数组」拼成版本列表。
         *
         * 当前版本排第 0 位（与上游 UpdateLog.LOG[0] = 当前版本的语义一致），
         * 历史版本按数组顺序（新 → 旧）紧随其后。
         * 抽成 companion 里的纯函数，一是可以直接被 JVM 单测覆盖，二是 SplashActivity
         * 计算预选下标时复用同一套解析，不需要再写一份。
         */
        fun parseVersions(currentItems: Array<out String>, historyItems: Array<out String>): List<VersionLog> {
            val versions = ArrayList<VersionLog>()
            val seen = HashSet<String>()

            // 1) 当前版本：首行当版本标题，其余行是正文
            val currentLines = ArrayList<String>()
            var currentLabel: String? = null
            for (raw in currentItems) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (currentLabel == null) {
                    // 标题行形如「【26.09.24 本次更新】」，抽出版本名；抽不到就整行当版本名
                    currentLabel = extractVersionLabel(line) ?: line
                } else {
                    currentLines.add(line)
                }
            }
            val current = currentLabel
            if (current != null) {
                versions.add(VersionLog(current, currentLines))
                seen.add(current)
            }

            // 2) 历史版本：遇到「## 日期」就切一组
            var lines: MutableList<String>? = null
            for (raw in historyItems) {
                val line = raw.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("## ")) {
                    val label = dateToVersionLabel(line.removePrefix("## ").trim())
                    if (seen.add(label)) {
                        val group = ArrayList<String>()
                        lines = group
                        versions.add(VersionLog(label, group))
                    } else {
                        // 与已有版本重名（发版归档时的边界情况）：整组丢弃，避免出现重复选项卡
                        lines = null
                    }
                } else {
                    lines?.add(line)
                }
            }
            return versions
        }

        /** 当前 BuildConfig 版本在版本列表中的下标；找不到时回退 0（即当前版本）。 */
        fun indexOfCurrentVersion(context: Context): Int {
            val versions = parseVersions(
                context.resources.getStringArray(R.array.update_log_current),
                context.resources.getStringArray(R.array.update_history_log)
            )
            // 更新日志的锚点只有日期（三通道共用同一份当日日志，见 docs/version-channels.md §3），
            // 所以比对要用 VERSION_NAME_BASE 而不是砍后缀：`26.11.08-beta.1` 要砍，`26.11.08.1`
            // 这种同日重发的稳定版后缀也要砍，`substringBefore('-')` 只处理得了前者。
            val current = BuildConfig.VERSION_NAME_BASE
            val index = versions.indexOfFirst { it.version == current }
            return if (index >= 0) index else 0
        }
    }

    /** 当前已渲染的下标，用于避免「先渲染再 select()」造成的重复渲染。 */
    private var currentIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_update_history) { _, _ ->
            setPageName(getString(R.string.pagename_updatehistoryactivity_001))

            val versions = parseVersions(
                resources.getStringArray(R.array.update_log_current),
                resources.getStringArray(R.array.update_history_log)
            )

            val container = findViewById<LinearLayout>(R.id.history_container)
            val tabs = findViewById<TabLayout>(R.id.version_tabs)

            for (entry in versions) {
                tabs.addTab(tabs.newTab().setText(entry.version))
            }
            // 版本页签：玻璃轨道 + 选中格子的主色胶囊（设计稿 E 方案）。
            // 必须在 addTab 之后调用，否则 tabCount 还是 0，一格胶囊都画不出来。
            SegmentedBarStyle.styleTabs(tabs)

            if (versions.isEmpty()) {
                // 数组为空时不加监听器，直接给兜底文案
                container.addView(
                    TextView(this).apply {
                        text = "暂无更新日志"
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setPadding(0, ToolsUtil.dp2px(16f), 0, 0)
                    })
            } else {
                tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab) {
                        showVersion(container, versions, tab.position)
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab) {}

                    override fun onTabReselected(tab: TabLayout.Tab) {}
                })

                var preselect = intent.getIntExtra(EXTRA_VERSION_INDEX, 0)
                if (preselect < 0 || preselect >= versions.size) preselect = 0

                // 先渲染目标版本，再让选项卡高亮同步过去：
                // 顺序反过来的话，第一个 tab 在 addTab 时已被自动选中、不会触发 onTabSelected，
                // 内容就会是空的。
                showVersion(container, versions, preselect)
                tabs.getTabAt(preselect)?.select()
            }
        }
    }

    /** 渲染第 [index] 个版本的日志；与当前已渲染版本相同则跳过。 */
    private fun showVersion(container: LinearLayout, versions: List<VersionLog>, index: Int) {
        if (index < 0 || index >= versions.size) return
        if (index == currentIndex) return
        currentIndex = index

        val entry = versions[index]
        container.removeAllViews()

        // 版本标题
        container.addView(
            TextView(this).apply {
                text = "Re：哔哩终端 " + entry.version
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, ToolsUtil.dp2px(8f), 0, ToolsUtil.dp2px(2f))
            })

        for (line in entry.lines) {
            addLogLine(container, line)
        }
    }

    /** 追加一行日志条目（分类小标题/编号条目统一按普通行处理）。 */
    private fun addLogLine(section: LinearLayout, line: String) {
        val isCategory = line.startsWith("[") && line.endsWith("]")
        section.addView(
            TextView(this).apply {
                text = line
                textSize = 11f
                if (isCategory) setTypeface(typeface, Typeface.BOLD)
                alpha = if (isCategory) 1f else 0.85f
                setPadding(0, ToolsUtil.dp2px(2f), 0, 0)
            })
    }
}
