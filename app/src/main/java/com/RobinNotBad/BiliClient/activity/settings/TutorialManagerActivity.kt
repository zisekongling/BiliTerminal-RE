package com.RobinNotBad.BiliClient.activity.settings

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class TutorialManagerActivity : BaseActivity() {

    private data class TutorialItem(
        val tag: String,
        val name: String,
        val description: String,
        val xmlArrayId: Int,
        val isPager: Boolean = false
    )

    private val tutorialItems = mutableListOf<TutorialItem>()

    @SuppressLint("MissingInflatedId", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildTutorialList()

        asyncInflate(R.layout.activity_tutorial_manager) { _, _ ->
            setPageName("教程管理")

            val container = findViewById<LinearLayout>(R.id.tutorial_list_container)

            for (item in tutorialItems) {
                val card = createTutorialCard(item)
                container.addView(card)
            }

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

    private fun buildTutorialList() {
        tutorialItems.clear()
        val tags = resources.getStringArray(R.array.tutorial_list)
        val names = arrayOf(
            "推荐页面教程",
            "视频详情教程",
            "用户主页教程",
            "搜索教程",
            "消息页面教程",
            "动态页教程",
            "动态详情页教程",
            "专栏详情页教程",
            "短视频教程"
        )
        val descs = arrayOf(
            "引导用户使用推荐页面和哔哩终端",
            "引导用户使用视频详情页",
            "引导用户使用用户主页",
            "引导用户使用搜索功能",
            "引导用户使用消息页面",
            "引导用户使用动态功能",
            "引导用户查看动态详情",
            "引导用户使用专栏详情页",
            "引导用户使用短视频播放功能"
        )
        val xmlArrayIds = intArrayOf(
            R.array.tutorial_recommend,
            R.array.tutorial_video,
            R.array.tutorial_space,
            R.array.tutorial_search,
            R.array.tutorial_message,
            R.array.tutorial_dynamic,
            R.array.tutorial_dynamic_info,
            R.array.tutorial_article,
            R.array.tutorial_short_video
        )

        for (i in tags.indices) {
            tutorialItems.add(
                TutorialItem(
                    tag = tags[i],
                    name = if (i < names.size) names[i] else tags[i],
                    description = if (i < descs.size) descs[i] else "",
                    xmlArrayId = if (i < xmlArrayIds.size) xmlArrayIds[i] else 0
                )
            )
        }

        // 添加页面滑动引导（显示一次后不再显示的教程）
        tutorialItems.add(TutorialItem("SearchActivity", "搜索页面引导", "搜索页面的左右翻页提示", 0, true))
        tutorialItems.add(TutorialItem("VideoInfoActivity", "视频详情页引导", "视频详情页的左右翻页提示", 0, true))
        tutorialItems.add(TutorialItem("UserInfoActivity", "用户主页引导", "用户主页的左右翻页提示", 0, true))
        tutorialItems.add(TutorialItem("OpusInfoActivity", "专栏详情页引导", "专栏详情页的左右翻页提示", 0, true))
        tutorialItems.add(TutorialItem("DynamicInfoActivity", "动态详情页引导", "动态详情页的左右翻页提示", 0, true))
    }

    private fun isTutorialCompleted(tag: String): Boolean {
        return SharedPreferencesUtil.getInt("tutorial_ver_$tag", -1) >= 0
    }

    private fun hasTutorialHistory(tag: String): Boolean {
        return SharedPreferencesUtil.getBoolean("tutorial_history_$tag", false)
    }

    private fun getTutorialVersion(tag: String): Int {
        return SharedPreferencesUtil.getInt("tutorial_ver_$tag", -1)
    }

    private fun isPagerTutorialCompleted(tag: String): Boolean {
        return !SharedPreferencesUtil.getBoolean("tutorial_pager_$tag", true)
    }

    private fun createTutorialCard(item: TutorialItem): MaterialCardView {
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
            setPadding(
                dp2px(8f),
                dp2px(8f),
                dp2px(8f),
                dp2px(8f)
            )
        }

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
            text = item.name
            textSize = 13f
            setTextColor(Color.parseColor("#ffffff"))
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

        val isCompleted = if (item.isPager) isPagerTutorialCompleted(item.tag) else isTutorialCompleted(item.tag)

        if (isCompleted) {
            statusBadge.text = "已完成"
            statusBadge.setTextColor(Color.parseColor("#ffffff"))
            statusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#27ae60"))
        } else {
            statusBadge.text = "未完成"
            statusBadge.setTextColor(Color.parseColor("#ffffff"))
            statusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#e67e22"))
        }

        titleRow.addView(titleText)
        titleRow.addView(statusBadge)

        val descText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(4f)
            }
            text = item.description
            textSize = 11f
            setTextColor(Color.parseColor("#aaaaaa"))
        }

        val historyText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(2f)
            }
            textSize = 10f
            visibility = View.GONE
        }

        if (!item.isPager && hasTutorialHistory(item.tag)) {
            historyText.text = "历史记录：已完成过（版本 ${getTutorialVersion(item.tag)}）"
            historyText.setTextColor(Color.parseColor("#888888"))
            historyText.visibility = View.VISIBLE
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

        val passBtn = MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp2px(32f),
                1f
            ).apply {
                marginEnd = dp2px(4f)
            }
            text = "通过教程"
            textSize = 11f
            isAllCaps = false
            setOnClickListener {
                passTutorial(item)
            }
        }

        val clearBtn = MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp2px(32f),
                1f
            ).apply {
                marginStart = dp2px(4f)
            }
            text = "清除进度"
            textSize = 11f
            isAllCaps = false
            setOnClickListener {
                clearSingleTutorial(item)
            }
        }

        buttonRow.addView(passBtn)
        buttonRow.addView(clearBtn)

        container.addView(titleRow)
        container.addView(descText)
        container.addView(historyText)
        container.addView(buttonRow)

        card.addView(container)

        return card
    }

    // ============ 通过教程 ============

    private fun passTutorial(item: TutorialItem) {
        if (item.isPager) {
            SharedPreferencesUtil.putBoolean("tutorial_pager_${item.tag}", false)
        } else {
            val xmlArray = resources.getStringArray(item.xmlArrayId)
            if (xmlArray.isNotEmpty()) {
                SharedPreferencesUtil.putBoolean("tutorial_history_${item.tag}", true)
                SharedPreferencesUtil.putInt("tutorial_ver_${item.tag}", xmlArray.size)
            }
        }
        MsgUtil.showMsg("「${item.name}」已通过")
        recreate()
    }

    // ============ 清除单个教程进度 ============

    private fun clearSingleTutorial(item: TutorialItem) {
        if (item.isPager) {
            SharedPreferencesUtil.removeValue("tutorial_pager_${item.tag}")
        } else {
            SharedPreferencesUtil.removeValue("tutorial_ver_${item.tag}")
            SharedPreferencesUtil.removeValue("tutorial_history_${item.tag}")
        }
        MsgUtil.showMsg("「${item.name}」的进度已清除")
        recreate()
    }

    // ============ 一键通过所有 ============

    private fun passAllTutorials() {
        for (item in tutorialItems) {
            if (item.isPager) {
                SharedPreferencesUtil.putBoolean("tutorial_pager_${item.tag}", false)
            } else {
                val xmlArray = resources.getStringArray(item.xmlArrayId)
                if (xmlArray.isNotEmpty()) {
                    SharedPreferencesUtil.putBoolean("tutorial_history_${item.tag}", true)
                    SharedPreferencesUtil.putInt("tutorial_ver_${item.tag}", xmlArray.size)
                }
            }
        }
        MsgUtil.showMsg("所有教程已标记为完成")
        recreate()
    }

    // ============ 清除所有教程进度 ============

    private fun clearAllTutorials() {
        for (item in tutorialItems) {
            if (item.isPager) {
                SharedPreferencesUtil.removeValue("tutorial_pager_${item.tag}")
            } else {
                SharedPreferencesUtil.removeValue("tutorial_ver_${item.tag}")
                SharedPreferencesUtil.removeValue("tutorial_history_${item.tag}")
            }
        }
        MsgUtil.showMsg("所有教程进度已清除")
        recreate()
    }

    // ============ 重置页面滑动引导 ============

    private fun clearPagerTutorials() {
        val pagerKeys = arrayOf(
            "SearchActivity", "VideoInfoActivity", "UserInfoActivity",
            "OpusInfoActivity", "DynamicInfoActivity"
        )
        for (key in pagerKeys) {
            SharedPreferencesUtil.removeValue("tutorial_pager_$key")
        }
        MsgUtil.showMsg("页面引导已重置")
        recreate()
    }

    private fun dp2px(dp: Float): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }
}