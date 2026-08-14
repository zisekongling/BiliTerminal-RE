package com.RobinNotBad.BiliClient.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity
import com.RobinNotBad.BiliClient.ui.theme.BiliColors
import com.RobinNotBad.BiliClient.ui.theme.BiliDimens
import com.RobinNotBad.BiliClient.ui.theme.ThemeManager
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ModernMySpaceActivity : AppCompatActivity() {

    private val viewModel: MySpaceViewModel by viewModels()
    private lateinit var avatarView: View
    private lateinit var nameText: TextView
    private lateinit var signText: TextView
    private lateinit var levelText: TextView
    private lateinit var menuContainer: LinearLayout
    private var logoutConfirmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(createLayout())
        observeState()
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

            val profileCard = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(ThemeManager.CARD)
                val pad = (BiliDimens.SPACING_LG * density).toInt()
                setPadding(pad, pad, pad, pad)
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(ThemeManager.CARD)
                    cornerRadius = BiliDimens.CARD_CORNER * density
                }
                background = bg

                avatarView = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (BiliDimens.ICON_XL * density).toInt(),
                        (BiliDimens.ICON_XL * density).toInt()
                    )
                    setBackgroundColor(ThemeManager.PRIMARY_LIGHT)
                }

                val infoCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((BiliDimens.SPACING_MD * density).toInt(), 0, 0, 0)
                }

                nameText = TextView(context).apply {
                    textSize = BiliDimens.TITLE_LARGE
                    setTextColor(ThemeManager.TEXT_PRIMARY)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                signText = TextView(context).apply {
                    textSize = BiliDimens.BODY_SMALL
                    setTextColor(ThemeManager.TEXT_SECONDARY)
                    setPadding(0, (2 * density).toInt(), 0, 0)
                }
                levelText = TextView(context).apply {
                    textSize = BiliDimens.BODY_SMALL
                    setTextColor(ThemeManager.PRIMARY)
                    setPadding(0, (2 * density).toInt(), 0, 0)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                infoCol.addView(nameText)
                infoCol.addView(signText)
                infoCol.addView(levelText)

                addView(avatarView)
                addView(infoCol)
            }

            menuContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            root.addView(profileCard)
            root.addView(menuContainer)
            addView(root)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { s ->
                nameText.text = s.userName
                signText.text = s.userSign.ifEmpty { "这个人很懒，什么都没写" }
                levelText.text = "LV ${s.userLevel}"

                menuContainer.removeAllViews()
                viewModel.getMenuItems().forEach { item ->
                    menuContainer.addView(createMenuItem(item))
                }
            }
        }
    }

    private fun createMenuItem(item: MenuAction): View {
        val density = resources.displayMetrics.density

        return TextView(this).apply {
            text = item.label
            textSize = BiliDimens.BODY_LARGE
            setTextColor(ThemeManager.TEXT_PRIMARY)
            val pad = (BiliDimens.SPACING_LG * density).toInt()
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ThemeManager.CARD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (BiliDimens.SPACING_SM * density).toInt()
            }

            if (item.targetClassName == "LOGOUT") {
                setTextColor(BiliColors.Error)
            }

            setOnClickListener {
                when {
                    item.targetClassName == "LOGOUT" -> handleLogout()
                    else -> navigateTo(item.targetClassName)
                }
            }
        }
    }

    private fun handleLogout() {
        if (logoutConfirmed) {
            CenterThreadPool.run {
                try {
                    SharedPreferencesUtil.removeValue(SharedPreferencesUtil.cookies)
                    SharedPreferencesUtil.removeValue(SharedPreferencesUtil.mid)
                    SharedPreferencesUtil.removeValue(SharedPreferencesUtil.csrf)
                    SharedPreferencesUtil.removeValue(SharedPreferencesUtil.refresh_token)
                    SharedPreferencesUtil.removeValue(SharedPreferencesUtil.cookie_refresh)
                    runOnUiThread {
                        MsgUtil.showMsg("账号已退出")
                        startActivity(Intent(this@ModernMySpaceActivity, LoginActivity::class.java))
                        finish()
                    }
                } catch (e: Exception) {
                    runOnUiThread { MsgUtil.err(e) }
                }
            }
        } else {
            MsgUtil.showMsg("再点一次退出登录！")
            logoutConfirmed = true
        }
    }

    private fun navigateTo(className: String) {
        try {
            startActivity(Intent(this, Class.forName(className)))
        } catch (e: ClassNotFoundException) {
            MsgUtil.showMsg("页面开发中")
        }
    }
}