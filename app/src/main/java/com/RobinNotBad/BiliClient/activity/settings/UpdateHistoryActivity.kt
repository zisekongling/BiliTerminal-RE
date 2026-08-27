package com.RobinNotBad.BiliClient.activity.settings

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.ToolsUtil

/**
 * 历史更新日志列表页。
 *
 * 读取 R.array.update_history_log，按日期分组展示：
 * 以 "## YYYY-MM-DD" 开头为日期标题，其余为当日日志条目（含分类小标题与编号条目）。
 * 与关于页"本次更新"（update_log_current）明确区分。
 */
class UpdateHistoryActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_update_history) { _, _ ->
            setPageName("历史更新日志")

            val container = findViewById<LinearLayout>(R.id.history_container)
            val items = resources.getStringArray(R.array.update_history_log)

            // 当前正在构建的日期组容器；遇到 "## 日期" 时切新组
            var currentDateContainer: LinearLayout? = null

            for (raw in items) {
                val line = raw.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("## ")) {
                    // 日期标题
                    val date = line.removePrefix("## ").trim()
                    val dateContainer = buildDateSection(container, date)
                    currentDateContainer = dateContainer
                } else {
                    val group = currentDateContainer
                    if (group != null) {
                        addLogLine(group, line)
                    }
                }
            }

            // 空数据兜底
            if (currentDateContainer == null) {
                container.addView(
                    TextView(this).apply {
                        text = "暂无历史更新日志"
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setPadding(0, ToolsUtil.dp2px(16f), 0, 0)
                    })
            }
        }
    }

    /** 新建一个日期分组：标题 + 条目容器，追加到外层容器。 */
    private fun buildDateSection(container: LinearLayout, date: String): LinearLayout {
        // 日期标题
        container.addView(
            TextView(this).apply {
                text = date
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, ToolsUtil.dp2px(10f), 0, ToolsUtil.dp2px(4f))
            })

        // 当日条目容器
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ToolsUtil.dp2px(8f), 0, 0, 0)
        }
        container.addView(section)
        return section
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
                visibility = View.VISIBLE
            })
    }
}
