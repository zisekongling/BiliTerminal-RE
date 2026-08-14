package com.RobinNotBad.BiliClient.ui.menu

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.RobinNotBad.BiliClient.ui.theme.BiliColors
import com.RobinNotBad.BiliClient.ui.theme.BiliDimens
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ModernMenuActivity : AppCompatActivity() {

    private val viewModel: MainMenuViewModel by viewModels()
    private lateinit var menuGrid: android.widget.GridLayout
    private lateinit var userHeaderLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(createMainLayout())
        observeState()
    }

    private fun applyTheme() {
        window.statusBarColor = ThemeManager.BACKGROUND
        window.navigationBarColor = ThemeManager.BACKGROUND
    }

    private fun createMainLayout(): View {
        val density = resources.displayMetrics.density

        return ScrollView(this).apply {
            setBackgroundColor(ThemeManager.BACKGROUND)
            isVerticalScrollBarEnabled = false

            val rootLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    (BiliDimens.SPACING_LG * density).toInt(),
                    (BiliDimens.SPACING_XL * density).toInt(),
                    (BiliDimens.SPACING_LG * density).toInt(),
                    (BiliDimens.SPACING_LG * density).toInt()
                )
            }

            userHeaderLayout = createUserHeader(density)
            rootLayout.addView(userHeaderLayout)

            val sectionTitle = createSectionTitle("常用功能", density)
            rootLayout.addView(sectionTitle)

            menuGrid = createMenuGrid(density)
            rootLayout.addView(menuGrid)

            addView(rootLayout)
        }
    }

    private fun createUserHeader(density: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeManager.CARD)
            val cornerRadius = (BiliDimens.CARD_CORNER * density).toInt()
            val padding = (BiliDimens.SPACING_LG * density).toInt()
            setPadding(padding, padding, padding, padding)

            background = createRoundRectDrawable(ThemeManager.CARD, BiliDimens.CARD_CORNER * density)

            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val avatarPlaceholder = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (40 * density).toInt(), (40 * density).toInt()
                )
                setBackgroundColor(ThemeManager.PRIMARY_LIGHT)
            }

            val nameColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((BiliDimens.SPACING_MD * density).toInt(), 0, 0, 0)
            }

            val userNameText = TextView(context).apply {
                id = View.generateViewId()
                text = "哔哩终端"
                textSize = BiliDimens.TITLE_MEDIUM
                setTextColor(ThemeManager.TEXT_PRIMARY)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val userDescText = TextView(context).apply {
                id = View.generateViewId()
                text = "点击登录体验更多功能"
                textSize = BiliDimens.BODY_SMALL
                setTextColor(ThemeManager.TEXT_TERTIARY)
                setPadding(0, (2 * density).toInt(), 0, 0)
            }

            nameColumn.addView(userNameText)
            nameColumn.addView(userDescText)

            topRow.addView(avatarPlaceholder)
            topRow.addView(nameColumn)

            addView(topRow)

            setOnClickListener {
                val intent = Intent(context, Class.forName("com.RobinNotBad.BiliClient.activity.user.MySpaceActivity"))
                startActivity(intent)
            }
        }
    }

    private fun createSectionTitle(title: String, density: Float): TextView {
        return TextView(this).apply {
            text = title
            textSize = BiliDimens.TITLE_SMALL
            setTextColor(ThemeManager.TEXT_SECONDARY)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val topPad = (BiliDimens.SPACING_XL * density).toInt()
            val bottomPad = (BiliDimens.SPACING_MD * density).toInt()
            setPadding(0, topPad, 0, bottomPad)
        }
    }

    private fun createMenuGrid(density: Float): GridLayout {
        return GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val items = buildDefaultMenuItems()
            items.forEach { item ->
                addView(createMenuItem(item, density))
            }
        }
    }

    private fun createMenuItem(item: MenuItemData, density: Float): View {
        val itemSize = ((resources.displayMetrics.widthPixels - BiliDimens.SPACING_LG * 2 * density) / 3).toInt()
        val padding = (BiliDimens.SPACING_SM * density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = itemSize - padding * 2
                height = (80 * density).toInt()
                setMargins(padding, padding, padding, padding)
            }
            setBackgroundColor(ThemeManager.CARD)
            clipToPadding = false
            background = createRoundRectDrawable(ThemeManager.CARD, BiliDimens.CARD_CORNER * density)

            val iconView = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (BiliDimens.ICON_LG * density).toInt(),
                    (BiliDimens.ICON_LG * density).toInt()
                )
                setBackgroundColor(ThemeManager.PRIMARY_LIGHT)
            }

            val labelText = TextView(context).apply {
                text = item.title
                textSize = BiliDimens.BODY_MEDIUM
                setTextColor(ThemeManager.TEXT_PRIMARY)
                gravity = Gravity.CENTER
                setPadding(0, (BiliDimens.SPACING_SM * density).toInt(), 0, 0)
                maxLines = 1
            }

            addView(iconView)
            addView(labelText)

            setOnClickListener {
                try {
                    val className = "com.RobinNotBad.BiliClient.activity.${item.targetActivity}"
                    val intent = Intent(context, Class.forName(className))
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                } catch (e: ClassNotFoundException) {
                    Toast.makeText(context, "${item.title} 页面开发中", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createRoundRectDrawable(color: Int, radius: Float): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                if (state.isLoading) return@collectLatest

                val badge = state.dynamicBadge + state.messageBadge
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBadges()
    }
}