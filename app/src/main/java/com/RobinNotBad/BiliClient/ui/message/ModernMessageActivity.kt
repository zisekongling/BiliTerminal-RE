package com.RobinNotBad.BiliClient.ui.message

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.RobinNotBad.BiliClient.ui.theme.BiliColors
import com.RobinNotBad.BiliClient.ui.theme.BiliDimens
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ModernMessageActivity : AppCompatActivity() {

    private val viewModel: MessageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(createLayout())
        observeState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun applyTheme() {
        window.statusBarColor = ThemeManager.BACKGROUND
        window.navigationBarColor = ThemeManager.BACKGROUND
    }

    private fun createLayout(): View {
        val density = resources.displayMetrics.density

        return ScrollView(this).apply {
            setBackgroundColor(ThemeManager.BACKGROUND)

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (BiliDimens.SPACING_LG * density).toInt()
                setPadding(pad, pad, pad, pad)
            }

            val toolbar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(ThemeManager.SURFACE)
                val pad = (BiliDimens.SPACING_LG * density).toInt()
                setPadding(pad, pad, pad, pad)
                elevation = BiliDimens.ELEVATION_CARD * density
                addView(TextView(context).apply {
                    text = "消息"
                    textSize = BiliDimens.TITLE_LARGE
                    setTextColor(ThemeManager.TEXT_PRIMARY)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
            }

            val noticeSection = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (BiliDimens.SPACING_LG * density).toInt()
                setPadding(pad, pad, pad, pad)
                addView(TextView(context).apply {
                    text = "通知"
                    textSize = BiliDimens.TITLE_SMALL
                    setTextColor(ThemeManager.TEXT_SECONDARY)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(0, (BiliDimens.SPACING_SM * density).toInt(), 0, (BiliDimens.SPACING_SM * density).toInt())
                })

                listOf(
                    "reply" to "回复我的",
                    "like" to "收到的赞",
                    "at" to "@我",
                    "system" to "系统通知"
                ).forEach { (type, label) ->
                    addView(createNoticeCard(density, type, label))
                }
            }

            root.addView(toolbar)
            root.addView(noticeSection)
            addView(root)
        }
    }

    private fun createNoticeCard(
        density: Float,
        type: String,
        label: String
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = (BiliDimens.SPACING_LG * density).toInt()
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ThemeManager.CARD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (BiliDimens.SPACING_SM * density).toInt()
            }

            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(ThemeManager.CARD)
                cornerRadius = BiliDimens.CARD_CORNER * density
            }
            background = bg

            addView(TextView(context).apply {
                tag = type
                text = label
                textSize = BiliDimens.BODY_LARGE
                setTextColor(ThemeManager.TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })

            addView(TextView(context).apply {
                tag = "${type}_badge"
                text = ""
                textSize = BiliDimens.CAPTION
                setTextColor(ThemeManager.PRIMARY)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                visibility = View.GONE
            })

            setOnClickListener {
                navigateToNotice(type)
            }
        }

        return card
    }

    private fun navigateToNotice(type: String) {
        try {
            val intent = Intent()
            intent.setClassName(
                this,
                "com.RobinNotBad.BiliClient.activity.message.NoticeActivity"
            )
            intent.putExtra("type", type)
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { s ->
                // Update badge counts
                updateBadge("reply", s.badge.replyUnread)
                updateBadge("like", s.badge.likeUnread)
                updateBadge("at", s.badge.atUnread)
                updateBadge("system", s.badge.systemUnread)
            }
        }
    }

    private fun updateBadge(type: String, count: Int) {
        val rootLayout = findViewById<ViewGroup>(android.R.id.content)
        val badgeText = findTextViewWithTag(rootLayout, "${type}_badge")
        if (badgeText != null) {
            badgeText.text = if (count > 0) "(${count}未读)" else ""
            badgeText.visibility = if (count > 0) View.VISIBLE else View.GONE
        }
    }

    private fun findTextViewWithTag(view: View, tag: String): TextView? {
        if (view is TextView && view.tag == tag) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findTextViewWithTag(view.getChildAt(i), tag)
                if (found != null) return found
            }
        }
        return null
    }
}