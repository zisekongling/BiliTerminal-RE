package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.tutorial.Tutorial
import com.RobinNotBad.BiliClient.tutorial.TutorialPagerActivity
import com.RobinNotBad.BiliClient.tutorial.TutorialStore
import com.RobinNotBad.BiliClient.tutorial.Tutorials
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * 教程管理页。
 *
 * 数据源只有一处：[Tutorials.all]（全屏教程与页面滑动提示都在同一张注册表里，
 * 后者按 [Tutorials.hints] 单独分组渲染）。
 * 列表、进度、通过、清除**全部走 [TutorialStore] 的正式 API**，本类里不再出现
 * `tutorial_ver_*` / `tutorial_pager_*` / `tutorial_history_*` 这类键名字面量：
 * 之前它按旧 tag 拼键（`tutorial_ver_<旧 tag>`），而新教程系统按 `tutorial_ver_<id>` 读，
 * 两套键对不上，于是「通过教程」后教程照样弹、「清除进度」也清不掉（审计 P1-1）。
 *
 * 卡片状态含义：
 * - 已完成：已读版本 >= 当前版本（[TutorialStore.isRead]）；
 * - 历史记录：读过旧版本但还没读当前版本（例如 `short_video` 升到 v4 之后）；
 * - 重看：不改进度，只把教程内容重新打开一遍（看完由 `TutorialPagerActivity` 自己 markRead）。
 */
class TutorialManagerActivity : BaseActivity() {

    /** 当前渲染用的卡片容器；从「重看」返回时需要重新渲染一遍状态。 */
    private var listContainer: LinearLayout? = null

    @SuppressLint("MissingInflatedId", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        asyncInflate(R.layout.activity_tutorial_manager) { _, _ ->
            setPageName(getString(R.string.pagename_tutorial_manager))

            val container = findViewById<LinearLayout>(R.id.tutorial_list_container)
            listContainer = container
            renderCards(container)

            findViewById<MaterialCardView>(R.id.btn_pass_all).setOnClickListener {
                passAllTutorials()
            }

            findViewById<MaterialCardView>(R.id.btn_clear_all).setOnClickListener {
                clearAllTutorials()
            }

            findViewById<MaterialCardView>(R.id.btn_clear_pager).setOnClickListener {
                clearPagerTutorials()
            }

            findViewById<View>(R.id.scrollView).requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        // 从「重看」回来时教程可能刚被标记已读，重新渲染状态。
        // 首次创建时容器还没 inflate 完（asyncInflate 回调晚于 onResume），这里为 null，跳过即可。
        listContainer?.let { renderCards(it) }
    }

    /** 重画全部卡片：教程注册表（全屏教程 + 页面滑动提示）。 */
    private fun renderCards(container: LinearLayout) {
        container.removeAllViews()

        for (tutorial in Tutorials.all) {
            if (tutorial.isFullScreen) container.addView(buildTutorialCard(tutorial))
        }

        container.addView(
            buildSectionHeader(
                getString(R.string.tutorial_section_page_hint),
                getString(R.string.tutorial_section_page_hint_desc)
            )
        )
        for (hint in Tutorials.hints) {
            container.addView(buildHintCard(hint))
        }
    }

    // ==================== 卡片构建 ====================

    private fun buildTutorialCard(tutorial: Tutorial): MaterialCardView {
        val readVersion = TutorialStore.readVersion(tutorial.id)
        // 只有「读过旧版本、当前版本还没读」才显示历史行；已完成的由状态标签表达，不必重复。
        val history = if (readVersion in 0 until tutorial.version) {
            getString(R.string.text_tutorial_history_done, readVersion)
        } else {
            null
        }

        return buildCard(
            title = tutorial.title,
            description = tutorial.descriptionRes?.let { getString(it) },
            done = TutorialStore.isRead(tutorial),
            history = history,
            buttons = listOf(
                getString(R.string.tutorial_btn_pass) to { passTutorial(tutorial) },
                getString(R.string.tutorial_btn_clear) to { clearTutorial(tutorial) },
                getString(R.string.tutorial_btn_replay) to { replayTutorial(tutorial) },
            )
        )
    }

    private fun buildHintCard(hint: Tutorial): MaterialCardView = buildCard(
        title = hint.title,
        description = null,
        done = TutorialStore.isRead(hint),
        history = null,
        buttons = listOf(
            getString(R.string.tutorial_btn_pass) to { passHint(hint) },
            getString(R.string.tutorial_btn_clear) to { resetHint(hint) },
        )
    )

    /** 分组标题卡（与布局里「教程列表」那根标题卡观感接近）。 */
    private fun buildSectionHeader(title: String, description: String): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            radius = resources.getDimension(R.dimen.card_round)
            isClickable = false
            isFocusable = false
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp2px(8f), dp2px(4f), dp2px(8f), dp2px(4f))
        }

        column.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = title
            textSize = 13f
            setTextColor(ColorScheme.TEXT_PRIMARY)
        })

        if (description.isNotBlank()) {
            column.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp2px(2f)
                }
                text = description
                textSize = 11f
                setTextColor(ColorScheme.TEXT_SECONDARY)
            })
        }

        card.addView(column)
        return card
    }

    private fun buildCard(
        title: String,
        description: String?,
        done: Boolean,
        history: String?,
        buttons: List<Pair<String, () -> Unit>>,
    ): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            radius = resources.getDimension(R.dimen.card_round)
            isClickable = false
            isFocusable = false
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp2px(8f), dp2px(8f), dp2px(8f), dp2px(8f))
        }

        // 颜色一律走主题色或既有语义色资源，不写死色值：写死白色在浅色主题下看不见标题，
        // 写死绿/橙也不跟主题走（依据 AGENTS.md「颜色只读 ?attr/ 或既有 color 资源」）。
        val textOnColor = ContextCompat.getColor(this, R.color.text_on_primary)
        val successColor = ContextCompat.getColor(this, R.color.status_success)
        val warningColor = ContextCompat.getColor(this, R.color.status_warning)

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            text = title
            textSize = 13f
            setTextColor(ColorScheme.TEXT_PRIMARY)
        }

        val statusBadge = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp2px(8f), dp2px(2f), dp2px(8f), dp2px(2f))
        }

        if (done) {
            statusBadge.text = getString(R.string.text_tutorial_done)
            statusBadge.setTextColor(textOnColor)
            // View.backgroundTintList 的 setter 是 API 21 才有的，minSdk 19 上要走 ViewCompat；
            // API < 21 时它等价于 no-op（statusBadge 本身没设 background），不会崩。
            ViewCompat.setBackgroundTintList(statusBadge, ColorStateList.valueOf(successColor))
        } else {
            statusBadge.text = getString(R.string.text_tutorial_undone)
            statusBadge.setTextColor(textOnColor)
            ViewCompat.setBackgroundTintList(statusBadge, ColorStateList.valueOf(warningColor))
        }

        titleRow.addView(titleText)
        titleRow.addView(statusBadge)
        container.addView(titleRow)

        if (!description.isNullOrBlank()) {
            container.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp2px(4f)
                }
                text = description
                textSize = 11f
                setTextColor(ColorScheme.TEXT_SECONDARY)
            })
        }

        if (history != null) {
            container.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp2px(2f)
                }
                text = history
                textSize = 10f
                setTextColor(ColorScheme.TEXT_TERTIARY)
            })
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(6f)
            }
        }

        buttons.forEachIndexed { index, (label, action) ->
            buttonRow.addView(MaterialButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    dp2px(32f),
                    1f
                ).apply {
                    if (index > 0) marginStart = dp2px(4f)
                }
                text = label
                textSize = 11f
                // 主题按钮样式默认开了「单行 + 自动字号（7～14sp）」；这里字号是特意写死的 11sp，
                // 把自动字号的上限钉回 11sp，既保留 11sp 的观感，又不会在大字体下折行。
                setAutoSizeTextTypeUniformWithConfiguration(
                    7,
                    11,
                    1,
                    TypedValue.COMPLEX_UNIT_SP
                )
                isAllCaps = false
                setOnClickListener { action() }
            })
        }

        container.addView(buttonRow)
        card.addView(container)

        return card
    }

    // ==================== 单篇操作 ====================

    private fun passTutorial(tutorial: Tutorial) {
        TutorialStore.markRead(tutorial)
        afterProgressChanged(getString(R.string.msg_tutorial_passed, tutorial.title))
    }

    private fun clearTutorial(tutorial: Tutorial) {
        TutorialStore.clear(tutorial.id)
        afterProgressChanged(getString(R.string.msg_tutorial_progress_cleared, tutorial.title))
    }

    /** 「重看」：只负责打开，不动已读状态（看完由 [TutorialPagerActivity] 自己 markRead）。 */
    private fun replayTutorial(tutorial: Tutorial) {
        TutorialPagerActivity.start(this, listOf(tutorial))
    }

    private fun passHint(hint: Tutorial) {
        TutorialStore.markRead(hint)
        afterProgressChanged(getString(R.string.msg_tutorial_passed, hint.title))
    }

    private fun resetHint(hint: Tutorial) {
        TutorialStore.clear(hint)
        afterProgressChanged(getString(R.string.msg_tutorial_progress_cleared, hint.title))
    }

    // ==================== 批量操作 ====================

    private fun passAllTutorials() {
        for (tutorial in Tutorials.all) TutorialStore.markRead(tutorial)
        MsgUtil.showMsg(getString(R.string.msg_tutorialmanageractivity_001))
        recreate()
    }

    private fun clearAllTutorials() {
        for (tutorial in Tutorials.all) TutorialStore.clear(tutorial)
        MsgUtil.showMsg(getString(R.string.msg_tutorialmanageractivity_002))
        recreate()
    }

    private fun clearPagerTutorials() {
        for (hint in Tutorials.hints) TutorialStore.clear(hint)
        MsgUtil.showMsg(getString(R.string.msg_tutorialmanageractivity_003))
        recreate()
    }

    // ==================== 辅助 ====================

    private fun afterProgressChanged(message: String) {
        MsgUtil.showMsg(message)
        recreate()
    }

    private fun dp2px(dp: Float): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }
}
